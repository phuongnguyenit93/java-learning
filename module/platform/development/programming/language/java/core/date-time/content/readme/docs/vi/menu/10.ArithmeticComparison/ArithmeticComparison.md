# Phép toán, so sánh và ảnh hưởng của quy tắc múi giờ

Phép toán ngày-giờ không chỉ là “cộng một con số”. Khi mã cộng 1 ngày, 24 giờ, 1 tháng hoặc đo khoảng cách giữa hai giá trị, kết quả phụ thuộc vào kiểu thời gian và ý nghĩa đang dùng.

## <a id="temporal-arithmetic">Phép cộng/trừ và ý nghĩa của từng kiểu thời gian</a>

Các kiểu `java.time` thường cung cấp thao tác dạng:

```java
value.plusDays(1);
value.minusHours(2);
value.plus(amount);
value.minus(amount);
```

Nhưng ý nghĩa khác nhau theo kiểu.

### LocalDate

```java
LocalDate invoiceDate = LocalDate.of(2026, 1, 31);
LocalDate nextMonth = invoiceDate.plusMonths(1);
// 2026-02-28
```

Đây là phép toán theo lịch.

### Instant

```java
Instant deadline = Instant.parse("2026-09-27T10:00:00Z");
Instant extended = deadline.plus(Duration.ofMinutes(30));
```

Đây là phép toán theo dòng thời gian.

### ZonedDateTime

`ZonedDateTime` có cả các trường lịch cục bộ và ngữ cảnh dòng thời gian, vì vậy cần phân biệt:

```java
zoned.plusDays(1);                // theo ngày lịch / giờ cục bộ
zoned.plus(Duration.ofHours(24)); // theo thời lượng thực trên dòng thời gian
```

Quanh thời điểm chuyển DST, hai thao tác có thể không dẫn tới cùng giờ cục bộ hoặc cùng số giây thực tế đã trôi qua.

### TemporalAmount và TemporalUnit

Khi nhìn Javadoc, người mới rất dễ gặp hai overload kiểu:

```java
temporal.plus(amount);
temporal.plus(number, unit);
```

Hai overload này dẫn tới hai giao diện dùng chung khác nhau:

- `TemporalAmount` là **một lượng thời gian đã có cấu trúc**, ví dụ `Period.ofMonths(2)` hoặc `Duration.ofMinutes(30)`;
- `TemporalUnit` là **đơn vị dùng để diễn giải một con số**, ví dụ DAYS, HOURS, MONTHS;
- `ChronoUnit` là enum triển khai chuẩn của `TemporalUnit`, cung cấp những đơn vị quen thuộc như `NANOS`, `SECONDS`, `MINUTES`, `HOURS`, `DAYS`, `WEEKS`, `MONTHS`, `YEARS`.

Mô hình tư duy:

```text
TemporalAmount
→ "cộng lượng này"
→ Period / Duration

TemporalUnit
→ "con số này đang tính theo đơn vị gì?"

ChronoUnit
→ bộ TemporalUnit chuẩn mà Java cung cấp
```

Quan hệ kiểu:

```text
TemporalAmount
├── Duration
└── Period

TemporalUnit
└── ChronoUnit
```

Ví dụ hai cách viết tương đương về ý định:

```java
LocalDate date = LocalDate.of(2026, 9, 27);

LocalDate byAmount = date.plus(Period.ofWeeks(2));
LocalDate byUnit = date.plus(2, ChronoUnit.WEEKS);
```

Tại sao Java cần các giao diện dùng chung này? Vì nhiều kiểu thời gian có thể chia sẻ từ vựng `plus`, `minus`, `between`, trường và đơn vị mà không cần mỗi lớp phát minh một giao diện hoàn toàn khác.

Nhưng việc dùng giao diện chung **không có nghĩa mọi đơn vị đều hợp lệ cho mọi kiểu**. `LocalDate` không có trường giờ trong ngày nên không hỗ trợ `HOURS`; `Instant` không mang ý nghĩa tháng theo lịch nên không hỗ trợ trực tiếp `MONTHS`. Khi thao tác hoặc đơn vị không được kiểu hỗ trợ, API có thể ném `UnsupportedTemporalTypeException`.

