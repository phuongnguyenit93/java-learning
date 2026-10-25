<a id="back-to-top"></a>

# Tổng quan web

## Menu
- [Vai trò của Spring Web trong ứng dụng Java và các loại ứng dụng web](#web-overview-001)
- [Phân biệt Spring Framework, Spring MVC, Spring WebFlux và Spring Boot](#web-overview-002)
- [Các mô-đun spring-web, spring-webmvc, spring-webflux và spring-websocket](#web-overview-003)
- [Kiến thức cần có: Java, IoC/DI, HTTP và xử lý đồng thời](#web-overview-004)
- [Chọn Servlet/MVC hoặc WebFlux theo chuỗi I/O và nhu cầu ứng dụng](#web-overview-005)
- [Phiên bản Spring, Jakarta Servlet và khả năng tương thích của API](#web-overview-006)

## <a id="web-overview-001">Vai trò của Spring Web trong ứng dụng Java và các loại ứng dụng web</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của Spring Web trong ứng dụng Java và các loại ứng dụng web

## 1. Spring Web giải quyết bài toán gì?

Một ứng dụng Java thông thường có thể chạy theo dòng lệnh, thực hiện tác vụ theo lịch hoặc xử lý dữ liệu mà không nhận yêu cầu HTTP. Khi muốn cung cấp website hoặc dịch vụ qua mạng, ứng dụng cần tiếp nhận HTTP request, xác định chức năng phải chạy, xử lý dữ liệu và gửi HTTP response cho trình duyệt, ứng dụng di động hoặc hệ thống khác.

**Spring Web** là nhóm thành phần của Spring Framework giúp xây dựng tầng giao tiếp web trên nền tảng Java. Thay vì tự đọc HTTP request và tự điều phối từng Servlet cho mọi URL, lập trình viên có thể khai báo điểm nhận request, tham số, dữ liệu đầu vào, kết quả và quy tắc xử lý lỗi bằng các thành phần Spring.

Ví dụ yêu cầu:

```http
GET /api/books/42 HTTP/1.1
Host: example.com
Accept: application/json
```

Ứng dụng có thể trả về:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":42,"title":"Java cơ bản"}
```

Đây là ví dụ minh họa giao thức: Spring giúp ánh xạ `GET /api/books/42` tới mã xử lý, chuyển `42` thành tham số, rồi chuyển đối tượng kết quả thành JSON khi cấu hình thư viện chuyển đổi phù hợp.

Cần phân biệt ba tên thường gặp:

- **Spring Framework** là nền tảng có IoC/DI và nhiều module, trong đó có các module web.
- **Spring Web / Spring MVC / Spring WebFlux** là các thành phần phục vụ xử lý web của Spring Framework. `spring-web` cung cấp nhiều nền tảng HTTP chung; `spring-webmvc` triển khai mô hình MVC trên Servlet; `spring-webflux` triển khai stack web reactive.
- **Spring Boot** hỗ trợ cấu hình tự động, quản lý dependency và khởi động ứng dụng Spring nhanh hơn. Boot thường được dùng cùng Spring Web, nhưng Boot và Spring Web không đồng nghĩa.

## 2. Vai trò chính của Spring Web

### 2.1. Đưa logic Java ra giao diện HTTP

Spring định tuyến request theo đường dẫn và phương thức HTTP, ví dụ `GET /products` để đọc danh sách hoặc `POST /products` để tạo mới. Controller đóng vai trò tiếp nhận yêu cầu và chuyển việc xử lý nghiệp vụ cho service, thay vì ôm toàn bộ logic trong phương thức nhận HTTP.

Các annotation thường gặp trong Spring MVC gồm `@Controller`, `@RestController`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`. Chúng khai báo giao ước nhận request và ràng buộc dữ liệu, không tự thay thế việc kiểm tra nghiệp vụ, xác thực hay quản lý giao dịch.

### 2.2. Chuyển đổi dữ liệu giữa HTTP và đối tượng Java

Ứng dụng có thể nhận dữ liệu từ path, query string, header, cookie, form hoặc request body; sau đó liên kết dữ liệu với tham số hoặc đối tượng Java. Với REST API, Spring thường dùng HTTP message converters/codecs để đọc và ghi JSON. Việc chuyển thành JSON thường cần thư viện serialization thích hợp, vốn được các starter tương ứng hỗ trợ cấu hình.

Ví dụ `GET /api/books?limit=10` chứa query parameter `limit`; trong khi `POST /api/books` có thể chứa JSON ở request body. Hai nơi chứa dữ liệu này khác nhau và cần được xử lý đúng cách.

### 2.3. Tổ chức lớp web và tích hợp hệ sinh thái

Tầng web có thể kết hợp với Spring Validation để kiểm tra dữ liệu, Spring Security để xác thực/phân quyền, tầng service để xử lý nghiệp vụ, và repository để làm việc với cơ sở dữ liệu. Spring cũng có cơ chế xử lý ngoại lệ, cấu hình CORS, nội dung phản hồi, upload file, điều hướng trang và các khả năng web khác.

Một cách phân chia trách nhiệm dễ bảo trì:

```text
Client (trình duyệt / mobile / dịch vụ khác)
                 |
              HTTP(S)
                 v
Web server / servlet container / runtime HTTP
                 |
    Spring Web: routing + binding + response
                 |
              Controller
                 |
               Service
                 |
       Repository / hệ thống ngoài
                 |
            Dữ liệu/kết quả
```

Sơ đồ là cách nhìn logic, không phải quy định mọi dự án đều phải có đủ lớp hoặc một lần gọi phải đi qua repository. Security filter, interceptor, exception handler và các thành phần hạ tầng cũng có thể tham gia luồng xử lý.

## 3. Một HTTP request đi qua ứng dụng Spring MVC như thế nào?

Với stack Servlet/Spring MVC, luồng phổ biến là:

1. Trình duyệt hoặc client gửi request đến server. Servlet container, chẳng hạn Tomcat, nhận kết nối và chuyển request đến ứng dụng.
2. Các Servlet filter phù hợp được áp dụng; đây có thể là nơi xử lý một phần vấn đề liên quan đến bảo mật hoặc thông tin request.
3. `DispatcherServlet` của Spring MVC đóng vai trò bộ điều phối trung tâm, tìm handler/controller phù hợp nhờ hệ thống handler mapping.
4. Spring liên kết tham số, thực thi các bước cần thiết như chuyển đổi kiểu và validation nếu đã khai báo, rồi gọi phương thức controller.
5. Controller gọi service, xử lý kết quả hoặc lỗi; Spring tạo response bằng cách ghi body (ví dụ JSON) hoặc giải quyết tên view để render HTML.
6. Container trả HTTP status, header và body về client. Nếu có lỗi, exception resolver hoặc cơ chế xử lý lỗi có thể tạo response tương ứng.

`DispatcherServlet` là khái niệm trọng tâm của Spring MVC. Trong **Spring WebFlux**, bộ điều phối trung tâm của stack reactive thường là `DispatcherHandler`, và cách xử lý I/O khác với mô hình Servlet truyền thống.

HTTP có các trạng thái giàu ý nghĩa: `200 OK` khi đọc thành công, `201 Created` khi tạo tài nguyên, `400 Bad Request` khi yêu cầu sai, `401 Unauthorized` khi chưa có thông tin xác thực hợp lệ, `403 Forbidden` khi không đủ quyền, `404 Not Found` khi tài nguyên không tồn tại, và `500 Internal Server Error` cho lỗi phía server. Mã trạng thái thực tế phải phản ánh tình huống; Spring không tự chọn đúng mọi mã cho mọi nghiệp vụ nếu không thiết kế xử lý phù hợp.

## 4. Các loại ứng dụng web thường gặp

Có thể phân loại theo **cách tạo giao diện**, **cách client giao tiếp**, hoặc **cách triển khai hệ thống**. Các nhóm dưới đây là những chiều phân loại khác nhau, vì thế một sản phẩm có thể thuộc nhiều nhóm đồng thời.

### 4.1. Website render HTML ở server (server-side rendering)

Server nhận request, lấy dữ liệu và render HTML trước khi trả về trình duyệt. Trong Spring MVC, `@Controller` có thể trả về tên view, kết hợp một template engine như Thymeleaf.

```text
Browser -- GET /catalog --> Spring MVC Controller
        <-- HTML đã render -- Template + dữ liệu
```

Phù hợp với website nội dung, trang quản trị, biểu mẫu và sản phẩm muốn render giao diện phía server. Ưu điểm là luồng render tập trung, trang có nội dung HTML ngay trong response; đổi lại mỗi bước điều hướng/submit có thể kéo theo một request và render lại toàn bộ hoặc một phần trang, tùy thiết kế.

### 4.2. Backend REST API trả dữ liệu cho client

Server chủ yếu trả JSON (hoặc một định dạng dữ liệu khác), không nhất thiết tạo HTML. Mobile app, frontend JavaScript, hệ thống đối tác hoặc một dịch vụ khác sẽ tiêu thụ API. Spring MVC thường dùng `@RestController` cho dạng này.

```text
Mobile / SPA / Service -- HTTP + JSON --> Spring API
Mobile / SPA / Service <-- status + JSON -- Spring API
```

Ví dụ hệ thống quản lý đơn hàng có endpoint đọc đơn, tạo đơn, thay đổi trạng thái. Điểm quan trọng là thiết kế tài nguyên, phương thức HTTP, mã trạng thái, xác thực, validation và hợp đồng dữ liệu rõ ràng. **REST** là phong cách thiết kế API; sử dụng `@RestController` không tự bảo đảm toàn bộ API tuân thủ các ràng buộc REST.

### 4.3. Ứng dụng một trang (SPA) có backend Spring

SPA (ví dụ frontend dùng React, Vue hoặc Angular) tải phần lớn giao diện JavaScript ở trình duyệt, sau đó gọi API để lấy/cập nhật dữ liệu. Spring Web có thể đảm nhiệm backend API; frontend có thể được phục vụ cùng origin hoặc triển khai riêng qua CDN/web server.

Với frontend và backend khác origin, cần hiểu cấu hình CORS. CORS là cơ chế trình duyệt kiểm soát việc script đọc phản hồi cross-origin, **không phải** cơ chế xác thực hoặc phân quyền. Authentication/authorization vẫn phải thực hiện tại backend.

### 4.4. Web app có tương tác thời gian thực

Khi client cần nhận cập nhật liên tục như thông báo, chat, giá thay đổi hoặc tiến độ tác vụ, ứng dụng có thể dùng:

- **WebSocket** cho kết nối hai chiều lâu dài khi cả client và server cần chủ động gửi thông điệp.
- **Server-Sent Events (SSE)** cho luồng sự kiện chủ yếu một chiều từ server đến trình duyệt qua HTTP.
- **Polling/long polling** khi client chủ động kiểm tra cập nhật định kỳ hoặc giữ request chờ dữ liệu.

Spring hỗ trợ xây dựng những kiểu giao tiếp này, nhưng lựa chọn giao thức cần dựa trên tính hai chiều, hạ tầng, số lượng kết nối, cơ chế reconnect và yêu cầu bảo mật. Không phải chức năng “thời gian thực” nào cũng cần WebSocket.

### 4.5. Ứng dụng monolith và hệ thống microservices

Đây là cách chia ranh giới triển khai, không phải cách biểu diễn giao diện:

- **Monolith**: một đơn vị triển khai có thể chứa UI server-side, API, service và truy cập dữ liệu. Spring Web xử lý điểm vào HTTP của ứng dụng đó.
- **Microservices**: nhiều dịch vụ triển khai độc lập, mỗi dịch vụ có thể có Spring Web API riêng và liên lạc qua HTTP hoặc messaging. Hệ thống cần quan tâm timeout, xác thực giữa dịch vụ, observability, lỗi mạng và nhất quán dữ liệu.
- **Modular monolith**: vẫn triển khai thành một ứng dụng, nhưng chia module nghiệp vụ rõ ràng để giảm phụ thuộc giữa các phần.

Dùng Spring Web **không bắt buộc** chọn microservices; một ứng dụng monolith có cấu trúc tốt thường là điểm khởi đầu hợp lý khi chưa có yêu cầu phân tách triển khai.

### 4.6. Website tĩnh

Website chỉ gồm HTML/CSS/JS được tạo sẵn và không cần xử lý nghiệp vụ Java ở server có thể được phục vụ trực tiếp từ CDN hoặc web server. Không nhất thiết cần Spring Web. Khi website tĩnh gọi backend API riêng, Spring Web có thể chỉ xuất hiện ở phía backend.

### Bảng đối chiếu nhanh

| Dạng ứng dụng | Phản hồi/giao tiếp chính | Spring thường đảm nhiệm | Ví dụ |
|---|---|---|---|
| Website server-side | HTML render từ server | MVC controller + view resolver/template | Trang quản trị |
| Backend API | HTTP status + JSON | REST controller, binding, serialization | API đơn hàng |
| SPA + backend | JavaScript UI gọi HTTP API | Backend REST, security, CORS khi cần | Dashboard React |
| Real-time | WebSocket, SSE hoặc polling | Endpoint và hạ tầng sự kiện | Chat, thông báo |
| Microservices | API giữa nhiều dịch vụ | HTTP endpoint ở từng service | Dịch vụ thanh toán |
| Website tĩnh | Tệp HTML/CSS/JS | Không bắt buộc sử dụng Spring | Landing page tĩnh |

Một hệ thống có thể vừa là “SPA + REST API” ở góc nhìn giao diện, vừa là “modular monolith” ở góc nhìn triển khai, và có thêm “SSE” cho thông báo. Vì vậy không nên coi các hàng trong bảng là những lựa chọn loại trừ nhau.

## 5. Spring MVC và Spring WebFlux: hai stack web chính

Spring cung cấp hai mô hình xử lý web nổi bật. Cả hai có thể cung cấp HTTP endpoint, controller và xử lý request/response; sự khác biệt đáng chú ý nằm ở mô hình thực thi và tương tác I/O.

| Tiêu chí | Spring MVC | Spring WebFlux |
|---|---|---|
| Nền tảng điển hình | Servlet API, servlet container | Reactive Streams, runtime non-blocking |
| Bộ điều phối | `DispatcherServlet` | `DispatcherHandler` |
| Phong cách phổ biến | Controller trả đối tượng/giá trị thông thường; hỗ trợ async khi cần | Controller hoặc functional endpoint, thường dùng `Mono`/`Flux` |
| I/O chặn luồng | Phù hợp với nhiều thư viện blocking truyền thống | Cần tránh blocking trên luồng xử lý sự kiện; tối ưu khi toàn bộ đường I/O hỗ trợ non-blocking |
| Use case thường thấy | CRUD, HTML server-side, REST với JPA/JDBC | Nhiều I/O đồng thời, streaming, tương tác với downstream reactive |
| Đánh đổi | Mô hình dễ tiếp cận và hệ sinh thái Servlet lâu đời | Cần hiểu reactive programming, backpressure, scheduler và boundary blocking |

**Không có quy tắc “WebFlux luôn nhanh hơn MVC”.** Kết quả phụ thuộc tải, thời gian đợi I/O, nguồn lực hệ thống và toàn bộ dependency. Một WebFlux controller gọi trực tiếp JDBC hoặc thư viện blocking trên event-loop vẫn có thể gây nghẽn. Ngược lại, MVC có hỗ trợ xử lý bất đồng bộ và có thể đáp ứng tốt khối lượng lớn ứng dụng thông thường.

Một số ứng dụng còn có thể sử dụng Spring MVC với virtual threads tùy JDK, servlet container và cấu hình. Virtual threads và WebFlux là hai cách tiếp cận concurrency khác nhau; không nên suy ra một ứng dụng dùng Java mới nhất thì tự động trở thành reactive.

**Gợi ý chọn stack:** Nếu hệ thống là website hoặc REST CRUD dùng JPA/JDBC và các thư viện blocking, Spring MVC thường dễ bắt đầu và vận hành. Nếu luồng xử lý chủ yếu non-blocking, cần streaming và có đội ngũ hiểu reactive, cân nhắc Spring WebFlux sau khi đánh giá nhu cầu thực tế.

## 6. Ví dụ Spring MVC REST endpoint nhỏ

Ví dụ này dành cho **dự án Spring Boot đã có dependency web MVC** và JDK phù hợp với phiên bản Spring Boot đang dùng. Đây là một lớp Java hoàn chỉnh *trong dự án Spring Boot*, không phải chương trình độc lập có thể chạy chỉ bằng `javac DemoApplication.java`: cần dependency Spring và tác vụ chạy của hệ thống build.

Tạo tệp `src/main/java/com/example/demo/DemoApplication.java`:

```java
package com.example.demo;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@RestController
class GreetingController {
    @GetMapping("/api/hello")
    public Map<String, String> hello(
            @RequestParam(defaultValue = "bạn") String name) {
        return Map.of("message", "Xin chào, " + name);
    }
}
```

Chạy ứng dụng bằng tác vụ tương ứng với dự án, chẳng hạn `./gradlew bootRun` (trên Windows có thể dùng `gradlew.bat bootRun`) nếu dự án có Spring Boot Gradle plugin và cấu hình web phù hợp. Khi ứng dụng đang lắng nghe tại cổng mặc định 8080, gửi:

```bash
curl -s "http://localhost:8080/api/hello?name=Lan"
```

Response body dự kiến (khi có bộ chuyển JSON được cấu hình, như thiết lập web MVC phổ biến):

```json
{"message":"Xin chào, Lan"}
```

Giải thích từng phần:

1. `@SpringBootApplication` đánh dấu entry point và bật cơ chế cấu hình phù hợp của Spring Boot.
2. `@RestController` cho biết giá trị trả về của phương thức handler được ghi vào HTTP response body thay vì hiểu như tên view.
3. `@GetMapping("/api/hello")` đăng ký handler cho HTTP GET ở đường dẫn đó.
4. `@RequestParam` đọc tham số `name` từ query string; khi vắng mặt, giá trị mặc định là `bạn`.
5. `Map.of` tạo dữ liệu Java; cơ chế HTTP message conversion chuyển map thành JSON để trả cho client. Phần nhập/xuất HTTP do Spring và runtime web thực hiện.

Nếu client truy cập `/api/hello` không có `name`, phản hồi sẽ mang thông điệp `Xin chào, bạn`. Ví dụ nhỏ này chỉ trình diễn định tuyến và serialization; trong ứng dụng thật, controller thường gọi service, thực hiện validation, xử lý lỗi và bảo vệ endpoint theo yêu cầu.

### Khi muốn trả HTML thay vì JSON

Trong dự án Spring MVC đã cấu hình template engine và có template phù hợp, controller thường có dạng:

```java
@Controller
class HomeController {
    @GetMapping("/home")
    String home(Model model) {
        model.addAttribute("title", "Trang chủ");
        return "home";
    }
}
```

Đây là **đoạn minh họa**, chưa chạy độc lập vì đã lược bỏ các `import`, cấu hình template engine và tệp template `home`. Khác với `@RestController`, giá trị `"home"` của một `@Controller` thông thường ở đây được hiểu là tên view để view resolver xử lý, thay vì body JSON.

## 7. Chọn loại ứng dụng và công nghệ cho từng yêu cầu

Bắt đầu từ **người tiêu thụ kết quả** và **kiểu tương tác**:

1. Nếu người dùng cần một website HTML render tại server, cân nhắc Spring MVC với template engine.
2. Nếu client là ứng dụng di động, SPA hoặc hệ thống khác, thiết kế HTTP API rõ ràng; Spring MVC hoặc WebFlux đều có thể phục vụ API.
3. Nếu cần cập nhật do server đẩy đến client, đánh giá SSE/WebSocket theo chiều giao tiếp và đặc tính tải.
4. Nếu nghiệp vụ chủ yếu dùng JDBC/JPA hoặc thư viện blocking, ưu tiên sự nhất quán với mô hình thực thi blocking; chỉ chuyển sang reactive khi có lý do cụ thể.
5. Chọn monolith, modular monolith hay microservices theo nhu cầu triển khai, quy mô nhóm và ranh giới nghiệp vụ; lựa chọn đó độc lập tương đối với chuyện render HTML hay trả JSON.

Ví dụ một website đặt vé có thể bắt đầu bằng một ứng dụng Spring MVC render HTML và kết nối cơ sở dữ liệu qua JPA. Khi bổ sung ứng dụng di động, cùng hệ thống có thể cung cấp REST API. Khi cần trạng thái chỗ ngồi cập nhật liên tục, có thể thêm SSE hoặc WebSocket. Các khả năng được mở rộng theo yêu cầu, không cần đổi toàn bộ ứng dụng thành microservices chỉ vì xuất hiện nhiều loại client.

## 8. Những hiểu nhầm và lỗi thiết kế phổ biến

- **“Spring Web là web server.”** Spring Web là framework tầng ứng dụng; web server/Servlet container hoặc runtime HTTP đảm nhiệm việc nhận kết nối mạng. Spring Boot có thể khởi chạy embedded server theo dependency và cấu hình, nhưng chúng là những vai trò khác nhau.
- **“Cứ dùng `@RestController` là API đạt chuẩn REST.”** Annotation chỉ cung cấp cách xử lý và ghi response; thiết kế resource, semantics của HTTP, status và hợp đồng API vẫn cần chủ động xây dựng.
- **“Controller nên chứa mọi logic.”** Nhồi validation nghiệp vụ, SQL và gọi hệ thống ngoài trực tiếp vào controller làm ứng dụng khó kiểm thử và bảo trì. Nên giữ controller mỏng, tách trách nhiệm vào service và các lớp phù hợp.
- **“CORS bảo vệ API.”** CORS chủ yếu là ràng buộc truy cập của JavaScript trong trình duyệt; API vẫn cần authentication, authorization, kiểm tra dữ liệu và các biện pháp bảo mật thực sự.
- **“Reactive nghĩa là mọi hàm chạy song song hoặc luôn nhanh.”** Reactive mô tả cách kết hợp và xử lý luồng bất đồng bộ, non-blocking; hiệu quả thực tế còn phụ thuộc điểm chặn luồng và tài nguyên.
- **“Một SPA bắt buộc cần Spring phục vụ file frontend.”** Frontend có thể được host riêng; Spring Web có thể chỉ cung cấp backend.
- **“Mỗi service phải triển khai dưới dạng microservice.”** Ranh giới code và ranh giới triển khai không giống nhau. Có thể tổ chức module nghiệp vụ rõ ràng trong cùng một ứng dụng.

## 9. Tự kiểm tra mức hiểu

**Tình huống 1:** Một ứng dụng iOS cần lấy danh sách sản phẩm dạng JSON. Spring Web đóng vai trò gì?  
**Giải đáp:** Cung cấp HTTP endpoint, ánh xạ request, gọi nghiệp vụ, chuyển đối tượng thành JSON và trả HTTP response. Spring không tạo giao diện iOS.

**Tình huống 2:** Website dùng Thymeleaf render trang tại server có cần REST API cho mọi màn hình không?  
**Giải đáp:** Không. Controller có thể trả view HTML trực tiếp; REST API chỉ cần khi có giao tiếp dữ liệu riêng hoặc client khác.

**Tình huống 3:** Ứng dụng Spring MVC dùng JPA nhưng cần thông báo tiến độ theo thời gian thực. Có nhất thiết viết lại toàn bộ bằng WebFlux không?  
**Giải đáp:** Không. Có thể đánh giá SSE, WebSocket hoặc polling trên kiến trúc hiện tại; cần thiết kế tài nguyên, timeout, reconnect và xử lý đồng thời phù hợp.

**Điểm cần nhớ:** Spring Web là lớp kết nối giữa giao thức web và logic ứng dụng Java. Loại ứng dụng được xác định bởi cách client tiêu thụ HTML/dữ liệu/sự kiện, còn Spring MVC hay WebFlux là lựa chọn stack xử lý web. Thiết kế monolith/microservices là chiều kiến trúc triển khai khác và không bị quyết định bởi việc dùng Spring Web.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-overview-002">Phân biệt Spring Framework, Spring MVC, Spring WebFlux và Spring Boot</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt Spring Framework, Spring MVC, Spring WebFlux và Spring Boot

Khi xây dựng ứng dụng Java, bốn tên gọi này thường xuất hiện cùng nhau nhưng chúng **không nằm ở bốn lựa chọn ngang hàng**. Spring Framework là nền tảng; Spring MVC và Spring WebFlux là hai mô hình xây dựng ứng dụng web thuộc Spring Framework; Spring Boot giúp cấu hình, khởi động, đóng gói và vận hành ứng dụng dùng các thành phần ấy.

Hiểu đúng mối quan hệ này giúp chọn công nghệ, đọc dependency, tìm lỗi cấu hình và tránh nhầm giữa cách xử lý HTTP với cách khởi động ứng dụng.

## 1. Bản đồ khái niệm: cái nào chứa cái nào?

~~~text
                          Ứng dụng Java
                                |
                         Spring Framework
                    (IoC/DI, bean, AOP, dữ liệu,
                     transaction, validation, ...)
                                |
                     +----------+-----------+
                     |                      |
               Spring MVC              Spring WebFlux
             (Servlet stack)          (Reactive stack)
                     |                      |
                HTTP API / UI          HTTP API / stream
                     \                      /
                      \                    /
                       Spring Boot (tùy chọn)
              auto-configuration, starter, chạy server,
                  cấu hình ngoài, đóng gói, Actuator
~~~

Sơ đồ trên thể hiện **quan hệ chức năng**, không phải quan hệ kế thừa: Spring Boot không phải lớp nằm bên dưới Spring MVC/WebFlux về mặt mã nguồn. Boot đứng ở lớp **thiết lập và vận hành**, có thể sử dụng MVC, WebFlux, các thư viện Spring khác hoặc không dùng HTTP.

Có thể hình dung:

- **Spring Framework:** bộ nền tảng để ứng dụng quản lý đối tượng và triển khai các mối quan tâm dùng chung.
- **Spring MVC:** cách Spring nhận và xử lý HTTP dựa trên Servlet API.
- **Spring WebFlux:** cách Spring nhận và xử lý HTTP theo mô hình reactive, hỗ trợ I/O không chặn và Reactive Streams.
- **Spring Boot:** cách khởi tạo một ứng dụng Spring ít cấu hình thủ công hơn, chọn cấu hình theo classpath và điều kiện.

Một ứng dụng phổ biến là **Spring Boot + Spring MVC + Spring Framework**. Một ứng dụng khác là **Spring Boot + Spring WebFlux + Spring Framework**. Spring Framework hoặc Spring MVC vẫn có thể dùng **không cần Spring Boot**.

## 2. Bảng so sánh theo trách nhiệm

| Khía cạnh | Spring Framework | Spring MVC | Spring WebFlux | Spring Boot |
|---|---|---|---|---|
| Bản chất | Nền tảng/libraries cho ứng dụng Java | Framework web theo Servlet | Framework web reactive | Công cụ cấu hình và chạy ứng dụng Spring |
| Câu hỏi giải quyết | Bean được tạo, nối và quản lý thế nào? | HTTP request được ánh xạ và xử lý theo Servlet thế nào? | HTTP và luồng dữ liệu bất đồng bộ được xử lý không chặn thế nào? | Làm sao dựng, cấu hình và chạy ứng dụng thuận tiện? |
| Thành phần tiêu biểu | IoC container, DI, AOP, transaction, events | DispatcherServlet, HandlerMapping, HandlerAdapter, HttpMessageConverter | DispatcherHandler, WebHandler, WebFilter, codecs, Reactor | SpringApplication, auto-configuration, starters, embedded server, Actuator |
| Module/thư viện thường thấy | spring-context, spring-beans, spring-core | spring-webmvc (cùng spring-web) | spring-webflux, Reactor | spring-boot, spring-boot-autoconfigure, starters |
| Mô hình xử lý request | Không quy định một mô hình web | Servlet; thường mỗi request sử dụng thread xử lý theo mô hình đồng bộ | Reactive; tác vụ I/O có thể không chặn, event-loop phù hợp | Phụ thuộc web stack được chọn |
| Kiểu controller điển hình | Không bắt buộc có controller HTTP | @Controller, @RestController, @RequestMapping | Cùng annotation controller hoặc RouterFunction/HandlerFunction | Dùng controller của MVC hoặc WebFlux, không định nghĩa thêm một mô hình controller độc lập |
| Chạy trực tiếp web server? | Bản thân container không mặc định mở HTTP server | Cần servlet container và cấu hình triển khai | Cần runtime HTTP/server và cấu hình triển khai | Có thể cấu hình và chạy embedded server tự động |
| Phù hợp | Hầu hết ứng dụng Spring, kể cả ứng dụng không web | REST CRUD, MVC view, JPA/JDBC, thư viện blocking | Streaming, nhiều kết nối I/O chờ đợi, chuỗi non-blocking end-to-end | Hầu hết ứng dụng Spring muốn khởi tạo/triển khai thuận tiện |

**Hai trục quyết định độc lập:**

1. **Có dùng Spring Boot không?** Đây là lựa chọn cách cấu hình, khởi động và vận hành.
2. **Nếu làm web, dùng Spring MVC hay WebFlux?** Đây là lựa chọn mô hình xử lý HTTP.

Không nên đặt câu hỏi “Spring MVC hay Spring Boot?” như hai lựa chọn thay thế trực tiếp.

## 3. Spring Framework: nền tảng chung

Spring Framework cung cấp **IoC container** quản lý các bean. Với **Dependency Injection (DI)**, mã nghiệp vụ khai báo dependency cần dùng, container tạo/tiêm các bean theo cấu hình thay vì từng nơi tự khởi tạo và nối đối tượng. Điều này giúp giảm phụ thuộc trực tiếp, tổ chức cấu hình tập trung và thuận tiện cho kiểm thử.

Ví dụ thành phần ứng dụng dùng chung cho MVC, WebFlux và thậm chí ứng dụng console:

~~~java
package example.core;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    public String greet(String name) {
        return "Xin chào, " + name;
    }
}
~~~

**Ý nghĩa:** @Service đánh dấu lớp để được đăng ký dưới dạng bean nếu nằm trong phạm vi component scan thích hợp. Spring không tự biến GreetingService thành HTTP endpoint; muốn nhận HTTP phải có thêm cấu hình và web stack, chẳng hạn một controller MVC hoặc WebFlux.

Một số khả năng khác của Spring Framework:

- **@Configuration, @Bean, @Component, @Autowired:** đăng ký/cấu hình và kết nối bean (thực tế thường ưu tiên constructor injection).
- **AOP, transaction:** tách logic xuyên suốt như transaction khỏi mã nghiệp vụ.
- **ApplicationContext, event, resource, conversion, validation:** hỗ trợ vòng đời và hạ tầng ứng dụng.
- **Tích hợp dữ liệu và kiểm thử:** lớp trừu tượng, công cụ kết nối nhiều công nghệ; từng tính năng cần dependency/cấu hình tương ứng.

Spring Framework **không đồng nghĩa** với Spring MVC: ứng dụng chạy batch job, ứng dụng command-line hoặc một thư viện dùng Spring context vẫn là ứng dụng Spring dù không có controller HTTP.

### Spring Framework có cần Spring Boot?

Không. Có thể tự tạo ApplicationContext, đăng ký bean, cấu hình Spring MVC trên servlet container hoặc cấu hình WebFlux server. Tuy nhiên cần chủ động quản lý thư viện, cấu hình cơ sở, tích hợp server và triển khai. Spring Boot phổ biến vì tự động hóa phần lớn công việc lặp lại này.

## 4. Spring MVC: web stack dựa trên Servlet

Spring MVC (Spring Web MVC, module **spring-webmvc**) xây dựng trên **Servlet API**. Điểm vào trung tâm là **DispatcherServlet**, một front controller nhận request rồi phối hợp các thành phần để chọn handler, gắn tham số, chạy controller và tạo HTTP response.

Luồng request đơn giản hóa:

~~~text
Client gửi HTTP
    -> Servlet container (thường Tomcat hoặc Jetty)
    -> Servlet Filter chain
    -> DispatcherServlet
    -> HandlerMapping: tìm controller method
    -> HandlerAdapter: gọi method, binding/validation
    -> Controller -> Service -> Repository
    -> Return value / exception resolution
    -> HttpMessageConverter (JSON, text...) hoặc ViewResolver (HTML view)
    -> HTTP response
~~~

Các chức năng quen thuộc:

- **@GetMapping, @PostMapping, @PathVariable, @RequestParam, @RequestBody:** ánh xạ HTTP và dữ liệu vào tham số.
- **@RestController:** kết hợp @Controller và @ResponseBody; kết quả thường chuyển thành JSON qua message converter khi là object.
- **@Controller:** có thể trả về tên view cùng model để render HTML ở server.
- **@ExceptionHandler, @ControllerAdvice:** xử lý lỗi tập trung.
- **@Valid, conversion, data binding:** xác thực và chuyển đổi dữ liệu khi được cấu hình đúng.
- **Servlet Filter, HandlerInterceptor:** xử lý yêu cầu xuyên suốt theo tầng tương ứng.

MVC hay dùng mô hình **thread-per-request** đối với xử lý đồng bộ: khi code chờ JDBC hoặc HTTP client blocking, thread đang xử lý bị giữ trong lúc chờ. Mô hình này rõ ràng và kết hợp tự nhiên với hệ sinh thái Java blocking, đặc biệt **JPA/Hibernate, JDBC**.

**Không được hiểu MVC là “chỉ có đồng bộ”.** Spring MVC hỗ trợ xử lý bất đồng bộ qua Callable, DeferredResult, CompletableFuture và một số reactive return types. Servlet async có thể giải phóng request thread trong lúc chờ và dispatch tiếp khi có kết quả. Tuy nhiên, hỗ trợ async đó **không tự chuyển toàn bộ tầng Servlet/MVC sang WebFlux non-blocking I/O**.

## 5. Spring WebFlux: web stack reactive

Spring WebFlux (module **spring-webflux**) xuất hiện từ Spring Framework 5 và xây dựng API web theo hợp đồng reactive, hỗ trợ **I/O không chặn** và **Reactive Streams backpressure**. Bộ điều phối tương ứng là **DispatcherHandler**. Ứng dụng WebFlux có thể chạy trên Reactor Netty hoặc server khác được hỗ trợ, trong đó có servlet container qua lớp adapter thích hợp; WebFlux **không bắt buộc chạy trên Netty**.

Hai kiểu khai báo endpoint:

1. **Annotated controller:** dùng @RestController/@GetMapping tương tự cú pháp MVC, nhưng method có thể trả Mono<T>, Flux<T>.
2. **Functional endpoints:** RouterFunction điều hướng request tới HandlerFunction, ví dụ ServerRequest và ServerResponse.

### Mono và Flux nói lên điều gì?

- **Mono<T>:** publisher phát tối đa một phần tử, hoặc hoàn thành rỗng, hoặc báo lỗi.
- **Flux<T>:** publisher có thể phát 0 đến nhiều phần tử, hoàn thành hoặc báo lỗi.
- Publisher mô tả một pipeline; với publisher **cold** thường chỉ thực thi khi có subscriber. Trong WebFlux, framework subscribe vào kết quả controller để tạo response.
- **Backpressure:** bên tiêu thụ có thể báo nhu cầu (demand) lên phía phát dữ liệu trong các đường truyền Reactive Streams hỗ trợ cơ chế đó. Backpressure không có nghĩa mọi nguồn ngoài (ví dụ một API bên thứ ba) tự động chịu điều tiết.

Luồng request đơn giản hóa:

~~~text
Client HTTP
    -> Reactive HTTP server (thường Reactor Netty)
    -> WebFilter / WebHandler
    -> DispatcherHandler
    -> Chọn annotated controller hoặc functional handler
    -> Mono / Flux pipeline
    -> Reactive codec ghi response, theo tín hiệu dữ liệu
    -> Client nhận HTTP response / stream
~~~

WebFlux thường dùng số lượng nhỏ **event-loop threads** để phục vụ nhiều kết nối đang chờ I/O. Điều đó hiệu quả khi phần lớn chuỗi xử lý cũng không chặn, ví dụ WebClient và driver database reactive. Một request vẫn có thể đi qua các scheduler/thread khác tùy thao tác và cấu hình; không nên quy toàn bộ WebFlux thành “mọi request chạy trên đúng một thread”.

### Blocking là rủi ro cần hiểu

Gọi **Thread.sleep**, JDBC/JPA blocking, client HTTP đồng bộ hoặc **Mono.block()** ngay trên event-loop có thể chặn các request khác chia sẻ event-loop đó. Ví dụ sau chỉ minh họa cách **cô lập công việc blocking** khi buộc phải dùng một thư viện cũ:

~~~java
Mono<Result> source = Mono.fromCallable(() -> blockingRepository.load())
    .subscribeOn(Schedulers.boundedElastic());
~~~

Ví dụ dùng giả định có lớp Result, biến blockingRepository và các import Reactor tương ứng; đây là **đoạn minh họa, không phải một chương trình biên dịch độc lập**. boundedElastic đưa việc blocking sang nhóm thread được thiết kế để chứa các công việc kiểu này; thao tác I/O phía repository **vẫn blocking**, không trở thành non-blocking thực sự và vẫn có giới hạn tài nguyên.

WebFlux thích hợp khi hệ thống có số lượng lớn kết nối đang chờ và một pipeline I/O non-blocking từ đầu đến cuối, ví dụ nhiều lời gọi dịch vụ bằng WebClient, streaming/SSE, kết nối dài, hoặc truy cập dữ liệu qua driver reactive như R2DBC. Nhưng **reactive không mặc nhiên nhanh hơn** cho nghiệp vụ đơn giản hay tác vụ CPU-bound: độ phức tạp, bối cảnh và benchmark thực tế mới quyết định.

### Giống nhau ở cú pháp không có nghĩa giống nhau ở runtime

Cả MVC lẫn WebFlux có thể dùng @RestController, @GetMapping, @RequestBody và cùng một service Spring. Tên annotation giống nhau **không cho biết ứng dụng chạy servlet stack hay reactive stack**. Để xác định phải xét dependency, loại ApplicationContext, auto-configuration và server thực tế.

## 6. Spring Boot: xây và chạy ứng dụng Spring

Spring Boot bổ sung cơ chế **opinionated defaults** và **auto-configuration có điều kiện** lên Spring Framework. Boot phân tích thư viện trên classpath, bean hiện có, properties và các điều kiện khác để đăng ký hạ tầng phù hợp.

Tính năng cốt lõi:

- **SpringApplication.run(...):** khởi động ApplicationContext và quy trình bootstrapping.
- **@SpringBootApplication:** gộp @SpringBootConfiguration, @EnableAutoConfiguration và @ComponentScan. Component scan mặc định bắt đầu từ package của lớp application trở xuống.
- **Starters:** nhóm dependency được lựa chọn sẵn (web stack, logging, testing...) để giảm cấu hình thủ công.
- **Embedded HTTP server:** ứng dụng web thường chạy như một process JAR có server nhúng; không bắt buộc tự triển khai WAR lên server ngoài.
- **Externalized configuration:** application.properties/application.yml, environment variables, command-line arguments... cấu hình theo môi trường.
- **Actuator (dependency tùy chọn):** health, metrics và các endpoint quản trị/quan sát; phải bật và bảo vệ endpoint theo môi trường.
- **Dependency management, packaging, test hỗ trợ:** quản lý phiên bản tương thích theo dòng Boot được chọn.

**Spring Boot không tự động thay thế kiến trúc ứng dụng:** bạn vẫn dùng bean của Spring Framework, controller MVC/WebFlux, service, repository, transaction. Boot cũng không mặc định biến API thành reactive.

### Cẩn thận khác biệt tên starter giữa các dòng Boot

Đối với ứng dụng web Servlet dùng MVC:

- **Spring Boot 3.x:** starter thông dụng là **spring-boot-starter-web**.
- **Spring Boot 4.x:** tài liệu chính thức dùng starter tách rõ **spring-boot-starter-webmvc**.

Đối với WebFlux, starter thông dụng là **spring-boot-starter-webflux**. Khi sao chép hướng dẫn cũ, hãy kiểm tra **dòng phiên bản Spring Boot đang dùng**; tránh suy luận một tên starter áp dụng y hệt cho mọi phiên bản. Nếu tạo dự án bằng Spring Initializr, chọn dependency **Spring Web** (MVC) hoặc **Spring Reactive Web** (WebFlux) phù hợp với phiên bản đã chọn.

Sự khác nhau giữa **starter** và **framework module**: spring-webmvc là module thực thi chức năng MVC thuộc Spring Framework; spring-boot-starter-webmvc là bộ dependency thuận tiện do Spring Boot cung cấp để kéo các thành phần cần thiết và cấu hình tương ứng.

## 7. Hai ứng dụng đối chiếu có thể chạy

Ví dụ minh họa hai endpoint HTTP cùng mục đích, nhưng một bên xử lý MVC, bên kia trả về Mono của WebFlux. **Tạo hai dự án riêng biệt** từ Spring Initializr, dùng **Java 17+**, một phiên bản Spring Boot ổn định tương thích với JDK bạn cài và Maven. Khi chọn dependency trên Initializr:

- Dự án **mvc-demo**: chọn **Spring Web** (được ánh xạ đến starter MVC phù hợp với phiên bản Boot).
- Dự án **webflux-demo**: chọn **Spring Reactive Web**.
- Mỗi dự án có Maven wrapper do Initializr sinh ra; dưới đây cung cấp **toàn bộ mã Java** cần thêm hoặc thay trong dự án tương ứng.

### 7.1. Dự án A — Spring Boot + Spring MVC

Tập tin **src/main/java/example/mvc/MvcApplication.java**:

~~~java
package example.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(MvcApplication.class, args);
    }
}
~~~

Tập tin **src/main/java/example/mvc/GreetingController.java**:

~~~java
package example.mvc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {
    @GetMapping("/greeting")
    public Greeting greet(@RequestParam(defaultValue = "bạn") String name) {
        return new Greeting("Xin chào, " + name);
    }

    public record Greeting(String message) {
    }
}
~~~

Cấu trúc package **example.mvc** phải nằm trong component scan bắt đầu từ MvcApplication. Nếu Initializr tạo package khác, đổi **package** của cả hai file cho nhất quán và đặt chúng vào đúng thư mục.

Tại thư mục gốc **mvc-demo**:

~~~bash
# macOS/Linux
./mvnw spring-boot:run

# Windows PowerShell hoặc Command Prompt: dùng thay lệnh trên
mvnw.cmd spring-boot:run
~~~

Mở một terminal khác:

~~~bash
curl -s "http://localhost:8080/greeting?name=Lan"
~~~

HTTP body (dạng JSON, trình bày một dòng):

~~~json
{"message":"Xin chào, Lan"}
~~~

**Giải thích:** request tới DispatcherServlet, được map vào greet(), tạo record Greeting rồi được JSON message converter serialize. Với starter MVC mặc định, Boot thường cấu hình embedded Tomcat. Controller trả object trực tiếp; bạn không cần viết Servlet hay cấu hình DispatcherServlet bằng tay.

### 7.2. Dự án B — Spring Boot + Spring WebFlux

Tập tin **src/main/java/example/reactive/ReactiveApplication.java**:

~~~java
package example.reactive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactiveApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReactiveApplication.class, args);
    }
}
~~~

