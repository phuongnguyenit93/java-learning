# Mô hình tư duy về bản địa hóa

## <a id="i18n-vs-l10n">Quốc tế hóa (i18n) và bản địa hóa (l10n) là gì?</a>

Trước khi nhìn vào API hay mã nguồn, cần tách hai khái niệm thường được dùng cùng nhau:

- **quốc tế hóa (internationalization/i18n)** là việc thiết kế phần mềm để nó **có khả năng thích nghi** với nhiều ngôn ngữ, vùng và quy ước trình bày mà không phải viết lại logic nghiệp vụ;
- **bản địa hóa (localization/l10n)** là việc **chọn và cung cấp nội dung/quy ước cụ thể** cho một Locale, ví dụ bản dịch tiếng Việt, định dạng số Việt Nam hoặc cách sắp xếp chữ theo một ngôn ngữ.

Hai tên viết tắt xuất phát từ số ký tự nằm giữa chữ đầu và chữ cuối:

```text
internationalization
i + 18 ký tự + n
→ i18n

localization
l + 10 ký tự + n
→ l10n
```

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

Ví dụ này cho thấy dữ liệu nghiệp vụ có thể giữ nguyên trong khi cách con người nhìn thấy dữ liệu thay đổi theo Locale.

### Vấn đề nếu phần mềm không được chuẩn bị cho nhiều ngôn ngữ và vùng

Một ứng dụng nhỏ có thể bắt đầu bằng đoạn mã như:

```java
String message = "Order created";
String totalText = "$" + order.total();
```

Sau đó khi cần hỗ trợ tiếng Việt, lập trình viên có thể thêm:

```java
if (language.equals("vi")) {
    message = "Đơn hàng đã được tạo";
} else {
    message = "Order created";
}
```

Nếu tiếp tục theo hướng này, các vấn đề nhanh chóng xuất hiện:

```text
thông điệp nằm rải rác trong mã nguồn
        ↓
thêm một ngôn ngữ phải sửa nhiều nhánh if/else
        ↓
người dịch phải sửa trực tiếp mã nguồn
        ↓
số/ngày giờ/tiền tệ vẫn được định dạng theo các giả định riêng
        ↓
logic nghiệp vụ và logic trình bày bị trộn vào nhau
```

Quốc tế hóa giải quyết vấn đề ở tầng thiết kế: **tách giá trị nghiệp vụ khỏi cách biểu diễn dành cho con người**, rồi truyền Locale vào đúng ranh giới cần trình bày dữ liệu.

Bản địa hóa sau đó cung cấp phần cụ thể: tài nguyên tiếng Việt, tài nguyên tiếng Anh, quy tắc định dạng số, ngày giờ, tiền tệ, thứ tự chữ, v.v.

Một điểm rất dễ hiểu nhầm:

```text
dịch thuật
→ dịch nội dung ngôn ngữ

bản địa hóa
→ dịch thuật
  + định dạng số/ngày giờ/tiền tệ
  + so sánh và sắp xếp văn bản theo ngôn ngữ
  + các quy ước trình bày khác của Locale
```

Vì vậy một ứng dụng đã dịch toàn bộ nhãn sang tiếng Việt **chưa chắc đã được bản địa hóa đúng** nếu nó vẫn hiển thị số, ngày hoặc tiền theo quy ước sai.

### Một hệ thống bản địa hóa thực tế gồm những mảnh nào?

Người mới thường nghe nhiều tên lớp/API riêng lẻ rồi không biết chúng ghép với nhau ra sao. Có thể nhìn toàn bộ mô-đun như một luồng xử lý gồm các mảnh sau:

```text
1. Locale
→ mô tả ngôn ngữ / hệ chữ / khu vực dùng cho cách trình bày

2. Thẻ ngôn ngữ (language tag)
→ dạng văn bản để mang thông tin Locale qua HTTP/cấu hình/cơ sở dữ liệu

3. Tài nguyên bản địa hóa
→ các thông điệp/tài nguyên khác nhau theo Locale

4. ResourceBundle
→ chọn đúng tài nguyên theo Locale và cơ chế dự phòng

5. MessageFormat
→ đưa dữ liệu động vào một câu đã dịch

6. NumberFormat / DecimalFormat / Currency
→ trình bày và phân tích số, phần trăm, tiền

7. Collator
→ so sánh/sắp xếp văn bản theo quy tắc ngôn ngữ

8. DateTimeFormatter + ZoneId
→ trình bày/phân tích ngày giờ; Locale và múi giờ giữ trách nhiệm riêng

9. BreakIterator
→ tìm ranh giới ký tự/từ/câu/dòng trong văn bản ngôn ngữ tự nhiên

10. Bidi
→ phân tích thứ tự logic và các đoạn có hướng viết LTR/RTL
```

Không phải ứng dụng nào cũng cần dùng hết các mảnh trên. Ví dụ một API thuần máy-với-máy có thể gần như không cần bản địa hóa; một website đa ngôn ngữ lại có thể dùng gần như toàn bộ luồng xử lý này.

## <a id="locale-sensitive-data">Dữ liệu nghiệp vụ và cách trình bày phụ thuộc Locale</a>

Đây là mô hình tư duy quan trọng nhất của mô-đun.

Không phải mọi dữ liệu đều nên thay đổi theo `Locale`.

