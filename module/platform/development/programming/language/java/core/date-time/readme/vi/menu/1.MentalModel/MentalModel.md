# Ngày và thời gian là gì, vì sao cần nhiều mô hình?

Ngày-giờ nhìn qua có vẻ đơn giản vì con người sử dụng ngày và giờ mỗi ngày. Nhưng trong phần mềm, câu “lưu thời gian” có thể đang nói về nhiều thứ hoàn toàn khác nhau:

```text
Ngày sinh của một người
→ cần ngày trên lịch

Giờ mở cửa 08:30
→ cần giờ trong ngày

Cuộc họp 2026-10-05 lúc 09:00 tại Hà Nội
→ cần ngày + giờ địa phương + quy tắc múi giờ

Một yêu cầu đến máy chủ tại đúng thời điểm nào
→ cần một mốc không mơ hồ trên dòng thời gian toàn cục
```

Nếu gom tất cả các ý nghĩa đó vào một biến tên `timestamp`, mã có thể vẫn biên dịch nhưng nghiệp vụ đã bị mô hình hóa sai. Mục tiêu đầu tiên của mô-đun này vì thế không phải học thuộc lớp, mà là biết **bài toán đang nói về loại thời gian nào**.

Ta sẽ dùng một ví dụ xuyên suốt: hệ thống đặt lịch họp. Người dùng nhập ngày, giờ và khu vực; hệ thống cần hiển thị lại đúng theo nơi người dùng ở, đồng thời lưu được một thời điểm toàn cầu rõ ràng khi cuộc họp đã được xác định.

## <a id="date-time-domains">Ngày và thời gian là gì, vì sao cần nhiều mô hình?</a>

### Ngày và thời gian trong phần mềm thực ra là gì?

Ở mức đơn giản nhất, **ngày-giờ là nhóm khái niệm và API dùng để mô hình hóa ngày, giờ, thời điểm, múi giờ và khoảng thời gian trong chương trình**.

Nó không chỉ trả lời một câu hỏi “mấy giờ rồi?”. Một ứng dụng có thể cần trả lời nhiều loại câu hỏi khác nhau:

```text
Ngày nào?
→ ngày theo lịch

Mấy giờ trong ngày?
→ giờ theo đồng hồ cục bộ

Sự kiện xảy ra chính xác lúc nào trên dòng thời gian?
→ Instant

Giờ địa phương đó thuộc khu vực nào?
→ múi giờ

Hai thời điểm cách nhau bao lâu?
→ Duration / Period

Làm sao đổi đối tượng thời gian thành văn bản và ngược lại?
→ định dạng / phân tích chuỗi
```

Vì những câu hỏi này có ý nghĩa khác nhau, Java không cố ép chúng vào một lớp duy nhất.

### `java.time` là gì và tại sao Java cần nó?

`java.time` là **API ngày-giờ hiện đại của Java**, xuất hiện từ Java 8 và là API chính để mã mới biểu diễn ngày, giờ, `Instant`, khoảng thời gian và múi giờ.

Trước đó Java đã có những API như `java.util.Date` và `Calendar`. Chúng vẫn tồn tại vì tương thích, nhưng có nhiều đặc điểm khó dùng cho mã hiện đại: tên/kiểu không diễn đạt nghiệp vụ rõ bằng `LocalDate`/`Instant`, nhiều API có thể thay đổi, và việc xử lý lịch/múi giờ dễ dựa vào trạng thái/mặc định ngầm.

`java.time` cải thiện bài toán theo hướng:

```text
Mỗi loại ý nghĩa thời gian
→ một kiểu rõ ràng

Các giá trị ngày-giờ chính
→ bất biến

Múi giờ / độ lệch UTC
→ mô hình riêng thay vì giả định ẩn

Định dạng / phân tích chuỗi
→ API riêng, bộ định dạng có thể tái sử dụng an toàn

"thời điểm hiện tại"
→ có Clock để biến nguồn thời gian thành phụ thuộc rõ ràng
```

Nói ngắn gọn:

> **Vai trò của `java.time` là giúp mã biểu diễn đúng loại thông tin thời gian mà nghiệp vụ thực sự sở hữu, rồi cung cấp các thao tác chuẩn để tạo, chuyển đổi, tính toán, so sánh, định dạng và phân tích các giá trị đó.**