## <a id="between-semantics">Ý nghĩa của phép tính khoảng cách `between`</a>

Có nhiều cách hỏi “khoảng cách giữa A và B”, và chúng không hoàn toàn giống nhau.

### ChronoUnit.between

```java
LocalDate start = LocalDate.of(2026, 9, 1);
LocalDate end = LocalDate.of(2026, 9, 27);

long days = ChronoUnit.DAYS.between(start, end); // 26
```

`ChronoUnit` trả một số lượng đơn vị hoàn chỉnh giữa hai giá trị thời gian phù hợp.

### Duration.between

```java
Instant a = Instant.parse("2026-09-27T10:00:00Z");
Instant b = Instant.parse("2026-09-27T11:30:00Z");

Duration elapsed = Duration.between(a, b);
```

Phù hợp khi cần thời lượng thực đã trôi qua trên dòng thời gian.

### Period.between

```java
LocalDate birth = LocalDate.of(1993, 7, 20);
LocalDate date = LocalDate.of(2026, 9, 27);

Period calendarDifference = Period.between(birth, date);
```

Kết quả giữ các thành phần năm/tháng/ngày theo lịch; nó không phải tổng số giây.

### Cách between đếm các đơn vị hoàn chỉnh

Khi dùng `ChronoUnit.HOURS.between`, kết quả là số đơn vị giờ hoàn chỉnh theo ý nghĩa của giá trị thời gian đó. Nếu ứng dụng cần phần lẻ của đơn vị, hãy giữ `Duration` hoặc nano giây rồi tính theo yêu cầu thay vì giả định `between` trả số thập phân.

### Khoảng thời gian cần định nghĩa rõ biên bao gồm và không bao gồm

`java.time` không có một lớp lõi tên `Interval` bắt buộc mọi ứng dụng phải dùng, nên nghiệp vụ thường tự định nghĩa ý nghĩa cho một khoảng thời gian. Một quy ước rất phổ biến là **khoảng nửa mở (half-open interval)**:

```text
[start, end)

start     → bao gồm đầu mút
end       → không bao gồm đầu mút
```

Ví dụ một sự kiện thuộc khoảng khi:

```java
boolean inside = !event.isBefore(start) && event.isBefore(endExclusive);
```

Quy ước này đặc biệt hữu ích cho truy vấn “toàn bộ sự kiện trong một ngày” vì tránh phải tự tạo giá trị kiểu `23:59:59.999999999`:

```java
LocalDate day = LocalDate.of(2026, 9, 27);
ZoneId zone = ZoneId.of("Europe/Paris");

Instant start = day.atStartOfDay(zone).toInstant();
Instant endExclusive = day.plusDays(1).atStartOfDay(zone).toInstant();
```

`LocalDate.atStartOfDay(zone)` không đơn giản là luôn ép `00:00`. Java dùng quy tắc múi giờ để tìm thời điểm bắt đầu hợp lệ: nếu đầu ngày rơi vào khoảng trống, kết quả được đẩy tới thời điểm ngay sau khoảng trống; trong trường hợp cực đoan cả ngày cục bộ bị bỏ qua, ngày-giờ kết quả thậm chí có thể thuộc ngày kế tiếp. Vì vậy `[startOfDay, startOfNextDay)` an toàn hơn tự ghép `00:00`/`23:59:59...` khi nghiệp vụ thực sự hỏi theo ngày của một vùng.

## <a id="date-time-comparison">So sánh giá trị ngày-giờ theo đúng ý nghĩa</a>

Các kiểu thường có:

```java
a.isBefore(b);
a.isAfter(b);
a.compareTo(b);
a.equals(b);
```

Nhưng phải hỏi **đang so sánh cái gì**.

### So sánh LocalDate

```java
LocalDate a = LocalDate.of(2026, 9, 27);
LocalDate b = LocalDate.of(2026, 9, 28);

a.isBefore(b); // true
```

Đây là thứ tự trên ngày theo lịch.

### So sánh Instant

```java
instantA.isBefore(instantB);
```

Đây là thứ tự trên dòng thời gian toàn cục.

