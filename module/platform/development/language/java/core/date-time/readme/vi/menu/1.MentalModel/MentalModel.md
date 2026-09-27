# Mô hình Date-Time trong Java

Date-Time nhìn qua có vẻ đơn giản vì con người sử dụng ngày và giờ mỗi ngày. Nhưng trong phần mềm, câu “lưu thời gian” có thể đang nói về nhiều thứ hoàn toàn khác nhau:

```text
Ngày sinh của một người
→ cần ngày trên lịch

Giờ mở cửa 08:30
→ cần giờ trong ngày

Cuộc họp 2026-10-05 lúc 09:00 tại Hà Nội
→ cần ngày + giờ địa phương + quy tắc múi giờ

Một request đến server tại đúng thời điểm nào
→ cần một điểm không mơ hồ trên timeline toàn cầu
```

Nếu gom tất cả các ý nghĩa đó vào một biến tên `timestamp`, code có thể vẫn compile nhưng domain đã bị mô hình hóa sai. Mục tiêu đầu tiên của module này vì thế không phải học thuộc class, mà là biết **bài toán đang nói về loại thời gian nào**.

Ta sẽ dùng một ví dụ xuyên suốt: hệ thống đặt lịch họp. Người dùng nhập ngày, giờ và khu vực; hệ thống cần hiển thị lại đúng theo nơi người dùng ở, đồng thời lưu được một thời điểm toàn cầu rõ ràng khi cuộc họp đã được xác định.

## <a id="date-time-domains">Date-Time là gì và vì sao cần nhiều mô hình?</a>

### Date-Time trong phần mềm thực ra là gì?

Ở mức đơn giản nhất, **Date-Time là nhóm khái niệm và API dùng để mô hình hóa ngày, giờ, thời điểm, múi giờ và khoảng thời gian trong chương trình**.

Nó không chỉ trả lời một câu hỏi “mấy giờ rồi?”. Một application có thể cần trả lời nhiều loại câu hỏi khác nhau:

```text
Ngày nào?
→ calendar date

Mấy giờ trong ngày?
→ wall-clock time

Sự kiện xảy ra chính xác lúc nào trên timeline?
→ instant

Giờ địa phương đó thuộc khu vực nào?
→ time zone

Hai thời điểm cách nhau bao lâu?
→ duration / period

Làm sao đổi object thời gian thành text và ngược lại?
→ formatting / parsing
```

Vì những câu hỏi này có semantics khác nhau, Java không cố ép chúng vào một class duy nhất.

### `java.time` là gì và tại sao Java cần nó?

`java.time` là **Date-Time API hiện đại của Java**, xuất hiện từ Java 8 và là API chính để code mới biểu diễn ngày, giờ, instant, duration và time zone.

Trước đó Java đã có những API như `java.util.Date` và `Calendar`. Chúng vẫn tồn tại vì compatibility, nhưng có nhiều đặc điểm khó dùng cho code hiện đại: tên/type không diễn đạt domain rõ bằng `LocalDate`/`Instant`, nhiều API mutable, và việc xử lý calendar/time-zone dễ dựa vào state/default ngầm.

`java.time` cải thiện bài toán theo hướng:

```text
Mỗi loại ý nghĩa thời gian
→ một type rõ ràng

Date-time values chính
→ immutable

Zone / offset
→ model riêng thay vì assumption ẩn

Formatting / parsing
→ API riêng, formatter có thể tái sử dụng an toàn

"now"
→ có Clock để biến nguồn thời gian thành dependency rõ ràng
```

Nói ngắn gọn:

> **Vai trò của `java.time` là giúp code biểu diễn đúng loại thông tin thời gian mà domain thực sự sở hữu, rồi cung cấp các operation chuẩn để tạo, chuyển đổi, tính toán, so sánh, format và parse các value đó.**

### Date-Time API gồm những nhóm thành phần nào?

Không cần thuộc hết class ngay. Hãy nhìn API theo **vai trò**:

