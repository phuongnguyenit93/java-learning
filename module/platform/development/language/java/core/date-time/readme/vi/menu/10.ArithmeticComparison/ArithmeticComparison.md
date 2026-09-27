# Tính toán và so sánh thời gian

Date-time arithmetic không chỉ là “cộng một con số”. Khi code cộng `1 day`, `24 hours`, `1 month` hoặc đo khoảng cách giữa hai value, kết quả phụ thuộc vào loại temporal và semantics đang dùng.

## <a id="temporal-arithmetic">plus/minus và semantics của temporal type</a>

Các type `java.time` thường cung cấp operation dạng:

```java
value.plusDays(1);
value.minusHours(2);
value.plus(amount);
value.minus(amount);
```

Nhưng ý nghĩa khác nhau theo type.

### LocalDate

```java
LocalDate invoiceDate = LocalDate.of(2026, 1, 31);
LocalDate nextMonth = invoiceDate.plusMonths(1);
// 2026-02-28
```

Đây là calendar arithmetic.

### Instant

```java
Instant deadline = Instant.parse("2026-09-27T10:00:00Z");
Instant extended = deadline.plus(Duration.ofMinutes(30));
```

Đây là timeline arithmetic.

### ZonedDateTime

`ZonedDateTime` có cả local calendar fields và timeline context, vì vậy cần phân biệt:

```java
zoned.plusDays(1);                // date-based/local semantics
zoned.plus(Duration.ofHours(24)); // timeline duration semantics
```

Quanh DST, hai operation có thể không dẫn tới cùng local time hoặc cùng elapsed seconds.

### TemporalAmount và TemporalUnit

Khi nhìn Javadoc, người mới rất dễ gặp hai overload kiểu:

```java
temporal.plus(amount);
temporal.plus(number, unit);
```

Hai overload này dẫn tới hai abstraction khác nhau:

- `TemporalAmount` là **một lượng thời gian đã có cấu trúc**, ví dụ `Period.ofMonths(2)` hoặc `Duration.ofMinutes(30)`;
- `TemporalUnit` là **đơn vị dùng để diễn giải một con số**, ví dụ DAYS, HOURS, MONTHS;
- `ChronoUnit` là enum implementation chuẩn của `TemporalUnit`, cung cấp những unit quen thuộc như `NANOS`, `SECONDS`, `MINUTES`, `HOURS`, `DAYS`, `WEEKS`, `MONTHS`, `YEARS`.

Mental model:

```text
TemporalAmount
→ "cộng lượng này"
→ Period / Duration

TemporalUnit
→ "con số này đang tính theo đơn vị gì?"

ChronoUnit
→ bộ TemporalUnit chuẩn mà Java cung cấp
```

Quan hệ type:

```text
TemporalAmount
├── Duration
└── Period

TemporalUnit
└── ChronoUnit
```

Ví dụ hai style tương đương về ý định:

```java
LocalDate date = LocalDate.of(2026, 9, 27);

LocalDate byAmount = date.plus(Period.ofWeeks(2));
LocalDate byUnit = date.plus(2, ChronoUnit.WEEKS);
```

Tại sao Java cần abstraction chung này? Vì nhiều temporal type có thể chia sẻ vocabulary `plus`, `minus`, `between`, field và unit mà không cần mỗi class phát minh một interface hoàn toàn khác.

Nhưng abstraction chung **không có nghĩa mọi unit đều hợp lệ cho mọi type**. `LocalDate` không có hour-of-day nên không hỗ trợ `HOURS`; `Instant` không mang calendar-month semantics nên không hỗ trợ trực tiếp `MONTHS`. Khi operation/unit không được type hỗ trợ, API có thể ném `UnsupportedTemporalTypeException`.

## <a id="between-semantics">Semantics của between</a>

Có nhiều cách hỏi “khoảng cách giữa A và B”, và chúng không hoàn toàn giống nhau.

### ChronoUnit.between

```java
LocalDate start = LocalDate.of(2026, 9, 1);
LocalDate end = LocalDate.of(2026, 9, 27);

long days = ChronoUnit.DAYS.between(start, end); // 26
```

`ChronoUnit` trả một số lượng unit hoàn chỉnh giữa hai temporal phù hợp.

### Duration.between

```java
Instant a = Instant.parse("2026-09-27T10:00:00Z");
Instant b = Instant.parse("2026-09-27T11:30:00Z");

Duration elapsed = Duration.between(a, b);
```

Phù hợp khi cần elapsed timeline time.

### Period.between

```java
LocalDate birth = LocalDate.of(1993, 7, 20);
LocalDate date = LocalDate.of(2026, 9, 27);

Period calendarDifference = Period.between(birth, date);
```

Kết quả giữ year/month/day calendar components; nó không phải tổng số seconds.

### Boundary và unit hoàn chỉnh

Khi dùng `ChronoUnit.HOURS.between`, kết quả là số hour unit hoàn chỉnh theo semantics của temporal đó. Nếu application cần fractional unit, giữ `Duration`/nanos rồi tính theo requirement thay vì giả định `between` trả decimal.

### Time range cần định nghĩa rõ inclusive/exclusive boundary

`java.time` không có một core class tên `Interval` bắt buộc mọi application phải dùng, nên domain thường tự định nghĩa semantics cho một khoảng thời gian. Convention rất phổ biến là **half-open interval**:

```text
[start, end)

start     → inclusive
end       → exclusive
```

Ví dụ một event thuộc range khi:

```java
boolean inside = !event.isBefore(start) && event.isBefore(endExclusive);
```

