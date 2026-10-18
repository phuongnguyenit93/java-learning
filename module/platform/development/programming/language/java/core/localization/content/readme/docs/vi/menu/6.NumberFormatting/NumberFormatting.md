# Định dạng số

Cùng một giá trị `1234567.89`, người dùng ở các Locale khác nhau có thể mong đợi dấu phân cách và cách hiển thị khác nhau. Vì vậy **giá trị số** và **văn bản biểu diễn số** là hai thứ khác nhau.

## <a id="number-format">NumberFormat theo Locale</a>

`NumberFormat` cung cấp bộ định dạng theo Locale:

Nói rõ hơn, `NumberFormat` là **lớp trừu tượng chuyển qua lại giữa giá trị số và cách viết số dành cho con người** theo một Locale.

```text
định dạng
số → String theo Locale

phân tích
String theo Locale → Number
```

Nó tồn tại vì dấu phân cách, cách nhóm chữ số, dấu phần trăm, cách trình bày tiền tệ và một số quy ước chữ số không phải lúc nào cũng giống nhau giữa các Locale.

Các mảnh chính trong nhóm API này là:

```text
NumberFormat
→ lớp trừu tượng / điểm tạo chung

DecimalFormat
→ lớp triển khai cho phép điều khiển mẫu chi tiết

DecimalFormatSymbols
→ tập ký hiệu phụ thuộc Locale

Locale
→ ngữ cảnh chọn quy ước
```

```java
BigDecimal value = new BigDecimal("1234567.89");

NumberFormat vi = NumberFormat.getNumberInstance(
        Locale.forLanguageTag("vi-VN")
);
NumberFormat us = NumberFormat.getNumberInstance(Locale.US);

System.out.println(vi.format(value));
System.out.println(us.format(value));
```

Kết quả cụ thể phụ thuộc dữ liệu Locale của môi trường chạy, nhưng khác biệt điển hình là dấu nhóm hàng nghìn và dấu thập phân.

Ngoài bộ định dạng số tổng quát còn có:

```java
NumberFormat.getIntegerInstance(locale);
NumberFormat.getPercentInstance(locale);
NumberFormat.getCurrencyInstance(locale);
```

Với định dạng đơn giản kiểu `printf`/`String.format`, Java cũng có overload nhận `Locale`:

```java
String text = String.format(Locale.US, "%,.2f", 1234.5);
```

Nếu bỏ `Locale`, các API định dạng kiểu này có thể dựa vào Locale mặc định. Khi đầu ra dành cho người dùng và cần bản địa hóa rõ ràng, truyền Locale tường minh giúp mã dễ hiểu và dễ kiểm thử hơn.

Chọn phương thức tạo theo **ý nghĩa trình bày**, không phải chỉ vì kết quả nhìn gần giống mong muốn.

Ví dụ phần trăm:

```java
NumberFormat percent = NumberFormat.getPercentInstance(Locale.US);
System.out.println(percent.format(0.25)); // dạng hiển thị khoảng 25%
```

Giá trị nghiệp vụ vẫn giữ `0.25`; `%` thuộc cách trình bày.

## <a id="decimal-format">DecimalFormat, mẫu định dạng và ký hiệu</a>

Khi cần kiểm soát mẫu định dạng chi tiết hơn, bộ định dạng thực tế thường là `DecimalFormat`:

```java
DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
DecimalFormat format = new DecimalFormat("#,##0.00", symbols);

String text = format.format(new BigDecimal("1234.5"));
```

Mẫu mô tả cấu trúc định dạng:

```text
#  → chữ số tùy chọn
0  → chữ số bắt buộc
.  → dấu thập phân trong cú pháp mẫu
,  → dấu nhóm chữ số trong cú pháp mẫu
```

`DecimalFormatSymbols` quyết định ký hiệu thực tế của Locale như dấu thập phân, dấu nhóm chữ số, dấu phần trăm và các ký hiệu khác.

Không nên ghi cứng dấu `,` hoặc `.` vào logic nghiệp vụ để “sửa” String sau khi định dạng. Hãy cấu hình bộ định dạng/ký hiệu đúng tại ranh giới trình bày.

### Quy tắc làm tròn không phải là Locale

Bộ định dạng có thể làm tròn để phần hiển thị có số chữ số mong muốn:

```java
format.setMaximumFractionDigits(2);
format.setRoundingMode(RoundingMode.HALF_UP);
```

Nhưng quy tắc làm tròn khi tính tiền/thuế vẫn là **chính sách nghiệp vụ**. Locale chỉ quyết định cách biểu diễn; không được để lựa chọn Locale thay đổi giá trị nghiệp vụ phải thanh toán.

## <a id="compact-number-format">CompactNumberFormat và dạng số rút gọn</a>