Tập tin **src/main/java/example/reactive/GreetingController.java**:

~~~java
package example.reactive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class GreetingController {
    @GetMapping("/greeting")
    public Mono<Greeting> greet(
            @RequestParam(defaultValue = "bạn") String name) {
        return Mono.fromSupplier(
                () -> new Greeting("Xin chào, " + name));
    }

    public record Greeting(String message) {
    }
}
~~~

Trong thư mục gốc **webflux-demo**, chạy:

~~~bash
# macOS/Linux
./mvnw spring-boot:run

# Windows: dùng thay lệnh trên
mvnw.cmd spring-boot:run
~~~

Từ terminal khác:

~~~bash
curl -s "http://localhost:8080/greeting?name=Lan"
~~~

HTTP body:

~~~json
{"message":"Xin chào, Lan"}
~~~

**Giải thích:** WebFlux framework subscribe vào Mono trả về từ controller và phát JSON khi publisher cung cấp Greeting. Mono.fromSupplier hoãn công việc tạo đối tượng đến lúc subscription; bản thân ví dụ quá nhỏ nên **không chứng minh lợi ích hiệu năng** của reactive. Với cấu hình mặc định thường dùng Reactor Netty.

**Quan trọng:** hai dự án đều có đường dẫn /greeting và cho cùng kết quả. Điểm khác nhau nằm ở **web stack, contract thực thi và chuỗi I/O**, không phải ở JSON body hay tên annotation. Chạy lần lượt từng dự án trên cổng 8080 hoặc cấu hình server.port khác nhau khi muốn chạy song song.

