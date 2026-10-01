# Duration và Period — thời lượng trôi qua và khoảng theo lịch

“Thêm một ngày” nghe giống “thêm 24 giờ”, nhưng trong miền ngày-giờ hai câu đó không phải lúc nào cũng tương đương. Java tách chúng thành hai mô hình: `Duration` và `Period`.

## <a id="duration-time-based">Duration — thời lượng trên dòng thời gian</a>

`Duration` biểu diễn lượng thời gian theo giây và nano giây.

```java
Duration thirtyMinutes = Duration.ofMinutes(30);
Duration twoHours = Duration.ofHours(2);
Duration oneDayAsTime = Duration.ofDays(1); // 24 giờ
```

Tư duy:

```text
Duration.ofHours(24)
→ 86.400 giây thời lượng đã trôi qua
```

Nó phù hợp cho thời gian chờ (timeout), thời lượng đã trôi qua, độ trễ (latency), TTL hoặc khoảng cách giữa hai mốc trên dòng thời gian.

```java
Instant start = Instant.parse("2026-10-05T02:00:00Z");
Instant end = Instant.parse("2026-10-05T03:30:00Z");

Duration elapsed = Duration.between(start, end);
System.out.println(elapsed.toMinutes()); // 90
```

`Duration` có thể âm nếu điểm kết thúc nằm trước điểm bắt đầu.

## <a id="period-date-based">Period — khoảng thời gian theo lịch</a>

`Period` biểu diễn lượng theo **năm, tháng, ngày**.

```java
Period oneMonth = Period.ofMonths(1);
Period oneYearTwoMonths = Period.of(1, 2, 0);
Period oneCalendarDay = Period.ofDays(1);
```

Nó phù hợp cho tuổi hoặc các quy tắc theo lịch:

```java
LocalDate start = LocalDate.of(2026, 1, 31);
LocalDate next = start.plus(Period.ofMonths(1));
// 2026-02-28
```

`Period.ofMonths(1)` không định nghĩa một số giây cố định vì tháng có độ dài khác nhau.

### Period không tự chuẩn hóa mọi đơn vị

```java
Period p = Period.of(0, 15, 0);
System.out.println(p);              // P15M
System.out.println(p.normalized()); // P1Y3M
```

`normalized()` có thể chuẩn hóa năm/tháng, nhưng ngày không thể đổi tổng quát sang tháng vì độ dài tháng thay đổi.

## <a id="duration-vs-period">Duration và Period: khác biệt về ý nghĩa</a>

Chọn theo câu hỏi:

```text
“Bao nhiêu thời gian thực đã trôi qua?”
→ Duration

“Bao nhiêu ngày/tháng/năm theo lịch?”
→ Period
```

### DST: 24 giờ có thể khác 1 ngày lịch

Ở múi giờ có lần chuyển DST:

```java
ZonedDateTime start = ZonedDateTime.of(
        LocalDateTime.of(2026, 3, 28, 12, 0),
        ZoneId.of("Europe/Paris")
);

ZonedDateTime plus24Hours = start.plus(Duration.ofHours(24));
ZonedDateTime plusOneDay = start.plus(Period.ofDays(1));
```

`plus24Hours` bảo toàn **thời lượng thực 24 giờ đã trôi qua** trên dòng thời gian. `plusOneDay` bảo toàn ý định **sang ngày lịch tiếp theo theo giờ cục bộ**, nên số giây thực tế đã trôi qua có thể tương đương 23 hoặc 25 giờ quanh lần chuyển DST.

Đây là lý do không nên đổi `Period.ofDays(1)` và `Duration.ofHours(24)` cho nhau chỉ vì ở nhiều ngày bình thường chúng cho kết quả cục bộ giống nhau.

### Độ dài tháng cũng phá vỡ giả định “lịch = số giây cố định”

```text
1 tháng
→ 28, 29, 30 hoặc 31 ngày tùy vị trí trên lịch

1 năm
→ có thể chứa ngày nhuận
```

Không có chuyển đổi tổng quát chính xác từ `Period.ofMonths(1)` thành một `Duration` nếu chưa biết điểm bắt đầu và múi giờ/ngữ cảnh cần thiết.

### Quy tắc chọn nhanh

| Nhu cầu | Chọn |
| --- | --- |
| thời gian chờ HTTP 30 giây | `Duration` |
| TTL của bộ nhớ đệm 10 phút | `Duration` |
| thời gian xử lý giữa hai `Instant` | `Duration` |
| cộng 1 tháng vào ngày hóa đơn | `Period` hoặc `plusMonths` |
| tuổi theo năm/tháng/ngày | `Period` |
| lịch chạy “cùng giờ ngày mai” | phép toán theo lịch, thường `Period`/`plusDays` trên giá trị có múi giờ |

Sau khi có đúng giá trị thời gian và lượng, bước tiếp theo là ranh giới rất phổ biến: **văn bản ↔ đối tượng ngày-giờ**.