| Loại thông tin | Giá trị chuẩn/nghiệp vụ | Cách trình bày theo Locale |
| --- | --- | --- |
| số tiền | `BigDecimal("1234567.89")` | `1.234.567,89` hoặc `1,234,567.89` |
| mã tiền tệ | `Currency.getInstance("USD")` | `$`, `US$`, tên hiển thị tùy ngữ cảnh |
| thời điểm | `Instant` | ngày/giờ theo Locale **và** múi giờ được chọn |
| trạng thái | enum/mã như `PAID` | `Đã thanh toán` / `Paid` |
| khóa thông điệp | `order.created` | văn bản bản dịch |
| chuỗi dùng cho giao thức | khóa, header, định danh | thường phải không phụ thuộc Locale |

Ví dụ đúng hướng:

```java
record Order(BigDecimal total, Currency currency, Instant createdAt) {}
```

Tầng trình bày nhận thêm ngữ cảnh:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
```

Rồi mới chuyển giá trị chuẩn thành văn bản cho người dùng.

Một lỗi thiết kế thường gặp là lưu luôn chuỗi đã định dạng:

```java
record Order(String total) {}

// "1.234.567,89 ₫"
```

Khi đó ứng dụng rất khó tính toán lại, đổi Locale, đổi tiền tệ hoặc tuần tự hóa theo định dạng dành cho máy. Giá trị dùng để hiển thị đã làm mất ranh giới với dữ liệu nghiệp vụ.

Mô hình tư duy nên là:

```text
giá trị nghiệp vụ
    ↓
giữ dạng biểu diễn ổn định, có nghĩa nghiệp vụ
    ↓
ranh giới trình bày
    + Locale
    + ZoneId khi thời gian cần múi giờ
    + Currency khi tiền tệ cần đơn vị
    ↓
văn bản dành cho người dùng
```

`Locale` **không phải** `ZoneId` và cũng **không phải** `Currency`. Locale cung cấp ngữ cảnh về ngôn ngữ/vùng/quy ước trình bày; các khái niệm còn lại có trách nhiệm riêng.

## <a id="localization-boundaries">Bản địa hóa nằm ở đâu trong ứng dụng?</a>

Bản địa hóa chủ yếu thuộc **ranh giới giao tiếp với con người**.

Ví dụ phù hợp:

```text
UI / phản hồi Web cho người dùng
→ thông điệp đã dịch
→ số, phần trăm, tiền tệ đã định dạng
→ ngày giờ đã định dạng

email / báo cáo / hóa đơn
→ văn bản + số/ngày giờ/tiền tệ theo Locale người nhận

sắp xếp danh sách tên cho người dùng
→ Collator theo ngôn ngữ
```

Ngược lại, dữ liệu phục vụ máy thường cần định dạng ổn định, không phụ thuộc môi trường:

```text
giá trị số trong cơ sở dữ liệu
hợp đồng API dành cho máy
khóa bộ nhớ đệm
định danh giao thức
trường log dùng để phân tích tự động
```

Ví dụ không nên dùng Locale mặc định để tạo định danh:

```java
String key = input.toLowerCase(); // phụ thuộc Locale mặc định
```

Nếu dữ liệu thật sự cần trung lập với Locale, cần biểu đạt ý định rõ:

```java
String key = input.toLowerCase(Locale.ROOT);
```

### Lộ trình của mô-đun theo ROADMAP

Sau chương này, hãy nhìn 13 chương chi tiết như phần triển khai của 9 cột mốc trong ROADMAP:

```text
1. Mô hình tư duy về bản địa hóa
→ hiểu i18n/l10n và tách dữ liệu nghiệp vụ khỏi cách trình bày
        ↓
2. Locale và thẻ ngôn ngữ
→ biểu diễn ngữ cảnh ngôn ngữ, trao đổi BCP 47 và chọn Locale được hỗ trợ
        ↓
3. ResourceBundle và tài nguyên bản địa hóa
→ tổ chức và tìm đúng tài nguyên theo Locale
        ↓
4. Thông điệp bản địa hóa có tham số
→ dùng MessageFormat để giữ cấu trúc câu trong tài nguyên dịch
        ↓
5. Số và tiền tệ theo Locale
→ định dạng/phân tích số, đồng thời giữ Currency tách khỏi Locale
        ↓
6. So sánh và sắp xếp văn bản theo Locale
→ dùng Collator khi thứ tự dành cho người đọc quan trọng
        ↓
7. Bản địa hóa ngày giờ theo Locale
→ định dạng/phân tích và các quy ước lịch, nhưng không trộn Locale với ZoneId
        ↓
8. Phân tách văn bản và văn bản hai chiều
→ dùng BreakIterator cho ranh giới văn bản và Bidi cho thứ tự logic/hướng viết
        ↓
9. Cơ chế dự phòng, Locale mặc định, các lỗi thường gặp và tổng hợp
→ hiểu cơ chế dự phòng/Locale mặc định và giữ dữ liệu dành cho máy độc lập với bản địa hóa
```

Mục tiêu của mô-đun không phải là “học thuộc các lớp trong `java.util` và `java.text`”. Mục tiêu là biết **giá trị nào phải giữ độc lập với Locale, ở ranh giới nào mới áp dụng bản địa hóa, và chọn đúng API Java để tạo cách trình bày đó**.