### 7.3. Kiểm tra dependency Maven

Với Spring Boot 4.x, một dependency MVC đặc trưng trong pom.xml là:

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
~~~

Với Spring Boot 3.x, dùng **spring-boot-starter-web** thay cho artifactId của ví dụ MVC trên. Với WebFlux trên các dòng tương ứng:

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
~~~

Đây là **các đoạn dependency** để đặt vào thẻ dependencies của pom.xml đã có Spring Boot parent/BOM tương thích, **không phải toàn bộ pom.xml độc lập**. Version của starter thường do dependency management của Boot quyết định; không tự trộn Spring Framework 6/7, Boot 3/4 hoặc Reactor versions tùy ý.

## 8. Minh họa execution model qua cùng một request

Giả sử endpoint vừa lấy khách hàng từ database vừa gọi API khác:

~~~text
MVC:     HTTP → Servlet thread → JDBC (chờ) → RestClient (chờ) → JSON
WebFlux: HTTP → reactive pipeline → R2DBC / WebClient → signals → JSON
~~~

Với MVC đồng bộ, thread thường chờ ở JDBC hoặc HTTP client. Với WebFlux dùng **driver và connector non-blocking thật**, event-loop có thể phục vụ công việc khác trong lúc chờ I/O. Đây chỉ là mô hình rút gọn: Servlet async có thể giải phóng request thread, còn WebFlux có thể đổi thread theo scheduler và vẫn bị nghẽn nếu gọi thư viện blocking.

**Điều kiện quan trọng:** `Mono` không làm JDBC thành non-blocking; Reactive Streams backpressure không tự điều tiết mọi API bên ngoài; CPU-bound work vẫn tiêu tốn CPU. Vì vậy, chọn stack theo **toàn bộ chuỗi I/O** chứ không nhìn annotation hay tên server. Cách lập bảng từng chặng I/O, benchmark và lựa chọn kiến trúc cụ thể được đào sâu ở bài riêng về Servlet/MVC so với WebFlux.

## 9. Cách chọn stack cho dự án

Cách nhớ để bắt đầu: **MVC** thường thích hợp cho ứng dụng Servlet/HTML/REST với JPA, JDBC và SDK blocking; **WebFlux** đáng cân nhắc nếu có streaming hoặc nhiều kết nối chờ I/O và chuỗi client/driver reactive xuyên suốt. Với tác vụ batch hay console không có HTTP, có thể dùng Spring Framework/Boot mà không cần web stack. Nếu hệ thống đang chạy MVC ổn định, hãy đo bottleneck trước khi di chuyển.

Các nhận diện quan trọng giúp tránh chọn sai runtime:

### Có thể dùng WebClient trong Spring MVC không?

**Có.** WebClient là HTTP client non-blocking thuộc hệ sinh thái WebFlux và có thể được dùng trong ứng dụng MVC. Thêm thư viện để dùng WebClient **không có nghĩa mọi controller MVC tự thành WebFlux**. Cần hiểu cách tích hợp kết quả async vào MVC để tránh vô tình gọi block() khiến thread chờ không cần thiết.

### Có nên đưa cả MVC và WebFlux starter vào cùng dự án?

Thông thường **không cần** để xử lý cùng một server API. Khi cả module MVC và WebFlux nằm trên classpath, Spring Boot mặc định **ưu tiên loại ứng dụng Servlet/MVC**; hiện tượng này hữu ích với dự án MVC dùng WebClient nhưng dễ gây nhầm khi người viết tưởng controller đã chạy WebFlux. Nếu thật sự muốn reactive web application, cần kiểm tra lại dependencies và xác định rõ WebApplicationType.REACTIVE theo yêu cầu thực tế.

### Spring Security, Spring Data, Spring Cloud có phải Spring Boot không?

Không. Chúng là **các dự án riêng trong hệ sinh thái Spring**, có thể tích hợp với Framework và thường được Boot hỗ trợ qua starter/auto-configuration. Phân biệt *Spring Framework* (nền tảng), *Spring Boot* (khởi động/cấu hình), *MVC/WebFlux* (web stack) và *các dự án bổ trợ* theo trách nhiệm thay vì nhầm chúng thành cùng một framework.

## 10. Những nhầm lẫn và lỗi phổ biến

1. **“Spring Boot thay thế Spring Framework.”** Sai. Boot dựa trên Spring Framework và tự động cấu hình nhiều thành phần của nó.
2. **“Spring Boot luôn là Spring MVC.”** Sai. Boot có thể chạy MVC, WebFlux hoặc không có web server.
3. **“Spring MVC là tên cũ của Spring Boot.”** Sai. MVC giải quyết xử lý web bằng Servlet; Boot giải quyết khởi động và cấu hình.
4. **“Dùng @RestController thì chắc chắn là MVC.”** Sai. WebFlux cũng hỗ trợ cùng annotation, cần nhìn web stack được kích hoạt.
5. **“WebFlux lúc nào cũng nhanh hơn MVC.”** Sai. Workload, client/database, độ trễ I/O, giới hạn CPU và cách triển khai mới quyết định.
6. **“Có Mono là mọi lời gọi non-blocking.”** Sai. Một Mono có thể bọc lệnh blocking; kiểu trả về không sửa bản chất I/O.
7. **“WebFlux chỉ chạy với Netty.”** Sai. Reactor Netty phổ biến với Boot, nhưng WebFlux hỗ trợ các runtime khác thích hợp.
8. **“MVC không hỗ trợ bất đồng bộ.”** Sai. MVC có Servlet async và các kiểu return async, dù hạ tầng khác WebFlux.
9. **“Thêm cả hai starter tự động chuyển hết sang reactive.”** Sai. Kiểm tra sự ưu tiên Servlet/MVC của Boot và auto-configuration.
10. **“@EnableWebMvc luôn cần khi dùng Spring Boot MVC.”** Không đúng trong cấu hình Boot thông thường: Boot đã tự cấu hình MVC; @EnableWebMvc trao quyền kiểm soát cấu hình MVC cho ứng dụng và có thể làm mất các tiện ích auto-configuration mặc định. Để tùy chỉnh nhỏ, ưu tiên WebMvcConfigurer.
11. **“Spring Boot tự tìm được mọi @Service ở mọi package.”** Sai. Component scan có phạm vi; đặt application class ở package gốc phù hợp hoặc cấu hình scan rõ ràng.
12. **“Để minh họa Spring Framework phải dùng HTTP.”** Sai. IoC/DI, transaction hoặc event vẫn có ích trong tác vụ không web.

## 11. Chẩn đoán khi ứng dụng chạy sai stack

Khi controller không được map hoặc ứng dụng sử dụng mô hình web không như mong đợi, kiểm tra theo thứ tự:

1. **Dependency tree:** ứng dụng đang có spring-webmvc, spring-webflux, starter nào và theo phiên bản Boot nào? Maven có thể dùng **mvn dependency:tree**.
2. **Component scan:** lớp @SpringBootApplication có nằm tại package bao trùm controller và service không?
3. **Auto-configuration:** chạy với tùy chọn **--debug** hoặc kiểm tra condition evaluation report/Actuator conditions khi đã cấu hình thích hợp; xác định config MVC hoặc WebFlux nào thực sự kích hoạt.
4. **HTTP server và context:** Tomcat/Jetty/Reactor Netty đang khởi động? server.port, servlet context path hoặc base path có bị đổi?
5. **Kiểu I/O trong service:** thread đang chờ JDBC/HTTP blocking, hay pipeline dùng driver và client non-blocking? Đừng chỉ nhìn chữ Mono/Flux.
6. **Lỗi thường gặp:** nếu port 8080 đã được dùng bởi dự án ví dụ kia, hãy dừng server trước hoặc đổi port, chẳng hạn **--server.port=8081** khi chạy ứng dụng Boot.

## 12. Ghi nhớ và tự kiểm tra

**Công thức nhận diện:** *Spring Framework = nền tảng; Spring MVC = Servlet web; Spring WebFlux = reactive web; Spring Boot = bootstrap và auto-configuration.*

Hãy thử trả lời:

1. Dự án dùng **@SpringBootApplication + spring-boot-starter-webmvc** thì đang dùng Framework, MVC hay Boot? **Cả ba**: Boot khởi động, MVC xử lý HTTP, Spring Framework cung cấp các thành phần nền.
2. Dự án chỉ có **spring-boot-starter-webflux**, controller trả **Mono<Greeting>**, chạy bằng Reactor Netty: dùng stack nào? **Boot + Spring WebFlux** (cùng hạ tầng Spring Framework).
3. Một REST API dùng **JPA/JDBC** với throughput tốt, chưa cần streaming: có bắt buộc chuyển WebFlux không? **Không**; MVC thường là lựa chọn tự nhiên, cần số liệu trước khi đổi.
4. Một app MVC thêm dependency WebFlux để gọi **WebClient**: endpoint của app có tự chuyển sang WebFlux không? **Không**; phải xác định web application type và cấu hình thực tế.
5. Một ứng dụng chỉ cần DI để chạy command-line: có phải thêm Spring MVC không? **Không**.

## Tài liệu chính thức để đào sâu