```text
1. Local calendar / wall-clock values
   ├── LocalDate
   ├── LocalTime
   └── LocalDateTime

2. Global timeline
   └── Instant

3. Zone / offset
   ├── ZoneId
   ├── ZoneOffset
   ├── ZonedDateTime
   ├── OffsetDateTime
   └── OffsetTime

4. Amount of time
   ├── Duration
   └── Period

5. Smaller calendar concepts
   ├── Year
   ├── YearMonth
   ├── MonthDay
   ├── Month
   └── DayOfWeek

6. Text boundary
   └── DateTimeFormatter / DateTimeFormatterBuilder

7. Current-time source
   └── Clock

8. Supporting abstractions
   ├── Temporal / TemporalAccessor
   ├── TemporalAmount / TemporalUnit
   ├── ChronoUnit
   ├── TemporalAdjuster / TemporalAdjusters
   └── ZoneRules
```

Các supporting abstraction ở nhóm 8 giúp nhiều date-time type dùng chung vocabulary. Người mới **không cần học chúng trước các type chính**; chỉ cần biết chúng tồn tại để khi gặp signature như `plus(TemporalAmount)` hay `get(TemporalField)` trong Javadoc không tưởng đó là một hệ thống hoàn toàn khác.

Đọc nhanh các abstraction này như sau:

| Abstraction | Vai trò đơn giản |
| --- | --- |
| `TemporalAccessor` | object cho phép **đọc** temporal field mà nó hỗ trợ |
| `Temporal` | temporal object có vocabulary chung cho `with`, `plus`, `minus`, `until` |
| `TemporalField` / `ChronoField` | mô tả **field nào** đang được hỏi, ví dụ day-of-month hoặc hour-of-day |
| `TemporalUnit` / `ChronoUnit` | mô tả **đơn vị nào**, ví dụ DAYS, HOURS, MONTHS |
| `TemporalAmount` | một **amount có cấu trúc**, điển hình là `Duration` hoặc `Period` |
| `TemporalAdjuster` | strategy/object biết cách điều chỉnh một temporal value |
| `TemporalAdjusters` | utility cung cấp các adjuster thường dùng như ngày cuối tháng |
| `TemporalQuery` | strategy để truy vấn/extract thông tin từ một temporal object |

Đây là **shared API vocabulary**, không phải tám loại “thời gian” mới. Hãy học `LocalDate`, `Instant`, zone, `Duration`... trước; các abstraction này sẽ dần có ý nghĩa khi gặp lại trong method signature.

Về package, API cũng được chia theo trách nhiệm:

```text
java.time
→ các type chính

java.time.format
→ format / parse

java.time.temporal
→ field, unit, query, adjuster và abstraction dùng chung

java.time.zone
→ zone rules và transition

java.time.chrono
→ calendar system khác ISO; là boundary nâng cao, không phải learning path chính của module này
```

### ISO-8601 và UTC — đọc ký hiệu trước khi đọc code

**ISO-8601** là tiêu chuẩn quốc tế mô tả cách biểu diễn ngày và thời gian theo một vocabulary thống nhất. Các type chính như `LocalDate`, `LocalTime` và `LocalDateTime` dùng ISO calendar model, và nhiều formatter mặc định của `java.time` sử dụng dạng text ISO-8601.

Trong module, bạn sẽ thường thấy những chuỗi như:

```text
2026-10-05
→ date

09:30:15
→ time

2026-10-05T09:30:15
→ chữ T ngăn phần date và time

2026-10-05T02:30:15Z
→ Z nghĩa là offset +00:00, tức UTC

2026-10-05T09:30:15+07:00
→ local date-time + offset +07:00

2026-10-05T09:30:15+07:00[Asia/Ho_Chi_Minh]
→ local fields + resolved offset + named ZoneId
```

**UTC (Coordinated Universal Time)** là mốc tham chiếu toàn cầu mà offset được tính tương đối so với nó. UTC không phải “múi giờ địa phương của mọi người”; nó là reference giúp nhiều hệ thống nói về cùng timeline.

Ở đây **timeline** có thể hiểu đơn giản là một đường khái niệm sắp các thời điểm theo thứ tự trước → sau; một `Instant` xác định một vị trí trên đường đó.

