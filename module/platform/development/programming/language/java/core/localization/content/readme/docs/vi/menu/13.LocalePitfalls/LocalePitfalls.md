# Các lỗi thường gặp với Locale và quốc tế hóa

Sau khi học từng API riêng, phần quan trọng nhất là biết **chỗ nào không nên dùng hành vi phụ thuộc Locale một cách vô thức**. Nhiều lỗi bản địa hóa không đến từ API sai, mà đến từ việc mã nguồn không nói rõ dữ liệu đang dành cho con người hay dành cho máy.

## <a id="default-locale">Locale mặc định và hai nhóm FORMAT/DISPLAY</a>

Java có Locale mặc định ở mức JVM:

```java
Locale current = Locale.getDefault();
```

Giá trị mặc định này tiện cho ứng dụng chạy cục bộ, nhưng trong hệ thống phía máy chủ phục vụ nhiều người dùng nó có thể trở thành **phụ thuộc ẩn**: cùng một đoạn mã nhưng chạy trên máy có cấu hình khác sẽ cho kết quả khác.

Java phân biệt hai nhóm mặc định quan trọng:

```java
Locale.Category.DISPLAY
→ dùng khi cần hiển thị tên ngôn ngữ/quốc gia/Locale

Locale.Category.FORMAT
→ dùng cho định dạng số/ngày giờ/tiền tệ...
```

Đọc riêng từng nhóm:

```java
Locale displayLocale = Locale.getDefault(Locale.Category.DISPLAY);
Locale formatLocale = Locale.getDefault(Locale.Category.FORMAT);
```

Có thể thay đổi Locale mặc định:

```java
Locale.setDefault(Locale.Category.FORMAT, Locale.US);
```

Nhưng đây là trạng thái ở mức JVM và có thể ảnh hưởng mã khác trong cùng tiến trình. Hệ thống phía máy chủ thường an toàn hơn khi truyền `Locale` **tường minh** theo ngữ cảnh yêu cầu/người dùng thay vì thay đổi giá trị mặc định toàn cục.

## <a id="turkish-i">Chuyển đổi chữ hoa/chữ thường và bài toán Turkish-I</a>

Các phương thức như:

```java
text.toLowerCase()
text.toUpperCase()
```

dùng Locale mặc định. Điều này nguy hiểm nếu String thật ra là định danh/token giao thức.

Một ví dụ kinh điển là chữ `I/i` trong tiếng Thổ Nhĩ Kỳ, nơi quan hệ chữ hoa/chữ thường khác tiếng Anh.

Vì vậy:

```text
văn bản dành cho người dùng
→ chuyển đổi hoa/thường có thể cần Locale của người dùng

định danh / token giao thức / khóa kỹ thuật
→ thường dùng Locale.ROOT hoặc quy tắc tường minh của giao thức
```

Ví dụ:

```java
String normalizedKey = input.toLowerCase(Locale.ROOT);
```

`Locale.ROOT` biểu diễn Locale trung lập về ngôn ngữ/quốc gia cho các thao tác cần hành vi không gắn với Locale của người dùng.

Không nên chuyển toàn bộ văn bản thành chữ thường như một giải pháp thay thế cho mọi phép so sánh không phân biệt hoa/thường. Từng miền nghiệp vụ có hợp đồng riêng; ánh xạ hoa/thường của Unicode có thể phức tạp hơn ASCII.

## <a id="default-locale-production-risk">Rủi ro của Locale mặc định trong môi trường vận hành</a>

Đoạn mã sau nhìn vô hại:

```java
NumberFormat format = NumberFormat.getNumberInstance();
String lower = input.toLowerCase();
DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);
```

Nhưng các API này **không cùng đọc một giá trị mặc định**. `String.toLowerCase()` dùng Locale mặc định chung của JVM, còn các API định dạng như `NumberFormat` và `DateTimeFormatter` dạng bản địa hóa dùng Locale mặc định của nhóm `FORMAT`. Điểm nguy hiểm chung là hành vi đang phụ thuộc trạng thái toàn cục của JVM thay vì Locale ứng dụng/người dùng truyền tường minh.

Hệ quả:

```text
máy lập trình viên → kiểm thử đạt
máy CI             → kết quả khác
nút vận hành A     → Locale X
nút vận hành B     → Locale Y
```

Hệ thống phía máy chủ phục vụ nhiều người dùng nên truyền Locale từ yêu cầu hoặc lựa chọn ngôn ngữ của người dùng một cách rõ ràng:

```java
String render(Order order, Locale userLocale) { ... }
```

Locale mặc định chỉ nên là một **chính sách có chủ đích**, không phải phụ thuộc vô hình.

Kiểm thử bản địa hóa cũng nên chạy với nhiều Locale mặc định để phát hiện mã nguồn lén phụ thuộc môi trường:

