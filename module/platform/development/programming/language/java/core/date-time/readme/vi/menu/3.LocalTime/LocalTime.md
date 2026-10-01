# LocalTime

`LocalTime` mô hình hóa **giờ trong một ngày** mà không gắn với ngày cụ thể hay múi giờ. Nó phù hợp với những câu như “cửa hàng mở lúc 08:30” hoặc “tác vụ chạy theo lịch nội bộ lúc 23:00”, miễn là quy tắc về múi giờ được quản lý ở nơi khác.

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
Độ lệch UTC bao nhiêu?
đã xảy ra trên dòng thời gian chưa?
```

Đó không phải thiếu sót; đó chính là hợp đồng của `LocalTime`.

### Tại sao không tự gắn ngày giả?

Nếu quy tắc nghiệp vụ là “quán mở hằng ngày lúc 08:30”, việc tạo `LocalDateTime` với ngày giả như `1970-01-01T08:30` khiến ngày giả có nguy cơ bị tuần tự hóa hoặc so sánh như dữ liệu thật.

```text
Chỉ biết giờ trong ngày
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

## <a id="local-time-precision">Độ chính xác tới nano giây</a>

`LocalTime` có các trường:

```text
giờ
phút
giây
nano giây trong giây
```

Ví dụ:

```java
LocalTime precise = LocalTime.of(9, 30, 15, 123_456_789);
```

Điều này nghĩa là API **có khả năng biểu diễn** tới nano giây, không có nghĩa hệ điều hành, cơ sở dữ liệu hay đồng hồ thực tế của máy luôn đo được chính xác tới nano giây.

Đây là điểm phân biệt quan trọng:

```text
độ chính xác khi biểu diễn
≠
độ chính xác / độ phân giải khi đo
```

Khi lưu trữ hoặc giao tiếp qua mạng, luôn kiểm tra độ chính xác mà hệ thống đích hỗ trợ. Một cột cơ sở dữ liệu chỉ giữ micro giây/mili giây có thể làm mất phần nano giây.

Các hằng số hữu ích:

```java
LocalTime midnight = LocalTime.MIDNIGHT; // 00:00
LocalTime noon = LocalTime.NOON;         // 12:00
```

Đọc trường:

```java
int hour = precise.getHour();
int minute = precise.getMinute();
int second = precise.getSecond();
int nano = precise.getNano();
```

## <a id="local-time-wrap">Phép toán và việc vòng qua ngày</a>

`LocalTime` không chứa ngày, vì vậy phép toán vượt qua biên `24:00` sẽ **vòng lại trong phạm vi một ngày**.

```java
LocalTime late = LocalTime.of(23, 30);
LocalTime later = late.plusHours(2);

System.out.println(later); // 01:30
```

Không có thông tin “ngày mai” trong kết quả vì `LocalTime` không có ngày theo lịch để tăng.

Tương tự:

```java
LocalTime time = LocalTime.of(10, 15);

time.plusHours(24);   // 10:15
time.minusMinutes(30); // 09:45
```

### Bẫy khi tính khoảng thời gian qua nửa đêm

Giả sử ca làm bắt đầu `22:00` và kết thúc `06:00`:

```java
LocalTime start = LocalTime.of(22, 0);
LocalTime end = LocalTime.of(6, 0);
```

Chỉ hai `LocalTime` không đủ để biết `06:00` là sáng **cùng ngày** hay **ngày hôm sau**. Nếu nghiệp vụ cần thời lượng đã trôi qua chính xác, phải cung cấp ngày hoặc quy tắc rõ ràng.

Ví dụ với ngày:

```java
LocalDate workDate = LocalDate.of(2026, 9, 27);

LocalDateTime startDateTime = workDate.atTime(start);
LocalDateTime endDateTime = workDate.plusDays(1).atTime(end);

Duration duration = Duration.between(startDateTime, endDateTime);
```

Ở đây `Duration` chỉ được dùng để minh họa “lượng thời gian đã trôi qua” sau khi đã bổ sung ngày. `Duration` là gì, khác `Period` thế nào và vì sao `24 giờ` không luôn đồng nghĩa `1 ngày theo lịch` sẽ được giải thích đầy đủ ở chương `Duration / Period`.

Tuy nhiên nếu ca làm liên quan một vùng có DST, `LocalDateTime` vẫn chưa đủ cho thời lượng đã trôi qua tuyệt đối; lúc đó cần `ZoneId`/`ZonedDateTime` hoặc `Instant`.

### Khi nào LocalTime phù hợp?

Dùng `LocalTime` cho **quy tắc theo đồng hồ cục bộ** không tự nó chỉ tới dòng thời gian: giờ mở cửa, giờ hạn chót trong ngày, thời điểm bắt đầu một lịch lặp. Khi câu hỏi chuyển thành “sự kiện thật sự xảy ra lúc nào trên toàn cầu?”, `LocalTime` phải được kết hợp với ngày và múi giờ.