`LocalDate`, `LocalDateTime` và các `Local*` concrete type tương ứng là **ISO calendar types**; chúng không phải object có thể đổi sang calendar system khác bằng configuration. Trong API nâng cao, từ **chronology** chỉ calendar system/quy tắc lịch đang được dùng. Nếu application thật sự cần calendar system khác ISO, `java.time.chrono` có các type/abstraction riêng như `ChronoLocalDate`. Đổi `Locale` để hiển thị text cũng không biến một `LocalDate` thành chronology khác.

### Exception thường gặp trong java.time

Người mới sẽ gặp một vài exception lặp lại trong module:

```text
DateTimeException
→ base runtime exception cho nhiều lỗi date-time

DateTimeParseException
→ parse text thất bại
→ là một DateTimeException

UnsupportedTemporalTypeException
→ temporal type không hỗ trợ field/unit đang yêu cầu
→ cũng là một DateTimeException
```

Ví dụ `LocalDate.of(2026, 2, 30)` có thể ném `DateTimeException`; parse text sai có thể ném `DateTimeParseException`; yêu cầu `HOURS` trên `LocalDate` có thể dẫn tới `UnsupportedTemporalTypeException`.

### DST là gì? — biết trước khi gặp gap/overlap

**DST (Daylight Saving Time)** là chính sách mà **một số** quốc gia/khu vực thay đổi offset/local clock trong một phần của năm. Không phải nơi nào cũng dùng DST, và rule có thể thay đổi theo quyết định pháp lý.

Khi clock đổi vì một zone transition, có hai tình huống người mới cần biết tên trước:

```text
gap
→ clock nhảy về phía trước
→ một khoảng local time không tồn tại

overlap
→ clock quay lại
→ một khoảng local time xuất hiện hai lần với hai offset khác nhau
```

Đây là lý do `LocalDateTime + ZoneId` đôi khi không map đơn giản 1:1 tới `Instant`. Chưa cần nhớ cách Java resolve ở đây; chapter về zone và pitfalls sẽ đi từng bước.

### KHÁI NIỆM — “thời gian” không phải chỉ có một nghĩa

Trong Java hiện đại, `java.time` không có một class duy nhất đại diện cho mọi khái niệm thời gian. Thay vào đó, mỗi type giữ một **ý nghĩa domain cụ thể**.

Mental model cơ bản:

```text
LocalDate
→ ngày trên lịch
→ 2026-10-05

LocalTime
→ giờ trong ngày
→ 09:00

LocalDateTime
→ ngày + giờ địa phương
→ 2026-10-05T09:00
→ vẫn chưa nói nơi nào trên Trái Đất

Instant
→ một điểm chính xác trên timeline UTC
→ có thể so sánh tuyệt đối giữa các hệ thống

ZoneOffset
→ độ lệch cụ thể so với UTC, ví dụ +07:00

ZoneId
→ tên vùng chứa tập quy tắc múi giờ, ví dụ Asia/Ho_Chi_Minh

ZonedDateTime
→ ngày + giờ địa phương + ZoneId

OffsetDateTime
→ ngày + giờ địa phương + một ZoneOffset cụ thể
```

Điểm quan trọng là mỗi type **cố ý không biết** một số thông tin.

Ví dụ:

```java
LocalDate birthday = LocalDate.of(1993, 7, 20);
```

Ngày sinh không cần phải biết “múi giờ UTC+7” hay “lúc 00:00”. Gắn các thông tin đó vào sẽ tạo ra dữ liệu giả mà domain không hề sở hữu.

Ngược lại:

```java
Instant receivedAt = Instant.now();
```

Một audit event cần biết request xảy ra tại điểm nào trên timeline, nên `Instant` phù hợp hơn `LocalDateTime`.

### VÌ SAO — một “timestamp” chung gây vấn đề gì?

Giả sử ta chỉ lưu:

```text
2026-11-01 01:30
```

