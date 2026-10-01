# ZoneOffset, ZoneId và quy tắc múi giờ

Khi chuyển từ ngày-giờ cục bộ sang dòng thời gian, ta cần biết đồng hồ cục bộ liên hệ với UTC như thế nào. Java có hai khái niệm rất dễ bị nhầm: `ZoneOffset` và `ZoneId`.

### Múi giờ thực ra là gì?

Trong phần mềm, một **múi giờ** không nên được hiểu đơn giản là “UTC+7” hay “UTC-5”. Với một vùng có tên, múi giờ là **tập quy tắc dùng để ánh xạ giữa dòng thời gian toàn cục và thời gian dân sự cục bộ của khu vực đó**.

Nó có thể trả lời:

```text
`Instant` này ở Paris hiển thị mấy giờ?
`LocalDateTime` này tại New York dùng độ lệch nào?
Ngày này có lần chuyển đổi DST không?
Giờ cục bộ này có tồn tại một lần, hai lần hay không tồn tại?
```

Quan hệ quan trọng:

```text
ZoneId
→ định danh bộ quy tắc của múi giờ

ZoneRules
→ chính các quy tắc thay đổi theo thời gian

ZoneOffset
→ một độ lệch cụ thể được áp dụng tại một thời điểm/ngữ cảnh
```

Vì vậy **múi giờ không đồng nghĩa với độ lệch UTC**. Độ lệch chỉ là một phần/kết quả của quy tắc tại một thời điểm.

### DST là gì? — biết trước khi gặp khoảng trống/chồng lặp

**DST (Daylight Saving Time)** là chính sách mà **một số** quốc gia/khu vực thay đổi độ lệch UTC hoặc đồng hồ cục bộ trong một phần của năm. Không phải nơi nào cũng dùng DST, và quy tắc có thể thay đổi theo quyết định pháp lý.

Khi đồng hồ đổi vì một lần chuyển đổi độ lệch theo quy tắc múi giờ, có hai tình huống người mới cần biết tên trước:

```text
gap
→ đồng hồ nhảy về phía trước
→ một khoảng giờ cục bộ không tồn tại

overlap
→ đồng hồ quay lại
→ một khoảng giờ cục bộ xuất hiện hai lần với hai độ lệch khác nhau
```

Đây là lý do `LocalDateTime + ZoneId` đôi khi không ánh xạ đơn giản 1:1 tới `Instant`. Chưa cần nhớ cách Java phân giải ở đây; các chương tiếp theo sẽ xây dần mô hình, và chương **Phép toán / So sánh** sẽ xử lý chi tiết khoảng trống/chồng lặp.

## <a id="zone-offset">ZoneOffset — một độ lệch cụ thể so với UTC</a>

`ZoneOffset` mô tả chênh lệch giữa giờ cục bộ và UTC tại một thời điểm, ví dụ:

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

Mỗi `ZoneOffset` là **một độ lệch UTC cụ thể**, không phải một bộ quy tắc theo vùng. Giá trị `+07:00` tự nó không mang theo lịch sử địa phương, luật DST hay danh tính khu vực.

Ví dụ dấu thời gian dạng văn bản:

```java
OffsetDateTime value = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");
```

Từ các trường cục bộ + độ lệch, Java xác định được `Instant`:

```java
Instant instant = value.toInstant();
```

Nhưng nếu chỉ có `+07:00`, ta không biết độ lệch đó đến từ `Asia/Ho_Chi_Minh`, một `ZoneId` dùng độ lệch cố định hay nơi nào khác có cùng độ lệch tại thời điểm đó.

## <a id="zone-id">ZoneId — định danh múi giờ</a>

`ZoneId` có thể biểu diễn một múi giờ theo độ lệch cố định hoặc một vùng có tên. Khi nghiệp vụ cần quy tắc thay đổi theo lịch sử/chính sách, ta thường dùng định danh vùng như:

```java
ZoneId vietnam = ZoneId.of("Asia/Ho_Chi_Minh");
ZoneId paris = ZoneId.of("Europe/Paris");
ZoneId newYork = ZoneId.of("America/New_York");
```

Tên vùng không chỉ là nhãn để hiển thị. Nó cho phép Java tra **bộ quy tắc múi giờ** tương ứng.

### Tránh mã múi giờ viết tắt mơ hồ

Các viết tắt ba chữ như `CST`, `EST`, `IST` nhìn ngắn nhưng **không phải định danh vùng toàn cầu đáng tin cậy**; cùng một dạng viết tắt có thể được hiểu khác nhau theo hệ thống hoặc khu vực. Với nghiệp vụ thật, ưu tiên định danh vùng kiểu IANA dạng `Area/City` như:

