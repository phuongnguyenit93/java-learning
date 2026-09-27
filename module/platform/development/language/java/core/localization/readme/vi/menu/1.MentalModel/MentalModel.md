# Mô hình Localization và Internationalization

Một ứng dụng chạy đúng về mặt nghiệp vụ chưa chắc đã **hiển thị đúng cho người dùng ở các quốc gia và ngôn ngữ khác nhau**.

Giả sử hệ thống có một đơn hàng với dữ liệu nghiệp vụ như sau:

```java
record Order(long id, BigDecimal total, Instant createdAt) {}

Order order = new Order(
        1001L,
        new BigDecimal("1234567.89"),
        Instant.parse("2026-09-27T08:30:00Z")
);
```

`1234567.89` là giá trị số của đơn hàng. `Instant` là thời điểm tuyệt đối đơn hàng được tạo. Những giá trị này không nên thay đổi chỉ vì người dùng chọn tiếng Việt hay tiếng Anh.

Nhưng cách **trình bày** có thể thay đổi:

```text
vi-VN
→ 1.234.567,89
→ 27/09/2026
→ "Đơn hàng đã được tạo"

en-US
→ 1,234,567.89
→ 9/27/26
→ "Order created"
```

Đó là nơi internationalization và localization xuất hiện.

## <a id="i18n-vs-l10n">Internationalization và Localization là gì?</a>

Trước hết cần tách hai khái niệm thường được dùng cùng nhau:

- **internationalization (i18n)** là việc thiết kế phần mềm để nó **có khả năng thích nghi** với nhiều ngôn ngữ, vùng và quy ước trình bày mà không phải viết lại logic nghiệp vụ;
- **localization (l10n)** là việc **chọn và cung cấp nội dung/quy ước cụ thể** cho một locale, ví dụ bản dịch tiếng Việt, định dạng số Việt Nam hoặc cách sắp xếp chữ theo một ngôn ngữ.

Hai tên viết tắt xuất phát từ số ký tự nằm giữa chữ đầu và chữ cuối:

```text
internationalization
i + 18 ký tự + n
→ i18n

localization
l + 10 ký tự + n
→ l10n
```

### Vấn đề nếu không internationalize từ đầu

Một ứng dụng nhỏ có thể bắt đầu bằng đoạn mã như:

```java
String message = "Order created";
String totalText = "$" + order.total();
```

Sau đó cần tiếng Việt, developer có thể thêm:

```java
if (language.equals("vi")) {
    message = "Đơn hàng đã được tạo";
} else {
    message = "Order created";
}
```

Nếu tiếp tục theo hướng này, các vấn đề nhanh chóng xuất hiện:

```text
message nằm rải rác trong source code
        ↓
thêm một ngôn ngữ phải sửa nhiều nhánh if/else
        ↓
translator phải đụng vào source code
        ↓
number/date/currency vẫn đang format theo giả định riêng
        ↓
logic nghiệp vụ và logic trình bày bị trộn vào nhau
```

Internationalization giải quyết vấn đề ở tầng thiết kế: **tách giá trị nghiệp vụ khỏi cách biểu diễn dành cho con người**, rồi truyền locale vào đúng ranh giới cần trình bày dữ liệu.

Localization sau đó cung cấp phần cụ thể: resource tiếng Việt, resource tiếng Anh, quy tắc format số, ngày, tiền tệ, thứ tự chữ, v.v.

Một điểm rất dễ hiểu nhầm:

```text
translation
→ dịch nội dung ngôn ngữ

localization
→ translation
  + number/date/currency formatting
  + text ordering/collation
  + các convention trình bày khác của locale
```

Vì vậy một ứng dụng đã dịch toàn bộ label sang tiếng Việt **chưa chắc đã localization đúng** nếu nó vẫn hiển thị số, ngày hoặc tiền theo convention sai.

### Một hệ thống localization thực tế gồm những mảnh nào?

Người mới thường nghe nhiều tên class riêng lẻ rồi không biết chúng ghép với nhau ra sao. Có thể nhìn toàn bộ module như một pipeline gồm các mảnh sau:

```text
1. Locale
→ mô tả ngôn ngữ / script / region dùng cho presentation

2. Language tag
→ dạng text để mang thông tin Locale qua HTTP/config/database

3. Localized resources
→ các message/resource khác nhau theo locale

4. ResourceBundle
→ chọn đúng resource theo Locale và fallback

5. MessageFormat
→ đưa dữ liệu động vào một câu đã dịch

6. NumberFormat / DecimalFormat / Currency
→ trình bày số, phần trăm, tiền

7. DateTimeFormatter + ZoneId
→ trình bày ngày giờ; Locale và múi giờ giữ trách nhiệm riêng

8. Collator
→ so sánh/sắp xếp text theo quy tắc ngôn ngữ

9. BreakIterator
→ tìm ranh giới character/word/sentence/line trong natural-language text

10. Bidi
→ phân tích logical order và visual direction của text LTR/RTL
```