### API ngày-giờ gồm những nhóm thành phần nào?

Không cần thuộc hết lớp ngay. Hãy nhìn API theo **vai trò**:

```text
1. Giá trị theo lịch / đồng hồ cục bộ
   ├── LocalDate
   ├── LocalTime
   └── LocalDateTime

2. Dòng thời gian toàn cục
   └── Instant

3. Múi giờ / độ lệch UTC
   ├── ZoneId
   ├── ZoneOffset
   ├── ZonedDateTime
   ├── OffsetDateTime
   └── OffsetTime

4. Lượng thời gian
   ├── Duration
   └── Period

5. Các khái niệm lịch nhỏ hơn
   ├── Year
   ├── YearMonth
   ├── MonthDay
   ├── Month
   └── DayOfWeek

6. Ranh giới văn bản
   └── DateTimeFormatter / DateTimeFormatterBuilder

7. Nguồn thời gian hiện tại
   └── Clock

8. Các API dùng chung hỗ trợ
   ├── Temporal / TemporalAccessor
   ├── TemporalAmount / TemporalUnit
   ├── ChronoUnit
   ├── TemporalAdjuster / TemporalAdjusters
   └── ZoneRules
```

Các API dùng chung ở nhóm 8 giúp nhiều kiểu ngày-giờ dùng chung từ vựng. Người mới **không cần học chúng trước các kiểu chính**; chỉ cần biết chúng tồn tại để khi gặp chữ ký như `plus(TemporalAmount)` hay `get(TemporalField)` trong Javadoc không tưởng đó là một hệ thống hoàn toàn khác.

Đọc nhanh các thành phần này như sau:

| Khái niệm trừu tượng | Vai trò đơn giản |
| --- | --- |
| `TemporalAccessor` | đối tượng cho phép **đọc** trường thời gian mà nó hỗ trợ |
| `Temporal` | đối tượng thời gian có từ vựng chung cho `with`, `plus`, `minus`, `until` |
| `TemporalField` / `ChronoField` | mô tả **trường nào** đang được hỏi, ví dụ ngày trong tháng hoặc giờ trong ngày |
| `TemporalUnit` / `ChronoUnit` | mô tả **đơn vị nào**, ví dụ DAYS, HOURS, MONTHS |
| `TemporalAmount` | một **lượng có cấu trúc**, điển hình là `Duration` hoặc `Period` |
| `TemporalAdjuster` | chiến lược/đối tượng biết cách điều chỉnh một giá trị thời gian |
| `TemporalAdjusters` | tiện ích cung cấp các bộ điều chỉnh thường dùng như ngày cuối tháng |
| `TemporalQuery` | chiến lược để truy vấn/lấy thông tin từ một đối tượng thời gian |

Đây là **từ vựng API dùng chung**, không phải tám loại “thời gian” mới. Hãy học `LocalDate`, `Instant`, múi giờ, `Duration`... trước; các thành phần này sẽ dần có ý nghĩa khi gặp lại trong chữ ký phương thức.

Về gói, API cũng được chia theo trách nhiệm:

```text
java.time
→ các kiểu chính

java.time.format
→ định dạng / phân tích chuỗi

java.time.temporal
→ trường, đơn vị, truy vấn, bộ điều chỉnh và các API dùng chung

java.time.zone
→ quy tắc múi giờ và lần chuyển đổi

java.time.chrono
→ hệ lịch khác ISO; là ranh giới nâng cao, không phải lộ trình học chính của mô-đun này
```

### ISO-8601 và UTC — đọc ký hiệu trước khi đọc mã

**ISO-8601** là tiêu chuẩn quốc tế mô tả cách biểu diễn ngày và thời gian theo một quy ước thống nhất. Các kiểu chính như `LocalDate`, `LocalTime` và `LocalDateTime` dùng mô hình lịch ISO, và nhiều bộ định dạng mặc định của `java.time` sử dụng dạng văn bản ISO-8601.

Trong mô-đun, bạn sẽ thường thấy những chuỗi như:

```text
2026-10-05
→ ngày

09:30:15
→ giờ

2026-10-05T09:30:15
→ chữ T ngăn phần ngày và giờ

2026-10-05T02:30:15Z
→ Z nghĩa là độ lệch +00:00, tức UTC

2026-10-05T09:30:15+07:00
→ ngày-giờ cục bộ + độ lệch +07:00

2026-10-05T09:30:15+07:00[Asia/Ho_Chi_Minh]
→ các trường cục bộ + độ lệch đã xác định + ZoneId theo vùng
```

**UTC (Coordinated Universal Time)** là mốc tham chiếu toàn cầu mà độ lệch UTC được tính tương đối so với nó. UTC không phải “múi giờ địa phương của mọi người”; nó là tham chiếu giúp nhiều hệ thống nói về cùng dòng thời gian.

Ở đây **dòng thời gian** có thể hiểu đơn giản là một đường khái niệm sắp các thời điểm theo thứ tự trước → sau; một `Instant` xác định một vị trí trên đường đó.

`LocalDate`, `LocalDateTime` và các kiểu `Local*` tương ứng là **các kiểu lịch ISO**; chúng không phải đối tượng có thể đổi sang hệ lịch khác bằng cấu hình. Trong API nâng cao, thuật ngữ **chronology** chỉ hệ lịch/quy tắc lịch đang được dùng. Nếu ứng dụng thật sự cần hệ lịch khác ISO, `java.time.chrono` có các kiểu và API riêng như `ChronoLocalDate`. Đổi `Locale` để hiển thị văn bản cũng không biến một `LocalDate` thành hệ lịch khác.

### Các ngoại lệ thường gặp trong java.time

Người mới sẽ gặp một vài ngoại lệ lặp lại trong mô-đun:

```text
DateTimeException
→ ngoại lệ nền tảng khi chạy chương trình cho nhiều lỗi ngày-giờ

DateTimeParseException
→ phân tích văn bản thất bại
→ là một DateTimeException

UnsupportedTemporalTypeException
→ kiểu thời gian không hỗ trợ trường/đơn vị đang yêu cầu
→ cũng là một DateTimeException
```

Ví dụ `LocalDate.of(2026, 2, 30)` có thể ném `DateTimeException`; phân tích văn bản sai có thể ném `DateTimeParseException`; yêu cầu `HOURS` trên `LocalDate` có thể dẫn tới `UnsupportedTemporalTypeException`.

### KHÁI NIỆM — “thời gian” không phải chỉ có một nghĩa

Trong Java hiện đại, `java.time` không có một lớp duy nhất đại diện cho mọi khái niệm thời gian. Thay vào đó, mỗi kiểu giữ một **ý nghĩa nghiệp vụ cụ thể**.

Mô hình tư duy cơ bản:

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
→ một điểm chính xác trên dòng thời gian UTC
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

Điểm quan trọng là mỗi kiểu **cố ý không biết** một số thông tin.

Ví dụ:

```java
LocalDate birthday = LocalDate.of(1993, 7, 20);
```

Ngày sinh không cần phải biết “múi giờ UTC+7” hay “lúc 00:00”. Gắn các thông tin đó vào sẽ tạo ra dữ liệu giả mà nghiệp vụ không hề sở hữu.

Ngược lại:

```java
Instant receivedAt = Instant.now();
```

Một sự kiện kiểm toán (audit) cần biết yêu cầu xảy ra tại điểm nào trên dòng thời gian, nên `Instant` phù hợp hơn `LocalDateTime`.

### VÌ SAO — một “dấu thời gian” chung gây vấn đề gì?

Giả sử ta chỉ lưu:

```text
2026-11-01 01:30
```

Giá trị này chưa đủ để biết một `Instant` toàn cục. Nó không nói `01:30` ở Việt Nam, New York hay London. Ở vùng có DST, cùng một giờ địa phương thậm chí có thể xuất hiện hai lần trong ngày chuyển từ giờ mùa hè về giờ chuẩn.

Ngược lại, nếu ta lưu mọi thứ thành mili giây tính từ epoch, ta lại làm mất ý nghĩa tự nhiên của một số nghiệp vụ:

```text
Sinh nhật 20/07
Giờ mở cửa 08:30
Ngày chốt sổ cuối tháng
```

Những khái niệm này trước hết là **giá trị theo lịch hoặc đồng hồ cục bộ**, không phải “một số mili giây kể từ epoch”.

