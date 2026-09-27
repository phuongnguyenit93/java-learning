# LocalTime

`LocalTime` mô hình hóa **giờ trong một ngày** mà không gắn với ngày cụ thể hay múi giờ. Nó phù hợp với những câu như “cửa hàng mở lúc 08:30” hoặc “job chạy theo lịch nội bộ lúc 23:00”, miễn là rule về zone được quản lý ở nơi khác.

## <a id="local-time-model">Mô hình LocalTime</a>

Ví dụ:

```java
LocalTime openingTime = LocalTime.of(8, 30);
LocalTime closingTime = LocalTime.of(21, 0);
```

`08:30` tự nó không trả lời được:

```text
ngày nào?
ở quốc gia nào?
UTC offset bao nhiêu?
đã xảy ra trên timeline chưa?
```

Đó không phải thiếu sót; đó chính là contract của `LocalTime`.

### Tại sao không tự gắn ngày giả?

Nếu business rule là “quán mở hằng ngày lúc 08:30”, việc tạo `LocalDateTime` với ngày giả như `1970-01-01T08:30` khiến ngày giả có nguy cơ bị serialize hoặc so sánh như dữ liệu thật.

```text
Chỉ biết time-of-day
        ↓
LocalTime
        ↓
khi cần một lịch cụ thể mới kết hợp với LocalDate
```

Kết hợp:

```java
LocalDate date = LocalDate.of(2026, 10, 5);
LocalTime time = LocalTime.of(9, 0);

LocalDateTime meeting = LocalDateTime.of(date, time);
// hoặc date.atTime(time)
```

## <a id="local-time-precision">Độ chính xác đến nanosecond</a>

`LocalTime` có các field:

```text
hour
minute
second
nano-of-second
```

Ví dụ:

```java
LocalTime precise = LocalTime.of(9, 30, 15, 123_456_789);
```

Điều này nghĩa là API **có khả năng biểu diễn** nanosecond, không có nghĩa hệ điều hành, database hay clock thực tế của máy luôn đo được chính xác đến nanosecond.

Đây là distinction quan trọng:

```text
representation precision
≠
measurement accuracy/resolution
```

Khi persistence hoặc giao tiếp qua network, luôn kiểm tra precision mà hệ thống đích hỗ trợ. Một database column chỉ giữ microsecond/millisecond có thể truncate phần nano.

Các constant hữu ích:

```java
LocalTime midnight = LocalTime.MIDNIGHT; // 00:00
LocalTime noon = LocalTime.NOON;         // 12:00
```

Đọc field:

```java
int hour = precise.getHour();
int minute = precise.getMinute();
int second = precise.getSecond();
int nano = precise.getNano();
```

## <a id="local-time-wrap">Arithmetic và việc vòng qua ngày</a>

`LocalTime` không chứa ngày, vì vậy arithmetic vượt qua biên `24:00` sẽ **wrap trong phạm vi một ngày**.

```java
LocalTime late = LocalTime.of(23, 30);
LocalTime later = late.plusHours(2);

System.out.println(later); // 01:30
```

Không có thông tin “ngày mai” trong kết quả vì `LocalTime` không có calendar date để tăng.

Tương tự:

```java
LocalTime time = LocalTime.of(10, 15);

time.plusHours(24);   // 10:15
time.minusMinutes(30); // 09:45
```

### Pitfall khi tính khoảng thời gian qua nửa đêm

Giả sử ca làm bắt đầu `22:00` và kết thúc `06:00`:

```java
LocalTime start = LocalTime.of(22, 0);
LocalTime end = LocalTime.of(6, 0);
```

Chỉ hai `LocalTime` không đủ để biết `06:00` là sáng **cùng ngày** hay **ngày hôm sau**. Nếu domain cần elapsed time chính xác, phải cung cấp ngày hoặc rule rõ ràng.

Ví dụ với ngày:

```java
LocalDate workDate = LocalDate.of(2026, 9, 27);

LocalDateTime startDateTime = workDate.atTime(start);
LocalDateTime endDateTime = workDate.plusDays(1).atTime(end);

Duration duration = Duration.between(startDateTime, endDateTime);
```

Ở đây `Duration` chỉ được dùng để minh họa “elapsed amount” sau khi đã bổ sung ngày. `Duration` là gì, khác `Period` thế nào và vì sao `24 hours` không luôn đồng nghĩa `1 calendar day` sẽ được giải thích đầy đủ ở chapter `Duration / Period`.

Tuy nhiên nếu ca làm liên quan một region có DST, `LocalDateTime` vẫn chưa đủ cho elapsed time tuyệt đối; lúc đó cần `ZoneId`/`ZonedDateTime` hoặc `Instant`.

### Khi nào LocalTime phù hợp?

Dùng `LocalTime` cho **wall-clock rule** không tự nó chỉ tới timeline: giờ mở cửa, giờ deadline trong ngày, thời điểm bắt đầu một lịch lặp. Khi câu hỏi chuyển thành “sự kiện thật sự xảy ra lúc nào trên toàn cầu?”, `LocalTime` phải được kết hợp với date và zone.
