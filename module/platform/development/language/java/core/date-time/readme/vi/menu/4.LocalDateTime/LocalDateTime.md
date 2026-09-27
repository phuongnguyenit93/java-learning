# LocalDateTime

`LocalDateTime` kết hợp `LocalDate` và `LocalTime`: nó biết **ngày nào** và **mấy giờ**, nhưng vẫn cố ý không biết `ZoneId` hay `ZoneOffset`.

Đây là type rất hữu ích nhưng cũng rất dễ bị dùng nhầm như một timestamp toàn cầu.

## <a id="local-date-time-model">Mô hình LocalDateTime</a>

Ví dụ:

```java
LocalDateTime meeting = LocalDateTime.of(2026, 10, 5, 9, 0);
```

Value trên nói:

```text
ngày = 2026-10-05
giờ = 09:00
```

Nó **không nói**:

```text
09:00 ở đâu?
offset so với UTC là bao nhiêu?
đó là instant nào?
```

Có thể tạo bằng cách ghép hai concept đã học:

```java
LocalDate date = LocalDate.of(2026, 10, 5);
LocalTime time = LocalTime.of(9, 0);

LocalDateTime a = LocalDateTime.of(date, time);
LocalDateTime b = date.atTime(time);
```

Arithmetic vẫn immutable:

```java
LocalDateTime rescheduled = meeting.plusDays(1).withHour(10);
```

## <a id="local-date-time-ambiguity">Vì sao LocalDateTime không phải Instant?</a>

Đây là distinction quan trọng nhất của chapter.

Giá trị:

```text
2026-10-05T09:00
```

có thể map tới nhiều instant khác nhau:

```java
LocalDateTime local = LocalDateTime.of(2026, 10, 5, 9, 0);

Instant vietnam = local
        .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
        .toInstant();

Instant paris = local
        .atZone(ZoneId.of("Europe/Paris"))
        .toInstant();

System.out.println(vietnam);
System.out.println(paris);
```

Cùng local fields nhưng vì zone rules khác nhau, hai kết quả trên là hai điểm khác nhau trên timeline.

Mental model:

```text
LocalDateTime
    + ZoneId
        ↓
resolve local fields theo zone rules
        ↓
ZonedDateTime
        ↓
Instant
```

### DST làm ambiguity rõ hơn

Ở các region có daylight saving time, một local date-time có thể rơi vào:

```text
normal case
→ đúng một offset hợp lệ

gap
→ local clock nhảy qua một khoảng
→ một số local time không tồn tại

overlap
→ clock quay lại
→ một số local time xảy ra hai lần với hai offset khác nhau
```

Vì `LocalDateTime` không giữ zone rules, bản thân nó không thể giải quyết gap/overlap. Chapter về `ZoneId` và pitfalls sẽ đi sâu phần này.

### Sai lầm phổ biến: dùng LocalDateTime cho createdAt

```java
class Order {
    LocalDateTime createdAt;
}
```

Nếu `createdAt` cần biểu diễn một sự kiện đã xảy ra trên hệ thống phân tán, chỉ `LocalDateTime` có thể làm mất context zone và khiến hai server ở hai vùng khó so sánh chính xác. `Instant` thường phù hợp hơn cho loại timestamp machine-oriented đó.

## <a id="local-date-time-use-cases">Khi nào LocalDateTime phù hợp?</a>

`LocalDateTime` đúng khi domain thật sự sở hữu **local calendar fields** nhưng timeline mapping chưa tồn tại hoặc không phải điều cần biểu diễn ở bước đó.

Ví dụ:

### 1. Người dùng nhập lịch trước khi chọn địa điểm

```java
LocalDateTime requestedSlot = LocalDateTime.of(2026, 10, 5, 9, 0);
```

Sau đó người dùng chọn zone:

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime scheduled = requestedSlot.atZone(zone);
```

### 2. Domain cố ý dùng local civil time

**Local civil time** là ngày/giờ theo đồng hồ và lịch mà con người tại một nơi sử dụng cho sinh hoạt/business, trước khi ta ánh xạ nó thành một global instant. Một business rule có thể nói “chốt sổ lúc 23:00 ngày cuối tháng theo local business calendar”. Local fields là phần của rule; zone có thể được cấu hình ở một boundary khác.

### 3. Dữ liệu database loại local timestamp

Nếu database column thật sự có semantics “timestamp without time zone”, `LocalDateTime` thường là mapping tự nhiên hơn `Instant`. Nhưng phải chắc rằng application không nhầm column đó với một global instant.

### Khi nào không nên dùng?

Không dùng `LocalDateTime` chỉ vì format input trông như:

```text
2026-10-05 09:00:00
```

**Text format không quyết định semantics.** Hãy hỏi dữ liệu muốn nói gì.

```text
Audit timestamp?          → thường Instant
Meeting in a real region? → ZonedDateTime / local + ZoneId
Date + time chưa có zone? → LocalDateTime
```

Chapter tiếp theo chuyển sang `Instant`, vì sau khi hiểu local date-time chưa phải timeline point, ta cần một type đại diện cho **một thời điểm toàn cầu không mơ hồ**.
