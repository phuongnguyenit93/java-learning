# Duration và Period

“Thêm một ngày” nghe giống “thêm 24 giờ”, nhưng trong date-time domain hai câu đó không phải lúc nào cũng tương đương. Java tách chúng thành hai mô hình: `Duration` và `Period`.

## <a id="duration-time-based">Duration — lượng thời gian theo timeline</a>

`Duration` là amount theo đơn vị time-based, chủ yếu seconds và nanoseconds.

```java
Duration thirtyMinutes = Duration.ofMinutes(30);
Duration twoHours = Duration.ofHours(2);
Duration oneDayAsTime = Duration.ofDays(1); // 24 hours
```

Tư duy:

```text
Duration.ofHours(24)
→ 86,400 seconds elapsed time
```

Nó phù hợp cho timeout, elapsed time, latency, TTL hoặc khoảng cách giữa hai timeline points.

```java
Instant start = Instant.parse("2026-10-05T02:00:00Z");
Instant end = Instant.parse("2026-10-05T03:30:00Z");

Duration elapsed = Duration.between(start, end);
System.out.println(elapsed.toMinutes()); // 90
```

`Duration` có thể âm nếu end nằm trước start.

## <a id="period-date-based">Period — lượng thời gian theo calendar date</a>

`Period` biểu diễn amount theo **year, month, day**.

```java
Period oneMonth = Period.ofMonths(1);
Period oneYearTwoMonths = Period.of(1, 2, 0);
Period oneCalendarDay = Period.ofDays(1);
```

Nó phù hợp cho age-like/calendar rule:

```java
LocalDate start = LocalDate.of(2026, 1, 31);
LocalDate next = start.plus(Period.ofMonths(1));
// 2026-02-28
```

`Period.ofMonths(1)` không định nghĩa một số giây cố định vì tháng có độ dài khác nhau.

### Period không tự normalize mọi thứ

```java
Period p = Period.of(0, 15, 0);
System.out.println(p);              // P15M
System.out.println(p.normalized()); // P1Y3M
```

`normalized()` có thể chuẩn hóa year/month, nhưng day không thể đổi tổng quát sang month vì độ dài tháng thay đổi.

## <a id="duration-vs-period">Duration vs Period: khác biệt về semantics</a>

Chọn theo câu hỏi:

```text
“Bao nhiêu thời gian thực đã trôi qua?”
→ Duration

“Bao nhiêu ngày/tháng/năm theo lịch?”
→ Period
```

### DST: 24 giờ có thể khác 1 ngày lịch

Ở zone có DST transition:

```java
ZonedDateTime start = ZonedDateTime.of(
        LocalDateTime.of(2026, 3, 28, 12, 0),
        ZoneId.of("Europe/Paris")
);

ZonedDateTime plus24Hours = start.plus(Duration.ofHours(24));
ZonedDateTime plusOneDay = start.plus(Period.ofDays(1));
```

`plus24Hours` bảo toàn **elapsed duration 24 giờ** trên timeline. `plusOneDay` bảo toàn ý định **sang ngày lịch tiếp theo với local-time semantics**, nên elapsed seconds có thể là 23 hoặc 25 giờ quanh DST transition.

Đây là lý do không nên đổi `Period.ofDays(1)` và `Duration.ofHours(24)` cho nhau chỉ vì ở nhiều ngày bình thường chúng cho local result giống nhau.

### Month length cũng phá giả định “calendar = fixed seconds”

```text
1 month
→ 28, 29, 30 hoặc 31 ngày tùy vị trí trên lịch

1 year
→ có thể chứa leap day
```

Không có conversion tổng quát chính xác từ `Period.ofMonths(1)` thành một `Duration` nếu chưa biết điểm bắt đầu và zone/context cần thiết.

### Rule chọn nhanh

| Nhu cầu | Chọn |
| --- | --- |
| HTTP timeout 30 giây | `Duration` |
| cache TTL 10 phút | `Duration` |
| thời gian xử lý giữa hai `Instant` | `Duration` |
| cộng 1 tháng vào ngày hóa đơn | `Period` hoặc `plusMonths` |
| tuổi theo năm/tháng/ngày | `Period` |
| lịch chạy “cùng giờ ngày mai” | calendar arithmetic, thường `Period`/`plusDays` trên zone-aware value |

Sau khi có đúng temporal value và amount, bước tiếp theo là boundary rất phổ biến: **text ↔ date-time object**.
