# ResourceBundle

Sau khi ứng dụng biết `Locale`, câu hỏi tiếp theo là: **lấy đúng thông điệp cho Locale đó bằng cách nào mà không viết một cây `if/else` cho từng ngôn ngữ?**

Java cung cấp `ResourceBundle` để tách tài nguyên cần bản địa hóa khỏi mã nguồn và chọn tài nguyên theo Locale.

## <a id="resourcebundle-model">ResourceBundle giải quyết bài toán gì?</a>

Nói đơn giản, `ResourceBundle` là **cơ chế tra cứu tài nguyên theo khóa + Locale**.

Nó không dịch một câu tiếng Anh thành tiếng Việt bằng AI hay thuật toán. Lập trình viên/nhóm dịch **chuẩn bị sẵn các tài nguyên**, còn `ResourceBundle` làm nhiệm vụ chọn đúng tài nguyên tại thời gian chạy.

Một hệ gói tài nguyên thường có năm mảnh:

```text
tên cơ sở (base name)
→ tên của cả họ tài nguyên, ví dụ Messages

Locale
→ Locale đang cần phục vụ, ví dụ vi-VN

họ gói tài nguyên
→ Messages.properties, Messages_vi.properties, Messages_vi_VN.properties...

khóa tài nguyên
→ định danh ổn định, ví dụ order.created

giá trị tài nguyên
→ nội dung thực tế, ví dụ "Đơn hàng đã được tạo"
```

Vai trò của `ResourceBundle` là nối năm mảnh đó lại và áp dụng quy tắc ứng viên/dự phòng để tìm giá trị phù hợp.

Giả sử UI cần thông điệp `order.created`.

Nếu ghi cứng trong mã nguồn:

```java
String message;
if (locale.getLanguage().equals("vi")) {
    message = "Đơn hàng đã được tạo";
} else {
    message = "Order created";
}
```

thì mã nguồn phải biết mọi ngôn ngữ và mọi câu dịch. `ResourceBundle` chuyển mô hình đó thành:

```text
mã nguồn chỉ biết khóa ổn định
        ↓
order.created
        ↓
ResourceBundle + Locale
        ↓
tài nguyên phù hợp
        ↓
văn bản đã bản địa hóa
```

Ví dụ các tệp:

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

Mã nguồn:

```java
Locale locale = Locale.forLanguageTag("vi-VN");
ResourceBundle bundle = ResourceBundle.getBundle("Messages", locale);

String message = bundle.getString("order.created");
```

Ở đây `Messages` là **tên cơ sở (base name)**, `order.created` là **khóa tài nguyên**, còn Locale quyết định gói cụ thể nào được ưu tiên.

`ResourceBundle` không chỉ dùng cho câu chữ; nó có thể cung cấp các tài nguyên/giá trị khác. Tuy nhiên trong ứng dụng hiện đại, trường hợp sử dụng phổ biến nhất là bản địa hóa thông điệp.

## <a id="bundle-naming">Tên gói tài nguyên và chuỗi Locale ứng viên</a>

Gói tài nguyên dùng quy ước tên dựa trên tên cơ sở và các thành phần Locale.

Với:

```java
Locale locale = Locale.forLanguageTag("en-US");
ResourceBundle.getBundle("Messages", locale);
```

Mô hình đơn giản của **việc phân giải gói tài nguyên** là:

```text
Locale yêu cầu en-US
        ↓
tạo chuỗi Locale ứng viên
        ↓
en-US → en → Locale.ROOT
        ↓
tìm gói tài nguyên phù hợp theo từng ứng viên
```

Với Locale có variant/script, danh sách ứng viên có thể chi tiết hơn. Không nên tự mô phỏng thuật toán bằng nối chuỗi; hãy để `ResourceBundle` và `ResourceBundle.Control` xử lý.

Ta có thể quan sát danh sách ứng viên:

