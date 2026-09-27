# Instant

Sau `LocalDateTime`, câu hỏi tiếp theo là: **làm sao biểu diễn một thời điểm đã được xác định rõ trên timeline toàn cầu?** `Instant` là type trung tâm cho câu hỏi đó.

## <a id="instant-model">Mô hình Instant</a>

`Instant` đại diện cho **một điểm trên timeline UTC**. Nó không chứa cách hiển thị theo lịch địa phương như `2026-10-05 09:00 Asia/Ho_Chi_Minh`; nó tập trung vào vị trí tuyệt đối của sự kiện.

```java
Instant now = Instant.now();
Instant explicit = Instant.parse("2026-10-05T02:00:00Z");
```

Ký tự `Z` trong ISO-8601 nghĩa là offset `+00:00`, tức UTC.

### Vì sao Instant quan trọng trong hệ thống phân tán?

Giả sử hai server ở hai vùng khác nhau ghi nhận cùng một event:

```text
Server A hiển thị: 09:00 tại Việt Nam
Server B hiển thị: 04:00 tại Paris
```

Hai local clock có thể khác nhau nhưng vẫn trỏ tới **cùng một instant**. Khi cần ordering, audit, event time hoặc communication giữa hệ thống, việc đưa dữ liệu về một timeline chung giúp loại bỏ ambiguity của local time.

### Instant không phải “UTC LocalDateTime”

Đừng mental-model `Instant` như một `LocalDateTime` đặc biệt đang ở zone UTC. `Instant` không sở hữu year/month/day/hour như domain fields chính. Nó là một timeline point; calendar fields chỉ xuất hiện khi ta chiếu instant đó qua một zone.

```java
Instant instant = Instant.parse("2026-10-05T02:00:00Z");

ZonedDateTime vietnamView = instant.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
ZonedDateTime parisView = instant.atZone(ZoneId.of("Europe/Paris"));
```

Cùng `instant`, nhiều local view có thể khác nhau.

### Boundary nâng cao — Java Time-Scale và leap second

Người mới có thể giữ mental model đơn giản: `Instant` là một điểm trên global UTC-oriented timeline. Ở mức specification sâu hơn, Java định nghĩa **Java Time-Scale** cho các date-time class; nó chia mỗi calendar day thành 86.400 phần theo model của Java và có quy tắc riêng quanh **leap second** — giây điều chỉnh đặc biệt đôi khi được thêm vào thang thời gian dân sự để giữ nó gần với chuyển động quay của Trái Đất.

Điểm cần nhớ cho application thông thường:

```text
Instant
→ phù hợp cho event timestamp / ordering / persistence boundary

Instant
→ không nên được suy diễn thành công cụ đo thời gian thiên văn/leap-second chính xác
  nếu domain không có requirement chuyên biệt đó
```

## <a id="epoch-time">Epoch time</a>

Một cách biểu diễn timeline point là đếm từ một mốc gọi là **epoch**. Với Java `Instant`, mốc chuẩn là `1970-01-01T00:00:00Z`.

```java
Instant epoch = Instant.EPOCH;
Instant fromSeconds = Instant.ofEpochSecond(1_000_000);
Instant fromMillis = Instant.ofEpochMilli(1_000_000);
```

Đọc dữ liệu:

```java
long seconds = instant.getEpochSecond();
int nanos = instant.getNano();
long millis = instant.toEpochMilli();
```

`Instant` về logic được biểu diễn bằng số giây epoch cộng phần nanosecond trong giây. `toEpochMilli()` chỉ đưa value về độ phân giải millisecond và có thể mất phần precision nhỏ hơn millisecond.

`Instant` có range rất lớn: từ `Instant.MIN` khoảng năm `-1000000000` tới `Instant.MAX` khoảng năm `+1000000000`. Đây chủ yếu là boundary kỹ thuật; business application thông thường nằm rất xa các giới hạn này.

### Epoch number cần có unit rõ ràng

Một raw number như:

```text
1728093600
```

không tự nói đó là:

```text
seconds?
milliseconds?
microseconds?
nanoseconds?
```

Sai unit có thể làm timestamp lệch hàng nghìn năm. Ở boundary JSON/database/message schema, hãy ghi rõ tên và unit, hoặc dùng textual ISO-8601 representation có contract rõ ràng.

### Epoch không xóa nhu cầu về zone

Epoch time xác định một instant nhưng người dùng vẫn cần zone để xem theo local calendar:

```java
Instant stored = Instant.parse("2026-10-05T02:00:00Z");
ZoneId userZone = ZoneId.of("Asia/Ho_Chi_Minh");

LocalDateTime displayedLocal = LocalDateTime.ofInstant(stored, userZone);
```

Timeline storage và presentation zone là hai trách nhiệm khác nhau.

## <a id="instant-use-cases">Use case và boundary của Instant</a>

`Instant` thường phù hợp cho dữ liệu kiểu:

```text
createdAt / updatedAt của event đã xảy ra
requestReceivedAt
messageProducedAt
tokenIssuedAt / expiresAt khi semantics là timeline deadline
audit log timestamp
```

Ví dụ:

```java
record AuditEvent(String action, Instant occurredAt) {}

AuditEvent event = new AuditEvent("PAYMENT_CONFIRMED", Instant.now());
```

### Khi Instant không đủ

Một cuộc họp tương lai “09:00 ngày 05/10 tại Europe/Paris” chứa intent theo **local civil time + region rules**. Nếu chỉ giữ instant sau lần resolve đầu tiên, application có thể mất context zone mà domain cần để hiển thị, chỉnh lịch hoặc phản ứng với thay đổi rule trong tương lai.

Vì vậy cần hỏi:

```text
Event đã xảy ra và chỉ cần vị trí timeline?
→ Instant thường đủ

Lịch tương lai gắn với nơi cụ thể?
→ giữ local date-time + ZoneId/ZonedDateTime theo domain requirement
```

### So sánh Instant

```java
Instant a = Instant.parse("2026-10-05T02:00:00Z");
Instant b = Instant.parse("2026-10-05T03:00:00Z");

a.isBefore(b); // true
a.isAfter(b);  // false
```

Vì cả hai đã ở cùng global timeline, comparison không cần đoán zone.

Chapter tiếp theo giải thích cầu nối quan trọng: **offset cụ thể khác gì một named time zone có rule thay đổi theo ngày?**