### Giá trị có múi giờ: cách biểu diễn cục bộ và vị trí trên dòng thời gian

Hai `ZonedDateTime` có thể có trường/múi giờ khác nhau nhưng cùng `Instant`. Khi nghiệp vụ hỏi “cùng sự kiện trên dòng thời gian không?”, cách rõ ràng là so `toInstant()` hoặc dùng API có ngữ nghĩa theo `Instant`.

```java
boolean sameMoment = a.toInstant().equals(b.toInstant());
boolean sameMomentDirectly = a.isEqual(b);
```

`isEqual(...)` trên các giá trị có múi giờ phù hợp trả lời câu hỏi bằng nhau trên dòng thời gian. Đừng dùng `equals()` như cách viết tắt cho mọi khái niệm “cùng thời điểm”; tính bằng nhau của đối tượng còn phụ thuộc trạng thái biểu diễn theo hợp đồng của từng kiểu.

Với `ZonedDateTime` và `OffsetDateTime`, có thể đọc các API theo hai nhóm:

```text
isEqual / isBefore / isAfter
→ câu hỏi trên Instant/dòng thời gian

equals
→ tính bằng nhau của cách biểu diễn đối tượng theo hợp đồng của kiểu

compareTo
→ thứ tự tự nhiên của kiểu; có tiêu chí phân định để nhất quán với tính bằng nhau
→ không nên giả định đây là "bộ so sánh chỉ theo Instant"
```

Ở đây:

```text
thứ tự tự nhiên
→ thứ tự mặc định mà compareTo(...) định nghĩa cho kiểu

tiêu chí phân định
→ tiêu chí phụ chỉ được dùng khi tiêu chí so sánh trước đó bằng nhau
```

Nếu logic nghiệp vụ chỉ quan tâm thứ tự trên dòng thời gian, `toInstant()` hoặc các phương thức `isBefore` / `isAfter` / `isEqual` làm ý định rõ hơn.

**Ranh giới nâng cao — hợp đồng chính xác:** người mới chỉ cần nắm quy tắc ở trên trước; phần dưới hữu ích khi mã sắp xếp hoặc bộ so sánh cần tuân thủ chính xác hợp đồng của kiểu.

```text
OffsetDateTime.equals(...)
→ cùng ngày-giờ cục bộ + cùng độ lệch

ZonedDateTime.equals(...)
→ cùng ngày-giờ cục bộ + cùng độ lệch + cùng ZoneId

isEqual / isBefore / isAfter
→ so theo Instant trên dòng thời gian

OffsetDateTime.compareTo(...)
→ trước hết so instant
→ nếu cùng Instant thì dùng ngày-giờ cục bộ làm tiêu chí phân định

ZonedDateTime.compareTo(...)
→ trước hết so instant
→ rồi ngày-giờ cục bộ
→ rồi ZoneId
→ hệ lịch (chronology) là tiêu chí phân định cuối trong hợp đồng ChronoZonedDateTime
```

Do đó hai giá trị có thể **cùng Instant nhưng `compareTo(...) != 0`**. Nếu cần bộ so sánh chỉ theo dòng thời gian, `OffsetDateTime.timeLineOrder()` và `ChronoZonedDateTime.timeLineOrder()` tồn tại đúng cho mục đích đó.

### Các giá trị cục bộ bằng nhau không tạo ra ý nghĩa toàn cục

```java
LocalDateTime vietnamNine = LocalDateTime.of(2026, 10, 5, 9, 0);
LocalDateTime parisNine = LocalDateTime.of(2026, 10, 5, 9, 0);
```

Hai giá trị bằng nhau về các trường cục bộ nhưng không chứng minh hai sự kiện ở Việt Nam và Paris xảy ra cùng `Instant`. Ngữ cảnh múi giờ phải được thêm trước.

## <a id="dst-gap-overlap">Khoảng trống và chồng lặp khi đổi giờ mùa hè (DST)</a>

Ở vùng áp dụng giờ mùa hè (DST), đồng hồ cục bộ có các lần chuyển đổi đặc biệt.

### Khoảng trống (gap) — một khoảng giờ cục bộ không tồn tại

