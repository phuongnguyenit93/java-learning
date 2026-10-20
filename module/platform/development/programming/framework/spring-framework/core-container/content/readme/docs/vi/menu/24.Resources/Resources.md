<a id="back-to-top"></a>

# Tài nguyên

## Menu
- [Resource trừu tượng hóa tài nguyên ngoài mã Java](#resources-001)
- [ClassPathResource, FileSystemResource và UrlResource](#resources-002)
- [ResourceLoader và ý nghĩa các tiền tố đường dẫn](#resources-003)
- [ResourcePatternResolver, classpath: và classpath*:](#resources-004)
- [Tìm nhiều tài nguyên bằng mẫu đường dẫn](#resources-005)
- [Tiêm Resource qua cấu hình hoặc điểm tiêm phù hợp](#resources-006)
- [Đọc InputStream và đóng luồng đúng trách nhiệm](#resources-007)
- [Tài nguyên trong JAR không nhất thiết chuyển được thành File](#resources-008)
- [Kiểm tra tồn tại, khả năng đọc và xử lý lỗi đường dẫn](#resources-009)

## <a id="resources-001">Resource trừu tượng hóa tài nguyên ngoài mã Java</a>

<details>
<summary>Xem chi tiết</summary>

# Resource — trừu tượng hóa tài nguyên ngoài mã Java trong Spring Core Container

## 1. Tại sao ứng dụng cần Resource?

Một chương trình Java thường phải đọc dữ liệu **không phải mã nguồn Java**: tệp cấu hình, mẫu email, chứng chỉ công khai, nội dung văn bản, template, ảnh, schema XML hoặc tài liệu nằm trong JAR, trên đĩa hay ở một URL. Nếu code nghiệp vụ tự nhận `File` cho mọi trường hợp, ứng dụng dễ chạy tốt trong IDE nhưng thất bại sau khi đóng gói JAR hoặc chuyển sang container.

Spring Framework giải quyết bài toán bằng giao diện **`org.springframework.core.io.Resource`**: một **descriptor trừu tượng** cho một tài nguyên có thể được đọc qua stream, đồng thời cung cấp các thông tin như trạng thái tồn tại, mô tả, URL/URI khi khả dụng, và khả năng lấy file vật lý khi nguồn thực sự hỗ trợ.

Tư tưởng chính:

- Code nghiệp vụ thường nhận **`Resource`**, không cần biết resource thực sự là một classpath entry, filesystem path, một URL hay dữ liệu đã có trong bộ nhớ.
- Khi cần đọc, code gọi **`getInputStream()`** và đóng stream theo đúng ownership.
- Resource **không bảo đảm** luôn có một `java.io.File`; một entry bên trong JAR có thể đọc qua stream nhưng không phải file riêng trên filesystem.
- Resource **không phải nội dung**; đây là đối tượng mô tả cách lấy nội dung, và một số loại resource chỉ dùng được một lần.
- Spring dùng abstraction này ở nhiều APIs: context loading, `@PropertySource`, XML bean definitions, template loading và ứng dụng do bạn tự viết.

**Ví dụ trực giác:**

```java
Resource template = new ClassPathResource("templates/welcome.txt");

try (InputStream stream = template.getInputStream()) {
    String content = new String(
            stream.readAllBytes(), StandardCharsets.UTF_8);
}
```

Đây là **snippet tích hợp**, yêu cầu imports `Resource`, `ClassPathResource`, `InputStream`, `StandardCharsets`, khai báo `IOException`/xử lý ngoại lệ và tài nguyên có thật tại `templates/welcome.txt` trên classpath. Cách đọc này phù hợp cả khi resource nằm trong thư mục `target/classes` hoặc bên trong JAR.

## 2. Giao diện Resource và InputStreamSource

`Resource` kế thừa `InputStreamSource`. Hợp đồng trọng tâm:

```java
public interface InputStreamSource {
    InputStream getInputStream() throws IOException;
}
```

Ngoài `getInputStream()`, `Resource` công bố các API quan trọng:

| API | Dùng để làm gì | Cần thận trọng |
|---|---|---|
| `getInputStream()` | Mở stream để đọc nội dung | Caller phải đóng; một số resource chỉ có stream dùng một lần |
| `exists()` | Kiểm tra có vẻ tài nguyên đang tồn tại | Không bảo đảm stream luôn mở thành công ở lần đọc sau |
| `isReadable()` | Kiểm tra khả năng đọc theo implementation | Không thay cho xử lý `IOException` khi thực sự đọc |
| `isOpen()` | Cho biết descriptor giữ một stream đã mở | `true` thường là **one-shot**; không gọi đọc nhiều lần |
| `isFile()` | Hỏi tài nguyên có phải regular filesystem file phù hợp không | Không suy rằng mọi classpath resource là file |
| `getURL()`, `getURI()` | Truy xuất locator khi supported | Có thể ném `IOException`; không phải mọi source có URL/URI thích hợp |
| `getFile()` | Lấy `java.io.File` vật lý | Có thể thất bại với JAR entry/remote resource |
| `contentLength()` | Độ dài bytes nếu implementation cung cấp | Có thể tốn I/O; với one-shot source có thể tiêu thụ stream |
| `lastModified()` | Metadata sửa đổi nếu có | Không có ý nghĩa/không được hỗ trợ ở mọi loại |
| `createRelative(path)` | Tạo descriptor tương đối theo quy tắc implementation | Semantics phụ thuộc loại resource |
| `getFilename()` | Tên cuối của resource nếu xác định được | Có thể `null`; không phải đường dẫn đầy đủ |
| `getDescription()` | Mô tả hữu ích trong log hoặc exception | Có thể chứa đường dẫn/URL nhạy cảm |

Đây là **bản tóm tắt các method**, không phải interface Java hoàn chỉnh để người học định nghĩa lại. Spring Reference trình bày `Resource` là abstraction hữu ích hơn `java.net.URL` trong một số trường hợp như classpath resource và resource tồn tại hay không.

### 2.1. Resource là descriptor, không phải byte[]

```java
Resource resource = loader.getResource(
        "classpath:data/report.txt");
```

Câu lệnh trên thường chỉ **tạo/tra descriptor**. Nó không có nghĩa file chắc chắn tồn tại hoặc toàn bộ nội dung đã được tải vào RAM. Thời điểm I/O thực tế có thể là khi gọi `exists()`, `getInputStream()`, `contentLength()` hoặc metadata methods khác.

Không nên gọi `getInputStream()` rồi giữ stream mở ở singleton bean trong suốt vòng đời ứng dụng nếu chỉ cần đọc nội dung một lần khi khởi tạo. Stream là tài nguyên I/O cần được đóng; còn `Resource` descriptor có thể được giữ và mở lại khi implementation hỗ trợ.

### 2.2. isOpen() không phải câu hỏi “file hiện có đang mở ở OS?”

`Resource.isOpen()` có nghĩa resource descriptor đại diện cho **stream đã mở**, thường không có khả năng tạo nhiều stream độc lập. Nó **không phải** một phép kiểm tra chung rằng user khác đang mở tệp hay resource đang lock.

`InputStreamResource` thông thường báo `isOpen() == true`, trong khi `ClassPathResource`, `FileSystemResource`, `ByteArrayResource` thường báo `false`, kể cả khi bạn đã tự gọi `getInputStream()` và đóng stream đó.

## 3. Các triển khai Resource phổ biến

| Loại | Nguồn dữ liệu | Cách khởi tạo điển hình | Lưu ý |
|---|---|---|---|
| `ClassPathResource` | Một entry trên classpath | `new ClassPathResource("data/a.txt")` | Chạy được trong JAR qua stream; `getFile()` không luôn hợp lệ |
| `FileSystemResource` | Tệp trên filesystem; hỗ trợ File/Path | `new FileSystemResource(path)` | Đường dẫn tương đối tùy working directory |
| `PathResource` | `java.nio.file.Path` | `new PathResource(path)` | Hữu ích khi dùng NIO `Path`/Files |
| `UrlResource` | `java.net.URL` (`file:`, `https:`, ...) | `new UrlResource(url)` | Network I/O, timeout/SSRF và trust boundaries |
| `ByteArrayResource` | Byte array trong RAM | `new ByteArrayResource(bytes)` | Tạo stream mới mỗi lần; phù hợp test/in-memory payload |
| `InputStreamResource` | Một `InputStream` đã có | `new InputStreamResource(stream)` | One-shot, `isOpen()=true`; không đọc lại |
| `ServletContextResource` | Tài nguyên web application root | Thường từ `WebApplicationContext` | Phụ thuộc servlet container, file vật lý không chắc có |

Tất cả đều thực hiện cùng ý tưởng **dùng Resource làm interface tại ranh giới giữa code và nơi lưu trữ**. Tuy nhiên, không đồng nhất khả năng hỗ trợ methods metadata của chúng.

### 3.1. ClassPathResource: tệp đóng gói cùng ứng dụng

Maven cấu trúc tiêu chuẩn:

```text
resource-demo/
├── pom.xml
└── src/main/
    ├── java/example/ResourceDemo.java
    └── resources/
        └── templates/
            └── greeting.txt
```

Tệp `src/main/resources/templates/greeting.txt` được Maven copy vào runtime classpath. Bạn có thể đọc bằng:

```java
Resource greeting = new ClassPathResource(
        "templates/greeting.txt");

try (InputStream in = greeting.getInputStream()) {
    String text = new String(
            in.readAllBytes(), StandardCharsets.UTF_8);
}
```

Đây là snippet tích hợp; cần các imports Spring/JDK và tài nguyên đã được đóng gói.

**Không nên làm thế này với classpath resource nếu ứng dụng có thể chạy trong JAR:**

```java
File file = new ClassPathResource(
        "templates/greeting.txt").getFile();
```

Trong IDE hoặc test chạy từ một thư mục classes, `getFile()` **có thể** hoạt động. Khi resource ở bên trong JAR nén, entry không phải một file độc lập để Java `File` trỏ vào, nên `getFile()` có thể ném `FileNotFoundException`/ `IOException`. Dùng **stream** cho nội dung hoặc API hỗ trợ URL/JAR entry khi thực sự cần.

### 3.2. FileSystemResource và PathResource

Khi tài nguyên được triển khai tách khỏi JAR, ví dụ `/etc/app/config.properties` hoặc một thư mục đã mount trong container:

```java
Path path = Path.of("/etc/app/config.properties");
Resource config = new FileSystemResource(path);

try (InputStream in = config.getInputStream()) {
    Properties p = new Properties();
    p.load(in);
}
```

Đây là snippet tích hợp, cần imports `Path`, `InputStream`, `Properties`, `Resource`, `FileSystemResource`, và đường dẫn có thật. Lưu ý `Properties.load(InputStream)` đọc theo quy tắc encoding của Java properties truyền thống; với nội dung UTF-8 có dấu tiếng Việt, cân nhắc `Properties.load(Reader)` dùng `InputStreamReader(..., StandardCharsets.UTF_8)`.

`PathResource` cũng bọc `Path` và thao tác bằng NIO. Nó có khác biệt nhất định về `createRelative` so với `FileSystemResource`; nếu logic relative path là yếu tố quan trọng, test trực tiếp implementation đang dùng.

**Lưu ý:** Nếu caller truyền một path tương đối, ví dụ `Path.of("config/app.properties")`, nó có thể được resolve từ **current working directory** của JVM, không phải tự nhiên từ `src/main/resources` hay thư mục chứa JAR.

### 3.3. UrlResource

```java
Resource remote = new UrlResource(
        "https://example.org/data/sample.txt");

try (InputStream in = remote.getInputStream()) {
    // Đọc bytes từ nguồn cho phép và xử lý theo giao thức.
}
```

Đây là snippet minh họa **có thể phát sinh network I/O**; không nằm trong chương trình chạy offline ở phần 8. `UrlResource` dựa trên Java URL/URLConnection, nên khả năng truy cập thực tế phụ thuộc protocol handler, network, TLS và permissions.

Không dùng URL từ người dùng không tin cậy mà không kiểm soát destination, redirects, private IP/metadata service, kích thước và timeout; **SSRF** có thể xảy ra nếu backend được phép truy cập các dịch vụ nội bộ. Với HTTP production, dùng HTTP client chuyên dụng khi cần kiểm soát timeout, redirects, status code, auth headers hoặc streaming policy sâu hơn.

URL `file:` là cách định vị một file local qua URL và `UrlResource`; nó không phải file HTTP. Ngược lại, `https:` thường không hỗ trợ `getFile()` thành file local.

### 3.4. ByteArrayResource

```java
byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
Resource resource = new ByteArrayResource(data);
```

Đây là snippet tích hợp. `ByteArrayResource` tạo **`ByteArrayInputStream` mới** khi gọi `getInputStream()` và rất hữu ích cho test, email attachment hoặc những APIs nhận `Resource` nhưng dữ liệu vốn đã nằm trong bộ nhớ.

Bạn có thể đọc lặp lại **nếu dữ liệu byte array được giữ ổn định**:

```java
String first = readUtf8(resource);
String second = readUtf8(resource);
```

Đoạn này là snippet tích hợp dựa trên helper `readUtf8` trong demo phần 8. Việc read lại phụ thuộc implementation; không suy từ interface `Resource` rằng mọi resource đều mở lại được.

### 3.5. InputStreamResource

```java
InputStream source = new ByteArrayInputStream(
        "once".getBytes(StandardCharsets.UTF_8));
Resource singleUse = new InputStreamResource(source);
```

Đây là snippet tích hợp. `InputStreamResource` được xây quanh **stream đã mở** và báo `isOpen()=true`. Nó phù hợp khi API đòi `Resource` nhưng caller chỉ có một stream; **không phù hợp** nếu library yêu cầu inspect content length bằng cách đọc, retry upload hoặc mở stream nhiều lần.

Sau khi stream đã được tiêu thụ/đóng, gọi lại `getInputStream()` trên cùng `InputStreamResource` thường gây `IllegalStateException` theo implementation hoặc không còn dữ liệu hợp lệ. Không dùng loại này làm descriptor dài hạn để cache.

Spring Reference khuyến nghị **ưu tiên `ByteArrayResource` hoặc resource file-based** khi có thể; chỉ dùng `InputStreamResource` khi không có nguồn biểu diễn phù hợp hơn.

### 3.6. ServletContextResource

Trong ứng dụng Servlet, một `WebApplicationContext` có thể trả `ServletContextResource` để resolve đường dẫn từ **web app root**. Nó cho phép stream access và URL access trong những ngữ cảnh phù hợp; `getFile()` chỉ có thể khi nội dung webapp được triển khai thành file vật lý thay vì nằm trong archive hoặc container-managed storage.

`ServletContextResource` không tự xuất hiện trong một ứng dụng Spring Core CLI dùng `new DefaultResourceLoader()`. Nó cần servlet environment và không phải phần bắt buộc của demo Java console.

## 4. ResourceLoader: tách code dùng tài nguyên khỏi cách định vị

`ResourceLoader` cung cấp API đơn giản:

```java
public interface ResourceLoader {
    Resource getResource(String location);
    ClassLoader getClassLoader();
}
```

Đây là dạng chữ ký minh họa từ Spring, không phải interface cần copy tự định nghĩa lại.

Ví dụ:

```java
ResourceLoader loader = new DefaultResourceLoader();
Resource image = loader.getResource(
        "classpath:assets/logo.png");
```

Đây là snippet tích hợp; cần imports `ResourceLoader`, `DefaultResourceLoader`, `Resource` và classpath resource thực tế.

Điểm hữu ích là `ResourceLoader` nhận một **location string** và trả `Resource`, để code khác chỉ cần làm việc với interface. Spring `ApplicationContext` cũng implements `ResourceLoader`, vì vậy các managed components có thể inject interface này mà không phải phụ thuộc toàn bộ context.

### 4.1. Những prefix quan trọng

| Prefix trong location | Ý nghĩa thường gặp | Ví dụ |
|---|---|---|
| `classpath:` | Tìm một resource trên classpath | `classpath:templates/welcome.txt` |
| `file:` | Tài nguyên filesystem dưới dạng file URL | `file:/var/app/data.txt` |
| `https:` | URL HTTPS có handler phù hợp | `https://example.org/data.txt` |
| Không có prefix | **Phụ thuộc loại ResourceLoader/ApplicationContext** | `templates/welcome.txt` |
| `classpath*:` | Prefix đặc biệt cho **ResourcePatternResolver**, có thể lấy nhiều matches | `classpath*:META-INF/example.xml` |

**Không giả định `classpath*:` là một biến thể thông thường của `getResource(String)`.** Khi cần nhiều resources hoặc wildcard matching, dùng `ResourcePatternResolver.getResources(String)`, thay vì ép `ResourceLoader.getResource` trả một resource đại diện cho nhiều file.

### 4.2. Location không có prefix có thể đổi nghĩa theo context

Một ví dụ kinh điển từ Spring Reference:

```java
Resource r = context.getResource(
        "config/some-template.txt");
```

Đây là snippet tích hợp, cần một `ApplicationContext context` cụ thể. Trong `ClassPathXmlApplicationContext`, đường dẫn không prefix có thể được xử lý theo classpath; trong `FileSystemXmlApplicationContext`, theo filesystem; trong web context, theo servlet context.

Muốn tránh phụ thuộc implicit context type, dùng **prefix rõ ràng**:

```java
Resource cp = context.getResource(
        "classpath:config/some-template.txt");
Resource local = context.getResource(
        "file:/var/app/some-template.txt");
```

Đây là hai snippets tích hợp. Với Windows, dùng `Path.toUri()`/URL construction thích hợp để tránh tự nối sai URI `file:` hoặc backslash.

### 4.3. ResourceLoaderAware so với constructor injection

Spring hỗ trợ interface callback `ResourceLoaderAware`:

```java
public final class TemplateService
        implements ResourceLoaderAware {

    private ResourceLoader loader;

    @Override
    public void setResourceLoader(ResourceLoader loader) {
        this.loader = loader;
    }
}
```

Đây là snippet tích hợp, cần imports `ResourceLoaderAware` và `ResourceLoader`. Khi bean được Spring quản lý, context có thể gọi `setResourceLoader` trong lifecycle aware callbacks.

Trong ứng dụng mới, có thể ưu tiên **constructor injection**:

```java
@Component
public final class TemplateService {
    private final ResourceLoader loader;

    public TemplateService(ResourceLoader loader) {
        this.loader = loader;
    }
}
```

Đây cũng là snippet tích hợp, cần import `@Component`, `ResourceLoader` và context có annotation injection infrastructure. Cả hai cách giúp code nghiệp vụ chỉ phụ thuộc interface, không cần cầm toàn bộ `ApplicationContext` nếu không sử dụng các khả năng khác.

## 5. ResourcePatternResolver và nhiều tài nguyên

Một `ResourceLoader` thông thường trả **một `Resource`** cho một location. Khi cần tìm nhiều resources trùng tên trên nhiều JARs hoặc dùng wildcard `**/*.xml`, Spring cung cấp:

```java
public interface ResourcePatternResolver extends ResourceLoader {
    Resource[] getResources(String locationPattern)
            throws IOException;
}
```

Đây là chữ ký trích lược. Implementation phổ biến là `PathMatchingResourcePatternResolver`.

```java
ResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver();

Resource[] allSchemas = resolver.getResources(
        "classpath*:META-INF/acme/*.xsd");
```

Đây là snippet tích hợp, giả định có ít nhất một schema phù hợp và các imports. Prefix `classpath*:` có thể tìm nhiều resources với cùng đường dẫn trên các classpath entries; resolver có thể kết hợp wildcard pattern.

### 5.1. Không dựa vào thứ tự matches không được cam kết

```java
for (Resource r : allSchemas) {
    System.out.println(r.getDescription());
}
```

Nếu code dùng kết quả scanning, **không dựa vào thứ tự mặc định** của các resources từ nhiều JAR/class loaders. Nếu nghiệp vụ đòi precedence, hãy định nghĩa cách sắp xếp/merge rõ ràng.

### 5.2. Classpath wildcard có giới hạn portability

JARs và classloaders khác nhau có thể expose directory entries/URL handlers khác nhau. Spring Reference cảnh báo wildcard resolution trên JAR cần được test trong môi trường deploy thật và `classpath*:*.xml` ở root không phải lúc nào cũng tìm đầy đủ matches như mong đợi.

Để dễ di chuyển giữa môi trường, ưu tiên **đường dẫn cụ thể** khi có thể; nếu cần scanning, viết integration test trên executable JAR và container runtime. Đừng tính cứng số resource matches nếu dependency graph hoặc classpath có thể thay đổi.

## 6. Quy tắc đọc Resource an toàn

### 6.1. Luôn đóng InputStream

```java
public static String readText(Resource resource)
        throws IOException {

    try (InputStream in = resource.getInputStream()) {
        return new String(
                in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

Đây là helper Java tích hợp; cần import `Resource`, `InputStream`, `IOException`, `StandardCharsets`. `try-with-resources` bảo đảm stream được đóng khi đọc thành công hoặc có exception.

**Lưu ý về kích thước:** `readAllBytes()` tải toàn bộ nội dung vào RAM. Nó phù hợp cho **tệp cấu hình nhỏ đã tin cậy**; không phù hợp cho tài nguyên lớn hoặc remote resource không giới hạn. Trong production, stream theo từng block và áp giới hạn số bytes.

### 6.2. exists() không phải guarantee chống race

```java
if (resource.exists()) {
    try (InputStream in = resource.getInputStream()) {
        // Vẫn phải xử lý IOException khi resource bị xóa hoặc không còn truy cập được.
    }
}
```

Đây là snippet minh họa. Giữa `exists()` và `getInputStream()`, file có thể bị xóa, permission có thể đổi hoặc remote server có thể ngắt kết nối. Đây là **time-of-check/time-of-use** (TOCTOU) problem; cứ mở stream và xử lý lỗi thật mới là cách tiếp cận bền vững.

### 6.3. Không suy rằng getFile() luôn thành công

```java
File localFile = resource.getFile();
```

Lệnh này chỉ nên dùng khi bạn **biết resource thật sự là file vật lý có thể truy cập**. Nó sẽ không biến một classpath JAR entry hay HTTPS object thành file trên ổ đĩa.

Nếu một third-party API chỉ nhận `File`, nhưng nguồn của bạn là `ClassPathResource` đóng gói trong JAR, các lựa chọn là:

1. Ưu tiên overload nhận `InputStream`, `Reader`, `URL` hoặc bytes nếu library có.
2. Nếu bắt buộc nhận `File`, sao chép resource **có kiểm soát** sang một temporary file, gọi library, rồi dọn file. Cần xem permissions, size caps, cleanup, concurrency và xử lý secrets.

Không thử sửa bằng cách lấy `getURL().getPath()` rồi `new File(path)` từ `jar:file:...!`; đó không phải filesystem path thông thường.

### 6.4. contentLength() và one-shot streams

`contentLength()` là một metadata convenience, nhưng với implementation không có chiều dài sẵn, cách tính có thể cần đọc dữ liệu. Riêng `InputStreamResource` có stream một lần, Spring cảnh báo tránh tính content length kiểu tổng quát vì điều đó có thể **tiêu thụ stream** trước khi bạn thực sự gửi/đọc nội dung.

Nếu làm upload/email attachment, dùng `ByteArrayResource` khi bytes đã có trong RAM và đủ nhỏ; dùng nguồn file-based/streaming với metadata rõ khi payload lớn. Đừng gọi `contentLength()` tùy tiện trước rồi hy vọng một one-shot stream còn nguyên.

### 6.5. getFilename() và getDescription()

`getFilename()` dùng khi cần tên file gợi ý (ví dụ filename của attachment), nhưng có thể `null` hoặc chứa giá trị không an toàn từ URL/remote path. Không dùng trực tiếp để chọn đường ghi filesystem mà không sanitize/path-normalize.

`getDescription()` tốt cho diagnostics. Tuy nhiên URL descriptions có thể chứa query strings/tokens; log một cách có kiểm soát. `Resource` không phải một security policy hoặc secret-redaction layer.

## 7. Resource abstraction và @Value/@PropertySource

### 7.1. Tiêm Resource vào Spring-managed bean

Spring hỗ trợ type conversion từ location string sang `Resource` trong nhiều ngữ cảnh. Ví dụ:

```java
@Component
public final class BannerService {

    private final Resource banner;

    public BannerService(
            @Value("classpath:assets/banner.txt")
            Resource banner) {
        this.banner = banner;
    }
}
```

Đây là snippet tích hợp; cần imports `@Component`, `@Value`, `Resource` và bean được container quản lý, cùng resource có thật. `@Value` cung cấp location string, hạ tầng Spring resolve/convert thành `Resource` theo context.

Một số ứng dụng chọn inject `ResourceLoader` thay vì một `Resource` cố định khi cần chọn location theo input hoặc cấu hình runtime.

### 7.2. @PropertySource liên hệ với Resource

`@PropertySource("classpath:app.properties")` sử dụng resource-loading infrastructure để **đọc tệp cấu hình** thành một nguồn các key-value properties; còn `Resource` là abstraction cấp thấp để tiếp cận nội dung của tệp đó.

```java
@Configuration
@PropertySource(
        value = "classpath:app.properties",
        encoding = "UTF-8")
class PropertiesConfig {
}
```

Đây là snippet tích hợp, cần imports annotation và resource thực sự có trên classpath. Nó **không** làm cho mọi file `app.properties` trong mọi thư mục/JAR tự được trộn; còn phụ thuộc location resolver, processing và các resource name rules.

**Phân biệt trách nhiệm:**

| Cơ chế | Câu hỏi giải quyết |
|---|---|
| `Resource` | Tôi đọc bytes của một nguồn như thế nào? |
| `ResourceLoader` | Tôi biến location string thành Resource ra sao? |
| `ResourcePatternResolver` | Tôi tìm tất cả resources khớp location/pattern thế nào? |
| `@PropertySource` | Tôi đưa key-value từ một resource vào `Environment` khi configuration processing thế nào? |
| `Environment` | Tôi resolve property values theo sources/precedence nào? |
| `@Value` | Tôi inject một resolved/configured value hoặc Resource vào bean ra sao? |

Nền tảng `Resource` dùng được **ngay cả khi bạn không dựng ApplicationContext**. Ví dụ runnable dưới đây chỉ phụ thuộc `spring-core`.

## 8. Ví dụ Java 17+ chạy độc lập: đọc tài nguyên từ bốn nguồn

Demo chạy offline, không phụ thuộc Spring Boot hoặc HTTP server. Nó cho thấy:

1. Đọc classpath resource bằng `ClassPathResource` và qua `DefaultResourceLoader`.
2. Đọc file được tạo trong thư mục temp bằng `FileSystemResource`.
3. Đọc cùng file qua `UrlResource` với `file:` URL.
4. Đọc `ByteArrayResource` **hai lần** để chứng minh repeatability.
5. Đọc `InputStreamResource` **một lần**, và xem `isOpen()`.
6. Quan sát `exists()`, `getFilename()`, `isFile()` trên những loại đã biết.

### 8.1. Yêu cầu và cấu trúc dự án

Yêu cầu **JDK 17+**, Maven 3.9+ và Spring Framework 6.2.0.

```text
resource-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── example/
        │       └── ResourceDemo.java
        └── resources/
            └── templates/
                └── greeting.txt
```

### 8.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>resource-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
            <version>6.2.0</version>
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
            </plugin>
        </plugins>
    </build>
</project>
```

### 8.3. src/main/resources/templates/greeting.txt

```text
hello from classpath
```

Có thể có một newline cuối file; helper `readText` bên dưới dùng `trim()` để kết quả in không phụ thuộc newline đó. Demo không dùng ký tự đặc biệt ở file nhằm giữ stdout giống nhau trên các môi trường terminal.

### 8.4. src/main/java/example/ResourceDemo.java

```java
package example;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;

public class ResourceDemo {

    public static void main(String[] args) throws IOException {
        Resource classpath = new ClassPathResource(
                "templates/greeting.txt");
        ResourceLoader loader = new DefaultResourceLoader();
        Resource fromLoader = loader.getResource(
                "classpath:templates/greeting.txt");

        System.out.println("classpath exists: "
                + classpath.exists());
        System.out.println("classpath filename: "
                + classpath.getFilename());
        System.out.println("classpath text: "
                + readText(classpath));
        System.out.println("loader text: "
                + readText(fromLoader));

        byte[] raw = "hello from memory"
                .getBytes(StandardCharsets.UTF_8);
        Resource memory = new ByteArrayResource(raw);
        String first = readText(memory);
        String second = readText(memory);

        System.out.println("memory text: " + first);
        System.out.println("memory repeatable: "
                + first.equals(second));

        Resource oneShot = new InputStreamResource(
                new ByteArrayInputStream(
                        "hello once".getBytes(
                                StandardCharsets.UTF_8)));

        System.out.println("one-shot isOpen: "
                + oneShot.isOpen());
        System.out.println("one-shot text: "
                + readText(oneShot));

        Path temporary = Files.createTempFile(
                "resource-example-", ".txt");

        try {
            Files.writeString(
                    temporary,
                    "hello from filesystem\n",
                    StandardCharsets.UTF_8);

            Resource disk = new FileSystemResource(temporary);
            Resource viaUrl = new UrlResource(
                    temporary.toUri().toURL());

            System.out.println("disk exists: " + disk.exists());
            System.out.println("disk isFile: " + disk.isFile());
            System.out.println("disk text: " + readText(disk));
            System.out.println("file URL same content: "
                    + readText(disk).equals(readText(viaUrl)));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String readText(Resource resource)
            throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(
                    in.readAllBytes(), StandardCharsets.UTF_8)
                    .trim();
        }
    }
}
```

### 8.5. Lệnh biên dịch và chạy

Ở thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.ResourceDemo
```

**Stdout dự kiến**, không tính log riêng của Maven nếu có:

```text
classpath exists: true
classpath filename: greeting.txt
classpath text: hello from classpath
loader text: hello from classpath
memory text: hello from memory
memory repeatable: true
one-shot isOpen: true
one-shot text: hello once
disk exists: true
disk isFile: true
disk text: hello from filesystem
file URL same content: true
```

### 8.6. Giải thích từng đoạn

- **Classpath**: Maven copy `templates/greeting.txt` vào classpath; `ClassPathResource` và loader có prefix `classpath:` đều resolve cùng nội dung. `exists()` trả true nếu resource thực sự có trong runtime classpath.
- **Filename**: `getFilename()` lấy segment tên tài nguyên cuối `greeting.txt`, không phải full path.
- **Memory**: `ByteArrayResource` tạo stream mới mỗi lần `readText`, nên hai nội dung giống hệt nhau.
- **One-shot**: `InputStreamResource` giữ stream đã tạo và báo `isOpen()=true`; chương trình chỉ đọc một lần rồi đóng.
- **Disk**: `Files.createTempFile` bảo đảm đường dẫn local được tạo, `Files.writeString` ghi UTF-8; `FileSystemResource` có khả năng đọc và `isFile()` true cho temporary regular file.
- **URL**: URL được tạo bằng `temporary.toUri().toURL()` là một **file URL**, nên `UrlResource` đọc cùng bytes như `FileSystemResource`. Không cần mạng.
- **Cleanup**: `finally` cố xóa temporary file kể cả khi một thao tác đọc sau bước tạo file ném exception.

**Giới hạn của stdout:** kết quả dựa vào file resources được đặt đúng path, working filesystem cho phép tạo/xóa temp files, và JVM chạy với môi trường mặc định phù hợp. Không in đường dẫn temp ngẫu nhiên nên các dòng còn lại ổn định.

### 8.7. Kiểm chứng classpath resource khi đóng JAR

Trong quá trình phát triển, `classpath.getFile()` có thể hoạt động vì Maven để classes/resources trong `target/classes`. Khi đóng JAR, resource có thể nằm trong archive và không trở thành `File`.

**Không đổi** helper thành:

```java
private static String readText(Resource resource)
        throws IOException {
    return Files.readString(resource.getFile().toPath());
}
```

Đây là **anti-example** cho resource có thể nằm trong JAR. Nó phụ thuộc vào file vật lý; không giữ được abstraction khi chuyển sang `jar:` URL/stream. Helper dùng `getInputStream()` như chương trình gốc bền vững hơn.

## 9. Một demo bổ sung: quét nhiều classpath resources

Nếu cần quét nhiều file có cùng pattern, dùng API riêng:

```java
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public final class PatternExample {

    public static void main(String[] args) throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();

        Resource[] resources = resolver.getResources(
                "classpath*:META-INF/spring/*.imports");

        System.out.println("matching resource count: "
                + resources.length);
    }
}
```

Đây là **ví dụ Java 17 có `main`**, có thể biên dịch với cùng Maven dependency `spring-core`, nhưng **số lượng matches phụ thuộc classpath đang chạy**. Vì vậy không có expected output nguyên số để tránh khẳng định sai. Nó không sửa tài nguyên, chỉ tra descriptors.

Với cách tổ chức modules lớn, tránh quét toàn bộ classpath trên mọi request; làm khi startup hoặc cache kết quả phù hợp, và cân nhắc chi phí I/O. Nên giới hạn pattern vào namespace của ứng dụng, ví dụ `classpath*:com/example/templates/**/*.txt`.

### 9.1. classpath: so với classpath*:

```text
classpath:config/app.properties
    -> một resource được ClassLoader/loader resolve

classpath*:config/app.properties
    -> nhiều resources cùng tên trên các classpath locations,
       thông qua ResourcePatternResolver

classpath*:com/example/**/*.xml
    -> nhiều matches với wildcard; test cả khi đóng JAR
```

`classpath*:` không tự hợp nhất nội dung nhiều resources thành một object có nghĩa nghiệp vụ; chỉ trả array descriptors. Nếu cần merge cấu hình, thứ tự nguồn/precedence phải do hệ thống cấu hình hoặc code của bạn xác định.

## 10. WritableResource: Resource không mặc định ghi được

Một số Resource implementations hỗ trợ giao diện `WritableResource`, bổ sung capability mở `OutputStream` hoặc các method phù hợp để ghi. Không vì có một `Resource` mà giả định có thể ghi bất kỳ nguồn nào, đặc biệt classpath entry bên trong JAR hoặc remote URL.

```java
WritableResource output = new PathResource(
        Path.of("/tmp/report.txt"));

try (OutputStream out = output.getOutputStream()) {
    out.write("ok".getBytes(StandardCharsets.UTF_8));
}
```

Đây là **snippet tích hợp**, cần imports `WritableResource`, `PathResource`, `Path`, `OutputStream`, `StandardCharsets` và quyền ghi vào đường dẫn. Không dùng nó cho tài nguyên đóng gói trong JAR nếu yêu cầu cập nhật tập tin sau deploy: nhiều artifact được phân phối read-only.

Đối với file uploads hoặc ghi output theo đường dẫn do người dùng cung cấp, cần kiểm soát file permissions, path traversal, overwrite policy, size limits và atomic rename khi phù hợp. `WritableResource` chỉ cung cấp thao tác I/O, **không tự thực hiện authorization**.

## 11. Resource.createRelative và địa chỉ tương đối

`Resource.createRelative(String relativePath)` cho phép mô tả tài nguyên mới tương đối với resource hiện tại. Tuy nhiên, semantics phụ thuộc implementation, ví dụ `ClassPathResource` so với `PathResource` hoặc một `FileSystemResource` đặc thù.

```java
Resource base = new ClassPathResource(
        "templates/messages/welcome.txt");

Resource sibling = base.createRelative("goodbye.txt");
```

Đây là snippet tích hợp; `sibling` có thể trỏ tới `templates/messages/goodbye.txt` theo semantics của `ClassPathResource`. Tệp chỉ có thể đọc khi thực sự tồn tại.

**Không dùng createRelative để nối trực tiếp input path không tin cậy** rồi cho người dùng đọc bất kỳ classpath secret/config nào. Kiểm soát danh sách resource được phép, canonicalize filesystem paths khi liên quan và loại bỏ traversal segments theo security policy.

Nếu code yêu cầu truy cập filesystem path xác định rõ, dùng `Path.resolve(...)` và kiểm tra containment phù hợp thay vì dựa vào một hành vi relative giống nhau trên mọi `Resource`.

## 12. Resource trong JAR, fat JAR, containers và module systems

### 12.1. “Chạy được trên máy tôi” nhưng fail sau package

Đây là triệu chứng kinh điển:

```java
File f = new ClassPathResource(
        "config/settings.xml").getFile();
```

Khi IDE dùng thư mục classes, file có thể tồn tại. Khi ứng dụng được đóng gói thành JAR, URL trở thành đường dẫn dạng archive entry (ví dụ `jar:file:...`), nên không có **file vật lý riêng** cho entry đó.

Sửa đúng:

```java
Resource r = new ClassPathResource("config/settings.xml");
try (InputStream in = r.getInputStream()) {
    // Parse XML từ stream.
}
```

Với XML untrusted, cấu hình parser chống XML External Entity (XXE) theo thư viện XML đang dùng. `Resource` không tự bảo vệ parser khỏi external entities.

### 12.2. Spring Boot nested JARs

Khi dùng Spring Boot executable JAR, launcher/classloader có thể cung cấp URLs cho nested JAR entries. Đường stream vẫn thường là abstraction thích hợp; tuyệt đối không biến một URL có dấu `!/` thành filesystem path bằng cách cắt chuỗi.

Spring Boot không làm cho toàn bộ `Resource` trở thành `File`. Nếu library cần path, hãy chọn API stream hoặc tạm giải nén một tài nguyên với chính sách cleanup rõ ràng.

### 12.3. Container filesystem có thể read-only

Trong Docker/Kubernetes, JAR thường bất biến còn external config có thể mount tại `/etc/app/config` hoặc một volume riêng. `FileSystemResource` đọc được khi path có thật và permissions phù hợp, nhưng không có bảo đảm writable. Đừng dùng `ClassPathResource` làm nơi persist user data.

### 12.4. Java module path và custom ClassLoader

`ClassPathResource` sử dụng classloader hoặc class đã được truyền vào. Với JPMS/module layers/custom classloaders, resource visibility có thể khác mô hình classpath đơn giản. Khi đóng gói thành module, cần kiểm thử thực tế khả năng tìm resource, resource path và các quy tắc truy cập của module loader.

## 13. Security: vị trí tài nguyên không phải trust boundary tự động

Resource abstraction giải quyết **cách truy cập**, không phải **quyền được truy cập**.

| Rủi ro | Ví dụ | Biện pháp |
|---|---|---|
| Path traversal | User nhập `../../secrets.txt` | Whitelist resource IDs, normalize paths, containment check |
| SSRF | User cung cấp `http://169.254.169.254/...` | Chặn internal destinations, validate redirects/DNS, restrict egress |
| Secrets trong log | In `getDescription()` có query token | Redact, log whitelist metadata |
| Memory exhaustion | `readAllBytes()` trên file nhiều GB | Stream theo chunk, giới hạn bytes |
| Stream leak | Không đóng `InputStream` | try-with-resources, ownership rõ |
| One-shot stream consumed | Tính length trước khi upload | Tránh metadata đọc stream; dùng repeatable resource hoặc length explicit |
| Resource missing/race | `exists()==true` nhưng bị xóa khi mở | Bắt và xử lý `IOException` |
| Mutable in-memory bytes | Byte array thay đổi giữa các lần đọc | Copy defensively khi cần immutability |
| Archive assumptions | `getFile()` trên classpath JAR entry | Dùng stream/URL-safe APIs |
| Trust of content | Parse XML/JSON/YAML untrusted không giới hạn | Bật parser protections, schema/data validation và limits |

### 13.1. Đừng dùng URL tuỳ ý như một “generic file upload”

Ví dụ:

```java
Resource r = loader.getResource(userSuppliedLocation);
```

Đây là **anti-pattern khi `userSuppliedLocation` không tin cậy**. Nó cho phép caller chọn location prefixes/phạm vi đọc vượt ngoài domain logic nếu không bị hạn chế. Thay bằng mapping từ **logical IDs** được phép đến location cố định/allowlisted, rồi mới tạo Resource.

### 13.2. Khi nào cần cache bytes?

Nếu resource nhỏ và ít thay đổi, có thể đọc một lần thành bytes và giữ ở cache có lifecycle/size limit. Nhưng **không cache InputStream đang mở** hoặc coi remote Resource luôn bất biến. Nếu cần dynamic reload, phải có cơ chế versioning/ETag/lastModified và policy refresh riêng; interface Resource tự nó không cung cấp một watch service.

## 14. Các lỗi phổ biến

| Hiểu nhầm | Thực tế | Cách xử lý |
|---|---|---|
| “Resource = java.io.File” | Resource là interface rộng hơn filesystem file | Dùng `InputStream`/descriptor thay vì `getFile()` bừa bãi |
| “Classpath file luôn có physical path” | JAR entry có thể không phải file riêng | Đọc qua `getInputStream()` |
| “ResourceLoader.getResource() bảo đảm resource tồn tại” | Nó có thể chỉ trả descriptor | Kiểm tra lúc đọc và xử lý I/O errors |
| “exists() true bảo đảm đọc sẽ thành công” | Race, permission, network có thể thay đổi | Bắt `IOException` tại operation |
| “isOpen() false nghĩa là stream chưa bao giờ được mở” | Nó mô tả loại descriptor, không phải trạng thái OS file lock | Hiểu semantics Resource |
| “Mọi Resource đều đọc nhiều lần” | InputStreamResource thường one-shot | Chọn ByteArrayResource hoặc file/classpath source nếu retry |
| “contentLength() luôn an toàn cho một stream” | Một số implementations có thể tiêu thụ stream | Tránh với one-shot resources |
| “classpath* là alias của classpath” | classpath* dành cho multi-resource resolution | Dùng `ResourcePatternResolver` |
| “getResource không prefix luôn là filesystem” | Phụ thuộc ResourceLoader/Context implementation | Ghi prefix rõ ràng |
| “file:/path giống URL HTTPS” | Một là file URL, một là network URL | Phân biệt protocol và security |
| “ByteArrayResource tự copy dữ liệu cho mọi use case” | API lưu byte array và có semantics cần kiểm tra | Copy defensively khi ownership thay đổi |
| “getFilename() luôn hợp lệ để đặt tên file output” | Có thể null hoặc chứa giá trị không đáng tin | Validate/sanitize filename |
| “Resource tạo ra là Spring bean” | Descriptor tạo bằng `new` vẫn dùng được không cần context | Chỉ dùng DI khi cần quản lý |
| “@PropertySource và Resource là một” | PropertySource đưa key-value vào Environment; Resource đọc bytes | Chọn đúng abstraction |
| “Resource tự watch file thay đổi” | Không có auto reload trên interface | Thiết kế refresh/reload riêng |
| “UrlResource là HTTP client đầy đủ” | Nó dựa trên URL protocol abstraction | Dùng HTTP client khi cần timeout/auth/redirect control |
| “ResourcePatternResolver always finds everything in every JAR” | ClassLoader/JAR wildcard behavior có caveats | Test packaged runtime |
| “Resource cho phép ghi vì có file name” | Ghi phụ thuộc WritableResource và backend | Kiểm tra capabilities/permissions |

### 14.1. Quy trình debug một resource không đọc được

1. Xác định **resource location** (prefix, path tương đối, tên tệp) có đúng không.
2. Nếu `classpath:`, kiểm tra `src/main/resources` đã vào runtime classpath/JAR chưa.
3. Nếu `file:`, kiểm tra working directory, path thực tế, file mounts và quyền đọc.
4. Nếu URL, kiểm tra protocol handler, DNS, TLS, timeout, status/network access và security policy.
5. Nếu có `getFile()`, xác định source có ở trong archive hay remote không.
6. Nếu `InputStreamResource`, kiểm tra stream có bị đọc/đóng một lần trước đó.
7. Kiểm tra `exists()` và `isReadable()`, nhưng vẫn phải bắt `IOException` khi mở stream.
8. Nếu cần wildcard, chắc chắn đang dùng `getResources()` trên resolver, không gọi `getResource()` nhầm.
9. Nếu đọc text, xác định charset, newline, BOM và input size.
10. Nếu parse JSON/XML/YAML, xác nhận parser/bảo mật/validation hợp lệ, không đổ lỗi cho Resource khi bytes đọc đúng.
11. Thử sau đóng gói JAR/executable archive, không chỉ trong IDE.
12. Giảm logging values/URLs nhạy cảm và thu thập exception cause/description có chọn lọc.

## 15. Kiến trúc ứng dụng: chấp nhận Resource, không chấp nhận File nếu không cần

Thiết kế cứng:

```java
public final class TemplateParser {
    public String parse(File templateFile) throws IOException {
        return Files.readString(templateFile.toPath());
    }
}
```

Đây là snippet minh họa, cần imports `File`, `Files`, `IOException`. Nó buộc callers phải có một file local.

Thiết kế linh hoạt hơn:

```java
public final class TemplateParser {
    public String parse(Resource template) throws IOException {
        try (InputStream in = template.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

Đây là snippet tích hợp cần imports `Resource`, `InputStream`, `IOException`, `StandardCharsets`. Caller có thể truyền `ClassPathResource`, `FileSystemResource`, `ByteArrayResource` hoặc loại khác thỏa contract.

**Hạn chế:** Phương án dùng `Resource` gắn code với Spring Core. Nếu module domain muốn hoàn toàn độc lập Spring, có thể nhận `InputStream` supplier, `Reader`, `Path` hoặc interface của chính ứng dụng. Tuy nhiên đừng vô tình nhận `InputStream` one-shot rồi cố retry nếu API của bạn yêu cầu mở lại.

Một abstraction custom hữu ích khi cần repeatability:

```java
@FunctionalInterface
public interface StreamSupplier {
    InputStream open() throws IOException;
}
```

Đây là snippet minh họa, cần imports và interface trong file riêng. Nó biểu đạt khả năng **mở stream mới mỗi lần**, khác với việc truyền một stream đã mở. Resource vẫn phù hợp khi bạn cần thêm thông tin file/URL/description.

## 16. Câu hỏi tự kiểm tra

**1. `Resource` thuộc package nào?**  
`org.springframework.core.io.Resource`.

**2. `Resource` extends interface nào?**  
`InputStreamSource`, với `getInputStream()`.

**3. Vì sao không nên dùng `getFile()` để đọc `ClassPathResource` trong mọi môi trường?**  
Một classpath resource trong JAR không có physical File riêng; `getInputStream()` là cách đọc tương thích hơn.

**4. `isOpen() == true` thường ám chỉ điều gì?**  
Resource descriptor đại diện cho một stream đã mở, thường chỉ đọc một lần; điển hình là `InputStreamResource`.

**5. Loại Resource nào thuận tiện khi dữ liệu đã có trong byte[] và cần đọc nhiều lần?**  
`ByteArrayResource`, vì có thể mở `ByteArrayInputStream` mới.

**6. ResourceLoader khác Resource như thế nào?**  
Loader resolve location string thành Resource descriptor; Resource cung cấp API đọc/metadata.

**7. `classpath:` và `classpath*:` khác nhau ở đâu?**  
`classpath:` định vị một resource; `classpath*:` dùng với `ResourcePatternResolver` để tìm nhiều resources/matches.

**8. `getResource("templates/x.txt")` không prefix có luôn lấy filesystem file không?**  
Không; default location resolution tùy loại loader/context.

**9. Tại sao `exists()` không thay thế try/catch khi đọc?**  
Tài nguyên có thể biến mất, mất quyền truy cập hoặc mạng thất bại sau khi kiểm tra.

**10. Khi nào nên dùng UrlResource?**  
Khi nguồn thực sự được mô tả bằng URL; với network URL phải kiểm soát bảo mật/timeout và cân nhắc HTTP client chuyên dụng.

**11. `ResourcePatternResolver` có đảm bảo wildcard classpath ổn định trên mọi executable JAR không?**  
Không hoàn toàn; phải test tùy classloader/JAR packaging.

**12. `Resource` có tự tạo một file temp cho mọi classpath JAR entry không?**  
Không. Nếu API bắt buộc `File`, ứng dụng phải tự chuyển đổi/copy có kiểm soát.

**13. Loại nào được dùng khi muốn ghi bytes qua abstraction?**  
Một implementation của `WritableResource`, ví dụ file/path-based resource hỗ trợ output.

**14. Resource có tự đọc và parse `.properties` thành Environment không?**  
Không. `Resource` chỉ trừu tượng I/O; `@PropertySource`/property loaders mới xử lý nội dung thành key-value.

**15. Có cần `ApplicationContext` để gọi `new ClassPathResource(...)` không?**  
Không. Resource APIs trong `spring-core` dùng được độc lập.

**16. Tại sao không nên log toàn bộ `getDescription()` đối với URL do người dùng cung cấp?**  
Mô tả có thể chứa query parameters, tokens hoặc nội dung nhạy cảm.

## 17. Tài liệu tham khảo chính thức

- [Spring Framework Reference — Resources](https://docs.spring.io/spring-framework/reference/core/resources.html): `Resource` interface, các implementations, `ResourceLoader`, `ResourcePatternResolver`, classpath/file/JAR behavior.
- [Spring Framework 6.2 API — Resource](https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/core/io/Resource.html): methods và capabilities của interface.
- [Spring Framework 6.2 API — ClassPathResource](https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/core/io/ClassPathResource.html): class loader behavior, stream và giới hạn file access.
- [Spring Framework 6.2 API — InputStreamResource](https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/core/io/InputStreamResource.html): one-shot stream semantics.
- [Spring Framework 6.2 API — ByteArrayResource](https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/core/io/ByteArrayResource.html): in-memory repeatable streams.
- [Spring Framework 6.2 API — ResourceLoader](https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/core/io/ResourceLoader.html): location resolution contract.
- [Spring Framework 6.2 API — ResourcePatternResolver](https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/core/io/support/ResourcePatternResolver.html): `getResources`, `classpath*:` và pattern APIs.
- [Spring Framework 6.2 API — WritableResource](https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/core/io/WritableResource.html): file/path writable resource operations.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-002">ClassPathResource, FileSystemResource và UrlResource</a>

<details>
<summary>Xem chi tiết</summary>

# ClassPathResource, FileSystemResource và UrlResource trong Spring Framework Core Container

## 1. Mục tiêu: một giao diện để truy cập nhiều nguồn tài nguyên

Một ứng dụng Spring thường phải đọc tệp cấu hình, mẫu email, chứng thư, tài liệu nhúng trong JAR, tệp do người vận hành gắn vào thư mục máy chủ hoặc tài liệu ở một URL. Java có `File`, `Path`, `URL`, `InputStream`, nhưng các kiểu này biểu diễn **những khía cạnh khác nhau**: vị trí lưu trữ, đường dẫn và dòng byte. Không phải tài nguyên nào cũng là một tệp vật lý có đường dẫn trên ổ đĩa.

Spring cung cấp giao diện `org.springframework.core.io.Resource` để mô tả **một nguồn dữ liệu có thể truy cập**, với ba implementation thường gặp:

- **`ClassPathResource`** — đọc nội dung có trên application classpath, bao gồm nội dung đóng gói trong JAR.
- **`FileSystemResource`** — truy cập một tệp/thư mục qua đường dẫn của hệ thống tệp cục bộ.
- **`UrlResource`** — bọc một `URL`/`URI` như `file:`, `https:` và các protocol mà JVM có URL handler tương ứng.

Điểm chung: code có thể làm việc với một biến kiểu `Resource` thay vì phải viết ba nhánh đọc khác nhau.

```java
public static String readResource(Resource resource) throws IOException {
    try (InputStream input = resource.getInputStream()) {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

Đây là **fragment** cần imports `Resource`, `InputStream`, `IOException` và `StandardCharsets`. Phần ví dụ runnable ở mục 9 sẽ cung cấp đầy đủ source files, dependency, lệnh chạy và output chính xác. Đoạn trên hợp với tài nguyên văn bản nhỏ; file rất lớn nên đọc theo dòng/stream hoặc dùng streaming parser để tránh load tất cả byte vào RAM.

### Nguyên tắc phải nhớ

**`Resource` không đồng nghĩa `java.io.File`.** Một `ClassPathResource` trong một JAR có thể đọc được qua `getInputStream()` nhưng không có tệp độc lập để `getFile()` trả về. Một `UrlResource` dùng HTTPS có thể đọc qua mạng nhưng cũng không thể chuyển trực tiếp thành local `File`.

Mục tiêu bài: so sánh chính xác constructor, cú pháp location, đặc tính API, khác biệt classpath/JAR và filesystem, giao thức URL, cách dùng `ResourceLoader`, đọc an toàn, kiểm thử và các lỗi điển hình.

## 2. Resource và InputStreamSource: hợp đồng chung

`Resource` mở rộng `InputStreamSource`. Các method chính của API Spring Core:

```java
// Chỉ là tóm lược chữ ký API để học; không định nghĩa lại trong dự án.
public interface Resource extends InputStreamSource {
    boolean exists();
    boolean isReadable();
    boolean isOpen();
    boolean isFile();

    java.net.URL getURL() throws java.io.IOException;
    java.net.URI getURI() throws java.io.IOException;
    java.io.File getFile() throws java.io.IOException;

    java.nio.channels.ReadableByteChannel readableChannel()
            throws java.io.IOException;

    long contentLength() throws java.io.IOException;
    long lastModified() throws java.io.IOException;

    Resource createRelative(String relativePath) throws java.io.IOException;
    String getFilename();
    String getDescription();
}
```

`InputStreamSource` khai báo `InputStream getInputStream() throws IOException`. Một Resource chủ yếu là **descriptor**, không nhất thiết là nội dung đã mở hoặc đã cache. Việc tạo `new ClassPathResource(...)` hay `loader.getResource(...)` không bảo đảm dữ liệu thực sự tồn tại.

| API | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `exists()` | Kiểm tra khả năng resource tồn tại | Có thể cần filesystem I/O hoặc network I/O; tình trạng có thể thay đổi ngay sau khi kiểm tra |
| `isReadable()` | Resource có vẻ đọc được | Không bảo đảm `getInputStream()` sau đó sẽ thành công trong mọi hoàn cảnh |
| `getInputStream()` | Mở luồng để đọc | Đóng bằng try-with-resources |
| `isOpen()` | Resource đang biểu diễn stream đã mở | Phần lớn file/classpath/URL resources là descriptor có thể mở lại; `InputStreamResource` khác |
| `isFile()` | Có thể xử lý như tệp vật lý theo implementation | Không đồng nhất với `exists()` |
| `getFile()` | Lấy `File` nếu có file thật | Có thể ném `FileNotFoundException`, đặc biệt JAR/HTTP |
| `getURL()` / `getURI()` | Lấy URL/URI của resource | Không hứa rằng protocol là `file:` |
| `contentLength()` | Lấy chiều dài byte | Có thể phát sinh I/O, tốn chi phí hoặc không xác định được |
| `lastModified()` | Lấy mốc sửa cuối khi được hỗ trợ | Có thể lỗi hoặc không đáng tin với resource từ mạng |
| `getFilename()` | Phần tên cuối của đường dẫn nếu có | Có thể null, không chứa toàn bộ đường dẫn |
| `getDescription()` | Mô tả dành cho debug | Có thể chứa URL/path nhạy cảm; không log mặc định |
| `createRelative(...)` | Tạo resource tương đối theo semantics implementation | Với `FileSystemResource` có lưu ý lịch sử; xem mục 6 |

### Readable không phải mở file sẵn

```text
Resource descriptor (mô tả vị trí)
        |
        +-- exists() / isReadable(): kiểm tra trạng thái
        |
        +-- getInputStream(): mở InputStream
                                 |
                                 +-- read bytes
                                 +-- close stream
```

Đừng gọi `resource.exists()` rồi coi dữ liệu vĩnh viễn sẵn sàng: tệp có thể bị xóa/đổi quyền hoặc server có thể mất mạng trước khi mở stream (**time-of-check/time-of-use**). Khi xử lý thực, vẫn phải bắt/khai báo `IOException`.

### Một descriptor có thể được đọc nhiều lần, nhưng không phải mọi implementation

`ClassPathResource`, `FileSystemResource` và `UrlResource` thường cho phép gọi `getInputStream()` nhiều lần, mỗi lần một stream mới. `InputStreamResource` là trường hợp đặc biệt mô tả **một stream đã mở**, `isOpen() == true`; nhiều lần đọc có thể lỗi hoặc stream đã cạn. Nếu dữ liệu nhỏ đã ở RAM và cần đọc nhiều lần, `ByteArrayResource` thường thích hợp hơn.

**Không giữ một InputStream mở như singleton chung** rồi cho nhiều thread đọc mà không có đồng bộ hóa. Giữ `Resource` descriptor và mở stream theo từng lần sử dụng thường an toàn hơn; với remote URL cần thiết kế cache, timeout và retry riêng.

## 3. ClassPathResource — dữ liệu đi cùng classpath/JAR

`ClassPathResource` tìm dữ liệu qua Java class loader hoặc một `Class<?>` được truyền vào constructor. Đường dẫn thông dụng:

```java
Resource logo = new ClassPathResource("images/logo.png");
Resource config = new ClassPathResource("config/client.properties");
```

Khi project Maven có:

```text
src/main/resources/
  images/logo.png
  config/client.properties
```

Maven mặc định copy contents của `src/main/resources` vào `target/classes`. Sau khi đóng gói, những nội dung ấy có thể nằm **bên trong JAR**, không còn là các file độc lập trên đĩa.

### Cách chỉ định path

Có ba nhóm constructor thường gặp:

```java
new ClassPathResource("data/message.txt");
new ClassPathResource("data/message.txt", classLoader);
new ClassPathResource("message.txt", MyFeature.class);
```

Các dòng là **fragments**: `classLoader` và `MyFeature` phải có giá trị cụ thể. Với constructor dựa trên `ClassLoader`, path classpath có thể được diễn giải tính từ classpath root; với constructor dựa trên `Class<?>`, path tương đối có thể được diễn giải theo package của class và path có dấu `/` đầu sẽ là absolute classpath path theo semantics `Class.getResource(...)`.

Ví dụ:
- `new ClassPathResource("data/message.txt")` thường tìm từ classpath root.
- `new ClassPathResource("example.txt", MyFeature.class)` tìm tương đối package chứa `MyFeature`.
- `new ClassPathResource("/data/message.txt", MyFeature.class)` tìm từ classpath root.

**Đừng nhầm với đường dẫn filesystem:** `"data/message.txt"` không phải đường dẫn tương đối thư mục thực thi của shell; nó là đường dẫn trong classpath. Thư mục `src/main/resources` là cấu trúc **lúc build**, không phải tiền tố cần đưa vào path runtime.

### Đọc từ JAR: dùng getInputStream, không ép getFile

```java
ClassPathResource resource =
        new ClassPathResource("data/message.txt");

try (InputStream input = resource.getInputStream()) {
    byte[] data = input.readAllBytes();
}
```

Đây là fragment có imports phù hợp. Nó vẫn làm việc khi `data/message.txt` được đóng gói trong JAR miễn resource được đưa lên classpath.

Ngược lại:

```java
File file = resource.getFile(); // KHÔNG portable khi resource nằm trong JAR.
```

`getFile()` có thể chạy trong IDE vì classpath hiện là thư mục `target/classes`; nhưng khi deploy một JAR, sẽ không có `File` độc lập trỏ tới entry trong JAR. Đó là lỗi “chạy ở local nhưng fail khi đóng gói” rất phổ biến.

Nếu thư viện bên ngoài **chỉ nhận File/Path**, hãy cân nhắc:
1. Tìm API của thư viện có nhận `InputStream`/`URL`/`Reader` không.
2. Nếu không có, **copy** resource qua `Files.copy(input, tempPath, ...)` ra một file tạm với quyền và lifecycle rõ, sau đó cung cấp `Path`; cleanup đúng cách và không chứa secret không kiểm soát.
3. Không dùng `Paths.get(resource.getURI())` như giải pháp tổng quát: `jar:file:...!/entry` không phải đường dẫn filesystem thường và có thể yêu cầu ZipFileSystem riêng, phức tạp về lifecycle.

### Trùng tên trên classpath

Nếu nhiều JAR cùng có `config/settings.xml`, `ClassPathResource` thông thường dựa vào một class loader có thể lấy **một** match, không có cơ chế bảo đảm nó là bản “đúng nhất” theo business logic. Đừng đặt configuration quan trọng ở một path dễ collision; namespace resource theo package/module và viết integration test với packaged artifact.

Nếu muốn quét **nhiều** resources trên classpath, xem `classpath*:`/`ResourcePatternResolver` ở mục 7 — đó là bài toán khác với một `ClassPathResource` đơn lẻ.

## 4. FileSystemResource — làm việc với đường dẫn của hệ điều hành

`FileSystemResource` đại diện cho file hoặc directory trên filesystem. Có thể tạo bằng `String`, `java.io.File` hoặc `java.nio.file.Path`:

```java
Resource fromFile = new FileSystemResource(new File("/opt/app/data/report.txt"));
Resource fromPath = new FileSystemResource(Path.of("/opt/app/data/report.txt"));
Resource fromString = new FileSystemResource("data/report.txt");
```

Các dòng là ví dụ đường dẫn theo kiểu hệ Unix, **không phải lệnh chạy đa nền tảng**. Trên Windows nên dùng `Path.of(...)`, `resolve(...)` và không hardcode dấu phân cách.

### Absolute và relative path

- Với `FileSystemResource` dùng độc lập, path tương đối thường được diễn giải dựa trên **working directory** của JVM.
- Path absolute chỉ đến vị trí tuyệt đối tương ứng trên filesystem.
- Working directory trong IDE, Docker, systemd, CI và khi chạy từ Maven có thể khác; đừng giả định file nằm ở `src/main/resources` nếu lấy bằng `FileSystemResource("...")`.
- Dùng `Path.of(baseDirectory, filename).toAbsolutePath().normalize()` hoặc `base.resolve(...)` để xây đường dẫn rõ ràng.
- Container/Kubernetes có thể mount config vào thư mục filesystem, thích hợp cho `FileSystemResource` hoặc `UrlResource` với `file:` URL.

Ví dụ:

```java
Path configDirectory = Path.of(System.getProperty("user.home"), "app-config");
Path configFile = configDirectory.resolve("settings.json");
Resource resource = new FileSystemResource(configFile);
```

Đây là fragment không tạo file và cần imports `Path`, `Resource`, `FileSystemResource`. Code production phải kiểm quyền truy cập, tồn tại và xử lý lỗi I/O.

### getFile có thể dùng nếu thật sự cần

Một `FileSystemResource` thông thường có `File` thật:

```java
File file = new FileSystemResource(configFile).getFile();
```

Tuy nhiên `getFile()` trả một handle, **không chứng minh file tồn tại hay đọc được**. Kiểm tra đường dẫn và xử lý `IOException` khi mở.

### Viết dữ liệu thì sao?

Đọc tài nguyên qua `Resource`/`getInputStream()` là contract chung. Một số implementation còn thực thi `WritableResource`, cung cấp `getOutputStream()`. `FileSystemResource` có thể hỗ trợ viết khi filesystem cho phép, nhưng **đừng cast mọi Resource thành WritableResource** hoặc giả định HTTP/classpath resource nào cũng ghi được.

Trong ứng dụng mới sử dụng filesystem, `java.nio.file.Files` thường cung cấp API ghi/atomic move/permission rõ hơn. Với cấu hình cần update an toàn, thiết kế file locking, permissions, đồng bộ reader và atomic replace riêng; đừng ghi trực tiếp một config vừa đang được nhiều consumers đọc.

## 5. UrlResource — URL file, HTTPS và những protocol JVM hỗ trợ

`UrlResource` bọc Java `URL` hoặc `URI`. Ví dụ:

```java
Resource localUrl = new UrlResource(Path.of("/data/notes.txt").toUri());
Resource remote = new UrlResource("https://example.com/assets/schema.json");
```

Đây là fragments; path filesystem chỉ là ví dụ. `UrlResource` với `file:` URL có thể dẫn tới file cục bộ; với `https:`, đọc dữ liệu có thể khởi tạo **network request**.

Những prefix thông dụng:

| Prefix | Ví dụ | Cách hiểu |
| --- | --- | --- |
| `file:` | `file:///opt/app/config.json` | Tài nguyên được ánh xạ tới filesystem URL |
| `https:` | `https://example.com/config.json` | Tài nguyên truy cập qua HTTPS |
| `http:` | `http://localhost:8080/data.txt` | HTTP không mã hóa, chỉ dùng nơi phù hợp |
| `ftp:` | `ftp://server/path/file` | Phụ thuộc URL handler/JDK và chính sách môi trường |
| `classpath:` | `classpath:demo/message.txt` | **Không** phải URL chuẩn của JVM; xử lý bởi `ResourceLoader` để tạo `ClassPathResource` |

`UrlResource` là wrapper cho URL, không tự biến mọi giao thức thành filesystem. HTTP resource có thể **không** hỗ trợ `getFile()`, hoặc `lastModified()/contentLength()` không phải thông tin đáng tin theo mạng. Có thể thử `exists()` nhưng tùy protocol nó có thể cần round-trip/HEAD request hoặc kết luận khác khi endpoint chặn HEAD, yêu cầu authentication, redirect hay trả status khác.

### Đừng tải URL không tin cậy tùy tiện

Không làm:

```java
Resource resource = new UrlResource(userSuppliedUrl);
String content = new String(resource.getInputStream().readAllBytes(),
        StandardCharsets.UTF_8);
```

Đây là **anti-pattern minh họa** với `userSuppliedUrl` chưa định nghĩa. Nó có thể dẫn đến **SSRF** hoặc truy cập internal metadata endpoints nếu client chấp nhận scheme/host không giới hạn. Ngoài ra, `readAllBytes()` với remote resource không giới hạn kích thước có thể gây áp lực bộ nhớ/DoS.

Nếu thật sự cần tải remote:
1. Prefer một client HTTP được cấu hình connect/read timeout, redirect policy, TLS verification và max response size.
2. Chỉ cho các schemes/hosts/ports đã duyệt; kiểm tra redirect và DNS/IP resolution theo threat model.
3. Không đưa URL credentials, token, signed query string hay response body nhạy cảm vào log.
4. Thiết lập retry có backoff/circuit breaker nếu hệ thống dựa vào availability của remote source.
5. Hạn chế bytes đọc, cân nhắc stream processing.
6. Nếu tải tài nguyên cần authentication, sử dụng HTTP client abstraction có quản lý credentials, không dồn user-info vào URL.
7. Ghi event ID/status/host alias đã allowlist thay vì log raw URL.
8. Không thực hiện network I/O bất ngờ trong Spring BeanFactoryPostProcessor hoặc constructor bean hạ tầng sớm.

`UrlResource` thích hợp để **biểu diễn** URL resource và tích hợp với API Spring. Với production HTTP có authentication/timeouts/retries, một client HTTP chuyên dụng thường dễ quản trị hơn.

## 6. So sánh ba implementation theo nhu cầu

| Tiêu chí | ClassPathResource | FileSystemResource | UrlResource |
| --- | --- | --- | --- |
| Nguồn | JVM classpath (directory/JAR) | Filesystem cục bộ | URL/URI (file, HTTPS, ...) |
| Lấy qua constructor | `new ClassPathResource("data/a.txt")` | `new FileSystemResource(Path.of("/data/a.txt"))` | `new UrlResource("https://...")` |
| Đóng gói vào JAR | Có thể đọc resource trong JAR | Không tìm entry JAR như filesystem file | Có thể wrap URL tương ứng, không mặc định classpath |
| `getInputStream()` | Có | Có | Có nếu protocol/đích hỗ trợ |
| `getFile()` | Chỉ khi thực sự nằm trên filesystem | Có File handle | Với `file:` có thể, HTTPS thường không |
| Phụ thuộc working directory | Không, path dựa classpath | Có nếu truyền relative path | URL absolute hoặc relative theo URI rules |
| I/O mạng | Không với classpath local/JAR thông thường | Không với local path thông thường | Có nếu scheme remote |
| Khi nên dùng | Templates, static data, default config đóng gói | File mount, file người dùng, export/import | File URL và remote URL được kiểm soát |
| Lưu ý bảo mật | Trùng tên, resource secrets đóng gói sai | Traversal, symlink, permission | SSRF, network timeout, redirect, credential URL |

### Các trường hợp hay chọn sai

- Muốn đọc `src/main/resources/messages.txt` sau khi package: dùng `ClassPathResource("messages.txt")`, không `FileSystemResource("src/main/resources/messages.txt")`.
- Muốn đọc file operator mount tại `/etc/myapp/secrets.json`: dùng `FileSystemResource(Path.of(...))` hoặc `UrlResource(fileURI)`; không trông chờ file đó ở classpath.
- Muốn Spring quản lý URL remote: dùng `UrlResource` nhưng cần kiểm soát mạng/security.
- Muốn nhận nhiều file qua wildcard `classpath*:config/*.xml`: dùng `ResourcePatternResolver` chứ không tạo `ClassPathResource` chứa dấu `*` như file name.
- Muốn thao tác tài nguyên rất lớn: dùng streaming/reader, đừng `readAllBytes()` không giới hạn.

## 7. ResourceLoader và ResourcePatternResolver

### 7.1. ResourceLoader.getResource(location)

Spring `ResourceLoader` có:

```java
Resource getResource(String location);
ClassLoader getClassLoader();
```

Đây là chữ ký API rút gọn. Mọi Spring `ApplicationContext` đều là `ResourceLoader`. Với `DefaultResourceLoader`:

```java
ResourceLoader loader = new DefaultResourceLoader();

Resource cp = loader.getResource("classpath:demo/message.txt");
Resource url = loader.getResource("file:///tmp/example.txt");
Resource web = loader.getResource("https://example.com/data.json");
```

Đây là fragments (không tải `web` trong ví dụ), cần imports tương ứng. `classpath:` buộc tạo loại classpath; `file:`/`https:` là URL-based. Khi không có prefix, loại resource phụ thuộc **implementation ResourceLoader/loại ApplicationContext**.

| Location string | Thông thường trả về | Mục đích |
| --- | --- | --- |
| `classpath:demo/message.txt` | ClassPathResource | Resource trong classpath |
| `file:///tmp/example.txt` | UrlResource | File thông qua URL |
| `https://example.com/data.json` | UrlResource | Remote URL |
| `demo/message.txt` (không prefix) | Phụ thuộc loader/context | Không nên giả định đồng nhất mọi context |

Ví dụ `ClassPathXmlApplicationContext` xử lý location không prefix theo classpath, trong khi `FileSystemXmlApplicationContext` diễn giải theo filesystem, và web context có thể theo servlet context. Để code portable giữa nhiều loại context, **nêu prefix tường minh**.

### 7.2. classpath: và classpath*: không giống nhau

- `classpath:config/app.xml`: tìm một resource trên classpath theo `ResourceLoader`.
- `classpath*:config/app.xml`: tìm **tất cả** những resources phù hợp qua cơ chế `ResourcePatternResolver`/classpath scanning khi API hỗ trợ.
- `classpath*:META-INF/*.xml`: quét pattern Ant-style trên nhiều classpath roots.

Ví dụ programmatic:

```java
ResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver();
Resource[] resources =
        resolver.getResources("classpath*:META-INF/spring/*.xml");
```

Đây là fragment cần imports `ResourcePatternResolver` và `PathMatchingResourcePatternResolver`. Wildcard resolution ở trong JAR phụ thuộc class loader/định dạng archive và đôi khi có giới hạn portable. Không trộn `getResources(...)` (mảng resources) với `ResourceLoader.getResource(...)` (một descriptor).

### 7.3. Wildcard caveat trong JAR

Spring Reference khuyến nghị kiểm thử classpath wildcard patterns trên môi trường deploy thực (nested JAR, application servers, custom class loaders). Các root-level pattern như `classpath*:*.xml` có thể không tìm hết file nằm tại root JAR theo một số class loader. Ưu tiên các package/directory prefixes rõ, ví dụ `classpath*:com/example/config/**/*.xml`, và test với packaged artifact.

## 8. createRelative và đường dẫn: điểm dễ sai

`Resource.createRelative(String relativePath)` tạo descriptor dựa trên resource hiện tại. Nhưng **cách kết hợp path không giống hệt nhau giữa mọi implementation**, nên không viết logic quan trọng dựa vào trực giác.

```java
Resource base = new ClassPathResource("templates/email/main.txt");
Resource sibling = base.createRelative("footer.txt");
```

Với classpath semantics thông thường, sibling trỏ tới tài nguyên cùng vùng đường dẫn với base. Đây là fragment, resource `footer.txt` phải thực sự có trong classpath mới đọc được.

### FileSystemResource có lịch sử xử lý leading slash

Spring Reference cảnh báo `FileSystemXmlApplicationContext` có một semantics path đặc biệt để tương thích ngược: một location có dấu `/` đầu vẫn có thể bị coi là tương đối với context trong một số con đường resource loading. **Đừng suy rằng mọi String path bắt đầu với `/` khi đi qua FileSystemXmlApplicationContext đều đại diện OS absolute path**.

Để bắt buộc filesystem absolute location qua ResourceLoader trong bối cảnh đó, dùng `file:` URL hợp lệ:

```java
Resource file = context.getResource("file:///opt/app/settings.xml");
```

Đây là fragment; `context` phải là `ApplicationContext`, path chỉ là ví dụ Unix. Nếu tự tạo `new FileSystemResource(Path.of(...))` từ Path absolute, semantics rõ hơn. `PathResource` cũng là lựa chọn hợp lý khi muốn toàn bộ createRelative behavior theo `java.nio.file.Path`.

### Không tạo path bằng chuỗi nối thẳng

```java
Path candidate = baseDirectory.resolve(filename).normalize();
```

Fragment trên chưa phải sandbox đầy đủ. Nếu `filename` đến từ người dùng, phải kiểm tra path traversal (ví dụ `../`), chuẩn hóa base và candidate, kiểm access policy, symlink/realpath race, permissions và file type. `normalize()` một mình **không** giải quyết symlink traversal hay những thay đổi filesystem đồng thời.

## 9. Demo Java 17+ và Spring Core: ba loại resource cùng một cách đọc

Chương trình sau có thể chạy trên Windows, Linux, macOS khi có **JDK 17+** và **Maven 3.9+**. Để không phụ thuộc mạng hay đường dẫn tuyệt đối theo OS, demo tạo một thư mục tạm của process và một local file, rồi bọc nó thành `FileSystemResource` và `UrlResource` có scheme `file:`.

Dữ liệu classpath nằm ở `src/main/resources/demo-resource/message.txt`. Trong bài chạy Maven `exec:java`, resource này được copy vào classpath khi `process-resources` hoàn tất trong phase `compile`.

### 9.1. Cấu trúc project

```text
spring-resource-demo/
├── pom.xml
└── src/main/
    ├── java/edu/example/resources/
    │   └── Main.java
    └── resources/demo-resource/
        └── message.txt
```

**pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>spring-resource-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
            <version>6.2.18</version>
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
                    <mainClass>edu.example.resources.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**src/main/resources/demo-resource/message.txt**

```text
hello-classpath
```

Đảm bảo file trên có đúng nội dung dòng `hello-classpath` và không thêm các ký tự nội dung tùy ý. Chương trình dùng `String.strip()` để loại dấu xuống dòng ở đầu/cuối nên hoạt động dù editor lưu một line break cuối file.

**src/main/java/edu/example/resources/Main.java**

```java
package edu.example.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;

public final class Main {

    public static void main(String[] args) throws Exception {
        Resource classpath =
                new ClassPathResource("demo-resource/message.txt");

        Path temporaryDirectory =
                Files.createTempDirectory("spring-resource-demo-");
        Path temporaryFile = temporaryDirectory.resolve("local.txt");

        try {
            Files.writeString(
                    temporaryFile, "hello-file\n", StandardCharsets.UTF_8);

            Resource filesystem = new FileSystemResource(temporaryFile);
            Resource fileUrl = new UrlResource(temporaryFile.toUri());

            ResourceLoader loader = new DefaultResourceLoader();
            Resource loadedClasspath =
                    loader.getResource("classpath:demo-resource/message.txt");
            Resource loadedFileUrl =
                    loader.getResource(temporaryFile.toUri().toString());

            System.out.println("classpath exists: " + classpath.exists());
            System.out.println("classpath: " + readText(classpath));
            System.out.println("filesystem exists: " + filesystem.exists());
            System.out.println("filesystem: " + readText(filesystem));
            System.out.println("file URL: " + readText(fileUrl));

            System.out.println("classpath filename: "
                    + classpath.getFilename());
            System.out.println("filesystem filename: "
                    + filesystem.getFilename());
            System.out.println("URL filename: " + fileUrl.getFilename());

            System.out.println("loader classpath: "
                    + readText(loadedClasspath));
            System.out.println("loader file URL: "
                    + readText(loadedFileUrl));

            Resource missing =
                    new ClassPathResource("demo-resource/absent.txt");
            System.out.println("missing exists: " + missing.exists());
        } finally {
            Files.deleteIfExists(temporaryFile);
            Files.deleteIfExists(temporaryDirectory);
        }
    }

    private static String readText(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(),
                    StandardCharsets.UTF_8).strip();
        }
    }
}
```

**Chạy:**

```bash
mvn -q compile exec:java
```

**Những dòng `System.out.println` từ `Main` theo đúng thứ tự:**

```text
classpath exists: true
classpath: hello-classpath
filesystem exists: true
filesystem: hello-file
file URL: hello-file
classpath filename: message.txt
filesystem filename: local.txt
URL filename: local.txt
loader classpath: hello-classpath
loader file URL: hello-file
missing exists: false
```

Có thể xuất hiện log do Maven/Spring runtime, nhưng các dòng từ `Main` chính là nội dung trên. Ví dụ không in đường dẫn tạm nên output không phụ thuộc OS hoặc tên tệp tạm ngẫu nhiên.

### 9.2. Giải thích từng bước

1. `new ClassPathResource("demo-resource/message.txt")` tạo descriptor classpath; JVM tìm thấy file đã được Maven copy vào classpath.
2. `Files.createTempDirectory(...)` tạo một thư mục tạm, `Files.writeString(...)` tạo `local.txt` với nội dung cố định. Trong `finally`, chương trình xóa file rồi thư mục.
3. `new FileSystemResource(temporaryFile)` dùng đường dẫn local thực.
4. `new UrlResource(temporaryFile.toUri())` chuyển cùng file sang `file:` URL; phần này **không tải mạng**.
5. `getInputStream()` từ cả ba resources trả các luồng đọc khác nhau nhưng cùng cách tiêu thụ bytes qua `readText(Resource)`.
6. `getFilename()` cho ba resources lấy phần tên cuối, nên output ổn định `message.txt`/`local.txt`.
7. `DefaultResourceLoader.getResource("classpath:...")` trả descriptor classpath; `getResource(fileUri)` trả URL-based descriptor.
8. `new ClassPathResource("demo-resource/absent.txt").exists()` trả false thay vì code phải gọi `getInputStream()` rồi bắt exception để xác định trước.
9. `try-with-resources` đóng mọi InputStream, còn `finally` cleanup file tạm ngay cả nếu đọc ném exception.

### 9.3. Khi đóng gói thành JAR

Nếu ứng dụng chạy từ JAR với dependencies trên classpath, `ClassPathResource.getInputStream()` vẫn là chiến lược nên dùng. Hãy thêm một test đóng gói và chạy thật để bảo đảm classpath resource có mặt. `ClassPathResource.getFile()` có thể fail vì đường dẫn resource nằm bên trong JAR. Đừng suy từ cách Maven `exec:java` đọc `target/classes` rằng mọi deployment giữ file ngoài JAR.

### 9.4. Vì sao demo không dùng UrlResource HTTPS?

Vì output HTTP phụ thuộc mạng/DNS/TLS/redirect và có thể không tái lập giữa các môi trường. Dùng `file:` URL cho phép chứng minh cơ chế `UrlResource` mà không phải liên lạc dịch vụ ngoài. Mẫu HTTPS ở mục 5 chỉ mang tính giải thích; muốn truy cập remote production cần timeout, endpoint allowlist, credential policy và integration test thật.

## 10. @Value Resource injection trong Spring ApplicationContext

Ngoài tạo Resource bằng `new`, Spring có thể inject tài nguyên qua `@Value` nếu container có conversion/editor cần thiết:

```java
@Component
public final class TemplateReader {
    private final Resource template;

    public TemplateReader(
            @Value("classpath:templates/welcome.txt") Resource template) {
        this.template = template;
    }

    public String read() throws IOException {
        try (InputStream in = template.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
```

Đây là **fragment** cần imports `Component`, `Value`, `Resource`, `InputStream`, `IOException`, `StandardCharsets`, resource file tồn tại và component registration. Không tự tạo `new TemplateReader(...)` rồi kỳ vọng Spring giải location; annotation chỉ có tác dụng với Spring-managed bean.

Trong một `@Bean` factory method cũng có thể khai báo:

```java
@Bean
TemplateService templateService(
        @Value("classpath:templates/welcome.txt") Resource resource) {
    return new TemplateService(resource);
}
```

Đây là fragment cần `TemplateService` và imports. Nó biểu diễn dependency **Resource descriptor**, không buộc convert mọi resource thành `File`.

Nếu muốn location có cấu hình ngoài:

```java
@Value("${app.template.location:classpath:templates/welcome.txt}")
private Resource template;
```

Với placeholder resolver phù hợp, property có thể chứa `classpath:...` hoặc `file:...` tùy policy. **Không** cho arbitrary untrusted URL/path làm giá trị nếu app sau đó tự động đọc resource: nó có thể mở local secrets hoặc thực hiện SSRF.

## 11. Kiểm thử: unit, integration và packaged deployment

### 11.1. Test classpath resource

```java
Resource resource =
        new ClassPathResource("demo-resource/message.txt");

if (!resource.exists()) {
    throw new AssertionError("Missing packaged resource");
}
try (InputStream in = resource.getInputStream()) {
    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    if (!"hello-classpath".equals(text.strip())) {
        throw new AssertionError("Unexpected classpath content");
    }
}
```

Fragment này dùng source/resource từ project ở mục 9 và imports tương ứng. Test nên chạy cả trong IDE/Maven và ít nhất một lần trong packaged distribution, vì classpath/JAR là điểm dễ phát sinh lỗi.

### 11.2. Test filesystem với temp directory

Dùng JUnit `@TempDir` (nếu dự án có JUnit) hoặc `Files.createTempDirectory` để cấp path riêng, không ghi vào thư mục source/build chung và không hardcode `/tmp` trên Windows. Test cả tệp tồn tại và tệp không tồn tại, quyền đọc và cleanup.

### 11.3. Test UrlResource không phụ thuộc Internet

Dùng `file:` URL tới file tạm cho unit test; dùng local fake HTTP server cho integration test URL remote, giả lập 200, 404, redirect, timeout, chunked response, giới hạn kích thước và error handling. **Không** dùng một URL công cộng làm điều kiện để CI pass.

### 11.4. Không kiểm exact path description trên mọi OS

`getDescription()`, `getURL()` và absolute path có thể chứa drive letters, separator, temp directories và encoding khác nhau. Test loại resource, filename, content hoặc protocol theo contract, không hardcode toàn bộ absolute URI string để assert cross-platform.

### 11.5. Test InputStream đã đóng

Nếu tạo resource custom, bảo đảm InputStream được đóng cả khi parser ném exception. Với một resource chỉ đọc một lần, `getInputStream()` mới không được tự động giả định. Tránh mock đơn giản trả lại cùng InputStream đã consumed cho tất cả lời gọi, khiến test che khuất lifecycle thực.

## 12. Xử lý lỗi, performance và bảo mật

### 12.1. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `FileNotFoundException` với ClassPathResource | File không được build vào classpath, path sai | Đặt trong `src/main/resources` và dùng đường dẫn từ classpath root |
| Code chạy IDE, fail sau JAR | Gọi `getFile()` trên entry trong JAR | Đổi sang `getInputStream()` hoặc copy ra temp |
| FileSystemResource không tìm thấy file | Working directory khác dự kiến | Dùng absolute Path từ base config rõ |
| `getResource("x")` khác giữa contexts | Không có prefix, semantics phụ thuộc loader | Dùng `classpath:` / `file:` tường minh |
| `getResource("classpath*:...")` không như ý | Chọn API trả một Resource thay vì nhiều | Dùng `ResourcePatternResolver.getResources(...)` |
| `getFile()` trên HTTPS resource lỗi | Không có local file tương ứng | Dùng stream hoặc HTTP client |
| Đọc URL HTTPS lúc startup bị treo | Không timeout/endpoint lỗi | HTTP client với timeout/retry/circuit breaker |
| `exists()` true nhưng đọc thất bại | TOCTOU/permission/network thay đổi | Bắt IOException ở read, không dựa check duy nhất |
| UTF-8 bị lỗi ký tự | Dùng default charset | Chỉ rõ `StandardCharsets.UTF_8` hoặc charset thực |
| Stream/file descriptor leak | Quên đóng `InputStream` | try-with-resources |
| `readAllBytes()` gây OOM | Resource quá lớn hoặc remote không giới hạn | Stream, giới hạn kích thước |
| `contentLength()` làm request chậm | Metadata lookup qua network | Tránh trong hot path/thiết lập cache |
| `getDescription()` làm lộ credential URL | Log raw resource description | Log logical resource ID, không URI có secret |
| Path user chứa `../` | Directory traversal | Validate allowed base, realpath/symlink |
| URL user trỏ tới internal endpoint | SSRF | Scheme/host/IP allowlist và redirect control |
| Resource trong JAR trùng path | Classpath collision | Namespace theo module và test packaging |
| Đọc lại InputStreamResource bị lỗi | Stream single-use | Dùng ByteArrayResource hoặc file/classpath descriptor |

### 12.2. Quy tắc đọc an toàn

1. Không coi `exists()` như kiểm tra quyền đọc vĩnh viễn.
2. Dùng `getInputStream()` và đóng stream đúng lifecycle.
3. Dùng streaming cho file lớn; chỉ dùng `readAllBytes()` khi kích thước đã được kiểm soát.
4. Không lộ raw filesystem path/URL có credential trong log/error.
5. Không nhận arbitrary user-controlled resource location rồi mở trực tiếp.
6. Kiểm tra content type/format thực, không dựa riêng `getFilename()` hay suffix để tin một file an toàn.
7. Với file upload/export, xây base path an toàn, xét traversal/symlink và atomic file operations khi cần.
8. Với HTTP URL, kiểm scheme/host/port/redirect/IP và thiết lập timeout/size limits.
9. Không gắn I/O nặng vào constructor/BeanFactoryPostProcessor trước khi ApplicationContext ready.
10. Khi file chứa secret, không copy nội dung vào exception message hoặc stack logging; resource bytes cũng là dữ liệu nhạy cảm.

### 12.3. Khác biệt text và binary

```java
try (InputStream input = resource.getInputStream()) {
    byte[] bytes = input.readAllBytes();
}
```

Đây là fragment. Với JSON/CSV/text nhỏ, decode bằng charset chỉ định. Với PDF/image/ZIP, không gọi `new String(bytes, UTF_8)`; dùng parser hoặc binary API chuyên dụng. Không coi file extension đủ để bảo đảm nội dung thực đúng định dạng.

## 13. Câu hỏi tự đánh giá

1. `Resource` nằm trong package nào và kế thừa interface nào?
2. `ClassPathResource` có bắt buộc là một `File` thật không?
3. Vì sao `getFile()` thường hoạt động trong IDE nhưng fail ở packaged JAR?
4. `FileSystemResource` dựa vào yếu tố nào khi path tương đối?
5. `UrlResource("https://...")` có giống filesystem file không?
6. Một `UrlResource` có `file:` URL có cần Internet không?
7. Cách đọc portable nhất cho ba loại Resource là gì?
8. Vì sao phải dùng try-with-resources khi gọi `getInputStream()`?
9. Gọi `exists()` xong có bỏ `IOException` khi mở stream được không?
10. `getResource("my/file")` không prefix có luôn chọn classpath không?
11. `classpath:` khác `classpath*:` ra sao?
12. Muốn lấy nhiều file `META-INF/*.xml` thì dùng API nào?
13. `Resource.isOpen()` có ý nghĩa gì với `InputStreamResource`?
14. Khi URL resource qua mạng, vì sao `contentLength()` có thể tốn chi phí?
15. Nếu một library chỉ nhận `File` nhưng resource trong JAR, có phương án nào?
16. Cần tránh điều gì khi nhận URL từ người dùng?
17. Cần kiểm soát những gì khi ghép user filename với base directory?
18. Với secret file, có nên log `getDescription()` không?
19. Để test UrlResource mà CI không phụ thuộc Internet, dùng gì?
20. Khi cần filesystem Path API thuần, ngoài FileSystemResource có implementation nào?
21. `@Value("classpath:...") Resource` có cần bean thuộc quyền quản lý Spring không?
22. `ResourceLoader.getResource` trả descriptor hay nội dung byte luôn?
23. Resource từ classpath có thể có tên file trùng nhau ở nhiều JAR không?
24. Khi file có thể lớn hàng GB, nên dùng `readAllBytes()` không?
25. Khi nào nên chọn HTTP client chuyên dụng thay vì đọc `UrlResource` trực tiếp?

**Đáp án:** (1) `org.springframework.core.io`, kế thừa `InputStreamSource`. (2) Không, có thể ở JAR. (3) IDE dùng directory, JAR dùng archive entry. (4) JVM working directory trừ context caveats. (5) Không. (6) Không nếu URL là file local. (7) `getInputStream()`. (8) Tránh leak. (9) Không, TOCTOU/network/permissions. (10) Không, phụ thuộc loader/context. (11) Một location vs quét nhiều bằng resolver chuyên dụng. (12) `ResourcePatternResolver.getResources(...)`. (13) Đại diện stream đã mở, có thể không reusable. (14) I/O qua network. (15) Dùng stream API hoặc copy temp file rồi cleanup. (16) SSRF, redirect và scheme/host không duyệt. (17) Traversal, symlink, permissions. (18) Không mặc định, có thể lộ thông tin. (19) Local `file:` URL hoặc fake HTTP server. (20) `PathResource`. (21) Có, nếu muốn container inject/convert. (22) Descriptor. (23) Có. (24) Không, cần stream/giới hạn. (25) Khi cần auth/timeout/retry/redirect/size policy và HTTP semantics rõ.

## 14. Checklist áp dụng vào dự án

- [ ] Chọn `ClassPathResource` cho file đóng gói trong classpath/JAR.
- [ ] Chọn `FileSystemResource` cho filesystem paths thật, nhất là operator-mounted files.
- [ ] Chọn `UrlResource` cho URL hợp lệ; hiểu file URL và HTTPS khác nhau.
- [ ] Lấy resource qua `ResourceLoader` với prefix tường minh khi code cần portable.
- [ ] Không gọi `getFile()` trên classpath entry trong JAR/HTTPS resource.
- [ ] Dùng `getInputStream()` và luôn đóng stream.
- [ ] File nhỏ được decode bằng charset cố định; file lớn streaming.
- [ ] Dùng `ResourcePatternResolver` cho patterns nhiều tài nguyên, không nhầm `classpath*:` với `classpath:`.
- [ ] Khi cần một Path, cân nhắc `PathResource` hoặc `FileSystemResource(Path)`.
- [ ] Biết working directory và deployment-specific filesystem mounts.
- [ ] Kiểm tra `exists()`/`isReadable()` nhưng vẫn xử lý IOException khi đọc.
- [ ] Không giả định mọi Resource có filename, content length hay lastModified đáng tin.
- [ ] Có packaged-JAR integration test để tránh classpath getFile bug.
- [ ] Có test URL remote trong local fake server, không phụ thuộc external site.
- [ ] Không cho untrusted URL/path chạm filesystem hoặc internal network tùy ý.
- [ ] Không log path/URI chứa secret và không chèn raw resource bytes vào exception.
- [ ] Xử lý path traversal, symlink và permission theo threat model.
- [ ] Đừng để network I/O trong bean bootstrap gây startup treo.
- [ ] Phân biệt read-only `Resource` với `WritableResource`.
- [ ] Đóng/cân nhắc cleanup tệp tạm nếu buộc extract classpath resource ra file.
- [ ] Biến resource location thành cấu hình được duyệt, không hardcode đường dẫn khác OS.
- [ ] Có test cho missing resource, permission denied, invalid charset/format.
- [ ] Sử dụng đúng abstraction khi thư viện cần byte stream thay vì file path.

## 15. Tài liệu chính thức

- Spring Framework Reference — Resources, built-in implementations, ResourceLoader, wildcard và filesystem caveats: https://docs.spring.io/spring-framework/reference/core/resources.html
- Spring Framework 6.2 — Resource Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/Resource.html
- Spring Framework 6.2 — ClassPathResource Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/ClassPathResource.html
- Spring Framework 6.2 — FileSystemResource Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/FileSystemResource.html
- Spring Framework 6.2 — UrlResource Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/UrlResource.html
- Spring Framework 6.2 — ResourceLoader Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/ResourceLoader.html
- Spring Framework 6.2 — PathMatchingResourcePatternResolver Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/support/PathMatchingResourcePatternResolver.html
- Spring Framework 6.2 — PathResource Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/PathResource.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-003">ResourceLoader và ý nghĩa các tiền tố đường dẫn</a>

<details>
<summary>Xem chi tiết</summary>

# ResourceLoader và ý nghĩa các tiền tố đường dẫn trong Spring Framework

## 1. Mục tiêu

Spring Framework cung cấp abstraction `Resource` để đọc dữ liệu từ classpath, hệ thống tệp hoặc URL bằng một API thống nhất. `ResourceLoader` chịu trách nhiệm **diễn giải chuỗi location**, dựa vào tiền tố như `classpath:`, `file:`, `https:` hay vào mặc định của loader khi không có tiền tố. `ResourcePatternResolver` mở rộng việc này để tìm **nhiều** resources bằng `classpath*:` và wildcard patterns.

Trong nhóm **Tài nguyên**, bài này giúp bạn:

- Biết chính xác `ResourceLoader.getResource(String)` trả một handle, không phải nội dung file.
- Phân biệt `classpath:`, `file:`, `http:`/`https:`, `classpath*:` và path không có scheme.
- Hiểu vì sao cùng một relative path có ý nghĩa khác nhau giữa `DefaultResourceLoader`, filesystem context và servlet context.
- Biết đọc bằng `getInputStream()` để chạy tốt cả trong IDE và JAR, thay vì phụ thuộc `getFile()`.
- Inject `ResourceLoader` hoặc `ResourcePatternResolver` vào Spring beans.
- Tạo ví dụ Java 17 + Spring Framework 6.2.9 có file classpath, file hệ thống tệp, URL handle và wildcard matching.
- Nhận diện, xử lý lỗi missing resources, path escaping, packaging JAR, concurrency và các rủi ro từ URL không tin cậy.

## 2. Resource và ResourceLoader

### Resource là handle, không phải file chắc chắn tồn tại

`org.springframework.core.io.Resource` kế thừa `InputStreamSource` và có các API:

~~~java
boolean exists();
boolean isReadable();
InputStream getInputStream() throws IOException;
URL getURL() throws IOException;
File getFile() throws IOException;
String getDescription();
String getFilename();
Resource createRelative(String path) throws IOException;
~~~

Đây là các chữ ký **rút gọn** để giải thích, không phải toàn bộ interface.

Một `Resource` mô tả nơi chứa dữ liệu và phương thức truy cập; nó không bắt buộc tương ứng với một filesystem `File`, càng không bảo đảm tồn tại.

~~~java
Resource resource =
        loader.getResource("classpath:unavailable-file.txt");

System.out.println(resource.exists());
~~~

Đây là snippet trong method có biến `ResourceLoader loader`. Lời gọi `getResource` thường trả **Resource descriptor** ngay cả khi file không tồn tại. Đến `exists()` hoặc `getInputStream()` mới đánh giá thực trạng nguồn. Khi thực sự đọc, phải chuẩn bị xử lý `IOException`.

### API ResourceLoader

~~~java
public interface ResourceLoader {
    String CLASSPATH_URL_PREFIX = "classpath:";
    Resource getResource(String location);
    ClassLoader getClassLoader();
}
~~~

Đây là interface **giản lược theo contract**. `getResource(location)` ánh xạ một chuỗi location thành **một `Resource` handle**. Nó **không** tự mở stream, parse JSON hay tải toàn bộ file vào memory.

Đọc text UTF-8:

~~~java
Resource resource =
        loader.getResource("classpath:config/notice.txt");

try (InputStream input = resource.getInputStream()) {
    String text = new String(
            input.readAllBytes(), StandardCharsets.UTF_8);
    System.out.println(text);
}
~~~

Đoạn này cần import `InputStream`, `StandardCharsets` và `Resource`, cùng biến `loader`. Với resource lớn, dùng buffered streaming/chunked processing thay cho `readAllBytes()`.

### Resource implementations

| Implementation | Nơi sử dụng | Lưu ý |
| --- | --- | --- |
| `ClassPathResource` | File trong runtime classpath hoặc JAR | `getInputStream()` portable; `getFile()` có thể lỗi khi resource trong JAR |
| `FileSystemResource` | Filesystem `Path` hoặc `File` | Có thể tạo trực tiếp từ `Path` |
| `UrlResource` | URL như `https:` hoặc `file:` | Remote URL có thể cần network và timeout khi đọc |
| `ByteArrayResource` | Bytes trong memory | Hữu ích khi unit test |
| `InputStreamResource` | InputStream có sẵn | Có thể là one-shot source; không mặc định đọc hai lần |
| `ServletContextResource` | Tài nguyên relative tới servlet context | Chỉ có ý nghĩa trong môi trường web phù hợp |

Phần quan trọng là **cùng interface `Resource`**, nhưng mỗi implementation có giới hạn khác nhau.

## 3. Tổng quan ý nghĩa các tiền tố đường dẫn

| Location | Ý nghĩa | API |
| --- | --- | --- |
| `classpath:demo/config.txt` | Tìm **một** resource trên classpath | `ResourceLoader.getResource` |
| `classpath:/demo/config.txt` | Classpath path bắt đầu bằng slash; thường được chuẩn hóa | `getResource` |
| `file:///opt/app/config.txt` | URL tới file trên filesystem Unix | `getResource` |
| `file:///C:/app/config.txt` | URL tới file trên Windows | `getResource` |
| `https://example.invalid/a.txt` | Remote HTTPS URL | `getResource`, cẩn trọng khi mở stream |
| `http://example.invalid/a.txt` | Remote HTTP URL | `getResource`, cẩn trọng bảo mật |
| `demo/config.txt` | Không prefix, tùy loader/context | `getResource` với loader đã xác định |
| `/WEB-INF/app.xml` | Có thể relative tới web application root | Web-aware `ResourceLoader` |
| `classpath*:META-INF/spring/*.xml` | Tìm **nhiều** tài nguyên phù hợp | `ResourcePatternResolver.getResources` |
| `classpath*:demo/config.txt` | Tìm mọi bản cùng classpath path | `getResources` |
| `file:/opt/app/templates/*.xml` | Filesystem wildcard | `getResources` |

Cần phân biệt hai khái niệm:

- **Tiền tố nơi lưu trữ:** `classpath:`, `file:`, `https:`.
- **Cơ chế tìm nhiều resources:** `classpath*:` và wildcard patterns (`*`, `**`) của `ResourcePatternResolver`.

Trong **Spring Framework 6.2**, **không dùng** `ResourceLoader.getResource("classpath*:...")` để mong nhận danh sách. API đúng để mở rộng pattern là `ResourcePatternResolver.getResources(...)`.

## 4. classpath: tài nguyên trên runtime classpath

Dự án Maven có:

~~~text
project/
├── pom.xml
└── src/main/
    ├── java/demo/App.java
    └── resources/config/settings.txt
~~~

Maven đưa `src/main/resources/config/settings.txt` vào `target/classes/config/settings.txt` khi compile; khi đóng JAR, nó có thể trở thành JAR entry.

Đọc đúng:

~~~java
Resource settings =
        loader.getResource("classpath:config/settings.txt");
~~~

**Không hardcode đường source tree:**

~~~java
File file = new File("src/main/resources/config/settings.txt");
~~~

Đoạn sau có thể hoạt động trong IDE nhưng thất bại khi chạy JAR từ một working directory khác. Nguồn code `src/main/resources` không phải một directory bắt buộc tồn tại trong production.

### classpath: và classpath:/

Với `ClassPathResource` sử dụng classloader, slash đầu trong classpath thường được xử lý/loại bỏ, khiến hai cách sau cùng biểu diễn path từ classpath root:

~~~text
classpath:config/settings.txt
classpath:/config/settings.txt
~~~

Nhưng hãy dùng nhất quán `classpath:config/settings.txt` và không suy luận như vậy cho custom protocol resolver khác.

### Trùng tên file giữa nhiều JAR

`classpath:config/settings.txt` thường chọn **một** kết quả theo classloader lookup order. Nếu có nhiều JAR cùng chứa path đó, code không tự merge dữ liệu. Đừng dựa vào thứ tự dependency nếu muốn policy cấu hình rõ ràng. Nếu thật sự cần mọi bản, dùng `classpath*:` qua `getResources(...)`.

### Package-relative khác classpath-root

`ClassPathResource("config/settings.txt")` với classloader mặc định thường tra từ classpath root. Một constructor khác nhận anchor `Class<?>` có thể dùng semantics relative tới class package; phải xem overload được gọi, không tự mặc định mọi relative resource đều theo package của class hiện tại.

## 5. file: và đường dẫn filesystem

Ví dụ Unix:

~~~java
Resource external =
        loader.getResource("file:///opt/myapp/conf/settings.yml");
~~~

Ví dụ Windows:

~~~java
Resource external =
        loader.getResource("file:///C:/myapp/conf/settings.yml");
~~~

`file:` là **URL scheme**. Nó quyết định truy cập filesystem thay vì suy luận từ loader-specific relative path.

### Ưu tiên Path.toUri()

Không nên tự nối dấu slash/backslash để tạo file URL. Java đã hỗ trợ:

~~~java
Path path = Path.of("conf", "settings.yml").toAbsolutePath();
String location = path.toUri().toString();

Resource resource = loader.getResource(location);
~~~

Đây là snippet, cần `Path` import và biến `loader`. Trên Windows/Unix, `Path.toUri()` tạo URI hợp lệ và xử lý những ký tự cần encoding.

Khi đã có `Path`, cũng có thể tạo resource trực tiếp:

~~~java
Resource resource = new FileSystemResource(path);
~~~

Đường này không đi qua ResourceLoader string parsing, nhưng rõ ràng và dễ kiểm thử khi source nhất định là filesystem.

### Dấu / đầu không thay cho file:

~~~java
loader.getResource("/conf/settings.yml");
~~~

không chắc chắn tương đương `file:///conf/settings.yml`. Một `ApplicationContext` có thể diễn giải path bắt đầu bằng slash theo context base của nó, không phải filesystem root. Để bắt buộc dùng file tuyệt đối, ghi rõ `file:` hoặc dùng `FileSystemResource`.

## 6. http: và https: tài nguyên qua mạng

~~~java
Resource remote =
        loader.getResource("https://example.invalid/config.json");
~~~

Loader thông thường tạo URL-backed resource, nhưng **chỉ tạo handle không có nghĩa là đã kết nối mạng**. Khi gọi `getInputStream()` mới cần DNS, TLS, proxy/network, và có thể thất bại.

Những lưu ý production:

- Không chấp nhận arbitrary remote URLs do người dùng nhập; tránh **SSRF** và truy cập mạng nội bộ trái phép.
- Whitelist scheme và host phù hợp, kiểm soát redirects.
- Cấu hình timeout, size limits và retry strategy rõ ràng.
- Không log secrets/credentials nằm trong URL.
- Không tải resource remote trong constructor hoặc mỗi HTTP request nếu có thể cache hoặc khởi tạo theo một cơ chế chuyên dụng.
- Tách URL parsing với thao tác network thực tế để unit test không phụ thuộc Internet.

Chương trình runnable ở dưới **chỉ kiểm tra kiểu Resource**, không mở kết nối tới `example.invalid`.

## 7. Đường dẫn không prefix phụ thuộc ResourceLoader

Đây là điểm quan trọng nhất khi triển khai thư viện Spring reusable.

~~~java
Resource resource =
        loader.getResource("conf/app.xml");
~~~

Đường `conf/app.xml` không có scheme. Nó **không bắt buộc** trỏ tới `./conf/app.xml`. Spring cho phép loader quyết định mặc định.

| Loader/context | Diễn giải không prefix thường gặp |
| --- | --- |
| `DefaultResourceLoader` | Classpath-relative resource |
| `ClassPathXmlApplicationContext` | Classpath |
| `FileSystemXmlApplicationContext` | Filesystem theo context |
| Web-aware `ApplicationContext` | Servlet context-relative path |
| Custom `ResourceLoader` | Do custom implementation định nghĩa |

`ApplicationContext` triển khai `ResourceLoader`. Tuy nhiên hành vi không prefix phụ thuộc **loại context**; không được coi mọi `ApplicationContext` là `DefaultResourceLoader` với semantics giống hệt.

### Caveat của filesystem context

Spring Reference có lưu ý lịch sử: `FileSystemXmlApplicationContext` xử lý unprefixed path bắt đầu bằng slash và path relative theo semantics riêng; slash đầu không luôn được hiểu như absolute filesystem root. Nếu muốn thật sự absolute, dùng:

~~~java
context.getResource("file:///opt/app/conf/app.xml");
~~~

Đây là snippet có biến `context` thích hợp. Explicit prefix giúp tránh phụ thuộc filesystem-context quirks.

### Web context

`/WEB-INF/config.xml` có thể hợp lệ khi đọc tương đối từ web application root qua `ServletContextResource`. Nhưng chạy cùng string trên CLI `DefaultResourceLoader` không có servlet root để tra. Framework component muốn portable nên document input scheme hoặc cho phép caller truyền Resource thay vì suy diễn.

## 8. ResourceLoaderAware và dependency injection

Spring-managed bean có thể nhận ResourceLoader qua `ResourceLoaderAware`:

~~~java
public class TemplateReader implements ResourceLoaderAware {

    private ResourceLoader loader;

    @Override
    public void setResourceLoader(ResourceLoader loader) {
        this.loader = loader;
    }

    public String load() throws IOException {
        Resource resource = loader.getResource(
                "classpath:templates/welcome.txt");
        try (InputStream in = resource.getInputStream()) {
            return new String(
                    in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
~~~

Đây là **class snippet**, cần imports `ResourceLoaderAware`, `ResourceLoader`, `Resource`, `InputStream`, `IOException` và `StandardCharsets`. Spring gọi callback khi quản lý bean trong ApplicationContext thích hợp.

Ưu tiên constructor injection nếu có thể:

~~~java
@Component
public class TemplateReader {
    private final ResourceLoader loader;

    public TemplateReader(ResourceLoader loader) {
        this.loader = loader;
    }
}
~~~

Đây là snippet, cần `@Component` và `ResourceLoader` imports, component scanning hoặc explicit registration.

Một `@Bean` factory method cũng có thể nhận `ResourceLoader` qua parameter injection trong Spring ApplicationContext. Điều này được sử dụng ở runnable demo.

### Resource và @Value

Spring có thể inject một Resource từ location string:

~~~java
@Value("classpath:templates/welcome.txt")
private Resource template;
~~~

Đây là snippet trong bean được xử lý annotation. `template` là handle, **không phải text**, nên vẫn phải mở stream nếu muốn đọc.

Nếu cần quét hàng chục templates qua wildcard, nên inject `ResourcePatternResolver` hoặc dùng `Resource[]` conversion phù hợp; đừng viết vòng lặp qua `getResource("classpath*:...")` rồi mong nó tự duyệt tất cả.

## 9. classpath*: và ResourcePatternResolver

`ResourcePatternResolver` extends `ResourceLoader` và thêm:

~~~java
Resource[] getResources(String locationPattern) throws IOException;
~~~

Một implementation chuẩn là `PathMatchingResourcePatternResolver`.

### Cách tìm nhiều resources

~~~java
ResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver();

Resource[] files = resolver.getResources(
        "classpath*:templates/*.txt");
~~~

Đây là snippet cần imports `ResourcePatternResolver`, `PathMatchingResourcePatternResolver` và `Resource`.

So sánh:

~~~text
classpath:config/a.xml
    getResource(...) → một Resource

classpath*:config/a.xml
    getResources(...) → tất cả matches cùng path

classpath*:META-INF/spring/*.xml
    getResources(...) → tất cả files khớp wildcard
~~~

`classpath*:` được hỗ trợ để tìm nhiều classpath locations, kể cả các bản trong nhiều JARs; từ Spring Framework 6.0 còn được mở rộng liên quan tới module path.

### Ant-style patterns

Các biểu thức:

~~~text
classpath*:META-INF/spring/*.xml
classpath*:demo/templates/**/*.txt
file:/opt/app/config/*.yml
classpath:com/example/**/config.xml
~~~

có thể được `PathMatchingResourcePatternResolver.getResources(...)` xử lý. `*` khớp phần path/filename trong segment, `**` có thể khớp nhiều subdirectories theo `AntPathMatcher`.

### Giới hạn portability

Classpath scanning với wildcard phụ thuộc ClassLoader và cách JAR/container cung cấp directory URLs. Tránh pattern quá rộng ngay ở classpath root như `classpath*:*.xml` nếu cần portability cao; tốt hơn dùng một root directory ổn định như `classpath*:META-INF/spring/*.xml`.

Khi kết quả từ nhiều JARs:

- Số lượng resources có thể khác giữa test và production.
- Thứ tự không phải business ordering đáng tin; sort khi cần.
- Có thể có trùng nội dung/đường dẫn logic; xác định dedup policy.
- JAR packaging hoặc module path có thể ảnh hưởng matches.
- `getResources(...)` có thể ném `IOException`; xử lý đúng contract.

## 10. Ví dụ chạy độc lập Java 17 + Spring 6.2.9

Ví dụ này minh họa **một classpath resource**, **một unprefixed resource**, **một file URI**, **một HTTPS URL handle**, **missing resource** và **hai files khớp wildcard**, sau đó chứng minh Spring bean nhận ResourceLoader qua dependency injection.

### 10.1. Cấu trúc

~~~text
resource-loader-demo/
├── pom.xml
└── src/main/
    ├── java/demo/ResourceLoaderDemo.java
    └── resources/tutorial249/
        ├── message.txt
        └── templates/
            ├── one.txt
            └── two.txt
~~~

### 10.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>resource-loader-demo</artifactId>
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
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 10.3. Ba file dữ liệu

`src/main/resources/tutorial249/message.txt`:

~~~text
hello classpath
~~~

`src/main/resources/tutorial249/templates/one.txt`:

~~~text
one
~~~

`src/main/resources/tutorial249/templates/two.txt`:

~~~text
two
~~~

Mỗi file chỉ chứa một dòng như trên. Maven đưa resources lên classpath sau khi compile.

### 10.4. src/main/java/demo/ResourceLoaderDemo.java

~~~java
package demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public class ResourceLoaderDemo {

    private static String read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(
                    in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }

    public static void main(String[] args) throws IOException {
        ResourceLoader loader = new DefaultResourceLoader();

        Resource bundled = loader.getResource(
                "classpath:tutorial249/message.txt");
        System.out.println("classpath=" + read(bundled));

        Resource bare = loader.getResource("tutorial249/message.txt");
        System.out.println("unprefixedIsClasspath="
                + (bare instanceof ClassPathResource));

        Path temp = Files.createTempFile("resource-demo-", ".txt");
        try {
            Files.writeString(temp, "hello file", StandardCharsets.UTF_8);
            Resource fileResource = loader.getResource(
                    temp.toUri().toString());
            System.out.println("file=" + read(fileResource));
        } finally {
            Files.deleteIfExists(temp);
        }

        Resource remote = loader.getResource(
                "https://example.invalid/never-fetched.txt");
        System.out.println("httpsIsUrlResource="
                + (remote instanceof UrlResource));

        Resource missing = loader.getResource(
                "classpath:tutorial249/not-here.txt");
        System.out.println("missingExists=" + missing.exists());

        ResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(loader);
        Resource[] matches = resolver.getResources(
                "classpath*:tutorial249/templates/*.txt");

        String names = Arrays.stream(matches)
                .map(Resource::getFilename)
                .sorted()
                .collect(Collectors.joining(","));

        System.out.println("patternCount=" + matches.length);
        System.out.println("patternNames=" + names);

        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            System.out.println("injected="
                    + context.getBean(MessageReader.class).read());
        }
    }

    public static class MessageReader {
        private final ResourceLoader loader;

        public MessageReader(ResourceLoader loader) {
            this.loader = loader;
        }

        public String read() throws IOException {
            return ResourceLoaderDemo.read(
                    loader.getResource("classpath:tutorial249/message.txt"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        public MessageReader messageReader(ResourceLoader loader) {
            return new MessageReader(loader);
        }
    }
}
~~~

### 10.5. Chạy với Maven và JDK 17+

~~~bash
java -version
mvn -version
mvn -q compile exec:java -Dexec.mainClass=demo.ResourceLoaderDemo
~~~

Stdout dự kiến **của chương trình** (không tính log Spring/Maven), với đúng ba resource files trên và không có library khác thêm file vào `tutorial249/templates/`:

~~~text
classpath=hello classpath
unprefixedIsClasspath=true
file=hello file
httpsIsUrlResource=true
missingExists=false
patternCount=2
patternNames=one.txt,two.txt
injected=hello classpath
~~~

### 10.6. Giải thích stdout

| Dòng | Điều chứng minh |
| --- | --- |
| `classpath=hello classpath` | `classpath:` tìm đúng tài nguyên Maven đưa vào runtime classpath |
| `unprefixedIsClasspath=true` | `DefaultResourceLoader` hiểu đường không tiền tố theo classpath |
| `file=hello file` | `Path.toUri()` tạo file URL và `ResourceLoader` đọc file thực |
| `httpsIsUrlResource=true` | Nhận diện URL handle mà **không gọi mạng** |
| `missingExists=false` | Resource handle có thể tồn tại khi file không có |
| `patternCount=2` | `classpath*:` với `getResources(...)` tìm hai files |
| `patternNames=one.txt,two.txt` | Names được sort, tránh dựa vào thứ tự quét |
| `injected=hello classpath` | Spring context truyền `ResourceLoader` vào một `@Bean` factory method |

### 10.7. Thí nghiệm tự làm

1. Xóa `message.txt` rồi chạy `mvn clean compile`: `getResource` vẫn trả handle nhưng `read` có thể ném `FileNotFoundException`.
2. Chuyển `classpath:tutorial249/message.txt` thành `file:tutorial249/message.txt`: source chuyển từ classpath sang filesystem; nếu file path không tồn tại, đọc thất bại.
3. Thêm `templates/three.txt`, build lại: pattern count thường thành 3, names gồm `three.txt`.
4. Thử gọi `loader.getResource("classpath*:tutorial249/templates/*.txt")` trong Spring 6.2: nó không phải API trả mảng matches. Quay lại `getResources`.
5. Đưa resource vào JAR thực tế: `getInputStream` có thể vẫn đọc tốt trong khi `getFile` không thể lấy JAR entry thành `File` riêng.
6. Thay `DefaultResourceLoader` bằng filesystem-aware context trong một test riêng; so sánh semantics unprefixed path.
7. Dùng direct `FileSystemResource(temp)` thay vì `loader.getResource(temp.toUri().toString())` để thấy cách làm khi caller đã có `Path`.

## 11. getFile() và sự khác biệt khi đóng JAR

Một lỗi phổ biến là:

~~~java
File file = resource.getFile();
~~~

Khi chạy trong IDE, `classpath:...` có thể trỏ tới `target/classes/...`, nên `getFile()` hoạt động. Nhưng khi đóng JAR, classpath resource là **entry bên trong archive**; không tồn tại một `File` vật lý tương ứng với entry đó. Spring có thể ném `FileNotFoundException` khi gọi `getFile()`, dù `getInputStream()` vẫn thành công.

Dùng:

~~~java
try (InputStream input = resource.getInputStream()) {
    byte[] data = input.readAllBytes();
    // Parse theo định dạng cần thiết.
}
~~~

Đây là snippet để đọc tài nguyên nhỏ. Với data lớn, dùng stream-oriented parser và size cap, tránh đọc không giới hạn vào RAM.

Nếu API bên thứ ba bắt buộc nhận `File`, có thể copy tạm resource stream tới một file do chương trình kiểm soát rồi xóa sau. Không biến mọi classpath/URL resource thành giả định file path.

### Resource.exists() không đảm bảo lần mở sau thành công

`exists()` là một phép kiểm tra tại thời điểm gọi. File có thể bị xóa, quyền truy cập thay đổi, network có thể ngắt giữa check và read. Luôn catch/propagate `IOException` ở lần `getInputStream()` thực tế.

## 12. So sánh các Spring ApplicationContexts

`ClassPathXmlApplicationContext` mặc định đọc cấu hình không prefix theo classpath. `FileSystemXmlApplicationContext` thường đọc đường tương đối theo filesystem context. `WebApplicationContext` có thêm servlet resource root.

Ví dụ minh họa:

~~~java
ApplicationContext fromClasspath =
        new ClassPathXmlApplicationContext("conf/context.xml");

ApplicationContext fromFilesystem =
        new FileSystemXmlApplicationContext("conf/context.xml");
~~~

Đây là **snippets**, yêu cầu `conf/context.xml` tồn tại ở nguồn tương ứng và các imports đúng. Chúng không thuộc demo chính vì demo dùng annotation configuration.

Đặc biệt:

~~~java
ApplicationContext context =
        new FileSystemXmlApplicationContext(
                "classpath:conf/context.xml");
~~~

Câu này yêu cầu file XML **khởi tạo context** được nạp từ classpath. Nhưng context instance vẫn là loại `FileSystemXmlApplicationContext`; khi sau đó gọi `context.getResource("no-prefix.txt")`, nó vẫn dùng mặc định filesystem context, không chuyển thành classpath loader vì XML được load qua `classpath:`.

Đây là lý do cần phân biệt **đường dẫn nguồn định nghĩa context** và **loader strategy của context instance**.

## 13. Kiểm thử không phụ thuộc hạ tầng

### Test code đọc Resource bằng ByteArrayResource

~~~java
Resource resource = new ByteArrayResource(
        "hello".getBytes(StandardCharsets.UTF_8));

try (InputStream in = resource.getInputStream()) {
    String actual = new String(
            in.readAllBytes(), StandardCharsets.UTF_8);
    if (!"hello".equals(actual)) {
        throw new AssertionError("Wrong content");
    }
}
~~~

Snippet test cần imports `ByteArrayResource`, `Resource`, `InputStream` và `StandardCharsets`. Không cần filesystem hoặc network.

### Test resource có/không trên classpath

~~~java
ResourceLoader loader = new DefaultResourceLoader();

if (!loader.getResource(
        "classpath:tutorial249/message.txt").exists()) {
    throw new AssertionError("Classpath resource missing");
}

if (loader.getResource(
        "classpath:tutorial249/no-such-file.txt").exists()) {
    throw new AssertionError("Unexpected file");
}
~~~

Đây là snippet dựa vào resource files của demo. Nếu file thiếu sau khi sửa source, hãy dùng `mvn clean compile` để xóa artefact cũ trong `target/classes`.

### Test wildcard

~~~java
ResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver();

Resource[] results = resolver.getResources(
        "classpath*:tutorial249/templates/*.txt");

if (results.length != 2) {
    throw new AssertionError("Expected two demo resources");
}
~~~

Assert số lượng cố định chỉ phù hợp **classpath test được kiểm soát**. Trong multi-module production app, số lượng có thể khác do nhiều JARs cùng path.

### Test packaged JAR

Sau build, thử đọc resource từ artifact thực sự được chạy trong packaging/deployment. Không dựa vào `src/main/resources` tồn tại. Đặc biệt test `getInputStream()` thay vì `getFile()` và kiểm tra wildcard behavior khi nằm trong JAR/boot launcher nếu ứng dụng dùng cơ chế đó.

## 14. Lỗi phổ biến và cách khắc phục

| Vấn đề | Nguyên nhân | Khắc phục |
| --- | --- | --- |
| `getResource` trả handle nhưng đọc lỗi | Target không tồn tại hoặc không đọc được | Kiểm tra `exists`, rồi xử lý `IOException` khi mở stream |
| `classpath:` không tìm thấy file | Resource chưa đưa vào runtime classpath/JAR | Kiểm tra Maven `src/main/resources` và build |
| `new File("src/main/resources/...")` lỗi production | Path source tree không còn tồn tại cạnh JAR | Dùng `classpath:` và `getInputStream` |
| `resource.getFile()` ném exception trong JAR | JAR entry không phải filesystem file riêng | Dùng stream hoặc copy temp khi cần |
| `file:` URL không đúng trên Windows | Ghép path bằng slash/backslash thủ công | Dùng `Path.toUri().toString()` |
| Unprefixed path đọc sai chỗ | Loader/context có default khác | Dùng explicit `classpath:`/`file:` |
| `/data/...` không là file root | Slash đầu vẫn context-relative | Dùng `file:///...` |
| `classpath:` chọn một trong nhiều file cùng tên | ClassLoader lookup trả một bản | Dùng `classpath*:` + `getResources` khi cần tất cả |
| `classpath*:` với `getResource` không hoạt động như mong đợi | Nhầm single-resource API với pattern API | Dùng `ResourcePatternResolver.getResources` |
| Wildcard không thấy file trong JAR | Classloader/JAR root matching constraints | Dùng stable prefix directory và test packaged build |
| Wildcard trả files lặp/khác thứ tự | Nhiều JARs có cùng path | Sort/deduplicate theo rule |
| HTTPS resource đọc chậm hoặc lỗi | Network, DNS, TLS, timeout | Xác thực source và quản lý timeout/retry |
| InputStreamResource đọc lần hai lỗi | One-shot stream | Đọc một lần/đổi Resource implementation |
| Tài nguyên tiếng Việt hiển thị sai | Charset không đúng | Đọc với `StandardCharsets.UTF_8` |
| Resource từ user URL gây SSRF | URL không được kiểm soát | Whitelist schemes/hosts và network restrictions |
| Đọc file quá lớn gây OOM | `readAllBytes` không giới hạn | Dùng streaming parser/size limit |
| `@Value Resource` chưa có text | @Value inject Resource handle | Mở stream/parse rõ ràng |
| `exists()` đúng nhưng stream vẫn lỗi | File/network thay đổi sau check | Vẫn catch IOException tại read |
| Web path chạy trong CLI thất bại | Không có servlet context | Dùng loader thích hợp/explicit location |

### Quy trình debug

1. Xác định chính xác **location string** (ẩn credentials/tokens).
2. Xác nhận loader/context type đang sử dụng.
3. Phân biệt **prefix** với path tương đối không prefix.
4. Kiểm tra runtime classpath/filesystem/remote source thật.
5. Nếu đang dùng wildcard, kiểm tra `getResources` và `classpath*:`.
6. Khi chạy packaged JAR, không trông chờ `getFile`.
7. Đọc root cause của `IOException`; kiểm tra network, permissions, encoding.
8. Nếu có nhiều matches, kiểm tra thứ tự/dedup.
9. Có test chạy cùng packaging/classloader như production.
10. Áp security policy trước khi nhận file/URL từ nguồn không tin cậy.

## 15. Checklist áp dụng

- [ ] Dùng `classpath:` cho resource đóng gói với ứng dụng.
- [ ] Dùng `file:` hoặc `FileSystemResource` cho file ngoài filesystem.
- [ ] Dùng `http:`/`https:` chỉ khi remote loading thực sự cần và an toàn.
- [ ] Không đoán đường không prefix luôn là working directory.
- [ ] Với nhiều resources, dùng `ResourcePatternResolver.getResources`.
- [ ] Hiểu `classpath*:` là nhiều matches, không phải đơn resource.
- [ ] Đọc bằng `getInputStream` với try-with-resources.
- [ ] Không coi `getFile` portable khi resource trong JAR.
- [ ] Xử lý IOException dù `exists` đã true.
- [ ] Với URL do user nhập, giới hạn scheme/host, timeout và size.
- [ ] Inject `ResourceLoader` thay vì toàn ApplicationContext nếu không cần.
- [ ] Test cả IDE và JAR deployment, nhất là wildcard scanning.
- [ ] Sort kết quả khi thứ tự quan trọng.
- [ ] Dùng UTF-8 hoặc charset phù hợp.
- [ ] Không load resource lớn vào RAM khi có streaming option.

## 16. Câu hỏi tự kiểm tra

**1. `getResource(...)` trả dữ liệu hay handle?**  
Một `Resource` handle; chưa chắc target tồn tại.

**2. Muốn đọc file trong JAR theo classpath, dùng tiền tố nào?**  
`classpath:` và `getInputStream()`.

**3. Có nên dùng `new File("src/main/resources/a.txt")` trong production?**  
Không; resource có thể được đóng trong JAR.

**4. `classpath:` tìm mọi bản file cùng tên từ các JAR khác nhau đúng không?**  
Không; dùng `classpath*:` thông qua `getResources(...)` khi cần nhiều.

**5. Đường `config/a.xml` không prefix luôn là filesystem relative path?**  
Không. Ý nghĩa phụ thuộc loader/context.

**6. `file:` URL nên tạo từ Windows Path bằng cách nào?**  
`Path.toUri().toString()` để không ghép slash thủ công.

**7. `Resource.getFile()` có luôn hoạt động khi resource nằm trong JAR?**  
Không. Dùng InputStream.

**8. `Resource.exists()` true có bảo đảm read tiếp thành công không?**  
Không, vẫn phải xử lý IOException khi mở.

**9. `classpath*:META-INF/spring/*.xml` gọi API nào trong Spring 6.2?**  
`ResourcePatternResolver.getResources(...)`.

**10. `ResourceLoaderAware` làm gì?**  
Spring cung cấp loader qua callback `setResourceLoader`.

**11. `@Value("classpath:...") Resource` trả string nội dung không?**  
Không. Inject Resource handle.

**12. Vì sao không đọc HTTPS URL tùy ý do user cung cấp?**  
Rủi ro SSRF, network abuse và lộ thông tin.

**13. Wildcard matching phụ thuộc classloader/JAR không?**  
Có, nên dùng thư mục gốc ổn định và integration test packaged build.

**14. Khi đã có `Path`, có cần đi qua ResourceLoader không?**  
Không bắt buộc; có thể dùng `FileSystemResource(Path)` trực tiếp.

## Tài liệu chính thức

- [Spring Framework Reference — Resources](https://docs.spring.io/spring-framework/reference/core/resources.html): ResourceLoader, prefix semantics, ApplicationContexts, wildcard patterns và classpath*.
- [Spring Framework 6.2 Javadoc — ResourceLoader](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/io/ResourceLoader.html).
- [Spring Framework 6.2 Javadoc — Resource](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/io/Resource.html).
- [Spring Framework 6.2 Javadoc — DefaultResourceLoader](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/io/DefaultResourceLoader.html).
- [Spring Framework 6.2 Javadoc — ResourcePatternResolver](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/io/support/ResourcePatternResolver.html).
- [Spring Framework 6.2 Javadoc — PathMatchingResourcePatternResolver](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/io/support/PathMatchingResourcePatternResolver.html).
- [Spring Framework 6.2 Javadoc — ClassPathResource](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/io/ClassPathResource.html).
- [Spring Framework 6.2 Javadoc — FileSystemResource](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/core/io/FileSystemResource.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-004">ResourcePatternResolver, classpath: và classpath*:</a>

<details>
<summary>Xem chi tiết</summary>

# ResourcePatternResolver, `classpath:` và `classpath*:` trong Spring Framework

## 1. Bài toán

Một ứng dụng Java có thể lấy cấu hình từ nhiều file: `src/main/resources` của dự án, thư mục classes, các thư viện JAR, hoặc Java modules. Ví dụ mỗi plugin đóng gói một file `META-INF/myapp/extensions.xml`, và ứng dụng cần tìm **tất cả** file đó chứ không phải chỉ file đầu tiên xuất hiện trong classpath.

Spring Core cung cấp ba abstraction liên quan:

- `Resource`: handle đại diện cho **một** tài nguyên.
- `ResourceLoader`: nhận một `location` và cung cấp **một Resource** bằng `getResource(String)`.
- `ResourcePatternResolver`: mở rộng ResourceLoader để nhận **một pattern** và trả **nhiều Resource** qua `getResources(String)`.

Hai tiền tố rất dễ nhầm:

- `classpath:`: tìm **một** classpath resource thông thường, theo cơ chế ClassLoader.
- `classpath*:`: tiền tố dành cho `ResourcePatternResolver.getResources(...)` để tìm **mọi occurrence** của đường dẫn tại các classpath/module roots mà resolver truy cập được.

Mục tiêu bài học: hiểu đúng hai tiền tố, áp dụng `*` và `**`, dùng `PathMatchingResourcePatternResolver`, giải thích giới hạn khi quét JAR và module path, tránh lỗi `getFile()` khi tài nguyên nằm trong JAR, và chạy ví dụ Java 17 cho hai classpath roots trùng tên.

## 2. Resource chỉ đại diện một tài nguyên

`org.springframework.core.io.Resource` cung cấp API đọc tài nguyên:

| Phương thức | Ý nghĩa |
| --- | --- |
| `exists()` | Kiểm tra resource có khả dụng/tồn tại hay không |
| `getInputStream()` | Mở stream nội dung; caller cần đóng |
| `getURL()`, `getURI()` | Đường dẫn URL/URI nếu hỗ trợ |
| `getFilename()` | Tên file nếu suy ra được |
| `getDescription()` | Chuỗi mô tả phục vụ lỗi/log |
| `contentLength()` | Lấy kích thước khi có thể; có thể cần I/O |
| `isReadable()` | Kiểm tra khả năng đọc của implementation |
| `getFile()` | Chỉ phù hợp khi resource thực sự biểu diễn được dưới dạng File |

Ví dụ:

```java
Resource one = resourceLoader.getResource(
        "classpath:config/settings.properties");

if (one.exists()) {
    try (InputStream stream = one.getInputStream()) {
        String text = new String(
                stream.readAllBytes(), StandardCharsets.UTF_8);
        System.out.println(text);
    }
}
```

Đoạn trên là **mảnh Java minh họa**, cần biến `resourceLoader` kiểu `ResourceLoader` và imports `Resource`, `InputStream`, `StandardCharsets`; method caller phải xử lý `IOException`.

**Quan trọng:** việc gọi `getResource` có thể tạo một handle cho file **chưa tồn tại**. Muốn xác minh có thể đọc, dùng `exists()` hoặc thử mở stream và xử lý `IOException`. Một `Resource` không tự chứa danh sách files, do đó không thể dùng `Resource.getFile()` hoặc `getInputStream()` để giải quyết wildcard thành nhiều files.

## 3. ResourceLoader.getResource(String)

`ResourceLoader` là interface ở `org.springframework.core.io`. Chữ ký cốt lõi:

```java
Resource getResource(String location);
ClassLoader getClassLoader();
```

Đây là trích đoạn API, không phải file Java độc lập.

Ví dụ tạo ResourceLoader standalone:

```java
ResourceLoader loader = new DefaultResourceLoader();
Resource r = loader.getResource(
        "classpath:myapp/config/application.xml");
```

Đây là mảnh minh họa với `org.springframework.core.io.DefaultResourceLoader`.

Một path có `classpath:` dùng semantics classpath; một path `file:` dùng filesystem URL; `https:` thường hướng đến một URL resource. Còn path **không tiền tố** có thể phụ thuộc ResourceLoader implementation: `ClassPathXmlApplicationContext` và `WebApplicationContext` có cách hiểu resource context khác nhau. Không suy luận path `/WEB-INF/...` trong web context giống một classpath path tuyệt đối.

`ResourceLoader.CLASSPATH_URL_PREFIX` bằng `"classpath:"`. Khi cần một file xác định, `getResource("classpath:...")` là API dễ đọc nhất.

## 4. ResourcePatternResolver.getResources(String)

`org.springframework.core.io.support.ResourcePatternResolver` **extends ResourceLoader** và thêm method:

```java
Resource[] getResources(String locationPattern)
        throws IOException;
```

Hằng số của nó là:

```java
ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
```

với giá trị `"classpath*:"`.

Ví dụ:

```java
ResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver();

Resource[] files = resolver.getResources(
        "classpath*:META-INF/myapp/extensions.xml");
```

Đây là mảnh Java cần import `ResourcePatternResolver` và `PathMatchingResourcePatternResolver` từ `org.springframework.core.io.support`, và method chứa lệnh phải xử lý `IOException`.

Mảng có thể chứa không, một hoặc nhiều resources. `ResourcePatternResolver` không hứa một thứ tự nghiệp vụ cố định; nếu cần kết hợp configs theo priority, application phải tự định nghĩa priority và sort.

**Không gọi** `ResourceLoader.getResource("classpath*:...")` để lấy danh sách: `classpath*:` là pseudo-prefix phục vụ **`getResources`**, không phải một URL tới một đối tượng `Resource` duy nhất.

## 5. So sánh nhanh classpath: và classpath*:

| Location | API | Ý nghĩa |
| --- | --- | --- |
| `classpath:config/app.xml` | `getResource` | Một Resource handle theo đường dẫn |
| `classpath:config/app.xml` | `getResources` | Khi không wildcard, thông thường mảng **một** handle |
| `classpath*:config/app.xml` | `getResources` | Tất cả occurrences ở các classpath/module roots được resolver tìm thấy |
| `classpath:config/*.xml` | `getResources` | Quét pattern dựa vào **một root classpath URL** có thể được chọn |
| `classpath*:config/*.xml` | `getResources` | Quét pattern trên **nhiều roots** |
| `classpath*:config/**/*.xml` | `getResources` | Quét nhiều roots, kể cả các thư mục con |
| `file:/var/app/config/*.xml` | `getResources` | Glob tài nguyên ở filesystem root tương ứng |

Các dòng `classpath:` có wildcard **chỉ có ý nghĩa nếu caller thực sự dùng pattern resolver**. `getResource` của ResourceLoader không tự thực thi Ant wildcard.

Để chọn API, tự hỏi: “Tôi cần một file theo tên, hay cần **mọi bản sao và mọi file khớp một mẫu**?”

## 6. Hai JAR có cùng resource name

Một ứng dụng plugin có:

```text
plugin-one.jar!/META-INF/plugins.xml
plugin-two.jar!/META-INF/plugins.xml
```

Lời gọi:

```java
Resource one = loader.getResource(
        "classpath:META-INF/plugins.xml");
```

thường trỏ đến **bản đầu tiên** ClassLoader tìm thấy. Trái lại:

```java
Resource[] all = resolver.getResources(
        "classpath*:META-INF/plugins.xml");
```

yêu cầu tìm các occurrences trong cả hai JAR. Với classloader truyền thống, resolver có thể dùng `ClassLoader.getResources(...)` để tra các URL cùng đường dẫn.

Không có ý nghĩa “all resources ở toàn bộ máy tính”: chỉ các locations classloader/module path expose mới có thể được tìm. Nếu plugin JAR không nằm trong classpath/module path liên quan, thêm dấu sao vào tiền tố không tự tìm được nó.

## 7. Ant-style patterns: *, ? và **

`PathMatchingResourcePatternResolver` hỗ trợ path matching theo kiểu `AntPathMatcher`:

| Pattern | Khớp |
| --- | --- |
| `config/*.xml` | Các XML trực tiếp dưới thư mục config |
| `config/?.xml` | Tên một ký tự cộng suffix .xml |
| `config/**/beans.xml` | beans.xml trong config hoặc thư mục con |
| `config/**/*-beans.xml` | Tệp có hậu tố -beans.xml ở nhiều cấp |
| `META-INF/*-context.xml` | Files phù hợp ngay dưới META-INF |
| `classpath*:com/acme/**/*.properties` | Properties của package root trên nhiều classpath locations |

`*` và `?` tác động trong một đoạn đường dẫn; `**` có thể khớp không hoặc nhiều đoạn thư mục. Cách hoạt động là **Spring xử lý path pattern**, không phải JDK `ClassLoader.getResource` có thể hiểu `*`/`**`.

Mẫu có thể kết hợp với `file:` hoặc những URL schemes mà ResourceLoader hỗ trợ. Đừng truyền một chuỗi wildcard cho `java.nio.file.Path.of(...)` rồi mong Java tự liệt kê nội dung.

## 8. Resolver quét wildcard thế nào?

Xét mẫu:

```text
classpath*:com/acme/config/**/*.xml
```

Spring có thể thực hiện:

```text
Tách root không chứa wildcard:
    com/acme/config/
           |
           v
Tìm các root URLs classloader/module path nhìn thấy
           |
           v
Duyệt từng root:
    - thư mục trên filesystem: walk files
    - root là JAR: xét JAR entries qua jar URL
    - URL scheme khác: theo khả năng hỗ trợ runtime
           |
           v
AntPathMatcher lọc đường dẫn khớp
           |
           v
Gộp những Resource phù hợp -> Resource[]
```

Javadoc cảnh báo quét wildcard bên trong JAR và application server classloader có giới hạn portability. Khi chạy trên WebLogic, WebSphere, OSGi hoặc custom classloader, cần integration test đúng artifact triển khai, không chỉ test qua thư mục `build/classes` ở IDE.

## 9. Không có wildcard vẫn dùng classpath*:

`classpath*:META-INF/myapp/extensions.xml` không có wildcard ở phần đường dẫn nhưng vẫn khác `classpath:META-INF/myapp/extensions.xml`: một bên **enumerate tất cả**, một bên chỉ tìm một.

Có thể kiểm tra khi file không tồn tại:

```java
Resource[] one = resolver.getResources(
        "classpath:missing/not-there.xml");

Resource[] all = resolver.getResources(
        "classpath*:missing/not-there.xml");
```

Với implementation chuẩn, `one` có thể là một mảng **một handle** dù file không tồn tại (`one[0].exists() == false`), còn `all` rỗng vì không có resource URL nào được enumerated.

Vì vậy **đừng dùng `resources.length > 0` làm điều kiện “file thực sự tồn tại”** nếu lookup không wildcard theo `classpath:`. Với một resource handle, hãy kiểm tra `exists()` hoặc xử lý `IOException` khi đọc.

## 10. classpath: + wildcard có thể bỏ sót tài nguyên

Mẫu:

```text
classpath:com/acme/**/*.xml
```

có thể hoạt động khi classpath chứa đúng một `com/acme/` root. Tuy nhiên nếu package root đó xuất hiện ở nhiều JAR/thư mục, resolver có thể chỉ lấy **URL đầu tiên** của root qua `ClassLoader.getResource("com/acme/")`. File khớp trong roots khác có thể bị bỏ sót.

Nếu mục tiêu là quét **nhiều JAR hoặc modules**, ưu tiên:

```text
classpath*:com/acme/**/*.xml
```

Đây là một trong những lý do quan trọng nhất khiến Spring hỗ trợ `classpath*:`. Tuy vậy, classloader/JAR limitations vẫn có thể tồn tại; dùng `classpath*:` không phải bảo đảm tuyệt đối trong mọi runtime container.

## 11. Vì sao classpath*:*.xml là pattern có rủi ro?

Cảnh báo chính thức của Spring:

```text
classpath*:*.xml
```

**Không nhất thiết tìm được các XML ở root của JAR**. Khi wildcard bắt đầu ngay từ classpath root, Spring phải hỏi classloader về resource root trống (có thể qua `ClassLoader.getResources("")`); API này không bảo đảm trả JAR roots, thường chỉ expose directories. Spring có workaround nhưng không hoàn toàn portable.

Ưu tiên bố trí file dưới một root rõ ràng:

```text
classpath*:META-INF/myapp/*.xml
classpath*:com/acme/config/**/*.xml
```

Lưu ý phân biệt: `classpath*:myapp.xml` **không có wildcard** có thể enumerate resource ở root JAR theo tên cụ thể; cảnh báo chủ yếu dành cho **wildcard bắt đầu từ root**, không phải mọi đường dẫn root.

## 12. Spring 6 trở đi: module path cũng được xét

Từ Spring Framework 6.0, `classpath*:` mở rộng phạm vi tìm kiếm sang module path, không chỉ traditional classpath. `PathMatchingResourcePatternResolver` kiểm tra modules thuộc `ModuleLayer.boot()`, bỏ system modules, rồi kết hợp kết quả classloader classpath.

Điều này không cho phép truy cập vô hạn mọi Java module. Visibility và packaging vẫn phụ thuộc JPMS, classloader hierarchy và module layer. Nếu chạy chương trình thật với modules, kiểm tra trên deployment module path thay vì tự suy luận từ một dự án chạy classpath thuần.

## 13. Không bảo đảm thứ tự của Resource[]

Javadoc `ResourcePatternResolver` muốn tránh trả trùng các entries cùng physical resource khi có thể, theo ý tưởng **set semantics**. Song:

- Cùng đường dẫn trong hai JAR là hai tài nguyên vật lý khác nhau.
- Không nên dùng thứ tự enumeration làm thứ tự ghi đè config.
- Classloader này có thể enumerate khác classloader khác.
- Một số packaging đặc biệt vẫn có thể tạo overlapping Resource entries.
- Để load tất cả configurations có priority, phải đọc metadata rồi sort theo priority/key rõ ràng.

Nếu bạn cần chỉ một bản cấu hình “thắng”, thường nên dùng `Environment`/`PropertySources` với precedence rõ ràng hoặc một resource name duy nhất, thay vì dựa vào thứ tự `Resource[]`.

## 14. Chương trình Java 17 chạy được: hai classpath roots

Ví dụ dùng Spring Framework **6.2.18**, không cần Spring Boot. Nó tạo hai thư mục tạm độc lập, coi chúng là hai classpath roots. Mỗi root có `demo/config/settings.properties` với nội dung khác nhau. Các file bổ sung cho phép kiểm tra cả `*.properties` và `**/*.properties`.

Nhờ một `URLClassLoader` riêng, ví dụ không phụ thuộc system classpath. Đầu ra được sort theo nội dung file để không dựa vào thứ tự enumeration.

### Cấu trúc

```text
resource-pattern-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/ResourcePatternDemo.java
```

### settings.gradle

```groovy
rootProject.name = 'resource-pattern-demo'
```

### build.gradle

```groovy
plugins {
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-core:6.2.18'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'ResourcePatternDemo'
}
```

### src/main/java/ResourcePatternDemo.java

```java
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public class ResourcePatternDemo {

    private static void make(
            Path root, String relative, String text)
            throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static List<String> contents(Resource[] resources)
            throws IOException {
        List<String> result = new ArrayList<>();
        for (Resource resource : resources) {
            try (InputStream input = resource.getInputStream()) {
                String text = new String(
                        input.readAllBytes(), StandardCharsets.UTF_8);
                result.add(text.trim());
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    private static void show(
            ResourcePatternResolver resolver,
            String label, String pattern)
            throws IOException {
        Resource[] resources = resolver.getResources(pattern);
        System.out.println(label + ".count=" + resources.length);
        System.out.println(label + ".values=" + contents(resources));
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths
                    .sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory(
                "spring-resource-pattern-");
        try {
            Path first = temp.resolve("first");
            Path second = temp.resolve("second");

            make(first, "demo/config/settings.properties", "first");
            make(first, "demo/config/extra.properties", "extra");

            make(second, "demo/config/settings.properties", "second");
            make(second,
                    "demo/config/nested/override.properties", "nested");

            URL[] roots = {
                    first.toUri().toURL(),
                    second.toUri().toURL()
            };

            try (URLClassLoader loader =
                         new URLClassLoader(roots, null)) {
                ResourcePatternResolver resolver =
                        new PathMatchingResourcePatternResolver(loader);

                show(resolver, "single",
                        "classpath:demo/config/settings.properties");
                show(resolver, "allExact",
                        "classpath*:demo/config/settings.properties");
                show(resolver, "oneLevel",
                        "classpath*:demo/config/*.properties");
                show(resolver, "recursive",
                        "classpath*:demo/config/**/*.properties");

                Resource missing = resolver.getResources(
                        "classpath:demo/config/missing.properties")[0];
                System.out.println("missing.singleExists="
                        + missing.exists());
                System.out.println("missing.allCount="
                        + resolver.getResources(
                            "classpath*:demo/config/missing.properties")
                                .length);
            }
        } finally {
            deleteTree(temp);
        }
    }
}
```

### Chạy

Yêu cầu Java 17+ và Gradle 8+:

```bash
gradle run --quiet
# Hoặc ./gradlew run --quiet nếu đã tạo Gradle Wrapper
```

### System.out dự kiến

```text
single.count=1
single.values=[first]
allExact.count=2
allExact.values=[first, second]
oneLevel.count=3
oneLevel.values=[extra, first, second]
recursive.count=4
recursive.values=[extra, first, nested, second]
missing.singleExists=false
missing.allCount=0
```

Giải thích:

- `single`: không wildcard, không `classpath*:` → một handle đầu tiên do classloader chọn. Root `first` được đưa vào URLClassLoader trước root `second` nên file đầu chứa `first`.
- `allExact`: cùng tên `settings.properties` tồn tại ở **cả hai classpath roots**, nên nhận hai resource handles.
- `oneLevel`: `*.properties` chỉ khớp các file ở ngay dưới `demo/config/`: hai settings + một extra = ba resources.
- `recursive`: `**/*.properties` khớp cả `nested/override.properties`, tổng bốn.
- `missing.singleExists=false`: `getResources("classpath:...")` có thể trả một handle cho file thiếu; kiểm tra `exists()` mới biết.
- `missing.allCount=0`: `classpath*:` enumerate nhưng không có occurrence nào của resource thiếu.
- Các values được sort alphabetically theo nội dung `extra`, `first`, `nested`, `second`, nên output không phụ thuộc thứ tự duyệt URLs.
- `URLClassLoader` được đóng trước khi xóa thư mục tạm; cleanup không sửa project files.

**Điểm đáng chú ý:** Dependency chỉ cần `spring-core`. `PathMatchingResourcePatternResolver` dùng được độc lập, không bắt buộc tạo `ApplicationContext`.

## 15. Sử dụng trong ApplicationContext

Spring ApplicationContext thông thường cũng cung cấp `ResourcePatternResolver`. Với một context đang được sử dụng:

```java
Resource[] files = context.getResources(
        "classpath*:META-INF/myapp/*.xml");

for (Resource file : files) {
    System.out.println(file.getDescription());
}
```

Đây là **mảnh minh họa**, biến `context` phải là `ApplicationContext` phù hợp, phương thức xử lý `IOException`. Context không có resources khớp sẽ trả mảng rỗng.

Nếu một component chỉ nhận `ResourceLoader` qua `ResourceLoaderAware`, không mặc nhiên cast được mọi loader sang `ResourcePatternResolver`. Có thể kiểm tra:

```java
if (loader instanceof ResourcePatternResolver resolver) {
    Resource[] resources = resolver.getResources(
            "classpath*:META-INF/myapp/*.xml");
}
```

Đây là mảnh trong method Java 17 với biến `loader` đúng kiểu; cần xử lý `IOException`. Nếu chỉ dùng `ResourceLoader.getResource`, wildcard vẫn không được mở rộng thành nhiều resources.

## 16. Không dùng Resource.getFile cho tài nguyên trong JAR

Cách làm dễ lỗi:

```java
Resource first = resolver.getResources(
        "classpath*:META-INF/myapp/config.xml")[0];
File file = first.getFile();
```

Đây là **anti-pattern**: mảng có thể rỗng; resource nằm trong JAR không nhất thiết có File vật lý riêng; `getFile()` có thể ném exception.

Ưu tiên:

```java
Resource[] files = resolver.getResources(
        "classpath*:META-INF/myapp/config.xml");

for (Resource file : files) {
    try (InputStream input = file.getInputStream()) {
        // Parse content từ stream.
    }
}
```

Mảnh minh họa cần imports `Resource`, `InputStream`, `IOException` handling. Dùng stream và đóng bằng try-with-resources phù hợp với file trong JAR, URL hoặc filesystem.

**Không chuyển `jar:file:/...!/config.xml` thành `new File(url.getPath())`**: URL path có ký tự `!` và encoding, không phải một filesystem path bình thường.

## 17. Hiệu năng và bảo mật

Tránh scan quá rộng:

```text
classpath*:**/*
```

Mẫu này có thể dò rất nhiều JARs và file entries, ảnh hưởng startup. Dùng một root rõ, ví dụ `classpath*:META-INF/myapp/*.xml`. Nếu biết chính xác đường dẫn nhưng muốn mọi occurrences, `classpath*:META-INF/myapp/plugin.xml` thường gọn và đáng tin hơn wildcard.

Không đọc mọi kết quả `Resource` vào bộ nhớ bằng `readAllBytes()` nếu files có thể lớn hoặc đến từ nguồn không tin cậy. Với production XML, cấu hình XML parser chống external entity injection/XXE. Nếu user có thể nhập đường dẫn, không cho phép tùy tiện sử dụng `file:`/`http:` để đọc vùng ngoài policy của ứng dụng.

`ResourcePatternResolver` là cơ chế **discovery**, không phải file watcher, hot reload manager hay configuration-merging engine. Muốn reload hoặc precedence phải có logic riêng.

## 18. Các lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| Chỉ tìm được một file cùng tên | Dùng `classpath:` | Dùng `getResources("classpath*:...")` |
| `getResource("classpath*:...")` không trả nhiều | Dùng sai interface | Dùng `ResourcePatternResolver.getResources` |
| `classpath:pkg/**/*.xml` bỏ sót file trong JAR thứ hai | Pattern root chỉ lấy một classpath URL | Dùng `classpath*:pkg/**/*.xml` |
| `classpath*:*.xml` bỏ sót file ở JAR root | Wildcard root bị hạn chế bởi ClassLoader | Đặt root rõ như META-INF/myapp/ |
| Wildcard chạy trong IDE nhưng fail khi đóng JAR | Filesystem vs JAR URLs, đóng gói khác | Test trên executable JAR thật |
| Mảng một phần tử nhưng resource không tồn tại | Non-wildcard lookup trả handle | `exists()` hoặc mở stream, xử lý lỗi |
| `Resource.getFile()` ném IOException | File nằm bên trong JAR | `getInputStream()` |
| Kết quả đổi thứ tự giữa môi trường | Enumeration order không phải business contract | Sort theo metadata/priority |
| File không được tìm thấy | Không đóng gói vào classpath/module path | Kiểm tra build resources/deployment |
| Quét nhiều JAR rất chậm | Pattern quá rộng | Narrow root, cache kết quả nếu phù hợp |
| Không thấy resource trong Java module | Visibility/layer/classloader giới hạn | Test module path thực tế |
| Trùng kết quả resource vật lý | Overlap classpath URLs | Dedupe theo identity nếu ứng dụng yêu cầu |
| Lỗi đọc stream | Quyền, IO, corrupt JAR | Xử lý IOException, log path an toàn |

## 19. Checklist thực hành

Trước khi dùng resolver, tự kiểm tra:

1. Cần một file hay mọi file?
2. Có wildcard trong pattern hay không?
3. Đã chọn đúng `getResource` hoặc `getResources`?
4. Các module/JAR có thể chứa cùng resource path?
5. Pattern có root cụ thể trước `*`/`**` hay chưa?
6. Đã thử trường hợp không tìm thấy và kiểm tra `exists()`?
7. Đã test resources nằm trong JAR thay vì chỉ trong `build/classes`?
8. Có đọc bằng `getInputStream` và đóng stream?
9. Có sắp xếp trước khi merge nếu order ảnh hưởng kết quả?
10. Có giới hạn scan để không ảnh hưởng hiệu năng?
11. Java modules/application server có classloader đặc biệt?
12. Có tránh đưa user-supplied path đến mọi URL/resource tùy ý?

Bài tập: đặt cùng `META-INF/myapp/extension.properties` vào hai JAR; thử `classpath:` và `classpath*:`. Sau đó thêm `config/a.xml`, `config/b.xml`, `config/nested/c.xml` rồi so sánh `config/*.xml` với `config/**/*.xml`. Cuối cùng đóng JAR và thay `getFile()` bằng `getInputStream()` khi đọc resource.

## 20. Nguồn chính thức

- [Spring Framework Reference — Resources](https://docs.spring.io/spring-framework/reference/core/resources.html): Resource, ResourceLoader, wildcard resource locations và portability.
- [Spring 6.2 Javadoc — ResourcePatternResolver](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/support/ResourcePatternResolver.html): `classpath*:` và `getResources`.
- [Spring 6.2 Javadoc — PathMatchingResourcePatternResolver](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/support/PathMatchingResourcePatternResolver.html): glob matching, nhiều classpath roots và JAR scanning.
- [Spring 6.2 Javadoc — ResourceLoader](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/ResourceLoader.html): single resource lookup.
- [Spring 6.2 Javadoc — ClassPathResource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/ClassPathResource.html): classpath location và stream đọc file.
- [Spring 6.2 Javadoc — Resource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/Resource.html): `exists`/`getInputStream`/`getFile`.
- [Spring 6.2 Javadoc — AntPathMatcher](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/util/AntPathMatcher.html): `*`, `?`, `**`.
- [Spring 6.2.18 Source — PathMatchingResourcePatternResolver](https://github.com/spring-projects/spring-framework/blob/v6.2.18/spring-core/src/main/java/org/springframework/core/io/support/PathMatchingResourcePatternResolver.java): resolver implementation và module/JAR nuances.

**Ghi nhớ:** `classpath:` thường cho **một** classpath resource; `classpath*:` thông qua `ResourcePatternResolver.getResources` tìm **mọi occurrence** mà classloader/module path có thể thấy. Ant wildcard giúp tìm theo mẫu, nhưng với JAR cần root đủ cụ thể, integration test trên artifact thật và đọc tài nguyên bằng stream thay vì ép thành file.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-005">Tìm nhiều tài nguyên bằng mẫu đường dẫn</a>

<details>
<summary>Xem chi tiết</summary>

# Tìm nhiều tài nguyên bằng mẫu đường dẫn trong Spring Framework

## 1. Khi nào cần tìm nhiều tài nguyên thay vì một file?

Một ứng dụng Java thường không chỉ có một file cấu hình hay template. Chẳng hạn, nhiều module hoặc JAR có thể đóng góp các file:

~~~text
demo251/templates/
├── email/
│   ├── welcome.txt
│   └── reset.txt
└── docs/
    ├── readme.txt
    └── metadata.json
~~~

Nếu biết chính xác đường dẫn `demo251/templates/email/welcome.txt`, bạn có thể lấy một resource. Nhưng khi cần **tất cả file `.txt` trong nhiều thư mục con**, bạn cần cơ chế tìm kiếm theo mẫu đường dẫn (*resource location pattern*).

Spring Framework cung cấp:

- `Resource`: abstraction của một tài nguyên như file classpath, file hệ thống hoặc URL.
- `ResourceLoader`: tải một resource handle từ một location xác định.
- `ResourcePatternResolver`: mở rộng `ResourceLoader` để trả về **nhiều** resources theo location pattern.
- `PathMatchingResourcePatternResolver`: implementation thông dụng có hỗ trợ `classpath*:` và wildcard theo cú pháp **Ant-style**.

Ví dụ cốt lõi:

~~~java
ResourcePatternResolver resolver =
    new PathMatchingResourcePatternResolver();

Resource[] files =
    resolver.getResources("classpath*:demo251/templates/**/*.txt");
~~~

Kết quả là một mảng `Resource[]` gồm các mục khớp mẫu, có thể nằm ở những thư mục classpath khác nhau hoặc trong JAR. Việc tìm thấy tài nguyên và việc đọc nội dung của nó là **hai bước riêng**.

> **Nguyên tắc chính:** Dùng `ResourcePatternResolver.getResources(pattern)` để tìm nhiều tài nguyên; dùng `Resource.getInputStream()` để đọc. Ưu tiên `classpath*:` khi cần xét nhiều vị trí classpath; tránh giả định tài nguyên JAR luôn tương ứng với một `java.io.File`.

Bài này tập trung vào việc tìm nhiều tài nguyên bằng mẫu đường dẫn trong *Spring Framework – core-container*, có ví dụ Java 17 chạy bằng Maven, bảng pattern, giải thích giới hạn và xử lý lỗi thực tế.

## 2. Phân biệt `Resource`, `ResourceLoader` và `ResourcePatternResolver`

| Thành phần | API chính | Vai trò |
| --- | --- | --- |
| `Resource` | `getInputStream()`, `exists()`, `getURL()`, `getFilename()` | Mô tả **một** tài nguyên |
| `ResourceLoader` | `getResource(String location)` | Trả một resource handle từ location |
| `ResourcePatternResolver` | `getResources(String locationPattern)` | Trả **nhiều** tài nguyên khớp pattern |
| `PathMatchingResourcePatternResolver` | Triển khai `ResourcePatternResolver` | Tìm tài nguyên bằng `classpath*:` và Ant-style patterns |
| `PathMatcher` | `match(pattern, path)` | Kiểm tra một path có khớp mẫu hay không |
| `AntPathMatcher` | Implementation `PathMatcher` | Mặc định được resolver sử dụng cho Ant patterns |

Giao diện rút gọn:

~~~java
public interface ResourcePatternResolver extends ResourceLoader {
    String CLASSPATH_ALL_URL_PREFIX = "classpath*:";
    Resource[] getResources(String locationPattern)
        throws IOException;
}
~~~

Đây là **chữ ký API minh họa**, không phải định nghĩa bạn cần sao chép vào dự án.

### Vì sao `getResources()` khác `getResource()`?

~~~java
Resource single = resolver.getResource(
    "classpath:demo251/templates/email/welcome.txt");

Resource[] many = resolver.getResources(
    "classpath*:demo251/templates/**/*.txt");
~~~

Dòng đầu lấy một handle theo đường dẫn cụ thể; dòng sau thực hiện pattern resolution và có thể tìm nhiều file. Không dùng `getResource("classpath:.../*.txt")` rồi kỳ vọng nó tự biến thành mảng files.

Một `Resource` được trả về cũng chưa chắc file tồn tại hoặc đọc được; kiểm tra `exists()` và xử lý `IOException` khi mở stream.

## 3. Các tiền tố đường dẫn cần hiểu

Các location prefixes thường gặp:

| Prefix / dạng | Ý nghĩa khái quát | Ví dụ |
| --- | --- | --- |
| `classpath:` | Tra tài nguyên thông qua class loader theo một location | `classpath:config/app.xml` |
| `classpath*:` | Tìm trên nhiều vị trí module/classpath | `classpath*:META-INF/beans.xml` |
| `file:` | URL file hệ thống | `file:/opt/app/config/*.xml` |
| `http:`/`https:` | URL resource | `https://example.test/config.txt` |
| Không prefix | Phụ thuộc loại `ResourceLoader`/context | `/WEB-INF/config.xml` |

`classpath*:` là **pseudo-prefix đặc biệt của Spring**, không phải cú pháp URL chuẩn của JDK.

### `classpath:`: một classpath location

~~~java
Resource resource =
    resolver.getResource("classpath:settings/app.properties");
~~~

Dòng này yêu cầu một resource handle theo location ấy; thông thường class loader quyết định một vị trí phù hợp. Nếu nhiều JAR cùng cung cấp một tên file, không nên xem `classpath:` là API liệt kê toàn bộ bản sao.

### `classpath*:`: tất cả resources cùng location

~~~java
Resource[] resources =
    resolver.getResources("classpath*:META-INF/beans.xml");
~~~

Resolver có thể tìm nhiều file `META-INF/beans.xml` trong các classpath entries/JAR khác nhau. Với path cố định và không có wildcard, cơ chế chủ yếu dựa trên `ClassLoader.getResources(...)`. Từ Spring Framework 6.0, prefix này còn được mở rộng tìm trong các modules thuộc boot layer, ngoài system modules theo tài liệu tương ứng.

**Không tự suy ra thứ tự mảng trả về** là thứ tự override config hoặc thứ tự JAR load mong muốn.

## 4. Mẫu đường dẫn Ant-style: `*`, `?` và `**`

`PathMatchingResourcePatternResolver` mặc định dùng `AntPathMatcher`.

| Pattern | Ý nghĩa phổ biến | Ví dụ |
| --- | --- | --- |
| `*` | Khớp nhiều ký tự **trong một segment** đường dẫn | `email/*.txt` |
| `?` | Khớp **một ký tự** trong một segment | `config?.xml` |
| `**` | Khớp không hoặc nhiều **thư mục con** | `templates/**/*.txt` |
| `*.xml` | Phần tên file kết thúc bằng `.xml` | `config/*-beans.xml` |
| `**/app-*.properties` | Tìm các file tương ứng trong cây thư mục | `settings/**/app-*.properties` |

Một số ví dụ:

~~~text
classpath*:demo251/templates/email/*.txt
classpath*:demo251/templates/**/*.txt
classpath*:META-INF/*-beans.xml
classpath*:com/example/**/applicationContext.xml
file:/opt/myapp/config/*.properties
~~~

**Không đánh đồng Ant patterns với regular expressions.** Ví dụ `**/*.txt` không có nghĩa là regex `.*` rồi cộng thêm ký tự theo cú pháp `java.util.regex.Pattern`.

### `*` so với `**`

Giả sử:

~~~text
reports/a.txt
reports/archive/b.txt
reports/archive/2025/c.txt
~~~

- `reports/*.txt` chỉ nhắm các file trực tiếp bên dưới `reports` như `a.txt`.
- `reports/**/*.txt` có thể khớp cả các file `.txt` trong các cấp con, bao gồm cấp trực tiếp theo matching rules của `AntPathMatcher`.
- `reports/archive/*.txt` chỉ nhắm một cấp của `archive`.

**Nên có tiền tố thư mục cụ thể trước phần wildcard**, đặc biệt khi tìm trong JAR qua `classpath*:`.

## 5. Tại sao kết hợp `classpath*:` và Ant pattern?

Để tìm nhiều resources trong toàn bộ classpath, bạn thường kết hợp:

~~~text
classpath*:com/example/**/mapping-*.xml
~~~

Resolver thực hiện đại ý:

1. Xác định phần **root path không có wildcard**, ví dụ `com/example/`.
2. Liệt kê các URL tương ứng root ấy trên module/classpath.
3. Với mỗi root, nếu đó là thư mục thì duyệt hệ thống file; nếu là JAR/ZIP phù hợp thì duyệt entries.
4. Áp dụng `PathMatcher` cho phần wildcard.
5. Trả mảng các `Resource` khớp.

Điều này hữu ích cho framework/plugin modules, nơi cùng một logical root xuất hiện trong nhiều JARs.

### Vì sao `classpath:` cùng Ant pattern có thể bỏ sót?

~~~text
classpath:com/example/**/*.xml
~~~

Nếu `com/example` tồn tại trên nhiều classpath roots, implementation có thể lấy root URL đầu tiên và chỉ duyệt bên dưới root đó. Các resources ở classpath root khác có thể không được tìm.

Spring Javadoc khuyến nghị **`classpath*:`** khi phải dò nhiều classpath locations có cùng base package/root.

## 6. Giới hạn quan trọng: wildcard ngay tại gốc JAR

Một pattern như:

~~~text
classpath*:*.xml
~~~

**không đáng tin cậy khi muốn tìm file nằm ở root của mọi JAR**. Theo Javadoc, ClassLoader thường không cung cấp đầy đủ các root JAR URLs khi được hỏi bằng chuỗi rỗng. Resolver có các biện pháp giảm thiểu ở một số trường hợp, nhưng không bảo đảm portable trên mọi class loader/packaging.

Thay vì quét từ root chung chung, hãy tổ chức tài nguyên dưới một thư mục có tên cụ thể:

~~~text
classpath*:META-INF/my-plugin/*.xml
classpath*:com/example/config/**/*.xml
~~~

Thư mục cố định trước wildcard giúp resolver xác định một root để duyệt đáng tin hơn.

### Một mẫu nên tránh khác

~~~text
classpath*:/**/*.xml
~~~

Dù nhìn giống một lệnh tìm mọi XML, mẫu quá rộng khiến ứng dụng có thể quét rất nhiều JARs và còn gặp giới hạn root lookup. Nó cũng có thể tìm nhầm những XML không thuộc module của bạn.

**Thiết kế tốt:** Chọn một base directory có namespace riêng, ví dụ `META-INF/company-module/` hoặc `com/example/features/`, rồi dùng wildcard chỉ dưới đó.

## 7. Giới hạn khi tài nguyên nằm trong JAR

Khi chạy bằng IDE, `src/main/resources` thường được copy thành các file thật trong thư mục `target/classes`. Sau khi đóng JAR, cùng tài nguyên có thể nằm **bên trong archive**:

~~~text
target/classes/demo251/templates/email/welcome.txt
~~~

so với:

~~~text
jar:file:/opt/app/plugins.jar!/demo251/templates/email/welcome.txt
~~~

Các kiểu URL và class loader thực tế có thể khác. Tùy Java container/framework, URL còn có thể dùng các scheme đặc thù.

### Quy tắc đọc an toàn

~~~java
try (InputStream in = resource.getInputStream()) {
    byte[] bytes = in.readAllBytes();
}
~~~

`getInputStream()` là API phù hợp với tài nguyên trong classpath, file, URL hoặc JAR nếu implementation hỗ trợ đọc.

**Không dùng máy móc:**

~~~java
File file = resource.getFile();
~~~

`getFile()` chỉ hoạt động khi Resource thật sự ánh xạ được sang file hệ thống. Nếu nằm trong JAR, resource có thể không có `java.io.File` độc lập và method có thể ném `FileNotFoundException` hoặc lỗi khác.

### Wildcard trong JAR không phải bảo đảm tuyệt đối

Javadoc Spring chỉ rõ pattern resolver có những giới hạn portable khi duyệt JAR URLs/classloader-specific URLs. Hãy **kiểm thử cách đóng gói thực tế**: chạy IDE, Maven test, JAR, Boot executable JAR nếu dùng và các runtime/container đặc thù.

Không khẳng định mọi nested JAR hoặc custom class loader sẽ luôn được quét giống nhau.

## 8. Ví dụ hoàn chỉnh Java 17 + Spring 6.1.x

Ví dụ dùng đúng **Spring Context 6.1.13**, một API resolver hoạt động độc lập, và **4 files dữ liệu** đã biết. Mục tiêu:

- Tìm 3 file `.txt` theo mẫu đệ quy.
- Tìm 2 file `.txt` trong thư mục email.
- Lấy một resource cụ thể để kiểm tra `exists()`.
- Tìm một mẫu không có resource phù hợp.
- Đọc nội dung text qua stream.
- **Sắp xếp theo tên file** để stdout có thứ tự xác định, không phụ thuộc thứ tự duyệt classpath.

### 8.1. Cấu trúc project

~~~text
spring-resource-pattern-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/example/resources/ResourceSearchDemo.java
        └── resources/
            └── demo251/
                └── templates/
                    ├── email/
                    │   ├── reset.txt
                    │   └── welcome.txt
                    └── docs/
                        ├── readme.txt
                        └── metadata.json
~~~

### 8.2. `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-resource-pattern-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.13</version>
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
                <version>3.3.0</version>
                <configuration>
                    <mainClass>com.example.resources.ResourceSearchDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 8.3. `src/main/resources/demo251/templates/email/welcome.txt`

~~~text
Welcome aboard
~~~

### 8.4. `src/main/resources/demo251/templates/email/reset.txt`

~~~text
Reset your password
~~~

### 8.5. `src/main/resources/demo251/templates/docs/readme.txt`

~~~text
Documentation
~~~

### 8.6. `src/main/resources/demo251/templates/docs/metadata.json`

~~~json
{"category":"docs","version":1}
~~~

File `metadata.json` cố ý không khớp `**/*.txt` để kiểm tra filter pattern.

### 8.7. `src/main/java/com/example/resources/ResourceSearchDemo.java`

~~~java
package com.example.resources;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public class ResourceSearchDemo {

    public static void main(String[] args) throws Exception {
        ResourcePatternResolver resolver =
            new PathMatchingResourcePatternResolver();

        Resource[] found = resolver.getResources(
            "classpath*:demo251/templates/**/*.txt");

        // Resolver không cam kết thứ tự hiển thị.
        List<Resource> sorted = Arrays.stream(found)
            .sorted(Comparator.comparing(Resource::getFilename))
            .toList();

        System.out.println("text-count=" + sorted.size());

        for (Resource resource : sorted) {
            // Không dùng resource.getFile(): resource có thể ở trong JAR.
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8))) {

                System.out.println(
                    resource.getFilename() + "=" + reader.readLine());
            }
        }

        Resource[] email = resolver.getResources(
            "classpath*:demo251/templates/email/*.txt");
        System.out.println("email-count=" + email.length);

        Resource welcome = resolver.getResource(
            "classpath:demo251/templates/email/welcome.txt");
        System.out.println("single-exists=" + welcome.exists());

        Resource[] missing = resolver.getResources(
            "classpath*:demo251/templates/**/*.csv");
        System.out.println("missing-count=" + missing.length);
    }
}
~~~

### 8.8. Lệnh chạy

Yêu cầu JDK 17+ và Maven 3.9.x. Đứng ở thư mục có `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Khi classpath chỉ có các dữ liệu ví dụ được cung cấp theo đường dẫn `demo251/...`, những dòng `System.out.println` là:

~~~text
text-count=3
readme.txt=Documentation
reset.txt=Reset your password
welcome.txt=Welcome aboard
email-count=2
single-exists=true
missing-count=0
~~~

Maven hoặc logging framework có thể in thêm thông tin. Dòng dữ liệu được chủ động sắp xếp `readme.txt`, `reset.txt`, `welcome.txt` theo tên file để tránh kết quả khác nhau do thứ tự quét.

### 8.9. Giải thích từng phần

1. `classpath*:demo251/templates/**/*.txt`: tìm các file đuôi `.txt` ở dưới `demo251/templates` kể cả thư mục con.
2. `getResources(...)`: trả `Resource[]`, không trả nội dung text ngay.
3. `Arrays.stream(found).sorted(...)`: tạo thứ tự output cố định theo filename.
4. `resource.getInputStream()`: đọc từng resource theo abstraction, an toàn hơn việc cố lấy đường dẫn file.
5. `try-with-resources`: đóng stream/reader sau khi đọc.
6. `classpath*:demo251/templates/email/*.txt`: chỉ tìm thư mục `email` một cấp.
7. `getResource("classpath:.../welcome.txt")`: dùng path chính xác cho một resource.
8. `**/*.csv`: không có dữ liệu phù hợp nên mảng kết quả rỗng.

**Giả thiết stdout:** Nếu một dependency/JAR khác cố ý thêm file đúng namespace `demo251/templates` của ví dụ, số tài nguyên có thể tăng. Đây là lý do chọn namespace path riêng trong các ứng dụng thật.

## 9. Dùng trực tiếp `ApplicationContext.getResources()`

`ApplicationContext` trong Spring Framework cung cấp khả năng resource pattern lookup:

~~~java
try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext()) {

    context.refresh();
    Resource[] resources = context.getResources(
        "classpath*:demo251/templates/**/*.txt");

    System.out.println(resources.length);
}
~~~

Đây là snippet minh họa với imports `AnnotationConfigApplicationContext` và `Resource`; giả định các fixture resources đã được đóng gói vào classpath. Với project ở mục 8, `resources.length` sẽ thường là 3.

### Khi nào nên dùng resolver riêng?

Nếu một thư viện chỉ cần tìm files và không thực sự sử dụng bean graph, việc khởi tạo một `PathMatchingResourcePatternResolver` trực tiếp là đủ. Bạn không phải tạo `ApplicationContext` chỉ để đọc thư mục templates.

Nếu đã có Spring-managed component, có thể inject `ResourcePatternResolver` hoặc nhận nó từ ApplicationContext tùy kiến trúc. Đừng tự giữ references tới ApplicationContext toàn cục chỉ để tìm resources khi một abstraction nhỏ hơn đáp ứng nhu cầu.

### Tại sao `ResourceLoaderAware` chưa chắc đủ?

`ResourceLoaderAware.setResourceLoader(ResourceLoader)` cung cấp API `getResource(...)`. Muốn gọi `getResources(pattern)`, cần contract `ResourcePatternResolver` hoặc kiểm tra resource loader implementation hỗ trợ interface mở rộng này.

**Không cast mù quáng** mọi `ResourceLoader` thành `ResourcePatternResolver` nếu API chỉ cam kết interface cơ sở. Với một Spring ApplicationContext tiêu chuẩn thường có pattern resolver, nhưng với custom loader nên kiểm tra.

## 10. Đọc nhiều resources một cách an toàn

Với nội dung text, dùng `InputStreamReader` với charset cụ thể:

~~~java
for (Resource resource : resolver.getResources(
        "classpath*:demo251/templates/**/*.txt")) {

    try (var reader = new BufferedReader(
            new InputStreamReader(
                resource.getInputStream(),
                StandardCharsets.UTF_8))) {

        String firstLine = reader.readLine();
        // Xử lý nội dung theo yêu cầu nghiệp vụ.
    }
}
~~~

Đây là snippet đặt trong method khai báo `throws IOException` hoặc xử lý checked exception, với imports `Resource`, `BufferedReader`, `InputStreamReader`, `StandardCharsets` và resolver đã được tạo.

### Không giữ InputStream lâu hơn nhu cầu

`Resource` có thể là handle có thể mở stream nhiều lần; **`InputStream` cụ thể là tài nguyên I/O phải đóng**. Khi quét nhiều files, nếu không đóng streams có thể làm rò file descriptors, socket hoặc JAR file handles trong những môi trường nhất định.

### Đọc binary resource

Không dùng `BufferedReader` cho hình ảnh, PDF hay binary content. Với dữ liệu nhị phân, dùng byte streams và parser phù hợp:

~~~java
try (InputStream input = resource.getInputStream()) {
    processBinary(input);
}
~~~

Đây là snippet minh họa yêu cầu method `processBinary(InputStream)` do ứng dụng định nghĩa.

### Không cần ép tài nguyên ra URL/File nếu chỉ để đọc

`Resource.getURL()` hữu ích cho diagnostics và identity, nhưng thường không cần nếu chỉ đọc. `Resource.getFile()` đặc biệt không portable qua JAR.

## 11. Không giả định thứ tự `Resource[]`

Không có contract rằng:

~~~java
resolver.getResources(pattern)[0]
~~~

luôn là resource từ module A hoặc luôn là file có tên nhỏ nhất. Thứ tự phát hiện có thể phụ thuộc class loader, cấu trúc JAR, filesystem traversal và runtime environment.

Nếu bạn muốn **hiển thị** danh sách, sắp xếp theo key có ý nghĩa:

~~~java
List<Resource> ordered = Arrays.stream(resources)
    .sorted(Comparator.comparing(
        r -> r.getURL().toExternalForm()))
    .toList();
~~~

Đây là snippet **minh họa** cần xử lý `IOException` của `getURL()` trước khi dùng trong Comparator; không sao chép trực tiếp vào production như một lambda không bắt checked exception. Trong ví dụ runnable, dùng `Resource::getFilename` để tránh checked exception và vì fixtures không trùng filename.

### Nếu filename trùng ở nhiều JAR?

`getFilename()` không phải identifier duy nhất. Hai resources có thể cùng `META-INF/beans.xml` nhưng khác JAR. Với danh sách cần phân biệt hoàn toàn, dùng identity chuẩn hóa như URL external form (có xử lý lỗi), hoặc kết hợp location/module/provider name.

Nếu filenames trùng, chỉ sort theo filename không tạo thứ tự toàn phần; cân nhắc tie-breaker theo stable logical key của ứng dụng.

### Không coi “file đầu tiên thắng” là chính sách cấu hình

Nếu một framework/plugin áp dụng nhiều files, phải quyết định precedence/merge rõ ràng:

- File nào chỉ định defaults?
- File nào override?
- Có chấp nhận cùng key/trùng resource name?
- Nếu có xung đột thì fail hay cho phép?
- Thứ tự được định nghĩa bởi config hay sắp xếp deterministic?

Đó là **logic của ứng dụng**, không phải mặc định của resolver.

## 12. Kết quả rỗng khác lỗi đọc

`getResources(pattern)` trả `Resource[]` và có thể là mảng rỗng khi không có khớp. Điều này không đồng nghĩa đường dẫn file bị lỗi cú pháp trong mọi trường hợp.

Bạn cần quyết định theo nghiệp vụ:

~~~java
Resource[] configs =
    resolver.getResources("classpath*:config/myapp/*.xml");

if (configs.length == 0) {
    throw new IllegalStateException(
        "Required config files were not found");
}
~~~

Đây là snippet minh họa, nằm trong method có `throws IOException` và resolver đã tạo.

### Khi nào trả rỗng là hợp lệ?

- Plugin modules là optional.
- Không phải môi trường nào cũng có localization bundle tùy chỉnh.
- Thư mục template bổ sung có thể chưa được cấu hình.
- Pattern chỉ dùng để phát hiện extensions, không bắt buộc.

### Khi nào nên fail fast?

- Ứng dụng bắt buộc có ít nhất một schema/config.
- Không tìm thấy certificate/known policy file cần thiết.
- Pattern được xác định sai do build resources không được đóng gói.
- Bạn mong có files nhưng deployment không chứa chúng.

Tránh trường hợp app tiếp tục chạy âm thầm với “0 config resources” khi điều đó phá vỡ yêu cầu hệ thống.

## 13. Lỗi phân biệt giữa file system và classpath

Một đường dẫn:

~~~text
classpath*:demo251/templates/**/*.txt
~~~

là tìm trong module/classpath mà class loader nhìn thấy.

Đường dẫn:

~~~text
file:/opt/app/templates/**/*.txt
~~~

là tìm trong hệ thống file tương ứng URL/đường dẫn.

Đặt resource trong `src/main/resources` rồi nhìn thấy nó khi chạy Maven không có nghĩa `file:./src/main/resources/...` sẽ tìm được sau đóng gói JAR. Ngược lại, user upload vào một thư mục ngoài ứng dụng **không tự trở thành classpath resource**.

### Khi dùng filesystem trong sản phẩm

Nếu người vận hành cần thêm files mà không rebuild JAR, vị trí ngoài ứng dụng là lựa chọn phù hợp. Nên:

1. Cấu hình root directory rõ ràng.
2. Resolve/canonicalize đường dẫn bên dưới root cho phép.
3. Hạn chế wildcard quét những thư mục không có quyền.
4. Tránh follow symlinks không kiểm soát khi có chính sách bảo mật.
5. Xử lý file thay đổi trong quá trình scan.
6. Kiểm soát encoding/kích thước và quyền truy cập.

**Mẫu đường dẫn không phải bộ lọc bảo mật.** Input path do người dùng cung cấp cần được validate, không được dùng trực tiếp làm quyền truy cập file server.

## 14. Resource tồn tại nhưng không chắc là File

Spring `Resource` có nhiều implementations:

- `ClassPathResource` cho classpath.
- `FileSystemResource` cho filesystem.
- `UrlResource` cho URL.
- `ByteArrayResource` hoặc `InputStreamResource` cho bytes/stream có cách sử dụng riêng.

Hai lời gọi sau khác nhau:

~~~java
resource.exists();
resource.isReadable();
~~~

Một resource có thể trả true cho `exists()` nhưng vẫn lỗi khi mở stream do permission, file thay đổi hoặc network.

### Hạn chế `getFile()`

Chỉ gọi `getFile()` khi bạn **biết** location ánh xạ được sang file hệ thống. Khi các resources nằm trong JAR, dùng `getInputStream()` hoặc API abstraction thay vì cố “giải nén đường dẫn”.

### Hạn chế `getFilename()`

`getFilename()` trả phần tên cuối của resource, có thể `null` đối với loại resource không có tên. Code generic nên xử lý null hoặc sử dụng logical identifier do caller cung cấp.

Ví dụ ở mục 8 biết trước fixtures có filename hợp lệ nên comparator không cần xử lý null.

## 15. Trùng lặp và kết quả từ nhiều JAR

Một điểm mạnh của `classpath*:` là tìm cùng logical path ở nhiều JAR:

~~~text
library-a.jar!/META-INF/company/modules.xml
library-b.jar!/META-INF/company/modules.xml
application.jar!/META-INF/company/modules.xml
~~~

Lời gọi:

~~~java
Resource[] modules = resolver.getResources(
    "classpath*:META-INF/company/modules.xml");
~~~

có thể trả nhiều resources, một cho mỗi location tương ứng.

### Semantics giống tập hợp

`ResourcePatternResolver` document rằng implementation nên tránh trả các entries overlap trỏ tới cùng physical resource, **trong khả năng có thể**. Tuy nhiên không nên giả định mọi resource có unique content hay unique logical name. Những JAR khác nhau có thể chứa files khác nhau với cùng tên.

Nếu muốn dedup theo URL, cân nhắc so sánh URL external forms sau khi có handling đúng về archive/loader. Nếu muốn dedup theo **nội dung**, dùng hash nhưng phải đọc bytes và chấp nhận chi phí. Hai chính sách này khác nhau.

**Đừng chỉ dedup bằng `getFilename()`** nếu module metadata cùng tên cố ý xuất hiện ở nhiều JAR: bạn có thể làm mất plugins.

## 16. Biết khi nào pattern resolution được thực thi

Gọi:

~~~java
Resource[] resources = resolver.getResources(pattern);
~~~

thực hiện tra cứu, duyệt URL roots/JAR entries hoặc filesystem khi cần. Nó không đăng ký một watcher để tự cập nhật array khi file mới xuất hiện.

**ResourcePatternResolver không phải file watcher.**

Nếu một plugin mới được thêm vào thư mục hay classpath sau khi mảng đã được lấy, phải xác định khả năng class loader thực tế nhìn thấy plugin, rồi chủ động quét lại. Với JAR mới chưa nằm trong classpath/module layer, chỉ quét lại không tự nạp JAR đó vào JVM.

### Tác động hiệu năng

Một pattern quá rộng như:

~~~text
classpath*:**/*.xml
~~~

có thể buộc quét nhiều entries, tốn I/O/CPU khi startup, đặc biệt ở ứng dụng lớn. Đừng gọi quét classpath toàn diện mỗi request. Nên:

- Dùng base directory cụ thể.
- Cache kết quả discovery khi appropriate.
- Chỉ quét lại khi nguồn tài nguyên thực sự thay đổi và runtime hỗ trợ.
- Đặt giới hạn nếu truy cập filesystem do người dùng kiểm soát.
- Tránh parse cùng nội dung nhiều lần không cần thiết.

## 17. Khi nào dùng `AntPathMatcher` trực tiếp?

`AntPathMatcher` chỉ kiểm tra **một đường dẫn đã biết có khớp mẫu hay không**; nó không tự duyệt classpath/JAR.

Ví dụ:

~~~java
AntPathMatcher matcher = new AntPathMatcher();

boolean a = matcher.match(
    "reports/**/*.txt",
    "reports/archive/2025/a.txt");

boolean b = matcher.match(
    "reports/*.txt",
    "reports/archive/a.txt");

System.out.println(a);
System.out.println(b);
~~~

Đây là snippet trong method Java, import `org.springframework.util.AntPathMatcher`. Output:

~~~text
true
false
~~~

`PathMatchingResourcePatternResolver` dùng `PathMatcher` để nhận diện subpaths, nhưng còn phải **tìm các resource locations** trước khi áp mẫu. Dùng `AntPathMatcher` trực tiếp khi bạn đã có danh sách paths, không phải khi cần một API quét files.

### Có thể thay PathMatcher không?

`PathMatchingResourcePatternResolver` cung cấp `setPathMatcher(...)`. Nếu tự tùy biến, hãy nhớ behavior của patterns do custom matcher quyết định, vì interface `ResourcePatternResolver` không bắt buộc mọi implementation dùng đúng Ant syntax.

Với ứng dụng thông thường, dùng mặc định là dễ hiểu và tương thích nhất.

## 18. Placeholder bên trong path pattern: đừng nhầm cơ chế

Ví dụ:

~~~text
classpath*:config/${app.env}/**/*.xml
~~~

Bản thân `ResourcePatternResolver.getResources(...)` không phải một property-placeholder-resolution API. Nếu bạn truyền nguyên chuỗi này trong Java code, đừng giả định resolver luôn tự hỏi Environment xem `app.env` là gì.

Nếu muốn path có cấu hình:

~~~java
String location = environment.resolveRequiredPlaceholders(
    "classpath*:config/${app.env}/**/*.xml");

Resource[] resources = resolver.getResources(location);
~~~

Đoạn này là snippet trong method có `Environment environment` và `ResourcePatternResolver resolver`, khai báo/handle `IOException`. Nó **thực hiện rõ hai bước**: resolve placeholder trước, rồi tìm resources theo pattern.

Đừng cho phép property `app.env` chứa tùy ý các ký tự traversal/pattern như `..`, `**` nếu nó đến từ input không đáng tin. Cần validate biến path để bảo đảm không vượt phạm vi root được phép.

### Resource pattern khác `@PropertySource`

`@PropertySource` thường chỉ nhận các **locations resource cụ thể**, không tự có wildcard scanning như một lần gọi `PathMatchingResourcePatternResolver.getResources(...)`. Nếu muốn nạp nhiều file properties theo mẫu, cần lập trình logic discovery/registration và precedence riêng; đừng mặc nhiên coi annotation đó hỗ trợ mọi pattern.

## 19. Resource discovery và đọc cấu hình là hai bước khác nhau

Tìm được danh sách file:

~~~java
Resource[] propertyFiles =
    resolver.getResources("classpath*:config/*.properties");
~~~

không có nghĩa Spring sẽ:

- Tự parse files ấy thành `Properties`.
- Tự thêm chúng vào `Environment`.
- Tự merge theo thứ tự bạn mong.
- Tự bind thành typed configuration bean.
- Tự cập nhật khi file thay đổi.

Muốn nạp thành sources, bạn phải dùng loader/`PropertySource` tương ứng và quyết định precedence. Nếu dùng Spring Boot, cơ chế **Config Data** có quy tắc bootstrap/locations/imports khác với một cuộc quét resources tự phát.

**Cảnh báo về xung đột:** Một framework loader tự động quét rồi nạp mọi `*.properties` có thể vô tình lấy secrets/test data hoặc override settings không kiểm soát. Hãy giới hạn path rõ ràng và validate nguồn.

## 20. Những pattern nên và không nên dùng

| Tình huống | Pattern thích hợp | Lưu ý |
| --- | --- | --- |
| Tất cả XML cùng path qua nhiều JAR | `classpath*:META-INF/company/beans.xml` | Không cần wildcard nội bộ |
| Metadata XML dưới package cụ thể | `classpath*:com/example/**/mapping-*.xml` | Nhiều classpath roots |
| Text templates mọi thư mục con | `classpath*:demo251/templates/**/*.txt` | Có root cụ thể |
| Email template một cấp | `classpath*:demo251/templates/email/*.txt` | Không duyệt thư mục con |
| External config files | `file:/opt/app/config/*.properties` | Quyền filesystem/đường dẫn thật |
| Chỉ một resource cụ thể | `classpath:config/main.properties` | Dùng `getResource` |
| Quét mọi XML ở gốc JAR | `classpath*:*.xml` | Không portable, dễ bỏ sót |
| Toàn bộ classpath không giới hạn | `classpath*:**/*` | Tốn tài nguyên và dễ bắt nhầm |

Một pattern tốt có namespace root riêng, độ rộng vừa đủ, semantics rõ khi đóng JAR và được kiểm thử ở cách đóng gói production.

## 21. Lỗi thường gặp

| Hiện tượng | Nguyên nhân có thể | Cách xử lý |
| --- | --- | --- |
| Pattern trả 0 resources | Sai root path/case/extension hoặc files không có trong runtime classpath | Kiểm tra build output và paths |
| IDE thấy file nhưng JAR không thấy | Resource chưa được đóng gói hoặc JAR URL scanning khác | Kiểm tra artifact và test packaging |
| `classpath:.../**/...` bỏ sót file từ JAR khác | Chỉ root đầu tiên được duyệt | Dùng `classpath*:` |
| `classpath*:*.xml` bỏ file ở JAR root | ClassLoader root enumeration limitation | Đặt file dưới root directory cụ thể |
| `getFile()` ném exception | Resource nằm trong JAR | Dùng `getInputStream()` |
| `getResources(...)` có thứ tự khác giữa máy | Resolver không cam kết order | Sắp xếp theo stable identity |
| Duplicate filenames khiến mất plugin | Dedup theo filename sai | Giữ URL/module identity |
| Pattern `*.txt` không quét subfolder | `*` chỉ segment | Dùng `**/*.txt` dưới root phù hợp |
| `getResource("*")` không trả mảng | Nhầm API single và multiple | Dùng `getResources(...)` |
| File read lỗi dù `exists()` true | Permission/network/TOCTOU | Handle IOException khi mở stream |
| `@PropertySource` không quét glob | Annotation loader khác resolver | Dùng discovery + registration rõ |
| Pattern chứa `${env}` không thay | Chưa chạy PropertyResolver | Resolve placeholders trước |
| Mỗi request quét toàn classpath | Quét tốn CPU/I/O | Startup discovery/caching |
| App nạp cấu hình sai thứ tự | Không tự có precedence khi scan | Sắp xếp, merge policy minh bạch |
| Bị quét ra nhiều XML của thư viện không liên quan | Base path quá rộng | Namespace path cụ thể |
| File upload của user trở thành scan path | Thiếu input validation | Root allowlist, normalize paths |

## 22. Quy trình kiểm thử thực tế

**Bước 1 — Test với fixture resources.** Đặt 2–3 files dưới `src/test/resources/demo251/...` hoặc `src/main/resources/demo251/...` và assert số lượng/tên.

**Bước 2 — Test `*` và `**`.** Một pattern chỉ một thư mục, một pattern recursive; xác nhận tài nguyên con được tìm đúng.

**Bước 3 — Test không tìm thấy.** Pattern không có match phải được xử lý theo nghiệp vụ: optional trả rỗng, required thì fail.

**Bước 4 — Test content read.** Mở input streams bằng try-with-resources; assert text/bytes thực tế.

**Bước 5 — Test JAR packaging.** Không chỉ chạy trong IDE; build và chạy artifact hoặc integration test trên packaging thực tế.

**Bước 6 — Test multiple classpath roots.** Nếu module/plugin sử dụng nhiều JAR, tạo hai JAR fixtures có cùng logical resource path và kiểm tra `classpath*:` tìm đủ.

**Bước 7 — Test trùng filename.** Không vô tình loại bỏ resources khác nhau chỉ vì cùng tên cuối.

**Bước 8 — Test order policy.** Nếu ứng dụng merge configs, xác định thứ tự và assert thứ tự ấy trước khi parse.

**Bước 9 — Test performance.** Đo thời gian quét với số lượng JAR thực tế, đặc biệt khi có thư mục lớn.

**Bước 10 — Test security.** Nếu pattern do external input quyết định, thử absolute paths, traversal attempts và wildcard mở rộng ngoài root.

### Kiểm tra bằng một Spring context nhỏ

~~~java
try (AnnotationConfigApplicationContext context =
         new AnnotationConfigApplicationContext()) {

    context.refresh();
    Resource[] resources = context.getResources(
        "classpath*:demo251/templates/**/*.txt");

    if (resources.length != 3) {
        throw new AssertionError(
            "Expected 3 demo template resources");
    }
}
~~~

Snippet này yêu cầu imports `AnnotationConfigApplicationContext`/`Resource` và các fixture files mục 8 trong classpath.

## 23. Câu hỏi tự kiểm tra

**Câu 1:** Vì sao `ResourceLoader.getResource(...)` không thích hợp để tìm nhiều file theo wildcard?

**Đáp án:** Nó trả một `Resource` handle từ location; API lấy nhiều resources là `ResourcePatternResolver.getResources(...)`.

**Câu 2:** `classpath*:META-INF/beans.xml` khác `classpath:META-INF/beans.xml` thế nào?

**Đáp án:** `classpath*:` tìm tất cả resources tương ứng trên classpath/module path có thể truy cập; `classpath:` thường chọn một resource handle theo loader.

**Câu 3:** `**/*.xml` khác `*.xml` thế nào?

**Đáp án:** `**` cho phép nhiều cấp thư mục; `*` nằm trong một segment, thường chỉ filenames trong một directory.

**Câu 4:** Có nên dùng `classpath*:*.xml` để tìm mọi root XML trong JARs không?

**Đáp án:** Không đáng tin. ClassLoader có giới hạn liệt kê JAR roots khi wildcard nằm ngay root. Đặt tài nguyên dưới thư mục cụ thể.

**Câu 5:** Đọc file trong JAR bằng `resource.getFile()` có portable không?

**Đáp án:** Không. Dùng `getInputStream()` nếu mục tiêu là đọc nội dung.

**Câu 6:** `getResources(...)` có tự parse `.properties` thành Spring Environment không?

**Đáp án:** Không. Nó chỉ tìm `Resource[]`; cần bước parse/register PropertySources riêng.

**Câu 7:** Có thể giả định phần tử đầu tiên trong `Resource[]` là config ưu tiên nhất không?

**Đáp án:** Không. Resolver không phải precedence manager; application cần định nghĩa và kiểm thử order.

**Câu 8:** Nếu pattern trả mảng rỗng thì có chắc do file không tồn tại trên disk không?

**Đáp án:** Không. Có thể do classpath, đóng gói JAR, root lookup hoặc pattern không khớp.

**Câu 9:** `AntPathMatcher.match(...)` có tự duyệt JAR không?

**Đáp án:** Không. Nó chỉ so khớp path strings; resolver mới thực hiện tìm kiếm roots và resources.

**Câu 10:** Vì sao không nên cho user tự cung cấp `file:/**/secret*.txt` làm scan pattern?

**Đáp án:** Có thể quét vượt phạm vi được phép, lộ dữ liệu và gây quá tải. Cần root allowlist và validation.

## 24. Checklist áp dụng trong dự án

- [ ] Đã phân biệt rõ cần một resource hay nhiều resources?
- [ ] Sử dụng `getResources(...)` với `ResourcePatternResolver` đúng API chưa?
- [ ] Prefix `classpath:` hoặc `classpath*:` có phù hợp với nhiều JAR roots không?
- [ ] Pattern dùng `*`, `?`, `**` đúng ý nghĩa và có root cụ thể trước wildcard không?
- [ ] Nếu chạy từ JAR, có tránh `Resource.getFile()` không?
- [ ] Đã mở streams với try-with-resources và charset phù hợp chưa?
- [ ] Có xử lý `IOException` và mảng rỗng như các trường hợp riêng chưa?
- [ ] Có sắp xếp output/merge order nếu cần deterministic behavior không?
- [ ] Có giữ được resources trùng filename nhưng khác module không?
- [ ] Pattern có quá rộng, dễ làm tăng thời gian startup không?
- [ ] Đã kiểm thử classpath scanning khi deploy bằng artifact/container thật chưa?
- [ ] Có tách discovery khỏi parsing/configuration precedence không?
- [ ] Nếu pattern từ cấu hình, đã resolve placeholder và validate path chưa?
- [ ] Nếu input từ người dùng, đã giới hạn filesystem roots và kích thước scan chưa?

## 25. Tóm tắt

- **`ResourcePatternResolver`** mở rộng `ResourceLoader` với `getResources(String)` để tìm nhiều tài nguyên.
- **`PathMatchingResourcePatternResolver`** là implementation thông dụng, hỗ trợ `classpath*:` và Ant-style path patterns.
- **`classpath*:`** hữu ích khi cùng resource root xuất hiện trên nhiều classpath entries/JARs; `classpath:` có thể không tìm đầy đủ trong trường hợp này.
- **`*`** khớp ký tự trong một path segment, **`?`** khớp một ký tự, **`**`** khớp nhiều cấp directory.
- Mẫu **`classpath*:*.xml`** tại JAR root không đáng tin; nên dùng root directory trước wildcard.
- **Resource trong JAR không luôn là `File`**; đọc qua `getInputStream()` và đóng stream đúng cách.
- Resolver **không cam kết thứ tự**; nếu cần precedence hoặc output ổn định, phải sắp xếp và thiết kế policy riêng.
- **Tìm tài nguyên không đồng nghĩa parse/register Spring configuration**, không phải file watcher và không thay thế security validation.
- Test với fixture resources và cách đóng gói thực tế để phát hiện khác biệt classloader/JAR.

## 26. Tài liệu chính thức

- [Spring Framework Reference — Resources](https://docs.spring.io/spring-framework/reference/core/resources.html): Resource abstraction, ResourceLoader, ResourcePatternResolver và wildcards.
- [Spring Framework 6.1 — ResourcePatternResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/support/ResourcePatternResolver.html): `classpath*:`, `getResources` và result set semantics.
- [Spring Framework 6.1 — PathMatchingResourcePatternResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/support/PathMatchingResourcePatternResolver.html): thuật toán scan, Ant-style matching, JAR caveats và root limitations.
- [Spring Framework 6.1 — Resource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/Resource.html): `getInputStream`, `exists`, `getFile`, `getURL`, `getFilename`.
- [Spring Framework 6.1 — AntPathMatcher Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/util/AntPathMatcher.html): syntax `*`, `?`, `**` và path matching semantics.
- [Spring Framework 6.1 — ResourceLoader Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/ResourceLoader.html): lookup một resource handle.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-006">Tiêm Resource qua cấu hình hoặc điểm tiêm phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Tiêm Resource qua cấu hình hoặc điểm tiêm phù hợp trong Spring Core Container

## 1. Mục tiêu

Khi viết ứng dụng Spring, ta thường cần đọc một mẫu email, tệp JSON, chứng chỉ công khai, cấu hình XML, văn bản mẫu hoặc dữ liệu tĩnh. Các tệp ấy có thể nằm trong **classpath**, trên **filesystem**, được phân phối cùng một **JAR**, hoặc được cung cấp qua một **URL**. Nếu code gắn trực tiếp với `java.io.File` hay đường dẫn tương đối của máy phát triển, ứng dụng dễ hỏng khi đóng gói và triển khai.

Spring Core cung cấp abstraction `org.springframework.core.io.Resource`. Thay vì bắt service biết tài nguyên nằm ở đâu, cấu hình có thể đưa trực tiếp **Resource cần dùng** vào constructor/property hoặc đưa **ResourceLoader** vào một service cần chọn tài nguyên theo tên tại runtime.

Bài này làm rõ:

- Cách chọn giữa **tiêm `Resource` cố định**, **tiêm `ResourceLoader`** và **tiêm `ResourcePatternResolver`**.
- Cách cấu hình qua `@Value`, `@Bean`, constructor injection, setter, XML và `@Autowired`.
- Cách hiểu chính xác `classpath:`, `classpath*:`, `file:`, URL và đường dẫn không có prefix.
- Các phương thức đọc của `Resource`, stream lifecycle, lỗi `getFile()` khi đóng gói JAR.
- Phân biệt **`Resource` interface** với **`@Resource` annotation** của Jakarta.
- Các lỗi thường gặp, thiết kế test được và một ví dụ **Java 17 + Spring Framework 6.2.x** hoàn chỉnh.

**Quy tắc trọng tâm:** nếu resource location đã được xác định bởi cấu hình, **tiêm trực tiếp `Resource`**. Nếu location được chọn khi thực thi, **tiêm `ResourceLoader`**. Nếu cần giải quyết nhiều tài nguyên theo wildcard hoặc `classpath*:`, dùng **`ResourcePatternResolver`** hoặc tiêm `Resource[]` phù hợp.

## 2. Resource là gì?

`Resource` là abstraction của Spring đại diện cho một **mô tả/điểm truy cập tài nguyên**, không nhất thiết là toàn bộ nội dung đã được tải vào bộ nhớ. Nó kế thừa `InputStreamSource`, nên có thể cung cấp `InputStream` để đọc bytes.

Các phương thức thường dùng:

| API | Ý nghĩa | Cần lưu ý |
| --- | --- | --- |
| `getInputStream()` | Mở luồng byte để đọc | Đóng bằng try-with-resources; có thể ném IOException |
| `exists()` | Kiểm tra resource có tồn tại theo implementation | Có thể liên quan I/O; không thay thế xử lý lỗi khi mở |
| `isReadable()` | Kiểm tra có khả năng đọc | Không bảo đảm lần mở sau luôn thành công |
| `getFilename()` | Tên tệp cuối cùng nếu có | Có thể trả null |
| `getDescription()` | Mô tả phục vụ chẩn đoán | Không chứa hợp đồng đường dẫn vật lý cố định |
| `getURL()` | URL trỏ đến tài nguyên khi hỗ trợ | URL không đồng nghĩa File |
| `getURI()` | URI khi hỗ trợ | Không đảm bảo chuyển thành Path của filesystem |
| `getFile()` | File vật lý khi hỗ trợ | Tài nguyên bên trong JAR thường **không** có File vật lý |
| `contentLength()` | Kích thước theo implementation | Với stream một lần, tránh gọi tùy tiện vì có thể tiêu thụ stream |
| `createRelative(String)` | Tạo tài nguyên liên quan khi hỗ trợ | Semantics phụ thuộc loại Resource |
| `isOpen()` | Một resource có giữ stream đã mở hay không | `InputStreamResource` thường true |

Ví dụ đọc byte theo cách tương thích với classpath/JAR:

~~~java
static String readUtf8(Resource resource) throws IOException {
    try (InputStream stream = resource.getInputStream()) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
~~~

Đây là snippet; cần import `java.io.InputStream`, `java.io.IOException`, `java.nio.charset.StandardCharsets` và `org.springframework.core.io.Resource`.

Dù `Resource` thường chỉ là một **descriptor**, stream do `getInputStream()` trả về là tài nguyên I/O thật và phải đóng. Không giả định mỗi implementation đều cho phép mở lặp lại nhiều lần.

### Các implementation quan trọng

| Implementation | Nguồn dữ liệu tiêu biểu | Phù hợp khi |
| --- | --- | --- |
| `ClassPathResource` | Tệp/resource trong classpath | Đọc tài nguyên đóng gói trong ứng dụng hoặc JAR |
| `FileSystemResource` | File/path trên filesystem | Đọc file được triển khai riêng bên ngoài JAR |
| `PathResource` | `java.nio.file.Path` | Đã có Path và muốn dùng NIO |
| `UrlResource` | URL như `file:`, `https:` | Cần truy cập resource thông qua URL |
| `ByteArrayResource` | Byte array trong bộ nhớ | Test, tài liệu được tạo động, payload nhỏ |
| `InputStreamResource` | InputStream đã có hoặc nguồn stream tương ứng | Tích hợp nguồn một lần/đặc thù; tránh tái sử dụng stream |
| `ServletContextResource` | Tài nguyên trong ServletContext | Web application tương ứng |

`ClassPathResource` là lựa chọn phổ biến cho file tĩnh đi cùng ứng dụng. Nó vẫn đọc được qua `getInputStream()` khi file nằm trong JAR, trong khi `getFile()` thường chỉ làm việc khi classpath resource nằm trên hệ thống tệp thật.

`InputStreamResource` khác: theo Javadoc, đây là descriptor cho stream đang mở, `isOpen()` trả true và stream gốc không nên được đọc nhiều lần. Nếu muốn nội dung có thể đọc lại trong test, dùng `ByteArrayResource` hoặc implementation thích hợp.

## 3. Resource khác với String path và File

Một đoạn code phụ thuộc filesystem:

~~~java
File file = new File("src/main/resources/demo/message.txt");
String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
~~~

Đoạn trên **chỉ là phản ví dụ** cho việc truy cập file đóng gói; cần import các lớp Java tương ứng. Nó chạy được trong một số cấu trúc source tree nhưng không phải hợp đồng của ứng dụng đóng gói. Khi đã build JAR, đường dẫn `src/main/resources/...` thường không tồn tại ở working directory của ứng dụng.

Cách đúng với tài nguyên đi cùng ứng dụng:

~~~java
Resource template = new ClassPathResource("demo/message.txt");
try (InputStream in = template.getInputStream()) {
    String content =
            new String(in.readAllBytes(), StandardCharsets.UTF_8);
}
~~~

Đây là snippet. Tên `demo/message.txt` được giải quyết từ classpath, có thể nằm trong `target/classes` khi phát triển hoặc trong JAR khi đóng gói.

### Không nên chuyển ClassPathResource thành File chỉ để đọc

~~~java
Path path = resource.getFile().toPath(); // Không portable trong JAR.
~~~

Nếu tài nguyên được giải quyết thành URL kiểu `jar:file:/...!/demo/message.txt`, nó không còn là một file thông thường ở filesystem của máy. `getInputStream()` là API đúng khi chỉ cần đọc bytes/text.

Khi một thư viện bên thứ ba thực sự đòi `File`/`Path` và không chấp nhận stream, hãy cân nhắc giải nén có kiểm soát sang một file tạm do ứng dụng sở hữu, quản lý lifecycle/xóa file, hoặc đổi sang API của thư viện hỗ trợ stream. Không giả định `Resource#getFile` luôn làm được.

## 4. Khi nào nên tiêm trực tiếp Resource?

Spring Reference khuyến nghị cách **tiêm tài nguyên như dependency** khi đường dẫn được xác định từ cấu hình, không phải quyết định bởi chính service trong runtime.

Ví dụ một service đọc một template cố định:

~~~java
public final class WelcomeService {
    private final Resource template;

    public WelcomeService(Resource template) {
        this.template = template;
    }

    public String welcome() throws IOException {
        try (InputStream in = template.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
~~~

Đây là class minh họa với imports `Resource`, `InputStream`, `IOException`, `StandardCharsets`. Service không biết template nằm trên classpath, file system hay test memory. Nó chỉ cần một dependency đúng interface.

**Ưu điểm:**

- Constructor xác định rõ dependency cần thiết.
- Dễ unit test bằng `new ByteArrayResource(bytes)` mà không khởi tạo cả Spring context.
- Có thể đổi từ classpath sang file ngoài JAR bằng cấu hình, không phải sửa service.
- Ít coupling tới container vì service không cần cầm reference `ApplicationContext` hoặc `ResourceLoader`.
- Giảm nguy cơ service tự ghép đường dẫn nguy hiểm từ dữ liệu người dùng.

**Giới hạn:** service nhận một `Resource` cụ thể; nếu phải chọn giữa hàng trăm template ở runtime, inject `ResourceLoader` hoặc một interface như `TemplateRepository` phù hợp hơn.

## 5. Tiêm Resource qua @Value và property placeholder

Spring `ApplicationContext` có khả năng chuyển chuỗi resource location thành đối tượng `Resource` bằng infrastructure conversion/PropertyEditor tương ứng. Có thể khai báo:

~~~java
@Component
public final class TemplateReader {
    private final Resource template;

    public TemplateReader(
            @Value("${template.path}") Resource template) {
        this.template = template;
    }
}
~~~

Đây là snippet cần imports `@Component`, `@Value`, `Resource`. `template.path` có thể được thêm vào `Environment` bằng `MapPropertySource`, `@PropertySource` hoặc một nguồn cấu hình thích hợp.

Ví dụ `application.properties` trong một Spring Core application nếu đã được nạp vào Environment:

~~~properties
template.path=classpath:demo/message.txt
~~~

**Đường xử lý:**

~~~text
PropertySources
  └── template.path = "classpath:demo/message.txt"
            │
            ▼
@Value("${template.path}") trên constructor argument
            │
            ▼
Placeholder/embedded value resolution
            │
            ▼
String-to-Resource conversion
            │
            ▼
Resource descriptor được inject vào TemplateReader
            │
            ▼
TemplateReader mở InputStream khi cần
~~~

Điểm quan trọng là bean nhận **Resource**, không phải String. Nếu trường đích lại là `String` thì ứng dụng sẽ nhận location String để tự quyết định cách dùng, không phải một descriptor được Spring mở sẵn.

### Tiêm literal location

Không cần key nếu tên tài nguyên được xác định trong cấu hình Java:

~~~java
public TemplateReader(
        @Value("classpath:demo/message.txt") Resource template) {
    this.template = template;
}
~~~

Đây là snippet constructor, giả định các member còn lại đã khai báo. Dùng literal có thể phù hợp cho file không thay đổi giữa môi trường; dùng `${template.path}` khi cần chuyển location theo deployment.

### Đừng nhầm default placeholder với file existence

~~~java
@Value("${template.path:classpath:demo/message.txt}")
Resource template;
~~~

Nếu key thiếu, Spring dùng default location `classpath:demo/message.txt`. Nhưng việc tạo `Resource` **không tự bảo đảm file tồn tại và có thể đọc**. Tùy đường sử dụng, lỗi có thể chỉ xuất hiện khi gọi `getInputStream()`. Với tài nguyên bắt buộc, hãy kiểm tra/read tại một thời điểm chủ đích trong lifecycle.

### @Value trên constructor, field và setter

Spring có thể inject tài nguyên tại:

- **Constructor parameter:** khuyến nghị cho dependency bắt buộc, final field và test đơn vị.
- **@Bean factory method parameter:** phù hợp khi cấu hình chủ động lắp ráp object.
- **Field được hỗ trợ annotation injection:** ngắn nhưng khó tạo object bằng Java thuần trong test.
- **Setter method:** thích hợp với dependency tùy chọn, legacy JavaBeans hoặc khi cần thay ở giai đoạn cấu hình.

Ví dụ field injection:

~~~java
@Component
public final class ReportTemplate {
    @Value("classpath:reports/header.txt")
    private Resource header;
}
~~~

Đây là snippet minh họa; cần file thực tế. Không viết business logic gọi `header.getInputStream()` trong constructor trước khi Spring field injection diễn ra; field được Spring gán sau khi instance đã được tạo.

## 6. Tiêm Resource bằng @Bean: wiring rõ và test được

Có thể tạo một `Resource` bean rồi inject như dependency thông thường:

~~~java
@Configuration(proxyBeanMethods = false)
public class ResourceConfig {

    @Bean("reportTemplateResource")
    public Resource reportTemplateResource() {
        return new ClassPathResource("reports/template.txt");
    }

    @Bean
    public ReportGenerator reportGenerator(
            @Qualifier("reportTemplateResource") Resource template) {
        return new ReportGenerator(template);
    }
}
~~~

Đây là snippet; cần type `ReportGenerator`, các imports Spring và resource file. Cách này cho thấy Spring **không chỉ inject Resource bằng String conversion** mà còn cho phép một `Resource` object là **managed bean** trong BeanFactory.

### @Qualifier có vai trò gì?

Nếu context có nhiều bean cùng type `Resource`, injection chỉ theo type `Resource` có thể mơ hồ. `@Qualifier("reportTemplateResource")` giúp chọn đúng bean definition tương ứng. Không giả định có một Resource bean duy nhất chỉ vì ứng dụng sử dụng một file trong một module.

**Khác biệt với `@Value`:** 

- `@Value("classpath:...") Resource`: Spring chuyển String location thành resource descriptor cho injection point; không bắt buộc có bean tên `reportTemplateResource`.
- `@Qualifier(...) Resource`: BeanFactory tìm một **bean thực sự đăng ký trong container**, chọn theo qualifier/name phù hợp rồi inject object đó.
- `@Bean Resource`: method cấu hình **sở hữu cách tạo Resource**, có thể dùng `ClassPathResource`, `FileSystemResource`, `ByteArrayResource` và custom logic.

Đừng viết `@Autowired Resource template` mà không có BeanDefinition phù hợp rồi giả định Spring tự tìm file nào có tên giống biến. `Resource` type injection **khác** việc lấy tên file qua `@Value`.

### Khi nào dùng @Bean Resource?

- Có nhiều service cần chia sẻ cùng descriptor.
- Cần chọn implementation hoặc tính toán location trong một configuration factory method.
- Muốn đặt bean name/qualifier rõ.
- Muốn unit test thay `Resource` bean bằng `ByteArrayResource` dễ dàng.
- Cần validate existence/location trong bootstrap, trước khi service bắt đầu sử dụng.

Lưu ý: chia sẻ **descriptor** có thể an toàn với những implementation reopenable; nhưng đừng biến một `InputStreamResource` đã có stream một lần thành singleton mà nhiều consumers cùng đọc lại.

## 7. ResourceLoader: khi đường dẫn được chọn ở runtime

Interface `ResourceLoader` là cách Spring trả `Resource` từ location String:

~~~java
Resource getResource(String location);
ClassLoader getClassLoader();
~~~

Mọi `ApplicationContext` đều implements `ResourceLoader`. Với code chỉ cần resource loading, nên phụ thuộc vào **ResourceLoader interface** thay vì inject cả `ApplicationContext`.

Ví dụ:

~~~java
public final class TemplateCatalog {
    private final ResourceLoader loader;

    public TemplateCatalog(ResourceLoader loader) {
        this.loader = loader;
    }

    public Resource findTemplate(String knownLocation) {
        return loader.getResource(knownLocation);
    }
}
~~~

Đây là class minh họa; location nên đến từ **allowlist/cấu hình có kiểm soát**, không phải URL/file path tùy ý từ HTTP request.

### Tiêm bằng constructor hoặc @Bean

~~~java
@Bean
TemplateCatalog templateCatalog(ResourceLoader loader) {
    return new TemplateCatalog(loader);
}
~~~

Đây là snippet method trong `@Configuration`. Spring có thể cung cấp `ResourceLoader` như một dependency được hỗ trợ trong context.

Với class được Spring quản lý, có thể dùng constructor injection:

~~~java
@Component
public final class CatalogService {
    private final ResourceLoader loader;

    public CatalogService(ResourceLoader loader) {
        this.loader = loader;
    }
}
~~~

Đây là snippet; cần annotation scanning/registration. Constructor injection giữ dependency explicit, có lợi cho unit tests.

### ResourceLoaderAware

Spring còn cung cấp:

~~~java
public final class LegacyLoaderBean implements ResourceLoaderAware {
    private ResourceLoader loader;

    @Override
    public void setResourceLoader(ResourceLoader loader) {
        this.loader = loader;
    }
}
~~~

Đây là class minh họa, cần import `ResourceLoaderAware`/`ResourceLoader`. Khi class là bean do ApplicationContext quản lý, Spring có thể gọi callback để cung cấp loader. Cách này hữu ích cho integration kiểu framework/legacy, nhưng business service bình thường nên ưu tiên constructor injection nếu có thể.

## 8. ResourceLoader và ResourcePatternResolver khác nhau

`ResourceLoader.getResource(String)` trả **một Resource** theo location. Nó không có contract trả về một mảng tất cả các file match wildcard.

`ResourcePatternResolver` kế thừa `ResourceLoader`, bổ sung:

~~~java
Resource[] getResources(String locationPattern) throws IOException;
~~~

Nó hỗ trợ:

- `classpath*:config/*.xml` — tìm nhiều resources trên classpath/module path theo pattern.
- `classpath*:META-INF/spring/*.imports` — tìm những resources tương ứng từ nhiều locations nếu classpath/module layout phù hợp.
- `file:/etc/myapp/config/*.properties` — tập files theo pattern trên filesystem nếu resolver implementation hỗ trợ.
- Những Ant-style pattern như `**/*.xml` theo resolver tương ứng.

Ví dụ:

~~~java
ResourcePatternResolver resolver =
        new PathMatchingResourcePatternResolver();

Resource[] templates =
        resolver.getResources("classpath*:templates/*.txt");
~~~

Đây là snippet với imports `ResourcePatternResolver`, `PathMatchingResourcePatternResolver` và `Resource`. Nếu cần loader do context quản lý, có thể inject `ResourcePatternResolver` thay vì tự tạo resolver với classloader khác.

### @Value có thể inject Resource[]

Theo Spring Reference, nếu property chứa pattern:

~~~properties
templates.path=classpath*:templates/*.txt
~~~

thì constructor parameter có thể nhận mảng:

~~~java
public TemplateCatalog(
        @Value("${templates.path}") Resource[] templates) {
    this.templates = templates;
}
~~~

Đây là snippet; cần field và class phù hợp. Spring dùng conversion/property editor thích hợp để giải quyết mảng, gồm wildcard matching.

**Ranh giới quan trọng:**

- `classpath:...` mô tả một resource.
- `classpath*:...` và wildcard là **quy ước tìm tập resources**, nên dùng `ResourcePatternResolver.getResources` hoặc injection `Resource[]` được hỗ trợ.
- Không truyền `classpath*:...` vào `ResourceLoader.getResource` rồi kỳ vọng nó tự quét cả classpath.
- Không coi `classpath*:...` là một `Resource` đơn lẻ đại diện tập file.

### Thứ tự kết quả wildcard

Khi nhiều JAR/module xuất bản cùng đường dẫn, số resources và iteration order có thể khác giữa classpath, runtime, build tool và container. Đừng chọn “file đầu tiên” theo một thứ tự không được hợp đồng bảo đảm. Nếu cần precedence, định nghĩa và sort theo quy tắc rõ, hoặc dùng cơ chế cấu hình source priority của ứng dụng.

Khi quét classpath từ JAR, wildcard resolution có những giới hạn portability phụ thuộc classloader/URL scheme. Spring Reference khuyên kiểm thử trong đúng packaging/runtime môi trường thực.

## 9. Prefix và cách chọn nguồn

| Location | Thông thường được hiểu là | API/cơ chế |
| --- | --- | --- |
| `classpath:demo/message.txt` | Một tài nguyên từ classpath | `ResourceLoader.getResource` hoặc chuyển String → Resource |
| `classpath*:demo/*.txt` | Nhiều tài nguyên theo classpath pattern | `ResourcePatternResolver.getResources` hoặc `Resource[]` |
| `file:/opt/app/template.txt` | File trên filesystem | `ResourceLoader` hoặc `FileSystemResource` |
| `file:///C:/app/template.txt` | File URL dạng Windows | ResourceLoader/UrlResource tùy API |
| `https://example.org/template.txt` | URL resource từ network | `UrlResource`/ResourceLoader nếu được phép |
| `demo/message.txt` không prefix | **Phụ thuộc context/loader implementation** | Tránh mơ hồ trong cấu hình portable |

### Đường dẫn không prefix là bẫy

Spring Reference chỉ ra rằng khi một location không có prefix, loại Resource được tạo **phụ thuộc ApplicationContext cụ thể**. Ví dụ, ClassPathXmlApplicationContext và FileSystemXmlApplicationContext có thể giải quyết location mặc định theo cơ chế khác nhau.

Vì vậy, với code/README có yêu cầu portable, nên viết prefix tường minh:

~~~properties
template.path=classpath:templates/welcome.txt
~~~

hoặc:

~~~properties
template.path=file:/opt/myapp/templates/welcome.txt
~~~

Nếu resource nằm ngoài JAR và do deployment quản lý, `file:` thường rõ hơn. Nếu resource đi cùng chương trình thì `classpath:` thường đúng hơn.

### Relative path và createRelative

`resource.createRelative("other.txt")` phụ thuộc semantics của từng implementation; không tự giả định rằng nó có thể vượt qua ranh giới thư mục/package một cách chuẩn hoá hoặc an toàn. Nếu đường dẫn đến từ người dùng, validate canonical path và base directory trước khi tạo resource. Không dùng `../` từ input chưa tin cậy để đọc file ngoài thư mục được phép.

## 10. Ví dụ Java 17 hoàn chỉnh: ba cách cung cấp Resource

Ví dụ sử dụng **Java 17**, **Maven**, **Spring Framework 6.2.19**. Không cần Spring Boot hay máy chủ HTTP.

Nó minh họa ba cách sử dụng cùng một resource file:

1. **Tiêm vào `@Bean` factory method bằng `@Value("${demo.resource.location}") Resource`**.
2. **Định nghĩa một bean `Resource` tên `namedResource` rồi tiêm với `@Qualifier`**.
3. **Tiêm `ResourceLoader` vào service và gọi `getResource` khi cần**.

Ngoài ra, code sử dụng `ApplicationContext.getResources(...)` để kiểm tra pattern `classpath*:demo/*.txt` có thể tìm thấy resource file.

### Cấu trúc thư mục

~~~text
spring-resource-injection/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── example/
        │       └── ResourceInjectionDemo.java
        └── resources/
            └── demo/
                └── message.txt
~~~

### pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>spring-resource-injection</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.19</version>
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
            </plugin>
        </plugins>
    </build>
</project>
~~~

### src/main/resources/demo/message.txt

~~~text
Xin chào từ Spring Resource!
~~~

Tệp này phải được lưu ở mã hóa **UTF-8**; Maven sẽ chép nó vào classpath. Giữ đúng dấu tiếng Việt và đường dẫn trong ví dụ để stdout chính xác.

### src/main/java/example/ResourceInjectionDemo.java

~~~java
package example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public final class ResourceInjectionDemo {

    public static void main(String[] args) throws IOException {
        try (var context = new AnnotationConfigApplicationContext()) {
            // Chuẩn bị property sources trước khi context refresh.
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource(
                            "demoResourceSettings",
                            Map.<String, Object>of(
                                    "demo.resource.location",
                                    "classpath:demo/message.txt")));

            context.register(DemoConfig.class);
            context.refresh();

            DemoReader reader = context.getBean(DemoReader.class);

            System.out.println("From @Value: " + reader.fromValue());
            System.out.println("From @Bean: " + reader.fromBean());
            System.out.println("From loader: " + reader.fromLoader());
            System.out.println("Exists: " + reader.exists());

            Resource[] resources =
                    context.getResources("classpath*:demo/*.txt");

            boolean found = Arrays.stream(resources)
                    .anyMatch(r -> "message.txt".equals(r.getFilename()));
            System.out.println("Pattern found: " + found);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class DemoConfig {

        @Bean
        public static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean("namedResource")
        public Resource namedResource() {
            return new ClassPathResource("demo/message.txt");
        }

        @Bean
        public DemoReader demoReader(
                @Value("${demo.resource.location}") Resource viaValue,
                @Qualifier("namedResource") Resource viaBean,
                ResourceLoader resourceLoader) {
            return new DemoReader(viaValue, viaBean, resourceLoader);
        }
    }

    public static final class DemoReader {
        private final Resource viaValue;
        private final Resource viaBean;
        private final ResourceLoader resourceLoader;

        public DemoReader(Resource viaValue, Resource viaBean,
                          ResourceLoader resourceLoader) {
            this.viaValue = viaValue;
            this.viaBean = viaBean;
            this.resourceLoader = resourceLoader;
        }

        public String fromValue() throws IOException {
            return read(viaValue);
        }

        public String fromBean() throws IOException {
            return read(viaBean);
        }

        public String fromLoader() throws IOException {
            Resource resource = resourceLoader.getResource(
                    "classpath:demo/message.txt");
            return read(resource);
        }

        public boolean exists() {
            return viaValue.exists();
        }

        private static String read(Resource resource) throws IOException {
            try (InputStream in = resource.getInputStream()) {
                return new String(
                        in.readAllBytes(),
                        StandardCharsets.UTF_8).strip();
            }
        }
    }
}
~~~

### Lệnh chạy

Từ thư mục `spring-resource-injection`, khi đã có Java 17 và Maven:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.ResourceInjectionDemo
~~~

Stdout của **chính các lệnh `System.out.println`** (không bao gồm logging của Maven/SLF4J nếu môi trường bổ sung):

~~~text
From @Value: Xin chào từ Spring Resource!
From @Bean: Xin chào từ Spring Resource!
From loader: Xin chào từ Spring Resource!
Exists: true
Pattern found: true
~~~

### Phân tích từng bước

1. Constructor không tham số `new AnnotationConfigApplicationContext()` tạo context chưa refresh.
2. `demo.resource.location` được thêm vào `Environment` với `MapPropertySource` trước registration/refresh.
3. `DemoConfig.placeholders()` là `static @Bean` tạo `PropertySourcesPlaceholderConfigurer` để giải quyết placeholder trong container. Static tránh phải tạo configuration instance sớm chỉ để lấy BeanFactoryPostProcessor.
4. `DemoConfig.namedResource()` trả một `ClassPathResource`, trở thành Spring bean tên `namedResource`.
5. Khi tạo `DemoReader`, Spring resolve `${demo.resource.location}` thành `classpath:demo/message.txt`, rồi chuyển String thành `Resource` ở injection point `viaValue`.
6. `@Qualifier("namedResource")` lấy object Resource được định nghĩa bởi `@Bean`, không phải một resource location được suy từ parameter name.
7. Parameter `ResourceLoader` được context cung cấp theo dependency resolving của ApplicationContext.
8. Ba method service mở stream của cùng file resource bằng ba đường được wiring khác nhau; chúng cùng trả nội dung file ở UTF-8. `strip()` loại bỏ newline cuối file để output gọn.
9. `exists()` trả true vì resource được đóng gói ở `src/main/resources/demo/message.txt`.
10. `context.getResources("classpath*:demo/*.txt")` là API của `ResourcePatternResolver` mà ApplicationContext hỗ trợ. `anyMatch` kiểm tra filename để không phụ thuộc thứ tự resource array.

### Vì sao ví dụ không in số lượng resources?

Các phụ thuộc và deployment packages có thể đóng góp thêm resources match pattern. **Số phần tử và thứ tự** của `classpath*:` có thể phụ thuộc classpath. Ví dụ chỉ kiểm tra chính file `message.txt` có được tìm thấy, nên stdout có thể dự đoán từ source tree đã cung cấp.

### Nếu dùng java -jar?

Với cấu hình Maven trên, `exec:java` chạy từ classpath Maven khi build. Để triển khai một runnable JAR độc lập cần thêm quy trình đóng gói dependencies/manifest thích hợp. Nhưng về nguyên tắc, `ClassPathResource#getInputStream()` vẫn có thể đọc resource nằm bên trong JAR khi resource được đóng gói đúng. **Không thay bằng `getFile()`** nếu muốn giữ tính portable.

## 11. Constructor injection khi không dùng @Bean factory method

Trong bài thực tế, service có thể được đăng ký bằng component scanning hoặc explicit registration, và dependency resource được mô tả ngay tại constructor:

~~~java
@Component
public final class EmailTemplateService {

    private final Resource template;

    public EmailTemplateService(
            @Value("${mail.template.location}") Resource template) {
        this.template = template;
    }

    public String render() throws IOException {
        try (InputStream in = template.getInputStream()) {
            return new String(
                    in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
~~~

Đây là snippet, cần imports và cấu hình quét `@Component`. Pattern constructor injection giúp class test được bằng `new EmailTemplateService(new ByteArrayResource(...))`. Không cần một `@Bean Resource` riêng nếu chỉ cần chuyển path String sang descriptor cho một injection point.

### Khi nào field injection chấp nhận được?

Trong code legacy hoặc demonstration ngắn, `@Value` trên field có thể phù hợp. Nhưng constructor injection thường tốt hơn khi resource là bắt buộc vì final field, bất biến sau construction và không có trạng thái object chưa được tiêm.

### Setter injection

Nếu template là tùy chọn hoặc có default:

~~~java
public void setTemplate(Resource template) {
    this.template = template;
}
~~~

Đây là snippet setter. Nếu dùng XML/JavaBeans property wiring, Spring có thể convert chuỗi path thành Resource để truyền vào setter. Nếu dùng @Autowired trên setter, cần BeanDefinition Resource được lựa chọn phù hợp; nếu dùng @Value trên setter parameter, có thể cung cấp location String để Spring convert.

## 12. XML: cấu hình bằng String location nhưng setter nhận Resource

Một kiểu cấu hình truyền thống:

~~~java
public final class DocumentService {
    private Resource template;

    public void setTemplate(Resource template) {
        this.template = template;
    }
}
~~~

Đây là class minh họa. Trong XML BeanDefinition, có thể viết:

~~~xml
<bean id="documentService" class="example.DocumentService">
    <property name="template"
              value="classpath:documents/report.txt"/>
</bean>
~~~

XML snippet trên cần được đặt trong file `<beans>` có namespace/schema hợp lệ, và `DocumentService` cần nằm đúng package. Spring sử dụng conversion phù hợp để chuyển String location thành Resource khi set property.

Để lấy file ngoài JAR, đổi:

~~~xml
<property name="template"
          value="file:/etc/myapp/documents/report.txt"/>
~~~

Ứng dụng không phải đổi code service. Đây là lợi ích của injection abstraction: **quyết định file location thuộc cấu hình**, không rải trực tiếp vào business logic.

### Resource loader phụ thuộc loại ApplicationContext

Một location không prefix trong XML có thể được giải quyết khác nhau giữa `ClassPathXmlApplicationContext` và `FileSystemXmlApplicationContext`. Nếu muốn portable, viết prefix `classpath:` hoặc `file:` rõ ràng, không dựa vào context mặc định.

## 13. @Resource annotation KHÔNG đồng nghĩa Resource interface

Đây là lỗi dễ mắc nhất vì tên giống nhau.

- `org.springframework.core.io.Resource` là **interface tài nguyên/stream**.
- `jakarta.annotation.Resource` là **annotation JSR-250** cho dependency injection, thông thường gắn với **bean name**; không mặc định có ý nghĩa “đọc tệp từ classpath”.

Ví dụ Jakarta `@Resource`:

~~~java
import jakarta.annotation.Resource;

public class ReportService {
    @Resource(name = "reportRepository")
    private ReportRepository repository;
}
~~~

Đây là snippet, cần type `ReportRepository` và Jakarta annotation support của Spring. Nó tìm/inject bean tên `reportRepository` chứ không đi tìm file `reportRepository`.

Ngược lại, đọc một file từ classpath có thể dùng:

~~~java
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;

public class ReportTemplate {
    @Value("classpath:reports/template.txt")
    private Resource template;
}
~~~

Dù cả hai cùng từ “Resource”, chúng là hai khái niệm khác nhau. Nếu một file import đồng thời hai type có tên `Resource`, Java có xung đột tên ngắn; dùng fully qualified name cho một loại hoặc tổ chức code tách rõ.

### @Resource(name=...) có thể inject bean Resource?

Có: nếu bạn đã đăng ký một **bean có type Spring Resource**, ví dụ `@Bean("reportTemplateResource")`, thì `@Resource(name="reportTemplateResource")` có thể yêu cầu inject **bean ấy bằng tên**. Nhưng đây vẫn là bean lookup, không phải parser cho path `classpath:...`. Với Java 17/Spring 6.x dùng package `jakarta.annotation`, không phải `javax.annotation` của API cũ.

## 14. Resource không phải InputStream đã mở sẵn

Nếu service lưu một `ClassPathResource` hoặc `FileSystemResource` như field, thường nó chỉ lưu một descriptor. Nhiều lần gọi `getInputStream()` có thể mở nhiều luồng đọc khác nhau theo implementation.

Ngược lại, nếu tạo:

~~~java
InputStream stream = someExistingStream;
Resource resource = new InputStreamResource(stream);
~~~

đây là snippet; cần stream thực. `InputStreamResource` được thiết kế quanh stream nguồn, có hạn chế **chỉ đọc một lần** và không phù hợp như một descriptor dùng đi dùng lại trong singleton service.

Nếu nguồn data trong bộ nhớ và cần đọc lại:

~~~java
Resource resource = new ByteArrayResource(
        "hello".getBytes(StandardCharsets.UTF_8));
~~~

`ByteArrayResource` phù hợp để test và dữ liệu nhỏ. Nhưng đừng chứa bytes khổng lồ trong một singleton `ByteArrayResource` mà không đánh giá memory usage.

### Ai chịu trách nhiệm đóng stream?

Người **gọi `getInputStream()` và sử dụng stream** thường chịu trách nhiệm đóng stream khi xong:

~~~java
try (InputStream in = resource.getInputStream()) {
    // read hoặc stream sang parser
}
~~~

Không giữ InputStream thành Spring singleton bean chỉ để đọc nhiều request; stream lifetime và concurrency không phù hợp. Nếu thư viện nhận `InputStream`, mở đúng scope, truyền rồi đóng theo ownership của API.

### Resource và encoding

`getInputStream()` trả **bytes**; Spring Resource không tự đoán UTF-8/UTF-16/ISO-8859-1. Khi chuyển thành text, chỉ định charset rõ:

~~~java
new InputStreamReader(
        resource.getInputStream(), StandardCharsets.UTF_8);
~~~

Đoạn là snippet; phải quản lý đóng reader/stream. Nếu file template được đóng gói UTF-8 nhưng đọc theo charset mặc định của OS, dấu tiếng Việt có thể bị lỗi khi deploy.

## 15. Tiêm nhiều resources: Resource[] và patterns

Nếu ứng dụng cần đọc tất cả templates trong một thư mục:

~~~java
public final class TemplateBundle {
    private final Resource[] templates;

    public TemplateBundle(
            @Value("classpath*:templates/*.txt") Resource[] templates) {
        this.templates = templates;
    }
}
~~~

Đây là snippet. Spring Reference mô tả hỗ trợ conversion từ String pattern sang `Resource[]`. Cần bảo đảm resources nằm trong classpath và pattern phù hợp.

### Tránh phụ thuộc thứ tự mặc định

Nếu templates có precedence rõ, đừng duyệt resource array theo thứ tự ngầm để chọn bản cấu hình “thắng”. Tạo quy tắc như filename/version/priority metadata và sort phù hợp.

### Khi muốn đọc nhiều JAR

`classpath*:` có thể tìm resources ở nhiều classpath elements, kể cả JAR. Nhưng không bảo đảm mọi loại runtime loader hỗ trợ wildcard scanning giống nhau; cần kiểm thử trong container thực, đặc biệt khi dùng executable/fat JAR hoặc module path.

### Inject ResourcePatternResolver

~~~java
@Component
public class DictionaryLoader {
    private final ResourcePatternResolver resolver;

    public DictionaryLoader(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    public Resource[] scan() throws IOException {
        return resolver.getResources("classpath*:dictionary/*.txt");
    }
}
~~~

Đây là snippet, cần imports. Nếu nhu cầu chỉ là đọc một static file, inject `Resource` sẽ đơn giản hơn. Nếu mỗi request chọn một pattern mà input được user kiểm soát, cần allowlist và giới hạn số resources, không mở quét filesystem vô hạn.

## 16. Validation và fail fast cho resource bắt buộc

Tiêm được một `Resource` **không đồng nghĩa tệp tồn tại**. `ResourceLoader.getResource("classpath:missing.txt")` thường trả descriptor ngay, và lỗi thực xảy ra lúc `getInputStream()`.

Nếu file là bắt buộc để ứng dụng khởi động, có thể validate khi tạo một immutable settings/service hoặc trong init method được Spring điều phối:

~~~java
@Bean
ConfigTemplate configTemplate(
        @Value("${template.path}") Resource template)
        throws IOException {

    if (!template.exists() || !template.isReadable()) {
        throw new IOException("Required template is unavailable");
    }

    try (InputStream in = template.getInputStream()) {
        String content =
                new String(in.readAllBytes(), StandardCharsets.UTF_8);
        if (content.isBlank()) {
            throw new IllegalArgumentException(
                    "Required template must not be blank");
        }
        return new ConfigTemplate(content);
    }
}
~~~

Đây là snippet, cần `ConfigTemplate`, imports và configuration class. Nó chọn chính sách **load sớm một lần thành immutable content**. Cách này phù hợp với tài nguyên nhỏ, không thay đổi trong vòng đời service. Nếu resource lớn hoặc được phép refresh, cần thiết kế caching/streaming riêng.

Không nên lặp `exists()` rồi tin rằng `getInputStream()` chắc chắn thành công: file/network resource có thể biến mất giữa hai thao tác. Vẫn phải xử lý `IOException` ở bước mở/read thật (time-of-check/time-of-use).

### Optional resource

Nếu resource không bắt buộc, có thể dùng `exists()` để quyết định dùng fallback đã được phép. Nhưng fallback phải được tài liệu hóa; không lặng lẽ chạy với một cấu hình nguy hiểm khi file bắt buộc vắng mặt.

### Kiểm tra size

Nếu dùng `readAllBytes()`, một file quá lớn có thể gây memory pressure. Với resource có thể bị kiểm soát từ bên ngoài, giới hạn size trước hoặc trong quá trình đọc/parse, dùng streaming parser khi phù hợp. `contentLength()` không phải giới hạn bảo mật đáng tin cho nguồn remote hoặc stream một lần.

## 17. Resource location lấy từ người dùng: kiểm soát rủi ro

Không nhận một location từ HTTP request rồi chuyển thẳng tới:

~~~java
Resource resource = resourceLoader.getResource(userInput);
~~~

Đây là **phản ví dụ**, giả định `userInput` do client cung cấp. Vì `ResourceLoader` hiểu các prefix như `file:`, `classpath:` và URL, một location không được kiểm tra có thể mở tài nguyên nội bộ hoặc nguồn mạng không mong muốn, tùy runtime và quyền ứng dụng.

### Khuyến nghị

1. Nếu client chỉ được chọn một vài mẫu, dùng **ID/enum** như `WELCOME`/`INVOICE` rồi ánh xạ server-side sang resource path cố định.
2. Với filesystem paths, giới hạn vào base directory được phép, chuẩn hóa canonical paths và chống `../` traversal.
3. Với URL từ client, cần chính sách chống SSRF: allowlist destination, kiểm tra DNS/IP ranges, redirects, private networks và outbound traffic. Chỉ chặn chuỗi `"file:"` hoặc regex URL đơn giản là không đủ.
4. Giới hạn bytes, timeouts và số lượng file match wildcard.
5. Không log raw URL chứa credentials, tokens hoặc các đường dẫn nhạy cảm.
6. Không tải remote resource trong constructor khi điều đó gây startup I/O blocking, trừ khi đây là yêu cầu cấu hình bắt buộc đã có timeout/fail policy rõ.
7. Dùng dependency injection để policy path được kiểm soát tập trung trong configuration/service layer.

**Resource không phải security sandbox.** Nó là abstraction thuận tiện để truy cập các nguồn, và vẫn sử dụng quyền file/network của process JVM.

## 18. Unit test với ByteArrayResource

Một lợi ích mạnh của inject `Resource` trực tiếp là unit test không cần chạy container hay phụ thuộc filesystem:

~~~java
byte[] content =
        "Mẫu chào".getBytes(StandardCharsets.UTF_8);

Resource fake = new ByteArrayResource(content);
WelcomeService service = new WelcomeService(fake);

String actual = service.welcome();
if (!"Mẫu chào".equals(actual)) {
    throw new AssertionError(actual);
}
~~~

Đây là snippet dựa trên class `WelcomeService` ở mục 4, cần imports `ByteArrayResource`, `Resource`, `StandardCharsets`, cùng khai báo `throws IOException` hoặc try/catch. Nó cho thấy service chỉ phụ thuộc `Resource` contract.

### Unit test ResourceLoader

Nếu service phải chọn đường dẫn lúc chạy, có thể tự tạo một fake `ResourceLoader` trả `ByteArrayResource` cho một tập location đã biết. Không bắt buộc khởi tạo `ApplicationContext` chỉ để unit test logic chọn location.

### Integration test

Tạo `AnnotationConfigApplicationContext` với resource file dưới `src/test/resources` hoặc `src/main/resources`, rồi kiểm tra:

- `@Value("classpath:...") Resource` được inject.
- `@Bean Resource`/`@Qualifier` resolve đúng bean.
- `ResourceLoader`/`ResourcePatternResolver` được cung cấp đúng.
- Đọc text theo UTF-8 và đóng stream.
- Bắt lỗi missing file phù hợp.
- Đóng gói JAR rồi đọc file để phát hiện việc vô tình dùng `getFile()`.

Nếu có resource pattern trong nhiều JAR, integration test nên assert tập tên file kỳ vọng **theo nội dung/identity**, không assert iteration order không được bảo đảm.

## 19. Lỗi thường gặp và cách sửa

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `FileNotFoundException` với resource trong JAR | Dùng `getFile()` thay vì stream | Dùng `getInputStream()` |
| Chạy IDE được nhưng `java -jar` lỗi | Path `src/main/resources/...` chỉ đúng ở source tree | Đóng gói resource vào classpath, dùng `classpath:` |
| Resource injected nhưng `exists()` false | Đường dẫn sai/file chưa copy vào build output | Kiểm tra tệp resources và prefix |
| Resource location lấy nhầm nơi | Không có prefix, context-dependent interpretation | Ghi rõ `classpath:` hoặc `file:` |
| `classpath*:...` không trả một Resource | Đây là pattern nhiều resources | Dùng `Resource[]` hoặc `getResources` |
| `@Autowired Resource` bị ambiguity | Nhiều bean type Resource | Dùng `@Qualifier` hoặc `@Value` location |
| `@Resource` không tải file | Jakarta annotation thực hiện bean injection | Dùng Spring Resource + @Value |
| Lỗi class import `Resource` | Trộn `jakarta.annotation.Resource` với `org.springframework.core.io.Resource` | Kiểm tra import/fully qualified name |
| Bean dùng Resource trước field injection | Field chưa được set trong constructor | Dùng constructor injection |
| Service đọc stream lần hai lỗi | Dùng `InputStreamResource` một lần | Dùng `ClassPathResource`/`ByteArrayResource` hoặc tạo stream mới |
| File text bị mất dấu | Charset nền tảng khác UTF-8 | Chỉ định `StandardCharsets.UTF_8` |
| Đọc resource từ JAR như Path lỗi | Resource không có filesystem path | Dùng stream hoặc extract có kiểm soát |
| Nhiều templates nhưng thứ tự không ổn | Pattern order phụ thuộc classloader | Sort bằng explicit precedence key |
| Resource loader được inject nhưng business service phụ thuộc cả ApplicationContext | Coupling quá rộng | Inject Resource/ResourceLoader chuyên biệt |
| @Value placeholder không resolve | Key không tồn tại, source thêm muộn, PSPC setup | Add sources trước refresh, strict validation |
| Remote URL khiến startup chậm | Resource mở network/I/O quá sớm | Timeout, preload policy rõ, defer hợp lý |
| Path traversal/SSRF qua resource location | Dùng raw user-provided path/URL | Allowlist ID/paths/egress và giới hạn tài nguyên |
| Test dựa vào số resources chính xác | Classpath có thêm JAR modules | Kiểm tra file mục tiêu và policy |
| Bean có multiple Resource beans sai lựa chọn | Type injection không đủ phân biệt | Tên/qualifier rõ ràng |

## 20. Checklist thiết kế

- Resource có **đường dẫn cố định** hay được **chọn động**?
- Nếu cố định, đã inject trực tiếp `Resource` vào constructor/service?
- Nếu động, đã dùng `ResourceLoader` hoặc một adapter/interface nghiệp vụ?
- Nếu wildcard/`classpath*:`, đã chọn `ResourcePatternResolver`/`Resource[]`?
- Có phân biệt Spring `Resource` với Jakarta `@Resource`?
- Có khai báo `classpath:`/`file:` rõ ràng để tránh mơ hồ?
- Resource có được đưa vào đúng `src/main/resources` khi đóng gói?
- Có tránh dùng `getFile()` cho classpath resource bên trong JAR?
- Có dùng try-with-resources cho InputStream và charset đúng?
- Các stream có được mở lại đúng implementation hay là một lần?
- Khi có nhiều bean Resource, có dùng qualifier/name rõ?
- Có thêm PropertySources cần thiết trước refresh?
- Với file bắt buộc, đã có validation/fail-fast policy?
- Có tránh cho client đưa raw resource location khi không có validation?
- Có giới hạn kích thước, thời gian, số file và quyền truy cập nguồn?
- Có test chạy từ classpath thực/JAR và test missing resource?
- Có tránh phụ thuộc vào wildcard result order?
- Có dùng ByteArrayResource trong unit test để giảm phụ thuộc I/O?
- Với remote source, có thiết kế timeout/cache/cleanup thích hợp?
- Có quyết định rõ ownership giữa config factory và business consumer?

## 21. Câu hỏi tự kiểm tra

**Câu 1.** Nếu template path được chọn trước khi app khởi động và không đổi lúc runtime, nên tiêm `Resource` hay `ResourceLoader`?

**Đáp án:** Ưu tiên tiêm `Resource`. Service chỉ cần descriptor, dễ test và không phải tự resolve location.

**Câu 2.** Một service nhận path theo role của người dùng ở runtime nên dùng gì?

**Đáp án:** `ResourceLoader` hoặc một template repository abstraction với allowlist; không tự nhận raw paths chưa được kiểm tra.

**Câu 3.** Tại sao `getFile()` thường lỗi với resource trong JAR?

**Đáp án:** Entry bên trong JAR không phải file vật lý độc lập trên filesystem; `getInputStream()` hỗ trợ đọc qua classpath/JAR.

**Câu 4.** `classpath:` và `classpath*:` có giống nhau không?

**Đáp án:** Không. `classpath:` chọn một resource; `classpath*:` dùng tìm nhiều resources, thường qua `ResourcePatternResolver.getResources` hoặc injection `Resource[]`.

**Câu 5.** `@Value("${template.path}") Resource` có cần BeanDefinition Resource cùng tên không?

**Đáp án:** Không. Spring có thể resolve chuỗi location từ property và convert sang Resource tại injection point.

**Câu 6.** `@Qualifier("namedResource") Resource` có ý nghĩa gì?

**Đáp án:** Chọn bean Resource đã đăng ký với qualifier/name phù hợp, không parse một string path.

**Câu 7.** `jakarta.annotation.Resource` có phải cùng type với `org.springframework.core.io.Resource` không?

**Đáp án:** Không. Cái đầu là annotation cho bean injection; cái sau là resource abstraction của Spring.

**Câu 8.** Có thể đọc `InputStreamResource` nhiều lần như ClassPathResource không?

**Đáp án:** Không nên. InputStreamResource thường đại diện cho một stream đã mở, có giới hạn đọc một lần; nên dùng implementation phù hợp nếu cần reopen.

**Câu 9.** `Resource.exists()` true thì `getInputStream()` chắc chắn thành công chứ?

**Đáp án:** Không. File hoặc network state có thể đổi, quyền truy cập có thể bị từ chối; vẫn phải xử lý IOException.

**Câu 10.** Constructor nhận `Resource` sẽ tự đọc hết dữ liệu ngay khi bean tạo chứ?

**Đáp án:** Không. Resource thông thường là descriptor. Thời điểm mở stream do service quyết định.

**Câu 11.** Muốn test service mà không dùng file thật có thể làm gì?

**Đáp án:** Inject `ByteArrayResource` chứa dữ liệu giả, rồi gọi API nghiệp vụ và assert kết quả.

**Câu 12.** Có thể gửi `https://...` hoặc `file:...` từ người dùng thẳng vào ResourceLoader không?

**Đáp án:** Không nên. Cần allowlist/quyền truy cập, phòng SSRF/path traversal, giới hạn bytes/timeouts và chẩn đoán không lộ secrets.

## 22. Tham khảo chính thức

- [Spring Framework Reference — Resources](https://docs.spring.io/spring-framework/reference/core/resources.html)
- [Spring Framework Reference — Resources as Dependencies](https://docs.spring.io/spring-framework/reference/core/resources.html#resources-as-dependencies)
- [Spring Framework 6.2 Javadoc — Resource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/Resource.html)
- [Spring Framework 6.2 Javadoc — ResourceLoader](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/ResourceLoader.html)
- [Spring Framework 6.2 Javadoc — ResourcePatternResolver](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/support/ResourcePatternResolver.html)
- [Spring Framework 6.2 Javadoc — ClassPathResource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/ClassPathResource.html)
- [Spring Framework 6.2 Javadoc — InputStreamResource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/InputStreamResource.html)
- [Spring Framework Reference — Injection with @Resource](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/resource.html)
- [Spring Framework Reference — Using @Value](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-007">Đọc InputStream và đóng luồng đúng trách nhiệm</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc InputStream và đóng luồng đúng trách nhiệm trong Spring Core Container

## 1. Mục tiêu: đọc được dữ liệu nhưng không rò rỉ tài nguyên

Ứng dụng Java/Spring thường đọc dữ liệu từ classpath, tệp hệ thống, HTTP response, tài liệu trong JAR hoặc bộ nhớ. Các API này thường đưa về `java.io.InputStream`, một luồng **byte tuần tự**. Đọc xong nhưng không đóng đúng cách có thể gây rò rỉ file descriptor, socket, kết nối HTTP, bộ nhớ native, hoặc khiến một luồng không thể được dùng lại.

Vấn đề quan trọng nhất không chỉ là cú pháp `read()`: **ai mở luồng, ai sở hữu luồng và ai được phép đóng luồng?**

- Nếu method **tự gọi `Resource.getInputStream()`**, method đó thông thường **sở hữu stream mới mở** và phải đóng ngay khi dùng xong.
- Nếu method **được truyền một `InputStream` đã mở từ caller**, cần hợp đồng ownership. Nếu chỉ **mượn** để đọc, method **không được tự đóng**; caller vẫn quản lý vòng đời. Nếu API nói rõ **chuyển giao ownership**, callee được quyền/được yêu cầu đóng.
- Với stream được quản lý bởi một framework hoặc một scope khác (ví dụ input từ servlet request), cần tuân theo contract của framework đó; không mặc định một helper tùy ý có quyền đóng toàn bộ request.
- **`try-with-resources` là công cụ tốt nhất cho luồng do block hiện tại sở hữu**, nhưng dùng nó bên trong helper mượn luồng có thể vô tình đóng luồng mà caller còn cần.

Ví dụ ngắn cho luồng do method mở:

```java
Resource resource = new ClassPathResource("data/message.txt");
try (InputStream in = resource.getInputStream()) {
    byte[] content = in.readAllBytes();
    String text = new String(content, StandardCharsets.UTF_8);
}
```

Đây là **snippet tích hợp**, cần import `Resource`, `ClassPathResource`, `InputStream`, `StandardCharsets`, xử lý `IOException`, và `data/message.txt` phải có trên runtime classpath. `readAllBytes()` chỉ phù hợp khi **kích thước đã nhỏ/được kiểm soát**; với nội dung không tin cậy hoặc lớn cần giới hạn/streaming.

**Quy tắc thực hành:** mỗi `InputStream` phải có một **owner chịu trách nhiệm cleanup rõ ràng**. Không nên “ai cũng đóng”, cũng không nên “không ai đóng vì GC sẽ xử lý”.

## 2. InputStream là gì và những gì nó không bảo đảm

`InputStream` là abstract class trong `java.io` để đọc **bytes**:

```java
int b = in.read();                // 0..255 hoặc -1 nếu EOF
int n = in.read(buffer);          // số bytes đọc hoặc -1 ở EOF
byte[] five = in.readNBytes(5);   // tối đa 5 bytes, từ Java 9
byte[] all = in.readAllBytes();   // đọc tới EOF, có thể tốn RAM lớn
long skipped = in.skip(10);       // tối đa 10 bytes, có thể ít hơn
in.close();                       // đóng theo semantics implementation
```

Các dòng trên là **minh họa API**, không phải chương trình độc lập. Cần một instance `in` đã được mở, một `byte[] buffer` và xử lý `IOException`.

### 2.1. EOF khác với chuỗi rỗng

Khi gọi `read()` một byte, giá trị **-1** báo **end-of-stream**. Với overload `read(byte[])`, kết quả thường là số byte đã đọc, hoặc -1 khi EOF; không được diễn giải byte 0 (`0x00`) thành EOF.

Một tệp rỗng vẫn là một tài nguyên hợp lệ: `readAllBytes()` có thể trả mảng độ dài 0 mà không báo lỗi. Nếu nghiệp vụ cần nội dung không rỗng, đó là **validation** riêng, không phải đặc điểm của đóng stream.

### 2.2. Stream là byte stream, không phải String

Nếu dữ liệu là văn bản, phải biết charset như UTF-8:

```java
try (InputStream in = resource.getInputStream();
     Reader reader = new InputStreamReader(
             in, StandardCharsets.UTF_8)) {
    // Đọc character data từ Reader.
}
```

Đây là snippet tích hợp. `InputStreamReader` giải mã bytes thành ký tự. Khi đóng `reader`, nó thường đóng underlying `in`; block trên còn đóng `in` nữa, thường vô hại với các streams phổ biến, nhưng **không cần khai báo cả hai resources nếu chỉ giữ reader**. Cách gọn:

```java
try (Reader reader = new InputStreamReader(
        resource.getInputStream(), StandardCharsets.UTF_8)) {
    // reader owns its underlying stream and closes it.
}
```

Đoạn này cũng là snippet tích hợp. **Quan trọng:** nếu reader được tạo trên một stream **mượn**, đóng reader thường kéo theo đóng stream mượn đó — một lỗi ownership dễ bỏ sót.

### 2.3. Đừng giả định mọi stream có thể rewind

Nhiều input streams chỉ đọc **theo một chiều**. `markSupported()`, `mark(int)`, `reset()` tùy implementation và giới hạn read-ahead; không phải API rewind phổ quát.

Ví dụ `ByteArrayInputStream` thường có thể reset; network/socket input stream không tự nhiên có khả năng đó. Nếu cần đọc lại từ đầu, **mở stream mới từ source hỗ trợ repeatability**, hoặc chủ động buffer bytes trong giới hạn an toàn.

## 3. Spring Resource và hợp đồng mở stream

Spring Core có `org.springframework.core.io.Resource`, kế thừa `InputStreamSource`. Trọng tâm của interface:

```java
InputStream getInputStream() throws IOException;
```

Khi method gọi `resource.getInputStream()`, nó nhận một stream để đọc và **chịu trách nhiệm đóng stream ấy** sau khi đọc xong. Spring không tự đóng nó chỉ vì method return, không chờ ApplicationContext.close() để thu gom mọi stream ứng dụng đã mở.

Spring `InputStreamSource` thông thường kỳ vọng **mỗi lần gọi `getInputStream()` trả stream mới**, đặc biệt cần thiết với mail attachments/consumers có thể đọc nhiều lần. Nhưng **`InputStreamResource` là ngoại lệ một lần**, được thảo luận ở phần 7.

### 3.1. Resource không phải InputStream

```java
Resource source = new ClassPathResource("data/message.txt");
```

Câu trên chỉ tạo **descriptor**, không nhất thiết mở stream ngay. Khi cần đọc:

```java
try (InputStream in = source.getInputStream()) {
    // Read and consume here.
}
```

Đây là snippets tích hợp. Giữ `Resource` descriptor để tái sử dụng có thể hợp lý với các loại repeatable; giữ một `InputStream` mở làm field singleton rồi nhiều request cùng đọc là một thiết kế rủi ro.

### 3.2. Những loại Resource thường mở lại được

| Spring Resource | Nguồn | Hành vi mở stream cần nhớ |
|---|---|---|
| `ClassPathResource` | Resource trong classpath/JAR | `getInputStream()` thường mở một stream mới; đừng ép `getFile()` với JAR entry |
| `FileSystemResource` | Tệp filesystem | Có thể mở mới mỗi lần nếu tệp còn tồn tại và đọc được |
| `PathResource` | `java.nio.file.Path` | File/path-based, tạo stream theo API NIO |
| `UrlResource` | `file:`, `http:`, `https:` hoặc URL hợp lệ | Mỗi lần có thể kết nối/đọc lại, nhưng nội dung/mạng có thể thay đổi |
| `ByteArrayResource` | Byte array in-memory | Tạo `ByteArrayInputStream` mới mỗi lần; bytes gốc cần ổn định |
| `InputStreamResource` | Một stream đã có hoặc supplier single access | **One-shot**, `isOpen()=true`; không dùng cho retry/read lại |

**Repeatable không có nghĩa immutable.** Hai lần mở `FileSystemResource` có thể đọc hai phiên bản file khác nhau nếu ai đó sửa file ở giữa; `UrlResource` cũng có thể nhận response khác. Lặp lại được chỉ nói về **khả năng lấy stream**, không bảo đảm cùng bytes.

### 3.3. Đừng dùng getFile() làm cách đọc universal

Đoạn có thể hoạt động trong IDE:

```java
Path path = new ClassPathResource(
        "templates/email.txt").getFile().toPath();
```

Nhưng nó có thể thất bại khi resource nằm **trong JAR**, vì JAR entry không phải filesystem file riêng. Cách bền vững là:

```java
try (InputStream in = new ClassPathResource(
        "templates/email.txt").getInputStream()) {
    // Đọc trực tiếp từ stream, cả khi nội dung ở trong JAR.
}
```

Đây là snippets tích hợp. Khi library chỉ nhận `File`, cần thiết kế copy tạm **có giới hạn, quyền truy cập đúng và cleanup**, hoặc tìm overload nhận `InputStream`/URL.

## 4. Quy tắc ownership: nguồn tạo và quyền đóng

Các trường hợp phổ biến:

| Tình huống | Ai mở stream? | Ai đóng? |
|---|---|---|
| Helper tự gọi `resource.getInputStream()` | Helper | Helper, ngay trong `try-with-resources` |
| Helper tự gọi `Files.newInputStream(path)` | Helper | Helper |
| `readBorrowed(InputStream in)` được caller giao stream để **mượn** | Caller | Caller |
| Callee nhận stream với hợp đồng **take ownership** | Caller mở nhưng chủ động chuyển ownership | Callee, theo hợp đồng đã công bố |
| `InputStreamReader`/buffer wrapper do helper dựng trên stream mà helper sở hữu | Helper/wrapper | Đóng outermost wrapper (đóng underlying stream) |
| Wrapper tạo quanh **borrowed stream** | Caller sở hữu underlying | Không đóng wrapper kiểu thường nếu việc đó sẽ đóng borrowed stream, trừ khi contract cho phép |
| `System.in` | JVM/process | Thường không đóng trong helper thư viện; thuộc vòng đời process |
| Servlet/request stream | Servlet container/request infrastructure | Tuân theo framework API contract, không tự suy như stream file |
| HTTP response body stream từ client | HTTP client/response owner theo API | Đảm bảo response/stream được đóng theo contract để trả connection |
| Input stream dùng bởi một parser/SDK khác | Tùy API | Kiểm tra docs xem parser/SDK có tự đóng hay chuyển ownership không |

**“Người mở thì đóng” là nguyên tắc mặc định, không phải luật tuyệt đối.** Một API có thể **nhận chuyển giao ownership** của stream từ caller; khi đó callee cần đóng. Vì vậy hãy ghi contract một cách minh bạch trong method signatures/Javadoc hoặc đặt tên rõ như `readBorrowed`, `consumeAndClose`.

### 4.1. Helper mượn stream: không tự đóng

```java
public static byte[] readPrefix(
        InputStream borrowed, int maxBytes) throws IOException {

    if (maxBytes < 0) {
        throw new IllegalArgumentException("maxBytes must be >= 0");
    }
    return borrowed.readNBytes(maxBytes);
}
```

Đây là **method tích hợp** cần `InputStream` và `IOException`. Method chỉ dùng stream trong phạm vi lời gọi và **không đóng**. Caller có thể đọc phần còn lại hoặc đóng stream theo ownership của mình.

Caller:

```java
try (InputStream owned = Files.newInputStream(path)) {
    byte[] header = readPrefix(owned, 16);
    // Caller vẫn được quyền đọc phần tiếp theo từ owned.
}
```

Đây là snippet tích hợp yêu cầu `path` kiểu `Path`, import `Files`; caller mở và đóng stream. `readPrefix` đọc tối đa 16 bytes, nhưng có thể trả ít hơn nếu EOF đến trước.

### 4.2. Callee nhận ownership: nói rõ và dùng finally/try-with-resources

```java
public static byte[] consumeAndClose(
        InputStream ownedByThisMethod) throws IOException {

    try (InputStream in = ownedByThisMethod) {
        return in.readAllBytes();
    }
}
```

Đây là **method tích hợp** dành cho dữ liệu được bảo đảm nhỏ. Contract rõ: caller chuyển ownership sang callee; sau khi gọi, **không được kỳ vọng stream còn dùng được**, kể cả lúc có exception.

Không tự đổi API `readBorrowed` thành `consumeAndClose` trong một thư viện public chỉ vì bạn thích `try-with-resources`. Đó là thay đổi hành vi ảnh hưởng callers.

### 4.3. Wrapper close có lan xuống underlying stream không?

Các wrappers như `BufferedInputStream`, `InputStreamReader`, `GZIPInputStream` thông thường **đóng underlying stream khi wrapper được đóng**.

```java
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {

    String firstLine = reader.readLine();
}
```

Đây là snippet tích hợp; trong trường hợp này helper **tự mở** stream, nên đóng `reader` là đúng. Không cần đưa từng stream lồng nhau vào resource-list chỉ để gọi close nhiều lần.

Đối với **borrowed stream**, nếu muốn tạo wrapper mà không được đóng underlying, cần contract và thiết kế rõ. Có thể dùng wrapper không đóng underlying do thư viện cung cấp hoặc một adapter phù hợp; nhưng đừng áp cách này cho mọi tình huống vì đôi khi API caller muốn đóng khi xong.

### 4.4. Ownership ở boundary giữa các tầng

Khi method A gọi B và C:

```text
A: sở hữu Resource descriptor
A: mở InputStream mới
A: chuyển InputStream cho B mượn
B: đọc một phần, không đóng
A: chuyển lại stream cho C mượn
C: đọc phần còn lại, không đóng
A: đóng stream trong finally/try-with-resources
```

Cấu trúc này ổn định hơn khi B/C có contract “borrowed”. Nếu B tự đóng, C sẽ gặp stream đã đóng và trách nhiệm cleanup khó truy vết.

## 5. try-with-resources: cú pháp và exception behavior

Từ Java 7, **`try-with-resources`** tự đóng các resources implement `AutoCloseable` theo thứ tự ngược với lúc khai báo, kể cả khi block ném exception.

```java
try (InputStream in = resource.getInputStream()) {
    int firstByte = in.read();
    // Xử lý bytes.
}
```

Khi thoát block, Java gọi `in.close()` một lần trên resource được quản lý tại khai báo. Nếu `read()` ném `IOException`, block vẫn cố close stream.

### 5.1. Có thể khai báo nhiều resources

```java
try (InputStream in = Files.newInputStream(sourcePath);
     OutputStream out = Files.newOutputStream(targetPath)) {
    in.transferTo(out);
}
```

Đây là snippet tích hợp; cần imports `Files`, `InputStream`, `OutputStream` và các `Path` có thật. Khi thoát, Java đóng **`out` trước `in`**. Điều đó phù hợp với nhiều pipeline copy, nhưng nếu copy sang file nhạy cảm cần thêm quy tắc atomic write/path validation.

### 5.2. Exception trong block và exception khi close

Một lỗi ít được biết: nếu body ném exception A và `close()` cũng ném exception B, **A vẫn là exception chính**, còn B được gắn trong **`A.getSuppressed()`**. Nếu chỉ in `getMessage()` của A, có thể bỏ sót lỗi cleanup.

```java
try (AutoCloseable item = () -> {
    throw new IOException("close failed");
}) {
    throw new IOException("read failed");
} catch (IOException e) {
    System.out.println("primary: " + e.getMessage());
    System.out.println("suppressed: "
            + e.getSuppressed()[0].getMessage());
} catch (Exception e) {
    throw new RuntimeException(e);
}
```

Đây là **snippet Java 17 minh họa**, yêu cầu `IOException`, và compile trong một method có throws/exception handling phù hợp. Nó in:

```text
primary: read failed
suppressed: close failed
```

Với nhiều exceptions khi close, Java có thể lưu nhiều suppressed exceptions. Đừng im lặng loại bỏ toàn bộ cleanup errors trong đường xử lý cần độ tin cậy cao.

### 5.3. Khi nào không dùng try-with-resources?

Không dùng để tự đóng một stream **bạn chỉ mượn** nếu caller cần nó tiếp tục hoạt động.

Sai theo hợp đồng borrower:

```java
public static String firstLine(InputStream borrowed)
        throws IOException {

    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                    borrowed, StandardCharsets.UTF_8))) {
        return reader.readLine();
    }
}
```

Đây là **anti-example** cho API mượn stream, vì `reader.close()` đóng luôn `borrowed`. Nếu API thực sự nhận chuyển ownership và được đặt tên `readFirstLineAndClose`, code có thể đúng; lỗi là **không rõ hợp đồng**.

Một phương án phù hợp khi chỉ cần mượn bytes là đọc trực tiếp mà không đóng, rồi cho caller đóng.

## 6. Các cách đọc dữ liệu phổ biến

### 6.1. Đọc một byte hoặc buffer

```java
byte[] buffer = new byte[8192];
int n;

while ((n = in.read(buffer)) != -1) {
    if (n == 0) {
        continue;
    }
    process(buffer, 0, n);
}
```

Đây là **snippet tích hợp**, yêu cầu một `InputStream in` và method `process(byte[],int,int)`. Buffer reading tránh tạo một byte[] lớn cho toàn bộ nội dung. Khi `read` trả số bytes ít hơn kích thước buffer, **đó không có nghĩa EOF**; vẫn tiếp tục tới `-1`.

### 6.2. readAllBytes() chỉ cho nguồn nhỏ, tin cậy

```java
try (InputStream in = resource.getInputStream()) {
    byte[] bytes = in.readAllBytes();
}
```

Phù hợp cho vài KB hoặc một file size đã giới hạn bằng contract. Không dùng bừa bãi trên URL/user upload nhiều GB, vì có thể gây `OutOfMemoryError`. `available()` **không phải kích thước toàn bộ stream**, nên không dùng `in.available()` để quyết định kích thước cấp phát an toàn.

### 6.3. readNBytes(limit + 1) để phát hiện vượt giới hạn

Nếu nguồn không tin cậy nhưng bạn muốn trả về toàn bộ một văn bản nhỏ, có thể đọc tối đa **N+1 bytes**:

```java
public static byte[] readSmall(
        InputStream in, int maxBytes) throws IOException {

    if (maxBytes < 0 || maxBytes == Integer.MAX_VALUE) {
        throw new IllegalArgumentException("invalid maxBytes");
    }

    byte[] data = in.readNBytes(maxBytes + 1);

    if (data.length > maxBytes) {
        throw new IOException("input exceeds byte limit");
    }
    return data;
}
```

Đây là method tích hợp, cần import `InputStream`/`IOException`. Nó **không đóng `in`**: contract của helper là borrowed stream. Caller mở/đóng.

Đối với nguồn vô hạn hoặc mạng không phản hồi, **byte limit không thay thế timeout/cancellation**. Nếu `InputStream.read` có thể block, cần thêm giới hạn thời gian/transport configuration bên ngoài helper.

Đây cũng **không phải bộ giải mã UTF-8**. Byte limit đo **bytes**, trong khi độ dài Java `String` đo UTF-16 code units; một ký tự Unicode có thể dùng nhiều bytes. Khi decode, cần xử lý charset/invalid input riêng.

### 6.4. Đọc line-based text

```java
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {

    String line;
    while ((line = reader.readLine()) != null) {
        // Xử lý từng dòng có giới hạn phù hợp.
    }
}
```

Đây là snippet tích hợp. `BufferedReader.readLine()` bỏ phần line terminator. Nếu nghiệp vụ yêu cầu giữ nguyên CRLF/LF hoặc xử lý binary data, đọc từng dòng kiểu này sẽ **thay đổi representation**; dùng binary streaming khi cần giữ bytes nguyên vẹn.

**Lưu ý:** `readLine()` không tự giới hạn chiều dài một dòng. Một dòng nhiều GB cũng có thể làm tốn bộ nhớ dù code có vòng `while`; cần max line length hoặc streaming parser khi dữ liệu không tin cậy.

### 6.5. transferTo() để sao chép binary

```java
try (InputStream in = source.getInputStream();
     OutputStream out = Files.newOutputStream(target)) {
    long copied = in.transferTo(out);
}
```

Đây là snippet tích hợp với `source` kiểu `Resource`, `target` kiểu `Path` và imports cần thiết. `transferTo` tồn tại trong Java 9+; nó copy tới EOF, không tự giới hạn bytes và không tự đóng các stream chỉ vì được gọi. `try-with-resources` mới đảm nhiệm close.

Không dùng `transferTo` không giới hạn trên payload không tin cậy khi disk quota có thể bị lấp đầy; nên dùng vòng đọc buffer, đếm bytes và fail khi vượt quota.

## 7. InputStreamResource: ngoại lệ một-lần trong Spring

`InputStreamResource` bọc một `InputStream` đã có, hoặc một `InputStreamSource` để lấy stream lazy theo constructor được hỗ trợ. Đây là loại `Resource` **single-access**, không phải nguồn repeatable.

Ví dụ:

```java
InputStream owned = new ByteArrayInputStream(
        "payload".getBytes(StandardCharsets.UTF_8));

Resource oneShot = new InputStreamResource(owned);
System.out.println(oneShot.isOpen()); // true
```

Đây là snippet tích hợp. `oneShot.getInputStream()` được gọi tối đa **một lần** theo contract của implementation trong Spring 6.2. Sau đó một lần gọi nữa có thể ném `IllegalStateException`. Dù stream ban đầu là `ByteArrayInputStream` có khả năng reset, wrapper `InputStreamResource` vẫn biểu đạt **single-access resource descriptor**.

### 7.1. Ai đóng stream mà InputStreamResource bọc?

Nếu code của bạn chuyển `InputStreamResource` cho một API **nhận ownership và đảm bảo đóng stream sau khi đọc**, API đó có thể đóng stream. Nếu API **chỉ mượn hoặc không cam kết close**, người tạo underlying stream vẫn cần cơ chế bảo đảm cleanup.

Trong API tự viết, cách dễ kiểm soát nhất là method **mở Resource stream và đóng ngay trong `try-with-resources`**:

```java
public static String readResource(Resource r) throws IOException {
    try (InputStream in = r.getInputStream()) {
        return new String(
                in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

Đây là snippet tích hợp cho **nguồn nhỏ**, và với one-shot resource phải gọi **chỉ một lần**. Không giữ một `InputStreamResource` singleton để nhiều requests cùng lấy stream: request sau có thể không mở lại được.

### 7.2. contentLength() có thể tiêu thụ stream

Javadoc `InputStreamResource` lưu ý class này không override `AbstractResource.contentLength()` để cung cấp chiều dài độc lập. Gọi `contentLength()` trên đối tượng one-shot như vậy có thể **đọc hết stream** trước khi nghiệp vụ tiêu thụ nội dung.

```java
Resource r = new InputStreamResource(ownedStream);
long n = r.contentLength();   // Không phải metadata lookup vô hại!
```

Đây là **anti-example** khi bạn cần đọc payload sau đó; `ownedStream` phải được định nghĩa và cần xử lý `IOException`. Không gọi `contentLength()`, `readAllBytes()` rồi `getInputStream()` lại theo kỳ vọng resource tự rewind.

Nếu cần biết length: truyền length riêng từ transport/protocol, dùng resource có metadata đáng tin, hoặc buffer **với giới hạn** sang `ByteArrayResource` rồi tính length trên byte[]. Đừng chọn cách buffer nếu payload lớn.

### 7.3. ByteArrayResource và file-based Resource cho retry

Khi một consumer như mail attachment sender hoặc HTTP client có thể **đọc lại nguồn nhiều lần**, dùng resource mở stream mới:

```java
byte[] bytes = "repeatable".getBytes(StandardCharsets.UTF_8);
Resource reusable = new ByteArrayResource(bytes);

String a = readResource(reusable);
String b = readResource(reusable);
```

Đây là snippet tích hợp dựa trên helper `readResource` ở trên. `a.equals(b)` là true khi byte[] không đổi. `ByteArrayResource` tạo stream mới khi đọc, nhưng không mặc nhiên deep-copy dữ liệu đầu vào để bảo vệ khỏi caller sửa byte[] sau đó. Nếu cần immutability, copy defensively tại ranh giới ownership.

### 7.4. isOpen() là property của Resource descriptor

`Resource.isOpen()` **không có nghĩa file đang bị lock**. Nó cho biết descriptor có đại diện cho stream đã mở hay không, thường dùng để nhận biết khả năng đọc một lần.

`ClassPathResource.isOpen()` thường là false, nhưng điều đó không cản bạn mở một InputStream từ nó. `InputStreamResource.isOpen()` true kể cả khi chưa gọi `getInputStream()` trong constructor lazy-supplier variant; semantics là one-shot descriptor.

## 8. Ví dụ hoàn chỉnh Java 17+ Spring Core: ai đóng, ai mượn và stream mở lại

Demo dưới đây chạy **offline** với một classpath text file. Nó thể hiện:

1. Helper **tự mở** stream từ `ClassPathResource` và đóng bằng `try-with-resources`.
2. `ByteArrayResource` đọc hai lần thành công.
3. `InputStreamResource` đọc một lần; lần thứ hai bị từ chối theo contract.
4. Helper **mượn** một `InputStream`, đọc phần đầu và **không đóng**; caller đóng sau khi sử dụng.
5. Đọc quá byte limit bị chặn trước khi nạp toàn bộ payload lớn.
6. Kiểm chứng đóng luồng ngay cả khi parsing báo `IOException`.

### 8.1. Yêu cầu và cấu trúc

Yêu cầu: **JDK 17+**, **Maven 3.9+**, Spring Framework **6.2.0**.

```text
inputstream-ownership-demo/
├── pom.xml
└── src/main/
    ├── java/example/StreamOwnershipDemo.java
    └── resources/data/notice.txt
```

### 8.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>inputstream-ownership-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
            <version>6.2.0</version>
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
            </plugin>
        </plugins>
    </build>
</project>
```

### 8.3. src/main/resources/data/notice.txt

```text
hello from classpath
```

Có hoặc không có newline cuối file đều được; helper dùng `strip()` cho output ổn định. Nội dung ở đây là văn bản ngắn đã được ứng dụng đóng gói.

### 8.4. src/main/java/example/StreamOwnershipDemo.java

```java
package example;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

public class StreamOwnershipDemo {

    public static void main(String[] args) throws IOException {

        Resource packaged = new ClassPathResource("data/notice.txt");
        System.out.println("classpath: " + readOwned(packaged, 1024));

        Resource reusable = new ByteArrayResource(
                "repeatable".getBytes(StandardCharsets.UTF_8));
        String first = readOwned(reusable, 128);
        String second = readOwned(reusable, 128);
        System.out.println("byte-array repeat: " + first.equals(second));

        Resource once = new InputStreamResource(
                new ByteArrayInputStream(
                        "once".getBytes(StandardCharsets.UTF_8)));

        System.out.println("one-shot open: " + once.isOpen());
        System.out.println("one-shot first: " + readOwned(once, 128));

        try {
            readOwned(once, 128);
            System.out.println("one-shot second rejected: false");
        } catch (IllegalStateException expected) {
            System.out.println("one-shot second rejected: true");
        }

        var owner = new CloseAwareStream(new ByteArrayInputStream(
                "hello-rest".getBytes(StandardCharsets.UTF_8)));

        try (owner) {
            System.out.println("borrowed prefix: "
                    + readBorrowedPrefix(owner, 5));
            System.out.println("borrower closed stream: "
                    + owner.isClosed());
        }
        System.out.println("owner closed stream: " + owner.isClosed());

        try {
            readOwned(
                    new ByteArrayResource(
                            "abcdef".getBytes(StandardCharsets.UTF_8)),
                    5);
            System.out.println("limit rejected: false");
        } catch (IOException expected) {
            System.out.println("limit rejected: true");
        }

        var failureSource = new CloseAwareStream(
                new ByteArrayInputStream(
                        "too-long".getBytes(StandardCharsets.UTF_8)));

        try {
            readOwned(new InputStreamResource(failureSource), 3);
        } catch (IOException expected) {
            System.out.println("closed on failure: "
                    + failureSource.isClosed());
        }
    }

    // Chính method này mở stream từ Resource, nên cũng đóng.
    private static String readOwned(Resource resource, int maxBytes)
            throws IOException {

        if (maxBytes < 0 || maxBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid byte limit");
        }

        try (InputStream in = resource.getInputStream()) {
            byte[] bytes = in.readNBytes(maxBytes + 1);

            if (bytes.length > maxBytes) {
                throw new IOException("resource too large");
            }

            // Decode UTF-8 strict, thay vì âm thầm thay byte sai.
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString()
                        .strip();
            } catch (CharacterCodingException badText) {
                throw new IOException("invalid UTF-8", badText);
            }
        }
    }

    // Helper chỉ mượn stream, tuyệt đối không close nó.
    private static String readBorrowedPrefix(
            InputStream borrowed, int n) throws IOException {

        return new String(
                borrowed.readNBytes(n),
                StandardCharsets.UTF_8);
    }

    // Instrumentation xác nhận đúng thời điểm gọi close().
    private static final class CloseAwareStream
            extends FilterInputStream {

        private boolean closed;

        CloseAwareStream(InputStream delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                closed = true;
            }
        }

        boolean isClosed() {
            return closed;
        }
    }
}
```

### 8.5. Lệnh chạy

Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.StreamOwnershipDemo
```

**Stdout chính xác do application in** trong demo này (không kể Maven/Spring logging có thể xuất hiện trên môi trường người học):

```text
classpath: hello from classpath
byte-array repeat: true
one-shot open: true
one-shot first: once
one-shot second rejected: true
borrowed prefix: hello
borrower closed stream: false
owner closed stream: true
limit rejected: true
closed on failure: true
```

### 8.6. Giải thích chi tiết

1. `new ClassPathResource(...)` tạo descriptor cho `data/notice.txt`. `readOwned` tự gọi `getInputStream()`, nên `try (InputStream in=...)` đóng stream khi đã decode văn bản; vì dùng `strip()`, newline cuối file không làm stdout có dòng thừa.
2. `ByteArrayResource` có thể mở stream mới từ cùng bytes mỗi lần, nên `first.equals(second)` là true.
3. `InputStreamResource.isOpen()` true theo semantics descriptor single-access. Một lần `readOwned` mở/đọc/đóng stream; lần thứ hai `InputStreamResource.getInputStream()` ném `IllegalStateException`, được bắt có chủ đích.
4. `readBorrowedPrefix` nhận borrowed `InputStream`, chỉ gọi `readNBytes(5)` và không đóng. Biến `owner` cho thấy trạng thái close vẫn false bên trong `try`; sau khi thoát `try(owner)`, trạng thái true.
5. `"abcdef"` có 6 bytes; khi giới hạn là 5, `readNBytes(6)` phát hiện vượt limit và ném `IOException`. `readOwned` vẫn đóng stream.
6. `failureSource` là stream có cờ kiểm chứng close. `"too-long"` dài hơn 3 bytes nên `readOwned` ném `IOException`; `try-with-resources` vẫn gọi `close()`, khiến `closed on failure: true`.

**Vì sao catch `IllegalStateException` riêng?** Với `InputStreamResource` trong Spring 6.2, việc yêu cầu underlying stream lần thứ hai là lỗi về trạng thái sử dụng resource, không phải cùng một lỗi với việc đọc một tệp không tồn tại. Không nên nuốt mọi `RuntimeException` chỉ để luôn in true.

**Vì sao `readOwned` không dùng `readAllBytes()`?** Vì helper có trách nhiệm hạn chế memory: nó đọc tối đa `maxBytes+1` bytes để nhận biết input quá lớn. Đây là giới hạn **số bytes nạp vào RAM**, không phải timeout, tốc độ tải, giới hạn số dòng hay bảo vệ hoàn chỉnh trước payload nén có độ phình lớn.

**Vì sao decode UTF-8 strict?** Một `new String(bytes, UTF_8)` thông thường có thể thay những chuỗi bytes invalid bằng replacement characters. Với file cấu hình hoặc input nhạy cảm, việc phát hiện lỗi mã hóa sớm giúp tránh parsing sai. Nếu input chứa BOM hoặc định dạng khác UTF-8, cần có chính sách riêng.

### 8.7. Kịch bản mở rộng: đọc một phần rồi tiếp tục

Nếu caller muốn helper đọc phần header rồi parse payload còn lại, không chuyển ownership:

```java
try (InputStream whole = resource.getInputStream()) {
    byte[] header = whole.readNBytes(4);
    // check magic bytes của format
    parseRestBorrowed(whole); // đọc từ vị trí hiện tại, không close
}
```

Đây là snippet tích hợp, cần implement `parseRestBorrowed` và `resource` phù hợp. Tác dụng của contract borrower là caller vẫn có quyền điều phối nhiều bước trên **một stream liên tục**; `parseRestBorrowed` không được close `whole` ngoài thỏa thuận.

## 9. Resource mở lại được không đồng nghĩa InputStream cũ đọc lại được

Hai hành vi khác nhau:

```java
Resource source = new ClassPathResource("data/notice.txt");

try (InputStream one = source.getInputStream()) {
    // đọc one đến EOF
}

try (InputStream two = source.getInputStream()) {
    // two là stream mới; đọc lại từ đầu
}
```

Đây là snippet tích hợp với imports. Một số resources đáp ứng fresh-stream contract; không phải bạn có thể `one.read()` tiếp từ đầu sau EOF.

Với `InputStreamResource`, dù interface tên là Resource, implementation đó **không cho fresh stream mỗi lần**. Nếu consumer có thể retry, sign request, tính hash rồi upload, render template nhiều lần hoặc attach cùng content vào nhiều emails, **chọn loại repeatable** thay vì resource bọc stream có sẵn một lần.

### 9.1. Hashing rồi gửi nội dung

Sai nếu dùng một stream duy nhất:

```java
try (InputStream in = resource.getInputStream()) {
    byte[] digest = calculateDigest(in);
    upload(in); // in đã ở EOF; có thể upload 0 bytes.
}
```

Đây là anti-example, với methods `calculateDigest`, `upload` là minh họa. Nếu cần hai passes và source repeatable, mở **hai stream mới**; nếu một-shot, thiết kế một pass tính digest đồng thời chuyển bytes, hoặc buffer có giới hạn rõ.

### 9.2. available() và mark/reset

Không dùng `available()` làm kích thước tệp; nó chỉ là estimate bytes có thể đọc mà không block theo implementation, **không đảm bảo toàn bộ content length**.

Không mặc định `reset()` luôn được hỗ trợ. `markSupported()` quyết định; với một số buffered streams, reset có thể thất bại khi đọc vượt read-ahead limit. Khả năng đọc lại đáng tin hơn thường đến từ **Resource cung cấp stream mới**, không phải rewind một stream tùy ý.

## 10. Đóng đúng khi dùng HTTP, servlet và thư viện khác

### 10.1. HTTP response body

Đối với HTTP client, việc đóng response body/stream theo API thường **giải phóng hoặc hoàn trả tài nguyên connection pool**. Ví dụ với Java 17 `HttpClient` và `BodyHandlers.ofInputStream()`, caller nhận một stream body và cần đóng khi dùng xong:

```java
HttpResponse<InputStream> response = client.send(
        request, HttpResponse.BodyHandlers.ofInputStream());

try (InputStream body = response.body()) {
    // consume with byte/time limits
}
```

Đây là snippet tích hợp yêu cầu `HttpClient client`, `HttpRequest request`, imports `HttpResponse`, `InputStream`, xử lý `IOException`/`InterruptedException`, và URL request hợp lệ. Dùng timeout/request policy để tránh treo; đóng body stream theo contract của client đang dùng.

Nếu consumer không đọc hết body nhưng đóng sớm, mức độ tái sử dụng kết nối phụ thuộc HTTP client. Không tự khẳng định `close()` sẽ luôn reuse cùng TCP connection.

### 10.2. Servlet request InputStream

Một servlet request có `getInputStream()` từ container; method helper đọc payload có thể chỉ được **mượn** stream. Việc đọc/đóng có thể ảnh hưởng filter/controller ở các tầng tiếp theo; tùy Servlet API, framework và request lifecycle, cần chọn đúng điểm chịu trách nhiệm cleanup.

Không viết generic parsing utility vừa nhận `HttpServletRequest` vừa đóng body stream ngay cả khi caller muốn một filter khác xử lý tiếp. Nếu muốn giới hạn request body, hãy dùng request body handling và filters theo contract framework đã chọn.

### 10.3. Zip/GZIP wrappers

`GZIPInputStream` và `ZipInputStream` thường đóng underlying stream khi đóng wrapper. Với `ZipInputStream`, xử lý các entries bằng `getNextEntry()` và `closeEntry()` đúng cách; đóng wrapper kết thúc luồng nén.

Nếu compressed input không tin cậy, **giới hạn kích thước sau giải nén**, số entries, mức độ nesting và CPU/time để tránh zip bomb. Chỉ giới hạn kích thước **bytes nén** của underlying InputStream là chưa đủ.

### 10.4. XML/JSON parsers

Nhiều parser APIs nhận `InputStream`, và hợp đồng **ai đóng** khác nhau giữa libraries/options. Chỉ vì parser `parse(InputStream)` return không có nghĩa chắc chắn stream đã bị đóng; cũng không được giả định parser không đóng. Tốt nhất wrapper owner mở và đóng tại một boundary duy nhất, đồng thời đọc docs library. Với XML untrusted, bật protections chống external entity resolution; `Resource`/try-with-resources không giải quyết XXE.

## 11. Xử lý IOException, logging và shutdown

### 11.1. Bắt IOException ở ranh giới có chính sách

`Resource.getInputStream()`, `InputStream.read()` và `close()` có thể ném `IOException`. Không nên:

```java
try {
    return readOwned(resource, 1024);
} catch (IOException e) {
    return ""; // Sai: che giấu lỗi đọc file bằng dữ liệu rỗng.
}
```

Đây là anti-example, giả định helper và resource tồn tại. Trả chuỗi rỗng có thể khiến ứng dụng hiểu một tệp trống hợp lệ thay vì lỗi I/O; nếu dữ liệu bắt buộc, nên propagate hoặc chuyển thành exception domain rõ nghĩa.

Một cách xử lý hợp lý:

```java
try {
    return readOwned(resource, 1024);
} catch (IOException e) {
    throw new IllegalStateException(
            "Unable to load required configuration", e);
}
```

Đây là snippet tích hợp; exception giữ `cause` để truy nguyên. Với optional resource, fallback phải là quyết định có chủ đích và được kiểm thử.

### 11.2. Log đúng thông tin, tránh lộ bí mật

`Resource.getDescription()` có thể chứa path, URL và thậm chí query string với tokens. Đừng log raw body hoặc credentials trong lỗi đọc. Log **logical resource name**, kích thước dự kiến, loại lỗi và correlation ID nếu cần; redact thông tin nhạy cảm.

### 11.3. close() không phải lúc nào cũng “không làm gì”

Với file stream, close thường giải phóng file handle; với decompression/crypto/network wrappers, close có thể flush/finalize/release underlying resources hoặc phát sinh lỗi. `ByteArrayInputStream.close()` theo JDK thực tế không có tác dụng, nhưng **đừng dùng ngoại lệ của in-memory stream** để kết luận mọi InputStream đều có thể không đóng.

### 11.4. GC không thay trách nhiệm đóng

Garbage collector quản lý bộ nhớ Java heap, **không bảo đảm** giải phóng file descriptors/socket đúng thời điểm nghiệp vụ cần. Một số lớp Java có cleanup mechanisms trong quá trình reclaim, nhưng đó không phải ownership contract. Dùng `try-with-resources`/finally ở scope cụ thể.

## 12. Hiệu năng, bảo mật và giới hạn tài nguyên

| Rủi ro | Cơ chế phát sinh | Cách kiểm soát |
|---|---|---|
| File descriptor leak | Quên đóng `FileInputStream` hoặc resource stream | `try-with-resources`, code review |
| Connection pool exhaustion | Không đóng/tiêu thụ đúng HTTP response body | Tuân client API contract, timeout |
| OutOfMemoryError | `readAllBytes()` với payload lớn/không giới hạn | Streaming hoặc read cap N+1 |
| Blocking vô hạn | Đọc network stream không timeout | Connect/read timeout, deadline/cancellation |
| Invalid UTF-8 bị xử lý âm thầm | Decode dùng replacement behavior | Strict decoder khi dữ liệu yêu cầu đúng mã hóa |
| File bị đổi sau exists() | TOCTOU | Bắt IOException lúc mở/đọc |
| URL không tin cậy | SSRF/internal resources | Allowlist destination, check redirects/DNS/egress |
| Path traversal | Dùng filename/relative path từ người dùng | Validate logical IDs và canonical containment |
| Decompression bomb | Giới hạn kích thước nén nhưng không giới hạn giải nén | Giới hạn decompressed bytes/entries/CPU |
| Double close/ownership confusion | Nhiều tầng nghĩ mình là owner | Ghi contract, một boundary chịu trách nhiệm |
| One-shot retry failure | Giữ `InputStreamResource` rồi đọc lại | ByteArrayResource/file resource hoặc stream supplier |
| Data corruption | Đọc binary thành text sai charset/line endings | Dùng binary stream nếu cần bảo toàn bytes |

### 12.1. Giới hạn readNBytes không thay timeout

Giới hạn bytes giúp giảm RAM, nhưng một server có thể gửi **một byte mỗi phút**. `readNBytes(1025)` có thể chờ lâu trước khi hoàn thành. Với network source, thêm timeout ở HTTP client/transport, hủy operation/cancellation khi hết deadline, kiểm soát response content-length và actual bytes.

### 12.2. Đọc thẳng vào output thay vì buffer toàn bộ

Nếu mục tiêu chuyển một tệp lớn vào output, dùng stream copy từng chunk:

```java
public static long copyBounded(
        InputStream borrowedInput,
        OutputStream borrowedOutput,
        long maxBytes) throws IOException {

    if (maxBytes < 0) {
        throw new IllegalArgumentException("negative limit");
    }

    byte[] buffer = new byte[8192];
    long total = 0;

    while (true) {
        int n = borrowedInput.read(buffer);
        if (n < 0) {
            return total;
        }
        if (n == 0) {
            continue;
        }
        if (n > maxBytes - total) {
            throw new IOException("copy would exceed byte limit");
        }
        borrowedOutput.write(buffer, 0, n);
        total += n;
    }
}
```

Đây là **method tích hợp** cần `InputStream`, `OutputStream`, `IOException`. Nó **mượn cả input và output**, nên **không đóng** chúng; caller quản lý lifecycle. Nó có thể ném sau khi đã ghi một phần output; khi copy sang file nghiệp vụ cần transaction-like temp file + atomic replace nếu dữ liệu phải all-or-nothing.

Để giảm CPU bị chiếm bởi stream implementation luôn trả `0` dù `len>0` (không tuân lối đọc thông thường), có thể thêm chính sách đếm số lần đọc bằng 0, cancellation hoặc timeout ở tầng adapter. Đừng dùng vòng vô hạn unbounded cho một source không đáng tin.

### 12.3. Kiểm tra checksum không đồng nghĩa quyền đọc vô hạn

Hashing có thể làm streaming không nạp toàn bộ file vào RAM, nhưng vẫn đọc toàn bộ bytes tới EOF, vì vậy có nguy cơ chiếm thời gian/băng thông nếu input không giới hạn. Giới hạn kích thước và thời gian vẫn cần thiết.

## 13. Những lỗi thường gặp

| Lỗi / hiểu nhầm | Điều đúng | Cách sửa |
|---|---|---|
| “Spring ApplicationContext sẽ đóng mọi stream mà service đã mở” | Không có contract tổng quát đó | Đóng tại scope sở hữu stream |
| “Mọi InputStream parameter nên bọc try-with-resources” | Có thể là borrowed stream | Làm rõ ownership, chỉ đóng nếu nhận ownership |
| “Nếu method nhận Resource thì caller phải đóng stream do callee mở” | Callee mở `getInputStream()` thường chịu trách nhiệm đóng | Cài try-with-resources trong callee |
| “Đóng BufferedReader không liên quan underlying stream” | Thường nó đóng underlying reader/input stream | Không đóng wrapper trên borrowed stream tùy tiện |
| “Resource descriptor là một stream đã mở” | Phần lớn Resource chỉ mô tả nguồn | Mở khi cần, đóng mỗi stream cụ thể |
| “`isOpen=false` nghĩa là không có stream nào” | Nó liên quan one-shot descriptor semantics | Hiểu đúng API |
| “`ClassPathResource.getFile()` chạy được khi đóng JAR” | JAR entry không luôn có physical file | Dùng `getInputStream()` |
| “`InputStreamResource` dùng đọc lại được nếu stream gốc là ByteArrayInputStream” | Wrapper vẫn single-access | Dùng `ByteArrayResource` |
| “contentLength() chỉ lấy metadata, không đọc nội dung” | Trên InputStreamResource có thể consume stream | Tránh gọi khi nguồn one-shot |
| “readAllBytes() luôn tiện và an toàn” | Có thể nạp dữ liệu khổng lồ | Cap bytes hoặc stream theo chunk |
| “available() bằng file length” | Không phải | Không dùng làm max size |
| “read(buffer) ít bytes hơn buffer nghĩa là EOF” | Partial reads là bình thường | Lặp tới -1 |
| “try-with-resources nuốt lỗi trong body khi close lỗi” | Body exception là primary, close được suppressed | Kiểm tra `getSuppressed()` |
| “Một `readLine()` đọc rất nhiều dòng vẫn có memory cố định” | Một dòng quá dài có thể tốn nhiều RAM | Áp max line length |
| “Đổi file trong JAR không cần rebuild/deploy” | JAR resource thường immutable artifact | Dùng external config hoặc rebuild |
| “Đọc UTF-8 bằng charset mặc định luôn đúng” | Default charset/cấu hình có thể khác | Chỉ rõ `StandardCharsets.UTF_8` |
| “Mọi source đều repeatable” | Có one-shot network/input resource | Phân biệt resource type/contract |
| “catch IOException rồi trả empty string luôn tốt” | Che lỗi I/O thành dữ liệu hợp lệ | Propagate/wrap/fallback rõ ràng |
| “GC sẽ đóng kịp mọi file handle” | Không có bảo đảm đúng thời điểm | Ownership và deterministic close |

## 14. Cách thiết kế API dễ bảo trì

Đặt tên method thể hiện semantics:

```java
// API sở hữu stream do tự mở từ descriptor:
String readSmallText(Resource source, int maxBytes) throws IOException;

// API chỉ mượn; caller vẫn sở hữu:
byte[] readHeader(InputStream borrowedInput, int length) throws IOException;

// API nhận chuyển giao stream và tự đóng:
void consumeAndClose(InputStream ownedInput) throws IOException;
```

Đây là **những chữ ký minh họa**, không phải class Java đầy đủ.

Nếu consumer có thể retry, API nhận `Resource` repeatable hoặc một supplier:

```java
@FunctionalInterface
public interface InputStreamOpener {
    InputStream open() throws IOException;
}
```

Đây là snippet interface Java, cần import `InputStream`, `IOException`. Mỗi `open()` phải tạo một stream thuộc ownership của caller nhận stream. Nếu implementation lại trả cùng một stream đã mở thì nó **không** thỏa hợp đồng fresh-stream mà một retrying consumer cần. Với Spring, `InputStreamSource` là abstraction đã có cho ý tưởng tương tự.

### 14.1. Đặt ownership trong tài liệu API

Ví dụ JavaDoc:

```java
/**
 * Reads a prefix without closing the supplied stream.
 * The caller retains ownership and must close the stream.
 *
 * @param borrowed input stream positioned at the next unread byte
 * @return up to the requested number of bytes
 */
```

Đây là đoạn **JavaDoc minh họa**. Tương tự, nếu callee đóng stream, ghi rõ `takes ownership` trong API contract.

### 14.2. Tránh pass-around stream quá xa

Nếu một singleton mở stream lúc startup rồi chuyển vào nhiều components, ownership dễ bị mất dấu. Giải pháp thường tốt hơn là inject `Resource`/supplier, để mỗi operation mở stream mới với lifecycle ngắn; hoặc đọc dữ liệu nhỏ thành một **immutable object** lúc bootstrap rồi inject object ấy vào consumers.

Khi nguồn thật sự là one-shot (HTTP upload), tạo một pipeline rõ có một owner duy nhất, truyền borrowed stream giữa các stages mà không yêu cầu rewind/retry tùy tiện.

## 15. Checklist review và bài tập

### 15.1. Checklist trước khi merge

1. Code có biết **stream đến từ đâu**? Mở bằng `getInputStream()` hay do caller truyền?
2. Owner nào phải gọi `close()`? API có nói rõ transfer hay borrow?
3. Có `try-with-resources`/finally quanh luồng mà code tự mở không?
4. Có wrapper `Reader`/`GZIPInputStream` vô tình đóng borrowed stream không?
5. Có thử `getFile()` trên một classpath resource có thể nằm trong JAR không?
6. Có dùng `InputStreamResource` cho một consumer cần retry/multiple reads không?
7. Có gọi `contentLength()` trên one-shot stream trước khi đọc không?
8. Có giới hạn bytes sau giải nén, thời gian, số dòng và memory cho input không tin cậy không?
9. Text có decode đúng charset, xử lý invalid bytes/BOM theo contract không?
10. Có nuốt `IOException` và biến nó thành giá trị rỗng không?
11. Khi read và close cùng lỗi, suppressed exceptions có được giữ/quan sát không?
12. Tests có xác nhận close xảy ra **cả khi đọc thành công và thất bại** không?
13. Có test packaged JAR/runtime classpath thực tế, không chỉ IDE không?
14. Có log path/URL/bytes có khả năng chứa secrets không?
15. Có bảo vệ against path traversal/SSRF khi location đến từ input người dùng không?

### 15.2. Bài tập 1: borrower tiếp tục đọc

Tạo một `ByteArrayInputStream` chứa `"headerBODY"`. Helper đọc 6 bytes đầu và **không đóng**, caller đọc phần còn lại; kỳ vọng header = `header`, phần còn lại = `BODY`. Sau đó caller đóng stream.

### 15.3. Bài tập 2: giới hạn kích thước

Dùng `ByteArrayResource` với 10 bytes, gọi helper bounded limit 9 và 10. Kỳ vọng limit 9 bị từ chối; limit 10 đọc thành công. Không dùng `available()` để tìm kích thước.

### 15.4. Bài tập 3: one-shot

Tạo `InputStreamResource` bọc một stream, gọi `getInputStream()` một lần rồi đọc/đóng, sau đó thử lần hai. Trên Spring Framework 6.2, lần hai bị từ chối theo contract. Thay bằng `ByteArrayResource` và thử lại; hai lần thành công.

### 15.5. Bài tập 4: close khi lỗi

Tạo một `FilterInputStream` ghi nhận `close()`. Đọc qua wrapper nhưng cố tình ném `IOException` giữa chừng. Dùng `try-with-resources` để xác nhận cờ close vẫn true; sau đó viết một test về suppressed exception khi cả body và close đều lỗi.

### 15.6. Bài tập 5: resource đóng JAR

Đưa `sample.txt` vào `src/main/resources`, chạy từ IDE rồi đóng JAR. Kiểm tra cách `getInputStream()` dùng được ở cả hai, trong khi `getFile()` có thể thất bại khi file là entry trong archive.

## 16. Câu hỏi tự kiểm tra

**1. `Resource.getInputStream()` trả về gì?**  
Một `InputStream` để đọc nội dung resource; caller nhận stream thường phải đóng sau khi dùng.

**2. Nếu method tự mở stream bằng `Files.newInputStream`, ai đóng?**  
Method đó, thường qua `try-with-resources`.

**3. Nếu một helper chỉ được truyền borrowed InputStream, có luôn được đóng không?**  
Không. Hợp đồng borrower nghĩa là caller vẫn sở hữu và sẽ đóng.

**4. Tại sao `BufferedReader.close()` có thể vô tình đóng borrowed InputStream?**  
Vì nó thường đóng underlying `InputStreamReader`, rồi reader đóng luôn stream bên dưới.

**5. `InputStreamResource` có đọc lại nhiều lần được không?**  
Không theo contract Spring 6.2; nó là one-shot và `getInputStream()` lần hai bị từ chối.

**6. Tại sao `ByteArrayResource` phù hợp khi cần repeatable source?**  
Vì mỗi lần `getInputStream()` có thể tạo stream mới trên byte[] được cung cấp.

**7. `contentLength()` trên one-shot InputStreamResource có thể nguy hiểm thế nào?**  
Có thể tiêu thụ stream để tính length, khiến tác vụ đọc chính không còn dữ liệu.

**8. Vì sao `getFile()` không an toàn với resource trong JAR?**  
Vì entry trong archive không phải filesystem file riêng.

**9. Khi có IOException ở body và ở close, exception nào là primary?**  
Exception ở body; close exception được gắn vào `getSuppressed()`.

**10. `readAllBytes()` phù hợp với input nào?**  
Input nhỏ đã tin cậy và được giới hạn; không nên dùng unbounded với network/user uploads lớn.

**11. `available()` có cho biết kích thước toàn bộ file không?**  
Không. Nó không phải content length API.

**12. `readNBytes(limit+1)` giúp gì?**  
Đọc tối đa limit+1 bytes để nhận biết payload vượt hạn mà không nạp toàn bộ.

**13. Đóng stream có tự hủy Spring ApplicationContext hoặc Resource descriptor không?**  
Không; đó là các vòng đời khác nhau. Đóng stream mở không đồng nghĩa context.close().

**14. Làm sao thử đúng luồng có được đóng trong đường exception?**  
Dùng wrapper `FilterInputStream` ghi nhận close, cố tình ném IOException rồi assert cờ close.

**15. `Resource.isOpen()` có nghĩa file đang bị lock không?**  
Không. Nó mô tả khả năng descriptor đại diện stream đang mở, thường one-shot.

**16. Khi caller cần retry upload, nên truyền gì?**  
Nguồn mở stream mới (`Resource` repeatable/`InputStreamSource` đúng contract) hoặc supplier rõ ràng, không đưa cùng one-shot stream và hy vọng rewind.

## Tài liệu tham khảo chính thức

- [Spring Framework Reference — Resources](https://docs.spring.io/spring-framework/reference/core/resources.html): Resource, InputStreamSource, getInputStream và trách nhiệm đóng của caller.
- [Spring Framework 6.2 API — Resource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/Resource.html): stream, metadata và isOpen.
- [Spring Framework 6.2 API — InputStreamSource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/InputStreamSource.html): mỗi lần getInputStream thường tạo fresh stream, repeatability.
- [Spring Framework 6.2 API — InputStreamResource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/InputStreamResource.html): single-use resource và contentLength pitfalls.
- [Spring Framework 6.2 API — ByteArrayResource](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/ByteArrayResource.html): in-memory repeatable resource.
- [Java SE 17 API — InputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html): read, readNBytes, readAllBytes, transferTo, close.
- [Java SE 17 Guide — try-with-resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html): auto-close order và suppressed exceptions.
- [Java SE 17 API — StandardCharsets](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/charset/StandardCharsets.html): UTF-8 và các charset chuẩn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-008">Tài nguyên trong JAR không nhất thiết chuyển được thành File</a>

<details>
<summary>Xem chi tiết</summary>

# Tài nguyên trong JAR không nhất thiết chuyển được thành File — Spring Framework Core Container

## 1. Vấn đề thực tế: chạy trong IDE được, đóng gói JAR lại lỗi

Một ứng dụng Spring Framework có tệp `src/main/resources/templates/welcome.txt`. Trong IDE hoặc khi chạy bằng Maven với `target/classes` trên classpath, đoạn sau có thể hoạt động:

```java
Resource resource = new ClassPathResource("templates/welcome.txt");
File file = resource.getFile(); // Có thể chạy khi resource là file trong target/classes.
```

Sau khi đóng gói thành JAR và chạy trên server, chương trình lại có thể ném `FileNotFoundException`, với thông báo đại ý:

```text
class path resource [templates/welcome.txt]
cannot be resolved to absolute file path because it does not reside in the file system
```

Tệp **vẫn tồn tại** trong classpath và vẫn đọc được. Vấn đề không nằm ở việc Spring quên đóng gói tài nguyên, mà nằm ở giả định sai: **một entry bên trong tệp nén JAR không phải một `java.io.File` riêng biệt trên hệ thống tệp**.

Cách đọc portable:

```java
Resource resource = new ClassPathResource("templates/welcome.txt");
try (InputStream in = resource.getInputStream()) {
    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    // Xử lý text.
}
```

Đây là fragment Java, cần imports `Resource`, `ClassPathResource`, `InputStream`, `StandardCharsets` và phương thức khai báo/handle `IOException`. Chương trình hoàn chỉnh, build/test ở cả classpath directory và packaged JAR được trình bày ở mục 9.

**Mệnh đề cần ghi nhớ:** `Resource.exists() == true` hoặc `getInputStream()` thành công **không đồng nghĩa** `getFile()` có thể trả về một file thật. Đây là nguyên tắc nền tảng khi thiết kế ứng dụng Java/Spring có thể triển khai dưới nhiều hình thức.

## 2. Ba khái niệm khác nhau: Resource, File và JAR entry

### 2.1. Resource là mô tả một nguồn dữ liệu

Spring `org.springframework.core.io.Resource` là abstraction cho tài nguyên. Nó có thể là classpath entry, local file, URL hoặc nguồn khác. Vì vậy API có thể cung cấp:

- `getInputStream()`: mở luồng đọc nội dung.
- `getURL()`/`getURI()`: xác định vị trí/tài nguyên theo URL/URI khi implementation hỗ trợ.
- `getFile()`: cung cấp `File` **chỉ khi có thể ánh xạ tài nguyên sang file hệ thống**.
- `exists()`, `isReadable()`, `isFile()`: cung cấp các tín hiệu trạng thái/khả năng.
- `contentLength()`, `lastModified()`, `getFilename()`, `getDescription()`: metadata có tính khả dụng khác nhau giữa implementations.

Không phải mọi implementation hỗ trợ mọi thao tác. Contract `Resource.getFile()` cho phép ném `FileNotFoundException` nếu resource không thể resolve thành **đường dẫn file tuyệt đối**.

### 2.2. java.io.File chỉ đại diện đường dẫn filesystem

`File` không chứa byte dữ liệu và không phải bộ giải nén JAR. Một đối tượng `new File("/opt/app/report.txt")` chỉ biểu diễn pathname trong filesystem của process. Sự tồn tại của object `File` không chứng minh tệp đó thật sự tồn tại, đọc được hoặc có quyền truy cập.

Nếu JVM đang đọc `classpath:templates/welcome.txt` từ:

```text
/myapp/
  target/
    classes/
      templates/
        welcome.txt
```

thì classpath URL có thể là `file:/.../target/classes/templates/welcome.txt`. Ở hình thức đó, có một file thực và `getFile()` có thể thành công.

### 2.3. JAR entry không phải file rời

Sau packaging:

```text
myapp.jar
├── META-INF/
│   └── MANIFEST.MF
├── com/example/Main.class
└── templates/
    └── welcome.txt
```

Entry `templates/welcome.txt` là nội dung bên trong archive `myapp.jar`. Với JDK class loader phổ biến, URL có thể trông như:

```text
jar:file:/opt/myapp/myapp.jar!/templates/welcome.txt
```

Chuỗi `!` đánh dấu ranh giới giữa JAR bên ngoài và entry bên trong; đó **không phải** pathname của một file độc lập mà `new File(...)` có thể mở trực tiếp. Java/JAR handler hoặc class loader biết cách đọc entry, nhưng `java.io.File` không có semantics “phần tử trong ZIP”.

**Vậy:** có thể `resource.exists() == true`, `resource.getURL()` trả một `jar:` URL, và `resource.getInputStream()` đọc đúng nội dung; trong khi `resource.getFile()` ném exception. Các kết quả này hoàn toàn nhất quán.

## 3. Contract Resource: API nào bảo đảm điều gì?

Giao diện `Resource` kế thừa `InputStreamSource`. Một số method quan trọng:

```java
// Chữ ký được rút gọn để giải thích; không cần tự định nghĩa interface.
InputStream getInputStream() throws IOException;
boolean exists();
boolean isReadable();
boolean isOpen();
boolean isFile();
URL getURL() throws IOException;
URI getURI() throws IOException;
File getFile() throws IOException;
long contentLength() throws IOException;
String getFilename();
String getDescription();
```

| Phép thử | Tài nguyên trong classpath directory | Tài nguyên trong JAR chưa giải nén |
| --- | --- | --- |
| `exists()` | Thường true nếu có file | Thường true nếu có entry |
| `isReadable()` | Thường true khi mở được | Thường true nếu đọc được từ archive |
| `getInputStream()` | Đọc từ file | Đọc từ JAR entry |
| `getURL().getProtocol()` | Thường `file` | Thường `jar` với Java classpath thông thường |
| `isFile()` | Thường true | Thường false |
| `getFile()` | Có thể trả `File` | Thường `FileNotFoundException` |
| `getFilename()` | `welcome.txt` | `welcome.txt` |
| `contentLength()` | Có thể lấy từ file | Có thể có từ archive/stream, tùy implementation |

Bảng là **hành vi điển hình**, không thay hợp đồng API. Với custom class loader, nested archives, servlet containers hoặc module resources, URL protocols và khả năng cung cấp `File` có thể khác. Vì vậy không viết business logic giả định `getURL().getProtocol()` luôn chính xác là `jar` mỗi khi deploy.

### Tại sao isReadable() không thay getFile()

`isReadable()` chỉ nói resource có vẻ đọc được qua abstraction, không nói nó là filesystem file. Một entry trong JAR có thể readable nhưng không file-backed. Tương tự, `isFile()` thường giúp đánh giá trước, nhưng không loại trừ **race condition**: file có thể biến mất hoặc quyền truy cập thay đổi trước thao tác thật.

Nếu mục tiêu là **đọc nội dung**, đừng gọi `isFile()` để quyết định có được đọc hay không; hãy **mở stream** và xử lý `IOException`.

### getURL(), getURI() cũng không phải File

```java
URL url = resource.getURL();
URI uri = resource.getURI();
```

Cả hai có thể biểu diễn `jar:`, `file:`, `https:` hoặc URL custom. Câu lệnh sau **không phải phép biến đổi tổng quát**:

```java
Path path = Path.of(resource.getURI());
```

Với `jar:...!/entry`, mặc định filesystem provider có thể không xử lý URI đó trực tiếp. `Path.of(URI)` không phải “phép giải nén JAR”; những Java NIO filesystem provider dành cho archive phải được tạo/quản lý riêng, có các giới hạn riêng. Không dùng chiến thuật này để chữa mọi lỗi `getFile()`.

## 4. ClassPathResource hoạt động nhờ ClassLoader như thế nào?

Spring `ClassPathResource` dùng class loader hoặc một `Class<?>` để tìm resource. Ví dụ:

```java
Resource a = new ClassPathResource("templates/welcome.txt");

Resource b = new ClassPathResource(
        "templates/welcome.txt",
        Thread.currentThread().getContextClassLoader());

Resource c = new ClassPathResource(
        "/templates/welcome.txt",
        Main.class);
```

Ba dòng là fragments; `Main` phải tồn tại và resource phải nằm ở vị trí tương ứng. Constructor dựa trên `ClassLoader` thường tìm từ classpath root; constructor dựa trên `Class<?>` có thể resolve đường dẫn tương đối với package của class, còn leading `/` làm đường dẫn absolute trong classpath.

Java trực tiếp cũng có cơ chế tương tự:

```java
try (InputStream in = Main.class.getResourceAsStream(
        "/templates/welcome.txt")) {
    if (in == null) {
        throw new FileNotFoundException(
                "Missing classpath resource: templates/welcome.txt");
    }
    // Đọc bytes.
}
```

Đây là Java fragment; `Class.getResourceAsStream()` trả null nếu không tìm thấy resource. `ClassPathResource.getInputStream()` thay vào đó ném `IOException` phù hợp khi không có resource. Cả hai có thể hoạt động với JAR.

### URL dạng jar không phải một URL file bình thường

Java URL handler biết cách mở:

```text
jar:file:/somewhere/application.jar!/templates/welcome.txt
```

nhưng `new File(url.getPath())` không chuyển path này thành file của entry. Tương tự, không nên dùng string parsing với `!` để chế ra `File` hoặc suy cấu trúc bên trong nested JAR: Boot loader và app servers có thể có URL form riêng.

Class loader abstraction được thiết kế để che sự khác nhau giữa classpath directory và archive. Cách portable là **để class loader/Spring Resource mở stream**, không suy ra đường dẫn filesystem.

### Không hardcode src/main/resources khi chạy

Tại build time:

```text
src/main/resources/templates/welcome.txt
```

Nhưng đường dẫn tại runtime là:

```text
classpath:templates/welcome.txt
```

Không dùng `new File("src/main/resources/templates/welcome.txt")` để đọc resource runtime: cách này chỉ tình cờ hoạt động khi process làm việc tại project root và source tree còn trên máy. Sau khi deploy artifact, folder source đó thường không tồn tại.

## 5. Nguyên nhân lỗi thường gặp khi đóng gói và chạy JAR

### 5.1. Dùng getFile() để cung cấp API nhận File

```java
ClassPathResource resource =
        new ClassPathResource("templates/welcome.txt");
File input = resource.getFile();
thirdPartyParser.parse(input);
```

Đoạn này là **anti-pattern minh họa** vì `thirdPartyParser` chưa định nghĩa; ngay cả khi `getFile()` chạy được ở local, không bảo đảm chạy trong JAR.

**Sửa ưu tiên:** tìm overload như `parse(InputStream)`, `parse(Reader)`, `parse(URL)` hoặc `parse(byte[])`. Chỉ khi thư viện bắt buộc nhận `File` mới cần copy resource ra file tạm.

### 5.2. Đưa ClassPathResource.getURI() sang Files.readString

```java
Path path = Paths.get(
        new ClassPathResource("config/defaults.json").getURI());
String json = Files.readString(path);
```

Fragment này **không portable** trong JAR. Có thể gặp `FileSystemNotFoundException`, `ProviderNotFoundException` hoặc lỗi URI/đường dẫn tùy environment. Không bắt exception rồi tự mặc định có một path cùng tên bên cạnh JAR — đó thường không phải cùng resource.

Sửa:

```java
Resource resource = new ClassPathResource("config/defaults.json");
try (InputStream in = resource.getInputStream()) {
    String json = new String(
            in.readAllBytes(), StandardCharsets.UTF_8);
}
```

Đây là fragment; với JSON lớn dùng streaming parser thay vì readAllBytes.

### 5.3. Dùng getResource().getPath() rồi new File

```java
URL url = Main.class.getResource("/config/defaults.json");
File file = new File(url.getPath());
```

Không an toàn:
- URL có thể là `jar:` chứ không phải `file:`.
- `URL.getPath()` có thể chứa percent-encoding; dấu cách trong directory path gây sai nếu chuyển chuỗi thô.
- Nested JAR protocol không theo quy tắc path OS.
- Với resource không tồn tại, `getResource(...)` trả `null`; tiếp tục gọi `getPath()` sẽ NPE.

Nếu mục tiêu chỉ đọc, dùng `getResourceAsStream()` hoặc `Resource.getInputStream()`. Nếu chắc URL thật sự `file:` và có file vật lý, xem API `getFile()`/`Path.of(uri)` trong ngữ cảnh cụ thể và xử lý lỗi.

### 5.4. Lẫn working directory với classpath root

```java
new File("templates/welcome.txt");
new ClassPathResource("templates/welcome.txt");
```

Dòng đầu tìm tương đối **working directory**, dòng sau tìm qua **classpath**. Chúng chỉ trùng nhau ngẫu nhiên khi developer tổ chức thư mục/bối cảnh chạy đặc biệt.

### 5.5. Quên đóng gói resource

Nếu `getInputStream()` thực sự báo không tìm thấy, đây là **vấn đề khác** với `getFile()` trên một JAR entry đã tồn tại. Kiểm tra nội dung JAR, build resource filtering và classpath path:

```bash
jar tf target/resource-jar-demo-1.0.0.jar
```

Lệnh liệt kê entries trong artifact mẫu ở mục 9. Kiểm tra tên `demo-data/message.txt`, không lấy `src/main/resources` làm prefix khi đọc runtime. Nếu không có entry thì cần sửa cấu hình build, không phải đổi `getFile()` thành API khác rồi mong resource xuất hiện.

## 6. Giải pháp chuẩn: InputStream, Reader và streaming parser

### 6.1. Text nhỏ

```java
public static String readUtf8(Resource resource) throws IOException {
    try (InputStream in = resource.getInputStream()) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

Cần imports `Resource`, `InputStream`, `IOException` và `StandardCharsets`. Dùng cho template, config nhỏ và static text đã biết kích thước. `readAllBytes()` tiêu thụ bộ nhớ theo kích thước resource, không dùng tùy tiện cho multi-GB file hoặc remote URL không tin cậy.

### 6.2. Reader cho text lớn

```java
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
    String line;
    while ((line = reader.readLine()) != null) {
        processLine(line);
    }
}
```

Fragment cần imports và method `processLine(String)` tự định nghĩa. Nó xử lý theo dòng và đóng cả reader/stream. Việc dùng UTF-8 phải phù hợp với encoding của resource; không để platform default charset quyết định ngẫu nhiên.

### 6.3. Binary resources

```java
try (InputStream in = resource.getInputStream()) {
    // Truyền stream cho parser phù hợp với binary format.
    imageDecoder.decode(in);
}
```

Đây là pseudocode minh họa; `imageDecoder` không được khai báo. Không chuyển ảnh, ZIP, PDF hoặc private key thẳng sang `String` chỉ vì API trả `InputStream`. Hãy dùng thư viện chuyên dụng, giới hạn kích thước/định dạng và bảo mật dữ liệu.

### 6.4. Khi nào URL thay InputStream?

Một số thư viện có overload nhận `URL`; `ClassPathResource.getURL()` có thể trả URL trỏ vào JAR entry. Đôi khi thư viện biết cách đọc `jar:` URL và hoạt động; đôi khi nó cố convert thành file và vẫn lỗi. **Đọc contract của thư viện**, không coi `URL` tự bảo đảm tương thích với mọi parser.

## 7. Nếu API bên thứ ba bắt buộc nhận File: trích xuất có kiểm soát

Đây là ngoại lệ hợp lệ: thư viện legacy hoặc native library chỉ nhận **đường dẫn file**. Khi đó phải có một file vật lý, nên có thể **copy resource ra một file tạm**.

```java
public static Path copyToTemp(Resource resource) throws IOException {
    Path temp = Files.createTempFile("embedded-resource-", ".dat");
    boolean success = false;
    try {
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        success = true;
        return temp;
    } finally {
        if (!success) {
            Files.deleteIfExists(temp);
        }
    }
}
```

Đoạn trên là **fragment** cần imports `Files`, `Path`, `InputStream`, `IOException`, `StandardCopyOption`, `Resource`. Caller chịu trách nhiệm cleanup file sau khi thư viện hoàn tất.

Sử dụng:

```java
Path temp = copyToTemp(
        new ClassPathResource("models/model.dat"));
try {
    library.open(temp.toFile());
} finally {
    Files.deleteIfExists(temp);
}
```

Đây là pseudocode do `library` chưa định nghĩa. Nếu thư viện giữ open file handle hoặc background thread cần file sau khi method trả về, **không xóa ngay**. Khi đó lifecycle file tạm phải theo lifecycle thư viện, không đơn thuần theo scope một method.

### Vì sao temp copy không hoàn toàn miễn phí?

- **Dung lượng:** cần disk space tương đương resource; nhiều workers có thể tạo nhiều bản sao.
- **Quyền:** thư mục temp có chính sách permission khác nhau theo OS/container.
- **Thông tin nhạy cảm:** resource chứa certificate/private data có thể cần file permissions, ephemeral encrypted storage và cleanup nghiêm ngặt.
- **I/O:** đọc từ JAR và ghi ra disk làm tăng startup time.
- **Consistency:** file được tạo ra là **một bản sao**, không còn liên hệ cập nhật tự động với JAR entry.
- **Cleanup:** `deleteOnExit()` không thay được lifecycle management, đặc biệt với server process chạy nhiều ngày.
- **Race/symlink:** không tự dựng tên temp bằng ghép chuỗi dự đoán; dùng `Files.createTempFile(...)` và chỉ thao tác file do process sở hữu.
- **File suffix:** một số thư viện cần extension nhất định để đoán format; chọn suffix phù hợp, nhưng không dùng suffix thay validation nội dung.

**Ưu tiên giải pháp khác khi có thể:** thư viện đọc stream, Reader, URL hoặc byte array thường ít rủi ro và portable hơn.

## 8. Nested JAR, Spring Boot executable JAR và class loaders đặc biệt

Spring Boot executable JAR thường có cấu trúc đặc biệt:

```text
my-service.jar
├── BOOT-INF/
│   ├── classes/
│   │   └── templates/welcome.txt
│   └── lib/
│       ├── spring-core-....jar
│       └── other-dependency.jar
└── org/springframework/boot/loader/...
```

Tài nguyên ứng dụng có thể nằm trong `BOOT-INF/classes`, còn resource của dependency nằm bên trong **JAR lồng JAR** dưới `BOOT-INF/lib`. Boot loader/class loader giải quyết URLs, classpath và nested JAR semantics cho runtime. Một entry có thể đọc qua `ClassPathResource.getInputStream()` mà không hề có file riêng ở OS path.

**Không** suy rằng URL của Boot nested JAR luôn giống đúng chuỗi chuẩn `jar:file:/app.jar!/entry`. URL format, loader và nested archive layout phụ thuộc version Boot và cấu hình packaging. Tập trung vào **hợp đồng Resource**, không parse dấu `!` để tự truy đường dẫn bên trong archive.

### Có phải Spring Boot tự giải getFile()?

Không. Spring Boot hỗ trợ load class và resources từ executable JAR, nhưng **không biến mọi nested entry thành java.io.File thực**. Code application dựa vào `getFile()` trên classpath resource vẫn có thể fail sau packaging, dù launcher đã tìm thấy resource và stream đọc bình thường.

### WAR/app-server cũng có tình huống tương tự

Khi deploy WAR:
- Container có thể **unpack** web archive, khiến một số resource trở thành file vật lý.
- Container khác có thể truy cập archive trực tiếp, khiến `getFile()` không khả dụng.
- `ServletContextResource`, `ClassPathResource`, URL handlers có semantics riêng.

Viết code portable trên `Resource.getInputStream()`/API streaming, không dựa vào hành vi unpack của một server cụ thể.

### Fat JAR và shaded JAR

Một shaded/uber JAR có thể gộp classes/resources từ dependencies vào cùng archive, trong khi Boot nested JAR giữ nhiều thư viện lồng trong artifact. Cả hai đều không bảo đảm mỗi resource có một tệp độc lập trên disk.

Với classpath resource trùng tên giữa nhiều JAR, class loader có thể trả một bản đầu tiên tùy thứ tự classpath. Namespace resource theo package hoặc module rõ ràng, test packaged artifact thực.

## 9. Demo hoàn chỉnh Java 17+ và Maven: chạy trên folder rồi trên JAR

Demo được thiết kế để **chứng minh thực nghiệm cùng một source Java và resource file**:

- Khi chạy từ `target/classes`, resource thường có URL protocol `file` và `getFile()` thành công.
- Khi chạy từ **JAR chứa resource**, không đặt `target/classes` trên classpath, resource thường có protocol `jar` và `getFile()` không khả dụng.
- Cả hai chế độ đều đọc nội dung qua `getInputStream()` và copy ra temp file thành công.

Yêu cầu: JDK 17+, Maven 3.9+ và dependency `spring-core:6.2.18`. Đây là ví dụ Spring **Core** thuần, không cần Spring Boot, web server, database hoặc network runtime. Maven có thể cần kết nối Maven Central **lần đầu** để tải dependencies, sau khi chúng đã cache việc chạy app không cần Internet.

### 9.1. Thư mục

```text
resource-jar-demo/
├── pom.xml
└── src/main/
    ├── java/edu/example/jarresource/
    │   └── Main.java
    └── resources/demo-data/
        └── message.txt
```

**pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>edu.example</groupId>
    <artifactId>resource-jar-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-core</artifactId>
            <version>6.2.18</version>
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
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.2</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <version>3.7.1</version>
            </plugin>
        </plugins>
    </build>
</project>
```

**src/main/resources/demo-data/message.txt**

```text
hello-from-resource
```

Đặt đúng nội dung `hello-from-resource`. Có thể có newline cuối file; code dùng `strip()` để output ổn định. Khi build `mvn package`, Maven copy resource dưới root của JAR thành `demo-data/message.txt`.

**src/main/java/edu/example/jarresource/Main.java**

```java
package edu.example.jarresource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public final class Main {
    public static void main(String[] args) throws IOException {
        Resource resource =
                new ClassPathResource("demo-data/message.txt");

        System.out.println("exists: " + resource.exists());
        System.out.println("filename: " + resource.getFilename());
        System.out.println("content: " + readUtf8(resource));
        System.out.println("URL protocol: "
                + resource.getURL().getProtocol());
        System.out.println("isFile: " + resource.isFile());

        try {
            resource.getFile();
            System.out.println("getFile: available");
        } catch (IOException ex) {
            // Không in exception message chứa absolute paths.
            System.out.println("getFile: unavailable");
        }

        // Trích xuất có kiểm soát khi một thư viện legacy cần File.
        Path temp = Files.createTempFile("jar-resource-demo-", ".txt");
        try {
            try (InputStream input = resource.getInputStream()) {
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("temp content: "
                    + Files.readString(temp, StandardCharsets.UTF_8).strip());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String readUtf8(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(),
                    StandardCharsets.UTF_8).strip();
        }
    }
}
```

### 9.2. Build đúng artifact và copy runtime dependencies

Trong thư mục chứa `pom.xml`:

```bash
mvn -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime
```

Sau lệnh này có:
- `target/classes/edu/example/jarresource/Main.class`.
- `target/classes/demo-data/message.txt`.
- `target/resource-jar-demo-1.0.0.jar`.
- `target/dependency/` chứa dependency runtime như `spring-core`, `spring-jcl`.

Dùng `jar tf` để chắc chắn JAR có resource:

```bash
jar tf target/resource-jar-demo-1.0.0.jar
```

Trong output danh sách có entry `demo-data/message.txt`. Lệnh `jar tf` in nhiều entry, vì vậy không tuyên bố “chỉ có một dòng” trong kết quả.

### 9.3. Chế độ A — chạy từ classpath directory

**Linux/macOS:**

```bash
java -cp "target/classes:target/dependency/*" edu.example.jarresource.Main
```

**Windows PowerShell/cmd:**

```powershell
java -cp "target/classes;target/dependency/*" edu.example.jarresource.Main
```

Dấu phân cách classpath là `:` trên Unix, `;` trên Windows. Cả hai command đều đặt `target/classes` trên classpath, chứa file vật lý `demo-data/message.txt`.

**Các dòng stdout của chương trình trong trường hợp classpath directory thông thường:**

```text
exists: true
filename: message.txt
content: hello-from-resource
URL protocol: file
isFile: true
getFile: available
temp content: hello-from-resource
```

Đây là tình huống mà developer dễ vô tình kết luận `getFile()` luôn hợp lệ.

### 9.4. Chế độ B — chỉ chạy app class từ JAR, KHÔNG thêm target/classes

**Linux/macOS:**

```bash
java -cp "target/resource-jar-demo-1.0.0.jar:target/dependency/*" edu.example.jarresource.Main
```

**Windows PowerShell/cmd:**

```powershell
java -cp "target/resource-jar-demo-1.0.0.jar;target/dependency/*" edu.example.jarresource.Main
```

Quan trọng: **không** thêm `target/classes` vào `-cp` của chế độ B. Nếu cả directory và JAR cùng tồn tại trên classpath, class loader có thể tìm resource ở directory trước, tạo kết quả sai cho mục tiêu kiểm chứng.

**Các dòng stdout của chương trình khi dùng Java classpath/JAR handler thông thường:**

```text
exists: true
filename: message.txt
content: hello-from-resource
URL protocol: jar
isFile: false
getFile: unavailable
temp content: hello-from-resource
```

So sánh hai output:
- `exists` và `content` **không đổi**.
- `getFile` đổi từ available sang unavailable.
- `temp content` **không đổi**, vì copy qua `InputStream` vẫn hoạt động.
- `URL protocol` minh họa cách ClassLoader giải resource ở từng loại classpath, không phải điều kiện nên hardcode cho mọi custom loader.

### 9.5. Vì sao chương trình chạy portable?

1. `ClassPathResource` không phụ thuộc working directory để tìm `demo-data/message.txt`.
2. `getInputStream()` là API chung cho cả filesystem-backed resource và JAR entry.
3. `getFile()` được đặt trong `try/catch` chỉ để **chứng minh sự khác nhau**, không được dùng để đọc nội dung bắt buộc.
4. `Files.createTempFile(...)` tạo tệp riêng với tên do hệ thống quản lý, không phụ thuộc absolute path và không in path ra stdout.
5. Tệp tạm được xóa ở `finally` sau khi đọc xong.
6. `strip()` làm output không lệ thuộc việc resource text có newline cuối file.
7. Runtime dependencies được cung cấp riêng qua classpath wildcard, không cần tạo executable fat JAR hay Spring Boot loader.
8. Ví dụ không dùng external URL/network I/O lúc chạy class main.

**Giới hạn kiểm chứng:** Output `file`/`jar` nêu trên đúng với cách chạy bằng JDK URLClassLoader/JAR handler thông thường và artifact dựng theo cấu trúc trên. Một runtime custom module loader hoặc shaded/nested archive có thể tạo URL khác. Bất biến cần dựa vào là **stream đọc được nhưng File không được bảo đảm**.

## 10. Khi chỉ cần kiểm tra JAR có đóng gói resource hay không

Dùng JDK `jar` command:

```bash
jar tf target/resource-jar-demo-1.0.0.jar
```

Khi cần phân tích việc đóng gói thực:
- Xác nhận path entry có đúng chữ hoa/chữ thường.
- Xác nhận file nằm trong `src/main/resources` hoặc mục Maven resources đã được cấu hình.
- Nếu có resource filtering, kiểm tra file không bị thay/loại bởi profile build.
- Kiểm tra artifact cuối mà process thực sự đang chạy, không chỉ một JAR ở `target` có thể không được deploy.
- Với Spring Boot executable JAR, application resource thường ở `BOOT-INF/classes/...`; class loader được Boot cung cấp sẽ giải từ đó.
- Với WAR, đường dẫn trong `WEB-INF/classes`/libraries và cách unpack phụ thuộc servlet container.
- Nếu classpath có nhiều versions của cùng file, kiểm tra class loader ordering và tránh collision.
- Đừng chỉ dựa vào absolute path in từ local IDE để suy runtime path của Docker image.

Một cách thêm vào unit test:

```java
Resource input = new ClassPathResource("demo-data/message.txt");
if (!input.exists()) {
    throw new AssertionError("Missing classpath resource");
}
try (InputStream stream = input.getInputStream()) {
    if (stream.read() == -1) {
        throw new AssertionError("Classpath resource is empty");
    }
}
```

Đây là test fragment, cần imports và resource từ ví dụ. Nhưng **test chạy trên target/classes chưa chứng minh behavior packaged JAR**, vì vậy nếu mục tiêu là phát hiện lỗi `getFile()`, cần chạy mode B trong CI hoặc chạy packaged artifact thật.

## 11. Phân biệt JAR resource với tệp bên cạnh JAR

Hai vị trí khác nhau:

```text
/opt/myapp/
  app.jar
  external/
    settings.properties
```

- `classpath:settings.properties` tìm resource trong classpath (chỉ có nếu classpath chứa nó).
- `new FileSystemResource(Path.of("/opt/myapp/external/settings.properties"))` tìm file từ filesystem.
- `new File("./external/settings.properties")` phụ thuộc JVM working directory; không mặc nhiên tương đối với nơi đặt `app.jar`.
- `Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI())` có thể chỉ tới **JAR chứa class**, không phải tài nguyên bên trong; và trong một số môi trường URL có thể đặc biệt/không file-backed.

Đừng lấy JAR path rồi `resolve("config.txt")` và kỳ vọng nó tìm được entry trong JAR. Nếu cần file có thể thay đổi mà không rebuild artifact, đặt ngoài JAR theo một đường dẫn cấu hình rõ ràng (mounted volume, file URL, platform config service), dùng `FileSystemResource`/`Path` tương ứng.

### Classpath resource có sửa trực tiếp được không?

Classpath/JAR resources thường là **đầu vào chỉ đọc**. Nếu bạn muốn lưu thay đổi config hay dữ liệu người dùng, đừng tìm cách ghi vào JAR entry đã deploy bằng `getFile()`. Ghi ra local data directory, database, object storage hoặc config management service; muốn thay nội dung đóng gói thì build/deploy artifact mới.

### Đừng tự giải nén cả JAR không cần thiết

Chỉ vì một thư viện cần một resource dưới dạng `File` không có nghĩa cần giải nén **toàn bộ ứng dụng**. Chọn:
1. Dùng stream/URL overload.
2. Nếu không có, copy **chính resource cần thiết** ra temp với lifecycle rõ.
3. Chỉ lựa chọn unzip nhiều resources khi bài toán thực sự đòi cấu trúc thư mục và có chính sách bảo mật, dung lượng, cleanup rõ ràng.

## 12. Các lựa chọn khác: ByteArrayResource, FileSystemResource và PathResource

Không phải mọi tài nguyên đều nằm trong archive:
- `ClassPathResource`: phù hợp resources **đóng gói** theo classpath, portable qua JAR.
- `FileSystemResource`: phù hợp file vật lý được deploy/mount riêng.
- `PathResource`: phù hợp khi cần xử lý dựa trên `java.nio.file.Path` thuần, có semantics riêng cho `createRelative`.
- `ByteArrayResource`: phù hợp nếu bytes đã nằm trong RAM, cần mở InputStream mới nhiều lần.
- `InputStreamResource`: bọc stream đang mở, thường **chỉ dùng một lần**; không dùng như descriptor có thể đọc lặp.

```java
Resource packaged =
        new ClassPathResource("demo-data/message.txt");

Resource external =
        new FileSystemResource(Path.of("/srv/app/config.txt"));

Resource inMemory =
        new ByteArrayResource("demo".getBytes(StandardCharsets.UTF_8));
```

Đây là fragments, đường dẫn Unix chỉ minh họa và cần imports. Cùng một API `getInputStream()` có thể đọc được cả ba khi data tồn tại. Nhưng `getFile()`:
- Có thể có đối với `FileSystemResource` và `PathResource` file-backed.
- Không bảo đảm với `ClassPathResource` (tùy classpath location).
- Không hợp lệ với `ByteArrayResource` vì không có một file OS tương ứng.

**Bài học tổng quát:** thiết kế của Resource cố ý cho phép tài nguyên “có byte nhưng không có file”. JAR chỉ là trường hợp dễ gặp nhất, không phải ngoại lệ duy nhất.

## 13. Path, URL encoding và ZipFileSystem: vì sao không tự chuyển đổi?

### 13.1. URL có ký tự mã hóa

Nếu path JAR nằm dưới thư mục có dấu cách, một URL có thể chứa `%20`. Dùng `new File(url.getPath())` trực tiếp không xử lý đầy đủ URI encoding và protocol. Nếu URL là `file:` thật, có thể dùng `Path.of(url.toURI())` trong code có xử lý `URISyntaxException`/provider, nhưng đó là **nhánh file-backed cụ thể**, không áp dụng được cho `jar:`.

### 13.2. JDK ZipFileSystem có ích nhưng không phải getFile()

Java NIO `FileSystems.newFileSystem(...)` có thể mount ZIP/JAR như một filesystem ảo trong một số trường hợp. Khi đó một `Path` có thể đại diện entry bên trong archive. Tuy nhiên:
- Nó không biến entry thành `java.io.File` độc lập.
- `Path.toFile()` thường không được hỗ trợ bởi non-default filesystem provider.
- Cần quản lý `FileSystem.close()` và provider ownership.
- Nested JAR/custom loaders có thể không tương thích trực tiếp.
- Nếu archive đang được process dùng và nhiều callers cùng mount, lifecycle/quyền ghi có thể phức tạp.

Vì vậy với bài toán **đọc một classpath resource**, ZipFileSystem là giải pháp quá phức tạp so với `getInputStream()`. Chỉ dùng NIO ZIP filesystem khi thật sự cần thao tác nhiều entries trong archive và đã kiểm tra đúng runtime.

### 13.3. java.nio.file.Files chỉ làm việc với Path thích hợp

`Files.readAllBytes(path)`, `Files.lines(path)` hoặc `Files.newInputStream(path)` là API tốt khi bạn có `Path` hợp lệ. Đừng ép `ClassPathResource` thành `Path` chỉ để dùng `Files`. Hãy dùng `resource.getInputStream()` rồi bọc `BufferedReader` hoặc truyền stream cho parser.

## 14. Tests nên viết để tránh lỗi “works locally, fails in JAR”

| Kiểm thử | Mục đích |
| --- | --- |
| Resource tồn tại trong build output | Đúng path và packaging |
| Đọc thành công qua InputStream từ target/classes | Xác nhận nội dung |
| Đọc thành công từ packaged JAR **không có target/classes trong classpath** | Xác nhận portable archive access |
| Kiểm `getFile()` chỉ trong demo | Chứng minh khác biệt, không tạo dependency production |
| InputStream đóng cả khi parser lỗi | Tránh leak |
| Resource không tồn tại | Có exception/handling rõ ràng |
| Content rỗng/sai encoding | Validation và lỗi rõ |
| Copy temp rồi cleanup | Không để file rác hoặc secret |
| Chạy trên Linux và Windows nếu hỗ trợ | Classpath separator/path semantics |
| Chạy Spring Boot executable JAR nếu sản phẩm dùng Boot | Nested JAR loader behavior |
| Chạy WAR/container nếu sản phẩm deploy WAR | Không dựa archive unpack |
| Hai JAR có cùng resource path | Phát hiện collision |
| File lớn | Memory/streaming behavior |
| Resource URL không tin cậy | Không SSRF/credential leaks |

### Đặt assertion vào hành vi, không vào implementation detail

Trong production test, không nhất thiết assert protocol luôn là `jar`. Test được thiết kế đúng nên assert:
- Nội dung mong đợi được đọc thành công.
- Không gọi `getFile()` ở đường xử lý bắt buộc.
- Đã đóng luồng.
- Nếu API chỉ nhận File, quá trình copy/cleanup thực hiện thành công.
- Resource thiếu gây lỗi có thể chẩn đoán mà không lộ secret path.

Demo in protocol để giúp học; test production nên tập trung **contract**.

### Maven exec:java khác packaged JAR

`mvn exec:java` mặc định chạy với project output directory trên classpath, nên có thể không phát hiện `getFile()` bug. Một integration test riêng khởi chạy `java -cp artifact.jar:dependencies/* Main` (hoặc `java -jar` với executable JAR đã cấu hình) giúp phát hiện sớm. Với Spring Boot, test artifact repackage và launcher thật, không chỉ `@SpringBootTest` trong IDE.

## 15. Bảo mật khi trích xuất JAR resource ra tệp

Thường các resources đóng gói là trusted build assets, nhưng vẫn cần kiểm soát đường đi khi copy hoặc sử dụng:

1. Chỉ chọn **resource path cho phép**, không nhận arbitrary key từ HTTP request rồi đọc mọi classpath entry.
2. Resource có thể chứa secret/certificate: không log raw bytes, URI/path đầy đủ hoặc copy vào nơi có quyền rộng.
3. Dùng `Files.createTempFile` để tránh predictable filename và collision, kiểm file permissions theo deployment OS.
4. Giới hạn kích thước nếu resource được lấy từ archive không tin cậy (đề phòng ZIP bomb/memory/disk exhaustion).
5. Nếu phải giải nén toàn bộ ZIP, kiểm **Zip Slip** khi entry path chứa `../`, absolute path hoặc symlink; không nối entry names trực tiếp vào output directory.
6. Không đặt remote URL do user kiểm soát vào `UrlResource` rồi tự mở network không kiểm soát.
7. Không biến warning/error thành full raw JAR URL chứa đường dẫn nội bộ hoặc access token.
8. Dọn tệp tạm trong `finally`; có cơ chế startup cleanup cho tệp tạm sót lại sau crash nếu app tạo lượng lớn files.
9. Nếu bên thứ ba yêu cầu native resource, kiểm file lifetime và quyền executable/write; không tự cấp quyền rộng.
10. Không sửa content bên trong JAR đang deploy như cách cập nhật cấu hình runtime; ưu tiên immutable artifacts.

### Thông tin nhạy cảm trong getDescription()

`resource.getDescription()` có thể chứa classpath path, absolute file path hoặc URL. Nó hữu ích để debug, nhưng trong một số trường hợp sẽ lộ topology/credential URI. Nếu cần log, dùng logical ID đã allowlist, ví dụ `resourceType=classpath` và `resourceKey=welcomeTemplate`, không log toàn bộ description của resource nhạy cảm.

## 16. Bảng lỗi và hướng khắc phục nhanh

| Lỗi / triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `FileNotFoundException: cannot be resolved to absolute file path` | Resource nằm trong JAR | `getInputStream()`; chỉ copy temp nếu API bắt buộc File |
| Resource tồn tại nhưng `getFile()` lỗi | Existence khác filesystem mapping | Bỏ giả định `exists() => File` |
| Lỗi sau chuyển sang Spring Boot executable JAR | Nested JAR resources không có standalone File | Dùng stream/URL API tương thích loader |
| Chạy IDE OK, `java -jar` fail | IDE dùng classpath directory | Test packaged artifact, refactor File assumptions |
| `Paths.get(resource.getURI())` fail | URI là `jar:`/custom protocol | Không ép URI sang default Path |
| `new File(url.getPath())` mở sai | jar protocol hoặc URL encoding | Dùng stream; parse URI chỉ với file protocol có kiểm tra |
| `FileSystemResource("src/main/resources/...")` fail trên server | Source tree/working directory không tồn tại | `ClassPathResource` với path runtime |
| ClassPathResource.getInputStream không thấy | File không có trong JAR/classpath | Kiểm `jar tf`, resources config, filename/case |
| `@Value("classpath:...") Resource` có nhưng code fail | Consumer gọi `getFile()` | Truyền stream cho consumer hoặc temp copy |
| API native chỉ nhận absolute path | Không thể cấp JAR entry trực tiếp | Extract một resource ra temp với lifecycle phù hợp |
| Resource URL file trong IDE nhưng không phải JAR | ClassLoader đã chọn directory khác trước JAR | Chạy jar-only classpath, loại target/classes |
| Copy temp để parse nhưng temp mất sớm | Library dùng file sau khi method return | Giữ file tới khi library đóng và cleanup |
| Lỗi trên Windows | `:` thay vì `;` trong classpath | Dùng separator đúng OS |
| Classpath có hai file cùng tên | Resource collision trong nhiều JAR | Namespace rõ, test class loader behavior |
| OOM khi đọc file lớn | `readAllBytes()` nạp toàn bộ | Streaming, giới hạn size |
| Leak file descriptors | Không đóng InputStream | try-with-resources |
| Log path/URL chứa secret | Diagnostic text quá chi tiết | Allowlist metadata, không ghi raw URI |
| Tests chỉ dùng Maven exec pass | Chưa chạy packaged JAR | Thêm smoke test artifact thật |

## 17. Quy trình chẩn đoán từng bước

Khi có lỗi liên quan resource JAR, thực hiện:

1. Xác định **resource location**: có phải `classpath:...` hay filesystem path?
2. Kiểm tra resource có ở `src/main/resources` và cuối build được đóng gói đúng không.
3. Dùng `jar tf` trên **artifact đã deploy** để kiểm tra entry.
4. Kiểm tra class loader và dạng runtime: classpath directory, plain JAR, shaded JAR, Spring Boot nested JAR, WAR hay module.
5. Gọi `resource.exists()`/mở `getInputStream()` để phân biệt “resource vắng mặt” và “không thể chuyển thành File”.
6. Nếu stream đọc được nhưng File không được, thay File-only path bằng stream parser.
7. Nếu thư viện không hỗ trợ stream, thiết kế copy temp với cleanup và quyền truy cập hợp lý.
8. Xóa `target/classes` khỏi runtime classpath khi test packaged JAR, để không vô tình chọn resource ở directory.
9. Kiểm charset, file size và parser behavior riêng, không nhầm parse error với path error.
10. Nếu vấn đề chỉ xảy ra trong Boot/WAR, kiểm loader/container thực; không hardcode URL protocol.
11. Viết regression test cho đường startup/operation thật đã fail.
12. Không sử dụng workaround tạm như “copy resource vào cwd bằng tay” mà không quản lý deployment/lifecycle.

### Diagnostic snippet an toàn

```java
Resource r = new ClassPathResource("demo-data/message.txt");
System.out.println("exists=" + r.exists());
System.out.println("isFile=" + r.isFile());
// Không cần in getDescription() hoặc raw URL nếu chứa secret.
try (InputStream in = r.getInputStream()) {
    System.out.println("readableBytes=" + (in.read() != -1));
}
```

Đây là fragment. Nó chỉ phục vụ xem resource có đọc được; không xác nhận toàn bộ nội dung. Nếu `getInputStream()` ném `IOException`, chẩn đoán tiếp resource path/classpath/package chứ không mặc nhiên sửa bằng `getFile()`.

## 18. Bài tập thực hành

**Bài 1 — kiểm chứng getFile:** Chạy hai chế độ ở mục 9, so output. Giải thích vì sao `content` giống nhau nhưng `getFile` khác nhau.

**Bài 2 — thay File bằng InputStream:** Cho đoạn code giả lập `parse(File)`, tìm overload nhận `InputStream`. Nếu thư viện không có, viết utility copy temp kèm try/finally.

**Bài 3 — kiểm đường dẫn:** Đặt file tại `src/main/resources/data/defaults.json`. So sánh `new File("src/main/resources/data/defaults.json")`, `new ClassPathResource("data/defaults.json")` khi chạy IDE và packaged JAR.

**Bài 4 — URL/URI:** In `getURL().getProtocol()` ở hai mode và giải thích tại sao `Path.of(resource.getURI())` không portable với `jar:`.

**Bài 5 — packaged classpath test:** Chạy `jar tf`, kiểm entry và viết một smoke test Java từ JAR-only classpath. Không thêm `target/classes`.

**Bài 6 — Spring Boot executable JAR:** Nếu bạn có dự án Boot, tạo `@Value("classpath:templates/welcome.txt") Resource` rồi đọc stream. Chạy artifact `java -jar` sau repackage và kiểm nội dung.

**Bài 7 — temp cleanup:** Tạo resource giả gây `IOException` giữa lúc đọc, đảm bảo file tạm được xóa kể cả khi copy thất bại.

**Bài 8 — resource lớn:** Viết loader đọc file 200 MB theo chunks thay vì `readAllBytes()`; đo giới hạn memory hợp lý và xác nhận đóng stream.

**Bài 9 — duplicate path:** Đưa cùng resource name vào hai test JAR khác nhau; tìm hiểu class loader chọn bản nào. Sau đó đổi namespace để tránh collision.

**Bài 10 — security:** Một endpoint nhận `?path=...` và đọc classpath/file theo giá trị do client gửi. Đề xuất allowlist, quyền, giới hạn kích thước và thông báo lỗi không lộ absolute path.

## 19. Câu hỏi tự kiểm tra và đáp án

1. `ClassPathResource` có luôn trả `File` được không?
2. Một entry trong JAR có phải tệp độc lập trên filesystem không?
3. `Resource.exists()` có chứng minh `getFile()` thành công không?
4. API nào nên dùng để đọc portable classpath resource?
5. Vì sao `getFile()` có thể chạy từ `target/classes` nhưng fail từ JAR?
6. URL protocol thường gặp ở classpath directory và JAR là gì?
7. Tại sao `new File(url.getPath())` không phải giải pháp?
8. `Path.of(resource.getURI())` có hỗ trợ mọi `jar:` URL không?
9. Với resource trong JAR, `getFilename()` có thể trả giá trị không?
10. `ClassPathResource("src/main/resources/foo.txt")` thường sai ở đâu?
11. `getResourceAsStream()` của Class làm gì khi không tìm thấy?
12. Nếu parser chỉ nhận File, phương án nào nên thử đầu tiên?
13. Khi bắt buộc copy temp, ai có trách nhiệm cleanup?
14. `deleteOnExit()` có đủ để quản lý temp files ở server lâu dài không?
15. Dùng `mvn exec:java` có luôn chứng minh resource đọc được trong JAR không?
16. Tại sao cần bỏ `target/classes` khỏi classpath khi smoke test JAR?
17. Spring Boot nested JAR có làm mọi resource thành File không?
18. Một WAR có thể được container unpack thành file không?
19. Resource chỉ đọc một lần khác reusable descriptor như thế nào?
20. `FileSystemResource` phù hợp khi nào hơn `ClassPathResource`?
21. `ByteArrayResource` có hỗ trợ file thật không?
22. Nếu file chứa JSON lớn, vì sao nên dùng streaming parser?
23. `getDescription()` có thể làm lộ thông tin gì?
24. Nếu `getInputStream()` cũng báo không thấy resource, nên kiểm điều gì?
25. Tại sao không nên sửa trực tiếp nội dung resource trong deployed JAR?

**Đáp án:** (1) Không. (2) Không. (3) Không. (4) `getInputStream()` và đóng stream. (5) Classpath directory có file; JAR entry không có file rời. (6) Thường `file` và `jar`. (7) JAR URL không là OS path, có encoding. (8) Không. (9) Có, metadata tên entry vẫn có thể tồn tại. (10) Runtime classpath root không chứa tiền tố source tree. (11) Trả `null`. (12) Tìm overload nhận stream/Reader/URL trước. (13) Caller/lifecycle owner. (14) Không. (15) Không. (16) Để tránh chọn directory resource trước archive. (17) Không. (18) Có, tùy container. (19) Có thể không mở lại được lần nữa. (20) Khi cấu hình nằm ngoài artifact trên filesystem. (21) Không. (22) Tránh OOM, xử lý incremental. (23) Absolute path/URL nội bộ, có thể cả credential. (24) Packaging, name/case, classloader, path. (25) Artifact nên immutable; dùng external configuration hoặc build lại.

## 20. Checklist production

- [ ] Tài nguyên đóng gói đọc bằng `getInputStream()`, không phụ thuộc `getFile()`.
- [ ] Path classpath xác định từ classpath root, không từ source directory.
- [ ] Cả classpath directory và packaged JAR được smoke test.
- [ ] Runtime packaged test không chứa `target/classes` cùng resource.
- [ ] Có kiểm tra entry bằng `jar tf` trên artifact thực.
- [ ] Dùng try-with-resources để đóng mọi InputStream/Reader.
- [ ] Không dùng `new File(url.getPath())` cho JAR/custom URLs.
- [ ] Không giả định `Path.of(resource.getURI())` hoạt động với mọi provider.
- [ ] Nếu thư viện chỉ nhận File, kiểm overload stream/URL trước.
- [ ] Nếu phải copy temp: quản lý permissions, size, lifecycle và cleanup.
- [ ] Không coi `isFile()`/`exists()` là bảo đảm bất biến giữa hai lời gọi.
- [ ] Có handling `IOException` và missing resource rõ.
- [ ] File lớn được xử lý theo stream, không load toàn bộ vô hạn.
- [ ] Charset khi đọc text được chỉ định rõ.
- [ ] Kiểm tương thích class loader Boot nested JAR/WAR/module khi deploy.
- [ ] Tránh hardcode absolute local path hoặc working directory của IDE.
- [ ] External mutable config dùng file mount/config store, không sửa JAR.
- [ ] Không log raw resource content, path hoặc URL có secret.
- [ ] File do người dùng chỉ định phải qua allowlist/sandbox/path validation.
- [ ] Có test với resource trùng path giữa các dependencies khi liên quan.
- [ ] Classpath resources đặt namespace rõ và đóng gói bởi build.
- [ ] Không khởi tạo I/O nặng từ classpath trong BFPP/constructor nếu không cần.
- [ ] Không cố sửa File assumption bằng unzip toàn JAR trừ khi được thiết kế thật.
- [ ] Tập trung vào contract đọc nội dung hơn implementation URL protocol.

## 21. Nguồn tham khảo chính thức

- Spring Framework Reference — Resources (`ClassPathResource`, `Resource`, `ResourceLoader`): https://docs.spring.io/spring-framework/reference/core/resources.html
- Spring Framework 6.2 — `ClassPathResource` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/ClassPathResource.html
- Spring Framework 6.2 — `Resource` Javadoc (getFile): https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/Resource.html
- Spring Framework 6.2 — `FileSystemResource` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/FileSystemResource.html
- Spring Framework 6.2 — `ByteArrayResource` Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/io/ByteArrayResource.html
- Spring Boot — Executable JAR Specification / Nested JARs: https://docs.spring.io/spring-boot/specification/executable-jar/nested-jars.html
- Java SE 17 — `ClassLoader.getResourceAsStream`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#getResourceAsStream(java.lang.String)
- Java SE 17 — `FileSystems`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystems.html
- Apache Maven — Standard Directory Layout: https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="resources-009">Kiểm tra tồn tại, khả năng đọc và xử lý lỗi đường dẫn</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra tồn tại, khả năng đọc và xử lý lỗi đường dẫn trong Spring Framework

## 1. Vấn đề thực tế: có Resource không có nghĩa đã có file

Trong Spring Core Container, `Resource` là một **đối tượng mô tả vị trí hoặc nội dung tài nguyên**. Nó có thể đại diện cho file trên đĩa, một mục trong JAR, URL từ xa, byte array hay một input stream. Bạn có thể tạo `Resource` từ một location mà tài nguyên vật lý **chưa tồn tại**:

~~~java
Resource resource = new ClassPathResource("config/not-found.properties");
~~~

Dòng code hợp lệ dù `not-found.properties` không có trên classpath. Vì vậy có ba câu hỏi **khác nhau**:

1. **Đã tạo được handle chưa?** — Chỉ có một đối tượng `Resource`, không chứng minh dữ liệu tồn tại.
2. **Resource hiện có và có vẻ đọc được không?** — Sử dụng `exists()`, `isReadable()` và đôi khi `isFile()`.
3. **Có mở stream và thực sự đọc thành công không?** — Gọi `getInputStream()` và xử lý `IOException`.

> Nguyên tắc quan trọng: **`exists() == true` hoặc `isReadable() == true` không bảo đảm `getInputStream()` chắc chắn thành công.** File có thể bị xóa, thay quyền, thay nội dung hoặc kết nối mạng có thể lỗi ngay sau phép kiểm tra. Luôn xử lý lỗi ở thao tác I/O cuối cùng.

Bài này giải thích các API kiểm tra, sự khác nhau giữa tài nguyên classpath và filesystem, vấn đề đường dẫn khi đóng JAR, xử lý lỗi vắng mặt/quyền truy cập, thao tác đường dẫn tương đối, bảo mật, và một chương trình **Java 17 + Spring Framework 6.1.13** có thể chạy độc lập.

## 2. Những API quan trọng của `Resource`

Giao diện `org.springframework.core.io.Resource` kế thừa `InputStreamSource`. Những method thường dùng gồm:

| API | Công dụng | Điểm cần nhớ |
| --- | --- | --- |
| `exists()` | Kiểm tra tài nguyên tồn tại về mặt vật lý | Không đồng nghĩa có thể đọc ở thời điểm sau |
| `isReadable()` | Kiểm tra tài nguyên có vẻ có thể đọc | `false` là tín hiệu không đọc được; `true` vẫn có thể lỗi khi mở |
| `isOpen()` | Cho biết handle đại diện một stream đã mở | Quan trọng với `InputStreamResource`, thường không tái đọc được |
| `isFile()` | Cho biết resource có dạng file của filesystem | Không bảo đảm tuyệt đối `getFile()` thành công |
| `getInputStream()` | Mở stream để đọc dữ liệu | Có thể ném `IOException` |
| `getURL()` | Lấy URL nếu resource biểu diễn được | Có thể ném `IOException` |
| `getURI()` | Lấy URI nếu resource biểu diễn được | Có thể ném `IOException` |
| `getFile()` | Lấy `java.io.File` nếu resource nằm trên filesystem | Không áp dụng tùy tiện cho file **bên trong JAR** |
| `getFilename()` | Lấy phần tên cuối của resource | Có thể trả `null`; không phải unique identifier |
| `getDescription()` | Mô tả resource để logging/diagnostics | Có thể chứa path nhạy cảm |
| `contentLength()` | Độ dài nội dung theo implementation | Có thể cần I/O hoặc ném exception |
| `lastModified()` | Thời điểm sửa đổi nếu xác định được | Không phải loại resource nào cũng hỗ trợ |
| `createRelative(path)` | Tạo resource handle tương đối | Không tự xác thực ranh giới an toàn cho user input |

`Resource` là abstraction chung, nên mỗi implementation có thể thực hiện các phép kiểm tra theo cách khác nhau. Đừng suy từ một `FileSystemResource` ra hành vi y hệt của mọi `UrlResource` hay `InputStreamResource`.

## 3. `Resource.exists()` — xác nhận tồn tại chứ không xác nhận sẵn sàng

Ví dụ:

~~~java
Resource present =
    new ClassPathResource("demo255/hello.txt");

Resource absent =
    new ClassPathResource("demo255/no-such-file.txt");

System.out.println(present.exists());
System.out.println(absent.exists());
~~~

Trong project có `demo255/hello.txt` được đóng gói vào classpath, output mong đợi:

~~~text
true
false
~~~

Đây là **snippet** trong method Java có import `Resource` và `ClassPathResource`; phần 11 cung cấp project runnable trọn vẹn.

### Khi `exists()` là false

Một số khả năng:

- Path/classpath location sai.
- Tên file hoặc chữ hoa/chữ thường sai trên filesystem phân biệt case.
- File chưa được copy từ `src/main/resources` sang output khi build.
- File có trong source tree nhưng không có trong artifact/JAR đang chạy.
- User process không truy cập được resource, hoặc URL không phản hồi theo cách implementation kiểm tra.
- File đã bị xóa, mount chưa sẵn sàng hoặc root path đang trỏ tới nơi khác.
- Đường dẫn tương đối được giải quyết từ working directory khác bạn dự đoán.

Không có ý nghĩa rằng `exists()==false` luôn tương đương với “chưa từng có file”; nó chỉ phản ánh kết quả phép kiểm tra của **resource implementation ở thời điểm đó**.

### Đừng dùng handle như một bằng chứng tồn tại

~~~java
ResourceLoader loader = new DefaultResourceLoader();
Resource resource = loader.getResource(
    "classpath:does-not-exist.txt");
~~~

`getResource(...)` trả một handle từ location string; không phải lệnh xác thực rằng file chắc chắn tồn tại. Thường chỉ khi hỏi `exists()` hoặc thực sự mở `getInputStream()` mới xác định được trạng thái hữu ích cho thao tác tiếp theo.

## 4. `Resource.isReadable()` — kiểm tra khả năng đọc, nhưng vẫn phải bắt lỗi

`isReadable()` cho biết nội dung có vẻ đọc được qua `getInputStream()`. Theo Javadoc Resource ở Spring 6.1, kết quả `true` **không bảo đảm** thao tác đọc cuối sẽ thành công; `false` cho biết resource không thể đọc được theo contract kiểm tra.

~~~java
if (!resource.isReadable()) {
    throw new IllegalStateException(
        "Resource is not readable: " + resource.getDescription());
}
~~~

Đây là snippet minh họa, không thay thế `try/catch` quanh thao tác I/O.

### Vì sao `exists()` và `isReadable()` khác nhau?

Một tài nguyên có thể:

- Tồn tại nhưng là **thư mục**, không phải file nội dung bạn muốn đọc.
- Tồn tại nhưng process không đủ quyền đọc.
- Tồn tại nhưng remote endpoint từ chối trả nội dung.
- Tồn tại ở thời điểm A, nhưng bị xóa trước khi mở stream ở thời điểm B.
- Có handle chứa stream đã được sử dụng hết.

`isReadable()` giúp phát hiện một số tình huống sớm, nhưng không thay cho việc thử mở stream.

### Với `FileSystemResource`

Implementation filesystem kiểm tra file thực và khả năng đọc theo API Java/NIO phù hợp. Quyền trên Linux/Windows, ACL và đặc điểm filesystem có thể khiến kết quả khác nhau. Không viết test cho quyền đọc với giả định root/admin và user thường có cùng kết quả.

## 5. `getInputStream()` là phép thử thực tế

Nếu cần nội dung, hãy **thực sự mở stream** và đọc trong `try-with-resources`:

~~~java
static String readUtf8(Resource resource) throws IOException {
    try (InputStream input = resource.getInputStream()) {
        return new String(
            input.readAllBytes(),
            StandardCharsets.UTF_8);
    }
}
~~~

Đây là một method đầy đủ nhưng cần imports `IOException`, `InputStream`, `StandardCharsets` và `Resource`. Nó thích hợp cho **tài nguyên nhỏ**, không phải dữ liệu vài GB.

Lợi ích:

- `getInputStream()` làm thao tác I/O thật, bộc lộ lỗi truy cập.
- `try-with-resources` đóng stream ngay cả khi đọc thất bại.
- `StandardCharsets.UTF_8` tránh lệ thuộc default charset của OS.
- Caller xử lý `IOException` theo chính sách bắt buộc/không bắt buộc.

### Không dùng chỉ `exists()` rồi mở stream mà không xử lý exception

~~~java
if (resource.exists()) {
    InputStream input = resource.getInputStream();
}
~~~

Đoạn trên vẫn có thể ném `IOException` do race condition và còn **chưa đóng stream**. Nó là anti-pattern nếu không có `try-with-resources`/finally phù hợp.

Cách an toàn hơn:

~~~java
if (resource.isReadable()) {
    try (InputStream input = resource.getInputStream()) {
        // Đọc và parse dữ liệu ở đây.
    } catch (IOException ex) {
        // Xử lý lỗi thực tế tại thao tác I/O.
    }
}
~~~

Đây là snippet minh họa, không phải một chương trình hoàn chỉnh. Ngay cả sau kiểm tra, `getInputStream()` vẫn có thể fail.

## 6. `isFile()` và `getFile()`: lưu ý khi nằm trong JAR

Nếu dùng:

~~~java
Resource resource =
    new ClassPathResource("demo255/hello.txt");
~~~

Khi chạy bằng IDE/Maven, resource có thể là file thật như:

~~~text
target/classes/demo255/hello.txt
~~~

Nhưng trong JAR, resource có thể là một entry bên trong archive:

~~~text
jar:file:/opt/app/app.jar!/demo255/hello.txt
~~~

Khi đó:

~~~java
File file = resource.getFile();
~~~

**có thể thất bại** vì không có `java.io.File` độc lập đại diện cho entry của JAR.

### Cách đúng: đọc stream

~~~java
try (InputStream input = resource.getInputStream()) {
    // Parse dữ liệu bằng stream.
}
~~~

Nếu mục tiêu chỉ là đọc cấu hình hoặc template, bạn thường **không cần `getFile()`**.

### Khi nào dùng `getFile()`?

Khi API phía sau bắt buộc nhận `java.io.File` và resource **thực sự nằm trên filesystem**. Ngay cả `resource.isFile()==true` chỉ là tín hiệu hữu ích; vẫn cần xử lý `IOException` từ `getFile()`.

Nếu một library chỉ nhận File nhưng tài nguyên nằm trong JAR, cân nhắc API overload nhận `InputStream`/`URL` hoặc giải nén tạm một cách có quản lý. Việc giải nén phải có cleanup, hạn chế kích thước và chính sách bảo mật rõ ràng.

## 7. `isOpen()`: cảnh báo về stream đọc một lần

`isOpen()` có nghĩa resource handle đang đại diện cho một stream đã mở. `InputStreamResource` là ví dụ điển hình.

~~~java
InputStreamResource resource =
    new InputStreamResource(inputStream);
~~~

Đây là snippet minh họa cần `inputStream` hợp lệ và import `InputStreamResource`.

Với loại resource này:

- `isOpen()` thường là `true`.
- Không nên trông chờ có thể mở và đọc lại một stream độc lập nhiều lần.
- Gọi `contentLength()` trên một resource kiểu stream có thể tiêu thụ stream, tùy implementation; tránh dùng nó để “kiểm tra” tùy tiện.
- Nên đóng stream theo ownership rõ ràng.

Nếu dữ liệu đã nằm trong một byte array và cần đọc lặp lại, dùng `ByteArrayResource` thay vì bọc một `InputStreamResource` đang mở.

### `ByteArrayResource` thích hợp cho test

~~~java
Resource memory = new ByteArrayResource(
    "hello".getBytes(StandardCharsets.UTF_8));
~~~

`ByteArrayResource` thường trả true cho `exists()` và `isReadable()`, false cho `isOpen()`, và có thể tạo stream mới trên cùng byte array. Đây là cách tạo resource test không cần file hệ thống.

## 8. Phân biệt các Resource implementations

| Implementation | Nguồn | Cần chú ý |
| --- | --- | --- |
| `ClassPathResource` | Classpath hoặc module resource | Không ép `getFile()` khi trong JAR |
| `FileSystemResource` | `File` hoặc `Path` của filesystem | Path tương đối và quyền truy cập |
| `PathResource` | `java.nio.file.Path` | Thích hợp khi ứng dụng thao tác NIO.2 |
| `UrlResource` | HTTP, HTTPS, file URL hoặc scheme hỗ trợ | Network/HTTP response và timeouts |
| `ByteArrayResource` | Byte array có sẵn | Không đại diện cho file path |
| `InputStreamResource` | Stream đã có | Có thể chỉ đọc một lần, `isOpen()` |
| `ServletContextResource` | Resource trong web application context | Phụ thuộc servlet deployment |
| `ModuleResource` | Java module resource | Khả năng truy cập tùy module path/runtime |

### Không cần phân nhánh theo class nếu không cần

Một method chỉ cần đọc nội dung thường nên nhận `Resource`:

~~~java
String parse(Resource resource) throws IOException {
    try (InputStream in = resource.getInputStream()) {
        return new String(
            in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
~~~

Khi đó caller có thể truyền `ClassPathResource`, `FileSystemResource` hoặc `ByteArrayResource` mà không sửa logic đọc.

Chỉ phân biệt implementation khi bạn cần chức năng đặc thù, ví dụ `WritableResource`, metadata file path hay stream lifecycle.

## 9. Cấu hình location: `classpath:` và `file:`

Một `ResourceLoader` hiểu các prefix phổ biến:

~~~java
Resource fromClasspath =
    loader.getResource("classpath:settings/app.properties");

Resource fromFile =
    loader.getResource("file:/opt/app/settings/app.properties");
~~~

Hai snippets cần biến `loader` kiểu `ResourceLoader`.

| Location | Ý nghĩa |
| --- | --- |
| `classpath:config/app.yml` | Tìm theo class loader/module path |
| `file:/opt/app/config/app.yml` | URL file hệ thống |
| `file:///C:/app/config/app.yml` | Dạng file URL tuyệt đối kiểu Windows |
| `https://config.example.test/app.yml` | URL mạng; có thêm rủi ro timeouts |
| `config/app.yml` | Không prefix; tùy implementation ResourceLoader/context |

**Đừng dùng một chuỗi `src/main/resources/...` như đường dẫn filesystem trong ứng dụng đã đóng JAR**, vì source folder có thể không tồn tại tại runtime. Đặt resource trên classpath hoặc dùng external file location đã được cấu hình rõ.

### Đường dẫn Windows

Trong Java string, dấu backslash là escape character, ví dụ `"C:\\config\\app.properties"`. Để tránh phụ thuộc cú pháp slash và drive letter, ứng dụng thường dùng:

~~~java
Path path = Path.of("C:", "config", "app.properties");
Resource resource = new FileSystemResource(path);
~~~

Đoạn này là **minh họa** về API, không phải bảo đảm `Path.of("C:", ...)` luôn là đường dẫn tuyệt đối trên mọi Windows configuration. Với đường dẫn quan trọng, dùng một absolute path cấu hình rõ và kiểm tra `path.isAbsolute()`.

### Không prefix dễ gây nhầm

`getResource("settings/app.properties")` có thể resolve khác khi `ResourceLoader` là `DefaultResourceLoader`, `FileSystemXmlApplicationContext` hoặc một web application context. Nên dùng prefix rõ ràng để code portable và dễ debug.

## 10. Đường dẫn tương đối phụ thuộc working directory

Nếu file bên ngoài ứng dụng được chỉ bằng đường dẫn tương đối:

~~~java
Path path = Path.of("config", "app.properties");
~~~

nó thường được giải quyết theo current working directory của **process**, không tự dựa vào vị trí file `.java` hay JAR.

Ví dụ:

~~~bash
cd /opt/myapp
java -jar application.jar
~~~

khác với:

~~~bash
cd /tmp
java -jar /opt/myapp/application.jar
~~~

nếu app dùng `Path.of("config/app.properties")`.

### Hướng xử lý

- Dùng một base directory được chỉ định tường minh.
- Chuyển thành absolute path nếu nghiệp vụ cần xác định cố định.
- In/log một đường dẫn đã chuẩn hóa **khi không chứa thông tin nhạy cảm**.
- Kiểm tra path sau khi resolve với root được cho phép.
- Kiểm thử khởi chạy ứng dụng từ working directories khác nhau.

Snippet:

~~~java
Path root = Path.of("/opt/myapp/config");
Path config = root.resolve("app.properties").normalize();
~~~

Đây là ví dụ Unix minh họa; trên Windows phải chọn absolute root phù hợp. `normalize()` loại một số segments `.`/`..` về mặt cú pháp, **không tự kiểm tra symlink hoặc quyền truy cập**.

## 11. Ví dụ hoàn chỉnh Java 17 + Spring Context 6.1.13

Project nhỏ sau tự tạo một file tạm, dùng một file có thật trên classpath và một byte-array resource. Nó trình diễn:

1. `ClassPathResource.exists()` và `isReadable()` khi resource có thật.
2. `ClassPathResource` với đường dẫn không tồn tại.
3. Bắt `IOException` khi cố mở resource thiếu.
4. `FileSystemResource` trỏ tới file tạm, đọc rồi xóa để thấy trạng thái thay đổi.
5. `ByteArrayResource`, `isOpen()` và đọc từ bộ nhớ.
6. Đọc bằng `getInputStream()`, không dùng `getFile()` nên chạy được khi đóng JAR.

### 11.1. Cấu trúc dự án

~~~text
spring-resource-check-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/example/resourcecheck/ResourceCheckDemo.java
        └── resources/
            └── demo255/
                └── hello.txt
~~~

### 11.2. File `pom.xml`

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-resource-check-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.13</version>
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
                <version>3.3.0</version>
                <configuration>
                    <mainClass>com.example.resourcecheck.ResourceCheckDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 11.3. File `src/main/resources/demo255/hello.txt`

~~~text
from-classpath
~~~

Đây là file text thật cần tạo ở đúng location. Maven Resources Plugin của lifecycle `compile` sẽ đưa file vào runtime classpath.

### 11.4. File `src/main/java/com/example/resourcecheck/ResourceCheckDemo.java`

~~~java
package com.example.resourcecheck;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public class ResourceCheckDemo {

    public static void main(String[] args) throws IOException {
        Resource classpath = new ClassPathResource("demo255/hello.txt");
        Resource missing =
            new ClassPathResource("demo255/no-such-file.txt");

        System.out.println("classpath-exists=" + classpath.exists());
        System.out.println("classpath-readable=" + classpath.isReadable());
        System.out.println("classpath-text=" + readUtf8(classpath).strip());

        System.out.println("missing-exists=" + missing.exists());
        System.out.println("missing-readable=" + missing.isReadable());
        try {
            readUtf8(missing);
            System.out.println("missing-read=unexpected-success");
        } catch (IOException expected) {
            System.out.println("missing-read=IOException");
        }

        Resource memory = new ByteArrayResource(
            "from-memory".getBytes(StandardCharsets.UTF_8));

        System.out.println("memory-exists=" + memory.exists());
        System.out.println("memory-readable=" + memory.isReadable());
        System.out.println("memory-open=" + memory.isOpen());
        System.out.println("memory-text=" + readUtf8(memory));

        Path directory = Files.createTempDirectory("spring-resource-255-");
        Path file = directory.resolve("current.txt");
        try {
            Files.writeString(file, "from-disk", StandardCharsets.UTF_8);
            Resource disk = new FileSystemResource(file);

            System.out.println("disk-exists=" + disk.exists());
            System.out.println("disk-readable=" + disk.isReadable());
            System.out.println("disk-text=" + readUtf8(disk));

            Files.delete(file);
            System.out.println("disk-after-delete=" + disk.exists());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static String readUtf8(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            return new String(
                input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
~~~

### 11.5. Chạy

Yêu cầu Java 17+ và Maven 3.9.x. Đứng ở thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng chủ động in bởi chương trình trong môi trường có quyền tạo file tạm và resource fixture được đóng gói đúng:

~~~text
classpath-exists=true
classpath-readable=true
classpath-text=from-classpath
missing-exists=false
missing-readable=false
missing-read=IOException
memory-exists=true
memory-readable=true
memory-open=false
memory-text=from-memory
disk-exists=true
disk-readable=true
disk-text=from-disk
disk-after-delete=false
~~~

Maven hoặc logging implementation có thể in thêm thông tin. Chương trình chỉ in marker cố định, không in tên file tạm ngẫu nhiên nên stdout dự kiến ổn định.

### 11.6. Vì sao output đúng?

- `classpath`: fixture `demo255/hello.txt` có thật trong runtime classpath.
- `missing`: không có fixture `demo255/no-such-file.txt`; `exists()`/`isReadable()` là false.
- Mở stream từ `missing` ném `IOException` (thường là một subtype như `FileNotFoundException`), được code bắt.
- `ByteArrayResource` đọc bytes trong bộ nhớ, không cần path vật lý và không giữ stream ở trạng thái open.
- `FileSystemResource` trỏ tới file tạm mà chương trình vừa tạo, nên tồn tại và đọc được trước khi xóa.
- Sau `Files.delete(file)`, resource handle vẫn là object hợp lệ nhưng `disk.exists()` trả false.
- `finally` dọn file/thư mục tạm ngay cả khi một bước trong block ném lỗi.

### 11.7. Điều kiện khi chạy trên máy khác

Nếu môi trường không cho phép tạo file trong temporary directory, demo có thể ném `IOException` trước các dòng liên quan đến disk. Đây là **lỗi môi trường thực**, không nên che bằng cách bịa một stdout thành công.

Nếu target classpath thiếu file `demo255/hello.txt`, `classpath.exists()` sẽ false và việc đọc sẽ thất bại. Hãy kiểm tra cấu trúc thư mục và Maven resources packaging, thay vì sửa code để giả vờ có resource.

## 12. Khi file là bắt buộc và khi file là tùy chọn

Chính sách xử lý khác nhau cho **required** và **optional** resources.

### Required resource — fail fast

Ví dụ một file schema không thể thiếu:

~~~java
static String readRequired(Resource resource) throws IOException {
    if (!resource.isReadable()) {
        throw new IOException(
            "Required resource is unavailable: "
            + resource.getDescription());
    }
    try (InputStream in = resource.getInputStream()) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
~~~

Đây là một method Java minh họa cần imports `IOException`, `InputStream`, `StandardCharsets` và `Resource`.

Nếu mở stream thất bại sau khi `isReadable()` thành true, exception vẫn nổi lên. Đúng chủ đích: **required configuration thiếu/không đọc được phải khiến thao tác thất bại**.

Với ứng dụng Spring, exception khi tạo bean có thể được wrap trong `BeanCreationException`; nên xem root cause để biết resource thật sự lỗi ở đâu.

### Optional resource — fallback có chủ đích

Ví dụ một file mô tả bổ sung:

~~~java
static String readOptional(Resource resource, String fallback) {
    if (!resource.exists()) {
        return fallback;
    }
    try (InputStream in = resource.getInputStream()) {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
        throw new IllegalStateException(
            "Optional resource exists but cannot be read", ex);
    }
}
~~~

Method trên cố ý **chỉ fallback khi file không có**, không nuốt mọi lỗi I/O. Nó giúp phân biệt resource optional thực sự không được cung cấp với resource đã cung cấp nhưng bị hỏng/quyền đọc không hợp lệ.

### Không coi mọi exception là “file không có”

Anti-pattern:

~~~java
try {
    return readConfig(resource);
} catch (Exception ignored) {
    return defaultConfig();
}
~~~

Đây là snippet minh họa, giả định có `readConfig` và `defaultConfig`.

Nó nuốt cả lỗi parse, thiếu quyền, I/O tạm thời hoặc bug lập trình. Nếu default không an toàn, ứng dụng có thể tiếp tục hoạt động sai mà không phát hiện vấn đề.

## 13. Phân loại lỗi thường gặp

| Loại lỗi | Ví dụ nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| `FileNotFoundException` | Classpath resource thiếu, không có file cụ thể | Kiểm tra path/artifact; optional vs required |
| `NoSuchFileException` | NIO path không tồn tại khi mở | Kiểm tra path/file race |
| `AccessDeniedException` | File ACL hoặc OS permission từ chối | Quyền process, mount, owner, service account |
| `InvalidPathException` | Chuỗi path không hợp lệ với filesystem provider | Validate/canonicalize đầu vào |
| `MalformedURLException` | URL syntax sai | Kiểm tra scheme/path encoding |
| `IOException` chung | I/O bị lỗi, URL/JAR/network problems | Log context, preserve cause, retry có giới hạn nếu hợp lý |
| `SecurityException` | Security policy từ chối thao tác | Không nuốt như file thiếu, xử lý policy |
| Parse exception | File có nhưng JSON/XML/properties sai định dạng | Fail fast/validation, không coi là missing |
| `BeanCreationException` | Resource lỗi trong lúc tạo bean | Xem exception chain đến root cause |

Tên exception cụ thể thay đổi theo implementation. Ví dụ `getInputStream()` của `ClassPathResource` thường ném `FileNotFoundException` khi không tìm thấy, trong khi NIO path có thể ném `NoSuchFileException`.

### Bảo toàn nguyên nhân

~~~java
try {
    return readRequired(resource);
} catch (IOException ex) {
    throw new IllegalStateException(
        "Cannot load application resource: "
        + resource.getDescription(), ex);
}
~~~

Snippet này giả định có method `readRequired` như phía trên. Truyền `ex` làm cause để stack trace giữ được thông tin lỗi gốc; đừng chỉ ghi `ex.getMessage()` và bỏ exception.

Lưu ý: `getDescription()` có thể chứa filesystem path hoặc URL; với secrets/đường dẫn nhạy cảm, cần redaction trước khi đưa vào log công khai.

## 14. Time-of-check/time-of-use (TOCTOU)

Xét trình tự:

~~~text
T1: resource.exists()      -> true
T2: process khác xóa file
T3: resource.getInputStream() -> IOException
~~~

Đây là **race condition tự nhiên** khi kiểm tra trước rồi mới sử dụng. Không có cách giải quyết bằng một `if (exists())` đơn giản.

### Cách thiết kế hợp lý

- Sử dụng `exists()`/`isReadable()` nếu cần đưa ra **thông báo thân thiện sớm**.
- Xử lý exception tại `getInputStream()`/`Files.newInputStream()`, vì đó là thao tác quyết định.
- Nếu nghiệp vụ cần file snapshot nhất quán, xem xét cơ chế locking, immutable file names hoặc atomic publication ở tầng lưu trữ.
- Không dùng kết quả check rồi thực hiện nhiều thao tác filesystem nhạy cảm mà không xác thực lại điều kiện cần.
- Với remote URLs, lập timeout/retry policies có giới hạn thay vì coi HEAD check trước là đủ.

### File thay đổi trong lúc đọc

Có thể mở stream thành công, rồi file bị thay/xóa hoặc mạng đứt giữa chừng. Bạn vẫn có thể nhận dữ liệu không đầy đủ hoặc `IOException` khi đọc. Nếu dữ liệu cần integrity, verify checksum/signature/format hoặc đọc vào snapshot được kiểm soát trước khi áp dụng.

## 15. `getURL()` và `getURI()` không giống `getInputStream()`

Có resource mà bạn muốn log vị trí:

~~~java
URL url = resource.getURL();
URI uri = resource.getURI();
~~~

Các method này có thể ném `IOException` tùy resource. Việc lấy được URL/URI **không nhất thiết chứng minh nội dung có thể tải được**.

Ví dụ `https://...` có thể là URL đúng cú pháp nhưng server vẫn trả 404/403/500, timeout, TLS error hoặc DNS failure.

### Không dùng URL string làm thông báo lỗi duy nhất

URL có thể chứa query parameter chứa token hoặc thông tin nhạy cảm. Nếu phải log, ưu tiên hiển thị resource type, logical resource name và sanitized URL/path.

### `getFile()` không tương đương `getURI()`

Một URI `jar:file:/...!/path` là định danh của entry trong JAR, không thể chuyển một cách đơn giản thành `File` trỏ tới chính entry ấy. Tránh:

~~~java
new File(resource.getURI());
~~~

khi chưa biết URI scheme thật sự là `file` và ứng dụng có quyền truy cập.

## 16. Thư mục không phải file nội dung

Một đường dẫn filesystem có thể trỏ tới thư mục:

~~~java
Path folder = Path.of("config");
Resource resource = new FileSystemResource(folder);
~~~

Nếu `config` tồn tại, `exists()` có thể đúng nhưng `isReadable()` của `FileSystemResource` có logic loại thư mục khi đánh giá một **file nội dung**.

### Đừng suy tồn tại là loại file đúng

Nếu nghiệp vụ cần một file regular, dùng kiểm tra NIO bổ sung:

~~~java
if (!Files.isRegularFile(path)) {
    throw new IOException("Expected a regular file");
}
~~~

Đây là snippet bên trong method có biến `Path path`, import `Files` và `IOException`. Sau check này vẫn phải handle exceptions khi mở hoặc đọc thực sự.

### Symlinks

Một `Path` có thể là symbolic link. Có hai vấn đề khác nhau:

- Link tồn tại nhưng target bị xóa.
- Link trỏ tới một location bên ngoài thư mục cho phép.

Nếu user có thể tạo/sửa symlink, `normalize()` không giải quyết được đầy đủ. Cần một **security policy** xác định có follow symlink không, và cơ chế kiểm tra/đọc chống race tương ứng.

## 17. Xử lý đường dẫn không đáng tin cậy

Nếu ứng dụng nhận tên file từ HTTP request, message queue hoặc plugin chưa đáng tin, không nên làm:

~~~java
Path target = root.resolve(userInput);
Resource resource = new FileSystemResource(target);
~~~

mà không validate `userInput`.

Một input như:

~~~text
../../secrets/private.key
~~~

có thể vượt root khi được resolve nếu không kiểm tra.

### Bước kiểm tra tối thiểu

Với base root đáng tin cậy:

~~~java
Path root = Path.of("/srv/app/allowed").toAbsolutePath().normalize();
Path candidate = root.resolve(userInput).normalize();

if (!candidate.startsWith(root)) {
    throw new SecurityException("Path is outside allowed root");
}
~~~

Đây là snippet minh họa cho filesystem dạng Unix, cần biến `String userInput` và imports `Path`. Kiểm tra `startsWith(Path)` dựa trên **path segments**, không phải phép so sánh chuỗi prefix đơn giản.

**Nhưng:** Đoạn check này mới bảo vệ trước path traversal cú pháp đơn giản. Nó **không chống hoàn toàn symlink escape hoặc TOCTOU**. Nếu file do attacker có thể chỉnh sửa đồng thời, cần chính sách filesystem/OS permissions, kiểm tra canonical/real paths và trong trường hợp cần mức bảo vệ cao là thao tác gắn với directory handles như `SecureDirectoryStream` khi được provider hỗ trợ.

### Không đưa arbitrary URL vào resource loader

Một input như `https://...` hoặc `file:///...` có thể mở ra resource ngoài vùng được phép nếu code nhận mọi scheme. Nếu người dùng chỉ được chọn **tên template**, hãy whitelist tên logic thay vì cho tự chọn arbitrary path/URL.

### Cũng cần giới hạn dung lượng

Resource readable chưa chắc an toàn để `readAllBytes()`. Với input không đáng tin, đặt giới hạn kích thước, tránh memory exhaustion và xử lý decompression bombs nếu resource được giải nén.

## 18. File resource và network resource cần chiến lược lỗi khác nhau

Một `UrlResource` dùng HTTP có thể thực hiện I/O mạng khi kiểm tra `exists()` hoặc `isReadable()`. Đường kết nối có thể gặp:

- Timeout khi connect/read.
- HTTP status không thành công.
- DNS failure.
- TLS certificate validation failure.
- Authentication/authorization failure.
- Redirect tới location bị chính sách chặn.

**Không retry vô hạn**, không coi mọi HTTP 403 là “config file optional không có”, và không bắt generic exception rồi dùng giá trị default không được phép.

### Retry khi nào?

- Lỗi tạm thời và operation có thể thử lại an toàn.
- Có retry count/backoff/timeout rõ ràng.
- Không chặn main application startup vô hạn nếu resource là optional.
- Có metrics/alert nếu resource bắt buộc liên tục thất bại.

Khi cần cấu hình từ URL từ xa thường xuyên, nên thiết kế một configuration provider/HTTP client rõ ràng với security/timeouts thay vì dựa hết vào API `exists()` đơn lẻ.

## 19. `contentLength()` không phải kiểm tra miễn phí

Trước khi đọc, một số code muốn dùng:

~~~java
long size = resource.contentLength();
~~~

API này có thể phát sinh I/O hoặc ném `IOException`. Với resource dạng input stream một lần, việc tính length có thể làm tiêu thụ stream.

Nếu cần bảo vệ khỏi file quá lớn:

- Với filesystem path tin cậy, có thể sử dụng `Files.size(path)` với exception handling phù hợp.
- Với HTTP, header Content-Length có thể không có hoặc sai; cần giới hạn **khi streaming**.
- Với `InputStreamResource`, tránh gọi `contentLength()` như một kiểm tra không tác động.
- Cần xác định ngưỡng bytes và dừng đọc khi vượt, không chỉ tin metadata.

### Example đọc giới hạn — ý tưởng

~~~java
try (InputStream in = resource.getInputStream()) {
    // Đọc từng block với counter, dừng khi quá giới hạn.
    // Không gọi readAllBytes() trên nguồn không tin cậy và không giới hạn.
}
~~~

Snippet này minh họa chiến lược, không phải implementation streaming-limiter hoàn chỉnh.

## 20. Encoding và parse errors là lớp lỗi khác

Đọc file UTF-8:

~~~java
try (InputStream in = resource.getInputStream();
     Reader reader = new InputStreamReader(
         in, StandardCharsets.UTF_8)) {
    // Parse text qua Reader.
}
~~~

Đây là snippet minh họa trong method có imports `InputStream`, `Reader`, `InputStreamReader`, `StandardCharsets`.

Nếu file tồn tại/đọc được nhưng có JSON/XML/YAML sai cú pháp, lỗi **parsing** xảy ra ở bước tiếp theo. Không biến mọi lỗi parsing thành “resource not found”.

### File dạng nhị phân

Dùng byte streams cho PDF, ảnh hoặc archive. Đừng đọc nhị phân bằng `String` chỉ vì `isReadable()` trả true.

### Charset không đúng

Nếu file lưu UTF-8 nhưng bạn dùng default charset của OS, lỗi chữ tiếng Việt có thể xuất hiện trong một môi trường và không xuất hiện ở môi trường khác. Chỉ định charset hoặc dùng parser biết rõ encoding trong format.

## 21. Sử dụng cùng API khi bean được Spring tạo

Ví dụ bean cấu hình:

~~~java
@Configuration
public class AppConfig {

    @Bean
    public Settings settings(ResourceLoader loader) throws IOException {
        Resource resource =
            loader.getResource("classpath:config/app.properties");

        try (InputStream input = resource.getInputStream()) {
            Properties properties = new Properties();
            properties.load(input);
            return new Settings(properties);
        }
    }
}
~~~

Đây là **snippet kiến trúc**, cần imports `Configuration`, `Bean`, `ResourceLoader`, `Resource`, `InputStream`, `IOException`, `Properties`, cùng class `Settings` có constructor phù hợp.

Nếu file không tồn tại, bean factory method có thể ném `IOException`; Spring wrap lỗi khởi tạo bean tương ứng. Điều này thường là **đúng** với config bắt buộc.

### Đừng chạy logic I/O dài trong `BeanFactoryPostProcessor` không cần thiết

Có các extension points xử lý metadata rất sớm trong Spring lifecycle. Nếu đọc remote resource hoặc tạo application beans quá sớm, bạn có thể gây lifecycle/ordering issues. Hãy chọn thời điểm khởi tạo config phù hợp; với Spring Boot, external configuration còn có cơ chế bootstrap/Config Data riêng.

### `@Value("classpath:...") Resource`

Một constructor có thể nhận `Resource` qua conversion/injection khi infrastructure tương ứng tồn tại:

~~~java
public FileBackedService(
    @Value("classpath:config/app.properties") Resource resource) {
    this.resource = resource;
}
~~~

Đây là snippet minh họa; injection tạo resource handle, không tự xác thực rằng file tồn tại. `FileBackedService` vẫn cần kiểm tra và xử lý lỗi khi thực sự sử dụng.

## 22. Tách lỗi theo chính sách thay vì exception type duy nhất

Một loader tốt nên trả lời được:

- **Không có tài nguyên**: optional fallback hay required fail?
- **Có nhưng không đủ quyền**: lỗi vận hành cần báo rõ.
- **Có nhưng không parse được**: lỗi dữ liệu, nên fail fast.
- **Tài nguyên quá lớn**: từ chối vì giới hạn.
- **URL tạm thời không tải được**: có retry policy?
- **Resource bị thay đổi**: có cần checksum/version hoặc snapshot?
- **Đường dẫn đi ngoài root**: lỗi bảo mật, không thử đọc.

Không nên:

~~~java
catch (Exception ignored) {
    return emptySettings;
}
~~~

vì nó mất root cause và biến lỗi quan trọng thành sự im lặng.

### Báo lỗi có ích nhưng không làm lộ dữ liệu

Ví dụ:

~~~text
Cannot read required resource [classpath:config/service.properties].
Cause: FileNotFoundException
~~~

Đây là thông báo minh họa. Nếu location là path đến secret hoặc remote URL chứa query token, cần che thông tin nhạy cảm. Với service chạy production, log nên phân biệt logical resource id và physical location được phép hiển thị.

## 23. Checklist debug khi đường dẫn không tìm thấy

1. **Nguồn resource là gì?** `ClassPathResource`, `FileSystemResource`, `UrlResource` hay custom implementation?
2. **Location thực tế là gì?** Kiểm tra prefix `classpath:`, `file:`, URL schemes và separator.
3. **Có đúng runtime classpath không?** File trong `src/main/resources` đã được copy/đóng gói vào artifact chưa?
4. **Chạy IDE, Maven hay JAR?** `getFile()` có đang bị gọi trên một JAR entry?
5. **Đường dẫn có phụ thuộc working directory không?** Kiểm tra absolute path/cwd nếu dùng filesystem.
6. **Tên file có đúng case không?** Linux thường phân biệt chữ hoa/thường.
7. **Path có trỏ tới directory thay vì regular file không?**
8. **Quyền user/service account có đọc được không?** Thử với identity thực của process.
9. **`exists()` và `isReadable()` trả gì?** Dùng làm dữ liệu chẩn đoán, không làm bảo đảm.
10. **Exception cụ thể khi `getInputStream()` là gì?** Bảo toàn cause.
11. **Có symlink hoặc path traversal không?** Đặc biệt nếu path từ input.
12. **Có lỗi format/encoding sau khi đọc không?** Tách biệt lỗi I/O với parse.
13. **Nếu remote URL, timeout/status/TLS/DNS có vấn đề gì?**
14. **Có thể tái hiện bằng resource test nhỏ không?** Giảm noise.
15. **Policy có đúng không?** File optional phải thật sự optional, file required không được bị bỏ qua.

## 24. Test cases nên có

| Trường hợp | Điều cần kiểm tra |
| --- | --- |
| Classpath fixture tồn tại | `exists()==true`, mở/đọc UTF-8 thành công |
| Classpath fixture không tồn tại | `exists()==false`, mở stream báo IOException |
| ByteArrayResource | Đọc được, không cần File |
| FileSystemResource có file tạm | Đọc nội dung chính xác |
| Xóa file trước khi đọc | `getInputStream()` thất bại, không treo |
| Thay đổi permission | Lỗi được xử lý, không coi là missing tùy tiện |
| Resource trong JAR | `getInputStream()` chạy, không ép `getFile()` |
| Directory path | Không coi như regular file config |
| Filename có dấu tiếng Việt | Encoding/path xử lý đúng platform |
| Input có `../` | Không thoát khỏi root được phép |
| Input có symlink ra ngoài root | Bị policy ngăn đúng cách |
| File rỗng | Phân biệt file hợp lệ rỗng với thiếu file |
| File JSON/XML malformed | Parse error được giữ và báo |
| Remote URL timeout/403/404 | Có timeout/policy, không silent fallback |
| File quá lớn | Có streaming limit, không OOM |
| Multiple reads trên InputStreamResource | Không mặc định stream tái sử dụng được |

### Dùng temporary resources để test độc lập

`Files.createTempDirectory` và `Files.createTempFile` giúp test filesystem không phụ thuộc thư mục cài đặt của ứng dụng. `try/finally` hoặc JUnit cleanup đảm bảo xóa file. Với test classpath, để fixture dưới `src/test/resources` và kiểm tra nó được build tool đưa vào test classpath.

### Không hard-code absolute path của developer

Test có path `C:\Users\Alice\Desktop\...` hoặc `/home/alice/...` thường không chạy trên CI. Nếu cần test permissions hoặc symlink, nên đánh dấu test platform-specific và nêu rõ preconditions.

## 25. Một số nhầm lẫn cần tránh

**Nhầm 1: “Resource được tạo là file tồn tại.”** Không. Đó có thể chỉ là handle cho location chưa có.

**Nhầm 2: “exists true nghĩa là luôn đọc được.”** Không. Có thể thiếu quyền, I/O lỗi hoặc trạng thái thay đổi.

**Nhầm 3: “isReadable true thì khỏi bắt IOException.”** Không. Có race và các lỗi runtime khác.

**Nhầm 4: “ClasspathResource luôn có getFile.”** Sai khi nằm trong JAR mà không được bung ra filesystem.

**Nhầm 5: “file: đọc được file trong JAR.”** `file:` chỉ đến filesystem URL, không tự là JAR entry path. Dùng classpath/resource stream khi tài nguyên nằm trong archive.

**Nhầm 6: “Không có path prefix nghĩa là classpath.”** Tùy ResourceLoader/context implementation.

**Nhầm 7: “normalize() chống mọi path traversal.”** Không. Nó chưa tự xử lý symlinks và TOCTOU.

**Nhầm 8: “Resource.exists luôn nhanh.”** Với URL/remote implementation, phép kiểm tra có thể liên quan network I/O.

**Nhầm 9: “File tồn tại thì parse chắc chắn thành công.”** Không. Nội dung có thể sai định dạng/encoding.

**Nhầm 10: “IOException nên luôn được catch và dùng default.”** Không. Chỉ fallback khi business policy cho phép.

## 26. Quy trình áp dụng trong ứng dụng thật

Một quy trình thiết kế loader rõ ràng:

1. Xác định resource **required** hay **optional**.
2. Chọn location scheme rõ nghĩa: classpath, filesystem hoặc URL.
3. Nếu location do user cung cấp, validate root/scheme/size và symlinks trước thao tác.
4. Lấy `Resource` handle phù hợp.
5. Nếu cần, kiểm tra `exists()` và `isReadable()` để báo lỗi sớm.
6. Mở `getInputStream()` trong try-with-resources.
7. Đọc có giới hạn đối với dữ liệu không đáng tin; chọn đúng charset/parser.
8. Phân biệt I/O error, parse error, validation error, security error.
9. Đóng streams và giải phóng temporary resources.
10. Với dữ liệu quan trọng, validate checksum/schema/business constraints.
11. Giữ nguyên exception cause và cung cấp thông tin chẩn đoán an toàn.
12. Test lại trên đúng packaging (IDE/classes directory/JAR/container).

Không phải ứng dụng nào cũng cần mọi bước ở mức độ như nhau. Nhưng **bước xử lý I/O thực** và **phân biệt lỗi/vắng mặt** là tối thiểu khi file quan trọng.

## 27. Câu hỏi tự kiểm tra

**Câu 1.** Tạo `new ClassPathResource("missing.txt")` có ném lỗi ngay không?

**Đáp án:** Thông thường không. Nó tạo một handle; cần `exists()` hoặc mở stream để xác định resource có thật.

**Câu 2.** `exists()==true` có đủ để chắc chắn `getInputStream()` thành công?

**Đáp án:** Không. File có thể bị thay đổi/xóa, thiếu quyền hoặc gặp lỗi I/O giữa hai thao tác.

**Câu 3.** Khi tài nguyên nằm trong JAR, nên đọc bằng gì?

**Đáp án:** `getInputStream()`. Không mặc định ép `getFile()` thành một File trên filesystem.

**Câu 4.** `isOpen()==true` thường cảnh báo điều gì?

**Đáp án:** Resource có thể đang giữ một input stream mở và không phù hợp đọc nhiều lần, như `InputStreamResource`.

**Câu 5.** File optional không tồn tại khác file có nhưng đọc lỗi thế nào?

**Đáp án:** File không tồn tại có thể được phép fallback; file có nhưng đọc lỗi thường cần báo lỗi vì quyền/dữ liệu/tình trạng hệ thống đã bất thường.

**Câu 6.** Vì sao `normalize()` chưa đủ chống người dùng đọc file qua symlink?

**Đáp án:** Nó xử lý segments theo cú pháp, không bảo đảm target thực sau symlink còn ở trong allowed root.

**Câu 7.** `ResourceLoader.getResource(...)` trả `Resource` chứng minh điều gì?

**Đáp án:** Nó trả một resource descriptor cho location; không chứng minh dữ liệu đọc được.

**Câu 8.** `isReadable()` trả false nghĩa gì?

**Đáp án:** Theo contract resource, nội dung không thể đọc qua stream trong phép kiểm tra hiện tại.

**Câu 9.** Có thể gọi `contentLength()` trên mọi `InputStreamResource` trước khi đọc không?

**Đáp án:** Không nên. Resource stream một lần có thể bị tiêu thụ để xác định length.

**Câu 10.** Nếu cần giữ root directory cố định, nên làm gì?

**Đáp án:** Resolve path bên dưới root đáng tin, kiểm tra segments/canonical path theo chính sách symlink và bảo vệ khỏi TOCTOU/OS permission bypass.

## 28. Checklist cuối bài

- [ ] Có biết Resource thuộc loại nào và có đại diện cho file/JAR/URL/bytes/stream không?
- [ ] Không nhầm handle với tài nguyên tồn tại?
- [ ] Phân biệt `exists()`, `isReadable()`, `isOpen()` và `isFile()`?
- [ ] Vẫn catch `IOException` tại `getInputStream()` dù đã precheck?
- [ ] Có đóng stream qua try-with-resources?
- [ ] Có tránh `getFile()` khi resource nằm trong JAR?
- [ ] Có chọn prefix location phù hợp `classpath:`/`file:`?
- [ ] Đường dẫn tương đối được giải quyết ở đâu, có rõ working directory?
- [ ] Có phân biệt required resource và optional resource không?
- [ ] Không lẫn permission/parse errors với missing resource?
- [ ] Có giới hạn kích thước dữ liệu và validate input chưa?
- [ ] Có chặn path traversal, symlink escape và URL schemes ngoài danh sách cho phép?
- [ ] Có bảo toàn exception root cause và redaction đường dẫn nhạy cảm?
- [ ] Có test bằng temporary files, missing fixture và packaged JAR?
- [ ] Có xử lý URL timeouts/retry policy nếu đọc remote resource?

## 29. Tóm tắt

- **`Resource` là một handle**; việc có object không bảo đảm file đã tồn tại.
- **`exists()`** kiểm tra tồn tại; **`isReadable()`** cho biết khả năng đọc theo implementation; cả hai đều không bảo đảm I/O sau đó thành công.
- **`getInputStream()` + `try-with-resources` + `IOException` handling** mới là đường đọc thực tế.
- **`isFile()`/`getFile()`** liên quan filesystem, không dùng máy móc cho resource nằm trong JAR.
- **`isOpen()`** giúp nhận diện resource dùng stream mở sẵn, đặc biệt `InputStreamResource`.
- **Classpath và filesystem paths khác nhau**; file ở source tree không đương nhiên có ở runtime.
- **Path traversal, symlinks, relative paths, URL schemes, file permissions và TOCTOU** cần được xem xét theo hệ thống triển khai.
- **Optional missing** có thể fallback theo policy; **unreadable, malformed hoặc corrupt resource** không nên bị nuốt lỗi.
- Mỗi đường đọc resource quan trọng cần có test trên packaging thực tế, vì hành vi IDE và JAR có thể khác.

## 30. Tài liệu chính thức

- [Spring Framework Reference — Resources](https://docs.spring.io/spring-framework/reference/core/resources.html): Resource, các implementations và ResourceLoader.
- [Spring Framework 6.1 — Resource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/Resource.html): `exists`, `isReadable`, `isOpen`, `isFile`, `getInputStream`, `getFile`.
- [Spring Framework 6.1 — ClassPathResource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/ClassPathResource.html): resource classpath, lấy stream và giới hạn filesystem File.
- [Spring Framework 6.1 — FileSystemResource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/FileSystemResource.html): File/Path, `isReadable` và file access.
- [Spring Framework 6.1 — InputStreamResource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/InputStreamResource.html): opened stream, `isOpen` và single-use lifecycle.
- [Spring Framework 6.1 — ByteArrayResource Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/ByteArrayResource.html): byte-backed resource phù hợp đọc lặp.
- [Spring Framework 6.1 — ResourceLoader Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/core/io/ResourceLoader.html): tìm resource handle theo location.
- [Java SE 17 — Files API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): file checks, stream I/O, temporary files và NIO exceptions.


</details>

- [Quay lại đầu trang](#back-to-top)
