# Định dạng và phân tích chuỗi ngày-giờ

Đối tượng ngày-giờ và văn bản là hai cách biểu diễn khác nhau. Đối tượng như `LocalDate` hay `Instant` giữ ý nghĩa thời gian; chuỗi như `27/09/2026`, `2026-09-27` hay `09:30 AM` chỉ là **cách biểu diễn** dùng ở giao diện, tệp, API hoặc giao thức.

Sai lầm phổ biến là để hình dạng chuỗi định dạng trở thành “mô hình thời gian”. Chương này tách rõ hai việc:

```text
format (định dạng)
→ đối tượng thời gian → văn bản

parse (phân tích)
→ văn bản → đối tượng thời gian
```

## <a id="date-time-formatter">DateTimeFormatter và tính bất biến</a>

`DateTimeFormatter` là bộ định dạng và phân tích chuỗi chính của `java.time`. Nó được thiết kế bất biến và an toàn đa luồng, nên có thể tái sử dụng thay vì tạo mới cho từng yêu cầu.

`DateTimeFormatterBuilder` là **bộ dựng dùng để ghép một bộ định dạng phức tạp từ nhiều phần** khi một bộ định dạng có sẵn hoặc một chuỗi mẫu đơn giản chưa đủ, ví dụ khi cần phần tùy chọn, ký tự cố định đặc biệt hoặc nhiều quy tắc phân tích/định dạng kết hợp. Người mới chưa cần dùng nó cho trường hợp cơ bản; chỉ cần biết nó là công cụ xây `DateTimeFormatter`, không phải một kiểu thời gian mới.

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu");

LocalDate date = LocalDate.of(2026, 9, 27);
String text = date.format(formatter);
// 27/09/2026
```

Phân tích chuỗi theo chiều ngược lại:

```java
LocalDate parsed = LocalDate.parse("27/09/2026", formatter);
```

### Quy tắc định dạng thuộc về ranh giới dữ liệu

Một giá trị nghiệp vụ:

```java
LocalDate invoiceDate = LocalDate.of(2026, 9, 27);
```

có thể được hiển thị nhiều cách:

```text
27/09/2026
09/27/2026
2026-09-27
27 Sep 2026
```

Không nên đổi mô hình nghiệp vụ chỉ vì giao diện muốn định dạng khác. Định dạng thuộc về ranh giới hiển thị hoặc tuần tự hóa, không phải bản thân mô hình nghiệp vụ.

### Bộ định dạng có sẵn

Java cung cấp nhiều bộ định dạng chuẩn:

```java
LocalDate date = LocalDate.parse("2026-09-27", DateTimeFormatter.ISO_LOCAL_DATE);
Instant instant = Instant.parse("2026-09-27T10:15:30Z");
OffsetDateTime offset = OffsetDateTime.parse("2026-09-27T17:15:30+07:00");
```

Khi giao thức đã có chuẩn ISO phù hợp, bộ định dạng có sẵn thường an toàn và dễ hiểu hơn tự tạo mẫu tùy ý.

### Định dạng Instant theo ngày-giờ địa phương cần ngữ cảnh múi giờ

`Instant` chỉ biết mốc trên dòng thời gian. Nếu muốn hiển thị nó thành năm/tháng/ngày/giờ của một khu vực, bộ định dạng hoặc bước chuyển đổi phải có múi giờ:

```java
Instant instant = Instant.parse("2026-09-27T07:30:00Z");

DateTimeFormatter display = DateTimeFormatter
        .ofPattern("dd/MM/uuuu HH:mm")
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

String text = display.format(instant);
```

Nếu dùng mẫu tùy chỉnh cần các trường như năm hoặc giờ nhưng không cung cấp múi giờ cho một `Instant`, bộ định dạng không có đủ trường lịch cục bộ để tạo đầu ra tương ứng và thao tác có thể thất bại với `UnsupportedTemporalTypeException`.

Mô hình tư duy:

```text
Instant
→ biết "khi nào"

ZoneId
→ biết "nhìn từ đâu"

DateTimeFormatter
→ biết "viết thành văn bản thế nào"
```

## <a id="format-patterns">Mẫu định dạng và bộ định dạng có sẵn</a>

Mẫu tùy chỉnh hữu ích khi định dạng là yêu cầu thật của giao diện hoặc tệp cũ:

```java
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm");
LocalDateTime value = LocalDateTime.of(2026, 9, 27, 14, 30);

String text = formatter.format(value);
// 27-09-2026 14:30
```

Nhưng ký tự mẫu có ý nghĩa riêng; không nên đoán bằng trực giác.

Ví dụ quan trọng:

```text
MM → tháng trong năm
mm → phút trong giờ

HH → giờ trong ngày 00-23
hh → giờ theo AM/PM 01-12

uuuu → năm liên tục qua các kỷ nguyên (proleptic year)
yyyy → năm trong kỷ nguyên (year-of-era)
YYYY → năm theo tuần (week-based-year)
```

Việc nhầm `MM` và `mm`, hoặc `yyyy` với `YYYY`, là lỗi rất phổ biến.

### `uuuu` và `yyyy`

Trong nhiều bài toán ngày tháng hiện đại, `uuuu` thường dễ dùng hơn khi cần phân tích ISO chặt chẽ vì nó là **năm liên tục qua các kỷ nguyên (proleptic year)**: năm được đánh số liên tục xuyên qua các kỷ nguyên, có cả năm `0` và năm âm khi đi lùi trước kỷ nguyên hiện tại.

`yyyy` là **năm trong kỷ nguyên (year-of-era)**: số năm nằm bên trong một kỷ nguyên như CE/BCE, nên để diễn giải đầy đủ còn cần ngữ cảnh kỷ nguyên.

`YYYY` lại là **năm theo tuần (week-based-year)**: năm được xác định theo quy tắc tuần, không phải năm dương lịch thông thường. Quanh cuối hoặc đầu năm, một ngày như `2025-12-29` có thể thuộc năm theo tuần khác năm dương lịch tùy quy tắc tuần. Vì vậy đừng dùng `YYYY-MM-dd` khi ý định thật sự là ngày theo lịch.

```java
DateTimeFormatter strictDate = DateTimeFormatter
        .ofPattern("dd/MM/uuuu")
        .withResolverStyle(ResolverStyle.STRICT);
