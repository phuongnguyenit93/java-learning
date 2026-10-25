# Bản địa hóa ngày giờ theo Locale

Ngày giờ là nơi `Locale` và `ZoneId` rất dễ bị trộn lẫn. Một cái quyết định **quy ước trình bày**, cái kia quyết định **quan hệ giữa thời điểm tuyệt đối (instant) và giờ địa phương**.

**Bản địa hóa ngày giờ** nghĩa là biến một giá trị ngày/giờ thành cách viết mà người dùng của một Locale quen đọc — ví dụ thứ tự ngày/tháng/năm, tên tháng, tên ngày trong tuần và độ dài của phần hiển thị.

Một luồng xử lý ngày giờ đầy đủ có thể gồm:

```text
giá trị thời gian
→ LocalDate / LocalDateTime / Instant / ZonedDateTime...

ZoneId (nếu cần chuyển từ instant sang giờ địa phương)
→ quyết định ngày/giờ địa phương nào đang được nói tới

Locale
→ quyết định quy ước ngôn ngữ/vùng

DateTimeFormatter + kiểu/mẫu
→ tạo String cuối cùng
```

Vì vậy bản địa hóa ngày giờ **không thay đổi bản chất thời gian**; nó thay đổi cách thời gian đã được xác định được trình bày cho con người.

## <a id="localized-date-format">Các kiểu định dạng ngày giờ theo Locale</a>

`DateTimeFormatter` cung cấp các kiểu định dạng đã bản địa hóa:

```java
DateTimeFormatter formatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.forLanguageTag("vi-VN"));

String text = formatter.format(LocalDate.of(2026, 9, 27));
```

Các `FormatStyle` gồm:

```text
FULL
LONG
MEDIUM
SHORT
```

Ta cũng có:

```java
DateTimeFormatter.ofLocalizedTime(style);
DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle);
```

Ưu điểm của kiểu định dạng theo Locale là ứng dụng chỉ nói **mức chi tiết mong muốn**, còn dữ liệu Locale quyết định mẫu cụ thể. Điều này tốt hơn việc ghi cứng mẫu cho mọi Locale.

```text
ứng dụng
→ ngày ở mức MEDIUM

Locale vi-VN
→ mẫu phù hợp tiếng Việt

Locale en-US
→ mẫu phù hợp tiếng Anh Mỹ
```

Kết quả chính xác có thể thay đổi theo dữ liệu Locale của môi trường chạy; không nên viết kiểm thử phụ thuộc quá chặt vào chuỗi cụ thể nếu hợp đồng chỉ yêu cầu kiểu định dạng theo Locale chứ không yêu cầu một mẫu cố định.

## <a id="locale-vs-zone">Locale và ZoneId có trách nhiệm khác nhau</a>

Đây là ranh giới quan trọng nhất của chương:

```text
Instant
→ một điểm trên dòng thời gian

ZoneId
→ biến Instant thành ngày/giờ địa phương theo quy tắc múi giờ

Locale
→ quyết định cách trình bày ngày/giờ địa phương cho người đọc
```

Ví dụ cùng một thời điểm tuyệt đối:

```java
Instant instant = Instant.parse("2026-09-27T08:30:00Z");

ZoneId hcm = ZoneId.of("Asia/Ho_Chi_Minh");
ZoneId newYork = ZoneId.of("America/New_York");

ZonedDateTime vietnamTime = instant.atZone(hcm);
ZonedDateTime newYorkTime = instant.atZone(newYork);
```

Sau đó mới chọn Locale để định dạng:

```java
DateTimeFormatter viFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.forLanguageTag("vi-VN"));

String display = viFormatter.format(vietnamTime);
```

Ta hoàn toàn có thể định dạng `newYorkTime` bằng `vi-VN`; điều đó có nghĩa “giờ New York nhưng trình bày theo quy ước tiếng Việt”. Không có mâu thuẫn nào.

## <a id="locale-week-conventions">WeekFields và quy ước tuần theo Locale</a>

Locale không chỉ ảnh hưởng cách **viết** ngày. Một số quy ước lịch dành cho con người cũng có thể khác giữa các Locale, đặc biệt là khái niệm **tuần**.

Ví dụ hai câu hỏi tưởng đơn giản:

```text
Một tuần bắt đầu vào thứ mấy?
Tuần đầu tiên của năm phải có tối thiểu bao nhiêu ngày?
```

không có một câu trả lời duy nhất cho mọi văn hóa/quy ước vùng.

Java biểu diễn các quy ước này bằng `WeekFields`:

```java
WeekFields weekFields = WeekFields.of(locale);

DayOfWeek firstDay = weekFields.getFirstDayOfWeek();
int minimalDays = weekFields.getMinimalDaysInFirstWeek();
```

Các thành phần chính:

```text
ngày đầu tuần
→ ngày được xem là bắt đầu tuần

số ngày tối thiểu trong tuần đầu tiên
→ số ngày tối thiểu để một tuần được tính là tuần đầu của năm

trường dữ liệu dựa trên tuần
→ weekOfMonth / weekOfYear / weekOfWeekBasedYear...
```

Vai trò của `WeekFields` là cung cấp **quy ước lịch phụ thuộc Locale** cho các trường hợp trình bày/lịch.

Nhưng phải giữ ranh giới quan trọng:

```text
quy ước lịch phụ thuộc Locale
≠ quy tắc nghiệp vụ
```

Nếu nghiệp vụ nói “tuần kế toán luôn bắt đầu thứ Hai”, hãy biểu diễn quy tắc đó tường minh bằng `WeekFields.of(DayOfWeek.MONDAY, ...)` hoặc một chính sách riêng. Không để Locale của người dùng vô tình đổi logic nghiệp vụ.