Một UI đôi khi không muốn hiển thị đầy đủ `1,200,000`, mà muốn dạng ngắn dễ đọc như “1.2M” hoặc dạng dài tương đương theo Locale.

Java 21 hỗ trợ **định dạng số rút gọn (compact number formatting)** thông qua `NumberFormat.getCompactNumberInstance(...)`.

```java
NumberFormat shortFormat = NumberFormat.getCompactNumberInstance(
        Locale.US,
        NumberFormat.Style.SHORT
);

NumberFormat longFormat = NumberFormat.getCompactNumberInstance(
        Locale.US,
        NumberFormat.Style.LONG
);

System.out.println(shortFormat.format(1_200_000));
System.out.println(longFormat.format(1_200_000));
```

Mô hình tư duy:

```text
giá trị số
→ 1_200_000

Locale + kiểu rút gọn
→ SHORT / LONG

CompactNumberFormat
→ cách biểu diễn rút gọn cho người dùng
```

`SHORT` và `LONG` không có nghĩa ứng dụng tự nối `K`, `M`, `B`. Mẫu rút gọn đến từ dữ liệu Locale, vì cách viết số rút gọn có thể khác giữa các ngôn ngữ.

Đây vẫn chỉ là **cách trình bày**. Giá trị nghiệp vụ phải tiếp tục giữ `1_200_000`, không lưu chuỗi rút gọn như giá trị số chính.

Định dạng rút gọn phù hợp cho bảng điều khiển, thẻ thống kê hoặc UI có không gian hẹp. Với hóa đơn, kế toán hoặc nơi độ chính xác phải nhìn thấy rõ, dạng đầy đủ thường phù hợp hơn.

## <a id="parsing-numbers">Phân tích số theo Locale</a>

Nếu người dùng nhập số đã bản địa hóa, bộ phân tích phải dùng cùng quy ước:

```java
NumberFormat format = NumberFormat.getNumberInstance(
        Locale.forLanguageTag("vi-VN")
);

Number parsed = format.parse(input);
```

Một String có thể mang nghĩa khác nếu phân tích bằng Locale khác. Vì vậy máy chủ không nên phân tích dữ liệu người dùng bằng Locale mặc định không rõ nguồn gốc.

`NumberFormat.parse(String)` có thể phân tích một phần đầu hợp lệ rồi dừng trước phần còn lại. Khi việc kiểm tra dữ liệu yêu cầu **toàn bộ String phải hợp lệ**, dùng `ParsePosition` và kiểm tra vị trí cuối:

```java
ParsePosition pos = new ParsePosition(0);
Number number = format.parse(input, pos);

boolean valid = number != null
        && pos.getIndex() == input.length()
        && pos.getErrorIndex() < 0;
```

Nếu lớp triển khai thực tế là `DecimalFormat` và cần `BigDecimal` thay vì `Long`/`Double` theo hành vi phân tích mặc định:

```java
NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
if (numberFormat instanceof DecimalFormat decimal) {
    decimal.setParseBigDecimal(true);
}
```

`NumberFormat.getNumberInstance(...)` trả về kiểu trừu tượng `NumberFormat`; không nên giả định mọi provider đều bắt buộc trả `DecimalFormat`. Khi đúng là `DecimalFormat`, bật `setParseBigDecimal(true)` giúp tránh đưa sai số xấp xỉ của số dấu phẩy động vào dữ liệu nghiệp vụ chỉ vì bước phân tích từ UI.

## <a id="formatting-vs-domain-value">Định dạng không được làm thay đổi giá trị nghiệp vụ</a>

Quy tắc nền tảng:

```text
BigDecimal / giá trị số nghiệp vụ
        ↓
tính toán, kiểm tra dữ liệu, lưu trữ
        ↓
chỉ khi đi ra ranh giới trình bày
        ↓
NumberFormat(locale)
        ↓
String cho người dùng
```

Không nên làm ngược:

```java
String formatted = format.format(total);
// rồi lưu chuỗi đã định dạng vào DB như giá trị số chính
```

String đã định dạng có thể mất độ chính xác hiển thị, thay cách nhóm chữ số hoặc không còn phù hợp để hệ thống khác phân tích tự động.

Trao đổi dữ liệu giữa các hệ thống cần một hợp đồng định dạng riêng, thường độc lập với Locale. Chỉ phần trình bày cho con người mới dùng bộ định dạng phụ thuộc Locale.

Chương tiếp theo thêm một chiều rất dễ bị nhầm với Locale: **tiền tệ (`Currency`)**. Locale có thể gợi ý cách hiển thị và loại tiền thường dùng tại một khu vực, nhưng tiền tệ của giao dịch phải là dữ liệu nghiệp vụ riêng.
