# Clock

Code dùng thời gian thường có một dependency vô hình: **đồng hồ hệ thống tại thời điểm method chạy**. Nếu business logic gọi `Instant.now()` hoặc `LocalDate.now()` trực tiếp ở nhiều nơi, test sẽ phụ thuộc thời gian thực và trở nên khó deterministic.

`Clock` tồn tại để biến nguồn “now” thành một object có thể truyền vào.

## <a id="clock-abstraction">Clock là abstraction cho nguồn thời gian hiện tại</a>

Không có `Clock`:

```java
boolean isExpired(Instant expiresAt) {
    return Instant.now().isAfter(expiresAt);
}
```

Method này phụ thuộc trực tiếp system clock. Test chạy lúc nào thì `now()` là lúc đó.

Với `Clock`:

```java
boolean isExpired(Instant expiresAt, Clock clock) {
    return Instant.now(clock).isAfter(expiresAt);
}
```

Business rule không đổi, nhưng nguồn current time đã trở thành dependency rõ ràng.

### Một số Clock factory

```java
Clock utc = Clock.systemUTC();
Clock systemZone = Clock.systemDefaultZone();
Clock fixed = Clock.fixed(
        Instant.parse("2026-09-27T10:00:00Z"),
        ZoneOffset.UTC
);
Clock shifted = Clock.offset(utc, Duration.ofMinutes(5));
```

`Clock` cung cấp cả instant và zone context cho những API `now(clock)` cần chúng.

```java
Instant now = clock.instant();
ZoneId zone = clock.getZone();
```

### InstantSource — khi chỉ cần nguồn Instant

Từ Java 17 trở đi, `java.time` còn có `InstantSource`: một abstraction **hẹp hơn `Clock`**, chỉ đại diện cho nguồn cung cấp current `Instant`.

```text
InstantSource
→ cần biết "instant hiện tại là gì?"

Clock
→ cũng cung cấp current instant
→ đồng thời mang ZoneId cho các API cần local/calendar context
```

`Clock` implements `InstantSource`, nên một service chỉ cần timeline timestamp có thể phụ thuộc vào interface hẹp hơn:

```java
boolean isExpired(Instant expiresAt, InstantSource source) {
    return !source.instant().isBefore(expiresAt);
}
```

Test vẫn có thể dùng nguồn cố định:

```java
InstantSource fixed = InstantSource.fixed(
        Instant.parse("2026-09-27T10:00:00Z")
);
```

Không cần thay mọi `Clock` bằng `InstantSource`: nếu logic cần `ZoneId`, `LocalDate.now(clock)` hoặc `ZonedDateTime.now(clock)`, `Clock` vẫn là abstraction phù hợp hơn.

### Clock không phải stopwatch đo elapsed time

`Clock`/`Instant.now()` thuộc **wall-clock time**: nó trả lời “trên timeline hiện tại đang là thời điểm nào?”. Wall clock có thể bị điều chỉnh bởi hệ điều hành, đồng bộ thời gian hoặc thay đổi nguồn clock.

Nếu mục tiêu là đo **một đoạn code chạy mất bao lâu** trong cùng JVM, Java có `System.nanoTime()`:

```java
long start = System.nanoTime();

doWork();

long elapsedNanos = System.nanoTime() - start;
```

Giá trị tuyệt đối từ `nanoTime()` không phải timestamp, không convert sang `Instant`, và không có ý nghĩa calendar. Chỉ **hiệu giữa hai lần đọc trong cùng JVM** mới có ý nghĩa elapsed-time.

Mental model:

```text
Clock / Instant.now()
→ "bây giờ là thời điểm nào?"

System.nanoTime()
→ "đã trôi qua bao lâu giữa hai điểm đo trong JVM?"
```

Hai bài toán khác nhau, dù cả hai đều có chữ “time”.

## <a id="fixed-clock-testing">Fixed Clock cho test deterministic</a>

Giả sử token hết hạn lúc 10:05 UTC:

```java
Instant expiresAt = Instant.parse("2026-09-27T10:05:00Z");
```

Test trước deadline:

```java
Clock beforeExpiry = Clock.fixed(
        Instant.parse("2026-09-27T10:04:00Z"),
        ZoneOffset.UTC
);

assertFalse(isExpired(expiresAt, beforeExpiry));
```

Test sau deadline:

```java
Clock afterExpiry = Clock.fixed(
        Instant.parse("2026-09-27T10:06:00Z"),
        ZoneOffset.UTC
);

assertTrue(isExpired(expiresAt, afterExpiry));
```

Hai test không cần sleep và không phụ thuộc lúc suite thực sự chạy.

### Không dùng Thread.sleep để “chờ thời gian” trong unit test

Pattern yếu:

```java
Thread.sleep(1_000);
assertTrue(...);
```

Nó làm test chậm và có thể flaky vì scheduler/CI load. Nếu logic chỉ cần kiểm tra rule thời gian, điều khiển `Clock` tốt hơn nhiều.

## <a id="clock-injection">Inject Clock thay vì gọi now khắp nơi</a>

Một service có thể nhận `Clock` qua constructor:

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

Production:

```java
TokenService service = new TokenService(Clock.systemUTC());
```

Test:

```java
Clock fixed = Clock.fixed(
        Instant.parse("2026-09-27T10:00:00Z"),
        ZoneOffset.UTC
);

TokenService service = new TokenService(fixed);
```

### Chọn nơi inject

Không nhất thiết mọi method đều phải nhận `Clock` parameter. Thường application inject một `Clock` ở service/component boundary rồi dùng lại bên trong. Mục tiêu là **không để business logic tự truy cập global time source một cách không kiểm soát**.

### Clock không thay thế scheduler

`Clock` trả lời “thời gian hiện tại là gì theo source này?”. Nó không tự chạy job, không schedule task và không đảm bảo wake-up timing. Scheduling là trách nhiệm khác.

Sau khi hiểu cách kiểm soát “now”, chapter cuối nhìn lại API cũ và những bug production thường đến từ default zone, legacy conversion và DST gap/overlap.