```

Không cần biến quy tắc này thành “luôn luôn cấm yyyy”; điều cần học là **ký tự mẫu mang ý nghĩa**, không chỉ quyết định hình dạng đầu ra.

### Locale ảnh hưởng văn bản hiển thị

Mẫu có tên tháng hoặc tên thứ cần `Locale` rõ ràng:

```java
DateTimeFormatter english = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
```

`Locale` ở đây là ngữ cảnh ngôn ngữ/khu vực dùng cho **hiển thị**, ví dụ tên tháng, tên thứ hoặc quy ước văn bản. Nó không thay thế `ZoneId` và không quyết định `Instant`. Phần bản địa hóa chuyên sâu thuộc mô-đun `localization`; ở đây chỉ cần biết bộ định dạng có thể phụ thuộc `Locale` và không nên vô tình dùng giá trị mặc định của máy khi hợp đồng đầu ra phải ổn định.

## <a id="strict-smart-lenient">ResolverStyle: STRICT, SMART, LENIENT</a>

Phân tích chuỗi không chỉ là tách ký tự. Sau khi đọc các trường, Java còn phải **phân giải** chúng thành một giá trị thời gian hợp lệ.

Ba chế độ phân giải chính:

```text
STRICT
→ yêu cầu các trường hợp lệ chính xác theo quy tắc

SMART
→ cho phép một số điều chỉnh hợp lý theo ý nghĩa của bộ định dạng

LENIENT
→ cho phép tràn giá trị và chuẩn hóa rộng hơn
```

Với bộ định dạng tạo bằng `DateTimeFormatter.ofPattern(...)`, **chế độ phân giải mặc định là `SMART`**. Các bộ định dạng dựng sẵn có thể mang cấu hình phân giải riêng, vì vậy đừng suy rộng rằng mọi `DateTimeFormatter` đều mặc định `SMART`. Nếu hợp đồng đầu vào yêu cầu từ chối dữ liệu lịch không hợp lệ một cách chặt chẽ, hãy kiểm tra hoặc cấu hình `ResolverStyle` có chủ đích.

Ví dụ:

```java
DateTimeFormatter strict = DateTimeFormatter
        .ofPattern("dd/MM/uuuu")
        .withResolverStyle(ResolverStyle.STRICT);

LocalDate.parse("31/02/2026", strict); // DateTimeParseException
```

Với đầu vào từ người dùng/API có hợp đồng chặt, `STRICT` thường dễ suy luận hơn vì dữ liệu lịch không hợp lệ bị từ chối thay vì âm thầm điều chỉnh.

### SMART không thay thế kiểm tra quy tắc nghiệp vụ

Ngay cả phân tích thành công, dữ liệu có thể vẫn vi phạm quy tắc ứng dụng:

```text
ngày hợp lệ trên lịch
≠
ngày hợp lệ cho đặt lịch
```

Ví dụ ngày đặt lịch có thể phải nằm trong 90 ngày tới hoặc không rơi vào ngày nghỉ. `DateTimeFormatter` chỉ xử lý văn bản ngày-giờ, không thay thế kiểm tra nghiệp vụ.

## <a id="parse-target-type">Phân tích chuỗi vào đúng kiểu thời gian</a>

Chuỗi chứa thông tin nào thì kiểu đích phải phù hợp.

```java
LocalDate date = LocalDate.parse("2026-09-27");
LocalDateTime local = LocalDateTime.parse("2026-09-27T14:30:00");
OffsetDateTime offset = OffsetDateTime.parse("2026-09-27T14:30:00+07:00");
ZonedDateTime zoned = ZonedDateTime.parse("2026-09-27T14:30:00+07:00[Asia/Ho_Chi_Minh]");
Instant instant = Instant.parse("2026-09-27T07:30:00Z");
```

### Đừng phân tích chuỗi rồi vứt mất thông tin

Nếu đầu vào có độ lệch UTC:

```text
2026-09-27T14:30:00+07:00
```

phân tích thành `LocalDateTime` sau khi tự cắt phần `+07:00` sẽ làm mất ngữ cảnh dòng thời gian. Hãy phân tích thành `OffsetDateTime` hoặc kiểu phù hợp trước, rồi chuyển đổi có chủ đích.

### Đừng tự tạo thông tin mà chuỗi đầu vào không có

Nếu đầu vào chỉ là:

```text
2026-09-27T14:30:00
```

không được tự kết luận đó là UTC hoặc múi giờ mặc định hệ thống trừ khi hợp đồng bên ngoài nói như vậy.

Mô hình tư duy:

```text
những trường thực sự có trong chuỗi
        ↓
phân tích vào kiểu giữ đúng ý nghĩa
        ↓
chỉ bổ sung múi giờ/độ lệch khi hợp đồng hoặc nghiệp vụ cung cấp
```

Sau khi đối tượng đã được phân tích đúng, bước tiếp theo là phép toán và so sánh: cùng là cộng/trừ/so sánh nhưng mỗi kiểu thời gian có ý nghĩa riêng.
