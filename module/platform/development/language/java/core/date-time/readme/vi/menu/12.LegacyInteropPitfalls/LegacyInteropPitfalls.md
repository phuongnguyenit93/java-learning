# Tương tác API cũ và các lỗi Date-Time thường gặp

`java.time` giải quyết nhiều vấn đề thiết kế của API date-time cũ, nhưng application thực tế vẫn thường gặp `java.util.Date`, `Calendar`, JDBC types, database columns và system defaults. Vì vậy cần biết cách interop mà không mang mental model cũ vào code mới.

## <a id="legacy-date-calendar">Date và Calendar: mental model legacy</a>

### java.util.Date

Tên `Date` dễ gây hiểu nhầm: `java.util.Date` không phải modern calendar-date type giống `LocalDate`. Nó chủ yếu biểu diễn một point-in-time dựa trên milliseconds từ epoch và có API legacy/mutable.

```java
Date legacy = new Date();
```

Nhiều getter/setter calendar-style của `Date` đã deprecated từ lâu; modern code nên ưu tiên `java.time`.

### Calendar

`Calendar` cung cấp calendar fields, zone và arithmetic theo kiểu mutable:

```java
Calendar calendar = Calendar.getInstance();
calendar.add(Calendar.DAY_OF_MONTH, 1);
```

Operation sửa trực tiếp object, khác mental model immutable của `java.time`.

Các API cũ vẫn xuất hiện ở boundary legacy, nhưng đừng chọn chúng cho code mới chỉ vì hệ thống đã có sẵn một vài method dùng `Date`.

### SimpleDateFormat — legacy formatter mutable và không thread-safe

Code cũ còn rất hay gặp `java.text.SimpleDateFormat`:

```java
SimpleDateFormat legacyFormatter = new SimpleDateFormat("dd/MM/yyyy");
```

Khác `DateTimeFormatter`, `SimpleDateFormat` là object **mutable và không thread-safe**. Vì vậy pattern kiểu chia sẻ một instance global/static giữa nhiều thread có thể tạo race condition và kết quả parse/format khó đoán.

```text
SimpleDateFormat
→ legacy formatter
→ mutable
→ không nên share giữa nhiều thread nếu không có synchronization phù hợp

DateTimeFormatter
→ modern java.time formatter
→ immutable + thread-safe
```

Khi migrate code mới, ưu tiên `DateTimeFormatter`; chỉ giữ `SimpleDateFormat` ở legacy boundary khi API cũ bắt buộc.

## <a id="legacy-conversion">Chuyển đổi legacy có chủ đích</a>

### Date ↔ Instant

`Date` và `Instant` đều có thể biểu diễn timeline point, nên conversion tương đối trực tiếp:

```java
Date legacy = new Date();
Instant instant = legacy.toInstant();

Date back = Date.from(instant);
```

Đây thường là boundary tốt: convert sang modern type sớm, xử lý bằng `java.time`, rồi chỉ convert lại nếu API legacy bắt buộc.

### Calendar → ZonedDateTime

```java
Calendar calendar = Calendar.getInstance();

ZonedDateTime modern = calendar.toInstant()
        .atZone(calendar.getTimeZone().toZoneId());
```

Ở đây cần cả timeline point và zone của `Calendar` để giữ local interpretation phù hợp.

### `java.sql.Date`, `Time`, `Timestamp`

**JDBC (Java Database Connectivity)** là API Java dùng để làm việc với relational database. Code JDBC cũ thường expose ba wrapper `java.sql.*` rất dễ bị nhầm với `java.time`:

```text
java.sql.Date
→ SQL DATE-style value
→ modern counterpart tự nhiên: LocalDate

java.sql.Time
→ SQL TIME-style value
→ modern counterpart tự nhiên: LocalTime

java.sql.Timestamp
→ SQL TIMESTAMP-style wrapper có fractional seconds
→ có API conversion với LocalDateTime và Instant
```

Java cung cấp conversion trực tiếp:

Trong snippet dưới, `sqlDate`, `sqlTime` và `timestamp` được giả sử là **legacy JDBC values đã được API/database layer cũ cung cấp**; mục tiêu của đoạn code chỉ là minh họa bước conversion sang `java.time` và ngược lại.