```text
Asia/Ho_Chi_Minh
Europe/Paris
America/New_York
```

Java có các ánh xạ tương thích như `ZoneId.SHORT_IDS`, và API cũ `TimeZone` cũng hỗ trợ nhiều mã viết tắt. Đây là cơ chế tương thích riêng, không có nghĩa lời gọi một tham số như `ZoneId.of("EST")` luôn hợp lệ. Với dữ liệu nghiệp vụ, hãy ưu tiên định danh vùng rõ ràng nếu hợp đồng có thể lưu/trao đổi `ZoneId`.

So sánh:

```text
+01:00
→ chỉ biết độ lệch hiện tại/cụ thể

Europe/Paris
→ biết vùng
→ bộ quy tắc có thể xác định độ lệch đúng cho từng ngày-giờ cục bộ/Instant
```

Vì DST hoặc thay đổi pháp lý, độ lệch của một vùng có thể khác nhau theo ngày.

**DST (Daylight Saving Time)** là chính sách mà một số khu vực điều chỉnh đồng hồ cục bộ trong một phần của năm. Không phải quốc gia/vùng nào cũng dùng DST, và quy tắc có thể thay đổi theo quyết định pháp lý; vì vậy mã không nên tự ghi cứng “mùa hè luôn +1 giờ”.

### Chọn ZoneOffset hay ZoneId?

```text
Hợp đồng bên ngoài chỉ sở hữu một độ lệch cụ thể như +07:00
→ ZoneOffset

Nghiệp vụ sở hữu một khu vực thật và cần quy tắc theo lịch sử/tương lai
→ ZoneId theo vùng

Cần một mốc sự kiện toàn cục, không cần giữ định danh cục bộ/múi giờ
→ thường chuyển về Instant
```

Đừng nâng một `ZoneOffset` thành `ZoneId` bằng suy đoán. `+07:00` có thể khớp nhiều nơi và không chứa đủ thông tin để biết vùng ban đầu.

### Múi giờ mặc định của hệ thống

```java
ZoneId systemZone = ZoneId.systemDefault();
```

API này tiện nhưng đưa môi trường chạy vào logic nghiệp vụ. Chạy cùng mã ở máy phát triển, CI và môi trường thực tế với mặc định múi giờ khác nhau có thể cho kết quả khác. Nếu nghiệp vụ biết múi giờ, nên truyền múi giờ/cấu hình rõ ràng thay vì dựa vào mặc định ngầm.

## <a id="zone-rules">ZoneRules và độ lệch thay đổi theo thời gian</a>

`ZoneId` theo vùng dẫn tới `ZoneRules`:

```java
ZoneId paris = ZoneId.of("Europe/Paris");
ZoneRules rules = paris.getRules();
```

Tập quy tắc dùng cơ sở dữ liệu múi giờ của JDK để trả lời các câu hỏi như:

```text
Instant này đang dùng độ lệch nào?
LocalDateTime này có độ lệch hợp lệ nào?
Có lần chuyển đổi DST ở đây không?
```

Ví dụ lấy độ lệch tại hai `Instant`:

```java
ZoneRules rules = ZoneId.of("Europe/Paris").getRules();

ZoneOffset winter = rules.getOffset(Instant.parse("2026-01-15T12:00:00Z"));
ZoneOffset summer = rules.getOffset(Instant.parse("2026-07-15T12:00:00Z"));
```

Hai độ lệch có thể khác nhau vì quy tắc của vùng.

### Vì sao `+07:00` không đồng nghĩa `Asia/Ho_Chi_Minh`?

Ngay cả khi hôm nay một vùng có độ lệch `+07:00`, quan hệ đúng là:

```text
ZoneId
    ↓ dùng quy tắc + Instant/ngày-giờ cục bộ
ZoneOffset
```

không phải:

```text
ZoneOffset
    ↓ suy ngược chắc chắn
ZoneId
```

Nhiều vùng có thể cùng độ lệch tại một thời điểm. Một độ lệch không chứa đủ thông tin để khôi phục vùng.

### Quy tắc múi giờ là dữ liệu có thể được cập nhật

Chính phủ có thể thay đổi luật giờ mùa hè hoặc độ lệch UTC. JDK cập nhật cơ sở dữ liệu múi giờ theo các bản phát hành/cập nhật. Vì thế một lịch tương lai gắn với vùng nên giữ `ZoneId` khi nghiệp vụ cần quy tắc của vùng, thay vì đóng băng một độ lệch được tính quá sớm.

Chương tiếp theo đặt các mảnh lại với nhau bằng `ZonedDateTime` và `OffsetDateTime`.
