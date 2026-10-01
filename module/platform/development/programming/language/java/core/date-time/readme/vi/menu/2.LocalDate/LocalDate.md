# LocalDate

Sau khi tách được “ngày”, “giờ” và “Instant”, kiểu dễ bắt đầu nhất là `LocalDate`: một ngày trên lịch ISO-8601 mà **không có giờ trong ngày và không có múi giờ**.

## <a id="local-date-model">Mô hình LocalDate</a>

`LocalDate` phù hợp khi thông tin nghiệp vụ chỉ là một **ngày theo lịch**.

```java
LocalDate birthday = LocalDate.of(1993, 7, 20);
LocalDate invoiceDate = LocalDate.of(2026, 9, 27);
```

Hai giá trị trên chứa các trường năm, tháng và ngày trong tháng. Chúng không chứa giờ, phút, `ZoneId` hay `ZoneOffset`.

### Vì sao không dùng LocalDateTime cho mọi thứ?

Ta có thể giả tạo giờ `00:00`:

```java
LocalDateTime birthday = LocalDateTime.of(1993, 7, 20, 0, 0);
```

Nhưng lúc này mô hình đang nói thêm một điều mà nghiệp vụ không sở hữu: “sinh nhật xảy ra lúc nửa đêm”. Khi tuần tự hóa hoặc truy vấn cơ sở dữ liệu hoặc chuyển đổi múi giờ, thông tin giả đó có thể tạo ra lỗi và câu hỏi vô nghĩa.

Mô hình tốt hơn:

```text
Nghiệp vụ chỉ biết ngày
        ↓
LocalDate
        ↓
chỉ thêm giờ/múi giờ khi một trường hợp sử dụng thật sự cần
```

### Tạo LocalDate

Các cách phổ biến:

```java
LocalDate explicit = LocalDate.of(2026, 9, 27);
LocalDate parsed = LocalDate.parse("2026-09-27");
LocalDate today = LocalDate.now();
```

`LocalDate.now()` phụ thuộc đồng hồ/múi giờ của môi trường. Trong logic nghiệp vụ cần kiểm thử có kết quả xác định, chương `Clock` sẽ chỉ ra cách tránh gọi `now()` rải rác.

Đọc trường:

```java
int year = explicit.getYear();
Month month = explicit.getMonth();
int monthValue = explicit.getMonthValue();
int day = explicit.getDayOfMonth();
DayOfWeek dayOfWeek = explicit.getDayOfWeek();
```

## <a id="local-date-arithmetic">Tính toán với ngày</a>

Vì `LocalDate` bất biến, phép toán luôn trả giá trị mới:

```java
LocalDate date = LocalDate.of(2026, 9, 27);

LocalDate tomorrow = date.plusDays(1);
LocalDate nextWeek = date.plusWeeks(1);
LocalDate nextMonth = date.plusMonths(1);
LocalDate lastYear = date.minusYears(1);
```

Ngoài các phương thức tiện ích như `plusDays`, Java còn có những API dùng chung cho lượng và đơn vị thời gian. Đoạn dưới chỉ là **giới thiệu trước**; `Period` sẽ được giải thích đầy đủ ở chương `Duration / Period`, còn `ChronoUnit` ở chương **Phép toán / So sánh**:

```java
LocalDate afterTenDays = date.plus(Period.ofDays(10));
long days = ChronoUnit.DAYS.between(date, afterTenDays);
```

Điểm cần hiểu là đây là **phép toán theo lịch**. `plusDays(1)` nghĩa là sang ngày lịch tiếp theo, không phải khẳng định “đã trôi qua đúng 24 giờ trên dòng thời gian”. Sự khác biệt này trở nên quan trọng khi ngày được kết hợp với múi giờ có DST.

### Điều chỉnh theo trường dữ liệu

```java
LocalDate date = LocalDate.of(2026, 9, 27);

LocalDate firstDay = date.withDayOfMonth(1);
LocalDate december = date.withMonth(12);
```

Với các quy tắc lịch phức tạp hơn, Java có `TemporalAdjusters`:

```java
LocalDate lastDayOfMonth = date.with(TemporalAdjusters.lastDayOfMonth());
LocalDate nextMonday = date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
```

Đừng biến `TemporalAdjusters` thành lịch nghiệp vụ. “Ngày làm việc tiếp theo” còn phụ thuộc cuối tuần, ngày lễ, quốc gia và chính sách công ty; đó là chính sách riêng của ứng dụng.

## <a id="calendar-validity">Ngày hợp lệ và hành vi cuối tháng</a>

`LocalDate` luôn đại diện cho một ngày hợp lệ của mô hình lịch mà nó sử dụng. Java không âm thầm giữ một giá trị như “31/02”.

```java
LocalDate.of(2026, 2, 30); // DateTimeException
```

### Năm nhuận

```java
LocalDate leapDay = LocalDate.of(2024, 2, 29);
boolean leap = leapDay.isLeapYear();
```

`2025-02-29` không hợp lệ; gọi `LocalDate.of(2025, 2, 29)` sẽ ném `DateTimeException`.

### Cộng tháng ở ngày cuối tháng

Một trường hợp dễ gây bất ngờ:

```java
LocalDate january31 = LocalDate.of(2026, 1, 31);
LocalDate result = january31.plusMonths(1);
```

Tháng 2/2026 không có ngày 31. `plusMonths` điều chỉnh về ngày hợp lệ cuối cùng của tháng đích, nên kết quả là `2026-02-28`.

Mô hình tư duy:

```text
2026-01-31
    + 1 tháng theo lịch
        ↓
tháng đích = 2026-02
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
Giá trị này có cần xác định một `Instant` toàn cục không?
```

Ví dụ: sinh nhật, ngày phát hành hóa đơn, ngày hết hạn theo lịch, ngày nghỉ lễ. Nếu chương trình bắt đầu cần “mấy giờ” hoặc “ở múi giờ nào”, đó là dấu hiệu chuyển sang kiểu khác thay vì nhồi thêm quy ước vào `LocalDate`.