- [Spring Framework — Spring Web MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html): DispatcherServlet, annotated controllers, Servlet async, message conversion.
- [Spring Framework — Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html): reactive contract, annotated/functional endpoints, runtime và so sánh với MVC.
- [Spring Framework — Core Technologies](https://docs.spring.io/spring-framework/reference/core.html): IoC container, bean, dependency injection.
- [Spring Boot — Web](https://docs.spring.io/spring-boot/reference/web/index.html): lựa chọn MVC/WebFlux và starter theo dòng phiên bản.
- [Spring Boot — Using @SpringBootApplication](https://docs.spring.io/spring-boot/reference/using/using-the-springbootapplication-annotation.html): auto-configuration, component scan và cấu hình ứng dụng.
- [Spring Boot — Reactive Web Applications](https://docs.spring.io/spring-boot/reference/web/reactive.html): WebFlux auto-configuration và lưu ý khi MVC/WebFlux cùng có mặt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-overview-003">Các mô-đun spring-web, spring-webmvc, spring-webflux và spring-websocket</a>

<details>
<summary>Xem chi tiết</summary>

# Các mô-đun `spring-web`, `spring-webmvc`, `spring-webflux` và `spring-websocket`

## 1. Bức tranh tổng thể

Trong Spring Framework, “web” không phải chỉ một cách xây dựng ứng dụng. Có một lớp hỗ trợ giao tiếp HTTP dùng chung, hai mô hình xử lý HTTP ở server và các API phục vụ giao tiếp hai chiều qua WebSocket.

| Artifact | Trách nhiệm chính | API/thành phần tiêu biểu | Phù hợp khi |
| --- | --- | --- | --- |
| `org.springframework:spring-web` | Hạ tầng HTTP và các chức năng web dùng chung | `HttpHeaders`, `HttpEntity`, `ResponseEntity`, `MediaType`, `RestClient`, HTTP message converters | Dùng HTTP abstractions, gọi REST đồng bộ, làm nền cho các web framework |
| `org.springframework:spring-webmvc` | Web/REST framework trên **Servlet stack** | `DispatcherServlet`, `@Controller`, `@RestController`, `HandlerMapping`, `ViewResolver` | REST CRUD, ứng dụng web dùng Servlet và nhiều thư viện blocking |
| `org.springframework:spring-webflux` | Web/REST framework **reactive**, thêm HTTP client reactive | `DispatcherHandler`, `Mono`, `Flux`, `RouterFunction`, `WebClient` | I/O non-blocking, streaming, luồng dữ liệu reactive |
| `org.springframework:spring-websocket` | WebSocket và STOMP messaging trên **Servlet stack** | `WebSocketHandler`, `TextWebSocketHandler`, `@EnableWebSocket`, `@EnableWebSocketMessageBroker` | Chat, thông báo thời gian thực, giao tiếp hai chiều trên ứng dụng Servlet |

**Không phải bốn lựa chọn ngang hàng:** `spring-web` là phần nền; `spring-webmvc` và `spring-webflux` là hai web framework HTTP khác nhau; `spring-websocket` bổ sung WebSocket cho Servlet stack. WebFlux còn có **API WebSocket reactive riêng**. Một ứng dụng có thể có nhiều artifact, nhưng sự tồn tại của một dependency không tự xác định loại server đang chạy.

```text
                      Spring Framework
                            |
                      spring-web
                HTTP foundation, codecs,
                 HTTP client đồng bộ
                            |
                 +----------+-----------+
                 |                      |
            spring-webmvc          spring-webflux
          Servlet / HTTP MVC      Reactive / HTTP
          DispatcherServlet       DispatcherHandler
                 |                      |
           spring-websocket      Reactive WebSocket API
          WebSocket + STOMP      trong spring-webflux
```

Sơ đồ mô tả **vai trò** của các module, không phải biểu đồ dependency transitive đầy đủ. Trong thực tế, các module cấp cao có thể kéo theo những thư viện nền qua cơ chế quản lý dependencies.

## 2. `spring-web`: nền tảng HTTP dùng chung

Module này cung cấp các đối tượng và cơ chế làm việc với HTTP, ví dụ:

- `HttpHeaders`: chứa header như `Content-Type`, `Accept`, `Authorization`.
- `HttpMethod`, `HttpStatusCode`, `MediaType`: phương thức, trạng thái HTTP và media type.
- `HttpEntity<T>`: body + headers; `ResponseEntity<T>` thêm trạng thái HTTP, thường dùng làm kết quả trả về controller.
- `HttpMessageConverter`: đọc/ghi body HTTP với các định dạng được hỗ trợ, chẳng hạn JSON khi đã có thư viện và cấu hình phù hợp.
- `RestClient`: HTTP client fluent **đồng bộ**; `RestTemplate` là HTTP client đồng bộ lâu đời còn gặp ở mã nguồn cũ.
- Các HTTP abstraction, codec và lớp tích hợp nền tảng được nhiều phần khác của Spring web sử dụng.

Ví dụ gọi một API bên ngoài bằng `RestClient`:

```java
import org.springframework.web.client.RestClient;

public class BookClient {
    private final RestClient client =
            RestClient.create("https://api.example.com");

    public String findBook(String id) {
        return client.get()
                .uri("/books/{id}", id)
                .retrieve()
                .body(String.class);
    }
}
```

Đây là **class minh họa**, không phải chương trình độc lập và `api.example.com` chỉ là địa chỉ giả định. `body(String.class)` trong cách dùng đồng bộ sẽ lấy kết quả trước khi phương thức trả về; cần cấu hình timeout, xử lý HTTP error và giới hạn tải theo yêu cầu ứng dụng.

**Cần phân biệt:** `RestClient` là **HTTP client**. Chỉ có `spring-web` không đồng nghĩa ứng dụng đã thiết lập `DispatcherServlet`, `DispatcherHandler`, HTTP server hoặc REST controller.

## 3. `spring-webmvc`: Spring MVC trên Servlet stack

### 3.1. Front controller và đường đi request

Spring MVC dựa trên Jakarta Servlet API, thường chạy trong Servlet container (Tomcat, Jetty…). Trung tâm điều phối request là `DispatcherServlet` theo mô hình *front controller*. Framework định tuyến một HTTP request tới controller phù hợp, chuyển tham số thành giá trị Java, thực thi logic và tạo response.

```text
HTTP client
    |
Servlet container và Filter chain
    |
DispatcherServlet
    |
HandlerMapping -> HandlerAdapter -> Controller method
    |
Business service / repository
    |
HTTP message conversion hoặc view rendering
    |
HTTP response
```

Các annotation thường gặp gồm `@RestController`, `@GetMapping`, `@PostMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`, `@ExceptionHandler`. Với REST API, `@RestController` thường trả dữ liệu trong response body; với ứng dụng render trang, `@Controller` có thể trả view name để `ViewResolver` xử lý.

Ví dụ REST controller:

```java
package example.mvc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {
    @GetMapping("/hello/{name}")
    public Greeting hello(@PathVariable String name) {
        return new Greeting("Xin chào, " + name);
    }

    public record Greeting(String message) {}
}
```

Khi ứng dụng MVC có server và JSON converter được cấu hình, gọi `GET /hello/Lan` sẽ nhận body:

```json
{"message":"Xin chào, Lan"}
```

Đây là **một class được Spring quản lý**, chưa phải ứng dụng chạy độc lập qua `javac`/`java`; cần application context, component scanning hoặc khai báo bean, cấu hình servlet và HTTP runtime. Trong Spring Boot, starter và auto-configuration thường lo phần lớn cấu hình này.

### 3.2. Blocking và asynchronous trong MVC

Ở mô hình Servlet phổ biến, thread do container cấp thực thi controller. Nếu code gọi JDBC blocking hoặc HTTP client blocking, thread thường chờ thao tác I/O hoàn thành. Điều này **không mặc nhiên là lỗi**: với JPA/JDBC và các thư viện đồng bộ, MVC là lựa chọn dễ hiểu và phổ biến.

Không nên kết luận MVC “chỉ có synchronous”: Servlet async và các kiểu như `Callable`, `DeferredResult` hoặc cơ chế streaming có thể cho phép xử lý bất đồng bộ trong một số giai đoạn. Tuy nhiên, application vẫn dùng **Servlet stack và DispatcherServlet**; các API này không tự biến server thành WebFlux.

## 4. `spring-webflux`: Spring WebFlux trên reactive stack

### 4.1. Mô hình reactive và non-blocking

WebFlux được Spring bổ sung từ phiên bản 5.0. Đây là web framework có hạ tầng **non-blocking**, Reactive Streams và hỗ trợ backpressure. Nó dùng `DispatcherHandler` cho cơ chế điều phối tương ứng, **không dùng `DispatcherServlet` làm dispatcher HTTP của WebFlux**.

WebFlux chạy được với Reactor Netty hoặc môi trường Servlet container có tích hợp non-blocking thích hợp. Vì vậy, **WebFlux không có nghĩa bắt buộc là Netty**.

WebFlux hỗ trợ hai phong cách xây endpoint:

1. **Annotated controller:** `@RestController`, `@GetMapping`… giống tên annotation ở MVC.
2. **Functional endpoint:** khai báo route qua `RouterFunction`, `HandlerFunction`.

WebFlux dùng Project Reactor trong nhiều API:

- `Mono<T>` đại diện một publisher có thể phát **0 hoặc 1** phần tử rồi hoàn tất/báo lỗi.
- `Flux<T>` đại diện một publisher có thể phát **0 đến nhiều** phần tử rồi hoàn tất/báo lỗi.
- `map`, `flatMap`, `filter`, `onErrorResume`… cho phép mô tả các bước biến đổi và xử lý.
- Backpressure trong Reactive Streams diễn tả khả năng bên tiêu thụ yêu cầu lượng dữ liệu cần nhận; không có nghĩa mọi tài nguyên phía sau đều tự giới hạn tải hoàn hảo.

Một endpoint WebFlux:

```java
package example.reactive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class GreetingController {
    @GetMapping("/hello/{name}")
    public Mono<Greeting> hello(@PathVariable String name) {
        return Mono.just(new Greeting("Xin chào, " + name));
    }

    public record Greeting(String message) {}
}
```

Request `GET /hello/Lan` trong một ứng dụng WebFlux được cấu hình đúng vẫn trả một JSON object tương đương ví dụ MVC. **Sự khác nhau quan trọng là HTTP runtime và cách xử lý kết quả**, không nằm ở tên annotation.

`Mono.just(...)` trong ví dụ tạo publisher từ giá trị có sẵn; nó **không minh họa một I/O non-blocking thực thụ**. Lợi ích WebFlux rõ hơn khi request kết hợp WebClient, database driver reactive hoặc luồng dữ liệu bất đồng bộ thật sự.

### 4.2. `WebClient`: client reactive thuộc WebFlux

`WebClient` là HTTP client dùng API reactive. Ví dụ:

```java
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class BookClient {
    private final WebClient client =
            WebClient.create("https://api.example.com");

    public Mono<String> findBook(String id) {
        return client.get()
                .uri("/books/{id}", id)
                .retrieve()
                .bodyToMono(String.class);
    }
}
```

`findBook` trả về `Mono<String>` để người gọi tiếp tục ghép các thao tác bất đồng bộ. HTTP request cần một HTTP connector thích hợp, endpoint thật và một subscriber; trong WebFlux, framework subscribe khi controller trả publisher phù hợp. Đây cũng là ví dụ **class minh họa**, chưa có chương trình chạy độc lập.

**Một ứng dụng MVC có thể dùng `WebClient`** để gọi dịch vụ khác mà server vẫn chạy MVC. Việc thêm `spring-webflux` nhằm lấy `WebClient` không tự chuyển dispatcher server thành `DispatcherHandler`.

### 4.3. Lợi ích và giới hạn thực tế

WebFlux có giá trị khi hệ thống xử lý nhiều I/O bằng thư viện **non-blocking thật**, cần streaming hoặc tổ hợp chuỗi sự kiện theo kiểu reactive. Nếu controller WebFlux gọi `Thread.sleep(...)`, JDBC blocking hay HTTP client đồng bộ ngay trên event-loop, thread có thể bị giữ và làm giảm khả năng đáp ứng của các request khác.

Khi bất khả kháng phải dùng code blocking, Reactor có scheduler như `Schedulers.boundedElastic()` để cô lập công việc blocking khỏi các thread nhạy cảm. Đó là biện pháp tương thích, **không chuyển driver blocking thành driver reactive**. Với hệ thống chủ yếu JPA/JDBC đồng bộ, MVC thường dễ thiết kế và vận hành hơn; WebFlux không mặc định nhanh hơn trên mọi workload.

## 5. `spring-websocket`: kết nối hai chiều trên Servlet stack

### 5.1. Phân biệt HTTP, WebSocket và STOMP

HTTP thông thường có mô hình request/response. WebSocket bắt đầu bằng một HTTP handshake nâng cấp giao thức khi thành công rồi duy trì một kết nối hai chiều. Cả client và server có thể chủ động gửi text hoặc binary message.

- **HTTP/REST:** phù hợp truy vấn, cập nhật tài nguyên, CRUD.
- **SSE (Server-Sent Events):** phù hợp việc server liên tục đẩy dữ liệu **một chiều** tới browser.
- **WebSocket:** phù hợp tương tác hai chiều thường xuyên trên kết nối lâu dài.
- **STOMP:** giao thức nhắn tin ở lớp ứng dụng có destination và lệnh như `SEND`/`SUBSCRIBE`; có thể chạy **trên WebSocket**, nhưng WebSocket **không bắt buộc** dùng STOMP.

`spring-websocket` cung cấp API WebSocket cho Servlet stack, ví dụ `WebSocketHandler` và `TextWebSocketHandler`. Với Spring messaging, Spring còn hỗ trợ STOMP broker relay, simple broker và xử lý message theo annotation.

### 5.2. Ví dụ WebSocket handler mức thấp

Ví dụ dưới đây là **một class cấu hình** dành cho ứng dụng Servlet/Spring MVC đã có server và các dependency cần thiết:

```java
package example.socket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EchoHandler(), "/ws/echo");
    }

    static class EchoHandler extends TextWebSocketHandler {
        @Override
        protected void handleTextMessage(WebSocketSession session,
                                         TextMessage message) throws Exception {
            session.sendMessage(
                    new TextMessage("echo:" + message.getPayload())
            );
        }
    }
}
```

Client JavaScript trong browser có thể gửi thử:

```javascript
const socket = new WebSocket("ws://localhost:8080/ws/echo");
socket.addEventListener("open", () => socket.send("hello"));
socket.addEventListener("message", event => console.log(event.data));
```

Nếu ứng dụng chạy ở cổng 8080 và WebSocket handshake thành công, kết quả nhận được là `echo:hello`. Ví dụ chỉ để hiểu cơ chế; triển khai thực tế phải xử lý đóng kết nối, lỗi mạng, giới hạn message, Origin, xác thực, phân quyền và vận hành session. `ws://` phù hợp cho local không mã hóa; kết nối được bảo vệ dùng `wss://`.

### 5.3. STOMP và pub/sub

Spring hỗ trợ STOMP over WebSocket qua `spring-websocket` cùng `spring-messaging`. Ví dụ cấu hình *broker đơn giản*:

```java
package example.socket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }
}
```

Trong mô hình này, `/ws` là điểm kết nối STOMP qua WebSocket; message gửi tới destination có prefix `/app` được điều phối tới handler `@MessageMapping` được định nghĩa phù hợp; simple broker phục vụ subscription trên `/topic`. Class trên **chưa có message controller** và không tự sinh thông điệp broadcast. Simple broker thích hợp các tình huống cơ bản, không nên xem là thay thế mọi tính năng reliability/persistence/scale của message broker chuyên dụng. Khi cần, có thể cấu hình broker relay. **SockJS là tùy chọn**, không phải điều kiện bắt buộc của WebSocket hoặc STOMP.

### 5.4. WebSocket ở WebFlux là API khác

WebFlux có WebSocket reactive thông qua `org.springframework.web.reactive.socket.WebSocketHandler` và `org.springframework.web.reactive.socket.WebSocketSession`. Đây là **những kiểu khác package** với `org.springframework.web.socket.WebSocketHandler` và `WebSocketSession` của Servlet stack. Ví dụ `TextWebSocketHandler` phía trên vì vậy **không chuyển nguyên xi sang WebFlux**. Với ứng dụng thuần WebFlux, hãy dùng WebSocket handler reactive thuộc WebFlux.

## 6. Chọn module theo yêu cầu

| Yêu cầu | Chọn gì? | Giải thích |
| --- | --- | --- |
| REST API CRUD, JDBC/JPA, code phần lớn đồng bộ | `spring-webmvc` | Servlet MVC khớp với nhiều thư viện blocking |
| Streaming/reactive server, nhiều I/O non-blocking | `spring-webflux` | Runtime và pipeline hỗ trợ Reactive Streams |
| Gọi HTTP synchronous | `spring-web` và `RestClient` | Client đồng bộ, không cần web server |
| Gọi HTTP reactive từ MVC hoặc WebFlux | `spring-webflux` và `WebClient` | Client reactive độc lập với loại server |
| Chat hai chiều trong ứng dụng MVC | `spring-websocket` | Handler WebSocket hoặc STOMP trên Servlet |
| WebSocket trong ứng dụng WebFlux | WebSocket API của `spring-webflux` | Handler/session reactive cùng stack |

Các module không giải quyết toàn bộ hệ thống chỉ bằng một dependency. Security, request validation, persistence, logging, backpressure ở những ranh giới khác, điều phối phiên kết nối và deployment cần được thiết kế riêng.

## 7. Khai báo dependency đúng cách

Nếu dùng Spring Framework thuần và đã quản lý phiên bản các artifact một cách nhất quán (ví dụ BOM), các tọa độ thư viện là:

```groovy
dependencies {
    implementation 'org.springframework:spring-web'        // nền HTTP
    implementation 'org.springframework:spring-webmvc'     // Servlet MVC
    implementation 'org.springframework:spring-webflux'    // WebFlux/WebClient
    implementation 'org.springframework:spring-websocket'  // Servlet WebSocket
}
```

Đây là **danh mục nhận diện**, không phải cấu hình yêu cầu cài đủ cả bốn. Chỉ chọn các module cần thiết; các module cấp cao có thể kéo `spring-web` theo dependency transitive. Với Framework thuần, application phải tự chuẩn bị server/runtime, context, các codec thích hợp và các cấu hình phụ trợ. STOMP messaging có thêm `spring-messaging`.

Khi dùng **Spring Boot 3.x**, starter giúp quản lý nhiều dependency và auto-configuration. Ba phương án **riêng biệt**:

```groovy
// 1. REST server trên Spring MVC:
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

```groovy
// 2. REST server trên Spring WebFlux:
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
```

```groovy
// 3. MVC + WebSocket/STOMP:
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
}
```

Tên starter có thể thay đổi qua các thế hệ Spring Boot; kiểm tra tài liệu theo **phiên bản thực tế** của dự án. Trong Boot 3.x, nếu cùng có cả starter MVC và WebFlux, Boot thường tự chọn **MVC làm web application type**. Một lý do thường gặp để giữ cả hai là server MVC dùng `WebClient`. Đừng kết luận server sử dụng `DispatcherHandler` chỉ vì classpath có `spring-webflux`.

Với ứng dụng Spring Boot 3.x tối giản, có thể dùng điểm khởi chạy sau cùng controller MVC hoặc WebFlux đã nêu; đây là một file khác, ở package cha của `example.mvc` hoặc `example.reactive` sao cho component scan tìm thấy controller:

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Để **chạy một trong các ví dụ HTTP**: tạo dự án Spring Boot 3.x với Java 17+, Gradle plugin/BOM phù hợp phiên bản Boot, chọn **một starter web** trong hai phương án đầu, đặt `Application.java` và `GreetingController.java` trong các package tương ứng rồi dùng `./gradlew bootRun` (Linux/macOS) hoặc `gradlew.bat bootRun` (Windows). Khi server thông thường ở `localhost:8080`, chạy:

```bash
curl http://localhost:8080/hello/Lan
```

Body dự kiến (nếu không bị đổi bởi cấu hình khác): `{"message":"Xin chào, Lan"}`. Phần WebSocket chỉ chạy khi chọn ứng dụng MVC với dependency WebSocket và cấu hình handler ở mục 5; `curl` thông thường không phải WebSocket client.

## 8. Những ngộ nhận thường gặp

1. **“Có `@RestController` thì là Spring MVC.”** Sai: MVC và WebFlux đều hỗ trợ annotated controllers. Phải nhìn web stack/runtime.
2. **“Chỉ cần `spring-web` là đủ tạo server REST.”** Sai: module nền không đồng nghĩa web server và dispatcher đã được cấu hình.
3. **“WebFlux luôn nhanh hơn MVC.”** Sai: lợi ích phụ thuộc tính chất workload và số lượng thành phần I/O non-blocking thật sự.
4. **“WebFlux bắt buộc là Netty.”** Sai: WebFlux có cơ chế tích hợp với Servlet container hỗ trợ non-blocking.
5. **“Trả `Mono` khiến mọi tác vụ thành non-blocking.”** Sai: JDBC blocking hoặc `Thread.sleep` vẫn blocking nếu được gọi ở bên trong pipeline.
6. **“MVC không được dùng `WebClient`.”** Sai: MVC có thể gọi WebClient từ HTTP client layer.
7. **“Có `spring-websocket` tức thay thế `spring-webmvc`.”** Sai: WebSocket thêm giao thức kết nối hai chiều, không thay REST dispatching.
8. **“WebSocket bằng STOMP.”** Sai: WebSocket là transport hai chiều; STOMP cung cấp ngữ nghĩa messaging tùy chọn.
9. **“Cấu hình CORS HTTP tự bảo vệ hết WebSocket.”** Sai: cần xét Origin/handshake và authentication/authorization của giao thức/stack sử dụng.
10. **“Có cả MVC và WebFlux dependency thì hai server stack tự chạy song song.”** Sai: cần kiểm tra web application type, server và dispatcher thực tế.

## 9. Bài tập vận dụng

**Tình huống A:** Một hệ thống bán hàng dùng JPA đồng bộ, CRUD JSON, thỉnh thoảng gọi API vận chuyển. Điểm khởi đầu hợp lý: **Spring MVC + RestClient**. Có thể dùng **WebClient trong MVC** nếu nhu cầu tích hợp async cụ thể chứng minh được lợi ích.

**Tình huống B:** Bảng trạng thái công việc chỉ nhận cập nhật từ server. So sánh **SSE** và **WebSocket**. Nếu browser cần gửi hành động tương tác liên tục, WebSocket phù hợp hơn; nếu chỉ nhận luồng sự kiện một chiều, SSE có thể đơn giản hơn.

**Tình huống C:** Một controller WebFlux trả `Mono<Order>` nhưng gọi JDBC blocking trong `map(...)`. Giải thích vì sao Mono không tự loại bỏ blocking, thread nào có nguy cơ bị chiếm, và giải pháp dùng reactive driver hoặc tách blocking khỏi event-loop.

**Tự kiểm tra:**

- `DispatcherServlet` khác `DispatcherHandler` thế nào? — Trước là trung tâm MVC/Servlet; sau là trung tâm WebFlux/reactive.
- `RestClient` khác `WebClient` thế nào? — REST client đồng bộ so với HTTP client reactive/non-blocking.
- WebFlux muốn làm WebSocket có luôn cần `spring-websocket` không? — Không; WebFlux có WebSocket reactive API riêng.

## 10. Đọc thêm từ tài liệu chính thức

- Spring MVC: https://docs.spring.io/spring-framework/reference/web/webmvc.html
- Spring WebFlux: https://docs.spring.io/spring-framework/reference/web/webflux.html
- Reactive Core và thành phần nền `spring-web`: https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html
- WebClient: https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html
- Servlet WebSocket API: https://docs.spring.io/spring-framework/reference/web/websocket/server.html
- STOMP: https://docs.spring.io/spring-framework/reference/web/websocket/stomp.html

**Điểm chốt:** `spring-web` là nền HTTP; `spring-webmvc` triển khai web server Servlet/MVC; `spring-webflux` là web framework reactive và cung cấp WebClient; `spring-websocket` phục vụ WebSocket/STOMP trên Servlet, còn WebFlux có WebSocket API reactive của chính nó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-overview-004">Kiến thức cần có: Java, IoC/DI, HTTP và xử lý đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Kiến thức cần có: Java, IoC/DI, HTTP và xử lý đồng thời

Trước khi học **Spring Framework Web**, cần nối bốn mảng kiến thức: Java diễn tả đối tượng và hành vi; IoC/DI giúp Spring khởi tạo, ghép và quản lý các đối tượng; HTTP quy định cách client và server trao đổi thông điệp; xử lý đồng thời quyết định điều gì xảy ra khi nhiều request đi vào cùng lúc. Hiểu các nền tảng này sẽ giúp đọc controller, service và lỗi ứng dụng theo cơ chế thực tế, thay vì chỉ ghi nhớ annotation.

## 1. Bức tranh tổng thể của một yêu cầu web

Một yêu cầu đi qua các lớp trách nhiệm điển hình:

```text
Client (trình duyệt, curl, ứng dụng khác)
    │  HTTP request: method + URL + headers + body (nếu có)
    ▼
Web server / Servlet container
    ▼
Spring MVC DispatcherServlet
    ▼
Handler mapping → Controller → Service → (Repository / hệ thống ngoài)
    ▲                     │          │
    │                     └── Spring DI cung cấp dependency
    └── HTTP response: status + headers + body
```

Đây là sơ đồ khái niệm cho **Spring MVC truyền thống**. Bộ điều phối tìm handler phù hợp; tầng controller xử lý phần giao tiếp HTTP; service chứa hành vi nghiệp vụ; framework chuyển đổi dữ liệu giữa JSON và đối tượng Java qua HTTP message converter. Các thành phần có thể được nhiều request sử dụng đồng thời.

Bạn cần trả lời được bốn câu: đối tượng Java biểu diễn request/response như thế nào; ai tạo và nối controller với service; một request HTTP hợp lệ gồm gì; và liệu service có an toàn khi nhiều luồng gọi cùng lúc hay không.

## 2. Nền tảng Java cần nắm

### 2.1. Class, object, interface, constructor và phạm vi truy cập

- **Class** mô tả dữ liệu và phương thức; **object** là một thể hiện của class. Hai request có thể gọi cùng một object service.
- **Interface** mô tả hợp đồng hành vi. Code phụ thuộc interface giúp thay triển khai và viết test mà không buộc caller biết chi tiết.
- **Constructor** tạo trạng thái hợp lệ ban đầu. Spring thường dùng constructor để truyền dependency đã được quản lý.
- `private` che trạng thái triển khai; `final` trên field ngăn gán lại tham chiếu sau khi khởi tạo. `final` không tự làm cho đối tượng được tham chiếu trở thành bất biến.
- **Method parameter và local variable** là dữ liệu của từng lần gọi. Field của bean singleton là trạng thái có thể được chia sẻ giữa các lần gọi.

Ví dụ DI thuần Java dưới đây chạy độc lập, chưa cần Spring. File `ConstructorDiDemo.java`:

```java
interface Formatter {
    String format(String name);
}

final class VietnameseFormatter implements Formatter {
    @Override
    public String format(String name) {
        return "Xin chào, " + name + "!";
    }
}

final class WelcomeService {
    private final Formatter formatter;

    WelcomeService(Formatter formatter) {
        this.formatter = formatter;
    }

    String welcome(String name) {
        return formatter.format(name);
    }
}

public class ConstructorDiDemo {
    public static void main(String[] args) {
        Formatter formatter = new VietnameseFormatter();
        WelcomeService service = new WelcomeService(formatter);
        System.out.println(service.welcome("Lan"));
    }
}
```

Chạy với JDK 17+:

```bash
javac ConstructorDiDemo.java
java ConstructorDiDemo
```

Kết quả:

```text
Xin chào, Lan!
```

`WelcomeService` sử dụng `Formatter` do nơi khác cung cấp qua constructor; nó không tự khởi tạo `VietnameseFormatter`. Đó là cơ chế **dependency injection**, ngay cả khi chưa có container.

### 2.2. Dữ liệu, generics, record, exception và annotation

- `String`, primitive, `List<T>`, `Map<K,V>` và `Optional<T>` là các kiểu thường xuất hiện trong request handler và service. Generics giúp compiler kiểm tra kiểu; `List<String>` biểu diễn tập phần tử khác với `String`.
- `record` (Java 16+, dùng được ở Java 17) thích hợp cho DTO đơn giản: compiler sinh constructor chuẩn, accessor, `equals`, `hashCode` và `toString`. Record ngăn gán lại các thành phần của record, nhưng không tự bảo đảm bất biến sâu nếu chứa collection mutable.
- Checked exception buộc khai báo/xử lý theo quy tắc ngôn ngữ; runtime exception không có yêu cầu đó. Trong ứng dụng web, một exception chưa được xử lý có thể dẫn tới HTTP 500; muốn chuyển thành mã lỗi nghiệp vụ cần cơ chế xử lý tương ứng.
- Annotation như `@Override` là metadata gắn vào khai báo. Spring đọc các annotation như `@Controller`, `@Service`, `@GetMapping` theo cơ chế của framework; viết annotation đơn thuần không làm một class trở thành HTTP endpoint nếu không được Spring đăng ký.
- Phân biệt **tham chiếu**, **giá trị** và **khả năng thay đổi trạng thái**. Java truyền đối số theo giá trị, kể cả khi giá trị đó là tham chiếu đến object; truyền tham chiếu không tạo bản sao độc lập của object.

Ví dụ DTO:

```java
record GreetingRequest(String name) {}
record GreetingResponse(String message) {}
```

Trong request JSON `{"name":"Lan"}`, tên khóa cần khớp cách ánh xạ của bộ chuyển đổi JSON. Đừng mặc định mọi lỗi dữ liệu đầu vào sẽ bị chặn: kiểm tra ràng buộc như chuỗi rỗng, null, độ dài và định dạng là bước riêng của validation.

## 3. IoC và DI trong Spring

### 3.1. IoC là quyền kiểm soát vòng đời; DI là cách truyền dependency

**Inversion of Control (IoC)** nghĩa là code ứng dụng giao cho một cơ chế bên ngoài quyền tạo, cấu hình và ghép các thành phần. Trong Spring, `ApplicationContext` là container quản lý các **bean**. **Dependency Injection (DI)** là cách container cung cấp đối tượng mà bean cần, thường qua constructor.

Không có DI, một service có thể tự `new` dependency cụ thể, khiến việc thay triển khai khó hơn. Với constructor injection, dependency là một yêu cầu rõ ràng của class và có thể cung cấp một fake khi unit test.

Một luồng tạo đối tượng điển hình:

```text
Spring khởi động → quét cấu hình/component → đăng ký bean
             → giải dependency → gọi constructor → bean sẵn sàng phục vụ
```

Các annotation stereotype hay gặp:

| Annotation | Vai trò thường gặp |
| --- | --- |
| `@Component` | Bean Spring tổng quát |
| `@Service` | Bean ở tầng nghiệp vụ; một dạng chuyên biệt của `@Component` |
| `@Repository` | Bean tầng truy cập dữ liệu; hỗ trợ cơ chế chuyển đổi một số exception truy cập dữ liệu |
| `@Controller` | Controller MVC, thường dùng với view |
| `@RestController` | Kết hợp `@Controller` và `@ResponseBody`; kết quả được ghi vào HTTP response body |

Cần có cấu hình đăng ký bean phù hợp (component scan hoặc khai báo `@Bean`). Spring Boot thường cung cấp cấu hình khởi đầu, nhưng vị trí package scan vẫn ảnh hưởng việc tìm thấy class.

### 3.2. Chọn constructor injection

```java
@Service
class GreetingService {
    String greet(String name) {
        return "Xin chào, " + name + "!";
    }
}

@RestController
class GreetingController {
    private final GreetingService greetingService;

    GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/hello")
    String hello(@RequestParam(defaultValue = "bạn") String name) {
        return greetingService.greet(name);
    }
}
```

Đoạn này **minh họa trong ứng dụng Spring MVC**; nó chưa phải chương trình Java độc lập vì phụ thuộc thư viện Spring, việc đăng ký bean và ứng dụng khởi động. Khi class chỉ có một constructor, Spring không yêu cầu `@Autowired` trên constructor đó.

`GreetingController` chỉ biết nó cần `GreetingService`. Trong unit test có thể truyền trực tiếp một service hoặc tách interface và truyền stub/fake. Một bean không được đăng ký sẽ gây lỗi giải dependency khi khởi động; nhiều bean phù hợp cùng một kiểu có thể cần `@Qualifier` hoặc `@Primary`.

### 3.3. Scope và vòng đời ảnh hưởng trực tiếp đến concurrency

**Singleton** là bean scope mặc định: trong một Spring container, thông thường một bean definition sinh ra một object được tái sử dụng. Nó **không đồng nghĩa** với singleton JVM toàn cục và **không đồng nghĩa** thread-safe. Một controller singleton phục vụ nhiều request đồng thời; lưu tên người dùng hoặc bộ đếm chưa đồng bộ vào field của nó có thể gây lỗi.

Scope `prototype` tạo đối tượng mới mỗi lần container được yêu cầu lấy bean theo scope đó; các scope request/session phụ thuộc web context. Đổi scope không phải cách tự động sửa mọi lỗi concurrency. Trạng thái theo từng request nên được truyền bằng tham số, local variable hoặc một abstraction quản lý phạm vi phù hợp.

## 4. HTTP: ngôn ngữ trao đổi giữa client và server

### 4.1. Cấu trúc request và response

Một HTTP request gồm phương thức, đường dẫn/tài nguyên đích, header và có thể có body. Một response gồm status, header và có thể có body. Ví dụ dễ đọc (dòng đầu phản ánh dạng HTTP/1.1; HTTP/2 và HTTP/3 có cách truyền trên đường dây khác):

```http
POST /api/greetings HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json

{"name":"Lan"}
```

```http
HTTP/1.1 201 Created
Content-Type: application/json

{"message":"Xin chào, Lan!"}
```

**URL path** như `/api/greetings/42` thường dùng để định danh tài nguyên; **query parameter** như `?lang=vi&page=2` thường biểu diễn lọc, sắp xếp hoặc phân trang; **header** chứa metadata như `Content-Type` hoặc `Authorization`; **body** mang payload. Cần phân biệt `Content-Type` (kiểu biểu diễn được gửi) với `Accept` (kiểu biểu diễn client mong nhận).

HTTP được thiết kế theo mô hình **stateless ở mức request**: mỗi request cần đủ thông tin để xử lý theo giao thức. Ứng dụng vẫn có thể duy trì trạng thái phiên bằng cookie, session hoặc token; đây là lớp cơ chế bổ sung, không phải tính chất lưu trạng thái sẵn có trong từng request.

### 4.2. Method và thuộc tính ngữ nghĩa

| Method | Ý nghĩa điển hình | Safe? | Idempotent? |
| --- | --- | --- | --- |
| `GET` | Lấy biểu diễn tài nguyên | Có | Có |
| `HEAD` | Lấy metadata response như GET, không có response body | Có | Có |
| `POST` | Yêu cầu xử lý/tạo tài nguyên theo ngữ nghĩa endpoint | Không | Không bảo đảm |
| `PUT` | Tạo hoặc thay thế trạng thái tài nguyên tại URI xác định | Không | Có |
| `PATCH` | Sửa một phần tài nguyên | Không | Không bảo đảm |
| `DELETE` | Xóa liên kết/tài nguyên theo ngữ nghĩa endpoint | Không | Có |

**Safe** nghĩa là client không yêu cầu một thay đổi trạng thái có ý nghĩa; log/metric kỹ thuật vẫn có thể phát sinh. **Idempotent** nghĩa là lặp cùng yêu cầu có cùng hiệu ứng dự định lên trạng thái server; các lần gọi không nhất thiết trả cùng status hoặc cùng response body. Ví dụ DELETE lần đầu có thể trả 204, lần sau trả 404 nhưng hiệu ứng "tài nguyên không còn" không thay đổi.

Các thuộc tính này quyết định cách thiết kế API, retry và cache. Đừng đưa thao tác chuyển tiền/xóa dữ liệu vào GET. Nếu cần chống tạo trùng do client gửi lại POST, API có thể thiết kế khóa idempotency và lưu trạng thái xử lý, thay vì cho rằng POST mặc nhiên idempotent.

### 4.3. Status code quan trọng

| Status | Ý nghĩa thường gặp |
| --- | --- |
| `200 OK` | Request thành công, thường có response body |
| `201 Created` | Đã tạo tài nguyên; có thể kèm header `Location` |
| `204 No Content` | Thành công, không có response body |
| `400 Bad Request` | Request không hợp lệ về cú pháp hoặc đầu vào theo xử lý |
| `401 Unauthorized` | Thiếu/không hợp lệ thông tin xác thực cho tài nguyên cần xác thực |
| `403 Forbidden` | Server hiểu request nhưng từ chối cấp quyền |
| `404 Not Found` | Không tìm thấy tài nguyên/endpoint tương ứng |
| `405 Method Not Allowed` | URI tồn tại nhưng method không được hỗ trợ |
| `409 Conflict` | Request xung đột với trạng thái tài nguyên |
| `415 Unsupported Media Type` | Kiểu nội dung gửi lên không được hỗ trợ |
| `500 Internal Server Error` | Lỗi phía server chưa được ánh xạ phù hợp |

Mã 4xx chủ yếu phản ánh lỗi phía request/client; 5xx phản ánh phía server, nhưng phải xem hoàn cảnh cụ thể. Cấu trúc JSON body không tự quyết định status. Cũng đừng nhầm **401 (authentication)** và **403 (authorization)**.

### 4.4. Điều Spring MVC làm giúp bạn

- `@GetMapping`, `@PostMapping`, `@PutMapping` ánh xạ method/đường dẫn đến handler.
- `@PathVariable` lấy giá trị trong đường dẫn; `@RequestParam` lấy query parameter/form parameter tùy trường hợp; `@RequestBody` yêu cầu chuyển body thành đối tượng.
- `@ResponseBody`/`@RestController` giúp serialize giá trị trả về qua message converter; `ResponseEntity<T>` cho phép điều khiển status, header và body.
- Converter JSON (ví dụ Jackson khi có dependency phù hợp) không thay thế validation hay định nghĩa quy tắc nghiệp vụ.
- DispatcherServlet thực hiện điều phối ở tầng web; service không nên bị buộc hiểu `HttpServletRequest` chỉ để làm một phép tính nghiệp vụ.

HTTPS là HTTP qua kết nối được bảo vệ bằng TLS; sử dụng HTTPS không thay thế xác thực, phân quyền, kiểm tra input hoặc cấu hình CORS. CORS là cơ chế do browser thực thi đối với truy cập khác origin, không phải cơ chế phân quyền API nói chung.

## 5. Xử lý đồng thời trong ứng dụng web

### 5.1. Nhiều request, cùng bean

Ở cấu hình Spring MVC dùng Servlet xử lý đồng bộ thông thường, server sử dụng các luồng thực thi để phục vụ nhiều request cùng lúc. Chi tiết còn tùy container, cấu hình, cơ chế async hoặc virtual threads; không nên giả định rằng mỗi kết nối HTTP mãi gắn với một Java thread.

Giả sử hai request gọi một controller singleton:

```java
@RestController
class UnsafeCounterController {
    private int counter = 0;

    @GetMapping("/unsafe-count")
    int next() {
        return ++counter;
    }
}
```

Đây là **mã minh họa lỗi**. `++counter` không phải thao tác nguyên tử; nhiều thread có thể đọc cùng giá trị rồi ghi đè kết quả, làm mất lượt tăng. Ngoài tính nguyên tử, việc đọc/ghi không có cơ chế đồng bộ còn liên quan đến quy tắc visibility của Java Memory Model. Không chữa bằng cách chỉ thêm `volatile`: `volatile` hỗ trợ visibility và thứ tự nhất định, nhưng không biến `++` thành một phép cập nhật nguyên tử.

Giải pháp cho một bộ đếm trong bộ nhớ, ở mức một JVM:

```java
private final AtomicInteger counter = new AtomicInteger();

int next() {
    return counter.incrementAndGet();
}
```

Cần import `java.util.concurrent.atomic.AtomicInteger`. Đây chỉ là một bộ đếm cục bộ. Nhiều instance ứng dụng hoặc yêu cầu cập nhật nhiều bản ghi cần cơ chế nhất quán ở tầng dữ liệu, chẳng hạn atomic database update/transaction. AtomicInteger không giải quyết tự động một giao dịch gồm nhiều bước.

### 5.2. Ví dụ Java 17+ chạy độc lập và có output xác định

File `ConcurrentCounterDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentCounterDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(4);

        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 1_000; i++) {
                results.add(pool.submit(() -> {
                    requests.incrementAndGet();
                }));
            }
            for (Future<?> result : results) {
                result.get();
            }
            System.out.println("Tổng yêu cầu: " + requests.get());
        } finally {
            pool.shutdown();
        }
    }
}
```

Chạy:

```bash
javac ConcurrentCounterDemo.java
java ConcurrentCounterDemo
```

Output:

```text
Tổng yêu cầu: 1000
```

`ExecutorService` điều phối 1.000 tác vụ trên pool 4 thread; `Future.get()` đợi tác vụ hoàn tất hoặc ném exception nếu tác vụ thất bại; `AtomicInteger.incrementAndGet()` cập nhật nguyên tử nên kết quả là 1.000. Ví dụ dùng pool cố định để mô phỏng các lời gọi đồng thời, **không có nghĩa** Spring MVC luôn dùng đúng cấu hình pool này.

Nếu đổi sang `int` và `requests++` không đồng bộ, kết quả có thể thấp hơn 1.000; tuy nhiên **không được lấy một lần chạy tình cờ ra 1.000 làm bằng chứng code an toàn**. Race condition phụ thuộc lịch chạy nên rất khó tái hiện ổn định.

### 5.3. Quy tắc an toàn khi viết controller/service

- Ưu tiên bean **stateless**: mỗi request đưa input vào method và nhận kết quả; biến cục bộ được tạo cho mỗi lời gọi.
- Với trạng thái phải chia sẻ, xác định phạm vi: một request, một JVM, nhiều instance ứng dụng hay database. Dùng cấu trúc dữ liệu thread-safe, lock hoặc giao dịch phù hợp với phạm vi đó.
- Bất biến và `final` giúp giảm rủi ro nhưng không thay thế thiết kế đồng bộ cho đối tượng mutable được chia sẻ.
- Một số API như `SimpleDateFormat` mutable không được thiết kế dùng chung an toàn; ưu tiên API `java.time` immutable như `DateTimeFormatter` khi phù hợp.
- Blocking I/O chờ database/HTTP ngoài sẽ giữ tài nguyên thực thi trong mô hình đồng bộ. Timeout, giới hạn connection pool, giới hạn tải và quan sát độ trễ quan trọng hơn việc tự thêm `new Thread()` cho từng request.
- Không lưu thông tin request/người dùng vào static field hoặc field singleton. Với `ThreadLocal`, phải hiểu luồng có thể được tái sử dụng và cần cleanup đúng vòng đời; truyền dữ liệu rõ ràng thường dễ kiểm soát hơn.
- **Spring MVC** và **Spring WebFlux** có mô hình thực thi khác nhau; reactive/non-blocking không làm mã nghiệp vụ có mutable shared state tự nhiên trở nên an toàn.

## 6. Ghép bốn kiến thức trong ví dụ Spring MVC

Ví dụ sau cần một **dự án Spring Boot 3.x / Spring Framework 6.x với dependency `spring-boot-starter-web`**, JDK 17+ và build tool đã cấu hình. Các file dưới đây là mã ứng dụng trong project đó, không phải chương trình chạy bằng `javac` thuần nếu chưa có thư viện Spring. Đặt ba file trong `src/main/java/demo/web/`.

`WebPrerequisitesApplication.java`:

```java
package demo.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebPrerequisitesApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebPrerequisitesApplication.class, args);
    }
}
```

`GreetingService.java`:

```java
package demo.web;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    public String greet(String name) {
        return "Xin chào, " + name + "!";
    }
}
```

`GreetingController.java`:

```java
package demo.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {
    private final GreetingService service;

    public GreetingController(GreetingService service) {
        this.service = service;
    }

    @GetMapping
    public GreetingResponse read(
            @RequestParam(defaultValue = "bạn") String name) {
        return new GreetingResponse(service.greet(name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GreetingResponse create(@RequestBody GreetingRequest request) {
        return new GreetingResponse(service.greet(request.name()));
    }

    public record GreetingRequest(String name) {}
    public record GreetingResponse(String message) {}
}
```

Với ứng dụng đã khởi động tại localhost:8080, thử:

```bash
curl -i 'http://localhost:8080/api/greetings?name=Lan'
curl -i -X POST 'http://localhost:8080/api/greetings' -H 'Content-Type: application/json' -d '{"name":"Mai"}'
```

Phản hồi GET có status **200** và JSON tương đương `{"message":"Xin chào, Lan!"}`; POST có status **201** và JSON tương đương `{"message":"Xin chào, Mai!"}`. Header thực tế và cách biểu diễn ký tự JSON có thể thay đổi theo môi trường.

Phân tích từng bước:

1. Spring Boot đăng ký `GreetingService` và `GreetingController` nhờ component scanning trong cùng package gốc `demo.web` (**IoC**).
2. Container truyền một `GreetingService` vào constructor của controller (**DI**, Java constructor và `final` field).
3. DispatcherServlet tìm mapping theo path và HTTP method; `@RequestParam` lấy query trên GET, `@RequestBody` chuyển JSON sang Java record trên POST (**HTTP + Java**).
4. Controller gọi method nghiệp vụ và trả một DTO record; Spring serialize thành JSON và gán status (**HTTP response**).
5. Mỗi request tạo dữ liệu local riêng trong method; service không lưu name vào field, nên phần logic này không có shared mutable state do ứng dụng tự tạo (**concurrency**).

Ví dụ cố ý đơn giản: không lưu tài nguyên thực, không chứng minh ý nghĩa "create" của POST trong API production, không xác thực/ủy quyền, không có validation và không xử lý lỗi tùy chỉnh. Nếu nhận `{"name":null}`, logic ví dụ có thể tạo lời chào với chữ `null`; cần bổ sung ràng buộc đầu vào trong bài về validation. Nếu xây API tạo tài nguyên thật, thường cần định danh tài nguyên, trạng thái bền vững và cân nhắc header `Location`.

## 7. Lỗi tư duy phổ biến và cách kiểm tra

| Ngộ nhận | Cách hiểu đúng |
| --- | --- |
| "Có `@Service` là tự được tiêm ở mọi nơi" | Bean phải nằm trong cấu hình/scan của context; dependency phải giải được |
| "Singleton tương đương thread-safe" | Scope chỉ nói về số instance; field mutable vẫn có race |
| "Field `final` làm cả object bất biến" | `final` chỉ cấm gán lại tham chiếu field; object bên trong có thể mutable |
| "GET và POST chỉ khác tên method" | Ngữ nghĩa safe, idempotent, cache và retry khác nhau |
| "HTTP stateless nghĩa là website không thể có login session" | Cookie/session/token bổ sung cơ chế giữ/xác minh trạng thái |
| "Trả về Java record tức tự có JSON và HTTP 200 trong mọi tình huống" | Cần Spring MVC/message converter, mapping và quy tắc chọn status |
| "Một request luôn có một thread riêng bất biến" | Phụ thuộc container, cấu hình, async và mô hình thực thi |
| "Dùng `volatile int` thì `++` an toàn" | `++` là thao tác đọc-sửa-ghi; cần atomic/lock phù hợp |
| "Chạy test concurrency một lần đúng kết quả là đủ" | Race có thể không xuất hiện ở lần chạy đó; cần thiết kế an toàn và test thích hợp |

Khi debug một endpoint, hãy truy lần lượt: **HTTP method/URL → handler mapping → binding/deserialize → dependency service → hành vi nghiệp vụ → xử lý exception → serialize/status → trạng thái dùng chung giữa request**. Cách lần theo này thường khoanh vùng lỗi nhanh hơn việc thử thay annotation ngẫu nhiên.

## 8. Tự kiểm tra trước khi học Spring Web sâu hơn

1. Nếu `WelcomeService` tự tạo `new VietnameseFormatter()` thay vì nhận `Formatter` từ constructor, khả năng thay implementation khi test thay đổi ra sao?
2. Vì sao `@RestController` có thể là singleton mà vẫn phục vụ nhiều người dùng đúng cách? Điều kiện nào làm nó nguy hiểm?
3. Hãy chỉ ra method, path, header và body trong ví dụ POST. Vì sao `Content-Type` và `Accept` không tương đương?
4. Vì sao cùng một DELETE có thể lần đầu trả 204, lần sau trả 404 mà vẫn giữ thuộc tính idempotent?
5. Nếu hai request đồng thời gọi `++counter`, thao tác nào có thể mất lượt tăng? Tại sao `AtomicInteger` chỉ giải quyết bộ đếm trong một JVM?
6. Trong ví dụ Spring, phần nào do Java cung cấp, phần nào do Spring container, phần nào do HTTP và phần nào phụ thuộc thiết kế concurrency?

**Đạt yêu cầu đầu vào** khi bạn có thể tự giải thích luồng request đi vào controller, đọc một constructor injection, phân biệt dữ liệu theo request với state chia sẻ, chọn HTTP method/status phù hợp và phát hiện field mutable nguy hiểm trong singleton. Khi đó các chủ đề tiếp theo như routing, data binding, validation, exception handling, filter/interceptor và security sẽ có nền tảng rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-overview-005">Chọn Servlet/MVC hoặc WebFlux theo chuỗi I/O và nhu cầu ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn Servlet/MVC hay WebFlux theo chuỗi I/O và nhu cầu ứng dụng

## 1. Mục tiêu và nguyên tắc chọn

Khi xây dựng ứng dụng web với Spring Framework, lựa chọn chính thường là **Spring Web MVC (Servlet stack)** hoặc **Spring WebFlux (reactive stack)**. Cả hai đều xây dựng REST API, xác thực, xử lý lỗi, trả JSON và phục vụ nhiều loại ứng dụng. Câu hỏi quan trọng không phải “framework nào hiện đại hơn?”, mà là:

1. **Toàn bộ đường đi của một request thực hiện những I/O nào?** Chẳng hạn đọc database, gọi HTTP, gọi Redis, gửi message, đọc file.
2. **Các thư viện ở từng chặng có thực sự hỗ trợ non-blocking không?**
3. **Ứng dụng cần tối ưu điều gì?** Khả năng bảo trì, số request đồng thời, tiêu thụ thread/memory, streaming, độ trễ cuối đuôi (p95/p99), hay khả năng tận dụng code hiện có.
4. **Đội ngũ có khả năng vận hành và gỡ lỗi mô hình reactive đến đâu?**

**Quy tắc xuất phát:** nếu phần lớn nghiệp vụ dùng thư viện đồng bộ như JDBC/JPA và Spring MVC đang đáp ứng tốt yêu cầu, ưu tiên MVC. Nếu ứng dụng xử lý nhiều I/O chờ lâu, có thể duy trì chuỗi non-blocking từ HTTP vào đến các hệ thống phụ thuộc và cần độ đồng thời lớn, WebFlux là ứng viên đáng thử nghiệm. Quyết định cuối cùng cần được kiểm chứng bằng tải thực tế.

## 2. Hiểu đúng blocking, asynchronous, non-blocking và reactive

- **Blocking I/O:** thread gọi một tác vụ và phải chờ kết quả trước khi tiếp tục. Ví dụ JDBC `repository.findById(id)`, lệnh gọi HTTP đồng bộ `RestClient.retrieve().body(...)`.
- **Asynchronous:** tác vụ hoàn thành ở thời điểm sau; caller có thể nhận `Future`, callback hoặc một đối tượng đại diện cho kết quả. “Async” không tự bảo đảm bên dưới không có thread đang chờ blocking.
- **Non-blocking I/O:** khi dữ liệu chưa sẵn sàng, thread không phải đứng chờ thao tác I/O đó; runtime nhận thông báo hoặc tín hiệu khi có thể tiếp tục xử lý.
- **Reactive programming:** mô tả pipeline dữ liệu/sự kiện cùng tín hiệu hoàn thành và lỗi, thường biểu diễn bằng `Mono<T>` (0 hoặc 1 kết quả) và `Flux<T>` (0 đến nhiều kết quả) trong Project Reactor.
- **Backpressure:** bên tiêu thụ có thể báo khả năng nhận dữ liệu cho publisher trong một pipeline hỗ trợ Reactive Streams. Backpressure không đồng nghĩa tự động giới hạn mọi HTTP request đến hoặc kiểm soát mọi hệ thống ngoại vi.

Lưu ý hai hiểu nhầm thường gặp:

- Có `Mono`/`Flux` **không có nghĩa** mọi chặng là non-blocking. `Mono.just(jdbcRepository.findById(id))` vẫn gọi JDBC **ngay lúc tạo Mono**, trên thread hiện tại.
- WebFlux **không mặc nhiên xử lý một request nhanh hơn** MVC. Ưu điểm chính của non-blocking là có thể phục vụ nhiều kết nối đang chờ I/O với ít thread hơn trong điều kiện phù hợp. CPU, database, connection pool và downstream vẫn đặt giới hạn.

## 3. Hai execution model nhìn từ một request

### Spring MVC / Servlet

Luồng điển hình khi controller gọi thư viện đồng bộ:

~~~text
HTTP request
  → Servlet container (ví dụ Tomcat)
  → Filter → DispatcherServlet → @Controller
  → Service → JDBC/JPA hoặc HTTP client đồng bộ
                         [thread chờ I/O]
  ← Service ← kết quả
  → Serialize JSON → HTTP response
~~~

Trong đường đi đồng bộ thông thường, một thread xử lý request bận chờ ở lời gọi blocking. Servlet container dùng thread pool để tiếp nhận nhiều request đồng thời. Nếu I/O chậm và lưu lượng lớn, request có thể phải xếp hàng chờ thread.

**MVC cũng hỗ trợ bất đồng bộ:** `Callable`, `DeferredResult`, `WebAsyncTask`, trả reactive type, streaming và SSE. Cơ chế Servlet async có thể giải phóng thread tiếp nhận request trước khi kết quả sẵn sàng. Vì vậy không nên đồng nhất “MVC” với “mọi request luôn giữ một platform thread đến cuối”; cần nhìn vào code và executor thực tế.

### Spring WebFlux

Luồng điển hình khi **các chặng thật sự non-blocking**:

~~~text
HTTP request
  → WebFlux runtime / event loop
  → WebFilter → DispatcherHandler → Controller trả Mono/Flux
  → WebClient / reactive repository: đăng ký I/O, chưa có dữ liệu thì trả thread
  → I/O sẵn sàng: tiếp tục pipeline qua tín hiệu
  → Encode dữ liệu → HTTP response non-blocking
~~~

Event loop có thể điều phối nhiều kết nối; nó **phải tránh bị chặn** bởi `Thread.sleep`, `Future.get`, `block()`, JDBC/JPA đồng bộ hoặc xử lý CPU kéo dài. Khi một event-loop thread bị chặn, nhiều request cùng dùng thread đó có thể bị chậm.

**Tên web server không quyết định mô hình:** MVC xây dựng trên Servlet API và thường chạy Tomcat/Jetty; WebFlux mặc định thường dùng Reactor Netty khi khởi tạo qua Spring Boot, nhưng cũng có thể chạy với các server hỗ trợ khác. “Chạy trên Tomcat” không tự suy ra MVC; phải xem web stack và runtime configuration.

## 4. Phân tích **toàn chuỗi I/O** trước khi chọn

Hãy vẽ một request đại diện, kể cả các hệ thống mà ứng dụng đang dùng:

~~~text
Client
  → HTTP server
  → Authentication/filter
  → Controller
  → Service
      ├─ SQL database / transaction
      ├─ External HTTP services
      ├─ Cache
      └─ Messaging / file storage
  → Serialize / stream response
  → Client
~~~

Phân loại từng chặng theo các câu hỏi sau:

| Chặng | Cách thường gặp có thể block | Lựa chọn non-blocking khi phù hợp |
| --- | --- | --- |
| HTTP server | Servlet handler xử lý đồng bộ | WebFlux event-loop/non-blocking transport |
| Gọi REST/HTTP ngoài | `RestClient`, `RestTemplate` sử dụng đồng bộ | `WebClient` với connector hỗ trợ non-blocking |
| SQL | JDBC, JPA/Hibernate ORM truyền thống | R2DBC với driver hỗ trợ và reactive repository |
| NoSQL/cache | Driver/template đồng bộ | Reactive driver/template tương ứng, nếu có |
| File/SDK bên thứ ba | SDK sync, `InputStream.read` | API async/non-blocking thực sự; phải xác minh SDK cụ thể |
| CPU xử lý JSON, mã hóa, nén | Không phải I/O; vẫn tiêu tốn CPU | Tối ưu thuật toán, điều phối CPU có giới hạn; reactive không làm CPU nhanh hơn |
| Ghi response | MVC có hỗ trợ async nhưng một số thao tác ghi vẫn blocking | WebFlux sử dụng non-blocking I/O |

**Điểm nghẽn quyết định:** nếu controller trả `Mono` nhưng service vẫn gọi JDBC đồng bộ, sự chờ đợi chưa biến mất. WebFlux khi đó vừa gánh chi phí mô hình reactive, vừa cần tài nguyên cho blocking calls. Ngược lại, ứng dụng MVC vẫn dùng được `WebClient` để phối hợp các lời gọi HTTP bất đồng bộ mà không phải chuyển toàn bộ server sang WebFlux.

Khi rà soát chuỗi I/O, ghi rõ cho từng dependency: API đang dùng; thread nào thực thi; thời gian chờ; timeout; giới hạn connection pool; chiến lược retry; khả năng hủy yêu cầu; và trạng thái giao dịch. Nếu thiếu thông tin về một SDK, hãy đo hoặc kiểm tra tài liệu của SDK thay vì suy đoán từ tên method có chữ “async”.

## 5. Khi nào chọn Spring MVC?

MVC thường phù hợp khi:

1. **CRUD, business application, quản trị nội bộ** với Spring Data JPA/Hibernate, JDBC hoặc các SDK đồng bộ chiếm phần lớn logic.
2. Luồng nghiệp vụ có giao dịch quan hệ và cách lập trình tuần tự dễ đọc, dễ debug; đội ngũ đã quen với `@Transactional` trong mô hình imperative.
3. Hiện trạng đạt SLA về latency/throughput với số thread, connection pool và chi phí hạ tầng chấp nhận được.
4. Hệ sinh thái thư viện, Servlet filter, interceptor, bảo mật hoặc tích hợp hiện có phù hợp với MVC.
5. Chỉ có vài lời gọi HTTP ra ngoài: có thể tối ưu hoặc gọi song song từng bước khi cần, không nhất thiết đổi web stack.

**Ví dụ tình huống:** tạo đơn hàng cần đọc và cập nhật các bảng qua JPA trong một transaction. JDBC/JPA vẫn blocking; MVC là nền tảng rõ ràng để triển khai nghiệp vụ, quản lý transaction, validation và xử lý lỗi. Không nên chuyển sang WebFlux chỉ vì muốn method trả `Mono<Order>`.

Các tối ưu nên xét trước khi đổi framework: index/query database, batch query, timeout, kích thước JDBC pool, giới hạn kết nối downstream, tránh N+1 query, cache hợp lý và đo p95/p99.

## 6. Khi nào WebFlux đáng cân nhắc?

WebFlux có lợi thế tiềm năng khi đồng thời có nhiều đặc điểm sau:

1. **Nhiều request đồng thời chờ I/O mạng**: API gateway/BFF tổng hợp nhiều upstream, các dịch vụ chat/notification hoặc các endpoint giữ kết nối lâu.
2. HTTP client, persistence, cache và adapter cần thiết có **API reactive/non-blocking thật sự**, hoặc phần blocking được cô lập rất ít và có giới hạn.
3. Có nhu cầu streaming `Flux`/SSE hoặc xử lý luồng dữ liệu lớn theo cách reactive; cân nhắc flow control và việc client đọc chậm.
4. Tài nguyên thread/memory hoặc tính ổn định dưới tải chờ I/O là vấn đề đã đo được.
5. Nhóm có kinh nghiệm Reactor: hiểu cold publisher, subscription, scheduler, error propagation, context và testing bất đồng bộ.

**Ví dụ tình huống:** dịch vụ BFF gọi đồng thời ba API độc lập có độ trễ mạng biến động, sau đó ghép kết quả. `WebClient` + `Mono.zip` mô tả tổ hợp các kết quả mà không giữ một thread để chờ từng socket. Với nhiều kết nối in-flight, mô hình này có thể tiết kiệm tài nguyên thread; lợi ích thực tế phụ thuộc vào giới hạn upstream và kết quả benchmark.

Không chọn WebFlux chỉ vì có endpoint WebSocket/SSE: MVC cũng hỗ trợ nhiều kiểu bất đồng bộ và streaming. Cần đánh giá quy mô, flow control, hạ tầng và thư viện phù hợp.

## 7. Ví dụ đối chiếu: cùng một bài toán gọi hai upstream

Giả sử endpoint `GET /profiles/{id}` cần lấy `GET /users/{id}` và `GET /scores/{id}` từ dịch vụ khác ở `http://localhost:8081`. Hai dịch vụ upstream độc lập và trả JSON:

~~~json
{"name":"Lan"}
~~~

~~~json
{"value":87}
~~~

Các ví dụ sau dành cho **hai ứng dụng Spring Boot 3.x độc lập**, dùng Spring Framework 6.1+ và Java 17+. Để chạy end-to-end cần có upstream giả lập ở cổng 8081 với đúng hai API này. Nếu upstream không hoạt động, request sẽ thất bại do kết nối; **không có stdout hay kết quả timing cố định** được giả định. Mỗi dự án cần Spring Boot Gradle plugin và dependency management thông thường.

### Phương án A — Spring MVC + HTTP client đồng bộ

Trong dự án MVC, khai báo dependency:

~~~groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
~~~

Tạo `src/main/java/example/mvc/MvcApp.java`:

~~~java
package example.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class MvcApp {
    public static void main(String[] args) {
        SpringApplication.run(MvcApp.class, args);
    }

    @RestController
    static class ProfileController {
        private final RestClient client = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .build();

        @GetMapping("/profiles/{id}")
        Profile find(@PathVariable long id) {
            User user = client.get().uri("/users/{id}", id)
                    .retrieve().body(User.class);
            Score score = client.get().uri("/scores/{id}", id)
                    .retrieve().body(Score.class);

            if (user == null || score == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
            }
            return new Profile(user.name(), score.value());
        }
    }

    record User(String name) {}
    record Score(int value) {}
    record Profile(String name, int score) {}
}
~~~

Ở ví dụ này, hai lời gọi `RestClient` lần lượt chờ kết quả trên thread xử lý; thời gian của đường HTTP gần với tổng thời gian chờ hai upstream (cộng chi phí xử lý khác). Đây là một cách triển khai dễ đọc, phù hợp khi ứng dụng dùng các dependency đồng bộ. Không kết luận rằng **mọi** ứng dụng MVC bắt buộc gọi tuần tự như đoạn code này.

### Phương án B — WebFlux + WebClient non-blocking

Trong **dự án riêng** cho WebFlux, dùng dependency:

~~~groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
~~~

Tạo `src/main/java/example/webflux/WebFluxApp.java`:

~~~java
package example.webflux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class WebFluxApp {
    public static void main(String[] args) {
        SpringApplication.run(WebFluxApp.class, args);
    }

    @RestController
    static class ProfileController {
        private final WebClient client = WebClient.builder()
                .baseUrl("http://localhost:8081")
                .build();

        @GetMapping("/profiles/{id}")
        Mono<Profile> find(@PathVariable long id) {
            Mono<User> user = client.get().uri("/users/{id}", id)
                    .retrieve().bodyToMono(User.class)
                    .switchIfEmpty(Mono.error(
                            new ResponseStatusException(HttpStatus.BAD_GATEWAY)));
            Mono<Score> score = client.get().uri("/scores/{id}", id)
                    .retrieve().bodyToMono(Score.class)
                    .switchIfEmpty(Mono.error(
                            new ResponseStatusException(HttpStatus.BAD_GATEWAY)));

            return Mono.zip(user, score)
                    .map(pair -> new Profile(
                            pair.getT1().name(), pair.getT2().value()));
        }
    }

    record User(String name) {}
    record Score(int value) {}
    record Profile(String name, int score) {}
}
~~~

`Mono.zip(user, score)` đăng ký hai publisher và chờ **cả hai** hoàn thành; các HTTP request có thể được xử lý đồng thời bằng connector non-blocking. Do đó, phần chờ mạng thường gần với nhánh chậm hơn (cộng overhead), nếu hai upstream thực sự độc lập. `retrieve()` mặc định chuyển các HTTP 4xx/5xx thành tín hiệu lỗi; ứng dụng thật cần mapping lỗi, timeout, cancellation và giới hạn downstream rõ ràng.

Với hai dự án trên, chạy ứng dụng bằng `./gradlew bootRun` (Windows: `gradlew.bat bootRun`), sau khi cấu hình Boot project và dịch vụ upstream. Gọi thử:

~~~bash
curl http://localhost:8080/profiles/42
~~~

Nếu upstream đáp ứng các JSON giả định, response body dự kiến của **cả hai** ứng dụng:

~~~json
{"name":"Lan","score":87}
~~~

Lưu ý: ví dụ minh họa **cách gọi tuần tự so với phối hợp async**, không phải một phép benchmark công bằng. MVC cũng có thể sử dụng `WebClient` hoặc executor để gọi song song; WebFlux có thể bị chậm nếu một bước vẫn blocking. Không so hiệu năng bằng cách chạy đơn lẻ hai đoạn này rồi suy ra kết luận chung.

## 8. Ba kiến trúc thường gặp và cách quyết định

| Tình huống | Lựa chọn khởi đầu hợp lý | Vì sao |
| --- | --- | --- |
| REST CRUD + JPA/JDBC + transaction nghiệp vụ | MVC | Persistence vẫn blocking; imperative dễ tích hợp và bảo trì |
| MVC hiện hữu cần gọi nhiều HTTP upstream | Giữ MVC, cân nhắc `WebClient`/async ở chặng HTTP | Tối ưu chặng chờ mạng trước khi cân nhắc migration toàn stack |
| Gateway/BFF nặng I/O, downstream non-blocking, lượng kết nối chờ lớn | Thử WebFlux và benchmark | Tối ưu thread khi phần lớn thời gian là chờ I/O |
| Reactive driver ở database nhưng các SDK then chốt vẫn sync | Đánh giá từng chặng; chưa đủ căn cứ đổi toàn ứng dụng | Blocking vẫn cần thread; chi phí cô lập blocking có thể đáng kể |
| Xử lý CPU nặng, mã hóa lớn, image/video processing | Tập trung kiến trúc worker, pool CPU và queue | Reactive không thêm CPU; phải tránh chiếm event loop |
| SSE/streaming với số client vừa phải | Cả MVC và WebFlux đều có lựa chọn | Quy mô, backpressure và vận hành mới quyết định |
| Dự án đang chạy MVC ổn định, chưa có số đo chứng minh vấn đề | Tiếp tục MVC | Migration mang chi phí và rủi ro không có lợi ích chắc chắn |

### Quy trình quyết định thực tế

1. **Chọn một luồng nghiệp vụ thật** (ví dụ tạo đơn hàng, truy xuất dashboard, gom dữ liệu cho màn hình).
2. **Vẽ I/O chain và đánh dấu mỗi lời gọi blocking/non-blocking**; xác nhận đến driver và connector thật, không dừng ở controller signature.
3. **Đặt yêu cầu tải cụ thể**: requests/giây, concurrent connections, độ trễ trung bình và p95/p99, tốc độ phản hồi downstream, memory budget, CPU budget.
4. **Xác định nút thắt trước**: thread pool, database connection pool, HTTP connection pool, query chậm, rate limit hoặc CPU.
5. **Chọn giải pháp nhỏ nhất xử lý được nút thắt**: tối ưu DB, timeout, bulkhead, caching, dùng async HTTP client, chỉnh pool, virtual threads hoặc WebFlux.
6. **Benchmark cùng nghiệp vụ, data, hạ tầng và profile tải**. Chỉ quyết định migration sau khi có bằng chứng.

## 9. Nếu WebFlux cần gọi thư viện blocking thì sao?

Không gọi trực tiếp hàm JDBC/SDK đồng bộ trên event loop, và không `block()` giữa reactive request pipeline. Một đường thoát tạm thời là chạy lời gọi blocking trên scheduler dành cho tác vụ blocking:

~~~java
Mono<Customer> customerMono = Mono.fromCallable(
        () -> blockingCustomerClient.findById(customerId))
    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
~~~

Đây là **đoạn minh họa đặt bên trong một service đã có** biến `blockingCustomerClient` và `customerId`; không phải một chương trình Java độc lập. `fromCallable` trì hoãn lời gọi tới khi có subscription; `subscribeOn(boundedElastic())` chuyển công việc blocking ra khỏi event loop.

**Giới hạn:** blocking vẫn xảy ra trên thread khác; boundedElastic có giới hạn tài nguyên và hàng chờ, không khiến SDK biến thành non-blocking. Phải cấu hình timeout, concurrency, connection pool và theo dõi saturation. Nếu đa số pipeline là JPA/JDBC blocking, MVC thường là lựa chọn đơn giản hơn.

Ngược lại, đừng thay code này thành `Mono.just(blockingCustomerClient.findById(customerId))` vì lời gọi blocking sẽ thực thi ngay trên thread tạo publisher.

## 10. Transaction, context, xử lý lỗi và kiểm thử

- **Transaction:** mô hình imperative JDBC/JPA phổ biến dùng transaction gắn với thread; reactive persistence cần transaction manager hỗ trợ reactive và context của Reactor. `@Transactional` không tự biến JDBC thành reactive; không thể giả định một transaction thread-local xuyên suốt mọi callback reactive. Khi chuyển mô hình cần thiết kế lại ranh giới transaction và cách kết hợp DB calls.
- **Context:** code MVC thường dễ dựa vào `ThreadLocal` trong phạm vi request; reactive chain có thể đổi thread, nên thông tin tracing/security/request phải được truyền bằng cơ chế context tương thích (ví dụ Reactor Context và tích hợp của framework). Tránh tự suy ra mọi `ThreadLocal` luôn tồn tại.
- **Lỗi:** MVC thường xử lý exception bằng `@ExceptionHandler`/`@ControllerAdvice`; WebFlux cần hiểu cả exception trước khi tạo publisher và tín hiệu lỗi phát sau subscription (`onErrorResume`, error handler). Quy định rõ HTTP status, timeout, retry và chính sách khi upstream lỗi một phần.
- **Retry:** retry không điều kiện dễ khuếch đại tải hoặc lặp thao tác không idempotent; chỉ retry lỗi phù hợp, có backoff và giới hạn tổng thời gian.
- **Kiểm thử:** MVC thường dùng `MockMvc`; WebFlux thường dùng `WebTestClient`, `StepVerifier` cho publisher và virtual time khi cần. Cả hai cần integration test cho timeout/streaming/slow client và các adapter bên ngoài.
- **Observability:** đo per-endpoint latency, active threads/event-loop blocking, connection-pool utilization, request queue, GC, CPU, memory, số request đang chờ, error rate và độ trễ dependency. Kết quả đo quan trọng hơn số lượng annotation reactive.

## 11. Virtual threads có làm WebFlux không còn cần thiết?

Với **Java 21+**, virtual threads có thể giúp mô hình imperative xử lý nhiều tác vụ chờ I/O với chi phí thread thấp hơn platform threads trong những điều kiện phù hợp. Với phiên bản Spring Boot hỗ trợ, có thể cấu hình:

~~~properties
spring.threads.virtual.enabled=true
~~~

Điều này khiến MVC với code đồng bộ trở thành phương án cần benchmark thêm, nhất là khi đang dùng JDBC/JPA. Tuy nhiên:

- I/O vẫn **blocking về mặt API**; driver và connection pool không được tăng khả năng xử lý vô hạn.
- Virtual threads không khiến CPU-heavy task nhanh hơn; vẫn phải kiểm soát số operation đang chạy.
- Một số đoạn code/khóa/native interaction có thể hạn chế hiệu quả; cần theo dõi dấu hiệu pinning theo JDK và phiên bản đang dùng.
- WebFlux vẫn cung cấp mô hình reactive có backpressure, tổ hợp pipeline non-blocking và streaming; hai hướng có trade-off khác nhau.

Vì vậy không được kết luận “có virtual threads thì luôn chọn MVC” hoặc “có WebFlux thì không bao giờ cần virtual threads”. Đo đúng hệ thống của mình, gồm cả throughput và p95/p99.

## 12. Các lỗi chọn kiến trúc và triển khai thường gặp

| Sai lầm | Tác hại | Cách sửa |
| --- | --- | --- |
| Chọn WebFlux vì nghe “nhanh hơn” | Tăng độ phức tạp khi bottleneck nằm ở DB/CPU | Xác định I/O chain và benchmark |
| Controller trả `Mono` nhưng gọi JPA trực tiếp | Chặn event loop hoặc dời blocking sang chỗ khác | Chọn MVC, hoặc cô lập blocking tạm thời với pool giới hạn |
| Gọi `block()` trong controller WebFlux | Chặn hoặc gây lỗi trên non-blocking thread | Trả publisher và compose bằng operator |
| Trộn cả hai starter rồi đoán đang chạy WebFlux | Cấu hình server stack khác mong đợi | Kiểm tra Boot auto-configuration, startup log và loại ứng dụng |
| Cho `flatMap` concurrency quá cao | Dồn tải lên downstream/connection pool | Đặt giới hạn concurrency và timeout hợp lý |
| Dùng `WebClient` rồi gọi `.block()` mọi nơi | Không tận dụng async tại chặng gọi | Dùng pipeline async khi phù hợp; nếu nghiệp vụ imperative, hiểu rõ điểm chặn |
| Không đặt timeout/cancel/retry policy | Request chờ vô hạn, queue phình, lỗi dây chuyền | Đặt deadline, retry có điều kiện, backoff và đo saturation |
| Benchmark chỉ một request | Không thấy throughput, queue, tail latency | Test theo tải và số kết nối đồng thời thực tế |
| Chuyển toàn bộ MVC sang WebFlux dù còn nhiều SDK sync | Chi phí migration lớn, ít lợi ích | Ưu tiên tối ưu chặng gây nghẽn trước |

**Chú ý về Spring Boot:** khi thêm cả `spring-boot-starter-web` và `spring-boot-starter-webflux` trong cùng ứng dụng Boot 3.x, auto-configuration web server thông thường ưu tiên **MVC** (nhiều dự án MVC thêm WebFlux starter chỉ để dùng `WebClient`). Nếu muốn một WebFlux server đúng nghĩa, cấu hình rõ mô hình ứng dụng và dependency; không nhận diện qua việc có `Mono` trong chữ ký method.

## 13. Bài tập tự đánh giá

Hãy chọn phương án xuất phát và giải thích theo I/O chain:

1. **Hệ thống bán hàng:** `Controller → Service → JPA transaction → trả JSON`. Tải vừa phải và p95 đáp ứng mục tiêu.
2. **BFF dashboard:** `Controller → 4 HTTP API độc lập → ghép response`, mỗi API có độ trễ mạng đáng kể, số kết nối đồng thời lớn.
3. **Ứng dụng MVC hiện hữu:** một endpoint gọi hai HTTP service chậm, còn phần lớn nghiệp vụ dùng JPA.
4. **WebFlux endpoint:** controller gọi SDK đọc file blocking trong `map(...)` trên event loop.

**Gợi ý đáp án:** (1) MVC, đo SQL và transaction trước. (2) Thử WebFlux + WebClient khi các chặng non-blocking và benchmark chứng minh hiệu quả; hoặc thử client async trong MVC nếu đó là kiến trúc hiện có. (3) Giữ MVC, cải thiện riêng bước HTTP (cân nhắc WebClient/async) trước khi migration. (4) Không giữ blocking trong event loop: thay SDK bằng API phù hợp hoặc offload với `boundedElastic` có kiểm soát; nếu kiến trúc chủ yếu blocking, đánh giá lại MVC.

## 14. Checklist trước khi chốt lựa chọn

- [ ] Đã vẽ request path từ HTTP đến database, HTTP client, cache, message, file và response.
- [ ] Đã xác minh dependency/driver nào blocking và nào non-blocking.
- [ ] Đã xác định yêu cầu concurrency, throughput, p95/p99, memory và budget.
- [ ] Đã kiểm tra giới hạn database/HTTP connection pool, thread pool và downstream.
- [ ] Đã cân nhắc MVC async/WebClient hoặc virtual threads trước khi migration lớn.
- [ ] Với WebFlux, không có blocking bất ngờ trên event loop; có concurrency limit, timeout và xử lý lỗi.
- [ ] Có test phù hợp cho streaming, slow consumer, timeout, cancellation và retry.
- [ ] Có benchmark trên workload đại diện và số liệu observability để bảo vệ quyết định.

**Kết luận vận dụng:** lựa chọn web stack từ **chuỗi I/O thực tế và mục tiêu vận hành**. MVC là mặc định hợp lý cho nhiều hệ thống imperative/JPA; WebFlux đáng giá khi ứng dụng được thiết kế xuyên suốt cho non-blocking, cần nhiều I/O đồng thời và nhóm có năng lực vận hành reactive. Cả hai đều là công cụ hợp lệ; kết luận phải được củng cố bằng đo lường.

### Tài liệu tham khảo chính thức

- [Spring Framework — Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html)
- [Spring Framework — WebFlux và tiêu chí lựa chọn](https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html)
- [Spring Framework — MVC asynchronous requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
- [Spring Framework — WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [Spring Boot — Virtual threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="web-overview-006">Phiên bản Spring, Jakarta Servlet và khả năng tương thích của API</a>

<details>
<summary>Xem chi tiết</summary>

# Phiên bản Spring, Jakarta Servlet và khả năng tương thích của API

## 1. Vì sao phải kiểm tra phiên bản trước khi viết ứng dụng web?

Một ứng dụng **Spring Web MVC** không chạy độc lập khỏi các lớp nền tảng: mã Java được biên dịch bằng JDK, sử dụng Spring Framework và các API Servlet, rồi được phục vụ bởi một Servlet container như Tomcat hoặc Jetty. Nếu dùng Spring Boot, Boot chọn và quản lý phần lớn phiên bản phụ thuộc và thường khởi động container nhúng.

Có bốn câu hỏi riêng biệt:

1. **JDK nào?** Ví dụ Java 8, 17, 21 hoặc 25; JVM phải chạy được bytecode và thỏa yêu cầu tối thiểu của Spring/container.
2. **Spring Framework nào?** Ví dụ 5.3, 6.2 hoặc 7.0; mỗi thế hệ có mức Java/Jakarta EE tối thiểu và thay đổi API riêng.
3. **API Servlet nào?** Ví dụ Servlet 4.0 (`javax.servlet.*`), Jakarta Servlet 5.0/6.0/6.1 (`jakarta.servlet.*`); đây là hợp đồng giữa mã web và container.
4. **Container nào?** Ví dụ Tomcat 9/10.1/11; container *triển khai* một phiên bản Servlet, chứ việc thêm một JAR Servlet API không tự nâng cấp container.

**Phạm vi bài:** web stack dựa trên Servlet của Spring MVC (`spring-webmvc`). Spring WebFlux có thể chạy trên Reactor Netty và không nhất thiết sử dụng Servlet API; vì vậy không áp bảng tương thích container Servlet một cách máy móc cho mọi ứng dụng WebFlux.

## 2. Bản đồ tương thích cần nhớ

| Thế hệ ứng dụng điển hình | Java tối thiểu | API web/namespace | Servlet container điển hình | Điều cần nhớ |
| --- | --- | --- | --- | --- |
| Spring Framework **5.3** / Spring Boot **2.7** | Java 8 | Java EE 8, **Servlet 4.0**, `javax.servlet.*` | **Tomcat 9** | Dòng cũ trước chuyển đổi namespace. |
| Spring Framework **6.0–6.2** / Spring Boot **3.x** | Java 17 | Jakarta EE 9+, **Servlet 5.0+**, `jakarta.servlet.*` | Boot 3.5 nhúng **Tomcat 10.1 / Servlet 6.0** | Servlet 5.0 là mức API tối thiểu của thế hệ Spring 6; phiên bản nhúng thực tế có thể cao hơn. |
| Spring Framework **7.0** / Spring Boot **4.x** | Java 17 | Jakarta EE 11, **Servlet 6.1+**, `jakarta.servlet.*` | **Tomcat 11 / Servlet 6.1**, hoặc Jetty 12.1 | Nâng baseline Servlet; không thể mặc định triển khai lên Tomcat 10.1. |

**Cách đọc bảng:** đây là quan hệ giữa *các dòng phiên bản*, không phải khẳng định mọi bản vá Spring Boot chọn cùng một bản Spring Framework. Chẳng hạn, Boot 3.5 sử dụng dòng Framework 6.2; Boot 4.0/4.1 sử dụng dòng Framework 7.0. Xem dependency management/BOM của đúng phiên bản Boot đang dùng để biết phiên bản thư viện thực tế.

Bảng riêng cho **Tomcat và Servlet**:

| Tomcat | Servlet specification | Namespace |
| --- | --- | --- |
| 9.0.x | 4.0 | `javax.servlet.*` |
| 10.0.x (đã hết hỗ trợ) | 5.0 | `jakarta.servlet.*` |
| 10.1.x | 6.0 | `jakarta.servlet.*` |
| 11.0.x | 6.1 | `jakarta.servlet.*` |

**Hai lưu ý dễ nhầm:**

- Tên tổ chức *Jakarta EE 8* đã xuất hiện trước khi đổi package; các API Jakarta EE 8/Servlet 4.0 vẫn dùng namespace `javax.*`. **Jakarta EE 9/Servlet 5.0** mới là mốc đổi sang `jakarta.*`.
- Các mức Java tối thiểu trong bảng là mức của *Spring Framework/thế hệ ứng dụng*. Một container riêng có thể yêu cầu Java thấp hơn, nhưng toàn bộ ứng dụng vẫn phải đáp ứng **mức cao nhất** trong các thành phần. Ví dụ Tomcat 10.1 có thể chạy với Java 11, nhưng Spring Framework 6 vẫn cần Java 17+.

## 3. Servlet API nằm ở đâu trong Spring MVC?

Luồng xử lý rút gọn:

```text
HTTP client
    |
    v
Servlet container (Tomcat/Jetty)
    |
    v
jakarta.servlet.Filter chain
    |
    v
Spring DispatcherServlet
    |
    v
HandlerMapping -> @Controller/@RestController -> HandlerAdapter
    |
    v
HttpMessageConverter / ViewResolver
    |
    v
HTTP response
```

`DispatcherServlet` là một Servlet do Spring MVC cung cấp. Spring sử dụng những đối tượng từ Servlet API như `HttpServletRequest`, `HttpServletResponse`, `ServletContext`, `Filter`, `ServletRegistration`. Container tạo và quản lý vòng đời request/response, filter, servlet; Spring thực hiện routing, binding, gọi handler, xử lý lỗi và tạo phản hồi.

Chẳng hạn, chữ ký sau phụ thuộc trực tiếp vào namespace của Servlet API:

```java
// Spring Framework 5.3 / Spring Boot 2.x
import javax.servlet.http.HttpServletRequest;

// Spring Framework 6+ / Spring Boot 3+
import jakarta.servlet.http.HttpServletRequest;
```

Đây là **hai kiểu Java khác nhau** có tên đầy đủ khác nhau; chúng không tự hoán đổi được chỉ vì cùng có tên `HttpServletRequest`. Tương tự: `javax.servlet.Filter` khác `jakarta.servlet.Filter`.

## 4. Điểm gãy lớn: Java EE → Jakarta EE

Khi các đặc tả enterprise chuyển sang Jakarta EE, các API chịu ảnh hưởng thay đổi namespace. Với ứng dụng web, ví dụ thường gặp:

| Trước (Java EE 8) | Sau (Jakarta EE 9+) | Vai trò |
| --- | --- | --- |
| `javax.servlet.http.HttpServletRequest` | `jakarta.servlet.http.HttpServletRequest` | Request HTTP |
| `javax.servlet.http.HttpServletResponse` | `jakarta.servlet.http.HttpServletResponse` | Response HTTP |
| `javax.servlet.Filter` | `jakarta.servlet.Filter` | Filter |
| `javax.servlet.annotation.WebFilter` | `jakarta.servlet.annotation.WebFilter` | Đăng ký filter |
| `javax.validation.Valid` | `jakarta.validation.Valid` | Bean Validation |
| `javax.persistence.Entity` | `jakarta.persistence.Entity` | JPA |
| `javax.annotation.PostConstruct` | `jakarta.annotation.PostConstruct` | Lifecycle annotation |

**Không thay thế toàn bộ `javax.` bằng `jakarta.` một cách mù quáng.** Các API thuộc Java SE như `javax.crypto`, `javax.net`, `javax.xml`, `javax.management` không cùng quy tắc chuyển đổi. Chỉ sửa những API thực sự đã chuyển sang Jakarta EE và căn cứ vào bản thư viện đang dùng.

Sự thay đổi có ba hệ quả:

- **Tương thích mã nguồn:** import `javax.servlet.*` không biên dịch được trong dự án chỉ có `jakarta.servlet-api`; phải sửa mã nguồn và các type liên quan.
- **Tương thích nhị phân:** một thư viện JAR biên dịch với Servlet `javax` vẫn tham chiếu bytecode đến `javax/servlet/...`; chỉ đổi import của *ứng dụng* chưa làm thư viện đó tương thích. Cần bản thư viện được build cho Jakarta hoặc chuyển đổi artifact một cách có kiểm chứng.
- **Tương thích lúc chạy:** container dựa trên Jakarta không cung cấp Servlet kiểu `javax` để ứng dụng Spring MVC mới sử dụng; có thể gặp `ClassNotFoundException`, `NoClassDefFoundError`, lỗi khởi tạo Servlet/filter hoặc không triển khai được WAR.

Công cụ migration của Tomcat có thể giúp chuyển namespace ở artifact cũ trong một số trường hợp, nhưng không thay thế việc kiểm tra API bị xóa, thay đổi hành vi, thư viện bên thứ ba, cấu hình triển khai và kiểm thử tích hợp.

## 5. Ví dụ chạy thực tế: Spring Boot 3.5 và Jakarta Servlet

Ví dụ sau dùng **Java 17+, Spring Boot 3.5.16**, Maven và `spring-boot-starter-web`. Boot quản lý Spring Framework cùng Tomcat nhúng tương thích; không phải tự thêm một phiên bản `servlet-api` tùy ý. Ứng dụng trả về phiên bản Servlet mà container đang báo.

**Cấu trúc dự án:**

```text
servlet-compat/
├── pom.xml
└── src/main/java/demo/
    ├── WebApplication.java
    └── ServletInfoController.java
```

**`pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>
    <groupId>demo</groupId>
    <artifactId>servlet-compat</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
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

**`src/main/java/demo/WebApplication.java`:**

```java
package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
```

**`src/main/java/demo/ServletInfoController.java`:**

```java
package demo;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServletInfoController {
    @GetMapping("/api/servlet")
    public String servletVersion(HttpServletRequest request) {
        ServletContext context = request.getServletContext();
        return "Servlet " + context.getMajorVersion()
                + "." + context.getMinorVersion();
    }
}
```

**Chạy khi đã cài JDK 17+ và Maven:**

```bash
mvn spring-boot:run
```

Ở terminal khác:

```bash
curl -s http://localhost:8080/api/servlet
```

Kết quả HTTP body với **Tomcat 10.1 nhúng mặc định của Boot 3.5**:

```text
Servlet 6.0
```

Trên Windows PowerShell có thể dùng `curl.exe` thay cho `curl`. Kết quả đang phản ánh **Servlet specification của container**, không phải chuỗi phiên bản Spring Framework hoặc phiên bản JDK. Nếu đổi sang container khác, phản hồi có thể khác.

**Thử lỗi có chủ đích:** đổi dòng import trong controller thành `import javax.servlet.http.HttpServletRequest;` trong khi vẫn dùng dependency Boot 3.5 ở trên. Dự án không có kiểu đó trên compile classpath và báo lỗi biên dịch liên quan package `javax.servlet.http`. Cách sửa là dùng `jakarta.servlet.http.HttpServletRequest` như ví dụ, không thêm JAR `javax.servlet-api` để che lỗi.

## 6. Spring Framework, Spring Boot và Servlet container không phải cùng một phiên bản

- **Spring Framework** cung cấp `spring-web`, `spring-webmvc`, IoC, transaction, v.v. Framework không tự quyết định phiên bản Tomcat nhúng của một dự án bất kỳ.
- **Spring Boot** là tầng cấu hình và quản lý dependencies, starters, auto-configuration, packaging. BOM của Boot quyết định các phiên bản Spring và container nhúng được kiểm thử cùng nhau.
- **Servlet API** là các interface/class tiêu chuẩn mà mã ứng dụng có thể biên dịch theo.
- **Servlet container** cung cấp implementation khi chạy, sở hữu vòng đời request/response, servlet và filter.

Trong **Boot executable JAR**, `spring-boot-starter-web` kéo vào container nhúng được quản lý (mặc định thường là Tomcat). Với **WAR triển khai lên container bên ngoài**, container đích phải đáp ứng mức Servlet mà thế hệ Spring yêu cầu; đồng thời packaging phải tránh đóng gói trùng các lớp/container implementation không phù hợp.

Đừng kết luận “đã dùng Java 21 nên Tomcat 9 chạy được Spring 6”. Java đủ mới chỉ giải quyết một điều kiện; Tomcat 9 vẫn cung cấp `javax.servlet`/Servlet 4.0 nên không thỏa yêu cầu Jakarta Servlet của Spring 6.

## 7. Chọn phiên bản khi bắt đầu và khi nâng cấp

### Tình huống A: ứng dụng Spring Boot 2.7 + Tomcat 9 hiện hữu

Bộ này thuộc thế hệ `javax.servlet.*`. Khi chuyển sang Boot 3.x, cần **cùng lúc** nâng yêu cầu JDK lên 17+, chuyển những API Java EE liên quan sang `jakarta.*`, chọn thư viện hỗ trợ Jakarta và kiểm tra container. Chỉ nâng Tomcat từ 9 sang 10.1 mà vẫn giữ WAR `javax` cũ không phải là nâng cấp tương thích hoàn chỉnh.

### Tình huống B: ứng dụng Boot 3.5 + Tomcat 10.1

Đây là tổ hợp hợp lệ: Spring 6.2 + Java 17+ + Jakarta Servlet 6.0 (mức Spring 6 tối thiểu là Servlet 5.0). Nếu triển khai WAR ra ngoài, cần kiểm tra version container và các dependency thực tế, không dựa vào việc compile trên máy cá nhân đã thành công.

### Tình huống C: muốn chuyển từ Boot 3.x sang Boot 4.x

Boot 4.x chuyển sang Spring 7, sử dụng Jakarta EE 11/Servlet **6.1+**. Nếu trước đó đang chạy Tomcat 10.1 (Servlet 6.0), phải chọn container đáp ứng Servlet 6.1, chẳng hạn Tomcat 11; đồng thời rà các thay đổi Spring/Boot và thư viện liên quan. Namespace vẫn là `jakarta.*`, nhưng **cùng namespace không bảo đảm cùng phiên bản API**.

### Tình huống D: thư viện web nội bộ vẫn import `javax.servlet.Filter`

Với Spring 6+/Boot 3+, hãy nâng hoặc rebuild thư viện bằng API Jakarta phù hợp. Khi xuất bản thư viện, xác định rõ biến thể hỗ trợ `javax` hay `jakarta`, khai báo dependency API thích hợp và kiểm thử với container đích. Một thư viện nhắm cả hai thế hệ có thể cần hai artifact/module hoặc một lớp thích nghi rõ ràng; Java không coi hai interface này là một.

## 8. Quy trình kiểm tra tương thích thực tế

1. **Ghi bộ phiên bản hiện tại:** `java -version`; phiên bản Boot/Framework trong `pom.xml` hoặc `build.gradle`; loại JAR/WAR; phiên bản container thực tế.
2. **Xác nhận dependency tree:** với Maven có thể dùng `mvn dependency:tree` để xem bản Spring, Tomcat và các JAR Servlet; với Gradle dùng `./gradlew dependencies --configuration runtimeClasspath`, hoặc `dependencyInsight` cho một dependency cụ thể.
3. **Kiểm tra namespace trong mã và dependencies:** tìm `javax.servlet`, `javax.validation`, `javax.persistence`, các implement `Filter`/`Servlet`, listener, JSP/tag library, framework tích hợp và annotation cũ. Chỉ chuyển những package có mapping thực sự.
4. **Đọc baseline từ tài liệu của đúng dòng Spring/Boot:** mức Java, Servlet, Jakarta EE và danh sách container được hỗ trợ. Đừng ghép ngẫu nhiên một bản Spring mới với Boot BOM cũ.
5. **Build sạch và chạy kiểm thử:** kiểm thử compile, khởi động context, endpoint HTTP, filter/security, serialization, validation, error handling; nếu deploy WAR, thử trên **đúng server đích**.
6. **Kiểm tra HTTP contract sau migration:** đường dẫn, method, status code, header, JSON, CORS, xác thực. Có thể giữ contract đối với client ngay cả khi thay đổi nền tảng Servlet bên trong, nhưng phải chứng minh qua kiểm thử.

Lệnh chẩn đoán ví dụ (trong dự án Maven ở mục 5):

```bash
java -version
mvn dependency:tree -Dincludes=org.springframework:spring-webmvc
mvn dependency:tree -Dincludes=org.apache.tomcat.embed:tomcat-embed-core
mvn dependency:tree -Dincludes=jakarta.servlet:jakarta.servlet-api
```

```bash
# Gradle: chạy tại dự án có Gradle wrapper
./gradlew dependencyInsight --dependency spring-webmvc --configuration runtimeClasspath
./gradlew dependencyInsight --dependency tomcat-embed-core --configuration runtimeClasspath
```

Không phải lúc nào `jakarta.servlet-api` cũng hiện thành dependency trực tiếp trong runtime tree: implementation Servlet có thể được cung cấp qua container, và scope/classpath khác nhau khi compile, runtime hoặc triển khai WAR. Vì vậy phải xem toàn bộ cấu hình dependency và môi trường chạy.

## 9. Các lỗi thường gặp và hướng xử lý

| Biểu hiện | Nguyên nhân khả dĩ | Cách kiểm tra và sửa |
| --- | --- | --- |
| `package javax.servlet.http does not exist` với Boot 3 | Mã vẫn dùng namespace trước Jakarta EE 9 | Đổi đúng API sang `jakarta.servlet.http` và cập nhật thư viện liên quan. |
| `NoClassDefFoundError: javax/servlet/Filter` | Một JAR cũ được build cho `javax.servlet` | Xác định JAR qua stack trace/dependency tree, nâng lên bản hỗ trợ Jakarta. |
| Triển khai Spring 6 WAR lên Tomcat 9 thất bại | Container chỉ có Servlet 4.0/`javax` | Chọn container tương thích Jakarta Servlet, kiểm tra cấu hình WAR. |
| Triển khai Spring 7 trên Tomcat 10.1 thất bại | Tomcat 10.1 triển khai Servlet 6.0, thấp hơn baseline 6.1 | Chọn container Servlet 6.1+ như Tomcat 11. |
| `UnsupportedClassVersionError` | JVM chạy thấp hơn bytecode Java dùng để build | Kiểm tra `java -version` của **runtime**, compiler target và Java baseline của framework. |
| `NoSuchMethodError` hoặc `AbstractMethodError` | API/implementation hay thư viện bất đồng phiên bản | Phân tích classpath, BOM, dependency overrides; đồng bộ bằng tập phiên bản được hỗ trợ. |
| Code compile nhưng request sai sau nâng cấp | Thay đổi cấu hình routing/filter/converter/behavior | Dùng kiểm thử integration và so sánh status/header/body giữa các phiên bản. |

**Quy tắc tránh lỗi:** không tùy tiện thêm cả `javax.servlet-api` và `jakarta.servlet-api` để “hỗ trợ mọi thứ”. Hai bộ kiểu không biến đổi lẫn nhau; classpath chứa cả hai có thể chỉ che dấu sự không tương thích của thư viện.

## 10. Phân biệt các loại tương thích

**API nguồn (source compatibility)** nói tới việc mã Java còn biên dịch với phiên bản API mới hay không. **Binary compatibility** nói tới bytecode/JAR đã build có còn liên kết được các class/method khi chạy hay không. **Runtime/platform compatibility** bao gồm JVM, Servlet container và các implementation thực tế. **HTTP/API contract compatibility** là cam kết với client qua URL, status, header, body; nó không buộc backend phải giữ cùng Servlet namespace.

Ví dụ: nâng từ Boot 2.7 lên 3.5 có thể cần sửa import Java và thay các thư viện `javax` để build/chạy được, nhưng endpoint `GET /api/users` vẫn có thể trả JSON và status giống hệt đối với client. Mặt khác, build thành công sau khi đổi namespace cũng chưa chứng minh filter, security, serialization hay lỗi HTTP giữ nguyên hành vi.

## 11. Tự kiểm tra hiểu bài

- Một dự án Spring Framework 6.2 biên dịch bằng Java 21 có deploy lên Tomcat 9 được không? **Không**: Tomcat 9 là Servlet 4.0/`javax`, khác mức Jakarta Servlet 5.0+ mà Spring 6 cần.
- Vì sao Boot 3.5 có thể trả `Servlet 6.0` dù bảng nói Spring 6 yêu cầu Servlet 5.0+? Vì **5.0+ là mức tối thiểu**, còn Tomcat 10.1 nhúng triển khai Servlet 6.0.
- Đã đổi `javax.servlet` sang `jakarta.servlet` rồi thì có thể giữ mọi JAR cũ không? **Không**: JAR đã biên dịch tham chiếu tên class và method riêng; cần kiểm tra và nâng dependency.
- Spring 7 và Spring 6 cùng dùng `jakarta.*` có chạy trên cùng Tomcat 10.1 không? **Không thể suy ra như vậy**: Spring 7 có baseline Servlet 6.1, trong khi Tomcat 10.1 là 6.0.
- Có nên đổi `javax.crypto.Cipher` thành `jakarta.crypto.Cipher`? **Không**: `javax.crypto` thuộc Java SE, không nằm trong lần đổi namespace Servlet/Jakarta EE.

## 12. Nguồn tài liệu chính thức và cách dùng

Các bảng baseline và ví dụ được xây dựng theo tài liệu phát hành hiện hành tại thời điểm soạn bài. Khi nâng một bản vá/milestone mới, ưu tiên tài liệu của **đúng phiên bản** đang triển khai:

- [Spring Framework Versions — JDK/Jakarta EE baselines](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions).
- [Spring Framework Overview — chuyển từ Java EE sang Jakarta EE](https://docs.spring.io/spring-framework/reference/overview.html).
- [Spring Web MVC và DispatcherServlet](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html).
- [Spring Boot 3.5 System Requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
- [Spring Boot 4.1 System Requirements](https://docs.spring.io/spring-boot/system-requirements.html).
- [Apache Tomcat: Which Version Do I Want?](https://tomcat.apache.org/whichversion.html).
- [Jakarta Servlet 6.1 specification](https://jakarta.ee/specifications/servlet/6.1/).

**Nguyên tắc lựa chọn:** luôn kiểm tra **JDK + Spring Framework/Boot + Servlet specification + container + dependencies của ứng dụng** như một bộ tương thích thống nhất.

</details>

- [Quay lại đầu trang](#back-to-top)