Giá trị này chưa đủ để biết một instant toàn cầu. Nó không nói `01:30` ở Việt Nam, New York hay London. Ở vùng có DST, cùng một giờ địa phương thậm chí có thể xuất hiện hai lần trong ngày chuyển từ giờ mùa hè về giờ chuẩn.

Ngược lại, nếu ta lưu mọi thứ thành epoch milliseconds, ta lại làm mất ý nghĩa tự nhiên của một số domain:

```text
Sinh nhật 20/07
Giờ mở cửa 08:30
Ngày chốt sổ cuối tháng
```

Những khái niệm này trước hết là **calendar/wall-clock concepts**, không phải “một số milliseconds kể từ epoch”.

Vì thế `java.time` tách domain thành các type rõ ràng thay vì khuyến khích một type đa năng.

### Human calendar time và machine timeline

Có thể chia tư duy thành hai phía lớn:

```text
Human-facing calendar / wall clock
├── LocalDate
├── LocalTime
└── LocalDateTime

Global machine timeline
└── Instant

Cầu nối giữa hai phía
├── ZoneId / ZoneRules
├── ZoneOffset
├── ZonedDateTime
└── OffsetDateTime
```

Ví dụ một cuộc họp:

```java
LocalDateTime localMeeting = LocalDateTime.of(2026, 10, 5, 9, 0);
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime scheduled = localMeeting.atZone(zone);
Instant globalPoint = scheduled.toInstant();
```

Flow tư duy:

```text
Người dùng nói: 09:00 ngày 05/10/2026
        ↓
LocalDateTime
        ↓ thêm nơi/quy tắc múi giờ
ZoneId
        ↓
ZonedDateTime
        ↓ quy về timeline chung
Instant
```

Đây là xương sống của toàn module.

## <a id="immutable-date-time">Tính bất biến của java.time</a>

Các class date-time chính như `LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `ZonedDateTime`, `Duration` và `Period` được thiết kế theo hướng **immutable**: một object đã tạo không bị thay đổi nội dung bởi các operation như `plusDays` hay `minusHours`.

Ví dụ:

```java
LocalDate original = LocalDate.of(2026, 10, 5);
LocalDate nextDay = original.plusDays(1);

System.out.println(original); // 2026-10-05
System.out.println(nextDay);  // 2026-10-06
```

`plusDays(1)` không sửa `original`. Nó trả về một value mới.

Điều này quan trọng vì date-time values thường được truyền qua nhiều layer. Nếu một method có thể âm thầm sửa object dùng chung, reasoning về thời gian sẽ rất khó:

```text
service A nhận ngày
        ↓
service B cộng thêm 1 ngày ngay trên cùng object
        ↓
service A bỗng thấy dữ liệu thay đổi
```

Immutable value tránh kiểu side effect đó và phù hợp với cách ta nhìn ngày/giờ như **giá trị** hơn là object có lifecycle mutable.

### Value-based: so giá trị, đừng dựa vào identity

Nhiều class chính của `java.time` được thiết kế như **value-based class**. Application nên coi hai object mang cùng value là tương đương về mặt domain:

```java
LocalDate a = LocalDate.of(2026, 10, 5);
LocalDate b = LocalDate.of(2026, 10, 5);

boolean sameValue = a.equals(b); // true
```

Không viết logic dựa vào `a == b`, object identity hoặc dùng date-time object làm monitor để `synchronized`. API contract quan tâm **value**, không hứa identity của instance.

Immutability cũng giúp các value type chính được chia sẻ an toàn giữa nhiều đoạn code/thread theo contract thread-safe của chúng, thay vì cần defensive mutation control như API mutable cũ.

### Operation kiểu “with/plus/minus”

Hãy đọc các method này theo quy tắc:

```text
withX(...)
→ tạo value mới với một field được thay thế/điều chỉnh

plusX(...)
→ tạo value mới sau khi cộng

minusX(...)
→ tạo value mới sau khi trừ
```

Ví dụ:

```java
LocalDateTime meeting = LocalDateTime.of(2026, 10, 5, 9, 0);

LocalDateTime moved = meeting
        .withHour(10)
        .plusDays(2);