Vì thế `java.time` tách nghiệp vụ thành các kiểu rõ ràng thay vì khuyến khích một kiểu đa năng.

### Thời gian theo lịch của con người và dòng thời gian toàn cục

Có thể chia tư duy thành hai phía lớn:

```text
Lịch / đồng hồ cục bộ cho con người
├── LocalDate
├── LocalTime
└── LocalDateTime

Dòng thời gian toàn cục của hệ thống
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

Luồng tư duy:

```text
Người dùng nói: 09:00 ngày 05/10/2026
        ↓
LocalDateTime
        ↓ thêm nơi/quy tắc múi giờ
ZoneId
        ↓
ZonedDateTime
        ↓ quy về dòng thời gian chung
Instant
```

Đây là xương sống của toàn mô-đun.

## <a id="immutable-date-time">Tính bất biến của java.time</a>

Các lớp ngày-giờ chính như `LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `ZonedDateTime`, `Duration` và `Period` được thiết kế theo hướng **bất biến**: một đối tượng đã tạo không bị thay đổi nội dung bởi các thao tác như `plusDays` hay `minusHours`.

Ví dụ:

```java
LocalDate original = LocalDate.of(2026, 10, 5);
LocalDate nextDay = original.plusDays(1);

System.out.println(original); // 2026-10-05
System.out.println(nextDay);  // 2026-10-06
```

`plusDays(1)` không sửa `original`. Nó trả về một giá trị mới.

Điều này quan trọng vì các giá trị ngày-giờ thường được truyền qua nhiều tầng. Nếu một phương thức có thể âm thầm sửa đối tượng dùng chung, việc suy luận về thời gian sẽ rất khó:

```text
dịch vụ A nhận ngày
        ↓
dịch vụ B cộng thêm 1 ngày ngay trên cùng đối tượng
        ↓
dịch vụ A bỗng thấy dữ liệu thay đổi
```

Giá trị bất biến tránh kiểu tác dụng phụ đó và phù hợp với cách ta nhìn ngày/giờ như **giá trị** hơn là đối tượng có vòng đời và trạng thái có thể thay đổi.

### Kiểu dựa trên giá trị: so giá trị, đừng dựa vào định danh đối tượng

Nhiều lớp chính của `java.time` được thiết kế như **lớp dựa trên giá trị**. Ứng dụng nên coi hai đối tượng mang cùng giá trị là tương đương về mặt nghiệp vụ:

```java
LocalDate a = LocalDate.of(2026, 10, 5);
LocalDate b = LocalDate.of(2026, 10, 5);

boolean sameValue = a.equals(b); // true
```

Không viết logic dựa vào `a == b`, định danh đối tượng hoặc dùng đối tượng ngày-giờ làm monitor (khóa đồng bộ) cho `synchronized`. Hợp đồng API quan tâm **giá trị**, không hứa về định danh của từng đối tượng.

Tính bất biến cũng giúp các kiểu giá trị chính được chia sẻ an toàn giữa nhiều đoạn mã/luồng theo hợp đồng an toàn đa luồng của chúng, thay vì cần kiểm soát thay đổi phòng vệ như API cũ có thể thay đổi trạng thái.

### Các thao tác `with` / `plus` / `minus`

Hãy đọc các phương thức này theo quy tắc:

```text
withX(...)
→ tạo giá trị mới với một trường được thay thế/điều chỉnh

plusX(...)
→ tạo giá trị mới sau khi cộng

minusX(...)
→ tạo giá trị mới sau khi trừ
```

Ví dụ:

```java
LocalDateTime meeting = LocalDateTime.of(2026, 10, 5, 9, 0);

LocalDateTime moved = meeting
        .withHour(10)
        .plusDays(2);
```

`meeting` vẫn là `2026-10-05T09:00`; `moved` là giá trị khác.

Đừng quên gán kết quả:

```java
meeting.plusDays(1); // kết quả bị bỏ đi
```

Đây là lỗi rất phổ biến khi người học quen với API có thể thay đổi.

## <a id="choose-date-time-type">Chọn kiểu dữ liệu theo ý nghĩa nghiệp vụ</a>

Câu hỏi đúng không phải là “lớp nào có nhiều phương thức nhất?”, mà là **“dữ liệu này thật sự biết điều gì?”**.