Convention này đặc biệt hữu ích cho query “toàn bộ event trong một ngày” vì tránh phải invent giá trị kiểu `23:59:59.999999999`:

```java
LocalDate day = LocalDate.of(2026, 9, 27);
ZoneId zone = ZoneId.of("Europe/Paris");

Instant start = day.atStartOfDay(zone).toInstant();
Instant endExclusive = day.plusDays(1).atStartOfDay(zone).toInstant();
```

`LocalDate.atStartOfDay(zone)` không đơn giản là luôn ép `00:00`. Nếu midnight rơi vào một zone transition/gap, Java trả **earliest valid time** của ngày đó trong zone. Vì vậy `[startOfDay, startOfNextDay)` an toàn hơn tự ghép `00:00`/`23:59:59...` khi domain thực sự hỏi theo ngày của một region.

## <a id="date-time-comparison">So sánh date-time đúng nghĩa</a>

Các type thường có:

```java
a.isBefore(b);
a.isAfter(b);
a.compareTo(b);
a.equals(b);
```

Nhưng phải hỏi **đang so sánh cái gì**.

### LocalDate comparison

```java
LocalDate a = LocalDate.of(2026, 9, 27);
LocalDate b = LocalDate.of(2026, 9, 28);

a.isBefore(b); // true
```

Đây là order trên calendar date.

### Instant comparison

```java
instantA.isBefore(instantB);
```

Đây là order trên global timeline.

### Zoned values: local representation và timeline identity

Hai `ZonedDateTime` có thể có field/zone khác nhau nhưng cùng instant. Khi domain hỏi “cùng sự kiện trên timeline không?”, cách rõ ràng là so `toInstant()` hoặc dùng API có semantics instant phù hợp.

```java
boolean sameMoment = a.toInstant().equals(b.toInstant());
boolean sameMomentDirectly = a.isEqual(b);
```

`isEqual(...)` trên các zone-aware temporal phù hợp trả lời câu hỏi timeline equality. Đừng dùng `equals()` như một shorthand cho mọi khái niệm “cùng thời điểm”; equality của object còn quan tâm representation/type state theo contract của type.

Với `ZonedDateTime` và `OffsetDateTime`, có thể đọc các API theo hai nhóm:

```text
isEqual / isBefore / isAfter
→ câu hỏi trên instant/timeline

equals
→ equality của object representation theo contract của type

compareTo
→ natural ordering của type; có tie-break để nhất quán với equality
→ không nên giả định đây là "instant-only comparator"
```

Ở đây:

```text
natural ordering
→ thứ tự mặc định mà compareTo(...) định nghĩa cho type

tie-break
→ tiêu chí phụ chỉ được dùng khi tiêu chí so sánh trước đó bằng nhau
```

Nếu business logic chỉ quan tâm timeline order, `toInstant()` hoặc các method `isBefore` / `isAfter` / `isEqual` làm intent rõ hơn.

**Boundary nâng cao — chi tiết contract:** người mới chỉ cần nắm rule ở trên trước; block dưới hữu ích khi code sorting/comparator cần đúng contract tuyệt đối.

```text
OffsetDateTime.equals(...)
→ cùng local date-time + cùng offset

ZonedDateTime.equals(...)
→ cùng local date-time + cùng offset + cùng ZoneId

isEqual / isBefore / isAfter
→ so theo instant trên timeline

OffsetDateTime.compareTo(...)
→ trước hết so instant
→ nếu cùng instant thì dùng local date-time làm tie-break

ZonedDateTime.compareTo(...)
→ trước hết so instant
→ rồi local date-time
→ rồi ZoneId
→ chronology (calendar system) là tie-break cuối trong contract ChronoZonedDateTime
```

Do đó hai value có thể **cùng instant nhưng `compareTo(...) != 0`**. Nếu cần comparator chỉ theo timeline, `OffsetDateTime.timeLineOrder()` và `ChronoZonedDateTime.timeLineOrder()` tồn tại đúng cho mục đích đó.

### So sánh local values không tạo ra global meaning

```java
LocalDateTime vietnamNine = LocalDateTime.of(2026, 10, 5, 9, 0);
LocalDateTime parisNine = LocalDateTime.of(2026, 10, 5, 9, 0);
```

Hai value bằng nhau về local fields nhưng không chứng minh hai event ở Việt Nam và Paris xảy ra cùng instant. Zone context phải được thêm trước.

## <a id="business-calendar-boundary">Business calendar là policy riêng</a>

JDK biết calendar mechanics nhưng không biết business rule của công ty bạn.

Ví dụ câu “deadline sau 3 ngày làm việc” cần trả lời:

```text
Thứ Bảy có tính không?
Chủ Nhật?
Ngày lễ quốc gia nào?
Ngày nghỉ riêng của công ty?
Cut-off 17:00 xử lý thế nào?
```

Không nên viết:

```java
deadline = start.plusDays(3);
```

rồi gọi đó là “3 business days”. `plusDays(3)` chỉ biết calendar days.

Một design rõ ràng hơn:

```java
interface BusinessCalendar {
    LocalDate addBusinessDays(LocalDate start, int days);
    boolean isBusinessDay(LocalDate date);
}
```

Implementation có thể sử dụng `LocalDate`, `DayOfWeek` và nguồn holiday riêng.

Boundary này quan trọng vì Java date-time API cung cấp **mechanics**, còn business calendar cung cấp **policy**.

Chapter tiếp theo xử lý một dependency thường bị giấu trong code: lời gọi “bây giờ là mấy giờ?” thông qua system clock.
