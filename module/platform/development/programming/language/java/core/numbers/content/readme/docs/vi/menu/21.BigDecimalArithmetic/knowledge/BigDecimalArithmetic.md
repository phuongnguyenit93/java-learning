<a id="back-to-top"></a>

# Phép toán BigDecimal

## Menu
- [add, subtract, multiply và scale của kết quả](#big-decimal-arithmetic-001)
- [divide chính xác và số thập phân vô hạn tuần hoàn](#big-decimal-arithmetic-002)
- [divide với scale, RoundingMode hoặc MathContext](#big-decimal-arithmetic-003)
- [divideToIntegralValue, remainder và divideAndRemainder](#big-decimal-arithmetic-004)
- [pow và sqrt theo precision được yêu cầu](#big-decimal-arithmetic-005)
- [negate, abs, plus, min, max và signum](#big-decimal-arithmetic-006)
- [setScale và việc trả về đối tượng mới](#big-decimal-arithmetic-007)
- [movePointLeft, movePointRight và scaleByPowerOfTen](#big-decimal-arithmetic-008)
- [stripTrailingZeros có thể tạo scale âm](#big-decimal-arithmetic-009)
- [ulp của BigDecimal](#big-decimal-arithmetic-010)
- [Tính nhiều bước và lựa chọn thời điểm làm tròn](#big-decimal-arithmetic-011)
- [ArithmeticException khi không thể đáp ứng yêu cầu chính xác](#big-decimal-arithmetic-012)

## <a id="big-decimal-arithmetic-001">add, subtract, multiply và scale của kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# `BigDecimal.add`, `subtract`, `multiply` và scale của kết quả trong Java

## 1. Vấn đề: cùng phép toán, vì sao số chữ số thập phân của kết quả khác nhau?

Trong Java, `BigDecimal` biểu diễn số decimal bằng một **unscaled value** kiểu `BigInteger` và một **scale** kiểu `int`. Khi cộng, trừ hoặc nhân, Java vừa tính **giá trị toán học**, vừa xác định **scale của kết quả** theo hợp đồng API.

Ví dụ:

```java
import java.math.BigDecimal;

BigDecimal a = new BigDecimal("1.20");  // scale 2
BigDecimal b = new BigDecimal("3.004"); // scale 3

System.out.println(a.add(b));      // 4.204
System.out.println(a.subtract(b)); // -1.804
System.out.println(a.multiply(b)); // 3.60480
```

Điều đáng chú ý là:

- Tổng `4.204` có **scale = 3**.
- Hiệu `-1.804` có **scale = 3**.
- Tích `3.60480` có **scale = 5**.

Với các phép **chính xác, không truyền `MathContext`**:

| Phép toán | Scale ưu tiên của kết quả |
| --- | --- |
| `a.add(b)` | `max(a.scale(), b.scale())` |
| `a.subtract(b)` | `max(a.scale(), b.scale())` |
| `a.multiply(b)` | `a.scale() + b.scale()` |

Trong ba phép được trình bày, công thức này mô tả *preferred scale* khi tính toán chính xác theo đặc tả `BigDecimal`. Khi dùng `MathContext` với giới hạn precision, phép làm tròn và việc biểu diễn kết quả có thể **thay đổi scale cuối**. Ngoài ra, scale có giới hạn kiểu `int`, nên phép tính trên scale cực trị có thể cần xử lý ngoại lệ.

**Điểm quan trọng nhất:** scale là thuộc tính **biểu diễn decimal**, không phải số lượng “độ chính xác tuyệt đối” của phép đo. Một kết quả có nhiều chữ số 0 cuối không có nghĩa dữ liệu nguồn đã trở nên chính xác hơn về vật lý hay nghiệp vụ.

## 2. Nhắc lại mô hình `unscaled × 10^-scale`

Một giá trị `BigDecimal` được xác định bằng:

\[
\text{value}=\text{unscaledValue}\times10^{-\text{scale}}.
\]

Ví dụ:

```java
BigDecimal x = new BigDecimal("12.340");

System.out.println(x.unscaledValue()); // 12340
System.out.println(x.scale());         // 3
System.out.println(x.precision());     // 5
```

Giá trị toán học là:

\[
12340\times 10^{-3}=12.340.
\]

`precision()` đếm chữ số của unscaled value (trường hợp unscaled bằng 0 thì precision là 1). `scale()` quyết định vị trí dấu thập phân. Hai khái niệm không thay thế nhau.

Một số ví dụ:

| Chuỗi | Unscaled value | Scale | Precision | Giá trị số |
| --- | ---: | ---: | ---: | --- |
| `"1.20"` | `120` | 2 | 3 | 1.2 |
| `"3.004"` | `3004` | 3 | 4 | 3.004 |
| `"0.10"` | `10` | 2 | 2 | 0.1 |
| `"0.2"` | `2` | 1 | 1 | 0.2 |
| `"1000"` | `1000` | 0 | 4 | 1000 |
| `"1E+3"` | `1` | -3 | 1 | 1000 |
| `"0.00"` | `0` | 2 | 1 | 0 |

`scale` có thể âm. Với `new BigDecimal("1E+3")`, unscaled là 1, scale là -3:

\[
1\times10^{-(-3)}=1000.
\]

Vì vậy không thể suy đoán scale chỉ bằng “đếm số sau dấu chấm” của mọi dạng chuỗi, nhất là khi có ký hiệu số mũ hoặc sau `stripTrailingZeros()`.

### Bất biến của `BigDecimal`

`BigDecimal` là đối tượng **immutable**:

```java
BigDecimal balance = new BigDecimal("1.20");
balance.add(new BigDecimal("3.00")); // Kết quả không được giữ

System.out.println(balance); // 1.20
```

Muốn cập nhật biến:

```java
balance = balance.add(new BigDecimal("3.00"));
System.out.println(balance); // 4.20
```

Phương thức trả một `BigDecimal` biểu diễn kết quả; nó không sửa đối tượng ban đầu. Điều này đúng với cả `add`, `subtract`, `multiply`, `setScale` và các phép biến đổi khác.

## 3. `add`: cộng chính xác và lấy scale lớn hơn

Chữ ký quan trọng:

```java
BigDecimal add(BigDecimal augend)
BigDecimal add(BigDecimal augend, MathContext mc)
```

Khi **không có `MathContext`**, phép cộng chính xác có scale ưu tiên:

\[
s_{\text{sum}}=\max(s_a,s_b).
\]

Lý do: để cộng hai số decimal ở hai vị trí dấu chấm khác nhau, có thể biểu diễn cả hai theo **cùng scale lớn hơn** (cùng đơn vị nhỏ hơn), rồi cộng unscaled values.

Ví dụ:

```java
BigDecimal a = new BigDecimal("1.20");
BigDecimal b = new BigDecimal("3.004");

BigDecimal sum = a.add(b);
System.out.println(sum);         // 4.204
System.out.println(sum.scale()); // 3
```

Phân tích:

\[
1.20=120\times10^{-2}=1200\times10^{-3}
\]

\[
3.004=3004\times10^{-3}.
\]

Vì `max(2,3)=3`, tổng là:

\[
(1200+3004)\times10^{-3}
=4204\times10^{-3}
=4.204.
\]

### Các số 0 cuối vẫn được giữ khi scale yêu cầu

```java
BigDecimal left = new BigDecimal("0.10");
BigDecimal right = new BigDecimal("0.2");

BigDecimal sum = left.add(right);

System.out.println(sum);         // 0.30
System.out.println(sum.scale()); // 2
```

Về toán học `0.3 = 0.30`, nhưng `BigDecimal` của phép cộng chính xác có scale 2 do số hạng `"0.10"` có scale 2.

Nếu cần kết quả hiển thị như `"0.30"` cho một định dạng hai chữ số lẻ, scale 2 có thể hữu ích. Tuy nhiên, đây là **quy tắc phép tính**, không phải bằng chứng rằng phép đo ban đầu chính xác tới hàng phần trăm.

### Cộng một số 0 không chắc giữ nguyên scale của số kia

```java
BigDecimal x = new BigDecimal("12.3"); // scale 1
BigDecimal zero = new BigDecimal("0.000"); // scale 3

BigDecimal result = x.add(zero);
System.out.println(result);         // 12.300
System.out.println(result.scale()); // 3
```

Dù giá trị toán học không thay đổi, scale tăng từ 1 lên 3. Không nên coi “cộng 0 thì đối tượng/scale luôn không đổi”; quy tắc của phép cộng vẫn áp dụng.

## 4. `subtract`: trừ chính xác cũng lấy scale lớn hơn

Chữ ký chính:

```java
BigDecimal subtract(BigDecimal subtrahend)
BigDecimal subtract(BigDecimal subtrahend, MathContext mc)
```

Không truyền context:

\[
s_{\text{difference}}=\max(s_a,s_b).
\]

Ví dụ:

```java
BigDecimal a = new BigDecimal("1.20");
BigDecimal b = new BigDecimal("3.004");

BigDecimal difference = a.subtract(b);

System.out.println(difference);         // -1.804
System.out.println(difference.scale()); // 3
```

Cơ chế vẫn là căn chỉnh các unscaled values về cùng scale:

\[
(1200-3004)\times10^{-3}=-1804\times10^{-3}=-1.804.
\]

### Kết quả âm không làm scale “âm”

```java
BigDecimal a = new BigDecimal("0.10");
BigDecimal b = new BigDecimal("0.2");

BigDecimal result = a.subtract(b);
System.out.println(result);         // -0.10
System.out.println(result.scale()); // 2
```

**Dấu âm của giá trị** và **dấu của scale** là hai vấn đề khác nhau. Kết quả `-0.10` là số âm với scale **dương** 2.

### Khi phép trừ tạo zero

```java
BigDecimal a = new BigDecimal("2.00");
BigDecimal b = new BigDecimal("-2.00");

BigDecimal sum = a.add(b);
System.out.println(sum);         // 0.00
System.out.println(sum.scale()); // 2
```

Tương tự với `new BigDecimal("2.00").subtract(new BigDecimal("2.00"))`, kết quả chính xác có giá trị zero và scale 2.

`BigDecimal` không có hai giá trị số 0 mang dấu âm/dương khác nhau như `double`. Nhưng hai số zero có scale khác nhau vẫn khác nhau theo `equals`, dù `compareTo` xác định chúng bằng nhau về số học.

## 5. `multiply`: nhân chính xác và cộng scale

Chữ ký:

```java
BigDecimal multiply(BigDecimal multiplicand)
BigDecimal multiply(BigDecimal multiplicand, MathContext mc)
```

Nếu không có context, với:

\[
a=u_a\times10^{-s_a},\qquad
b=u_b\times10^{-s_b}
\]

thì:

\[
ab=(u_a u_b)\times10^{-(s_a+s_b)}.
\]

Do đó:

\[
s_{\text{product}}=s_a+s_b.
\]

Ví dụ:

```java
BigDecimal a = new BigDecimal("1.20");  // scale 2
BigDecimal b = new BigDecimal("3.004"); // scale 3

BigDecimal result = a.multiply(b);

System.out.println(result);         // 3.60480
System.out.println(result.scale()); // 5
```

Unscaled values là 120 và 3004. Nhân chúng được 360480, scale tổng là `2+3=5`, do đó:

\[
360480\times10^{-5}=3.60480.
\]

Hai chữ số 0 sau cùng không phải lỗi tính toán. Chúng xuất hiện vì phép nhân chính xác **giữ scale ưu tiên bằng tổng scale**.

### Một ví dụ rõ hơn về chữ số 0 cuối

```java
BigDecimal x = new BigDecimal("1.20");
BigDecimal y = new BigDecimal("3.0");

BigDecimal product = x.multiply(y);

System.out.println(product);         // 3.600
System.out.println(product.scale()); // 3
```

Giá trị toán học bằng `3.6`, nhưng scale 3 vì `2+1=3`. Nếu mục tiêu là hiển thị tiền với hai chữ số lẻ, có thể áp dụng `setScale(2, ...)` tại ranh giới nghiệp vụ thích hợp; đừng giả định scale của tích luôn đúng số chữ số cần xuất.

### Nhân với số 0

```java
BigDecimal a = new BigDecimal("1.0");  // scale 1
BigDecimal zero = new BigDecimal("0.00"); // scale 2

BigDecimal product = a.multiply(zero);

System.out.println(product);         // 0.000
System.out.println(product.scale()); // 3
```

**Giá trị bằng 0** không làm scale tự trở thành 0. Phép nhân chính xác vẫn tuân theo tổng scale trong tình huống thường gặp này.

### Tăng số chữ số kết quả không có nghĩa precision đầu vào sai

Với `1.20 × 3.004`, tích có unscaled value sáu chữ số (`360480`) và precision 6. Đây là **chữ số của biểu diễn kết quả**, không phải một cam kết khoa học rằng tất cả sáu chữ số đều đáng tin nếu hai đầu vào vốn là số đo có sai số.

## 6. Scale âm: cộng/trừ và nhân vẫn tuân theo công thức

Một `BigDecimal` có thể có scale âm khi tạo từ dạng lũy thừa 10:

```java
BigDecimal thousand = new BigDecimal("1E+3"); // scale -3
BigDecimal amount = new BigDecimal("2.50");   // scale 2
```

### Cộng và trừ với scale âm

```java
BigDecimal sum = thousand.add(amount);
BigDecimal difference = thousand.subtract(amount);

System.out.println(sum);             // 1002.50
System.out.println(sum.scale());     // 2
System.out.println(difference);      // 997.50
System.out.println(difference.scale()); // 2
```

Scale ưu tiên là `max(-3,2)=2`. Để cộng/trừ chính xác, `1E+3` được căn chỉnh sang scale 2, tương đương unscaled value `100000`.

### Nhân với scale âm

```java
BigDecimal product = thousand.multiply(amount);

System.out.println(product);         // 2.50E+3
System.out.println(product.scale()); // -1
System.out.println(product.toPlainString()); // 2500
```

Scale tổng bằng `-3+2=-1`. Unscaled value của tích là `1×250=250`, nên:

\[
250\times10^{-(-1)}=2500.
\]

`toString()` có thể in dạng số mũ `2.50E+3`, còn `toPlainString()` in `2500`. Khi parse lại chuỗi plain `"2500"`, scale sẽ thường là 0, **không còn là -1**. Nếu scale phải được bảo toàn qua serialization, lựa chọn định dạng là một phần hợp đồng dữ liệu.

### `stripTrailingZeros()` còn có thể tạo scale âm

```java
BigDecimal raw = new BigDecimal("1000.00");
BigDecimal normalized = raw.stripTrailingZeros();

System.out.println(normalized);         // 1E+3
System.out.println(normalized.scale()); // -3
```

`stripTrailingZeros()` giữ nguyên giá trị toán học nhưng thay đổi scale. Đừng chuẩn hóa số tiền/đo lường như vậy khi scale hoặc trailing zeros có ý nghĩa báo cáo.

## 7. `MathContext`: quy tắc về scale còn đúng hoàn toàn không?

Có overload của `add`, `subtract`, `multiply` nhận `java.math.MathContext`:

```java
BigDecimal result = a.add(b, mc);
BigDecimal difference = a.subtract(b, mc);
BigDecimal product = a.multiply(b, mc);
```

`MathContext` bao gồm:

- **Precision**: giới hạn số chữ số có nghĩa của kết quả (không phải số vị trí sau dấu thập phân).
- **RoundingMode**: quy tắc làm tròn khi cần giảm số chữ số có nghĩa.

Khi có `MathContext` với precision hữu hạn, Java có thể làm tròn kết quả chính xác, dẫn đến scale thực tế **khác preferred scale** ban đầu.

Ví dụ:

```java
import java.math.MathContext;
import java.math.RoundingMode;

BigDecimal a = new BigDecimal("1.20");
BigDecimal b = new BigDecimal("3.004");

MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

System.out.println(a.add(b, mc));      // 4.20
System.out.println(a.subtract(b, mc)); // -1.80
System.out.println(a.multiply(b, mc)); // 3.60
```

Bảng đối chiếu:

| Phép | Không context | Scale chính xác | Có `MathContext(3, HALF_UP)` | Scale sau làm tròn |
| --- | --- | ---: | --- | ---: |
| `1.20 + 3.004` | `4.204` | 3 | `4.20` | 2 |
| `1.20 - 3.004` | `-1.804` | 3 | `-1.80` | 2 |
| `1.20 × 3.004` | `3.60480` | 5 | `3.60` | 2 |

Sự khác nhau này là **chủ đích của API**, không phải Java tự làm phép cộng/trừ/nhân sai. Khi context giới hạn ba chữ số có nghĩa, các số ở cuối bị làm tròn theo `RoundingMode`.

### Khi làm tròn gây carry

```java
BigDecimal left = new BigDecimal("999.9");
BigDecimal right = new BigDecimal("0.2");

BigDecimal rounded = left.add(
        right, new MathContext(3, RoundingMode.HALF_UP));

System.out.println(rounded);             // 1.00E+3
System.out.println(rounded.precision()); // 3
System.out.println(rounded.scale());     // -1
```

Tổng toán học chính xác là `1000.1`. Với ba chữ số có nghĩa, nó làm tròn thành giá trị 1000 được biểu diễn `1.00E+3`, scale -1. Khi có carry, scale có thể thay đổi đáng kể, kể cả trở thành âm.

### `MathContext.UNLIMITED`

`MathContext.UNLIMITED` có `getPrecision() == 0`; số 0 ở đây có nghĩa **không giới hạn precision bởi context**, không phải “precision của một số BigDecimal bằng 0”.

```java
BigDecimal a = new BigDecimal("1.20");
BigDecimal b = new BigDecimal("3.004");

System.out.println(a.add(b, MathContext.UNLIMITED)); // 4.204
System.out.println(a.multiply(b, MathContext.UNLIMITED)); // 3.60480
```

Đối với ba phép cộng/trừ/nhân, context không giới hạn không yêu cầu cắt chữ số; nhưng vẫn chịu giới hạn tài nguyên và scale biểu diễn. Riêng phép chia có thể tạo chuỗi decimal vô hạn như `1/3`, cần hợp đồng làm tròn phù hợp; không suy luận rằng `MathContext.UNLIMITED` luôn khiến phép chia trả một kết quả hữu hạn chữ số.

### Precision của kết quả không nhất thiết bằng chính xác số đặt trong context

`new MathContext(6)` có nghĩa **tối đa** sáu chữ số có nghĩa; nếu kết quả tự nhiên chỉ có ba chữ số có nghĩa, không phải lúc nào Java cũng đệm đủ thành sáu. Ngược lại, số unscaled nhiều chữ số có thể bị rút gọn đáng kể vì làm tròn.

Không lấy `MathContext.getPrecision()` rồi mặc định đó chính là `result.precision()` trên mọi phép tính.

## 8. `setScale` là bước chuẩn hóa kết quả, không phải thay cho MathContext

Nếu yêu cầu hiển thị hoặc lưu số có **đúng hai chữ số thập phân**, hãy dùng `setScale` sau khi xác định chính sách nghiệp vụ:

```java
import java.math.RoundingMode;

BigDecimal exact = new BigDecimal("1.20")
        .add(new BigDecimal("3.004"));

BigDecimal display = exact.setScale(2, RoundingMode.HALF_UP);

System.out.println(exact);   // 4.204
System.out.println(display); // 4.20
```

- `add` chính xác trả `4.204` với scale 3.
- `setScale(2, HALF_UP)` trả `4.20` với scale 2.

Bởi có chữ số bị loại, hai giá trị toán học khác nhau (`4.204` và `4.20`); đây là làm tròn được chỉ định rõ ràng, không phải phép cộng sai.

### Khi nào dùng `RoundingMode.UNNECESSARY`?

Nếu giảm scale mà không được phép mất thông tin:

```java
BigDecimal a = new BigDecimal("1.2300");
System.out.println(
        a.setScale(2, RoundingMode.UNNECESSARY)); // 1.23
```

Ở đây chỉ bỏ các số 0 ở cuối, nên không cần làm tròn giá trị toán học. Trái lại:

```java
BigDecimal x = new BigDecimal("1.234");
// x.setScale(2, RoundingMode.UNNECESSARY);
// ArithmeticException: cần bỏ chữ số 4 khác 0
```

Dùng `UNNECESSARY` khi muốn **phát hiện** vi phạm điều kiện độ phân giải thay vì âm thầm làm tròn. Với hệ thống tài chính, phí, thuế, thanh toán hoặc phép đo, `RoundingMode` phải tuân thủ chính sách nghiệp vụ và quy định có liên quan; không tự cho rằng `HALF_UP` đúng cho mọi trường hợp.

### Làm tròn ở từng bước hay ở bước cuối?

Đây là một quyết định quan trọng:

```java
BigDecimal sum = a.add(b);         // Giữ tổng chính xác
BigDecimal result = sum.setScale(2, RoundingMode.HALF_UP);
```

Trong nhiều bài toán, tính toán chính xác trước và làm tròn tại ranh giới xuất/lưu là phù hợp. Nhưng một số hợp đồng nghiệp vụ yêu cầu làm tròn từng dòng hóa đơn trước khi cộng. Hai cách **có thể khác kết quả**.

Ví dụ:

- Hai khoản `0.005` nếu làm tròn `HALF_UP` **từng khoản** đến hai chữ số: `0.01 + 0.01 = 0.02`.
- Nếu **cộng trước**: `0.005 + 0.005 = 0.010`, rồi làm tròn hai chữ số: `0.01`.

Không có một quy tắc duy nhất cho mọi hệ thống. Phải ghi rõ đơn vị nhỏ nhất, thời điểm làm tròn, độ chính xác và cách xử lý trường hợp nửa.

## 9. Scale và equality: `equals` khác `compareTo`

Do scale là một phần của biểu diễn `BigDecimal`, hai đối tượng có **cùng giá trị số học** có thể không bằng nhau theo `equals`:

```java
BigDecimal a = new BigDecimal("2.0");  // scale 1
BigDecimal b = new BigDecimal("2.00"); // scale 2

System.out.println(a.equals(b));     // false
System.out.println(a.compareTo(b));  // 0
```

`equals` yêu cầu giá trị và scale phù hợp; `compareTo` kiểm tra thứ tự giá trị toán học và không phân biệt số 0 cuối.

Hệ quả:

- `HashSet<BigDecimal>`/`HashMap<BigDecimal,...>` dùng `equals` và `hashCode`, nên có thể coi `2.0` và `2.00` là **hai khóa** khác nhau.
- `TreeSet<BigDecimal>`/`TreeMap<BigDecimal,...>` dựa vào `compareTo` tự nhiên, nên thường coi chúng là **cùng khóa về thứ tự**.
- `setScale` hay `stripTrailingZeros` có thể làm kết quả của `equals` thay đổi dù số học không đổi.

Nếu scale là thông tin quan trọng (ví dụ cố định 2 chữ số decimal), giữ scale và dùng `equals` theo yêu cầu schema. Nếu chỉ muốn xác định hai số đại diện cùng giá trị toán học, dùng `compareTo(...) == 0` hoặc xây dựng quy tắc canonical phù hợp.

### Kết quả của phép nhân và khóa collection

```java
BigDecimal p = new BigDecimal("1.20")
        .multiply(new BigDecimal("3.0")); // 3.600, scale 3
BigDecimal q = new BigDecimal("3.60");     // 3.60, scale 2

System.out.println(p.equals(q));    // false
System.out.println(p.compareTo(q)); // 0
```

Nếu dùng `p` làm khóa `HashMap` rồi truy vấn bằng `q`, việc không tìm thấy khóa có thể là **đúng theo `equals`** chứ không phải lỗi hashmap. Chọn quy tắc scale/canonical hóa dữ liệu ở ranh giới phù hợp.

## 10. Những trường hợp đặc biệt cần kiểm tra

### 10.1. Đầu vào dạng số mũ

`new BigDecimal("1E+3")` có scale -3; cộng với `2.50` lấy scale 2, nhân với `2.50` lấy scale -1. Đừng ép `scale` luôn không âm trong mã kiểm tra, và đừng giả định tổng scale lúc nhân là số chữ số sau dấu chấm theo dạng hiển thị.

### 10.2. Kết quả bằng 0

`0.00` và `0.000` bằng nhau theo `compareTo` nhưng khác `equals`; các phép toán chính xác vẫn có scale ưu tiên. Khi cần giữ dấu vết quy tắc độ phân giải của nguồn, đừng tự động thay mọi zero bằng `BigDecimal.ZERO` có scale 0.

### 10.3. Giới hạn scale kiểu `int`

`scale()` là `int`. Nếu tổng scale khi nhân vượt miền số nguyên `int`, hoặc có phép điều chỉnh scale vượt miền, Java có thể ném `ArithmeticException`. Một số chi tiết với kết quả bằng 0 và phương thức có context được xử lý riêng theo hợp đồng API; không nên tạo một công thức modulo hay cố ép kiểu để vượt hạn chế đó.

Dữ liệu được xây dựng từ các exponent cực đoan có thể dẫn tới thao tác căn chỉnh scale, cấp phát hoặc tính toán tốn kém. Với đầu vào không tin cậy, cần giới hạn số chữ số, exponent, precision và khối lượng công việc.

### 10.4. Dấu của số 0

`BigDecimal` không có khái niệm `+0.0` và `-0.0` tách biệt theo biểu diễn IEEE 754 như `double`. Nhập `new BigDecimal("-0.00")` biểu diễn số 0 có scale 2; các phép cộng/trừ/nhân tuân theo giá trị 0 và scale. Nếu dấu âm của nguyên văn đầu vào có ý nghĩa báo cáo, phải giữ chuỗi đầu vào riêng.

### 10.5. Đầu vào từ `double` có thể đã mất decimal nguồn

```java
BigDecimal good = new BigDecimal("0.1");
BigDecimal fromDouble = new BigDecimal(0.1);

System.out.println(good); // 0.1
System.out.println(fromDouble);
// 0.1000000000000000055511151231257827021181583404541015625
```

`new BigDecimal(double)` biểu diễn chính xác giá trị nhị phân đã có của `double`, không phải decimal chuỗi mà người dùng đã nhập. Với decimal nghiệp vụ, ưu tiên `new BigDecimal(String)`; `BigDecimal.valueOf(double)` dùng dạng decimal từ `Double.toString`, nhưng cũng không khôi phục sai số đã phát sinh trước đó trong luồng `double`.

Nếu muốn tính chính xác bằng `BigDecimal`, **đừng đưa dữ liệu qua `double` trước**, rồi hy vọng kết quả add/multiply sẽ tự sửa độ chính xác đã mất.

## 11. Demo Java 17 hoàn chỉnh

Lưu tệp `BigDecimalOperationsScaleDemo.java`:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalOperationsScaleDemo {

    static void show(String label, BigDecimal x) {
        System.out.println(label + " = " + x
                + " | scale=" + x.scale()
                + " | precision=" + x.precision());
    }

    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("1.20");
        BigDecimal b = new BigDecimal("3.004");

        show("add", a.add(b));
        show("subtract", a.subtract(b));
        show("multiply", a.multiply(b));

        BigDecimal x = new BigDecimal("0.10");
        BigDecimal y = new BigDecimal("0.2");

        show("decimal add", x.add(y));
        show("decimal subtract", x.subtract(y));
        show("decimal multiply", x.multiply(y));

        BigDecimal thousand = new BigDecimal("1E+3");
        BigDecimal small = new BigDecimal("2.50");

        show("negative-scale add", thousand.add(small));
        show("negative-scale multiply", thousand.multiply(small));

        show("zero add", new BigDecimal("2.00")
                .add(new BigDecimal("-2.00")));
        show("zero multiply", new BigDecimal("1.0")
                .multiply(new BigDecimal("0.00")));

        MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

        show("context add", a.add(b, mc));
        show("context subtract", a.subtract(b, mc));
        show("context multiply", a.multiply(b, mc));
        show("context carry", new BigDecimal("999.9")
                .add(new BigDecimal("0.2"), mc));

        show("setScale2", a.add(b)
                .setScale(2, RoundingMode.HALF_UP));

        BigDecimal p = new BigDecimal("1.20")
                .multiply(new BigDecimal("3.0"));
        BigDecimal q = new BigDecimal("3.60");

        System.out.println("equals 3.600 / 3.60 = "
                + p.equals(q));
        System.out.println("compare 3.600 / 3.60 = "
                + p.compareTo(q));
    }
}
```

Biên dịch/chạy:

```bash
javac BigDecimalOperationsScaleDemo.java
java BigDecimalOperationsScaleDemo
```

Đầu ra trên Java 17:

```text
add = 4.204 | scale=3 | precision=4
subtract = -1.804 | scale=3 | precision=4
multiply = 3.60480 | scale=5 | precision=6
decimal add = 0.30 | scale=2 | precision=2
decimal subtract = -0.10 | scale=2 | precision=2
decimal multiply = 0.020 | scale=3 | precision=2
negative-scale add = 1002.50 | scale=2 | precision=6
negative-scale multiply = 2.50E+3 | scale=-1 | precision=3
zero add = 0.00 | scale=2 | precision=1
zero multiply = 0.000 | scale=3 | precision=1
context add = 4.20 | scale=2 | precision=3
context subtract = -1.80 | scale=2 | precision=3
context multiply = 3.60 | scale=2 | precision=3
context carry = 1.00E+3 | scale=-1 | precision=3
setScale2 = 4.20 | scale=2 | precision=3
equals 3.600 / 3.60 = false
compare 3.600 / 3.60 = 0
```

Kết quả giúp phân biệt:

- Các phép **chính xác không context** chọn scale dựa trên scale toán hạng.
- `MathContext` có thể làm tròn chữ số và thay đổi scale.
- `setScale` diễn đạt số vị trí decimal được yêu cầu bởi schema.
- Hai kết quả có cùng giá trị toán học vẫn có thể khác nhau theo `equals` vì scale.

## 12. Những lỗi phổ biến

| Sai lầm | Điều xảy ra | Cách làm phù hợp |
| --- | --- | --- |
| Cộng hai `BigDecimal` rồi giữ nguyên scale của toán hạng thứ nhất | Kết quả chính xác dùng `max(scaleA,scaleB)` | Kiểm tra `result.scale()`; chuẩn hóa khi cần |
| Nghĩ scale của tích cũng là `max(scaleA,scaleB)` | Phép nhân chính xác dùng **tổng scale** | `scaleA + scaleB` |
| Mong `1.20 * 3.0` in `3.6` | Scale 3 cho `3.600` | Chọn định dạng `setScale`/formatter theo nghiệp vụ |
| Gọi `a.add(b);` mà không gán | Đối tượng `a` bất biến | `a = a.add(b);` nếu muốn cập nhật biến |
| Coi MathContext precision 2 là scale 2 | Precision tính chữ số có nghĩa | `setScale(2, roundingMode)` để có hai vị trí decimal |
| Nghĩ context không ảnh hưởng scale | Làm tròn có thể làm scale thay đổi | Kiểm tra cả value, precision và scale |
| Dùng `RoundingMode.UNNECESSARY` khi cần làm tròn | Ném `ArithmeticException` | Chọn rounding policy hoặc từ chối dữ liệu |
| Làm tròn từng hạng và sau khi cộng mà không xét khác biệt | Hai luồng tính có thể khác kết quả | Quy định rõ thời điểm làm tròn |
| `p.equals(q)` kiểm tra cùng giá trị toán học | Hai giá trị có scale khác nhau sẽ không equals | `p.compareTo(q) == 0` nếu chỉ so giá trị |
| Chuẩn hóa `stripTrailingZeros` mọi lúc | Có thể mất trailing zeros/scale âm | Chỉ chuẩn hóa khi hợp đồng cho phép |
| Nghĩ scale của zero luôn 0 | `0.000` có scale 3 | Giữ scale theo schema hoặc chính sách |
| Cho rằng scale không thể âm | Dạng `"1E+3"` có scale -3 | Dùng đúng mô hình unscaled × 10^-scale |
| Dùng `double` rồi new `BigDecimal(double)` | Mang sai số nhị phân vào phép decimal | Parse nguồn decimal từ chuỗi |
| Giữ kết quả exact cộng/trừ/nhân rồi mặc định phù hợp tất cả cách hiển thị | Scale toán học không tự bằng scale xuất báo cáo | Định dạng theo đơn vị và yêu cầu của hệ thống |
| Tin kết quả `BigDecimal` không thể gây lỗi tài nguyên | Scale/precision quá lớn có thể vượt giới hạn | Validate kích thước và miền đầu vào |

## 13. Bài tập có đáp án

**Bài 1.** Tính giá trị và scale của `new BigDecimal("1.20").add(new BigDecimal("3.004"))`.

**Đáp án:** `4.204`, scale 3 vì `max(2,3)=3`.

**Bài 2.** Cùng hai toán hạng trên, kết quả của `subtract` và `multiply` là gì?

**Đáp án:** `subtract = -1.804` (scale 3); `multiply = 3.60480` (scale 5 do `2+3=5`).

**Bài 3.** Tại sao `"0.10" + "0.2"` lại ra `"0.30"` thay vì in `"0.3"`?

**Đáp án:** Phép cộng chính xác có preferred scale 2, do toán hạng thứ nhất có scale 2. Hai chuỗi cùng giá trị số học nhưng biểu diễn `BigDecimal` có scale khác nhau.

**Bài 4.** `new BigDecimal("1E+3").multiply(new BigDecimal("2.50"))` có scale nào?

**Đáp án:** Scale -1, vì `-3+2=-1`. Giá trị toán học là 2500, `toString()` có thể in `"2.50E+3"`.

**Bài 5.** Vì sao `new BigDecimal("1.0").multiply(new BigDecimal("0.00"))` có thể in `"0.000"`?

**Đáp án:** Dù tích bằng 0, phép nhân chính xác giữ tổng scale `1+2=3`.

**Bài 6.** Nếu ứng dụng yêu cầu tổng được hiển thị với đúng hai chữ số decimal, phương thức nào diễn đạt rõ hơn: `new MathContext(2)` hay `setScale(2, HALF_UP)`?

**Đáp án:** `setScale(2, HALF_UP)` quy định đúng hai vị trí decimal; `MathContext(2)` giới hạn hai chữ số có nghĩa, không phải hai vị trí sau dấu chấm.

**Bài 7.** Vì sao `a.add(b, new MathContext(3, HALF_UP))` có thể trả `4.20` thay vì `4.204`?

**Đáp án:** MathContext chỉ cho tối đa ba chữ số có nghĩa, khiến tổng chính xác được làm tròn; scale cuối thay từ 3 thành 2.

**Bài 8.** `BigDecimal("3.600").equals(BigDecimal("3.60"))` là gì? `compareTo` cho gì?

**Đáp án:** `equals` là `false` vì scale lần lượt 3 và 2; `compareTo` là 0 vì cùng giá trị toán học.

**Bài 9.** Nêu một tình huống mà làm tròn từng dòng rồi cộng khác với cộng trước rồi làm tròn.

**Đáp án:** Hai dòng `0.005`, làm tròn `HALF_UP` mỗi dòng đến hai chữ số được `0.01+0.01=0.02`; cộng chính xác trước thành `0.010`, làm tròn hai chữ số được `0.01`. Phải theo quy tắc nghiệp vụ.

**Bài 10.** Với `BigDecimal a = new BigDecimal("10.00"); a.subtract(BigDecimal.ONE);`, sau khi chạy biến `a` là bao nhiêu nếu không gán kết quả?

**Đáp án:** `10.00`. `BigDecimal` bất biến, nên gọi `subtract` mà bỏ qua giá trị trả về không thay đổi `a`.

**Tóm tắt:** Trong phép tính **chính xác không truyền context**, `add` và `subtract` chọn scale lớn hơn của hai toán hạng, `multiply` cộng hai scale. Đây là quy tắc **biểu diễn kết quả**; nếu thêm `MathContext`, kết quả có thể làm tròn và đổi scale. `setScale` phục vụ yêu cầu cố định vị trí decimal, còn `compareTo` và `equals` có ý nghĩa khác nhau khi scale khác nhau. Giữ tính bất biến, dữ liệu nguồn decimal chính xác và chọn chính sách làm tròn trước khi áp dụng vào nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-002">divide chính xác và số thập phân vô hạn tuần hoàn</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.divide chính xác và số thập phân vô hạn tuần hoàn trong Java

## 1. Mục tiêu và hiện tượng quan trọng

**java.math.BigDecimal** lưu giá trị thập phân theo mô hình một **unscaled value** nguyên và một **scale**. Với cộng, trừ và nhân không có MathContext, ta có thể nhận kết quả thập phân **chính xác** trong giới hạn tài nguyên. Nhưng **phép chia** có một trở ngại toán học: thương của hai số thập phân hữu hạn có thể có **vô hạn chữ số thập phân tuần hoàn**.

Ví dụ:

- **1/8 = 0.125**: biểu diễn hữu hạn, BigDecimal có thể trả chính xác.
- **1/3 = 0.333333…**: thập phân vô hạn tuần hoàn; không thể lưu thành một BigDecimal hữu hạn mà vẫn giữ giá trị 1/3 chính xác.
- **1/6 = 0.166666…**: vô hạn tuần hoàn, dù 6 có chứa thừa số 2.
- **3/6 = 1/2 = 0.5**: phải **rút gọn phân số** trước khi kết luận có thập phân vô hạn hay không.

Trong Java:

~~~java
import java.math.BigDecimal;

BigDecimal exact = BigDecimal.ONE.divide(new BigDecimal("8"));
// exact = 0.125

// Lệnh dưới ném ArithmeticException khi thực thi:
// BigDecimal recurring = BigDecimal.ONE.divide(new BigDecimal("3"));
~~~

Đây là hành vi **đúng theo hợp đồng API**. BigDecimal không cố đoán số chữ số người dùng muốn giữ, cũng không tự chuyển kết quả không hữu hạn sang một số xấp xỉ khi gọi overload chia chính xác.

Bài học sẽ giải thích **khi nào chia chính xác được**, cơ chế lặp vô hạn, cách lựa chọn các overload **divide**, ý nghĩa **scale**, **precision**, **MathContext**, **RoundingMode**, các ngoại lệ và cách kiểm tra bằng Java 17+.

## 2. Ôn lại mô hình BigDecimal

Giá trị một BigDecimal có thể viết:

\[
x = u \times 10^{-s}
\]

Trong đó **u** là unscaled integer (BigInteger) và **s** là scale (int). Ví dụ:

~~~java
BigDecimal n = new BigDecimal("10.00");

System.out.println(n.unscaledValue()); // 1000
System.out.println(n.scale());         // 2
~~~

Ta có **1000 × 10^−2 = 10.00**.

BigDecimal **immutable**: phương thức divide trả về một BigDecimal mới, không thay đổi toán hạng gốc. Kết quả có thể có **scale khác** so với kết quả của một overload khác dù cùng giá trị số học.

~~~java
BigDecimal a = new BigDecimal("10.00");
BigDecimal b = new BigDecimal("4");
BigDecimal q = a.divide(b);

System.out.println(q); // 2.50
System.out.println(a); // 10.00
~~~

Không được nhầm **giá trị số**, **scale** và **độ chính xác** (*precision*):

- **Giá trị số**: 2.5.
- **Scale** trong ví dụ: 2, nên chuỗi đại diện 2.50.
- **Precision** của 2.50: 3 chữ số có nghĩa.

Một giá trị BigDecimal với scale âm vẫn hợp lệ; có thể được hiển thị bằng dạng khoa học như **1E+3**. Điều này cũng ảnh hưởng cách xác định scale của kết quả phép chia.

## 3. Thế nào là phép chia chính xác?

**Chia chính xác (exact division)** nghĩa là giá trị trả về phải bằng **đúng thương toán học** của hai số đầu vào, **không bỏ chữ số có nghĩa** do làm tròn.

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal eight = new BigDecimal("8");

BigDecimal quotient = one.divide(eight); // 0.125
~~~

Vì 0.125 kết thúc sau ba chữ số thập phân, một BigDecimal hữu hạn có thể biểu diễn nó chính xác.

Với một số như **1/3**, không tồn tại số chữ số thập phân hữu hạn nào biểu diễn chính xác thương. Dù cho phép 10, 100 hoặc một triệu chữ số, mọi biểu diễn hữu hạn dạng 0.333… đều khác 1/3 một lượng nhỏ.

Do đó:

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

// one.divide(three) -> ArithmeticException
~~~

Đừng diễn giải exception thành **overflow của kiểu số 64 bit** hay lỗi precision mặc định quá thấp. Đây là việc yêu cầu một **kết quả exact nhưng không tồn tại dưới dạng thập phân hữu hạn**.

### Hai loại ArithmeticException cần phân biệt

Phép chia BigDecimal có thể ném ArithmeticException khi:

1. **Số chia bằng 0**: ví dụ 1/0; phép toán không xác định và BigDecimal không trả infinity/NaN.
2. **Thương thập phân không kết thúc khi bắt buộc exact**: ví dụ 1/3 với divide(divisor) hoặc precision=0.

Một số overload còn có ngoại lệ khi **RoundingMode.UNNECESSARY** yêu cầu kết quả không được làm tròn nhưng scale/precision được chỉ định không đủ.

Do đó chỉ bắt ArithmeticException rồi luôn in “số vô hạn tuần hoàn” là sai: có thể nguyên nhân là chia cho zero hoặc làm tròn bị cấm.

## 4. Điều kiện toán học: mẫu số sau rút gọn chỉ có 2 và 5

Xét phân số hữu tỉ **a/b**, với b ≠ 0. Rút gọn thành **p/q**, sao cho q > 0 và gcd(|p|, q) = 1.

Phân số có biểu diễn thập phân **hữu hạn** khi và chỉ khi q có dạng:

\[
q = 2^m5^n,\qquad m,n \ge 0
\]

Tức là **mẫu số sau rút gọn không có thừa số nguyên tố nào ngoài 2 và 5**.

Lý do: một số thập phân hữu hạn d chữ số sau dấu chấm có mẫu số **10^d = 2^d × 5^d**. Sau rút gọn, mọi thừa số nguyên tố của mẫu phải đến từ 2 và 5. Ngược lại, nếu q chỉ gồm 2 và 5, có thể nhân tử và mẫu để đưa mẫu thành một lũy thừa của 10.

| Phân số | Sau rút gọn | Phân tích mẫu | Dạng thập phân |
| --- | --- | --- | --- |
| 1/2 | 1/2 | 2 | 0.5 — hữu hạn |
| 1/8 | 1/8 | 2³ | 0.125 — hữu hạn |
| 1/40 | 1/40 | 2³ × 5 | 0.025 — hữu hạn |
| 3/6 | 1/2 | 2 | 0.5 — hữu hạn |
| 1/3 | 1/3 | 3 | 0.(3) — tuần hoàn |
| 1/6 | 1/6 | 2 × 3 | 0.1(6) — tuần hoàn |
| 1/7 | 1/7 | 7 | 0.(142857) — tuần hoàn |
| 22/7 | 22/7 | 7 | 3.(142857) — tuần hoàn |

Ký hiệu **0.(3)** nghĩa là chữ số 3 lặp lại mãi; **0.1(6)** là 0.16666… và **0.(142857)** là chu kỳ sáu chữ số.

**Quan trọng:** xét **mẫu sau rút gọn**, không xét mẫu ban đầu. Trường hợp **3/6** có mẫu ban đầu chứa thừa số 3 nhưng khi rút gọn mẫu còn 2, nên phép chia exact hoàn toàn thành công.

### Mở rộng sang hai BigDecimal có scale bất kỳ

Với:

\[
x=u_x \times 10^{-s_x}, \qquad y=u_y \times 10^{-s_y}
\]

thì:

\[
\frac{x}{y} = \frac{u_x}{u_y} \times 10^{s_y-s_x}
\]

Nhân với một lũy thừa của 10 (chỉ mang các thừa số 2 và 5) **không thể khử những thừa số nguyên tố khác 2/5 trong mẫu sau rút gọn**. Vì vậy việc kiểm tra mẫu số của tỷ số hai **unscaled integer** cũng cho biết thương có khai triển thập phân hữu hạn hay không, miễn số chia khác 0.

Đây là tính chất toán học hữu ích để giải thích, không phải lúc nào cũng cần tự cài kiểm tra: nếu chỉ muốn lấy thương exact, cứ gọi **divide(divisor)** và xử lý điều kiện lỗi theo hợp đồng.

## 5. Chữ ký divide và cách chọn overload

Các overload phổ biến của BigDecimal trong Java 17:

| Cách gọi | Kết quả mong muốn | Cần lưu ý |
| --- | --- | --- |
| **a.divide(b)** | Thương chính xác, nếu biểu diễn hữu hạn | Ném ArithmeticException với thương thập phân không kết thúc |
| **a.divide(b, scale, mode)** | Thương có **scale được chỉ định** | Làm tròn theo mode nếu cần |
| **a.divide(b, mode)** | Thương có **scale bằng a.scale()** | Dễ làm tròn ngoài ý muốn nếu a.scale() nhỏ |
| **a.divide(b, mc)** | Thương làm tròn theo **precision và mode** trong MathContext | precision=0 yêu cầu exact |
| **a.divide(b, scale, intMode)** | Overload dùng hằng integer rounding cũ | Deprecated, nên dùng enum RoundingMode |

Không có một overload nào được xem là tốt nhất cho mọi bài toán. Chọn theo **yêu cầu kết quả**:

- Nếu đòi **exact** và chỉ chấp nhận số thập phân hữu hạn: **divide(b)**.
- Nếu cần **đúng N vị trí sau dấu thập phân**: **divide(b, scale, roundingMode)**.
- Nếu cần **N chữ số có nghĩa** trên nhiều bậc độ lớn: **divide(b, MathContext)**.
- Nếu ứng dụng muốn scale kết quả giống chính số bị chia: **divide(b, roundingMode)**, nhưng phải hiểu đây là một chính sách rất cụ thể.

**RoundingMode** là enum thuộc **java.math**; nên dùng **RoundingMode.HALF_UP**, **HALF_EVEN**, **DOWN**, **UNNECESSARY**, v.v. thay cho các hằng BigDecimal.ROUND_* đã deprecated.

## 6. divide(BigDecimal) — chọn khi cần exact

Ví dụ chia hữu hạn:

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal eight = new BigDecimal("8");
BigDecimal forty = new BigDecimal("40");

System.out.println(one.divide(eight)); // 0.125
System.out.println(one.divide(forty)); // 0.025
~~~

Ví dụ chia vô hạn tuần hoàn:

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

try {
    System.out.println(one.divide(three));
} catch (ArithmeticException e) {
    System.out.println("Exact decimal quotient is unavailable");
}
~~~

**Đừng viết code xử lý ngoại lệ như trên để mặc nhiên tiếp tục với một số gần đúng** khi hệ thống thực sự yêu cầu phân số chính xác. Khi bài toán phải lưu thương 1/3 mà không được phép làm tròn, cần **biểu diễn phân số** (tử và mẫu BigInteger đã rút gọn), chứ BigDecimal không thể lưu toàn bộ khai triển vô hạn.

### Preferred scale của phép chia exact

Trong đặc tả BigDecimal, **preferred scale của phép chia** là:

\[
s_{\text{preferred}} = s_{\text{dividend}} - s_{\text{divisor}}
\]

Nhưng **scale thực tế có thể lớn hơn** nếu cần thêm chữ số để biểu diễn thương chính xác.

Ví dụ:

~~~java
BigDecimal q = new BigDecimal("10.00")
        .divide(new BigDecimal("4"));

System.out.println(q);         // 2.50
System.out.println(q.scale()); // 2
~~~

Số bị chia 10.00 có scale 2; số chia 4 có scale 0; preferred scale=2 và thương 2.50 vừa đúng yêu cầu. Với **1/8**, preferred scale là 0 nhưng để biểu diễn chính xác 0.125 phải tăng đến scale 3.

Không suy rằng **divide exact luôn bỏ toàn bộ số 0 cuối** hoặc luôn trả lại đúng scale của dividend: quy tắc về preferred scale và biểu diễn exact quyết định điều đó.

## 7. divide(divisor, scale, RoundingMode) — số chữ số sau dấu thập phân

Ví dụ làm tròn thương không kết thúc:

~~~java
import java.math.RoundingMode;

BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

BigDecimal rounded = one.divide(three, 4, RoundingMode.HALF_UP);
// 0.3333
~~~

Overload này tạo kết quả **scale=4**, tức có 4 vị trí sau dấu chấm thập phân với cách hiểu thông thường của scale dương. Với hai chữ số:

~~~java
BigDecimal two = new BigDecimal("2");
BigDecimal result = two.divide(three, 2, RoundingMode.HALF_UP);
// 0.67
~~~

**Scale không phải precision.** Chia 12345/3 ở scale=2 sẽ tạo số ở hai chữ số phần lẻ (ví dụ 4115.00), còn chia với MathContext precision=3 sẽ cho ba chữ số có nghĩa gần 4.12E+3.

### Không dùng số chữ số theo thói quen

Nếu đầu ra là tiền và cần 2 chữ số sau dấu thập phân, scale=2 có thể phù hợp, **sau khi** xác định quy tắc tính tiền và thời điểm làm tròn. Nhưng nếu đầu ra là tỷ lệ đo khoa học biến thiên từ 10^−12 tới 10^12, scale=2 thường không phù hợp. Phải lựa chọn chính sách trước khi gọi API.

## 8. divide(divisor, RoundingMode) — bẫy scale ẩn

Có một overload không có tham số scale:

~~~java
BigDecimal result = BigDecimal.ONE.divide(
        new BigDecimal("8"), RoundingMode.HALF_UP);
~~~

**Kết quả là 0**, không phải 0.13. Vì **BigDecimal.ONE.scale() = 0**, overload này tạo thương với **scale=0**, rồi làm tròn 0.125 thành số nguyên gần nhất theo HALF_UP (là 0).

So sánh:

~~~java
BigDecimal a = BigDecimal.ONE;           // scale 0
BigDecimal b = new BigDecimal("1.00");   // scale 2
BigDecimal eight = new BigDecimal("8");

System.out.println(a.divide(eight, RoundingMode.HALF_UP)); // 0
System.out.println(b.divide(eight, RoundingMode.HALF_UP)); // 0.13
~~~

Hai số bị chia **1** và **1.00** có cùng giá trị số học; **chỉ khác scale**, nhưng overload này cho kết quả đã làm tròn khác nhau.

Đây là lý do khi cần một số chữ số phần thập phân rõ ràng, nên dùng **divide(divisor, scale, roundingMode)** thay vì phụ thuộc vào scale của đầu vào.

## 9. divide(divisor, MathContext) — số chữ số có nghĩa

**MathContext** mang hai thông số:

- **precision**: số chữ số có nghĩa cho kết quả.
- **roundingMode**: quy tắc làm tròn nếu phải bỏ chữ số.

~~~java
import java.math.MathContext;
import java.math.RoundingMode;

MathContext mc = new MathContext(5, RoundingMode.HALF_UP);
BigDecimal q = BigDecimal.ONE.divide(new BigDecimal("3"), mc);
// 0.33333
~~~

**MathContext(5)** ở đây giữ 5 chữ số có nghĩa, không phải 5 chữ số sau dấu chấm cho mọi thương.

Ví dụ khác:

~~~java
BigDecimal q = new BigDecimal("12345")
        .divide(new BigDecimal("3"),
                new MathContext(3, RoundingMode.HALF_UP));
// 4.12E+3 (giá trị 4120)
~~~

Kết quả chính xác của 12345/3 là 4115; chỉ giữ ba chữ số có nghĩa và HALF_UP thì làm tròn thành 4120, biểu diễn khoa học **4.12E+3**.

### MathContext.UNLIMITED không giải quyết khai triển vô hạn

~~~java
// Vẫn ném ArithmeticException:
BigDecimal.ONE.divide(
        new BigDecimal("3"), MathContext.UNLIMITED);
~~~

**MathContext.UNLIMITED** có **precision=0**, nghĩa là yêu cầu tính exact cho các phương thức tương ứng. Nó **không** có nghĩa BigDecimal được phép sinh vô hạn chữ số rồi chạy mãi. Với 1/3 không kết thúc, Java ném ArithmeticException.

Ngược lại, **MathContext.DECIMAL32**, **DECIMAL64**, **DECIMAL128** đặt precision hữu hạn theo các cấu hình có sẵn; chúng là quy tắc tính toán thập phân, không tự biến BigDecimal thành một kiểu primitive cố định.

## 10. RoundingMode: chọn quy tắc khi phải làm tròn

Các lựa chọn quan trọng:

| RoundingMode | Ý nghĩa ngắn |
| --- | --- |
| **HALF_UP** | Trung điểm làm tròn xa 0 |
| **HALF_EVEN** | Trung điểm chọn chữ số cuối được giữ là chẵn |
| **HALF_DOWN** | Trung điểm làm tròn về 0 |
| **DOWN** | Khi cần bỏ phần dư, làm tròn về 0 |
| **UP** | Nếu có phần dư, làm tròn xa 0 |
| **FLOOR** | Về âm vô cùng |
| **CEILING** | Về dương vô cùng |
| **UNNECESSARY** | Không cho phép làm tròn; nếu cần sẽ ném ArithmeticException |

Sự khác biệt giữa HALF_UP và HALF_EVEN ở **đúng trung điểm**:

~~~java
BigDecimal one = BigDecimal.ONE;
BigDecimal eight = new BigDecimal("8");

BigDecimal halfEven = one.divide(
        eight, 2, RoundingMode.HALF_EVEN); // 0.12
BigDecimal halfUp = one.divide(
        eight, 2, RoundingMode.HALF_UP);   // 0.13
~~~

Thương chính xác 0.125 nằm chính giữa 0.12 và 0.13. HALF_EVEN chọn kết quả có chữ số cuối **2 (chẵn)**; HALF_UP chọn kết quả **xa 0**, tức 0.13. Với giá trị âm, quy tắc hướng về zero/xa zero phải được hiểu theo dấu, không bằng cách cắt chuỗi.

### UNNECESSARY là ràng buộc chất lượng dữ liệu

~~~java
BigDecimal exactAtScale = BigDecimal.ONE.divide(
        new BigDecimal("8"), 3, RoundingMode.UNNECESSARY);
// 0.125, không phải làm tròn

// Ném ArithmeticException vì cần làm tròn:
BigDecimal.ONE.divide(
        new BigDecimal("3"), 2, RoundingMode.UNNECESSARY);
~~~

Dùng UNNECESSARY khi nghiệp vụ **không chấp nhận mất chữ số** và muốn phát hiện trường hợp không đáp ứng được scale/precision đã định, thay vì vô tình làm tròn.

## 11. Chia cho zero, zero bị chia và các ngoại lệ

| Phép toán | Hành vi |
| --- | --- |
| **0 / 3** | Kết quả exact bằng 0 |
| **1 / 0** | ArithmeticException (division by zero) |
| **0 / 0** | ArithmeticException (undefined division) |
| **1 / 3** qua divide exact | ArithmeticException vì khai triển không kết thúc |
| **1 / 3** qua MathContext.UNLIMITED | ArithmeticException vì vẫn yêu cầu exact |
| **1 / 3** qua divide(scale=2, UNNECESSARY) | ArithmeticException vì cần làm tròn |
| **1 / 3** qua divide(scale=2, HALF_UP) | 0.33 |

BigDecimal **không có NaN và Infinity** như double. Phép chia zero không trả Double.POSITIVE_INFINITY hoặc NaN mà **ném ngoại lệ**.

Đối với mã production, đừng gom mọi ArithmeticException từ divide thành cùng thông báo. Thường nên kiểm tra **divisor.signum() == 0** trước để phân biệt đầu vào không hợp lệ. Những chính sách lỗi và làm tròn cần được thể hiện rõ với người gọi.

## 12. Ví dụ kiểm tra trước khả năng kết thúc thập phân

Khi không cần thử chia rồi bắt exception, ta có thể dựa trên tiêu chí mẫu số sau rút gọn chỉ gồm 2, 5.

Với hai BigDecimal hữu hạn:

1. Lấy **unscaledValue()** của số bị chia và số chia.
2. Với số chia bằng 0, từ chối.
3. Với tử bằng 0 và mẫu khác 0, thương là 0 và hữu hạn.
4. Rút gọn tỷ số hai unscaled integer bằng gcd.
5. Từ mẫu rút gọn, loại toàn bộ thừa số 2 và 5; nếu còn 1, thương có dạng thập phân hữu hạn.

Sự khác nhau về scale giữa hai BigDecimal chỉ tương ứng việc nhân với lũy thừa của 10, nên không đưa thêm thừa số nguyên tố ngoài 2 và 5.

Phương thức sau chỉ minh họa **tính kết thúc thập phân**, không tính thương:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;

static boolean hasTerminatingDecimal(BigDecimal a, BigDecimal b) {
    if (b.signum() == 0) {
        throw new ArithmeticException("Division by zero");
    }
    if (a.signum() == 0) {
        return true;
    }

    BigInteger numerator = a.unscaledValue().abs();
    BigInteger denominator = b.unscaledValue().abs();

    denominator = denominator.divide(denominator.gcd(numerator));

    BigInteger two = BigInteger.valueOf(2);
    BigInteger five = BigInteger.valueOf(5);

    while (denominator.mod(two).signum() == 0) {
        denominator = denominator.divide(two);
    }
    while (denominator.mod(five).signum() == 0) {
        denominator = denominator.divide(five);
    }
    return denominator.equals(BigInteger.ONE);
}
~~~

Đây là **phương thức phải đặt bên trong class** và cần import java.math.BigDecimal/BigInteger. Nó trả:

- **true** với 1/40, 3/6, 0/3.
- **false** với 1/6 hoặc 1/3.
- **ArithmeticException** với số chia 0.

**Giới hạn thực tế:** kết luận toán học về khai triển hữu hạn **không bảo đảm** phép chia thực tế luôn hoàn tất với bộ nhớ và scale hữu hạn của Java nếu đầu vào quá lớn, scale cực trị hoặc vượt giới hạn triển khai. Với ứng dụng thông thường, việc gọi **divide** trực tiếp vẫn đơn giản hơn; tự triển khai bộ phân tích mẫu chỉ cần thiết khi tiêu chí là một phần nghiệp vụ hoặc muốn giải thích lý do làm tròn.

## 13. Phân biệt làm tròn để tính và làm tròn để hiển thị

Một mẫu dữ liệu có thể có nhiều giá trị cần giữ khác nhau:

- **Số gốc**: tỷ lệ, giá, số đo hoặc số tiền chưa làm tròn.
- **Kết quả tính toán**: giá trị sau chia theo một chính sách scale/precision.
- **Chuỗi hiển thị**: qua NumberFormat theo Locale, có thể thêm dấu nhóm và làm tròn để người dùng đọc.

Ví dụ một phép tính phí cần chính sách chia và làm tròn theo nghiệp vụ:

~~~java
BigDecimal total = new BigDecimal("10.00");
BigDecimal people = new BigDecimal("3");

BigDecimal perPerson = total.divide(
        people, 2, RoundingMode.HALF_UP);
// 3.33
~~~

**3.33 × 3 = 9.99**, không bằng 10.00; còn dư 0.01. Đó là hệ quả của chia không kết thúc và làm tròn theo đơn vị nhỏ nhất, không phải lỗi BigDecimal. Nếu cần **phân bổ chính xác 10.00**, phải có quy tắc phân phối số dư (ví dụ một người nhận thêm 0.01) và kiểm tra tổng cuối cùng.

Không tự động đổi sang double để tránh exception hoặc để dễ hiển thị. Double có thể mang thêm sai số nhị phân và không tự giải quyết quy tắc phân bổ/làm tròn.

## 14. Phân số chính xác nếu không được phép làm tròn

Nếu yêu cầu toán học là **giữ đúng 1/3**, một BigDecimal hữu hạn không đủ. Có thể lưu tử và mẫu bằng BigInteger, rút gọn theo gcd:

~~~java
import java.math.BigInteger;

BigInteger numerator = BigInteger.ONE;
BigInteger denominator = BigInteger.valueOf(3);

BigInteger gcd = numerator.gcd(denominator);
numerator = numerator.divide(gcd);
denominator = denominator.divide(gcd);
// Giá trị hữu tỉ giữ dưới dạng 1/3
~~~

Đoạn này minh họa cách biểu diễn hai thành phần, **không định nghĩa một lớp phân số đầy đủ**. Với hệ thống thực, phải chuẩn hóa dấu mẫu (thường dương), từ chối mẫu 0, xử lý toán học phân số và giới hạn tài nguyên.

Chỉ khi cần xuất thập phân cho người xem hoặc gửi một API yêu cầu decimal hữu hạn mới áp dụng chính sách làm tròn rõ ràng. Nhờ vậy, **độ chính xác của mô hình** không bị mất sớm.

## 15. Checklist lựa chọn overload

Hãy trả lời các câu hỏi theo thứ tự:

1. **Có bắt buộc exact không?** Nếu có, divide(divisor); nếu thương không hữu hạn thì từ chối hoặc chọn biểu diễn phân số.
2. **Kết quả có độ rộng cố định theo phần thập phân không?** Nếu có, divide(divisor, scale, mode).
3. **Kết quả chỉ yêu cầu N chữ số có nghĩa?** Nếu có, divide(divisor, MathContext).
4. **Bạn có chủ đích giữ scale của dividend không?** Chỉ khi có mới chọn divide(divisor, mode).
5. **Có chính sách làm tròn nào được chấp nhận?** Xác định HALF_EVEN, HALF_UP hoặc mode khác từ hợp đồng.
6. **Chia zero hoặc số quá lớn thì sao?** Kiểm tra miền dữ liệu, scale, precision, giới hạn tài nguyên và cách báo lỗi.
7. **Có cần giữ phân số exact qua nhiều phép toán không?** Nếu có, dùng mô hình tử/mẫu, tránh làm tròn trung gian.

Sự lựa chọn tốt nhất là **phù hợp hợp đồng dữ liệu**, không phải API có chữ ký ngắn nhất.

## 16. Những sai lầm dễ mắc

| Sai lầm | Hệ quả | Cách làm đúng |
| --- | --- | --- |
| Gọi BigDecimal.ONE.divide(3) rồi nghĩ Java tự cho 0.333... | ArithmeticException | Chọn rounding hoặc biểu diễn phân số |
| Coi ArithmeticException luôn là chia cho zero | Bỏ qua lỗi khai triển không kết thúc/rounding forbidden | Phân loại điều kiện lỗi |
| Nghĩ BigDecimal có thể lưu vô hạn chữ số | Bản thân BigDecimal là biểu diễn hữu hạn | Chọn biểu diễn hữu tỉ khi cần exact 1/3 |
| Xét mẫu 6 để kết luận 3/6 tuần hoàn | Chưa rút gọn | Rút gọn bằng gcd trước |
| Cho rằng mẫu có cả 2 và 5 thì luôn hữu hạn | Mẫu có thể còn 3 hoặc 7 | Mẫu rút gọn chỉ được có thừa số 2,5 |
| Dùng divide(divisor, HALF_UP) nhưng tưởng tự giữ 2 chữ số | Overload giữ scale của dividend | Dùng divide(divisor,2,HALF_UP) |
| Nhầm MathContext(5) là 5 chữ số thập phân | Precision là 5 chữ số có nghĩa | Dùng scale cho phần lẻ |
| Chọn MathContext.UNLIMITED để tránh lỗi 1/3 | Vẫn yêu cầu exact và ném exception | Precision hữu hạn + rounding |
| Dùng RoundingMode.UNNECESSARY khi thương không vừa scale | ArithmeticException | Tăng scale hoặc cho rounding nếu hợp đồng cho phép |
| Dùng double làm trung gian trước khi chia BigDecimal | Có thể mất độ chính xác thập phân đầu vào | Khởi tạo từ String hoặc giá trị nguyên |
| Cứ làm tròn từng bước rồi cho rằng tổng bằng số gốc | Có thể lệch do số dư | Thiết kế quy tắc phân bổ số dư |
| Dùng NumberFormat làm bước tính chia | Chuỗi UI không phải số gốc | Tính bằng BigDecimal, format riêng |
| Bỏ qua độ lớn input hoặc precision | Tốn CPU/heap, có thể vượt giới hạn triển khai | Ràng buộc đầu vào và tài nguyên |


## 17. Chương trình Java 17+ chạy độc lập

Tạo tệp **DecimalDivideDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

public class DecimalDivideDemo {
    static boolean hasTerminatingDecimal(BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        if (a.signum() == 0) {
            return true;
        }

        BigInteger numerator = a.unscaledValue().abs();
        BigInteger denominator = b.unscaledValue().abs();
        denominator = denominator.divide(denominator.gcd(numerator));

        BigInteger two = BigInteger.valueOf(2);
        BigInteger five = BigInteger.valueOf(5);

        while (denominator.mod(two).signum() == 0) {
            denominator = denominator.divide(two);
        }
        while (denominator.mod(five).signum() == 0) {
            denominator = denominator.divide(five);
        }
        return denominator.equals(BigInteger.ONE);
    }

    public static void main(String[] args) {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal three = new BigDecimal("3");
        BigDecimal eight = new BigDecimal("8");

        System.out.println("1 / 8 exact: " + one.divide(eight));
        System.out.println("1 / 40 exact: "
                + one.divide(new BigDecimal("40")));
        System.out.println("10.00 / 4 exact: "
                + new BigDecimal("10.00")
                        .divide(new BigDecimal("4")));

        try {
            one.divide(three);
        } catch (ArithmeticException ex) {
            System.out.println("1 / 3 exact: ArithmeticException");
        }

        try {
            one.divide(three, MathContext.UNLIMITED);
        } catch (ArithmeticException ex) {
            System.out.println("1 / 3 UNLIMITED: ArithmeticException");
        }

        System.out.println("1 / 3 4dp HALF_UP: "
                + one.divide(three, 4, RoundingMode.HALF_UP));
        System.out.println("2 / 3 2dp HALF_UP: "
                + new BigDecimal("2").divide(
                        three, 2, RoundingMode.HALF_UP));
        System.out.println("1 / 3 5sig HALF_UP: "
                + one.divide(three,
                        new MathContext(5, RoundingMode.HALF_UP)));
        System.out.println("12345 / 3 3sig HALF_UP: "
                + new BigDecimal("12345").divide(
                        three, new MathContext(3, RoundingMode.HALF_UP)));

        System.out.println("1 / 8 2dp HALF_EVEN: "
                + one.divide(eight, 2, RoundingMode.HALF_EVEN));
        System.out.println("1 / 8 2dp HALF_UP: "
                + one.divide(eight, 2, RoundingMode.HALF_UP));

        System.out.println("1 / 8 HALF_UP no scale: "
                + one.divide(eight, RoundingMode.HALF_UP));
        System.out.println("1.00 / 8 HALF_UP no scale: "
                + new BigDecimal("1.00")
                        .divide(eight, RoundingMode.HALF_UP));

        try {
            one.divide(three, 2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            System.out.println("1 / 3 UNNECESSARY: ArithmeticException");
        }

        try {
            one.divide(BigDecimal.ZERO);
        } catch (ArithmeticException ex) {
            System.out.println("1 / 0: ArithmeticException");
        }

        System.out.println("0 / 3 exact: "
                + BigDecimal.ZERO.divide(three));

        System.out.println("1/40 terminates: "
                + hasTerminatingDecimal(one, new BigDecimal("40")));
        System.out.println("1/6 terminates: "
                + hasTerminatingDecimal(one, new BigDecimal("6")));
        System.out.println("3/6 terminates: "
                + hasTerminatingDecimal(three, new BigDecimal("6")));

        System.out.println("1/3 rounded input unchanged: " + one);
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac --release 17 DecimalDivideDemo.java
java DecimalDivideDemo
~~~

Stdout đã đối chiếu bằng Java:

~~~text
1 / 8 exact: 0.125
1 / 40 exact: 0.025
10.00 / 4 exact: 2.50
1 / 3 exact: ArithmeticException
1 / 3 UNLIMITED: ArithmeticException
1 / 3 4dp HALF_UP: 0.3333
2 / 3 2dp HALF_UP: 0.67
1 / 3 5sig HALF_UP: 0.33333
12345 / 3 3sig HALF_UP: 4.12E+3
1 / 8 2dp HALF_EVEN: 0.12
1 / 8 2dp HALF_UP: 0.13
1 / 8 HALF_UP no scale: 0
1.00 / 8 HALF_UP no scale: 0.13
1 / 3 UNNECESSARY: ArithmeticException
1 / 0: ArithmeticException
0 / 3 exact: 0
1/40 terminates: true
1/6 terminates: false
3/6 terminates: true
1/3 rounded input unchanged: 1
~~~

### Diễn giải kết quả

1. **1/8 và 1/40** có thương thập phân hữu hạn, nên divide exact trả đúng 0.125 và 0.025.
2. **10.00/4 = 2.50** thể hiện BigDecimal giữ scale theo quy tắc preferred scale thay vì tự bỏ zero tận cùng.
3. **1/3** không có thập phân hữu hạn, nên cả divide exact và MathContext.UNLIMITED đều ném ArithmeticException.
4. **4dp** chỉ 4 chữ số phần thập phân: 0.3333. **5sig** chỉ 5 chữ số có nghĩa: 0.33333.
5. **12345/3** chính xác là 4115, còn MathContext(3, HALF_UP) làm tròn còn 4120 và hiển thị 4.12E+3.
6. **HALF_EVEN và HALF_UP** ở 0.125 với scale=2 cho kết quả 0.12 và 0.13, dù đầu vào giống nhau.
7. **divide(divisor, RoundingMode)** không truyền scale dùng **scale của dividend**: từ 1 (scale 0) cho kết quả 0, nhưng 1.00 (scale 2) cho 0.13.
8. **UNNECESSARY** từ chối trường hợp cần bỏ chữ số; phép chia cho zero được báo lỗi riêng.
9. Hàm kiểm tra thập phân hữu hạn trả true với 1/40, false với 1/6 và true với **3/6 sau rút gọn**.
10. Toán hạng BigDecimal.ONE không bị sửa bởi những phép chia và rounding; đây là tính bất biến.

## 18. Câu hỏi và bài tập

**Bài 1.** Khi gọi BigDecimal.ONE.divide(new BigDecimal("3")), tại sao nhận ArithmeticException?

*Đáp án:* Thương 1/3 là thập phân vô hạn tuần hoàn, không thể biểu diễn chính xác bằng BigDecimal hữu hạn và overload này không có chính sách làm tròn.

**Bài 2.** Vì sao 3/6 có thể chia chính xác nhưng 1/6 thì không?

*Đáp án:* Rút gọn 3/6 thành 1/2, mẫu chỉ có thừa số 2. Mẫu 6 của 1/6 chứa 3 nên khai triển thập phân không kết thúc.

**Bài 3.** Mẫu số rút gọn 2³ × 5² có thể tạo thập phân hữu hạn không?

*Đáp án:* Có. Mẫu chỉ chứa 2 và 5, có thể nhân để thành lũy thừa của 10.

**Bài 4.** Tại sao MathContext.UNLIMITED vẫn ném exception khi chia 1/3?

*Đáp án:* Precision=0 có nghĩa yêu cầu tính exact, không phải tạo một chuỗi vô hạn chữ số.

**Bài 5.** Muốn 2/3 thành 0.67 với hai chữ số sau dấu thập phân, gọi phương thức nào?

*Đáp án:* **new BigDecimal("2").divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP)**.

**Bài 6.** Muốn chia một số lớn chỉ giữ 4 chữ số có nghĩa, nên dùng scale hay MathContext?

*Đáp án:* **MathContext(4, mode)**. Scale là số vị trí phần thập phân, không phải tổng số chữ số có nghĩa.

**Bài 7.** Tại sao BigDecimal.ONE.divide(new BigDecimal("8"), RoundingMode.HALF_UP) cho 0?

*Đáp án:* Overload này giữ scale của dividend là 0, nên 0.125 được làm tròn thành số nguyên 0.

**Bài 8.** Tại 1/8, làm tròn còn hai chữ số sau dấu với HALF_EVEN và HALF_UP khác nhau thế nào?

*Đáp án:* 0.125 nằm giữa 0.12 và 0.13; HALF_EVEN chọn 0.12, HALF_UP chọn 0.13.

**Bài 9.** Cần giữ đúng phân số 1/3 cho nhiều phép toán trước khi xuất kết quả. Nên làm gì?

*Đáp án:* Biểu diễn dưới dạng tử/mẫu số nguyên (ví dụ BigInteger) đã chuẩn hóa, không làm tròn sang BigDecimal từ đầu.

**Bài 10.** Chia tổng 10.00 đều cho ba người, mỗi người giữ 2 chữ số tiền, vì sao 3.33×3 không bằng 10.00?

*Đáp án:* Do làm tròn thương không kết thúc. Nếu cần bảo toàn tổng, phải xác định chính sách phân bổ phần dư 0.01.

**Bài 11.** Có thể dùng BigDecimal.divide cho số chia 0 và mong nhận Infinity không?

*Đáp án:* Không. BigDecimal không có Infinity/NaN; phép chia zero ném ArithmeticException.

## 19. Tài liệu tham khảo chính thức

- [BigDecimal Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): các overload divide, preferred scale, exact arithmetic, ArithmeticException và biểu diễn thập phân.
- [MathContext Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html): precision, roundingMode và UNLIMITED.
- [RoundingMode Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): các quy tắc làm tròn số dương và âm.
- [BigInteger Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigInteger.html): gcd và biểu diễn tử/mẫu của phân số chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-003">divide với scale, RoundingMode hoặc MathContext</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.divide: chọn scale, RoundingMode hay MathContext trong Java

Phép chia số thập phân không phải lúc nào cũng có kết quả thập phân hữu hạn. Ví dụ \(1/8=0.125\) biểu diễn decimal chính xác, nhưng \(1/3=0.3333\ldots\) kéo dài vô hạn. Với `java.math.BigDecimal`, **cách gọi `divide` quyết định khi nào được làm tròn, scale của kết quả và mức chính xác được phép**.

Java có nhiều overload của `divide`, nhưng bốn cách quan trọng nhất khi học:

```java
BigDecimal a = BigDecimal.ONE;
BigDecimal b = BigDecimal.valueOf(3);

// a.divide(b);                            // chính xác; 1/3 gây ArithmeticException
BigDecimal x = a.divide(b, 2, RoundingMode.HALF_UP); // 0.33, scale 2
BigDecimal y = a.divide(b, RoundingMode.HALF_UP);    // 0, scale của a bằng 0
BigDecimal z = a.divide(b, new MathContext(3, RoundingMode.HALF_UP)); // 0.333
```

Những đoạn ngắn trong bài giả định đã `import java.math.BigDecimal;`, `MathContext` và `RoundingMode` và được đặt trong một method/JShell. Chương trình Java 17+ đầy đủ, chạy được, nằm ở phần 12.

**Quy tắc tổng quát:** Chọn overload theo **hợp đồng kết quả**: cần chính xác không làm tròn, cần số chữ số **sau dấu thập phân**, muốn giữ **scale của số bị chia**, hay cần số **chữ số có nghĩa**.

## 1. Nền tảng: value, scale và precision của BigDecimal

`BigDecimal` mô hình hóa một số decimal bằng:

\[
\text{value}=\text{unscaledValue}\times10^{-\text{scale}}
\]

Ví dụ `new BigDecimal("12.3400")` có unscaled value `123400`, scale 4 và precision 6. Hai đại lượng dễ nhầm:

- **Scale**: vị trí thập phân trong biểu diễn. Khi không âm, thường là số chữ số sau dấu chấm; **scale cũng có thể âm**.
- **Precision**: tổng số chữ số có nghĩa trong dạng thập phân của giá trị không scale, không phải số chữ số sau dấu chấm.

```java
BigDecimal number = new BigDecimal("12.3400");

System.out.println(number.unscaledValue()); // 123400
System.out.println(number.scale());         // 4
System.out.println(number.precision());     // 6
```

Trong phép chia, **scale và precision là hai loại giới hạn khác nhau**. Yêu cầu “giữ hai chữ số thập phân” thường cần `scale = 2`; còn yêu cầu “giữ ba chữ số có nghĩa” thường cần `MathContext(3, mode)`.

`BigDecimal` là **immutable**: `divide` trả về một giá trị mới, không thay đổi số bị chia hay số chia.

## 2. Tổng quan những overload quan trọng

| Cách gọi | Quy tắc kết quả | Khi nào chọn |
| --- | --- | --- |
| `a.divide(b)` | Thương **chính xác**, không chấp nhận khai triển decimal vô hạn | Khi toán học bảo đảm phép chia có dạng decimal hữu hạn |
| `a.divide(b, scale, mode)` | Thương có **scale được chỉ định**, làm tròn theo mode nếu cần | Tiền, đơn vị đo, giá trị cần số chữ số sau dấu thập phân |
| `a.divide(b, mode)` | Thương có **scale bằng `a.scale()`**, làm tròn nếu cần | Khi có chủ đích duy trì scale của số bị chia |
| `a.divide(b, context)` | Làm tròn theo **precision** và `RoundingMode` của `MathContext` | Tính toán với ngân sách chữ số có nghĩa |

Còn có một overload cũ nhận `int roundingMode`/hằng số legacy; không nên sử dụng trong code mới. Các hằng trong `RoundingMode` tạo API rõ kiểu và tránh số magic.

**Điểm mấu chốt:** `divide(b, RoundingMode.HALF_UP)` **không ngầm chọn scale = 2**. Nó dùng **scale của `a`**. Nếu `a == BigDecimal.ONE` thì scale bằng 0, nên `1/3` có thể thành **`0`**. Đây là lỗi đặc biệt nguy hiểm khi lập trình viên tưởng mode chỉ quyết định cách chọn chữ số cuối mà không quan tâm scale.

## 3. Khi nào dùng `divide(divisor)` thay vì overload làm tròn?

Phép `divide(divisor)` yêu cầu **thương decimal chính xác**. Ví dụ `BigDecimal.ONE.divide(new BigDecimal("8"))` trả `0.125`, còn chia cho `3` ném `ArithmeticException` vì thương có vô hạn chữ số. Sau khi rút gọn phân số, thương hữu hạn chỉ khi mẫu gồm các thừa số nguyên tố 2 và 5; cơ chế và chứng minh thuộc bài học *divide chính xác và số thập phân vô hạn tuần hoàn*.

Ở đây, điều cần quyết định là **chính sách đầu ra**: nếu nghiệp vụ yêu cầu exact thì chấp nhận kết quả hoặc báo lỗi; nếu cho phép làm tròn, hãy chọn rõ scale hay precision trong overload tương ứng. Preferred scale của divide exact là `a.scale() - b.scale()`, nhưng scale thực tế có thể lớn hơn để biểu diễn hết thương.

## 4. divide(divisor, scale, RoundingMode): đặt scale chính xác

Đây là lựa chọn trực tiếp nếu kết quả cần **N chữ số sau dấu chấm decimal**:

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = BigDecimal.valueOf(3);

BigDecimal rounded2 =
        one.divide(three, 2, RoundingMode.HALF_UP);
BigDecimal rounded5 =
        one.divide(three, 5, RoundingMode.HALF_UP);

System.out.println(rounded2); // 0.33
System.out.println(rounded5); // 0.33333
```

Từng tham số:

- `divisor`: số chia, không được bằng zero.
- `scale`: scale yêu cầu của **thương**, kiểu `int`; có thể bằng 0 hoặc âm.
- `roundingMode`: cách chọn giá trị có scale đó nếu thương chính xác không vừa biểu diễn.

Ngay cả khi thương **đã hữu hạn**, ép scale nhỏ hơn có thể buộc phải làm tròn:

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal eight = BigDecimal.valueOf(8);

System.out.println(one.divide(eight, 2, RoundingMode.HALF_UP));   // 0.13
System.out.println(one.divide(eight, 2, RoundingMode.HALF_EVEN)); // 0.12
```

`1/8 = 0.125` là điểm giữa khi làm tròn đến hai chữ số sau dấu thập phân. `HALF_UP` chọn 0.13; `HALF_EVEN` chọn 0.12 vì chữ số cuối là 2 (chẵn).

### Scale 0 và scale âm

```java
System.out.println(
        BigDecimal.ONE.divide(BigDecimal.valueOf(3),
                0, RoundingMode.HALF_UP)); // 0

System.out.println(
        new BigDecimal("123").divide(BigDecimal.ONE,
                -1, RoundingMode.HALF_UP)); // 1.2E+2
```

Scale 0 tạo thương ở đơn vị nguyên; scale -1 tạo giá trị theo bội số của 10. `new BigDecimal("123") / 1` chính xác là 123, nhưng yêu cầu scale -1 khiến kết quả làm tròn thành 120 và có thể được in bằng dạng khoa học `1.2E+2`.

**Không sử dụng `setScale` mà không nghĩ đến rounding:** nếu đang cần giảm scale và bị mất chữ số có nghĩa, `setScale(newScale)` không có mode có thể ném `ArithmeticException`. Overload divide có mode đặt rõ chính sách ngay từ đầu.

## 5. divide(divisor, RoundingMode): lấy scale từ số bị chia

Đây là overload dễ bị hiểu nhầm:

```java
BigDecimal a = new BigDecimal("1.00");
BigDecimal b = BigDecimal.valueOf(3);

System.out.println(a.divide(b, RoundingMode.HALF_UP)); // 0.33
```

`a.scale() == 2`, nên thương được làm tròn về scale 2. Nhưng khi `a` được tạo từ `BigDecimal.ONE`:

```java
BigDecimal a = BigDecimal.ONE; // scale = 0
BigDecimal b = BigDecimal.valueOf(3);

System.out.println(a.divide(b, RoundingMode.HALF_UP)); // 0
```

Hai lời gọi dùng **cùng phép chia toán học 1/3 và cùng rounding mode**, nhưng khác scale của số bị chia. Do đó khác kết quả.

Tình huống nguy hiểm khác:

```java
BigDecimal amount = new BigDecimal("100");   // scale 0
BigDecimal factor = new BigDecimal("3");
BigDecimal result = amount.divide(factor, RoundingMode.HALF_UP);
// result == 33, không phải 33.33
```

Nếu hợp đồng yêu cầu 2 chữ số sau dấu chấm, hãy chỉ rõ **`divide(factor, 2, RoundingMode.HALF_UP)`**. Đừng dùng overload 2 tham số rồi dựa vào scale đầu vào tình cờ bằng 2.

## 6. divide(divisor, MathContext): giới hạn chữ số có nghĩa

`MathContext` chứa:

- `precision`: số chữ số có nghĩa cần giữ (`0` biểu thị không giới hạn).
- `roundingMode`: quy tắc làm tròn khi precision không đủ.

```java
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

BigDecimal one = BigDecimal.ONE;
BigDecimal three = BigDecimal.valueOf(3);
BigDecimal result = one.divide(three, mc);

System.out.println(result); // 0.333
```

**Không được đồng nhất `precision = 3` với `scale = 3`**. Cùng context áp dụng cho một thương lớn:

```java
BigDecimal big = new BigDecimal("12345");
BigDecimal seven = BigDecimal.valueOf(7);
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

System.out.println(big.divide(seven, mc)); // 1.76E+3
```

Thương toán học xấp xỉ 1763.571..., làm tròn **ba chữ số có nghĩa** thành 1760, được in `1.76E+3`. Scale của kết quả là **-1**, không phải 3.

Cùng bài toán khác với `scale = 2`:

```java
BigDecimal a = new BigDecimal("123.45");
BigDecimal b = BigDecimal.valueOf(2);

System.out.println(
        a.divide(b, 2, RoundingMode.HALF_UP)); // 61.73

System.out.println(
        a.divide(b, new MathContext(2, RoundingMode.HALF_UP))); // 62
```

`123.45/2 = 61.725`, nên:

- **Scale 2** → 61.73 (hai chữ số sau dấu thập phân).
- **Precision 2** → 62 (hai chữ số có nghĩa).

### MathContext.UNLIMITED và precision = 0

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal eight = BigDecimal.valueOf(8);

System.out.println(
        one.divide(eight, MathContext.UNLIMITED)); // 0.125
```

`MathContext.UNLIMITED` đặt precision bằng 0; đối với divide, nó yêu cầu thương **chính xác**, tương tự `divide(divisor)` trong trường hợp thông thường. Nó **không** có nghĩa tính tùy ý nhiều chữ số của `1/3` mà không giới hạn bộ nhớ.

```java
// one.divide(BigDecimal.valueOf(3), MathContext.UNLIMITED);
// -> ArithmeticException, vì 1/3 có decimal vô hạn
```

Nếu đặt precision hữu hạn nhưng `RoundingMode.UNNECESSARY` và thương cần làm tròn, Java cũng có thể ném `ArithmeticException`. Phải chọn mode chấp nhận làm tròn khi bài toán yêu cầu xấp xỉ.

## 7. RoundingMode: phân biệt điểm giữa, dấu và hướng làm tròn

`RoundingMode` quyết định cách xử lý khi buộc phải bỏ chữ số, **không** tự quyết định nên làm tròn ở bước nào.

| Mode | Ý nghĩa | `+1/8` tại scale 2 | `-1/8` tại scale 2 |
| --- | --- | --- | --- |
| `UP` | Ra xa zero nếu có phần bị loại | `0.13` | `-0.13` |
| `DOWN` | Về zero | `0.12` | `-0.12` |
| `CEILING` | Về dương vô cực | `0.13` | `-0.12` |
| `FLOOR` | Về âm vô cực | `0.12` | `-0.13` |
| `HALF_UP` | Điểm giữa thì ra xa zero | `0.13` | `-0.13` |
| `HALF_DOWN` | Điểm giữa thì về zero | `0.12` | `-0.12` |
| `HALF_EVEN` | Điểm giữa thì chọn chữ số cuối chẵn | `0.12` | `-0.12` |
| `UNNECESSARY` | Không cho phép làm tròn có mất thông tin | `ArithmeticException` | `ArithmeticException` |

Ví dụ:

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal eight = BigDecimal.valueOf(8);

System.out.println(
        one.divide(eight, 2, RoundingMode.HALF_UP));   // 0.13
System.out.println(
        one.divide(eight, 2, RoundingMode.HALF_EVEN)); // 0.12
System.out.println(
        one.negate().divide(eight, 2, RoundingMode.HALF_UP)); // -0.13
```

Không mặc định `HALF_UP` đúng cho mọi bài toán tiền tệ hoặc `HALF_EVEN` đúng cho mọi phép thống kê. Hãy tuân thủ hợp đồng của hệ thống/ngành/định dạng đầu ra.

### Khi muốn ngăn làm tròn âm thầm

```java
try {
    BigDecimal exactTwoPlaces = BigDecimal.ONE.divide(
            BigDecimal.valueOf(3), 2, RoundingMode.UNNECESSARY);
} catch (ArithmeticException ex) {
    // 1/3 không thể lưu chính xác ở scale 2
}
```

`UNNECESSARY` là công cụ để **phát hiện yêu cầu làm tròn**. Nó cũng có thể phù hợp khi hợp đồng yêu cầu từ chối giao dịch thay vì tự cắt dữ liệu.

## 8. Chia cho 0: không giống phép chia double

Một `BigDecimal` không có giá trị đặc biệt `NaN` hoặc `Infinity`. Với `BigDecimal.divide`, số chia bằng 0 gây **`ArithmeticException`**:

```java
BigDecimal a = BigDecimal.ONE;
BigDecimal zero = BigDecimal.ZERO;

// a.divide(zero, 2, RoundingMode.HALF_UP);
// -> ArithmeticException: Division by zero
```

Khác với primitive `double`:

```java
System.out.println(1.0 / 0.0); // Infinity
System.out.println(0.0 / 0.0); // NaN
```

Không viết code dùng `BigDecimal` rồi kỳ vọng `divide` có thể trả Infinity. Trong Java, phải có **hợp đồng xử lý chia cho 0**, ví dụ validate ngay đầu vào và cung cấp thông báo nghiệp vụ.

```java
static BigDecimal safeDivide(BigDecimal a, BigDecimal b,
                             int scale, RoundingMode mode) {
    if (b.signum() == 0) {
        throw new IllegalArgumentException("Divisor must be nonzero");
    }
    return a.divide(b, scale, mode);
}
```

Đây là helper minh họa cần đặt **trong một class**, không phải chương trình độc lập. `signum() == 0` kiểm tra zero theo giá trị, kể cả `0.00` có scale khác 0. Có thể validate null qua `Objects.requireNonNull` nếu đầu vào có thể null.

**Đừng bắt tất cả `ArithmeticException` rồi coi là chia cho 0**. Với overload divide chính xác hoặc `UNNECESSARY`, exception còn có thể phát sinh vì thương không biểu diễn chính xác ở precision/scale yêu cầu.

## 9. Chia chính xác không phải lúc nào cũng giữ scale mong muốn

Khi chia một giá trị có trailing zeros, kết quả của các overload có thể khác ở **scale** dù **giá trị toán học bằng nhau**:

```java
BigDecimal a = new BigDecimal("2.000");
BigDecimal b = new BigDecimal("4");

BigDecimal exact = a.divide(b);                    // 0.500
BigDecimal sameScale = a.divide(b, RoundingMode.HALF_UP); // 0.500
BigDecimal scale2 = a.divide(b, 2, RoundingMode.HALF_UP); // 0.50
```

Một vấn đề là `BigDecimal.equals` so sánh **giá trị và scale**, còn `compareTo` chỉ so sánh **giá trị số**:

```java
System.out.println(new BigDecimal("0.50")
        .equals(new BigDecimal("0.500")));    // false
System.out.println(new BigDecimal("0.50")
        .compareTo(new BigDecimal("0.500"))); // 0
```

Nếu dùng `HashSet<BigDecimal>` hoặc `HashMap<BigDecimal, ...>`, scale khác nhau có thể biến thành **hai khóa khác nhau**. Không nên bỏ qua scale sau khi chia, đặc biệt khi hệ thống dùng decimal biểu diễn đơn vị đo, tỷ lệ hoặc quy tắc làm tròn.

Một cách ổn định cho dữ liệu chốt ở scale 2 là **chỉ định scale 2 ngay trong phép chia**, chứ không chọn scale từ `a` tình cờ.

## 10. Chọn precision làm việc và thời điểm chốt kết quả

Một thuật toán nhiều bước thường cần **precision làm việc** lớn hơn precision dữ liệu được ghi nhận. Ví dụ chia ba khoản, cộng dồn, nhân tỷ lệ hay phân bổ chi phí.

Không tự động làm tròn về hai chữ số sau mỗi phép toán chỉ vì mục tiêu cuối là hai chữ số. Kết quả có thể phụ thuộc việc bạn làm tròn ở đâu.

Giả sử hai thành phần chính xác đều là `0.015`:

```text
Làm tròn từng thành phần theo HALF_UP ở scale 2:
0.015 -> 0.02
0.015 -> 0.02
Tổng = 0.04

Cộng rồi làm tròn tổng:
0.015 + 0.015 = 0.030
0.030 -> 0.03
Tổng = 0.03
```

**Cả hai có thể đúng trong những hợp đồng khác nhau**:

- Nếu từng thành phần là dòng giao dịch độc lập phải chốt ở hai chữ số, tổng đúng có thể là 0.04.
- Nếu đầu vào là các thành phần trung gian của một khoản duy nhất cần chốt một lần, kết quả có thể là 0.03.

Trong một luồng BigDecimal, nên xác định trước: đầu vào dạng decimal chính xác hay gần đúng; làm tròn ở đơn giá, dòng hay tổng; scale cần giữ; rounding mode; cách xử lý phần dư và cách lưu giá trị đã chốt.

### Precision của MathContext không thay thế hợp đồng về scale

```java
MathContext working = new MathContext(12, RoundingMode.HALF_EVEN);
BigDecimal approximateRatio =
        BigDecimal.ONE.divide(BigDecimal.valueOf(7), working);
// Có 12 chữ số có nghĩa; kết quả không mang mặc định scale 2.

BigDecimal committed =
        approximateRatio.setScale(2, RoundingMode.HALF_UP);
// Phải có quyết định rõ rằng đây chính là bước chốt ở scale 2.
```

Đừng mặc định `MathContext` có thể “làm mọi phép chia chính xác”: đó là **xấp xỉ có giới hạn chữ số có nghĩa** khi thương decimal vô hạn. Khi phải bảo toàn chính xác phân số ban đầu, mô hình cặp tử số/mẫu số hoặc một kiểu phân số hữu tỉ riêng có thể phù hợp hơn việc ép mọi kết quả sang BigDecimal hữu hạn.

## 11. Những API liên quan cần biết nhưng không nhầm với divide có rounding

`BigDecimal` còn có:

- `divideToIntegralValue(divisor)`: lấy phần nguyên của thương theo hướng về 0, trình bày dưới dạng BigDecimal.
- `remainder(divisor)`: lấy phần dư theo quy tắc BigDecimal; không phải phép `%` primitive dùng trên đối tượng.
- `divideAndRemainder(divisor)`: nhận đồng thời thương nguyên và phần dư.

Ví dụ:

```java
BigDecimal ten = new BigDecimal("10");
BigDecimal three = new BigDecimal("3");

System.out.println(ten.divideToIntegralValue(three)); // 3
System.out.println(ten.remainder(three));             // 1
```

**Không dùng `divideToIntegralValue` để thay cho `divide(..., 0, mode)` một cách bừa bãi**: phép thứ nhất lấy phần nguyên về 0, trong khi divide với scale 0 có thể làm tròn theo mode như `HALF_UP` hoặc `CEILING`.

Nếu mục tiêu là hiển thị hai chữ số, việc dùng `String.format("%.2f", someDouble)` cũng **không tương đương** với chia BigDecimal chính xác rồi chốt theo `RoundingMode` của nghiệp vụ. Định dạng chuỗi chỉ là bước biểu diễn văn bản và có thể mang các hạn chế của kiểu đầu vào.

## 12. Chương trình Java 17+ chạy độc lập

Tạo file **`BigDecimalDivideDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalDivideDemo {
    static void showError(String label, Runnable operation) {
        try {
            operation.run();
            System.out.println(label + "=NOT_THROWN");
        } catch (ArithmeticException ex) {
            System.out.println(label + "=ArithmeticException");
        }
    }

    public static void main(String[] args) {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal three = BigDecimal.valueOf(3);
        BigDecimal eight = BigDecimal.valueOf(8);

        System.out.println("exactOneOverEight=" + one.divide(eight));
        showError("exactOneOverThree", () -> one.divide(three));
        System.out.println("scaleTwo=" +
                one.divide(three, 2, RoundingMode.HALF_UP));
        System.out.println("scaleFive=" +
                one.divide(three, 5, RoundingMode.HALF_UP));
        System.out.println("scaleZero=" +
                one.divide(three, 0, RoundingMode.HALF_UP));
        System.out.println("scaleMinusOne=" +
                new BigDecimal("123").divide(
                        BigDecimal.ONE, -1, RoundingMode.HALF_UP));

        System.out.println("implicitScaleZero=" +
                one.divide(three, RoundingMode.HALF_UP));
        System.out.println("implicitScaleTwo=" +
                new BigDecimal("1.00").divide(
                        three, RoundingMode.HALF_UP));
        System.out.println("sameScaleResult=" +
                new BigDecimal("2.000").divide(
                        new BigDecimal("4"), RoundingMode.HALF_EVEN));

        MathContext mc3 = new MathContext(3, RoundingMode.HALF_UP);
        System.out.println("contextThree=" + one.divide(three, mc3));
        System.out.println("contextBig=" +
                new BigDecimal("12345").divide(
                        new BigDecimal("7"), mc3));
        System.out.println("unlimitedExact=" +
                one.divide(eight, MathContext.UNLIMITED));
        showError("unlimitedRecurring",
                () -> one.divide(three, MathContext.UNLIMITED));

        BigDecimal a = new BigDecimal("123.45");
        BigDecimal two = BigDecimal.valueOf(2);
        System.out.println("scaleVersusPrecisionScale=" +
                a.divide(two, 2, RoundingMode.HALF_UP));
        System.out.println("scaleVersusPrecisionContext=" +
                a.divide(two, new MathContext(
                        2, RoundingMode.HALF_UP)));

        System.out.println("tieHalfUp=" +
                one.divide(eight, 2, RoundingMode.HALF_UP));
        System.out.println("tieHalfEven=" +
                one.divide(eight, 2, RoundingMode.HALF_EVEN));
        System.out.println("negativeHalfUp=" +
                one.negate().divide(
                        eight, 2, RoundingMode.HALF_UP));
        System.out.println("negativeHalfEven=" +
                one.negate().divide(
                        eight, 2, RoundingMode.HALF_EVEN));

        showError("unnecessaryRecurring",
                () -> one.divide(
                        three, 2, RoundingMode.UNNECESSARY));
        showError("divideByZero",
                () -> one.divide(
                        BigDecimal.ZERO, 2, RoundingMode.HALF_UP));

        BigDecimal exactWithScale = new BigDecimal("1.00")
                .divide(new BigDecimal("8.0"));
        System.out.println("exactPreferredScaleValue=" +
                exactWithScale.toPlainString());
        System.out.println("exactPreferredScaleActual=" +
                exactWithScale.scale());

        BigDecimal forty = new BigDecimal("40");
        System.out.println("terminatingThreeOverForty=" +
                new BigDecimal("3").divide(forty));

        BigDecimal small = new BigDecimal("0.015");
        BigDecimal settledEach =
                small.setScale(2, RoundingMode.HALF_UP);
        System.out.println("roundedEachThenSum=" +
                settledEach.add(settledEach));
        System.out.println("sumThenRound=" +
                small.add(small).setScale(
                        2, RoundingMode.HALF_UP));

        System.out.println("divideIntegral=" +
                BigDecimal.TEN.divideToIntegralValue(three));
        System.out.println("remainder=" +
                BigDecimal.TEN.remainder(three));

        System.out.println("primitiveDoubleZero=" + (1.0 / 0.0));
        System.out.println("oneUnchanged=" + one);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac BigDecimalDivideDemo.java
java BigDecimalDivideDemo
```

**Stdout dự kiến:**

```text
exactOneOverEight=0.125
exactOneOverThree=ArithmeticException
scaleTwo=0.33
scaleFive=0.33333
scaleZero=0
scaleMinusOne=1.2E+2
implicitScaleZero=0
implicitScaleTwo=0.33
sameScaleResult=0.500
contextThree=0.333
contextBig=1.76E+3
unlimitedExact=0.125
unlimitedRecurring=ArithmeticException
scaleVersusPrecisionScale=61.73
scaleVersusPrecisionContext=62
tieHalfUp=0.13
tieHalfEven=0.12
negativeHalfUp=-0.13
negativeHalfEven=-0.12
unnecessaryRecurring=ArithmeticException
divideByZero=ArithmeticException
exactPreferredScaleValue=0.125
exactPreferredScaleActual=3
terminatingThreeOverForty=0.075
roundedEachThenSum=0.04
sumThenRound=0.03
divideIntegral=3
remainder=1
primitiveDoubleZero=Infinity
oneUnchanged=1
```

Đầu ra cho thấy:

- `exactOneOverEight` thành công nhưng `exactOneOverThree` ném exception.
- `implicitScaleZero=0` và `implicitScaleTwo=0.33` xuất phát từ **scale khác nhau của số bị chia**, dù đều là 1/3.
- `scaleVersusPrecisionScale=61.73` khác `scaleVersusPrecisionContext=62` vì một phía giới hạn **scale**, phía kia giới hạn **chữ số có nghĩa**.
- `tieHalfUp=0.13` và `tieHalfEven=0.12` chứng minh chính sách làm tròn điểm giữa.
- `exactPreferredScaleActual=3` chứng minh scale thương chính xác có thể **lớn hơn preferred scale**.
- `roundedEachThenSum=0.04` khác `sumThenRound=0.03` do **thời điểm chốt** chứ không phải bug BigDecimal.

## 13. Lỗi phổ biến và cách phòng tránh

| Hiểu nhầm hoặc cách làm | Thực tế |
| --- | --- |
| “`divide(b)` luôn tự chọn số chữ số hợp lý.” | Không: decimal vô hạn gây `ArithmeticException`. |
| “`divide(b, HALF_UP)` mặc định làm tròn hai chữ số.” | Không: giữ scale của số bị chia. |
| “`scale = 2` và `MathContext(2)` giống nhau.” | Không: một là hai chữ số sau dấu chấm, một là hai chữ số có nghĩa. |
| “`MathContext.UNLIMITED` tự biểu diễn được 1/3 vô hạn.” | Không: phép chia không kết thúc vẫn ném exception. |
| “`RoundingMode.UNNECESSARY` luôn an toàn với phép chia.” | Không: nếu phải làm tròn sẽ có `ArithmeticException`. |
| “Tất cả `ArithmeticException` của divide đều nghĩa là chia cho 0.” | Không: còn có thương decimal không kết thúc hoặc không vừa precision/scale. |
| “`BigDecimal.divide` cho Infinity như `double`.” | Không: BigDecimal không có NaN/Infinity. |
| “RoundingMode.HALF_EVEN luôn khác HALF_UP.” | Chỉ khác ở một số điểm giữa tùy chữ số cuối; nhiều đầu vào cho cùng kết quả. |
| “Chia chính xác đảm bảo kết quả có preferred scale.” | Preferred scale chỉ là ưu tiên; kết quả exact có thể cần scale lớn hơn. |
| “Các kết quả so sánh `equals` khi cùng số học.” | `equals` còn so scale; `compareTo` mới so giá trị số. |
| “Dùng BigDecimal tạo từ `new BigDecimal(0.1)` sẽ sửa sai số nguồn.” | Không: đó là biểu diễn chính xác của xấp xỉ nhị phân `double`. |
| “Có thể tự làm tròn sau mọi bước để an toàn hơn.” | Có thể mất thông tin; hợp đồng mới quyết định thời điểm cần chốt. |

Checklist trước mỗi `divide`:

1. **Số chia khác 0 chưa?** Kiểm tra `divisor.signum() != 0`, kể cả biểu diễn `0.00`.
2. **Thương có thể có decimal vô hạn không?** Nếu có, phải chọn scale/precision và mode chấp nhận làm tròn hoặc từ chối phép tính.
3. **Đích yêu cầu scale cố định hay chữ số có nghĩa?** Chọn `divide(b, scale, mode)` hoặc `divide(b, MathContext)` tương ứng.
4. **Có thực sự muốn giữ scale của số bị chia?** Chỉ khi đúng như vậy mới chọn `divide(b, mode)`.
5. **RoundingMode nào là hợp đồng?** Xác định ở điểm giữa, số âm, giao dịch độc lập và phép chia chính xác không vừa độ rộng.
6. **Tính nội bộ và quyết toán có tách rời không?** Chỉ làm tròn tại ranh giới có ý nghĩa, không tự làm tròn mọi bước.
7. **Đầu ra cần được lưu/so sánh thế nào?** Phân biệt `compareTo` và `equals` vì scale khác nhau.
8. **Cách xử lý exception đã rõ chưa?** Phân loại chia cho zero, decimal vô hạn và `UNNECESSARY` trước khi báo lỗi phù hợp.
9. **Kết quả có thể quá lớn hoặc tốn tài nguyên không?** Đặt giới hạn precision, scale, chiều dài đầu vào theo chính sách ứng dụng.
10. **Đã kiểm thử số âm, zero, điểm giữa và scale âm chưa?** Những trường hợp này thường phát hiện bug sớm nhất.

**Kết luận:** `BigDecimal.divide` có nhiều overload vì không có một chính sách làm tròn đúng cho mọi tình huống. `divide(divisor)` yêu cầu **chính xác**; `divide(divisor, scale, mode)` chọn **scale cụ thể**; `divide(divisor, mode)` lấy **scale của số bị chia**; còn `divide(divisor, MathContext)` giới hạn **chữ số có nghĩa**. Chọn overload dựa trên yêu cầu dữ liệu và hợp đồng làm tròn, không dựa trên cảm giác rằng một mode hay một số chữ số “có vẻ đủ”.

Tài liệu API: [Oracle Java 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html), [RoundingMode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html) và [MathContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-004">divideToIntegralValue, remainder và divideAndRemainder</a>

<details>
<summary>Xem chi tiết</summary>

# divideToIntegralValue, remainder và divideAndRemainder trong BigDecimal

> **Java core – numbers · Phép toán BigDecimal**  
> **Mục tiêu:** Hiểu sự khác biệt giữa chia lấy **phần nguyên của thương**, phép chia lấy **phần dư** và API trả **cả hai**; đọc đúng dấu, scale, precision và ngoại lệ; phân biệt remainder với phép modulo Euclid; biết khi nào nên truyền `MathContext` và viết mã Java 17+ kiểm chứng chính xác kết quả.

## 1. Khi phép chia thập phân không cần thương có phần lẻ

Một tình huống thường gặp: chia một số tiền, khối lượng hoặc số lượng chính xác thành các nhóm, rồi cần biết **có bao nhiêu nhóm nguyên** và **phần còn lại là bao nhiêu**.

Ví dụ có `17.5` đơn vị và mỗi nhóm cần `3` đơn vị:

\[
17.5 = 5 \times 3 + 2.5
\]

Ta cần thương nguyên 5 và phần dư 2.5; không nhất thiết phải tính thương thập phân lặp vô hạn `17.5/3 = 5.83333...`.

`BigDecimal` cung cấp ba phương thức cho nhu cầu ấy:

```java
import java.math.BigDecimal;

BigDecimal amount = new BigDecimal("17.5");
BigDecimal unit = new BigDecimal("3");

BigDecimal quotient = amount.divideToIntegralValue(unit);
BigDecimal remainder = amount.remainder(unit);
BigDecimal[] both = amount.divideAndRemainder(unit);

System.out.println(quotient);  // 5.0
System.out.println(remainder); // 2.5
System.out.println(both[0]);   // 5.0
System.out.println(both[1]);   // 2.5
```

**Vì sao in `5.0` chứ không phải `5`?** `BigDecimal` có thuộc tính **scale**. Một kết quả có giá trị số nguyên vẫn có thể được biểu diễn bằng BigDecimal scale dương. `5.0` vẫn là giá trị số 5; phần cách biểu diễn sẽ được phân tích ở mục về scale.

### Bảng API cốt lõi

| Phương thức | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| `a.divideToIntegralValue(b)` | `BigDecimal` | Phần nguyên của thương, cắt phần lẻ về phía 0 |
| `a.remainder(b)` | `BigDecimal` | `a - q×b` với q là thương nguyên ở trên |
| `a.divideAndRemainder(b)` | `BigDecimal[]` | Mảng hai phần tử: `[0]` là q, `[1]` là r |
| `a.divideToIntegralValue(b, mc)` | `BigDecimal` | Thương nguyên, nhưng bị ràng buộc khả năng biểu diễn bởi `MathContext` |
| `a.remainder(b, mc)` | `BigDecimal` | Phần dư dùng thương nguyên tính với `mc` |
| `a.divideAndRemainder(b, mc)` | `BigDecimal[]` | Cặp thương nguyên và dư với `mc` |

Cả sáu phương thức trên có trong Java 17, thuộc `java.math.BigDecimal`. Không phải phương thức static: chúng được gọi trên **số bị chia**.

## 2. Định nghĩa chính xác của thương nguyên

Cho số bị chia `a` và số chia `b != 0`. Phần nguyên của thương được tính bằng cách **cắt phần thập phân của thương thực về phía số 0** (*truncate toward zero*).

Ký hiệu:

\[
q = \operatorname{trunc}(a/b)
\]
\[
r = a - q\times b
\]

Đẳng thức phải luôn thỏa mãn theo giá trị số học:

\[
a = q\times b + r
\]

Đặc biệt, q không nhất thiết là kết quả của hàm **floor** (làm tròn xuống −∞). Sự khác biệt lộ rõ với số âm:

```text
 a       b      a/b (xấp xỉ)   q = trunc(a/b)    r
-------------------------------------------------------
 17.5    3       5.8333...          5         2.5
-17.5    3      -5.8333...         -5        -2.5
 17.5   -3      -5.8333...         -5         2.5
-17.5   -3       5.8333...          5        -2.5
```

Với `a=-17.5` và `b=3`, nếu dùng `floor(-5.8333...)` thì q=−6; nhưng `divideToIntegralValue` trả giá trị q=−5. Hai quy tắc khác nhau.

**Đừng gọi thương này là “làm tròn xuống” khi có thể có số âm.** Dùng “cắt phần lẻ về 0” hoặc “thương nguyên theo truncation”.

### Thương nguyên vẫn mang kiểu BigDecimal

`divideToIntegralValue` trả **`BigDecimal`**, không phải `BigInteger`, `int` hay `long`:

```java
BigDecimal q = new BigDecimal("17.5")
        .divideToIntegralValue(new BigDecimal("3"));

// Có thể là 5.0, nhưng vẫn biểu diễn một số nguyên.
System.out.println(q.toBigIntegerExact()); // 5
```

Nếu API tiếp theo thật sự cần kiểu nguyên, `toBigIntegerExact()` có thể diễn giải giá trị số nguyên thành BigInteger mà không cắt phần lẻ. Để chuyển về int hoặc long mà không mất giá trị, cân nhắc `intValueExact()`/`longValueExact()`; chúng báo lỗi nếu kết quả không vừa miền kiểu đích.

## 3. remainder là gì? Không phải “modulo luôn không âm”

Với `a=17.5`, `b=3`:

```java
BigDecimal a = new BigDecimal("17.5");
BigDecimal b = new BigDecimal("3");

BigDecimal q = a.divideToIntegralValue(b);
BigDecimal r = a.remainder(b);

System.out.println(q); // 5.0
System.out.println(r); // 2.5
```

Đẳng thức `a = q×b + r` được thực hiện theo giá trị BigDecimal chính xác:

```java
BigDecimal reconstructed = q.multiply(b).add(r);
System.out.println(reconstructed.compareTo(a) == 0); // true
```

Với số âm:

```java
BigDecimal negative = new BigDecimal("-17.5");
System.out.println(negative.divideToIntegralValue(b)); // -5.0
System.out.println(negative.remainder(b));             // -2.5
```

**Phần dư của BigDecimal có thể âm.** Nó lấy dấu theo số bị chia `a` khi phần dư khác 0, do q được cắt về phía 0. Đó là cùng tinh thần với toán tử `%` của số nguyên Java, **không** phải phép modulo Euclid luôn thuộc đoạn `[0,m)` với môđun dương.

Các đặc tính khi `b != 0` và các giá trị hữu hạn:

- `a = q×b+r`;
- `|r| < |b|` khi r khác 0, và điều kiện cũng thỏa với r=0;
- r khác 0 mang dấu của a;
- đổi dấu b có thể đổi dấu q, nhưng **không đổi dấu r** nếu a giữ nguyên;
- thương nguyên q được cắt về 0, không phụ thuộc chế độ rounding thông thường của phép chia decimal.

### Phân biệt remainder với modulo Euclid

Giả sử cần phần dư **không âm** từ một môđun dương 3:

```java
BigDecimal a = new BigDecimal("-17.5");
BigDecimal m = new BigDecimal("3");

BigDecimal raw = a.remainder(m); // -2.5
BigDecimal euclidean = raw.signum() < 0
        ? raw.add(m) : raw;       // 0.5
```

`0.5` là phần dư Euclid trong đoạn `[0,3)`, còn `-2.5` là phần dư của BigDecimal. Nếu muốn phép modulo Euclid cho BigDecimal, hãy định nghĩa **môđun dương** và chuẩn hóa phần dư như trên.

BigInteger có `mod(modulus)` với yêu cầu modulus dương; **BigDecimal không có `mod(BigDecimal)` tương ứng**. Không tự đặt tên `remainder` là modulo không âm rồi xây thuật toán dựa trên giả định sai.

## 4. divideAndRemainder: một lời gọi, hai kết quả

Khi chương trình cần **cả thương nguyên lẫn phần dư**, viết:

```java
BigDecimal[] result = amount.divideAndRemainder(unit);
BigDecimal quotient = result[0];
BigDecimal remainder = result[1];
```

Mảng có **đúng hai phần tử** theo thứ tự:

1. `result[0]`: thương nguyên `divideToIntegralValue`.
2. `result[1]`: phần dư `remainder`.

Đừng đảo hai phần tử hoặc hiểu `result[0]` là thương decimal có phần lẻ.

```java
BigDecimal amount = new BigDecimal("17.5");
BigDecimal unit = new BigDecimal("3");

BigDecimal[] qr = amount.divideAndRemainder(unit);

System.out.println("q = " + qr[0]); // q = 5.0
System.out.println("r = " + qr[1]); // r = 2.5
```

Theo Javadoc, khi cần cả thương và dư, phương thức này **có thể nhanh hơn** gọi lần lượt hai phép riêng vì phần tính thương có thể được dùng lại. Không suy luận rằng một lời gọi luôn có tốc độ cố định hoặc luôn nhanh hơn trong mọi workload; muốn tối ưu thực tế phải benchmark.

### Tính bất biến của BigDecimal

Phép chia và lấy dư **không thay đổi** hai toán hạng:

```java
BigDecimal a = new BigDecimal("17.5");
BigDecimal b = new BigDecimal("3");
BigDecimal[] qr = a.divideAndRemainder(b);

System.out.println(a); // 17.5
System.out.println(b); // 3
```

`BigDecimal` là immutable; việc gán lại biến hay lưu một giá trị mới không thay đổi đối tượng cũ. Mảng `BigDecimal[]` do phương thức trả về là một container gồm hai tham chiếu, bạn có thể thay phần tử của mảng nhưng không làm đổi giá trị BigDecimal bên trong.

## 5. Khác gì với divide và phép chia decimal thông thường?

Đây là khác biệt rất quan trọng:

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

// Không có thương decimal kết thúc:
 // BigDecimal bad = one.divide(three); // ArithmeticException

BigDecimal integerPart = one.divideToIntegralValue(three);
BigDecimal rest = one.remainder(three);
BigDecimal[] both = one.divideAndRemainder(three);

System.out.println(integerPart); // 0
System.out.println(rest);        // 1
System.out.println(both[0]);     // 0
System.out.println(both[1]);     // 1
```

`1/3` có phần thập phân lặp vô hạn, vì vậy `BigDecimal.divide(BigDecimal)` **không có rounding mode/context** không thể tạo thương decimal kết thúc chính xác và ném `ArithmeticException`.

Nhưng **thương nguyên của 1/3 chỉ là 0, phần dư là 1**. Hai giá trị đó được biểu diễn chính xác; `divideToIntegralValue` và `remainder` không cần tính mọi chữ số của thương decimal.

Khi muốn thương có phần thập phân theo quy tắc làm tròn, dùng một API chia **khác**:

```java
import java.math.RoundingMode;

BigDecimal rounded = one.divide(
        three, 2, RoundingMode.HALF_UP);
System.out.println(rounded); // 0.33
```

Nhưng `0.33` **không phải** thương nguyên q=0 và cũng không được dùng thay q trong công thức remainder mặc định. Nếu dùng `0.33` làm thương để tính `1 - 0.33×3`, phần còn lại là 0.01 — khác định nghĩa r=1.

| Phép toán | `1/3` | Ý nghĩa |
| --- | --- | --- |
| `one.divide(three)` | Ném `ArithmeticException` | Yêu cầu thương decimal chính xác kết thúc |
| `one.divide(three, 2, HALF_UP)` | 0.33 | Thương decimal làm tròn |
| `one.divideToIntegralValue(three)` | 0 | Phần nguyên của thương |
| `one.remainder(three)` | 1 | Phần còn lại theo q=0 |
| `one.divideAndRemainder(three)` | [0, 1] | Trả cả hai |

**Bài học:** Không chọn `divideToIntegralValue` chỉ để “tránh exception” nếu yêu cầu của nghiệp vụ thực sự cần một thương decimal. Nó cố ý **bỏ phần lẻ của thương**.

## 6. Dấu của thương và dư: kiểm tra đủ 4 trường hợp

Thử bốn tổ hợp dấu:

```java
BigDecimal positive = new BigDecimal("17.5");
BigDecimal negative = new BigDecimal("-17.5");
BigDecimal three = new BigDecimal("3");
BigDecimal minusThree = new BigDecimal("-3");

System.out.println(java.util.Arrays.toString(
        positive.divideAndRemainder(three)));
// [5.0, 2.5]

System.out.println(java.util.Arrays.toString(
        negative.divideAndRemainder(three)));
// [-5.0, -2.5]

System.out.println(java.util.Arrays.toString(
        positive.divideAndRemainder(minusThree)));
// [-5.0, 2.5]

System.out.println(java.util.Arrays.toString(
        negative.divideAndRemainder(minusThree)));
// [5.0, -2.5]
```

Lưu ý:

- q âm khi a và b trái dấu; q dương khi chúng cùng dấu và |a|≥|b|.
- r là 2.5 nếu a=17.5; r là −2.5 nếu a=−17.5.
- Khi a có độ lớn nhỏ hơn |b|, q=0 theo trị số và r=a.
- Không dùng `Math.floor` hay rounding xuống để tự tính q âm; đó sẽ là phép toán khác.

Trong hệ thống kế toán hoặc đo lường, hãy kiểm thử số âm nếu có khả năng phát sinh số dư điều chỉnh/hoàn tiền. Chỉ thử a>0, b>0 thường bỏ sót nhầm lẫn về dấu.

## 7. Scale: q là số nguyên về giá trị nhưng có thể in 5.0 hay 2.00

Mỗi `BigDecimal` được diễn giải qua **unscaled value** và **scale**:

\[
\text{value}=\text{unscaledValue}\times10^{-\text{scale}}.
\]

`new BigDecimal("17.5")` có scale 1; `new BigDecimal("3")` có scale 0. Preferred scale của kết quả `divideToIntegralValue` là hiệu scale của số bị chia và số chia; trong ví dụ này là 1:

```java
BigDecimal q = new BigDecimal("17.5")
        .divideToIntegralValue(new BigDecimal("3"));

System.out.println(q);         // 5.0
System.out.println(q.scale()); // 1
```

Thương **về mặt toán học là số nguyên 5**. Nhưng `BigDecimal` có thể giữ scale 1 và in `5.0` để phù hợp biểu diễn ưu tiên của phép toán.

Ví dụ có hai chữ số phần thập phân:

```java
BigDecimal a = new BigDecimal("10.00");
BigDecimal b = new BigDecimal("4");

BigDecimal[] qr = a.divideAndRemainder(b);
System.out.println(java.util.Arrays.toString(qr));
// [2.00, 2.00]

System.out.println(qr[0].scale()); // 2
System.out.println(qr[1].scale()); // 2
```

Ở đây hai kết quả có scale 2, nhưng nếu ta viết dữ liệu đầu vào là `"10"` thay vì `"10.00"`, dạng in của thương và dư có thể khác.

**Không hứa mọi thương nguyên luôn có scale 0, hay scale luôn bằng đúng hiệu scale.** Preferred scale là một nguyên tắc định dạng/biểu diễn; trong một số phép chia hoặc khi `MathContext` ràng buộc precision, API có thể phải dùng scale khác để biểu diễn đúng thương nguyên.

### equals khác compareTo

`BigDecimal.equals` xét cả giá trị **và scale**. `compareTo` xét thứ tự giá trị số:

```java
BigDecimal x = new BigDecimal("2.00");
BigDecimal y = new BigDecimal("2");

System.out.println(x.equals(y));      // false
System.out.println(x.compareTo(y)==0); // true
```

Để kiểm tra đẳng thức `a=q×b+r` theo **giá trị số**, dùng `compareTo(...)==0`. Nếu hợp đồng còn yêu cầu khớp biểu diễn (scale), kiểm tra thêm `scale()` hoặc dùng `equals` khi đó là chủ đích.

Không tự động `stripTrailingZeros()` mọi kết quả trong ứng dụng tài chính: nó có thể thay đổi scale và cách in dữ liệu, đôi khi thành dạng ký pháp khoa học. Hãy phân biệt **số học chính xác** và **quy tắc biểu diễn**.

## 8. MathContext là gì và liên quan thế nào tới chia thương nguyên?

`MathContext` cấu hình **precision** (số chữ số có nghĩa) và một `RoundingMode`:

```java
import java.math.MathContext;
import java.math.RoundingMode;

MathContext mc = new MathContext(
        4, RoundingMode.HALF_UP);
```

Các overload:

```java
BigDecimal q = a.divideToIntegralValue(b, mc);
BigDecimal r = a.remainder(b, mc);
BigDecimal[] pair = a.divideAndRemainder(b, mc);
```

Đối với ba phương thức này, hãy hiểu `mc` chủ yếu là **giới hạn khả năng biểu diễn phần thương nguyên**. Không được suy rằng `RoundingMode.HALF_UP` sẽ làm tròn `5.833...` thành q=6. Phần nguyên theo quy tắc **cắt về 0** vẫn là số 5.

Một điểm tinh tế từ Javadoc: phần dư tính qua thương nguyên **không bị làm tròn về precision của `MathContext`**; vì vậy giá trị remainder trả về **có thể có nhiều chữ số hơn `mc.getPrecision()`**.

### Ví dụ nếu thương nguyên cần nhiều chữ số hơn precision

```java
BigDecimal a = new BigDecimal("12345");
BigDecimal b = new BigDecimal("2");
MathContext tooSmall = new MathContext(
        3, RoundingMode.DOWN);

try {
    a.divideToIntegralValue(b, tooSmall);
} catch (ArithmeticException ex) {
    System.out.println("precision insufficient");
}
```

Vì phần nguyên của `12345 / 2` là `6172`, cần 4 chữ số có nghĩa để biểu diễn chính xác. `MathContext(3,...)` không thể giữ đúng q, nên `divideToIntegralValue` ném `ArithmeticException` thay vì “làm tròn q=6170” hay “cắt tiếp q=617”.

```java
MathContext enough = new MathContext(
        4, RoundingMode.DOWN);

BigDecimal[] qr = a.divideAndRemainder(b, enough);
System.out.println(java.util.Arrays.toString(qr));
// [6172, 1]
```

**Bản chất:** với overload có MathContext, không có nghĩa API cho phép trả một thương nguyên sai chỉ để vừa precision. Nếu thương nguyên chính xác không thể biểu diễn trong giới hạn đã chọn, nó có thể ném exception.

### RoundingMode không thay định nghĩa của phần dư

```java
BigDecimal a = new BigDecimal("17.5");
BigDecimal b = new BigDecimal("3");

BigDecimal[] qr = a.divideAndRemainder(
        b, new MathContext(2, RoundingMode.HALF_UP));

// q vẫn là 5 về mặt số học, r vẫn là 2.5.
```

Kết quả không phải một phép chia decimal thông thường đã được làm tròn. Khi cần chia ra 5.83 hoặc 5.833, dùng `divide(..., mc)` và hiểu rõ yêu cầu precision của **thương có phần lẻ**.

## 9. MathContext.UNLIMITED và precision = 0

Một `MathContext` có `precision == 0` biểu thị **không giới hạn precision** theo nghĩa của MathContext. Hằng tiện ích:

```java
MathContext unlimited = MathContext.UNLIMITED;
```

Có thể dùng:

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");
BigDecimal[] qr = one.divideAndRemainder(
        three, MathContext.UNLIMITED);

System.out.println(java.util.Arrays.toString(qr));
// [0, 1]
```

Nhưng **đừng suy** rằng `one.divide(three, MathContext.UNLIMITED)` sẽ tự sinh vô hạn chữ số hoặc tự chọn rounding. Phép chia decimal không có biểu diễn thập phân hữu hạn chính xác vẫn có thể ném `ArithmeticException` vì không có cách trả kết quả chính xác hữu hạn.

Precision không giới hạn ở đây **không có nghĩa heap, CPU hay size BigDecimal vô hạn**. BigDecimal vẫn bị ràng buộc bởi tài nguyên và giới hạn triển khai.

## 10. remainder với MathContext không nhất thiết vừa precision

Javadoc nêu remainder không phải phép toán làm tròn theo cách hiểu “kết quả phải có nhiều nhất P chữ số”:

```java
BigDecimal r = a.remainder(b, mc);
```

Phương thức này về nguyên tắc tính:

```text
q = a.divideToIntegralValue(b, mc)
r = a - q*b
```

Sau khi xác định q theo điều kiện của context, phần dư được hình thành theo giá trị chính xác từ phép trừ, chứ không bắt buộc bị ép còn `mc.getPrecision()` chữ số.

Ví dụ một phép trừ có scale nhỏ hoặc giá trị dư rất nhiều chữ số sau dấu chấm có thể cần nhiều chữ số có nghĩa hơn q. Nếu muốn làm tròn r sau khi tính, đó là **quyết định nghiệp vụ riêng**; bạn có thể dùng `r.round(mc)` và chấp nhận rằng đẳng thức `a = q×b + r_rounded` có thể không còn đúng tuyệt đối.

### Không dùng context để “giấu” overflow

`BigDecimal` không có overflow theo miền 32/64 bit primitive như `int`/`long`, nhưng nó vẫn có thể ném `ArithmeticException` khi chính xác không phù hợp với `MathContext`, khi chia cho zero, hoặc gặp giới hạn scale. Không nên catch toàn bộ lỗi rồi trả `BigDecimal.ZERO`: giá trị 0 thật và phép toán không hợp lệ là hai trường hợp khác nhau.

## 11. Zero divisor và các trường hợp biên

Mọi phép trong nhóm đều yêu cầu **divisor khác 0**:

```java
BigDecimal ten = BigDecimal.TEN;

// ten.divideToIntegralValue(BigDecimal.ZERO); // ArithmeticException
// ten.remainder(BigDecimal.ZERO);             // ArithmeticException
// ten.divideAndRemainder(BigDecimal.ZERO);    // ArithmeticException
```

Kiểm tra trước nếu đầu vào có thể không hợp lệ:

```java
static BigDecimal[] split(BigDecimal a, BigDecimal b) {
    if (b.signum() == 0) {
        throw new IllegalArgumentException(
                "divisor must not be zero");
    }
    return a.divideAndRemainder(b);
}
```

Đoạn mã này giả định a và b không null; nếu API nhận dữ liệu tùy ý, cần kiểm tra null trước khi gọi `signum()` và `divideAndRemainder()`.

### Số bị chia bằng 0

```java
BigDecimal a = BigDecimal.ZERO;
BigDecimal b = new BigDecimal("7");
BigDecimal[] qr = a.divideAndRemainder(b);

System.out.println(qr[0].compareTo(BigDecimal.ZERO) == 0); // true
System.out.println(qr[1].compareTo(BigDecimal.ZERO) == 0); // true
```

Scale và cách in số 0 có thể phụ thuộc cách tạo toán hạng; hãy so sánh **giá trị số** bằng `compareTo` khi đó mới là mục tiêu.

### |a| < |b|

```java
BigDecimal a = new BigDecimal("2.5");
BigDecimal b = new BigDecimal("8");

BigDecimal q = a.divideToIntegralValue(b);
BigDecimal r = a.remainder(b);

System.out.println(q.compareTo(BigDecimal.ZERO)==0); // true
System.out.println(r.compareTo(a)==0);                // true
```

Nếu độ lớn của số bị chia nhỏ hơn độ lớn divisor, thương nguyên có giá trị 0 và phần dư có giá trị đúng bằng số bị chia. Không nhầm với thương decimal `0.3125`.

## 12. Số học tài chính: chia nhóm khác chia tiền bình quân

Với `BigDecimal` trong tài chính, không phải lúc nào cũng nên chọn `divideToIntegralValue`:

- Nếu mục tiêu là **số nhóm nguyên** và **dư giữ lại**, phép này đúng.
- Nếu mục tiêu là chia tiền **đều cho nhiều người**, bạn có thể cần quy tắc làm tròn theo đơn vị tiền nhỏ nhất.
- Nếu mục tiêu là tính thuế, lãi suất hoặc tỷ lệ %, thường cần thương decimal và `RoundingMode` theo hợp đồng.

Ví dụ chia 10.00 đơn vị theo nhóm 4.00 đơn vị:

```java
BigDecimal available = new BigDecimal("10.00");
BigDecimal batchSize = new BigDecimal("4.00");

BigDecimal[] qr = available.divideAndRemainder(batchSize);
// Giá trị số của thương nguyên = 2, phần dư = 2.00.
```

Đây là cách đếm số **nhóm đầy đủ** và lượng còn lại. Nó không nói rằng “mỗi nhóm nhận 2.50 đơn vị”: đó là bài toán chia đều 10.00 thành bốn phần, cần `divide` theo đơn vị thích hợp.

Nếu cần bảo toàn đúng scale tiền tệ (ví dụ luôn 2 chữ số), chỉ áp dụng `setScale(2,...)` sau khi xác định ngữ nghĩa số học và quy tắc rounding, không giả định scale do `divideAndRemainder` sinh ra luôn bằng scale hiển thị nghiệp vụ.

## 13. Kiểm tra tính đúng bằng đẳng thức a = q*b + r

Đây là **invariant** hữu ích nhất khi viết unit test:

```java
static boolean invariantHolds(BigDecimal a, BigDecimal b) {
    if (b.signum() == 0) return false;

    BigDecimal[] qr = a.divideAndRemainder(b);
    BigDecimal rebuilt = qr[0].multiply(b).add(qr[1]);

    return rebuilt.compareTo(a) == 0;
}
```

Ngoài ra kiểm tra:

```java
BigDecimal[] qr = a.divideAndRemainder(b);

boolean smallRemainder =
        qr[1].abs().compareTo(b.abs()) < 0;

boolean signCompatible =
        qr[1].signum() == 0
        || qr[1].signum() == a.signum();
```

Ba kiểm tra này đặc biệt hữu ích với số âm, divisor âm và số có scale khác nhau. **Không nhất thiết dùng `equals`** cho đẳng thức reconstructed, bởi scale của giá trị tái dựng có thể khác scale của a dù hai giá trị số học bằng nhau.

### Kiểm thử với property-based approach

Có thể sinh nhiều cặp `a,b` có scale khác nhau (b≠0), bao gồm các số âm và các số như `0.00`/`1E+3`, rồi kiểm tra các invariant trên.

Khi chạy overload với `MathContext`, hãy phân biệt hai trường hợp:

- Phép toán trả kết quả → kiểm tra invariant.
- Phép toán ném `ArithmeticException` vì precision không cho phép thương nguyên → đây có thể là **hành vi đúng của contract**, không phải thất bại của invariant.

## 14. Java 17+ demo hoàn chỉnh

Tạo tệp **`BigDecimalQuotientRemainderDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;

public class BigDecimalQuotientRemainderDemo {

    private static void show(String label,
            BigDecimal a, BigDecimal b) {
        BigDecimal[] qr = a.divideAndRemainder(b);
        BigDecimal rebuilt = qr[0].multiply(b).add(qr[1]);

        System.out.println(label + " -> q=" + qr[0]
                + ", r=" + qr[1]
                + ", identity=" + (rebuilt.compareTo(a) == 0));
    }

    private static BigDecimal euclideanMod(
            BigDecimal a, BigDecimal modulus) {
        if (modulus.signum() <= 0) {
            throw new IllegalArgumentException(
                    "modulus must be positive");
        }
        BigDecimal r = a.remainder(modulus);
        return r.signum() < 0 ? r.add(modulus) : r;
    }

    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("17.5");
        BigDecimal b = new BigDecimal("3");

        show("positive", a, b);
        show("negative dividend", a.negate(), b);
        show("negative divisor", a, b.negate());
        show("both negative", a.negate(), b.negate());

        System.out.println("integral = "
                + a.divideToIntegralValue(b));
        System.out.println("remainder = " + a.remainder(b));
        System.out.println("euclidean mod(-17.5,3) = "
                + euclideanMod(a.negate(), b));

        BigDecimal[] scaled =
                new BigDecimal("10.00")
                .divideAndRemainder(new BigDecimal("4"));
        System.out.println("scaled = " + Arrays.toString(scaled));
        System.out.println("quotient scale = "
                + scaled[0].scale());
        System.out.println("2.00 equals 2 = "
                + new BigDecimal("2.00").equals(
                        new BigDecimal("2")));
        System.out.println("2.00 compareTo 2 = "
                + (new BigDecimal("2.00").compareTo(
                        new BigDecimal("2")) == 0));

        BigDecimal one = BigDecimal.ONE;
        BigDecimal three = new BigDecimal("3");
        System.out.println("1 / 3 qr = " +
                Arrays.toString(one.divideAndRemainder(three)));

        try {
            one.divide(three);
        } catch (ArithmeticException ex) {
            System.out.println("exact 1 / 3 = rejected");
        }

        System.out.println("rounded 1 / 3 = " +
                one.divide(three, 2, RoundingMode.HALF_UP));

        BigDecimal numerator = new BigDecimal("12345");
        BigDecimal denominator = new BigDecimal("2");

        try {
            numerator.divideToIntegralValue(denominator,
                    new MathContext(3, RoundingMode.DOWN));
        } catch (ArithmeticException ex) {
            System.out.println("precision 3 = rejected");
        }

        BigDecimal[] withContext =
                numerator.divideAndRemainder(denominator,
                    new MathContext(4, RoundingMode.DOWN));
        System.out.println("precision 4 = "
                + Arrays.toString(withContext));

        System.out.println("unlimited 1/3 qr = "
                + Arrays.toString(one.divideAndRemainder(
                        three, MathContext.UNLIMITED)));

        try {
            a.divideAndRemainder(BigDecimal.ZERO);
        } catch (ArithmeticException ex) {
            System.out.println("zero divisor = rejected");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 BigDecimalQuotientRemainderDemo.java
java BigDecimalQuotientRemainderDemo
```

**Kết quả dự kiến:**

```text
positive -> q=5.0, r=2.5, identity=true
negative dividend -> q=-5.0, r=-2.5, identity=true
negative divisor -> q=-5.0, r=2.5, identity=true
both negative -> q=5.0, r=-2.5, identity=true
integral = 5.0
remainder = 2.5
euclidean mod(-17.5,3) = 0.5
scaled = [2.00, 2.00]
quotient scale = 2
2.00 equals 2 = false
2.00 compareTo 2 = true
1 / 3 qr = [0, 1]
exact 1 / 3 = rejected
rounded 1 / 3 = 0.33
precision 3 = rejected
precision 4 = [6172, 1]
unlimited 1/3 qr = [0, 1]
zero divisor = rejected
```

### Những điểm chương trình xác minh

1. Cả bốn tổ hợp dấu đều tuân theo công thức `a=q×b+r`, và phần dư khác 0 theo dấu số bị chia.
2. `remainder` của số âm không nhất thiết không âm; muốn modulo Euclid phải chuẩn hóa riêng với môđun dương.
3. BigDecimal giữ scale trong một số phép tính: `10.00/4` cho q=2.00, r=2.00; `equals` khác `compareTo`.
4. `divideAndRemainder(1,3)` không cần biểu diễn thương decimal vô hạn, nhưng `divide(3)` chính xác thì phải báo lỗi.
5. `MathContext(3)` không đủ để biểu diễn thương nguyên 6172; `MathContext(4)` cho kết quả.
6. `MathContext.UNLIMITED` không biến `divideAndRemainder` thành phép chia decimal vô hạn.
7. Chia cho 0 bị từ chối bằng `ArithmeticException`.

## 15. Lỗi thường gặp và cách sửa

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “divideToIntegralValue trả int” | Trả `BigDecimal` chứa một giá trị số nguyên |
| “Integral quotient luôn là Math.floor(a/b)” | Cắt về phía 0; với số âm khác floor |
| “remainder luôn không âm” | Phần dư BigDecimal có thể âm; dấu theo số bị chia |
| “BigDecimal.remainder giống BigInteger.mod” | Không; BigInteger.mod cần modulus dương và trả modulo không âm |
| “divideAndRemainder trả thương decimal và dư” | Phần tử 0 là **thương nguyên**, phần tử 1 là remainder |
| “divide(BigDecimal) và divideToIntegralValue giống nhau” | Một bên cần thương decimal chính xác, bên kia chỉ lấy phần nguyên |
| “Nếu 1/3 không chia chính xác được thì lấy dư cũng lỗi” | `1.divideAndRemainder(3)` vẫn cho [0,1] |
| “Có MathContext.HALF_UP thì q=5.833... được làm tròn thành 6” | q vẫn theo truncation về 0, không làm tròn thương decimal thành 6 |
| “MathContext precision nhỏ thì kết quả bị cắt còn đủ chữ số” | Nếu không thể biểu diễn thương nguyên đúng, có thể ném `ArithmeticException` |
| “remainder(mc) luôn có ≤ mc.precision chữ số” | Phần dư không tự bị làm tròn theo precision; có thể dài hơn |
| “MathContext.UNLIMITED biến mọi phép chia thành chính xác” | Chia decimal lặp vô hạn vẫn không biểu diễn hữu hạn |
| “Thương nguyên phải có scale 0” | Có thể là 5.0 hoặc 2.00 tùy preferred scale |
| “2.00.equals(2) là true” | false vì scale khác; `compareTo` bằng 0 |
| “Trả kết quả rồi BigDecimal ban đầu bị chỉnh sửa” | BigDecimal là immutable; các phương thức trả giá trị mới |
| “Chia cho 0 chỉ tạo số Infinity” | BigDecimal không có Infinity; ném `ArithmeticException` |
| “Dùng rounding cho remainder mà không ảnh hưởng invariant” | Làm tròn phần dư có thể phá đẳng thức a=q*b+r |
| “Cần chia nhóm nguyên thì phải gọi divide trước” | Có thể dùng divideAndRemainder trực tiếp, tránh thương decimal lặp vô hạn |

## 16. Bài tập và lời giải

**Bài 1.** Với a=17.5 và b=3, viết q và r theo API BigDecimal.  
**Đáp án:** q có giá trị 5 (có thể hiển thị 5.0), r=2.5; dùng `a.divideAndRemainder(b)` lấy cả hai.

**Bài 2.** Với a=−17.5, b=3, vì sao q không phải −6?  
**Đáp án:** `divideToIntegralValue` cắt thương về 0 nên q=−5; r=−2.5. Nếu dùng floor sẽ là một phép toán khác.

**Bài 3.** Với a=17.5 và b=−3, dấu r là gì?  
**Đáp án:** r=+2.5, vì phần dư khác 0 mang dấu số bị chia a, không phải dấu divisor b.

**Bài 4.** `BigDecimal.ONE.divide(new BigDecimal("3"))` và `BigDecimal.ONE.remainder(new BigDecimal("3"))` có giống hành vi không?  
**Đáp án:** Không. divide không rounding yêu cầu thương decimal kết thúc chính xác nên ném lỗi; remainder trả 1.

**Bài 5.** Kết quả `new BigDecimal("10.00").divideAndRemainder(new BigDecimal("4"))`?  
**Đáp án:** [2.00, 2.00], theo biểu diễn scale của ví dụ; giá trị thương nguyên là 2, dư là 2.

**Bài 6.** Dùng `divideAndRemainder` thì phần tử nào chứa q, phần tử nào chứa r?  
**Đáp án:** `qr[0]` là q, `qr[1]` là r.

**Bài 7.** Vì sao `a.remainder(b, new MathContext(3))` có thể ném ngoại lệ?  
**Đáp án:** Vì thuật toán cần tính thương nguyên theo MathContext; nếu thương nguyên có phần bắt buộc không thể biểu diễn dưới precision=3, phép toán có thể ném ArithmeticException.

**Bài 8.** Dùng `MathContext(3, RoundingMode.UP)` với 12345/2 có làm q tròn thành 6180 không?  
**Đáp án:** Không. Thương nguyên chính xác theo truncation là 6172; giới hạn precision=3 không chứa đủ số chữ số nên có thể ném ArithmeticException.

**Bài 9.** Nếu cần phần dư Euclid của −17.5 theo modulus dương 3, làm thế nào?  
**Đáp án:** Lấy `r = a.remainder(m)` (−2.5), rồi nếu r âm thì `r.add(m)` (0.5).

**Bài 10.** Vì sao test identity bằng `rebuilt.equals(a)` có thể trả false dù số học đúng?  
**Đáp án:** Vì BigDecimal.equals xét thêm scale; dùng `rebuilt.compareTo(a)==0` nếu kiểm tra bằng nhau theo trị số.

**Bài 11.** Muốn biểu diễn phần nguyên thương bằng BigInteger, chọn API nào sau khi chia?  
**Đáp án:** `a.divideToIntegralValue(b).toBigIntegerExact()`, vì phần thương không có thành phần phân số về giá trị.

**Bài 12.** Nếu input từ người dùng có divisor=0, nên làm gì?  
**Đáp án:** Validate và báo lỗi theo hợp đồng; API chia/nhiều phép remainder sẽ ném ArithmeticException chứ không trả Infinity.

## 17. Checklist trước khi dùng ba phương thức

1. **Đọc đúng tên:** `divideToIntegralValue` cho thương nguyên, `remainder` cho phần dư, `divideAndRemainder` cho cả hai.
2. **Chọn quy tắc nguyên:** q cắt về 0, không phải floor cho số âm.
3. **Không nhầm remainder với modulo không âm.** Nếu cần modulo Euclid, kiểm tra modulus dương và chuẩn hóa r.
4. **Đảm bảo divisor khác 0**; có chính sách rõ ràng cho null và dữ liệu sai.
5. **Đừng dùng divideToIntegralValue thay divide một cách máy móc** nếu nghiệp vụ cần thương decimal/làm tròn.
6. **Dùng MathContext đúng mục tiêu:** precision là giới hạn kết quả thương nguyên, không tự chọn q đã làm tròn.
7. **Remainder với context có thể nhiều chữ số hơn precision**; không lén làm tròn nếu phải giữ invariant.
8. **Kiểm tra scale và equals:** trị số nguyên không nhất thiết scale 0; dùng compareTo nếu chỉ kiểm tra giá trị.
9. **Kiểm tra invariant a=q*b+r**, độ lớn phần dư và dấu phần dư trên dữ liệu dương/âm.
10. **Khi cần cả hai, ưu tiên divideAndRemainder** cho ý định rõ ràng và có thể dùng lại phép tính.
11. **Với kết quả quy về int/long**, dùng chuyển đổi Exact để phát hiện vượt miền.
12. **Tôn trọng đơn vị đo và số tiền:** chia thành nhóm nguyên khác chia đều thành tiền decimal có rounding.
13. **Kiểm thử trường hợp biên:** 0, divisor âm, a âm, |a|<|b|, 1/3, scale khác nhau và precision nhỏ.

### Tài liệu tham khảo

- [BigDecimal — Javadoc Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)
- [MathContext — Javadoc Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html)
- [RoundingMode — Javadoc Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html)

**Ghi nhớ:** Cả ba phương thức lấy **thương nguyên theo hướng về 0** và phần dư r thỏa `a=q×b+r`. `remainder` có thể âm và không đồng nghĩa modulo Euclid; `divideAndRemainder` trả mảng `[q,r]`. Với `MathContext`, API không tự làm tròn thương nguyên cho vừa precision mà có thể ném ngoại lệ. Luôn kiểm tra divisor, scale và ý nghĩa nghiệp vụ của việc giữ phần dư.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-005">pow và sqrt theo precision được yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# `BigDecimal.pow()` và `BigDecimal.sqrt()` theo precision yêu cầu

## 1. Mục tiêu bài học

Khi làm việc với số thập phân cần kiểm soát chữ số có nghĩa, Java cung cấp `BigDecimal` cùng `MathContext`. Hai phép toán chính của bài này là lũy thừa nguyên `pow(int, MathContext)` và căn bậc hai `sqrt(MathContext)`. Cần hiểu rõ khi nào kết quả chính xác, khi nào kết quả được làm tròn, cách chọn precision phù hợp và những trường hợp API từ chối phép tính.

Các lời gọi quan trọng:

```java
BigDecimal power = base.pow(exponent, context);
BigDecimal root = value.sqrt(context);
```

`BigDecimal` có thể lưu số thập phân với độ chính xác tùy ý, nhưng căn của 2 hoặc nghịch đảo của 3 không thể được biểu diễn chính xác bằng một chuỗi chữ số thập phân hữu hạn. Vì thế, lựa chọn precision và quy tắc làm tròn là một phần của yêu cầu tính toán, không chỉ là tùy chọn hiển thị.

## 2. Phân biệt precision và scale

`BigDecimal` biểu diễn giá trị `unscaledValue × 10^(-scale)`:

- **`precision`**: số chữ số có nghĩa của giá trị thập phân, đếm từ chữ số khác 0 đầu tiên. Precision trong `MathContext` đặt giới hạn số chữ số có nghĩa *của kết quả phép toán*.
- **`scale`**: số chữ số ở bên phải dấu thập phân trong cách biểu diễn khi scale không âm; scale cũng có thể âm. `12.3400` có `precision() == 6` và `scale() == 4`.
- **`RoundingMode`**: quy tắc xử lý phần chữ số bỏ đi. `HALF_EVEN` làm tròn đến giá trị gần nhất và chọn chữ số cuối chẵn khi đúng chính giữa; `HALF_UP` làm tròn về phía xa 0 ở điểm giữa; `UNNECESSARY` yêu cầu kết quả không phải làm tròn.
- **`MathContext`**: chứa precision và rounding mode. `new MathContext(8, RoundingMode.HALF_EVEN)` có nghĩa là *8 chữ số có nghĩa*, không có nghĩa là *8 chữ số sau dấu phẩy*.

Ví dụ độc lập trong một phương thức Java đã import `java.math.*`:

```java
BigDecimal value = new BigDecimal("12345.678");
MathContext mc4 = new MathContext(4, RoundingMode.HALF_EVEN);

System.out.println(value.round(mc4).toPlainString()); // 12350
System.out.println(value.setScale(4, RoundingMode.HALF_EVEN)
        .toPlainString());                              // 12345.6780
```

Lệnh thứ nhất làm tròn còn 4 chữ số có nghĩa; lệnh thứ hai đặt đúng 4 chữ số sau dấu thập phân. Việc thêm các chữ số 0 cuối có thể tăng `precision()` của đối tượng nhưng không tăng độ chính xác số học đã được tính.

Khởi tạo từ chuỗi như `new BigDecimal("0.1")` để giữ giá trị thập phân mong muốn. `new BigDecimal(0.1)` giữ chính xác giá trị `double` nhị phân đã xấp xỉ trước đó. Nếu giá trị nguồn đã là `double`, `BigDecimal.valueOf(double)` thường phù hợp hơn constructor nhận `double`, nhưng không thể khôi phục chữ số vốn đã mất tại nguồn.

## 3. Tính `pow`: số mũ nguyên

### 3.1. `pow(int n)` trả về kết quả chính xác

```java
BigDecimal exact = new BigDecimal("1.2345").pow(3);
// 1.881365963625
```

Theo Java 17, `pow(n)` nhận số mũ nguyên từ **0 đến 999_999_999**, và trả kết quả chính xác không giới hạn precision. API xác định `BigDecimal.ZERO.pow(0)` trả về `BigDecimal.ONE`. Dù một số mũ nằm trong phạm vi hợp lệ, phép tính có thể sinh ra kết quả khổng lồ và tốn bộ nhớ/thời gian; không dùng cách này tùy tiện cho số mũ lớn.

`pow` không có overload nhận `BigDecimal` làm số mũ phân số. Viết `pow(1 / 2)` là sai khi muốn lấy căn, vì `1 / 2` trong Java là phép chia nguyên, nhận giá trị `0`. Dùng `sqrt(mc)` cho căn bậc hai.

### 3.2. `pow(int n, MathContext mc)` trả về kết quả theo precision

```java
MathContext mc6 = new MathContext(6, RoundingMode.HALF_EVEN);
BigDecimal rounded = new BigDecimal("1.2345").pow(3, mc6);
// 1.88137: sáu chữ số có nghĩa
```

Khi precision của `mc` **dương**, overload này còn cho phép **số mũ nguyên âm**: `new BigDecimal("2").pow(-3, mc6)` nhận giá trị `0.125`. Số mũ âm biểu diễn nghịch đảo của lũy thừa dương và có thể cần làm tròn. Cơ số `0` với số mũ âm không hợp lệ.

Ràng buộc từ hợp đồng `BigDecimal` Java 17:

| Trường hợp | Kết quả |
| --- | --- |
| `x.pow(0)` hoặc `x.pow(0, mc)` | Bằng `1`, kể cả khi `x` bằng `0`. |
| `x.pow(n)` với `n < 0` | Ném `ArithmeticException`. |
| `x.pow(n, mc)` với `n < 0` và `mc.precision > 0` | Tính nghịch đảo theo precision; cơ số phải khác `0`. |
| `abs(n) > 999_999_999` | Ném `ArithmeticException`. |
| `mc.precision == 0` và `n < 0` | Ném `ArithmeticException`. |
| `mc.precision > 0` nhưng số chữ số thập phân của `abs(n)` lớn hơn precision | Ném `ArithmeticException`, ví dụ `pow(123, new MathContext(2))`. |
| Cần làm tròn nhưng mode là `UNNECESSARY` | Ném `ArithmeticException`. |

**Độ chính xác của thuật toán:** `pow(n, mc)` dùng thuật toán lũy thừa nội bộ có chữ số làm việc bổ sung. Java 17 mô tả kết quả nói chung nằm trong khoảng **hai ulp** của giá trị chính xác theo precision đã chọn (`ulp` là đơn vị ở vị trí chữ số cuối). Không được khẳng định `pow(n, mc)` luôn bằng từng chữ số với `pow(n).round(mc)` trong mọi trường hợp. Khi bắt buộc phải đạt kết quả của việc làm tròn *một lần* trên giá trị chính xác và số mũ đủ nhỏ, có thể tính `pow(n)` rồi `round(mc)`, chấp nhận chi phí bộ nhớ/thời gian lớn hơn.

Nếu kết quả `pow` sẽ tiếp tục tham gia các phép toán khác, cân nhắc tính bằng precision trung gian cao hơn precision đầu ra. Rounding sau mỗi bước có thể làm mất chữ số quan trọng, nhất là khi lấy hiệu của hai giá trị gần nhau.

## 4. Tính `sqrt(MathContext)` theo precision

`BigDecimal.sqrt(MathContext)` có từ **Java 9**, vì vậy dùng được trong Java 17+.

```java
MathContext mc8 = new MathContext(8, RoundingMode.HALF_EVEN);
BigDecimal root = new BigDecimal("2").sqrt(mc8);
// 1.4142136: tám chữ số có nghĩa
```

Lưu ý các tình huống:

1. **Đầu vào âm:** `new BigDecimal("-4").sqrt(mc8)` ném `ArithmeticException`. `BigDecimal` không trả về `NaN`.
2. **Căn chính xác:** `new BigDecimal("9").sqrt(mc8)` trả về giá trị `3`. Kết quả chính xác có thể sử dụng ít chữ số có nghĩa hơn precision được cấp.
3. **Căn vô tỉ:** căn của `2` có khai triển thập phân vô hạn. Muốn kết quả hữu hạn, truyền `MathContext` có precision dương và rounding mode phù hợp.
4. **`MathContext.UNLIMITED`:** precision bằng `0` yêu cầu kết quả chính xác. `sqrt(4)` hợp lệ nhưng `sqrt(2)` ném `ArithmeticException`, vì không có biểu diễn thập phân hữu hạn chính xác.
5. **`RoundingMode.UNNECESSARY`:** yêu cầu kết quả chính xác trong precision cho phép; sẽ ném `ArithmeticException` nếu cần làm tròn.
6. **Preferred scale:** scale ưu tiên của căn là `value.scale() / 2` (chia nguyên). Scale thực tế có thể khác do quy tắc biểu diễn/làm tròn. Không coi scale là precision, và không kỳ vọng số 0 cuối luôn được giữ.

Theo đặc tả Java 17, kết quả của `sqrt(mc)` nằm trong một `ulp` so với căn chính xác ở precision đó; với `HALF_UP`, `HALF_DOWN` hoặc `HALF_EVEN`, sai số nằm trong **nửa ulp**. Đây là tính chất của phép tính theo chữ số có nghĩa, khác với yêu cầu số chữ số sau dấu thập phân.

**Tránh chuyển qua `double`:**

```java
// Không tương đương với sqrt(mc) có chính sách thập phân:
BigDecimal unsafe = new BigDecimal(Math.sqrt(2.0));
```

`Math.sqrt` dùng `double` với số bit hữu hạn. Chuyển qua `double` có thể mất thông tin, tràn hoặc làm tròn theo chính sách khác với `MathContext`; nếu yêu cầu là số học thập phân có kiểm soát, giữ toàn bộ phép tính bằng `BigDecimal`.

## 5. Chương trình Java 17+ hoàn chỉnh, biên dịch và chạy

Lưu file **`PrecisionPowSqrtDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class PrecisionPowSqrtDemo {
    public static void main(String[] args) {
        BigDecimal base = new BigDecimal("1.2345");
        MathContext mc6 = new MathContext(6, RoundingMode.HALF_EVEN);
        MathContext mc8 = new MathContext(8, RoundingMode.HALF_EVEN);

        System.out.println("pow exact = "
                + base.pow(3).toPlainString());
        System.out.println("pow precision 6 = "
                + base.pow(3, mc6).toPlainString());
        System.out.println("sqrt(2) precision 8 = "
                + new BigDecimal("2").sqrt(mc8).toPlainString());
        System.out.println("2^-3 precision 8 = "
                + new BigDecimal("2").pow(-3, mc8).toPlainString());
        System.out.println("sqrt(9) precision 8 = "
                + new BigDecimal("9").sqrt(mc8).toPlainString());
    }
}
```

Chạy tại thư mục chứa file, với JDK 17 trở lên:

```bash
javac PrecisionPowSqrtDemo.java
java PrecisionPowSqrtDemo
```

Stdout:

```text
pow exact = 1.881365963625
pow precision 6 = 1.88137
sqrt(2) precision 8 = 1.4142136
2^-3 precision 8 = 0.125
sqrt(9) precision 8 = 3
```

`toPlainString()` chỉ định dạng chuỗi thay vì dạng ký hiệu khoa học khi có thể; nó không sửa giá trị, làm tròn lại hay tăng precision. Các kết quả trên dùng đầu vào dạng chuỗi để không mang sai số nhị phân của `double`.

## 6. Yêu cầu số chữ số sau dấu thập phân: khi nào dùng `setScale`

Nếu yêu cầu là **6 chữ số có nghĩa**, truyền `MathContext(6, ...)`. Nếu yêu cầu là **6 chữ số sau dấu thập phân**, cần tính với precision làm việc đủ cao, rồi chuyển kết quả sang scale 6:

```java
// Đặt trong main; import java.math.* ở đầu file.
int decimalPlaces = 6;
MathContext work = new MathContext(20, RoundingMode.HALF_EVEN);
BigDecimal unformatted = new BigDecimal("2").sqrt(work);
BigDecimal display = unformatted.setScale(decimalPlaces, RoundingMode.HALF_EVEN);
System.out.println(display.toPlainString()); // 1.414214
```

Với ví dụ căn của 2, precision 20 cung cấp dư chữ số cần thiết để in 6 chữ số thập phân. **Không có công thức chung “precision = số chữ số thập phân + 2” cho mọi giá trị**: kết quả rất lớn/nhỏ, phép tính phức tạp và các giá trị sát ngưỡng làm tròn cần được đánh giá riêng.

Làm tròn hai lần (*double rounding*) có thể dẫn đến kết quả cuối không trùng với việc làm tròn trực tiếp giá trị toán học chính xác. Nếu nghiệp vụ đòi kết quả đúng tới N chữ số thập phân, cần xác định ngân sách sai số, tăng precision làm việc phù hợp và kiểm chứng các tình huống sát ngưỡng. Với tài chính, quy tắc scale/rounding của nghiệp vụ phải được nêu riêng; MathContext không tự biết quy tắc tiền tệ.

Ví dụ muốn hiển thị số lượng chữ số thập phân cố định, có thể chọn `setScale(2, RoundingMode.HALF_EVEN)` sau khi tính; nếu chỉ so sánh giá trị số học, có thể giữ `BigDecimal` gốc và tránh định dạng quá sớm.

## 7. Ngoại lệ và xác thực đầu vào

Các phép toán có thể ném `ArithmeticException` nếu miền giá trị không hợp lệ, yêu cầu precision không thể đáp ứng, hoặc `UNNECESSARY` cấm làm tròn. Ví dụ (đặt trong phương thức, đã import `java.math.*`):

```java
MathContext mc = new MathContext(8, RoundingMode.HALF_EVEN);

try {
    new BigDecimal("-4").sqrt(mc);
} catch (ArithmeticException ex) {
    System.out.println("Không có căn bậc hai thực cho số âm");
}
try {
    new BigDecimal("2").sqrt(MathContext.UNLIMITED);
} catch (ArithmeticException ex) {
    System.out.println("sqrt(2) không thể biểu diễn chính xác hữu hạn");
}
try {
    new BigDecimal("2").pow(-1);
} catch (ArithmeticException ex) {
    System.out.println("pow âm cần MathContext precision dương");
}
```

Nếu precision nhận từ người dùng, kiểm tra `precision > 0` cho cách tính gần đúng này; `new MathContext(-1)` ném `IllegalArgumentException`, còn `new MathContext(0)` biểu diễn unlimited. Với `pow`, kiểm tra cả giá trị số mũ, số chữ số của số mũ so với precision và cơ số 0 khi số mũ âm.

Có thể đóng gói quy tắc thống nhất bằng lớp sau, lưu riêng thành **`DecimalMath.java`**:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

public final class DecimalMath {
    private DecimalMath() {}

    public static BigDecimal pow(BigDecimal base, int exponent, int precision) {
        Objects.requireNonNull(base, "base");
        if (precision <= 0) {
            throw new IllegalArgumentException("precision phải > 0");
        }
        MathContext mc = new MathContext(precision, RoundingMode.HALF_EVEN);
        return base.pow(exponent, mc);
    }

    public static BigDecimal sqrt(BigDecimal value, int precision) {
        Objects.requireNonNull(value, "value");
        if (precision <= 0) {
            throw new IllegalArgumentException("precision phải > 0");
        }
        MathContext mc = new MathContext(precision, RoundingMode.HALF_EVEN);
        return value.sqrt(mc);
    }
}
```

Lớp này lựa chọn `HALF_EVEN` làm chính sách mặc định *cho ví dụ*. Ứng dụng thực tế nên cho phép chọn mode theo yêu cầu nghiệp vụ. Nếu cơ số/số mũ hoặc căn không hợp lệ, ngoại lệ từ `BigDecimal` sẽ được truyền cho tầng gọi xử lý. Đừng bắt mọi `ArithmeticException` rồi trả `BigDecimal.ZERO` một cách âm thầm, vì điều đó biến lỗi thành dữ liệu sai.

## 8. Lỗi phổ biến cần tránh

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Tưởng `precision = 8` nghĩa là 8 số sau dấu phẩy | Precision đếm chữ số có nghĩa | Tách MathContext để tính và setScale để hiển thị |
| Dùng `pow(-2)` không có MathContext | Overload chính xác không nhận mũ âm | Dùng `pow(-2, mc)` với precision dương |
| Dùng `sqrt(MathContext.UNLIMITED)` cho số không có căn thập phân hữu hạn | Kết quả chính xác không biểu diễn được | Dùng precision dương và rounding mode |
| Dùng `pow(n)` cho n rất lớn khi chỉ cần kết quả làm tròn | Tạo kết quả chính xác quá tốn tài nguyên | Ưu tiên `pow(n, mc)` khi chấp nhận sai số API |
| Dùng `Math.pow` / `Math.sqrt` qua `double` | Không giữ phép tính thập phân theo MathContext | Tính trực tiếp bằng `BigDecimal` |
| So sánh `BigDecimal` bằng `equals` khi scale khác nhau | `1.0` không `equals` `1.00` dù bằng nhau về số học | Dùng `compareTo(...) == 0` khi chỉ xét giá trị |
| Làm tròn precision cuối ở mỗi bước tính | Tích lũy sai số trung gian | Chọn precision làm việc đủ cao, làm tròn ở ranh giới phù hợp |
| Dùng `new BigDecimal(0.1)` | Nhận giá trị chính xác của `double` xấp xỉ | Khởi tạo từ chuỗi hoặc `BigDecimal.valueOf` |

## 9. Tự luyện tập và kiểm chứng

1. `new BigDecimal("12.345").pow(2)` cho kết quả chính xác nào? Làm tròn với `new MathContext(4, HALF_EVEN)` còn bao nhiêu?
2. Vì sao `sqrt(4)` hoạt động với `MathContext.UNLIMITED`, nhưng `sqrt(2)` không?
3. Tính `new BigDecimal("10").pow(-2, new MathContext(5, HALF_EVEN))`; thử bỏ `MathContext` và giải thích.
4. Thiết kế cách hiện `sqrt(2)` với đúng hai chữ số sau dấu thập phân, đồng thời phân biệt precision tính toán và scale đầu ra.

Đáp án tham khảo: (1) chính xác là `152.399025`, sau làm tròn bốn chữ số có nghĩa là `152.4`; (2) căn của 2 có biểu diễn thập phân vô hạn; (3) `0.01`, còn `pow(-2)` không có MathContext ném ngoại lệ; (4) tính `sqrt` bằng MathContext đủ precision, rồi `setScale(2, HALF_EVEN)` cho `1.41`.

Khi dùng thực tế, hãy xác định **precision của phép tính**, **rounding mode** và **scale của kết quả xuất ra** như ba yêu cầu rõ ràng. `pow` giúp xử lý lũy thừa nguyên chính xác hoặc gần đúng; `sqrt` xử lý căn bậc hai không âm theo precision đã chọn. Với các công thức đòi hỏi sai số chặt chẽ, cần kiểm tra giới hạn thuật toán và kiểm thử tại các ranh giới làm tròn.

Nguồn API chính thức: [Java SE 17 — `java.math.BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html) (`pow(int)`, `pow(int, MathContext)`, `sqrt(MathContext)`).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-006">negate, abs, plus, min, max và signum</a>

<details>
<summary>Xem chi tiết</summary>

# negate, abs, plus, min, max và signum trong BigDecimal

## 1. Mục tiêu và bối cảnh

Trong Java, BigDecimal biểu diễn số thập phân với độ chính xác tùy ý, phù hợp khi cần kiểm soát chữ số và cách làm tròn (ví dụ tiền tệ). Nhóm phương thức này phục vụ hai việc khác nhau:

- **Tác động lên dấu hoặc trị tuyệt đối:** negate(), abs(), plus().
- **Xác định dấu hoặc chọn số theo thứ tự:** signum(), min(BigDecimal), max(BigDecimal).

Đây là các phương thức của java.math.BigDecimal. Chúng **không thay đổi đối tượng gốc** vì BigDecimal là kiểu bất biến (immutable). Kết quả là một giá trị BigDecimal hoặc, riêng signum(), một số nguyên int.

BigDecimal lưu giá trị theo dạng unscaledValue × 10^(-scale). Ví dụ, -12.340 có scale = 3. Hai số 2.0 và 2.00 có giá trị số học bằng nhau nhưng scale khác nhau; điều này ảnh hưởng đến kết quả biểu diễn khi dùng min/max.

## 2. Bảng tra cứu nhanh

| Phương thức | Ý nghĩa | Kết quả | Đặc điểm quan trọng |
| --- | --- | --- | --- |
| negate() | Đổi dấu x thành -x | BigDecimal | Giữ scale của x |
| abs() | Trị tuyệt đối | BigDecimal | Số âm thành dương; số không và số dương giữ nguyên giá trị |
| plus() | Dấu cộng đơn ngôi +x | BigDecimal | Không cộng thêm 1; không đổi giá trị hay scale |
| min(y) | Chọn giá trị nhỏ hơn giữa x và y | BigDecimal | Dựa trên compareTo(); khi hai giá trị bằng nhau, trả x (đối tượng gọi) |
| max(y) | Chọn giá trị lớn hơn giữa x và y | BigDecimal | Dựa trên compareTo(); khi hai giá trị bằng nhau, trả x (đối tượng gọi) |
| signum() | Xác định dấu của x | int | -1 nếu âm, 0 nếu bằng không, 1 nếu dương |

Ở bảng trên, x là BigDecimal nhận lời gọi phương thức; y là tham số BigDecimal khác. min() và max() không có phiên bản nhận MathContext.

## 3. negate(), abs(), plus(): ba phép toán đơn ngôi

- **x.negate()** cho giá trị đối của x. Số dương thành âm, số âm thành dương; với 0, dấu số học vẫn là 0.
- **x.abs()** cho độ lớn không âm của x. Có thể hình dung là x khi x không âm, hoặc x.negate() khi x âm.
- **x.plus()** là phép cộng đơn ngôi, giống ý nghĩa toán học của +x. Nó hữu ích nhất khi dùng cùng MathContext để áp dụng độ chính xác/làm tròn.

Ba phương thức không tham số bảo toàn scale, kể cả các số 0 có phần thập phân. Ví dụ, -12.340.negate() cho 12.340 chứ không tự rút gọn thành 12.34.

### Ví dụ Java 17: đổi dấu và giữ nguyên dữ liệu gốc

Lưu thành BigDecimalUnaryDemo.java:

~~~java
import java.math.BigDecimal;

public class BigDecimalUnaryDemo {
    public static void main(String[] args) {
        BigDecimal x = new BigDecimal("-12.340");

        System.out.println("x       = " + x);
        System.out.println("negate  = " + x.negate());
        System.out.println("abs     = " + x.abs());
        System.out.println("plus    = " + x.plus());
        System.out.println("signum  = " + x.signum());
        System.out.println("scale   = " + x.scale());
        System.out.println("x after = " + x);
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac BigDecimalUnaryDemo.java
java BigDecimalUnaryDemo
~~~

Đầu ra:

~~~text
x       = -12.340
negate  = 12.340
abs     = 12.340
plus    = -12.340
signum  = -1
scale   = 3
x after = -12.340
~~~

Lệnh cuối cho thấy lời gọi không sửa x. Không dùng toán tử trừ đơn ngôi hoặc Math.abs(x) với BigDecimal; hãy gọi trực tiếp negate() và abs().

### Số 0 và biểu diễn

~~~java
import java.math.BigDecimal;

public class BigDecimalZeroDemo {
    public static void main(String[] args) {
        BigDecimal z = new BigDecimal("-0.00");
        System.out.println(z);               // 0.00
        System.out.println(z.negate());      // 0.00
        System.out.println(z.abs());         // 0.00
        System.out.println(z.signum());      // 0
        System.out.println(z.scale());       // 2
        System.out.println(z.compareTo(BigDecimal.ZERO)); // 0
    }
}
~~~

BigDecimal không duy trì một dấu âm có ý nghĩa số học riêng cho zero. Chuỗi đầu vào "-0.00" trở thành giá trị 0.00 với scale 2; signum() vẫn trả 0. Nếu chỉ cần nhận biết có bằng 0 hay không, dùng signum() == 0 hoặc compareTo(BigDecimal.ZERO) == 0; đừng so sánh chuỗi hiển thị.

## 4. signum(): quyết định theo dấu mà không làm mất độ chính xác

signum() trả về đúng ba trạng thái:

| Giá trị | Kết quả signum() | Ý nghĩa |
| --- | ---: | --- |
| -0.001 | -1 | Âm |
| 0, 0.00, -0.00 | 0 | Bằng không |
| 0.001 | 1 | Dương |

Dùng khi nghiệp vụ rẽ nhánh theo âm/dương/không, chẳng hạn phân loại biến động số dư:

~~~java
import java.math.BigDecimal;

public class BigDecimalSignDemo {
    static String describe(BigDecimal amount) {
        return switch (amount.signum()) {
            case -1 -> "decrease";
            case 0 -> "unchanged";
            case 1 -> "increase";
            default -> throw new IllegalStateException("Unexpected sign");
        };
    }

    public static void main(String[] args) {
        System.out.println(describe(new BigDecimal("-7.50")));
        System.out.println(describe(new BigDecimal("0.00")));
        System.out.println(describe(new BigDecimal("7.50")));
    }
}
~~~

Đầu ra:

~~~text
decrease
unchanged
increase
~~~

signum() không phải phép làm tròn và không phụ thuộc scale. Không suy ra số chữ số, độ lớn hay dấu gốc của chuỗi văn bản từ kết quả -1/0/1.

## 5. min() và max(): so sánh giá trị số học

Với x.min(y), phương thức trả về giá trị nhỏ hơn; với x.max(y), trả về giá trị lớn hơn. Cả hai dùng thứ tự tự nhiên của BigDecimal, tức kết quả compareTo().

Quy tắc chọn cụ thể:

- Nếu x.compareTo(y) < 0, x.min(y) trả x và x.max(y) trả y.
- Nếu x.compareTo(y) > 0, x.min(y) trả y và x.max(y) trả x.
- Nếu x.compareTo(y) == 0, **cả x.min(y) và x.max(y) đều trả x**, kể cả khi scale của x và y khác nhau.

### Ví dụ Java 17: thứ tự và scale

Lưu thành BigDecimalMinMaxDemo.java:

~~~java
import java.math.BigDecimal;

public class BigDecimalMinMaxDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");
        BigDecimal c = new BigDecimal("3.5");

        System.out.println("min(a,c) = " + a.min(c));
        System.out.println("max(a,c) = " + a.max(c));
        System.out.println("compare  = " + a.compareTo(b));
        System.out.println("equals   = " + a.equals(b));
        System.out.println("min(a,b) = " + a.min(b));
        System.out.println("min(b,a) = " + b.min(a));
        System.out.println("max(a,b) = " + a.max(b));
    }
}
~~~

Chạy:

~~~bash
javac BigDecimalMinMaxDemo.java
java BigDecimalMinMaxDemo
~~~

Đầu ra:

~~~text
min(a,c) = 2.0
max(a,c) = 3.5
compare  = 0
equals   = false
min(a,b) = 2.0
min(b,a) = 2.00
max(a,b) = 2.0
~~~

**Điểm dễ nhầm:** compareTo() xem 2.0 và 2.00 là bằng nhau về mặt số học; equals() còn xét scale nên trả false. min/max chọn theo compareTo(), không theo equals(). Trường hợp bằng nhau, toán hạng đứng bên trái quyết định cách biểu diễn/scale của kết quả. Vì vậy kết quả của min(a,b) có thể hiển thị khác min(b,a) dù cùng giá trị.

min/max không tự làm tròn, không đổi scale của BigDecimal được chọn và không cộng/trừ hai số. Để giới hạn một giá trị vào đoạn [lower, upper] khi lower <= upper, có thể dùng x.max(lower).min(upper); cần định nghĩa trước chính sách scale nếu kết quả sẽ được lưu hoặc hiển thị theo chuẩn cố định.

## 6. Các phiên bản nhận MathContext

negate(MathContext), abs(MathContext) và plus(MathContext) có overload cho phép **áp dụng độ chính xác (precision) và quy tắc làm tròn**. Chúng trả về kết quả tương ứng được làm tròn theo MathContext. MathContext kiểm soát **số chữ số có nghĩa**, không đồng nghĩa với số chữ số sau dấu thập phân; muốn cố định số lẻ thường dùng setScale() với RoundingMode phù hợp.

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalContextDemo {
    public static void main(String[] args) {
        BigDecimal x = new BigDecimal("-123.4567");
        MathContext mc = new MathContext(4, RoundingMode.HALF_UP);

        System.out.println("original    = " + x);
        System.out.println("plus(mc)    = " + x.plus(mc));
        System.out.println("negate(mc)  = " + x.negate(mc));
        System.out.println("abs(mc)     = " + x.abs(mc));
        System.out.println("exact abs   = " + x.abs());
    }
}
~~~

Đầu ra:

~~~text
original    = -123.4567
plus(mc)    = -123.5
negate(mc)  = 123.5
abs(mc)     = 123.5
exact abs   = 123.4567
~~~

Ở đây 4 là **precision = 4 chữ số có nghĩa**; chữ số tiếp theo là 5 nên HALF_UP làm tròn. Không có MathContext, ba phép toán đơn ngôi giữ độ chính xác ban đầu. Với MathContext.UNLIMITED (precision = 0), không yêu cầu làm tròn. RoundingMode.UNNECESSARY cùng một precision không đủ lớn có thể phát sinh ArithmeticException nếu kết quả bắt buộc làm tròn.

Đặc biệt, plus(mc) không đồng nghĩa cộng thêm số dương nào: nó áp dụng phép cộng đơn ngôi rồi làm tròn theo mc. Việc dùng MathContext cho nghiệp vụ tính tiền phải tuân thủ quy tắc làm tròn thực tế của nghiệp vụ.

## 7. Tình huống ứng dụng: xử lý số dư và giới hạn

Giả sử một khoản điều chỉnh có dấu âm biểu thị tiền rời tài khoản. Ta có thể:

1. Dùng signum() để phân loại chiều biến động.
2. Dùng abs() để hiển thị độ lớn của khoản điều chỉnh.
3. Dùng negate() để tạo giá trị đảo chiều cho bút toán hoàn tác (khi quy tắc nghiệp vụ cho phép).
4. Dùng min()/max() để áp dụng các ngưỡng số học.

~~~java
import java.math.BigDecimal;

public class BigDecimalBusinessDemo {
    public static void main(String[] args) {
        BigDecimal change = new BigDecimal("-125.50");
        BigDecimal lower = new BigDecimal("-100.00");
        BigDecimal upper = new BigDecimal("100.00");

        BigDecimal limited = change.max(lower).min(upper);

        System.out.println("sign    = " + change.signum());
        System.out.println("display = " + change.abs());
        System.out.println("reverse = " + change.negate());
        System.out.println("limited = " + limited);
        System.out.println("source  = " + change);
    }
}
~~~

Đầu ra:

~~~text
sign    = -1
display = 125.50
reverse = 125.50
limited = -100.00
source  = -125.50
~~~

Đoạn này minh họa biến đổi giá trị, **không tự thực hiện giao dịch tài chính**. Ví dụ cũng cho thấy kẹp giá trị (clamp) chỉ giới hạn số; việc chọn ngưỡng và tạo bút toán đảo chiều phải thuộc về quy tắc nghiệp vụ. Khi dữ liệu cần 2 chữ số thập phân, thống nhất scale/rounding tại ranh giới nhập, lưu và xuất thay vì giả định min/max luôn trả scale 2.

## 8. Lỗi thường gặp và cách tránh

1. **Nhầm plus() với add().** x.plus() giữ giá trị x; để cộng hai BigDecimal dùng x.add(y). Tương tự, negate() chỉ đảo dấu, còn x.subtract(y) mới là phép trừ hai số.
2. **Dùng toán tử của kiểu nguyên thủy.** BigDecimal không dùng trực tiếp +, -, <, > hoặc Math.abs(BigDecimal). Dùng add/subtract/compareTo/abs/negate theo API.
3. **Nhầm so sánh bằng với equals().** x.equals(y) có thể false khi scale khác; x.compareTo(y) == 0 kiểm tra bằng nhau theo giá trị số học. min/max dùng compareTo().
4. **Giả định min/max chuẩn hóa dữ liệu.** Trong trường hợp hai giá trị bằng nhau, chúng chọn toán hạng gọi phương thức; không tự ép scale. Nếu cần chuẩn hóa, áp dụng setScale(...) theo quy tắc riêng.
5. **Cấp BigDecimal bằng new BigDecimal(0.1).** Constructor nhận double giữ đúng giá trị nhị phân đã được biểu diễn gần đúng, có thể gây số thập phân dài bất ngờ. Với hằng số thập phân dùng new BigDecimal("0.1") hoặc BigDecimal.valueOf(0.1).
6. **Để null làm đối số.** x.min(null) hoặc x.max(null) gây NullPointerException. Bảo đảm x và đối số là BigDecimal hợp lệ trước khi gọi.
7. **Nhầm MathContext precision với scale.** new MathContext(4) thường giới hạn 4 chữ số có nghĩa, không phải 4 chữ số lẻ. Đừng làm tròn tùy tiện nếu độ chính xác nguyên gốc còn cần thiết.
8. **Tưởng đổi dấu làm thay đổi biến nguồn.** BigDecimal immutable; cần lưu kết quả, ví dụ x = x.negate(), khi thật sự muốn biến x trỏ tới đối tượng mang giá trị mới.
9. **Kiểm tra số âm bằng chuỗi.** signum() xử lý chính xác cả 0.00; kiểm tra chuỗi bắt đầu bằng dấu '-' không phải phép kiểm tra dấu số học tin cậy.

## 9. Tự kiểm tra

**Câu 1.** Với x = new BigDecimal("-0.000"), x.signum() bằng bao nhiêu? x.abs().scale() bằng bao nhiêu?

**Đáp án:** 0 và 3. BigDecimal nhận giá trị zero, giữ scale là 3 khi gọi abs() không tham số.

**Câu 2.** Cho a = new BigDecimal("1.0"), b = new BigDecimal("1.00"). a.min(b) và b.min(a) có hiển thị như nhau không?

**Đáp án:** a.min(b) là 1.0, b.min(a) là 1.00. compareTo() bằng 0 nên nhận lại toán hạng gọi phương thức.

**Câu 3.** Muốn đổi -9.25 thành 9.25 thì dùng phương thức gì? Nếu x đã dương, abs() có làm x âm không?

**Đáp án:** x.negate() hoặc x.abs() đều cho 9.25 khi x = -9.25, nhưng ý nghĩa khác nhau; abs() không bao giờ làm một số dương trở thành âm.

**Câu 4.** Điều gì khác nhau giữa new BigDecimal("12.3456").plus() và .plus(new MathContext(4, RoundingMode.HALF_UP))?

**Đáp án:** plus() giữ 12.3456; phiên bản nhận MathContext làm tròn còn 12.35 (4 chữ số có nghĩa).

## 10. Ghi nhớ

- **negate()** đảo dấu; **abs()** lấy độ lớn không âm; **plus()** giữ giá trị (hoặc áp dụng làm tròn khi truyền MathContext).
- **signum()** trả -1/0/1 để kiểm tra dấu chính xác.
- **min()/max()** chọn theo compareTo(); khi bằng nhau, trả toán hạng gọi phương thức và giữ biểu diễn của nó.
- BigDecimal bất biến, scale có ý nghĩa, và việc làm tròn phải được chỉ định rõ khi cần.

Tham khảo API chuẩn trong tài liệu Java SE 17: java.math.BigDecimal, java.math.MathContext và java.math.RoundingMode.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-007">setScale và việc trả về đối tượng mới</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.setScale() và việc trả về đối tượng mới

## 1. Mục tiêu bài học

Trong Java, `BigDecimal` biểu diễn số thập phân với độ chính xác tùy ý, thường được dùng cho tiền tệ, thuế, tỷ lệ và các phép tính cần kiểm soát chữ số thập phân. `setScale()` đặt **scale** của giá trị kết quả; nó **không sửa đối tượng `BigDecimal` ban đầu** vì lớp này bất biến (*immutable*). Phải sử dụng giá trị trả về nếu cần kết quả đã đổi scale.

```java
BigDecimal price = new BigDecimal("12.345");
price.setScale(2, RoundingMode.HALF_UP); // Bỏ qua kết quả!
System.out.println(price);              // 12.345

BigDecimal rounded = price.setScale(2, RoundingMode.HALF_UP);
System.out.println(rounded); // 12.35
System.out.println(price);   // 12.345
```

Các đoạn minh họa rời trong bài giả định đã `import java.math.BigDecimal;` và `import java.math.RoundingMode;`. Ví dụ đầy đủ có thể biên dịch nằm ở phần 5.

## 2. Scale khác gì precision?

`BigDecimal` biểu diễn giá trị bằng **unscaled value** (số nguyên) và **scale** theo công thức:

```text
giá trị = unscaledValue × 10^(-scale)
```

Ví dụ, `new BigDecimal("123.4500")` có `unscaledValue() = 1234500`, `scale() = 4` và `precision() = 7`. Scale cho biết vị trí dấu thập phân; precision là tổng số chữ số có nghĩa trong biểu diễn. Sau `setScale(2)`, kết quả `123.45` có unscaled value `12345` và scale `2`, còn trị số toán học giữ nguyên.

`new BigDecimal("2.5")` có scale 1, trong khi `new BigDecimal("2.500")` có scale 3. Cùng trị số nhưng khác biểu diễn. `setScale(2)` yêu cầu **hai vị trí sau dấu phẩy**, không phải hai chữ số có nghĩa; để giới hạn chữ số có nghĩa, tìm hiểu `MathContext` và các phép toán áp dụng nó.

Scale còn có thể âm: `new BigDecimal("1234").setScale(-2, RoundingMode.HALF_UP)` biểu diễn giá trị `1200` ở scale -2 và có thể in `1.2E+3`. Scale âm tương ứng làm tròn đến hàng chục, hàng trăm hoặc lớn hơn.

## 3. Ba overload và quy tắc thay đổi scale

| API | Ý nghĩa |
| --- | --- |
| `setScale(int newScale)` | Chỉ cho phép thay đổi scale **chính xác**; nếu phải bỏ chữ số khác 0 thì ném `ArithmeticException`. |
| `setScale(int newScale, RoundingMode roundingMode)` | Cho phép làm tròn khi cần theo chế độ được chọn. |
| `setScale(int newScale, int roundingMode)` | Overload dùng hằng số `int` cũ, đã deprecated; nên dùng `RoundingMode`. |

**Tăng scale** bổ sung chữ số 0 mà không đổi giá trị số học:

```java
BigDecimal x = new BigDecimal("7.2");
System.out.println(x.setScale(3)); // 7.200
System.out.println(x);             // 7.2
```

**Giảm scale** phải xét phần bị loại:

```java
new BigDecimal("7.2300").setScale(2);                    // 7.23, chính xác
new BigDecimal("7.235").setScale(2, RoundingMode.HALF_UP); // 7.24
new BigDecimal("7.235").setScale(2);                    // ArithmeticException
```

Phát biểu “`setScale(2)` luôn làm tròn” là sai: overload một tham số không có chế độ làm tròn mặc định. Nhưng “giảm scale luôn lỗi” cũng sai: giảm từ `7.2300` xuống `7.23` không mất chữ số có giá trị khác 0 nên vẫn chính xác.

Nếu dùng `RoundingMode.UNNECESSARY`, phương thức chủ động ném `ArithmeticException` mỗi khi yêu cầu phải làm tròn. Cách này hữu ích khi kiểm tra dữ liệu đầu vào phải đã đúng scale.

## 4. Hiểu và lựa chọn RoundingMode

Ví dụ số dương `2.345` về scale 2:

| Chế độ | Kết quả | Quy tắc |
| --- | --- | --- |
| `HALF_UP` | `2.35` | Đúng giữa hai mức thì đi xa 0. |
| `HALF_EVEN` | `2.34` | Đúng giữa thì chọn chữ số cuối giữ lại là chẵn; `2.355` thành `2.36`. |
| `DOWN` | `2.34` | Cắt phần bị bỏ, hướng về 0. |
| `UP` | `2.35` | Khi có phần bị bỏ khác 0, đi xa 0. |
| `CEILING` | `2.35` | Làm tròn về dương vô cực. |
| `FLOOR` | `2.34` | Làm tròn về âm vô cực. |
| `UNNECESSARY` | Exception | Không chấp nhận mất độ chính xác. |

Số âm cho thấy `CEILING` và `FLOOR` khác `UP` và `DOWN`:

```java
BigDecimal negative = new BigDecimal("-2.345");
System.out.println(negative.setScale(2, RoundingMode.CEILING)); // -2.34
System.out.println(negative.setScale(2, RoundingMode.FLOOR));   // -2.35
System.out.println(negative.setScale(2, RoundingMode.HALF_UP)); // -2.35
```

`HALF_EVEN` có thể giảm thiên lệch tổng thể khi nhiều kết quả rơi đúng giữa hai mức. Nhưng yêu cầu nghiệp vụ, quy định tài chính hoặc quy tắc tiền tệ mới là căn cứ để chọn chế độ; không có `RoundingMode` nào là đúng cho mọi hệ thống.

## 5. Chương trình Java 17+ hoàn chỉnh

Lưu vào tệp `BigDecimalSetScaleDemo.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalSetScaleDemo {
    public static void main(String[] args) {
        BigDecimal original = new BigDecimal("12.345");
        BigDecimal rounded = original.setScale(2, RoundingMode.HALF_UP);
        System.out.println("original = " + original);
        System.out.println("rounded = " + rounded);

        BigDecimal amount = new BigDecimal("2.5");
        BigDecimal expanded = amount.setScale(3);
        System.out.println("expanded = " + expanded);
        System.out.println("amount unchanged = " + amount);

        BigDecimal exact = new BigDecimal("12.340").setScale(2);
        System.out.println("exact = " + exact);

        BigDecimal tie = new BigDecimal("2.345");
        System.out.println("HALF_UP = " + tie.setScale(2, RoundingMode.HALF_UP));
        System.out.println("HALF_EVEN = " + tie.setScale(2, RoundingMode.HALF_EVEN));

        BigDecimal sameScale = new BigDecimal("1.20");
        BigDecimal result = sameScale.setScale(2);
        System.out.println("same value = " + sameScale.equals(result));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac BigDecimalSetScaleDemo.java
java BigDecimalSetScaleDemo
```

Kết quả chuẩn:

```text
original = 12.345
rounded = 12.35
expanded = 2.500
amount unchanged = 2.5
exact = 12.34
HALF_UP = 2.35
HALF_EVEN = 2.34
same value = true
```

Giải thích: `rounded` nhận kết quả được làm tròn, nhưng `original` giữ nguyên. `expanded` có thêm hai số 0 vì scale tăng từ 1 lên 3. `exact` chỉ loại chữ số 0 nên không cần rounding mode. `tie` minh họa khác biệt giữa hai chính sách làm tròn khi nằm đúng giữa. Trường hợp `sameScale` so sánh bằng giá trị và scale, không kiểm tra sự khác biệt tham chiếu.

## 6. “Trả về đối tượng mới” phải hiểu đúng

Về mặt ngữ nghĩa, `setScale()` trả về một `BigDecimal` có scale mới được yêu cầu mà **không thay đổi trạng thái của đối tượng gốc**:

```java
BigDecimal a = new BigDecimal("10.555");
BigDecimal b = a.setScale(2, RoundingMode.HALF_UP);
// a: 10.555, scale = 3
// b: 10.56, scale = 2
```

Tuy nhiên, không được suy ra JVM **luôn cấp phát instance vật lý khác**. Khi scale đã bằng scale yêu cầu, hoặc nhờ khả năng tái sử dụng/cache, một triển khai có thể trả lại chính tham chiếu trước đó. Không được viết phép kiểm tra `a != a.setScale(a.scale())` với kỳ vọng luôn đúng. Hợp đồng API là **giá trị kết quả + tính bất biến**, không phải danh tính tham chiếu mới ở mọi lời gọi.

Cần phân biệt các cách dùng sau:

- `amount.setScale(2, mode);`: kết quả bị bỏ; `amount` không đổi.
- `amount = amount.setScale(2, mode);`: gán biến địa phương sang tham chiếu kết quả; đối tượng gốc vẫn không đổi.
- `return amount.setScale(2, mode);`: trả kết quả đã chuẩn hóa cho caller.
- Với biến `final BigDecimal amount`, có thể tạo `BigDecimal rounded = amount.setScale(...)`, nhưng không được gán lại `amount`.

Đối tượng `BigDecimal` bất biến nên chia sẻ chính đối tượng giữa các luồng là an toàn về mặt trạng thái nội tại. Nếu nhiều luồng cùng đọc và gán lại **một biến/field dùng chung** chứa tham chiếu `BigDecimal`, vẫn cần đồng bộ hoặc cấu trúc dữ liệu luồng an toàn phù hợp; tính bất biến không tự động biến nhiều phép cập nhật tham chiếu thành một thao tác nguyên tử.

## 7. Ảnh hưởng đến equals(), compareTo() và collection

`setScale()` có thể làm thay đổi kết quả `equals()` dù giá trị số học giữ nguyên:

```java
BigDecimal x = new BigDecimal("2.0");
BigDecimal y = x.setScale(2); // 2.00

System.out.println(x.equals(y));    // false
System.out.println(x.compareTo(y)); // 0
System.out.println(x.scale());      // 1
System.out.println(y.scale());      // 2
```

`equals()` của `BigDecimal` xét cả giá trị biểu diễn và scale; `compareTo()` so sánh theo trị số toán học. Do đó, với mục tiêu “hai số có bằng nhau về số học không?”, thường dùng `compareTo(...) == 0`. Còn `HashSet<BigDecimal>` và khóa `HashMap<BigDecimal, ...>` dùng `equals()/hashCode()`: `2.0` và `2.00` có thể là hai khóa khác nhau. Nếu nghiệp vụ yêu cầu khóa chuẩn hóa, hãy áp dụng một chiến lược scale thống nhất **trước khi lưu/truy vấn**.

`stripTrailingZeros()` cũng trả về một `BigDecimal` và có thể biến đổi scale; nó không thay thế `setScale(n, mode)` khi yêu cầu số chữ số thập phân cố định. Cũng cần nhớ định dạng hiển thị chuỗi (ví dụ tiền tệ theo locale) là một trách nhiệm riêng: không suy ra hợp đồng hiển thị chỉ từ `BigDecimal.toString()`.

## 8. Tình huống thực tế: làm tròn số tiền

Giả sử nghiệp vụ yêu cầu làm tròn số tiền phải trả về hai chữ số sau dấu phẩy theo `HALF_UP`. Chương trình độc lập sau thể hiện cách viết hàm trả về kết quả, giữ nguyên đầu vào:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneyExample {
    static BigDecimal toTwoDecimals(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static void main(String[] args) {
        BigDecimal raw = new BigDecimal("19.995");
        BigDecimal payable = toTwoDecimals(raw);
        System.out.println("raw = " + raw);
        System.out.println("payable = " + payable);
    }
}
```

Lưu thành `MoneyExample.java`, chạy `javac MoneyExample.java` rồi `java MoneyExample`:

```text
raw = 19.995
payable = 20.00
```

Hàm `toTwoDecimals` chuẩn hóa dữ liệu tại ranh giới nghiệp vụ. Cần quyết định **làm tròn ở thời điểm nào**: từng dòng hóa đơn, sau khi cộng tổng, trước/sau khi tính thuế... Làm tròn sớm ở mọi bước có thể làm tổng cuối khác so với làm tròn một lần. Việc chọn scale 2 trong ví dụ chỉ là quy ước giả định, không đúng cho mọi loại tiền và quy định.

Nếu cần kiểm tra giá trị đầu vào đã đúng scale mà không được làm tròn:

```java
BigDecimal valid = new BigDecimal("10.00");
BigDecimal invalid = new BigDecimal("10.001");
System.out.println(valid.setScale(2, RoundingMode.UNNECESSARY)); // 10.00
// invalid.setScale(2, RoundingMode.UNNECESSARY) ném ArithmeticException.
```

## 9. Những lỗi thường gặp

1. **Không giữ giá trị trả về:** gọi `x.setScale(2, mode)` rồi tiếp tục dùng `x`. Cách sửa: gán cho biến kết quả, gán lại biến nếu được phép, hoặc return.
2. **Tin rằng overload một tham số luôn làm tròn:** `new BigDecimal("1.235").setScale(2)` ném `ArithmeticException`. Nếu có thể mất chữ số, chọn `RoundingMode` theo nghiệp vụ.
3. **Tin rằng giảm scale luôn lỗi:** `new BigDecimal("1.2300").setScale(2)` hợp lệ vì chính xác.
4. **Khởi tạo sai từ `double`:** `new BigDecimal(0.1)` giữ chính xác giá trị nhị phân của `double` (vốn không bằng chính xác 0.1 thập phân). Nên tạo bằng `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)` trong tình huống phù hợp.
5. **Nhầm scale và precision:** `setScale(2)` định vị dấu thập phân chứ không giới hạn hai chữ số có nghĩa; dùng `MathContext` khi bài toán yêu cầu precision.
6. **So sánh sai:** `==` xét tham chiếu; `equals()` xét thêm scale; `compareTo()` xét giá trị số học.
7. **Đợi chia rồi mới gọi `setScale()`:** `BigDecimal.ONE.divide(new BigDecimal("3"))` có thể ném `ArithmeticException` vì phần thập phân vô hạn; `setScale()` không cứu được phép chia đã lỗi. Dùng overload `divide(divisor, scale, roundingMode)` khi muốn chia có làm tròn.
8. **Nhầm các phương thức bất biến:** `add()`, `subtract()`, `multiply()`, `divide()` và `setScale()` đều trả giá trị; phải dùng kết quả khi cần.
9. **Tùy tiện làm tròn nhiều lần:** kết quả tổng có thể đổi nếu làm tròn trung gian. Viết kiểm thử cho các ngưỡng như `1.005`, `-1.005`, `2.345` và quy tắc nghiệp vụ đã chọn.

## 10. Câu hỏi tự kiểm tra

- `new BigDecimal("5.200").setScale(1)` có lỗi không? **Không**: trả `5.2` vì chỉ bỏ số 0.
- `new BigDecimal("5.201").setScale(1)` có lỗi không? **Có**: `ArithmeticException` do phải bỏ chữ số có giá trị khác 0.
- Sau `BigDecimal x = new BigDecimal("1.234"); x.setScale(2, RoundingMode.DOWN);`, `x` có giá trị gì? Vẫn **`1.234`**, vì kết quả `1.23` không được giữ.
- `new BigDecimal("8").setScale(2).toString()` trả gì? **`"8.00"`**.
- Tại sao `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` sai? Vì hai đối tượng khác scale, dù `compareTo()` trả 0.
- Vì sao không khẳng định `setScale()` luôn tạo instance mới? Vì API cam kết tính bất biến và kết quả chứ không cam kết danh tính tham chiếu luôn khác nhau.

**Ghi nhớ:** `setScale()` trả về giá trị có scale được yêu cầu; nó không sửa `BigDecimal` ban đầu. Chọn quy tắc làm tròn tường minh, luôn sử dụng kết quả và phân biệt scale với trị số toán học.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-008">movePointLeft, movePointRight và scaleByPowerOfTen</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal: movePointLeft, movePointRight và scaleByPowerOfTen

Ba phương thức `movePointLeft(int n)`, `movePointRight(int n)` và `scaleByPowerOfTen(int n)` của `java.math.BigDecimal` đều giúp nhân hoặc chia một số thập phân với lũy thừa của 10 **một cách chính xác**, không đi qua phép tính dấu phẩy động `double`. Điểm khác nhau quan trọng nằm ở **hướng dịch chuyển, cách giữ `scale` và cách biểu diễn kết quả**. Hiểu những điểm này giúp xử lý tiền tệ, đổi đơn vị thập phân và số ở dạng khoa học mà không làm thay đổi giá trị ngoài ý muốn.

## 1. Nền tảng: giá trị số và scale

Một `BigDecimal` có hai thành phần cốt lõi:

- `unscaledValue()`: số nguyên không chứa dấu thập phân.
- `scale()`: số chữ số thập phân theo quy tắc `value = unscaledValue × 10^(-scale)`. `scale` có thể dương, bằng 0 hoặc âm.

| Cách tạo | `toString()` | `unscaledValue()` | `scale()` | Giá trị |
|---|---|---:|---:|---:|
| `new BigDecimal("123.45")` | `123.45` | `12345` | 2 | 123,45 |
| `new BigDecimal("123.4500")` | `123.4500` | `1234500` | 4 | 123,45 |
| `new BigDecimal("123")` | `123` | `123` | 0 | 123 |
| `new BigDecimal("1.23E+3")` | `1.23E+3` | `123` | -1 | 1230 |

Do đó, hai đối tượng `BigDecimal` có thể biểu diễn **cùng giá trị số** nhưng khác `scale`. Các phương thức trong bài không làm tròn số; tuy nhiên, chúng có thể tạo kết quả khác nhau về `scale`, số chữ số 0 và `toString()`.

## 2. Bảng so sánh ba phương thức

Với `x` là giá trị ban đầu, `s = x.scale()` và `n` là số nguyên:

| Phương thức | Giá trị toán học mới | `scale` kết quả | Ý nghĩa |
|---|---|---|---|
| `x.movePointLeft(n)` | `x × 10^(-n)` | `max(s + n, 0)` | Dịch dấu thập phân sang trái `n` vị trí khi `n > 0` |
| `x.movePointRight(n)` | `x × 10^n` | `max(s - n, 0)` | Dịch dấu thập phân sang phải `n` vị trí khi `n > 0` |
| `x.scaleByPowerOfTen(n)` | `x × 10^n` | `s - n` | Thay đổi số mũ thập phân và giữ `scale` âm nếu cần |

**Quy tắc dấu của `n`:**

- `n > 0`: `movePointLeft` chia cho `10^n`; `movePointRight` và `scaleByPowerOfTen` nhân với `10^n`.
- `n < 0`: hướng dịch chuyển đảo lại. Ví dụ `movePointLeft(-2)` tương đương về giá trị với `movePointRight(2)`; `scaleByPowerOfTen(-2)` là chia cho 100.
- `n = 0`: giá trị số không đổi. Tuy nhiên `movePointLeft(0)` và `movePointRight(0)` vẫn có thể chuẩn hóa `scale` âm thành 0; `scaleByPowerOfTen(0)` giữ nguyên `scale`.

Đặc biệt, **`movePointRight(n)` và `scaleByPowerOfTen(n)` bằng nhau về giá trị số, nhưng không nhất thiết bằng nhau theo `equals()`**.

## 3. Ví dụ Java 17+ chạy độc lập

Tạo tệp `BigDecimalShiftDemo.java`:

~~~java
import java.math.BigDecimal;

public class BigDecimalShiftDemo {
    public static void main(String[] args) {
        BigDecimal x = new BigDecimal("12.3400");

        show("original", x);
        show("left(2)", x.movePointLeft(2));
        show("right(3)", x.movePointRight(3));
        show("power(3)", x.scaleByPowerOfTen(3));

        // Chứng minh BigDecimal bất biến.
        System.out.println("original after calls: " + x);

        BigDecimal y = new BigDecimal("1.23");
        BigDecimal right = y.movePointRight(3);
        BigDecimal power = y.scaleByPowerOfTen(3);

        show("1.23 right(3)", right);
        show("1.23 power(3)", power);
        System.out.println("compareTo = " + right.compareTo(power));
        System.out.println("equals = " + right.equals(power));
        System.out.println("plain right = " + right.toPlainString());
        System.out.println("plain power = " + power.toPlainString());
    }

    private static void show(String name, BigDecimal value) {
        System.out.println(name + ": " + value + " | scale=" + value.scale());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BigDecimalShiftDemo.java
java BigDecimalShiftDemo
~~~

Kết quả chuẩn:

~~~text
original: 12.3400 | scale=4
left(2): 0.123400 | scale=6
right(3): 12340.0 | scale=1
power(3): 12340.0 | scale=1
original after calls: 12.3400
1.23 right(3): 1230 | scale=0
1.23 power(3): 1.23E+3 | scale=-1
compareTo = 0
equals = false
plain right = 1230
plain power = 1230
~~~

Giải thích từng thay đổi:

1. `12.3400` có `scale = 4`. `movePointLeft(2)` tạo `0.123400` với `scale = 6`: dịch giá trị sang trái hai bậc 10 và giữ những số 0 thập phân.
2. `movePointRight(3)` tạo `12340.0` với `scale = 1`; ở đây `scaleByPowerOfTen(3)` cũng có `scale = 1` nên kết quả biểu diễn giống nhau.
3. `1.23` có `scale = 2`. Dịch phải ba vị trí làm `s - n = -1`: `movePointRight(3)` chuẩn hóa thành `1230`, `scale = 0`; `scaleByPowerOfTen(3)` giữ `scale = -1`, in ra `1.23E+3`.
4. `compareTo() == 0` vì cùng giá trị 1230, nhưng `equals() == false` vì `BigDecimal.equals()` còn xét `scale`. `toPlainString()` hiển thị cả hai là `1230`, song không làm thay đổi bản thân đối tượng.
5. Mọi thao tác trả về **BigDecimal mới**; biến `x` ban đầu vẫn là `12.3400`.

## 4. movePointLeft(n): dịch sang trái

`x.movePointLeft(n)` thực hiện phép nhân `x × 10^(-n)`. Với `n > 0`, dấu thập phân chuyển sang trái; nếu cần, Java thêm các số 0 ở đầu phần trị.

~~~java
BigDecimal a = new BigDecimal("12345.67");
System.out.println(a.movePointLeft(2)); // 123.4567
System.out.println(a.movePointLeft(5)); // 0.1234567
System.out.println(a.movePointLeft(-1)); // 123456.7
~~~

Ba dòng này là **đoạn minh họa đặt bên trong một phương thức Java**, không phải một chương trình hoàn chỉnh; đoạn chương trình ở mục 3 là ví dụ biên dịch độc lập.

Lưu ý về `scale`: `new BigDecimal("1E+3")` có giá trị 1000 nhưng `scale = -3`. Gọi `movePointLeft(2)` cho giá trị 10 và `scale = 0`, không giữ `scale = -1`. Đây là điểm khác biệt với `scaleByPowerOfTen(-2)`, vốn tạo giá trị 10 nhưng vẫn giữ `scale = -1`.

Ví dụ thực tế: từ **đơn vị nhỏ nhất** sang đơn vị lớn hơn 100 lần, `new BigDecimal("12345").movePointLeft(2)` cho `123.45`. Chỉ nên dùng hệ số 100 nếu hệ đơn vị đang xử lý thực sự có tỷ lệ 100:1.

## 5. movePointRight(n): dịch sang phải

`x.movePointRight(n)` thực hiện `x × 10^n`. Với `n > 0`, dấu thập phân chuyển sang phải; nếu không còn chữ số sau dấu thập phân, kết quả có `scale = 0` và thêm số 0 vào phần nguyên khi cần.

~~~java
BigDecimal a = new BigDecimal("12.345");
System.out.println(a.movePointRight(2));  // 1234.5
System.out.println(a.movePointRight(4));  // 123450
System.out.println(a.movePointRight(-2)); // 0.12345
~~~

Khi dùng để đổi tiền thành đơn vị nhỏ nhất, **dịch dấu thập phân không đảm bảo số nguyên**. Ví dụ `12.345 × 100 = 1234.5`; nếu đơn vị nhỏ nhất phải nguyên thì phải kiểm tra hoặc áp dụng quy tắc làm tròn rõ ràng, không dùng `intValue()` để âm thầm mất phần lẻ.

Hai phương thức `movePointLeft` và `movePointRight` có tính đối xứng **theo giá trị số**. Không nên mặc định phép dịch ngược tái tạo chính xác `scale` ban đầu, nhất là khi có `scale` âm hoặc khi một bước đã được chuẩn hóa về 0.

## 6. scaleByPowerOfTen(n): điều chỉnh lũy thừa của 10

`x.scaleByPowerOfTen(n)` trả về `x × 10^n` bằng cách chọn `scale mới = scale cũ - n`, kể cả khi kết quả âm. Điều này đặc biệt tự nhiên khi dữ liệu đang ở dạng `1.23E+3` hoặc cần thao tác với bậc 10 lớn.

~~~java
BigDecimal a = new BigDecimal("1.23");
System.out.println(a.scaleByPowerOfTen(3));  // 1.23E+3
System.out.println(a.scaleByPowerOfTen(-2)); // 0.0123
System.out.println(a.scaleByPowerOfTen(0));  // 1.23
~~~

Chẳng hạn `new BigDecimal("5").scaleByPowerOfTen(3)` cho giá trị 5000 ở dạng `5E+3` với `scale = -3`, còn `movePointRight(3)` cho `5000` với `scale = 0`. Không nên diễn giải chữ `E` là sai số; đây chỉ là **cách thể hiện giá trị số dưới dạng khoa học**.

Ví dụ với dữ liệu ban đầu đã có `scale` âm:

~~~java
BigDecimal a = new BigDecimal("1E+3");
System.out.println(a.movePointLeft(0));      // 1000
System.out.println(a.scaleByPowerOfTen(0));  // 1E+3
System.out.println(a.movePointLeft(2));      // 10
System.out.println(a.scaleByPowerOfTen(-2)); // 1E+1
~~~

Dù `movePointLeft(0)` và `scaleByPowerOfTen(0)` giữ cùng giá trị 1000, chúng biểu diễn khác nhau vì cách xử lý `scale` âm.

## 7. Phân biệt với setScale, multiply và làm tròn

Các API có tên gần giống nhưng mục đích khác nhau:

| Phép toán | Thay đổi **giá trị số**? | Mục đích |
|---|---|---|
| `movePointLeft(2)` | Có: chia 100 | Dịch dấu thập phân sang trái |
| `movePointRight(2)` | Có: nhân 100 | Dịch dấu thập phân sang phải |
| `scaleByPowerOfTen(2)` | Có: nhân 100 | Nhân theo bậc 10, giữ `scale` toán học |
| `setScale(2)` | Không, nếu có thể biểu diễn chính xác | Đặt hai chữ số thập phân; có thể ném `ArithmeticException` nếu phải làm tròn |
| `setScale(2, RoundingMode.HALF_UP)` | Có thể, do làm tròn | Quy định độ chính xác biểu diễn và cách làm tròn |
| `multiply(new BigDecimal("100"))` | Có: nhân 100 | Nhân số thập phân tổng quát; `scale` là tổng `scale` của hai toán hạng |

Ví dụ `new BigDecimal("1.234").setScale(2, RoundingMode.HALF_UP)` cho `1.23`, còn `movePointRight(2)` cho `123.4`. Hai phép toán giải quyết hai nhu cầu hoàn toàn khác nhau.

`scaleByPowerOfTen` và hai `movePoint...` không cần `MathContext` hoặc `RoundingMode` vì bản thân việc nhân/chia theo lũy thừa 10 là chính xác trong mô hình `BigDecimal`, miễn `scale` và tài nguyên biểu diễn còn hợp lệ. Nếu bài toán yêu cầu làm tròn đến số chữ số xác định, hãy thực hiện bước `setScale(..., RoundingMode...)` riêng và chọn quy tắc làm tròn theo nghiệp vụ.

Cũng đừng giả định `multiply(new BigDecimal("100"))` có cùng `scale` với `movePointRight(2)`: chẳng hạn `new BigDecimal("1.23").multiply(new BigDecimal("100"))` tạo `123.00` (`scale = 2`), còn `movePointRight(2)` tạo `123` (`scale = 0`).

## 8. Tình huống thực tế: chuyển đơn vị nhỏ nhất một cách an toàn

Giả sử một hệ thống lưu giá trị tiền bằng **đơn vị nguyên nhỏ nhất**, với quy ước một đơn vị lớn bằng 100 đơn vị nhỏ. Chiều đọc dữ liệu dùng `movePointLeft(2)`. Chiều ghi ngược cần xác nhận số đơn vị nhỏ là số nguyên.

Tệp độc lập `MinorUnitDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.BigInteger;

public class MinorUnitDemo {
    public static void main(String[] args) {
        BigDecimal minorUnits = new BigDecimal("12345");
        BigDecimal majorUnits = minorUnits.movePointLeft(2);
        BigInteger exactMinorUnits =
                majorUnits.movePointRight(2).toBigIntegerExact();

        System.out.println("major = " + majorUnits);
        System.out.println("minor = " + exactMinorUnits);

        BigDecimal invalidMajor = new BigDecimal("12.345");
        try {
            invalidMajor.movePointRight(2).toBigIntegerExact();
            System.out.println("unexpected");
        } catch (ArithmeticException ex) {
            System.out.println("fractional minor unit rejected");
        }
    }
}
~~~

Chạy:

~~~bash
javac MinorUnitDemo.java
java MinorUnitDemo
~~~

Kết quả:

~~~text
major = 123.45
minor = 12345
fractional minor unit rejected
~~~

Điểm cần nhớ:

- `toBigIntegerExact()` kiểm tra tính nguyên một cách tường minh; khác với `toBigInteger()` vốn bỏ phần lẻ nếu có.
- Nếu nghiệp vụ **cho phép** nhập nhiều hơn hai chữ số thập phân, phải quy định làm tròn hoặc từ chối trước khi lưu. `movePointRight(2)` tự nó không làm tròn.
- Không mặc định mọi loại tiền tệ hay đơn vị đo có tỷ lệ 100:1. Hệ số chuyển đổi phải đến từ quy ước của hệ thống.
- Nếu còn cần giới hạn phạm vi của kiểu `long`, có thể dùng `longValueExact()` thay cho ép kiểu thường để phát hiện cả phần lẻ lẫn tràn miền giá trị.

## 9. Hiển thị, so sánh và dữ liệu bên ngoài

**`toString()` và `toPlainString()`:** `toString()` có thể chọn ký pháp khoa học như `1.23E+3`. `toPlainString()` hiển thị dạng không dùng số mũ, ví dụ `1230`. Đây chỉ là định dạng chuỗi; không nên dùng `toPlainString()` để suy ra `scale` gốc hoặc để chuẩn hóa logic so sánh.

**`compareTo()` và `equals()`:** `compareTo()` so sánh theo giá trị số; `equals()` yêu cầu cả giá trị lẫn `scale` phù hợp. Ví dụ `new BigDecimal("1230")` và `new BigDecimal("1.23E+3")` so sánh bằng 0 theo `compareTo()` nhưng không `equals()`. Trong `HashSet` hoặc khóa `HashMap`, quy tắc `equals()/hashCode()` mới quyết định trùng khóa. Nếu ứng dụng cần giá trị số tương đương được coi là cùng một khóa, phải thống nhất quy tắc chuẩn hóa từ trước; `stripTrailingZeros()` có thể làm đổi `scale` sang số âm và không nên dùng tùy tiện khi `scale` mang ý nghĩa nghiệp vụ.

**Khởi tạo chính xác:** ưu tiên `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)` thay vì `new BigDecimal(0.1)`. Constructor nhận `double` ghi nhận giá trị nhị phân chính xác của tham số và có thể tạo chuỗi thập phân dài không mong muốn. Khi đổi đơn vị, lỗi khởi tạo này thường gây nhầm lẫn với hoạt động của các phương thức dịch chuyển.

**Bất biến:** `BigDecimal` là immutable. Lệnh `x.movePointLeft(2);` nếu không giữ giá trị trả về sẽ không thay đổi `x`. Hãy gán `BigDecimal smaller = x.movePointLeft(2);` hoặc gán lại `x = x.movePointLeft(2);` khi thực sự muốn cập nhật biến.

## 10. Các lỗi phổ biến và giới hạn cần cân nhắc

1. **Nhầm hướng nhân/chia:** `movePointLeft(3)` chia 1000, còn `movePointRight(3)` và `scaleByPowerOfTen(3)` nhân 1000. Hãy kiểm tra bằng một giá trị đơn giản như 12.34 trước khi đổi dữ liệu hàng loạt.
2. **Nhầm `scale` với giá trị số:** `1.23E+3` và `1230` là cùng giá trị, nhưng có thể khác `equals()` và cách hiển thị.
3. **Ngầm giả định kết quả nguyên:** `movePointRight(2)` trên `12.345` là `1234.5`, không phải 1235 hay 1234.
4. **Dùng `setScale` thay cho phép nhân/chia:** `setScale` kiểm soát số chữ số sau dấu thập phân; nó không thể thay thế quy tắc nhân với `10^n`.
5. **Bỏ qua `n` âm hoặc bằng 0:** `n` âm đổi hướng; `n = 0` vẫn có thể khiến `movePoint...` điều chỉnh `scale` âm về 0.
6. **Dùng số mũ quá lớn:** `scale` của `BigDecimal` thuộc kiểu `int`. Yêu cầu tạo `scale` ngoài phạm vi, hoặc tạo chuỗi biểu diễn quá lớn, có thể gây `ArithmeticException` hoặc vấn đề tài nguyên. Phải xác thực bậc 10 đầu vào và tránh `toPlainString()` cho các số có số mũ cực lớn.
7. **Lưu trữ/serialize mà không có quy ước:** Nếu hệ thống cần đúng hai chữ số thập phân, hãy định nghĩa rõ số chữ số, quy tắc làm tròn và format đầu ra; không suy luận những quy ước này từ việc gọi `movePointRight` hay `scaleByPowerOfTen`.

## 11. Tự kiểm tra

Hãy dự đoán giá trị, `scale` và biểu diễn `toString()` cho các trường hợp sau:

~~~java
BigDecimal a = new BigDecimal("0.0100");
BigDecimal b = new BigDecimal("5");
BigDecimal c = new BigDecimal("1E+3");

BigDecimal q1 = a.movePointRight(2);
BigDecimal q2 = b.movePointRight(3);
BigDecimal q3 = b.scaleByPowerOfTen(3);
BigDecimal q4 = c.movePointLeft(0);
BigDecimal q5 = c.scaleByPowerOfTen(0);
~~~

Đáp án:

| Biến | Giá trị số | `toString()` | `scale()` |
|---|---:|---|---:|
| `q1` | 1 | `1.00` | 2 |
| `q2` | 5000 | `5000` | 0 |
| `q3` | 5000 | `5E+3` | -3 |
| `q4` | 1000 | `1000` | 0 |
| `q5` | 1000 | `1E+3` | -3 |

Hai câu hỏi quan trọng để tự giải thích: Vì sao `q2.compareTo(q3) == 0` nhưng `q2.equals(q3) == false`? Vì sao `q4` và `q5` có cùng giá trị mà `toString()` khác nhau? Cả hai đều bắt nguồn từ cách `scale` được bảo toàn hoặc được chuẩn hóa.

Khi chọn phương thức, hãy xuất phát từ **quy tắc toán học** (chia hay nhân với `10^n`), rồi xác định xem **`scale` có mang ý nghĩa nghiệp vụ hoặc ảnh hưởng đến đầu ra, phép so sánh và lưu trữ hay không**. Dịch dấu thập phân theo `movePointLeft`/`movePointRight` thuận tiện cho đổi đơn vị; `scaleByPowerOfTen` phù hợp khi muốn thao tác rõ ràng với bậc lũy thừa 10 và giữ `scale` có thể âm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-009">stripTrailingZeros có thể tạo scale âm</a>

<details>
<summary>Xem chi tiết</summary>

# `BigDecimal.stripTrailingZeros()` có thể tạo `scale` âm

## 1. Vấn đề cần hiểu

Trong Java, `BigDecimal.stripTrailingZeros()` loại bỏ các chữ số `0` không cần thiết ở **cuối phần số nguyên không có dấu thập phân (unscaled value)** của biểu diễn `BigDecimal`, đồng thời điều chỉnh `scale` để **giá trị số học không đổi**. Vì thế, kết quả có thể có `scale < 0`, ngay cả khi đối tượng ban đầu có `scale = 0` hoặc `scale > 0`.

Ví dụ:

~~~java
BigDecimal original = new BigDecimal("600.00");
BigDecimal normalized = original.stripTrailingZeros();

System.out.println(normalized);         // 6E+2
System.out.println(normalized.scale()); // -2
~~~

`6E+2` vẫn là **600**. `scale = -2` không biểu thị số âm, lỗi tràn số hoặc mất độ chính xác. Đây là cách biểu diễn hợp lệ của cùng một giá trị `BigDecimal`.

Sau bài này, bạn cần giải thích được phép biến đổi `unscaledValue/scale`, phân biệt so sánh giá trị với so sánh biểu diễn, và chọn cách định dạng hoặc chuẩn hóa phù hợp.

## 2. `BigDecimal` lưu số như thế nào?

Về mặt khái niệm, một giá trị hữu hạn của `BigDecimal` được xác định bởi:

\[
\text{value}=\text{unscaledValue}\times 10^{-\text{scale}}
\]

Trong đó:

- `unscaledValue()` trả về một `BigInteger` chứa các chữ số có nghĩa kèm dấu, trước khi đặt dấu thập phân.
- `scale()` là số chữ số ở bên phải dấu thập phân theo công thức trên. `scale` có thể **dương, bằng 0 hoặc âm**.
- `precision()` là số chữ số trong `unscaledValue` ở biểu diễn thập phân (không tính dấu; số `0` có precision bằng 1).
- Hai đối tượng có thể cùng giá trị toán học nhưng khác `unscaledValue`, `scale`, `precision`.

Ví dụ:

| Biểu diễn | `unscaledValue` | `scale` | Giá trị |
| --- | ---: | ---: | ---: |
| `600.00` | `60000` | `2` | `600` |
| `600` | `600` | `0` | `600` |
| `6E+2` | `6` | `-2` | `600` |
| `0.001000` | `1000` | `6` | `0.001` |

Với `6E+2`, công thức cho `6 × 10^(-(-2)) = 6 × 10² = 600`. Do đó **scale âm nghĩa là vị trí dấu thập phân được dịch về phía phải của unscaled value**, tương đương nhân với một lũy thừa dương của 10.

Lưu ý: `scale` không phải số chữ số có nghĩa; để xem số chữ số có nghĩa, dùng `precision()`. Cũng không nên xem `scale` là quy định định dạng hiển thị cố định cho mọi phép toán.

## 3. Vì sao `stripTrailingZeros()` làm `scale` âm?

Phương thức trả về một đối tượng `BigDecimal` tương đương về giá trị, bỏ các thừa số 10 cuối `unscaledValue`. Mỗi lần bỏ một chữ số 0 ở cuối `unscaledValue`, `scale` giảm 1 để giữ nguyên kết quả phép nhân.

Với `new BigDecimal("600.00")`:

| Bước | `unscaledValue` | `scale` | Giá trị |
| --- | ---: | ---: | ---: |
| Ban đầu | `60000` | `2` | `600.00` |
| Bỏ một `0` | `6000` | `1` | `600.0` |
| Bỏ một `0` nữa | `600` | `0` | `600` |
| Bỏ tiếp `0` | `60` | `-1` | `600` |
| Bỏ `0` cuối | `6` | `-2` | `600` |

Kết quả có `unscaledValue = 6` và `scale = -2`. `toString()` biểu diễn nó là `6E+2`.

Điểm dễ nhầm là phương thức không chỉ xóa các số 0 **sau dấu thập phân trong chuỗi hiển thị**. Nó còn có thể loại bỏ số 0 **ở cuối số nguyên** khi biểu diễn dạng unscaled có đủ thừa số 10.

Một số kết quả tiêu biểu:

| Đầu vào `new BigDecimal("...")` | Sau `stripTrailingZeros()` (dạng `toString()`) | `scale` kết quả |
| --- | --- | ---: |
| `"125.000"` | `125` | `0` |
| `"120.3000"` | `120.3` | `1` |
| `"1000"` | `1E+3` | `-3` |
| `"600.00"` | `6E+2` | `-2` |
| `"0.001000"` | `0.001` | `3` |
| `"0.000"` | `0` | `0` |

**Trường hợp đặc biệt là số 0:** `stripTrailingZeros()` trả về biểu diễn `BigDecimal.ZERO` với `scale = 0`, bất kể số 0 ban đầu có bao nhiêu chữ số sau dấu thập phân. Không thể suy ra `scale` của kết quả chỉ bằng cách đếm chữ số `0` trong chuỗi đầu vào.

`stripTrailingZeros()` là phép biến đổi **chính xác**, không làm tròn, không dùng `MathContext` và không thay đổi đối tượng nguồn (vì `BigDecimal` bất biến). Độ chính xác của giá trị số học vẫn được giữ nguyên; `precision()` của biểu diễn có thể thay đổi.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu tệp dưới tên `BigDecimalNegativeScaleDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalNegativeScaleDemo {
    public static void main(String[] args) {
        BigDecimal original = new BigDecimal("600.00");
        BigDecimal normalized = original.stripTrailingZeros();

        System.out.printf(
                "original: %s, unscaled=%s, scale=%d%n",
                original, original.unscaledValue(), original.scale()
        );
        System.out.printf(
                "normalized: %s, unscaled=%s, scale=%d%n",
                normalized, normalized.unscaledValue(), normalized.scale()
        );

        System.out.println("plain: " + normalized.toPlainString());
        System.out.println("equals: " + original.equals(normalized));
        System.out.println("compareTo: " + original.compareTo(normalized));
        System.out.println(
                "fixed scale 2: "
                        + normalized.setScale(2, RoundingMode.UNNECESSARY)
        );

        BigDecimal zero = new BigDecimal("0.000").stripTrailingZeros();
        System.out.printf("zero: %s, scale=%d%n", zero, zero.scale());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac BigDecimalNegativeScaleDemo.java
java BigDecimalNegativeScaleDemo
~~~

Kết quả stdout:

~~~text
original: 600.00, unscaled=60000, scale=2
normalized: 6E+2, unscaled=6, scale=-2
plain: 600
equals: false
compareTo: 0
fixed scale 2: 600.00
zero: 0, scale=0
~~~

Giải thích:

1. Giá trị gốc `600.00` có `unscaledValue = 60000`, `scale = 2`; đối tượng chuẩn hóa có `unscaledValue = 6`, `scale = -2`.
2. `toString()` có thể dùng ký pháp mũ (`6E+2`), còn `toPlainString()` trả về biểu diễn thập phân thông thường (`600`).
3. `equals()` trả về `false` vì hai đối tượng khác `scale` dù giá trị số học giống nhau.
4. `compareTo()` trả về `0` vì so sánh **giá trị số học**.
5. `setScale(2, RoundingMode.UNNECESSARY)` tạo một đối tượng mới có `scale = 2` và giá trị `600.00`; trường hợp này chỉ thêm các chữ số 0, không cần làm tròn.
6. Số 0 sau khi bỏ các số 0 tận cùng có `scale = 0`.

## 5. Hệ quả quan trọng với `equals()`, `compareTo()` và hash

`BigDecimal.equals(Object)` kiểm tra cả **giá trị** và **scale**. `BigDecimal.compareTo(BigDecimal)` so sánh giá trị số học, bỏ qua khác biệt chỉ do scale.

~~~java
BigDecimal a = new BigDecimal("1000");
BigDecimal b = a.stripTrailingZeros(); // 1E+3; scale = -3

System.out.println(a.equals(b));    // false
System.out.println(a.compareTo(b)); // 0
~~~

Trong `HashSet<BigDecimal>` và khóa `HashMap<BigDecimal, ...>`, `equals()` và `hashCode()` quyết định trùng khóa. Vì thế, `1000` và `1E+3` có thể là **hai khóa khác nhau**. Trong `TreeSet<BigDecimal>` sử dụng thứ tự tự nhiên `compareTo()`, chúng lại được coi là tương đương theo phép so sánh thứ tự.

Điều này không phải do `scale` âm riêng lẻ; sự khác biệt `equals`/`compareTo` đã tồn tại cả với `1.0` và `1.00`. Việc gọi `stripTrailingZeros()` có thể làm khác biệt biểu diễn lộ rõ hơn.

Nếu nghiệp vụ yêu cầu so sánh **giá trị tiền hoặc số đo**, thông thường dùng `compareTo() == 0`. Nếu yêu cầu so khớp chính xác biểu diễn, bao gồm scale, dùng `equals()`. Với collection dựa trên hash, hãy quyết định và áp dụng một quy tắc chuẩn hóa đầu vào thống nhất, đồng thời xét nhu cầu giữ scale gốc.

## 6. Phân biệt giá trị, scale và cách hiển thị

Một số API nhìn tương tự nhưng giải quyết vấn đề khác nhau:

| API | Mục đích | Điều cần chú ý |
| --- | --- | --- |
| `stripTrailingZeros()` | Bỏ các thừa số 10 dư trong biểu diễn | Có thể tạo scale âm và không giữ số chữ số thập phân ban đầu |
| `scale()` | Đọc scale hiện tại | Scale âm là hợp lệ |
| `toString()` | In biểu diễn chuẩn; có thể dùng ký pháp mũ | `new BigDecimal("600.00").stripTrailingZeros().toString()` là `6E+2` |
| `toPlainString()` | In dạng số thập phân thông thường, không ký pháp mũ | Chỉ đổi **chuỗi hiển thị**, không đổi đối tượng hay scale |
| `setScale(n, mode)` | Tạo đối tượng mới có scale xác định | Có thể cần làm tròn, hoặc ném lỗi nếu `UNNECESSARY` và phải làm tròn |
| `compareTo()` | So sánh giá trị số học | Không yêu cầu scale giống nhau |
| `equals()` | So sánh giá trị và scale | Có thể trả `false` cho hai số cùng giá trị |

Ví dụ, khi chỉ muốn chuỗi `"600"` thay cho `"6E+2"`:

~~~java
BigDecimal x = new BigDecimal("600.00").stripTrailingZeros();
String plain = x.toPlainString(); // "600"; x.scale() vẫn là -2
~~~

Đừng nhầm `toPlainString()` với việc chuẩn hóa dữ liệu nội bộ: đọc lại chuỗi `"600"` bằng `new BigDecimal(plain)` sẽ tạo đối tượng có `scale = 0`, khác `scale = -2` ban đầu. Nếu trao đổi dữ liệu yêu cầu giữ cả biểu diễn lẫn giá trị, cần định nghĩa rõ format truyền tải.

Nếu giao diện phải luôn hiển thị hai chữ số thập phân, hãy định rõ quy tắc format hoặc scale ở ranh giới hiển thị; `stripTrailingZeros()` không phục vụ yêu cầu này:

~~~java
BigDecimal amount = new BigDecimal("600.00");
BigDecimal fixed = amount.stripTrailingZeros()
                         .setScale(2, RoundingMode.UNNECESSARY);
// fixed.toPlainString() -> "600.00"
~~~

Khi cần đổi `scale` của số có phần thập phân không phù hợp (ví dụ `12.345` thành scale 2), `RoundingMode.UNNECESSARY` sẽ ném `ArithmeticException`, vì phải bỏ chữ số khác 0. Hãy chọn quy tắc làm tròn theo yêu cầu nghiệp vụ thay vì tùy tiện bỏ phần dư.

## 7. Cách sử dụng phù hợp trong thực tế

**Trường hợp A — chỉ muốn hiển thị không có ký pháp mũ.** Dùng `toPlainString()` sau khi chuẩn hóa. Đừng tự giả định `scale >= 0`:

~~~java
BigDecimal value = new BigDecimal("1000").stripTrailingZeros();
System.out.println(value.toString());      // 1E+3
System.out.println(value.toPlainString()); // 1000
~~~

**Trường hợp B — cần một scale cố định theo hợp đồng dữ liệu.** Dùng `setScale(targetScale, roundingMode)` với chính sách làm tròn được quyết định rõ. Ví dụ số tiền VND không có phần lẻ có thể đặt scale 0; tiền có hai chữ số thập phân có thể đặt scale 2 nếu ứng dụng thực sự dùng quy tắc đó. Không áp dụng `stripTrailingZeros()` sau bước đặt scale nếu cần giữ biểu diễn cố định.

**Trường hợp C — kiểm tra số có phải số nguyên về mặt giá trị.** Cách kiểm tra sau là sai với một số nguyên lớn có scale âm:

~~~java
BigDecimal value = new BigDecimal("1000").stripTrailingZeros();
boolean wrong = value.scale() == 0; // false dù giá trị là số nguyên
~~~

Với `BigDecimal` hữu hạn, sau `stripTrailingZeros()`, điều kiện `scale() <= 0` nhận diện giá trị nguyên. Khi thực sự cần lấy một `BigInteger` và kiểm tra chính xác không có phần lẻ, có thể dùng `toBigIntegerExact()`: phương thức này ném `ArithmeticException` nếu có phần lẻ khác 0.

**Trường hợp D — chuẩn hóa số để so sánh hoặc dùng làm khóa.** Thống nhất quy tắc từ lúc tiếp nhận giá trị. `stripTrailingZeros()` có thể hữu ích khi bạn chấp nhận loại bỏ scale để hợp nhất các biểu diễn cùng giá trị (ví dụ `1.0` và `1.00` cùng về `1`). Tuy nhiên việc này sẽ đánh mất thông tin scale ban đầu, nên không thích hợp nếu scale mang ý nghĩa nghiệp vụ.

## 8. Những lỗi phổ biến và cách tránh

1. **Giả định kết quả của `stripTrailingZeros()` luôn có `scale >= 0`.** Một số tròn như `1000` trở thành `1E+3` với `scale = -3`. Viết code chấp nhận cả ba trường hợp scale dương, 0, âm.
2. **Cho rằng `scale` âm nghĩa là số âm hoặc giá trị bị thay đổi.** Scale là số mũ trong công thức biểu diễn; kiểm tra giá trị bằng `compareTo()`.
3. **Dùng `equals()` khi chỉ muốn so sánh giá trị.** `600.00` không `equals` với `6E+2` dù `compareTo() == 0`.
4. **Dùng `stripTrailingZeros()` để cố giữ hai chữ số thập phân cho số tiền.** Phương thức thực hiện điều ngược lại: loại bỏ thông tin scale không cần để giữ giá trị toán học. Quy định scale hoặc format riêng.
5. **Tưởng `toPlainString()` đổi scale của đối tượng.** Nó chỉ sinh chuỗi; `x.scale()` không đổi.
6. **Dùng `new BigDecimal(0.1)` để thử nghiệm thập phân và rồi đổ lỗi cho `stripTrailingZeros()`.** `double` nhị phân thường không biểu diễn chính xác `0.1`. Khi muốn đúng literal thập phân, dùng `new BigDecimal("0.1")`; khi nhận sẵn `double`, cân nhắc `BigDecimal.valueOf(double)` theo hợp đồng đầu vào.

## 9. Tự kiểm tra

Xét đoạn mã:

~~~java
BigDecimal x = new BigDecimal("1200.00");
BigDecimal y = x.stripTrailingZeros();
~~~

Bạn hãy trả lời:

1. `x.unscaledValue()` và `x.scale()` bằng bao nhiêu?
2. `y.unscaledValue()`, `y.scale()`, `y.toString()` và `y.toPlainString()` là gì?
3. `x.equals(y)` và `x.compareTo(y)` cho kết quả gì?
4. Nếu một API yêu cầu đúng hai chữ số sau dấu thập phân, bạn nên xử lý `y` thế nào?

**Đáp án:** `x` có `unscaledValue = 120000` và `scale = 2`; `y` có `unscaledValue = 12`, `scale = -2`, `toString() = "1.2E+3"` và `toPlainString() = "1200"`. `equals` là `false`, `compareTo` là `0`. Để biểu diễn hai chữ số thập phân, dùng `y.setScale(2, RoundingMode.UNNECESSARY)` và `toPlainString()` khi xuất chuỗi, cho `"1200.00"`.

Điểm cốt lõi: `stripTrailingZeros()` giữ nguyên **giá trị** nhưng có thể thay đổi **biểu diễn**. Khi biểu diễn tối giản của một số chứa lũy thừa 10 ở phần nguyên, `scale` âm là kết quả chính xác và được hỗ trợ đầy đủ bởi `BigDecimal`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-010">ulp của BigDecimal</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.ulp() trong Java: đơn vị tại chữ số cuối cùng

## 1. ulp là gì?

`BigDecimal` biểu diễn số thập phân với độ chính xác tùy ý, nhưng mỗi đối tượng có một **scale** cụ thể. Khi cần biết giá trị của một đơn vị tại *hàng thập phân cuối cùng trong biểu diễn hiện tại*, dùng phương thức:

```java
public BigDecimal ulp()
```

**ULP** viết tắt của *unit in the last place* (đơn vị tại chữ số cuối). Với `BigDecimal`, kết quả là một `BigDecimal` dương, có cùng `scale` với đối tượng gốc. Giá trị số học luôn bằng:

\[
\operatorname{ulp}(x)=10^{-x.\operatorname{scale}()}
\]

Ví dụ, `123.45` có `scale = 2` nên `ulp() = 0.01`; `123.4500` có `scale = 4` nên `ulp() = 0.0001`. Chúng bằng nhau về **giá trị số học**, nhưng chữ số cuối của hai biểu diễn nằm ở hai hàng thập phân khác nhau.

**Phân biệt ngay từ đầu:** `ulp()` cho biết đơn vị ở vị trí chữ số cuối **đang được lưu**. Nó không tự đo sai số tính toán, không làm tròn, và không xác định độ chính xác mà nghiệp vụ cần.

## 2. Vì sao scale quyết định ulp?

Mỗi `BigDecimal` có các thành phần:
- `unscaledValue()` là phần nguyên chưa đặt dấu thập phân (kiểu `BigInteger`).
- `scale()` là số nguyên mô tả vị trí dấu thập phân.
- `precision()` là số chữ số của phần nguyên chưa đặt dấu thập phân; precision của số 0 bằng 1.

Công thức biểu diễn:

\[
x = \operatorname{unscaledValue}(x) \times 10^{-\operatorname{scale}(x)}
\]

Ví dụ, `new BigDecimal("123.4500")` có phần không scale `1234500`, `scale = 4`, `precision = 7`. Nếu tăng phần không scale từ 1234500 lên 1234501 mà giữ scale, giá trị tăng 0.0001, đúng bằng `ulp()`.

Đừng nhầm **scale** (vị trí dấu thập phân) và **precision** (số chữ số có nghĩa của phần không scale). `ulp()` phụ thuộc **scale**, không trực tiếp phụ thuộc precision:

| Chuỗi tạo `BigDecimal` | `scale()` | `precision()` | `ulp()` |
| --- | ---: | ---: | ---: |
| `"123.45"` | 2 | 5 | `0.01` |
| `"123.4500"` | 4 | 7 | `0.0001` |
| `"1"` | 0 | 1 | `1` |
| `"0.00"` | 2 | 1 | `0.01` |
| `"-7.20"` | 2 | 3 | `0.01` |
| `"6E+2"` | -2 | 1 | `1E+2` |

Scale có thể **âm**: `6E+2` biểu thị 600 với phần không scale là 6, `scale = -2`, nên ulp bằng 100. Vì thế, `ulp()` không nhất thiết nhỏ hơn 1.

## 3. Hành vi được Java quy định

- Phương thức trả về **`BigDecimal`**, không phải `double`, và kết quả luôn dương.
- Kết quả giữ **đúng scale** của giá trị nhận lời gọi. Giá trị tương đương `BigDecimal.valueOf(1L, x.scale())` về cả số học lẫn scale.
- Với số 0, ulp vẫn là 1 ở scale của chính số 0 đó; không có ngoại lệ `ulp() = 0`.
- `ulp()` **không nhận `MathContext`**, không thay đổi giá trị gốc và không phải thao tác làm tròn. `BigDecimal` là bất biến.

Các số 0 dưới đây bằng nhau về mặt số học nhưng trả kết quả khác:

```java
new BigDecimal("0").ulp();      // 1, scale = 0
new BigDecimal("0.00").ulp();   // 0.01, scale = 2
new BigDecimal("0E+2").ulp();   // 1E+2, scale = -2
```

Java mô tả ulp của số khác 0 theo đơn vị ở chữ số cuối của biểu diễn và mô tả ulp của số 0 là một đơn vị với cùng scale. Đừng diễn giải `ulp()` thành khoảng cách giữa hai số `BigDecimal` gần nhau nhất một cách tuyệt đối: `BigDecimal` không bị bó vào một định dạng dấu chấm động nhị phân cố định.

## 4. Chương trình Java 17 hoàn chỉnh

Lưu tệp **`BigDecimalUlpDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalUlpDemo {
    public static void main(String[] args) {
        show("A", new BigDecimal("123.45"));
        show("B", new BigDecimal("123.4500"));
        show("C", new BigDecimal("-7.20"));
        show("D", new BigDecimal("0.00"));
        show("E", new BigDecimal("6E+2"));

        BigDecimal rounded = new BigDecimal("123.456")
                .round(new MathContext(4, RoundingMode.HALF_UP));
        show("Rounded", rounded);

        BigDecimal original = new BigDecimal("100.00");
        show("Original", original);
        show("Normalized", original.stripTrailingZeros());
    }

    private static void show(String label, BigDecimal number) {
        BigDecimal unit = number.ulp();
        System.out.println(label + ": value=" + number
                + ", scale=" + number.scale()
                + ", ulp=" + unit
                + ", ulpScale=" + unit.scale());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac BigDecimalUlpDemo.java
java BigDecimalUlpDemo
```

**Kết quả chính xác** (chương trình đã được biên dịch với `javac --release 17` và chạy kiểm chứng):

```text
A: value=123.45, scale=2, ulp=0.01, ulpScale=2
B: value=123.4500, scale=4, ulp=0.0001, ulpScale=4
C: value=-7.20, scale=2, ulp=0.01, ulpScale=2
D: value=0.00, scale=2, ulp=0.01, ulpScale=2
E: value=6E+2, scale=-2, ulp=1E+2, ulpScale=-2
Rounded: value=123.5, scale=1, ulp=0.1, ulpScale=1
Original: value=100.00, scale=2, ulp=0.01, ulpScale=2
Normalized: value=1E+2, scale=-2, ulp=1E+2, ulpScale=-2
```

Giải thích kết quả:
1. `A` và `B` cùng giá trị 123.45, nhưng `B` có thêm hai chữ số 0 ở phần thập phân nên ulp nhỏ hơn 100 lần.
2. `C` âm nhưng ulp dương. Dấu của số không quyết định ulp.
3. `D` là số 0, vẫn mang scale 2 và ulp 0.01.
4. `E` có scale âm nên ulp là 100 (in ra dạng khoa học `1E+2`).
5. `Rounded` dùng 4 chữ số có nghĩa, làm tròn thành 123.5, scale 1, ulp 0.1.
6. `Normalized` bỏ các số 0 tận cùng của 100.00, thay scale 2 bằng -2; giá trị vẫn là 100 nhưng ulp đổi từ 0.01 thành 100.

## 5. Quan hệ với làm tròn và thay đổi biểu diễn

### 5.1. setScale có thể thay đổi ulp và giá trị

`setScale(newScale, roundingMode)` trả về một biểu diễn với scale được yêu cầu. Khi giảm scale, nếu cần bỏ chữ số có nghĩa, `RoundingMode` quyết định cách làm tròn:

```java
BigDecimal original = new BigDecimal("1.236");
BigDecimal rounded = original.setScale(2, RoundingMode.HALF_UP);
// original = 1.236; original.ulp() = 0.001
// rounded  = 1.24;  rounded.ulp()  = 0.01
```

ULP tăng từ 0.001 lên 0.01 do scale giảm. **Sai số thực tế** của phép làm tròn là `|1.24 - 1.236| = 0.004`, không phải 0.01. Muốn đo sai số, cần so sánh với giá trị tham chiếu chính xác (nếu có).

Tăng scale cũng đổi ulp **dù không đổi giá trị số học**:

```java
BigDecimal x = new BigDecimal("2.5");
BigDecimal y = x.setScale(3);
// x = 2.5   -> ulp = 0.1
// y = 2.500 -> ulp = 0.001
// x.compareTo(y) == 0
```

### 5.2. MathContext: precision không phải scale

`MathContext(4, RoundingMode.HALF_UP)` yêu cầu tối đa 4 chữ số có nghĩa khi thực hiện một phép toán với context. Nó không hứa rằng ulp của mọi kết quả đều là 0.0001. Ví dụ sau (đoạn minh họa được đặt trong phương thức Java có thể chạy, đã import `java.math.*` hoặc các lớp tương ứng):

```java
BigDecimal a = new BigDecimal("123.456")
        .round(new MathContext(4, RoundingMode.HALF_UP));
// a = 123.5; scale = 1; ulp = 0.1

BigDecimal b = BigDecimal.ONE.divide(
        new BigDecimal("3"),
        new MathContext(4, RoundingMode.HALF_UP));
// b = 0.3333; scale = 4; ulp = 0.0001
```

Cùng precision 4 nhưng ulp khác nhau vì **scale thực tế** của kết quả khác nhau. Một `MathContext` chỉ có hiệu lực với thao tác mà bạn truyền nó vào (hoặc khi gọi phương thức `round(mc)`); nó không tự thay đổi tất cả đối tượng `BigDecimal`.

### 5.3. stripTrailingZeros có thể làm ulp đổi mạnh

`stripTrailingZeros()` giữ nguyên giá trị số học nhưng bỏ số 0 tận cùng khỏi biểu diễn, đôi khi tạo scale âm:

```java
BigDecimal x = new BigDecimal("100.00");
BigDecimal y = x.stripTrailingZeros();
// x.compareTo(y) == 0        // true: cùng giá trị số học
// x.equals(y)                // false: khác scale
// x.ulp().equals(y.ulp())    // false: 0.01 khác 1E+2
```

Với số 0, `stripTrailingZeros()` trả `BigDecimal.ZERO` ở scale 0. Vì vậy `new BigDecimal("0.000").ulp()` là 0.001, còn ulp của kết quả sau chuẩn hóa là 1.

Nếu số chữ số thập phân mang ý nghĩa nghiệp vụ (chẳng hạn giá được công bố với 2 chữ số), cần cân nhắc trước khi gọi `stripTrailingZeros()`.

## 6. Ứng dụng thực tế

**Quan sát độ chi tiết của biểu diễn:** `new BigDecimal("19.990").ulp()` trả 0.001, thể hiện hàng thập phân cuối cùng hiện có trong đối tượng. Nó không nói đơn vị tiền tệ nhỏ nhất là bao nhiêu.

**Theo dõi pipeline xử lý dữ liệu:** ghi `value`, `scale()` và `ulp()` trước/sau `setScale()`, `round()` hoặc `stripTrailingZeros()` để phát hiện bước thay đổi mức chi tiết.

**Phân tích kết quả từ phép tính có MathContext:** `ulp()` giúp biết hàng thập phân cuối cùng còn được giữ sau thao tác. Tuy nhiên, khi muốn đánh giá độ chính xác của thuật toán, cần thêm định nghĩa lỗi và giá trị tham chiếu.

Ví dụ nghiệp vụ yêu cầu số tiền có 2 chữ số thập phân:

```java
BigDecimal amount = new BigDecimal("19.995");
BigDecimal payable = amount.setScale(2, RoundingMode.HALF_UP);
// payable = 20.00; payable.ulp() = 0.01
```

Chính `setScale(2, HALF_UP)` thực hiện quy tắc làm tròn; `ulp()` chỉ mô tả biểu diễn sau đó. ULP không tự đặt ra quy tắc tiền tệ, ngưỡng so sánh hoặc mức sai số có thể chấp nhận.

## 7. So sánh với các API liên quan

| API / khái niệm | Chức năng | Cạm bẫy |
| --- | --- | --- |
| `x.ulp()` | Đơn vị tại chữ số cuối theo scale của `x` | Không đo sai số tính toán. |
| `x.scale()` | Vị trí dấu thập phân | Có thể âm, 0 hoặc dương. |
| `x.precision()` | Số chữ số trong phần unscaled | Không đồng nghĩa số chữ số sau dấu phẩy. |
| `x.setScale(...)` | Đặt scale, có thể làm tròn | Có thể đổi giá trị số học. |
| `x.round(mc)` | Làm tròn theo số chữ số có nghĩa | Scale kết quả phụ thuộc độ lớn. |
| `x.stripTrailingZeros()` | Bỏ số 0 dư trong biểu diễn | Có thể đổi scale/ulp dù số học không đổi. |
| `Math.ulp(double)` | Bước biểu diễn số chấm động nhị phân `double` | Khác hệ cơ số và ngữ nghĩa với `BigDecimal.ulp()`. |

`Math.ulp(1.0)` với `double` xấp xỉ `2.22e-16`, nhưng `new BigDecimal("1.0").ulp()` bằng `0.1`. Không dùng kết quả của hai API thay thế trực tiếp cho nhau.

## 8. Lỗi thường gặp

1. **Coi ulp là sai số:** `ulp() = 0.01` không chứng minh phép tính sai 0.01 hoặc không quá 0.005. Hãy tính lỗi từ phép toán, quy tắc làm tròn và giá trị tham chiếu.
2. **Coi cùng giá trị thì cùng ulp:** `1.0` và `1.000` có cùng giá trị số học nhưng khác scale. Hãy kiểm tra `scale()`.
3. **Dùng precision để suy ra ulp:** `precision = 4` vẫn có thể cho ulp 0.1 hoặc 0.0001. Hãy xem scale của chính kết quả.
4. **Bỏ qua số 0 hoặc scale âm:** `0.00` có ulp 0.01; `6E+2` có ulp 100.
5. **Gọi stripTrailingZeros rồi kỳ vọng ulp giữ nguyên:** scale có thể đổi từ dương sang âm. Đừng bỏ các số 0 có ý nghĩa nghiệp vụ.
6. **Khởi tạo bằng double theo cách không mong muốn:** `new BigDecimal(0.1)` biểu diễn chính xác giá trị nhị phân của `double 0.1`, không đúng 0.1 thập phân, nên scale/ulp có thể bất ngờ. Khi có dữ liệu thập phân rõ ràng, ưu tiên `new BigDecimal("0.1")` hoặc cân nhắc `BigDecimal.valueOf(0.1)`.
7. **Dùng ulp làm epsilon toàn cục để so sánh:** scale có thể được thêm hoặc bớt số 0 mà số học không đổi. Ngưỡng dung sai phải do bài toán quy định.

## 9. Tự kiểm tra kiến thức

**Câu 1.** `new BigDecimal("0.0000").ulp()` bằng bao nhiêu?  
**Đáp án:** 0.0001 vì scale bằng 4, dù giá trị là 0.

**Câu 2.** Vì sao `new BigDecimal("5.00").ulp()` khác `new BigDecimal("5").ulp()`?  
**Đáp án:** đối tượng đầu có scale 2 nên ulp 0.01; đối tượng sau có scale 0 nên ulp 1.

**Câu 3.** `new BigDecimal("600.0").stripTrailingZeros()` có thể trở thành `6E+2`. ULP trước/sau là gì?  
**Đáp án:** trước là 0.1 (scale 1), sau là `1E+2` (scale -2); giá trị số học vẫn là 600.

**Câu 4.** Có thể suy ra từ `ulp() = 0.01` rằng lỗi của một phép chia không quá 0.005 không?  
**Đáp án:** không. ULP mô tả vị trí chữ số cuối, còn giới hạn lỗi phụ thuộc thuật toán và quy tắc làm tròn.

## 10. Ghi nhớ

- Với mọi `BigDecimal x`, **`x.ulp()` có giá trị `10^(-x.scale())` và cùng scale với `x`**.
- Scale là thuộc tính của **biểu diễn**, nên các đối tượng cùng giá trị số học có thể có ulp khác nhau.
- `ulp()` hỗ trợ giải thích dữ liệu và kết quả phép tính. Kiểm soát độ chính xác cần chính sách làm tròn và định nghĩa sai số riêng.

Tài liệu chính thức: [Java SE 17 — BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html) và [Java SE 17 — MathContext](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-011">Tính nhiều bước và lựa chọn thời điểm làm tròn</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal: tính nhiều bước và lựa chọn thời điểm làm tròn

## 1. Vấn đề: cùng phép toán, kết quả cuối có thể khác nhau

Trong quy trình nhân đơn giá với số lượng, giảm giá, tính thuế, quy đổi, chia tiền hay tính lãi nhiều kỳ, **thời điểm làm tròn là quy tắc nghiệp vụ**. Làm tròn mọi kết quả trung gian và chỉ làm tròn tổng cuối là hai chính sách khác nhau, kể cả khi cùng dùng `BigDecimal`, cùng scale và cùng `RoundingMode`.

Ví dụ có ba khoản, mỗi khoản có giá trị gốc `0.335`, kết quả cần hai chữ số sau dấu thập phân và dùng `HALF_UP`:

- **Chốt từng khoản rồi cộng:** `0.335 → 0.34`; tổng `0.34 × 3 = 1.02`.
- **Cộng số gốc rồi chốt:** `0.335 × 3 = 1.005`; tổng `1.005 → 1.01`.

Chênh lệch `0.01` không phải lỗi của Java. Cần xác định tiền được ghi nhận theo từng dòng, theo tổng hóa đơn hay theo cơ sở tính thuế/lãi được quy định. Mục tiêu bài này là biết **giữ độ chính xác khi cần**, **làm tròn đúng ranh giới**, **chọn precision/scale/mode phù hợp** và **đối soát tổng sau khi phân bổ**.

## 2. Scale, precision, MathContext và tính chính xác

`BigDecimal` biểu diễn một số bằng `unscaledValue × 10^(-scale)`. Với `new BigDecimal("123.450")`, `scale() == 3` và `precision() == 6`.

| Thành phần | Ý nghĩa | Ví dụ |
|---|---|---|
| `scale` | Số chữ số bên phải dấu thập phân; có thể âm | `123.450` có scale 3 |
| `precision` | Tổng số chữ số có nghĩa của giá trị | `123.450` có precision 6 |
| `setScale(2, mode)` | Chốt còn hai chữ số thập phân; có thể làm tròn | `123.456 → 123.46` với `HALF_UP` |
| `round(new MathContext(5, mode))` | Chốt còn tối đa năm chữ số có nghĩa | `1234.56789 → 1234.6` với `HALF_EVEN` |

`new MathContext(5)` **không có nghĩa là năm chữ số sau dấu thập phân**. MathContext kiểm soát precision kết quả; `setScale` kiểm soát scale kết quả.

Các overload `add`, `subtract`, `multiply` **không truyền MathContext** thường giữ kết quả toán học chính xác trong biểu diễn thập phân, trong giới hạn tài nguyên và phạm vi scale được hỗ trợ. Với `multiply`, scale kết quả thường bằng tổng scale hai toán hạng; với `add`/`subtract`, scale thường bằng scale lớn hơn của hai toán hạng. Điều này không có nghĩa kết quả đã được chốt theo đơn vị tiền tệ.

**Khởi tạo dữ liệu thập phân đúng:**

~~~java
BigDecimal unitPrice = new BigDecimal("19.995");
BigDecimal discountRate = new BigDecimal("0.075");
BigDecimal quantity = BigDecimal.valueOf(3L);
~~~

Tránh `new BigDecimal(0.1)` nếu ý định là giá trị thập phân chính xác `0.1`: `double` đã chứa gần đúng nhị phân. `BigDecimal.valueOf(0.1)` lấy biểu diễn thập phân từ `Double.toString` và thường dễ dùng hơn constructor nhận `double`, nhưng **không khôi phục sai số do phép tính double xảy ra trước khi chuyển đổi**. Với tiền và tỷ lệ cố định, ưu tiên `String`, số nguyên đơn vị nhỏ hoặc dữ liệu thập phân được parse đúng định dạng.

`BigDecimal` là immutable: `a.add(b)`, `a.setScale(...)` và `a.round(...)` trả kết quả mới, không thay đổi `a`. Phải gán và sử dụng giá trị trả về.

## 3. Ví dụ nhiều bước: làm tròn cuối và làm tròn mỗi khoản

Giả sử đơn giá `19.995`, số lượng `3`, giảm giá `7.5%`, thuế `8%` trên giá trị sau giảm. Số tiền thanh toán cuối cùng được chốt tới hai chữ số thập phân theo `HALF_UP`. Đây là **mô hình minh họa**, không xác lập chính sách thuế hay định giá của bất kỳ quốc gia nào.

**A — chỉ chốt tổng cuối:**

1. Gốc: `19.995 × 3 = 59.985`.
2. Chiết khấu: `59.985 × 0.075 = 4.498875`.
3. Giá trị chịu thuế: `59.985 - 4.498875 = 55.486125`.
4. Thuế: `55.486125 × 0.08 = 4.43889000`.
5. Tổng gốc `55.486125 + 4.43889000 = 59.92501500`, làm tròn thành **59.93**.

**B — chốt đơn giá và các khoản khi ghi nhận:**

1. Đơn giá `19.995 → 20.00`; giá trị gốc `20.00 × 3 = 60.00`.
2. Chiết khấu `60.00 × 0.075 = 4.50000 → 4.50`.
3. Giá trị chịu thuế `60.00 - 4.50 = 55.50`.
4. Thuế `55.50 × 0.08 = 4.4400 → 4.44`.
5. Tổng đã chốt **59.94**.

Hai cách lệch `0.01`. Nếu đầu vào đơn giá bắt buộc hai chữ số thì đó là chính sách xác thực/chốt đơn giá, không được lẫn với việc làm tròn tùy ý tại tầng xử lý.

### Chương trình Java 17+ hoàn chỉnh

Lưu thành `RoundingPipelineDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class RoundingPipelineDemo {
    private static final RoundingMode MODE = RoundingMode.HALF_UP;

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, MODE);
    }

    public static void main(String[] args) {
        BigDecimal unitPrice = new BigDecimal("19.995");
        BigDecimal quantity = new BigDecimal("3");
        BigDecimal discountRate = new BigDecimal("0.075");
        BigDecimal taxRate = new BigDecimal("0.08");

        // A: Dùng giá trị trung gian chính xác, chốt tổng cuối.
        BigDecimal gross = unitPrice.multiply(quantity);
        BigDecimal discount = gross.multiply(discountRate);
        BigDecimal taxable = gross.subtract(discount);
        BigDecimal tax = taxable.multiply(taxRate);
        BigDecimal finalOnly = money(taxable.add(tax));

        // B: Chốt đơn giá, chiết khấu và thuế ở từng bước.
        BigDecimal grossEarly = money(unitPrice).multiply(quantity);
        BigDecimal discountEarly = money(grossEarly.multiply(discountRate));
        BigDecimal taxableEarly = grossEarly.subtract(discountEarly);
        BigDecimal taxEarly = money(taxableEarly.multiply(taxRate));
        BigDecimal eachStep = money(taxableEarly.add(taxEarly));

        System.out.println("final only = " + finalOnly);
        System.out.println("each step  = " + eachStep);
        System.out.println("difference = " + eachStep.subtract(finalOnly));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac RoundingPipelineDemo.java
java RoundingPipelineDemo
~~~

Stdout:

~~~text
final only = 59.93
each step  = 59.94
difference = 0.01
~~~

Hàm `money` thể hiện thao tác **chốt số tiền**. Đừng thêm `setScale(2, ...)` ở một bước chỉ vì kết quả sau cùng là tiền. Mọi điểm chốt phải được chỉ rõ trong đặc tả nghiệp vụ.

## 4. Quyết định thời điểm làm tròn theo ranh giới nghiệp vụ

| Bước thực tế | Câu hỏi phải trả lời | Ảnh hưởng |
|---|---|---|
| Nhân đơn giá với số lượng | Đơn giá được phép có bao nhiêu chữ số? Chốt trước hay sau khi nhân? | Làm tròn đơn giá có thể khuếch đại chênh lệch theo số lượng |
| Tính nhiều dòng hóa đơn | Tổng dòng đã chốt hay tổng giá trị gốc mới là cơ sở tính? | Tổng các dòng đã hiển thị có thể khác tổng tính toán |
| Chiết khấu/thuế | Chốt riêng theo dòng, theo loại thuế hay toàn hóa đơn? | Ảnh hưởng cơ sở tính và kết quả cuối |
| Tiền lãi nhiều kỳ | Lãi kỳ trước nhập vào gốc ngay hay giữ chưa chốt? | Ảnh hưởng lãi kép và đối soát |
| Quy đổi ngoại tệ | Độ chính xác tỷ giá và thời điểm chốt theo loại tiền đích? | Sai lệch khi tỷ giá được làm tròn sớm |
| Phân bổ tiền | Đơn vị nhỏ nhất có thể chuyển/ghi sổ là bao nhiêu? | Cần phân bổ phần dư để tổng các khoản bằng tổng cần chia |
| Hiển thị báo cáo | Làm tròn chỉ để xem, hay số đó đã được ghi sổ? | Không được để hiển thị tự ý thay đổi dữ liệu giao dịch |

Một thiết kế rõ nghĩa phân biệt **input → phép tính chưa chốt → điểm chốt/giao dịch → hiển thị**. Một số tiền đã ghi sổ thường phải tiếp tục tham gia quy trình bằng **giá trị đã chốt** nếu hợp đồng/ledger yêu cầu; giữ số thô để phục vụ kiểm toán không có nghĩa được dùng số thô thay số đã ghi sổ. Ngược lại, báo cáo chỉ định dạng số để đọc không nên âm thầm làm tròn và ghi ngược lại nguồn.

Cần mô tả riêng scale của đơn giá, scale của tỷ lệ, scale nội bộ của phép chia, scale khi chốt và scale hiển thị. Chúng không nhất thiết giống nhau. Với pháp luật thuế/lãi, phải tuân thủ quy định áp dụng; không có một mode duy nhất đúng cho mọi lĩnh vực.

## 5. Double rounding: làm tròn hai lần có thể sai khác một lần

~~~java
BigDecimal x = new BigDecimal("2.345");
BigDecimal twice = x.setScale(2, RoundingMode.HALF_UP)
                    .setScale(1, RoundingMode.HALF_UP);
BigDecimal once = x.setScale(1, RoundingMode.HALF_UP);

System.out.println(twice); // 2.4
System.out.println(once);  // 2.3
~~~

Làm tròn hai lần: `2.345 → 2.35 → 2.4`. Làm tròn trực tiếp tới một chữ số: `2.345 → 2.3`. Không có định luật nói `round(round(x))` luôn bằng `round(x)` khi chính sách/scale hai lần khác nhau. Làm tròn trước phép nhân cũng gây mất mát: `19.995` thành `20.00`, nhân `3` thành `60.00`, không thể suy lại thành `59.985`.

**Giải pháp:** giữ số trung gian đủ chính xác, chỉ chốt ở ranh giới được quy định; nếu buộc phải chốt hai lần thì coi đó là hai giao dịch đã chốt, không cố “làm tròn bù” để giả vờ kết quả như tính một lần.

## 6. Phép chia: bắt buộc quản lý trường hợp vô hạn

`BigDecimal.divide(divisor)` không có chế độ làm tròn chỉ thành công khi thương có biểu diễn thập phân hữu hạn. Ví dụ `1 / 8 = 0.125` thành công, nhưng `1 / 3` có biểu diễn vô hạn và ném `ArithmeticException`. Chia cho 0 cũng ném `ArithmeticException`.

~~~java
BigDecimal one = new BigDecimal("1");
BigDecimal three = new BigDecimal("3");

// one.divide(three); // ArithmeticException vì thương vô hạn.

BigDecimal byScale = one.divide(three, 6, RoundingMode.HALF_EVEN);
BigDecimal byPrecision = one.divide(
        three, new MathContext(6, RoundingMode.HALF_EVEN));

System.out.println(byScale);     // 0.333333
System.out.println(byPrecision); // 0.333333
~~~

Hai kết quả này giống nhau **riêng với 1/3**. Scale 6 nghĩa là sáu chữ số sau dấu thập phân; precision 6 là sáu chữ số có nghĩa. Thí dụ thương của `12345/7` sẽ có cách biểu diễn khác nhau dưới hai quy tắc.

Cẩn thận với overload `divide(BigDecimal divisor, RoundingMode mode)`: nó lấy **scale kết quả từ scale của số bị chia**. Đây là lỗi dễ bị bỏ qua:

~~~java
System.out.println(new BigDecimal("1")
        .divide(new BigDecimal("3"), RoundingMode.HALF_UP)); // 0
System.out.println(new BigDecimal("1.00")
        .divide(new BigDecimal("3"), RoundingMode.HALF_UP)); // 0.33
~~~

Khi cần scale cố định, dùng `divide(divisor, scale, mode)`; khi cần số chữ số có nghĩa, dùng `divide(divisor, mc)`. `MathContext.UNLIMITED` có precision 0, yêu cầu phép toán chính xác và **không thể biến thương 1/3 thành hữu hạn**. Không được tùy tiện dùng một precision rồi khẳng định có kết quả toán học chính xác tuyệt đối.

## 7. MathContext trên từng bước khác chốt tổng cuối

Một `MathContext` có precision hữu hạn sẽ làm tròn **kết quả của mỗi phương thức được truyền context**. Ví dụ:

~~~java
BigDecimal x = new BigDecimal("1234.56789");
System.out.println(x.round(new MathContext(5, RoundingMode.HALF_EVEN)));
System.out.println(x.setScale(2, RoundingMode.HALF_EVEN));
~~~

Stdout:

~~~text
1234.6
1234.57
~~~

Do đó `a.multiply(b, mc).divide(c, mc).add(d, mc)` có thể làm tròn ở ba bước. Nó không có nghĩa là tính chính xác ba bước rồi làm tròn một lần. Khi làm tròn ở mỗi bước, đổi thứ tự cộng/gom nhóm cũng có thể đổi kết quả.

Gợi ý lựa chọn:

1. Với `add`, `subtract`, `multiply` thập phân hữu hạn, giữ kết quả chính xác nếu nghiệp vụ và tài nguyên cho phép.
2. Với phép chia vô hạn, căn bậc hai hoặc thuật toán lặp, chọn working precision theo độ chính xác đầu ra cần đạt và mức độ tích lũy sai số.
3. Giữ chữ số dự phòng (guard digits) nếu còn nhiều bước. **Không có số guard digits cố định đảm bảo mọi công thức chính xác**.
4. Chỉ chốt bằng `setScale` ở ranh giới đã xác định. Nếu nghiệp vụ yêu cầu chốt từng kỳ, từng khoản, làm đúng các lần chốt đó.
5. Muốn cam kết sai số tối đa, phải phân tích công thức và thứ tự tính, không chỉ đặt precision lớn một cách cảm tính.

Một lần làm tròn theo mode gần nhất tới scale 2 có sai số tuyệt đối tối đa `0.005` đơn vị so với **giá trị ngay trước lần làm tròn đó**. Nếu làm tròn `n` dòng rồi cộng, sai lệch có thể tích lũy; nhân/chia sau đó có thể làm sai số biến đổi thêm. Không áp dụng ngưỡng một bước cho toàn quy trình khi chưa phân tích các bước sau.

## 8. Chọn RoundingMode kể cả với số âm

| Mode | Hướng làm tròn | Ví dụ scale 2 |
|---|---|---|
| `HALF_UP` | Gần nhất, điểm giữa ra xa 0 | `2.345 → 2.35`; `-2.345 → -2.35` |
| `HALF_EVEN` | Gần nhất, điểm giữa giữ chữ số cuối chẵn | `2.345 → 2.34`; `2.355 → 2.36` |
| `DOWN` | Về 0 (không đồng nghĩa luôn nhỏ hơn) | `-2.349 → -2.34` |
| `UP` | Ra xa 0 nếu có chữ số bị bỏ | `-2.341 → -2.35` |
| `FLOOR` | Về âm vô cực | `-2.341 → -2.35` |
| `CEILING` | Về dương vô cực | `-2.341 → -2.34` |
| `UNNECESSARY` | Cấm làm tròn làm mất thông tin; ném lỗi nếu cần | `2.340 → 2.34`, `2.345 → lỗi` |

`HALF_EVEN` có thể giảm thiên lệch khi làm tròn nhiều giá trị rơi đúng điểm giữa; `HALF_UP` quen thuộc trong nhiều quy tắc tiền tệ. Nhưng cả hai đều **phải theo chính sách đã được xác định**, không tự suy ra từ một ví dụ. Đặc biệt kiểm tra số âm và chứng từ hoàn trả vì `DOWN` và `FLOOR` khác nhau.

`UNNECESSARY` là cách kiểm tra số đã hợp lệ tại scale yêu cầu:

~~~java
new BigDecimal("12.340").setScale(2, RoundingMode.UNNECESSARY); // 12.34
// new BigDecimal("12.345").setScale(2, RoundingMode.UNNECESSARY);
// ArithmeticException vì không thể giảm scale mà giữ nguyên giá trị.
~~~

## 9. Phân bổ phần dư khi chia số tiền đã chốt

Nếu phải chia `1.00` cho ba người, `1.00 / 3` chốt thành `0.33`; nhân ngược ba chỉ được `0.99`. Để **tổng các khoản bằng tổng phải chi**, cần có quy tắc phân bổ phần dư, thí dụ lấy phần cơ sở theo `DOWN` và phát các cent còn thừa theo thứ tự xác định.

Chương trình Java 17+ dưới đây áp dụng cho **số tiền không âm có tối đa hai chữ số sau dấu thập phân**, số người chia dương; lưu thành `AllocationDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class AllocationDemo {
    static List<BigDecimal> allocate(BigDecimal amount, int recipients) {
        if (recipients <= 0 || amount.signum() < 0) {
            throw new IllegalArgumentException("recipients > 0, amount >= 0");
        }
        BigDecimal total = amount.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal divisor = BigDecimal.valueOf(recipients);
        BigDecimal base = total.divide(divisor, 2, RoundingMode.DOWN);
        int extraCents = total.subtract(base.multiply(divisor))
                .movePointRight(2).intValueExact();

        List<BigDecimal> shares = new ArrayList<>();
        for (int i = 0; i < recipients; i++) {
            shares.add(i < extraCents
                    ? base.add(new BigDecimal("0.01"))
                    : base);
        }
        return shares;
    }

    public static void main(String[] args) {
        List<BigDecimal> shares = allocate(new BigDecimal("1.00"), 3);
        BigDecimal sum = shares.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println(shares);
        System.out.println(sum);
    }
}
~~~

Biên dịch bằng `javac AllocationDemo.java`, chạy `java AllocationDemo`:

~~~text
[0.34, 0.33, 0.33]
1.00
~~~

Quy tắc trên cho người đầu tiên nhận cent thừa. Trong nghiệp vụ thật, chọn thứ tự có thể cần công bằng theo thời gian, chia theo trọng số, lưu phương án phân bổ, xử lý số âm/hoàn tiền và scale khác 2 theo đơn vị tiền tệ. Bất biến cần giữ là **tổng phần đã phân bổ bằng chính xác tổng đã chốt**.

## 10. Tổ chức pipeline rõ ràng, kiểm toán được

Cần đặc tả trước khi viết công thức:

- **Đầu vào:** đơn vị tiền, precision đơn giá, scale tỷ giá/thuế suất, dạng lưu trữ thập phân, ràng buộc làm tròn sẵn.
- **Thứ tự tính:** áp giảm giá/thuế/phí vào cơ sở nào, tính theo dòng hay tổng, kỳ tính lãi, phép nhân/chia cần độ chính xác bao nhiêu.
- **Ranh giới chốt:** điểm nào bắt buộc chốt, scale và mode cụ thể cho từng loại khoản; giá trị chốt có trở thành đầu vào bước tiếp theo không.
- **Đối soát:** tổng dòng đã hiển thị có phải bằng tổng chứng từ không, phần dư hay điều chỉnh được ghi ở dòng nào.
- **Lưu vết:** giữ đầu vào, tỷ lệ, chính sách làm tròn và phiên bản quy tắc; không dùng `double` vô tình làm trung gian.
- **Hiển thị:** định dạng để xem không được tự ý ghi đè giá trị đã chốt.

Tên biến nên thể hiện ngữ nghĩa: `rawSubtotal`, `postedDiscount`, `taxableAmount`, `postedTax`, `settlementTotal`. Hàm `roundTaxLine(...)` hay `roundSettlement(...)` diễn đạt ý nghĩa rõ hơn một `setScale(2, HALF_UP)` đặt tùy ý. Quy tắc chưa rõ thì cần thống nhất với người quyết định nghiệp vụ; không chọn cách cho kết quả “trông đẹp hơn”.

**Lưu ý so sánh:** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` trả `false` vì scale khác, trong khi `compareTo(...) == 0` là `true` vì giá trị số bằng nhau. Khi kiểm tra số tiền tính toán, cân nhắc `compareTo`; khi kiểm tra đúng chuẩn biểu diễn/scale, kiểm tra scale riêng hoặc sử dụng `equals` theo contract.

## 11. Kiểm thử các ranh giới và bất biến

1. **Điểm giữa và dấu:** kiểm thử `2.345`, `2.355`, `-2.345`, `0.005`, `0.0049`, với mode/scale nghiệp vụ.
2. **Điểm chốt:** ba khoản `0.335` dẫn tới `1.02` theo từng khoản hoặc `1.01` theo tổng. Ví dụ giảm giá/thuế trên phải cho `59.93` hoặc `59.94` đúng chính sách.
3. **Double rounding:** `2.345 → 2.35 → 2.4` khác `2.345 → 2.3`; phát hiện refactor đặt nhầm `setScale`.
4. **Chia:** `1/3` chính xác phải báo lỗi; chia theo scale/context ra kết quả đã định; chia cho 0 phải báo lỗi.
5. **Phân bổ:** thử `1.00 / 3`, `0.01 / 3`, `0.00 / 3` và tổng lớn; mỗi phần có scale hợp lệ, tổng phần bằng tổng trước chia.
6. **Dữ liệu biên:** giá trị âm, hoàn tiền, thuế suất 0, chiết khấu 100%, phép toán lặp dài, ngoại tệ với số chữ số nhỏ khác 2.
7. **Bất biến lưu trữ:** kiểm tra số đã chốt có được dùng làm đầu vào đúng ranh giới, phân biệt so sánh giá trị và so sánh scale.

Ví dụ kiểm tra kết quả của đoạn chương trình đầu, sau khi biến `finalOnly` được tính:

~~~java
if (finalOnly.compareTo(new BigDecimal("59.93")) != 0) {
    throw new AssertionError("Incorrect final rounding policy");
}
~~~

Chỉ kiểm thử một đầu ra tình cờ giống nhau không đủ chứng minh ranh giới làm tròn đúng. Nên chủ động chọn dữ liệu khiến hai cách làm tròn khác nhau, để kiểm thử bắt được việc di chuyển một phép `setScale`.

## 12. Lỗi phổ biến và bài tập

**Các lỗi cần tránh:** chốt mọi phép nhân vì “tiền có hai số lẻ”; nhầm `MathContext(2)` với `setScale(2)`; quên `divide` có thể ném lỗi vì thương vô hạn; gọi `divide(divisor, mode)` mà bỏ qua scale của số bị chia; làm tròn hai lần qua các tầng service/DTO; dùng `double` cho tỷ lệ mà không kiểm soát; giả định tổng từng phần đã làm tròn luôn bằng tổng làm tròn một lần; nhầm `DOWN` với `FLOOR` cho số âm; dùng `equals` khi chỉ cần bằng nhau về số học.

**Tự luyện:**

1. Bốn khoản `0.125`, `HALF_UP`, scale 2: chốt từng khoản rồi cộng bao nhiêu? Cộng số thô rồi chốt bao nhiêu?
2. Vì sao `new BigDecimal("1").divide(new BigDecimal("8"))` có kết quả còn `1/3` có thể ném lỗi?
3. Với `5.55`, `round(new MathContext(2, HALF_EVEN))` và `setScale(2, HALF_EVEN)` có giống nhau không?
4. Phân bổ `0.02` cho ba người như thế nào để tổng vẫn chính xác hai cent?

**Đáp án:** (1) `0.13 × 4 = 0.52`; cộng trước `0.125 × 4 = 0.500 → 0.50`. (2) `1/8 = 0.125` hữu hạn, `1/3` vô hạn. (3) Không: kết quả lần lượt `5.6` và `5.55`. (4) Với thứ tự cố định có thể là `[0.01, 0.01, 0.00]`.

### Checklist trước khi đưa pipeline vào sử dụng

Giá trị nào phải chính xác tuyệt đối? Bước nào cần xấp xỉ, scale hay precision nào là đủ? Điểm chốt nghiệp vụ ở đâu, mode nào áp dụng và cách xử lý số âm ra sao? Giá trị sau chốt có được dùng cho bước tiếp theo? Tổng sau phân bổ có khớp tổng cần ghi nhận? Có kiểm thử phân biệt chốt từng phần với chốt tổng không?

### Tài liệu chuẩn

- Java SE 17, `BigDecimal`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html
- Java SE 17, `MathContext`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/MathContext.html
- Java SE 17, `RoundingMode`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html

**Nguyên tắc:** làm rõ số nào còn là giá trị trung gian, số nào đã được chốt và *chốt vì điều gì*; sau đó mới áp dụng `setScale`, `MathContext` và `RoundingMode` đúng với ranh giới đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-arithmetic-012">ArithmeticException khi không thể đáp ứng yêu cầu chính xác</a>

<details>
<summary>Xem chi tiết</summary>

# ArithmeticException khi không thể đáp ứng yêu cầu chính xác trong BigDecimal

## 1. Vấn đề cần hiểu

`BigDecimal` biểu diễn số thập phân bằng một số nguyên không chia tỷ lệ (`unscaledValue`) và một `scale`:

```text
giá trị = unscaledValue × 10^(-scale)
1.230  = 1230 × 10^(-3)
```

Nhờ đó, các số như `0.1`, `12.34` có thể được biểu diễn chính xác theo hệ thập phân nếu khởi tạo từ chuỗi thích hợp. Tuy nhiên, **không phải thương của hai số thập phân hữu hạn cũng có biểu diễn thập phân hữu hạn**. Chẳng hạn, `1 / 3 = 0.3333...` có vô hạn chữ số.

Nếu chương trình yêu cầu kết quả **chính xác tuyệt đối** trong khi biểu diễn hữu hạn không đáp ứng được, hoặc cấm mọi làm tròn dù thao tác cần làm tròn, `BigDecimal` ném `ArithmeticException`. Đây là tín hiệu rằng *điều kiện chính xác đã yêu cầu không thể được đáp ứng*, không có nghĩa kiểu `BigDecimal` luôn thiếu chính xác.

Ba tình huống thường gặp:

- Gọi `divide(BigDecimal)` cho thương thập phân vô hạn, chẳng hạn `1 / 3`.
- Dùng `RoundingMode.UNNECESSARY` với `setScale`, `divide` hay `round` trong trường hợp muốn có kết quả thì phải loại bỏ chữ số có giá trị.
- Dùng `MathContext` có giới hạn chữ số đáng kể (`precision`) và chế độ `UNNECESSARY` khi kết quả không thể giữ nguyên ở giới hạn ấy.

**Phạm vi:** Bài tập trung vào ngoại lệ do yêu cầu chính xác/làm tròn trong các phép toán `BigDecimal`. Chia cho 0 cũng ném `ArithmeticException`, nhưng là nguyên nhân độc lập cần phân biệt khi chẩn đoán.

## 2. Chính xác, scale, precision và rounding là bốn ý khác nhau

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| Giá trị số | Đại lượng toán học được biểu diễn | `1.230` và `1.23` cùng giá trị |
| `scale` | Số chữ số ở bên phải dấu thập phân đối với giá trị scale không âm | `1.230` có scale 3 |
| `precision` | Số chữ số của `unscaledValue` (không đếm dấu); tính cả số 0 cuối nếu còn trong biểu diễn | `1.230` có precision 4 |
| `RoundingMode` | Quy tắc khi phải bỏ chữ số có giá trị để đáp ứng scale/precision | `HALF_UP`, `HALF_EVEN`, `DOWN`, `UNNECESSARY` |

Ví dụ, `new BigDecimal("1.230").setScale(2, RoundingMode.UNNECESSARY)` thành `1.23` **không gây lỗi**: chỉ bỏ một chữ số 0 và giá trị không đổi. Ngược lại, `new BigDecimal("1.234").setScale(2, RoundingMode.UNNECESSARY)` phải bỏ chữ số 4 có giá trị, nên ném `ArithmeticException`.

`UNNECESSARY` không phải là chế độ “làm tròn tốt nhất”; nó là **điều kiện kiểm tra** rằng phép biến đổi *không cần* làm tròn. Dùng nó khi việc mất bất kỳ chữ số có giá trị nào phải bị phát hiện và xử lý.

`MathContext.UNLIMITED` có `precision = 0`, nghĩa là không áp dụng giới hạn chữ số đáng kể. Nó **không** khiến `1 / 3` có một chuỗi chữ số thập phân hữu hạn. Vì vậy, chia thương vô hạn với ngữ cảnh không giới hạn vẫn có thể ném ngoại lệ.

## 3. Chẩn đoán lỗi chia chính xác từ bản chất thương

`divide(divisor)` chỉ thành công khi thương có biểu diễn thập phân hữu hạn. Sau khi rút gọn phân số, mẫu số chỉ được chứa thừa số 2 và 5; vì vậy `1/8`, `3/6` thành công, còn `1/3`, `1/6` ném `ArithmeticException`. Bài *divide chính xác và số thập phân vô hạn tuần hoàn* đã chứng minh tiêu chí này.

Khi **xử lý ngoại lệ**, trước hết kiểm tra `divisor.signum() == 0` để tách lỗi đầu vào. Nếu số chia khác 0, kiểm tra overload có yêu cầu thương exact không, hay một `RoundingMode.UNNECESSARY` hoặc `MathContext` đang từ chối bỏ chữ số có giá trị. **Đừng** bắt chung các trường hợp rồi tự động chọn `HALF_UP`: điều đó làm thay đổi hợp đồng chính xác.

## 4. Các overload dễ bị nhầm của divide

```java
BigDecimal a = new BigDecimal("1");
BigDecimal b = new BigDecimal("3");

// Yêu cầu thương chính xác: ném ArithmeticException.
a.divide(b);

// Quy định 2 chữ số sau dấu thập phân và chế độ làm tròn.
a.divide(b, 2, RoundingMode.HALF_UP); // 0.33

// Quy định tối đa 5 chữ số đáng kể cho kết quả.
a.divide(b, new MathContext(5, RoundingMode.HALF_EVEN)); // 0.33333

// Cấm làm tròn trong kết quả có scale 2: ném ArithmeticException.
a.divide(b, 2, RoundingMode.UNNECESSARY);
```

Các dòng này là **minh họa độc lập về API**, không phải một chương trình chạy tuần tự: nếu thực thi dòng `divide(b)` chưa bắt ngoại lệ, các dòng sau sẽ không chạy.

Điểm cần nhớ:

1. `divide(divisor)` yêu cầu chính xác và không tự chọn số chữ số hay chính sách làm tròn.
2. `divide(divisor, scale, roundingMode)` kiểm soát **số chữ số sau dấu thập phân**. Ví dụ scale 2 tạo số dạng `0.33`.
3. `divide(divisor, mathContext)` kiểm soát **chữ số đáng kể**, khác với số chữ số thập phân. Ví dụ precision 5 cho `0.33333`, còn `12345 / 1` ở precision 5 vẫn có năm chữ số trước dấu thập phân.
4. `divide(divisor, roundingMode)` là một overload khác: **scale của kết quả bằng scale của số bị chia**. Nó không mặc định là hai chữ số thập phân. Vì sự khác biệt này, trong code nghiệp vụ hãy ưu tiên overload khai báo tường minh scale hoặc `MathContext`.

Ngay cả khi chọn `UNNECESSARY`, phép chia vẫn có thể thành công nếu thương chính xác phù hợp scale/precision yêu cầu. Ví dụ `new BigDecimal("1").divide(new BigDecimal("8"), 3, RoundingMode.UNNECESSARY)` trả `0.125`; với scale 2 thì ném ngoại lệ.

## 5. Ví dụ Java 17+ chạy được và kết quả chính xác

Lưu tệp `BigDecimalExactnessDemo.java`:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class BigDecimalExactnessDemo {
    private static void attempt(String label, Runnable operation) {
        try {
            operation.run();
        } catch (ArithmeticException e) {
            System.out.println(label + ": " + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) {
        BigDecimal one = new BigDecimal("1");
        BigDecimal three = new BigDecimal("3");

        System.out.println("1 / 8 exact = "
                + one.divide(new BigDecimal("8")));

        attempt("1 / 3 exact", () ->
                System.out.println(one.divide(three)));

        System.out.println("1 / 3 scale=2 = "
                + one.divide(three, 2, RoundingMode.HALF_UP));

        System.out.println("1 / 3 precision=5 = "
                + one.divide(three, new MathContext(5, RoundingMode.HALF_EVEN)));

        System.out.println("Drop trailing zero = "
                + new BigDecimal("1.230")
                        .setScale(2, RoundingMode.UNNECESSARY));

        attempt("Drop nonzero digit", () ->
                System.out.println(new BigDecimal("1.234")
                        .setScale(2, RoundingMode.UNNECESSARY)));

        attempt("Precision=2 without rounding", () ->
                System.out.println(new BigDecimal("12.345")
                        .round(new MathContext(2, RoundingMode.UNNECESSARY))));

        attempt("Division by zero", () ->
                System.out.println(one.divide(BigDecimal.ZERO)));
    }
}
```

Chạy:

```bash
javac BigDecimalExactnessDemo.java
java BigDecimalExactnessDemo
```

Stdout:

```text
1 / 8 exact = 0.125
1 / 3 exact: ArithmeticException
1 / 3 scale=2 = 0.33
1 / 3 precision=5 = 0.33333
Drop trailing zero = 1.23
Drop nonzero digit: ArithmeticException
Precision=2 without rounding: ArithmeticException
Division by zero: ArithmeticException
```

Ví dụ cố ý chỉ in **loại ngoại lệ**: thông điệp chi tiết của `ArithmeticException` có thể thay đổi giữa API hoặc bản JDK, và không nên dùng thông điệp ấy làm giao kèo logic. Trong ứng dụng thật, không nên bắt tất cả `ArithmeticException` rồi thay bằng một kết quả mặc định: nó có thể che khuất lỗi chia cho 0 hay vi phạm invariant.

## 6. Vì sao MathContext với UNNECESSARY cũng gây lỗi?

`MathContext` là cặp gồm `precision` và `roundingMode`. Ví dụ:

```java
BigDecimal x = new BigDecimal("1.234");
MathContext strict = new MathContext(3, RoundingMode.UNNECESSARY);

// Cần đổi 1.234 thành số chỉ có 3 chữ số đáng kể.
// Vì chữ số 4 cuối có giá trị, round(strict) ném ArithmeticException.
x.round(strict);
```

Ngược lại, `new BigDecimal("1.230").round(new MathContext(3, RoundingMode.UNNECESSARY))` có thể cho `1.23` vì chỉ bỏ số 0 cuối mà không đổi giá trị.

Giới hạn precision không chỉ tác động đến `round`: một số overload của `add`, `subtract`, `multiply`, `divide`, `pow` nhận `MathContext` và cũng có thể yêu cầu làm tròn. Chẳng hạn `new BigDecimal("1.23").multiply(new BigDecimal("2"), new MathContext(2, RoundingMode.UNNECESSARY))` muốn giữ `2.46` ở hai chữ số đáng kể: không thể, nên ném ngoại lệ.

Nếu phép tính phải chính xác, cân nhắc giữ nguyên giá trị trung gian không giới hạn precision và chỉ áp dụng quy tắc biểu diễn ở **ranh giới đã thống nhất**. Đừng tự động tăng precision tùy ý rồi nghĩ ngoại lệ đã được giải quyết: thương `1 / 3` vẫn không bao giờ có biểu diễn thập phân hữu hạn chính xác.

## 7. Chọn giải pháp theo yêu cầu nghiệp vụ

**Trường hợp A — cần kết quả làm tròn hợp lệ.** Xác định scale hoặc precision cần dùng, rồi chọn `RoundingMode` theo quy tắc nghiệp vụ:

```java
BigDecimal ratio = new BigDecimal("1")
        .divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP);
// 0.3333
```

**Trường hợp B — không chấp nhận mất bất kỳ chữ số có giá trị nào.** Dùng phép tính exact hoặc `UNNECESSARY` như một assertion có chủ ý; chuyển trường hợp không đáp ứng thành thông báo lỗi nghiệp vụ có ngữ cảnh:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class ExactMoney {
    public static BigDecimal requireTwoDecimals(BigDecimal value) {
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException cause) {
            throw new IllegalArgumentException(
                    "Amount cannot be represented exactly with two decimals", cause);
        }
    }
}
```

Đoạn mã là class tiện ích có thể biên dịch, không có `main` nên không tự chạy. Với `12.340`, nó trả `12.34`; với `12.345`, nó ném `IllegalArgumentException` và giữ nguyên nguyên nhân `ArithmeticException` để hỗ trợ truy vết. Chỉ áp dụng ràng buộc hai chữ số nếu đó thực sự là hợp đồng dữ liệu của hệ thống.

**Trường hợp C — chia tiền hoặc đơn vị không chia đều.** Làm tròn thương từng phần có thể làm tổng phân bổ khác tổng gốc. Phải quy định cách xử lý phần dư, chẳng hạn phân bổ cho phần tử cuối:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class AllocationDemo {
    public static void main(String[] args) {
        BigDecimal total = new BigDecimal("10.00");
        BigDecimal count = new BigDecimal("3");
        BigDecimal first = total.divide(count, 2, RoundingMode.DOWN);
        BigDecimal second = first;
        BigDecimal last = total.subtract(first).subtract(second);

        System.out.println(first + ", " + second + ", " + last);
        System.out.println(first.add(second).add(last));
    }
}
```

Chạy `javac AllocationDemo.java` rồi `java AllocationDemo`, stdout là:

```text
3.33, 3.33, 3.34
10.00
```

Ví dụ này chỉ minh họa cách bảo toàn tổng; thứ tự chia phần dư, đồng tiền, đơn vị nhỏ nhất, quy định thuế và yêu cầu kiểm toán phải được xác định riêng. `RoundingMode.DOWN` được dùng để dễ theo dõi phân bổ số dương ở đây, không phải một chế độ phổ quát cho mọi giao dịch.

## 8. Cách chẩn đoán khi thấy ArithmeticException

1. **Xác định phép toán gây lỗi**: `divide`, `setScale`, `round`, hay overload dùng `MathContext`. Đọc stack trace và giá trị các toán hạng, thay vì chỉ bắt và nuốt ngoại lệ.
2. **Kiểm tra mẫu số**: nếu bằng 0 thì xử lý điều kiện đầu vào; quy tắc làm tròn không khắc phục chia cho 0.
3. **Kiểm tra yêu cầu exact**: `divide(divisor)` với thương thập phân vô hạn sẽ thất bại; hãy quyết định có cho phép làm tròn hay từ chối phép chia.
4. **Kiểm tra `UNNECESSARY`**: muốn rút bớt scale/precision có đang phải bỏ chữ số có giá trị không? Nếu có, đó là vi phạm đúng nghĩa của chính sách exact.
5. **Xác định precision khác scale**: `MathContext(2, ...)` là hai chữ số đáng kể, không phải hai số sau dấu phẩy; `setScale(2, ...)` mới kiểm soát hai chữ số sau dấu thập phân.
6. **Đối chiếu hợp đồng nghiệp vụ**: phép tính gần đúng có hợp lệ không, dùng kiểu làm tròn nào, làm tròn một lần hay nhiều lần, và phải phân bổ phần dư như thế nào?
7. **Ghi test cho ca biên**: thương kết thúc (`1/8`), thương vô hạn (`1/3`), bỏ zero (`1.230 → 1.23`), bỏ nonzero (`1.234 → 1.23`), và mẫu số bằng 0.

Không nên “sửa nhanh” bằng `double`: số nhị phân dấu phẩy động có thể đưa thêm sai số biểu diễn. Cũng không nên làm tròn kín đáo trong utility dùng chung khi người gọi chưa chấp thuận chính sách làm tròn.

## 9. Lỗi phổ biến và cách tránh

| Sai lầm | Hệ quả | Thay bằng |
| --- | --- | --- |
| Tưởng `BigDecimal` khiến `1/3` exact trở thành số thập phân hữu hạn | `divide` ném ngoại lệ | Chấp nhận làm tròn có chủ đích hoặc từ chối nghiệp vụ |
| Dùng `divide(divisor, RoundingMode.HALF_UP)` và ngỡ rằng kết quả có hai chữ số sau dấu thập phân | Scale kết quả phụ thuộc số bị chia | Dùng `divide(divisor, 2, RoundingMode.HALF_UP)` nếu cần scale 2 |
| Nhầm `MathContext(2, ...)` với `setScale(2, ...)` | Precision áp lên chữ số đáng kể, scale áp lên phần thập phân | Chọn tham số đúng ý nghĩa |
| Dùng `UNNECESSARY` như cách tránh tính toán chính sách làm tròn | Ngoại lệ khi cần làm tròn thật | Chọn chính sách nghiệp vụ hoặc xác nhận exact có chủ đích |
| Dùng `new BigDecimal(0.1)` để nhập decimal chính xác | Sao chép giá trị nhị phân của literal `double` | Dùng `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)` khi nhận `double` |
| Làm tròn nhiều bước mà không kiểm tra tổng | Sai lệch tích lũy, mất phần dư | Xác định điểm làm tròn và quy tắc phân bổ phần dư |
| Bắt `ArithmeticException` rồi trả `BigDecimal.ZERO` | Che lỗi chia cho 0/làm tròn không hợp lệ | Gắn ngữ cảnh, báo lỗi hoặc xử lý theo chính sách rõ ràng |

Lưu ý: `BigDecimal.valueOf(0.1)` dùng biểu diễn chuỗi thập phân của giá trị `double` đầu vào nên thông thường cho `0.1`, nhưng không thể phục hồi độ chính xác đã mất trước đó trong quá trình tính toán bằng `double`.

## 10. Tự kiểm tra

- Vì sao `new BigDecimal("1").divide(new BigDecimal("40"))` thành công, còn chia cho `30` lại thất bại? **Vì** `1/40 = 0.025` là hữu hạn (mẫu chỉ có 2 và 5), còn `1/30` có thừa số 3 ở mẫu sau rút gọn.
- `new BigDecimal("1.200").setScale(2, RoundingMode.UNNECESSARY)` có lỗi không? **Không**; kết quả `1.20` giữ nguyên giá trị.
- `new BigDecimal("1.201").setScale(2, RoundingMode.UNNECESSARY)` có lỗi không? **Có**; cần bỏ chữ số 1 có giá trị.
- Có phải tăng `MathContext` lên 100 chữ số là cách thực hiện chính xác `1/3`? **Không**; precision 100 vẫn chỉ là một xấp xỉ nếu cho phép rounding, còn exact vẫn không thể biểu diễn hữu hạn.
- Khi nào `UNNECESSARY` là lựa chọn hợp lý? **Khi yêu cầu chính xác là invariant đã được xác định**, ví dụ giá trị gửi tới trường lưu trữ có scale cố định và không cho phép mất chữ số có giá trị.

## 11. Ghi nhớ

`ArithmeticException` trong các thao tác exact của `BigDecimal` bảo vệ hợp đồng chính xác: nó xuất hiện khi phép toán không thể tạo kết quả thập phân hữu hạn chính xác, hoặc khi các giới hạn scale/precision buộc phải làm tròn nhưng `RoundingMode.UNNECESSARY` cấm điều đó. Hãy phân biệt **scale** với **precision**, chọn overload của `divide` tường minh, xác định chính sách làm tròn từ yêu cầu nghiệp vụ, và chỉ xem việc bắt ngoại lệ là bước xử lý có chủ đích sau khi đã hiểu nguyên nhân.


</details>

- [Quay lại đầu trang](#back-to-top)
