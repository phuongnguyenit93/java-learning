# OffsetDateTime, ZonedDateTime và chuyển đổi múi giờ

Sau khi đã có các trường cục bộ, độ lệch UTC và quy tắc múi giờ, Java cung cấp hai kiểu giá trị quan trọng để mang ngữ cảnh đó cùng ngày-giờ: `ZonedDateTime` và `OffsetDateTime`.

## <a id="zoned-date-time">ZonedDateTime — ngày-giờ cục bộ + ZoneId + độ lệch đã xác định</a>

`ZonedDateTime` mô hình hóa một ngày-giờ gắn với **một `ZoneId`**. Trong ứng dụng lập lịch, `ZoneId` thường là vùng có tên như `Asia/Ho_Chi_Minh` hoặc `Europe/Paris`, nhưng về mặt API nó cũng có thể là một múi giờ biểu diễn bằng độ lệch cố định.

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime meeting = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, zone);
```

Về mặt khái niệm:

```text
Các trường LocalDateTime
        +
ZoneId
        ↓ phân giải bằng ZoneRules
ZoneOffset hợp lệ tại thời điểm đó
        ↓
ZonedDateTime
```

Một `ZonedDateTime` vì thế có thể trả cả:

```java
LocalDateTime local = meeting.toLocalDateTime();
ZoneId zoneId = meeting.getZone();
ZoneOffset offset = meeting.getOffset();
Instant instant = meeting.toInstant();
```

Đây là lựa chọn tự nhiên khi nghiệp vụ nói “09:00 tại Europe/Paris”, “17:00 tại America/New_York”, hoặc nói chung cần giữ ngữ cảnh múi giờ cùng ngày-giờ cục bộ. Khi `ZoneId` là theo vùng, giá trị còn có thể sử dụng quy tắc của vùng thay đổi theo thời gian.

## <a id="offset-date-time">OffsetDateTime — ngày-giờ cục bộ + độ lệch cụ thể</a>

`OffsetDateTime` giữ các trường cục bộ cùng **một `ZoneOffset` cụ thể**, nhưng không giữ danh tính vùng có tên và bộ quy tắc của vùng.

```java
OffsetDateTime value = OffsetDateTime.of(
        2026, 10, 5,
        9, 0, 0, 0,
        ZoneOffset.ofHours(7)
);
```

Nó vẫn xác định được một `Instant` vì ngày-giờ cục bộ + độ lệch là đủ:

```java
Instant instant = value.toInstant();
```

Nhưng từ giá trị đó không thể kết luận vùng là `Asia/Ho_Chi_Minh`.

### Chọn ZonedDateTime hay OffsetDateTime?

```text
Nghiệp vụ cần vùng có tên và quy tắc theo thời gian?
→ ZonedDateTime

Ranh giới chỉ cung cấp ngày-giờ cục bộ + độ lệch cụ thể?
→ OffsetDateTime

Chỉ cần mốc toàn cục để lưu/so sánh?
→ cân nhắc Instant
```

Giao thức/API thường truyền dấu thời gian có độ lệch rất tốt, nhưng ứng dụng lập lịch có thể vẫn cần `ZoneId` riêng nếu quy tắc tương lai là thông tin quan trọng.

## <a id="same-instant-vs-same-local">Cùng Instant và cùng ngày-giờ cục bộ là hai ý khác nhau</a>

Hai giá trị có múi giờ có thể khác đồng hồ cục bộ nhưng cùng `Instant`:

```java
ZonedDateTime vietnam = ZonedDateTime.of(
        2026, 10, 5, 9, 0, 0, 0,
        ZoneId.of("Asia/Ho_Chi_Minh")
);

ZonedDateTime parisSameInstant = vietnam.withZoneSameInstant(
        ZoneId.of("Europe/Paris")
);
```

Hai đối tượng hiển thị các trường cục bộ khác nhau nhưng:

```java
vietnam.toInstant().equals(parisSameInstant.toInstant()); // true
```

Ngược lại, ta có thể cố giữ cùng các trường cục bộ ở hai múi giờ:

```text
09:00 Vietnam
09:00 Paris
```

thì phần lớn chúng là **hai `Instant` khác nhau**.

Mô hình tư duy:

```text
cùng Instant
→ giữ vị trí trên dòng thời gian
→ đồng hồ cục bộ phải thay đổi khi đổi múi giờ

cùng giá trị cục bộ
→ giữ các trường lịch/đồng hồ
→ vị trí trên dòng thời gian có thể thay đổi
```

Đây là lý do “chuyển đổi múi giờ” phải nói rõ muốn bảo toàn điều gì.

## <a id="zone-conversion">Chuyển đổi múi giờ có chủ đích</a>

### Giữ nguyên Instant

```java
ZonedDateTime source = ZonedDateTime.of(
        2026, 10, 5, 9, 0, 0, 0,
        ZoneId.of("Asia/Ho_Chi_Minh")
);

ZonedDateTime parisView = source.withZoneSameInstant(
        ZoneId.of("Europe/Paris")
);
```

Dùng khi đang hiển thị **cùng một sự kiện** cho người dùng ở múi giờ khác.

### Giữ nguyên các trường ngày-giờ cục bộ

```java
ZonedDateTime reinterpreted = source.withZoneSameLocal(
        ZoneId.of("Europe/Paris")
);
```

Thao tác này giữ ngày/giờ cục bộ càng nguyên vẹn càng tốt rồi phân giải theo múi giờ mới; vì vậy `Instant` thường thay đổi. Đây không phải “đổi cách hiển thị” cùng một sự kiện, mà là **diễn giải lại** lịch cục bộ trong vùng khác.

### Chuyển từ Instant sang cách hiển thị cục bộ

```java
Instant eventTime = Instant.parse("2026-10-05T02:00:00Z");

ZonedDateTime vietnamView = eventTime.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
ZonedDateTime parisView = eventTime.atZone(ZoneId.of("Europe/Paris"));
```

Cả hai cách hiển thị giữ cùng một sự kiện trên dòng thời gian.

### OffsetDateTime cũng có hai cách chuyển đổi tương ứng

Cùng mô hình tư duy áp dụng cho `OffsetDateTime`:

```java
OffsetDateTime original = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");

OffsetDateTime sameInstant = original.withOffsetSameInstant(
        ZoneOffset.ofHours(2)
);
// 2026-10-05T04:00+02:00 → cùng Instant

OffsetDateTime sameLocal = original.withOffsetSameLocal(
        ZoneOffset.ofHours(2)
);
// 2026-10-05T09:00+02:00 → cùng trường cục bộ, Instant thay đổi
```

```text
withOffsetSameInstant
→ giữ mốc trên dòng thời gian, đổi đồng hồ cục bộ để phù hợp độ lệch mới

withOffsetSameLocal
→ giữ các trường cục bộ, diễn giải lại chúng dưới độ lệch mới
```

Vì `OffsetDateTime` không có quy tắc của vùng, đây chỉ là đổi giữa các độ lệch cụ thể; không có bước tra quy tắc DST như khi đổi `ZoneId` theo vùng.

Khi làm việc với khoảng trống/chồng lặp DST, việc phân giải các trường cục bộ có thêm quy tắc đặc biệt; chương **Phép toán / So sánh** sẽ giải thích chi tiết. Trước đó, ta cần phân biệt hai kiểu “khoảng thời gian”: `Duration` và `Period`.
