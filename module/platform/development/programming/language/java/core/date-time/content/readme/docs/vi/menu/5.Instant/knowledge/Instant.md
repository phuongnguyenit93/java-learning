# Instant và dòng thời gian toàn cục

Sau `LocalDateTime`, câu hỏi tiếp theo là: **làm sao biểu diễn một thời điểm đã được xác định rõ trên dòng thời gian toàn cầu?** `Instant` là kiểu trung tâm cho câu hỏi đó.

## <a id="instant-model">Mô hình Instant</a>

`Instant` đại diện cho **một điểm trên dòng thời gian UTC**. Nó không chứa cách hiển thị theo lịch địa phương như `2026-10-05 09:00 Asia/Ho_Chi_Minh`; nó tập trung vào vị trí tuyệt đối của sự kiện.

```java
Instant now = Instant.now();
Instant explicit = Instant.parse("2026-10-05T02:00:00Z");
```

Ký tự `Z` trong ISO-8601 nghĩa là độ lệch `+00:00`, tức UTC.

### Vì sao Instant quan trọng trong hệ thống phân tán?

Giả sử hai máy chủ ở hai vùng khác nhau ghi nhận cùng một sự kiện:

```text
Máy chủ A hiển thị: 09:00 tại Việt Nam
Máy chủ B hiển thị: 04:00 tại Paris
```

Hai đồng hồ cục bộ có thể khác nhau nhưng vẫn trỏ tới **cùng một `Instant`**. Khi cần sắp xếp, kiểm toán (audit), thời điểm sự kiện hoặc giao tiếp giữa hệ thống, việc đưa dữ liệu về một dòng thời gian chung giúp loại bỏ tính mơ hồ của giờ cục bộ.

### Instant không phải “UTC LocalDateTime”

Đừng hình dung `Instant` như một `LocalDateTime` đặc biệt đang ở múi giờ UTC. `Instant` không sở hữu các trường năm/tháng/ngày/giờ như dữ liệu nghiệp vụ chính. Nó là một mốc trên dòng thời gian; các trường theo lịch chỉ xuất hiện khi ta chiếu `Instant` đó qua một múi giờ.

```java
Instant instant = Instant.parse("2026-10-05T02:00:00Z");

ZonedDateTime vietnamView = instant.atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
ZonedDateTime parisView = instant.atZone(ZoneId.of("Europe/Paris"));
```

Cùng một `Instant`, nhiều cách hiển thị cục bộ có thể khác nhau.

### Phần nâng cao — Java Time-Scale và giây nhuận

Người mới có thể giữ mô hình tư duy đơn giản: `Instant` là một điểm trên dòng thời gian toàn cục dựa trên UTC. Ở mức đặc tả sâu hơn, Java định nghĩa **Java Time-Scale** cho các lớp ngày-giờ; nó chia mỗi ngày theo lịch thành 86.400 phần theo mô hình của Java và có quy tắc riêng quanh **giây nhuận** — giây điều chỉnh đặc biệt đôi khi được thêm vào thang thời gian dân sự để giữ nó gần với chuyển động quay của Trái Đất.

Điểm cần nhớ cho ứng dụng thông thường:

```text
Instant
→ phù hợp cho dấu thời gian sự kiện / sắp xếp / ranh giới lưu trữ

Instant
→ không nên được suy diễn thành công cụ đo thời gian thiên văn/giây nhuận chính xác
  nếu nghiệp vụ không có yêu cầu chuyên biệt đó
```

## <a id="epoch-time">Thời gian tính từ mốc gốc Unix (epoch)</a>

Một cách biểu diễn mốc trên dòng thời gian là đếm từ một mốc gọi là **epoch**. Với Java `Instant`, mốc chuẩn là `1970-01-01T00:00:00Z`.

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

`Instant` về mặt logic được biểu diễn bằng số giây tính từ epoch cộng phần nano giây trong giây. `toEpochMilli()` chỉ đưa giá trị về độ phân giải mili giây và có thể mất phần độ chính xác nhỏ hơn mili giây.

`Instant` có phạm vi rất lớn: từ `Instant.MIN` khoảng năm `-1000000000` tới `Instant.MAX` khoảng năm `+1000000000`. Đây chủ yếu là ranh giới kỹ thuật; nghiệp vụ ứng dụng thông thường nằm rất xa các giới hạn này.

### Giá trị epoch phải ghi rõ đơn vị

Một giá trị số thô như:

```text
1728093600
```

không tự nói đó là:

```text
giây?
mili giây?
micro giây?
nano giây?
```

Sai đơn vị có thể làm dấu thời gian lệch hàng nghìn năm. Ở ranh giới JSON/cơ sở dữ liệu/lược đồ thông điệp, hãy ghi rõ tên và đơn vị, hoặc dùng biểu diễn văn bản ISO-8601 có hợp đồng rõ ràng.

### Epoch không loại bỏ nhu cầu về múi giờ

Thời gian epoch xác định một `Instant` nhưng người dùng vẫn cần múi giờ để xem theo lịch cục bộ:

```java
Instant stored = Instant.parse("2026-10-05T02:00:00Z");
ZoneId userZone = ZoneId.of("Asia/Ho_Chi_Minh");

LocalDateTime displayedLocal = LocalDateTime.ofInstant(stored, userZone);
```

Lưu trữ trên dòng thời gian và hiển thị theo múi giờ là hai trách nhiệm khác nhau.

## <a id="instant-use-cases">Trường hợp sử dụng và giới hạn của Instant</a>

`Instant` thường phù hợp cho dữ liệu kiểu:

```text
createdAt / updatedAt của sự kiện đã xảy ra
requestReceivedAt
messageProducedAt
tokenIssuedAt / expiresAt khi ý nghĩa là hạn chót trên dòng thời gian
dấu thời gian của nhật ký kiểm toán (audit)
```

Ví dụ:

```java
record AuditEvent(String action, Instant occurredAt) {}

AuditEvent event = new AuditEvent("PAYMENT_CONFIRMED", Instant.now());
```

### Khi Instant không đủ

Một cuộc họp tương lai “09:00 ngày 05/10 tại Europe/Paris” chứa ý định theo **thời gian dân sự cục bộ + quy tắc của vùng**. Nếu chỉ giữ `Instant` sau lần phân giải đầu tiên, ứng dụng có thể mất ngữ cảnh múi giờ mà nghiệp vụ cần để hiển thị, chỉnh lịch hoặc phản ứng với thay đổi quy tắc trong tương lai.

Vì vậy cần hỏi:

```text
Sự kiện đã xảy ra và chỉ cần vị trí trên dòng thời gian?
→ Instant thường đủ

Lịch tương lai gắn với nơi cụ thể?
→ giữ ngày-giờ cục bộ + ZoneId/ZonedDateTime theo yêu cầu nghiệp vụ
```

### So sánh Instant

```java
Instant a = Instant.parse("2026-10-05T02:00:00Z");
Instant b = Instant.parse("2026-10-05T03:00:00Z");

a.isBefore(b); // true
a.isAfter(b);  // false
```

Vì cả hai đã ở cùng dòng thời gian toàn cục, so sánh không cần đoán múi giờ.

Chương tiếp theo giải thích cầu nối quan trọng: **một độ lệch UTC cụ thể khác gì một múi giờ theo vùng có quy tắc thay đổi theo ngày?**