## <a id="localized-numbering-calendar">DecimalStyle và phần mở rộng Unicode của Locale</a>

`Locale` có thể mang nhiều thông tin hơn ngôn ngữ + khu vực thông qua **phần mở rộng Unicode của Locale**. Một số API ngày giờ có thể dùng các phần mở rộng này để chọn hệ lịch, hệ chữ số, khu vực ghi đè hoặc múi giờ ghi đè cho phần trình bày.

Điều này **không thay đổi mô hình tư duy nền tảng**:

```text
Locale vẫn không phải ZoneId.
vi-VN không tự động đồng nghĩa Asia/Ho_Chi_Minh.
Múi giờ nghiệp vụ/lựa chọn của người dùng vẫn nên được mô hình hóa riêng.
```

Nhưng nó giải thích vì sao một số API bản địa hóa có thể đọc thêm lựa chọn từ Locale khi lập trình viên chủ động cung cấp một Locale có phần mở rộng.

Với định dạng ngày giờ, `DecimalStyle` mô tả các ký hiệu số dùng trong bộ định dạng:

```java
DecimalStyle style = DecimalStyle.of(locale);

char zeroDigit = style.getZeroDigit();
char positiveSign = style.getPositiveSign();
char negativeSign = style.getNegativeSign();
char decimalSeparator = style.getDecimalSeparator();
```

`DateTimeFormatter` có hai cách cần phân biệt:

```java
formatter.withLocale(locale);
formatter.localizedBy(locale);
```

Mô hình tư duy nhập môn:

```text
withLocale(locale)
→ đổi Locale dùng cho văn bản/mẫu đã bản địa hóa

localizedBy(locale)
→ áp dụng Locale rộng hơn, bao gồm các phần mở rộng Locale có liên quan
```

Ví dụ một Locale có lựa chọn về lịch hiển thị:

```java
Locale thaiBuddhist = Locale.forLanguageTag("th-TH-u-ca-buddhist");

DateTimeFormatter formatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.LONG)
        .localizedBy(thaiBuddhist);

String display = formatter.format(LocalDate.of(2026, 9, 27));
```

Ở đây `LocalDate` vẫn biểu diễn cùng ngày nghiệp vụ; lựa chọn `ca-buddhist` chỉ ảnh hưởng **hệ lịch (chronology/calendar) dùng cho phần trình bày** khi bộ định dạng hỗ trợ nó. Đừng lưu “năm hiển thị theo Phật lịch” như một giá trị thời gian mới chỉ vì UI cần cách viết khác.

Không nên nhồi phần mở rộng Unicode vào mọi yêu cầu nếu ứng dụng không có trường hợp sử dụng thật. Mục tiêu ở đây là hiểu **Locale có thể mang lựa chọn mở rộng và một số bộ định dạng biết đọc chúng**, chứ không phải biến Locale thành nơi chứa toàn bộ ngữ cảnh nghiệp vụ.

## <a id="localized-pattern">Lấy mẫu định dạng theo Locale</a>

Khi khung làm việc (framework) hoặc tầng báo cáo cần biết mẫu tương ứng với kiểu định dạng theo Locale, `DateTimeFormatterBuilder.getLocalizedDateTimePattern(...)` cho phép truy vấn mẫu:

```java
String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.MEDIUM,
        null,
        IsoChronology.INSTANCE,
        locale
);
```

Điều này hữu ích cho công cụ hoặc tích hợp cần mẫu cụ thể. Tuy nhiên mã nghiệp vụ thường nên dùng `ofLocalizedDate/Time/DateTime` trực tiếp thay vì lấy mẫu rồi ghi cứng/sao chép lại.

Nếu ứng dụng thật sự yêu cầu mẫu cố định theo hợp đồng, dùng `DateTimeFormatter.ofPattern(pattern, locale)`. Khi đó **ứng dụng sở hữu mẫu**, không còn hoàn toàn dựa vào kiểu định dạng theo Locale của JDK.

## <a id="localized-parsing">Phân tích ngày giờ theo Locale</a>

Việc phân tích dữ liệu người dùng nhập cũng cần Locale nếu văn bản có tên tháng/ngày hoặc mẫu phụ thuộc Locale:

```java
DateTimeFormatter formatter = DateTimeFormatter
        .ofPattern("d MMMM uuuu", Locale.forLanguageTag("vi-VN"));

LocalDate date = LocalDate.parse(input, formatter);
```

Nhưng hợp đồng ngày giờ giữa các hệ thống nên dùng định dạng chuẩn, rõ ràng như ISO thay vì văn bản hiển thị phụ thuộc Locale:

```java
Instant.parse("2026-09-27T08:30:00Z");
LocalDate.parse("2026-09-27");
```

Quy tắc:

```text
hiển thị/nhập liệu cho con người
→ bộ định dạng theo Locale khi phù hợp

hợp đồng dành cho máy / dữ liệu lưu trữ
→ định dạng ổn định và tường minh, thường là ISO
```

Khi phân tích văn bản theo Locale, ứng dụng cũng phải quyết định chính sách phân giải/kiểm tra dữ liệu và xử lý `DateTimeParseException`; đừng coi dữ liệu hiển thị là biểu diễn chuẩn của giá trị nghiệp vụ.

Chương kế tiếp chuyển sang cột mốc về **văn bản ngôn ngữ tự nhiên**: trước hết là cách `BreakIterator` tìm ranh giới ký tự/từ/câu/dòng, sau đó là cách `Bidi` phân tích văn bản trộn hướng LTR/RTL. Cơ chế dự phòng của `ResourceBundle` sẽ được tổng hợp ở cột mốc cuối cùng cùng các rủi ro của Locale mặc định.
