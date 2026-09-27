# LocalDate

Sau khi tách được “ngày”, “giờ” và “instant”, type dễ bắt đầu nhất là `LocalDate`: một ngày trên lịch ISO-8601 mà **không có giờ trong ngày và không có múi giờ**.

## <a id="local-date-model">Mô hình LocalDate</a>

`LocalDate` phù hợp khi business fact là một **calendar date**.

```java
LocalDate birthday = LocalDate.of(1993, 7, 20);
LocalDate invoiceDate = LocalDate.of(2026, 9, 27);
```

Hai value trên chứa các field như year, month, day-of-month. Chúng không chứa `hour`, `minute`, `ZoneId` hay `ZoneOffset`.

### Vì sao không dùng LocalDateTime cho mọi thứ?

Ta có thể giả tạo giờ `00:00`:

```java
LocalDateTime birthday = LocalDateTime.of(1993, 7, 20, 0, 0);
```

Nhưng lúc này model đang nói thêm một điều mà domain không sở hữu: “sinh nhật xảy ra lúc nửa đêm”. Khi serialize, query database hoặc convert zone, thông tin giả đó có thể tạo ra bug và câu hỏi vô nghĩa.

Model tốt hơn:

```text
Domain chỉ biết ngày
        ↓
LocalDate
        ↓
chỉ thêm time/zone khi một use case thật sự cần
```

### Tạo LocalDate

Các cách phổ biến:

```java
LocalDate explicit = LocalDate.of(2026, 9, 27);
LocalDate parsed = LocalDate.parse("2026-09-27");
LocalDate today = LocalDate.now();
```

`LocalDate.now()` phụ thuộc clock/zone của môi trường. Trong business logic cần test deterministic, chapter `Clock` sẽ chỉ ra cách tránh gọi `now()` rải rác.

Đọc field:

```java
int year = explicit.getYear();
Month month = explicit.getMonth();
int monthValue = explicit.getMonthValue();
int day = explicit.getDayOfMonth();
DayOfWeek dayOfWeek = explicit.getDayOfWeek();
```

## <a id="local-date-arithmetic">Tính toán với ngày</a>

Vì `LocalDate` immutable, arithmetic luôn trả value mới:

```java
LocalDate date = LocalDate.of(2026, 9, 27);

LocalDate tomorrow = date.plusDays(1);
LocalDate nextWeek = date.plusWeeks(1);
LocalDate nextMonth = date.plusMonths(1);
LocalDate lastYear = date.minusYears(1);
```

Ngoài các method tiện ích như `plusDays`, Java còn có những abstraction chung cho amount/unit. Đoạn dưới chỉ là **preview**; `Period` sẽ được giải thích đầy đủ ở chapter `Duration / Period`, còn `ChronoUnit` ở chapter `Arithmetic / Comparison`:

```java
LocalDate afterTenDays = date.plus(Period.ofDays(10));
long days = ChronoUnit.DAYS.between(date, afterTenDays);
```

Điểm cần hiểu là đây là **calendar arithmetic**. `plusDays(1)` nghĩa là sang ngày lịch tiếp theo, không phải khẳng định “đã trôi qua đúng 24 giờ trên timeline”. Sự khác biệt này trở nên quan trọng khi date được kết hợp với zone có DST.

### Điều chỉnh theo field

```java
LocalDate date = LocalDate.of(2026, 9, 27);

LocalDate firstDay = date.withDayOfMonth(1);
LocalDate december = date.withMonth(12);
```

Với các rule lịch phức tạp hơn, Java có `TemporalAdjusters`:

```java
LocalDate lastDayOfMonth = date.with(TemporalAdjusters.lastDayOfMonth());
LocalDate nextMonday = date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
```

Đừng biến `TemporalAdjusters` thành business calendar. “Ngày làm việc tiếp theo” còn phụ thuộc cuối tuần, ngày lễ, quốc gia và policy công ty; đó là policy riêng của application.

## <a id="calendar-validity">Ngày hợp lệ và hành vi cuối tháng</a>

`LocalDate` luôn đại diện cho một ngày hợp lệ của calendar model mà nó sử dụng. Java không âm thầm giữ một value như “31/02”.

```java
LocalDate.of(2026, 2, 30); // DateTimeException
```

### Leap year

```java
LocalDate leapDay = LocalDate.of(2024, 2, 29);
boolean leap = leapDay.isLeapYear();
```

`2025-02-29` không hợp lệ và sẽ fail khi tạo theo cách strict của `LocalDate.of`.

### Cộng tháng ở ngày cuối tháng

Một case dễ gây bất ngờ:

```java
LocalDate january31 = LocalDate.of(2026, 1, 31);
LocalDate result = january31.plusMonths(1);
```

Tháng 2/2026 không có ngày 31. `plusMonths` điều chỉnh về ngày hợp lệ cuối cùng của tháng đích, nên kết quả là `2026-02-28`.

Mental model:

```text
2026-01-31
    + 1 calendar month
        ↓
month đích = 2026-02
        ↓
31 không hợp lệ
        ↓
chọn ngày hợp lệ cuối cùng = 28
```

Điều này khác hoàn toàn với việc cộng một số giây cố định.

### Khi nào LocalDate là lựa chọn đúng?

Dùng `LocalDate` khi câu trả lời cho câu hỏi sau là “không”:

```text
Giá trị này có cần biết giờ trong ngày không?
Giá trị này có cần xác định một instant toàn cầu không?
```

Ví dụ: sinh nhật, ngày phát hành hóa đơn, ngày hết hạn theo lịch, ngày nghỉ lễ. Nếu chương trình bắt đầu cần “mấy giờ” hoặc “ở zone nào”, đó là dấu hiệu chuyển sang type khác thay vì nhồi thêm convention vào `LocalDate`.