| Bài toán | Kiểu khởi đầu phù hợp | Vì sao |
| --- | --- | --- |
| ngày sinh | `LocalDate` | chỉ là ngày lịch |
| giờ mở cửa hằng ngày | `LocalTime` | chỉ là giờ trong ngày |
| lịch học 09:00 ngày 05/10 trước khi chọn địa điểm | `LocalDateTime` | có ngày + giờ nhưng chưa có múi giờ |
| thời điểm yêu cầu đến máy chủ | `Instant` | cần điểm toàn cầu không mơ hồ |
| lịch họp 09:00 tại Paris | `ZonedDateTime` | cần cả các trường cục bộ và quy tắc múi giờ |
| dấu thời gian từ giao thức có `+07:00` nhưng không có vùng | `OffsetDateTime` | dữ liệu biết độ lệch cụ thể nhưng không biết ZoneId |
| kỳ thanh toán `2026-10` | `YearMonth` | biết năm + tháng nhưng cố ý không tự tạo ngày |
| sinh nhật lặp lại `--07-20` | `MonthDay` | biết tháng + ngày nhưng không gắn năm cụ thể |
| chỉ một năm như `2026` | `Year` | nghiệp vụ chỉ sở hữu năm |
| `09:30+07:00` từ giao thức | `OffsetTime` | giờ trong ngày + độ lệch nhưng không có ngày/vùng |

### Quy tắc chọn nhanh

```text
Chỉ ngày?                      → LocalDate
Chỉ giờ trong ngày?            → LocalTime
Ngày + giờ, chưa có múi giờ?       → LocalDateTime
Cần mốc tuyệt đối trên dòng thời gian?→ Instant
Cần ngày/giờ + quy tắc vùng?    → ZonedDateTime
Cần ngày/giờ + độ lệch cố định?    → OffsetDateTime
Cần năm-tháng nhưng không có ngày? → YearMonth
Cần tháng-ngày nhưng không có năm? → MonthDay
```

Các kiểu như `Year`, `YearMonth`, `MonthDay` và `OffsetTime` không có chương riêng trong lộ trình học này vì cơ chế của chúng được suy ra từ các khái niệm chính. Nhưng chúng rất quan trọng khi mô hình hóa: **nếu nghiệp vụ chỉ biết năm-tháng, đừng tự tạo ngày `01`; nếu nghiệp vụ chỉ biết tháng-ngày, đừng tự tạo một năm giả**.

### “Local” không có nghĩa là “múi giờ máy hiện tại”

Tên `LocalDateTime` dễ gây hiểu nhầm. `Local` ở đây nghĩa là **các trường ngày/giờ địa phương chưa gắn múi giờ/độ lệch**, không phải tự động lấy múi giờ mặc định hệ thống.

```java
LocalDateTime local = LocalDateTime.of(2026, 10, 5, 9, 0);
```

Đối tượng này không chứa:

```text
Asia/Ho_Chi_Minh
+07:00
UTC
```

Muốn biến nó thành một mốc trên dòng thời gian, ứng dụng phải cung cấp múi giờ/độ lệch một cách có chủ đích.

### Lộ trình học của mô-đun

Từ đây, các chương đi theo đúng câu hỏi nghiệp vụ:

```text
Ngày?                → LocalDate
Giờ?                 → LocalTime
Ngày + giờ cục bộ?    → LocalDateTime
Mốc trên dòng thời gian?  → Instant
Độ lệch UTC hay múi giờ?  → ZoneOffset / ZoneId
Gắn múi giờ vào giá trị?   → ZonedDateTime / OffsetDateTime
Khoảng thời gian?     → Duration / Period
Văn bản ↔ ngày-giờ?       → Định dạng / Phân tích chuỗi
Cộng/trừ/so sánh + DST?   → Phép toán / So sánh
"Bây giờ" kiểm thử được? → Clock
API cũ + múi giờ mặc định + lưu trữ? → Tương tác API cũ / Tổng hợp
```

Nếu chỉ nhớ một điều trước khi sang chương tiếp theo, hãy nhớ: **chọn kiểu ngày-giờ theo ý nghĩa dữ liệu, không theo thói quen lưu mọi thứ thành dấu thời gian**.
