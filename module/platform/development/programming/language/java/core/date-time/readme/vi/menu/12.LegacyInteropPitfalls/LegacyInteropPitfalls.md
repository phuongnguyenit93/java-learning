# Tương tác với API ngày-giờ cũ và tổng hợp mô hình

`java.time` giải quyết nhiều vấn đề thiết kế của API ngày-giờ cũ, nhưng ứng dụng thực tế vẫn thường gặp `java.util.Date`, `Calendar`, các kiểu JDBC, cột cơ sở dữ liệu và giá trị mặc định của hệ thống. Vì vậy cần biết cách tương tác mà không mang mô hình tư duy cũ vào mã mới.

## <a id="legacy-date-calendar">Date và Calendar: mô hình tư duy của API cũ</a>

### java.util.Date

Tên `Date` dễ gây hiểu nhầm: `java.util.Date` không phải kiểu ngày theo lịch hiện đại giống `LocalDate`. Nó chủ yếu biểu diễn một mốc thời gian dựa trên mili giây từ epoch và có API cũ, có thể thay đổi trạng thái.

```java
Date legacy = new Date();
```

Nhiều getter/setter kiểu lịch của `Date` đã bị đánh dấu lỗi thời từ lâu; mã mới nên ưu tiên `java.time`.

### Calendar

`Calendar` cung cấp các trường theo lịch, múi giờ và phép toán theo kiểu có thể thay đổi:

```java
Calendar calendar = Calendar.getInstance();
calendar.add(Calendar.DAY_OF_MONTH, 1);
```

Thao tác sửa trực tiếp đối tượng, khác mô hình tư duy bất biến của `java.time`.

Các API cũ vẫn xuất hiện ở ranh giới tích hợp với hệ thống cũ, nhưng đừng chọn chúng cho mã mới chỉ vì hệ thống đã có sẵn một vài phương thức dùng `Date`.

### SimpleDateFormat — bộ định dạng cũ, thay đổi được và không an toàn đa luồng

Mã cũ còn rất hay gặp `java.text.SimpleDateFormat`:

```java
SimpleDateFormat legacyFormatter = new SimpleDateFormat("dd/MM/yyyy");
```

Khác `DateTimeFormatter`, `SimpleDateFormat` là đối tượng **có thể thay đổi và không an toàn đa luồng**. Vì vậy cách chia sẻ một đối tượng `static` dùng chung giữa nhiều luồng có thể tạo tranh chấp dữ liệu và kết quả phân tích/định dạng khó đoán.

```text
SimpleDateFormat
→ bộ định dạng cũ
→ có thể thay đổi
→ không nên chia sẻ giữa nhiều luồng nếu không có đồng bộ phù hợp

DateTimeFormatter
→ bộ định dạng java.time hiện đại
→ bất biến + an toàn đa luồng
```

Khi chuyển mã sang API mới, ưu tiên `DateTimeFormatter`; chỉ giữ `SimpleDateFormat` ở ranh giới với hệ thống cũ khi API cũ bắt buộc.

## <a id="legacy-conversion">Chuyển đổi API cũ có chủ đích</a>

### Date ↔ Instant

`Date` và `Instant` đều có thể biểu diễn mốc trên dòng thời gian, nên chuyển đổi tương đối trực tiếp:

```java
Date legacy = new Date();
Instant instant = legacy.toInstant();

Date back = Date.from(instant);
```

Đây thường là ranh giới tốt: chuyển đổi sang kiểu hiện đại sớm, xử lý bằng `java.time`, rồi chỉ chuyển đổi lại nếu API cũ bắt buộc.

### Calendar → ZonedDateTime

```java
Calendar calendar = Calendar.getInstance();

ZonedDateTime modern = calendar.toInstant()
        .atZone(calendar.getTimeZone().toZoneId());
```

Ở đây cần cả mốc trên dòng thời gian và múi giờ của `Calendar` để giữ cách diễn giải cục bộ phù hợp.

### `java.sql.Date`, `Time`, `Timestamp`

**JDBC (Java Database Connectivity)** là API Java dùng để làm việc với cơ sở dữ liệu quan hệ. Mã JDBC cũ thường cung cấp ba kiểu bọc `java.sql.*` rất dễ bị nhầm với `java.time`:

```text
java.sql.Date
→ giá trị kiểu SQL DATE
→ kiểu java.time tương ứng tự nhiên: LocalDate

java.sql.Time
→ giá trị kiểu SQL TIME
→ kiểu java.time tương ứng tự nhiên: LocalTime

java.sql.Timestamp
→ kiểu bọc SQL TIMESTAMP có phần giây lẻ
→ có API chuyển đổi với LocalDateTime và Instant
```

