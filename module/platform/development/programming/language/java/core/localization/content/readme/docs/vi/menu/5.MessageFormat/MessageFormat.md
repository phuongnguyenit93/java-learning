# Thông điệp bản địa hóa có tham số

`ResourceBundle` giúp chọn **đúng mẫu câu**, nhưng nhiều thông điệp cần dữ liệu động:

```text
Đơn hàng 1001 có tổng tiền 1.234.567,89
Order 1001 totals 1,234,567.89
```

Cách đơn giản nhất là nối chuỗi, nhưng cấu trúc câu của mỗi ngôn ngữ có thể khác nhau. `MessageFormat` cho phép người dịch kiểm soát vị trí tham số trong cả câu.

## <a id="messageformat-model">Tham số giữ chỗ của MessageFormat</a>

`MessageFormat` là **bộ định dạng dành cho một mẫu thông điệp có tham số**. Nó nhận một mẫu, các đối số và một `Locale`, rồi tạo ra câu cuối cùng.

Các thành phần chính:

```text
mẫu (pattern)
→ "Order {0} was created for {1}."

tham số giữ chỗ / chỉ số đối số
→ {0}, {1}, ...

giá trị đối số
→ 1001, "An", ...

kiểu/cách định dạng tùy chọn
→ number, date, choice...

Locale
→ ảnh hưởng các bộ định dạng con phụ thuộc Locale
```

Vai trò của nó **không phải dịch câu**. `ResourceBundle` thường cung cấp câu/mẫu đã dịch; `MessageFormat` chỉ đưa dữ liệu động vào mẫu đó theo đúng vị trí và cách định dạng.

Mẫu cơ bản dùng chỉ số:

```text
Order {0} was created for {1}.
```

Java:

```java
MessageFormat format = new MessageFormat(
        "Order {0} was created for {1}.",
        Locale.US
);

String text = format.format(new Object[] {1001L, "An"});
```

Điểm quan trọng là **thứ tự trong câu thuộc mẫu**, không thuộc mã nguồn nối chuỗi.

Gói tài nguyên tiếng Anh có thể là:

```properties
order.created=Order {0} was created for {1}.
```

Gói tài nguyên tiếng Việt:

```properties
order.created=Đơn hàng {0} của {1} đã được tạo.
```

Mã nguồn dùng cùng khóa + các đối số:

```java
String pattern = bundle.getString("order.created");
MessageFormat formatter = new MessageFormat(pattern, locale);
String message = formatter.format(new Object[] {order.id(), customerName});
```

### Vì sao không nối chuỗi?

```java
"Order " + id + " of " + customer + " created"
```

Nối chuỗi khóa người dịch vào thứ tự do lập trình viên quyết định. Với ngôn ngữ khác, vị trí chủ ngữ/tân ngữ hoặc cách biểu đạt có thể cần đổi. Tham số giữ chỗ giúp toàn bộ câu nằm trong tài nguyên dịch.

## <a id="translation-key-design">Thiết kế khóa bản dịch và thông điệp</a>

Khóa bản dịch nên **ổn định theo ý nghĩa**, không dựa trực tiếp vào câu tiếng Anh hiện tại.

Tốt:

```properties
order.created=Order {0} was created.
order.cancelled=Order {0} was cancelled.
```

Kém ổn định:

```properties
Order_was_created=Order was created
```

Nếu câu tiếng Anh thay cách diễn đạt, khóa ngữ nghĩa `order.created` vẫn giữ nguyên định danh.

### Không chia một câu thành các mảnh khó dịch

Tránh:

```text
"Order " + id + " was " + statusText
```

vì người dịch không kiểm soát được toàn bộ cấu trúc câu.

Ưu tiên:

```properties
order.status=Order {0} is {1}.
```

Nếu ngữ pháp của từng trạng thái khác nhau đáng kể giữa các ngôn ngữ, dùng khóa/thông điệp hoàn chỉnh cho từng ý nghĩa thường tốt hơn việc ép mọi ngôn ngữ vào một mẫu câu tiếng Anh.

