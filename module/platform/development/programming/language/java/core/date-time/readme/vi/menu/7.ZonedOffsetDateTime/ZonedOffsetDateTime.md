# ZonedDateTime và OffsetDateTime

Sau khi đã có local fields, offset và zone rules, Java cung cấp hai value type quan trọng để mang context đó cùng date-time: `ZonedDateTime` và `OffsetDateTime`.

## <a id="zoned-date-time">ZonedDateTime — local date-time + ZoneId + resolved offset</a>

`ZonedDateTime` mô hình hóa một date-time gắn với **một `ZoneId`**. Trong application scheduling, `ZoneId` thường là named region như `Asia/Ho_Chi_Minh` hoặc `Europe/Paris`, nhưng về mặt API nó cũng có thể là một fixed-offset zone.

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime meeting = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, zone);
```

Conceptually:

```text
LocalDateTime fields
        +
ZoneId
        ↓ resolve bằng ZoneRules
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

Đây là lựa chọn tự nhiên khi domain nói “09:00 tại Europe/Paris”, “17:00 tại America/New_York”, hoặc nói chung cần giữ zone context cùng local view. Khi `ZoneId` là region-based, value còn có thể sử dụng region rules thay đổi theo thời gian.

## <a id="offset-date-time">OffsetDateTime — local date-time + offset cụ thể</a>

`OffsetDateTime` giữ local fields cùng **một `ZoneOffset` cụ thể**, nhưng không giữ named region rules.

```java
OffsetDateTime value = OffsetDateTime.of(
        2026, 10, 5,
        9, 0, 0, 0,
        ZoneOffset.ofHours(7)
);
```

Nó vẫn xác định được một instant vì local date-time + offset là đủ:

```java
Instant instant = value.toInstant();
```

Nhưng từ value đó không thể kết luận region là `Asia/Ho_Chi_Minh`.

### Chọn ZonedDateTime hay OffsetDateTime?

```text
Domain cần named region và rule theo thời gian?
→ ZonedDateTime

Boundary chỉ cung cấp local date-time + concrete offset?
→ OffsetDateTime

Chỉ cần global point để lưu/so sánh?
→ cân nhắc Instant
```

Protocol/API thường truyền timestamp có offset rất tốt, nhưng application scheduling có thể vẫn cần `ZoneId` riêng nếu future rule semantics quan trọng.

## <a id="same-instant-vs-same-local">Cùng instant và cùng local time là hai ý khác nhau</a>

Hai zoned date-time có thể khác local clock nhưng cùng instant:

```java
ZonedDateTime vietnam = ZonedDateTime.of(
        2026, 10, 5, 9, 0, 0, 0,
        ZoneId.of("Asia/Ho_Chi_Minh")
);

ZonedDateTime parisSameInstant = vietnam.withZoneSameInstant(
        ZoneId.of("Europe/Paris")
);
```

Hai object hiển thị local fields khác nhau nhưng:

```java
vietnam.toInstant().equals(parisSameInstant.toInstant()); // true
```

Ngược lại, ta có thể cố giữ cùng local fields ở hai zone:

```text
09:00 Vietnam
09:00 Paris
```

thì phần lớn chúng là **hai instant khác nhau**.

Mental model:

```text
same instant
→ giữ vị trí timeline
→ local clock phải thay đổi khi đổi zone

same local
→ giữ các field calendar/clock
→ timeline position có thể thay đổi
```

Đây là lý do “convert zone” phải nói rõ muốn bảo toàn điều gì.

## <a id="zone-conversion">Chuyển zone có chủ đích</a>

### Giữ nguyên instant

```java
ZonedDateTime source = ZonedDateTime.of(
        2026, 10, 5, 9, 0, 0, 0,
        ZoneId.of("Asia/Ho_Chi_Minh")
);

ZonedDateTime parisView = source.withZoneSameInstant(
        ZoneId.of("Europe/Paris")
);
```

Dùng khi đang hiển thị **cùng một sự kiện** cho người dùng ở zone khác.

### Giữ nguyên local fields

```java
ZonedDateTime reinterpreted = source.withZoneSameLocal(
        ZoneId.of("Europe/Paris")
);
```

Operation này giữ local date/time càng nguyên vẹn càng tốt rồi resolve theo zone mới; vì vậy instant thường thay đổi. Đây không phải “đổi cách hiển thị” cùng event, mà là **reinterpret** local schedule trong region khác.

### Chuyển từ Instant sang local view

```java
Instant eventTime = Instant.parse("2026-10-05T02:00:00Z");

ZonedDateTime vietnamView = eventTime.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
ZonedDateTime parisView = eventTime.atZone(ZoneId.of("Europe/Paris"));
```

Cả hai view giữ cùng event identity.

### OffsetDateTime cũng có same-instant và same-local

Cùng mental model áp dụng cho `OffsetDateTime`:

```java
OffsetDateTime original = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");

OffsetDateTime sameInstant = original.withOffsetSameInstant(
        ZoneOffset.ofHours(2)
);
// 2026-10-05T04:00+02:00 → cùng instant

OffsetDateTime sameLocal = original.withOffsetSameLocal(
        ZoneOffset.ofHours(2)
);
// 2026-10-05T09:00+02:00 → cùng local fields, instant thay đổi
```

```text
withOffsetSameInstant
→ giữ timeline point, đổi local clock để phù hợp offset mới

withOffsetSameLocal
→ giữ local fields, reinterpret chúng dưới offset mới
```

Vì `OffsetDateTime` không có region rules, đây chỉ là đổi giữa các concrete offset; không có DST rule lookup như khi đổi `ZoneId` region-based.

Khi làm việc với DST gap/overlap, việc resolve local fields có thêm rule đặc biệt; chapter Pitfalls sẽ giải thích rõ hơn. Trước đó, ta cần phân biệt hai kiểu “khoảng thời gian”: `Duration` và `Period`.
