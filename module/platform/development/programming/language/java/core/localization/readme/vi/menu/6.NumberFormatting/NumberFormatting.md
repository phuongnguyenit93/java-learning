# Định dạng số

Cùng một giá trị `1234567.89`, người dùng ở các locale khác nhau có thể mong đợi separator và cách hiển thị khác nhau. Vì vậy **numeric value** và **numeric text** là hai thứ khác nhau.

## <a id="number-format">NumberFormat theo Locale</a>

`NumberFormat` cung cấp formatter theo locale:

Nói rõ hơn, `NumberFormat` là **abstraction chuyển qua lại giữa numeric value và cách viết số dành cho con người** theo một locale.

```text
format
number → localized String

parse
localized String → Number
```

Nó tồn tại vì separator, grouping, percent sign, currency presentation và một số quy ước chữ số không phải lúc nào cũng giống nhau giữa các locale.

Các mảnh chính trong nhóm API này là:

```text
NumberFormat
→ abstraction/factory chung

DecimalFormat
→ implementation cho phép điều khiển pattern chi tiết

DecimalFormatSymbols
→ tập ký hiệu phụ thuộc locale

Locale
→ context chọn convention
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

Kết quả cụ thể phụ thuộc dữ liệu locale của môi trường chạy, nhưng khác biệt điển hình là dấu nhóm hàng nghìn và dấu thập phân.

Ngoài number formatter tổng quát còn có:

```java
NumberFormat.getIntegerInstance(locale);
NumberFormat.getPercentInstance(locale);
NumberFormat.getCurrencyInstance(locale);
```

Với formatting đơn giản kiểu `printf`/`String.format`, Java cũng có overload nhận `Locale`:

```java
String text = String.format(Locale.US, "%,.2f", 1234.5);
```

Nếu bỏ `Locale`, các API formatting kiểu này có thể dựa vào locale mặc định. Khi output là user-facing và cần localization rõ ràng, truyền locale tường minh giúp code dễ hiểu và dễ test hơn.

Chọn factory theo **ý nghĩa trình bày**, không phải chỉ vì kết quả nhìn gần giống mong muốn.

Ví dụ phần trăm:

```java
NumberFormat percent = NumberFormat.getPercentInstance(Locale.US);
System.out.println(percent.format(0.25)); // dạng hiển thị khoảng 25%
```

Giá trị nghiệp vụ vẫn giữ `0.25`; `%` thuộc cách trình bày.

## <a id="decimal-format">DecimalFormat, pattern và symbols</a>

Khi cần kiểm soát pattern chi tiết hơn, formatter thực tế thường là `DecimalFormat`:

```java
DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
DecimalFormat format = new DecimalFormat("#,##0.00", symbols);

String text = format.format(new BigDecimal("1234.5"));
```

Pattern mô tả cấu trúc format:

```text
#  → digit tùy chọn
0  → digit bắt buộc
.  → decimal separator trong cú pháp pattern
,  → grouping separator trong cú pháp pattern
```

`DecimalFormatSymbols` quyết định ký hiệu thực tế của locale như decimal separator, grouping separator, percent sign và các symbol khác.

Không nên ghi cứng dấu `,` hoặc `.` vào logic nghiệp vụ để “sửa” string sau khi format. Hãy cấu hình formatter/symbols đúng tại ranh giới trình bày.

### Quy tắc làm tròn không phải là locale

Formatter có thể làm tròn để phần hiển thị có số chữ số mong muốn:

```java
format.setMaximumFractionDigits(2);
format.setRoundingMode(RoundingMode.HALF_UP);
```

Nhưng quy tắc làm tròn khi tính tiền/thuế vẫn là **chính sách nghiệp vụ**. Locale chỉ quyết định cách biểu diễn; không được để lựa chọn locale thay đổi giá trị nghiệp vụ phải thanh toán.

## <a id="compact-number-format">CompactNumberFormat và dạng số rút gọn</a>

Một UI đôi khi không muốn hiển thị đầy đủ `1,200,000`, mà muốn dạng ngắn dễ đọc như “1.2M” hoặc dạng dài tương đương theo locale.

Java 21 hỗ trợ **compact number formatting** thông qua `NumberFormat.getCompactNumberInstance(...)`.

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

Mental model:

```text
numeric value
→ 1_200_000

Locale + compact Style
→ SHORT / LONG

CompactNumberFormat
→ user-facing compact representation
```

`SHORT` và `LONG` không có nghĩa application tự nối `K`, `M`, `B`. Pattern rút gọn đến từ locale data, vì cách viết compact number có thể khác giữa các ngôn ngữ.

Đây vẫn chỉ là **presentation**. Domain value phải tiếp tục giữ `1_200_000`, không lưu chuỗi compact như giá trị số chính.

Compact format phù hợp cho dashboard, statistic card hoặc UI có không gian hẹp. Với hóa đơn, kế toán hoặc nơi precision phải nhìn thấy rõ, dạng đầy đủ thường phù hợp hơn.

## <a id="parsing-numbers">Parse số theo Locale</a>

Nếu người dùng nhập số localized, parser phải dùng cùng convention:

```java
NumberFormat format = NumberFormat.getNumberInstance(
        Locale.forLanguageTag("vi-VN")
);

Number parsed = format.parse(input);
```

Một string có thể mang nghĩa khác nếu parse bằng locale khác. Vì vậy máy chủ không nên parse dữ liệu người dùng bằng locale mặc định không rõ nguồn gốc.

`NumberFormat.parse(String)` có thể parse một phần đầu hợp lệ rồi dừng trước phần còn lại. Khi validation yêu cầu **toàn bộ string phải hợp lệ**, dùng `ParsePosition` và kiểm tra vị trí cuối:

```java
ParsePosition pos = new ParsePosition(0);
Number number = format.parse(input, pos);

boolean valid = number != null
        && pos.getIndex() == input.length()
        && pos.getErrorIndex() < 0;
```

Với `DecimalFormat`, nếu cần `BigDecimal` thay vì `Long`/`Double` theo hành vi parse mặc định:

```java
DecimalFormat decimal = (DecimalFormat) NumberFormat.getNumberInstance(locale);
decimal.setParseBigDecimal(true);
```

Điều này tránh đưa sai số xấp xỉ của số dấu phẩy động vào dữ liệu nghiệp vụ chỉ vì bước parse từ UI.

## <a id="formatting-vs-domain-value">Formatting không được thay đổi giá trị nghiệp vụ</a>

Quy tắc nền tảng:

```text
BigDecimal / numeric domain value
        ↓
calculation, validation, persistence
        ↓
chỉ khi đi ra presentation boundary
        ↓
NumberFormat(locale)
        ↓
String cho người dùng
```

Không nên làm ngược:

```java
String formatted = format.format(total);
// rồi lưu formatted vào DB như giá trị số chính
```

String đã format có thể mất độ chính xác hiển thị, thay cách nhóm chữ số hoặc không còn phù hợp để hệ thống khác parse tự động.

Trao đổi dữ liệu giữa các hệ thống cần một hợp đồng định dạng riêng, thường độc lập với locale. Chỉ phần trình bày cho con người mới dùng formatter phụ thuộc locale.

Chương tiếp theo thêm một chiều rất dễ bị nhầm với locale: **tiền tệ (`Currency`)**. Locale có thể gợi ý cách hiển thị và loại tiền thường dùng tại một region, nhưng currency của giao dịch phải là dữ liệu nghiệp vụ riêng.