Khi đồng hồ nhảy về phía trước, ví dụ từ 02:00 lên 03:00, các giờ cục bộ trong khoảng bị bỏ qua không tồn tại trong múi giờ đó.

Khi dùng API tiện ích như `LocalDateTime.atZone(zone)`, Java phân giải khoảng trống bằng cách điều chỉnh giờ cục bộ tiến qua độ dài khoảng trống theo quy tắc của `ZonedDateTime`.

Với đầu vào nghiệp vụ quan trọng, đừng chỉ dựa vào điều chỉnh mặc định nếu “thời gian không tồn tại” phải được báo cho người dùng. Có thể kiểm tra `ZoneRules`:

```java
LocalDateTime local = ...;
ZoneId zone = ZoneId.of("Europe/Paris");

List<ZoneOffset> validOffsets = zone.getRules().getValidOffsets(local);

if (validOffsets.isEmpty()) {
    // giờ cục bộ rơi vào khoảng trống
}
```

### Chồng lặp (overlap) — một giờ cục bộ xảy ra hai lần

Khi đồng hồ quay lại, cùng một giờ cục bộ có thể hợp lệ với hai độ lệch.

```java
List<ZoneOffset> validOffsets = zone.getRules().getValidOffsets(local);

if (validOffsets.size() == 2) {
    // giờ cục bộ bị mơ hồ
}
```

Khi tạo `ZonedDateTime` từ giá trị cục bộ bằng API thông thường, Java có quy tắc cụ thể:

```text
bình thường
→ 1 độ lệch hợp lệ → dùng độ lệch đó

khoảng trống (gap)
→ 0 độ lệch hợp lệ
→ ngày-giờ cục bộ được đẩy tiến theo độ dài khoảng trống

chồng lặp (overlap)
→ 2 độ lệch hợp lệ
→ mặc định dùng độ lệch trước lần chuyển đổi
  (thường là độ lệch mùa hè; API gọi đây là earlier offset)
```

Nếu ứng dụng cần lần xuất hiện còn lại trong vùng chồng lặp, `withLaterOffsetAtOverlap()` cho phép chọn độ lệch sau lần chuyển đổi; `withEarlierOffsetAtOverlap()` chọn độ lệch trước lần chuyển đổi một cách rõ ràng.

Khi cần xem chính lần chuyển đổi thay vì chỉ đếm các độ lệch hợp lệ, `ZoneRules.getTransition(localDateTime)` trả `ZoneOffsetTransition` cho khoảng trống/chồng lặp tương ứng; `nextTransition(instant)` và `previousTransition(instant)` giúp tìm lần chuyển đổi quanh mốc trên dòng thời gian.

Điểm học quan trọng không phải nhớ một ngày DST cụ thể, mà là hiểu:

```text
LocalDateTime + ZoneId
không phải lúc nào cũng ánh xạ 1:1 tới Instant
```

## <a id="business-calendar-boundary">Lịch nghiệp vụ có quy tắc riêng</a>

JDK biết cơ chế lịch nhưng không biết quy tắc nghiệp vụ của công ty bạn.

Ví dụ câu “hạn chót sau 3 ngày làm việc” cần trả lời:

```text
Thứ Bảy có tính không?
Chủ Nhật?
Ngày lễ quốc gia nào?
Ngày nghỉ riêng của công ty?
Mốc chốt 17:00 xử lý thế nào?
```

Không nên viết:

```java
deadline = start.plusDays(3);
```

rồi gọi đó là “3 ngày làm việc”. `plusDays(3)` chỉ biết ngày theo lịch.

Một thiết kế rõ ràng hơn:

```java
interface BusinessCalendar {
    LocalDate addBusinessDays(LocalDate start, int days);
    boolean isBusinessDay(LocalDate date);
}
```

Cách triển khai có thể sử dụng `LocalDate`, `DayOfWeek` và nguồn ngày nghỉ riêng.

Ranh giới này quan trọng vì API ngày-giờ của Java cung cấp **cơ chế**, còn lịch nghiệp vụ cung cấp **chính sách**.

Chương tiếp theo xử lý một phụ thuộc thường bị giấu trong mã: lời gọi “bây giờ là mấy giờ?” thông qua đồng hồ hệ thống.
