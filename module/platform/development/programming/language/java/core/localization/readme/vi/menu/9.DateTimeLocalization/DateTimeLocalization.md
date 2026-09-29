# Localization cho Date-Time

Date-time là nơi `Locale` và `ZoneId` rất dễ bị trộn lẫn. Một cái quyết định **quy ước hiển thị**, cái kia quyết định **quan hệ giữa instant và giờ địa phương**.

**Date-time localization** nghĩa là biến một giá trị ngày/giờ thành cách viết mà người dùng của một locale quen đọc — ví dụ thứ tự ngày/tháng/năm, tên tháng, tên ngày trong tuần và độ dài của phần hiển thị.

Một pipeline date-time đầy đủ có thể gồm:

```text
temporal value
→ LocalDate / LocalDateTime / Instant / ZonedDateTime...

ZoneId (nếu cần chuyển từ instant sang local time)
→ quyết định local date/time nào đang được nói tới

Locale
→ quyết định convention ngôn ngữ/vùng

DateTimeFormatter + style/pattern
→ tạo String cuối cùng
```

Vì vậy localization của ngày giờ **không thay đổi bản chất thời gian**; nó thay đổi cách thời gian đã được xác định được trình bày cho con người.

## <a id="localized-date-format">Localized date/time styles</a>

`DateTimeFormatter` cung cấp các style localized:

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

Ưu điểm của localized style là ứng dụng chỉ nói **mức chi tiết mong muốn**, còn dữ liệu locale quyết định pattern cụ thể. Điều này tốt hơn việc ghi cứng pattern cho mọi locale.

```text
application
→ MEDIUM date

Locale vi-VN
→ pattern phù hợp tiếng Việt

Locale en-US
→ pattern phù hợp US English
```

Kết quả chính xác có thể thay đổi theo dữ liệu locale của môi trường chạy; không nên viết test phụ thuộc quá chặt vào chuỗi cụ thể nếu hợp đồng chỉ yêu cầu localized style chứ không yêu cầu một pattern cố định.

## <a id="locale-vs-zone">Locale và ZoneId có trách nhiệm khác nhau</a>

Đây là ranh giới quan trọng nhất của chapter:

```text
Instant
→ một điểm trên timeline

ZoneId
→ biến Instant thành local date/time theo rule múi giờ

Locale
→ quyết định cách trình bày local date/time cho người đọc
```

Ví dụ cùng một instant:

```java
Instant instant = Instant.parse("2026-09-27T08:30:00Z");

ZoneId hcm = ZoneId.of("Asia/Ho_Chi_Minh");
ZoneId newYork = ZoneId.of("America/New_York");

ZonedDateTime vietnamTime = instant.atZone(hcm);
ZonedDateTime newYorkTime = instant.atZone(newYork);
```

Sau đó mới chọn locale để format:

```java
DateTimeFormatter viFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.forLanguageTag("vi-VN"));

String display = viFormatter.format(vietnamTime);
```

Ta hoàn toàn có thể format `newYorkTime` bằng `vi-VN`; điều đó có nghĩa “giờ New York nhưng trình bày theo convention tiếng Việt”. Không có mâu thuẫn nào.

## <a id="locale-week-conventions">WeekFields và quy ước tuần theo Locale</a>

Locale không chỉ ảnh hưởng cách **viết** ngày. Một số quy ước lịch dành cho con người cũng có thể khác giữa các locale, đặc biệt là khái niệm **tuần**.

Ví dụ hai câu hỏi tưởng đơn giản:

```text
Một tuần bắt đầu vào thứ mấy?
Tuần đầu tiên của năm phải có tối thiểu bao nhiêu ngày?
```

không có một câu trả lời duy nhất cho mọi culture.

Java biểu diễn các quy ước này bằng `WeekFields`:

```java
WeekFields weekFields = WeekFields.of(locale);

DayOfWeek firstDay = weekFields.getFirstDayOfWeek();
int minimalDays = weekFields.getMinimalDaysInFirstWeek();
```

Các thành phần chính:

```text
first day of week
→ ngày được xem là bắt đầu tuần

minimal days in first week
→ số ngày tối thiểu để một tuần được tính là tuần đầu của năm

week-based fields
→ weekOfMonth / weekOfYear / weekOfWeekBasedYear...
```

