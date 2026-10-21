<a id="back-to-top"></a>

# Nội dung HTTP

## Menu
- [HttpMessageConverter và quá trình đọc/ghi request–response body trong MVC](#http-message-bodies-001)
- [Chọn bộ chuyển đổi cho chuỗi, byte&#91;&#93;, Resource, JSON và XML](#http-message-bodies-002)
- [Cấu hình bộ chuyển đổi: thay thế mặc định hay mở rộng danh sách](#http-message-bodies-003)
- [Tuần tự hóa JSON, DTO, kiểu generic và tham chiếu vòng](#http-message-bodies-004)
- [Jackson và cấu hình ánh xạ dữ liệu phù hợp với phiên bản Spring](#http-message-bodies-005)
- [Thương lượng nội dung theo Accept và lựa chọn media type](#http-message-bodies-006)
- [Phân biệt lỗi JSON sai cú pháp, JSON sai kiểu và dữ liệu không hợp lệ](#http-message-bodies-007)
- [RequestBodyAdvice, ResponseBodyAdvice và giới hạn biến đổi nội dung dùng chung](#http-message-bodies-008)

## <a id="http-message-bodies-001">HttpMessageConverter và quá trình đọc/ghi request–response body trong MVC</a>

<details>
<summary>Xem chi tiết</summary>

# HttpMessageConverter và quá trình đọc/ghi request–response body trong Spring MVC

## 1. Mục tiêu và phạm vi

`HttpMessageConverter<T>` là cơ chế Spring Framework dùng để **đọc byte từ HTTP request body thành dữ liệu Java** và **ghi dữ liệu Java thành HTTP response body**. Đây là thành phần nền tảng phía sau `@RequestBody`, `@ResponseBody`, `@RestController`, `HttpEntity` và `ResponseEntity` trong **Spring Web MVC**.

Sau bài học, bạn có thể:

1. Trình bày điểm bắt đầu và kết thúc của quá trình đọc/ghi body trong một request MVC.
2. Giải thích Spring chọn converter dựa trên kiểu Java, `Content-Type`, `Accept` và các điều kiện của handler.
3. Phân biệt converter JSON, chuỗi, byte array, form data và tài nguyên nhị phân.
4. Xây dựng controller nhận JSON/text/binary rồi tạo response có status, headers và body đúng media type.
5. Phân biệt lỗi chọn converter với lỗi deserialize, validation và lỗi ghi response; xác định thời điểm cần tạo converter tùy biến.
6. Tránh các nhầm lẫn giữa `@RequestBody`, `@ModelAttribute`, `@RequestPart`, `@ResponseBody` và render view.

Ví dụ dùng **Java 17, Spring Boot 3.3.5 (Spring Framework 6.1.x)** để khởi động một ứng dụng Spring MVC. Spring Boot cung cấp cấu hình mặc định và thư viện JSON thông dụng; hành vi được giải thích thuộc Spring MVC. Những phiên bản Spring Framework mới hơn có thể có converter và API cấu hình khác, vì vậy phần API cụ thể bên dưới được gắn với thế hệ 6.1.

## 2. Body của HTTP là gì, và converter làm gì?

Request ví dụ:

```http
POST /api/messages HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json
X-Request-Id: req-42

{"message":"Hi","copies":2}
```

- `Content-Type` mô tả **kiểu biểu diễn request body được gửi đến server**.
- `Accept` mô tả **kiểu biểu diễn response body mà client có thể nhận**.
- Hai header này là thông tin về **media type**, không phải cấu trúc class Java.
- JSON trong request là byte/chuỗi được mã hóa; converter phải chuyển nó thành object mà controller khai báo.

Nếu method của controller nhận `EchoInput` và trả `EchoOutput`, đường đi thường là:

```text
Request bytes (JSON)
        |
        v
Servlet container / HttpServletRequest
        |
        v
DispatcherServlet + HandlerMapping chọn controller
        |
        v
Argument resolver gặp @RequestBody EchoInput
        |
        v
HttpMessageConverter đọc JSON -> EchoInput
        |
        v
(Bean Validation nếu có @Valid) -> controller
        |
        v
Controller trả EchoOutput hoặc ResponseEntity<EchoOutput>
        |
        v
Return-value handler + HttpMessageConverter ghi EchoOutput -> JSON bytes
        |
        v
HTTP response (status + headers + body)
```

Ví dụ phản hồi thành công:

```http
HTTP/1.1 201
Content-Type: application/json
X-Converted-By: spring-mvc

{"echoed":"HiHi","utf16Units":4}
```

Spring không tự biến mọi object thành JSON trong mọi trường hợp. Việc đọc/ghi thành công phụ thuộc vào việc có **converter thích hợp được đăng ký**, media type khớp và object có thể serialize/deserialize. Các ví dụ HTTP ở bài học chỉ biểu diễn những headers quan trọng; headers hệ thống có thể khác theo container và cấu hình.

### HttpMessageConverter không làm tất cả việc xử lý HTTP

`HttpMessageConverter` không trực tiếp quyết định endpoint nào được gọi, không thay thế authorization hay business logic và không tự đảm bảo Bean Validation. Phân trách nhiệm cơ bản:

| Thành phần | Trách nhiệm |
| --- | --- |
| `DispatcherServlet` / `HandlerMapping` | Chọn handler theo URL, HTTP method, `consumes`, `produces`, điều kiện khác |
| Argument resolver | Tạo tham số Java cho method, khi cần thì yêu cầu converter đọc body |
| `HttpMessageConverter` | Chuyển đổi giữa HTTP body và kiểu Java được hỗ trợ |
| JSON mapper (ví dụ Jackson) | Thực hiện chi tiết serialize/deserialize JSON cho JSON converter |
| Bean Validation | Kiểm tra constraint như `@NotBlank`, `@Min` sau khi bind |
| Return-value handler | Chọn cách xử lý giá trị trả về: response body, view, async response… |
| `ResponseEntity` | Đóng gói status + headers + body cho response |

## 3. Giao diện HttpMessageConverter<T>

Trong Spring Framework 6.1, giao diện `org.springframework.http.converter.HttpMessageConverter<T>` mô tả hợp đồng đọc/ghi. Các thao tác tiêu biểu là:

```java
boolean canRead(Class<?> clazz, MediaType mediaType);
boolean canWrite(Class<?> clazz, MediaType mediaType);
List<MediaType> getSupportedMediaTypes();
T read(Class<? extends T> clazz, HttpInputMessage inputMessage)
        throws IOException, HttpMessageNotReadableException;
void write(T value, MediaType contentType,
           HttpOutputMessage outputMessage)
        throws IOException, HttpMessageNotWritableException;
```

Đây là **hình dạng API rút gọn để giải thích**, chưa phải một interface Java hoàn chỉnh có đủ imports/annotations/generic overloads.

Ý nghĩa:

- `canRead`: converter có khả năng đọc nội dung theo **kiểu Java đích** và media type của request không?
- `read`: đọc bytes từ `HttpInputMessage.getBody()` và tạo giá trị Java. Nếu nội dung sai cấu trúc/không chuyển đổi được, converter có thể ném `HttpMessageNotReadableException`.
- `canWrite`: converter có khả năng xuất object theo media type mong muốn không?
- `write`: serialize object và ghi bytes qua `HttpOutputMessage.getBody()`, đồng thời thiết lập thông tin headers cần thiết.
- `getSupportedMediaTypes`: tập media types mà converter khai báo hỗ trợ; các converter còn có khả năng xét generic types thông qua những giao diện mở rộng.

Ví dụ `List<EchoInput>` đòi hỏi giữ thông tin generic để Jackson biết kiểu phần tử. Spring MVC có hỗ trợ những converter/giao diện chuyên biệt như `GenericHttpMessageConverter`, chứ không chỉ suy từ `List.class` rồi đoán.

Converter được Spring MVC giữ trong hạ tầng `RequestMappingHandlerAdapter` và các thành phần xử lý response liên quan. Ở client như `RestTemplate` hoặc `RestClient` cũng có khái niệm message converters, nhưng bài này tập trung phía server MVC.

## 4. Spring MVC đọc request body như thế nào?

Khi request khớp mapping sau:

```java
@PostMapping(path = "/messages",
             consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> create(@RequestBody EchoInput input) {
    // ...
}
```

Luồng chính:

1. Handler mapping kiểm tra phương thức, URL và ràng buộc `consumes`. `consumes="application/json"` nói rằng endpoint nhận JSON body.
2. Argument resolver xác định tham số `@RequestBody EchoInput` cần đọc body.
3. MVC duyệt các message converters đã đăng ký, tìm converter có thể đọc **`EchoInput` từ `application/json`**; trong cấu hình Boot 3.3.5 có Jackson 2, đó thường là `MappingJackson2HttpMessageConverter`.
4. Converter lấy body dạng bytes từ request; JSON mapper tạo `EchoInput` và chuyển các trường tương ứng theo kiểu Java.
5. Nếu có `@Valid` và validator phù hợp, Spring kiểm tra constraint; controller chỉ chạy sau bước xử lý tham số thành công.
6. Nếu body bắt buộc nhưng không đọc được, method có thể chưa được gọi và MVC chuyển lỗi thành HTTP response.

`@RequestBody` mặc định có `required = true`; bạn có thể đặt `@RequestBody(required = false)` nếu nghiệp vụ cho phép không có body, rồi **phải xử lý giá trị null**. Đừng nhầm request body thiếu với JSON object rỗng `{}`: object rỗng vẫn là nội dung hợp lệ về cú pháp nhưng có thể thiếu fields hoặc vi phạm validation.

### @RequestBody, HttpEntity và những cách nhận dữ liệu khác

```java
public void json(@RequestBody EchoInput input) { /* ... */ }
public void both(HttpEntity<EchoInput> request) { /* ... */ }
public void header(@RequestHeader("X-Request-Id") String id,
                   @RequestBody EchoInput input) { /* ... */ }
```

Ba method trên chỉ là **chữ ký minh họa**, cần annotations mapping và đặt trong một controller để hoạt động. `HttpEntity<EchoInput>` chứa cả `getBody()` và `getHeaders()`, và được argument resolver chuyển đổi body thông qua converter; không cần đặt `@RequestBody` lên `HttpEntity`. Nếu chỉ cần một header, `@RequestHeader` cùng `@RequestBody` thường rõ ràng hơn.

Cần tách biệt:

- `@RequestParam`, `@ModelAttribute`: thông thường dùng binding tham số query/form thông qua `WebDataBinder`, **không phải** con đường deserialize JSON chuẩn của `@RequestBody`.
- `@RequestPart`: dùng cho một part của multipart request, có thể chuyển part JSON bằng converter thích hợp.
- `HttpServletRequest.getInputStream()`: cho phép thao tác trực tiếp stream body khi cần raw bytes; tự đọc stream có thể khiến framework không còn dữ liệu để đọc lại.
- `MultipartFile`: dùng cơ chế multipart và lưu trữ tệp upload, không nên thay bằng một `@RequestBody byte[]` khổng lồ cho mọi use case.

Đặc biệt, body của `application/x-www-form-urlencoded` có thể đã bị Servlet container parse khi truy cập request parameters. Với HTML form thường nên dùng `@RequestParam` hoặc `@ModelAttribute`, không dựa vào việc đọc lại cùng body qua `@RequestBody`.

## 5. Spring MVC ghi response body như thế nào?

Một controller có thể trả object Java:

```java
@RestController
public class ProductController {
    @GetMapping(path = "/api/products/7",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ProductDto findOne() {
        return new ProductDto(7L, "Book");
    }

    public record ProductDto(long id, String name) {}
}
```

Vì `@RestController` tương đương `@Controller` cộng semantics `@ResponseBody` trên các handler methods, giá trị `ProductDto` được serialize thành HTTP body. Nếu Jackson converter phù hợp được đăng ký, response tương ứng có thể là:

```http
HTTP/1.1 200
Content-Type: application/json

{"id":7,"name":"Book"}
```

Với `@Controller` thông thường, một giá trị trả về kiểu `String` **có thể được hiểu là tên view để render**, tùy chữ ký/annotation. Khi muốn trả text hoặc JSON body, dùng `@ResponseBody`, `@RestController` hoặc `ResponseEntity` phù hợp.

`ResponseEntity<T>` bổ sung status và headers bên cạnh body:

```java
return ResponseEntity.status(HttpStatus.CREATED)
        .header("X-Created-By", "mvc")
        .body(new ProductDto(7L, "Book"));
```

Return-value handler lấy object `ProductDto`, xác định media type có thể viết (xét `produces`, client `Accept` và converter), chọn converter, sau đó viết bytes. `ResponseEntity` không bỏ qua converter: **body của nó vẫn phải được chuyển thành định dạng có thể gửi**.

Khi trả `ResponseEntity<Void>` hoặc status `204 No Content`, thông thường không có body cần serialize. Đừng hứa rằng một object vẫn sẽ được ghi xuống body của status cấm body. Với `ResponseEntity<Resource>`, MVC có converter và hành vi hỗ trợ tài nguyên; vấn đề file download, range requests và streaming cần xem xét cấu hình/kiểu Resource cụ thể.

### @ResponseBody không đồng nghĩa với “mặc định JSON”

Nếu trả `String` từ `@RestController`, `StringHttpMessageConverter` thường xử lý chuỗi thành text. Nếu trả object, JSON converter có thể xử lý khi JSON phù hợp với media type mong muốn. Khi dùng `produces = "application/json"`, đảm bảo nội dung do converter ghi thực sự là JSON hợp lệ; không tự nối chuỗi trông giống JSON rồi khai báo bừa Content-Type.

## 6. Các converter thường gặp

Trong Spring Framework 6.1, những loại quan trọng gồm:

| Converter | Kiểu Java tiêu biểu | Nội dung có thể xử lý |
| --- | --- | --- |
| `MappingJackson2HttpMessageConverter` | DTO, record, `Map`, `List<Dto>` | JSON, thường `application/json` và `application/*+json`, khi có Jackson 2 |
| `StringHttpMessageConverter` | `String` | Text, thường `text/plain` và các text media types |
| `ByteArrayHttpMessageConverter` | `byte[]` | Raw bytes; converter có thể hỗ trợ `*/*` |
| `ResourceHttpMessageConverter` | `Resource` | Dữ liệu tài nguyên/tệp |
| `FormHttpMessageConverter` | `MultiValueMap` | Đọc/ghi `application/x-www-form-urlencoded`; hỗ trợ **ghi** multipart |
| `MappingJackson2XmlHttpMessageConverter` | DTO/record nếu mapper hỗ trợ | XML khi thêm dependency Jackson XML |
| `SourceHttpMessageConverter` | XML `Source` | Các XML `Source` được hỗ trợ |

Bảng phản ánh hệ Spring Framework 6.x/Jackson 2; không nên bê nguyên tên converter giữa các major versions mà không kiểm tra tài liệu phiên bản. Việc hỗ trợ XML, Protobuf hay thư viện thứ ba phụ thuộc classpath, cấu hình và độ ưu tiên. `FormHttpMessageConverter` **không phải** cơ chế duy nhất nhận multipart upload: Spring MVC còn có multipart resolution và các argument resolvers như `@RequestPart` / `MultipartFile`.

### Vì sao String và byte[] có thể gây nhầm?

`StringHttpMessageConverter` và `ByteArrayHttpMessageConverter` có phạm vi media type tương đối rộng. Vì thế:

- Với Java return type `String`, converter text có thể được chọn trước converter JSON tùy cấu hình/thứ tự; body có thể là các ký tự nguyên văn. Đừng coi `"hello"` là JSON string hợp lệ chỉ vì response được dán header JSON.
- Với `byte[]`, dữ liệu trả về là bytes thực; nếu đó là PDF, hình ảnh hoặc binary, hãy khai báo `Content-Type` phù hợp (ví dụ `application/pdf` / `application/octet-stream`), và cân nhắc `Content-Disposition` khi tải tệp.
- Với JSON DTO, giữ response type là record/DTO giúp Jackson đảm nhiệm escape ký tự, null policy và biểu diễn JSON đúng quy tắc.

## 7. Content-Type, Accept, consumes và produces

Bốn tên này thường bị trộn lẫn:

| Tên | Bên đặt | Ý nghĩa chính |
| --- | --- | --- |
| Request `Content-Type` | Client | Body request đang được gửi theo kiểu gì |
| Request `Accept` | Client | Client muốn/cho phép nhận response media type nào |
| Mapping `consumes` | Server/controller | Endpoint được chọn cho kiểu request body nào |
| Mapping `produces` | Server/controller | Endpoint có thể tạo response theo media type nào |

Ví dụ:

```java
@PostMapping(path = "/reports",
             consumes = MediaType.APPLICATION_JSON_VALUE,
             produces = MediaType.APPLICATION_JSON_VALUE)
public ReportResponse create(@RequestBody ReportRequest request) {
    // ...
}
```

Đây là method minh họa; `ReportRequest`/`ReportResponse` là DTO giả định. Nếu client gửi `Content-Type: text/plain`, mapping JSON không thỏa: HTTP **415 Unsupported Media Type** thường được trả về. Nếu client gửi JSON hợp lệ nhưng `Accept: application/xml` và endpoint chỉ tạo JSON, server có thể trả **406 Not Acceptable**. Nếu client gửi `Content-Type: application/json` nhưng JSON sai cú pháp, converter đã được chọn mà việc đọc thất bại: thường **400 Bad Request**.

Bốn khía cạnh khác nhau:

1. **Endpoint matching:** Spring chọn method có route và media conditions khớp.
2. **Reader compatibility:** kiểu tham số Java + request media type phải được converter đọc được.
3. **Writer compatibility:** kiểu trả về Java + negotiated response media type phải được converter ghi được.
4. **Dữ liệu hợp lệ:** payload phải parse được, dữ liệu deserialize được và đạt các constraint cần thiết.

Đừng nhầm `Accept` với `Content-Type`: gửi JSON vào không có nghĩa client bắt buộc phải nhận JSON ra; hai chiều độc lập. Việc thương lượng media type có thể chịu ảnh hưởng bởi cấu hình `ContentNegotiationConfigurer`, headers, converter được đăng ký và handler mapping. Trong API hiện đại, header `Accept` là phương tiện tiêu biểu.

Các vấn đề encoding cũng thuộc biểu diễn body: `Content-Type: text/plain;charset=UTF-8` cho biết charset của text; JSON hiện đại thông thường dùng UTF-8. Không tự chuyển binary thành `String` nếu cần bảo toàn byte chính xác. `String.length()` trong Java đếm đơn vị mã UTF-16, còn HTTP `Content-Length` nói về **số byte**, hai giá trị không nhất thiết bằng nhau.

## 8. Ví dụ Spring MVC đầy đủ chạy được với Java 17

Ví dụ minh họa ba dạng request body (JSON/text/binary) và ba dạng response (JSON/text/binary). Đây là ứng dụng thực thi được; không cần database.

Cấu trúc:

```text
mvc-converters-demo/
├── pom.xml
└── src/main/java/demo/converter/
    ├── ConverterApplication.java
    └── BodyController.java
```

**`pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>mvc-converters-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
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
```

**`src/main/java/demo/converter/ConverterApplication.java`**

```java
package demo.converter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConverterApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConverterApplication.class, args);
    }
}
```

**`src/main/java/demo/converter/BodyController.java`**

```java
package demo.converter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class BodyController {

    public record EchoInput(@NotBlank String message,
                            @Min(1) int copies) {}

    public record EchoOutput(String echoed, int utf16Units) {}

    @PostMapping(path = "/messages",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EchoOutput> createMessage(
            @Valid @RequestBody EchoInput input) {

        String echoed = input.message().repeat(input.copies());
        EchoOutput result = new EchoOutput(echoed, echoed.length());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Converted-By", "spring-mvc")
                .body(result);
    }

    @PostMapping(path = "/plain",
                 consumes = MediaType.TEXT_PLAIN_VALUE,
                 produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> readPlain(@RequestBody String text) {
        return ResponseEntity.ok("text:" + text);
    }

    @PostMapping(path = "/bytes",
                 consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Integer> readBytes(@RequestBody byte[] bytes) {
        return Map.of("byteCount", bytes.length);
    }

    @GetMapping(path = "/download",
                produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> download() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sample.bin\"")
                .body(new byte[]{65, 66, 67});
    }

    @GetMapping(path = "/health-text",
                produces = MediaType.TEXT_PLAIN_VALUE)
    public String healthText() {
        return "ready";
    }
}
```

Giải thích điểm then chốt của từng endpoint:

- `/api/messages`: Jackson JSON converter đọc request JSON thành `EchoInput`; `@Valid` kiểm tra message không trống và copies ít nhất bằng 1; controller tạo `EchoOutput`; JSON converter ghi response status `201` kèm header tùy chỉnh.
- `/api/plain`: `StringHttpMessageConverter` đọc và ghi text. Phần body chỉ là text; không có JSON escaping.
- `/api/bytes`: `ByteArrayHttpMessageConverter` đọc bytes không diễn giải chúng là text; controller trả `Map` để Jackson ghi response JSON.
- `/api/download`: controller tạo ba byte `65,66,67`, converter byte array ghi ra HTTP output và `Content-Disposition` đề xuất tên file.
- `/api/health-text`: `@RestController` bảo đảm chuỗi trả về là **response body**, không được hiểu là tên template view.

Chạy từ thư mục chứa `pom.xml`, với JDK 17+ và Maven được cài đặt:

```bash
mvn spring-boot:run
```

### 8.1. JSON vào, JSON ra

```bash
curl -i -X POST http://localhost:8080/api/messages \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"message":"Hi","copies":2}'
```

Phần status, header và body dự kiến:

```http
HTTP/1.1 201
Content-Type: application/json
X-Converted-By: spring-mvc

{"echoed":"HiHi","utf16Units":4}
```

Giá trị `4` ở đây là `"HiHi".length()`, không phải mặc định biểu diễn độ dài mọi chuỗi Unicode. Thứ tự thuộc tính JSON ở đây là cách biểu diễn điển hình; test client nên so sánh các field, không dựa vào thứ tự keys.

### 8.2. Text vào, text ra

```bash
curl -s -X POST http://localhost:8080/api/plain \
  -H 'Content-Type: text/plain' \
  -H 'Accept: text/plain' \
  --data-binary 'Hello MVC'
```

Response body chính xác:

```text
text:Hello MVC
```

`--data-binary` giúp gửi đúng dữ liệu text của ví dụ. Nếu cần gửi ký tự quốc tế, hãy thống nhất UTF-8 ở client, headers và converter.

### 8.3. Bytes vào, JSON ra

```bash
curl -s -X POST http://localhost:8080/api/bytes \
  -H 'Content-Type: application/octet-stream' \
  -H 'Accept: application/json' \
  --data-binary 'ABC'
```

Response body:

```json
{"byteCount":3}
```

Đây là ba byte ASCII `A=65`, `B=66` và `C=67`. Khi upload dữ liệu lớn, không nên luôn đọc toàn bộ body vào `byte[]`; phải cân nhắc giới hạn payload, multipart handling hoặc streaming chuyên biệt.

### 8.4. Bytes ra, tải về tệp

```bash
curl -s -D headers.txt \
  -o sample.bin \
  http://localhost:8080/api/download
```

Tệp `sample.bin` có ba byte `41 42 43` ở hệ hex, tức `ABC` ở ASCII. Header có giá trị quan trọng:

```http
Content-Type: application/octet-stream
Content-Disposition: attachment; filename="sample.bin"
```

Lệnh `-D` chỉ lưu response headers vào `headers.txt`; `-o` lưu response body vào `sample.bin`. Đừng dùng chế độ hiển thị terminal để kiểm tra binary tùy ý, vì byte có thể không phải text đọc được.

### 8.5. Lỗi do dữ liệu hoặc media type

Gửi sai constraint:

```bash
curl -i -X POST http://localhost:8080/api/messages \
  -H 'Content-Type: application/json' \
  -d '{"message":"","copies":0}'
```

Dự kiến response status `400 Bad Request` vì `@NotBlank` và `@Min(1)` không đạt. Hình dạng JSON lỗi mặc định có thể thay đổi theo cấu hình Spring Boot và exception handling; không nên đưa một JSON error body giả định thành contract.

Gửi sai request media type:

```bash
curl -i -X POST http://localhost:8080/api/messages \
  -H 'Content-Type: text/plain' \
  --data-binary '{"message":"Hi","copies":2}'
```

Thường nhận `415 Unsupported Media Type` vì mapping `consumes` chỉ nhận JSON, mặc dù nội dung text trông giống JSON. Server dùng **header thật sự**, không nhìn ký tự `{` để tự suy luận định dạng.

Gửi Accept không khớp:

```bash
curl -i -X POST http://localhost:8080/api/messages \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/xml' \
  -d '{"message":"Hi","copies":2}'
```

Thường nhận `406 Not Acceptable` vì mapping `produces` chỉ công bố JSON. Lệnh này minh họa **đầu ra** không được client chấp nhận.

### 8.6. Thử bằng PowerShell

Nếu shell đang dùng PowerShell, có thể gửi request JSON không phụ thuộc cú pháp xuống dòng của Bash:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/messages' -ContentType 'application/json' -Body '{"message":"Hi","copies":2}'
```

PowerShell thường deserialize JSON thành đối tượng khi dùng `Invoke-RestMethod`, nên đầu ra terminal không nhất thiết là nguyên văn JSON bytes; dùng `Invoke-WebRequest` hoặc công cụ HTTP khác nếu muốn đối chiếu status/headers/raw representation.

## 9. Tùy biến danh sách message converters

Với Spring MVC 6.1, `WebMvcConfigurer` có hai extension points thường dùng:

```java
@Override
public void configureMessageConverters(
        List<HttpMessageConverter<?>> converters) {
    // Khai báo danh sách theo chủ đích.
}

@Override
public void extendMessageConverters(
        List<HttpMessageConverter<?>> converters) {
    // Điều chỉnh danh sách đã được cấu hình.
}
```

Hai method này là **đoạn minh họa**, không phải một lớp đầy đủ có imports. Trong Spring MVC tự cấu hình bằng `WebMvcConfigurer`, dùng `configureMessageConverters` để khai báo converter tùy chỉnh có thể **thay thế thiết lập converter mặc định**; dùng `extendMessageConverters` để bổ sung vào danh sách đã có thường ít rủi ro hơn. Với Spring Boot, cũng có cơ chế tự phát hiện các `HttpMessageConverter` beans; phải xem behavior của version và tránh đăng ký trùng converter.

Không sửa thứ tự converter tùy tiện. Nếu converter quá rộng được đặt trước JSON converter và luôn báo `canRead`/`canWrite` là true cho mọi kiểu, nó có thể chọn nhầm và phá vỡ những endpoint bình thường.

### Ví dụ converter cho text/x-compact

Giả sử ứng dụng cần media type riêng `text/x-compact` để trao đổi một dòng văn bản. Converter mẫu bên dưới được tách thành ba snippet; chúng có thể được thêm vào **ứng dụng ở mục 8** trong cùng package `demo.converter`. Đây là phần mở rộng tùy chọn, ứng dụng ở mục 8 chạy độc lập mà không cần các file này.

**`CompactLine.java`**

```java
package demo.converter;

public record CompactLine(String value) {}
```

**`CompactLineConverter.java`**

```java
package demo.converter;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class CompactLineConverter
        extends AbstractHttpMessageConverter<CompactLine> {

    public CompactLineConverter() {
        super(new MediaType(
                "text", "x-compact", StandardCharsets.UTF_8));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return CompactLine.class == clazz;
    }

    @Override
    protected CompactLine readInternal(
            Class<? extends CompactLine> clazz,
            HttpInputMessage input) throws IOException {
        byte[] raw = input.getBody().readAllBytes();
        return new CompactLine(
                new String(raw, StandardCharsets.UTF_8));
    }

    @Override
    protected void writeInternal(
            CompactLine line, HttpOutputMessage output)
            throws IOException {
        output.getBody().write(
                line.value().getBytes(StandardCharsets.UTF_8));
    }
}
```

**`CompactConverterConfiguration.java`**

```java
package demo.converter;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CompactConverterConfiguration implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(
            List<HttpMessageConverter<?>> converters) {
        converters.add(0, new CompactLineConverter());
    }
}
```

**Thêm hai method sau vào `BodyController`**; cần import `org.springframework.web.bind.annotation.GetMapping` (file chính đã có `GetMapping`) và dùng record `CompactLine` cùng package:

```java
@PostMapping(path = "/compact",
             consumes = "text/x-compact",
             produces = "text/x-compact")
public CompactLine compact(@RequestBody CompactLine input) {
    return new CompactLine(input.value().toUpperCase(
            java.util.Locale.ROOT));
}

@GetMapping(path = "/compact",
            produces = "text/x-compact")
public CompactLine compactExample() {
    return new CompactLine("hello");
}
```

Hai method là **đoạn cần chèn vào class controller**, không phải file Java đứng riêng. Client:

```bash
curl -s -X POST http://localhost:8080/api/compact \
  -H 'Content-Type: text/x-compact' \
  -H 'Accept: text/x-compact' \
  --data-binary 'hello'
```

Response body:

```text
HELLO
```

Lưu ý về converter tùy biến:

- `supports` chỉ cho class `CompactLine`; `new MediaType(...)` giới hạn media type. Thiết kế hẹp tránh tranh chấp với converter mặc định.
- `readInternal` đọc bytes rồi giải mã UTF-8; `writeInternal` mã hóa giá trị thành UTF-8. Đây là converter demo, **chưa xử lý validation cấu trúc, độ dài, ký tự cấm hoặc giới hạn request size**.
- `readAllBytes()` nạp toàn body vào RAM: chỉ dùng khi request đã được giới hạn dung lượng. Với stream lớn cần thiết kế khác.
- `extendMessageConverters` bảo toàn danh sách mặc định; chèn converter ở đầu trong ví dụ giúp media type riêng được xử lý trước các converter rộng. Trong sản phẩm thật, xem xét sự xung đột của các media types đã đăng ký.
- Nếu định dạng chỉ là JSON với cách đặt tên field khác, thường **cấu hình Jackson ObjectMapper/serializer** thích hợp hơn viết một converter thủ công.
- Converter chỉ đảm nhận body. Endpoint vẫn cần mapping `consumes`/`produces`, validation và error handling đúng như bình thường.

## 10. RequestBodyAdvice và ResponseBodyAdvice nằm ở đâu?

Spring MVC còn cung cấp extension points để can thiệp ở các điểm quanh quá trình chuyển đổi:

- `RequestBodyAdvice` có thể can thiệp khi request body chuẩn bị được đọc hoặc vừa chuyển thành object cho các tham số như `@RequestBody`/`HttpEntity`, tùy phase và method mà advice triển khai.
- `ResponseBodyAdvice` được gọi ở bước chuẩn bị ghi return value, sau khi một converter đã được chọn và trước khi body được ghi ra response.

Các advice thường được khai báo thông qua `@ControllerAdvice` và có `supports(...)` để chọn phạm vi phù hợp. Chúng hữu ích khi có yêu cầu thống nhất về envelope, auditing metadata hoặc điều chỉnh representation. Tuy nhiên:

- Không nên dùng advice để lén đổi contract JSON mà client không biết.
- Nếu `ResponseBodyAdvice` thay object bằng kiểu mà converter đã chọn **không thể ghi**, request có thể lỗi ở bước output. Đặc biệt phải thận trọng với response kiểu `String`.
- Không dùng advice để đọc lại request body nếu request stream đã tiêu thụ; tránh log nội dung nhạy cảm.
- Authentication, authorization, request size và business validation có vị trí xử lý riêng, không được xem advice là giải pháp thay thế.

## 11. Lỗi thường gặp và cách phân tích

| Dấu hiệu | Giai đoạn thường gặp | Nguyên nhân cần kiểm tra |
| --- | --- | --- |
| `415 Unsupported Media Type` | Mapping/chọn request reader | Header `Content-Type` không khớp `consumes` hoặc không có converter phù hợp |
| `406 Not Acceptable` | Negotiation/chọn response writer | `Accept` không khớp `produces` hoặc không có converter viết được |
| `400 Bad Request` | Request conversion hoặc validation | JSON hỏng, body thiếu, field type sai, constraint lỗi |
| `HttpMessageNotReadableException` | Converter đọc request | Deserialize thất bại, dữ liệu không theo cấu trúc cần thiết |
| `MethodArgumentNotValidException` | Validation sau khi tạo argument | Object vi phạm Bean Validation, khi không xử lý qua `BindingResult` |
| `HttpMessageNotWritableException` | Converter ghi response | Serialize thất bại, mapper không ghi được object |
| Response trống hoặc khác mong đợi | Return-value processing | Method trả view name thay vì body, status `204`, body `null`, converter khác được chọn |
| Chữ Unicode bị méo | Đọc/ghi text | Charset của client/server không khớp |
| Tệp bị lỗi | Binary representation | Biến bytes thành String, nhầm media type hoặc xử lý stream sai |

Một số HTTP status là **kết quả thường gặp**, không bảo đảm tuyệt đối cho mọi cấu hình. Ví dụ: một serialization failure có thể dẫn đến lỗi server, nhưng exception handler/custom response cũng có thể thay đổi status. Để chẩn đoán chính xác, hãy ghi lại **request method, URL, Content-Type, Accept, declared DTO return type, mapping consumes/produces, converter list, exception class và stack trace liên quan**. Không ghi raw payload nhạy cảm hoặc secrets vào log.

Quy trình kiểm tra ngắn:

1. Dùng `curl -i` hoặc công cụ tương đương để nhìn **request/response headers và status**.
2. Kiểm tra routing và `consumes`/`produces` trước.
3. Kiểm tra converter cho kiểu tham số/return type; nhớ thứ tự.
4. Kiểm tra JSON/form/charset và validation sau khi đã xác nhận được converter.
5. Xem exception gốc nếu có, trước khi bổ sung custom advice hoặc converter.
6. Khi đổi cấu hình, thử lại từng media type hiện có để phát hiện ảnh hưởng ngoài ý muốn.

### Một số sai lầm điển hình

**Sai: nhầm String với JSON DTO.**

```java
@GetMapping(produces = "application/json")
@ResponseBody
public String value() {
    return "hello"; // Đừng tự cho rằng converter sẽ tạo JSON string hợp lệ.
}
```

Đây là method minh họa. Nếu muốn JSON đúng định dạng, hãy trả DTO/record như `new MessageResponse("hello")` để mapper serialize, hoặc dùng explicit representation khi đã hiểu converter được chọn. JSON string hợp lệ cần dấu nháy và escaping theo tiêu chuẩn JSON; text thông thường không tự là JSON.

**Sai: đọc request stream hai lần.**

```java
public void handle(@RequestBody byte[] bytes,
                   HttpServletRequest request) {
    // Không kỳ vọng request.getInputStream() còn nguyên body ban đầu.
}
```

Đây chỉ là chữ ký minh họa. Request body thông thường là stream tiêu thụ một lần. Một filter đọc stream trước cũng có thể làm controller thấy body rỗng. `ContentCachingRequestWrapper` chỉ cache dữ liệu **đã đọc qua wrapper**, không tự cho phép đọc lại body tùy ý. Nếu cần bảo toàn raw body để kiểm tra chữ ký, thiết kế filter/wrapper và giới hạn dung lượng cẩn thận.

**Sai: tự thêm converter wildcard.** Nếu một converter tùy biến hỗ trợ `Object.class` và `*/*`, nó có thể giành xử lý từ Jackson hoặc String converters, khiến những endpoint không liên quan thay đổi output. Hãy hạn chế class và media type theo đúng format mới.

**Sai: coi message conversion là authentication hoặc schema security.** Deserialize JSON thành entity có property nhạy cảm có thể dẫn đến over-posting; luôn dùng input DTO riêng, validation thích hợp và chỉ map các field được phép vào domain model. Không tự tin rằng converter đã bảo vệ dữ liệu người dùng.

## 12. Quy tắc chọn giải pháp

| Trường hợp | Cách giải quyết thường phù hợp |
| --- | --- |
| Client gửi JSON và cần DTO Java | `@RequestBody` + JSON converter + validation |
| Client gửi một chuỗi nguyên văn | `@RequestBody String` và media type text |
| Client gửi raw binary kích thước vừa phải | `@RequestBody byte[]`; chú ý giới hạn dung lượng |
| Client gửi HTML form | `@RequestParam` / `@ModelAttribute` |
| Client gửi multipart file/part | `MultipartFile` / `@RequestPart` và multipart resolver |
| Controller trả dữ liệu JSON | Trả DTO/record qua `@RestController` hoặc `ResponseEntity<T>` |
| Trả body với status/headers cụ thể | `ResponseEntity<T>` |
| Tải file | `ResponseEntity<Resource>` hoặc `byte[]` với media type và headers đúng |
| Thêm format wire protocol riêng | Tạo `HttpMessageConverter<T>` hẹp, đăng ký đúng thứ tự |
| Chỉ đổi serialization JSON | Tùy chỉnh Jackson thay vì viết converter từ đầu |

Lưu ý rằng converter và type conversion của `WebDataBinder` là **hai mảng khác nhau**. Ví dụ form field `age=20` thành `int` qua binding infrastructure khác với chuyển JSON `{"age":20}` thành record qua Jackson. Cả hai có thể dùng Bean Validation nhưng khác ở đường đưa dữ liệu vào object.

## 13. Câu hỏi tự kiểm tra và thử nghiệm

1. Client gửi `Content-Type: text/plain` vào endpoint `consumes="application/json"`. Spring kiểm tra điều gì trước khi Jackson được gọi?
2. Nếu `@RestController` trả `String`, tại sao không nên mặc nhiên kết luận rằng response body là JSON?
3. Hai hướng `canRead` và `canWrite` khác nhau ra sao? Một converter có thể chỉ hỗ trợ một chiều không?
4. Khi client đặt `Accept: application/xml`, server chỉ có JSON writer và endpoint `produces="application/json"`, vì sao thường nhận `406`?
5. Vì sao `@Valid` không thể thay thế deserialize? Vì sao deserialize thành công chưa đảm bảo dữ liệu hợp lệ?
6. Vì sao `ByteArrayHttpMessageConverter` có thể thích hợp hơn `StringHttpMessageConverter` cho nội dung PDF/ZIP?
7. Trong Spring MVC 6.1, khi nào nên chọn `extendMessageConverters` thay vì `configureMessageConverters`?
8. Vì sao `HttpMessageConverter` mới không nên khai báo `supports(Object.class)` và `*/*` một cách quá rộng?
9. Nếu trả `ResponseEntity<Void>` với status `204`, Spring có cần gọi writer để serialize JSON DTO không?
10. Điều gì xảy ra nếu đặt một converter mới lên đầu danh sách và nó báo đọc được tất cả kiểu Java, bất kể media type?

**Gợi ý:** (1) Mapping `consumes`/media type request. (2) String converter thường ghi text. (3) Một hướng là parse bytes vào Java, hướng kia serialize Java ra bytes; năng lực phụ thuộc converter. (4) Không có representation phù hợp client. (5) Converter tạo object, validator kiểm tra ràng buộc. (6) Phải giữ nguyên byte. (7) Để giữ danh sách converter mặc định rồi bổ sung. (8) Tránh giành xử lý không chủ ý. (9) Không có body để serialize. (10) Có thể chọn nhầm converter và phá response/request hiện tại.

## 14. Tài liệu chính thức

- [Spring Framework 6.2 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html)
- [Spring Framework 6.2 — Configuring Message Converters](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/message-converters.html)
- [Spring Framework — @RequestBody](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html)
- [Spring Framework — @ResponseBody](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responsebody.html)
- [Spring Framework — ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html)
- [Spring Framework 6.1 — HttpMessageConverter API](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/http/converter/HttpMessageConverter.html)
- [Spring Framework 6.1 — MappingJackson2HttpMessageConverter API](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/http/converter/json/MappingJackson2HttpMessageConverter.html)
- [Spring Framework 6.1 — WebMvcConfigurer API](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/servlet/config/annotation/WebMvcConfigurer.html)

Tài liệu tham khảo có cả bản 6.x và trang hiện hành để tra cứu ý tưởng, nhưng các ví dụ code đã chọn API/kiểu cấu hình tương thích với **Spring Framework 6.1 / Spring Boot 3.3.5**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-message-bodies-002">Chọn bộ chuyển đổi cho chuỗi, byte&#91;&#93;, Resource, JSON và XML</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn bộ chuyển đổi cho chuỗi, byte[], Resource, JSON và XML trong Spring MVC

> **Phạm vi:** Spring Web MVC (Servlet stack) chạy với Java 17+ và ví dụ Spring Boot 3.x / Spring Framework 6.x. Bài học tập trung vào cách Spring đọc và ghi **HTTP message body** qua `HttpMessageConverter` khi controller nhận `@RequestBody`, trả `@ResponseBody` hoặc `ResponseEntity<?>`. Năm nhóm giá trị cần phân biệt là **`String`, `byte[]`, `Resource`, object JSON và object XML**. Tên converter Jackson trong Spring Framework 7 có thay đổi; phần code demo dùng API tương thích nhánh Spring 6.x.

## 1. HTTP body và converter: vấn đề cần giải quyết

Một handler có thể trả về rất nhiều loại Java:

```java
@GetMapping("/hello")
public String hello() {
    return "Xin chào";
}

@GetMapping("/profile")
public ProfileDto profile() {
    return new ProfileDto(7, "Lan");
}
```

Cả hai đều là giá trị Java nhưng wire format khác nhau:

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

Xin chào
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":7,"name":"Lan"}
```

Spring MVC không tự đoán rằng mọi `String` đều là JSON hay mọi object đều là XML. Khi giá trị đi qua xử lý body, framework chọn một `HttpMessageConverter` có thể xử lý **kiểu dữ liệu Java** và **media type** hiện hành. Converter phụ trách biến body từ byte HTTP thành object Java (đọc), hoặc object Java thành byte HTTP (ghi).

Ghi nhớ vai trò của từng thành phần:

| Thành phần | Nhiệm vụ |
| --- | --- |
| `@RequestBody` | Yêu cầu Spring đọc **request body** thành đối số Java |
| `@ResponseBody` / `@RestController` | Yêu cầu Spring ghi giá trị trả về thành **response body** |
| `ResponseEntity<T>` | Cung cấp status, headers và body; body vẫn thường đi qua converter |
| `HttpMessageConverter<T>` | Đọc/ghi body với kiểu Java và media type nó hỗ trợ |
| Request `Content-Type` | Client thông báo **định dạng body đang gửi lên** |
| Request `Accept` | Client thông báo **định dạng phản hồi có thể nhận** |
| Response `Content-Type` | Server công bố **định dạng body thực tế trả về** |
| Mapping `consumes` / `produces` | Giới hạn media type của route cho request/response |

**Hai chiều phải phân biệt:** với `@RequestBody`, Spring xét kiểu tham số Java và request `Content-Type`; với `@ResponseBody`, Spring xét kiểu giá trị trả về, những converter có thể ghi, `Accept` và các ràng buộc `produces`, `Content-Type` đã đặt.

## 2. Bộ converter thường gặp cho năm nhóm dữ liệu

| Giá trị Java | Converter Spring MVC phổ biến | Media type cần chú ý | Ví dụ dùng |
| --- | --- | --- | --- |
| `String` | `StringHttpMessageConverter` | Chủ yếu body văn bản, thường trả `text/plain` nếu không chọn khác | Text, CSV tự xây, nội dung chữ |
| `byte[]` | `ByteArrayHttpMessageConverter` | Hỗ trợ rộng `*/*`; mặc định ghi `application/octet-stream` | Binary payload nhỏ, dữ liệu đã ở bộ nhớ |
| `Resource` | `ResourceHttpMessageConverter` | Dữ liệu resource/file; thường `application/octet-stream` hoặc media type khai báo | Trả file, tải xuống, byte stream từ nguồn tài nguyên |
| POJO/record JSON | `MappingJackson2HttpMessageConverter` | `application/json`, thường có `application/*+json` | REST JSON API |
| POJO/record XML | `MappingJackson2XmlHttpMessageConverter` | `application/xml`, `text/xml` và XML suffix khi được hỗ trợ | Tích hợp XML / content negotiation |

Tên Jackson 2 converter trong bảng đúng với **Spring Framework 6.x**. Từ Spring Framework 7.x, hệ API converter được cập nhật cho Jackson thế hệ mới; cần đối chiếu tài liệu đúng version trước khi khai báo hoặc tùy biến class converter. Các API controller chung như `@RequestBody`, `ResponseEntity`, `produces` và `consumes` vẫn là cơ chế quan trọng.

Một nhóm khác trong Spring là `FormHttpMessageConverter` cho `application/x-www-form-urlencoded` / multipart ở phạm vi tương ứng, nhưng không phải đối tượng chính của bài này. Không nên dùng `StringHttpMessageConverter` để tự viết JSON/XML khi một converter chuyên dụng và DTO rõ kiểu đáp ứng yêu cầu tốt hơn.

### 2.1. Converter có phải lúc nào cũng sẵn?

Không. Một số converter đi kèm Spring Web; converter JSON/XML phụ thuộc thư viện serialization tương ứng trên classpath và cấu hình MVC. Spring Boot thường tự cấu hình một tập converter phù hợp với dependency. Ví dụ:

- Thêm **Spring Web** trên dự án Spring Boot 3.x thông thường mang theo Jackson JSON.
- Để dùng Jackson XML trong ví dụ, cần thêm `com.fasterxml.jackson.dataformat:jackson-dataformat-xml` đúng version do Spring Boot quản lý.
- Nếu không có XML converter phù hợp, dù client gửi `Accept: application/xml`, Spring **không thể tự động tạo XML từ mọi Java object** chỉ bằng đổi header.
- Một hệ thống có thể chủ động thay đổi/thứ tự converter, media types được hỗ trợ hoặc `ObjectMapper`. Không xem bảng là lời cam kết cố định của mọi ứng dụng.

## 3. Quy trình chọn converter ở tầng controller

Sơ đồ khái niệm:

```text
                      HTTP request
                           |
       +-------------------+---------------------+
       |                                         |
@RequestBody T                              Handler execution
       |                                         |
Content-Type + T                           Return value (T)
       |                                         |
canRead(T, media type)?                    @ResponseBody / ResponseEntity
       |                                         |
read(...) -> T                            Accept + produces + Content-Type
       |                                         |
Controller sử dụng object                   canWrite(T, media type)?
                                                 |
                                             write(...) -> HTTP body
```

Khi Spring xử lý request, có hai giai đoạn khác nhau:

1. **Chọn handler**: `@RequestMapping` và các biến thể `@GetMapping`, `@PostMapping` có thể giới hạn route bằng `consumes` và `produces`; không thỏa các điều kiện này có thể loại mapping trước khi converter chạy.
2. **Đọc/ghi body**: một converter được đăng ký phải hỗ trợ kiểu Java và media type hợp lệ. Với response, Spring còn thực hiện content negotiation, thường dựa trên `Accept` và kiểu body.

Thứ tự converter **có thể ảnh hưởng** khi nhiều converter đều tuyên bố xử lý được cùng một Java type/media type. Spring kiểm tra những converter phù hợp trong danh sách đã đăng ký. Vì vậy custom converter khai báo quá rộng như `*/*` hoặc chèn lên đầu danh sách có thể làm thay đổi output khác dự kiến.

### 3.1. Phân biệt ba vị trí đặt media type

```java
@PostMapping(
    path = "/documents",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE)
public DocumentDto create(@RequestBody DocumentDto input) {
    return input;
}
```

- `consumes = application/json` có nghĩa method mong request body là JSON. Client cần gửi `Content-Type: application/json`.
- `produces = application/json` có nghĩa method tạo response JSON, client nên chấp nhận `application/json` trong header `Accept`.
- `ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(...)` đặt response `Content-Type` cụ thể. Nó **không bắt Spring biến một `String` tùy ý thành JSON hợp lệ**.

Khi client gửi body XML tới route chỉ `consumes = application/json`, kết quả thông thường là **415 Unsupported Media Type**. Khi client chỉ chấp nhận XML nhưng route chỉ `produces = application/json`, kết quả thường là **406 Not Acceptable**. Ngoài ra, nếu định dạng được chấp nhận về mặt header nhưng body sai cú pháp/không giải mã được, thường nhận **400 Bad Request** do lỗi đọc request.

## 4. `StringHttpMessageConverter` — trả hoặc nhận văn bản

Ví dụ text response:

```java
@GetMapping(path = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
public String hello() {
    return "Xin chào Spring MVC";
}
```

Vì method thuộc `@RestController` (hoặc có `@ResponseBody`), Spring ghi chuỗi vào body, không coi chuỗi là tên view. Kiểu dữ liệu trả về là `String` nên converter dành cho String thường được chọn và tạo body text.

Các điều cần nhớ:

- `StringHttpMessageConverter` đọc/ghi chuỗi, không đóng vai trò mapping JSON object sang DTO.
- `Content-Type` được xác định bởi mapping, headers và negotiation; nếu muốn nội dung văn bản cụ thể, dùng `produces = "text/plain"` hoặc đặt rõ content type với `ResponseEntity`.
- Với Unicode, cần mã hóa đúng. Ở Spring Boot hiện đại, UTF-8 thường được dùng cho text; có thể khai báo rõ `text/plain;charset=UTF-8` ở output contract khi cần.
- `String` chứa `{"ok":true}` vẫn chỉ là các ký tự. Việc đặt `produces = "application/json"` **không tự kiểm tra** chuỗi ấy là JSON hợp lệ. Có thể vô tình trả JSON sai cú pháp.
- Nếu muốn JSON string scalar (ví dụ response JSON hợp lệ là `"hello"` với dấu nháy ở body), cần hiểu sự khác biệt giữa String converter và Jackson. Tốt nhất trả DTO/object khi xây API JSON để tránh hành vi lựa chọn converter tùy cấu hình.

Ví dụ nhận text:

```java
@PostMapping(
        path = "/echo-text",
        consumes = MediaType.TEXT_PLAIN_VALUE,
        produces = MediaType.TEXT_PLAIN_VALUE)
public String echoText(@RequestBody String body) {
    return "text: " + body;
}
```

Với `Content-Type: text/plain` và body `Java`, server trả body `text: Java`. `@RequestBody` đọc **body** chứ không phải query string. Nếu chỉ có `?body=Java` mà body trống, method có `@RequestBody` bắt buộc có thể nhận lỗi vì thiếu body.

## 5. `ByteArrayHttpMessageConverter` — `byte[]` nguyên bản

Một mảng byte là dữ liệu byte **đã ở bộ nhớ**:

```java
@GetMapping(path = "/bytes", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseEntity<byte[]> bytes() {
    byte[] data = new byte[] {0x41, 0x42, 0x43};
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(data);
}
```

Body HTTP gồm chính xác ba byte hexa `41 42 43`, tương ứng chữ ASCII `ABC`. Không có thêm dấu nháy JSON, Base64 hoặc các ký tự JSON array.

Dùng `byte[]` khi:

- Body nhỏ hoặc vừa và đã cần toàn bộ byte trong RAM.
- Đọc/ghi binary protocol, dữ liệu nén hoặc file rất nhỏ.
- Cần kiểm soát **chính xác các byte** sau khi qua HTTP converter, không muốn serialization sang JSON.

Không dùng `byte[]` để đọc toàn bộ file rất lớn chỉ nhằm gửi cho client; nó buộc ứng dụng giữ nội dung trong bộ nhớ. Khi nguồn nội dung là file hoặc một tài nguyên có thể mở stream, xem `Resource` ở mục 6.

Nếu client gửi binary lên:

```java
@PostMapping(
        path = "/byte-count",
        consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
        produces = MediaType.TEXT_PLAIN_VALUE)
public String byteCount(@RequestBody byte[] body) {
    return "bytes=" + body.length;
}
```

Nếu client gửi ba byte `ABC` với `Content-Type: application/octet-stream`, body phản hồi là `bytes=3`. Đếm ở đây là **số byte**, không phải số ký tự Unicode.

Media type mặc định của `ByteArrayHttpMessageConverter` khi ghi thường là `application/octet-stream` dù converter hỗ trợ nhiều media type. Trong REST, đặt rõ `Content-Type` theo ý nghĩa thực của dữ liệu (ví dụ ảnh PNG có thể là `image/png`), không giả định client có thể đoán định dạng từ file extension.

## 6. `ResourceHttpMessageConverter` — trả về tài nguyên/file

`org.springframework.core.io.Resource` là abstraction cho dữ liệu có thể truy cập qua nguồn tài nguyên, chẳng hạn `ClassPathResource`, `FileSystemResource`, `ByteArrayResource` hoặc một nguồn stream thích hợp. Spring MVC có thể ghi nội dung `Resource` ra response thông qua `ResourceHttpMessageConverter`.

Ví dụ download một file nhúng:

```java
@GetMapping(path = "/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseEntity<Resource> download() {
    Resource body = new ByteArrayResource(
            "FILE\n".getBytes(StandardCharsets.UTF_8));

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"example.txt\"")
            .body(body);
}
```

Khi client gọi `GET /download`, server gửi header `Content-Disposition: attachment; filename="example.txt"` và **5 byte** của `FILE` cộng một newline. `ByteArrayResource` ở đây chỉ nhằm minh họa bằng dữ liệu tự tạo; với file có sẵn, có thể dùng `FileSystemResource` hoặc `PathResource` theo chính sách cho phép truy cập.

### 6.1. `byte[]` và `Resource` khác nhau thế nào?

| Câu hỏi | `byte[]` | `Resource` |
| --- | --- | --- |
| Cách biểu diễn | Nội dung nằm trong một mảng byte | Handle/abstraction tới nội dung |
| Bộ nhớ | Thường giữ toàn bộ body trong RAM | Có thể lấy dữ liệu từ file/stream khi ghi |
| File lớn | Không lý tưởng | Thường thích hợp hơn nếu loại Resource và hạ tầng phù hợp |
| Converter chính | `ByteArrayHttpMessageConverter` | `ResourceHttpMessageConverter` |
| Metadata file | Tự đặt header | Cũng cần chủ động đặt `Content-Type`, `Content-Disposition` khi cần |
| Streaming | Trả ngay byte đã có | Có thể mở stream lúc ghi, tùy `Resource` |

**Quan trọng với streaming:** `Resource` không bảo đảm mọi triển khai đều tái mở stream được. `InputStreamResource` có thể chỉ đọc một lần, và tính Content-Length có thể tiêu thụ stream nếu dùng subclass/bọc không đúng. Với `InputStreamResource` cần hiểu quy ước lazy stream supplier và giới hạn hỗ trợ; tránh vừa đọc thử stream để đo độ dài rồi lại yêu cầu converter đọc tiếp. Với file thường, chọn Resource thích hợp cho phép hệ thống xử lý kích thước/streaming thuận tiện hơn.

### 6.2. Download, cache và HTTP Range

Các response trả `Resource` có thể liên quan đến partial-content/Range support của Spring MVC trong những điều kiện nhất định. Không đồng nhất `Resource` với việc **mọi endpoint đều tự hỗ trợ resumable download**. Chức năng phụ thuộc cách controller trả Resource, loại Resource, headers, cấu hình và hạ tầng HTTP.

Nếu cần download production:

1. Xác định quyền người dùng đối với file trước khi tạo `Resource`.
2. Không nối trực tiếp path từ query hoặc URL để đọc file nếu chưa chuẩn hóa và kiểm tra path traversal.
3. Đặt `Content-Type` chính xác; nếu cần download thay vì hiển thị trực tiếp, đặt `Content-Disposition: attachment` với filename đã xử lý an toàn.
4. Kiểm tra Content-Length, memory profile, Range, timeout, file không tồn tại và lỗi I/O bằng integration test với file đủ lớn.

## 7. JSON — `MappingJackson2HttpMessageConverter`

JSON converter biến body như sau thành object Java:

```http
POST /api/messages/json
Content-Type: application/json
Accept: application/json

{"text":"Chao ban"}
```

Controller:

```java
@PostMapping(
        path = "/messages/json",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
public MessageDto echoJson(@RequestBody MessageDto input) {
    return input;
}
```

Với kiểu `record MessageDto(String text) { }`, Jackson deserializer đọc JSON thành Java object; Jackson serializer ghi object đó thành JSON. Cần Jackson JSON (thường có sẵn khi thêm Spring Boot starter web phù hợp với Boot 3.x).

### 7.1. Tại sao nên trả DTO thay vì chuỗi JSON tự nối?

Không nên:

```java
// Anti-pattern: ghép JSON thủ công.
return "{\"text\":\"" + userInput + "\"}";
```

Đoạn nối chuỗi trên không xử lý đầy đủ dấu nháy, xuống dòng, escape và các trường hợp đặc biệt. Với JSON API hãy dùng DTO/record và để Jackson serialize:

```java
public record MessageDto(String text) {
}
```

Đây là **định nghĩa type minh họa**; khi đặt trong cùng file chứa `public class DemoApplication`, bỏ `public` của record hoặc tách thành file `MessageDto.java`. Việc trả `MessageDto` đảm bảo serializer phù hợp với kiểu object và các quy tắc escape cần thiết.

### 7.2. JSON không phải là một media type cho mọi giá trị

Nếu response thực sự là file/binary nhưng chỉ đặt `application/json`, client có thể hiểu sai. Nếu endpoint có thể trả hai biểu diễn cho **cùng một object**, cần cài các converter và sử dụng content negotiation rõ ràng. Trong API production, schema DTO phải phù hợp và không trực tiếp serialize entity chứa thông tin nhạy cảm.

Với JSON request, `@Valid @RequestBody MessageDto input` có thể chạy Bean Validation nếu được cấu hình và có dependency phù hợp. Conversion chỉ là **bước đọc**, không phải xác thực dữ liệu hay phân quyền.

## 8. XML — `MappingJackson2XmlHttpMessageConverter`

XML converter là một lựa chọn riêng với JSON. Với Spring Framework 6.x, Jackson XML converter dùng `XmlMapper` từ thư viện:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-xml</artifactId>
</dependency>
```

Ví dụ trên là dependency Maven đặt trong `<dependencies>`, để Spring Boot parent/BOM quản lý version. Không thêm version Jackson khác biệt ngẫu nhiên với bộ dependency đang dùng.

Một record được khai báo rõ tên root XML:

```java
@JacksonXmlRootElement(localName = "message")
record MessageDto(String text) {
}
```

Import `com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement`. Với XML converter được đăng ký, handler:

```java
@PostMapping(
        path = "/messages/xml",
        consumes = MediaType.APPLICATION_XML_VALUE,
        produces = MediaType.APPLICATION_XML_VALUE)
public MessageDto echoXml(@RequestBody MessageDto input) {
    return input;
}
```

Một request phù hợp:

```http
POST /api/messages/xml
Content-Type: application/xml
Accept: application/xml

<message><text>Chao ban</text></message>
```

Response XML có phần tử `message` và con `text` chứa `Chao ban`. Định dạng cụ thể (XML declaration, whitespace, namespace) tùy cấu hình serializer; không nên kiểm thử bằng so sánh nguyên văn mọi byte nếu hợp đồng chỉ yêu cầu cấu trúc/giá trị XML.

Spring MVC còn có những converter XML khác, ví dụ `Jaxb2RootElementHttpMessageConverter` khi JAXB phù hợp hiện diện, `MarshallingHttpMessageConverter` dựa vào Spring OXM, và `SourceHttpMessageConverter` cho kiểu `Source`. **Đừng đồng nhất** "hỗ trợ XML" với "chắc chắn đang dùng Jackson XML"; hãy kiểm tra classpath và converter list. Java 17 không còn tích hợp JAXB vào JDK như các phiên bản Java cũ; dùng JAXB phải thêm API/provider tương thích.

### 8.1. Có cần hai endpoint để trả JSON và XML?

Không nhất thiết. Nếu cùng kiểu DTO có converter cho cả hai media type và mapping cho phép:

```java
@GetMapping(
        path = "/messages/format",
        produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE
        })
public MessageDto format() {
    return new MessageDto("Xin chào");
}
```

- `Accept: application/json` → JSON converter.
- `Accept: application/xml` → XML converter, nếu thư viện và cấu hình hiện có hỗ trợ `MessageDto`.
- `Accept: text/plain` → thường **406 Not Acceptable** vì route không hứa biểu diễn text.
- `Accept: */*` → cả hai đều có thể chấp nhận; đừng viết test mặc định rằng XML luôn được chọn. Quy tắc negotiation và thứ tự có thể ảnh hưởng.

Đối với API tích hợp bên ngoài, công bố rõ media types được hỗ trợ và thử từng đường đọc/ghi bằng integration test.

## 9. Ví dụ đầy đủ chạy được: năm kiểu body trong cùng một ứng dụng

Ví dụ này triển khai Spring Boot 3.x và Spring Framework 6.x, Java 17+, gồm:

- Response text `String`.
- Response và request `byte[]`.
- Response file `Resource`.
- JSON request/response object.
- XML request/response object (phụ thuộc Jackson XML).
- Một endpoint JSON/XML dùng header `Accept` để chọn biểu diễn.

### 9.1. Maven dependencies

Tạo dự án Spring Boot 3.x trên [Spring Initializr](https://start.spring.io/) với **Java 17+** và **Spring Web**. Nếu project đã có dependency Spring Web do Initializr tạo, giữ nguyên. Trong `pom.xml`, thêm dependency XML bên cạnh Spring Web:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.dataformat</groupId>
        <artifactId>jackson-dataformat-xml</artifactId>
    </dependency>
</dependencies>
```

Đây là **đoạn `<dependencies>`**, không phải file POM độc lập; Spring Initializr cung cấp `project`, `parent`/BOM, plugin Maven và metadata. Nếu đang dùng dependency management của Spring Boot, thường không cần khai báo phiên bản Jackson XML.

### 9.2. Toàn bộ source Java

Thay nội dung file `src/main/java/com/example/demo/DemoApplication.java` bằng đoạn hoàn chỉnh dưới đây. Chỉ có một `public class`; record `MessageDto` là top-level package-private hợp lệ trong cùng file:

```java
package com.example.demo;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@JacksonXmlRootElement(localName = "message")
record MessageDto(String text) {
}

@RestController
@RequestMapping("/api")
class ConverterController {

    @GetMapping(path = "/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> text() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "text/plain;charset=UTF-8"))
                .body("Xin chào Spring MVC");
    }

    @PostMapping(
            path = "/echo-text",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String echoText(@RequestBody String body) {
        return "text: " + body;
    }

    @GetMapping(
            path = "/bytes",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> bytes() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new byte[] {65, 66, 67});
    }

    @PostMapping(
            path = "/byte-count",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String byteCount(@RequestBody byte[] data) {
        return "bytes=" + data.length;
    }

    @GetMapping(
            path = "/download",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> download() {
        Resource file = new ByteArrayResource(
                "FILE\n".getBytes(StandardCharsets.UTF_8));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"example.txt\"")
                .body(file);
    }

    @PostMapping(
            path = "/messages/json",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageDto json(@RequestBody MessageDto input) {
        return input;
    }

    @PostMapping(
            path = "/messages/xml",
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public MessageDto xml(@RequestBody MessageDto input) {
        return input;
    }

    @GetMapping(
            path = "/messages/format",
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.APPLICATION_XML_VALUE
            })
    public MessageDto format() {
        return new MessageDto("Xin chào");
    }
}
```

**Vì sao Spring dùng đúng converter?**

- `text()` trả `ResponseEntity<String>`, nên body là String, được ghi qua converter dành cho chuỗi.
- `bytes()` trả `ResponseEntity<byte[]>`, nên body là byte array (không phải JSON list).
- `download()` trả `ResponseEntity<Resource>`, nên converter cho Resource đọc nội dung resource.
- `json()` nhận và trả `MessageDto` với `application/json`, nên converter JSON đọc/ghi.
- `xml()` nhận và trả `MessageDto` với `application/xml`, nên converter XML đọc/ghi.
- `format()` cũng trả `MessageDto`, nhưng route cho phép cả JSON lẫn XML; giá trị `Accept` là yếu tố quyết định đầu ra.

### 9.3. Chạy ứng dụng

Từ thư mục gốc dự án:

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows PowerShell hoặc Command Prompt
mvnw.cmd spring-boot:run
```

### 9.4. Kiểm chứng request và response

**Text:**

```bash
curl -s -H 'Accept: text/plain' \
  http://localhost:8080/api/text
```

Body UTF-8:

```text
Xin chào Spring MVC
```

**Text request:**

```bash
curl -s -X POST \
  -H 'Content-Type: text/plain' \
  -H 'Accept: text/plain' \
  --data-binary 'Java' \
  http://localhost:8080/api/echo-text
```

Body:

```text
text: Java
```

**Byte response:**

```bash
curl -s -o sample.bin -H 'Accept: application/octet-stream' \
  http://localhost:8080/api/bytes
xxd -p sample.bin
```

Nếu máy có `xxd`, dòng in ra là:

```text
414243
```

Đây là **mã hex của ba byte**; cURL tự lưu body nhị phân vào `sample.bin`, không có ký tự newline thừa. Không phải output JSON `[65,66,67]`.

**Byte request:**

```bash
curl -s -X POST \
  -H 'Content-Type: application/octet-stream' \
  --data-binary 'ABC' \
  http://localhost:8080/api/byte-count
```

Body:

```text
bytes=3
```

**Resource download:**

```bash
curl -s -OJ http://localhost:8080/api/download
```

Với client cURL hỗ trợ `-OJ`/Content-Disposition, file được lưu tên `example.txt` (trừ khi môi trường hoặc tên file xung đột dẫn đến cách xử lý khác). Nội dung là `FILE` và newline cuối, tổng **5 byte**. Nếu không muốn client tự chọn tên từ header, dùng `curl -s -o local.txt URL` để lưu vào file mong muốn.

**JSON input và output:**

```bash
curl -s -X POST http://localhost:8080/api/messages/json \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  --data-binary '{"text":"Chao ban"}'
```

Body JSON về mặt dữ liệu:

```json
{"text":"Chao ban"}
```

**XML input và output:**

```bash
curl -s -X POST http://localhost:8080/api/messages/xml \
  -H 'Content-Type: application/xml' \
  -H 'Accept: application/xml' \
  --data-binary '<message><text>Chao ban</text></message>'
```

Body XML có giá trị tương đương:

```xml
<message><text>Chao ban</text></message>
```

Jackson XML có thể phát ra XML declaration hoặc định dạng khác tương đương; ví dụ trên trình bày **cấu trúc và giá trị**, không cam kết byte-for-byte trừ khi serializer được cấu hình cố định.

**Negotiation theo `Accept`:**

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/api/messages/format

curl -i -H 'Accept: application/xml' \
  http://localhost:8080/api/messages/format

curl -i -H 'Accept: text/plain' \
  http://localhost:8080/api/messages/format
```

Hai request đầu nhận `200 OK` với `Content-Type` tương ứng JSON/XML, còn request thứ ba thường nhận **406 Not Acceptable** do mapping chỉ cho phép JSON/XML. Nếu muốn so sánh kết quả trên Windows PowerShell, dùng `curl.exe` và viết mỗi lệnh trên một dòng để tránh cú pháp nối dòng của shell Bash.

## 10. Khi converter được chọn sai hoặc không tìm thấy

### 10.1. `406 Not Acceptable`: client không chấp nhận kiểu server có thể tạo

Ví dụ:

```http
GET /api/messages/format
Accept: text/plain
```

Trong demo, route chỉ có `produces = application/json, application/xml`. Kết quả thường 406. Cách xử lý là client yêu cầu một biểu diễn thực sự được hỗ trợ, hoặc server chủ động triển khai thêm biểu diễn (kèm converter/mapping tương ứng). Không nên cố ép `Content-Type` sai để né lỗi.

### 10.2. `415 Unsupported Media Type`: request body không được hỗ trợ

Ví dụ:

```http
POST /api/messages/json
Content-Type: application/xml

<message><text>Hi</text></message>
```

Mapping chỉ `consumes = application/json`; request có thể bị từ chối 415 **trước khi đọc body**. Cũng có thể phát sinh 415 khi không có converter đọc được body vào kiểu tham số cần thiết với media type đã khai báo.

### 10.3. `400 Bad Request`: payload sai nội dung

```http
POST /api/messages/json
Content-Type: application/json

{"text":
```

Header đúng JSON nhưng body sai cú pháp. Jackson không parse được và Spring MVC thường trả 400. Lỗi parse, lỗi kiểu field, lỗi validation và lỗi nội dung nghiệp vụ là những nhóm khác nhau; nên có xử lý lỗi nhất quán thay vì đổi media type để lấp lỗi.

### 10.4. `500 Internal Server Error` hoặc lỗi ghi response

Ngay cả khi route và `Accept` tương thích, server vẫn có thể lỗi khi serialize object, ví dụ vòng tham chiếu, getter ném exception, serializer thiếu cấu hình hoặc đối tượng không thể biểu diễn bằng converter hiện có. Để chẩn đoán, xem class converter thực dùng, stacktrace gốc và kiểu Java trả về. Đừng mặc định mọi lỗi response là 406; 406 chủ yếu nói về **không thể thỏa định dạng client chấp nhận**.

### 10.5. Tại sao `return "index";` đôi khi trả view, đôi khi trả text?

```java
@Controller
class PageController {
    @GetMapping("/page")
    public String page() {
        return "index";
    }
}
```

Trong `@Controller`, `String` có thể là **tên view**. Khi method có `@ResponseBody` hoặc class dùng `@RestController`, String trở thành **HTTP body** và converter có cơ hội xử lý nó. Đây là khác biệt ở tầng return-value handler, không phải lỗi của String converter.

## 11. Cấu hình converter trong Spring MVC và Spring Boot

### 11.1. Thông thường, không cần đăng ký thủ công

Spring Web MVC đăng ký một bộ converter mặc định tùy thuộc các thư viện hiện diện. Spring Boot còn tự động thêm/chỉnh một số converter theo dependency và cấu hình riêng. Với ứng dụng dùng `spring-boot-starter-web` và Jackson, `@RestController` trả DTO thường đã có JSON. Thêm Jackson XML tương thích thường cho phép XML nếu không có cấu hình khác chặn.

Kiểm tra dependency quan trọng hơn viết bean converter mới chỉ để sửa `406`. Khi cả JSON/XML cùng hỗ trợ và client không gửi `Accept` cụ thể, việc ứng dụng chọn định dạng nào còn tùy thứ tự converter và chính sách negotiation.

### 11.2. `extendMessageConverters` so với `configureMessageConverters`

Trong Spring Framework **6.x**:

```java
@Configuration
class ConverterConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(
            List<HttpMessageConverter<?>> converters) {
        // Quan sát hoặc tùy chỉnh danh sách đã được MVC cấu hình.
        // Không tự xóa converter mặc định khi chưa có lý do.
    }
}
```

Đoạn trên là **config minh họa**, cần import `org.springframework.context.annotation.Configuration`, `org.springframework.web.servlet.config.annotation.WebMvcConfigurer`, `org.springframework.http.converter.HttpMessageConverter` và `java.util.List`. `extendMessageConverters` cho phép điều chỉnh danh sách sau bước cấu hình mặc định.

Còn `configureMessageConverters(List<...>)` trong Spring MVC 6.x có thể **thay thế cơ chế đăng ký converter mặc định** nếu tự đưa vào các converter; với Spring Boot có thêm các cơ chế tự động đăng ký bean cần xem xét. Đừng thêm mỗi JSON converter rồi làm mất support String, Resource hoặc các converter trước đó. Cũng không tự thêm `@EnableWebMvc` trong Boot nếu không chủ đích nhận trách nhiệm về MVC configuration.

Nếu chỉ cần định dạng cụ thể cho một endpoint, nên khai báo `consumes`/`produces`, dùng `ResponseEntity` và kiểu body chính xác trước khi đụng vào danh sách converter toàn ứng dụng.

### 11.3. Xem thứ tự converter đang chạy

Trong ứng dụng Boot 3.x / Framework 6.x, có thể quan sát danh sách converter được MVC cấu hình:

```java
// Đoạn chẩn đoán riêng; không bắt buộc thêm vào demo.
@Component
class ConverterPrinter implements ApplicationRunner {

    private final RequestMappingHandlerAdapter adapter;

    ConverterPrinter(RequestMappingHandlerAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (HttpMessageConverter<?> converter
                : adapter.getMessageConverters()) {
            System.out.println(converter.getClass().getSimpleName());
        }
    }
}
```

Đoạn này cần import `org.springframework.stereotype.Component`, `org.springframework.boot.ApplicationRunner`, `org.springframework.boot.ApplicationArguments`, `org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter` và `org.springframework.http.converter.HttpMessageConverter`; đây là **mã chẩn đoán**, không phải test cố định về output hay thứ tự danh sách. Tên và số lượng converter phụ thuộc version và classpath.

Nếu các media types được cấu hình quá rộng cho custom converter, nó có thể được chọn trước converter hợp lý hơn. Cần kiểm thử `Accept: */*`, `Accept: application/json` và `Accept: application/xml` sau khi thay đổi thứ tự.

## 12. Cách chọn dựa trên yêu cầu ứng dụng

| Yêu cầu đầu ra | Java return type đề xuất | Media type | Lý do |
| --- | --- | --- | --- |
| Thông báo text đơn giản | `String` | `text/plain;charset=UTF-8` | Rõ dạng văn bản |
| Dữ liệu CSV nhỏ tự dựng | `String` / `byte[]` | `text/csv` | Chọn encoding và escape CSV đúng |
| Binary nhỏ có sẵn trong RAM | `byte[]` | `application/octet-stream` hoặc type cụ thể | Gửi trực tiếp byte |
| File trên đĩa/tài nguyên có thể mở stream | `Resource` | Type thật, với `Content-Disposition` khi tải xuống | Không cần giữ toàn bộ nội dung file trong một mảng |
| REST payload có schema | DTO/record | `application/json` | Jackson mapping và JSON escaping |
| Tích hợp XML theo hợp đồng | DTO/record có XML mapping phù hợp | `application/xml` | Jackson XML/JAXB tương thích |
| Cùng một object hỗ trợ JSON/XML | DTO/record | `produces={JSON,XML}` | Chọn biểu diễn qua `Accept` |

Không dùng `String`/`byte[]` chỉ để né thiết kế DTO. Không dùng `Resource` thay JSON cho object có schema. Converter biến dữ liệu **trên đường truyền**, không thay thế validation, authentication/authorization, mã hóa dữ liệu nhạy cảm, hay kiểm soát kích thước request.

## 13. Các rủi ro và lỗi phổ biến khi đưa vào production

| Sai lầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| Nhầm request `Content-Type` với `Accept` | 415 hoặc 406 | Chỉ rõ chiều đọc/gửi body |
| Trả `String` trông giống JSON | Có thể trả text không hợp lệ hoặc không đúng serializer | Trả DTO qua Jackson |
| Gửi object JSON nhưng ép `produces=text/plain` | Converter không phù hợp hoặc sai contract | Sử dụng `application/json` |
| Tưởng `byte[]` là Base64 JSON | Client parse sai | Phân biệt binary raw với JSON Base64 |
| Đọc cả file lớn vào byte array | Tốn RAM, khó scale | `Resource`/streaming phù hợp |
| Giả định XML luôn có | 406, 500 hoặc thiếu converter | Thêm XML dependency và test classpath |
| Tùy biến converter quá rộng `*/*` | Lấn converter mặc định | Hạn chế supportedMediaTypes và kiểm soát thứ tự |
| Bỏ converter mặc định khi override config | String/Resource/JSON ngừng hoạt động | Ưu tiên cách mở rộng an toàn |
| Trả Resource chứa path do client tự chọn | Lộ file, path traversal | Xác thực quyền và giới hạn đường dẫn |
| Deserialize object JSON/XML vào entity chứa quyền | Mass assignment, lộ field | Request DTO, validate, explicit mapping |
| Tin tuyệt đối filename/media type từ client | File giả định dạng, injection | Validate dữ liệu, sanitize filename |
| XML parser cho phép external entities | XXE, đọc tài nguyên ngoài ý muốn | Dùng thư viện XML đã cập nhật và cấu hình bảo vệ parser |

Đặc biệt với XML, nếu thêm custom parser hoặc converter mới, kiểm tra **XXE / external entity resolution**, giới hạn kích thước và độ sâu tài liệu. Không chủ động bật external entities để xử lý dữ liệu không tin cậy. Với JSON, chú ý độ sâu nesting, kích thước request và những field không được phép deserialze vào domain entity.

### 13.1. Trình tự gỡ lỗi nhanh

1. Xem method có `@ResponseBody`/`@RestController` hoặc trả `ResponseEntity` không.
2. Ghi lại **runtime body type** và loại tham số `@RequestBody`.
3. Kiểm tra request `Content-Type`, `Accept` và mapping `consumes`/`produces`.
4. Xác nhận dependency converter tương ứng có trong runtime classpath.
5. Kiểm tra danh sách converter + thứ tự nếu có custom config.
6. Dùng `curl -i` hoặc integration test để đọc status, response `Content-Type` và body thực.
7. Nếu là 400/500, xem exception gốc của converter; đừng nhầm lỗi dữ liệu với lỗi negotiation.

## 14. Tự kiểm tra

**Câu 1.** Client gửi `POST /messages/json` với `Content-Type: application/xml` nhưng mapping chỉ `consumes=application/json`. Trả gì?

**Đáp án:** Thông thường **415 Unsupported Media Type**, vì định dạng request body không khớp.

**Câu 2.** Một endpoint trả `ResponseEntity<byte[]>` ba byte `65,66,67`, media type `application/octet-stream`. Client nhận gì?

**Đáp án:** Ba byte `41 42 43` theo hệ hex (ASCII `ABC`), không phải JSON array hay Base64.

**Câu 3.** Một endpoint trả `MessageDto` và hỗ trợ JSON, XML. Client gửi `Accept: application/xml` nhưng app chưa có converter XML. Có tự đổi JSON thành XML không?

**Đáp án:** Không. Cần converter XML phù hợp trên classpath và hỗ trợ kiểu Java tương ứng; nếu không, response không thể thỏa contract XML.

**Câu 4.** Vì sao `return "{\"text\":\"hi\"}";` là cách không nên dùng cho JSON API?

**Đáp án:** Đây là String thủ công, có nguy cơ escape không đúng, sai content type và lệ thuộc converter selection; hãy trả DTO/record để Jackson serialize.

**Câu 5.** Tại sao `Resource` thường hữu ích hơn `byte[]` với file dung lượng lớn?

**Đáp án:** Resource có thể đại diện cho nguồn dữ liệu được đọc khi gửi response, thay vì nạp toàn bộ file vào mảng byte trước đó; vẫn phải chọn loại Resource và cấu hình streaming phù hợp.

**Câu 6.** `@GetMapping(produces=APPLICATION_JSON_VALUE)` có bảo đảm bất kỳ `String` trả về nào là JSON hợp lệ?

**Đáp án:** Không. `produces` là contract media type của mapping, không thay thế việc serialize object bằng JSON converter hay validate JSON thô.

## 15. Tài liệu chuẩn

- [Spring Framework 6.2 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html): các converter cơ bản, JSON/XML và dependency cần có.
- [Spring Framework 6.2 — Configuring Message Converters](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/message-converters.html): `configureMessageConverters` và `extendMessageConverters`.
- [Spring Framework — Request Mapping: consumes/produces](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html): mapping dựa trên Content-Type/Accept.
- [Spring Framework — ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html): status, headers và body converter.
- [Spring Framework — @RequestBody](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html): giải mã request body và validation.
- [Spring Framework — @ResponseBody](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responsebody.html): chuyển return value thành HTTP response body.
- [Spring Boot 3.5 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): converter auto-configuration trong Boot 3.x.
- [Spring Framework — HTTP Range Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-range.html): cơ chế partial content cho Resource trong những điều kiện thích hợp.

**Tổng kết ứng dụng:** Bắt đầu từ **kiểu Java của body** và **media type mà HTTP request/response yêu cầu**, sau đó để converter đúng trách nhiệm đọc hoặc ghi. Dùng `String` cho text, `byte[]` cho binary ở bộ nhớ, `Resource` cho nguồn tài nguyên, DTO/record + Jackson cho JSON, và converter/XML mapping tương thích cho XML. Kiểm tra đồng thời `Content-Type`, `Accept`, `consumes`, `produces`, thư viện runtime và thứ tự converter trước khi tùy chỉnh cấu hình MVC.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-message-bodies-003">Cấu hình bộ chuyển đổi: thay thế mặc định hay mở rộng danh sách</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu hình bộ chuyển đổi HTTP: thay thế mặc định hay mở rộng danh sách trong Spring MVC

## 1. Mục tiêu và bối cảnh

Trong Spring MVC, khi controller nhận một request JSON rồi trả về JSON, việc biến **bytes của HTTP body** thành Java object và ngược lại thường được thực hiện bởi `HttpMessageConverter`. Cấu hình sai danh sách converter có thể khiến endpoint trước đó chạy được bất ngờ trả **415 Unsupported Media Type** hoặc **406 Not Acceptable**. Câu hỏi trọng tâm của bài là: **khi nào nên thay toàn bộ converter mặc định và khi nào chỉ cần mở rộng danh sách đang có?**

Sau bài này, bạn sẽ:
- Hiểu nơi Spring MVC dùng `HttpMessageConverter`, cách chọn converter khi đọc và ghi body, vai trò của `Content-Type`, `Accept` và thứ tự trong danh sách.
- Phân biệt `WebMvcConfigurer#configureMessageConverters` với `extendMessageConverters` ở **Spring Framework 6.x**; hiểu khác biệt giữa Spring MVC thông thường và **Spring Boot 3.x**.
- Triển khai một converter tự tạo, thêm vào danh sách mà vẫn giữ JSON mặc định, và kiểm chứng qua controller, `curl` và `MockMvc`.
- Biết lúc nào nên cấu hình Jackson/`ObjectMapper`, dùng bean converter của Spring Boot hoặc cố ý thay thế toàn bộ; tránh nhầm với converter của form/query parameter.

**Phiên bản cố định để tránh nhầm API:** mã minh họa và chữ ký `configureMessageConverters(List<HttpMessageConverter<?>>)` / `extendMessageConverters(List<HttpMessageConverter<?>>)` ở đây áp dụng cho **Spring Framework 6.x / Spring Boot 3.x, Java 17+**. Từ Spring Framework 7, API cấu hình đã phát triển theo mô hình builder; không sao chép chữ ký ở đây vào dự án 7.x mà không đối chiếu tài liệu phiên bản đang dùng.

## 2. Converter được gọi ở bước nào?

Hình dung hai request:

~~~http
POST /api/greetings/json HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json

{"text":"hello"}
~~~

~~~http
POST /api/greetings/custom HTTP/1.1
Host: localhost:8080
Content-Type: application/vnd.demo.uppercase
Accept: application/vnd.demo.uppercase

hello
~~~

Ở ví dụ đầu, Spring MVC có thể dùng converter Jackson để đọc body JSON thành object `Greeting` và viết object thành JSON. Ở ví dụ thứ hai, ta sẽ tự tạo converter đọc dạng text của media type riêng và ghi ra chữ hoa.

Luồng xử lý thường gặp:

1. `DispatcherServlet` định tuyến request đến handler.
2. Với tham số `@RequestBody` (hoặc part của `@RequestPart` cần chuyển payload), Spring MVC tìm converter có khả năng **đọc** kiểu Java đích cùng `Content-Type`.
3. Handler xử lý đối tượng đã đọc.
4. Với `@ResponseBody`, `@RestController`, `ResponseEntity<T>`, Spring MVC chọn converter có khả năng **ghi** kiểu kết quả với media type được đàm phán theo `Accept`, `produces` và cấu hình.
5. Converter dùng luồng HTTP để đọc/ghi bytes thực tế.

Giao diện `HttpMessageConverter<T>` có các hành vi như `canRead`, `canWrite`, `read`, `write` và các media type được hỗ trợ. Một converter có thể làm được chiều đọc, chiều ghi hoặc cả hai, tùy implementation.

`Content-Type` mô tả **body được gửi đến**; `Accept` mô tả **dạng phản hồi client chấp nhận**. `consumes` và `produces` trên handler ràng buộc route được chọn. Chúng không tự tạo converter và không bảo đảm dữ liệu thực sự parse được.

Một vài converter quen thuộc (khả dụng thực tế phụ thuộc classpath và cấu hình):

| Converter | Công dụng |
| --- | --- |
| `MappingJackson2HttpMessageConverter` | JSON sang/từ Java object qua Jackson (Framework 6.x) |
| `StringHttpMessageConverter` | Body chuỗi văn bản |
| `ByteArrayHttpMessageConverter` | Đọc/ghi mảng `byte[]` |
| `ResourceHttpMessageConverter` | Làm việc với `Resource` |
| `FormHttpMessageConverter` hoặc `AllEncompassingFormHttpMessageConverter` | Xử lý form data và một số trường hợp ghi multipart |
| Converter XML | JSON/XML theo thư viện liên quan có trong classpath |

Đây là cơ chế của **Spring MVC Servlet stack**, không phải `HttpMessageReader`/`HttpMessageWriter` trong Spring WebFlux.

## 3. Hai hook của `WebMvcConfigurer`: điểm khác biệt cốt lõi

Trong Spring Framework 6.x:

~~~java
public interface WebMvcConfigurer {
    default void configureMessageConverters(
            List<HttpMessageConverter<?>> converters) {}

    default void extendMessageConverters(
            List<HttpMessageConverter<?>> converters) {}
}
~~~

Đoạn trên là **mô phỏng chữ ký của hai method**, không phải toàn bộ định nghĩa thật của interface.

### 3.1. `configureMessageConverters`: khai báo danh sách từ đầu

Trong cấu hình **Spring MVC thông thường**, danh sách truyền vào ban đầu rỗng. Nếu các cấu hình `configureMessageConverters` điền converter vào danh sách, quá trình **tự động đăng ký danh sách mặc định của framework sẽ không diễn ra theo cơ chế thông thường**. Bạn phải bảo đảm mọi định dạng mà hệ thống cần đều có converter phù hợp.

Ví dụ dưới đây chỉ minh họa **phương án thay thế** cho Spring MVC thuần. **Đừng thêm class này vào dự án thực hành ở phần 5**:

~~~java
package com.example.demo.replace;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ReplaceConvertersConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(
            List<HttpMessageConverter<?>> converters) {
        converters.add(new MappingJackson2HttpMessageConverter());
    }
}
~~~

Nếu danh sách hiệu lực thực tế chỉ có converter JSON, các chức năng vốn dựa vào `StringHttpMessageConverter` hoặc `ByteArrayHttpMessageConverter` có thể ngừng hoạt động. Phải chủ động đăng ký đủ converter khác khi chọn cách thay thế; đừng chỉ sửa một lỗi JSON rồi vô tình bỏ mất hỗ trợ text, bytes, form, XML hoặc các kiểu body khác.

**Ngoại lệ quan trọng của Spring Boot:** Spring Boot 3.x tự cấu hình converter và bổ sung các bean `HttpMessageConverter` theo cơ chế `HttpMessageConverters`. Vì thế không suy diễn máy móc rằng thêm một converter trong `configureMessageConverters` sẽ làm mất *tất cả* converter trong ứng dụng Boot. Kết quả hiệu lực phụ thuộc Boot auto-configuration và các `WebMvcConfigurer` khác. Khi cần một converter bổ sung mà vẫn giữ hành vi Boot, cách dễ dự đoán hơn là `extendMessageConverters` hoặc cơ chế đăng ký converter của Boot.

### 3.2. `extendMessageConverters`: bổ sung hoặc điều chỉnh ở cuối

Hook này nhận danh sách đã được cấu hình (gồm converter mặc định, hoặc danh sách do các config khác thiết lập) để bạn **thêm, xóa, sắp thứ tự hay tinh chỉnh**:

~~~java
@Configuration
public class ExtendConvertersConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(
            List<HttpMessageConverter<?>> converters) {
        converters.add(0, new UppercaseGreetingConverter());
    }
}
~~~

Đây là minh họa ở dạng class: cần các import `Configuration`, `WebMvcConfigurer`, `HttpMessageConverter` và class `UppercaseGreetingConverter` ở cùng project. Ứng dụng hoàn chỉnh ở phần 5 sẽ có đầy đủ file.

- `add(0, ...)` đưa converter mới lên **đầu danh sách**, để nó có cơ hội được xét trước nếu nhiều converter cùng hỗ trợ kiểu và media type.
- `add(...)` ở cuối thường là lựa chọn ít gây ảnh hưởng hơn khi định dạng được thêm rất riêng biệt.
- Nếu converter mới hỗ trợ quá rộng (`*/*`, `Object.class`), cho nó đứng đầu có thể khiến JSON/text sẵn có bị “cướp” bởi converter mới.
- `extend` **không bảo đảm giữ mặc định** nếu chính bạn gọi `converters.clear()`, `removeIf(...)` hoặc sửa converter hiện có theo cách làm mất khả năng đọc/ghi. Tên method không tự bảo vệ khỏi thay đổi phá vỡ tương thích.

| Khía cạnh | `configureMessageConverters` | `extendMessageConverters` |
| --- | --- | --- |
| Khi được gọi trong chu trình MVC 6.x | Giai đoạn cấu hình danh sách converter | Sau cấu hình / bổ sung mặc định |
| Mục đích phổ biến | Chủ động kiểm soát bộ converter sẽ dùng | Giữ và tùy chỉnh danh sách đã có |
| Với Spring MVC thuần | Thêm converter thường vô hiệu hóa bước đăng ký default | Mặc định được lập trước khi mở rộng nếu cần |
| Rủi ro | Bỏ sót khả năng đọc/ghi cũ | Thứ tự xung đột hoặc sửa/xóa sai converter |
| Gợi ý trong Spring Boot 3.x | Cần xét tương tác Boot auto-config | Thường tiện hơn khi chỉ cần bổ sung |
| Nên kiểm chứng | Danh sách converter hiệu lực và nhiều media type | Danh sách/thứ tự, JSON cũ và media type mới |

### 3.3. Khi nào cố ý thay thế toàn bộ?

Chỉ cân nhắc chủ động thay bộ mặc định khi bạn kiểm soát rõ mọi định dạng mà ứng dụng được phép trao đổi (ví dụ giao thức API chuyên biệt hoặc môi trường hạn chế). Khi đó cần kiểm thử đầy đủ các đường `@RequestBody`, `@ResponseBody`, `ResponseEntity`, exception response, file/resource, multipart/form, text và JSON đang được dùng.

Với nhu cầu thường gặp như thêm một media type, sửa JSON naming strategy, thay một serializer hoặc thêm hỗ trợ loại payload mới, bạn **không cần phá danh sách mặc định**.

## 4. Spring MVC thuần khác Spring Boot như thế nào?

### Cấu hình MVC thuần

Với cấu hình Spring MVC bật bằng `@EnableWebMvc`, Spring Framework đăng ký các thành phần hạ tầng MVC và cung cấp tập converter mặc định dựa trên thư viện có sẵn. `configureMessageConverters` cho bạn chỉ định danh sách riêng, còn `extendMessageConverters` cho phép can thiệp sau bước cấu hình. Nếu dùng cả hai và `configure` đã điền converter, `extend` thấy danh sách do config tạo ra; không được giả định default đã được giữ.

### Spring Boot 3.x

`spring-boot-starter-web` thường đã có Jackson và auto-configuration phục vụ JSON. Spring Boot thu thập các bean `HttpMessageConverter`, bổ sung/ghi đè một số converter mặc định, và cung cấp `HttpMessageConverters` để quản lý tổ hợp. Những đường cấu hình thường dùng:

1. Giữ auto-configuration, tạo bean converter riêng khi cần một converter thêm.
2. Dùng `WebMvcConfigurer#extendMessageConverters` khi cần kiểm soát vị trí hoặc tinh chỉnh danh sách có sẵn.
3. Với JSON, ưu tiên `spring.jackson.*`, bean `Jackson2ObjectMapperBuilderCustomizer`, hoặc Jackson module / annotation riêng cho DTO nếu chỉ muốn thay một phần serialization.
4. Khi cần toàn quyền MVC, có thể chọn `@EnableWebMvc` hoặc kế thừa cấu hình MVC chuyên sâu. **Lựa chọn đó làm Spring Boot bỏ qua phần đáng kể Web MVC auto-configuration**, nên chỉ dùng khi đã chủ động quản lý hệ quả.

Không nên thêm `@EnableWebMvc` vào class `WebMvcConfigurer` của ứng dụng Spring Boot thông thường chỉ để đăng ký một converter.

**Lưu ý phiên bản:** các class `HttpMessageConverters` và cơ chế builder/configuration được chỉnh sửa theo thế hệ Spring Boot/Framework. Khi chuyển từ Boot 3 lên Boot 4/Framework 7, hãy kiểm tra Javadoc phiên bản tương ứng thay vì giữ nguyên import API 6.x.

## 5. Thực hành: thêm media type riêng mà JSON vẫn hoạt động

Trong bài này, ta xây dựng media type `application/vnd.demo.uppercase`. Client gửi text `hello`; controller nhận object `Greeting`, trả lại cùng object; converter ghi body `HELLO`. Song song, endpoint JSON mặc định tiếp tục đọc và ghi `{"text":"hello"}`.

Tạo dự án bằng Spring Initializr với **Spring Boot 3.x**, **Java 17+**, **Maven**, dependency **Spring Web**. Thêm **Spring Boot Starter Test** để chạy phần MockMvc. Không tạo thêm class `ReplaceConvertersConfig` ở phần 3.

Cấu trúc file:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/Greeting.java
src/main/java/com/example/demo/UppercaseGreetingConverter.java
src/main/java/com/example/demo/ConverterConfig.java
src/main/java/com/example/demo/GreetingController.java
src/test/java/com/example/demo/GreetingControllerTest.java
~~~

### 5.1. Điểm khởi động

**`src/main/java/com/example/demo/DemoApplication.java`**

~~~java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

### 5.2. Kiểu dữ liệu

**`src/main/java/com/example/demo/Greeting.java`**

~~~java
package com.example.demo;

public record Greeting(String text) {
}
~~~

Jackson đi kèm Starter Web trong Boot 3.x hỗ trợ record này với định dạng JSON có property `text`.

### 5.3. Viết converter với media type giới hạn

**`src/main/java/com/example/demo/UppercaseGreetingConverter.java`**

~~~java
package com.example.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;

public class UppercaseGreetingConverter
        extends AbstractHttpMessageConverter<Greeting> {

    public static final String MIME = "application/vnd.demo.uppercase";

    public UppercaseGreetingConverter() {
        super(MediaType.parseMediaType(MIME));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return Greeting.class.equals(clazz);
    }

    @Override
    protected Greeting readInternal(
            Class<? extends Greeting> clazz,
            HttpInputMessage inputMessage) throws IOException {

        byte[] body = inputMessage.getBody().readAllBytes();
        String text = new String(body, StandardCharsets.UTF_8);
        return new Greeting(text);
    }

    @Override
    protected void writeInternal(
            Greeting greeting,
            HttpOutputMessage outputMessage) throws IOException {

        String upper = greeting.text().toUpperCase(Locale.ROOT);
        outputMessage.getBody()
                .write(upper.getBytes(StandardCharsets.UTF_8));
    }
}
~~~

Các chi tiết cần hiểu:

- `AbstractHttpMessageConverter<Greeting>` hiện thực phần lớn hợp đồng `HttpMessageConverter`, giúp tập trung vào kiểu Java được hỗ trợ, đọc body và ghi body.
- `supports` chỉ chấp nhận class `Greeting`; constructor chỉ đăng ký **một** media type riêng. Converter không tự giành dữ liệu JSON (`application/json`) của Jackson.
- `readInternal` nhận bytes UTF-8 của HTTP request và tạo `Greeting`; `writeInternal` tạo chữ hoa với `Locale.ROOT`, trả bytes UTF-8. Không tự nối thêm newline.
- `readAllBytes()` dùng để giữ ví dụ đơn giản; **trong production** cần giới hạn dung lượng request ở server/proxy trước khi nhận body hoặc dùng streaming nếu thích hợp. Ví dụ này không dành cho payload lớn.
- Dữ liệu đầu vào được coi là text UTF-8 do **hợp đồng media type riêng của ví dụ**. Converter thực tế nên kiểm tra charset và quy tắc encoding, thông báo lỗi khi dữ liệu không hợp lệ.
- Đối với kiểu có nhiều polymorphic subtype, `supports(Greeting.class.equals(clazz))` có thể quá chặt; đây là lựa chọn có chủ đích để tránh converter bắt nhầm kiểu khác.

### 5.4. Mở rộng thay vì thay danh sách mặc định

**`src/main/java/com/example/demo/ConverterConfig.java`**

~~~java
package com.example.demo;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ConverterConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(
            List<HttpMessageConverter<?>> converters) {
        converters.add(0, new UppercaseGreetingConverter());
    }
}
~~~

Chỉ dùng `extendMessageConverters`. Ta thêm converter riêng vào danh sách hiệu lực mà không xóa cấu hình converter JSON đã có. Với hỗ trợ media type hẹp và class hẹp, `add(0, ...)` an toàn trong phạm vi mẫu: converter chỉ cạnh tranh khi cả **kiểu Java lẫn media type** phù hợp. Trong hệ thống thực, cần đánh giá thứ tự và media type của các converter khác trước khi chọn index.

### 5.5. Controller kiểm tra cả định dạng riêng và JSON

**`src/main/java/com/example/demo/GreetingController.java`**

~~~java
package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {

    @PostMapping(
            path = "/custom",
            consumes = UppercaseGreetingConverter.MIME,
            produces = UppercaseGreetingConverter.MIME)
    public Greeting custom(@RequestBody Greeting greeting) {
        return greeting;
    }

    @PostMapping(
            path = "/json",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Greeting json(@RequestBody Greeting greeting) {
        return greeting;
    }
}
~~~

Handler `/custom` cần converter riêng ở cả chiều request và response; handler `/json` cần converter Jackson của Boot. Nếu thay converter mặc định không cẩn thận, test JSON sẽ phát hiện hồi quy.

## 6. Chạy và đọc kết quả HTTP

Tại thư mục dự án có `pom.xml`, chạy:

~~~bash
./mvnw spring-boot:run
~~~

Với Windows, dùng `mvnw.cmd spring-boot:run`; nếu project không có wrapper, dùng `mvn spring-boot:run` khi đã cài Maven.

Gửi body text theo media type tùy chỉnh:

~~~bash
curl -i -X POST "http://localhost:8080/api/greetings/custom"   -H "Content-Type: application/vnd.demo.uppercase"   -H "Accept: application/vnd.demo.uppercase"   --data-binary "hello"
~~~

**Kết quả kỳ vọng:** status **200 OK**, header response có `Content-Type` tương thích `application/vnd.demo.uppercase` và **body chính xác**:

~~~text
HELLO
~~~

Code `writeInternal` không thêm ký tự xuống dòng vào HTTP body; đoạn Markdown phía trên có xuống dòng để hiển thị, không hàm ý một newline của response.

Kiểm chứng JSON mặc định còn chạy:

~~~bash
curl -i -X POST "http://localhost:8080/api/greetings/json"   -H "Content-Type: application/json"   -H "Accept: application/json"   --data-binary '{"text":"hello"}'
~~~

**Kết quả kỳ vọng:** **200 OK** và JSON body tương đương:

~~~json
{"text":"hello"}
~~~

Thứ tự các property JSON không cần cố định. Nếu chạy trong Windows PowerShell, nên dùng `curl.exe` thay vì alias `curl`; cách escape JSON và nối dòng phải phù hợp shell. Ví dụ trên dành cho Linux/macOS/Git Bash.

### 6.1. Kiểm thử 415 và 406

Request sai `Content-Type` cho endpoint custom:

~~~bash
curl -i -X POST "http://localhost:8080/api/greetings/custom"   -H "Content-Type: application/json"   -H "Accept: application/vnd.demo.uppercase"   --data-binary '{"text":"hello"}'
~~~

Kết quả điển hình: **415 Unsupported Media Type** do handler chỉ nhận `application/vnd.demo.uppercase`. Ngay cả khi ứng dụng có Jackson, `consumes` của handler vẫn là một điều kiện chọn route.

Request chấp nhận định dạng response không khớp:

~~~bash
curl -i -X POST "http://localhost:8080/api/greetings/custom"   -H "Content-Type: application/vnd.demo.uppercase"   -H "Accept: application/json"   --data-binary "hello"
~~~

Kết quả điển hình: **406 Not Acceptable**, vì handler khai báo chỉ `produces = application/vnd.demo.uppercase`, không đáp ứng `Accept: application/json`. Nội dung lỗi có thể thay đổi theo cấu hình Spring Boot, Servlet container và `@ControllerAdvice`, nên chỉ xác nhận mã status và contract HTTP.

**Phân biệt:** 415 thường liên quan media type của **request body**; 406 liên quan media type của **response** mà client yêu cầu; 400 có thể do body có media type được chấp nhận nhưng dữ liệu không parse được thành kiểu Java.

## 7. Kiểm thử tự động bằng MockMvc

Thêm dependency `spring-boot-starter-test` trong dự án. Với Spring Boot 3.x, viết:

**`src/test/java/com/example/demo/GreetingControllerTest.java`**

~~~java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GreetingController.class)
@Import(ConverterConfig.class)
class GreetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void newConverterReadsAndWritesCustomMediaType() throws Exception {
        mockMvc.perform(post("/api/greetings/custom")
                        .contentType(UppercaseGreetingConverter.MIME)
                        .accept(UppercaseGreetingConverter.MIME)
                        .content("hello"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        UppercaseGreetingConverter.MIME))
                .andExpect(content().string("HELLO"));
    }

    @Test
    void jacksonJsonStillReadsAndWrites() throws Exception {
        mockMvc.perform(post("/api/greetings/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("hello"));
    }

    @Test
    void rejectsUnsupportedRequestContentType() throws Exception {
        mockMvc.perform(post("/api/greetings/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(UppercaseGreetingConverter.MIME)
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsUnacceptableResponseMediaType() throws Exception {
        mockMvc.perform(post("/api/greetings/custom")
                        .contentType(UppercaseGreetingConverter.MIME)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("hello"))
                .andExpect(status().isNotAcceptable());
    }
}
~~~

Chạy:

~~~bash
./mvnw test
~~~

Bốn test cần thiết vì chỉ kiểm tra endpoint tùy chỉnh thành công **không chứng minh** JSON mặc định còn chạy. Test thứ hai là phép kiểm tra hồi quy cốt lõi khi so sánh “mở rộng” với “thay thế”. `@Import(ConverterConfig.class)` đảm bảo cấu hình converter được đưa vào test slice. `@WebMvcTest` kiểm tra stack MVC bằng `MockMvc` mà không cần mở server thật.

Nếu class test nằm trong dự án đang bật Spring Security với rule xác thực/CSRF, phải cấu hình MockMvc phù hợp chính sách đó; không tắt bảo mật tùy tiện chỉ để test converter. Bài mẫu chỉ có Spring Web và Starter Test.

## 8. Thay JSON behavior mà không cần tự tạo converter mới

Nếu mục tiêu là đổi cách Jackson serialize/deserialize JSON, phương án tùy biến **Jackson** thường đơn giản hơn việc thay toàn bộ `HttpMessageConverter`. Ví dụ Spring Boot 3.x có thể cấu hình:

~~~properties
spring.jackson.serialization.indent_output=true
spring.jackson.default-property-inclusion=non_null
~~~

Lưu ý property này điều chỉnh việc thụt dòng JSON và bỏ property có giá trị null theo Jackson; không thay media type, không can thiệp converter text hay form. Khi nâng cấp, kiểm tra danh sách property được phiên bản Boot hỗ trợ.

Với logic phức tạp hơn, bạn có thể đăng ký `Jackson2ObjectMapperBuilderCustomizer` để cấu hình `ObjectMapper` do Boot xây dựng. Nếu tự tạo `MappingJackson2HttpMessageConverter` với `new ObjectMapper()`, bạn có thể làm mất cấu hình module, Java Time, mix-in, naming strategy và các cấu hình khác mà Boot/Jackson đã chuẩn bị. Khi dùng `@JsonFormat`, `@JsonProperty` hoặc serializer tùy chỉnh trên DTO, sự thay đổi còn có thể giới hạn ở một model, an toàn hơn với toàn ứng dụng.

Đôi khi bạn cần sửa một converter đang có trong `extendMessageConverters`. Ví dụ minh họa:

~~~java
@Override
public void extendMessageConverters(
        List<HttpMessageConverter<?>> converters) {
    for (HttpMessageConverter<?> candidate : converters) {
        if (candidate instanceof MappingJackson2HttpMessageConverter jackson) {
            // Đã tìm được converter Jackson.
            // Chỉ điều chỉnh cấu hình có chủ đích và đã kiểm thử.
        }
    }
}
~~~

Đây là **đoạn minh họa**, không phải class Java độc lập; cần các import và annotation/config tương ứng. Tránh mutate `ObjectMapper` được chia sẻ sau khi đã phục vụ request: nhiều cấu hình Jackson được thiết kế để xác lập lúc startup. Nếu cần thay riêng một converter, chuẩn bị mapper/cấu hình trước khi server chạy; kiểm tra side effect với những nơi cùng dùng mapper đó.

## 9. Thứ tự converter và content negotiation

Spring MVC có thể có nhiều converter cho một Java type và media type. Khi các converter cùng báo `canRead`/`canWrite`, **thứ tự đăng ký** có thể quyết định converter được chọn. Nhất là khi client gửi `Accept: */*`, converter đứng trước có khả năng được ưu tiên. Tuy nhiên chọn response còn phụ thuộc `produces`, các media type hỗ trợ, kiểu trả về và content negotiation; không phải cứ converter đầu tiên trong list là luôn thắng.

Các nguyên tắc nên tuân thủ:

- Giới hạn `supportedMediaTypes` thật hẹp cho converter tùy chỉnh (ví dụ `application/vnd.demo.uppercase`), tránh `MediaType.ALL` nếu không thực sự cần.
- Chỉ hỗ trợ đúng Java type trong `supports`, hoặc thêm điều kiện rõ cho generic type nếu triển khai `GenericHttpMessageConverter`.
- Nếu cần override converter mặc định cho cùng type/media type, sắp thứ tự có ý thức và kiểm thử **đọc lẫn ghi**; với Boot xem xét cơ chế bean/HttpMessageConverters.
- Hạn chế đặt converter generic nhận `Object` đầu danh sách; nó có thể chặn `MappingJackson2HttpMessageConverter`, trả nội dung hoặc media type sai.
- Kiểm tra cả `Content-Type` request, `Accept`, `consumes`, `produces`. Một status 406/415 không đủ để kết luận converter “bị thiếu” trước khi đối chiếu toàn bộ.
- Đừng đặt `produces` sai thực tế: controller tuyên bố JSON nhưng converter lại ghi text không phải JSON sẽ làm sai contract API.

Nếu cần xác minh trong hệ thống đang chạy, có thể xem danh sách converter được đăng ký với `RequestMappingHandlerAdapter#getMessageConverters()` sau khi context được khởi tạo; kết quả phụ thuộc auto-config và các `WebMvcConfigurer` khác. Chỉ đọc cấu hình nguồn của một class chưa đủ để biết toàn bộ danh sách hiệu lực.

## 10. Phân biệt bộ chuyển đổi HTTP body với data binding

Spring MVC có hai nhóm chuyển đổi **khác nhiệm vụ**:

| Bài toán | Cơ chế phù hợp | Ví dụ |
| --- | --- | --- |
| JSON body thành Java object | `HttpMessageConverter` | `@RequestBody Greeting body` |
| Java object thành JSON response | `HttpMessageConverter` | `@RestController` trả `Greeting` |
| JSON part trong multipart | `HttpMessageConverter` theo part | `@RequestPart("metadata") SomeDto dto` |
| Query parameter string thành `LocalDate` | `ConversionService` / `Formatter` | `@RequestParam LocalDate date` |
| Form field string thành property của model | `WebDataBinder` và conversion/formatting | `@ModelAttribute Form form` |
| Chuỗi cần trim trước khi bind form | `@InitBinder`/editor/formatter phù hợp | `WebDataBinder.registerCustomEditor(...)` |

Đừng sửa `configureMessageConverters` để xử lý `@RequestParam("date") LocalDate date`; thông thường cần `Converter<String, LocalDate>`, `Formatter` hoặc `@DateTimeFormat`. Ngược lại, một `Converter<String, String>` của conversion service không tự giải mã JSON body.

Những nhầm lẫn này đặc biệt hay xuất hiện khi controller có cả `@RequestBody` và `@RequestParam`: mỗi loại tham số có cơ chế resolution và conversion tương ứng.

## 11. Những lỗi cấu hình thường gặp

### Lỗi 1: Chỉ thêm một converter trong `configureMessageConverters` của MVC thuần

**Hiện tượng:** endpoint JSON mới hoạt động nhưng response `String`, binary, form hoặc định dạng khác hỏng.

**Nguyên nhân:** danh sách explicit làm bỏ qua bước đăng ký defaults thông thường.

**Cách sửa:** đổi sang `extendMessageConverters` nếu chỉ thêm chức năng; nếu thật sự thay thế, khai báo đầy đủ converter cần thiết và test những endpoint hiện hữu.

### Lỗi 2: Cho converter riêng hỗ trợ `*/*` và `Object.class`

**Hiện tượng:** response `application/json` đôi khi bị ghi thành text tùy chỉnh; serializer cũ không được gọi.

**Nguyên nhân:** converter tùy chỉnh bắt quá nhiều type/media type và có thứ tự ưu tiên cao.

**Cách sửa:** giới hạn `supports`, `supportedMediaTypes`, vị trí; kiểm thử JSON/text cả chiều đọc/ghi.

### Lỗi 3: Dùng `@EnableWebMvc` chỉ để thay một converter trong Spring Boot

**Hiện tượng:** cấu hình web/resource handling và nhiều MVC customizations của Boot thay đổi ngoài dự kiến.

**Nguyên nhân:** `@EnableWebMvc` chuyển ứng dụng sang quản lý MVC chủ động, làm mất đường auto-configuration MVC thông dụng.

**Cách sửa:** dùng bean `WebMvcConfigurer` đơn giản, không `@EnableWebMvc`, hoặc cơ chế converter mà Boot cung cấp.

### Lỗi 4: Gửi JSON với `Content-Type: text/plain`

**Hiện tượng:** DTO `@RequestBody` không parse được hoặc nhận status 415.

**Nguyên nhân:** converter Jackson thường nhắm các JSON media type, trong khi header tuyên bố text.

**Cách sửa:** gửi header đúng dạng `application/json` và dữ liệu JSON hợp lệ; không sửa converter thành nhận `*/*` để che lỗi client.

### Lỗi 5: Chỉ kiểm thử HTTP 200

**Hiện tượng:** API mới hoạt động, API cũ sử dụng converter mặc định khác bị hỏng sau deploy.

**Cách sửa:** có test regression cho ít nhất JSON read/write, media type mới, 415, 406; mở rộng thêm các endpoint text/bytes/form thực sự đang sử dụng.

### Lỗi 6: Nhầm lỗi validation với lỗi converter

**Hiện tượng:** request chuyển thành Java object được nhưng bị 400 do `@Valid`.

**Nguyên nhân:** converter giải mã body thành công; lỗi phát sinh ở validation sau đó.

**Cách sửa:** đọc exception và log tương ứng (ví dụ parse JSON thường phát sinh `HttpMessageNotReadableException`; lỗi validation có thể là `MethodArgumentNotValidException`), xác định tầng trước khi thay cấu hình.

### Lỗi 7: Tưởng cấu hình MVC luôn áp dụng cho RestClient hoặc WebFlux

**Hiện tượng:** một `RestClient` hoặc reactive endpoint không sử dụng converter đã thêm trong MVC.

**Nguyên nhân:** client HTTP và WebFlux có stack riêng; không giả định chúng chia sẻ danh sách converter với Spring MVC server.

**Cách sửa:** cấu hình đúng client hoặc codec reactive tương ứng và kiểm thử riêng.

## 12. Quy tắc quyết định và checklist

| Nhu cầu | Phương án nên bắt đầu |
| --- | --- |
| Chỉ thêm media type/Java type mới vào MVC | `extendMessageConverters` với converter hẹp |
| Dùng Spring Boot 3.x và có bean converter riêng | Đăng ký `HttpMessageConverter` bean theo cơ chế Boot; xem xét `HttpMessageConverters` khi cần |
| Muốn đổi JSON naming/date/null/indent | Jackson annotation, `spring.jackson.*`, customizer/ObjectMapper |
| Muốn thay converter mặc định cụ thể | Cơ chế override converter/bean của Boot hoặc `extend` với thứ tự rõ; kiểm thử |
| Cần kiểm soát toàn bộ protocol hỗ trợ trong MVC thuần | `configureMessageConverters` và đăng ký đủ converter |
| Muốn chuyển query/form field | `ConversionService`, `Formatter`, `@InitBinder`, không phải converter HTTP body |

Trước khi đưa cấu hình vào production, hãy tự kiểm tra: **(1)** framework và Boot đang dùng phiên bản nào; **(2)** danh sách converter thực tế còn hỗ trợ các media type cũ không; **(3)** converter tùy chỉnh có giới hạn type/media type đủ chặt không; **(4)** có test cả request/response và 406/415 không; **(5)** có dùng `@EnableWebMvc` ngoài ý muốn không; **(6)** các response lỗi, resource download, form/multipart và client giao tiếp khác còn hoạt động không.

## Tài liệu chính thức theo phiên bản

- [Spring Framework 6.2 — Message Converters trong MVC Config](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/message-converters.html)
- [Spring Framework 6.2 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html)
- [Spring Framework 6.2 — `WebMvcConfigurer` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/web/servlet/config/annotation/WebMvcConfigurer.html)
- [Spring Boot 3.5 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html)
- [Spring Framework 6.2 — MVC enable configuration](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/enable.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-message-bodies-004">Tuần tự hóa JSON, DTO, kiểu generic và tham chiếu vòng</a>

<details>
<summary>Xem chi tiết</summary>

# Spring MVC: Tuần tự hóa JSON, DTO, kiểu generic và tham chiếu vòng

## 1. Mục tiêu bài học

Trong REST API với Spring MVC, controller nhận dữ liệu JSON từ client và trả JSON trở lại. Đây không chỉ là thao tác chuyển chuỗi: hệ thống cần biết Java type, hợp đồng DTO, quy tắc validation và cách biểu diễn một object graph mà không lặp vô hạn.

Sau bài này, bạn sẽ:

- Giải thích **serialization** (Java object → JSON) và **deserialization** (JSON → Java object) bằng Jackson thông qua Spring MVC.
- Phân biệt `@RequestBody`, `@ResponseBody`, `@RestController`, `ResponseEntity` và `HttpMessageConverter`.
- Thiết kế request DTO, response DTO để tránh lộ dữ liệu nội bộ hay vô tình serialize JPA entity.
- Sử dụng các container generic như `List<BookResponse>` và `ApiResponse<List<BookResponse>>`; dùng `TypeReference`/`JavaType` khi tự đọc JSON.
- Nhận ra vòng tham chiếu hai chiều, lựa chọn DTO hữu hạn, `@JsonIgnore`, `@JsonManagedReference`/`@JsonBackReference` hoặc `@JsonIdentityInfo`.
- Kiểm thử chính xác status, JSON structure, validation và xử lý lỗi.

Ví dụ thực hành dùng **Java 17+, Spring Boot 3.5.0, Spring Framework 6.2 và Jackson 2** (các import `com.fasterxml.jackson.*`). Các đoạn code được ghi rõ khi chỉ minh họa; dự án hoàn chỉnh nằm ở mục 9.

## 2. Luồng dữ liệu JSON trong Spring MVC

Một request:

~~~http
POST /api/books HTTP/1.1
Content-Type: application/json
Accept: application/json

{"title":"Spring MVC","pages":240}
~~~

Một handler tối giản:

~~~java
@PostMapping("/books")
public BookResponse create(@RequestBody CreateBookRequest input) {
    return new BookResponse(1, input.title(), input.pages());
}
~~~

Handler này là một phần minh họa, giả định các record đã được khai báo và controller có `@RestController`.

**Luồng nhận dữ liệu**:

1. MVC chọn handler theo path, HTTP method và điều kiện mapping.
2. `@RequestBody` yêu cầu đọc request body; `HttpMessageConverter` thích hợp được chọn dựa vào Content-Type và Java target type.
3. Với JSON và Spring Framework 6.2/Jackson 2, converter thông thường là `MappingJackson2HttpMessageConverter` sử dụng `ObjectMapper` để parse và tạo `CreateBookRequest`.
4. Nếu controller khai báo `@Valid` thì Bean Validation chạy sau khi deserialize thành công.
5. Handler xử lý nghiệp vụ và tạo giá trị trả về.
6. `@ResponseBody` (trực tiếp hoặc từ `@RestController`) làm Spring dùng converter ghi object thành JSON trong HTTP response.

**Những phân biệt cần nhớ**:

| Khái niệm | Nhiệm vụ | Ví dụ |
| --- | --- | --- |
| Serialization | Object Java → JSON bytes | `BookResponse` trả cho browser |
| Deserialization | JSON bytes → Object Java | `@RequestBody CreateBookRequest` |
| DTO mapping | Domain/entity → contract của API | `BookEntity` → `BookResponse` |
| Bean Validation | Kiểm tra quy tắc của object sau parse | `@NotBlank`, `@Min` |
| `ResponseEntity<T>` | Điều khiển status, headers và body | `201 Created` + `BookResponse` |
| `HttpMessageConverter` | Đọc/ghi HTTP body theo media type | Jackson JSON converter |
| `ObjectMapper` | Jackson API để map JSON ↔ Java object | `readValue`, `writeValueAsString` |

`@RestController` tương đương dùng `@Controller` cùng `@ResponseBody` ở class. Với `@Controller` thuần, một giá trị trả về dạng `String` có thể được hiểu là tên **view** chứ không phải chuỗi JSON. `ResponseEntity` bọc status và headers ở lớp HTTP, không mặc nhiên serialize bản thân nó thành JSON body.

### 2.1. Content-Type và Accept

- `Content-Type: application/json` nói rằng body client gửi là JSON.
- `Accept: application/json` đề nghị server trả JSON.
- Handler có `consumes = "application/json"` có thể trả **415 Unsupported Media Type** nếu client gửi Content-Type khác.
- Nếu client chỉ chấp nhận media type mà server không thể tạo theo `produces`/converter, có thể trả **406 Not Acceptable**.
- JSON hỏng cú pháp hoặc value không thể chuyển sang Java type phù hợp thường dẫn tới **400 Bad Request** trước khi handler chạy.
- Một lỗi **serialization** khi ghi response (ví dụ vòng graph) là lỗi phía server, không phải mặc nhiên là lỗi JSON input của client.

Spring Boot 3.x với `spring-boot-starter-web` thông thường đã có Jackson và cấu hình JSON message converter. Không cần tự tạo `ObjectMapper` mới trong mọi controller.

## 3. Java object được biểu diễn thành JSON như thế nào?

JSON có object, array, string, number, boolean và `null`. Nó không mang Java class, package, setter, constructor, generic parameter hoặc kiểu JPA. Jackson dùng metadata từ Java type, accessor và annotation để xác định field JSON.

JavaBean:

~~~java
public class BookBean {
    private long id;
    private String title;

    public BookBean() {}
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
~~~

Record trong Java 17:

~~~java
public record BookResponse(long id, String title, int pages) {}
~~~

Jackson 2 trong Spring Boot 3.x hỗ trợ Java records. Giá trị `new BookResponse(1, "Java", 300)` tương đương JSON:

~~~json
{"id":1,"title":"Java","pages":300}
~~~

Để deserialize một DTO, Jackson cần cách xây dựng object phù hợp: canonical constructor cho record, hoặc constructor/factory/getter-setter/field theo cấu hình cho class. Không phải mọi class immutable đều được hỗ trợ tự động nếu thiếu creator và metadata tương ứng.

**Object property order trong JSON không được bảo đảm là contract.** Khi test, so sánh field bằng JSONPath hoặc parse tree thay vì đòi raw JSON string trùng từng ký tự.

### 3.1. Jackson annotations thiết yếu

~~~java
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicUser(
        @JsonProperty("user_id") long id,
        String username,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nickname) {}
~~~

Với `new PublicUser(5, "lan", null)`, Jackson tạo JSON tương đương:

~~~json
{"user_id":5,"username":"lan"}
~~~

| Annotation | Tác động điển hình | Chú ý |
| --- | --- | --- |
| `@JsonProperty("name")` | Đổi tên property trong JSON | Là thay đổi API contract |
| `@JsonIgnore` | Bỏ property khỏi JSON binding | Cần xem ảnh hưởng cả chiều đọc/ghi |
| `@JsonIgnoreProperties({...})` | Bỏ các property được chỉ ra | Không thay authorization |
| `@JsonInclude(NON_NULL)` | Không serialize các field null | Không bắt buộc giá trị tồn tại |
| `@JsonFormat` | Format JSON cho ngày/giờ, số, enum… | Do Jackson xử lý |
| `@JsonCreator` | Chỉ rõ constructor/factory deserialize | Hay đi cùng `@JsonProperty` |
| `@JsonView` | Chọn tập property theo view | Phải kiểm thử các view và bảo vệ dữ liệu |

Ví dụ JSON chứa ngày:

~~~java
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

public record EventResponse(
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate eventDate) {}
~~~

Với Spring Boot 3.x, Jackson Java Time module thường có sẵn qua dependency được quản lý. `@JsonFormat` tác động vào **Jackson JSON mapping**. `@DateTimeFormat` chủ yếu dùng trong **Spring MVC request parameter / form binding**: không nên dùng lẫn hai annotation rồi giả định JSON được xử lý như form.

### 3.2. JSON field vắng mặt, null và field không mong đợi

Giả sử:

~~~java
public record CreateBookRequest(String title, Integer pages) {}
~~~

Body `{"title":"A"}` và `{"title":"A","pages":null}` thường đều cho `pages == null` trong cấu hình quen thuộc. Nếu field bắt buộc, dùng `@NotNull` kết hợp `@Valid`. Với PATCH cần phân biệt "không cung cấp field", "cung cấp null" và "cung cấp value", dùng contract thích hợp (JSON Merge Patch, presence wrapper hoặc JsonNode), thay vì coi null và vắng mặt là như nhau.

Jackson có thể bỏ qua unknown fields trong cấu hình Spring Boot thông thường, nhưng chính sách này cấu hình được. Unknown-field behavior cần được định nghĩa và kiểm thử. Dù có từ chối field lạ, authorization vẫn phải được kiểm tra riêng.

## 4. DTO — hợp đồng rõ ràng giữa client và server

**DTO (Data Transfer Object)** chứa dữ liệu API được phép nhận/gửi. Entity/domain model có thể chứa quan hệ và trạng thái mà client không nên thấy.

Ví dụ entity nội bộ (minh họa):

~~~java
class AccountEntity {
    long id;
    String displayName;
    String passwordHash;
    boolean administrator;
    TeamEntity team;
}
~~~

Nếu controller trả entity trực tiếp, serializer có thể thấy hash, trạng thái quản trị hoặc đi theo liên kết `team`. Với JPA/Hibernate còn có thể phát sinh lazy loading, N+1 query và vòng tham chiếu.

Response DTO:

~~~java
public record AccountResponse(long id, String displayName) {}
~~~

Request DTO:

~~~java
import jakarta.validation.constraints.NotBlank;

public record UpdateAccountRequest(@NotBlank String displayName) {}
~~~

Mapping trong service:

~~~java
AccountResponse toResponse(AccountEntity account) {
    return new AccountResponse(account.id, account.displayName);
}
~~~

Các đoạn trên minh họa thiết kế, chưa phải file chạy độc lập vì chưa định nghĩa `TeamEntity` và các thành phần cần thiết khác.

**Vì sao tách request và response DTO?**

- Client không được tự đặt `id`, `role`, `balance` hoặc `passwordHash` chỉ bằng cách thêm property vào JSON.
- Service quyết định trường nào sửa được và thực hiện kiểm tra authorization.
- Response chỉ có field được công bố; không lộ object graph hay property nội bộ.
- DB schema và entity có thể thay đổi mà không phải đổi JSON contract.
- API versioning, validation và tài liệu OpenAPI dễ mô tả hơn.

DTO record gọn và immutable, phù hợp snapshot thông tin. DTO class vẫn hữu ích khi có yêu cầu construction/mapping đặc biệt. **Không bind `@RequestBody` trực tiếp vào JPA entity** nếu nó có nhiều field ngoài quyền sửa của người dùng. DTO hạn chế field không tự thay thế kiểm tra quyền xem/sửa resource trong service.

## 5. Generic response và type erasure

### 5.1. Generic DTO đơn và lồng nhau

~~~java
public record ApiResponse<T>(String status, T data) {}
~~~

Dùng cho một giá trị:

~~~java
ApiResponse<BookResponse> one =
        new ApiResponse<>("ok", new BookResponse(1, "Java", 300));
~~~

Dùng cho nhiều giá trị:

~~~java
ApiResponse<List<BookResponse>> many = new ApiResponse<>(
        "ok",
        List.of(
                new BookResponse(1, "Java", 300),
                new BookResponse(2, "Spring", 240)));
~~~

JSON của `many`:

~~~json
{
  "status": "ok",
  "data": [
    {"id":1,"title":"Java","pages":300},
    {"id":2,"title":"Spring","pages":240}
  ]
}
~~~

**JSON không ghi "ApiResponse<List<BookResponse>>" vào body.** Nó chỉ có object và array. Java generics ở runtime chịu **type erasure**, vì vậy lúc tự đọc JSON phải cung cấp đủ kiểu phần tử.

Spring MVC thường có thể xác định generic target từ **chữ ký handler**:

~~~java
@PostMapping("/batch")
public void receive(@RequestBody List<BookResponse> books) {
    // Spring biết element type BookResponse từ method signature.
}
~~~

Đây chỉ là chữ ký minh họa. Để validate từng item list còn phải xét container element validation/cấu hình tương ứng; không tự suy ra `@Valid` trên collection nào cũng xử lý giống một request DTO thuần.

### 5.2. Sai lầm khi dùng raw List.class

~~~java
ObjectMapper mapper = new ObjectMapper();
List<?> rawBooks = mapper.readValue(
        "[{\"id\":1,\"title\":\"Java\",\"pages\":300}]",
        List.class);
System.out.println(rawBooks.get(0).getClass().getName());
~~~

Stdout với Jackson 2 mặc định:

~~~text
java.util.LinkedHashMap
~~~

Vì `List.class` thiếu thông tin phần tử, một object JSON mặc định được chuyển thành `LinkedHashMap`. Ép trực tiếp `(BookResponse) rawBooks.get(0)` có thể ném `ClassCastException`.

### 5.3. Đúng: TypeReference<T>

~~~java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

ObjectMapper mapper = new ObjectMapper();
String json = """
    [{"id":1,"title":"Java","pages":300}]
    """;

List<BookResponse> books =
        mapper.readValue(json, new TypeReference<List<BookResponse>>() {});
System.out.println(books.get(0).title());
~~~

Stdout:

~~~text
Java
~~~

Đoạn này giả định `BookResponse` record đã được khai báo. Với generic wrapper:

~~~java
ApiResponse<List<BookResponse>> result = mapper.readValue(
        """
        {"status":"ok","data":[{"id":1,"title":"Java","pages":300}]}
        """,
        new TypeReference<ApiResponse<List<BookResponse>>>() {});
~~~

Anonymous subclass của `TypeReference` nắm giữ signature generic cụ thể. Nếu tạo `new TypeReference<List<T>>() {}` bên trong một generic method mà `T` vẫn chưa được ràng buộc thành class cụ thể, runtime không tự biết `T` thật.

### 5.4. Đúng: JavaType khi dựng kiểu ở runtime

~~~java
import com.fasterxml.jackson.databind.JavaType;

JavaType listType = mapper.getTypeFactory()
        .constructCollectionType(List.class, BookResponse.class);
JavaType nestedType = mapper.getTypeFactory()
        .constructParametricType(ApiResponse.class, listType);

ApiResponse<List<BookResponse>> parsed =
        mapper.readValue(json, nestedType);
~~~

Biến `json` ở đây cần chứa một object `ApiResponse` như ví dụ trước. `JavaType` thích hợp khi loại phần tử là một giá trị `Class<?>` chỉ biết lúc chạy. Với Spring clients có API nhận `ParameterizedTypeReference<T>`, có thể viết:

~~~java
ParameterizedTypeReference<ApiResponse<List<BookResponse>>> responseType =
        new ParameterizedTypeReference<>() {};
~~~

Import `org.springframework.core.ParameterizedTypeReference`. Đây là type token của **Spring**, khác với `com.fasterxml.jackson.core.type.TypeReference` của **Jackson**; không truyền trực tiếp cái này vào `ObjectMapper.readValue`.

| Bối cảnh | Cách xác định kiểu |
| --- | --- |
| Spring MVC handler `@RequestBody List<BookDto>` | Chữ ký tham số controller |
| Controller trả `ResponseEntity<ApiResponse<List<BookDto>>>` | Chữ ký return type cùng object thật |
| Jackson `readValue(json, List.class)` | Mất element type, thường có Map |
| Jackson `readValue(json, new TypeReference<List<BookDto>>() {})` | Giữ element type |
| Jackson với kiểu dựng động | Dùng `JavaType`/`TypeFactory` |
| Spring HTTP client có generic API | Dùng `ParameterizedTypeReference<T>` khi overload hỗ trợ |

`ResponseEntity<T>` bọc response HTTP, không phải field tự động trong JSON. Nếu muốn status ở body, phải khai báo trong `ApiResponse<T>`; status HTTP 201 và `"status":"ok"` là hai thành phần khác nhau.

## 6. Vòng tham chiếu trong object graph

Giả sử quan hệ hai chiều:

~~~java
class Team {
    public long id;
    public String name;
    public List<User> users = new ArrayList<>();
}

class User {
    public long id;
    public String username;
    public Team team;
}
~~~

Các import `java.util.List` và `java.util.ArrayList` cần có. Sau khi tạo `team.users.add(user)` và `user.team = team`, graph trở thành:

~~~text
Team
 └─ users[0] → User
                  └─ team → Team
                                └─ users[0] → User
                                                  └─ ...
~~~

Jackson tự duyệt property mà không có cấu trúc chặn vòng có thể đi qua `team.users[0].team.users[0]...`, gây lỗi recursion/serialization hoặc giới hạn độ sâu, tùy phiên bản. Trong HTTP response có thể gây lỗi server. Một object graph Java có thể tham chiếu lại cùng một instance; JSON object/array thông thường không có cơ chế Java pointer identity riêng.

### 6.1. Giải pháp ưu tiên: DTO có độ sâu hữu hạn

Thay vì trả entity graph đầy đủ:

~~~java
record TeamSummary(long id, String name) {}
record UserResponse(long id, String username, TeamSummary team) {}
~~~

JSON:

~~~json
{
  "id":10,
  "username":"lan",
  "team":{"id":3,"name":"Platform"}
}
~~~

TeamSummary không chứa users. Với endpoint GET team, dùng DTO riêng:

~~~java
record UserSummary(long id, String username) {}
record TeamResponse(long id, String name, List<UserSummary> members) {}
~~~

Do đó response có chỗ dừng. Service chọn đúng dữ liệu cần truy vấn; nếu dùng JPA, có thể dùng projection/fetch phù hợp để tránh N+1. Tạo DTO không tự đảm bảo SQL hiệu quả nhưng cho phép kiểm soát chính xác contract.

### 6.2. @JsonIgnore: bỏ chiều không cần thiết

~~~java
import com.fasterxml.jackson.annotation.JsonIgnore;

class User {
    public long id;
    public String username;

    @JsonIgnore
    public Team team;
}
~~~

Khi serialize Team với list Users, thuộc tính User.team bị bỏ, làm đứt vòng. Tuy nhiên User cũng mất thông tin team khi serialize riêng theo mapping này. Nếu các endpoint cần JSON khác nhau, response DTO vẫn rõ ràng hơn việc đổi annotation entity tùy trường hợp.

### 6.3. @JsonManagedReference và @JsonBackReference

~~~java
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonBackReference;

class Team {
    public long id;
    public String name;

    @JsonManagedReference
    public List<User> users = new ArrayList<>();
}

class User {
    public long id;
    public String username;

    @JsonBackReference
    public Team team;
}
~~~

Khi serialize Team, chiều `Team → users` được ghi, còn reference ngược `User → team` không được ghi:

~~~json
{
  "id":3,
  "name":"Platform",
  "users":[{"id":10,"username":"lan"}]
}
~~~

Với parent-child phù hợp, Jackson còn có thể khôi phục backlink trong deserialization. Các cặp managed/back cần đúng hướng và tên; nếu có nhiều quan hệ độc lập, có thể đặt tên cặp rõ ràng, ví dụ `@JsonManagedReference("members")` và `@JsonBackReference("members")`.

**Giới hạn:** đây là quy tắc có hướng. Serialize User riêng thì Team có thể bị loại bỏ; không thích hợp máy móc cho many-to-many hoặc quan hệ hai list đối xứng. Phải xác định JSON contract trước khi dùng.

### 6.4. @JsonIdentityInfo: biểu diễn object bằng identity

~~~java
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
class Team {
    public long id;
    public String name;
    public List<User> users = new ArrayList<>();
}

@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
class User {
    public long id;
    public String username;
    public Team team;
}
~~~

Một dạng JSON có thể được tạo khi serialize Team làm root (thứ tự duyệt và identity scope ảnh hưởng chi tiết):

~~~json
{
  "id":3,
  "name":"Platform",
  "users":[{"id":10,"username":"lan","team":3}]
}
~~~

Lần đầu object xuất hiện có thể được ghi đầy đủ, lần sau ghi id thay vì đệ quy tiếp. Điều này **thay đổi contract**: client phải hiểu chỗ nào là object, chỗ nào là id. Object id cần tồn tại và nhất quán. Deserialization một reference id không có định nghĩa object tương ứng có thể đòi resolver riêng, không tự truy vấn database.

Nếu dùng `@JsonIdentityReference(alwaysAsId = true)` thì cần đặc biệt kiểm tra phía client có đủ dữ liệu và có thể khôi phục reference hay không. Đừng chọn identity annotation chỉ vì muốn hết exception mà chưa biết dạng JSON đích.

### 6.5. Chọn cách nào?

| Cách | Hiệu ứng | Phù hợp khi | Điểm cần tránh |
| --- | --- | --- | --- |
| DTO hữu hạn | Chỉ xuất dữ liệu đã chọn | API public, hợp đồng ổn định | Mapper vẫn phải truy vấn hiệu quả |
| `@JsonIgnore` | Loại một property | Field thực sự không cần xuất | Có thể làm mất field ở endpoint khác |
| Managed/back | Serialize một chiều parent → child | Quan hệ cha-con rõ | Không phù hợp mọi graph |
| `@JsonIdentityInfo` | Thay object lặp bằng id | Client hiểu identity references | Contract phức tạp, cần id resolver |
| Đổi hết sang EAGER | Không cắt vòng | Không phải giải pháp | N+1/payload phình to |

**Lưu ý JPA/Hibernate:** vòng quan hệ, lỗi lazy initialization và truy vấn N+1 là các vấn đề riêng nhưng thường xuất hiện cùng khi trả entity trực tiếp. Đừng chữa tất cả bằng cách tăng fetch eager, bật Open Session in View hoặc nâng giới hạn JSON nesting. Thiết kế service load đúng dữ liệu và map sang DTO.

## 7. Validation và lỗi JSON cần phân biệt

Ví dụ request DTO:

~~~java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

public record CreateBookRequest(
        @NotBlank String title,
        @NotNull @Min(1) Integer pages) {}
~~~

Handler có `@Valid`:

~~~java
@PostMapping("/books")
public ResponseEntity<BookResponse> create(
        @Valid @RequestBody CreateBookRequest request) {
    // Service xử lý và tạo response...
}
~~~

Đoạn handler minh họa chữ ký; tham khảo mục 9 để có code chạy đầy đủ.

| Input | Loại lỗi/kết quả thường gặp |
| --- | --- |
| `{"title":"Java","pages":200}` | JSON parse và validation OK |
| `{"title":"","pages":200}` | `@NotBlank` không đạt → HTTP 400 |
| `{"title":"Java","pages":0}` | `@Min(1)` không đạt → HTTP 400 |
| `{"title":"Java","pages":null}` | `@NotNull` không đạt → HTTP 400 |
| `{"title":"Java","pages":"abc"}` | Lỗi Jackson mapping String → Integer → HTTP 400 |
| `{"title":` | JSON cú pháp hỏng → HTTP 400 |
| Body không phải JSON, handler `consumes=application/json` | HTTP 415 |
| Accept không phù hợp converter/produces | HTTP 406 |

Bean Validation cần provider, trong Spring Boot dùng `spring-boot-starter-validation`. JSON parse lỗi thường được Spring MVC bọc qua `HttpMessageNotReadableException`, còn validation failure cho `@Valid @RequestBody` trong cấu hình phổ biến qua `MethodArgumentNotValidException`. Loại exception còn tùy cấu hình method validation; không hardcode nội dung lỗi framework như contract.

Nếu muốn response lỗi thống nhất, dùng `@RestControllerAdvice` với `@ExceptionHandler` và DTO lỗi đã định nghĩa. Validation không thay thế authorization, giới hạn request size, giới hạn collection length hoặc bảo vệ tài nguyên.

## 8. Phân biệt Jackson JSON với form binding

| Client gửi | Annotation thường chọn | Công cụ |
| --- | --- | --- |
| JSON body `{"title":"Java"}` | `@RequestBody CreateBookRequest` | Jackson HTTP converter |
| Query string `?page=2` | `@RequestParam int page` | ConversionService/Formatter |
| HTML form `title=Java&pages=200` | `@ModelAttribute CreateBookForm` | WebDataBinder + Spring conversion |
| URL `/books/7` | `@PathVariable long id` | Spring conversion |
| Response Java DTO → JSON | `@ResponseBody` hoặc `@RestController` | JSON message converter |

`@DateTimeFormat` chủ yếu cấu hình Spring formatting với parameters/form; `@JsonFormat` điều chỉnh Jackson JSON. Cùng một `LocalDate` nhưng hai đường binding có thể được cấu hình riêng. Không giả định multipart/form hoặc form-url-encoded được parse như JSON chỉ vì controller có DTO.

## 9. Ví dụ thực hành hoàn chỉnh: Book REST API

Dự án Maven, Java 17, Spring Boot 3.5.0. Dữ liệu lưu trong memory để tập trung vào JSON. Controller có request DTO riêng, response DTO riêng và generic response wrapper. Domain entity có `internalNote` nhưng client không bao giờ thấy nó.

Cấu trúc:

~~~text
json-dto-demo/
├── pom.xml
└── src/
    ├── main/java/demo/JsonDtoApplication.java
    └── test/java/demo/BookControllerTest.java
~~~

### 9.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>json-dto-demo</artifactId>
    <version>1.0.0</version>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
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

### 9.2. JsonDtoApplication.java

Đặt toàn bộ mã dưới đây trong file `src/main/java/demo/JsonDtoApplication.java`. Java cho phép nhiều package-private class/record trong cùng một file nhưng chỉ có một top-level public class trùng tên file.

~~~java
package demo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class JsonDtoApplication {
    public static void main(String[] args) {
        SpringApplication.run(JsonDtoApplication.class, args);
    }
}

// Client chỉ được truyền title và pages; id do server tạo.
record CreateBookRequest(
        @NotBlank String title,
        @NotNull @Min(1) Integer pages) {
}

// API response không có thông tin nội bộ.
record BookResponse(long id, String title, int pages) {
    static BookResponse from(BookEntity book) {
        return new BookResponse(book.id(), book.title(), book.pages());
    }
}

// Wrapper generic: T có thể là BookResponse hoặc List<BookResponse>.
record ApiResponse<T>(String status, T data) {
}

// Domain object không được trả trực tiếp qua HTTP.
record BookEntity(long id, String title, int pages, String internalNote) {
}

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
class BookController {
    private final Map<Long, BookEntity> books = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(2);

    BookController() {
        books.put(1L, new BookEntity(1, "Java Basics", 200, "private-a"));
        books.put(2L, new BookEntity(2, "Spring MVC", 240, "private-b"));
    }

    @GetMapping("/books")
    public ResponseEntity<ApiResponse<List<BookResponse>>> list() {
        List<BookResponse> data = books.values().stream()
                .sorted(Comparator.comparingLong(BookEntity::id))
                .map(BookResponse::from)
                .toList();
        return ResponseEntity.ok(new ApiResponse<>("ok", data));
    }

    @GetMapping("/books/{id}")
    public ResponseEntity<ApiResponse<BookResponse>> one(
            @PathVariable long id) {
        BookEntity book = books.get(id);
        if (book == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                new ApiResponse<>("ok", BookResponse.from(book)));
    }

    @PostMapping(path = "/books",
                 consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<BookResponse>> create(
            @Valid @RequestBody CreateBookRequest request) {
        long id = ids.incrementAndGet();
        BookEntity book = new BookEntity(
                id, request.title(), request.pages(), "server-only");
        books.put(id, book);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("ok", BookResponse.from(book)));
    }
}
~~~

Điểm chính của code:

- `@RestController` kích hoạt serialization cho response.
- `@RequestBody` dùng Jackson để tạo `CreateBookRequest`; `@Valid` yêu cầu Bean Validation.
- Response method trả `ResponseEntity<ApiResponse<List<BookResponse>>>` hoặc `ResponseEntity<ApiResponse<BookResponse>>` với generic type rõ ràng.
- Chỉ `BookResponse` được chuyển thành JSON; `internalNote` trong `BookEntity` không có trong API payload.
- `ConcurrentHashMap` và `AtomicLong` giúp demo hoạt động khi có request đồng thời. Data chỉ tồn tại trong memory, mất sau restart; đây không phải ví dụ persistence production.
- GET list sắp xếp theo id để ví dụ và test có thứ tự mảng xác định.

### 9.3. Chạy và kiểm tra HTTP

Tại thư mục có `pom.xml`:

~~~bash
mvn spring-boot:run
~~~

GET list:

~~~bash
curl -i 'http://localhost:8080/api/books' \
  -H 'Accept: application/json'
~~~

HTTP **200 OK**, body tương đương:

~~~json
{
  "status": "ok",
  "data": [
    {"id":1,"title":"Java Basics","pages":200},
    {"id":2,"title":"Spring MVC","pages":240}
  ]
}
~~~

POST tạo book:

~~~bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  --data '{"title":"Modern Java","pages":310}'
~~~

Với POST đầu tiên trên instance vừa khởi động, trả **201 Created**, body tương đương:

~~~json
{
  "status": "ok",
  "data": {"id":3,"title":"Modern Java","pages":310}
}
~~~

Lần POST tiếp theo có id lớn hơn. Không có `internalNote` trong body. GET `/api/books/1` trả DTO với id 1, còn `/api/books/999` (khi chưa có id này) trả 404 với body trống theo handler.

Thử sai validation:

~~~bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  --data '{"title":"","pages":0}'
~~~

HTTP thường là **400 Bad Request** do `@NotBlank` và `@Min(1)`. Body thông báo lỗi mặc định không cố định, nên không dự đoán nguyên văn. Nếu gửi `{"title":"X","pages":"abc"}`, lỗi xảy ra trước validation ở bước deserialize Integer.

Ví dụ shell trên dùng cách nối dòng `\` của Bash. Trên Windows PowerShell, có thể dùng `curl.exe` trên một dòng hoặc ký tự nối dòng PowerShell phù hợp; alias `curl` có thể không phải chương trình curl thực.

### 9.4. Test HTTP contract và nested generic

Tạo file `src/test/java/demo/BookControllerTest.java`:

~~~java
package demo;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void getListHasOnlyDtoFields() throws Exception {
        mvc.perform(get("/api/books").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.data[0].title").value("Java Basics"))
            .andExpect(jsonPath("$.data[0].internalNote").doesNotExist());
    }

    @Test
    void createReturns201AndHidesInternalData() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Modern Java","pages":310}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("Modern Java"))
            .andExpect(jsonPath("$.data.pages").value(310))
            .andExpect(jsonPath("$.data.internalNote").doesNotExist());
    }

    @Test
    void invalidDtoReturns400() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"","pages":0}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void readNestedGenericWithoutRawMap() throws Exception {
        String json = """
                {"status":"ok",
                 "data":[{"id":1,"title":"Java Basics","pages":200}]}
                """;
        ApiResponse<List<BookResponse>> decoded = mapper.readValue(
                json, new TypeReference<ApiResponse<List<BookResponse>>>() {});
        assertEquals("ok", decoded.status());
        assertEquals(BookResponse.class, decoded.data().get(0).getClass());
        assertEquals("Java Basics", decoded.data().get(0).title());
    }
}
~~~

Chạy:

~~~bash
mvn test
~~~

Test HTTP kiểm tra status và JSONPath thay vì thứ tự property. Test generic khẳng định Jackson tạo được `BookResponse` thay vì Map. Các test dùng context chung nên test POST có thể thêm book mới; GET list vẫn luôn có hai book seeded với id 1 và id 2 ở đầu khi sắp xếp theo id.

## 10. JSON cycle với JPA/Hibernate: xử lý ở tầng nào?

Mối quan hệ JPA:

~~~java
// Minh họa hình dạng relationship, chưa phải entity chạy độc lập.
@Entity
class Department {
    @OneToMany(mappedBy = "department")
    List<Employee> employees;
}

@Entity
class Employee {
    @ManyToOne(fetch = FetchType.LAZY)
    Department department;
}
~~~

Cần chú ý bốn rủi ro riêng:

1. **Graph cycle:** Department → employees → department → … nếu serialize quan hệ hai chiều.
2. **LazyInitializationException:** Jackson truy cập association khi persistence context đã đóng.
3. **N+1 queries:** mỗi nested getter có thể kéo thêm truy vấn.
4. **Data exposure:** entity lộ field nội bộ.

Đổi tất cả sang `FetchType.EAGER` không giải quyết cycle và có thể tăng tải. Đặt `@JsonIgnore` bừa trên entity có thể làm thay đổi API của nhiều endpoint. Thiết kế query/projection lấy đúng field, chuyển sang DTO trong phạm vi dữ liệu còn dùng được và trả DTO. Cấu hình Hibernate module cho Jackson (nếu có) không thay thế thiết kế JSON contract.

## 11. Lỗi phổ biến và cách chẩn đoán

| Dấu hiệu | Khả năng cao | Hướng xử lý |
| --- | --- | --- |
| `LinkedHashMap cannot be cast to BookResponse` | Đọc JSON generic bằng raw `List.class` | `TypeReference`/`JavaType` |
| 400 do JSON parse | JSON không hợp lệ/field sai kiểu | Kiểm tra body, Content-Type và root cause |
| 400 do validation | Parse xong nhưng constraint sai | `@Valid`, constraint, handler errors |
| 415 | Sai Content-Type | Kiểm tra `consumes` |
| 406 | Sai Accept/produces/converter | Kiểm tra `Accept` và `produces` |
| 500 khi serialize graph | Vòng object hoặc getter lỗi | Response DTO hữu hạn, xem stacktrace |
| LazyInitializationException | Đụng JPA lazy relation ngoài context | Mapping/projection trong service |
| JSON lộ private field | Trả entity hoặc DTO chứa field ngoài ý muốn | Dùng response DTO allowlist |
| JSON ngày sai format | Jackson date config | `@JsonFormat` / JavaTimeModule |
| Test JSON thất bại vì key order | So sánh chuỗi JSON trực tiếp | JSONPath/parse tree |

Nhiều lỗi có cùng HTTP status nhưng xuất phát từ bước khác nhau. Khi chẩn đoán, xác định request có vào handler chưa, lỗi nằm ở parse/validation/service/serialization, rồi mới sửa đúng tầng.

Một số anti-pattern khác:

- Không dùng `Map<String,Object>` cho mọi request nếu cấu trúc đã biết; khó validate và mất type safety.
- Không coi `@JsonIgnore` hay DTO là giải pháp thay cho phân quyền resource.
- Không tăng max JSON depth để che vòng object do API serialize trực tiếp entity.
- Không dùng cách deserialize polymorphic class name từ JSON không tin cậy bằng default typing mở rộng; cần allowlist/discriminator rõ.
- Không nhét `ResponseEntity` vào property DTO rồi hy vọng Jackson tự hiểu HTTP headers.
- Không tự đoán unknown-field handling; ghi rõ policy và kiểm thử.
- Không ép `LocalDateTime` thành một UTC instant khi JSON không có offset/zone.
- Không tự giả định field null bị lược bỏ trừ khi có cấu hình/annotation tương ứng.

## 12. Bài tập tự luyện

**Bài 1 — DTO và bảo mật:** Entity `Customer` có `id, displayName, email, passwordHash, role`. Tạo `CustomerCreateRequest` chỉ nhận `displayName,email` và `CustomerResponse` chỉ trả `id,displayName`. Nêu nơi kiểm tra quyền role.

**Bài 2 — Generic:** Cho `{"status":"ok","data":[{"id":1,"title":"Java","pages":200}]}`. Viết `TypeReference<ApiResponse<List<BookResponse>>>` và dự đoán kiểu `data.get(0)` nếu chỉ dùng `ApiResponse.class`.

**Bài 3 — Cycle:** Dựng sơ đồ `Department ↔ Employee`. Thiết kế `DepartmentResponse` chứa `List<EmployeeSummary>`, trong `EmployeeSummary` không còn `department.employees`. Vì sao chặn được recursion?

**Bài 4 — Annotation:** So sánh `@JsonIgnore`, managed/back references và identity. Với endpoint GET Team có users nhưng user không cần chứa Team, chọn cách hợp lý; với API công khai lâu dài, cân nhắc DTO.

**Bài 5 — Validation:** Dự đoán bước thất bại cho `pages=0`, `pages=null` và `pages="abc"`. Vì sao validator không được gọi cho tất cả trường hợp?

**Bài 6 — HTTP:** Phân biệt 400 parse, 400 validation, 415, 406 và server-side serialization error. Thêm test vào dự án mục 9.

**Bài 7 — Compatibility:** Client cũ chỉ biết `id,title`; client mới muốn thêm `pages,language`. Đề xuất cách mở rộng response DTO, versioning và unknown-field policy.

## 13. Tài liệu tham khảo

- [Spring Framework 6.2 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html).
- [Spring Framework 6.2 — @RequestBody](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/requestbody.html).
- [Spring Framework 6.2 — @ResponseBody](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/responsebody.html).
- [Jackson 2 — ObjectMapper Javadoc](https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/2.18.4/com/fasterxml/jackson/databind/ObjectMapper.html).
- [Jackson 2 — TypeReference Javadoc](https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-core/2.18.4/com/fasterxml/jackson/core/type/TypeReference.html).
- [Jackson 2 — JsonManagedReference](https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-annotations/2.18.4/com/fasterxml/jackson/annotation/JsonManagedReference.html), [JsonBackReference](https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-annotations/2.18.4/com/fasterxml/jackson/annotation/JsonBackReference.html), [JsonIdentityInfo](https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-annotations/2.18.4/com/fasterxml/jackson/annotation/JsonIdentityInfo.html).



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-message-bodies-005">Jackson và cấu hình ánh xạ dữ liệu phù hợp với phiên bản Spring</a>

<details>
<summary>Xem chi tiết</summary>

# Jackson và cấu hình ánh xạ dữ liệu phù hợp với phiên bản Spring

## 1. Tại sao chủ đề này quan trọng?

Trong một API Spring MVC, controller thường nhận dữ liệu JSON bằng `@RequestBody` và trả JSON thông qua `@ResponseBody`, `@RestController` hoặc `ResponseEntity`. Spring MVC không tự biến JSON thành Java object bằng cách so từng key với từng field của controller. Nó ủy quyền việc đọc/ghi **HTTP message body** cho một `HttpMessageConverter`, thường là converter tích hợp **Jackson**.

Jackson giải quyết ánh xạ (*data mapping*) giữa biểu diễn JSON và object Java: tên trường, constructor, record, ngày giờ, enum, null, quan hệ object lồng nhau, serializer/deserializer riêng. Spring thêm lớp tích hợp giúp chọn converter theo HTTP `Content-Type`/`Accept` và cấu hình mapper thông qua bean, properties hoặc cấu hình MVC.

Điều cần đặc biệt lưu ý khi nâng cấp: **Spring Framework 6.x / Spring Boot 3.x chủ yếu dùng Jackson 2**, còn **Spring Framework 7.x / Spring Boot 4.x mặc định chuyển sang Jackson 3**. Không thể bê nguyên imports, builder customizer và mọi property cũ sang phiên bản mới.

Bài tập trung vào **Spring MVC Servlet stack**, các mốc phiên bản, cơ chế truyền JSON, lựa chọn cấu hình và cách bảo đảm hợp đồng API bằng kiểm thử. Spring WebFlux dùng codec/reactive stack khác; không áp dụng máy móc toàn bộ `HttpMessageConverter` của MVC cho WebFlux.

## 2. Chuỗi xử lý JSON từ request đến response

Giả sử client gửi:

```http
POST /api/books HTTP/1.1
Content-Type: application/json
Accept: application/json

{"book_title":"Spring MVC","published_on":"2026-10-01"}
```

Luồng cơ bản trong Spring MVC:

```text
HTTP request
     |
DispatcherServlet chọn @PostMapping("/api/books")
     |
@RequestBody BookRequest
     |
RequestResponseBodyMethodProcessor
     |
HttpMessageConverter chọn theo media type + kiểu Java
     |
Jackson JSON converter -> Jackson mapper đọc JSON
     |
BookRequest Java object -> controller
     |
BookResponse hoặc ResponseEntity<BookResponse>
     |
Jackson mapper ghi JSON
     |
HTTP response: Content-Type: application/json
```

Các thành phần có vai trò khác nhau:

- `@RequestBody` yêu cầu giải mã request body thành kiểu Java của tham số. Thường dùng cho JSON API.
- `@ResponseBody` đánh dấu giá trị trả về để ghi trực tiếp vào response body. `@RestController` có ý nghĩa tương đương `@Controller` kết hợp `@ResponseBody` ở cấp class.
- `ResponseEntity<T>` cho phép chủ động status, headers, body; nó không tự chọn format JSON.
- `HttpMessageConverter` quyết định khả năng đọc/ghi theo media type và Java type. Spring MVC có nhiều converter cho string, byte array, JSON, XML... tùy classpath/cấu hình.
- **Jackson mapper** đọc token JSON, khởi tạo object, chuyển dữ liệu, áp dụng annotation/custom serializer; chiều ngược lại viết object thành JSON.

Khác biệt cần phân biệt:

| Thành phần | Chức năng | Ví dụ |
| --- | --- | --- |
| HTTP `Content-Type` | Định dạng của **body yêu cầu** | `application/json` |
| HTTP `Accept` | Định dạng **phản hồi mà client chấp nhận** | `application/json` |
| Jackson JSON mapper | Quy tắc JSON ↔ Java | `@JsonProperty("book_title")` |
| Bean Validation | Kiểm tra ràng buộc sau khi đọc DTO | `@Valid`, `@NotBlank` |
| Service/business logic | Kiểm tra dữ liệu theo trạng thái hệ thống | Mã sách đã tồn tại chưa? |
| Database mapping | Chuyển Java entity ↔ dữ liệu lưu trữ | JPA/Hibernate ORM |

**Jackson khác Hibernate ORM và khác Hibernate Validator**. Không dùng Jackson annotation thay cho authorization, kiểm tra nghiệp vụ hay constraint database.

## 3. Ma trận phiên bản Spring, Jackson và API cấu hình

| Nền tảng thường gặp | Thế hệ Jackson mặc định/ưu tiên | Kiểu mapper | JSON converter trong MVC | Hướng tùy chỉnh thường dùng |
| --- | --- | --- | --- | --- |
| Spring Framework 5.x + Boot 2.x | Jackson 2 | `com.fasterxml.jackson.databind.ObjectMapper` | `MappingJackson2HttpMessageConverter` | `spring.jackson.*`, `Jackson2ObjectMapperBuilderCustomizer`; nhiều API Jakarta vẫn ở `javax.*` |
| Spring Framework 6.0–6.2 + Boot 3.0–3.5 | Jackson 2 | `com.fasterxml.jackson.databind.ObjectMapper` | `MappingJackson2HttpMessageConverter` | `spring.jackson.*`, `Jackson2ObjectMapperBuilderCustomizer`, Jackson 2 modules |
| Spring Framework 7.0+ + Boot 4.0+ | Jackson 3 **ưu tiên mặc định** | `tools.jackson.databind.json.JsonMapper` | `JacksonJsonHttpMessageConverter` | `spring.jackson.*` theo schema Boot 4, `JsonMapperBuilderCustomizer`, Jackson 3 modules |
| Boot 4.x trong giai đoạn chuyển đổi | Có thể bật hỗ trợ Jackson 2 deprecated | `com.fasterxml.jackson.databind.ObjectMapper` cho đường legacy | Converter Jackson 2 nếu được cài/cấu hình thích hợp | Module `spring-boot-jackson2`, namespace cấu hình `spring.jackson2.*` |

Phiên bản tương thích **do BOM và dependency tree thực tế quyết định**; các khoảng trong bảng là thế hệ phát hành thông dụng, không bảo đảm mọi project không override phụ thuộc đều có đúng bản đó.

Những mốc cần nhớ:

1. **Boot 3 / Spring 6:** mã Java JSON điển hình import `com.fasterxml.jackson.databind.ObjectMapper`; customizer là `org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer`.
2. **Boot 4 / Spring 7:** chuyển phần lớn dependency group/package sang `tools.jackson`; mapper được Spring Boot cấu hình thường là `JsonMapper`, và customizer là `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`.
3. **Ngoại lệ package:** Jackson annotation phổ biến như `@JsonProperty`, `@JsonAlias`, `@JsonInclude`, `@JsonTypeInfo` vẫn thuộc `com.fasterxml.jackson.annotation`. Không tự đổi chúng thành `tools.jackson.annotation`.
4. `MappingJackson2HttpMessageConverter` và `Jackson2ObjectMapperBuilder` là cách tích hợp Jackson 2; **không lấy làm cấu hình mặc định cho hệ thống Jackson 3 mới**. Spring Framework 7 vẫn có hỗ trợ Jackson 2 deprecated như bước đệm nâng cấp.
5. Spring Boot 4 có `spring.jackson.use-jackson2-defaults=true` để kéo cấu hình mặc định Jackson 3 gần với hành vi Jackson 2 trong Boot 3; đây là **chế độ tương thích defaults**, không có nghĩa framework đã quay lại sử dụng thư viện Jackson 2.
6. Trong Boot 4, nếu chủ động giữ Jackson 2, dùng cấu hình `spring.jackson2.*` với module tương thích deprecated. Các property mới cho JSON reader/writer như `spring.jackson.json.read.*`, `spring.jackson.json.write.*` cần được kiểm tra theo tài liệu Boot 4 khi nâng cấp.
7. Một số API của Spring MVC dành cho **cấu hình danh sách converter** cũng đổi giữa Framework 6.2 và 7.0; kiểm tra đúng overload của `WebMvcConfigurer` trước khi sao chép ví dụ từ tài liệu.

**Quy tắc nâng cấp:** phân loại đoạn code là Jackson annotations, Jackson databind/parser, Spring MVC converter hay Spring Boot auto-configuration rồi đối chiếu **đúng major version của từng lớp**. Đừng coi thay một import là đủ.

## 4. Jackson annotation nào giải quyết bài toán nào?

Những annotation sau có tác dụng khi mapper tương ứng được kích hoạt qua Spring MVC hoặc gọi mapper trực tiếp.

| Annotation | Công dụng | Lưu ý |
| --- | --- | --- |
| `@JsonProperty("external_name")` | Đặt tên thuộc tính trong JSON độc lập với tên Java | Có tác động đến serialization/deserialization tùy nơi đặt |
| `@JsonAlias({"oldName"})` | Chấp nhận tên khác **khi đọc JSON** | Khi ghi response vẫn dùng tên chính, không tự phát ra mọi alias |
| `@JsonIgnore` | Bỏ qua một thuộc tính khỏi mapping theo ngữ cảnh annotation | Không thay cho phân quyền hoặc chiến lược DTO |
| `@JsonIgnoreProperties(ignoreUnknown=true)` | Bỏ qua JSON key không biết trên kiểu được chú thích | Có thể che giấu client gửi field sai chính tả |
| `@JsonInclude(Include.NON_NULL)` | Không ghi property có Java value `null` | Khác với `NON_EMPTY`, `NON_DEFAULT`; không làm input bắt buộc |
| `@JsonFormat(pattern="yyyy-MM-dd")` | Định dạng ngày khi đọc/ghi nếu kiểu và module hỗ trợ | Test timezone, locale và format theo contract |
| `@JsonPropertyOrder` | Điều khiển thứ tự field được ghi | Không nên lấy thứ tự JSON key làm contract trừ khi có yêu cầu thật |
| `@JsonCreator` | Chọn constructor/factory phù hợp để tạo object | Hữu ích với immutable types/constructors đặc biệt |
| `@JsonValue` | Giá trị đại diện khi serialize enum/value object | Cần xem tác động lên deserialization tương ứng |
| `@JsonView` | Chọn nhóm thuộc tính theo view trên các endpoint có cấu hình view | Không phải authorization layer; kiểm thử default inclusion |
| `@JsonTypeInfo` | Ghi/đọc thông tin polymorphic type | Cực kỳ thận trọng với type id và các subtype được phép |

Ví dụ Java records:

```java
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

record CreateAuthorRequest(
        @JsonProperty("full_name")
        @JsonAlias({"name", "authorName"})
        String fullName) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record AuthorResponse(
        @JsonProperty("full_name") String fullName,
        String middleName) {
}
```

Với request JSON `{"authorName":"An"}`, Jackson có thể bind thành `CreateAuthorRequest("An")`. Khi trả `new AuthorResponse("An", null)`, response là object có `"full_name":"An"` và **không có `middleName`** nếu mapper dùng đúng annotation. `@JsonAlias` dùng cho backward-compatible input; nó không sinh `"authorName"` trong response.

**Đừng mặc định rằng chỉ cần gắn `@JsonIgnore` lên password của một entity là đủ bảo vệ API.** Có thể tồn tại endpoint khác serialize entity bằng kiểu/view/mixin khác. DTO response chỉ chứa trường được phép công bố là lựa chọn rõ ràng hơn.

## 5. Ví dụ hoàn chỉnh cho Spring Boot 3.x / Spring Framework 6.x

Phần này là một mini REST API có thể chạy trên Java 17+ trong dự án Spring Boot **3.x** sử dụng Spring MVC và Jackson **2**. Không dùng đoạn này như project Boot 4 chưa được chuyển đổi: phụ thuộc, imports và mapping defaults đã khác.

### 5.1. Dependency

Ví dụ `build.gradle` **fragment** khi dự án đã có plugin Spring Boot, dependency management, repositories và Java toolchain:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

`spring-boot-starter-web` trong dòng Boot 3 thường kéo `spring-boot-starter-json` và Jackson 2. Để dùng `LocalDate`, Jackson Java Time hỗ trợ qua module JSR-310 đi cùng starter được Boot cấu hình. Không tự tạo một `ObjectMapper` trần bỏ qua module mặc định.

### 5.2. Source runnable

Tạo **`src/main/java/demo/jackson/JacksonDemoApplication.java`**:

```java
package demo.jackson;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class JacksonDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(JacksonDemoApplication.class, args);
    }
}

record BookRequest(
        @JsonProperty("book_title") String title,
        @JsonProperty("published_on")
        @JsonAlias({"publication_date"})
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate publishedOn,
        String note) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record BookResponse(
        String id,
        @JsonProperty("book_title") String title,
        @JsonProperty("published_on")
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate publishedOn,
        String note) {
}

@RestController
@RequestMapping("/api/books")
class BookController {

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BookResponse> create(@RequestBody BookRequest body) {
        BookResponse result = new BookResponse(
                "bk-1", body.title(), body.publishedOn(), body.note());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping(
            path = "/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    BookResponse get(@PathVariable String id) {
        return new BookResponse(
                id, "Spring MVC", LocalDate.of(2026, 10, 1), null);
    }
}
```

Đây là **một tệp Java biên dịch hợp lệ khi có classpath Boot 3**, vì chỉ class `JacksonDemoApplication` là `public`; record/controller còn lại package-private. Đưa vào package `demo.jackson` để component scan của `@SpringBootApplication` nhận controller.

Controller này cố ý không dùng database hoặc validation. Nó cho thấy rõ ánh xạ JSON:

- `@JsonProperty("book_title")` giữ contract JSON ổn định dù Java accessor là `title()`.
- `@JsonAlias("publication_date")` chấp nhận tên cũ của input, trong khi response giữ key `published_on`.
- `LocalDate` được đọc/ghi ở dạng ISO ngày theo `@JsonFormat(pattern = "yyyy-MM-dd")`; input sai ngày có thể gây lỗi convert.
- `@JsonInclude(NON_NULL)` áp dụng cho `BookResponse`, nên khi `note=null` response **bỏ hẳn trường note**, không trả `"note":null`.
- `POST` trả 201, `GET` trả 200. Hành vi này không liên quan đến việc persist thực sự; ID `bk-1` là giá trị demo.

### 5.3. Chạy và quan sát HTTP

Khởi động ứng dụng qua wrapper Gradle của dự án:

```bash
./gradlew bootRun
```

Yêu cầu POST sử dụng tên JSON chính:

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"book_title":"Spring MVC","published_on":"2026-10-01"}'
```

Kết quả thông thường:

```http
HTTP/1.1 201
Content-Type: application/json

{"id":"bk-1","book_title":"Spring MVC","published_on":"2026-10-01"}
```

Header thực tế như charset, content length, date có thể khác theo server. **Thứ tự key trong JSON không thuộc contract** của ví dụ. Nội dung JSON trên thể hiện tập thuộc tính mong đợi.

Yêu cầu với alias cũ:

```bash
curl -sS -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -d '{"book_title":"Spring MVC","publication_date":"2026-10-01"}'
```

Response vẫn trả key chuẩn `published_on`, không trả `publication_date`:

```json
{"id":"bk-1","book_title":"Spring MVC","published_on":"2026-10-01"}
```

Yêu cầu GET:

```bash
curl -sS 'http://localhost:8080/api/books/bk-9'
```

Body dự kiến:

```json
{"id":"bk-9","book_title":"Spring MVC","published_on":"2026-10-01"}
```

Lệnh curl trong phần này giả định dự án chạy cổng 8080 và không có filter/security/proxy thay đổi giao thức. Nếu thêm request validation, error handling hoặc JSON config, kết quả cần được kiểm thử lại.

## 6. Tùy chỉnh Jackson trong Spring Boot 3

Có ba mức lựa chọn: **annotation trên DTO**, **properties của Boot**, và **bean customizer**. Nên bắt đầu ở mức nhỏ nhất đáp ứng yêu cầu, tránh thay thế toàn bộ auto-configuration một cách không cần thiết.

### 6.1. Cấu hình bằng application properties

Trong Boot 3, ví dụ:

```properties
# Chỉ áp dụng cho mapper được Boot auto-configure trong dòng Boot 3.
spring.jackson.default-property-inclusion=non_null
spring.jackson.deserialization.fail-on-unknown-properties=true
spring.jackson.serialization.indent-output=false
spring.jackson.property-naming-strategy=SNAKE_CASE
```

Các thuộc tính minh họa này là **lựa chọn tùy chỉnh**; không tự thêm tất cả vào ứng dụng mẫu, vì đổi naming strategy toàn cục có thể tác động tới những DTO khác. Nếu đã gắn `@JsonProperty` tường minh, tên ngoài của các field đó nên được kiểm thử riêng khi phối hợp global naming strategy.

- `default-property-inclusion=non_null` bỏ properties null trong response của những đối tượng dùng cấu hình chung; `@JsonInclude` trên class/field có thể ghi đè tại phạm vi hẹp.
- `deserialization.fail-on-unknown-properties=true` làm JSON chứa key lạ gây lỗi thay vì lặng lẽ bỏ qua, trừ khi type có quy tắc riêng như `@JsonIgnoreProperties(ignoreUnknown=true)`.
- `serialization.indent-output` điều khiển pretty printing; nội dung JSON có thêm khoảng trắng nhưng không đổi mô hình dữ liệu.
- `property-naming-strategy=SNAKE_CASE` chuyển ví dụ Java `publishedOn` sang JSON `published_on` khi không có tên tường minh khác.

**Chính sách unknown fields là quyết định hợp đồng API**: strict giúp bắt lỗi gõ sai; permissive giúp client/server tiến hóa ít ma sát hơn. Không có một thiết lập luôn tối ưu. Cần kết hợp validation vì một field sai chính tả có thể trở thành `null` và validation sẽ kiểm tra độ bắt buộc nếu được kích hoạt.

### 6.2. Tùy chỉnh builder trong Boot 3

Tạo `src/main/java/demo/jackson/Jackson2Config.java`:

```java
package demo.jackson;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class Jackson2Config {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer prettyJson() {
        return builder -> builder.indentOutput(true);
    }
}
```

Phần này chạy với API Boot 3; nó đặt indent output cho mapper do builder Boot tạo. Nếu cùng lúc khai báo property `spring.jackson.serialization.indent-output=false`, **thứ tự cấu hình của customizer** quyết định kết quả cuối; đừng để hai nguồn thiết lập trái nhau. Khi cần ổn định, chọn một nguồn cấu hình và viết test xác minh.

Với logic serializer/deserializer phức tạp, cân nhắc **Jackson `Module`** để đăng ký serializer cho một kiểu cụ thể. Đừng tạo `new ObjectMapper()` ngẫu nhiên ở mọi controller/service: các mapper đó có thể thiếu Java Time, naming policy, inclusion policy và custom modules đang được Boot quản lý.

### 6.3. Khi nào cần trực tiếp `ObjectMapper`?

Trong service hoặc batch job nội bộ của Boot 3:

```java
import com.fasterxml.jackson.databind.ObjectMapper;

class JsonExportService {
    private final ObjectMapper mapper;

    JsonExportService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String toJson(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }
}
```

Đây là class Java minh họa DI; không phải controller độc lập. Inject mapper đã cấu hình từ Spring giúp quy tắc JSON nhất quán với MVC. Tuy nhiên, cần hiểu rằng converter thực tế có thể dùng mapper riêng nếu ai đó thay đổi cấu hình `HttpMessageConverters`; vì vậy **không giả định bean `ObjectMapper` được inject chắc chắn là mapper đang xử lý HTTP** khi ứng dụng có custom converter.

Jackson 2 `ObjectMapper` có thể cấu hình trước khi bắt đầu sử dụng; khi được chia sẻ trong ứng dụng, tránh sửa cấu hình runtime đồng thời với serialization từ nhiều thread. Cách an toàn là cấu hình một lần, sau đó reuse mapper hoặc reader/writer chuyên dụng.

## 7. Cấu hình message converter: thứ tự, media type và tác động của Spring Boot

Ở Spring MVC, `RequestMappingHandlerAdapter` có danh sách `HttpMessageConverter` để xử lý body. Không phải một request có thể được đọc bởi **bất kỳ** converter nào; Spring xét content type, target type và converter support.

Ví dụ API chấp nhận và trả JSON:

```java
@PostMapping(
    path = "/payload",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE)
public Payload handle(@RequestBody Payload payload) {
    return payload;
}
```

Đây là method minh họa trong `@RestController`, `Payload` và `MediaType` cần định nghĩa/import. Với một converter phù hợp:

- `Content-Type: application/json` cùng JSON hợp lệ → deserialization chạy.
- `Content-Type: text/plain` với body JSON có thể dẫn đến **415 Unsupported Media Type** vì `consumes` và converter.
- `Accept: application/xml` khi chỉ có JSON được công bố/có thể ghi có thể dẫn đến **406 Not Acceptable**.
- JSON malformed hoặc không ánh xạ được kiểu Java có thể gây `HttpMessageNotReadableException` và thường là **400 Bad Request**.
- JSON đọc thành object thành công nhưng vi phạm `@Valid`/`@Validated` là bước validation riêng; exception thường khác lỗi parse.

Trong **Framework 6.2**, `WebMvcConfigurer#configureMessageConverters(List<HttpMessageConverter<?>>)` dùng để cấu hình danh sách tùy biến (thay mặc định tùy cơ chế); `extendMessageConverters(List<HttpMessageConverter<?>>)` thích hợp khi chỉ muốn chỉnh converter đã đăng ký. Boot cũng bổ sung các converter beans do auto-configuration phát hiện. Cần chọn cách cấu hình thống nhất, kiểm thử tất cả media types thật sự được hỗ trợ.

Trong **Framework 7.x**, tài liệu chính thức trình bày cấu hình thông qua `configureMessageConverters(HttpMessageConverters.ServerBuilder)` và các converter Jackson 3. Ví dụ Framework 7 không nên copy nguyên chữ ký `List<HttpMessageConverter<?>>` từ dự án 6.2 nếu API đã đổi.

Không được tự động áp đặt converter JSON toàn cục cho mọi endpoint XML, form, byte array hoặc file download. Thứ tự converter có thể ảnh hưởng content negotiation và khả năng đọc/ghi.

## 8. Spring Boot 4 và Spring Framework 7: dùng Jackson 3

### 8.1. Package và dependency thay đổi

Khác biệt điển hình:

```text
Boot 3 / Jackson 2
  implementation 'org.springframework.boot:spring-boot-starter-web'
  com.fasterxml.jackson.databind.ObjectMapper
  MappingJackson2HttpMessageConverter
  Jackson2ObjectMapperBuilderCustomizer

Boot 4 / Jackson 3
  Spring MVC web starter theo BOM/starter Boot 4
  tools.jackson.databind.json.JsonMapper
  JacksonJsonHttpMessageConverter
  JsonMapperBuilderCustomizer
```

Đây là **bảng gợi nhớ**, không phải đoạn Gradle copy nguyên. Spring Boot 4 tách module/starter theo thế hệ mới; kiểm tra dependency hiện có qua chính BOM/guide của Boot 4 trước khi bổ sung các starter riêng.

Đối với Jackson 3, core/databind modules nằm dưới Maven group `tools.jackson` (ví dụ `tools.jackson.core:jackson-databind`). **Jackson annotations vẫn từ `com.fasterxml.jackson.annotation`**. Những loại thuộc Jackson databind như mapper, module, serializer/deserializer có thể đổi package hoặc tên API. Đừng sửa hàng loạt tất cả `com.fasterxml.jackson.*` bằng tìm–thay một cách cơ học.

### 8.2. Java config kiểu Jackson 3 của Boot 4

Trong project **Boot 4 / Jackson 3** đã có Spring MVC và auto-configuration, có thể tạo file **`src/main/java/demo/jackson/Jackson3Config.java`**:

```java
package demo.jackson;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.SerializationFeature;

@Configuration
class Jackson3Config {

    @Bean
    JsonMapperBuilderCustomizer prettyJson() {
        return builder -> builder.enable(
                SerializationFeature.INDENT_OUTPUT);
    }
}
```

Đây là ví dụ **dành riêng Boot 4**; không biên dịch trong Boot 3 vì `JsonMapperBuilderCustomizer` và `tools.jackson.databind.SerializationFeature` của Jackson 3 không có sẵn ở baseline cũ. Spring Boot sẽ áp dụng customizer lên builder mapper được auto-configure.

Trong một dự án Framework 7 sử dụng cấu hình MVC trực tiếp, đường JSON converter chuẩn là `JacksonJsonHttpMessageConverter` với `JsonMapper`. Ví dụ minh họa độc lập về mapper:

```java
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;

JsonMapper mapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();
```

Đây chỉ là **fragment Jackson 3** có import/biến theo ngữ cảnh. Mapper này chưa tự đăng ký vào Spring MVC; khi viết Boot application, ưu tiên builder/customizer hoặc converter mechanism do Boot cung cấp.

### 8.3. Cấu hình properties trong Boot 4

Ví dụ dùng `application.properties` cho **Jackson 3 ở Boot 4**:

```properties
spring.jackson.serialization.indent-output=true
spring.jackson.use-jackson2-defaults=false
spring.jackson.find-and-add-modules=true
```

Trong Boot 4, các feature JSON reader/writer chuyên biệt được nhóm dưới `spring.jackson.json.read.*` và `spring.jackson.json.write.*`. Không sao chép `spring.jackson.read.*` hoặc `spring.jackson.write.*` cũ mà không kiểm tra loại feature, tên property và mapping tương đương. Một số tùy chọn không còn ánh xạ 1:1 và cần customizer.

Đối với migration từng bước:

```properties
# Tạm căn chỉnh defaults của Jackson 3 với các defaults mà Boot 3 dùng
# cho Jackson 2; KHÔNG chuyển runtime quay lại Jackson 2.
spring.jackson.use-jackson2-defaults=true
```

Chỉ đặt cờ này khi có bằng chứng hợp đồng JSON của ứng dụng phụ thuộc defaults cũ và bạn cần giai đoạn chuyển đổi. Mục tiêu cuối là có **tests mô tả contract JSON tường minh** để không phụ thuộc quá nhiều vào defaults của framework.

### 8.4. Khi cần Jackson 2 trong Boot 4

Spring Boot 4 cung cấp đường chuyển tiếp deprecated cho Jackson 2:

```groovy
// Chỉ trong dự án Boot 4 đang cần tương thích Jackson 2 tạm thời.
dependencies {
    implementation 'org.springframework.boot:spring-boot-jackson2'
}
```

Các setting của đường Jackson 2 được đặt trong namespace `spring.jackson2.*`, **không tự trộn** với mapper Jackson 3 `spring.jackson.*`. Bản thân việc thêm module compatibility không chứng minh rằng HTTP converter đang dùng mapper Jackson 2; cần xem classpath, auto-configuration, converter được chọn và test ở endpoint.

Nếu code có API dependency yêu cầu Jackson 2 ở một module, cân nhắc giữ mapper Jackson 2 **cục bộ ở ranh giới module** trong khi HTTP JSON của ứng dụng tiếp tục dùng Jackson 3; hai thế hệ có thể tồn tại đồng thời nhưng quy tắc ánh xạ không được phép mơ hồ. Hỗ trợ legacy là bước đệm có kế hoạch loại bỏ.

## 9. Đổi cấu hình của Jackson 2 sang Jackson 3 mà không phá JSON contract

Trước migration, lập **một bộ JSON contract samples** gồm input/output và status code đã được client sử dụng. Chạy chúng trên hệ thống hiện tại rồi mới nâng thư viện.

| Mối quan tâm | Boot 3 / Jackson 2 | Boot 4 / Jackson 3 |
| --- | --- | --- |
| Mapper runtime | `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.json.JsonMapper` |
| Spring Boot customizer | `Jackson2ObjectMapperBuilderCustomizer` | `JsonMapperBuilderCustomizer` |
| Spring converter | `MappingJackson2HttpMessageConverter` | `JacksonJsonHttpMessageConverter` |
| Module/serializer custom | Jackson 2 `Module`, `JsonSerializer`, `JsonDeserializer` | Jackson 3 module/serializer API; xem thay đổi tên/signature |
| Cấu hình core properties | `spring.jackson.*` theo Boot 3 | `spring.jackson.*` theo Boot 4, một số nhánh property đổi tên |
| JSON reader/writer feature settings | Nhánh `spring.jackson.read.*`/`write.*` ở các bản cũ hỗ trợ | `spring.jackson.json.read.*`/`json.write.*` cho các JSON feature tương ứng |
| Giữ hành vi mặc định gần bản cũ | Defaults do Boot 3 đặt | `spring.jackson.use-jackson2-defaults=true` (chỉ là hỗ trợ migration) |
| Tạm giữ Jackson 2 khi lên Boot 4 | Sẵn có ở baseline | `spring-boot-jackson2` deprecated và `spring.jackson2.*` |
| Jackson annotations chung | `com.fasterxml.jackson.annotation.*` | Vẫn `com.fasterxml.jackson.annotation.*` đối với annotations chung |

Kiểm thử hồi quy tối thiểu nên bao gồm:

1. **Tên field:** `camelCase`, `snake_case`, `@JsonProperty` và alias nhận vào từ client cũ.
2. **Null / field vắng:** response bỏ null hay trả null? Input thiếu field tương ứng có được chấp nhận không? Test riêng sự khác nhau giữa “vắng”, “null” và “giá trị rỗng”.
3. **Ngày giờ:** `LocalDate`, `LocalDateTime`, `OffsetDateTime` và timezone; ngày ISO, timestamp epoch, milliseconds precision. Không áp một format date-time cho mọi loại.
4. **Enum:** tên enum, viết hoa/thường, giá trị không nhận diện, giá trị JSON tùy chỉnh bằng `@JsonCreator`/`@JsonValue`.
5. **Number:** `long` lớn, `BigDecimal`, số thực, rounding/scale khi có ý nghĩa nghiệp vụ; tránh chuyển ID lớn sang `double` ở client JavaScript.
6. **Unknown fields:** có từ chối unknown field không? Có alias/backward compatibility không? Xác nhận lỗi HTTP.
7. **Object nested/collections:** cách xử lý generics, null elements, immutable constructor/record.
8. **Polymorphic data:** whitelist type hợp lệ; không cho payload tự chọn Java class tùy ý.
9. **Error response:** malformed JSON, mismatched types, unsupported media type, validation errors và cấu trúc `ProblemDetail`/API error contract.
10. **Mixed dependencies:** xác nhận chỉ có mapper/converter nào xử lý một endpoint và không vô tình bỏ module hỗ trợ Java Time.

Migration an toàn dựa trên **tests về kết quả**. Chỉ assert `ObjectMapper` tồn tại trong context là chưa chứng minh Spring MVC đang dùng đúng mapper ấy.

## 10. Một số vấn đề ánh xạ hay gây lỗi

### 10.1. Ngày giờ với `java.time`

Trong Boot 3, `spring-boot-starter-json` thường cung cấp và Boot đăng ký module Java Time. Nếu bạn tạo một `new ObjectMapper()` riêng, `LocalDate` có thể không được đọc/ghi như kỳ vọng do thiếu `JavaTimeModule` hoặc cấu hình timestamp. Ví dụ Jackson 2 thuần, **không nhất thiết cần trong Boot 3**:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
```

Đây là **fragment** yêu cầu dependency `jackson-datatype-jsr310` tương ứng. Trong Boot application, tốt hơn inject mapper đã được auto-configure để không mất các quy tắc chung. Trong Jackson 3, hệ sinh thái module và mặc định thay đổi; không chép imports `com.fasterxml.jackson.datatype.jsr310` sang code 3.x một cách mặc định.

Phân biệt:

- `LocalDate` biểu diễn ngày **không có giờ, timezone**; pattern `yyyy-MM-dd` phù hợp cho ngày lịch.
- `LocalDateTime` có ngày và giờ **nhưng không có offset/timezone**; không đủ để xác định thời điểm tuyệt đối.
- `OffsetDateTime` và `Instant` đại diện điểm thời gian có ngữ nghĩa offset/UTC; lựa chọn format ISO 8601 và chính sách chuyển timezone rõ ràng.
- Thay đổi global timezone không tự làm mọi kiểu `java.time` có cùng semantics. Viết test với offset khác nhau.

### 10.2. Cấu trúc object và record

Jackson có thể dùng default constructor + setters, constructor được đánh dấu `@JsonCreator`, record canonical constructor và các cơ chế introspection khác tùy phiên bản. Với Java 17 record, tên record components là một phần của hợp đồng cấu trúc trong Java; `@JsonProperty` giúp cố định tên JSON.

Nếu DTO chỉ có constructor nhiều tham số và mapper không tìm được cách tạo object, lỗi thường có thông tin “no Creators” hoặc “cannot construct instance”. Cách xử lý: dùng DTO/record phù hợp, khai báo `@JsonCreator`/`@JsonProperty` khi cần, hoặc tạo deserializer cụ thể. Tránh bật “field visibility ANY” toàn cục chỉ để sửa một DTO, vì có thể làm lộ thêm fields của kiểu khác.

### 10.3. Không biết field trong input

Hai chính sách:

```java
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Chấp nhận field lạ cho đúng kiểu DTO này:
@JsonIgnoreProperties(ignoreUnknown = true)
record FlexibleRequest(String name) {}
```

Hoặc, với Boot 3 muốn strict **toàn cục**:

```properties
spring.jackson.deserialization.fail-on-unknown-properties=true
```

Không đặt đồng thời hai lựa chọn trái nhau trên cùng DTO mà không hiểu precedence. Nếu client gửi `{"nmae":"An"}` thay vì `{"name":"An"}`, strict mapping sẽ bắt lỗi sớm; permissive mapping có thể tạo object với `name=null`. Bean Validation (nếu có và được kích hoạt) cần kiểm tra riêng tên bắt buộc.

### 10.4. Serialization không có nghĩa là validation

`@JsonProperty(required=true)` không phải công tắc thay thế mọi ràng buộc `@NotNull`/`@NotBlank`. Hành vi của `required` phụ thuộc nơi khai báo property/creator và Jackson version; không sử dụng nó làm lớp kiểm tra dữ liệu nghiệp vụ duy nhất.

Với API:

```java
@PostMapping
public ResponseEntity<Void> create(
        @Valid @RequestBody CreateRequest body) {
    return ResponseEntity.status(201).build();
}
```

Đây là **method fragment**: cần import `jakarta.validation.Valid` và khai báo `CreateRequest` với constraints. Quy trình là Jackson **deserialize** trước; khi có object, Bean Validation mới kiểm tra ràng buộc. Lỗi body không parse được và lỗi constraint không hợp lệ là **hai nhóm lỗi khác nhau**, nên handler cần xử lý khác nhau.

### 10.5. Không nên serialize JPA Entity trực tiếp

Entity có thể có lazy proxies, quan hệ hai chiều, field nội bộ, dữ liệu nhạy cảm hoặc vòng tham chiếu. Jackson tự serialize graph quá rộng có thể gây recursion, extra SQL queries/N+1 hoặc vô tình lộ thông tin. DTO response giúp giới hạn contract:

```java
record UserView(long id, String displayName) {}
```

Không xem `@JsonIgnore` trên entity là kiến trúc API đầy đủ; ở boundary HTTP, convert entity sang DTO rồi serialize DTO thường dễ kiểm soát hơn.

## 11. Cách Spring MVC chọn mapper và các lỗi HTTP

| Hiện tượng | Đường gây lỗi điển hình | Điều cần kiểm tra |
| --- | --- | --- |
| `415 Unsupported Media Type` | `Content-Type`/`consumes` không tương thích | Client gửi JSON với `application/json` hay text/plain? |
| `406 Not Acceptable` | Không có converter/media type đáp ứng `Accept` | `produces`, converter và Accept header |
| `400 Bad Request` với JSON sai cú pháp | Jackson không deserialize được | JSON malformed, kiểu field, enum/date format |
| `400 Bad Request` sau khi DTO được đọc | Validation không đạt | `@Valid`/`@Validated`, `BindingResult` và error handler |
| `500 Internal Server Error` khi ghi JSON | Serialization failed lúc trả response | Vòng tham chiếu, lazy-loading, serializer, cấu hình module |
| Response field biến mất | `@JsonIgnore` hoặc inclusion policy | `@JsonInclude`, MixIn, `JsonView`, null/empty |
| Response tên field khác trước | Naming strategy / annotation / migration | `@JsonProperty`, `SNAKE_CASE`, schema contract |
| JSON date thành timestamp/array | Module/format/defaults khác | `JavaTimeModule`, mapper, `@JsonFormat`, Jackson generation |
| Chỉ một endpoint có JSON khác | Converter/mapper hoặc view riêng | Converter registry, custom `ObjectMapper`, annotation scope |

**Status trên chỉ là đường đi thông dụng**. `@ControllerAdvice`, Spring Security, proxy, filter hoặc custom exception resolver có thể thay đổi kết quả cuối. Chỉ nhìn HTTP 400 không xác định được JSON parse hay validation đã hỏng.

Ví dụ REST exception handler (đặt trong project Boot 3 mẫu, chỉ xử lý malformed JSON):

```java
package demo.jackson;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class JsonErrorAdvice {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> malformedJson(
            HttpMessageNotReadableException ex) {

        // Không gửi ex.getMostSpecificCause().getMessage() cho client.
        return ResponseEntity.badRequest()
                .body(Map.of("code", "INVALID_JSON"));
    }
}
```

Đây là class hoàn chỉnh nhưng cần Spring context của ứng dụng. Nếu bật advice này, test JSON không hợp lệ có thể assert `{"code":"INVALID_JSON"}`. Trong hệ thống production, cân nhắc `ProblemDetail` và chiến lược lỗi thống nhất để client phân biệt invalid JSON, invalid field, unauthorized và conflict.

## 12. Kiểm thử ánh xạ JSON với MockMvc

Sau khi thêm starter test như phần 5, tạo **`src/test/java/demo/jackson/JacksonDemoApplicationTests.java`**:

```java
package demo.jackson;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JacksonDemoApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void readsCanonicalNamesAndOmitsNullInResponse() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("""
                    {"book_title":"Spring MVC",
                     "published_on":"2026-10-01"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("bk-1"))
            .andExpect(jsonPath("$.book_title").value("Spring MVC"))
            .andExpect(jsonPath("$.published_on").value("2026-10-01"))
            .andExpect(jsonPath("$.note").doesNotExist());
    }

    @Test
    void acceptsAliasButWritesCanonicalName() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"book_title":"Spring MVC",
                     "publication_date":"2026-10-01"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.published_on").value("2026-10-01"))
            .andExpect(jsonPath("$.publication_date").doesNotExist());
    }

    @Test
    void returnsKnownJsonPropertyNames() throws Exception {
        mvc.perform(get("/api/books/bk-9")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("bk-9"))
            .andExpect(jsonPath("$.book_title").value("Spring MVC"))
            .andExpect(jsonPath("$.published_on").value("2026-10-01"))
            .andExpect(jsonPath("$.note").doesNotExist());
    }

    @Test
    void rejectsMalformedDate() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"book_title":"Spring MVC",
                     "published_on":"not-a-date"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonJsonContentType() throws Exception {
        mvc.perform(post("/api/books")
                .contentType(MediaType.TEXT_PLAIN)
                .content("""
                    {"book_title":"Spring MVC"}
                    """))
            .andExpect(status().isUnsupportedMediaType());
    }
}
```

Chạy:

```bash
./gradlew test
```

Kỳ vọng năm method test **pass** trên dự án Boot 3 có JSON converter mặc định và không có Security hoặc error customization thay đổi status. Các kiểm tra dùng JSONPath nên không phụ thuộc thứ tự field/whitespace.

Nếu muốn kiểm tra strict unknown-field, thêm property `spring.jackson.deserialization.fail-on-unknown-properties=true` vào profile test, rồi gửi body chứa `"unexpected_field":1` và assert 400. Nếu muốn kiểm tra permissive mapping, tạo DTO riêng có `@JsonIgnoreProperties(ignoreUnknown=true)` và test response. Đừng dựa vào một default Jackson không được kiểm soát khi nâng major version.

### Test chính mapper so với test HTTP

| Loại test | Bao phủ | Không bao phủ |
| --- | --- | --- |
| Test `ObjectMapper` trực tiếp trong Boot 3 | Serialization/deserialization, annotation, module | MVC chọn converter, Content-Type, Accept, controller mapping |
| Test `JsonMapper` trực tiếp trong Boot 4 | Jackson 3 mapping rules | Converter/Boot MVC integration |
| `@JsonTest` (Spring Boot test slice) | Cấu hình JSON slice và component mapping | Toàn bộ MVC filter chain/endpoints |
| `@WebMvcTest` + `MockMvc` | MVC controllers, converter, selected web infrastructure | Full application/service/database nếu không thêm |
| `@SpringBootTest` + `@AutoConfigureMockMvc` | Web application context và request/response handling | Reverse proxy, browser, network transport thật |

Để xác nhận nâng cấp Boot 3 → Boot 4, kết hợp mapper-level và HTTP contract tests. Một unit test serializing một DTO bằng mapper tự tạo có thể **pass** nhưng production controller lại dùng mapper/converter khác.

## 13. Những rủi ro khi tùy biến sâu

### Thay `ObjectMapper` bean

Trong Boot 3, nếu tự khai báo bean `ObjectMapper` thay bean được Boot auto-configure, hãy chắc chắn đăng ký module và policies quan trọng (Java Time, Java Optional, parameter names, inclusion/naming, custom serializers). Boot không thể mặc nhiên áp dụng mọi điều chỉnh của bạn lên mapper đã tạo thủ công. Tương tự, Boot 4 có auto-configured `JsonMapper`/builder riêng; đừng đưa Jackson 2 bean và mong mọi converter Jackson 3 sử dụng nó.

### Polymorphic deserialization

Tránh bật **default typing quá rộng** cho payload JSON đến từ client không đáng tin, đặc biệt kiểu metadata cho phép chọn subtype bằng Java class name. Nếu cần đa hình, khai báo tập subtype hợp lệ và dùng `@JsonTypeInfo`/`@JsonSubTypes` một cách giới hạn, kết hợp validation/authorization. Review CVE và cập nhật Jackson/Spring theo bản vá phù hợp.

### Date and decimal fidelity

Chọn kiểu Java phù hợp dữ liệu:

- Số tiền: `BigDecimal` thay vì `double` nếu cần tính toán thập phân chính xác và scale/rounding có kiểm soát.
- ID 64-bit: kiểm thử với client JavaScript để tránh mất precision khi JSON number vượt giới hạn an toàn của JS; nếu cần, thiết kế contract ID dạng string.
- Thời điểm: `Instant`/`OffsetDateTime` với format ISO và timezone rõ ràng; không gửi `LocalDateTime` rồi bắt client tự đoán múi giờ.
- Enum và name casing: quy định chặt chẽ contract ở boundary, kiểm thử giá trị lạ và migration.

### Tách request DTO và response DTO

DTO dành cho input có thể cho phép alias, nhưng output nên dùng tên canonical. Không tái sử dụng entity đa mục đích nếu field bên trong có thể bị serialize ra ngoài. Đừng coi `@JsonView` hay `@JsonIgnore` là điểm kiểm soát quyền truy cập duy nhất.

## 14. Quy trình nâng cấp Jackson và Spring có kiểm chứng

1. **Chốt phiên bản thật:** kiểm tra `spring-webmvc`, Spring Boot BOM, `jackson-databind`, các Jackson modules và dependency tree. Với Gradle, dùng `./gradlew dependencies --configuration runtimeClasspath` và `dependencyInsight` cho dependency quan trọng.
2. **Chốt HTTP JSON contract:** lưu mẫu request/response của endpoint quan trọng; định nghĩa status, Content-Type, Accept, tên field, null, date, enum và unknown-field policy.
3. **Kiểm kê custom code:** tìm `ObjectMapper`, `JsonMapper`, `Jackson2ObjectMapperBuilderCustomizer`, converter custom, serializer/deserializer, mixin, Java Time, `@JsonProperty`, `@JsonView` và `spring.jackson.*`.
4. **Chọn đường migration:** lên Boot 4 và Jackson 3 là hướng ưu tiên; nếu module không tương thích, dùng Jackson 2 compatibility có thời hạn với phạm vi rõ ràng.
5. **Chuyển imports/API đúng loại:** phần lớn databind/parser sang `tools.jackson`; giữ annotation imports `com.fasterxml.jackson.annotation`; đổi Spring customizer/converter cho đúng phiên bản.
6. **Kiểm tra cấu hình properties:** xem các property reader/writer được đổi nhánh; quyết định có tạm bật `use-jackson2-defaults` không; bỏ các customizer không còn tác dụng.
7. **Chạy unit và integration tests:** xác minh Jackson mapper trực tiếp **và** pipeline HTTP thông qua MVC với cả positive/negative cases.
8. **Kiểm tra lỗi, bảo mật và observability:** không leak rejected input nhạy cảm; kiểm tra content negotiation, 400/406/415/500, monitoring và logging sau deploy.
9. **Gỡ compatibility:** khi hợp đồng đã ổn định và mọi thư viện liên quan hỗ trợ Jackson 3, loại bỏ đường Jackson 2 deprecated và tests chỉ phục vụ workaround.

Một mốc nâng cấp coi là đạt khi client quan trọng nhận **cùng hợp đồng dữ liệu** hoặc một thay đổi API versioned đã được công bố, không phải chỉ khi build xanh.

## 15. Bài tập tự kiểm tra

1. Controller Boot 3 khai báo `@PostMapping(consumes="application/json")` với `@RequestBody BookRequest`. Client gửi `Content-Type: text/plain` nhưng body là JSON hợp lệ: bước nào từ chối và HTTP status dự kiến là gì?
2. Một record Java có accessor `bookTitle()` nhưng API phải trả key `book_title`. Hãy so sánh `@JsonProperty` trên field/record component với global `SNAKE_CASE`. Cách nào phù hợp nếu chỉ một DTO có tên đặc biệt?
3. Từ input `{"publication_date":"2026-10-01"}` sang `BookRequest` và response `BookResponse`, hãy giải thích tại sao `@JsonAlias` chấp nhận tên input cũ nhưng output luôn dùng `published_on`.
4. Client gửi ngày `2026-02-30`; vì sao có thể lỗi **deserialization** trước khi Bean Validation chạy? Kiểm tra test bằng `MockMvc`.
5. Sau nâng cấp Boot 3 → Boot 4, code import `com.fasterxml.jackson.databind.ObjectMapper` và customizer Jackson 2 không tác động HTTP response mới. Hãy chỉ ra thay đổi Jackson 3 và cách xác nhận converter thực tế.
6. Khi đặt `spring.jackson.use-jackson2-defaults=true` trong Boot 4, ứng dụng đã thật sự chạy Jackson 2 chưa? Kiểm tra khái niệm **defaults compatibility** với **library/runtime generation**.
7. Một endpoint serialize entity có quan hệ hai chiều và field `passwordHash`. Hãy đề xuất response DTO và test đảm bảo JSON không lộ field nội bộ.
8. Lập ma trận contract tests cho enum, `BigDecimal`, null/vắng field, unknown JSON property và `OffsetDateTime` ở Boot 3/4. Ghi rõ hành vi muốn giữ và thay đổi được chủ động chấp nhận.
9. Viết một customizer tương đương ở Boot 3 (Jackson2ObjectMapperBuilderCustomizer) và Boot 4 (JsonMapperBuilderCustomizer), rồi chứng minh trong test HTTP đầu ra được ảnh hưởng.

## 16. Tài liệu chính thức để đối chiếu theo phiên bản

- [Spring Framework 6.2 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html): `MappingJackson2HttpMessageConverter`, Jackson 2 `ObjectMapper`.
- [Spring Framework 6.2 — Message Converters Configuration](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/message-converters.html): `WebMvcConfigurer`, converter list cho dòng Framework 6.
- [Spring Framework 7 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html): converter Jackson 3 sử dụng `JsonMapper`.
- [Spring Framework 7 — MVC Converter Configuration](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/message-converters.html): `HttpMessageConverters.ServerBuilder` và converter Jackson 3.
- [Spring MVC — `@RequestBody`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html): resolver, message converter và validation.
- [Spring Boot 3 — JSON](https://docs.spring.io/spring-boot/3.4/reference/features/json.html): auto-configure Jackson 2 và custom serializer/deserializer.
- [Spring Boot 4 — JSON](https://docs.spring.io/spring-boot/reference/features/json.html): Jackson 3, `JsonMapper` và cấu hình mới.
- [Spring Boot 4 — MVC Configuration](https://docs.spring.io/spring-boot/how-to/spring-mvc.html): customizer/configuration properties Jackson 3.
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide): thay đổi Jackson 3, package, customizers, Jackson 2 compatibility và properties.
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes): Jackson 3 mặc định ưu tiên và Jackson 2 deprecated.
- [Introducing Jackson 3 support in Spring — Spring Blog](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/): mục đích migration, compatibility defaults và ví dụ customizer Boot 3/4.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-message-bodies-006">Thương lượng nội dung theo Accept và lựa chọn media type</a>

<details>
<summary>Xem chi tiết</summary>

# Thương lượng nội dung theo Accept và lựa chọn media type trong Spring MVC

## 1. Mục tiêu và ngữ cảnh

Một tài nguyên HTTP có thể có **nhiều cách biểu diễn (representations)**: JSON cho ứng dụng, văn bản thuần cho công cụ dòng lệnh, HTML cho trình duyệt, XML cho hệ thống tích hợp. **Content negotiation** là quá trình xác định representation nào phù hợp với khả năng và ưu tiên mà client nêu ra, khả năng của server, mapping controller và bộ chuyển đổi nội dung.

Trong Spring MVC (Servlet stack), bài này giúp bạn:

- Đọc chính xác header `Accept`, media type, wildcard, trọng số `q` và quan hệ với `Content-Type`.
- Biết `ContentNegotiationManager` xác định media type client mong muốn ra sao; hiểu mặc định hiện đại và các chiến lược tùy chỉnh.
- Giải thích `@GetMapping(produces=...)`, `@RequestMapping(produces=...)`, `@ResponseBody`/`@RestController` và `HttpMessageConverter` cùng tham gia chọn representation.
- Biết khi nào xảy ra `406 Not Acceptable`, phân biệt với `415 Unsupported Media Type` và các lỗi khác.
- Thiết kế endpoint hỗ trợ một hay nhiều representation, kiểm tra bằng `curl`/MockMvc, lưu ý cache `Vary: Accept`, versioning và bảo mật.

Ví dụ dùng **Java 17+ với Spring Framework 6.2/Spring Boot 3.x** (và dependency Spring MVC + Jackson); không áp các giả định của Spring WebFlux. Các đoạn lệnh/tests nêu hành vi kỳ vọng với ứng dụng mẫu, không khẳng định output/body lỗi HTTP cố định trên mọi server hoặc cấu hình bảo mật.

## 2. Phân biệt resource, representation và media type

**Resource** là đối tượng được xác định bởi URI, chẳng hạn `/api/media/items/7`. Cùng resource có thể được trình bày thành những representation khác nhau, ví dụ:

```http
GET /api/media/items/7 HTTP/1.1
Host: localhost:8080
Accept: application/json
```

Server có thể trả:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":7,"name":"Pen"}
```

Trong khi client khác yêu cầu văn bản:

```http
GET /api/media/items/7 HTTP/1.1
Host: localhost:8080
Accept: text/plain
```

Server có thể trả:

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

Item 7: Pen
```

Đây là **một URL, hai representation**, với điều kiện server thật sự hỗ trợ cả hai. Header `Content-Type` ở response xác nhận **kiểu dữ liệu thực được gửi**, còn `Accept` ở request nêu **định dạng client muốn nhận**. Giá trị `charset` và một số header khác phụ thuộc converter/cấu hình; các mẫu HTTP ở đây minh họa, không cam kết byte-for-byte.

Media type có cú pháp `type/subtype`, có thể mang parameters:

- `application/json`: JSON.
- `text/plain`: văn bản thuần.
- `text/html`: HTML.
- `application/xml`: XML.
- `application/problem+json`: dạng Problem Details theo JSON.
- `application/vnd.example.v1+json`: kiểu JSON đặc thù ứng dụng (vendor media type).
- `text/plain;charset=UTF-8`: loại nội dung có tham số charset.

**Không phải cứ trả về object Java thì server tự tạo được XML/CSV/HTML.** Phải có cơ chế serialize representation đó, được cấu hình và thỏa điều kiện media type.

## 3. Ngữ nghĩa của Accept

`Accept` cho server biết những media type mà client chấp nhận trong **response**. Đây là header cho *response representation*, không mô tả request body. Dạng tổng quát:

```http
Accept: application/json, text/plain;q=0.6, */*;q=0.1
```

Theo HTTP Semantics (RFC 9110):

- **Thiếu `Accept`:** client không hạn chế server vào một danh sách media type cụ thể; server có thể chọn representation khả dụng. HTTP không quy định “bắt buộc phải là JSON”.
- **`Accept: */*`:** cho phép bất kỳ media type nào. Không nói client *ưu tiên* JSON.
- **`Accept: application/*`:** cho phép các subtype trong nhóm `application` (chẳng hạn `application/json`), nếu server hỗ trợ.
- **`Accept: application/json`:** chỉ chỉ rõ JSON; nếu server chỉ có `text/plain`, phía Spring MVC thường dẫn đến HTTP 406.
- **`Accept: application/json, text/plain`:** cả hai có trọng số mặc định `q=1`; chỉ riêng header này không ấn định representation được chọn khi hai bản đều đáp ứng.
- **`q`:** trọng số ưu tiên nằm trong khoảng `0` đến `1`; nếu bỏ qua thì mặc định `1`; `q=0` nghĩa là representation đó không được chấp nhận.
- **Wildcard:** `*/*` bao quát mọi loại, `text/*` bao quát subtype thuộc nhóm text. Media range càng cụ thể thường có độ ưu tiên tương ứng khi xác định q cho media type cụ thể.

Ví dụ:

```http
Accept: application/json;q=0.9, text/plain;q=0.4
```

Nếu JSON và text đều có thể phục vụ, JSON được **ưu tiên hơn theo q**. Đổi q thành `application/json;q=0.2, text/plain;q=0.9` thể hiện ưu tiên text. `q` là **trọng số tương đối**, không phải xác suất 90% server trả JSON.

Ví dụ thêm:

```http
Accept: application/json;q=0, text/plain;q=1
```

Theo ngữ nghĩa HTTP, client đã loại trừ JSON và chấp nhận text. Điều này chỉ có nghĩa khi server thật sự có representation text; nếu không có, server có thể trả 406 hoặc theo HTTP specification lựa chọn bỏ qua preference. Trong ứng dụng Spring MVC có `produces` rõ ràng, trường hợp không tương thích thường là 406.

Một tình huống cần tránh suy luận sai:

```http
Accept: application/json;q=0, */*;q=0.8
```

**Wildcard không nhất thiết “cứu” JSON đã bị loại trừ:** media range cụ thể `application/json` có quyền ưu tiên khi xác định q cho chính JSON đó. Việc tính mức chấp nhận dựa trên độ đặc hiệu và q chứ không chỉ lấy giá trị q lớn nhất trong toàn bộ header.

### Bảng đọc nhanh

| Accept | Ý định ở HTTP | Khi endpoint chỉ trả JSON |
| --- | --- | --- |
| Không có header | Không chỉ định hạn chế | Thường JSON, nếu framework chọn được |
| `*/*` | Chấp nhận tất cả | Thường JSON |
| `application/json` | Muốn JSON | JSON (nếu converter ghi được) |
| `application/*` | Chấp nhận họ application | JSON có thể phù hợp |
| `text/plain` | Chỉ muốn text | Thường 406 |
| `application/xml` | Chỉ muốn XML | Thường 406 |
| `application/json;q=0` | Không nhận JSON | Thường 406 nếu không có bản hợp lệ khác |
| `application/json;q=0.2,text/plain;q=0.9` | Ưu tiên text hơn JSON | Ứng dụng chỉ có JSON vẫn có thể trả JSON vì q=0.2 vẫn chấp nhận |

Đây là **mô hình HTTP và kết quả thường gặp**; mapping, codec và interceptor cụ thể mới quyết định status cuối cùng của ứng dụng.

## 4. Accept và Content-Type: hai chiều dữ liệu

Bốn tình huống:

| Header | Hướng | Câu hỏi |
| --- | --- | --- |
| Request `Accept` | Client → Server | “Tôi muốn nhận response ở định dạng nào?” |
| Request `Content-Type` | Client → Server | “Body tôi **đang gửi** ở định dạng nào?” |
| Response `Content-Type` | Server → Client | “Body server **đang trả** có định dạng nào?” |
| Response `Vary` | Server → client/cache | “Biến thể response bị ảnh hưởng bởi header request nào?” |

Một POST có thể đồng thời gửi:

```http
POST /api/media/echo HTTP/1.1
Content-Type: application/json
Accept: application/json

{"id":7,"name":"Pen"}
```

- `Content-Type: application/json` xác định cách server **đọc** body. Trong Spring MVC, `consumes` và `HttpMessageConverter.canRead` liên quan chiều này.
- `Accept: application/json` ảnh hưởng cách server **ghi** response. Trong Spring MVC, `produces` và `HttpMessageConverter.canWrite` liên quan chiều này.
- Nếu body bị gắn nhãn `text/plain` nhưng endpoint chỉ nhận JSON → thường **415 Unsupported Media Type**.
- Nếu body gửi JSON đúng nhưng client yêu cầu XML cho response, endpoint chỉ trả JSON → thường **406 Not Acceptable**.
- Nếu JSON cú pháp sai nhưng `Content-Type` đúng, lỗi thường **400 Bad Request** vì không đọc/deserialize được body. Không nhầm 400 với 406 hay 415.

`Accept-Language` chọn ngôn ngữ, `Accept-Encoding` chọn content coding như gzip, còn `Accept` ở đây chọn **media type**. Các header này là những chiều negotiation khác nhau, không thay thế nhau. CORS `Access-Control-Allow-Headers`/`Access-Control-Allow-Methods` cũng là vấn đề riêng.

## 5. Spring MVC tìm media type và chọn representation thế nào?

Luồng tổng quan, có thể xem như hai quyết định liên quan:

```text
HTTP request: path + method + Accept + (Content-Type nếu có body)
            |
            v
ContentNegotiationManager
  -> xác định requested/acceptable media types
            |
            v
RequestMappingHandlerMapping
  -> chọn handler khớp path, HTTP method, params/headers,
     consumes (request body) và produces (response)
            |
            v
Handler return value: object / String / ResponseEntity / ...
            |
            v
RequestResponseBodyMethodProcessor hoặc HTTP entity processor
  -> so acceptable media types với các kiểu có thể ghi
     bởi HttpMessageConverter tương ứng return type
            |
            v
Chọn response media type + converter -> serialize body
            |
            v
HTTP response: status + Content-Type + body
```

**Đây là sơ đồ logic, không phải một thứ tự cứng cho mọi nhánh nội bộ.** Handler mapping nhận thông tin về `Accept` từ negotiation manager khi đánh giá điều kiện `produces`; khi chuẩn bị ghi response, Spring tiếp tục dùng thông tin acceptable/producible media types cùng tập converter. Một handler có thể match về path/method nhưng sau đó vẫn không ghi được kiểu body như mong muốn.

### 5.1. Mặc định content negotiation trong Spring MVC hiện đại

Với Spring MVC 6.x không tùy chỉnh, **header `Accept` là nguồn được kiểm tra theo mặc định** để xác định requested media types. Đừng sao chép mô hình Spring MVC 4.x cũ, nơi path extension như `.json` từng được ưu tiên trong cấu hình lịch sử.

Trong ứng dụng 6.x phổ biến:

- `GET /api/items/7` và `GET /api/items/7.json` **không mặc nhiên là cùng một route**. Việc match suffix/path extension phụ thuộc cấu hình routing và không phải cơ chế mặc định nên dựa vào.
- Query `?format=json` **không mặc nhiên điều khiển** content negotiation. Muốn dùng cần cấu hình strategy rõ ràng.
- Không có `Accept` hoặc client gửi `*/*`: Spring có thể chọn một kiểu server hỗ trợ, nhưng nếu bạn tự khai báo nhiều handler cùng path khác `produces` và không định nghĩa ưu tiên mặc định, **đừng giả định lựa chọn luôn ổn định**. Nên thiết kế representation mặc định tường minh khi hợp đồng API cần nó.
- Bộ lọc, error handler, controller advice, Spring Security và reverse proxy có thể làm status/header quan sát được khác ví dụ.

### 5.2. `@RequestMapping(produces=...)` là điều kiện mapping

```java
@GetMapping(path = "/books/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
public Book book(@PathVariable long id) {
    return new Book(id, "Java");
}
```

`produces` mô tả loại response mà method **cam kết có thể cung cấp** cho điều kiện chọn handler. Header `Accept: application/json` tương thích; `Accept: application/xml` không tương thích khi chỉ có handler JSON.

Ghi chú:

- Method-level `produces` có thể **override** declaration ở class level thay vì “thêm vào” danh sách của class.
- Một method có thể khai báo `produces = {"application/json", "application/xml"}`, **nhưng chỉ khai báo không tạo khả năng xuất XML**. Phải có converter và serializer hỗ trợ cùng kiểu object.
- Dùng `produces` khi API cam kết response media type ổn định, muốn client nhận 406 khi yêu cầu định dạng không hỗ trợ.
- Nếu dùng `@Controller` thường và return `String` là **tên view**, cần `@ResponseBody` để serialize giá trị; với `@RestController`, behavior tương đương `@Controller + @ResponseBody` cho method.
- `produces` thường được xét ngay khi chọn method; converter thực tế được kiểm tra ở giai đoạn ghi response. Hai điểm này nên được debug riêng.

### 5.3. `HttpMessageConverter` quyết định ghi được kiểu nào

Spring MVC ghi body từ `@ResponseBody`/`ResponseEntity` thông qua các converter:

| Converter (khái niệm) | Dữ liệu/định dạng thường ghi |
| --- | --- |
| `MappingJackson2HttpMessageConverter` | Java object → JSON qua Jackson ObjectMapper |
| `StringHttpMessageConverter` | `String` → text; mặc định hỗ trợ các text media type và có thể cấu hình khác |
| `ByteArrayHttpMessageConverter` | `byte[]` → byte stream |
| `MappingJackson2XmlHttpMessageConverter` hoặc converter XML khác | Java object → XML nếu có đúng thư viện và đăng ký |
| `FormHttpMessageConverter` | Form/`MultiValueMap` theo media type được hỗ trợ |

Tên/lựa chọn converter có thể thay đổi với phiên bản và cấu hình Spring; ví dụ `StringHttpMessageConverter` có thể hỗ trợ nhiều media type và lựa chọn converter chịu ảnh hưởng thứ tự. **Không suy ra JSON từ một `String`** chỉ vì client gửi `Accept: application/json`; để có JSON hợp lệ, hãy trả một DTO/record phù hợp và dùng JSON converter.

Nếu không có converter ghi được return type với media type cần thiết, Spring không thể thực hiện content negotiation như mong muốn. Khác biệt giữa “mapping từ chối `produces`” và “converter không ghi được” quan trọng khi đọc log.

### 5.4. Chọn media type là giao của ba yếu tố

Cách suy nghĩ hữu ích:

1. **Client chấp nhận** (`Accept` + q + wildcard).
2. **Handler công bố được cung cấp** (`produces` hoặc khả năng suy ra nếu không khai báo).
3. **Converter thật sự hỗ trợ** Java return type và media type đích.

Một lựa chọn hợp lệ phải thỏa điều kiện của các tầng liên quan. Chỉ thêm `produces="application/xml"` không đủ nếu thiếu XML converter. Chỉ thêm Jackson vào classpath cũng không đảm bảo client muốn XML được phục vụ. Tăng q của một media type không “tạo ra” representation mà server chưa hỗ trợ.

## 6. Ví dụ Java 17+ Spring Boot: một URL, hai representation

Mục tiêu là minh họa hai method xử lý **cùng URL** nhưng khác `produces`. Khi `Accept` rõ ràng, Spring MVC chọn method tương ứng.

Tạo một dự án Maven Spring Boot 3.x, Java 17+ với `spring-boot-starter-web` (thường bao gồm Jackson JSON). Đặt hai tệp sau cùng package `com.example.demo`.

Tệp `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

Tệp `src/main/java/com/example/demo/MediaController.java`:

```java
package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    @GetMapping(path = "/items/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Item itemJson(@PathVariable("id") long id) {
        return new Item(id, "Pen");
    }

    @GetMapping(path = "/items/{id}",
                produces = MediaType.TEXT_PLAIN_VALUE)
    public String itemText(@PathVariable("id") long id) {
        return "Item " + id + ": Pen";
    }

    @GetMapping(path = "/json-only/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Item jsonOnly(@PathVariable("id") long id) {
        return new Item(id, "Pen");
    }

    @PostMapping(path = "/echo",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public Item echo(@RequestBody Item item) {
        return item;
    }

    public record Item(long id, String name) {}
}
```

Điểm quan trọng:

- `GET /api/media/items/7` + `Accept: application/json` → handler `itemJson`, object Java được Jackson serialize JSON.
- Cùng URL + `Accept: text/plain` → handler `itemText`, String được text converter ghi ra.
- `GET /api/media/json-only/7` + `Accept: text/plain` → không có handler representation phù hợp, thường 406.
- `POST /api/media/echo` dùng `consumes` cho body **đến**, `produces` cho body **đi**.
- Handler `itemJson` và `itemText` có cùng URL và GET nhưng khác `produces`, vì vậy đều có thể được đăng ký. **Đừng dựa vào behavior khi `Accept` không nêu ưu tiên rõ ràng**: hai mapping có thể có mức ưu tiên tương đương và kết quả tùy logic so khớp/cấu hình. Một API production thường cần chọn một định dạng mặc định hợp đồng và thiết kế fallback rõ ràng.
- Ví dụ không có database; bất kể `id` nhận gì, resource minh họa đều có tên `Pen`. Trong dịch vụ thật, “không có resource” là 404 nghiệp vụ riêng, không phải lỗi content negotiation.

Chạy trong dự án Maven Boot đã cấu hình:

```bash
./mvnw spring-boot:run
```

Windows PowerShell: `.\mvnw.cmd spring-boot:run`. Boot mặc định dùng cổng 8080 nếu bạn chưa đổi `server.port`. Không thể dùng `javac MediaController.java` độc lập khi không có Spring/Boot/Jackson dependencies trên classpath.

## 7. Thử bằng curl và dự đoán status/Content-Type

Trong ví dụ dưới, `curl -i` hiển thị status và headers. Trên Windows PowerShell (đặc biệt phiên bản cũ), ưu tiên `curl.exe`; dấu `\` nối dòng là cú pháp bash/zsh, có thể viết mỗi command trên một dòng nếu dùng PowerShell.

### 7.1. Yêu cầu JSON

```bash
curl -i -H "Accept: application/json" \
  http://localhost:8080/api/media/items/7
```

Kỳ vọng **200 OK**, `Content-Type` tương thích `application/json`, body:

```json
{"id":7,"name":"Pen"}
```

Không nên assert thứ tự field JSON khi serializer có thể cấu hình lại.

### 7.2. Yêu cầu text/plain

```bash
curl -i -H "Accept: text/plain" \
  http://localhost:8080/api/media/items/7
```

Kỳ vọng **200 OK**, `Content-Type` tương thích `text/plain`, body chính xác:

```text
Item 7: Pen
```

### 7.3. q-value ưu tiên text

```bash
curl -i -H "Accept: application/json;q=0.2, text/plain;q=0.9" \
  http://localhost:8080/api/media/items/7
```

Với hai handler `produces` và cấu hình MVC chuẩn, trọng số của text lớn hơn nên representation văn bản được ưu tiên. Đảo trọng số để quan sát JSON được ưu tiên:

```bash
curl -i -H "Accept: application/json;q=0.9, text/plain;q=0.2" \
  http://localhost:8080/api/media/items/7
```

Không được suy luận rằng q=0.2 “cấm” JSON: bất kỳ q dương nào vẫn biểu diễn mức chấp nhận, dù kém ưu tiên.

### 7.4. Wildcard

```bash
curl -i -H "Accept: text/*" \
  http://localhost:8080/api/media/items/7
```

Trong ví dụ, `text/plain` tương thích `text/*`, do đó client có thể nhận văn bản. Thử `Accept: */*` hoặc bỏ hẳn Accept có thể khớp nhiều representation; nếu thiết kế API cần kết quả chắc chắn, đừng kiểm thử theo một mặc định không được cam kết.

### 7.5. 406 khi không có representation thích hợp

```bash
curl -i -H "Accept: application/xml" \
  http://localhost:8080/api/media/json-only/7
```

Kỳ vọng **406 Not Acceptable**. `GET /api/media/json-only/7` có mapping GET tồn tại, nhưng `produces=application/json` không đáp ứng `application/xml`. Không cần đổi route hay HTTP method. Sửa ở client: nhận JSON; hoặc mở rộng server để thực sự hỗ trợ XML nếu hợp đồng yêu cầu.

Để xem request chỉ có một representation nhưng client ưu tiên format khác chưa hẳn thất bại, thử:

```bash
curl -i -H "Accept: application/json;q=0.2, application/xml;q=0.9" \
  http://localhost:8080/api/media/json-only/7
```

JSON **vẫn được chấp nhận** (q=0.2 > 0), nên endpoint JSON có thể đáp ứng. Server không bắt buộc phải xuất XML chỉ vì XML có q cao hơn nếu không hỗ trợ XML.

### 7.6. 415 là lỗi hướng ngược lại

```bash
curl -i -X POST http://localhost:8080/api/media/echo \
  -H "Content-Type: text/plain" \
  -H "Accept: application/json" \
  --data "hello"
```

Kỳ vọng **415 Unsupported Media Type** vì `consumes=application/json`, trong khi request body được khai báo `text/plain`. Đặt `Accept: application/json` không chữa được 415 vì Accept chỉ nêu format **response**.

POST đúng:

```bash
curl -i http://localhost:8080/api/media/echo \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data '{"id":7,"name":"Pen"}'
```

Kỳ vọng **200 OK** và object JSON giống body gửi lên (demo echo).

POST body JSON hợp lệ nhưng muốn response XML:

```bash
curl -i http://localhost:8080/api/media/echo \
  -H "Content-Type: application/json" \
  -H "Accept: application/xml" \
  --data '{"id":7,"name":"Pen"}'
```

Thường **406** vì endpoint chỉ khai báo JSON output; JSON input vẫn là hợp lệ. Tùy cấu hình, giai đoạn từ chối có thể xảy ra trong request mapping trước khi controller được gọi.

## 8. MockMvc: kiểm tra behavior theo hợp đồng

Tạo `src/test/java/com/example/demo/MediaControllerTest.java` trong dự án có `spring-boot-starter-test`, cùng package:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
class MediaControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void acceptsJsonRepresentation() throws Exception {
        mvc.perform(get("/api/media/items/7")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.name").value("Pen"));
    }

    @Test
    void acceptsPlainTextRepresentation() throws Exception {
        mvc.perform(get("/api/media/items/7")
                .accept(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.TEXT_PLAIN))
            .andExpect(content().string("Item 7: Pen"));
    }

    @Test
    void usesRequestedQualityPreference() throws Exception {
        mvc.perform(get("/api/media/items/7")
                .header("Accept",
                        "text/plain;q=0.9, application/json;q=0.2"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.TEXT_PLAIN));
    }

    @Test
    void acceptsTextWildcard() throws Exception {
        mvc.perform(get("/api/media/items/7")
                .accept(MediaType.parseMediaType("text/*")))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.TEXT_PLAIN));
    }

    @Test
    void returns406ForUnsupportedResponseType() throws Exception {
        mvc.perform(get("/api/media/json-only/7")
                .accept(MediaType.APPLICATION_XML))
            .andExpect(status().isNotAcceptable());
    }

    @Test
    void returns415ForUnsupportedRequestBodyType() throws Exception {
        mvc.perform(post("/api/media/echo")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content("hello"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void handlesValidJsonPost() throws Exception {
        mvc.perform(post("/api/media/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"id\":7,\"name\":\"Pen\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Pen"));
    }
}
```

Chạy:

```bash
./mvnw test
```

PowerShell: `.\mvnw.cmd test`. Đây là **mẫu kiểm thử có thể chạy trong ứng dụng Boot đủ dependency**, không phải tuyên bố một lệnh đã được thực thi. Nếu dự án tích hợp Spring Security, custom advice, custom converter hoặc `@WebMvcTest` được tùy chỉnh, hãy cấu hình test tương ứng trước khi diễn giải status.

Để test sát HTTP contract, hãy kiểm tra **status, Content-Type tương thích, body phù hợp** và khi cần các response header đặc biệt. Không test `Content-Type` bằng một chuỗi cố định chứa charset nếu framework được phép thêm hoặc bỏ tham số charset. Không gắn với thứ tự các field JSON.

## 9. Cấu hình ContentNegotiationManager khi thực sự cần

Spring MVC cung cấp `WebMvcConfigurer.configureContentNegotiation` để cấu hình chiến lược. Mặc định hiện đại thường chỉ dùng `Accept`. Nếu có yêu cầu hỗ trợ `?format=json` hoặc `?format=txt`, bạn có thể bật **query parameter strategy** một cách chủ động.

Ví dụ cấu hình (lớp độc lập trong cùng package ứng dụng, nhưng chỉ áp dụng khi sản phẩm **thật sự muốn** dùng tham số `format`):

```java
package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MediaNegotiationConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(
            ContentNegotiationConfigurer configurer) {
        configurer
            .favorParameter(true)
            .parameterName("format")
            .mediaType("json", MediaType.APPLICATION_JSON)
            .mediaType("txt", MediaType.TEXT_PLAIN);
    }
}
```

Lúc đó `?format=json`/`?format=txt` có thể tác động đến requested media type và mapping tương ứng. **Không chép lớp cấu hình này vào dự án mà vẫn mong các test chỉ kiểm tra Accept có cùng ý nghĩa khi request chứa format parameter**: chiến lược tham số có thể tác động mức ưu tiên nếu được kích hoạt. Hãy tự định nghĩa rõ hợp đồng khi Accept và `format` mâu thuẫn.

Lưu ý:

1. Bật `favorParameter(true)` là quyết định API design, **không phải sửa lỗi “Spring không hiểu Accept”**.
2. Danh sách mapping media type như `json`, `txt` cần được kiểm soát; đừng để client đặt tên extension/media type tùy ý.
3. Khi dùng query parameter, phải xét **cache key** (URI query là một phần target), khác với cache theo Accept header.
4. Khuyến nghị chung là tránh phụ thuộc vào **path extension** như `/users.json` để chọn định dạng trong dự án Spring MVC mới. Suffix routing có lịch sử liên quan parsing/ambiguity và rủi ro reflected file download (RFD).
5. Nếu API chỉ cam kết JSON, cấu hình đơn giản `produces=application/json` thường rõ ràng hơn một loạt chiến lược fallback mơ hồ.

### 9.1. Có nên ép `defaultContentType`?

Spring MVC cho phép cấu hình default content type trong content negotiation. Nhưng chọn default phải theo hợp đồng ứng dụng: nếu API chỉ có JSON, JSON là lựa chọn hợp lý; nếu resource có nhiều format, hãy xác định rõ client không gửi Accept cần nhận bản nào, rồi **kiểm thử**. Không suy ra chỉ cần `defaultContentType` thì mọi handler `produces` ngang nhau tự động được phân giải chắc chắn.

Nếu server phải từ chối request thiếu `Accept` theo quy tắc riêng, đó là **chính sách ứng dụng tùy chỉnh**, không phải chuẩn HTTP mặc định. Thông thường không nên bắt client khai báo `Accept` khi chỉ phục vụ một định dạng.

## 10. Nội dung mặc định, 406 và trường hợp không có converter

### 10.1. Khi nào 406?

Theo HTTP, server có thể trả 406 khi không có representation hợp với `Accept`; RFC cũng cho phép bỏ qua preferences trong những trường hợp nhất định. **Spring MVC với ràng buộc `produces` rõ ràng thường trả 406** khi path/method phù hợp nhưng `Accept` không khớp, thông qua `HttpMediaTypeNotAcceptableException`.

Ví dụ `GET /api/media/json-only/7` có mapping GET, client gửi `Accept: application/xml`: lỗi là **406**, không phải 404/405/415.

Không nên suy luận từ status 406 rằng controller đã chạy; với `produces`, Spring có thể loại mapping trước khi gọi method. Nếu mapping được chọn nhưng converter không ghi được, cũng có thể có exception trong giai đoạn ghi response; tùy trường hợp và phiên bản, không phải mọi lỗi serialize đều biến thành 406 (có lỗi máy chủ 500).

### 10.2. Khi nào 415?

`415 Unsupported Media Type` phản ánh **request representation** không được hỗ trợ: `Content-Type` của body không phù hợp `consumes` hoặc decoder của server. Dù client gửi `Accept: application/json`, body vẫn có thể là `text/plain` và dẫn đến 415. `HttpMediaTypeNotSupportedException` là exception Spring thường gặp.

### 10.3. Khi nào 400, 404, 405?

- `400`: JSON sai cú pháp, body không deserialize được, hoặc binding/validation không hợp lệ; có thể xảy ra khi `Content-Type` vẫn đúng.
- `404`: URL không có mapping/resource phù hợp.
- `405`: URL phù hợp nhưng HTTP method không hỗ trợ; thường có header `Allow`.
- `406`: response media type không thể thỏa `Accept`.
- `415`: request body media type không được hỗ trợ.

Nếu request vi phạm **nhiều điều kiện cùng lúc**, đừng dự đoán lỗi bằng bảng trên theo thứ tự tuyệt đối; giai đoạn mapping, converters, filters, exception handlers và customizations có thể quyết định status xuất hiện trước.

### 10.4. `Content-Type` trên response lỗi không nhất thiết giống response thành công

Khi Spring Boot dùng controller advice hoặc `ProblemDetail`, response lỗi có thể là `application/problem+json`, HTML error page hoặc body rỗng tùy cấu hình. Không kết luận sai rằng endpoint đang trả XML chỉ vì test 406 không có body JSON; lỗi negotiation có thể yêu cầu fallback có chủ đích để client vẫn đọc được thông điệp. Response `406` không đảm bảo liệt kê đầy đủ tất cả representation được hỗ trợ; muốn client biết capabilities, có thể mô tả trong OpenAPI hoặc tài liệu API.

## 11. Media type có suffix +json và versioning

Một số API dùng vendor media type như:

```http
Accept: application/vnd.example.item.v1+json
```

Ý định là yêu cầu một representation có media type **cụ thể**. Các JSON converter hiện đại có thể hỗ trợ nhóm `application/*+json`, nhưng **mapping `produces = application/json` không mặc nhiên đồng nghĩa với mọi vendor media type**. Nếu muốn endpoint nhận `application/vnd.example.item.v1+json`, hãy khai báo `produces` tương ứng và kiểm tra converter hỗ trợ ghi cùng kiểu đó.

Ví dụ minh họa đường mở rộng:

```java
@GetMapping(
    path = "/versioned/{id}",
    produces = "application/vnd.example.item.v1+json")
public Item versioned(@PathVariable("id") long id) {
    return new Item(id, "Pen");
}
```

Đoạn này **phải được thêm như một method trong controller nơi kiểu `Item` đã tồn tại** (hoặc tự định nghĩa Item/import annotation); không phải một tệp Java đứng riêng. Không tự động xem `Accept: application/json` là tương đương vendor media type trên. Versioning qua media type có lợi khi cần định nghĩa representation rõ ràng, nhưng làm phức tạp client, caching và docs; cân nhắc với URL/version header tùy mục tiêu.

Khi dùng `application/problem+json`, hãy phân biệt nó với media type thành công của resource. Response lỗi là một contract khác, không tự chứng minh endpoint hỗ trợ problem+json như một representation thành công.

## 12. Cache: khi response phụ thuộc Accept, cân nhắc Vary

Giả sử cùng `GET /api/media/items/7` trả JSON hoặc text theo `Accept`. Cache trung gian không nhận biết biến thể có thể phục vụ bản text cho client chỉ muốn JSON nếu chỉ khóa cache bằng URL. Header:

```http
Vary: Accept
```

thông báo cho cache rằng việc lựa chọn response phụ thuộc header `Accept` của request. Cần xem xét phát header này **cho các response liên quan**, không chỉ một nhánh thành công. Spring hay reverse proxy **có thể** thêm `Vary` ở một số tính năng như CORS, nhưng bạn không nên mặc định mọi response content negotiation đều tự có `Vary: Accept` trong mọi cấu hình.

Có thể gắn thêm header từ ứng dụng cho response có nhiều variant:

```java
@GetMapping(path = "/cached/{id}", produces = "application/json")
public ResponseEntity<Item> cachedJson(@PathVariable long id) {
    return ResponseEntity.ok()
            .varyBy("Accept")
            .body(new Item(id, "Pen"));
}
```

Đây là method minh họa đặt trong controller có imports `ResponseEntity`, `GetMapping`, `PathVariable` và record `Item` phù hợp; **không phải nguyên chương trình độc lập**. Nếu dự án bạn dùng phiên bản API không hỗ trợ builder method ở trên, có thể dùng `header(HttpHeaders.VARY, HttpHeaders.ACCEPT)` trong builder. `Vary` phải phản ánh đúng các header thực sự ảnh hưởng representation, không phải thay thế policy cache, TTL hay ETag.

Lưu ý: nếu response chứa dữ liệu theo user/authorization, riêng `Vary: Accept` **không làm nội dung cá nhân an toàn để shared-cache**. Vẫn cần `Cache-Control`, xác thực và phân quyền phù hợp.

## 13. Chẩn đoán production: từ request tới converter

Khi client nhận định dạng bất ngờ hoặc 406:

1. **Capture request thật:** path, method, `Accept`, `Content-Type`, body; công cụ như browser, curl, API gateway có thể tự thêm `Accept: */*` hoặc nhiều loại.
2. **Tách chiều đọc/ghi:** 415 nghi `Content-Type`/`consumes`, 406 nghi `Accept`/`produces`/canWrite; 400 nghi cú pháp/deserialization.
3. **Kiểm tra mapping:** phương thức controller và `@RequestMapping(produces=...)` ở class/method; method-level ghi đè class-level. Kiểm tra path/method trước khi đi sâu vào format.
4. **Liệt kê representation thật sự có:** không chỉ annotation mà còn thư viện Jackson XML, converter, serialization của Java return type.
5. **Rà q/wildcard:** xem q=0, q thiếu (mặc định 1), wildcard và subtype; đừng đơn giản “lấy phần tử Accept đầu tiên”.
6. **Kiểm tra chiến lược negotiation:** mặc định Spring MVC 6.x dùng Accept; tìm custom `WebMvcConfigurer`, `ContentNegotiationManager`, `favorParameter`, `defaultContentType`.
7. **Kiểm tra cách trả `String`:** `@Controller` không có `@ResponseBody` có thể trả view name; `@RestController` trả response body.
8. **Kiểm tra converter:** nếu DTO trả JSON lỗi khi gặp kiểu đặc biệt (cycles, lazy proxies, unsupported serializers), không thể chỉ sửa `Accept` rồi chắc chắn giải quyết được.
9. **Xem cache/gateway:** cùng URL có nhiều format cần `Vary: Accept` và cấu hình cache thích hợp; proxy có thể xóa/chỉnh header.
10. **Đừng khẳng định body lỗi mặc định cố định:** Spring Boot/ProblemDetail/advice có thể khác nhau. Kiểm tra status và headers, sau đó log an toàn.
11. **Quan sát mapping đã đăng ký:** Spring Boot Actuator `/actuator/mappings` nếu dependency và cấu hình cho phép; không mở actuator nhạy cảm công khai chỉ để debug.
12. **Kiểm thử từng biến:** giữ URL/method cố định rồi lần lượt thay `Accept`; thử wildcard, q và không Accept. Sau đó riêng rẽ thay `Content-Type` ở POST.

Đối với logging, cân nhắc mức debug cho những lớp chọn mapping/HTTP converter trong môi trường kiểm thử; tránh log body, token và dữ liệu cá nhân chỉ để điều tra negotiation.

## 14. Thiết kế API: lựa chọn thực tế

**API JSON thuần:** khai báo `produces=application/json` ở endpoint, có Jackson, và dùng DTO/record làm response. `Accept: application/json` đáp ứng, `Accept: application/xml` thường 406. Đây là thiết kế đơn giản, dễ tài liệu hóa.

**API nhiều định dạng:** định nghĩa danh sách representation được hỗ trợ; cung cấp converter cho mỗi định dạng; quyết định response mặc định rõ ràng; mô tả `Accept` và q; kiểm tra status/content-type và `Vary`. Nếu dùng nhiều handler cùng URL phân biệt `produces`, test cả request client gửi header cụ thể lẫn trường hợp thiếu/tie để tránh lựa chọn mơ hồ.

**API export/báo cáo:** nếu JSON, CSV, PDF là các **định dạng xuất có cấu trúc khác nhau** và có logic tạo tài liệu khác nhau, có thể dùng `Accept` hoặc một URL export riêng (`/reports/42.csv` chỉ khi được thiết kế/route tường minh). Không chỉ đổi header `Content-Type` rồi gửi body vẫn là JSON; media type phải phản ánh nội dung.

**Client gọi API:** đặt `Accept` theo định dạng mình thực sự đọc được, không luôn để `*/*`. Đặt `Content-Type` theo body **mình thực sự gửi**. Nếu API có nhiều representation, client dùng Accept trọng số khi sẵn sàng nhận nhiều kiểu.

## 15. Lỗi phổ biến và cách sửa

| Sai lầm | Hệ quả | Cách sửa |
| --- | --- | --- |
| Hiểu `Accept` là định dạng request body | Debug nhầm 406 thành 415 | Phân biệt `Accept` và `Content-Type` |
| Chỉ khai báo `produces="application/xml"` mà không cài converter | Không thể serialize object thành XML đúng | Thêm/cấu hình converter và test với DTO thực |
| Trả `String` với `@Controller` rồi mong response body | Spring có thể coi String là view name | Dùng `@ResponseBody`/`@RestController` |
| Mong `GET /items.json` tự đổi format theo đuôi | Routing 6.x mặc định không cần suffix strategy | Dùng Accept hoặc khai báo route explicit |
| Mong `?format=xml` hoạt động mặc định | Parameter strategy chưa bật | Cấu hình rõ hoặc dùng Accept |
| Bỏ qua q hoặc wildcard | Chọn nhầm media type | Dùng parser/negotiation của framework |
| Cho rằng q=0.1 nghĩa là bị cấm | Từ chối representation vẫn hợp lệ | Chỉ q=0 loại trừ; q dương là chấp nhận |
| Thiếu test cho Accept không tương thích | Chỉ phát hiện 406 trên production | Test bằng MockMvc và client thực |
| Bỏ qua `Vary: Accept` khi caching nhiều variant | Cache có thể trộn JSON và text | Thiết kế Vary và cache policy rõ |
| Dùng JSON converter nhưng đổi `Content-Type` thành XML | Payload và nhãn không khớp | Serialize XML thật hoặc không công bố XML |
| Quên exception/response lỗi cũng có media type | Client không đọc được lỗi nhất quán | Thiết kế lỗi riêng và test trường hợp 406 |
| Dùng wildcard `*/*` trong test rồi hardcode JSON | Có thể chọn representation khác khi server đổi | Test với Accept cụ thể khi cần hợp đồng ổn định |

## 16. Câu hỏi tự kiểm tra

**1.** Một GET chỉ `produces = application/json`, client gửi `Accept: application/xml`. Status thường là gì?

**Đáp án:** `406 Not Acceptable`; đường dẫn/method có thể đúng nhưng không có representation phù hợp.

**2.** Client gửi JSON body kèm `Content-Type: text/plain` tới POST chỉ `consumes = application/json`, nhưng đặt `Accept: application/json`. Có giải quyết lỗi không?

**Đáp án:** Không. `Accept` không sửa nhãn của request body; thường nhận `415`.

**3.** `Accept: text/plain;q=0.8, application/json;q=0.2` có buộc server luôn chọn text không?

**Đáp án:** Không. Nó ưu tiên text **nếu server có bản text**; JSON với q=0.2 vẫn chấp nhận nếu chỉ có JSON.

**4.** `Accept: */*` có đồng nghĩa “chỉ JSON” không?

**Đáp án:** Không. Nó chấp nhận mọi media type.

**5.** Nếu JSON converter đã có, `produces = "application/xml"` có đủ để xuất XML không?

**Đáp án:** Không. Cần XML converter/serializer thực sự hỗ trợ return type, đồng thời mapping tương thích.

**6.** Hai handler cùng GET và URL nhưng `produces` khác nhau có thể đăng ký không?

**Đáp án:** Có. Đây là một cách tạo representation riêng theo Accept; phải xác định cách xử lý khi client không có Accept hoặc sở thích ngang nhau.

**7.** Cùng URL trả JSON hoặc text tùy `Accept`; cache trung gian cần chú ý header nào?

**Đáp án:** `Vary: Accept`, cùng policy cache phù hợp.

**8.** Vì sao 406 không luôn đồng nghĩa method controller đã chạy?

**Đáp án:** `produces` có thể bị loại ngay tại giai đoạn chọn handler trước khi gọi controller.

## 17. Tài liệu tham khảo

- Spring Framework 6.2 — Content Types và cấu hình negotiation: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/content-negotiation.html
- Spring Framework 6.2 — Mapping Requests, điều kiện produces/consumes: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-requestmapping.html
- Spring Framework 6.2 — HTTP Message Conversion: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html
- Spring Framework 6.2 — @ResponseBody: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/responsebody.html
- Spring Framework 6.2 — Error Responses: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html
- RFC 9110 — HTTP Semantics, Accept/q/wildcard/Vary và 406/415: https://www.rfc-editor.org/rfc/rfc9110.html
- RFC 9457 — Problem Details for HTTP APIs: https://www.rfc-editor.org/rfc/rfc9457.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-message-bodies-007">Phân biệt lỗi JSON sai cú pháp, JSON sai kiểu và dữ liệu không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt lỗi JSON sai cú pháp, JSON sai kiểu và dữ liệu không hợp lệ trong Spring MVC

## 1. Mục tiêu

Ba HTTP request sau đều có thể nhận phản hồi **400 Bad Request**, nhưng **nguyên nhân, thời điểm lỗi và cách sửa hoàn toàn khác nhau**:

```json
{"sku":"A-100", "quantity": 2,
```

```json
{"sku":"A-100", "quantity": "nhiều", "shipping":{"city":"Hà Nội"}}
```

```json
{"sku":"", "quantity": 0, "shipping":{"city":""}}
```

- **JSON sai cú pháp:** văn bản không tuân theo ngữ pháp JSON. Ví dụ đầu tiên thiếu dấu `}` kết thúc object. JSON parser không tạo được cấu trúc dữ liệu hoàn chỉnh.
- **JSON sai kiểu hoặc không thể giải mã sang kiểu Java:** văn bản là JSON hợp lệ, nhưng mapper không tạo được kiểu Java đích. Ví dụ `"quantity": "nhiều"` không chuyển được thành `Integer` với cấu hình Jackson thông thường.
- **Dữ liệu không hợp lệ:** JSON đúng cú pháp và chuyển thành object Java được, nhưng **vi phạm quy tắc của API**, ví dụ số lượng phải ≥ 1, SKU không rỗng, thành phố giao hàng bắt buộc.

Sau bài này bạn sẽ phân biệt chính xác ba lớp lỗi, hiểu luồng `@RequestBody` của Spring MVC, biết khi nào gặp `HttpMessageNotReadableException` hoặc `MethodArgumentNotValidException`, xử lý lỗi có cấu trúc, không tiết lộ thông tin nội bộ và viết kiểm thử tái hiện từng trường hợp.

**Phạm vi:** Spring MVC (Servlet stack), Spring Framework 6.x, Spring Boot 3.x, Jackson làm JSON mapper, Java 17+. Với WebFlux, Moshi/Gson hoặc cấu hình Jackson thay đổi, tên exception và hành vi chuyển kiểu có thể khác.

## 2. Ba tầng kiểm tra trong đường đi của request

Một request đến controller dạng:

```java
@PostMapping("/api/orders")
OrderCreated create(@Valid @RequestBody CreateOrderRequest request) {
    // Được gọi khi body đọc được, object tạo được và validation qua.
}
```

*Đoạn này chỉ minh họa chữ ký; ứng dụng Java đầy đủ ở mục 8.*

Luồng xử lý cơ bản:

```text
Client
   │
   │ POST /api/orders
   │ Content-Type: application/json
   ▼
DispatcherServlet / RequestMappingHandlerAdapter
   │
   ├─ (A) Chọn HTTP message converter phù hợp với media type
   │      Content-Type sai → có thể 415 Unsupported Media Type
   │
   ├─ (B) JSON parser (Jackson)
   │      Văn bản không đúng ngữ pháp JSON → JsonParseException
   │      Spring bọc thành HttpMessageNotReadableException → 400
   │
   ├─ (C) JSON deserialization / mapping
   │      JSON hợp lệ, nhưng không tạo được CreateOrderRequest:
   │      ví dụ String "nhiều" → Integer quantity
   │      → MismatchedInputException / InvalidFormatException
   │      Spring bọc thành HttpMessageNotReadableException → 400
   │
   ├─ (D) Validation trên object Java đã tạo (`@Valid`)
   │      @NotBlank, @Min, @NotNull, nested @Valid, ...
   │      → MethodArgumentNotValidException → thường 400
   │
   └─ (E) Controller method
          → xử lý nghiệp vụ nếu input hợp lệ
          → có thể tự trả lỗi nghiệp vụ như 409/422/400 tùy hợp đồng
```

**Điểm phân biệt lớn nhất:** một JSON có thể **đúng cú pháp** nhưng **sai kiểu Java**; một Java object có thể **tạo thành công** nhưng **vi phạm validation**. Không gom cả ba thành câu “JSON invalid” trong tài liệu API hoặc metric vận hành.

**Lưu ý về thứ tự:** các bước trên là mô hình dễ hiểu, không hứa hẹn rằng mọi request đều đi qua tất cả bước. Ví dụ `Content-Type: text/plain` có thể bị từ chối ở bước chọn converter, trước khi parser JSON chạy. Một converter tùy biến có thể gộp hai bước parse và mapping trong cùng lời gọi. Phần routing, filter bảo mật, giới hạn body hoặc đọc network cũng có thể dừng request sớm hơn.

## 3. Lỗi JSON sai cú pháp (syntax error)

JSON phải tuân theo cú pháp riêng: object đặt trong `{}`, key là chuỗi trong dấu nháy kép, mỗi cặp phân tách bằng dấu phẩy, string phải có escape hợp lệ, số không được viết theo mọi kiểu JavaScript.

### 3.1. Các ví dụ sai

| Request body | Lỗi cú pháp | Dạng đúng |
| --- | --- | --- |
| `{"id": 1,` | Thiếu trường hoặc phần kết thúc sau dấu phẩy | `{"id": 1}` |
| `{'id': 1}` | JSON chuẩn yêu cầu dấu nháy kép cho key | `{"id": 1}` |
| `{id: 1}` | Tên thuộc tính không được bỏ nháy trong JSON chuẩn | `{"id": 1}` |
| `{"id": 1,}` | Trailing comma không hợp lệ theo JSON chuẩn | `{"id": 1}` |
| `{"active": True}` | Boolean JSON là `true` hoặc `false`, chữ thường | `{"active": true}` |
| `{"name": "A" "age": 3}` | Thiếu dấu phẩy giữa hai cặp | `{"name":"A","age":3}` |
| `{"x":"dòng xuống↵trực tiếp"}` | Ký tự newline thô bên trong JSON string | `{"x":"dòng xuống\nhợp lệ"}` |

Các trường hợp trên có thể bị Jackson báo `com.fasterxml.jackson.core.JsonParseException` hoặc subtype (ví dụ EOF bất ngờ). Khi đọc `@RequestBody`, Spring MVC thường bọc lỗi thành `org.springframework.http.converter.HttpMessageNotReadableException`. Controller method **thường chưa được gọi**, vì không có object đích để xử lý.

Nếu ứng dụng bật một số tính năng parser mở rộng (ví dụ cho phép single quotes hoặc trailing comma), dữ liệu sai theo JSON chuẩn có thể được chấp nhận. Các ví dụ trong bài giả định cấu hình JSON mặc định của Spring Boot 3. Đừng nhầm “parser có chế độ dễ dãi” với “JSON chuẩn cho phép cú pháp đó”.

### 3.2. Cách đọc lỗi parser

Thông tin root cause trong server log có thể chứa vị trí line/column, token không hợp lệ và lời giải thích từ Jackson. Điều này hữu ích khi debug; khi trả API public, tránh sao chép toàn bộ exception message hoặc nội dung body vào phản hồi vì có thể rò rỉ dữ liệu, phụ thuộc version và cấu trúc nội bộ.

Khuyến nghị response ổn định:

```json
{
  "code": "JSON_SYNTAX",
  "fields": []
}
```

Đây là **hợp đồng lỗi do ví dụ bài học chủ động xây dựng**, không phải định dạng lỗi mặc định của Spring Boot. Nếu API cần chỉ rõ line/column, hãy quyết định trong hợp đồng riêng và kiểm tra tác động bảo mật. Không cần trả “field” khi parser chưa phân tích được một JSON document hợp lệ.

### 3.3. JSON thiếu body khác gì JSON sai cú pháp?

Request `POST` không gửi body dù `@RequestBody(required = true)` (mặc định) có thể tạo `HttpMessageNotReadableException` kiểu “Required request body is missing”. Đây **không hẳn là lỗi cú pháp của một tài liệu JSON**, vì không có JSON document để parse. Nên dùng mã tổng quát `BODY_NOT_READABLE` hoặc `BODY_REQUIRED` nếu phân loại riêng.

Một body chứa chữ `null` là **JSON hợp lệ** (literal null); tác động tiếp theo phụ thuộc kiểu đích và việc `@RequestBody` có yêu cầu object khác null hay không. Đừng gọi nó là JSON sai cú pháp.

## 4. JSON đúng cú pháp nhưng sai kiểu/không deserialization được

JSON chỉ có các loại giá trị cơ bản: object, array, string, number, boolean và null. Java có kiểu phong phú hơn (`Integer`, `LocalDate`, `UUID`, enum, record, collections, các lớp do ứng dụng định nghĩa). Jackson phải áp dụng quy tắc ánh xạ giữa hai phía.

### 4.1. Ví dụ dữ liệu JSON hợp lệ nhưng không khớp Java

Giả sử DTO có `Integer quantity`, `LocalDate deliveryDate` và `List<String> labels`.

| JSON đầu vào | Kiểu Java đích | Nguyên nhân có thể thất bại |
| --- | --- | --- |
| `{"quantity":"nhiều"}` | `Integer` | Không parse được chuỗi thành số |
| `{"quantity":999999999999}` | `Integer` | Số vượt phạm vi integer |
| `{"deliveryDate":"ngày mai"}` | `LocalDate` | Không đúng định dạng được parser ngày chấp nhận |
| `{"labels":{"key":"x"}}` | `List<String>` | Object thay vì array, khi không có cấu hình coercion riêng |
| `{"shipping":[] }` | Object `ShippingAddress` | Array không phải dạng object dự kiến |
| `{"status":"UNKNOWN"}` | Enum `OrderStatus` | Không có constant tương ứng với chuỗi |
| `{"id":"không-phải-uuid"}` | `UUID` | Chuỗi không tạo được UUID theo quy tắc mapper |

Jackson thường ném `com.fasterxml.jackson.databind.exc.MismatchedInputException` hoặc subtype `InvalidFormatException`. Các exception này là lỗi **đọc/ánh xạ request**, và Spring MVC thường chuyển thành `HttpMessageNotReadableException`, nên HTTP status mặc định hay là **400**.

**Lưu ý quan trọng về coercion:** không phải cứ JSON string gặp trường Java kiểu số là lỗi. Tùy Jackson và cấu hình, `"quantity":"2"` **có thể** được chấp nhận và đổi thành `Integer(2)`. `2.0`, chuỗi trống, boolean hoặc singleton thay cho array cũng có hành vi phụ thuộc các tùy chọn coercion. Vì thế dùng `"quantity":"nhiều"` trong test để có case sai kiểu chắc chắn hơn trong cấu hình demo. Nếu hợp đồng API yêu cầu **bắt buộc JSON number**, hãy cấu hình deserialization/coercion nghiêm ngặt và thêm test; chỉ annotation `Integer` chưa bảo đảm cấm tất cả string chứa số.

### 4.2. Sai hình dạng và lỗi không đọc được

Các trường hợp khác:

- Object chứa cấu trúc lồng nhau không phù hợp với record/class constructor.
- JSON hợp lệ nhưng Jackson không có cách tạo object (không có creator thích hợp, constructor không thể dùng hoặc cấu hình polymorphic type không phù hợp).
- Một constructor/creator của DTO tự ném lỗi; đây cũng có thể trở thành lỗi ánh xạ.
- Với cấu hình chặt chẽ, thuộc tính JSON **không khai báo** có thể gây `UnrecognizedPropertyException`. Nhưng Spring Boot thường cấu hình Jackson bỏ qua unknown properties; không mặc nhiên khẳng định mọi thuộc tính lạ đều bị từ chối.
- JSON có duplicate keys đôi khi được parser/mapper chấp nhận; muốn từ chối bắt buộc cần bật kiểm tra duplicate tương ứng, không suy ra đó luôn là syntax error.

**Nguyên tắc:** nếu parser hiểu được tài liệu JSON nhưng mapper không thể tạo ra object Java mong đợi, đây là lỗi **deserialization/mapping**, không phải lỗi Bean Validation. `@NotNull`, `@Min` không chạy có ý nghĩa trên một DTO **chưa thể được tạo hoàn chỉnh**.

Một mã API phù hợp:

```json
{
  "code": "JSON_TYPE",
  "fields": []
}
```

Trong hệ thống thực, có thể phân loại chi tiết `JSON_SHAPE`/`JSON_VALUE_FORMAT`/`JSON_TYPE`. Đừng phụ thuộc vào việc mọi `JsonMappingException` đều là “sai kiểu do client”: custom deserializer, bug trong setter hoặc cấu hình server cũng có thể gây exception. Ví dụ ở mục 8 chỉ xếp `MismatchedInputException` vào `JSON_TYPE`; các lỗi khác dùng mã fallback.

### 4.3. Các giá trị null và thiếu trường

`null` là một JSON value hợp lệ và thiếu field cũng không phải syntax error. Ví dụ:

```json
{"sku":"A-100","quantity":null,"shipping":{"city":"Hà Nội"}}
```

Khi `quantity` dùng **`Integer`** (wrapper), Jackson có thể tạo object với `quantity == null`. `@NotNull` trên quantity sau đó báo **validation error**. Ngược lại, primitive `int` không lưu được null, nên mapper có thể tạo giá trị mặc định `0` hoặc báo lỗi tùy cấu hình `FAIL_ON_NULL_FOR_PRIMITIVES`; đây là lý do DTO request thường nên dùng wrapper + constraint rõ ràng cho field bắt buộc.

`null` cho một nested object cũng có thể được deserialize thành null; chỉ `@Valid` trên field đó **không buộc field phải tồn tại**. Thêm `@NotNull` nếu shipping bắt buộc, và `@Valid` để kiểm tra các constraint bên trong khi shipping khác null.

## 5. JSON hợp lệ về cấu trúc và kiểu nhưng vi phạm quy tắc dữ liệu

Sau khi parser và mapper thành công, Java object có thể vẫn sai với hợp đồng của API:

```java
public record CreateOrderRequest(
    @NotBlank String sku,
    @NotNull @Min(1) Integer quantity,
    @NotNull @Valid ShippingAddress shipping
) {}

public record ShippingAddress(@NotBlank String city) {}
```

*Đây là định nghĩa minh họa; cần import Jakarta Validation và đặt vào project phù hợp. Phiên bản đầy đủ ở mục 8.*

Body:

```json
{"sku":"","quantity":0,"shipping":{"city":""}}
```

JSON hoàn toàn đúng cú pháp, các kiểu Java đều tạo được (`String`, `Integer`, record lồng nhau), nhưng:

- `sku=""` vi phạm `@NotBlank`.
- `quantity=0` vi phạm `@Min(1)`; không phải lỗi parse số.
- `shipping.city=""` vi phạm `@NotBlank` ở object lồng nhau; `@Valid` trên `shipping` cho phép đi sâu.
- `@NotNull` làm rõ việc thiếu trường hoặc truyền null là không hợp lệ.

Nếu controller khai báo `@Valid @RequestBody CreateOrderRequest request` và **không** có `Errors`/`BindingResult` liền sau, Spring MVC thường ném `org.springframework.web.bind.MethodArgumentNotValidException`. Exception chứa `BindingResult`, trong đó có `FieldError` cho các trường như `sku`, `quantity` và `shipping.city`. HTTP status mặc định là **400 Bad Request** trong Spring MVC.

Phản hồi do ví dụ xây dựng:

```json
{
  "code": "VALIDATION_FAILED",
  "fields": ["quantity", "shipping.city", "sku"]
}
```

Lỗi có thể thuộc toàn object (cross-field constraint), không chỉ field. Nếu cần, ghi `ObjectError`/`global` riêng. Khi thiết kế JSON API, dùng mã lỗi ổn định và bản địa hóa thông điệp; không trả nguyên `getRejectedValue()` khi dữ liệu có thể chứa bí mật.

### 5.1. @Valid và @Validated có gì khác trong phạm vi này?

- `@Valid` là cách thông dụng để yêu cầu Jakarta Bean Validation chạy trên object đã được tạo. Thêm `@Valid` tại property lồng nhau để cascade validation.
- `@Validated` hỗ trợ validation groups và các cơ chế tích hợp của Spring khi cần.
- Muốn các constraint `@NotBlank`, `@Min` hoạt động, dự án phải có Jakarta Validation implementation, ví dụ `spring-boot-starter-validation`.
- Chỉ thêm annotation mà không có provider validation hoặc không kích hoạt validation đúng vị trí có thể khiến request vi phạm constraint vẫn tới service.
- **Semantic validation không đồng nghĩa kiểm tra nghiệp vụ trong database:** ví dụ trùng SKU, hết hàng, trạng thái đơn đã khóa hay không có quyền sửa đơn. Các lỗi đó có thể phát sinh **sau** Bean Validation khi controller gọi service; chọn HTTP 400/409/422 theo hợp đồng và ngữ nghĩa, không ép tất cả thành `MethodArgumentNotValidException`.

### 5.2. Khi nào không còn là MethodArgumentNotValidException?

Từ Spring MVC 6.1, method validation có thể được áp dụng khi constraint đặt trực tiếp trên parameter/return value của handler, ví dụ `@Min(1) @RequestParam int page`. Trong trường hợp này, `HandlerMethodValidationException` có thể xuất hiện thay vì `MethodArgumentNotValidException`. Điều đó không thay đổi bản chất rằng lỗi xuất hiện ở giai đoạn validation, nhưng bạn cần xử lý đúng exception theo chữ ký method.

Nếu controller khai báo `@Valid @RequestBody Dto dto, Errors errors` **liền nhau**, các lỗi Bean Validation của object đã deserialize có thể được trao cho controller thay vì ném `MethodArgumentNotValidException`. Vẫn không thể dùng `Errors` đó bắt lỗi syntax hoặc deserialize thất bại, vì method chưa nhận được DTO hợp lệ.

## 6. Bảng đối chiếu nhanh: nguyên nhân, exception, HTTP

| Tình huống | Giai đoạn | Exception thường thấy | HTTP mặc định/phổ biến |
| --- | --- | --- | --- |
| `{"quantity":1,}` | JSON syntax | `JsonParseException` bên trong `HttpMessageNotReadableException` | 400 |
| `{"quantity":"nhiều"}` khi cần `Integer` | Deserialization | `InvalidFormatException`/`MismatchedInputException` bên trong `HttpMessageNotReadableException` | 400 |
| `{"quantity":0}` với `@Min(1)` | Bean Validation sau khi deserialize | `MethodArgumentNotValidException` | 400 |
| `{"quantity":null}` với `@NotNull` và `Integer` | Bean Validation | `MethodArgumentNotValidException` | 400 |
| Không gửi body nhưng `@RequestBody` yêu cầu body | HTTP body missing | `HttpMessageNotReadableException` | 400 |
| `Content-Type: text/plain` gửi tới endpoint chỉ nhận JSON | Chọn converter/media type | `HttpMediaTypeNotSupportedException` | 415 |
| `Accept: text/plain` khi endpoint chỉ tạo `application/json` | Content negotiation | `HttpMediaTypeNotAcceptableException` | 406 |
| `@RequestParam int page` nhận `page=abc` | Chuyển kiểu request parameter ngoài JSON body | `MethodArgumentTypeMismatchException` | 400 |
| Constraint trực tiếp trên handler parameter | Method validation | `HandlerMethodValidationException` (MVC 6.1+) | 400 cho input vi phạm |
| Object hợp lệ nhưng vi phạm business rule | Service/domain | Exception/response do ứng dụng định nghĩa | 400, 409 hoặc 422 tùy hợp đồng |

**Không suy đoán nguyên nhân từ mỗi HTTP 400.** Trên wire, ba lỗi chính đều có thể cùng status 400; để phân biệt cần mã lỗi response, log/metric server hoặc exception chain.

**Đừng phụ thuộc 100% vào một tên exception:** behavior thay đổi theo Spring Framework/Jackson, converter, cấu hình exception handlers và kiểu controller. Khi ghi log phục vụ debugging, dùng `exception.getCause()`, phân loại có kiểm soát; không đưa raw exception class của bên thứ ba thành hợp đồng public API dài hạn.

## 7. Thiết kế hợp đồng lỗi JSON có ích cho client

Các client frontend/mobile cần biết có nên **sửa cấu trúc request** hay **hiển thị lỗi form**. Một API minh bạch có thể chuẩn hóa:

| `code` | Ý nghĩa cho client | Gợi ý phản ứng |
| --- | --- | --- |
| `JSON_SYNTAX` | Body không phải JSON đúng cú pháp | Sửa cách serialize, báo lỗi phía client/integration |
| `JSON_TYPE` | JSON có cấu trúc/giá trị không ánh xạ được sang DTO | Sửa kiểu dữ liệu hoặc shape theo API contract |
| `BODY_NOT_READABLE` | Body bị thiếu hoặc không đọc được vì nguyên nhân khác | Kiểm tra request body và content negotiation |
| `VALIDATION_FAILED` | DTO đã parse/deserialize được nhưng vi phạm constraint | Hiển thị field errors để người dùng sửa |
| `BUSINESS_RULE_VIOLATION` | Đầu vào qua validation kỹ thuật nhưng vi phạm nghiệp vụ | Thông báo trạng thái và hướng xử lý tương ứng |

Bài mẫu trả kiểu đơn giản `{"code":"...","fields":[...]}` để thấy rõ khác biệt. Trong dự án lớn có thể dùng `ProblemDetail` (RFC 9457), ví dụ `type`, `title`, `status`, `detail` và extension `code`/`errors`. Đó là **lựa chọn định dạng**, không làm thay đổi nơi exception xuất hiện.

Lời khuyên cho server:

1. Thống nhất schema phản hồi lỗi trong toàn API, gồm error code ổn định và field path nếu an toàn.
2. Không trả thẳng stack trace, raw JSON body, đường dẫn file máy chủ hoặc thông điệp Jackson chứa nội dung bí mật.
3. Với validation, trả mã phù hợp cho từng field; khi i18n, render message từ `MessageSource`. Cân nhắc giới hạn số lỗi/phần tử để tránh response quá lớn.
4. Đặt log/metric tách riêng `JSON_SYNTAX`, `JSON_TYPE`, `VALIDATION_FAILED`; đó là tín hiệu khác nhau về bug client và UX.
5. Đảm bảo custom `@RestControllerAdvice` không nuốt mọi exception thành HTTP 400. Lỗi server không liên quan tới input nên được phát hiện đúng (thường HTTP 500), không trá hình thành lỗi người dùng.
6. Kiểm thử cả lỗi thiếu body, JSON null, unknown fields, coercion string-to-number, nested validation, field thuộc enum và giới hạn payload trên cấu hình deploy thực tế.

## 8. Ứng dụng Spring Boot 3 + Java 17 chạy được

Ví dụ dùng **Spring Boot 3.3.13**, Spring MVC, Jackson và Jakarta Validation. Demo có ba error code tách biệt; đồng thời có fallback cho `HttpMessageNotReadableException` không xác định chính xác là cú pháp hay sai kiểu.

### 8.1. Cấu trúc

```text
json-errors-demo/
├── pom.xml
└── src/
    ├── main/java/example/
    │   ├── JsonErrorsDemoApplication.java
    │   ├── OrderController.java
    │   └── JsonErrorAdvice.java
    └── test/java/example/
        └── OrderControllerTest.java
```

### 8.2. File pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>json-errors-demo</artifactId>
    <version>1.0.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.13</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
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
```

`spring-boot-starter-web` cung cấp Spring MVC và JSON conversion thông thường qua Jackson. `spring-boot-starter-validation` kích hoạt các Jakarta constraints trong demo; `spring-boot-starter-test` chứa JUnit Jupiter, MockMvc và bộ assertion test.

### 8.3. File JsonErrorsDemoApplication.java

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JsonErrorsDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(JsonErrorsDemoApplication.class, args);
    }
}
```

### 8.4. File OrderController.java

```java
package example;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Các record package-private được đặt trong cùng file theo cú pháp Java 17.
record ShippingAddress(@NotBlank String city) {}

record CreateOrderRequest(
        @NotBlank String sku,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Valid ShippingAddress shipping
) {}

record OrderCreated(
        String status, String sku, Integer quantity, String city
) {}

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderCreated create(@Valid @RequestBody CreateOrderRequest request) {
        return new OrderCreated(
                "accepted",
                request.sku(),
                request.quantity(),
                request.shipping().city());
    }
}
```

Các record vừa là Java DTO, vừa khai báo rõ quy tắc validation. `@NotNull @Valid ShippingAddress shipping` đảm bảo nested object không null và validate được `city`. `@NotBlank String sku` kiểm tra chuỗi; `@Min(1) Integer quantity` kiểm tra số dương sau khi deserialize. Method không khai báo `Errors` liền sau request DTO, nên lỗi validation được Spring chuyển tới global exception handler.

### 8.5. File JsonErrorAdvice.java

Đây là phần cốt lõi để **phân loại** thay vì trả chung mọi trường hợp là “invalid JSON”.

```java
package example;

import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Cấu trúc phản hồi của demo, dùng chung cho các nhóm lỗi.
record ApiError(String code, List<String> fields) {}

@RestControllerAdvice
public class JsonErrorAdvice extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String code = classifyUnreadable(ex);
        return new ResponseEntity<>(
                new ApiError(code, List.of()),
                headers,
                status);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<String> fields = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .toList();

        return new ResponseEntity<>(
                new ApiError("VALIDATION_FAILED", fields),
                headers,
                status);
    }

    private static String classifyUnreadable(Throwable error) {
        // Spring có thể bọc lỗi Jackson trong một hoặc nhiều cause.
        boolean typeMismatch = false;
        Throwable cursor = error;

        while (cursor != null) {
            if (cursor instanceof JsonParseException) {
                return "JSON_SYNTAX";
            }
            if (cursor instanceof MismatchedInputException) {
                typeMismatch = true;
            }
            cursor = cursor.getCause();
        }

        // Không đoán tất cả lỗi unreadable đều do cú pháp hoặc kiểu.
        return typeMismatch ? "JSON_TYPE" : "BODY_NOT_READABLE";
    }
}
```

**Vì sao phân loại như vậy?**

- `JsonParseException` cho biết lỗi ở lớp parser, nên `JSON_SYNTAX`.
- `MismatchedInputException` (bao gồm nhiều `InvalidFormatException`) đại diện một số lỗi ánh xạ giá trị/hình dạng đầu vào, nên `JSON_TYPE`.
- Thiếu body hoặc lỗi đọc khác có thể chỉ là `HttpMessageNotReadableException` không có cause thuộc hai loại trên, nên dùng fallback `BODY_NOT_READABLE`.

Điều này chỉ là **phân loại thực dụng cho demo**. Không có bảo đảm mọi lỗi deserialization từ Jackson đều nằm trong `MismatchedInputException`; `JsonMappingException` còn có những subtype liên quan tới bug trong creator, setter, custom deserializer hoặc cấu hình. Trong production, cân nhắc xử lý rõ từng trường hợp, ghi log đầy đủ ở server, phản hồi gọn và an toàn ở client.

`ResponseEntityExceptionHandler` vốn có handler trung tâm cho nhiều exception Spring MVC. Ở đây **override** hai phương thức `handleHttpMessageNotReadable`/`handleMethodArgumentNotValid` để thống nhất body, tránh viết `@ExceptionHandler` trùng với handler kế thừa gây lỗi ánh xạ exception handler.

### 8.6. Chạy và gửi bốn loại request

Cài JDK 17+ và Maven. Từ thư mục chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Request hợp lệ:

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"sku":"A-100","quantity":2,"shipping":{"city":"Ha Noi"}}'
```

Kỳ vọng HTTP **200**, body JSON:

```json
{"status":"accepted","sku":"A-100","quantity":2,"city":"Ha Noi"}
```

Request **sai cú pháp** (cố ý thiếu `}`):

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"sku":"A-100","quantity":2,'
```

Kỳ vọng HTTP **400**, body:

```json
{"code":"JSON_SYNTAX","fields":[]}
```

Request **sai kiểu** (chuỗi không chuyển được sang số nguyên):

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"sku":"A-100","quantity":"many","shipping":{"city":"Ha Noi"}}'
```

Kỳ vọng HTTP **400**, body:

```json
{"code":"JSON_TYPE","fields":[]}
```

Request **vi phạm Bean Validation**:

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"sku":"","quantity":0,"shipping":{"city":""}}'
```

Kỳ vọng HTTP **400**, body:

```json
{"code":"VALIDATION_FAILED","fields":["quantity","shipping.city","sku"]}
```

Với ví dụ này, ba status lỗi đều là 400; **`code` khác nhau**, vì nguyên nhân xảy ra ở tầng khác nhau. Danh sách lỗi validation được sắp xếp theo field để tránh phụ thuộc thứ tự provider. Còn status/body lỗi 415 hoặc 406 không được custom theo schema ở trên: handler kế thừa có thể tạo `ProblemDetail`, nên đừng mong `ApiError` cho mọi loại lỗi HTTP chưa override.

Lệnh `curl` có dấu `\` để nối dòng cho shell POSIX. Trên PowerShell, dùng `curl.exe` và nhập câu lệnh một dòng hoặc áp dụng cú pháp multiline của PowerShell; tránh alias `curl` trỏ tới `Invoke-WebRequest`.

### 8.7. MockMvc kiểm chứng đầy đủ ba tầng lỗi

File `src/test/java/example/OrderControllerTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void validJsonReachesController() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"sku":"A-100","quantity":2,
                             "shipping":{"city":"Ha Noi"}}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("accepted"))
            .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void malformedJsonIsSyntaxFailure() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sku\":\"A-100\",\"quantity\":2,"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("JSON_SYNTAX"));
    }

    @Test
    void invalidIntegerIsDeserializationFailure() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"sku":"A-100","quantity":"many",
                             "shipping":{"city":"Ha Noi"}}
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("JSON_TYPE"));
    }

    @Test
    void validJsonObjectCanFailBeanValidation() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"sku":"","quantity":0,
                             "shipping":{"city":""}}
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("""
                    {"code":"VALIDATION_FAILED",
                     "fields":["quantity","shipping.city","sku"]}
                    """));
    }

    @Test
    void missingBodyIsNeitherSyntaxNorDtoValidation() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BODY_NOT_READABLE"));
    }

    @Test
    void nullQuantityDeserializesThenFailsNotNull() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"sku":"A-100","quantity":null,
                             "shipping":{"city":"Ha Noi"}}
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fields[0]").value("quantity"));
    }

    @Test
    void wrongMediaTypeIs415NotJsonSyntaxError() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("hello"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unacceptableResponseTypeIs406NotJsonError() throws Exception {
        mvc.perform(post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_PLAIN)
                    .content("""
                            {"sku":"A-100","quantity":2,
                             "shipping":{"city":"Ha Noi"}}
                            """))
            .andExpect(status().isNotAcceptable());
    }
}
```

Chạy:

```bash
mvn test
```

Các test giúp khóa hành vi mong muốn: JSON parser lỗi, mapper lỗi, constraint lỗi, body thiếu, null hợp lệ cú pháp nhưng không hợp lệ theo constraint, và 415/406 liên quan tới media type. Tất cả test chạy qua Spring MVC test context với `MockMvc`; không cần bật HTTP server riêng.

**Giới hạn kiểm thử:** những assertion nêu trên là kỳ vọng theo cấu hình demo. Khi thay đổi Jackson features, đối tượng DTO, constraint groups, error handler hoặc Spring Framework version, chạy lại test và cập nhật hợp đồng một cách có chủ ý.

## 9. Các trường hợp biên thường gây hiểu nhầm

### 9.1. JSON số được đặt trong chuỗi

```json
{"quantity":"2"}
```

Nhiều cấu hình Jackson chuyển được `"2"` thành `Integer(2)`, vậy request có thể **thành công** nếu các field khác hợp lệ. Nếu yêu cầu JSON number tuyệt đối, cần áp dụng chính sách coercion chặt chẽ và viết test `"quantity":"2"` phải bị từ chối. Không có một “bản chất JSON sai kiểu” cố định tách khỏi **hợp đồng deserialization** đã cấu hình.

### 9.2. Trường vắng mặt, null và chuỗi rỗng

| JSON | Đích | Kết quả thường gặp |
| --- | --- | --- |
| Không có `quantity` | `Integer quantity` | `null`, sau đó `@NotNull` báo validation error |
| `"quantity":null` | `Integer quantity` | `null`, `@NotNull` báo validation error |
| `"quantity":""` | `Integer quantity` | Có thể thành `null` hoặc mapping error tùy coercion; cần test |
| `"quantity":"abc"` | `Integer quantity` | Mapping error, không tạo được số |
| `"sku":""` | `String sku` + `@NotBlank` | String hợp lệ về kiểu, validation error |
| `"sku":null` | `String sku` + `@NotBlank` | Null vẫn deserialize được, validation error |
| `"shipping":null` | `@NotNull @Valid ShippingAddress` | Null hợp lệ cú pháp; `@NotNull` báo validation error |
| Không có `shipping.city` | `@Valid ShippingAddress` + `@NotBlank city` | Nếu object shipping được tạo, nested validation báo city |

### 9.3. Date/time, enum và định dạng tùy chỉnh

Giả sử DTO có:

```java
record Appointment(java.time.LocalDate date, Priority priority) {}
enum Priority { LOW, HIGH }
```

JSON `{"date":"tomorrow","priority":"URGENT"}` là JSON đúng cú pháp nhưng Jackson không thể parse ngày hoặc enum theo cấu hình thông thường. Nếu bạn cần ngày theo định dạng khác, dùng cấu hình Jackson như `@JsonFormat` hoặc custom deserializer; `@DateTimeFormat` chủ yếu dành cho Spring type conversion (ví dụ form/query) và không phải giải pháp chung cho JSON `@RequestBody`. Với enum có unknown value, hãy quyết định chính sách thay vì âm thầm map mọi giá trị lạ.

### 9.4. Unknown fields và duplicate keys

JSON `{"sku":"A-100","quantity":1,"shipping":{"city":"HN"},"admin":true}` là đúng cú pháp. `admin` là field **ngoài hợp đồng DTO**, nhưng không nhất thiết gây lỗi với cấu hình Jackson của Spring Boot: unknown property có thể bị bỏ qua. Muốn API từ chối, đặt chính sách strict/deserialize thích hợp và có test. Dữ liệu nhạy cảm trong entity tuyệt đối không nên được expose tự do dưới dạng request DTO có setter tương ứng.

Với duplicate key như `{"quantity":1,"quantity":2}`, JSON parser thường có thể nhận nếu không bật tính năng kiểm tra duplicate. Đừng khẳng định luôn là syntax error. Quyết định chính sách trong API contract, parser settings và kiểm thử.

### 9.5. Validation sau khi controller được gọi

Nếu bạn dùng `Errors` hoặc `BindingResult` ngay sau `@Valid @RequestBody`, các Bean Validation error có thể đi vào method để bạn xử lý thủ công. Nhưng mapper chưa tạo được object thì controller vẫn không thể chạy. Tương tự, tự kiểm tra nghiệp vụ sau khi parse/validation (ví dụ tồn kho) là lỗi ở giai đoạn riêng: một input có thể hợp lệ về JSON, Java type và Bean Validation nhưng vẫn bị service từ chối.

## 10. Quy trình xử lý khi API báo HTTP 400

1. **Ghi lại HTTP method, URL, Content-Type và Accept** trước. Nếu status 415/406, xử lý header/media-type thay vì parser/DTO.
2. **Xác nhận body JSON chuẩn** bằng parser; kiểm tra dấu nháy, dấu phẩy, bracket, escape và encoding. Nếu parser lỗi, sửa producer JSON.
3. **Đối chiếu schema Java và payload**: object/array, number/string/boolean/null, ngày, enum, khóa thuộc tính, generic collection, nested structure; xem cấu hình coercion.
4. **Đọc constraint validation**: `@NotNull`, `@NotBlank`, `@Min`, nested `@Valid`, custom validators và validation groups.
5. **Xem exception chain tại server** để phân biệt parser và mapper, sau đó xử lý qua advice. Không công khai thông tin nhạy cảm của chain.
6. **Kiểm tra API error code**: `JSON_SYNTAX`, `JSON_TYPE`, `VALIDATION_FAILED`; cùng HTTP 400 nhưng hướng xử lý của client khác nhau.
7. **Viết regression test** với một case tối thiểu cho từng tầng, thêm các ranh giới như null, missing body, date/enum không đúng, Content-Type không phù hợp.

Đối với người mới, hãy đặt breakpoint vào `JsonErrorAdvice.handleHttpMessageNotReadable` và `handleMethodArgumentNotValid`. Hai đường đi này đủ để thấy sự khác biệt giữa **“object chưa được tạo”** và **“object đã được tạo nhưng vi phạm constraint”**.

## 11. Bài tự kiểm tra

1. JSON `{"age": 21,}` là loại lỗi gì? **Sai cú pháp** nếu parser tuân JSON chuẩn vì trailing comma.
2. JSON `{"age":"twenty"}` vào `Integer age` là lỗi gì? **Deserialization/type mismatch** theo Jackson thông thường; JSON vẫn đúng cú pháp.
3. JSON `{"age":17}` vào `Integer age` với `@Min(18)` là lỗi gì? **Validation**; không phải lỗi chuyển kiểu.
4. JSON `{"age":null}` vào `Integer age` với `@NotNull` có phải sai cú pháp không? **Không.** Null là literal JSON hợp lệ; `@NotNull` báo vi phạm constraint.
5. JSON `{"age":"21"}` chắc chắn sai kiểu không? **Không chắc.** Jackson có thể chuyển chuỗi số thành integer tùy chính sách coercion.
6. Vì sao `BindingResult` sau `@RequestBody` không bắt mọi lỗi JSON sai cú pháp? **Không có DTO đã deserialize thành công**, nên controller chưa có argument để tiếp nhận kết quả validation.
7. Ngoại lệ Spring bao ngoài JSON parser lỗi là gì? **`HttpMessageNotReadableException`**; cause có thể là `JsonParseException`.
8. Lỗi `@NotBlank` ở DTO `@Valid @RequestBody` không có `Errors` ngay sau thường được báo bằng gì? **`MethodArgumentNotValidException`**.
9. HTTP 415 có nói gì về syntax JSON? **Không nhất thiết.** Nó thường báo media type request không phù hợp với endpoint.
10. Khi enum có giá trị không tồn tại, tại sao không nên mặc định quy về validation? **Jackson có thể thất bại trước khi DTO được tạo**, đó là lỗi deserialization.
11. Vì sao API nên có `code` bên cạnh HTTP status? **Vì cùng HTTP 400 có nhiều giai đoạn lỗi và hướng xử lý khác nhau**.
12. Nếu DTO parse và validate thành công nhưng inventory service báo hết hàng, có phải lỗi JSON không? **Không.** Đó là quy tắc nghiệp vụ, cần hợp đồng lỗi và HTTP status riêng.

## 12. Nguồn tài liệu và điểm cần nhớ

- [Spring MVC — @RequestBody](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/requestbody.html): giải mã body qua `HttpMessageConverter` và xử lý `@Valid`/`Errors`.
- [Spring MVC — Validation](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html): `MethodArgumentNotValidException` và `HandlerMethodValidationException`.
- [Spring MVC — Message Converters](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html): cơ chế chọn converter từ media type.
- [Spring Framework 6.2 — ResponseEntityExceptionHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.8/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html): override các nhánh xử lý `HttpMessageNotReadableException` và `MethodArgumentNotValidException`.
- [Jackson Databind — MismatchedInputException](https://www.javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/2.17.2/com/fasterxml/jackson/databind/exc/MismatchedInputException.html): lỗi mapping/deserialize đầu vào.
- [Jakarta Bean Validation](https://jakarta.ee/specifications/bean-validation/3.0/): constraint validation trên Java object.

**Quy tắc để nhớ:** *Syntax* trả lời “JSON có đọc thành tài liệu được không?”; *type/deserialization* trả lời “tài liệu có biến thành object Java đích được không?”; *validation* trả lời “object đã tạo có thỏa hợp đồng dữ liệu không?”. Ba câu hỏi khác nhau có thể cùng dẫn tới HTTP 400, nên phải phân loại theo tầng xử lý và cung cấp phản hồi rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="http-message-bodies-008">RequestBodyAdvice, ResponseBodyAdvice và giới hạn biến đổi nội dung dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# RequestBodyAdvice, ResponseBodyAdvice và giới hạn biến đổi nội dung dùng chung trong Spring MVC

## 1. Bối cảnh: vì sao cần can thiệp vào HTTP body?

Khi nhiều endpoint Spring MVC có một yêu cầu biểu diễn giống nhau, ta có thể muốn **chỉnh dữ liệu request vừa được giải mã** hoặc **điều chỉnh response trước khi ghi ra HTTP** mà không lặp cùng một đoạn code ở mỗi controller. Ví dụ:

- Chuẩn hóa khoảng trắng ở một loại request DTO cụ thể trước validation.
- Thêm cấu trúc JSON `{"data": ...}` cho **một nhóm** API có hợp đồng response rõ ràng.
- Bổ sung metadata/header dựa trên chính sách áp dụng cho controller đã chọn.
- Cung cấp xử lý đặc thù cho một loại body mà `HttpMessageConverter` đã đọc được.

Spring Web MVC cung cấp hai extension points:

- **`RequestBodyAdvice`:** can thiệp vào quá trình đọc body thành object Java, trước khi object được chuyển cho controller.
- **`ResponseBodyAdvice<T>`:** can thiệp vào object trả về sau khi converter ghi response đã được chọn, nhưng trước khi object được converter serialize xuống response body.

Đây là các hook của **Spring MVC trên Servlet stack**. Spring WebFlux sử dụng pipeline/reactive codecs khác; không chuyển nguyên các class advice dưới đây sang WebFlux. Ví dụ đầy đủ dùng **Java 17, Spring Boot 3.3.5, Spring Framework 6.1.x**, để các import, phương thức và HTTP kết quả có ngữ cảnh phiên bản rõ ràng.

**Nguyên tắc cốt lõi:** Advice giúp chia sẻ xử lý tại một vị trí của MVC pipeline. Nó không tự biến mọi kiểu response thành JSON, không được thiết kế để đọc tất cả body của mọi loại request, và không phải chỗ thay thế cho authorization, Bean Validation, streaming, error mapping hay logic nghiệp vụ.

## 2. Đặt hai advice vào vòng đời Spring MVC

Một chu kỳ request JSON thông thường:

```text
HTTP request / JSON bytes
       |
       v
Servlet Filter(s) / DispatcherServlet
       |
       v
HandlerMapping chọn controller method
       |
       v
Argument resolver gặp @RequestBody
       |
       v
Chọn HttpMessageConverter đọc JSON
       |
       v
RequestBodyAdvice.supports(...)
       |
       +--> beforeBodyRead(HttpInputMessage) nếu có body
       |         |
       |         v
       |    HttpMessageConverter.read(...)
       |         |
       |         v
       +--> afterBodyRead(Java object)
       |
       +--> handleEmptyBody(...) nếu body rỗng
       |
       v
Bean Validation nếu tham số dùng @Valid/@Validated
       |
       v
Controller method và logic ứng dụng
       |
       v
Return-value handler chọn response media type và converter
       |
       v
ResponseBodyAdvice.supports(...)
       |
       v
ResponseBodyAdvice.beforeBodyWrite(...)
       |
       v
HttpMessageConverter.write(...)
       |
       v
HTTP response / bytes
```

Sơ đồ đơn giản hóa để học: resolver, converter và framework có nhiều nhánh theo media type, kiểu tham số và cách trả về. Với body malformed JSON, converter có thể ném exception **trước** `afterBodyRead`; với body rỗng, `handleEmptyBody` là đường riêng. Khi body không qua converter, advice tương ứng có thể hoàn toàn không chạy. <u>Không suy luận rằng một advice luôn chạy với mọi HTTP request/response.</u>

Ví dụ controller nhận dữ liệu:

```java
@PostMapping("/notes")
@ResponseBody
public NoteView create(@Valid @RequestBody NoteInput input) {
    // ...
}
```

Đây là method minh họa. `RequestBodyAdvice` có thể xử lý `NoteInput` **sau JSON deserialization, trước validation**. `ResponseBodyAdvice` có thể xử lý `NoteView` **trước JSON serialization**. Nếu controller ném exception, luồng xuất response phụ thuộc exception resolver/handler; không mặc định đi theo kết quả return bình thường của method này.

## 3. RequestBodyAdvice: trách nhiệm và thứ tự các hook

Giao diện đầy đủ thuộc package `org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice`. Các method tiêu biểu của Spring Framework 6.1:

```java
boolean supports(
    MethodParameter methodParameter,
    Type targetType,
    Class<? extends HttpMessageConverter<?>> converterType);

HttpInputMessage beforeBodyRead(
    HttpInputMessage inputMessage,
    MethodParameter parameter,
    Type targetType,
    Class<? extends HttpMessageConverter<?>> converterType) throws IOException;

Object afterBodyRead(
    Object body,
    HttpInputMessage inputMessage,
    MethodParameter parameter,
    Type targetType,
    Class<? extends HttpMessageConverter<?>> converterType);

Object handleEmptyBody(
    Object body,
    HttpInputMessage inputMessage,
    MethodParameter parameter,
    Type targetType,
    Class<? extends HttpMessageConverter<?>> converterType);
```

Các chữ ký trên là **trích mẫu API**, cần imports và nằm trong class triển khai interface để biên dịch. `RequestBodyAdviceAdapter` đã có implementations mặc định cho ba hook cuối, chỉ cần implement `supports` và override những hook muốn dùng.

### 3.1. supports: bộ lọc phạm vi đầu tiên

`supports(methodParameter, targetType, converterType)` được hỏi trước khi advice áp dụng. Nó cung cấp:

- `methodParameter`: vị trí tham số trong controller, dùng kiểm tra custom annotation hoặc kiểu tham số.
- `targetType`: kiểu Java thực sự được converter đọc; có thể khác parameter type (ví dụ `HttpEntity<NoteInput>`).
- `converterType`: class của converter mà Spring đã chọn.

Nếu trả `false`, advice bỏ qua tham số đó. Nếu trả `true`, các hook thích hợp có cơ hội chạy. Nên **lọc ít nhất theo endpoint/annotation + kiểu dữ liệu + converter** khi định thay đổi object; điều này bảo vệ những API không cùng hợp đồng dữ liệu.

```java
@Override
public boolean supports(
        MethodParameter parameter, Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType) {
    return parameter.hasParameterAnnotation(NormalizeNote.class)
            && targetType == NoteController.NoteInput.class
            && MappingJackson2HttpMessageConverter.class
                    .isAssignableFrom(converterType);
}
```

Đây là đoạn từ ví dụ hoàn chỉnh bên dưới. Nó chỉ opt-in khi có `@NormalizeNote`, kiểu đích đúng `NoteInput`, và converter tương thích Jackson JSON. Không dùng điều kiện `return true` toàn cục rồi ép mọi `Object` sang cùng một DTO.

### 3.2. beforeBodyRead: trước lúc converter đọc bytes

`beforeBodyRead` nhận `HttpInputMessage` chứa headers + `InputStream` request. Hook có thể trả nguyên `inputMessage` hoặc **một wrapper** hợp lệ cung cấp body stream khác. Mọi thay đổi bytes có thể gây hệ quả:

- Nếu chỉnh raw JSON, phải giữ JSON hợp lệ và thống nhất encoding/charset, media type.
- Không thể giả định stream đọc lại được: input stream thường chỉ tiêu thụ một lần.
- Nếu thay byte length, `Content-Length` cũ không còn đúng; headers/content encoding/integrity signature liên quan cũng phải được cân nhắc.
- Giải mã payload bảo mật, giải nén hoặc kiểm tra checksum là vấn đề protocol/security cần thiết kế rõ ràng; không nên thêm bằng advice chỉ vì có sẵn hook.
- Việc đọc toàn bộ body vào RAM để biến đổi có rủi ro về dung lượng/DoS; cần giới hạn request ở tầng phù hợp.

Trong đa số API JSON thông thường, xử lý **object sau conversion** bằng `afterBodyRead` dễ kiểm chứng hơn thao tác lại bytes trước Jackson.

### 3.3. afterBodyRead: sau converter, trước controller

`afterBodyRead` nhận đối tượng đã được converter parse thành công. Bạn có thể trả nguyên object hoặc trả một object khác **tương thích với kiểu tham số controller**. Ví dụ tạo record mới từ một record cũ với `text.strip()`, rồi để Bean Validation kiểm tra giá trị mới.

Điểm mấu chốt: nếu input `"   "`, sau khi trim thành `""`, `@NotBlank` mới báo vi phạm. Điều này là một chính sách **thay đổi nghĩa của input**, vì vậy phải được công khai trong API contract và chỉ áp dụng có chủ đích. Không tự ý thay tất cả các text fields của mọi DTO — khoảng trắng có thể có nghĩa trong mật khẩu, chữ ký, truy vấn tìm kiếm hoặc nội dung người dùng.

Nếu converter không parse được JSON thì không có object hợp lệ để truyền vào `afterBodyRead`. Trường hợp này thường dẫn tới `HttpMessageNotReadableException` và HTTP `400` theo cấu hình phổ biến.

### 3.4. handleEmptyBody: request body thực sự rỗng

`handleEmptyBody` được gọi trên nhánh body rỗng của request được xử lý qua body reader. Default adapter trả nguyên body (thường là `null`). Nếu tham số `@RequestBody` là bắt buộc, kết quả `null` có thể khiến Spring báo thiếu body. Không xem JSON `{}`, JSON `null`, request không có body, và JSON lỗi cú pháp là một tình huống: chúng không nhất thiết kích hoạt cùng hook.

Nếu hợp đồng API quy định body là bắt buộc, giải pháp nhất quán thường là **trả lỗi client**, không bí mật tạo object mặc định từ body rỗng. Chỉ tự cấp default khi endpoint thật sự công bố điều đó, và kiểm chứng interaction với `@RequestBody(required = true/false)`.

### 3.5. Đường dữ liệu được hỗ trợ

Javadoc mô tả `RequestBodyAdvice` cho các tham số được xử lý qua `@RequestBody` hoặc `HttpEntity`. Không mặc định nó xử lý toàn bộ `@ModelAttribute`, query params, form binding, multipart stream hoặc những lần code tự gọi `request.getInputStream()`. `@RequestPart` có phần xử lý riêng; nếu cần chính sách ở multipart phải kiểm tra chính xác resolver và hành vi của phiên bản Spring đang dùng.

## 4. ResponseBodyAdvice<T>: can thiệp trước lúc ghi body

Giao diện `org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice<T>` có hai method chính:

```java
boolean supports(
    MethodParameter returnType,
    Class<? extends HttpMessageConverter<?>> converterType);

T beforeBodyWrite(
    T body,
    MethodParameter returnType,
    MediaType selectedContentType,
    Class<? extends HttpMessageConverter<?>> selectedConverterType,
    ServerHttpRequest request,
    ServerHttpResponse response);
```

Đây là chữ ký minh họa của API Spring MVC 6.1. Advice được dùng cho kết quả `@ResponseBody` và `ResponseEntity` khi MVC dùng converter để viết body; `@RestController` cũng sử dụng semantics `@ResponseBody`.

- `supports` xác định có nên áp dụng cho method return và converter đã chọn hay không.
- `beforeBodyWrite` nhận body và metadata **sau khi Spring chọn converter, trước khi gọi converter.write**. Bạn có thể trả nguyên body hoặc object khác; chỉ nên trả kiểu mà converter đã chọn vẫn serialize được.
- `selectedContentType` đã phản ánh negotiation cho response; advice không nên tùy tiện giả định JSON khi actual response là text/binary.
- `response.getHeaders()` cho phép bổ sung header liên quan response, nhưng việc thay body và headers phải nhất quán.

### Vì sao “wrap mọi response bằng {data: ...}” có thể hỏng?

Giả sử method trả `String`:

```java
@GetMapping(path = "/ping", produces = "text/plain")
public String ping() {
    return "pong";
}
```

Spring có thể chọn `StringHttpMessageConverter`. Nếu `ResponseBodyAdvice<Object>` thay chuỗi bằng `new Envelope<>("pong")`, converter **đã được chọn từ trước** vẫn có thể kỳ vọng nhận `String`. Hậu quả có thể là `ClassCastException`, lỗi ghi response hoặc body sai định dạng. Advice **không tự chạy lại thuật toán chọn converter sau khi bạn đổi object**.

Vì vậy, nên kiểm tra đồng thời:

1. **Hợp đồng endpoint** (annotation, package/controller, return type).
2. **Loại converter** (`MappingJackson2HttpMessageConverter` nếu wrap DTO JSON).
3. **Loại body thực tế** trước khi sửa.
4. **Response media type**; không thay đổi ngoài các representation đã cam kết.
5. **Body null/empty, status và headers** để không phá những response không có nội dung.

Kể cả khi converter hỗ trợ object mới, việc bọc `{"data": ...}` cũng **là thay đổi công khai về JSON schema**. Client và OpenAPI schema phải biết trước.

### Tương quan với ResponseEntity

`ResponseEntity<T>` cho phép controller đặt status/headers/body. `ResponseBodyAdvice` có thể can thiệp body khi return-value handler ghi body qua converter, nhưng điều đó **không có nghĩa** advice được quyền đổi ngầm status `201` thành `200`, thay `Content-Type` sang tùy ý hoặc sinh body trên status `204 No Content`. Các trường hợp `ResponseEntity<Void>`, `HEAD`, `304`, download file, binary streams hay byte ranges cần xem là ngoại lệ rõ ràng đối với một chính sách wrap JSON.

## 5. So sánh hai advice

| Khía cạnh | `RequestBodyAdvice` | `ResponseBodyAdvice` |
| --- | --- | --- |
| Thời điểm | Trước/sau converter **đọc request** | Sau khi chọn converter **ghi response**, trước thao tác ghi |
| Đầu vào chính | `HttpInputMessage` hoặc object đã deserialize | Object controller trả về, headers/selected media type |
| Lọc phạm vi | `supports(MethodParameter, Type, converterType)` | `supports(returnType, converterType)` |
| Hook cần nhớ | `beforeBodyRead`, `afterBodyRead`, `handleEmptyBody` | `beforeBodyWrite` |
| Use case phù hợp | Chuẩn hóa DTO cụ thể đã được parse | Wrap DTO JSON có chủ đích, bổ sung headers |
| Rủi ro tiêu biểu | Đọc stream, thay input trước validation, thay nghĩa dữ liệu | Thay return type sau chọn converter, thay API schema |
| Không thay thế được | Authorization, validation, giải quyết mọi kiểu form/multipart | Exception mapping toàn cục, negotiation, serializer cho mọi format |

**Hai advice hoạt động theo hướng khác nhau.** Request advice không phải “filter mọi inbound bytes”, response advice không phải “filter mọi outbound bytes”. Dùng hook đúng đối tượng giảm rủi ro khó phát hiện.

## 6. Ví dụ chạy được: opt-in normalization và JSON envelope

Ví dụ có ba endpoint:

- `POST /api/notes/normalized`: chỉ endpoint này **opt-in** để strip whitespace ở `NoteInput.text` trước `@Valid`; response được wrap thành `{"data":{"text":"..."}}`.
- `POST /api/notes/plain`: JSON input/output bình thường, không qua chính sách normalization/wrapping của hai advice ví dụ.
- `GET /api/text`: text response qua `StringHttpMessageConverter`, giữ nguyên text; không wrap.

Chính sách cố tình được áp dụng hẹp. Một endpoint mới không nên bỗng thay đổi JSON hay input chỉ vì dùng chung `@RestController`.

### 6.1. Cấu trúc project

```text
mvc-body-advice-demo/
├── pom.xml
└── src/main/java/example/bodyadvice/
    ├── BodyAdviceApplication.java
    ├── NormalizeNote.java
    ├── WrapData.java
    ├── NoteController.java
    ├── NormalizeNoteAdvice.java
    └── WrapDataAdvice.java
```

Các class cùng package `example.bodyadvice`, vì vậy dùng type từ file khác trong package không cần import riêng.

**`pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>example</groupId>
    <artifactId>mvc-body-advice-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
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
```

**`src/main/java/example/bodyadvice/BodyAdviceApplication.java`**

```java
package example.bodyadvice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BodyAdviceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BodyAdviceApplication.class, args);
    }
}
```

### 6.2. Hai annotation chỉ rõ endpoint muốn dùng chính sách nào

**`NormalizeNote.java`**

```java
package example.bodyadvice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface NormalizeNote {}
```

**`WrapData.java`**

```java
package example.bodyadvice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WrapData {}
```

`@NormalizeNote` đặt trên **tham số** cần biến đổi; `@WrapData` đặt trên **method** cho phép wrap response. Runtime retention cần thiết vì Spring dùng reflection/metadata của handler method để phát hiện các annotation này.

### 6.3. Controller: hai contract JSON khác nhau, một endpoint text

**`NoteController.java`**

```java
package example.bodyadvice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NoteController {

    public record NoteInput(@NotBlank String text) {}

    public record NoteOutput(String text) {}

    @PostMapping(path = "/notes/normalized",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @WrapData
    public NoteOutput normalized(
            @NormalizeNote @Valid @RequestBody NoteInput request) {
        return new NoteOutput(request.text());
    }

    @PostMapping(path = "/notes/plain",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public NoteOutput plain(
            @Valid @RequestBody NoteInput request) {
        return new NoteOutput(request.text());
    }

    @GetMapping(path = "/text",
                produces = MediaType.TEXT_PLAIN_VALUE)
    public String text() {
        return "ready";
    }
}
```

`@NotBlank` kiểm tra chuỗi có nội dung không chỉ là whitespace. Controller không tự trim input. Biến đổi được đặt trong request advice, chỉ kích hoạt khi có `@NormalizeNote`; `@WrapData` cũng chỉ gắn vào endpoint cần envelope. Không có database, authentication hay state: response phản ánh input.

### 6.4. Request advice: sau Jackson, trước validation

**`NormalizeNoteAdvice.java`**

```java
package example.bodyadvice;

import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

@ControllerAdvice(assignableTypes = NoteController.class)
public class NormalizeNoteAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {

        return parameter.hasParameterAnnotation(NormalizeNote.class)
                && targetType == NoteController.NoteInput.class
                && MappingJackson2HttpMessageConverter.class
                        .isAssignableFrom(converterType);
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {

        if (body instanceof NoteController.NoteInput note) {
            String text = note.text();
            return new NoteController.NoteInput(
                    text == null ? null : text.strip());
        }
        return body;
    }
}
```

Các chi tiết:

- `@ControllerAdvice(assignableTypes = NoteController.class)` giới hạn controller mà advice nhắm tới. Trong `supports` còn có hai lớp giới hạn: marker annotation và loại converter/DTO.
- `RequestBodyAdviceAdapter` giữ nguyên `beforeBodyRead` và `handleEmptyBody` mặc định; ví dụ chỉ thay đổi object khi Jackson đã deserialize thành công.
- `String.strip()` (Java 11+) loại bỏ các ký tự whitespace ở đầu/cuối theo `Character.isWhitespace`. Nó **không** giải quyết Unicode normalization tổng quát, không sanitize HTML và không thay thế kiểm tra bảo mật.
- Khi `text=null`, giữ nguyên null để `@NotBlank` xử lý; không lặng lẽ gán chuỗi mặc định.
- Advice trả **một `NoteInput` mới**, cùng kiểu controller đang yêu cầu, không trả envelope hoặc object tùy ý gây lỗi ép kiểu.
- Lỗi JSON syntax xảy ra ở converter nên không mong `afterBodyRead` được gọi để “sửa” mọi JSON hỏng.

### 6.5. Response advice: chỉ bọc JSON DTO có marker

**`WrapDataAdvice.java`**

```java
package example.bodyadvice;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice(assignableTypes = NoteController.class)
public class WrapDataAdvice implements ResponseBodyAdvice<Object> {

    public record Envelope<T>(T data) {}

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {

        return returnType.hasMethodAnnotation(WrapData.class)
                && MappingJackson2HttpMessageConverter.class
                        .isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        if (body == null) {
            return null;
        }
        response.getHeaders().add("X-Envelope-Applied", "true");
        return new Envelope<>(body);
    }
}
```

**Vì sao ví dụ này an toàn trong phạm vi minh họa?** Endpoint đánh dấu `@WrapData` chỉ trả `NoteOutput` được Jackson serialize dưới JSON; body mới là `Envelope<NoteOutput>`, cũng được Jackson xử lý. Chúng ta không bọc text, bytes, `Resource` hoặc response không có body. Trong ứng dụng thật, nên còn kiểm tra rằng kiểu body thuộc một tập DTO/response contract đã duyệt để tránh vô tình bọc loại return mới nếu developer thêm `@WrapData` nhầm chỗ.

`beforeBodyWrite` có thể thay object, nhưng **không thay được kết quả chọn converter đã xảy ra trước đó**. Khi body null, hook giữ nguyên null; nó không cố tạo `{"data":null}` trong trường hợp response có chủ đích không có body. Header `X-Envelope-Applied` chỉ được thêm khi body thực sự được bọc.

### 6.6. Chạy ứng dụng

Mở terminal tại thư mục `mvc-body-advice-demo` có `pom.xml`:

```bash
mvn spring-boot:run
```

Yêu cầu JDK 17+ và Maven hoạt động. Spring Boot mặc định mở `http://localhost:8080` nếu không có cấu hình thay đổi cổng.

**Gửi input JSON với khoảng trắng và marker opt-in:**

```bash
curl -i -X POST http://localhost:8080/api/notes/normalized \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"text":"  Hello MVC  "}'
```

Các phần quan trọng của response:

```http
HTTP/1.1 201
Content-Type: application/json
X-Envelope-Applied: true

{"data":{"text":"Hello MVC"}}
```

Giải thích theo thứ tự:

1. Jackson đọc JSON thành `new NoteInput("  Hello MVC  ")`.
2. `NormalizeNoteAdvice.supports` đúng vì tham số có `@NormalizeNote` và converter JSON phù hợp.
3. `afterBodyRead` trả `new NoteInput("Hello MVC")`.
4. `@Valid` kiểm tra `@NotBlank` trên giá trị đã được strip.
5. Controller trả `new NoteOutput("Hello MVC")` với status `201`.
6. `WrapDataAdvice.supports` đúng vì method có `@WrapData` và converter writer là Jackson.
7. `beforeBodyWrite` trả `new Envelope<>(NoteOutput)` và bổ sung response header.
8. Jackson ghi JSON có field `data`.

**So sánh endpoint không opt-in:**

```bash
curl -i -X POST http://localhost:8080/api/notes/plain \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"text":"  Hello MVC  "}'
```

Kết quả chính:

```http
HTTP/1.1 200
Content-Type: application/json

{"text":"  Hello MVC  "}
```

Không có `X-Envelope-Applied` do advice không bọc response; khoảng trắng được giữ nguyên do tham số không gắn `@NormalizeNote`.

**Endpoint text không bị biến đổi:**

```bash
curl -i http://localhost:8080/api/text -H 'Accept: text/plain'
```

Response body:

```text
ready
```

Ở đây `StringHttpMessageConverter` có thể được chọn. Vì method không có `@WrapData` và media type là text, chương trình không wrap text thành JSON. Đây là kiểm tra quan trọng chống lỗi chọn converter.

**Validation chạy trên dữ liệu đã chuẩn hóa:**

```bash
curl -i -X POST http://localhost:8080/api/notes/normalized \
  -H 'Content-Type: application/json' \
  -d '{"text":"   "}'
```

`afterBodyRead` trả `NoteInput("")`, sau đó `@NotBlank` không đạt; response thường là `400 Bad Request`. Không thể kết luận body lỗi mặc định cũng có `data`: annotation `@WrapData` áp dụng cho return của handler được chọn, còn exception response có pipeline xử lý riêng.

**JSON malformed:**

```bash
curl -i -X POST http://localhost:8080/api/notes/normalized \
  -H 'Content-Type: application/json' \
  --data-binary '{"text":'
```

Converter không thể parse nội dung; thường trả `400`. Advice `afterBodyRead` không có DTO hợp lệ để sửa. Header lỗi/JSON body thực tế tùy cấu hình của Spring Boot và exception handlers.

Trên PowerShell, có thể thử bằng `Invoke-RestMethod`:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/notes/normalized' -ContentType 'application/json' -Body '{"text":"  Hello MVC  "}'
```

PowerShell hiển thị đối tượng JSON đã parse theo kiểu PowerShell; muốn quan sát nguyên status/header/body, dùng công cụ HTTP cho phép xem raw response.

## 7. Những giới hạn quan trọng của biến đổi nội dung dùng chung

### 7.1. Phạm vi không tự động bao trùm mọi request

`RequestBodyAdvice` gắn với body được resolver/converter xử lý như `@RequestBody` và `HttpEntity`. Nó không phải Servlet `Filter` đọc mọi request, không xử lý mặc định query string, path variables, session state, toàn bộ HTML form binding hay body do controller đọc thủ công. Việc có thể dùng advice trong một số resolver khác phải kiểm tra chính xác theo phiên bản và implementation; không lấy một kết quả quan sát được rồi hứa tính toàn cục.

Nếu cần áp dụng cho **raw HTTP requests trước MVC routing**, hãy đánh giá Servlet `Filter`. Nhưng Filter cũng có rủi ro tiêu thụ stream và thứ tự filter; không tự đọc body vô hạn để log/biến đổi.

### 7.2. Response advice không bảo đảm chạy với mọi kiểu return

Response body phải đi qua một MVC return-value handler có message conversion phù hợp. Một controller render Thymeleaf/JSP view theo `ModelAndView` không đi theo cùng tuyến trả JSON. File streaming, `StreamingResponseBody`, `SseEmitter`, `ResponseBodyEmitter`, WebSocket, response bị commit sớm, response không có body và servlet tự ghi output cũng có vòng đời khác.

Nếu contract yêu cầu thống nhất nội dung cho từng loại transport, hãy tạo policy riêng phù hợp transport đó. Đừng ép một `ResponseBodyAdvice<Object>` bao trùm cả tải tệp, SSE, binary và HTML view.

### 7.3. Chọn converter xảy ra trước response advice

`ResponseBodyAdvice.beforeBodyWrite` được gọi **sau khi** Spring đã xác định converter. Khi advice đổi `String` thành `Map` hoặc DTO, converter được chọn từ `String` có thể không serialize được kiểu mới. Lỗi có thể phát sinh ngay cả khi JSON mapper tồn tại trên classpath. Điều kiện `supports` cần kiểm tra converter, return annotation/type, và loại body mới có tương thích không.

Với JSON API muốn một envelope ổn định, cách dễ bảo đảm hợp đồng hơn là **controller/service trả chính kiểu `ApiResponse<T>`** theo contract, hoặc dùng advice chỉ cho endpoints đã khai báo rõ. Nếu dùng streaming/binary, giữ response type và converter ban đầu.

### 7.4. Media type, headers và payload phải nhất quán

Biến đổi bytes hoặc object có thể làm thay đổi representation. Cần xét:

- `Content-Type` và charset có phù hợp body mới?
- `Content-Length` cũ có còn đúng nếu thay bytes? Có phải để framework/container tính lại thay vì giữ một giá trị stale?
- `Content-Encoding`, compression và checksums có cần điều chỉnh?
- `ETag`, `Last-Modified`, cache validators, response caching có còn phản ánh nội dung mới?
- Response chứa chữ ký số hay HMAC trên **raw bytes** thì biến đổi trước/sau ký có phá tính toàn vẹn không?
- Có thay đổi JSON schema mà client, mobile apps, OpenAPI và tests chưa biết?

Một thay đổi “nhỏ” như thêm envelope có thể phá ETag hoặc response clients đang parse các fields ở top-level. Nếu byte transformation là bắt buộc, hãy thiết kế một tầng có trách nhiệm với headers, streaming và integrity; không chỉ sửa body bằng hook.

### 7.5. Security: không dùng advice để thay authorization

Advice có thể normalize/sanitize một tập DTO đã chọn, nhưng không nên âm thầm thay đổi identity, tenant, permission hay ownership mà service không kiểm chứng. Đặc biệt:

- Không tự ghi log body chứa password, access token, session, PII hay thông tin tài chính.
- Đừng trông chờ normalization toàn cục biến dữ liệu nguy hiểm thành an toàn. HTML sanitization, SQL parameterization, path safety và output encoding có quy tắc khác nhau.
- Nếu input cần raw signature verification, việc trim/normalize trước xác minh có thể làm chữ ký sai. Sửa bytes sau xác minh cũng có thể làm khác payload đã được ký.
- Không bọc lỗi authentication/authorization thành HTTP `200 OK` chỉ để hợp một dạng `{"data": ...}`.
- Server vẫn cần input DTO giới hạn field, Bean Validation và authorization trên resource cụ thể.

### 7.6. Validation, exception và error responses

Request advice có thể đổi object **trước validation**, vì vậy validator nhìn thấy giá trị sau biến đổi. Đây là thứ tự quan trọng khi test. Tuy nhiên, advice không thay thế `@Valid`, custom validators hoặc business rules.

`@ControllerAdvice` dùng cho body advice cũng có thể là nơi chứa `@ExceptionHandler`, nhưng đó là **hai cơ chế khác nhau**. Response lỗi do handler/`ProblemDetail`, exception resolver hoặc `/error` sinh ra; không mặc định chịu cùng điều kiện wrap như success body. Nếu muốn error contract thống nhất, thiết kế `@RestControllerAdvice` / `ResponseEntityExceptionHandler` riêng, giữ status đúng và tránh double wrapping.

### 7.7. Performance, memory và concurrency

Các advice thường là Spring singleton beans. Không lưu thông tin request đang xử lý vào mutable instance fields vì nhiều request có thể chạy đồng thời. Nếu cần dữ liệu request-scoped, dùng method arguments, `request.getHeaders()`, scoped components hoặc request attributes phù hợp.

`beforeBodyRead` đọc toàn bộ stream có thể tăng RAM và latency cho mọi request. `ResponseBodyAdvice` tạo cây object mới cho response lớn cũng tăng allocations. Với payload lớn, ưu tiên DTO/streaming/codec phù hợp và đặt size limits. Không tạo wrapper liên tiếp khi nhiều advice có cùng nhiệm vụ, tránh `{"data":{"data":...}}`.

### 7.8. Thứ tự nhiều advice và idempotency

Khi có nhiều `@ControllerAdvice` / `RequestBodyAdvice` / `ResponseBodyAdvice`, thứ tự có thể liên quan `@Order` và cơ chế sorting của Spring. Thiết kế sao cho các biến đổi độc lập hoặc khai báo order rõ ràng khi thật sự cần. Phải test **thứ tự thực tế** trong phiên bản sử dụng; không giả định thứ tự quét classpath cố định.

Nên làm biến đổi **idempotent** nếu có khả năng đi qua cùng policy nhiều lần. Với envelope JSON, trước khi wrap có thể kiểm tra kiểu `ApiEnvelope` hoặc giới hạn chỉ một marker/handler. Với request normalization, `strip` có tính idempotent theo thao tác đó nhưng không có nghĩa chuỗi input ban đầu được bảo toàn — hợp đồng cần nói rõ.

### 7.9. Null body, HEAD, 204 và 304

Nếu trả `null` hoặc một response không có body, việc cố thêm envelope có thể đi ngược ngữ nghĩa HTTP. Đặc biệt `204 No Content` và `304 Not Modified` không phải response dùng để gửi JSON payload; `HEAD` chỉ có metadata như tương ứng GET, không truyền response body. Hãy tôn trọng status/method và các output paths đặc biệt.

Hook advice có thể vẫn được xét trong một vài nhánh `null` tùy implementation, nhưng không nên dựa vào đó để tạo body cho trường hợp không có nội dung. Trong ví dụ, `if (body == null) return null` giúp giữ nguyên hợp đồng không body.

## 8. Khi nào nên dùng cơ chế khác?

| Bài toán cụ thể | Cơ chế thường phù hợp hơn |
| --- | --- |
| Đọc/ghi định dạng body mới (binary/protocol) | `HttpMessageConverter<T>` được giới hạn kiểu và media type |
| Tùy chỉnh cách Jackson serialize/deserialize JSON | Jackson `ObjectMapper`, custom serializer/deserializer, `@Json*` |
| Chỉ thêm chuẩn response cho một endpoint | Trả `ResponseEntity<ApiEnvelope<T>>` hoặc DTO envelope tường minh |
| Chuẩn hóa lỗi từ nhiều controller | `@RestControllerAdvice` + `@ExceptionHandler` / `ResponseEntityExceptionHandler` |
| Authentication, authorization | Spring Security filters và application service/policy |
| Cross-cutting tracing/headers quanh mọi HTTP request | Servlet `Filter` hoặc `HandlerInterceptor` tùy yêu cầu |
| Chỉ đọc request metadata và đặt request attributes | `HandlerInterceptor` hoặc scoped service |
| Binding query/form thành object | `@ModelAttribute` + `WebDataBinder`, converter/formatter/validator |
| Streaming download/upload | `Resource`, multipart support, `StreamingResponseBody` hoặc I/O riêng |
| Áp dụng envelope cho một nhóm JSON API | `ResponseBodyAdvice` với opt-in và converter guard, sau khi định nghĩa schema |
| Đổi object sau JSON parse trước `@Valid` | `RequestBodyAdvice.afterBodyRead`, chỉ với DTO/chính sách rõ ràng |

Quyết định dựa trên **mục đích và thời điểm cần can thiệp**: raw bytes trước routing, object sau conversion, response DTO trước serialization, hay exception response. Mỗi vị trí có metadata và giới hạn riêng.

## 9. Các lỗi thường gặp và cách điều tra

| Hiện tượng | Nguyên nhân khả dĩ | Hướng kiểm tra |
| --- | --- | --- |
| `afterBodyRead` không chạy | `supports=false`, controller không dùng `@RequestBody`/`HttpEntity`, body rỗng hoặc converter ném lỗi | Kiểm tra tham số, converter, marker, nhánh `handleEmptyBody` và exception |
| Advice không được Spring phát hiện | Thiếu `@ControllerAdvice`, package ngoài component scan, scope assignableTypes sai | Kiểm tra annotation, component scan, handler class |
| `ClassCastException` lúc ghi response | `ResponseBodyAdvice` đổi body sang kiểu không phù hợp converter đã chọn | Kiểm tra `selectedConverterType`, `selectedContentType` và return type |
| JSON vẫn bị lỗi `400` trước controller | Jackson không parse được, body thiếu, validation không đạt | Phân biệt conversion exception với constraint violations |
| Text bị wrap thành object | `supports` trả true quá rộng, thiếu filter converter/marker | Giới hạn vào Jackson JSON DTO endpoints |
| Endpoint khác đột nhiên đổi JSON schema | Global advice áp dụng lan rộng | Dùng opt-in annotation, `@ControllerAdvice` scope và contract tests |
| Response đã bị wrap hai lần | Nhiều advice hoặc xử lý ở controller lẫn advice | Chỉ một nơi sở hữu envelope, kiểm tra wrapper type |
| Headers sai sau đổi payload | `Content-Type`, length, encoding, ETag không tương thích | Không thay raw bytes tùy tiện; kiểm chứng response wire format |
| Upload lớn làm server hết bộ nhớ | `readAllBytes()`, buffering body toàn cục | Giới hạn kích thước và chọn streaming/multipart đúng nhu cầu |
| Header advice xuất hiện nhưng response không đúng | Exception/đã commit response hoặc converter write fail | Kiểm tra stack trace và thời điểm write/commit |
| Validation không thấy value gốc | `afterBodyRead` đã mutate/thay DTO | Contract hóa normalization, test dữ liệu trước và sau |
| Response lỗi bị đổi thành HTTP 200 | Global envelope logic can thiệp sai status/exception | Giữ error contract và HTTP status, tách exception handling |

Một quy trình điều tra thực tế:

1. Ghi lại request `method`, URL, `Content-Type`, `Accept` và response status; không log secrets.
2. Xác nhận mapping đã khớp và argument thật sự đi qua converter.
3. Xem `supports` của advice request/response và loại converter được chọn.
4. Xem converter đã parse thành object hay ném lỗi; kiểm tra Bean Validation sau đó.
5. Đối chiếu object controller trả về với object advice giao cho converter.
6. Kiểm tra raw response headers/body, kể cả body rỗng, binary, `204` và error response.
7. Test các endpoint **không opt-in** để phát hiện ảnh hưởng ngoài phạm vi.

## 10. Tiêu chí thiết kế một advice dùng chung

Trước khi đưa một advice vào ứng dụng có nhiều module/controller, xác nhận:

- **Contract:** API nào đã công bố input normalization hoặc output envelope?
- **Scope:** annotation/controller/DTO/media type nào opt-in? Có đủ loại trừ text, HTML, file và streaming?
- **Converter:** body trước và sau biến đổi có còn tương thích converter được chọn?
- **Validation:** thay đổi diễn ra trước hay sau constraint checks; test giá trị rỗng, null, sai định dạng?
- **Error path:** có giữ đúng status và schema của validation errors, authorization errors, server errors?
- **Transport:** có giữ headers, charset, content length, cache validators và chữ ký?
- **Observability:** có đo được lỗi và latency mà không ghi payload nhạy cảm?
- **Concurrency:** advice có stateless hoặc xử lý state an toàn?
- **Regression tests:** có test endpoint nhận/không nhận policy, JSON/string/binary/null, cả success và failure?
- **Ownership:** một nơi chịu trách nhiệm normalize/wrap, tránh controller và advice cùng làm.

Không nhất thiết phải triển khai tất cả các loại hook. Trong nhiều hệ thống, **DTO tường minh + validation + controller trả `ResponseEntity<ApiResponse<T>>`** đã đủ, dễ quan sát và hợp đồng rõ. Dùng advice khi lợi ích tái sử dụng thật sự lớn hơn chi phí thay đổi ngầm ở một bước framework.

## 11. Câu hỏi tự kiểm tra

1. `RequestBodyAdvice.afterBodyRead` chạy trước hay sau JSON deserialization? Trước hay sau `@Valid` thông thường?
2. Body bị thiếu và JSON malformed có luôn gọi `afterBodyRead` không? Vì sao?
3. Vì sao điều kiện `ResponseBodyAdvice.supports` chỉ dựa vào `@RestController` là quá rộng?
4. Spring có chọn lại `HttpMessageConverter` sau khi `beforeBodyWrite` trả về một object loại khác không?
5. Với method trả `String` dùng `StringHttpMessageConverter`, vì sao wrap thành một record `Envelope<String>` có thể thất bại?
6. Khi nào cần `Filter` thay vì `RequestBodyAdvice`?
7. Nếu API trả `204 No Content`, có nên ép `{"data":null}` vào response không?
8. Nếu request chứa chữ ký HMAC cho raw bytes, việc trim trước kiểm tra chữ ký ảnh hưởng thế nào?
9. Vì sao `@ControllerAdvice` dùng cho xử lý body không tự động chuẩn hóa response của tất cả `@ExceptionHandler`?
10. Vì sao phải test cả endpoint không được áp dụng advice khi triển khai chính sách mới?

**Định hướng:** (1) Sau deserialize, trước validation. (2) Body rỗng theo `handleEmptyBody`; malformed JSON có thể fail trước `afterBodyRead`. (3) Vì có JSON, string, binary, streaming với converter/contract khác nhau. (4) Không dựa vào việc chạy lại lựa chọn converter. (5) Converter có thể kỳ vọng `String`. (6) Khi cần xử lý HTTP raw ở tầng Servlet trước pipeline MVC thích hợp. (7) Không, phải tôn trọng status không có body. (8) Raw bytes bị thay nên chữ ký không còn tương ứng. (9) Exception có return handler, status và contract riêng. (10) Để phát hiện việc advice vô tình thay đổi API khác.

## 12. Tài liệu tham khảo

- [Spring Framework 6.1 — RequestBodyAdvice Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/RequestBodyAdvice.html)
- [Spring Framework 6.1 — RequestBodyAdviceAdapter Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/RequestBodyAdviceAdapter.html)
- [Spring Framework 6.1 — ResponseBodyAdvice Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.html)
- [Spring Framework 6.1 — RequestResponseBodyMethodProcessor Javadoc](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/RequestResponseBodyMethodProcessor.html)
- [Spring Framework — @RequestBody](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html)
- [Spring Framework — @ResponseBody](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responsebody.html)
- [Spring Framework — @ControllerAdvice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html)
- [Spring Framework — HTTP Message Converters](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html)

Bài học này mô tả cơ chế cho Spring MVC 6.1 với API chạy ví dụ đã cố định bằng Spring Boot 3.3.5. Khi nâng major version Spring, cần kiểm tra chữ ký và pipeline tương ứng trước khi sao chép advice vào sản phẩm.


</details>

- [Quay lại đầu trang](#back-to-top)
