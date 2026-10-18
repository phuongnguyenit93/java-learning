# Nạp tài nguyên từ classpath

ClassLoader không chỉ liên quan tới bytecode trong tệp `.class`. Ứng dụng Java thường đóng gói thêm:

```text
config/default.yml
META-INF/services/...
templates/email.txt
plugin.properties
```

Những tệp này là **tài nguyên trên classpath**. Chúng có thể nằm trong thư mục, JAR, runtime image hoặc nguồn mà ClassLoader hiểu được.

Phần này dùng `URL`, `InputStream`, `Path` và try-with-resources để quan sát tài nguyên. Nếu các API I/O đó còn mới, chỉ cần giữ mô hình tư duy “tài nguyên có thể được tìm và đọc như một luồng dữ liệu”; chi tiết về luồng dữ liệu/tệp thuộc module `io` và không cần học lại ở đây.

Điểm dễ nhầm nhất là đường dẫn tài nguyên của `Class`, đường dẫn tài nguyên của `ClassLoader` và đường dẫn trên hệ thống tệp **không dùng cùng mô hình tư duy**.

## <a id="class-resource">Class.getResource: đường dẫn tương đối theo gói (package) hoặc tuyệt đối từ gốc tài nguyên</a>

`Class#getResource(String)` có hai cách hiểu path.

Giả sử class:

```text
com/example/plugins/PaymentPlugin.class
```

và resources:

```text
com/example/plugins/plugin.properties
config/plugins.yml
```

### Không có dấu /

```java
URL url = PaymentPlugin.class.getResource("plugin.properties");
```

Path được hiểu **relative với package của class**:

```text
"plugin.properties"
→ com/example/plugins/plugin.properties
```

Điều này hữu ích khi resource đi sát một class/package.

### Có dấu / ở đầu

```java
URL url = PaymentPlugin.class.getResource("/config/plugins.yml");
```

Path được hiểu từ resource root:

```text
"/config/plugins.yml"
→ config/plugins.yml
```

Đây là rule rất đáng nhớ:

```text
Class.getResource("x")
→ package-relative

Class.getResource("/x")
→ absolute từ resource root của class/module
```

> **Nâng cao — JPMS boundary:** với class thuộc **named module**, dấu `/` không biến lookup thành một cách vượt qua ranh giới module. `Class.getResource` tìm resource trong module của chính class theo các quy tắc resource access của module; với non-`.class` resource, mức độ mở của package có thể quyết định resource có được truy cập hay không. Vì vậy cùng một path có thể hoạt động trên classpath phẳng nhưng trả `null` khi chuyển sang named module nếu package/resource không được expose phù hợp.

API trả `null` nếu không tìm thấy resource, nên code cần xử lý trường hợp này rõ ràng:

```java
URL url = PaymentPlugin.class.getResource("plugin.properties");
if (url == null) {
    throw new IllegalStateException("Missing plugin.properties");
}
```

## <a id="loader-resource">ClassLoader.getResource: tên tính từ gốc tài nguyên của ClassLoader</a>

`ClassLoader#getResource(String)` dùng resource name theo root semantics. Convention là **không có leading slash**:

```java
ClassLoader loader = PaymentPlugin.class.getClassLoader();

URL url = loader.getResource("config/plugins.yml");
```

So sánh:

| API | Input | Resource được tìm |
| --- | --- | --- |
| `PaymentPlugin.class.getResource("plugin.properties")` | relative | `com/example/plugins/plugin.properties` |
| `PaymentPlugin.class.getResource("/config/plugins.yml")` | absolute | `config/plugins.yml` |
| `loader.getResource("config/plugins.yml")` | root-relative | `config/plugins.yml` |

Một lỗi phổ biến:

```java
loader.getResource("/config/plugins.yml");
```

Với `ClassLoader` API, leading slash không mang semantics “absolute” như `Class.getResource`; dùng resource name không có slash đầu là cách portable đúng contract.

> **Nâng cao — JPMS boundary:** trong môi trường named module, các quy tắc encapsulation của module vẫn áp dụng. `ClassLoader.getResource` không phải một “lối tắt” để bỏ qua ranh giới JPMS; khi chuyển từ classpath phẳng sang module path, cần kiểm tra cả loader visibility lẫn module resource access.