```

`meeting` vẫn là `2026-10-05T09:00`; `moved` là value khác.

Đừng quên gán kết quả:

```java
meeting.plusDays(1); // kết quả bị bỏ đi
```

Đây là lỗi rất phổ biến khi người học quen với API mutable.

## <a id="choose-date-time-type">Chọn type theo ý nghĩa domain</a>

Câu hỏi đúng không phải là “class nào có nhiều method nhất?”, mà là **“dữ liệu này thật sự biết điều gì?”**.

| Bài toán | Type khởi đầu phù hợp | Vì sao |
| --- | --- | --- |
| ngày sinh | `LocalDate` | chỉ là ngày lịch |
| giờ mở cửa hằng ngày | `LocalTime` | chỉ là giờ trong ngày |
| lịch học 09:00 ngày 05/10 trước khi chọn địa điểm | `LocalDateTime` | có ngày + giờ nhưng chưa có zone |
| thời điểm request đến server | `Instant` | cần điểm toàn cầu không mơ hồ |
| lịch họp 09:00 tại Paris | `ZonedDateTime` | cần cả local fields và quy tắc zone |
| timestamp từ protocol có `+07:00` nhưng không có region | `OffsetDateTime` | dữ liệu biết offset cụ thể nhưng không biết ZoneId |
| kỳ thanh toán `2026-10` | `YearMonth` | biết năm + tháng nhưng cố ý không invent ngày |
| sinh nhật lặp lại `--07-20` | `MonthDay` | biết tháng + ngày nhưng không gắn năm cụ thể |
| chỉ một năm như `2026` | `Year` | domain chỉ sở hữu year |
| `09:30+07:00` từ protocol | `OffsetTime` | time-of-day + offset nhưng không có date/region |

### Quy tắc chọn nhanh

```text
Chỉ ngày?                      → LocalDate
Chỉ giờ trong ngày?            → LocalTime
Ngày + giờ, chưa có zone?       → LocalDateTime
Cần điểm tuyệt đối trên timeline?→ Instant
Cần ngày/giờ + region rules?    → ZonedDateTime
Cần ngày/giờ + fixed offset?    → OffsetDateTime
Cần year-month nhưng không có day?→ YearMonth
Cần month-day nhưng không có year?→ MonthDay
```

Các type như `Year`, `YearMonth`, `MonthDay` và `OffsetTime` không có chapter riêng trong learning path này vì mechanics của chúng được suy ra từ các concept chính. Nhưng chúng rất quan trọng về mặt modeling: **nếu domain chỉ biết year-month, đừng invent ngày `01`; nếu domain chỉ biết month-day, đừng invent một năm giả**.

### “Local” không có nghĩa là “múi giờ máy hiện tại”

Tên `LocalDateTime` dễ gây hiểu nhầm. `Local` ở đây nghĩa là **các field ngày/giờ địa phương chưa gắn zone/offset**, không phải tự động lấy system default zone.

```java
LocalDateTime local = LocalDateTime.of(2026, 10, 5, 9, 0);
```

Object này không chứa:

```text
Asia/Ho_Chi_Minh
+07:00
UTC
```

Muốn biến nó thành một timeline point, application phải cung cấp zone/offset một cách có chủ đích.

### Lộ trình module

Từ đây, các chapter đi theo đúng câu hỏi domain:

```text
Ngày?                → LocalDate
Giờ?                 → LocalTime
Ngày + giờ local?    → LocalDateTime
Điểm trên timeline?  → Instant
Offset hay zone?      → ZoneOffset / ZoneId
Gắn zone vào value?   → ZonedDateTime / OffsetDateTime
Khoảng thời gian?     → Duration / Period
Text ↔ date-time?     → Formatting / Parsing
Cộng/trừ/so sánh?     → Arithmetic / Comparison
"Bây giờ" test được? → Clock
API cũ + DST/default? → Legacy Interop / Pitfalls
```

Nếu chỉ nhớ một điều trước khi sang chapter tiếp theo, hãy nhớ: **chọn date-time type theo ý nghĩa dữ liệu, không theo thói quen lưu mọi thứ thành timestamp**.