```java
ResourceBundle.Control control = ResourceBundle.Control.getControl(
        ResourceBundle.Control.FORMAT_DEFAULT
);

List<Locale> candidates = control.getCandidateLocales(
        "Messages",
        Locale.forLanguageTag("en-US")
);
```

Sau khi gói tài nguyên được phân giải, **tra cứu khóa là một bước khác**: nếu khóa không có trong gói cụ thể, `ResourceBundle` có thể tiếp tục tìm trong chuỗi gói cha đã được thiết lập. Đừng trộn “gói nào được chọn” với “khóa được tìm ở gói/gói cha nào”.

Điểm quan trọng: **cơ chế dự phòng là một phần của hợp đồng tra cứu**, không phải quy tắc `equals` của `Locale`. Dự phòng qua Locale mặc định và chính sách gói tài nguyên gốc sẽ được học sâu ở cột mốc cuối.

## <a id="properties-vs-class-bundle">Gói tài nguyên `.properties` và gói tài nguyên dạng lớp</a>

Java hỗ trợ hai kiểu gói tài nguyên chính.

### `.properties`

```text
Messages_en.properties
Messages_vi.properties
```

Ưu điểm:

- tách văn bản khỏi mã nguồn Java;
- dễ cho người dịch và công cụ dịch xử lý;
- phù hợp với dữ liệu khóa/giá trị dạng văn bản;
- không cần biên dịch lại lớp Java khi sửa bản dịch nếu quy trình triển khai cho phép thay tài nguyên.

### Gói tài nguyên dạng lớp

Có thể tạo lớp kế thừa `ListResourceBundle`:

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

Gói tài nguyên dạng lớp có thể trả đối tượng chứ không chỉ String, nhưng đổi lại tài nguyên bị gắn với mã Java/vòng đời biên dịch.

Với bản dịch thông điệp thông thường, `.properties` thường đơn giản và dễ vận hành hơn.

Trong Java hiện đại, `PropertyResourceBundle` đọc gói `.properties` bằng UTF-8 theo hành vi chuẩn hiện hành, đồng thời có cơ chế tương thích với mã hóa cũ. Dù vậy, kho mã và quy trình xây dựng vẫn nên thống nhất UTF-8 rõ ràng để tránh khác biệt giữa các công cụ.

## <a id="bundle-cache">Bộ nhớ đệm của ResourceBundle</a>

`ResourceBundle.getBundle(...)` có cơ chế bộ nhớ đệm để tránh đọc và tạo gói tài nguyên lặp lại cho cùng ngữ cảnh tra cứu.

Điều đó có hai ý nghĩa:

```text
hiệu năng
→ không phải tải tài nguyên từ đầu cho mỗi yêu cầu

cập nhật khi chương trình đang chạy
→ sửa tệp tài nguyên trên đĩa không có nghĩa JVM đang chạy sẽ thấy ngay lập tức
```

API hỗ trợ xóa bộ nhớ đệm khi thật sự cần:

```java
ResourceBundle.clearCache();
```

hoặc theo bộ nạp lớp (`ClassLoader`):

```java
ResourceBundle.clearCache(classLoader);
```

`ResourceBundle.Control` còn cho phép tùy biến TTL, tải lại và cách chọn ứng viên, nhưng đó là cơ chế nâng cao. Trước khi tùy biến, hãy xác định ứng dụng thật sự cần nạp lại tài nguyên khi đang chạy hay chỉ cần gói cố định theo mỗi lần triển khai.

Phần phân biệt **thiếu gói tài nguyên** với **thiếu khóa**, cùng chính sách xử lý `MissingResourceException`, được gom về cột mốc cuối trong chương Cơ chế dự phòng để học cùng toàn bộ hành vi dự phòng và xử lý lỗi của `ResourceBundle`.

Chương kế tiếp giải quyết bước sau tra cứu: thông điệp thường chứa dữ liệu động như tên người dùng, số lượng hoặc tổng tiền. **Ghép chuỗi thủ công không an toàn cho ngữ pháp của mọi ngôn ngữ**, nên Java có `MessageFormat`.
