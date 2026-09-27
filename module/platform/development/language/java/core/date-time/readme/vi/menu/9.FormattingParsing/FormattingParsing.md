# Định dạng và phân tích Date-Time

Date-time object và text là hai representation khác nhau. Object như `LocalDate` hay `Instant` giữ semantics; chuỗi như `27/09/2026`, `2026-09-27` hay `09:30 AM` chỉ là **cách biểu diễn** dùng ở UI, file, API hoặc protocol.

Sai lầm phổ biến là để format string trở thành “model thời gian”. Chapter này tách rõ hai việc:

```text
format
→ temporal object → text

parse
→ text → temporal object
```

## <a id="date-time-formatter">DateTimeFormatter và tính bất biến</a>

`DateTimeFormatter` là formatter/parser chính của `java.time`. Nó được thiết kế immutable và thread-safe, nên có thể tái sử dụng thay vì tạo mới cho từng request.

`DateTimeFormatterBuilder` là **builder dùng để ghép một formatter phức tạp từ nhiều phần** khi một predefined formatter hoặc một pattern string đơn giản chưa đủ, ví dụ khi cần section optional, literal đặc biệt hoặc nhiều rule parse/format kết hợp. Người mới chưa cần dùng nó cho case cơ bản; chỉ cần biết nó là công cụ xây `DateTimeFormatter`, không phải một temporal type mới.

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu");

LocalDate date = LocalDate.of(2026, 9, 27);
String text = date.format(formatter);
// 27/09/2026
```

Parse theo chiều ngược lại:

```java
LocalDate parsed = LocalDate.parse("27/09/2026", formatter);
```

### Formatter là policy ở boundary

Domain value:

```java
LocalDate invoiceDate = LocalDate.of(2026, 9, 27);
```

có thể được hiển thị nhiều cách:

```text
27/09/2026
09/27/2026
2026-09-27
27 Sep 2026
```

Không nên đổi domain model chỉ vì UI muốn format khác. Format belongs to presentation/serialization boundary.

### Predefined formatter

Java cung cấp nhiều formatter chuẩn:

```java
LocalDate date = LocalDate.parse("2026-09-27", DateTimeFormatter.ISO_LOCAL_DATE);
Instant instant = Instant.parse("2026-09-27T10:15:30Z");
OffsetDateTime offset = OffsetDateTime.parse("2026-09-27T17:15:30+07:00");
```

Khi protocol đã có chuẩn ISO phù hợp, predefined formatter thường an toàn và dễ hiểu hơn tự tạo pattern tùy ý.

### Format một Instant cần local context nếu output có calendar fields

`Instant` chỉ biết timeline point. Nếu muốn render nó thành year/month/day/hour của một khu vực, formatter hoặc bước conversion phải có zone:

```java
Instant instant = Instant.parse("2026-09-27T07:30:00Z");

DateTimeFormatter display = DateTimeFormatter
        .ofPattern("dd/MM/uuuu HH:mm")
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

String text = display.format(instant);
```

Nếu dùng custom pattern cần các field như year/hour nhưng không cung cấp zone cho một `Instant`, formatter không có đủ local calendar fields để tạo output tương ứng và operation có thể fail với `UnsupportedTemporalTypeException`.

Mental model:

```text
Instant
→ biết "khi nào"

ZoneId
→ biết "nhìn từ đâu"

DateTimeFormatter
→ biết "viết thành text thế nào"
```

## <a id="format-patterns">Pattern và predefined formatter</a>

Custom pattern hữu ích khi format là requirement thật của UI/file legacy:

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm");
LocalDateTime value = LocalDateTime.of(2026, 9, 27, 14, 30);

String text = formatter.format(value);
// 27-09-2026 14:30
```

Nhưng pattern letters có semantics riêng; không nên đoán bằng trực giác.

Ví dụ quan trọng:

```text
MM → month-of-year
mm → minute-of-hour

HH → hour-of-day 00-23
hh → clock-hour-of-am-pm 01-12

uuuu → proleptic year
yyyy → year-of-era
YYYY → week-based-year
```

Việc nhầm `MM` và `mm`, hoặc `yyyy` với `YYYY`, là bug rất phổ biến.

### `uuuu` và `yyyy`

Trong nhiều business date hiện đại, `uuuu` thường dễ dùng hơn khi cần strict ISO-style parsing vì nó là **proleptic year**: năm được đánh số liên tục xuyên qua era, có cả year `0` và year âm khi đi lùi trước era hiện tại.