Java cung cấp chuyển đổi trực tiếp:

Trong đoạn mã dưới, `sqlDate`, `sqlTime` và `timestamp` được giả sử là **các giá trị JDBC cũ đã được API hoặc tầng cơ sở dữ liệu cung cấp**; mục tiêu của đoạn mã chỉ là minh họa bước chuyển đổi sang `java.time` và ngược lại.

```java
LocalDate date = sqlDate.toLocalDate();
java.sql.Date sqlDateAgain = java.sql.Date.valueOf(date);

LocalTime time = sqlTime.toLocalTime();
java.sql.Time sqlTimeAgain = java.sql.Time.valueOf(time);

LocalDateTime localDateTime = timestamp.toLocalDateTime();
java.sql.Timestamp timestampFromLocal = java.sql.Timestamp.valueOf(localDateTime);

Instant instant = timestamp.toInstant();
java.sql.Timestamp timestampFromInstant = java.sql.Timestamp.from(instant);
```

Nhưng các chuyển đổi cũ này **không hoàn toàn đối xứng** và có vài bẫy quan trọng:

```text
java.sql.Date.toInstant()
→ không được hỗ trợ, ném UnsupportedOperationException

java.sql.Time.toInstant()
→ không được hỗ trợ, ném UnsupportedOperationException

java.sql.Time.valueOf(LocalTime)
→ chỉ giữ giờ/phút/giây
→ phần nanosecond của LocalTime bị mất

Timestamp.from(Instant) / timestamp.toInstant()
→ chuyển đổi theo dòng thời gian

Timestamp.valueOf(LocalDateTime) / timestamp.toLocalDateTime()
→ diễn giải ngày-giờ cục bộ của Timestamp cũ
→ có thể phụ thuộc múi giờ mặc định khi ánh xạ giá trị mili giây cũ ↔ các trường cục bộ
```

Vì vậy nếu nghiệp vụ sở hữu một `Instant`, ưu tiên ranh giới `Timestamp ↔ Instant`; nếu lược đồ thật sự mang ý nghĩa ngày-giờ cục bộ thì dùng `Timestamp ↔ LocalDateTime` có chủ đích và kiểm soát các giả định về múi giờ mặc định trong tầng JDBC/cơ sở dữ liệu.

Đừng vì cả `Timestamp` và `Instant` đều có thể liên quan dòng thời gian mà mặc định mọi cột `TIMESTAMP` trong cơ sở dữ liệu đều có cùng ý nghĩa. Ý nghĩa của kiểu SQL, cách xử lý múi giờ và độ chính xác còn phụ thuộc cơ sở dữ liệu, lược đồ và trình điều khiển. Ranh giới cơ sở dữ liệu phải được thiết kế rõ thay vì suy từ tên Java lớp.

### JDBC hiện đại có thể làm việc trực tiếp với java.time

Từ JDBC 4.2, nhiều kiểu `java.time` có ánh xạ chuẩn trực tiếp qua `setObject` / `getObject`, nên mã mới **không bắt buộc phải đi vòng qua `java.sql.Date/Time/Timestamp`** chỉ để truy cập cơ sở dữ liệu.

Ví dụ ở trình điều khiển/cơ sở dữ liệu hỗ trợ ánh xạ tương ứng:

Trong đoạn mã này, `preparedStatement` là một `PreparedStatement` đã được ứng dụng tạo để gửi tham số xuống cơ sở dữ liệu, còn `resultSet` là `ResultSet` nhận từ truy vấn. Chúng là các đối tượng tại ranh giới JDBC, không phải kiểu ngày-giờ.

```java
LocalDate invoiceDate = LocalDate.of(2026, 9, 27);
preparedStatement.setObject(1, invoiceDate);

LocalDate loadedDate = resultSet.getObject("invoice_date", LocalDate.class);

OffsetDateTime occurredAt = OffsetDateTime.parse("2026-09-27T17:30:00+07:00");
preparedStatement.setObject(2, occurredAt);

OffsetDateTime loadedOccurredAt = resultSet.getObject(
        "occurred_at",
        OffsetDateTime.class
);
```

`LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetTime` và `OffsetDateTime` có ánh xạ JDBC 4.2 chuẩn. Tuy nhiên kiểu cơ sở dữ liệu thực tế, khả năng của trình điều khiển và ý nghĩa múi giờ vẫn phải được kiểm tra theo lược đồ/nhà cung cấp. Đặc biệt, đừng suy rằng mọi trình điều khiển đều ánh xạ trực tiếp `Instant` giống nhau chỉ vì ứng dụng dùng `Instant` trong nghiệp vụ.

