<a id="back-to-top"></a>

# Chuyển đổi BigDecimal

## Menu
- [toString và dạng khoa học khi phù hợp](#big-decimal-conversions-001)
- [toPlainString và bảo toàn cách trình bày mong muốn](#big-decimal-conversions-002)
- [toEngineeringString và số mũ theo bội của 3](#big-decimal-conversions-003)
- [intValue, longValue và phần dữ liệu có thể mất](#big-decimal-conversions-004)
- [Các phương thức *ValueExact kiểm tra phần lẻ và giới hạn](#big-decimal-conversions-005)
- [toBigInteger khác toBigIntegerExact](#big-decimal-conversions-006)
- [floatValue, doubleValue và khả năng làm tròn hoặc thành vô cực](#big-decimal-conversions-007)
- [Parse và format BigDecimal theo locale](#big-decimal-conversions-008)
- [Phân biệt giá trị tính toán với scale trình bày](#big-decimal-conversions-009)
- [Thiết kế dạng trao đổi giữ số mà không đi qua double trung gian](#big-decimal-conversions-010)

## <a id="big-decimal-conversions-001">toString và dạng khoa học khi phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.toString() và dạng khoa học khi phù hợp

## Mục tiêu

Khi chuyển một `BigDecimal` thành chuỗi trong Java, phương thức `toString()` không phải lúc nào cũng tạo ra một số viết đầy đủ theo dạng thập phân thông thường. Nó có thể chọn **dạng khoa học** (*scientific notation*) như `1E-7` hoặc `1E+6`. Hành vi này có quy tắc rõ ràng, phụ thuộc vào **giá trị không chia tỷ lệ**, **scale** và **precision** của `BigDecimal`.

Sau bài này, bạn có thể giải thích khi nào `toString()` sinh dạng khoa học, chọn đúng giữa `toString()`, `toPlainString()` và `toEngineeringString()`, và hiểu tác động của việc chuyển chuỗi ngược lại thành `BigDecimal`.

## 1. BigDecimal lưu giá trị như thế nào?

`BigDecimal` biểu diễn một số bằng hai thành phần chính:

- **Unscaled value**: số nguyên chứa các chữ số có nghĩa, lấy bằng `unscaledValue()`.
- **Scale**: số chữ số thập phân về mặt biểu diễn, lấy bằng `scale()`. Scale có thể dương, bằng 0 hoặc âm.

Giá trị số học được tính theo công thức:

\[
\text{value} = \text{unscaledValue} \times 10^{-\text{scale}}
\]

Ví dụ:

| Chuỗi tạo BigDecimal | Unscaled value | Scale | Giá trị |
|---|---:|---:|---|
| `"123.4500"` | `1234500` | `4` | `123.4500` |
| `"0.000001"` | `1` | `6` | `0.000001` |
| `"0.0000001"` | `1` | `7` | `0.0000001` |
| `"1E+3"` | `1` | `-3` | `1000` |
| `"1000"` | `1000` | `0` | `1000` |

Hai đối tượng `new BigDecimal("1E+3")` và `new BigDecimal("1000")` **bằng nhau về giá trị số học**, nhưng khác scale và unscaled value. Vì vậy `compareTo()` có thể trả `0` trong khi `equals()` trả `false`.

**Precision** là số chữ số trong giá trị không chia tỷ lệ nếu không tính dấu và số 0 dẫn đầu (riêng giá trị 0 có precision bằng 1). Ví dụ `123.4500` có precision `7`, scale `4`; `1E+3` có precision `1`, scale `-3`. Đừng nhầm precision với số chữ số sau dấu thập phân.

## 2. toString() sinh dạng thập phân hay dạng khoa học?

`BigDecimal.toString()` tạo **biểu diễn chuỗi chuẩn (canonical)** của cặp unscaled value và scale. Phương thức bảo toàn giá trị, scale và các số 0 cuối có ý nghĩa đối với biểu diễn; nó **không làm tròn** và không chuyển qua `double`.

Để hiểu quy tắc hiển thị, xét **adjusted exponent** của `BigDecimal`:

\[
\text{adjustedExponent} = \text{precision} - \text{scale} - 1
\]

Với `BigDecimal` khác 0, adjusted exponent cho biết lũy thừa của 10 ứng với chữ số có nghĩa đầu tiên. Cơ chế chuẩn của `toString()` chọn:

1. **Dạng thập phân thông thường** nếu **scale không âm** (`scale >= 0`) **và adjusted exponent từ -6 trở lên** (`adjustedExponent >= -6`).
2. **Dạng khoa học** trong các trường hợp còn lại, với một chữ số có nghĩa đứng trước dấu chấm, phần còn lại (nếu có) đứng sau, rồi ký hiệu `E` và số mũ.

Với số mũ dương, Java thêm dấu `+` sau `E`, chẳng hạn `1E+3`. Với số mũ âm, Java dùng dấu `-` như `1E-7`. Ở dạng thông thường, dấu thập phân là `.`, không phụ thuộc thiết lập ngôn ngữ của máy.

Hai ví dụ sát ngưỡng rất đáng nhớ:

- `new BigDecimal("0.000001").toString()` tạo `"0.000001"`: precision `1`, scale `6`, adjusted exponent `-6`, nên giữ dạng thông thường.
- `new BigDecimal("0.0000001").toString()` tạo `"1E-7"`: precision `1`, scale `7`, adjusted exponent `-7`, nên chuyển sang dạng khoa học.

Quy tắc còn phụ thuộc **scale**, không chỉ độ lớn của số. `new BigDecimal("1000000").toString()` là `"1000000"` vì scale `0`; `new BigDecimal("1E+6").toString()` là `"1E+6"` vì scale `-6`. Giá trị số học của chúng giống nhau.

### Chuỗi khoa học có nghĩa gì?

Trong cú pháp Java `BigDecimal`:

- `1E-7` nghĩa là \(1 \times 10^{-7}\), tức `0.0000001`.
- `1.23E+4` nghĩa là \(1.23 \times 10^4\), tức `12300`.
- `1E+6` nghĩa là \(1 \times 10^6\), tức `1000000`.

`E` là ký hiệu số mũ cơ số 10, không phải phép tính bằng hằng số toán học Euler. Trong Java, có thể dựng lại giá trị bằng `new BigDecimal("1E-7")`.

## 3. Phân biệt toString(), toPlainString(), toEngineeringString()

Cả ba phương thức **chỉ tạo chuỗi**, không sửa hoặc làm tròn đối tượng `BigDecimal`.

| Phương thức | Mục đích | Ví dụ với `new BigDecimal("0.0000001")` |
|---|---|---|
| `toString()` | Chuỗi chuẩn, có thể dùng dạng khoa học tùy scale và adjusted exponent | `"1E-7"` |
| `toPlainString()` | Chuỗi dạng thập phân đầy đủ, **không có ký hiệu E** | `"0.0000001"` |
| `toEngineeringString()` | Khi viết dưới dạng số mũ, chọn **số mũ là bội số của 3**; hệ số có thể có 1–3 chữ số trước dấu thập phân | `"100E-9"` |

Ví dụ dễ gặp:

| Giá trị được tạo | `toString()` | `toPlainString()` | `toEngineeringString()` |
|---|---|---|---|
| `"123.4500"` | `123.4500` | `123.4500` | `123.4500` |
| `"0.000001"` | `0.000001` | `0.000001` | `0.000001` |
| `"0.0000001"` | `1E-7` | `0.0000001` | `100E-9` |
| `"1E+3"` | `1E+3` | `1000` | `1E+3` |
| `"1.23E+4"` | `1.23E+4` | `12300` | `12.3E+3` |
| `"1000000"` | `1000000` | `1000000` | `1000000` |
| `"1000000.00"` | `1000000.00` | `1000000.00` | `1000000.00` |

`100E-9` và `1E-7` có cùng giá trị vì \(100 \times 10^{-9} = 10^{-7}\). Dạng kỹ thuật có ích khi trình bày bậc độ lớn theo nhóm nghìn, triệu, tỷ; còn dạng khoa học tiêu chuẩn của `toString()` ưu tiên một chữ số trước dấu chấm.

### Chọn phương thức nào?

- **Ghi lại biểu diễn BigDecimal để có thể đọc lại chính xác cả value/scale**: dùng `toString()`. Chuỗi chuẩn chứa đủ thông tin để `new BigDecimal(original.toString()).equals(original)` là `true`.
- **Giao diện, log hoặc trường văn bản yêu cầu không có E**: dùng `toPlainString()`, nhưng kiểm tra độ dài vì số có scale rất lớn về độ lớn tuyệt đối có thể tạo chuỗi dài.
- **Hiển thị số mũ theo bội của 3**: dùng `toEngineeringString()` nếu đây là quy tắc trình bày mong muốn.

Với số lượng tiền có cách hiển thị cố định, phải xác định **scale/làm tròn** riêng trước khi tạo chuỗi. `toPlainString()` chỉ đổi cách biểu diễn, không tự áp dụng hai chữ số phần lẻ. Ví dụ `new BigDecimal("12.345").toPlainString()` vẫn là `"12.345"`.

## 4. Chương trình Java 17+ có thể chạy ngay

Lưu dưới tên **`BigDecimalStringDemo.java`**:

```java
import java.math.BigDecimal;

public class BigDecimalStringDemo {
    private static void show(String source) {
        BigDecimal number = new BigDecimal(source);

        System.out.println(source
                + " | " + number.toString()
                + " | " + number.toPlainString()
                + " | " + number.toEngineeringString()
                + " | " + number.precision()
                + " | " + number.scale());
    }

    public static void main(String[] args) {
        System.out.println("input | toString | plain | engineering | precision | scale");

        for (String source : new String[] {
                "123.4500",
                "0.000001",
                "0.0000001",
                "1E+3",
                "1.23E+4",
                "1000000",
                "1000000.00"
        }) {
            show(source);
        }

        BigDecimal withZeros = new BigDecimal("1000000.00");
        BigDecimal stripped = withZeros.stripTrailingZeros();
        System.out.println("stripTrailingZeros: " + stripped.toString());

        BigDecimal scientific = new BigDecimal("1E+3");
        BigDecimal fromCanonical = new BigDecimal(scientific.toString());
        BigDecimal fromPlain = new BigDecimal(scientific.toPlainString());

        System.out.println("canonical equals: " + scientific.equals(fromCanonical));
        System.out.println("plain equals: " + scientific.equals(fromPlain));
        System.out.println("plain compareTo: " + scientific.compareTo(fromPlain));
    }
}
```

Biên dịch và chạy:

```bash
javac BigDecimalStringDemo.java
java BigDecimalStringDemo
```

Kết quả chính xác:

```text
input | toString | plain | engineering | precision | scale
123.4500 | 123.4500 | 123.4500 | 123.4500 | 7 | 4
0.000001 | 0.000001 | 0.000001 | 0.000001 | 1 | 6
0.0000001 | 1E-7 | 0.0000001 | 100E-9 | 1 | 7
1E+3 | 1E+3 | 1000 | 1E+3 | 1 | -3
1.23E+4 | 1.23E+4 | 12300 | 12.3E+3 | 3 | -2
1000000 | 1000000 | 1000000 | 1000000 | 7 | 0
1000000.00 | 1000000.00 | 1000000.00 | 1000000.00 | 9 | 2
stripTrailingZeros: 1E+6
canonical equals: true
plain equals: false
plain compareTo: 0
```

**Cách đọc kết quả:** Dòng `0.0000001` cho thấy `toString()` có thể tự chọn dạng mũ khi adjusted exponent nhỏ hơn `-6`. Dòng `1E+3` cho thấy `toPlainString()` biến giá trị thành `1000` nhưng **không giữ scale âm** sau khi parse chuỗi trở lại. Dòng `stripTrailingZeros` minh họa rằng cùng giá trị `1000000` có thể được biểu diễn bằng dạng mũ khi scale thay đổi.

## 5. Vì sao toPlainString() không luôn phù hợp với round-trip của BigDecimal?

Đối với `BigDecimal`, có hai cách so sánh khác nhau:

- `compareTo()` so sánh **giá trị số học**; `new BigDecimal("1E+3").compareTo(new BigDecimal("1000")) == 0`.
- `equals()` yêu cầu cùng **giá trị số học và scale**; hai đối tượng trên không `equals` vì scale lần lượt `-3` và `0`.

Xét:

```java
BigDecimal original = new BigDecimal("1E+3");
BigDecimal parsedCanonical = new BigDecimal(original.toString());
BigDecimal parsedPlain = new BigDecimal(original.toPlainString());

System.out.println(original.scale());             // -3
System.out.println(parsedCanonical.scale());      // -3
System.out.println(parsedPlain.scale());          // 0
System.out.println(original.equals(parsedPlain)); // false
System.out.println(original.compareTo(parsedPlain) == 0); // true
```

`toString()` là lựa chọn chuẩn khi cần **round-trip tái lập cùng biểu diễn BigDecimal**, bao gồm scale. `toPlainString()` phù hợp hơn khi cần chuỗi dễ đọc không có ký hiệu khoa học. Nếu ứng dụng chỉ quan tâm đến giá trị số học, `compareTo()` phản ánh quan hệ số học phù hợp hơn `equals()`; nếu scale là một phần hợp đồng dữ liệu, hãy giữ thông tin scale hoặc dùng `toString()`.

Chú ý `System.out.println(number)`, phép ghép chuỗi `"x=" + number` và `String.valueOf(number)` đều gọi `toString()` đối với `BigDecimal` không null. Vì vậy các cách in đó **vẫn có thể hiện dạng khoa học**. Gọi `toPlainString()` trực tiếp nếu đích hiển thị cần dạng đầy đủ.

## 6. stripTrailingZeros() có thể làm xuất hiện E

`stripTrailingZeros()` trả về một `BigDecimal` tương đương về **giá trị số học** nhưng bỏ các số 0 tận cùng trong biểu diễn unscaled value khi có thể. Kết quả có thể có **scale âm**, làm `toString()` đổi sang dạng khoa học.

Ví dụ:

```java
BigDecimal before = new BigDecimal("1000000.00");
BigDecimal after = before.stripTrailingZeros();

System.out.println(before.toString());  // 1000000.00
System.out.println(before.scale());     // 2
System.out.println(after.toString());   // 1E+6
System.out.println(after.scale());      // -6
System.out.println(after.toPlainString()); // 1000000
```

Đây **không phải sai số và không phải phép làm tròn**. Giá trị vẫn bằng một triệu; chính biểu diễn unscaled value/scale đã thay đổi. `stripTrailingZeros()` cũng không sửa đối tượng `before` vì `BigDecimal` bất biến.

Điều này hay gây nhầm lẫn khi một hàm chuẩn hóa dữ liệu gọi `stripTrailingZeros()` rồi nơi khác vô tình dùng `toString()` để xuất kết quả. Nếu yêu cầu đầu ra là chuỗi không E, chọn `toPlainString()` sau khi có đối tượng kết quả. Nếu yêu cầu giữ nguyên số chữ số thập phân ban đầu (ví dụ `1000000.00`), **đừng bỏ trailing zeros trước khi lưu biểu diễn**.

## 7. Các trường hợp biên và sai lầm thường gặp

### Coi dạng khoa học là mất giá trị hoặc mất độ chính xác

`1E-7` và `0.0000001` đều là giá trị thập phân chính xác \(10^{-7}\). `toString()` không rút gọn chữ số có nghĩa theo một precision mới. Nó chỉ chọn cách viết tương ứng với unscaled value/scale hiện có. Đừng dùng `doubleValue()` rồi chuyển sang chuỗi để “né E”: `double` có miền biểu diễn và độ chính xác nhị phân khác `BigDecimal`.

### Nghĩ rằng BigDecimal lớn tự động phải in khoa học

`new BigDecimal("1000000")` in `1000000`; `new BigDecimal("1E+6")` in `1E+6` dù giá trị bằng nhau. Điều kiện của `toString()` là **scale và adjusted exponent**, không phải một ngưỡng giá trị tuyệt đối duy nhất.

### Xóa số 0 cuối rồi kỳ vọng cùng scale

`new BigDecimal("12.3400").toString()` là `"12.3400"` vì scale `4` vẫn được bảo toàn. `stripTrailingZeros()` có thể thay đổi scale; `toPlainString()` không tự khôi phục số 0 biểu diễn ban đầu. Nếu dữ liệu yêu cầu đúng số chữ số, dùng `setScale(scale, roundingMode)` theo quy định nghiệp vụ trước khi xuất chuỗi. Phương thức `setScale` có thể cần làm tròn và yêu cầu `RoundingMode`; khác về chức năng so với ba phương thức chỉ tạo chuỗi.

### Dùng chuỗi để quyết định số nào lớn hơn

So sánh chuỗi `"1E+3"` và `"900"` theo thứ tự từ điển không tương đương so sánh số. Hãy so sánh chính các đối tượng `BigDecimal` bằng `compareTo()` trước, rồi chuyển sang chuỗi khi cần hiển thị hoặc truyền dữ liệu.

### Quên yêu cầu định dạng theo vùng miền

`toString()`, `toPlainString()` và `toEngineeringString()` đều dùng ký hiệu thập phân kiểu Java (dấu chấm và ký hiệu E khi có), **không tự đổi** thành dấu phẩy theo locale hoặc thêm dấu phân cách hàng nghìn. Nếu giao diện cần quy tắc trình bày theo vùng miền, dùng cơ chế định dạng như `NumberFormat`/`DecimalFormat`, cấu hình locale và làm tròn phù hợp. Việc định dạng để hiển thị cần được phân biệt với biểu diễn chuẩn dùng để lưu/đọc lại BigDecimal.

### Không chú ý kích thước đầu ra

`toPlainString()` luôn cố tạo dạng đầy đủ; với scale có độ lớn rất lớn, chuỗi có thể chứa số lượng chữ số hoặc số 0 rất lớn. Chỉ chọn dạng đầy đủ khi thật sự cần và dữ liệu đầu vào có giới hạn phù hợp. Dạng mũ của `toString()` ngắn hơn trong nhiều trường hợp, nhưng không phải cơ chế giới hạn dung lượng đầu ra.

## 8. Quy trình chọn cách chuyển chuỗi

1. **Cần lưu và khôi phục chính xác đối tượng BigDecimal, kể cả scale?** Dùng `toString()` rồi đọc bằng `new BigDecimal(text)`. Đây là biểu diễn chuẩn phù hợp cho round-trip.
2. **Cần hiển thị đầy đủ, không được có E?** Dùng `toPlainString()`; tự quy định scale/làm tròn và độ dài đầu ra nếu nghiệp vụ đòi hỏi.
3. **Cần ký pháp lũy thừa với số mũ theo bội của 3?** Dùng `toEngineeringString()`.
4. **Bất ngờ thấy E sau một bước chuẩn hóa?** Kiểm tra `stripTrailingZeros()` và `scale()` trước/sau.
5. **Hai chuỗi khác nhau nhưng mô tả cùng một số?** Kiểm tra cả `compareTo()` và `scale()`; không mặc định `equals()` phản ánh riêng giá trị số học.

**Tự kiểm tra:** Tại sao `new BigDecimal("0.000001").toString()` là `"0.000001"`, còn `new BigDecimal("0.0000001").toString()` là `"1E-7"`? Vì adjusted exponent lần lượt là `-6` và `-7`, trong khi scale đều không âm. Và tại sao `new BigDecimal("1E+3").toPlainString()` tạo `"1000"` nhưng đọc lại có thể `equals() == false`? Vì chuỗi thông thường làm mất scale âm `-3`, đối tượng đọc lại có scale `0` dù giá trị số học không đổi.

**Ghi nhớ:** `BigDecimal.toString()` chọn dạng khoa học dựa trên scale và adjusted exponent, bảo toàn biểu diễn để round-trip; `toPlainString()` bảo đảm không có E; `toEngineeringString()` dùng số mũ theo bội số của 3. Cả ba **không thực hiện làm tròn**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-conversions-002">toPlainString và bảo toàn cách trình bày mong muốn</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.toPlainString() và bảo toàn cách trình bày mong muốn

## 1. Mục tiêu: xuất số thập phân ở dạng thông thường

`BigDecimal` trong Java biểu diễn số thập phân có độ chính xác tùy ý. Một số có thể được xuất dưới dạng **ký pháp khoa học** như `1E+3` hoặc `1.2E-7`. Khi cần một chuỗi số thập phân thông thường, **không chứa `E` hoặc `e` để biểu diễn số mũ**, hãy dùng `toPlainString()`:

```java
BigDecimal large = new BigDecimal("1E+3");
BigDecimal small = new BigDecimal("1.2E-7");

System.out.println(large.toString());      // 1E+3
System.out.println(large.toPlainString()); // 1000
System.out.println(small.toString());      // 1.2E-7
System.out.println(small.toPlainString()); // 0.00000012
```

`toPlainString()` chỉ **chuyển cách biểu diễn của giá trị thành `String`**. Nó không làm tròn, không thay đổi `BigDecimal` nguồn, không thêm dấu phân cách hàng nghìn và không tự chọn số lượng chữ số thập phân theo nghiệp vụ. Muốn “bảo toàn cách trình bày mong muốn” cần phân biệt rõ **giá trị**, **scale**, **cách chuyển ra chuỗi** và **định dạng dành cho người đọc**.

> Các đoạn mã minh họa rời bên dưới sử dụng `import java.math.BigDecimal;` và, khi có làm tròn, `import java.math.RoundingMode;`. Chương trình Java 17+ đầy đủ có lệnh biên dịch và đầu ra nằm ở mục 7.

## 2. Ba cách chuyển BigDecimal sang chuỗi

Ba phương thức liên quan có mục đích khác nhau:

| Phương thức | Cách biểu diễn | Điểm cần nhớ |
| --- | --- | --- |
| `toString()` | Chuỗi chuẩn (*canonical*) của `BigDecimal`, có thể dùng ký pháp khoa học. | Đặc tả bảo đảm chuỗi thể hiện phân biệt được cặp `unscaledValue` và `scale`; có thể dùng để tái tạo cùng trạng thái số học và scale qua constructor nhận `String`. |
| `toPlainString()` | Dạng thập phân thông thường, không dùng số mũ. | Dễ đọc hoặc phù hợp nơi yêu cầu decimal text không số mũ; khi scale âm, chuyển lại từ chuỗi này có thể **khác scale**. |
| `toEngineeringString()` | Có thể dùng ký pháp kỹ thuật, với số mũ (khi có) là bội số của 3. | Hữu ích cho biểu diễn kỹ thuật; không phải lựa chọn thay cho `toPlainString()` nếu đầu ra cấm ký pháp số mũ. |

Ví dụ:

```java
BigDecimal a = new BigDecimal("123.4500");
BigDecimal b = new BigDecimal("1E+3");
BigDecimal c = new BigDecimal("1.2E-7");

System.out.println(a.toString());      // 123.4500
System.out.println(a.toPlainString()); // 123.4500
System.out.println(b.toString());      // 1E+3
System.out.println(b.toPlainString()); // 1000
System.out.println(c.toString());      // 1.2E-7
System.out.println(c.toPlainString()); // 0.00000012
```

Với nhiều giá trị thông thường, `toString()` và `toPlainString()` có thể giống hệt. Sự khác biệt nổi bật khi đối tượng có **scale âm** hoặc giá trị đủ nhỏ để `toString()` chọn ký pháp khoa học theo quy tắc biểu diễn của `BigDecimal`.

`String.valueOf(bigDecimal)` và phép nối chuỗi như `"value=" + bigDecimal` gọi đến cách biểu diễn `toString()` thông qua chuyển đối tượng sang chuỗi. Nếu yêu cầu cụ thể là **không số mũ**, hãy gọi `toPlainString()` tường minh.

## 3. Vì sao xuất hiện E+3 hoặc E-7? Vai trò của scale

`BigDecimal` được xác định bởi một số nguyên không xét dấu phẩy (*unscaled value*) và một số nguyên `scale`:

```text
giá trị = unscaledValue × 10^(-scale)
```

Ví dụ:

| Khởi tạo | Trị số | `scale()` | `toString()` | `toPlainString()` |
| --- | --- | ---: | --- | --- |
| `new BigDecimal("12.3400")` | 12.3400 | 4 | `12.3400` | `12.3400` |
| `new BigDecimal("1E+3")` | 1000 | -3 | `1E+3` | `1000` |
| `new BigDecimal("1.2E-7")` | 0.00000012 | 8 | `1.2E-7` | `0.00000012` |
| `new BigDecimal("0.00")` | 0 | 2 | `0.00` | `0.00` |

`scale` không phải số chữ số có nghĩa (*precision*). Nó cho biết vị trí dấu thập phân đối với `unscaledValue`. Scale **có thể âm**: `1E+3` biểu diễn `1 × 10³` và có scale `-3`. Khi chuyển sang dạng plain, `toPlainString()` viết đủ chữ số 0 thành `1000`.

Với scale không âm, `toPlainString()` in vị trí dấu chấm thập phân phù hợp, kể cả các chữ số 0 cuối do scale quy định; ví dụ `12.3400` vẫn là `12.3400`. Đây là cách giữ **biểu diễn decimal đã có trong đối tượng** khi cần hiển thị đúng số lượng chữ số lẻ đang lưu.

Điều đó khác với việc khôi phục **chính xác chuỗi nhập ban đầu**: `BigDecimal` không lưu mọi ký tự, dấu `+`, số 0 ở đầu hoặc kiểu viết số mũ trong văn bản gốc.

## 4. toPlainString() không làm tròn và không tự cố định chữ số thập phân

So sánh ba nhu cầu riêng biệt:

1. **Không dùng số mũ:** `value.toPlainString()`.
2. **Buộc kết quả có đúng hai chữ số sau dấu chấm:** trước hết chọn `value.setScale(2, roundingMode)`, sau đó mới `toPlainString()`.
3. **Hiển thị cho con người với dấu phân nhóm, dấu thập phân theo ngôn ngữ hoặc ký hiệu tiền tệ:** dùng công cụ định dạng như `NumberFormat` hoặc `DecimalFormat` với `Locale` và quy tắc làm tròn phù hợp.

Ví dụ:

```java
BigDecimal amount = new BigDecimal("12.3");

System.out.println(amount.toPlainString());           // 12.3
System.out.println(amount.setScale(2).toPlainString()); // 12.30
System.out.println(amount.toPlainString());           // 12.3
```

`setScale(2)` ở đây tăng scale từ 1 lên 2 nên không phải làm tròn. Phương thức trả về **`BigDecimal` kết quả mới về mặt ngữ nghĩa**; đối tượng `amount` ban đầu không thay đổi vì `BigDecimal` bất biến. Không nên giả định JVM luôn tạo tham chiếu vật lý khác, đặc biệt khi scale yêu cầu đã đúng.

Nếu cần giảm scale mà phải loại chữ số khác 0, overload `setScale(int)` sẽ ném `ArithmeticException`. Khi nghiệp vụ cho phép làm tròn, nêu rõ `RoundingMode`:

```java
BigDecimal raw = new BigDecimal("12.345");

System.out.println(raw.toPlainString()); // 12.345
System.out.println(
        raw.setScale(2, RoundingMode.HALF_UP).toPlainString()
); // 12.35
```

**Không làm tròn vô tình trong bước trình bày.** `toPlainString()` tự nó không thay giá trị; lời gọi `setScale(2, HALF_UP)` đã chủ động áp dụng quy tắc làm tròn. Với tiền tệ, thuế, thống kê hoặc dữ liệu lưu trữ, cần xác định rõ thời điểm và phương pháp làm tròn theo hợp đồng nghiệp vụ.

## 5. “Bảo toàn cách trình bày” có nhiều mức khác nhau

Cần xác định chính xác thứ muốn giữ:

### 5.1. Giữ trị số toán học

Hai chuỗi `1E+3` và `1000` mô tả cùng một số. Trong phép so sánh trị số `BigDecimal.compareTo()`, chúng bằng nhau:

```java
BigDecimal scientific = new BigDecimal("1E+3");
BigDecimal plain = new BigDecimal("1000");

System.out.println(scientific.compareTo(plain)); // 0
```

Nếu giao thức chỉ yêu cầu trị số decimal chính xác và cấm số mũ, `toPlainString()` có thể phù hợp.

### 5.2. Giữ scale đang có khi scale không âm

`new BigDecimal("12.3400").toPlainString()` cho `"12.3400"`; hai chữ số 0 cuối vẫn xuất hiện. Giá trị `new BigDecimal("12.34")` chỉ in `"12.34"` vì scale của nó là 2, không phải 4. `toPlainString()` phản ánh trạng thái biểu diễn đã có, **không tự khôi phục** số chữ số 0 mà một bước xử lý trước đó đã bỏ.

Nếu phải luôn in hai chữ số thập phân, tạo đối tượng có scale 2 theo quy tắc phù hợp trước khi xuất.

### 5.3. Giữ nguyên cả chữ viết người dùng đã nhập

`BigDecimal` không lưu bản sao nguyên văn chuỗi nguồn. Ví dụ:

```java
BigDecimal x = new BigDecimal("00012.300");
System.out.println(x.toPlainString()); // 12.300
```

Số 0 ở đầu không còn. Tương tự, cách viết `+12.30`, `12.30` và một số kiểu ký pháp số mũ có thể được chuẩn hóa khi tạo `BigDecimal`. Nếu yêu cầu sản phẩm là **lưu chính xác chuỗi người dùng đã nhập** (chẳng hạn biên bản, đơn hàng có chữ viết gốc, trường văn bản được ký số), phải lưu riêng `String originalInput`, đồng thời phân tích thành `BigDecimal` để tính toán khi cần.

Ví dụ về số 0 âm: `new BigDecimal("-0.00").toPlainString()` xuất `"0.00"`, không giữ dấu âm đầu vào. `BigDecimal` biểu diễn giá trị 0, không giữ “negative zero” như một thành phần ngữ nghĩa riêng của dữ liệu decimal.

### 5.4. Giữ scale âm khi chuyển qua chuỗi rồi đọc lại

Đây là trường hợp **dễ bị bỏ sót**:

```java
BigDecimal original = new BigDecimal("1E+3");     // scale = -3
String plainText = original.toPlainString();      // "1000"
BigDecimal restored = new BigDecimal(plainText);  // scale = 0

System.out.println(original.scale());           // -3
System.out.println(restored.scale());           // 0
System.out.println(original.equals(restored));  // false
System.out.println(original.compareTo(restored)); // 0
```

Hai đối tượng bằng nhau về **trị số**, nhưng `equals()` trả `false` vì `BigDecimal.equals()` xét cả biểu diễn và scale. Không nên dùng `toPlainString()` làm chuỗi *round-trip* khi hợp đồng dữ liệu cần bảo toàn **cả scale âm**.

Ngược lại:

```java
BigDecimal original = new BigDecimal("1E+3");
BigDecimal restored = new BigDecimal(original.toString());

System.out.println(original.equals(restored)); // true
```

`toString()` được thiết kế là biểu diễn chuẩn một-một của cặp unscaled value và scale, thuận lợi cho việc chuyển ra chuỗi rồi khôi phục `BigDecimal` với cùng biểu diễn. Vì vậy, lựa chọn phương thức xuất cần xuất phát từ yêu cầu **hiển thị không số mũ** hay **bảo toàn dữ liệu BigDecimal khi truyền/lưu**.

## 6. Tác động của stripTrailingZeros() và các phép chuyển đổi trước khi xuất

`stripTrailingZeros()` bỏ các chữ số 0 cuối có thể bỏ mà không thay đổi giá trị số học, rồi trả về `BigDecimal`. Nó có thể thay đổi scale sang **âm**:

```java
BigDecimal amount = new BigDecimal("1000");
BigDecimal stripped = amount.stripTrailingZeros();

System.out.println(amount.toPlainString());   // 1000
System.out.println(stripped.toString());      // 1E+3
System.out.println(stripped.toPlainString()); // 1000
System.out.println(stripped.scale());         // -3
```

Nếu sau khi `stripTrailingZeros()` lại gọi `toString()`, ký pháp khoa học có thể xuất hiện dù đầu vào ban đầu là số thông thường. `toPlainString()` tạo dạng không số mũ, nhưng **không phục hồi scale và dấu 0 cuối của biểu diễn gốc** đã bị `stripTrailingZeros()` loại bỏ.

Ví dụ:

```java
BigDecimal original = new BigDecimal("12.3400");
BigDecimal stripped = original.stripTrailingZeros();

System.out.println(original.toPlainString()); // 12.3400
System.out.println(stripped.toPlainString()); // 12.34
```

Nếu hợp đồng hiển thị đòi đúng bốn vị trí thập phân, đừng gọi `stripTrailingZeros()` trước khi xuất; hoặc áp dụng `setScale(4)` khi việc nâng scale là có chủ đích. Nên định nghĩa một nơi chịu trách nhiệm cho việc “chuẩn hóa số để lưu” và một nơi cho việc “định dạng số để xem”, thay vì trộn các thao tác này trong nhiều lớp.

### Tránh tạo BigDecimal từ double nếu yêu cầu decimal chính xác

`new BigDecimal(0.1)` biểu diễn chính xác giá trị nhị phân của `double` literal `0.1`, vốn là một xấp xỉ của số thập phân 0.1. `toPlainString()` **không sửa được** sai khác đầu vào đó. Ưu tiên:

```java
BigDecimal fromText = new BigDecimal("0.1");
BigDecimal fromDouble = BigDecimal.valueOf(0.1);

System.out.println(fromText.toPlainString());   // 0.1
System.out.println(fromDouble.toPlainString()); // 0.1
```

`BigDecimal.valueOf(double)` sử dụng biểu diễn chuỗi của `double` để dựng `BigDecimal`; điều đó thường phù hợp cho literal như `0.1`, nhưng không thể khôi phục chữ số thập phân đã bị mất bởi những phép tính `double` trước đó.

## 7. Chương trình Java 17+ chạy độc lập

Lưu toàn bộ mã sau thành `BigDecimalPlainStringDemo.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalPlainStringDemo {
    public static void main(String[] args) {
        BigDecimal thousand = new BigDecimal("1E+3");
        BigDecimal tiny = new BigDecimal("1.2E-7");

        System.out.println("thousand toString = " + thousand.toString());
        System.out.println("thousand plain = " + thousand.toPlainString());
        System.out.println("tiny toString = " + tiny.toString());
        System.out.println("tiny plain = " + tiny.toPlainString());

        BigDecimal entered = new BigDecimal("12.3400");
        System.out.println("entered plain = " + entered.toPlainString());
        System.out.println("stripped plain = "
                + entered.stripTrailingZeros().toPlainString());

        BigDecimal oneDecimal = new BigDecimal("12.3");
        System.out.println("one decimal = " + oneDecimal.toPlainString());
        System.out.println("two decimals = "
                + oneDecimal.setScale(2).toPlainString());

        BigDecimal rounded = new BigDecimal("12.345");
        System.out.println("rounded two decimals = "
                + rounded.setScale(2, RoundingMode.HALF_UP)
                         .toPlainString());

        BigDecimal fromPlain = new BigDecimal(thousand.toPlainString());
        BigDecimal fromCanonical = new BigDecimal(thousand.toString());
        System.out.println("original scale = " + thousand.scale());
        System.out.println("plain round-trip scale = " + fromPlain.scale());
        System.out.println("plain equals original = "
                + thousand.equals(fromPlain));
        System.out.println("canonical equals original = "
                + thousand.equals(fromCanonical));
        System.out.println("same numeric value = "
                + (thousand.compareTo(fromPlain) == 0));
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```bash
javac BigDecimalPlainStringDemo.java
java BigDecimalPlainStringDemo
```

Kết quả chuẩn:

```text
thousand toString = 1E+3
thousand plain = 1000
tiny toString = 1.2E-7
tiny plain = 0.00000012
entered plain = 12.3400
stripped plain = 12.34
one decimal = 12.3
two decimals = 12.30
rounded two decimals = 12.35
original scale = -3
plain round-trip scale = 0
plain equals original = false
canonical equals original = true
same numeric value = true
```

Đối chiếu phần đầu để thấy lợi ích của `toPlainString()` với số mũ; phần giữa cho thấy dấu 0 cuối được giữ hay bị loại tùy **scale hiện tại**; phần cuối chứng minh một bản decimal text không số mũ có thể **mất thông tin scale âm** khi đọc lại, dù vẫn bảo toàn giá trị số học.

## 8. Hiển thị theo locale, tiền tệ và trao đổi dữ liệu

### 8.1. Chuỗi plain phù hợp khi nào?

`toPlainString()` hữu ích khi đích yêu cầu một số thập phân ASCII cơ bản:

- Giá trị đặt vào tệp văn bản hoặc một trường decimal quy định **không dùng số mũ**.
- Chuỗi số thập phân trao đổi với hệ thống có thể đọc dấu chấm làm dấu thập phân, không yêu cầu dấu phân nhóm.
- Các thông báo kỹ thuật cần xem toàn bộ chữ số thập phân thay vì ký pháp khoa học.

Tuy nhiên, cần kiểm tra hợp đồng của hệ thống đích: có nơi chấp nhận ký pháp số mũ, có nơi bắt buộc scale cố định, có nơi giới hạn số chữ số hoặc độ dài. `toPlainString()` không bảo đảm đáp ứng mọi giới hạn đó.

### 8.2. Giao diện cho người dùng thường cần formatter

Ví dụ muốn hiển thị `1234.50` theo chuẩn sử dụng dấu chấm phân nhóm và dấu phẩy thập phân ở Đức:

```java
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class LocaleNumberDemo {
    public static void main(String[] args) {
        BigDecimal amount = new BigDecimal("1234.50");

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.GERMANY);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        System.out.println(amount.toPlainString());
        System.out.println(formatter.format(amount));
    }
}
```

Lưu thành `LocaleNumberDemo.java` rồi chạy `javac LocaleNumberDemo.java` và `java LocaleNumberDemo`. Với dữ liệu và `Locale.GERMANY` trên Java 17+, đầu ra:

```text
1234.50
1.234,50
```

Khác biệt ở đây là **quy tắc trình bày của locale**, không phải thay đổi giá trị trong `BigDecimal`. `NumberFormat` có khả năng làm tròn để vừa mức chữ số hiển thị; nếu có yêu cầu nghiệp vụ cụ thể về làm tròn, hãy thiết lập và kiểm thử chính sách phù hợp thay vì phụ thuộc hoàn toàn vào mặc định của formatter. Với tiền tệ, sử dụng formatter tiền tệ khi cần ký hiệu tiền, vị trí ký hiệu và quy tắc hiển thị; không tự ghép ký hiệu tiền vào chuỗi plain nếu có yêu cầu địa phương hóa.

### 8.3. Lưu trữ và serialization: chọn theo hợp đồng

Nếu muốn bảo toàn **cả unscaled value và scale**, `toString()` có đặc tính round-trip phù hợp cho `BigDecimal` qua constructor `String`. Cũng có thể lưu tường minh `unscaledValue` cùng `scale` khi thiết kế cấu trúc dữ liệu chuyên biệt.

Nếu hợp đồng yêu cầu **đầu ra không có ký pháp số mũ** và chỉ quan tâm trị số, `toPlainString()` thường hợp lý. Khi đọc lại, đừng giả định `equals()` giữ nguyên với số từng có scale âm; so sánh `compareTo() == 0` nếu mục tiêu thực sự là bằng nhau về trị số.

Đối với JSON, cơ sở dữ liệu, XML hoặc API, kiểm tra kiểu dữ liệu, quy tắc serialize của thư viện và giới hạn độ dài/precision/scale cụ thể. Không nên tự cho rằng mọi serializer đều gọi `toPlainString()`, hoặc rằng mọi hệ thống lưu chữ số 0 và scale giống Java.

### 8.4. Cân nhắc hiệu năng và độ dài

Một `BigDecimal` chứa số mũ rất lớn vẫn có thể được biểu diễn dưới dạng chuỗi khoa học ngắn gọn. Khi chuyển thành dạng plain, nhiều chữ số 0 phải xuất hiện trực tiếp.

```java
BigDecimal large = new BigDecimal("1E+10");
System.out.println(large.toPlainString()); // 10000000000
```

Với số mũ quá lớn, chuỗi plain có thể dài bất thường, tiêu tốn bộ nhớ và không phù hợp với giới hạn kích thước payload. Trước khi xuất dữ liệu đầu vào không tin cậy, cần giới hạn phạm vi trị số, scale, độ dài hoặc sử dụng biểu diễn khác theo yêu cầu giao thức. Không dùng `toPlainString()` vô điều kiện trên dữ liệu có số mũ không giới hạn.

## 9. Mẫu áp dụng và các lỗi thường gặp

### Mẫu 1 — Decimal text không số mũ, có đúng hai chữ số thập phân

Khi hợp đồng yêu cầu đúng 2 chữ số sau dấu chấm và quy định `HALF_UP`:

```java
static String toPlainTwoDecimals(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
}
```

Đây là **phương thức minh họa** phải đặt trong một lớp và import `BigDecimal`, `RoundingMode` tương ứng; chính sách `HALF_UP` và scale 2 chỉ là ví dụ. Một số nghiệp vụ có quy tắc làm tròn riêng hoặc không cho phép làm tròn giá trị gốc.

Nếu tuyệt đối không cho phép mất chữ số, có thể thay bằng `value.setScale(2, RoundingMode.UNNECESSARY).toPlainString()`. Trường hợp đầu vào cần giảm scale mà có chữ số khác 0 bị bỏ sẽ ném `ArithmeticException`, giúp phát hiện dữ liệu vi phạm hợp đồng.

### Mẫu 2 — Bảo toàn chính xác text nhập vào

Nếu yêu cầu giữ mọi ký tự gốc:

```java
String originalInput = "00012.300";
BigDecimal value = new BigDecimal(originalInput);

System.out.println(originalInput);        // 00012.300
System.out.println(value.toPlainString()); // 12.300
```

Lưu chuỗi gốc riêng; `BigDecimal` giữ thông tin để tính toán, **không phải kho lưu nguyên văn**. Đặc biệt cần chú ý khoảng trắng, ký hiệu cộng, số 0 đầu và chuẩn dấu phân cách theo locale trước khi parse.

### Mẫu 3 — Cấu trúc round-trip có giữ scale

Khi mục tiêu là khôi phục đúng đối tượng `BigDecimal` theo `equals()`, dùng `toString()` rồi `new BigDecimal(text)`, hoặc lưu hai thành phần `unscaledValue` và `scale`. Không dùng plain text thay thế tùy ý khi scale âm có thể xuất hiện.

### Sai lầm thường gặp

1. **Cho rằng `toPlainString()` tự làm tròn:** nó chỉ xuất chuỗi. Muốn giảm chữ số phải có thao tác làm tròn tường minh.
2. **Cho rằng `toPlainString()` luôn có đúng hai chữ số lẻ:** số chữ số phụ thuộc scale đang có. `12.3` vẫn là `12.3`, còn `12.3000` vẫn là `12.3000` nếu chưa thay scale.
3. **Cho rằng chuỗi kết quả luôn khớp nguyên văn input:** `"00012.300"` thành `"12.300"`; mất cách viết gốc.
4. **Dùng `stripTrailingZeros()` trước khi cần giữ số chữ số lẻ:** `12.3400` thành `12.34`. Đây là thay đổi biểu diễn có chủ đích và không thể đoán lại mọi dấu 0 ban đầu.
5. **Dùng `toPlainString()` cho serialization phải giữ scale âm:** `1E+3` thành `1000`, parse lại có scale 0 thay vì -3.
6. **Dùng `new BigDecimal(double)` và nghĩ rằng xuất plain sửa được sai số đầu vào:** không đúng; nên kiểm soát cách tạo số và toán học trước khi chuyển sang chuỗi.
7. **Dùng `toPlainString()` để định dạng locale:** nó không thêm dấu phân cách hàng nghìn, không chọn dấu phẩy thập phân và không hiển thị đơn vị tiền.
8. **Dùng `equals()` khi chỉ cần bằng nhau về trị số:** `1E+3` và `1000` có thể khác scale; dùng `compareTo() == 0` cho so sánh trị số.
9. **Bỏ qua quy mô của chuỗi plain:** số có mũ lớn có thể bung ra nhiều chữ số 0; áp đặt giới hạn theo giao thức trước khi xuất.
10. **Nhầm chuỗi với đối tượng:** `toPlainString()` trả `String`; việc gọi nó không sửa hay định dạng vĩnh viễn `BigDecimal` nguồn. Muốn lưu cách trình bày, lưu riêng chuỗi, cấu hình formatter hoặc hợp đồng định dạng.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `new BigDecimal("1E+3").toPlainString()` là `"1000"`?

**Đáp án:** `toPlainString()` biểu diễn decimal không số mũ. Giá trị `1 × 10³` được viết bằng các chữ số thông thường.

**Câu 2.** Vì sao `new BigDecimal("12.3400").toPlainString()` vẫn là `"12.3400"`?

**Đáp án:** Đối tượng có scale 4. `toPlainString()` không tự bỏ số 0 cuối hoặc chọn scale khác.

**Câu 3.** Nếu muốn `"12.30"` từ `new BigDecimal("12.3")` thì làm gì?

**Đáp án:** `value.setScale(2).toPlainString()`, vì chỉ tăng scale từ 1 lên 2 và thêm số 0 không mất độ chính xác.

**Câu 4.** Nếu cần `"12.35"` từ `new BigDecimal("12.345")` với quy tắc `HALF_UP`?

**Đáp án:** `value.setScale(2, RoundingMode.HALF_UP).toPlainString()`. Việc làm tròn đến `12.35` diễn ra ở `setScale()`.

**Câu 5.** `new BigDecimal(new BigDecimal("1E+3").toPlainString())` có `equals()` với bản gốc không?

**Đáp án:** Không. Bản gốc có scale -3, bản parse từ `"1000"` có scale 0. `compareTo()` vẫn trả 0 vì trị số bằng nhau.

**Câu 6.** Lúc nào ưu tiên `toString()` thay vì `toPlainString()`?

**Đáp án:** Khi cần biểu diễn canonical có thể tái tạo đúng unscaled value và scale qua constructor nhận chuỗi, và hợp đồng cho phép ký pháp khoa học.

**Câu 7.** Muốn hiện `"1.234,50"` theo quy ước Đức thì dùng `toPlainString()` hay formatter?

**Đáp án:** Dùng `NumberFormat` hoặc `DecimalFormat` được cấu hình theo `Locale.GERMANY`, với chính sách chữ số thập phân và rounding phù hợp.

**Câu 8.** Muốn lưu lại chính xác input `"00012.300"`, chỉ giữ `BigDecimal` có đủ không?

**Đáp án:** Không. Cần lưu riêng chuỗi input gốc nếu những chữ số 0 đầu hoặc dấu/ký hiệu định dạng ban đầu là dữ liệu cần bảo toàn.

**Ghi nhớ:** `toPlainString()` tạo **chuỗi số thập phân không dùng ký pháp khoa học** từ `BigDecimal` đang có. Nó bảo toàn trị số nhưng **không tự cố định scale, làm tròn, định dạng locale hay lưu nguyên văn văn bản nhập**. Nếu cần bảo toàn scale âm khi serialize, cân nhắc `toString()` hoặc lưu cấu trúc; nếu cần số chữ số hiển thị cụ thể, áp dụng `setScale()` theo đúng quy tắc rồi mới xuất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-conversions-003">toEngineeringString và số mũ theo bội của 3</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal.toEngineeringString(): số mũ theo bội của 3

## 1. Ký pháp kỹ thuật là gì?

Khi một con số quá lớn hoặc quá nhỏ, người ta thường viết theo ký pháp khoa học (*scientific notation*):

~~~text
12 300 000 = 1.23 × 10^7
0.000000123 = 1.23 × 10^-7
~~~

Trong **ký pháp kỹ thuật** (*engineering notation*), số mũ của 10 được chọn là **bội số nguyên của 3**:

~~~text
12 300 000 = 12.3 × 10^6
0.000000123 = 123 × 10^-9
~~~

Điều này có ích khi đọc số theo nhóm ba bậc độ lớn: nghìn (`10^3`), triệu (`10^6`), tỷ (`10^9`), milli (`10^-3`), micro (`10^-6`), nano (`10^-9`). Java hỗ trợ việc chuyển đối tượng `BigDecimal` thành chuỗi theo dạng này bằng:

~~~java
String text = number.toEngineeringString();
~~~

`toEngineeringString()` trả về một `String` biểu diễn **cùng giá trị toán học**. Phương thức không làm tròn, không giới hạn precision, không thay đổi đơn vị đo, không thay đổi đối tượng `BigDecimal` ban đầu.

Ví dụ:

~~~java
BigDecimal x = new BigDecimal("1E+4");
System.out.println(x.toString());            // 1E+4
System.out.println(x.toEngineeringString()); // 10E+3
System.out.println(x.toPlainString());       // 10000
~~~

Cả ba chuỗi biểu diễn giá trị 10.000. Điểm khác nhau là cách đặt dấu thập phân và số mũ.

## 2. Quy tắc "số mũ là bội số của 3"

Trong chuỗi kiểu `ME+n`, dấu `E` chỉ lũy thừa thập phân:

~~~text
M E n = M × 10^n

1E+4 = 1 × 10^4 = 10000
10E+3 = 10 × 10^3 = 10000
100E-9 = 100 × 10^-9 = 0.0000001
~~~

Với số **khác 0** được viết ở dạng có `E`:

- Ký pháp khoa học chuẩn hóa thường đặt một chữ số khác 0 trước dấu chấm, tương ứng `1 ≤ |M| < 10`.
- Ký pháp kỹ thuật đặt từ một đến ba chữ số trước dấu chấm, tương ứng `1 ≤ |M| < 1000`; số mũ `n` chia hết cho 3.
- Dấu của số được giữ ở phần định trị: `-10E+3` bằng `-10000`.
- Số mũ có thể âm: `-3`, `-6`, `-9` cũng là bội của 3.

| Giá trị | Dạng khoa học | Dạng kỹ thuật | Vì sao |
|---|---|---|---|
| 10.000 | `1E+4` | `10E+3` | Giảm số mũ 4 xuống 3 và tăng định trị gấp 10 |
| 100.000 | `1E+5` | `100E+3` | Giảm số mũ 5 xuống 3 và tăng định trị gấp 100 |
| 1.000.000 | `1E+6` | `1E+6` | Số mũ 6 đã chia hết cho 3 |
| 0,0000001 | `1E-7` | `100E-9` | Số mũ -9 chia hết cho 3 |
| 0,00000001 | `1E-8` | `10E-9` | Số mũ -9 chia hết cho 3 |
| 0,000000001 | `1E-9` | `1E-9` | Số mũ -9 đã phù hợp |

Nếu dịch dấu thập phân trong định trị sang phải `r` chữ số thì phải giảm số mũ đi `r` để **giữ nguyên giá trị**:

~~~text
M × 10^e = (M × 10^r) × 10^(e-r)

1.23 × 10^-7 = 123 × 10^-9
1.2345 × 10^8 = 123.45 × 10^6
~~~

Cách chuyển đổi hoàn toàn không yêu cầu tính toán bằng số chấm động `double`.

**Quan trọng:** "Số mũ là bội của 3" chỉ áp dụng **khi Java quyết định xuất chuỗi có ký hiệu `E`**. `toEngineeringString()` không bắt buộc in `E` đối với mọi đối tượng.

## 3. So sánh toString(), toEngineeringString(), toPlainString()

Đây là ba cách xuất chuỗi khác nhau của `BigDecimal`:

| Phương thức | Cách biểu diễn | Có thể có `E`? | Mục tiêu |
|---|---|---|---|
| `toString()` | Biểu diễn chuẩn (*canonical*), có thể là khoa học hoặc dạng thường | Có | Chuyển số thành chuỗi chuẩn, giữ cặp giá trị–scale khi parse lại |
| `toEngineeringString()` | Nếu dùng `E` thì số mũ là bội của 3 | Có | Trình bày theo nhóm ba bậc thập phân |
| `toPlainString()` | Dạng thập phân thường, không in số mũ | Không | Chuỗi số thường, không có `E` |

So sánh giá trị trả về thực tế:

| Constructor | `toString()` | `toEngineeringString()` | `toPlainString()` |
|---|---|---|---|
| `new BigDecimal("1E+4")` | `1E+4` | `10E+3` | `10000` |
| `new BigDecimal("1E+5")` | `1E+5` | `100E+3` | `100000` |
| `new BigDecimal("1E+6")` | `1E+6` | `1E+6` | `1000000` |
| `new BigDecimal("1E-7")` | `1E-7` | `100E-9` | `0.0000001` |
| `new BigDecimal("1E-8")` | `1E-8` | `10E-9` | `0.00000001` |
| `new BigDecimal("1E-9")` | `1E-9` | `1E-9` | `0.000000001` |
| `new BigDecimal("0.000000123")` | `1.23E-7` | `123E-9` | `0.000000123` |
| `new BigDecimal("12345.00")` | `12345.00` | `12345.00` | `12345.00` |
| `new BigDecimal("1000")` | `1000` | `1000` | `1000` |
| `new BigDecimal("0E+7")` | `0E+7` | `0.00E+9` | `0` |

Với `new BigDecimal("1000")`, tất cả ba phương thức đều in `1000`. Phương thức kỹ thuật **không tự động ép số 1000 sang `1E+3`**. Tương tự, `new BigDecimal("123456789").toEngineeringString()` vẫn trả `"123456789"` dù giá trị khá lớn.

Đây là điểm dễ sai khi viết các chương trình yêu cầu "mọi số phải được in có số mũ kỹ thuật". Nếu hợp đồng yêu cầu **luôn có `E`**, phải dùng bộ định dạng có cấu hình riêng, bao gồm quy tắc precision, rounding và locale. Chỉ `toEngineeringString()` không bảo đảm hình thức đó.

## 4. Vì sao Java đôi khi in dạng thập phân thường?

Một `BigDecimal` gồm hai thành phần: giá trị nguyên chưa chia tỷ lệ (`unscaledValue`) và `scale`:

~~~text
giá trị = unscaledValue × 10^(-scale)
~~~

`scale()` có thể dương, bằng 0 hoặc âm. `precision()` là số chữ số của phần nguyên chưa chia tỷ lệ, không tính dấu. Ví dụ:

| Đối tượng | `precision()` | `scale()` | Giá trị |
|---|---:|---:|---|
| `new BigDecimal("123.4500")` | 7 | 4 | 123.4500 |
| `new BigDecimal("1000")` | 4 | 0 | 1000 |
| `new BigDecimal("1E+3")` | 1 | -3 | 1000 |
| `new BigDecimal("0.0000001")` | 1 | 7 | 0.0000001 |

Với số khác 0, số mũ điều chỉnh (*adjusted exponent*) là:

~~~text
adjustedExponent = precision() - scale() - 1
~~~

Đối với `toString()` và `toEngineeringString()`, nếu `scale >= 0` và `adjustedExponent >= -6` thì Java dùng dạng thập phân thường. Khi điều kiện đó không đúng, chuỗi thường dùng ký pháp `E`; `toEngineeringString()` điều chỉnh để số mũ của `E` là bội của 3. Quy tắc cụ thể cho số 0 có các ngoại lệ biểu diễn vì số 0 không có định trị chuẩn hóa duy nhất.

Ví dụ ranh giới:

~~~java
BigDecimal a = new BigDecimal("0.000001");  // precision=1, scale=6
BigDecimal b = new BigDecimal("0.0000001"); // precision=1, scale=7

System.out.println(a.toEngineeringString()); // 0.000001
System.out.println(b.toEngineeringString()); // 100E-9
~~~

Số thứ nhất có adjusted exponent `-6` nên vẫn in bình thường; số thứ hai có adjusted exponent `-7` nên dùng ký pháp số mũ. Do đó, không thể suy luận đơn giản rằng "số nào nhỏ hơn 1 đều có `E`".

## 5. Chương trình Java 17+ có thể chạy độc lập

Tạo tệp `EngineeringStringDemo.java` với nội dung:

~~~java
import java.math.BigDecimal;

public class EngineeringStringDemo {
    public static void main(String[] args) {
        String[] inputs = {
            "1E+4", "1E+5", "1E+6",
            "1E-7", "1E-8", "1E-9",
            "0.000000123", "12345.00", "1000", "0E+7"
        };

        for (String input : inputs) {
            BigDecimal number = new BigDecimal(input);
            System.out.println(
                    input
                    + " | sci=" + number.toString()
                    + " | eng=" + number.toEngineeringString()
                    + " | plain=" + number.toPlainString());
        }

        BigDecimal original = new BigDecimal("1E+4");
        BigDecimal fromEngineering =
                new BigDecimal(original.toEngineeringString());
        BigDecimal fromCanonical =
                new BigDecimal(original.toString());

        System.out.println("original scale=" + original.scale());
        System.out.println("engineering scale=" + fromEngineering.scale());
        System.out.println("same numeric value="
                + (original.compareTo(fromEngineering) == 0));
        System.out.println("same representation="
                + original.equals(fromEngineering));
        System.out.println("canonical preserves representation="
                + original.equals(fromCanonical));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac EngineeringStringDemo.java
java EngineeringStringDemo
~~~

Kết quả chuẩn:

~~~text
1E+4 | sci=1E+4 | eng=10E+3 | plain=10000
1E+5 | sci=1E+5 | eng=100E+3 | plain=100000
1E+6 | sci=1E+6 | eng=1E+6 | plain=1000000
1E-7 | sci=1E-7 | eng=100E-9 | plain=0.0000001
1E-8 | sci=1E-8 | eng=10E-9 | plain=0.00000001
1E-9 | sci=1E-9 | eng=1E-9 | plain=0.000000001
0.000000123 | sci=1.23E-7 | eng=123E-9 | plain=0.000000123
12345.00 | sci=12345.00 | eng=12345.00 | plain=12345.00
1000 | sci=1000 | eng=1000 | plain=1000
0E+7 | sci=0E+7 | eng=0.00E+9 | plain=0
original scale=-4
engineering scale=-3
same numeric value=true
same representation=false
canonical preserves representation=true
~~~

Tên nhãn `sci` trong ví dụ chỉ giúp đối chiếu với `toString()`; phương thức này không bắt buộc luôn trả ký pháp khoa học. Các dòng `1000` và `12345.00` cho thấy kết quả có thể là số thập phân thường.

Các dòng cuối kiểm tra tính chất quan trọng khi **parse ngược** một chuỗi engineering notation thành `BigDecimal`: cùng giá trị số chưa chắc cùng `scale`.

## 6. Parse chuỗi engineering: giữ giá trị, có thể đổi scale

Constructor `new BigDecimal(String)` chấp nhận ký pháp `E`, ví dụ:

~~~java
BigDecimal parsed = new BigDecimal("10E+3");
System.out.println(parsed.toPlainString()); // 10000
~~~

Tuy nhiên, nếu sau đó parse chuỗi từ `toEngineeringString()`, hai đối tượng trước và sau **có thể khác scale**:

~~~java
BigDecimal before = new BigDecimal("1E+4");  // 1 × 10^4
String text = before.toEngineeringString();  // "10E+3"
BigDecimal after = new BigDecimal(text);      // 10 × 10^3

System.out.println(before.scale());          // -4
System.out.println(after.scale());           // -3
System.out.println(before.compareTo(after)); // 0
System.out.println(before.equals(after));    // false
~~~

Vì sao? `BigDecimal` tính giá trị theo công thức `unscaledValue × 10^(-scale)`:

| Đối tượng | Unscaled value | Scale | Giá trị |
|---|---:|---:|---:|
| `before`, được tạo từ `"1E+4"` | 1 | -4 | 10000 |
| `after`, được tạo từ `"10E+3"` | 10 | -3 | 10000 |

`compareTo() == 0` biểu thị giá trị toán học bằng nhau. `equals()` yêu cầu giá trị **và scale** bằng nhau; vì vậy trả `false` ở đây. Đây là hành vi của `BigDecimal`, không phải lỗi của `toEngineeringString()`.

| Chuỗi xuất rồi tạo lại bằng `new BigDecimal(text)` | Giữ nguyên giá trị toán học? | Luôn giữ nguyên scale? |
|---|---|---|
| `x.toString()` | Có | **Có**: biểu diễn chuẩn của `BigDecimal` |
| `x.toEngineeringString()` | Có | **Không nhất thiết** |
| `x.toPlainString()` | Có | **Không nhất thiết**, nhất là khi scale âm |

Với `toPlainString()`, ví dụ `new BigDecimal("1E+4")` xuất ra `"10000"`. Parse lại thành `new BigDecimal("10000")` có scale 0, khác scale -4 ban đầu.

**Quy tắc thực tế:** nếu chỉ cần giá trị số, có thể so sánh bằng `compareTo(other) == 0`. Nếu serializing/deserializing phải bảo toàn cả biểu diễn (`scale`, một phần của ngữ nghĩa `equals()`), dùng `toString()` và `new BigDecimal(String)`. Không nên dùng `toEngineeringString()` làm định dạng round trip chính xác về scale trong hợp đồng dữ liệu.

## 7. Số 0 là trường hợp đặc biệt

Số khác 0 có thể chuẩn hóa định trị trong khoảng từ 1 đến dưới 1000, nhưng số 0 thì không: mọi biểu thức `0 × 10^n` vẫn là 0. Bởi vậy Java còn dùng các số 0 sau dấu thập phân để thể hiện scale.

~~~java
BigDecimal zero = new BigDecimal("0E+7");
System.out.println(zero.toString());            // 0E+7
System.out.println(zero.toEngineeringString()); // 0.00E+9
System.out.println(zero.toPlainString());       // 0
~~~

`0.00E+9` vẫn bằng 0; số mũ 9 là bội của 3. Không thể suy ra số mũ này từ "độ lớn" của số 0; nó phụ thuộc biểu diễn của đối tượng. Parse `"0.00E+9"` còn có thể giữ lại scale -7 của `"0E+7"`, bởi hai chữ số 0 thập phân nằm trong định trị.

Ví dụ khác:

~~~java
System.out.println(new BigDecimal("0E-7").toEngineeringString());
// 0.0E-6
~~~

Đối với số 0, không áp dụng máy móc quy tắc định trị `1 ≤ |M| < 1000`. Khi xây dựng thuật toán riêng chuyển đổi dạng engineering, cần xử lý zero riêng và không tự đưa ra kết luận về precision hoặc scale chỉ từ độ lớn bằng 0.

## 8. Trường hợp số 0 cuối và stripTrailingZeros()

Các `BigDecimal` sau cùng giá trị 1000 nhưng có biểu diễn khác nhau:

~~~java
BigDecimal first = new BigDecimal("1E+3");
BigDecimal second = new BigDecimal("1000");
BigDecimal third = new BigDecimal("1000.00");

System.out.println(first.toEngineeringString());  // 1E+3
System.out.println(second.toEngineeringString()); // 1000
System.out.println(third.toEngineeringString());  // 1000.00
~~~

`first` có scale -3, `second` có scale 0, `third` có scale 2. Các số 0 ở cuối chuỗi đầu vào có thể ảnh hưởng precision và scale, dù giá trị toán học không đổi.

`stripTrailingZeros()` trả về một `BigDecimal` mới, bỏ các số 0 không cần thiết khỏi unscaled value; trong trường hợp này scale thay đổi:

~~~java
BigDecimal count = new BigDecimal("1000");
BigDecimal stripped = count.stripTrailingZeros();

System.out.println(count.toEngineeringString());    // 1000
System.out.println(stripped.toEngineeringString()); // 1E+3
System.out.println(stripped.scale());               // -3
~~~

Vì thế `stripTrailingZeros()` **không chỉ đổi cách hiển thị**, mà còn thay đổi biểu diễn của đối tượng kết quả. Nếu dùng trước khi serialize, bạn có thể vô tình thay đổi kết quả của `equals()` hoặc các xử lý phụ thuộc scale.

Không dùng `stripTrailingZeros()` một cách máy móc để ép mọi số thành dạng có `E`: với các số không có số 0 cuối như `123456789`, phương thức không tạo ra số mũ.

## 9. Engineering notation không có tác dụng rounding

`toEngineeringString()` không làm tròn. Nếu số có nhiều chữ số có nghĩa, chuỗi engineering vẫn biểu diễn toàn bộ giá trị:

~~~java
BigDecimal x = new BigDecimal("1.23456789012345E+7");
System.out.println(x.toEngineeringString());
// 12.3456789012345E+6
~~~

Nếu bài toán cần giới hạn số chữ số có nghĩa, phải làm tròn riêng trước khi chuyển thành chuỗi:

~~~java
import java.math.MathContext;
import java.math.RoundingMode;

BigDecimal x = new BigDecimal("1.23456789E+7");
BigDecimal rounded = x.round(new MathContext(4, RoundingMode.HALF_UP));

System.out.println(rounded.toEngineeringString()); // 12.35E+6
~~~

`round(MathContext)` xác định precision và chế độ làm tròn; `toEngineeringString()` chỉ xác định dạng biểu diễn. Tương tự, `setScale(2, RoundingMode.HALF_UP)` nhằm làm tròn đến hai chữ số sau dấu thập phân, không nhằm đặt số mũ thành bội của 3.

Một phương thức khác dễ nhầm là `scaleByPowerOfTen(n)`:

~~~java
BigDecimal a = new BigDecimal("123");
BigDecimal b = a.scaleByPowerOfTen(3); // giá trị 123000
~~~

`scaleByPowerOfTen(3)` **thực sự thay đổi giá trị** bằng cách nhân `10^3`. Nó không phải công cụ đổi cách viết số 123 sang engineering notation mà giữ nguyên giá trị.

## 10. Ví dụ ứng dụng và lựa chọn API

Giả sử một cảm biến ghi dòng điện bằng ampere:

~~~java
BigDecimal currentAmpere = new BigDecimal("0.000000123");
String engineering = currentAmpere.toEngineeringString();

System.out.println(engineering + " A"); // 123E-9 A
~~~

`123E-9 A` và `0.000000123 A` cùng giá trị. Nếu muốn đầu ra là `123 nA`, ứng dụng phải tự chọn **tiền tố đơn vị** và xử lý quy đổi theo hợp đồng kỹ thuật; `toEngineeringString()` không thêm `n`, `μ`, `m`, `k` hoặc `M`.

Những trường hợp sử dụng phù hợp gồm báo cáo điện/điện tử, biểu diễn đại lượng vật lý trải dài nhiều bậc độ lớn, dữ liệu đo lường và các bảng kỹ thuật quen đọc theo bội của 1000.

| Nhu cầu | Phương thức / hướng xử lý |
|---|---|
| Chuỗi chuẩn có thể parse lại và bảo toàn scale | `toString()` |
| Chuỗi không chứa ký pháp `E` | `toPlainString()` |
| Khi in số mũ, muốn số mũ là bội số 3 | `toEngineeringString()` |
| Luôn bắt buộc số mũ engineering, kể cả số nguyên thông thường | Bộ định dạng riêng với quy tắc rõ ràng |
| Giới hạn số chữ số có nghĩa | `round(MathContext)` trước khi xuất chuỗi |
| Đặt số chữ số thập phân cố định | `setScale(scale, mode)` đúng theo nghiệp vụ |
| Đổi đơn vị, thêm tiền tố SI | Logic xử lý đơn vị riêng, sau khi xác định giá trị và bậc SI |

Trong API, JSON, CSV và lưu trữ, hãy phân biệt ba điều: (1) client có chấp nhận chuỗi số mũ hay không, (2) có cần bảo toàn `scale` hay không, và (3) có cần tuân theo locale hay đơn vị của người dùng hay không. `toEngineeringString()` dùng cú pháp số Java (dấu chấm và `E`), nhưng điều đó không đồng nghĩa mọi giao thức ngoài hệ thống đều chấp nhận dạng này.

## 11. Những lỗi thường gặp

| Nhận định sai | Thực tế | Cách tránh |
|---|---|---|
| "Gọi engineering là lúc nào cũng có E" | `new BigDecimal("1000")` vẫn trả `1000` | Kiểm tra nhu cầu có bắt buộc số mũ hay không |
| "Dùng engineering sẽ làm tròn" | Phương thức chỉ tạo chuỗi | Làm tròn riêng bằng context hoặc scale |
| "`10E+3` là 10 nghìn khác `1E+4`" | Cùng giá trị 10000 | Kiểm tra định trị × `10^số_mũ` |
| "Số mũ âm không theo bội ba" | `-3`, `-6`, `-9` là bội ba | Xét cả mũ âm |
| "Chuỗi engineering parse lại luôn equals bản gốc" | Có thể đổi `scale` | Dùng `toString()` nếu cần round trip biểu diễn |
| "Số 0 tuân theo chuẩn định trị của số khác 0" | `0.00E+9` là kết quả hợp lệ | Xử lý zero riêng |
| "`stripTrailingZeros` chỉ đổi chuỗi" | Trả đối tượng có thể đổi scale | Kiểm tra tác động đến equals / định dạng |
| "Engineering có sẵn đơn vị kilo/micro" | Chỉ có số mũ, không có đơn vị | Viết logic quy đổi đơn vị riêng |
| "`toPlainString` parse lại luôn giữ scale" | Scale âm mất đi | Chọn `toString` khi cần bảo toàn scale |
| "Thấy E+6 thì biết BigDecimal đã dùng double" | Chuỗi `E` là ký pháp thập phân, không hàm ý kiểu `double` | Tách việc biểu diễn chuỗi khỏi kiểu dữ liệu |

## 12. Bài tập tự kiểm tra

**Bài 1.** `new BigDecimal("1E+8").toEngineeringString()` là gì?

**Đáp án:** `100E+6`, vì `1 × 10^8 = 100 × 10^6`. Số mũ 6 chia hết cho 3.

**Bài 2.** Vì sao `new BigDecimal("1E-7").toEngineeringString()` là `100E-9` chứ không phải `1E-6`?

**Đáp án:** `1E-6` lớn gấp mười lần `1E-7`. Phải tăng định trị thành `100` và giảm mũ xuống `-9` để giữ nguyên giá trị.

**Bài 3.** Vì sao hai số cùng bằng 1000 mà `new BigDecimal("1000").toEngineeringString()` là `1000`, còn `new BigDecimal("1E+3").toEngineeringString()` là `1E+3`?

**Đáp án:** `BigDecimal` giữ scale khác nhau (0 so với -3). Quy tắc in số mũ xét biểu diễn, không chỉ độ lớn của giá trị.

**Bài 4.** Parse `"10E+3"` rồi so với `new BigDecimal("1E+4")` bằng `equals()` có nhận `true` không?

**Đáp án:** Không. Hai số có cùng giá trị nhưng scale -3 và -4. `compareTo() == 0` nhận `true`.

**Bài 5.** Khi cần luôn in engineering notation với `E` cho số `123456789`, có chỉ gọi `toEngineeringString()` được không?

**Đáp án:** Không. Với đối tượng có scale 0 ấy, Java chọn dạng thường. Cần một công cụ format bắt buộc, xác định rõ precision/rounding và hình thức đầu ra.

**Bài 6.** Mô tả kết quả `new BigDecimal("0E+7").toEngineeringString()`.

**Đáp án:** `0.00E+9`. Giá trị vẫn là 0, số mũ 9 là bội của 3; các chữ số 0 trong định trị hỗ trợ biểu đạt scale của số 0.

---

**Ghi nhớ:** `toEngineeringString()` là phép **chuyển sang chuỗi**; nếu chuỗi dùng ký hiệu `E`, số mũ sẽ là bội của 3. Giá trị số không đổi, nhưng parse ngược từ chuỗi có thể tạo `BigDecimal` với scale khác. Chọn `toString()` cho round trip bảo toàn scale, `toPlainString()` để không in `E`, và `toEngineeringString()` để trình bày theo ký pháp kỹ thuật khi phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-conversions-004">intValue, longValue và phần dữ liệu có thể mất</a>

<details>
<summary>Xem chi tiết</summary>

# `BigDecimal.intValue()`, `longValue()` và phần dữ liệu có thể mất

## 1. Vì sao cần hiểu kỹ chuyển đổi từ BigDecimal sang số nguyên?

`BigDecimal` biểu diễn các số thập phân với độ chính xác tùy ý, trong khi `int` chỉ chứa được số nguyên có dấu 32 bit và `long` chỉ chứa được số nguyên có dấu 64 bit. Khi viết:

~~~java
BigDecimal value = new BigDecimal("123.99");

int a = value.intValue();   // 123
long b = value.longValue(); // 123
~~~

Java **không báo lỗi** dù phần `.99` đã biến mất. Hai phương thức này thực hiện **chuyển đổi thu hẹp có thể mất dữ liệu**, không phải phép làm tròn theo một quy tắc tài chính.

Thậm chí phần nguyên cũng có thể mất nếu nằm ngoài phạm vi kiểu đích:

~~~java
new BigDecimal("2147483648").intValue()  // -2147483648
new BigDecimal("4294967296").intValue()  // 0
~~~

Điều cần ghi nhớ:

- `intValue()` và `longValue()` **loại bỏ phần thập phân theo hướng về 0**; sau đó giới hạn vào số bit của kiểu đích. Chúng **không kiểm tra mất mát dữ liệu**.
- `intValueExact()` và `longValueExact()` **chỉ trả kết quả nếu giá trị là số nguyên chính xác và nằm trong phạm vi kiểu đích**; nếu không, chúng ném `ArithmeticException`.
- Nếu nghiệp vụ cho phép làm tròn, hãy **chọn rõ `RoundingMode` và làm tròn trước**, rồi dùng phương thức `...ValueExact()` để kiểm tra giới hạn.
- Việc chương trình biên dịch hoặc trả được `int`/`long` không chứng minh phép chuyển đổi đã giữ đúng giá trị ban đầu.

## 2. Hai nguồn mất dữ liệu độc lập

### 2.1. Mất phần thập phân: cắt về 0, không làm tròn

`intValue()` và `longValue()` bỏ phần không nguyên. Chúng chỉ giữ phần nguyên gần 0 nhất:

| BigDecimal đầu vào | `intValue()` | `longValue()` | Điều bị mất |
| --- | ---: | ---: | --- |
| `123.999` | `123` | `123` | `0.999` |
| `-123.999` | `-123` | `-123` | `-0.999` |
| `0.999` | `0` | `0` | Toàn bộ giá trị khác 0 |
| `-0.999` | `0` | `0` | Toàn bộ giá trị khác 0 |
| `1.000` | `1` | `1` | Không mất **giá trị số**; chỉ không giữ lại scale 3 |

Ví dụ, `new BigDecimal("-2.9").intValue()` là `-2`. Đây **không** phải `Math.floor(-2.9)` (kết quả `-3.0`), cũng **không** phải làm tròn đến số nguyên gần nhất. Chữ “truncate” là **loại bỏ phần thập phân về 0**.

`intValue()` và `longValue()` không nhận `RoundingMode`. Các mode như `HALF_UP`, `HALF_EVEN`, `DOWN`, `FLOOR` chỉ có tác dụng nếu bạn chủ động dùng API làm tròn như `setScale`.

### 2.2. Mất bit ở phần nguyên: số có thể đổi dấu hoặc thành 0

Phạm vi Java của hai kiểu:

| Kiểu | Số bit | Giá trị nhỏ nhất | Giá trị lớn nhất |
| --- | ---: | ---: | ---: |
| `int` | 32 | `-2147483648` | `2147483647` |
| `long` | 64 | `-9223372036854775808` | `9223372036854775807` |

Sau khi loại bỏ phần thập phân, nếu phần nguyên quá lớn hoặc quá nhỏ:

- `intValue()` giữ **32 bit thấp** của phần nguyên theo biểu diễn hai bù, rồi diễn giải kết quả thành `int` có dấu.
- `longValue()` giữ **64 bit thấp** và diễn giải thành `long` có dấu.
- Không có ngoại lệ chỉ vì vượt phạm vi; giá trị nhận được có thể hoàn toàn khác số ban đầu.

Ví dụ:

| Giá trị BigDecimal | `intValue()` | `longValue()` | Giải thích |
| --- | ---: | ---: | --- |
| `2147483647` | `2147483647` | `2147483647` | Trong phạm vi cả hai |
| `2147483648` | `-2147483648` | `2147483648` | Vượt `int`, nhưng vừa `long` |
| `4294967296` (= 2^32) | `0` | `4294967296` | 32 bit thấp đều bằng 0 |
| `9223372036854775807` | `-1` | `9223372036854775807` | Vừa `long`; 32 bit thấp bằng tất cả bit 1 |
| `9223372036854775808` (= 2^63) | `0` | `-9223372036854775808` | Vượt `long`, bit dấu bị diễn giải thành âm |
| `18446744073709551616` (= 2^64) | `0` | `0` | 64 bit thấp đều bằng 0 |

Ở đây nói “giữ bit thấp” là mô tả **kết quả theo đặc tả chuyển đổi**; không nên dựa vào một giả định về cấu trúc bộ nhớ bên trong `BigDecimal`.

Ví dụ `4294967296` thành `0` vì `4294967296 = 2^32`: khi chỉ giữ phần dư theo `2^32`, kết quả là 0. Số âm cũng có thể biến thành số dương: `new BigDecimal("-2147483649").intValue()` cho `2147483647`.

**Cắt phần thập phân và mất bit có thể xảy ra cùng lúc.** Một số như `4294967296.75` có thể mất `.75` trước, rồi chuyển phần nguyên `4294967296` thành `0` nếu dùng `intValue()`.

## 3. Hai phương thức an toàn hơn: intValueExact và longValueExact

API `BigDecimal` cung cấp:

~~~java
int exactInt = value.intValueExact();
long exactLong = value.longValueExact();
~~~

Chúng đòi hỏi **đồng thời cả hai điều kiện**:

1. **Giá trị toán học phải là số nguyên chính xác**: không được bỏ một phần thập phân khác 0.
2. **Giá trị nguyên phải nằm trong phạm vi** của `int` hoặc `long`.

Nếu bất kỳ điều kiện nào không đạt, `ArithmeticException` được ném. Không phải phép toán nào cũng thất bại chỉ vì `scale() > 0`:

~~~java
new BigDecimal("1.000").intValueExact(); // 1: phần lẻ có giá trị 0
new BigDecimal("1.001").intValueExact(); // ArithmeticException
~~~

`1.000` có scale 3, nhưng giá trị toán học là số nguyên 1. Vì vậy **đừng chỉ nhìn số chữ số sau dấu thập phân** để quyết định có thể chuyển chính xác hay không.

Bảng lựa chọn:

| Dữ liệu đầu vào | `intValueExact()` | `longValueExact()` |
| --- | --- | --- |
| `123` | `123` | `123` |
| `1.000` | `1` | `1` |
| `123.999` | `ArithmeticException` | `ArithmeticException` |
| `2147483648` | `ArithmeticException` | `2147483648` |
| `9223372036854775807` | `ArithmeticException` | `9223372036854775807` |
| `9223372036854775808` | `ArithmeticException` | `ArithmeticException` |

Nếu cần xử lý lỗi rõ ràng trong ứng dụng:

~~~java
BigDecimal input = new BigDecimal("123.99");

try {
    long converted = input.longValueExact();
    System.out.println(converted);
} catch (ArithmeticException ex) {
    System.out.println("Giá trị có phần lẻ hoặc vượt phạm vi long");
}
~~~

Đoạn mã này nằm trong một phương thức hoặc block thích hợp có import `java.math.BigDecimal`; đầu ra sẽ là dòng thông báo trong `catch`. Thông điệp exception cụ thể của JDK không nên dùng làm điều kiện nghiệp vụ, vì có thể khác nhau theo tình huống hoặc phiên bản.

## 4. Chương trình Java 17+ minh họa đầy đủ

Tạo file **`BigDecimalIntegerConversionDemo.java`**:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalIntegerConversionDemo {
    public static void main(String[] args) {
        String[] samples = {
                "123.999",
                "-123.999",
                "2147483648",
                "4294967296",
                "9223372036854775808",
                "18446744073709551616",
                "1.000",
                "1.001"
        };

        for (String input : samples) {
            BigDecimal value = new BigDecimal(input);
            System.out.println(input
                    + " | int=" + value.intValue()
                    + " | long=" + value.longValue()
                    + " | intExact=" + exactInt(value)
                    + " | longExact=" + exactLong(value));
        }

        BigDecimal amount = new BigDecimal("12.75");
        System.out.println("truncate: " + amount.intValue());
        System.out.println("HALF_UP: "
                + amount.setScale(0, RoundingMode.HALF_UP).intValueExact());

        BigDecimal negative = new BigDecimal("-12.75");
        System.out.println("negative HALF_UP: "
                + negative.setScale(0, RoundingMode.HALF_UP).intValueExact());

        System.out.println("BigInteger trunc: "
                + new BigDecimal("1.999").toBigInteger());
    }

    private static String exactInt(BigDecimal value) {
        try {
            return Integer.toString(value.intValueExact());
        } catch (ArithmeticException ex) {
            return "ArithmeticException";
        }
    }

    private static String exactLong(BigDecimal value) {
        try {
            return Long.toString(value.longValueExact());
        } catch (ArithmeticException ex) {
            return "ArithmeticException";
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BigDecimalIntegerConversionDemo.java
java BigDecimalIntegerConversionDemo
~~~

Kết quả:

~~~text
123.999 | int=123 | long=123 | intExact=ArithmeticException | longExact=ArithmeticException
-123.999 | int=-123 | long=-123 | intExact=ArithmeticException | longExact=ArithmeticException
2147483648 | int=-2147483648 | long=2147483648 | intExact=ArithmeticException | longExact=2147483648
4294967296 | int=0 | long=4294967296 | intExact=ArithmeticException | longExact=4294967296
9223372036854775808 | int=0 | long=-9223372036854775808 | intExact=ArithmeticException | longExact=ArithmeticException
18446744073709551616 | int=0 | long=0 | intExact=ArithmeticException | longExact=ArithmeticException
1.000 | int=1 | long=1 | intExact=1 | longExact=1
1.001 | int=1 | long=1 | intExact=ArithmeticException | longExact=ArithmeticException
truncate: 12
HALF_UP: 13
negative HALF_UP: -13
BigInteger trunc: 1
~~~

**Cách đọc kết quả:** Cột `int` và `long` luôn hiển thị một giá trị nguyên, kể cả khi sai lệch nghiêm trọng. Hai cột `intExact` và `longExact` giúp nhìn rõ trường hợp nào không thể chuyển chính xác. `2147483648` là phép thử đặc biệt hữu ích: `intValue()` đổi thành số âm, `intValueExact()` báo lỗi, còn `longValueExact()` thành công vì vẫn vừa phạm vi 64 bit.

Đoạn chương trình bắt riêng `ArithmeticException` trong phương thức trợ giúp để minh họa toàn bộ các trường hợp trên một lần chạy. Trong mã nghiệp vụ, thường nên **để lỗi lan lên tầng kiểm tra dữ liệu** hoặc biến thành lỗi xác thực có thông tin đầu vào phù hợp, thay vì âm thầm trả giá trị mặc định.

## 5. Nếu nghiệp vụ thật sự cần làm tròn hoặc cắt bỏ phần lẻ

Chọn chính sách theo yêu cầu, sau đó tách rõ **bước xử lý phần thập phân** và **bước kiểm tra phạm vi số nguyên**.

### 5.1. Làm tròn theo quy tắc đã định

~~~java
BigDecimal amount = new BigDecimal("12.75");

int halfUp = amount
        .setScale(0, RoundingMode.HALF_UP)
        .intValueExact(); // 13
~~~

`setScale(0, HALF_UP)` là một lựa chọn nghiệp vụ: làm tròn đến số nguyên gần nhất và khi đúng nửa thì đi xa số 0. Khi đã tạo giá trị nguyên bằng phép làm tròn chủ động, `intValueExact()` tiếp tục bảo đảm nó nằm trong phạm vi `int`.

Không nên làm như sau nếu mục tiêu là tính số nguyên sau khi làm tròn:

~~~java
int wrongForRounding = new BigDecimal("12.75").intValue(); // 12, không phải 13
~~~

Ví dụ các mode cho `-12.75` khi scale 0:

| Mode | Kết quả |
| --- | ---: |
| `DOWN` | `-12` |
| `UP` | `-13` |
| `HALF_UP` | `-13` |
| `HALF_EVEN` | `-13` |
| `CEILING` | `-12` |
| `FLOOR` | `-13` |

Ở giá trị `-12.75`, nhiều mode tình cờ trả cùng số. Để phân biệt các mode tại điểm đúng nửa, thử cả `2.5` và `-2.5`. Ví dụ `HALF_EVEN` làm tròn `2.5` thành `2`, còn `HALF_UP` thành `3`.

### 5.2. Nếu cắt về 0 là quyết định có chủ ý, vẫn kiểm tra tràn

~~~java
BigDecimal quantity = new BigDecimal("-12.75");

long truncatedChecked = quantity
        .setScale(0, RoundingMode.DOWN)
        .longValueExact(); // -12
~~~

`RoundingMode.DOWN` có nghĩa là đi **về 0**, không phải luôn giảm giá trị. Kết quả làm tròn tới scale 0 được biểu diễn thành số nguyên, rồi `longValueExact()` kiểm tra phạm vi.

Cách này thích hợp khi bạn muốn giữ đúng chính sách bỏ phần thập phân **nhưng không chấp nhận chuyển sai vì tràn 64 bit**. Nếu muốn **cấm mọi phần lẻ khác 0**, không dùng `DOWN`: hãy gọi trực tiếp `longValueExact()`.

### 5.3. Với số nguyên lớn hơn long: dùng BigInteger

Nếu bài toán thật sự cần một số nguyên có thể lớn hơn `Long.MAX_VALUE`, đừng ép về `long`:

~~~java
BigDecimal huge = new BigDecimal("9223372036854775808");

java.math.BigInteger whole = huge.toBigIntegerExact();
// 9223372036854775808, không mất dữ liệu.
~~~

Phân biệt:

- `toBigInteger()`: bỏ phần lẻ (nếu có), không áp giới hạn 32/64 bit.
- `toBigIntegerExact()`: **từ chối** phần lẻ khác 0 bằng `ArithmeticException`, vẫn không giới hạn 32/64 bit như hai kiểu nguyên primitive.
- `toBigInteger().intValue()`: sau cùng vẫn **có thể mất các bit cao**. Đổi sang `BigInteger` không tự làm cho mọi phép ép về `int`/`long` trở nên an toàn.

Với dữ liệu đếm, kích thước, hoặc ID vốn phải là số nguyên, một lựa chọn khác là xử lý chuỗi đầu vào bằng `Integer.parseInt`, `Long.parseLong` hoặc `new BigInteger(text)`, theo đúng phạm vi và định dạng đầu vào. Không nhất thiết phải đi vòng qua `BigDecimal` nếu nghiệp vụ không chấp nhận số thập phân ngay từ đầu.

## 6. Giá trị hiển thị, precision, scale và chuyển đổi sang kiểu nguyên

Một `BigDecimal` có **precision** (số chữ số có nghĩa) và **scale** (số chữ số thập phân theo biểu diễn). `intValue()` và `longValue()` không chuyển hai thuộc tính đó thành kiểu nguyên; kết quả chỉ còn một số nguyên primitive.

Ví dụ:

~~~java
BigDecimal input = new BigDecimal("00123.4500");

System.out.println(input);          // 123.4500
System.out.println(input.scale());  // 4
System.out.println(input.intValue()); // 123
~~~

Dù nhập `00123.4500`, đối tượng `BigDecimal` không còn giữ các số 0 đứng trước trong cách `toString()` chuẩn. Sau khi gọi `intValue()`, chữ số lẻ `.4500` cũng mất. Không thể đảo ngược từ `123` để biết giá trị gốc từng là `123.45`, `123.99` hay `123.000`.

Đặc biệt:

~~~java
new BigDecimal("1.000").intValueExact(); // thành công: 1
new BigDecimal("1.001").intValueExact(); // lỗi: còn phần lẻ khác 0
~~~

Làm tròn khi tạo `BigDecimal` bằng `MathContext` cũng có thể **mất dữ liệu trước** khi gọi `intValueExact()`. Ví dụ nếu đầu vào `1.001` bị làm tròn thành `1.0` ở bước trước thì phương thức Exact ở bước sau chỉ biết kiểm tra giá trị **đang có**, không thể phát hiện phần lẻ đã bị loại. Do đó hãy kiểm tra tính hợp lệ trước bước làm tròn ngoài ý muốn.

## 7. Các lỗi phổ biến trong ứng dụng

**Sai lầm 1 — Đổi giá tiền sang `int` bằng `intValue()` rồi tưởng đã làm tròn.** Với `12.75`, kết quả là `12`, có thể gây sai lệch khi thống kê hoặc hạch toán. Định nghĩa rõ đơn vị (đồng, cent, hay đơn vị nhỏ nhất), chính sách làm tròn và thời điểm làm tròn. Nếu cần biểu diễn tiền theo đơn vị nhỏ nhất bằng số nguyên, hãy nhân theo đúng hệ số, làm tròn theo hợp đồng rồi dùng `longValueExact()` để kiểm tra.

**Sai lầm 2 — Kiểm tra `BigDecimal` không âm rồi tin phép chuyển sang `int` cũng không âm.** `2147483648` là số dương nhưng `intValue()` thành số âm do tràn phạm vi. Đối với dữ liệu không thể mất như ID hoặc giới hạn tài nguyên, dùng chuyển đổi Exact và kiểm tra miền hợp lệ của nghiệp vụ.

**Sai lầm 3 — Chỉ kiểm tra giá trị ≤ `Integer.MAX_VALUE`.** Ngoài giới hạn trên còn có giới hạn dưới và phần thập phân. Kiểm tra tay cũng dễ bỏ sót lỗi; `intValueExact()` giải quyết cả hai điều kiện cốt lõi.

**Sai lầm 4 — Tưởng `longValue()` an toàn vì `long` rất rộng.** Một `BigDecimal` vẫn có thể vượt 64 bit; `9223372036854775808` chuyển sang `long` thành `Long.MIN_VALUE`.

**Sai lầm 5 — Bắt mọi `ArithmeticException` rồi trả `0`.** `0` có thể là giá trị hợp lệ và che giấu trường hợp dữ liệu bị lỗi. Hãy phân biệt dữ liệu không nguyên hoặc ngoài miền theo yêu cầu nghiệp vụ và giữ thông tin lỗi hữu ích.

**Sai lầm 6 — Dùng `setScale(0, DOWN)` khi yêu cầu là không cho phép phần lẻ.** `DOWN` cố tình cắt phần lẻ; dùng `intValueExact()` hoặc `longValueExact()` trực tiếp nếu phần lẻ phải bị từ chối.

**Sai lầm 7 — Cho rằng chuyển từ `double` sang `BigDecimal` khôi phục giá trị gốc.** `new BigDecimal(double)` phản ánh chính xác giá trị nhị phân của `double`, có thể khác con số thập phân người dùng dự định. Khi đầu vào là văn bản decimal, ưu tiên parse từ chuỗi chính xác; đừng bỏ qua nguồn dữ liệu trước khi chọn phương thức chuyển kiểu.

**Sai lầm 8 — Dùng `longValue()` để kiểm tra số có vừa long không.** Phương thức này **luôn trả về một `long`** với giá trị có thể sai do cắt/tràn. Chính `longValueExact()` mới là phép kiểm tra chuyển đổi đầy đủ.

## 8. Checklist lựa chọn API

| Nhu cầu | Phương án | Có kiểm tra phần lẻ? | Có kiểm tra phạm vi int/long? |
| --- | --- | --- | --- |
| Chấp nhận cắt phần lẻ và nguy cơ mất bit | `intValue()` / `longValue()` | Không | Không |
| Chỉ nhận số nguyên vừa `int` | `intValueExact()` | Có | Có, 32 bit |
| Chỉ nhận số nguyên vừa `long` | `longValueExact()` | Có | Có, 64 bit |
| Làm tròn có chủ đích, rồi chuyển an toàn | `setScale(0, mode).intValueExact()` hoặc `...longValueExact()` | Làm tròn theo mode đã chọn | Có |
| Cắt phần lẻ nhưng không cho phép tràn | `setScale(0, DOWN).longValueExact()` | Chủ động cắt phần lẻ | Có |
| Giữ số nguyên chính xác, kể cả rất lớn | `toBigIntegerExact()` | Có | Không giới hạn vào 32/64 bit |
| Chấp nhận cắt phần lẻ thành BigInteger | `toBigInteger()` | Không | Không giới hạn vào 32/64 bit |

Trong code review, hãy tự hỏi: **phần thập phân có được phép bỏ đi không? Có quy tắc làm tròn nào bắt buộc không? Giá trị sau cùng có cần nằm đúng trong 32 hoặc 64 bit không?** Nếu một phép chuyển đổi sai cần được phát hiện, các phương thức `...ValueExact()` là lựa chọn mặc định phù hợp.

## 9. Câu hỏi luyện tập

**Câu 1.** `new BigDecimal("-7.99").intValue()` là bao nhiêu? Có phải làm tròn xuống không?

**Đáp án:** `-7`. Đây là cắt về 0; làm tròn theo `FLOOR` tới số nguyên sẽ là `-8`.

**Câu 2.** Vì sao `new BigDecimal("4294967296").intValue()` là `0` nhưng `longValue()` vẫn cho `4294967296`?

**Đáp án:** `4294967296 = 2^32` vượt phạm vi `int`; 32 bit thấp đều là 0. Giá trị đó vẫn nằm trong phạm vi `long`.

**Câu 3.** `new BigDecimal("4.000").intValueExact()` có ném lỗi không?

**Đáp án:** Không, kết quả là `4` vì giá trị toán học là số nguyên. Scale 3 không phải điều kiện thất bại; phần lẻ khác 0 mới là vấn đề.

**Câu 4.** Muốn biến `9.6` thành số nguyên `10` nhưng vẫn phát hiện tràn `int`, viết gì?

~~~java
int result = new BigDecimal("9.6")
        .setScale(0, RoundingMode.HALF_UP)
        .intValueExact();
~~~

**Câu 5.** `new BigDecimal("9223372036854775808").longValue()` có ném `ArithmeticException` không? `longValueExact()` thì sao?

**Đáp án:** `longValue()` không ném và trả `-9223372036854775808`. `longValueExact()` ném `ArithmeticException` vì giá trị dương đầu vào vượt `Long.MAX_VALUE`.

## 10. Tài liệu tham khảo

- [BigDecimal — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html): mô tả `intValue()`, `longValue()`, `intValueExact()`, `longValueExact()`, `toBigInteger()` và `toBigIntegerExact()`.
- [RoundingMode — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/RoundingMode.html): quy tắc làm tròn, đặc biệt `DOWN`, `HALF_UP`, `HALF_EVEN` và `UNNECESSARY`.
- [Integer và Long — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): hằng giới hạn kiểu số nguyên Java; với 64 bit tham khảo [Long](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Long.html).

**Ghi nhớ:** `intValue()`/`longValue()` thuận tiện nhưng **có thể âm thầm mất phần lẻ và bit cao**. `intValueExact()`/`longValueExact()` giúp giữ nguyên hợp đồng dữ liệu bằng cách báo lỗi nếu số không nguyên hoặc không vừa kiểu đích. Làm tròn có chủ đích là **bước riêng biệt**, cần quy tắc nghiệp vụ rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-conversions-005">Các phương thức *ValueExact kiểm tra phần lẻ và giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Các phương thức `*ValueExact` của BigDecimal: kiểm tra phần lẻ và giới hạn kiểu đích

Khi chuyển `BigDecimal` sang `byte`, `short`, `int`, `long` hoặc `BigInteger`, cần quyết định rõ có **cho phép mất thông tin** hay không. Nhóm phương thức `byteValueExact()`, `shortValueExact()`, `intValueExact()`, `longValueExact()` và `toBigIntegerExact()` dành cho trường hợp yêu cầu **kết quả là số nguyên chính xác**.

**Quy tắc cốt lõi:** phép chuyển đổi `*Exact` chỉ thành công nếu giá trị `BigDecimal` không có **phần lẻ khác 0**; đối với các kiểu nguyên có độ rộng cố định, giá trị còn phải **nằm trong phạm vi biểu diễn của kiểu đích**. Nếu không đạt điều kiện, phương thức ném `ArithmeticException` thay vì tự cắt phần lẻ, làm tròn hoặc tràn số âm thầm.

```java
new BigDecimal("42.000").intValueExact(); // 42: chính xác
new BigDecimal("42.100").intValueExact(); // ArithmeticException: phần lẻ
new BigDecimal("2147483648").intValueExact(); // ArithmeticException: vượt int
```

Đoạn trên minh họa các biểu thức riêng lẻ; hai dòng cuối ném ngoại lệ nếu được thực thi mà không có xử lý.

## 1. Hai điều kiện độc lập: số nguyên về giá trị và vừa phạm vi

`BigDecimal` dùng biểu diễn `unscaledValue × 10^(-scale)`. `scale > 0` thường có nghĩa là có vị trí dành cho chữ số thập phân, **nhưng không chứng minh số có phần lẻ khác 0**.

Ví dụ:

| Giá trị `BigDecimal` | Scale | Là số nguyên về giá trị? | `intValueExact()` |
|---|---:|---|---|
| `"42"` | 0 | Có | `42` |
| `"42.000"` | 3 | Có: các chữ số sau dấu đều bằng 0 | `42` |
| `"42.100"` | 3 | Không: phần lẻ 0.100 | Ném `ArithmeticException` |
| `"-2.75"` | 2 | Không | Ném `ArithmeticException` |
| `"0.000"` | 3 | Có, bằng 0 | `0` |
| `"1E+3"` | -3 | Có, bằng 1000 | `1000` |
| `"2147483647"` | 0 | Có, vừa `int` | `2147483647` |
| `"2147483648"` | 0 | Có, nhưng vượt `int` | Ném `ArithmeticException` |

Lưu ý: phép chuyển chính xác kiểm tra **phần lẻ theo giá trị**, không đơn giản đòi `scale() == 0`. Vì thế `42.000` hợp lệ và `1E+3` cũng hợp lệ.

Hai điều kiện cần xét theo thứ tự logic:

1. **Tính nguyên:** có tồn tại giá trị nguyên bằng chính xác `BigDecimal` đầu vào không? `42.000` có, `42.100` không.
2. **Phạm vi:** số nguyên đó có thể được biểu diễn đúng trong `byte`, `short`, `int` hoặc `long` hay không? `2147483648` là số nguyên nhưng không thuộc `int`.

Không cần đoán ngoại lệ phát sinh vì lý do nào từ thông báo lỗi: API cam kết `ArithmeticException` khi không chuyển được chính xác, còn nội dung message có thể khác theo phiên bản triển khai. Nếu ứng dụng cần thông báo nghiệp vụ riêng cho “có phần lẻ” và “quá giới hạn”, hãy kiểm tra riêng từng điều kiện trước hoặc giải thích rõ chính sách sau khi bắt ngoại lệ.

## 2. Các phương thức chuyển đổi chính xác

| Phương thức | Kiểu trả về | Phạm vi kiểu đích | Điều kiện để thành công |
|---|---|---|---|
| `byteValueExact()` | `byte` | -128 đến 127 | Giá trị nguyên, trong phạm vi `byte` |
| `shortValueExact()` | `short` | -32768 đến 32767 | Giá trị nguyên, trong phạm vi `short` |
| `intValueExact()` | `int` | -2147483648 đến 2147483647 | Giá trị nguyên, trong phạm vi `int` |
| `longValueExact()` | `long` | -9223372036854775808 đến 9223372036854775807 | Giá trị nguyên, trong phạm vi `long` |
| `toBigIntegerExact()` | `BigInteger` | Không có giới hạn bit cố định | Giá trị nguyên; còn phụ thuộc giới hạn tài nguyên thực tế |

Những phương thức trên **không có tham số `RoundingMode`**: nếu giá trị không chuyển được chính xác, bạn phải xử lý lỗi hoặc chủ động làm tròn trước bằng một bước riêng đã được quy định.

### Ví dụ với `byte` và `short`

```java
new BigDecimal("127.00").byteValueExact(); // 127
new BigDecimal("128").byteValueExact();    // ArithmeticException
new BigDecimal("-128").byteValueExact();   // -128
new BigDecimal("-129").byteValueExact();   // ArithmeticException

new BigDecimal("32767").shortValueExact(); // 32767
new BigDecimal("32768").shortValueExact(); // ArithmeticException
```

`"127.00"` không bị từ chối chỉ vì scale là 2; giá trị vẫn chính xác bằng số nguyên 127. `"128"` bị từ chối vì `byte` tối đa là 127, dù hoàn toàn không có phần lẻ.

### Ví dụ với `int` và `long`

```java
new BigDecimal("2147483647").intValueExact(); // 2147483647
new BigDecimal("2147483648").intValueExact(); // ArithmeticException

new BigDecimal("9223372036854775807").longValueExact(); // Long.MAX_VALUE
new BigDecimal("9223372036854775808").longValueExact(); // ArithmeticException
```

Ở giới hạn âm cũng tương tự: giá trị nhỏ hơn `Integer.MIN_VALUE` hoặc `Long.MIN_VALUE` không thể chuyển chính xác sang kiểu tương ứng.

### Trường hợp `toBigIntegerExact()`

`BigInteger` có độ chính xác số nguyên tùy ý nên **không gặp giới hạn 8/16/32/64 bit**. Tuy nhiên, nó vẫn không thể giữ phần thập phân:

```java
new BigDecimal("999999999999999999999.000").toBigIntegerExact();
// Trả về BigInteger có giá trị 999999999999999999999

new BigDecimal("999999999999999999999.001").toBigIntegerExact();
// ArithmeticException: có phần lẻ khác 0
```

Có thể dùng `toBigIntegerExact()` khi cần chuyển một giá trị thập phân sang số nguyên lớn mà vẫn bảo toàn **toàn bộ giá trị**.

## 3. Khác biệt với các phương thức chuyển đổi thông thường

Nhóm **không có hậu tố `Exact`** có thể trả về giá trị mà không báo lỗi khi mất thông tin:

- `byteValue()`, `shortValue()`, `intValue()`, `longValue()` trả phần nguyên theo hướng về 0; nếu số vượt phạm vi kiểu đích, các bit thấp của phần nguyên được giữ theo độ rộng tương ứng, làm giá trị có thể đổi dấu hoặc không còn bằng số ban đầu.
- `toBigInteger()` loại phần lẻ theo hướng về 0, không kiểm tra xem phần lẻ có bị mất hay không.
- `floatValue()` và `doubleValue()` chuyển sang số thực nhị phân, có thể mất chữ số chính xác, cho kết quả vô cùng khi quá lớn hoặc về 0 khi quá nhỏ. **Không có phương thức `floatValueExact()` hoặc `doubleValueExact()` tương ứng trong `BigDecimal`.**

Ví dụ:

| Giá trị đầu vào | Gọi thông thường | Kết quả | Gọi `Exact` |
|---|---|---|---|
| `"42.1"` | `intValue()` | `42` | `intValueExact()` ném ngoại lệ |
| `"-2.75"` | `intValue()` | `-2` | `intValueExact()` ném ngoại lệ |
| `"2147483648"` | `intValue()` | `-2147483648` | `intValueExact()` ném ngoại lệ |
| `"128"` | `byteValue()` | `-128` | `byteValueExact()` ném ngoại lệ |
| `"1.99"` | `toBigInteger()` | `1` | `toBigIntegerExact()` ném ngoại lệ |
| `"-1.99"` | `toBigInteger()` | `-1` | `toBigIntegerExact()` ném ngoại lệ |

Ở ví dụ `2147483648`, `intValue()` trả `-2147483648` vì chỉ lấy 32 bit thấp của phần nguyên, được diễn giải theo kiểu `int` có dấu. Đây **không phải** một phép làm tròn hợp lệ theo nghiệp vụ và không phải cách kiểm tra số có vừa kiểu dữ liệu.

**Hệ quả:** dùng `intValue()` để chuyển giá tiền, mã định danh, số lượng, dữ liệu kế toán hoặc trường cần tính toàn vẹn có thể âm thầm bỏ chữ số hoặc tạo giá trị sai. Nếu yêu cầu là “chỉ chấp nhận số nguyên trong phạm vi”, `intValueExact()` là lựa chọn trực tiếp.

## 4. Ví dụ Java 17+ chạy độc lập và stdout có kiểm chứng

Tạo tệp `BigDecimalExactConversionDemo.java`:

```java
import java.math.BigDecimal;
import java.util.function.Supplier;

public class BigDecimalExactConversionDemo {
    private static void check(String label, Supplier<?> conversion) {
        try {
            System.out.println(label + " -> " + conversion.get());
        } catch (ArithmeticException ex) {
            System.out.println(label + " -> ArithmeticException");
        }
    }

    public static void main(String[] args) {
        check("42.000 int",
                () -> new BigDecimal("42.000").intValueExact());
        check("42.100 int",
                () -> new BigDecimal("42.100").intValueExact());
        check("-2.75 int",
                () -> new BigDecimal("-2.75").intValueExact());

        check("2147483647 int",
                () -> new BigDecimal("2147483647").intValueExact());
        check("2147483648 int",
                () -> new BigDecimal("2147483648").intValueExact());

        check("127.00 byte",
                () -> new BigDecimal("127.00").byteValueExact());
        check("128 byte",
                () -> new BigDecimal("128").byteValueExact());
        check("32767 short",
                () -> new BigDecimal("32767").shortValueExact());
        check("32768 short",
                () -> new BigDecimal("32768").shortValueExact());

        check("long max",
                () -> new BigDecimal("9223372036854775807").longValueExact());
        check("long overflow",
                () -> new BigDecimal("9223372036854775808").longValueExact());

        check("big integer",
                () -> new BigDecimal("999999999999999999999.000")
                        .toBigIntegerExact());
        check("big fraction",
                () -> new BigDecimal("999999999999999999999.001")
                        .toBigIntegerExact());

        System.out.println("42.1 intValue = "
                + new BigDecimal("42.1").intValue());
        System.out.println("2147483648 intValue = "
                + new BigDecimal("2147483648").intValue());
        System.out.println("128 byteValue = "
                + new BigDecimal("128").byteValue());
        System.out.println("zero scale=3 -> int = "
                + new BigDecimal("0.000").intValueExact());
        System.out.println("scientific -> int = "
                + new BigDecimal("1E+3").intValueExact());
    }
}
```

Biên dịch và chạy (Java 17 trở lên):

```bash
javac BigDecimalExactConversionDemo.java
java BigDecimalExactConversionDemo
```

Kết quả stdout:

```text
42.000 int -> 42
42.100 int -> ArithmeticException
-2.75 int -> ArithmeticException
2147483647 int -> 2147483647
2147483648 int -> ArithmeticException
127.00 byte -> 127
128 byte -> ArithmeticException
32767 short -> 32767
32768 short -> ArithmeticException
long max -> 9223372036854775807
long overflow -> ArithmeticException
big integer -> 999999999999999999999
big fraction -> ArithmeticException
42.1 intValue = 42
2147483648 intValue = -2147483648
128 byteValue = -128
zero scale=3 -> int = 0
scientific -> int = 1000
```

Hàm `check` bắt `ArithmeticException` để minh họa từng trường hợp thất bại mà không dừng toàn bộ chương trình. Nó cố ý in **tên loại ngoại lệ** thay vì `ex.getMessage()`, vì câu chữ thông báo không phải hợp đồng ổn định cần phụ thuộc.

Quan sát hai kết luận từ chương trình: `42.000` và `1E+3` chuyển chính xác dù scale lần lượt là 3 và -3; ngược lại `2147483648` không thể chuyển đúng sang `int`, dù là một số nguyên thuần túy.

## 5. Khi nào nên làm tròn trước rồi mới chuyển?

`*ValueExact` **không tự áp dụng chính sách làm tròn**. Nếu nghiệp vụ cho phép làm tròn, hãy tách quyết định làm tròn thành bước hiển thị rõ trong mã, rồi chuyển chính xác:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal quantity = new BigDecimal("12.7");

int rounded = quantity
        .setScale(0, RoundingMode.HALF_UP)
        .intValueExact(); // 13

int truncated = quantity
        .setScale(0, RoundingMode.DOWN)
        .intValueExact(); // 12
```

Đây là **đoạn mã minh họa** để đặt trong phương thức Java thích hợp, không phải một lớp Java có `main` độc lập. `setScale(0, ...) ` làm rõ chính sách xử lý phần thập phân. `intValueExact()` sau đó vẫn **kiểm tra phạm vi `int`**: làm tròn trước không bảo đảm một số quá lớn trở nên vừa kiểu.

Các chế độ làm tròn có ý nghĩa khác nhau; ví dụ `HALF_UP` làm tròn tới số nguyên gần nhất và chọn tăng độ lớn tại điểm giữa, `DOWN` bỏ phần lẻ theo hướng về 0. Việc chọn chế độ nào phải theo hợp đồng nghiệp vụ, không phải sở thích của lập trình viên.

Nếu nghiệp vụ **không cho phép mất bất kỳ phần lẻ nào**, gọi thẳng `intValueExact()` là đúng; đừng làm tròn trước để “lách” kiểm tra tính chính xác.

## 6. Dùng trong nhập liệu, API và xử lý lỗi

Một ví dụ điển hình: nhận số lượng từ chuỗi JSON, CSV hoặc database dưới dạng `BigDecimal`, rồi chuyển sang `int`. Nếu hệ thống yêu cầu số lượng phải là số nguyên không âm và không vượt số lượng tối đa, hãy làm các bước riêng:

1. **Xác thực miền nghiệp vụ:** số lượng phải không âm hoặc phải dương, phải đáp ứng giới hạn nghiệp vụ. `intValueExact()` chỉ kiểm tra tính nguyên và giới hạn kỹ thuật, **không** kiểm tra “phải dương”, “không được bằng 0” hay “tối đa 100 sản phẩm”.
2. **Chuyển chính xác:** dùng `intValueExact()`, `longValueExact()`, v.v. để loại phần lẻ không hợp lệ và tràn kiểu.
3. **Ánh xạ lỗi:** bắt `ArithmeticException` tại ranh giới phù hợp để tạo lỗi nhập liệu rõ ràng, thay vì để ứng dụng xử lý sai dữ liệu.

Ví dụ hàm có thể đặt trong lớp xử lý yêu cầu:

```java
static int parseQuantity(BigDecimal raw) {
    if (raw == null) {
        throw new IllegalArgumentException("Quantity is required");
    }
    final int quantity;
    try {
        quantity = raw.intValueExact();
    } catch (ArithmeticException ex) {
        throw new IllegalArgumentException(
                "Quantity must be a 32-bit integer", ex);
    }
    if (quantity < 0 || quantity > 100) {
        throw new IllegalArgumentException(
                "Quantity must be between 0 and 100");
    }
    return quantity;
}
```

Đoạn hàm trên dành cho một lớp Java thích hợp (hoặc đưa vào ví dụ có `main`) và không tự chạy riêng như một tệp. Nó phân biệt **lỗi chuyển đổi** (giá trị thập phân hoặc vượt `int`) với **lỗi quy tắc miền** (dù là số nguyên nhưng ngoài 0–100). Tránh chỉ viết `int x = raw.intValue()` vì có thể âm thầm thay đổi giá trị mà không phát hiện.

Nếu cần giữ số nguyên rất lớn mà vẫn từ chối phần lẻ, hãy dùng `toBigIntegerExact()` thay vì ép về `long` hoặc `int`.

## 7. Phân biệt chính xác số học với biểu diễn dữ liệu

Một số ngộ nhận phổ biến:

- **`scale() > 0` không có nghĩa phép chuyển `Exact` sẽ thất bại.** `"5.000"` là số nguyên về giá trị.
- **`scale() == 0` không bảo đảm phép chuyển `Exact` thành công.** `"999999999999999999999"` lớn hơn phạm vi `long` dù không có phần lẻ.
- **`precision()` không thay thế phép kiểm tra phạm vi.** Precision cho biết số chữ số có nghĩa, còn giá trị âm/dương, scale và giới hạn đích vẫn cần xét. Gọi đúng phương thức `*Exact` là cách ngắn gọn để API thực thi ràng buộc.
- **Phần lẻ khác 0 cũng bao gồm giá trị rất nhỏ.** `"0.0000001"` không thể chuyển thành 0 bằng `intValueExact()`, dù `intValue()` cho 0.
- **`-0.000` vẫn bằng số nguyên 0.** Giá trị số học bằng 0, nên chuyển chính xác thành 0, bất kể cách viết ban đầu.
- **`BigDecimal` bất biến.** Các phương thức chuyển đổi trả về giá trị kiểu đích; chúng không chỉnh scale hay làm tròn chính đối tượng `BigDecimal`.

Ngoài ra, `BigDecimal` có thể được tạo từ `double`. `new BigDecimal(0.1)` thể hiện chính xác giá trị nhị phân xấp xỉ của `double`, khác với giá trị thập phân mong muốn `0.1`. Khi dữ liệu đầu vào vốn là thập phân, nên ưu tiên `new BigDecimal("0.1")` hoặc nguồn decimal phù hợp; không trộn vấn đề **nguồn số có chính xác hay không** với vấn đề **chuyển sang kiểu nguyên có chính xác hay không**.

## 8. Cách chọn nhanh phương thức

| Yêu cầu | Cách làm |
|---|---|
| Chỉ chấp nhận số nguyên vừa `byte` | `byteValueExact()` |
| Chỉ chấp nhận số nguyên vừa `short` | `shortValueExact()` |
| Chỉ chấp nhận số nguyên vừa `int` | `intValueExact()` |
| Chỉ chấp nhận số nguyên vừa `long` | `longValueExact()` |
| Chấp nhận số nguyên vượt 64 bit, từ chối phần lẻ | `toBigIntegerExact()` |
| Cho phép làm tròn theo chính sách rõ ràng | `setScale(0, roundingMode)` rồi `*ValueExact()` |
| Chủ đích cắt phần lẻ và chấp nhận kết quả có thể mất thông tin | `intValue()`, `longValue()`, `toBigInteger()` tương ứng; ghi rõ ý nghĩa và rủi ro |
| Cần số thực `double` / `float` | Dùng `doubleValue()` / `floatValue()`, chấp nhận khả năng mất độ chính xác hoặc kiểm tra riêng điều kiện ứng dụng |

## 9. Tự kiểm tra

**Câu 1.** `new BigDecimal("7.000").intValueExact()` có ném lỗi không?  
**Đáp án:** Không; kết quả là 7 vì phần lẻ bằng 0, dù scale bằng 3.

**Câu 2.** `new BigDecimal("7.001").intValueExact()` trả gì?  
**Đáp án:** Ném `ArithmeticException` vì có phần lẻ khác 0.

**Câu 3.** `new BigDecimal("128").byteValueExact()` so với `byteValue()` thế nào?  
**Đáp án:** `byteValueExact()` ném `ArithmeticException` do 128 vượt `byte`; `byteValue()` trả -128 do chỉ giữ 8 bit thấp.

**Câu 4.** `new BigDecimal("2147483648").toBigIntegerExact()` có thành công không?  
**Đáp án:** Có; `BigInteger` không bị giới hạn bởi 32 bit. `intValueExact()` trên cùng giá trị sẽ ném ngoại lệ.

**Câu 5.** Khi nào `new BigDecimal("12.7").setScale(0, RoundingMode.HALF_UP).intValueExact()` là lựa chọn đúng?  
**Đáp án:** Khi quy tắc nghiệp vụ **cho phép và yêu cầu** làm tròn tới đơn vị theo `HALF_UP`; kết quả là 13. Nếu yêu cầu là nhập số nguyên nguyên trạng, phải từ chối `12.7` bằng `intValueExact()`.

**Câu 6.** `new BigDecimal("1E+3").intValueExact()` trả gì?  
**Đáp án:** 1000. Scale âm không cản trở chuyển đổi chính xác nếu giá trị số học là số nguyên và nằm trong phạm vi.

**Ghi nhớ:** `*ValueExact` là hàng rào chống mất dữ liệu âm thầm khi chuyển `BigDecimal` sang **số nguyên**: phải **không có phần lẻ** và **vừa phạm vi kiểu đích** (ngoại trừ `BigInteger` không có giới hạn bit cố định). Đừng nhầm việc cắt số, làm tròn và chuyển đổi chính xác là cùng một thao tác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-conversions-006">toBigInteger khác toBigIntegerExact</a>

<details>
<summary>Xem chi tiết</summary>

# `toBigInteger()` khác `toBigIntegerExact()`: chuyển `BigDecimal` sang số nguyên chính xác

## 1. Mục tiêu bài học

`BigDecimal` biểu diễn các giá trị thập phân có độ chính xác tùy ý; `BigInteger` biểu diễn số nguyên có độ lớn tùy ý. Khi chuyển từ `BigDecimal` sang `BigInteger`, Java cung cấp hai lựa chọn có **hợp đồng khác nhau**:

- `toBigInteger()`: **loại bỏ phần lẻ** của giá trị thập phân, đi về **0**, rồi trả về `BigInteger`. Phép chuyển đổi này có thể mất dữ liệu mà không báo lỗi.
- `toBigIntegerExact()`: chỉ chuyển đổi nếu giá trị có thể biểu diễn **chính xác bằng số nguyên**. Nếu có phần lẻ khác 0, ném `ArithmeticException`.

Đây là khác biệt về **chính sách xử lý dữ liệu**, không phải chỉ khác tên phương thức. Sau bài học, bạn có thể dự đoán kết quả với số dương/âm, phần lẻ, các số 0 cuối, scientific notation; viết kiểm tra đầu vào đúng yêu cầu nghiệp vụ; và tránh nhầm lẫn với `intValue()`, `intValueExact()`, `RoundingMode` hoặc `Math.floor`.

## 2. Hai phương thức trả về cùng kiểu, nhưng khác cách xử lý phần lẻ

Trong `java.math.BigDecimal` có các phương thức:

```java
public BigInteger toBigInteger();
public BigInteger toBigIntegerExact();
```

Cả hai trả về `java.math.BigInteger` có **độ lớn số nguyên tùy ý**, thay vì giới hạn ở 32 bit (`int`) hay 64 bit (`long`). Nhưng hãy chú ý đến phần lẻ:

| `BigDecimal` đầu vào | `toBigInteger()` | `toBigIntegerExact()` | Lý do |
|---|---:|---:|---|
| `12` | `12` | `12` | Vốn là số nguyên |
| `12.00` | `12` | `12` | Phần thập phân chỉ gồm 0 |
| `12.99` | `12` | **Ném `ArithmeticException`** | Có phần lẻ khác 0 |
| `-12.99` | `-12` | **Ném `ArithmeticException`** | Có phần lẻ khác 0 |
| `-0.75` | `0` | **Ném `ArithmeticException`** | Cắt phần lẻ về 0 |
| `0.000` | `0` | `0` | Giá trị số học bằng 0 |
| `1E+3` | `1000` | `1000` | Chính xác là một số nguyên |
| `1E-3` | `0` | **Ném `ArithmeticException`** | Chính xác là 0.001 |

**Điểm cốt lõi:** `Exact` có nghĩa “không làm mất phần thập phân có nghĩa”. Nó **không đòi hỏi scale của `BigDecimal` phải bằng 0**: `12.00` có scale 2 nhưng vẫn là giá trị nguyên 12 và chuyển đổi chính xác được.

## 3. Cơ chế: bỏ phần lẻ theo hướng 0 hay bảo toàn giá trị

### 3.1. `toBigInteger()` cắt về 0

Xét biểu thức `new BigDecimal("12.99").toBigInteger()`: kết quả là `12`. Phần `.99` biến mất; không có phép làm tròn đến số nguyên gần nhất.

Với giá trị âm, kết quả là `-12`, **không phải `-13`**:

```text
-13 ----- -12.99 ----- -12 ----- 0 ----- 12 ----- 12.99 ----- 13
                        ^                         ^
             toBigInteger(-12.99)       toBigInteger(12.99)
```

Đi về 0 khác với đi về âm vô cực:

- `toBigInteger(-12.99)` = `-12`: bỏ phần lẻ, hướng về 0.
- `Math.floor(-12.99)` = `-13.0`: lấy số nguyên theo hướng −∞.
- `Math.ceil(-12.99)` = `-12.0`: lấy số nguyên theo hướng +∞.

Mặc dù `toBigInteger()` và `Math.ceil()` cho cùng phần nguyên ở ví dụ số âm này, chúng **không phải hai phép toán tương đương** cho cả số dương và số âm.

Theo nghĩa số học, với `x` dương: `toBigInteger(x)` tương ứng `floor(x)`; với `x` âm: nó tương ứng `ceil(x)`; với `x` nguyên: giữ nguyên. Nếu muốn biểu đạt trực tiếp việc bỏ phần thập phân bằng một `RoundingMode`, tên tương ứng là `RoundingMode.DOWN` (hướng về 0), không phải `RoundingMode.FLOOR`.

### 3.2. `toBigIntegerExact()` không chấp nhận mất phần lẻ

`toBigIntegerExact()` chỉ trả về nếu chuyển đổi giữ nguyên **giá trị số học**. Ví dụ:

```java
new BigDecimal("42.000").toBigIntegerExact(); // BigInteger 42
new BigDecimal("-42.000").toBigIntegerExact(); // BigInteger -42
new BigDecimal("42.001").toBigIntegerExact(); // ArithmeticException
```

Hai số `42.000` và `-42.000` đều có scale 3 nhưng có phần thập phân bằng 0. Vì vậy, **không được kiểm tra `scale() == 0` làm điều kiện duy nhất** để quyết định có chuyển đổi chính xác được không.

Nếu có phần lẻ khác 0, Java ném `ArithmeticException` để lập trình viên có cơ hội xử lý trường hợp đầu vào không đáp ứng hợp đồng. Nếu ứng dụng cần chấp nhận số thập phân và cố ý làm tròn, phải lựa chọn chính sách làm tròn riêng; không nên dùng `toBigInteger()` như một bước làm tròn ngầm.

## 4. Chương trình Java hoàn chỉnh: kết quả từng trường hợp

**Tệp `BigDecimalToBigIntegerDemo.java`** (Java 17+):

```java
import java.math.BigDecimal;
import java.math.BigInteger;

public class BigDecimalToBigIntegerDemo {
    public static void main(String[] args) {
        String[] inputs = {
                "12.99",
                "-12.99",
                "12.000",
                "0.00",
                "1E+3",
                "1E-3"
        };

        for (String input : inputs) {
            BigDecimal amount = new BigDecimal(input);
            BigInteger truncated = amount.toBigInteger();

            System.out.print("input=" + amount.toPlainString()
                    + ", trunc=" + truncated + ", exact=");

            try {
                System.out.println(amount.toBigIntegerExact());
            } catch (ArithmeticException ex) {
                System.out.println("ArithmeticException");
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac BigDecimalToBigIntegerDemo.java
java BigDecimalToBigIntegerDemo
```

Kết quả:

```text
input=12.99, trunc=12, exact=ArithmeticException
input=-12.99, trunc=-12, exact=ArithmeticException
input=12.000, trunc=12, exact=12
input=0.00, trunc=0, exact=0
input=1000, trunc=1000, exact=1000
input=0.001, trunc=0, exact=ArithmeticException
```

Các dòng này cho thấy ba điều:

1. `toBigInteger()` **luôn cung cấp phần nguyên** của các giá trị bình thường trên; nó không cảnh báo việc mất `.99` hoặc `.001`.
2. `toBigIntegerExact()` thất bại **chỉ khi giá trị không nguyên** (hoặc không thể chuyển đổi chính xác vì giới hạn tài nguyên trong những trường hợp cực đoan), chứ không đơn thuần khi chuỗi chứa dấu chấm thập phân.
3. `toPlainString()` được dùng để hiển thị `1E+3` thành `1000`, `1E-3` thành `0.001`. Hai phương thức chuyển sang `BigInteger` luôn xuất ra số nguyên và không mang scale của đầu vào.

## 5. `Scale`, zero cuối và ký pháp khoa học có ảnh hưởng thế nào?

Một `BigDecimal` được mô tả bằng **unscaled value** và **scale**:

```text
giá trị = unscaledValue × 10^(-scale)

"12.000" => unscaledValue = 12000, scale = 3
"1E+3"  => unscaledValue = 1,     scale = -3
"1E-3"  => unscaledValue = 1,     scale = 3
```

Bài toán `toBigIntegerExact()` là xác định **giá trị số học cuối cùng có phải số nguyên hay không**, không phải tìm xem `scale` dương, âm hoặc bằng 0.

- Scale dương **vẫn có thể** chuyển chính xác nếu tất cả chữ số của phần lẻ đều là 0, ví dụ `12.000`.
- Scale bằng 0 là số nguyên đã biểu diễn trực tiếp, ví dụ `12`.
- Scale âm làm giá trị là bội số của một lũy thừa của 10, ví dụ `1E+3 = 1000`; nó vẫn nguyên.
- Scale dương với phần lẻ khác 0 sẽ làm `toBigIntegerExact()` thất bại, ví dụ `1E-3 = 0.001`.

**Không cần gọi `stripTrailingZeros()` trước `toBigIntegerExact()`.** Phương thức exact tự đánh giá tính nguyên của giá trị. Gọi `stripTrailingZeros()` có thể thay đổi scale và chuỗi hiển thị, nên chỉ dùng nếu nghiệp vụ thực sự cần chuẩn hóa biểu diễn.

Ví dụ `2.0` và `2.00` từ chuỗi có scale lần lượt 1 và 2. Cả hai chuyển sang cùng số nguyên `2` qua `toBigIntegerExact()` mà không mất **giá trị số học**. Tuy nhiên, kết quả `BigInteger` không còn nhớ đầu vào có một hay hai chữ số thập phân. “Exact” ở đây là exact về **giá trị**, không phải “giữ nguyên định dạng”.

## 6. `toBigInteger` khác cách làm tròn `setScale`

Nếu muốn làm tròn trước khi chuyển sang số nguyên, phải viết quy tắc rõ ràng:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;

BigDecimal x = new BigDecimal("-12.99");

System.out.println(x.toBigInteger()); // -12
System.out.println(x.setScale(0, RoundingMode.DOWN));    // -12
System.out.println(x.setScale(0, RoundingMode.FLOOR));   // -13
System.out.println(x.setScale(0, RoundingMode.CEILING)); // -12
System.out.println(x.setScale(0, RoundingMode.HALF_UP)); // -13
```

Đoạn mã này là ví dụ minh họa bên trong một phương thức có thể chạy (cần đặt imports ở đầu tệp). Với `x = -12.99`:

| Cách xử lý | Kết quả | Quy tắc |
|---|---:|---|
| `x.toBigInteger()` | `-12` | Cắt về 0, bỏ phần lẻ |
| `x.setScale(0, DOWN)` | `-12` | Làm tròn về 0 |
| `x.setScale(0, FLOOR)` | `-13` | Làm tròn về −∞ |
| `x.setScale(0, CEILING)` | `-12` | Làm tròn về +∞ |
| `x.setScale(0, HALF_UP)` | `-13` | Làm tròn tới gần nhất, quy tắc HALF_UP khi cách đều |

Khi đã làm tròn bằng `setScale(0, mode)`, có thể gọi `toBigIntegerExact()` ở bước cuối: giá trị đã có scale 0, nên bước chuyển không tiếp tục bỏ phần lẻ. Ví dụ:

```java
BigInteger rounded = x.setScale(0, RoundingMode.FLOOR)
                      .toBigIntegerExact(); // -13
```

Ở đây việc làm tròn là **có chủ đích** và được biểu đạt rõ trong mã. Ngược lại, `toBigIntegerExact()` trực tiếp từ `-12.99` sẽ ném ngoại lệ.

**Sai lầm phổ biến:** Vì một số tài liệu gọi thao tác cắt phần lẻ là “round down”, người học dễ đồng nhất nó với `FLOOR`. Trong `RoundingMode` của Java, `DOWN` nghĩa là về **0**, còn `FLOOR` nghĩa là về **âm vô cực**. Kiểm tra với số âm sẽ làm lộ ngay sự khác biệt.

## 7. Phân biệt với `intValue()`, `longValue()` và bản `Exact`

Sau `toBigInteger...`, ta nhận một `BigInteger` tùy ý độ lớn. Nếu đích cuối cùng lại là `int` hoặc `long`, cần kiểm tra **hai loại rủi ro độc lập**:

1. **Có phần lẻ không?** Nếu có, phép đổi sang số nguyên có thể mất phần thập phân.
2. **Có vượt giới hạn kiểu đích không?** Nếu có, chuyển sang `int`/`long` có thể mất các bit cao.

Đó là lý do các phương thức exact khác cũng quan trọng:

| Phương thức từ `BigDecimal` | Mất phần lẻ được phép? | Kiểm tra giới hạn `int`/`long`? |
|---|---|---|
| `toBigInteger()` | Có | Không áp dụng: đích là `BigInteger` |
| `toBigIntegerExact()` | Không | Không áp dụng: đích là `BigInteger` |
| `intValue()` / `longValue()` | Có | Không, có thể mất bit cao |
| `intValueExact()` / `longValueExact()` | Không | Có; ném `ArithmeticException` nếu ngoài miền |

Ví dụ khi số nguyên vượt `int`:

**Tệp `BigIntegerRangeDemo.java`:**

```java
import java.math.BigDecimal;
import java.math.BigInteger;

public class BigIntegerRangeDemo {
    public static void main(String[] args) {
        BigDecimal source = new BigDecimal("2147483648");
        BigInteger big = source.toBigIntegerExact();

        System.out.println("BigInteger=" + big);
        System.out.println("long=" + source.longValueExact());

        try {
            System.out.println("int=" + source.intValueExact());
        } catch (ArithmeticException ex) {
            System.out.println("intValueExact: ArithmeticException");
        }
    }
}
```

```bash
javac BigIntegerRangeDemo.java
java BigIntegerRangeDemo
```

```text
BigInteger=2147483648
long=2147483648
intValueExact: ArithmeticException
```

Giá trị `2_147_483_648` lớn hơn `Integer.MAX_VALUE` (`2_147_483_647`), nhưng vẫn vừa `long` và luôn có thể lưu bằng `BigInteger`. Dùng `toBigIntegerExact()` giúp tránh mất phần lẻ, nhưng **không tự động kiểm tra** giá trị có vừa `int` hoặc `long` khi chuyển tiếp; hãy dùng `big.intValueExact()` hoặc `big.longValueExact()` nếu cần.

## 8. Thực hành: kiểm tra số lượng phải là số nguyên

Giả sử hệ thống đọc một trường `quantity` từ dữ liệu thập phân. Yêu cầu: số lượng phải nguyên và không âm; tuyệt đối không cho phép `2.8` biến thành `2`.

**Tệp `WholeQuantityDemo.java`:**

```java
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

public class WholeQuantityDemo {
    public static BigInteger requireWholeQuantity(BigDecimal quantity) {
        Objects.requireNonNull(quantity, "quantity");

        if (quantity.signum() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }

        try {
            return quantity.toBigIntegerExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Quantity must be a whole number: " + quantity, ex);
        }
    }

    public static void main(String[] args) {
        BigDecimal[] values = {
                new BigDecimal("2.00"),
                new BigDecimal("2.8"),
                new BigDecimal("0"),
                new BigDecimal("-1.0")
        };

        for (BigDecimal value : values) {
            try {
                System.out.println(value + " -> "
                        + requireWholeQuantity(value));
            } catch (IllegalArgumentException ex) {
                System.out.println(value + " -> REJECTED");
            }
        }
    }
}
```

```bash
javac WholeQuantityDemo.java
java WholeQuantityDemo
```

```text
2.00 -> 2
2.8 -> REJECTED
0 -> 0
-1.0 -> REJECTED
```

Ở ví dụ này, việc bắt `ArithmeticException` có ý nghĩa: dữ liệu có phần lẻ vi phạm quy tắc nghiệp vụ nên được chuyển thành thông báo lỗi phù hợp. Không nên bắt rồi âm thầm thay bằng `toBigInteger()` vì sẽ biến một đầu vào không hợp lệ thành đầu vào “hợp lệ giả”.

Nếu hệ thống chỉ chấp nhận số lượng dương, bổ sung kiểm tra `quantity.signum() > 0`; nếu cần giới hạn tối đa, dùng `BigInteger.compareTo(maximum)` sau khi chuyển chính xác.

### Khi nào `toBigInteger()` là lựa chọn hợp lệ?

Trong một số bài toán, yêu cầu **cố ý bỏ phần thập phân**. Ví dụ, lấy phần nguyên của một phép đo đã xác định rõ quy tắc cắt về 0. Khi ấy `toBigInteger()` đúng yêu cầu; hãy đặt tên biến/hàm thể hiện rõ ý định, chẳng hạn `truncatedUnits`.

Còn với định danh, số lượng mặt hàng, số thứ tự, cấu hình giới hạn hoặc dữ liệu mà phần lẻ không hợp lệ, `toBigIntegerExact()` an toàn hơn vì thể hiện hợp đồng bằng mã và báo lỗi sớm.

## 9. Các trường hợp biên và lỗi thường gặp

### 9.1. Số âm gần 0

```java
BigDecimal x = new BigDecimal("-0.99");

System.out.println(x.toBigInteger()); // 0
// x.toBigIntegerExact() sẽ ném ArithmeticException.
```

Một giá trị âm có thể bị cắt về 0. Nếu hệ thống dựa vào dấu hoặc quy tắc chỉ số nhóm, mất thông tin dấu như vậy có thể gây lỗi logic.

### 9.2. Những số 0 cuối và scale khác nhau

```java
new BigDecimal("2.0").toBigIntegerExact();  // 2
new BigDecimal("2.00").toBigIntegerExact(); // 2
new BigDecimal("2.01").toBigIntegerExact(); // ArithmeticException
```

Tính exact dựa trên phần lẻ **có giá trị khác 0**, không phụ thuộc số chữ số đã nhập. `BigInteger` trả về không còn giữ scale.

### 9.3. Khởi tạo từ `double` có thể đưa vào sai số nhị phân

```java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal(0.1);
```

`a` biểu diễn chính xác `0.1` thập phân. `b` biểu diễn chính xác giá trị nhị phân của `double` gần 0.1, nên có nhiều chữ số phụ. **Không nên tạo `BigDecimal` từ `double` nếu cần giữ nguyên giá trị chữ số đầu vào.** Ưu tiên `BigDecimal(String)` cho dữ liệu thập phân hoặc cân nhắc `BigDecimal.valueOf(double)` khi đã có `double` và hiểu tính giới hạn của dữ liệu đó. Sau khi tạo đối tượng, phương thức chuyển đổi không thể khôi phục độ chính xác đã mất trước đó.

### 9.4. Đừng bỏ qua ngoại lệ

Ví dụ này đánh mất một validation quan trọng:

```java
// Cách không nên làm nếu quy tắc đòi hỏi một số nguyên chính xác:
try {
    return amount.toBigIntegerExact();
} catch (ArithmeticException ignored) {
    return amount.toBigInteger(); // Âm thầm cắt phần lẻ
}
```

Đây chỉ là minh họa đoạn thân một phương thức có biến `amount` kiểu `BigDecimal`. Nếu cần làm tròn, hãy xác định rounding mode rõ ràng và ghi nhận quyết định. Nếu cần từ chối, hãy phát sinh lỗi có ngữ cảnh.

### 9.5. Không lẫn ngoại lệ tính nguyên với `null` và dữ liệu không parse được

Nếu biến `BigDecimal` là `null`, gọi bất kỳ phương thức nào trong hai phương thức trên sẽ ném `NullPointerException`. Nếu chuỗi đầu vào không hợp lệ, `new BigDecimal(text)` ném `NumberFormatException` ngay lúc parse, trước khi đến giai đoạn chuyển `BigInteger`. Hai loại lỗi này khác với `ArithmeticException` do phần lẻ khác 0.

### 9.6. Giá trị rất lớn

`BigInteger` không giới hạn ở `Integer.MAX_VALUE` hay `Long.MAX_VALUE`, nhưng vẫn chịu giới hạn thực tế của bộ nhớ và dung lượng biểu diễn. Không coi “arbitrary precision” là “không bao giờ gặp giới hạn tài nguyên”. Cũng cần tránh chuyển tiếp về kiểu nguyên cố định mà không kiểm tra phạm vi.

## 10. Bài tập tự kiểm tra (có đáp án)

1. **`new BigDecimal("8.999").toBigInteger()` cho kết quả gì?**  
   `8`, vì cắt phần lẻ về 0.

2. **`new BigDecimal("-8.999").toBigInteger()` cho kết quả gì?**  
   `-8`, không phải `-9`: `toBigInteger()` không phải floor.

3. **`new BigDecimal("8.000").toBigIntegerExact()` có lỗi không?**  
   Không. Kết quả là `BigInteger` có giá trị `8`, dù scale đầu vào lớn hơn 0.

4. **`new BigDecimal("8.001").toBigIntegerExact()` làm gì?**  
   Ném `ArithmeticException` vì phần lẻ khác 0.

5. **`new BigDecimal("1E+5").toBigIntegerExact()` có phải lỗi vì viết theo ký pháp khoa học?**  
   Không. Kết quả là `100000`, một số nguyên chính xác.

6. **Giá trị `2.99` phải làm tròn lên thành `3` theo quy tắc nghiệp vụ. Dùng phương thức nào?**  
   `new BigDecimal("2.99").setScale(0, RoundingMode.CEILING).toBigIntegerExact()` (với số dương này cho `3`). `toBigInteger()` trực tiếp chỉ cho `2`.

7. **Giá trị `2147483648.00` là số nguyên chính xác nhưng vượt `int`. `toBigIntegerExact()` có thành công không?**  
   Có. Kết quả là `2147483648` dưới dạng `BigInteger`. Nhưng chuyển tiếp bằng `intValueExact()` sẽ ném `ArithmeticException`.

8. **Sự khác nhau giữa `toBigIntegerExact()` và `intValueExact()` là gì?**  
   Cả hai đều không cho phép mất phần lẻ; `intValueExact()` còn phải bảo đảm kết quả nằm trong miền `int`, còn `BigInteger` hỗ trợ số nguyên lớn tùy ý.

## 11. Tổng kết và chọn API

Hãy quyết định theo hợp đồng dữ liệu:

| Yêu cầu | Giải pháp |
|---|---|
| Cố ý bỏ phần lẻ, hướng về 0 | `decimal.toBigInteger()` |
| Chỉ chấp nhận giá trị nguyên chính xác | `decimal.toBigIntegerExact()` |
| Phải làm tròn theo chính sách cụ thể | `decimal.setScale(0, mode).toBigIntegerExact()` |
| Phải chuyển sang `int` chính xác | `decimal.intValueExact()` hoặc `decimal.toBigIntegerExact().intValueExact()` |
| Phải chuyển sang `long` chính xác | `decimal.longValueExact()` hoặc `decimal.toBigIntegerExact().longValueExact()` |

Quy tắc nhớ nhanh: **Không có `Exact`: chấp nhận cắt phần lẻ. Có `Exact`: không làm mất phần lẻ; nếu không thể giữ nguyên giá trị, báo lỗi.** Mọi trường hợp vẫn phải kiểm tra kiểu đích, khả năng null và giới hạn tài nguyên tương ứng.

Tham khảo Javadoc chính thức:

- [Java 21 — `BigDecimal`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)
- [Java 21 — `BigInteger`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html)
- [Java 21 — `RoundingMode`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-conversions-007">floatValue, doubleValue và khả năng làm tròn hoặc thành vô cực</a>

<details>
<summary>Xem chi tiết</summary>

# `BigDecimal.floatValue()`, `doubleValue()`: chuyển đổi, làm tròn và vô cực

## 1. Ý nghĩa của phép chuyển sang số thực nhị phân

`BigDecimal` biểu diễn chính xác một giá trị thập phân có số chữ số hữu hạn thông qua unscaled value và scale; còn `float` và `double` là hai kiểu số thực dấu phẩy động nhị phân IEEE 754. Hai phương thức:

~~~java
BigDecimal amount = new BigDecimal("12.5");
float f = amount.floatValue();
double d = amount.doubleValue();
~~~

trả về giá trị số nguyên thủy tương ứng. Đây là **chuyển giá trị số**, không phải định dạng chuỗi và cũng không phải phép tính với `MathContext`.

Dữ liệu `BigDecimal` hợp lệ có thể gặp bốn tình huống khi chuyển kiểu:

1. **Chuyển chính xác:** số như `0.5`, `12.5` hoặc số nguyên nhỏ biểu diễn được đúng bằng nhị phân.
2. **Bị làm tròn:** số như `0.1` không có biểu diễn nhị phân hữu hạn đúng tuyệt đối.
3. **Overflow:** độ lớn quá lớn, kết quả trở thành `Infinity` hoặc `-Infinity`.
4. **Underflow:** số có độ lớn cực nhỏ được làm tròn thành số *subnormal*, `0.0` hoặc `-0.0`.

Hai phương thức cho phép mất độ chính xác hoặc tạo vô cực. **Chúng không tự ném `ArithmeticException` chỉ vì cần làm tròn hay tràn phạm vi.** Điều này khác những phép tính `BigDecimal` có ràng buộc làm tròn tường minh.

## 2. Độ chính xác và phạm vi khác nhau như thế nào?

| Đặc tính | `BigDecimal` | `float` | `double` |
| --- | --- | --- | --- |
| Biểu diễn | Unscaled integer × 10^(-scale) | IEEE 754 binary32 | IEEE 754 binary64 |
| Độ chính xác số có nghĩa | Theo giá trị và bộ nhớ | 24 bit nhị phân | 53 bit nhị phân |
| Số chữ số thập phân có nghĩa xấp xỉ | Không cố định | 6–9 | 15–17 |
| Có `NaN` và `Infinity` | Không | Có | Có |
| Phân biệt số không âm dấu | Không | Có | Có |
| Biểu diễn đúng `0.1` thập phân | Có | Không | Không |

`float` có miền giá trị hữu hạn đến khoảng `3.4028235E38`, còn `double` đến khoảng `1.7976931348623157E308`. Phạm vi rộng hơn không đồng nghĩa chính xác hơn vô hạn: `double` vẫn chỉ có 53 bit có nghĩa.

Cơ chế chính: phép chuyển đưa giá trị thập phân vào tập các giá trị nhị phân biểu diễn được, lựa chọn kết quả theo quy tắc làm tròn nhị phân đến gần nhất, nếu đúng giữa thì chọn giá trị có bit thấp nhất của significand là chẵn (*ties to even*). Khoảng cách giữa hai số có thể biểu diễn tăng dần theo độ lớn của chúng.

### 2.1. Khi `0.1` được in ra vẫn là `0.1`

~~~java
BigDecimal decimal = new BigDecimal("0.1");
double binary = decimal.doubleValue();
System.out.println(binary); // 0.1
~~~

Đầu ra `0.1` **không chứng minh phép chuyển bảo toàn giá trị**. `Double.toString` trả về biểu diễn thập phân ngắn đọc lại được cùng giá trị `double`. Giá trị nhị phân thực sự của `binary`, khi được chuyển chính xác về `BigDecimal`, là:

~~~text
0.1000000000000000055511151231257827021181583404541015625
~~~

Có thể quan sát bằng:

~~~java
double binary = new BigDecimal("0.1").doubleValue();
System.out.println(new BigDecimal(binary).toPlainString());
~~~

`new BigDecimal("0.1")` là số thập phân 0.1 đúng theo văn bản. `new BigDecimal(binary)` lại mô tả **giá trị nhị phân đã lưu** trong biến `double`; hai đối tượng này không có cùng giá trị toán học. Vì thế cần tách bạch **chuỗi in ra** và **độ chính xác số**.

## 3. Làm tròn khi chuyển sang `float` hoặc `double`

### 3.1. Ví dụ với `float`: mất một đơn vị ở số nguyên lớn

~~~java
BigDecimal n = new BigDecimal("16777217");
float f = n.floatValue();
double d = n.doubleValue();

System.out.println(f); // 1.6777216E7 = 16777216
System.out.println(d); // 1.6777217E7 = 16777217
~~~

`16_777_216 = 2^24` là mốc quan trọng: `float` còn biểu diễn chính xác số đó, nhưng không biểu diễn được mọi số nguyên kế tiếp. Giá trị `16_777_217` nằm giữa hai số `float` kề nhau `16_777_216` và `16_777_218`. Quy tắc chọn kết quả khi đúng giữa làm nó thành `16_777_216`.

`double` có độ chính xác cao hơn nên vẫn chứa được đúng `16_777_217`. Điều này **không có nghĩa mọi số nguyên đều chính xác trong `double`**.

### 3.2. Ví dụ với `double`: vượt giới hạn nguyên liên tiếp

~~~java
BigDecimal n = new BigDecimal("9007199254740993");
System.out.println(n.doubleValue());
// 9.007199254740992E15 = 9007199254740992
~~~

`9_007_199_254_740_992 = 2^53` là mốc tương ứng cho `double`. Số `2^53 + 1` không biểu diễn được đúng; sau chuyển đổi, giá trị giảm một đơn vị.

### 3.3. Không chỉ số nguyên mới chịu ảnh hưởng

Một số thập phân hữu hạn như `0.1` thường có phần thập phân nhị phân lặp vô hạn. Do đó ngay ở khoảng giá trị rất nhỏ và chưa gần overflow, quá trình chuyển vẫn có thể làm tròn.

Cần phân biệt hai loại giới hạn:

- **Giới hạn độ chính xác:** giá trị vẫn hữu hạn nhưng khác nguồn một chút do bị làm tròn.
- **Giới hạn miền giá trị:** kết quả có thể thành vô cực hoặc zero/subnormal.

Nếu cần giữ chính xác chữ số thập phân trong tính tiền, thuế, lãi suất theo quy định hoặc số liệu kế toán, nên giữ `BigDecimal` trong luồng tính toán.

## 4. Overflow: một `BigDecimal` hữu hạn có thể thành vô cực

Với giá trị lớn hơn độ lớn hữu hạn mà kiểu đích có thể biểu diễn, `floatValue()` và `doubleValue()` có thể cho `Infinity` hoặc `-Infinity`:

~~~java
BigDecimal big = new BigDecimal("1E39");

System.out.println(big.floatValue());  // Infinity
System.out.println(big.doubleValue()); // 1.0E39

BigDecimal huge = new BigDecimal("1E309");
System.out.println(huge.doubleValue()); // Infinity

BigDecimal negativeHuge = new BigDecimal("-1E309");
System.out.println(negativeHuge.doubleValue()); // -Infinity
~~~

`1E39` đã vượt phạm vi hữu hạn của `float` nhưng vẫn nằm trong phạm vi `double`. `1E309` vượt cả phạm vi của `double`. Đây là **tràn số khi chuyển kiểu**, không phải việc `BigDecimal` tự chứa vô cực.

`BigDecimal` không có giá trị `NaN` hay vô cực. Các phép `new BigDecimal("Infinity")`, `new BigDecimal(Double.POSITIVE_INFINITY)` và `BigDecimal.valueOf(Double.POSITIVE_INFINITY)` đều không xây dựng được đối tượng `BigDecimal` hợp lệ, mà ném `NumberFormatException`.

Khi API đích cấm số không hữu hạn, phải kiểm tra **sau chuyển đổi**:

~~~java
double converted = input.doubleValue();
if (!Double.isFinite(converted)) {
    throw new ArithmeticException("Gia tri khong vua pham vi double huu han");
}
~~~

Đây là đoạn mã phương thức, giả sử `input` có kiểu `BigDecimal`. `Double.isFinite` từ chối cả `Infinity`, `-Infinity` và `NaN`; phép chuyển một `BigDecimal` hợp lệ sang `double` không tự tạo `NaN`, nhưng kiểm tra này hữu ích tại ranh giới hệ thống.

**Quan trọng:** `isFinite` chỉ bảo đảm kết quả hữu hạn; nó không phát hiện việc `0.1` hay số nguyên lớn bị làm tròn thành một giá trị hữu hạn khác.

## 5. Underflow: số subnormal và kết quả `0.0`/`-0.0`

Ngoài vùng *normal*, IEEE 754 còn có vùng *subnormal* giúp biểu diễn các giá trị dương khác 0 nhỏ hơn normal dương nhỏ nhất. Nếu độ lớn nguồn còn nhỏ hơn khả năng biểu diễn sau khi làm tròn, kết quả thành số không.

| Hằng số | Ý nghĩa |
| --- | --- |
| `Float.MIN_NORMAL` | Số `float` normal dương nhỏ nhất, khoảng `1.17549435E-38` |
| `Float.MIN_VALUE` | Số `float` dương khác 0 nhỏ nhất, subnormal, khoảng `1.4E-45` |
| `Double.MIN_NORMAL` | Số `double` normal dương nhỏ nhất, khoảng `2.2250738585072014E-308` |
| `Double.MIN_VALUE` | Số `double` dương khác 0 nhỏ nhất, subnormal, khoảng `4.9E-324` |

Lưu ý `MIN_VALUE` **không phải số âm nhỏ nhất**. Trong các kiểu `float`/`double`, đó là số dương khác 0 có độ lớn nhỏ nhất.

~~~java
System.out.println(new BigDecimal("1E-50").doubleValue()); // 1.0E-50
System.out.println(new BigDecimal("1E-50").floatValue());  // 0.0

System.out.println(new BigDecimal("5E-324").doubleValue()); // 4.9E-324
System.out.println(new BigDecimal("1E-400").doubleValue()); // 0.0
System.out.println(new BigDecimal("-1E-400").doubleValue()); // -0.0
~~~

`5E-324` làm tròn về `Double.MIN_VALUE`, còn `1E-400` thành `0.0`. Với `-1E-400`, kết quả là **zero có bit dấu âm**, in ra `-0.0`. Hai giá trị `0.0` và `-0.0` so sánh `==` cho `true`, nhưng bit dấu khác nhau.

~~~java
double zero = new BigDecimal("-1E-400").doubleValue();
boolean negativeZero =
        Double.doubleToRawLongBits(zero) == Long.MIN_VALUE;
System.out.println(negativeZero); // true
~~~

Chính `BigDecimal` không phân biệt zero âm và zero dương theo bit dấu như IEEE 754. Vì vậy đừng mong một vòng chuyển đổi qua `BigDecimal` bảo toàn biểu diễn bit `-0.0` khi giá trị toán học bằng 0.

**Underflow không nhất thiết tạo exception.** Nó có thể là lỗi nghiệp vụ nghiêm trọng nếu số rất nhỏ khác 0 bị hiểu như bằng 0 (ví dụ xác suất, hệ số đo lường hoặc thuật toán chia tỷ lệ).

## 6. Ví dụ độc lập chạy với Java 17+

Lưu đoạn sau thành tệp **BigDecimalFloatingConversionDemo.java**:

~~~java
import java.math.BigDecimal;

public class BigDecimalFloatingConversionDemo {
    private static void show(String text) {
        BigDecimal decimal = new BigDecimal(text);
        System.out.println(text
                + " -> double=" + decimal.doubleValue()
                + ", float=" + decimal.floatValue());
    }

    private static boolean exactAsDouble(BigDecimal original) {
        double converted = original.doubleValue();
        return Double.isFinite(converted)
                && original.compareTo(new BigDecimal(converted)) == 0;
    }

    private static boolean exactAsFloat(BigDecimal original) {
        float converted = original.floatValue();
        return Float.isFinite(converted)
                && original.compareTo(
                        new BigDecimal((double) converted)) == 0;
    }

    public static void main(String[] args) {
        show("0.1");
        show("16777217");
        show("1E39");
        show("1E309");
        show("1E-50");
        show("-1E-400");
        show("5E-324");

        double value = new BigDecimal("0.1").doubleValue();
        System.out.println("exact binary double decimal: "
                + new BigDecimal(value).toPlainString());
        System.out.println("shortest roundtrip decimal: "
                + BigDecimal.valueOf(value).toPlainString());

        System.out.println("0.1 exact double? "
                + exactAsDouble(new BigDecimal("0.1")));
        System.out.println("0.5 exact double? "
                + exactAsDouble(new BigDecimal("0.5")));
        System.out.println("16777217 exact float? "
                + exactAsFloat(new BigDecimal("16777217")));
        System.out.println("16777216 exact float? "
                + exactAsFloat(new BigDecimal("16777216")));

        double tinyNegative = new BigDecimal("-1E-400").doubleValue();
        System.out.println("negative zero bits? "
                + (Double.doubleToRawLongBits(tinyNegative)
                        == Long.MIN_VALUE));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BigDecimalFloatingConversionDemo.java
java BigDecimalFloatingConversionDemo
~~~

Kết quả chính xác:

~~~text
0.1 -> double=0.1, float=0.1
16777217 -> double=1.6777217E7, float=1.6777216E7
1E39 -> double=1.0E39, float=Infinity
1E309 -> double=Infinity, float=Infinity
1E-50 -> double=1.0E-50, float=0.0
-1E-400 -> double=-0.0, float=-0.0
5E-324 -> double=4.9E-324, float=0.0
exact binary double decimal: 0.1000000000000000055511151231257827021181583404541015625
shortest roundtrip decimal: 0.1
0.1 exact double? false
0.5 exact double? true
16777217 exact float? false
16777216 exact float? true
negative zero bits? true
~~~

Trong chương trình, `show` in đồng thời kết quả hai kiểu cho cùng một `BigDecimal`. Các hàm `exactAsDouble` và `exactAsFloat` thực hiện bước kiểm tra tiếp theo: thay vì chỉ nhìn giá trị in ra, chúng đối chiếu **giá trị nhị phân thực sự** với `BigDecimal` nguồn.

## 7. Kiểm tra phép chuyển có chính xác hay không

Phương thức `exactAsDouble` trong ví dụ áp dụng các bước:

1. Gọi `doubleValue()`.
2. Kiểm tra `Double.isFinite` để từ chối vô cực (và NaN nếu kiểu nguồn là số thực khác).
3. Dùng `new BigDecimal(converted)` để tái hiện chính xác giá trị nhị phân hữu hạn dưới dạng thập phân.
4. So sánh `compareTo` với `BigDecimal` ban đầu. Nếu bằng 0, giá trị toán học được bảo toàn.

Với `float`, chuyển `float` kết quả thành `double` trước khi truyền vào constructor `BigDecimal` là hợp lý: mọi `float` hữu hạn đều được `double` biểu diễn chính xác. Việc mở rộng kiểu ở đây không làm mất thêm dữ liệu, nhưng **cũng không phục hồi chữ số đã mất lúc chuyển từ `BigDecimal` sang `float`**.

Các hàm này kiểm tra **tính chính xác của giá trị**, không phải bảo toàn `scale`, các số 0 tận cùng hay văn bản đầu vào. `BigDecimal("12.500")` và `BigDecimal("12.5")` bằng nhau theo `compareTo`, dù khác scale và khác `equals`.

### 7.1. Tại sao không dùng `BigDecimal.valueOf(double)` để kiểm tra?

~~~java
BigDecimal source = new BigDecimal("0.1");
double d = source.doubleValue();

System.out.println(source.compareTo(BigDecimal.valueOf(d))); // 0
System.out.println(source.compareTo(new BigDecimal(d)));      // -1
~~~

Hai kết quả khác nhau vì:

- `BigDecimal.valueOf(d)` dựa trên dạng thập phân ngắn `Double.toString(d)`, thường là dạng giúp round-trip về cùng giá trị `double` (ở đây là `0.1`).
- `new BigDecimal(d)` giữ chính xác giá trị nhị phân thực tế của `double` thành một thập phân đầy đủ.

Giá trị `double` tương ứng `0.1` hơi **lớn hơn** 0.1 thập phân chính xác, do đó `source.compareTo(new BigDecimal(d))` trả `-1`. **So sánh với `valueOf` có thể cho kết quả “bằng nhau” giả tạo nếu mục tiêu là phát hiện mọi sai số chuyển kiểu.**

Không gọi `new BigDecimal(Double.POSITIVE_INFINITY)`: constructor đó chỉ nhận giá trị hữu hạn; phải kiểm tra `isFinite` trước.

### 7.2. Hàm chuyển chỉ nhận giá trị `double` chính xác

~~~java
public static double requireExactDouble(BigDecimal input) {
    double value = input.doubleValue();
    if (!Double.isFinite(value)
            || input.compareTo(new BigDecimal(value)) != 0) {
        throw new ArithmeticException("Mat do chinh xac khi chuyen double");
    }
    return value;
}
~~~

Đây là **phương thức minh họa để đặt trong một class đã import `java.math.BigDecimal`**, không phải chương trình độc lập. Nó chấp nhận `0.5` và từ chối `0.1`, số vượt phạm vi hoặc số cực nhỏ bị làm tròn về 0.

Nếu dữ liệu có thể là `null`, phải quy định cách xử lý trước khi gọi `input.doubleValue()`; nếu không sẽ ném `NullPointerException`.

Trong `BigDecimal` không có phương thức chuẩn `doubleValueExact()` hoặc `floatValueExact()` giống như `intValueExact()` và `longValueExact()`. Nếu ứng dụng yêu cầu chuyển số thực nhị phân **mà không được mất độ chính xác**, cần kiểm tra thủ công hoặc thiết kế giao diện tiếp nhận `BigDecimal`.

## 8. Chọn chính sách chuyển kiểu theo bài toán

| Yêu cầu nghiệp vụ | Cách tiếp cận |
| --- | --- |
| Chấp nhận xấp xỉ trong tính toán | Dùng `doubleValue()` hoặc `floatValue()` khi đã hiểu sai số |
| Kết quả phải hữu hạn | Sau chuyển, kiểm tra `Double.isFinite`/`Float.isFinite` |
| Bắt buộc giữ nguyên giá trị toán học | Kiểm tra `isFinite` rồi đối chiếu với `new BigDecimal(converted)` |
| Bắt buộc giữ scale/chữ số gốc | Tiếp tục dùng `BigDecimal`; xác minh scale và định dạng riêng |
| Dùng tiền hoặc số thập phân cần chính xác | Tính toán bằng `BigDecimal` xuyên suốt, chỉ chuyển ở ranh giới API thực sự cần |
| Phát hiện underflow từ số khác 0 thành zero | So sánh giá trị nguồn với giá trị đã chuyển; kiểm tra zero khi cần thông báo đặc thù |

**Không nhầm `RoundingMode.UNNECESSARY` với kiểm tra chuyển `double`.** Ví dụ:

~~~java
BigDecimal x = new BigDecimal("0.10")
        .setScale(2, java.math.RoundingMode.UNNECESSARY);
double d = x.doubleValue();
~~~

`setScale(2, UNNECESSARY)` thành công vì số thập phân đó có thể biểu diễn ở scale 2 mà không đổi giá trị. Nhưng `d` vẫn là phép xấp xỉ nhị phân của 0.1. Đây là hai điều kiện chính xác **khác hệ cơ số**.

## 9. Lỗi phổ biến

| Dấu hiệu hoặc hiểu lầm | Thực tế |
| --- | --- |
| In `0.1` nên chắc chắn đã giữ đúng số thập phân `0.1` | `Double.toString` trả dạng ngắn; xem `new BigDecimal(double)` để biết giá trị nhị phân |
| Dùng `double` thì số nguyên nào cũng chính xác | Với số nguyên rất lớn, khoảng cách giữa các giá trị `double` vượt 1 |
| Chuyển số `BigDecimal` hữu hạn không thể ra `Infinity` | Overflow có thể cho `Infinity` hoặc `-Infinity` mà không báo lỗi |
| Underflow chỉ có thể là số 0 dương | Giá trị âm đủ nhỏ có thể thành `-0.0` |
| `Float.MIN_VALUE` là số âm cực tiểu | Đây là số **dương khác 0 nhỏ nhất**, thuộc vùng subnormal |
| `Double.isFinite` xác nhận độ chính xác | Nó chỉ loại bỏ các giá trị không hữu hạn |
| `BigDecimal.valueOf(double)` luôn tái hiện mọi chữ số nhị phân của `double` | Nó dùng biểu diễn thập phân ngắn; constructor nhận `double` cho biểu diễn chính xác |
| Mở rộng `float` đã bị làm tròn sang `double` sẽ phục hồi chữ số | Các chữ số đã mất không thể khôi phục bằng ép kiểu |
| Không có exception thì chuyển đổi không mất thông tin | Precision loss, overflow và underflow có thể xảy ra trong phép chuyển thành công |

## 10. Tự kiểm tra

1. `new BigDecimal("0.5").doubleValue()` có chính xác không? **Có:** `0.5 = 1/2` biểu diễn đúng bằng nhị phân.
2. `new BigDecimal("0.1").doubleValue()` có đúng chính xác số thập phân 0.1 không? **Không:** giá trị nhị phân gần nhất chỉ là xấp xỉ.
3. `new BigDecimal("16777217").floatValue()` cho giá trị bao nhiêu? **16_777_216**, vì giới hạn precision 24 bit và quy tắc làm tròn.
4. `new BigDecimal("1E39").floatValue()` và `doubleValue()` có như nhau không? **Không:** kết quả lần lượt là `Infinity` và `1.0E39`.
5. `new BigDecimal("1E-50").floatValue()` trả về gì? **`0.0`**, còn `doubleValue()` vẫn là `1.0E-50`.
6. Khi giá trị trả về là `0.0`, có thể kết luận `BigDecimal` nguồn bằng 0 không? **Không:** giá trị khác 0 có thể underflow về zero.
7. `Double.isFinite` có kiểm tra chuyển đúng đến từng chữ số không? **Không:** nó chỉ kiểm tra kết quả hữu hạn.
8. Muốn xác minh phép chuyển sang `double` bảo toàn giá trị, so sánh với đối tượng nào? **`new BigDecimal(result)`**, với `result` hữu hạn; không chỉ so với `BigDecimal.valueOf(result)`.

**Kết luận:** `floatValue()` và `doubleValue()` là các phép chuyển sang số dấu phẩy động nhị phân có thể **làm tròn, overflow thành vô cực hoặc underflow về subnormal/zero**. Chọn kiểu theo hợp đồng dữ liệu, kiểm tra `isFinite` khi cần, và đối chiếu **giá trị nhị phân thực sự** nếu việc giữ nguyên giá trị là bắt buộc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-conversions-008">Parse và format BigDecimal theo locale</a>

<details>
<summary>Xem chi tiết</summary>

# Parse và format BigDecimal theo locale trong Java

## 1. Vấn đề: cùng một số, nhiều cách viết

Một BigDecimal có giá trị `1234567.50` có thể được hiển thị theo những quy ước khác nhau:

| Locale | Cách hiển thị thường gặp | Dấu thập phân | Dấu nhóm nghìn |
| --- | --- | --- | --- |
| `Locale.US` | `1,234,567.50` | Dấu chấm `.` | Dấu phẩy `,` |
| `Locale.GERMANY` | `1.234.567,50` | Dấu phẩy `,` | Dấu chấm `.` |
| Các locale khác | Có thể dùng chữ số hoặc ký tự Unicode khác | Phụ thuộc locale | Có thể là khoảng trắng/Unicode |

**BigDecimal lưu giá trị số thập phân cùng precision và scale, không lưu locale.** Locale là ngữ cảnh văn hóa/ngôn ngữ dùng để chuyển giá trị thành văn bản hoặc hiểu văn bản do người dùng nhập.

Bài học tập trung vào hai hướng:

- **Format:** `BigDecimal → String` cho giao diện, báo cáo, biểu mẫu.
- **Parse:** `String` theo locale → `BigDecimal` để kiểm tra và tính toán.

Để tránh mất chính xác, xử lý thập phân trực tiếp bằng BigDecimal và lớp `java.text`, không chuyển qua `double` chỉ để format hay parse.

## 2. Các API liên quan và vai trò

| API | Chức năng |
| --- | --- |
| `java.math.BigDecimal` | Biểu diễn thập phân chính xác, độc lập locale |
| `java.util.Locale` | Mô tả ngôn ngữ/vùng; ví dụ `Locale.US`, `Locale.GERMANY`, `Locale.forLanguageTag("vi-VN")` |
| `java.text.NumberFormat` | Lớp nền để format/parse số theo locale |
| `java.text.DecimalFormat` | Cung cấp pattern định dạng và `setParseBigDecimal(true)` |
| `java.text.DecimalFormatSymbols` | Dấu thập phân, dấu nhóm và các ký hiệu số theo locale |
| `java.text.ParsePosition` | Biết parser đã tiêu thụ bao nhiêu ký tự; phát hiện phần thừa |
| `java.math.RoundingMode` | Quy tắc làm tròn khi số chữ số hiển thị bị giới hạn |
| `java.util.Currency` | Định danh đồng tiền, tách biệt với locale |

**Hai loại chuỗi không nên trộn lẫn:**

- Chuỗi số **cho máy**: `1234567.50`, cú pháp thập phân được hợp đồng dữ liệu quy định; `BigDecimal.toPlainString()` tạo chuỗi thập phân không dùng dấu nhóm theo locale.
- Chuỗi số **cho con người**: `1,234,567.50` hay `1.234.567,50`; cách viết phụ thuộc locale.

Đặc biệt `1,234` có thể có nghĩa là 1234 trong US nhưng 1.234 trong Germany. Parser không thể đoán locale một cách đáng tin chỉ từ dấu phẩy/chấm: locale phải được lấy từ cấu hình phiên, lựa chọn người dùng hoặc hợp đồng dữ liệu.

## 3. Format BigDecimal với NumberFormat

Lấy formatter bằng `NumberFormat.getNumberInstance(locale)` hoặc `NumberFormat.getInstance(locale)`. Tùy mục đích hiển thị, cấu hình:

- `setGroupingUsed(true)`: hiển thị dấu phân nhóm phần nguyên.
- `setMinimumFractionDigits(2)`: luôn hiển thị tối thiểu hai chữ số thập phân.
- `setMaximumFractionDigits(2)`: hiển thị tối đa hai chữ số thập phân.
- `setRoundingMode(RoundingMode.HALF_EVEN)`: quy tắc làm tròn nếu phần thập phân dài hơn giới hạn.

`format(value)` nhận trực tiếp BigDecimal thông qua overload `format(Object)`. Không cần gọi `doubleValue()`, vì chuyển đổi này có thể mất precision.

### Ví dụ Java 17: US và Germany

Tạo tệp `LocaleFormatDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public class LocaleFormatDemo {
    static String display(BigDecimal value, Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        return format.format(value);
    }

    public static void main(String[] args) {
        BigDecimal amount = new BigDecimal("1234567.50");

        System.out.println("US = " + display(amount, Locale.US));
        System.out.println("DE = " + display(amount, Locale.GERMANY));
        System.out.println("plain = " + amount.toPlainString());
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac LocaleFormatDemo.java
java LocaleFormatDemo
~~~

Đầu ra với dữ liệu locale tiêu chuẩn:

~~~text
US = 1,234,567.50
DE = 1.234.567,50
plain = 1234567.50
~~~

Đối tượng `amount` không bị sửa. Chuỗi đã format có mục đích **trình bày**, không phải tự động là cú pháp số hợp lệ cho cơ sở dữ liệu, JSON hoặc constructor BigDecimal.

Nếu dùng `Locale.getDefault()`, kết quả phụ thuộc môi trường chạy. Với API, bài kiểm thử và xử lý server cần tái lập được, nên truyền locale tường minh. Với giao diện, lấy locale từ lựa chọn/ngữ cảnh của người dùng.

## 4. BigDecimal(String) không hiểu ký hiệu theo locale

Constructor `new BigDecimal(String)` đọc cú pháp số thập phân của Java. Nó không biết dấu chấm và dấu phẩy nào là dấu nhóm hay dấu thập phân theo vùng:

| Chuỗi đầu vào | `new BigDecimal(...)` |
| --- | --- |
| `"1234.50"` | Hợp lệ |
| `"-0.125"` | Hợp lệ |
| `"1,234.50"` | `NumberFormatException` |
| `"1.234,50"` | `NumberFormatException` |

Để parse số do người dùng gõ theo locale, cần dùng `NumberFormat`/`DecimalFormat` tương ứng. Không thay toàn bộ dấu phẩy bằng dấu chấm vì trong một số locale dấu phẩy là **dấu thập phân**, nhưng locale khác dùng nó làm **dấu nhóm**. Một số locale sử dụng ký tự phân nhóm Unicode, chẳng hạn khoảng trắng không ngắt dòng.

## 5. Parse trực tiếp ra BigDecimal

`NumberFormat.parse(...)` trả về kiểu `Number`, không bảo đảm luôn là BigDecimal: với cấu hình mặc định, đối tượng kết quả có thể là `Long` hoặc `Double`. Vì thế, đừng cast thẳng về BigDecimal nếu chưa kiểm tra cấu hình.

`DecimalFormat` có phương thức `setParseBigDecimal(true)` để parser tạo BigDecimal cho giá trị số thập phân thông thường. Lấy đối tượng từ `NumberFormat.getNumberInstance(locale)`; trong JDK 17 với các locale phổ biến như US/Germany, formatter này là DecimalFormat. Khi xây dựng thư viện cho provider đặc biệt, nên kiểm tra `instanceof DecimalFormat`.

Điểm quan trọng khác: `parse` có thể chấp nhận **tiền tố hợp lệ** của chuỗi mà bỏ qua ký tự ở phía sau. Với biểu mẫu người dùng, hãy dùng `ParsePosition` và yêu cầu vị trí kết thúc bằng độ dài chuỗi. Bên cạnh đó, kiểm tra kiểu trả về vì `BigDecimal` không biểu diễn được NaN hoặc Infinity.

### Ví dụ Java 17: parse toàn bộ chuỗi

Tạo tệp `LocaleParseDemo.java`:

~~~java
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;

public class LocaleParseDemo {
    static BigDecimal parseFull(String text, Locale locale)
            throws ParseException {
        NumberFormat generic = NumberFormat.getNumberInstance(locale);
        if (!(generic instanceof DecimalFormat format)) {
            throw new IllegalStateException("DecimalFormat unavailable");
        }

        format.setParseBigDecimal(true);
        format.setParseIntegerOnly(false);

        ParsePosition position = new ParsePosition(0);
        Number number = format.parse(text, position);
        if (!(number instanceof BigDecimal decimal)
                || position.getIndex() != text.length()) {
            throw new ParseException("Invalid localized number",
                    Math.max(0, position.getIndex()));
        }
        return decimal;
    }

    public static void main(String[] args) throws ParseException {
        BigDecimal us = parseFull("1,234,567.50", Locale.US);
        BigDecimal de = parseFull("1.234.567,50", Locale.GERMANY);

        System.out.println("US = " + us.toPlainString());
        System.out.println("DE = " + de.toPlainString());
        System.out.println("same = " + (us.compareTo(de) == 0));

        try {
            parseFull("1,234.50oops", Locale.US);
            System.out.println("Unexpected acceptance");
        } catch (ParseException ex) {
            System.out.println("Suffix rejected");
        }
    }
}
~~~

Chạy:

~~~bash
javac LocaleParseDemo.java
java LocaleParseDemo
~~~

Đầu ra:

~~~text
US = 1234567.50
DE = 1234567.50
same = true
Suffix rejected
~~~

Ở đây `setParseBigDecimal(true)` giữ giá trị decimal mà không đi qua double, còn `ParsePosition` chặn chuỗi có phần đuôi `oops`.

**Giới hạn:** kiểm tra tiêu thụ hết chuỗi **không đồng nghĩa** cú pháp dấu nhóm đã đúng theo quy tắc nghiêm ngặt của biểu mẫu. DecimalFormat có thể parse tương đối dễ dãi với một số vị trí grouping; nếu hợp đồng yêu cầu nhóm ba chữ số chặt chẽ, phải bổ sung validation tương ứng với locale/pattern. Đừng mặc định rằng `setGroupingUsed(true)` tự động kiểm tra cấu trúc nhập hoàn toàn nghiêm ngặt. Trong **Java 17**, API `DecimalFormat.setStrict` chưa có; tránh dùng ví dụ dựa vào API của phiên bản JDK mới hơn.

## 6. DecimalFormatSymbols và mẫu hiển thị

Khi cần pattern xác định, tạo `DecimalFormat` với `DecimalFormatSymbols.getInstance(locale)`. Trong pattern không bản địa hóa:

- `,` là ký hiệu **grouping** của pattern;
- `.` là ký hiệu **decimal** của pattern;
- `0` là chữ số bắt buộc; `#` là chữ số tùy chọn.

Chẳng hạn pattern `#,##0.00` yêu cầu nhóm nghìn và đúng hai chữ số phần lẻ. Với locale Germany, formatter **hiển thị** dấu chấm cho grouping và dấu phẩy cho decimal dù trong pattern viết ngược.

Tạo tệp `LocalePatternDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class LocalePatternDemo {
    public static void main(String[] args) {
        DecimalFormatSymbols symbols =
                DecimalFormatSymbols.getInstance(Locale.GERMANY);
        DecimalFormat formatter =
                new DecimalFormat("#,##0.00", symbols);
        formatter.setRoundingMode(RoundingMode.HALF_EVEN);

        BigDecimal value = new BigDecimal("1234.555");
        System.out.println("formatted = " + formatter.format(value));
        System.out.println("original = " + value.toPlainString());
    }
}
~~~

~~~bash
javac LocalePatternDemo.java
java LocalePatternDemo
~~~

Đầu ra:

~~~text
formatted = 1.234,56
original = 1234.555
~~~

`DecimalFormat.applyPattern(...)` nhận pattern theo ký hiệu chuẩn, trong khi `applyLocalizedPattern(...)` nhận pattern đã bản địa hóa. Không nhầm pattern với chuỗi số nhập vào. Nếu muốn đổi dấu thập phân/dấu nhóm, cấu hình locale/symbols phù hợp; tránh dùng `String.replace` lên chuỗi vì dễ phá số âm, số có nhiều dấu nhóm và tiền tố/hậu tố.

## 7. Format có thể làm tròn: không đảm bảo round-trip

Đối tượng BigDecimal gốc vẫn giữ nguyên khi format, nhưng **chuỗi hiển thị có thể mất chữ số** vì `maximumFractionDigits` và `RoundingMode`. Khi parse lại, những chữ số đã bị mất không thể khôi phục.

Ví dụ `1234.567` format theo US với tối đa hai chữ số lẻ, HALF_UP → `1,234.57`. Parse lại → `1234.57`, khác giá trị gốc.

Tạo tệp `LocaleRoundTripDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class LocaleRoundTripDemo {
    public static void main(String[] args) {
        DecimalFormat format =
                (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        format.setRoundingMode(RoundingMode.HALF_UP);
        format.setParseBigDecimal(true);

        BigDecimal original = new BigDecimal("1234.567");
        String displayed = format.format(original);
        BigDecimal parsed = (BigDecimal)
                format.parse(displayed, new ParsePosition(0));

        System.out.println("original = " + original);
        System.out.println("displayed = " + displayed);
        System.out.println("parsed = " + parsed);
        System.out.println("same value = "
                + (original.compareTo(parsed) == 0));
    }
}
~~~

~~~bash
javac LocaleRoundTripDemo.java
java LocaleRoundTripDemo
~~~

Đầu ra:

~~~text
original = 1234.567
displayed = 1,234.57
parsed = 1234.57
same value = false
~~~

Ngay cả khi giá trị số học không đổi, **scale có thể khác**: `new BigDecimal("2.0").compareTo(new BigDecimal("2.00")) == 0`, nhưng `equals(...)` trả false vì BigDecimal xét cả scale. Formatter có thể thay đổi số chữ số 0 cuối theo `minimumFractionDigits`/`maximumFractionDigits`; do đó không dùng format→parse như phương thức lưu trữ một giá trị và scale chính xác.

Quy tắc thực hành: giữ BigDecimal gốc trong mô hình dữ liệu; chỉ format khi cần trình bày. Nếu người dùng thực sự chỉnh sửa giá trị, parse bằng locale đã được xác định rồi kiểm tra lại chính sách nghiệp vụ.

## 8. Tiền tệ: Locale và Currency là hai thông tin khác nhau

`NumberFormat.getCurrencyInstance(locale)` tạo cách **trình bày tiền tệ** theo locale; `Currency` xác định **đồng tiền** (USD, EUR, VND...). BigDecimal chỉ lưu con số, không kèm mã đồng tiền. Một số locale có thể sử dụng ký hiệu tương tự nhau cho các đồng tiền khác nhau, nên không suy ra mã ISO 4217 đáng tin chỉ từ ký hiệu hiển thị.

Tạo tệp `LocaleCurrencyDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public class LocaleCurrencyDemo {
    public static void main(String[] args) {
        BigDecimal value = new BigDecimal("1234.50");
        NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);

        usd.setCurrency(Currency.getInstance("USD"));
        usd.setMinimumFractionDigits(2);
        usd.setMaximumFractionDigits(2);
        usd.setRoundingMode(RoundingMode.HALF_EVEN);

        System.out.println(usd.format(value));
        System.out.println(value.toPlainString());
    }
}
~~~

~~~bash
javac LocaleCurrencyDemo.java
java LocaleCurrencyDemo
~~~

Đầu ra với locale US tiêu chuẩn:

~~~text
$1,234.50
1234.50
~~~

**Currency formatter không nhất thiết chấp nhận cùng chuỗi như number formatter.** Cần định nghĩa liệu người dùng nhập cả ký hiệu tiền hoặc chỉ phần số; dùng parser tương ứng và lưu mã currency riêng trong miền nghiệp vụ. Không giả định mọi loại tiền đều dùng hai chữ số thập phân: cấu hình theo chính sách đồng tiền/giao dịch.

Với Việt Nam, có thể truyền `Locale.forLanguageTag("vi-VN")`. Đầu ra chính xác về dấu cách, dấu nhóm và vị trí ký hiệu có thể tùy dữ liệu locale và provider của JDK; không hard-code chúng trong phép tính hoặc kiểm thử chạy đa môi trường.

## 9. Luồng xử lý input từ UI và API

Một luồng nhập số từ người dùng nên được xác định rõ trước khi code:

1. **Xác định locale đáng tin cậy** từ lựa chọn/ngữ cảnh người dùng hoặc hợp đồng API. Đừng đoán locale từ chuỗi số.
2. **Quy định những gì được phép nhập:** dấu nhóm, số âm, khoảng trắng đầu/cuối, số mũ, ký hiệu tiền hoặc số chữ số phần lẻ tối đa.
3. **Parse bằng DecimalFormat theo locale** và bật `setParseBigDecimal(true)`, kiểm tra tiêu thụ hết chuỗi bằng `ParsePosition`.
4. **Bổ sung kiểm tra cú pháp nếu cần nhập nghiêm ngặt.** Vị trí dấu nhóm, Unicode whitespace và dấu phân cách phải được xét theo locale thật; việc parse thành công chưa chắc là input hợp lệ theo chính sách sản phẩm.
5. **Kiểm tra nghiệp vụ:** giá trị min/max, loại tiền, số chữ số lẻ, rounding mode. Không làm tròn dữ liệu vừa nhập một cách âm thầm nếu quy định yêu cầu từ chối.
6. **Lưu và trao đổi định dạng máy được quy định:** ví dụ decimal string không dấu nhóm; JSON number chỉ khi các bên giữ độ chính xác cần thiết. Chỉ format có locale ở bước xuất giao diện.

Một yêu cầu thường gặp là chỉ chấp nhận số tiền có **không quá hai chữ số lẻ mang giá trị**. Có thể dùng `setScale(2, RoundingMode.UNNECESSARY)` để từ chối khi phải mất giá trị do cắt chữ số. Cách này không kiểm tra số chữ số 0 mà người dùng gõ, vì `12.5000` có thể chuyển chính xác thành `12.50`.

### Ví dụ Java 17: kiểm tra scale không tự làm tròn

Tạo tệp `LocaleScalePolicyDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;

public class LocaleScalePolicyDemo {
    static BigDecimal requireCents(BigDecimal input) {
        return input.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static void main(String[] args) {
        System.out.println(requireCents(new BigDecimal("12.5")));
        System.out.println(requireCents(new BigDecimal("12.5000")));

        try {
            System.out.println(requireCents(new BigDecimal("12.345")));
        } catch (ArithmeticException ex) {
            System.out.println("Rejected: fractional value would be lost");
        }
    }
}
~~~

~~~bash
javac LocaleScalePolicyDemo.java
java LocaleScalePolicyDemo
~~~

Đầu ra:

~~~text
12.50
12.50
Rejected: fractional value would be lost
~~~

Nếu nghiệp vụ đòi từ chối cả `12.5000` vì chuỗi **đã nhập bốn chữ số lẻ**, phải có kiểm tra cú pháp/độ dài đầu vào riêng trước hoặc trong khi parse. Đừng nhầm kiểm tra tính chính xác số học với kiểm tra hình thức văn bản.

## 10. An toàn luồng và formatter có trạng thái

`BigDecimal` là bất biến. `NumberFormat` và `DecimalFormat` lại **có trạng thái mutable**: gọi `setRoundingMode`, `setCurrency`, `setParseBigDecimal` hay `setMaximumFractionDigits` thay đổi hành vi của instance.

Các formatter này **không mặc định thread-safe**. Không chia sẻ một `static DecimalFormat` có thể bị chỉnh sửa giữa nhiều request đồng thời mà thiếu đồng bộ hóa. Cách dễ hiểu là tạo formatter theo từng request/luồng; nếu dùng cache hoặc clone để tối ưu, phải kiểm soát đồng thời và việc thay đổi cấu hình.

Tránh phụ thuộc `Locale.getDefault()` cho kết quả phải tái lập. Trong kiểm thử, luôn chọn locale tường minh. Trong UI, dùng locale thực của người dùng; trong API, hợp đồng nhận dữ liệu phải nói rõ dạng số được chấp nhận.

## 11. Các lỗi phổ biến

1. **Dùng `new BigDecimal("1.234,50")`:** constructor không parse theo locale; dùng DecimalFormat với Locale.GERMANY.
2. **Cast `NumberFormat.parse(...)` về BigDecimal không bật `setParseBigDecimal(true)`:** dễ gặp ClassCastException hoặc đi qua kiểu không mong muốn.
3. **Chỉ bắt ParseException:** `parse` có thể nhận tiền tố và bỏ phần dư, nên cần kiểm tra ParsePosition.
4. **Nghĩ ParsePosition đảm bảo dấu nhóm hoàn toàn đúng:** parser có thể dễ dãi; kiểm tra input chuẩn theo locale nếu cần.
5. **Tự thay `,` thành `.`:** không hỗ trợ dấu nhóm Unicode, vị trí dấu hoặc locale khác.
6. **Chuyển BigDecimal sang double trước format:** có thể mất độ chính xác.
7. **Không cấu hình giới hạn chữ số và rounding mode:** chuỗi hiển thị có thể bị làm tròn ngoài mong đợi.
8. **Dùng round-trip format→parse để lưu giá trị:** chữ số bị cắt và scale gốc có thể mất.
9. **Trộn locale và loại tiền:** tiền tệ là metadata nghiệp vụ riêng, không chỉ là ký hiệu hiển thị.
10. **Tạo một DecimalFormat static dùng chung đa luồng:** có nguy cơ hành vi không an toàn trong truy cập đồng thời.
11. **Bỏ qua input đặc biệt như NaN/Infinity:** BigDecimal không biểu diễn được các giá trị ấy; kiểm tra kiểu sau parse.
12. **Dùng locale mặc định trong API/test:** có thể thay đổi giữa máy và gây kết quả khó lặp lại.

## 12. Tự kiểm tra

**Câu 1.** Chuỗi `"1.234,50"` nên được parse theo locale nào để có số `1234.50`?

**Đáp án:** `Locale.GERMANY`. BigDecimal(String) không parse trực tiếp chuỗi này.

**Câu 2.** Tại sao `"1,234"` không đủ để xác định giá trị số khi chưa biết locale?

**Đáp án:** Trong US dấu phẩy thường phân nhóm hàng nghìn (1234), trong Germany dấu phẩy thường là dấu thập phân (1.234).

**Câu 3.** Với formatter US, maxFractionDigits=2, HALF_UP, BigDecimal `1234.567` hiển thị gì? Parse lại có bằng số gốc không?

**Đáp án:** `1,234.57`, parse lại thành `1234.57` nên khác số gốc.

**Câu 4.** `ParsePosition.getIndex() == text.length()` có đảm bảo mọi dấu nhóm đặt đúng quy cách không?

**Đáp án:** Không. Nó chỉ kiểm tra đã parse hết ký tự; cú pháp grouping có thể cần được kiểm tra bổ sung.

**Câu 5.** `setScale(2, UNNECESSARY)` có từ chối `12.5000` không?

**Đáp án:** Không; giá trị chuyển chính xác thành `12.50`. Nếu cần từ chối chuỗi có quá hai chữ số sau dấu, phải kiểm tra input dưới dạng văn bản.

**Câu 6.** Tại sao không nên dùng một instance DecimalFormat mutable chung cho mọi request?

**Đáp án:** NumberFormat/DecimalFormat không tự đồng bộ; nhiều luồng có thể truy cập/tráo đổi trạng thái và cho kết quả không đáng tin.

## 13. Tổng kết

- **BigDecimal giữ số thập phân; Locale chỉ cách ghi/đọc văn bản**, vì vậy phân biệt dữ liệu máy và giao diện.
- **Format:** lấy NumberFormat theo locale, cấu hình grouping, số chữ số lẻ và RoundingMode; truyền BigDecimal trực tiếp.
- **Parse:** lấy DecimalFormat theo locale, bật `setParseBigDecimal(true)`, kiểm tra `ParsePosition`, kiểu trả về và ràng buộc nghiệp vụ.
- **Không giả định round-trip bảo toàn thông tin** nếu định dạng có làm tròn hoặc thay scale; không dùng double làm trung gian.
- Formatter mutable cần quản lý theo luồng, locale phải được lựa chọn rõ ràng, và đồng tiền phải được lưu độc lập với số.

Tài liệu API Java SE 17: [BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html), [NumberFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/NumberFormat.html), [DecimalFormat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormat.html), [DecimalFormatSymbols](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/DecimalFormatSymbols.html) và [ParsePosition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/ParsePosition.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-conversions-009">Phân biệt giá trị tính toán với scale trình bày</a>

<details>
<summary>Xem chi tiết</summary>

# BigDecimal: phân biệt giá trị tính toán với scale trình bày

Trong Java, khi một phép tính `BigDecimal` cho ra `13.000` nhưng giao diện cần hiển thị `13.00`, câu hỏi cần trả lời là: **giá trị số cần thay đổi, hay chỉ số chữ số được trình bày cần thay đổi?** Hai nhu cầu này có thể dẫn đến những thao tác khác nhau, thậm chí kết quả lưu trữ và cách kiểm tra bằng nhau khác nhau.

Bài học thuộc chủ đề **Chuyển đổi BigDecimal** và tập trung vào ranh giới giữa **giá trị tính toán**, **biểu diễn thập phân/`scale`**, **quy tắc làm tròn** và **chuỗi hiển thị**. Ví dụ dùng Java 17+ và chuỗi đầu vào thập phân để tránh sai số do chuyển đổi `double` không cần thiết.

## 1. Ba lớp khái niệm cần phân biệt

1. **Giá trị số (numeric value):** đại lượng toán học mà `BigDecimal` biểu diễn, chẳng hạn 13; 13.0; 13.00 đều có cùng giá trị số.
2. **Biểu diễn `BigDecimal`:** cặp số nguyên `unscaledValue` và `scale`. Các cách viết cùng giá trị có thể có `scale` khác nhau. `scale` là thuộc tính của đối tượng, không đơn thuần là lựa chọn phông chữ hay định dạng màn hình.
3. **Chuỗi trình bày:** các ký tự người dùng nhìn thấy: `13`, `13.00`, `13,00`, `1,000.00` hoặc `1E+3`. Quy tắc này có thể phụ thuộc locale, đơn vị, quy ước sản phẩm và đối tượng nhận dữ liệu.

Công thức nội tại của `BigDecimal`:

~~~text
numeric value = unscaledValue × 10^(-scale)
~~~

Ví dụ:

| Khởi tạo | Giá trị số | `unscaledValue()` | `scale()` | `toString()` |
|---|---:|---:|---:|---|
| `new BigDecimal("13")` | 13 | 13 | 0 | `13` |
| `new BigDecimal("13.0")` | 13 | 130 | 1 | `13.0` |
| `new BigDecimal("13.00")` | 13 | 1300 | 2 | `13.00` |
| `new BigDecimal("1.3E+1")` | 13 | 13 | 0 | `13` |
| `new BigDecimal("1E+3")` | 1000 | 1 | -3 | `1E+3` |

Lưu ý dòng `"1.3E+1"`: `BigDecimal` phân tích chuỗi khoa học thành một giá trị có `unscaledValue = 13` và `scale = 0`, nên `toString()` của đối tượng là `13`. Chuỗi đầu vào ban đầu không được giữ nguyên như một nhãn hiển thị riêng.

Hai `BigDecimal` có cùng **giá trị số** khi `compareTo() == 0`. Phương thức `equals()` đòi hỏi thêm `scale` bằng nhau:

~~~java
BigDecimal a = new BigDecimal("12.30");
BigDecimal b = new BigDecimal("12.300");

System.out.println(a.compareTo(b) == 0); // true
System.out.println(a.equals(b));         // false
~~~

Vì vậy, câu "hai số bằng nhau" phải chỉ rõ là **bằng theo toán học** hay **bằng theo biểu diễn `BigDecimal`**.

## 2. Giá trị tính toán và scale sau phép toán

`BigDecimal` hỗ trợ các phép tính thập phân chính xác (trong giới hạn toán tử, precision/rounding và tài nguyên), nhưng **kết quả phép tính vẫn có `scale`**. Với các phép tính chính xác không dùng `MathContext`, Java có quy tắc `scale` ưu tiên:

| Phép tính | `scale` ưu tiên |
|---|---|
| `a.add(b)` | `max(a.scale(), b.scale())` |
| `a.subtract(b)` | `max(a.scale(), b.scale())` |
| `a.multiply(b)` | `a.scale() + b.scale()` |
| `a.divide(b)` chính xác | `a.scale() - b.scale()` nếu đủ; phép chia có thể cần scale lớn hơn để biểu diễn thương chính xác |
| `a.scaleByPowerOfTen(n)` | `a.scale() - n` và **giá trị số nhân với `10^n`** |

Các quy tắc này mô tả **biểu diễn của kết quả**; chúng không tự quyết định giao diện phải in bao nhiêu chữ số. Với phép chia có kết quả thập phân vô hạn như `1 / 3`, `divide(BigDecimal)` không rounding có thể ném `ArithmeticException`. Khi đó phải chọn overload `divide` có `scale`/`RoundingMode` hoặc `MathContext` tùy ý nghĩa tính toán.

Ví dụ:

~~~java
BigDecimal quantity = new BigDecimal("12.30"); // scale 2
BigDecimal extra = new BigDecimal("0.700");   // scale 3
BigDecimal total = quantity.add(extra);

System.out.println(total);         // 13.000
System.out.println(total.scale()); // 3
~~~

Tổng số học là **13**, còn đối tượng kết quả biểu diễn ở **scale 3**. Việc màn hình cần hiện `13.00` là một yêu cầu khác với phép cộng vừa thực hiện.

**`precision()` không phải `scale()`:** `precision` là số chữ số trong phần không có dấu thập phân của biểu diễn, còn `scale` cho biết bậc thập phân. Ví dụ `123.4500` có `precision = 7` và `scale = 4`. `MathContext(5)` đặt giới hạn chữ số có nghĩa cho phép tính có context, không có nghĩa hiển thị đúng 5 chữ số sau dấu thập phân.

## 3. Những API nào đổi giá trị, API nào đổi biểu diễn?

| Thao tác | Có thể đổi **giá trị số**? | Ảnh hưởng `scale` và cách hiển thị |
|---|---|---|
| `value.setScale(2)` | Không, nếu biểu diễn chính xác được; nếu không thì ném ngoại lệ | Chỉnh `scale` thành 2 |
| `value.setScale(2, RoundingMode.HALF_UP)` | **Có** khi phải làm tròn | `scale = 2`, có thể bỏ hoặc tăng chữ số giá trị |
| `value.stripTrailingZeros()` | **Không** | Bỏ các số 0 tận cùng; `scale` có thể giảm và trở thành âm |
| `value.toString()` | **Không** | Trả chuỗi canonical, có thể dùng dạng khoa học |
| `value.toPlainString()` | **Không** | Trả chuỗi không dùng số mũ, không bắt buộc có số chữ số thập phân cố định |
| `DecimalFormat.format(value)` | Không thay đổi đối tượng đầu vào | Tạo chuỗi đã định dạng, có thể làm tròn cho mục đích hiển thị |
| `value.movePointLeft(n)` | **Có** nếu `n != 0` và giá trị khác 0 | Chia cho `10^n`, đồng thời thay `scale` |
| `value.movePointRight(n)` | **Có** nếu `n != 0` và giá trị khác 0 | Nhân với `10^n`, đồng thời thay `scale` |
| `value.scaleByPowerOfTen(n)` | **Có** nếu `n != 0` và giá trị khác 0 | Nhân với `10^n`, giữ kết quả `scale` âm khi cần |
| `value.round(MathContext)` | Có thể, nếu phải làm tròn chữ số có nghĩa | `precision` và `scale` sau rounding phụ thuộc kết quả |

Không nhìn vào tên `scaleByPowerOfTen` rồi cho rằng nó chỉ điều khiển "format": phương thức này **thực sự nhân/chia giá trị số** theo lũy thừa của 10. Ngược lại, gọi `toPlainString()` không nhân/chia và không tạo một `BigDecimal` đã được đổi `scale`.

## 4. setScale: giữ nguyên số nếu có thể, làm tròn nếu bắt buộc

`setScale(int newScale)` yêu cầu một biểu diễn có `scale` mới **mà không được thay đổi giá trị số**. Nếu đổi `scale` cần bỏ chữ số khác 0, overload này ném `ArithmeticException`.

~~~java
BigDecimal source = new BigDecimal("13.000");

System.out.println(source.setScale(2)); // 13.00
System.out.println(source.setScale(1)); // 13.0
System.out.println(source.setScale(0)); // 13
~~~

Cả ba kết quả đều bằng số học với `source`. Nhưng với `new BigDecimal("13.005").setScale(2)`, việc loại chữ số 5 làm đổi giá trị; vì thế Java **không âm thầm cắt bỏ** mà ném ngoại lệ.

Khi nghiệp vụ cho phép làm tròn, chọn `RoundingMode` **tường minh**:

~~~java
BigDecimal original = new BigDecimal("2.005");

System.out.println(
        original.setScale(2, RoundingMode.HALF_UP)
); // 2.01

System.out.println(
        original.setScale(2, RoundingMode.HALF_EVEN)
); // 2.00
~~~

Các kết quả trên khác giá trị số 2.005: **làm tròn là quyết định của miền nghiệp vụ**, không còn là lựa chọn hiển thị thuần túy nếu lấy kết quả làm đầu vào phép tính tiếp theo.

Chọn `RoundingMode.UNNECESSARY` khi muốn khẳng định phép chuyển phải chính xác: phương thức sẽ báo lỗi nếu phải làm tròn. Với tiền tệ hoặc đo lường, không chọn một quy tắc rounding chỉ vì đầu ra trông đẹp; phải theo hợp đồng nghiệp vụ.

## 5. Ví dụ Java 17+: cùng giá trị, nhiều dạng biểu diễn

Tạo tệp `BigDecimalValueDisplayDemo.java`:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class BigDecimalValueDisplayDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("12.30");
        BigDecimal b = new BigDecimal("12.300");

        System.out.println("a: " + a + " | scale=" + a.scale());
        System.out.println("b: " + b + " | scale=" + b.scale());
        System.out.println("value equal: " + (a.compareTo(b) == 0));
        System.out.println("representation equal: " + a.equals(b));

        BigDecimal total = a.add(new BigDecimal("0.700"));
        System.out.println("sum: " + total + " | scale=" + total.scale());
        System.out.println("normalized: " + total.stripTrailingZeros()
                + " | scale=" + total.stripTrailingZeros().scale());
        System.out.println("fixed: " + total.setScale(2)
                + " | scale=" + total.setScale(2).scale());

        BigDecimal scientific = new BigDecimal("1E+3");
        System.out.println("notation: " + scientific.toString());
        System.out.println("plain: " + scientific.toPlainString());
        System.out.println("scientific scale: " + scientific.scale());

        DecimalFormat display = new DecimalFormat(
                "#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        display.setRoundingMode(RoundingMode.HALF_UP);

        BigDecimal amount = new BigDecimal("1234.5");
        System.out.println("display: " + display.format(amount));
        System.out.println("original after format: " + amount);
    }
}
~~~

Chạy trong thư mục chứa tệp:

~~~bash
javac BigDecimalValueDisplayDemo.java
java BigDecimalValueDisplayDemo
~~~

Kết quả:

~~~text
a: 12.30 | scale=2
b: 12.300 | scale=3
value equal: true
representation equal: false
sum: 13.000 | scale=3
normalized: 13 | scale=0
fixed: 13.00 | scale=2
notation: 1E+3
plain: 1000
scientific scale: -3
display: 1,234.50
original after format: 1234.5
~~~

Giải thích:

- `a` và `b` bằng giá trị số nhưng khác `scale`. Điều này quan trọng nếu một tầng hệ thống dùng `equals()`, `hashCode()` hoặc tập hợp băm làm khóa.
- `total` là **13** về toán học. Gọi `stripTrailingZeros()` cho biểu diễn `13`; gọi `setScale(2)` cho `13.00` mà vẫn giữ giá trị 13.
- `scientific.toString()` hiển thị `1E+3` còn `toPlainString()` hiển thị `1000`. Đối tượng gốc vẫn có `scale = -3`.
- `DecimalFormat` chỉ tạo chuỗi `1,234.50` để hiển thị theo quy ước `Locale.US` trong ví dụ. `amount` sau định dạng vẫn là đối tượng biểu diễn `1234.5` với `scale = 1`.

`BigDecimal` là immutable: các phương thức chuyển đổi/rounding trả về đối tượng mới; các phương thức chuỗi/format không chỉnh sửa đối tượng đã tính.

## 6. toString, toPlainString và chuỗi có số thập phân cố định

Ba yêu cầu này không giống nhau:

**A. Cần chuỗi canonical của BigDecimal.** `toString()` trả chuỗi có quy tắc xác định; có thể chọn ký pháp khoa học, và có thể chuyển ngược bằng constructor `BigDecimal(String)` để thu lại cùng cặp biểu diễn `unscaledValue`/`scale` trong điều kiện hợp lệ. Ví dụ `new BigDecimal("1E+3").toString()` là `"1E+3"`.

**B. Cần chuỗi không dùng số mũ.** `toPlainString()` trả dạng thập phân phẳng, chẳng hạn `"1000"` thay cho `"1E+3"`. Nó **không tự đặt số chữ số thập phân cố định**, cũng không bảo toàn `scale` âm nếu sau đó parse lại chuỗi `"1000"`. Trong ví dụ này, parse `"1000"` tạo `scale = 0`, khác đối tượng ban đầu có `scale = -3`.

**C. Cần đúng hai chữ số sau dấu thập phân.** Nếu chính sách là tạo một `BigDecimal` có `scale = 2`, dùng `setScale(2)` hoặc overload có `RoundingMode`, rồi `toPlainString()` để nhận chuỗi `"1000.00"`. Nếu chỉ cần giao diện, dùng bộ định dạng trình bày phù hợp, không làm thay đổi dữ liệu tính toán.

Ví dụ:

~~~java
BigDecimal amount = new BigDecimal("1000");
System.out.println(amount.toPlainString());           // 1000
System.out.println(amount.setScale(2).toPlainString()); // 1000.00
~~~

Chú ý `toPlainString()` trên số có số mũ cực lớn có thể tạo chuỗi rất dài, tốn bộ nhớ. Đừng dùng nó vô điều kiện trên dữ liệu số mũ do người dùng nhập mà không giới hạn.

Nếu giao diện theo locale Việt Nam, dấu nhóm nghìn và dấu thập phân có thể khác `Locale.US`; đừng tự thay dấu `.` thành `,` trong chuỗi canonical rồi dùng chuỗi đó làm đầu vào cho `new BigDecimal(...)`. Phân tích dữ liệu và định dạng cho người dùng nên có **các quy tắc riêng**.

## 7. stripTrailingZeros: chuẩn hóa giá trị, nhưng thay đổi scale

`stripTrailingZeros()` trả `BigDecimal` bằng về giá trị số và bỏ các chữ số 0 ở cuối phần không chia tỷ lệ:

~~~java
BigDecimal x = new BigDecimal("1000.00");
BigDecimal y = x.stripTrailingZeros();

System.out.println(x);         // 1000.00
System.out.println(x.scale()); // 2
System.out.println(y);         // 1E+3
System.out.println(y.scale()); // -3
~~~

Trong ví dụ này, `stripTrailingZeros()` không thay số 1000, nhưng biểu diễn mới có **scale âm**. Vì vậy:
- Nếu mục tiêu là gộp các `BigDecimal` bằng giá trị số làm khóa, chuẩn hóa có thể hữu ích nhưng cần áp dụng cùng cách ở mọi nơi.
- Nếu `scale` thể hiện độ phân giải, số chữ số đo lường hoặc cam kết hợp đồng, không bỏ tự động.
- Nếu mục tiêu hiển thị `1000.00`, `stripTrailingZeros()` không phải công cụ phù hợp.

`stripTrailingZeros()` trên mọi giá trị bằng 0 chuẩn hóa về `BigDecimal.ZERO`, có `scale = 0`. Vì thế `new BigDecimal("0.000").stripTrailingZeros()` không giữ ba chữ số thập phân.

## 8. Scale tính toán có thể ảnh hưởng đến kết quả phép tính tiếp theo

Một quy tắc nguy hiểm là "hai số `compareTo() == 0` nên thay thế lẫn nhau trong mọi phép tính đều như nhau". Giá trị số trước phép tính giống nhau, nhưng **overload có rounding và scale mặc định có thể cho kết quả số học khác nhau**.

Ví dụ:

~~~java
BigDecimal divisor = new BigDecimal("3");

System.out.println(
        new BigDecimal("2.0").divide(divisor, RoundingMode.HALF_UP)
); // 0.7

System.out.println(
        new BigDecimal("2.00").divide(divisor, RoundingMode.HALF_UP)
); // 0.67
~~~

Overload `divide(BigDecimal divisor, RoundingMode roundingMode)` trả kết quả ở **scale của số bị chia**. Với `2.0`, kết quả làm tròn có một chữ số sau dấu thập phân; với `2.00`, kết quả có hai chữ số. Do đó hai đối tượng ban đầu bằng về giá trị số nhưng cho kết quả đã làm tròn khác nhau qua overload này.

Để tránh phụ thuộc ngầm, viết rõ quy tắc của nghiệp vụ:

~~~java
BigDecimal ratio = new BigDecimal("2.0")
        .divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP);
System.out.println(ratio); // 0.6667
~~~

`MathContext` kiểm soát số chữ số **có nghĩa** và rounding của phép tính; `scale` kiểm soát vị trí dấu thập phân. Không dùng `new MathContext(2)` với ý nghĩa "hai chữ số sau dấu phẩy". Tham số `precision = 2` có nghĩa hai chữ số có nghĩa, nên kết quả sẽ phụ thuộc độ lớn của số.

## 9. Định dạng chỉ ở lớp hiển thị: DecimalFormat và locale

Để hiển thị số cho con người, Java có `DecimalFormat` và `NumberFormat`. Đây là bước **render**: nhận một giá trị và tạo chuỗi theo pattern/locale. Ví dụ với `Locale.US`:

~~~java
DecimalFormat formatter = new DecimalFormat(
        "#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
formatter.setRoundingMode(RoundingMode.HALF_UP);

BigDecimal value = new BigDecimal("1234.5");
String label = formatter.format(value);

System.out.println(label); // 1,234.50
System.out.println(value); // 1234.5
~~~

Đoạn trên cần `import java.math.*; import java.text.*; import java.util.*;` hoặc các import cụ thể như ở chương trình mục 5.

**Lựa chọn phù hợp:**
- Cần tính toán thêm: giữ `BigDecimal` gốc với độ chính xác phù hợp.
- Cần nhập/xuất máy với format chuẩn: định nghĩa format và chính sách parse cụ thể; chuỗi `toString()` thường phù hợp cho giá trị số canonical.
- Cần hiển thị cho người dùng: dùng formatter theo locale và đơn vị mong muốn; chuỗi format có thể chứa dấu phân nhóm và không thể đưa trực tiếp vào constructor `BigDecimal(String)`.
- Cần lưu **giá trị đã chốt sau làm tròn**: áp dụng rounding vào `BigDecimal` trước thời điểm chốt theo nghiệp vụ, thay vì phụ thuộc số đã hiển thị.

`DecimalFormat` là đối tượng **không an toàn để chia sẻ và dùng đồng thời giữa nhiều luồng** nếu không đồng bộ phù hợp. Có thể tạo mỗi lần dùng hoặc cung cấp một instance riêng theo ngữ cảnh ứng dụng.

Một giao diện có thể hiển thị `12.35` từ dữ liệu `12.3456` mà không đổi dữ liệu gốc. Nếu người dùng xác nhận giao dịch ở `12.35`, ứng dụng phải quy định rõ **giá trị chốt** nào được lưu và tính toán tiếp; không suy ra ngầm rằng giá trị gốc đã bị làm tròn bởi formatter.

## 10. Chuyển đổi dữ liệu giữa các tầng ứng dụng

Hãy hình dung luồng xử lý:

~~~text
Chuỗi đầu vào / dữ liệu bên ngoài
        |
        v
Parse thành BigDecimal với quy ước rõ
        |
        v
Tính toán BigDecimal + rounding theo nghiệp vụ
        |
        +----> Lưu giá trị chốt theo hợp đồng schema
        |
        +----> Format theo locale và yêu cầu giao diện
                  |
                  v
              Chuỗi hiển thị
~~~

Tại mỗi ranh giới, cần phân biệt những hợp đồng sau.

**Đầu vào:** Với chuỗi decimal canonical `"12.345"`, `new BigDecimal("12.345")` giữ đúng giá trị thập phân. Tránh `new BigDecimal(0.1)` khi ý muốn là chính xác 0.1 vì số `double` thường không biểu diễn chính xác số thập phân đó; `BigDecimal.valueOf(0.1)` hoặc constructor từ chuỗi có hành vi phù hợp hơn cho trường hợp này.

**Tính toán:** Dùng `add`, `subtract`, `multiply`, `divide` và `MathContext`/`RoundingMode` nếu cần. Không làm tròn sau mỗi thao tác một cách máy móc; vị trí làm tròn phải theo chính sách, vì làm tròn ở các bước khác nhau có thể tạo tổng khác nhau.

**Lưu trữ:** Khi ghi vào cột cơ sở dữ liệu `DECIMAL(p,s)`/`NUMERIC(p,s)`, `p` và `s` là hợp đồng của schema/database, không mặc định trùng với `precision()` và `scale()` của đối tượng đang có. Database/driver có thể ép scale, làm tròn hoặc từ chối dữ liệu tùy hệ quản trị và schema. Hãy xác minh hành vi thực tế, dùng truy vấn có tham số và kiểm tra các trường hợp số chữ số vượt giới hạn. Không coi `toPlainString()` là một cơ chế kiểm soát schema.

**Giao tiếp JSON:** Nhiều thư viện serialize `BigDecimal` dưới dạng số JSON; một số có tùy chọn biểu diễn dạng khoa học hoặc dạng plain. Nếu gửi như **JSON number**, consumer có thể parse thành floating point và làm mất chữ số chính xác, cũng như không giữ `scale`. Nếu hợp đồng cần cả độ chính xác và số chữ số biểu diễn, có thể định nghĩa chuỗi decimal và metadata `scale`/đơn vị, đồng thời yêu cầu consumer tuân thủ. Không phụ thuộc cú pháp JSON number để truyền ý nghĩa "luôn hai chữ số".

**Hiển thị:** Chọn formatter phù hợp người dùng; ví dụ `#,##0.00` yêu cầu hai chữ số sau thập phân trong locale US. Dấu phẩy nhóm nghìn là ký hiệu trình bày, không phải thành phần của giá trị số.

## 11. Ví dụ chọn thiết kế: tiền, đo lường và số liệu báo cáo

### Trường hợp A — tiền với đơn vị nhỏ nhất

Giả sử hệ thống quy định một đơn vị lớn bằng 100 đơn vị nhỏ và chỉ chấp nhận **số nguyên đơn vị nhỏ nhất**. Giá trị gửi vào là `"12.345"` đơn vị lớn:

~~~java
BigDecimal amount = new BigDecimal("12.345");
BigDecimal minor = amount.movePointRight(2); // 1234.5
~~~

Nếu dùng `longValue()`, phần lẻ bị cắt và làm sai số tiền. Hãy yêu cầu chuyển đổi **chính xác** bằng `longValueExact()` hoặc `toBigIntegerExact()`; nếu số lẻ không hợp lệ thì từ chối, hoặc làm tròn theo quy tắc đã được phê duyệt trước khi chuyển.

Ví dụ quy tắc chốt giá trị rõ ràng:

~~~java
BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
long cents = rounded.movePointRight(2).longValueExact();

System.out.println(rounded); // 12.35
System.out.println(cents);   // 1235
~~~

Đây là **một quyết định làm tròn thay đổi giá trị**, không phải chỉ đổi hiển thị. Hệ số 100 chỉ đúng cho đơn vị/hệ tiền được giả định trong ví dụ; không suy rằng mọi loại tiền đều có hai chữ số đơn vị nhỏ nhất.

### Trường hợp B — số đo có độ phân giải

Hai số `1.2` và `1.20` bằng nhau về số học, nhưng nếu nguồn đo quy ước số chữ số thập phân thể hiện độ phân giải, việc gọi `stripTrailingZeros()` có thể làm mất thông tin biểu diễn. Cần xác định rõ **độ chính xác phép đo** có được mô hình hóa bằng `scale` hay bằng metadata riêng. `BigDecimal` giữ số chữ số thập phân nhưng không tự chứng minh số liệu đo thực tế có độ chính xác đến mức đó.

### Trường hợp C — báo cáo chỉ yêu cầu trình bày đồng nhất

Dữ liệu sau cộng là `13.000` nhưng báo cáo cần đúng hai chữ số. Nếu không chốt giá trị mới, có thể dùng formatter để tạo `"13.00"`. Nếu cần xuất một giá trị decimal có `scale = 2` vì hợp đồng trao đổi dữ liệu, dùng `setScale(2)` với xử lý lỗi hoặc rounding rõ ràng. Hai hướng đều cho chữ `13.00` nhưng **ý nghĩa đối với dữ liệu gốc khác nhau**.

## 12. Sai lầm thường gặp

1. **Nhầm `scale` với độ chính xác phép tính:** `scale = 2` chỉ xác định vị trí dấu thập phân trong biểu diễn, không khẳng định con số đã được đo hoặc tính chính xác đến hai chữ số đó.
2. **Tưởng `setScale` chỉ đổi nhãn:** nếu giảm `scale` mà phải loại chữ số khác 0, kết quả làm tròn đổi giá trị; overload không cho phép làm tròn sẽ ném ngoại lệ.
3. **Tưởng `toPlainString()` đảm bảo hai chữ số:** nó chỉ bỏ ký pháp mũ. `new BigDecimal("1E+3").toPlainString()` là `"1000"`, không phải `"1000.00"`.
4. **Dùng `stripTrailingZeros()` trước mọi phép tính:** có thể xóa thông tin `scale` cần thiết và làm kết quả rounding của một số overload trở nên khác trước.
5. **Dùng `scaleByPowerOfTen` để "định dạng":** phương thức này nhân/chia giá trị số theo bậc 10, không chỉ sửa cách in số.
6. **Tin rằng chuỗi hiển thị là dữ liệu canonical:** `"1,234.50"` còn phụ thuộc locale và dấu ngăn cách; tách parse và format.
7. **Nhầm `compareTo` với `equals`:** `new BigDecimal("2.0").compareTo(new BigDecimal("2.00")) == 0`, nhưng `equals()` là `false`. Kết quả này có thể tác động đến khóa `HashMap` và cấu trúc có sắp xếp như `TreeMap`.
8. **Khởi tạo bằng `new BigDecimal(double)` rồi trách rounding/scale:** sai lệch có thể xuất phát từ giá trị binary floating point ban đầu; tạo bằng chuỗi decimal hoặc `BigDecimal.valueOf(double)` khi phù hợp.
9. **Làm tròn sớm và nhiều lần:** phép cộng các giá trị đã làm tròn từng dòng có thể khác làm tròn một lần ở tổng cuối. Quy định rõ thời điểm chốt rounding.
10. **Quên giới hạn đầu ra:** ký pháp khoa học có thể biểu diễn số có bậc mũ lớn ngắn gọn, trong khi `toPlainString()` có thể tạo rất nhiều ký tự. Kiểm soát giới hạn tài nguyên đối với dữ liệu không tin cậy.

## 13. Bài tập tự kiểm tra

**Bài 1.** Với mã:

~~~java
BigDecimal original = new BigDecimal("3.000");
BigDecimal a = original.setScale(1);
BigDecimal b = original.stripTrailingZeros();

System.out.println(a);                 // ?
System.out.println(a.scale());         // ?
System.out.println(b);                 // ?
System.out.println(b.scale());         // ?
System.out.println(original.equals(a));// ?
System.out.println(original.compareTo(b) == 0);// ?
~~~

**Đáp án:** `3.0`, `1`, `3`, `0`, `false`, `true`. Cả ba bằng về giá trị số nhưng khác `scale` và có thể khác `equals()`.

**Bài 2.** `new BigDecimal("3.005").setScale(2, RoundingMode.HALF_UP)` trả gì? **Đáp án:** `3.01`. Đây là thay đổi giá trị số; nếu không truyền rounding mode, overload `setScale(2)` ném `ArithmeticException`.

**Bài 3.** `new BigDecimal("1E+3").setScale(2).toPlainString()` trả gì? **Đáp án:** `"1000.00"`. Việc đặt `scale = 2` là một thao tác trên đối tượng mới; `toPlainString()` sau đó chuyển thành chuỗi.

**Bài 4.** Hệ thống cần tính trung bình của nhiều con số rồi hiển thị hai chữ số. Có nên `setScale(2, HALF_UP)` sau *từng phần tử* trước khi cộng? **Gợi ý:** chỉ nếu hợp đồng yêu cầu làm tròn từng phần tử. Thông thường hãy giữ giá trị tính toán phù hợp và làm tròn ở điểm xác định bởi nghiệp vụ; sau đó mới format giao diện. So sánh phương án qua các dữ liệu có chữ số thứ ba để kiểm tra chênh lệch.

**Bài 5.** Một backend gửi JSON number `1.00` cho client và yêu cầu client luôn biết người nhập đã gõ hai chữ số sau dấu thập phân. Điều đó có chắc chắn không? **Đáp án:** Không. Cấu trúc JSON number và cách parser bên nhận hoạt động không bảo đảm giữ ý nghĩa `scale`. Phải thỏa thuận hợp đồng trao đổi rõ, có thể dùng chuỗi decimal hoặc metadata riêng khi số chữ số biểu diễn là dữ liệu nghiệp vụ.

## 14. Quy tắc áp dụng và tài liệu tham khảo

Trước khi đổi một `BigDecimal`, hãy trả lời theo đúng thứ tự:

1. **Giá trị toán học có được phép thay đổi không?** Nếu không, tránh rounding không được yêu cầu; dùng chuyển đổi chính xác và kiểm tra ngoại lệ.
2. **`scale` có phải một phần hợp đồng dữ liệu không?** Nếu có, bảo toàn hoặc chuẩn hóa theo quy tắc được công bố; không tùy tiện bỏ số 0 cuối.
3. **Đầu ra phục vụ tính toán, lưu trữ hay hiển thị?** Dùng `BigDecimal` cho tính toán, định nghĩa rõ schema cho lưu trữ, dùng định dạng/locale cho trình bày.
4. **Khi nào và theo quy tắc nào sẽ làm tròn?** Mã hóa điều đó bằng `RoundingMode`/`MathContext` hoặc overload `divide` tương ứng.

Tài liệu chính thức:
- [BigDecimal — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html): biểu diễn `scale`, preferred scale, `setScale`, `toString`, `toPlainString`, `stripTrailingZeros` và các phép toán.
- [RoundingMode — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/RoundingMode.html): ý nghĩa các chế độ làm tròn.
- [DecimalFormat — Java SE 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/DecimalFormat.html): pattern định dạng số, locale và việc sử dụng đa luồng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="big-decimal-conversions-010">Thiết kế dạng trao đổi giữ số mà không đi qua double trung gian</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế dạng trao đổi giữ số chính xác, không qua `double` trung gian

## 1. Bài toán và tiêu chí thành công

Khi chuyển một giá trị `BigDecimal` qua JSON, HTTP, hàng đợi, tập tin hoặc giữa Java và JavaScript, lỗi phổ biến là đưa số qua `double`/`Double` ở một mắt xích bất kỳ. `double` là số dấu phẩy động nhị phân hữu hạn (IEEE 754 binary64), nên không thể biểu diễn chính xác mọi số thập phân, kể cả `0.1`, và không thể giữ riêng rẽ mọi số nguyên lớn.

Ví dụ trong Java:

~~~java
BigDecimal exact = new BigDecimal("9007199254740993.01");
BigDecimal altered = BigDecimal.valueOf(exact.doubleValue());

System.out.println(exact.toPlainString());   // 9007199254740993.01
System.out.println(altered.toPlainString()); // 9007199254740994
~~~

Việc chuyển sang `double` đã làm mất dữ liệu; chuyển ngược bằng `BigDecimal.valueOf(...)` **không thể khôi phục** những chữ số vốn đã bị làm tròn.

Một dạng trao đổi tốt cần định nghĩa rõ:

1. **Giữ nguyên giá trị số học**: phía nhận đọc được đúng số thập phân phía gửi định truyền.
2. **Có cần giữ nguyên `scale` không?** `1.0` và `1.00` có cùng giá trị theo `compareTo`, nhưng không bằng nhau theo `BigDecimal.equals`.
3. **Giữ nguyên mọi chữ số trên đường truyền**, không chuyển ngầm qua `double`, `float`, `Number` của JavaScript hoặc ép kiểu tương tự.
4. **Hợp đồng có thể kiểm thử**: thống nhất kiểu dữ liệu, cú pháp, giới hạn, quy tắc làm tròn và cách báo lỗi.
5. **Bảo vệ tài nguyên**: kiểm soát độ dài, `precision`, `scale` và số mũ trước các phép chuyển đổi tốn bộ nhớ.

Bài học này tập trung vào thiết kế và thực hiện trao đổi `BigDecimal` giữa các thành phần. Khi cần phép tính có làm tròn, chính sách làm tròn phải được xác định riêng, không lén thực hiện trong bước mã hóa hoặc giải mã.

## 2. Vì sao `double` là bước trung gian không an toàn?

`BigDecimal` biểu diễn một số bằng `unscaledValue × 10^(-scale)`. Với chuỗi thập phân, `new BigDecimal(String)` đọc các chữ số chính xác theo biểu diễn thập phân.

Ngược lại, `double` lưu một lượng hữu hạn bit cho số có nghĩa và số mũ nhị phân. Có hai nhóm lỗi chính:

- **Phân số thập phân**: nhiều số như `0.1` không biểu diễn chính xác bằng nhị phân hữu hạn. `new BigDecimal(0.1)` cho giá trị chính xác của *số double đang lưu*, có thể dài hơn nhiều so với chuỗi `"0.1"`.
- **Số nguyên vượt miền an toàn của JavaScript**: `Number` là binary64, nên mọi số nguyên trong khoảng `[-(2^53-1), 2^53-1]` mới được bảo đảm biểu diễn chính xác từng đơn vị. `9007199254740993` không thuộc miền đó.

Minh họa:

~~~java
BigDecimal text = new BigDecimal("0.1");
BigDecimal viaDoubleConstructor = new BigDecimal(0.1);

System.out.println(text.equals(viaDoubleConstructor)); // false

BigDecimal huge = new BigDecimal("9007199254740993");
System.out.println(
        BigDecimal.valueOf(huge.doubleValue()).toPlainString()
); // 9007199254740992
~~~

Đừng hiểu nhầm rằng `BigDecimal.valueOf(double)` vô dụng: nó thường thích hợp hơn `new BigDecimal(double)` khi **đầu vào đã thực sự là một `double`** và bạn cần biểu diễn thập phân theo `Double.toString`. Tuy nhiên, không cách nào trong hai cách đó lấy lại chữ số từng tồn tại trước khi dữ liệu bị chuyển sang `double`.

**Quy tắc kiến trúc:** nếu nguồn là chuỗi thập phân, dữ liệu `DECIMAL/NUMERIC` hoặc `BigDecimal`, giữ nó dưới dạng **chuỗi thập phân chính xác hoặc kiểu decimal chính xác** cho đến khi đến đích.

## 3. Đầu tiên phải phân biệt hai mức “giữ nguyên”

### A. Giữ giá trị số học

Hai biểu diễn có giá trị bằng nhau nếu:

~~~java
BigDecimal a = new BigDecimal("1000");
BigDecimal b = new BigDecimal("1E+3");

System.out.println(a.compareTo(b) == 0); // true
System.out.println(a.equals(b));         // false
~~~

Mức bảo toàn này phù hợp khi chỉ quan tâm kết quả tính toán, chẳng hạn tổng đo lường được chuẩn hóa và quy tắc nghiệp vụ không dùng `scale`.

### B. Giữ cả giá trị lẫn `scale`

`BigDecimal.equals` xét cả giá trị và `scale`. Ví dụ:

| Giá trị nhập | `unscaledValue()` | `scale()` |
| --- | ---: | ---: |
| `"1.0"` | `10` | `1` |
| `"1.00"` | `100` | `2` |
| `"1000"` | `1000` | `0` |
| `"1E+3"` | `1` | `-3` |

Nếu `scale` là phần của hợp đồng (ví dụ để giữ số chữ số thập phân đã khai báo), cần round-trip mà `BigDecimal.equals` cũng trả `true`.

Không nên quy kết số chữ số 0 cuối là “không quan trọng” trong mọi hệ thống. Đối với tính toán số học có thể không quan trọng, nhưng trong nhập liệu, xuất chứng từ hoặc trao đổi dựa trên biểu diễn, thông tin đó có thể cần được giữ lại.

## 4. Các phương án wire format và đánh đổi

### Phương án 1 — JSON string chứa decimal chính xác (khuyến nghị mặc định)

~~~json
{
  "currency": "USD",
  "amount": "9007199254740993.01"
}
~~~

Hợp đồng: `amount` là **JSON string**, không phải JSON number. Nội dung chuỗi biểu diễn số thập phân theo cú pháp đã thỏa thuận; phía Java giải mã trực tiếp bằng `new BigDecimal(amount)`.

Ưu điểm:

- JavaScript `JSON.parse` trả về **string**, không tự làm tròn qua `Number`.
- Java, Python, .NET hoặc các ngôn ngữ khác có thể parse bằng kiểu decimal có độ chính xác cần thiết.
- Dễ duy trì tính toàn vẹn của số nguyên rất lớn, nhiều chữ số thập phân và ký pháp số mũ.
- Có thể quy định rằng chuỗi là đầu ra của `BigDecimal.toString()` để giữ cả `scale`.

Cần thống nhất schema: tên trường, loại JSON **string**, định dạng dấu chấm thập phân, dấu âm, hỗ trợ số mũ, độ dài tối đa và ràng buộc miền giá trị. Đừng để client tùy tiện gọi `Number(amount)` hoặc `parseFloat(amount)` trước khi tính toán.

### Phương án 2 — JSON number với pipeline decimal chính xác từ đầu đến cuối

~~~json
{
  "amount": 9007199254740993.01
}
~~~

Cú pháp JSON **cho phép** literal số có nhiều chữ số, nhưng JSON không bảo đảm mọi parser giữ nguyên độ chính xác. Nếu một bên làm `JSON.parse` trong JavaScript thông thường, `amount` sẽ trở thành `Number`; số đã mất chính xác ngay tại bước parse.

Phương án này chỉ đáp ứng yêu cầu khi **mọi mắt xích** đều đọc token số trực tiếp thành decimal tùy ý và không có bước trung gian qua binary64. Ví dụ một parser Java được cấu hình bind trường sang `BigDecimal` có thể đọc token decimal chính xác, trong khi một parser khác bind sang `Double` có thể làm tròn.

Việc Java tự serialize `BigDecimal` thành JSON numeric token không đủ chứng minh rằng toàn tuyến an toàn. Hãy kiểm thử cả các consumer, message broker, proxy và bước chuyển đổi dữ liệu.

### Phương án 3 — đơn vị nhỏ nhất dưới dạng chuỗi số nguyên

Ví dụ, nếu hợp đồng quy định hai chữ số thập phân:

~~~json
{
  "currency": "USD",
  "minorUnits": "123456",
  "scale": 2
}
~~~

Giá trị được hiểu là `123456 × 10^(-2) = 1234.56`. `minorUnits` là **string số nguyên** để phía JavaScript không vô tình mất chính xác; phía Java có thể dùng `BigInteger`.

Phương án này hữu ích trong thanh toán hoặc hệ thống đòi hỏi số nguyên theo một đơn vị đã định trước. Nhưng `scale` không nên tự suy từ mã tiền tệ một cách tùy tiện; cần áp dụng đúng chính sách và phiên bản quy định nghiệp vụ. Nếu giá trị gốc có nhiều chữ số hơn `scale` cho phép, phải xác định **từ chối** hay **làm tròn với chế độ nào** trước khi mã hóa.

### Phương án 4 — truyền tách `unscaled` và `scale`

~~~json
{
  "unscaled": "100000",
  "scale": 2
}
~~~

Phía nhận dựng lại:

~~~java
BigDecimal number = new BigDecimal(
        new BigInteger("100000"), 2
); // 1000.00
~~~

Biểu diễn này giữ cả giá trị và `scale` mà không cần dựa trên quy tắc chuỗi chuẩn. `unscaled` phải được coi là string số nguyên lớn; `scale` là số nguyên có ràng buộc miền và độ lớn hợp lý (ở Java, `BigDecimal.scale()` trả `int`).

Ưu điểm là contract tường minh và dễ tái dựng. Nhược điểm là schema dài hơn và mọi bên phải triển khai đúng công thức `unscaled × 10^(-scale)`, đặc biệt với `scale` âm.

### Chọn phương án nào?

| Mục tiêu | Khuyến nghị |
| --- | --- |
| API JSON có web client hoặc nhiều ngôn ngữ | Decimal **string** |
| Chỉ có các service dùng parser decimal đã được xác minh | JSON number có thể dùng, kèm kiểm thử toàn tuyến |
| Dữ liệu gắn với đơn vị nhỏ nhất cố định | Integer minor units **string** cùng scale/đơn vị |
| Phải lưu đúng biểu diễn `BigDecimal` gồm scale | Decimal `toString()` hoặc cặp `unscaled/scale` |

## 5. Hợp đồng chuỗi decimal: `toString()` khác `toPlainString()`

Hai phương thức đều tạo chuỗi chứa giá trị số mà không chuyển qua `double`, nhưng **khả năng giữ scale khi parse lại khác nhau**.

| Giá trị ban đầu | `toString()` | `toPlainString()` | Parse lại có giữ scale? |
| --- | --- | --- | --- |
| `new BigDecimal("12.3400")` | `12.3400` | `12.3400` | Cả hai |
| `new BigDecimal("1E+3")` | `1E+3` | `1000` | Chỉ `toString()` |
| `new BigDecimal("1000")` | `1000` | `1000` | Cả hai |
| `new BigDecimal("0.000")` | `0.000` | `0.000` | Cả hai |

Điểm then chốt:

~~~java
BigDecimal original = new BigDecimal("1E+3");

BigDecimal canonicalRoundTrip =
        new BigDecimal(original.toString());

BigDecimal plainRoundTrip =
        new BigDecimal(original.toPlainString());

System.out.println(original.equals(canonicalRoundTrip)); // true
System.out.println(original.equals(plainRoundTrip));     // false
System.out.println(original.compareTo(plainRoundTrip));  // 0
~~~

`BigDecimal.toString()` có thể dùng ký pháp mũ (`1E+3`), nhưng biểu diễn chuẩn này chứa đủ thông tin để `new BigDecimal(original.toString()).equals(original)` trả về `true`.

`toPlainString()` loại bỏ ký pháp mũ, phù hợp để hiển thị hoặc trao đổi khi chỉ cần giữ **giá trị số học**. Với scale âm, việc đọc lại chuỗi plain thường tạo scale khác. Nếu một hệ thống chỉ cho chuỗi thập phân không có số mũ nhưng vẫn phải giữ scale, truyền kèm trường `scale` theo schema riêng.

Lưu ý: `stripTrailingZeros()` **thay đổi scale** dù giá trị giữ nguyên; không gọi nó một cách vô thức trước khi xuất dữ liệu cần round-trip chính xác theo `equals()`. Tương tự, `setScale(...)` có thể thêm số 0 hoặc làm tròn, vì vậy là một quy tắc nghiệp vụ riêng, không phải bước bắt buộc của serialization.

## 6. Thiết kế hợp đồng trao đổi cụ thể

Ví dụ thống nhất hợp đồng cho `PaymentWire`:

~~~json
{
  "currency": "USD",
  "amount": "9007199254740993.01"
}
~~~

- `currency`: mã đơn vị theo danh sách hệ thống hỗ trợ, được kiểm tra độc lập.
- `amount`: JSON **string**, chứa chuỗi canonical do `BigDecimal.toString()` tạo ra.
- Sử dụng dấu `.` làm dấu thập phân, dấu `-` cho số âm, có thể có số mũ `E` cùng dấu số mũ `+` hoặc `-`.
- Không chứa dấu phân nhóm (`,`), ký hiệu tiền tệ, khoảng trắng hoặc định dạng phụ thuộc locale.
- Giới hạn tổng ký tự, `precision`, `scale` và miền giá trị được xác định theo nghiệp vụ.
- Người nhận parse trực tiếp `String → BigDecimal`, không sử dụng `Double`, `Float`, `BigDecimal(double)` hoặc `Number` làm trung gian.
- Nếu dữ liệu không hợp lệ, người nhận trả lỗi validation rõ ràng; không âm thầm thay thế bằng `0` hoặc làm tròn.

Vì hợp đồng lựa chọn chuỗi canonical, các dạng như `"01.20"`, `"1e3"` hay `"1E+03"` có thể là chuỗi mà một parser decimal đọc được, nhưng **bị từ chối theo tính canonical** nếu đầu ra `BigDecimal.toString()` khác nguyên văn đầu vào. Nếu cần tương thích nhiều producer có quy tắc khác nhau, có thể nới canonical check nhưng vẫn giữ kiểm tra decimal chính xác và thống nhất yêu cầu bảo toàn scale.

## 7. Java 17+ — ví dụ hoàn chỉnh chạy độc lập

Tệp: `LosslessDecimalWireDemo.java`.

Đây là mô hình dữ liệu wire độc lập với thư viện JSON. Một thư viện JSON có thể ánh xạ record `PaymentWire` thành object JSON, trong đó trường `amount` mang kiểu string. Chương trình dưới đây tập trung kiểm chứng tính toàn vẹn của decimal tại điểm mã hóa/giải mã.

~~~java
import java.math.BigDecimal;
import java.util.Objects;

public class LosslessDecimalWireDemo {
    record PaymentWire(String currency, String amount) {}

    private static final int MAX_TEXT_LENGTH = 120;
    private static final int MAX_PRECISION = 50;
    private static final int MAX_ABS_SCALE = 50;

    static PaymentWire encode(String currency, BigDecimal amount) {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        return new PaymentWire(currency, amount.toString());
    }

    static BigDecimal decode(PaymentWire wire) {
        Objects.requireNonNull(wire, "wire");
        String decimal = wire.amount();

        if (decimal == null || decimal.isEmpty()
                || decimal.length() > MAX_TEXT_LENGTH
                || !decimal.matches(
                        "-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"
                )) {
            throw new IllegalArgumentException("Invalid decimal string");
        }

        final BigDecimal parsed;
        try {
            parsed = new BigDecimal(decimal);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid decimal number", ex);
        }

        if (parsed.precision() > MAX_PRECISION
                || Math.abs((long) parsed.scale()) > MAX_ABS_SCALE) {
            throw new IllegalArgumentException("Decimal outside contract");
        }

        // Bảo đảm một cách viết canonical tương ứng một biểu diễn.
        if (!parsed.toString().equals(decimal)) {
            throw new IllegalArgumentException("Non-canonical decimal");
        }

        return parsed;
    }

    public static void main(String[] args) {
        BigDecimal original = new BigDecimal("9007199254740993.01");
        PaymentWire wire = encode("USD", original);
        BigDecimal decoded = decode(wire);

        System.out.println("wire amount: " + wire.amount());
        System.out.println(
                "same numeric value: " + (original.compareTo(decoded) == 0)
        );
        System.out.println("same value and scale: " + original.equals(decoded));

        BigDecimal passedThroughDouble =
                BigDecimal.valueOf(original.doubleValue());
        System.out.println(
                "through double: " + passedThroughDouble.toPlainString()
        );

        BigDecimal exponent = new BigDecimal("1E+3");
        BigDecimal restored = decode(encode("USD", exponent));
        System.out.println("exponent wire: " + exponent.toString());
        System.out.println("restored scale: " + restored.scale());
        System.out.println("exponent round-trip equals: "
                + exponent.equals(restored));
        System.out.println("plain reparsed equals: "
                + exponent.equals(new BigDecimal(exponent.toPlainString())));
    }
}
~~~

Chạy:

~~~bash
javac LosslessDecimalWireDemo.java
java LosslessDecimalWireDemo
~~~

Stdout mong đợi:

~~~text
wire amount: 9007199254740993.01
same numeric value: true
same value and scale: true
through double: 9007199254740994
exponent wire: 1E+3
restored scale: -3
exponent round-trip equals: true
plain reparsed equals: false
~~~

Phần `MAX_TEXT_LENGTH`, `MAX_PRECISION` và `MAX_ABS_SCALE` là **giới hạn minh họa**, cần thiết kế lại theo miền số cho từng API. Trường `currency` trong demo chỉ được mang theo; triển khai thực tế cần xác minh enum/danh sách tiền tệ, quyền truy cập, và các quy tắc giá trị (ví dụ có cho phép số âm không). Giới hạn scale được kiểm tra **trước khi** có thao tác `toPlainString()` có thể mở rộng một số mũ khổng lồ thành chuỗi quá dài.

### Dùng với thư viện JSON

Trên Java, một thư viện JSON như Jackson có thể deserialize chuỗi JSON `"amount"` trực tiếp vào thuộc tính `String` của DTO hoặc record, sau đó gọi `decode`. Ví dụ payload:

~~~json
{"currency":"USD","amount":"9007199254740993.01"}
~~~

Trường `amount` bắt buộc giữ là **string** trong JSON schema. Khi serialize, lấy `BigDecimal.toString()` để tạo string. Khi deserialize, đọc string và gọi `new BigDecimal(String)` như ví dụ. **Không bind sang `double` hay `Double` rồi mới gọi `BigDecimal.valueOf`**.

Nếu chọn JSON number, có thể bind trực tiếp token sang `BigDecimal` bằng cấu hình parser thích hợp, nhưng phải xác nhận tất cả các mắt xích khác cũng xử lý decimal chính xác. Nhiều parser mặc định chuyển token JSON number sang kiểu binary64 hoặc một kiểu có giới hạn khác; không đủ an toàn để suy đoán từ phía gửi.

## 8. Client JavaScript: đọc chuỗi như chuỗi

Trong JavaScript thông thường:

~~~javascript
const payload = JSON.parse(
  '{"currency":"USD","amount":"9007199254740993.01"}'
);

console.log(typeof payload.amount); // string
console.log(payload.amount);        // 9007199254740993.01
~~~

Chuỗi `amount` vẫn giữ nguyên ký tự thập phân. **Không** làm thế này nếu cần tính toán chính xác:

~~~javascript
const unsafe = Number(payload.amount);
console.log(unsafe); // giá trị đã bị làm tròn
~~~

JavaScript `BigInt` phù hợp với số nguyên tùy ý, **không hỗ trợ trực tiếp phần thập phân**. Với decimal tổng quát, hãy dùng thư viện decimal tùy ý hoặc truyền chuỗi về service có `BigDecimal`. Với hợp đồng minor units, chỉ chuyển chuỗi đơn vị nhỏ nhất thành `BigInt` sau validation và giữ scale riêng.

Nếu muốn hiển thị theo locale, xử lý định dạng tại tầng UI bằng công cụ hỗ trợ decimal phù hợp. `Intl.NumberFormat` làm việc với `Number` theo các cách gọi quen thuộc; việc gọi `Number(decimalString)` trước khi format có thể đã mất chính xác. Không thay dấu chấm bằng dấu phẩy trong dữ liệu wire vì locale của người dùng.

## 9. Những đường đi khác cũng phải giữ chính xác

### HTTP/REST

- Request/response schema xác định `amount` là string và tài liệu hóa giới hạn, ví dụ `"12.3400"`.
- Bên Java nhận trực tiếp chuỗi, xác thực, parse thành `BigDecimal`.
- Bên server serialize lại từ `BigDecimal` bằng `toString()` nếu cần giữ scale.
- Kiểm thử HTTP thực tế qua serializer, gateway và client thay vì chỉ unit test hàm `decode`.

### Cơ sở dữ liệu quan hệ

Với cột `DECIMAL(p,s)` hoặc `NUMERIC(p,s)` theo đặc tả hệ quản trị, dùng `PreparedStatement.setBigDecimal(...)` và `ResultSet.getBigDecimal(...)`. Tránh đưa dữ liệu qua `setDouble/getDouble`.

~~~java
// Minh họa trong ngữ cảnh đã có PreparedStatement ps và ResultSet rs:
ps.setBigDecimal(1, new BigDecimal("1234.56"));
BigDecimal amount = rs.getBigDecimal("amount");
~~~

Đây là **đoạn minh họa JDBC**, không phải chương trình chạy độc lập. Schema database vẫn có thể áp đặt precision/scale, làm tròn hoặc từ chối dữ liệu tùy engine/cấu hình. Hãy kiểm tra chính sách cột và round-trip của chính driver/database được dùng.

### Message broker, CSV, spreadsheet và tập tin

- Payload thông qua queue vẫn dùng hợp đồng chuỗi decimal; không dùng bước mapper trung gian chuyển thành `Double`.
- CSV là text nhưng công cụ import có thể tự đoán cột thành floating point. Khai báo cột decimal là text hoặc nhập bằng parser decimal chính xác.
- Nếu truyền `unscaled/scale`, tránh đưa `unscaled` vào JSON number ở consumer JavaScript.
- Khi giao tiếp với hệ thống chưa hỗ trợ decimal chính xác, yêu cầu tiêu chuẩn đầu ra dạng **string** và kiểm tra round-trip thay vì trông chờ “JSON number lúc nào cũng chính xác”.

## 10. Validation, chuẩn hóa và bảo mật tài nguyên

Một parser decimal chính xác không đồng nghĩa mọi đầu vào đều hợp lệ:

1. **Cú pháp**: chặn `NaN`, `Infinity`, chuỗi rỗng, whitespace không quy định, dấu phân nhóm và số có format ngoài hợp đồng. `BigDecimal` không đại diện các giá trị `NaN/Infinity`.
2. **Độ dài và precision**: hạn chế số lượng chữ số để tránh payload rất dài.
3. **Scale và exponent**: giới hạn miền `scale`; không gọi `toPlainString()` trên `1E+100000000` khi chưa kiểm soát vì có thể tạo chuỗi khổng lồ.
4. **Miền giá trị**: kiểm tra số âm/dương, `min/max`, đơn vị, loại tiền và ý nghĩa trường.
5. **Canonical representation**: nếu muốn ký số, hash, cache hoặc tránh nhiều cách viết của cùng biểu diễn, xác định thuật toán canonical rồi áp dụng nhất quán. `BigDecimal.toString()` duy trì scale, nhưng nó không ép hai giá trị numerically equal có scale khác nhau thành một chuỗi.
6. **Tính nguyên tử của xử lý**: xác thực trước khi ghi database hoặc gửi tiếp, không âm thầm làm tròn tại lớp hạ tầng.

Đối với số 0, `BigDecimal` không giữ dấu âm tách biệt: `new BigDecimal("-0.00").toString()` là `"0.00"`. Nếu hợp đồng bắt buộc bảo toàn từng ký tự đầu vào (bao gồm dấu `-` của “âm 0”, số 0 đứng trước, chữ `e` thường hoặc khoảng trắng), phải lưu **raw input string** riêng. Bảo toàn `BigDecimal` theo `equals` không phải là bảo toàn byte-for-byte nguyên văn dữ liệu gốc.

Nếu phía gửi có thể cung cấp `1.00`, còn phía nhận lưu cột database scale cố định bằng `2`, có thể giữ được scale này; nhưng không nên mặc nhiên tin rằng bất kỳ cột `DECIMAL/NUMERIC` nào cũng bảo tồn đúng scale từng payload. Phải kiểm thử driver, schema và cách truy vấn.

## 11. Quy tắc kiểm thử một giao thức decimal

Một vòng kiểm thử tối thiểu:

- **Giá trị lớn**: `9007199254740993`, `9007199254740993.01`; khẳng định không qua `double` và không sai chữ số.
- **Giá trị rất nhỏ**: `0.0000000000000000000001`; khẳng định không mất phần thập phân.
- **Scale khác nhau**: `1.0` và `1.00`; kiểm tra cả `compareTo` và `equals`.
- **Scale âm**: `1E+3` và `1E-7`; xác nhận `toString` và parse lại giữ scale.
- **Số âm và số 0**: `-12.3400`, `0`, `0.00`; kiểm tra quy tắc canonical.
- **Biên validation**: payload rỗng, quá dài, có dấu phân nhóm, exponent vượt giới hạn, giá trị ngoài miền.
- **Liên thông**: Java → JSON → JavaScript → JSON → Java; các consumer phải giữ trường `amount` dưới dạng string từ đầu đến cuối.

Với hợp đồng cần giữ scale, bất biến kiểm thử có thể viết:

~~~java
BigDecimal before = new BigDecimal("1E+3");
String wireValue = before.toString();
BigDecimal after = new BigDecimal(wireValue);

assert before.equals(after);
assert before.scale() == after.scale();
~~~

Khi không bật assertions (`-ea`), Java bỏ qua `assert`; trong test JUnit nên dùng assertion framework của bộ kiểm thử để tránh phụ thuộc cờ JVM. Đây là đoạn minh họa điều kiện round-trip, không phải một ứng dụng chạy độc lập.

### Bài tập ngắn

1. Một web client nhận `{"amount":9007199254740993}` bằng `JSON.parse`. Chỗ nào có nguy cơ mất thông tin?
2. Với `new BigDecimal("1E+3")`, bạn chọn `toString()` hay `toPlainString()` để sau khi parse vẫn có `scale = -3`?
3. Một service ghi `BigDecimal("123.4500")` thành JSON string `"123.4500"`, service khác parse qua `Double` trước khi tạo `BigDecimal`. Đường truyền đó có còn bảo đảm cả giá trị và scale không?
4. Nếu hợp đồng chỉ truyền minor units là `"12345"` và quy định `scale = 2`, giá trị tiền là bao nhiêu?
5. Nếu cần giữ nguyên nguyên văn `"-0.00"`, chỉ giữ đối tượng `BigDecimal` đã parse có đủ không?

**Đáp án:** (1) `JSON.parse` tạo `Number` từ token JSON number, có thể làm tròn số nguyên vượt giới hạn an toàn. (2) `toString()`; `toPlainString()` tạo `"1000"` và mất scale âm khi đọc lại. (3) Không: bước `Double` có thể đổi giá trị hoặc làm mất scale, dù kết quả tình cờ đúng ở một số đầu vào. (4) `123.45`. (5) Không: `BigDecimal` không lưu dấu âm của số 0 dưới dạng dấu độc lập; phải lưu chuỗi đầu vào khi cần bảo toàn nguyên văn.

**Kết luận kỹ thuật:** chọn wire format decimal chính xác ngay từ schema, sử dụng `String ↔ BigDecimal` (hoặc `unscaled + scale`), xác định rõ mức bảo toàn và tuyệt đối kiểm thử toàn tuyến có JavaScript/JSON parser. Một lần chuyển qua binary64 có thể biến lỗi mất chính xác thành vĩnh viễn.


</details>

- [Quay lại đầu trang](#back-to-top)