Không phải ứng dụng nào cũng cần dùng hết tám mảnh. Ví dụ một API thuần machine-to-machine có thể gần như không cần localization; một website đa ngôn ngữ lại có thể dùng gần như toàn bộ pipeline trên.

## <a id="locale-sensitive-data">Dữ liệu nghiệp vụ và dữ liệu phụ thuộc locale</a>

Đây là mental model quan trọng nhất của module.

Không phải mọi dữ liệu đều nên thay đổi theo `Locale`.

| Loại thông tin | Giá trị chuẩn/nghiệp vụ | Cách trình bày theo locale |
| --- | --- | --- |
| số tiền | `BigDecimal("1234567.89")` | `1.234.567,89` hoặc `1,234,567.89` |
| mã tiền tệ | `Currency.getInstance("USD")` | `$`, `US$`, tên hiển thị tùy ngữ cảnh |
| thời điểm | `Instant` | ngày/giờ theo locale **và** zone được chọn |
| trạng thái | enum/code như `PAID` | `Đã thanh toán` / `Paid` |
| message key | `order.created` | text bản dịch |
| chuỗi dùng cho giao thức | key, header, identifier | thường phải locale-neutral |

Ví dụ đúng hướng:

```java
record Order(BigDecimal total, Currency currency, Instant createdAt) {}
```

Tầng trình bày nhận thêm ngữ cảnh:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
```

Rồi mới chuyển canonical value thành text cho người dùng.

Một lỗi thiết kế thường gặp là lưu luôn chuỗi đã format:

```java
record Order(String total) {}

// "1.234.567,89 ₫"
```

Khi đó ứng dụng rất khó tính toán lại, đổi locale, đổi tiền tệ hoặc tuần tự hóa theo định dạng dành cho máy. Giá trị dùng để hiển thị đã làm mất ranh giới với dữ liệu nghiệp vụ.

Mental model nên là:

```text
domain value
    ↓
giữ representation ổn định, có nghĩa nghiệp vụ
    ↓
presentation boundary
    + Locale
    + ZoneId khi thời gian cần múi giờ
    + Currency khi tiền tệ cần đơn vị
    ↓
text dành cho người dùng
```

`Locale` **không phải** `ZoneId` và cũng **không phải** `Currency`. Locale cung cấp ngữ cảnh về ngôn ngữ/vùng/quy ước trình bày; các khái niệm còn lại có trách nhiệm riêng.

## <a id="localization-boundaries">Localization nằm ở đâu trong ứng dụng?</a>

Localization chủ yếu thuộc **ranh giới giao tiếp với con người**.

Ví dụ phù hợp:

```text
UI / Web response cho người dùng
→ message đã dịch
→ số, phần trăm, tiền tệ đã format
→ ngày giờ đã format

email / report / invoice
→ text + number/date/currency theo locale người nhận

sorting danh sách tên cho người dùng
→ Collator theo ngôn ngữ
```

Ngược lại, dữ liệu phục vụ máy thường cần format ổn định, không phụ thuộc môi trường:

```text
database numeric value
API machine contract
cache key
protocol identifier
log field dùng để parse tự động
```

Ví dụ không nên dùng locale mặc định để tạo identifier:

```java
String key = input.toLowerCase(); // phụ thuộc default Locale
```

Nếu dữ liệu thật sự locale-neutral, cần biểu đạt ý định rõ:

```java
String key = input.toLowerCase(Locale.ROOT);
```

### Bản đồ các khái niệm trong module

Sau chương này, các phần còn lại nối tiếp nhau như sau:

```text
Tách domain khỏi presentation
        ↓
Locale
→ context ngôn ngữ / script / region
        ↓
Language Tag
→ cách biểu diễn locale giữa các hệ thống
        ↓
ResourceBundle
→ lấy text/resource đúng locale
        ↓
MessageFormat
→ chèn tham số vào message mà vẫn tôn trọng cấu trúc bản dịch
        ↓
NumberFormat / DecimalFormat
→ số và phần trăm
        ↓
Currency
→ đơn vị tiền tệ + cách hiển thị
        ↓
Collator
→ so sánh/sắp xếp text cho người dùng
        ↓
DateTimeFormatter + Locale
→ localized date/time presentation
        ↓
ResourceBundle fallback
→ xử lý khi resource cụ thể không tồn tại
        ↓
Pitfalls
→ default locale, case conversion, parse/format và message key
        ↓
BreakIterator
→ text boundary cho character / word / sentence / line
        ↓
Bidi
→ logical order vs visual order với LTR / RTL text
```

Mục tiêu của module không phải là “học thuộc các class trong `java.util` và `java.text`”. Mục tiêu là biết **giá trị nào phải giữ độc lập với locale, ở ranh giới nào mới áp dụng localization, và chọn đúng API Java để tạo cách trình bày đó**.