## <a id="messageformat-types">Các kiểu `number`, `date` và `choice` trong mẫu</a>

`MessageFormat` có thể áp dụng bộ định dạng con cho đối số:

```text
Total: {0,number}
Created: {1,date,medium}
```

```java
MessageFormat format = new MessageFormat(
        "Total: {0,number} - Created: {1,date,medium}",
        Locale.US
);
```

Các kiểu lịch sử của `MessageFormat` gắn với bộ định dạng trong `java.text` và các kiểu `Date`/số. Với miền nghiệp vụ dùng `java.time`, ứng dụng thường kiểm soát việc định dạng ngày giờ riêng bằng `DateTimeFormatter` rồi truyền văn bản đã được trình bày theo Locale vào thông điệp, hoặc thiết kế lớp chuyển đổi rõ ràng.

Kiểu `choice` tồn tại để chọn văn bản theo khoảng số, nhưng quy tắc số ít/số nhiều thực tế của nhiều ngôn ngữ phức tạp hơn rất nhiều. Không nên giả định một quy tắc kiểu `1 item / many items` đủ cho mọi ngôn ngữ.

Điểm học chính ở mô-đun Java Core là: **thông điệp có tham số phải tôn trọng Locale và cho phép bản dịch sở hữu cấu trúc câu**.

## <a id="quote-escaping">Dấu nháy đơn và cách thoát ký tự</a>

`MessageFormat` dùng dấu `'` làm ký tự trích dẫn trong mẫu. Đây là nguồn lỗi rất phổ biến.

Ví dụ để tạo dấu nháy đơn hiển thị trực tiếp, thường phải dùng hai dấu nháy đơn:

```java
MessageFormat format = new MessageFormat(
        "User ''{0}'' has {1,number} orders",
        Locale.US
);
```

Vì người dịch có thể thêm dấu nháy đơn tự nhiên trong một ngôn ngữ, mẫu cần được kiểm thử như mã nguồn. Không nên cho rằng văn bản dịch “chỉ là String nên không thể làm hỏng bộ định dạng”.

Quy tắc an toàn:

```text
giá trị tài nguyên dùng MessageFormat
→ được xem là mẫu
→ tham số giữ chỗ và dấu nháy đơn có cú pháp riêng
→ phải kiểm thử với đối số thực tế
```

Nếu thông điệp không cần tham số, không bắt buộc phải đưa qua `MessageFormat`.

## <a id="messageformat-locale">Locale của MessageFormat</a>

`MessageFormat` có Locale riêng vì các bộ định dạng con như `number`/`date` phụ thuộc Locale.

```java
MessageFormat vi = new MessageFormat("Tổng: {0,number}", Locale.forLanguageTag("vi-VN"));
MessageFormat us = new MessageFormat("Total: {0,number}", Locale.US);

System.out.println(vi.format(new Object[] {1234567.89}));
System.out.println(us.format(new Object[] {1234567.89}));
```

Cùng giá trị số nhưng các dấu phân cách có thể khác.

Không nên tải mẫu theo `vi-VN` rồi vô tình định dạng đối số bằng Locale mặc định khác của JVM. Một luồng xử lý nhất quán thường là:

```text
Locale từ yêu cầu/người dùng
        ↓
ResourceBundle(locale)
        ↓
mẫu
        ↓
MessageFormat(mẫu, cùng Locale)
        ↓
thông điệp đã bản địa hóa
```

`MessageFormat` là bộ định dạng có thể thay đổi trạng thái và các `Format` của `java.text` nói chung không nên được chia sẻ vô điều kiện giữa nhiều luồng. Cách đơn giản ở phía máy chủ là tạo bộ định dạng cho từng thao tác/yêu cầu, hoặc quản lý đồng bộ hóa/bộ nhớ đệm một cách có chủ đích.

Chương tiếp theo rời khỏi việc định dạng toàn bộ thông điệp để đi sâu vào một thành phần xuất hiện rất thường xuyên: **số**.
