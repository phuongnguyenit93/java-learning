# Các lỗi thường gặp với Locale và i18n

Sau khi học từng API riêng, phần quan trọng nhất là biết **chỗ nào không nên dùng hành vi phụ thuộc locale một cách vô thức**. Nhiều lỗi localization không đến từ API sai, mà đến từ việc mã nguồn không nói rõ dữ liệu đang dành cho con người hay dành cho máy.

## <a id="turkish-i">Case conversion và bài toán Turkish-I</a>

Các method như:

```java
text.toLowerCase()
text.toUpperCase()
```

dùng default locale. Điều này nguy hiểm nếu string thật ra là identifier/protocol token.

Một ví dụ kinh điển là chữ `I/i` trong Turkish, nơi quan hệ uppercase/lowercase khác English.

Vì vậy:

```text
text dành cho người dùng
→ case conversion có thể cần user Locale

identifier / protocol / technical key
→ thường dùng Locale.ROOT hoặc rule explicit của protocol
```

Ví dụ:

```java
String normalizedKey = input.toLowerCase(Locale.ROOT);
```

`Locale.ROOT` biểu diễn locale trung lập về language/country cho các thao tác cần hành vi không gắn với locale của người dùng.

Không nên chuyển toàn bộ text thành chữ thường như một giải pháp thay thế cho mọi phép so sánh không phân biệt hoa/thường. Từng miền nghiệp vụ có hợp đồng riêng; ánh xạ hoa/thường của Unicode có thể phức tạp hơn ASCII.

## <a id="default-locale-production-risk">Rủi ro của default Locale trong production</a>

Code sau nhìn vô hại:

```java
NumberFormat format = NumberFormat.getNumberInstance();
String lower = input.toLowerCase();
DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);
```

Nhưng nếu không truyền locale, hành vi có thể phụ thuộc môi trường JVM hoặc category mặc định.

Hệ quả:

```text
developer machine → test pass
CI machine        → output khác
production node A → locale X
production node B → locale Y
```

Backend phục vụ nhiều người dùng nên truyền locale từ request hoặc lựa chọn ngôn ngữ của người dùng một cách rõ ràng:

```java
String render(Order order, Locale userLocale) { ... }
```

Locale mặc định chỉ nên là một **chính sách có chủ đích**, không phải phụ thuộc vô hình.

Test localization cũng nên chạy với nhiều default locale để phát hiện code lén phụ thuộc environment:

```java
Locale previous = Locale.getDefault();
try {
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    // run focused test
} finally {
    Locale.setDefault(previous);
}
```

Nếu test chạy song song, mutate global default có thể gây interference; hãy cô lập test hoặc ưu tiên code nhận locale explicit.

## <a id="format-parse-roundtrip">Text đã format không phải serialization ổn định cho máy</a>

Một anti-pattern thường gặp:

```text
domain number/date
→ format cho user
→ lưu/gửi như canonical value
→ parse lại ở service khác
```

Formatting theo locale được tối ưu cho **con người đọc**, không phải cho vòng chuyển đổi format → parse giữa các hệ thống.

Ví dụ `1,234` có thể được hiểu khác tùy locale. Date `03/04/2026` có thể gây nhầm ngày/tháng. Formatter còn có thể round hoặc bỏ thông tin mà UI không cần.

Thay vào đó:

```text
machine boundary
→ numeric JSON number/string contract rõ ràng
→ ISO date/time khi phù hợp
→ currency code explicit

human boundary
→ localized NumberFormat / DateTimeFormatter / message
```

Ngay cả khi format rồi parse bằng cùng formatter, quá trình chuyển đổi hai chiều cũng không phải lúc nào bảo toàn chính xác object ban đầu vì phần hiển thị có thể cố ý giảm độ chính xác hoặc bỏ bớt field.

## <a id="translation-key-design">Thiết kế translation key và message</a>

Key nên **ổn định theo ý nghĩa**, không dựa trực tiếp vào câu English hiện tại.

Tốt:

```properties
order.created=Order {0} was created.
order.cancelled=Order {0} was cancelled.
```

Kém ổn định:

```properties
Order_was_created=Order was created
```

Nếu câu English thay wording, semantic key `order.created` vẫn giữ identity.

### Không chia một câu thành các fragment khó dịch

Tránh:

```text
"Order " + id + " was " + statusText
```

vì người dịch không kiểm soát được toàn bộ cấu trúc câu.

Ưu tiên:

```properties
order.status=Order {0} is {1}.
```

và tốt hơn nữa, nếu grammar từng status khác nhau đáng kể, dùng key/message hoàn chỉnh cho từng ý nghĩa thay vì ép mọi ngôn ngữ vào một template English.

### Checklist khi review code localization

```text
[ ] domain value có đang bị lưu thành formatted String không?
[ ] Locale có được truyền explicit ở user-facing boundary không?
[ ] Locale có bị nhầm với ZoneId/Currency/country không?
[ ] identifier có gọi toLowerCase()/toUpperCase() bằng default locale không?
[ ] machine serialization có vô tình dùng NumberFormat/DateFormat localized không?
[ ] message có bị nối từ nhiều fragment không?
[ ] MessageFormat pattern có được test apostrophe/placeholder không?
[ ] ResourceBundle missing key/fallback có được kiểm soát không?
[ ] user-visible sorting có cần Collator không?
[ ] DB collation và Java Collator có đang bị giả định là giống nhau không?
```

Nếu chỉ nhớ một nguyên tắc sau module này, hãy nhớ:

```text
Ý nghĩa nghiệp vụ phải ổn định.
Locale chỉ nên thay đổi cách con người nhìn thấy ý nghĩa đó.
```