```java
LocalDate date = sqlDate.toLocalDate();
java.sql.Date sqlDateAgain = java.sql.Date.valueOf(date);

LocalTime time = sqlTime.toLocalTime();
java.sql.Time sqlTimeAgain = java.sql.Time.valueOf(time);

LocalDateTime localDateTime = timestamp.toLocalDateTime();
java.sql.Timestamp timestampFromLocal = java.sql.Timestamp.valueOf(localDateTime);

Instant instant = timestamp.toInstant();
java.sql.Timestamp timestampFromInstant = java.sql.Timestamp.from(instant);
```

Nhưng các conversion legacy này **không hoàn toàn đối xứng** và có vài bẫy quan trọng:

```text
java.sql.Date.toInstant()
→ không được hỗ trợ, ném UnsupportedOperationException

java.sql.Time.toInstant()
→ không được hỗ trợ, ném UnsupportedOperationException

java.sql.Time.valueOf(LocalTime)
→ chỉ giữ hour/minute/second
→ phần nanosecond của LocalTime bị mất

Timestamp.from(Instant) / timestamp.toInstant()
→ timeline-oriented conversion

Timestamp.valueOf(LocalDateTime) / timestamp.toLocalDateTime()
→ local date-time interpretation của legacy Timestamp
→ có thể phụ thuộc default time-zone khi mapping legacy millisecond value ↔ local fields
```

Vì vậy nếu domain sở hữu một `Instant`, ưu tiên boundary `Timestamp ↔ Instant`; nếu schema thật sự mang local date-time semantics thì dùng `Timestamp ↔ LocalDateTime` có chủ đích và kiểm soát default-zone assumptions trong stack JDBC/database.

Đừng vì cả `Timestamp` và `Instant` đều có thể liên quan timeline mà mặc định mọi database `TIMESTAMP` column đều có cùng semantics. Ý nghĩa của SQL type, time-zone handling và precision còn phụ thuộc database/schema/driver. Boundary database phải được thiết kế rõ thay vì suy từ tên Java class.

### Modern JDBC có thể làm việc trực tiếp với java.time

Từ JDBC 4.2, nhiều `java.time` type có standard mapping trực tiếp qua `setObject` / `getObject`, nên code mới **không bắt buộc phải đi vòng qua `java.sql.Date/Time/Timestamp`** chỉ để truy cập database.

Ví dụ ở driver/database hỗ trợ mapping tương ứng:

Trong snippet này, `preparedStatement` là một `PreparedStatement` đã được application tạo để gửi parameter xuống database, còn `resultSet` là `ResultSet` nhận từ query. Chúng là JDBC boundary objects, không phải date-time types.

```java
LocalDate invoiceDate = LocalDate.of(2026, 9, 27);
preparedStatement.setObject(1, invoiceDate);

LocalDate loadedDate = resultSet.getObject("invoice_date", LocalDate.class);

OffsetDateTime occurredAt = OffsetDateTime.parse("2026-09-27T17:30:00+07:00");
preparedStatement.setObject(2, occurredAt);

OffsetDateTime loadedOccurredAt = resultSet.getObject(
        "occurred_at",
        OffsetDateTime.class
);
```

`LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetTime` và `OffsetDateTime` có mapping JDBC 4.2 chuẩn. Tuy nhiên database type thực tế, driver capability và time-zone semantics vẫn phải được kiểm tra theo schema/provider. Đặc biệt, đừng suy rằng mọi driver có direct `Instant` mapping giống nhau chỉ vì application dùng `Instant` trong domain.

### Không convert bằng text nếu có API trực tiếp

Pattern không cần thiết:

```text
Date → format String → parse LocalDateTime
```

Nó đưa format/locale/default zone vào giữa một conversion có thể làm trực tiếp và an toàn hơn.

## <a id="system-default-zone-risk">Rủi ro của system default zone</a>

Các lời gọi như:

```java
ZoneId.systemDefault();
LocalDate.now();
ZonedDateTime.now();
```

có thể phụ thuộc cấu hình máy nếu không truyền `Clock`/zone rõ ràng.

Bug thường xuất hiện khi:

```text
developer laptop → Asia/Ho_Chi_Minh
CI               → UTC
production       → UTC hoặc region khác
```

Cùng một `Instant`, `LocalDate.now(zone)` quanh midnight có thể cho ngày khác giữa các zone.

### Conversion nguy hiểm

```java
LocalDateTime local = ...;
Instant instant = local.atZone(ZoneId.systemDefault()).toInstant();
```

Nếu `local` thuộc business zone cụ thể, dùng system default là assumption ẩn. Hãy truyền đúng `ZoneId` từ domain/configuration.

## <a id="dst-gap-overlap">DST gap và overlap</a>