### Không chuyển đổi qua chuỗi nếu có API trực tiếp

Cách chuyển đổi không cần thiết:

```text
Date → định dạng thành String → phân tích thành LocalDateTime
```

Nó đưa định dạng/`Locale`/múi giờ mặc định vào giữa một chuyển đổi có thể làm trực tiếp và an toàn hơn.

## <a id="system-default-zone-risk">Rủi ro của múi giờ mặc định hệ thống</a>

Các lời gọi như:

```java
ZoneId.systemDefault();
LocalDate.now();
ZonedDateTime.now();
```

có thể phụ thuộc cấu hình máy nếu không truyền `Clock`/múi giờ rõ ràng.

Lỗi thường xuất hiện khi:

```text
máy phát triển   → Asia/Ho_Chi_Minh
CI               → UTC
môi trường thực tế → UTC hoặc vùng khác
```

Cùng một `Instant` có thể thuộc các `LocalDate` khác nhau khi được chiếu qua các múi giờ khác nhau, đặc biệt quanh nửa đêm cục bộ.

### Chuyển đổi nguy hiểm

```java
LocalDateTime local = ...;
Instant instant = local.atZone(ZoneId.systemDefault()).toInstant();
```

Nếu `local` thuộc một múi giờ nghiệp vụ cụ thể, dùng giá trị mặc định hệ thống là giả định ẩn. Hãy truyền đúng `ZoneId` từ nghiệp vụ/cấu hình.

## <a id="timestamp-storage-boundary">Chọn cách lưu trữ theo đúng ý nghĩa dữ liệu</a>

Không có một quy tắc “mọi dữ liệu ngày-giờ đều phải lưu UTC” áp dụng cho mọi nghiệp vụ. Câu đúng hơn là: **lưu đủ thông tin để phục hồi đúng ý nghĩa mà nghiệp vụ sở hữu**.

### Sự kiện đã xảy ra

Ví dụ:

```text
paymentCapturedAt
requestReceivedAt
auditEventAt
```

Thường cần một mốc trên dòng thời gian → lưu theo `Instant`/UTC là lựa chọn tự nhiên.

### Lịch tương lai gắn với địa điểm

Ví dụ:

```text
"09:00 ngày 05/10 tại Europe/Paris"
```

Nghiệp vụ có thể cần giữ:

```text
ngày-giờ cục bộ
+ ZoneId
```

vì quy tắc của vùng là một phần của ý định lịch. Chỉ giữ `Instant` hoặc độ lệch được tính ở thời điểm ban đầu có thể mất thông tin cần cho việc đổi lịch, hiển thị hoặc thay đổi quy tắc múi giờ trong tương lai.

### Ngày và giờ nghiệp vụ cục bộ

Ngày sinh, ngày hóa đơn hoặc giờ mở cửa không nên bị ép thành `Instant` nếu nghiệp vụ không có ý nghĩa trên dòng thời gian.

### Danh sách kiểm tra trước khi chọn cột và kiểu dữ liệu

```text
1. Đây là giá trị theo lịch hay sự kiện trên dòng thời gian?
2. Có cần giữ một vùng múi giờ có tên không?
3. Độ lệch UTC là dữ liệu gốc hay chỉ là kết quả phân giải?
4. Đây là lịch tương lai hay sự kiện đã xảy ra?
5. Cơ sở dữ liệu giữ độ chính xác tới mức nào?
6. Khi đọc lại, ứng dụng cần phục hồi chính xác thông tin nào?
```

Nếu trả lời được các câu đó, lựa chọn giữa `LocalDate`, `LocalDateTime`, `Instant`, `OffsetDateTime` và `ZonedDateTime` sẽ xuất phát từ nghiệp vụ thay vì một quy ước mơ hồ.

Kết thúc mô-đun, mô hình tư duy nên là:

```text
Hiểu ý nghĩa thời gian trước
        ↓
chọn kiểu thời gian giữ đúng thông tin
        ↓
thêm múi giờ/độ lệch chỉ khi nghiệp vụ cần
        ↓
chọn Duration hay Period theo ý nghĩa
        ↓
định dạng/phân tích chuỗi tại ranh giới dữ liệu
        ↓
truyền Clock vào để kiểm soát "thời điểm hiện tại"
        ↓
tránh múi giờ mặc định ngầm và kiểm soát chuyển đổi API cũ
```
