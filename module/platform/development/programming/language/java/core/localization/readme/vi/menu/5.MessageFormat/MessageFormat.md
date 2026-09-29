# MessageFormat

ResourceBundle giúp chọn **đúng mẫu câu**, nhưng nhiều message cần dữ liệu động:

```text
Đơn hàng 1001 có tổng tiền 1.234.567,89
Order 1001 totals 1,234,567.89
```

Cách đơn giản nhất là nối chuỗi, nhưng cấu trúc câu của mỗi ngôn ngữ có thể khác nhau. `MessageFormat` cho phép người dịch kiểm soát vị trí tham số trong cả câu.

## <a id="messageformat-model">Placeholder của MessageFormat</a>

`MessageFormat` là **formatter dành cho một message template có tham số**. Nó nhận một pattern, các argument và một `Locale`, rồi tạo ra câu cuối cùng.

Các thành phần chính:

```text
pattern
→ "Order {0} was created for {1}."

placeholder / argument index
→ {0}, {1}, ...

argument value
→ 1001, "An", ...

optional format type/style
→ number, date, choice...

Locale
→ ảnh hưởng các sub-format phụ thuộc locale
```

Vai trò của nó **không phải dịch câu**. `ResourceBundle` thường cung cấp câu/pattern đã dịch; `MessageFormat` chỉ đưa dữ liệu động vào pattern đó theo đúng vị trí và format.

Pattern cơ bản dùng index:

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

Điểm quan trọng là **thứ tự trong câu thuộc pattern**, không thuộc code nối string.

Bundle tiếng Anh có thể là:

```properties
order.created=Order {0} was created for {1}.
```

Bundle tiếng Việt:

```properties
order.created=Đơn hàng {0} của {1} đã được tạo.
```

Code dùng cùng key + arguments:

```java
String pattern = bundle.getString("order.created");
MessageFormat formatter = new MessageFormat(pattern, locale);
String message = formatter.format(new Object[] {order.id(), customerName});
```

### Vì sao không nối chuỗi?

```java
"Order " + id + " of " + customer + " created"
```

Nối chuỗi khóa người dịch vào thứ tự do lập trình viên quyết định. Với ngôn ngữ khác, vị trí chủ ngữ/tân ngữ hoặc cách biểu đạt có thể cần đổi. Placeholder giúp toàn bộ câu nằm trong resource.

## <a id="messageformat-types">Number, date và choice trong pattern</a>

`MessageFormat` có thể áp dụng sub-format cho argument:

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

Các type lịch sử của `MessageFormat` gắn với formatter trong `java.text` và các kiểu `Date`/number. Với miền nghiệp vụ dùng `java.time`, ứng dụng thường kiểm soát việc định dạng date-time riêng bằng `DateTimeFormatter` rồi truyền text đã được trình bày theo locale vào message, hoặc thiết kế adapter rõ ràng.

`choice` format tồn tại để chọn text theo numeric range, nhưng pluralization thực tế của nhiều ngôn ngữ phức tạp hơn rất nhiều. Không nên giả định một rule kiểu `1 item / many items` đủ cho mọi language.

Điểm học chính ở module Java Core là: **message parameterization phải tôn trọng locale và cho phép bản dịch sở hữu cấu trúc câu**.

## <a id="quote-escaping">Apostrophe và escaping</a>

`MessageFormat` dùng dấu `'` làm ký tự quote trong pattern. Đây là nguồn bug rất phổ biến.

Ví dụ để tạo apostrophe literal, thường phải dùng hai apostrophe:

```java
MessageFormat format = new MessageFormat(
        "User ''{0}'' has {1,number} orders",
        Locale.US
);
```

Vì người dịch có thể thêm apostrophe tự nhiên trong một ngôn ngữ, pattern cần được kiểm thử như mã nguồn. Không nên cho rằng text dịch “chỉ là string nên không thể làm hỏng formatter”.

Quy tắc an toàn:

```text
resource value dùng MessageFormat
→ được xem là pattern
→ placeholder và apostrophe có syntax riêng
→ phải validate/test với argument thực tế
```

Nếu message không cần parameter, không bắt buộc phải đưa qua `MessageFormat`.

## <a id="messageformat-locale">Locale của MessageFormat</a>

`MessageFormat` có locale riêng vì các sub-format như number/date phụ thuộc locale.

```java
MessageFormat vi = new MessageFormat("Tổng: {0,number}", Locale.forLanguageTag("vi-VN"));
MessageFormat us = new MessageFormat("Total: {0,number}", Locale.US);

System.out.println(vi.format(new Object[] {1234567.89}));
System.out.println(us.format(new Object[] {1234567.89}));
```

Cùng numeric value nhưng separators có thể khác.

Không nên load pattern theo `vi-VN` rồi vô tình format argument bằng JVM default locale khác. Một pipeline nhất quán thường là:

```text
request/user locale
        ↓
ResourceBundle(locale)
        ↓
pattern
        ↓
MessageFormat(pattern, same locale)
        ↓
localized message
```

`MessageFormat` là formatter có thể thay đổi trạng thái và các `Format` của `java.text` nói chung không nên được chia sẻ vô điều kiện giữa nhiều thread. Cách đơn giản trong backend xử lý theo request là tạo formatter cho từng thao tác/request, hoặc quản lý đồng bộ hóa/cache một cách có chủ đích.

Chương tiếp theo tách khỏi whole-message formatting để đi sâu vào một thành phần xuất hiện rất thường xuyên: **số**.
