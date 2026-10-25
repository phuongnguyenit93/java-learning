<a id="back-to-top"></a>

# Tệp và tài nguyên

## Menu
- [MultipartResolver và cấu hình multipart của Servlet container](#file-resources-001)
- [Nhận MultipartFile, Part và kết hợp tệp với dữ liệu JSON](#file-resources-002)
- [Giới hạn dung lượng, số tệp, kiểu tệp và tên tệp không tin cậy](#file-resources-003)
- [StreamingResponseBody, Resource và Content-Disposition khi tải xuống](#file-resources-004)
- [Range request, ResourceRegion và phản hồi 206 Partial Content](#file-resources-005)
- [ResourceHandler, vị trí tài nguyên tĩnh và thứ tự ánh xạ](#file-resources-006)
- [Resource chain, phiên bản tài nguyên và đường dẫn có dấu vân tay nội dung](#file-resources-007)
- [Ngăn path traversal và kiểm soát quyền truy cập tệp](#file-resources-008)

## <a id="file-resources-001">MultipartResolver và cấu hình multipart của Servlet container</a>

<details>
<summary>Xem chi tiết</summary>

# MultipartResolver và cấu hình multipart của Servlet container trong Spring MVC

## 1. Mục tiêu

Khi client gửi form có file (ảnh, tài liệu, CSV...) đến một endpoint Spring MVC, request thường có `Content-Type: multipart/form-data`. Để `@RequestParam MultipartFile` hoặc `@RequestPart` nhận được dữ liệu, không chỉ controller mà cả **Servlet container** và cơ chế **`MultipartResolver`** phải phối hợp đúng.

Sau bài này, bạn có thể:

1. Đọc cấu trúc multipart, phân biệt **HTTP part**, `jakarta.servlet.http.Part`, `MultipartFile` và `MultipartHttpServletRequest`.
2. Giải thích chính xác việc **`DispatcherServlet` nhận diện resolver mang bean name `multipartResolver`** và **Servlet container thực hiện phân tích body**.
3. Cấu hình kích thước, thư mục tạm, ngưỡng ghi đĩa bằng **Servlet multipart config**, không nhầm với phương thức của `StandardServletMultipartResolver`.
4. Dùng `spring.servlet.multipart.*` trên **Spring Boot 3.x**, biết điều gì đã được auto-configured và khi nào cần bean `StandardServletMultipartResolver` tự khai báo.
5. Phân biệt `resolveLazily` và `strictServletCompliance` cùng tác động đến thời điểm phát sinh lỗi và tính portable.
6. Xây dựng ứng dụng Java 17+ có endpoint upload thông thường và upload file kèm JSON part, gọi bằng `curl -F`, kiểm thử với MockMvc.
7. Nhận diện lỗi 400/413/415, giới hạn kích thước, dữ liệu tạm và rủi ro filename giả, path traversal, dung lượng lớn hoặc file độc hại.

**Phiên bản bài học:** Spring Framework **6.x**, Spring Boot **3.x**, Servlet API `jakarta.servlet.*`, Java 17+. Spring Framework 6 đã loại `CommonsMultipartResolver` cũ; triển khai được hướng dẫn ở đây là **`StandardServletMultipartResolver`** dựa trên Servlet `Part`. Ví dụ không thuộc Spring WebFlux (WebFlux dùng API multipart reactive riêng).

## 2. Multipart/form-data là gì?

Một form HTML upload tệp có thể được viết:

~~~html
<form method="post" action="/api/uploads/simple"
      enctype="multipart/form-data">
  <input type="file" name="file">
  <input type="text" name="note">
  <button type="submit">Upload</button>
</form>
~~~

`enctype="multipart/form-data"` là điểm khác biệt so với form URL-encoded thông thường. Body thực tế được chia thành các **part** bằng boundary do client sinh ra:

~~~http
POST /api/uploads/simple HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data; boundary=demoBoundary

--demoBoundary
Content-Disposition: form-data; name="note"

document
--demoBoundary
Content-Disposition: form-data; name="file"; filename="hello.txt"
Content-Type: text/plain

Hello Spring
--demoBoundary--
~~~

Ví dụ trên để **giải thích cấu trúc wire**, không phải yêu cầu tự gõ boundary bằng tay. Trong request thực, line ending dùng CRLF và phần chứa file có thể là **binary bytes**, không phải text dễ đọc.

Mỗi part có header riêng. `Content-Disposition` thường chứa `name` khớp với field của form; file part có thể mang `filename` do client cung cấp. `Content-Type` của part cho biết loại dữ liệu client tuyên bố, nhưng **không chứng minh file an toàn hoặc đúng MIME**.

**Không tự đặt** header `Content-Type: multipart/form-data` khi dùng `curl -F` hay JavaScript `FormData`: client phải thêm `boundary` phù hợp với body. Đặt Content-Type thủ công mà thiếu boundary là nguyên nhân phổ biến khiến container không parse được request.

## 3. Ai thực sự parse multipart: container hay Spring?

Trong Spring MVC 6.x:

~~~text
HTTP multipart request
        |
        v
Servlet container (Tomcat/Jetty/Undertow ...)
        |
        v
DispatcherServlet
        |
        +-- tìm bean có tên "multipartResolver"
        |          |
        |          v
        |  StandardServletMultipartResolver
        |          |
        |          v
        |  Servlet request.getParts() / Part
        |          |
        |          v
        |  MultipartHttpServletRequest
        |
        v
Handler adapter --> @RequestParam MultipartFile /
                    @RequestPart / Part
        |
        v
Controller --> Response
~~~

**Phân công trách nhiệm:**

- **Servlet container** (ví dụ embedded Tomcat của Boot) cung cấp parsing theo Servlet multipart API. Nó được cấu hình trên **servlet registration** bằng `MultipartConfigElement`, `<multipart-config>` trong `web.xml`, hoặc cơ chế Servlet tương ứng. Đây là nơi đặt **max file size, max request size, temporary storage location, file size threshold**.
- **`StandardServletMultipartResolver`** là adapter/chiến lược của Spring MVC dựa trên `request.getParts()`. Nó nhận diện request phù hợp, yêu cầu parse (ngay hoặc lazy), bao bọc request thành `MultipartHttpServletRequest`, và phối hợp giải phóng tài nguyên.
- **`DispatcherServlet`** sử dụng resolver tìm được theo **bean name cố định `multipartResolver`**. Nó không tự chọn một bean `MultipartResolver` đặt tên tùy ý trong cấu hình MVC thuần.
- **Controller** nhận phần đã xử lý dưới dạng `MultipartFile`, `Part`, dữ liệu form hoặc DTO JSON từ một part phù hợp.

Một resolver của Spring **không thay thế parser của Servlet container**. Nếu không bật multipart parsing ở servlet registration, chỉ thêm `new StandardServletMultipartResolver()` thường vẫn không đủ.

Ngược lại, khi Servlet hỗ trợ `getParts()` nhưng Spring MVC không có resolver như mong đợi, controller dùng `MultipartFile` có thể không bind đúng theo luồng multipart MVC; dùng `Part` trực tiếp là một cơ chế khác dựa trên Servlet. Hãy xác định hướng tích hợp thay vì sửa ngẫu nhiên annotation handler.

## 4. `MultipartResolver` và `StandardServletMultipartResolver`

Giao diện `org.springframework.web.multipart.MultipartResolver` là hợp đồng chiến lược có các method chủ chốt:

| API | Ý nghĩa |
| --- | --- |
| `isMultipart(HttpServletRequest)` | Request có phù hợp tiêu chí multipart của resolver không |
| `resolveMultipart(HttpServletRequest)` | Parse/wrap request thành `MultipartHttpServletRequest` |
| `cleanupMultipart(MultipartHttpServletRequest)` | Dọn tài nguyên tạm sau xử lý khi thích hợp |

`StandardServletMultipartResolver` là implementation theo Servlet Part API trong Spring MVC 6.x. Hai tùy chọn đáng nhớ:

### 4.1. `setResolveLazily(boolean)`

- **Mặc định `false`**: Spring cố gắng parse các part ngay trong giai đoạn `resolveMultipart`. Lỗi multipart do parsing thường xuất hiện sớm, trước khi vào handler.
- **`true`**: trì hoãn việc parse cho tới khi ứng dụng truy cập file/parameter/part. Lỗi kích thước hoặc body hỏng có thể phát sinh **muộn hơn**.

Lưu ý: lazy không có nghĩa container tuyệt đối không đọc request sớm; middleware, filter hoặc code khác có thể truy cập parameters/parts trước, kích hoạt xử lý. Điều thay đổi chính là **thời điểm Spring yêu cầu parse**. Khi debug exception, phải hiểu request lifecycle.

### 4.2. `setStrictServletCompliance(boolean)`

- **Mặc định `false`**: resolver thử xử lý các request có `multipart/*` tùy khả năng parser của container.
- **`true`**: chỉ wrap các request **`multipart/form-data`**, cho phép những multipart subtype khác được xử lý bởi đường custom nếu cần.

Các container không giống nhau ở những media type ngoài `multipart/form-data`. Theo Javadoc Spring, ví dụ Tomcat có thể chấp nhận những request multipart khác mà Jetty không chấp nhận. Dùng `strictServletCompliance=true` khi muốn rõ chuẩn form upload và tính portable cao hơn; nếu ứng dụng có `multipart/mixed` chuyên biệt, phải tự thiết kế đường xử lý thích hợp.

**Đừng hiểu strict=true là tăng độ bảo mật file upload**: nó chỉ thay tiêu chí resolver áp dụng cho media type, không scan virus hay xác thực nội dung file.

### 4.3. Khai báo bean trong MVC không dùng Boot auto-config

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

@Configuration
public class MultipartResolverConfig {

    @Bean(name = "multipartResolver")
    public MultipartResolver multipartResolver() {
        StandardServletMultipartResolver resolver =
                new StandardServletMultipartResolver();
        resolver.setResolveLazily(false);
        resolver.setStrictServletCompliance(true);
        return resolver;
    }
}
~~~

Đây là **class Java hoàn chỉnh** theo Spring Framework 6.x, nhưng muốn upload chạy cần thêm **Servlet multipart config trên DispatcherServlet registration** và cấu hình controller/web context khác. Bean tên `multipartResolver` là điều kiện quan trọng.

**Không nên sao chép bean này vào mọi Spring Boot application**: Boot 3.x thường đã tự đăng ký resolver khi bật multipart và thỏa auto-configuration. Khai báo thêm có thể làm bạn vô tình thay hành vi đã cấu hình bằng properties.

## 5. Servlet container: đặt limit và thư mục tạm ở đâu?

Trong Servlet API Jakarta, `MultipartConfigElement` nhận tham số:

~~~java
new MultipartConfigElement(
        "/var/tmp/my-app-upload", // location
        2L * 1024 * 1024,         // maxFileSize: 2 MiB
        3L * 1024 * 1024,         // maxRequestSize: 3 MiB
        0                         // fileSizeThreshold: ghi tạm lên disk
);
~~~

Đây là **biểu thức Java minh họa**, không phải một file ứng dụng riêng. Các tham số size ở đây là **số byte** trong constructor Servlet, không phải Spring Boot DataSize string.

- **`location`**: thư mục container dùng để lưu các part được ghi ra đĩa/tệp tạm. Thư mục phải tồn tại hoặc được container quản lý theo cách hỗ trợ, writable bởi tiến trình server, đủ dung lượng và được quản trị quyền truy cập.
- **`maxFileSize`**: kích thước tối đa **mỗi file/part** theo parser container (bytes).
- **`maxRequestSize`**: kích thước tối đa **toàn multipart request**, gồm các part và overhead boundary/headers; thường cần lớn hơn giới hạn file đơn.
- **`fileSizeThreshold`**: ngưỡng trước khi các part được container ghi ra disk; ngưỡng 0 có thể khiến container dùng disk rất sớm. Hành vi thực tế tùy container.

**Quan trọng:** Không có API `setMaxUploadSize(...)` tương đương trên `StandardServletMultipartResolver` của Spring 6 để thay giới hạn Servlet registration. Những hướng dẫn cấu hình `CommonsMultipartResolver#setMaxUploadSize` cho Spring cũ **không còn áp dụng** với Framework 6.

### 5.1. Cấu hình Servlet registration bằng Java (không dùng Boot)

Ví dụ minh họa trong một initializer thuần Spring MVC:

~~~java
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletRegistration;
import org.springframework.web.servlet.support
        .AbstractAnnotationConfigDispatcherServletInitializer;

public class AppInitializer
        extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return null;
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[]{MvcConfig.class};
    }

    @Override
    protected String[] getServletMappings() {
        return new String[]{"/"};
    }

    @Override
    protected void customizeRegistration(
            ServletRegistration.Dynamic registration) {

        registration.setMultipartConfig(new MultipartConfigElement(
                "/var/tmp/my-app-upload",
                2L * 1024 * 1024,
                3L * 1024 * 1024,
                0
        ));
    }
}
~~~

**Đây là đoạn minh họa MVC thuần**, không dùng trực tiếp trong project Boot thực hành ở phần 7. Bạn cần tự định nghĩa bean config `MvcConfig` phù hợp (chứa resolver và MVC controllers), bảo đảm directory tồn tại/quyền ghi, cài Servlet container triển khai; không thể biên dịch file riêng nếu chưa có `MvcConfig`.

Tùy kiến trúc, có thể cấu hình `<multipart-config>` ngay trong đăng ký servlet ở `web.xml`. Đối với **Spring Boot embedded Servlet**, cách nên ưu tiên là properties ở phần tiếp theo, tránh song song cấu hình cùng một giới hạn ở nhiều nơi.

## 6. Spring Boot 3.x tự cấu hình những gì?

Với dependency `spring-boot-starter-web` và multipart enabled, Spring Boot có **`MultipartAutoConfiguration`** chịu trách nhiệm tạo `MultipartConfigElement` và `StandardServletMultipartResolver` khi cần, dựa trên `MultipartProperties` (`spring.servlet.multipart.*`). Boot cũng dùng những điều kiện auto-configuration để **không** tạo duplicate bean khi ứng dụng đã có cấu hình riêng tương ứng.

Điểm phân biệt:

| Câu hỏi | Spring MVC thuần | Spring Boot 3.x thường gặp |
| --- | --- | --- |
| Ai thêm bean `multipartResolver`? | Chính bạn khai báo | Boot thường auto-configure |
| Ai bật multipart trên Servlet? | Initializer / `web.xml` | Boot tạo `MultipartConfigElement` |
| Giới hạn upload nằm đâu? | Registration config | `spring.servlet.multipart.max-*` |
| Resolver parse body bằng gì? | Servlet `Part` | Cũng là Servlet `Part` |
| Có nên thêm `@EnableWebMvc` để bật upload? | Chỉ khi cần cấu hình MVC thuần | **Không** cần và có thể làm mất auto-config khác |
| Có cần Commons FileUpload library không? | Không | Không |

### 6.1. Thuộc tính chính trong `application.properties`

~~~properties
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=2MB
spring.servlet.multipart.max-request-size=3MB
spring.servlet.multipart.file-size-threshold=0B
spring.servlet.multipart.resolve-lazily=false
spring.servlet.multipart.strict-servlet-compliance=true
~~~

**Giải thích từng dòng:**

- `enabled=true`: cho phép Boot auto-configure multipart handling (mặc định thường bật).
- `max-file-size=2MB`: giới hạn mỗi file khoảng 2 MiB theo cơ chế `DataSize` của Boot (hậu tố đơn vị phải hiểu theo quy tắc Boot, không coi là miligiây hoặc số file).
- `max-request-size=3MB`: giới hạn toàn request; cần tính cả các part text/JSON và phần overhead. Nếu cho phép 2 file mỗi file 2MB, tổng request 3MB **không đủ**.
- `file-size-threshold=0B`: ngưỡng ghi tạm; có thể chọn giá trị khác khi hiểu giới hạn memory/disk.
- `resolve-lazily=false`: parse multipart sớm ở giai đoạn resolver.
- `strict-servlet-compliance=true`: resolver chỉ wrap dạng `multipart/form-data`, tránh khác biệt subtype giữa containers.

Theo tài liệu `MultipartProperties` của Spring Boot 3.5, **mặc định** file size là **1MB**, request size **10MB**, threshold **0**. Đừng nhầm các mặc định này với chính sách sản phẩm; nếu ứng dụng cho phép nhiều file/loại file khác nhau, phải chọn giới hạn hợp lý theo threat model.

### 6.2. Cấu hình thư mục tạm

Có thể thêm:

~~~properties
spring.servlet.multipart.location=/var/tmp/myapp-multipart
~~~

**Không bật dòng này mù quáng trên Windows hoặc máy không có thư mục tương ứng.** Khi không khai báo, Boot/Servlet container thường dùng thư mục tạm mặc định. Với production, nên cấp directory riêng trên volume có quota, quyền tối thiểu, cơ chế dọn rác và giám sát dung lượng.

Giá trị `location` của multipart **không phải** thư mục đích lưu file vĩnh viễn. Đừng coi request đã upload thành công chỉ vì Servlet tạo một temporary file. Muốn lưu lâu dài, ứng dụng phải chủ động validate và lưu bằng cơ chế riêng.

### 6.3. Không nhầm với các size limit khác

Một request lớn có thể bị chặn trước khi tới resolver do:
- Reverse proxy (ví dụ Nginx, API gateway, ingress) có giới hạn body riêng.
- Servlet connector/container có các tham số khác hoặc giới hạn tổng request.
- WAF/bảo mật hệ thống.
- Application validation/business rules sau khi parse.

Do đó, `spring.servlet.multipart.max-file-size=2MB` **không đủ để kết luận status HTTP chính xác** trong mọi triển khai. Response có thể là 413, 400, một lỗi khác, hoặc kết nối bị ngắt trước khi vào Spring tùy tầng từ chối và cách xử lý exception.

## 7. Ứng dụng Spring Boot 3.x chạy được

Bài thực hành có hai endpoint:

- `POST /api/uploads/simple`: nhận một file và ghi chú form.
- `POST /api/uploads/with-json`: nhận file và một **JSON part** theo `@RequestPart`.

Để dễ kiểm tra, controller **không lưu file vĩnh viễn**; nó chỉ đọc metadata để xác nhận body được parse/bind đúng. Điều này tránh giả định file temporary có thể dùng lâu sau request. Bản production cần lưu nội dung thật an toàn sau kiểm tra.

Tạo project Spring Initializr với **Java 17+**, **Spring Boot 3.x**, **Maven**, dependencies **Spring Web** và **Spring Boot Starter Test**; package `com.example.demo`.

Cấu trúc:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/UploadController.java
src/main/resources/application.properties
src/test/java/com/example/demo/UploadControllerTest.java
~~~

### 7.1. `DemoApplication.java`

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

### 7.2. `application.properties`

~~~properties
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=2MB
spring.servlet.multipart.max-request-size=3MB
spring.servlet.multipart.file-size-threshold=0B
spring.servlet.multipart.resolve-lazily=false
spring.servlet.multipart.strict-servlet-compliance=true
~~~

Không cần tạo bean `StandardServletMultipartResolver` thủ công khi giữ cấu hình Boot mặc định này.

### 7.3. `UploadController.java`

~~~java
package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    @PostMapping(
            path = "/simple",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> simple(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "note", defaultValue = "") String note) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new UploadError("EMPTY_FILE"));
        }
        if (note.length() > 100) {
            return ResponseEntity.badRequest()
                    .body(new UploadError("NOTE_TOO_LONG"));
        }

        return ResponseEntity.ok(new UploadReceipt(
                file.getOriginalFilename(), file.getSize(), note));
    }

    @PostMapping(
            path = "/with-json",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> withJson(
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") UploadMetadata metadata) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new UploadError("EMPTY_FILE"));
        }
        if (metadata.label() == null ||
                metadata.label().isBlank() ||
                metadata.label().length() > 100) {
            return ResponseEntity.badRequest()
                    .body(new UploadError("INVALID_LABEL"));
        }

        return ResponseEntity.ok(new UploadReceipt(
                file.getOriginalFilename(),
                file.getSize(),
                metadata.label()));
    }

    public record UploadMetadata(String label) {}
    public record UploadReceipt(String originalName, long size, String note) {}
    public record UploadError(String code) {}
}
~~~

**Giải thích:**

- `@RequestParam("file") MultipartFile` đọc file part theo tên form field. `MultipartFile` cung cấp `isEmpty()`, `getSize()`, `getOriginalFilename()`, `getContentType()`, `getInputStream()`, `transferTo(...)`.
- `@RequestParam("note") String` đọc một form parameter thông thường; trong ví dụ `defaultValue=""` cho phép bỏ ghi chú.
- `@RequestPart("metadata") UploadMetadata` dùng **`HttpMessageConverter`** cho nội dung part, thường là Jackson khi part khai báo `Content-Type: application/json`. Không nhầm JSON part với một field form chứa chuỗi JSON nhưng không có content type thích hợp.
- `@RequestPart("file") MultipartFile` cũng nhận file part; cả `@RequestParam` và `@RequestPart` có vai trò phù hợp cho trường hợp file, còn DTO JSON nên sử dụng `@RequestPart` rõ ràng.
- `file.getOriginalFilename()` do **client cung cấp**, có thể chứa đường dẫn/ký tự đặc biệt và không được tin để tạo disk path. Ở đây trả về JSON **chỉ để quan sát kết quả**, không dùng như tên file lưu vĩnh viễn.
- `file.getContentType()` là dữ liệu từ request; không xác thực bản chất file. Kiểm tra size và label trong ví dụ chỉ là **minh họa**, production cần kiểm soát chặt hơn.
- `file.isEmpty()` là true cho file rỗng/không có nội dung, không phải quy tắc xác định malware. Nếu file được phép rỗng theo nghiệp vụ, điều kiện phải đổi.
- Bài này không tạo database/storage, nên “upload thành công” trong ví dụ có nghĩa **đã parse và đọc metadata**, không phải đã lưu tài nguyên.

### 7.4. Chạy và gọi bằng `curl -F`

Chạy trong thư mục có `pom.xml`:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows dùng `mvnw.cmd spring-boot:run`; nếu máy cài Maven nhưng không có wrapper, dùng `mvn spring-boot:run`.

Tạo file UTF-8 có nội dung `Hello Spring` tên `hello.txt`. Gọi:

~~~bash
curl -i -X POST "http://localhost:8080/api/uploads/simple"   -F "file=@hello.txt;type=text/plain"   -F "note=demo"
~~~

Khi file chứa 12 byte `Hello Spring`, kết quả kỳ vọng **HTTP 200 OK** và body JSON tương đương:

~~~json
{"originalName":"hello.txt","size":12,"note":"demo"}
~~~

Lưu ý: nếu file có newline cuối, BOM, hoặc encoding/bytes khác thì `size` khác 12. Không đưa kích thước của file thực tế vào ví dụ mà không kiểm tra bytes.

Đối với endpoint kèm JSON part:

~~~bash
curl -i -X POST "http://localhost:8080/api/uploads/with-json"   -F "file=@hello.txt;type=text/plain"   -F 'metadata={"label":"report"};type=application/json'
~~~

Kết quả kỳ vọng HTTP 200 với `note="report"` và `size=12` nếu file đúng 12 byte:

~~~json
{"originalName":"hello.txt","size":12,"note":"report"}
~~~

**Không thêm** `-H "Content-Type: multipart/form-data"` bằng tay vì `curl -F` sẽ tự tạo boundary cho đúng body.

Trường hợp file thiếu hoặc part sai tên có thể tạo lỗi argument resolution (thường HTTP 400) trước khi code `file.isEmpty()` chạy. Trường hợp file có tên nhưng body rỗng sẽ đi vào nhánh trả JSON `{"code":"EMPTY_FILE"}` nếu request parse thành công.

### 7.5. Gửi multipart từ browser

~~~javascript
async function sendFile(file, note) {
  const form = new FormData();
  form.append("file", file);
  form.append("note", note);

  const response = await fetch("/api/uploads/simple", {
    method: "POST",
    body: form
  });

  if (!response.ok) {
    throw new Error("Upload failed: " + response.status);
  }
  return await response.json();
}
~~~

Trình duyệt tự tạo boundary khi `body` là `FormData`. Nếu gửi JSON part, bạn có thể làm:

~~~javascript
const form = new FormData();
form.append("file", chosenFile);
form.append(
  "metadata",
  new Blob([JSON.stringify({label: "report"})], {
    type: "application/json"
  })
);

await fetch("/api/uploads/with-json", {
  method: "POST",
  body: form
});
~~~

Đây là đoạn minh họa trong browser nơi `chosenFile` đã lấy từ input file hoặc File API. `Blob` giúp tạo part có media type `application/json`. Không cần serialize toàn bộ `FormData` thành JSON.

## 8. Kiểm thử controller với MockMvc

Tạo file **`src/test/java/com/example/demo/UploadControllerTest.java`**:

~~~java
package com.example.demo;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadController.class)
class UploadControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void readsFormFileAndNote() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                "Hello Spring".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/uploads/simple")
                        .file(file)
                        .param("note", "demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalName").value("hello.txt"))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.note").value("demo"));
    }

    @Test
    void readsJsonPartThroughMessageConverter() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain",
                "Hello Spring".getBytes(StandardCharsets.UTF_8));

        MockMultipartFile metadata = new MockMultipartFile(
                "metadata", "",
                "application/json",
                "{\"label\":\"report\"}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/uploads/with-json")
                        .file(file)
                        .file(metadata))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.note").value("report"));
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/uploads/simple")
                        .file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
    }

    @Test
    void rejectsOversizedNoteByBusinessRule() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "one.txt", "text/plain", new byte[]{65});

        mockMvc.perform(multipart("/api/uploads/simple")
                        .file(file)
                        .param("note", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTE_TOO_LONG"));
    }
}
~~~

Chạy:

~~~bash
./mvnw test
~~~

MockMvc giúp kiểm tra **argument binding, file part, JSON part và controller logic** mà không cần mở một HTTP socket thật. Test `readsJsonPartThroughMessageConverter` chứng minh part `metadata` được parse thành Java record qua Jackson khi media type chính xác.

### 8.1. Tại sao MockMvc không đủ để kiểm tra max-file-size của container?

`MockMultipartFile` và `MockMultipartHttpServletRequest` thường tạo multipart data đã parse trong bộ nhớ test. **Chúng không mô phỏng trọn Servlet connector, disk storage và multipart parser thật của Tomcat/Jetty/Undertow.** Vì vậy một test MockMvc upload 5MB có thể **không** bị giới hạn bởi `spring.servlet.multipart.max-file-size=2MB` dù môi trường production chặn.

Để test kích thước thực, cần:
- Chạy **embedded server trên HTTP socket thật**, ví dụ `@SpringBootTest(webEnvironment = RANDOM_PORT)` và HTTP client gửi multipart thật.
- Hoặc chạy server và dùng `curl -F` với file lớn hơn giới hạn, kiểm tra response **và log tại container/proxy**.
- Test riêng request lớn hơn `max-request-size`, nhiều file nhỏ có tổng vượt hạn mức, file zero-byte, part JSON malformed và thiếu boundary.

Không tự assert 413 cứng trong MockMvc controller slice; status của request vượt hạn mức còn phụ thuộc **tầng từ chối và exception handling**.

## 9. Hai giới hạn `max-file-size` và `max-request-size`

| Cấu hình | Đo lường | Ví dụ |
| --- | --- | --- |
| `max-file-size=2MB` | Mỗi file riêng lẻ | File 2,5MB có thể bị từ chối |
| `max-request-size=3MB` | Tổng request multipart | Hai file 1,8MB mỗi file có thể vượt tổng |
| `file-size-threshold=0B` | Ngưỡng memory → disk theo container | Có thể tạo tệp tạm ngay |
| Reverse proxy max body | Toàn HTTP body trước app | Có thể từ chối trước Spring |
| Validation controller | Sau khi parse request | Có thể từ chối file dù dưới parser limit |

**Đặt limit thành nhiều lớp:** reverse proxy, Servlet, nghiệp vụ và storage quota. Chỉ một limit trong controller là quá muộn: container có thể đã đọc/lưu tạm dữ liệu rất lớn trước khi handler chạy. Khi có nhiều file, nhớ giới hạn cả **số lượng file, dung lượng tổng và tổng tài nguyên người dùng có thể tiêu thụ**.

Nếu cần upload nhiều GB, phải thiết kế riêng: streaming, chunk/resumable uploads, storage trực tiếp có URL ký có hạn, quota và anti-malware pipeline. Không chỉ nâng `max-file-size` lên vài GB rồi gọi `MultipartFile.getBytes()` trên toàn file, vì `getBytes()` đưa nội dung vào heap.

## 10. Lazy parsing và cách xử lý exception

### 10.1. Parse eager (mặc định)

`spring.servlet.multipart.resolve-lazily=false` tương ứng `resolver.setResolveLazily(false)`. Khi `DispatcherServlet` nhận request multipart, resolver có thể yêu cầu parse ngay. Lỗi kích thước, boundary thiếu, thư mục tạm lỗi hoặc permission có thể phát sinh **trước lúc controller được gọi**.

Do đó đoạn `try/catch` bên trong handler:

~~~java
@PostMapping("/upload")
public String upload(@RequestParam MultipartFile file) {
    try {
        return "OK";
    } catch (Exception ex) {
        return "ERROR";
    }
}
~~~

Đây là **mẫu minh họa không phù hợp để xử lý mọi lỗi multipart**: nếu parse thất bại trước khi vào method, `catch` này không thể nhận exception. Nên dùng exception handling của MVC nếu exception đi qua `DispatcherServlet`; còn lỗi bị proxy/container chặn trước Spring cần xử lý ở tầng đó.

### 10.2. Parse lazy

`spring.servlet.multipart.resolve-lazily=true` khiến Spring trì hoãn parse tới khi cần truy cập parameter/file. Nó có thể thay đổi vị trí stack trace và nơi `MultipartException` xuất hiện, **không tăng size limit** và không làm parsing chắc chắn thành công.

Cấu hình lazy hữu ích trong một số luồng xử lý đặc biệt, nhưng khó debug hơn nếu nhiều interceptor/filter truy cập request parameters. Đừng bật lazy như cách “sửa lỗi 413”; phải kiểm tra giới hạn thực tế.

### 10.3. `MaxUploadSizeExceededException` và HTTP 413

Trong những trường hợp được Spring MVC bắt và nhận diện dưới dạng `MaxUploadSizeExceededException`, bạn có thể ánh xạ sang **413 Payload Too Large** qua `@RestControllerAdvice`:

~~~java
@RestControllerAdvice
public class UploadExceptionAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(
            MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "FILE_TOO_LARGE"));
    }
}
~~~

**Đây là class minh họa bổ sung, không thuộc bản chạy ở phần 7.** Cần import `org.springframework.web.multipart.MaxUploadSizeExceededException`, `java.util.Map`, `org.springframework.http.HttpStatus`, `ResponseEntity`, `@ExceptionHandler`, `@RestControllerAdvice`. Khi server/Servlet container ném exception khác hoặc proxy từ chối trước MVC, handler này **không bảo đảm** chạy. Cần log an toàn và kiểm thử end-to-end với container thật.

Dùng `413` khi body lớn hơn chính sách có thể được ứng dụng phản hồi được. Đừng dựa vào mọi kích thước vượt hạn mức luôn tạo `MaxUploadSizeExceededException`; một số connector có thể đóng kết nối hoặc phát lỗi trước.

## 11. Thư mục tạm, vòng đời `MultipartFile` và lưu file lâu dài

`MultipartFile` đại diện dữ liệu upload trong **request hiện tại**, có thể được giữ trong memory hoặc file tạm tùy parser và threshold.

Các điểm cốt lõi:
- **Temporary upload storage** không phải kho dữ liệu bền vững. Framework/container có thể cleanup sau khi request kết thúc.
- Không giữ reference `MultipartFile` trong singleton, queue hoặc task bất đồng bộ để đọc ở thời điểm không còn request. Nếu cần tác vụ nền, phải **di chuyển/copy dữ liệu vào storage có kiểm soát trước**, có transaction/cleanup rõ ràng.
- `transferTo(Path/File)` có thể dùng để lưu dữ liệu, nhưng semantics move/copy và đường dẫn tương đối phụ thuộc implementation. Khi cần ổn định, dùng đường dẫn tuyệt đối chuẩn hóa trong vùng storage được phép và thiết kế atomic move phù hợp.
- File được upload chưa chắc là tài nguyên được public. Không phục vụ lại bằng static resource handler khi chưa kiểm soát truy cập và Content-Type/Content-Disposition của response.
- Khi xảy ra exception giữa chừng, lưu file tạm của ứng dụng phải có cleanup và quota; cleanup của resolver đối với request multipart không tự dọn mọi bản copy do ứng dụng tạo.

### 11.1. Không tin filename do client gửi

`file.getOriginalFilename()` có thể chứa `../`, dấu phân tách đường dẫn, ký tự Unicode bất thường hoặc tên trùng. Trên hệ thống thực, đừng viết:

~~~java
// KHÔNG AN TOÀN
Path target = uploadRoot.resolve(file.getOriginalFilename());
file.transferTo(target);
~~~

Đây là **mẫu phản diện** minh họa path traversal/collision/overwrite. Không chỉ xóa chuỗi `../` bằng replace đơn giản rồi coi là an toàn; cần chuẩn hóa path, lựa chọn tên server-side và chính sách lưu rõ.

Phương án tốt hơn thường là:
1. Server sinh **ID ngẫu nhiên không đoán trước**, ví dụ UUID, làm storage key.
2. Nếu giữ extension, lấy extension từ chính sách loại file hợp lệ và **xác định loại file thật** chứ không tin tên/Content-Type của client.
3. Lưu dưới thư mục nằm ngoài public document root, phân quyền tối thiểu, kiểm soát symlink và canonical path.
4. Lưu original display name **là metadata không đáng tin**, escape khi đưa vào HTML hoặc header.
5. Dùng `Content-Disposition: attachment` thích hợp khi download tài liệu không đáng render inline; kết hợp `X-Content-Type-Options: nosniff` và MIME hợp lệ.

### 11.2. Kiểm tra file upload nhiều lớp

- **Kích thước:** giới hạn tại proxy/container trước controller, cộng quota theo user/tenant.
- **Loại file:** extension + MIME do client khai báo chỉ là gợi ý; kiểm tra magic bytes/parser theo loại thực, chặn dạng không mong muốn.
- **Nội dung nguy hiểm:** scanner/antivirus và sandbox khi phù hợp; với ảnh, cân nhắc decode và re-encode để loại bỏ payload không cần thiết.
- **Archive:** phòng zip bomb, path traversal trong archive entry và tài nguyên CPU/memory.
- **Quyền truy cập:** xác thực và phân quyền upload/download, kiểm soát ownership.
- **CSRF:** nếu authentication dùng cookie trên trình duyệt, POST upload thay đổi trạng thái cần CSRF protection phù hợp.
- **Storage:** directory/volume riêng có quota, tên server-side, backup/lifecycle, cleanup.
- **Thông báo lỗi:** không tiết lộ absolute path, storage credential hoặc stack trace trong JSON lỗi.

Việc giải mã body thành công **không** có nghĩa tệp an toàn để lưu hoặc mở. Đây là tầng nghiệp vụ/bảo mật bên trên `MultipartResolver`.

## 12. Những lỗi phổ biến và cách chẩn đoán

| Triệu chứng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| Controller nhận file null/missing | Tên part khác `@RequestParam("file")`, thiếu part, sai Content-Type/boundary | Đối chiếu `name`, `curl -F`, header request thực |
| Lỗi multipart parse trước controller | Container chưa được bật multipart hoặc body/part hỏng | Kiểm tra servlet multipart config, logs, cause |
| `MaxUploadSizeExceededException` hoặc 413 | Vượt `max-file-size`, `max-request-size`, limit proxy | So size file và toàn body, đúng tầng từ chối |
| File nhỏ vẫn vượt `max-request-size` | Nhiều part hoặc overhead; tổng lớn hơn limit | Tính toàn request, không chỉ file lớn nhất |
| `@RequestPart` JSON bị 415 | Part `metadata` không có `Content-Type: application/json` hoặc thiếu converter | Dùng `curl -F 'metadata=...;type=application/json'` |
| Multipart vượt qua controller nhưng không còn file sau request | File nằm ở temp storage và đã cleanup | Copy/transfer trước khi request kết thúc |
| Upload thành công ở Tomcat nhưng fail ở Jetty | Multipart subtype/HTTP method mà container parse khác nhau | Cân nhắc `strict-servlet-compliance=true`, test container |
| Thêm resolver bean nhưng vẫn không parse | Chưa có `MultipartConfigElement` trên servlet registration | Cấu hình hai tầng, kiểm tra startup |
| Dùng `setMaxUploadSize` không compile | API của `CommonsMultipartResolver` cũ | Dùng Servlet/Boot multipart properties |
| `@RequestBody` DTO không đọc toàn multipart | `@RequestBody` thích hợp cho body đơn định dạng; JSON là part | Dùng `@RequestPart` cho JSON part |
| `MockMvc` cho phép file vượt size limit | Mocked multipart bypass container parser | Test với HTTP thật trên embedded server |
| Disk đầy hoặc permission denied | Temp upload location không writable/volume thiếu quota | Kiểm tra user chạy server, mount và cleanup |
| 400/413/415 không ổn định giữa môi trường | Proxy/container/exception handlers khác nhau | Đo HTTP + logs tại từng tầng |
| Filename có dấu `../` hoặc tên tuyệt đối | Client kiểm soát `getOriginalFilename()` | Sinh storage key server-side và validate |
| `multipartResolver` không được tìm thấy | Bean sai tên hoặc khác context của `DispatcherServlet` | Kiểm tra bean name chính xác và context |

### Quy trình debug thực tế

1. Xác minh **client** có gửi `multipart/form-data; boundary=...` và body thật sự có part đúng tên.
2. Xác minh **Servlet registration** đã bật multipart, giới hạn file/request và temp location có quyền ghi.
3. Với Spring MVC thuần, xác minh bean **`multipartResolver`**; với Boot xem auto-config và property `spring.servlet.multipart.enabled`.
4. Kiểm tra `resolve-lazily` quyết định thời điểm parse; `strict-servlet-compliance` và container quyết định media subtype.
5. Đọc exception gốc tại **proxy/container** và Spring; không giả định mọi lỗi đều do controller.
6. Kiểm tra đúng `@RequestParam`, `@RequestPart`, media type của JSON part, validation và file handling.
7. Test bằng HTTP thật với file lớn, nhiều file và request form bất thường; không suy kết luận limit từ MockMvc.
8. Bảo đảm dữ liệu được lưu vào storage quản lý nếu ứng dụng cần giữ lâu; đừng dựa vào thư mục tạm.

## 13. Câu hỏi ôn tập

**1. `StandardServletMultipartResolver` có tự thay thế Servlet container multipart parser không?**

Không. Nó dùng `jakarta.servlet.http.Part`/`request.getParts()` của container. Container phải được cấu hình để parse multipart.

**2. Vì sao bean resolver cần tên `multipartResolver` trong Spring MVC thuần?**

Vì `DispatcherServlet` tìm resolver theo bean name quy ước đó. Bean đặt tên khác có thể không được tự động dùng làm multipart strategy.

**3. Muốn giới hạn 2 MiB mỗi file trong Spring Boot 3.x thì dùng phương án nào?**

Đặt `spring.servlet.multipart.max-file-size=2MB`; giới hạn được áp dụng vào Servlet multipart configuration. Không gọi method `setMaxUploadSize` trên `StandardServletMultipartResolver` (không tồn tại).

**4. `resolve-lazily=true` có giải quyết lỗi quá kích thước không?**

Không. Nó thay đổi thời điểm parse/lỗi xuất hiện, không tăng hoặc bỏ giới hạn size của container.

**5. `strict-servlet-compliance=true` xử lý những media type nào?**

Resolver chỉ wrap multipart form upload với `multipart/form-data`. Các subtype multipart khác không được Spring resolver này bao bọc, thuận tiện nếu ứng dụng có xử lý riêng.

**6. Vì sao MockMvc không chứng minh được size limit của Servlet container?**

Vì MockMvc thường cấp multipart object đã dựng sẵn mà không đi qua parser/connector HTTP thật. Muốn kiểm thử size limit cần server thực và HTTP multipart thật.

**7. Tại sao `getOriginalFilename()` không được dùng trực tiếp làm storage path?**

Vì client kiểm soát giá trị đó; có thể gây path traversal, ghi đè, tên không hợp lệ hoặc giả mạo extension. Nên sinh storage key phía server và xác thực loại file.

## 14. Checklist trước khi triển khai

- Phân biệt resolver của **Spring MVC** và parser/limits của **Servlet container**; cấu hình đủ hai tầng khi không dùng Spring Boot auto-config.
- Trên Boot 3.x, ưu tiên `spring.servlet.multipart.*` và chỉ khai báo bean tùy chỉnh nếu có nhu cầu rõ.
- Cấu hình `max-file-size`, `max-request-size`, temp `location` và `file-size-threshold` theo tài nguyên thật, thêm quota/proxy limit.
- Chọn `resolve-lazily` theo vị trí xử lý lỗi mong muốn, `strict-servlet-compliance` theo yêu cầu tính portable và media subtype.
- Dùng `@RequestParam MultipartFile` cho file/form field và `@RequestPart` cho JSON part với content type phù hợp.
- Không tự đặt multipart Content-Type header khi dùng `curl -F` hoặc browser `FormData`.
- Kiểm tra empty file, size, type thực, filename, nội dung nguy hiểm, quyền upload và storage path.
- Lưu file lâu dài trước khi request kết thúc nếu nghiệp vụ yêu cầu; quản trị temp disk và cleanup.
- Test cả controller và HTTP thật qua container/proxy, đặc biệt lỗi upload vượt giới hạn hoặc multipart malformed.
- Không sử dụng ví dụ `CommonsMultipartResolver`/Javax Servlet từ Spring 5 để cấu hình Spring 6/Jakarta.

## Nguồn tham khảo chính thức

- [Spring Framework 6.2 — Multipart Resolver](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/multipart.html)
- [Spring Framework 6.2 — Multipart Forms](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/multipart-forms.html)
- [Spring Framework — StandardServletMultipartResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/multipart/support/StandardServletMultipartResolver.html)
- [Spring Framework — MultipartResolver interface](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/multipart/MultipartResolver.html)
- [Spring Boot 3.5 — MultipartProperties Javadoc](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/web/servlet/MultipartProperties.html)
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html)
- [Spring Framework 6.0 — Release Notes (CommonsMultipartResolver removed)](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.0-Release-Notes)
- [OWASP — File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-resources-002">Nhận MultipartFile, Part và kết hợp tệp với dữ liệu JSON</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận MultipartFile, Part và kết hợp tệp với dữ liệu JSON trong Spring MVC

## 1. Mục tiêu và bối cảnh

Nhiều API nhận không chỉ JSON hoặc chỉ file, mà nhận **một file cùng metadata có cấu trúc** trong cùng HTTP request. Ví dụ một client muốn tạo tài liệu với thuộc tính `title`, `category` và file `note.txt`:

- `title`, `category`: dữ liệu nghiệp vụ được kiểm tra bằng Bean Validation.
- `file`: bytes tải lên, filename và Content-Type do client cung cấp.
- Server cần xử lý metadata và file cùng một thao tác, đồng thời quản lý bảo mật/giới hạn kích thước.

Trong **Spring Web MVC trên Servlet stack**, có ba kiểu tham số phổ biến:

1. `MultipartFile`: abstraction của Spring dùng để đọc metadata và nội dung file part.
2. `jakarta.servlet.http.Part`: abstraction gốc của Jakarta Servlet cho từng multipart part, không phụ thuộc kiểu `MultipartFile`.
3. `@RequestPart` với DTO Java: dùng `HttpMessageConverter` để giải mã **nội dung của một part** như JSON thành DTO.

Sau bài này, bạn có thể giải thích HTTP multipart boundaries/headers, phân biệt `@RequestParam`, `@ModelAttribute`, `@RequestPart` và `@RequestBody`, triển khai API nhận JSON + file, viết tests bằng MockMvc, chọn API lưu file an toàn, và xử lý validation/security/limits.

Ví dụ chính dùng **Java 17, Spring Boot 3.3.5, Spring Framework 6.1.x** và `spring-boot-starter-web`. Các ví dụ code Spring là Servlet MVC, không áp dụng nguyên mẫu cho WebFlux (`FilePart`/reactive stack khác).

## 2. multipart/form-data là gì?

HTTP request có thể có một body chứa **nhiều part**. Mỗi part có header `Content-Disposition`, có thể có `Content-Type` và có payload riêng. Envelope bên ngoài mang media type `multipart/form-data` và `boundary`:

```http
POST /api/uploads/mixed HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data; boundary=DemoBoundary
Accept: application/json

--DemoBoundary
Content-Disposition: form-data; name="metadata"
Content-Type: application/json; charset=UTF-8

{"title":"Ghi chú","category":"notes"}
--DemoBoundary
Content-Disposition: form-data; name="file"; filename="note.txt"
Content-Type: text/plain

ABC
--DemoBoundary--
```

Đây là **biểu diễn wire format minh họa**: thực tế HTTP multipart dùng `CRLF` để phân tách headers/body và boundary, còn file bytes có thể không phải text. Khi dùng `curl -F` hoặc browser `FormData`, client tự chọn boundary, tạo headers và encode body. **Không tự ghi header `Content-Type: multipart/form-data` không có boundary** nếu thư viện client đã tự dựng multipart; header sai có thể làm server không parse được.

Phân biệt hai tầng media type:

- **Outer** `Content-Type: multipart/form-data; boundary=...` báo body là multipart.
- **Part `metadata`** có `Content-Type: application/json`; Jackson converter có thể đọc thành `UploadMetadata`.
- **Part `file`** có `Content-Type: text/plain` hoặc `application/octet-stream`; với `MultipartFile`/`Part` thường server nhận bytes và tự quyết định kiểm tra, không deserialize sang JSON DTO.
- `Content-Disposition: form-data; name="metadata"` định danh tên part. Tên phải khớp `@RequestPart("metadata")`; filename chỉ là metadata client cung cấp, không đáng tin.

Một file và metadata JSON nằm trong **một request**, nhưng không có nghĩa việc lưu file + lưu database đã là một transaction nguyên tử. Nếu bước ghi file thành công nhưng ghi database lỗi, cần cleanup/compensation hoặc workflow đáng tin để không để lại orphan file.

### Multipart không phải JSON chứa một file

Một request JSON:

```json
{"title":"Ghi chú","fileBase64":"QUJD"}
```

khác với multipart. Base64 làm payload phình ra và phải giải mã; dùng multipart phù hợp hơn khi cần truyền file bytes và text/JSON parts. Nếu API nhỏ, chỉ nhận chuỗi, JSON thuần vẫn có thể đủ. Việc chọn multipart nên dựa trên **hợp đồng HTTP** và cách client gửi dữ liệu.

## 3. Từ Servlet part đến đối số controller

Trong Spring MVC 6.x, `StandardServletMultipartResolver` tích hợp parser `jakarta.servlet.http.Part` của Servlet container vào `DispatcherServlet`. Spring Boot MVC thường tự đăng ký resolver và `MultipartConfigElement`, nên controller không tự khởi tạo multipart parser. Cơ chế này được khai thác ở đây để phân biệt **file part** với **JSON metadata part**; cấu hình resolver, bean name `multipartResolver`, eager/lazy parsing và chính sách Servlet container được giải thích chuyên sâu ở bài về `MultipartResolver` của chương.

```text
POST multipart/form-data
       |
Servlet container -> parsed Parts / temporary storage
       |
Spring MVC MultipartResolver
       |
       +-- "file"     -> MultipartFile hoặc Servlet Part
       |
       +-- "metadata" (Content-Type: application/json)
                        -> HttpMessageConverter -> UploadMetadata DTO
                        -> @Valid / Bean Validation
       |
Controller -> service -> HTTP response
```

Giá trị trả về không được bảo đảm an toàn chỉ vì parser thành công: controller vẫn cần validation, quyền truy cập và lưu file vào storage riêng. Với JSON part, **`Content-Type` của từng part**, không chỉ outer `multipart/form-data`, quyết định converter có thể đọc DTO.

### Cấu hình cần có để ví dụ chạy

Ví dụ Maven ở mục 7 có `application.properties` giới hạn multipart ngay tại Servlet configuration:

```properties
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=2MB
spring.servlet.multipart.max-request-size=5MB
spring.servlet.multipart.file-size-threshold=0B
spring.servlet.multipart.strict-servlet-compliance=true
```

`max-file-size` áp dụng từng file; `max-request-size` áp dụng toàn multipart envelope, gồm metadata và các part khác. `file-size-threshold` chỉ điều khiển ngưỡng dùng bộ nhớ/đĩa tạm, không phải quota lâu dài. `strict-servlet-compliance` giới hạn resolver vào `multipart/form-data`, không xác minh nội dung file. Nếu không chủ động đặt `resolve-lazily`, thông thường parse sớm theo mặc định; bật lazy có thể đổi **thời điểm lỗi parsing**, không tăng size limit.

Giới hạn proxy, Servlet container và bộ nhớ/đĩa vẫn tác động độc lập; `MockMvc` không mô phỏng toàn bộ HTTP multipart parser thật. Các bài kiểm thử giới hạn và bảo vệ upload cần dùng server/container thực trong môi trường kiểm soát.

## 4. MultipartFile: API chính của Spring MVC

`org.springframework.web.multipart.MultipartFile` cung cấp:

| Method | Ý nghĩa |
| --- | --- |
| `getName()` | Tên form field/part, ví dụ `file` |
| `getOriginalFilename()` | Filename do client khai báo; **không đáng tin** |
| `getContentType()` | MIME type do client khai báo; có thể null hoặc giả mạo |
| `isEmpty()` | Tệp không được chọn hoặc có zero bytes, tùy request |
| `getSize()` | Kích thước nội dung theo bytes |
| `getBytes()` | Đọc toàn bộ nội dung vào `byte[]` — tránh với file lớn |
| `getInputStream()` | Đọc nội dung dạng stream; nên đóng sau khi dùng |
| `transferTo(File/Path)` | Chuyển nội dung sang file đích theo contract/implementation cụ thể |

Khi handler nhận:

```java
@PostMapping(path = "/upload",
             consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public String upload(@RequestParam("file") MultipartFile file) {
    // ...
}
```

đây là method minh họa, cần đặt trong controller với imports. `@RequestParam` có thể dùng với `MultipartFile` và form fields. `@RequestPart("file") MultipartFile` cũng hợp lệ. Đối với tệp đơn giản, cả hai thường giải quyết cùng part file; ý nghĩa của `@RequestPart` trở nên quan trọng hơn khi **part chứa JSON cần converter đọc thành DTO**.

### Nội dung MultipartFile chỉ có thời hạn request

File bytes có thể được giữ trong RAM hoặc temporary file do container quản lý. Sau khi request kết thúc, dữ liệu tạm có thể bị xóa. Vì vậy:

- Nếu cần sử dụng sau request, chuyển nội dung vào storage do ứng dụng kiểm soát trong lúc request còn hiệu lực.
- Không giữ `MultipartFile` trong singleton field hoặc đưa object này cho async job rồi trông đợi stream vẫn còn mở.
- Đừng đồng nhất `getOriginalFilename()` với tên file lưu trên disk. Filename có thể chứa slash, backslash, dấu chấm, Unicode khác thường hoặc các chuỗi path traversal như `../../secret.txt`.
- Với file lớn, `getBytes()` có thể tạo áp lực bộ nhớ; dùng stream hoặc cơ chế streaming/upload chuyên dụng khi phù hợp.
- Không tự tin `getContentType()` phản ánh nội dung thật; đây là metadata của client.

## 5. jakarta.servlet.http.Part: API thấp hơn của Servlet

`jakarta.servlet.http.Part` là abstraction chuẩn của Servlet API. Các thao tác tiêu biểu:

```java
String name = part.getName();
String filename = part.getSubmittedFileName();
String contentType = part.getContentType();
long size = part.getSize();
try (InputStream in = part.getInputStream()) {
    // Đọc bytes cần thiết.
}
```

Đoạn này chỉ là snippet, yêu cầu import `java.io.InputStream` và tham số `Part part` có sẵn. Ngoài các method trên, `Part` có `getHeader(s)`, `write(String)` và `delete()`. `write`/`delete` tương tác với cơ chế multipart của container; chú ý quy tắc storage/location tương đối và portability.

Trong controller:

```java
@PostMapping(path = "/part",
             consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public String inspect(@RequestPart("file") Part file) {
    return file.getName() + ":" + file.getSize();
}
```

Method chỉ minh họa, nếu ở `@RestController` thì chuỗi là response body; nếu ở `@Controller` mà không có `@ResponseBody` thì chuỗi có thể được hiểu là view name. `Part` hữu ích khi cần API chuẩn Servlet hoặc truy cập headers của part; `MultipartFile` tiện hơn khi dự án thuần Spring MVC.

### MultipartFile hay Part?

| Nhu cầu | Ưu tiên thường hợp lý |
| --- | --- |
| Đa số API upload Spring MVC | `MultipartFile` |
| Muốn API Servlet không phụ thuộc Spring multipart abstraction | `jakarta.servlet.http.Part` |
| Xem headers raw của part | `Part` hoặc request multipart headers API |
| Dữ liệu file vài MB, cần stream | Cả hai có input stream |
| Upload metadata JSON thành DTO | **`@RequestPart("metadata") UploadMetadata`** |
| Cần nhận nhiều file cùng tên field | `List<MultipartFile>` hoặc array |
| Form HTML có fields text + file | `@ModelAttribute` hoặc `@RequestParam` cho fields, `MultipartFile` cho file |

Không cần biến tất cả các `Part` thành `MultipartFile` bằng cách copy bytes sang RAM để “thống nhất”. Chọn một abstraction rõ ràng theo contract/controller.

## 6. @RequestPart để ghép JSON metadata và file

Khi request multipart có JSON part `metadata` và file part `file`:

```java
@PostMapping(path = "/mixed",
             consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<?> upload(
        @Valid @RequestPart("metadata") UploadMetadata metadata,
        @RequestPart("file") MultipartFile file) {
    // ...
}
```

`@RequestPart("metadata") UploadMetadata` khác `@RequestParam String metadata`:

- `@RequestPart` cho DTO sử dụng `HttpMessageConverter` đọc **nội dung part** dựa trên **Content-Type của part**, tương tự `@RequestBody` với nguyên request body JSON.
- `@RequestParam("metadata") String` nhận text/raw parameter; để thành DTO thường phải tự parse JSON, không có đường converter chuẩn như `@RequestPart`.
- `@RequestPart("file") MultipartFile` nhận file trực tiếp. File bytes không bị Jackson tự đọc thành DTO.
- `@Valid` trên metadata khiến Spring kiểm tra các constraint sau khi converter deserialize; nếu validation fail mà không có `BindingResult` xử lý, thường nhận `400 Bad Request`.
- `consumes="multipart/form-data"` áp dụng cho **outer request**, còn JSON part phải có `Content-Type: application/json`. Một client gửi JSON string nhưng part bị gắn `text/plain` có thể gây `415 Unsupported Media Type` đối với DTO.

Đối chiếu:

| Annotation | Thường dùng để đọc | Cơ chế |
| --- | --- | --- |
| `@RequestBody Dto` | Một request có body JSON | JSON converter trên whole request |
| `@RequestPart("metadata") Dto` | Part có nội dung JSON của multipart | JSON converter **trên riêng part** |
| `@RequestPart("file") MultipartFile` | Part chứa tệp | Multipart argument handling |
| `@RequestParam("file") MultipartFile` | Form file field | Multipart argument handling |
| `@RequestParam String title` | Form field/query parameter | Conversion/binding của parameters |
| `@ModelAttribute UploadForm` | Các form fields và file properties | `WebDataBinder` property/constructor binding |

**Không đặt `@RequestBody UploadMetadata` chung với `@RequestPart MultipartFile` rồi mong JSON converter đọc được metadata từ một khối JSON riêng trong whole multipart.** Request body tổng thể là multipart envelope, không phải một JSON document. Phải dùng `@RequestPart` cho JSON part.

## 7. Ví dụ Spring Boot đầy đủ chạy được: JSON + file + Servlet Part

Ví dụ nhận một text file nhỏ và JSON metadata. Server trả JSON receipt gồm `title`, số byte đọc thực tế và SHA-256 digest (để minh họa xử lý stream, **không phải một cơ chế antivirus**). Có endpoint riêng minh họa `Part` và endpoint form đơn giản với `@RequestParam`.

### 7.1. Cây dự án Maven

```text
mvc-upload-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/upload/
    │   │   ├── UploadApplication.java
    │   │   └── UploadController.java
    │   └── resources/
    │       └── application.properties
    └── test/java/demo/upload/
        └── UploadControllerTest.java
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
    <artifactId>mvc-upload-demo</artifactId>
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

**`src/main/resources/application.properties`**

```properties
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=2MB
spring.servlet.multipart.max-request-size=5MB
spring.servlet.multipart.file-size-threshold=0B
spring.servlet.multipart.strict-servlet-compliance=true
```

Với demo, tệp lớn quá 2 MB sẽ bị từ chối qua multipart limits trước hoặc trong quá trình giải tham số. HTTP status/body cụ thể của lỗi upload size có thể phụ thuộc handler/container; cần test và có exception advice cho hợp đồng production nếu muốn response thống nhất.

**`src/main/java/demo/upload/UploadApplication.java`**

```java
package demo.upload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UploadApplication {
    public static void main(String[] args) {
        SpringApplication.run(UploadApplication.class, args);
    }
}
```

### 7.2. Controller có ba dạng nhận dữ liệu

**`src/main/java/demo/upload/UploadController.java`**

```java
package demo.upload;

import jakarta.servlet.http.Part;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    public record UploadMetadata(
            @NotBlank @Size(max = 80) String title,
            @NotBlank String category) {}

    public record UploadReceipt(
            String title,
            String category,
            long byteCount,
            String sha256) {}

    @PostMapping(
            path = "/mixed",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadReceipt> mixed(
            @Valid @RequestPart("metadata") UploadMetadata metadata,
            @RequestPart("file") MultipartFile file)
            throws IOException, NoSuchAlgorithmException {

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "File is empty");
        }
        // Chỉ là demo kiểm tra declared MIME; không thay content sniffing.
        if (!MediaType.TEXT_PLAIN_VALUE.equals(file.getContentType())) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only declared text/plain files are accepted");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size = 0;
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
                size += count;
            }
        }

        UploadReceipt receipt = new UploadReceipt(
                metadata.title(), metadata.category(),
                size, HexFormat.of().formatHex(digest.digest()));

        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    @PostMapping(
            path = "/part",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Long> servletPart(
            @RequestPart("file") Part part) throws IOException {

        long count;
        try (InputStream input = part.getInputStream()) {
            count = input.transferTo(OutputStream.nullOutputStream());
        }
        return Map.of("byteCount", count);
    }

    @PostMapping(
            path = "/simple",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> simple(
            @RequestParam("description") String description,
            @RequestParam("file") MultipartFile file) {

        if (description.isBlank() || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid upload");
        }
        return Map.of(
                "description", description,
                "byteCount", file.getSize());
    }
}
```

**Kiểm tra nghĩa của từng phần:**

1. `UploadMetadata` là record Java 17; Jackson đọc JSON part có `Content-Type: application/json` thành record.
2. `@Valid` kích hoạt `@NotBlank` và `@Size` trước khi controller làm xử lý nghiệp vụ. Nếu thiếu `title` hoặc `category`, thường trả `400` do `MethodArgumentNotValidException`.
3. `MultipartFile` cung cấp stream bytes và metadata. Ví dụ chỉ chấp nhận declared `text/plain` để có tình huống `415` minh họa; kiểm tra này **không bảo vệ** chống giả mạo MIME hay nội dung độc hại.
4. `MessageDigest SHA-256` đọc file theo các khối 8192 byte và tính digest, không gọi `getBytes()`. Tuy nhiên Servlet container vẫn có cơ chế buffer/file tạm; việc đọc stream ở controller không có nghĩa request luôn end-to-end streaming.
5. `byteCount` là số byte đã đọc thực, không phải số ký tự Unicode hay `String.length()`.
6. `servletPart` nhận `jakarta.servlet.http.Part`, dùng Java `InputStream.transferTo(OutputStream.nullOutputStream())` để đếm bytes trong stream. Đó là cách minh họa Servlet Part, **không ghi dữ liệu vĩnh viễn**.
7. `simple` minh họa form field `description` và tệp `file` qua `@RequestParam`. Không cần DTO JSON part cho trường hợp text field đơn giản.
8. Controller không dùng `getOriginalFilename()` làm path, không lưu file lên server. Đây là quyết định có chủ đích để tránh ví dụ bảo mật kém; mục 11 trình bày quy tắc lưu thật.

Một điểm kiểm tra compile: `InputStream.transferTo(OutputStream)` có từ Java 9; `OutputStream.nullOutputStream()` từ Java 11; `HexFormat` từ Java 17. Với Java 17, những API này hợp lệ.

### 7.3. Chạy project và tạo file mẫu

Ở thư mục `mvc-upload-demo` có `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Hai lệnh chạy **tuần tự**. JDK cần là 17+ và Maven hoạt động. Sau khi chạy app trên `localhost:8080`, tạo tệp test:

```bash
printf 'ABC' > note.txt
```

Lệnh này dùng shell Bash hoặc tương đương, và ghi đúng ba bytes ASCII `A`, `B`, `C` **không có newline**. Trên PowerShell, có thể tạo bằng byte array thay vì `Set-Content` (vì công cụ đó có thể đổi encoding/thêm newline).

Upload JSON + file bằng curl:

```bash
curl -i -X POST 'http://localhost:8080/api/uploads/mixed' \
  -H 'Accept: application/json' \
  -F 'metadata={"title":"Ghi chú","category":"notes"};type=application/json' \
  -F 'file=@note.txt;type=text/plain'
```

Đường curl `-F` tự tạo `multipart/form-data` và boundary; **không cần** thêm `-H 'Content-Type: multipart/form-data'` thủ công. `type=application/json` ở metadata part là chi tiết quyết định để JSON converter đọc record.

Phần response chính dự kiến:

```http
HTTP/1.1 201 Created
Content-Type: application/json

{"title":"Ghi chú","category":"notes","byteCount":3,"sha256":"b5d4045c3f466fa91fe2cc6abe79232a1a57cdf104f7a26e716e0a1e2789df78"}
```

Đây là SHA-256 của đúng bytes ASCII `ABC`. Nếu file có newline hoặc mã hóa khác, SHA-256 và byteCount sẽ khác. Thứ tự các JSON keys và whitespace không phải hợp đồng cố định; khi test, nên so sánh JSON fields.

### 7.4. Upload bằng Servlet Part

```bash
curl -i -X POST 'http://localhost:8080/api/uploads/part' \
  -F 'file=@note.txt;type=text/plain'
```

Response body:

```json
{"byteCount":3}
```

Ở đây Spring chuyển part `file` cho `Part`; controller không cần `MultipartFile`. Nếu dùng `Part.write(String)` để lưu thật, cần đọc thêm quy tắc multipart temp location/container và không dùng raw filename client làm đường dẫn lưu.

### 7.5. Form field thông thường + file

```bash
curl -i -X POST 'http://localhost:8080/api/uploads/simple' \
  -F 'description=Meeting notes' \
  -F 'file=@note.txt;type=text/plain'
```

Response dự kiến:

```json
{"description":"Meeting notes","byteCount":3}
```

Khác với `/mixed`, field `description` là một text form field bình thường, không phải một JSON document; `@RequestParam` phù hợp hơn.

### 7.6. Trường hợp lỗi cần quan sát

**File rỗng:**

```bash
: > empty.txt
curl -i 'http://localhost:8080/api/uploads/mixed' \
  -F 'metadata={"title":"Empty","category":"notes"};type=application/json' \
  -F 'file=@empty.txt;type=text/plain'
```

Dự kiến `400 Bad Request` do `file.isEmpty()`. Mẫu `: > empty.txt` là cú pháp Bash; trên PowerShell cần dùng cách tạo file 0 bytes khác.

**Metadata không hợp lệ:**

```bash
curl -i 'http://localhost:8080/api/uploads/mixed' \
  -F 'metadata={"title":"","category":"notes"};type=application/json' \
  -F 'file=@note.txt;type=text/plain'
```

Dự kiến `400` do `@NotBlank`. Hình dạng error body phụ thuộc cấu hình Boot/MVC exception handling; demo không định nghĩa một error JSON schema cụ thể.

**JSON part không đúng media type:**

```bash
curl -i 'http://localhost:8080/api/uploads/mixed' \
  -F 'metadata={"title":"Demo","category":"notes"};type=text/plain' \
  -F 'file=@note.txt;type=text/plain'
```

Thường nhận `415 Unsupported Media Type`: outer body vẫn là multipart hợp lệ, nhưng `HttpMessageConverter` không đọc được một DTO `UploadMetadata` từ part `text/plain`. Không nhầm với file content type `text/plain`, vì file part được nhận như bytes.

**File khai MIME khác:**

```bash
curl -i 'http://localhost:8080/api/uploads/mixed' \
  -F 'metadata={"title":"Demo","category":"notes"};type=application/json' \
  -F 'file=@note.txt;type=application/octet-stream'
```

Dự kiến `415` từ kiểm tra do controller tự viết. Nếu client khai `text/plain` nhưng nội dung thật là mã độc hoặc binary, controller demo **không phát hiện** chỉ qua header; phải áp dụng chính sách xác thực nội dung và quét tệp khi hệ thống lưu/cho phép truy cập.

### 7.7. Client browser với FormData

Một HTML form cho tệp:

```html
<form method="post" action="/api/uploads/simple"
      enctype="multipart/form-data">
    <input name="description" type="text" required>
    <input name="file" type="file" required>
    <button type="submit">Upload</button>
</form>
```

Form này POST multipart tới endpoint `/simple`. Vì server trả JSON, browser sẽ hiển thị JSON response; trong sản phẩm thật có thể redirect/render view hoặc dùng JavaScript.

Với `fetch` và JSON part:

```javascript
const metadata = {title: "Ghi chú", category: "notes"};
const fileInput = document.querySelector("#file");
const file = fileInput.files[0];

const data = new FormData();
data.append(
  "metadata",
  new Blob([JSON.stringify(metadata)], {type: "application/json"})
);
data.append("file", file);

const response = await fetch("/api/uploads/mixed", {
  method: "POST",
  body: data
});

if (!response.ok) {
  throw new Error("Upload failed: HTTP " + response.status);
}
const receipt = await response.json();
console.log(receipt);
```

Đây là **đoạn JavaScript dùng trong browser** có một input `id="file"` trong DOM; không phải chương trình tự chạy độc lập nếu chưa tạo input và script context. `FormData.append("metadata", Blob application/json)` đảm bảo part JSON có media type thích hợp. **Đừng đặt thủ công outer `Content-Type` trong fetch**; browser tự sinh boundary phù hợp. Kiểm tra `file` tồn tại và kích thước ở client để cải thiện UX, nhưng server vẫn phải validate độc lập.

## 8. MockMvc kiểm thử multipart, JSON converter và Servlet Part

MockMvc có `MockMultipartFile` và `MockPart` để mô phỏng một HTTP multipart request. Đây là cách tốt để kiểm chứng behavior mà không chạy server port thật.

**`src/test/java/demo/upload/UploadControllerTest.java`**

```java
package demo.upload;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UploadControllerTest {

    @Autowired
    MockMvc mvc;

    private MockMultipartFile metadata(String json, String mime) {
        return new MockMultipartFile(
                "metadata", "", mime,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile file(String mime, byte[] content) {
        return new MockMultipartFile(
                "file", "note.txt", mime, content);
    }

    @Test
    void acceptsJsonMetadataAndTextFile() throws Exception {
        mvc.perform(multipart("/api/uploads/mixed")
                    .file(metadata(
                        "{\"title\":\"Demo\",\"category\":\"notes\"}",
                        "application/json"))
                    .file(file("text/plain",
                        "ABC".getBytes(StandardCharsets.UTF_8))))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.title").value("Demo"))
           .andExpect(jsonPath("$.category").value("notes"))
           .andExpect(jsonPath("$.byteCount").value(3))
           .andExpect(jsonPath("$.sha256").value(
               "b5d4045c3f466fa91fe2cc6abe79232a1a57cdf104f7a26e716e0a1e2789df78"));
    }

    @Test
    void invalidJsonMetadataFailsValidation() throws Exception {
        mvc.perform(multipart("/api/uploads/mixed")
                    .file(metadata(
                        "{\"title\":\"\",\"category\":\"notes\"}",
                        "application/json"))
                    .file(file("text/plain", new byte[]{65})))
           .andExpect(status().isBadRequest());
    }

    @Test
    void nonJsonMetadataPartIsUnsupported() throws Exception {
        mvc.perform(multipart("/api/uploads/mixed")
                    .file(metadata(
                        "{\"title\":\"Demo\",\"category\":\"notes\"}",
                        "text/plain"))
                    .file(file("text/plain", new byte[]{65})))
           .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        mvc.perform(multipart("/api/uploads/mixed")
                    .file(metadata(
                        "{\"title\":\"Demo\",\"category\":\"notes\"}",
                        "application/json"))
                    .file(file("text/plain", new byte[0])))
           .andExpect(status().isBadRequest());
    }

    @Test
    void servletPartIsRead() throws Exception {
        MockPart part = new MockPart(
                "file", "note.txt",
                "ABC".getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(MediaType.TEXT_PLAIN);

        mvc.perform(multipart("/api/uploads/part").part(part))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.byteCount").value(3));
    }

    @Test
    void ordinaryMultipartFieldsCanUseRequestParam()
            throws Exception {
        mvc.perform(multipart("/api/uploads/simple")
                    .param("description", "notes")
                    .file(file("text/plain", new byte[]{65, 66})))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.description").value("notes"))
           .andExpect(jsonPath("$.byteCount").value(2));
    }
}
```

Chạy:

```bash
mvn test
```

Bộ test khai báo **6 test methods**. Khi chúng pass trên project đã tạo đúng file, Maven kết thúc exit code `0`; log thường có thông tin tương tự `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`. Những test này xác minh MVC argument resolution và converter; **MockMvc không đồng nhất với proxy/container thật** về mọi giới hạn upload, filesystem temp, network streaming, multipart parsing quá lớn hoặc security filters. Hãy bổ sung integration tests qua embedded server và reverse proxy cho upload production.

### Vì sao mock test cần Content-Type riêng của metadata part?

`MockMultipartFile("metadata", "", "application/json", ...)` thiết lập MIME của metadata là JSON. Nếu part được khai text/plain, `@RequestPart UploadMetadata` cần converter hiểu DTO từ text/plain và thường không có. Khi kiểm tra raw multipart, cần nhìn **part headers**, không chỉ outer `multipart/form-data`.

### Test kích thước và encoding

Lệnh `mvn test` ở trên không khẳng định upload 3MB sẽ luôn trả status cụ thể qua real Tomcat. `MockMvc` thường không đi qua cùng cơ chế kích thước parser của container. Test thực qua HTTP server nên bao gồm:

- File 0 bytes, file sát ngưỡng 2MB và file vượt ngưỡng.
- Tổng request (nhiều parts) vượt `max-request-size`.
- Filename chứa `../`, `\\`, Unicode/emoji và quá dài.
- MIME header khai sai so với magic bytes.
- JSON part thiếu `Content-Type`, sai charset, malformed JSON và DTO vi phạm constraints.
- Client dừng upload giữa chừng, network timeouts, retry, concurrent requests.
- Nội dung tệp không đáng tin chứa script, HTML, executable, archive bomb hoặc file format exploit.

## 9. Nhiều file và tên part

Nếu frontend gửi nhiều files với cùng name `files`:

```java
@PostMapping(
        path = "/batch",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public List<Long> batch(
        @RequestParam("files") List<MultipartFile> files) {
    return files.stream().map(MultipartFile::getSize).toList();
}
```

Đây là method minh họa trong `@RestController`, cần import `List`, `MultipartFile`, `RequestParam`, `PostMapping` và `MediaType`. Client có thể dùng:

```bash
curl -F 'files=@a.txt' -F 'files=@b.txt' \
  'http://localhost:8080/api/uploads/batch'
```

Nhưng endpoint `/batch` **không thuộc project runnable ở mục 7** trừ khi bạn chủ động thêm method đó vào controller; lệnh curl là hướng dẫn riêng cho biến thể minh họa. Với nhiều files, cần tổng giới hạn dung lượng, giới hạn số part, concurrency và tránh lưu tất cả byte arrays trong RAM.

Nếu một request có cả JSON metadata và nhiều files, khai báo `@Valid @RequestPart("metadata") Dto metadata` và `@RequestPart("files") List<MultipartFile> files`; xác nhận frontend dùng đúng tên và JSON part content type.

## 10. @ModelAttribute cho form field và file trên một bean

Trong HTML form truyền thống, bạn có thể bind text fields cùng `MultipartFile` vào một object:

```java
public class UploadForm {
    @NotBlank
    private String title;
    private MultipartFile file;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public MultipartFile getFile() { return file; }
    public void setFile(MultipartFile file) { this.file = file; }
}
```

Method trong `@Controller`:

```java
@PostMapping(path = "/forms", consumes = "multipart/form-data")
public String handle(
        @Valid @ModelAttribute("uploadForm") UploadForm form,
        BindingResult errors) {
    if (errors.hasErrors()) {
        return "uploads/new";
    }
    // Kiểm tra file != null, !file.isEmpty() và xử lý storage.
    return "redirect:/uploads/done";
}
```

Hai đoạn này là **snippets minh họa**, chưa có imports, class controller/templates và phương án persistence đầy đủ. `@ModelAttribute` dùng `WebDataBinder` để liên kết fields `title` và `file` với DTO; `@Valid` có thể kiểm tra `title`, còn `MultipartFile` cần quy tắc kiểm tra file riêng (hoặc custom validator). `BindingResult` nên đứng ngay sau tham số được validate.

**Khi nào chọn cách nào?**

- HTML form text + file, không có JSON part: `@ModelAttribute UploadForm` hoặc nhiều `@RequestParam` thường tự nhiên nhất.
- API JSON metadata + file, muốn nested DTO và Bean Validation: `@RequestPart("metadata") Dto`.
- File raw không metadata: `@RequestParam MultipartFile`/`@RequestPart MultipartFile`.
- Kiểm soát header/container-level của part: `Part`.

Không gượng ép form text field thành một chuỗi JSON rồi parse thủ công chỉ vì muốn tái sử dụng DTO, nếu UX/API không yêu cầu JSON part.

## 11. Lưu file an toàn: thiết kế production

Ví dụ ở mục 7 **không lưu file**, chỉ xử lý stream. Khi lưu file thật, cần thiết kế nhiều lớp phòng vệ:

### 11.1. Không tin filename do client cung cấp

`getOriginalFilename()` hoặc `Part.getSubmittedFileName()` là metadata **do client gửi**. Không được dùng thẳng làm path:

```java
// PHẢN VÍ DỤ: file name có thể là "../../secrets.txt".
Path target = uploadsDir.resolve(file.getOriginalFilename());
file.transferTo(target);
```

Đây là snippet **không an toàn**, không dùng trong production. Tên có thể chứa path separators, encoding lạ, reserved names, `..`, unicode normalization; ghép path làm rủi ro path traversal hoặc ghi đè.

Thiết kế tốt hơn: sinh storage key server-side (UUID/random), giữ tên client riêng làm metadata sau khi sanitize và giới hạn độ dài/charset. Nếu buộc cho phép download dưới tên gốc, encode `Content-Disposition` an toàn; không tự nối headers bằng raw filename.

Ví dụ ý tưởng (chưa phải upload service hoàn chỉnh):

```java
String storageKey = java.util.UUID.randomUUID().toString();
Path root = Path.of("/srv/app-upload-data").toAbsolutePath();
Path target = root.resolve(storageKey).normalize();
if (!target.startsWith(root)) {
    throw new SecurityException("Invalid upload target");
}
```

Cần kiểm tra và quản lý **filesystem symlinks**, permissions, path existence/atomic create và race conditions; phép `startsWith` đơn lẻ không giải quyết mọi rủi ro. Server-owned key tránh phần lớn path injection nhưng không thay quyền filesystem. Dùng thư mục ngoài web root để nội dung upload không tự được phục vụ như static resources.

### 11.2. MIME, extension và nội dung thật

`file.getContentType()` và extension `.pdf`/`.png` không phải bằng chứng nội dung thật. Với loại file được cho phép:

1. Kiểm tra declared MIME + extension (tham khảo, không quyết định cuối).
2. Đọc magic bytes hoặc parse cấu trúc file bằng thư viện phù hợp; giới hạn parser/resource.
3. Chỉ chấp nhận allowlist formats cần cho nghiệp vụ.
4. Nếu file có thể độc hại, quét malware qua quy trình phù hợp.
5. Đặt download `Content-Type` và `Content-Disposition` đúng, hạn chế browser sniffing (ví dụ `X-Content-Type-Options: nosniff`).
6. Với ảnh/video, cân nhắc transcode/re-encode bằng pipeline an toàn để giảm rủi ro embedded payload.
7. Đừng xử lý file dữ liệu do client gửi như executable, HTML được tin cậy hoặc template phía server.

### 11.3. Size limit và resource exhaustion

Giới hạn cần tồn tại ở nhiều tầng: reverse proxy, ingress/API gateway, Servlet parser, app policy (file type/count/size), storage quota, CPU/memory của parser, thời gian upload và total concurrent uploads. Multipart parser có thể viết dữ liệu tạm ra disk; một hệ thống hết disk vì nhiều files hợp lệ về kích thước cũng bị DoS.

Nếu dùng `file.getBytes()`, toàn nội dung nạp vào heap. Nếu dùng stream, giảm một loại memory copy nhưng request multipart vẫn có thể được buffer tạm; không đồng nghĩa với upload streaming từ network đến object storage. Với file hàng GB, cân nhắc resumable/chunked upload hoặc direct-to-object-storage có URL upload ngắn hạn và policy bảo mật rõ ràng.

### 11.4. Dữ liệu JSON metadata cũng phải validate và authorize

Chỉ cho client gửi những fields được phép. Đừng bind metadata vào JPA entity có `ownerId`, `role`, `tenantId`, `approved` rồi tin những giá trị đó. Lấy identity/tenant từ authentication context và kiểm tra quyền truy cập trước khi gắn file vào resource.

Nếu metadata ghi `ownerId` do client cung cấp, backend vẫn phải xác minh user được phép thao tác trên owner đó. `@Valid` và converter bảo đảm cú pháp/constraints, **không thay authorization**.

### 11.5. Storage + database không tự atomic

Một workflow robust có thể là:

```text
1. Validate authentication, authorization và metadata
2. Kiểm tra file count/size/type
3. Ghi file vào vùng staging/quarantine bằng key do server sinh
4. Scan/parse theo policy
5. Ghi DB metadata + trạng thái PENDING/READY theo transaction
6. Công bố file chỉ khi trạng thái READY
7. Nếu lỗi: cleanup/compensation, retry có kiểm soát
```

Đây là sequence kiến trúc, không phải lời hứa tính atomic của filesystem + SQL. Nếu DB commit lỗi sau khi file đã ghi, cần cleanup; nếu file thất bại sau khi DB đã tạo record, cần trạng thái/tái thử hoặc compensation. Đừng công bố download URL trước khi file được chấp nhận.

### 11.6. Security context và CSRF

Với app web dựa vào session/cookie có Spring Security, multipart form POST thường cần CSRF protection; file upload không phải ngoại lệ. API dùng bearer tokens và không dựa vào cookie có chính sách khác. Ví dụ Boot demo không thêm Spring Security, vì vậy không chứng minh chiến lược CSRF production.

Nếu cho phép upload từ browser domain khác, phải thiết kế CORS có giới hạn origins/methods/headers; CORS không tự xác thực người gửi hoặc chống CSRF.

## 12. Exception handling và HTTP status

| Lỗi | Nguồn thường gặp | Status điển hình |
| --- | --- | --- |
| Thiếu part bắt buộc | `@RequestPart("file")` hoặc `metadata` không được gửi | `400 Bad Request` |
| JSON part sai cú pháp | Jackson converter không đọc được metadata | `400 Bad Request` |
| JSON part bị khai `text/plain` | Không có converter đọc DTO cho media type đó | `415 Unsupported Media Type` |
| `@Valid` metadata không đạt | `MethodArgumentNotValidException` | `400 Bad Request` |
| File rỗng theo policy demo | Controller ném `ResponseStatusException(BAD_REQUEST)` | `400` |
| File có declared MIME không được phép | Controller policy trong demo | `415` |
| File vượt size limit | Multipart resolver/container, `MaxUploadSizeExceededException` tùy cấu hình | Thường `413 Payload Too Large` khi được map phù hợp; kiểm tra môi trường |
| Outer Content-Type không phải multipart | `consumes=multipart/form-data` không khớp | `415` |
| Disk đầy, I/O hoặc scanner lỗi | Storage/server | `5xx`; không lộ server path/stack |
| File/metadata thuộc tenant khác | Authorization ở service | `403` hoặc `404` theo security policy |

**Đừng hứa một status duy nhất cho mọi môi trường multipart**: lỗi size-limit có thể xảy ra tại reverse proxy/container trước khi đi vào controller/advice, và khi response đã commit không thể đổi status. Nếu API yêu cầu một error contract ổn định, cần kiểm thử end-to-end và cấu hình error responses ở đúng tầng.

Một handler minh họa cho size limit trong MVC, **không bắt buộc** để chạy demo:

```java
@RestControllerAdvice
public class UploadErrorAdvice {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> tooLarge(
            MaxUploadSizeExceededException exception) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded content exceeds the configured limit.");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(p);
    }
}
```

Đây là snippet trong file riêng, cần imports `RestControllerAdvice`, `ExceptionHandler`, `ResponseEntity`, `ProblemDetail`, `HttpStatus` và `MaxUploadSizeExceededException`. Đường xử lý này chỉ áp dụng nếu exception tới MVC resolver; reverse proxy chặn trước sẽ không đi vào advice. `ProblemDetail` thuộc Spring 6.x hỗ trợ RFC 9457.

## 13. Lỗi thường gặp khi kết hợp JSON với file

| Hiện tượng | Nguyên nhân | Cách khắc phục |
| --- | --- | --- |
| `@RequestPart Dto` nhận 415 dù body nhìn giống JSON | JSON part có Content-Type text/plain/missing, converter không hỗ trợ | Gửi `metadata` part với `application/json` |
| `MissingServletRequestPartException` | Tên part sai hoặc không gửi | Kiểm tra `name="metadata"` và `@RequestPart("metadata")` |
| Outer multipart request không parse được | Header boundary thiếu/sai, client tự đặt Content-Type | Để browser/curl tạo boundary tự động |
| `@RequestBody Dto` không parse được request multipart | Đang đọc whole multipart như JSON | Dùng `@RequestPart` cho JSON part |
| File luôn có size 0 | File rỗng hoặc client gửi sai field | Kiểm tra `file.isEmpty()`, raw part bytes và tên |
| ByteCount khác độ dài String | Unicode charset / newline / binary | Đếm bytes từ input stream, không dùng `String.length()` |
| Validation JSON không chạy | Quên `@Valid` hoặc validator dependency | Thêm `@Valid` và starter-validation |
| Metadata đúng nhưng chưa bảo vệ file | Chỉ validate DTO, không kiểm tra content/policy | Xác thực quyền, size, MIME/magic, scanner |
| Upload hiện HTML error, client mong JSON | Exception xảy ra ngoài controller/advice | Thống nhất error handling ở MVC + proxy/container |
| Upload lớn làm tăng RAM | Dùng `file.getBytes()` hoặc buffer toàn bộ | Giới hạn kích thước, dùng stream/appropriate storage pipeline |
| Trùng filename làm ghi đè file cũ | Tin `getOriginalFilename()` | Sinh UUID/storage key server-side |
| File có `Content-Type: image/png` nhưng là script | Trust header client | Kiểm tra bytes và parser, không execute tệp |
| Bắt `Part` không thành công | Servlet multipart config/resolver/container khác nhau | Xác nhận Boot multipart enabled, Servlet API và part name |
| File biến mất trong async job | Multipart temp cleanup sau request | Di chuyển vào durable storage trước khi trả/queue |
| Metadata/DB đã lưu nhưng file thất bại | Không có workflow compensation | Dùng staging + transaction state + cleanup |
| 413 không vào ControllerAdvice | Proxy/container reject từ sớm | Test giới hạn ở từng tầng; cấu hình error policy |

Quy trình debug:

1. Kiểm tra HTTP request **outer Content-Type** và boundary trước.
2. Xem từng part: `Content-Disposition name`, filename, **part Content-Type**, byte count.
3. Đối chiếu names với `@RequestPart` / `@RequestParam`.
4. Nếu DTO không deserialize, xác định converter và lỗi 400/415; nếu DTO hợp lệ mà file sai, kiểm tra upload policy.
5. Xem multipart limits ở proxy, Servlet/container và Boot.
6. Với production, kiểm tra storage state, cleanup, scanner và authorization; không log raw file bytes, token hay PII.

## 14. Nên chọn thiết kế API nào?

| Trường hợp | Cách nên làm |
| --- | --- |
| Upload đơn file không metadata | `@RequestParam("file") MultipartFile` hoặc `@RequestPart` |
| Upload file + vài form text fields | `@RequestParam` hoặc `@ModelAttribute UploadForm` |
| Upload file + metadata JSON lồng nhau | `@Valid @RequestPart("metadata") Dto` + `@RequestPart("file") MultipartFile` |
| Cần Servlet-native access | `jakarta.servlet.http.Part` |
| Nhiều tệp cùng một field | `List<MultipartFile>` và giới hạn số lượng |
| Ứng dụng WebFlux | `FilePart` và reactive codecs, không dùng MVC `MultipartFile` |
| Tệp rất lớn | Streaming/resumable/direct-to-storage architecture, không `getBytes()` |
| API cần upload + transaction nghiệp vụ | Staging, state machine, compensation, idempotency |
| Cần kiểm tra format thực | File signature/parser/sandbox scanning, không chỉ client MIME |
| Client dùng browser `fetch` | `FormData` + JSON `Blob` với application/json; để browser tạo boundary |

## 15. Bài tập tự kiểm tra

1. Multipart outer request và JSON part có hai `Content-Type` khác nhau như thế nào?
2. Vì sao `@RequestPart("metadata") UploadMetadata` có thể deserialize JSON còn `@RequestParam("metadata") String` không tự thành DTO?
3. Khi nào dùng `MultipartFile`, khi nào dùng Servlet `Part`?
4. Tại sao `@RequestBody UploadMetadata` không thay thế được `@RequestPart` trong request multipart?
5. File có `filename="../../etc/passwd"` hoặc Content-Type `image/png` có đáng tin không?
6. Vì sao upload service không nên giữ `MultipartFile` để đọc stream sau request đã kết thúc?
7. Khi dùng `curl -F` hoặc `fetch(FormData)`, tại sao không nên tự đặt outer Content-Type không có boundary?
8. `@Valid` có phát hiện malware hoặc path traversal trong tên file không?
9. Lý do gì khiến `MockMvc` size test không thay cho kiểm thử thật qua Servlet container?
10. Tại sao upload file thành công nhưng insert database thất bại không được giải quyết bởi một SQL transaction đơn thuần?
11. Vì sao return byteCount bằng cách đọc `InputStream` an toàn hơn `String.length()` cho binary?
12. Nếu metadata part gửi `text/plain` nhưng nội dung là `{"title":"..."}`, Spring dựa vào thông tin nào để chọn converter?

**Gợi ý:** (1) Outer multipart/form-data có boundary; part JSON là application/json. (2) RequestPart dùng HttpMessageConverter trên part; RequestParam String lấy text. (3) Spring convenience so với Servlet-native API. (4) Whole body không phải JSON mà là multipart envelope. (5) Không, cả filename và MIME đều do client gửi. (6) Temp content có thể bị dọn sau request. (7) Boundary của payload phải khớp header. (8) Không, đây là policy/security độc lập. (9) Container/proxy và temporary disk handling khác mock. (10) Filesystem/object storage không nằm trong cùng DB transaction. (11) Byte count không phụ thuộc encoding. (12) Part Content-Type và converter cho Java target type.

## 16. Tài liệu chính thức

- [Spring Framework 6.1 — Multipart Resolver](https://docs.spring.io/spring-framework/reference/6.1/web/webmvc/mvc-servlet/multipart.html)
- [Spring Framework — Multipart Forms và @RequestPart](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html)
- [Spring Framework 6.1 — MultipartFile Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/multipart/MultipartFile.html)
- [Spring Framework 6.1 — RequestPart Javadoc](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/bind/annotation/RequestPart.html)
- [Spring Framework 6.1 — RequestPartMethodArgumentResolver](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/RequestPartMethodArgumentResolver.html)
- [Spring Boot 3.3 — Common Application Properties, Multipart](https://docs.spring.io/spring-boot/3.3/appendix/application-properties/index.html)
- [Jakarta Servlet — Part API](https://jakarta.ee/specifications/servlet/6.0/apidocs/jakarta.servlet/jakarta/servlet/http/part)
- [RFC 7578 — Returning Values from Forms: multipart/form-data](https://www.rfc-editor.org/rfc/rfc7578)
- [Spring Framework — HTTP Message Converters](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html)

**Kết luận:** Với multipart/form-data, Spring MVC đưa file parts vào `MultipartFile` hoặc `Part`, còn JSON part có thể được giải mã bằng `@RequestPart Dto` qua `HttpMessageConverter`. Một API upload dùng được ngoài thực tế phải có hợp đồng MIME và part names rõ ràng, validation DTO, kiểm tra bytes/size/quyền truy cập, lưu trữ an toàn và kế hoạch xử lý lỗi/cleanup; upload là một bài toán HTTP, dữ liệu và bảo mật, không chỉ là gọi `getBytes()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-resources-003">Giới hạn dung lượng, số tệp, kiểu tệp và tên tệp không tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn dung lượng, số tệp, kiểu tệp và tên tệp không tin cậy trong Spring MVC

## 1. Mục tiêu và phạm vi

Upload không chỉ là nhận một đối tượng `MultipartFile` rồi ghi bytes xuống ổ đĩa. Client kiểm soát dữ liệu, tên tệp và nhiều HTTP headers, có thể cố tình gửi quá nhiều tệp, tệp quá lớn, định dạng giả mạo, metadata bất thường hoặc một tài liệu mà lúc giải mã phình ra hàng gigabyte. API cần nhiều tầng phòng vệ để tránh chiếm tài nguyên, ghi đè tệp, path traversal, XSS và lộ dữ liệu.

Bài này tập trung **Spring MVC (Servlet stack), Java 17+, Spring Boot 3.x/Spring Framework 6.x**, bao gồm:

- Phân biệt giới hạn **một file**, **một multipart request**, **số file/part**, quota và concurrency.
- Cấu hình `spring.servlet.multipart.max-file-size`, `max-request-size`, `file-size-threshold` và vai trò Servlet container/proxy.
- Kiểm tra `MultipartFile.isEmpty()`, `getSize()`, kiểu file, magic bytes và nội dung bằng parser tin cậy.
- Vì sao `getContentType()` và `getOriginalFilename()` là dữ liệu **không đáng tin**.
- Sinh storage key ngẫu nhiên, lưu ngoài webroot, xử lý rollback/cleanup, scan/quarantine và quyền download.
- Phân biệt lỗi HTTP 400/413/415 và nơi lỗi được phát hiện.
- Xây ví dụ Spring Boot 3 có thể chạy cùng curl, MockMvc, bảng tình huống kiểm thử và checklist production.

**Ranh giới bảo mật:** Ví dụ code chỉ nhận PNG/JPEG và kiểm tra magic bytes, không có image decoder/antivirus; **không đủ an toàn để tự động phát ảnh cho người dùng trên production**. Nếu triển khai thật, cần kiểm tra cấu trúc tệp đầy đủ, giới hạn pixel/CPU, tái mã hóa, quét malware và quyền truy cập theo rủi ro.

## 2. Đường đi của multipart upload

```text
Client: multipart/form-data
         |
         v
Reverse proxy / CDN / WAF
  [body bytes, rate limit, timeouts, connection limits]
         |
         v
Servlet container (Tomcat / Jetty / Undertow)
  [parse parts, temp space, header and part limits]
         |
         v
Spring multipart handling
  [MultipartConfigElement / StandardServletMultipartResolver]
  [spring.servlet.multipart.*]
         |
         v
Spring MVC controller
  @RequestParam("files") List<MultipartFile>
         |
         v
Application policy
  [1..3 files, no empty, <=2 MiB each]
  [allowlist type + signature + full format validation]
         |
         v
Private staging / AV / CDR / decoder / metadata
         |
         v
Private accepted storage, authorized download
         |
         v
JSON response with server-generated ID, never raw path
```

Mỗi tầng bảo vệ một vấn đề khác. `max-file-size` không thay hạn mức số multipart parts; một check `files.size()` chỉ chạy **sau khi parser đã xử lý request**; chỉ dùng antivirus cũng không thay rate limiting. Trong hệ thống thật cần limits nhất quán từ edge đến service.

### Khác biệt file và multipart part

Một `multipart/form-data` request có nhiều phần: file, chuỗi text, các field ẩn và metadata. Nhiều part không phải file vẫn khiến parser dùng RAM/CPU/disk. Vì vậy policy “tối đa ba file” **khác** “tối đa ba part”; cần kiểm soát cả số part ở container và số file ở application.

## 3. MultipartFile: những giá trị nào đáng tin?

Ví dụ controller:

```java
@PostMapping(path = "/api/uploads",
             consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<?> upload(
        @RequestParam("files") List<MultipartFile> files) {
    // Xử lý policy ở service rồi lưu vào vùng riêng.
}
```

Đây chỉ là snippet controller; ứng dụng đầy đủ ở phần sau.

| API | Ý nghĩa | Cần lưu ý |
| --- | --- | --- |
| `getName()` | Tên parameter/part, ví dụ `files` | Không dùng làm path |
| `getOriginalFilename()` | Tên do **client** khai báo | Không tin, có thể chứa `../` hoặc backslash |
| `getContentType()` | MIME client khai báo cho **part** | Dễ giả mạo, có thể null |
| `getSize()` | Dung lượng bytes đã nhận cho file | Cần check nhưng đã qua bước parse |
| `isEmpty()` | Empty hoặc chưa chọn file | Cần policy nếu file không được rỗng |
| `getInputStream()` | Stream bytes file đã upload | Phải đóng stream; tránh đọc hết file lớn vào heap |
| `transferTo(Path/File)` | Chuyển/copy part sang nơi khác tùy implementation | Chỉ truyền path do server tạo |

`getOriginalFilename()` là điểm Spring Javadoc đặc biệt cảnh báo: có thể chứa đường dẫn hoặc ký tự độc hại, không dùng nguyên tên trong filesystem. Không dùng `getContentType()` làm bằng chứng rằng bytes là PNG/PDF. Header do client đặt được.

`MultipartFile` gắn với vòng đời multipart request. Không giữ object này trong background queue để đọc sau khi request kết thúc; nếu cần scan async, copy sang vùng staging riêng và chỉ truyền storage ID cho worker.

## 4. Cấu hình giới hạn ở Spring Boot 3

```properties
spring.servlet.multipart.enabled=true

# Tối đa 2 MiB/file
spring.servlet.multipart.max-file-size=2MB

# Tổng multipart request tối đa 7 MiB
spring.servlet.multipart.max-request-size=7MB

# Ngưỡng chuyển nội dung multipart ra temp disk
spring.servlet.multipart.file-size-threshold=0B

# Property tùy chỉnh của ứng dụng mẫu
app.upload.directory=./private-uploads
```

Ý nghĩa:

- `max-file-size` giới hạn từng file trong multipart, Boot 3.x mặc định thường **1MB**.
- `max-request-size` giới hạn toàn request bao gồm bytes của file, field text, boundary và headers từng part, Boot 3.x thường mặc định **10MB**.
- `file-size-threshold` quyết định thời điểm dữ liệu được ghi vào disk; **không phải** giới hạn file upload.
- `spring.servlet.multipart.location` có thể chỉ định thư mục **tạm** cho multipart parser; không nên nhầm nơi tạm với nơi lưu bền.
- `app.upload.directory` là cấu hình riêng của demo, không phải Spring Boot tự phát sinh storage service.

Nếu ba file × 2MiB = 6MiB, giới hạn request 7MiB để dành overhead là ví dụ hợp lý cho một bài học, **không đảm bảo đủ mọi multipart request hợp lệ**. Filename/field metadata và proxy overhead có thể làm thay đổi tổng bytes.

### Giới hạn ở edge và Servlet container

Reverse proxy có thể chặn body ở mức nhỏ hơn Boot config. Container có thể có chính sách riêng về số multipart parts, request parameters, kích thước headers, thời gian nhận dữ liệu và thư mục lưu tạm. Cách cấu hình khác nhau theo Tomcat/Jetty/Undertow **và từng phiên bản**; không tự bịa một property chung cho tất cả. Cần đọc đúng tài liệu server đang chạy.

App-level check `getSize()` không ngăn parser đọc request lớn trước khi service chạy. Do đó ba tầng **gateway / multipart container / ứng dụng** nên cùng có trần.

## 5. Giới hạn số lượng file và quota

Hợp đồng API ví dụ: **1–3 file cho mỗi request**. Một kiểm tra sau parse:

```java
if (files == null || files.isEmpty()) {
    throw rejected("At least one file is required");
}
if (files.size() > 3) {
    throw rejected("At most three files are allowed");
}
```

Đây là snippet trong service sẽ định nghĩa bên dưới. Việc đếm file hữu ích cho nghiệp vụ nhưng chưa chặn một request chứa hàng nghìn **text parts**. Production có thể cần thêm:

- Số multipart parts/fields, độ dài mỗi field, tổng metadata bytes.
- Số file và tổng bytes mỗi người dùng/ngày, mỗi tenant, tổng quota storage.
- Tốc độ upload và số request đồng thời; timeout cho client gửi chậm.
- Số tác vụ giải mã/quét virus chạy đồng thời; CPU/RAM/disk budget.
- Số phần tử và tổng bytes **sau giải nén**, ngăn ZIP bombs.
- Số pixel và số frame ảnh/video để ngăn file nhỏ giải mã thành dữ liệu khổng lồ.

Các hạn mức này phải đặt ở lớp thực sự nhìn thấy chúng: container không biết quota nghiệp vụ user, còn service sau parse không chặn mọi chi phí parser.

## 6. Xác thực loại file: extension, MIME, signature và decoder

| Tín hiệu | Ví dụ | Độ tin cậy |
| --- | --- | --- |
| Extension tên gốc | `avatar.png` | Thấp: client đổi được |
| `MultipartFile.getContentType()` | `image/png` | Thấp: client giả mạo được |
| Magic bytes | PNG: `89 50 4E 47 0D 0A 1A 0A` | Kiểm tra sơ bộ, vẫn giả được |
| Full decoding và validation | Parser ảnh an toàn đọc được cấu trúc | Mạnh hơn, vẫn cần giới hạn tài nguyên |
| Re-encode/scan/CDR | Tạo ảnh sạch hoặc lọc active content | Defense in depth phù hợp định dạng |

### Magic bytes không có nghĩa ảnh an toàn

PNG có 8 byte signature, JPEG thường bắt đầu `FF D8 FF`. Một file 8 byte đúng header PNG nhưng không có image data có thể qua phép check signature mà không phải ảnh thực sự. Một file có header hợp lệ vẫn có thể chứa metadata, đoạn dữ liệu hỏng hoặc payload tấn công parser.

Trong demo, `detectSignature` chỉ đảm bảo phần đầu phù hợp format cho phép; **không** cam kết valid PNG/JPEG. Với production cần dùng decoder đầy đủ, giới hạn resolution/pixel/frames, chống decompression bombs, re-encode và quét malware theo mức rủi ro.

Không mở rộng allowlist sang SVG, PDF, DOCX, ZIP hoặc EXE chỉ bằng cách thêm vài magic bytes. Mỗi format có active content, macro, archive entry, parser và chính sách riêng.

### Content-Type của request khác MIME của file part

Request `Content-Type: multipart/form-data` mô tả envelope. `Content-Type: image/png` của từng part do client khai báo loại file. Endpoint `consumes=multipart/form-data` nhận sai envelope thì thường **415 Unsupported Media Type**; file part có bytes sai policy, nhưng multipart parse được, ứng dụng có thể trả **400 Bad Request**.

## 7. Tên file gốc không tin cậy

Các chuỗi không nên dùng làm file path:

```text
../../etc/passwd
..\..\windows\system.ini
report.pdf.exe
avatar.PNG
.htaccess
CON
photo.jpg:payload
name%2e%2e%2fsecret.png
Tên rất dài / Unicode gây nhầm lẫn / control chars
```

Rủi ro gồm path traversal, ghi đè tệp, thiết bị đặc biệt, Alternate Data Streams trên Windows, collision trên filesystem case-insensitive, tên Unicode tương đương, HTTP header injection khi dùng `Content-Disposition` và XSS khi hiện tên trên HTML.

**Chiến lược an toàn nhất:** Sinh key storage bởi server, hoàn toàn **không dùng getOriginalFilename để quyết định path**. Ví dụ `Files.createTempFile(privateRoot, "uploaded-", ".png")` sau khi server xác minh loại. Nếu muốn lưu original name để hiển thị, đặt trong metadata riêng, giới hạn bytes/ký tự theo policy, normalize Unicode, escape theo output context; không làm khóa phân quyền hay nguồn extension.

Chỉ gọi `Paths.get(originalFilename).getFileName()` không xóa hết rủi ro: tên còn có thể là device name, quá dài, trùng nhau, chứa Unicode trick và control characters. Không dùng tên client để save file ngay cả khi đã cắt đoạn `../`.

## 8. Storage an toàn và lifecycle

Tách bốn vùng logic:

1. **Temporary multipart space**: container tạo khi parse, có vòng đời tạm.
2. **Private staging/quarantine**: file đã nhận nhưng chưa kiểm tra hết.
3. **Accepted private storage**: file đã pass policy và scan, có metadata/owner/tenant.
4. **Authorized delivery**: API download nhận opaque ID, kiểm tra quyền, phát headers đúng.

Với filesystem, dùng thư mục **ngoài webroot** và không tạo public static mapping tới thư mục upload. Chỉ process ứng dụng được ghi vào vùng cần thiết, không execute; tránh filesystem path dưới sự kiểm soát người dùng. Với object storage, bucket/object mặc định private; cân nhắc presigned URL thời hạn ngắn chỉ sau authorization/scan.

Khi lỗi giữa nhiều file, cần xóa các file được tạo trong request. Nếu ứng dụng crash hoặc DB commit thất bại, cần cleanup job/orphan reconciliation vì filesystem và database không có transaction ACID chung. Upload chưa quét xong không nên được trả ra public.

## 9. API contract ví dụ

```text
POST /api/uploads
Content-Type: multipart/form-data
1..3 file parts có cùng name = "files"
Mỗi file không rỗng, tối đa 2 MiB
Chỉ cho phép PNG/JPEG theo signature sơ bộ
Khi MIME part được khai báo thì phải khớp signature
Tên file lưu do server sinh; không công khai raw path
```

Ví dụ JSON thành công, `id` và `size` chỉ là minh họa:

```json
{
  "files": [
    {
      "id": "uploaded-483927102931.png",
      "mediaType": "image/png",
      "size": 1024
    }
  ]
}
```

Demo trả HTTP `201 Created` khi file đã lưu riêng theo policy sơ bộ; `id` là storage key nội bộ, **không phải URL public**. Trong hệ thống cần scan async, có thể trả `202 Accepted` + job ID và trạng thái chờ trước khi cho tải.

## 10. Ứng dụng Spring Boot 3 Java 17+ chạy được

Cần project Maven Spring Initializr với Java 17+, `spring-boot-starter-web` và `spring-boot-starter-test` (test scope). Không cần database. Các tệp đặt trong package `com.example.demo`:

```text
src/main/java/com/example/demo/
  DemoApplication.java
  UploadRejectedException.java
  UploadService.java
  UploadController.java
  UploadExceptionHandler.java
src/main/resources/application.properties
src/test/java/com/example/demo/UploadControllerTest.java
```

### 10.1. DemoApplication.java

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

### 10.2. UploadRejectedException.java

```java
package com.example.demo;

import org.springframework.http.HttpStatus;

public class UploadRejectedException extends RuntimeException {
    private final HttpStatus status;

    public UploadRejectedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
```

Exception này chỉ biểu diễn **input bị từ chối theo policy**. Lỗi `IOException` khi ghi disk không được tự chuyển thành HTTP 400, vì đó có thể là sự cố server.

### 10.3. UploadService.java

```java
package com.example.demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

    private static final int MAX_FILES = 3;
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final byte[] PNG_MAGIC = {
        (byte) 0x89, 0x50, 0x4E, 0x47,
        0x0D, 0x0A, 0x1A, 0x0A
    };

    private final Path root;

    public UploadService(Environment environment) {
        String directory = environment.getProperty(
                "app.upload.directory", "./private-uploads");
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public List<UploadedFile> store(List<MultipartFile> files)
            throws IOException {

        if (files == null || files.isEmpty()) {
            throw rejected("At least one file is required");
        }
        if (files.size() > MAX_FILES) {
            throw rejected("At most three files are allowed");
        }

        // Xác thực toàn bộ danh sách trước khi tạo file lưu.
        List<Kind> types = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw rejected("Empty files are not allowed");
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new UploadRejectedException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Each file must be at most 2 MiB");
            }

            // Không dùng getOriginalFilename() làm file path.
            Kind kind = detectSignature(file);
            String declared = file.getContentType();
            if (declared != null && !declared.isBlank()
                    && !kind.mediaType().equals(
                        declared.toLowerCase(Locale.ROOT))) {
                throw rejected(
                    "Declared type does not match detected type");
            }
            types.add(kind);
        }

        Files.createDirectories(root);
        List<Path> created = new ArrayList<>();
        List<UploadedFile> results = new ArrayList<>();

        try {
            for (int i = 0; i < files.size(); i++) {
                Kind kind = types.get(i);
                Path destination = Files.createTempFile(
                        root, "uploaded-", "." + kind.extension());
                created.add(destination);

                try (InputStream in = files.get(i).getInputStream()) {
                    Files.copy(in, destination,
                            StandardCopyOption.REPLACE_EXISTING);
                }

                results.add(new UploadedFile(
                    destination.getFileName().toString(),
                    kind.mediaType(), Files.size(destination)));
            }
            return List.copyOf(results);
        } catch (IOException | RuntimeException error) {
            for (Path path : created) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            throw error;
        }
    }

    private Kind detectSignature(MultipartFile file) throws IOException {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(8);
        }

        if (Arrays.equals(header, PNG_MAGIC)) {
            return new Kind("image/png", "png");
        }
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return new Kind("image/jpeg", "jpg");
        }
        throw rejected("Only PNG and JPEG signatures are permitted");
    }

    private UploadRejectedException rejected(String message) {
        return new UploadRejectedException(
                HttpStatus.BAD_REQUEST, message);
    }

    private record Kind(String mediaType, String extension) {}

    public record UploadedFile(
            String id, String mediaType, long size) {}
}
```

Phân tích code:

- Tối đa ba `MultipartFile` và tối đa 2MiB/file; số part toàn request cần container limit riêng.
- `getSize()` defense in depth **sau** parser; parser có cấu hình max sizes.
- `detectSignature` đọc tối đa 8 bytes đầu, xác minh sơ bộ PNG/JPEG. **Không xác minh toàn ảnh.**
- MIME part do client khai báo chỉ được check thêm, không quyết định sự an toàn.
- Không đọc original filename, không lấy extension client để tạo destination.
- `Files.createTempFile` sinh tên server, chỉ trong thư mục do cấu hình server kiểm soát.
- `Files.copy` đích là file tạm mới; `REPLACE_EXISTING` ở đây chỉ ghi vào file **do chính server vừa tạo**, không ghi đè path do client đặt.
- Nếu copy thất bại, cố xóa tất cả file đã tạo trong request; cần cleanup định kỳ khi crash hoặc DB lỗi.
- Code không đặt permission riêng hoặc chống adversarial symlink trong thư mục: production phải quản lý owner, private volume và đảm bảo user khác không sửa thư mục.
- Service có thể bị lỗi storage, khi đó không nên trả “file type invalid” mà phải log/báo lỗi hệ thống thích hợp.

**Không đưa file mẫu này lên Internet như một API public mà không có authn/authz/quota/scanner/decoder đầy đủ.** Upload trả ID không có nghĩa file đã sẵn sàng cho download.

### 10.4. UploadController.java

```java
package com.example.demo;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService service;

    public UploadController(UploadService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("files") List<MultipartFile> files)
            throws IOException {

        List<UploadService.UploadedFile> saved = service.store(files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UploadResponse(saved));
    }

    public record UploadResponse(
            List<UploadService.UploadedFile> files) {}
}
```

Controller phụ trách HTTP status và JSON, không chấp nhận user gửi storage path. Nếu hoàn toàn thiếu `files` part bắt buộc, Spring MVC có thể từ chối 400 ở argument resolution **trước khi service chạy**.

### 10.5. UploadExceptionHandler.java

```java
package com.example.demo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class UploadExceptionHandler {

    @ExceptionHandler(UploadRejectedException.class)
    public ResponseEntity<ApiError> rejected(
            UploadRejectedException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(
                    "UPLOAD_REJECTED", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> oversized(
            MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError(
                    "UPLOAD_TOO_LARGE",
                    "Multipart request exceeds configured limits"));
    }

    public record ApiError(String code, String message) {}
}
```

Nếu multipart parsing exception đến được MVC resolver, advice có thể trả 413. Nếu proxy hoặc container từ chối **trước DispatcherServlet**, advice không bắt được lỗi gốc: body có thể khác hoặc kết nối bị đóng. Không bắt toàn bộ `Exception` rồi trả 400; lỗi disk full/IO thuộc hạ tầng.

### 10.6. application.properties

```properties
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=2MB
spring.servlet.multipart.max-request-size=7MB
spring.servlet.multipart.file-size-threshold=0B
app.upload.directory=./private-uploads
```

`./private-uploads` chỉ dùng demo. Production đặt trên volume riêng bên ngoài webroot, giới hạn quyền process, có scan và cleanup.

### 10.7. Chạy ứng dụng

```bash
./mvnw spring-boot:run
```

PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Lệnh yêu cầu dự án Maven Boot có wrappers và dependencies; không chạy `javac UploadService.java` độc lập khi thiếu classpath Spring.

## 11. curl: thử upload và lỗi

### 11.1. Một tệp PNG

```bash
curl -i http://localhost:8080/api/uploads \
  -F "files=@photo.png;type=image/png"
```

Nếu `photo.png` là file nhỏ có PNG signature, app trả 201 và JSON gồm `files[0].id`, `mediaType`, `size`. Không assert id ngẫu nhiên. Demo chưa giải mã toàn ảnh.

### 11.2. Hai tệp

```bash
curl -i http://localhost:8080/api/uploads \
  -F "files=@photo.png;type=image/png" \
  -F "files=@photo.jpg;type=image/jpeg"
```

Lặp cùng tên part `files` để tạo `List<MultipartFile>`. Không tự set boundary header khi dùng `curl -F`, curl tự tạo boundary đúng.

### 11.3. Loại không hợp lệ

```bash
curl -i http://localhost:8080/api/uploads \
  -F "files=@notes.txt;type=text/plain"
```

Nếu bytes không khớp PNG/JPEG signature: `400 Bad Request` với `UPLOAD_REJECTED`. Đổi extension/MIME thành png không làm bytes của file biến thành ảnh, dù kẻ tấn công có thể giả magic bytes; đó là lý do cần decoder/scanner.

### 11.4. Bốn file trong một request

```bash
curl -i http://localhost:8080/api/uploads \
  -F "files=@photo.png;type=image/png" \
  -F "files=@photo.png;type=image/png" \
  -F "files=@photo.png;type=image/png" \
  -F "files=@photo.png;type=image/png"
```

Nếu request vẫn ở dưới multipart limits, service trả **400** vì count >3. Nếu tổng bytes vượt `max-request-size`, parser/gateway có thể từ chối sớm bằng 413. Không dựa vào thứ tự xử lý để khẳng định bốn file luôn cho 400.

### 11.5. File >2MiB

Request có file lớn có thể bị multipart parser chặn trước controller, thường HTTP **413** qua framework/container mapping tương ứng. Với `MockMultipartFile` trong MockMvc, parser limit không được mô phỏng đầy đủ; service check size vẫn có thể được test riêng.

### 11.6. Sai Content-Type request

```bash
curl -i http://localhost:8080/api/uploads \
  -H "Content-Type: application/json" \
  --data '{"files":[]}'
```

Thông thường **415** vì endpoint chỉ chấp nhận `multipart/form-data`. Không nhầm envelope media type với `image/png` của từng part.

## 12. Kiểm thử MockMvc

Tạo `src/test/java/com/example/demo/UploadControllerTest.java` trong dự án có `spring-boot-starter-test`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.upload.directory=./target/test-private-uploads",
    "spring.servlet.multipart.max-file-size=2MB",
    "spring.servlet.multipart.max-request-size=7MB"
})
@AutoConfigureMockMvc
class UploadControllerTest {

    @Autowired MockMvc mvc;

    private static final byte[] PNG_HEADER = {
        (byte) 0x89, 0x50, 0x4E, 0x47,
        0x0D, 0x0A, 0x1A, 0x0A
    };

    private MockMultipartFile png(String filename) {
        return new MockMultipartFile(
            "files", filename, "image/png", PNG_HEADER);
    }

    @Test
    void serverNameDoesNotUseClientPath() throws Exception {
        mvc.perform(multipart("/api/uploads")
                .file(png("../../client-name.png")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.files[0].mediaType")
                .value("image/png"))
            .andExpect(jsonPath("$.files[0].id",
                not(containsString("client-name"))));
    }

    @Test
    void rejectsFourFiles() throws Exception {
        mvc.perform(multipart("/api/uploads")
                .file(png("a.png"))
                .file(png("b.png"))
                .file(png("c.png"))
                .file(png("d.png")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code")
                .value("UPLOAD_REJECTED"));
    }

    @Test
    void rejectsSpoofedMime() throws Exception {
        MockMultipartFile fake = new MockMultipartFile(
            "files", "photo.png", "image/png",
            "not a PNG".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/uploads").file(fake))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
            "files", "empty.png", "image/png", new byte[0]);

        mvc.perform(multipart("/api/uploads").file(empty))
            .andExpect(status().isBadRequest());
    }

    @Test
    void applicationRejectsOverTwoMiB() throws Exception {
        MockMultipartFile huge = new MockMultipartFile(
            "files", "large.png", "image/png",
            new byte[2 * 1024 * 1024 + 1]);

        mvc.perform(multipart("/api/uploads").file(huge))
            .andExpect(status().isPayloadTooLarge());
    }
}
```

Chạy:

```bash
./mvnw test
```

PowerShell:

```powershell
.\mvnw.cmd test
```

**Giới hạn test:** `PNG_HEADER` chỉ có 8 bytes, không phải một ảnh PNG hoàn chỉnh. Test này chứng minh **logic chính sách sơ bộ** (không phải file format security). Khi thêm decoder, hãy dùng fixture ảnh hợp lệ và tệp hỏng trong test. MockMvc không thay thế container multipart limits; test app-layer oversized khác với test parser/proxy 413.

File thử nghiệm được lưu dưới `target/test-private-uploads`; Maven `clean` có thể dọn thư mục target, hoặc dùng volume/temporary folder riêng trong CI. Với Spring Security, cần thêm mock identity và CSRF token phù hợp: HTTP 403 có thể xảy ra trước controller.

## 13. Phải kiểm thử HTTP thực ở đâu?

Một HTTP integration test qua embedded Tomcat/Jetty hoặc staging ingress cần bao phủ:

- Multipart boundary sai hoặc thiếu; request ngắt giữa chừng.
- `max-file-size` và `max-request-size` tại parser (không chỉ service).
- Tổng multipart parts rất lớn dù số file ít.
- Proxy body limit, timeout và client upload chậm.
- Temp disk hết chỗ; thư mục không có quyền ghi.
- Header lạ/quá dài, filename có control chars/Unicode.
- Upload concurrent vượt rate/quota.
- Scanner/decoder fail hoặc timeout.
- Behavior trả lỗi trước khi `@RestControllerAdvice` có thể chạy.

Có thể dùng `@SpringBootTest(webEnvironment = RANDOM_PORT)` và HTTP client thực, hoặc khởi động app rồi `curl` từ test harness. Khi reverse proxy tồn tại, cần test qua **đúng ingress production**; chỉ gọi loopback bỏ qua nhiều policy.

## 14. Khi phục vụ file đã upload

Upload vào private storage chưa có nghĩa đã an toàn để phát file. Khi thêm download/preview API:

1. Ánh xạ một opaque ID tới server-side metadata và path; không lấy tên do client gửi để tạo path.
2. Xác thực user và **kiểm tra quyền trên chính file/tenant** khi tải.
3. Không public directory chứa file upload qua static resource mapping.
4. Chỉ cho phép download khi file đã pass validation/scan policy; có trạng thái quarantine.
5. Đặt `Content-Type` theo loại **đã được xác minh**, không tin MIME của upload request.
6. Dùng `Content-Disposition: attachment` cho format có active content; encode filename header đúng và không chèn raw control chars.
7. Cân nhắc `X-Content-Type-Options: nosniff` và CSP/isolated origin.
8. Với ảnh muốn render inline, ưu tiên decode/re-encode trước khi phát.
9. Thiết kế cache policy phù hợp dữ liệu cá nhân, không dùng cache public tùy tiện.
10. Có cleanup/retention và báo cáo abuse.

Một filename ngẫu nhiên không thay thế authorization. ID có thể rò qua log, browser history hoặc đường dẫn chia sẻ; tài nguyên riêng tư vẫn phải kiểm tra quyền trên mỗi request.

## 15. Tình huống tấn công và lớp phòng vệ

| Tình huống | Nguy cơ | Kiểm tra |
| --- | --- | --- |
| File 20MB tới API max2MB | Hết băng thông/temp | Parser và edge size limits |
| 500 file 1KB | Quá nhiều file | App file count + container part count |
| 1 file và 10.000 field text | Quá nhiều multipart parts | Container/parameter limits |
| `avatar.png.exe` | Double extension | Ignore raw name, allowlist |
| `../../secret` | Path traversal | Server-generated key, private root |
| `image/png` nhưng bytes là text | MIME spoofing | Signature + decoder |
| PNG magic đúng nhưng body sai | Header spoofing | Full image decoding/re-encode |
| 100KB ZIP giải nén 10GB | Decompression bomb | Uncompressed bytes, entry/depth limits |
| 5KB ảnh khai báo 100000×100000 pixels | Image bomb | Pixel cap và CPU/memory budget |
| File PDF chứa JavaScript | Active content | Restrict format, sanitize/CDR/sandbox |
| Tên file cực dài/Unicode trick | Metadata/FS confusion | Ignore in path, validate metadata |
| Cùng tên 100 lần | Overwrite | Random unique generated path |
| Upload cùng lúc từ nhiều client | Resource exhaustion | Rate, quota, concurrency |
| DB insert lỗi sau khi lưu | Orphan file | Compensating cleanup |
| Upload bị ngắt | Temp leftovers | Lifecycle expiry/cleanup |
| File được trả public ngay khi nhận | Malware/XSS | Private quarantine + approval |
| Unauthorized download | Data exposure | Ownership/tenant checks |

Không có một check “file extension hợp lệ” nào thay thế defense in depth.

## 16. Kiến trúc kiểm định file production

Có thể chia thành các chốt:

**Chốt trước multipart parsing:** gateway/container giới hạn bytes, thời gian, số parts, rate và connections.

**Chốt sau parse:** service giới hạn số file/empty/size/type/metadata và quota user; không sử dụng filename client làm path.

**Chốt quarantine:** file đi vào private staging, full decode, pixel/resource cap, malware scanner, CDR/re-encode và timeout. Scanner lỗi thì không nên mặc định allow file.

**Chốt publish:** chỉ file đạt policy mới chuyển sang accepted/private storage, có owner, scan status, checksum và retention. Nếu database và filesystem cập nhật thất bại từng phần thì cần cleanup/reconcile.

**Chốt download:** xác thực, authorize, chọn headers/content disposition, rate và cache policy.

Nếu scan bất đồng bộ, response có thể dùng HTTP `202 Accepted` với job ID và trạng thái pending, không ngụ ý đã được phép download. Queue chỉ mang storage key và metadata cần, không giữ `MultipartFile`/ServletRequest qua vòng đời HTTP.

## 17. Vì sao số byte upload chưa đủ kiểm soát tài nguyên?

Một file nhỏ có thể trở nên cực lớn hoặc cực tốn CPU sau khi phân tích:

- ZIP nén chặt, nhiều cấp thư mục, nested archives.
- Ảnh có pixel dimensions bất thường hoặc metadata chunk lớn.
- PDF phức tạp, font/vector/embedded objects làm parser tốn RAM.
- Video codec/cấu trúc dữ liệu gây decode cực tốn CPU.
- HTML/SVG/script có active content, rủi ro browser nếu render inline.

Đặt budget **sau giải nén/giải mã** và giới hạn concurrency của parser/AV. Không sử dụng `MultipartFile.getBytes()` trên file lớn trong nhiều request đồng thời khi có thể streaming. Tính quota theo cả bytes lưu và chi phí xử lý.

## 18. Lỗi HTTP và ngoại lệ

| Lỗi | Status thường gặp | Nơi phát hiện |
| --- | --- | --- |
| Thiếu file part | 400 | MVC argument binding |
| File rỗng/quá số file | 400 | Service policy |
| Không được phép loại file | 400 theo demo | Service policy |
| File vượt size tại service | 413 | Service policy |
| Multipart request quá lớn | Thường 413 | Parser/exception resolver |
| Proxy từ chối body | Có thể 413/connection close | Edge |
| Sai envelope Content-Type | 415 | MVC request mapping |
| Thiếu xác thực/quyền | 401/403 | Security filters |
| Disk full/staging lỗi | 500 hoặc status server đã thiết kế | Storage/infrastructure |
| Scan async chưa xong | 202 (nếu thiết kế) | Workflow |
| Multipart syntax lỗi | Thường 400, tùy parser | Container/framework |

Một request có thể vi phạm nhiều điều kiện. Không dự đoán “file count luôn được kiểm tra trước file size”: parser/edge có thể dừng trước service. Body JSON lỗi mặc định cũng không đồng nhất giữa Tomcat/Boot/advice/proxy.

## 19. Anti-pattern thường gặp

1. Chỉ kiểm `getSize` trong controller; quá muộn với các chi phí parser trước đó.
2. Không có app file-count limit và container part-count limit.
3. Coi max-request-size = max-file-size × số file chính xác; quên multipart overhead.
4. Coi `Content-Type` client khai báo là bằng chứng thực sự.
5. Tin extension gốc `.png`, bỏ qua magic và decoder.
6. Chỉ check magic, cho scan bypass; file hỏng vẫn có header hợp lệ.
7. Dùng `getOriginalFilename` để tạo directory/path.
8. Chỉ sanitize dấu `../` rồi tin mọi filename còn lại.
9. Đặt upload trong webroot hoặc mở execute permission.
10. Dùng raw filename trong response headers mà không escape.
11. Cho tải file trước khi quét xong.
12. Không giới hạn quota, concurrency hoặc CPU time cho parser.
13. Không kiểm uncompressed bytes/pixel dimensions của tệp.
14. Log raw multipart body, token và filename nhạy cảm.
15. Coi một request không có Content-Length là an toàn.
16. Dùng `MultipartFile.getBytes()` với nhiều file lớn, gây pressure heap.
17. Không cleanup file tạm/orphan sau lỗi/crash.
18. Không authorize download vì đã sinh tên file ngẫu nhiên.
19. Bắt tất cả exception thành 400, che lỗi disk full/IO.
20. Dựa MockMvc làm bằng chứng parser/gateway size limit.
21. Dùng callback async để đọc `MultipartFile` sau request lifecycle.
22. Không có scan state/versioning policy cho định dạng file mới.

## 20. Checklist trước production

### HTTP và hệ thống

- [ ] `consumes=multipart/form-data` và endpoint có xác thực/phân quyền.
- [ ] Max file bytes, max multipart request bytes, số parts và field limits được định nghĩa ở gateway/container/app.
- [ ] Quota user/tenant, rate và concurrency giới hạn.
- [ ] Temp disk riêng, đủ quota, timeout, cleanup, permission chặt.
- [ ] Test HTTP integration cho size limits, malformed boundary, 413/415 và edge policy.

### Content và metadata

- [ ] File count, empty, per-file size và business conditions được validate.
- [ ] Allowlist format theo nhu cầu; không tin extension/MIME client.
- [ ] Magic bytes **và** full decoder/parser/scan theo risk profile.
- [ ] Giới hạn pixels, frames, uncompressed bytes, CPU/memory/time.
- [ ] Không dùng `getOriginalFilename()` làm path/storage key.
- [ ] Metadata tên gốc nếu lưu được normalize, giới hạn và escape.
- [ ] Có malware scanning/quarantine/CDR hoặc tái mã hóa khi cần.

### Storage và download

- [ ] Storage riêng, không public webroot, không execute permission.
- [ ] Server-generated object ID, collision-safe và không overwrite.
- [ ] Có owner/tenant/status/expiry, transaction cleanup và orphan sweeper.
- [ ] Download luôn authorize và đặt Content-Type/Disposition/nosniff phù hợp.
- [ ] Không public nội dung khi scan chưa hoàn thành.
- [ ] Logging và error response không lộ path, exception hay dữ liệu nhạy cảm.
- [ ] Test abuse, traversal, malformed content, multi-tenant access.

## 21. Bài tập tự kiểm tra

**1.** `max-file-size` khác `max-request-size` thế nào?

**Đáp án:** Một cái giới hạn từng file, một cái toàn multipart request gồm bytes và overhead. Không thay thế nhau.

**2.** `files.size() <= 3` có giới hạn mọi multipart part không?

**Đáp án:** Không; các text part khác vẫn có thể rất nhiều. Cần hạn mức container/parser.

**3.** MIME của `MultipartFile.getContentType()` đáng tin không?

**Đáp án:** Không. Do client gửi; cần allowlist, signature và kiểm nội dung thực.

**4.** Đầu file có 8 byte PNG magic có đủ chứng nhận an toàn không?

**Đáp án:** Không. Có thể fake/hỏng/độc; cần full decoding và resource limits.

**5.** Khi filename là `../../secret`, nên làm gì?

**Đáp án:** Không dùng tên gốc làm đường dẫn. Sinh key do server quyết định trong private storage; nếu lưu tên gốc như metadata thì kiểm soát riêng.

**6.** Khi upload bốn file nhỏ, vì sao có thể thấy 413 thay vì 400?

**Đáp án:** Parser hoặc gateway có thể từ chối request vì tổng bytes trước khi controller đếm file.

**7.** MockMultipartFile lớn có test được max-file-size của Tomcat không?

**Đáp án:** Không đầy đủ. Nó test app check; phải kiểm thử HTTP thật để xác nhận parser/container.

**8.** Disk full có nên map thành 400 không?

**Đáp án:** Không; đó là lỗi hệ thống, cần báo lỗi server và cleanup/telemetry.

**9.** Tên file ngẫu nhiên có đủ bảo vệ API download không?

**Đáp án:** Không. Vẫn phải kiểm tra user/tenant, trạng thái scan và quyền đọc.

**10.** Nếu scanner chạy nền, API nên trả gì?

**Đáp án:** Có thể trả 202 + job ID/pending state theo contract, không cho client tải file trước khi pass.

## 22. Tài liệu tham khảo

- Spring Boot 3 MultipartProperties: https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/autoconfigure/web/servlet/MultipartProperties.html
- Spring Framework 6.2 MultipartFile Javadoc: https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/web/multipart/MultipartFile.html
- Spring Framework Multipart Resolver: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/multipart.html
- Spring Framework multipart form arguments: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html
- Spring Framework MVC error responses: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- OWASP File Upload Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html
- OWASP Unrestricted File Upload: https://owasp.org/www-community/vulnerabilities/Unrestricted_File_Upload
- RFC 7578 multipart/form-data: https://www.rfc-editor.org/rfc/rfc7578.html
- Jakarta Servlet specification: https://jakarta.ee/specifications/servlet/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-resources-004">StreamingResponseBody, Resource và Content-Disposition khi tải xuống</a>

<details>
<summary>Xem chi tiết</summary>

# StreamingResponseBody, Resource và Content-Disposition khi tải xuống trong Spring MVC

## 1. Mục tiêu và phạm vi

Khi xây dựng API tải xuống tài liệu, báo cáo CSV, ảnh hoặc file lưu trong storage, controller Spring MVC phải quyết định **dữ liệu lấy từ đâu**, **ghi HTTP response body theo cách nào**, và **trình duyệt sẽ hiển thị hay tải về file**. Ba công cụ liên quan trực tiếp:

- **`Resource`**: abstraction của Spring cho một nguồn dữ liệu như file, classpath resource, mảng byte hoặc stream; có thể trả qua `ResponseEntity<Resource>`.
- **`StreamingResponseBody`**: callback để ứng dụng ghi từng phần vào `OutputStream` của response, thích hợp khi tạo hoặc chuyển dữ liệu lớn mà không dựng toàn bộ body trong heap.
- **`Content-Disposition`**: response header chỉ dẫn cách xử lý nội dung, đặc biệt `attachment` cùng tên file tải xuống hoặc `inline` khi phù hợp.

Sau bài này, bạn sẽ hiểu luồng render/download của Spring MVC, cách đặt `Content-Type`, `Content-Length`, `Content-Disposition`, sự khác nhau giữa buffered và streaming, các trường hợp HTTP Range, cơ chế bất đồng bộ của Servlet stack, và cách kiểm thử **status + headers + bytes thực tế** bằng MockMvc.

**Phiên bản mã minh họa:** Java 17+, Spring Framework 6.x, Spring Boot 3.x, **Spring MVC Servlet stack** (`spring-boot-starter-web`). Đây không phải ví dụ WebFlux: `Flux<DataBuffer>`, reactive backpressure và `ServerResponse` thuộc cơ chế khác.

## 2. Download thực chất là một HTTP response

Một response tải file bình thường có thể trông như sau:

~~~http
HTTP/1.1 200 OK
Content-Type: text/plain
Content-Disposition: attachment; filename="sample.txt"
Content-Length: 17
X-Content-Type-Options: nosniff

Hello Spring MVC
~~~

Các điểm cần hiểu:

1. **HTTP status** biểu diễn kết quả: `200 OK` khi có toàn bộ file, `206 Partial Content` cho một range hợp lệ được xử lý, `404 Not Found` khi không có resource được phép tải, `403 Forbidden` khi không có quyền, `416 Range Not Satisfiable` khi yêu cầu range không thể đáp ứng theo quy tắc HTTP.
2. **`Content-Type`** mô tả định dạng bytes. Ví dụ `application/pdf`, `image/png`, `text/csv` hoặc `application/octet-stream` khi chỉ muốn biểu diễn dữ liệu nhị phân chung. Header này **khác** với tên file.
3. **`Content-Disposition`** quyết định hướng xử lý `attachment`/ `inline`, đồng thời truyền filename nếu cần. Trình duyệt có thể áp dụng chính sách của nó; header không đảm bảo luôn hiện một hộp thoại “Save As”.
4. **`Content-Length`** là số byte của response representation **nếu biết và có thể khai báo đúng**. Ví dụ `Hello Spring MVC\n` dùng UTF-8 gồm 17 byte, với ký tự xuống dòng LF.
5. Body có thể là bytes đọc từ file, sinh từ báo cáo hoặc lấy từ object storage. Không nhất thiết là JSON; `ResponseEntity<byte[]>`, `ResponseEntity<Resource>` và `ResponseEntity<StreamingResponseBody>` đều có thể trả file.
6. `HEAD` và HTTP Range, cache, proxy, reverse proxy, content compression là những vấn đề khác cần xét theo hợp đồng API.

Đối với response file, **không** gói raw file bytes vào một JSON string rồi hy vọng browser tự hiểu là file. Nếu metadata cần đi cùng file, có thể cung cấp một API metadata riêng hoặc headers được định nghĩa rõ; tránh thay đổi tùy tiện media type của body.

## 3. `Resource`: abstraction đọc bytes từ một nguồn

Giao diện `org.springframework.core.io.Resource` kế thừa `InputStreamSource`, cung cấp các khả năng như:

| API | Vai trò | Lưu ý |
| --- | --- | --- |
| `getInputStream()` | Mở stream để đọc nội dung | Người đọc thường phải đóng stream đúng vòng đời |
| `exists()` | Kiểm tra resource hiện diện | Không thay cho kiểm tra quyền truy cập |
| `isReadable()` | Có thể đọc hay không | Có thể thay đổi giữa lúc kiểm tra và đọc |
| `contentLength()` | Xác định kích thước nếu nguồn hỗ trợ | Một số dạng stream có thể tiêu thụ nội dung để tính length |
| `getFilename()` | Lấy tên resource nếu biết | Không được mặc nhiên tin tên từ client |
| `getURI()`, `getURL()` | Vị trí resource nếu hỗ trợ | Không phải lúc nào cũng có filesystem path |
| `isFile()`, `getFile()` | Kiểm tra/truy cập nguồn file thật khi phù hợp | Resource trong JAR thường không phải file rời |

Các implementation quan trọng:

| Implementation | Nguồn dữ liệu | Dùng khi |
| --- | --- | --- |
| `ByteArrayResource` | Mảng byte đã có trong memory | Nội dung nhỏ, dữ liệu test, file sinh sẵn trong memory |
| `FileSystemResource` | File trên filesystem | Download từ vùng lưu trữ server có kiểm soát |
| `PathResource` | `java.nio.file.Path` | Khi code thao tác qua Path |
| `ClassPathResource` | Resource đóng gói trong classpath/JAR | Download fixture/file phân phối cùng ứng dụng |
| `UrlResource` | URL có thể truy cập | Chỉ với URL server kiểm soát; tránh SSRF từ URL user nhập |
| `InputStreamResource` | Stream đang mở/nguồn stream | **Cần đặc biệt thận trọng** với stream một lần và `contentLength()` |

Controller Spring MVC thường trả:

~~~java
@GetMapping("/sample")
public ResponseEntity<Resource> sample() {
    Resource resource = new ClassPathResource("downloads/sample.txt");
    return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"sample.txt\"")
            .body(resource);
}
~~~

Đây là **handler minh họa**, cần import `Resource`, `ClassPathResource`, `ResponseEntity`, `MediaType`, `HttpHeaders`, `GetMapping`. File `src/main/resources/downloads/sample.txt` phải tồn tại. Trong ứng dụng thật, kiểm tra resource tồn tại, quyền truy cập và xử lý IOException; không để trường hợp thiếu file lộ stack trace hoặc đường dẫn nội bộ.

Khi trả `Resource`, Spring MVC thường dùng **`ResourceHttpMessageConverter`** để đọc source và ghi body response. Tùy resource/type/header, Spring có thể cung cấp metadata và hỗ trợ Range requests. Đây **không** đồng nghĩa Spring tự phân quyền file hoặc bảo vệ path traversal.

### Vì sao `ByteArrayResource` không thích hợp cho file rất lớn?

~~~java
byte[] wholeFile = Files.readAllBytes(path);
Resource resource = new ByteArrayResource(wholeFile);
~~~

Đây là đoạn minh họa trong method với `Path path`. `Files.readAllBytes` đọc toàn file vào heap. Nếu 200 người cùng tải file 200 MiB, dung lượng heap có thể tăng không kiểm soát. Với file đã có trên disk, `FileSystemResource`/`PathResource` thường tránh phải dựng một bản sao toàn bộ file trong byte array. Nếu dữ liệu được **sinh dần**, `StreamingResponseBody` thường biểu đạt ý định rõ hơn.

## 4. `Content-Disposition`: attachment, inline và tên file

HTTP response header thường gặp:

~~~http
Content-Disposition: attachment; filename="report.csv"
~~~

hoặc:

~~~http
Content-Disposition: inline; filename="diagram.png"
~~~

- **`attachment`**: gợi ý user agent xử lý response như một nội dung có thể lưu/tải xuống, thường sử dụng `filename` làm tên gợi ý.
- **`inline`**: cho phép trình duyệt hiển thị nội dung nếu trình duyệt có khả năng và chính sách cho phép, chẳng hạn ảnh/PDF. Không bảo đảm hiển thị ở mọi môi trường.
- **`filename`** chỉ là **tên gợi ý**, không phải đường dẫn file hệ thống, không phải quyền truy cập và không phải giá trị bảo mật có thể tin tuyệt đối.
- **`filename*`** theo encoding phù hợp giúp truyền tên có ký tự Unicode, ví dụ tên tiếng Việt. User agent có thể có khác biệt về ưu tiên `filename`/ `filename*`.

### 4.1. Dùng `ContentDisposition` builder của Spring

Thay vì ghép một chuỗi header với dấu nháy và dấu phân tách bằng tay:

~~~java
String disposition = ContentDisposition.attachment()
        .filename("report.csv")
        .build()
        .toString();

return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .body(resource);
~~~

Đây là **đoạn minh họa trong method** có biến `Resource resource`; cần import `org.springframework.http.ContentDisposition`, `HttpHeaders`, `ResponseEntity`. Builder giúp tạo cú pháp header đúng và escape ký tự trong filename theo quy tắc implementation.

Nếu tên có Unicode, Spring Framework 6.x hỗ trợ:

~~~java
String disposition = ContentDisposition.attachment()
        .filename("báo-cáo.csv", StandardCharsets.UTF_8)
        .build()
        .toString();
~~~

Theo Javadoc, `filename(String, Charset)` encode tên theo RFC 5987; hỗ trợ UTF-8 và các charset được API cho phép. Khi tải xuống qua `Content-Disposition` của HTTP response, cách này có thể hữu ích. **Đừng áp dụng overload này cho phần `Content-Disposition` của multipart/form-data request**, vì RFC 7578 không cho dùng cơ chế `filename*` của RFC 5987 trong multipart form upload.

**Tránh ghép filename thô từ input người dùng:** filename có thể chứa dấu nháy, CR/LF, dấu phân tách đường dẫn hoặc chuỗi điều khiển. Builder xử lý escaping header, nhưng ứng dụng vẫn phải giới hạn tên hiển thị, ký tự nguy hiểm và luôn sử dụng **tên/path ở server được quản lý**. Không cho một tham số `filename` từ request quyết định file nào được đọc.

### 4.2. `Content-Type` không được lựa chọn tùy tiện theo extension

Ví dụ file PDF có MIME `application/pdf`; CSV có thể dùng `text/csv`; dữ liệu không biết rõ có thể dùng `application/octet-stream`. Đừng tin hoàn toàn `MultipartFile.getContentType()`, đuôi tên file hoặc dữ liệu metadata do client nhập từ bước upload trước.

Nếu server trả file do user upload, cần xác thực loại file và quyết định `Content-Type` phù hợp. Với nội dung không đáng render inline, chọn `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff` và storage/download origin có chính sách bảo mật thích hợp. `attachment` hoặc `nosniff` là lớp phòng vệ, không bảo đảm mọi trình duyệt ngăn mọi tấn công nếu response chứa active content nguy hiểm.

## 5. `Resource`, Range và giới hạn của `StreamingResponseBody`

Với một `ResponseEntity<Resource>` phù hợp, Spring MVC có thể xử lý một HTTP `Range` request để trả một phần file, hỗ trợ tải tiếp và media seeking. Ví dụ client xin `Range: bytes=0-4` trên representation dài 17 bytes; phản hồi thành công một vùng thường là:

```http
HTTP/1.1 206 Partial Content
Content-Range: bytes 0-4/17
Content-Length: 5

Hello
```

Đây là **byte offsets**, không phải chỉ số ký tự Unicode. `206` chỉ đúng khi server thực sự phục vụ range tương ứng; range không thể đáp ứng có thể dẫn tới `416 Range Not Satisfiable`. Điều kiện cache như `ETag`, `If-Range` và representation encoding phải được xét khi cần download resume an toàn.

Điểm khác biệt cần nhớ để chọn API download: một `Resource` có thể được Spring MVC hỗ trợ Range qua message converters thích hợp, còn `StreamingResponseBody` chỉ là callback ghi vào `OutputStream` — **không tự tạo khả năng HTTP Range/206**. `InputStreamResource` một-lần-đọc cũng không nên được đối xử như `FileSystemResource` có thể truy xuất byte ranges và tính kích thước lặp lại. Chi tiết xử lý các dạng Range, multi-range response, `ResourceRegion` và `416` thuộc bài chuyên sâu ngay sau phần download trong chương.

## 6. `StreamingResponseBody`: ghi trực tiếp vào OutputStream

Spring MVC cung cấp functional interface:

~~~java
@FunctionalInterface
public interface StreamingResponseBody {
    void writeTo(OutputStream outputStream) throws IOException;
}
~~~

Đây là **biểu diễn chữ ký rút gọn**, không phải toàn bộ source của thư viện Spring. Handler có thể trả `StreamingResponseBody` hoặc `ResponseEntity<StreamingResponseBody>`:

~~~java
@GetMapping("/download-stream")
public ResponseEntity<StreamingResponseBody> download() {
    StreamingResponseBody body = outputStream -> {
        for (int i = 1; i <= 3; i++) {
            byte[] bytes = ("row-" + i + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            outputStream.write(bytes);
        }
        outputStream.flush();
    };

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment()
                            .filename("rows.txt").build().toString())
            .contentType(MediaType.TEXT_PLAIN)
            .body(body);
}
~~~

Đây là đoạn handler minh họa trong controller, cần imports và một ứng dụng Spring MVC có async support. Nó sẽ gửi:

~~~text
row-1
row-2
row-3
~~~

Thứ tự bytes là tất định vì callback ghi tuần tự theo vòng for. Đây **không phải** luồng SSE, không mở một kết nối gửi sự kiện không kết thúc; chỉ là response file được ghi theo từng đoạn.

### 6.1. Điểm mạnh

- Không cần `ByteArrayOutputStream` gom toàn bộ file hoặc CSV lớn rồi mới gửi.
- Có thể copy từ một `InputStream` mở trong callback, hoặc tạo từng dòng báo cáo ngay khi đang ghi.
- Có thể kết hợp `ResponseEntity` để đặt status, headers, MIME type và filename **trước lúc ghi body**.
- Giảm nhu cầu giữ toàn bộ bytes trên heap; phù hợp với dữ liệu được tạo tuần tự hoặc đọc từ kho dữ liệu lớn.

### 6.2. Điều `StreamingResponseBody` không làm

- Đây là **Servlet MVC asynchronous request processing**, nhưng bản thân việc gọi `OutputStream.write(...)` thông thường vẫn có thể **blocking**. Nó không tự động biến server thành non-blocking reactive I/O hay cấp backpressure kiểu Reactor.
- Không tự implement HTTP Range, virus scanning, authentication, rate limiting, checksum, compression hoặc retry/resume.
- Không tự giải quyết việc client ngắt kết nối, storage bị lỗi, connection timeout hoặc thread pool quá tải.
- Không tạo `Content-Length` nếu bạn không biết trước kích thước. Response có thể dùng chunked transfer trong HTTP/1.1 hoặc cơ chế framing khác trong HTTP/2.
- Không đảm bảo rằng browser bắt đầu nhận bytes ngay sau mỗi lần `write`; buffer của Servlet container, proxy, HTTP stack và client có thể trì hoãn.

### 6.3. Stream file hiện hữu bằng `Files.copy`

Nếu bạn đã xác thực quyền truy cập và có một `Path` an toàn trỏ đến file hiện hữu:

~~~java
StreamingResponseBody body = outputStream -> {
    try (InputStream input = Files.newInputStream(authorizedPath)) {
        input.transferTo(outputStream);
    }
    outputStream.flush();
};
~~~

Đây là đoạn minh họa trong một method có biến `Path authorizedPath` đã được chọn từ metadata server và đã kiểm tra quyền. `InputStream.transferTo` dùng một buffer hợp lý để copy stream; không bắt buộc gom cả file vào byte array.

**Mở input bên trong callback** là quan trọng vì callback có thể chạy trên executor **sau khi controller method trả về**. Không mở một `InputStream` trong try-with-resources của method handler rồi đóng nó trước khi task streaming bắt đầu.

Không tự đóng `outputStream` do container sở hữu trong các handler thông thường; flush khi thích hợp, để framework/container quản lý vòng đời response.

## 7. Async Servlet, thread pool và thời hạn xử lý

Khi handler trả `StreamingResponseBody`, Spring MVC dùng cơ chế async request để thực hiện callback trong một task. Thread xử lý request ban đầu có thể được trả về container, trong khi callback vẫn đang ghi dữ liệu trên một thread từ **AsyncTaskExecutor**.

Đối với production, tài liệu Spring MVC khuyến nghị **cấu hình rõ executor phù hợp**. Không dựa vào executor mặc định không phù hợp với tải thực tế nếu hệ thống có nhiều request streaming kéo dài. Một ví dụ dùng Spring MVC Java config:

~~~java
@Configuration
public class DownloadAsyncConfig implements WebMvcConfigurer {

    private final AsyncTaskExecutor executor;

    public DownloadAsyncConfig(
            @Qualifier("downloadExecutor") AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    @Bean(name = "downloadExecutor")
    public static ThreadPoolTaskExecutor downloadExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(64);
        pool.setThreadNamePrefix("download-");
        return pool;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(60_000L);
    }
}
~~~

Đây là class minh họa đầy đủ về cấu trúc cấu hình; cần import Spring `@Configuration`, `@Bean`, `@Qualifier`, `AsyncTaskExecutor`, `ThreadPoolTaskExecutor`, `WebMvcConfigurer`, `AsyncSupportConfigurer`. Phần 9 sẽ cung cấp các import đầy đủ cho ứng dụng runnable.

**Lưu ý:** core/max pool 4/8, queue 64 và timeout 60 giây là thông số demo, **không phải tối ưu mặc định cho mọi ứng dụng**. Kích thước executor phải dựa trên tốc độ lưu trữ, thời gian truyền, số client đồng thời, memory, backpressure/throttling tại application và thời hạn ở reverse proxy. Timeout của Servlet container/proxy có thể khác thời hạn cấu hình MVC.

Nếu có Spring Security, Servlet filters hoặc MDC/tracing dùng `ThreadLocal`, cần kiểm tra async dispatch và **context propagation**. Không giả định mọi thread-local được sao chép tự động sang worker thread. Dùng cơ chế tích hợp của Spring Security/Micrometer và cấu hình filter dispatch types phù hợp.

### Lỗi sau khi response đã commit

Khi response body đã bắt đầu được gửi, status và headers có thể đã được **commit**. Nếu file bị mất giữa chừng hoặc output stream ném `IOException`, server **không thể đơn giản đổi** từ 200 sang 500 kèm JSON lỗi sạch. Client có thể nhận response bị cắt hoặc lỗi kết nối.

Do đó:

1. Kiểm tra quyền, existence, metadata, content type và kích thước phù hợp **trước khi trả body**.
2. Trong callback, sử dụng `try/finally` hoặc try-with-resources để đóng **nguồn input**.
3. Để IOException lan ra đúng cơ chế lỗi/log thay vì nuốt rồi tuyên bố file hoàn chỉnh.
4. Client phải xử lý nội dung chưa đầy đủ, timeout và checksum khi use case yêu cầu.
5. Với báo cáo sinh động có khả năng fail cao, cân nhắc tạo file/job hoàn chỉnh ở backend trước, sau đó cung cấp URL download khi báo cáo sẵn sàng.

## 8. So sánh `Resource`, `byte[]`, `StreamingResponseBody`

| Tiêu chí | `ResponseEntity<byte[]>` | `ResponseEntity<Resource>` | `ResponseEntity<StreamingResponseBody>` |
| --- | --- | --- | --- |
| Dạng dữ liệu | Toàn bytes đã ở bộ nhớ | Resource abstraction | Callback ghi từng đoạn |
| Bộ nhớ khi file lớn | Thường cao nếu dựng toàn bộ byte[] | Tùy Resource: file có thể đọc theo stream | Có thể giữ bộ nhớ thấp |
| Tạo báo cáo theo dòng | Cần gom bytes trước | Có thể dùng resource đã tạo | Rất phù hợp |
| Chuyển file từ disk | Có thể dùng nhưng không tối ưu | `FileSystemResource`/`PathResource` thường hợp lý | `Files.copy` khi cần logic streaming riêng |
| Content-Length | Biết nếu có byte[] | Có thể biết qua Resource | Chỉ đặt nếu tính chính xác từ trước |
| Range requests | Không mặc định tự áp dụng theo byte[] như Resource | Spring MVC hỗ trợ với resource phù hợp | Ứng dụng tự xử lý nếu cần |
| Async callback | Không | Không phải API streaming callback này | Có, trên Servlet async processing |
| Độ phức tạp | Thấp | Thấp-vừa | Cao hơn: executor, timeout, stream lifecycle |
| Dữ liệu nhạy cảm | Phải kiểm tra quyền | Phải kiểm tra quyền | Phải kiểm tra quyền |

Không có lựa chọn “luôn tốt nhất”. Với file có sẵn trong storage, trả `Resource` có thể đơn giản và đáng tin hơn. Với báo cáo CSV được sinh ngay trong lúc response đang ghi hoặc dữ liệu từ nguồn lớn được đọc tuần tự, `StreamingResponseBody` thường là lựa chọn sáng sủa.

## 9. Ví dụ chạy được: file Resource và báo cáo CSV streaming

Ứng dụng mẫu có hai endpoint:

- `GET /api/downloads/resource`: trả nội dung `Hello Spring MVC\n` dạng text file bằng `ByteArrayResource`, có `Content-Disposition: attachment`, `Content-Length` xác định.
- `GET /api/downloads/stream`: sinh CSV ba dòng dữ liệu bằng `StreamingResponseBody`, đặt `Content-Disposition`, không gom toàn bộ báo cáo vào RAM.
- `GET /api/downloads/inline`: thể hiện `Content-Disposition: inline` với một đoạn text, khác attachment.
  
Demo sử dụng bytes xác định để có thể kiểm thử kết quả chính xác mà không phải đọc file tùy ý của máy. **Đối với file lớn production, thay ByteArrayResource bằng FileSystemResource/PathResource hoặc đọc stream từ storage có kiểm soát.**

Tạo project Spring Initializr:

- Java: **17+**.
- Spring Boot: **3.x**.
- Build: Maven.
- Dependencies: **Spring Web**, **Spring Boot Starter Test**.
- Base package: `com.example.demo`.

Cấu trúc:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/DownloadAsyncConfig.java
src/main/java/com/example/demo/DownloadController.java
src/test/java/com/example/demo/DownloadControllerTest.java
~~~

### 9.1. Khởi động `DemoApplication.java`

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

### 9.2. Cấu hình executor `DownloadAsyncConfig.java`

~~~java
package com.example.demo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DownloadAsyncConfig implements WebMvcConfigurer {

    private final AsyncTaskExecutor executor;

    public DownloadAsyncConfig(
            @Qualifier("downloadExecutor") AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    @Bean(name = "downloadExecutor")
    public static ThreadPoolTaskExecutor downloadExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(4);
        pool.setMaxPoolSize(8);
        pool.setQueueCapacity(64);
        pool.setThreadNamePrefix("download-");
        return pool;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(60_000L);
    }
}
~~~

`ThreadPoolTaskExecutor` được Spring quản lý vòng đời khi đăng ký qua `@Bean`. `@Bean` là static để tránh vòng khởi tạo giữa instance `@Configuration` và dependency executor do chính class cấu hình đó tạo ra. Khi application context khởi động, Spring cung cấp instance executor cho constructor.

### 9.3. Hai đường tải file trong `DownloadController.java`

~~~java
package com.example.demo;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/downloads")
public class DownloadController {

    private static final byte[] SAMPLE =
            "Hello Spring MVC\n".getBytes(StandardCharsets.UTF_8);

    private static final String NOSNIFF = "X-Content-Type-Options";

    @GetMapping("/resource")
    public ResponseEntity<Resource> resource() {
        Resource body = new ByteArrayResource(SAMPLE);

        String disposition = ContentDisposition.attachment()
                .filename("sample.txt")
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(SAMPLE.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(NOSNIFF, "nosniff")
                .body(body);
    }

    @GetMapping("/stream")
    public ResponseEntity<StreamingResponseBody> stream() {

        StreamingResponseBody body = out -> {
            out.write("id,name\n".getBytes(StandardCharsets.UTF_8));

            for (int i = 1; i <= 3; i++) {
                String line = i + ",item-" + i + "\n";
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
        };

        String disposition = ContentDisposition.attachment()
                .filename("report.csv")
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(NOSNIFF, "nosniff")
                .body(body);
    }

    @GetMapping("/inline")
    public ResponseEntity<Resource> inline() {
        Resource body = new ByteArrayResource(
                "Preview\n".getBytes(StandardCharsets.UTF_8));

        String disposition = ContentDisposition.inline()
                .filename("preview.txt")
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(NOSNIFF, "nosniff")
                .body(body);
    }
}
~~~

**Chi tiết quan trọng:**

1. Cả ba endpoint trả bytes, không chuyển dữ liệu thành JSON. `@RestController` khiến return values được Spring MVC xử lý như HTTP response; với `ResponseEntity<Resource>`, converter phù hợp đọc resource và ghi bytes.
2. `SAMPLE` chứa **17 byte UTF-8**, gồm text `Hello Spring MVC` 16 ký tự ASCII và một LF. Vì biết size chính xác, endpoint resource đặt `Content-Length: 17`.
3. `ByteArrayResource` trong bài mẫu chỉ chứa dữ liệu nhỏ cố định. Trong production với file lớn, thay bằng nguồn stream/file phù hợp, không đọc file lớn thành `byte[]`.
4. `/stream` sinh một header CSV rồi ba dòng; callback ghi từng dòng vào output. `Content-Length` không được đặt vì demo không cần precompute kích thước; HTTP stack chọn framing phù hợp.
5. `Content-Disposition.attachment()` tạo filename gợi ý để download. `ContentDisposition.inline()` cho phép browser xem text khi phù hợp, không nhất thiết mở dialog lưu file.
6. `X-Content-Type-Options: nosniff` là lớp phòng vệ cho MIME, không phải thay thế validation/authorization.
7. Pool cấu hình riêng cho StreamingResponseBody cần được giám sát nếu số lượng kết nối kéo dài tăng. Trong ứng dụng thật, xử lý client disconnect và timeout một cách có chủ đích.

## 10. Thử bằng curl

Khởi chạy từ thư mục có `pom.xml`:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows dùng `mvnw.cmd spring-boot:run`; nếu không có Maven Wrapper, dùng `mvn spring-boot:run` khi đã cài Maven.

### 10.1. Tải `Resource`

~~~bash
curl -i http://localhost:8080/api/downloads/resource
~~~

Status **200 OK**, `Content-Type: text/plain`, `Content-Disposition` với `attachment; filename="sample.txt"`, `Content-Length: 17`, body:

~~~text
Hello Spring MVC
~~~

Đoạn Markdown hiển thị một dòng text; **bytes thực tế có LF cuối dòng**. Với file binary, không dùng `curl -i` để nhìn body trực tiếp trong terminal, vì các control bytes có thể làm terminal hiển thị sai; dùng:

~~~bash
curl -fL -D headers.txt   -o sample.txt   http://localhost:8080/api/downloads/resource
~~~

`-D headers.txt` lưu headers, `-o sample.txt` lưu bytes, `-f` báo lỗi HTTP 4xx/5xx. Nếu không chỉ định `-o`, `curl` ghi body ra stdout.

### 10.2. Tải báo cáo CSV streaming

~~~bash
curl -i http://localhost:8080/api/downloads/stream
~~~

Kết quả body kỳ vọng:

~~~csv
id,name
1,item-1
2,item-2
3,item-3
~~~

Header `Content-Disposition` gợi ý tên `report.csv`. Client chỉ có thể khẳng định file hoàn chỉnh khi đã đọc hết body đúng cách và không gặp lỗi. Không tự mặc định có `Content-Length` trên streaming response.

### 10.3. Thử `inline`

~~~bash
curl -i http://localhost:8080/api/downloads/inline
~~~

Trình duyệt có thể hiển thị `Preview` thay vì ép tải xuống nếu hỗ trợ text/plain và không có chính sách khác cản trở. `curl` chỉ hiển thị bytes, **không bắt chước chính xác quyết định hiển thị của browser**.

### 10.4. Thử byte Range với Resource

~~~bash
curl -i   -H "Range: bytes=0-4"   http://localhost:8080/api/downloads/resource
~~~

Với Spring MVC xử lý range cho `ByteArrayResource`, response thường là **206 Partial Content**, `Content-Range: bytes 0-4/17` và body đúng `Hello`. Nếu endpoint hoặc filter/proxy thay đổi body/headers, cần test integration thực tế để xác nhận. `StreamingResponseBody` không tự tạo cơ chế range tương tự.

Các lệnh shell ở trên dùng `\` để nối dòng trên Linux/macOS/Git Bash. Trên Windows PowerShell, dùng `curl.exe` với tham số trên một dòng hoặc ký tự nối dòng thích hợp.

## 11. MockMvc: kiểm tra headers, bytes, asyncDispatch và Range

Tạo file **`src/test/java/com/example/demo/DownloadControllerTest.java`**:

~~~java
package com.example.demo;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DownloadControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void resourceReturnsAttachmentAndExpectedBytes() throws Exception {
        mockMvc.perform(get("/api/downloads/resource"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sample.txt\""))
                .andExpect(header().longValue(
                        HttpHeaders.CONTENT_LENGTH, 17L))
                .andExpect(content().bytes(
                        "Hello Spring MVC\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void streamReturnsCsvAsynchronously() throws Exception {
        MvcResult pending = mockMvc.perform(get("/api/downloads/stream"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"report.csv\""))
                .andExpect(content().string(
                        "id,name\n"
                        + "1,item-1\n"
                        + "2,item-2\n"
                        + "3,item-3\n"));
    }

    @Test
    void inlineResponseUsesInlineDisposition() throws Exception {
        mockMvc.perform(get("/api/downloads/inline"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"preview.txt\""))
                .andExpect(content().string("Preview\n"));
    }

    @Test
    void resourceHandlesSingleByteRange() throws Exception {
        mockMvc.perform(get("/api/downloads/resource")
                        .header(HttpHeaders.RANGE, "bytes=0-4"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_RANGE, "bytes 0-4/17"))
                .andExpect(content().bytes(
                        "Hello".getBytes(StandardCharsets.UTF_8)));
    }
}
~~~

Chạy:

~~~bash
./mvnw test
~~~

**Điểm then chốt của test:**

- Kiểm tra **status** và **đúng bytes**, không chỉ xem Content-Disposition.
- Kiểm tra `attachment` và `inline` là hai disposition khác nhau.
- Với `StreamingResponseBody`, controller bắt đầu **async processing**. Trong MockMvc phải chờ và thực hiện `asyncDispatch(pending)` rồi mới assert response hoàn chỉnh. Nếu test chỉ kiểm tra kết quả ngay sau `perform(...)` có thể thấy body chưa hoàn tất.
- Test Range xác minh resource có khả năng đáp ứng `206 Partial Content` cho đoạn bytes hợp lệ trong cấu hình mẫu.
- `@SpringBootTest` + `@AutoConfigureMockMvc` chạy MVC context với executor config thực tế của dự án. Nếu có Spring Security, các test GET có thể cần authentication theo rule đã cấu hình.
- MockMvc không chứng minh streaming qua mạng sẽ có throughput tốt hoặc hoạt động y hệt reverse proxy/Servlet connector; cần HTTP integration/performance tests khi tải file lớn thực.

## 12. Download file thật từ filesystem: xử lý path và quyền

Ứng dụng thật thường có file trong storage và nhận request kiểu `GET /api/documents/{id}/download`. **Không nhận trực tiếp absolute path từ client.** Hãy tra metadata bằng ID do server quản lý, kiểm tra người dùng hiện tại có quyền với document, rồi lấy storage key đã xác nhận.

Ví dụ **minh họa tách biệt với project ở phần 9**:

~~~java
@GetMapping("/documents/{id}/download")
public ResponseEntity<Resource> download(@PathVariable UUID id)
        throws IOException {

    DocumentRecord record = documentService.findAuthorized(id);
    Path file = storageService.resolveApprovedPath(record.storageKey());

    if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
        return ResponseEntity.notFound().build();
    }

    Resource resource = new FileSystemResource(file);
    String header = ContentDisposition.attachment()
            .filename(record.safeDownloadName(), StandardCharsets.UTF_8)
            .build().toString();

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(Files.size(file))
            .header(HttpHeaders.CONTENT_DISPOSITION, header)
            .header("X-Content-Type-Options", "nosniff")
            .body(resource);
}
~~~

**Đây chỉ là controller method minh họa:** `DocumentRecord`, `documentService`, `storageService` cần được định nghĩa; không phải file Java chạy độc lập. Các kiểu cần import gồm `UUID`, `Path`, `Files`, `IOException`, `Resource`, `FileSystemResource`, `ContentDisposition`, `StandardCharsets`, `ResponseEntity`, `MediaType`, `HttpHeaders`, `GetMapping`, `PathVariable`.

Lưu ý bảo mật:

1. **Tra quyền trước khi stream.** Không trả khác biệt nhạy cảm giữa 403 và 404 nếu chính sách yêu cầu không tiết lộ sự tồn tại của resource.
2. `storageKey` do server quản lý và dùng allowlist/canonical path. `root.resolve(input).normalize().startsWith(root)` chỉ kiểm tra **lexical path**, không đủ khi có symlink hoặc race. Với filesystem có attacker-controlled symlinks, cần storage isolation và cơ chế open file an toàn (canonical real path, `SecureDirectoryStream` khi thích hợp, quyền hệ điều hành).
3. **Không dùng `MultipartFile.getOriginalFilename()` trực tiếp làm file path** và không tự ghép `Content-Disposition` từ raw filename.
4. `Files.isRegularFile` rồi `Files.size` và mở file có thể gặp **TOCTOU race**: file có thể đổi giữa các bước. Trong storage có nhiều concurrent writes, cần cơ chế versioning/locking/object immutability nếu phải bảo đảm size/ETag khớp.
5. Nếu metadata xác nhận MIME đáng tin, có thể trả media type đúng; nếu không, `application/octet-stream` cùng `attachment` và `nosniff` thường thận trọng.
6. Log một `documentId`/request ID thay vì log full storage path, token hoặc nội dung file.
7. Với tải xuống rất lớn từ S3/GCS/object storage, cân nhắc **presigned URL ngắn hạn** khi mô hình bảo mật cho phép, thay vì proxy tất cả bytes qua Spring server; validate quyền trước khi cấp URL.

## 13. Không nên dùng `InputStreamResource` như một `FileSystemResource` thông thường

`InputStreamResource` biểu diễn nguồn stream đang mở và thường có tính **đọc một lần**. Một anti-pattern:

~~~java
InputStream input = Files.newInputStream(file);
Resource resource = new InputStreamResource(input);

// Tránh tự gọi resource.contentLength() trong hoàn cảnh này:
long length = resource.contentLength();
~~~

Đây là đoạn minh họa. Vì việc tính `contentLength()` trên một stream một lần có thể tiêu thụ stream, body có thể không còn đủ bytes để gửi. Spring Framework có xử lý đặc biệt ở một số nơi cho `InputStreamResource` chính xác (exact class), nhưng các subclass hoặc trường hợp gọi độ dài thủ công vẫn dễ mắc lỗi.

Tài liệu `ResponseEntity` khuyến nghị nếu phải dùng `InputStreamResource`, hãy cân nhắc **on-demand `InputStreamSource`**, và với custom subclass phải kiểm soát `contentLength()` để không tiêu thụ stream. Trong nhiều trường hợp:
- File trên disk → `FileSystemResource`/`PathResource`.
- File nhỏ trong memory → `ByteArrayResource`.
- Stream sinh/đọc tuần tự có logic riêng → `StreamingResponseBody` với nguồn input mở trong callback.

Đừng giả định Resource nào cũng hỗ trợ reopen, repeatable stream hoặc range giống nhau. Đặc tính của source quyết định.

## 14. `Content-Length`, Transfer-Encoding, HTTP/2 và buffering

### Biết chính xác kích thước

Với byte array, `contentLength(bytes.length)` là chính xác. Với file ổn định có `Files.size(file)`, size có thể phù hợp nhưng phải xét file có bị sửa trong lúc download hay không.

### Không biết trước kích thước

Với CSV sinh trên đường truyền, không cần dựng toàn file chỉ để biết `Content-Length`. Bỏ header này và để container/proxy dùng cơ chế HTTP phù hợp. Trong HTTP/1.1, response có thể dùng `Transfer-Encoding: chunked`; trong HTTP/2 không có `Transfer-Encoding: chunked` theo cách HTTP/1.1.

**Không tự đặt `Transfer-Encoding`** từ controller như một yêu cầu ứng dụng: HTTP server kiểm soát framing. Đừng tự gán một Content-Length ước lượng; client có thể nhận file hỏng nếu bytes không khớp header.

### Buffering nhiều lớp

`OutputStream.flush()` có thể đẩy buffer phía server, nhưng không đảm bảo dữ liệu vượt qua reverse proxy/CDN/browser ngay lập tức. Với streaming thật cần đo tại client và cấu hình proxy buffering, timeout phù hợp. Nhiều request streaming dài sẽ chiếm thread của executor và network resources, nên cần concurrency controls, rate limiting và telemetry.

### Compression và content encoding

Khi response được nén (`Content-Encoding: gzip`), `Content-Length`, Range và ETag phải xét **encoded representation bytes**. Tránh khai báo Content-Length của bytes chưa nén nếu HTTP middleware sẽ thay đổi payload trên wire. Test end-to-end khi bật gzip ở proxy/container.

## 15. HTTP caching, ETag và conditional requests

Download API có thể hỗ trợ cache để giảm băng thông nếu tài nguyên không nhạy cảm hoặc đã được phân quyền/cache an toàn:

- `ETag` nhận diện version của representation.
- `Last-Modified` cho biết thời gian sửa đổi.
- `Cache-Control` quy định khả năng cache và thời hạn.
- `If-None-Match` hoặc `If-Modified-Since` có thể dẫn đến `304 Not Modified` khi đủ điều kiện.
- `If-Range` kết hợp Range để bảo đảm client không ghép bytes của hai phiên bản file khác nhau.

Ví dụ metadata cho file công khai có version:

~~~http
HTTP/1.1 200 OK
ETag: "report-v3"
Cache-Control: public, max-age=3600
Content-Disposition: attachment; filename="report-v3.csv"
~~~

Đây chỉ là **ví dụ header**: trên production, `ETag` phải thực sự gắn với nội dung/version. Với file nhạy cảm, thường cần `Cache-Control: private` hoặc `no-store` theo threat model, không để shared cache/CDN lưu dữ liệu cá nhân vô tình.

Không làm ETag bằng cách đọc toàn bộ file lớn sau mỗi request chỉ để tính hash; có thể lưu content hash/version trong metadata khi upload/publish. Đừng tự trả 304 nếu request chưa được kiểm tra đúng preconditions của HTTP.

## 16. Chọn status và xử lý lỗi download

| Tình huống | HTTP status thường phù hợp | Ghi chú |
| --- | --- | --- |
| Resource tồn tại, được phép đọc toàn bộ | 200 | Body là bytes file |
| Một byte Range hợp lệ được đáp ứng | 206 | Có `Content-Range` tương ứng |
| Client xin range vượt giới hạn | 416 | Xem quy tắc response range và representation |
| ID không tồn tại hoặc không công khai sự tồn tại | 404 | Không lộ filesystem path |
| Có resource nhưng user không có quyền | 403 hoặc 404 theo chính sách | Phân quyền trước khi gửi bytes |
| Backend storage lỗi trước khi response commit | 500/503 tùy bản chất | Error response có thể nhất quán nếu chưa commit |
| Stream lỗi khi body đã gửi một phần | Có thể kết nối bị ngắt/body thiếu | Không còn đổi status tùy ý được |
| Backend tạo báo cáo chưa xong | 202 hoặc endpoint trạng thái job | Không cố stream response chưa xác định khi nghiệp vụ cần job |

Bảo đảm ứng dụng xử lý exception một cách nhất quán; tuy nhiên khi đã gửi bytes một phần, một `@RestControllerAdvice` không thể bảo đảm chuyển kết quả thành JSON body hợp lệ. Client download quan trọng cần kiểm tra tổng byte length, checksum/version hoặc thông tin integrity riêng.

## 17. Các lỗi thường gặp

### 17.1. Download “rỗng” dù controller trả 200

Có thể do `InputStream` bị đóng trước khi callback chạy, đã bị `contentLength()` tiêu thụ, hoặc file tạm upload đã bị cleanup trước khi download. Kiểm tra lifecycle của nguồn và nơi mở stream, dùng `FileSystemResource`/on-demand stream đúng cách.

### 17.2. Browser hiển thị PDF/HTML thay vì tải về

Có thể `Content-Disposition` thiếu `attachment`, `Content-Type` không phù hợp, hoặc browser có chính sách riêng. Dùng builder `ContentDisposition.attachment().filename(...)` khi sản phẩm thực sự yêu cầu download.

### 17.3. Tên file tải về chứa ký tự lỗi

Không ghép filename Unicode thô vào header theo cách không encode. Dùng `ContentDisposition.attachment().filename(name, UTF_8)` theo API Spring 6.x và kiểm thử với browsers mục tiêu. Chọn tên hiển thị đã kiểm tra, không coi filename là đường dẫn.

### 17.4. `StreamingResponseBody` chạy ổn trong dev nhưng treo khi nhiều client

Callback ghi output bằng blocking I/O trên async executor. Executor ít thread/queue quá lớn, storage chậm, client tải chậm hoặc proxy buffering có thể gây nghẽn. Cấu hình executor có giới hạn, timeouts, metrics và backpressure ở tầng ứng dụng.

### 17.5. MockMvc test stream có body rỗng

Nếu test không thực hiện `asyncDispatch`, có thể nó đang kiểm tra request **trước khi callback hoàn tất**. Dùng `request().asyncStarted()`, `MvcResult` và `asyncDispatch` rồi mới assert bytes.

### 17.6. Cứ `Resource` là tự hỗ trợ resumable download

Sai. Tùy Resource implementation, cấu hình converter, conditional request, reverse proxy và source có đủ metadata/repeatability. Đặc biệt `InputStreamResource` không thay thế hoàn toàn `FileSystemResource` cho Range.

### 17.7. Tin filename client để đọc file server

Sai. Không dùng `root.resolve(request.getParameter("path"))` hoặc `getOriginalFilename()` từ upload làm đường dẫn download. Dùng ID do server ánh xạ tới storage key và kiểm tra ACL/canonical path/symlink.

### 17.8. Cố đổi response thành 500 JSON sau khi stream đã commit

Không khả thi một cách đáng tin. Thực hiện preflight trước khi stream, báo lỗi vận hành qua log/metrics, và thiết kế client phát hiện download không đầy đủ.

### 17.9. Dùng `ByteArrayResource` cho mọi file lớn

Nếu bytes được dựng bằng `Files.readAllBytes`, bộ nhớ tăng theo kích thước file và số request đồng thời. Chọn file-backed Resource hoặc streaming phù hợp.

### 17.10. Đặt `Content-Length` và `Content-Encoding` không nhất quán

Nếu gzip middleware thay đổi bytes, Content-Length phải phản ánh representation gửi thực tế; nếu không chắc, không tự đặt size. Test sau reverse proxy thay vì chỉ kiểm tra MockMvc.

## 18. Câu hỏi ôn tập

**1. `Resource` và `StreamingResponseBody` khác nhau ở đâu?**

`Resource` mô tả nguồn dữ liệu để Spring `HttpMessageConverter` ghi ra response; `StreamingResponseBody` cho ứng dụng trực tiếp quyết định cách ghi từng đoạn vào `OutputStream` dưới cơ chế async MVC.

**2. `Content-Disposition: attachment` có bảo đảm browser chắc chắn mở hộp thoại Save As?**

Không. Nó là hướng dẫn disposition; browser và cấu hình người dùng có thể tự tải vào thư mục hoặc dùng cơ chế khác.

**3. Vì sao cần đặt filename qua `ContentDisposition` builder?**

Builder giúp tạo header đúng cú pháp, quote/escape filename; overload nhận UTF-8 hỗ trợ encode tên Unicode. Tên file vẫn cần được kiểm soát và không phải dữ liệu dùng để chọn storage path.

**4. `StreamingResponseBody` có phải non-blocking WebFlux?**

Không. Nó sử dụng Servlet async processing và thực hiện callback trên executor, còn ghi OutputStream thường vẫn blocking.

**5. Vì sao `InputStreamResource` có thể gây lỗi khi tính độ dài?**

Vì nguồn stream thường đọc một lần; một số thao tác tính `contentLength()` có thể đọc hết stream. Không tự gọi trên nguồn không repeatable.

**6. Khi nào client nhận 206?**

Khi server xử lý Range request hợp lệ, chỉ trả một phần bytes và báo `Content-Range` phù hợp. Streaming theo từng đoạn không tự dẫn đến 206.

**7. Có nên đặt `Content-Length` trên báo cáo CSV được sinh dần?**

Chỉ khi biết chính xác số bytes sẽ gửi và không có tầng biến đổi representation làm thay đổi nó. Nếu không biết trước, để server chọn HTTP framing.

**8. Nếu lỗi xảy ra sau khi đã gửi 100 KiB đầu của file thì trả JSON 500 được không?**

Không đáng tin. Response có thể đã commit. Cần xử lý log, failure/cancellation và phía client cần phát hiện incomplete download.

## 19. Checklist production

- Chọn `ResponseEntity<Resource>` khi nguồn là file/resource thích hợp; chọn `StreamingResponseBody` khi cần tạo/chuyển dữ liệu tuần tự mà không buffer toàn bộ.
- Đặt `Content-Type` phù hợp với nội dung đã xác minh; dùng `ContentDisposition` builder để gửi filename và disposition.
- Chỉ khai báo `Content-Length` khi biết chính xác; kiểm thử sau content encoding/proxy nếu có.
- Kiểm tra quyền truy cập, metadata, file existence và storage path **trước khi body bắt đầu**.
- Với storage file lớn, tránh `Files.readAllBytes`/memory buffering và `InputStreamResource` không repeatable khi cần Range.
- Nếu cần resume/range, chọn Resource phù hợp, kiểm tra `206`, `Content-Range`, `ETag`/`If-Range` và phản hồi cho range không hợp lệ.
- Cấu hình async executor, timeout, quotas, metrics và reverse-proxy buffering cho StreamingResponseBody.
- Không trả active content nguy hiểm inline trừ khi sản phẩm yêu cầu và đã có bảo vệ phù hợp; dùng `attachment`, `nosniff`, authorization và origin isolation khi cần.
- Test **status + filename/header + MIME + bytes** và dùng `asyncDispatch` cho streaming trong MockMvc.
- Với file rất lớn hoặc lượng người tải cao, cân nhắc object storage/CDN/presigned URL, immutable versions và kiểm tra toàn vẹn dữ liệu.
- Không để tên file do client điều khiển quyết định đường dẫn đọc file hay viết header thô.
- Thiết kế xử lý lỗi streaming không thể rollback sau response commit.

## Tài liệu chính thức

- [Spring Framework 6.2 — Asynchronous Requests và StreamingResponseBody](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-async.html)
- [Spring Framework 6.2 — HTTP Range Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-range.html)
- [Spring Framework 6.2 — ResponseEntity](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/responseentity.html)
- [Spring Framework 6.2 — StreamingResponseBody Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/StreamingResponseBody.html)
- [Spring Framework 6.2 — ContentDisposition.Builder Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/http/ContentDisposition.Builder.html)
- [Spring Framework — Core Resource interface](https://docs.spring.io/spring-framework/reference/core/resources.html)
- [RFC 9110 — HTTP Semantics (Content-Disposition, Range, caching)](https://www.rfc-editor.org/rfc/rfc9110.html)
- [RFC 6266 — Content-Disposition in HTTP](https://www.rfc-editor.org/rfc/rfc6266.html)
- [OWASP — File Upload Cheat Sheet (liên quan file lưu trữ và download)](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-resources-005">Range request, ResourceRegion và phản hồi 206 Partial Content</a>

<details>
<summary>Xem chi tiết</summary>

# Range request, ResourceRegion và phản hồi 206 Partial Content trong Spring MVC

## 1. Vì sao HTTP Range quan trọng?

Khi client tải tệp video, PDF hoặc một tài liệu lớn, nó có thể cần **một đoạn bytes** thay vì tải lại toàn bộ. Video player muốn tua đến vị trí mới, download manager muốn tiếp tục tệp tải dở, PDF viewer có thể cần vùng chứa metadata/trang cần mở.

HTTP hỗ trợ cơ chế **Range Requests**, định nghĩa trong RFC 9110. Client gửi header `Range`, server có thể đáp bằng `206 Partial Content` với đúng vùng bytes. Trong Spring Web MVC, `Resource`, `HttpRange`, `ResourceRegion` và `ResourceRegionHttpMessageConverter` hỗ trợ phần lớn công việc này.

Bài học dành cho **Spring MVC trên Servlet stack** với **Java 17, Spring Boot 3.3.5 và Spring Framework 6.1.x**. Nội dung gồm cú pháp và ngữ nghĩa HTTP Range, Spring auto-range, sử dụng ResourceRegion, 200/206/416, multi-range, ETag/If-Range, ứng dụng Maven chạy được, MockMvc tests và các giới hạn khi triển khai thực tế. WebFlux có reactive writers khác, không áp dụng nguyên mã controller/converter MVC.

## 2. Hiểu byte offsets đúng

Giả sử tệp có đúng 10 bytes ASCII:

```text
Nội dung: A B C D E F G H I J
Vị trí:   0 1 2 3 4 5 6 7 8 9
Độ dài:  10 bytes
```

Client gửi:

```http
GET /api/files/sample HTTP/1.1
Host: localhost:8080
Range: bytes=2-5
```

Một phản hồi hợp lệ khi server chấp nhận Range:

```http
HTTP/1.1 206 Partial Content
Content-Type: text/plain
Accept-Ranges: bytes
Content-Range: bytes 2-5/10
Content-Length: 4

CDEF
```

Giải thích:

- `bytes` là đơn vị range của HTTP.
- Byte **đầu 2, cuối 5 đều bao gồm**: có `5 - 2 + 1 = 4` bytes.
- `/10` trong `Content-Range` là kích thước của **toàn bộ representation**, không phải độ dài đoạn.
- `Content-Length: 4` là độ dài **phần response thực tế**. Với transfer encoding/chunking, header này không nhất thiết luôn có.
- HTTP status thật là `206`, không phải `200` với JSON `{"code":206}`.
- Body chỉ chứa `CDEF`.

**Range dựa trên bytes, không dựa trên ký tự.** Với UTF-8, một ký tự tiếng Việt có thể dài nhiều bytes, nên Range có thể cắt giữa một ký tự; client tải theo phần cần ghép bytes rồi mới giải mã. Với content coding `gzip` hoặc `br`, offsets áp dụng cho **selected representation bytes** (đã content-encoded nếu được gửi ở dạng đó); không tự đồng nhất với offset của file gốc chưa nén.

## 3. Bốn dạng Range phổ biến

```http
Range: bytes=<first>-<last>
Range: bytes=<first>-
Range: bytes=-<suffix-length>
Range: bytes=<first1>-<last1>,<first2>-<last2>
```

Với `ABCDEFGHIJ` dài 10 bytes:

| Header | Cách hiểu | Phần có thể trả |
| --- | --- | --- |
| `bytes=0-3` | Từ byte 0 đến byte 3 | `ABCD` |
| `bytes=2-5` | Từ byte 2 đến byte 5 | `CDEF` |
| `bytes=7-` | Từ byte 7 tới hết | `HIJ` |
| `bytes=-3` | Ba bytes cuối | `HIJ` |
| `bytes=9-99` | Từ 9, end vượt total | `J` (được chặn tại 9) |
| `bytes=0-1,8-9` | Hai vùng không liên tiếp | `AB` và `IJ` |
| `bytes=10-20` | Bắt đầu ở ngoài file | Không thỏa, thường `416` |
| `bytes=5-4` | End trước start | Cú pháp/phạm vi không hợp lệ |

**`bytes=7-` khác `bytes=-7`**: mẫu đầu từ offset 7 tới cuối; mẫu sau lấy 7 bytes cuối. Nếu suffix length lớn hơn file, vùng hợp lệ có thể là toàn bộ representation.

Client có thể gửi header Range nhưng **server được phép bỏ qua** và trả `200 OK` với toàn bộ representation trong các tình huống hợp lệ. Đừng giả định cứ có `Range` là chắc chắn nhận 206. Các phạm vi sai, overflow, hoặc chứa hàng trăm ranges cần được xử lý bằng parser/giới hạn phù hợp, không tự `split("-")` rồi ép `int`.

## 4. 200 OK, 206 Partial Content, 416 Range Not Satisfiable

### 4.1. 200: trả toàn bộ

```http
HTTP/1.1 200 OK
Content-Type: text/plain
Content-Length: 10
Accept-Ranges: bytes

ABCDEFGHIJ
```

Thường là response khi không có `Range`. `Accept-Ranges: bytes` là tín hiệu server hỗ trợ byte ranges; header này **không nhất thiết** có ở mọi server hỗ trợ. Một server không hỗ trợ ranges có thể quảng bá `Accept-Ranges: none`. Vắng header không luôn có nghĩa range không được hỗ trợ.

Không đặt `Content-Range` trên 200 thông thường để mô tả toàn bộ file; RFC dùng Content-Range theo ngữ nghĩa của 206/416.

### 4.2. 206: trả một phần

```http
HTTP/1.1 206 Partial Content
Content-Type: text/plain
Content-Range: bytes 7-9/10
Content-Length: 3

HIJ
```

Một **single-part 206** phải có `Content-Range` mô tả đúng phần bytes được gửi. Với `bytes=7-9` có 3 bytes. Header `Content-Length`, nếu được phát, ghi kích thước response body, không ghi total trong `/10`.

### 4.3. 416: không có vùng byte nào thỏa

```http
HTTP/1.1 416 Range Not Satisfiable
Content-Range: bytes */10
```

`bytes */10` thể hiện toàn bộ representation hiện tại dài 10 bytes. Đây là dạng unsatisfied-range được RFC khuyến nghị cho lỗi phạm vi không thỏa. Không trả `Content-Range: bytes 20-30/10` vì nó không mô tả vùng tồn tại.

Một Range header **cú pháp sai** và một Range header **đúng cú pháp nhưng ngoài độ dài** không nhất thiết có mọi hành vi giống nhau; status/body cụ thể có thể chịu ảnh hưởng parser, reverse proxy và exception handling.

## 5. Nhiều ranges và multipart/byteranges

Với:

```http
Range: bytes=0-1,8-9
```

server có thể phản hồi bằng hai parts:

```http
HTTP/1.1 206 Partial Content
Content-Type: multipart/byteranges; boundary=demo-boundary

--demo-boundary
Content-Type: text/plain
Content-Range: bytes 0-1/10

AB
--demo-boundary
Content-Type: text/plain
Content-Range: bytes 8-9/10

IJ
--demo-boundary--
```

Ví dụ chỉ minh họa cấu trúc; HTTP multipart thực tế dùng CRLF và boundary thực do server tự tạo. Những quy tắc then chốt:

- Với nhiều parts, outer `Content-Type` là `multipart/byteranges`, **không** phải `multipart/form-data` (loại này dùng với upload).
- `Content-Range` phải nằm trong **từng part**, không ở top-level của response multipart.
- `Content-Length` nếu có là kích thước **toàn multipart body** gồm boundaries, part headers và bytes; không chỉ `2+2=4`.
- Server có thể gộp ranges trùng/chồng/lân cận hoặc giới hạn số ranges; client cần parse Content-Range thực tế thay vì giả định đúng cấu trúc yêu cầu.
- `ResourceRegionHttpMessageConverter` của Spring hỗ trợ viết một `ResourceRegion` hoặc collection `ResourceRegion` tương ứng.

## 6. Spring MVC xử lý Range tự động

Với Spring MVC 6.1, một controller có thể trả `Resource` hoặc `ResponseEntity<Resource>`. Framework có thể xử lý Range mà controller **không cần đọc header `Range` hoặc tự cắt bytes**.

```text
GET /files/42
  Range: bytes=2-5
      |
      v
DispatcherServlet -> @GetMapping
      |
      v
Controller trả ResponseEntity<Resource> status 200
      |
      v
Spring đọc Range qua HttpRange
      |
      v
HttpRange -> List<ResourceRegion>
      |
      v
ResourceRegionHttpMessageConverter ghi 206 + 4 bytes
      |
      v
HTTP 206, Content-Range: bytes 2-5/10
```

**Hai điều kiện quan trọng:**

1. Resource không phải `InputStreamResource`.
2. Với `ResponseEntity<Resource>`, **status ban đầu phải là 200 OK**.

Vì vậy, không làm:

```java
// PHẢN VÍ DỤ: tự đặt 206 không khiến MVC auto-range.
return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .body(resource);
```

Nếu đưa status 206 sẵn nhưng body vẫn là toàn bộ `Resource`, nhánh tự xử lý ranges có thể bị bỏ qua và response sai ngữ nghĩa. Hãy để Spring bắt đầu từ 200 hoặc tự triển khai vùng bytes/headers đúng nếu có use case đặc biệt.

### Resource nào phù hợp?

| Resource | Có phù hợp auto-range? | Lưu ý |
| --- | --- | --- |
| `FileSystemResource` | Có, thường phù hợp | File thực, có length và mở stream lại |
| `PathResource` | Có, thường phù hợp | Cần storage path an toàn và quyền truy cập |
| `ByteArrayResource` | Có, với dữ liệu nhỏ | Đã giữ toàn bộ bytes trong RAM |
| `InputStreamResource` | **Không dùng cho auto-range** | Stream thường đọc một lần, length/repeatability khó đảm bảo |
| Custom `Resource` | Tùy implementation | Cần length chính xác, stream có thể mở/seek phù hợp |

Trong Spring MVC, auto-range được thực hiện ở return-value/converter layer; `HttpRange` và `ResourceRegion` là abstraction cho từng vùng, `ResourceRegionHttpMessageConverter` ghi đúng bytes. Với static assets phục vụ qua Spring resource handler, Spring cũng có hỗ trợ Range; đừng tạo controller không cần thiết cho file static public.

### Nếu status trước Range không phải 200?

Một conditional GET trả `304 Not Modified` không phải `206`. Một response lỗi `404`, `403` hoặc `500` cũng không chuyển thành partial file chỉ vì client gửi Range. Theo RFC, Range được xem xét sau các preconditions, và chỉ khi response không xét Range sẽ là 200. Kiểm tra authorization và điều kiện representation **trước** bước gửi bytes.

## 7. Dự án Maven hoàn chỉnh chạy được

Ta tạo API phục vụ đúng một tệp test có 10 bytes ASCII `ABCDEFGHIJ`. Controller luôn trả `ResponseEntity<Resource>` với status 200 khi thành công; Spring tự chọn 200, 206 hoặc 416 theo Range. Kết quả dễ kiểm chứng vì kích thước và bytes đã cố định.

### 7.1. Cây dự án

```text
mvc-range-demo/
├── pom.xml
└── src/
    ├── main/java/demo/ranges/
    │   ├── RangeApplication.java
    │   └── SampleFileController.java
    └── test/java/demo/ranges/
        └── SampleFileControllerTest.java
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
    <artifactId>mvc-range-demo</artifactId>
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

**`src/main/java/demo/ranges/RangeApplication.java`**

```java
package demo.ranges;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RangeApplication {
    public static void main(String[] args) {
        SpringApplication.run(RangeApplication.class, args);
    }
}
```

**`src/main/java/demo/ranges/SampleFileController.java`**

```java
package demo.ranges;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class SampleFileController {

    private static final byte[] SAMPLE =
            "ABCDEFGHIJ".getBytes(StandardCharsets.US_ASCII);

    @GetMapping(path = "/sample",
                produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Resource> sample() {
        Resource resource = new ByteArrayResource(SAMPLE) {
            @Override
            public String getFilename() {
                return "sample.txt";
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sample.txt\"")
                .body(resource);
    }
}
```

**Vì sao ví dụ này hỗ trợ Range?**

- `SAMPLE` dài đúng 10 bytes (ASCII), được `ByteArrayResource` cung cấp để mở stream lại và lấy `contentLength()`.
- `ResponseEntity.ok()` tạo response ban đầu 200; Spring MVC có thể chuyển sang 206/416.
- `produces=text/plain` và `Content-Type: text/plain` phù hợp với bytes tệp.
- `Content-Disposition` sử dụng filename **do server viết cứng** để minh họa download, không dùng tên user gửi.
- Controller không đọc `Range`, không tự chia mảng và không cố định `Content-Length` hoặc `Content-Range`. Điều đó do framework quyết định theo từng request.
- `ByteArrayResource` chỉ thích hợp ví dụ nhỏ: tệp hàng GB nên dùng `FileSystemResource`/storage native range thay vì `Files.readAllBytes()` gây tốn RAM.

### 7.2. Lệnh chạy và kết quả HTTP

Trong thư mục `mvc-range-demo` với JDK 17+ và Maven:

```bash
mvn test
mvn spring-boot:run
```

Chạy hai lệnh theo thứ tự. Ứng dụng mặc định dùng `http://localhost:8080`.

**Toàn bộ tài nguyên:**

```bash
curl -i http://localhost:8080/api/files/sample
```

Các giá trị quan trọng:

```http
HTTP/1.1 200
Content-Type: text/plain
Accept-Ranges: bytes

ABCDEFGHIJ
```

**Bốn bytes đầu:**

```bash
curl -i -H 'Range: bytes=0-3' \
  http://localhost:8080/api/files/sample
```

```http
HTTP/1.1 206
Content-Range: bytes 0-3/10
Content-Type: text/plain

ABCD
```

**Ba bytes cuối:**

```bash
curl -i -H 'Range: bytes=-3' \
  http://localhost:8080/api/files/sample
```

Dự kiến `206` với `Content-Range: bytes 7-9/10`, body `HIJ`.

**Từ byte 7 đến cuối:**

```bash
curl -i -H 'Range: bytes=7-' \
  http://localhost:8080/api/files/sample
```

Dự kiến `206`, `Content-Range: bytes 7-9/10`, body `HIJ`.

**Phạm vi ngoài tài nguyên:**

```bash
curl -i -H 'Range: bytes=20-30' \
  http://localhost:8080/api/files/sample
```

Dự kiến `416` và header `Content-Range: bytes */10` theo nhánh auto-range của Spring MVC 6.1. Body lỗi hoặc các headers khác phụ thuộc framework/config, không phải một JSON contract riêng của demo.

**Hai vùng không liên tiếp:**

```bash
curl -i -H 'Range: bytes=0-1,8-9' \
  http://localhost:8080/api/files/sample
```

Dự kiến `206`, `Content-Type: multipart/byteranges; boundary=...`, từng part chứa `Content-Range`, phần `AB` và `IJ`. Boundary được tạo động, không hardcode trong tests. Với binary lớn, dùng `curl -D headers.txt -o bytes.bin` để không in nội dung file ra terminal.

Trên Windows PowerShell, `curl` có thể được ánh xạ đến công cụ khác tùy môi trường; hãy sử dụng `curl.exe` nếu cần các options curl chính xác.

## 8. Kiểm thử hợp đồng bằng MockMvc

**`src/test/java/demo/ranges/SampleFileControllerTest.java`**

```java
package demo.ranges;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SampleFileControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void noRangeReturnsWholeResource() throws Exception {
        mvc.perform(get("/api/files/sample"))
           .andExpect(status().isOk())
           .andExpect(header().string(
                    HttpHeaders.ACCEPT_RANGES, "bytes"))
           .andExpect(content().bytes(
                    "ABCDEFGHIJ".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void firstFourBytesReturn206() throws Exception {
        mvc.perform(get("/api/files/sample")
                    .header(HttpHeaders.RANGE, "bytes=0-3"))
           .andExpect(status().isPartialContent())
           .andExpect(header().string(
                    HttpHeaders.CONTENT_RANGE, "bytes 0-3/10"))
           .andExpect(content().bytes(
                    "ABCD".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void suffixRangeSelectsLastThreeBytes() throws Exception {
        mvc.perform(get("/api/files/sample")
                    .header(HttpHeaders.RANGE, "bytes=-3"))
           .andExpect(status().isPartialContent())
           .andExpect(header().string(
                    HttpHeaders.CONTENT_RANGE, "bytes 7-9/10"))
           .andExpect(content().bytes(
                    "HIJ".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void openEndedRangeSelectsRest() throws Exception {
        mvc.perform(get("/api/files/sample")
                    .header(HttpHeaders.RANGE, "bytes=7-"))
           .andExpect(status().isPartialContent())
           .andExpect(header().string(
                    HttpHeaders.CONTENT_RANGE, "bytes 7-9/10"))
           .andExpect(content().bytes(
                    "HIJ".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void invalidOffsetReturns416() throws Exception {
        mvc.perform(get("/api/files/sample")
                    .header(HttpHeaders.RANGE, "bytes=20-30"))
           .andExpect(status().isRequestedRangeNotSatisfiable())
           .andExpect(header().string(
                    HttpHeaders.CONTENT_RANGE, "bytes */10"));
    }

    @Test
    void multipleRangesUseMultipartByteranges() throws Exception {
        MvcResult result = mvc.perform(get("/api/files/sample")
                    .header(HttpHeaders.RANGE, "bytes=0-1,8-9"))
           .andExpect(status().isPartialContent())
           .andReturn();

        String contentType = result.getResponse().getContentType();
        assertTrue(contentType != null &&
                contentType.startsWith("multipart/byteranges;"));

        String body = new String(
                result.getResponse().getContentAsByteArray(),
                StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("Content-Range: bytes 0-1/10"));
        assertTrue(body.contains("Content-Range: bytes 8-9/10"));
        assertTrue(body.contains("AB"));
        assertTrue(body.contains("IJ"));
    }

    @Test
    void httpRangeCreatesCorrectResourceRegion() {
        Resource resource = new ByteArrayResource(
                "ABCDEFGHIJ".getBytes(StandardCharsets.US_ASCII));
        List<HttpRange> ranges = HttpRange.parseRanges("bytes=2-5");
        List<ResourceRegion> regions =
                HttpRange.toResourceRegions(ranges, resource);

        assertEquals(1, regions.size());
        assertEquals(2L, regions.get(0).getPosition());
        assertEquals(4L, regions.get(0).getCount());
    }
}
```

Chạy:

```bash
mvn test
```

Có **7 test methods**. Nếu tất cả pass trên project tạo đúng file, Maven kết thúc exit code `0` và tổng kết thường có `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`. Đây là mã học tập đã được thiết kế theo API của Java 17 và Spring Boot 3.3.5; bài này không tuyên bố đã chạy Maven trên môi trường tạo Sheet.

MockMvc kiểm tra pipeline MVC, nhưng không đại diện đầy đủ cho reverse proxy, cache, HTTP compression, connection failures hoặc streaming file hàng GB. Với binary thật cần kiểm thử end-to-end qua embedded server và CDN/proxy.

## 9. HttpRange và ResourceRegion: API bên dưới

`HttpRange` là abstraction cho các vùng bytes client yêu cầu. Có thể parse header:

```java
List<HttpRange> ranges =
        HttpRange.parseRanges("bytes=2-5");
List<ResourceRegion> regions =
        HttpRange.toResourceRegions(ranges, resource);
```

Đây là snippet cần biến `resource`, imports và context phù hợp; `httpRangeCreatesCorrectResourceRegion` ở phần tests đã cho một ví dụ chạy trọn.

Các factories khác:

```java
HttpRange firstFour = HttpRange.createByteRange(0, 3);
HttpRange fromSeven = HttpRange.createByteRange(7);
HttpRange lastThree = HttpRange.createSuffixRange(3);
```

`ResourceRegion` gồm `Resource` gốc, `position` và `count`:

```java
ResourceRegion region = new ResourceRegion(resource, 2L, 4L);
```

**`count` là số bytes, không phải last byte offset.** Với `bytes=2-5`: position = 2, count = 4. Nếu gán count = 5 thì vùng sẽ lấy thêm byte thứ 6, gây off-by-one.

`HttpRange.parseRanges` có thể ném `IllegalArgumentException` khi input sai hoặc quá nhiều ranges; `toResourceRegions` tính dựa vào length của Resource và có thể từ chối dữ liệu không thỏa. Đừng tự parse bằng regex đơn giản mà không xử lý suffix, overflow, comma-separated ranges và content-length thay đổi.

`ResourceRegionHttpMessageConverter` ghi một hoặc nhiều `ResourceRegion` ra HTTP response. Dùng manual `ResourceRegion` khi cần giới hạn range policy, backing store đặc biệt hoặc custom handling; nếu chỉ tải file thông thường thì `ResponseEntity<Resource>` với 200 đơn giản và ít lỗi hơn.

### Khi tự tạo response 206

Nếu bạn quyết định đọc header Range và trả regions thủ công, phải chịu trách nhiệm:

1. Đọc/validate Range và total size của **đúng selected representation**.
2. Kiểm tra authorization, preconditions và `If-Range` đúng ngữ nghĩa.
3. Nếu không dùng Range: trả 200 + toàn bộ Resource.
4. Nếu một hoặc nhiều range hợp lệ: trả 206 + đúng region(s), content type và Content-Range/multipart.
5. Nếu không thỏa: trả 416 + `Content-Range: bytes */total` khi thích hợp.
6. Không để `Content-Length` của 206 bằng chiều dài toàn bộ file.
7. Giới hạn số ranges, tổng bytes, seek costs, header length và read time.
8. Không chuyển binary response sang JSON envelope qua `ResponseBodyAdvice`.

Nếu không cần các trách nhiệm đặc biệt đó, đừng tự tái triển khai protocol đã được Spring hỗ trợ.

## 10. If-Range, ETag và bản nhất quán của tài nguyên

Client tải một phần file v1, sau đó file trên server thay thành v2. Nếu client yêu cầu phần còn lại và ghép bytes của hai versions, tệp cuối sẽ lỗi. Vì vậy **resume download** an toàn cần xác định toàn bộ các byte thuộc cùng một phiên bản của representation.

HTTP dùng:

- `ETag`: validator của representation.
- `Last-Modified`: metadata về thời gian sửa đổi.
- `If-None-Match` / `If-Modified-Since`: conditional caching, có thể dẫn đến `304 Not Modified`.
- `If-Range`: yêu cầu áp dụng Range **chỉ khi representation vẫn match validator**; nếu không, gửi toàn bộ.

Ví dụ:

```http
GET /api/files/video HTTP/1.1
Range: bytes=1000-
If-Range: "file-version-v7"
```

Nếu `If-Range` match theo **strong validation**, server có thể trả 206. Nếu không match, server phải bỏ qua Range và gửi toàn bộ representation (thường 200). **Weak ETag** có dạng `W/"..."` không thích hợp cho strong comparison trong If-Range.

**Lưu ý Spring MVC 6.1:** Nhánh auto range khi trả `ResponseEntity<Resource>` quan sát `Range` và status 200, nhưng **không nên mặc định** rằng application đã có full `If-Range` handling và snapshot consistency trong mọi đường Servlet/proxy. Khi tệp có thể thay đổi, kiểm thử cụ thể hoặc triển khai precondition/validator logic ở tầng phù hợp; đừng nối file v1 với v2 chỉ vì HTTP trả 206.

Nếu conditional GET trả `304` thì không xử lý Range như một phần của 200. Theo RFC, Range được xét sau các preconditions và chỉ có ý nghĩa khi response không Range sẽ là 200.

### Strong validator theo bytes

Một strong ETag nên gắn chắc với bytes của representation được chọn. Có thể dùng immutable storage version hoặc digest nội dung khi phù hợp; đừng tính SHA-256 của file 8GB từ đầu ở **mọi** Range request nếu backend không có cache metadata. Với file có Content-Encoding khác nhau cần kiểm tra validators theo đúng representation, tránh cùng validator cho hai byte sequences không giống nhau.

## 11. FileSystemResource, PathResource và bảo mật

Demo dùng `ByteArrayResource` vì chỉ có 10 bytes. Với file thật, nên dùng `FileSystemResource` hoặc `PathResource` có kích thước thực và stream đọc lại:

```java
Path file = trustedRoot.resolve(serverOwnedKey).normalize();
if (!file.startsWith(trustedRoot) ||
        !Files.isRegularFile(file) ||
        !Files.isReadable(file)) {
    throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "File not found");
}
Resource resource = new FileSystemResource(file);
return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(resource);
```

Đây là **snippet** chưa có class/imports hoặc định nghĩa `trustedRoot`/`serverOwnedKey`; nó chỉ diễn đạt ý tưởng. Thực tế:

- Kiểm tra quyền truy cập resource **trước** khi trả Resource.
- Không lấy tên tệp raw từ client hay request parameter làm storage path. Dùng storage key server tạo.
- `Path.normalize()` / `startsWith()` chưa đủ để ngăn symlink race hoặc TOCTOU; cần filesystem isolation và security policy.
- Đặt `Content-Disposition` đúng, encode filename an toàn; tránh dùng original filename từ upload làm đường dẫn lưu.
- Với file user-uploaded, cân nhắc `X-Content-Type-Options: nosniff` và loại content được phục vụ để tránh trình duyệt thực thi content nguy hiểm.
- Tài nguyên cần stable contentLength và nội dung ổn định trong lúc gửi. Nếu tệp đổi kích thước giữa lúc tính Range và đọc, `Content-Range` có thể sai.

Đối với object storage/CDN có hỗ trợ byte-range natively, có thể để backend/storage thực hiện Range trực tiếp thay vì download cả object về Java rồi tự skip; điều này quan trọng với file hàng GB.

## 12. Content-Encoding, caching và proxy

Range offsets áp dụng cho **selected representation bytes**. Một file 1000 bytes có thể gzip thành 400 bytes; `Range: bytes=0-99` của representation gzip không thể ghép tùy tiện với đoạn 0–99 của file chưa nén.

Tầng compression/filter/CDN có thể biến đổi response và làm các header sau không còn đúng:

- `Content-Range` và `Content-Length` nếu bytes bị thay.
- `ETag` nếu validator không đại diện cùng byte representation.
- `Vary: Accept-Encoding` và `Cache-Control` khi proxy cache các variants.
- `Content-Type` / `Content-Disposition` / `Accept-Ranges` nếu sản phẩm chuyển từ file download sang view nội bộ.

Nếu cần streaming/resume/video đáng tin, nên tránh biến đổi content coding tự động không kiểm soát trên endpoint range, hoặc bảo đảm mọi offsets, validators và cache keys nhất quán. Test trực tiếp origin và qua proxy/CDN, không chỉ MockMvc local.

## 13. HEAD, media players và download resume

**HEAD** trả metadata tương ứng GET nhưng không gửi response body. Nó hữu ích để client biết kích thước, validator và `Accept-Ranges`. Tuy nhiên việc trả `HEAD` phải thực sự không gửi bytes; framework/controller/proxy cần được kiểm tra riêng.

**Video player** có thể sử dụng Range để seek. Nhưng HTTP 206 không tự làm video seekable: file container/index/codec cũng phải hỗ trợ. Tương tự, PDF viewer chỉ tải từng phần hiệu quả khi cấu trúc PDF cho phép progressive access phù hợp.

**Resume client:** nếu nhận 206 và Content-Range khớp offset/version thì có thể nối bytes. Nếu server trả **200** thì nên coi như một full representation (thay thế file tạm thay vì nối). Nếu trả 416 thì kiểm tra total, validators, bytes đã tải và quyết định hoàn tất hay tải lại; không lặp retry 416 vô hạn.

## 14. Hiệu năng và hạn chế Range abuse

- Client có thể yêu cầu rất nhiều ranges nhỏ, dẫn đến seek/file reads và multipart overhead lớn. Áp giới hạn số ranges, tổng byte count, header size, timeout và rate limiting khi phù hợp.
- Nếu server đọc toàn bộ file vào `byte[]` rồi trả một đoạn, đã mất phần lớn lợi ích range. Với file lớn dùng seekable source hoặc native range I/O.
- `InputStream.skip()` trên một số nguồn có thể cần thực sự tiêu thụ bytes bị bỏ qua; API có `skip` không đồng nghĩa với random access hiệu quả.
- Nếu client gửi ranges trùng/lân cận, server có thể gộp theo RFC để giảm overhead.
- Mỗi request Range vẫn phải qua authorization và policy của tài nguyên như full GET; không tiết lộ dữ liệu chỉ vì gửi một phần.
- File privately owned cần `Cache-Control` phù hợp, tránh cache dùng chung làm lộ dữ liệu giữa users.
- Tránh ghi log body file/range bytes hoặc access tokens để debug.

## 15. Các lỗi thường gặp

| Hiện tượng | Nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| `Range` nhưng response luôn 200 | Body không phải `Resource` thích hợp, dùng `InputStreamResource`, status ban đầu khác 200, proxy bỏ Range | Kiểm tra Resource class, return type, original HTTP status |
| `206` nhưng gửi cả file | Controller đặt status 206 thủ công mà không cắt body | Để Spring auto-range từ status 200 hoặc tự dùng ResourceRegion đúng |
| Header `Content-Range` thiếu/mâu thuẫn | Tự gắn header sai hoặc converter khác ghi body | Test status, bytes và total length |
| Off-by-one khi lấy đoạn | Nhầm count với last position | `count=last-first+1` |
| `416` khi bắt đầu ở total size | Request có `first >= totalLength` | Kiểm tra `bytes */total` |
| Resume file bị hỏng | Các đoạn thuộc file versions khác | Strong validators và If-Range/versioned object |
| `multipart/byteranges` bị parse sai | Coi nó là multipart/form-data hoặc lấy một top-level Content-Range | Parse boundary/headers từng part |
| Download tốn RAM | `Files.readAllBytes` trên file lớn | FileSystemResource/PathResource/backend native ranged read |
| Browser phát video không seek | Media index, proxy compression hoặc headers sai | Kiểm tra file media, range responses end-to-end |
| `Content-Length` quá lớn trong 206 | Set bằng toàn file thay vì partial body | Để converter tính length đúng |
| Test MockMvc pass, production fail | Proxy/CDN/HTTP coding/streaming khác | End-to-end integration test |
| Tải file sai quyền | Dùng path ID không kiểm tra principal/resource | Authentication, authorization và storage keys |
| Tải file cũ/mới lẫn nhau | File bị sửa trong lúc đọc | Immutable version/snapshot và ETag |
| CPU tăng bởi hàng trăm ranges | Thiếu range count/size policy | Giới hạn parser và hạ tầng |

Chẩn đoán nhanh:

1. Xem status, Range request, `Accept-Ranges`, `Content-Range`, `Content-Length`, `Content-Type`.
2. So total size hiện tại và byte offset tính theo **selected representation**.
3. Kiểm tra Resource có repeatable stream và contentLength chính xác không.
4. Với một range, so `last-first+1` với bytes response.
5. Với nhiều range, kiểm tra từng `Content-Range` part và dynamic boundary.
6. Đối chiếu qua origin và proxy để phân biệt compression/cache.
7. Kiểm tra file version/ETag/If-Range khi resume.
8. Kiểm tra authorization, byte size limits và resource exhaustion.

## 16. Bài tập tự kiểm tra

1. Tài nguyên dài 10 bytes, `bytes=2-5` tạo `ResourceRegion.position`, `count` và body gì?
2. Vì sao `bytes=-3` và `bytes=3-` không tương đương?
3. `Content-Range: bytes 7-9/10` có số bytes bao nhiêu?
4. Nếu trả `ResponseEntity.status(PARTIAL_CONTENT).body(resource)`, Spring có tự cắt Resource không?
5. Vì sao `InputStreamResource` không phù hợp auto-range?
6. 416 có thể dùng `Content-Range` theo dạng nào?
7. Có được đặt một `Content-Range` top-level trong multipart/byteranges nhiều phần không?
8. Tại sao cần `If-Range` và strong ETag với resume trên file mutable?
9. Vì sao MockMvc test không thay thế proxy integration test?
10. Vì sao Content-Encoding ảnh hưởng offsets?
11. Một GET có Range có được bỏ qua quyền truy cập không?
12. Với 416, client có nên tự nối body lỗi vào file tạm không?

**Gợi ý:** (1) Position=2, count=4, CDEF. (2) Suffix lấy 3 byte cuối, open-ended từ offset 3. (3) 3 bytes. (4) Không; auto-range cần response ban đầu 200 và Resource phù hợp. (5) Stream một lần khó biết length và đọc lại. (6) `bytes */total`. (7) Không; mỗi part có Content-Range riêng. (8) Tránh nối dữ liệu khác phiên bản. (9) Compression/cache/container khác MockMvc. (10) Range tính theo encoded representation bytes. (11) Không. (12) Không; phải kiểm tra total và validators.

## 17. Tài liệu tham khảo

- [RFC 9110 — Range Requests, Content-Range, 206 và 416](https://www.rfc-editor.org/rfc/rfc9110.html)
- [Spring Framework — Range Requests (Spring MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-range.html)
- [Spring Framework 6.1 — HttpRange Javadoc](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/http/HttpRange.html)
- [Spring Framework 6.1 — ResourceRegionHttpMessageConverter](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/http/converter/ResourceRegionHttpMessageConverter.html)
- [Spring Framework 6.1 — ResourceRegion Javadoc](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/core/io/support/ResourceRegion.html)
- [Spring Framework — ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html)
- [Spring MVC 6.1 source — AbstractMessageConverterMethodProcessor](https://github.com/spring-projects/spring-framework/blob/v6.1.14/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/method/annotation/AbstractMessageConverterMethodProcessor.java)
- [MDN — Range requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Range_requests)

**Tổng kết:** Client dùng `Range` để yêu cầu byte offsets của một representation. Spring MVC có thể trả `206` và `Content-Range` tự động khi controller trả `Resource`/`ResponseEntity<Resource>` status ban đầu 200 (trừ `InputStreamResource`). `HttpRange` biểu diễn range do client yêu cầu, `ResourceRegion` giữ vùng position/count và `ResourceRegionHttpMessageConverter` ghi single/multipart bytes. Production còn phải quan tâm authorization, validator, caching, compression, version consistency và giới hạn requests.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-resources-006">ResourceHandler, vị trí tài nguyên tĩnh và thứ tự ánh xạ</a>

<details>
<summary>Xem chi tiết</summary>

# ResourceHandler, vị trí tài nguyên tĩnh và thứ tự ánh xạ trong Spring MVC

## 1. Vấn đề thực tế

Một dự án Spring Boot có các file:

```text
src/main/resources/static/css/site.css
src/main/resources/static/images/logo.svg
src/main/resources/templates/home.html
src/main/resources/manuals/intro.txt
```

Trình duyệt yêu cầu `GET /css/site.css`: Spring MVC cần tìm đúng file và trả CSS. Nhưng `GET /manuals/intro.txt` thường chưa hoạt động nếu bạn chỉ thêm thư mục `manuals` vào classpath. Còn `GET /products/42` nên chạy controller, ngay cả khi static resource handler mặc định của Spring Boot có URL pattern `/**`.

Từ ba tình huống đó, bài học giải đáp:

1. **ResourceHandler là gì?** URL pattern được ánh xạ sang `ResourceHttpRequestHandler` để tìm và phục vụ file tĩnh.
2. **Tài nguyên phải nằm ở đâu?** Những thư mục mặc định của Boot, đường dẫn classpath, đường dẫn servlet context, JAR/WAR và vị trí tự cấu hình.
3. **Thứ tự ánh xạ hoạt động thế nào?** `HandlerMapping` của controller thường ưu tiên trước resource handler mặc định; trong resource mappings, pattern cụ thể được chọn thay vì wildcard rộng.
4. **Khi nào dùng `WebMvcConfigurer#addResourceHandlers`, `spring.mvc.static-path-pattern`, `spring.web.resources.static-locations`, `spring.web.resources.add-mappings`?**
5. **Làm thế nào dùng caching, versioned URLs, conditional GET, content types, bảo mật?**
6. **Kiểm thử ra sao?** Viết Spring Boot 3/Java 17 có file tĩnh mặc định, thư mục riêng, route controller trùng URL với static file; dùng MockMvc xác nhận kết quả.

**Phạm vi:** Spring MVC trên Servlet stack, Spring Framework 6.x/Spring Boot 3.x, Java 17+. Cơ chế WebFlux dùng `ResourceWebHandler` và `WebFluxConfigurer`, **không** dùng `ResourceHttpRequestHandler` của Servlet MVC.

## 2. “Static resource” nghĩa gì?

Static resource là nội dung được trả về chủ yếu dưới dạng file/bytes thay vì chạy controller để dựng một tài liệu mới: CSS, JS, icon, hình ảnh, font, file PDF công khai, tài liệu tải xuống. “Tĩnh” **không có nghĩa** không có header HTTP động: Spring vẫn có thể tính `Last-Modified`, trả `304 Not Modified` hoặc áp dụng cache policy.

Ví dụ:

```http
GET /css/site.css HTTP/1.1
Host: localhost:8080
```

Phản hồi:

```http
HTTP/1.1 200 OK
Content-Type: text/css
Cache-Control: max-age=300

body { color: #123456; }
```

Phần status/content-type/header chính xác phụ thuộc cấu hình và tài nguyên, nhưng luồng chính là tìm file, mở stream và ghi nội dung. Client chỉ thấy **URL công khai**, không thấy hoặc không cần biết file nguồn nằm dưới `src/main/resources/static/`.

**Phân biệt ba khái niệm:**

- **Resource location**: thư mục nguồn như `classpath:/static/`. Đây là cấu hình phía server.
- **URL mapping pattern**: đường URL client yêu cầu như `/**` hoặc `/assets/**`.
- **Resource path**: phần còn lại sau URL pattern dùng để tìm file, ví dụ `css/site.css`.

Ví dụ cấu hình:

```java
registry.addResourceHandler("/files/**")
        .addResourceLocations("classpath:/downloadable/");
```

Client gọi `GET /files/guide.pdf` thì server tìm `classpath:/downloadable/guide.pdf`. Không tìm file ở `src/main/resources/static/files/guide.pdf` trừ khi thư mục ấy cũng nằm trong locations tương ứng.

## 3. Thành phần của Spring MVC resource pipeline

Một request thông thường:

```text
Client GET /assets/main.css
   │
   ▼
DispatcherServlet
   │
   ├── HandlerMapping: tìm handler cho URL
   │     ├── Controller @GetMapping(...)
   │     ├── View controller / welcome page...
   │     └── Resource handler mappings
   │
   ▼
ResourceHttpRequestHandler (khi URL được map sang static handler)
   │
   ├── xác định path tương đối từ URL đã match
   ├── duyệt locations đã đăng ký
   ├── ResourceResolver chain
   │     ├── version / encoded / webjar resolver nếu cấu hình
   │     └── PathResourceResolver để tìm file được phép
   ├── kiểm tra file tồn tại, được phép truy cập và đọc được
   ├── xác định media type, Last-Modified, cache headers
   └── truyền nội dung tài nguyên vào HttpServletResponse
```

**Các lớp cần phân biệt:**

| Thành phần | Package / vai trò |
| --- | --- |
| `ResourceHandlerRegistry` | API cấu hình qua `WebMvcConfigurer#addResourceHandlers`; đăng ký pattern và location |
| `ResourceHandlerRegistration` | Kết quả `addResourceHandler(...)`; cấu hình locations/cache/resource chain |
| `ResourceHttpRequestHandler` | MVC `HttpRequestHandler` thực sự phục vụ static resource |
| `HandlerMapping` | Quyết định request URL nên vào controller hay static handler |
| `ResourceResolver` | Tìm URL/resource: path, version, WebJars, encoded resources… |
| `ResourceTransformer` | Biến đổi response resource khi cần, ví dụ rewrite URL trong CSS |
| `org.springframework.core.io.Resource` | Trừu tượng truy cập classpath, filesystem hoặc ServletContext |
| `ViewResolver` | **Không phải static resource handler**; dùng khi controller trả logical view name |

`ResourceHandlerRegistry` không nhận GET rồi trực tiếp viết bytes. Nó tạo các mapping cho `ResourceHttpRequestHandler` khi ứng dụng khởi động; đến request runtime, `HandlerMapping` chọn handler này.

### 3.1. HandlerMapping khác ViewResolver

Controller:

```java
@Controller
class PageController {
    @GetMapping("/home")
    String home() {
        return "home";
    }
}
```

`"home"` là logical view name, thường được Thymeleaf/JSP resolver xử lý để tạo HTML. Trong khi đó, file `static/main.css` được `ResourceHttpRequestHandler` phục vụ như file, không cần logical view name, không cần Thymeleaf render.

Không nên tìm cách dùng `spring.thymeleaf.prefix=classpath:/static/` để giải quyết các URL CSS. Template engine và HTTP static resources là hai pipeline khác nhau.

### 3.2. Static handler khác @ResponseBody

Controller có thể dùng `ResponseEntity<Resource>` để tải file:

```java
@GetMapping("/download/{id}")
ResponseEntity<Resource> download(@PathVariable long id) {
    // Phân quyền, lookup Resource riêng theo id,
    // và trả header phù hợp.
}
```

Đây là đoạn **minh họa không độc lập**, thích hợp khi cần nghiệp vụ, quyền truy cập, nội dung người dùng upload, audit hoặc kiểm tra ID. Ngược lại, CSS/JS/logo công khai có URL quy ước nên để static resource handler giải quyết. Không dùng file handler công khai để vô tình lộ tài liệu riêng tư.

## 4. Vị trí static resources mặc định trong Spring Boot

Với Spring Boot Servlet MVC 3.x, trong điều kiện auto-configuration mặc định, tài nguyên được phục vụ từ các vị trí trên classpath:

1. `classpath:/META-INF/resources/`
2. `classpath:/resources/`
3. `classpath:/static/`
4. `classpath:/public/`

Ngoài các classpath locations, có thể có root của ServletContext khi triển khai kiểu servlet webapp. Với executable JAR, đặt nội dung dưới `src/main/resources/static/` là lựa chọn phổ biến, dễ đóng gói và dễ hiểu.

Ví dụ:

| Đường dẫn file trong dự án | URL mặc định | Ghi chú |
| --- | --- | --- |
| `src/main/resources/static/css/site.css` | `/css/site.css` | Không có `/static` trong URL |
| `src/main/resources/public/images/logo.svg` | `/images/logo.svg` | Vẫn được dùng như một resource location |
| `src/main/resources/resources/manual.pdf` | `/manual.pdf` | Một default location khác |
| `src/main/resources/META-INF/resources/favicon.ico` | `/favicon.ico` | Phù hợp convention của một số JAR |
| `src/main/resources/templates/home.html` | **Không có URL static mặc định** | Dành cho template engine, không phục vụ trực tiếp |
| `src/main/resources/config/secrets.yml` | **Không có URL static mặc định** | Không nên expose bí mật |
| `src/main/resources/manuals/intro.txt` | **Không có URL static mặc định** | Muốn công khai phải đăng ký location |

**Lưu ý:** Bảng mô tả từng vị trí có thể hoạt động khi có file tương ứng, không bảo đảm thứ tự “nếu trùng tên ở nhiều locations thì cái nào thắng” là hợp đồng ứng dụng lâu dài. Tránh đặt hai file public cùng relative path trong những static locations khác nhau; chỉ giữ một bản rõ ràng.

### 4.1. static không đồng nghĩa /static

File `src/main/resources/static/css/site.css` được phục vụ tại:

```text
http://localhost:8080/css/site.css
```

Không mặc định tại:

```text
http://localhost:8080/static/css/site.css
```

Muốn URL có tiền tố `/static`, phải đặt mapping URL riêng, ví dụ property `spring.mvc.static-path-pattern=/static/**` (phần 6). Khi đó URL và vị trí file vẫn là hai chuyện khác nhau.

### 4.2. Có nên dùng src/main/webapp?

`src/main/webapp/` là vị trí quen thuộc của các ứng dụng WAR/Servlet truyền thống. Với Spring Boot executable JAR, thư mục này **không phải cách an toàn để đóng gói static content**; nhiều build setup không đưa nó vào JAR như bạn mong muốn. Hãy dùng `src/main/resources/static/` hoặc một trong các default classpath locations.

JSP dưới `WEB-INF/jsp/` cũng không phải static public resource. Nếu ứng dụng dùng JSP, cần Servlet/JSP engine phù hợp, ViewResolver và đóng gói được hỗ trợ.

### 4.3. Welcome page index.html

Nếu `src/main/resources/static/index.html` tồn tại, Boot có thể dùng nó làm **welcome page** cho `GET /`. Một controller được map tường minh `@GetMapping("/")` thường được chọn trước welcome page mapping. Đây là một cơ chế cụ thể trong chuỗi HandlerMappings, không có nghĩa Boot “vô điều kiện phục vụ index.html trước controller”.

Ví dụ:

```text
static/index.html có tồn tại
  → GET / có thể được welcome-page handler phục vụ
  → GET /index.html được static handler phục vụ

Thêm @GetMapping("/")
  → GET / chạy controller thay vì welcome page fallback
```

Nếu có cả static index và template index, Boot mặc định tìm static index trước, rồi mới đến template index, với điều kiện không có route explicit ưu tiên hơn.

## 5. Thứ tự ánh xạ là thứ tự HandlerMapping, không phải tên file

Khi Spring MVC tìm handler cho một URL, các `HandlerMapping` có thể áp dụng các giá trị `order`. Theo API `ResourceHandlerRegistry#setOrder(...)`, mapping tài nguyên tĩnh mặc định có thứ tự:

```text
Integer.MAX_VALUE - 1
= 2147483646
= Ordered.LOWEST_PRECEDENCE - 1
```

Đây là giá trị **ưu tiên rất thấp**: các mapping controller MVC phổ biến có order nhỏ hơn và được xét trước. Nó giữ static resources như fallback sau những route động thực sự đã được khai báo.

Sơ đồ đơn giản:

```text
GET /catalog/42
  │
  ├── RequestMappingHandlerMapping có @GetMapping("/catalog/{id}")
  │        → MATCH → chạy controller → JSON
  │
  └── ResourceHandlerMapping mặc định "/**"
           → không đến lượt cho request này

GET /assets/site.css
  │
  ├── Không có controller cho URL này
  │
  └── Resource handler "/**"
           → MATCH → tìm static/assets/site.css
           → trả CSS
```

**Lưu ý về tính chính xác:** thứ tự giữa các loại HandlerMappings có thể thay đổi khi cấu hình MVC hoặc Spring Boot, và có thêm các nhóm như RouterFunctionMapping/WelcomePageHandlerMapping. Giá trị `Integer.MAX_VALUE-1` là mặc định của `ResourceHandlerRegistry` trong Spring Framework 6.2, **không phải một giá trị bảo đảm cho mọi loại handler mapping**.

### 5.1. Thứ tự mapping khác độ cụ thể của path

Có **hai mức** phân giải:

1. **Giữa các HandlerMapping**: order nhỏ hơn được hỏi trước. Một HandlerMapping đã tìm được handler thường kết thúc quá trình chọn, dù HandlerMapping khác có pattern URL “trông có vẻ cụ thể hơn”.
2. **Bên trong một HandlerMapping**: hệ thống so sánh các URL patterns, thường chọn pattern phù hợp và cụ thể hơn theo matching strategy đã cấu hình.

Ví dụ với hai resource patterns đăng ký cùng một ResourceHandlerRegistry:

```java
registry.addResourceHandler("/assets/**")
        .addResourceLocations("classpath:/brand-assets/");

registry.addResourceHandler("/**")
        .addResourceLocations("classpath:/fallback-assets/");
```

Trong cùng mapping, URL `/assets/logo.svg` có pattern `/assets/**` cụ thể hơn `/**`. Nhưng **không** coi thứ tự hai dòng Java là phương thức bảo đảm ưu tiên; matching sử dụng các rules của PathPattern/Ant matcher tương ứng.

Với Spring Boot đã có mapping `/**` mặc định, việc đăng ký thêm `/manuals/**` qua `addResourceHandlers` vẫn có thể hoạt động vì pattern cụ thể hơn trên cùng chuỗi MVC resource mappings; hãy test khi thay đổi order hoặc bật `@EnableWebMvc`.

### 5.2. Tại sao wildcard /** có thể gây khó debug 404?

Khi Boot đăng ký static handler mặc định `/**`, URL không trùng controller như `/not-found-route` vẫn có thể **match ResourceHttpRequestHandler**, nhưng resource file tương ứng không tồn tại. Kết quả vẫn là 404, song exception có thể là `NoResourceFoundException` (Spring 6.1+) thay vì “No handler found”.

Hệ quả:

- Không khẳng định cứ HTTP 404 là `DispatcherServlet` không tìm thấy handler.
- Cấu hình `spring.mvc.throw-exception-if-no-handler-found=true` không tự giải quyết mọi 404 nếu `/**` vẫn match resource handler.
- Kiểm tra log handler type và source exception; phân biệt URL chưa có controller và URL static resource không tồn tại.

**Không đặt resource handler cao hơn toàn bộ controller chỉ để ưu tiên CSS**, vì nó có thể “nuốt” route động nếu pattern quá rộng.

### 5.3. Phân biệt Filter order và HandlerMapping order

Servlet `FilterRegistrationBean#setOrder(...)` quyết định thứ tự filter trong Servlet container; `HandlerMapping#setOrder(...)` quyết định việc MVC chọn handler. `ViewResolver#setOrder(...)` lại áp dụng khi controller trả logical view name. Ba loại order độc lập.

```text
Servlet Filter order
  → DispatcherServlet
      → HandlerMapping order
          → controller hoặc resource handler
              → ViewResolver order (CHỈ khi có view name)
```

Chỉnh `@Order` trên filter không phải cách thay thứ tự static-vs-controller. Chỉnh ViewResolver không làm file CSS được phục vụ sớm hơn.

## 6. Bốn công cụ cấu hình thường bị nhầm

### 6.1. spring.mvc.static-path-pattern: đổi URL pattern

Mặc định static resources được map tại `/**`. Muốn công khai dưới URL prefix `/resources/`:

```properties
spring.mvc.static-path-pattern=/resources/**
```

Khi đó file `classpath:/static/css/site.css` được truy cập qua `GET /resources/css/site.css` theo cấu hình mặc định phù hợp.

Property này **không dời file trên classpath**. Đừng nhầm nó với directory location. Cũng cân nhắc rằng welcome-page mapping có thể bị ảnh hưởng bởi đường static path, nên phải kiểm thử `GET /` khi thay property.

### 6.2. spring.web.resources.static-locations: đổi nguồn

Muốn chỉ dùng một directory custom thay thế các classpath locations mặc định:

```properties
spring.web.resources.static-locations=classpath:/site-assets/
```

Khi đó Boot lấy static resources từ location được cấu hình này (và khả năng ServletContext root trong Servlet MVC), thay vì cộng thêm tất cả các default classpath locations. **Đây là REPLACE, không phải append** trong cách Boot xử lý property.

Vì vậy nếu đã set location custom rồi không thấy `static/css/site.css` nữa, hãy kiểm tra bạn có vô tình thay thế các default locations hay không.

Muốn sử dụng cả nguồn custom và một nguồn mặc định, hãy khai báo rõ danh sách:

```properties
spring.web.resources.static-locations=classpath:/site-assets/,classpath:/static/
```

Đừng dùng property cho một thư mục yêu cầu chính sách URL riêng khi `addResourceHandlers` mô tả rõ hơn.

### 6.3. spring.web.resources.add-mappings=false: tắt tự động

```properties
spring.web.resources.add-mappings=false
```

Khi đặt false, Boot không thêm **các mappings static mặc định** của mình. Các resource handlers do bạn chủ động đăng ký vẫn có thể tồn tại qua cấu hình MVC tương ứng. Đây có thể là lựa chọn khi muốn API-only, static files do CDN/reverse proxy phục vụ hoặc muốn kiểm soát toàn bộ mapping.

**Không tự bật property này chỉ để xử lý một 404**: có thể làm mất CSS/JS/favicon/welcome page đang chạy đúng. Sau thay đổi, test các URL public và route controller chính.

### 6.4. addResourceHandlers: thêm mapping tường minh

Để công khai riêng thư mục `classpath:/manuals/` tại `/manuals/**`:

```java
@Configuration
class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/manuals/**")
                .addResourceLocations("classpath:/manuals/")
                .setCacheControl(
                        CacheControl.maxAge(Duration.ofMinutes(5))
                                .cachePublic());
    }
}
```

*Đoạn cấu hình minh họa; cần imports `Configuration`, `WebMvcConfigurer`, `ResourceHandlerRegistry`, `CacheControl` và `Duration`. Project chạy đầy đủ ở mục 9.*

Cách này thêm một mapping có URL namespace rõ ràng; nó **không cần thay** đường static mặc định `/**`. Khi ứng dụng có nhiều nhóm files (ảnh, manual công khai, bundle), nên thiết kế path tường minh, ví dụ `/assets/**`, `/manuals/**` hoặc `/webjars/**`.

### 6.5. @EnableWebMvc trong Spring Boot

Không thêm `@EnableWebMvc` chỉ vì muốn đăng ký một mapping resource. Trong Spring Boot, annotation này làm bạn tự chịu trách nhiệm phần lớn MVC configuration, khiến các defaults Boot có thể không còn được áp dụng.

Ưu tiên:

- Dùng Boot properties cho thay đổi đơn giản.
- Dùng `@Configuration implements WebMvcConfigurer` và override `addResourceHandlers` cho mapping thêm.
- Chỉ dùng `@EnableWebMvc` khi chủ đích kiểm soát MVC ở mức Framework và đã bổ sung lại những thành phần cần thiết.

## 7. Cache và versioned URLs của static resources

`ResourceHttpRequestHandler` không chỉ đọc file tĩnh; phản hồi còn có thể mang **caching headers** và xử lý **conditional GET**. Hãy phân biệt ba nhiệm vụ thay vì bật một property rồi coi như đã xử lý hết:

| Nhiệm vụ | HTTP/Spring mechanism | Quyết định thiết kế |
| --- | --- | --- |
| Cache trong thời hạn | `Cache-Control`, ví dụ `max-age` | File không version không nên cache dài khi bytes có thể đổi tại cùng URL |
| Kiểm tra còn mới | `Last-Modified` / `If-Modified-Since` và status `304` | Không phải mọi nguồn Resource cung cấp timestamp chính xác |
| Tránh cache cũ sau deploy | Resource chain, URL fingerprint theo nội dung hoặc version cố định | Phải phát sinh URL mới trong HTML/template và giữ asset cũ đủ lâu |

Ví dụ cho resource tương đối ít đổi, handler riêng có thể đặt cache 5 phút:

```java
registry.addResourceHandler("/manuals/**")
        .addResourceLocations("classpath:/manuals/")
        .setCacheControl(
                CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic());
```

Đây là fragment bên trong `WebMvcConfigurer#addResourceHandlers`; cần imports `CacheControl`, `Duration` và cấu hình bean thích hợp. Response có thể chứa `Cache-Control: max-age=300, public`. Nếu client gửi `If-Modified-Since` và resource có metadata cập nhật phù hợp, server có thể phản hồi `304 Not Modified` mà không truyền lại body.

Với static assets như CSS/JS được cache dài, **đường dẫn có content hash** giúp khi bytes đổi thì URL đổi. Nhưng tạo một URL phiên bản không đồng nghĩa có `Cache-Control` đúng, và bật `VersionResourceResolver` không tự sửa mọi đường dẫn hardcode trong HTML. Cần bảo đảm asset URLs được tạo/rewrite bằng template, `ResourceUrlProvider`, filter tương ứng hoặc manifest của bundler. Đừng thêm version resolver vào mọi resource handler tùy chỉnh nếu chưa kiểm tra mapping thực được dùng; một resolver custom cũng không được bỏ qua path containment.

Bài kế tiếp về **resource chain và dấu vân tay nội dung** là nơi trình bày cấu hình `ContentVersionStrategy`/`FixedVersionStrategy`, `VersionResourceResolver`, URL rewriting, Java config và kiểm thử rolling deployment. Ở đây điều quan trọng là xác định static file **được HandlerMapping nào phục vụ** trước khi chọn caching policy cho handler đó.

## 8. Bảo mật: file công khai không phải nơi chứa bí mật

Một static resource handler thường nên phục vụ **nội dung có thể công khai**. Những file sau không nên đặt trong public static locations:

```text
application-prod.yml chứa credentials
secret.key
private-invoices/customer-123.pdf
.env
backup.sql
repository .git/
```

Lý do: nếu có mapping rộng và file lọt vào resource root, client có thể gọi URL tương ứng trực tiếp. Việc thêm `/WEB-INF/`, đặt file ngoài web root hoặc bảo vệ bằng quyền truy cập thích hợp là trách nhiệm ứng dụng.

### 8.1. Path traversal

Ví dụ tấn công muốn lấy:

```text
GET /manuals/../config/secret.txt
GET /manuals/%2e%2e%2fconfig%2fsecret.txt
```

Spring MVC cùng `PathResourceResolver` và Servlet container có bảo vệ đối với path đi khỏi allowed resource locations. Tuy nhiên:

- Đừng vô hiệu hóa path validation hoặc tự ghép path từ request rồi mở `new File(...)`.
- Sự từ chối có thể xảy ra ở Servlet firewall/container/path matching trước resource handler; HTTP status cụ thể còn tùy cấu hình.
- Không dùng test “một biến thể traversal trả 404” để khẳng định đã chống mọi biến thể encoding.
- Hãy cập nhật Spring/container, giới hạn locations và test cả authorized/unauthorized file access theo môi trường triển khai.

### 8.2. Spring Security và static assets

ResourceHandler **không tự mở bypass Spring Security**. Servlet security filter chain có thể áp dụng cho CSS/JS/image requests. Nếu giao diện login không hiện CSS, kiểm tra authorize rules:

```java
// Ý tưởng policy trong SecurityFilterChain,
// không phải toàn bộ security configuration:
.requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
.anyRequest().authenticated()
```

*Đoạn minh họa chỉ để hiểu policy; cần dự án có Spring Security và cấu hình đầy đủ.*

Ưu tiên `permitAll()` cho static assets công khai thay vì loại bỏ toàn bộ filter security bằng `web.ignoring()` khi bạn muốn giữ các security headers và protections. Không mở `/**` công khai nếu API/website còn tài nguyên cần bảo vệ.

### 8.3. CORS, content type và content sniffing

Nếu asset được tải cross-origin, CORS có thể cần cấu hình tùy loại request/usage; static handler có thể có CORS policy riêng. Không thêm `Access-Control-Allow-Origin: *` cho toàn bộ resource một cách vô điều kiện khi có credentials hoặc nội dung nhạy cảm.

Đảm bảo media types được xác định đúng (ví dụ `text/css`, `application/javascript`, `image/svg+xml`) và cân nhắc `X-Content-Type-Options: nosniff`. Một số định dạng, đặc biệt SVG/HTML/JS do người dùng upload, có thể chạy script trong các bối cảnh khác nhau: **không đưa untrusted uploads vào thư mục static công khai mà không có policy/validation**.

## 9. Dự án Spring Boot 3 + Java 17 chạy được

Demo thiết kế để nhìn thấy **cả static mapping mặc định và mapping custom**, đồng thời chứng minh route controller có ưu tiên hơn wildcard static fallback.

### 9.1. Hành vi sẽ kiểm thử

- File `static/assets/site.css` được phục vụ tại `GET /assets/site.css` (static mapping mặc định `/**`).
- File `manuals/guide.txt` được phục vụ tại `GET /manuals/guide.txt` sau khi đăng ký `/manuals/**`.
- File `static/catalog/7` tồn tại, nhưng `GET /catalog/7` chạy controller trả JSON (mapping động được chọn trước).
- `GET /manuals/absent.txt` trả 404; đó là request match resource handler nhưng không có file.
- `GET /index.html` phục vụ static welcome-file asset; khi chạy trong Servlet container thật, `GET /` có thể sử dụng welcome-page support.
- Response cho `/manuals/guide.txt` có cache-control `max-age=300` (chính sách demo).

Dùng Java 17+, Maven, Spring Boot **3.3.13** và Spring MVC.

### 9.2. Cấu trúc thư mục

```text
resource-handler-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/example/ResourceHandlerDemoApplication.java
    │   └── resources/
    │       ├── application.properties
    │       ├── static/
    │       │   ├── index.html
    │       │   ├── assets/site.css
    │       │   └── catalog/7
    │       └── manuals/guide.txt
    └── test/java/example/ResourceHandlerDemoTest.java
```

### 9.3. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>resource-handler-demo</artifactId>
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

### 9.4. ResourceHandlerDemoApplication.java

Tạo file `src/main/java/example/ResourceHandlerDemoApplication.java`. Các class phụ package-private cùng file, chỉ class main là public.

```java
package example;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class ResourceHandlerDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResourceHandlerDemoApplication.class, args);
    }
}

@Configuration
class PublicManualsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // URL /manuals/** trỏ vào classpath:/manuals/
        // và không thay thế static handler mặc định của Boot.
        registry.addResourceHandler("/manuals/**")
                .addResourceLocations("classpath:/manuals/")
                .setCacheControl(
                        CacheControl.maxAge(Duration.ofMinutes(5))
                                .cachePublic());
    }
}

@RestController
class CatalogController {
    @GetMapping("/catalog/{id}")
    Map<String, Object> product(@PathVariable int id) {
        return Map.of("id", id, "source", "controller");
    }
}
```

**Vì sao code chạy được?**

- `@SpringBootApplication` bật Spring Boot MVC auto-configuration khi đã thêm `spring-boot-starter-web`.
- `PublicManualsConfig implements WebMvcConfigurer` chỉ **bổ sung** một resource mapping, không bật `@EnableWebMvc`, nên Boot defaults vẫn còn.
- `registry.addResourceHandler("/manuals/**")` đăng ký URL namespace; `addResourceLocations("classpath:/manuals/")` trỏ tới folder thực được Maven đóng gói từ `src/main/resources/manuals/`.
- `CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()` phục vụ cache public có thời hạn 300 giây; đây chỉ là policy demo, không phù hợp với mọi tài liệu.
- `CatalogController` nhận URL `/catalog/7` trước resource fallback; file static trùng URL được thêm vào dự án **cố tình** để chứng minh thứ tự.

### 9.5. application.properties

File `src/main/resources/application.properties`:

```properties
# Giữ mặc định; giải thích rõ để quan sát thứ tự ánh xạ.
spring.web.resources.add-mappings=true
spring.mvc.static-path-pattern=/**
```

Không định nghĩa `spring.web.resources.static-locations`: sử dụng các classpath locations mặc định của Boot và custom location qua WebMvcConfigurer. Không cần đặt một thứ tự `registry.setOrder(...)` nếu chưa có nhu cầu rõ.

### 9.6. static/assets/site.css

File `src/main/resources/static/assets/site.css`:

```css
body {
    font-family: system-ui, sans-serif;
    color: #123456;
}
```

URL là `/assets/site.css`. Trình duyệt không gọi `/static/assets/site.css`; chữ `static` chỉ là tên location trên classpath.

### 9.7. manuals/guide.txt

File `src/main/resources/manuals/guide.txt`:

```text
Welcome to the public manual.
```

URL là `/manuals/guide.txt` nhờ mapping custom. File này nằm ngoài bốn static classpath locations mặc định, minh họa rằng chỉ “nằm trong resources” không tự tạo URL công khai.

### 9.8. static/catalog/7 (tài nguyên cố ý trùng route)

File `src/main/resources/static/catalog/7`:

```text
STATIC-FALLBACK-SHOULD-NOT-WIN
```

Khi gọi `GET /catalog/7`, controller vẫn được chọn, response JSON là `{"id":7,"source":"controller"}` (thứ tự keys không cần cố định). File này chỉ là fixture để test routing precedence; trong dự án production không nên có duplicate URL vô ích.

### 9.9. static/index.html

File `src/main/resources/static/index.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Static Resource Demo</title>
    <link rel="stylesheet" href="/assets/site.css">
</head>
<body>
    <h1>Public index page</h1>
</body>
</html>
```

Đây là file HTML static, không phải Thymeleaf template: không có biểu thức view engine và có thể truy cập tại `/index.html`. Trong Boot Servlet MVC, cũng có thể được sử dụng cho welcome page `/` khi không có controller route riêng.

Khi ứng dụng có context path, dùng URL tương đối hoặc công cụ tạo context-aware URL trong template; literal `href="/assets/site.css"` trỏ tới origin root, nên chỉ phù hợp với demo chạy không có context path.

### 9.10. Chạy và kiểm tra bằng cURL

Cần JDK 17+ và Maven, ở thư mục chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Terminal khác:

```bash
curl -i http://localhost:8080/assets/site.css
curl -i http://localhost:8080/manuals/guide.txt
curl -i http://localhost:8080/catalog/7
curl -i http://localhost:8080/index.html
curl -i http://localhost:8080/manuals/not-found.txt
```

Kỳ vọng:

| URL | Status | Nội dung chính |
| --- | ---: | --- |
| `/assets/site.css` | 200 | CSS chứa `color: #123456;` |
| `/manuals/guide.txt` | 200 | Text `Welcome to the public manual.` |
| `/catalog/7` | 200 | JSON `{"id":7,"source":"controller"}` |
| `/index.html` | 200 | HTML chứa `Public index page` |
| `/manuals/not-found.txt` | 404 | Không có resource trong configured location |

Với `/manuals/guide.txt`, response header Cache-Control có `max-age=300`. `curl -i` hiển thị thêm headers phụ thuộc Tomcat/Boot. Đừng so sánh toàn bộ response byte-for-byte gồm ngày và headers tự sinh.

Để thử conditional HTTP (tùy Last-Modified của resource):

```bash
curl -I http://localhost:8080/manuals/guide.txt
# Đọc Last-Modified nếu có, rồi gửi:
curl -i -H 'If-Modified-Since: <date-from-last-modified>' \
  http://localhost:8080/manuals/guide.txt
```

Thay giá trị ngày đúng từ server. Nếu resource chưa thay đổi và server hỗ trợ validator thích hợp, response có thể là HTTP 304. Dòng có dấu góc ở đây là placeholder **chỉ trong lệnh CLI**, không phải giá trị HTTP thực.

## 10. MockMvc chứng minh default/custom/mapping priority

File `src/test/java/example/ResourceHandlerDemoTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceHandlerDemoTest {

    @Autowired
    MockMvc mvc;

    @Test
    void defaultBootStaticLocationServesCss() throws Exception {
        mvc.perform(get("/assets/site.css"))
            .andExpect(status().isOk())
            .andExpect(handler().handlerType(
                    ResourceHttpRequestHandler.class))
            .andExpect(content().string(
                    containsString("color: #123456;")));
    }

    @Test
    void customResourceHandlerServesManual() throws Exception {
        mvc.perform(get("/manuals/guide.txt"))
            .andExpect(status().isOk())
            .andExpect(handler().handlerType(
                    ResourceHttpRequestHandler.class))
            .andExpect(content().string(
                    containsString("Welcome to the public manual.")));
    }

    @Test
    void customManualHasFiveMinuteCachePolicy() throws Exception {
        mvc.perform(get("/manuals/guide.txt"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    "Cache-Control", containsString("max-age=300")));
    }

    @Test
    void controllerTakesPrecedenceOverStaticWildcard()
            throws Exception {
        mvc.perform(get("/catalog/7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.source").value("controller"));
    }

    @Test
    void missingCustomResourceIsNotFound() throws Exception {
        mvc.perform(get("/manuals/absent.txt"))
            .andExpect(status().isNotFound());
    }

    @Test
    void indexHtmlIsAvailableAsStaticFile() throws Exception {
        mvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                    containsString("Public index page")));
    }

    @Test
    void staticIsNotAUrlPrefixByDefault() throws Exception {
        mvc.perform(get("/static/assets/site.css"))
            .andExpect(status().isNotFound());
    }
}
```

Chạy `mvn test`. Test xác nhận không chỉ status code mà cả **handler type** cho resources và **nội dung file**. Khi URL `/catalog/7`, test JSON chứng minh controller thật sự chạy dù có file static trùng URL.

`@SpringBootTest` với `@AutoConfigureMockMvc` nạp Boot auto-configuration và custom WebMvcConfigurer; không cần mở network port. Nhưng có những khác biệt khi deploy WAR/executable JAR hoặc dùng reverse proxy. Để kiểm chứng welcome page `GET /`, caching qua browser và headers do container tạo, có thể bổ sung integration tests chạy embedded server trên random port.

### 10.1. Vì sao không test bằng cách “cứ 404 là OK”?

Request `/manuals/absent.txt` có thể được resource HandlerMapping chọn, nhưng không tìm file. Test 404 chỉ chứng minh resource không được trả, chưa giải thích **nhánh** gây 404. Muốn phân tích thêm, dùng `handler().handlerType(ResourceHttpRequestHandler.class)` khi handler tồn tại, kiểm tra resolved exception nếu cần hoặc bật debug logging. Với Spring Framework 6.1+ `NoResourceFoundException` có thể được dùng khi không tìm thấy resource.

## 11. Resource locations ở ngoài classpath

Có trường hợp files là ảnh upload công khai, có thư mục cố định do admin quản lý hoặc được cập nhật sau khi JAR build. Có thể dùng `file:` location:

```java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/media/**")
            .addResourceLocations("file:/srv/myapp/public-media/")
            .setCacheControl(
                    CacheControl.maxAge(Duration.ofMinutes(10)));
}
```

Đoạn trên **chỉ minh họa**. File location có thể khác trên Windows/Linux, container images và cloud mounts. Nếu chọn public filesystem root, phải cấu hình quyền truy cập/ownership và không trộn với private directories; file nguồn cần nằm dưới location được phép.

### 11.1. Vì sao không lấy path từ query parameter?

Không triển khai kiểu sau:

```java
// Ví dụ sai: KHÔNG dùng với input từ người dùng.
Path file = Paths.get(request.getParameter("path"));
return Files.readAllBytes(file);
```

Nó bỏ qua các checks mà resource handler và path resolver đang cung cấp, có thể dẫn đến traversal hoặc đọc file tùy ý. Nếu cần cấp file theo ID nghiệp vụ, map ID tới metadata tin cậy ở server, kiểm tra ownership và sử dụng API tải file có kiểm soát.

### 11.2. Lưu file trong S3/CDN/object storage

Static content lớn và có lưu lượng cao có thể đặt ở CDN/object storage. Khi đó Spring MVC resource handler không nhất thiết là nơi tối ưu để truyền toàn bộ bytes. Có thể trả signed URL, redirect có kiểm soát hoặc dùng CDN origins với quyền phù hợp. Tài nguyên của nhiều tenants cần chính sách ACL chính xác; đừng chỉ đổi resource location sang một bucket public rồi coi như đã bảo mật.

## 12. Tương tác với templates, frontend bundlers và reverse proxy

### 12.1. Thymeleaf/JSP reference static URLs

Trong Thymeleaf:

```html
<link rel="stylesheet" th:href="@{/assets/site.css}">
<img th:src="@{/images/logo.svg}" alt="Logo">
<script th:src="@{/js/app.js}" defer></script>
```

Template engine tạo đường URL thích hợp theo context path. File thật vẫn nằm trong `static/assets/site.css`, `static/images/logo.svg` hoặc thư mục mapping custom. Với resource chain versioned URLs, cần bảo đảm URL rewriting đã được cấu hình đầy đủ.

Đừng tham chiếu `@{/static/assets/site.css}` theo thói quen nếu mapping URL vẫn là `/**`.

### 12.2. Khi frontend tool đã build bundles

Các bundlers có thể tạo:

```text
dist/assets/main-48abd3.css
dist/assets/main-93cf12.js
```

Có thể copy build output vào `src/main/resources/static/` ở giai đoạn packaging, hoặc cấu hình resource location tới output thích hợp. Trong sản phẩm thực tế, pipeline build phải bảo đảm filenames và index.html tham chiếu khớp. Không đọc thẳng source React/Vue project như static production asset nếu browser không hiểu modules/chưa build.

### 12.3. Reverse proxy / ingress

Nginx, Apache, CDN hoặc gateway có thể phục vụ static assets **trước khi request tới Spring Boot**. Khi đó bạn thấy 404/403/cache headers nhưng Spring MVC chưa hề chạy. Chẩn đoán theo từng layer:

```text
Browser
  → CDN/browser cache
  → Reverse proxy
  → Servlet container
  → Spring Security filters
  → DispatcherServlet
  → HandlerMapping / ResourceHttpRequestHandler
```

Kiểm tra request có tới ứng dụng không, host/path rewrite, forwarded headers, cache key và thời điểm deploy assets. Không sửa `ResourceHandlerRegistry` nếu 404 do CDN trỏ nhầm thư mục.

## 13. Debug checklist theo thứ tự nhanh nhất

1. **Xác định URL ngoài trình duyệt**: `/assets/site.css` hay `/static/assets/site.css`?
2. **Xác định file thực**: có trong `target/classes/static/` hoặc bên trong JAR sau build không?
3. **Kiểm tra Boot config**: `spring.web.resources.add-mappings`, `spring.mvc.static-path-pattern`, `spring.web.resources.static-locations`; properties của profile đang chạy.
4. **Kiểm tra handler mapping**: một controller có route trùng URL không? Có registry custom pattern bao phủ URL không?
5. **Kiểm tra resource handler**: URL đã match nhưng relative path/location có đúng không?
6. **Kiểm tra order**: giữa các HandlerMappings, controller thường chạy trước resource fallback; đừng nhầm order của filter/view resolver.
7. **Kiểm tra Security/CORS**: request thực tế bị 401/403/redirect login hay không?
8. **Kiểm tra cache**: stale content, 304, Cache-Control, content-version URL đã đúng chưa?
9. **Kiểm tra reverse proxy**: request có tới Servlet container không, có bị rewrite path không?
10. **Dùng MockMvc** để kiểm tra resource URL và controller priority; dùng HTTP integration test để xác nhận Servlet container/cache headers và deployment packaging.

Trong môi trường phát triển có thể bật logging:

```properties
logging.level.org.springframework.web.servlet.resource=DEBUG
logging.level.org.springframework.web.servlet.handler=DEBUG
```

Không bật DEBUG toàn web framework trên production dài hạn nếu sinh log rất lớn hoặc có nguy cơ lộ dữ liệu.

## 14. Các lỗi phổ biến

| Hiểu nhầm | Hậu quả | Sửa như thế nào |
| --- | --- | --- |
| “File nằm trong resources là tự public” | `manuals/guide.txt` 404 | Đặt trong default static dir hoặc đăng ký addResourceHandlers |
| “Static file URL phải có /static” | Sai URL, 404 | Nhớ `classpath:/static/` không đồng nghĩa URL `/static/` |
| “Thay static-path-pattern sẽ đổi thư mục nguồn” | Request vẫn tìm vị trí cũ | Dùng static-locations để đổi location |
| “static-locations tự append mặc định” | CSS cũ biến mất | Đây là replace; khai báo đủ locations |
| “ResourceHandlerRegistry đặt trước controller nếu code config viết trước” | Hiểu sai precedence | HandlerMapping order, không phải thứ tự câu lệnh |
| “/** wildcard luôn thắng @GetMapping” | Nghĩ route động không chạy | Controller mapping thường có priority cao hơn |
| “Cứ 404 là NoHandlerFoundException” | Exception handler không bắt được lỗi static file | Xem NoResourceFoundException và mapping /** |
| “@EnableWebMvc chỉ bật MVC cho Boot” | Mất nhiều auto-configuration | Dùng WebMvcConfigurer khi chỉ cần chỉnh mapping |
| “ViewResolver xử lý CSS” | Chỉnh nhầm template prefix | Dùng resource mappings và ResourceHttpRequestHandler |
| “Cache static 1 năm mà không cần version URL” | Users thấy file cũ | Gắn hash/version hoặc cache ngắn |
| “Bật VersionResourceResolver là HTML tự đổi URL” | URL không khớp, cache issues | ResourceUrlProvider/encoding integration |
| “Bật CORS * cho mọi file” | Mở rộng quyền quá mức | CORS policy theo nhu cầu và credentials |
| “Có PathResourceResolver nên upload nào cũng an toàn” | XSS, lộ dữ liệu qua public files | Tách uploads private, validate MIME/ACL |
| “Servlet root và classpath static giống nhau” | Deploy WAR/JAR khác hành vi | Hiểu packaging/resource loading cụ thể |
| “Security phải bỏ qua mọi static assets” | Mất security headers/protections | Permit public paths trong SecurityFilterChain nếu phù hợp |
| “Thấy Cache-Control là chắc 304” | Sai HTTP caching semantics | 304 phụ thuộc conditional request/validators |
| “CSS không đổi trong browser thì sửa mapping” | Sửa sai layer | Kiểm tra browser/CDN cache trước |
| “File static luôn có thứ tự ưu tiên theo folder name” | Duplicate path ngoài ý muốn | Tránh trùng relative paths giữa locations |

## 15. Tự kiểm tra

**1.** File `src/main/resources/static/css/main.css` có URL nào dưới Boot default?  
**Đáp án:** `GET /css/main.css`, không phải `/static/css/main.css`.

**2.** File `src/main/resources/manuals/intro.txt` tự có URL không?  
**Đáp án:** không nằm trong các default locations; cần thêm mapping hoặc chuyển file.

**3.** `addResourceHandler("/public/**")` và `addResourceLocations("classpath:/assets/")`: URL `/public/site.css` tìm file ở đâu?  
**Đáp án:** `classpath:/assets/site.css`.

**4.** `ResourceHttpRequestHandler` làm gì?  
**Đáp án:** nhận static request được map, resolve file và phục vụ bytes/headers.

**5.** `ViewResolver` có phải `ResourceHttpRequestHandler` không?  
**Đáp án:** không; ViewResolver dùng cho logical view name trong pipeline render.

**6.** `HandlerMapping` của resource registry có order mặc định bao nhiêu trong Spring 6.2?  
**Đáp án:** `Integer.MAX_VALUE - 1`, ưu tiên rất thấp.

**7.** Khi `@GetMapping("/catalog/{id}")` và static `catalog/7` cùng tồn tại, ai thường thắng?  
**Đáp án:** controller vì RequestMappingHandlerMapping được xét trước resource fallback.

**8.** Có phải `/assets/**` luôn được xét trước `/**` vì được khai báo trước trong Java?  
**Đáp án:** không; path specificity và mapping rules quyết định trong cùng HandlerMapping.

**9.** Cấu hình nào đổi URL prefix của static assets?  
**Đáp án:** `spring.mvc.static-path-pattern`.

**10.** Cấu hình nào đổi resource locations của Boot?  
**Đáp án:** `spring.web.resources.static-locations`; nó thay thế default locations.

**11.** Tắt Boot static auto mappings thế nào?  
**Đáp án:** `spring.web.resources.add-mappings=false`.

**12.** Vì sao bật `@EnableWebMvc` dễ làm static files biến mất?  
**Đáp án:** bạn nhận trách nhiệm cấu hình MVC thay nhiều defaults của Boot.

**13.** `/manuals/not-found.txt` trả 404, có chắc không có handler không?  
**Đáp án:** không; resource handler có thể match rồi không tìm thấy file.

**14.** Khi nào nên dùng content version URL?  
**Đáp án:** khi cần cache dài hạn nhưng asset có thể đổi theo release; URL hash/version giúp cache busting.

**15.** `If-Modified-Since` có thể dẫn tới status nào nếu file chưa đổi?  
**Đáp án:** 304 Not Modified, tùy resource validator/HTTP policy.

**16.** Đặt private user PDFs vào `static/` có an toàn không?  
**Đáp án:** không mặc định; static location là khu vực public theo URL mapping, cần access control riêng.

**17.** Header `X-Content-Type-Options: nosniff` có vai trò gì?  
**Đáp án:** hạn chế browser đoán MIME vượt khai báo server, một lớp giảm nguy cơ xử lý sai kiểu.

**18.** Khi CDN trả 404, có chắc `ResourceHandlerRegistry` của Spring đang sai không?  
**Đáp án:** không; request có thể chưa đến Spring.

**19.** Vì sao cần kiểm tra bên trong JAR sau build?  
**Đáp án:** file đúng trong source tree chưa bảo đảm được đóng gói tới classpath runtime.

**20.** Nên viết test nào để chứng minh thứ tự controller/static?  
**Đáp án:** tạo route/controller và file static cùng URL, assert response từ controller và handler phù hợp.

## 16. Tài liệu chính thức

- [Spring Framework 6.2 — Static Resources](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/static-resources.html): `addResourceHandlers`, cache control, versioned resource URLs, resource resolver chain.
- [Spring Framework 6.2 — ResourceHandlerRegistry Javadoc](https://docs.spring.io/spring-framework/docs/6.2.10/javadoc-api/org/springframework/web/servlet/config/annotation/ResourceHandlerRegistry.html): `addResourceHandler` và `setOrder`, mặc định `Integer.MAX_VALUE - 1`.
- [Spring Framework 6.2 — ResourceHttpRequestHandler Javadoc](https://docs.spring.io/spring-framework/docs/6.2.16/javadoc-api/org/springframework/web/servlet/resource/ResourceHttpRequestHandler.html): tìm và phục vụ resource, conditional request/Last-Modified.
- [Spring Framework 6.2 — VersionResourceResolver Javadoc](https://docs.spring.io/spring-framework/docs/6.2.13/javadoc-api/org/springframework/web/servlet/resource/VersionResourceResolver.html): content/fixed version strategies.
- [Spring Boot 3.3 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/servlet.html): static content locations, `static-path-pattern`, `static-locations`, welcome page, WebJars.
- [Spring Framework — Handler Mappings](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/handlermapping.html): cách MVC chọn handler theo URL/order.
- [Spring Framework 6.2 — MVC Test Framework](https://docs.spring.io/spring-framework/reference/6.2/testing/spring-mvc-test-framework.html): MockMvc và kiểm thử handler.

**Ghi nhớ:** ResourceHandler gắn **URL pattern** với **vị trí file/resource thực** để `ResourceHttpRequestHandler` phục vụ. Spring Boot có các classpath static locations mặc định và fallback `/**`; controller mappings thường được xét trước. Khi file không xuất hiện, kiểm tra **đường URL → HandlerMapping order → resource location → file packaging → security/cache/proxy** thay vì đoán hoặc chỉnh sai ViewResolver.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-resources-007">Resource chain, phiên bản tài nguyên và đường dẫn có dấu vân tay nội dung</a>

<details>
<summary>Xem chi tiết</summary>

# Resource chain, phiên bản tài nguyên và đường dẫn có dấu vân tay nội dung trong Spring MVC

## 1. Bài toán: static assets đã đổi nhưng client vẫn thấy nội dung cũ

Trong ứng dụng Spring MVC render trang web, CSS, JavaScript, ảnh và font thường được cache tại browser hoặc CDN để tăng tốc. Nếu tên đường dẫn không đổi sau một lần deploy, cache có thể tiếp tục phục vụ tài nguyên cũ:

```text
Release A:
  HTML -> /css/site.css
  Browser lưu CSS A, Cache-Control: max-age=2592000

Release B:
  HTML -> /css/site.css
  Server có nội dung CSS B, browser vẫn có thể dùng CSS A

Release B với content fingerprint:
  HTML -> /css/site-<hash-B>.css
  URL mới -> cache key mới -> trình duyệt tải CSS B
```

**Content fingerprint** là mã nhận diện được tính từ **bytes nội dung file** và gắn vào public URL, thường nằm trước phần mở rộng. Ví dụ hình thức `/css/site-e36d2e05253c6c7085a91522ce43a0b4.css`. Khi file đổi, fingerprint thường đổi; trình duyệt không dùng nhầm cached response của URL cũ.

**Resource chain** là cơ chế Spring MVC gồm các `ResourceResolver` và `ResourceTransformer` để tìm, kiểm tra phiên bản, biến đổi hoặc phục vụ static resources. Kết hợp chain với `ResourceUrlProvider` và `ResourceUrlEncodingFilter`, ứng dụng có thể **viết URL logic** trong template nhưng **phát URL có version** cho client mà không cần đổi tên file trong source tree.

Sau bài này, cần nắm:

- `ResourceHttpRequestHandler` và các static resource locations.
- Resolver chain so với transformer chain; inbound resource URL so với outbound generated URL.
- `VersionResourceResolver`, `ContentVersionStrategy`, `FixedVersionStrategy`.
- Bật content versioning bằng Spring Boot properties hoặc Java `WebMvcConfigurer`.
- Sinh đường dẫn fingerprint qua Thymeleaf, `ResourceUrlProvider` và `ResourceUrlEncodingFilter`.
- Kết hợp HTTP caching, deploy nhiều phiên bản và CDN để tránh 404.
- Kiểm thử HTML → URL fingerprint → GET asset bằng embedded Servlet container.

Baseline ví dụ chạy là **Java 17+, Spring Framework 6.2.x / Spring Boot 3.5.x và Spring MVC Servlet**. Spring WebFlux dùng API resource reactive riêng; không import `org.springframework.web.servlet.resource` vào ứng dụng WebFlux.

## 2. Spring Boot phục vụ static files từ đâu?

Trong một Boot Servlet app, static resources thông thường được tìm ở:

```text
classpath:/META-INF/resources/
classpath:/resources/
classpath:/static/
classpath:/public/
```

Ví dụ:

| Tệp trong source | URL logic thường gặp |
| --- | --- |
| `src/main/resources/static/css/site.css` | `/css/site.css` |
| `src/main/resources/static/js/app.js` | `/js/app.js` |
| `src/main/resources/public/images/logo.svg` | `/images/logo.svg` |
| `src/main/resources/META-INF/resources/favicon.ico` | `/favicon.ico` |

Boot thường map static content vào `/**`. Có thể đổi pattern bằng `spring.mvc.static-path-pattern` hoặc static locations bằng `spring.web.resources.static-locations`. Folder `templates/` dành cho template engine **không mặc nhiên public** như static. Trong executable JAR, `src/main/webapp` không phải lựa chọn chuẩn như WAR.

Spring MVC đăng ký `ResourceHttpRequestHandler` cho các resource handlers; handler này biết cách đọc resource, xử lý HTTP conditions như Last-Modified, cung cấp media type và chạy resource chain đã cấu hình.

**Không thêm `@EnableWebMvc` chỉ để bật static resources trong Spring Boot**: annotation này có thể khiến Spring Boot MVC auto-configuration không còn áp dụng theo cách mặc định. Nếu cần custom MVC handler, dùng `WebMvcConfigurer` mà không thêm `@EnableWebMvc`, trừ khi bạn chủ ý quản lý toàn bộ MVC infrastructure.

## 3. Sơ đồ hoạt động khi HTML tham chiếu CSS có fingerprint

```text
File trong JAR:
  classpath:/static/css/site.css
                    |
Resource handler /css/** (hoặc Boot static handler /**)
                    |
         VersionResourceResolver
                    |
         ContentVersionStrategy
                    |
    tính MD5 hex từ bytes nội dung
                    |
        ResourceUrlProvider sinh URL
                    |
   /css/site-<fingerprint>.css
                    |
Browser GET /css/site-<fingerprint>.css
                    |
DispatcherServlet / ResourceHttpRequestHandler
                    |
VersionResourceResolver:
   bóc hash từ tên, tìm file site.css,
   xác minh hash khớp resource hiện tại
                    |
PathResourceResolver tìm bytes file thật
                    |
HTTP 200 + CSS + cache headers
```

Spring không bắt buộc **tạo file vật lý có fingerprint**. File đóng gói vẫn là `site.css`; URL versioned là kết quả mapping qua resolver. URL logic `/css/site.css` thường vẫn có thể được yêu cầu trực tiếp, nên cần chính sách cache phù hợp cho cả đường dẫn không version.

### So sánh inbound và outbound

- **Outbound**: tại thời điểm render HTML, `ResourceUrlProvider` tìm public versioned path cho `/css/site.css`, ví dụ `/css/site-abc....css`.
- **Inbound**: client gửi GET vào public versioned path; `VersionResourceResolver` loại phần version và kiểm tra nó có khớp nội dung actual file không.
- **Không khớp version**: URL giả mạo hash thường không được resolver phục vụ; nhiều cấu hình trả 404.
- **Tệp không tồn tại**: version strategy không biến tài nguyên thiếu thành tài nguyên hợp lệ.

Nếu bạn hardcode `href="/css/site.css"` trong một **static HTML file** được phục vụ nguyên bytes, resource URL rewrite không tự chạy trên chuỗi tùy ý đó. Phải dùng template/helper/manifest hoặc build-time rewriting.

## 4. Các thành phần của resource chain

### 4.1. ResourceResolver chain

| Thành phần | Nhiệm vụ |
| --- | --- |
| `PathResourceResolver` | Tìm resource gốc trong configured allowed locations; thường là resolver cuối |
| `VersionResourceResolver` | Áp dụng chiến lược version, sinh/kiểm tra public URL |
| `EncodedResourceResolver` | Chọn tài nguyên đã nén sẵn như gzip/brotli khi thích hợp |
| `WebJarsResourceResolver` | Resolve version-less WebJars URLs khi có locator dependency đúng |

**Thứ tự resolver ảnh hưởng correctness.** Khi có cả `EncodedResourceResolver` và `VersionResourceResolver`, Spring yêu cầu **Encoded trước Version** để content hash được tính dựa trên tệp **không nén**, tránh khác nhau do gzip/brotli.

Resolver có thể cache kết quả lookup theo cấu hình. `resourceChain(true)` trong Java config bật resource chain caching; `spring.web.resources.chain.cache` là property của Boot cho cache chain nội bộ, không tương đương header HTTP `Cache-Control`.

### 4.2. ResourceTransformer chain

Transformer có thể thay đổi **representation** khi phục vụ. Ví dụ `CssLinkResourceTransformer` xét `@import` và `url(...)` trong CSS, tra chain rồi rewrite link tương ứng với public URL:

```css
@import "theme.css";

.banner {
  background-image: url("../images/banner.svg");
}
```

Khi đúng transformer, CSS có thể trả references tới versioned URLs. Nhưng Spring **không tự phân tích lại mọi chuỗi JavaScript** chứa URL, ES-module import, dynamic `fetch()`, source map hay JSON manifest. Khi những assets này có logic phụ thuộc URL, có thể cần bundler, fixed strategy hoặc transformer có chủ đích.

Nếu cả resolver và transformer đều cần, chú ý đúng thứ tự xử lý và test **body thực sự trả về**, không chỉ nhìn source file.

## 5. ContentVersionStrategy: hash bytes và đường dẫn fingerprint

Trong Spring Framework 6.x, `org.springframework.web.servlet.resource.ContentVersionStrategy` dùng **MD5 hex** của nội dung để tạo version đưa vào tên file. Ví dụ:

```text
Resource: /css/site.css
MD5(bytes): <32 ký tự hex của file thật>
Public URL: /css/site-<32 ký tự hex>.css
```

MD5 của file rỗng là `d41d8cd98f00b204e9800998ecf8427e`. Hash này **chỉ hợp lệ cho nội dung rỗng**, không phải cho các đoạn CSS mẫu trong bài. Không hardcode hash giả vào HTML production.

Các đặc tính:

1. Cùng resource bytes/strategy → cùng fingerprint; deploy lại không bắt buộc tạo URL mới.
2. Bytes thay đổi → fingerprint thường khác; URL mới không đụng cache key cũ.
3. Hash từ bytes, nên khác biệt encoding/newlines/chuỗi comment cũng có thể đổi URL.
4. Không phải thuật toán security signing, checksum chống giả mạo hay **Subresource Integrity (SRI)**. MD5 không phù hợp làm bảo đảm nguồn tài nguyên đáng tin.
5. Không tự minify, transpile, tạo tệp .gz/.br hoặc tạo CDN deployment manifest.
6. Phiên bản trong URL giúp invalidation; **không quyết định thời hạn cache HTTP** nếu chưa cấu hình cache headers.

### Filename path version khác query-string version

```text
ContentVersionStrategy:
  /css/site-<hash>.css

Cách thêm query thủ công khác:
  /css/site.css?v=<hash>
```

Query version có thể hoạt động, nhưng CDN/proxy policies có thể bỏ qua hoặc chuẩn hóa query parameters. Spring MVC `ContentVersionStrategy` sử dụng URL path với filename versioning. Không mô tả `?v=...` như output chuẩn của class này.

## 6. Bật resource content fingerprint trong Spring Boot 3.5

Với default Boot MVC static handler, cấu hình đơn giản trong **`src/main/resources/application.properties`**:

```properties
spring.web.resources.chain.strategy.content.enabled=true
spring.web.resources.chain.strategy.content.paths=/**
```

YAML tương đương:

```yaml
spring:
  web:
    resources:
      chain:
        strategy:
          content:
            enabled: true
            paths: "/**"
```

Chọn **một** trong properties/YAML; không khai báo trái ngược ở hai nơi rồi đoán precedence.

Với cấu hình này, các assets ở static locations mà handler quản lý có thể có versioned URLs. `/**` ở đây là pattern **tài nguyên tĩnh**, không làm URLs mọi REST endpoints tự có fingerprint.

Nếu chỉ muốn một số thư mục, scope strategy cho URL patterns cụ thể:

```properties
spring.web.resources.chain.strategy.content.enabled=true
spring.web.resources.chain.strategy.content.paths=/css/**,/js/**
```

Để áp dụng cho ảnh/fonts mà CSS references, cần đảm bảo locations/patterns của chúng cũng có versioning hoặc chọn cách build phù hợp.

### Resource chain cache nội bộ

```properties
spring.web.resources.chain.cache=true
```

Property này liên quan cache **resource resolution trong Spring**, không tự tạo `Cache-Control: max-age=...`. Khi dev chỉnh file liên tục, có thể tắt cache hoặc dùng Boot DevTools/rebuild đúng cách. Trong production JAR immutable, cache nội bộ có thể giảm overhead.

### Vì sao không cần Java config cho ví dụ cơ bản?

Boot 3.5 hỗ trợ content/fixed resource strategies qua properties. Nếu đồng thời tự thêm resource handler cho cùng đường dẫn, hãy kiểm tra chain và mapping thực sự nào được dùng: Boot properties **không được giả định tự đi vào mọi custom ResourceHandlerRegistration**.

## 7. FixedVersionStrategy: prefix version thay vì hash từng file

Có loại assets mà filename có hash gây khó, đặc biệt JS module loader với relative module paths. Spring hỗ trợ **fixed version prefix** dựa trên release:

```properties
spring.web.resources.chain.strategy.content.enabled=true
spring.web.resources.chain.strategy.content.paths=/**

spring.web.resources.chain.strategy.fixed.enabled=true
spring.web.resources.chain.strategy.fixed.paths=/js/lib/**
spring.web.resources.chain.strategy.fixed.version=v12
```

Ví dụ output:

```text
/css/site.css
    -> /css/site-<content-hash>.css

/js/lib/module.js
    -> /v12/js/lib/module.js
```

`v12` là giá trị do ứng dụng/deploy quyết định. Khi nội dung JS đổi, URL **không tự đổi** nếu quên tăng version. Đổi v12→v13 dù bytes không đổi có thể khiến browser tải lại file không cần thiết. Đừng cấu hình patterns overlapping mà không test rằng assets trong `/js/lib/**` thật sự dùng fixed thay vì content strategy.

| Tiêu chí | Content version | Fixed version |
| --- | --- | --- |
| Giá trị version | Hash content bytes | Chuỗi release định sẵn |
| Một file đổi | Thường chỉ URL file đó đổi | Cần nâng release version |
| File không đổi qua deploy | URL có thể được dùng lại | Thường URL đổi theo release |
| Phù hợp | CSS, assets độc lập, image/font | JS module tree hoặc rollout theo release |
| Nguy cơ | Relative imports, build dependency graphs | Quên đổi version dẫn đến stale cache |

## 8. Chương trình Spring Boot 3.5 + Thymeleaf minh họa đầy đủ

Ví dụ gồm application Java 17, Thymeleaf template, một CSS, properties và integration tests. Spring Boot quản lý static resource handler, không dùng bean resource handler tùy chỉnh trong ví dụ này.

### 8.1. Cấu trúc thư mục

```text
resource-chain-demo/
├─ build.gradle
└─ src/
   ├─ main/
   │  ├─ java/demo/resource/ResourceChainApplication.java
   │  └─ resources/
   │     ├─ application.properties
   │     ├─ static/css/site.css
   │     └─ templates/home.html
   └─ test/
      └─ java/demo/resource/ResourceChainApplicationTests.java
```

### 8.2. Gradle dependencies

Với project đã khai báo Boot 3.5 plugin, repositories, Java 17 toolchain và dependency management, thêm fragment:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đây là **fragment `build.gradle`**, không phải toàn bộ file build. Nếu dùng Maven, thêm các starter tương ứng qua Spring Boot BOM.

### 8.3. Properties

Tạo `src/main/resources/application.properties`:

```properties
spring.web.resources.chain.strategy.content.enabled=true
spring.web.resources.chain.strategy.content.paths=/**
```

Không khai báo thêm `@EnableWebMvc` hoặc `WebMvcConfigurer` cho cùng resource pattern. Khi dùng Thymeleaf, Boot auto-configure URL rewriting tương ứng khi điều kiện thích hợp.

### 8.4. Java application và controller

Tạo `src/main/java/demo/resource/ResourceChainApplication.java`:

```java
package demo.resource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootApplication
public class ResourceChainApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResourceChainApplication.class, args);
    }
}

@Controller
class HomeController {
    @GetMapping("/")
    String home() {
        return "home";
    }
}
```

**Một file Java hợp lệ:** chỉ `ResourceChainApplication` là public; controller package-private được Boot component scan phát hiện. `@Controller` trả tên view `home`, không trả trực tiếp text/JSON như `@RestController`.

### 8.5. CSS thật

Tạo `src/main/resources/static/css/site.css`:

```css
:root {
  color-scheme: light;
}

body {
  font-family: system-ui, sans-serif;
  margin: 2rem;
  color: #214e34;
}

h1 {
  font-size: 1.8rem;
}
```

Nội dung này là dữ liệu demo. MD5 trong URL phụ thuộc **đúng bytes/line endings** của file khi đóng gói.

### 8.6. Template Thymeleaf

Tạo `src/main/resources/templates/home.html`:

```html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Resource Chain Demo</title>
  <link rel="stylesheet" th:href="@{/css/site.css}">
</head>
<body>
  <h1>Content fingerprint trong Spring MVC</h1>
  <p>Mở DevTools Network để xem CSS URL có hash.</p>
</body>
</html>
```

`th:href="@{/css/site.css}"` là URL logic. Khi Thymeleaf + `ResourceUrlEncodingFilter` + resource chain cùng hoạt động, HTML render cho browser có dạng:

```html
<link rel="stylesheet"
      href="/css/site-0123456789abcdef0123456789abcdef.css">
```

Đây là **minh họa format**, không phải hash thực của CSS trên. Phải lấy versioned URL từ HTML runtime; không hardcode hash vào HTML gốc.

### 8.7. Chạy thử HTTP

```bash
./gradlew bootRun

curl -i 'http://localhost:8080/'
curl -i 'http://localhost:8080/css/site.css'
```

HTTP đầu tiên thường trả 200 `text/html`, HTML có `href` versioned. HTTP thứ hai yêu cầu URL logic, thường trả 200 `text/css`. Sau đó copy đúng public CSS URL từ HTML vào curl, ví dụ:

```bash
# Thay 32 ký tự hex bằng giá trị thực tế từ HTML response.
curl -i 'http://localhost:8080/css/site-0123456789abcdef0123456789abcdef.css'
```

Dòng trên có hash giả, nên **không cam kết 200**; để thành công phải dùng URL runtime thực tế. Header như `Cache-Control`, `ETag`, `Last-Modified` phụ thuộc handler/cache config; đừng tự coi bật fingerprint là đã đặt caching policy một năm.

## 9. Kiểm thử integration qua Servlet container thật

Một unit test gọi `VersionResourceResolver` riêng có thể kiểm tra hash nhưng **không chứng minh HTML rendering, URL filter và Servlet resource handler cùng làm việc**. Dùng `@SpringBootTest(RANDOM_PORT)` để gửi HTTP qua embedded server.

Tạo `src/test/java/demo/resource/ResourceChainApplicationTests.java`:

```java
package demo.resource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResourceChainApplicationTests {

    private static final Pattern VERSIONED_CSS =
            Pattern.compile("href=\"(/css/site-[0-9a-f]{32}\\.css)\"");

    @Autowired
    TestRestTemplate http;

    @Test
    void templateLinksVersionedCssWhichCanBeFetched() {
        ResponseEntity<String> page =
                http.getForEntity("/", String.class);

        assertEquals(HttpStatus.OK, page.getStatusCode());
        String html = page.getBody();
        assertNotNull(html);

        Matcher matcher = VERSIONED_CSS.matcher(html);
        assertTrue(matcher.find(), "Expected versioned CSS link");

        ResponseEntity<String> css =
                http.getForEntity(matcher.group(1), String.class);

        assertEquals(HttpStatus.OK, css.getStatusCode());
        assertNotNull(css.getBody());
        assertTrue(css.getBody().contains("font-family:"));
    }

    @Test
    void unversionedLogicalUrlStillWorksInThisDemo() {
        ResponseEntity<String> css =
                http.getForEntity("/css/site.css", String.class);

        assertEquals(HttpStatus.OK, css.getStatusCode());
        assertNotNull(css.getBody());
        assertTrue(css.getBody().contains("color-scheme:"));
    }

    @Test
    void incorrectHashDoesNotResolve() {
        String badPath =
                "/css/site-00000000000000000000000000000000.css";

        ResponseEntity<String> css =
                http.getForEntity(badPath, String.class);

        assertEquals(HttpStatus.NOT_FOUND, css.getStatusCode());
    }
}
```

Chạy:

```bash
./gradlew test
```

Ba test được **thiết kế để pass** với project/baseline tương ứng; đây là kỳ vọng sau khi rà soát, **chưa phải kết quả Gradle thực thi**. Test kiểm tra hình thức hash gồm 32 hex thay vì hardcode một hash. Nếu deployment có `server.servlet.context-path=/shop` hoặc Security custom, cập nhật pattern/HTTP paths và quyền truy cập tương ứng.

Để kiểm tra thay đổi assets qua hai release, lấy URL A từ build A, chỉnh bytes CSS rồi build B và lấy URL B; assert URL khác, B trả nội dung mới. Không sửa static file runtime trên cùng một JAR rồi dựa vào chain cache để tự refresh.

## 10. Java config khi cần ResourceHandlerRegistry riêng

Phần này là **phương án thay thế** cho tự cấu hình Boot của phần 8, không phải code cần thêm vào demo đó.

Trong app dùng Spring MVC `WebMvcConfigurer` có thể cấu hình một resource handler riêng cho `/assets/**`:

```java
package demo.resource;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.VersionResourceResolver;

@Configuration
class AssetConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .resourceChain(true)
                .addResolver(new VersionResourceResolver()
                        .addContentVersionStrategy("/**"));
    }
}
```

Đặt file ở project có directory `src/main/resources/static/assets/`. File `app.css` trong đó có logical URL `/assets/app.css` và public versioned URL `/assets/app-<hash>.css`. `resourceChain(true)` bật caching resolver chain; `VersionResourceResolver` thực hiện versioning; Spring cung cấp cơ chế lookup resource gốc theo locations.

**Lưu ý cấu hình URL:** nếu cùng app vẫn có default Boot static handler `/**` và một custom handler `/assets/**`, phải xác minh path mapping cụ thể được chọn; đừng cấu hình hai bộ version strategies xung đột.

### 10.1. CSS link rewriting

Nếu custom chain cần rewrite `@import` hoặc `url(...)` trong CSS và chưa có transformer tương ứng:

```java
registry.addResourceHandler("/assets/**")
        .addResourceLocations("classpath:/static/assets/")
        .resourceChain(true)
        .addResolver(new VersionResourceResolver()
                .addContentVersionStrategy("/**"))
        .addTransformer(new CssLinkResourceTransformer());
```

Đây là **method fragment**, cần import `org.springframework.web.servlet.resource.CssLinkResourceTransformer`. Test CSS response thực tế, không thêm trùng transformer nếu chain đã tự đăng ký nó.

### 10.2. Kết hợp encoded resolver

Khi có precompressed assets gzip/brotli được tạo bởi build:

```java
registry.addResourceHandler("/assets/**")
        .addResourceLocations("classpath:/static/assets/")
        .resourceChain(true)
        .addResolver(new EncodedResourceResolver())
        .addResolver(new VersionResourceResolver()
                .addContentVersionStrategy("/**"));
```

Đây là **method fragment**, cần import `EncodedResourceResolver`. Thứ tự **Encoded trước Version** là điều quan trọng Spring docs nêu để content fingerprint ổn định với tệp chưa nén. Phải có tệp `.gz`/`.br` thực tế và test `Accept-Encoding`, `Content-Encoding`, `Vary` nếu muốn chứng minh khả năng phục vụ compressed variants. Thêm resolver không tự tạo file nén.

## 11. ResourceUrlProvider và ResourceUrlEncodingFilter

### ResourceUrlProvider

Spring MVC cung cấp `ResourceUrlProvider` làm API xây URL từ handlers và resolver chain:

```java
@Controller
class AssetInfoController {
    private final ResourceUrlProvider resourceUrls;

    AssetInfoController(ResourceUrlProvider resourceUrls) {
        this.resourceUrls = resourceUrls;
    }

    @GetMapping("/asset-info")
    String show(Model model) {
        String publicUrl =
                resourceUrls.getForLookupPath("/css/site.css");
        model.addAttribute("cssUrl", publicUrl);
        return "asset-info";
    }
}
```

Đây là **class fragment**, cần imports cho `@Controller`, `@GetMapping`, `Model` và `ResourceUrlProvider`; template `asset-info` phải có trong project. Trả về có thể là versioned path hoặc `null` khi resource lookup thất bại. `getForLookupPath` nhận **MVC lookup path**, không phải URL Internet có host, và có thể cần xử lý context path/public CDN base theo môi trường.

### ResourceUrlEncodingFilter

Class `org.springframework.web.servlet.resource.ResourceUrlEncodingFilter` wrap response để các view/helper sử dụng `HttpServletResponse#encodeURL` có thể đưa resource version vào URL trong lúc render.

- Với Spring Boot 3.5, Thymeleaf/FreeMarker thường được Boot auto-configure filter này khi có resource chain thích hợp.
- Với JSP, Spring Boot docs hướng dẫn đăng ký filter theo cách thủ công.
- Với static HTML và client-side JS dựng chuỗi URL, filter **không tự tìm/thay mọi text**.
- Nếu cần output URL ở một API/JSON manifest, gọi provider hoặc dùng build asset manifest.

Ví dụ cấu hình riêng cho JSP:

```java
@Configuration
class ResourceUrlFilterConfig {
    @Bean
    ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
        return new ResourceUrlEncodingFilter();
    }
}
```

Đây là fragment cần imports `Configuration`, `Bean` và `ResourceUrlEncodingFilter`. **Đừng thêm bean này vào demo Thymeleaf đã có filter auto-configured** chỉ vì tưởng mỗi ứng dụng phải tự tạo.

## 12. HTTP cache: resource fingerprint không tự đặt Cache-Control

`ContentVersionStrategy` thay đổi URL dựa trên content; `Cache-Control` thay đổi cách browser/CDN cache response. Đây là hai thao tác **bổ trợ nhưng độc lập**.

| Cơ chế | Tác dụng |
| --- | --- |
| Fingerprinted path | Cache key khác khi nội dung asset thay đổi |
| `Cache-Control: max-age` | Freshness lifetime của response |
| `ETag` / `If-None-Match` | Conditional request nếu HTTP handler có ETag |
| `Last-Modified` / `If-Modified-Since` | Conditional request theo timestamp tài nguyên |
| `304 Not Modified` | Cho phép dùng cached representation sau revalidation |
| `Cache-Control: immutable` | Thông báo asset URL sẽ không đổi representation trong thời hạn thích hợp |
| CDN cache | Tầng cache bên ngoài app; cần chú ý key, purge và version retention |

Java MVC resource handler có thể cấu hình:

```java
registry.addResourceHandler("/assets/**")
        .addResourceLocations("classpath:/static/assets/")
        .setCacheControl(
                CacheControl.maxAge(java.time.Duration.ofDays(365)))
        .resourceChain(true)
        .addResolver(new VersionResourceResolver()
                .addContentVersionStrategy("/**"));
```

Fragment cần import `CacheControl`. **Nguy cơ:** resource handler này cũng có thể phục vụ URL logic `/assets/app.css` không version, nhưng `max-age=365d` sẽ áp lên response đó nếu không phân biệt. Một URL không đổi nhưng content đổi là recipe cho stale cache. Với production cần chia policies giữa URLs versioned và unversioned (hoặc giới hạn truy cập/cache của unversioned) và assert headers cho cả hai.

Property Boot có thể dùng:

```properties
spring.web.resources.cache.cachecontrol.max-age=30d
spring.web.resources.chain.cache=true
```

Cái đầu ảnh hưởng **HTTP headers** của resource handler; cái sau ảnh hưởng **resource resolution cache** trong app. Không bật max-age lớn cho toàn bộ static resources nếu một số entry points hoặc unversioned URLs còn cần revalidate thường xuyên.

### HTML document nên được cập nhật kịp thời

Chiến lược thực tế:

```text
HTML:
  Cache-Control: no-cache (có thể lưu, phải revalidate)
  hoặc TTL ngắn phù hợp sản phẩm

Versioned CSS/JS:
  Cache-Control: public, max-age=31536000, immutable
  chỉ khi URL đại diện cho bytes bất biến và assets cũ còn sẵn
```

Đó là **khuyến nghị kiến trúc**, không phải headers tự sinh bởi resource version resolver. Nếu HTML vẫn cache một tháng, người dùng có thể tiếp tục dùng asset URL từ release cũ dù release mới đã deploy.

## 13. Rolling deployment và asset retention — lý do 404 sau nâng cấp

Giả sử hai server đang phục vụ hai release:

```text
Node A: site.css -> hash AAA
Node B: site.css -> hash BBB

HTML từ A tham chiếu /css/site-AAA.css
Request asset đến B
B chỉ có bytes tương ứng hash BBB
=> VersionResourceResolver không xác nhận hash AAA
=> có thể trả 404
```

Đặc biệt nguy hiểm khi rolling update/blue-green deployment và client vẫn giữ HTML cũ. Resource chain của từng app instance **không phải kho lưu mọi phiên bản đã phát hành**.

Giải pháp:

- Giữ assets của nhiều release trên **shared CDN/object storage** đủ lâu cho rollback/transition.
- Build/đóng gói directories versioned hoặc manifest có retention; không chỉ ghi đè static files cũ.
- Đồng bộ HTML rollout với asset availability.
- Áp dụng fixed release prefix khi nhóm tài nguyên cần deploy nguyên cây phụ thuộc.
- Theo dõi 404 của static URLs, đặc biệt hash từ release trước.
- Không purge CDN versioned assets quá sớm sau rollout.

URL content-hash chỉ hữu ích cho long cache nếu **cùng URL luôn biểu diễn cùng bytes** và origin có thể phục vụ nó khi còn client cần.

## 14. JavaScript module imports và source maps

Ví dụ JS module:

```javascript
// main.js
import { greet } from "./util.js";
greet();
```

Nếu HTML tham chiếu `main-<hash>.js` thì relative import `./util.js` trong JavaScript **không bảo đảm** tự được resource chain rewrite. `CssLinkResourceTransformer` chỉ xử lý CSS `@import`/`url()`, không phân tích ES module syntax.

Các phương án:

1. **Fixed version prefix** cho toàn bộ cây JS lib/module nếu relative paths cần giữ nguyên.
2. **Bundler** như Vite, webpack hoặc Rollup xuất files có hashes và rewrite import references, kèm manifest.
3. Custom JS transformer chuyên biệt nếu có lý do mạnh và test parser/build correctness.
4. Giữ unversioned module import paths với caching policy phù hợp, nhưng có trade-off về cache busting.

Source maps `.map`, web fonts, SVG/images trong CSS, `srcset`, dynamic JS `fetch` hay CDN-hosted assets cũng cần kiểm tra URLs riêng; không giả định ResourceUrlEncodingFilter tự can thiệp mọi trường hợp.

## 15. Bảo mật static content và Spring Security

Static resources thường public, nhưng việc đặt tệp trong `static/` **không tự biến nó thành an toàn để công khai**. Không đưa private key, credentials, database backups, file cấu hình nhạy cảm hoặc các tệp upload có thể thực thi script vào resource locations công khai.

Khi dùng Spring Security, cần allow static paths **đúng phạm vi**:

```java
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/css/**", "/js/**", "/images/**")
            .permitAll()
        .anyRequest().authenticated());
```

Fragment yêu cầu Spring Security config và imports tương ứng, **không có trong runnable demo**. Với custom mapping `/assets/**`, sửa matchers tương ứng. Không mở `/**` chỉ vì CSS bị 403.

Hash MD5 **không thay thế HTTPS/SRI/CSP hoặc bảo vệ supply-chain**. Nếu tải third-party resources, cần chính sách integrity và CORS phù hợp. Với tệp người dùng upload, cần bảo vệ path traversal, MIME, content disposition và authorization độc lập với resource versioning.

## 16. Sai lầm thường gặp khi cấu hình

| Triệu chứng | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| HTML vẫn chứa `/css/site.css` | Không dùng URL rewriting; link nằm trong static HTML; filter thiếu | Xem HTML source, Thymeleaf và `ResourceUrlProvider` |
| Link versioned có hash nhưng GET 404 | Wrong hash, build bytes khác, URL mapping/scope/CDN sai | Xác minh packaged file và version actual |
| Build mới CSS đổi nhưng hash cũ | Container vẫn dùng JAR cũ, cache lookup/devtools chưa refresh, rebuild chưa copy assets | So sánh bytes trong build artifact |
| Hash khác dù CSS “giống nhau” | Khác whitespace/line endings/encoding/build transforms | So sánh byte-for-byte |
| CSS background/font 404 | CSS internal links không được rewrite hoặc relative path sai | Kiểm tra CSS output/transformer |
| JS module imports lỗi | JS links không được rewrite như CSS | Dùng bundler/fixed strategy |
| CSS GET 200 nhưng browser vẫn cũ | Cache HTML/unversioned paths quá dài | Xem Cache-Control và URLs thật trên Network tab |
| Sau rolling deployment có 404 assets | Node mới không giữ URL hash cũ | Shared asset store/version retention |
| Tất cả assets 401/403 | Security matcher không permit versioned path | Chỉnh whitelist chính xác |
| Dev sửa CSS mà không cập nhật | Resource chain cache hoặc build file chưa refresh | Tắt cache dev, rebuild/restart |
| JSP không đổi links | Thiếu `ResourceUrlEncodingFilter` hoặc URL tag không gọi `encodeURL` | Dùng filter/helper/provider |
| Có gzip/br nhưng hash bất ổn | Encoded/Version resolver order sai | Encoded trước Version |
| Tự thêm `@EnableWebMvc` làm mất tính năng Boot | MVC auto-config bị thay | Dùng `WebMvcConfigurer` |
| Static resources mất sau deploy JAR | Dùng `src/main/webapp`/path sai | Đặt trong classpath static |
| URL có dấu fingerprint nhưng sai host | CDN URL/forwarded proxy/context-path config không đồng bộ | Kiểm tra external base URL |

## 17. Kiểm tra phiên bản Spring Framework và Boot

| Phiên bản thường gặp | Điều cần nhớ |
| --- | --- |
| Spring Framework 5.x / Boot 2.x | Resource chain tồn tại; một số bản Boot rất cũ dùng `spring.resources.*` thay vì `spring.web.resources.*` |
| Spring Framework 6.x / Boot 3.x | Java 17+, properties `spring.web.resources.chain.strategy.content.*` và Thymeleaf URL rewriting như ví dụ |
| Spring Framework 7.x / Boot 4.x | Resource chain và content/fixed strategies vẫn có; Boot 4 reference tiếp tục mô tả `spring.web.resources.chain.strategy.*`; starter/test infrastructure có thể đổi |

Không lấy code từ WebFlux package `org.springframework.web.reactive.resource` khi dùng MVC `org.springframework.web.servlet.resource`. Cùng tên class nhưng API/return types khác.

Khi chuyển Boot 3→4, kiểm tra: Spring MVC starters, handler registration, resource chain, template filter, URL rewriting, cache headers, Security rules, WebJars locator và integration tests. Đừng chỉ kiểm tra project biên dịch; cần HTTP tests thật.

## 18. Checklist triển khai production

- [ ] Xác định đúng static asset owner: Spring MVC, bundler, object storage hay CDN.
- [ ] Chọn một chiến lược versioning chính, tránh thêm hash hai tầng không chủ đích.
- [ ] Content strategy được bật cho đúng patterns/locations, fixed strategy cho assets phù hợp.
- [ ] URL logic từ template/provider được rewrite, không hardcode MD5.
- [ ] Version resolver xác minh inbound URLs, có test hash sai 404.
- [ ] Resource resolver ordering đúng, nhất là Encoded trước Version.
- [ ] CSS internal links, JS modules, fonts, source maps và CDN paths được test.
- [ ] Cache headers cho HTML, versioned và unversioned assets được tách phù hợp.
- [ ] Giữ assets cũ đủ lâu khi rolling deployments, CDN/cache/rollback.
- [ ] Spring Security chỉ permit các static paths cần public.
- [ ] Không đặt secret/config/upload tùy tiện trong static locations.
- [ ] Có integration test HTML→fingerprinted href→asset GET và test sau rebuild.
- [ ] Có monitoring cho cache hit ratio, static 404 và URLs sai hostname/prefix.

## 19. Bài tập thực hành

1. Sửa một ký tự trong `site.css` của ví dụ rồi build lại. URL trong HTML thay đổi không? Vì sao hash phụ thuộc line endings?
2. Phân biệt outbound `ResourceUrlProvider` với inbound `VersionResourceResolver`.
3. Vì sao `@{/css/site.css}` trong Thymeleaf có thể được rewrite nhưng hardcoded href trong `static/index.html` thì không?
4. Khi thêm `spring.web.resources.cache.cachecontrol.max-age=365d`, URL logic `/css/site.css` có rủi ro gì?
5. Nếu HTML release A trỏ `site-A.css` nhưng request được route đến node B chỉ có bytes release B, vì sao có thể 404?
6. Viết test kiểm tra CSS link từ trang HTML theo regex thay vì MD5 cố định.
7. Dùng `CssLinkResourceTransformer` với CSS `url("../images/logo.svg")` rồi kiểm tra response.
8. Dùng Java config có `EncodedResourceResolver` và `VersionResourceResolver`: đăng ký theo thứ tự nào?
9. Khi ES module sử dụng relative import, vì sao FixedVersionStrategy hoặc bundler manifest có thể tốt hơn tự fingerprint filename?
10. Dự án đã dùng Vite tạo `app-<hash>.js`. Có cần thêm Spring VersionResourceResolver nữa không? Phân tích kết quả URL và chi phí.
11. Với Security bật, tại sao versioned `/css/site-<hash>.css` bị 403 dù file tồn tại? Sửa matcher mà không mở API.
12. Tìm sự khác biệt giữa cache chain trong JVM và HTTP cache browser; thiết kế kiểm thử cho mỗi loại.

## 20. Tài liệu chính thức

- [Spring Framework 6.2 — Static Resources](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/static-resources.html): `ResourceHttpRequestHandler`, resource chain, version resolver và URL provider.
- [Spring Boot 3.5 — Servlet Static Content](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): content/fixed strategies, default locations, Thymeleaf/FreeMarker integration.
- [Spring Boot 4 — Servlet Static Content](https://docs.spring.io/spring-boot/4.0/reference/web/servlet.html): resource chain configuration và tương thích thế hệ mới.
- [Spring Framework 6.2 — ContentVersionStrategy](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/resource/ContentVersionStrategy.html): MD5 fingerprint.
- [Spring Framework 6.2 — VersionResourceResolver](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/resource/VersionResourceResolver.html): inbound/outbound version paths.
- [Spring Framework 6.2 — ResourceUrlProvider](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/resource/ResourceUrlProvider.html): sinh public static URL.
- [Spring Framework 6.2 — CssLinkResourceTransformer](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/resource/CssLinkResourceTransformer.html): CSS @import và url().
- [Spring Framework 6.2 — ResourceUrlEncodingFilter](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/resource/ResourceUrlEncodingFilter.html): rewrite link trong response encoding.
- [Spring MVC — HTTP Caching](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-caching.html): Cache-Control và conditional requests.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="file-resources-008">Ngăn path traversal và kiểm soát quyền truy cập tệp</a>

<details>
<summary>Xem chi tiết</summary>

# Ngăn path traversal và kiểm soát quyền truy cập tệp trong Spring MVC

> **Phạm vi:** Spring Web MVC (Servlet stack), Java 17+ và Spring Boot 3.x/Spring Framework 6.x. Bài học tập trung vào cách API **upload, download, xem, sửa và xóa tệp** tránh truy cập ngoài vùng lưu trữ, không để người dùng đọc file của người khác, và bảo vệ hệ thống trước đường dẫn độc hại, symbolic links, race conditions, file quá lớn hoặc nội dung nguy hiểm. Cơ chế bảo vệ quan trọng nhất là **không cho client quyết định đường dẫn vật lý** và **kiểm tra quyền ở server cho từng thao tác trên từng tài nguyên**.

## 1. Vấn đề: một tên tệp có thể dẫn tới dữ liệu ngoài ý muốn

Giả sử ứng dụng có endpoint:

```http
GET /api/download?name=invoice.pdf
```

Code thiếu an toàn:

```java
// ANTI-PATTERN — không sử dụng trong ứng dụng thật.
@GetMapping("/download")
public byte[] download(@RequestParam String name) throws IOException {
    return Files.readAllBytes(
            Path.of("/srv/app/uploads/" + name));
}
```

Client có thể thay `name` bằng đoạn đường dẫn đi ngược lên thư mục cha. Nếu ứng dụng nối trực tiếp input vào đường dẫn, một file nhạy cảm ngoài thư mục upload có thể bị đọc (nếu process có quyền filesystem), ví dụ:

```text
/srv/app/uploads/../../private/config.yml
```

Đây là **path traversal / directory traversal**, một loại truy cập filesystem trái phép do đường dẫn do client kiểm soát. Các hệ điều hành, web server và URL decoder có thể biểu diễn đường dẫn theo nhiều cách, vì vậy chỉ kiểm tra xem chuỗi có chứa `../` hay không **không đủ**.

Bên cạnh traversal còn có **IDOR/BOLA (Broken Object Level Authorization)**: client chỉ cần thay ID tệp bằng ID hợp lệ của người khác. Đường dẫn vật lý có thể vẫn nằm hoàn toàn trong thư mục cho phép, nhưng truy cập vẫn trái quyền.

**Hai hàng rào độc lập:**

1. **Filesystem isolation:** tệp được mở phải thuộc vùng lưu trữ, với cách xử lý symlink và quyền OS phù hợp.
2. **Object-level authorization:** người dùng hiện tại phải được phép **đọc/tạo/cập nhật/xóa đúng tệp đó**, bất kể họ có đoán được ID hay không.

Không đạt một trong hai thì endpoint vẫn có thể không an toàn.

## 2. Những nguồn input không đáng tin

Đường dẫn có thể được đưa vào ứng dụng từ nhiều vị trí:

- `@PathVariable("filename")` và `@RequestParam("path")`.
- Header `Content-Disposition` của multipart upload, qua `MultipartFile.getOriginalFilename()`.
- JSON `{"path":"..." }` hoặc các form fields có tên file/thư mục.
- Metadata lưu trong database nếu trước đó được ghi từ client mà không kiểm soát.
- URL lấy từ callback, archive entry name khi giải nén ZIP/TAR hoặc URI chỉ tới remote file.

Dữ liệu đã được lưu trong DB **không tự động trở thành tin cậy**. Nếu user từng có thể sửa `storedPath`, việc lấy `storedPath` từ repository và truyền vào `Files.readAllBytes` vẫn có thể gây traversal.

**Đừng nhầm:**
- Client filename (tên hiển thị): giá trị để trình bày, phải hạn chế/escape.
- Server storage key (tên file nội bộ): **do server tạo**, không lấy từ tên client.
- Opaque file ID (ID API): client dùng tham chiếu tài nguyên; server ánh xạ ID sang metadata.
- Owner/tenant/ACL: dữ liệu quyền từ identity và hệ thống quản lý tài nguyên, **không** tin trường `ownerId` do client tự gửi.

## 3. Các biến thể của path traversal

| Biến thể | Rủi ro | Cách suy nghĩ |
| --- | --- | --- |
| `../` trên hệ Unix | Đi lên thư mục cha | Không cho path client đến filesystem trực tiếp |
| `..\\` trên Windows | Dùng ký tự phân tách Windows | Không chỉ tìm dấu `/` |
| `/etc/...` hoặc `C:\\...` | Đường dẫn tuyệt đối / ổ đĩa | Không cho input tự xác định root |
| UNC paths như `\\\\server\\share` | Truy cập mạng qua đường dẫn Windows | Phân biệt root/UNC trên nền tảng |
| `%2e%2e%2f`, `%252e%252e%252f` | URL encoding / nhiều lớp decode | Thống nhất một cơ chế decode, không tự decode lặp |
| `foo/../secret` | `..` ở giữa | Dùng `Path.normalize()` nhưng chưa đủ cho symlink |
| Symlink `uploads/link -> /etc` | Đi ra ngoài dù path dạng text ở trong root | Kiểm tra real path/NOFOLLOW, root private |
| Hard link hoặc bind mount | Dữ liệu filesystem được tham chiếu nhiều cách | Dùng OS isolation, kiểm soát ai có quyền thay nội dung |
| ZIP entry `../../x` | Zip Slip khi giải nén | Kiểm tra mỗi archive entry trước khi tạo file |
| Tên file `..`, dấu phân tách, NUL, tên đặc biệt | Parser/OS/ngữ cảnh hiển thị khác nhau | Sinh storage key độc lập từ server |

Các ví dụ là **dữ liệu kiểm thử/chẩn đoán**, không có nghĩa mọi framework/container sẽ cho chuỗi ấy đến handler. Một số tầng có thể trả 400/404 trước; điều đó **không thay thế** việc endpoint và storage service được thiết kế an toàn.

### 3.1. Vì sao so chuỗi prefix không an toàn?

Ví dụ:

```java
// SAI: so sánh chuỗi text, không hiểu ranh giới path component.
if (target.toString().startsWith("/srv/files")) {
    // Có thể chấp nhận /srv/files-evil/...
}
```

`Path.startsWith(Path)` có ngữ nghĩa theo thành phần đường dẫn, nên tốt hơn `String.startsWith`:

```java
Path root = Path.of("/srv/files").toAbsolutePath().normalize();
Path target = root.resolve(relativeName).normalize();

if (!target.startsWith(root)) {
    throw new SecurityException("Outside storage root");
}
```

Nhưng đoạn trên chỉ kiểm tra **đường dẫn từ góc nhìn lexical**. Nếu `/srv/files/link` là symbolic link tới thư mục ngoài root thì `target.startsWith(root)` vẫn có thể trả true dù file thực tế ở nơi khác. Xem mục 5 về `toRealPath` và TOCTOU.

### 3.2. Không dùng regex cấm vài chuỗi làm bảo vệ chính

```java
// SAI: không phải allowlist/hard isolation.
if (input.contains("../")) {
    throw new IllegalArgumentException("Bad path");
}
```

Trên Windows còn có `..\\`; dữ liệu có thể đã hoặc chưa URL-decode; path có segment đặc biệt; symbolic link không xuất hiện trong input. Ưu tiên **ID -> metadata -> server-generated key**, hơn là cố biến một client path bất kỳ thành “safe filename” bằng cách thay `../` thành chuỗi rỗng.

## 4. Thiết kế đề xuất: dùng ID thay vì tên file vật lý

Thay API:

```http
GET /api/files?path=../../private/secret.txt
```

bằng:

```http
GET /api/files/11111111-1111-4111-8111-111111111111
```

Server có metadata:

```text
fileId                 -> 11111111-1111-4111-8111-111111111111
owner                  -> alice
storageKey             -> 86a6c5d8-...-generated.bin
displayFilename        -> invoice.txt
mediaType              -> text/plain
size                   -> 1234
status                 -> AVAILABLE
tenantId               -> tenant-a
```

Lưu ý:
- `fileId` là **tham chiếu tài nguyên**; người dùng vẫn có thể gửi một fileId khác, nên **bắt buộc authorization**.
- `storageKey` do server tạo với UUID/nguồn ngẫu nhiên đủ mạnh hoặc khóa hệ thống nội bộ, **không** xây từ `getOriginalFilename()`.
- `displayFilename` là metadata để hiển thị; không có quyền quyết định nơi lưu.
- File nằm ngoài `resources/static`, `public` hoặc thư mục servlet webroot; không để Spring tự phục vụ tài nguyên private thông qua static handler.
- Các thao tác upload/download/delete đều phải tra metadata và kiểm tra `user/tenant/action/resource`.

Sơ đồ:

```text
GET /api/files/{opaqueId}
     |
Spring Security xác thực principal
     |
FileService.findById(opaqueId)
     |
Authorization: principal có thể READ file này?
     |
Metadata: server generated storage key
     |
Storage root private + kiểm tra đường dẫn/file type
     |
Open file -> stream qua ResponseEntity<Resource>
```

**Không dùng UUID khó đoán làm bằng chứng quyền.** UUID giảm khả năng đoán hàng loạt nhưng không thay thế authorization. Một link bị lộ, log sai hoặc client tự thay ID vẫn phải bị kiểm tra quyền.

## 5. Kiểm soát Path trong Java NIO

### 5.1. `normalize()` là phép toán lexical

```java
Path root = Path.of("/srv/app/private-files")
        .toAbsolutePath()
        .normalize();

Path p = root.resolve("drafts/../file.txt").normalize();
// p có hình thức /srv/app/private-files/file.txt
```

`Path.normalize()` loại bỏ các segment `.` và `..` có thể rút gọn về mặt cú pháp; nó **không đọc filesystem** và **không biết symlink trỏ tới đâu**. Nó cũng không xác thực quyền truy cập hoặc chứng minh file tồn tại.

### 5.2. `toRealPath()` kiểm tra filesystem thực

```java
Path configuredRoot = Path.of("/srv/app/private-files");
Path rootReal = configuredRoot.toRealPath(); // Root phải tồn tại.
Path realFile = rootReal.resolve(serverGeneratedName).toRealPath();

if (!realFile.startsWith(rootReal)) {
    throw new SecurityException("Outside permitted root");
}
```

Đây là snippet minh họa. `toRealPath()` (không truyền `NOFOLLOW_LINKS`) giải quyết symlinks và yêu cầu file tồn tại. Nếu một symlink trỏ ra ngoài, real path có thể không còn nằm dưới root, nên phép `startsWith` phát hiện được **tại thời điểm kiểm tra**.

Nhưng đoạn này vẫn có nguy cơ **TOCTOU** nếu một tiến trình khác có thể thay symlink hoặc đường dẫn sau lúc kiểm tra, trước lúc file được mở. Do đó không coi `toRealPath() + startsWith() + open()` là cơ chế chống race condition tuyệt đối.

### 5.3. `NOFOLLOW_LINKS` và giới hạn của nó

Khi không muốn mở symlink ở **thành phần cuối cùng**:

```java
try (InputStream input = Files.newInputStream(
        candidate,
        StandardOpenOption.READ,
        LinkOption.NOFOLLOW_LINKS)) {
    // Đọc/stream trong phạm vi chính sách áp dụng.
}
```

`LinkOption.NOFOLLOW_LINKS` yêu cầu Java không follow symbolic link theo thao tác hỗ trợ. Tuy nhiên nếu `candidate` có **thư mục cha** do đối thủ kiểm soát, các thành phần trung gian vẫn cần bảo vệ. Vì vậy nên kết hợp:

1. Root lưu trữ chỉ do service user quản lý; bên không tin cậy không có quyền tạo/chỉnh symlink/hard link trong đó.
2. Server tự sinh **một filename duy nhất**, tránh nested path tùy ý.
3. Không cho client truy cập path trực tiếp.
4. Với môi trường đối kháng mạnh, dùng `SecureDirectoryStream`/open-relative-to-directory-handle khi file system provider hỗ trợ, hoặc dùng object storage API đã có ranh giới quyền phù hợp.
5. Hạn chế quyền OS và container mount của tiến trình ứng dụng.

`NOFOLLOW_LINKS` là một biện pháp chuyên biệt, **không** thay cho kiểm tra ACL/owner hay phân quyền ở ứng dụng.

### 5.4. Tạo tệp không ghi đè file hiện có

Đối với upload, tránh:

```java
// Không nên: client tên file có thể trùng và ghi đè.
Files.copy(upload.getInputStream(), root.resolve(upload.getOriginalFilename()));
```

Nên tạo key server-side và dùng thao tác **atomic create**:

```java
String storageName = UUID.randomUUID() + ".bin";
Path destination = root.resolve(storageName);

try (InputStream in = upload.getInputStream();
     OutputStream out = Files.newOutputStream(
             destination,
             StandardOpenOption.CREATE_NEW,
             StandardOpenOption.WRITE)) {
    in.transferTo(out);
}
```

Đây là **fragment upload**; root phải là directory bảo vệ, file đã được kiểm tra quyền/giới hạn trước đó, và cần lưu metadata vào database cùng chính sách cleanup nếu lỗi. `CREATE_NEW` đảm bảo thao tác tạo mới thất bại nếu đích đã tồn tại (kể cả symbolic link) và kiểm tra tồn tại + tạo mới là atomic với các thao tác filesystem tương ứng. Đừng tự viết `if (!Files.exists(path)) Files.write(...)` rồi tưởng không có race. Với filesystem có ACL cần yêu cầu initial permissions/private create phù hợp.

## 6. Giới hạn tên file client: sử dụng để hiển thị, không làm storage key

`MultipartFile.getOriginalFilename()` là **input do client cung cấp**. Javadoc Spring cảnh báo giá trị này có thể chứa path information, `..` hoặc ký tự nguy hiểm; không được dùng trực tiếp làm đường dẫn lưu.

Sai:

```java
@PostMapping("/upload")
void upload(@RequestParam MultipartFile file) throws IOException {
    file.transferTo(Path.of("/data/files/", file.getOriginalFilename()));
}
```

Đây là **anti-pattern**: lỗi biên dịch/overload tùy cách dùng `Path.of(...)`, nhưng vấn đề chính là tin client filename và nguy cơ traversal/overwrite. **Đừng sử dụng** mẫu trên; file API thực tế nên có `@RequestParam("file")`, giới hạn kích thước và lưu theo tên server sinh.

Quy tắc tốt:
- Đọc original filename chỉ để hiển thị, audit hoặc gắn response `Content-Disposition` sau khi lọc/chuẩn hóa.
- Giới hạn độ dài và ký tự cho phép của tên hiển thị; loại bỏ control characters, dấu phân tách đường dẫn và chuỗi mang ý nghĩa đặc biệt.
- Với file download, dùng `ContentDisposition.attachment().filename(safeName, StandardCharsets.UTF_8)` để tạo header có escaping/encoding phù hợp, rồi test trên client/browsers mục tiêu.
- Dù display name là `../secret.txt` cũng **không bao giờ** được dùng để xây đường dẫn đọc/ghi thực tế.

**Không tự lấy `Paths.get(original).getFileName()` làm bảo đảm tuyệt đối:** trên Linux, dấu `\\` không là path separator như Windows, ký tự đặc biệt vẫn có thể gây header/UX issues; còn attacker vẫn có thể gây collision. Cách đơn giản là lưu display name qua allowlist và cấp storage name hoàn toàn độc lập.

## 7. Authorization: ngăn IDOR/BOLA trên mỗi file

### 7.1. Authentication khác authorization

- **Authentication:** xác nhận identity, ví dụ principal `alice`.
- **Authorization:** xác nhận `alice` được phép **READ/WRITE/DELETE file X** thuộc tenant tương ứng.

Một API đã gắn `.authenticated()` vẫn có thể cho mọi user đọc file của nhau nếu thiếu kiểm tra trên record:

```java
// ANTI-PATTERN: chỉ biết user đã đăng nhập, không kiểm tra owner.
@GetMapping("/api/files/{id}")
public Resource download(@PathVariable String id) {
    FileRecord file = repository.findById(id);
    return new FileSystemResource(storageRoot.resolve(file.storageKey()));
}
```

Code này có thể có path an toàn nhưng vẫn bị IDOR. Đúng hơn:

```java
FileRecord file = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND));

authorizationService.requireCanRead(currentUser, file);

Path filePath = storageService.resolveServerKey(file.storageKey());
Resource body = storageService.open(filePath);
```

Đây là snippet nghiệp vụ; trong demo chạy được ở mục 9, chính sách tối thiểu là so `authenticated principal name` với `owner` trong metadata. Production còn có team sharing, tenant scoping, ACL, account suspension, retention và audit.

### 7.2. 403 hay 404?

Với tài nguyên thuộc người khác:
- **403 Forbidden:** người dùng đã xác thực nhưng không được phép; rõ ràng để debug và quản trị.
- **404 Not Found:** API cố tình không tiết lộ tài nguyên có tồn tại, thường được dùng để giảm enumeration.

Hai lựa chọn có thể hợp lý nếu được thiết kế nhất quán. Không trả 200 kèm thông điệp lỗi. Demo ở mục 9 dùng **403** để học cách quan sát authorization; app có yêu cầu bảo mật tồn tại tài nguyên có thể dùng **404** sau khi áp chính sách kiểm tra.

### 7.3. Tránh lấy chủ sở hữu từ request

```json
{
  "fileId": "11111111-1111-4111-8111-111111111111",
  "owner": "alice"
}
```

Client gửi body như trên **không khiến client trở thành alice**. Chủ sở hữu và tenant phải lấy từ identity đã xác thực cùng metadata server, không lấy từ header `X-User` tự đặt hay giá trị `ownerId` trong form.

### 7.4. Spring Security: hai lớp kiểm soát

- **Lớp HTTP:** `SecurityFilterChain` bắt buộc đăng nhập cho `/api/files/**` và áp quy tắc method/path tổng quát.
- **Lớp domain:** service kiểm tra đúng file record (owner, tenant, sharing ACL, action), không dựa duy nhất vào URL pattern.
- **Lớp OS/storage:** quyền file/dir, container isolation và storage credentials giới hạn blast radius nếu ứng dụng bị lỗi.

Khi chạy nhiều instance hoặc object storage, metadata/ACL check và mở object phải nhất quán với permission design; nếu cần revoke ngay, tránh cache quyền quá lâu và tránh phát link public không thể thu hồi.

## 8. Những thao tác file cần kiểm soát riêng

| Thao tác | Rủi ro | Điều phải kiểm tra |
| --- | --- | --- |
| GET/download | Traversal, IDOR, lộ secrets | AuthN/AuthZ, server storage key, canonical containment và loại file |
| POST/upload | Truy cập ngoài root, overwrite, malware, DoS | AuthN/AuthZ, server key, CREATE_NEW, size/type/quota/scanning |
| PUT/replace | Ghi đè sai file/owner, lost update | Quyền WRITE, ID ownership, version/concurrency |
| DELETE | Xóa file của người khác, link tricks | Quyền DELETE, metadata state, safe deletion và audit |
| List/search | Lộ file names/IDs/tài khoản | Tenant/owner scope ngay trong query, pagination |
| Preview/thumbnail | Parser exploit, XXE/image bombs | Sandbox/isolation, format validation, resource limits |
| ZIP/TAR extract | Zip Slip, symlinks trong archive, zip bomb | Per-entry containment, symlink policy, total expanded quota |

**Không lưu upload vào** `src/main/resources/static`, `src/main/webapp` hoặc thư mục được servlet container tự phục vụ nếu file private. Một `@GetMapping` có authorization sẽ vô nghĩa nếu người dùng cũng có thể tải cùng file qua một public static URL.

## 9. Ví dụ hoàn chỉnh chạy được: file download theo ID, kiểm tra quyền

Ví dụ dưới đây dùng **Spring Boot 3.x, Java 17+, Spring Web và Spring Security**. Vì muốn minh họa authorization thực sự, ví dụ cấu hình **hai tài khoản demo** qua HTTP Basic: `alice` và `bob`. Mỗi người chỉ tải được file của mình. Một private temp directory được server tạo khi ứng dụng khởi động, metadata được định nghĩa phía server.

**Giới hạn cố ý:** đây là demo **chỉ GET/download**, không có upload, database, email, external storage hoặc authorization phức tạp. `{noop}` passwords chỉ dành cho local learning, không dùng production. CSRF không bị tắt; vì demo chỉ cung cấp GET, không cần thực hiện state-changing HTTP requests. File demo nhỏ và tên file server kiểm soát hoàn toàn.

### 9.1. Chuẩn bị Maven project

Tạo Spring Boot 3.x Maven project tại [Spring Initializr](https://start.spring.io/), Java 17+, dependencies **Spring Web** và **Spring Security**:

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
</dependencies>
```

Đây là **fragment `<dependencies>`** trong POM do Spring Initializr cung cấp, không phải toàn bộ POM. Đặt Java source dưới đây vào duy nhất file `src/main/java/com/example/demo/DemoApplication.java`.

### 9.2. Mã Java đầy đủ

```java
package com.example.demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        // CHỈ DEMO: {noop} và mật khẩu tĩnh không dùng production.
        return new InMemoryUserDetailsManager(
                User.withUsername("alice")
                        .password("{noop}alice-password")
                        .roles("USER")
                        .build(),
                User.withUsername("bob")
                        .password("{noop}bob-password")
                        .roles("USER")
                        .build());
    }
}

record FileRecord(
        String id,
        String owner,
        String storageKey,
        String displayName,
        long size) {
}

@Service
class PrivateFileStore {

    static final String ALICE_ID =
            "11111111-1111-4111-8111-111111111111";

    static final String BOB_ID =
            "22222222-2222-4222-8222-222222222222";

    private final Path root;
    private final Map<String, FileRecord> records;

    PrivateFileStore() throws IOException {
        // Thư mục do server tạo, không có client path trong tên.
        root = Files.createTempDirectory("private-file-demo-")
                .toRealPath();

        Path aliceFile = root.resolve("alice-note.txt");
        Path bobFile = root.resolve("bob-note.txt");

        Files.writeString(
                aliceFile, "Hello Alice\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

        Files.writeString(
                bobFile, "Hello Bob\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

        records = Map.of(
                ALICE_ID,
                new FileRecord(
                        ALICE_ID, "alice", "alice-note.txt",
                        "alice.txt", Files.size(aliceFile)),
                BOB_ID,
                new FileRecord(
                        BOB_ID, "bob", "bob-note.txt",
                        "bob.txt", Files.size(bobFile)));
    }

    Optional<FileRecord> find(String id) {
        return Optional.ofNullable(records.get(id));
    }

    InputStream open(FileRecord record) throws IOException {
        // Metadata được tạo nội bộ. Pattern giúp không vô tình
        // dùng đường dẫn có segment/dấu phân tách từ DB sau này.
        if (!record.storageKey().matches("[a-z0-9-]+\\.txt")) {
            throw new IOException("Invalid internal storage key");
        }

        Path path = root.resolve(record.storageKey()).normalize();
        if (!path.startsWith(root)) {
            throw new IOException("Outside storage root");
        }

        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException("Stored file is unavailable");
        }

        // Không follow symlink ở thành phần file cuối.
        // Root do ứng dụng sở hữu: không cho user tạo nested paths.
        return Files.newInputStream(
                path,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS);
    }
}

@RestController
@RequestMapping("/api/files")
class FileController {

    private final PrivateFileStore store;

    FileController(PrivateFileStore store) {
        this.store = store;
    }

    @GetMapping("/{id}")
    ResponseEntity<Resource> download(
            @PathVariable("id") String id,
            Authentication authentication) throws IOException {

        FileRecord record = store.find(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "File not found"));

        // Authorization cấp object: KHÔNG chỉ kiểm tra logged-in.
        if (!record.owner().equals(authentication.getName())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Access denied");
        }

        // Luồng được mở chỉ sau khi đã kiểm tra principal + owner.
        InputStream stream = store.open(record);
        Resource body = new InputStreamResource(stream);

        String contentDisposition = ContentDisposition.attachment()
                .filename(record.displayName(), StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(record.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
```

### 9.3. Giải thích bảo mật của chương trình

- `SecurityFilterChain` bắt buộc authentication; `Authentication#getName()` được Spring Security cung cấp từ identity HTTP Basic, không lấy từ header `X-User` do client tự bịa.
- `PrivateFileStore.find(id)` chỉ trả metadata mà **server tạo**. Controller không dùng `id` làm filesystem path.
- So sánh `record.owner()` với `authentication.getName()` là object authorization tối thiểu. Nếu bob gửi ID hợp lệ của Alice, API trả 403. Ứng dụng thật cần service/ACL kiểm soát quyền động và tenant.
- Root được tạo bằng `Files.createTempDirectory(...)` và thực hiện `toRealPath()` tại startup; file keys là chuỗi cố định nội bộ không chứa thư mục, không có path do client cung cấp. Thư mục lưu production phải đặt ở vị trí riêng có ACL/owner rõ ràng; ví dụ không khẳng định permissions của mọi nền tảng là giống nhau.
- `Files.isRegularFile(...NOFOLLOW_LINKS)` kiểm tra file cuối không phải symlink tại thời điểm kiểm tra; `Files.newInputStream(...NOFOLLOW_LINKS)` cố gắng không follow symlink ở file cuối khi mở. Root/private directory vẫn phải được bảo vệ khỏi tiến trình độc hại có thể đổi cấu trúc giữa hai thao tác.
- `InputStreamResource` giúp response converter truyền nội dung từ stream. Vì stream one-shot, response đặt trước `contentLength(record.size())` từ metadata demo đã có. Khi metadata không còn đáng tin hoặc file có thể đổi size, cần thiết kế cơ chế nhất quán và xử lý error/streaming riêng.
- `ContentDisposition.attachment()` tạo header download thay vì cho user kiểm soát raw header. Tên hiển thị trong metadata cũng nên được kiểm soát khi cho user tải lên.
- `Cache-Control: no-store` và `nosniff` giảm rủi ro caching/content sniffing. Chúng không thay authorization hoặc nội dung scan.
- Ví dụ có root thư mục tạm và metadata in-memory: **restart sẽ tạo lại file mới**; không dùng cách này để lưu file bền vững.

### 9.4. Khởi chạy

Trong thư mục Spring Initializr project:

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows PowerShell hoặc Command Prompt
mvnw.cmd spring-boot:run
```

Spring Boot mặc định dùng `http://localhost:8080` nếu chưa cấu hình port/context path.

### 9.5. Kiểm thử HTTP theo identity

**Alice tải file của Alice:**

```bash
curl -i -u alice:alice-password \
  http://localhost:8080/api/files/11111111-1111-4111-8111-111111111111
```

Phần chính của response:

```http
HTTP/1.1 200
Content-Type: text/plain
Cache-Control: no-store
X-Content-Type-Options: nosniff

Hello Alice
```

Trong response thực, còn có `Content-Disposition: attachment` với filename được encode theo cách của Spring và `Content-Length: 12`. Body là **12 byte**: `Hello` (5) + space (1) + `Alice` (5) + newline (1). Trình duyệt có thể download thay vì render text vì header attachment.

**Bob cố truy cập file Alice:**

```bash
curl -i -u bob:bob-password \
  http://localhost:8080/api/files/11111111-1111-4111-8111-111111111111
```

Dự kiến **403 Forbidden**; file không được mở bởi controller vì kiểm tra owner đã thất bại. Body lỗi cụ thể do Spring Boot/Security error configuration quyết định, nên không ép JSON exact output.

**Alice dùng ID không tồn tại:**

```bash
curl -i -u alice:alice-password \
  http://localhost:8080/api/files/99999999-9999-4999-8999-999999999999
```

Dự kiến **404 Not Found**.

**Không gửi thông tin đăng nhập:**

```bash
curl -i \
  http://localhost:8080/api/files/11111111-1111-4111-8111-111111111111
```

Dự kiến **401 Unauthorized** với challenge `WWW-Authenticate` thích hợp cho HTTP Basic, do Spring Security xử lý trước controller.

**Alice cố đưa traversal string vào URL:**

```bash
curl -i -u alice:alice-password \
  'http://localhost:8080/api/files/..%2F..%2Fprivate%2Fsecret.txt'
```

Request có thể bị servlet container/Spring Security firewall/MVC mapping từ chối trước handler dưới dạng 400/404. Nếu handler nhận được một ID không nằm trong map, nó không tạo filesystem path từ ID, do đó không mở được file ngoài root. **Không cam kết chính xác status cho mọi encoding/môi trường**; chỉ cam kết thiết kế không ánh xạ input đó thành path.

Trong PowerShell, dùng `curl.exe` và viết lệnh một dòng để tránh `curl` alias và ký tự xuống dòng Bash.

## 10. Tải lên an toàn: pipeline khuyến nghị

Demo ở mục 9 chỉ GET. Để thêm `POST multipart` trong ứng dụng thật, không chỉ cần `@RequestParam MultipartFile`; cần một pipeline có giới hạn rõ:

```text
Authenticated user
   |
Authorization: canUploadTo(targetFolder/tenant)?
   |
Multipart size limits + quotas + rate limiting
   |
Validate filename display + allowlisted extension
   |
Validate actual file type/signature + parse/sandbox policy
   |
Server generates opaque storage key
   |
Private root (outside webroot) + CREATE_NEW
   |
Copy as stream; compute hash/scan if needed
   |
Persist metadata: owner/tenant/ACL/size/status
   |
Return 201 Created + opaque file ID
```

### 10.1. Spring Boot multipart limits

Ví dụ trong `application.properties`:

```properties
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB
spring.servlet.multipart.file-size-threshold=0B
```

- `max-file-size` giới hạn một file.
- `max-request-size` giới hạn toàn multipart request.
- `file-size-threshold=0B` liên quan ngưỡng lưu phần nội dung multipart ra disk theo cơ chế Servlet/container; **không** thay thế quota đĩa, timeout, số lượng parts và cleanup.
- Những setting này không phải hard cap chung cho mọi JSON/binary request. Cấu hình thêm reverse proxy/container hoặc byte-counting limit tùy đường dữ liệu.

### 10.2. Không dùng `getBytes()` cho upload lớn

```java
// Không nên: tạo cả byte array trong heap.
byte[] full = upload.getBytes();
```

Thay vì vậy, sau khi phân quyền/giới hạn và tạo storage key:

```java
// Fragment minh họa bên trong storage service.
String storageKey = UUID.randomUUID().toString() + ".bin";
Path destination = storageRoot.resolve(storageKey);

try (InputStream input = upload.getInputStream();
     OutputStream output = Files.newOutputStream(
             destination,
             StandardOpenOption.CREATE_NEW,
             StandardOpenOption.WRITE)) {
    input.transferTo(output);
}
```

Đoạn trên cần `java.util.UUID`, `java.io.InputStream`, `java.io.OutputStream`, `java.nio.file.*`; `storageRoot` phải là thư mục private do server kiểm soát và `upload` là multipart object đã kiểm tra. Không dùng originalFilename để tạo destination. Cần rollback/cleanup nếu copy fail, không publish file metadata trước khi upload và scan hoàn tất.

### 10.3. MIME type và extension không đáng tin

`MultipartFile.getContentType()` là thông tin do client gửi. File tên `photo.png` hoặc header `image/png` không có nghĩa bytes thật là PNG. Với yêu cầu nghiệp vụ:
- Chỉ cho phép loại file cần thiết.
- Dựa vào file signatures + thư viện parse đáng tin cậy, không chỉ extension/header.
- Chặn tệp thực thi/script khi không cần thiết.
- Quét malware/CDR nếu môi trường yêu cầu.
- Tránh xử lý ảnh/XML/PDF/ZIP thiếu giới hạn kích thước sau khi giải mã hoặc giải nén.
- Không phục vụ file user upload trên cùng origin có thể thực thi HTML/JavaScript nếu nội dung không tin cậy; cân nhắc domain chuyên cho download và `Content-Disposition: attachment`.

**Zip Slip:** với archive, mỗi entry name là untrusted path. Cần normalize và kiểm tra containment của từng entry, chặn symbolic link, đường dẫn tuyệt đối, tổng kích thước đã giải nén và số entry. Chỉ kiểm tra filename của ZIP upload **không đủ**.

## 11. Tải xuống và Content-Disposition

Với private file đã được phép đọc, một response điển hình:

```java
return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename(safeDisplayName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore())
        .body(resource);
```

Đây là snippet riêng, cần `safeDisplayName` đã được kiểm soát và `resource` có thể stream. Không dùng raw string:

```java
// ANTI-PATTERN: tên lấy từ client có thể gây header/display issues.
response.setHeader("Content-Disposition",
        "attachment; filename=" + userSuppliedName);
```

Không cho client gửi content disposition để đổi đường dẫn mở file. Filename trong header chỉ điều khiển **gợi ý tên tải xuống**, không có quyền thay storage key.

Nếu endpoint hỗ trợ HTTP Range/partial content, cache validators, CDN hoặc signed links:
- Chỉ thực hiện sau khi đã kiểm tra ACL.
- Đảm bảo mỗi đoạn byte tải xuống đều thuộc cùng file được phép.
- Với redirect sang object storage, signed URL phải có phạm vi object cụ thể, TTL ngắn và không lộ credentials chung.
- Không lưu private response ở public CDN cache trừ khi đã thiết kế cache key/auth isolation rõ ràng.

## 12. Symlink, TOCTOU và giới hạn của path validation

### 12.1. TOCTOU nghĩa là gì?

**Time of Check to Time of Use**: hệ thống kiểm tra path/file ở thời điểm T1, nhưng mở/tác động file ở T2. Nếu một đối thủ có thể sửa symlink/thư mục giữa hai thời điểm, kiểm tra đúng ở T1 chưa chắc bảo vệ thao tác T2.

```text
T1: Files.isRegularFile("/private/root/a.txt")
        |
Attacker replaces entry a.txt with symlink
        |
T2: Files.newInputStream("/private/root/a.txt")
        |
Unexpected target
```

Nếu attacker có quyền sửa thư mục lưu, chỉ `normalize()` hay `toRealPath()` không giải quyết triệt để. Biện pháp trọng yếu là **OS directory ownership và ACL** sao cho attacker không thể thay đổi cấu trúc root.

### 12.2. Điều gì bảo vệ mạnh hơn?

- Sử dụng **flat private storage root** và storage key không chứa `/`, `\\` hoặc nested segments.
- Không cấp shell/local user không tin cậy quyền write trong root/parent directories.
- Dùng `CREATE_NEW` khi tạo, `NOFOLLOW_LINKS` lúc mở theo hỗ trợ API, và một file root không thể bị thay bởi actor khác.
- Với yêu cầu chống race trong môi trường filesystem shared/adversarial, cân nhắc `SecureDirectoryStream` (relative operations gắn directory handle) nếu provider hỗ trợ, hoặc cơ chế storage API chuyên dụng.
- File permission/ACL nên tối thiểu: user chạy app chỉ đọc/chỉnh những file cần thiết; không chạy process dưới tài khoản root hoặc dùng mount rộng chứa secrets.
- Nếu cách lưu trữ cho phép hardlinks hoặc bind mounts từ actor khác, cần chính sách OS/container mount phù hợp; chỉ kiểm tra symlink là chưa đủ.

**Lưu ý:** `SecureDirectoryStream` không phải lúc nào cũng được hỗ trợ bởi filesystem provider; phải có fallback có đánh giá an toàn, không tự “fallback” sang truy cập path không kiểm soát khi yêu cầu security cao.

### 12.3. Path validation và authorization phải giữ riêng

Ngay cả khi:
```text
Path is inside /srv/app/private-files
```
thì file vẫn có thể thuộc tenant khác. Và ngay cả khi file được gắn `owner=alice` trong database, một `storageKey=../../secret` vẫn có thể dẫn ra ngoài filesystem. Cần **cả hai** lớp kiểm soát.

## 13. Kiểm thử an ninh bằng MockMvc và service tests

Trong dự án Spring Boot demo, có thể thêm `spring-boot-starter-test` và dependency test cho Spring Security:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-test</artifactId>
  <scope>test</scope>
</dependency>
```

Test HTTP:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FileAccessTest {

    @Autowired
    MockMvc mvc;

    @Test
    void ownerMayRead() throws Exception {
        mvc.perform(get("/api/files/" + PrivateFileStore.ALICE_ID)
                        .with(httpBasic("alice", "alice-password")))
                .andExpect(status().isOk());
    }

    @Test
    void otherUserForbidden() throws Exception {
        mvc.perform(get("/api/files/" + PrivateFileStore.ALICE_ID)
                        .with(httpBasic("bob", "bob-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedMustAuthenticate() throws Exception {
        mvc.perform(get("/api/files/" + PrivateFileStore.ALICE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownIdIsNotFound() throws Exception {
        mvc.perform(get("/api/files/not-a-file")
                        .with(httpBasic("alice", "alice-password")))
                .andExpect(status().isNotFound());
    }
}
```

Lưu file tại `src/test/java/com/example/demo/FileAccessTest.java`. Đây là test JUnit 5 dùng Spring Boot 3.x và security-test; code giả định chưa thêm Security configuration khác. Lệnh:

```bash
# macOS / Linux
./mvnw test

# Windows
mvnw.cmd test
```

Cần thêm các test cho upload/path operations (không có trong demo GET):
- `../`, `..\\`, absolute path, encoded traversal, double-decoding ở tầng phù hợp.
- File ID của tenant khác, share/revoke, deleted/quarantined file.
- Symlink trong storage root (trên filesystem hỗ trợ) và file bị thay giữa check/open.
- Multipart lớn hơn limit, loại file bị cấm, MIME giả, ZIP entry traversal.
- Filename chứa control chars/CRLF/Unicode dài, duplicate storage keys.
- 404/403 consistency, no path leaks trong error body và logs.
- Static URL không bypass controller security.
- Download lớn có streaming/memory profile tốt, không `Files.readAllBytes` toàn file.

### 13.1. Unit test Path startsWith đúng semantics

```java
import java.nio.file.Path;

public class PathBoundaryExample {
    public static void main(String[] args) {
        Path root = Path.of("/srv/files").normalize();
        Path good = root.resolve("a.txt").normalize();
        Path sibling = Path.of("/srv/files-evil/x.txt").normalize();

        System.out.println(good.startsWith(root));
        System.out.println(sibling.startsWith(root));
    }
}
```

Đây là chương trình Java **độc lập** (ví dụ đường dẫn dạng Unix được `Path` hiểu theo hệ điều hành). Trên Linux/macOS với `/` là separator, chạy:

```bash
javac PathBoundaryExample.java
java PathBoundaryExample
```

Stdout:

```text
true
false
```

Không lấy output này làm bằng chứng chống symlink; chương trình chỉ kiểm tra lexical component prefix trên hệ Unix. Trên Windows cần test bằng đường dẫn native tương ứng.

## 14. Những lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| `root + userPath` | Traversal/đọc ngoài root | Dùng opaque ID → metadata → storage key server tạo |
| Chỉ kiểm tra `contains("../")` | Bỏ qua Windows, encoding, symlink | Allowlist storage key + Path checks + OS isolation |
| Dùng `String.startsWith(root)` | Chấp nhận sibling directory như `root-evil` | Dùng `Path.startsWith(root)` |
| Tin `normalize()` là real path | Symlink có thể thoát root | Dùng toRealPath/NOFOLLOW và private storage |
| Check rồi mở file trong shared writable dir | TOCTOU symlink swap | Không cho attacker ghi vào dir; dùng handle-relative API nếu cần |
| Lấy `getOriginalFilename()` để lưu | Traversal, overwrite, tên độc hại | UUID/random server key; displayName tách biệt |
| Dùng `Files.write(...)` ghi đè file | Mất dữ liệu/race | `CREATE_NEW` cho upload mới |
| Kiểm tra đã đăng nhập nhưng không kiểm tra owner | IDOR/BOLA | ACL trên từng record và action |
| Tin `ownerId`, `tenantId` do client gửi | Cross-user/cross-tenant access | Derive identity/tenant từ server auth context |
| File private dưới `static/` | Bypass auth controller | Outside webroot; serve qua authorized endpoint |
| Tin `Content-Type`/extension upload | File giả dạng, XSS, parser exploit | Validate bytes/format, allowlist, scan |
| Dùng `MultipartFile.getBytes()` | Nạp file lớn vào heap | Stream sang storage, enforce quotas |
| `readAllBytes()` khi download | OOM/file lớn | `Resource`/stream, giới hạn tải |
| Trả raw file path trong error | Lộ cấu trúc server | Error body chung và log bảo mật |
| Response file cho phép inline HTML không tin cậy | Stored XSS | Attachment, nosniff, isolated download domain |
| Cache private file công khai | Lộ dữ liệu người dùng | `no-store` hoặc cache partition/permission model |
| Dùng UUID thay authorization | Ai có ID đều có thể truy cập | Check principal + record ACL |
| Upload ZIP kiểm tra tên archive nhưng không entry | Zip Slip | Per-entry safe extraction + quotas |

## 15. Checklist triển khai production

1. Tài nguyên được yêu cầu qua **opaque file ID** hay client có thể tự chọn filesystem path?
2. Storage key có được **server sinh** và không chứa segment do client cung cấp?
3. Directory root có nằm **ngoài webroot**, với OS permissions và container mounts tối thiểu?
4. Đã kiểm tra **authentication + authorization trên từng file, action và tenant** chưa?
5. Với symlink, đã kiểm soát root/parent permissions, `NOFOLLOW_LINKS` và TOCTOU chưa?
6. Upload có `max-file-size`, `max-request-size`, quotas, content validation/scanning và cleanup khi lỗi?
7. File upload được tạo bằng thao tác không ghi đè có tính atomic như `CREATE_NEW`?
8. Download có status/header đúng, không lộ storage path, không nạp toàn file vào heap?
9. Content-Disposition filename có được sanitize/encode; có `nosniff` và chính sách cache?
10. File private có thể bị truy cập trực tiếp qua static handler hoặc CDN URL không?
11. Quá trình xóa/đổi quyền có làm invalid signed URLs/cached copies đúng hợp đồng không?
12. Có kiểm thử người dùng A/B, traversal, encoded input, symlink, parser, concurrency, large files và audit logs?

## 16. Tự kiểm tra

**Câu 1.** `Path.resolve(input).normalize().startsWith(root)` đã chặn hoàn toàn traversal chưa?

**Đáp án:** Chưa. Nó phát hiện nhiều lexical escapes nhưng không đảm bảo đường dẫn thực khi có symbolic links và có thể còn TOCTOU. Bảo vệ tốt nhất là không dùng client path, dùng root private và server-generated key.

**Câu 2.** Nếu Alice biết UUID file của Bob nhưng cả hai đều đã đăng nhập, Alice được tải file Bob không?

**Đáp án:** Không. UUID không phải quyền; service phải kiểm tra object-level authorization, owner/tenant/ACL.

**Câu 3.** Có được dùng `MultipartFile.getOriginalFilename()` làm tên file trên server không?

**Đáp án:** Không nên. Đây là dữ liệu client, có thể chứa path/`..`; chỉ dùng làm display metadata đã xử lý, còn storage key do server tạo.

**Câu 4.** Tại sao `CREATE_NEW` tốt hơn “check file chưa tồn tại rồi write”?

**Đáp án:** `CREATE_NEW` dùng thao tác atomic tạo mới, tránh race giữa check và write và không ghi đè file/symlink đã có.

**Câu 5.** Trả 403 hay 404 khi một file thuộc tenant khác?

**Đáp án:** Tùy chính sách tiết lộ sự tồn tại tài nguyên. 403 biểu thị không được phép; 404 có thể giảm enumeration. Cả hai phải không cấp dữ liệu trái quyền.

**Câu 6.** `Content-Disposition: attachment` và `X-Content-Type-Options: nosniff` đã đủ để upload mọi loại file chưa?

**Đáp án:** Không. Cần giới hạn loại/size, quét hoặc xử lý an toàn, storage isolation và authorization; header chỉ giảm một số rủi ro phía client.

**Câu 7.** Vì sao tệp nằm trong thư mục cho phép vẫn có thể bị truy cập trái quyền?

**Đáp án:** Vì path containment khác **quyền trên đối tượng**. Tệp của người khác vẫn có thể cùng root; phải kiểm tra ACL/owner/tenant.

## 17. Tài liệu chính thức

- [Spring Framework 6.2 — MultipartFile Javadoc](https://docs.spring.io/spring-framework/docs/6.2.9/javadoc-api/org/springframework/web/multipart/MultipartFile.html): `getOriginalFilename()` là giá trị client, không dùng mù quáng để lưu.
- [Spring Framework — Multipart Files](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html): cách nhận multipart request trong MVC.
- [Spring Boot 3.5 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): static resource handlers và web configuration.
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/): multipart size limits và container settings.
- [Java 17 — Path](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Path.html): `normalize`, `startsWith`, `toRealPath`.
- [Java 17 — Files](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html): `CREATE_NEW`, input/output streams, file attributes.
- [Java 17 — LinkOption](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/LinkOption.html): `NOFOLLOW_LINKS`.
- [OWASP Path Traversal](https://owasp.org/www-community/attacks/Path_Traversal): rủi ro/biện pháp giảm thiểu traversal.
- [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html): allowlist, content validation, storage isolation, access controls và file quotas.
- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): least privilege, quyền cấp tài nguyên và kiểm tra trên từng request.

**Kết luận:** Spring MVC không tự bảo đảm file an toàn chỉ vì controller nhận `@PathVariable`, `MultipartFile` hoặc trả `Resource`. Thiết kế đúng là **ID tài nguyên → metadata do server quản lý → authentication và object authorization → storage key do server sinh → private root có OS permissions → thao tác file an toàn và streaming**. `normalize()`/`toRealPath()`/`NOFOLLOW_LINKS` là những lớp kỹ thuật bổ sung, không thay cho việc loại bỏ client-controlled path, ngăn TOCTOU và kiểm soát quyền người dùng.


</details>

- [Quay lại đầu trang](#back-to-top)