```java
Locale previousGeneral = Locale.getDefault();
Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
try {
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    // chạy kiểm thử tập trung
} finally {
    Locale.setDefault(previousGeneral);
    Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
    Locale.setDefault(Locale.Category.FORMAT, previousFormat);
}
```

`Locale.setDefault(Locale)` thay đổi Locale mặc định chung và cả hai nhóm mặc định `DISPLAY`/`FORMAT`, vì vậy kiểm thử dùng cách này phải khôi phục đủ cả ba giá trị nếu trước đó chúng có thể khác nhau. Đây vẫn là trạng thái toàn cục nên các kiểm thử chạy song song có thể ảnh hưởng lẫn nhau; hãy cô lập kiểm thử hoặc ưu tiên mã nhận Locale tường minh.

## <a id="format-parse-roundtrip">Văn bản đã định dạng không phải dạng tuần tự hóa ổn định cho máy</a>

Một cách làm sai thường gặp:

```text
giá trị số/ngày giờ nghiệp vụ
→ định dạng cho người dùng
→ lưu/gửi như giá trị chuẩn
→ phân tích lại ở dịch vụ khác
```

Định dạng theo Locale được tối ưu cho **con người đọc**, không phải cho vòng chuyển đổi định dạng → phân tích giữa các hệ thống.

Ví dụ `1,234` có thể được hiểu khác tùy Locale. Ngày `03/04/2026` có thể gây nhầm ngày/tháng. Bộ định dạng còn có thể làm tròn hoặc bỏ thông tin mà UI không cần.

Thay vào đó:

```text
ranh giới dành cho máy
→ hợp đồng giá trị số/String trong JSON rõ ràng
→ ngày giờ ISO khi phù hợp
→ mã tiền tệ tường minh

ranh giới dành cho con người
→ NumberFormat / DateTimeFormatter / thông điệp theo Locale
```

Ngay cả khi định dạng rồi phân tích bằng cùng bộ định dạng, quá trình chuyển đổi hai chiều cũng không phải lúc nào bảo toàn chính xác đối tượng ban đầu vì phần hiển thị có thể cố ý giảm độ chính xác hoặc bỏ bớt trường.

### Danh sách kiểm tra khi rà soát mã bản địa hóa

```text
[ ] giá trị nghiệp vụ có đang bị lưu thành String đã định dạng không?
[ ] Locale có được truyền tường minh ở ranh giới dành cho người dùng không?
[ ] Locale có bị nhầm với ZoneId/Currency/quốc gia không?
[ ] định danh có gọi toLowerCase()/toUpperCase() bằng Locale mặc định không?
[ ] tuần tự hóa dành cho máy có vô tình dùng NumberFormat/DateFormat theo Locale không?
[ ] thông điệp có bị nối từ nhiều mảnh không?
[ ] mẫu MessageFormat có được kiểm thử dấu nháy đơn/tham số giữ chỗ không?
[ ] khóa bị thiếu/cơ chế dự phòng của ResourceBundle có được kiểm soát không?
[ ] việc sắp xếp dành cho người dùng có cần Collator không?
[ ] Quy tắc collation của DB và Java Collator có đang bị giả định là giống nhau không?
```

## <a id="localization-synthesis">Tổng hợp: chọn đúng ranh giới bản địa hóa</a>

Sau khi học các API riêng lẻ, cách ra quyết định nên quay về một luồng thống nhất thay vì bắt đầu từ tên lớp:

```text
1. Xác định giá trị gốc có ý nghĩa nghiệp vụ gì
   → số, tiền tệ, thời điểm, trạng thái, định danh...
        ↓
2. Xác định dữ liệu đang đi tới con người hay tới máy
   → ranh giới trình bày hay ranh giới cần biểu diễn ổn định?
        ↓
3. Nếu dành cho con người, xác định Locale tường minh
   → lựa chọn người dùng / thông tin từ yêu cầu / chính sách sản phẩm
        ↓
4. Chọn đúng cơ chế
   → ResourceBundle / MessageFormat
   → NumberFormat / Currency
   → DateTimeFormatter + ZoneId
   → Collator / BreakIterator / Bidi khi bài toán văn bản cần chúng
        ↓
5. Kiểm soát cơ chế dự phòng và Locale mặc định
   → không để môi trường JVM âm thầm quyết định hành vi
        ↓
6. Giữ biểu diễn dành cho máy ổn định
   → không lưu chuỗi đã bản địa hóa làm giá trị nghiệp vụ, định danh hoặc định dạng giao thức
```

Nếu một bước trong chuỗi này không rõ, vấn đề thường nằm ở **ranh giới trách nhiệm** chứ không phải ở việc thiếu thêm một bộ định dạng.

Nếu chỉ nhớ một nguyên tắc sau mô-đun này, hãy nhớ:

```text
Ý nghĩa nghiệp vụ phải ổn định.
Locale chỉ nên thay đổi cách con người nhìn thấy ý nghĩa đó.
```