### Kết quả tìm tài nguyên vẫn phụ thuộc ClassLoader

Hai plugin loaders có thể cùng tìm:

```text
plugin.properties
```

nhưng nhận hai resource khác nhau vì source/namespace của hai loader khác nhau.

Do đó khi debug resource:

```text
resource name
  +
loader dùng để lookup
→ kết quả
```

chứ không chỉ nhìn mỗi string path.

## <a id="resource-enumeration">Một tên tài nguyên có thể có nhiều kết quả</a>

`getResource` trả một result phù hợp theo lookup order của loader. Nhưng với metadata kiểu SPI hoặc config fragment, classpath có thể chứa nhiều resource cùng tên.

Ví dụ:

```text
library-a.jar!/META-INF/services/com.example.api.Plugin
library-b.jar!/META-INF/services/com.example.api.Plugin
```

Khi cần tất cả:

```java
import java.net.URL;
import java.util.Enumeration;

Enumeration<URL> resources =
        loader.getResources("META-INF/services/com.example.api.Plugin");

while (resources.hasMoreElements()) {
    System.out.println(resources.nextElement());
}
```

Đừng thay `getResources` bằng việc gọi `getResource` rồi giả định chỉ có một file. Service/provider style metadata thường dựa chính vào khả năng aggregate nhiều resource.

Thứ tự tìm kiếm có thể phụ thuộc vào cách triển khai loader và chiến lược delegation. Nếu hành vi nghiệp vụ phụ thuộc vào thứ tự resource, hợp đồng đó phải được thiết kế rõ ràng thay vì vô tình phụ thuộc vào thứ tự classpath.

## <a id="resource-stream-lifecycle">Luồng dữ liệu của tài nguyên cũng cần vòng đời rõ ràng</a>

`getResourceAsStream` thuận tiện khi chỉ cần đọc bytes/text:

```java
try (InputStream input =
             loader.getResourceAsStream("config/plugins.yml")) {

    if (input == null) {
        throw new IllegalStateException("Missing config/plugins.yml");
    }

    byte[] bytes = input.readAllBytes();
    System.out.println(new String(bytes, StandardCharsets.UTF_8));
}
```

Ba rule:

```text
not found
→ có thể trả null

found
→ nhận InputStream

dùng xong
→ close bằng try-with-resources
```

Không nên giả định stream là `FileInputStream`. Resource có thể đến từ JAR hoặc protocol khác.

Với plugin loader có lifecycle ngắn, việc đóng stream và đóng loader/resource container khi thích hợp giúp tránh file handle/resource leak, đặc biệt trên Windows nơi open JAR/file handle có thể ảnh hưởng replace/delete artifact.

## <a id="classpath-vs-filesystem">Tài nguyên trên classpath không nhất thiết là tệp trên hệ thống</a>

Code sau chỉ đúng trong một số deployment:

```java
URL url = loader.getResource("config/plugins.yml");
Path path = Path.of(url.toURI());
```

Nếu URL là `file:`, conversion có thể hợp lệ. Nhưng resource đóng gói trong JAR có thể có URL dạng:

```text
jar:file:/app/app.jar!/config/plugins.yml
```

và JDK runtime resource có thể dùng protocol khác như `jrt:`.

Vì vậy:

```text
classpath resource
≠
filesystem file
```

Nếu chỉ cần đọc resource, ưu tiên API stream:

```java
try (InputStream input =
             loader.getResourceAsStream("config/plugins.yml")) {
    ...
}
```

Nếu thật sự cần một `Path` để thư viện khác xử lý, ứng dụng phải quyết định chiến lược riêng: copy resource ra file tạm, mount filesystem phù hợp, hoặc yêu cầu config nằm ngoài classpath.

## <a id="resource-vs-class-loading">Tìm tài nguyên khác với nạp và định nghĩa class</a>

Ví dụ plugin xuyên suốt đến đây có hai không gian tìm kiếm song song:

```text
class lookup
→ binary class name
→ ClassLoader

resource lookup
→ resource name/path
→ Class / ClassLoader resource API
```

Resource lookup không tự initialize class. Chương tiếp theo quay lại lifecycle để xem chính xác static initialization diễn ra ra sao, được khóa thế nào và lỗi khởi tạo để lại trạng thái gì.
