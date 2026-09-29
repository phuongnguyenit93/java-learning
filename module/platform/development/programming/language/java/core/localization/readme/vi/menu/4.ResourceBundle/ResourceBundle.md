# ResourceBundle

Sau khi ứng dụng biết `Locale`, câu hỏi tiếp theo là: **lấy đúng message cho locale đó bằng cách nào mà không viết một cây `if/else` cho từng ngôn ngữ?**

Java cung cấp `ResourceBundle` để tách resource có thể localized ra khỏi source code và chọn resource theo locale.

## <a id="resourcebundle-model">ResourceBundle giải quyết bài toán gì?</a>

Nói đơn giản, `ResourceBundle` là **cơ chế tra cứu resource theo key + Locale**.

Nó không dịch một câu tiếng Anh thành tiếng Việt bằng AI hay thuật toán. Developer/team dịch **chuẩn bị sẵn các resource**, còn `ResourceBundle` làm nhiệm vụ chọn đúng resource tại runtime.

Một hệ bundle thường có năm mảnh:

```text
base name
→ tên của cả họ resource, ví dụ Messages

Locale
→ locale đang cần phục vụ, ví dụ vi-VN

bundle family
→ Messages.properties, Messages_vi.properties, Messages_vi_VN.properties...

resource key
→ định danh ổn định, ví dụ order.created

resource value
→ nội dung thực tế, ví dụ "Đơn hàng đã được tạo"
```

Vai trò của `ResourceBundle` là nối năm mảnh đó lại và áp dụng candidate/fallback rules để tìm value phù hợp.

Giả sử UI cần message `order.created`.

Nếu hard-code:

```java
String message;
if (locale.getLanguage().equals("vi")) {
    message = "Đơn hàng đã được tạo";
} else {
    message = "Order created";
}
```

thì source code phải biết mọi ngôn ngữ và mọi câu dịch. `ResourceBundle` chuyển mô hình đó thành:

```text
code chỉ biết stable key
        ↓
order.created
        ↓
ResourceBundle + Locale
        ↓
resource phù hợp
        ↓
localized text
```

Ví dụ các file:

```text
Messages.properties
Messages_en.properties
Messages_en_US.properties
Messages_vi.properties
Messages_vi_VN.properties
```

Nội dung:

```properties
# Messages_en.properties
order.created=Order created

# Messages_vi.properties
order.created=Đơn hàng đã được tạo
```

Code:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
ResourceBundle bundle = ResourceBundle.getBundle("Messages", locale);

String message = bundle.getString("order.created");
```

Ở đây `Messages` là **base name**, `order.created` là **resource key**, còn locale quyết định bundle cụ thể nào được ưu tiên.

`ResourceBundle` không chỉ dùng cho câu chữ; nó có thể cung cấp các resource/value khác. Tuy nhiên trong ứng dụng hiện đại, trường hợp sử dụng phổ biến nhất là localization cho message.

## <a id="bundle-naming">Tên bundle và candidate locales</a>

Resource bundle dùng convention tên dựa trên base name và các thành phần locale.

Với:

```java
Locale locale = Locale.forLanguageTag("en-US");
ResourceBundle.getBundle("Messages", locale);
```

mental model đơn giản của candidate chain là:

```text
Messages_en_US
        ↓ nếu không có / key không có ở level hiện tại
Messages_en
        ↓
Messages (base bundle)
```

Với locale có variant/script, candidate list có thể chi tiết hơn. Không nên tự mô phỏng thuật toán bằng nối string; hãy để `ResourceBundle` và `ResourceBundle.Control` xử lý.

Ta có thể quan sát candidate list:

```java
ResourceBundle.Control control = ResourceBundle.Control.getControl(
        ResourceBundle.Control.FORMAT_DEFAULT
);

List<Locale> candidates = control.getCandidateLocales(
        "Messages",
        Locale.forLanguageTag("en-US")
);
```

Điểm quan trọng: **fallback là một phần của hợp đồng tra cứu**, không phải quy tắc `equals` của `Locale`.

## <a id="properties-vs-class-bundle">Properties bundle và class-based bundle</a>

Java hỗ trợ hai kiểu bundle chính.

### `.properties`

```text
Messages_en.properties
Messages_vi.properties
```

Ưu điểm:

- tách text khỏi Java source;
- dễ cho người dịch và công cụ dịch xử lý;
- phù hợp với key/value text;
- không cần compile Java class khi sửa bản dịch nếu deployment flow cho phép thay resource.

### class-based bundle

Có thể tạo class kế thừa `ListResourceBundle`:

```java
public class Messages_en extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
        return new Object[][] {
                {"order.created", "Order created"}
        };
    }
}
```

Class bundle có thể trả object chứ không chỉ string, nhưng đổi lại resource bị gắn với Java code/compile lifecycle.

Với message translation thông thường, `.properties` thường đơn giản và dễ vận hành hơn.

Trong Java hiện đại, `PropertyResourceBundle` đọc properties bundle bằng UTF-8 theo hành vi chuẩn hiện hành, đồng thời có cơ chế tương thích với encoding cũ. Dù vậy, repository và quy trình build vẫn nên thống nhất UTF-8 rõ ràng để tránh khác biệt giữa các công cụ.

## <a id="bundle-cache">Caching của ResourceBundle</a>

`ResourceBundle.getBundle(...)` có cơ chế cache để tránh đọc và tạo bundle lặp lại cho cùng ngữ cảnh tra cứu.

Điều đó có hai ý nghĩa:

```text
performance
→ không phải load resource từ đầu cho mỗi request

runtime update
→ sửa file resource trên disk không có nghĩa JVM đang chạy sẽ thấy ngay lập tức
```

API hỗ trợ xóa cache khi thật sự cần:

```java
ResourceBundle.clearCache();
```

hoặc theo class loader:

```java
ResourceBundle.clearCache(classLoader);
```

`ResourceBundle.Control` còn cho phép tùy biến TTL, reload và cách chọn candidate, nhưng đó là cơ chế nâng cao. Trước khi tùy biến, hãy xác định ứng dụng thật sự cần nạp lại resource khi đang chạy hay chỉ cần bundle cố định theo mỗi lần triển khai.

### Missing key khác missing bundle

Nếu không tìm được bundle phù hợp, `getBundle` có thể ném `MissingResourceException`. Nếu bundle tồn tại nhưng `getString(key)` không tìm thấy key trong chain cha/fallback, việc lấy key cũng có thể ném `MissingResourceException`.

Do đó production code nên có chiến lược kiểm soát key completeness bằng test/build validation, thay vì để người dùng là người đầu tiên phát hiện missing translation.

Chương kế tiếp giải quyết bước sau lookup: message thường chứa dữ liệu động như tên người dùng, số lượng hoặc tổng tiền. **Ghép chuỗi thủ công không an toàn cho ngữ pháp của mọi ngôn ngữ**, nên Java có `MessageFormat`.
