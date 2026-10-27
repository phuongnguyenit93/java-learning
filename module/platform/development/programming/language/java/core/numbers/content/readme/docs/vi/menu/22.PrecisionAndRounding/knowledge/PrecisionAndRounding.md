<a id="back-to-top"></a>

# Precision và làm tròn

## Menu
- [MathContext gồm precision và RoundingMode](#precision-and-rounding-001)
- [UNLIMITED, DECIMAL32, DECIMAL64 và DECIMAL128](#precision-and-rounding-002)
- [Precision bằng 0 không phải scale bằng 0](#precision-and-rounding-003)
- [UP và DOWN: ra xa hoặc về phía 0](#precision-and-rounding-004)
- [CEILING và FLOOR: hướng về dương hoặc âm vô cực](#precision-and-rounding-005)
- [HALF_UP và HALF_DOWN ở vị trí đúng giữa](#precision-and-rounding-006)
- [HALF_EVEN và lựa chọn chữ số chẵn](#precision-and-rounding-007)
- [UNNECESSARY để yêu cầu kết quả không cần làm tròn](#precision-and-rounding-008)
- [Đối chiếu các chế độ với cả số dương và số âm](#precision-and-rounding-009)
- [Làm tròn hai lần và khác biệt với làm tròn ở bước cuối](#precision-and-rounding-010)
- [Phân biệt setScale với round(MathContext)](#precision-and-rounding-011)
- [Không mặc định Math.round tương đương mọi chế độ HALF_UP](#precision-and-rounding-012)

## <a id="precision-and-rounding-001">MathContext gồm precision và RoundingMode</a>

<details>
<summary>Xem chi tiết</summary>

# MathContext trong Java: precision và RoundingMode

## 1. Bài toán mà MathContext giải quyết

Khi tính toán với `BigDecimal`, có hai câu hỏi độc lập:

- **Được giữ bao nhiêu chữ số có nghĩa?** Ví dụ kết quả phép chia `1 / 3` không thể biểu diễn hết bằng một số thập phân hữu hạn, nên cần quyết định mức chính xác.
- **Nếu phải bỏ chữ số, làm tròn theo quy tắc nào?** Với `2.5`, có thể làm tròn thành `2` hoặc `3`, tùy quy tắc.

`java.math.MathContext` gói hai quyết định này vào một đối tượng bất biến:

```java
MathContext context = new MathContext(5, RoundingMode.HALF_EVEN);
```

Ở đây `precision = 5` là **tối đa năm chữ số có nghĩa của kết quả**; `RoundingMode.HALF_EVEN` quyết định cách xử lý phần chữ số bị loại bỏ. `MathContext` được truyền vào các phép toán `BigDecimal` hoặc phương thức `round` để giới hạn độ chính xác của kết quả. `MathContext` không mô tả số chữ số sau dấu thập phân và cũng không tự động áp dụng cho mọi phép toán.

Ví dụ:

| Số ban đầu | `MathContext(3, HALF_UP)` | Giải thích |
|---|---|---|
| `12345.67` | `1.23E+4` (giá trị `12300`) | Giữ ba chữ số có nghĩa: 1, 2, 3; chữ số kế là 4 |
| `12.345` | `12.3` | Giữ ba chữ số 1, 2, 3; chữ số kế là 4 |
| `0.0012345` | `0.00123` | Các số 0 đứng trước 1 không chiếm chữ số có nghĩa |
| `9.999` | `10.0` | Làm tròn gây nhớ sang hàng cao hơn nhưng vẫn có ba chữ số có nghĩa |

Để hiểu đúng kết quả, cần phân biệt **giá trị**, **precision** và **scale**.

## 2. Precision của BigDecimal khác scale như thế nào?

`BigDecimal` mô tả số thập phân thông qua *unscaled value* và *scale*: về mặt toán học,

```text
giá trị = unscaledValue × 10^(-scale)
```

- `BigDecimal.precision()`: số chữ số của giá trị không chia tỷ lệ (*unscaled value*), không tính dấu âm; các số 0 ở cuối biểu diễn có thể vẫn được tính.
- `BigDecimal.scale()`: số chữ số được dời sang phải dấu thập phân theo biểu diễn của đối tượng; scale có thể bằng 0 hoặc âm.
- `MathContext.getPrecision()`: số chữ số có nghĩa **tối đa** dùng khi một phép toán có context tạo ra kết quả; đây không phải thuộc tính cố định của mọi `BigDecimal` liên quan.

| Giá trị tạo từ chuỗi | `precision()` | `scale()` | Ý nghĩa |
|---|---:|---:|---|
| `"123.4500"` | 7 | 4 | Bốn chữ số sau dấu chấm, gồm hai số 0 cuối |
| `"0.00123"` | 3 | 5 | Ba chữ số có nghĩa, năm chữ số sau dấu chấm |
| `"100.00"` | 5 | 2 | Biểu diễn vẫn giữ hai số 0 cuối |
| `"1E+3"` | 1 | -3 | Giá trị bằng `1000`, scale âm |

Ví dụ hai thao tác hoàn toàn khác nhau:

```java
BigDecimal x = new BigDecimal("12.345");

BigDecimal byPrecision = x.round(
    new MathContext(4, RoundingMode.HALF_UP)
); // 12.35: tối đa 4 chữ số có nghĩa

BigDecimal byScale = x.setScale(4, RoundingMode.HALF_UP);
// 12.3450: đúng 4 chữ số sau dấu chấm
```

**Quy tắc chọn:** dùng `MathContext` để kiểm soát chữ số có nghĩa trong phép toán; dùng `setScale` để kiểm soát số chữ số thập phân của đầu ra hoặc đơn vị đo/tiền tệ. Một bài toán có thể cần cả hai, ở các bước khác nhau.

## 3. Tạo MathContext và chọn precision

```java
import java.math.MathContext;
import java.math.RoundingMode;

MathContext a = new MathContext(6);
// precision = 6, chế độ mặc định là HALF_UP

MathContext b = new MathContext(6, RoundingMode.HALF_EVEN);
// precision = 6, chế độ HALF_EVEN

int digits = b.getPrecision();               // 6
RoundingMode mode = b.getRoundingMode();     // HALF_EVEN

MathContext unlimited = MathContext.UNLIMITED;
```

`MathContext` bất biến: không có thao tác đổi `precision` hoặc `roundingMode` tại chỗ. Có thể tái sử dụng cùng context ở nhiều phép toán. Precision âm không hợp lệ; precision bằng `0` mang ý nghĩa **không giới hạn chữ số có nghĩa**.

Java cung cấp một số context đặt sẵn:

| Hằng số | Precision | RoundingMode |
|---|---:|---|
| `MathContext.UNLIMITED` | 0 | `HALF_UP` (thường không được dùng vì không làm tròn theo precision) |
| `MathContext.DECIMAL32` | 7 | `HALF_EVEN` |
| `MathContext.DECIMAL64` | 16 | `HALF_EVEN` |
| `MathContext.DECIMAL128` | 34 | `HALF_EVEN` |

Các tên `DECIMAL32/64/128` gợi đến định dạng thập phân IEEE 754, nhưng việc chọn hằng số không có nghĩa `BigDecimal` biến thành kiểu dấu phẩy động nhị phân như `float`/`double`. Context chỉ quy định precision và rounding cho các phép toán được áp dụng.

**Lưu ý về `UNLIMITED`:** “không giới hạn precision” không đồng nghĩa “mọi phép chia đều thành công”. `1 / 8` có biểu diễn thập phân hữu hạn, còn `1 / 3` có khai triển vô hạn; `BigDecimal.divide(divisor)` hoặc `divide(divisor, MathContext.UNLIMITED)` sẽ ném `ArithmeticException` cho phép chia không thể biểu diễn chính xác hữu hạn. Chế độ làm tròn trong `UNLIMITED` không giải quyết vấn đề này.

## 4. Các RoundingMode và ý nghĩa với số âm

Các chế độ thuộc enum `java.math.RoundingMode`, độc lập với `MathContext` và cũng dùng được với `setScale`, `divide`.

| Chế độ | Nguyên tắc | `2.5` → số nguyên | `-2.5` → số nguyên |
|---|---|---:|---:|
| `UP` | Bỏ phần dư theo hướng **xa 0** | `3` | `-3` |
| `DOWN` | Bỏ phần dư theo hướng **về 0** | `2` | `-2` |
| `CEILING` | Hướng về **+∞** | `3` | `-2` |
| `FLOOR` | Hướng về **−∞** | `2` | `-3` |
| `HALF_UP` | Gần nhất; nếu đúng giữa hai mức thì **xa 0** | `3` | `-3` |
| `HALF_DOWN` | Gần nhất; nếu đúng giữa hai mức thì **về 0** | `2` | `-2` |
| `HALF_EVEN` | Gần nhất; nếu đúng giữa thì chữ số cuối được giữ là **chẵn** | `2` | `-2` |
| `UNNECESSARY` | Cấm làm tròn không chính xác; ném lỗi nếu buộc bỏ chữ số khác 0 | lỗi | lỗi |

Trong bảng, “→ số nguyên” tương đương `new BigDecimal("2.5").setScale(0, mode)` và phép tính tương tự với số âm.

Với ba chế độ `HALF_*`, phần lớn giá trị không nằm đúng trung điểm sẽ cho cùng kết quả gần nhất. Khác biệt rõ nhất nằm ở trường hợp *tie*:

```text
2.5 → HALF_UP: 3     HALF_DOWN: 2     HALF_EVEN: 2
3.5 → HALF_UP: 4     HALF_DOWN: 3     HALF_EVEN: 4
2.25 (1 chữ số sau dấu chấm) → HALF_EVEN: 2.2
2.35 (1 chữ số sau dấu chấm) → HALF_EVEN: 2.4
```

Với `HALF_EVEN`, “chẵn” nói đến **chữ số cuối còn lại**, không phải kiểm tra xem toàn bộ số đã làm tròn có phải số nguyên chẵn hay không. Nó giúp giảm thiên lệch tích lũy khi có nhiều lần làm tròn đối xứng, nhưng không phải quy tắc bắt buộc cho mọi nghiệp vụ. `HALF_UP` cũng không phải “luôn làm tròn lên”: `2.41` về số nguyên vẫn bằng `2`.

`UP` khác `CEILING` ở số âm; `DOWN` khác `FLOOR` ở số âm. Không suy luận chế độ theo tên tiếng Anh đơn lẻ mà bỏ qua hướng trục số.

## 5. MathContext áp dụng ở đâu trong BigDecimal?

Những cách dùng điển hình:

```java
MathContext mc = new MathContext(4, RoundingMode.HALF_EVEN);

BigDecimal value = new BigDecimal("123.456");
BigDecimal rounded = value.round(mc);

BigDecimal sum = value.add(new BigDecimal("7.89"), mc);
BigDecimal difference = value.subtract(new BigDecimal("7.89"), mc);
BigDecimal product = value.multiply(new BigDecimal("7.89"), mc);
BigDecimal quotient = value.divide(new BigDecimal("7.89"), mc);

BigDecimal constructed = new BigDecimal("123.456", mc);
```

Các phương thức này trả về `BigDecimal` mới; `BigDecimal` là bất biến. Nếu muốn giữ kết quả, phải gán biến, ví dụ `value = value.round(mc)`; gọi `value.round(mc);` riêng lẻ sẽ không đổi `value`.

Đặc biệt, **context không tự lan truyền**. Ví dụ:

```java
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);
BigDecimal a = new BigDecimal("10.1234");
BigDecimal b = new BigDecimal("1.2345");

BigDecimal exactSum = a.add(b);       // 11.3579
BigDecimal roundedSum = a.add(b, mc); // 11.4
```

Việc tạo `a`, `b` hoặc gọi một phép toán có `mc` không tạo “precision toàn cục” cho các phép toán tiếp theo. Phải truyền context rõ ràng ở những bước muốn giới hạn chữ số.

### Phép chia: trường hợp đặc biệt dễ sai

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");
BigDecimal eight = new BigDecimal("8");

BigDecimal exact = one.divide(eight); // 0.125
BigDecimal approximate = one.divide(
    three, new MathContext(4, RoundingMode.HALF_UP)
); // 0.3333

// one.divide(three) ném ArithmeticException vì 1/3 vô hạn.
```

Có hai API phổ biến, phục vụ hai nhu cầu khác nhau:

- `a.divide(b, mc)` yêu cầu kết quả theo **số chữ số có nghĩa** (`mc.precision`).
- `a.divide(b, scale, roundingMode)` yêu cầu kết quả theo **số chữ số sau dấu thập phân** (`scale`).

Ví dụ `1 / 3`: precision 4 cho `0.3333`; scale 2 cho `0.33`. Nhưng với `12345 / 7`, precision 2 và scale 2 tạo kết quả có mức chi tiết rất khác nhau.

Ngay cả khi truyền context, `RoundingMode.UNNECESSARY` vẫn có thể khiến phép tính ném `ArithmeticException` nếu kết quả chính xác cần nhiều chữ số hơn precision cho phép.

## 6. Ví dụ Java 17+ hoàn chỉnh, có thể chạy

Tạo file `MathContextDemo.java`:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class MathContextDemo {
    public static void main(String[] args) {
        MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

        BigDecimal amount = new BigDecimal("12345.67");
        BigDecimal rounded = amount.round(mc);
        BigDecimal third = BigDecimal.ONE.divide(new BigDecimal("3"), mc);
        BigDecimal exact = BigDecimal.ONE.divide(new BigDecimal("8"));
        BigDecimal scaled = new BigDecimal("2.345")
                .setScale(2, RoundingMode.HALF_EVEN);

        System.out.println("precision=" + mc.getPrecision()
                + ", rounding=" + mc.getRoundingMode());
        System.out.println("rounded=" + rounded.toPlainString());
        System.out.println("1/3=" + third.toPlainString());
        System.out.println("1/8=" + exact.toPlainString());
        System.out.println("scale=" + scaled.toPlainString());

        try {
            BigDecimal.ONE.divide(new BigDecimal("3"));
        } catch (ArithmeticException ex) {
            System.out.println("1/3 exact: ArithmeticException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac MathContextDemo.java
java MathContextDemo
```

Kết quả chính xác:

```text
precision=3, rounding=HALF_UP
rounded=12300
1/3=0.333
1/8=0.125
scale=2.34
1/3 exact: ArithmeticException
```

Giải thích: `amount` được rút về ba chữ số có nghĩa nên mang giá trị `12300`; `toPlainString()` hiển thị dạng thường thay cho ký pháp mũ có thể xuất hiện trong `toString()`. `1/3` được xấp xỉ dưới context; `1/8` biểu diễn được chính xác. `2.345` làm tròn `HALF_EVEN` ở hai chữ số sau dấu chấm thành `2.34` vì chữ số hàng phần trăm đang được giữ là 4 (chẵn). Cuối cùng, phép chia `1/3` không context ném ngoại lệ như đã bắt.

## 7. Trình tự làm tròn ảnh hưởng kết quả

Sử dụng context sớm sẽ làm mất chữ số trước các bước sau. Trong chuỗi tính toán dài, điều này có thể tạo ra **double rounding** (làm tròn hai lần) và sai lệch tích lũy.

```java
BigDecimal x = new BigDecimal("1.249");
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);

BigDecimal viaTwoSteps = x.round(mc)
        .setScale(1, RoundingMode.HALF_UP); // 1.3

BigDecimal direct = x.setScale(1, RoundingMode.HALF_UP); // 1.2
```

Vì sao? Bước đầu `1.249` → `1.25`; bước hai gặp đúng trung điểm nên `1.25` → `1.3`. Trong khi đó, làm tròn trực tiếp `1.249` ở một chữ số sau dấu chấm cho `1.2`.

Khi bài toán cần tổng hợp nhiều phần tử, hãy xác định **thời điểm làm tròn** theo đặc tả. Nếu không bắt buộc làm tròn từng phép, thường nên duy trì đủ chữ số ở bước trung gian rồi thực hiện một lần làm tròn tại điểm được quy định. Trường hợp cần tính gần đúng khi chia vô hạn, chọn precision đủ lớn (có thể thêm *guard digits*) và đánh giá sai số, thay vì mặc định một context rất thấp cho mọi bước.

Không có một `MathContext` đúng cho mọi miền dữ liệu. Độ chính xác tài chính, đo lường, thống kê, giao dịch và làm tròn thuế có thể có quy tắc khác nhau.

## 8. Ví dụ nghiệp vụ: làm tròn số tiền cuối bước tính

Giả sử một bài toán minh họa yêu cầu tiền thuế làm tròn đến hai chữ số thập phân bằng `HALF_UP` (đây chỉ là quy tắc giả định của ví dụ, không khẳng định quy định thuế thực tế):

```java
BigDecimal subtotal = new BigDecimal("19.99");
BigDecimal rate = new BigDecimal("0.0825");

BigDecimal tax = subtotal.multiply(rate)
        .setScale(2, RoundingMode.HALF_UP);
BigDecimal total = subtotal.add(tax);

System.out.println("tax=" + tax.toPlainString());     // tax=1.65
System.out.println("total=" + total.toPlainString()); // total=21.64
```

`19.99 × 0.0825 = 1.649175` chính xác trước khi chuyển thành `1.65`. Nếu nghiệp vụ yêu cầu làm tròn số tiền theo từng dòng hàng trước khi cộng, kết quả có thể khác làm tròn sau khi cộng cả hóa đơn. Quy tắc này phải được xác định ở cấp nghiệp vụ; `MathContext` không tự lựa chọn thay.

Khi phép chia tạo tỷ lệ hoặc đơn giá cần xấp xỉ, có thể định nghĩa context cho bước trung gian:

```java
MathContext working = new MathContext(16, RoundingMode.HALF_EVEN);

BigDecimal totalAmount = new BigDecimal("100.00");
BigDecimal units = new BigDecimal("3");
BigDecimal approximateUnitPrice = totalAmount.divide(units, working);

BigDecimal displayPrice = approximateUnitPrice
        .setScale(2, RoundingMode.HALF_UP);
// approximateUnitPrice ~= 33.33333333333333; displayPrice = 33.33
```

Trong hệ thống thật, precision trung gian phải được chọn theo độ lớn đầu vào, số phép tính và yêu cầu sai số. Precision 16 ở đây chỉ là ví dụ.

## 9. Khởi tạo BigDecimal đúng để precision có ý nghĩa

`BigDecimal` có thể lưu trữ chính xác chữ số thập phân nhập dưới dạng chuỗi. Tránh đưa trực tiếp literal `double` vào constructor khi muốn số thập phân theo cách con người viết:

```java
BigDecimal recommended = new BigDecimal("0.1");
BigDecimal alternative = BigDecimal.valueOf(0.1);
BigDecimal surprising = new BigDecimal(0.1);
```

`recommended` và `alternative` có giá trị thập phân `0.1`. Đối tượng `surprising` mang giá trị chuyển đổi **chính xác từ số nhị phân `double`**, vốn không bằng đúng `0.1`; làm tròn nó bằng `MathContext` có thể che đi nguồn sai số nhưng không xóa được nguyên nhân. Với dữ liệu tiền hoặc dữ liệu đo yêu cầu biểu diễn thập phân kiểm soát được, ưu tiên chuỗi đầu vào hoặc parse chuỗi số thập phân có hợp đồng rõ ràng.

Việc hiển thị cũng cần cân nhắc: `toString()` có thể dùng ký pháp mũ; `toPlainString()` không dùng ký pháp mũ; `setScale(2, ...)` bảo đảm đúng hai chữ số thập phân trong biểu diễn số dùng để hiển thị. Không nên suy ra yêu cầu đầu ra từ riêng `MathContext.precision`.

## 10. Lỗi phổ biến và cách kiểm tra

| Lỗi | Hậu quả | Cách làm đúng |
|---|---|---|
| Hiểu `precision = 2` là hai số sau dấu chấm | `123.45` có thể thành `1.2E+2` | Dùng `setScale(2, mode)` khi cần hai chữ số thập phân |
| Chia `1 / 3` không có rounding | `ArithmeticException` | Dùng `divide` với context hoặc scale và rounding phù hợp |
| Nghĩ `UNLIMITED` chia số nào cũng được | Phép chia thập phân vô hạn vẫn lỗi | Xác định sai số cho phép và đặt context hữu hạn |
| Dùng `HALF_UP` khi nghiệp vụ đòi `HALF_EVEN` | Khác kết quả ở các tie | Đưa mode vào đặc tả và test |
| Nhầm `UP` với `CEILING`, `DOWN` với `FLOOR` | Làm tròn sai với số âm | Test đồng thời số dương và âm |
| Gọi `x.round(mc)` mà không gán kết quả | `x` giữ giá trị cũ | Gán `x = x.round(mc)` nếu cần |
| Áp dụng context quá thấp ở mọi phép toán | Sai lệch qua nhiều bước, double rounding | Quy định điểm làm tròn; giữ độ chính xác trung gian đủ lớn |
| Tạo số bằng `new BigDecimal(0.1)` | Mang sai số biểu diễn nhị phân | Dùng `"0.1"` hoặc `BigDecimal.valueOf(0.1)` |
| Dùng `equals` để so sánh giá trị số thập phân khác scale | `1.0` khác `1.00` với `equals` | Dùng `compareTo(...) == 0` nếu chỉ xét giá trị số; dùng `equals` khi scale cũng quan trọng |
| Chọn rounding tùy tiện cho tiền/thuế | Có thể vi phạm yêu cầu nghiệp vụ | Ghi rõ loại phép tính, bước làm tròn, scale và rounding mode |

Để phát hiện làm tròn ngoài ý muốn trong các phép tính cần chính xác tuyệt đối, dùng `RoundingMode.UNNECESSARY` như một phép kiểm tra:

```java
new BigDecimal("12.340").setScale(2, RoundingMode.UNNECESSARY);
// Thành công: chỉ loại bỏ số 0 không làm đổi giá trị.

new BigDecimal("12.345").setScale(2, RoundingMode.UNNECESSARY);
// Ném ArithmeticException vì phải bỏ chữ số 5 khác 0.
```

Nếu hiển thị hoặc kiểm tra đầu ra bằng test, ưu tiên so sánh có chủ đích: dùng `compareTo` cho giá trị toán học, kiểm tra thêm `scale` hay chuỗi khi định dạng là một phần hợp đồng. `round(mc)` không bảo đảm tự chuẩn hóa scale: kết quả đã đủ precision có thể giữ nguyên các số 0 cuối.

## 11. Bài tập tự kiểm tra

1. `new BigDecimal("0.009876").round(new MathContext(2, HALF_UP))` nhận giá trị gì?
2. `new BigDecimal("-2.1").setScale(0, CEILING)` và cùng phép tính với `FLOOR` khác nhau thế nào?
3. Vì sao `new BigDecimal("100.00").precision()` bằng 5, còn `new BigDecimal("1E+2").precision()` bằng 1 dù cùng giá trị?
4. Một bảng báo cáo cần đúng ba chữ số sau dấu thập phân: nên chỉ dùng `MathContext(3)` hay `setScale(3, mode)`?
5. Phép chia `2 / 7` được yêu cầu bốn chữ số có nghĩa: lời gọi `BigDecimal` nào phù hợp?

Đáp án và lập luận:

1. `0.0099`: hai chữ số có nghĩa là 9 và 8; chữ số tiếp theo là 7 nên làm tròn 8 lên 9.
2. `CEILING` cho `-2` (hướng +∞), `FLOOR` cho `-3` (hướng −∞).
3. `precision()` dựa trên biểu diễn không chia tỷ lệ: `100.00` có năm chữ số, `1E+2` có một; giá trị toán học giống nhau không buộc precision/scale giống nhau.
4. Dùng `setScale(3, mode)`, bởi mục tiêu là vị trí dấu thập phân, không phải tổng chữ số có nghĩa.
5. Ví dụ `BigDecimal.valueOf(2).divide(BigDecimal.valueOf(7), new MathContext(4, RoundingMode.HALF_UP))`, kết quả `0.2857`.

**Ghi nhớ:** `MathContext = (precision, RoundingMode)` điều khiển **số chữ số có nghĩa và cách làm tròn của kết quả phép toán có sử dụng context**. `setScale` điều khiển **vị trí thập phân**. Chọn mức chính xác, chế độ làm tròn và thời điểm áp dụng theo yêu cầu của phép tính; với `BigDecimal`, mọi thao tác đều phải được gọi rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-002">UNLIMITED, DECIMAL32, DECIMAL64 và DECIMAL128</a>

<details>
<summary>Xem chi tiết</summary>

# UNLIMITED, DECIMAL32, DECIMAL64 và DECIMAL128 trong Java

## 1. Vấn đề cần giải quyết

Khi tính toán với `BigDecimal`, kết quả có thể cần rất nhiều chữ số. Phép chia `1 / 3` có vô hạn chữ số thập phân; ngay cả phép nhân hai số thập phân hữu hạn cũng có thể tạo ra kết quả dài hơn mức độ chính xác cần dùng. `MathContext` trong gói `java.math` cho phép chỉ định **độ chính xác của kết quả (precision)** và **quy tắc làm tròn (rounding mode)** cho từng phép toán.

Java cung cấp bốn hằng `MathContext` thường dùng:

- `MathContext.UNLIMITED`: tính chính xác, không chủ động làm tròn vì giới hạn chữ số.
- `MathContext.DECIMAL32`: tối đa 7 chữ số có nghĩa, làm tròn `HALF_EVEN`.
- `MathContext.DECIMAL64`: tối đa 16 chữ số có nghĩa, làm tròn `HALF_EVEN`.
- `MathContext.DECIMAL128`: tối đa 34 chữ số có nghĩa, làm tròn `HALF_EVEN`.

Các tên `DECIMAL32`/`DECIMAL64`/`DECIMAL128` gợi đến độ chính xác của các định dạng số thập phân IEEE 754. Trong Java, chúng là **các cấu hình tính toán cho `BigDecimal`**, không phải các kiểu dữ liệu 32/64/128 bit và không áp đặt toàn bộ giới hạn số mũ, biểu diễn hoặc lưu trữ của định dạng IEEE 754 tương ứng.

## 2. Phân biệt precision, scale và MathContext

Một `BigDecimal` gồm giá trị nguyên không chia tỷ lệ (`unscaledValue`) và `scale`; giá trị toán học là `unscaledValue × 10^(-scale)`.

- **Precision**: số chữ số có nghĩa trong biểu diễn thập phân của `BigDecimal`. Ví dụ, `123.4500` có precision 7 vì cả hai chữ số `0` cuối đang được giữ trong biểu diễn.
- **Scale**: số chữ số bên phải dấu thập phân theo cách lưu trữ thông thường; `123.4500` có scale 4. Scale có thể âm; ví dụ `1.23E+4` có scale -2.
- **MathContext precision**: giới hạn chữ số có nghĩa của **kết quả khi phép toán được gọi với context đó**. Không đồng nghĩa với số chữ số sau dấu phẩy.
- **Rounding mode**: quyết định làm gì với các chữ số bị loại khi kết quả vượt precision.

Ví dụ: `12345.6789` có 9 chữ số có nghĩa. Làm tròn với `DECIMAL32` (7 chữ số) tạo `12345.68`: giữ tổng cộng 7 chữ số có nghĩa, chứ không phải giữ 7 chữ số sau dấu phẩy. Với số `0.000123456789`, các chữ số `0` đứng đầu không làm tăng số chữ số có nghĩa.

`MathContext` là đối tượng bất biến. Chỉ việc khai báo context **không tự thay đổi** các `BigDecimal` sẵn có và Java không có MathContext mặc định toàn cục áp lên mọi phép toán. Phải truyền context cho phép toán, gọi `round(context)`, hoặc dùng constructor nhận context.

## 3. Bảng so sánh bốn hằng có sẵn

| MathContext | `getPrecision()` | `getRoundingMode()` | Ý nghĩa |
| --- | ---: | --- | --- |
| `UNLIMITED` | 0 | `HALF_UP` | Precision bằng 0 nghĩa là **không giới hạn số chữ số**, không có nghĩa chỉ giữ 0 chữ số. Chế độ `HALF_UP` được lưu trong object nhưng không được dùng để làm tròn theo precision khi giới hạn là 0. |
| `DECIMAL32` | 7 | `HALF_EVEN` | Giữ tối đa 7 chữ số có nghĩa của kết quả. |
| `DECIMAL64` | 16 | `HALF_EVEN` | Giữ tối đa 16 chữ số có nghĩa của kết quả. |
| `DECIMAL128` | 34 | `HALF_EVEN` | Giữ tối đa 34 chữ số có nghĩa của kết quả. |

Về cấu hình, bốn hằng tương ứng với:

~~~java
MathContext.UNLIMITED;  // new MathContext(0, RoundingMode.HALF_UP)
MathContext.DECIMAL32;  // new MathContext(7, RoundingMode.HALF_EVEN)
MathContext.DECIMAL64;  // new MathContext(16, RoundingMode.HALF_EVEN)
MathContext.DECIMAL128; // new MathContext(34, RoundingMode.HALF_EVEN)
~~~

Đây là các giá trị có sẵn để tái sử dụng; comment giải thích cấu hình tương đương, không phải các lệnh gán lại hằng. `new MathContext(7)` mặc định dùng `HALF_UP`, **khác** `DECIMAL32` ở quy tắc làm tròn. Tương tự, `new MathContext(16)` và `new MathContext(34)` không mặc nhiên có `HALF_EVEN`.

## 4. Chính xác tuyệt đối và làm tròn có kiểm soát

### 4.1. UNLIMITED không có nghĩa là mọi phép chia đều trả về một giá trị

Với phép cộng, trừ và nhân các số `BigDecimal` hữu hạn, Java có thể trả về kết quả thập phân chính xác. Phép chia chỉ có kết quả thập phân hữu hạn khi phân số tối giản có mẫu số chứa các thừa số nguyên tố **chỉ gồm 2 và/hoặc 5**. Ví dụ:

- `1 / 8 = 0.125`: biểu diễn thập phân hữu hạn, `divide(8, UNLIMITED)` thành công.
- `1 / 40 = 0.025`: cũng hữu hạn.
- `1 / 3 = 0.3333…`: thập phân vô hạn tuần hoàn, không thể biểu diễn chính xác bằng một `BigDecimal` hữu hạn. Gọi `divide(3, UNLIMITED)` ném `ArithmeticException`.
- `1 / 7` cũng không có biểu diễn thập phân hữu hạn.

`UNLIMITED` không tự chọn số chữ số hay độ làm tròn khi gặp phép chia vô hạn. Để nhận giá trị gần đúng có giới hạn, truyền một context có precision dương như `DECIMAL64` hoặc chọn `scale` và `RoundingMode` bằng overload thích hợp của `divide`. Các phép toán như `sqrt` với context unlimited cũng yêu cầu kết quả chính xác có thể biểu diễn; căn không chính xác như `sqrt(2)` không thể được trả về dưới dạng hữu hạn chính xác.

### 4.2. Ba context DECIMAL làm tròn đến chữ số có nghĩa

Với `1 / 3`:

| Context | Kết quả của `BigDecimal.ONE.divide(new BigDecimal("3"), context)` |
| --- | --- |
| `UNLIMITED` | Ném `ArithmeticException` |
| `DECIMAL32` | `0.3333333` (7 chữ số 3) |
| `DECIMAL64` | `0.3333333333333333` (16 chữ số 3) |
| `DECIMAL128` | `0.3333333333333333333333333333333333` (34 chữ số 3) |

Nếu làm tròn `123456789.987654321`, `DECIMAL32` cho `1.234568E+8`, `DECIMAL64` cho `123456789.9876543` và `DECIMAL128` giữ nguyên `123456789.987654321`. Ký hiệu `E+8` là dạng hiển thị khoa học của `BigDecimal.toString()`; giá trị tương ứng là `123456800`.

### 4.3. HALF_EVEN là gì?

`RoundingMode.HALF_EVEN` thường được gọi là *banker's rounding*: nếu phần bỏ đi **đúng bằng một nửa đơn vị ở vị trí làm tròn**, chọn chữ số cuối được giữ là **chẵn**. Nếu lớn hơn một nửa thì làm tròn ra xa số 0; nếu nhỏ hơn thì giữ gần số 0 hơn (diễn giải với số dương; chế độ làm tròn được định nghĩa cho cả số âm).

Ví dụ khi chỉ giữ **1 chữ số có nghĩa**:

- `2.5 → 2`, vì chữ số giữ lại là 2 (chẵn).
- `3.5 → 4`, vì phải tăng 3 lên 4 (chẵn).
- `2.51 → 3`: phần bỏ đi lớn hơn đúng một nửa, nên không áp dụng quy tắc hòa chẵn.

`HALF_EVEN` hữu ích khi nhiều giá trị thường xuyên được làm tròn vì tránh thiên lệch một chiều trong các trường hợp đúng nửa. Tuy nhiên, quy tắc nghiệp vụ có thể bắt buộc `HALF_UP`, `DOWN` hay `UNNECESSARY`; đừng tự đổi quy tắc chỉ vì tên preset nghe có vẻ phù hợp.

## 5. Ví dụ Java hoàn chỉnh (Java 17+)

Tạo file `MathContextPresetsDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class MathContextPresetsDemo {
    public static void main(String[] args) {
        BigDecimal one = new BigDecimal("1");
        BigDecimal three = new BigDecimal("3");

        System.out.println("UNLIMITED 1/8: " +
                one.divide(new BigDecimal("8"), MathContext.UNLIMITED));
        try {
            System.out.println("UNLIMITED 1/3: " +
                    one.divide(three, MathContext.UNLIMITED));
        } catch (ArithmeticException e) {
            System.out.println("UNLIMITED 1/3: ArithmeticException");
        }

        System.out.println("DECIMAL32 1/3: " +
                one.divide(three, MathContext.DECIMAL32));
        System.out.println("DECIMAL64 1/3: " +
                one.divide(three, MathContext.DECIMAL64));
        System.out.println("DECIMAL128 1/3: " +
                one.divide(three, MathContext.DECIMAL128));

        BigDecimal value = new BigDecimal("123456789.987654321");
        System.out.println("DECIMAL32 round: " + value.round(MathContext.DECIMAL32));
        System.out.println("DECIMAL64 round: " + value.round(MathContext.DECIMAL64));
        System.out.println("DECIMAL128 round: " + value.round(MathContext.DECIMAL128));

        BigDecimal a = new BigDecimal("1.234567");
        BigDecimal b = new BigDecimal("0.0000009");
        System.out.println("sum exact: " + a.add(b));
        System.out.println("sum DECIMAL32: " + a.add(b, MathContext.DECIMAL32));

        MathContext oneDigit = new MathContext(1, RoundingMode.HALF_EVEN);
        System.out.println("HALF_EVEN 2.5: " +
                new BigDecimal("2.5").round(oneDigit));
        System.out.println("HALF_EVEN 3.5: " +
                new BigDecimal("3.5").round(oneDigit));
        System.out.println("setScale(2): " +
                new BigDecimal("1.234567").setScale(2, RoundingMode.HALF_EVEN));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MathContextPresetsDemo.java
java MathContextPresetsDemo
~~~

Kết quả chuẩn:

~~~text
UNLIMITED 1/8: 0.125
UNLIMITED 1/3: ArithmeticException
DECIMAL32 1/3: 0.3333333
DECIMAL64 1/3: 0.3333333333333333
DECIMAL128 1/3: 0.3333333333333333333333333333333333
DECIMAL32 round: 1.234568E+8
DECIMAL64 round: 123456789.9876543
DECIMAL128 round: 123456789.987654321
sum exact: 1.2345679
sum DECIMAL32: 1.234568
HALF_EVEN 2.5: 2
HALF_EVEN 3.5: 4
setScale(2): 1.23
~~~

Đoạn `try/catch` chỉ bắt phép chia không thể biểu diễn chính xác với `UNLIMITED`. Các phép `round` tác động lên **kết quả trả về**; đối tượng `value` ban đầu vẫn giữ đầy đủ chữ số vì `BigDecimal` bất biến. Ví dụ tổng cho thấy `add(b)` trả giá trị chính xác còn `add(b, DECIMAL32)` trả tổng đã giới hạn 7 chữ số có nghĩa.

## 6. Dùng context đúng trong chương trình thực tế

### 6.1. Cách truyền MathContext

Các API thông dụng nhận tham số `MathContext` gồm `add`, `subtract`, `multiply`, `divide`, `pow` và `sqrt` (nếu phiên bản Java hỗ trợ), cũng như `round` và một số constructor của `BigDecimal`:

~~~java
MathContext mc = MathContext.DECIMAL64;
BigDecimal x = new BigDecimal("19.876543210987654321");
BigDecimal y = new BigDecimal("3");

BigDecimal sum = x.add(y, mc);       // làm tròn tổng theo mc
BigDecimal product = x.multiply(y, mc); // làm tròn tích theo mc
BigDecimal quotient = x.divide(y, mc); // làm tròn thương theo mc
BigDecimal rounded = x.round(mc);    // làm tròn chính x, không sửa x
BigDecimal roundedOnInput = new BigDecimal("19.876543210987654321", mc);
~~~

Đây là **đoạn mã minh họa** bên trong phương thức/khối lệnh có import `java.math.*`, không phải một chương trình `main` độc lập.

Cẩn thận với thời điểm làm tròn. `x.multiply(y, mc).add(z, mc)` có thể khác `x.multiply(y).add(z).round(mc)` vì biểu thức thứ nhất làm tròn ở mỗi bước. Sai số làm tròn trung gian có thể tích lũy; cần quyết định dựa trên yêu cầu của thuật toán, kể cả với precision 34.

### 6.2. Khi nào nên chọn hằng nào?

| Trường hợp | Hướng chọn | Lý do/cảnh báo |
| --- | --- | --- |
| Cộng, trừ, nhân cần kết quả thập phân chính xác | Không truyền context hoặc `UNLIMITED` | Không chủ động làm tròn; vẫn cần xét hiệu năng/bộ nhớ nếu số quá lớn. |
| Chia có khả năng vô hạn tuần hoàn | `DECIMAL32`/`64`/`128` hoặc context tùy chỉnh | Precision dương giúp phép chia trả về kết quả làm tròn. |
| Bài toán yêu cầu khoảng 7 chữ số có nghĩa | `DECIMAL32` | Ít chữ số hơn, nhưng không phù hợp khi cần nhiều thông tin. |
| Cần khoảng 16 chữ số có nghĩa | `DECIMAL64` | Phù hợp nếu yêu cầu số chữ số có nghĩa và `HALF_EVEN` đều được chấp nhận. |
| Cần khoảng 34 chữ số có nghĩa | `DECIMAL128` | Cung cấp precision cao hơn; không có nghĩa mọi phép tính đều chính xác tuyệt đối. |
| Tài chính có quy định 2 chữ số thập phân | `setScale(2, roundingMode)` theo quy tắc nghiệp vụ | Hai chữ số **sau dấu phẩy** không phải 2 chữ số **có nghĩa**; `DECIMAL32` không thay cho scale. |
| Cần quy tắc khác hoặc precision ấn định | `new MathContext(n, mode)` | Ví dụ `new MathContext(20, RoundingMode.HALF_UP)`. |
| Không chấp nhận mất bất kỳ chữ số nào | Context với `RoundingMode.UNNECESSARY` | Ném `ArithmeticException` nếu phép toán cần làm tròn; không tự làm tròn âm thầm. |

Ví dụ, muốn thương có **chính xác hai chữ số sau dấu phẩy**:

~~~java
BigDecimal price = new BigDecimal("10");
BigDecimal count = new BigDecimal("3");
BigDecimal amount = price.divide(count, 2, RoundingMode.HALF_UP);
// amount.toPlainString() là "3.33"
~~~

Ngược lại, `price.divide(count, MathContext.DECIMAL32)` cho `3.333333` (7 chữ số có nghĩa), không đáp ứng quy định hai chữ số thập phân.

### 6.3. Phối hợp precision và scale

Một phép tính có thể giới hạn số chữ số có nghĩa ở bước trung gian bằng `MathContext`, sau đó chuẩn hóa số chữ số thập phân bằng `setScale` ở ranh giới xuất dữ liệu. Khi làm vậy, **hai lần làm tròn có thể tạo sai lệch kép** so với tính chính xác rồi làm tròn một lần ở cuối. Nếu nghiệp vụ cần chính xác tuyệt đối đến thời điểm thanh toán, hãy giữ các phép tính chính xác ở những bước có thể, làm tròn theo quy tắc đã thiết kế tại điểm thích hợp và bổ sung test các trường hợp đúng nửa.

`setScale(2, mode)` yêu cầu scale 2, **không** hạn chế tổng số chữ số có nghĩa: `123456.78` vẫn có 8 chữ số có nghĩa. Còn `round(DECIMAL32)` có thể tạo scale âm hoặc cách hiển thị khoa học nếu phần nguyên lớn.

## 7. Những lỗi phổ biến

1. **Hiểu `UNLIMITED` là "chia được mọi số".** Nó đòi hỏi kết quả chính xác và ném ngoại lệ cho thương tuần hoàn như `1/3`. Cần cung cấp giới hạn precision hoặc scale khi chấp nhận gần đúng.
2. **Nhầm precision với scale.** `DECIMAL64` giới hạn **16 chữ số có nghĩa**, không phải 16 chữ số sau dấu phẩy.
3. **Tưởng preset luôn dùng `HALF_UP`.** Ba preset DECIMAL dùng `HALF_EVEN`; chỉ `UNLIMITED` giữ cấu hình `HALF_UP` và không dùng nó để làm tròn theo precision.
4. **Dùng `new MathContext(7)` thay cho `DECIMAL32` mà không xét mode.** Constructor một tham số dùng `HALF_UP`, nên ở điểm hòa nửa kết quả có thể khác.
5. **Cho rằng DECIMAL128 tương đương một kiểu IEEE decimal128 trọn vẹn.** Java `BigDecimal` không tự áp giới hạn exponent hay định dạng mã hóa của decimal128 chỉ vì truyền context này.
6. **Dùng `new BigDecimal(0.1)` rồi tin rằng context sửa được sai số đầu vào.** Literal `double` `0.1` đã không biểu diễn thập phân chính xác; constructor nhận `double` bảo toàn giá trị nhị phân được chuyển đổi. Dùng `new BigDecimal("0.1")` hoặc cân nhắc `BigDecimal.valueOf(0.1)` để có cách biểu diễn thập phân dự kiến.
7. **Làm tròn quá sớm qua nhiều bước.** Kết quả trung gian mất chữ số có thể ảnh hưởng kết quả cuối; context không bảo đảm số chữ số sau các bước làm tròn vẫn phản ánh phép toán chính xác ban đầu.
8. **So sánh kết quả theo chuỗi hoặc `equals` một cách tùy tiện.** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` là `false` do scale khác, còn `compareTo(...) == 0` khi giá trị số bằng nhau. Context có thể làm thay đổi scale và dạng `toString()`; đánh giá theo giá trị hay biểu diễn phải đúng mục đích.
9. **Dùng precision cao để thay kiểm tra sai số.** Một kết quả với 34 chữ số vẫn là gần đúng nếu phép chia hoặc bước trung gian phải làm tròn; cần thiết kế ngưỡng sai số/kiểm thử phù hợp.

## 8. Câu hỏi tự kiểm tra

**1. Vì sao `MathContext.UNLIMITED.getPrecision() == 0` nhưng `1/3` lại gây ngoại lệ?** Vì precision 0 biểu thị không giới hạn, buộc phép chia có kết quả chính xác biểu diễn bằng thập phân hữu hạn. `1/3` không đáp ứng điều đó.

**2. Muốn làm tròn tiền `123.456` còn hai chữ số sau dấu phẩy, có nên dùng `DECIMAL32`?** Không. `DECIMAL32` giữ tối đa 7 chữ số có nghĩa, trong trường hợp này có thể không làm tròn gì; nên dùng `setScale(2, mode)` theo quy tắc tiền tệ.

**3. `DECIMAL64` có luôn cho kết quả đúng hơn `DECIMAL32` không?** Nó giữ nhiều chữ số có nghĩa hơn trong cùng kiểu phép tính, nhưng điều đó không tự bảo đảm đầu vào chính xác, cách làm tròn trung gian đúng yêu cầu, hay thuật toán ổn định số học.

**4. Khi nào chọn context tự tạo?** Khi yêu cầu precision hoặc rounding mode khác preset; ví dụ `new MathContext(12, RoundingMode.HALF_UP)` hoặc dùng `UNNECESSARY` để phát hiện tình huống cần làm tròn.

**Ghi nhớ:** Chọn `MathContext` theo **số chữ số có nghĩa** và **quy tắc làm tròn** của phép toán; chọn `setScale` theo **số chữ số thập phân** cần trình bày/lưu trữ. `UNLIMITED` nghĩa là tính chính xác khi có thể biểu diễn, trong khi ba preset `DECIMALxx` chấp nhận làm tròn với `HALF_EVEN`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-003">Precision bằng 0 không phải scale bằng 0</a>

<details>
<summary>Xem chi tiết</summary>

# Precision bằng 0 không phải scale bằng 0 trong Java BigDecimal

Khi làm việc với số thập phân chính xác bằng `BigDecimal`, hai từ **precision** và **scale** mô tả hai khía cạnh khác nhau. Đặc biệt, **`MathContext` có `precision = 0` nghĩa là không giới hạn số chữ số có nghĩa khi tính toán**, hoàn toàn không có nghĩa kết quả có `scale = 0` (không có chữ số sau dấu thập phân).

## 1. Phân biệt ba đại lượng dễ nhầm

Một `BigDecimal` biểu diễn giá trị bằng công thức:

```text
value = unscaledValue × 10^(-scale)
```

- **`BigDecimal.precision()`**: số chữ số trong hệ số nguyên không gồm dấu (`unscaledValue`), bỏ các số 0 đứng đầu. Số 0 có precision bằng **1**. Chữ số 0 ở cuối hệ số vẫn được tính khi biểu diễn chúng bằng scale tương ứng.
- **`BigDecimal.scale()`**: số mũ âm của 10 trong biểu diễn trên. Với số dạng thông thường, scale dương thường là số chữ số sau dấu thập phân; scale bằng 0 là số nguyên ở biểu diễn hiện tại; scale âm cũng hợp lệ.
- **`MathContext.getPrecision()`**: số chữ số có nghĩa tối đa mà một phép toán có áp dụng `MathContext` sẽ giữ lại khi làm tròn. Riêng giá trị **0** là tín hiệu *unlimited precision*: không áp dụng giới hạn precision. Nó không phải precision thực tế bằng 0 của `BigDecimal`.

Ví dụ:

| Giá trị khởi tạo | `precision()` | `scale()` | Giải thích |
|---|---:|---:|---|
| `new BigDecimal("123.4500")` | 7 | 4 | Hệ số nguyên là `1234500`, có 7 chữ số. |
| `new BigDecimal("0.0012300")` | 5 | 7 | Hệ số `12300` có 5 chữ số, scale là 7. |
| `new BigDecimal("1000")` | 4 | 0 | Giá trị có biểu diễn scale 0, dù precision là 4. |
| `new BigDecimal("1E+3")` | 1 | -3 | Cùng giá trị số học 1000, nhưng biểu diễn khác. |
| `new BigDecimal("0.00")` | 1 | 2 | Giá trị bằng 0 có precision 1, vẫn giữ scale 2. |

**Cần nhớ:** precision mô tả **chữ số có nghĩa**, scale mô tả **vị trí dấu thập phân trong biểu diễn**. Hai đại lượng không phải hai cách gọi của cùng một thông số.

## 2. `MathContext` có precision bằng 0 hoạt động ra sao?

`MathContext` định nghĩa precision và `RoundingMode` cho những phép toán `BigDecimal` nhận đối số context:

```java
MathContext unlimited = MathContext.UNLIMITED;
MathContext alsoUnlimited = new MathContext(0, RoundingMode.DOWN);

System.out.println(unlimited.getPrecision());     // 0
System.out.println(alsoUnlimited.getPrecision());  // 0
```

Hai context trên đều **không làm tròn chỉ vì giới hạn chữ số có nghĩa**, bất kể chế độ làm tròn được cấu hình. `MathContext.UNLIMITED` có `precision = 0` và `RoundingMode.HALF_UP`; chế độ làm tròn này không biến nó thành context giới hạn precision.

Ví dụ, làm tròn bằng context unlimited không thay đổi số nguồn:

```java
BigDecimal x = new BigDecimal("1234.567");
BigDecimal y = x.round(MathContext.UNLIMITED);
// y = 1234.567; y.precision() = 7; y.scale() = 3
```

Nhưng **unlimited không đồng nghĩa mọi phép toán luôn trả về kết quả thành công**:

- Phép cộng, trừ, nhân có thể biểu diễn kết quả thập phân hữu hạn một cách chính xác, tùy giới hạn tài nguyên và phạm vi scale mà `BigDecimal` hỗ trợ.
- Phép chia có kết quả thập phân vô hạn tuần hoàn, như `1 / 3`, không thể được biểu diễn chính xác bằng một `BigDecimal` hữu hạn. Với `MathContext.UNLIMITED` hoặc `new MathContext(0, ...)`, `divide` sẽ ném `ArithmeticException` vì không có precision hữu hạn để làm tròn.
- Phép chia cho 0 vẫn ném ngoại lệ. Unlimited precision không loại bỏ điều kiện lỗi toán học.
- **Không có quy tắc bảo đảm scale giữ nguyên** khi tính toán. Scale của kết quả còn phụ thuộc phép toán, scale toán hạng và biểu diễn chính xác của kết quả.

Ví dụ, `new BigDecimal("1.20").add(new BigDecimal("3.4"), MathContext.UNLIMITED)` cho `4.60` (scale 2), còn phép nhân tương ứng cho `4.080` (scale 3). Context unlimited không đưa hai kết quả về scale 0.

## 3. `setScale(0)` tác động lên điều gì?

`setScale(0, roundingMode)` yêu cầu **kết quả có scale bằng 0**. Nếu phần thập phân khác 0, Java phải áp dụng chế độ làm tròn đã cung cấp:

```java
BigDecimal x = new BigDecimal("1234.567");

BigDecimal a = x.setScale(0, RoundingMode.HALF_UP); // 1235
BigDecimal b = x.setScale(2, RoundingMode.HALF_UP); // 1234.57
```

- `a.scale() == 0`, nhưng `a.precision() == 4`. Scale 0 không làm precision thành 0.
- `b.scale() == 2` vì có đúng hai chữ số sau dấu thập phân trong biểu diễn.
- `x.setScale(0)` (không có `RoundingMode`) chỉ thành công nếu có thể đổi scale **chính xác**, chẳng hạn `1234.000` thành `1234`; với `1234.567` nó ném `ArithmeticException` vì phải làm tròn.
- `RoundingMode.UNNECESSARY` có ý nghĩa “không cho phép mất thông tin”: nếu phép đổi scale đòi làm tròn thì ném ngoại lệ.

`BigDecimal` là bất biến: `setScale`, `round` và các phép số học trả về đối tượng kết quả; chúng không âm thầm sửa giá trị biến nguồn.

## 4. `round(MathContext)` khác `setScale`

Với cùng một số:

| Thao tác | Giá trị kết quả | Precision | Scale | Ràng buộc được áp dụng |
|---|---|---:|---:|---|
| `new BigDecimal("1234.567")` | `1234.567` | 7 | 3 | Biểu diễn ban đầu |
| `x.round(MathContext.UNLIMITED)` | `1234.567` | 7 | 3 | Không giới hạn chữ số có nghĩa |
| `x.round(new MathContext(3, HALF_UP))` | `1.23E+3` | 3 | -1 | 3 chữ số có nghĩa |
| `x.setScale(0, HALF_UP)` | `1235` | 4 | 0 | 0 chữ số sau dấu thập phân |

Trong bảng, `HALF_UP` là `RoundingMode.HALF_UP`.

Quan sát dòng precision 3: `1.23E+3` có giá trị số học là **1230**, khác **1235** của `setScale(0, HALF_UP)`. Làm tròn đến 3 chữ số có nghĩa nghĩa là làm tròn theo hàng chục trong trường hợp này; scale kết quả thậm chí **âm**. Vì thế thay `setScale(0, ...)` bằng `round(new MathContext(0, ...))` là lỗi về bản chất yêu cầu.

Một điểm tinh tế: nếu làm tròn theo precision khiến phần hệ số được chuẩn hóa, kết quả có thể chuyển sang ký pháp khoa học. `toPlainString()` có thể dùng khi cần hiển thị số thường, nhưng chỉ đổi **cách hiển thị**, không làm scale thành 0 hoặc thay quy tắc làm tròn.

## 5. Ví dụ Java 17+ chạy độc lập

Lưu tệp `PrecisionZeroVsScaleZero.java`:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class PrecisionZeroVsScaleZero {
    private static void show(String label, BigDecimal value) {
        System.out.println(label + ": " + value
                + " | precision=" + value.precision()
                + " | scale=" + value.scale());
    }

    public static void main(String[] args) {
        BigDecimal value = new BigDecimal("1234.567");
        MathContext unlimited = new MathContext(0, RoundingMode.DOWN);

        show("source", value);
        show("MathContext(0)", value.round(unlimited));
        show("setScale(0)", value.setScale(0, RoundingMode.HALF_UP));
        show("MathContext(3)",
                value.round(new MathContext(3, RoundingMode.HALF_UP)));

        BigDecimal one = BigDecimal.ONE;
        BigDecimal three = new BigDecimal("3");
        show("zero 0.00", new BigDecimal("0.00"));
        show("zero 0", BigDecimal.ZERO);

        System.out.println("1 / 8 unlimited: "
                + one.divide(new BigDecimal("8"), MathContext.UNLIMITED));

        try {
            System.out.println("1 / 3 unlimited: "
                    + one.divide(three, MathContext.UNLIMITED));
        } catch (ArithmeticException ex) {
            System.out.println("1 / 3 unlimited: ArithmeticException");
        }

        System.out.println("1 / 3 precision=4: "
                + one.divide(three,
                        new MathContext(4, RoundingMode.HALF_UP)));
        System.out.println("1 / 3 scale=2: "
                + one.divide(three, 2, RoundingMode.HALF_UP));
    }
}
```

Biên dịch và chạy:

```bash
javac PrecisionZeroVsScaleZero.java
java PrecisionZeroVsScaleZero
```

Kết quả chuẩn:

```text
source: 1234.567 | precision=7 | scale=3
MathContext(0): 1234.567 | precision=7 | scale=3
setScale(0): 1235 | precision=4 | scale=0
MathContext(3): 1.23E+3 | precision=3 | scale=-1
zero 0.00: 0.00 | precision=1 | scale=2
zero 0: 0 | precision=1 | scale=0
1 / 8 unlimited: 0.125
1 / 3 unlimited: ArithmeticException
1 / 3 precision=4: 0.3333
1 / 3 scale=2: 0.33
```

Trong chương trình, `new MathContext(0, RoundingMode.DOWN)` không cắt `1234.567` xuống một số nguyên; `setScale(0, HALF_UP)` mới thực sự đặt scale về 0. Ngoài ra, precision hữu hạn 4 cho phép `1 / 3` trả `0.3333`, trong khi scale cố định 2 cho `0.33`.

## 6. Chọn công cụ cho từng yêu cầu

| Bài toán thực tế | Cách tiếp cận |
|---|---|
| Cần phép tính chính xác, không chủ động giới hạn chữ số có nghĩa | Dùng phép toán chính xác hoặc `MathContext.UNLIMITED`; chủ động xử lý trường hợp chia vô hạn tuần hoàn. |
| Cần số chữ số có nghĩa hữu hạn cho thuật toán đo lường/tính gần đúng | Dùng `MathContext(p, RoundingMode)` với `p > 0`; thống nhất quy tắc làm tròn trong chuỗi phép tính. |
| Cần đúng 2 chữ số thập phân cho một giá trị tiền tệ hay đầu ra quy định | Dùng `setScale(2, RoundingMode...)` theo quy tắc nghiệp vụ; không dùng precision 2 để thay thế. |
| Cần làm tròn về đơn vị nguyên | Dùng `setScale(0, RoundingMode...)` rồi kiểm tra kết quả. |
| Cần chắc chắn phép toán không được mất chữ số | Dùng chế độ `UNNECESSARY` cho những phép có thể đòi làm tròn và xử lý `ArithmeticException`. |

Ví dụ tiền tệ: `new BigDecimal("12.345").setScale(2, RoundingMode.HALF_UP)` cho `12.35`. Ngược lại, `round(new MathContext(2, RoundingMode.HALF_UP))` cho `12`: đây là **hai chữ số có nghĩa**, không phải hai chữ số sau dấu thập phân.

Khi tạo `BigDecimal` từ số thập phân, ưu tiên constructor chuỗi hoặc `BigDecimal.valueOf(double)` tùy dữ liệu đầu vào. `new BigDecimal(0.1)` giữ giá trị nhị phân thực của `double`, nên có thể mang một dãy chữ số thập phân dài ngoài ý muốn; vấn đề này độc lập với việc precision context có bằng 0 hay không.

## 7. Các bẫy thường gặp

1. **Nhầm `precision = 0` của `MathContext` là “kết quả không có chữ số”.** Quy ước 0 ở context mang nghĩa unlimited; `BigDecimal.precision()` của số 0 vẫn là 1.
2. **Tưởng unlimited precision nghĩa là scale bằng 0.** Scale của `new BigDecimal("1.2300")` là 4 dù phép `round(MathContext.UNLIMITED)` giữ nguyên biểu diễn đó.
3. **Tưởng unlimited precision tự xử lý phép chia vô hạn.** `1 / 3` cần một precision hữu hạn hoặc scale kèm `RoundingMode` khi muốn kết quả xấp xỉ.
4. **Trộn làm tròn theo chữ số có nghĩa với làm tròn theo chữ số thập phân.** `MathContext(3)` khác `setScale(3)`. Phải xuất phát từ đơn vị đo hoặc quy định nghiệp vụ để chọn.
5. **Dùng `equals` để so sánh giá trị số học khi scale khác nhau.** `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` trả `false`, vì `equals` xét cả scale; `compareTo` trả 0 vì giá trị số học bằng nhau.
6. **Giả định scale luôn không âm hoặc luôn được bảo toàn.** Ví dụ `new BigDecimal("1000").stripTrailingZeros()` có thể trở thành `1E+3` với scale -3. `stripTrailingZeros()` chỉ chuẩn hóa biểu diễn; nó không có nghĩa “làm tròn về số nguyên”.
7. **Gọi `setScale` mà quên gán kết quả.** Với kiểu bất biến, phải dùng `result = value.setScale(...)` hoặc sử dụng trực tiếp giá trị trả về.

## 8. Tự kiểm tra

- `new MathContext(0, RoundingMode.HALF_EVEN).getPrecision()` bằng bao nhiêu? **0**, biểu thị unlimited precision.
- `new BigDecimal("7.000").setScale(0, RoundingMode.UNNECESSARY)` có thành công không? **Có**, được `7` vì các chữ số bỏ đi đều bằng 0.
- `new BigDecimal("7.001").setScale(0, RoundingMode.UNNECESSARY)` thế nào? **Ném `ArithmeticException`**, do cần làm tròn.
- `new BigDecimal("0.00").scale()` bằng bao nhiêu? **2**; còn `precision()` là **1**.
- `new BigDecimal("1000").round(new MathContext(2, RoundingMode.HALF_UP))` có scale bằng 0 không? **Không nhất thiết**: kết quả là `1.0E+3`, precision 2, scale -2.

**Quy tắc quyết định:** cần giới hạn *bao nhiêu chữ số có nghĩa* thì dùng `MathContext` với precision dương; cần giới hạn *bao nhiêu chữ số sau dấu thập phân* thì dùng `setScale`. Giá trị precision **0 của `MathContext`** chỉ bỏ giới hạn precision, không đặt scale về 0.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-004">UP và DOWN: ra xa hoặc về phía 0</a>

<details>
<summary>Xem chi tiết</summary>

# UP và DOWN trong Java: làm tròn ra xa hoặc về phía 0

## 1. Mục tiêu và ý nghĩa

Khi phải giảm số chữ số thập phân hoặc số chữ số có nghĩa, Java cần biết phải xử lý phần bị loại bỏ như thế nào. Hai giá trị `RoundingMode.UP` và `RoundingMode.DOWN` của `java.math.RoundingMode` mô tả **hướng làm tròn so với số 0**, không mô tả hướng tăng hay giảm theo trục số.

- **UP — ra xa 0:** nếu phần bị bỏ có bất kỳ chữ số khác 0, tăng **độ lớn tuyệt đối** của kết quả lên một đơn vị ở vị trí giữ lại cuối cùng.
- **DOWN — về phía 0:** bỏ phần vượt quá độ chính xác đã chọn, **không tăng** chữ số cuối cùng còn giữ; nói cách khác, cắt bớt phần lẻ theo hướng 0.

Ví dụ, giữ **hai chữ số sau dấu thập phân**:

| Số ban đầu | UP | DOWN | Vì sao? |
| --- | ---: | ---: | --- |
| 1.231 | 1.24 | 1.23 | Phần bỏ đi là 1; UP tăng độ lớn, DOWN giữ nguyên phần đã giữ |
| -1.231 | -1.24 | -1.23 | UP đi về phía âm hơn; DOWN đi gần 0 hơn |
| 1.239 | 1.24 | 1.23 | UP không đợi chữ số bỏ đi đạt 5 |
| -1.239 | -1.24 | -1.23 | Quy tắc dựa vào trị tuyệt đối, áp dụng đối xứng với số âm |
| 1.230 | 1.23 | 1.23 | Chỉ bỏ số 0 nên không cần thay đổi giá trị |
| 0.001 | 0.01 | 0.00 | Với số rất nhỏ khác 0, UP vẫn đi xa 0 |
| -0.001 | -0.01 | 0.00 | DOWN có thể cho kết quả bằng 0 |

Điểm cốt lõi: **UP không đồng nghĩa với số lớn hơn; DOWN không đồng nghĩa với số nhỏ hơn**. Với số âm, UP thường cho số nhỏ hơn về mặt thứ tự số học, trong khi DOWN cho số lớn hơn.

## 2. Cơ chế theo vị trí giữ lại

Giả sử chỉ giữ hai chữ số sau dấu thập phân. Với `3.141`, phần được giữ trước khi làm tròn là `3.14`, còn phần bỏ là `0.001`.

- Dùng UP: phần bỏ khác 0, tăng một bước `0.01` theo hướng **ra xa 0** → `3.15`.
- Dùng DOWN: loại bỏ `0.001` theo hướng **về 0** → `3.14`.
- Với `-3.141`: UP → `-3.15`, DOWN → `-3.14`.

Một bước ở đây được xác định bởi **độ phân giải của kết quả**: giữ hai chữ số thập phân thì bước bằng `0.01`; giữ một chữ số thập phân thì bước bằng `0.1`; giữ phần nguyên thì bước bằng `1`. UP **chỉ** tăng một bước ở mức kết quả khi có phần khác 0 phải bỏ, bất kể chữ số đầu tiên bị bỏ là `1` hay `9`.

Đối với một số khác 0 cần rút ngắn, có thể hình dung:

- `DOWN`: lấy trị tuyệt đối → cắt tới độ phân giải đích → đặt lại dấu.
- `UP`: lấy trị tuyệt đối → nếu có phần khác 0 bị cắt, tăng một bước ở độ phân giải đích → đặt lại dấu.

Đây là cách hiểu khái niệm. Trong chương trình, hãy dùng `BigDecimal` cùng `RoundingMode` thay vì tự xử lý dấu hoặc nhân chia thủ công, đặc biệt ở biên độ lớn, giá trị âm và số có nhiều chữ số.

## 3. Thực hành với BigDecimal trên Java 17+

`BigDecimal` biểu diễn số thập phân chính xác theo hệ cơ số 10 và có các phương thức nhận `RoundingMode`. Dùng chuỗi như `new BigDecimal("1.231")` để bảo toàn đúng giá trị thập phân được viết ra; tránh lấy một giá trị `double` không chính xác rồi mới chuyển thành `BigDecimal`.

Tạo tệp `RoundingUpDownDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class RoundingUpDownDemo {
    private static void show(String input) {
        BigDecimal value = new BigDecimal(input);
        BigDecimal up = value.setScale(2, RoundingMode.UP);
        BigDecimal down = value.setScale(2, RoundingMode.DOWN);

        System.out.println(input
                + " -> UP=" + up.toPlainString()
                + ", DOWN=" + down.toPlainString());
    }

    public static void main(String[] args) {
        for (String input : new String[]{
                "1.231", "-1.231", "1.239",
                "1.230", "0.001", "-0.001"}) {
            show(input);
        }

        BigDecimal seven = new BigDecimal("7");
        BigDecimal three = new BigDecimal("3");
        System.out.println("7 / 3 UP="
                + seven.divide(three, 2, RoundingMode.UP));
        System.out.println("7 / 3 DOWN="
                + seven.divide(three, 2, RoundingMode.DOWN));

        BigDecimal number = new BigDecimal("17.89");
        System.out.println("Precision 3 UP="
                + number.round(new MathContext(3, RoundingMode.UP)));
        System.out.println("Precision 3 DOWN="
                + number.round(new MathContext(3, RoundingMode.DOWN)));
    }
}
~~~

Chạy:

~~~bash
javac RoundingUpDownDemo.java
java RoundingUpDownDemo
~~~

Kết quả:

~~~text
1.231 -> UP=1.24, DOWN=1.23
-1.231 -> UP=-1.24, DOWN=-1.23
1.239 -> UP=1.24, DOWN=1.23
1.230 -> UP=1.23, DOWN=1.23
0.001 -> UP=0.01, DOWN=0.00
-0.001 -> UP=-0.01, DOWN=0.00
7 / 3 UP=2.34
7 / 3 DOWN=2.33
Precision 3 UP=17.9
Precision 3 DOWN=17.8
~~~

Các điểm quan sát:

1. `setScale(2, mode)` giữ **hai chữ số sau dấu thập phân**, không phải hai chữ số có nghĩa.
2. `1.230` thành `1.23` ở cả hai mode: số 0 cuối chỉ ảnh hưởng cách biểu diễn ban đầu, không làm thay đổi giá trị.
3. `-0.001` với DOWN thành `0.00`; `BigDecimal` không duy trì một giá trị số âm riêng biệt cho zero.
4. `7 / 3` có phần dư khác 0: tại scale 2, UP là `2.34`, DOWN là `2.33`.
5. `MathContext(3, ...)` làm tròn theo **ba chữ số có nghĩa**. Với `17.89`, UP thành `17.9`, DOWN thành `17.8`.

## 4. Scale và precision là hai yêu cầu khác nhau

### Scale: số chữ số sau dấu thập phân

`BigDecimal.setScale(int newScale, RoundingMode mode)` thay đổi scale. Với `newScale = 2`:

- `new BigDecimal("123.456").setScale(2, UP)` → `123.46`.
- `new BigDecimal("123.456").setScale(2, DOWN)` → `123.45`.
- `new BigDecimal("-123.456").setScale(2, UP)` → `-123.46`.
- `new BigDecimal("-123.456").setScale(2, DOWN)` → `-123.45`.

Nếu **tăng scale**, ví dụ `1.2` thành scale 3, kết quả `1.200` chỉ được thêm số 0; không cần chọn lên hay xuống vì không mất chữ số khác 0. Nếu giảm scale nhưng phần bỏ toàn là 0, UP và DOWN cũng cho cùng giá trị.

Scale có thể âm: `new BigDecimal("124").setScale(-1, RoundingMode.DOWN)` đại diện cho `1.2E+2`, tức **120**; dùng UP cho **130**. Để hiển thị dạng thập phân thông thường, có thể gọi `toPlainString()`.

### Precision: tổng số chữ số có nghĩa

`MathContext(precision, mode)` ràng buộc **tổng chữ số có nghĩa của kết quả**, được dùng với `round(context)` hoặc một phép toán nhận `MathContext`:

| Giá trị gốc | Đích | UP | DOWN |
| --- | --- | ---: | ---: |
| 17.89 | 3 chữ số có nghĩa | 17.9 | 17.8 |
| 0.01234 | 3 chữ số có nghĩa | 0.0124 | 0.0123 |
| -987.65 | 3 chữ số có nghĩa | -988 | -987 |

Không nhầm `setScale(3, ...)` với `MathContext(3, ...)`: scale 3 giữ **ba chữ số sau dấu thập phân**, còn precision 3 giữ **ba chữ số có nghĩa** tùy độ lớn của số. `MathContext` có precision bằng 0 nghĩa là **không giới hạn precision**; khi đó quy tắc làm tròn không được dùng để cắt chữ số.

## 5. UP/DOWN và CEILING/FLOOR khác nhau thế nào?

Các tên gần giống nhau dễ gây lỗi. `RoundingMode.CEILING` hướng về **dương vô cực**, `RoundingMode.FLOOR` hướng về **âm vô cực**; chúng được xác định theo trục số. UP và DOWN được xác định theo khoảng cách đến 0.

Ví dụ làm tròn về phần nguyên (scale 0):

| Giá trị | UP (xa 0) | DOWN (gần 0) | CEILING (+∞) | FLOOR (-∞) |
| --- | ---: | ---: | ---: | ---: |
| 2.1 | 3 | 2 | 3 | 2 |
| -2.1 | -3 | -2 | -2 | -3 |
| 2.0 | 2 | 2 | 2 | 2 |
| -2.0 | -2 | -2 | -2 | -2 |

Với số dương, UP giống CEILING và DOWN giống FLOOR **trong tình huống làm tròn cùng độ phân giải**. Với số âm, sự ghép cặp đảo lại. Vì vậy yêu cầu “luôn làm tròn lên trên trục số” phải dùng CEILING, trong khi yêu cầu “luôn đẩy độ lớn ra xa 0” mới dùng UP.

`RoundingMode.HALF_UP` lại là quy tắc làm tròn **đến giá trị gần nhất**, với trường hợp đúng nửa bước thì đi ra xa 0. Ví dụ `1.231` giữ hai chữ số: UP → `1.24`, nhưng HALF_UP → `1.23`. Chữ UP trong HALF_UP **không** làm quy tắc này giống `RoundingMode.UP`.

## 6. Áp dụng đúng vào phép chia và phép tính nhiều bước

Một phép chia thập phân có thể không kết thúc: `1 / 3` không thể được biểu diễn bằng hữu hạn chữ số thập phân. Với `BigDecimal`, có thể chỉ định scale và rounding mode:

~~~java
BigDecimal result = new BigDecimal("1")
        .divide(new BigDecimal("3"), 2, RoundingMode.UP);
// result = 0.34
~~~

Nếu dùng `divide(BigDecimal divisor)` mà kết quả thập phân không kết thúc, `BigDecimal` có thể ném `ArithmeticException`. Việc chọn mode là một quyết định của nghiệp vụ: UP đảm bảo độ lớn không bị cắt giảm ở mức biểu diễn đích, còn DOWN cắt phần vượt quá.

Với giá trị âm, quy tắc vẫn như cũ: `-7 / 3` về scale 2 sẽ thành `-2.34` với UP và `-2.33` với DOWN. Không viết logic “cộng một phần nhỏ để làm tròn lên” áp dụng cho mọi dấu, vì với số âm hướng ra xa 0 chính là hướng âm hơn.

Hạn chế làm tròn sau **mọi bước trung gian** nếu bài toán yêu cầu kết quả chính xác ở cuối. Chẳng hạn:

- Trước tiên lấy `1.234 + 1.234 = 2.468`, sau đó dùng DOWN ở scale 2 → `2.46`.
- Nếu cắt mỗi số xuống `1.23` trước khi cộng, tổng thành `2.46`; cùng kết quả trong ví dụ này nhưng không phải luôn như vậy.
- Với `1.239 + 1.239 = 2.478`, dùng UP **một lần ở cuối** được `2.48`, còn làm UP từng số thành `1.24 + 1.24 = 2.48`; cũng trùng nhau trong trường hợp này.
- Trường hợp cho thấy khác biệt rõ: `1.231 + 1.231 = 2.462`; UP ở cuối cho `2.47`, còn UP mỗi số thành `1.24 + 1.24 = 2.48`.

Do đó cần quy định **thời điểm làm tròn**, **độ phân giải** và **mode**. Các phép tính cộng, trừ, nhân giữa `BigDecimal` có thể giữ độ chính xác thập phân mà không bắt buộc làm tròn; chỉ làm tròn tại ranh giới được yêu cầu. Nếu truyền `MathContext` ngay vào phép toán, kết quả của chính phép toán đó sẽ bị làm tròn theo context.

## 7. Cách chọn UP hay DOWN trong thực tế

Chọn mode từ đặc tả thay vì từ cảm giác tên gọi:

- **UP** khi yêu cầu rõ rằng giá trị khác 0, sau khi rút gọn, phải có **độ lớn không nhỏ hơn** độ lớn ban đầu ở độ phân giải đích; ví dụ quy ước làm tròn số lượng tài nguyên để đủ chỗ. Kiểm tra cả dấu âm và ý nghĩa nghiệp vụ.
- **DOWN** khi yêu cầu **cắt bớt** phần vượt quá để kết quả tiến về 0; hữu ích cho thuật toán cần loại bỏ các chữ số ngoài độ chính xác đích.
- Nếu yêu cầu là **gần nhất**, hãy đánh giá các mode `HALF_UP`, `HALF_EVEN`, v.v. UP/DOWN đều có sai lệch một hướng về độ lớn nếu phải làm tròn nhiều lần.
- Trong tài chính, tính phí và thuế, tuyệt đối không tự suy diễn rằng UP hoặc DOWN là chuẩn mặc định. Quy tắc làm tròn phải theo hợp đồng, chính sách hoặc quy định tương ứng; phép toán với giá trị âm (điều chỉnh, hoàn trả, ghi âm) đặc biệt dễ làm sai ý nghĩa.

Một câu hỏi kiểm tra trước khi lập trình: **“Khi số bị loại bỏ có chữ số khác 0, muốn trị tuyệt đối của kết quả tăng hay giảm (hoặc giữ ở phía gần 0)?”** Nếu muốn tăng độ lớn chọn UP; muốn cắt về 0 chọn DOWN.

## 8. Những lỗi thường gặp

**Lỗi 1 — Hiểu UP là luôn tăng giá trị số.** `-1.231` với UP scale 2 thành `-1.24`, là số **nhỏ hơn** `-1.231` trên trục số. Với yêu cầu tăng theo trục số, cân nhắc CEILING.

**Lỗi 2 — Tưởng UP chỉ làm tròn khi chữ số bỏ đi từ 5 trở lên.** UP áp dụng khi **bất kỳ** phần khác 0 nào bị loại. `2.001` scale 2 với UP vẫn là `2.01`. Quy tắc ngưỡng 5 thuộc các mode làm tròn gần nhất.

**Lỗi 3 — Dùng `double` để lưu giá trị thập phân rồi giả định nó chính xác.** Nhiều số cơ số 10 không thể biểu diễn đúng dưới dạng nhị phân dấu phẩy động. Với giá trị thập phân nghiệp vụ, xây dựng `BigDecimal` từ chuỗi hoặc từ `BigDecimal.valueOf(double)` khi dữ liệu đã là `double`, rồi xác nhận yêu cầu về nguồn dữ liệu và độ chính xác.

**Lỗi 4 — Quên `BigDecimal` bất biến.** Phương thức `setScale` trả về **đối tượng kết quả**, không sửa đối tượng gốc:

~~~java
BigDecimal original = new BigDecimal("1.231");
original.setScale(2, RoundingMode.UP); // Bỏ kết quả trả về.
System.out.println(original);          // 1.231

BigDecimal rounded = original.setScale(2, RoundingMode.UP);
System.out.println(rounded);           // 1.24
~~~

**Lỗi 5 — Nhầm số chữ số thập phân với chữ số có nghĩa.** `setScale(2, ...)` và `round(new MathContext(2, ...))` có thể cho kết quả rất khác nhau. Chọn đúng API theo yêu cầu.

**Lỗi 6 — Không tính tới kết quả bằng 0.** DOWN có thể biến `-0.001` thành `0.00` khi giữ hai chữ số thập phân; nếu cần biết dấu của dữ liệu đầu vào, lưu thông tin đó **trước khi làm tròn**.

**Lỗi 7 — Làm tròn quá sớm hoặc lặp lại không nhất quán.** Áp dụng UP từng phần rồi cộng có thể lệch so với cộng chính xác rồi UP tổng. Hãy ghi rõ ranh giới tính toán nào được phép làm tròn.

## 9. Tự kiểm tra

Thử tự tính trước khi chạy Java; mọi dòng đều đưa về scale 2:

| Đầu vào | UP | DOWN |
| --- | ---: | ---: |
| 12.000 | 12.00 | 12.00 |
| 12.001 | 12.01 | 12.00 |
| -12.001 | -12.01 | -12.00 |
| 0.0001 | 0.01 | 0.00 |
| -9.999 | -10.00 | -9.99 |

Nếu còn phân vân với số âm, hãy so sánh **khoảng cách từ mỗi kết quả tới 0**: UP chọn kết quả xa 0 hơn, DOWN chọn kết quả gần 0 hơn. Nếu không loại bỏ chữ số khác 0 nào, hai mode cho cùng giá trị số.

**Ghi nhớ:** Trong Java, `RoundingMode.UP` = **ra xa 0** và `RoundingMode.DOWN` = **về phía 0**. Quyết định độc lập với dấu, nhưng vị trí kết quả trên trục số phụ thuộc dấu. Luôn kết hợp mode với scale hoặc precision phù hợp và thời điểm làm tròn được xác định rõ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-005">CEILING và FLOOR: hướng về dương hoặc âm vô cực</a>

<details>
<summary>Xem chi tiết</summary>

# CEILING và FLOOR: làm tròn về dương vô cực và âm vô cực trong Java

## 1. Mục tiêu và bối cảnh

Khi làm việc với số thực hoặc số thập phân, “làm tròn” không phải lúc nào cũng có nghĩa là tìm số nguyên gần nhất. Đôi khi yêu cầu nghiệp vụ là **chọn giá trị nhỏ nhất không thấp hơn số gốc** hoặc **chọn giá trị lớn nhất không vượt quá số gốc**. Đây chính là hai hướng làm tròn **CEILING** (về dương vô cực) và **FLOOR** (về âm vô cực).

Sau bài này, bạn có thể:

- Giải thích chính xác hai hướng làm tròn trên trục số, kể cả với số âm.
- Dùng `Math.ceil(double)` và `Math.floor(double)` để làm tròn đến giá trị nguyên dưới dạng `double`.
- Dùng `BigDecimal.setScale(..., RoundingMode.CEILING/FLOOR)` để giữ số chữ số thập phân mong muốn.
- Phân biệt `Math.ceilDiv` và `Math.floorDiv` với phép chia số nguyên `/`; chọn đúng API trong bài toán đếm trang, nhóm chỉ số.
- Nhận diện sai lầm về dấu âm, số thực nhị phân, kiểu trả về và trường hợp biên.

## 2. Định nghĩa chính xác

Với số thực hữu hạn `x`:

- **Ceiling**: `ceil(x)` là **số nguyên nhỏ nhất lớn hơn hoặc bằng `x`**. Trên trục số, kết quả hướng về **+∞**.
- **Floor**: `floor(x)` là **số nguyên lớn nhất nhỏ hơn hoặc bằng `x`**. Trên trục số, kết quả hướng về **−∞**.

Về mặt toán học, chúng thỏa:

```text
floor(x) <= x <= ceil(x)

Ví dụ trên trục số:
...  -4     -3     -2     -1      0      1      2      3 ...
         ^ -2.7 nằm giữa -3 và -2
           floor(-2.7) = -3      ceil(-2.7) = -2
```

Kết quả với các ví dụ tiêu biểu:

| x | CEILING | FLOOR | Giải thích |
|---:|---:|---:|---|
| `2.7` | `3` | `2` | `3` ở phía +∞, `2` ở phía −∞ |
| `2.0` | `2` | `2` | Đã nguyên nên giữ nguyên |
| `-2.7` | `-2` | `-3` | `-2` lớn hơn `-2.7`; `-3` nhỏ hơn |
| `-2.0` | `-2` | `-2` | Đã nguyên nên giữ nguyên |
| `0.2` | `1` | `0` | Hai hướng khác nhau |
| `-0.2` | `0`* | `-1` | *Với `double`, `Math.ceil(-0.2)` tạo `-0.0` |

**Điểm dễ nhầm nhất:** “CEILING luôn tăng phần trị tuyệt đối” là sai. Với `-2.7`, đi về +∞ là đi **sang phải** tới `-2`, tức trị tuyệt đối giảm. “FLOOR là bỏ phần thập phân” cũng sai với số âm: bỏ phần thập phân của `-2.7` cho `-2`, trong khi `floor(-2.7) = -3`.

## 3. Làm tròn đến số nguyên bằng `Math.ceil` và `Math.floor`

Java cung cấp hai hàm tĩnh:

```java
public static double Math.ceil(double a);
public static double Math.floor(double a);
```

Cả hai nhận `double` và **trả về `double`**, dù với đầu vào hữu hạn thông thường giá trị kết quả bằng một số nguyên toán học. Điều này **không đồng nghĩa** kết quả tự động trở thành kiểu `int` hoặc `long`.

Chương trình độc lập sau chạy được với Java 17+:

**Tệp `CeilingFloorDemo.java`**

```java
import java.util.Locale;

public class CeilingFloorDemo {
    public static void main(String[] args) {
        double[] values = {2.7, 2.0, -2.7, -2.0, -0.25, 0.0, -0.0};

        for (double x : values) {
            System.out.printf(
                    Locale.ROOT,
                    "x=%s | ceil=%s | floor=%s%n",
                    x, Math.ceil(x), Math.floor(x));
        }
    }
}
```

Biên dịch và chạy:

```bash
javac CeilingFloorDemo.java
java CeilingFloorDemo
```

Kết quả:

```text
x=2.7 | ceil=3.0 | floor=2.0
x=2.0 | ceil=2.0 | floor=2.0
x=-2.7 | ceil=-2.0 | floor=-3.0
x=-2.0 | ceil=-2.0 | floor=-2.0
x=-0.25 | ceil=-0.0 | floor=-1.0
x=0.0 | ceil=0.0 | floor=0.0
x=-0.0 | ceil=-0.0 | floor=-0.0
```

Hai đặc điểm đáng chú ý:

1. `Math.ceil` không phải “luôn cộng một”: nếu `x` đã là số nguyên thì giữ nguyên.
2. IEEE 754 có hai biểu diễn zero: `+0.0` và `-0.0`. Chúng so sánh bằng nhau với `==`, nhưng có bit dấu khác nhau; `Math.ceil(-0.25)` cho `-0.0`, không phải `+0.0`.

### Các trường hợp biên của số thực

Theo hợp đồng API:

- Với `Double.NaN`, hai hàm đều trả `NaN`.
- Với `Double.POSITIVE_INFINITY` hoặc `Double.NEGATIVE_INFINITY`, trả chính giá trị vô cực đó.
- Với `+0.0` hoặc `-0.0`, giữ nguyên dấu zero.
- Nếu đầu vào đã là giá trị `double` nguyên về mặt toán học, trả lại chính nó.
- Nhiều số `double` rất lớn không còn biểu diễn được phần lẻ; làm tròn không khôi phục được những chữ số đã mất trước đó.

Một phép kiểm tra riêng:

```java
public class SpecialValues {
    public static void main(String[] args) {
        System.out.println(Math.ceil(Double.NaN));
        System.out.println(Math.floor(Double.POSITIVE_INFINITY));
        System.out.println(Math.ceil(Double.NEGATIVE_INFINITY));
        System.out.println(Double.doubleToRawLongBits(Math.ceil(-0.25))
                == Double.doubleToRawLongBits(-0.0));
    }
}
```

```bash
javac SpecialValues.java
java SpecialValues
```

```text
NaN
Infinity
-Infinity
true
```

## 4. Phân biệt với `Math.round`, ép kiểu và làm tròn theo trị tuyệt đối

Bốn phép xử lý này trả lời những câu hỏi khác nhau:

| Cách xử lý | `2.7` | `-2.7` | Ý nghĩa |
|---|---:|---:|---|
| `Math.ceil(x)` | `3.0` | `-2.0` | Số nguyên nhỏ nhất ≥ x |
| `Math.floor(x)` | `2.0` | `-3.0` | Số nguyên lớn nhất ≤ x |
| `Math.round(x)` | `3` | `-3` | Số nguyên gần nhất; trường hợp cách đều chọn hướng +∞ |
| `(int) x` | `2` | `-2` | Cắt phần thập phân, hướng về 0, kèm quy tắc chuyển kiểu |

Ví dụ nhấn mạnh sự khác biệt ở số âm:

```java
public class RoundingComparison {
    public static void main(String[] args) {
        double x = -2.3;
        System.out.println(Math.ceil(x));
        System.out.println(Math.floor(x));
        System.out.println(Math.round(x));
        System.out.println((int) x);
    }
}
```

```bash
javac RoundingComparison.java
java RoundingComparison
```

```text
-2.0
-3.0
-2
-2
```

Với số `-2.3`, CEILING là `-2.0`, FLOOR là `-3.0`; `Math.round` chọn số gần nhất `-2`; ép kiểu cắt phần lẻ ra `-2`. Sự trùng kết quả ở ví dụ này không có nghĩa các phép toán tương đương.

**Lưu ý khi ép kết quả về kiểu nguyên:** Chuyển `double` sang `int` hoặc `long` có thể làm mất phạm vi và thông tin đặc biệt. Java chuyển `NaN` thành `0`, và giá trị ngoài miền số nguyên được bão hòa đến biên kiểu đích. Vì vậy, nếu cần số nguyên lưu trữ, hãy kiểm tra `Double.isFinite(x)`, miền giá trị và quy tắc yêu cầu trước khi ép kiểu. Không dùng phép ép kiểu như một cách kiểm tra dữ liệu đầu vào.

## 5. Làm tròn ở chữ số thập phân: `BigDecimal` và `RoundingMode`

`Math.ceil`/`Math.floor` làm tròn đến giá trị nguyên. Nếu phải làm tròn tới **2 chữ số sau dấu thập phân**, **hàng chục**, hoặc một mức `scale` bất kỳ, dùng `BigDecimal.setScale(int, RoundingMode)`:

- `RoundingMode.CEILING`: chọn giá trị biểu diễn được gần nhất theo hướng **+∞** khi bỏ chữ số.
- `RoundingMode.FLOOR`: chọn giá trị biểu diễn được gần nhất theo hướng **−∞** khi bỏ chữ số.

Ở `scale = 2`, các điểm hợp lệ cách nhau `0.01`; ở `scale = 0`, các điểm hợp lệ là số nguyên; ở `scale = -1`, các điểm hợp lệ là bội số của 10. Hai mode áp dụng nhất quán bất kể số dương hay số âm.

| Giá trị gốc | Scale | CEILING | FLOOR |
|---:|---:|---:|---:|
| `12.341` | 2 | `12.35` | `12.34` |
| `-12.341` | 2 | `-12.34` | `-12.35` |
| `12.300` | 2 | `12.30` | `12.30` |
| `121` | −1 | `130` | `120` |
| `-121` | −1 | `-120` | `-130` |

Chương trình độc lập:

**Tệp `DecimalCeilingFloor.java`**

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DecimalCeilingFloor {
    public static void main(String[] args) {
        for (String input : new String[]{"12.341", "-12.341"}) {
            BigDecimal value = new BigDecimal(input);
            BigDecimal ceiling = value.setScale(2, RoundingMode.CEILING);
            BigDecimal floor = value.setScale(2, RoundingMode.FLOOR);

            System.out.println(input + " -> CEILING=" + ceiling
                    + ", FLOOR=" + floor);
        }

        BigDecimal value = new BigDecimal("-121");
        System.out.println("To tens: CEILING="
                + value.setScale(-1, RoundingMode.CEILING).toPlainString()
                + ", FLOOR="
                + value.setScale(-1, RoundingMode.FLOOR).toPlainString());
    }
}
```

```bash
javac DecimalCeilingFloor.java
java DecimalCeilingFloor
```

```text
12.341 -> CEILING=12.35, FLOOR=12.34
-12.341 -> CEILING=-12.34, FLOOR=-12.35
To tens: CEILING=-120, FLOOR=-130
```

### Những điều cần nhớ với `BigDecimal`

1. **Tạo từ chuỗi thập phân:** ưu tiên `new BigDecimal("12.341")` hoặc chuỗi đầu vào đã kiểm tra. `new BigDecimal(0.1)` đưa vào biểu diễn nhị phân không chính xác của `double`, nên có thể làm đổi kết quả ở ngưỡng làm tròn. Nếu bắt đầu từ `double` và chấp nhận cách biểu diễn decimal theo `Double.toString`, `BigDecimal.valueOf(double)` thường phù hợp hơn constructor nhận `double`.
2. **`BigDecimal` là bất biến:** `setScale` trả về một giá trị mới; hãy gán kết quả. Bản gốc không tự thay đổi.
3. **`scale` khác `precision`:** `setScale(2, ...)` giữ 2 chữ số *sau dấu thập phân*, không phải 2 chữ số có nghĩa. `MathContext` quản lý precision (số chữ số có nghĩa), có thể chọn cùng rounding mode nhưng khác bài toán.
4. **`CEILING` khác `UP` và `FLOOR` khác `DOWN`:** `UP` hướng **ra xa 0**; `DOWN` hướng **về 0**. Chẳng hạn với `-1.231` làm tròn scale 2: CEILING = `-1.23`, FLOOR = `-1.24`, UP = `-1.24`, DOWN = `-1.23`.
5. **Số có giá trị 0:** `BigDecimal` không có cách lưu dấu `-0.0` như IEEE 754. `new BigDecimal("-0.001").setScale(2, RoundingMode.CEILING)` cho `0.00`.

## 6. Chia số nguyên theo hướng: `Math.ceilDiv` và `Math.floorDiv`

Nếu bài toán bắt đầu bằng **hai số nguyên**, không cần chuyển sang `double` để chia rồi gọi `Math.ceil`/`Math.floor`. Java có:

- `Math.floorDiv(a, b)`: thương nguyên làm tròn về −∞ (có từ Java 8 cho `int`/`int`).
- `Math.ceilDiv(a, b)`: thương nguyên làm tròn về +∞ (có từ Java 18).
- Phép chia nguyên `a / b`: bỏ phần lẻ của thương, hướng về 0.

So sánh các dấu:

| a | b | `a / b` | `Math.ceilDiv(a,b)` | `Math.floorDiv(a,b)` |
|---:|---:|---:|---:|---:|
| 8 | 3 | 2 | 3 | 2 |
| −8 | 3 | −2 | −2 | −3 |
| 8 | −3 | −2 | −2 | −3 |
| −8 | −3 | 2 | 3 | 2 |
| 9 | 3 | 3 | 3 | 3 |

Chương trình sau yêu cầu **Java 18+** (khuyến nghị Java 21):

**Tệp `IntegerCeilingFloor.java`**

```java
public class IntegerCeilingFloor {
    public static void main(String[] args) {
        int[][] samples = {{8, 3}, {-8, 3}, {8, -3}, {-8, -3}};

        for (int[] sample : samples) {
            int a = sample[0];
            int b = sample[1];
            System.out.println(a + "/" + b + ": trunc=" + (a / b)
                    + ", ceil=" + Math.ceilDiv(a, b)
                    + ", floor=" + Math.floorDiv(a, b));
        }
    }
}
```

```bash
javac IntegerCeilingFloor.java
java IntegerCeilingFloor
```

```text
8/3: trunc=2, ceil=3, floor=2
-8/3: trunc=-2, ceil=-2, floor=-3
8/-3: trunc=-2, ceil=-2, floor=-3
-8/-3: trunc=2, ceil=3, floor=2
```

**Các giới hạn cần kiểm tra:** chia cho `0` gây `ArithmeticException`. Với `Integer.MIN_VALUE / -1` (hoặc `Long.MIN_VALUE / -1`), thương toán học vượt miền kiểu nguyên: các phiên bản `ceilDiv`/`floorDiv` thông thường có quy tắc tràn số đặc biệt. Nếu yêu cầu phát hiện tràn số, dùng `Math.ceilDivExact`/`Math.floorDivExact` (Java 18+) hoặc chọn kiểu/thuật toán an toàn hơn. Đừng thực hiện `Math.ceil((double) a / b)` rồi ép nguyên một cách máy móc: chuyển sang `double` có thể mất độ chính xác ở số nguyên lớn.

### Khi nào dùng `ceilMod` và `floorMod`?

Từ Java 18, `Math.ceilMod(a,b)` đi cặp với `ceilDiv(a,b)`; `Math.floorMod(a,b)` đi cặp với `floorDiv(a,b)`. Chúng thỏa đồng nhất thức số học theo miền `int`/`long` tương ứng (xét quy tắc số nguyên Java):

```text
a = ceilDiv(a,b) * b + ceilMod(a,b)
a = floorDiv(a,b) * b + floorMod(a,b)
```

Với `a = -8`, `b = 3`: `floorDiv = -3`, `floorMod = 1`; `ceilDiv = -2`, `ceilMod = -2`. Phép dư `%` theo chia cắt về 0 cho `-8 % 3 = -2` và không thay thế `floorMod` được trong những bài toán cần chỉ số modulo không âm khi `b > 0`.

## 7. Ứng dụng thực tế

### 7.1. Số trang tối thiểu để chứa toàn bộ bản ghi

Có `totalItems >= 0` bản ghi, mỗi trang tối đa `pageSize > 0`. Số trang cần thiết là ceiling của thương. Với Java 18+:

```java
public class PaginationExample {
    public static int pageCount(int totalItems, int pageSize) {
        if (totalItems < 0 || pageSize <= 0) {
            throw new IllegalArgumentException("Invalid pagination arguments");
        }
        return Math.ceilDiv(totalItems, pageSize);
    }

    public static void main(String[] args) {
        System.out.println(pageCount(21, 10));
        System.out.println(pageCount(20, 10));
        System.out.println(pageCount(0, 10));
    }
}
```

```bash
javac PaginationExample.java
java PaginationExample
```

```text
3
2
0
```

Lý do: 21 phần tử chiếm 2 trang đầy và 1 trang lẻ; làm tròn lên để không bỏ sót phần tử. Với dữ liệu lớn vượt `int`, cân nhắc overload `long`.

### 7.2. Chia nhóm khi chỉ số có thể âm

Giả sử chia trục thời gian thành các ô rộng 60 đơn vị, với gốc là 0. Chỉ số `-1` thuộc ô `[-60, 0)`, nên mã ô phải là `-1`, không phải `0`:

```java
System.out.println(-1 / 60);                 // 0: cắt về 0
System.out.println(Math.floorDiv(-1, 60));   // -1: đúng ô [-60, 0)
System.out.println(Math.floorDiv(61, 60));   // 1: đúng ô [60, 120)
```

Đây là ví dụ **minh họa đoạn mã**; đặt trong phương thức (ví dụ `main`) để chạy. `Math.floorDiv` hữu ích khi nhóm số nguyên cả phía trước và phía sau mốc 0.

### 7.3. Quy tắc làm tròn đơn vị tiền hoặc khối lượng

Nếu chính sách yêu cầu chọn **mức thanh toán không thấp hơn số tiền tính toán**, có thể dùng `RoundingMode.CEILING` tại `scale` được quy định. Nếu yêu cầu chọn **mức không vượt quá giá trị**, dùng `RoundingMode.FLOOR`. Với giá trị âm, kết quả vẫn luôn theo đúng hướng trên trục số. Trước khi áp dụng vào hệ thống thật, phải xác định rõ quy tắc cho hoàn tiền, tiền âm, đơn vị tiền và quy định nghiệp vụ; không suy ra “luôn làm tròn lên tiền” một cách chung chung.

## 8. Các lỗi phổ biến và cách tránh

| Sai lầm | Vì sao sai | Cách đúng |
|---|---|---|
| Cho rằng `ceil(-2.7) == -3` | Nhầm hướng +∞ với xa 0 | `Math.ceil(-2.7) == -2.0` |
| Cho rằng `floor(-2.7) == -2` | Nhầm với phép cắt phần lẻ | `Math.floor(-2.7) == -3.0` |
| Dùng `(int) (a / b)` để tính số trang | Phép chia nguyên đã loại phần dư | `Math.ceilDiv(a,b)` với `a >= 0, b > 0` |
| Dùng `Math.ceil(x * 100) / 100` cho mọi khoản tiền | Phép nhân/chia `double` có sai số nhị phân | Dùng `BigDecimal` từ nguồn thập phân chính xác và `setScale(2, mode)` |
| Nhầm `CEILING` với `UP` | Với số âm, hướng về +∞ lại gần 0 | Chọn theo yêu cầu “về +∞” hay “ra xa 0” |
| Quên kiểu trả về của `Math.ceil` | Kết quả là `double`, có NaN/vô cực/−0.0 | Giữ `double` hoặc kiểm tra trước khi đổi kiểu |
| Quên kiểm tra `pageSize > 0` | Chia nguyên cho zero gây lỗi | Validate đầu vào trước `ceilDiv` |
| Tin rằng `Math.ceil` sửa được sai số đã tồn tại | Thông tin có thể đã mất khi tạo `double` | Dùng `BigDecimal`/kiểu dữ liệu đúng từ nguồn |

**Mẹo tự kiểm:** Với giá trị hữu hạn và kết quả có nghĩa, CEILING không được nhỏ hơn đầu vào; FLOOR không được lớn hơn đầu vào. Riêng cách so sánh số thực NaN cần được xử lý riêng vì mọi phép so sánh thứ tự với `NaN` đều cho `false`.

## 9. Bài tập tự kiểm tra có lời giải

1. `Math.ceil(-4.01)` và `Math.floor(-4.01)` là bao nhiêu?  
   **Đáp án:** `-4.0` và `-5.0`. CEILING đi về +∞, FLOOR đi về −∞.
2. `new BigDecimal("-5.301").setScale(2, RoundingMode.CEILING)` và `FLOOR` trả gì?  
   **Đáp án:** `-5.30` và `-5.31`. Khoảng cách giá trị ở scale 2 là 0.01.
3. Có 31 bản ghi, mỗi trang tối đa 10. Cần bao nhiêu trang?  
   **Đáp án:** `Math.ceilDiv(31, 10) == 4` (Java 18+).
4. Tại sao `Math.floorDiv(-1, 10) == -1` nhưng `-1 / 10 == 0`?  
   **Đáp án:** Phép thứ nhất lấy floor của thương `-0.1`, phép thứ hai cắt thương về 0.
5. `Math.ceil(-0.25) == 0.0` có đúng không?  
   **Đáp án:** Đúng khi so sánh bằng `==`, nhưng kết quả có biểu diễn `-0.0`, khác bit dấu so với `+0.0`.

## 10. Ghi nhớ và tham khảo

Hãy bắt đầu từ **hướng của kết quả trên trục số**, sau đó chọn API theo loại dữ liệu:

- Số thực cần làm tròn tới giá trị nguyên dưới dạng `double`: `Math.ceil` hoặc `Math.floor`.
- Số thập phân cần kiểm soát chữ số sau dấu phẩy, đặc biệt các giá trị chính xác theo hệ thập phân: `BigDecimal.setScale(scale, RoundingMode.CEILING/FLOOR)`.
- Thương của hai số nguyên: `Math.ceilDiv` hoặc `Math.floorDiv` (kiểm tra phiên bản Java, trường hợp 0 và tràn số).

Tài liệu API chính thức:

- [Java 21 — `java.lang.Math`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html)
- [Java 21 — `java.math.RoundingMode`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html)
- [Java 21 — `java.math.BigDecimal`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-006">HALF_UP và HALF_DOWN ở vị trí đúng giữa</a>

<details>
<summary>Xem chi tiết</summary>

# HALF_UP và HALF_DOWN khi giá trị nằm đúng giữa

## Vấn đề cần hiểu

Trong Java, `RoundingMode.HALF_UP` và `RoundingMode.HALF_DOWN` đều làm tròn về **giá trị gần nhất**. Chúng chỉ khác nhau khi giá trị ban đầu **cách đều hai mức đích**, gọi là *đúng giữa* (*tie* hay *halfway case*).

Ví dụ, giữ hai chữ số sau dấu thập phân: `2.34` và `2.35` cách nhau `0.01`, còn `2.345` cách mỗi số `0.005`. Khi đó:

- `HALF_UP` chọn `2.35` (xa số 0 hơn).
- `HALF_DOWN` chọn `2.34` (gần số 0 hơn).
- `2.344` không đúng giữa: cả hai chọn `2.34`.
- `2.346` không đúng giữa: cả hai chọn `2.35`.

Với mục tiêu giữ `s` chữ số thập phân, hai mức liền kề cách nhau `10^-s` và điểm giữa cách mỗi mức `(10^-s)/2`. Ở `scale = 2`, nửa bước chính xác là `0.005`.

```text
2.34           2.345           2.35
 |---------------|---------------|
       0.005           0.005
           đúng giữa
```

**Cần xét toàn bộ phần chữ số bị loại.** `2.345000` vẫn đúng giữa vì chỉ thêm số 0 cuối; `2.345001` không còn đúng giữa vì nằm gần `2.35` hơn. Việc “chữ số đầu bị loại là 5” chưa đủ để kết luận có tie.

## Quy tắc khi số dương và số âm

| Số ban đầu, giữ 2 chữ số | HALF_UP | HALF_DOWN | Giải thích |
|---|---|---|---|
| `2.344` | `2.34` | `2.34` | Gần mức thấp hơn |
| `2.345` | `2.35` | `2.34` | Tie dương |
| `2.346` | `2.35` | `2.35` | Gần mức cao hơn |
| `-2.344` | `-2.34` | `-2.34` | Gần `-2.34` |
| `-2.345` | `-2.35` | `-2.34` | Tie âm |
| `-2.346` | `-2.35` | `-2.35` | Gần `-2.35` |
| `0.005` | `0.01` | `0.00` | Tie sát 0 |
| `-0.005` | `-0.01` | `0.00` | Tie âm sát 0 |
| `2.34501` | `2.35` | `2.35` | Trên điểm giữa |

Điểm quan trọng nhất: **“UP” không có nghĩa kết quả số học tăng**. Với `-2.345`, `HALF_UP` cho `-2.35`, tức là số nhỏ hơn trên trục số nhưng **xa 0 hơn**; `HALF_DOWN` cho `-2.34`, tức là **gần 0 hơn**. Với `-0.005` và `HALF_DOWN`, `BigDecimal` trả `0.00` (số 0 có scale 2), không cần một kết quả “-0.00”.

Khi giá trị **không** đúng giữa, cả hai chế độ đều chọn số gần nhất bất kể dấu; ví dụ `-2.346` thành `-2.35` ở cả hai chế độ.

## Java 17+ — chương trình chạy độc lập

Tạo file `HalfTieDemo.java`:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class HalfTieDemo {
    private static void show(String raw) {
        BigDecimal value = new BigDecimal(raw);
        BigDecimal up = value.setScale(2, RoundingMode.HALF_UP);
        BigDecimal down = value.setScale(2, RoundingMode.HALF_DOWN);

        System.out.println(raw + " | " + up.toPlainString()
                + " | " + down.toPlainString());
    }

    public static void main(String[] args) {
        System.out.println("value | HALF_UP | HALF_DOWN");
        for (String raw : new String[] {
                "2.344", "2.345", "2.346",
                "-2.344", "-2.345", "-2.346",
                "0.005", "-0.005", "2.34501"
        }) {
            show(raw);
        }

        BigDecimal one = BigDecimal.ONE;
        BigDecimal eight = new BigDecimal("8");
        System.out.println("1 / 8 | "
                + one.divide(eight, 2, RoundingMode.HALF_UP)
                + " | "
                + one.divide(eight, 2, RoundingMode.HALF_DOWN));

        BigDecimal p = new BigDecimal("12.35");
        System.out.println("precision 3 | "
                + p.round(new MathContext(3, RoundingMode.HALF_UP))
                        .toPlainString()
                + " | "
                + p.round(new MathContext(3, RoundingMode.HALF_DOWN))
                        .toPlainString());
    }
}
```

Dùng JDK 17 hoặc mới hơn:

```bash
javac HalfTieDemo.java
java HalfTieDemo
```

Kết quả chính xác:

```text
value | HALF_UP | HALF_DOWN
2.344 | 2.34 | 2.34
2.345 | 2.35 | 2.34
2.346 | 2.35 | 2.35
-2.344 | -2.34 | -2.34
-2.345 | -2.35 | -2.34
-2.346 | -2.35 | -2.35
0.005 | 0.01 | 0.00
-0.005 | -0.01 | 0.00
2.34501 | 2.35 | 2.35
1 / 8 | 0.13 | 0.12
precision 3 | 12.4 | 12.3
```

Giải thích mã:

1. `new BigDecimal(raw)` nhận biểu diễn **thập phân chính xác** từ chuỗi, giúp các trường hợp tie đúng như mô tả.
2. `setScale(2, mode)` yêu cầu chính xác 2 chữ số sau dấu thập phân và áp dụng chế độ nếu phải loại chữ số.
3. `1 / 8 = 0.125` nằm giữa `0.12` và `0.13`; `divide(eight, 2, mode)` trả về `0.13` theo `HALF_UP`, `0.12` theo `HALF_DOWN`.
4. `new MathContext(3, mode)` yêu cầu **3 chữ số có nghĩa**, nên `12.35` thành `12.4` hoặc `12.3`.
5. `BigDecimal` là kiểu **bất biến**. `setScale`, `divide` và `round` trả về kết quả mới; không thay đổi đối tượng cũ. Muốn lưu thì gán lại, chẳng hạn `value = value.setScale(2, mode)`.

## Scale, precision và độ lớn một bước làm tròn

**Scale** là số chữ số bên phải dấu thập phân. `setScale(2, mode)` làm tròn theo phần trăm đơn vị; `setScale(0, mode)` làm tròn tới đơn vị; `setScale(-1, mode)` làm tròn tới hàng chục.

**Precision** trong `MathContext` là **tổng số chữ số có nghĩa**, không phải riêng phần thập phân. Ví dụ `12.35` có 4 chữ số có nghĩa; làm tròn về precision 3 tạo tie giữa `12.3` và `12.4`.

| Mục tiêu | Đầu vào | HALF_UP | HALF_DOWN |
|---|---|---|---|
| `setScale(2)` | `7.125` | `7.13` | `7.12` |
| `setScale(0)` | `-2.5` | `-3` | `-2` |
| `setScale(-1)` | `145` | `150` | `140` |
| `MathContext(3)` | `12.35` | `12.4` | `12.3` |

Làm tròn ở scale âm có thể cho biểu diễn ký pháp khoa học trong `BigDecimal`; `toPlainString()` hiển thị theo dạng thông thường. Nếu `setScale` cần loại chữ số nhưng không cung cấp `RoundingMode`, phép gọi có thể ném `ArithmeticException`. `RoundingMode.UNNECESSARY` cũng ném lỗi khi cần làm tròn, hữu ích nếu hợp đồng dữ liệu yêu cầu kết quả chính xác tuyệt đối ở scale đích.

## Những lỗi hiểu và áp dụng thường gặp

### 1. Thấy chữ số 5 rồi tự cho là đúng giữa

Ở scale 2, `2.34499` ở dưới nửa bước nên cả hai về `2.34`; `2.34500` là tie nên cho kết quả khác; `2.34501` ở trên nửa bước nên cả hai về `2.35`. Khi kiểm thử phải có các giá trị ngay trước, đúng tại và ngay sau điểm giữa.

### 2. Nhầm HALF_UP với UP, CEILING hoặc Math.round

`RoundingMode.UP` luôn đi xa số 0 khi phải loại chữ số; `DOWN` luôn đi về 0. `CEILING` về phía dương và `FLOOR` về phía âm. Chúng khác với `HALF_UP`/`HALF_DOWN` vì hai chế độ HALF ưu tiên **mức gần nhất**.

`Math.round(-2.5)` trả `-2`: tại tie, `Math.round` lựa chọn về phía dương trên trục số, khác `BigDecimal("-2.5").setScale(0, RoundingMode.HALF_UP)` cho `-3`. Vì vậy, không thay `RoundingMode` bằng `Math.round` theo trực giác tên gọi.

### 3. Dùng double làm mất tính chính xác của tie

`new BigDecimal(2.675)` nhận giá trị nhị phân xấp xỉ mà `double` đang lưu, có thể không đúng bằng `2.675`. Điểm giữa thập phân có thể bị lệch khiến kết quả bất ngờ. Để diễn đạt giá trị thập phân chính xác, dùng `new BigDecimal("2.675")`; tại scale 2 với `HALF_UP`, kết quả là `2.68`.

`BigDecimal.valueOf(2.675)` chuyển qua chuỗi biểu diễn của `double` và thường gần với giá trị con người mong đợi hơn constructor nhận `double`, nhưng **không khôi phục được độ chính xác đã mất** trong các phép toán `double` trước đó.

### 4. Làm tròn ở bước trung gian tùy ý

Hai khoản `1.245`: làm tròn từng khoản tại scale 2 với `HALF_UP` rồi cộng cho `1.25 + 1.25 = 2.50`. Nếu cộng trước rồi làm tròn một lần, `1.245 + 1.245 = 2.490` cho `2.49`. Cùng một chế độ nhưng khác kết quả vì **thời điểm làm tròn** khác nhau. Quy định nghiệp vụ phải nêu cả thời điểm, scale và chế độ.

### 5. Chọn chế độ vì “nghe hợp lý”

Không có quy tắc phổ quát rằng mọi khoản tiền phải dùng `HALF_UP` hoặc `HALF_DOWN`. Hãy theo yêu cầu của hệ thống, chuẩn hay hợp đồng. Còn `HALF_EVEN`, một chính sách phổ biến khác, chọn chữ số cuối được giữ là **số chẵn** tại tie: ở scale 2, `2.345` thành `2.34` nhưng `2.355` thành `2.36`. `HALF_EVEN` không đồng nghĩa với `HALF_DOWN`.

## Áp dụng và tự kiểm tra

Trước khi triển khai, trả lời được: đầu vào có giữ chính xác giá trị thập phân không; mục tiêu là scale hay precision; khi tie phải đi xa hay gần số 0; làm tròn ở bước nào; đã kiểm thử số âm và các số sát hai bên tie hay chưa. Dùng `RoundingMode` tường minh trong `setScale`, `divide` hoặc `round(MathContext)`.

**Bài tự kiểm tra:** Làm tròn `-7.1250` tới 2 chữ số. Đây là tie giữa `-7.12` và `-7.13`, nên `HALF_UP` cho **`-7.13`**, `HALF_DOWN` cho **`-7.12`**. Nếu đổi thành `-7.1251`, cả hai đều cho `-7.13` do gần `-7.13` hơn.

**Ghi nhớ:** Hai chế độ đều làm tròn về mức gần nhất; **chỉ tại điểm đúng giữa**, `HALF_UP` chọn xa số 0 và `HALF_DOWN` chọn gần số 0.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-007">HALF_EVEN và lựa chọn chữ số chẵn</a>

<details>
<summary>Xem chi tiết</summary>

# HALF_EVEN và lựa chọn chữ số chẵn trong BigDecimal

## 1. Vì sao cần quy tắc HALF_EVEN?

Khi biểu diễn hoặc tính toán số thập phân với số chữ số giới hạn, đôi khi phải bỏ bớt chữ số. Thông thường ta làm tròn đến giá trị **gần số ban đầu nhất**. Tuy nhiên, có những giá trị nằm **chính xác giữa hai kết quả hợp lệ** (gọi là *halfway* hoặc *tie*): cần thêm quy tắc để chọn một phía.

**RoundingMode.HALF_EVEN** trong Java là quy tắc *round to nearest, ties to even*: **chọn kết quả gần nhất; nếu hai phía cách đều nhau, chọn kết quả có chữ số cuối được giữ lại là số chẵn** (0, 2, 4, 6, 8). Đây còn được gọi là *round half to even* hoặc *banker's rounding*.

Phạm vi: Java 17+, `java.math.BigDecimal`, `java.math.RoundingMode`, `java.math.MathContext`. Bài này giải thích chính xác chữ số nào phải chẵn, những trường hợp không thực sự là tie, xử lý số âm, khác biệt với HALF_UP/HALF_DOWN và lựa chọn thời điểm làm tròn trong ứng dụng.

## 2. Cơ chế: gần nhất trước, chẵn chỉ khi hòa

Giả sử làm tròn một giá trị đến số nguyên. Những giá trị có phần lẻ khác 0.5 được làm tròn về số nguyên gần hơn. Nếu phần lẻ bằng đúng 0.5, hai số nguyên gần nhất cách đều nhau nên HALF_EVEN chọn số chẵn:

| Số gốc | Hai kết quả gần nhất | HALF_EVEN | Lý do |
| --- | --- | ---: | --- |
| 2.4 | 2 và 3 | 2 | Khoảng cách đến 2 nhỏ hơn |
| 2.5 | 2 và 3 | 2 | Hòa; 2 chẵn |
| 3.5 | 3 và 4 | 4 | Hòa; 4 chẵn |
| 4.5 | 4 và 5 | 4 | Hòa; 4 chẵn |
| 5.5 | 5 và 6 | 6 | Hòa; 6 chẵn |
| -2.5 | -3 và -2 | -2 | Hòa; chữ số 2 chẵn |
| -3.5 | -4 và -3 | -4 | Hòa; chữ số 4 chẵn |
| 2.51 | 2 và 3 | 3 | Gần 3 hơn; không hòa |

**HALF_EVEN không có nghĩa “luôn làm tròn thành số nguyên chẵn”.** Khi giá trị không hòa, một kết quả có chữ số lẻ vẫn được chọn nếu nó gần hơn, như 2.51 → 3.

Khi cần giữ chữ số thập phân, tính chẵn/lẻ áp dụng cho **chữ số cuối cùng được giữ lại**, không phải phần nguyên. Nếu làm tròn 1.25 đến một chữ số thập phân, ta chọn giữa 1.2 và 1.3: 1.2 có chữ số hàng phần mười là 2 (chẵn), nên được chọn. Với 1.35, ta chọn 1.4 vì chữ số 4 chẵn.

### Kiểm tra tie chính xác

Với scale đích bằng 2, hai số thập phân kề nhau cách 0.01; điểm giữa cách mỗi phía 0.005.

- **1.005**: chính giữa 1.00 và 1.01 → 1.00 (hàng phần trăm là 0, chẵn).
- **1.015**: chính giữa 1.01 và 1.02 → 1.02 (chữ số 2, chẵn).
- **1.0050**: vẫn bằng 1.005, nên vẫn hòa → 1.00.
- **1.0051**: gần 1.01 hơn → 1.01, dù chữ số hàng phần trăm là 1 (lẻ).
- **1.0049**: gần 1.00 hơn → 1.00.

Nếu chữ số đầu tiên bị bỏ là 5 nhưng **phần bị bỏ sau chữ số 5 còn khác 0**, giá trị không nằm chính giữa. Đừng quyết định theo chẵn/lẻ trước khi xác định khoảng cách.

## 3. Dùng setScale(scale, RoundingMode.HALF_EVEN)

Trong BigDecimal, phương thức `setScale(int newScale, RoundingMode roundingMode)` tạo biểu diễn với scale yêu cầu. Scale bằng 0 tương ứng không có chữ số sau dấu thập phân; scale 1 giữ một chữ số; scale 2 giữ hai chữ số. Scale âm cho phép làm tròn đến hàng chục, hàng trăm.

Ví dụ, `x.setScale(2, RoundingMode.HALF_EVEN)` yêu cầu giữ đúng hai chữ số thập phân và dùng HALF_EVEN nếu phải bỏ các chữ số có giá trị. Nếu chỉ tăng scale như 1.2 → 1.200, không có giá trị nào bị bỏ, nên không thực hiện làm tròn theo nghĩa chọn gần nhất. BigDecimal là **immutable**: phương thức trả về giá trị, không sửa đối tượng gốc.

### Ví dụ Java 17: tie khi làm tròn số nguyên

Lưu dưới tên `HalfEvenIntegerDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class HalfEvenIntegerDemo {
    public static void main(String[] args) {
        String[] inputs = {
            "2.4", "2.5", "3.5", "4.5",
            "5.5", "-2.5", "-3.5", "2.51"
        };

        for (String input : inputs) {
            BigDecimal x = new BigDecimal(input);
            BigDecimal rounded = x.setScale(0, RoundingMode.HALF_EVEN);
            System.out.println(input + " -> " + rounded.toPlainString());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac HalfEvenIntegerDemo.java
java HalfEvenIntegerDemo
~~~

Đầu ra chính xác:

~~~text
2.4 -> 2
2.5 -> 2
3.5 -> 4
4.5 -> 4
5.5 -> 6
-2.5 -> -2
-3.5 -> -4
2.51 -> 3
~~~

Cặp 2.5/3.5 chứng minh HALF_EVEN không mặc định luôn làm tròn lên hay xuống. Hai giá trị âm chứng minh đây cũng không phải quy tắc luôn làm tròn về 0.

## 4. Trường hợp thập phân, dấu âm và chuyển nhớ

Với scale đích bằng 1, chữ số cuối được giữ lại nằm ở hàng phần mười. Tương tự, với scale đích bằng 2, xét hàng phần trăm. Những chữ số chẵn có thể nằm sau dấu thập phân, dù phần nguyên là số chẵn hay lẻ.

| Giá trị | Scale đích | Kết quả HALF_EVEN | Lý do |
| --- | ---: | --- | --- |
| 1.25 | 1 | 1.2 | Tie, chữ số 2 chẵn |
| 1.35 | 1 | 1.4 | Tie, chữ số 4 chẵn |
| 1.2500 | 1 | 1.2 | Số 0 phía sau không phá tie |
| 1.2501 | 1 | 1.3 | Lớn hơn điểm giữa; gần 1.3 |
| 1.2499 | 1 | 1.2 | Nhỏ hơn điểm giữa; gần 1.2 |
| -1.25 | 1 | -1.2 | Tie, chữ số 2 chẵn |
| -1.35 | 1 | -1.4 | Tie, chữ số 4 chẵn |
| 9.95 | 1 | 10.0 | Tie giữa 9.9 và 10.0; chữ số 0 chẵn |
| 8.235 | 2 | 8.24 | Tie giữa 8.23 và 8.24; 4 chẵn |
| 8.245 | 2 | 8.24 | Tie giữa 8.24 và 8.25; 4 chẵn |

Tạo `HalfEvenDecimalDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class HalfEvenDecimalDemo {
    static void show(String source) {
        BigDecimal number = new BigDecimal(source);
        System.out.println(source + " -> "
                + number.setScale(1, RoundingMode.HALF_EVEN).toPlainString());
    }

    public static void main(String[] args) {
        show("1.25");
        show("1.35");
        show("1.2500");
        show("1.2501");
        show("1.2499");
        show("-1.25");
        show("-1.35");
        show("9.95");
    }
}
~~~

~~~bash
javac HalfEvenDecimalDemo.java
java HalfEvenDecimalDemo
~~~

Đầu ra:

~~~text
1.25 -> 1.2
1.35 -> 1.4
1.2500 -> 1.2
1.2501 -> 1.3
1.2499 -> 1.2
-1.25 -> -1.2
-1.35 -> -1.4
9.95 -> 10.0
~~~

Với 9.95 → 10.0, hãy chú ý đến **chuyển nhớ**: việc tăng chữ số bên trái là hợp lệ. Khi giữ một chữ số lẻ, chữ số cuối của 10.0 là 0, thỏa mãn tiêu chí chẵn.

## 5. So sánh HALF_EVEN, HALF_UP và HALF_DOWN

Cả ba thuộc nhóm chọn số gần nhất khi không hòa. Khi chính xác hòa:

- **HALF_UP**: chọn phương án xa 0 hơn.
- **HALF_DOWN**: chọn phương án gần 0 hơn.
- **HALF_EVEN**: chọn phương án có chữ số cuối giữ lại chẵn.

| Giá trị → scale 0 | HALF_UP | HALF_DOWN | HALF_EVEN |
| --- | ---: | ---: | ---: |
| 2.5 | 3 | 2 | 2 |
| 3.5 | 4 | 3 | 4 |
| -2.5 | -3 | -2 | -2 |
| -3.5 | -4 | -3 | -4 |
| 2.6 | 3 | 3 | 3 |

Không nhầm **HALF_UP** với **UP**: `RoundingMode.UP` làm tròn ra xa 0 bất cứ khi nào bỏ phần có giá trị, kể cả không hòa. Tương tự, `RoundingMode.DOWN` làm tròn về 0 chứ không giống `HALF_DOWN`.

Tạo `HalfEvenModesDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class HalfEvenModesDemo {
    static void compare(String text) {
        BigDecimal x = new BigDecimal(text);
        String up = x.setScale(0, RoundingMode.HALF_UP).toPlainString();
        String down = x.setScale(0, RoundingMode.HALF_DOWN).toPlainString();
        String even = x.setScale(0, RoundingMode.HALF_EVEN).toPlainString();
        System.out.println(text + ": UP=" + up
                + ", DOWN=" + down + ", EVEN=" + even);
    }

    public static void main(String[] args) {
        compare("2.5");
        compare("3.5");
        compare("-2.5");
        compare("-3.5");
        compare("2.6");
    }
}
~~~

~~~bash
javac HalfEvenModesDemo.java
java HalfEvenModesDemo
~~~

Đầu ra:

~~~text
2.5: UP=3, DOWN=2, EVEN=2
3.5: UP=4, DOWN=3, EVEN=4
-2.5: UP=-3, DOWN=-2, EVEN=-2
-3.5: UP=-4, DOWN=-3, EVEN=-4
2.6: UP=3, DOWN=3, EVEN=3
~~~

Hai chế độ có thể cho kết quả trùng nhau trong một trường hợp riêng lẻ mà không có nghĩa tương đương. Hãy kiểm tra cả hai loại tie (chữ số cuối cũ chẵn/lẻ) và các giá trị không hòa.

## 6. HALF_EVEN cùng MathContext: phân biệt precision với scale

`setScale(2, HALF_EVEN)` giữ **hai chữ số sau dấu thập phân**. `MathContext(4, HALF_EVEN)` giữ tối đa **bốn chữ số có nghĩa** tính từ chữ số khác 0 đầu tiên. Đây là hai quy tắc giới hạn hoàn toàn khác nhau:

- 12.345 với scale 2 → 12.34.
- 12.345 với precision 4 → 12.34, tình cờ trùng vì chữ số có nghĩa thứ tư chính là hàng phần trăm.
- 123.45 với scale 2 → 123.45; với precision 4 → 123.4.
- 123.55 với precision 4 → 123.6: hai kết quả 123.5 và 123.6 cách đều 123.55, chữ số 6 chẵn.

`BigDecimal.round(MathContext)` làm tròn theo precision và rounding mode; các phép toán hỗ trợ `MathContext` cũng có thể làm tròn kết quả theo ngữ cảnh này. Precision 0 trong `MathContext.UNLIMITED` biểu thị không giới hạn số chữ số có nghĩa; quy tắc làm tròn không bị áp dụng chỉ vì precision. Nếu dùng `UNNECESSARY` mà thực sự cần loại bỏ chữ số có giá trị, Java ném `ArithmeticException`.

Tạo `HalfEvenPrecisionDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class HalfEvenPrecisionDemo {
    public static void main(String[] args) {
        BigDecimal first = new BigDecimal("12.345");
        BigDecimal second = new BigDecimal("12.355");
        BigDecimal large = new BigDecimal("123.45");
        MathContext mc = new MathContext(4, RoundingMode.HALF_EVEN);

        System.out.println("first scale 2  = "
                + first.setScale(2, RoundingMode.HALF_EVEN));
        System.out.println("second scale 2 = "
                + second.setScale(2, RoundingMode.HALF_EVEN));
        System.out.println("first prec 4   = " + first.round(mc));
        System.out.println("second prec 4  = " + second.round(mc));
        System.out.println("large prec 4   = " + large.round(mc));
    }
}
~~~

~~~bash
javac HalfEvenPrecisionDemo.java
java HalfEvenPrecisionDemo
~~~

Đầu ra:

~~~text
first scale 2  = 12.34
second scale 2 = 12.36
first prec 4   = 12.34
second prec 4  = 12.36
large prec 4   = 123.4
~~~

Chọn `setScale` khi có quy định số chữ số phần lẻ; chọn `MathContext` khi giới hạn số chữ số **có nghĩa** trong phép tính. Không đổi một API sang API còn lại chỉ vì đều hỗ trợ HALF_EVEN.

## 7. HALF_EVEN khi chia BigDecimal

Một số phép chia không thể biểu diễn thành số thập phân hữu hạn (ví dụ 1/3). `BigDecimal.divide(divisor)` có thể ném `ArithmeticException` khi không có kết quả thập phân hữu hạn chính xác. Có thể chỉ định scale và chế độ làm tròn trong overload `divide(divisor, scale, roundingMode)`.

Ví dụ: 1/8 = 0.125 nằm chính giữa 0.12 và 0.13 → 0.12 (2 chẵn). 3/8 = 0.375 nằm chính giữa 0.37 và 0.38 → 0.38 (8 chẵn).

Tạo `HalfEvenDivisionDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class HalfEvenDivisionDemo {
    public static void main(String[] args) {
        BigDecimal eight = new BigDecimal("8");
        BigDecimal first = BigDecimal.ONE.divide(
                eight, 2, RoundingMode.HALF_EVEN);
        BigDecimal second = new BigDecimal("3").divide(
                eight, 2, RoundingMode.HALF_EVEN);
        BigDecimal third = BigDecimal.ONE.divide(
                new BigDecimal("3"), 2, RoundingMode.HALF_EVEN);

        System.out.println("1 / 8 = " + first);
        System.out.println("3 / 8 = " + second);
        System.out.println("1 / 3 = " + third);
    }
}
~~~

~~~bash
javac HalfEvenDivisionDemo.java
java HalfEvenDivisionDemo
~~~

Đầu ra:

~~~text
1 / 8 = 0.12
3 / 8 = 0.38
1 / 3 = 0.33
~~~

1/3 được làm tròn về 0.33 vì gần 0.33 hơn 0.34, không phụ thuộc tính chẵn. Để biết cần giữ bao nhiêu chữ số và khi nào làm tròn, phải dựa vào yêu cầu đầu ra/nhất quán của nghiệp vụ.

## 8. Vì sao còn gọi là banker's rounding?

Nếu dữ liệu có rất nhiều giá trị dương rơi đúng điểm giữa, làm tròn luôn xa 0 theo HALF_UP có thể tạo xu hướng tăng tổng. HALF_EVEN chọn hướng lên hoặc xuống theo tính chẵn của chữ số cuối được giữ. Với phân bố các tie cân bằng, cách này **có thể làm giảm thiên lệch tích lũy**.

Ví dụ, xét bốn giá trị cần làm tròn thành số nguyên:

| Cách tính | Các giá trị | Tổng |
| --- | --- | ---: |
| Chưa làm tròn | 1.5, 2.5, 3.5, 4.5 | 12 |
| HALF_UP | 2, 3, 4, 5 | 14 |
| HALF_EVEN | 2, 2, 4, 4 | 12 |

Đây là minh họa, **không phải chứng minh HALF_EVEN luôn giữ nguyên tổng hoặc hoàn toàn không thiên lệch**. Kết quả tùy dữ liệu, đặc biệt khi tie tập trung về một nhóm chữ số. Tên gọi "banker's rounding" cũng không có nghĩa tất cả ngân hàng, chuẩn kế toán hay thuế đều bắt buộc dùng HALF_EVEN; phải theo quy định nghiệp vụ thực tế.

### Làm tròn từng khoản khác làm tròn tổng

Nếu có ba khoản 1.005 và cần số tiền hai chữ số lẻ:

- Làm tròn từng khoản HALF_EVEN: mỗi khoản 1.00; tổng bằng **3.00**.
- Cộng giá trị nguyên gốc rồi làm tròn một lần: 3.015 → **3.02**.

Tạo `HalfEvenTotalsDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class HalfEvenTotalsDemo {
    public static void main(String[] args) {
        BigDecimal[] items = {
            new BigDecimal("1.005"),
            new BigDecimal("1.005"),
            new BigDecimal("1.005")
        };

        BigDecimal rawTotal = BigDecimal.ZERO;
        BigDecimal roundedEachTotal = BigDecimal.ZERO;

        for (BigDecimal item : items) {
            rawTotal = rawTotal.add(item);
            roundedEachTotal = roundedEachTotal.add(
                    item.setScale(2, RoundingMode.HALF_EVEN));
        }

        BigDecimal roundedAtEnd =
                rawTotal.setScale(2, RoundingMode.HALF_EVEN);

        System.out.println("raw total        = " + rawTotal);
        System.out.println("round each, sum  = " + roundedEachTotal);
        System.out.println("sum, round once  = " + roundedAtEnd);
    }
}
~~~

~~~bash
javac HalfEvenTotalsDemo.java
java HalfEvenTotalsDemo
~~~

Đầu ra:

~~~text
raw total        = 3.015
round each, sum  = 3.00
sum, round once  = 3.02
~~~

Cả hai phép làm tròn đều tuân thủ HALF_EVEN. Việc chọn **thời điểm làm tròn** (theo dòng, theo giao dịch hay theo tổng) ảnh hưởng kết quả và phải xác định rõ từ quy tắc nghiệp vụ. Hạn chế các lần làm tròn trung gian nếu không bắt buộc, để không mất độ chính xác cần cho phép tính sau.

## 9. Tránh tạo BigDecimal bằng double nếu cần tie thập phân chính xác

Nhiều số thập phân không biểu diễn chính xác trong hệ nhị phân của kiểu `double`. Vì vậy, `new BigDecimal(2.675)` tạo BigDecimal biểu diễn chính xác **giá trị double nhị phân đã nhận**, vốn hơi nhỏ hơn 2.675. Khi làm tròn về hai chữ số, số này gần 2.67 hơn; HALF_EVEN không chọn theo tie vì trên thực tế không có tie.

Ngược lại, `new BigDecimal("2.675")` giữ chính xác 2.675: nó ở chính giữa 2.67 và 2.68, nên HALF_EVEN chọn 2.68. `BigDecimal.valueOf(2.675)` dùng biểu diễn thập phân chuẩn của double nên cũng cho 2.68 trong ví dụ, nhưng cách đó không phục hồi được mọi độ chính xác đã mất từ dữ liệu trước đó.

Tạo `HalfEvenInputDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class HalfEvenInputDemo {
    public static void main(String[] args) {
        BigDecimal exact = new BigDecimal("2.675");
        BigDecimal fromDouble = new BigDecimal(2.675);
        BigDecimal viaValueOf = BigDecimal.valueOf(2.675);

        System.out.println("String  = "
                + exact.setScale(2, RoundingMode.HALF_EVEN));
        System.out.println("double  = "
                + fromDouble.setScale(2, RoundingMode.HALF_EVEN));
        System.out.println("valueOf = "
                + viaValueOf.setScale(2, RoundingMode.HALF_EVEN));
    }
}
~~~

~~~bash
javac HalfEvenInputDemo.java
java HalfEvenInputDemo
~~~

Đầu ra:

~~~text
String  = 2.68
double  = 2.67
valueOf = 2.68
~~~

Khi cần kiểm soát chữ số thập phân (đặc biệt các khoản tiền), hãy ưu tiên dữ liệu BigDecimal tạo từ chuỗi thập phân chính xác hoặc nguồn số nguyên được quy đổi rõ ràng, tránh đưa sai số nhị phân vào tie.

## 10. Những lỗi phổ biến

1. **Xem chữ số đầu tiên bị bỏ là 5 thì chắc chắn hòa.** Ví dụ 1.2501 có các chữ số khác 0 sau 5 nên phải chọn số gần hơn, tức 1.3 tại scale 1.
2. **Xét số chẵn trước khi xác định khoảng cách.** 2.51 → 3, dù 3 lẻ; lý do là gần nhất.
3. **Xét chẵn/lẻ của phần nguyên bất kể scale.** 1.25 → 1.2 tại scale 1 vì chữ số hàng phần mười là 2, không phải vì phần nguyên 1.
4. **Tưởng HALF_EVEN luôn làm tròn xuống.** 3.5 → 4, 1.35 → 1.4 và 9.95 → 10.0.
5. **Tưởng số âm luôn làm tròn về 0.** -3.5 → -4, còn -2.5 → -2.
6. **Nhầm HALF_UP với UP, HALF_DOWN với DOWN.** Hai chế độ không có HALF không yêu cầu tie mới làm tròn theo hướng.
7. **Nhầm precision với scale.** MathContext precision đếm chữ số có nghĩa; setScale quy định chữ số phần thập phân.
8. **Dùng `new BigDecimal(double)` cho hằng số decimal.** Giá trị nhị phân có thể khác rất nhỏ nhưng đủ làm kết quả ở ranh giới thay đổi.
9. **Làm tròn ở nhiều bước mà không có chính sách.** Tổng sau làm tròn từng khoản có thể khác làm tròn tổng một lần.
10. **Quên lưu giá trị trả về.** BigDecimal là immutable: `value.setScale(...)` không cập nhật `value`; dùng biến mới hoặc phép gán phù hợp.
11. **Dùng HALF_EVEN cho mọi số tiền theo quán tính.** Cần kiểm tra yêu cầu loại giao dịch, chuẩn báo cáo và quy định hiện hành của hệ thống.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Làm tròn `4.5` và `5.5` về scale 0 theo HALF_EVEN. Vì sao kết quả khác chiều?

**Đáp án:** 4 và 6. Cả hai là tie, nhưng hai cặp lân cận lần lượt là (4, 5) và (5, 6); chọn chữ số chẵn.

**Câu 2.** Làm tròn `8.235` và `8.245` về scale 2 theo HALF_EVEN.

**Đáp án:** Cả hai đều là 8.24: giá trị thứ nhất ở giữa 8.23 và 8.24, giá trị thứ hai ở giữa 8.24 và 8.25; chữ số cuối 4 là chẵn.

**Câu 3.** Vì sao `1.2500` → `1.2` còn `1.2501` → `1.3` ở scale 1?

**Đáp án:** 1.2500 bằng đúng 1.25 nên là tie; 1.2501 gần 1.3 hơn nên quy tắc gần nhất quyết định.

**Câu 4.** Với `new BigDecimal("12.345")`, `setScale(2, HALF_EVEN)` khác `round(new MathContext(2, HALF_EVEN))` ra sao?

**Đáp án:** Kết quả lần lượt là 12.34 và 12; cách thứ nhất giữ hai chữ số lẻ, cách thứ hai giữ hai chữ số có nghĩa.

**Câu 5.** HALF_EVEN có bảo đảm tổng đã làm tròn từng dòng bằng tổng gốc được làm tròn một lần?

**Đáp án:** Không. Ba giá trị 1.005 minh họa kết quả có thể là 3.00 hoặc 3.02 tùy thời điểm áp dụng quy tắc.

## 12. Ghi nhớ

- **HALF_EVEN chọn kết quả gần nhất; nếu hòa, chọn chữ số cuối được giữ lại là chẵn.**
- Chẵn/lẻ **chỉ giải quyết tie**, không thay thế quy tắc khoảng cách.
- Dùng `setScale(scale, RoundingMode.HALF_EVEN)` để kiểm soát chữ số sau dấu thập phân; dùng `MathContext` để kiểm soát chữ số có nghĩa.
- Nhận dữ liệu thập phân chính xác và chọn thời điểm làm tròn theo yêu cầu nghiệp vụ.
- HALF_EVEN có thể giảm xu hướng sai lệch khi xử lý nhiều tie cân bằng, nhưng không bảo đảm mọi kết quả tổng đều giống dữ liệu chưa làm tròn.

Tài liệu API chính thức: Java SE 17 `java.math.RoundingMode`, `java.math.BigDecimal` và `java.math.MathContext`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-008">UNNECESSARY để yêu cầu kết quả không cần làm tròn</a>

<details>
<summary>Xem chi tiết</summary>

# `RoundingMode.UNNECESSARY`: yêu cầu kết quả không cần làm tròn trong Java

## 1. Vì sao cần chế độ “không cho phép làm tròn”?

Khi làm việc với `BigDecimal`, lập trình viên thường chỉ định **scale** (số chữ số sau dấu thập phân) hoặc **precision** (số chữ số có nghĩa). Nếu kết quả chính xác có nhiều chữ số hơn mức được yêu cầu, phép tính cần loại bỏ hoặc thay đổi chữ số. Khi đó, một chế độ như `HALF_UP` hoặc `HALF_EVEN` sẽ quyết định cách làm tròn.

**`RoundingMode.UNNECESSARY` có ý nghĩa khác:** tuyên bố rằng thao tác **phải thực hiện được mà không thay đổi giá trị toán học**. Nếu không thể thỏa ràng buộc scale/precision mà vẫn giữ nguyên giá trị, `BigDecimal` ném `ArithmeticException` thay vì âm thầm làm tròn.

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal exact = new BigDecimal("12.3400");
BigDecimal a = exact.setScale(2, RoundingMode.UNNECESSARY); // 12.34
BigDecimal b = exact.setScale(5, RoundingMode.UNNECESSARY); // 12.34000

// Khi chạy sẽ ném ArithmeticException vì phải bỏ chữ số 5:
BigDecimal c = new BigDecimal("12.345")
        .setScale(2, RoundingMode.UNNECESSARY);
~~~

Các dòng trên là **đoạn mã minh họa**, đặt vào phương thức hoặc chương trình thích hợp. Bản chạy độc lập có ở phần 6.

Điểm cốt lõi: `UNNECESSARY` **không có nghĩa là bỏ qua bước xử lý, không có nghĩa là giữ nguyên chuỗi đầu vào và cũng không tự bảo đảm phép toán luôn thành công**. Nó là một **điều kiện kiểm tra tính chính xác** của phép toán dưới ràng buộc đã yêu cầu. `BigDecimal` vẫn có thể thay đổi cách biểu diễn, thêm hoặc bớt các số 0 tận cùng, miễn giá trị không đổi.

## 2. Nền tảng: giá trị, scale và precision của `BigDecimal`

`BigDecimal` lưu một **unscaled value** là số nguyên, kết hợp với một **scale** nguyên:

~~~text
giá trị = unscaledValue × 10^(-scale)
~~~

Ví dụ:

| `BigDecimal` tạo từ chuỗi | Unscaled value | Scale | Precision |
| --- | ---: | ---: | ---: |
| `"12.34"` | 1234 | 2 | 4 |
| `"12.3400"` | 123400 | 4 | 6 |
| `"0.00120"` | 120 | 5 | 3 |
| `"1200"` | 1200 | 0 | 4 |

**Scale** là vị trí dấu thập phân theo biểu diễn hiện tại; scale có thể âm (ví dụ scale = -2 biểu thị bội số của 100). **Precision** là số chữ số thập phân của unscaled value khi bỏ dấu; nó không đồng nghĩa với số chữ số sau dấu thập phân.

`new BigDecimal("12.3400")` và `new BigDecimal("12.34")` có **giá trị số học bằng nhau**, nhưng scale/precision ban đầu khác nhau. Vì vậy:

- `compareTo()` cho kết quả 0 khi hai `BigDecimal` có cùng giá trị toán học.
- `equals()` còn xét scale nên hai đối tượng trên **không bằng nhau theo `equals`**.
- `setScale(2, UNNECESSARY)` có thể biến `12.3400` thành `12.34` vì chỉ loại bỏ các chữ số 0 không ảnh hưởng giá trị.

Ví dụ `setScale(5, UNNECESSARY)` lại thêm số 0 để tạo `12.34000`, vẫn là một phép chuyển đổi **chính xác**. Do đó “không cần làm tròn” khác với “không được thay đổi cách hiển thị”.

## 3. `setScale(..., RoundingMode.UNNECESSARY)`: kiểm tra biểu diễn chính xác ở scale mong muốn

Phương thức:

~~~java
BigDecimal result = value.setScale(targetScale, RoundingMode.UNNECESSARY);
~~~

yêu cầu biểu diễn giá trị bằng đúng `targetScale`. Có ba tình huống:

1. **Tăng scale:** thêm chữ số 0 ở phần thập phân để giữ nguyên giá trị, nên có thể thực hiện chính xác.
2. **Giảm scale nhưng chỉ bỏ số 0 tận cùng:** vẫn chính xác, thành công.
3. **Giảm scale và phải bỏ chữ số khác 0:** không thể giữ nguyên giá trị; ném `ArithmeticException`.

| Giá trị đầu vào | Yêu cầu | Kết quả |
| --- | --- | --- |
| `12.34` | `setScale(4, UNNECESSARY)` | `12.3400` |
| `12.3400` | `setScale(2, UNNECESSARY)` | `12.34` |
| `12.345` | `setScale(2, UNNECESSARY)` | `ArithmeticException` |
| `1000` | `setScale(-2, UNNECESSARY)` | `1.0E+3` (scale = -2) |
| `1250` | `setScale(-2, UNNECESSARY)` | `ArithmeticException` |

Ở hai hàng cuối, scale = -2 yêu cầu giá trị là bội số chính xác của **100**: `1000` chia hết cho 100, còn `1250` thì không. Dạng chuỗi `1.0E+3` chỉ là cách `BigDecimal.toString()` biểu diễn `1000` có scale -2.

Cùng phép chuyển `12.345` xuống 2 chữ số, các chế độ làm tròn khác có thể tạo `12.35` hoặc `12.34`, tùy quy tắc; `UNNECESSARY` chọn **báo lỗi** để người gọi không nhầm kết quả đã bị sửa.

### 3.1. Ứng dụng: xác minh dữ liệu có đúng số chữ số thập phân được phép

Giả sử một hệ thống chỉ nhận tiền có tối đa hai chữ số sau dấu chấm, nhưng phải bảo toàn giá trị khách hàng nhập:

~~~java
public static BigDecimal requireTwoDecimalPlaces(String input) {
    return new BigDecimal(input)
            .setScale(2, RoundingMode.UNNECESSARY);
}
~~~

Đây là phương thức cần đặt trong một class đã import `java.math.BigDecimal` và `java.math.RoundingMode`.

- `"19"` → `19.00`.
- `"19.2"` → `19.20`.
- `"19.2000"` → `19.20`: các số 0 dư không đổi giá trị.
- `"19.201"` → `ArithmeticException`: không thể biểu diễn chính xác ở scale 2.

Đó là kiểm tra **khả năng biểu diễn chính xác ở scale 2**, không phải kiểm tra **chuỗi gốc có đúng hai chữ số**. Nếu yêu cầu dữ liệu là đúng hai chữ số được nhập, phải xác minh định dạng chuỗi riêng. Ngoài ra, chính sách tiền tệ thực tế có thể còn cần ràng buộc số âm, miền giá trị, currency và các quy tắc nghiệp vụ.

## 4. `MathContext` và precision: làm tròn theo số chữ số có nghĩa

`MathContext` gồm precision và rounding mode. Ví dụ:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

MathContext exactThreeDigits =
        new MathContext(3, RoundingMode.UNNECESSARY);

BigDecimal x = new BigDecimal("123.00").round(exactThreeDigits);
// x = 123: bỏ 2 chữ số 0 tận cùng nhưng không đổi giá trị.

// Ném ArithmeticException: không thể biểu diễn chính xác
// giá trị 123.40 bằng tối đa 3 chữ số có nghĩa.
BigDecimal y = new BigDecimal("123.40").round(exactThreeDigits);
~~~

`MathContext(3, UNNECESSARY)` yêu cầu kết quả vừa có **tối đa 3 chữ số có nghĩa** vừa **không mất giá trị**. Nó không yêu cầu “giữ đúng 3 chữ số sau dấu thập phân”. Chẳng hạn, `123.00` giảm được xuống `123` mà vẫn đúng, còn `123.40` cần bốn chữ số có nghĩa để giữ chính xác giá trị `123.4`.

Một số phép tính như `add`, `subtract`, `multiply`, `divide` có overload nhận `MathContext`. Khi cần thu kết quả về precision đã cho, chúng thực hiện kiểm tra tương tự. Với `UNNECESSARY`, nếu kết quả chính xác cần nhiều chữ số có nghĩa hơn và không thể giảm bằng cách bỏ các số 0 không ảnh hưởng giá trị, thao tác sẽ thất bại.

### 4.1. Precision = 0 là trường hợp đặc biệt

~~~java
MathContext unlimited =
        new MathContext(0, RoundingMode.UNNECESSARY);
~~~

Trong `MathContext`, **precision = 0 nghĩa là không giới hạn độ chính xác**. Khi đó chế độ rounding không tác động đến việc giới hạn số chữ số có nghĩa, vì không có giới hạn nào được đặt ra. Tuy nhiên, điều này **không khiến phép chia có phần thập phân vô hạn tự trở nên biểu diễn được**: `1 / 3` vẫn không có kết quả `BigDecimal` chính xác với hữu hạn chữ số.

`MathContext.UNLIMITED` là hằng có precision 0 và rounding mode `HALF_UP`; trong các phép toán không cần giới hạn precision, rounding mode của nó không tạo ra khác biệt. Đừng hiểu `MathContext.UNLIMITED` là vô hiệu hóa mọi lỗi số học (ví dụ chia cho 0 và phép chia không kết thúc vẫn lỗi).

## 5. Phép chia: nơi `UNNECESSARY` thường tạo lỗi khó hiểu

### 5.1. Phân biệt ba overload của `divide`

| Cách gọi | Ràng buộc kết quả | Điều gì xảy ra nếu không thể chính xác? |
| --- | --- | --- |
| `a.divide(b)` | Kết quả chính xác với biểu diễn thập phân hữu hạn | `ArithmeticException` khi phép chia không kết thúc, ví dụ `1/3` |
| `a.divide(b, scale, UNNECESSARY)` | Kết quả đúng scale đã chỉ định, không làm tròn | `ArithmeticException` nếu chữ số cần bỏ khác 0 |
| `a.divide(b, mathContextWithUNNECESSARY)` | Kết quả đúng giới hạn precision, không làm tròn | `ArithmeticException` nếu phải làm tròn hoặc kết quả không biểu diễn chính xác được ở precision đó |

Một overload khác, `a.divide(b, RoundingMode.UNNECESSARY)`, dùng **scale của `a`** làm scale kết quả, dễ gây bất ngờ. Ví dụ `new BigDecimal("10").divide(new BigDecimal("4"), RoundingMode.UNNECESSARY)` sẽ lỗi vì scale của `10` là 0, dù giá trị chia chính xác `2.5` có thể được lưu bằng `BigDecimal`. Nếu muốn kết quả scale 1, phải gọi overload có tham số scale tường minh.

### 5.2. “Chia chính xác” khác “chia chính xác ở scale/precision được yêu cầu”

~~~java
BigDecimal ten = new BigDecimal("10");
BigDecimal four = new BigDecimal("4");

// Chính xác ở scale 1:
BigDecimal ok = ten.divide(four, 1, RoundingMode.UNNECESSARY);
// ok = 2.5

// Không thể chính xác ở scale 0:
BigDecimal fail = ten.divide(four, 0, RoundingMode.UNNECESSARY);
// ArithmeticException
~~~

`10 ÷ 4 = 2.5` là giá trị **hoàn toàn biểu diễn được bằng số thập phân hữu hạn**, nhưng không phải bằng scale 0. Đây là lỗi do **ràng buộc biểu diễn**, không phải do phép chia “vốn không chính xác”.

Với precision thì tương tự:

~~~java
MathContext p3 = new MathContext(3, RoundingMode.UNNECESSARY);

BigDecimal ok = BigDecimal.ONE.divide(new BigDecimal("8"), p3);
// 0.125 có 3 chữ số có nghĩa: thành công.

BigDecimal fail = BigDecimal.ONE.divide(new BigDecimal("32"), p3);
// 0.03125 có 4 chữ số có nghĩa: ArithmeticException.
~~~

### 5.3. Phép chia có thập phân vô hạn

`1 / 3 = 0.3333...` không thể ghi chính xác bằng một `BigDecimal` có hữu hạn chữ số thập phân:

~~~java
BigDecimal a = BigDecimal.ONE;
BigDecimal b = new BigDecimal("3");

// Cả hai đều không thể trả về BigDecimal chính xác:
a.divide(b);
a.divide(b, new MathContext(20, RoundingMode.UNNECESSARY));
~~~

Trong hai dòng trên, lỗi phát sinh tại phép chia được thực thi đầu tiên; để kiểm tra dòng thứ hai riêng, đặt các dòng trong những khối `try/catch` độc lập.

Một phân số tối giản `n/d` có biểu diễn thập phân hữu hạn khi **mẫu d chỉ có các thừa số nguyên tố 2 và 5**. Ví dụ `1/8 = 0.125`, `1/20 = 0.05` hữu hạn; `1/3`, `1/6` không hữu hạn. Tiêu chí này áp dụng sau khi rút gọn phân số, với mẫu là số nguyên dương.

Nếu yêu cầu nghiệp vụ **cho phép làm tròn**, hãy chọn một scale hoặc precision và chế độ làm tròn có chủ đích, ví dụ:

~~~java
BigDecimal oneThird = BigDecimal.ONE.divide(
        new BigDecimal("3"), 2, RoundingMode.HALF_UP);
// oneThird = 0.33, là giá trị xấp xỉ.
~~~

Nếu nghiệp vụ **cấm làm tròn**, giữ `UNNECESSARY` và xử lý lỗi như tín hiệu dữ liệu hoặc kỳ vọng phép toán chưa đáp ứng điều kiện, thay vì tự đổi sang `HALF_UP`.

## 6. Chương trình Java 17+ có thể biên dịch và chạy

Tạo tệp **UnnecessaryDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class UnnecessaryDemo {
    private static void attempt(String label, Runnable calculation) {
        try {
            calculation.run();
        } catch (ArithmeticException ex) {
            System.out.println(label + " -> ArithmeticException");
        }
    }

    public static void main(String[] args) {
        BigDecimal amount = new BigDecimal("12.3400");

        System.out.println("scale down exact: "
                + amount.setScale(2, RoundingMode.UNNECESSARY));
        System.out.println("scale up exact: "
                + amount.setScale(5, RoundingMode.UNNECESSARY));

        attempt("scale down inexact", () ->
                System.out.println(new BigDecimal("12.345")
                        .setScale(2, RoundingMode.UNNECESSARY)));

        System.out.println("divide exact scale: "
                + new BigDecimal("10").divide(
                        new BigDecimal("4"), 1, RoundingMode.UNNECESSARY));

        attempt("divide inexact scale", () ->
                System.out.println(new BigDecimal("10").divide(
                        new BigDecimal("4"), 0, RoundingMode.UNNECESSARY)));

        MathContext precision3 =
                new MathContext(3, RoundingMode.UNNECESSARY);
        System.out.println("precision exact: "
                + new BigDecimal("123.00").round(precision3));

        attempt("precision inexact", () ->
                System.out.println(new BigDecimal("123.40")
                        .round(precision3)));

        System.out.println("exact division: "
                + BigDecimal.ONE.divide(new BigDecimal("8")));

        attempt("nonterminating division", () ->
                System.out.println(BigDecimal.ONE.divide(
                        new BigDecimal("3"), precision3)));

        System.out.println("exact precision division: "
                + BigDecimal.ONE.divide(new BigDecimal("8"), precision3));

        attempt("precision division", () ->
                System.out.println(BigDecimal.ONE.divide(
                        new BigDecimal("32"), precision3)));

        MathContext unlimited =
                new MathContext(0, RoundingMode.UNNECESSARY);
        System.out.println("unlimited division: "
                + BigDecimal.ONE.divide(new BigDecimal("32"), unlimited));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac UnnecessaryDemo.java
java UnnecessaryDemo
~~~

Đầu ra:

~~~text
scale down exact: 12.34
scale up exact: 12.34000
scale down inexact -> ArithmeticException
divide exact scale: 2.5
divide inexact scale -> ArithmeticException
precision exact: 123
precision inexact -> ArithmeticException
exact division: 0.125
nonterminating division -> ArithmeticException
exact precision division: 0.125
precision division -> ArithmeticException
unlimited division: 0.03125
~~~

Trong chương trình, `attempt` bắt `ArithmeticException` nhằm cho phép các trường hợp khác tiếp tục chạy sau một phép tính cố ý sai ràng buộc. Ứng dụng thực tế nên có chính sách xử lý lỗi phù hợp, tránh che lỗi hoặc trả một giá trị thay thế tùy tiện.

## 7. Dùng `UNNECESSARY` ở đâu?

### 7.1. Kiểm tra dữ liệu trước khi lưu

Nếu cơ sở dữ liệu sử dụng `DECIMAL(12, 2)` và nghiệp vụ yêu cầu không làm mất phần lẻ, có thể dùng:

~~~java
BigDecimal normalized =
        input.setScale(2, RoundingMode.UNNECESSARY);
~~~

Sau đó **vẫn phải kiểm tra giới hạn độ lớn/số chữ số nguyên** để phù hợp precision của cột và các quy tắc nghiệp vụ khác. `setScale(2, UNNECESSARY)` chỉ chứng minh rằng giá trị có thể được biểu diễn đúng ở scale 2; nó không chứng minh giá trị vừa vào mọi cột hoặc nằm trong miền hợp lệ.

### 7.2. Phân phối số lượng nguyên

Nếu chia một tổng đơn vị nguyên thành các phần và bắt buộc không còn phần dư, có thể dùng `divide(..., 0, UNNECESSARY)` để yêu cầu thương nguyên chính xác. Ví dụ `10 / 2` thành công ở scale 0, còn `10 / 4` báo lỗi vì thương 2.5 không nguyên. Nếu muốn kiểm tra tính chia hết của **số nguyên**, `BigInteger.divideAndRemainder` hoặc phép chia và phần dư nguyên có thể diễn đạt ý định rõ ràng hơn.

### 7.3. Kiểm tra bất biến trong tính toán

Trong một chuỗi phép tính mà phần nghiệp vụ xác nhận **không được mất chữ số**, `UNNECESSARY` biến giả định “chắc chắn chia/chuẩn hóa chính xác được” thành điều kiện được kiểm tra ở runtime. Điều này đặc biệt hữu ích ở **ranh giới dữ liệu**: trước khi lưu, gửi API hoặc chuyển sang scale chuẩn.

Tuy nhiên, `UNNECESSARY` không loại bỏ sai số đã phát sinh từ nguồn dữ liệu trước đó. Ví dụ, `new BigDecimal(0.1)` mang chính xác giá trị nhị phân của literal `double` 0.1 sau khi đã làm tròn theo IEEE 754, không mang chính xác số thập phân 0.1 kỳ vọng. Khi cần dữ liệu thập phân đúng theo văn bản, ưu tiên `new BigDecimal("0.1")` hoặc một nguồn thập phân có kiểm soát. Nếu thật sự bắt đầu bằng `double`, `BigDecimal.valueOf(0.1)` dùng biểu diễn thập phân chuẩn của giá trị double và thường phù hợp hơn constructor nhận double, nhưng không phục hồi số thập phân gốc chưa biết.

## 8. Các ngoại lệ và hiểu lầm cần tránh

| Hiểu lầm | Điều đúng |
| --- | --- |
| `UNNECESSARY` nghĩa là “đừng làm gì” | API vẫn đổi scale/precision khi đổi được mà không mất giá trị |
| Có `UNNECESSARY` thì kết quả phải thành công | Nó ném `ArithmeticException` nếu cần làm tròn |
| `setScale(2, UNNECESSARY)` bắt chuỗi nhập phải có đúng 2 chữ số sau dấu chấm | Nó kiểm tra **giá trị** có thể biểu diễn chính xác ở scale 2 |
| Precision = 2 tương đương scale = 2 | Precision đếm chữ số có nghĩa; scale chỉ vị trí dấu thập phân |
| Mọi phép chia hữu hạn đều thành công ở bất kỳ scale nào | `10/4` hữu hạn nhưng không biểu diễn chính xác ở scale 0 |
| `MathContext(0, UNNECESSARY)` khiến `1/3` biểu diễn chính xác | Precision không giới hạn vẫn không tạo được một số thập phân hữu hạn bằng `1/3` |
| `BigDecimal.divide(BigDecimal, UNNECESSARY)` không đặt scale | Overload này dùng **scale của số bị chia** |
| `UNNECESSARY` tự kiểm tra miền giá trị, định dạng hoặc tính hợp lệ nghiệp vụ | Nó chỉ kiểm tra **nhu cầu làm tròn** trong thao tác đang gọi |

Lưu ý thêm: **không phải mọi `ArithmeticException` đều do `UNNECESSARY`**. Chia cho số 0 cũng ném `ArithmeticException`; vì vậy khi ghi log và phản hồi lỗi nghiệp vụ cần phân biệt dữ liệu đầu vào, phép toán được gọi và ràng buộc mong muốn, thay vì mặc định mọi lỗi đều mang nghĩa “cần làm tròn”.

## 9. Tự kiểm tra kiến thức

1. `new BigDecimal("5.000").setScale(0, UNNECESSARY)` có thành công không? **Có**, kết quả `5` vì chỉ bỏ số 0 dư.
2. `new BigDecimal("5.001").setScale(2, UNNECESSARY)` có thành công không? **Không**, vì `5.001` không thể giữ nguyên khi chỉ còn 2 chữ số sau dấu chấm.
3. `new BigDecimal("1").divide(new BigDecimal("8"), 2, UNNECESSARY)` thế nào? **Ném `ArithmeticException`**, vì `0.125` không thể chính xác ở scale 2.
4. Cùng phép chia đó với `scale = 3` thì sao? **Thành công**, kết quả `0.125`.
5. `new BigDecimal("123.00").round(new MathContext(3, UNNECESSARY))` thế nào? **Thành công**, kết quả `123` vì có thể bỏ các số 0 dư mà vẫn bảo toàn giá trị.
6. Khi nào nên dùng `HALF_EVEN` hoặc `HALF_UP`? **Khi nghiệp vụ chủ động cho phép làm tròn** và đã chỉ rõ quy tắc. Khi yêu cầu tuyệt đối không đổi giá trị, dùng `UNNECESSARY` để phát hiện vi phạm.

**Ghi nhớ:** `RoundingMode.UNNECESSARY` là cách diễn đạt yêu cầu **“kết quả ở scale/precision này phải chính xác, nếu không hãy báo lỗi”**. Muốn dùng đúng, trước tiên xác định mình đang giới hạn **scale** hay **precision**, sau đó dự kiến và xử lý `ArithmeticException` khi ràng buộc không thể được đáp ứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-009">Đối chiếu các chế độ với cả số dương và số âm</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu các chế độ làm tròn với số dương và số âm trong Java

## 1. Mục tiêu và tình huống

Khi làm tròn số trong Java, lựa chọn chế độ làm tròn (`RoundingMode`) phải dựa trên **hướng làm tròn và quy tắc xử lý trường hợp nằm đúng giữa hai giá trị**, đặc biệt khi đầu vào có thể âm. Tên gọi như `UP`, `DOWN`, `CEILING` và `FLOOR` rất dễ bị hiểu sai nếu chỉ thử với số dương.

Sau bài này, bạn có thể:

- Giải thích chính xác tám giá trị của `java.math.RoundingMode`.
- Dự đoán kết quả làm tròn số **dương và âm**, tại điểm giữa (tie) lẫn không phải điểm giữa.
- Phân biệt `UP/DOWN` với `CEILING/FLOOR`, và ba quy tắc `HALF_UP`, `HALF_DOWN`, `HALF_EVEN`.
- Dùng `BigDecimal.setScale(...)`, `divide(...)` và `MathContext` đúng mục đích; phát hiện lúc nào làm tròn sẽ gây mất thông tin.
- Chọn chính sách làm tròn nhất quán cho tiền tệ, số đo, báo cáo và các phép tính theo yêu cầu nghiệp vụ.

Điều kiện quan trọng: **rounding chỉ xảy ra khi phải bỏ phần có nghĩa**. Nếu kết quả đã biểu diễn chính xác ở scale/precision được yêu cầu, mọi chế độ đều cho cùng giá trị; `UNNECESSARY` cũng hợp lệ.

## 2. Hai cách nhìn về hướng làm tròn

Xét việc làm tròn về số nguyên (`scale = 0`):

- **Tiến ra xa 0** (`UP`): `2.1 → 3` và `-2.1 → -3`. Độ lớn tuyệt đối tăng nếu có phần lẻ.
- **Tiến về 0** (`DOWN`): `2.9 → 2` và `-2.9 → -2`. Đây là phép *truncate* ở vị trí làm tròn.
- **Tiến về +∞** (`CEILING`): `2.1 → 3` nhưng `-2.1 → -2`. Số âm ít âm hơn là số **lớn hơn**.
- **Tiến về −∞** (`FLOOR`): `2.9 → 2` nhưng `-2.1 → -3`. Số âm âm hơn là số **nhỏ hơn**.

Do đó `UP` **không đồng nghĩa** với `CEILING`; `DOWN` **không đồng nghĩa** với `FLOOR`. Chúng trùng nhau ở một số đầu vào, nhưng khác nhau khi đảo dấu.

Với các chế độ bắt đầu bằng `HALF_`, Java ưu tiên kết quả **gần số gốc nhất**. Nếu một trong hai ứng viên gần hơn, cả `HALF_UP`, `HALF_DOWN` và `HALF_EVEN` đều chọn ứng viên đó. Sự khác biệt chỉ xuất hiện tại **điểm giữa chính xác**, chẳng hạn `2.5` nằm giữa `2` và `3` khi làm tròn đến số nguyên.

Hãy nhớ đơn vị vị trí làm tròn:

- `setScale(0, mode)`: làm tròn đến **hàng đơn vị**.
- `setScale(2, mode)`: làm tròn đến **2 chữ số sau dấu thập phân**, đơn vị bước là `0.01`.
- `setScale(-1, mode)`: làm tròn đến **hàng chục**, đơn vị bước là `10`. Scale âm hợp lệ.
- `new MathContext(4, mode)`: giới hạn **4 chữ số có nghĩa**, không phải 4 chữ số sau dấu thập phân.

## 3. Bảng đối chiếu đủ tám chế độ với số dương và âm

Bảng dưới đây xét `new BigDecimal(input).setScale(0, mode)`. Các đầu vào `±2.5` là **điểm giữa**; `±2.4` **không** phải điểm giữa.

| `RoundingMode` | `+2.5` | `-2.5` | `+2.4` | `-2.4` | Quy tắc |
| --- | ---: | ---: | ---: | ---: | --- |
| `UP` | `3` | `-3` | `3` | `-3` | Xa 0, hễ có chữ số phải bỏ |
| `DOWN` | `2` | `-2` | `2` | `-2` | Về 0, bỏ phần lẻ |
| `CEILING` | `3` | `-2` | `3` | `-2` | Về +∞ |
| `FLOOR` | `2` | `-3` | `2` | `-3` | Về −∞ |
| `HALF_UP` | `3` | `-3` | `2` | `-2` | Gần nhất; điểm giữa thì xa 0 |
| `HALF_DOWN` | `2` | `-2` | `2` | `-2` | Gần nhất; điểm giữa thì về 0 |
| `HALF_EVEN` | `2` | `-2` | `2` | `-2` | Gần nhất; điểm giữa thì chữ số giữ lại là chẵn |
| `UNNECESSARY` | lỗi | lỗi | lỗi | lỗi | Cấm làm tròn mất giá trị |

Trong bảng, “lỗi” nghĩa là `ArithmeticException` vì yêu cầu bỏ phần lẻ không bằng 0 nhưng chế độ `UNNECESSARY` cấm việc đó.

Cách đọc nhanh hai hàng dễ nhầm:

- `CEILING(-2.4) = -2` vì `-2` lớn hơn `-2.4`; `FLOOR(-2.4) = -3` vì `-3` nhỏ hơn `-2.4`.
- `UP(-2.4) = -3` vì `-3` xa 0 hơn; `DOWN(-2.4) = -2` vì `-2` gần 0 hơn.

Các chế độ `HALF_` cho cùng kết quả với `±2.4` vì các giá trị này gần `±2` hơn `±3`.

### Bảng bổ sung để phân biệt ba chế độ `HALF_`

Với `setScale(0, mode)`:

| Đầu vào | `HALF_UP` | `HALF_DOWN` | `HALF_EVEN` |
| --- | ---: | ---: | ---: |
| `2.5` | `3` | `2` | `2` |
| `-2.5` | `-3` | `-2` | `-2` |
| `3.5` | `4` | `3` | `4` |
| `-3.5` | `-4` | `-3` | `-4` |
| `4.5` | `5` | `4` | `4` |
| `-4.5` | `-5` | `-4` | `-4` |

`HALF_EVEN` chọn **ứng viên có chữ số cuối được giữ lại là chẵn** tại điểm giữa: `2.5 → 2` nhưng `3.5 → 4`; `-2.5 → -2` nhưng `-3.5 → -4`. “Even” xét **chữ số tại vị trí làm tròn**, không xét dấu của số gốc. Đây còn được gọi là *banker's rounding* và có thể giảm thiên lệch do liên tiếp gặp điểm giữa trong một số tập dữ liệu; điều đó không có nghĩa nó luôn phù hợp với mọi nghiệp vụ.

## 4. Từng chế độ hoạt động ra sao?

### `UP` — xa số 0

Khi kết quả không biểu diễn chính xác ở vị trí yêu cầu, `UP` làm tăng **độ lớn tuyệt đối**:

- `3.01 → 4`, `-3.01 → -4` khi `scale = 0`.
- `3.00 → 3`, `-3.00 → -3` vì phần bị bỏ chỉ gồm số 0.

Dùng khi yêu cầu làm tròn số lượng hoặc đơn vị theo hướng luôn đủ về mặt độ lớn, nhưng cần kiểm tra xem chính sách thực tế có phù hợp với số âm hay không.

### `DOWN` — về số 0

`DOWN` loại bỏ phần chữ số vượt quá độ chính xác yêu cầu:

- `3.99 → 3`, `-3.99 → -3` khi `scale = 0`.
- Không phải luôn là “làm tròn xuống dưới” theo trục số: với số âm, `-3` **lớn hơn** `-3.99`.

### `CEILING` — về dương vô cực

- `3.01 → 4`, `-3.01 → -3`.
- `CEILING` không có nghĩa “tăng chữ số” của mọi số; nó nghĩa **chọn ứng viên lớn hơn hoặc bằng đầu vào**.

### `FLOOR` — về âm vô cực

- `3.99 → 3`, `-3.01 → -4`.
- `FLOOR` không có nghĩa “giảm độ lớn tuyệt đối”; với số âm, kết quả có thể xa 0 hơn.

### `HALF_UP` — gần nhất, tie xa 0

- `2.49 → 2`, `2.50 → 3`; `-2.49 → -2`, `-2.50 → -3`.
- Đây là quy tắc thường gặp khi một đặc tả ghi “5 thì làm tròn lên”, nhưng **phải xác minh** quy tắc áp dụng cho số âm: `HALF_UP` làm tròn `-2.5` thành `-3`.

### `HALF_DOWN` — gần nhất, tie về 0

- `2.49 → 2`, `2.50 → 2`, `2.51 → 3`.
- `-2.49 → -2`, `-2.50 → -2`, `-2.51 → -3`.

`HALF_DOWN` không có nghĩa luôn về 0: khi khoảng cách lệch khỏi điểm giữa, nó vẫn chọn kết quả gần hơn.

### `HALF_EVEN` — gần nhất, tie về chữ số chẵn

- `2.5 → 2`, `3.5 → 4`, `-2.5 → -2`, `-3.5 → -4`.
- Với hai chữ số sau dấu thập phân: `1.245 → 1.24` vì chữ số hàng phần trăm **4** chẵn; `1.255 → 1.26` vì ứng viên có chữ số hàng phần trăm **6** chẵn.

Chính xác **nửa đơn vị** ở vị trí làm tròn mới là tie. Chẳng hạn `1.2451` làm tròn đến scale 2 sẽ là `1.25` với cả ba chế độ `HALF_` vì nó **lớn hơn** điểm giữa `1.245`, dù sau chữ số `5` còn chữ số khác 0.

### `UNNECESSARY` — bắt buộc không mất thông tin

- `new BigDecimal("2.00").setScale(0, UNNECESSARY)` thành `2`: bỏ các chữ số 0, **không** mất giá trị.
- `new BigDecimal("2.01").setScale(0, UNNECESSARY)` ném `ArithmeticException`.
- Dùng để thực thi bất biến: nếu giá trị không thể biểu diễn chính xác theo định dạng yêu cầu thì coi là lỗi đầu vào, không âm thầm làm tròn.

## 5. Chương trình Java 17+ chạy độc lập

Tạo tệp `SignedRoundingModesDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class SignedRoundingModesDemo {
    private static String round(String raw, int scale, RoundingMode mode) {
        try {
            return new BigDecimal(raw)
                    .setScale(scale, mode)
                    .toPlainString();
        } catch (ArithmeticException ex) {
            // "ERROR" biểu diễn trường hợp UNNECESSARY không cho phép làm tròn.
            return "ERROR";
        }
    }

    public static void main(String[] args) {
        String[] values = {"2.5", "-2.5", "2.4", "-2.4"};
        System.out.println("mode ` +2.5 ` -2.5 ` +2.4 ` -2.4");

        for (RoundingMode mode : RoundingMode.values()) {
            System.out.printf("%s ` %s ` %s ` %s ` %s%n",
                    mode,
                    round(values[0], 0, mode),
                    round(values[1], 0, mode),
                    round(values[2], 0, mode),
                    round(values[3], 0, mode));
        }

        System.out.println("HALF_EVEN (+3.5,-3.5): "
                + round("3.5", 0, RoundingMode.HALF_EVEN)
                + "; " + round("-3.5", 0, RoundingMode.HALF_EVEN));

        System.out.println("HALF_UP (+1.235,-1.235): "
                + round("1.235", 2, RoundingMode.HALF_UP)
                + "; " + round("-1.235", 2, RoundingMode.HALF_UP));

        System.out.println("HALF_EVEN (1.245,1.255): "
                + round("1.245", 2, RoundingMode.HALF_EVEN)
                + "; " + round("1.255", 2, RoundingMode.HALF_EVEN));

        System.out.println("CEILING/FLOOR (-1.231): "
                + round("-1.231", 2, RoundingMode.CEILING)
                + "; " + round("-1.231", 2, RoundingMode.FLOOR));

        System.out.println("HALF_EVEN tens (145,155): "
                + round("145", -1, RoundingMode.HALF_EVEN)
                + "; " + round("155", -1, RoundingMode.HALF_EVEN));

        System.out.println("UNNECESSARY exact: "
                + round("2.00", 0, RoundingMode.UNNECESSARY));
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~bash
javac SignedRoundingModesDemo.java
java SignedRoundingModesDemo
~~~

Stdout:

~~~text
mode ` +2.5 ` -2.5 ` +2.4 ` -2.4
UP ` 3 ` -3 ` 3 ` -3
DOWN ` 2 ` -2 ` 2 ` -2
CEILING ` 3 ` -2 ` 3 ` -2
FLOOR ` 2 ` -3 ` 2 ` -3
HALF_UP ` 3 ` -3 ` 2 ` -2
HALF_DOWN ` 2 ` -2 ` 2 ` -2
HALF_EVEN ` 2 ` -2 ` 2 ` -2
UNNECESSARY ` ERROR ` ERROR ` ERROR ` ERROR
HALF_EVEN (+3.5,-3.5): 4; -4
HALF_UP (+1.235,-1.235): 1.24; -1.24
HALF_EVEN (1.245,1.255): 1.24; 1.26
CEILING/FLOOR (-1.231): -1.23; -1.24
HALF_EVEN tens (145,155): 140; 160
UNNECESSARY exact: 2
~~~

Chương trình dùng các **chuỗi thập phân chính xác** và `BigDecimal.setScale`, tránh nhiễu từ biểu diễn nhị phân của `double`. Nó chủ động bắt `ArithmeticException` để minh họa `UNNECESSARY`; trong code nghiệp vụ, thường nên để lỗi này được xử lý theo chính sách validation, thay vì chỉ ghi “ERROR” chung chung.

Các phép tính kiểm chứng bằng Java: `HALF_EVEN` với `3.5/-3.5` cho `4/-4`, với `1.245/1.255` ở scale 2 cho `1.24/1.26`, với `145/155` ở scale -1 cho `140/160`. Số âm `-1.231` được làm tròn ở scale 2 thành `-1.23` theo `CEILING` và `-1.24` theo `FLOOR`.

## 6. Bên dưới `setScale`: scale, precision, tie và dấu

### Scale không đồng nhất với precision

Với `BigDecimal`, **scale** điều khiển số chữ số phía sau dấu thập phân theo biểu diễn `unscaledValue × 10^(-scale)`. **Precision** là tổng chữ số có nghĩa của biểu diễn.

Ví dụ:

~~~java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

BigDecimal x = new BigDecimal("1234.567");

System.out.println(
        x.setScale(4, RoundingMode.HALF_UP).toPlainString()
); // 1234.5670: bốn chữ số SAU dấu thập phân

System.out.println(
        x.round(new MathContext(4, RoundingMode.HALF_UP)).toPlainString()
); // 1235: bốn chữ số CÓ NGHĨA
~~~

Đây là **đoạn minh họa các biểu thức Java**, có thể chạy trong `jshell` hoặc đặt vào phương thức `main`; không phải một tệp Java độc lập ở dạng trình bày này.

Khi `MathContext` yêu cầu `precision = 0`, ý nghĩa là không giới hạn precision, nên thao tác thông thường không buộc phải làm tròn vì giới hạn precision. `MathContext.UNLIMITED` cũng quy định `RoundingMode.HALF_UP` nhưng do precision không giới hạn, thông thường không dùng quy tắc đó để cắt chữ số.

### Tie tính theo vị trí cần giữ, không theo riêng chữ số 5

Làm tròn `scale = 2` có bước `0.01`, vì vậy:

| Dữ liệu | Hai ứng viên gần nhau | Kết quả `HALF_EVEN` |
| --- | --- | --- |
| `1.245` | `1.24` và `1.25` | `1.24` (4 chẵn) |
| `1.255` | `1.25` và `1.26` | `1.26` (6 chẵn) |
| `-1.245` | `-1.24` và `-1.25` | `-1.24` (4 chẵn) |
| `-1.255` | `-1.25` và `-1.26` | `-1.26` (6 chẵn) |
| `1.2451` | `1.24` và `1.25` | `1.25` (gần hơn) |

Không được áp dụng quy tắc `HALF_EVEN` kiểu “gặp chữ số 5 thì làm tròn về chẵn” một cách máy móc khi sau chữ số 5 vẫn có số khác 0. Chỉ khi phần bị bỏ **đúng bằng một nửa** đơn vị làm tròn mới xét tính chẵn/lẻ.

### Scale âm cũng có thể dùng để làm tròn

Muốn làm tròn về hàng chục, có thể gọi `setScale(-1, mode)`:

~~~java
BigDecimal first = new BigDecimal("145");
BigDecimal second = new BigDecimal("155");

System.out.println(
        first.setScale(-1, RoundingMode.HALF_EVEN).toPlainString()
); // 140
System.out.println(
        second.setScale(-1, RoundingMode.HALF_EVEN).toPlainString()
); // 160
~~~

Cả hai đều nằm đúng giữa hai bội số liên tiếp của 10. Với `145`, hàng chục `4` là chẵn, nên chọn `140`; với `155`, hàng chục `6` là chẵn, nên chọn `160`. `toPlainString()` dùng để in `140` hoặc `160` thay vì dạng có số mũ mà `toString()` có thể tạo khi scale âm.

## 7. Khi phép toán tự nó cần chế độ làm tròn

### Phép chia có thể không có khai triển thập phân hữu hạn

Không phải phép chia nào cũng biểu diễn được dưới dạng `BigDecimal` hữu hạn. Ví dụ `1 / 3` có khai triển vô hạn. Phép `divide(BigDecimal)` không kèm độ chính xác/chế độ làm tròn có thể ném `ArithmeticException` khi kết quả thập phân không kết thúc.

Hãy khai báo rõ độ chính xác của kết quả:

~~~java
BigDecimal one = new BigDecimal("1");
BigDecimal eight = new BigDecimal("8");

System.out.println(
        one.divide(eight, 2, RoundingMode.HALF_UP)
); // 0.13

System.out.println(
        one.negate().divide(eight, 2, RoundingMode.HALF_UP)
); // -0.13
~~~

Hai ví dụ làm tròn `±0.125` về scale 2, điểm giữa `±0.12` và `±0.13`. Chế độ `HALF_UP` chọn `±0.13` vì xa 0 hơn tại tie.

Lưu ý: `divide(BigDecimal divisor, int scale, RoundingMode mode)` làm tròn **theo scale**. Một overload khác là `divide(BigDecimal divisor, MathContext mc)`, làm tròn **theo precision** được khai báo trong `MathContext`. Với `divide` và `UNNECESSARY`, phép chia phải chính xác trong độ chính xác/scale cho phép, nếu không sẽ ném ngoại lệ.

### Tích lũy phép toán: làm tròn ở đâu?

Thực hiện một chuỗi tính toán có làm tròn **sau mỗi bước** có thể khác làm tròn **một lần ở bước cuối**. Ví dụ cụ thể:

- Làm tròn từng giá trị `0.125` về 2 chữ số bằng `HALF_UP` được `0.13`; ba giá trị đã làm tròn cộng lại là `0.39`.
- Cộng ba giá trị gốc trước: `0.125 + 0.125 + 0.125 = 0.375`; rồi làm tròn một lần về 2 chữ số bằng `HALF_UP` được `0.38`.

Không có cách nào luôn đúng cho mọi nghiệp vụ. Nếu yêu cầu là làm tròn **từng dòng hóa đơn**, kết quả có thể khác cách tính tổng chưa làm tròn rồi làm tròn khi kết toán. Quy tắc phải được xác định cùng với thứ tự phép toán; không được thay đổi tùy tiện khi tối ưu code.

## 8. Số âm và hệ sinh thái Java: những nhầm lẫn nguy hiểm

### `Math.round` không phải `BigDecimal` + `HALF_UP`

`Math.round(double)` là API khác với quy tắc ở `RoundingMode.HALF_UP`. Với giá trị chính xác có thể biểu diễn như `±2.5`:

~~~java
System.out.println(Math.round(2.5));  // 3
System.out.println(Math.round(-2.5)); // -2

System.out.println(
        new BigDecimal("-2.5").setScale(0, RoundingMode.HALF_UP)
); // -3
~~~

`Math.round(-2.5)` chọn `-2`, trong khi `HALF_UP` chọn `-3`. Khi chuyển đổi code từ `Math.round` sang `BigDecimal`, cần đối chiếu quy tắc tại số âm và tie, không chỉ kiểm tra số dương.

### Không tạo `BigDecimal` từ literal `double` nếu muốn thập phân chính xác

~~~java
BigDecimal exact = new BigDecimal("2.675");
BigDecimal fromDouble = new BigDecimal(2.675);

System.out.println(exact.setScale(2, RoundingMode.HALF_EVEN)); // 2.68
~~~

`2.675` không có biểu diễn nhị phân hữu hạn bằng `double`, nên `fromDouble` mang giá trị nhị phân đã được xấp xỉ rồi chuyển sang thập phân; khi làm tròn ở vị trí tie, kết quả có thể khác số thập phân bạn tưởng đã nhập. Dùng constructor từ `String` hoặc xử lý theo hợp đồng đầu vào; khi nhận sẵn `double` và muốn biểu diễn thập phân dựa trên `Double.toString`, cân nhắc `BigDecimal.valueOf(double)`.

### Dấu âm và số 0

`BigDecimal` không giữ một giá trị “âm 0” tách biệt theo nghĩa so sánh số học. Vì thế, làm tròn số âm rất nhỏ **về 0** có thể hiển thị `0`, không phải `-0`:

~~~java
System.out.println(
        new BigDecimal("-0.1").setScale(0, RoundingMode.CEILING)
); // 0

System.out.println(
        new BigDecimal("-0.1").setScale(0, RoundingMode.FLOOR)
); // -1
~~~

Không suy đoán dấu ban đầu từ kết quả đã được làm tròn về 0. Nếu nghiệp vụ cần phân biệt hướng thay đổi, lưu thông tin đó riêng.

## 9. Chọn chế độ theo yêu cầu nghiệp vụ

| Yêu cầu cụ thể | Cách triển khai phù hợp | Cần xác nhận thêm |
| --- | --- | --- |
| Làm tròn đối xứng gần nhất, điểm giữa xa 0 | `HALF_UP` | Quy định đối với giá trị âm và vị trí làm tròn |
| Làm tròn gần nhất, điểm giữa về 0 | `HALF_DOWN` | Quy tắc này có thực sự được hợp đồng yêu cầu không |
| Làm tròn gần nhất, điểm giữa về chữ số chẵn | `HALF_EVEN` | Có được phép áp dụng khi tính tổng/từng dòng không |
| Luôn chọn mức cao hơn trên trục số | `CEILING` | Phải áp dụng đúng cả với số âm |
| Luôn chọn mức thấp hơn trên trục số | `FLOOR` | Phải áp dụng đúng cả với số âm |
| Luôn tăng độ lớn tuyệt đối khi có phần bị bỏ | `UP` | Có ý định áp dụng đối xứng cho số âm không |
| Loại bỏ phần vượt quá vị trí làm tròn về phía 0 | `DOWN` | Có chấp nhận bỏ thông tin không |
| Dữ liệu bắt buộc chính xác ở scale cho trước | `UNNECESSARY` | Lỗi phải báo hay từ chối ở tầng nào |

Với tiền tệ và dữ liệu kế toán, **không tự áp đặt** `HALF_UP` hoặc `HALF_EVEN` chỉ vì nó phổ biến. Một quốc gia, chuẩn giao dịch, thuế, ngân hàng hoặc hệ thống có thể yêu cầu chính sách riêng cho từng phép tính. Quy định tối thiểu nên nêu rõ: vị trí làm tròn, mode, thời điểm làm tròn, cách cộng tổng và xử lý giá trị âm/hoàn tiền.

Với thống kê hoặc mô phỏng, `HALF_EVEN` có thể giảm độ lệch hướng của các trường hợp tie nếu các điều kiện dữ liệu phù hợp, nhưng nó **không sửa** mọi sai lệch do lấy mẫu, độ chính xác đầu vào hay thứ tự phép tính.

Với đầu vào buộc phải có hai chữ số thập phân chính xác, ví dụ thay vì âm thầm làm tròn `12.345`, có thể kiểm tra:

~~~java
BigDecimal submitted = new BigDecimal("12.345");
BigDecimal checked = submitted.setScale(2, RoundingMode.UNNECESSARY);
// ArithmeticException: sẽ phải làm tròn, nên giá trị không hợp lệ theo quy tắc này.
~~~

## 10. Lỗi thường gặp và checklist khi tự kiểm tra

1. **Nhầm `UP` thành “về +∞”.** `-2.4` theo `UP` ra `-3`, theo `CEILING` ra `-2`.
2. **Nhầm `DOWN` thành “về −∞”.** `-2.4` theo `DOWN` ra `-2`, theo `FLOOR` ra `-3`.
3. **Cho rằng mọi `HALF_` khác nhau trên mọi dữ liệu.** Với `±2.4`, cả ba đều ra `±2`; chúng chỉ khác nhau ở tie.
4. **Diễn giải `HALF_EVEN` là “nếu số gốc chẵn thì giữ nguyên”.** Nó xét chữ số giữ lại ở **vị trí đang làm tròn**: `3.5 → 4` là hợp lệ.
5. **Giả định `UNNECESSARY` luôn báo lỗi khi đổi scale.** Đổi `2.00` thành `2` là chính xác, nên không lỗi.
6. **Nhầm scale và precision.** `setScale(2, mode)` và `MathContext(2, mode)` quy định hai thứ khác nhau.
7. **Quên số âm trong kiểm thử.** Bộ kiểm thử tối thiểu cho mỗi mode nên có `+2.5`, `-2.5`, `+2.4`, `-2.4` và `±3.5`, đồng thời kiểm tra cả trường hợp không cần làm tròn.
8. **Sử dụng `double` khiến tie không còn chính xác.** Tạo `BigDecimal` từ chuỗi thập phân nếu đó là dữ liệu gốc.

### Bài tập tự kiểm tra

Không chạy chương trình ngay, hãy dự đoán kết quả trước:

1. `new BigDecimal("-7.1").setScale(0, FLOOR)` so với `...CEILING`?
2. `new BigDecimal("-7.5").setScale(0, HALF_UP)` so với `...HALF_DOWN`?
3. `new BigDecimal("6.5").setScale(0, HALF_EVEN)` và `new BigDecimal("7.5").setScale(0, HALF_EVEN)`?
4. `new BigDecimal("-2.345").setScale(2, HALF_EVEN)`?
5. `new BigDecimal("12.000").setScale(0, UNNECESSARY)` có ném lỗi không?
6. `new BigDecimal("145").setScale(-1, HALF_UP).toPlainString()` và `new BigDecimal("-145").setScale(-1, HALF_UP).toPlainString()`?

**Đáp án và lý do:**

1. `FLOOR = -8` (hướng −∞); `CEILING = -7` (hướng +∞).
2. `HALF_UP = -8` (tie xa 0); `HALF_DOWN = -7` (tie về 0).
3. `6.5 → 6` và `7.5 → 8` theo `HALF_EVEN` vì ứng viên được chọn có chữ số hàng đơn vị chẵn.
4. `-2.345 → -2.34`: hai ứng viên có cùng khoảng cách; chữ số hàng phần trăm `4` chẵn.
5. **Không lỗi**, kết quả `12`, vì bỏ các số 0 ở phần lẻ không đổi giá trị.
6. `145 → 150` và `-145 → -150` theo `HALF_UP` khi làm tròn hàng chục, vì cả hai là tie và phải chọn phía xa 0.

**Ghi nhớ:** Với giá trị âm, “lên/xuống” phải được định nghĩa theo **trục số** hay **khoảng cách đến 0**. Khi kết quả gần nhau như nhau, ba chế độ `HALF_` mới khác nhau. Hãy quyết định scale hoặc precision cùng chính sách mode, kiểm thử dấu âm và xử lý riêng yêu cầu không được mất độ chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-010">Làm tròn hai lần và khác biệt với làm tròn ở bước cuối</a>

<details>
<summary>Xem chi tiết</summary>

# Làm tròn hai lần và khác biệt với làm tròn ở bước cuối

## 1. Vấn đề: cùng một dữ liệu, hai cách làm tròn có thể cho hai kết quả

Trong tính toán thập phân, **làm tròn hai lần** (*double rounding*) là tình huống một giá trị bị làm tròn ở một mức trung gian rồi lại làm tròn về mức cuối cùng. Kết quả **có thể khác** việc giữ nguyên các chữ số cần thiết và chỉ làm tròn một lần ở bước cuối.

Xét số thập phân chính xác `1.245`, cùng quy tắc `RoundingMode.HALF_UP`:

| Quy trình | Giá trị trung gian | Giá trị cuối |
| --- | --- | --- |
| Làm tròn hai lần: 3 → 2 → 1 chữ số sau dấu phẩy | `1.245 → 1.25` | `1.3` |
| Làm tròn trực tiếp: 3 → 1 chữ số sau dấu phẩy | Không có | `1.2` |

Lần làm tròn đầu tiên đã đổi `1.245` thành `1.25`, khiến lần làm tròn thứ hai nhìn thấy **một trường hợp đúng giữa** (`1.25` nằm giữa `1.2` và `1.3`). Trong khi đó, `1.245` thực sự gần `1.2` hơn `1.3`. Sai khác này đến từ thông tin bị mất ở bước trung gian, **không phải lỗi của `BigDecimal`**.

```java
BigDecimal value = new BigDecimal("1.245");
BigDecimal twice = value
        .setScale(2, RoundingMode.HALF_UP)
        .setScale(1, RoundingMode.HALF_UP);
BigDecimal once = value.setScale(1, RoundingMode.HALF_UP);

System.out.println(twice); // 1.3
System.out.println(once);  // 1.2
```

Các đoạn mã rời trong bài giả định đã import `java.math.BigDecimal` và `java.math.RoundingMode`. Phần chương trình hoàn chỉnh bên dưới có thể biên dịch và chạy độc lập với Java 17+.

## 2. Cơ chế toán học: sai khác xuất hiện ở đâu?

Ký hiệu `R_s(x)` là kết quả làm tròn `x` về **scale `s`**, với một `RoundingMode` nhất định. Hai phép xử lý cần so sánh là:

```text
Làm tròn trung gian: R_1(R_2(x))
Làm tròn một lần:   R_1(x)
```

Với `x = 1.245`, `HALF_UP`:

1. `R_2(1.245) = 1.25` vì bỏ chữ số 5 đúng giữa hai mức ở hàng phần trăm và `HALF_UP` làm tròn lên với số dương.
2. `R_1(1.25) = 1.3` vì `1.25` là điểm giữa `1.2` và `1.3`.
3. `R_1(1.245) = 1.2` vì giá trị ban đầu **nhỏ hơn** điểm giữa `1.25`; nó chưa đủ gần `1.3` để làm tròn lên.

Như vậy:

```text
R_1(R_2(1.245)) = 1.3
R_1(1.245)      = 1.2
```

Sai khác bằng `0.1` — một đơn vị ở độ chính xác đích, dù việc làm tròn trung gian chỉ thay đổi `1.245` một lượng `0.005`.

Điều cần ghi nhớ là **một giá trị vốn nằm sát ngưỡng làm tròn ở độ chính xác cuối** có thể bị đẩy tới hoặc vượt ngưỡng vì một bước làm tròn trước đó. Không phải mọi giá trị đều gặp tình huống này, nhưng không thể bảo đảm hai quy trình luôn tương đương chỉ vì dùng cùng `RoundingMode`.

### Vì sao “đúng giữa” quan trọng?

Với `HALF_UP` và kết quả một chữ số sau dấu phẩy, ngưỡng giữa `1.2` và `1.3` là `1.25`. Số `1.245` nằm **dưới ngưỡng** này. Làm tròn đến hai chữ số trước đã đẩy nó thành đúng `1.25`, khiến quyết định ở bước cuối thay đổi.

Trong thuật toán tính toán, một quy tắc làm tròn áp dụng hai lần không nhất thiết có tính chất:

```text
R_final(R_intermediate(x)) == R_final(x)
```

Nhất là với các chế độ làm tròn gần nhất như `HALF_UP`, `HALF_EVEN` và `HALF_DOWN`.

## 3. Với HALF_EVEN cũng có thể xảy ra khác biệt

`HALF_EVEN` chọn kết quả có chữ số cuối chẵn khi hai giá trị ứng viên cách điểm đầu vào đúng bằng nhau. Cách này thường được gọi là *banker's rounding*, nhưng **không miễn nhiễm với làm tròn hai lần**.

Xét `1.251`:

```java
BigDecimal x = new BigDecimal("1.251");
BigDecimal intermediate = x.setScale(2, RoundingMode.HALF_EVEN);
BigDecimal twice = intermediate.setScale(1, RoundingMode.HALF_EVEN);
BigDecimal once = x.setScale(1, RoundingMode.HALF_EVEN);

System.out.println(intermediate); // 1.25
System.out.println(twice);        // 1.2
System.out.println(once);         // 1.3
```

Giải thích:

- `1.251` về hai chữ số sau dấu phẩy là `1.25` vì gần `1.25` hơn `1.26`.
- `1.25` về một chữ số sau dấu phẩy tạo thế hòa giữa `1.2` và `1.3`; `HALF_EVEN` chọn `1.2` vì chữ số ở hàng phần mười là 2 (chẵn).
- `1.251` về trực tiếp một chữ số là `1.3` vì giá trị gốc lớn hơn điểm giữa `1.25`; trường hợp này **không hòa**, nên quy tắc chẵn không được sử dụng.

Vì thế việc chọn `HALF_EVEN` nhằm hạn chế thiên lệch trong nhiều phép làm tròn không đồng nghĩa với việc có thể làm tròn trung gian tùy ý.

## 4. Không phải hai lần làm tròn nào cũng cho kết quả khác

Ví dụ `1.234` với `HALF_UP`:

```text
1.234 → 1.23 → 1.2
1.234 ─────────→ 1.2
```

Hai đường cùng trả `1.2`. Sai khác phụ thuộc vào chữ số bị bỏ, vị trí ngưỡng, scale trung gian, scale cuối và chế độ làm tròn.

Khi chỉ liên tiếp **giảm scale trên cùng một số** bằng một chế độ làm tròn có hướng cố định (`DOWN` hướng về 0, `UP` xa 0, `FLOOR` hướng về âm vô cực, `CEILING` hướng về dương vô cực), kết quả hai lần khớp kết quả trực tiếp ở scale đích: các phép làm tròn theo hướng này không tạo tình huống “đúng giữa” mới làm đổi lựa chọn. Nhưng khi chèn **phép tính khác** giữa các lần làm tròn, hoặc thay đổi rounding mode, không được tự suy rộng kết luận đó.

Ngược lại, với các chế độ gần nhất như `HALF_UP` và `HALF_EVEN`, phải tính đến nguy cơ làm tròn hai lần ngay cả khi scale trung gian mịn hơn scale cuối.

Một cách nhìn hữu ích: nếu quy trình đưa từ `1.245` đến `1.25`, phần `0.005` đã mất không còn có thể được suy ra từ `1.25`. Lần làm tròn sau chỉ thấy số đã bị biến đổi.

## 5. Phân biệt làm tròn hai lần với làm tròn từng phần trước khi tổng hợp

Một nguồn sai khác thường gặp khác là **thứ tự làm tròn so với phép cộng hoặc nhân**. Dù không nhất thiết là cùng một số bị gọi `setScale()` hai lần, đó vẫn là cùng vấn đề thiết kế: **đặt phép làm tròn ở bước nào của phép tính**.

### 5.1. Cộng hai khoản nhỏ

Hai khoản chính xác là `0.006` và `0.006`. Nếu làm tròn từng khoản về hai chữ số bằng `HALF_UP` trước khi cộng:

```text
round(0.006, 2) + round(0.006, 2)
= 0.01 + 0.01
= 0.02
```

Nếu cộng ở độ chính xác gốc rồi làm tròn tổng một lần:

```text
round(0.006 + 0.006, 2)
= round(0.012, 2)
= 0.01
```

Kết quả chênh `0.01`. Nếu hợp đồng yêu cầu từng khoản đã là một đơn vị hạch toán phải làm tròn độc lập, quy trình đầu có thể là đúng. Nếu quy định chỉ làm tròn tổng cuối, quy trình sau mới đúng. **Không có công thức duy nhất đúng cho mọi trường hợp tài chính.**

### 5.2. Làm tròn đơn giá trước khi nhân số lượng

Giá đơn vị giả định là `0.335`, số lượng là 3.

```text
Làm tròn đơn giá trước:
round(0.335, 2) × 3
= 0.34 × 3
= 1.02

Nhân chính xác rồi làm tròn tổng dòng:
round(0.335 × 3, 2)
= round(1.005, 2)
= 1.01
```

Với `HALF_UP`, hai quy trình chênh `0.01`. Đây không phải sai số nhị phân vì toàn bộ giá trị có thể được lưu chính xác trong `BigDecimal`; sai khác hoàn toàn do **quy tắc làm tròn được áp dụng ở thời điểm khác nhau**.

### 5.3. Chia số và độ chính xác trung gian

Một phép chia như `1 / 3` có biểu diễn thập phân vô hạn; `BigDecimal.ONE.divide(new BigDecimal("3"))` không thể có kết quả thập phân hữu hạn chính xác và sẽ ném `ArithmeticException`. Khi buộc phải làm tròn ở bước chia, cần đặt scale/`MathContext` trung gian đủ phù hợp và định nghĩa rõ ranh giới làm tròn nghiệp vụ, ví dụ `divide(divisor, 8, RoundingMode.HALF_EVEN)`. Không nên gọi `divide()` thiếu quy tắc và hy vọng `setScale()` sau đó sẽ xử lý được exception đã xảy ra.

Dù chọn thêm các chữ số “guard digits” ở trung gian thường giúp giảm rủi ro, không có số chữ số dư cố định nhỏ nào tự động bảo đảm mọi phép tính và mọi đầu vào cho ra đúng kết quả như phép tính chính xác vô hạn. Cần hiểu độ chính xác của dữ liệu, chuỗi phép tính và tiêu chuẩn làm tròn cần chứng minh.

## 6. Precision và MathContext: dạng làm tròn hai lần khác

`BigDecimal.setScale(n, mode)` giữ `n` chữ số **sau dấu thập phân**. `MathContext(p, mode)` giới hạn `p` **chữ số có nghĩa** (*precision*) trong một số phép toán hoặc khi gọi `round(MathContext)`. Hai tham số này khác nhau, nhưng nguy cơ làm tròn trung gian tồn tại ở cả hai.

Ví dụ với `9.949`:

```java
BigDecimal x = new BigDecimal("9.949");
MathContext p3 = new MathContext(3, RoundingMode.HALF_UP);
MathContext p2 = new MathContext(2, RoundingMode.HALF_UP);

BigDecimal twice = x.round(p3).round(p2);
BigDecimal once = x.round(p2);

System.out.println(x.round(p3).toPlainString()); // 9.95
System.out.println(twice.toPlainString());       // 10
System.out.println(once.toPlainString());        // 9.9
```

`9.949` được làm tròn từ bốn xuống ba chữ số có nghĩa thành `9.95`. Sau đó, từ ba xuống hai chữ số có nghĩa thành `10`. Nhưng nếu đi thẳng từ `9.949` xuống hai chữ số có nghĩa, kết quả là `9.9`.

Khi tính toán nhiều bước, ví dụ nhân rồi cộng với `MathContext` giới hạn, mỗi phép toán sử dụng context có thể làm tròn ngay ở kết quả của phép đó. Điều này ảnh hưởng tới kết quả cuối, kể cả khi sau đó còn một lời gọi `setScale()` để hiển thị. Hãy phân biệt rõ:

- **Precision tính toán**: độ chính xác của kết quả các bước số học.
- **Scale lưu trữ/trao đổi**: mức thập phân theo hợp đồng dữ liệu.
- **Scale hiển thị**: số chữ số dùng cho giao diện, hóa đơn hoặc báo cáo.

Không nên hoán đổi ba khái niệm trên.

## 7. Ví dụ Java 17+ hoàn chỉnh, chạy được

Lưu chương trình sau vào tệp `DoubleRoundingDemo.java`. Mỗi phép tính dùng chuỗi thập phân để tránh nhiễu do biểu diễn `double`.

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class DoubleRoundingDemo {
    public static void main(String[] args) {
        RoundingMode halfUp = RoundingMode.HALF_UP;

        BigDecimal value = new BigDecimal("1.245");
        BigDecimal intermediate = value.setScale(2, halfUp);
        BigDecimal twice = intermediate.setScale(1, halfUp);
        BigDecimal once = value.setScale(1, halfUp);

        System.out.println("original = " + value);
        System.out.println("intermediate = " + intermediate);
        System.out.println("twice = " + twice);
        System.out.println("once = " + once);

        BigDecimal halfEvenValue = new BigDecimal("1.251");
        BigDecimal halfEvenTwice = halfEvenValue
                .setScale(2, RoundingMode.HALF_EVEN)
                .setScale(1, RoundingMode.HALF_EVEN);
        BigDecimal halfEvenOnce =
                halfEvenValue.setScale(1, RoundingMode.HALF_EVEN);
        System.out.println("HALF_EVEN twice = " + halfEvenTwice);
        System.out.println("HALF_EVEN once = " + halfEvenOnce);

        BigDecimal itemA = new BigDecimal("0.006");
        BigDecimal itemB = new BigDecimal("0.006");
        BigDecimal sumOfRoundedItems =
                itemA.setScale(2, halfUp).add(itemB.setScale(2, halfUp));
        BigDecimal roundedTotal = itemA.add(itemB).setScale(2, halfUp);
        System.out.println("sum rounded items = " + sumOfRoundedItems);
        System.out.println("round sum = " + roundedTotal);

        BigDecimal unitPrice = new BigDecimal("0.335");
        BigDecimal quantity = new BigDecimal("3");
        BigDecimal roundedUnitFirst =
                unitPrice.setScale(2, halfUp).multiply(quantity);
        BigDecimal roundedLineTotal =
                unitPrice.multiply(quantity).setScale(2, halfUp);
        System.out.println("round unit first = " + roundedUnitFirst);
        System.out.println("round line total = " + roundedLineTotal);

        BigDecimal precisionInput = new BigDecimal("9.949");
        MathContext p3 = new MathContext(3, halfUp);
        MathContext p2 = new MathContext(2, halfUp);
        System.out.println("precision twice = "
                + precisionInput.round(p3).round(p2).toPlainString());
        System.out.println("precision once = "
                + precisionInput.round(p2).toPlainString());
    }
}
```

Chạy từ thư mục chứa tệp:

```bash
javac DoubleRoundingDemo.java
java DoubleRoundingDemo
```

Đầu ra kỳ vọng:

```text
original = 1.245
intermediate = 1.25
twice = 1.3
once = 1.2
HALF_EVEN twice = 1.2
HALF_EVEN once = 1.3
sum rounded items = 0.02
round sum = 0.01
round unit first = 1.02
round line total = 1.01
precision twice = 10
precision once = 9.9
```

Điểm cần quan sát: cùng số ban đầu và cùng rounding mode nhưng đường đi khác có thể tạo kết quả khác. Chương trình giữ kết quả `BigDecimal` gốc không đổi vì `BigDecimal` là immutable. `setScale()` và `round(MathContext)` **trả về kết quả**; các lời gọi không làm biến đổi instance nguồn.

## 8. Cách thiết kế quy trình tính toán tránh mất độ chính xác không cần thiết

**Bước 1 — Xác định kết quả cuối và đơn vị làm tròn.** Ghi rõ “số tiền của từng dòng hóa đơn làm tròn về 0.01”, “tổng lãi làm tròn sau mỗi kỳ”, hoặc “chỉ định dạng hiển thị hai chữ số sau dấu phẩy”. Những câu này mô tả ba quy trình khác nhau.

**Bước 2 — Giữ dữ liệu thập phân chính xác ở đầu vào.** Nếu số gốc là decimal do người dùng/DB cung cấp, khởi tạo `BigDecimal` từ chuỗi hoặc dữ liệu decimal chính xác. Tránh `new BigDecimal(0.1)` nếu định dùng số thập phân chính xác 0.1; constructor này giữ chính xác giá trị nhị phân xấp xỉ của literal `double`. Thường dùng `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)`.

**Bước 3 — Dùng độ chính xác tính toán phù hợp.** `BigDecimal.add()` và `multiply()` không truyền `MathContext` thực hiện số học chính xác theo khả năng số học thập phân (với scale của toán hạng). `divide()` có thể đòi chính sách rounding khi thương không hữu hạn. Nếu dùng `MathContext` trong chuỗi tính toán, hiểu rằng kết quả trung gian có thể đã bị làm tròn.

**Bước 4 — Làm tròn ở ranh giới được định nghĩa.** Nếu hợp đồng đòi làm tròn từng line item, làm vậy chủ ý; nếu hợp đồng đòi làm tròn một lần sau khi cộng tổng, trì hoãn `setScale()` đến cuối tổng. Tránh làm tròn “cho đẹp dữ liệu” ở những bước vẫn còn phép toán phía sau.

**Bước 5 — Chọn rounding mode theo nghiệp vụ.** `HALF_EVEN`, `HALF_UP`, `FLOOR` và `CEILING` phục vụ nhu cầu khác nhau. Đặc biệt lưu ý số âm, hoàn tiền, đảo giao dịch và các điểm đúng giữa.

**Bước 6 — Thử ở các ranh giới.** Ngoài số bình thường, kiểm tra các giá trị vừa dưới/trên midpoint, số âm, số 0 và trường hợp rất nhỏ. Kiểm tra cả tổng nhiều khoản và thứ tự toán tử. Sự khác biệt nhỏ trên một giao dịch có thể tích lũy khi xử lý lượng lớn giao dịch.

Một hàm có ranh giới rõ ràng có thể viết như sau:

```java
static BigDecimal totalRoundedAtEnd(
        BigDecimal first,
        BigDecimal second,
        int resultScale,
        RoundingMode mode) {
    return first.add(second).setScale(resultScale, mode);
}
```

Đây là một **đoạn phương thức minh họa** đặt trong lớp Java; nó thực thi chính sách làm tròn sau phép cộng. Nếu yêu cầu là hai dòng phải được làm tròn độc lập, hàm này không đáp ứng chính sách đó và phải được thay bằng quy trình tương ứng.

## 9. Những giả định sai và cách kiểm tra

| Nhầm lẫn | Điều đúng cần nhớ |
| --- | --- |
| “Cùng `RoundingMode` thì làm tròn bao nhiêu lần cũng giống nhau.” | Sai đối với nhiều kiểu làm tròn gần nhất; `1.245` với `HALF_UP` là phản ví dụ. |
| “`HALF_EVEN` loại bỏ hoàn toàn sai số làm tròn hai lần.” | Sai; `1.251` với `HALF_EVEN` là phản ví dụ. |
| “`BigDecimal` luôn bảo đảm kết quả đúng dù làm tròn ở đâu.” | Nó biểu diễn decimal chính xác và tuân thủ chính sách rounding được chỉ định; chính sách đặt sai thời điểm vẫn cho kết quả khác. |
| “Làm tròn từng hạng mục luôn sai.” | Đúng hay sai phụ thuộc quy tắc hạch toán/thuế/hóa đơn được yêu cầu. |
| “Chỉ cần lưu hai chữ số thập phân cho mọi phép tính tiền.” | Trung gian có thể cần thêm chữ số; nên phân biệt precision tính toán với scale kết quả cuối. |
| “Cứ tính với `double` rồi dùng `setScale()` là hết sai số.” | Chuyển một giá trị nhị phân đã xấp xỉ sang decimal không tự tái tạo được số thập phân gốc mong muốn. |
| “Sau phép tính nào cũng gọi `setScale(2)` để thống nhất.” | Có thể làm mất thông tin và làm đổi giá trị của những phép tính phía sau. |

Về mặt kỹ thuật, cũng phải nhớ rằng `BigDecimal.equals()` xét cả scale: `new BigDecimal("1.2").equals(new BigDecimal("1.20"))` là `false` mặc dù `compareTo()` trả 0. Khi so sánh kết quả tính toán theo **giá trị số học**, dùng `compareTo() == 0` hoặc chuẩn hóa theo một quy tắc nhất quán; khi kiểm thử chuỗi định dạng, so sánh nội dung chuỗi như một kiểm tra riêng.

## 10. Bài tập tự kiểm tra

**Câu 1.** `new BigDecimal("1.245").setScale(2, HALF_UP).setScale(1, HALF_UP)` so với `new BigDecimal("1.245").setScale(1, HALF_UP)`?

**Đáp án:** `1.3` và `1.2`. Bước đầu đã biến `1.245` thành midpoint `1.25` cho lần làm tròn thứ hai.

**Câu 2.** Vì sao `HALF_EVEN` không ngăn hoàn toàn sai khác do double rounding?

**Đáp án:** `1.251` → `1.25` → `1.2`, nhưng làm tròn trực tiếp một chữ số được `1.3`. Bước trung gian tạo ra trường hợp tie giả.

**Câu 3.** Hai khoản `0.006` và `0.006`, `HALF_UP` về 2 chữ số thập phân: tổng sau khi làm tròn từng khoản là bao nhiêu, và làm tròn tổng cuối là bao nhiêu?

**Đáp án:** `0.02` và `0.01`. Chọn kết quả nào phụ thuộc ranh giới làm tròn do nghiệp vụ quy định.

**Câu 4.** Với đơn giá `0.335`, số lượng 3, `HALF_UP` về 2 chữ số: làm tròn giá đơn vị trước khi nhân và làm tròn tổng dòng sau khi nhân khác nhau không?

**Đáp án:** `0.34 × 3 = 1.02`, còn `round(0.335 × 3, 2) = 1.01`.

**Câu 5.** `setScale(2)` và `round(new MathContext(2, mode))` có giống nhau?

**Đáp án:** Không. `setScale(2)` chỉ định 2 chữ số sau dấu phẩy (và sẽ lỗi nếu phải làm tròn mà không có mode), trong khi `MathContext(2, mode)` dùng 2 chữ số có nghĩa.

**Câu 6.** Muốn giảm nguy cơ sai khác do làm tròn hai lần trong một pipeline, nguyên tắc thực tế là gì?

**Đáp án:** Duy trì đủ độ chính xác trung gian; chỉ làm tròn tại ranh giới được quy định; tránh hai bước làm tròn gần nhất liên tiếp nếu có thể tính và làm tròn trực tiếp từ giá trị đầy đủ; thử nghiệm các giá trị sát ngưỡng. Nếu nghiệp vụ yêu cầu làm tròn từng hạng mục, phải thực hiện theo đúng yêu cầu đó.

**Ghi nhớ:** Làm tròn là một phép biến đổi có thể làm mất thông tin. **`R_final(R_intermediate(x))` không nhất thiết bằng `R_final(x)`**; tương tự, làm tròn từng thành phần trước khi cộng/nhân có thể khác làm tròn kết quả cuối. Tính toán bằng `BigDecimal` giúp tránh sai số biểu diễn thập phân của `double`, nhưng không thay thế việc chọn đúng **độ chính xác, quy tắc và thời điểm làm tròn**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-011">Phân biệt setScale với round(MathContext)</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `setScale` với `round(MathContext)` trong Java BigDecimal

## 1. Hai cách làm tròn phục vụ hai câu hỏi khác nhau

Khi tính số thập phân trong Java, `java.math.BigDecimal` có hai phương thức thường bị nhầm là tương đương:

```java
BigDecimal resultA = value.setScale(2, RoundingMode.HALF_UP);
BigDecimal resultB = value.round(new MathContext(2, RoundingMode.HALF_UP));
```

Cùng dùng số `2` và chế độ `HALF_UP`, nhưng **ý nghĩa của số 2 hoàn toàn khác nhau**:

- `setScale(2, ...)` đặt **scale = 2**, tức có hai chữ số sau dấu thập phân trong biểu diễn thập phân chuẩn của số. Nếu cần, giá trị được làm tròn; nếu đang thiếu chữ số, các số 0 được thêm vào.
- `round(new MathContext(2, ...))` giới hạn kết quả còn **tối đa hai chữ số có nghĩa** (*precision*). Vị trí dấu thập phân sẽ tùy độ lớn của số.

Hãy bắt đầu với một ví dụ cụ thể:

```java
BigDecimal x = new BigDecimal("123.4567");
BigDecimal a = x.setScale(2, RoundingMode.HALF_UP); // 123.46
BigDecimal b = x.round(new MathContext(2, RoundingMode.HALF_UP)); // 1.2E+2, giá trị 120
```

`a` giữ hai chữ số **sau dấu chấm**, còn `b` giữ hai chữ số **có nghĩa tính từ chữ số đầu tiên khác 0**. Do đó `setScale(2)` tuyệt đối không thể thay bằng `MathContext(2)` nếu đặc tả yêu cầu “hiển thị hai chữ số thập phân”.

| Tiêu chí | `setScale(newScale, mode)` | `round(MathContext)` |
|---|---|---|
| Tham số đếm chữ số | `newScale`: vị trí dấu thập phân | `MathContext.precision`: tổng chữ số có nghĩa tối đa |
| Quan tâm đến độ lớn số? | Scale được ấn định dù số lớn hay nhỏ | Vị trí làm tròn thay đổi theo độ lớn số |
| Có thể thêm số 0 ở cuối? | Có, khi tăng scale | Không có mục tiêu đệm đủ chữ số sau dấu thập phân |
| Có thể thay đổi scale? | Có: trả kết quả với **scale đúng bằng** `newScale` nếu thành công | Có thể: scale của kết quả phụ thuộc precision và việc làm tròn |
| Số chữ số có nghĩa của kết quả | Không bị giới hạn bởi `newScale` | Không vượt `precision` dương (theo biểu diễn kết quả) |
| Ứng dụng thường gặp | Tiền có hai số thập phân, làm tròn đến 0,001, định dạng đầu ra | Chia số, tính toán gần đúng, quy định số chữ số có nghĩa trong kết quả |

Cả hai đều là thao tác **trả về `BigDecimal`**; đối tượng gốc là bất biến và không tự thay đổi.

## 2. Nền tảng: `scale()` và `precision()` đo những gì?

Với `BigDecimal`, giá trị toán học được tính theo công thức:

```text
value = unscaledValue × 10^(-scale)
```

`scale()` lưu số mũ thập phân theo cách biểu diễn nội bộ; `precision()` đếm số chữ số trong unscaled value (không tính dấu). Những chữ số 0 ở cuối của dạng nhập có thể ảnh hưởng đến hai thuộc tính này.

```java
BigDecimal n = new BigDecimal("123.4500");
System.out.println(n.precision()); // 7
System.out.println(n.scale());     // 4
```

- `123.4500` chứa **bảy chữ số** trong unscaled value `1234500`.
- Có **bốn chữ số sau dấu chấm** nên scale là 4.
- `new BigDecimal("123.45")` có cùng giá trị toán học nhưng precision 5 và scale 2.
- `new BigDecimal("1E+3")` có giá trị 1000, precision 1, scale -3.

Bảng này giúp thấy tại sao hai lối làm tròn khác nhau:

| Số nhập từ chuỗi | Precision | Scale | `setScale(2, HALF_UP)` | `round(MathContext(2, HALF_UP))` |
|---|---:|---:|---|---|
| `123.456` | 6 | 3 | `123.46` | `1.2E+2` (120) |
| `12.345` | 5 | 3 | `12.35` | `12` |
| `0.001234` | 4 | 6 | `0.00` | `0.0012` |
| `1.2` | 2 | 1 | `1.20` | `1.2` |
| `9.995` | 4 | 3 | `10.00` | `10` (thường biểu diễn dưới dạng `1.0E+1`) |

**Chú ý hàng cuối:** khi kết quả làm tròn vượt qua ngưỡng `9...9`, số có thể đổi độ lớn. Với context precision 2, đối tượng có thể dùng dạng khoa học để giữ đúng hai chữ số có nghĩa. Vì thế đừng dùng số chữ số của chuỗi `toPlainString()` làm cách xác định precision: `1.2E+2` và `120` có thể biểu diễn cùng một đối tượng nhưng khác cách in.

## 3. `setScale`: yêu cầu độ phân giải thập phân cố định

Có hai overload phổ biến:

```java
BigDecimal x = new BigDecimal("12.3456");

BigDecimal a = x.setScale(2, RoundingMode.HALF_UP); // 12.35
BigDecimal b = x.setScale(4);                       // 12.3456, không cần làm tròn
```

`setScale(int newScale, RoundingMode roundingMode)` luôn trả kết quả có `scale() == newScale`, trừ khi không thể thực hiện và ném ngoại lệ.

- **Giảm scale:** bỏ bớt vị trí thập phân, cần làm tròn nếu các chữ số bị loại không đều là 0. Ví dụ `12.3456 → 12.35` với scale 2 và `HALF_UP`.
- **Tăng scale:** giữ nguyên giá trị số và thêm số 0 vào phần thập phân. `new BigDecimal("1.2").setScale(4)` cho `1.2000`; precision mới là 5 và scale mới là 4.
- **Giữ scale:** không cần đổi giá trị, kết quả tiếp tục là biểu diễn có scale đó.
- **Scale âm:** hợp lệ, nghĩa là làm tròn theo chục, trăm, nghìn... Ví dụ `new BigDecimal("1234").setScale(-2, RoundingMode.HALF_UP)` có giá trị `1200`, thường được in bằng ký pháp mũ.

`setScale(int newScale)` không có đối số rounding mode: nếu việc đổi scale buộc bỏ chữ số khác 0, nó ném `ArithmeticException`. Không nên nghĩ overload này ngầm dùng `HALF_UP`:

```java
new BigDecimal("5.200").setScale(2); // 5.20: chỉ bỏ số 0
// new BigDecimal("5.205").setScale(2); // ArithmeticException
```

Đây là hành vi hữu ích khi cần phát hiện sai lệch thay vì tự động làm tròn. Khi cần xác định chế độ, hãy truyền `RoundingMode` tường minh.

### Vì sao `setScale` không giới hạn precision?

```java
BigDecimal value = new BigDecimal("123456789.12345");
BigDecimal twoDecimals = value.setScale(2, RoundingMode.HALF_UP);
// 123456789.12: vẫn có 11 chữ số có nghĩa.
```

Hai chữ số sau dấu chấm không đồng nghĩa hai chữ số có nghĩa. Hơn nữa, đệm số 0 để tăng scale có thể **tăng** precision theo biểu diễn mà không thay đổi giá trị toán học.

## 4. `round(MathContext)`: giới hạn chữ số có nghĩa

`MathContext` kết hợp hai thông tin:

```java
MathContext mc = new MathContext(4, RoundingMode.HALF_UP);
int p = mc.getPrecision();                 // 4
RoundingMode m = mc.getRoundingMode();     // HALF_UP
```

`value.round(mc)` làm tròn để kết quả dùng tối đa `p` chữ số có nghĩa. Chữ số 0 đứng trước chữ số khác 0 đầu tiên không “ăn” precision. Vị trí dấu chấm sẽ thay đổi theo độ lớn số:

```java
MathContext mc = new MathContext(4, RoundingMode.HALF_UP);

new BigDecimal("12345.67").round(mc);   // 1.235E+4 (giá trị 12350)
new BigDecimal("12.34567").round(mc);   // 12.35
new BigDecimal("0.001234567").round(mc); // 0.001235
```

Nếu đối tượng **đã có precision không vượt giới hạn**, phương thức không cần làm tròn; nó không chủ động thêm số 0 để đủ p chữ số. Ví dụ `new BigDecimal("1.2").round(new MathContext(4))` vẫn là `1.2`, không phải `1.200`.

Một vài điểm cần nhớ:

1. `new MathContext(4)` dùng mặc định `RoundingMode.HALF_UP`; có thể truyền mode để giao tiếp rõ yêu cầu.
2. Precision **0** trong `MathContext` nghĩa là không giới hạn precision; `MathContext.UNLIMITED` là context có precision 0. Khi đó `round(mc)` không làm tròn vì giới hạn chữ số.
3. `MathContext.DECIMAL32`, `DECIMAL64`, `DECIMAL128` lần lượt dùng precision 7, 16, 34 và mode `HALF_EVEN`. Chúng giới hạn phép toán thập phân khi được truyền vào, không biến dữ liệu thành `float`/`double`.
4. `MathContext` không đặt chính sách toàn cục. `a.add(b)` tiếp tục tính không giới hạn precision dù trước đó bạn đã gọi `a.round(mc)`; muốn áp dụng cho phép cộng phải gọi `a.add(b, mc)`.

### `MathContext` trong phép tính, khác với làm tròn kết quả đã có

Các phép toán như `add`, `subtract`, `multiply`, `divide` có overload nhận `MathContext`:

```java
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);
BigDecimal a = new BigDecimal("10.1234");
BigDecimal b = new BigDecimal("1.2345");

BigDecimal exact = a.add(b);     // 11.3579
BigDecimal rounded = a.add(b, mc); // 11.4
```

`round(mc)` xử lý một **đối tượng kết quả hiện có**, còn `add(b, mc)` yêu cầu phép cộng tạo kết quả theo precision được chỉ định. Đối với phép chia tạo thập phân vô hạn, cấp context ngay trong phép toán còn giúp xác định điểm dừng biểu diễn:

```java
BigDecimal one = BigDecimal.ONE;
BigDecimal three = new BigDecimal("3");

BigDecimal byPrecision = one.divide(
    three, new MathContext(4, RoundingMode.HALF_UP)
); // 0.3333: 4 chữ số có nghĩa

BigDecimal byScale = one.divide(
    three, 2, RoundingMode.HALF_UP
); // 0.33: 2 chữ số sau dấu chấm

// one.divide(three); // ArithmeticException: 1/3 không có biểu diễn hữu hạn.
```

Đặc biệt, `MathContext.UNLIMITED` **không** khắc phục phép chia `1 / 3`: precision 0 yêu cầu kết quả chính xác, mà `1 / 3` có khai triển thập phân vô hạn. Muốn chia ra giá trị xấp xỉ hữu hạn thì phải chọn precision hoặc scale và mode thích hợp.

## 5. RoundingMode chung nhưng đơn vị làm tròn khác nhau

Cả hai API dùng `RoundingMode`, nhưng vị trí làm tròn do **scale** hoặc **precision** quyết định.

| Mode | Quy tắc chung | Ví dụ làm tròn `2.5` về số nguyên |
|---|---|---|
| `UP` | Xa số 0 khi có phần bị bỏ khác 0 | `3` |
| `DOWN` | Về số 0 | `2` |
| `CEILING` | Về +∞ | `3` |
| `FLOOR` | Về −∞ | `2` |
| `HALF_UP` | Gần nhất, đúng giữa thì xa 0 | `3` |
| `HALF_DOWN` | Gần nhất, đúng giữa thì về 0 | `2` |
| `HALF_EVEN` | Gần nhất, đúng giữa thì giữ chữ số cuối chẵn | `2` |
| `UNNECESSARY` | Yêu cầu chính xác; lỗi nếu phải bỏ chữ số có ảnh hưởng giá trị | `ArithmeticException` |

Với số âm, `CEILING` và `FLOOR` khác với `UP`/`DOWN`: `-2.5` làm tròn về số nguyên theo `CEILING` là `-2`, còn theo `FLOOR` là `-3`. `HALF_EVEN` không phải “luôn xuống”: `2.5 → 2` nhưng `3.5 → 4`.

Ví dụ minh họa hai loại ràng buộc:

```java
BigDecimal x = new BigDecimal("12.345");
BigDecimal scaled = x.setScale(2, RoundingMode.HALF_EVEN); // 12.34
BigDecimal significant = x.round(
    new MathContext(4, RoundingMode.HALF_EVEN)
); // 12.34
```

Hai kết quả có thể tình cờ bằng nhau. Điều này **không** chứng minh hai API tương đương: `new BigDecimal("123.456").setScale(2, HALF_EVEN)` là `123.46`, còn `round(MathContext(4, HALF_EVEN))` là `123.5`.

## 6. Chương trình Java 17+ hoàn chỉnh: so sánh và kết quả thật

Tạo file `ScaleVsContextDemo.java`:

```java
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class ScaleVsContextDemo {
    public static void main(String[] args) {
        BigDecimal x = new BigDecimal("123.4567");
        MathContext mc = new MathContext(4, RoundingMode.HALF_UP);
        System.out.println("setScale(2): " + x.setScale(2, RoundingMode.HALF_UP).toPlainString());
        System.out.println("round(4): " + x.round(mc).toPlainString());

        BigDecimal small = new BigDecimal("0.00123456");
        System.out.println("small scale(4): " + small.setScale(4, RoundingMode.HALF_UP).toPlainString());
        System.out.println("small round(4): " + small.round(mc).toPlainString());

        BigDecimal padded = new BigDecimal("1.2").setScale(4, RoundingMode.UNNECESSARY);
        System.out.println("padded: " + padded.toPlainString()
                + ", precision=" + padded.precision()
                + ", scale=" + padded.scale());

        BigDecimal value = new BigDecimal("1.249");
        System.out.println("direct: " + value.setScale(1, RoundingMode.HALF_UP).toPlainString());
        System.out.println("two steps: " + value.round(new MathContext(3, RoundingMode.HALF_UP))
                .setScale(1, RoundingMode.HALF_UP).toPlainString());

        BigDecimal three = new BigDecimal("3");
        System.out.println("divide precision: " + BigDecimal.ONE.divide(three, mc).toPlainString());
        System.out.println("divide scale: " + BigDecimal.ONE.divide(three, 2, RoundingMode.HALF_UP).toPlainString());
        System.out.println("exact scale: " + new BigDecimal("2.500")
                .setScale(2, RoundingMode.UNNECESSARY).toPlainString());

        try {
            new BigDecimal("2.501").setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            System.out.println("rounding required: ArithmeticException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ScaleVsContextDemo.java
java ScaleVsContextDemo
```

Kết quả chuẩn:

```text
setScale(2): 123.46
round(4): 123.5
small scale(4): 0.0012
small round(4): 0.001235
padded: 1.2000, precision=5, scale=4
direct: 1.2
two steps: 1.3
divide precision: 0.3333
divide scale: 0.33
exact scale: 2.50
rounding required: ArithmeticException
```

Đây là hai góc nhìn rõ nhất:

- Với số lớn `123.4567`, scale 2 vẫn giữ toàn bộ phần nguyên và hai chữ số lẻ; precision 4 chỉ giữ bốn chữ số có nghĩa, nên `123.5`.
- Với số nhỏ `0.00123456`, scale 4 chỉ giữ đến chữ số ở vị trí `10^-4`, nên `0.0012`; precision 4 đếm từ chữ số 1 đầu tiên nên `0.001235`.

`toPlainString()` được dùng để hiển thị không có ký pháp mũ; nó không đổi giá trị hay precision của đối tượng.

## 7. Không làm tròn hai lần một cách vô thức

Hai đường xử lý cùng đầu vào có thể cho **giá trị khác nhau** nếu làm tròn trung gian:

```java
BigDecimal original = new BigDecimal("1.249");

BigDecimal direct = original.setScale(1, RoundingMode.HALF_UP);
// 1.2

BigDecimal twoStages = original
        .round(new MathContext(3, RoundingMode.HALF_UP)) // 1.25
        .setScale(1, RoundingMode.HALF_UP);              // 1.3
```

Giải thích:

1. Đường trực tiếp xét `1.249`: gần `1.2` hơn `1.3`, nên được `1.2`.
2. Đường hai bước làm tròn `1.249` thành `1.25` (ba chữ số có nghĩa). Khi giảm scale về 1, `1.25` là trường hợp đúng giữa; `HALF_UP` chọn `1.3`.

Hiện tượng này gọi là *double rounding*. Nếu nghiệp vụ chỉ yêu cầu làm tròn **kết quả cuối** đến hai chữ số sau dấu chấm, hãy áp dụng `setScale(2, mode)` ở bước cuối phù hợp, không tùy tiện chèn một `round(MathContext)` có precision thấp trước đó. Nếu cần làm tròn trung gian vì chia vô hạn, hãy chọn precision làm việc đủ lớn và kiểm tra sai số cũng như quy tắc nghiệp vụ.

Ngược lại, có những bài toán thật sự yêu cầu từng bước phải được làm tròn (ví dụ theo đơn vị giao dịch, từng dòng thuế, quy chuẩn đo lường). Khi đó phải tôn trọng đúng **thời điểm** làm tròn đã được quy định. Chọn API không thể tách rời cách xác định điểm làm tròn.

## 8. Khi làm việc với tiền, tỷ lệ, đo lường và hiển thị

### Trường hợp A: số tiền cuối phải có hai chữ số thập phân

Ví dụ nghiệp vụ giả định cần làm tròn `HALF_UP` về 0,01 đơn vị tiền tệ:

```java
BigDecimal net = new BigDecimal("19.99");
BigDecimal rate = new BigDecimal("0.0825");

BigDecimal taxExact = net.multiply(rate); // 1.649175
BigDecimal tax = taxExact.setScale(2, RoundingMode.HALF_UP); // 1.65
```

`setScale(2, ...)` phù hợp vì yêu cầu nói rõ **hai vị trí sau dấu thập phân**. `MathContext(2, ...)` không thể bảo đảm điều này: `1.649175` về hai chữ số có nghĩa thành `1.6` (có giá trị và số chữ số thập phân khác).

Quy tắc làm tròn tiền phụ thuộc hợp đồng và pháp luật áp dụng; `HALF_UP` trong ví dụ chỉ là giả định kỹ thuật.

### Trường hợp B: chia ra tỷ lệ cần độ chính xác làm việc

```java
MathContext working = new MathContext(16, RoundingMode.HALF_EVEN);
BigDecimal total = new BigDecimal("100.00");
BigDecimal count = new BigDecimal("3");

BigDecimal unit = total.divide(count, working); // xấp xỉ 33.33333333333333
BigDecimal shown = unit.setScale(2, RoundingMode.HALF_UP); // 33.33
```

Ở đây hai API giải quyết **hai bước khác nhau**: context cho phép phép chia tạo kết quả gần đúng có 16 chữ số có nghĩa; `setScale(2)` chuẩn hóa độ phân giải của kết quả dùng để hiển thị. Nếu hệ thống cần phân bổ tổng số tiền chính xác cho nhiều đơn vị, làm tròn đơn giá rồi nhân trở lại có thể tạo phần dư; nghiệp vụ cần quy tắc phân bổ phần dư riêng.

### Trường hợp C: đo lường theo chữ số có nghĩa

Một thiết bị cung cấp kết quả `0.000987654` và quy trình báo cáo yêu cầu **ba chữ số có nghĩa**:

```java
BigDecimal reading = new BigDecimal("0.000987654");
BigDecimal reported = reading.round(
    new MathContext(3, RoundingMode.HALF_EVEN)
); // 0.000988
```

`setScale(3)` ở đây sẽ cho `0.001`, mất hầu hết độ chi tiết; nó không biểu đạt yêu cầu chữ số có nghĩa. Dù vậy, quy định đo lường có thể yêu cầu cả chữ số có nghĩa, chữ số chắc chắn và cách xử lý độ không đảm bảo: chỉ context chưa bao quát toàn bộ phép đo.

## 9. Những ngoại lệ và chi tiết dễ bỏ sót

### 9.1. `RoundingMode.UNNECESSARY`: chỉ được đổi biểu diễn nếu vẫn chính xác

```java
new BigDecimal("2.500").setScale(2, RoundingMode.UNNECESSARY);
// 2.50: bỏ số 0 không đổi giá trị

// new BigDecimal("2.501").setScale(2, RoundingMode.UNNECESSARY);
// ArithmeticException: cần bỏ chữ số 1 có ảnh hưởng giá trị
```

Tương tự, `round(new MathContext(p, UNNECESSARY))` chỉ thành công nếu giảm precision mà không làm thay đổi giá trị. Ví dụ từ `2.500` xuống precision 2 có thể thành `2.5`, nhưng từ `2.501` xuống precision 2 thì không thể chính xác.

### 9.2. Cùng giá trị số, khác `equals`

```java
BigDecimal a = new BigDecimal("1.2");
BigDecimal b = a.setScale(2); // 1.20

boolean sameObjectValueAndScale = a.equals(b); // false
boolean sameNumericValue = a.compareTo(b) == 0; // true
```

`BigDecimal.equals` xét cả **giá trị và scale**; `compareTo` so sánh giá trị số. Khi test chức năng có quy tắc biểu diễn (ví dụ yêu cầu đúng hai chữ số lẻ), kiểm tra cả `scale()` hoặc chuỗi đầu ra. Khi chỉ so sánh giá trị toán học, `compareTo() == 0` có thể thích hợp hơn.

### 9.3. Dạng mũ và số 0 cuối

Một số phép `round(MathContext)` trả ra đối tượng có scale âm hoặc ký pháp mũ:

```java
BigDecimal y = new BigDecimal("12345")
        .round(new MathContext(2, RoundingMode.HALF_UP));
// y.toString(): "1.2E+4"
// y.toPlainString(): "12000"
// y.precision(): 2
```

`toPlainString()` là lựa chọn hiển thị dạng thường, nhưng không phải công cụ giới hạn số chữ số thập phân. Trước khi in cho người dùng theo định dạng cố định, hãy xác định scale hoặc dùng định dạng được chỉ rõ bởi đặc tả.

### 9.4. Tránh chuyển thẳng `double` vào `BigDecimal`

```java
BigDecimal exactDecimal = new BigDecimal("0.1");
BigDecimal fromValueOf = BigDecimal.valueOf(0.1);
BigDecimal fromDouble = new BigDecimal(0.1);
```

`new BigDecimal(0.1)` lưu chính xác giá trị nhị phân `double` được truyền vào, vốn không biểu diễn đúng `0.1`. Nếu sau đó áp dụng `round(MathContext)`, bạn có thể che bớt chữ số nhiễu nhưng không giải quyết đúng nguồn dữ liệu. Với đầu vào thập phân cần kiểm soát, nên parse từ chuỗi hoặc `BigDecimal.valueOf` với sự hiểu biết về dữ liệu nguồn.

### 9.5. Không có context ngầm định cho mọi phép toán

```java
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);
BigDecimal x = new BigDecimal("9.999");
BigDecimal rounded = x.round(mc); // 10.0
BigDecimal plusExact = x.add(BigDecimal.ONE); // 10.999: không nhận context
BigDecimal plusRounded = x.add(BigDecimal.ONE, mc); // 11.0
```

Kết quả của một lệnh gọi không tự xác lập “precision mặc định” cho những lệnh khác. Khi thiết kế service tính toán, hãy truyền context rõ ràng hoặc đưa nó vào API nghiệp vụ phù hợp.

## 10. Bảng quyết định nhanh

| Yêu cầu phát biểu | Cách dùng phù hợp | Lý do |
|---|---|---|
| “Giữ đúng hai chữ số sau dấu thập phân” | `setScale(2, mode)` | Scale cố định là 2 |
| “Làm tròn đến hàng chục” | `setScale(-1, mode)` | Scale -1 tương ứng bội số của 10 |
| “Làm tròn thành bốn chữ số có nghĩa” | `round(new MathContext(4, mode))` | Precision cố định là 4 |
| “Chia 1/3 lấy sáu chữ số có nghĩa” | `divide(divisor, new MathContext(6, mode))` | Phép chia thập phân vô hạn cần precision và mode |
| “Chia 1/3 lấy sáu chữ số sau dấu chấm” | `divide(divisor, 6, mode)` | Phép chia dùng scale cố định |
| “Chỉ chấp nhận cắt bớt số 0 dư, không được mất giá trị” | `setScale(target, UNNECESSARY)` | Ném lỗi khi phải làm tròn không chính xác |
| “Giữ đủ precision trung gian rồi hiển thị hai chữ số lẻ” | `divide(..., workingContext)` rồi `setScale(2, mode)` | Hai công đoạn có hai mục tiêu khác nhau |

Không áp dụng máy móc cả hai phương thức cho mọi giá trị: mỗi lần làm tròn đều có thể mất thông tin. Quy trình tốt cần ghi rõ độ chính xác làm việc, đơn vị đầu ra, chế độ làm tròn và thời điểm làm tròn.

## 11. Tự kiểm tra và đáp án

**Câu 1.** Cho `x = new BigDecimal("0.00056789")`. So sánh `x.setScale(5, HALF_UP)` và `x.round(new MathContext(3, HALF_UP))`.

**Đáp án:** `setScale(5)` cho `0.00057`; context precision 3 cho `0.000568`. Scale đếm vị trí sau dấu chấm; precision đếm từ chữ số 5 đầu tiên khác 0.

**Câu 2.** Tại sao `new BigDecimal("1.20").setScale(4)` có precision lớn hơn trước?

**Đáp án:** kết quả `1.2000` chứa năm chữ số trong unscaled value và scale bằng 4, trong khi `1.20` có precision 3 và scale 2. Giá trị toán học vẫn bằng nhau.

**Câu 3.** Nếu nhận `12345.678` và yêu cầu làm tròn đến hàng trăm, dùng API gì?

**Đáp án:** `setScale(-2, mode)`, vì hàng trăm tương ứng scale -2. Ví dụ với `HALF_UP` cho giá trị 12300. Có thể nhận kết quả tương ứng bằng context trong một số trường hợp, nhưng context biểu đạt số chữ số có nghĩa, không trực tiếp biểu đạt đơn vị hàng trăm với mọi đầu vào.

**Câu 4.** `new BigDecimal("1.249")` làm tròn trực tiếp về một chữ số sau dấu thập phân so với `round(MathContext(3, HALF_UP))` rồi `setScale(1, HALF_UP)` có giống nhau không?

**Đáp án:** không. Trực tiếp được `1.2`; hai bước được `1.3` do double rounding.

**Câu 5.** Nếu mục tiêu là phát hiện mọi thay đổi giá trị khi hạ scale, nên dùng gì?

**Đáp án:** `setScale(targetScale, RoundingMode.UNNECESSARY)`; nếu phải bỏ chữ số khác 0, phương thức ném `ArithmeticException`.

**Ghi nhớ cốt lõi:** `setScale` trả lời **“bao nhiêu chữ số thập phân?”**; `round(MathContext)` trả lời **“bao nhiêu chữ số có nghĩa?”**. Cùng `RoundingMode`, khác vị trí làm tròn. Xác định nhu cầu trước khi chọn API, và luôn chú ý thứ tự các bước tính để tránh làm tròn hai lần.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="precision-and-rounding-012">Không mặc định Math.round tương đương mọi chế độ HALF_UP</a>

<details>
<summary>Xem chi tiết</summary>

# Không mặc định `Math.round` tương đương mọi chế độ `HALF_UP`

## 1. Điều cần nhớ

Trong Java, **`Math.round` không phải tên gọi khác của `RoundingMode.HALF_UP`**. Hai cơ chế đều có thể cho kết quả giống nhau trong nhiều trường hợp, nhưng **khác nhau ở điểm đúng nửa khi giá trị âm**, khác loại dữ liệu đầu vào/đầu ra, cách xử lý độ chính xác và các giá trị đặc biệt.

- `Math.round(float)` trả về `int`; `Math.round(double)` trả về `long`. Nó chọn **số nguyên gần nhất**, và nếu nằm đúng giữa hai số nguyên thì chọn số lớn hơn, tức **hướng về dương vô cực** (`+∞`).
- `RoundingMode.HALF_UP` là quy tắc làm tròn dùng với những API như `BigDecimal.setScale`, `divide` hoặc `MathContext`. Nó chọn kết quả gần nhất; nếu **đúng nửa**, chọn kết quả **xa số 0 hơn**.
- `Math.round(-1.5) == -1` nhưng `new BigDecimal("-1.5").setScale(0, RoundingMode.HALF_UP)` cho `-2`. Đây là phản ví dụ đủ để bác bỏ kết luận hai cơ chế tương đương.

Mục tiêu là chọn **quy tắc nghiệp vụ** trước rồi mới chọn API, thay vì nhìn một vài ví dụ dương và suy rộng cho mọi đầu vào.

## 2. Quy tắc ở điểm đúng nửa: khác nhau chính xác ở đâu?

Xét số cần làm tròn đến số nguyên, hai số nguyên lân cận có khoảng cách bằng nhau ở đầu vào `n + 0.5`.

Với `Math.round`, tại điểm đúng nửa Java chọn số nguyên lớn hơn về mặt số học. Ví dụ `-2` lớn hơn `-3`, nên `Math.round(-2.5)` cho `-2`.

Với `HALF_UP`, “UP” trong tên của `RoundingMode` **không có nghĩa tăng giá trị về `+∞`**. Nó có nghĩa **tăng độ lớn tuyệt đối, tức đi xa số 0** khi gặp đúng nửa. Do đó `-2.5` thành `-3`.

| Giá trị đầu vào | `Math.round(double)` | `BigDecimal.setScale(0, HALF_UP)` | Nhận xét |
| ---: | ---: | ---: | --- |
| `2.4` | `2` | `2` | Cả hai chọn số gần nhất. |
| `2.5` | `3` | `3` | Đúng nửa dương: trùng kết quả. |
| `1.5` | `2` | `2` | Đúng nửa dương: trùng kết quả. |
| `0.5` | `1` | `1` | Đúng nửa dương: trùng kết quả. |
| `-0.5` | `0` | `-1` | Đúng nửa âm: **khác**. |
| `-1.5` | `-1` | `-2` | Đúng nửa âm: **khác**. |
| `-2.5` | `-2` | `-3` | Đúng nửa âm: **khác**. |
| `-2.4` | `-2` | `-2` | Không đúng nửa: cùng chọn số gần nhất. |

Các đầu vào `.5` trong bảng biểu diễn chính xác được bằng `double`, nên khác biệt này thực sự đến từ **chính sách xử lý điểm hòa**, không phải sai số dấu phẩy động.

Không thể giải thích `Math.round(x)` bằng “luôn làm tròn lên” vì `Math.round(2.1) == 2`, cũng không thể nói nó luôn làm tròn về phía số 0 vì `Math.round(-1.6) == -2`. Quy tắc đầy đủ là: **số nguyên gần nhất; nếu hòa, chọn số lớn hơn**.

Một cách nhớ đơn giản: với các đầu vào hữu hạn mà kết quả trong miền biểu diễn và không vướng chi tiết độ chính xác của phép cộng dấu phẩy động, quy tắc `Math.round` giống làm tròn đến số nguyên gần nhất với điểm hòa hướng về `+∞`. Không nên tự thay toàn bộ hiện thực bằng công thức dấu phẩy động `(long)Math.floor(x + 0.5)`: phép cộng `x + 0.5` còn chịu làm tròn nhị phân, và ép kiểu/giới hạn miền giá trị có những quy tắc riêng.

## 3. Phân biệt các `RoundingMode` dễ nhầm

Để so sánh cho công bằng, đặt cùng mục tiêu **scale = 0** trong `BigDecimal` (không còn chữ số thập phân):

| Chế độ | Quy tắc ở điểm đúng nửa | `2.5` | `-2.5` |
| --- | --- | ---: | ---: |
| `HALF_UP` | Xa số 0 | `3` | `-3` |
| `HALF_DOWN` | Về số 0 | `2` | `-2` |
| `HALF_EVEN` | Chọn láng giềng chẵn | `2` | `-2` |
| `UP` | Xa số 0 với mọi phần lẻ khác 0 | `3` | `-3` |
| `DOWN` | Về số 0 với mọi phần lẻ khác 0 | `2` | `-2` |
| `CEILING` | Hướng `+∞` | `3` | `-2` |
| `FLOOR` | Hướng `-∞` | `2` | `-3` |

Chú ý `CEILING` cũng không phải cách thay thế tổng quát cho `Math.round`: `CEILING` biến `2.1` thành `3`, còn `Math.round(2.1)` trả `2`. Tương tự, `FLOOR` không phải phép làm tròn gần nhất vì `Math.floor(-1.1) == -2.0`.

`HALF_EVEN` chỉ chọn số chẵn khi đúng nửa: `2.5 → 2`, `3.5 → 4`, `-2.5 → -2`, `-3.5 → -4`. Nó không cùng quy tắc với `Math.round`, và `Math.rint(double)` dù có xử lý điểm hòa theo số nguyên chẵn thì **trả `double`**, có thể giữ dấu của số 0, đồng thời vẫn chịu hạn chế biểu diễn nhị phân.

Từ “làm tròn lên” trong hội thoại thường mơ hồ: có thể chỉ `UP` (xa số 0), `CEILING` (về `+∞`) hoặc `HALF_UP` (chỉ xử lý điểm nửa). Trong mã, hãy dùng **tên chế độ cụ thể** thay cho cụm từ chung chung.

## 4. Khác biệt về API và kiểu dữ liệu

### 4.1. `Math.round` trả về kiểu nguyên cố định

Hai overload chuẩn là:

~~~java
int nearestInt = Math.round(2.5f);    // float -> int
long nearestLong = Math.round(2.5d);  // double -> long
~~~

`Math.round` không nhận `RoundingMode` hay số chữ số sau dấu phẩy. Nó làm tròn về **số nguyên**, không có phiên bản `Math.round(x, 2)` chuẩn nào trong JDK để làm tròn hai chữ số thập phân.

Việc trả về `int`/`long` cũng có hệ quả về **tràn miền giá trị**: kết quả vượt giới hạn sẽ bị chặn ở cực trị tương ứng, không thể mang mọi chữ số của đầu vào. Đây không phải là một phép kiểm tra số học chính xác hoặc một cơ chế báo lỗi vượt phạm vi.

### 4.2. `BigDecimal` có thể làm tròn ở scale hoặc precision

~~~java
BigDecimal amount = new BigDecimal("-2.5");
BigDecimal asInteger = amount.setScale(0, RoundingMode.HALF_UP);
// -3: trả về BigDecimal mới, amount không đổi.

BigDecimal price = new BigDecimal("12.345");
BigDecimal cents = price.setScale(2, RoundingMode.HALF_UP);
// 12.35: chính xác hai chữ số sau dấu phẩy.
~~~

`setScale(2, ...)` là yêu cầu **hai chữ số thập phân** (scale 2). Trong khi đó:

~~~java
MathContext mc = new MathContext(3, RoundingMode.HALF_UP);
BigDecimal result = new BigDecimal("123.45").round(mc);
// 123: giữ 3 chữ số có nghĩa, không phải 3 chữ số sau dấu phẩy.
~~~

`MathContext` giới hạn **chữ số có nghĩa của kết quả**, nên `round(mc)` với precision 3 không tương đương `setScale(3, ...)`, cũng không tương đương `Math.round`.

Ở ví dụ nguồn decimal, ưu tiên `new BigDecimal("...")` để giữ chính xác giá trị văn bản. Khi đầu vào đã ở kiểu `double`, `BigDecimal.valueOf(value)` chuyển theo biểu diễn thập phân chuẩn của `Double.toString(value)`; đó là giá trị dựa trên **double đã có**, không phục hồi được thông tin đã mất trong các phép tính trước đó. `new BigDecimal(value)` lại biểu diễn chính xác giá trị nhị phân của `double`, có thể tạo ra chuỗi thập phân dài ngoài dự kiến.

## 5. Chương trình Java 17+ chạy được và đầu ra

Tạo file `RoundingComparison.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class RoundingComparison {
    public static void main(String[] args) {
        double[] examples = {
                2.5, -2.5, 2.4, -2.4,
                1.5, -1.5, 0.5, -0.5
        };

        for (double value : examples) {
            BigDecimal decimal = BigDecimal.valueOf(value);

            System.out.println(value
                    + " | Math.round=" + Math.round(value)
                    + " | HALF_UP=" + decimal.setScale(0, RoundingMode.HALF_UP)
                    + " | HALF_EVEN=" + decimal.setScale(0, RoundingMode.HALF_EVEN));
        }

        System.out.println("float -> int: " + Math.round(2.5f));
        System.out.println("double -> long: " + Math.round(2.5d));

        System.out.println("NaN: " + Math.round(Double.NaN));
        System.out.println("+Infinity: " + Math.round(Double.POSITIVE_INFINITY));
        System.out.println("-Infinity: " + Math.round(Double.NEGATIVE_INFINITY));
        System.out.println("large: " + Math.round(1e30));

        double scaled = 1.005 * 100;
        System.out.println("scaled = " + scaled);
        System.out.println("scaled Math.round = " + Math.round(scaled) / 100.0);

        System.out.println("decimal HALF_UP = "
                + new BigDecimal("1.005")
                    .setScale(2, RoundingMode.HALF_UP).toPlainString());
        System.out.println("decimal HALF_EVEN = "
                + new BigDecimal("1.005")
                    .setScale(2, RoundingMode.HALF_EVEN).toPlainString());

        System.out.println("rint 2.5=" + Math.rint(2.5)
                + ", rint -1.5=" + Math.rint(-1.5));
    }
}
~~~

Chạy:

~~~bash
javac RoundingComparison.java
java RoundingComparison
~~~

Kết quả:

~~~text
2.5 | Math.round=3 | HALF_UP=3 | HALF_EVEN=2
-2.5 | Math.round=-2 | HALF_UP=-3 | HALF_EVEN=-2
2.4 | Math.round=2 | HALF_UP=2 | HALF_EVEN=2
-2.4 | Math.round=-2 | HALF_UP=-2 | HALF_EVEN=-2
1.5 | Math.round=2 | HALF_UP=2 | HALF_EVEN=2
-1.5 | Math.round=-1 | HALF_UP=-2 | HALF_EVEN=-2
0.5 | Math.round=1 | HALF_UP=1 | HALF_EVEN=0
-0.5 | Math.round=0 | HALF_UP=-1 | HALF_EVEN=0
float -> int: 3
double -> long: 3
NaN: 0
+Infinity: 9223372036854775807
-Infinity: -9223372036854775808
large: 9223372036854775807
scaled = 100.49999999999999
scaled Math.round = 1.0
decimal HALF_UP = 1.01
decimal HALF_EVEN = 1.00
rint 2.5=2.0, rint -1.5=-2.0
~~~

**Đọc output:** Bốn hàng `-2.5`, `-1.5`, `-0.5` và `0.5` cho thấy chính sách làm tròn khác nhau. Dòng `0.5` còn giúp phân biệt `HALF_UP` với `HALF_EVEN`. `Math.rint` là ví dụ bổ sung về quy tắc “tie to even”; khác cả kiểu trả về so với `Math.round`.

## 6. Điểm khó: nhị phân `double` khiến “.5” tưởng tượng không đúng .5

Các số thập phân như `0.1`, `1.005` thường **không có biểu diễn chính xác bằng `double`**. Vì vậy kết quả của phép nhân, phép chia trước khi làm tròn có thể nằm hơi lệch phía trên hoặc dưới điểm hòa thập phân mà mắt thường thấy.

Một cách viết phổ biến để lấy hai chữ số sau dấu phẩy:

~~~java
double rounded = Math.round(1.005 * 100) / 100.0;
~~~

Chương trình ở trên cho thấy:

~~~text
scaled = 100.49999999999999
scaled Math.round = 1.0
decimal HALF_UP = 1.01
~~~

`1.005 * 100` dưới dạng `double` tạo `100.49999999999999`, **nhỏ hơn 100.5**, nên `Math.round` trả `100` và sau chia là `1.0`. Ngược lại `new BigDecimal("1.005")` giữ chính xác đầu vào thập phân, nên `setScale(2, HALF_UP)` trả `1.01`. Đây là **hai vấn đề tách biệt**:

1. **Quy tắc hòa nửa:** `Math.round` chọn hướng `+∞`; `HALF_UP` chọn xa số 0.
2. **Biểu diễn đầu vào:** phép tính `double` có thể khiến một điểm hòa thập phân không còn thực sự ở đúng nửa.

Không thể kết luận mọi biểu thức `Math.round(value * 100) / 100.0` đều sai: nó có thể phù hợp khi kết quả gần đúng kiểu `double` là chấp nhận được, phạm vi nhỏ và quy tắc ở điểm nửa đã được thỏa thuận. Nhưng với giá tiền hoặc quy định làm tròn thập phân, cách này thường không diễn đạt đúng hợp đồng nghiệp vụ, chưa kể nguy cơ tràn `int`/`long` nếu nhân hệ số lớn.

Khi dùng `BigDecimal` cho tiền hoặc định lượng thập phân theo hợp đồng, hãy xác định rõ: **đầu vào từ đâu, bao nhiêu chữ số sau dấu phẩy, làm tròn theo mode nào, và ở bước nào**. Ví dụ:

~~~java
BigDecimal amount = new BigDecimal("1.005");
BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
// 1.01
~~~

Nếu phép toán trước đó đã làm việc trên `double` và bị mất chính xác, chỉ chuyển sang `BigDecimal` ở bước cuối **không bảo đảm** khôi phục giá trị thập phân ban đầu.

## 7. `NaN`, vô cực, số rất lớn và số âm bằng 0

Tài liệu Java quy định các trường hợp đặc biệt cho `Math.round`:

- Đầu vào `NaN` → `0`.
- `Math.round(double)` với `+Infinity` hoặc giá trị quá lớn dương → `Long.MAX_VALUE`; với `-Infinity` hoặc quá lớn âm → `Long.MIN_VALUE`.
- `Math.round(float)` áp dụng tương tự với `Integer.MAX_VALUE` và `Integer.MIN_VALUE`.
- `Math.round(-0.0)` trả số nguyên `0`; kiểu nguyên không giữ dấu của zero.

Đây là **giá trị trả về theo đặc tả**, không phải ngoại lệ và cũng không chứng minh rằng đầu vào hợp lệ cho nghiệp vụ. Ví dụ, `Math.round(Double.NaN) == 0` có thể làm mất tín hiệu dữ liệu lỗi nếu bạn coi `0` là một số bình thường.

Đầu vào cần làm tròn chính xác sang `long`? Với `BigDecimal`, sau khi làm tròn theo mode đã chọn, `longValueExact()` ném `ArithmeticException` nếu giá trị không thể biểu diễn chính xác dưới dạng `long`. Có thể kiểm tra rõ `Double.isFinite(value)` trước khi đưa `double` qua pipeline. Hãy chọn rõ chính sách xử lý vô cực, `NaN` và quá miền thay vì dựa ngầm vào cơ chế chặn của `Math.round`.

## 8. Chọn phương án theo yêu cầu

| Yêu cầu | API thường phù hợp | Điểm cần xác nhận |
| --- | --- | --- |
| Giá trị `float`/`double` sang số nguyên gần nhất, đúng nửa hướng `+∞` | `Math.round(value)` | Nhận `int` hoặc `long`; kiểm tra `NaN`, vô cực và quá miền nếu dữ liệu yêu cầu. |
| Thập phân đúng nửa **xa số 0** | `BigDecimal.setScale(scale, HALF_UP)` | Scale và cách tạo `BigDecimal` phải phản ánh đầu vào mong muốn. |
| Thập phân đúng nửa về số 0 | `setScale(scale, HALF_DOWN)` | Khác `Math.round` ở điểm nửa dương. |
| Thập phân đúng nửa chọn chữ số chẵn | `setScale(scale, HALF_EVEN)` | Dùng khi hợp đồng nghiệp vụ cho phép; không mặc định cho mọi ứng dụng. |
| Mọi phần lẻ đều làm tròn về `+∞` | `CEILING` hoặc `Math.ceil` theo kiểu đầu vào | Khác làm tròn đến số gần nhất. |
| Làm tròn đến 2 chữ số **sau dấu phẩy** | `setScale(2, mode)` | Không nhầm với `MathContext(2, mode)` chỉ 2 chữ số có nghĩa. |
| Cần giữ chữ số có nghĩa tối đa theo phép toán thập phân | `MathContext(precision, mode)` | Precision khác scale; không tương đương `Math.round`. |
| Phải phát hiện làm tròn ngoài yêu cầu | `setScale(scale, UNNECESSARY)` | Ném ngoại lệ nếu loại bỏ chữ số khác 0. |

Trường hợp chuyển từ decimal đã làm tròn về kiểu nguyên:

~~~java
BigDecimal input = new BigDecimal("-2.5");
long result = input.setScale(0, RoundingMode.HALF_UP).longValueExact();
// result == -3L
~~~

Đoạn này phải nằm trong lớp/phương thức hợp lệ có các import của `java.math`; đây là **đoạn minh họa**, không phải chương trình độc lập.

## 9. Checklist review và lỗi thường gặp

1. **Chỉ thử số dương** như `1.5`, `2.5` rồi kết luận `Math.round == HALF_UP`. Phải thử ít nhất `-0.5`, `-1.5`, `-2.5`.
2. **Hiểu “UP” là hướng số lớn hơn.** `HALF_UP` đi xa số 0 khi đúng nửa; `CEILING` mới là hướng `+∞`.
3. **Viết `Math.round(x * 100) / 100` và tưởng kết quả hai chữ số thập phân kiểu decimal.** `/ 100` với hai toán hạng nguyên có thể còn chia nguyên; `/ 100.0` cho `double`, vẫn có sai số nhị phân.
4. **Cho rằng `Math.round` nhận mọi độ chính xác.** Nó chỉ làm tròn `float`/`double` sang `int`/`long`.
5. **Bỏ qua `NaN`, vô cực hoặc tràn miền.** API trả 0/các cực trị thay vì báo lỗi, có thể che khuất dữ liệu bất thường.
6. **Dùng `new BigDecimal(double)` vô điều kiện.** Constructor này phản ánh đúng giá trị nhị phân của `double`, không nhất thiết là chuỗi thập phân người dùng nhìn thấy.
7. **So `Math.round` với `setScale(2)` rồi quy khác biệt cho rounding mode.** Phải đồng nhất đích làm tròn (ở ví dụ đầu bài là scale 0) trước khi so sánh.
8. **Làm tròn ở mỗi bước không có chủ đích.** Kết quả có thể khác khi chỉ làm tròn một lần ở cuối; cần quy định rõ thời điểm làm tròn.
9. **Nhầm `Math.rint` là `Math.round`.** Cả hai nhận `double`, nhưng `rint` trả `double` và chọn số chẵn ở điểm hòa, `round` trả `long` và chọn về `+∞` ở điểm hòa.

## 10. Bài tập tự kiểm tra

**Bài 1.** Dự đoán `Math.round(-3.5)` và `new BigDecimal("-3.5").setScale(0, HALF_UP)`.

**Đáp án:** `-3` và `-4`. Đầu vào cách đều `-3` và `-4`, nhưng chọn hướng khác nhau.

**Bài 2.** Hai biểu thức này có luôn tương đương không?

~~~java
Math.round(x)
BigDecimal.valueOf(x).setScale(0, RoundingMode.HALF_UP)
~~~

**Đáp án:** Không. Chúng khác ở các giá trị âm đúng nửa; ngoài ra khác loại kết quả và xử lý giá trị đặc biệt, miền số, precision/biểu diễn.

**Bài 3.** Tại sao `Math.round(1.005 * 100) / 100.0` có thể ra `1.0`, còn `new BigDecimal("1.005").setScale(2, HALF_UP)` ra `1.01`?

**Đáp án:** Phép nhân trên `double` tạo `100.49999999999999`, nhỏ hơn điểm nửa `100.5`; còn chuỗi thập phân trong `BigDecimal` giữ giá trị `1.005` chính xác.

**Bài 4.** Muốn làm tròn `-2.5` về `-2` theo quy tắc chọn số nguyên gần nhất và hòa về phía `+∞`, gọi API nào?

**Đáp án:** `Math.round(-2.5d)`. Nếu muốn trình bày quy tắc cho decimal, có thể tính bằng `BigDecimal` với một giải pháp đặt mode phù hợp theo điều kiện; không được mặc định gọi `HALF_UP`.

## 11. Tài liệu Java để đối chiếu

- [`Math.round(float)`, `Math.round(double)` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html#round(double)): định nghĩa số nguyên gần nhất và trường hợp hòa hướng `+∞`; các trường hợp đặc biệt.
- [`RoundingMode` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): mô tả `HALF_UP`, `HALF_DOWN`, `HALF_EVEN`, `UP`, `DOWN`, `CEILING` và `FLOOR`.
- [`BigDecimal` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): `setScale`, `round`, `valueOf` và `longValueExact`.

**Ghi nhớ:** `Math.round` = gần nhất, **hòa về `+∞`**; `HALF_UP` = gần nhất, **hòa xa số 0**. Với số âm ở điểm `.5`, kết quả thường khác. Hãy kiểm soát thêm kiểu dữ liệu và biểu diễn nhị phân trước khi dùng một phép làm tròn để thay thế phép khác.


</details>

- [Quay lại đầu trang](#back-to-top)
