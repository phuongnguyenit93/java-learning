# Clock và thời gian có thể kiểm thử

Mã dùng thời gian thường có một phụ thuộc vô hình: **đồng hồ hệ thống tại thời điểm phương thức chạy**. Nếu logic nghiệp vụ gọi `Instant.now()` hoặc `LocalDate.now()` trực tiếp ở nhiều nơi, kiểm thử sẽ phụ thuộc thời gian thực và trở nên khó có kết quả xác định.

`Clock` tồn tại để biến nguồn “thời điểm hiện tại” thành một đối tượng có thể truyền vào.

## <a id="clock-abstraction">Clock trừu tượng hóa nguồn thời gian hiện tại</a>

Không có `Clock`:

```java
boolean isExpired(Instant expiresAt) {
    return !Instant.now().isBefore(expiresAt);
}
```

Phương thức này phụ thuộc trực tiếp vào đồng hồ hệ thống. Kiểm thử chạy lúc nào thì `now()` là lúc đó.

Với `Clock`:

```java
boolean isExpired(Instant expiresAt, Clock clock) {
    return !Instant.now(clock).isBefore(expiresAt);
}
```

Quy tắc nghiệp vụ không đổi, nhưng nguồn thời gian hiện tại đã trở thành phụ thuộc rõ ràng.

Trong ví dụ này, quy ước là token **hết hạn ngay khi `now >= expiresAt`**. Nếu nghiệp vụ muốn chỉ hết hạn sau mốc đó, có thể dùng `isAfter`; điều quan trọng là chọn và kiểm thử trường hợp bằng đúng hạn chót một cách nhất quán.

### Một số phương thức tạo Clock

```java
Clock utc = Clock.systemUTC();
Clock systemZone = Clock.systemDefaultZone();
Clock fixed = Clock.fixed(
        Instant.parse("2026-09-27T10:00:00Z"),
        ZoneOffset.UTC
);
Clock shifted = Clock.offset(utc, Duration.ofMinutes(5));
```

`Clock` cung cấp cả `Instant` và ngữ cảnh múi giờ cho những API `now(clock)` cần chúng.

```java
Instant now = clock.instant();
ZoneId zone = clock.getZone();
```

### InstantSource — khi chỉ cần nguồn Instant

Từ Java 17 trở đi, `java.time` còn có `InstantSource`: một **giao diện hẹp hơn `Clock`**, chỉ đại diện cho nguồn cung cấp `Instant` hiện tại.

```text
InstantSource
→ cần biết "Instant hiện tại là gì?"

Clock
→ cũng cung cấp Instant hiện tại
→ đồng thời mang ZoneId cho các API cần ngữ cảnh lịch/múi giờ
```

`Clock` triển khai `InstantSource`, nên một dịch vụ chỉ cần dấu thời gian trên dòng thời gian có thể phụ thuộc vào giao diện hẹp hơn:

```java
boolean isExpired(Instant expiresAt, InstantSource source) {
    return !source.instant().isBefore(expiresAt);
}
```

Kiểm thử vẫn có thể dùng nguồn cố định:

```java
InstantSource fixed = InstantSource.fixed(
        Instant.parse("2026-09-27T10:00:00Z")
);
```

Không cần thay mọi `Clock` bằng `InstantSource`: nếu logic cần `ZoneId`, `LocalDate.now(clock)` hoặc `ZonedDateTime.now(clock)`, `Clock` vẫn là lựa chọn phù hợp hơn.

### Clock không phải đồng hồ bấm giờ để đo thời lượng

`Clock`/`Instant.now()` trả lời câu hỏi **“hiện tại là mốc nào trên dòng thời gian?”**. Đồng hồ hệ thống có thể bị điều chỉnh bởi hệ điều hành, đồng bộ thời gian hoặc thay đổi nguồn thời gian.

Nếu mục tiêu là đo **một đoạn mã chạy mất bao lâu** trong cùng JVM, Java có `System.nanoTime()`:

```java
long start = System.nanoTime();

doWork();

long elapsedNanos = System.nanoTime() - start;
```

Giá trị tuyệt đối từ `nanoTime()` không phải dấu thời gian, không chuyển đổi sang `Instant`, và không có ý nghĩa lịch. Chỉ **hiệu giữa hai lần đọc trong cùng JVM** mới có ý nghĩa thời lượng đã trôi qua.

Mô hình tư duy:

```text
Clock / Instant.now()
→ "bây giờ là thời điểm nào?"

System.nanoTime()
→ "đã trôi qua bao lâu giữa hai điểm đo trong JVM?"
```

Hai bài toán khác nhau, dù cả hai đều liên quan đến thời gian.

## <a id="fixed-clock-testing">Clock cố định cho kiểm thử có kết quả xác định</a>

Giả sử token hết hạn lúc 10:05 UTC:

```java
Instant expiresAt = Instant.parse("2026-09-27T10:05:00Z");
```

Kiểm thử trước hạn chót:

```java
Clock beforeExpiry = Clock.fixed(
        Instant.parse("2026-09-27T10:04:00Z"),
        ZoneOffset.UTC
);

assertFalse(isExpired(expiresAt, beforeExpiry));
```

Kiểm thử sau hạn chót:

```java
Clock afterExpiry = Clock.fixed(
        Instant.parse("2026-09-27T10:06:00Z"),
        ZoneOffset.UTC
);

assertTrue(isExpired(expiresAt, afterExpiry));
```

Kiểm thử đúng tại hạn chót:

```java
Clock atExpiry = Clock.fixed(
        expiresAt,
        ZoneOffset.UTC
);

assertTrue(isExpired(expiresAt, atExpiry));
```

Ba kiểm thử không cần chờ thời gian thực và không phụ thuộc lúc bộ kiểm thử thực sự chạy.

### Không dùng Thread.sleep để “chờ thời gian” trong kiểm thử đơn vị

Cách viết yếu:

```java
Thread.sleep(1_000);
assertTrue(...);
```

Nó làm kiểm thử chậm và có thể không ổn định vì bộ lập lịch hoặc tải của CI. Nếu logic chỉ cần kiểm tra quy tắc thời gian, điều khiển `Clock` tốt hơn nhiều.

## <a id="clock-injection">Truyền Clock vào thay vì gọi now() khắp nơi</a>

Một dịch vụ có thể nhận `Clock` qua hàm tạo:

```java
final class TokenService {
    private final Clock clock;

    TokenService(Clock clock) {
        this.clock = clock;
    }

    boolean isExpired(Instant expiresAt) {
        return !clock.instant().isBefore(expiresAt);
    }
}
```

Môi trường thực tế:

```java
TokenService service = new TokenService(Clock.systemUTC());
```

Kiểm thử:

```java
Clock fixed = Clock.fixed(
        Instant.parse("2026-09-27T10:00:00Z"),
        ZoneOffset.UTC
);

TokenService service = new TokenService(fixed);
```

### Chọn nơi truyền Clock vào

Không nhất thiết mọi phương thức đều phải nhận tham số `Clock`. Thường ứng dụng truyền một `Clock` vào tại ranh giới dịch vụ/thành phần rồi dùng lại bên trong. Mục tiêu là **không để logic nghiệp vụ tự truy cập nguồn thời gian toàn cục một cách không kiểm soát**.

### Clock không thay thế bộ lập lịch

`Clock` trả lời “thời gian hiện tại là gì theo nguồn này?”. Nó không tự chạy tác vụ, không lập lịch tác vụ và không đảm bảo thời điểm đánh thức. Lập lịch là trách nhiệm khác.

Sau khi hiểu cách kiểm soát “thời điểm hiện tại”, chương cuối nhìn lại API cũ, rủi ro của múi giờ mặc định và cách chọn thông tin cần lưu trữ. Phần khoảng trống/chồng lặp DST đã được xử lý ở chương phép toán và quy tắc múi giờ ngay trước đó.