`yyyy` là **year-of-era**: số năm nằm bên trong một era như CE/BCE, nên về mặt đầy đủ nó cần ngữ cảnh era để biểu diễn toàn bộ year semantics.

`YYYY` lại là **week-based-year**: năm được xác định theo tuần chứa ngày đó, không phải calendar year thông thường. Quanh cuối/tháng đầu năm, một ngày như `2025-12-29` có thể thuộc week-based-year khác calendar year tùy week rules. Vì vậy đừng dùng `YYYY-MM-dd` khi ý định thật sự là calendar date.

```java
DateTimeFormatter strictDate = DateTimeFormatter
        .ofPattern("dd/MM/uuuu")
        .withResolverStyle(ResolverStyle.STRICT);
```

Không cần biến quy tắc này thành “luôn luôn cấm yyyy”; điều cần học là **pattern letter mang semantics**, không chỉ là shape của output.

### Locale ảnh hưởng text

Pattern có month name/day name cần `Locale` rõ ràng:

```java
DateTimeFormatter english = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
```

`Locale` ở đây là context ngôn ngữ/khu vực dùng cho **presentation**, ví dụ tên tháng, tên thứ hoặc quy ước text. Nó không thay thế `ZoneId` và không quyết định instant. Localization chuyên sâu thuộc module `localization`; ở đây chỉ cần biết formatter có thể phụ thuộc locale và không nên vô tình dùng machine default khi contract cần deterministic output.

## <a id="strict-smart-lenient">ResolverStyle: STRICT, SMART, LENIENT</a>

Parsing không chỉ là tách ký tự. Sau khi đọc field, Java còn phải **resolve** chúng thành một temporal value hợp lệ.

Ba resolver style chính:

```text
STRICT
→ yêu cầu field hợp lệ đúng theo rule

SMART
→ có một số điều chỉnh hợp lý theo semantics formatter

LENIENT
→ cho phép overflow và normalize rộng hơn
```

Với formatter tạo bằng các factory/pattern thông thường, **resolver style mặc định là `SMART`**. Nếu contract yêu cầu reject dữ liệu calendar không hợp lệ một cách chặt chẽ, đừng giả định default là strict; hãy cấu hình `ResolverStyle.STRICT` có chủ đích.

Ví dụ:

```java
DateTimeFormatter strict = DateTimeFormatter
        .ofPattern("dd/MM/uuuu")
        .withResolverStyle(ResolverStyle.STRICT);

LocalDate.parse("31/02/2026", strict); // DateTimeParseException
```

Với input từ user/API có contract chặt, `STRICT` thường dễ reasoning hơn vì invalid calendar data bị reject thay vì âm thầm điều chỉnh.

### SMART không đồng nghĩa validation business

Ngay cả parse thành công, dữ liệu có thể vẫn vi phạm rule application:

```text
date hợp lệ trên lịch
≠
date hợp lệ cho booking
```

Ví dụ ngày booking có thể phải nằm trong 90 ngày tới hoặc không rơi vào holiday. `DateTimeFormatter` chỉ xử lý temporal text, không thay thế business validation.

## <a id="parse-target-type">Parse vào đúng temporal type</a>

Text chứa thông tin nào thì target type phải phù hợp.

```java
LocalDate date = LocalDate.parse("2026-09-27");
LocalDateTime local = LocalDateTime.parse("2026-09-27T14:30:00");
OffsetDateTime offset = OffsetDateTime.parse("2026-09-27T14:30:00+07:00");
ZonedDateTime zoned = ZonedDateTime.parse("2026-09-27T14:30:00+07:00[Asia/Ho_Chi_Minh]");
Instant instant = Instant.parse("2026-09-27T07:30:00Z");
```

### Đừng parse rồi vứt mất thông tin

Nếu input có offset:

```text
2026-09-27T14:30:00+07:00
```

parse thành `LocalDateTime` sau khi tự cắt phần `+07:00` sẽ làm mất timeline context. Hãy parse thành `OffsetDateTime` hoặc type phù hợp trước, rồi convert có chủ đích.

### Đừng invent thông tin mà text không có

Nếu input chỉ là:

```text
2026-09-27T14:30:00
```

không được tự kết luận đó là UTC hoặc system default zone trừ khi external contract nói như vậy.

Mental model:

```text
text fields thực sự có
        ↓
parse vào type giữ đúng semantics
        ↓
chỉ bổ sung zone/offset khi contract/domain cung cấp
```

Sau khi object đã được parse đúng, bước tiếp theo là arithmetic và comparison: cùng là cộng/trừ/so sánh nhưng mỗi temporal type có semantics riêng.
