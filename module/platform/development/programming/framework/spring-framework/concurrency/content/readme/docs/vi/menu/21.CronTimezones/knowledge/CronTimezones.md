<a id="back-to-top"></a>

# Cron và múi giờ

## Menu
- [Sáu trường của biểu thức cron trong Spring](#cron-timezones-001)
- [Dấu *, ?, phạm vi, danh sách và bước nhảy](#cron-timezones-002)
- [Các ký hiệu L, W và # khi cần lịch theo ngày](#cron-timezones-003)
- [CronExpression để phân tích và tìm lần chạy kế tiếp](#cron-timezones-004)
- [Macro cron và cách đọc lịch rõ nghĩa](#cron-timezones-005)
- [Chỉ định múi giờ thay cho phụ thuộc môi trường máy chủ](#cron-timezones-006)
- [Giờ bị bỏ qua hoặc lặp lại khi chuyển giờ mùa hè](#cron-timezones-007)
- [Không suy rộng cú pháp cron của hệ điều hành hoặc Quartz sang Spring](#cron-timezones-008)
- [Kiểm chứng lịch quanh ranh giới tháng, năm và thay đổi múi giờ](#cron-timezones-009)

## <a id="cron-timezones-001">Sáu trường của biểu thức cron trong Spring</a>

<details>
<summary>Xem chi tiết</summary>

# Sáu trường của biểu thức cron trong Spring

## 1. Cron của Spring có sáu trường, không phải năm hoặc bảy

Trong Spring Framework, biểu thức **cron** mô tả các thời điểm theo lịch mà một công việc nên được kích hoạt. Bạn sẽ gặp cron trong `@Scheduled(cron = "...")`, `CronTrigger`, `TaskScheduler.schedule(Runnable, Trigger)` và `CronExpression`.

Điểm đầu tiên phải ghi nhớ: **cron chuẩn của Spring Framework có đúng sáu trường**, phân tách bằng khoảng trắng:

```text
┌─────────────── giây         (second,       0–59)
│ ┌───────────── phút         (minute,       0–59)
│ │ ┌─────────── giờ          (hour,         0–23)
│ │ │ ┌───────── ngày/tháng   (day-of-month, 1–31)
│ │ │ │ ┌─────── tháng        (month,        1–12 hoặc JAN–DEC)
│ │ │ │ │ ┌───── thứ/tuần     (day-of-week,  0–7 hoặc SUN–SAT)
│ │ │ │ │ │
* * * * * *
1 2 3 4 5 6   ← vị trí trường, không phải các giá trị cần dùng
```

Đối với **day-of-week trong Spring**, **0 hoặc 7 là Chủ nhật**, 1 là thứ Hai, 2 là thứ Ba, ..., 6 là thứ Bảy. Đây là quy ước cron thông thường và **khác quy ước số trong Quartz**. Vì thế, khi học các bài cron của Spring, không thể lấy nguyên một chuỗi của hệ scheduler khác rồi mặc định nó có ý nghĩa giống nhau.

Ví dụ đơn giản:

```text
0 30 9 * * MON-FRI
│ │  │ │ │ └── thứ Hai đến thứ Sáu
│ │  │ │ └──── mọi tháng
│ │  │ └────── mọi ngày trong tháng
│ │  └──────── 09 giờ
│ └─────────── phút 30
└───────────── giây 00

=> 09:30:00 mỗi ngày thứ Hai đến thứ Sáu,
   tính theo múi giờ được dùng để diễn giải lịch.
```

**Ba mục tiêu của bài:** đọc được một biểu thức theo đúng thứ tự sáu trường, tự viết/kiểm chứng lịch bằng API của Spring, và tránh những sai lệch về múi giờ, thứ trong tuần và môi trường chạy.

Đây là cú pháp Spring Framework 6.x (Java 17+). Một sản phẩm khác có tên “cron”, ví dụ crontab của Unix, Quartz hoặc một cloud scheduler, có thể có số trường/quy tắc khác. **Hãy xác định parser/engine đang thực sự sử dụng trước khi copy biểu thức.**

## 2. Ý nghĩa và phạm vi hợp lệ của từng trường

| Vị trí | Trường | Khoảng số phổ biến | Ví dụ | Ý nghĩa |
| --- | --- | --- | --- | --- |
| 1 | Giây (*second*) | `0–59` | `0`, `*/10` | Thời điểm trong một phút |
| 2 | Phút (*minute*) | `0–59` | `30`, `0/15` | Thời điểm trong một giờ |
| 3 | Giờ (*hour*) | `0–23` | `9`, `8-17` | Giờ theo hệ 24 tiếng |
| 4 | Ngày trong tháng (*day-of-month*) | `1–31` | `1`, `L`, `1W` | Ngày theo tháng của lịch |
| 5 | Tháng (*month*) | `1–12` hoặc `JAN–DEC` | `1`, `JAN`, `3-6` | Tháng trong năm |
| 6 | Thứ trong tuần (*day-of-week*) | `0–7` hoặc `SUN–SAT` | `MON-FRI`, `5L`, `MON#1` | Ngày của tuần |

Không phải lúc nào điền được một số hợp lệ vào từng trường cũng tạo ra lịch thực sự có lần chạy. Ví dụ yêu cầu ngày 31 của tháng 2 là lịch không tồn tại. Vì vậy cần **hai mức kiểm tra**: chuỗi có parse hợp lệ không và có các thời điểm tương lai phù hợp không.

### Trường giây

```text
0 * * * * *       Đầu mỗi phút, tại giây 00.
*/15 * * * * *    Mỗi 15 giây: 00, 15, 30, 45.
10,20,40 * * * * * Các giây 10, 20 và 40 của mỗi phút.
```

Khi biểu thức bắt đầu bằng `*`, đó là **mọi giây**, không phải “một lần mỗi phút”. Do vậy `* * * * * *` có thể kích hoạt mỗi giây: cần cân nhắc tải thực thi.

### Trường phút

```text
0 0 * * * *       Đầu mỗi giờ (giây 0, phút 0).
0 */15 * * * *   Phút 0, 15, 30, 45 của mọi giờ.
0 10,40 * * * *  Phút 10 và 40 của mọi giờ.
```

Hai mẫu `*/15` và `0/15` khi áp dụng cho phút bắt đầu từ 0 đều có thể biểu diễn các mốc 0,15,30,45. Chúng là **các mốc trên đồng hồ**, không phải cứ đợi 15 phút sau khi task trước kết thúc.

### Trường giờ

```text
0 0 9 * * *         09:00 mỗi ngày.
0 0 9,12,18 * * *   09:00, 12:00 và 18:00.
0 0 8-17 * * MON-FRI Đầu mỗi giờ từ 08:00 tới 17:00 ngày làm việc.
```

Trường giờ dùng **24 giờ**, vì vậy `18` là 18:00; không dùng `24` để biểu diễn nửa đêm, phải dùng `0`.

### Ngày trong tháng, tháng và thứ

```text
0 0 0 1 * *        Nửa đêm ngày 1 của mọi tháng.
0 0 0 L * *        Nửa đêm ngày cuối cùng của mọi tháng.
0 0 9 * JAN MON-FRI 09:00 vào các thứ Hai–Sáu trong tháng 1.
0 0 9 * * 1        09:00 thứ Hai (1 = MON trong Spring).
```

Các ví dụ dùng `*` ở trường day-of-month khi đang giới hạn weekday, và ngược lại. Điều này giúp người đọc biết **trường nào đang thực sự quyết định ngày**; không cố đoán kết quả khi đồng thời đặt nhiều ràng buộc phức tạp vào hai trường ngày.

## 3. Các ký hiệu cơ bản: *, ?, -, , và /

Cron có những ký hiệu mang nghĩa riêng. Viết được đúng sáu trường là điều kiện cần, nhưng để đọc đúng lịch bạn còn phải hiểu các ký hiệu bên trong trường.

| Ký hiệu | Ý nghĩa | Ví dụ đúng trong Spring |
| --- | --- | --- |
| `*` | Mọi giá trị hợp lệ trong trường | `0 0 * * * *`: đầu mỗi giờ |
| `?` | Thay cho `*` **trong trường ngày của tháng hoặc thứ trong tuần** | `0 0 9 ? * MON` |
| `-` | Khoảng giá trị **bao gồm hai đầu** | `MON-FRI`, `9-17` |
| `,` | Danh sách các giá trị chọn lọc | `9,12,18` |
| `/n` | Bước tăng với độ dài n trong trường/khoảng | `*/10`, `0/15` |
| `L` | Lần cuối/ngày cuối tùy trường | `L` ở ngày/tháng, `5L` ở thứ |
| `W` | Ngày trong tuần gần ngày của tháng được chọn | `1W`, `LW` |
| `#` | Thứ x của một tuần xuất hiện lần n trong tháng | `MON#1`, `5#2` |

### Dấu * không phải “không quan tâm” hoàn toàn

`*` có nghĩa “tất cả giá trị hợp lệ của trường”. Khi viết `0 * * * * *`, trường phút là mọi phút, trong khi giây cố định là 0. Nếu viết `* * * * * *`, **cả trường giây cũng mở**, nên cron có thể phát mỗi giây. Độ chi tiết ở trường đầu tiên quyết định rất nhiều tần suất.

### Dấu ? trong Spring khác cách diễn giải của nhiều tài liệu Quartz

Trong `CronExpression` của Spring, `?` có thể dùng **thay cho `*`** ở day-of-month hoặc day-of-week; các trường khác không được dùng `?`. Ví dụ:

```text
0 0 9 ? * MON-FRI   09:00 thứ Hai–Sáu.
0 0 9 * * MON-FRI   Cùng ý nghĩa thực tế trong Spring.
0 0 9 15 * ?        09:00 ngày 15 mỗi tháng.
```

Không cần ép buộc “phải có một dấu ?” như một số quy tắc của Quartz. Tuy nhiên, khi cron có **điều kiện ngày phức tạp**, dùng `?` cho trường không cần giới hạn vẫn giúp ý định dễ đọc hơn.

### Khoảng và danh sách

```text
0 0 8-10 * * *      08:00, 09:00, 10:00.
0 0 6,19 * * *      06:00 và 19:00.
0 0 9 * * MON-FRI   09:00 ngày làm việc (theo tuần, không xét ngày lễ).
```

`MON-FRI` nghĩa thứ Hai đến thứ Sáu, **không tự loại trừ ngày nghỉ lễ quốc gia**. Nếu nghiệp vụ cần lịch làm việc có ngày nghỉ đặc biệt, phải kiểm tra business calendar bên ngoài cú pháp cron.

### Bước tăng không phải phép delay sau khi task hoàn tất

```text
0 */20 * * * *     Mốc phút 0, 20, 40 trong mỗi giờ.
*/10 * * * * *    Giây 0, 10, 20, 30, 40, 50.
0 0/30 8-10 * * * 08:00, 08:30, 09:00, 09:30, 10:00, 10:30.
```

`*/20` trên trường phút luôn chia theo **giá trị phút trong giờ**; không đảm bảo 20 phút sau khi công việc trước chạy xong. Với “chạy xong rồi nghỉ 20 phút”, API `fixedDelay` phù hợp hơn.

## 4. Các ký hiệu nâng cao: L, W, # và trường thứ trong tuần

Spring `CronExpression` hỗ trợ một số biểu thức mở rộng kiểu Quartz trong **hai trường ngày**, nhưng vẫn theo quy ước **weekday number của Spring**, không dùng toàn bộ parser Quartz.

### L: ngày cuối tháng hoặc lần xuất hiện cuối của một thứ

| Cron | Ý nghĩa |
| --- | --- |
| `0 0 0 L * *` | 00:00 vào **ngày cuối cùng** của mỗi tháng |
| `0 0 0 L-3 * *` | 00:00 vào ngày đứng trước ngày cuối tháng 3 ngày |
| `0 0 0 LW * *` | 00:00 vào **ngày làm việc cuối cùng** của tháng (thứ Hai–Sáu) |
| `0 0 0 * * 5L` | 00:00 vào **thứ Sáu cuối cùng** của tháng |
| `0 0 0 * * THUL` | 00:00 vào **thứ Năm cuối cùng** của tháng |

Ở trường ngày trong tháng, `L` là ngày cuối cùng, nên tự thích nghi với tháng 28/29/30/31 ngày. Trong trường day-of-week, ghép `dL` hoặc `DDDL` là lần cuối của thứ đó trong tháng. Tránh viết một `L` vô ngữ cảnh rồi suy ra nó có cùng nghĩa ở hai trường.

### W: ngày làm việc gần ngày n

```text
0 0 9 1W * *   09:00 ngày làm việc đầu tiên gần ngày 1.
0 0 9 15W * *  09:00 ngày trong tuần gần ngày 15.
0 0 9 LW * *   09:00 ngày làm việc cuối cùng trong tháng.
```

`W` dựa trên **thứ Hai đến thứ Sáu**, không biết lịch nghỉ lễ. Quy tắc “gần nhất” cũng có xử lý mép tháng: nếu ngày 1 rơi vào thứ Bảy, `1W` chọn thứ Hai tiếp theo thay vì đẩy về tháng trước. Hãy kiểm chứng vài tháng cụ thể bằng `CronExpression.next(...)` trước khi dùng cho tính lương/hóa đơn.

### #: lần xuất hiện thứ n trong tháng

```text
0 0 9 ? * MON#1   09:00 thứ Hai đầu tiên của tháng.
0 0 9 ? * 5#2     09:00 thứ Sáu thứ hai của tháng.
```

Trong `MON#1`, con số sau `#` là thứ tự lần xuất hiện; trong `5#2`, `5` chỉ thứ Sáu theo quy ước Spring và `2` nghĩa lần thứ hai. Đây là chỗ nhiều người mang nhầm quy ước từ Quartz sang.

Nếu phải chạy vào “ngày làm việc thứ năm của tháng” **sau khi trừ ngày nghỉ lễ**, `#` cũng không làm được bài toán ấy. Cần calendar nghiệp vụ và bộ điều phối có kiểm tra dữ liệu lịch thực tế.

## 5. Tám biểu thức cron Spring thường dùng và cách đọc

| Biểu thức | Lịch dự kiến |
| --- | --- |
| `0 * * * * *` | Ở giây 0 của **mỗi phút** |
| `*/10 * * * * *` | **Mỗi 10 giây** ở các mốc giây phù hợp |
| `0 0 * * * *` | Đầu **mỗi giờ** |
| `0 30 9 * * MON-FRI` | 09:30 thứ Hai–Sáu |
| `0 0 18 * * *` | 18:00 hằng ngày |
| `0 0 0 1 * *` | Nửa đêm ngày 1 của mỗi tháng |
| `0 0 0 L * *` | Nửa đêm ngày cuối mỗi tháng |
| `0 0 9 ? * MON#1` | 09:00 thứ Hai đầu tiên trong tháng |

### Bài tập đọc mẫu bằng tay

**Biểu thức 1:** `0 15 9-17 * * MON-FRI`

Đọc từ trái sang phải:

- Giây 0.
- Phút 15.
- Giờ 9 đến 17, bao gồm 9 và 17.
- Mọi ngày tháng, mọi tháng.
- Chỉ thứ Hai đến thứ Sáu.

**Kết quả:** 09:15, 10:15, ..., 17:15 từ thứ Hai đến thứ Sáu theo múi giờ của lịch.

**Biểu thức 2:** `0 0/30 8-10 * * *`

- Giây 0.
- Phút 0 và 30.
- Giờ 8, 9, 10.
- Mọi ngày.

**Kết quả:** 08:00, 08:30, 09:00, 09:30, 10:00, 10:30 mỗi ngày.

**Biểu thức 3:** `0 0 9 1 * *`

- Giây 0, phút 0, giờ 9.
- Ngày 1 của mỗi tháng.
- Tất cả các tháng.

**Kết quả:** 09:00 ngày đầu tiên mỗi tháng.

### Các lỗi khi đọc sáu trường

- Đọc `0 30 9 * * *` là “mỗi ngày vào 09:00” mà bỏ qua phút 30: lịch thực tế là **09:30**.
- Đọc `*/10 * * * * *` là “mỗi 10 phút”: thực tế ở **trường giây**.
- Đọc `0 */10 * * * *` là “mỗi 10 giây”: thực tế ở **trường phút**, giây cố định 0.
- Tưởng `* * * * * *` là một lần mỗi phút: biểu thức mở cả trường giây.
- Tưởng `0 0 9 * * MON-FRI` tự bỏ ngày nghỉ lễ: cron chỉ đọc thứ trong tuần.
- Quên rằng mốc 09:00 cần được diễn giải trong **một time zone cụ thể**.

## 6. Spring cron khác crontab Unix và Quartz thế nào?

Tên “cron” xuất hiện ở nhiều sản phẩm nhưng cú pháp không hoàn toàn giống nhau.

| Đặc điểm | Unix crontab thông thường | Spring Framework CronExpression | Quartz cron |
| --- | --- | --- | --- |
| Số trường cơ bản | 5 | **6** | 6 hoặc 7 tùy biểu thức |
| Trường giây ở đầu | Không | **Có** | Có |
| Trường năm | Không | **Không** | Có thể có trường thứ bảy |
| Quy ước số Chủ nhật | Thường 0 hoặc 7 | **0 hoặc 7** | Thường **1** |
| `L`, `W`, `#` | Không đồng nhất giữa các engine | Có hỗ trợ trong trường ngày | Có, nhưng cần đọc parser Quartz |
| Macro như `@daily` | Phụ thuộc cron implementation | Có trong `CronExpression` | Không mặc định tương đương mọi engine |

### Lỗi thường gặp: lấy nguyên mẫu năm trường của Linux

Trên Unix crontab, biểu thức:

```text
30 9 * * MON-FRI
```

thường nghĩa là 09:30 thứ Hai–Sáu. Nhưng Spring cần **trường giây phía trước**:

```text
0 30 9 * * MON-FRI
```

Truyền trực tiếp năm trường vào `CronExpression.parse(...)` của Spring sẽ không hợp lệ. Không xử lý bằng cách tự thêm `*` ở đầu: nếu thêm `*`, công việc có thể được kích hoạt **mỗi giây trong phút 30** của giờ 09. Giá trị đúng để chạy một lần ở phút đó là **`0` ở trường giây**.

### Lỗi khác: lấy biểu thức Quartz có năm/chủ nhật khác hoặc thêm năm

Quartz có thể có trường **year** thứ bảy. Với `CronExpression` của Spring, một chuỗi bảy trường không phải format hợp lệ của API này. Khi bạn port cron từ một hệ khác, đừng chỉ cắt bỏ year và coi mọi phần còn lại luôn tương đương: kiểm tra lại day-of-week và ký hiệu nâng cao.

Ví dụ theo quy ước Spring:

```text
0 0 9 * * 0   09:00 Chủ nhật.
0 0 9 * * 1   09:00 thứ Hai.
0 0 9 * * 7   09:00 Chủ nhật.
```

Quartz sử dụng quy ước ngày trong tuần khác: `1` thường là Chủ nhật. Nếu viết Spring cron bằng tên `SUN`/`MON` thay số, bạn giảm nguy cơ hiểu sai.

### Dấu ? cũng cần đọc theo parser đang dùng

Trong Spring, `?` có thể thay `*` ở day-of-month hoặc day-of-week. Không dùng giả thiết parser Quartz làm cơ sở kiểm tra mọi chuỗi Spring; chỉ dùng Javadoc `CronExpression` và chạy test với dependency đúng version.

## 7. CronExpression: parse, kiểm tra hợp lệ, tìm lần chạy kế tiếp

Spring cung cấp `org.springframework.scheduling.support.CronExpression` từ Spring Framework 5.3. Đây là đối tượng **tính thời điểm khớp biểu thức**, không phải một executor tự chạy task. Hai API quan trọng:

```java
CronExpression cron = CronExpression.parse("0 30 9 * * MON-FRI");
boolean valid = CronExpression.isValidExpression(
        "0 30 9 * * MON-FRI");

ZonedDateTime next = cron.next(seedZonedDateTime);
```

Đây là **snippet** trong code đã import `CronExpression`, `ZonedDateTime` và có biến `seedZonedDateTime`. Nó chưa tự nộp lịch hay mở thread.

- `parse(String)` tạo đối tượng cron hoặc ném `IllegalArgumentException` nếu không hợp lệ.
- `isValidExpression(String)` trả boolean để kiểm tra cú pháp mà không phải tự viết regex sáu trường.
- `next(Temporal)` trả **lần khớp tiếp theo sau thời điểm seed**, hoặc có thể `null` nếu không tìm được.
- Để cron có thể tính theo múi giờ và quy tắc lịch thực tế, nên đưa **`ZonedDateTime`** chứ không dùng `LocalDateTime` trần khi có yêu cầu múi giờ.
- Nếu cần **vừa tính lần kế tiếp vừa chạy thật**, dùng `TaskScheduler` với `CronTrigger` hoặc `@Scheduled`.

Một regex tự viết như “chia chuỗi thành sáu phần rồi so range” không đủ để nhận diện `L`, `W`, `#`, macro, ngày 29/2 và các mốc DST. Hãy dùng parser chính thức để kiểm tra.

## 8. Ví dụ Maven + Java 17: phân tích và tính thời điểm cron

Ví dụ này chỉ dùng **Spring Framework 6.2.14** và Java 17, không cần Spring Boot, không cần DB hay web server. Nó cho thấy sáu trường, thứ trong tuần, chuỗi 5/7 trường không hợp lệ, cách tính mốc sau giờ 09:30 và sự chuyển đổi UTC của giờ Việt Nam.

Cấu trúc:

```text
spring-cron-six-fields/
├── pom.xml
└── src/main/java/demo/CronSixFieldsDemo.java
```

### 8.1. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>spring-cron-six-fields</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.14</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.CronSixFieldsDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 8.2. Tệp src/main/java/demo/CronSixFieldsDemo.java

```java
package demo;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.scheduling.support.CronExpression;

public final class CronSixFieldsDemo {
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

        // Cứ 15 giây, tại giây 0, 15, 30, 45 của phút.
        CronExpression every15Seconds =
                CronExpression.parse("*/15 * * * * *");
        ZonedDateTime seed =
                ZonedDateTime.of(2026, 11, 2, 10, 0, 7, 0, zone);
        ZonedDateTime next15 = every15Seconds.next(seed);
        System.out.println(
                "next15Seconds=" + next15.toLocalDateTime());

        // 09:30 mỗi thứ Hai–Sáu. Seed là thứ Sáu đã đúng 09:30,
        // nên lần kế tiếp phải là thứ Hai tuần sau.
        CronExpression weekdays =
                CronExpression.parse("0 30 9 * * MON-FRI");
        ZonedDateTime friday =
                ZonedDateTime.of(2026, 11, 6, 9, 30, 0, 0, zone);
        ZonedDateTime nextWorkday = weekdays.next(friday);
        System.out.println(
                "nextWorkday=" + nextWorkday.toLocalDateTime());
        System.out.println("nextWorkdayUtc=" + nextWorkday.toInstant());

        // L: ngày cuối cùng của tháng 11/2026 (ngày 30).
        CronExpression monthEnd =
                CronExpression.parse("0 0 0 L * *");
        ZonedDateTime novemberStart =
                ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone);
        ZonedDateTime nextMonthEnd = monthEnd.next(novemberStart);
        System.out.println(
                "nextMonthEnd=" + nextMonthEnd.toLocalDateTime());

        // Đúng sáu trường -> hợp lệ.
        System.out.println("validSix="
                + CronExpression.isValidExpression(
                        "0 30 9 * * MON-FRI"));

        // Unix 5 trường; Quartz 7 trường -> không hợp lệ ở đây.
        System.out.println("validFive="
                + CronExpression.isValidExpression(
                        "30 9 * * MON-FRI"));
        System.out.println("validSeven="
                + CronExpression.isValidExpression(
                        "0 30 9 * * MON-FRI 2026"));

        // Phút 60 ngoài khoảng hợp lệ.
        System.out.println("validMinute60="
                + CronExpression.isValidExpression(
                        "0 60 9 * * *"));
    }
}
```

### 8.3. Biên dịch và chạy

Trong thư mục có `pom.xml`:

```bash
mvn -q compile exec:java
```

**Standard output kỳ vọng từ chương trình Java** (không tính thông tin Maven khi tải dependency hoặc plugin):

```text
next15Seconds=2026-11-02T10:00:15
nextWorkday=2026-11-09T09:30
nextWorkdayUtc=2026-11-09T02:30:00Z
nextMonthEnd=2026-11-30T00:00
validSix=true
validFive=false
validSeven=false
validMinute60=false
```

### 8.4. Giải thích vì sao output xác định

- `seed` là **thứ Hai ngày 2/11/2026 lúc 10:00:07** theo múi giờ Việt Nam. Lần có giây chia hết cho 15 kế tiếp là 10:00:15.
- `friday` là **thứ Sáu ngày 6/11/2026 lúc đúng 09:30**. `CronExpression.next(...)` tìm **lần kế tiếp sau seed**, không lấy lại chính seed, nên tới 09:30 **thứ Hai 9/11/2026**.
- Múi giờ `Asia/Ho_Chi_Minh` là UTC+07:00 vào tháng 11/2026; 09:30 địa phương tương ứng 02:30 UTC.
- Ngày cuối của tháng 11 là ngày 30, nên `L` cho ra 2026-11-30 lúc nửa đêm.
- `isValidExpression(...)` kiểm tra cú pháp; biến thể 5 trường thiếu giây, 7 trường thừa năm và phút 60 đều không hợp lệ với parser Spring này.

Đoạn code **không dùng đồng hồ hiện tại**, `Thread.sleep(...)` hay scheduler thread. Vì các thời điểm seed đã cố định, output có thể kiểm tra được theo ngày lịch. Đây là ví dụ **chỉ tính toán và validate biểu thức**, chưa phải một ứng dụng `@Scheduled` tự kích hoạt theo giờ thực.

**Lưu ý về version:** ví dụ khóa Spring 6.2.14 để người đọc có cùng API. Khi nâng cấp Framework, chạy lại kiểm thử parser và lịch cụ thể thay vì ngầm giả định mọi trường hợp DST và các ký hiệu đặc biệt luôn bất biến.

## 9. @Scheduled(cron): kết hợp sáu trường với thời gian chạy thật

Trong ứng dụng Spring, cách dùng khai báo phổ biến:

```java
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
```

`@EnableScheduling` bật cơ chế phát hiện các method gắn `@Scheduled` trong Spring-managed beans.

Ví dụ một job chạy lúc 09:30 thứ Hai–Sáu tại Việt Nam:

```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyReportJob {

    @Scheduled(
            cron = "0 30 9 * * MON-FRI",
            zone = "Asia/Ho_Chi_Minh")
    public void publishReport() {
        // Thực hiện nghiệp vụ, có timeout và error handling phù hợp.
        System.out.println("Report tick");
    }
}
```

Đây là các **class tích hợp Spring** và không có stdout theo thời gian cố định trong một lần chạy CLI: job thực hiện khi tới mốc cron thực tế. Trong Spring Boot, cần đúng component scan/import và infrastructure scheduling đã bật; nếu chưa bật scheduling, method annotation có thể không được đăng ký.

### Ý nghĩa của hai thuộc tính quan trọng

- `cron`: chuỗi sáu trường, được Spring parse và dùng để tính mốc kích hoạt.
- `zone`: múi giờ để diễn giải **giờ/phút/ngày** của biểu thức. Có thể để trống để sử dụng múi giờ của scheduler, nhưng khi nghiệp vụ gắn với vùng thời gian cụ thể, nên khai báo rõ.

Trong ví dụ:

```text
0 30 9 * * MON-FRI  +  zone=Asia/Ho_Chi_Minh
        |
        v
09:30:00 thứ Hai–Sáu tại Việt Nam
        |
        v
02:30:00 UTC vào các ngày có offset +07:00
```

Không lấy timezone mặc định của container trong một cluster làm chuẩn cho khách hàng toàn cầu. Nếu người dùng ở nhiều quốc gia cần lịch theo giờ của từng người, không thể chỉ có **một `@Scheduled` annotation tĩnh** để đáp ứng từng zone/từng thời điểm riêng. Cần dữ liệu schedule per-user và một cơ chế điều phối phù hợp.

### Chọn scheduler bằng tên

Từ Spring Framework 6.1, `@Scheduled` có thuộc tính `scheduler` để chỉ định bean `TaskScheduler` hoặc `ScheduledExecutorService` theo tên/qualifier:

```java
@Scheduled(
        cron = "0 30 9 * * MON-FRI",
        zone = "Asia/Ho_Chi_Minh",
        scheduler = "businessCronScheduler")
public void publishReport() {
    reportService.publish();
}
```

Đây là snippet trong Spring bean có `reportService` đã được inject và scheduler bean tương ứng. Muốn chạy đúng, phải tồn tại bean `businessCronScheduler`. Nếu không có qualifier và nhiều scheduler, quy trình chọn executor tùy configuration, do đó hãy kiểm tra bean thực tế khi debug.

### @Scheduled không phải lời hứa exactly-once toàn cluster

Nếu có ba pod Spring Boot và mỗi pod đăng ký bean chứa annotation, cả ba có thể cùng kích hoạt job ở 09:30. Cron chỉ quyết định **mốc thời gian trên mỗi instance**, không cung cấp leader election hoặc khóa phân tán.

Với job tài chính, gửi mail hoặc xử lý batch quan trọng, cần job ID, idempotency hoặc một hệ thống coordination phù hợp. Không kết luận “cron hợp lệ” đồng nghĩa “chỉ một lần xử lý nghiệp vụ”.

## 10. CronTrigger và TaskScheduler: đăng ký lịch bằng Java code

Khi không muốn gắn một annotation tĩnh, có thể dùng `CronTrigger`:

```java
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

CronTrigger trigger = new CronTrigger(
        "0 30 9 * * MON-FRI",
        ZoneId.of("Asia/Ho_Chi_Minh"));

ScheduledFuture<?> handle = taskScheduler.schedule(
        () -> runDailyReport(),
        trigger);
```

Đây là **snippet tích hợp** trong method có `taskScheduler` và `runDailyReport()` đã tồn tại. `CronTrigger` áp dụng cùng định dạng sáu trường, tính lần tiếp theo dựa trên `TriggerContext`, còn `TaskScheduler` chịu trách nhiệm thực thi.

Nếu job có lịch động theo dữ liệu DB/user, `CronTrigger` thuận tiện để đăng ký riêng từng rule sau khi validate. Nhưng cần quản lý handle, cập nhật/hủy lịch khi cấu hình thay đổi và xử lý restart nếu lịch cần bền vững. Không tự tạo một thread hoặc scheduler cho từng user nếu không có giới hạn tài nguyên.

### Lenient và fixed execution khi task chạy lâu

Trong Spring Framework 6.1.3+, `CronTrigger` có các factory rõ ràng:

- `CronTrigger.forLenientExecution(expression)`: tính lịch tiếp theo dựa vào **thời điểm hoàn tất** lần trước; một số lần bắn theo biểu thức có thể bị bỏ qua khi task chậm.
- `CronTrigger.forFixedExecution(expression)`: tính lần tiếp theo từ **mốc scheduled trước đó**, nhằm không bỏ các mốc gốc; các lần catch-up có thể chạy muộn.

Với constructor `new CronTrigger(expression, zone)`, hành vi tương ứng dạng lenient. Sự khác nhau này **không thay đổi sáu trường cron**, nhưng thay đổi cách scheduler xử lý missed ticks khi công việc kéo dài.

```java
CronTrigger lenient = CronTrigger.forLenientExecution(
        "0 */5 * * * *");

CronTrigger fixed = CronTrigger.forFixedExecution(
        "0 */5 * * * *");
```

Code trên minh họa hai chính sách; các factory không nhất thiết mang cùng đối số zone như constructor, vì thế nếu cần zone tùy chỉnh phải tham chiếu API và cách cấu hình scheduler clock tương ứng. Hãy đánh giá backlog và tác dụng phụ trước khi chọn fixed catch-up.

**Không nhầm cron với fixed-rate tuyệt đối:** dù cron có thời điểm danh nghĩa, một callback vẫn có thể chạy trễ do scheduler worker bận, GC hoặc dependency chậm. Việc có tạo thêm những lần catch-up hay bỏ lịch là vấn đề **trigger semantics và implementation**, không chỉ các dấu trong expression.

## 11. Múi giờ: cùng sáu trường, khác thời điểm thực

Biểu thức:

```text
0 0 9 * * *
```

có nghĩa **09:00 giờ địa phương của zone đang dùng**, không phải mặc định 09:00 UTC.

Nếu dùng `Asia/Ho_Chi_Minh`, thời điểm tương ứng thường là 02:00 UTC. Nếu dùng `Europe/Berlin`, giờ UTC tương ứng thay đổi theo mùa do DST. Một cron expression giống hệt nhau có thể tạo ra các `Instant` khác nhau tùy zone.

### ZoneId và ZonedDateTime

Trong code dùng `CronExpression.next(...)`, luôn xác định ngữ cảnh:

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime seed = ZonedDateTime.now(zone);
ZonedDateTime next = CronExpression
        .parse("0 0 9 * * *")
        .next(seed);
```

Đây là snippet cần imports `ZoneId`, `ZonedDateTime` và `CronExpression`. Khi muốn output ổn định trong test, thay `ZonedDateTime.now(zone)` bằng một mốc cố định hoặc inject `Clock.fixed(...)`. Không viết integration test mà chỉ “sleep tới 09:00” và mong CI luôn cùng múi giờ.

### DST gap và overlap

Ở múi giờ có chuyển đổi giờ mùa hè (*daylight saving time*):

- **Gap:** một giờ địa phương có thể không tồn tại vào ngày chuyển đồng hồ. Cron nhắm vào giờ đó có thể không có một lần khớp thông thường như ngày khác.
- **Overlap:** một giờ địa phương có thể xuất hiện hai lần với hai offset khác nhau. Nếu nghiệp vụ yêu cầu đúng một lần cho **mỗi ngày theo lịch**, phải làm rõ cách xử lý hai lần 01:30/02:30 trên ngày chuyển đồng hồ.
- Thời điểm thực trong UTC không nhất thiết cách nhau đúng 24 giờ giữa hai lần cron “09:00 hằng ngày” ở zone có DST.
- Cần test chính sách của **CronExpression/CronTrigger và version cụ thể** ở trước/sau ngày chuyển DST, không đoán bằng cách cộng `Duration.ofHours(24)`.

Nếu yêu cầu là “mỗi ngày đúng 09:00 của người dùng”, giữ zone gốc trong dữ liệu lịch. Nếu yêu cầu là “cứ đúng 24 giờ elapsed time”, cron theo wall clock và `fixedRate`/delay là hai mô hình khác nhau.

### Clock và timezone mặc định

Để lịch dễ chuyển giữa local/staging/production, chọn `zone` rõ ràng ở annotation hoặc `CronTrigger`. Không dựa vào thay đổi JVM default timezone bất ngờ. Nếu chỉ có một zone chung và scheduler dùng một `Clock` cấu hình, kiểm tra giờ của clock cũng như timezone nơi trigger được tính.

## 12. Các macro cron mà Spring hỗ trợ

Ngoài dạng sáu trường, `CronExpression` của Spring hỗ trợ các macro tiện lợi. Macro được **mở rộng thành biểu thức sáu trường** tương ứng.

| Macro | Tương đương | Ý nghĩa |
| --- | --- | --- |
| `@yearly` hoặc `@annually` | `0 0 0 1 1 *` | 00:00 ngày 1/1 hằng năm |
| `@monthly` | `0 0 0 1 * *` | 00:00 ngày 1 mỗi tháng |
| `@weekly` | `0 0 0 * * 0` | 00:00 Chủ nhật mỗi tuần |
| `@daily` hoặc `@midnight` | `0 0 0 * * *` | 00:00 hằng ngày |
| `@hourly` | `0 0 * * * *` | Đầu mỗi giờ |

Ví dụ:

```java
CronExpression daily = CronExpression.parse("@daily");
CronExpression hourly = CronExpression.parse("@hourly");
```

Đây là snippet trong ứng dụng dùng Spring. Dù macro hợp lệ, **không coi một macro là một trường thứ bảy**: nó là biểu diễn rút gọn được parser hỗ trợ.

Khi đọc code người khác, macro giúp dễ hiểu nhưng cũng có thể che khuất múi giờ. `@daily` không thể tự trả lời “nửa đêm ở đâu?”; zone của scheduler/trigger vẫn quyết định.

## 13. Tắt cron có kiểm soát: giá trị "-" không phải biểu thức sáu trường

Spring `@Scheduled.cron` hỗ trợ giá trị đặc biệt `"-"`, được đặt tên `Scheduled.CRON_DISABLED`, để **vô hiệu hóa trigger**. Giá trị này hữu ích khi cron lấy từ application properties.

Ví dụ cấu hình:

```properties
jobs.report.cron=-
```

Và annotation tích hợp tương ứng (property placeholder được Spring resolve):

```java
@Scheduled(
        cron = "${jobs.report.cron:-}",
        zone = "Asia/Ho_Chi_Minh")
public void publishReport() {
    reportService.publish();
}
```

Trong ví dụ trên, nếu property `jobs.report.cron` không tồn tại, giá trị mặc định là `"-"` và trigger không được đăng ký. Nếu property được cấu hình thành `0 30 9 * * MON-FRI`, job được đăng ký theo lịch đó.

**Phân biệt cẩn thận:**

- `"-"` là **sentinel của `@Scheduled` và hạ tầng đăng ký cron của Spring**; nó không phải một chuỗi sáu trường để đưa vào `CronExpression.parse("-")`.
- Một expression không hợp lệ **không** nên được diễn giải là “job đang tắt”: cấu hình lỗi có thể khiến khởi tạo ứng dụng thất bại hoặc scheduler báo lỗi.
- Đặt thuộc tính default `"-"` có nghĩa quên cấu hình sẽ **không chạy job**; với job quan trọng, cần báo động khi cấu hình disable ngoài ý muốn.

Để thay đổi lịch **ngay trong runtime** mà không khởi động lại application, không được mặc định chỉnh property là đủ: `@Scheduled` thường được xử lý và đăng ký trong vòng đời bean. Cần cơ chế hủy/đăng ký lại lịch hoặc một dynamic scheduling component có owner rõ ràng.

## 14. Những lỗi thường gặp với cron sáu trường

### Lỗi 1: Dùng năm trường kiểu Unix trong @Scheduled

**Sai:**

```java
@Scheduled(cron = "30 9 * * MON-FRI")
```

**Đúng:**

```java
@Scheduled(cron = "0 30 9 * * MON-FRI")
```

Vì Spring có trường giây ở đầu. Mẫu sai thiếu trường nên không hợp lệ.

### Lỗi 2: Dùng bảy trường kiểu Quartz

**Sai đối với CronExpression Spring:** `0 30 9 * * MON-FRI 2026`. Trường `2026` là **year** kiểu Quartz, không có vị trí trong sáu trường của Spring.

### Lỗi 3: Nhầm số thứ

Dùng `1` để biểu diễn Chủ nhật vì nhớ Quartz. Trong Spring `1` là **thứ Hai**; viết `SUN` khi muốn Chủ nhật.

### Lỗi 4: Quên múi giờ

Cron đúng 09:30 nhưng môi trường production chạy ở UTC còn dev ở UTC+07. Dùng `zone="Asia/Ho_Chi_Minh"` nếu đó là yêu cầu nghiệp vụ. Không giả định mọi server có timezone giống nhau.

### Lỗi 5: Nghĩ cron biết ngày nghỉ lễ

`MON-FRI` chỉ giới hạn thứ. Ngày lễ rơi vào thứ Hai vẫn khớp. Cần bộ lọc business calendar nếu muốn “ngày làm việc” theo luật/nghỉ lễ tổ chức.

### Lỗi 6: Coi cron hợp lệ là bảo đảm job chạy đúng giờ

Một `ThreadPoolTaskScheduler` có pool size nhỏ, callback blocking dài hoặc JVM bị pause vẫn có thể làm job bắt đầu muộn. Cần metric start lag, timeout, retry và chính sách missed schedule.

### Lỗi 7: Hiểu ? như cú pháp bắt buộc của mọi cron

Trong Spring `?` có thể thay `*` ở hai trường ngày. Dùng parser của Spring để kiểm tra, không ép quy tắc ngầm của Quartz.

### Lỗi 8: Nhầm */10 giữa giây và phút

`*/10 * * * * *` là mốc **mỗi 10 giây**; `0 */10 * * * *` là mốc **mỗi 10 phút**. Thứ tự trường rất quan trọng.

### Lỗi 9: Nhầm cron với fixedDelay

Cron là các mốc phù hợp theo lịch; fixed-delay đợi sau khi lần trước hoàn tất. Hai mô hình không thay thế nhau nếu task có thể chạy dài hơn khoảng giữa các lần kích hoạt.

### Lỗi 10: Ngày trong tháng không tồn tại

Biểu thức như ngày 31 của tháng 2 có thể có cú pháp số nằm trong range nhưng không có lần chạy hợp lệ. Kiểm tra bằng `CronExpression.next(...)` qua các thời điểm thực, không chỉ `isValidExpression(...)`.

### Lỗi 11: Tạo nhiều bản sao job khi chạy nhiều pod

Tất cả pod có thể đăng ký cùng annotation. Nếu chỉ một job được phép xử lý toàn cluster, cần distributed claim/lease hoặc job store và idempotency.

### Lỗi 12: Thêm nhiều @Scheduled vào cùng method và không để ý

`@Scheduled` có thể lặp lại; mỗi khai báo tạo một trigger độc lập. Các trigger có thể gần nhau hoặc chồng lấn, nên cần kiểm tra cấu hình và isolation.

### Lỗi 13: Dùng @daily mà không hiểu 00:00 ở zone nào

`@daily` tương đương 00:00 mỗi ngày theo zone của lịch; không tự có UTC hoặc timezone của khách hàng.

### Lỗi 14: Bỏ qua exception và kết luận scheduler sẽ tự retry

Cron parser không quyết định retry lỗi nghiệp vụ. Nếu task gọi API lỗi, cần quy định retry budget, backoff, idempotency và trạng thái job. Cron chỉ quyết định mốc kích hoạt.

## 15. Kiểm thử cron đúng bằng ngày giờ, không đợi đồng hồ thật

Một bài kiểm thử tốt tách **cú pháp**, **lần khớp kế tiếp** và **thực thi trên scheduler**.

### 15.1. Test parser

Dùng `CronExpression.isValidExpression(...)`:

```java
assert CronExpression.isValidExpression("0 0 9 * * MON-FRI");
assert !CronExpression.isValidExpression("0 9 * * MON-FRI");
assert !CronExpression.isValidExpression("0 0 9 * * MON-FRI 2026");
```

Đây là snippet kiểm tra trong test Java có import và assertions enabled; trong JUnit, ưu tiên `assertTrue/assertFalse` hơn phụ thuộc flag `-ea`. Không viết regex tự phát cho các ký hiệu nâng cao.

### 15.2. Test lần chạy kế tiếp với seed cố định

Dùng `ZonedDateTime.of(...)` và `cron.next(...)`, kiểm tra cả local và instant UTC. Thử trước/sau 09:30, cuối tháng, thứ Sáu tới thứ Hai, cuối năm, và tháng 2 năm nhuận.

Ví dụ một kiểm thử ý tưởng:

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
CronExpression cron = CronExpression.parse("0 30 9 * * MON-FRI");
ZonedDateTime seed =
        ZonedDateTime.of(2026, 11, 6, 9, 30, 0, 0, zone);

ZonedDateTime next = cron.next(seed);
// Mong đợi 2026-11-09T09:30 theo múi giờ Việt Nam.
```

Tính qua date/time library cho kết quả chính xác hơn việc tự cộng “3 ngày” trong mọi loại lịch phức tạp. Đối với DST, dùng zone có DST và mốc trước/sau ngày chuyển đồng hồ để xác nhận chính sách.

### 15.3. Integration test với scheduler thật

Khi muốn kiểm chứng `@Scheduled` hoặc `TaskScheduler.schedule(..., CronTrigger)` đã thực sự thực thi:

1. Bật `@EnableScheduling` và đảm bảo bean được quản lý bởi Spring.
2. Cấu hình scheduler đúng bean/qualifier.
3. Dùng cron ngắn tạm thời cho môi trường test (ví dụ mỗi giây) nhưng **không** dùng cùng schedule test ở production.
4. Dùng `CountDownLatch`/counter để chờ tối đa vài giây và xác nhận callback được gọi.
5. Dùng timeout để test không treo.
6. Tắt/hủy lịch ở teardown để tránh rò thread/job.
7. Tạo thêm test với task blocking để xem missed ticks, error handling và khả năng phục hồi.
8. Nếu nghiệp vụ đòi đúng một lần trên cluster, phải test nhiều instance, durable state và idempotency; parser cron không cung cấp các bảo đảm ấy.

### 15.4. Các tình huống biên nhất định phải thử

| Kịch bản | Mục đích |
| --- | --- |
| 5 trường, 6 trường, 7 trường | Phân biệt Unix/Spring/Quartz |
| Giây `*/10` và phút `*/10` | Tránh nhầm thứ tự |
| Thứ `0`, `1`, `7` | Xác nhận Chủ nhật/Thứ Hai |
| `MON#1`, `5L` | Kiểm tra ký hiệu nâng cao |
| `L` trong tháng 2 nhuận | Ngày cuối tháng thay đổi |
| 09:00 ở HCM và Berlin | Zone thay đổi Instant |
| DST gap/overlap | Giờ địa phương không tồn tại/lặp |
| Nhiều pod | Nhận diện duplicate triggering |
| Task chạy lâu hơn khoảng cron | Missed ticks/lenient vs fixed execution |
| Cron property bằng `"-"` | Disabled trigger được áp dụng đúng |

## 16. Checklist và tài liệu tham khảo

### Checklist tự kiểm tra trước khi dùng cron Spring

1. **Biểu thức có đúng sáu trường?** Giây, phút, giờ, ngày/tháng, tháng, thứ/tuần.
2. **Có chọn đúng trường giây không?** Đầu phút là `0`, không phải `*`.
3. **Range từng trường hợp lệ?** Giây/phút 0–59, giờ 0–23, ngày 1–31, tháng 1–12, thứ 0–7.
4. **Có đang mang quy ước Quartz/Unix vào Spring?** Đặc biệt 5/7 trường và weekday `1`.
5. **Dùng ký hiệu nâng cao đúng vị trí?** `?` chỉ ở ngày/tháng hoặc thứ; `L`/`W`/`#` phải có nghĩa theo field.
6. **Đã dùng parser chính thức?** `CronExpression.parse/isValidExpression`.
7. **Đã kiểm tra các mốc kế tiếp?** Dùng `next(ZonedDateTime)` với seed cố định và kiểm tra local/UTC.
8. **Zone là gì?** Khai báo rõ ở `@Scheduled(zone = ...)` hoặc `CronTrigger`.
9. **Có DST và lịch nghỉ lễ không?** Cron không thay business calendar.
10. **Scheduler có đủ tài nguyên?** Task lâu có thể làm trễ lần chạy; đo p95/p99 start lag.
11. **Lịch có cần dynamic reload?** Nếu có, phải quản lý hủy/đăng ký lại thay vì tin property tự cập nhật tức thì.
12. **Có cần chạy một lần toàn cluster không?** Thiết kế claim/idempotency.
13. **Lỗi nghiệp vụ và missed ticks có chính sách chưa?** Retry, backoff, catch-up hay skip phải được định nghĩa.
14. **Có cách tắt cron an toàn không?** Giá trị `"-"` trong hạ tầng `@Scheduled`, đi kèm cảnh báo nếu critical job bị tắt.

### Tài liệu chính thức

- [Spring Framework 6.2 – CronExpression Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html): sáu trường, các ký hiệu `*`, `?`, `L`, `W`, `#`, macro và `next(...)`.
- [Spring Framework 6.2 – @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): `cron`, `zone`, `scheduler` và hằng `CRON_DISABLED`.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): cấu hình `@EnableScheduling` và ví dụ cron.
- [Spring Framework 6.2 – CronTrigger Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html): parser, time zone, lenient/fixed execution.
- [Spring Framework 6.2 – TaskScheduler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/TaskScheduler.html): đăng ký trigger programmatic.
- [Spring Framework 6.2 – EnableScheduling Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/EnableScheduling.html): kích hoạt quét và đăng ký scheduled methods.
- [Java SE 17 – ZonedDateTime](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html): wall-clock time và offset theo zone.
- [Java SE 17 – ZoneId](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZoneId.html): quy tắc múi giờ và DST.

**Kết luận:** Biểu thức cron Spring có đúng **sáu trường theo thứ tự GIÂY – PHÚT – GIỜ – NGÀY TRONG THÁNG – THÁNG – THỨ TRONG TUẦN**. Thứ tự này quyết định mỗi dấu `*`, `/` hoặc con số có ý nghĩa gì. Hãy luôn kiểm chứng bằng `CronExpression.next(ZonedDateTime)` trong đúng múi giờ, và nhớ rằng cron chỉ xác định **khi nào nên kích hoạt**, không đảm bảo job chạy đúng thời điểm, không tự retry lỗi nghiệp vụ hay bảo đảm exactly-once qua nhiều instance.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cron-timezones-002">Dấu *, ?, phạm vi, danh sách và bước nhảy</a>

<details>
<summary>Xem chi tiết</summary>

# Dấu *, ?, phạm vi, danh sách và bước nhảy trong Spring Cron

## 1. Tại sao cần đọc đúng ký hiệu?

Trong Spring Framework, cron là cách diễn tả **thời điểm trong lịch** để hệ thống kích hoạt công việc. Một thay đổi một ký hiệu có thể khiến tác vụ chạy mỗi giây thay vì mỗi phút, chạy cả cuối tuần thay vì chỉ ngày làm việc, hoặc không bao giờ chạy vào ngày mong muốn.

Ví dụ một service đồng bộ dữ liệu lúc 09:00, 09:15, 09:30 và 09:45, từ thứ Hai đến thứ Sáu. Một biểu thức Spring hợp lệ là:

~~~text
0 0/15 9 ? * MON-FRI
│ │    │ │ │ └── ngày trong tuần: từ MON đến FRI
│ │    │ │ └──── tháng: mọi tháng
│ │    │ └────── ngày trong tháng: không giới hạn cụ thể
│ │    └──────── giờ: 09
│ └───────────── phút: 00, 15, 30, 45
└─────────────── giây: 00
~~~

Mẫu trên đã có đủ sáu trường. Nhưng nếu muốn chạy cả lúc 10 giờ, ta cần **phạm vi giờ**:

~~~text
0 0/15 9-10 ? * MON-FRI
~~~

Nó tương ứng 09:00, 09:15, 09:30, 09:45, 10:00, 10:15, 10:30, 10:45 vào những ngày từ thứ Hai đến thứ Sáu, **không** có mốc 11:00.

Những ký hiệu cần làm chủ:

- `*`: toàn bộ giá trị hợp lệ của **một trường**.
- `?`: trong **ngày của tháng** hoặc **ngày của tuần**, Spring chấp nhận thay cho `*`.
- `-`: phạm vi liên tiếp, **bao gồm cả hai đầu mút**.
- `,`: danh sách rời rạc, lấy hợp các giá trị được liệt kê.
- `/`: bước nhảy trong phạm vi một trường, **không phải** độ trễ kể từ lúc tác vụ trước hoàn thành.

Chương này tập trung vào cú pháp cron **của Spring Framework 6.x** (hợp lệ với Spring Boot 3.x), không coi cron Unix, Kubernetes CronJob hay Quartz là một ngôn ngữ hoàn toàn giống nhau.

## 2. Spring sử dụng sáu trường: giây trước, không có trường năm

Một cron expression Spring thông thường có đúng **sáu trường ngăn bởi khoảng trắng**:

~~~text
┌──────────── giây          0–59
│ ┌────────── phút          0–59
│ │ ┌──────── giờ           0–23
│ │ │ ┌────── ngày/tháng    1–31
│ │ │ │ ┌──── tháng         1–12 hoặc JAN–DEC
│ │ │ │ │ ┌── thứ           0–7 hoặc SUN–SAT
│ │ │ │ │ │                 0 hoặc 7 = Chủ nhật
* * * * * *
~~~

| Vị trí | Tên | Giá trị thông thường | Ví dụ |
| --- | --- | --- | --- |
| 1 | Giây | `0–59` | `0`, `*/10` |
| 2 | Phút | `0–59` | `0,15,30,45` |
| 3 | Giờ | `0–23` | `9-17` |
| 4 | Ngày trong tháng | `1–31` | `1,15`, `?` |
| 5 | Tháng | `1–12` hoặc `JAN–DEC` | `JAN,MAR` |
| 6 | Ngày trong tuần | `0–7`, `SUN–SAT` | `MON-FRI` |

Spring đánh số **thứ Hai = 1**, **thứ Ba = 2**, …, **thứ Bảy = 6**, **Chủ nhật = 0 hoặc 7**. Đây là quy ước cron của Spring; Quartz thường đánh số **Chủ nhật = 1**, nên copy biểu thức dùng số thứ từ Quartz có thể đổi nghĩa. Khi người đọc không muốn tra bảng số, dùng `MON`, `TUE`, `WED`, `THU`, `FRI`, `SAT`, `SUN`.

**Các bẫy ngay từ cấu trúc:**

- Cron Unix truyền thống có **năm trường** (phút, giờ, ngày, tháng, thứ). `0 9 * * MON-FRI` là ví dụ dạng Unix, **không hợp lệ** làm biểu thức Spring sáu trường.
- Quartz hỗ trợ thêm trường năm tùy chọn trong một số biểu thức. `@Scheduled(cron = ...)` của Spring không tự nhận định dạng bảy trường kiểu Quartz.
- Biểu thức `* * * * * *` có giây là `*`, nghĩa là **mỗi giây**, không phải mỗi phút.
- Muốn mỗi phút tại giây 0, viết `0 * * * * *`.
- Muốn mỗi giờ đúng phút 0, giây 0, viết `0 0 * * * *`.

Nếu một biểu thức có vẻ “đúng” nhưng lịch chạy sai, hãy **đếm số trường từ trái sang phải** trước tiên; đừng đoán bằng cách nhìn vào dòng mô tả của cron generator bất kỳ.

## 3. Dấu *: toàn bộ giá trị hợp lệ của từng trường

`*` mang nghĩa “chấp nhận mọi giá trị của trường này”. Nhưng do từng trường có miền giá trị riêng, `*` trong giây và `*` trong ngày của tháng hoàn toàn khác nhau.

~~~text
* * * * * *       mỗi giây
0 * * * * *       mỗi phút tại giây 00
0 0 * * * *       đầu mỗi giờ
0 0 0 * * *       nửa đêm hằng ngày
0 0 9 * * *       09:00 mỗi ngày
0 0 9 * * MON-FRI 09:00 các ngày thứ Hai–thứ Sáu
~~~

Trong `0 0 9 * * MON-FRI`, ngày trong tháng là `*` nhưng ngày trong tuần bị giới hạn bởi `MON-FRI`; lịch **không** chạy cả thứ Bảy và Chủ nhật. `*` chỉ có nghĩa “trường này không thêm ràng buộc riêng”.

### 3.1. Không suy luận * là “một lần”

`@Scheduled(cron = "* * * * * *")` sẽ tạo trigger phù hợp với mọi giây khi scheduler đang hoạt động. Nếu công việc tốn 10 giây, việc lập lịch và bỏ qua các mốc khi bận còn phụ thuộc `CronTrigger`, scheduler và chính sách thực thi; nhưng không được dùng `*` để đại diện cho “chạy một lần lúc khởi động”.

Nếu muốn **chạy một lần**, dùng startup hook phù hợp, `TaskScheduler.schedule(Runnable, Instant)` hoặc job queue, chứ không dùng cron với sáu dấu sao.

### 3.2. Sao trong trường giờ và phút

~~~text
0 * 9 * * *      mỗi phút từ 09:00 đến 09:59
0 0 9 * * *      chỉ 09:00 hằng ngày
0 0 9-17 * * *   mỗi giờ đúng :00 từ 09:00 đến 17:00
~~~

Biểu thức thứ ba có tổng cộng chín mốc mỗi ngày trong điều kiện lịch bình thường: 09, 10, 11, 12, 13, 14, 15, 16 và 17 giờ; **không** phải chạy mỗi phút trong giờ hành chính.

## 4. Dấu ?: chỉ ở day-of-month hoặc day-of-week

Spring `CronExpression` cho phép `?` trong hai trường:

- Vị trí 4 — **ngày của tháng**.
- Vị trí 6 — **ngày của tuần**.

Tài liệu Spring mô tả trong hai trường này `?` có thể được dùng **thay cho `*`**. Nó giúp người đọc hiểu rằng lịch đang chủ yếu lựa chọn theo trường ngày còn lại, và rất phổ biến trong ví dụ mượn phong cách Quartz.

~~~text
0 0 9 ? * MON-FRI    09:00 từ thứ Hai đến thứ Sáu
0 0 9 * * MON-FRI    cũng là 09:00 từ thứ Hai đến thứ Sáu

0 0 9 1,15 * ?      09:00 ngày 1 và 15 hằng tháng
0 0 9 1,15 * *      cũng không giới hạn ngày trong tuần
~~~

**Không dùng** `?` để chỉ “bất kỳ phút nào”:

~~~text
0 ? 9 * * *         SAI: ? ở trường phút
? 0 9 * * MON       SAI: ? ở trường giây
~~~

`?` không phải placeholder cho một đối số sẽ được Spring inject lúc chạy, cũng không biểu thị ngày chưa biết sẽ được API bên ngoài gửi tới. Nó là **ký hiệu cron** được parser xử lý khi xây lịch.

### 4.1. Một ngày trong tháng và một thứ cùng bị giới hạn

Ví dụ:

~~~text
0 0 9 1 * MON
~~~

Trong Spring, khi **cả day-of-month và day-of-week đều có điều kiện cụ thể**, ngày được chọn phải thỏa **cả hai điều kiện**: đó phải là **ngày 1 của tháng và đồng thời là thứ Hai**. Đây là trường hợp dễ sai khi chuyển từ Unix cron, nơi hai day fields hạn chế thường được xử lý theo logic OR. Nếu muốn “09:00 ngày đầu tháng, bất kể thứ”, hãy viết:

~~~text
0 0 9 1 * ?
~~~

Nếu muốn “09:00 mọi thứ Hai, bất kể ngày trong tháng”:

~~~text
0 0 9 ? * MON
~~~

Nếu yêu cầu nghiệp vụ là “ngày 1 **hoặc** mọi thứ Hai”, cân nhắc hai lịch tách biệt và xử lý trùng khi ngày 1 rơi vào thứ Hai. Đừng dựa vào việc trình phân tích cron của nhiều nền tảng có cùng phép OR/AND.

**Cách an toàn:** chọn một trường ngày cụ thể, đặt `?` ở trường còn lại, sau đó dùng `CronExpression.next(...)` để xem một số lần kích hoạt kế tiếp.

## 5. Dấu -: phạm vi liên tiếp, bao gồm hai đầu

`a-b` chọn tất cả các giá trị từ `a` đến `b` trong **cùng một trường**.

| Biểu thức con | Trường | Các giá trị |
| --- | --- | --- |
| `8-10` | Giờ | 8, 9, 10 |
| `10-15` | Phút | 10, 11, 12, 13, 14, 15 |
| `MON-FRI` | Thứ | MON, TUE, WED, THU, FRI |
| `JAN-MAR` | Tháng | JAN, FEB, MAR |
| `1-5` | Ngày trong tháng | Ngày 1, 2, 3, 4, 5 |

Ví dụ:

~~~text
0 0 8-10 * * *       08:00, 09:00, 10:00 hằng ngày
0 15 8-10 ? * MON-FRI
                       08:15, 09:15, 10:15, các ngày làm việc
0 0 9 ? JAN-MAR MON  09:00 các thứ Hai trong tháng 1, 2, 3
~~~

Phạm vi **bao gồm** cả điểm cuối: `8-10` chứa 10, `MON-FRI` chứa FRI. Điều này khác cách hiểu chỉ lấy tới `9` thường gặp khi lập trình với khoảng nửa mở `[8,10)`.

### 5.1. Phạm vi không phải khoảng thời gian liên tục

`0 0 8-10 * * *` có phạm vi ở **giờ** nhưng phút và giây đều cố định ở 0. Vì thế nó chỉ có ba thời điểm chứ không liên tục chạy trong toàn bộ khoảng 08:00–10:59.

Muốn **mỗi phút** trong khoảng giờ 08–10:

~~~text
0 * 8-10 * * *
~~~

Muốn **mỗi 15 phút** từ 08:00 đến 10:45:

~~~text
0 */15 8-10 * * *
~~~

Muốn **đúng 08:00 tới 10:00, bao gồm 10:00 nhưng không có 10:15** thì không diễn đạt bằng `0 */15 8-10 ...`, vì kiểu này vẫn chọn 10:15, 10:30 và 10:45. Có thể dùng nhiều registration có ngữ nghĩa rõ hoặc một trigger tùy biến.

### 5.2. Phạm vi không phải công thức qua nửa đêm

Không coi `22-2` là cách chắc chắn để diễn đạt từ 22:00 hôm nay tới 02:00 hôm sau; nhiều parser, bao gồm mẫu phạm vi tăng thông thường, không hỗ trợ dạng đảo như thế. Hãy tách:

~~~text
0 0 22-23 * * *   22:00, 23:00
0 0 0-2 * * *     00:00, 01:00, 02:00
~~~

Hoặc dùng danh sách ở **cùng một trường** khi các mốc đơn giản: `22,23,0,1,2`. Khi có điều kiện thứ/ngày liên quan đến đêm kéo qua nửa đêm, cần kiểm tra **ngày lịch của từng mốc**, tránh hiểu “thứ Hai 23:00 đến thứ Ba 02:00” thành “mọi mốc đều thuộc thứ Hai”.

## 6. Dấu phẩy ,: danh sách rời rạc

Danh sách `a,b,c` chọn **một trong các giá trị** ở một trường. Khác với phạm vi, các phần tử không phải liền nhau.

~~~text
0 0 6,19 * * *         06:00 và 19:00 mỗi ngày
0 0 9 1,15 * ?         09:00 ngày 1 và 15 hằng tháng
0 0 9 ? * MON,WED,FRI  09:00 thứ Hai, Tư, Sáu
0 0 9 ? JAN,MAR MON    09:00 thứ Hai trong tháng 1 và 3
~~~

Danh sách có thể chứa nhiều dạng thành phần trong cùng trường nếu parser chấp nhận, chẳng hạn `1,3-5` cho các giá trị 1, 3, 4, 5. Ở tầng ứng dụng, ưu tiên biểu thức gọn, dễ review thay vì kết hợp quá nhiều phép trong một trường đến mức người vận hành không đọc nổi.

### 6.1. Danh sách giờ không phải danh sách ngày

~~~text
0 0 9,17 * * MON-FRI
~~~

Có hai mốc trên **mỗi** ngày làm việc: 09:00 và 17:00.

Trong khi:

~~~text
0 0 9 * * MON,FRI
~~~

Có một mốc 09:00 trên **hai** thứ của tuần: thứ Hai và thứ Sáu. Dấu phẩy chỉ vận hành trong **trường nó đứng**, không dịch sang trường khác.

### 6.2. Đừng chèn khoảng trắng vào danh sách

Một cron expression Spring có đúng sáu **trường cách nhau bởi khoảng trắng**. Trong một trường, viết `MON,WED,FRI`, không viết `MON, WED, FRI`. Khoảng trắng thừa có thể khiến biểu thức bị parser tách thành sai số trường.

Dùng biểu thức trong application properties và code review nên để nguyên chuỗi, không tự format dấu phẩy bằng công cụ dành cho văn xuôi.

## 7. Dấu /: bước nhảy bên trong một trường

`/n` lấy các mốc cách nhau n **giá trị trong phạm vi của một trường**. Nó không có nghĩa “nghỉ đúng n giây/phút kể từ lúc tác vụ trước kết thúc”.

Các dạng thường gặp:

| Mẫu ở trường phút/giây | Giá trị được chọn |
| --- | --- |
| `*/15` | 0, 15, 30, 45 trong miền 0–59 |
| `0/15` | 0, 15, 30, 45 |
| `5/15` | 5, 20, 35, 50 |
| `10-50/20` | 10, 30, 50 |
| `*/20` | 0, 20, 40 |
| `*/7` trong giây | 0, 7, 14, 21, 28, 35, 42, 49, 56 |

Ví dụ:

~~~text
0 */15 * * * *         tại phút 00,15,30,45 của mỗi giờ
0 5/15 * * * *         tại phút 05,20,35,50 của mỗi giờ
0 10-50/20 * * * *     tại phút 10,30,50 của mỗi giờ
0 0/30 8-10 * * *      08:00,08:30,09:00,09:30,10:00,10:30
*/10 * * * * *         tại giây 00,10,20,30,40,50 của mỗi phút
~~~

**Phân biệt trường vị trí:** `*/10 * * * * *` có bước nhảy trong **giây** nên được chọn sáu lần mỗi phút, còn `0 */10 * * * *` có bước nhảy trong **phút** và giây cố định ở 0 nên được chọn sáu lần mỗi giờ.

### 7.1. Vì sao “*/7 giây” không phải nhịp đều đúng 7 giây?

Trong trường giây, `*/7` chọn 0, 7, 14, …, 56 của **mỗi phút**. Sau giây 56, mốc tiếp theo là giây 0 của **phút tiếp theo**, tức cách nhau **4 giây**, không phải 7.

~~~text
10:10:49
10:10:56
10:11:00   <- chỉ 4 giây sau mốc 10:10:56
10:11:07
~~~

Vì bước nhảy được tính trong **miền 0–59 của trường giây**, nó không luôn tạo khoảng cách tuyệt đối bằng n giây giữa hai lần bắt đầu. Nếu cần khoảng cách thực sự tính từ **kết thúc** tác vụ trước, dùng `scheduleWithFixedDelay` hoặc `@Scheduled(fixedDelay=...)`. Nếu cần nhịp đều tính từ **thời điểm bắt đầu** trong khả năng scheduler, dùng `fixedRate` hoặc thiết kế trigger theo `Duration` phù hợp.

### 7.2. Điểm bắt đầu của bước nhảy

`*/15` và `0/15` ở trường phút có cùng các giá trị 0,15,30,45. `5/15` thì bắt đầu từ phút 5 nên cho 5,20,35,50. `10-50/20` dùng phạm vi đóng 10..50 và bước 20.

Không viết bước `/0`: bước nhảy phải là số nguyên dương hợp lệ. Cần kiểm tra lại các bước lớn hơn kích thước miền; biểu thức có thể parse được nhưng không hề có nhịp như người đọc mong muốn.

### 7.3. Bước nhảy không tự điều tiết tải

Nếu `@Scheduled(cron = "*/10 * * * * *")` kích hoạt một job rất chậm, cron đang mô tả **các mốc trên lịch**, không đưa ra backpressure, concurrency limit, retry hay timeout I/O. Với scheduling nhiều pod, mỗi instance lại có thể lên lịch theo các mốc đó. Trong production cần giám sát actual start times và lựa chọn mô hình scheduler phù hợp.

## 8. Ghép các ký hiệu: đọc từ trái sang phải

Xét biểu thức tổng hợp:

~~~text
0 0/15 9-10 ? * MON-FRI
~~~

Đọc lần lượt:

1. Giây `0`: bắt đầu đúng giây 00.
2. Phút `0/15`: 00, 15, 30, 45.
3. Giờ `9-10`: 09 và 10.
4. Ngày trong tháng `?`: không thêm điều kiện ngày trong tháng.
5. Tháng `*`: cả 12 tháng.
6. Thứ `MON-FRI`: thứ Hai đến thứ Sáu.

Vậy vào **mỗi ngày làm việc**, các thời điểm theo múi giờ đã chọn là:

~~~text
09:00:00
09:15:00
09:30:00
09:45:00
10:00:00
10:15:00
10:30:00
10:45:00
~~~

Có **8 mốc mỗi ngày làm việc** (2 giờ × 4 lần trong giờ). Nếu viết `0 */15 9-10 ? * MON-FRI` thay `0/15` thì kết quả tương đương.

### 8.1. Một bảng đọc nhanh có giải thích

| Spring cron | Ý nghĩa |
| --- | --- |
| `* * * * * *` | Mỗi giây |
| `0 * * * * *` | Mỗi phút ở giây 00 |
| `0 */5 * * * *` | Phút 00, 05, …, 55 trong mỗi giờ |
| `0 0 * * * *` | Đầu mỗi giờ |
| `0 0 8-10 * * *` | 08:00, 09:00, 10:00 mỗi ngày |
| `0 0 6,19 * * *` | 06:00 và 19:00 mỗi ngày |
| `0 0/30 8-10 * * *` | Mỗi 30 phút từ 08:00 tới 10:30 |
| `0 0 9 ? * MON-FRI` | 09:00 các ngày thứ Hai đến thứ Sáu |
| `0 0 9 1,15 * ?` | 09:00 ngày 1 và 15 hằng tháng |
| `0 15 9-17 ? * MON,WED,FRI` | Phút 15, mỗi giờ 09..17, vào thứ Hai/Tư/Sáu |
| `0 0 0 25 DEC ?` | Nửa đêm ngày 25 tháng 12 |
| `0 0 9 ? JAN-MAR MON` | 09:00 các thứ Hai trong tháng 1–3 |

Bảng thể hiện **các thời điểm được biểu thức chọn**. Nó không bảo đảm scheduler luôn thực thi đúng mili-giây đó: tải hệ thống, thread pool, scheduler pause/shutdown, timeout và chính sách missed executions có thể làm thực tế khác đi.

## 9. Cú pháp nâng cao: L, W, # và macro

Bài này tập trung vào `*`, `?`, `-`, `,`, `/`; tuy nhiên khi đọc biểu thức Spring trong dự án thực tế, bạn còn có thể gặp:

- `L` trong ngày của tháng: **ngày cuối tháng**; ví dụ `0 0 0 L * *`.
- `L-3` trong ngày của tháng: **ngày thứ ba tính ngược từ ngày cuối**.
- `LW`: ngày làm việc cuối tháng.
- `1W`: ngày làm việc gần ngày 1 nhất, với quy tắc không chuyển sang tháng khác.
- `5L` trong ngày của tuần: thứ Sáu cuối tháng theo số thứ của Spring.
- `MON#1`: thứ Hai đầu tiên của tháng.
- Các macro `@hourly`, `@daily`, `@weekly`, `@monthly`, `@yearly` (và alias tương ứng) thay cho biểu thức sáu trường thông dụng.

Ví dụ:

~~~text
0 0 0 L * *        nửa đêm ngày cuối tháng
0 0 0 ? * MON#1    nửa đêm thứ Hai đầu tiên của tháng
0 0 0 * * 5L       nửa đêm thứ Sáu cuối cùng của tháng
@hourly             đầu mỗi giờ
@daily              nửa đêm hằng ngày
~~~

Không suy luận rằng mọi cú pháp đặc biệt của Quartz đều được Spring hỗ trợ giống hệt, đặc biệt ở đánh số ngày trong tuần. Với macro, không thêm các trường `*` ở đằng sau macro như thể macro chỉ chiếm một vị trí: macro tự đại diện cho toàn bộ biểu thức.

Trong quy tắc “lịch làm việc cuối tháng” hoặc “thứ Hai đầu tháng”, múi giờ và calendar logic quan trọng hơn việc cố nhìn ký hiệu để tính nhẩm; hãy dùng `CronExpression.next(...)` và kiểm thử tháng có 28/29/30/31 ngày.

## 10. Cron, múi giờ và DST: cùng một chuỗi, mốc UTC khác nhau

Chuỗi cron **không tự chứa timezone**. `09:00` là “9 giờ trên lịch địa phương mà scheduler/trigger đang sử dụng”, không phải một timestamp tuyệt đối.

Với Spring:

~~~java
// Snippet trong một Spring-managed component:
@Scheduled(
        cron = "0 0 9 ? * MON-FRI",
        zone = "Asia/Ho_Chi_Minh")
public void refreshBusinessDay() {
    // Chạy theo lịch 09:00 các ngày làm việc ở Việt Nam.
}
~~~

`zone` dùng tên múi giờ IANA. Nếu bỏ nó, thời gian được diễn giải theo timezone mặc định của cơ chế scheduler được sử dụng; trên các môi trường khác nhau, mặc định này có thể khác nhau. Production nên **khai báo rõ zone** khi nghiệp vụ gắn với giờ địa phương.

Một vài hệ quả:

- `09:00 Asia/Ho_Chi_Minh` tương ứng `02:00 UTC` trong điều kiện offset +07:00.
- Một pod cấu hình UTC và một pod cấu hình Asia/Ho_Chi_Minh có thể kích hoạt “09:00” ở những thời điểm thực khác nhau nếu code không cố định zone.
- Những vùng có daylight saving time có các thời điểm địa phương **không tồn tại** hoặc **xuất hiện hai lần** khi đổi giờ. Lịch 02:30 hoặc 01:30 vào ngày chuyển DST cần được kiểm thử kỹ: đừng mặc định “cron luôn chạy đúng một lần mỗi ngày”.
- `CronExpression.next(ZonedDateTime)` nhận cả ngày/giờ và timezone, rất phù hợp để kiểm tra các mốc cụ thể.
- Nếu nghiệp vụ cần “mỗi 24 giờ đã trôi qua” thay vì “mỗi ngày theo lịch lúc 09:00”, đó là yêu cầu **Duration**, không nên đồng nhất với cron daily ở vùng có DST.

Ví dụ tuần làm việc ở Việt Nam, khi không có DST hiện tại, kết quả dễ đoán hơn; nhưng vẫn phải tính ngày lễ riêng nếu yêu cầu “ngày làm việc” mang nghĩa **không gồm ngày nghỉ lễ**. `MON-FRI` chỉ lọc ngày trong tuần, không tự biết ngày lễ Việt Nam.

### 10.1. Cấu hình ngoài mã

~~~properties
# Ví dụ application.properties cho một job chạy theo lịch riêng:
jobs.inventory.cron=0 0/15 9-10 ? * MON-FRI
jobs.inventory.zone=Asia/Ho_Chi_Minh
~~~

~~~java
// Snippet trong Spring-managed component:
@Scheduled(
        cron = "${jobs.inventory.cron}",
        zone = "${jobs.inventory.zone:Asia/Ho_Chi_Minh}")
public void synchronize() {
    inventoryService.synchronize();
}
~~~

Spring resolve các placeholder `${...}` tại lúc cấu hình scheduling được xử lý. Dữ liệu cần đi qua validation/config review: một property sai khiến context fail khi đăng ký task; không mặc nhiên kỳ vọng thay đổi giá trị property trong một process đang chạy sẽ tự tái đăng ký lịch. Cũng có thể dùng `@Scheduled(cron = Scheduled.CRON_DISABLED)` hoặc sentinel cấu hình tương ứng để tắt trigger có chủ đích.

## 11. Dự án hoàn chỉnh: Java 17 + Spring Boot kiểm tra cú pháp và lần chạy kế

Thay vì in timestamp của đồng hồ thực rồi so sánh theo cảm tính, ta chọn **mốc đầu vào cố định** và tính các lần kích hoạt bằng `org.springframework.scheduling.support.CronExpression`. Nhờ vậy output của ví dụ không thay đổi theo thời gian chạy, thread scheduling hoặc tải của máy.

Dùng **Java 17, Maven, Spring Boot 3.3.5**. Dự án gồm:

~~~text
spring-cron-symbols/
├── pom.xml
└── src/main/java/com/example/cron/
    └── CronSymbolsApplication.java
~~~

### 11.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>spring-cron-symbols</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 11.2. CronSymbolsApplication.java

~~~java
package com.example.cron;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.support.CronExpression;

@SpringBootApplication
public class CronSymbolsApplication {
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        try (ConfigurableApplicationContext context =
                     SpringApplication.run(
                             CronSymbolsApplication.class, args)) {
            // Runner chạy xong thì context được đóng.
        }
    }

    @Bean
    ApplicationRunner inspectCron() {
        return args -> {
            ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
            ZonedDateTime base = ZonedDateTime.of(
                    2026, 1, 5, 8, 59, 58, 0, zone);

            String businessHours =
                    "0 0/15 9-10 ? * MON-FRI";

            System.out.println("SIX_FIELDS_VALID="
                    + CronExpression.isValidExpression(businessHours));
            System.out.println("UNIX_FIVE_FIELDS_VALID="
                    + CronExpression.isValidExpression(
                            "*/15 9-10 * * MON-FRI"));

            CronExpression weekdayCron =
                    CronExpression.parse(businessHours);

            ZonedDateTime cursor = base;
            for (int i = 1; i <= 4; i++) {
                cursor = weekdayCron.next(cursor);
                if (cursor == null) {
                    throw new IllegalStateException("No next fire");
                }
                System.out.println("NEXT_" + i
                        + "=" + FORMAT.format(cursor));
            }

            CronExpression sevenSeconds =
                    CronExpression.parse("*/7 * * * * *");

            ZonedDateTime afterSecond56 = ZonedDateTime.of(
                    2026, 1, 5, 10, 10, 56, 0, zone);
            ZonedDateTime stepped =
                    sevenSeconds.next(afterSecond56);
            if (stepped == null) {
                throw new IllegalStateException("No next step");
            }
            System.out.println("STEP_AFTER_56="
                    + FORMAT.format(stepped));

            CronExpression firstAndFifteenth =
                    CronExpression.parse("0 0 9 1,15 * ?");
            ZonedDateTime dayOfMonth =
                    firstAndFifteenth.next(base);
            if (dayOfMonth == null) {
                throw new IllegalStateException("No next day");
            }
            System.out.println("DOM_LIST_NEXT="
                    + FORMAT.format(dayOfMonth));
        };
    }
}
~~~

**Điểm cần chú ý:**

- `CronExpression` đến từ **Spring Framework** thông qua dependency của Spring Boot, không thuộc JDK thuần; mã nguồn dùng Java 17 syntax.
- `CronExpression.parse(...)` parse một chuỗi sáu trường; `isValidExpression(...)` kiểm tra cú pháp.
- `next(temporal)` tìm mốc phù hợp **sau** temporal đầu vào, theo zone nếu truyền `ZonedDateTime`.
- Mốc đầu vào cố định là **thứ Hai, ngày 05/01/2026 lúc 08:59:58**, zone `Asia/Ho_Chi_Minh`.
- Lặp bốn lần `next` và cập nhật cursor để lấy bốn thời điểm liên tiếp; không tạo bốn scheduler jobs.
- Không cần `@EnableScheduling` vì ví dụ **chỉ phân tích biểu thức CronExpression**, không đăng ký `@Scheduled` hay worker định kỳ.

### 11.3. Biên dịch/chạy

Trong thư mục chứa `pom.xml`, với JDK 17+ và Maven:

~~~bash
mvn spring-boot:run
~~~

Bỏ qua log khởi động của Spring Boot/Maven, **bảy dòng** do chương trình in:

~~~text
SIX_FIELDS_VALID=true
UNIX_FIVE_FIELDS_VALID=false
NEXT_1=2026-01-05 09:00:00
NEXT_2=2026-01-05 09:15:00
NEXT_3=2026-01-05 09:30:00
NEXT_4=2026-01-05 09:45:00
STEP_AFTER_56=2026-01-05 10:11:00
DOM_LIST_NEXT=2026-01-15 09:00:00
~~~

Có **tám dòng**, không phải bảy: hai kiểm tra valid, bốn lần `next`, một lần kiểm tra bước giây và một lần kiểm tra danh sách ngày. Việc liệt kê rõ số dòng giúp phân biệt stdout của chương trình và log Spring khi copy để kiểm tra.

Giải thích:

- `SIX_FIELDS_VALID=true` vì cron có đúng sáu trường với giá trị hợp lệ.
- `UNIX_FIVE_FIELDS_VALID=false` vì chuỗi ví dụ chỉ có năm trường; nếu cố `parse` sẽ có lỗi.
- Bốn dòng `NEXT` là bốn mốc trong giờ 09 ngày thứ Hai; `0/15` chọn phút 00/15/30/45.
- `STEP_AFTER_56` chứng minh biểu thức `*/7` chọn giây 00 của phút tiếp theo, không chờ cho đủ 7 giây kể từ giây 56.
- `DOM_LIST_NEXT` là ngày 15/01/2026, vì sau ngày 05/01 thì lần gần nhất trong danh sách `1,15` là ngày 15.

Nếu dùng `CronExpression.next(LocalDateTime)` thay vì `ZonedDateTime`, bạn không truyền timezone trong đối tượng và dễ bỏ sót DST. Khi nghiệp vụ phụ thuộc lịch vùng cụ thể, dùng `ZonedDateTime` trong test.

## 12. Áp dụng vào @Scheduled thật

Ví dụ này **khác** ứng dụng phân tích thuần túy mục 11: để chạy method theo lịch, một Spring application cần bật scheduling processing.

~~~java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScheduledApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScheduledApplication.class, args);
    }
}
~~~

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeekdayPoller {
    private final InventoryService inventory;

    public WeekdayPoller(InventoryService inventory) {
        this.inventory = inventory;
    }

    @Scheduled(
            cron = "0 0/15 9-10 ? * MON-FRI",
            zone = "Asia/Ho_Chi_Minh")
    public void poll() {
        inventory.synchronize();
    }
}
~~~

Đây là **hai class minh họa để tích hợp** vào một ứng dụng có bean `InventoryService`; không phải hai class bổ sung mà dự án test mục 11 cần để chạy. Method `poll()` không được nhận tham số; Spring tự đăng ký nó khi `@EnableScheduling` được bật và bean được quản lý.

**Trách nhiệm bên trong method:** timeout HTTP/DB, transaction scope hợp lý, xử lý exception, idempotency và trạng thái lần chạy. Khi job chỉ gửi task async rồi trả về, “scheduled invocation đã xong” **không** có nghĩa công việc nghiệp vụ ở executor khác cũng đã xong.

Nếu ứng dụng có nhiều scheduler, cần xác định bean `TaskScheduler` nào được sử dụng. Trong Spring Framework 6.1+, `@Scheduled` có thuộc tính `scheduler` để chỉ định bean/qualifier thích hợp; đó là **một attribute annotation**, không phải thêm tham số vào `poll()`.

### 12.1. Dùng TaskScheduler và CronTrigger programmatic

Nếu biểu thức đến từ config hoặc cần đăng ký/hủy lịch chủ động:

~~~java
// Snippet nằm trong một Spring-managed component.
CronTrigger trigger = new CronTrigger(
        "0 0/15 9-10 ? * MON-FRI",
        ZoneId.of("Asia/Ho_Chi_Minh"));

ScheduledFuture<?> handle =
        taskScheduler.schedule(this::synchronize, trigger);
~~~

Cần import `org.springframework.scheduling.support.CronTrigger`, `java.time.ZoneId`, `java.util.concurrent.ScheduledFuture` và `TaskScheduler`. `taskScheduler` cùng `synchronize()` là thành phần đã có trong bean. Mẫu programmatic cho phép lưu `handle` và gọi `cancel(false)` khi cần dừng registration. Nó **không** tự tạo job bền vững qua restart.

Chính sách trigger khi một lượt chạy lâu hơn khoảng cách giữa các mốc (skip/misfire/catch-up) cần kiểm tra theo phiên bản `CronTrigger` và scheduler thực tế; không giả định cron sẽ chạy bù **tất cả** thời điểm đã bỏ lỡ.

## 13. Kiểm tra biểu thức trước khi triển khai

Việc parser chấp nhận một cron **không bảo đảm** nó khớp yêu cầu nghiệp vụ. Kiểm tra tối thiểu:

1. **Đủ sáu trường**, trường đầu tiên là giây, không phải phút.
2. **Giá trị hợp lệ** từng trường: không có phút 60, giờ 24, tháng 13, thứ vượt miền; `?` chỉ ở hai trường ngày.
3. **Phạm vi bao gồm đầu mút**; `9-17` chọn cả 17, có thể gây một lần chạy ngoài giờ dự định.
4. **Danh sách đúng trường**: `9,17` ở giờ khác `MON,FRI` ở thứ.
5. **Bước nhảy đúng điểm bắt đầu**: `*/15` khác `5/15`, `*/7` không đều qua biên phút.
6. **Ngày trong tháng và ngày trong tuần**: tránh copy quy tắc OR/AND của Unix hoặc Quartz.
7. **Zone**: test ở timezone nghiệp vụ, không phụ thuộc máy chạy CI.
8. **Tháng đặc biệt**: test tháng 2, leap year, ngày 30/31, và DST nếu vùng có chuyển giờ.
9. **Các mốc gần nhất**: tính 5–10 lần `next` quanh thời điểm quan trọng; biểu thức “hợp lệ” nhưng có thể hiếm khớp hoặc không có thời điểm phù hợp.
10. **Tải và độ tin cậy**: cron quyết định thời điểm đủ điều kiện, không bảo đảm exactly-once, backpressure, timeout, retry và distributed coordination.

Ví dụ helper test:

~~~java
// Snippet Java 17 trong lớp test/service có import cần thiết.
String expression = "0 0 9 ? * MON-FRI";
if (!CronExpression.isValidExpression(expression)) {
    throw new IllegalArgumentException("Invalid Spring cron");
}

CronExpression cron = CronExpression.parse(expression);
ZonedDateTime cursor = ZonedDateTime.of(
        2026, 1, 5, 8, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"));

for (int i = 0; i < 5; i++) {
    cursor = cron.next(cursor);
    if (cursor == null) {
        throw new IllegalStateException("No next matching time");
    }
    System.out.println(cursor);
}
~~~

Đây là snippet không độc lập: cần import `CronExpression`, `ZonedDateTime`, `ZoneId`. Lưu ý `isValidExpression` chỉ xác thực cú pháp parser, **không** xác thực các cam kết doanh nghiệp như “không chạy ngày lễ”, “không trùng job giữa nhiều pod” hoặc “không bao giờ chạy hai lượt đồng thời”.

### 13.1. Bảng lỗi thường gặp

| Biểu hiện | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| Job chạy mỗi giây | `* * * * * *` với `*` ở giây | Dùng `0 * * * * *` nếu muốn mỗi phút |
| Startup fail do cron | Chỉ có năm trường hoặc một trường out-of-range | Đếm sáu trường, chạy `CronExpression.isValidExpression` |
| Muốn thứ Hai–Sáu nhưng chọn sai ngày | Nhầm số DOW của Quartz hoặc Unix | Dùng `MON-FRI`, xác minh số của Spring |
| Chạy lúc 10:45 dù chỉ muốn tới 10:00 | `9-10` giới hạn **giờ**, minute step vẫn chọn cả giờ 10 | Tách/sửa biểu thức theo đúng mốc cuối |
| Bước 7 giây có một quãng 4 giây | `*/7` reset ở biên phút | Chọn duration-based scheduler nếu cần chu kỳ đều |
| Không chạy ngày mong muốn | Đồng thời hạn chế day-of-month và day-of-week | Dùng `?` ở trường không muốn ràng buộc, test `next` |
| Job chạy lệch 7 giờ | Múi giờ scheduler khác mong đợi | Đặt `zone` tường minh |
| Một ngày chạy không đúng một lần | DST: giờ địa phương bỏ qua hoặc lặp; job chậm | Test lịch DST và chính sách trigger/lock |
| Muốn chạy ngày lễ theo lịch Việt Nam | `MON-FRI` chỉ là thứ trong tuần | Dùng business calendar riêng |
| Job chạy ở mỗi pod | `@Scheduled` được đăng ký trong từng instance | Distributed lock/DB claim/idempotency |
| Đổi property mà cron không đổi | Registration đã tạo khi context khởi động | Thiết kế cơ chế rescheduling nếu cần thay động |
| Job quá tải hoặc chạy kéo dài | Cron không kiểm soát runtime của task | Bounded concurrency, timeout, metrics và retry |

## 14. Bài tập tự kiểm tra

1. Vì sao `0 9 * * MON-FRI` có thể hợp lệ với Unix cron nhưng không hợp lệ với `@Scheduled(cron = ...)` của Spring?
2. `0 * * * * *` khác `* * * * * *` ở tần suất thế nào?
3. `0 0 8-10 * * *` có chạy lúc 08:15 không? Vì sao?
4. `0 0/20 8-9 * * *` có những mốc nào trong hai giờ 08 và 09?
5. `0 5/15 * * * *` có mốc phút 00 không? Các mốc cụ thể là gì?
6. `0 0 9 ? * MON-FRI` khác `0 0 9 * * MON-FRI` về mặt điều kiện ngày thế nào trong Spring?
7. `0 0 9 1 * MON` và `0 0 9 1 * ?` có khác nhau không? Ngày nào được chọn?
8. Vì sao `*/7 * * * * *` không có khoảng cách 7 giây đều trên toàn trục thời gian?
9. Vì sao dùng `MON-FRI` an toàn hơn copy một dãy số thứ từ Quartz?
10. Một cron 09:00 được deploy lên server ở UTC và Asia/Ho_Chi_Minh. Không khai báo zone thì rủi ro gì?
11. Nếu muốn ngày làm việc nhưng loại trừ cả ngày nghỉ lễ Việt Nam, biểu thức cron đơn thuần đã đủ chưa?
12. Nếu có 3 pod cùng chạy một cron expression, ký hiệu trong cron có tự bảo đảm chỉ một pod thực hiện không?

**Kết luận:** hãy đọc Spring cron theo sáu trường **giây – phút – giờ – ngày trong tháng – tháng – thứ trong tuần**. `*` chọn toàn miền, `?` chỉ thay `*` ở hai trường ngày, `-` lấy phạm vi **bao gồm hai đầu**, `,` lấy các giá trị trong danh sách, còn `/` chọn bước trong **miền của trường**, không phải một timer bảo đảm khoảng cách thực tế. Luôn tính trước các mốc bằng `CronExpression.next(...)` với timezone rõ ràng trước khi đưa lịch vào production.

## Tài liệu chính thức

- [Spring Framework — Task Execution and Scheduling: Cron Expressions](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-cron-expression)
- [Spring Framework 6.x — CronExpression API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html)
- [Spring Framework 6.x — @Scheduled API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework 6.x — CronTrigger API](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html)
- [Spring Engineering — Improved Cron Expressions](https://spring.io/blog/2020/11/10/new-in-spring-5-3-improved-cron-expressions/)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cron-timezones-003">Các ký hiệu L, W và # khi cần lịch theo ngày</a>

<details>
<summary>Xem chi tiết</summary>

# Các ký hiệu L, W và # khi cần lịch theo ngày

Trong module **Spring Framework - concurrency**, nhóm **Cron và múi giờ**, các lịch như “ngày cuối tháng”, “ngày trong tuần gần mùng 15” hay “thứ Sáu thứ hai của tháng” cần cú pháp cron biểu diễn **ngày theo vị trí trong tháng**. Spring `CronExpression` hỗ trợ ba nhóm ký hiệu đặc biệt:

- **`L` — last:** ngày cuối tháng hoặc lần cuối của một thứ cụ thể trong tháng.
- **`W` — nearest weekday:** ngày thứ Hai–thứ Sáu gần một ngày-tháng cụ thể nhất nhưng không vượt sang tháng khác.
- **`#` — nth weekday:** lần xuất hiện thứ n của một thứ trong tháng.

Cú pháp đúng chưa đủ: lịch có thể chọn sai ngày nếu nhầm trường, nhầm số thứ trong Spring với Quartz, không xét tháng thiếu lần xuất hiện thứ năm, quên múi giờ hoặc tưởng weekday có nghĩa là ngày giao dịch không nghỉ lễ. Bài học giải thích ngữ nghĩa và cơ chế, cung cấp chương trình Java 17 độc lập đã kiểm chứng cùng các ví dụ Spring `@Scheduled` và `CronTrigger`.

## 1. Vị trí của L, W và # trong cron Spring

Cron của Spring Framework sử dụng **sáu trường**, theo thứ tự **giây – phút – giờ – ngày-tháng – tháng – thứ**:

~~~text
┌──────────── giây          0..59
│ ┌────────── phút          0..59
│ │ ┌──────── giờ           0..23
│ │ │ ┌────── ngày-tháng    1..31 / L / L-n / LW / nW
│ │ │ │ ┌──── tháng         1..12 hoặc JAN..DEC
│ │ │ │ │ ┌── thứ           0..7 hoặc MON..SUN / dL / d#n
│ │ │ │ │ │
0 30 8 LW * ?
~~~

Ví dụ cuối đọc là **08:30 ngày thứ Hai đến thứ Sáu cuối cùng của mỗi tháng**, tính trong múi giờ mà scheduler sử dụng.

**Hai trường ngày khác nhau:**

- Vị trí thứ **tư**, *day-of-month*: `L`, `L-3`, `LW`, `1W`, `15W`.
- Vị trí thứ **sáu**, *day-of-week*: `FRIL`, `5L`, `MON#1`, `FRI#2`.
- `W` ở đây **không có nghĩa Wednesday**; Wednesday được viết `WED`.
- `#` là cách đếm một **thứ** trong tháng, không phải cách đếm “ngày mùng n”.
- `L` đổi nghĩa theo trường; `L` ở trường day-of-month khác `FRIL` ở trường day-of-week.

### 1.1. * và ? có ý nghĩa gì?

Trong Spring, `?` có thể dùng thay `*` trong **day-of-month** hoặc **day-of-week** để nói không giới hạn trường đó. Dùng `?` cho trường ngày không quan tâm giúp biểu thức dễ đọc:

~~~text
0 0 9 L * ?        # theo ngày cuối tháng, không giới hạn thứ
0 0 9 ? * FRI#2    # theo thứ Sáu thứ hai, không giới hạn ngày-tháng
~~~

Phần chú thích sau dấu `#` ở khối trên **chỉ dành cho người đọc**, không phải thành phần của cron string đưa vào `@Scheduled`. Dấu `#` **nằm trong** `FRI#2` mới là cú pháp thật.

Nếu **cả day-of-month và day-of-week đều được ràng buộc**, Spring yêu cầu một ngày khớp với cả hai tiêu chí; không mặc định mang quy tắc OR của một số biến thể Unix cron vào Spring. Hãy để trường không liên quan là `?` hoặc `*`.

## 2. Ký hiệu L ở trường ngày-tháng: luôn chọn cuối tháng

Trong day-of-month:

- `L`: **ngày cuối cùng của tháng**, bất kể tháng có 28, 29, 30 hay 31 ngày.
- `L-n`: **lùi n ngày** từ ngày cuối. Ví dụ tháng có 31 ngày thì `L-3` là ngày 28.
- `LW`: **ngày thứ Hai–thứ Sáu cuối cùng của tháng**.

Các biểu thức tương ứng:

| Biểu thức cron Spring | Nghĩa |
| --- | --- |
| `0 0 9 L * ?` | 09:00 ngày cuối mỗi tháng |
| `0 0 9 L-3 * ?` | 09:00, lùi 3 ngày từ ngày cuối tháng |
| `0 30 8 LW * ?` | 08:30 weekday cuối tháng |

Bảng ngày minh họa:

| Tháng | `L` | `L-3` | `LW` |
| --- | --- | --- | --- |
| 02/2024 (năm nhuận) | 29/02 | 26/02 | 29/02 (thứ Năm) |
| 02/2026 | 28/02 (thứ Bảy) | 25/02 | 27/02 (thứ Sáu) |
| 03/2026 | 31/03 (thứ Ba) | 28/03 | 31/03 (thứ Ba) |
| 10/2026 | 31/10 (thứ Bảy) | 28/10 | 30/10 (thứ Sáu) |

**Tại sao không dùng `31` để nói “cuối tháng”?** Vì tháng 2, 4, 6, 9 và 11 không có ngày 31. `0 0 9 31 * ?` không chạy cuối những tháng đó; `L` mới mô tả đúng yêu cầu.

### 2.1. LW chỉ xét weekday, không biết ngày nghỉ lễ

Nếu ngày cuối tháng là thứ Bảy hoặc Chủ nhật, `LW` chọn thứ Sáu ngay trước đó. Nếu ngày cuối là Mon–Fri, nó chọn chính ngày cuối.

Ví dụ 02/2026 ngày 28 là thứ Bảy, vậy `LW` chọn **27/02/2026**. Nhưng nếu ngày 27 đó là nghỉ lễ hoặc lịch đóng cửa đặc biệt theo ngân hàng, cron vẫn chọn nó. Trong cú pháp này, weekday = **thứ Hai đến thứ Sáu**, không phải “business day theo calendar tài chính”.

Nếu cần ngày giao dịch cuối cùng theo ngày nghỉ lễ Việt Nam, thị trường chứng khoán hay lịch của doanh nghiệp, phải dùng **business calendar/holiday service** riêng hoặc kiểm tra lịch nghiệp vụ trước khi tạo side effect.

### 2.2. L trong trường thứ: dL / DDDL

Để chọn **lần cuối của một thứ trong tháng**, đặt `L` sau tên hoặc chỉ số thứ ở **day-of-week**:

~~~text
0 0 9 ? * 5L      # thứ Sáu cuối cùng của tháng (Spring FRI=5)
0 0 9 ? * FRIL    # cùng ngày, dễ đọc hơn
0 0 9 ? * THUL    # thứ Năm cuối cùng của tháng
~~~

Khi viết `5L`, ký hiệu `L` không chỉ “ngày cuối tháng”; nó điều chỉnh **thứ Sáu** thành lần thứ Sáu cuối cùng xuất hiện trong tháng. Với một tháng kết thúc bằng thứ Bảy, `FRIL` chọn thứ Sáu ngay trước đó. Với tháng kết thúc vào thứ Ba, nó chọn thứ Sáu của tuần trước.

Bảng đánh số thứ trong Spring:

| Spring | Thứ | Tên |
| --- | --- | --- |
| 0 hoặc 7 | Chủ nhật | SUN |
| 1 | Thứ Hai | MON |
| 2 | Thứ Ba | TUE |
| 3 | Thứ Tư | WED |
| 4 | Thứ Năm | THU |
| 5 | Thứ Sáu | FRI |
| 6 | Thứ Bảy | SAT |

Spring kế thừa cách đánh số cron truyền thống. Quartz lại thường dùng SUN=1, MON=2, FRI=6; nếu copy `6L` từ Quartz vào Spring thì nó có thể biến thành **thứ Bảy cuối tháng**, không còn là thứ Sáu. Dùng tên `FRIL` khi có thể để loại nhầm lẫn.

## 3. Ký hiệu W: weekday gần ngày n, nhưng không qua ranh giới tháng

Trong day-of-month, cú pháp `nW` nghĩa là **ngày thứ Hai–thứ Sáu gần ngày n nhất** của tháng đó.

Các trường hợp:

1. Nếu ngày n là Mon–Fri, giữ **nguyên ngày n**.
2. Nếu là thứ Bảy, thường chọn **thứ Sáu trước đó**.
3. Nếu là Chủ nhật, thường chọn **thứ Hai kế tiếp**.
4. Nếu cách chọn thông thường làm vượt khỏi tháng, chuyển sang weekday còn lại **trong chính tháng**.

Ví dụ:

| Biểu thức | Tình huống | Ngày được chọn |
| --- | --- | --- |
| `1W` tháng 03/2026 | Mùng 1 là Chủ nhật | **Thứ Hai 02/03/2026** |
| `1W` tháng 08/2026 | Mùng 1 là thứ Bảy | **Thứ Hai 03/08/2026** |
| `31W` tháng 10/2026 | Ngày 31 là thứ Bảy | **Thứ Sáu 30/10/2026** |
| `15W` khi ngày 15 là Mon–Fri | Đã là weekday | **Chính ngày 15** |
| `15W` khi ngày 15 là Chủ nhật | Gần thứ Hai kế tiếp | **Thứ Hai ngày 16** |

Ví dụ cú pháp:

~~~text
0 0 9 1W * ?      # weekday đầu tháng, gần ngày 1 nhất
0 0 9 15W * ?     # weekday gần ngày 15 nhất
0 0 9 31W * ?     # weekday gần ngày 31, nếu tháng có ngày 31
~~~

### 3.1. Vì sao 1W không quay về tháng trước?

Giả sử 01/08/2026 rơi vào thứ Bảy. Thứ Sáu 31/07 gần hơn thứ Hai 03/08 về mặt số ngày; nhưng `1W` **không được rời tháng**. Vì vậy kết quả là **03/08/2026**.

Tương tự, nếu ngày cuối tháng rơi vào Chủ nhật, `nW` ở ngày cuối có thể quay về thứ Sáu trước đó thay vì chọn thứ Hai đầu tháng tiếp.

Hãy nhớ giới hạn này khi kiểm thử tháng 2 và những tháng có ngày mùng 1/cuối tháng vào weekend.

### 3.2. 31W khác LW

`31W` **không phải** cách viết khác của `LW`:

- `31W` phụ thuộc vào sự tồn tại của **ngày 31**. Các tháng không có ngày 31 không phải tự động chuyển sang ngày cuối tháng.
- `LW` chọn weekday cuối cùng ở **mọi tháng**.

Với yêu cầu “weekday cuối tháng”, nên dùng `LW`. Với yêu cầu “weekday gần ngày 15”, dùng `15W`. Với yêu cầu “ngày làm việc có trừ ngày nghỉ lễ”, **cả hai đều chưa đủ**.

### 3.3. W không có nghĩa “mỗi thứ Tư”

Người mới có thể đọc `15W` như “ngày 15 là Wednesday”. Điều đó sai; `W` ở đây nghĩa *weekday* (Mon–Fri). Khi cần thứ Tư cụ thể, dùng trường day-of-week như `WED` hoặc `WED#2`, tùy yêu cầu.

## 4. Ký hiệu #: lần xuất hiện thứ n của một thứ trong tháng

Cú pháp `d#n` (chỉ số) hoặc `DDD#n` (tên thứ) nằm ở **day-of-week**:

- `MON#1`: thứ Hai **đầu tiên** trong tháng.
- `FRI#2`: thứ Sáu **thứ hai** trong tháng.
- `5#3`: thứ Sáu **thứ ba** theo số Spring FRI=5.
- `FRI#5`: thứ Sáu **thứ năm**, nhưng không phải tháng nào cũng có.

Ví dụ:

~~~text
0 0 8 ? * MON#1   # 08:00 thứ Hai đầu tiên
0 0 9 ? * FRI#2   # 09:00 thứ Sáu thứ hai
0 0 9 ? * FRI#5   # 09:00 thứ Sáu thứ năm, chỉ tháng có
~~~

### 4.1. Ví dụ tính FRI#2 và MON#1

Tháng 03/2026 có thứ Sáu vào các ngày **06, 13, 20, 27**. Vì vậy:

- `FRI#1` = 06/03/2026.
- `FRI#2` = **13/03/2026**.
- `FRI#4` = 27/03/2026.
- `MON#1` = **02/03/2026**.

`FRI#2` không có nghĩa ngày mùng 2 là thứ Sáu hoặc cứ hai tuần chạy một lần. Nó **đếm thứ Sáu từ đầu tháng**.

### 4.2. Vì sao FRI#5 không thể thay FRIL?

Tháng 02/2026 chỉ có bốn thứ Sáu: 06, 13, 20, 27. Không có thứ Sáu thứ năm. Ngược lại, tháng 10/2026 có năm thứ Sáu: 02, 09, 16, 23, 30.

| Lịch | Tháng 02/2026 | Tháng 10/2026 |
| --- | --- | --- |
| `FRIL` / `5L` | **27/02** | **30/10** |
| `FRI#4` | 27/02 | 23/10 |
| `FRI#5` | **Không có lần chạy** | 30/10 |

Do đó, khi yêu cầu “thứ Sáu cuối cùng mỗi tháng”, luôn ưu tiên `FRIL`/`5L`. `FRI#5` có thể parse hợp lệ nhưng trong tháng thiếu thứ Sáu thứ năm sẽ **không có thời điểm khớp**.

### 4.3. Trường hợp nào nên dùng #?

`#` phù hợp lịch hội họp cố định theo vị trí thứ trong tháng, chẳng hạn “thứ Hai đầu tiên” hoặc “thứ Năm thứ ba”. Nếu lịch là “thứ Sáu cuối”, không tính n, mà dùng `L`. Nếu lịch cần thứ làm việc theo calendar ngày nghỉ lễ, cron không tự biết ngày lễ hoặc nghỉ bù.

## 5. Bảng chọn nhanh cho các yêu cầu thường gặp

| Yêu cầu | Cron ở 09:00 | Ghi chú |
| --- | --- | --- |
| Ngày cuối mỗi tháng | `0 0 9 L * ?` | Kể cả weekend |
| Ba ngày trước cuối tháng | `0 0 9 L-3 * ?` | Trừ theo ngày lịch |
| Weekday cuối tháng | `0 0 9 LW * ?` | Không loại ngày lễ |
| Weekday gần mùng 1 | `0 0 9 1W * ?` | Không rời tháng |
| Weekday gần mùng 15 | `0 0 9 15W * ?` | Không phải thứ Tư |
| Thứ Hai đầu tiên | `0 0 9 ? * MON#1` | Đếm lần xuất hiện |
| Thứ Sáu thứ hai | `0 0 9 ? * FRI#2` | Đếm từ đầu tháng |
| Thứ Sáu cuối | `0 0 9 ? * FRIL` | Tương đương `5L` trong Spring |
| Thứ Sáu thứ năm | `0 0 9 ? * FRI#5` | Không có ở mọi tháng |

Nên dùng các tên `MON`, `FRI`, `SAT` thay vì chỉ số số học trong lịch nghiệp vụ; chúng thường dễ review hơn và tránh sai lệch giữa Spring và Quartz.

### 5.1. So sánh với Unix cron và Quartz

**Unix crontab truyền thống** thường có **năm trường**, không có trường giây. Các extension `L`, `W`, `#` không được hỗ trợ đồng nhất ở mọi bản cron. Không copy một chuỗi Spring sáu trường vào hệ thống Unix hoặc ngược lại.

**Quartz** có cú pháp tương tự cho các ngày nâng cao, nhưng cách đánh số thứ khác với Spring: Quartz thường dùng Sunday=1, Friday=6; Spring dùng Sunday=0/7, Friday=5. Ví dụ Quartz `6L` và Spring `5L` cùng đại diện thứ Sáu cuối tháng.

**Spring CronExpression** dùng sáu trường, không nhận một trường năm thứ bảy theo cú pháp Quartz tùy chọn. Các quy tắc day-of-month/day-of-week khi cả hai được ràng buộc cũng cần được kiểm tra theo chính engine đích.

## 6. Java 17 chạy độc lập: minh họa chính xác L, W và #

Chương trình sau dùng `YearMonth`, `LocalDate`, `DayOfWeek` và `TemporalAdjusters` để tính **ngày lịch theo ý nghĩa** của `L`, `LW`, `nW`, `dL` và `d#n`.

**Đây là minh họa Java thuần, không phải parser CronExpression của Spring.** Chúng ta dùng nó để hiểu quy tắc với những ngày cố định; ứng dụng thực tế phải dùng parser của Spring và kiểm thử lịch thực tế ở các timezone quan tâm.

Tạo tệp **`CronCalendarSymbolsDemo.java`**:

~~~java
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

public class CronCalendarSymbolsDemo {
    static boolean isWeekday(LocalDate day) {
        return day.getDayOfWeek() != DayOfWeek.SATURDAY
                && day.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    static LocalDate nearestWeekday(YearMonth month, int n) {
        LocalDate original = month.atDay(n);
        if (isWeekday(original)) {
            return original;
        }
        if (original.getDayOfWeek() == DayOfWeek.SATURDAY) {
            return n == 1 ? original.plusDays(2) : original.minusDays(1);
        }
        return n == month.lengthOfMonth()
                ? original.minusDays(2) : original.plusDays(1);
    }

    static LocalDate lastWeekday(YearMonth month) {
        LocalDate result = month.atEndOfMonth();
        while (!isWeekday(result)) {
            result = result.minusDays(1);
        }
        return result;
    }

    static LocalDate lastOccurrence(YearMonth month, DayOfWeek weekday) {
        return month.atEndOfMonth().with(
                TemporalAdjusters.previousOrSame(weekday));
    }

    static Optional<LocalDate> nthOccurrence(
            YearMonth month, DayOfWeek weekday, int n) {
        if (n < 1 || n > 5) {
            throw new IllegalArgumentException("n must be 1 to 5");
        }
        LocalDate first = month.atDay(1).with(
                TemporalAdjusters.nextOrSame(weekday));
        LocalDate candidate = first.plusWeeks(n - 1);
        return candidate.getMonthValue() == month.getMonthValue()
                ? Optional.of(candidate) : Optional.empty();
    }

    public static void main(String[] args) {
        YearMonth feb26 = YearMonth.of(2026, 2);
        YearMonth mar26 = YearMonth.of(2026, 3);
        YearMonth aug26 = YearMonth.of(2026, 8);
        YearMonth oct26 = YearMonth.of(2026, 10);
        System.out.println("1. 2026-02 L: " + feb26.atEndOfMonth());
        System.out.println("2. 2024-02 L: "
                + YearMonth.of(2024, 2).atEndOfMonth());
        System.out.println("3. 2026-03 1W: " + nearestWeekday(mar26, 1));
        System.out.println("4. 2026-08 1W: " + nearestWeekday(aug26, 1));
        System.out.println("5. 2026-10 31W: " + nearestWeekday(oct26, 31));
        System.out.println("6. 2026-02 LW: " + lastWeekday(feb26));
        System.out.println("7. 2026-10 FRIL: "
                + lastOccurrence(oct26, DayOfWeek.FRIDAY));
        System.out.println("8. 2026-03 FRI#2: "
                + nthOccurrence(mar26, DayOfWeek.FRIDAY, 2).orElseThrow());
        System.out.println("9. 2026-02 FRI#5: "
                + nthOccurrence(feb26, DayOfWeek.FRIDAY, 5)
                       .map(LocalDate::toString).orElse("none"));
    }
}
~~~

Biên dịch và chạy trên Java 17 trở lên:

~~~bash
javac --release 17 CronCalendarSymbolsDemo.java
java CronCalendarSymbolsDemo
~~~

Stdout **đã kiểm chứng bằng thực thi Java**:

~~~text
1. 2026-02 L: 2026-02-28
2. 2024-02 L: 2024-02-29
3. 2026-03 1W: 2026-03-02
4. 2026-08 1W: 2026-08-03
5. 2026-10 31W: 2026-10-30
6. 2026-02 LW: 2026-02-27
7. 2026-10 FRIL: 2026-10-30
8. 2026-03 FRI#2: 2026-03-13
9. 2026-02 FRI#5: none
~~~

Giải thích logic:

- `atEndOfMonth()` hiểu số ngày tháng, kể cả năm nhuận: gần với `L`.
- `lastWeekday(...)` lùi tới ngày Mon–Fri cuối cùng: gần với `LW`.
- `nearestWeekday(...)` xử lý weekend, đặc biệt trường hợp mùng 1 và cuối tháng, không chọn ngày ở tháng khác.
- `previousOrSame(FRIDAY)` từ ngày cuối tháng tìm thứ Sáu cuối cùng: `FRIL`.
- `nextOrSame(FRIDAY)` từ ngày mùng 1, cộng n-1 tuần, tìm `FRI#n`.
- Nếu candidate của `#5` đã qua tháng sau, trả `Optional.empty()`; tuyệt đối không thay bằng lần thứ tư.

Chương trình minh họa **calendar calculation**, không tự hỗ trợ tất cả tổ hợp cron, DST, month filter, trigger lifecycle hay error policy. Với Spring, dùng `CronExpression.parse(...)` và `next(...)` để xác minh output thật của parser.

## 7. Sử dụng trong Spring Framework @Scheduled

Đây là một ví dụ **tích hợp Spring Framework 6.1+ và Java 17+**, gồm cấu hình scheduler và hai method công việc. Nó cần `spring-context`, ApplicationContext, component scanning và lifecycle của Spring; **không phải mã độc lập chạy bằng javac thiếu classpath**.

Tệp **`CalendarSchedulerConfiguration.java`**:

~~~java
package example.scheduling;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class CalendarSchedulerConfiguration {
    @Bean("calendarScheduler")
    public ThreadPoolTaskScheduler calendarScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("calendar-");
        return scheduler;
    }
}
~~~

Tệp **`CalendarJobs.java`**:

~~~java
package example.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CalendarJobs {
    @Scheduled(
            cron = "0 30 8 LW * ?",
            zone = "Asia/Ho_Chi_Minh",
            scheduler = "calendarScheduler")
    public void reconcileLastWeekday() {
        System.out.println("Reconcile on last Mon-Fri of month");
    }

    @Scheduled(
            cron = "0 0 9 ? * FRI#2",
            zone = "Asia/Ho_Chi_Minh",
            scheduler = "calendarScheduler")
    public void sendSecondFridayReminder() {
        System.out.println("Send second Friday reminder");
    }
}
~~~

**Ý nghĩa:**

- `@EnableScheduling` kích hoạt việc đăng ký `@Scheduled`.
- `scheduler = "calendarScheduler"` chọn đúng bean (thuộc tính này có trong Spring Framework 6.1+); tránh mập mờ khi có nhiều scheduler.
- `zone = "Asia/Ho_Chi_Minh"` làm cho điều kiện ngày/giờ được đánh giá trong múi giờ Việt Nam, không phụ thuộc timezone máy chủ.
- Method đầu chạy **08:30 weekday cuối tháng**; method thứ hai **09:00 thứ Sáu thứ hai của tháng**.
- Đây là hai trigger độc lập. Việc callback được kích hoạt **không bảo đảm nghiệp vụ thực hiện chính xác một lần toàn cluster**, không bảo đảm database commit hoặc downstream nhận request thành công.
- `System.out.println` chỉ phục vụ minh họa; trong sản phẩm nên có logging, metrics, exception handling, idempotency và deadline phù hợp.

### 7.1. Các biểu thức khác có thể dùng tương tự

~~~java
@Scheduled(cron = "0 0 9 L * ?", zone = "Asia/Ho_Chi_Minh")
public void onLastCalendarDay() {}

@Scheduled(cron = "0 0 9 1W * ?", zone = "Asia/Ho_Chi_Minh")
public void onFirstWeekday() {}

@Scheduled(cron = "0 0 9 ? * MON#1", zone = "Asia/Ho_Chi_Minh")
public void onFirstMonday() {}
~~~

Đây là **các method Spring minh họa**, phải đặt trong bean do Spring quản lý và đã bật scheduling. Không khai báo tất cả lịch trên nếu không có nhu cầu: mỗi annotation được đăng ký thành lịch riêng, có thể tạo nhiều lần chạy.

## 8. CronTrigger và CronExpression: preview trước khi đưa lên production

Nếu muốn đăng ký lịch bằng code thay vì `@Scheduled`, Spring cung cấp `CronTrigger`:

~~~java
import java.time.ZoneId;
import org.springframework.scheduling.support.CronTrigger;

CronTrigger trigger = new CronTrigger(
        "0 30 8 LW * ?", ZoneId.of("Asia/Ho_Chi_Minh"));
scheduler.schedule(task, trigger);
~~~

Đoạn này là **snippet Spring**, giả định `scheduler` là một `TaskScheduler` và `task` là `Runnable`. Constructor `CronTrigger(String, ZoneId)` giữ zone tường minh. Phương thức `schedule(task, trigger)` có thể trả `ScheduledFuture<?>` hoặc `null` khi trigger không có lần kích hoạt tiếp theo; cần xử lý lifecycle và chính sách hủy đúng.

Để kiểm thử **các ngày được tính**, dùng `CronExpression.parse(...).next(...)`:

~~~java
package example.scheduling;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

public class SpringCronPreview {
    public static void main(String[] args) {
        CronExpression expression =
                CronExpression.parse("0 30 8 LW * ?");
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime cursor = ZonedDateTime.of(
                2026, 2, 1, 0, 0, 0, 0, zone);

        for (int i = 0; i < 4; i++) {
            cursor = expression.next(cursor);
            if (cursor == null) {
                break;
            }
            System.out.println(cursor.toLocalDate());
        }
    }
}
~~~

Class này có `main` nhưng **cần Spring Framework JAR trong classpath**. Không nhận nó là chương trình đã biên dịch bằng JDK thuần ở mục 6. Các ngày kỳ vọng cho chuỗi `LW` từ tháng 02/2026 là **27/02, 31/03, 30/04, 29/05**; hãy kiểm thử trực tiếp bằng đúng phiên bản Spring trong ứng dụng.

`CronExpression.isValidExpression(...)` giúp xác nhận biểu thức **hợp lệ về cú pháp**, nhưng không chứng minh rằng `FRI#5` có một lượt trong **mọi tháng**. Để kiểm thử lịch thật, dùng một seed cố định và lấy nhiều lần `next(...)`, kiểm tra kết quả bằng ngày-tháng và zone.

### 8.1. Múi giờ trong preview

Dùng `ZonedDateTime` với `ZoneId` tương ứng, thay vì `LocalDateTime` không mang offset. Một biểu thức 09:00 được đánh giá theo zone và tương ứng với một `Instant` UTC cụ thể.

Nếu bạn preview ở `Asia/Ho_Chi_Minh` nhưng production đặt `zone="UTC"` hoặc để timezone JVM mặc định khác, **kết quả instant có thể lệch**, dù chuỗi cron giống hệt nhau.

## 9. Múi giờ, DST và lịch theo tháng

Ba ký hiệu `L/W/#` chọn ngày trong **calendar địa phương**, nhưng thời điểm thực tế xảy ra phải tính bằng ngày + giờ + zone:

- `zone = "Asia/Ho_Chi_Minh"`: lịch 09:00 nghĩa là 09:00 giờ Việt Nam; với mốc 13/03/2026, tương ứng **02:00 UTC**.
- Không đặt zone: scheduler dùng zone/clock mặc định của môi trường; hai máy ở hai timezone có thể chạy cùng cron vào các instant khác nhau.
- Với zone có DST, có những giờ địa phương **không tồn tại** khi đồng hồ tiến lên hoặc **xuất hiện hai lần** khi đồng hồ lùi. Hành vi cụ thể của trigger cần được kiểm thử với phiên bản Spring, context thực thi và zone đó.
- `Asia/Ho_Chi_Minh` hiện không áp dụng DST, nhưng đặt zone tường minh vẫn giúp ứng dụng không phụ thuộc cấu hình máy.

Snippet Java thuần chuyển giờ địa phương thành instant:

~~~java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime local = LocalDate.of(2026, 3, 13)
        .atTime(9, 0).atZone(zone);
Instant instant = local.toInstant();
~~~

Đây là **đoạn minh họa** cần imports `ZoneId`, `ZonedDateTime`, `LocalDate`, `Instant`. Với mốc trên, instant tương ứng là 02:00 UTC cùng ngày. Không dùng phép trừ 7 giờ thủ công cho mọi nơi: các zone khác có offset theo DST và lịch sử khác nhau.

### 9.1. Ngày nghỉ lễ không tự xuất hiện trong cron

Các biểu thức `LW` và `nW` chỉ biết các thứ Mon–Fri, không biết **Tết, ngày nghỉ bù, ngày đóng cửa giao dịch hoặc lịch tài chính**. Nếu muốn “ngày làm việc cuối tháng theo ngân hàng” thì cần một nguồn business calendar, không mặc định `LW` đã giải quyết.

Một chiến lược là tính *actual business date* từ holiday calendar, lưu lịch nghiệp vụ và chỉ đăng ký trigger tương ứng; hoặc chạy cron trong một cửa sổ thời gian rồi check calendar trước khi thực hiện side effect. Cần bảo đảm idempotency nếu job có thể nhận lệnh chạy lại.

## 10. Cron quyết định ngày kích hoạt, không bảo đảm kết quả nghiệp vụ

Dù biểu thức đúng, một job vẫn có thể bị trễ hoặc không chạy như dự kiến vì:

- Scheduler thread đang bận với callback lâu.
- Ứng dụng restart/shutdown lúc sắp tới trigger.
- Có nhiều replicas cùng có `@Scheduled`, khiến một lịch được kích hoạt **nhiều lần**.
- Callback ném exception, Spring error handler log/suppress hoặc thay đổi chính sách tiếp tục.
- Callback chỉ submit sang `@Async` rồi return; nghiệp vụ con còn nằm trong queue hoặc đã fail.
- Worker bị cạn DB connection, lock, I/O timeout hoặc API downstream lỗi.

Do đó để quản trị lịch theo tháng cần log/metrics cho **scheduled_at**, **started_at**, **finished_at**, **status**, **run_id** và lỗi. Nếu nghiệp vụ quan trọng, cần job store, retry/idempotency và cơ chế tránh trùng trên nhiều instance; cron string không cung cấp exactly-once.

### 10.1. Khi nào dùng Spring local @Scheduled, khi nào cần job scheduler mạnh hơn?

- **Tác vụ phụ trợ nội bộ, có thể chạy lại:** `@Scheduled` với cron rõ nghĩa, monitoring và idempotency thường đủ.
- **Tác vụ báo cáo có SLA nhưng chấp nhận recovery riêng:** có thể dùng `TaskScheduler`, run status và trigger đăng ký lại sau restart.
- **Lịch tài chính quan trọng, cần misfire/recovery và điều phối cụm:** cân nhắc job store bền vững/Quartz/điều phối riêng với rõ ràng về tính trùng và retry.
- **Ngày giao dịch phức tạp:** business calendar hoặc domain scheduler là nguồn quyết định ngày, không chỉ `L/W/#`.

Lựa chọn phụ thuộc yêu cầu độ bền, độ chính xác, mức độ quan trọng của side effect và cách ứng dụng được triển khai.

## 11. Sai lầm thường gặp

**“L luôn là thứ Sáu cuối tháng.”** Sai: `L` ở day-of-month là ngày cuối bất kể thứ; `FRIL` ở day-of-week mới là thứ Sáu cuối.

**“15W nghĩa là thứ Tư gần ngày 15.”** Sai: `W` là weekday Mon–Fri, không phải Wednesday.

**“31W dùng được cho ngày làm việc cuối mọi tháng.”** Sai: các tháng không có ngày 31 không tự chuyển sang cuối tháng; dùng `LW`.

**“FRI#5 nghĩa là thứ Sáu cuối tháng.”** Sai: có nhiều tháng không có thứ Sáu thứ năm; dùng `FRIL`.

**“Spring 6L là thứ Sáu cuối như Quartz.”** Sai: `6=SAT` trong Spring, nên là thứ Bảy cuối.

**“@Scheduled có 5 trường như crontab Unix.”** Sai: Spring yêu cầu sáu trường và có trường giây.

**“Parser chấp nhận expression thì mỗi tháng đều có lần chạy.”** Sai với `FRI#5` và một số ngày-tháng cụ thể.

**“Dùng LW là đã loại ngày lễ.”** Sai: cron chỉ phân biệt thứ Hai–thứ Sáu.

**“Cron không cần zone vì tất cả server đều giống nhau.”** Sai: timezone mặc định của JVM/container có thể khác, nên cron chạy vào instant khác.

**“L/W/# bảo đảm job chạy đúng một lần trong cluster.”** Sai: các ký hiệu chỉ chọn ngày, không điều phối nhiều instance.

**“Đã tới giờ cron tức job chắc chắn thành công.”** Sai: cần tách trigger, bắt đầu thực thi, DB commit và kết quả downstream.

## 12. Checklist kiểm thử trước khi triển khai

1. Tách đủ sáu trường, ghi rõ trường thứ tư và trường thứ sáu.
2. Kiểm tra cách đánh số thứ theo **Spring**, không copy chỉ số Quartz.
3. Test tháng 28/29/30/31 ngày và năm nhuận.
4. Test `1W` khi mùng 1 là thứ Bảy/Chủ nhật.
5. Test `LW` khi cuối tháng rơi cuối tuần.
6. Test `FRI#5` với tháng có và **không có** thứ Sáu thứ năm.
7. Dùng `CronExpression.next(ZonedDateTime)` với seed cố định và đúng `ZoneId`.
8. Test zone/giờ local, đặc biệt ngày DST với zone có đổi giờ.
9. Nếu cần ngày làm việc theo lịch nghỉ lễ, kết hợp business calendar và test ngày nghỉ bù.
10. Xác nhận job được đăng ký đúng một hay nhiều lần trong môi trường nhiều replicas.
11. Giám sát trigger delay, callback duration, execution failure và idempotency.
12. Xác minh chính sách restart/misfire/retry khi lần lịch quan trọng có thể bị lỡ.

Không dùng `ZonedDateTime.now()` làm mốc duy nhất cho test: thời điểm thay đổi sẽ làm test khó tái lập. Dùng các tháng/ngày xác định và assert chính xác những lần `next(...)` được chọn.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Cần chạy vào ngày cuối tháng, kể cả thứ Bảy/Chủ nhật. Chọn `L` hay `LW`?

**Đáp án:** dùng `L` tại day-of-month; `LW` có thể chọn một ngày trước ngày cuối.

**Câu 2.** `1W` tháng 08/2026 chọn ngày nào?

**Đáp án:** thứ Hai **03/08/2026** vì mùng 1 là thứ Bảy và không được quay sang tháng 7.

**Câu 3.** `31W` có tự chạy cuối tháng 02/2026 không?

**Đáp án:** không. Tháng Hai không có ngày 31. Muốn weekday cuối mọi tháng, dùng `LW`.

**Câu 4.** `FRI#2` chọn ngày nào trong tháng 03/2026?

**Đáp án:** thứ Sáu **13/03/2026**, lần thứ hai của thứ Sáu.

**Câu 5.** Vì sao `FRI#5` không phù hợp yêu cầu thứ Sáu cuối mọi tháng?

**Đáp án:** vì nhiều tháng chỉ có bốn thứ Sáu. `FRIL` luôn chỉ thứ Sáu cuối tháng.

**Câu 6.** Trong Spring, `6L` là thứ Sáu hay thứ Bảy cuối tháng?

**Đáp án:** thứ Bảy cuối tháng, vì Spring đánh số `6=SAT`; với Friday dùng `5L`/`FRIL`.

**Câu 7.** Có nên dùng `15W` làm ngày làm việc theo lịch ngân hàng không?

**Đáp án:** chưa đủ; nó chỉ xét Mon–Fri, không biết ngày nghỉ lễ hoặc nghỉ bù.

**Câu 8.** Một cron 09:00 muốn chạy theo giờ Việt Nam ở mọi môi trường thì cấu hình thế nào?

**Đáp án:** dùng `zone = "Asia/Ho_Chi_Minh"` hoặc `CronTrigger` với `ZoneId.of("Asia/Ho_Chi_Minh")`, rồi xác minh những instant thực tế.

**Câu 9.** `CronExpression.isValidExpression("0 0 9 ? * FRI#5")` là true có đảm bảo mỗi tháng có trigger không?

**Đáp án:** không. Biểu thức hợp lệ về cú pháp, nhưng không có lần chạy ở tháng thiếu thứ Sáu thứ năm.

**Câu 10.** `@Scheduled` chạy ở ba application replicas có thể gây ba lượt xử lý không?

**Đáp án:** có, trừ khi triển khai cơ chế điều phối, khóa hoặc xử lý trùng/idempotency phù hợp.

## 14. Tài liệu tham khảo chính thức

- Spring Framework — `CronExpression` và cú pháp `L/W/#`, `parse`, `next`: https://docs.spring.io/spring-framework/docs/6.0.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html
- Spring Framework — “New in Spring 5.3: Improved Cron Expressions”: https://spring.io/blog/2020/11/10/new-in-spring-5-3-improved-cron-expressions/
- Spring Framework — Task Execution and Scheduling, `@Scheduled`, `TaskScheduler` và `CronTrigger`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework — `CronTrigger(String, ZoneId)`: https://www.springframework.org/spring/docs/6.2.18/javadoc-api/org/springframework/scheduling/support/CronTrigger.html
- Java SE 17 — `YearMonth`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/YearMonth.html
- Java SE 17 — `TemporalAdjusters`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/temporal/TemporalAdjusters.html
- Java SE 17 — `ZonedDateTime`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cron-timezones-004">CronExpression để phân tích và tìm lần chạy kế tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# CronExpression để phân tích và tìm lần chạy kế tiếp trong Spring Framework

## 1. Mục tiêu: tính thời điểm kế tiếp mà chưa cần khởi chạy scheduler

Một biểu thức cron có thể mô tả “09:30 từ thứ Hai đến thứ Sáu”, “ngày cuối cùng của tháng lúc 00:00”, hoặc “thứ Sáu thứ hai mỗi tháng lúc 09:00”. Khi vận hành một hệ thống Spring, ta thường cần **xem trước các mốc chạy** trước khi chấp nhận cấu hình, hiển thị lịch trong giao diện quản trị, cảnh báo lịch bất thường, hoặc kiểm thử xử lý múi giờ và DST.

Spring Framework cung cấp class **`org.springframework.scheduling.support.CronExpression`** (từ Spring 5.3). Ba API quan trọng:

- `CronExpression.parse(String)`: phân tích một chuỗi cron, trả một đối tượng có thể tái sử dụng; ném `IllegalArgumentException` nếu format không hợp lệ.
- `CronExpression.isValidExpression(String)`: kiểm tra chuỗi cron có parse được hay không, hữu ích cho validation của input cấu hình.
- `expression.next(Temporal)`: tìm **mốc tiếp theo sau mốc đầu vào** thỏa cron, trả một temporal cùng kiểu (ví dụ `LocalDateTime` hoặc `ZonedDateTime`); có thể trả `null` nếu không tìm được mốc phù hợp.

Ví dụ một dòng:

```java
CronExpression cron = CronExpression.parse("0 30 9 * * MON-FRI");
ZonedDateTime next = cron.next(
        ZonedDateTime.of(2026, 10, 29, 8, 0, 0, 0,
                ZoneId.of("Asia/Ho_Chi_Minh")));
```

Đây là **fragment** cần imports của Spring và `java.time`; chương trình Maven độc lập đầy đủ nằm ở mục 7. Kết quả `next` là **2026-10-29 09:30 ở Việt Nam**, vì ngày 29/10/2026 là thứ Năm và thời gian đầu vào là 08:00.

**Điểm cốt lõi:** `CronExpression` chỉ **diễn giải biểu thức và tính toán mốc**, không tự tạo thread, không đăng ký job, không gọi method nghiệp vụ và không đảm bảo một tác vụ thực tế sẽ được thực thi đúng thời điểm đó. Muốn lên lịch chạy, dùng `@Scheduled`, `TaskScheduler`/`CronTrigger` hoặc scheduler phù hợp.

## 2. Đầu vào của CronExpression: sáu trường và một ngữ cảnh thời gian

`CronExpression.parse(...)` của Spring Framework nhận **sáu trường**, theo thứ tự `second minute hour day-of-month month day-of-week`. Trường giây là 0–59, phút 0–59, giờ 0–23, ngày trong tháng 1–31, tháng 1–12 hoặc tên tháng, thứ 0–7 hoặc `MON`–`SUN`. Spring dùng **0 hoặc 7 là Chủ nhật, 1 là thứ Hai**; không chuyển nguyên mapping numeric từ Quartz.

```text
0 30 9 * * MON-FRI   09:30 ngày thứ Hai–Sáu
0 0 0 L * *         00:00 ngày cuối tháng
0 0 9 ? * FRI#2     09:00 thứ Sáu thứ hai
```

Parser sẽ từ chối cron Linux chỉ năm trường (`30 9 * * MON-FRI`) và Quartz có trường năm thứ bảy. **Chuỗi cron không kèm timezone**: với `next(...)`, truyền `ZonedDateTime` ở `ZoneId` nghiệp vụ để diễn giải đúng các trường lịch. Mốc trả về có thể chuyển sang `Instant` để so sánh giữa các vùng.

## 3. Cú pháp để thử API, không phải tự viết parser

Thay vì dạy lại toàn bộ các toán tử, khi dùng API hãy chuẩn bị cả case hợp lệ và không hợp lệ:

| Input | Ý nghĩa cần kiểm chứng bằng API |
| --- | --- |
| `0 0/30 8-10 * * *` | Bước phút kết hợp phạm vi giờ; next phải qua 08:00, 08:30, 09:00... |
| `0 0 0 L * *` | `L` là cuối tháng kể cả tháng Hai nhuận. |
| `0 0 9 ? * FRI#2` | `?` ở trường ngày-tháng, `#` chọn thứ Sáu thứ hai. |
| `@daily` | Macro được Spring mở rộng thành `0 0 0 * * *`. |
| `30 9 * * MON-FRI` | Không hợp lệ do chỉ có năm trường. |
| `0 0 25 * * *` | Không hợp lệ do giờ 25 ngoài miền 0–23. |

`*` chọn mọi giá trị của trường; `?` chỉ thay `*` ở day-of-month/day-of-week; `-` chọn khoảng, `,` chọn danh sách và `/` chọn bước **trong cùng một trường**, không phải độ trễ kể từ khi callback kết thúc. `L/W/#` chọn ngày theo quy tắc calendar. Trong học phần này, trọng tâm không phải viết lại regex cron mà là **dùng `parse`/`isValidExpression` để xác thực và `next` để chứng minh mốc cụ thể**; một expression parse thành công vẫn có thể sai yêu cầu business hoặc không tìm được mốc tiếp theo.

## 4. API parse(String): phân tích, lỗi và tái sử dụng

Cú pháp sử dụng:

```java
import org.springframework.scheduling.support.CronExpression;

CronExpression cron = CronExpression.parse("0 30 9 * * MON-FRI");
```

`parse(...)` ném `IllegalArgumentException` nếu chuỗi cron sai định dạng. Đừng hiển thị raw stack trace cho người dùng cuối; hãy trả một lỗi validation rõ ràng và lưu nguyên nhân trong log server nếu cần.

Ví dụ fragment xử lý input quản trị:

```java
public CronExpression parseOrReject(String input) {
    try {
        return CronExpression.parse(input);
    } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException(
                "Cron không hợp lệ: cần sáu trường giây-phút-giờ-ngày-tháng-thứ",
                ex);
    }
}
```

Đây là method fragment, cần import `CronExpression` và một lớp Java chứa method. Với input được phép tùy chỉnh, kiểm soát thêm độ dài, số lần preview, quyền cấu hình và các constraints nghiệp vụ; **parse thành công chưa bảo đảm lịch an toàn**.

Đối tượng `CronExpression` biểu diễn một expression đã parse, dùng lại để tìm nhiều mốc; không cần parse lại cùng chuỗi trong mỗi vòng lặp.

### 4.1. isValidExpression(String) chỉ là bước đầu

```java
boolean ok = CronExpression.isValidExpression("0 30 9 * * MON-FRI");
// true

boolean missingSecond = CronExpression.isValidExpression("30 9 * * MON-FRI");
// false

boolean badHour = CronExpression.isValidExpression("0 0 25 * * *");
// false
```

API `isValidExpression` phù hợp để kiểm tra syntactic validity. Nhưng:

1. **Valid không đồng nghĩa lịch có ích**: `* * * * * *` được chấp nhận và chạy mỗi giây, có thể quá dày cho workload.
2. **Valid không đồng nghĩa job được đăng ký**: chỉ tạo đối tượng cron chưa tạo một scheduled task.
3. **Valid không đồng nghĩa có mốc tiếp theo trong mọi hoàn cảnh**: `next(...)` có thể trả `null`.
4. **Valid không đồng nghĩa múi giờ đúng**: timezone được quyết định bởi temporal khi preview hoặc bởi `@Scheduled.zone`/`CronTrigger` khi chạy thực tế.

Với API nhận cron từ người dùng, nên kết hợp cú pháp, ngữ nghĩa nghiệp vụ và xem trước vài mốc chạy trong **đúng timezone**.

## 5. next(Temporal): tìm mốc tương lai từ một seed

Chữ ký Spring 6.2.1:

```java
@Nullable
public <T extends Temporal & Comparable<? super T>> T next(T temporal)
```

Mục đích: cho một temporal đại diện **mốc tham chiếu**, tìm mốc **tiếp theo sau tham chiếu** thỏa expression. Các kiểu thường dùng:

- `LocalDateTime`: một ngày-giờ địa phương nhưng **không có timezone/UTC offset**. Dễ kiểm tra lịch đơn giản, nhưng không đủ để giải quyết DST khi lập lịch thật.
- `ZonedDateTime`: gồm ngày giờ, ZoneId và offset hiện hành; phù hợp preview lịch có timezone và DST, nhất là khi so sánh các mốc thực tế.
- Có thể dùng các kiểu `Temporal` khác thỏa ràng buộc generic **nếu** chúng cung cấp đủ các trường ngày/giờ mà cron cần. Đừng cho rằng một `Instant` chỉ có epoch timestamp sẽ thay thế `ZonedDateTime` trong mọi tình huống.

Ví dụ fragment:

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime seed = ZonedDateTime.of(
        2026, 10, 29, 8, 0, 0, 0, zone);
CronExpression cron = CronExpression.parse("0 30 9 * * MON-FRI");
ZonedDateTime next = cron.next(seed);
```

Kết quả trên Spring 6.2.1: `2026-10-29T09:30+07:00[Asia/Ho_Chi_Minh]`.

### 5.1. next không nhất thiết trả đúng seed

Nếu seed **đã là một mốc hợp lệ**, `next(seed)` tìm mốc *kế tiếp*, không chỉ trả lại seed như một phép “isMatch”. Muốn một danh sách mốc sắp tới, bắt đầu từ seed cố định rồi liên tục gọi `next` với **kết quả trước đó**.

### 5.2. Luôn kiểm tra null

Javadoc cho phép `next(...)` trả `null` nếu không thể tìm được temporal phù hợp. Vì vậy:

```java
ZonedDateTime candidate = expression.next(seed);
if (candidate == null) {
    // Không tìm được mốc tiếp theo trong điều kiện/khả năng của parser.
    // Trả lỗi validation hoặc trạng thái "no next occurrence".
}
```

Đây là fragment; `expression` và `seed` phải đã được khai báo. Không viết `expression.next(seed).toInstant()` nếu chưa xét khả năng null. Đối với một UI xem trước lịch, nên hiển thị trạng thái không có mốc tiếp theo thay vì tự tạo giá trị giả hoặc vòng lặp vô hạn.

### 5.3. Tính các lần chạy kế tiếp có giới hạn

```java
public List<ZonedDateTime> preview(
        String cronText, ZoneId zone,
        ZonedDateTime from, int limit) {

    if (limit < 1 || limit > 20) {
        throw new IllegalArgumentException("limit phải trong 1..20");
    }

    CronExpression expression = CronExpression.parse(cronText);
    ZonedDateTime cursor = from.withZoneSameInstant(zone);
    List<ZonedDateTime> result = new ArrayList<>();

    for (int i = 0; i < limit; i++) {
        ZonedDateTime next = expression.next(cursor);
        if (next == null) {
            break;
        }
        result.add(next);
        cursor = next;
    }
    return result;
}
```

Đây là **fragment method** cần `List`, `ArrayList`, `ZoneId`, `ZonedDateTime` và `CronExpression` imports. Ưu tiên `withZoneSameInstant(zone)` khi muốn chuyển **cùng một thời điểm thực tế** sang múi giờ khác; `withZoneSameLocal(...)` sẽ cố giữ nguyên giờ trên đồng hồ địa phương, tức thay đổi instant.

Cần có **limit** để tránh xử lý vô hạn khi preview cấu hình từ input người dùng. Nếu vòng lặp gặp `null`, nên dừng và thông báo rõ ràng.

## 6. Múi giờ không nằm trong chuỗi cron

Chuỗi `0 0 9 * * MON-FRI` chỉ mô tả **09:00 địa phương**, không tự quyết định đó là 09:00 ở Việt Nam hay Berlin. Vì vậy, cùng một cron có thể tương ứng với những mốc UTC khác nhau:

- 09:00 ở `Asia/Ho_Chi_Minh` thường tương ứng 02:00 UTC (UTC+07:00).
- 09:00 ở `Europe/Berlin` có thể là 08:00 UTC vào mùa đông (UTC+01:00) hoặc 07:00 UTC vào mùa hè (UTC+02:00).

**Cách preview an toàn:** truyền `ZonedDateTime` với ZoneId rõ ràng vào `next(...)`. Đừng dùng `LocalDateTime.now()` không gắn múi giờ rồi cố coi kết quả là UTC hoặc giờ sản phẩm.

Ví dụ:

```java
ZoneId zone = ZoneId.of("Europe/Berlin");
ZonedDateTime berlinStart =
        ZonedDateTime.of(2026, 3, 28, 23, 0, 0, 0, zone);
CronExpression expression = CronExpression.parse("0 30 2 * * *");
ZonedDateTime candidate = expression.next(berlinStart);
```

Ngày 29/03/2026 Berlin chuyển sang giờ mùa hè, thời điểm **02:30 địa phương không tồn tại**. Kết quả thực nghiệm với Spring 6.2.1: `candidate` là **2026-03-30T02:30+02:00[Europe/Berlin]**, bỏ qua 02:30 vào ngày chuyển giờ.

### 6.1. DST có thể tạo giờ bị mất

Khi đồng hồ địa phương nhảy từ 01:59:59 lên 03:00:00, một cron tại 02:30 **không có mốc 02:30 để khớp** hôm đó. Không thể tuyên bố “cron hằng ngày chắc chắn chạy đúng một lần mỗi 24 giờ” trong múi giờ có DST; các ngày có thể dài 23 hoặc 25 giờ.

### 6.2. DST có thể tạo một giờ bị lặp lại

Khi Berlin trở về giờ mùa đông vào **25/10/2026**, đồng hồ đi qua 02:30 hai lần với offset khác nhau:

```text
2026-10-25T02:30+02:00[Europe/Berlin]   (lần thứ nhất)
2026-10-25T02:30+01:00[Europe/Berlin]   (lần thứ hai)
```

Thử `CronExpression.parse("0 30 2 * * *")` và gọi `next` liên tiếp với `ZonedDateTime` qua mốc đó cho kết quả **hai instant khác nhau**, dù đồng hồ địa phương cùng hiển thị 02:30.

Điều này không có nghĩa mọi ứng dụng thực tế chắc chắn chạy job hai lần: scheduler thật còn phụ thuộc runtime, trigger context, misfire/skip policy, shutdown, contention và loại trigger. **CronExpression preview** cho biết những mốc cron có thể khớp, không phải audit log đã thực thi.

### 6.3. LocalDateTime so với ZonedDateTime

`LocalDateTime` không có ZoneId. Nó không thể diễn tả hai lần 02:30 tại DST fallback là hai instant khác nhau. Nếu business requirement phụ thuộc timezone, dùng `ZonedDateTime` hoặc chuyển kết quả sang `Instant` sau khi đã tính lịch với ZoneId chính xác.

Không dùng một `Instant` đơn thuần thay cho timezone business, vì một instant là thời điểm tuyệt đối nhưng biểu thức cron cần đánh giá các **trường lịch địa phương**.

## 7. Dự án Maven Java 21 + Spring Framework 6.2.1 chạy được

Ví dụ sau là chương trình độc lập, không cần Spring Boot. Nó kiểm chứng:

1. Ba mốc kế tiếp của cron ngày làm việc ở `Asia/Ho_Chi_Minh`.
2. Ngày cuối tháng 2 và thứ Sáu thứ hai trong tháng 10/2026.
3. `isValidExpression` phân biệt biểu thức sáu trường với năm trường và giờ ngoài phạm vi.
4. Macro `@daily`.
5. `parse` ném `IllegalArgumentException` cho cron thiếu trường.
6. Một giờ **bị mất** lúc DST spring-forward và **giờ bị lặp** lúc DST fall-back ở Berlin.

### 7.1. Cấu trúc

```text
cron-expression-demo/
├── pom.xml
└── src/main/java/demo/CronExpressionDemo.java
```

### 7.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>cron-expression-demo</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>6.2.1</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.0</version>
        <configuration>
          <mainClass>demo.CronExpressionDemo</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 7.3. Tệp src/main/java/demo/CronExpressionDemo.java

```java
package demo;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

public class CronExpressionDemo {
    private static ZonedDateTime at(String zone, int year, int month, int day,
                                   int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(zone));
    }

    public static void main(String[] args) {
        CronExpression weekdays = CronExpression.parse("0 30 9 * * MON-FRI");
        ZonedDateTime when = at("Asia/Ho_Chi_Minh", 2026, 10, 29, 8, 0);
        for (int i = 1; i <= 3; i++) {
            when = weekdays.next(when);
            System.out.println("weekday" + i + "=" + when);
        }

        CronExpression monthEnd = CronExpression.parse("0 0 0 L * *");
        System.out.println("monthEnd=" +
                monthEnd.next(at("Asia/Ho_Chi_Minh", 2026, 2, 27, 23, 59)));

        CronExpression secondFriday = CronExpression.parse("0 0 9 ? * FRI#2");
        System.out.println("secondFriday=" +
                secondFriday.next(at("Asia/Ho_Chi_Minh", 2026, 10, 1, 0, 0)));

        System.out.println("validSixFields=" +
                CronExpression.isValidExpression("0 30 9 * * MON-FRI"));
        System.out.println("validFiveFields=" +
                CronExpression.isValidExpression("30 9 * * MON-FRI"));
        System.out.println("validHour25=" +
                CronExpression.isValidExpression("0 0 25 * * *"));
        System.out.println("dailyMacro=" +
                CronExpression.parse("@daily").next(
                    at("Asia/Ho_Chi_Minh", 2026, 10, 29, 8, 0)));

        try {
            CronExpression.parse("30 9 * * MON-FRI");
            throw new AssertionError("Invalid cron must throw");
        } catch (IllegalArgumentException expected) {
            System.out.println("parseRejectsFiveFields=true");
        }

        CronExpression twoThirty = CronExpression.parse("0 30 2 * * *");
        ZonedDateTime gap = twoThirty.next(at("Europe/Berlin", 2026, 3, 28, 23, 0));
        System.out.println("berlinSpringGap=" + gap);
        ZonedDateTime first = twoThirty.next(at("Europe/Berlin", 2026, 10, 25, 1, 59));
        System.out.println("berlinFallFirst=" + first);
        System.out.println("berlinFallNext=" + twoThirty.next(first));
    }
}
```

### 7.4. Cách chạy

Yêu cầu Maven và JDK 21 trở lên. Trong thư mục có `pom.xml`:

```bash
mvn -q -DskipTests compile exec:java
```

**Stdout đã biên dịch và chạy thực tế với Spring Framework 6.2.1:**

```text
weekday1=2026-10-29T09:30+07:00[Asia/Ho_Chi_Minh]
weekday2=2026-10-30T09:30+07:00[Asia/Ho_Chi_Minh]
weekday3=2026-11-02T09:30+07:00[Asia/Ho_Chi_Minh]
monthEnd=2026-02-28T00:00+07:00[Asia/Ho_Chi_Minh]
secondFriday=2026-10-09T09:00+07:00[Asia/Ho_Chi_Minh]
validSixFields=true
validFiveFields=false
validHour25=false
dailyMacro=2026-10-30T00:00+07:00[Asia/Ho_Chi_Minh]
parseRejectsFiveFields=true
berlinSpringGap=2026-03-30T02:30+02:00[Europe/Berlin]
berlinFallFirst=2026-10-25T02:30+02:00[Europe/Berlin]
berlinFallNext=2026-10-25T02:30+01:00[Europe/Berlin]
```

Đây là **output từ chương trình đã chạy**, không phải các mốc tính bằng tay. Thư viện timezone của JDK có thể được cập nhật giữa các phiên bản JDK; khi làm việc với lịch tương lai xa, cần biết timezone database (tzdb) của runtime đang sử dụng.

### 7.5. Giải thích phép kiểm tra Việt Nam

Seed `2026-10-29 08:00 Asia/Ho_Chi_Minh` là sáng thứ Năm. Cron `0 30 9 * * MON-FRI` khớp 09:30 thứ Năm, 09:30 thứ Sáu, sau đó bỏ qua thứ Bảy và Chủ nhật để đến 09:30 thứ Hai 02/11/2026.

Cron `0 0 0 L * *` tìm ngày cuối tháng: từ đêm 27/02/2026, mốc tiếp là **00:00 ngày 28/02/2026**. Còn cron `0 0 9 ? * FRI#2` tìm thứ Sáu thứ hai của tháng 10, tức **09/10/2026**.

`CronExpression.isValidExpression` phát hiện lỗi số trường và giờ 25 (ngoài 0..23), còn `parse` ném exception cho cron năm trường. Cách kiểm thử dùng seed cố định giúp output tái lập, không phụ thuộc thời điểm chạy lệnh hoặc timezone mặc định của máy.

### 7.6. Giải thích phép kiểm tra Berlin

Vào ngày 29/03/2026, Berlin không tồn tại **02:30** vì chuyển sang DST. Kết quả cron 02:30 hằng ngày tính từ tối 28/03 là **30/03/2026 02:30+02:00**.

Vào ngày 25/10/2026, đồng hồ Berlin lặp lại khoảng 02:00–02:59 với hai offsets; gọi `next` liên tiếp cho **02:30+02:00** rồi **02:30+01:00**. Hai chuỗi có phần giờ-phút giống nhau nhưng chỉ hai instant khác nhau. Điều này giải thích vì sao một UI xem trước lịch nên hiển thị cả **ZoneId/UTC offset**, không chỉ “02:30”.

## 8. Tích hợp CronExpression với @Scheduled

Sử dụng `CronExpression` để **kiểm tra và xem trước**. Muốn chạy method định kỳ, Spring hỗ trợ annotation:

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Cấu hình TaskScheduler bean khi cần điều khiển pool và thread name.
}

@Component
public class ReportScheduler {
    @Scheduled(cron = "0 30 9 * * MON-FRI",
               zone = "Asia/Ho_Chi_Minh")
    public void report() {
        generateReport();
    }
}
```

Đây là **fragment tích hợp**, cần imports thích hợp (`@Configuration`, `@EnableScheduling`, `@Component`, `@Scheduled`) và method `generateReport()` thực tế.

Ở đây:

- `cron` xác định giờ/ngày theo biểu thức Spring.
- `zone` xác định múi giờ dùng để resolve lịch, **không được mã hóa trong biểu thức cron**.
- `@EnableScheduling` kích hoạt infrastructure đăng ký các annotated methods.
- `TaskScheduler`/scheduler implementation chịu trách nhiệm chạy công việc khi tới hạn.

Không cần trực tiếp gọi `CronExpression.parse(...)` trong method `report()`; Spring xử lý schedule khi đăng ký annotation. Việc dùng `CronExpression` ở controller quản trị/validation là một **use case độc lập**, bổ trợ cho scheduling thật.

### 8.1. Placeholder từ cấu hình

```java
@Scheduled(cron = "${reports.cron}",
           zone = "${reports.zone}")
public void export() {
    exportReports();
}
```

Với:

```properties
reports.cron=0 30 9 * * MON-FRI
reports.zone=Asia/Ho_Chi_Minh
```

Đây là fragments; ứng dụng phải có property resolution phù hợp, bean chứa method và `@EnableScheduling`. Có thể dùng `CronExpression.isValidExpression(...)` khi tiếp nhận cấu hình **trước khi khởi động lại/đăng ký schedule** nếu ứng dụng cho người dùng thay đổi lịch.

**Cần lưu ý:** thay giá trị property trong database khi application đang chạy không mặc nhiên khiến `@Scheduled` tự đăng ký lại lịch; các thay đổi dynamic phải được thiết kế qua scheduler API/registration và lifecycle phù hợp.

### 8.2. Giá trị “-” để tắt @Scheduled cron

Spring có sentinel `Scheduled.CRON_DISABLED`, giá trị `"-"`, có thể sử dụng trong cấu hình annotation cron để vô hiệu hóa trigger (ví dụ property placeholder có default `-`). **Không đồng nhất** giá trị này với một cron expression hợp lệ cho `CronExpression.parse("-")`; nó là quy ước dành cho cơ chế `@Scheduled`.

Ví dụ:

```java
@Scheduled(cron = "${reports.cron:-}")
public void export() {
    exportReports();
}
```

Đây là fragment của annotation; khi property bị thiếu và fallback là `-`, schedule được disabled thay vì tìm next execution. Khi thiết kế form cấu hình, nên phân biệt **Enabled/Disabled** với trường cron hợp lệ thay vì cố cho parser nhận `-`.

## 9. Tích hợp TaskScheduler và CronTrigger khi muốn lập lịch bằng code

Đối với cấu hình runtime hoặc các job cần thay đổi lịch mà không hardcode annotation, Spring cung cấp `TaskScheduler` và `CronTrigger`:

```java
TaskScheduler scheduler = /* bean injected */;
Runnable task = () -> processReport();

CronTrigger trigger = new CronTrigger(
        "0 30 9 * * MON-FRI", ZoneId.of("Asia/Ho_Chi_Minh"));

ScheduledFuture<?> registration = scheduler.schedule(task, trigger);
```

Đây là **fragment**; cần imports `TaskScheduler`, `CronTrigger`, `ZoneId`, `ScheduledFuture` và hàm `processReport()`. Nó khác với việc gọi `CronExpression.next(...)`:

- `CronExpression.next(...)` chỉ trả một timestamp dự kiến.
- `TaskScheduler.schedule(task, trigger)` thực sự **đăng ký một Runnable** để scheduler chạy.
- `CronTrigger` dùng expression và trigger context — gồm last completion / execution / scheduler clock — để quyết định lần kế tiếp.
- Registration trả `ScheduledFuture<?>` để có thể quản lý việc hủy theo semantics của scheduler.

Trong Spring Framework 6.2, `CronTrigger` mặc định có cách tính dựa vào **thời điểm hoàn thành lượt trước** ở những trường hợp phù hợp, nên các lần cron bị lỡ vì job dài có thể không được chạy bù theo đúng thứ tự preview đơn giản.

**Phân biệt quan trọng:** nếu bạn liên tục gọi `CronExpression.next(previousMatch)`, bạn liệt kê các mốc lý thuyết. Một `CronTrigger` đang chạy thật có thể tính theo completion context và bỏ qua các mốc đã lỡ; không lấy danh sách preview làm bằng chứng “job chắc chắn chạy N lần”.

### 9.1. Khi nào không nên dùng CronExpression để schedule thủ công?

Một anti-pattern là vòng lặp lấy `next` rồi `Thread.sleep(Duration.between(now,next))`, tự tạo thread và tự retry. Cách này phải xử lý clock change, shutdown, DST, exceptions, cancellation và race điều phối. Spring đã có `TaskScheduler` và `CronTrigger` cho việc quản lý lifecycle. Hãy dùng `CronExpression` cho parsing/preview; giao **việc thực thi** cho scheduler thích hợp.

## 10. UI xem trước lịch: đầu vào, đầu ra và validation

Một API preview lịch được thiết kế tốt nên nhận:

- **Expression**: chuỗi cron hoặc macro của Spring, ví dụ `0 30 9 * * MON-FRI`.
- **ZoneId**: múi giờ IANA rõ ràng, ví dụ `Asia/Ho_Chi_Minh`, `Europe/Berlin`.
- **Start time**: mốc tham chiếu là instant có offset hoặc biểu diễn thời gian xác định rõ zone.
- **Limit**: số mốc trả về có giới hạn hợp lý, ví dụ 5 hoặc 10.

Dữ liệu trả về nên bao gồm cho mỗi occurrence:

| Trường | Vì sao cần |
| --- | --- |
| `localDateTime` | Hiển thị giờ theo người dùng |
| `zoneId` | Cho biết lịch theo múi giờ nào |
| `offset` | Phân biệt hai lần trùng 02:30 khi DST fall-back |
| `instant/UTC` | Cho phép so sánh và ghi lịch sử các mốc tuyệt đối |
| `sequence` | Đánh thứ tự dự kiến, không nhầm với execution history |

**Validation đề xuất:** xác minh cron parse được, ZoneId tồn tại, giới hạn preview nhỏ, xác nhận expression có đủ chính sách nghiệp vụ (không quá dày, không rơi vào các giờ nhạy cảm nếu business không cho phép), xử lý `null` từ `next` và tránh tự chọn default timezone của server mà người dùng không biết.

### 10.1. Đừng trả về số timestamp không giới hạn

Nếu một endpoint cho người dùng nhập expression rồi đòi liệt kê “mọi lần chạy trong 10 năm”, cron mỗi giây có thể tạo hàng trăm triệu occurrences. Cần **limit** và/hoặc range bounding, deadline xử lý và phân trang. Đặc biệt tránh biến validation của input không tin cậy thành một vòng lặp CPU dài.

### 10.2. Không sử dụng timezone abbreviation mơ hồ

Các chuỗi `CST`, `EST` hoặc tên rút gọn vùng có thể mơ hồ tùy hệ thống. Dùng IANA ZoneId chuẩn như `America/New_York`, `Europe/Berlin`, `Asia/Ho_Chi_Minh`. Nếu một ứng dụng muốn “cùng giờ địa phương dù quốc gia đổi DST”, ZoneId theo vùng sẽ phù hợp hơn việc cố định offset +01:00 hoặc +07:00 một cách tùy tiện.

### 10.3. Múi giờ của JVM có thể khác timezone sản phẩm

`ZoneId.systemDefault()` phản ánh môi trường tiến trình chạy. Docker/CI có thể chạy UTC, trong khi người dùng cần giờ Việt Nam. Nếu preview không nhận zone tường minh, cùng cron có thể ra mốc “khác ngày” khi chuyển môi trường. Nên truyền zone từ hợp đồng API/cấu hình business và kiểm thử độc lập với timezone mặc định.

## 11. Cron và những ngộ nhận về tần suất

Một cron kiểu `0 */5 * * * *` là các mốc phút 0,5,10,... theo lịch của mỗi giờ. Nó không có nghĩa “cứ sau đúng 5 phút kể từ lần job chạy xong”. Khi một job chạy 8 phút:

- Danh sách lý thuyết của `CronExpression.next` vẫn có các mốc theo cron.
- `CronTrigger` đang thực thi có thể bỏ qua hoặc chậm các firing tùy policy.
- Scheduler pool chỉ có một thread có thể trì hoãn job khác.
- Nhiều instances có thể mỗi instance kích hoạt job riêng tại cùng một mốc.

Nếu nghiệp vụ yêu cầu tối đa một job đang chạy, phải kiểm soát concurrency/job state; `CronExpression` **không cung cấp lock**. Nếu yêu cầu đảm bảo không mất lần chạy qua restart, cần durable scheduler/job store hoặc reconciliation mechanism.

### 11.1. fixedRate/fixedDelay không phải cron

- `fixedDelay` đo thời gian đợi **sau khi lần trước hoàn tất**.
- `fixedRate` mong muốn nhịp theo thời gian cố định từ start của periodic registration.
- `cron` khớp các trường lịch năm-tháng-ngày-giờ-phút-giây trong timezone.

Ba cơ chế trả lời các yêu cầu khác nhau. Đừng dùng `CronExpression.next` để mô phỏng chính xác runtime semantics của `fixedDelay`; nó không biết lúc job thực tế kết thúc.

### 11.2. Cron không đảm bảo đúng một lượt trên mỗi ngày lịch

Với DST, một giờ có thể không tồn tại hoặc xuất hiện hai lần. Với job dài, scheduler có thể trễ hoặc bỏ một số firing. Với ứng dụng nhiều instance, cùng giờ có thể xảy ra nhiều executions độc lập. Nếu yêu cầu “mỗi ngày nghiệp vụ đúng một lần”, cần định nghĩa **ngày nghiệp vụ**, idempotency key và trạng thái đã xử lý (theo ZoneId phù hợp).

## 12. Kiểm tra lỗi trước khi đưa cấu hình vào production

| Input / tình huống | Lỗi hiểu phổ biến | Cách xử lý |
| --- | --- | --- |
| `30 9 * * MON-FRI` | Đó là cron 5 trường của UNIX, Spring cần 6 | Thêm giây đầu: `0 30 9 * * MON-FRI` |
| `0 0 25 * * *` | Giờ 25 ngoài 0..23 | Validation bằng `isValidExpression` |
| `0 0 9 * * 1` | Hiểu 1 là Chủ nhật theo Quartz | Spring hiểu 1 là thứ Hai |
| `0 0 9 * * MON-FRI` | Cho rằng cron tự có timezone | Truyền `ZonedDateTime` hoặc `@Scheduled.zone` |
| `0 30 2 * * *` tại Berlin DST | Cho rằng 02:30 luôn tồn tại mỗi ngày | Xem trước ZonedDateTime qua chuyển giờ |
| `CronExpression.parse(...)` rồi không thấy job chạy | Chỉ parsing, chưa đăng ký Runnable | Dùng `@Scheduled` hoặc `TaskScheduler/CronTrigger` |
| `next(seed)` trả null | Dereference và ném NPE | Kiểm tra null, thông báo rõ cho UI |
| Gọi next từ LocalDateTime | Mất thông tin offset/DST | Dùng ZonedDateTime cho lịch có zone |
| Sử dụng Instant trực tiếp như thời gian địa phương | Thiếu các field calendar theo zone | Chuyển seed Instant sang ZonedDateTime |
| Dùng `@daily` và cho rằng 24h cố định | Ngày DST có thể 23/25h | Dùng lịch theo ZoneId đúng mục đích |
| Dùng `CronExpression.next` để mô tả missed firings thật | Không biết last completion của runtime | Dùng CronTrigger/observation/job history |
| Dùng `@Scheduled(cron="-")` rồi cố parse `"-"` | Sentinel disable khác cron parser | Xử lý disabled state riêng |
| Cho phép cron mỗi giây cho tác vụ tốn vài phút | Backlog/overlap/saturation | Policy tần suất tối thiểu và giới hạn concurrency |
| Thay property cron và nghĩ schedule cập nhật tức thời | Registration có thể đã cố định | Thiết kế re-registration/dynamic scheduling |
| Nhiều pods cùng bật cron và nghĩ chỉ chạy một lần | Từng instance có lịch riêng | Distributed lock/leader election/job store |
| Hiển thị giờ 02:30 không kèm offset ở DST fallback | Hai instants trông như một | Hiển thị ZoneId và offset |
| Giữ cron format của Quartz hoặc UNIX mà không kiểm tra | Sai số trường hoặc thứ trong tuần | Validation và test theo Spring parser thật |

## 13. Kiểm thử có độ tin cậy cao

Một bài test tốt về cron **không cần chờ đến giờ thực** hoặc dùng `Thread.sleep(...)` hàng phút. Chỉ cần fixed seed, timezone rõ và đối chiếu `CronExpression.next(...)` với mốc kỳ vọng.

Các nhóm test nên có:

1. **Field syntax**: sáu trường, ranges, steps, names, invalid values.
2. **Boundary**: cuối tháng 2, năm nhuận, ngày cuối năm, tháng không đủ 31 ngày.
3. **Ngày trong tuần**: MON-FRI, last Friday, second Friday.
4. **Múi giờ**: Asia/Ho_Chi_Minh (không DST hiện tại) và vùng có DST.
5. **DST gap**: giờ không tồn tại được xử lý ra sao.
6. **DST overlap**: offset nào cho lần thứ nhất/thứ hai.
7. **Seed đúng mốc**: next là mốc sau seed, không lặp seed vô hạn.
8. **Parser errors**: cron 5 trường, hour 25, invalid zone.
9. **Business policy**: không cho phép lịch quá dày, tính đúng ngày nghiệp vụ.
10. **Runtime integration**: kiểm tra `TaskScheduler/CronTrigger` thực thi khi cần; bài unit test `CronExpression` không thể chứng minh scheduler lifecycle.

Thời gian test nên được cố định: **không gọi `ZonedDateTime.now()` khi viết expected stdout**. Các ví dụ ở đây dùng ngày cụ thể trong năm 2026 để kết quả tái lập.

## 14. Thời gian chờ trong hàng đợi, misfire và khác biệt với preview

`CronExpression.next(seed)` có thể nói mốc tiếp theo của biểu thức là 09:30. Nhưng một job thực tế có thể **bắt đầu 09:33** vì:

- Scheduler pool đang bận vì job khác chạy lâu.
- JVM dừng tạm do GC, máy bị áp lực CPU hoặc process bị pause.
- Ứng dụng bị restart đúng giờ đến hạn.
- Trigger dùng last completion context và bỏ các firing đã lỡ.
- Hệ thống dùng nhiều nodes, leader election hoặc distributed coordination.

Khi quan sát scheduling, tách:

- **Expected cron occurrence**: mốc từ cron ở timezone business.
- **Actual execution start**: lúc worker thực thi.
- **Completion**: lúc phần công việc kết thúc.
- **Misfire/skip**: mốc không được thực thi theo policy.
- **Outcome**: success/failure/cancelled.

Không báo “CronExpression tính sai” chỉ vì job chạy muộn khi poolSize=1 và có task chặn thread; lỗi nằm ở resource scheduling chứ không phải parser.

## 15. Thiết kế API và hiệu năng khi cron do người dùng nhập

Khi cho phép quản trị viên nhập biểu thức cron:

- Có giới hạn độ dài đầu vào và số lượng schedules mỗi tenant.
- Parse đúng phiên bản Spring chạy thật để tránh chênh lệch cú pháp.
- Ràng buộc tần suất: một expression mỗi giây không phù hợp mọi workload.
- Xem trước 5–10 mốc tiếp theo theo ZoneId đã chọn, kèm offset.
- Chặn hoặc cảnh báo biểu thức rơi vào giờ business không mong muốn.
- Định nghĩa concurrency, retry và quota khi nhiều lịch đến hạn cùng lúc.
- Có cơ chế rollback phiên bản cấu hình và audit ai thay đổi lịch.
- Không coi chuỗi cron là cách đảm bảo durable/job history; lưu trạng thái xử lý riêng.
- Bảo đảm input không tin cậy không có khả năng tạo preview vô hạn hoặc thay đổi mọi cron của các tenant khác.

Với lịch hiếm, có thể tốn công tìm mốc kế tiếp; nếu `next` trả null, hãy trả một kết quả rõ cho người dùng. Không giả định luôn có ngày phù hợp chỉ vì expression parse được.

### 15.1. Chọn ZonedDateTime hay Instant cho dữ liệu lưu trữ?

**Lưu mốc chạy tuyệt đối** bằng `Instant` hoặc timestamp UTC để audit/compare và tránh nhầm offset. **Lưu lịch định kỳ** bằng *cron expression + ZoneId + policy*; không chỉ lưu một mốc UTC rồi cộng 24 giờ cho lịch “09:30 mỗi ngày” ở vùng có DST.

Khi hiển thị UI, chuyển Instant sang ZoneId của lịch/người dùng, đồng thời giữ offset khi có DST ambiguity.

### 15.2. Preview không phải reservation

Danh sách 10 mốc do `CronExpression.next` tính ra không tự “đặt chỗ” trên scheduler và không cam kết 10 lượt sẽ diễn ra. Nếu yêu cầu bảo đảm, cần cơ chế nhận việc, durable persistence và monitoring/alerting, không chỉ hiển thị một bảng preview.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `CronExpression.parse("30 9 * * MON-FRI")` có hợp lệ không?

Không. Biểu thức này chỉ có năm trường; Spring yêu cầu sáu trường bắt đầu từ giây. Cần `0 30 9 * * MON-FRI`.

**Câu 2.** Gọi `CronExpression.next(...)` có khiến job chạy không?

Không. Nó chỉ tính timestamp của mốc tiếp theo. Để chạy job, cần `@Scheduled` hoặc `TaskScheduler.schedule(...)` với `CronTrigger`.

**Câu 3.** Vì sao `0 0 9 * * 1` có thể bị hiểu sai khi chuyển từ Quartz?

Spring dùng 1 = thứ Hai; Quartz có quy tắc đánh số ngày khác. Nên dùng `MON` trong chuỗi để giảm nhầm.

**Câu 4.** Từ 2026-10-29 08:00 Asia/Ho_Chi_Minh, ba mốc của `0 30 9 * * MON-FRI` là gì?

09:30 ngày 29/10, 09:30 ngày 30/10 và 09:30 ngày 02/11/2026, đều ở Asia/Ho_Chi_Minh (+07:00).

**Câu 5.** Vì sao 02:30 Berlin ngày 29/03/2026 không xuất hiện trong preview?

Vì DST spring-forward làm khoảng 02:00–02:59 không tồn tại vào ngày ấy. Với cron 02:30 hằng ngày, mốc kế tiếp sau tối 28/03 là 30/03/2026 lúc 02:30+02:00.

**Câu 6.** Tại sao 25/10/2026 ở Berlin có hai mốc “02:30”?

DST fall-back lặp lại giờ 02:00–02:59, lần đầu offset +02:00 và lần thứ hai +01:00. Cần ZoneId/offset hoặc Instant để phân biệt.

**Câu 7.** Sau khi ứng dụng shutdown 10 phút, có thể dùng cách lặp `CronExpression.next` để khẳng định mọi missed firing đã được chạy bù không?

Không. Danh sách các mốc lý thuyết không phải execution history; policy thực tế thuộc CronTrigger/scheduler/job store.

**Câu 8.** Tại sao parser có thể trả `null` từ `next` dù chuỗi hợp lệ?

Vì parsing xác nhận cấu trúc/giới hạn của biểu thức, không hứa tìm được mốc tiếp theo cho mọi seed và mọi ràng buộc lịch. Phải kiểm tra null trước khi dùng kết quả.

## 17. Checklist trước khi đưa CronExpression vào production

- Đã sử dụng cú pháp Spring **sáu trường**, không sao chép nguyên cron UNIX hoặc Quartz không tương thích?
- Có hiểu trường 0 là **giây** và thứ trong tuần 0/7 là Chủ nhật?
- Dùng `parse` cho cấu hình cần thực thi và `isValidExpression` cho validation cơ bản?
- Có kiểm tra `null` của `next(...)`, giới hạn số mốc preview?
- Có chọn `ZoneId` tường minh và dùng `ZonedDateTime` cho lịch qua DST?
- Có kiểm tra trường hợp DST gap và DST overlap trên vùng business thực tế?
- Có hiểu `@daily`, `@weekly` là **lịch địa phương**, không nhất thiết mỗi 24h/7x24h tuyệt đối?
- Có phân biệt parsing/preview với scheduling thực tế dùng `CronTrigger`/`@Scheduled`?
- Có kiểm tra task runtime, scheduler contention, misfire/skip và pool size?
- Có định nghĩa behavior khi job không chạy do restart, rollback, exception hoặc resource saturation?
- Có ngăn overlap và trùng việc trên nhiều replicas khi business yêu cầu?
- Nếu cron được sửa runtime, có cơ chế hủy/đăng ký lại lịch và audit thay đổi?
- Có policy disabled rõ ràng, không coi `"-"` là cron parser expression?
- Nếu cần bảo đảm hoàn tất công việc, có job store/outbox/queue bền vững và idempotency?

## 18. Kết luận

`CronExpression` của Spring giải quyết **phân tích cú pháp cron** và **tính mốc phù hợp kế tiếp**. Nó hỗ trợ sáu trường từ giây đến thứ trong tuần, một số ký hiệu mở rộng `L/W/#` và macro `@daily`, `@weekly`…; nó **không tự thực thi job**.

Sử dụng `CronExpression.parse(...)` để tạo expression, `isValidExpression(...)` để phát hiện cấu hình sai, rồi `next(ZonedDateTime)` để preview những mốc tiếp theo ở **đúng ZoneId**, xử lý `null` và giới hạn số lần lặp. Với cron theo giờ địa phương, DST có thể khiến một giờ không tồn tại hoặc xuất hiện hai lần — hai mốc có cùng giờ-phút không nhất thiết là cùng một instant.

Khi thật sự muốn chạy định kỳ, kết hợp `@Scheduled(cron=..., zone=...)` hoặc `TaskScheduler`/`CronTrigger` và quản lý concurrency, lifecycle, misfire, idempotency và durable execution theo yêu cầu.

### Tài liệu tham khảo

- [Spring Framework 6.2.1 — CronExpression API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/support/CronExpression.html)
- [Spring Framework 6.2.1 — CronTrigger API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/support/CronTrigger.html)
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2.1 — Scheduled API](https://docs.spring.io/spring-framework/docs/6.2.1/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Java 21 — ZonedDateTime API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/ZonedDateTime.html)
- [Java 21 — ZoneId API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/ZoneId.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cron-timezones-005">Macro cron và cách đọc lịch rõ nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# Macro cron và cách đọc lịch rõ nghĩa trong Spring Framework

**Module:** Spring Framework - concurrency  
**Nhóm:** Cron và múi giờ  
**Bài học:** Macro cron và cách đọc lịch rõ nghĩa.

## 1. Vì sao cron macro hữu ích?

Trong một hệ thống Spring, có những công việc như cập nhật số liệu theo giờ, tổng hợp log vào đầu ngày, đối soát mỗi tháng, gửi thông báo định kỳ hoặc chạy quy trình bảo trì. Các công việc này thường được đăng ký bằng `@Scheduled(cron = "...")` hoặc `TaskScheduler` cùng `CronTrigger`.

Biểu thức `0 0 * * * *` ngắn nhưng không dễ đọc với người chưa quen vị trí các trường. Cron macro thay nó bằng tên có ý nghĩa:

```java
@Scheduled(cron = "@hourly", zone = "Asia/Ho_Chi_Minh")
public void aggregateMetrics() {
    // Gọi từ Spring scheduler vào đầu mỗi giờ Việt Nam.
}
```

Đây là **method minh họa** trong một bean Spring; muốn Spring tự đăng ký lịch cần `@EnableScheduling`. Macro giúp diễn đạt đúng một số lịch phổ biến, **không** tạo ra một loại executor mới, không làm I/O tự non-blocking và không tự lưu job sau khi JVM restart.

Cần phân biệt:

```text
Ý định: "Đầu mỗi giờ theo giờ Việt Nam"
                |
                v
Macro:          @hourly
                |
                v
Cron 6 trường:  0 0 * * * *
                |
                v
Timezone:       Asia/Ho_Chi_Minh
                |
                v
Trigger tính mốc: 09:00, 10:00, 11:00...
                |
                v
Scheduler thực thi Runnable tại mốc thực tế
```

Sử dụng macro làm lịch dễ đọc hơn, nhưng **lịch đúng nghĩa** vẫn đòi hỏi xác định mốc ngày/giờ, timezone, chính sách chạy trễ, khả năng trùng lặp và xử lý lỗi.

## 2. Toàn bộ cron macro chuẩn của Spring

Trong Spring Framework 6.x, danh sách macro đã được tài liệu chính thức mô tả gồm năm nhóm chính và hai alias:

| Macro | Biểu thức cron 6 trường | Đọc theo lời |
| --- | --- | --- |
| `@yearly` hoặc `@annually` | `0 0 0 1 1 *` | **00:00:00 ngày 1 tháng 1**, mỗi năm |
| `@monthly` | `0 0 0 1 * *` | **00:00:00 ngày 1**, mỗi tháng |
| `@weekly` | `0 0 0 * * 0` | **00:00:00 Chủ nhật**, mỗi tuần |
| `@daily` hoặc `@midnight` | `0 0 0 * * *` | **00:00:00 hằng ngày** |
| `@hourly` | `0 0 * * * *` | **Phút 00 và giây 00 của mọi giờ** |

Hai cặp tương đương: `@annually` ↔ `@yearly`, và `@midnight` ↔ `@daily`. Mỗi macro là cách viết ngắn của **một biểu thức đầy đủ**, không có phép “tự chọn giờ bắt đầu” hoặc “cộng thời lượng từ lần thực thi trước”.

### Bốn cách hiểu sai dễ gây sự cố

**`@hourly` không có nghĩa “mỗi 60 phút kể từ khi application start”.** Nếu server khởi động lúc 08:27:35, mốc kế theo macro là 09:00:00 (trong timezone scheduler dùng), chứ không phải 09:27:35.

**`@weekly` không có nghĩa “Thứ hai đầu tuần”.** Macro tương đương `0 0 0 * * 0`: **Chủ nhật** 00:00 theo quy ước ngày trong tuần của Spring cron.

**`@monthly` không có nghĩa “ngày cuối tháng” hay “cứ 30 ngày”.** Nó chạy đầu ngày **1** của từng tháng, dù tháng có 28, 29, 30 hay 31 ngày.

**`@yearly` không có nghĩa “sau đúng 365 ngày”.** Nó chạy vào đầu ngày 1/1; năm nhuận và các quy tắc calendar khiến khoảng thời gian theo giây giữa hai mốc có thể khác nhau.

Các macro trên chỉ có ý nghĩa trọn vẹn khi biết **timezone**. `@daily` ở Việt Nam và `@daily` ở UTC không cùng một `Instant`.

### Những tên không có trong danh sách

Đừng tự suy đoán mọi tên bắt đầu bằng `@` đều được Spring chấp nhận. Các tên như `@reboot`, `@every 5m`, `@minutely` không thuộc danh sách macro trên. Để chạy đầu mỗi phút, viết cron sáu trường:

```text
0 * * * * *
```

Để chạy mỗi 5 phút:

```text
0 0/5 * * * *
```

Để chạy 09:30 vào các ngày Thứ hai–Thứ sáu:

```text
0 30 9 * * MON-FRI
```

Đây là các biểu thức **Spring cron**, không phải lệnh của shell. Nếu muốn tên doanh nghiệp dễ đọc, hãy đặt property/hằng số có ý nghĩa rồi lưu biểu thức cron hợp lệ; đừng phát minh thêm macro chưa được parser hỗ trợ.

## 3. Đọc macro theo lịch đã mở rộng, không phải một trường cron

Một macro Spring đại diện cho **cả biểu thức sáu trường** theo thứ tự `second minute hour day-of-month month day-of-week`. Ví dụ, `@hourly` chính là `0 0 * * * *` (đầu giờ), còn `@daily` là `0 0 0 * * *` (đầu ngày). Nếu lịch theo nghiệp vụ phải chạy 09:30 thứ Hai–Sáu, không có macro tương ứng: hãy viết `0 30 9 * * MON-FRI`.

```text
@weekly       -> 0 0 0 * * 0       -> Chủ nhật 00:00
@monthly      -> 0 0 0 1 * *       -> Ngày 1 mỗi tháng
0 * * * * *   -> giây 0 mỗi phút  -> KHÔNG phải @hourly
```

Số thứ của Spring là 0/7 cho Chủ nhật, 1 cho thứ Hai; Quartz thường khác. Spring chấp nhận `*` (mọi giá trị), `?` chỉ ở hai trường ngày, các danh sách/phạm vi/bước và `L/W/#` ở trường phù hợp. Các ký hiệu và ranh giới bước đã được hướng dẫn riêng; **trong bài này**, câu hỏi quan trọng là macro mở rộng thành giờ/phút/ngày nào và có đúng yêu cầu nghiệp vụ hay không. `@reboot`, `@every` và `@minutely` không phải macro Spring chính thức; dùng `CronExpression.isValidExpression(...)` và `next(ZonedDateTime)` khi kiểm chứng.

## 4. Cron macro khác `fixedRate` và `fixedDelay`

Cron macro mô tả **calendar schedule**, không phải khoảng nghỉ của một vòng lặp. Giả sử ứng dụng bắt đầu lúc 08:23:40:

| Cấu hình | Ý nghĩa mốc tiếp |
| --- | --- |
| `@Scheduled(cron = "@hourly")` | Đầu giờ kế tiếp: **09:00:00** |
| `@Scheduled(fixedRate = 3_600_000)` | Chạy theo nhịp **1 giờ** tính từ mốc đăng ký lịch/initial delay, không buộc đầu giờ |
| `@Scheduled(fixedDelay = 3_600_000)` | Đợi **1 giờ sau khi callback trước hoàn tất** |
| `@Scheduled(cron = "@daily")` | 00:00 theo timezone, không phải 24 giờ sau lúc callback trước chạy xong |

Nếu job `@hourly` mất 20 phút, mốc cron tiếp vẫn gắn với đầu giờ. Nếu dùng fixed-delay một giờ, thời điểm tiếp phụ thuộc vào **thời gian job thực thi**. Khác biệt này ảnh hưởng SLA, tải lên downstream và việc bỏ lỡ kỳ.

**Chọn macro** khi ý định khớp chính xác một trong năm lịch. Nếu cần 09:30 mỗi sáng, không có macro “@daily-at-09:30”; dùng `0 30 9 * * *`. Nếu muốn nghỉ 30 phút sau lần đồng bộ trước, dùng fixed-delay hoặc một trigger được thiết kế theo completion.

## 5. Time zone: cùng macro, hai lịch khác nhau

Spring `@Scheduled` có `zone` để diễn giải cron theo vùng thời gian:

```java
@Scheduled(cron = "@daily", zone = "Asia/Ho_Chi_Minh")
public void vietnamDayStart() {
    // 00:00 theo lịch Việt Nam.
}

@Scheduled(cron = "@daily", zone = "UTC")
public void utcDayStart() {
    // 00:00 theo UTC.
}
```

Đây là **hai method minh họa** trong các Spring beans có scheduling được bật. Cùng một ngày hiển thị nhưng khác `Instant`:

| Lịch | Giờ local | Thời điểm UTC |
| --- | --- | --- |
| `@daily`, `Asia/Ho_Chi_Minh` | 30/10/2026 00:00 +07:00 | `2026-10-29T17:00:00Z` |
| `@daily`, `UTC` | 30/10/2026 00:00 +00:00 | `2026-10-30T00:00:00Z` |

Múi giờ làm hai lịch lệch nhau bảy giờ. Nếu không đặt `zone`, `@Scheduled` dùng timezone của scheduler, và bạn phải kiểm tra cấu hình thực tế thay vì giả định luôn là timezone máy dev.

Sử dụng zone IANA như `Asia/Ho_Chi_Minh` giúp thể hiện rõ quy tắc lịch. `ZoneId` không phải chỉ là offset tĩnh ở mọi nơi; vùng có daylight saving time (DST) có thể thay đổi offset theo ngày.

### DST và “mỗi ngày”

Ở vùng đổi giờ mùa hè, một giờ địa phương có thể **không tồn tại** khi đồng hồ tiến lên, hoặc **lặp lại** khi đồng hồ lùi. Do đó:

- `@daily` nghĩa là **đầu mỗi ngày theo lịch địa phương**, không bảo đảm khoảng cách tuyệt đối giữa mọi lần là chính xác `Duration.ofHours(24)` ở mọi zone.
- Cron chạy 02:30 trong ngày nhảy giờ có thể gặp mốc không tồn tại; cần kiểm tra `CronExpression.next()` trên `ZonedDateTime` ở đúng vùng.
- Khi giờ bị lặp, phải quy định nghiệp vụ muốn một hay hai cơ hội thực thi, có cần lock và idempotency hay không.
- Tránh dựa vào `LocalDateTime` trần để kết luận `Instant`; phải có zone và quy tắc offset.
- Với batch tài chính/daily close, lưu cả business date theo zone lẫn `scheduledInstant` để dễ audit.

Việt Nam hiện không áp dụng DST nhưng hệ thống đa quốc gia có thể có nhiều zone.

## 6. `CronExpression`: preview lịch, chưa chạy tác vụ

Spring cung cấp `org.springframework.scheduling.support.CronExpression` từ Spring Framework 5.3+. API có thể parse macro và tính thời điểm khớp tiếp theo:

```java
CronExpression expression =
        CronExpression.parse("@hourly");

ZonedDateTime next =
        expression.next(seedZonedDateTime);

boolean valid =
        CronExpression.isValidExpression("@daily");
```

Đây là **đoạn minh họa**; `seedZonedDateTime` cần tồn tại. `parse` ném `IllegalArgumentException` với syntax không hợp lệ, `isValidExpression` kiểm tra syntax, và `next` tính mốc sau seed (có thể trả `null` nếu không còn mốc tìm được).

`CronExpression` chỉ **tính toán**, không tạo worker thread, không enqueue job và không tự gọi method được annotate `@Scheduled`. Khi cần test, dùng mốc cố định thay vì chờ đồng hồ thật tới đầu giờ.

**Vì sao chọn `ZonedDateTime`?** Vì các trường cron là thời gian địa phương nhưng kết quả cần gắn vào timeline thật. `ZonedDateTime` mang theo timezone và xử lý offset; sau đó `.toInstant()` dùng để so sánh giữa các vùng.

## 7. Java 17 runnable demo: mọi macro và lịch kế tiếp

Dự án sau có thể chạy bằng **Java 17+** và Maven với Spring Framework **6.2.12**. Nó dùng trực tiếp `spring-context`; không phải khởi động Spring Boot hoặc một server. Ví dụ tạo seed cố định, so sánh macro với biểu thức mở rộng, kiểm tra alias và timezone.

Cấu trúc:

```text
cron-macro-demo/
├── pom.xml
└── src/main/java/com/example/cron/
    └── CronMacroDemo.java
```

### `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>cron-macro-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.12</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>com.example.cron.CronMacroDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### `src/main/java/com/example/cron/CronMacroDemo.java`

```java
package com.example.cron;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import org.springframework.scheduling.support.CronExpression;

public class CronMacroDemo {

    private record Macro(String name, String full) {}

    public static void main(String[] args) {
        ZoneId vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime seed = ZonedDateTime.of(
                2026, 10, 29, 8, 34, 20, 0, vietnam);

        DateTimeFormatter format =
                DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

        List<Macro> macros = List.of(
                new Macro("@hourly", "0 0 * * * *"),
                new Macro("@daily", "0 0 0 * * *"),
                new Macro("@weekly", "0 0 0 * * 0"),
                new Macro("@monthly", "0 0 0 1 * *"),
                new Macro("@yearly", "0 0 0 1 1 *"));

        for (Macro item : macros) {
            ZonedDateTime fromMacro = Objects.requireNonNull(
                    CronExpression.parse(item.name()).next(seed));
            ZonedDateTime fromFull = Objects.requireNonNull(
                    CronExpression.parse(item.full()).next(seed));

            if (!fromMacro.equals(fromFull)) {
                throw new IllegalStateException(
                        "Cron macro expansion differs: " + item.name());
            }

            System.out.println(
                    item.name() + "=" + format.format(fromMacro));
        }

        System.out.println("annuallySame=" +
                Objects.equals(
                        CronExpression.parse("@annually").next(seed),
                        CronExpression.parse("@yearly").next(seed)));
        System.out.println("midnightSame=" +
                Objects.equals(
                        CronExpression.parse("@midnight").next(seed),
                        CronExpression.parse("@daily").next(seed)));

        System.out.println("fiveFieldsValid=" +
                CronExpression.isValidExpression("0 * * * *"));

        Instant fixed = Instant.parse("2026-10-29T01:34:20Z");
        ZonedDateTime vnNext = Objects.requireNonNull(
                CronExpression.parse("@daily").next(
                        fixed.atZone(vietnam)));
        ZonedDateTime utcNext = Objects.requireNonNull(
                CronExpression.parse("@daily").next(
                        fixed.atZone(ZoneOffset.UTC)));

        System.out.println("dailyVietnamUtc=" +
                vnNext.toInstant());
        System.out.println("dailyUtcUtc=" +
                utcNext.toInstant());
    }
}
```

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

**Các dòng do `System.out` in khi hoàn tất bình thường:**

```text
@hourly=2026-10-29 09:00:00
@daily=2026-10-30 00:00:00
@weekly=2026-11-01 00:00:00
@monthly=2026-11-01 00:00:00
@yearly=2027-01-01 00:00:00
annuallySame=true
midnightSame=true
fiveFieldsValid=false
dailyVietnamUtc=2026-10-29T17:00:00Z
dailyUtcUtc=2026-10-30T00:00:00Z
```

Phân tích output:

1. Seed được cố định tại **29/10/2026 08:34:20** theo `Asia/Ho_Chi_Minh`.
2. `@hourly` chạy tiếp **09:00 cùng ngày**; `@daily` chạy tiếp **00:00 ngày 30/10**.
3. 29/10 là **Thứ năm**; mốc `@weekly` tiếp là **Chủ nhật 1/11 00:00**.
4. `@monthly` tiếp cũng là **1/11 00:00** — hai lịch khác nhau có thể trùng thời điểm một lần.
5. `@yearly` tiếp là **1/1/2027 00:00**.
6. Alias sinh cùng `next` mốc với macro gốc.
7. Chuỗi cron **năm trường** không được chấp nhận trong API Spring này.
8. Với cùng `Instant` gốc, `@daily` tại Việt Nam và UTC cho hai mốc tương lai khác nhau.

Output không phụ thuộc thời gian hiện tại hay race scheduling; parser chỉ tính toán từ seed cố định. Maven/JVM có thể in thêm thông báo công cụ. Code mẫu gồm đủ `package`, imports, class, dependency và lệnh chạy; đây là ứng dụng console **dùng thư viện Spring**, không cần Spring application context.

## 8. Macro trong Spring `@Scheduled`

Dùng annotation khi lịch được khai báo ổn định và Spring quản lý việc đăng ký:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class SchedulingConfiguration {
}
```

```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HousekeepingJobs {

    @Scheduled(cron = "@hourly",
               zone = "Asia/Ho_Chi_Minh")
    public void hourlyMetrics() {
        // Mỗi đầu giờ.
    }

    @Scheduled(cron = "@daily",
               zone = "Asia/Ho_Chi_Minh")
    public void dayStart() {
        // 00:00 hằng ngày.
    }

    @Scheduled(cron = "0 30 9 * * MON-FRI",
               zone = "Asia/Ho_Chi_Minh")
    public void weekdayReminder() {
        // 09:30 các ngày làm việc.
    }
}
```

Đây là **hai class minh họa trong ứng dụng Spring**, không phải phần code của dự án console ở mục 7. Method `@Scheduled` đồng bộ thông thường không nhận tham số; nghiệp vụ nên lấy dependencies qua constructor injection.

**Không có `@EnableScheduling` thì Spring có thể không đăng ký các annotation `@Scheduled` của ứng dụng**, dù method vẫn có thể được gọi trực tiếp. `@EnableScheduling` và `@EnableAsync` là hai cơ chế khác nhau; không cần gắn `@Async` chỉ để sử dụng cron.

Một method có thể mang nhiều `@Scheduled` declarations và Spring xử lý từng declaration độc lập; chúng có thể overlap khi đến hạn gần nhau. Đồng thời nhiều instance ứng dụng có thể đăng ký cùng một cron. Với job có side effect, đừng kết luận “một annotation = một thực thi trên toàn hệ thống”.

### Nên tách cron khỏi source bằng property

```java
@Scheduled(
        cron = "${jobs.report.cron:@daily}",
        zone = "${jobs.report.zone:Asia/Ho_Chi_Minh}")
public void report() {
    // Phần nghiệp vụ do service thực hiện.
}
```

Ví dụ `application.yml`:

```yaml
jobs:
  report:
    cron: "@daily"
    zone: "Asia/Ho_Chi_Minh"
```

Đây là method minh họa cần đặt trong Spring-managed bean. Các property được resolve trong lúc Spring xây/đăng ký scheduled tasks. **Sửa file runtime không mặc nhiên làm lịch tự đổi ngay**. Nếu cần re-schedule động, phải có cơ chế hủy/đăng ký lại theo state được quản lý.

### Tắt lịch theo môi trường

Spring có sentinel `Scheduled.CRON_DISABLED` mang giá trị `"-"`. Với property:

```yaml
jobs:
  report:
    cron: "-"
    zone: "Asia/Ho_Chi_Minh"
```

Annotation handler của Spring hiểu `"-"` là **không đăng ký cron trigger**. Nó **không phải** một cron expression hợp lệ cho `CronExpression.parse("-")`. Phân biệt ngữ cảnh parser và ngữ cảnh annotation/config.

### Khi có nhiều cron, xác định scheduler bean

```java
@Bean("taskScheduler")
public ThreadPoolTaskScheduler taskScheduler() {
    ThreadPoolTaskScheduler scheduler =
            new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("scheduled-");
    return scheduler;
}
```

Đây là method minh họa trong class `@Configuration` cần imports tương ứng. Theo Javadoc, `ThreadPoolTaskScheduler` thực thi callback trên **scheduler threads**. Nếu job gọi HTTP blocking hoặc DB query lâu, nó giữ một trong các scheduler threads và có thể khiến lịch khác chậm. Pool size 2 chỉ minh họa, cần đo tải thực và áp dụng timeout/resource limits.

## 9. Dùng macro với `CronTrigger` và `TaskScheduler` programmatic

Nếu lịch được tạo theo yêu cầu người dùng, `TaskScheduler` cho phép đăng ký và giữ handle:

```java
ZoneId vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
CronTrigger daily = new CronTrigger("@daily", vietnam);

ScheduledFuture<?> handle =
        taskScheduler.schedule(
                () -> refreshDailySummary(),
                daily);

if (handle != null) {
    // Khi không muốn kích hoạt tiếp:
    handle.cancel(false);
}
```

Đây là **đoạn minh họa**, cần bean `taskScheduler`, method `refreshDailySummary` và imports `ZoneId`, `CronTrigger`, `ScheduledFuture`. `TaskScheduler.schedule(Runnable, Trigger)` có thể trả `null` nếu trigger không tạo mốc tiếp theo.

Ba lớp cần tách:

- `CronExpression`: parse **cú pháp** và tính thời điểm tiếp theo; **không tự chạy task**.
- `CronTrigger`: triển khai `Trigger` để chọn thời điểm dựa trên `TriggerContext`, có chính sách tính lịch.
- `TaskScheduler`: đăng ký `Runnable` với backend để **thực sự chạy** tại các mốc.

`ScheduledFuture.cancel(false)` ngăn lần scheduled tiếp theo theo hợp đồng backend, nhưng **không tự cancel một HTTP call** hoặc một business task đã được dispatch sang executor khác.

## 10. Lịch bị trễ, bị bỏ và chiến lược catch-up

Macro `@hourly` chỉ nói “đầu mỗi giờ”, không nói phải làm gì nếu một job mất **75 phút** hoặc ứng dụng tắt giữa các mốc.

Trong Spring 6.2, `CronTrigger` có các chính sách:

- `new CronTrigger(expression)` hoặc `CronTrigger.forLenientExecution(expression)`: tính mốc theo **completion time**; một số mốc cron đã lỡ có thể **bị bỏ qua** nếu callback chạy lâu.
- `CronTrigger.forFixedExecution(expression)`: tính theo **last scheduled time** để giữ các mốc theo lịch, có thể phải thực hiện những lần tới **muộn** để bù.

Điều này khác với việc khôi phục công việc đã bỏ khi **toàn bộ tiến trình ngừng chạy**. Một scheduler local trong RAM không tự có lịch sử những giờ đã qua khi JVM restart. Nếu nghiệp vụ bắt buộc backfill, lưu `jobId`, due time, last processed period và trạng thái durable; chủ động đánh giá những kỳ thiếu rồi enqueue công việc cần bù.

Ngay cả một trigger không bỏ mốc cũng **không tự bảo đảm exactly-once side effect**. Job có thể chết sau khi ghi DB nhưng trước khi mark success, hoặc chạy trên nhiều replicas đồng thời. Cần idempotency key và xử lý transaction/lease tương ứng.

### Scheduler threads không phải worker pool vô hạn

`ThreadPoolTaskScheduler` có pool hữu hạn. Nếu callback chạy lâu trên chính scheduler thread, các cron khác có thể trễ. Khi cần tách:

```text
CronTrigger / TaskScheduler
           |
       callback ngắn
           |
           | workerExecutor.execute(realJob)
           v
TaskExecutor có bounded queue
           |
        realJob()
```

Nhưng khi callback chỉ submit rồi return ngay, cron tiếp theo có thể submit thêm một job **khi job trước còn chạy**, làm overlap. Cần concurrency limit hoặc single-flight, và xử lý `TaskRejectedException`. `CallerRunsPolicy` có thể khiến scheduler thread tự chạy job lúc worker pool đầy, phá ý định tách pool.

## 11. Kiểm tra cron do người dùng/cấu hình cung cấp

```java
public static void validateCron(String cron) {
    if (!CronExpression.isValidExpression(cron)) {
        throw new IllegalArgumentException(
                "Unsupported cron expression: " + cron);
    }
}
```

Đây là method minh họa dùng class Spring `CronExpression`, không phải một phần của console demo. `isValidExpression` chỉ xác minh **cú pháp**. Cron `"* * * * * *"` có thể hợp lệ nhưng chạy **mỗi giây**, quá thường xuyên cho việc đối soát nặng.

Quy trình khuyến nghị:

1. Chỉ cho phép những macro/biểu thức mà sản phẩm thực sự cần; không tùy tiện nhận cron từ nguồn không tin cậy.
2. Validate syntax bằng Spring parser **cùng version với runtime**.
3. Validate `ZoneId` và ý nghĩa local time; tránh mơ hồ UTC/giờ máy.
4. Dùng `CronExpression.next(ZonedDateTime)` preview nhiều mốc, đặc biệt cuối tháng, năm nhuận và DST.
5. Kiểm tra khoảng cách tối thiểu giữa các mốc để không gây overload.
6. Kiểm tra người dùng có quyền tạo/sửa lịch và phạm vi tenant/job.
7. Lưu expression, timezone, business owner, version, thời điểm sửa và audit trail.
8. Xác định hủy/đăng ký lại thế nào khi config đổi trong lúc job đang chạy.
9. Định nghĩa deadline, retry, backpressure, overlapped-job policy và idempotency.
10. Nếu job phải survive restart, lưu job state bền vững; cron string đơn lẻ không đáp ứng.

### Không nhầm giữa lịch và điều kiện nghiệp vụ

Macro `@monthly` luôn trỏ tới **ngày 1**, nhưng một quy trình “đối soát tháng trước sau khi các dịch vụ hoàn tất” có thể không nên chạy ngay 00:00. Nếu cần chắc chắn dữ liệu tháng trước đã chốt, hãy chọn giờ phù hợp, hoặc kiểm tra completion marker trước khi xử lý. Cron chỉ kích hoạt *cơ hội xử lý*, không xác minh điều kiện kinh doanh.

## 12. Testing cron chính xác mà không chờ thời gian thật

Test không nên `Thread.sleep()` tới đầu giờ. Dùng thời điểm gốc cố định:

```java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime seed = ZonedDateTime.of(
        2026, 10, 29, 8, 34, 20, 0, zone);

ZonedDateTime next =
        CronExpression.parse("@hourly").next(seed);

assertEquals(
        ZonedDateTime.of(2026, 10, 29, 9, 0, 0, 0, zone),
        next);
```

Đây là **đoạn JUnit 5 minh họa**, cần `@Test`, import `assertEquals`, `CronExpression`, `ZoneId`, `ZonedDateTime` và dependency JUnit 5. Không phải source nằm trong dự án console ở mục 7.

Các ca cần kiểm thử:

- **Mọi macro và alias:** `@annually` == `@yearly`, `@midnight` == `@daily`; mỗi macro bằng biểu thức mở rộng.
- **Đúng mốc:** nếu seed là `09:00:00`, `@hourly` trả lần **10:00** tiếp theo, không trả lại seed.
- **Ranh giới ngày:** `@daily` từ 23:59:59 tới 00:00 ngày kế tiếp.
- **Ranh giới tháng:** `@monthly` từ 31/12 sang 01/01.
- **Năm nhuận:** `@yearly` là lịch 1/1, không cộng 365 ngày cứng.
- **Ngày tuần:** `@weekly` vào Chủ nhật, không phải đầu tuần ISO Thứ hai.
- **Timezone:** cùng `Instant` nhưng zone khác cho lịch local khác.
- **DST:** ngày giờ không tồn tại hoặc lặp ở một zone có DST.
- **Cú pháp:** năm trường, macro lạ, ký hiệu đặt sai chỗ bị từ chối.
- **Chính sách sản phẩm:** biểu thức hợp lệ nhưng tần suất quá cao bị từ chối ở application.
- **Integration:** Spring phải bật scheduling, đúng scheduler bean, không nhân bản job ngoài ý muốn.
- **Nhiều replica:** kiểm thử lock/lease/idempotency riêng; test `CronExpression.next` không chứng minh globally-once.

### Kiểm tra code review bằng lời, không chỉ bằng parser

Trước khi chấp nhận `@weekly`, hãy hỏi “Chủ nhật 00:00 có thật là đầu tuần kinh doanh không?” Trước khi chấp nhận `@monthly`, hỏi “Dữ liệu tháng trước đã chốt lúc 00:00 ngày 1 chưa?” Trước khi chấp nhận `@hourly`, hỏi “Đầu giờ có tạo đỉnh lưu lượng chung cho hàng trăm service không?” Cách đọc đúng bao gồm **ý nghĩa nghiệp vụ**, không chỉ cú pháp.

## 13. Lỗi thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Đọc `@hourly` thành “một giờ sau lúc start” | Chạy sớm hoặc muộn so với kỳ vọng | Hiểu mốc đầu giờ; nếu cần interval dùng fixed-rate/delay |
| Đọc `@weekly` thành Thứ hai | Chạy Chủ nhật | Dùng `0 0 0 * * MON` cho Thứ hai |
| Đọc `@monthly` thành cuối tháng | Chạy ngày 1 | Dùng `0 0 0 L * *` khi cần ngày cuối |
| Đọc `@yearly` thành 365 ngày | Lệch với lịch năm nhuận | Dùng macro cho 1/1, Duration cho thời lượng |
| Nhầm `0 0 * * * *` với `0 * * * * *` | Tăng tần suất 60 lần | Đọc từ giây trước |
| Copy cron Unix năm trường | Spring không parse theo format đó | Dùng sáu trường |
| Copy số DOW theo Quartz | Sai thứ | Dùng tên `MON`, `SUN` hoặc quy tắc Spring |
| Dùng `@minutely`, `@reboot` | Parser từ chối | Dùng macro được Spring công bố hoặc cron 6 trường |
| Bỏ `zone` | Chạy sai giờ giữa môi trường | Zone explicit cho nghiệp vụ |
| Coi mọi ngày dài 24 giờ ở DST zone | Sai mốc | Preview bằng `ZonedDateTime` |
| Nghĩ `@daily` là durable job | Mất tick khi restart | Lưu job state/JobStore |
| Nghĩ 3 replicas chỉ chạy 1 job | Trùng side effect | Distributed coordination và idempotency |
| Dùng `"-"` trong `CronExpression.parse` | Đây chỉ là sentinel của `@Scheduled` | Phân biệt disable với cron |
| Nghĩ sửa property sẽ re-register tự động | Lịch không đổi tức thời | Lifecycle reschedule rõ ràng |
| Gọi `Future.get()` của periodic job ở request | Có thể chặn rất lâu | Metrics/callback, không chờ Future periodic |
| Nghĩ macro quyết định catch-up | Mốc bỏ lỡ không được bù như mong muốn | Chọn trigger policy, durable recovery |
| Callback block lâu trên scheduler pool nhỏ | Trễ các cron khác | Timeout, offload có backpressure |
| Chỉ kiểm tra syntax, không kiểm tra tần suất | Cron mỗi giây làm quá tải | Whitelist, quota và min interval |

## 14. Tự kiểm tra kiến thức

**1.** `@hourly` chạy vào những thời điểm nào?  
**Đáp:** Đầu mọi giờ, phút 00 giây 00 theo timezone, ví dụ 09:00, 10:00, 11:00.

**2.** Macro nào tương đương `@yearly`?  
**Đáp:** `@annually`.

**3.** Macro nào tương đương `@daily`?  
**Đáp:** `@midnight`.

**4.** `@weekly` là đầu tuần Thứ hai?  
**Đáp:** Không. Spring quy định Chủ nhật 00:00 qua `0 0 0 * * 0`.

**5.** `@monthly` có phải ngày cuối tháng không?  
**Đáp:** Không, là ngày 1 lúc 00:00.

**6.** Cron `0 0 * * * *` và `0 * * * * *` khác gì?  
**Đáp:** Biểu thức đầu mỗi đầu giờ, biểu thức sau mỗi đầu phút.

**7.** `@daily` ở Việt Nam có trùng 00:00 UTC không?  
**Đáp:** Không. 00:00 Việt Nam là 17:00 UTC của ngày trước.

**8.** `CronExpression.parse("@daily")` có tự đăng ký job không?  
**Đáp:** Không. Nó chỉ parse và tính next time; dùng `TaskScheduler`/`@Scheduled` để thực thi.

**9.** Có thể dùng `@reboot` như một macro Spring chính thức không?  
**Đáp:** Không thuộc danh sách macro Spring đã công bố.

**10.** `@hourly` bảo đảm bù mọi giờ bỏ lỡ khi JVM tắt?  
**Đáp:** Không. Cần durable recovery nếu không được bỏ kỳ.

**11.** Nhiều replicas đều bật `@Scheduled(cron="@daily")` thì sao?  
**Đáp:** Mỗi replica có thể chạy một lần. Cần coordination và idempotency để ngăn trùng side effect.

**12.** Cron mỗi giây có thể hợp lệ nhưng chưa chắc an toàn?  
**Đáp:** Đúng. Phải giới hạn tần suất, concurrency, resource budget và downstream.

## 15. Tóm tắt và tài liệu chính thức

**Macro là tên có ý nghĩa của một cron sáu trường, không phải cơ chế scheduler mới.** Hãy luôn đọc mốc chính xác và timezone, xác minh qua `CronExpression.next(ZonedDateTime)` rồi thiết kế thực thi, lỗi, missed triggers và idempotency cho production.

- `@hourly` = `0 0 * * * *` → đầu mỗi giờ.
- `@daily`/`@midnight` = `0 0 0 * * *` → đầu mỗi ngày.
- `@weekly` = `0 0 0 * * 0` → Chủ nhật 00:00.
- `@monthly` = `0 0 0 1 * *` → ngày 1 lúc 00:00.
- `@yearly`/`@annually` = `0 0 0 1 1 *` → ngày 1/1 lúc 00:00.

**Tài liệu tham khảo:**

- [Spring Framework 6.2 — Scheduling và cron macros](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html): danh sách macro, cú pháp sáu trường, `@Scheduled`.
- [Spring Framework 6.2 — `CronExpression` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html): `parse`, `next`, macro và validation.
- [Spring Framework 6.2 — `CronTrigger` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html): timezone, lenient/fixed execution.
- [Spring Framework 6.2 — `@Scheduled` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): cron, zone, disabled trigger và annotation semantics.
- [Java SE 17 — `ZonedDateTime`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html): time zone, offset và `Instant`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cron-timezones-006">Chỉ định múi giờ thay cho phụ thuộc môi trường máy chủ</a>

<details>
<summary>Xem chi tiết</summary>

# Chỉ định múi giờ thay cho phụ thuộc môi trường máy chủ

> **Module:** Spring Framework – concurrency  
> **Nhóm kiến thức:** Cron và múi giờ  
> **Mục tiêu:** Cấu hình lịch `@Scheduled` theo **múi giờ nghiệp vụ được chỉ định** thay vì ngầm dựa vào timezone của máy chủ/JVM; kiểm chứng cùng một biểu thức cron luôn sinh ra mốc UTC mong muốn qua các môi trường; nhận biết giới hạn của việc ghim zone trước DST, thay đổi cấu hình, triển khai nhiều replica và yêu cầu đúng một lần mỗi ngày nghiệp vụ.

## 1. Vấn đề thực tế: cùng một cron, hai máy lại chạy cách nhau nhiều giờ

Giả sử nhóm vận hành yêu cầu tổng hợp báo cáo **09:30 sáng các ngày làm việc tại Việt Nam**. Lập trình viên triển khai:

~~~java
@Scheduled(cron = "0 30 9 * * MON-FRI")
public void createDailyReport() {
    reportService.generate();
}
~~~

Đây là **snippet Spring tích hợp**, cần `@EnableScheduling`, bean Spring và dependency `reportService`; chưa phải chương trình Java chạy một mình. Biểu thức có đủ sáu trường và đúng 09:30 theo lịch, nhưng **không chỉ rõ “09:30 ở đâu”**.

Hệ quả khi chuyển ứng dụng giữa các môi trường:

| Máy chạy scheduler | Timezone hiệu lực nếu không ghi `zone` và scheduler dùng timezone mặc định tương ứng | Mốc thực cho 09:30 ngày 29/10/2026 |
| --- | --- | --- |
| Máy A, múi giờ `Asia/Ho_Chi_Minh` | 09:30 giờ Việt Nam | `2026-10-29T02:30:00Z` |
| Máy B, múi giờ `UTC` | 09:30 UTC | `2026-10-29T09:30:00Z` |
| Máy C, múi giờ `America/New_York` | 09:30 giờ New York | `2026-10-29T13:30:00Z` |

Một thay đổi Docker image, JVM option hoặc cấu hình scheduler có thể khiến job chạy sai giờ dù mã nguồn không đổi. Có ứng dụng chạy đúng trên laptop nhưng lệch giờ sau khi lên Kubernetes vì container mặc định UTC.

**Cách sửa trọng tâm của bài:**

~~~java
@Scheduled(
    cron = "0 30 9 * * MON-FRI",
    zone = "Asia/Ho_Chi_Minh")
public void createDailyReport() {
    reportService.generate();
}
~~~

Khi zone đã chỉ định, cron được tính theo **09:30 giờ Việt Nam**, bất kể JVM đặt mặc định là UTC hoặc New York. Đây là **tính ổn định của ý nghĩa lịch**, không phải bảo đảm job bắt đầu đúng đến từng mili giây hay chỉ chạy một lần toàn cluster.

## 2. Quy tắc của Spring: zone tường minh thắng zone mặc định

`@Scheduled` có thuộc tính `zone` kiểu `String`. Javadoc Spring giải thích: thuộc tính này xác định timezone dùng để tính **biểu thức cron**; giá trị mặc định là chuỗi rỗng, tức sử dụng timezone của scheduler. Vì vậy:

1. Nếu khai báo `@Scheduled(cron = "...", zone = "Asia/Ho_Chi_Minh")`, Spring tính lịch ở zone tường minh.
2. Nếu chỉ khai báo `@Scheduled(cron = "...")`, zone trống; scheduler/clock được cấu hình quyết định timezone hiệu lực. Trong cấu hình mặc định thông dụng, nó chịu ảnh hưởng của timezone JVM.
3. Nếu scheduler bean được cấu hình `Clock` riêng, zone fallback có thể không trùng với `TimeZone.getDefault()` của JVM. **Không được nói rằng Spring luôn lấy timezone của hệ điều hành trong mọi cấu hình**.
4. `zone` thuộc **lịch cron**; nó không phải cấu hình tên scheduler bean, không phải thuộc tính thay đổi nội dung timestamp trong DB và không điều khiển timezone cho từng HTTP request.

Nói gọn: với cron gắn với địa phương, **ngữ cảnh thời gian là một phần của hợp đồng nghiệp vụ**, nên phải xuất hiện tường minh trong cấu hình job.

Spring cron dùng **sáu trường** `second minute hour day-of-month month day-of-week`. Ví dụ `0 30 9 * * MON-FRI` là giây 0, phút 30, giờ 9, từ thứ Hai đến thứ Sáu trong zone đã chọn. Không chuyển tự động biểu thức Unix năm trường sang Spring mà không kiểm tra.

## 3. Vì sao chỉnh timezone của server không phải lời giải bền vững?

Một cách chữa cháy là bắt toàn bộ server chạy ở timezone Việt Nam. Chẳng hạn set `-Duser.timezone=Asia/Ho_Chi_Minh` hoặc chỉnh `TZ` trong container. Nó có thể làm cron **không ghi zone** chạy đúng vào lúc đó, nhưng mang theo các rủi ro:

- Một ứng dụng có thể chạy hàng chục cron cho nhiều khu vực; một timezone toàn JVM không diễn tả được tất cả.
- Thay JVM option ảnh hưởng cả formatting, thư viện sử dụng default timezone và log thời gian, có thể tạo tác dụng phụ không liên quan scheduling.
- Mỗi image, node, pod hoặc môi trường CI có thể có timezone mặc định khác nhau; không nên dựa vào môi trường “tình cờ đúng”.
- Scheduler có thể dùng `Clock` được inject riêng, nên thay timezone OS/JVM chưa chắc làm thay đổi mọi lịch theo cách kỳ vọng.
- Khi triển khai nhiều replica tại các region khác nhau, những replica cùng cron nhưng khác timezone mặc định có thể thực thi tại **những thời điểm UTC khác nhau**.
- Thay default timezone lúc runtime là **thay đổi trạng thái dùng chung trong process**; không nên sử dụng như thao tác cấu hình một job đơn lẻ.

Một hệ thống có thể vẫn đặt timezone JVM là UTC để thống nhất log/đồng hồ vận hành; điều đó không xung đột với việc mỗi lịch chỉ rõ `zone` theo nghiệp vụ.

**Khuyến nghị:** kiểm soát timezone **ở từng job hoặc lịch nghiệp vụ**, đồng thời dùng `Instant`/UTC cho timestamp hệ thống và so sánh thời điểm. Không coi việc cấu hình `TZ` là sự thay thế cho `@Scheduled(zone=...)`.

## 4. Ba kiểu thời gian cần phân biệt: LocalDateTime, ZonedDateTime, Instant

Một yêu cầu “09:30 giờ Việt Nam” có thể được biểu diễn ở nhiều lớp:

| Kiểu Java Time | Chứa thông tin nào? | Có xác định duy nhất thời điểm trên trục UTC? |
| --- | --- | --- |
| `LocalTime` | 09:30, chưa có ngày hay vùng | Không |
| `LocalDateTime` | Ngày và 09:30, nhưng chưa có zone | Không |
| `ZoneId` | Vùng có quy tắc offset theo ngày/giờ, ví dụ `Asia/Ho_Chi_Minh` | Tự nó không chứa một thời điểm |
| `ZonedDateTime` | Local date/time + `ZoneId` + offset được giải quyết | Có; sau khi resolve DST overlap/gap |
| `Instant` | Một mốc tuyệt đối như `2026-10-29T02:30:00Z` | Có |

Ví dụ:

~~~java
ZoneId businessZone = ZoneId.of("Asia/Ho_Chi_Minh");
ZonedDateTime localSlot = ZonedDateTime.of(
    2026, 10, 29, 9, 30, 0, 0, businessZone);
Instant scheduledAt = localSlot.toInstant();
~~~

Đây là **đoạn Java tích hợp** với các import từ `java.time`. `scheduledAt` là `2026-10-29T02:30:00Z`. Khi cần đối chiếu thời điểm giữa nhiều máy, so sánh `Instant` chứ không so sánh chuỗi `09:30` không có zone.

**Zone không phải offset cố định.** `Asia/Ho_Chi_Minh` hiện có offset +07:00 và không áp DST thường niên; nhưng với `America/New_York`, offset thay đổi theo mùa. Vì thế hãy lưu **IANA region ID** nếu nghiệp vụ mô tả một địa phương thay vì chỉ lưu `+07:00` hoặc `-05:00`.

## 5. Cấu hình Spring tối thiểu, không phụ thuộc timezone máy chủ

Một Spring Framework application đơn giản có thể khai báo:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
~~~

Sau đó bean chứa cron:

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BusinessReportJob {
    private final ReportService reports;

    public BusinessReportJob(ReportService reports) {
        this.reports = reports;
    }

    @Scheduled(
        cron = "0 30 9 * * MON-FRI",
        zone = "Asia/Ho_Chi_Minh")
    public void run() {
        reports.generateForCurrentBusinessDay();
    }
}
~~~

Đây là **các đoạn mã tích hợp project**, `ReportService` là service nghiệp vụ cần được triển khai. Lớp cấu hình phải được nạp và scheduled bean phải được Spring quản lý. Không có `@EnableScheduling` hoặc cơ chế registration tương đương thì annotation sẽ không tự sinh một scheduler.

Giả sử hai JVM có timezone mặc định khác nhau, cả hai vẫn tính **cùng lịch 09:30 Việt Nam**. Tuy nhiên nếu **cả hai đều chạy ứng dụng**, cả hai có thể thực thi job: `zone` chỉ sửa **mốc thời gian**, không xử lý **số lượng instance thực thi**.

Với nhiều job và nhiều scheduler bean, cần xác nhận `TaskScheduler` nào thực sự được dùng. Có thể chỉ định scheduler bằng cấu hình Spring phù hợp; đừng giả định tăng thread pool của `@Async` sẽ thay đổi số thread của scheduling.

## 6. Đưa zone vào cấu hình ngoài nhưng vẫn chỉ định tường minh

Nên externalize cron và zone khi nhiều environment có nghiệp vụ khác nhau, nhưng vẫn ghi rõ rằng **zone là tham số của job**, không lấy từ timezone của pod:

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CutoffJob {
    @Scheduled(
        cron = "${jobs.cutoff.cron:0 30 9 * * MON-FRI}",
        zone = "${jobs.cutoff.zone:Asia/Ho_Chi_Minh}")
    public void execute() {
        // Gọi service xử lý ngày nghiệp vụ đã được xác định.
    }
}
~~~

Ví dụ `application.yaml`:

~~~yaml
jobs:
  cutoff:
    cron: "0 30 9 * * MON-FRI"
    zone: "Asia/Ho_Chi_Minh"
~~~

Đây là **snippet Spring**, không phải ứng dụng độc lập: cần `@EnableScheduling` và bean được component-scan. Giá trị sau dấu `:` trong `${...:...}` là fallback của placeholder, khác với ký hiệu cron. Chỉ để fallback khi đó thực sự là policy sản phẩm; với nghiệp vụ đa quốc gia, tốt hơn là bắt buộc cấu hình zone theo tenant/job.

Giá trị đặc biệt `-` của Spring (`Scheduled.CRON_DISABLED`) cho phép tắt cron qua property. Khi tắt lịch, giá trị zone vẫn có thể hiện diện trong cấu hình nhưng **không có trigger chạy**.

**Lưu ý:** thay đổi YAML hay ConfigMap không mặc nhiên cập nhật một lịch `@Scheduled` đã được đăng ký khi context khởi tạo. Đổi thời điểm hoặc zone tại runtime cần cơ chế hủy/đăng ký lại, hoặc một giải pháp scheduler quản lý lịch động.

## 7. Ba nguồn cấu hình dễ nhầm: hệ điều hành, JVM và Clock scheduler

| Nguồn | Ví dụ | Ảnh hưởng điển hình |
| --- | --- | --- |
| Zone hệ điều hành/container | `TZ=UTC`, cấu hình `/etc/localtime` | Có thể ảnh hưởng timezone default khi JVM khởi tạo, nhưng không phải luôn quyết định mọi clock |
| JVM default timezone | `-Duser.timezone=UTC`, `TimeZone.setDefault(...)` | Ảnh hưởng API lấy default zone của JVM và code phụ thuộc default |
| `TaskScheduler`/trigger clock | `Clock` hoặc scheduler tùy biến | Có thể quyết định zone mặc định khi `CronTrigger` không được cấp `ZoneId` riêng |
| `@Scheduled.zone` | `Asia/Ho_Chi_Minh` | Ghim zone dùng để tính **cron của method cụ thể** |
| Business data | `tenant.timeZoneId` | Mô tả zone nghiệp vụ cho từng tenant và cách tính job của họ |
| Định dạng log | log formatter timezone | Chỉ quyết định cách trình bày timestamp trong log, không nhất thiết quyết định cron |

Quy tắc khắc phục sự cố: đọc đủ **cron, zone được khai báo, scheduler/clock, JVM default timezone, và mốc thực tế theo UTC**. Không chỉ nhìn một biến môi trường `TZ` rồi kết luận scheduler đã chạy sai.

Chẳng hạn một pod chạy UTC có thể in `2026-10-29 02:30Z` trong log. Đó lại chính là **09:30 giờ Việt Nam**, hoàn toàn đúng cho lịch đã ghim `Asia/Ho_Chi_Minh`. Lỗi chỉ xuất hiện nếu người vận hành đối chiếu chuỗi giờ trên log mà quên chuyển zone.

## 8. Ví dụ Java 17 chạy được: đổi timezone server nhưng giữ nguyên cron đã ghim

Ví dụ này dùng `CronTrigger` của **Spring Framework 6.2.9** để chứng minh cơ chế tính lịch **không cần đợi đến 09:30**. Nó giữ một `Instant` tham chiếu cố định và thử hai môi trường JVM: `UTC` và `America/New_York`.

Ta so sánh:

- `pinned`: `new CronTrigger(cron, ZoneId.of("Asia/Ho_Chi_Minh"))` có zone tường minh, tương đương ý nghĩa `@Scheduled(zone = "...")`.
- `inherited`: `new CronTrigger(cron)` sử dụng zone từ `TriggerContext.getClock()`; trong demo, clock được tạo từ `ZoneId.systemDefault()`, nên **thay default JVM** sẽ làm lịch tính khác.

### Cấu trúc project

~~~text
explicit-zone-demo/
├── pom.xml
└── src/main/java/demo/ExplicitZoneDemo.java
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>explicit-zone-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.9</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### src/main/java/demo/ExplicitZoneDemo.java

~~~java
package demo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

public class ExplicitZoneDemo {
    private static final String CRON = "0 30 9 * * MON-FRI";
    private static final Instant REF =
        Instant.parse("2026-10-29T00:00:00Z");

    private static Instant next(CronTrigger trigger) {
        Clock clock = Clock.fixed(REF, ZoneId.systemDefault());
        return trigger.nextExecution(new SimpleTriggerContext(clock));
    }

    public static void main(String[] args) {
        TimeZone original = TimeZone.getDefault();
        try {
            CronTrigger pinned = new CronTrigger(
                CRON, ZoneId.of("Asia/Ho_Chi_Minh"));
            CronTrigger inherited = new CronTrigger(CRON);

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Instant pinnedOnUtcServer = next(pinned);
            Instant inheritedOnUtcServer = next(inherited);

            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            Instant pinnedOnNyServer = next(pinned);
            Instant inheritedOnNyServer = next(inherited);

            System.out.println("Pinned on UTC server: " + pinnedOnUtcServer);
            System.out.println("Pinned on NY server: " + pinnedOnNyServer);
            System.out.println("Default on UTC server: " +
                inheritedOnUtcServer);
            System.out.println("Default on NY server: " +
                inheritedOnNyServer);
            System.out.println("Pinned is stable: " +
                pinnedOnUtcServer.equals(pinnedOnNyServer));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
~~~

Chạy từ thư mục project bằng JDK 17+ và Maven:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.ExplicitZoneDemo
~~~

**Stdout chính xác đã kiểm chứng** khi chạy Spring 6.2.9 với các quy tắc timezone hiện hành trong JDK:

~~~text
Pinned on UTC server: 2026-10-29T02:30:00Z
Pinned on NY server: 2026-10-29T02:30:00Z
Default on UTC server: 2026-10-29T09:30:00Z
Default on NY server: 2026-10-29T13:30:00Z
Pinned is stable: true
~~~

Mã được **biên dịch với target Java 17 và đã chạy thực tế** trên JDK có Spring Framework 6.2.9. Khối `finally` trả default timezone về giá trị ban đầu để tránh làm ảnh hưởng các công việc Java khác trong cùng tiến trình demo.

**Không dùng `TimeZone.setDefault` để đổi zone production theo từng request.** Demo cố ý thay default như một phép thử, chỉ chạy trong một chương trình kiểm chứng độc lập; `TimeZone.setDefault` tác động toàn JVM, có thể tạo race với thư viện hoặc thread khác.

## 9. Giải thích vì sao kết quả demo đáng tin

Cùng biểu thức `0 30 9 * * MON-FRI`:

1. `REF` cố định tại `2026-10-29T00:00:00Z` nên không bị phụ thuộc ngày hiện tại.
2. Với zone `Asia/Ho_Chi_Minh`, 09:30 ngày đó tương ứng **02:30Z**. `pinned` trả cùng giá trị khi environment clock chuyển giữa UTC và New York.
3. Với scheduler clock ở UTC, cron không ghim tính 09:30 UTC, tức **09:30Z**.
4. Với clock ở `America/New_York` tại thời điểm tham chiếu, local zone có offset **-04:00**, nên 09:30 địa phương tương ứng **13:30Z**.
5. `pinnedOnUtcServer.equals(pinnedOnNyServer)` trả `true` vì cả hai là cùng một `Instant`.

Demo chủ động truyền `Clock.fixed(REF, ZoneId.systemDefault())` vào `SimpleTriggerContext` để làm rõ **sự phụ thuộc của trigger không ghim vào scheduler clock**, chứ không chứng minh rằng mọi ứng dụng Spring tự lấy timezone trực tiếp từ JVM tại mỗi lần tick.

Nếu ứng dụng dùng `@Scheduled` thật, kiểm chứng thêm **bean scheduler đang active** và cách nó được đăng ký. Code demo tính toán `nextExecution` mà không tạo một scheduled job thường trú; do vậy nó có đầu ra xác định và không phải đợi cron hoạt động ngoài đời.

## 10. Tính ổn định không có nghĩa toàn bộ hệ thống ngừng sử dụng timezone mặc định

Khi `@Scheduled(zone="Asia/Ho_Chi_Minh")` được ghim, **mốc cron** độc lập với default timezone của scheduler. Tuy nhiên thân job có thể còn chứa:

~~~java
LocalDate today = LocalDate.now(); // Dùng default timezone của JVM
~~~

Đây là **snippet Java minh họa**. Nếu job chạy đúng lúc 09:30 Việt Nam nhưng `LocalDate.now()` được tính ở một timezone khác, ý nghĩa “ngày nghiệp vụ hôm nay” có thể khác, đặc biệt quanh 00:00 hoặc các zone lệch ngày.

Sửa thành:

~~~java
ZoneId businessZone = ZoneId.of("Asia/Ho_Chi_Minh");
LocalDate businessDate = LocalDate.now(businessZone);
~~~

Lưu ý khác: `LocalDate.now(businessZone)` lấy **thời điểm hiện tại của hệ thống** rồi chuyển về business zone; nó không nhất thiết bằng **ngày của lần chạy dự kiến** nếu job bị trễ qua ngày hoặc đang xử lý lượt chạy bù. Với công việc phải xử lý theo một business date xác định, nên truyền `scheduledBusinessDate` từ job record hoặc tính rõ theo lịch và trạng thái lần trước.

Đừng để logic DB, ngày chốt sổ, tên file, đường dẫn lưu trữ hoặc câu lệnh truy vấn lọc theo `CURRENT_DATE` của DB server mà không xác định timezone. **Ghim zone của cron chỉ giải quyết thời điểm gọi method**, còn các phép tính ngày trong method và trong DB vẫn phải dùng hợp đồng thời gian riêng.

## 11. Xác thực timezone để tránh sai chính tả và fallback nguy hiểm

Ưu tiên ID vùng do IANA quản lý như `Asia/Ho_Chi_Minh`, `Europe/Paris`, `America/New_York`, hoặc `UTC`. Tránh viết tắt mơ hồ `CST`, `PST`, `IST` vì có thể không diễn tả duy nhất zone hay quy tắc DST.

Trong Java:

~~~java
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
CronExpression.parse("0 30 9 * * MON-FRI");
~~~

Đây là **snippet tích hợp**, cần import `ZoneId` và `CronExpression`. `ZoneId.of` báo `DateTimeException` khi ID không hợp lệ; `CronExpression.parse` báo lỗi với cron không hợp lệ. Validation sớm giúp không deploy một lịch sai ngầm.

**Chi tiết đáng chú ý của Spring 6.2:** khi Spring xử lý `@Scheduled.zone`, nó dùng utility `StringUtils.parseTimeZoneString` để diễn giải timezone. Utility này, khác với việc gọi thô `TimeZone.getTimeZone`, **từ chối nhiều chuỗi không hợp lệ bằng `IllegalArgumentException`** thay vì âm thầm rơi về GMT. Vì vậy không nên viết rằng `@Scheduled` chắc chắn dùng GMT nếu gõ sai tên zone. Tuy nhiên validate tường minh vẫn hữu ích để cung cấp lỗi cấu hình dễ hiểu và thống nhất toàn hệ thống.

Không áp dụng một bảng offset cứng như `UTC+7` cho mọi khách hàng và mọi thời điểm. Với zone có DST, offset có thể thay đổi theo ngày; timezone data có thể được cập nhật khi chính sách của quốc gia thay đổi.

## 12. DST: ngay cả khi ghim đúng zone, mốc local có thể mất hoặc trùng

Ghim zone giúp các instance đồng nhất **cách diễn giải lịch**, nhưng không làm mọi thời điểm dân sự tồn tại duy nhất:

- **DST gap (spring-forward):** đồng hồ nhảy từ 01:59 lên 03:00; giờ 02:30 **không tồn tại** vào ngày chuyển. Với một cron 02:30 trong zone đó, có thể không có tick tương ứng cho local date ấy.
- **DST overlap (fall-back):** đồng hồ đi lùi; giờ 01:30 có thể xuất hiện với hai offset khác nhau, tạo **hai `Instant`** ứng với một nhãn giờ địa phương.
- **Vietnam:** `Asia/Ho_Chi_Minh` theo quy tắc hiện hành không có DST theo mùa, nhưng code đa thị trường phải kiểm thử các zone khác.

Đối với job gửi báo cáo thông thường, việc bỏ qua một mốc DST có thể được chấp nhận. Với việc cần đúng **một lần mỗi ngày nghiệp vụ** (chốt sổ, tính phí, phát hóa đơn), hãy định nghĩa rõ cách xử lý gap/overlap: dời giờ, bỏ, chọn offset đầu/cuối, hay tạo job theo business date có unique key.

Không xem `cron+zone` là cơ chế exactly-once. Giữ các khóa nghiệp vụ như `(tenantId, businessDate, jobType)` để tránh chạy trùng ngay cả khi DST hoặc retry xảy ra.

## 13. Khi mỗi tenant có timezone riêng, @Scheduled cố định không còn đủ

`@Scheduled(zone="Asia/Ho_Chi_Minh")` gắn với **một lịch khai báo** cho một method. Nếu ứng dụng phục vụ nhiều khách hàng và mỗi người cần báo cáo 09:30 **theo timezone của họ**, một zone cố định là sai hợp đồng.

Một hướng thiết kế:

~~~text
TenantSchedule {
    tenantId
    localTime = 09:30
    zoneId = "America/New_York" hoặc "Asia/Ho_Chi_Minh"
    cron = "0 30 9 * * MON-FRI"
    nextDueAt = Instant (UTC)
    scheduleVersion
}
      |
      v
Domain scheduler tính nextDueAt bằng zoneId của tenant
      |
      v
Lưu job vào DB/queue với business key và trạng thái
      |
      v
Worker đến hạn đọc job và xử lý idempotent
~~~

Nếu muốn dùng Spring trực tiếp, `TaskScheduler.schedule(Runnable, Trigger)` có thể kết hợp một `CronTrigger(cron, ZoneId.of(tenantZone))`. Caller có thể giữ `ScheduledFuture` để hủy hoặc đăng ký lại khi tenant đổi zone. Nhưng **handle trong JVM không bền qua restart** và không tự ngăn các replica đăng ký trùng; job quan trọng cần lưu trạng thái và điều phối phân tán.

Khi tenant đổi timezone từ `Asia/Ho_Chi_Minh` sang `Europe/Paris`, cần quy tắc rõ: áp dụng từ business date nào, xử lý job đã lên lịch ra sao và có chạy bù không. Chỉ cập nhật một string trong DB mà không thay đổi lịch đã đăng ký có thể tạo sai lệch vận hành.

## 14. Phải phân biệt scheduling time, actual time và business date

Một cron có zone chuẩn vẫn có thể kích hoạt trễ. Ví dụ lịch 09:30 nhưng pool scheduler đang có tác vụ blocking nên callback thực sự bắt đầu 09:34.

Nên ghi ba loại thông tin:

| Trường | Đại diện cho |
| --- | --- |
| `scheduledAt` (UTC Instant) | Mốc dự kiến theo cron/zone |
| `startedAt` (UTC Instant) | Thời điểm thực tế worker bắt đầu |
| `businessDate` (LocalDate + ZoneId) | Ngày nghiệp vụ mà job xử lý |

`startedAt - scheduledAt` đo scheduling lag; nó không phải sai số do timezone nếu `scheduledAt` vốn đã được tính đúng. Khi dùng `@Scheduled` thuần, method không mặc nhiên nhận `scheduledAt` làm tham số; để cần kiểm toán chính xác từng expected run, ứng dụng có thể tự tính lịch từ job store/clock hoặc dùng scheduling infrastructure phù hợp.

Những yêu cầu như “chạy một lần cho mỗi business date”, “tự xử lý ngày bị bỏ lỡ” nên được thể hiện bằng **job ID và trạng thái đã xử lý**, không chỉ bằng một callback cron.

## 15. Deploy nhiều instance: cùng giờ không đồng nghĩa một lần thực thi

Ba replica cùng có `@Scheduled(cron=..., zone="Asia/Ho_Chi_Minh")` sẽ tính cùng lịch UTC, nhưng **mỗi replica có thể chạy method của riêng mình**. Ghim zone giải quyết việc mỗi nơi diễn giải 09:30 khác nhau; nó không cung cấp distributed lock.

Các lựa chọn:

- **Việc idempotent, được phép chạy nhiều lần:** mỗi replica có thể tự làm khi thực sự cần, hoặc các instance làm những phần độc lập.
- **Việc chỉ một instance nên thực hiện:** dùng leader election, distributed lock/lease hoặc job queue có phân phối work đúng thiết kế.
- **Việc không được bỏ lỡ sau downtime:** lưu next due/last processed business date trong database, có scanner/catch-up và reconciliation.
- **Việc có side effect quan trọng:** dùng unique constraint/idempotency key, transaction và bảo vệ khi consumer retry/worker crash.

Nếu replica A dùng timezone UTC và B dùng timezone Việt Nam nhưng cả hai **đều ghim zone trong cron**, lịch đồng nhất. Tuy nhiên các phiên bản code khác nhau hoặc cấu hình `jobs.cutoff.zone` khác nhau vẫn có thể làm divergence; vì vậy nên quản lý config qua deployment review và log giá trị hiệu lực.

## 16. Chuyện gì xảy ra khi thay zone lúc ứng dụng đang chạy?

`@Scheduled` thường đăng ký cron khi bean được xử lý trong quá trình khởi tạo Spring context. **Thay đổi giá trị property sau đó không mặc nhiên tái đăng ký lịch.**

Một ví dụ: lúc startup `jobs.cutoff.zone=Asia/Ho_Chi_Minh`, sau đó ConfigMap bị sửa thành `UTC`. Trong một ứng dụng không có cơ chế refresh scheduling chuyên dụng, trigger cũ có thể vẫn dùng zone Việt Nam. Việc code khác đọc property mới không làm `CronTrigger` đang active tự đổi quy tắc.

Nếu yêu cầu đổi runtime là chính thức, thiết kế quy trình:

1. Validate `cron` và `ZoneId` mới, lưu version/cấu hình có audit.
2. Tìm lịch hiện hành theo job ID.
3. `cancel(false)` handle cũ nếu dùng `TaskScheduler`; xác nhận ý nghĩa “không thêm tick” chứ không phải hủy side effect đang chạy.
4. Tạo trigger mới với `ZoneId` mới, lưu next due và generation/version.
5. Giải quyết race giữa callback cũ đang chạy và lịch mới, tránh tạo duplicate.
6. Nếu nhiều node, chuyển ownership/lease đúng cách; không chỉ thay một `ConcurrentHashMap` trong một JVM.

Đừng đổi `TimeZone.setDefault(...)` để giả lập hot reload zone cho một job; đó là thay đổi toàn process và có thể tác động các thư viện không liên quan.

## 17. Kiểm thử xuyên môi trường: chọn mốc cố định, không dùng giờ hiện tại

Bộ test trọng tâm của yêu cầu “không phụ thuộc server” phải chạy **cùng cron và cùng reference instant** qua ít nhất hai scheduler clock có zone khác nhau:

1. **Positive:** `CronTrigger(cron, ZoneId.of("Asia/Ho_Chi_Minh"))` phải cho cùng `Instant` dưới clock UTC và New York.
2. **Control:** `CronTrigger(cron)` không cấp zone sẽ cho mốc khác nếu clock zone khác, như demo.
3. **Parse cron:** kiểm tra đủ sáu trường và miền giá trị hợp lệ, không dùng nhầm cron Unix năm trường.
4. **Parse zone:** `ZoneId.of(zone)` phải nhận region ID hợp lệ; test typo và property thiếu.
5. **Integration annotation:** mở Spring context với `@EnableScheduling`, một lịch thử chạy trong thời hạn ngắn và xác minh scheduler bean/zone được đăng ký. Không đợi đến 09:30 thật chỉ để chạy unit test.
6. **Log format:** test `Instant` của lượt chạy và local display; không assert chuỗi log format theo timezone của OS.
7. **DST:** nếu business có region đổi giờ, kiểm tra gap/overlap với các mốc cố định và policy bù/dedupe.
8. **Cluster:** chạy hai instance có zone JVM khác nhau nhưng cùng config job; xác nhận mốc đều giống nhau, rồi test khóa/lease nếu phải một lần toàn cluster.
9. **Restart:** khởi động lại qua thời điểm cron để xác nhận chiến lược missed-run/catch-up, không mặc định `@Scheduled` sẽ tự phục hồi.
10. **Dependency overload:** giữ scheduler worker bằng latch và đo scheduling lag, tránh dùng `Thread.sleep(10)` tạo test giòn.

Một unit test cho timezone không cần database, network hay web server. Dùng `Clock.fixed` và `SimpleTriggerContext` như demo giúp kết quả xác định, không phụ thuộc hôm nay là ngày nào.

## 18. Logging và monitoring cần nói rõ zone của nghiệp vụ

Nếu log chỉ in:

~~~text
2026-10-29 02:30:00 INFO  CutoffJob started
~~~

thì câu hỏi “job chạy 02:30 hay 09:30?” không có lời giải rõ ràng khi thiếu timezone của timestamp.

Một event/log có cấu trúc nên tách:

~~~text
job=cutoff
cron="0 30 9 * * MON-FRI"
jobZone="Asia/Ho_Chi_Minh"
scheduledAtUtc="2026-10-29T02:30:00Z"
scheduledLocal="2026-10-29T09:30+07:00[Asia/Ho_Chi_Minh]"
actualStartedAtUtc="2026-10-29T02:30:01Z"
scheduleVersion=7
~~~

Đây là **mẫu dữ liệu log**, không phải stdout của chương trình demo. Các thời điểm ví dụ và số version chỉ để minh họa cấu trúc. Đừng giả định mọi method `@Scheduled` nhận sẵn `scheduledAtUtc`; một thiết kế cần audit expected-fire phải tính hoặc lưu thông tin đó từ scheduling infrastructure/job store.

Các metric nên có: `schedule_lag_seconds`, `scheduled_job_runs_total`, `scheduled_job_failures_total`, duration, missed business dates, retry count và duplicate count. Tránh dùng raw tenant/job ID làm label có cardinality cao; để ID trong tracing/log có chính sách bảo mật khi cần.

Khi chẩn đoán lệch giờ, so sánh `scheduledAtUtc` dự kiến với `actualStartedAtUtc`. Nếu lệch đúng số giờ của timezone, kiểm tra effective zone; nếu lệch vài phút và pool đang đầy, kiểm tra scheduling lag; nếu một job chạy nhiều lần cùng mốc, kiểm tra multi-instance và idempotency.

## 19. Các hiểu nhầm thường gặp

| Hiểu nhầm | Phản hồi chính xác |
| --- | --- |
| “Có cron đúng là đủ.” | Cron chỉ mô tả ngày/giờ; phải biết zone để chuyển sang `Instant`. |
| “Timezone server giống nhau mãi.” | Docker, Kubernetes, image hoặc config scheduler có thể thay đổi. |
| “`@Scheduled` luôn lấy timezone OS.” | Khi `zone` trống, scheduler clock quyết định; không nhất thiết chính là OS/JVM default trong mọi cấu hình. |
| “Đặt `-Duser.timezone=UTC` rồi viết cron theo giờ VN sẽ không sao.” | Nếu không ghim `zone`, cron được diễn giải theo zone scheduler, dễ lệch. |
| “Dùng `zone=UTC` rồi giữ cron 09:30 vẫn là 09:30 Việt Nam.” | `zone=UTC` nghĩa là 09:30 UTC; nếu nghiệp vụ là VN, dùng zone VN. |
| “`Asia/Ho_Chi_Minh` và `+07:00` hoàn toàn giống trong mọi thời kỳ.” | Region zone có quy tắc lịch sử/tương lai, offset cố định không diễn tả hết. |
| “Ghim zone khiến body method dùng đúng business date.” | `LocalDate.now()` hoặc DB `CURRENT_DATE` còn phụ thuộc timezone riêng. |
| “Timezone sai thì Spring chắc chắn âm thầm dùng GMT.” | Spring 6.2 dùng parser có kiểm tra và thường từ chối ID không hợp lệ; cần validate rõ từ lúc cấu hình. |
| “Ghim zone là job chạy đúng giờ từng mili giây.” | Scheduler load, CPU pause, GC và shutdown có thể gây trễ. |
| “Ghim zone bảo đảm job chỉ chạy một lần.” | Mỗi replica có thể đăng ký lịch, cần coordination/idempotency. |
| “Cron 02:30 tồn tại mỗi ngày ở mọi region.” | DST gap có thể làm local 02:30 không tồn tại. |
| “Cron 01:30 là duy nhất ở mọi ngày.” | DST overlap có thể tạo hai instants khác nhau. |
| “Sửa YAML zone lúc chạy là trigger tự cập nhật.” | Lịch đã đăng ký thường cần cancel/re-register hoặc restart. |
| “`TimeZone.setDefault` là cách đổi zone cho một tenant.” | Nó tác động đến trạng thái timezone toàn JVM, không đúng phạm vi từng job. |

## 20. Checklist trước khi triển khai

- Yêu cầu nghiệp vụ đã nêu rõ **giờ địa phương, ngày áp dụng và zone**.
- `@Scheduled` được bật trong đúng `ApplicationContext` bằng `@EnableScheduling` hoặc hạ tầng tương đương.
- Cron có đúng **sáu trường** theo Spring và đã được parse/test.
- Với lịch theo địa phương, `zone` là một **IANA region ID** rõ ràng hoặc `UTC` theo hợp đồng.
- Không dựa vào default timezone của máy chủ, JVM hay Docker image để định nghĩa ý nghĩa giờ nghiệp vụ.
- Zone/cron bên ngoài được validate và có version; không tự động áp một giá trị ngầm khi thiếu cấu hình quan trọng.
- Test dùng `Clock.fixed`, cùng `Instant` và hai default zones khác nhau; assert mốc UTC không đổi khi zone được ghim.
- Code trong job không vô ý lấy business date bằng `LocalDate.now()` ở default zone không liên quan.
- Với DST gap/overlap, đã có policy và test nếu zone nghiệp vụ có chuyển giờ.
- Log thể hiện UTC instant, local time và effective `ZoneId` để dễ chẩn đoán.
- Có chiến lược scheduler saturation, timeout, catch-up, shutdown và cảnh báo job lỗi.
- Nếu chạy nhiều replica hoặc có side effect lớn, có lease/lock, idempotency và lịch sử business date đã hoàn tất.
- Nếu zone thay đổi runtime, có quy trình hủy lịch cũ và đăng ký lịch mới an toàn.

**Kết luận:** Để một Spring cron chạy theo đúng giờ của nghiệp vụ, hãy **chỉ định `@Scheduled(zone = "Asia/Ho_Chi_Minh")` hoặc một `ZoneId` tường minh trong `CronTrigger`**, không ủy thác ý nghĩa lịch cho timezone mặc định của server. Test với cùng `Instant` qua các timezone môi trường sẽ chứng minh tính ổn định. Sau khi giải quyết đúng múi giờ, vẫn phải thiết kế riêng business date, DST, nhiều replica, sự cố scheduler và tính bền vững của công việc.

## Tài liệu tham khảo

- [Spring Framework 6.2 – @Scheduled Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html): default của `zone`, biểu thức cron và các thuộc tính annotation.
- [Spring Framework 6.2 – CronTrigger Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html): constructor có `ZoneId`, clock, `nextExecution`.
- [Spring Framework 6.2 – SimpleTriggerContext Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/SimpleTriggerContext.html): truyền `Clock` khi tính lịch trong test.
- [Spring Framework 6.2 – CronExpression Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html): cú pháp sáu trường và tính next timestamp.
- [Spring Framework – Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html): `@EnableScheduling`, `@Scheduled` và `TaskScheduler`.
- [Spring Framework – StringUtils.parseTimeZoneString Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/util/StringUtils.html): cơ chế từ chối timezone string không hợp lệ.
- [Java SE 17 – ZoneId](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZoneId.html): timezone region IDs và timezone rules.
- [Java SE 17 – Clock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html): fixed clock giúp test có thời điểm xác định.
- [Java SE 17 – Instant](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Instant.html): mốc thời gian UTC để so sánh xuyên hệ thống.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cron-timezones-007">Giờ bị bỏ qua hoặc lặp lại khi chuyển giờ mùa hè</a>

<details>
<summary>Xem chi tiết</summary>

# Giờ bị bỏ qua hoặc lặp lại khi chuyển giờ mùa hè

## 1. Vấn đề: cron theo giờ địa phương không phải lúc nào cũng có một lần mỗi ngày

Trong Spring Framework, `@Scheduled(cron = "...", zone = "...")` cho phép chạy tác vụ theo **thời gian trên đồng hồ của một múi giờ**. Một ngày địa phương có thể dài 23, 24 hoặc 25 giờ nếu vùng đó áp dụng **daylight saving time (DST, giờ mùa hè)**. Khi đồng hồ đổi giờ, một khoảng giờ địa phương có thể **không tồn tại** hoặc **xuất hiện hai lần**.

- **Spring forward / gap (giờ bị bỏ qua):** đồng hồ nhảy lên trước, chẳng hạn từ 01:59:59 lên 03:00:00. Không có 02:30 trong ngày này. Cron 02:30 không có một thời điểm thực nào để khớp.
- **Fall back / overlap (giờ lặp lại):** đồng hồ lùi, chẳng hạn từ 01:59:59 về 01:00:00. Cùng chuỗi 01:30 xuất hiện hai lần, nhưng gắn với hai UTC offset khác nhau và hai `Instant` khác nhau.
- **Zone không có DST vào ngày xét:** giờ địa phương thông thường ứng với một thời điểm thực; tuy nhiên không thể suy ra mọi nơi hay mọi năm đều có cùng quy tắc.

Tác động không chỉ là log hiển thị khác nhau. Một cron để khóa sổ ngày, gửi hóa đơn, chạy batch hoặc tính phí có thể **không có lần chạy** vào gap hoặc **có hai ứng viên kích hoạt** ở overlap. Nếu nghiệp vụ cần “mỗi ngày đúng một kết quả”, phải thiết kế thêm chính sách xử lý, idempotency và phục hồi khi thiếu việc.

**Nguyên tắc:** tách ba tầng: **quy tắc múi giờ** xác định local time có 0/1/2 offset; **Spring cron** tìm ứng viên tiếp theo; **scheduler + hệ thống nghiệp vụ** quyết định có thực thi, xử lý thiếu/trùng và xác nhận kết quả hay không. Không thể coi `@Scheduled` là bảo đảm exactly-once.

## 2. Instant, LocalDateTime, ZoneId, ZoneOffset khác nhau thế nào?

| Kiểu Java | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| `Instant` | `2026-11-01T05:30:00Z` | Một mốc thời gian tuyệt đối, duy nhất trên trục UTC |
| `LocalDateTime` | `2026-11-01T01:30` | Chỉ ngày-giờ đồng hồ, **không kèm zone/offset**, có thể thiếu hoặc lặp |
| `ZoneOffset` | `-04:00` hoặc `-05:00` | Độ lệch cụ thể so với UTC của một mốc |
| `ZoneId` | `America/New_York` | Tên vùng kèm **bộ quy tắc đổi offset** theo thời gian |
| `ZonedDateTime` | `2026-11-01T01:30-04:00[America/New_York]` | Local time + offset + quy tắc vùng, ánh xạ được thành Instant |
| `Duration` | `PT24H` | Khoảng thời gian thực 24 giờ, không đồng nghĩa mọi ngày lịch |
| `LocalDate` | `2026-11-01` | Ngày kinh doanh theo múi giờ đã chọn |

`LocalDateTime` không đủ để xác định một `Instant` trong trường hợp overlap. Một lần `01:30 -04:00` và một lần `01:30 -05:00` cách nhau một giờ trên UTC. Ngược lại, không có `Instant` nào ứng với `02:30` tại lúc gap.

**Không thay `America/New_York` bằng offset cố định `-05:00`.** New York dùng cả -05:00 và -04:00 ở các giai đoạn khác nhau; một offset cố định sẽ không phản ánh quy tắc DST. Tương tự, `Asia/Ho_Chi_Minh` và `UTC` là các zone khác nhau về giờ hiển thị; cron không tự hiểu “múi giờ người dùng” nếu chỉ biết cấu hình máy chủ.

Mốc thực tế nên được ghi log/lưu trữ bằng `Instant` hoặc timestamp UTC; khi cần nghiệp vụ địa phương, lưu thêm `ZoneId`, `businessDate` và chính sách diễn giải mốc lặp.

## 3. Ví dụ 2026 tại America/New_York: một giờ mất, một giờ lặp

### Ngày 08/03/2026: spring forward

```text
2026-03-08 01:59:59 -05:00
                  |
                  | một giây thực trôi qua
                  v
2026-03-08 03:00:00 -04:00

Khoảng local 02:00:00 ... 02:59:59 không tồn tại.
```

Tại `2026-03-08 02:30`, `ZoneRules.getValidOffsets(...)` trả **0** offset. Một cron:

```java
@Scheduled(cron = "0 30 2 * * *", zone = "America/New_York")
public void runDaily() {
    // Nghiệp vụ.
}
```

sẽ không có thời điểm 02:30 ngày đó để thỏa cron theo wall-clock. Đây là **mảnh Java tích hợp Spring**, cần `@EnableScheduling` và Spring-managed bean. Nó không tự cam kết chuyển sang 03:00 hay 03:30; **chạy bù là chính sách phải định nghĩa riêng**.

### Ngày 01/11/2026: fall back

```text
2026-11-01 01:30 -04:00 (EDT) = 2026-11-01T05:30:00Z
          ... 01:59:59 -04:00
                  |
                  v
          ... 01:00:00 -05:00 (EST)
2026-11-01 01:30 -05:00 (EST) = 2026-11-01T06:30:00Z
```

`2026-11-01 01:30` có hai offset hợp lệ `[-04:00, -05:00]`. Cron:

```java
@Scheduled(cron = "0 30 1 * * *", zone = "America/New_York")
public void runAtOneThirty() {
    // Nghiệp vụ.
}
```

có thể có hai **ứng viên mốc lịch** trong ngày. Với `CronExpression.next(ZonedDateTime)` của **Spring Framework 6.2.9** được chạy kiểm chứng, hai ứng viên lần lượt là:

1. `2026-11-01T01:30-04:00[America/New_York]`
2. `2026-11-01T01:30-05:00[America/New_York]`

Hai mốc cách nhau 60 phút thực. **Không suy ra `@Scheduled` chắc chắn gọi method đúng hai lần**: lịch thực tế còn phụ thuộc `CronTrigger`, completion time, scheduler bận hay down, và cấu hình của phiên bản Spring.

### So sánh với Việt Nam

`Asia/Ho_Chi_Minh` không có DST gap/overlap tại các ngày của ví dụ này. Với `2026-03-08 02:30`, zone Việt Nam có **một** offset hợp lệ. Tuy nhiên việc ứng dụng triển khai tại Việt Nam không đồng nghĩa server luôn chạy JVM timezone Việt Nam; nên khai báo zone theo **hợp đồng thời gian nghiệp vụ**.

## 4. Cron Spring có sáu trường và dùng zone để hiểu giờ địa phương

Một biểu thức cron Spring có dạng:

```text
second minute hour day-of-month month day-of-week
   0     30    2        *          *       *
```

Nó khác với nhiều cron Unix năm trường. `0 30 2 * * *` là 02:30:00 hằng ngày **trong zone đã chọn**, không tự kèm một mốc UTC cố định.

Cấu hình rõ múi giờ:

```java
@Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Ho_Chi_Minh")
public void startBusinessDay() {
    // Tác vụ từ thứ Hai tới thứ Sáu lúc 09:00 giờ Việt Nam.
}
```

Đây là **mảnh minh họa**, cần Spring-managed bean và `@EnableScheduling`. Nếu để `zone = ""`, Spring dùng zone theo scheduler clock/default, có thể khác nhau trên những máy hoặc container khác nhau. Khi lấy tên zone từ config, nên validate bằng `ZoneId.of(value)` để phát hiện lỗi chính tả; đừng dựa vào một API có thể fallback về GMT khi tên vùng không hợp lệ.

Ngoài timezone, cần biết giờ cron **không kiểm soát số lần business side effect xảy ra**. Nhiều replicas có nhiều scheduler riêng; callback có thể chạy trễ, bị lỗi hoặc bị hủy trong lúc shutdown. Nên dùng cron để xác định ứng viên, không coi đó là hệ thống giao job bền vững.

## 5. ZoneRules giải thích gap, normal và overlap

Java 17 có `ZoneId.getRules().getValidOffsets(localDateTime)`:

- **0 offset:** local time nằm trong **gap**, không có mốc UTC tương ứng.
- **1 offset:** local time hợp lệ không mơ hồ.
- **2 offset:** local time nằm trong **overlap**, có hai mốc UTC.

```java
ZoneId zone = ZoneId.of("America/New_York");
LocalDateTime gap = LocalDateTime.of(2026, 3, 8, 2, 30);
LocalDateTime overlap = LocalDateTime.of(2026, 11, 1, 1, 30);

List<ZoneOffset> missing = zone.getRules().getValidOffsets(gap);
List<ZoneOffset> repeated = zone.getRules().getValidOffsets(overlap);
```

Đây là **mảnh Java 17** với imports `java.time.*` và `java.util.List`. Nhớ xét `size()` trước khi chọn `get(0)` hoặc `get(1)`.

Một bẫy: `LocalDateTime.atZone(zone)` **có thể tự điều chỉnh** một local time bị gap, ví dụ 02:30 sang 03:30. Đây là chính sách resolve của Java khi chuyển local time sang zoned time, **không phải** quy tắc cron chắc chắn tự chạy bù 03:30. Muốn có chính sách gap rõ ràng, hãy kiểm tra valid offsets trước khi tạo thời điểm thực.

## 6. Ví dụ Java 17+ và Spring 6.2.9 có thể chạy

Chương trình sau **không cần đợi tới ngày chuyển DST**. Nó dùng Java Time API xác định gap/overlap và `CronExpression.next` của Spring 6.2.9 tính các ứng viên cron. Ví dụ đã được chạy kiểm chứng bằng JShell với Spring 6.2.9 và JDK; mười dòng stdout khớp dữ liệu ở dưới.

Cấu trúc:

```text
cron-dst-demo/
├── pom.xml
└── src/main/java/demo/CronDstDemo.java
```

Nội dung `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>cron-dst-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.9</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

Nội dung `src/main/java/demo/CronDstDemo.java`:

```java
package demo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.scheduling.support.CronExpression;

public final class CronDstDemo {

    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("America/New_York");
        LocalDateTime gap = LocalDateTime.of(2026, 3, 8, 2, 30);
        LocalDateTime overlap = LocalDateTime.of(2026, 11, 1, 1, 30);
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(overlap);

        System.out.println("gap offsets = "
                + zone.getRules().getValidOffsets(gap).size());
        System.out.println("overlap offsets = " + offsets);

        System.out.println("first instant = "
                + ZonedDateTime.ofLocal(overlap, zone, offsets.get(0))
                        .toInstant());
        System.out.println("second instant = "
                + ZonedDateTime.ofLocal(overlap, zone, offsets.get(1))
                        .toInstant());

        CronExpression morning = CronExpression.parse("0 30 2 * * *");
        ZonedDateTime springStart =
                ZonedDateTime.of(2026, 3, 7, 2, 30, 0, 0, zone);
        System.out.println("spring next = " + morning.next(springStart));

        CronExpression repeated = CronExpression.parse("0 30 1 * * *");
        ZonedDateTime fallStart =
                ZonedDateTime.of(2026, 10, 31, 1, 30, 0, 0, zone);
        ZonedDateTime first = repeated.next(fallStart);
        ZonedDateTime second = repeated.next(first);

        System.out.println("fall first = " + first);
        System.out.println("fall second = " + second);
        System.out.println("same local time = "
                + first.toLocalDateTime().equals(second.toLocalDateTime()));
        System.out.println("minutes apart = "
                + Duration.between(first.toInstant(), second.toInstant())
                        .toMinutes());
        System.out.println("HCM valid offsets = "
                + ZoneId.of("Asia/Ho_Chi_Minh")
                        .getRules().getValidOffsets(gap).size());
    }
}
```

Chạy với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.CronDstDemo
```

Stdout của chương trình:

```text
gap offsets = 0
overlap offsets = [-04:00, -05:00]
first instant = 2026-11-01T05:30:00Z
second instant = 2026-11-01T06:30:00Z
spring next = 2026-03-09T02:30-04:00[America/New_York]
fall first = 2026-11-01T01:30-04:00[America/New_York]
fall second = 2026-11-01T01:30-05:00[America/New_York]
same local time = true
minutes apart = 60
HCM valid offsets = 1
```

Giải thích:

1. `gap offsets = 0`: không có 02:30 ở New York vào 08/03/2026.
2. `overlap offsets = [-04:00, -05:00]`: có hai lần 01:30 vào 01/11/2026.
3. Hai `Instant` cách nhau một giờ; log chỉ chứa local date/time sẽ bỏ mất khác biệt.
4. Spring 6.2.9 `CronExpression.next` bỏ qua 08/03 khi tìm 02:30 tiếp theo sau 07/03, trả 09/03.
5. Spring 6.2.9 `CronExpression.next` tìm ra cả hai 01:30 với offset khác nhau ở fall back.
6. `HCM valid offsets = 1`: cùng local time không có gap tại zone Việt Nam ở ngày dùng thử.

**Phạm vi chứng minh:** đây là kiểm tra **ứng viên thời gian**, không phải chạy `@Scheduled` thực trên các ngày DST, không đo throughput và không xác nhận cron đã giao một job bền vững. Với JDK tzdb hoặc Spring version khác, hãy chạy lại regression test thay vì mặc định giữ mọi hành vi.

## 7. Vì sao @Scheduled thực tế không luôn chạy đủ hai mốc overlap?

Spring sử dụng `CronTrigger`/scheduler để xác định lần gọi callback thực tế. Với kiểu cron lenient thông thường, mốc kế tiếp dựa trên **thời điểm callback trước hoàn tất**. Nếu callback đầu tiên kéo dài đến quá mốc thứ hai, mốc thứ hai có thể bị bỏ qua dù `CronExpression` có thể tính được khi hỏi từ một thời điểm sớm hơn.

Ngoài ra, ứng dụng có thể:

- Chưa chạy, đang khởi động hoặc đang dừng tại thời điểm cron.
- Có scheduler thread bận, callback bị trễ hoặc không được phát theo chính sách hiện hành.
- Dùng `@Async` hay dispatch sang executor khác; callback scheduled hoàn tất trước lúc business job hoàn tất.
- Có nhiều instance, mỗi instance có `@Scheduled` riêng.
- Có nhiều annotation `@Scheduled` trên cùng method, mỗi cái là một lịch độc lập.
- Dùng custom `TaskScheduler` hoặc trigger có cách xử lý missed fire khác.

Nếu phiên bản Spring hỗ trợ `CronTrigger.forLenientExecution(...)` và `forFixedExecution(...)`, cần hiểu khác biệt giữa mốc kế tiếp theo **completion time** và theo **scheduled time**. Nhưng cả hai vẫn là scheduling trong tiến trình; **không phải hệ thống durable replay** sau crash khi không có kho trạng thái bổ sung.

Vì vậy hãy nói **“Spring 6.2.9 CronExpression tính ra hai ứng viên 01:30”**, không hứa mọi `@Scheduled` listener hoàn tất hai lần. Nếu business yêu cầu một kết quả/ngày, trách nhiệm xác nhận completion phải nằm ở lớp nghiệp vụ bền vững.

## 8. Giờ không tồn tại khác với lịch bị bỏ lỡ khi hệ thống down

| Hiện tượng | Nguyên nhân | Biện pháp |
| --- | --- | --- |
| 02:30 ở ngày spring forward | **Không có Instant nào** cho local time ấy | Chính sách skip, dời mốc, hoặc backfill theo business date |
| 09:00 hợp lệ nhưng ứng dụng down | Có Instant hợp lệ, nhưng scheduler không hoạt động | Persistent job store, reconcile, restart recovery |
| 09:00 hợp lệ nhưng scheduler quá tải | Callback bị trì hoãn hoặc bỏ theo trigger | Tách scheduler/executor, đo latency và completion |
| 01:30 ở ngày fall back | **Hai Instant** cùng local time | Chọn earlier/later offset, chạy cả hai hoặc dedup một lần/ngày |
| Hai JVM cùng có @Scheduled | Nhiều scheduler độc lập | Distributed claim và idempotency |
| Consumer retry sau side effect | Một intent có nhiều attempts | Idempotency key và transaction/ack phù hợp |

**Không gộp hai loại “miss”**: gap là vấn đề **lịch địa phương không tồn tại**; outage là một **lịch hợp lệ nhưng chưa được hệ thống xử lý**. Bộ máy retry nên biết nó đang phục hồi loại nào, không âm thầm nhân đôi việc.

## 9. Chọn chính sách cho giờ bị bỏ qua

Với `02:30` không tồn tại vào ngày DST gap, có các lựa chọn hợp lệ:

**A. Bỏ qua ngày đó.** Hợp nếu công việc không bắt buộc mỗi ngày, ví dụ refresh dữ liệu tạm và chu kỳ sau có thể cập nhật. Không hợp với khóa sổ hay tính phí bắt buộc.

**B. Chạy tại mốc hợp lệ sớm nhất sau gap.** Với ví dụ New York, đó là khoảng `03:00`. Hệ thống phải xác định nó đang thực thi **đại diện cho 02:30 đã bị bỏ qua**, không phải một cron 03:00 bình thường.

**C. Dời giờ theo độ dài gap.** Với gap một giờ, `02:30` được dời thành `03:30`. Đây là chính sách khác B; Java `atZone` có thể thực hiện adjustment kiểu này, nhưng nghiệp vụ không nên chấp nhận ngầm.

**D. Đảm bảo một job cho mỗi business date** bằng ledger/reconciliation, có thể chọn một giờ dễ vận hành hơn như 04:15, nhưng **chính ledger** mới cho biết ngày nào còn thiếu. Điều này cũng xử lý được application downtime.

Mảnh Java Time API để phát hiện gap:

```java
ZoneId zone = ZoneId.of("America/New_York");
LocalDateTime desired = LocalDateTime.of(2026, 3, 8, 2, 30);

var rules = zone.getRules();
var offsets = rules.getValidOffsets(desired);

if (offsets.isEmpty()) {
    var transition = rules.getTransition(desired);
    // transition.getDateTimeAfter() là mốc local hợp lệ đầu sau gap.
    // Lựa chọn chuyển 03:00 hay 03:30 là chính sách nghiệp vụ.
}
```

Đây chỉ là **mảnh Java 17**, cần chương trình điều phối thật để lưu planned date, chọn Instant, kiểm soát retry và tránh trùng. Đừng thay đổi thời gian chạy trong hệ thống tài chính mà không xác nhận yêu cầu nghiệp vụ.

## 10. Chính sách cho giờ lặp

Tại fall back, người dùng chọn 01:30 có thể muốn:

- **Một lần cho mỗi ngày địa phương:** chỉ một trong hai thời điểm 01:30 được chấp nhận; dùng `businessDate` để dedup. Có thể chọn earlier/later theo chính sách hoặc để cả hai trigger đến nhưng chỉ một claim thành công.
- **Hai lần vì đồng hồ thực sự chạm 01:30 hai lần:** lưu hai `scheduledInstant`, xử lý cả hai nếu đúng yêu cầu.
- **Chạy theo 24 giờ thực:** sử dụng interval/`Instant` thay vì một cron wall-clock “01:30 mỗi ngày”, chấp nhận giờ hiển thị địa phương thay đổi.
- **Từ chối giờ mơ hồ:** nếu sản phẩm không hỗ trợ quyết định này, validation cấu hình giờ trong vùng DST có thể yêu cầu user chọn giờ khác hoặc offset cụ thể.

Chọn `ZoneOffset` khi cần phân biệt:

```java
LocalDateTime local = LocalDateTime.of(2026, 11, 1, 1, 30);
ZoneId zone = ZoneId.of("America/New_York");
List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);

ZonedDateTime first = ZonedDateTime.ofLocal(local, zone, offsets.get(0));
ZonedDateTime second = ZonedDateTime.ofLocal(local, zone, offsets.get(1));
```

Đây là **mảnh Java 17** với imports `java.time.*` và `java.util.List`; cần kiểm tra `offsets.size() == 2` trước khi truy cập cả hai phần tử. Hai mốc có cùng local time nhưng có `toInstant()` khác nhau.

## 11. Idempotency theo ngày và theo occurrence

Nếu business yêu cầu **một báo cáo cho mỗi tenant và business date**, khóa hợp lý là:

```text
(job_type, tenant_id, business_date)
```

Ví dụ `DAILY_RECONCILIATION / tenant-42 / 2026-11-01`. Hai callback lúc 01:30 -04:00 và 01:30 -05:00 đều cố tạo cùng khóa; chỉ một được phép tạo intent/job nếu có unique constraint và transaction.

Nếu yêu cầu **cả hai occurrence**, chọn:

```text
(job_type, tenant_id, scheduled_instant)
```

Lúc đó mốc `05:30Z` và `06:30Z` khác nhau và có thể cùng hợp lệ. Không chọn khóa chỉ theo `LocalDateTime` vì nó không phân biệt được hai lần 01:30.

Ví dụ PostgreSQL cho once-per-business-day:

```sql
CREATE TABLE daily_runs (
    job_type text NOT NULL,
    tenant_id text NOT NULL,
    business_date date NOT NULL,
    status text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (job_type, tenant_id, business_date)
);
```

Một lần tick thử tạo intent:

```sql
INSERT INTO daily_runs(job_type, tenant_id, business_date, status)
VALUES ('DAILY_RECONCILIATION', 'tenant-42', DATE '2026-11-01', 'PENDING')
ON CONFLICT DO NOTHING;
```

Đây là **mẫu SQL minh họa**, chưa phải job engine hoàn chỉnh. Trong thực tế phải kiểm tra số rows được insert, claim công việc nguyên tử, có lease/status/retry và xử lý công việc idempotent. Nếu worker đã gửi một side effect rồi chết trước khi đánh dấu `DONE`, sau restart có thể retry; unique intent giúp đối soát nhưng side effect bên ngoài DB vẫn cần idempotency key hoặc cơ chế bù riêng.

## 12. Mẫu Spring: cron chỉ đánh thức bộ đối soát business date

Nếu nghiệp vụ “mỗi ngày có một kết quả” quan trọng hơn “callback chạy đúng một lần”, một service có thể để cron kích hoạt việc **tìm những ngày còn thiếu**:

```java
@Component
public class DailyReconciliationTicker {
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("America/New_York");

    private final ReconciliationCoordinator coordinator;

    public DailyReconciliationTicker(
            ReconciliationCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(cron = "0 15 4 * * *", zone = "America/New_York")
    public void trigger() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        coordinator.reconcileMissingDatesUpTo(today.minusDays(1));
    }
}
```

Đây là **mảnh tích hợp Spring**: cần `@EnableScheduling`, imports `@Component`, `@Scheduled`, `ZoneId`, `LocalDate` và `ReconciliationCoordinator` do ứng dụng định nghĩa. Mẫu xử lý đến **hôm qua** để tránh coi ngày hiện tại chưa đầy đủ đã hoàn tất.

`ReconciliationCoordinator` trong hệ thống thật cần:

1. Đọc job ledger bền vững để biết business dates nào còn thiếu `COMPLETED`.
2. Atomic claim theo `(job_type, tenant_id, business_date)`, kể cả khi có nhiều replicas.
3. Submit xử lý vào worker/broker phù hợp, không giữ scheduler thread làm toàn bộ việc nặng.
4. Ghi completion bền vững sau side effect cần bảo đảm; lỗi có thể retry có giới hạn.
5. Backfill các ngày thiếu sau downtime dài (có giới hạn số ngày/lần để tránh overload).
6. Log planned business date, Instant thực thi, retry attempts và instance đã claim.

Giờ 04:15 trong ví dụ ít gặp gap ở New York hiện tại, nhưng **không phải “giờ an toàn” với mọi zone/năm**. Giá trị chính của thiết kế là ledger + reconciliation, không phải đổi cron sang 04:15.

## 13. UTC, fixed rate và “mỗi ngày địa phương” không tương đương

| Yêu cầu | Mô hình phù hợp | Lưu ý |
| --- | --- | --- |
| 09:00 mỗi ngày New York | Cron `zone="America/New_York"` | Mốc UTC thay đổi theo DST |
| 09:00 mỗi ngày UTC | Cron `zone="UTC"` | Giờ địa phương New York thay đổi |
| Mỗi 24 giờ thực | Duration/interval theo Instant | Không nhất thiết cùng local time mỗi ngày |
| Mỗi ngày có một kết quả | LocalDate theo business zone + ledger | Tự backfill gap/downtime |
| Mỗi occurrence ở 01:30 | Lưu `scheduledInstant` và offset | Có thể có hai lần vào ngày fold |
| Một lần trong ngày fold | Unique key theo business date | Đừng nhầm duplicate trigger với duplicate business result |

`Duration.ofDays(1)` là 24 giờ thực. Một ngày lịch địa phương có thể dài 23 hoặc 25 giờ, nên cộng `Duration.ofDays(1)` không tự có nghĩa “ngày mai cùng giờ địa phương”. `ZonedDateTime.plusDays(1)` thể hiện phép cộng ngày lịch theo zone, nhưng bản thân resolver cho gap/overlap cũng cần được hiểu và phù hợp chính sách.

`@Scheduled(fixedRate = 86_400_000)` không đồng nghĩa cron 09:00 hằng ngày theo zone DST; nó biểu diễn chu kỳ định kỳ theo thời lượng. `fixedDelay` còn đo khoảng cách giữa **kết thúc callback trước** và **bắt đầu callback sau**, không phải cam kết mốc giờ địa phương.

## 14. Logging và dữ liệu lưu trữ phải phân biệt hai lần 01:30

Nên ghi:

- `scheduledInstant`: mốc dự kiến trên UTC (nếu có).
- `startedInstant`, `finishedInstant`: thời điểm bắt đầu/kết thúc thực.
- `zoneId`: ví dụ `America/New_York`.
- `localScheduledTime` và `businessDate`.
- `offset` hoặc `scheduledInstant` để phân biệt hai lần fold.
- `jobKey`/idempotency key và `attempt`/instance ID để phân biệt retries với trigger riêng.

Ví dụ:

```text
job=DAILY_REPORT local=2026-11-01T01:30 offset=-04:00 instant=2026-11-01T05:30:00Z
job=DAILY_REPORT local=2026-11-01T01:30 offset=-05:00 instant=2026-11-01T06:30:00Z
```

Hai dòng này không nhất thiết là lỗi; chúng là hai occurrence khác nhau. Cần business key để biết có được phép cả hai hay không.

Nếu dùng PostgreSQL, `timestamptz` lưu một mốc thời gian tuyệt đối; cách hiển thị phụ thuộc session timezone. Nếu muốn lưu cả “zone người dùng” và “business date”, nên có cột riêng. `timestamp without time zone` không mang offset nên không đủ để phân biệt hai lần 01:30.

Không chỉ dựa vào `LocalDateTime` làm ID của một task cron đã được lên lịch, cũng không dùng một tên thread để suy ra loại trigger.

## 15. Multi-instance, transaction và restart

DST không phải nguồn duy nhất gây job trùng/thiếu:

- **Nhiều JVM:** mỗi instance có Spring scheduler riêng, cùng cron có thể được kích hoạt đồng thời.
- **Server down:** các mốc hợp lệ trong lúc ứng dụng không chạy có thể không được phục hồi bởi `@Scheduled` mặc định.
- **Retry:** một side effect đã thành công nhưng task ghi trạng thái thất bại có thể được thực hiện lại.
- **Executor overload:** scheduler callback có thể chạy nhưng worker queue đầy; một tick accepted không phải completion receipt.
- **CronTrigger last completion:** callback lâu có thể làm bỏ mốc kế tiếp ngay cả ngoài DST.
- **DST overlap:** riêng giờ địa phương lặp lại có thể tạo thêm một ứng viên.

Nếu cần chính xác một **kết quả nghiệp vụ** mỗi ngày trên cluster, hãy dùng database unique key, atomic claim, status ledger, idempotent consumer và reconciliation. Distributed lock giúp điều phối nhưng **không tạo durable job** và không ngăn side effect đã thực hiện bị lặp sau crash nếu thiếu idempotency.

Nếu policy là hai occurrence hợp lệ, mỗi occurrence cần một ID/Instant ổn định để retry không tạo việc mới vô hạn. Nếu policy là once-per-business-day, calendar date theo zone phải là thành phần khóa.

## 16. Kiểm thử DST không cần đổi đồng hồ hệ thống

Sử dụng `CronExpression` và `ZoneRules` thay vì chờ tới ngày chuyển DST:

| Test | Dữ liệu đầu vào | Kỳ vọng |
| --- | --- | --- |
| Spring forward gap | New York, 08/03/2026 02:30 | `getValidOffsets` trả 0 |
| Next cron sau gap | `0 30 2 * * *`, bắt đầu 07/03 02:30 | Spring 6.2.9 trả 09/03 02:30 -04 |
| Fall back overlap | New York, 01/11/2026 01:30 | Hai offset -04 và -05 |
| Next cron lúc fold | `0 30 1 * * *` từ 31/10 | Spring 6.2.9 trả hai mốc 01:30 khác offset |
| Zone khác | Asia/Ho_Chi_Minh | Một offset ở local time thử nghiệm |
| Once-per-business-day | Hai trigger cùng business date | Unique key chỉ tạo một intent |
| Per-occurrence | Hai Instant khác nhau trong overlap | Có thể tạo hai intent nếu policy cho phép |
| Restart | Process down qua một business date | Ledger tìm được ngày còn thiếu |
| Multi-instance | Hai replicas claim cùng job | Chỉ một atomic claim thành công |
| Nâng cấp | Thay Spring hoặc tzdb | Kết quả vẫn phù hợp hợp đồng nghiệp vụ |

**Tầng unit test thời gian** chỉ kiểm tra candidates. **Tầng integration test** phải kiểm tra completion, transaction, idempotency, replay, quá tải và quyền sở hữu job. Một `CronExpression.next` trả mốc không có nghĩa `@Scheduled` đã chạy callback ở production.

Không phải mọi zone chuyển DST lúc 02:00 hoặc có gap đúng một giờ. Quy tắc có thể đổi trong lịch sử và cả tương lai; dùng `ZoneRules.getTransition` và tzdb phù hợp, không hardcode một khung 02:00–03:00 cho mọi quốc gia.

Đã có sửa lỗi Spring Framework liên quan `CronExpression` bỏ ngày khi DST gap xảy ra ở nửa đêm trong nhánh **6.2.19**. Vì vậy, khi upgrade, kiểm thử đúng zone/vùng giờ đang vận hành; kết quả đã chạy với Spring **6.2.9** trong bài không phải cam kết chung của mọi version.

## 17. Các ngộ nhận cần tránh

1. **“Một cron theo giờ địa phương luôn có đúng một mốc mỗi ngày.”** Không đúng tại gap/fold.
2. **“Hai log cùng 01:30 chứng minh hệ thống gọi trùng.”** Phải kiểm tra offset và `Instant`.
3. **“Spring @Scheduled mặc định chạy theo zone của user.”** Không; phải cấu hình `zone` hoặc scheduler clock.
4. **“Lấy `LocalDateTime.atZone` là đủ để biết giờ có tồn tại.”** Resolver có thể điều chỉnh gap; cần `getValidOffsets`.
5. **“Cron tự bù mọi giờ bị mất lúc 03:00.”** Không nên giả định; phải kiểm tra implementation và business policy.
6. **“`CronExpression.next` trả hai mốc nên callback luôn chạy hai lần.”** Không suy ra được khi scheduler bận/down.
7. **“Dùng UTC vẫn có 09:00 New York cả năm.”** Local time thay đổi theo DST.
8. **“`fixedRate` 24 giờ là một lần mỗi ngày địa phương.”** Ngày DST có thể không dài 24 giờ.
9. **“Offset -05:00 tương đương America/New_York.”** Zone còn chứa luật DST theo ngày.
10. **“Unique key bằng ngày-giờ địa phương luôn đủ.”** Nó không phân biệt hai occurrences trong fold.
11. **“Distributed lock đảm bảo exactly-once side effect.”** Cần transaction, idempotency và recovery.
12. **“Chọn 04:00 giải quyết DST vĩnh viễn cho mọi vùng.”** Không có giờ phổ quát như vậy.
13. **“Cron mất mốc do server down và gap là một vấn đề.”** Hai nguyên nhân khác nhau.
14. **“Tzdb và Spring parser luôn không đổi.”** Quy tắc và implementation có thể được nâng cấp.
15. **“Một scheduled tick thành công đồng nghĩa báo cáo đã có.”** Cần completion state của nghiệp vụ.
16. **“Có một scheduler cho cả cluster.”** Spring scheduling thường là theo từng ApplicationContext/JVM.

## 18. Tự kiểm tra kiến thức

**Câu 1.** Vì sao New York không có 02:30 ngày 08/03/2026?  
**Đáp án:** Đồng hồ nhảy từ gần 02:00 lên 03:00 khi DST bắt đầu. `ZoneRules.getValidOffsets` trả danh sách rỗng.

**Câu 2.** Hai lần 01:30 ngày 01/11/2026 khác nhau ở đâu?  
**Đáp án:** Một lần -04:00 ứng với 05:30Z, lần kia -05:00 ứng với 06:30Z; cách nhau 60 phút thực.

**Câu 3.** `CronExpression.next` của Spring 6.2.9 tìm lịch 02:30 tiếp theo sau 07/03/2026 như thế nào?  
**Đáp án:** Nó bỏ qua 08/03 có 02:30 không tồn tại và trả `2026-03-09T02:30-04:00[America/New_York]`.

**Câu 4.** `CronExpression.next` đã có hai ứng viên 01:30 thì có hứa `@Scheduled` sẽ xử lý thành công đúng hai lần không?  
**Đáp án:** Không. Còn last completion, executor, uptime, restart và số replicas.

**Câu 5.** Một bảng báo cáo cần đúng một row cho mỗi ngày kinh doanh. Unique key nên dựa vào gì?  
**Đáp án:** `(job_type, tenant_id, business_date)` với atomic claim và idempotent worker; không chỉ dựa vào số callback.

**Câu 6.** Nếu nghiệp vụ cố ý xử lý cả hai lần 01:30, cần lưu gì?  
**Đáp án:** Hai `scheduledInstant`/offset phân biệt, cùng job key per occurrence và retry idempotent.

**Câu 7.** `Duration.ofDays(1)` có luôn giữ cùng giờ địa phương sang ngày hôm sau không?  
**Đáp án:** Không; 24 giờ thực không luôn bằng một ngày lịch địa phương ở DST.

**Câu 8.** Chỉ có `@Scheduled` có đảm bảo chạy bù khi server ngừng ở mốc cron không?  
**Đáp án:** Không; cần persistent ledger/job store và reconciliation phù hợp.

**Câu 9.** Tại sao phải kiểm thử DST lại sau khi nâng cấp Spring hoặc JDK tzdb?  
**Đáp án:** Parser/trigger và quy tắc zone có thể thay đổi, nhất là với gap ở giờ bất thường hoặc luật DST mới.

**Ghi nhớ:** **LocalDateTime có thể ánh xạ tới 0, 1 hoặc 2 Instants; ZoneRules cho biết trường hợp nào.** Cron Spring diễn giải giờ địa phương theo zone, còn kết quả nghiệp vụ đúng một lần cần một chính sách rõ ràng về gap/overlap, transaction, idempotency và phục hồi.

### Tài liệu tham khảo

- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [Spring Framework 6.2.9 — @Scheduled JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
- [Spring Framework 6.2.9 — CronExpression JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/support/CronExpression.html)
- [Spring Framework 6.2.9 — CronTrigger JavaDoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/scheduling/support/CronTrigger.html)
- [Spring Framework — DST midnight gap fix #36873](https://github.com/spring-projects/spring-framework/issues/36873)
- [Java SE 17 — ZoneRules](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/zone/ZoneRules.html)
- [Java SE 17 — ZonedDateTime](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html)
- [Java SE 17 — ZoneOffsetTransition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/zone/ZoneOffsetTransition.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cron-timezones-008">Không suy rộng cú pháp cron của hệ điều hành hoặc Quartz sang Spring</a>

<details>
<summary>Xem chi tiết</summary>

# Không suy rộng cú pháp cron của hệ điều hành hoặc Quartz sang Spring

Khi cấu hình công việc định kỳ trong Spring Framework, rất dễ sao chép một cron expression từ Linux `crontab`, Quartz Scheduler hoặc một công cụ tạo cron trực tuyến rồi đặt thẳng vào `@Scheduled(cron = "...")`. **Đây là một sai lầm có thể khiến ứng dụng không khởi động, lịch chạy sai giờ hoặc chạy nhầm ngày trong tuần.**

Dù cùng gọi là *cron*, các dialect khác nhau ở **số trường**, **thứ tự trường**, **cách đánh số thứ trong tuần**, **các ký hiệu mở rộng**, **trường năm**, **múi giờ**, **quy tắc trùng điều kiện ngày** và **chính sách thực thi**. Một biểu thức có thể hợp lệ ở công cụ A nhưng không hợp lệ ở Spring; nguy hiểm hơn, **nó vẫn hợp lệ trong Spring nhưng có ý nghĩa khác**.

Bài Knowledge thuộc nhóm **Cron và múi giờ** của module **Spring Framework – concurrency**, tập trung vào việc **đọc, chuyển đổi và kiểm chứng đúng Spring cron**. Mục tiêu không phải học thuộc mọi biến thể cron trên thế giới, mà là biết đâu là hợp đồng chính thức của Spring Framework 6.x và cách xác minh lịch thực tế trước khi deploy.

## 1. Ba hệ thống dùng “cron” nhưng không cùng một ngôn ngữ

| Nơi sử dụng | Dạng điển hình | Điểm quan trọng |
| --- | --- | --- |
| Linux/Unix user `crontab` truyền thống | **5 trường thời gian** + lệnh hệ điều hành | Không có trường giây trong năm trường thời gian chuẩn |
| Java Quartz Scheduler `CronTrigger` | **6 trường bắt buộc** + **trường năm tùy chọn** | Có giây, có thể có năm; DOW thường đánh số `1=SUN` tới `7=SAT` |
| Spring Framework `@Scheduled(cron=...)` / `CronExpression` | **Đúng 6 trường** | Có giây, **không có trường năm**; DOW `0 hoặc 7 = SUN`, `1=MON` tới `6=SAT` |

**Không nên kết luận rằng cứ có sáu trường thì một Quartz expression sẽ giữ nguyên ý nghĩa ở Spring.** Một số biểu thức dùng chữ `MON`, `FRI` và wildcard có thể tình cờ tương thích; biểu thức dùng **số thứ trong tuần** có thể chuyển sang ngày khác.

Cũng đừng nhầm tên class:

- `org.springframework.scheduling.support.CronTrigger` thuộc **Spring Framework** và dùng parser Spring.
- `org.quartz.CronTrigger` thuộc **Quartz** với lịch/trigger, job store và API Quartz riêng.

Cả hai mang tên “CronTrigger”, nhưng **không phải một kiểu dữ liệu tương đương, không phải một scheduler chung**, và không có cơ chế tự dịch tất cả expression giữa hai thư viện.

### Ba quy tắc đầu tiên

1. **Xác định đích cuối:** Spring `@Scheduled`, Spring `CronTrigger`, Linux `crontab` hay Quartz?
2. **Đếm và đặt tên từng trường**, đừng “thấy giống cron” rồi copy.
3. **Kiểm tra ý nghĩa bằng parser của chính đích sử dụng**, tính các lần chạy kế tiếp với timezone và dữ liệu ngày cụ thể.

## 2. Spring yêu cầu sáu trường, bắt đầu bằng giây

Theo tài liệu Spring Framework, định dạng là:

```text
┌────────── second       (0-59)
│ ┌──────── minute       (0-59)
│ │ ┌────── hour         (0-23)
│ │ │ ┌──── day-of-month (1-31)
│ │ │ │ ┌── month        (1-12 hoặc JAN-DEC)
│ │ │ │ │ ┌ day-of-week  (0-7; 0 hoặc 7 = SUN)
│ │ │ │ │ │
* * * * * *
```

Ví dụ Spring:

| Biểu thức Spring | Ý nghĩa theo timezone đang áp dụng |
| --- | --- |
| `0 * * * * *` | Vào **giây 0** của mỗi phút |
| `*/10 * * * * *` | Cứ mười giây trong các giây 0,10,20,30,40,50 |
| `0 */5 * * * *` | Vào giây 0, mỗi năm phút |
| `0 0 * * * *` | Đầu mỗi giờ |
| `0 0 9 * * MON-FRI` | 09:00:00 từ thứ Hai đến thứ Sáu |
| `0 30 8 * * MON` | 08:30:00 mỗi thứ Hai |
| `0 0 0 1 * *` | Nửa đêm ngày 1 mỗi tháng |
| `0 0 2 * * *` | 02:00:00 mỗi ngày *theo zone*, cần xét DST nếu zone có đổi giờ |

Hai dòng dễ nhầm nhất:

```text
0 */5 * * * *    Spring: mỗi năm PHÚT, tại giây 0.
*/5 * * * * *    Spring: mỗi năm GIÂY, từ giây 0 tới 55.
```

Chúng khác nhau **gấp 60 lần tần suất**. Lỗi này có thể biến một tác vụ chỉ nên chạy 12 lần mỗi giờ thành 720 lần mỗi giờ.

### 2.1. Dấu * không mang ý nghĩa “đợi một khoảng thời gian”

Dấu `*` là **mọi giá trị hợp lệ của trường**, không phải “chờ một đơn vị kể từ thời điểm đăng ký”.

Ví dụ `0 */5 * * * *` khớp các mốc phút 00, 05, 10,... trong giờ, chứ không nhất thiết đúng năm phút sau lúc application bắt đầu. Nếu ứng dụng được khởi động lúc 10:03:27, mốc cron kế tiếp có thể là 10:05:00.

Nếu muốn “cứ 5 phút kể từ khi ứng dụng bắt đầu và không phụ thuộc phút trên đồng hồ”, hãy cân nhắc `fixedRate`/`fixedDelay`; nếu cần lịch calendar cụ thể, dùng cron.

### 2.2. Các trường số và tên tiếng Anh

Spring hỗ trợ tên viết tắt tháng/ngày như `JAN`, `DEC`, `MON`, `FRI`; thường dễ hiểu hơn các số khi chuyển đổi giữa dialect. Ví dụ:

```java
@Scheduled(cron = "0 0 9 * * MON-FRI")
public void checkBusinessDays() {
    // ...
}
```

Đây là fragment cần bean Spring được quản lý và `@EnableScheduling`. Với ngày trong tuần, **ưu tiên tên `MON-FRI` thay cho khoảng số** khi config có nguy cơ được chuyển từ Quartz.

## 3. Linux/Unix crontab: năm trường thời gian, không có giây

Một dòng trong user crontab thường có dạng:

```cron
# minute hour day-of-month month day-of-week command
0 9 * * MON-FRI /opt/app/bin/reconcile
```

Năm trường thời gian là **minute, hour, day-of-month, month, day-of-week**. Phần cuối là **lệnh hệ điều hành**, không phải trường năm hay trường giây. Với system crontab như `/etc/crontab`, có thể còn **trường username** đứng giữa thời gian và lệnh; đó cũng không phải cron field để chuyển sang Spring.

Spring `@Scheduled(cron = "...")` chỉ nhận **chuỗi diễn tả lịch**, không nhận shell command:

```java
// Đúng ý nghĩa: 09:00 thứ Hai đến thứ Sáu.
@Scheduled(cron = "0 0 9 * * MON-FRI")
public void reconcile() {
    reconciliationService.run();
}
```

Đây là fragment, cần service và infrastructure Spring hợp lệ.

### 3.1. Bảng chuyển đổi Linux → Spring

| Ý định | Linux user crontab (5 thời gian) | Spring (6 trường) |
| --- | --- | --- |
| Mỗi phút | `* * * * *` | `0 * * * * *` |
| Mỗi 5 phút | `*/5 * * * *` | `0 */5 * * * *` |
| Mỗi giờ tại phút 0 | `0 * * * *` | `0 0 * * * *` |
| 09:00 ngày thường | `0 9 * * MON-FRI` | `0 0 9 * * MON-FRI` |
| 02:30 hằng ngày | `30 2 * * *` | `0 30 2 * * *` |
| 00:00 ngày 1 mỗi tháng | `0 0 1 * *` | `0 0 0 1 * *` |
| 08:15 thứ Hai | `15 8 * * MON` | `0 15 8 * * MON` |

**Quy trình chuyển đúng:** thêm một trường **giây** vào vị trí **đầu tiên** (thường là `0`), không thêm tùy tiện vào cuối, và không copy phần **command**.

Ví dụ sai:

```text
Crontab:        0 9 * * MON-FRI
Copy vào Spring: "0 9 * * MON-FRI"  --> thiếu trường, không hợp lệ.
Nối * vào cuối: "0 9 * * MON-FRI *"  --> sai vị trí/loại trường.
Spring đúng:    "0 0 9 * * MON-FRI".
```

### 3.2. Không suy rộng @reboot, env và shell semantics

Trong một số cron daemon, các dạng như `@reboot`, các biến môi trường `SHELL`/`PATH`/`CRON_TZ`, pipe/redirect và việc thực thi một command là đặc thù hệ điều hành/daemon. **Spring không biến `@Scheduled(cron=...)` thành một user crontab shell entry**.

Spring có các **macro được định nghĩa riêng** (xem phần 7), không có nghĩa mọi macro của crontab đều hợp lệ. Nếu cần chạy khi startup, dùng lifecycle callback có chủ đích như `ApplicationReadyEvent`/`ApplicationRunner`, không copy `@reboot` vào cron Spring rồi đoán.

### 3.3. Ngày trong tháng và thứ trong tuần: đừng đoán quy tắc AND/OR

Một số crontab truyền thống có quy tắc riêng khi **cả day-of-month và day-of-week đều bị giới hạn** (thường là logic OR theo tài liệu của daemon). Đừng mặc định biểu thức trộn hai điều kiện này sẽ có cùng tập ngày trong Spring hoặc Quartz.

Cách dễ đọc hơn khi **chỉ quan tâm một trường ngày** là để trường còn lại không ràng buộc bằng `*` hoặc `?` theo syntax parser:

```text
0 0 9 15 * ?     ngày 15 hàng tháng, 09:00.
0 0 9 ? * MON    thứ Hai hàng tuần, 09:00.
```

Cả hai là biểu thức Spring hợp lệ trong phiên bản có `CronExpression`. Nếu yêu cầu nghiệp vụ thật sự là “ngày 15 **hoặc** thứ Hai”, hãy mô tả bằng logic tường minh/trigger riêng và kiểm thử, thay vì dựa vào quy tắc được nhớ từ daemon khác.

## 4. Quartz: có thể sáu hoặc bảy trường, thứ trong tuần đánh số khác

Trong **Java Quartz Scheduler**, cron thường gồm sáu trường bắt buộc:

```text
second minute hour day-of-month month day-of-week [year]
```

Trong đó **`[year]` là trường tùy chọn thứ bảy**. Spring `CronExpression` **không nhận trường thứ bảy**.

Ví dụ Quartz cho năm 2027:

```text
0 0 9 ? * MON-FRI 2027
```

**Không copy cả chuỗi này vào Spring**: Spring yêu cầu đúng sáu trường, không có year. Bỏ `2027` đi thành:

```text
0 0 9 ? * MON-FRI
```

thì có thể chạy vào các ngày thường **của mọi năm**, **không còn ràng buộc riêng năm 2027**.

Nếu ứng dụng chỉ được phép chạy trong 2027, phải thiết kế thêm boundary nghiệp vụ/feature flag/start-stop hoặc scheduling mechanism hỗ trợ giới hạn thời gian đó. **Không được đơn giản xóa trường năm và gọi là chuyển đổi tương đương.**

### 4.1. Numeric day-of-week: cùng sáu trường nhưng sai ý nghĩa

Khác biệt dễ bị bỏ sót:

| Ngày | Spring | Quartz Java |
| --- | --- | --- |
| Sunday | `0` hoặc `7` | `1` |
| Monday | `1` | `2` |
| Tuesday | `2` | `3` |
| Wednesday | `3` | `4` |
| Thursday | `4` | `5` |
| Friday | `5` | `6` |
| Saturday | `6` | `7` |

Chẳng hạn:

```text
Quartz: 0 0 9 ? * 2       --> 09:00 mỗi thứ HAI.
Spring: 0 0 9 ? * 2       --> 09:00 mỗi thứ BA.
Spring an toàn: 0 0 9 ? * MON.
```

**Đây là lỗi nguy hiểm hơn thiếu trường**: chuỗi có thể hợp lệ ở Spring và ứng dụng khởi động bình thường, nhưng job chạy nhầm ngày. Nếu job phát sinh báo cáo, thông báo hoặc đối soát tài chính, kết quả có thể sai dù không có exception.

Các ví dụ khác:

- `0 0 9 ? * 5#2` trong Spring là **thứ Sáu thứ hai** trong tháng; cùng số `5` ở Quartz thường là **thứ Năm**. Hậu tố `#2` có thể được hai parser hỗ trợ, nhưng mapping `5` khác nhau.
- `0 0 9 ? * 6L` trong Spring diễn giải **thứ Bảy cuối cùng** trong tháng, còn trong Quartz numeric `6` ứng với thứ Sáu.
- Dùng `FRI#2`, `FRIL` hoặc `MON` giúp giảm rủi ro lệch numeric mapping, nhưng vẫn cần kiểm tra hỗ trợ syntax trong version parser cụ thể.

**Đừng chỉ kiểm tra “parse thành công”**; phải kiểm tra **lần chạy tiếp theo thực sự là ngày mong muốn**.

### 4.2. Quartz có misfire policy và job store; Spring cron không tự kế thừa

Quartz có `JobDetail`, `Trigger`, tùy chọn misfire handling và job store theo cách triển khai Quartz. Spring `@Scheduled(cron=...)` dùng hạ tầng scheduling khác. Chuyển một Quartz expression sang Spring không tự mang theo:

- misfire instructions;
- durable job store;
- cluster coordination;
- giới hạn repeat count/end time;
- recovery sau restart;
- concurrency rules của Quartz job class.

Nếu yêu cầu phụ thuộc các semantics trên, cần thiết kế lại scheduler/architecture, không chỉ sao chép sáu trường cron.

## 5. Ký hiệu mở rộng: Spring có hỗ trợ một số kiểu Quartz, không phải toàn bộ

Một hiểu lầm đối nghịch là “Spring khác Quartz nên mọi ký hiệu của Quartz đều sai”. Điều này cũng không đúng. `CronExpression` của Spring Framework 5.3+ hỗ trợ một số cú pháp day-of-month/week như `L`, `W`, `#`, nhưng **vẫn giữ cách đánh số thứ theo cron thông thường**.

### 5.1. Những ký hiệu cơ bản

| Ký hiệu | Ý nghĩa điển hình trong trường phù hợp | Ví dụ Spring |
| --- | --- | --- |
| `*` | Mọi giá trị được phép | `0 * * * * *` |
| `,` | Danh sách giá trị | `0 0 9,15 * * *` |
| `-` | Dải liên tiếp | `0 0 9 * * MON-FRI` |
| `/n` | Bước nhảy theo dải | `0 */15 * * * *` |
| `?` | Không ràng buộc day-of-month hoặc day-of-week | `0 0 9 ? * MON` |
| `L` | “Last” tùy trường | `0 0 0 L * *` |
| `W` | Ngày làm việc gần một ngày cụ thể | `0 0 9 15W * ?` |
| `#` | Thứ N của một weekday trong tháng | `0 0 9 ? * MON#2` |

**Lưu ý:** ký hiệu chỉ hợp lệ **ở những trường được quy định**, không phải đặt `L`, `W`, `#` vào bất kỳ trường nào cũng parse được.

### 5.2. Ví dụ Spring hỗ trợ

```text
0 0 0 L * *       ngày cuối cùng mỗi tháng lúc 00:00
0 0 0 L-3 * *     ba ngày trước ngày cuối tháng lúc 00:00
0 0 9 1W * ?      weekday gần ngày mùng 1, lúc 09:00
0 0 9 LW * ?      weekday cuối cùng của tháng, lúc 09:00
0 0 9 ? * 5L      thứ Sáu cuối cùng của tháng, lúc 09:00
0 0 9 ? * MON#2   thứ Hai thứ hai của tháng, lúc 09:00
```

**`W` không nhất thiết chọn đúng ngày trong tháng nếu ngày được nhắc đến là cuối tuần**; nó chọn ngày làm việc gần nhất theo quy tắc parser. Nếu cần biết ngày chính xác của năm/tháng cụ thể, tính bằng `CronExpression.next(...)` và kiểm tra các ngày giáp cuối tuần.

Với `#`, chú ý tháng không có “weekday thứ năm” như `FRI#5`: một số tháng sẽ **không có mốc khớp**. Đó là đặc trưng lịch, không phải scheduler tự bù một mốc.

### 5.3. Macro không đồng nhất giữa hệ thống

Spring `CronExpression` hỗ trợ các macro thông dụng:

| Macro Spring | Tương đương Spring 6-field |
| --- | --- |
| `@yearly` hoặc `@annually` | `0 0 0 1 1 *` |
| `@monthly` | `0 0 0 1 * *` |
| `@weekly` | `0 0 0 * * 0` |
| `@daily` hoặc `@midnight` | `0 0 0 * * *` |
| `@hourly` | `0 0 * * * *` |

Ví dụ:

```java
@Scheduled(cron = "@hourly", zone = "UTC")
public void collectHourly() {
    // ...
}
```

Lưu ý `@reboot` của một số crontab daemon **không thuộc bảng macro Spring**; cũng không mặc định mọi Quartz extension như một token hash scheduler-specific sẽ được parser Spring hỗ trợ.

**Phân biệt sentinel disable:** `@Scheduled(cron = "-")` là cơ chế **vô hiệu hóa trigger** trong annotation/registrar, thường dùng với property placeholder. Nó **không phải biểu thức 6 trường**, nên không được coi là một mẫu cron có lần chạy tương lai.

## 6. Múi giờ: cú pháp đúng vẫn có thể chạy sai giờ

Cron mô tả **ngày/giờ lịch**, nên phải biết timezone nào giải nghĩa mốc `09:00`. Trong Spring:

```java
@Scheduled(cron = "0 0 9 * * MON-FRI",
           zone = "Asia/Ho_Chi_Minh")
public void morningSync() {
    // ...
}
```

Đây là fragment cần Spring bean và `@EnableScheduling`. Với `zone` được nêu tường minh, `09:00` được diễn giải theo timezone **Asia/Ho_Chi_Minh**, không theo timezone mặc định của JVM/deployment host.

Nếu để `zone` trống, Spring dùng scheduler's timezone theo quy tắc của `@Scheduled`. Khi deploy từ máy lập trình sang container/Kubernetes/host khác, default timezone có thể thay đổi; lúc đó cùng một expression có thể chạy lệch UTC offset so với ý định.

### 6.1. Timezone là một phần của hợp đồng lịch

| Yêu cầu nghiệp vụ | Thiết kế dễ hiểu |
| --- | --- |
| 09:00 mỗi ngày làm việc tại Việt Nam | Cron + `zone="Asia/Ho_Chi_Minh"` |
| 09:00 UTC mọi ngày | Cron + `zone="UTC"` |
| 09:00 New York mỗi thứ Hai | Cron + `zone="America/New_York"`, test DST |
| Mỗi đúng 24 giờ tính từ lần bắt đầu | Xem fixed rate / `Duration`, không coi là “09:00 local” |
| Chỉ chạy một lần lúc một `Instant` cụ thể | One-off scheduling theo `Instant`, không nhất thiết cron |

**Đừng tự cộng +7 hoặc -5 cố định** cho một timezone có DST. Zone rules thay đổi theo thời gian; `ZoneId` đại diện cho khu vực có lịch sử/quy tắc offset, không chỉ một offset số học.

### 6.2. DST: giờ có thể bị bỏ qua hoặc lặp

Tại một số khu vực, có ngày chuyển sang DST mà một khoảng local time **không tồn tại**, và có ngày quay lại khiến một khoảng local time **lặp**. Ví dụ, khi nghiên cứu lịch ở `America/New_York`, nên kiểm tra các ngày chuyển giờ vào tháng 3/tháng 11 của năm liên quan.

Không nên mượn hành vi cụ thể của **Linux crond**, **Quartz misfire policy** hoặc ví dụ trên một website để khẳng định Spring sẽ “chạy bù đúng một lần” trong mọi DST transition. Hãy kiểm tra actual next instants trên **Spring CronExpression/CronTrigger cùng version**, và xác định chính sách nghiệp vụ khi có giờ bị mất/lặp.

### 6.3. Kiểm thử timezone bằng ZonedDateTime

Với Spring Framework 6.x, cách rõ ràng là:

```java
CronExpression expression =
        CronExpression.parse("0 0 9 * * MON-FRI");
ZonedDateTime base =
        ZonedDateTime.of(2026, 1, 2, 8, 59, 0, 0,
                         ZoneId.of("Asia/Ho_Chi_Minh"));
ZonedDateTime next = expression.next(base);
```

Đây là fragment, cần imports `java.time.ZoneId`, `ZonedDateTime` và Spring `CronExpression`. Đầu vào có zone rõ ràng, giúp chương trình tính mốc tiếp theo theo cùng cách giải thích local date/time. Phần sau sẽ có ví dụ runnable với đầy đủ imports và kết quả kiểm chứng.

**Không dùng `LocalDateTime` trần để kết luận thời điểm tuyệt đối trên timeline** khi lịch đi qua DST; ưu tiên `ZonedDateTime` và chuyển sang `Instant`/UTC khi logging hay so sánh mốc tuyệt đối.

## 7. Java 17 runnable không cần Spring: kiểm tra số trường để tránh copy nhầm

Ví dụ sau cố ý chỉ làm **bước 1: phân loại theo số trường**. Nó không phải Spring parser, không xác minh đầy đủ range, weekday hoặc timezone; kết quả `6 fields` chỉ cho biết **ứng viên** để đưa vào parser Spring.

Tạo file **CronFieldCountDemo.java**:

```java
public class CronFieldCountDemo {
    private static void describe(String expression) {
        int fields = expression.trim().split("\\s+").length;
        String dialectHint = switch (fields) {
            case 5 -> "POSIX_LIKE";
            case 6 -> "SPRING_CANDIDATE_OR_QUARTZ";
            case 7 -> "QUARTZ_YEAR_CANDIDATE";
            default -> "UNKNOWN";
        };
        System.out.println(fields + " fields: " + dialectHint);
    }

    public static void main(String[] args) {
        describe("0 9 * * MON-FRI");
        describe("0 0 9 * * MON-FRI");
        describe("0 0 9 ? * MON-FRI 2027");
    }
}
```

Lệnh chạy:

```bash
javac CronFieldCountDemo.java
java CronFieldCountDemo
```

**Stdout xác định:**

```text
5 fields: POSIX_LIKE
6 fields: SPRING_CANDIDATE_OR_QUARTZ
7 fields: QUARTZ_YEAR_CANDIDATE
```

Giải thích:

- Năm trường **gợi ý** user crontab nhưng không bảo đảm mọi biểu thức POSIX-like đều hợp lệ; vẫn cần kiểm tra giá trị từng trường.
- Sáu trường là **ứng viên** cho Spring và Quartz, nhưng **không chứng minh tương đương ngữ nghĩa**: numeric weekday `2` là ví dụ phản chứng.
- Bảy trường là gợi ý Quartz có year, không tương thích với Spring CronExpression sáu trường.
- Java `split("\\s+")` tách whitespace, không giải thích `?`, `L`, `W`, `#` hoặc DST; đó là công việc của parser chính thức.

Chương trình này có thể chạy bằng JDK 17+ không cần dependency Spring, phù hợp bước kiểm tra nhanh khi nhập expression từ một hệ thống khác.

## 8. Chứng minh bằng chính Spring CronExpression: parse và tính lịch

Với Spring Framework 6.x, `org.springframework.scheduling.support.CronExpression` cung cấp:

- `CronExpression.isValidExpression(String)` để kiểm tra biểu thức theo parser Spring.
- `CronExpression.parse(String)` để tạo expression hoặc báo lỗi nếu sai.
- `CronExpression.next(Temporal)` để tính lần khớp tiếp theo, không cần thực sự chờ scheduler chạy.

**Điểm đặc biệt quan trọng:** kiểm tra cú pháp hợp lệ không đủ. `"0 0 9 ? * 2"` là sáu trường và có thể được Spring chấp nhận, nhưng nó lên lịch **thứ Ba**, không phải thứ Hai theo cách đánh số Quartz.

### 8.1. Demo Java 17 trong dự án có Spring Framework

Tạo file **src/main/java/com/example/demo/CronSpringProof.java** trong một project đã có dependency `spring-context` 6.x. Đây là chương trình có `public static void main`; không cần khởi động Spring Boot context để dùng parser, nhưng phải có Spring jars trong classpath.

```java
package com.example.demo;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.scheduling.support.CronExpression;

public class CronSpringProof {
    public static void main(String[] args) {
        String posix = "0 9 * * MON-FRI";
        String spring = "0 0 9 * * MON-FRI";
        String quartzWithYear = "0 0 9 ? * MON-FRI 2027";

        System.out.println("posixValid="
                + CronExpression.isValidExpression(posix));
        System.out.println("springValid="
                + CronExpression.isValidExpression(spring));
        System.out.println("quartzYearValid="
                + CronExpression.isValidExpression(quartzWithYear));

        ZonedDateTime base = ZonedDateTime.of(
                2026, 1, 2, 8, 59, 0, 0, ZoneId.of("UTC"));

        CronExpression weekdays = CronExpression.parse(spring);
        ZonedDateTime first = weekdays.next(base);
        ZonedDateTime second = weekdays.next(first);
        System.out.println("first=" + first.toOffsetDateTime());
        System.out.println("second=" + second.toOffsetDateTime());

        CronExpression numberTwo =
                CronExpression.parse("0 0 9 ? * 2");
        ZonedDateTime numericNext = numberTwo.next(base);
        System.out.println("springWeekday2="
                + numericNext.toOffsetDateTime());
    }
}
```

Chạy qua build tool đã khai báo Spring Framework 6.x. Với Gradle application plugin và `mainClass` trỏ tới `com.example.demo.CronSpringProof`, có thể dùng:

```bash
./gradlew run
```

Hoặc biên dịch/chạy bằng `javac`/`java` nếu đưa đầy đủ Spring jars và dependencies vào classpath; không thể dùng JDK trần vì `CronExpression` là class của Spring.

**Các dòng stdout do `main` in ra:**

```text
posixValid=false
springValid=true
quartzYearValid=false
first=2026-01-02T09:00Z
second=2026-01-05T09:00Z
springWeekday2=2026-01-06T09:00Z
```

Giải thích có kiểm soát:

- `posix` có năm trường, không phải Spring cron.
- `spring` có đúng sáu trường và mô tả thứ Hai–thứ Sáu.
- `quartzWithYear` có bảy trường, nên không hợp lệ với Spring.
- `base` là thứ Sáu **02/01/2026, 08:59 UTC**. Lần khớp đầu là thứ Sáu 09:00, lần tiếp theo là thứ Hai 05/01/2026 lúc 09:00.
- Với Spring, trường DOW bằng `2` là **thứ Ba**; lần tiếp theo sau `base` là 06/01/2026 lúc 09:00. Điều này trực tiếp chứng minh mối nguy copy numeric DOW từ Quartz.
- Chuyển kết quả sang `OffsetDateTime` để in ổn định ở dạng `Z` thay vì phụ thuộc cách `ZonedDateTime.toString()` thêm zone suffix.

**Đây là test theo timezone UTC có chủ ý.** Với cron nghiệp vụ chạy theo `Asia/Ho_Chi_Minh` hoặc `America/New_York`, hãy đặt `base` bằng đúng zone ấy và kiểm tra các ngày quanh tháng/giờ quan trọng.

### 8.2. Bảng test nên có trước khi chuyển dialect

| Input | Kỳ vọng hợp lệ trong Spring? | Điều kiểm tra tiếp |
| --- | --- | --- |
| `0 9 * * MON-FRI` | Không | Thiếu trường giây |
| `0 0 9 * * MON-FRI` | Có | 09:00 ngày thường theo zone |
| `0 0 9 ? * 2` | Có | Spring hiểu là **thứ Ba**, không phải Monday Quartz |
| `0 0 9 ? * MON` | Có | Dùng tên thứ cho độ rõ ràng |
| `0 0 9 ? * MON 2027` | Không | Trường năm Quartz không có trong Spring |
| `0 0 9 1W * ?` | Có | Ngày làm việc gần ngày 1 từng tháng |
| `0 0 9 ? * MON#2` | Có | Thứ Hai thứ hai của tháng |
| `@hourly` | Có (Spring macro) | Đầu giờ |
| `@reboot` | Không (không là Spring macro) | Dùng lifecycle callback cho startup |
| `-` | Không phải 6-field cron | Sentinel disable riêng của `@Scheduled` |

Hàng “có” nghĩa **được parser Spring 6.x chấp nhận**, không phải khẳng định business semantics đã đúng. Cần kiểm tra next occurrences và timezone.

## 9. Ví dụ Spring Boot 3.x hoàn chỉnh: cấu hình cron rõ ràng

Ví dụ dưới đây dùng **Java 17+, Spring Boot 3.x/Spring Framework 6.x** và dependency `spring-boot-starter-web`. Ứng dụng bật scheduling, dùng cron sáu trường từ property và zone rõ ràng, đồng thời có endpoint kiểm tra số lần callback bắt đầu. Nó còn in ra các lần khớp tiếp theo bằng `CronExpression` để người học có thể đối chiếu **parser thực tế** trước khi vận hành.

Đặt tất cả code trong **src/main/java/com/example/demo/CronDialectApplication.java**:

```java
package com.example.demo;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
public class CronDialectApplication {
    public static void main(String[] args) {
        SpringApplication.run(CronDialectApplication.class, args);
    }

    @Bean
    CommandLineRunner verifySpringCron() {
        return args -> {
            CronExpression expr =
                    CronExpression.parse("0 0 9 * * MON-FRI");
            ZonedDateTime base = ZonedDateTime.of(
                    2026, 1, 2, 8, 59, 0, 0, ZoneId.of("UTC"));
            ZonedDateTime first = expr.next(base);
            System.out.println("cron-next-1="
                    + first.toOffsetDateTime());
            System.out.println("cron-next-2="
                    + expr.next(first).toOffsetDateTime());
        };
    }
}

@Component
class CleanupJob {
    private static final Logger log =
            LoggerFactory.getLogger(CleanupJob.class);
    private final AtomicInteger runs = new AtomicInteger();

    @Scheduled(
        cron = "${jobs.cleanup.cron:0 */5 * * * *}",
        zone = "${jobs.cleanup.zone:UTC}")
    public void runCleanup() {
        int n = runs.incrementAndGet();
        log.info("Cleanup tick {}, thread={}",
                n, Thread.currentThread().getName());
    }

    int runs() {
        return runs.get();
    }
}

@RestController
class CronStatusController {
    private final CleanupJob job;

    CronStatusController(CleanupJob job) {
        this.job = job;
    }

    @GetMapping("/cron/status")
    public Map<String, Integer> status() {
        return Map.of("runs", job.runs());
    }
}
```

Các class package-private có thể đứng cùng file chứa public `CronDialectApplication`. Đặt tùy chọn trong **src/main/resources/application.properties**:

```properties
jobs.cleanup.cron=0 */5 * * * *
jobs.cleanup.zone=Asia/Ho_Chi_Minh
```

Chạy bằng build Spring Boot:

```bash
./gradlew bootRun
curl -s http://localhost:8080/cron/status
```

Trên Windows, dùng `gradlew.bat bootRun` và `curl.exe` theo shell. Trong lúc startup, `CommandLineRunner` in ra:

```text
cron-next-1=2026-01-02T09:00Z
cron-next-2=2026-01-05T09:00Z
```

Đây là các **dòng do runner in**; Spring Boot cũng in startup logs khác. Vì runner dùng anchor UTC cố định nên những dòng trên không phụ thuộc thời điểm hiện tại.

Endpoint ví dụ:

```json
{"runs":1}
```

**Số lần chạy không cố định:** cron property mặc định/được cấu hình kích hoạt ở phút chia hết cho 5. Khi bạn gọi endpoint ngay sau startup có thể là `0`; sau một tick có thể là `1`; các lần sau tăng dần. Không dùng biến `runs` này như bằng chứng job nghiệp vụ đã hoàn tất: nó chỉ đếm số invocation.

### 9.1. Các điểm đúng trong ví dụ

1. `@EnableScheduling` bật annotation processing.
2. `CleanupJob` là Spring-managed bean nhờ `@Component` và cùng package scan.
3. `cron="${...}"` lấy **Spring sáu trường**, không nhận lệnh shell hoặc Quartz year.
4. `zone="${...}"` cố định ý nghĩa giờ địa phương khi ứng dụng deploy ở những máy khác.
5. `CronExpression.parse(...)` trong runner là công cụ học/kiểm chứng độc lập với runtime task registration; test cụ thể thêm những expression mới trước khi đưa vào property.
6. Spring quản lý lifecycle scheduler. Trên nhiều replica, **mỗi instance** có thể có một trigger riêng — không có distributed singleton tự động.

### 9.2. Ví dụ sai khi copy cron từ hệ điều hành

Nếu đưa trực tiếp:

```properties
jobs.cleanup.cron=*/5 * * * *
```

thì đây chỉ có **năm trường**. Spring scheduled processor sẽ không đăng ký nó như một lịch cron hợp lệ; tùy giai đoạn xử lý/cấu hình, ứng dụng có thể lỗi startup. Không nên dựa vào thông báo lỗi của một phiên bản rồi mong nó tự chỉnh thành sáu trường.

Nếu sửa thành:

```properties
jobs.cleanup.cron=*/5 * * * * *
```

thì đã có sáu trường nhưng **mỗi năm giây**, không phải mỗi năm phút. Sửa đúng là:

```properties
jobs.cleanup.cron=0 */5 * * * *
```

Tốt nhất là đưa một **test next-executions** vào quy trình review PR để tránh lỗi có cú pháp hợp lệ nhưng sai nghĩa.

### 9.3. Disable trigger qua property

Với Spring `@Scheduled`, có thể dùng:

```java
@Scheduled(cron = "${jobs.cleanup.cron:-}", zone = "UTC")
public void runCleanup() {
    // ...
}
```

Khi property không có, default `-` tắt trigger theo `Scheduled.CRON_DISABLED`. **Không trộn sentinel `-` vào công cụ tính `CronExpression.next` như thể nó là một cron expression sáu trường**. Nếu validation code cần hỗ trợ disabled mode, xử lý `"-"` như trạng thái cấu hình **riêng biệt** trước khi gọi parser.

## 10. Khi cần năm cụ thể, đừng xóa field rồi bỏ quên nghiệp vụ

Quartz có thể ghi:

```text
0 0 9 ? * MON-FRI 2027
```

Một bản port vội vàng bỏ `2027`, thành `0 0 9 ? * MON-FRI`; kết quả là chạy **mọi năm**. Các lựa chọn an toàn phụ thuộc yêu cầu:

- Nếu chỉ muốn bật job trong một giai đoạn, dùng property/feature toggle và quản lý lifecycle của schedule.
- Nếu cần “chỉ trong 2027” nhưng vẫn chạy hằng ngày, worker có thể kiểm tra **business date theo zone** và skip khi ngoài khoảng. Cần xem xét side effect nếu lịch vẫn tick.
- Nếu phải đăng ký/hủy đúng mốc start/end, dùng `TaskScheduler`/`ScheduledFuture` và điều phối lifecycle theo `Instant`.
- Nếu cần misfire recovery/cluster durability của Quartz, giữ Quartz Scheduler và cấu hình tương ứng, đừng ép Spring `@Scheduled` giả lập tất cả.
- Nếu job quan trọng, lưu trạng thái và idempotency để không xử lý hai lần khi retry hoặc chạy nhiều instance.

### Business date không giống UTC date

Một job “chỉ năm 2027 ở Việt Nam” phải đánh giá ngày theo `Asia/Ho_Chi_Minh` nếu đó là hợp đồng. `LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))` có thể khác ngày UTC gần nửa đêm. Việc dùng `Instant.now()` rồi kiểm tra năm sau khi chuyển sang đúng zone giúp tránh lỗi ranh giới năm.

**Đừng coi server timezone mặc định là dữ liệu nghiệp vụ**.

## 11. Mối quan hệ giữa CronExpression, CronTrigger, @Scheduled

Trong Spring có ba mức liên quan:

| API | Vai trò |
| --- | --- |
| `CronExpression` | Parse, kiểm tra, tính mốc kế tiếp từ `Temporal` (không tự chạy task) |
| `CronTrigger` | Là một `Trigger` cho `TaskScheduler`, dùng cron expression và zone |
| `@Scheduled(cron=...)` | Annotation để Spring đăng ký task sau khi đã bật `@EnableScheduling` và tạo bean |

Ví dụ API trực tiếp:

```java
TaskScheduler scheduler = ...;
CronTrigger trigger = new CronTrigger(
        "0 0 9 * * MON-FRI",
        ZoneId.of("Asia/Ho_Chi_Minh"));
ScheduledFuture<?> future =
        scheduler.schedule(this::reconcile, trigger);
```

Đây là **fragment**: cần scheduler, method và imports; `CronTrigger` trong ví dụ là **Spring** `org.springframework.scheduling.support.CronTrigger`. Không dùng `org.quartz.CronTrigger` làm đối số thay thế.

### 11.1. CronTrigger và misfire không giống Quartz

Spring `CronTrigger` có các lựa chọn lịch dựa trên completion hoặc scheduled time (ví dụ `forLenientExecution` và `forFixedExecution` ở các phiên bản Spring 6.1.3+). Những lựa chọn này ảnh hưởng cách tính lần trigger kế tiếp khi task chạy lâu.

Tuy nhiên, **đây không phải cơ chế misfire/job store của Quartz**: việc restart process, mất lịch in-memory hay scale nhiều replicas vẫn phải được giải quyết theo kiến trúc. Đừng tự ánh xạ một Quartz MisfireInstruction sang một tên method Spring trông tương tự rồi xem như chức năng tương đương.

### 11.2. @Scheduled không tự chạy một lần trên toàn cluster

Một instance Spring Boot chứa một `@Scheduled` bean thường có schedule riêng. Nếu deploy ba replicas/pods, mỗi process có thể chạy cùng lịch. Cú pháp cron chỉ mô tả **thời điểm**, không quy định **ai sở hữu công việc**.

Với job có side effect không được nhân đôi, cần distributed lock/lease, leader election hoặc durable job claiming và idempotency. Với job không được bỏ sót, cần persistence/recovery phù hợp; Spring cron in-process đơn thuần không phải guaranteed-delivery queue.

## 12. Lịch theo ngày thực tế: lưu ý DST, task chạy lâu và restart

Ngay cả khi cron expression đúng:

- **DST gap:** một local time có thể không tồn tại trong một ngày.
- **DST overlap:** một local time có thể xuất hiện hai lần với offset khác nhau.
- **Scheduler lag:** CPU contention, GC pause và pool quá tải có thể làm tick đến muộn.
- **Task duration:** một lượt chạy lâu hơn khoảng cách hai mốc có thể làm trigger bỏ mốc hoặc chạy muộn tùy `CronTrigger` mode/implementation.
- **Restart:** scheduler in-memory không tự phát lại toàn bộ mốc đã bỏ lỡ.
- **Duplicate registrations:** nhiều bean, repeatable `@Scheduled` hoặc nhiều instance có thể kích hoạt trùng.

Trong ứng dụng cần tính giờ chính xác, đừng chỉ kiểm tra cron với một ngày “đẹp”. Hãy kiểm tra ít nhất:

1. Một ngày làm việc bình thường.
2. Cuối tuần.
3. Ngày cuối tháng và tháng ngắn.
4. Ngày gần năm mới (nếu có year boundary).
5. Ngày quanh DST transition trong timezone liên quan.
6. Khoảng thời gian dài hơn một chu kỳ khi task chậm hơn lịch.
7. Khi context shutdown/restart.
8. Khi có nhiều replicas và retry.

### 12.1. Đừng tin vào số lần tick để quyết định dữ liệu còn thiếu

Ví dụ một job mỗi ngày lúc 09:00 bị tắt ba ngày. Sau khi bật lại, nó không tự có ba bản tin nghiệp vụ bền vững chỉ vì “cron đã lỡ ba mốc”. Nếu cần xử lý bù, job nên quét những công việc **đủ điều kiện theo dữ liệu** và lưu watermark/last-successful-business-date, không chỉ tăng biến đếm mỗi khi `@Scheduled` chạy.

## 13. Quy trình chuyển một cron expression sang Spring

Một checklist chuyển đổi có thể đưa trực tiếp vào code review:

1. **Xác nhận nguồn:** Linux user crontab, system crontab, Quartz Java, Quartz.NET hay một cloud scheduler? Không suy ra từ chữ “cron” đơn thuần.
2. **Xác định dialect và version:** bao nhiêu field, có year, có macro đặc thù?
3. **Bỏ phần không thuộc lịch:** user/command của crontab OS; không đưa vào annotation.
4. **Map đúng thứ tự trường:** Spring cần `second minute hour day-of-month month day-of-week`.
5. **Nếu từ OS năm trường:** thêm `second=0` vào **đầu**, không thêm vào cuối.
6. **Nếu từ Quartz có year:** không xóa mà quên semantics; xác định cách áp giới hạn năm.
7. **Map lại số thứ:** Spring `0/7=SUN`, `1=MON`; Quartz Java thường `1=SUN`. Ưu tiên tên chữ `MON-FRI`.
8. **Kiểm tra special chars:** `?`, `L`, `W`, `#`, macros theo chính Spring version. Không copy extension không rõ.
9. **Chọn timezone:** dùng `zone` rõ ràng hoặc `ZonedDateTime` tương ứng.
10. **Chạy parser Spring:** `CronExpression.parse(...)`; tránh tin mỗi bộ sinh cron của nền tảng khác.
11. **Tính ít nhất 5–10 lần chạy tiếp theo:** kiểm tra weekday, month, DST và biên năm.
12. **Kiểm tra behavior runtime:** worker capacity, error handler, task overlap, multi-replica, restart và idempotency.
13. **Viết test chống hồi quy:** cố định anchor time/zone, assert những lần khớp quan trọng.
14. **Giám sát sau deploy:** log scheduled vs actual start, run success, lag, retry và số instance thực thi.

**Mục tiêu của conversion không chỉ là “đủ sáu dấu cách”.** Nó phải bảo toàn **lịch nghiệp vụ**. Một migration có parser PASS nhưng chạy sai thứ trong tuần là migration thất bại.

## 14. Các lỗi phổ biến

**Lỗi 1 — Copy năm trường Linux vào @Scheduled.** Spring cần sáu trường, nên expression thiếu giây không hợp lệ.

**Lỗi 2 — Thêm trường ở cuối thay vì đầu.** Sai thứ tự có thể làm biểu thức invalid hoặc đổi ý nghĩa.

**Lỗi 3 — Sao chép `*/5 * * * *` thành `*/5 * * * * *`.** Linux mỗi năm phút, Spring mỗi năm giây.

**Lỗi 4 — Copy dòng crontab có cả shell command hoặc username.** `@Scheduled(cron)` nhận expression, không nhận command.

**Lỗi 5 — Tin rằng sáu trường Quartz giữ nguyên nghĩa trong Spring.** Numeric DOW bị lệch.

**Lỗi 6 — Copy Quartz year field.** Spring `CronExpression` không nhận bảy trường.

**Lỗi 7 — Bỏ year field không thêm business guard.** Job từng giới hạn một năm trở thành job chạy mọi năm.

**Lỗi 8 — Copy số weekday trong cú pháp `#` hoặc `L`.** Token có thể parse được nhưng chọn nhầm thứ.

**Lỗi 9 — Khẳng định Spring không hỗ trợ `?`, `L`, `W`, `#`.** Spring 5.3+ `CronExpression` có hỗ trợ các dạng đã được tài liệu nêu, trong trường hợp hợp lệ.

**Lỗi 10 — Tin mọi Quartz extension đều được Spring hỗ trợ.** Phải đối chiếu parser/version, không suy từ tên ký hiệu.

**Lỗi 11 — Giả định @reboot là một Spring macro.** Không; dùng lifecycle callback đúng mục đích.

**Lỗi 12 — Trộn sentinel `-` với CronExpression sáu trường.** `-` dùng để disable `@Scheduled`, không có next occurrence.

**Lỗi 13 — Không đặt zone và mặc định mọi host cùng timezone.** Deploy đổi môi trường có thể đổi giờ chạy.

**Lỗi 14 — Cộng UTC offset cố định cho vùng có DST.** DST thay đổi offset; dùng `ZoneId`.

**Lỗi 15 — Chỉ xem parse success.** Một biểu thức hợp lệ vẫn có thể chạy nhầm thứ.

**Lỗi 16 — Dựa vào một online cron generator không chọn dialect.** Generator có thể dùng Linux năm trường, Quartz bảy trường hoặc cú pháp riêng.

**Lỗi 17 — Cho rằng Spring CronTrigger có Quartz misfire/job store.** Hai scheduler khác nhau; không tự kế thừa durability/cluster policy.

**Lỗi 18 — Tin cron bảo đảm chạy chính xác và một lần duy nhất.** Scheduling có lag; nhiều replicas có thể chạy trùng.

**Lỗi 19 — Chỉ kiểm tra ngày bình thường, không test cuối tháng/DST.** Các lỗi calendar thường chỉ xuất hiện hiếm.

**Lỗi 20 — Coi mỗi tick là nghiệp vụ thành công.** Tick started, business completed và side effect durable là các trạng thái khác.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** `0 9 * * MON-FRI` trong user crontab Linux nên chuyển thế nào để chạy 09:00 ngày thường trong Spring?

**Đáp án:** `0 0 9 * * MON-FRI`. Thêm trường **giây 0** ở đầu, không copy command OS.

**Câu 2.** `*/5 * * * *` và `*/5 * * * * *` có tương đương không?

**Đáp án:** Không. Chuỗi Linux năm trường là mỗi năm phút; chuỗi Spring sáu trường là mỗi năm giây.

**Câu 3.** Một Quartz expression dùng day-of-week số `2` và không có year field, copy sang Spring có giữ ngày không?

**Đáp án:** Không nhất thiết. Quartz Java dùng `2=Monday`; Spring dùng `2=Tuesday`. Nên chuyển sang `MON` và kiểm tra next dates.

**Câu 4.** Spring có chấp nhận trường năm tùy chọn như Quartz không?

**Đáp án:** Không. `CronExpression` của Spring dùng đúng sáu trường. Nếu year là ràng buộc nghiệp vụ, phải giữ bằng cơ chế khác.

**Câu 5.** `0 0 9 ? * MON#2` có hợp lệ trong Spring 6.x không?

**Đáp án:** Có; nó mô tả 09:00 của thứ Hai thứ hai trong tháng. Ký hiệu `#` thuộc những extension đã được Spring hỗ trợ.

**Câu 6.** Vì sao `5L` từ Quartz đáng nghi khi chuyển sang Spring?

**Đáp án:** Số thứ trong tuần khác nhau. Trong Spring `5` là thứ Sáu; trong Quartz `5` thường là thứ Năm. Hãy dùng tên weekday và test.

**Câu 7.** `@Scheduled(cron="@hourly")` có dùng trong Spring không? Thế `@reboot`?

**Đáp án:** Spring có macro `@hourly`, tương đương `0 0 * * * *`. `@reboot` không phải macro Spring; startup action cần cơ chế lifecycle phù hợp.

**Câu 8.** Nếu `CronExpression.isValidExpression(...)` trả true, đã chắc business schedule chính xác chưa?

**Đáp án:** Chưa. Nó kiểm tra hợp lệ theo parser; cần tính next occurrences, zone, weekday và boundary dates để xác minh ý nghĩa.

**Câu 9.** `@Scheduled(cron="0 0 9 * * MON-FRI")` không chỉ định zone, có chắc chạy 09:00 Việt Nam?

**Đáp án:** Không. Nó phụ thuộc scheduler's timezone. Nếu nghiệp vụ là 09:00 Việt Nam, chỉ định `zone="Asia/Ho_Chi_Minh"`.

**Câu 10.** Copy Quartz cron kèm year 2027 sang Spring, xóa year là xong?

**Đáp án:** Không. Xóa year làm mất giới hạn 2027. Cần guard hay scheduling lifecycle thích hợp để giữ yêu cầu.

**Câu 11.** Có ba replicas Spring cùng một biểu thức cron. Có bảo đảm job chỉ chạy một lần mỗi mốc trên toàn cluster?

**Đáp án:** Không. Mỗi instance có scheduling local; cần distributed coordination hoặc idempotency theo yêu cầu.

**Câu 12.** Khi đổi một lịch từ Quartz sang Spring, nên test những gì ngoài syntax?

**Đáp án:** Next dates, timezone/DST, weekday, các ký hiệu L/W/#, giới hạn year, misfire/restart, overlap, concurrency, idempotency và số instance.

## Kết luận

**Không có một “ngôn ngữ cron chung” áp dụng y nguyên cho Linux crontab, Quartz và Spring.** Với Spring Framework 6.x, hãy luôn xuất phát từ hợp đồng **sáu trường** `second minute hour day-of-month month day-of-week`, lưu ý `0/7=Sunday` và `1=Monday`, **không có year field**, đồng thời kiểm tra macros/extension theo đúng Spring parser.

Việc chuyển từ năm trường Linux phải **thêm giây vào đầu**; chuyển từ Quartz phải **đánh giá lại numeric weekday và trường năm**. Một expression sáu trường parse thành công vẫn có thể chạy sai thứ hoặc sai zone. Vì vậy, trước deploy hãy dùng `CronExpression.parse` và `next` với `ZonedDateTime` phù hợp, kiểm thử các mốc quan trọng, rồi rà soát scheduling runtime, retries và multi-replica behavior.

## Tài liệu tham khảo

1. Spring Framework Reference — Cron Expressions, six fields, L/W/#, macros: https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-cron-expression
2. Spring Framework 6.2 Javadoc — `CronExpression`, Quartz weekday caveat: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html
3. Spring Framework 6.2 Javadoc — `@Scheduled` cron and zone: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html
4. Spring Framework 6.2 Javadoc — `CronTrigger`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html
5. Quartz Java — Cron Trigger Tutorial (six fields plus optional year, weekday numbers): https://www.quartz-scheduler.org/documentation/quartz-2.5.x/tutorials/crontrigger.html
6. Linux man-pages — `crontab(5)`, five fields, command, day matching and DST caveats: https://man7.org/linux/man-pages/man5/crontab.5.html
7. Java SE 17 — `ZonedDateTime`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html
8. Spring Framework Reference — `@EnableScheduling` / TaskScheduler infrastructure: https://docs.spring.io/spring-framework/reference/integration/scheduling.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cron-timezones-009">Kiểm chứng lịch quanh ranh giới tháng, năm và thay đổi múi giờ</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng lịch quanh ranh giới tháng, năm và thay đổi múi giờ

> **Module:** Spring Framework – concurrency  
> **Nhóm kiến thức:** Cron và múi giờ  
> **Mục tiêu:** xác minh **các thời điểm chạy tiếp theo** của `@Scheduled`, `CronTrigger` và `CronExpression` khi lịch đi qua cuối tháng, đầu năm, năm nhuận và các ngày đổi múi giờ/DST. Một cron parse được chưa đủ: cần kiểm tra cả **UTC Instant**, **ngày giờ địa phương**, **ZoneId/offset**, chính sách xử lý mốc bị lỡ và cách ứng dụng tránh chạy trùng nghiệp vụ.

Nội dung dựa trên Java 17 và Spring Framework 6.2.x. Các ví dụ có mốc thời gian cố định, không phải chờ cron thực chạy vào đêm giao thừa. Hai ví dụ hoàn chỉnh (Java thuần và Spring Context) có lệnh chạy, stdout dự kiến và giải thích. Các snippet cho ứng dụng được ghi rõ.

## 1. Kết quả học tập

Bạn sẽ có thể:

1. Đọc chính xác sáu trường của Spring cron, phân biệt Unix cron năm trường và Quartz có thể dùng bảy trường.
2. Diễn đạt lịch đầu tháng, cuối tháng, cuối năm, đầu năm và ngày 29/2 với cú pháp Spring.
3. Chứng minh các mốc qua `31/12 -> 01/01`, `31/01 -> 28/02`, `29/02`, tháng 30/31 ngày.
4. Phân biệt `Instant`, `LocalDateTime`, `ZonedDateTime`, `ZoneId`, `ZoneOffset`, `Clock` và `YearMonth`.
5. Phân tích tác động của timezone mặc định trên server, timezone explicit trong `@Scheduled.zone` và `CronTrigger(expression, zone)`.
6. Phát hiện DST **gap** (giờ không tồn tại) và **overlap** (giờ có hai offset) bằng `ZoneRules`.
7. Dùng `Clock.fixed`, `SimpleTriggerContext` và `CronTrigger.nextExecution` để kiểm thử không ngủ.
8. Sinh chuỗi nhiều lần bằng `CronExpression.next(ZonedDateTime)` và đối chiếu theo yêu cầu nghiệp vụ.
9. Phân biệt kết quả tính lịch với việc thực thi của worker, xử lý late/missed triggers, nhiều pod và idempotency.
10. Thiết kế golden test vectors, CI checks và metrics khi nâng phiên bản Spring hoặc timezone database.

## 2. Vì sao các ranh giới ngày là bài kiểm thử bắt buộc?

Trong ngày thường, một cron `0 0 9 * * *` có vẻ hoạt động tốt. Nhưng một hệ thống chạy lâu có thể gặp:

- Đêm 31/12 và ngày 01/01: năm, tháng và ngày đồng thời thay đổi.
- Ngày 30/04 sang 01/05: tháng 30 ngày khác tháng 31 ngày.
- Ngày 31/01 sang tháng 2: năm thường có 28 ngày, năm nhuận có 29 ngày.
- Lịch đặt ngày 31 của tháng: không có ngày 31 ở tháng 2/4/6/9/11.
- Ngày 29/02: chỉ xuất hiện ở một số năm, với quy tắc thế kỷ.
- Một tenant ở Việt Nam và một tenant ở New York: cùng “09:00” nhưng thành các UTC Instant khác nhau.
- Mùa xuân New York: một khoảng giờ địa phương biến mất do đồng hồ nhảy tới trước.
- Mùa thu New York: một khoảng giờ địa phương lặp lại vì đồng hồ lùi.
- Pod chạy UTC còn laptop chạy `Asia/Ho_Chi_Minh`: lịch không chỉ định zone có thể lệch bảy giờ.
- Restart hoặc deploy khi mốc cron đã qua: ứng dụng có thể bỏ một lần chạy, dù biểu thức cron đúng.

Đây là ba lớp trách nhiệm khác nhau:

~~~text
Business calendar rule + ZoneId
              |
              v
    Cron parser / trigger
              |
              v
         Planned Instant
              |
              v
    TaskScheduler + executor
              |
              v
     Actual invocation time
              |
              v
  Transaction / job result / retry
~~~

**Unit test cron** xác minh mốc kế tiếp. **Integration test scheduler** xác minh việc method được gọi. **Business test** xác minh kỳ được xử lý đúng, không trùng và có cơ chế phục hồi. Không được suy một phép kiểm tra thành công ở lớp thứ nhất sẽ đảm bảo hai lớp sau.

## 3. Cú pháp sáu trường của Spring

~~~text
second minute hour day-of-month month day-of-week
   0      0    9          1      *       *
~~~

`0 0 9 1 * *` có nghĩa **09:00 ngày 1 mỗi tháng**, với timezone dùng để diễn giải rule.

| Trường | Phạm vi và đặc tính | Ví dụ |
| --- | --- | --- |
| Seconds | 0–59 | `0` |
| Minutes | 0–59 | `0`, `*/15` |
| Hours | 0–23 | `9` |
| Day of month | 1–31, `L`, `W` và biến thể | `1`, `L`, `1W`, `LW` |
| Month | 1–12 hoặc JAN–DEC | `2`, `JAN` |
| Day of week | 0–7 (0 hoặc 7 là Sunday), MON–SUN | `MON-FRI`, `FRI#2` |

Spring hỗ trợ một số ký hiệu mở rộng kiểu Quartz nhưng **đánh số thứ trong tuần theo cron**, khác Quartz. Đừng lấy “1 = Sunday” từ Quartz rồi dùng cho Spring; nên dùng tên `MON`, `FRI` để tránh nhầm.

### 3.1. Những biểu thức kiểm thử quan trọng

| Spring cron | Ý nghĩa theo zone của trigger |
| --- | --- |
| `0 0 9 1 * *` | 09:00 ngày đầu mỗi tháng |
| `0 0 9 L * *` | 09:00 ngày cuối thực tế của tháng |
| `0 0 9 31 * *` | 09:00 ngày 31, **chỉ** ở tháng có ngày 31 |
| `0 0 9 29 2 *` | 09:00 ngày 29/02 các năm nhuận |
| `0 0 9 1 1 *` | 09:00 ngày 01/01 hằng năm |
| `0 0 9 LW * *` | 09:00 ngày trong tuần cuối tháng |
| `0 0 9 1W * *` | 09:00 ngày trong tuần gần ngày 1 nhất |
| `0 0 9 ? * MON-FRI` | 09:00 mỗi thứ Hai đến thứ Sáu |
| `0 30 2 * * *` | 02:30 mỗi ngày, có thể gặp DST gap |
| `0 30 1 * * *` | 01:30 mỗi ngày, có thể gặp DST overlap |

**`L` khác `31`:** `L` là “last day” và tự chuyển 31 → 30 → 28/29 theo tháng. Ngược lại, `31` không hề tạo fire vào tháng 2 vì ngày đó không tồn tại.

**`W`/`LW` không phải lịch ngày làm việc doanh nghiệp.** Chúng dựa trên weekday theo calendar, không tính Tết, lễ ngân hàng, nghỉ công ty hoặc ngày chốt sổ nội bộ.

### 3.2. Không dùng biểu thức bảy trường năm từ Quartz

~~~text
0 0 9 1 1 * 2027
~~~

Đây **không** phải định dạng Spring cron sáu trường hợp lệ. Nếu một job chỉ có hiệu lực năm 2027, hãy lưu hiệu lực trong job definition hoặc business predicate, rồi disable/reschedule theo contract. Không thêm một trường year cuối expression như Quartz.

### 3.3. Cron parse thành công chưa chứng minh lịch đúng

~~~java
boolean valid = CronExpression.isValidExpression(
        "0 0 9 L * *");
~~~

Đây là **snippet** cần `org.springframework.scheduling.support.CronExpression`. Giá trị true chỉ cho biết biểu thức hợp lệ về cú pháp; nó không kiểm chứng timezone, expected next Instant, DST, cũng không chứng minh task sẽ được chạy một lần toàn hệ thống.

## 4. Instant và giờ địa phương không phải một thứ

Một thời điểm:

~~~text
2026-12-31T17:00:00Z
~~~

tương đương:

~~~text
2027-01-01 00:00:00 Asia/Ho_Chi_Minh (+07:00)
2026-12-31 17:00:00 UTC
~~~

Do đó, UTC còn thuộc năm 2026 nhưng ngày kinh doanh Việt Nam đã sang **2027**. Điều này đặc biệt nguy hiểm khi làm billing hoặc báo cáo đầu tháng/năm mà chỉ dựa `LocalDate.now(Clock.systemUTC())`.

| Kiểu | Chứa gì? | Dùng khi nào? |
| --- | --- | --- |
| `Instant` | Điểm tuyệt đối trên timeline | Lưu planned/actual event, so sánh thứ tự |
| `LocalDateTime` | Năm/tháng/ngày/giờ chưa có zone | Nhận một lịch địa phương từ người dùng |
| `ZoneId` | Vùng và quy tắc timezone | Lịch 09:00 tại HCM/New York, DST |
| `ZoneOffset` | Offset cụ thể, ví dụ +07:00 | Ghi log, chuyển đổi tại một thời điểm |
| `ZonedDateTime` | Local time + zone + resolved offset | Tính local calendar và chuyển sang Instant |
| `Clock` | Nguồn “now” cùng zone mặc định | Inject vào code và test với `Clock.fixed` |
| `YearMonth` | Một tháng trong một năm | Business period, số ngày thực của tháng |

### 4.1. Tính timestamp cho 09:00 Việt Nam

~~~java
ZoneId hcm = ZoneId.of("Asia/Ho_Chi_Minh");
LocalDateTime local =
        LocalDateTime.of(2027, 1, 1, 9, 0);
Instant utcInstant = local.atZone(hcm).toInstant();
// 2027-01-01T02:00:00Z
~~~

**Snippet Java 17**. Sau khi có `Instant` tuyệt đối, không cộng thêm bảy giờ; timezone đã được áp vào phép chuyển đổi.

### 4.2. ZoneId khác fixed ZoneOffset

`ZoneOffset.of("-05:00")` luôn cố định -05:00. `ZoneId.of("America/New_York")` có thể dùng -05:00 vào mùa đông và -04:00 vào mùa hè theo tzdb. Lịch “09:00 New York mỗi ngày” phải dùng zone theo vùng; offset cố định mùa đông sẽ làm chạy lệch một giờ trong giai đoạn chuyển DST.

### 4.3. Clock.systemDefaultZone() không phải lựa chọn tốt cho cron nghiệp vụ

Một pod UTC, máy phát triển giờ Việt Nam và worker mới ở vùng khác có thể cùng dùng `Clock.systemDefaultZone()` nhưng kết quả cron không explicit zone lại khác nhau. Chọn `@Scheduled(zone = "...")` hoặc `new CronTrigger(expression, ZoneId)`, đồng thời ghi rõ zone của lịch trong data model.

## 5. Test năm mới: 31/12 → 01/01

Giả sử lịch chạy **09:00 ngày 01/01 theo giờ Việt Nam**:

~~~java
new CronTrigger(
        "0 0 9 1 1 *",
        ZoneId.of("Asia/Ho_Chi_Minh"));
~~~

Chọn điểm bắt đầu `2026-12-31T16:59:59Z`, tức `2026-12-31 23:59:59` tại HCM.

Kết quả cron mong đợi:

~~~text
2027-01-01T02:00:00Z
= 2027-01-01 09:00:00 Asia/Ho_Chi_Minh
~~~

Cùng biểu thức nhưng dùng UTC:

~~~text
2027-01-01T09:00:00Z
= 2027-01-01 09:00:00 UTC
= 2027-01-01 16:00:00 Asia/Ho_Chi_Minh
~~~

Hai mốc lệch bảy giờ; không phải lỗi Spring cron, mà là khác biệt zone. Kiểm thử phải assert **UTC Instant**, không chỉ nhìn giờ số 09 trên giao diện.

### 5.1. Biên một giây trước và đúng mốc

Với test “next execution”, thử cả:

- `2027-01-01T01:59:59Z`: còn một giây trước 09:00 HCM; lần kế tiếp phải là `02:00:00Z`.
- `2027-01-01T02:00:00Z`: đúng mốc 09:00 HCM; API lấy lần *kế tiếp sau mốc* có thể trả năm tiếp theo, tùy method và cách cursor được xây.
- `2027-01-01T02:00:01Z`: đã qua mốc, next phải sang kỳ tiếp theo.

Điểm then chốt: phân biệt “khớp tại hiện tại” với “lần kế tiếp” để không vô tình lên lịch trùng một fire đã được xử lý. Với bộ test cụ thể, phải ghi rõ API đang gọi và semantics của mốc đầu vào.

## 6. Test cuối tháng: L, 31, 28 và 29

Quy tắc `0 0 9 L * *` chọn ngày cuối tháng:

| Kỳ | Ngày thực | Instant 09:00 HCM |
| --- | --- | --- |
| Tháng 1/2027 | 31/01/2027 | `2027-01-31T02:00:00Z` |
| Tháng 2/2027 | 28/02/2027 | `2027-02-28T02:00:00Z` |
| Tháng 2/2028 | 29/02/2028 | `2028-02-29T02:00:00Z` |
| Tháng 4/2027 | 30/04/2027 | `2027-04-30T02:00:00Z` |
| Tháng 12/2027 | 31/12/2027 | `2027-12-31T02:00:00Z` |

### 6.1. Ngày 31 không đồng nghĩa cuối tháng

`0 0 9 31 * *` sẽ **bỏ qua** tháng 2,4,6,9,11. Nếu yêu cầu là báo cáo 12 lần/năm, dùng biểu thức này là thiếu nghiệp vụ. Kiểm thử nên có tháng 30 ngày và tháng 2, chứ không chỉ tháng 1 và tháng 3.

### 6.2. Lịch 29/2 chỉ tồn tại ở các năm nhuận

`0 0 9 29 2 *` sau 31/12/2027 sẽ cho lần kế tiếp vào **29/02/2028**, không âm thầm chuyển sang 28/02/2027.

Kiểm tra calendar:

~~~java
System.out.println(YearMonth.of(2027, 2).lengthOfMonth()); // 28
System.out.println(YearMonth.of(2028, 2).lengthOfMonth()); // 29
System.out.println(Year.isLeap(2028)); // true
~~~

**Snippet Java 17**. Quy tắc nhuận của ISO/Gregorian không chỉ là `year % 4 == 0`: năm chia hết cho 100 không nhuận trừ khi chia hết cho 400. `Year.isLeap(2000)` true, `Year.isLeap(2100)` false.

### 6.3. “Sinh ngày 29/2” muốn chuyển sang 28/2 là nghiệp vụ riêng

Nếu chính sách người dùng nói rằng ở năm thường, một sự kiện 29/2 phải chạy ngày 28/2 hoặc 01/3, chỉ một cron cố định `29 2` không đủ. Hãy viết quy tắc business calendar rõ ràng, sau đó test cả năm thường và năm nhuận. Không nhận giá trị “không có mốc” như một lần chạy thành công.

## 7. Test vectors ranh giới có thể kiểm chứng bằng máy

| Case | From Instant | Cron / Zone | Expected next Instant |
| --- | --- | --- | --- |
| Giao thừa | `2026-12-31T16:59:59Z` | `0 0 9 1 1 *`, HCM | `2027-01-01T02:00:00Z` |
| Cùng cron, đổi zone | `2026-12-31T16:59:59Z` | `0 0 9 1 1 *`, UTC | `2027-01-01T09:00:00Z` |
| Cuối tháng 1 | `2027-01-30T00:00:00Z` | `0 0 9 L * *`, HCM | `2027-01-31T02:00:00Z` |
| Từ cuối tháng 1 sang cuối tháng 2 | `2027-01-31T02:00:01Z` | `0 0 9 L * *`, HCM | `2027-02-28T02:00:00Z` |
| Năm nhuận tiếp theo | `2027-12-31T16:59:59Z` | `0 0 9 29 2 *`, HCM | `2028-02-29T02:00:00Z` |

**Vì sao cần cả from và zone?** Một chuỗi cron không biểu thị mốc tuyệt đối nếu chưa có zone. Không lưu một fixture chỉ có expected local string “09:00” và cho rằng thế là đủ: hai nơi đều thấy 09:00 nhưng có thể lệch nhiều giờ theo UTC.

## 8. Java 17 chạy độc lập: leap year, năm mới và DST

Chương trình đầu tiên **không cần Spring**; nó kiểm tra nền tảng Java time để có expected values rõ ràng trước khi test CronTrigger.

Tạo tệp **`CalendarBoundaryJavaDemo.java`**:

~~~java
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.zone.ZoneRules;

public class CalendarBoundaryJavaDemo {

    public static void main(String[] args) {
        ZoneId hcm = ZoneId.of("Asia/Ho_Chi_Minh");

        Instant before = Instant.parse("2026-12-31T16:59:59Z");
        Instant after = Instant.parse("2026-12-31T17:00:00Z");

        System.out.println("before HCM = "
                + before.atZone(hcm).toLocalDateTime());
        System.out.println("new year HCM = "
                + after.atZone(hcm).toLocalDateTime());

        System.out.println("Feb 2027 days = "
                + YearMonth.of(2027, 2).lengthOfMonth());
        System.out.println("Feb 2028 days = "
                + YearMonth.of(2028, 2).lengthOfMonth());
        System.out.println("2028 leap = " + Year.isLeap(2028));

        ZoneId ny = ZoneId.of("America/New_York");
        ZoneRules rules = ny.getRules();

        LocalDateTime gap =
                LocalDateTime.of(2026, 3, 8, 2, 30);
        LocalDateTime overlap =
                LocalDateTime.of(2026, 11, 1, 1, 30);

        System.out.println("NY gap offsets = "
                + rules.getValidOffsets(gap).size());
        System.out.println("NY overlap offsets = "
                + rules.getValidOffsets(overlap).size());

        System.out.println("NY gap transition = "
                + rules.getTransition(gap).isGap());
        System.out.println("NY overlap transition = "
                + rules.getTransition(overlap).isOverlap());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CalendarBoundaryJavaDemo.java
java CalendarBoundaryJavaDemo
~~~

**Stdout dự kiến theo tzdb áp dụng cho các mốc năm 2026:**

~~~text
before HCM = 2026-12-31T23:59:59
new year HCM = 2027-01-01T00:00
Feb 2027 days = 28
Feb 2028 days = 29
2028 leap = true
NY gap offsets = 0
NY overlap offsets = 2
NY gap transition = true
NY overlap transition = true
~~~

### 8.1. Giải thích

- 17:00:00Z ngày 31/12/2026 là 00:00 ngày 01/01/2027 tại Việt Nam; local business year đã đổi.
- 2027 không nhuận và 2028 nhuận, nên tháng 2 có 28/29 ngày.
- 02:30 ngày 08/03/2026 tại New York nằm trong khoảng DST **gap**: không có offset hợp lệ.
- 01:30 ngày 01/11/2026 tại New York nằm trong DST **overlap**: có hai offset hợp lệ.
- `ZoneRules.getTransition` cho biết chuyển đổi tương ứng là gap hay overlap.
- Code không dùng `LocalDateTime.now()` hay timezone mặc định, nên không phụ thuộc ngày hiện tại của developer.

**Giới hạn:** tzdb được đóng gói/phân phối theo môi trường JDK; chính phủ có thể sửa quy tắc chuyển giờ. Các kết quả DST năm 2026 ở trên dựa vào quy tắc được áp dụng cho New York trong tzdb thông dụng. Nếu dữ liệu vùng giờ cập nhật theo luật mới, CI nên ghi rõ sự thay đổi và review expected values.

## 9. DST: một LocalDateTime có 0, 1 hoặc 2 offset

Trong phần lớn thời gian, local datetime ánh xạ tới một offset. Nhưng ở các ngày đổi giờ:

~~~text
New York, spring forward 2026-03-08
01:59:59 -> 03:00:00
02:00 đến 02:59 không tồn tại

New York, fall back 2026-11-01
01:59:59 (offset trước) -> 01:00:00 (offset sau)
01:00 đến 01:59 xuất hiện hai lần
~~~

Với một `LocalDateTime` nằm trong gap hoặc overlap, `ZoneRules.getValidOffsets` trả danh sách kích thước **0** hoặc **2**, không phải 1. Một log chỉ ghi “2026-11-01 01:30” là mơ hồ nếu thiếu `ZoneId`/offset/`Instant`.

~~~java
ZoneId ny = ZoneId.of("America/New_York");

LocalDateTime local =
        LocalDateTime.of(2026, 11, 1, 1, 30);

var offsets = ny.getRules().getValidOffsets(local);
System.out.println(offsets.size()); // 2 theo tzdb nêu trên
~~~

Đây là **snippet Java**, không phải chương trình độc lập.

### 9.1. getOffset(LocalDateTime) có thể che ambiguity

`ZoneRules.getOffset(LocalDateTime)` có thể trả một offset “best effort” khi local time nằm trong gap hoặc overlap. Nếu nghiệp vụ cần biết rõ “01:30 lần thứ nhất hay thứ hai”, kiểm tra `getValidOffsets` và/hoặc `getTransition`, sau đó chọn một chính sách được phê duyệt.

### 9.2. Cron 02:30 ở ngày DST gap

~~~text
0 30 2 * * *
~~~

Nếu dùng zone New York, 02:30 địa phương **không tồn tại** vào ngày chuyển giờ tháng 3/2026. Không tự khẳng định mọi Spring version sẽ “bù lúc 03:00” hoặc “bỏ hẳn ngày” theo một quy tắc universal. Hãy chạy `CronExpression.next` hoặc `CronTrigger.nextExecution` với version triển khai, liệt kê nhiều mốc và so với business policy.

### 9.3. Cron 01:30 ở ngày DST overlap

~~~text
0 30 1 * * *
~~~

Giờ địa phương có thể tương ứng hai `Instant` vào ngày fall-back. Cần kiểm chứng **một hay hai occurrences** theo implementation và trigger mode đang dùng. Dù engine phát ra một hay hai fires, tầng nghiệp vụ phải xác định có cho phép hai logical operations trong cùng business day hay không.

### 9.4. Local time có thể lùi; Instant phải tiến

Ở overlap, thời gian hiển thị có thể quay từ 01:59 về 01:00. Nhưng hai timestamp tuyệt đối trên timeline vẫn tăng. Trong test chuỗi cron, hãy xác nhận `nextInstant.isAfter(previousInstant)`, **không** đòi mỗi `LocalTime` lớn hơn lần trước.

## 10. Clock.fixed + SimpleTriggerContext + CronTrigger

Spring cung cấp:

~~~java
Instant nextExecution(TriggerContext context);
~~~

Một test tính lịch không cần worker:

~~~java
Clock fixed = Clock.fixed(
        Instant.parse("2026-12-31T16:59:59Z"),
        ZoneId.of("UTC"));

SimpleTriggerContext context =
        new SimpleTriggerContext(fixed);

CronTrigger trigger = new CronTrigger(
        "0 0 9 1 1 *",
        ZoneId.of("Asia/Ho_Chi_Minh"));

Instant next = trigger.nextExecution(context);
// 2027-01-01T02:00:00Z
~~~

Đây là **Spring snippet**; cần imports `java.time.Clock`/`Instant`/`ZoneId`, `CronTrigger`, `SimpleTriggerContext` và dependency `spring-context`.

Nó thực hiện **phép tính next planned Instant**. Không có `Thread.sleep`, không cần bật `@EnableScheduling`, không mở scheduler pool, không kiểm tra service thật có commit dữ liệu hay không.

### 10.1. Clock context và timezone explicit

Nếu `CronTrigger` được tạo bằng constructor có `ZoneId` thì zone đó dùng để diễn giải cron. Nếu không truyền zone, mặc định phụ thuộc clock/trigger context của scheduler. Một clock có zone UTC **không buộc** cron HCM explicit trở thành UTC.

### 10.2. Clock.fixed không phải virtual-time scheduler

`Clock.fixed` luôn báo cùng một `Instant`. `Clock.offset(fixed, Duration.ofHours(1))` tạo một clock khác, không tự làm delayed queue của `ThreadPoolTaskScheduler` chạy nhanh thêm một giờ. Khi cần test “cron 09:00 sẽ tính lúc nào?”, hỏi `nextExecution`; khi cần test execution thật, dùng scheduler/test harness có điều khiển và đợi `CountDownLatch`/future có timeout.

### 10.3. Spring CronTrigger không tự lưu business history

`SimpleTriggerContext` có thể giữ `lastScheduledExecution`, `lastActualExecution` và `lastCompletion`, nhưng trạng thái này dùng cho phép tính trigger, không phải job database. Nếu app restart, một Spring scheduler in-memory không tự replay mọi mốc bị lỡ qua nhiều ngày.

## 11. Spring Context Maven chạy được: xác minh năm mới, tháng và năm nhuận

Dự án chỉ cần Spring Context và Java 17. Không có HTTP server hoặc thread scheduler. Nó kiểm chứng **các kết quả tính lịch**, nên không flaky theo CPU/OS.

Cấu trúc:

~~~text
cron-boundary-demo/
├── pom.xml
└── src/main/java/demo/CronBoundarySpringDemo.java
~~~

Tệp **`pom.xml`**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>cron-boundary-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.16</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Tệp **`src/main/java/demo/CronBoundarySpringDemo.java`**:

~~~java
package demo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

public class CronBoundarySpringDemo {

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-12-31T16:59:59Z"),
                    ZoneOffset.UTC);
        }

        @Bean
        BoundaryService boundaryService(Clock fixedClock) {
            return new BoundaryService(fixedClock);
        }
    }

    static class BoundaryService {
        private final Clock clock;

        BoundaryService(Clock clock) {
            this.clock = clock;
        }

        Instant first(String expression, ZoneId zone) {
            return new CronTrigger(expression, zone)
                    .nextExecution(new SimpleTriggerContext(clock));
        }

        Instant nextFrom(String expression, ZoneId zone,
                         String fromIsoUtc) {
            Clock at = Clock.fixed(
                    Instant.parse(fromIsoUtc), ZoneOffset.UTC);
            return new CronTrigger(expression, zone)
                    .nextExecution(new SimpleTriggerContext(at));
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            BoundaryService svc =
                    context.getBean(BoundaryService.class);

            ZoneId hcm = ZoneId.of("Asia/Ho_Chi_Minh");
            ZoneId utc = ZoneOffset.UTC;

            System.out.println("new year HCM = "
                    + svc.first("0 0 9 1 1 *", hcm));

            System.out.println("new year UTC = "
                    + svc.first("0 0 9 1 1 *", utc));

            System.out.println("Jan last day = "
                    + svc.nextFrom("0 0 9 L * *", hcm,
                            "2027-01-30T00:00:00Z"));

            System.out.println("Feb last day = "
                    + svc.nextFrom("0 0 9 L * *", hcm,
                            "2027-01-31T02:00:01Z"));

            System.out.println("next leap day = "
                    + svc.nextFrom("0 0 9 29 2 *", hcm,
                            "2027-12-31T16:59:59Z"));
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.CronBoundarySpringDemo
~~~

**Stdout của class demo khi chạy thành công:**

~~~text
new year HCM = 2027-01-01T02:00:00Z
new year UTC = 2027-01-01T09:00:00Z
Jan last day = 2027-01-31T02:00:00Z
Feb last day = 2027-02-28T02:00:00Z
next leap day = 2028-02-29T02:00:00Z
~~~

### 11.1. Giải thích đường chạy

- `Clock.fixed` đặt mốc 2026-12-31T16:59:59Z cho service. Spring context tạo bean và gọi method kiểm chứng, không tạo worker timer.
- `first` dùng timezone HCM hoặc UTC explicit, nên same cron ra hai Instant khác nhau.
- `nextFrom` tạo clock cố định mới cho từng case, bao gồm mốc sau 31/01/2027 và sau 31/12/2027.
- `L` tìm ngày cuối phù hợp, không giả định tháng 2 có 31 ngày.
- `29 2` bỏ các năm không có 29/02, trả 29/02/2028.
- `try-with-resources` đóng Spring context; không để lại scheduler thread vì demo chưa hề khởi tạo `TaskScheduler`.

**Đây là chương trình thực thi được**, khác với snippet `@Scheduled` phía dưới. Trong môi trường Maven có log riêng, kết quả do class in vẫn là năm dòng trên. Nếu timezone database thay đổi quy tắc vùng, cần review test vector tương ứng.

## 12. Sinh N lần kế tiếp: CronExpression.next(ZonedDateTime)

Một test chỉ kiểm tra một mốc có thể không phát hiện lệch khi sang tháng thứ hai. Dùng `CronExpression` với một `ZonedDateTime` mang `ZoneId`:

~~~java
ZoneId hcm = ZoneId.of("Asia/Ho_Chi_Minh");
CronExpression expression =
        CronExpression.parse("0 0 9 L * *");

ZonedDateTime cursor = ZonedDateTime.ofInstant(
        Instant.parse("2026-12-30T00:00:00Z"), hcm);

for (int i = 0; i < 14; i++) {
    ZonedDateTime next = expression.next(cursor);
    if (next == null) {
        throw new IllegalStateException("No next fire");
    }
    System.out.println(next.toInstant() + " | " + next);
    cursor = next;
}
~~~

**Snippet Spring**, cần import `CronExpression`, `ZoneId`, `ZonedDateTime`, `Instant`. Mục tiêu là sinh 14 tháng, đủ để đi qua tháng 2 và năm mới. Ba dòng đầu dự kiến:

~~~text
2026-12-31T02:00:00Z | 2026-12-31T09:00+07:00[Asia/Ho_Chi_Minh]
2027-01-31T02:00:00Z | 2027-01-31T09:00+07:00[Asia/Ho_Chi_Minh]
2027-02-28T02:00:00Z | 2027-02-28T09:00+07:00[Asia/Ho_Chi_Minh]
~~~

Không phải output toàn bộ vòng lặp 14 lần, chỉ minh họa ba dòng đầu.

### 12.1. Assertions cần có

- `next != null` (trừ khi business cho phép lịch đã hết).
- `next.toInstant().isAfter(cursor.toInstant())`: mốc tuyệt đối tăng.
- `next.getHour() == 9` và `next.getMinute() == 0` khi 09:00 là giờ hợp lệ.
- `next.getDayOfMonth() == next.toLocalDate().lengthOfMonth()` với rule `L`.
- Với rule ngày đầu tháng, `getDayOfMonth() == 1`.
- Với rule 29/2, ngày/tháng luôn là 29/2 và năm phải nhuận.
- Với một lịch “một lần mỗi business day”, không có hai side effects theo cùng business key, ngay cả ở DST overlap.
- Test cả thời điểm **trước, đúng và sau** mốc fire, không chỉ từ một ngày ngẫu nhiên.

### 12.2. Tại sao không truyền Instant trực tiếp vào CronExpression.next?

`CronExpression.next` tính bằng một `Temporal` cung cấp các trường calendar và timezone thích hợp. Nên dùng `ZonedDateTime` thay vì một `Instant` không có khái niệm ngày/tháng/weekday địa phương tự thân. `CronTrigger.nextExecution` là API khác, có nhiệm vụ chuyển quy tắc lịch sang `Instant` dựa vào `TriggerContext` và zone.

## 13. Spring @Scheduled với zone rõ ràng

Đây là ví dụ **snippet trong ứng dụng Spring**, không phải chương trình độc lập:

~~~java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonthCloseJob {

    @Scheduled(
            cron = "0 0 9 L * *",
            zone = "Asia/Ho_Chi_Minh")
    public void closeMonth() {
        // Xác định kỳ nghiệp vụ nào cần xử lý.
        // Claim idempotent job key và cập nhật trạng thái.
    }
}
~~~

Cấu hình kích hoạt:

~~~java
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulerConfig {

    @Bean("taskScheduler")
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setClock(Clock.systemUTC());
        s.setPoolSize(2);
        s.setThreadNamePrefix("calendar-");
        return s;
    }
}
~~~

Clock chung của scheduler dùng UTC, **nhưng** `@Scheduled.zone` đã explicit HCM, nên cron `09:00` vẫn theo HCM. Đây là lựa chọn dễ audit hơn thay vì tin vào timezone mặc định của container.

Nếu bỏ `zone`:

~~~java
@Scheduled(cron = "0 0 9 L * *")
public void closeMonth() { /* ... */ }
~~~

Spring dùng timezone của scheduler/clock theo default; hai môi trường có default zone khác nhau có thể đưa ra hai Instant khác nhau. Các mốc kiểm chứng từ lúc phát triển phải chạy trong cả timezone khác UTC nếu business phụ thuộc local time.

### 13.1. @Scheduled không tự bảo đảm job hoàn tất

Method có thể bắt đầu trễ vì pool bận, lỗi I/O, lock hoặc GC. Khi đã gọi method, side effect có thể fail sau một transaction hoặc remote call. Chỉ tính `nextExecution` không kiểm chứng được deadline, rollback hay trạng thái DB. Cần integration test riêng.

### 13.2. @Scheduled trên nhiều pod có thể chạy nhiều lần

Nếu ba pods cùng bật `@Scheduled`, mỗi pod có thể tạo một invocation. Cùng `ZoneId` chỉ giúp chúng **tính mốc giống nhau** chứ không chọn một pod duy nhất. Job có side effect phải có ownership/idempotency trên storage dùng chung.

## 14. Lenient và fixed execution khi task kéo dài

Một cron `0 0 9 * * *` dự kiến 09:00 mỗi ngày, nhưng task ở 09:00 hôm trước vẫn chạy tới 09:20 ngày hôm sau. Spring `CronTrigger` có các semantics khác nhau tùy constructor/factory:

- `new CronTrigger(expression)` hoặc `CronTrigger.forLenientExecution(expression)`: lenient execution, dựa vào `lastCompletion` để tính lịch tiếp theo; có thể **bỏ qua** một số mốc đã đi qua.
- `CronTrigger.forFixedExecution(expression)`: tính theo `lastScheduledExecution`, có thể tạo các lần thực thi bù cho mốc đã lỡ khi runtime còn theo dõi trạng thái.
- Các factory này có từ Spring Framework 6.1.3, nhưng API/semantics cụ thể phải được kiểm tra theo version ứng dụng.
- `CronTrigger(expression, ZoneId)` là một lựa chọn explicit zone với semantics lenient thông thường. Các factory fixed/lenient dạng chuỗi không tự chứa ZoneId; chúng dựa vào scheduler/trigger context phù hợp.

### 14.1. SimpleTriggerContext chứa lịch sử

~~~java
Clock fixed = Clock.fixed(
        Instant.parse("2027-01-01T02:20:00Z"),
        ZoneId.of("Asia/Ho_Chi_Minh"));

SimpleTriggerContext context =
        new SimpleTriggerContext(fixed);

context.update(
        Instant.parse("2027-01-01T02:00:00Z"),
        Instant.parse("2027-01-01T02:06:00Z"),
        Instant.parse("2027-01-01T02:12:00Z"));
~~~

**Snippet Spring**. Ba Instant lần lượt là `lastScheduledExecution`, `lastActualExecution` và `lastCompletion`. Chúng không đồng nhất nếu worker khởi đầu muộn hoặc task chạy lâu.

### 14.2. Không nhầm fixed execution với job store bền

Fixed execution có thể giúp xử lý mốc đã bỏ lỡ theo semantics trigger trong runtime, nhưng không phải database lưu tất cả planned events qua crash/restart. Nếu billing bắt buộc xử lý đủ mỗi tháng, phải có job store/period watermark và phát hiện kỳ chưa commit.

### 14.3. Reconciliation theo business period

Ví dụ một job xử lý tháng 12/2026 bị retry ngày 02/01/2027. Logical key vẫn phải là `(tenantId, YearMonth.of(2026,12), jobType)`, không phải tháng 01/2027 chỉ vì “now” đã đổi.

Đừng để attempt number, server timezone hoặc timestamp hiện tại làm thay đổi identity của cùng logical operation.

## 15. Lịch ngày làm việc không đồng nghĩa lịch lễ

Spring cron có `1W` và `LW` để chọn weekday, nhưng:

- Không biết Tết Nguyên Đán, Quốc khánh, lịch đóng cửa ngân hàng hoặc ngày nghỉ riêng của tenant.
- Nếu yêu cầu “ngày giao dịch cuối cùng trong tháng” theo thị trường, cần holiday calendar có phiên bản, không chỉ weekday.
- Nếu một ngày bị đổi lịch nghỉ công bố muộn, phải xác định cách reschedule hoặc tính bù.
- Nếu business cutoff là “sau khi hoàn tất dữ liệu giao dịch”, 09:00 cuối tháng có thể còn quá sớm.

Một snippet mô tả ứng dụng:

~~~java
LocalDate date =
        Instant.now(clock).atZone(tenantZone).toLocalDate();

if (calendar.isLastBusinessDay(tenantId, date)) {
    startIdempotentClose(tenantId, YearMonth.from(date));
}
~~~

**Snippet**: cần `clock`, `tenantZone`, `calendar` và job service. Nếu cần chạy job lâu, lưu `YearMonth` trong task input thay vì tính lại từ `LocalDate.now()` ở lần retry sau.

## 16. Cách kiểm chứng CronTrigger qua DST

Để biết engine đang làm gì ở New York:

1. Chọn JDK version/tzdb và Spring version đang triển khai.
2. Chọn `ZoneId.of("America/New_York")`, không dùng fixed -05:00.
3. Với cron 02:30, đặt cursor trước ngày 08/03/2026.
4. Với cron 01:30, đặt cursor trước ngày 01/11/2026.
5. Liệt kê ít nhất 5 lần next, chuyển mỗi kết quả thành `Instant`, local datetime và offset.
6. Kiểm tra local date, whether gap/overlap, số occurrences theo ngày.
7. So với policy business: skip, shift, one effect/day hay xử lý từng occurrence.
8. Chạy lại sau khi upgrade Spring hoặc tzdb.

Ví dụ **snippet chỉ tính toán**:

~~~java
ZoneId ny = ZoneId.of("America/New_York");
CronExpression expression =
        CronExpression.parse("0 30 1 * * *");

ZonedDateTime cursor = ZonedDateTime.ofInstant(
        Instant.parse("2026-10-31T00:00:00Z"), ny);

for (int i = 0; i < 5; i++) {
    ZonedDateTime next = expression.next(cursor);
    if (next == null) {
        throw new IllegalStateException("No next occurrence");
    }
    System.out.println(
            "instant=" + next.toInstant()
            + " local=" + next.toLocalDateTime()
            + " offset=" + next.getOffset());
    cursor = next;
}
~~~

**Không cố ghi expected stdout cụ thể ở đây:** mục tiêu là **khảo sát và phê duyệt DST behavior của phiên bản engine/tzdb đang dùng**, không bịa rằng mọi cron engine phải phát một hay hai fires. Khi đã thống nhất policy, chuyển kết quả thật thành golden test vectors.

### 16.1. Vì sao cần kiểm tra cả Instant và local time?

Trong overlap, hai `Instant` có thể có cùng `LocalDateTime` là 01:30 nhưng khác offset; log chỉ local time sẽ gây nhầm thành một lần. Trong gap, một `LocalDateTime` 02:30 không có Instant hợp lệ, nên không được tự gắn “Z” vào chuỗi rồi coi đó là giờ đúng.

### 16.2. Không đo khoảng cách ngày bằng Duration.ofHours(24)

Một ngày theo lịch địa phương tại zone có DST có thể dài 23 hoặc 25 giờ theo elapsed time. Cron 09:00 hằng ngày khác `fixedRate(Duration.ofHours(24))` trong các mùa đổi giờ. Dùng calendar rule + ZoneId nếu yêu cầu là giờ địa phương.

## 17. Trường hợp tenant đổi múi giờ

Giả sử tenant đổi lịch từ `Asia/Ho_Chi_Minh` sang `Europe/Berlin` từ năm mới. Phải phân biệt ba dữ liệu:

1. `createdAt`: thời điểm thực UTC của yêu cầu, không đổi.
2. `scheduleZone`: ZoneId được áp dụng cho cron tại một phiên bản lịch.
3. `businessPeriod`: kỳ mà job sẽ xử lý, phải ổn định qua retry/redeploy.

Một metadata record minh họa:

~~~text
tenantId: tenant-42
expression: 0 0 9 L * *
scheduleZone: Europe/Berlin
effectiveFrom: 2027-01-01T00:00:00Z
scheduleVersion: 3

Previous schedule:
scheduleZone: Asia/Ho_Chi_Minh
effectiveUntil: 2027-01-01T00:00:00Z
~~~

Đây là **mô hình tài liệu**, không phải cấu hình executable. Khi đổi zone, mốc pending `scheduledAt` có thể cần được tính lại. Cần ownership của thao tác hủy/re-register; không đổi global `TimeZone.setDefault` và giả định mọi pending task sẽ tự điều chỉnh.

Cần trả lời trước khi release:

- Job tháng 12 đã fire theo zone cũ có được fire thêm theo zone mới không?
- Nếu công việc đang chạy khi đổi zone, có đổi `businessPeriod` của job đó không?
- Khi rollback cấu hình, có tái mở các kỳ đã xử lý không?
- Nếu nhiều pod cập nhật cấu hình không đồng thời, cơ chế claim nào bảo vệ duplicate?
- Có lưu version zone dùng để tính `scheduledAt` cho audit về sau không?

**Tốt nhất:** persist lịch và phiên bản có hiệu lực, tính next Instant có đối chiếu, claim job idempotent theo business key.

## 18. Dùng job store để vượt qua restart/overlap cluster

Cron không phải job database. Một thiết kế đơn giản:

~~~text
Cron trigger -> business calendar scan
                       |
                       v
            Find missing business periods
                       |
                       v
            Atomic claim (unique business key)
                       |
                       v
             Bounded worker + retry budget
                       |
                       v
             Durable status and result
~~~

Một bảng PostgreSQL minh họa:

~~~sql
CREATE TABLE calendar_jobs (
    tenant_id        VARCHAR(100) NOT NULL,
    job_type         VARCHAR(80) NOT NULL,
    business_period  VARCHAR(20) NOT NULL,
    status           VARCHAR(30) NOT NULL,
    schedule_version INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, job_type, business_period)
);
~~~

Schema này chỉ minh họa unique business key. Production cần transaction, lease/fencing/recovery, retry status và audit. Unique key ở DB **không** tự đảm bảo side effect với hệ thống ngoài cũng atomic; nếu gọi payment provider hoặc publish event, cần idempotency key ở provider/outbox/reconciliation.

### 18.1. Một kỳ qua năm mới vẫn phải giữ nguyên key

Ví dụ job tháng 12/2026 bắt đầu ngày 31/12/2026 nhưng do downstream timeout nên retry ngày 01/01/2027. Dù “now” đã thuộc tháng 1, nó vẫn là job `business_period="2026-12"`. Nếu tạo key mới theo tháng đang chạy, bạn có thể ghi kết quả sai kỳ hoặc tạo duplicate.

### 18.2. Nhiều pods không được tự tạo nhiều hiệu ứng

Ba replica có thể cùng tính ra planned Instant. Cơ chế distributed claim hoặc queue consumer coordination quyết định owner thực thi; transaction/unique business key bảo vệ dữ liệu. Một `synchronized`/`AtomicBoolean` cục bộ chỉ có tác dụng trong một JVM.

### 18.3. Cron có thể bỏ mốc lúc downtime

Nếu application tắt đúng 09:00 ngày cuối tháng, scheduler RAM-only không đảm bảo lúc 09:10 restart sẽ tự chạy bù mốc đã lỡ. Job store cần tính các kỳ chưa hoàn tất từ business calendar, không chỉ đếm tick.

## 19. Unit test JUnit 5 cho các case quan trọng

### 19.1. Test ngày đầu năm HCM

~~~java
@Test
void januaryFirstInVietnam() {
    Clock fixed = Clock.fixed(
            Instant.parse("2026-12-31T16:59:59Z"),
            ZoneOffset.UTC);

    CronTrigger trigger = new CronTrigger(
            "0 0 9 1 1 *",
            ZoneId.of("Asia/Ho_Chi_Minh"));

    assertEquals(
            Instant.parse("2027-01-01T02:00:00Z"),
            trigger.nextExecution(new SimpleTriggerContext(fixed)));
}
~~~

Đây là **JUnit 5 snippet**, cần `@Test`, `assertEquals`, Java time và Spring imports. Không chờ cron ngoài đời.

### 19.2. Test nhiều tháng với last day

~~~java
CronExpression exp =
        CronExpression.parse("0 0 9 L * *");
ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

ZonedDateTime cursor = ZonedDateTime.ofInstant(
        Instant.parse("2026-12-30T00:00:00Z"), zone);

for (int i = 0; i < 14; i++) {
    ZonedDateTime next = exp.next(cursor);
    assertNotNull(next);
    assertTrue(next.toInstant().isAfter(cursor.toInstant()));
    assertEquals(
            next.toLocalDate().lengthOfMonth(),
            next.getDayOfMonth());
    assertEquals(9, next.getHour());
    cursor = next;
}
~~~

Đây là **JUnit snippet**: các assertions thuộc JUnit 5. Nó kiểm chứng tháng 30/31/28 ngày và ranh giới năm trong nhiều lần, không chỉ một output mẫu.

### 19.3. Test năm nhuận thế kỷ

~~~java
assertTrue(Year.isLeap(2000));
assertFalse(Year.isLeap(2100));
assertEquals(29, YearMonth.of(2028, 2).lengthOfMonth());
assertEquals(28, YearMonth.of(2027, 2).lengthOfMonth());
~~~

Đây là **JUnit snippet**. Mục tiêu là tránh viết logic `year % 4 == 0` thiếu quy tắc thế kỷ.

### 19.4. Test DST gap/overlap

~~~java
ZoneId ny = ZoneId.of("America/New_York");

LocalDateTime gap =
        LocalDateTime.of(2026, 3, 8, 2, 30);
LocalDateTime overlap =
        LocalDateTime.of(2026, 11, 1, 1, 30);

assertEquals(0, ny.getRules().getValidOffsets(gap).size());
assertEquals(2, ny.getRules().getValidOffsets(overlap).size());
~~~

Đây là **JUnit snippet** phụ thuộc tzdb quy tắc áp dụng tại các mốc năm 2026. Khi tzdb được cập nhật, kiểm tra lại expected theo luật vùng thời gian đã được triển khai.

### 19.5. Test cùng cron khác zone

Cho cùng `fromInstant` và expression 09:00 ngày 1/1, xác nhận HCM next = 02:00Z và UTC next = 09:00Z. Không chỉ kiểm tra local displayed time, vì cả hai đều in 09:00 theo zone của chúng.

### 19.6. Test ngay trước, ngay đúng và sau mốc

Chọn mốc `2027-01-01T02:00:00Z` rồi test từ một giây trước, đúng mốc và một giây sau. Ghi rõ semantics “next strictly after” của API đã chọn. Không để scheduler lặp mãi một mốc hoặc xử lý lại business period vì cursor sai.

### 19.7. Test không phụ thuộc timezone laptop

Cùng test cần pass trên máy UTC và máy HCM vì zone được truyền explicit. Đừng dùng `TimeZone.setDefault` global giữa các test chạy song song — có thể gây race. Inject Clock/ZoneId thay vì sửa JVM default.

## 20. Monitoring: planned vs actual

Một event scheduler nên phân biệt:

~~~text
plannedAt  = 2027-01-01T02:00:00Z
actualAt   = 2027-01-01T02:00:04Z
finishedAt = 2027-01-01T02:00:09Z

scheduleLag = 4 seconds
runDuration = 5 seconds
~~~

Với job theo timezone, log đủ:

- `plannedAt`/`actualAt` dưới dạng `Instant` UTC.
- `businessDate` hoặc `businessPeriod` theo zone nghiệp vụ.
- `scheduleZone` và **offset** tại thời điểm.
- `scheduleVersion` để biết rule cũ/mới.
- `jobId`, `tenantId`, `attempt` và kết quả (không log thông tin nhạy cảm).
- `wasDuplicateClaim`, `wasMissedAndRecovered`, `errorType`.

Metrics có thể gồm `cron_schedule_lag_seconds`, `cron_job_duration_seconds`, `cron_missed_periods_total`, `cron_duplicate_claims_total` và `cron_execution_errors_total`. Đừng dùng job UUID làm label trong Prometheus nếu gây cardinality rất cao.

Đo elapsed runtime bằng `System.nanoTime()` hoặc metrics timer phù hợp; `System.nanoTime()` **không phải Unix timestamp**. Nếu planned/actual được đo ở các máy clock lệch nhau, cần đánh giá clock skew khi suy schedule lag.

## 21. Những ngộ nhận phổ biến

**1. “Cron hợp lệ về cú pháp thì chắc chắn đúng giờ nghiệp vụ.”** Sai: timezone, DST, month length và business rules vẫn có thể sai.

**2. “Spring cron có năm trường giống Linux.”** Sai: Spring có sáu trường, bao gồm giây.

**3. “Tháng nào cũng có ngày 31.”** Sai. Dùng `L` nếu cần ngày cuối tháng.

**4. “Lịch 29/2 sẽ chạy 28/2 năm thường.”** Không mặc định. Đó là business rule khác.

**5. “LocalDateTime có thể đổi sang Instant mà không cần zone.”** Sai. Giờ có thể không tồn tại hoặc trùng offset.

**6. “-05:00 luôn là giờ New York.”** Sai trong mùa hè khi có DST.

**7. “Asia/Ho_Chi_Minh hiện có ngày DST giống New York.”** Không. Dùng zone có DST để test gap/overlap.

**8. “Giờ hiển thị 01:30 chứng minh cùng một instant.”** Sai ở DST overlap.

**9. “Clock.fixed làm worker tự chạy khi tăng thời gian giả.”** Sai: nó không điều khiển executor queue.

**10. “CronExpression.next nên nhận Instant không có zone.”** Không nên; dùng `ZonedDateTime` mang zone cho các trường calendar.

**11. “UTC date luôn là business date.”** Sai quanh năm mới với zone khác UTC.

**12. “ZoneId.of khác timezone server nên không cần test.”** Vẫn cần test target zone, DST và cấu hình.

**13. “Mỗi tenant đổi zone thì cứ sửa TimeZone.setDefault của JVM.”** Sai: default global không đại diện nhiều tenant và gây rủi ro test/concurrency.

**14. “Lịch L và W đã tính ngày nghỉ lễ.”** Sai: còn holiday calendar.

**15. “Một lời gọi nextExecution đúng nghĩa là task đã chạy.”** Sai: mới tính mốc dự kiến.

**16. “@Scheduled tự đảm bảo một lần trên ba pod.”** Sai. Cần distributed claim và idempotency.

**17. “Fixed CronTrigger có nghĩa lưu mọi mốc qua crash.”** Sai. Recovery cần durable job store.

**18. “Chỉ cần kiểm thử một ngày bình thường.”** Sai: phải bao phủ tháng 2, tháng 30/31 ngày, giao thừa và DST.

**19. “Năm chia hết cho 4 luôn nhuận.”** Sai ở năm chia hết 100 nhưng không chia hết 400.

**20. “Khi retry ở tháng mới, đổi businessPeriod của job tháng cũ.”** Sai. Retry phải giữ logical operation identity.

**21. “Cùng chuỗi cron ở hai zone luôn là cùng Instant.”** Sai. HCM và UTC 09:00 lệch bảy giờ.

**22. “Đổi tzdb là thay đổi kỹ thuật không ảnh hưởng business.”** Có thể ảnh hưởng mọi lịch theo vùng có đổi luật; phải review lịch đã persisted.

## 22. Câu hỏi tự kiểm tra

**Câu 1:** `0 0 9 L * *` có chạy ngày 28/02/2027 không?

**Đáp án:** có, 28/02 là ngày cuối tháng 2/2027. 09:00 HCM là 02:00Z.

**Câu 2:** `0 0 9 31 * *` có chạy tháng 4 không?

**Đáp án:** không; tháng 4 có 30 ngày.

**Câu 3:** `0 0 9 29 2 *` từ cuối năm 2027 có mốc gần nhất nào?

**Đáp án:** 29/02/2028 09:00 theo zone, vì 2028 nhuận.

**Câu 4:** 2026-12-31T17:00Z thuộc năm nào ở HCM?

**Đáp án:** 2027, ngày địa phương là 01/01/2027.

**Câu 5:** 09:00 01/01/2027 HCM và UTC có cùng Instant không?

**Đáp án:** không, lần lượt là 02:00Z và 09:00Z.

**Câu 6:** Vì sao 02:30 New York ngày 08/03/2026 không thể có offset bình thường?

**Đáp án:** nó nằm trong DST gap, giờ địa phương đó bị bỏ qua khi đồng hồ nhảy tới trước.

**Câu 7:** 01:30 New York ngày 01/11/2026 có thể có bao nhiêu offset?

**Đáp án:** hai, vì fall-back overlap.

**Câu 8:** Khi tenant đổi zone, nên thay lại mọi businessPeriod đã ghi nhận không?

**Đáp án:** không tự động. Cần chính sách lịch sử và schedule version; kỳ đã xử lý phải giữ identity đúng.

**Câu 9:** `CronTrigger.nextExecution` có kiểm chứng database effect thành công không?

**Đáp án:** không, nó chỉ tính mốc lịch.

**Câu 10:** Tại sao cần test từ một giây trước và sau mốc?

**Đáp án:** để kiểm chứng semantics next-occurrence và không bỏ/trùng một fire do lỗi cursor boundary.

**Câu 11:** `LW` đã bảo đảm là ngày làm việc của ngân hàng?

**Đáp án:** không, nó không biết lịch ngày lễ ngân hàng.

**Câu 12:** Ba pod cùng cron và zone cần gì để không phát hành hóa đơn trùng?

**Đáp án:** unique business key, atomic claim/coordination và idempotency ở side effect.

## 23. Checklist trước khi release

- [ ] Xác định Spring cron sáu trường và đúng dialect.
- [ ] Kiểm tra parse và **N mốc kế tiếp** qua tháng/năm.
- [ ] Có `ZoneId` explicit cho cron theo giờ địa phương.
- [ ] Assert UTC Instant và local date/time + offset.
- [ ] Test 31/12–01/01; 31/01–28/02; tháng 30/31 ngày.
- [ ] Test 29/02 ở năm thường và năm nhuận, cả quy tắc năm 2100.
- [ ] Dùng `L` cho last day; không dùng ngày 31 như bản thay thế.
- [ ] Test gap/overlap với zone có DST thật.
- [ ] Document chính sách khi giờ không tồn tại hoặc xảy ra hai lần.
- [ ] Test `CronExpression.next(ZonedDateTime)`/`CronTrigger.nextExecution` trên engine/version thực tế.
- [ ] Dùng `Clock.fixed` cho pure time tests; không đợi lịch thật.
- [ ] Không nhầm Clock giả với virtual scheduler điều khiển delayed queue.
- [ ] Phân biệt lenient/fixed execution, last completion/scheduled.
- [ ] Định nghĩa ngày làm việc nội bộ nếu có lịch nghỉ lễ.
- [ ] Persist business period/key, timezone và schedule version.
- [ ] Có recovery/backfill khi app restart đúng dịp cuối tháng.
- [ ] Có atomic claim/idempotency cho multi-pod.
- [ ] Có timeout, retry budget và cancellation trong job thực.
- [ ] Đo scheduled lag và execution duration.
- [ ] Test sau nâng cấp Spring, JDK/tzdb hoặc timezone của tenant.
- [ ] Không ghi credential/payload nhạy cảm vào job log.
- [ ] Đóng context/executor sau integration tests.

## 24. Kết luận

**Lịch cron cần được kiểm chứng bằng những thời điểm thực, không chỉ bằng một chuỗi “09:00”.** Với Spring 6.2, kết hợp `Clock.fixed`, `SimpleTriggerContext` và `CronTrigger.nextExecution` để assert mốc đầu năm/cuối tháng; dùng `CronExpression.next(ZonedDateTime)` để duyệt nhiều kỳ và kiểm tra calendar. Với DST, dùng `ZoneRules.getValidOffsets` để biết giờ địa phương có **0, 1 hay 2 offset**, sau đó xác nhận behavior CronTrigger và chọn policy nghiệp vụ rõ.

Một cron tính đúng vẫn có thể chạy muộn, bị bỏ do restart, trùng trên nhiều pod hoặc thất bại ở business side effect. Vì vậy, cần tách **planned Instant**, **execution**, **businessPeriod**, **idempotency** và **recovery**. Như vậy hệ thống mới đúng qua giao thừa, tháng hai, đổi timezone và mọi đợt triển khai.

## Tài liệu tham khảo

- Spring Framework 6.2 — `CronExpression`, các trường cron/L/W/#: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronExpression.html
- Spring Framework 6.2 — `CronTrigger`, zone, fixed/lenient execution: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/CronTrigger.html
- Spring Framework 6.2 — `SimpleTriggerContext`: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/support/SimpleTriggerContext.html
- Spring Framework 6.2 — `@Scheduled` (cron/zone): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html
- Spring Framework — Task Execution and Scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Blog — Improved Cron Expressions: https://spring.io/blog/2020/11/10/new-in-spring-5-3-improved-cron-expressions/
- Java SE 17 — `ZoneRules`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/zone/ZoneRules.html
- Java SE 17 — `ZonedDateTime`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html
- Java SE 17 — `Clock`/`Instant`/`YearMonth`/`Year`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/package-summary.html
- Java SE 17 — `ZoneRulesProvider`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/zone/ZoneRulesProvider.html


</details>

- [Quay lại đầu trang](#back-to-top)