Vai trò của `WeekFields` là cung cấp **calendar convention phụ thuộc locale** cho các use case trình bày/lịch.

Nhưng phải giữ boundary quan trọng:

```text
Locale-sensitive calendar convention
≠ business rule
```

Nếu nghiệp vụ nói “tuần kế toán luôn bắt đầu thứ Hai”, hãy encode rule đó tường minh bằng `WeekFields.of(DayOfWeek.MONDAY, ...)` hoặc một policy riêng. Không để locale của người dùng vô tình đổi logic nghiệp vụ.

## <a id="localized-numbering-calendar">DecimalStyle và Unicode locale extensions</a>

`Locale` có thể mang nhiều thông tin hơn language + region thông qua **Unicode locale extensions**. Một số API date-time có thể dùng các extension này để chọn calendar system, numbering system, region override hoặc time-zone override cho presentation.

Điều này **không thay đổi mental model nền tảng**:

```text
Locale vẫn không phải ZoneId.
vi-VN không tự động đồng nghĩa Asia/Ho_Chi_Minh.
Time-zone nghiệp vụ/user preference vẫn nên được model riêng.
```

Nhưng nó giải thích vì sao một số API localization có thể đọc thêm preference từ locale khi developer chủ động cung cấp một locale có extension.

Với date-time formatting, `DecimalStyle` mô tả các ký hiệu số dùng trong formatter:

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

Mental model beginner:

```text
withLocale(locale)
→ đổi locale dùng cho localized text/pattern behavior

localizedBy(locale)
→ áp dụng locale rộng hơn, bao gồm các relevant locale extensions
```

Không nên nhồi Unicode extension vào mọi request nếu application không có use case thật. Mục tiêu ở đây là hiểu **Locale có thể mang preference mở rộng và một số formatter biết đọc chúng**, chứ không phải biến Locale thành nơi chứa toàn bộ business context.

## <a id="localized-pattern">Lấy localized pattern</a>

Khi framework/reporting layer cần biết pattern mà localized style tương ứng, `DateTimeFormatterBuilder.getLocalizedDateTimePattern(...)` cho phép truy vấn pattern:

```java
String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.MEDIUM,
        null,
        IsoChronology.INSTANCE,
        locale
);
```

Điều này hữu ích cho tooling hoặc integration cần pattern cụ thể. Tuy nhiên business code thường nên dùng `ofLocalizedDate/Time/DateTime` trực tiếp thay vì lấy pattern rồi hard-code/copy lại.

Nếu ứng dụng thật sự yêu cầu pattern cố định theo hợp đồng, dùng `DateTimeFormatter.ofPattern(pattern, locale)`. Khi đó **ứng dụng sở hữu pattern**, không còn hoàn toàn dựa vào localized style của JDK.

## <a id="localized-parsing">Parsing date/time theo Locale</a>

Việc parse dữ liệu người dùng nhập cũng cần locale nếu text có tên tháng/ngày hoặc pattern phụ thuộc locale:

```java
DateTimeFormatter formatter = DateTimeFormatter
        .ofPattern("d MMMM uuuu", Locale.forLanguageTag("vi-VN"));

LocalDate date = LocalDate.parse(input, formatter);
```

Nhưng hợp đồng date/time giữa các hệ thống nên dùng định dạng chuẩn, rõ ràng như ISO thay vì text hiển thị phụ thuộc locale:

```java
Instant.parse("2026-09-27T08:30:00Z");
LocalDate.parse("2026-09-27");
```

Quy tắc:

```text
human display/input
→ Locale-aware formatter khi phù hợp

machine contract / persistence
→ explicit stable format, thường ISO
```

Khi parse text theo locale, ứng dụng cũng phải quyết định chính sách resolver/validation và xử lý `DateTimeParseException`; đừng coi dữ liệu hiển thị là biểu diễn chuẩn của giá trị nghiệp vụ.

Chương kế tiếp đi sâu vào một hành vi đã xuất hiện từ `ResourceBundle`: **fallback**. Fallback giúp ứng dụng dùng resource tổng quát hơn khi resource cụ thể thiếu, nhưng nếu không hiểu chuỗi tra cứu này nó cũng có thể che giấu lỗi bản dịch/cấu hình.
