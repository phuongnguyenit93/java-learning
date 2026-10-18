# LocalDateTime

`LocalDateTime` kết hợp `LocalDate` và `LocalTime`: nó biết **ngày nào** và **mấy giờ**, nhưng vẫn cố ý không biết `ZoneId` hay `ZoneOffset`.

Đây là kiểu rất hữu ích nhưng cũng rất dễ bị dùng nhầm như một dấu thời gian toàn cầu.

## <a id="local-date-time-model">Mô hình LocalDateTime</a>

Ví dụ:

```java
LocalDateTime meeting = LocalDateTime.of(2026, 10, 5, 9, 0);
```

Giá trị trên nói:

```text
ngày = 2026-10-05
giờ = 09:00
```

Nó **không nói**:

```text
09:00 ở đâu?
độ lệch so với UTC là bao nhiêu?
đó là `Instant` nào?
```

Có thể tạo bằng cách ghép hai khái niệm đã học:

```java
LocalDate date = LocalDate.of(2026, 10, 5);
LocalTime time = LocalTime.of(9, 0);

LocalDateTime a = LocalDateTime.of(date, time);
LocalDateTime b = date.atTime(time);
```

Các phép toán vẫn giữ tính bất biến:

```java
LocalDateTime rescheduled = meeting.plusDays(1).withHour(10);
```

## <a id="local-date-time-ambiguity">Vì sao LocalDateTime không phải Instant?</a>

Đây là điểm phân biệt quan trọng nhất của chương.

Giá trị:

```text
2026-10-05T09:00
```

có thể ánh xạ tới nhiều `Instant` khác nhau:

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

Cùng các trường cục bộ nhưng vì quy tắc múi giờ khác nhau, hai kết quả trên là hai điểm khác nhau trên dòng thời gian.

Mô hình tư duy:

```text
LocalDateTime
    + ZoneId
        ↓
phân giải các trường cục bộ theo quy tắc múi giờ
        ↓
ZonedDateTime
        ↓
Instant
```

### DST làm sự mơ hồ của LocalDateTime rõ hơn

Ở các vùng có áp dụng giờ mùa hè (DST), một ngày-giờ cục bộ có thể rơi vào:

```text
trường hợp bình thường
→ đúng một độ lệch hợp lệ

khoảng trống (gap)
→ đồng hồ cục bộ nhảy qua một khoảng
→ một số giờ cục bộ không tồn tại

chồng lặp (overlap)
→ đồng hồ quay lại
→ một số giờ cục bộ xảy ra hai lần với hai độ lệch khác nhau
```

Vì `LocalDateTime` không giữ quy tắc múi giờ, bản thân nó không thể giải quyết khoảng trống/chồng lặp. Chương `ZoneOffset / ZoneId` giới thiệu nguyên nhân; chương **Phép toán / So sánh** sẽ đi sâu cách Java phân giải các trường hợp này.

### Sai lầm phổ biến: dùng LocalDateTime cho createdAt

```java
class Order {
    LocalDateTime createdAt;
}
```

Nếu `createdAt` cần biểu diễn một sự kiện đã xảy ra trên hệ thống phân tán, chỉ `LocalDateTime` có thể làm mất ngữ cảnh múi giờ và khiến hai máy chủ ở hai vùng khó so sánh chính xác. `Instant` thường phù hợp hơn cho loại dấu thời gian hướng hệ thống đó.

## <a id="local-date-time-use-cases">Khi nào LocalDateTime phù hợp?</a>

`LocalDateTime` đúng khi nghiệp vụ thật sự sở hữu **các trường lịch cục bộ** nhưng ánh xạ lên dòng thời gian chưa tồn tại hoặc không phải điều cần biểu diễn ở bước đó.

Ví dụ:

### 1. Người dùng nhập lịch trước khi chọn địa điểm

```java
LocalDateTime requestedSlot = LocalDateTime.of(2026, 10, 5, 9, 0);
```

Sau đó người dùng chọn múi giờ:

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime scheduled = requestedSlot.atZone(zone);
```

### 2. Nghiệp vụ cố ý dùng thời gian dân sự cục bộ

**Thời gian dân sự cục bộ (local civil time)** là ngày/giờ theo đồng hồ và lịch mà con người tại một nơi sử dụng cho sinh hoạt/nghiệp vụ, trước khi ta ánh xạ nó thành một `Instant` toàn cục. Một quy tắc nghiệp vụ có thể nói “chốt sổ lúc 23:00 ngày cuối tháng theo lịch nghiệp vụ cục bộ”. Các trường cục bộ là phần của quy tắc; múi giờ có thể được cấu hình ở một ranh giới khác.

### 3. Dữ liệu cơ sở dữ liệu mang nghĩa ngày-giờ cục bộ

Nếu cột cơ sở dữ liệu thật sự có ý nghĩa “dấu thời gian không kèm múi giờ”, `LocalDateTime` thường là ánh xạ tự nhiên hơn `Instant`. Nhưng phải chắc rằng ứng dụng không nhầm cột đó với một `Instant` toàn cục.

### Khi nào không nên dùng?

Không dùng `LocalDateTime` chỉ vì định dạng đầu vào trông như:

```text
2026-10-05 09:00:00
```

**Chuỗi định dạng không quyết định ý nghĩa.** Hãy hỏi dữ liệu muốn nói gì.

```text
Dấu thời gian kiểm toán (audit)? → thường Instant
Cuộc họp ở vùng thực?      → ZonedDateTime / cục bộ + ZoneId
Ngày + giờ chưa có múi giờ?→ LocalDateTime
```

Chương tiếp theo chuyển sang `Instant`, vì sau khi hiểu ngày-giờ cục bộ chưa phải mốc trên dòng thời gian, ta cần một kiểu đại diện cho **một thời điểm toàn cầu không mơ hồ**.
