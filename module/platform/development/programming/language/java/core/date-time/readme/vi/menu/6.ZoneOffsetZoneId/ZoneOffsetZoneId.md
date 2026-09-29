# ZoneOffset và ZoneId

Khi chuyển từ local date-time sang timeline, ta cần biết local clock liên hệ với UTC như thế nào. Java có hai khái niệm rất dễ bị nhầm: `ZoneOffset` và `ZoneId`.

### Múi giờ thực ra là gì?

Trong phần mềm, một **time zone** không nên được hiểu đơn giản là “UTC+7” hay “UTC-5”. Với một named region, time zone là **tập quy tắc dùng để ánh xạ giữa global timeline và local civil time của khu vực đó**.

Nó có thể trả lời:

```text
Instant này ở Paris hiển thị mấy giờ?
LocalDateTime này tại New York dùng offset nào?
Ngày này có DST transition không?
Local time này có tồn tại một lần, hai lần hay không tồn tại?
```

Quan hệ quan trọng:

```text
ZoneId
→ định danh bộ quy tắc của zone

ZoneRules
→ chính các quy tắc thay đổi theo thời gian

ZoneOffset
→ một offset cụ thể được áp dụng tại một thời điểm/context
```

Vì vậy **time zone không đồng nghĩa với offset**. Offset chỉ là một phần/kết quả của rule tại một thời điểm.

## <a id="zone-offset">ZoneOffset — một độ lệch cụ thể so với UTC</a>

`ZoneOffset` mô tả chênh lệch giữa local time và UTC tại một thời điểm, ví dụ:

```text
Z       → +00:00
+07:00  → nhanh hơn UTC 7 giờ
-05:00  → chậm hơn UTC 5 giờ
```

```java
ZoneOffset utc = ZoneOffset.UTC;
ZoneOffset vietnamLikeOffset = ZoneOffset.of("+07:00");
ZoneOffset minusFive = ZoneOffset.ofHours(-5);
```

Offset là **một con số cố định**. `+07:00` không mang theo lịch sử địa phương, luật DST hay tên quốc gia.

Ví dụ textual timestamp:

```java
OffsetDateTime value = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
```

Từ local fields + offset, Java xác định được instant:

```java
Instant instant = value.toInstant();
```

Nhưng nếu chỉ có `+07:00`, ta không biết offset đó thuộc `Asia/Ho_Chi_Minh`, một fixed-offset system hay nơi nào khác có cùng offset tại thời điểm đó.

## <a id="zone-id">ZoneId — danh tính vùng thời gian</a>

`ZoneId` có thể là một region-based ID như:

```java
ZoneId vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
ZoneId paris = ZoneId.of("Europe/Paris");
ZoneId newYork = ZoneId.of("America/New_York");
```

Tên region không chỉ là nhãn để display. Nó cho phép Java tra **bộ quy tắc múi giờ** tương ứng.

### Tránh short zone ID mơ hồ

Các viết tắt ba chữ như `CST`, `EST`, `IST` nhìn ngắn nhưng **không phải định danh region toàn cầu đáng tin cậy**; cùng một abbreviation có thể được hiểu khác nhau theo hệ thống hoặc khu vực. Với domain thật, ưu tiên IANA-style region ID dạng `Area/City` như:

```text
Asia/Ho_Chi_Minh
Europe/Paris
America/New_York
```

Java có hỗ trợ một số short ID vì compatibility, nhưng đừng dùng chúng làm canonical business zone nếu contract có thể lưu/trao đổi region ID rõ ràng.

So sánh:

```text
+01:00
→ chỉ biết offset hiện tại/cụ thể

Europe/Paris
→ biết region
→ rule engine có thể xác định offset đúng cho từng local date-time/instant
```

Vì DST hoặc thay đổi pháp lý, offset của một region có thể khác nhau theo ngày.

**DST (Daylight Saving Time)** là chính sách mà một số khu vực điều chỉnh local clock trong một phần của năm. Không phải quốc gia/region nào cũng dùng DST, và rule có thể thay đổi theo quyết định pháp lý; vì vậy code không nên tự hard-code “mùa hè luôn +1 giờ”.

### Chọn ZoneOffset hay ZoneId?

```text
External contract chỉ sở hữu một offset cụ thể như +07:00
→ ZoneOffset

Domain sở hữu một khu vực thật và cần rule theo lịch sử/tương lai
→ ZoneId region-based

Cần một global event point, không cần giữ local/zone identity
→ thường convert về Instant
```

Đừng nâng một `ZoneOffset` thành `ZoneId` bằng suy đoán. `+07:00` có thể khớp nhiều nơi và không chứa đủ thông tin để biết region ban đầu.

### System default zone

```java
ZoneId systemZone = ZoneId.systemDefault();
```

API này tiện nhưng đưa environment vào business logic. Chạy cùng code ở laptop, CI và production với default zone khác nhau có thể cho kết quả khác. Nếu domain biết zone, nên truyền zone/configuration rõ ràng thay vì dựa vào default ngầm.

## <a id="zone-rules">ZoneRules và offset thay đổi theo thời gian</a>

`ZoneId` region-based dẫn tới `ZoneRules`:

```java
ZoneId paris = ZoneId.of("Europe/Paris");
ZoneRules rules = paris.getRules();
```

Rule set dùng date-time database của JDK để trả lời các câu hỏi như:

```text
Instant này đang dùng offset nào?
LocalDateTime này có offset hợp lệ nào?
Có transition DST ở đây không?
```

Ví dụ lấy offset tại hai instant:

```java
ZoneRules rules = ZoneId.of("Europe/Paris").getRules();

ZoneOffset winter = rules.getOffset(Instant.parse("2026-01-15T12:00:00Z"));
ZoneOffset summer = rules.getOffset(Instant.parse("2026-07-15T12:00:00Z"));
```

Hai offset có thể khác nhau vì region rules.

### Vì sao `+07:00` không đồng nghĩa `Asia/Ho_Chi_Minh`?

Ngay cả khi hôm nay một region có offset `+07:00`, quan hệ đúng là:

```text
ZoneId
    ↓ dùng rules + instant/local date-time
ZoneOffset
```

không phải:

```text
ZoneOffset
    ↓ suy ngược chắc chắn
ZoneId
```

Nhiều region có thể cùng offset tại một thời điểm. Một offset không chứa đủ thông tin để khôi phục region.

### Time-zone rules là dữ liệu có thể được cập nhật

Chính phủ có thể thay đổi luật giờ mùa hè hoặc offset. JDK cập nhật time-zone database theo các release/update. Vì thế một lịch tương lai gắn với region nên giữ `ZoneId` khi domain cần rule semantics, thay vì đóng băng một offset được tính quá sớm.

Chapter tiếp theo đặt các mảnh lại với nhau bằng `ZonedDateTime` và `OffsetDateTime`.