Ở region áp dụng daylight saving time, local clock có các transition đặc biệt.

### Gap — một khoảng local time không tồn tại

Khi clock nhảy về phía trước, ví dụ từ 02:00 lên 03:00, các local time trong khoảng bị bỏ qua không tồn tại trong zone đó.

Khi dùng API tiện ích như `LocalDateTime.atZone(zone)`, Java resolve gap bằng cách điều chỉnh local time tiến qua độ dài gap theo rule của `ZonedDateTime`.

Với input business quan trọng, đừng chỉ dựa vào adjustment mặc định nếu “thời gian không tồn tại” phải được báo cho user. Có thể kiểm tra `ZoneRules`:

```java
LocalDateTime local = ...;
ZoneId zone = ZoneId.of("Europe/Paris");

List<ZoneOffset> validOffsets = zone.getRules().getValidOffsets(local);

if (validOffsets.isEmpty()) {
    // local time falls in a gap
}
```

### Overlap — một local time xảy ra hai lần

Khi clock quay lại, cùng local time có thể hợp lệ với hai offset.

```java
List<ZoneOffset> validOffsets = zone.getRules().getValidOffsets(local);

if (validOffsets.size() == 2) {
    // ambiguous local time
}
```

Khi tạo `ZonedDateTime` từ local value bằng API thông thường, Java có rule cụ thể:

```text
normal
→ 1 offset hợp lệ → dùng offset đó

gap
→ 0 offset hợp lệ
→ local date-time được đẩy tiến theo độ dài gap

overlap
→ 2 offset hợp lệ
→ mặc định chọn earlier offset tại local timeline
  (thường là offset mùa hè / trước transition)
```

Nếu application cần occurrence còn lại trong overlap, `withLaterOffsetAtOverlap()` cho phép chọn later offset; `withEarlierOffsetAtOverlap()` chọn earlier offset một cách explicit.

Khi cần inspect transition thay vì chỉ đếm valid offsets, `ZoneRules.getTransition(localDateTime)` trả `ZoneOffsetTransition` cho gap/overlap tương ứng; `nextTransition(instant)` và `previousTransition(instant)` giúp tìm transition quanh timeline point.

Điểm học quan trọng không phải nhớ một ngày DST cụ thể, mà là hiểu:

```text
LocalDateTime + ZoneId
không phải lúc nào cũng map 1:1 tới Instant
```

## <a id="timestamp-storage-boundary">Chọn storage semantics có chủ đích</a>

Không có một quy tắc “mọi date-time đều phải lưu UTC” áp dụng cho mọi domain. Câu đúng hơn là: **lưu đủ thông tin để phục hồi đúng semantics mà business sở hữu**.

### Event đã xảy ra

Ví dụ:

```text
paymentCapturedAt
requestReceivedAt
auditEventAt
```

Thường cần một timeline point → `Instant`/UTC-oriented storage là tự nhiên.

### Lịch tương lai gắn với địa điểm

Ví dụ:

```text
"09:00 ngày 05/10 tại Europe/Paris"
```

Domain có thể cần giữ:

```text
local date-time
+ ZoneId
```

vì region rule là một phần của intent. Chỉ giữ initial instant hoặc initial offset có thể mất thông tin cần cho rescheduling/display/future rule updates.

### Local business date/time

Ngày sinh, ngày hóa đơn hoặc giờ mở cửa không nên bị ép thành instant nếu domain không có timeline semantics.

### Checklist trước khi chọn column/type

```text
1. Đây là calendar value hay timeline event?
2. Có cần named region không?
3. Offset có phải dữ liệu gốc hay chỉ là kết quả resolve?
4. Đây là lịch tương lai hay event đã xảy ra?
5. Database lưu precision tới mức nào?
6. Khi đọc lại, application cần phục hồi chính xác thông tin nào?
```

Nếu trả lời được các câu đó, lựa chọn giữa `LocalDate`, `LocalDateTime`, `Instant`, `OffsetDateTime` và `ZonedDateTime` sẽ xuất phát từ domain thay vì convention mơ hồ.

Kết thúc module, mental model nên là:

```text
Hiểu ý nghĩa thời gian trước
        ↓
chọn temporal type giữ đúng thông tin
        ↓
thêm zone/offset chỉ khi domain cần
        ↓
chọn Duration hay Period theo semantics
        ↓
format/parse ở boundary
        ↓
inject Clock cho "now"
        ↓
tránh default zone và kiểm soát DST/legacy conversion
```
