<a id="back-to-top"></a>

# Định tuyến yêu cầu

## Menu
- [@Controller, @RestController và cơ chế phát hiện controller](#request-routing-001)
- [@RequestMapping ở lớp và phương thức; các annotation @GetMapping, @PostMapping](#request-routing-002)
- [Điều kiện ánh xạ theo HTTP method, params, headers, consumes và produces](#request-routing-003)
- [Biến đường dẫn, biểu thức chính quy và đường dẫn có ký tự đại diện](#request-routing-004)
- [PathPattern, quy tắc chọn ánh xạ cụ thể và lỗi ánh xạ trùng](#request-routing-005)
- [Xử lý HEAD, OPTIONS và phân biệt lỗi 404, 405, 406, 415](#request-routing-006)
- [@RequestMapping trên interface và cách tổ chức hợp đồng controller](#request-routing-007)
- [Kiểm soát dấu gạch chéo cuối đường dẫn và khác biệt cấu hình theo phiên bản](#request-routing-008)

## <a id="request-routing-001">@Controller, @RestController và cơ chế phát hiện controller</a>

<details>
<summary>Xem chi tiết</summary>

# @Controller, @RestController và cơ chế phát hiện controller trong Spring Web

Trong Spring Web, **controller** là đối tượng Spring (bean) tham gia tiếp nhận request HTTP, gọi nghiệp vụ và quyết định kết quả trả về. Hai annotation dễ gặp nhất là **@Controller** và **@RestController**. Chúng cùng xác định một lớp có vai trò controller, nhưng **khác cách Spring diễn giải giá trị trả về từ handler method**.

Bài học thuộc nhóm **Định tuyến yêu cầu** nên cần phân biệt hai bước xảy ra trước khi request đến method: **Spring phát hiện/đăng ký controller thành bean**, sau đó **Spring MVC đăng ký các request mapping** của những bean phù hợp. Đánh dấu class bằng annotation chỉ là điều kiện để framework nhận diện; controller còn phải nằm trong ApplicationContext đang phục vụ request và có method được ánh xạ.

Mục tiêu sau bài:

- Phân biệt chính xác **@Component, @Controller, @RestController và @ResponseBody**.
- Giải thích tại sao có class @RestController nhưng gọi URL vẫn nhận **404**.
- Mô tả cơ chế **component scan → bean definition → controller detection → handler mapping → request dispatch**.
- Chọn **view name** hay **response body** đúng theo yêu cầu ứng dụng.
- Tổ chức package, khai báo mapping, kiểm chứng bằng một ví dụ Spring Boot MVC có thể chạy được.
- Biết giới hạn của controller detection khi dùng context nhiều tầng, proxy và web stack khác nhau.

## 1. Controller nằm ở đâu trong Spring MVC?

Một HTTP request không được đưa trực tiếp đến method chỉ vì tên method giống URL. Spring MVC đi qua **DispatcherServlet**, chọn handler qua **HandlerMapping**, rồi gọi handler bằng **HandlerAdapter** và xử lý kết quả trả về.

~~~text
Client HTTP request
       |
Servlet container / Servlet filter chain
       |
DispatcherServlet
       |
RequestMappingHandlerMapping
       |      (đã phát hiện và đăng ký controller mappings)
       v
Chọn handler theo path, HTTP method,
Content-Type, Accept, params, headers...
       |
RequestMappingHandlerAdapter
       |-- resolve arguments: @PathVariable, @RequestParam,
       |   @RequestBody, Model, Servlet request, ...
       v
@Controller / @RestController handler method
       |
       +-- Model + view name -> ViewResolver -> HTML
       |
       +-- @ResponseBody / @RestController
       |      -> HttpMessageConverter -> JSON/text/...
       |
       +-- ResponseEntity -> status + headers + body
       |
HTTP response
~~~

Đây là luồng rút gọn của **Spring MVC trên Servlet stack**. Với Spring WebFlux, các annotation controller quen thuộc vẫn được hỗ trợ nhưng hạ tầng bên dưới là WebFlux HandlerMapping/HandlerAdapter, **DispatcherHandler** và reactive codecs. Cách phát hiện/controller mapping có những ý tưởng tương tự, còn mô hình I/O khác nhau.

**Hai bước không được nhập làm một:**

1. **Bean discovery/registration:** lớp Java được đưa vào Spring ApplicationContext dưới dạng bean.
2. **Request mapping registration:** Spring MVC xem xét các controller beans, tìm @RequestMapping hoặc @GetMapping/@PostMapping... rồi đăng ký các điều kiện HTTP tương ứng.

Một class có thể là Spring bean nhưng **không phải HTTP controller**; một class có @Controller nhưng **không được đăng ký thành bean trong đúng context** cũng không thể phục vụ request.

## 2. @Controller là gì?

**@Controller** thuộc package **org.springframework.stereotype**. Đây là một stereotype annotation đánh dấu loại thành phần web, được meta-annotated với **@Component**, nên nằm trong nhóm mặc định mà Spring component scan có thể phát hiện.

~~~java
package example.web;

import org.springframework.stereotype.Controller;

@Controller
public class HomeController {
}
~~~

Đây chỉ là **khai báo một controller bean tiềm năng**, chưa có endpoint: không có @GetMapping hoặc mapping nào khác.

Khi thêm handler:

~~~java
@Controller
public class HomeController {

    @GetMapping("/home")
    public String home() {
        return "home";
    }
}
~~~

Đoạn này **minh họa**, chưa độc lập: còn cần import @GetMapping, MVC context, view resolver và template/view tên **home**. Trong Spring MVC truyền thống, giá trị String **"home"** được hiểu là **tên view** để ViewResolver giải quyết. Nếu có template phù hợp, view sẽ được render thành HTML.

### Không phải mọi giá trị trả về của @Controller đều là tên view

Với @Controller, Spring MVC có nhiều return-value handler. Kết quả phụ thuộc **annotation trên method, kiểu dữ liệu trả về và MVC configuration**:

- **String** hoặc **View** trong handler thông thường: thường biểu thị view name hoặc View.
- **ModelAndView**: chỉ rõ view và model.
- **Model** hoặc **@ModelAttribute**: dùng cho dữ liệu model theo quy tắc MVC.
- **@ResponseBody**: đưa return value vào HTTP response body.
- **ResponseEntity<T>**: mang status, headers và body, có thể ghi body **ngay cả khi class chỉ dùng @Controller**.
- **redirect:** hoặc **forward:** trong view name: tạo redirect/forward theo cơ chế view resolution.

Vì vậy không nên kết luận “@Controller thì tuyệt đối không thể trả JSON”. @Controller **có thể trả JSON**, chỉ cần sử dụng kiểu/annotation trả response body đúng.

## 3. @RestController là gì?

**@RestController** thuộc package **org.springframework.web.bind.annotation** và là một **composed annotation** có hai ý nghĩa chính:

~~~text
@RestController
    ≈ @Controller
    + @ResponseBody ở cấp class
~~~

Ký hiệu ≈ thể hiện **tương đương về semantics chính**, không phải định nghĩa đầy đủ từng thuộc tính của annotation.

~~~java
package example.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    @GetMapping("/api/status")
    public Status status() {
        return new Status("ok");
    }

    public record Status(String value) {
    }
}
~~~

Đây là **đoạn controller minh họa** cần ApplicationContext MVC/Boot và JSON converter thích hợp, không phải chương trình Java chạy độc lập. Nếu được đăng ký và có Jackson hỗ trợ record, endpoint trả body JSON tương đương:

~~~json
{"value":"ok"}
~~~

Khác với @Controller trả tên view, các handler của @RestController mặc định có semantics **@ResponseBody**: đối tượng được chuyển thành biểu diễn HTTP qua các **HttpMessageConverter** (MVC) thay vì chuyển tới ViewResolver.

### So sánh ngắn gọn

| Tiêu chí | @Controller | @RestController |
|---|---|---|
| Thuộc nhóm stereotype Spring | Có; meta-annotated @Component | Có; meta-annotated @Controller và @ResponseBody |
| Được component scan phát hiện | Có khi trong vùng scan và bộ lọc phù hợp | Có trong cùng điều kiện |
| Có thể chứa @GetMapping/@PostMapping | Có | Có |
| String trả về mặc định | Thường là **view name** | **Response body** chuỗi String |
| Trả object JSON mặc định | Cần @ResponseBody/ResponseEntity hoặc cơ chế thích hợp | Qua response body converter khi JSON được hỗ trợ |
| Dùng Model + template HTML | Phù hợp tự nhiên | Không phù hợp nếu muốn String được hiểu là view name |
| Dùng làm REST API | Được, nếu thêm @ResponseBody | Phổ biến và tiện lợi |
| Có tự tạo server/URL không? | Không | Không |
| Có khác biệt trong cách scan package không? | Không đáng kể ở cấp stereotype | Không đáng kể ở cấp stereotype |

**Lựa chọn theo return semantics:** HTML template phía server thường dùng @Controller; JSON/text API thường dùng @RestController. Cả hai có thể sống trong cùng một ứng dụng Spring MVC.

## 4. @ResponseBody là mấu chốt của khác biệt

**@ResponseBody** yêu cầu framework xử lý giá trị trả về như **HTTP response body**. Có thể đặt trên **method** hoặc **class**.

Cùng mục đích trả API JSON, hai cách dưới đây gần tương đương:

~~~java
@Controller
public class ProfileController {

    @ResponseBody
    @GetMapping("/api/profile")
    public Profile profile() {
        return new Profile(7, "An");
    }

    public record Profile(int id, String name) {
    }
}
~~~

~~~java
@RestController
public class ProfileController {

    @GetMapping("/api/profile")
    public Profile profile() {
        return new Profile(7, "An");
    }

    public record Profile(int id, String name) {
    }
}
~~~

**Đây là hai phiên bản thay thế nhau**, không được đồng thời tạo hai class cùng tên/mapping trong một project. Cần import annotation, dependency Spring Web và converter JSON phù hợp.

Cả hai trả một đối tượng được serialize, ví dụ:

~~~json
{"id":7,"name":"An"}
~~~

Trong Spring MVC, **RequestResponseBodyMethodProcessor** là một trong các cơ chế xử lý kiểu return này và phối hợp các **HttpMessageConverter** với media type. JSON converter thường đến từ Jackson. Nếu dự án không có converter hỗ trợ kiểu return và media type, Spring không thể tự tạo JSON chỉ vì xuất hiện @RestController.

### Trường hợp String dễ nhầm

~~~java
@Controller
class HtmlController {

    @GetMapping("/welcome")
    public String welcome() {
        return "welcome";
    }
}
~~~

Spring MVC có thể tìm view **welcome**; cần template/view đã cấu hình. Còn:

~~~java
@RestController
class TextController {

    @GetMapping("/welcome")
    public String welcome() {
        return "welcome";
    }
}
~~~

Spring MVC ghi body văn bản **welcome** (nếu mapping phù hợp), **không tìm template welcome**. Hai ví dụ là **lựa chọn riêng**, không tạo cùng URL trong một app nếu không chủ ý xử lý khác điều kiện.

Lỗi phổ biến là đổi một class @Controller thành @RestController để “sửa 404” rồi thấy browser hiển thị chữ **home** thay vì trang HTML. Khi đó nguyên nhân là **thay đổi cách xử lý return value**, chưa chắc đã sửa chuyện scan/mapping.

### ResponseEntity là trường hợp đặc biệt

~~~java
@Controller
class HealthController {

    @GetMapping("/check")
    public ResponseEntity<String> check() {
        return ResponseEntity.ok()
                .header("X-Check", "passed")
                .body("OK");
    }
}
~~~

**Phải import** @Controller, @GetMapping và **org.springframework.http.ResponseEntity** khi đưa đoạn method minh họa vào project. ResponseEntity được MVC xử lý như một kiểu return đặc biệt, nên **không cần @ResponseBody** trên method mới ghi được body "OK". Kết quả: status 200, X-Check: passed, body OK.

Tuy vậy, dùng @RestController thường rõ ý đồ hơn khi cả class chỉ chứa API endpoints.

## 5. Cơ chế phát hiện controller: từ class tới bean

### Bước A — Spring tìm candidate components

Ở Java configuration, ví dụ:

~~~java
@Configuration
@ComponentScan("example.web")
class WebConfig {
}
~~~

Đây là **đoạn cấu hình**, cần import @Configuration và @ComponentScan. Trong lúc khởi tạo ApplicationContext, Spring quét các class có stereotype phù hợp dưới **example.web** và các subpackages theo quy tắc component scanning. Spring dùng metadata của class để xác định candidate và đăng ký **BeanDefinition**.

Mặc định, các annotation thuộc họ **@Component** như **@Service**, **@Repository**, **@Controller**, **@Configuration**, và **@RestController** (qua meta-annotations) có thể được phát hiện.

**Tức là:** @RestController được phát hiện vì nó **gián tiếp mang @Component**, chứ không phải vì Spring Boot liên tục tìm mọi tên class kết thúc bằng "Controller".

Ví dụ:

~~~text
example/
  web/
    StatusController.java       <-- @RestController, nằm trong scan
  service/
    GreetingService.java        <-- @Service, ngoài vùng "example.web"
  another/
    OtherController.java        <-- @Controller, ngoài vùng "example.web"
~~~

Nếu cấu hình chỉ scan **example.web**, hai class ngoài package này không tự được đưa vào context bằng việc quét ấy. Vẫn có thể đăng ký thủ công hoặc thông qua cấu hình khác, nhưng **không được kết luận chúng đã được scan**.

### Bước B — BeanDefinition được tạo, bean được khởi tạo

Sau khi xác định component, Spring lưu cấu hình bean và thực hiện quá trình tạo/phụ thuộc/lifecycle theo loại bean và scope. Bean có thể được:

- Quét bởi **@ComponentScan** hoặc **context:component-scan** trong XML.
- Đăng ký qua **@Bean** của @Configuration.
- Import từ cấu hình khác bằng **@Import**.
- Đăng ký lập trình bằng API của ApplicationContext/registry.

**@Controller không buộc bạn phải dùng component scan.** Một object controller có thể được đăng ký theo cách khác, miễn Spring MVC nhìn thấy nó là controller bean hợp lệ trong đúng context.

### Bước C — Spring MVC xác định bean nào là handler

Khi MVC infrastructure khởi tạo, **RequestMappingHandlerMapping** (hoặc hạ tầng mapping tương ứng) tìm các bean types được xem là **handler**, điển hình những type có **@Controller** kể cả qua meta-annotation như @RestController. Framework kiểm tra các method có @RequestMapping hoặc composed mappings như @GetMapping, @PostMapping.

Ví dụ:

~~~java
@Service
class LibraryService {

    @GetMapping("/books")
    public String list() {
        return "data";
    }
}
~~~

Đây là **đoạn minh họa lỗi thiết kế**. Dù @Service được scan thành bean và method có @GetMapping, Spring MVC **không coi nó là annotated controller chỉ nhờ @GetMapping**. Không nên đặt handler annotation vào @Service rồi kỳ vọng tự tạo endpoint. Hãy có @Controller/@RestController gọi service và ánh xạ URL.

### Bước D — Đăng ký mappings

Một handler method thường tạo metadata chứa:

- URL path/pattern.
- HTTP method: GET, POST, PUT, DELETE, PATCH...
- **params**: điều kiện query/form parameter khi có.
- **headers**: điều kiện header khác khi có.
- **consumes**: media type của request body.
- **produces**: media type response được client chấp nhận.
- Các yêu cầu custom conditions hoặc API version tùy cấu hình/framework version.

Mapping kết hợp các điều kiện ở **class level** và **method level**. Nếu có hai mapping trùng không thể phân biệt ở cùng môi trường, ứng dụng có thể báo lỗi **Ambiguous mapping** trong startup; những mapping chồng lấp nhưng khác độ cụ thể có thể chỉ gây mơ hồ ở lúc request, tùy cấu hình.

### Bước E — DispatcherServlet dispatch request

Khi request đến, Spring đánh giá mapping đã đăng ký, chọn handler method thích hợp, bind method arguments, gọi controller và xử lý kết quả. **Không quét lại toàn bộ classpath để tìm controller cho từng request**.

Điều này giải thích vì sao sửa source code và lưu file chưa khiến endpoint mới xuất hiện trong tiến trình Java đang chạy nếu dự án chưa có cơ chế reload/restart tương ứng.

## 6. Spring Boot phát hiện controller như thế nào?

Trong Spring Boot, **@SpringBootApplication** tích hợp những cơ chế cần thiết như **@SpringBootConfiguration**, **@EnableAutoConfiguration** và **@ComponentScan**. Theo mặc định, component scanning bắt đầu ở **package chứa lớp ứng dụng chính** và các subpackages.

Cấu trúc được khuyến khích:

~~~text
src/main/java/
  com/example/shop/
    ShopApplication.java         <-- @SpringBootApplication
    web/
      ProductRestController.java <-- @RestController
      HomeController.java        <-- @Controller
    service/
      ProductService.java        <-- @Service
    config/
      WebConfiguration.java      <-- @Configuration
~~~

Do ShopApplication nằm trong **com.example.shop**, tất cả các package con phù hợp đều nằm trong phạm vi scan mặc định.

**Ví dụ lỗi:**

~~~text
src/main/java/
  com/example/shop/bootstrap/
    ShopApplication.java       <-- @SpringBootApplication
  com/example/shop/web/
    ProductRestController.java <-- @RestController
~~~

Khi đó package mặc định để scan là **com.example.shop.bootstrap** và các package con; **com.example.shop.web** là package anh em, không nằm trong scan. Ứng dụng vẫn có thể khởi động HTTP server nhưng GET tới mapping của ProductRestController trả **404**.

**Sửa hợp lý:**

- Di chuyển ShopApplication lên **com.example.shop** (cấu trúc rõ ràng và dễ duy trì).
- Hoặc khi có lý do thật sự, cấu hình scan rõ bằng @SpringBootApplication(scanBasePackages = "com.example.shop") hoặc @ComponentScan thích hợp. Khi thay đổi scan, cần nhớ những phần khác như entity/repository scan có cơ chế riêng; không suy rằng chỉnh component scan là đủ cho mọi loại metadata.

**Không nên đặt lớp chính vào default package (không có dòng package)**: Spring Boot cảnh báo cấu trúc này có thể khiến classpath scan quá rộng và khó kiểm soát. Dùng package Java có tên rõ ràng.

### Component scan không đồng nghĩa @EnableWebMvc

- **@ComponentScan** phát hiện và đăng ký các bean như controller/service.
- **@EnableWebMvc** bật cấu hình MVC Java trong **Spring MVC thuần**.
- **Spring Boot MVC auto-configuration** thiết lập MVC infrastructure/DispatcherServlet theo điều kiện, nên Boot thông thường **không cần @EnableWebMvc**.

Một dự án có thể scan được @Controller nhưng chưa có DispatcherServlet/web infrastructure hoặc dependency cần thiết. Ngược lại, servlet MVC chạy nhưng không scan được controller cũng không có mapping tương ứng. Khi debug phải xác định **cả hai tầng**.

## 7. Ví dụ Spring Boot MVC hoàn chỉnh: HTML và JSON cùng chạy

Ví dụ này giúp phân biệt **@Controller → Thymeleaf view** và **@RestController → JSON** trong **cùng một ứng dụng**. Dùng **Java 17+**, Maven, Spring Initializr tại **https://start.spring.io/** và chọn dependency:

1. **Spring Web** để chạy Spring MVC.
2. **Thymeleaf** để render HTML template.
3. Có thể thêm **Spring Boot DevTools** trong môi trường phát triển, nhưng không bắt buộc.

Dùng phiên bản Spring Boot được Initializr hỗ trợ và JDK tương thích. **Không dùng Spring Reactive Web** cho ví dụ Servlet MVC này. Trong các bản Boot, artifact starter MVC có thể khác tên (Boot 3.x thường dùng spring-boot-starter-web, Boot 4.x dùng spring-boot-starter-webmvc); chọn qua Initializr để nhận cấu hình phù hợp.

Tạo package nền **example.discovery** trong project. Các file:

~~~text
src/main/
  java/example/discovery/
    DiscoveryApplication.java
    service/GreetingService.java
    web/PageController.java
    web/GreetingRestController.java
    web/MixedController.java
  resources/
    templates/greeting.html
~~~

### 7.1. Lớp ứng dụng chính

**src/main/java/example/discovery/DiscoveryApplication.java**

~~~java
package example.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DiscoveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryApplication.class, args);
    }
}
~~~

**Vì sao đặt ở đây:** lớp chính thuộc **example.discovery**, nằm phía trên các package **example.discovery.web** và **example.discovery.service**, nên Spring component scan mặc định có thể phát hiện controller và service. Không cần @ComponentScan tùy biến.

### 7.2. Service chỉ chứa nghiệp vụ

**src/main/java/example/discovery/service/GreetingService.java**

~~~java
package example.discovery.service;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public String greet(String name) {
        return "Xin chào, " + name + "!";
    }
}
~~~

Service là Spring bean nhưng **không khai báo HTTP endpoint**. Tách logic vào service cho phép HTML controller và JSON controller dùng chung mà không nhân đôi nghiệp vụ.

### 7.3. HTML controller: @Controller và Model

**src/main/java/example/discovery/web/PageController.java**

~~~java
package example.discovery.web;

import example.discovery.service.GreetingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    private final GreetingService greetingService;

    public PageController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/greeting")
    public String greetingPage(
            @RequestParam(defaultValue = "An") String name,
            Model model) {
        model.addAttribute("message", greetingService.greet(name));
        return "greeting";
    }
}
~~~

Điểm quan trọng:

- **@Controller** làm class thành controller bean.
- **@GetMapping("/greeting")** đăng ký HTTP GET /greeting.
- **@RequestParam** đọc tham số query; defaultValue được dùng khi không truyền tên.
- **Model** lưu giá trị message cho view.
- **return "greeting"** là **logical view name**, được Thymeleaf view resolver liên kết tới template **templates/greeting.html**, không phải body chữ "greeting".

**src/main/resources/templates/greeting.html**

~~~html
<!DOCTYPE html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Ví dụ Spring Controller</title>
</head>
<body>
    <h1 th:text="${message}">Thông điệp</h1>
</body>
</html>
~~~

Thymeleaf thay nội dung h1 bằng message có trong Model. **th:text** escape HTML cho dữ liệu hiển thị, phù hợp với văn bản từ request. Hạn chế dùng th:utext cho dữ liệu không tin cậy vì có nguy cơ chèn HTML/script nếu không kiểm soát.

### 7.4. JSON API controller: @RestController

**src/main/java/example/discovery/web/GreetingRestController.java**

~~~java
package example.discovery.web;

import example.discovery.service.GreetingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
public class GreetingRestController {

    private final GreetingService greetingService;

    public GreetingRestController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping(path = "/{name}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public GreetingResponse byName(@PathVariable String name) {
        return new GreetingResponse(greetingService.greet(name));
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public GreetingResponse create(@RequestBody GreetingRequest request) {
        return new GreetingResponse(
                greetingService.greet(request.name()));
    }

    public record GreetingRequest(String name) {
    }

    public record GreetingResponse(String message) {
    }
}
~~~

Vì @RestController đã bao gồm @ResponseBody ở mức class, đối tượng GreetingResponse được JSON message converter serialize. **@RequestMapping** ở class kết hợp với **@GetMapping** và **@PostMapping** ở method. Ví dụ trên có hai HTTP mappings khác nhau:

~~~text
GET  /api/greetings/{name}
POST /api/greetings
~~~

**@ResponseStatus(HttpStatus.CREATED)** làm response POST thành status **201** khi xử lý thành công. **consumes** hạn chế Content-Type request; **produces** thể hiện response media type. Bản đơn giản này không validate trường name null/blank; production API nên thêm DTO validation và xử lý lỗi theo contract.

### 7.5. @Controller vẫn trả response body khi cần

**src/main/java/example/discovery/web/MixedController.java**

~~~java
package example.discovery.web;

import example.discovery.service.GreetingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class MixedController {

    private final GreetingService service;

    public MixedController(GreetingService service) {
        this.service = service;
    }

    @ResponseBody
    @GetMapping(path = "/text",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String greetingText(
            @RequestParam(defaultValue = "An") String name) {
        return service.greet(name);
    }

    @GetMapping(path = "/headers",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> greetingWithHeader() {
        return ResponseEntity.ok()
                .header("X-Source", "controller")
                .body("Kết quả từ ResponseEntity");
    }
}
~~~

Hai method trong class @Controller **không đi qua template**: /text có @ResponseBody, /headers trả ResponseEntity. Đây là bằng chứng @Controller không bị giới hạn ở HTML.

### 7.6. Chạy và kiểm tra từng endpoint

Tại thư mục gốc dự án Spring Initializr (nơi có pom.xml), chạy:

~~~bash
# macOS/Linux
./mvnw spring-boot:run
~~~

Trên Windows PowerShell:

~~~powershell
.\mvnw.cmd spring-boot:run
~~~

Server MVC mặc định thường nghe tại **http://localhost:8080**, trừ khi dự án thay đổi server.port. Mở terminal khác:

**A. HTML view**

~~~bash
curl -i "http://localhost:8080/greeting?name=Lan"
~~~

Kỳ vọng status **200**, Content-Type tương ứng HTML và body chứa:

~~~html
<h1>Xin chào, Lan!</h1>
~~~

HTML thực tế có thêm phần head/body và whitespace do template; trên đây là **đoạn quan trọng cần kiểm tra**, không phải toàn bộ response byte-for-byte.

**B. JSON API**

~~~bash
curl -i -H "Accept: application/json" \
  "http://localhost:8080/api/greetings/Lan"
~~~

Body JSON dự kiến (về mặt nội dung):

~~~json
{"message":"Xin chào, Lan!"}
~~~

Nếu JSON serializer escape các ký tự Unicode, những chuỗi JSON khác nhau về cách escape có thể biểu diễn **cùng giá trị message**.

**C. POST JSON**

~~~bash
curl -i -X POST "http://localhost:8080/api/greetings" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data '{"name":"Minh"}'
~~~

Kỳ vọng status **201 Created** và JSON body:

~~~json
{"message":"Xin chào, Minh!"}
~~~

Nếu đặt Content-Type: text/plain trong khi mapping chỉ consumes JSON, Spring MVC thường trả **415 Unsupported Media Type**. Nếu gửi JSON sai cú pháp, có thể trả **400 Bad Request**. Đừng nhầm việc nhận body JSON qua @RequestBody với việc class được gắn @RestController.

**D. Văn bản từ @Controller + @ResponseBody**

~~~bash
curl -i -H "Accept: text/plain" \
  "http://localhost:8080/text?name=Mai"
~~~

Body dự kiến:

~~~text
Xin chào, Mai!
~~~

Đây **không phải view name**, nhờ @ResponseBody.

**E. ResponseEntity từ @Controller**

~~~bash
curl -i "http://localhost:8080/headers"
~~~

Kỳ vọng status **200**, response header:

~~~http
X-Source: controller
~~~

Và body:

~~~text
Kết quả từ ResponseEntity
~~~

Nếu làm ví dụ trên Windows PowerShell cũ, có thể dùng **curl.exe** thay vì alias curl để chắc chắn gọi công cụ curl thật. Khi chạy đồng thời nhiều demo Spring Boot, hãy chắc chắn cổng 8080 chưa bị chiếm.

## 8. Khi component scan không thấy controller: một thí nghiệm

Từ project trên, nếu chuyển DiscoveryApplication.java sang package **example.discovery.bootstrap** mà vẫn giữ controller ở **example.discovery.web**, cấu trúc thành:

~~~text
example.discovery.bootstrap.DiscoveryApplication
example.discovery.web.PageController
example.discovery.web.GreetingRestController
example.discovery.web.MixedController
example.discovery.service.GreetingService
~~~

Chỉ thay package/đường dẫn của lớp main mà không đổi vùng scan, mặc định Boot quét **example.discovery.bootstrap** và các subpackages. Các bean sibling **web** và **service** không thuộc phạm vi ấy. Ứng dụng có thể khởi động nhưng không có các endpoint mong đợi; request GET /api/greetings/Lan hoặc /greeting thường trả **404**.

**Cách khắc phục:** đưa lớp main về package **example.discovery**. Đây là cấu trúc đơn giản và thường nên ưu tiên.

Một phương án tùy hoàn cảnh là khai báo:

~~~java
@SpringBootApplication(scanBasePackages = "example.discovery")
public class DiscoveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryApplication.class, args);
    }
}
~~~

Đây là **phiên bản thay thế của lớp main**, không được đồng thời tạo hai DiscoveryApplication. Annotation parameter giải quyết phạm vi component scan cho ví dụ; trong dự án có JPA entity hoặc Spring Data repositories, có thể cần xét riêng phạm vi @EntityScan/@EnableJpaRepositories và cấu trúc package.

### Một số lý do khác khiến không tìm thấy controller

- Class không có @Controller/@RestController hoặc custom annotation meta-annotated phù hợp.
- Controller được đánh dấu stereotype đúng nhưng không phải bean trong **DispatcherServlet ApplicationContext** đang xử lý request.
- @ComponentScan có **excludeFilters**, hoặc **useDefaultFilters=false** khiến class bị loại.
- Bean đăng ký có điều kiện **@Profile**, **@Conditional...** chưa thỏa.
- Controller không được đưa vào runtime classpath/artifact, hoặc đã biên dịch code cũ.
- Có nhiều DispatcherServlet hoặc nhiều ApplicationContexts, controller nằm trong context không nhìn thấy từ servlet cần phục vụ request.
- Handler method thiếu @RequestMapping hoặc composed HTTP mapping, nên bean controller không cung cấp endpoint.
- URL bạn gọi sai context path, servlet mapping, prefix @RequestMapping, HTTP method, hoặc bị filter/security/proxy chặn trước khi đến MVC.

**Chẩn đoán theo tầng:** đầu tiên xác định bean tồn tại hay không, sau đó xác định mapping được đăng ký hay không, cuối cùng mới đối chiếu HTTP request thực tế.

## 9. Từ controller được phát hiện đến mapping chính xác

**Bean được nhận diện và endpoint được định tuyến là hai công việc khác nhau.** Sau khi Spring tìm thấy controller, `RequestMappingHandlerMapping` đăng ký metadata của các method. Prefix ở class như `@RequestMapping("/api/books")` kết hợp với `@GetMapping("/{id}")` thành `GET /api/books/{id}`; `@PostMapping` tại class đó có thể cùng dùng path `/api/books` nhưng khác HTTP method.

Controller mẫu ở phần 7 đã cho thấy GET với URI variable, POST nhận JSON, response status và media type. Khi ứng dụng chạy, nên kiểm tra thêm các tình huống sau:

| Dấu hiệu | Câu hỏi tiếp theo |
| --- | --- |
| HTTP 404 | Bean/controller có được scan, method có mapping và URL có đúng prefix/context path không? Một handler cũng có thể chủ động trả 404 cho tài nguyên không tồn tại. |
| HTTP 405 | Path đã có handler nhưng request sử dụng sai HTTP method? |
| HTTP 406 | Header `Accept` có phù hợp với response `produces` và converter không? |
| HTTP 415 | Request `Content-Type` có phù hợp `consumes` và converter đọc body không? |
| Startup báo `Ambiguous mapping` | Có hai handler đăng ký cùng điều kiện method/path/params/headers/media type không? |

Một bean controller có thể có nhiều handler method, hoặc không có mapping nào. Trùng mapping không được sửa chỉ bằng cách đổi tên phương thức Java: cần thay điều kiện HTTP theo hợp đồng API. Đối với việc so sánh nhiều mẫu URI cạnh tranh, hãy dựa vào độ cụ thể của mapping chứ không dựa vào thứ tự khai báo.

Phần này chỉ nối quá trình **controller discovery** với routing. Các bài tiếp theo trong chương đi sâu riêng vào mapping mức class/method, tổ hợp điều kiện, URI patterns, ambiguity và mã lỗi; không cần lặp lại cùng một ứng dụng minh họa ở mỗi mức khái niệm.

## 10. MVC annotation controller khác gì Servlet hoặc functional endpoint?

Spring MVC vẫn có thể viết HTTP Servlet theo cách thấp hơn, tự đăng ký Servlet hoặc sử dụng functional endpoint **RouterFunction**. Vì vậy “ứng dụng Spring Web có endpoint” không đồng nghĩa mọi endpoint đều nằm trong @Controller.

| Kiểu HTTP handler | Cách đăng ký | Spring MVC RequestMappingHandlerMapping có quản lý như annotated controller? |
|---|---|---|
| @Controller + @GetMapping | Bean controller + annotation mapping | Có |
| @RestController + @GetMapping | Bean controller + annotation mapping | Có |
| Một @Service có @GetMapping | Bean service nhưng không mang stereotype controller | Không, chỉ vì @GetMapping thì chưa đủ |
| Servlet riêng | Servlet registration/mapping | Không phải annotated controller mapping |
| Spring MVC functional RouterFunction | RouterFunction bean | Đi qua functional routing infrastructure, không phải @GetMapping |
| Spring WebFlux annotated controller | Reactive application context + reactive mapping | Dùng WebFlux infrastructure, không phải MVC DispatcherServlet |
| Spring WebFlux functional route | RouterFunction<ServerResponse> WebFlux | Đi qua WebFlux functional routing |

**Đặc biệt:** @RestController không có nghĩa “dùng Spring WebFlux”. Nó hoạt động trong cả **Spring MVC** và **Spring WebFlux**, tùy dependency, ApplicationContext và web stack đang kích hoạt. Để biết server thật dùng Servlet hay reactive, hãy kiểm tra runtime và cấu hình thực tế.

## 11. Vòng đời bean, Dependency Injection và kiến trúc controller

Vì controller là **Spring bean**, nó tham gia Dependency Injection, scope, lifecycle và có thể chịu tác động của các BeanPostProcessor.

Trong ví dụ runnable, cả PageController và GreetingRestController đều nhận GreetingService qua **constructor injection**. Spring giải quyết một bean GreetingService và tiêm vào controller. Nếu không có bean tương thích, việc tạo context thường thất bại với lỗi dependency injection thay vì âm thầm tạo endpoint thiếu nghiệp vụ.

### Vì sao constructor injection là lựa chọn tốt?

- Dependency được xác định rõ ngay trong constructor.
- Dễ tạo controller trong unit test bằng service fake.
- Field có thể final, tránh trạng thái chưa khởi tạo.
- Không cần dựa vào field injection và runtime reflection để cài từng field.

### Thread-safety và scope

Controller beans mặc định thường là **singleton**. Nhiều request có thể gọi **cùng một controller instance**, đồng thời trên nhiều thread (đối với MVC/Servlet).

**Lỗi không nên viết:**

~~~java
@RestController
class UnsafeController {

    private String currentName;

    @GetMapping("/unsafe")
    public String unsafe(@RequestParam String name) {
        currentName = name;
        return currentName;
    }
}
~~~

Đây là **minh họa lỗi concurrent state**, có thể gây request nọ trả giá trị của request kia. Controller không nên chứa mutable request-specific fields. Dữ liệu request nên lưu trong **biến cục bộ**, method parameters hoặc cơ chế scope/context được quản lý đúng.

Controller singleton không ngăn service có trạng thái độc lập, nhưng service cũng phải được thiết kế thread-safe nếu là singleton. @RestController không làm ứng dụng tự an toàn luồng.

### Validation và phân tầng

Controller chịu trách nhiệm **chuyển giao HTTP**: đọc path/query/body, áp dụng validation phù hợp, gọi service, lựa chọn status/response. Nghiệp vụ và giao dịch thường đặt ở service. Không dồn việc truy vấn database, gửi email, nhiều chiến lược xử lý dài vào controller nếu có thể tách thành thành phần chuyên trách.

Một API production thường bổ sung:

- DTO đầu vào/đầu ra và validation (@Valid cùng provider tương thích).
- @ControllerAdvice/@RestControllerAdvice để chuẩn hóa xử lý lỗi.
- Authentication/authorization trong filter/security chain.
- Logging/tracing, giới hạn payload và timeout.
- Tests cho mapping, content types và status codes.

**@ControllerAdvice không phải controller endpoint thông thường.** Nó giúp áp dụng exception handlers, binder/model methods cho phạm vi controller phù hợp. **@RestControllerAdvice** tương đương @ControllerAdvice có semantics @ResponseBody cho exception response handlers, không tự tạo một REST route vì có chữ RestController trong tên annotation.

## 12. Custom stereotype và cách Spring nhận diện qua meta-annotation

Do @Controller là stereotype được meta-annotated với @Component, một annotation riêng có thể biểu đạt ý đồ dự án bằng cách meta-annotate:

~~~java
package example.discovery.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Controller;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Controller
public @interface WebPage {
}
~~~

Đây là một **annotation Java hoàn chỉnh** (file **WebPage.java**), được Spring nhận diện là controller stereotype nếu component scanning và metadata xử lý theo cấu hình phù hợp.

Dùng:

~~~java
@WebPage
public class WelcomePageController {

    @GetMapping("/legacy-home")
    public String home() {
        return "greeting";
    }
}
~~~

Đoạn class là **ví dụ minh họa** cần import WebPage/@GetMapping, có view greeting và context MVC như phần trên.

**Điểm khác biệt:** custom stereotype mang **@Controller** thì thừa hưởng ý nghĩa controller; custom stereotype chỉ mang **@Component** mà không mang @Controller thường được scan thành bean nhưng **không đủ để được xem là annotated MVC controller**.

Khi tự tạo annotation mới, tránh nhân thêm layer trừu tượng không cần thiết cho dự án nhỏ. Nếu cần aliasing các thuộc tính annotation, phải xem **@AliasFor** và các yêu cầu khai báo meta-annotation ở phiên bản Spring tương ứng; không tùy ý đặt field rồi giả định nó sẽ được chuyển tiếp.

## 13. Proxy, context và những trường hợp controller khó phát hiện

### Controller bean bị bọc AOP proxy

Spring có thể dùng AOP proxy để triển khai transaction, security hay advice. Với controller có interface và proxy kiểu JDK, handler detection có thể không nhìn thấy annotations trên concrete class theo cách bạn tưởng. Tài liệu Spring khuyến nghị **class-based proxying** cho controller khi cần AOP proxy.

Không nên tự khắc phục bằng cách thêm @RestController lên từng method hoặc đặt controller mapping lên service. Xác định **kiểu proxy thực tế**, vị trí annotation, cấu hình AOP và version framework; khi cần có thể cấu hình class-based proxy phù hợp. Với Spring 6+, interface annotation/proxy cần đối chiếu Javadoc đúng phiên bản.

### Root context và DispatcherServlet context

Một ứng dụng Servlet truyền thống có thể tạo:

~~~text
Root WebApplicationContext:
   Service, Repository, security-related infrastructure...
      |
      | parent được child tham chiếu
      v
DispatcherServlet child WebApplicationContext:
   Controller, HandlerMapping, HandlerAdapter, ViewResolver...
~~~

**Child context có thể nhìn thấy bean trong parent**; parent không tự thấy bean chỉ tồn tại ở child. Trong Spring MVC truyền thống, controller thường đặt trong context của DispatcherServlet tương ứng. Nếu tạo nhiều DispatcherServlet, mỗi servlet có thể có mappings và controller context riêng.

Một ví dụ lỗi thiết kế là scan controller vào một context không được servlet phục vụ rồi cho rằng cứ có ApplicationContext trong JVM là HTTP request sẽ tìm thấy controller. Phải kiểm tra **bean visibility và hạ tầng HandlerMapping của đúng servlet**.

### Lazy initialization và profile

Nếu một controller bị ràng buộc **@Profile("admin")**, bean có thể chỉ tồn tại khi profile admin được kích hoạt. Nếu bean chưa được định nghĩa vì profile/condition không thỏa, Spring MVC không thể đăng ký handler cho nó. Với lazy initialization, đừng suy từ việc constructor đã chạy hay chưa mà kết luận mapping có tồn tại; hãy kiểm tra bean definitions/mapping registry phù hợp.

### Controller nằm ngoài dependency runtime

Maven/Gradle có thể compile class nhưng không đưa nó vào artifact đang chạy do sai module dependency hoặc packaging. Đọc đúng classpath, log startup và artifact/version được triển khai, nhất là trong hệ thống nhiều module. Java source trong editor không đồng nghĩa class đó đang tồn tại trong tiến trình web server.

## 14. Cách chẩn đoán controller không được phát hiện

Đi từ **đúng ứng dụng → đúng bean → đúng mapping → đúng HTTP request**:

### Bước 1 — Xác nhận đúng tiến trình và web stack

- Đúng cổng **server.port** và đúng context path chưa?
- Đúng instance/environment đang chạy? Có reverse proxy, Docker port mapping hoặc gateway đổi đường dẫn không?
- Dự án là **Spring MVC Servlet** hay **Spring WebFlux reactive**?
- Logs có cho thấy embedded server khởi động thành công không?

### Bước 2 — Xác nhận controller là Spring bean

- Có @Controller/@RestController hoặc custom annotation meta-annotated @Controller không?
- Class có nằm dưới package @SpringBootApplication hoặc các @ComponentScan tương ứng?
- Có filters, @Profile, @Conditional nào khiến bean bị loại?
- Có lỗi tạo bean do dependency injection? Nếu startup failed, đừng chỉ truy 404.
- Nếu không dùng Boot, XML/Java configuration đã scan hoặc đăng ký controller trong web context thích hợp chưa?

### Bước 3 — Xác nhận RequestMappingHandlerMapping đã đăng ký handler

- Method có @GetMapping/@PostMapping/@RequestMapping không?
- URL class-level @RequestMapping và method-level có ghép đúng?
- Có mapping trùng, class proxy hoặc interface annotation gây khó nhận diện không?
- Servlet nào sở hữu mapping này? Có nhiều ApplicationContexts không?
- Có auto-configuration/scan khác đã tạo bean nhưng MVC infrastructure chưa bật đúng cách không?

Trong môi trường phát triển, nếu bật **Spring Boot Actuator** và expose endpoint **mappings** một cách có kiểm soát, có thể kiểm tra mappings đã đăng ký. Cẩn trọng vì endpoint quản trị có thể chứa thông tin chi tiết ứng dụng; không mở công khai không kiểm soát. Không bắt buộc dùng Actuator nếu có startup log và application context inspection phù hợp.

### Bước 4 — Kiểm tra HTTP request thực tế

~~~bash
curl -i -v -H "Accept: application/json" \
  "http://localhost:8080/api/greetings/Lan"
~~~

Xem:

- Path, query string, method và base path.
- Content-Type cho request có body; Accept cho response cần nhận.
- Authentication, proxy, CORS, redirect và filters.
- **404** do route không tồn tại khác với **405** sai HTTP method, **406** media type response không đáp ứng, **415** request media type không được hỗ trợ.

Browser devtools hoặc Postman/curl hữu ích hơn đoán dựa trên URL trong code. Nếu có Spring Security, một endpoint hợp lệ có thể trả **401/403** trước khi controller được gọi.

### Bước 5 — Đọc logs đúng điểm

Một số dấu hiệu:

| Dấu hiệu | Nguyên nhân đáng kiểm tra |
|---|---|
| Ứng dụng Boot đã khởi động nhưng controller mới 404 | Package scan, profile/condition, URL, mapping chưa đăng ký |
| Startup báo **Ambiguous mapping** | Hai handler có điều kiện đăng ký không thể phân biệt |
| 404 chỉ với đường dẫn có tiền tố | Context path, servlet path hoặc class-level @RequestMapping |
| 405 cho URL tương tự | HTTP method khác handler mapping |
| API trả chữ **greeting** thay vì HTML | Có thể đổi @Controller thành @RestController khiến String thành body |
| MVC cố tìm template với tên **ok** | @Controller trả String view name khi người viết muốn body "ok" |
| JSON serialization thất bại | Converter/dependency, DTO serialization, kiểu return |
| Controller cần service nhưng context không startup | Missing bean, circular dependency, constructor injection/configuration |
| Endpoint biến mất chỉ ở profile production | Conditional registration, packaging, @Profile, config khác |
| Chỉ một DispatcherServlet nhận được controller | Sai hoặc khác biệt giữa child contexts và servlet mappings |

## 15. Kiểm chứng bằng test và quan sát cấu hình

Việc kiểm thử có thể chia thành ba lớp:

1. **Unit test của service/controller:** gọi Java method để kiểm tra nghiệp vụ. Không chứng minh URL mapping, scan hoặc converter hoạt động.
2. **MVC slice test:** @WebMvcTest với MockMvc trong ứng dụng Boot MVC; thường giới hạn context vào lớp web, cần import/mock service và các custom configurations phù hợp. Không được kết luận toàn app scan đúng chỉ từ slice test.
3. **Integration test với full context:** @SpringBootTest kết hợp MockMvc hoặc cổng HTTP thực để xác nhận component scan, handler mappings, converters, filters theo setup thực tế.

Ví dụ hành vi muốn test cho project ở phần 7:

- GET /greeting?name=Lan trả **HTML** và trong body có **Xin chào, Lan!**.
- GET /api/greetings/Lan với Accept: application/json trả JSON có **message**.
- POST /api/greetings với Content-Type: application/json và body hợp lệ trả **201**.
- GET /text?name=Mai trả **text/plain**; không cố tìm view tên câu chào.
- GET /headers có **X-Source: controller** ngay cả khi class dùng @Controller.
- Request sai method/media type phản hồi theo contract.
- Thử chuyển controller ra ngoài scan scope trong **môi trường kiểm thử riêng** thì mapping tương ứng phải không còn xuất hiện.

Trong test và môi trường production, cần tránh phụ thuộc vào chuỗi toàn bộ HTML/JSON có whitespace/order cố định khi không cần thiết. Kiểm tra **status, header, JSON field, một số HTML element quan trọng**, và những side effects nghiệp vụ thật sự.

## 16. Những nhầm lẫn thường gặp

**1. “@RestController tự tạo REST server.”** Không. Nó chỉ mô tả controller và @ResponseBody semantics. Server/DispatcherServlet hoặc WebFlux runtime được cấu hình riêng.

**2. “@Controller chỉ dành cho trang HTML.”** Không. @Controller có thể dùng @ResponseBody, ResponseEntity để trả body JSON/text.

**3. “@RestController + @GetMapping vẫn render template khi trả String.”** Không theo cách thông thường: String là response body, không phải logical view name.

**4. “Bất kỳ @Component nào có @GetMapping đều tạo endpoint.”** Không. MVC annotated handler detection tìm controller stereotype thích hợp.

**5. “@Controller có nghĩa chắc chắn đã được phát hiện.”** Không. Annotation không tự thêm class nằm ngoài component scan vào ApplicationContext.

**6. “Spring Boot tự scan toàn bộ Maven dependencies.”** Không. Mặc định scan từ package của @SpringBootApplication, ngoại lệ do explicit import/configuration/dependencies.

**7. “Đổi method name thì đổi URL.”** Không. URL được khai báo trong request mapping; method Java name chỉ là tên nội bộ (trừ các cơ chế configuration hiếm khác).

**8. “Mỗi @RestController chỉ có một endpoint.”** Không. Một bean có thể chứa nhiều handler methods.

**9. “@RequestMapping đủ cho mọi thứ, không cần xem HTTP method.”** @RequestMapping nếu không giới hạn method có semantics rộng; nên dùng shortcut annotation @GetMapping/@PostMapping tương ứng khi hợp đồng rõ ràng.

**10. “@RestController mặc nhiên trả application/json dù return String hay thiếu JSON converter.”** Không. Return type, converters và content negotiation quyết định body/media type.

**11. “Hai controller cùng URL thì framework luôn chọn class đầu tiên.”** Không. Mapping registry xem xét toàn bộ điều kiện; ambiguity có thể làm startup/request thất bại.

**12. “Controller singleton có thể lưu biến instance cho mỗi request.”** Nguy hiểm. Singleton bean có thể xử lý concurrent requests, tránh mutable request-specific state.

**13. “Dùng @ControllerAdvice là tự có URL mới.”** Không. Advice cung cấp xử lý chung, không tự tạo HTTP mapping.

**14. “Cần @EnableWebMvc trong mọi project Spring Boot để phát hiện @RestController.”** Không. Boot thường tự cấu hình MVC; thêm @EnableWebMvc có thể làm mất những mặc định của Boot.

**15. “@RestController chắc chắn là WebFlux.”** Không. MVC và WebFlux đều có annotated controller model; khác nhau ở web stack thực tế.

## 17. Tự kiểm tra và bài tập ứng dụng

**Câu 1:** Một class @Controller trả về String **"dashboard"** từ @GetMapping("/dashboard"), chuyện gì xảy ra?

**Đáp án:** Khi không có @ResponseBody hoặc return-value semantics đặc biệt khác, Spring MVC coi String là tên view **dashboard** và dùng view resolution. Nếu thiếu view/template thì có thể phát sinh lỗi render.

**Câu 2:** Cùng method trên nhưng class đổi thành @RestController?

**Đáp án:** Spring đưa chuỗi **dashboard** vào HTTP response body. Nó không tự render template dashboard.

**Câu 3:** Một class @Service chứa @GetMapping("/x") và được scan thành bean. GET /x có được map không?

**Đáp án:** Không chỉ nhờ @GetMapping. Annotated MVC handler detection yêu cầu controller stereotype hoặc handler configuration phù hợp.

**Câu 4:** Main class đặt trong example.app.bootstrap, controller trong example.app.web. Mặc định Boot có quét controller không?

**Đáp án:** Không nếu chỉ dùng scan mặc định từ package example.app.bootstrap; cần đặt main ở package cha example.app hoặc khai báo scan hợp lý.

**Câu 5:** Làm sao @Controller trả JSON mà không chuyển toàn class sang @RestController?

**Đáp án:** Thêm **@ResponseBody** vào handler cần trả body hoặc dùng **ResponseEntity<T>**.

**Câu 6:** @RestController có thể trả HTML không?

**Đáp án:** Có thể trả body có Content-Type text/html nếu endpoint chủ đích sinh HTML, nhưng return String của @RestController không được hiểu như view name; muốn server-side template view, @Controller phù hợp hơn.

**Câu 7:** Nếu đã có controller bean, chắc chắn URL được đăng ký chưa?

**Đáp án:** Chưa. MVC phải có infrastructure đúng context, nhận diện stereotype, tìm method mappings và đăng ký thành công.

**Câu 8:** Spring MVC quét toàn bộ classpath lại trên từng HTTP request?

**Đáp án:** Không. Mapping registry được xây trong quá trình khởi tạo và dùng khi dispatch request.

**Câu 9:** Vì sao controller chạy ở dev nhưng không có trong production?

**Đáp án:** Kiểm tra profiles, conditionals, package/classpath của artifact, deployment, context, web stack và URL/proxy trước khi sửa annotation.

**Câu 10:** Một @RestController dùng field String lastName để lưu query name, có an toàn không?

**Đáp án:** Thường không; controller mặc định singleton và có thể xử lý nhiều request đồng thời, cần giữ request state ở biến cục bộ hoặc mechanism đúng scope.

### Bài tập thực hành

Từ project chạy được ở phần 7, làm từng bước:

1. Thêm GET /api/greetings/default trả một GreetingResponse với thông điệp cố định, kiểm tra tương tác với pattern GET /api/greetings/{name}. Hãy quan sát mapping specificity thay vì đoán Spring luôn chọn một method.
2. Thêm @Controller method với @ResponseBody trả **text/plain**, kiểm chứng không tìm view resolver.
3. Đặt một controller mới ở package không thuộc scan, khởi động rồi quan sát mapping chưa có; chuyển về subpackage đúng và kiểm chứng.
4. Thử một endpoint cùng URL nhưng GET so với POST; xác nhận hai method HTTP được ánh xạ riêng.
5. Thêm handler có consumes=application/json, thử request Content-Type: text/plain và giải thích status.
6. Thêm Spring Security nếu chương trình học đã đề cập; xác định filter chain có thể chặn request trước MVC khi thiếu quyền, không nhầm đó là controller discovery error.

Không cần sửa code production để làm các thí nghiệm có tính phá cấu hình; dùng project học tập hoặc nhánh thử riêng.

## 18. Tài liệu chính thức

- [Spring Framework — Annotated Controllers: Declaration](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann.html): @Controller, @RestController, component scanning và lưu ý AOP proxy.
- [Spring Framework — Mapping Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html): @RequestMapping, @GetMapping, điều kiện mapping và pattern.
- [Spring Framework — Return Values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html): phân loại các kiểu trả về của handler.
- [Spring Framework — @ResponseBody](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responsebody.html): ghi HTTP response body và message converters.
- [Spring Framework — ResponseEntity](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html): điều khiển status, headers, body.
- [Spring Framework — Classpath Scanning](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html): stereotype annotations, meta-annotation, filters.
- [Spring Framework — DispatcherServlet](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html): request flow và context.
- [Spring Boot — Structuring Your Code](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html): package gốc của @SpringBootApplication và phạm vi component scan.
- [Spring Boot — Servlet Web Applications](https://docs.spring.io/spring-boot/reference/web/servlet.html): Spring MVC auto-configuration.
- [Spring Framework — Annotated Controllers in WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann.html): cùng ý tưởng annotation trên reactive stack.

**Điểm cần ghi nhớ:** **@Controller** đánh dấu lớp là Spring MVC controller bean và cho phép xử lý view hoặc response tùy return type. **@RestController** thêm @ResponseBody semantics để trả HTTP body thuận tiện. **Controller discovery** cần cả đăng ký Spring bean trong đúng context lẫn **RequestMappingHandlerMapping** tìm/đăng ký handler methods. Khi thấy 404, hãy xác định lần lượt **đúng ứng dụng → bean có tồn tại → mapping có đăng ký → request có khớp**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-routing-002">@RequestMapping ở lớp và phương thức; các annotation @GetMapping, @PostMapping</a>

<details>
<summary>Xem chi tiết</summary>

# @RequestMapping ở lớp và phương thức; @GetMapping và @PostMapping trong Spring MVC

## 1. Mục tiêu: biến URL và HTTP method thành một điểm xử lý

Trong Spring MVC, một controller có thể cung cấp nhiều endpoint. **`@RequestMapping`** mô tả điều kiện để một HTTP request được giao cho controller method, chẳng hạn đường dẫn, HTTP method, query parameter, header, kiểu dữ liệu gửi vào và kiểu phản hồi. Các annotation **`@GetMapping`** và **`@PostMapping`** là dạng chuyên biệt, diễn đạt rõ một request dùng GET hay POST.

Sau bài học, bạn có thể:

- Đặt `@RequestMapping` ở **mức class** để khai báo prefix chung, rồi đặt `@GetMapping` hoặc `@PostMapping` ở **mức method** để khai báo endpoint cụ thể.
- Viết cú pháp tương đương bằng `@RequestMapping(method = RequestMethod.GET/POST)` và hiểu lý do ưu tiên annotation chuyên biệt.
- Phân biệt **URL path**, **HTTP method**, **URI variable**, **query parameter**, **request body** và các điều kiện `params`, `headers`, `consumes`, `produces`.
- Phân biệt một route được ánh xạ thành công với việc Spring đã chuyển tham số, gọi business code và tạo response.
- Xây ví dụ Java 17/Spring Boot MVC chạy được, xác minh bằng `curl` và `MockMvc`, nhận biết các tình huống 404, 405, 400, 406 và 415.

Phạm vi ở đây là **Spring MVC trên Servlet stack**. Spring WebFlux cũng có nhiều annotation giống tên, nhưng dùng web runtime reactive và cơ chế điều phối riêng. Bài học mô tả mapping của MVC; nó không yêu cầu kiến thức chi tiết về WebFlux.

## 2. Vị trí của mapping trong luồng xử lý MVC

Khi ứng dụng có `DispatcherServlet` hoạt động, luồng điển hình:

```text
Client gửi GET /api/books/42
                 |
           Servlet container
                 |
           DispatcherServlet
                 |
      RequestMappingHandlerMapping
      - HTTP method là GET?
      - Path /api/books/42 khớp?
      - Params, headers, consumes, produces khớp?
                 |
           chọn HandlerMethod
                 |
       RequestMappingHandlerAdapter
       - resolve @PathVariable, @RequestBody...
       - gọi controller method
                 |
       return value / message converters
                 |
           HTTP response
```

**`@RequestMapping` không trực tiếp gửi HTTP response.** Nó cung cấp metadata để `RequestMappingHandlerMapping` xác định handler method thích hợp. Sau khi chọn handler, `HandlerAdapter` mới chuẩn bị tham số, gọi method và xử lý kiểu trả về.

Ví dụ:

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping("/{id}")
    public String byId(@PathVariable long id) {
        return "book:" + id;
    }
}
```

Đây là **fragment** (chưa có imports và ứng dụng chạy). Khi nhận `GET /api/books/42`, Spring kết hợp hai mapping thành một endpoint, lấy `id=42` và gọi `byId(42)`. Việc chuyển chuỗi `"42"` thành `long` thuộc giai đoạn resolve tham số, sau khi mapping đã xác định handler.

## 3. `@RequestMapping` tại mức class và mức method

### 3.1. Mức class: nhóm các endpoint cùng tiền tố

Đặt `@RequestMapping("/api/books")` lên class giúp mọi method bên trong có chung URL prefix, tránh lặp `/api/books`:

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping
    public String list() {
        return "list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id) {
        return "book:" + id;
    }

    @PostMapping
    public String create() {
        return "created";
    }
}
```

Đây là **ví dụ riêng để đọc cú pháp**, không phải class của dự án hoàn chỉnh ở mục 8.

| Khai báo trên class | Khai báo trên method | HTTP endpoint kết quả |
| --- | --- | --- |
| `@RequestMapping("/api/books")` | `@GetMapping` | `GET /api/books` |
| `@RequestMapping("/api/books")` | `@GetMapping("/{id}")` | `GET /api/books/42` |
| `@RequestMapping("/api/books")` | `@PostMapping` | `POST /api/books` |

**Mức class không tự sinh ra handler.** Mặc dù class có prefix, chỉ các handler method đã được đăng ký đúng mới trở thành endpoint. Không có `@GetMapping` tại method thì việc khai báo prefix ở class không mặc nhiên tạo `GET /api/books`.

`@RequestMapping` ở mức class không bắt buộc: bạn có thể viết đường dẫn đầy đủ trên từng method. Nó đặc biệt hữu ích với một nhóm tài nguyên cùng prefix, chẳng hạn `/api/users` hoặc `/admin/reports`.

### 3.2. Mức method: xác định thao tác cụ thể

Ở mức method, có thể viết:

```java
@RequestMapping(path = "/{id}", method = RequestMethod.GET)
public String read(@PathVariable String id) {
    return id;
}
```

Hoặc ngắn gọn hơn:

```java
@GetMapping("/{id}")
public String read(@PathVariable String id) {
    return id;
}
```

Hai dạng này cùng biểu diễn HTTP GET cho `/{id}` trong cùng ngữ cảnh class. Tương tự:

```java
@RequestMapping(path = "", method = RequestMethod.POST)
public String create() {
    return "created";
}
```

Có thể viết:

```java
@PostMapping
public String create() {
    return "created";
}
```

Các đoạn đều là **method fragment đặt bên trong controller**, không phải Java file chạy độc lập.

### 3.3. Không khai báo HTTP method có ý nghĩa gì?

`@RequestMapping("/ping")` **không giới hạn** request vào GET hoặc POST. Spring MVC có thể khớp nhiều HTTP method cho mapping đó theo các điều kiện còn lại. Hành vi này có thể làm một endpoint vô tình chấp nhận thao tác ngoài ý muốn.

Ví dụ:

```java
@RequestMapping("/ping")
public String ping() {
    return "pong";
}
```

Đừng mặc định hiểu đó là “GET route”. Nếu mục đích là endpoint chỉ để đọc trạng thái, hãy dùng `@GetMapping("/ping")`. Nếu một method **thực sự** hỗ trợ nhiều HTTP method, khai báo tường minh:

```java
@RequestMapping(
    path = "/shared",
    method = {RequestMethod.GET, RequestMethod.POST}
)
public String shared() {
    return "shared";
}
```

Dùng nhiều method phải có chủ đích: GET được kỳ vọng có tính **safe/read-only**, còn POST thường dùng để yêu cầu xử lý một hành động hoặc tạo tài nguyên. Không nên dùng GET cho thao tác ghi/chuyển trạng thái chỉ vì URL tiện gọi.

## 4. `@GetMapping`: biểu diễn thao tác đọc

`@GetMapping` là composed annotation được xây dựng trên `@RequestMapping(method = RequestMethod.GET)`. Nó nhận phần lớn các thuộc tính mapping quan trọng như `path`/`value`, `params`, `headers` và `produces`.

Ví dụ:

```java
@GetMapping(path = "/{id}", produces = "application/json")
public Book getBook(@PathVariable("id") long id) {
    return new Book(id, "Java");
}
```

Khi dùng `@RestController` với JSON converter như Jackson, Spring có thể chuyển `Book` thành JSON. Các kiểu `@PathVariable` và record `Book` phải tồn tại phù hợp; snippet này không phải chương trình độc lập.

Những tính chất nên ghi nhớ:

- GET thường dùng để **truy xuất tài nguyên**. Theo HTTP semantics, GET là phương thức safe; thiết kế endpoint tránh cố ý thay đổi trạng thái hệ thống.
- `@GetMapping("/{id}")` khớp đường dẫn với **một segment** được capture vào `id`; biến đường dẫn không phải query parameter.
- HTTP GET có thể có `Accept` để lựa chọn định dạng phản hồi; `produces` mô tả những media type handler có thể tạo.
- Spring MVC còn hỗ trợ HTTP **HEAD** thông qua GET mapping: HEAD tương ứng metadata/header như GET nhưng không gửi response body.
- Với HTTP **OPTIONS**, Spring MVC thường có hỗ trợ tự tổng hợp các HTTP method hợp lệ thành header `Allow`; ứng dụng có thể tùy chỉnh hành vi này.

### 4.1. `@PathVariable` và `@RequestParam`

Hai request khác nhau:

```text
GET /api/books/42        -> id nằm trên URI path
GET /api/books?id=42     -> id là query parameter
```

Hai cách lấy dữ liệu:

```java
@GetMapping("/{id}")
public String byPath(@PathVariable("id") long id) {
    return "path:" + id;
}

@GetMapping(params = "id")
public String byQuery(@RequestParam("id") long id) {
    return "query:" + id;
}
```

Đây là các method trong controller có `@RequestMapping("/api/books")`. Chúng có **hai kiểu mapping khác nhau**. Trong ví dụ thứ hai, `params = "id"` tham gia bước **chọn handler**; `@RequestParam("id")` giải quyết việc **lấy và chuyển giá trị** sau đó.

Một `@GetMapping` với `@RequestParam` nhưng **không** thêm `params` vẫn có thể được chọn trước khi framework phát hiện thiếu tham số và trả lỗi binding. Phân biệt điều kiện tìm handler với điều kiện bắt buộc khi giải mã đối số.

### 4.2. Nhiều path cho cùng handler

Khi hai URL có cùng hành vi, có thể dùng array:

```java
@GetMapping(path = {"/status", "/health"})
public String status() {
    return "ok";
}
```

Đây là hai path trỏ cùng một method. Tuy nhiên, nhiều tên URL cho một tài nguyên có thể tạo thêm vấn đề tài liệu API/cache; chỉ sử dụng khi thật sự cần giữ alias hoặc khả năng tương thích.

## 5. `@PostMapping`: gửi dữ liệu để xử lý hoặc tạo tài nguyên

`@PostMapping` tương đương `@RequestMapping(method = RequestMethod.POST)`. POST thường được dùng khi client gửi body để server xử lý một hành động hoặc tạo tài nguyên mới.

Một ví dụ điển hình:

```java
@PostMapping(
    consumes = "application/json",
    produces = "application/json"
)
public ResponseEntity<Book> create(
        @RequestBody CreateBookRequest request) {
    Book created = service.create(request);
    return ResponseEntity
            .created(URI.create("/api/books/" + created.id()))
            .body(created);
}
```

Đoạn trên là **mã minh họa**, giả định có `service`, `Book`, `CreateBookRequest` và imports. Nó không chạy độc lập. Dự án hoàn chỉnh ở mục 8 thay thế `service` bằng kho lưu dữ liệu trong bộ nhớ để người học tự chạy.

Các phần mang ý nghĩa khác nhau:

| Phần | Vai trò |
| --- | --- |
| `@PostMapping` | Giới hạn handler vào HTTP POST |
| `consumes = "application/json"` | Điều kiện request `Content-Type` hỗ trợ JSON |
| `@RequestBody` | Đọc body, dùng message converter chuyển thành Java object |
| `produces = "application/json"` | Cho biết dạng response có thể trả |
| `ResponseEntity.created(uri)` | Trả **201 Created** với header `Location` |
| `body(created)` | Đưa representation của tài nguyên vừa tạo vào response body |

Không nhầm **`@PostMapping`** với **`@RequestBody`**: POST là HTTP method, còn `@RequestBody` chỉ định cách lấy dữ liệu đầu vào từ body. POST cũng có thể nhận form data hoặc multipart tùy controller; JSON không phải định dạng duy nhất.

Với hành động thành công, mã 200, 201 hay 204 tùy API contract. `@PostMapping` **không tự đặt status thành 201**: nếu trả Java object thông thường từ `@RestController` mà không cấu hình status, phản hồi thành công thường là 200. Dùng `@ResponseStatus(HttpStatus.CREATED)` hoặc `ResponseEntity.created(...)` nếu thật sự muốn 201.

## 6. Thuộc tính của `@RequestMapping` và các annotation chuyên biệt

| Thuộc tính | Dạng ví dụ | Dùng để |
| --- | --- | --- |
| `value` hoặc `path` | `"/books/{id}"` | Ghép đường dẫn HTTP |
| `method` | `RequestMethod.GET` | Giới hạn HTTP method; được ấn định bởi `@GetMapping`/`@PostMapping` |
| `params` | `"view=compact"`, `"!debug"` | Query parameter condition |
| `headers` | `"X-Client=mobile"` | Header condition |
| `consumes` | `"application/json"` | Media type mà handler chấp nhận từ request body |
| `produces` | `"application/json"` | Media type phản hồi mà handler có thể tạo |
| `name` | `"books.detail"` | Đặt tên mapping khi cần tham chiếu/quan sát |

**`value` và `path` là các alias** cùng biểu diễn phần đường dẫn, không phải hai prefix khác nhau. Với annotation chuyên biệt, `method` đã được cố định: không viết `@GetMapping(method = ...)` vì thuộc tính đó không tồn tại ở `@GetMapping`.

### 6.1. Kết hợp các điều kiện ở class và method

Thông thường, path ở class được kết hợp với path ở method, còn những điều kiện như HTTP method, params và headers được kết hợp theo ngữ nghĩa của `RequestMappingInfo` tương ứng. Riêng **`consumes` và `produces` ở method ghi đè khai báo tương ứng tại class**, không đơn giản cộng dồn.

Ví dụ:

```java
@RestController
@RequestMapping(
    path = "/api/documents",
    produces = "application/json"
)
public class DocumentController {

    @GetMapping("/{id}")
    public DocumentDto detail(@PathVariable long id) {
        return new DocumentDto(id, "doc");
    }

    @GetMapping(path = "/plain", produces = "text/plain")
    public String plain() {
        return "doc";
    }

    public record DocumentDto(long id, String name) {}
}
```

Đây là **class minh họa**, cần cấu hình Spring và JSON converter khi thực thi:

- `GET /api/documents/42`: method kế thừa `produces=application/json`.
- `GET /api/documents/plain`: method khai báo `produces=text/plain` nên **ghi đè** giá trị class.
- Pattern literal `/plain` thường cụ thể hơn `/{id}` khi cả hai có thể khớp.

Nếu khai báo hạn chế HTTP method ở class (ví dụ chỉ GET), các method cấp dưới không được tự ý mở rộng thành POST bằng `@PostMapping`; nên đặt prefix chung ở class, còn HTTP method riêng ở từng method để tránh sự nhầm lẫn.

### 6.2. Điều kiện `params`

```java
@GetMapping(path = "/reports", params = "view=compact")
public String compact() {
    return "compact";
}

@GetMapping(path = "/reports", params = "!view")
public String defaultView() {
    return "default";
}
```

Đây là **hai method cùng controller**:

- `GET /reports?view=compact` chọn `compact()`.
- `GET /reports` không có `view` chọn `defaultView()`.
- `GET /reports?view=full` không thỏa hai điều kiện trên; Spring có thể báo lỗi điều kiện parameter (thường là 400) tùy cấu hình và các mapping khác.

`params="debug"` đòi tham số hiện diện; `params="!debug"` đòi tham số vắng mặt; `params="mode=fast"` đòi giá trị cụ thể. Đây là **điều kiện routing**, không đồng nghĩa logic validate mọi giá trị trong đối số Java.

### 6.3. Điều kiện `headers`

```java
@GetMapping(
    path = "/client-info",
    headers = "X-Client=mobile"
)
public String mobileInfo() {
    return "mobile";
}
```

Handler chỉ khớp khi request có header `X-Client` theo điều kiện khai báo. Với `Content-Type` và `Accept`, nên dùng `consumes`/`produces` vì chúng mô tả media type rõ ràng hơn.

### 6.4. `Content-Type` khác `Accept`

| Header | Nói về | Điều kiện thường liên quan |
| --- | --- | --- |
| `Content-Type: application/json` | Định dạng **body mà client gửi lên** | `consumes="application/json"` |
| `Accept: application/json` | Định dạng **response client chấp nhận** | `produces="application/json"` |

Một request POST có JSON body thường cần `Content-Type` phù hợp để `@RequestBody` parse. Khi client yêu cầu dạng response không được hỗ trợ, điều kiện `produces` hoặc content negotiation có thể đưa tới HTTP 406. Khi media type body không được hỗ trợ, có thể đưa tới HTTP 415.

Không nên kết luận rằng cứ gửi `Accept: application/json` thì body của request là JSON: `Accept` và `Content-Type` giải quyết hai phía khác nhau.

## 7. So khớp URL và chọn method cụ thể

### 7.1. Literal, biến URI, wildcard

Giả sử trong cùng controller:

```java
@GetMapping("/featured")
public String featured() { return "featured"; }

@GetMapping("/{id}")
public String byId(@PathVariable String id) { return id; }
```

Với `GET /api/books/featured`, literal `/featured` được ưu tiên so với biến `/{id}` dù thứ tự khai báo trong file Java thế nào. Với `GET /api/books/42`, handler `/{id}` khớp.

Spring MVC 6.x mặc định sử dụng `PathPatternParser`. Một số cú pháp tiêu biểu:

- `/items/{id}`: capture một segment thành biến `id`.
- `/items/{id:[0-9]+}`: chỉ khớp segment số theo regex được khai báo.
- `/assets/*.png`: wildcard trong một segment.
- `/assets/**`: bắt nhiều segment ở **cuối** pattern.

Với `PathPatternParser`, `**` và `{*variable}` chỉ được đặt ở cuối pattern. Không sử dụng pattern kiểu `/a/**/b` như thể nó luôn hợp lệ.

### 7.2. Không dựa vào thứ tự method trong mã nguồn

Spring MVC chọn mapping dựa trên các request conditions và quy tắc so sánh độ cụ thể. Phương thức khai báo trước không tự động “thắng”. Nếu nhiều method có mapping thực chất giống nhau, ứng dụng có thể lỗi startup; nếu hai mapping khác nhau nhưng cạnh tranh mà không xác định được duy nhất handler, có thể gặp ambiguous handler methods.

Ví dụ **cố tình sai**, không ghép vào ứng dụng chạy ở mục 8:

```java
@GetMapping("/books/{id}")
public String one(@PathVariable String id) { return id; }

@GetMapping("/books/{id}")
public String two(@PathVariable String id) { return "other:" + id; }
```

Hai method có cùng mapping. Đổi tên Java method không sửa được xung đột: phải sửa API contract (đường dẫn, HTTP method hoặc một điều kiện khác có ý nghĩa).

### 7.3. Context path không nằm trong annotation

Khi deploy ứng dụng dưới context path `/catalog`:

```text
URL ngoài:   http://localhost:8080/catalog/api/books/42
Context path:                      /catalog
Class @RequestMapping:                    /api/books
Method @GetMapping:                                 /{id}
```

Controller không tự thêm `/catalog` vào `@RequestMapping`. Context path được xác định bởi deployment/HTTP server configuration. Nếu đặt cả prefix vào annotation, bạn có thể vô tình tạo URL sai.

### 7.4. Trạng thái trailing slash

Ở Spring MVC hiện đại, đừng giả định `/api/books` và `/api/books/` luôn được coi là cùng một path. Hành vi tùy phiên bản và cấu hình path matching. Nếu API cần hỗ trợ cả hai, quyết định rõ chính sách: redirect, định nghĩa alias, hoặc cấu hình phù hợp, và có test chứng minh.

## 8. Ví dụ hoàn chỉnh: GET và POST trên cùng tài nguyên

Ví dụ Java 17 + Spring Boot **3.3.5** dùng Spring MVC, embedded Tomcat và Jackson qua `spring-boot-starter-web`. Dữ liệu được lưu **trong bộ nhớ của controller** để dễ thử; restart ứng dụng sẽ làm mất các sách thêm sau khi chạy. Đây là ví dụ học tập, không mô hình persistence cho production.

### 8.1. Cấu trúc file

```text
book-routing-demo/
├── pom.xml
└── src/
    ├── main/
    │   └── java/example/routing/
    │       ├── RoutingApplication.java
    │       └── BookController.java
    └── test/
        └── java/example/routing/
            └── BookControllerTest.java
```

### 8.2. Maven `pom.xml`

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
    <artifactId>book-routing-demo</artifactId>
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

Spring Boot parent quản lý các dependency version liên quan. Bạn cần JDK 17+ và Maven; các lệnh `mvn` dưới đây chạy trong thư mục có `pom.xml`. Không thêm `spring-boot-starter-webflux` để chạy ví dụ này.

### 8.3. Application entry point

File `src/main/java/example/routing/RoutingApplication.java`:

```java
package example.routing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RoutingApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoutingApplication.class, args);
    }
}
```

`@SpringBootApplication` kích hoạt auto-configuration/component scan. Vì controller nằm cùng package `example.routing`, Spring sẽ phát hiện bean khi ứng dụng khởi động.

### 8.4. Controller đầy đủ

File `src/main/java/example/routing/BookController.java`:

```java
package example.routing;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final Map<Long, Book> books = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(2);

    public BookController() {
        books.put(1L, new Book(1L, "Java căn bản"));
        books.put(2L, new Book(2L, "Spring MVC"));
    }

    // GET /api/books
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Book> list() {
        return books.values().stream()
                .sorted(Comparator.comparingLong(Book::id))
                .toList();
    }

    // GET /api/books/featured
    @GetMapping(path = "/featured",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public Book featured() {
        return books.get(1L);
    }

    // GET /api/books/{id}
    @GetMapping(path = "/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Book> find(@PathVariable("id") long id) {
        Book book = books.get(id);
        return book == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(book);
    }

    // POST /api/books
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Book> create(@RequestBody CreateBook request) {
        long id = nextId.incrementAndGet();
        Book book = new Book(id, request.title());
        books.put(id, book);

        return ResponseEntity
                .created(URI.create("/api/books/" + id))
                .body(book);
    }

    public record Book(long id, String title) {}
    public record CreateBook(String title) {}
}
```

**Các route Spring đăng ký:**

| HTTP request | Handler method | Kết quả tiêu biểu |
| --- | --- | --- |
| `GET /api/books` | `list()` | Mảng JSON các sách, sắp xếp theo ID |
| `GET /api/books/featured` | `featured()` | JSON sách ID 1 |
| `GET /api/books/1` | `find(1)` | HTTP 200, JSON sách ID 1 |
| `GET /api/books/999` | `find(999)` | HTTP 404 do business không có sách |
| `POST /api/books` với JSON | `create(...)` | HTTP 201, `Location` và JSON sách mới |

Phân tích mapping:

- Prefix `/api/books` đến từ **class**, còn `/featured` và `/{id}` đến từ **method**.
- `@GetMapping` và `@PostMapping` phân biệt **hai hành vi trên cùng path `/api/books`**.
- `/featured` có độ cụ thể cao hơn `/{id}`; request không bị ép `"featured"` sang `long`.
- Với `/api/books/999`, handler **được tìm thấy** nhưng business method trả 404; khác lỗi không có route.
- `create` nhận JSON body qua `@RequestBody` rồi trả `201 Created` với `Location: /api/books/3` cho request tạo đầu tiên.
- `ConcurrentHashMap` và `AtomicLong` giúp ví dụ tránh lỗi truy cập dữ liệu cơ bản khi nhiều request tới cùng lúc; chúng **không** thay persistence, transaction, validation hoặc business rules thực tế.

### 8.5. Chạy ứng dụng và gửi request

Tại `book-routing-demo`, chạy test trước rồi bật web server:

```bash
mvn test
mvn spring-boot:run
```

Lệnh thứ hai giữ terminal chạy; mở một terminal khác. Với cấu hình mặc định và cổng 8080 còn trống, gửi:

```bash
curl -s http://localhost:8080/api/books
```

Body dự kiến:

```json
[{"id":1,"title":"Java căn bản"},{"id":2,"title":"Spring MVC"}]
```

Tìm theo ID:

```bash
curl -s http://localhost:8080/api/books/1
```

Body:

```json
{"id":1,"title":"Java căn bản"}
```

Xem đường dẫn literal `/featured`:

```bash
curl -s http://localhost:8080/api/books/featured
```

Body cũng là sách ID 1. Ví dụ này cho thấy Spring chọn `featured()` thay vì cố chuyển `featured` thành `long` cho `find(...)`.

Gửi POST tạo sách (cú pháp dòng lệnh POSIX):

```bash
curl -i -X POST http://localhost:8080/api/books \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"title":"HTTP và REST"}'
```

Phản hồi dự kiến có status và header (bỏ qua các header phụ thuộc server):

```http
HTTP/1.1 201
Location: /api/books/3
Content-Type: application/json

{"id":3,"title":"HTTP và REST"}
```

**ID 3** giả định đây là lần tạo sách đầu tiên kể từ khi khởi động. Nếu đã POST trước đó, `AtomicLong` tiếp tục tăng, nên ID khác. Dòng `Location` ở đây dùng path tương đối hợp lệ cho ví dụ; khi có gateway, context path, reverse proxy hoặc nhiều domain, cần chọn cách tạo URI theo deployment thật.

Sau đó:

```bash
curl -s http://localhost:8080/api/books/3
```

Body sau khi tạo lần đầu:

```json
{"id":3,"title":"HTTP và REST"}
```

Ví dụ không áp dụng bean validation cho trường `title`; production cần kiểm tra nội dung, độ dài, uniqueness, authorization và lưu trữ bền vững theo yêu cầu. Không copy kho in-memory làm tầng lưu trữ thật.

### 8.6. Cùng path, khác HTTP method

Hai endpoint này đều có **path `/api/books`**:

```text
GET  /api/books -> list()
POST /api/books -> create(...)
```

`GET /api/books` không tự gọi `create`; `POST /api/books` không tự gọi `list`. Nếu thử:

```bash
curl -i -X PUT http://localhost:8080/api/books
```

Trong demo, Spring MVC có path tương ứng nhưng không hỗ trợ PUT cho endpoint đó, nên kết quả thường là **405 Method Not Allowed**. Điều này khác **404 Not Found**, vốn thường biểu thị không có route phù hợp cho path hoặc business trả not-found.

Lưu ý `HEAD` có thể được hỗ trợ tự động khi đã có GET, và `OPTIONS` có thể được Spring xử lý theo các method đã đăng ký. Đừng liệt kê “chỉ GET/POST tuyệt đối” mà bỏ qua quy tắc HTTP infrastructure này.

## 9. Kiểm thử mapping bằng `MockMvc`

Thêm file `src/test/java/example/routing/BookControllerTest.java`:

```java
package example.routing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void combinesClassAndMethodMappings() throws Exception {
        mvc.perform(get("/api/books/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Java căn bản"));

        mvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void postCreatesBookWithLocation() throws Exception {
        mvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"HTTP và REST\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/books/3"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.title").value("HTTP và REST"));
    }

    @Test
    void rejectsUnsupportedHttpMethod() throws Exception {
        mvc.perform(put("/api/books"))
                .andExpect(status().isMethodNotAllowed());
    }
}
```

`@WebMvcTest(BookController.class)` tạo **MVC test slice**: kiểm tra controller, route resolution, message conversion và MVC configuration cần thiết, không cần khởi động một TCP HTTP server thật. `MockMvc` gửi request qua servlet testing pipeline và kiểm tra HTTP status, header, JSON response.

Vì chỉ test POST một lần trong class, ID đầu tiên được tạo dự kiến là 3. Với các test thực tế có nhiều thao tác ghi hoặc chạy song song, không nên dựa vào trạng thái chia sẻ giữa test methods; hãy cô lập trạng thái, mock service hoặc reset dữ liệu.

Chạy:

```bash
mvn test
```

Kỳ vọng ba test methods hoàn tất thành công nếu project được tạo đúng, dependency được tải và môi trường không can thiệp. Bài viết nêu **kết quả dự kiến theo code**, không khẳng định mọi máy đều đã chạy test thành công.

## 10. Các lỗi thường gặp khi dùng mapping

### 10.1. Gắn nhiều annotation mapping lên cùng một method

**Sai:**

```java
@GetMapping("/x")
@PostMapping("/x")
public String both() {
    return "x";
}
```

`@GetMapping` và `@PostMapping` đều là composed `@RequestMapping`. Spring không hỗ trợ dùng đồng thời nhiều annotation mapping như vậy trên **cùng một element**; có thể log warning và chỉ nhận một mapping. Muốn hỗ trợ GET lẫn POST, dùng `@RequestMapping(path="/x", method={RequestMethod.GET, RequestMethod.POST})`, hoặc tách thành hai method có contract riêng.

### 10.2. Nhầm class-level mapping tự tạo endpoint

`@RequestMapping("/api")` ở class chỉ là **metadata chung**. Cần có method tương ứng được Spring nhận diện; nếu class không phải controller bean hoặc thiếu method mapping, không có endpoint mong muốn.

### 10.3. Sai HTTP method

`@GetMapping("/save")` có thể cho phép client truy cập để “lưu” bằng GET, nhưng việc ghi dữ liệu qua GET vi phạm kỳ vọng safe HTTP và có thể gặp prefetch, cache hoặc crawler. Chọn `@PostMapping` cho tạo/thao tác xử lý mới, `@PutMapping` cho thay thế cập nhật, `@PatchMapping` cho cập nhật từng phần, `@DeleteMapping` cho xóa theo API contract.

### 10.4. Nhầm `@PathVariable` với `@RequestParam`

`/books/42` khác `/books?id=42`. Viết `@GetMapping("/{id}")` mà thử `/books?id=42` thì không tự thỏa path pattern. Phải định nghĩa một endpoint phù hợp với kiểu tham số thực tế.

### 10.5. Trùng mapping

Hai method cùng `@GetMapping("/api/books/{id}")` trong cùng handler registry có thể gây **ambiguous mapping khi ứng dụng khởi động**. Thay đổi tên Java method không đủ; cần phân biệt path, HTTP method hoặc điều kiện request có ý nghĩa.

### 10.6. 404 khác 405, 406 và 415

| Trạng thái | Khả năng điển hình | Kiểm tra |
| --- | --- | --- |
| 404 | Không có mapping cho path hoặc resource không tồn tại | Prefix class/method, context path, path variable, controller scan và code trả 404 |
| 405 | Path có handler mapping nhưng HTTP method không được chấp nhận | GET/POST/PUT/DELETE, `Allow` header |
| 406 | Client chỉ chấp nhận response media type khác `produces` | `Accept` và các message converter |
| 415 | Request body `Content-Type` không được hỗ trợ | `consumes` và các message converter |
| 400 | Sai body JSON, thiếu tham số bắt buộc, type conversion hoặc params condition | `@RequestBody`, `@RequestParam`, `@PathVariable`, nội dung request |
| 500 / startup failure | Đăng ký mapping trùng, lỗi bean hoặc code xử lý | Startup log và root cause |

Một HTTP status **không chứng minh duy nhất nguyên nhân**, vì exception handler, security và các tầng khác có thể thay đổi kết quả. Hãy quan sát cả request, mapping registry, converter và log server.

### 10.7. Mặc định trailing slash và path matching

`GET /api/books` không luôn được hiểu giống `GET /api/books/`. Spring 6.x thay đổi một số mặc định so với các thế hệ cũ. Nếu API cần cả hai dạng, kiểm thử rõ thay vì dựa vào thói quen của phiên bản trước.

### 10.8. Dùng `@RequestMapping` ở class với `method` quá hẹp

Ví dụ đặt `@RequestMapping(path="/api/books", method=GET)` trên class rồi viết `@PostMapping` trong class đó là thiết kế mâu thuẫn về HTTP method restriction. Cách phổ biến: class giữ **path prefix**, method quyết định GET/POST cụ thể.

### 10.9. Gắn mapping sai stack

Spring MVC và WebFlux có một số annotation cùng package, nhưng runtime điều phối khác nhau. Ví dụ dùng `spring-boot-starter-web` tạo MVC server thông thường, còn `spring-boot-starter-webflux` tạo reactive web stack nếu Boot cấu hình tương ứng. Chỉ thấy `@GetMapping` trong mã nguồn chưa đủ xác định là MVC hay WebFlux.

## 11. Những quy tắc thiết kế endpoint nên dùng

1. **Đặt prefix theo tài nguyên**: `/api/books`, `/api/orders` và chia các thao tác bằng HTTP method hoặc sub-resource path hợp lý.
2. **Dùng annotation chuyên biệt ở method**: `@GetMapping`, `@PostMapping`… để method constraint rõ ngay khi đọc code.
3. **Không đưa động từ vào GET để làm hành động ghi**: ví dụ `GET /createBook` là API contract dễ gây tác dụng phụ ngoài dự kiến.
4. **Trả HTTP status có ý nghĩa**: GET thành công thường 200; POST tạo tài nguyên thường 201 kèm `Location`; tài nguyên không tồn tại có thể 404; body/method không phù hợp có thể 400/405/415/406.
5. **Phân biệt routing và validation**: chọn handler dựa trên path/method/params/header/media type; sau đó validation của body và business rules được xử lý bởi các cơ chế tương ứng.
6. **Kiểm thử route chính và edge cases**: cùng path khác method, path literal cạnh path variable, media type mismatch, URI không hợp lệ, không tồn tại dữ liệu.
7. **Không tạo mapping quá rộng**: `/**` hoặc method-unrestricted mapping có thể vô tình nhận nhiều request; nên chủ động giới hạn.
8. **Tách nghiệp vụ khỏi controller trong dự án thật**: demo in-memory ở mục 8 để dễ quan sát; hệ thống vận hành nên dùng service/repository, transaction, validation và security thích hợp.

## 12. Bài tập và tự kiểm tra

### Bài 1 — dự đoán endpoint

Cho:

```java
@RestController
@RequestMapping("/students")
class StudentController {
    @GetMapping
    String all() { return "all"; }

    @GetMapping("/{id}")
    String one(@PathVariable String id) { return id; }

    @PostMapping
    String add() { return "created"; }
}
```

Hãy xác định handler cho `GET /students`, `GET /students/15`, `POST /students` và `PUT /students`. Đáp án: lần lượt `all()`, `one("15")`, `add()`; PUT không có handler hỗ trợ và thường nhận 405.

### Bài 2 — chuyển annotation

Viết lại `@RequestMapping(path="/products/{id}", method=RequestMethod.GET)` bằng annotation rút gọn. **Đáp án:** `@GetMapping("/products/{id}")`.

Ngược lại, viết `@PostMapping(path="/products", consumes="application/json")` thành `@RequestMapping`. **Đáp án:**

```java
@RequestMapping(
    path = "/products",
    method = RequestMethod.POST,
    consumes = "application/json"
)
```

Đây là **annotation fragment**, dùng trên method của controller.

### Bài 3 — phân tích request body

Một method `@PostMapping(consumes="application/json", produces="application/json")` nhận `@RequestBody`. Client gửi `Content-Type: text/plain` kèm chuỗi JSON. Vì sao chỉ có “văn bản trông giống JSON” là chưa đủ? **Gợi ý:** `consumes` kiểm tra media type của request, không chỉ kiểm tra các ký tự body.

### Bài 4 — literal và biến URI

Trong controller có `@GetMapping("/new")` và `@GetMapping("/{id}")`. Hãy giải thích vì sao `GET /new` thường chọn literal route, kể cả khi method cho biến `id` xuất hiện trước trong file.

### Bài 5 — cùng path GET và POST

Hãy thiết kế API nhỏ:

- `GET /notes`: đọc danh sách.
- `POST /notes`: nhận JSON và thêm một ghi chú.
- `GET /notes/{id}`: đọc một ghi chú.
- POST thành công: trả 201 và `Location`.

Sử dụng một `@RequestMapping("/notes")` ở class và annotation chuyên biệt trên ba method. Viết `MockMvc` test để chứng minh GET không gọi POST và POST không trả kết quả danh sách.

### Câu hỏi ghi nhớ

- **`@RequestMapping` đặt được ở đâu?** — Class và method; class thường cung cấp prefix/điều kiện chung, method bổ sung điều kiện endpoint.
- **`@GetMapping` khác `@RequestMapping` như thế nào?** — Là composed annotation chuyên biệt có `method=GET`.
- **`@PostMapping` có tự trả 201 không?** — Không; controller cần khai báo status hoặc trả `ResponseEntity` phù hợp.
- **Hai method có cùng path nhưng một GET một POST có hợp lệ không?** — Có, vì HTTP method là một phần điều kiện mapping.
- **`params` có giống `@RequestParam` không?** — Không; `params` giới hạn route, `@RequestParam` lấy/convert đối số khi handler được gọi.
- **`consumes` dựa vào header nào?** — Thông thường `Content-Type` của request.
- **`produces` liên quan header nào?** — `Accept` và cơ chế content negotiation của response.
- **Spring có chọn method theo vị trí dòng trong file không?** — Không; dựa trên request conditions và độ cụ thể.

## 13. Nguồn tham khảo chính thức

- Spring MVC Mapping Requests: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
- Spring MVC Annotated Controllers: https://docs.spring.io/spring-framework/reference/web/webmvc/controller.html
- `@RequestMapping` Javadoc: https://docs.spring.io/spring-framework/docs/6.1.8/javadoc-api/org/springframework/web/bind/annotation/RequestMapping.html
- `@GetMapping` Javadoc: https://docs.spring.io/spring-framework/docs/6.1.8/javadoc-api/org/springframework/web/bind/annotation/GetMapping.html
- `@PostMapping` Javadoc: https://docs.spring.io/spring-framework/docs/6.1.8/javadoc-api/org/springframework/web/bind/annotation/PostMapping.html
- `MockMvc`: https://docs.spring.io/spring-framework/reference/testing/mockmvc.html

**Tóm điểm chính:** `@RequestMapping` ở class gom những đường dẫn và điều kiện chung; `@GetMapping`/`@PostMapping` ở method chỉ ra **hành động nào xử lý từng HTTP method**. Spring MVC kết hợp path, HTTP method và các điều kiện còn lại để chọn handler. Sau khi chọn xong, cơ chế binding, converter và controller code mới tạo response.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-routing-003">Điều kiện ánh xạ theo HTTP method, params, headers, consumes và produces</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện ánh xạ theo HTTP method, params, headers, consumes và produces trong Spring MVC

## 1. Mục tiêu: một đường dẫn chưa đủ để xác định controller method

Trong Spring Web MVC, `@RequestMapping` và các annotation rút gọn như `@GetMapping`, `@PostMapping` không chỉ chọn endpoint theo URI. Spring có thể yêu cầu **đồng thời** đúng HTTP method, request parameters, headers, kiểu nội dung request (`consumes`) và kiểu nội dung mà client chấp nhận (`produces`).

Giả sử ứng dụng nhận:

~~~http
POST /api/items?source=mobile HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Accept: application/json
X-Client: mobile

{"name":"Notebook"}
~~~

Các phần mang ý nghĩa riêng:

- `POST`: **HTTP method**; mô tả thao tác mà client muốn thực hiện.
- `/api/items`: **path** của tài nguyên/endpoint.
- `source=mobile`: **request parameter** dùng để lọc mapping bằng `params` nếu hợp đồng endpoint yêu cầu.
- `X-Client: mobile`: **header** dùng để lọc mapping bằng `headers` nếu được khai báo.
- `Content-Type: application/json`: kiểu representation **client gửi lên**; liên quan `consumes`.
- `Accept: application/json`: kiểu representation **client muốn server trả về**; liên quan `produces`.
- JSON body: dữ liệu thực tế được `@RequestBody`/message converter đọc **sau** khi chọn được handler phù hợp. Nội dung body **không** phải điều kiện `params` và không được so khớp bằng `consumes` theo từng thuộc tính JSON.

**Mục tiêu bài học:** nhìn vào một HTTP request và dự đoán controller method nào sẽ được chọn, giải thích vì sao request bị từ chối (ví dụ 405/415/406/400), và viết mapping rõ ràng, tránh trùng/khó hiểu. Bài tập trung vào **Spring MVC annotated controllers**, không chuyển sang chi tiết xây dựng router Spring WebFlux.

## 2. Bảng tổng quan năm nhóm điều kiện

| Annotation attribute | Đọc từ request | Ví dụ | Ý nghĩa |
| --- | --- | --- | --- |
| `method` / `@GetMapping` ... | HTTP method | `GET`, `POST` | Loại thao tác HTTP có thể vào endpoint |
| `params` | Request parameters (thường query hoặc form data) | `mode=full`, `debug`, `!debug` | Chỉ chọn mapping khi điều kiện tham số thỏa |
| `headers` | HTTP headers | `X-Client=mobile`, `X-Tenant` | Chỉ chọn mapping khi điều kiện header thỏa |
| `consumes` | `Content-Type` của request | `application/json` | Endpoint có chấp nhận media type của body đến không? |
| `produces` | `Accept` của request | `application/json`, `text/plain` | Endpoint có tạo được representation client chấp nhận không? |

Ví dụ một mapping có nhiều điều kiện:

~~~java
@PostMapping(
    path = "/items",
    params = "source=mobile",
    headers = "X-Client=mobile",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
public Item create(@RequestBody CreateItemRequest input) {
    // ... xử lý ...
}
~~~

Đoạn minh họa cần `Item`, `CreateItemRequest`, biến thể controller và import; ví dụ **chạy độc lập đầy đủ** nằm ở phần 9.

Một request phải đáp ứng **tất cả các nhóm điều kiện đã khai báo**. Nếu `POST`, URI và `Content-Type` phù hợp nhưng thiếu `source=mobile`, mapping này vẫn không phù hợp. Spring có thể tìm mapping khác, hoặc trả lỗi nếu không có lựa chọn hợp lệ.

### Phân biệt phép AND và OR

- Các **nhóm điều kiện khác loại** trên cùng mapping được kết hợp như phép **AND**.
- Một mảng `params = {"a=1","b=2"}` đòi hỏi **cả hai** điều kiện.
- Một mảng `headers = {"X-Client=mobile","X-Feature"}` cũng đòi hỏi **cả hai**.
- Nhiều giá trị media trong `consumes = {"application/json","text/plain"}` thường là **các lựa chọn thay thế** trong cùng nhóm (OR). `produces` tương tự.
- Nhiều HTTP method trong `@RequestMapping(method = {GET, POST})` là **các lựa chọn thay thế**. Tuy vậy, thông thường viết hai method chuyên trách sẽ dễ đọc và đúng ngữ nghĩa HTTP hơn.
- Nhiều path trong mảng mapping cũng là các lựa chọn URI; không có nghĩa phải khớp đồng thời tất cả path.

Spring biểu diễn tập điều kiện dưới dạng `RequestMappingInfo`, rồi dùng các `RequestCondition` tương ứng để kiểm tra request và so độ cụ thể.

## 3. Chọn theo HTTP method: GET, POST, PUT, PATCH và DELETE

Có thể khai báo tổng quát:

~~~java
@RequestMapping(path = "/items", method = RequestMethod.GET)
public List<Item> list() { ... }
~~~

Hoặc dùng annotation rút gọn, dễ thấy mục đích hơn:

~~~java
@GetMapping("/items")
public List<Item> list() { ... }

@PostMapping("/items")
public Item create(@RequestBody CreateItemRequest input) { ... }
~~~

Hai snippet trên **chỉ minh họa cú pháp**; phần thân và class hỗ trợ được lược bỏ. Chúng không thể đặt chung với một `@GetMapping("/items")` khác trùng y hệt mà không có điều kiện phân biệt.

| Annotation | Tương đương về mapping method | Ngữ nghĩa HTTP điển hình |
| --- | --- | --- |
| `@GetMapping` | `RequestMethod.GET` | Lấy representation; safe và idempotent theo HTTP |
| `@PostMapping` | `RequestMethod.POST` | Gửi dữ liệu để xử lý/tạo tài nguyên; không mặc định idempotent |
| `@PutMapping` | `RequestMethod.PUT` | Tạo/thay thế trạng thái đích theo hợp đồng; idempotent |
| `@PatchMapping` | `RequestMethod.PATCH` | Cập nhật từng phần; không mặc định idempotent |
| `@DeleteMapping` | `RequestMethod.DELETE` | Yêu cầu xóa; idempotent về hiệu ứng dự định |

**Điểm dễ bỏ sót:** `@RequestMapping("/items")` ở **method level** mà **không khai báo `method`** mặc định không giới hạn HTTP method. Đừng dùng để thay cho `@GetMapping` nếu endpoint chỉ được phép đọc.

### Khi method không hợp lệ: 405

Ví dụ chỉ có `@GetMapping("/api/items/7")` nhưng client gửi `POST /api/items/7`. Nếu path phù hợp mà phương thức HTTP không được hỗ trợ, Spring MVC thường phản hồi **405 Method Not Allowed** và có thể cung cấp header `Allow`. Đó là khác biệt với **404 Not Found** khi không có mapping path phù hợp.

**HEAD và OPTIONS:** `@GetMapping` hỗ trợ HEAD theo cơ chế MVC/Servlet trong các trường hợp thông thường, không cần viết một method riêng chỉ để bỏ body. Spring MVC cũng có xử lý OPTIONS mặc định dựa vào những HTTP method được mapping. Vì vậy không nên khẳng định rằng `@GetMapping` chỉ có thể nhận GET ở mọi trường hợp bất kể framework hỗ trợ HEAD.

## 4. Điều kiện params: hiện diện, vắng mặt hoặc giá trị cụ thể

`params` kiểm tra **request parameter** để lọc mapping. Ví dụ:

~~~java
@GetMapping(path = "/search", params = "mode=summary")
public String summary() { return "summary"; }

@GetMapping(path = "/search", params = "mode=full")
public String full() { return "full"; }
~~~

Khi gửi:

~~~http
GET /search?mode=full HTTP/1.1
Host: localhost:8080
~~~

chỉ method `full()` phù hợp với điều kiện ở ví dụ.

Các biểu thức thông dụng:

| Biểu thức | Ý nghĩa | Ví dụ request khớp |
| --- | --- | --- |
| `params = "mode"` | Có parameter tên `mode` | `?mode=full` hoặc `?mode=` |
| `params = "!mode"` | Không có parameter `mode` | `/search` |
| `params = "mode=full"` | Giá trị bằng `full` | `?mode=full` |
| `params = "mode!=full"` | Không có parameter hoặc giá trị khác `full` theo ngữ nghĩa điều kiện của Spring | `/search?mode=summary` |
| `params = {"mode=full", "page=1"}` | Cả hai cùng thỏa | `?mode=full&page=1` |

Nếu chỉ muốn chặn tham số không cho dùng, có thể dùng `!mode`; nếu muốn bắt buộc tham số tồn tại, dùng `mode`. Không nên dùng `mode!=full` như một cách bắt buộc parameter tồn tại và có giá trị khác `full`: biểu thức phủ định có thể cũng khớp khi tham số vắng mặt.

### params khác @RequestParam thế nào?

~~~java
@GetMapping(path = "/reports", params = "format=csv")
public String report(@RequestParam(defaultValue = "1") int page) {
    return "page=" + page;
}
~~~

- `params="format=csv"`: **điều kiện chọn controller method**; nếu request không có `format=csv`, method này không phải ứng viên.
- `@RequestParam int page`: **giải đối số cho method đã chọn**; ở đây `page` mặc định là 1 nếu client không gửi.
- Có thể dùng `@RequestParam("format") String format` để lấy giá trị parameter vào method body, nhưng nó không tự biến thành điều kiện chọn mapping kiểu `params="format=csv"`.

`params` có thể xét request parameters bao gồm những parameter được Servlet API cung cấp từ form URL-encoded tùy request. Dữ liệu JSON nằm trong `@RequestBody` **không** trở thành một tập request parameters chỉ vì có các thuộc tính giống tên.

### Thiếu điều kiện params trả status nào?

Nếu path và HTTP method khớp với mapping nhưng tất cả ứng viên đều bị loại **chỉ vì điều kiện params**, Spring MVC thường trả **400 Bad Request** qua cơ chế báo unsatisfied parameters. Tuy nhiên, nên kiểm thử chính xác bằng `MockMvc` hoặc integration test vì exception handler tùy biến và các điều kiện khác có thể làm thay đổi status.

## 5. Điều kiện headers: quyết định theo metadata HTTP

`headers` dùng biểu thức gần giống `params`, nhưng kiểm tra HTTP headers.

~~~java
@GetMapping(path = "/clients", headers = "X-Client=mobile")
public String mobile() { return "mobile"; }

@GetMapping(path = "/clients", headers = "X-Client=web")
public String web() { return "web"; }
~~~

Request:

~~~http
GET /clients HTTP/1.1
Host: localhost:8080
X-Client: mobile
~~~

sẽ phù hợp với method `mobile()`. Điều kiện có thể viết:

- `headers = "X-Client"`: có header tên `X-Client`.
- `headers = "!X-Client"`: header không xuất hiện.
- `headers = "X-Client=mobile"`: header có giá trị mong muốn.
- `headers = "X-Client!=mobile"`: phủ định giá trị.
- `headers = {"X-Client=mobile", "X-Feature"}`: hai điều kiện phải thỏa.

Tên header HTTP **không phân biệt hoa/thường**; giá trị header cần xét theo semantics cụ thể của header và cách matcher xử lý. Vì vậy không nên nhầm `X-Client` và `x-client` là hai header khác nhau.

**Không dùng `headers` để thay cho `consumes`/`produces`.** Spring có thể hỗ trợ biểu thức dựa trên `Content-Type`/`Accept` ở `headers`, nhưng các trường đó có cú pháp media type, wildcard và quality negotiation; sử dụng `consumes`/`produces` thể hiện đúng ý định và có logic so khớp chuyên biệt.

### Header matching không tự chứng minh quyền truy cập

Một client tự gửi `X-Client: mobile` có thể giả mạo giá trị ấy. Điều kiện mapping không thay thế authentication, authorization, signature verification hay tenant isolation. Nếu header quyết định đường xử lý đặc quyền, cần kiểm soát security ở lớp phù hợp, không tin một chuỗi client cung cấp.

## 6. consumes: server nhận được Content-Type nào?

`consumes` là điều kiện về **media type mà handler có thể tiêu thụ từ request body**, thường dựa vào `Content-Type` request.

~~~java
@PostMapping(
    path = "/items",
    consumes = MediaType.APPLICATION_JSON_VALUE
)
public void create(@RequestBody ItemInput input) {
    // ...
}
~~~

Request phù hợp:

~~~http
POST /items HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{"name":"Bút"}
~~~

Request không phù hợp với điều kiện trên nếu gửi:

~~~http
POST /items HTTP/1.1
Host: localhost:8080
Content-Type: text/plain

Bút
~~~

Nếu đúng path và method nhưng media type request không được mapping chấp nhận, Spring MVC thường phản hồi **415 Unsupported Media Type**. Sau bước chọn mapping, framework còn phải có `HttpMessageConverter` phù hợp để đọc body thành Java type; đủ điều kiện `consumes` **không bảo đảm** deserialize thành công.

### Media type và wildcard

`consumes` có thể dùng:

~~~java
consumes = "application/json"
consumes = {"application/json", "application/xml"}
consumes = "application/*"
consumes = "!text/plain"
~~~

- Một danh sách media type là những lựa chọn được phép; không đòi hỏi body vừa là JSON vừa là XML.
- Wildcard media type chỉ nên dùng khi các converter và hợp đồng dữ liệu thực sự hỗ trợ.
- Negation thể hiện media type bị loại trừ nhưng **không tự tạo** khả năng đọc mọi media còn lại.
- `application/json;charset=UTF-8` có thể tương thích với điều kiện `application/json` theo quy tắc media type; không dùng so sánh chuỗi thô để tự viết lại logic này.
- Trong trường hợp request không có body hoặc không khai báo Content-Type, hành vi matching còn phụ thuộc ngữ cảnh; đừng mặc định `consumes="application/json"` luôn hoạt động giống nhau cho GET không body và POST có body. Thông thường chỉ dùng consumes khi endpoint thực sự yêu cầu request representation.

### Mapping match khác khả năng đọc dữ liệu

Ngay cả khi `Content-Type: application/json` phù hợp, JSON lỗi cú pháp như `{"name":` vẫn có thể dẫn tới **400 Bad Request** do `HttpMessageNotReadableException`. Đây là lỗi **đọc body**, khác với **415** vì media type không được hỗ trợ.

Nếu một endpoint chỉ nhận JSON, nên khai báo `consumes = MediaType.APPLICATION_JSON_VALUE` và kiểu `@RequestBody` rõ ràng. Đừng parse JSON từ chuỗi tùy tiện nếu framework đã có converter chuẩn.

## 7. produces: client chấp nhận response ở định dạng nào?

`produces` là điều kiện về **media type handler có thể trả về**, thường được chọn dựa trên `Accept` của client.

~~~java
@GetMapping(
    path = "/items/{id}",
    produces = MediaType.APPLICATION_JSON_VALUE
)
public Item get(@PathVariable long id) {
    return new Item(id, "Bút");
}
~~~

Client có thể gửi:

~~~http
GET /items/7 HTTP/1.1
Host: localhost:8080
Accept: application/json
~~~

Mapping JSON phù hợp. Nếu client gửi:

~~~http
GET /items/7 HTTP/1.1
Host: localhost:8080
Accept: application/pdf
~~~

mà không có mapping/converter tạo representation phù hợp, Spring MVC thường phản hồi **406 Not Acceptable**. Đây khác với **415**, vì 406 liên quan **response mong muốn**, còn 415 liên quan **request body gửi lên**.

### Accept và Content-Type đặt ở hai phía

| Header | Ai gửi? | Được dùng làm gì? | Mapping attribute |
| --- | --- | --- | --- |
| Request `Content-Type` | Client | Thông báo dạng dữ liệu body **gửi vào server** | `consumes` |
| Request `Accept` | Client | Thông báo các dạng response **client chấp nhận** | `produces` |
| Response `Content-Type` | Server | Thông báo dạng body **server đã gửi về** | Kết quả content negotiation/serialization |

Trong phản hồi JSON bình thường, server đặt `Content-Type: application/json`. Không dùng **response Content-Type** như một điều kiện đầu vào để xác định method vì nó chưa tồn tại tại thời điểm routing.

### Quality và wildcard

Header:

~~~http
Accept: application/json;q=0.9, text/plain;q=0.5
~~~

có thể nêu mức ưu tiên các media type; `q` là quality value. `Accept: */*` cho phép nhiều định dạng phù hợp, nhưng nếu có nhiều mapping cùng đường dẫn và method phân biệt **chỉ bằng `produces`**, cần kiểm thử lựa chọn/tie-breaking cho request Accept rộng. Với API sản phẩm, có thể tránh ambiguity bằng hợp đồng Accept tường minh, mapping cụ thể và test.

Dù mapping chọn được `produces="application/xml"`, server vẫn cần converter để encode object ra XML. `produces` không tự cài JAXB/Jackson XML; không hứa output XML chỉ bằng annotation khi thiếu converter.

## 8. Spring chọn mapping khi nhiều ứng viên cùng path

Về mặt ý tưởng, trong Spring MVC:

~~~text
HTTP request
    |
    v
DispatcherServlet
    |
    v
RequestMappingHandlerMapping
    |
    +-- match path pattern?
    +-- match HTTP method?
    +-- match request params?
    +-- match request headers?
    +-- match consumes / Content-Type?
    +-- match produces / Accept?
    |
    +-- Không còn ứng viên => xử lý không khớp / báo lỗi HTTP phù hợp
    |
    +-- Một ứng viên => trả HandlerMethod
    |
    +-- Nhiều ứng viên => so mức độ cụ thể / RequestMappingInfo
    |                      nếu không thể phân định => ambiguity
    v
RequestMappingHandlerAdapter
    |
    +-- resolve arguments, invoke controller, handle return value
    v
HTTP response
~~~

Đây là sơ đồ học thuật đơn giản hóa, không phải mô tả thứ tự kiểm tra tối ưu hóa trong từng phiên bản source code. Điều quan trọng là **tất cả điều kiện đã khai báo đều phải được đáp ứng** và các mapping cạnh tranh được so độ cụ thể, chứ không chọn bừa method nào khai báo trước.

### Phân biệt xung đột lúc startup và ambiguity lúc request

Hai mapping có cấu hình tương đương hoàn toàn có thể gây lỗi đăng ký **ambiguous mapping** khi ứng dụng khởi động:

~~~java
@GetMapping("/same")
public String first() { return "A"; }

@GetMapping("/same")
public String second() { return "B"; }
~~~

Đây là ví dụ **không hợp lệ để chạy đồng thời** trong cùng controller/application vì cùng path, method và điều kiện mapping. Không khắc phục bằng cách đổi tên Java method: mapping xét request conditions, không dựa vào tên method.

Hai mapping không hoàn toàn trùng điều kiện có thể đều là ứng viên cho một request cụ thể; Spring so độ ưu tiên. Nếu chúng không thể phân biệt đủ rõ, có thể gặp ambiguity tại runtime. Hãy đặt điều kiện loại trừ nhau hoặc phân đường dẫn/HTTP method rõ ràng và viết test cho các request chồng lấp.

### Điều kiện ở class và method

~~~java
@RestController
@RequestMapping(path = "/api")
class ExampleController {

    @GetMapping(path = "/items", produces = "application/json")
    List<Item> items() { ... }
}
~~~

Path cuối cùng là `/api/items`. Thường dùng class-level `@RequestMapping` cho base path, method-level shortcuts cho method/path chính xác. Với `consumes` và `produces`, khai báo ở method sẽ **ghi đè** điều kiện tương ứng ở class thay vì chỉ thêm điều kiện vào danh sách class-level; tránh nghĩ chúng luôn được cộng dồn. Với các condition khác, hãy tham khảo Spring API và kiểm thử mapping kết hợp thay vì suy diễn tùy tiện.

Không xếp hai annotation mapping khác nhau trên cùng một method, ví dụ `@GetMapping` và `@PostMapping` song song, để hy vọng chúng hợp thành OR: Spring cảnh báo và chỉ dùng mapping đầu tiên được phát hiện. Nếu thực sự cần nhiều HTTP method cùng một method, hãy dùng một `@RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})` rồi xử lý nghiệp vụ cẩn thận.

## 9. Ví dụ Spring Boot MVC hoàn chỉnh cho cả năm nhóm điều kiện

Ví dụ sau độc lập, dùng Java 17+, Spring Boot 3.3.5 (Spring Framework 6.1.x) để các API minh họa ổn định. Một controller chứa các endpoint có điều kiện độc lập, tránh xung đột mapping. Không có database hoặc thư viện ngoài Web starter.

### 9.1 Cấu trúc và Gradle

~~~text
mapping-conditions-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/example/mapping/MappingConditionsApplication.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'mapping-conditions-demo'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'example'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
~~~

Dùng JDK 17+ và Gradle tương thích với Spring Boot plugin. Nếu project có Gradle Wrapper thì chạy wrapper; nếu chưa có, cần Gradle đã cài để chạy `gradle bootRun` hoặc tạo wrapper. Không cần bắt buộc dùng WebFlux để triển khai các mapping này.

### 9.2 Java source

Tạo `src/main/java/example/mapping/MappingConditionsApplication.java`:

~~~java
package example.mapping;

import java.util.Map;

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
public class MappingConditionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MappingConditionsApplication.class, args);
    }

    public record Item(long id, String name) {}
    public record NewItem(String name) {}

    @RestController
    @RequestMapping("/api")
    static class DemoController {

        // HTTP method + path + output JSON.
        @GetMapping(
                path = "/items/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Item byId(@PathVariable long id) {
            return new Item(id, "Bút");
        }

        // Hai GET cùng path, chỉ một mode tương ứng được chọn.
        @GetMapping(
                path = "/find",
                params = "mode=summary",
                produces = MediaType.TEXT_PLAIN_VALUE)
        public String summary() {
            return "summary";
        }

        @GetMapping(
                path = "/find",
                params = "mode=full",
                produces = MediaType.TEXT_PLAIN_VALUE)
        public String full() {
            return "full";
        }

        // Phân biệt tham số có mặt và vắng mặt.
        @GetMapping(
                path = "/flag",
                params = "debug",
                produces = MediaType.TEXT_PLAIN_VALUE)
        public String debugOn() {
            return "debug:on";
        }

        @GetMapping(
                path = "/flag",
                params = "!debug",
                produces = MediaType.TEXT_PLAIN_VALUE)
        public String debugOff() {
            return "debug:off";
        }

        // Hai GET cùng path, nhưng yêu cầu header khác nhau.
        @GetMapping(
                path = "/clients",
                headers = "X-Client=mobile",
                produces = MediaType.TEXT_PLAIN_VALUE)
        public String mobile() {
            return "client:mobile";
        }

        @GetMapping(
                path = "/clients",
                headers = "X-Client=web",
                produces = MediaType.TEXT_PLAIN_VALUE)
        public String web() {
            return "client:web";
        }

        // Cùng POST /items nhưng tiêu thụ và trả về media khác.
        @PostMapping(
                path = "/items",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<Item> createJson(@RequestBody NewItem input) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new Item(101L, input.name()));
        }

        @PostMapping(
                path = "/items",
                consumes = MediaType.TEXT_PLAIN_VALUE,
                produces = MediaType.TEXT_PLAIN_VALUE)
        public ResponseEntity<String> createText(@RequestBody String input) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("created: " + input);
        }

        // Cùng GET /format, phân biệt theo Accept header.
        @GetMapping(
                path = "/format",
                produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, String> json() {
            return Map.of("view", "json");
        }

        @GetMapping(
                path = "/format",
                produces = MediaType.TEXT_PLAIN_VALUE)
        public String plain() {
            return "view:text";
        }
    }
}
~~~

Ví dụ có năm kiểu điều kiện chính: HTTP method (`GET`/`POST`), `params`, `headers`, `consumes` và `produces`. Với annotation `@RestController`, `String` được ghi thành response body, không được hiểu là tên HTML view.

**Lưu ý về `@PathVariable`:** Spring Boot Gradle plugin cung cấp compiler metadata phù hợp trong setup thông thường. Nếu công cụ build riêng không giữ tên parameter để reflection đọc được, hãy ghi tường minh `@PathVariable("id")` và các annotation parameter khác để tránh phụ thuộc compiler flag `-parameters`.

### 9.3 Chạy ứng dụng và kiểm tra

Với wrapper có sẵn:

~~~bash
./gradlew bootRun
~~~

Windows PowerShell:

~~~powershell
.\gradlew.bat bootRun
~~~

Không có wrapper thì dùng `gradle bootRun` sau khi cài Gradle tương thích. Ví dụ giả định cổng mặc định là `8080` và không có ứng dụng khác chiếm cổng.

**A. GET JSON theo method, path và produces:**

~~~bash
curl -i -H "Accept: application/json" http://localhost:8080/api/items/7
~~~

Response mong đợi: `200 OK`, `Content-Type` là JSON, body:

~~~json
{"id":7,"name":"Bút"}
~~~

**B. params chọn summary hoặc full:**

~~~bash
curl -i "http://localhost:8080/api/find?mode=summary"
curl -i "http://localhost:8080/api/find?mode=full"
~~~

Hai body lần lượt:

~~~text
summary
full
~~~

Nếu gọi `/api/find?mode=other` hoặc không truyền `mode`, cả hai method trên không khớp điều kiện giá trị. Trong cấu hình chuẩn, trường hợp chỉ sai params thường nhận 400; test thực tế cần kiểm soát exception handler nếu ứng dụng có tùy biến.

**C. params có mặt và vắng mặt:**

~~~bash
curl -i "http://localhost:8080/api/flag?debug"
curl -i "http://localhost:8080/api/flag"
~~~

Các body:

~~~text
debug:on
debug:off
~~~

Điều kiện `params="debug"` chỉ đòi hiện diện; không đồng nghĩa `debug=true`. `?debug=false` vẫn có parameter tên `debug` và phù hợp với method `debugOn` trong demo.

**D. headers chọn client:**

~~~bash
curl -i -H "X-Client: mobile" http://localhost:8080/api/clients
curl -i -H "X-Client: web" http://localhost:8080/api/clients
~~~

Body lần lượt:

~~~text
client:mobile
client:web
~~~

Bỏ header hoặc gửi `X-Client: other` sẽ không thỏa cả hai mapping này; không khẳng định status cố định nếu ứng dụng thêm những mapping/header rules khác.

**E. POST cùng URI, khác Content-Type và Accept:**

~~~bash
curl -i -X POST "http://localhost:8080/api/items" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"name":"Notebook"}'
~~~

Status mong đợi: `201 Created`; body:

~~~json
{"id":101,"name":"Notebook"}
~~~

Đổi thành text:

~~~bash
curl -i -X POST "http://localhost:8080/api/items" \
  -H "Content-Type: text/plain" \
  -H "Accept: text/plain" \
  --data-binary "Pencil"
~~~

Status mong đợi: `201 Created`; body:

~~~text
created: Pencil
~~~

Sự khác biệt đến từ **hai ánh xạ `consumes`**, mỗi mapping chỉ nhận kiểu request tương ứng; đồng thời `produces` của mapping chọn ra kiểu response.

**F. produces chọn format theo Accept:**

~~~bash
curl -i -H "Accept: application/json" http://localhost:8080/api/format
curl -i -H "Accept: text/plain" http://localhost:8080/api/format
~~~

Body tương ứng:

~~~json
{"view":"json"}
~~~

~~~text
view:text
~~~

Đây là hai method có cùng GET/path nhưng media type phân biệt được khi client gửi Accept cụ thể. Không sử dụng kết quả cho request không gửi `Accept` để khẳng định framework luôn ưu tiên một method.

**G. Sai Content-Type, Accept hoặc HTTP method:**

~~~bash
curl -i -X POST "http://localhost:8080/api/items" \
  -H "Content-Type: application/xml" \
  -H "Accept: application/json" \
  -d '<item><name>Notebook</name></item>'

curl -i -H "Accept: application/pdf" http://localhost:8080/api/format

curl -i -X DELETE http://localhost:8080/api/items/7
~~~

Trong dự án demo và không tùy biến error handling, các status kỳ vọng theo từng trường hợp là **415 Unsupported Media Type**, **406 Not Acceptable** và **405 Method Not Allowed**. Body lỗi và danh sách header cụ thể có thể khác theo cấu hình Spring Boot. Các lệnh là ví dụ kiểm thử HTTP, không phải stdout của chương trình Java.

### 9.4 Bảng test case ngắn của demo

| Request | Mapping kỳ vọng | Kết quả |
| --- | --- | --- |
| `GET /api/items/7` + `Accept: application/json` | `byId` | JSON `{"id":7,"name":"Bút"}` |
| `GET /api/find?mode=full` | `full` | `full` |
| `GET /api/find?mode=summary` | `summary` | `summary` |
| `GET /api/flag` | `debugOff` | `debug:off` |
| `GET /api/flag?debug` | `debugOn` | `debug:on` |
| `GET /api/clients` + `X-Client: mobile` | `mobile` | `client:mobile` |
| `POST /api/items` + JSON Content-Type/Accept | `createJson` | 201 + JSON |
| `POST /api/items` + text/plain Content-Type/Accept | `createText` | 201 + text |
| `GET /api/format` + `Accept: text/plain` | `plain` | `view:text` |
| `GET /api/format` + `Accept: application/json` | `json` | JSON |
| `GET /api/format` + `Accept: application/pdf` | Không có produces phù hợp | 406 thường gặp |
| `POST /api/items` + `Content-Type: application/xml` | Không có consumes phù hợp | 415 thường gặp |
| `DELETE /api/items/7` | Không có DELETE mapping trên path | 405 thường gặp |

## 10. Những lỗi định tuyến thường gặp và cách chẩn đoán

| HTTP status / hiện tượng | Chẩn đoán đầu tiên | Hướng xử lý |
| --- | --- | --- |
| **404 Not Found** | Không có path/handler phù hợp; có thể do cấu hình context path/servlet mapping | Xem URL thật, controller registration, logs/mappings |
| **405 Method Not Allowed** | Path có mapping nhưng không cho HTTP method request | So `GET/POST/PUT/PATCH/DELETE` và `Allow` |
| **400 Bad Request** khi `params` không thỏa | Có path/method phù hợp nhưng tham số điều kiện thiếu/sai | So query/form parameters với `params` |
| **400 Bad Request** sau khi chọn handler | Sai kiểu tham số, JSON malformed, validation thất bại | So `@RequestParam`, `@PathVariable`, `@RequestBody`, converter/validator |
| **415 Unsupported Media Type** | Content-Type request không phù hợp consumes/converter | Kiểm tra đúng request `Content-Type` và codec |
| **406 Not Acceptable** | `Accept` không phù hợp produces/không tạo được representation | Kiểm tra `Accept`, `produces`, converter ghi body |
| **500/ambiguous mapping** khi startup | Trùng điều kiện đăng ký giữa nhiều method | Đổi mapping để phân biệt, không chỉ đổi tên Java method |
| **Ambiguous handler methods** lúc request | Nhiều candidate ngang ưu tiên cho request cụ thể | Thiết kế điều kiện loại trừ, thêm test case chồng lấp |
| Controller method vào đúng nhưng body sai | Mapping đã xong; có thể lỗi return-value handler/serialization | Xem `@RestController`, `ResponseEntity`, converter |
| Có header nhưng vẫn không match | Sai giá trị, whitespace, proxy làm mất header, policy gateway | Log header một cách an toàn, trace qua proxy |

**Không dùng HTTP status làm chứng cứ duy nhất**: `@ControllerAdvice`, filter/security, reverse proxy hay handler mapping tùy biến có thể thay đổi phản hồi. Cần xác định request thật mà ứng dụng nhận sau proxy và danh sách mappings thực tế.

### Log có ích khi debug

Với ứng dụng phát triển, có thể bật log mapping ở mức phù hợp trong `application.properties`:

~~~properties
logging.level.org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping=TRACE
logging.level.org.springframework.web.servlet.DispatcherServlet=DEBUG
~~~

Bật tracing chi tiết chỉ khi cần; đừng bật lâu dài cho production vì lượng log, hiệu năng và nguy cơ ghi dữ liệu nhạy cảm. Nếu có Spring Boot Actuator, endpoint `/actuator/mappings` có thể giúp xem ánh xạ được đăng ký **khi đã cài và cấu hình Actuator**, không mặc định công khai trong ứng dụng demo.

## 11. Phân biệt ranh giới của điều kiện mapping với argument resolution

Nhìn hai method:

~~~java
@GetMapping(path = "/orders", params = "status=PAID")
public String paidOrders(@RequestParam String status) { ... }

@GetMapping(path = "/orders", params = "status=PENDING")
public String pendingOrders(@RequestParam String status) { ... }
~~~

`params="status=PAID"`/`"status=PENDING"` quyết định **method nào được chọn**. Sau đó `@RequestParam String status` mới tạo đối số cho method tương ứng.

Một số đối chiếu quan trọng:

- `@RequestMapping.params` không phải `@RequestParam`.
- `@RequestMapping.headers` không phải `@RequestHeader`.
- `@RequestMapping.consumes` không phải `@RequestBody`.
- `@RequestMapping.produces` không phải `@ResponseBody`.
- `consumes`/`produces` ảnh hưởng **ứng viên mapping**; `HttpMessageConverter` và return-value handler quyết định Java body ↔ bytes thực sự.
- `headers = "X-Client=mobile"` chỉ lọc request; muốn dùng nội dung header bên trong method, vẫn phải nhận qua `@RequestHeader("X-Client")` hoặc đọc request context.

**Ví dụ quan trọng:** POST mapping có `consumes="application/json"` nhưng method nhận `@RequestBody String` vẫn có thể hoạt động theo các converter hiện có; ngược lại `@RequestBody MyDto` cần converter phù hợp. Không suy ra Java target type chỉ từ consumes.

## 12. Những ngộ nhận dễ làm API sai hợp đồng

| Ngộ nhận | Cách hiểu đúng |
| --- | --- |
| “Cùng path chỉ được có một method controller” | Có thể có nhiều mapping cùng path khi HTTP method/params/headers/media conditions phân biệt |
| “`@RequestMapping` mặc định là GET” | Method-level không khai báo method có thể match nhiều HTTP method |
| “`params` đọc từ JSON body” | Kiểm tra request parameters, không đọc từng JSON property |
| “`@RequestParam` tự phân nhánh mapping” | Argument resolver hoạt động sau bước chọn handler |
| “`headers` tự xác thực người dùng” | Header do client gửi có thể giả mạo; authentication/authorization nằm lớp khác |
| “`consumes` đọc Accept” | `consumes` dựa vào request Content-Type |
| “`produces` đọc Content-Type request” | `produces` dựa vào request Accept/response representation |
| “Khớp consumes là parse body chắc chắn thành công” | Converter vẫn có thể thiếu hoặc dữ liệu sai cú pháp |
| “Khai báo produces XML thì chắc chắn xuất XML” | Cần XML message converter hỗ trợ Java type |
| “Mọi media type trong mảng phải đúng đồng thời” | Trong consumes/produces, media alternatives là OR |
| “Một mảng params có thể chỉ cần một giá trị đúng” | Các expression trong params array là AND |
| “Trùng mapping sẽ chọn method viết trước” | Có thể gây lỗi ambiguous mapping; phải sửa điều kiện |
| “`Accept: */*` luôn chọn JSON” | Phụ thuộc candidates và content negotiation; test hợp đồng rõ ràng |
| “415 và 406 giống nhau” | 415 về request media; 406 về response acceptable media |
| “GET mapping không bao giờ xử lý HEAD” | Spring MVC hỗ trợ HEAD cho GET mapping trong cấu hình thông thường |
| “Method-level produces luôn nối thêm class-level produces” | Method-level produces/consumes ghi đè điều kiện tương ứng ở class |

## 13. Bài tập tự kiểm tra điều kiện

**Bài 1.** Có:

~~~java
@GetMapping(path = "/search",
            params = {"type=book", "page=1"},
            headers = "X-Client=mobile",
            produces = "application/json")
public Object find() { ... }
~~~

Request nào phù hợp?

1. `GET /search?type=book&page=1` với `X-Client: mobile`, `Accept: application/json`.
2. `GET /search?type=book` với `X-Client: mobile`, `Accept: application/json`.
3. `GET /search?type=book&page=1` với `X-Client: web`, `Accept: application/json`.
4. `POST /search?type=book&page=1` với `X-Client: mobile`, `Accept: application/json`.

**Đáp án:** chỉ (1), vì tất cả điều kiện đều phải thỏa. Đoạn là minh họa mapping (không định nghĩa class/return body).

**Bài 2.** Một POST endpoint có `consumes="application/json"` và `produces="text/plain"`. Client gửi `Content-Type: application/json`, `Accept: application/json`. Điều kiện nào làm endpoint không khớp?

**Đáp án:** `produces` do client không chấp nhận `text/plain`. Muốn nhận response text, client cần gửi `Accept: text/plain` hoặc loại Accept cho phép nó theo hợp đồng.

**Bài 3.** Có hai GET `/report`, một dùng `params="mode=fast"` và một dùng `params="mode=slow"`. Client gửi `?mode=fast` thì method nào được chọn? Nếu không có `mode` thì chuyện gì xảy ra?

**Đáp án:** mapping fast được chọn. Khi thiếu mode, không có mapping nào trong hai mapping đáp ứng điều kiện params; status trong cấu hình MVC thông thường có thể là 400 do unsatisfied params, nhưng cần kiểm thử cấu hình thực.

**Bài 4.** API muốn trả JSON hoặc CSV trên cùng path. Nên dùng header tự đặt `format`, query param, hay `produces`?

**Gợi ý:** nếu đây là các representation khác nhau của cùng tài nguyên, có thể dùng `produces` và content negotiation với `Accept: application/json` hoặc `Accept: text/csv`, **nhưng** phải có converter/logic thực sự phát CSV và cần test với Accept rộng. Nếu nghiệp vụ muốn xuất báo cáo thành tệp và yêu cầu khác biệt đáng kể, endpoint export riêng hoặc query format rõ ràng có thể dễ vận hành hơn.

**Bài 5.** Tại sao chỉ sửa `@RequestBody` thành `@RequestParam` không nhất thiết giải quyết 415?

**Gợi ý:** 415 xảy ra ở tầng điều kiện `consumes`/khả năng đọc media type, có thể trước khi argument được resolve. Cần kiểm tra `Content-Type`, annotation mapping, input contract và converter chứ không chỉ đổi parameter annotation.

## 14. Checklist khi thiết kế và kiểm thử một mapping

- [ ] Method đúng ngữ nghĩa HTTP; dùng `@GetMapping`/`@PostMapping`... tường minh.
- [ ] Base path ở class và path tại method tạo ra URI cuối cùng như mong muốn.
- [ ] Chỉ dùng `params` khi cần chọn handler theo request parameters; tách rõ khỏi `@RequestParam`.
- [ ] Chỉ dùng `headers` khi cần điều kiện header thật sự; không coi điều kiện ấy là security.
- [ ] `consumes` biểu thị request `Content-Type` được chấp nhận, có converter đọc body tương ứng.
- [ ] `produces` biểu thị response representation phù hợp `Accept`, có converter ghi body tương ứng.
- [ ] Khi dùng mảng, hiểu đúng AND ở params/headers và OR ở media/method alternatives.
- [ ] Kiểm thử request thiếu param/header, giá trị sai và Content-Type/Accept không phù hợp.
- [ ] Kiểm thử 405, 400, 415, 406 và 404 trong cấu hình ứng dụng thật.
- [ ] Nếu có nhiều mappings cùng method/path, xác minh chúng phân biệt rõ bằng điều kiện và không ambiguous.
- [ ] Nếu class có consumes/produces mặc định, kiểm tra method-level overrides.
- [ ] Kiểm tra behavior với `Accept: */*`, không tự phỏng đoán format được chọn.
- [ ] Khi lỗi, phân biệt mismatch ở mapping với conversion/validation/serialization sau routing.
- [ ] Bổ sung `MockMvc`/integration tests cho đúng tổ hợp method, headers, params, media types.
- [ ] Ghi tài liệu API rõ request headers/body, response Content-Type và status errors.

**Kết luận vận dụng:** Trong Spring MVC, URL chỉ là một thành phần của `RequestMappingInfo`. Một request được đưa tới controller method khi **HTTP method, params, headers, consumes và produces** đều phù hợp với yêu cầu mapping; nếu nhiều method cạnh tranh, Spring tiếp tục đánh giá độ cụ thể. Thiết kế các điều kiện rõ ràng giúp ứng dụng phân tuyến chính xác, trả lỗi HTTP đúng nguyên nhân và tránh mapping trùng/khó debug.

### Tài liệu tham khảo chính thức

- [Spring Framework — Mapping Requests (Spring MVC)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html)
- [Spring Framework 6.1 — RequestMapping annotation API](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/bind/annotation/RequestMapping.html)
- [Spring Framework 6.1 — RequestMappingInfo API](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/mvc/method/RequestMappingInfo.html)
- [Spring Framework 6.1 — MVC Request Conditions API](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/servlet/mvc/condition/package-summary.html)
- [Spring Framework — HTTP Message Converters](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html)
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-routing-004">Biến đường dẫn, biểu thức chính quy và đường dẫn có ký tự đại diện</a>

<details>
<summary>Xem chi tiết</summary>

# Biến đường dẫn, biểu thức chính quy và đường dẫn có ký tự đại diện trong Spring MVC

Spring MVC dùng các mẫu URI để quyết định controller method nào xử lý request. Một route có thể khớp URL cố định như `/products/search`, lấy định danh từ một segment như `/products/{id}`, ràng buộc segment bằng biểu thức chính quy như `/products/{id:[0-9]+}` hoặc nhận nhiều segment qua ký tự đại diện. Các cú pháp trông gần nhau nhưng khác về **độ rộng của đường dẫn được khớp**, **giá trị được capture**, **mức ưu tiên khi nhiều handler match** và **cách xử lý lỗi**.

Bài này áp dụng cho Spring MVC trên Servlet stack, tập trung vào Spring Framework 6.x và Spring Boot 3.x, dùng Java 17+. Mặc định Spring MVC 6 sử dụng `PathPatternParser` cùng các `PathPattern` đã parse. Với ứng dụng đời cũ sử dụng `AntPathMatcher` hoặc được cấu hình path matching khác, phải xác nhận lại hành vi trước khi sao chép pattern.

## 1. Mục tiêu và mô hình định tuyến

Sau bài này, bạn cần có thể:

- Giải thích `@PathVariable`, named path variable, regex constraint, `?`, `*`, `**` và `{*path}`.
- Phân biệt một path segment với nhiều segment và với query string.
- Dự đoán route cụ thể nào sẽ được chọn nếu cả literal, variable và wildcard cùng match.
- Phân biệt route không match (thường 404) với route match nhưng kiểu dữ liệu không convert được (thường 400).
- Viết và kiểm thử controller theo Spring Boot 3 với Java 17+.
- Nhận diện rủi ro ở trailing slash, URI encoding, file extension, reverse proxy và đường dẫn không đáng tin cậy.

Sơ đồ logic:

~~~text
GET /api/products/42?lang=vi
            |
            v
Servlet container → DispatcherServlet
                         |
                         v
              RequestMappingHandlerMapping
              match method GET + URL path
                         |
                         v
              "/api/products/{id:[0-9]+}"
                         |
                         v
             URI variable id = "42"
                         |
                         v
             Argument resolver:
             @PathVariable long id → 42L
                         |
                         v
                   Controller
                         |
                         v
                 HTTP response
~~~

Query `?lang=vi` có thể dùng qua `@RequestParam`, nhưng **không phải path segment** để regex của `{id}` kiểm tra. Ngoài path, `@RequestMapping` còn có điều kiện method, params, headers, consumes, produces; đúng đường dẫn vẫn chưa chắc đủ để gọi được controller.

## 2. Đường dẫn URL gồm những gì?

Xét URL:

~~~text
https://api.example.test/store/api/products/42?lang=vi#details
|______ scheme/host ____| |____ path ________| |query| fragment
~~~

Giả định web application có context path `/store` và `DispatcherServlet` map mặc định vào `/`. Controller khai báo `@RequestMapping("/api/products")` và `@GetMapping("/{id}")`. Spring MVC so khớp route `/api/products/42` trong phạm vi ứng dụng, không cần viết context path vào annotation.

Các path segment là những phần được tách bởi slash:

~~~text
/api/products/42
  |     |     |
 api products 42
~~~

Một path variable đơn `{id}` capture **một segment**, không tự nhận cả `42/reviews`. Query string `?lang=vi` được truyền ở phần khác; fragment `#details` do client quản lý và không được gửi trong HTTP request-target.

Phân biệt dấu hỏi:

- Trong **pattern** `/files/v?.txt`, ký hiệu `?` có nghĩa match đúng một ký tự trong segment.
- Trong **URL request** `/files/v1.txt?download=true`, dấu `?` mở đầu query string.

Một URI pattern thuộc Spring MVC khác với Servlet container URL mapping: pattern `/**` trong controller không đồng nghĩa pattern servlet `/*`. Container chọn web context và servlet trước, sau đó `RequestMappingHandlerMapping` của Spring mới chọn method.

## 3. Bảng cú pháp URI pattern trong PathPatternParser

| Pattern | Ý nghĩa | Ví dụ match | Ví dụ không match |
| --- | --- | --- | --- |
| `/products/list` | Route literal cố định | `/products/list` | `/products/1` |
| `/products/{id}` | Một segment, capture vào `id` | `/products/42` | `/products/42/reviews` |
| `/pages/t?st` | `?` khớp đúng một ký tự trong segment | `/pages/test`, `/pages/t3st` | `/pages/tst` |
| `/images/*.png` | `*` khớp 0 hoặc nhiều ký tự trong một segment | `/images/logo.png` | `/images/icons/logo.png` |
| `/teams/*/members` | Một segment dạng wildcard | `/teams/java/members` | `/teams/java/core/members` |
| `/assets/**` | 0 hoặc nhiều segment ở cuối route | `/assets`, `/assets/a/b` | `/other/a/b` |
| `/assets/{*path}` | Catch-all cuối route và capture phần path còn lại | `/assets/a/b.png` | `/other/a/b.png` |
| `/products/{id:[0-9]+}` | Một segment có một hay nhiều chữ số ASCII | `/products/42` | `/products/abc` |

Những quy tắc phải nhớ:

1. `?` và `*` **không vượt qua dấu slash**, chỉ kiểm soát ký tự trong một segment.
2. `{name}` capture một segment để truy cập bằng `@PathVariable`.
3. `**` match nhiều segment nhưng **không tạo named variable**.
4. `{*path}` cũng match nhiều segment ở cuối và **có tạo variable**.
5. Với `PathPatternParser`, `**` và `{*path}` chỉ hợp lệ ở **cuối pattern**; không đặt catch-all vào giữa route.

### 3.1. Ký hiệu ? trong pattern

Đoạn minh họa trong một controller:

~~~java
@GetMapping("/files/v?.txt")
public String oneLetterVersion() {
    return "one-char-version";
}
~~~

Kết quả:

~~~text
/files/v1.txt     MATCH
/files/vA.txt     MATCH
/files/v10.txt    KHÔNG MATCH
/files/v.txt      KHÔNG MATCH
~~~

Ở đây dấu `?` là một ký tự wildcard, không liên quan đến query. Nếu request có thêm `?download=true`, đường dẫn vẫn là `/files/v1.txt`.

### 3.2. Ký hiệu *: wildcard trong một segment

Pattern:

~~~java
@GetMapping("/images/*.png")
public String png() {
    return "single-level-png";
}
~~~

So khớp:

~~~text
/images/cat.png          MATCH
/images/logo-small.png   MATCH
/images/icons/cat.png    KHÔNG MATCH
~~~

Ở `/images/icons/cat.png`, có hai segment sau `/images`, nên một dấu `*` trong `*.png` không đủ. Nếu yêu cầu ứng dụng là quản lý file phân cấp, hãy dùng `{*path}` hoặc thiết kế URI có nhiều phần rõ nghĩa; đừng giả định `*` “bắt mọi thứ”.

### 3.3. Ký hiệu **: catch-all

Pattern `/resources/**` có thể match `/resources`, `/resources/logo.png` và `/resources/images/logo.png`.

Các pattern sau **không hợp lệ** khi dùng `PathPatternParser`:

~~~text
/resources/**/logo.png
/a/**/b
/a/{*rest}/meta
~~~

Điều kiện catch-all ở cuối giúp Spring so sánh độ cụ thể của các route ít mơ hồ hơn. Nếu cần endpoint `/reports/{year}/export`, nên mô tả số segment thực sự cần thay vì buộc `**` nằm ở giữa.

Một khác biệt khi nâng cấp: `AntPathMatcher` cũ cho phép một số pattern có `**` ở giữa, nhưng `PathPatternParser` không chấp nhận kiểu đó. Vì vậy cần kiểm tra những route trước đây dùng `/**/something`.

## 4. @PathVariable: trích dữ liệu từ URI template

Một controller có thể khai báo:

~~~java
@RestController
@RequestMapping("/api/products")
class ProductController {

    @GetMapping("/{id}")
    public String byId(@PathVariable("id") long productId) {
        return "product-" + productId;
    }
}
~~~

Khi client gọi `GET /api/products/42`, Spring trích URI variable `id` có giá trị text `"42"`, chuyển sang Java `long` rồi gọi method. Với ví dụ trên, body response là:

~~~text
product-42
~~~

Đoạn code trên minh họa một controller cần được đặt trong ứng dụng Spring MVC đã cấu hình component scanning và dependencies. `@PathVariable("id")` nêu rõ tên variable. Nếu tên tham số Java cũng là `id`, có thể viết `@PathVariable long id` khi biên dịch với parameter name metadata phù hợp, chẳng hạn `-parameters`; nêu tên tường minh tránh phụ thuộc vào cấu hình compiler.

### 4.1. Nhiều path variables

~~~java
@GetMapping("/users/{userId}/orders/{orderId}")
public String order(
        @PathVariable("userId") long userId,
        @PathVariable("orderId") long orderId) {
    return userId + ":" + orderId;
}
~~~

Request:

~~~text
GET /users/7/orders/81
~~~

Kết quả:

~~~text
7:81
~~~

Hai biến `userId` và `orderId` lấy từ **hai segment khác nhau**. Nếu request thêm `/details` mà không có route hỗ trợ phần đó, method không tự nhận segment dư.

### 4.2. Chuyển đổi kiểu và lỗi xảy ra sau matching

URI variable ban đầu là giá trị text. Spring có thể dùng hệ thống `ConversionService` cùng các converter/formatter để chuyển thành `long`, `UUID`, `Enum` hoặc kiểu dữ liệu được ứng dụng hỗ trợ.

Ví dụ:

~~~java
@GetMapping("/orders/{id}")
public String uuid(@PathVariable("id") java.util.UUID id) {
    return id.toString();
}
~~~

Với `/orders/not-a-uuid`, route `/orders/{id}` vẫn có thể **match**, sau đó conversion từ String sang UUID thất bại. Xử lý lỗi mặc định của Spring MVC thường trả `400 Bad Request`. Đây là bước khác với regex constraint khiến URL **không match ngay từ đầu**.

### 4.3. @PathVariable khác @RequestParam

~~~java
@GetMapping("/products/{id}")
public String read(
        @PathVariable("id") long id,
        @RequestParam(name = "lang", defaultValue = "vi") String lang) {
    return id + ":" + lang;
}
~~~

Request `GET /products/42?lang=en` trả:

~~~text
42:en
~~~

Biến `id` thuộc path, còn `lang` thuộc query parameter. `/products/42?lang=en` và `/products/42?lang=vi` có **cùng phần path** khi Spring so URL pattern. Một mapping có thể dùng thêm điều kiện `params` nếu thực sự muốn chọn handler dựa trên query, nhưng đó là điều kiện riêng.

## 5. Named catch-all {*path}: capture nhiều segment

Khi cần lấy trọn phần đường dẫn còn lại:

~~~java
@GetMapping("/files/{*path}")
public String file(@PathVariable("path") String path) {
    return path;
}
~~~

Với request `GET /files/manuals/java/intro.pdf`, biến `path` nhận:

~~~text
/manuals/java/intro.pdf
~~~

**Giá trị capture bao gồm dấu slash đầu tiên** ở phần path còn lại trong ví dụ chuẩn của Spring. Vì vậy không nên mặc định giá trị là `manuals/java/intro.pdf`.

| Mapping | Khớp qua nhiều segment? | Có biến tên? |
| --- | --- | --- |
| `/files/{name}` | Không | Có: `name` |
| `/files/*` | Không | Không |
| `/files/**` | Có | Không |
| `/files/{*path}` | Có | Có: `path` |

Hãy dùng `{*path}` cho các route thực sự cần cây đường dẫn, ví dụ đường dẫn phân cấp của tài liệu trong một storage namespace. Tuy nhiên **không nối chuỗi path do client cung cấp vào đường dẫn file của hệ điều hành rồi mở file trực tiếp**. Cần kiểm tra root cho phép, canonical path, path traversal `..`, symlink, URL encoding và quyền truy cập. Một named catch-all chỉ là cơ chế match URL, **không bảo đảm an toàn dữ liệu**.

## 6. Regex constraint: {name:regex}

URI template có thể ràng buộc định dạng của segment ngay tại thời điểm tìm handler:

~~~java
@GetMapping("/products/{id:[0-9]+}")
public String product(@PathVariable("id") long id) {
    return "id=" + id;
}
~~~

Regex `[0-9]+` chấp nhận một hoặc nhiều chữ số ASCII. Các trường hợp:

~~~text
/products/42       MATCH
/products/007      MATCH
/products/abc      KHÔNG MATCH
/products/42abc    KHÔNG MATCH
/products/42/x     KHÔNG MATCH
~~~

Ràng buộc regex được áp dụng cho **segment của path**, không kiểm tra JSON body và không quyết định sự tồn tại của một tài nguyên trong database.

### 6.1. Regex và Java type là hai bước độc lập

So sánh:

~~~text
Route 1: /books/{id:[0-9]+}
         GET /books/abc
         → regex không match → xét route khác hoặc thường 404

Route 2: /books/{id}
         Java argument: @PathVariable long id
         GET /books/abc
         → route match → String→long conversion lỗi → thường 400
~~~

Một chuỗi rất lớn như `99999999999999999999999999999` vẫn thỏa `[0-9]+` nhưng **không fit trong Java `long`**. Khi đó argument conversion thất bại dù regex đã match.

Quyết định dùng regex phụ thuộc hợp đồng API:
- Nếu muốn định tuyến ID số và slug chữ sang hai handler riêng, constraint có thể hữu ích.
- Nếu muốn một endpoint tiếp nhận mọi input rồi trả lỗi validation có thông điệp cụ thể, để `{id}` không ràng buộc và validate/convert có thể hợp lý hơn.
- Không mặc định rằng request sai định dạng lúc nào cũng nên trả 400 hay 404; trạng thái thực tế phụ thuộc tầng thất bại và các handler đang đăng ký.

### 6.2. Regex slug

~~~java
@GetMapping("/articles/{slug:[a-z][a-z0-9-]*}")
public String article(@PathVariable("slug") String slug) {
    return "slug=" + slug;
}
~~~

Pattern này bắt buộc ký tự đầu là chữ thường ASCII, tiếp theo có thể là chữ thường, số hoặc dấu gạch nối.

| Path | Match? | Giải thích |
| --- | --- | --- |
| `/articles/spring-mvc` | Có | Chỉ chứa ký tự cho phép |
| `/articles/a1` | Có | Bắt đầu bằng chữ |
| `/articles/1st` | Không | Ký tự đầu là số |
| `/articles/SPRING` | Không | Chữ hoa không thuộc pattern |
| `/articles/lập-trình` | Không | Chữ có dấu không thuộc `[a-z]` |
| `/articles/a/b` | Không | Có hai segment sau `/articles` |

Regex trên chỉ là ví dụ cho **slug ASCII**. Nếu yêu cầu định danh tiếng Việt, phải chọn quy tắc slug/Unicode normalization và encoding nhất quán, không tự thay tất cả input tiếng Việt bằng pattern này.

### 6.3. Escape biểu thức chính quy trong Java string

Với regex Java như `\d+`, vì dấu `\` còn nằm bên trong Java string literal nên cần escape trong source thành `\\d+`. Ví dụ source Java:

~~~java
@GetMapping("/numbers/{id:\\d+}")
public String number(@PathVariable("id") long id) {
    return Long.toString(id);
}
~~~

Nếu muốn tránh nhầm số lượng dấu backslash ở các tầng Java string → URI template → Java regex, dùng `[0-9]+` trong ví dụ đơn giản thường dễ đọc hơn.

Không đưa regex từ client vào route declaration. Routing metadata phải được định nghĩa có kiểm soát khi khởi tạo ứng dụng; nội dung người dùng không được phép trở thành biểu thức Java regex tùy ý ở runtime để tránh nhập nhằng, hiệu năng khó dự đoán và regex denial-of-service.

### 6.4. Cẩn trọng với regex phức tạp

Spring cũng có thể trích nhiều biến từ một path segment, chẳng hạn tên, version và extension của artifact. Ví dụ cú pháp theo tài liệu Spring:

~~~java
@GetMapping("/{name:[a-z-]+}-{version:[0-9]+}{ext:\\.[a-z]+}")
public String artifact(
        @PathVariable("name") String name,
        @PathVariable("version") String version,
        @PathVariable("ext") String ext) {
    return name + "|" + version + "|" + ext;
}
~~~

Đây là **method minh họa bên trong controller**, cần import annotation; nó không phải chương trình Java độc lập. Nếu phần `name` được phép chứa `-`, việc phân tách các thành phần cùng segment có thể khó hiểu khi định dạng thực tế phức tạp. Thiết kế URI đơn giản hơn như `/artifacts/{name}/versions/{version}` thường giúp documentation và authorization dễ đọc hơn.

Dùng regex để giới hạn **cú pháp**, tiếp tục thực hiện business validation sau khi controller nhận argument. Việc một slug match pattern không có nghĩa nó tồn tại hoặc người dùng có quyền truy cập.

## 7. Khi nhiều pattern cùng match: quy tắc specificity

Giả sử controller khai báo hai route:

~~~java
@GetMapping("/products/search")
public String search() {
    return "search";
}

@GetMapping("/products/{id}")
public String byId(@PathVariable("id") String id) {
    return "id=" + id;
}
~~~

Request `GET /products/search` thỏa cả `/products/search` và `/products/{id}`, nhưng **route literal cụ thể hơn được chọn**. Thứ tự hai method trong source Java **không phải quy tắc ưu tiên**.

Với `PathPatternParser`, Spring so sánh ứng viên bằng cơ chế như `PathPattern.SPECIFICITY_COMPARATOR`:
- Các pattern cụ thể, ít wildcard/variables thường ưu tiên hơn.
- Catch-all `**` và `{*path}` được xếp sau những pattern thông thường có thể match.
- Khi có nhiều candidate cùng điểm, độ dài/cấu trúc pattern và điều kiện request mapping khác được xem xét.
- Nếu vẫn không thể phân biệt kết quả phù hợp, route có thể bị đánh giá mơ hồ, gây lỗi khi đăng ký hoặc lúc xử lý request.

Đừng lập trình theo giả định “cái nào khai báo trước sẽ thắng”.

| Request | Pattern cùng có thể match | Handler được ưu tiên thông thường |
| --- | --- | --- |
| `GET /products/search` | `/products/search`, `/products/{id}` | Literal search |
| `GET /assets/logo.png` | `/assets/*.png`, `/assets/{*path}` | One-segment PNG |
| `GET /assets/icons/logo.png` | `/assets/{*path}` | Catch-all |
| `GET /users/42` | `/users/{id}`, `/users/{*rest}` | Single-segment variable |
| `GET /users/42/settings` | `/users/{*rest}` | Catch-all (nếu không có route cụ thể hơn) |

Bảng giả định các điều kiện HTTP method, headers, produces/consumes, params cũng phù hợp. Một path match tốt không thể thắng nếu mapping không chấp nhận method của request.

### 7.1. Ambiguous mapping thường xuất hiện thế nào?

Ví dụ hai method:

~~~java
@GetMapping("/items/{id}")
public String byId(@PathVariable("id") String id) {
    return id;
}

@GetMapping("/items/{name}")
public String byName(@PathVariable("name") String name) {
    return name;
}
~~~

Tên biến `id` và `name` **không đủ phân biệt URL**: một segment bất kỳ có thể match cả hai. Spring có thể phát hiện mapping tương đương lúc khởi tạo hoặc ambiguous handler khi request được xử lý, tùy cách đăng ký/điều kiện. Phương án tốt hơn:
- Tách namespace: `/items/by-id/{id}` và `/items/by-name/{name}`.
- Hoặc dùng regex **không chồng lấn**, ví dụ `{id:[0-9]+}` và slug bắt đầu bằng chữ.
- Hoặc dùng một endpoint `/items/{key}` rồi giải quyết loại key trong nghiệp vụ.

Không thêm thêm wildcard để “hết lỗi ambiguous” nếu nó khiến URL quá rộng. Hãy diễn đạt thiết kế tài nguyên trước rồi mới chọn syntax.

## 8. Ví dụ Spring Boot 3 hoàn chỉnh có thể chạy

Dùng một dự án Spring Boot 3.x có Java 17+ với dependency `spring-boot-starter-web`. Tệp `src/main/java/demo/routing/RoutingApplication.java` chứa một public main class và một controller package-private nằm cùng package để Spring Boot component scan phát hiện.

~~~java
package demo.routing;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class RoutingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingApplication.class, args);
    }
}

@RestController
@RequestMapping("/api")
class RouteController {

    @GetMapping("/products/search")
    public Map<String, String> search() {
        return Map.of("route", "search");
    }

    @GetMapping("/products/{id:[0-9]+}")
    public Map<String, String> byId(@PathVariable("id") long id) {
        return Map.of("route", "by-id", "value", Long.toString(id));
    }

    @GetMapping("/products/{slug:[a-z][a-z0-9-]*}")
    public Map<String, String> bySlug(@PathVariable("slug") String slug) {
        return Map.of("route", "by-slug", "value", slug);
    }

    @GetMapping("/assets/*.png")
    public Map<String, String> png() {
        return Map.of("route", "one-png-segment");
    }

    @GetMapping("/assets/{*path}")
    public Map<String, String> asset(@PathVariable("path") String path) {
        return Map.of("route", "catch-all", "path", path);
    }

    @GetMapping("/reports/{year:[0-9]+}/{month:[0-9]+}")
    public Map<String, Integer> report(
            @PathVariable("year") int year,
            @PathVariable("month") int month) {
        return Map.of("year", year, "month", month);
    }
}
~~~

**Giải thích tính chạy được:** trong một project Spring Boot hợp lệ có plugin, repository và dependency, public class `RoutingApplication` khớp tên file, controller nằm trong cùng package được scan, `Map.of` có từ Java 9, record không bắt buộc. JSON converter thường là Jackson đi cùng starter web. Đây là mã ứng dụng phụ thuộc Spring, không phải một file có thể `javac` độc lập khi không có classpath.

Trong `build.gradle` đã cấu hình Spring Boot plugin và repositories, dependencies cần:

~~~groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
~~~

Khởi chạy trên macOS/Linux:

~~~bash
./gradlew bootRun
~~~

Hoặc Windows PowerShell:

~~~powershell
.\gradlew.bat bootRun
~~~

Giả định cổng 8080 và không đổi context path, gọi:

~~~bash
curl -i "http://localhost:8080/api/products/search"
curl -i "http://localhost:8080/api/products/42"
curl -i "http://localhost:8080/api/products/spring-mvc"
curl -i "http://localhost:8080/api/assets/logo.png"
curl -i "http://localhost:8080/api/assets/icons/logo.png"
curl -i "http://localhost:8080/api/reports/2026/10"
~~~

Các JSON body được kỳ vọng, không cố định thứ tự field hoặc response headers:

| Request | Handler | JSON body tương đương |
| --- | --- | --- |
| `/api/products/search` | `search()` | `{"route":"search"}` |
| `/api/products/42` | `byId(42)` | `{"route":"by-id","value":"42"}` |
| `/api/products/spring-mvc` | `bySlug("spring-mvc")` | `{"route":"by-slug","value":"spring-mvc"}` |
| `/api/assets/logo.png` | `png()` | `{"route":"one-png-segment"}` |
| `/api/assets/icons/logo.png` | `asset(...)` | `{"route":"catch-all","path":"/icons/logo.png"}` |
| `/api/reports/2026/10` | `report(2026,10)` | `{"year":2026,"month":10}` |

Với `/api/products/search`, regex slug cũng có thể match vì `search` là chữ thường. Nhưng literal route cụ thể hơn nên được chọn.

Thử request không hợp pattern:

~~~bash
curl -i "http://localhost:8080/api/products/Hello"
curl -i "http://localhost:8080/api/products/42/extra"
curl -i "http://localhost:8080/api/reports/2026/Oct"
~~~

Nếu không có route khác, thường là 404: `Hello` không thuộc regex ID hoặc slug; `42/extra` có segment dư; `Oct` không phải chữ số. Response lỗi thực tế có thể do Spring Boot error handling/static resource mapping hoặc Spring Security tùy cấu hình.

Thử giá trị số quá lớn:

~~~bash
curl -i "http://localhost:8080/api/products/99999999999999999999999999"
~~~

Regex `[0-9]+` chấp nhận các chữ số, nhưng kiểu `long` không chứa được số đó. Argument resolution có thể trả 400. Qua đây có thể thấy **regex match không thay thế type conversion**.

## 9. Kiểm thử route bằng MockMvc

Với project Spring Boot có dependency test:

~~~groovy
dependencies {
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
~~~

Một class test `src/test/java/demo/routing/RoutingApplicationTests.java`, cùng package với main application:

~~~java
package demo.routing;

import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RoutingApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void literalWinsOverSlug() throws Exception {
        mvc.perform(get("/api/products/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("search"));
    }

    @Test
    void numberIsCapturedAndConverted() throws Exception {
        mvc.perform(get("/api/products/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("by-id"))
                .andExpect(jsonPath("$.value").value("42"));
    }

    @Test
    void nestedPathUsesCatchAll() throws Exception {
        mvc.perform(get("/api/assets/icons/logo.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("catch-all"))
                .andExpect(jsonPath("$.path").value("/icons/logo.png"));
    }
}
~~~

Chạy trong project Gradle có wrapper và JUnit Platform:

~~~bash
./gradlew test
~~~

Ba test lần lượt kiểm tra route tĩnh thắng route slug, giá trị ID được convert và catch-all capture path nhiều segment. `MockMvc` kiểm tra Spring MVC routing/response mà không cần mở cổng network thực, nhưng **không đại diện đầy đủ cho proxy, Servlet container và URL encoding của production**. Nếu security hoặc ingress có path rewrite, cần thêm integration test chạy ở môi trường tương đương.

## 10. Các tình huống biên và thay đổi phiên bản

### 10.1. Trailing slash

`/api/products/42` và `/api/products/42/` khác nhau. Từ Spring Framework 6.0, việc match **trailing slash tùy chọn không được bật mặc định** như một số cấu hình/phiên bản trước. Vì vậy route `/api/products/{id}` không tự bảo đảm nhận cả hai dạng.

Giải pháp có thể là canonical URL, redirect ở proxy/filter theo chính sách rõ ràng hoặc khai báo đường dẫn bổ sung cần hỗ trợ. Với POST, redirect có thể ảnh hưởng HTTP method; cần chọn loại redirect phù hợp. Đừng âm thầm sửa route bằng cách thêm catch-all quá rộng.

### 10.2. Dấu chấm, suffix và extension

`/users/alice.json` có thể được capture bởi `/users/{name}` thành segment chứa dấu chấm. Spring MVC hiện đại **không tự động dùng `.*` suffix pattern matching** theo mặc định từ Spring 5.3. Dùng `Accept` và `produces` để thương lượng JSON/XML rõ ràng hơn việc suy từ phần mở rộng.

Nếu pattern khai báo tường minh `/images/*.png` thì dấu `.png` vẫn là một phần pattern bình thường, không phải cơ chế suy đoán media type.

### 10.3. URL encoding và dấu slash mã hóa

Ký tự `%2F` đại diện cho slash được encode. Nhưng proxy, container và framework có các chính sách khác nhau về việc chấp nhận/giải mã encoded slash. `PathPatternParser` so pattern trên cấu trúc `PathContainer` theo từng segment nhằm tránh lỗi giải mã toàn path rồi làm thay đổi cấu trúc; điều đó **không bảo đảm** mọi reverse proxy, Servlet container đều chấp nhận encoded slash hoặc xử lý giống nhau.

Đặc biệt khi phân quyền, thành phần security và handler mapping phải đồng thuận về path normalization. Cần kiểm thử encoded slash, double encoding, dot segments và dấu `;` ở ingress/container thực tế; không dựa vào đơn vị test của Spring MVC để kết luận an toàn production.

### 10.4. Unicode, chữ hoa/thường và slug

Regex `[a-z]+` chỉ cho chữ thường ASCII. Nếu slug có tiếng Việt, emoji hoặc ký tự Unicode, cần quyết định chuẩn hóa, percent encoding và giới hạn định dạng một cách nhất quán. Path matching thông thường phân biệt chữ hoa/thường trừ khi được cấu hình khác.

Nên tạo URL bằng URI builder hoặc thư viện HTTP thay vì nối dữ liệu raw từ client vào URL mà không encode. Khi thay đổi quy tắc slug, cân nhắc các URL cũ, link ngoài, bookmark và redirect để tránh phá hỏng hợp đồng public API.

### 10.5. Semicolon và matrix variables

Path có thể chứa semicolon content, ví dụ:

~~~text
/products/42;color=red;size=large
~~~

Spring MVC có cơ chế `@MatrixVariable` cho path parameters. `PathPattern` hỗ trợ parse segment và semicolon content theo thiết kế, nhưng container/proxy/security có thể thay đổi hoặc từ chối những URL này. Chỉ sử dụng matrix variables khi cả hệ thống có hợp đồng và kiểm thử rõ. Đừng nhầm chúng với query parameters dạng `?color=red&size=large`.

### 10.6. Context path và reverse proxy

Giả sử ứng dụng có `server.servlet.context-path=/store`, controller `@RequestMapping("/api")` và `@GetMapping("/products/{id}")`, URL gọi trực tiếp là `/store/api/products/42`. Controller **không viết** `/store` vào mapping.

Nếu reverse proxy phục vụ `/platform/store/api/products/42` nhưng forward nội bộ thành `/store/api/products/42`, prefix `/platform` thuộc proxy. Phải biết proxy giữ hay xóa prefix khi chẩn đoán 404. Forwarded headers chỉ nên được tin khi đến từ proxy đáng tin cậy.

## 11. Lỗi phổ biến và cách debug

| Dấu hiệu | Nguyên nhân có thể | Hướng kiểm tra |
| --- | --- | --- |
| `/items/abc` trả 404 | Regex ID chỉ cho số; route không match | Mapping registry, regex, URL thực |
| `/items/abc` trả 400 | Route `{id}` đã match nhưng convert sang `long` lỗi | Argument resolver, exception handling |
| `/items/search` chạy vào handler lạ | Thiếu literal route hoặc mapping conditions khác | Specificity, HTTP method, consumes/produces |
| `/files/a/b.png` không match `*.png` | Dấu `*` không vượt slash | Cấu trúc path, có cần `{*path}` không |
| App lỗi lúc startup | Catch-all giữa pattern hoặc mapping mơ hồ | Log startup và PathPatternParser |
| Dấu slash cuối trả 404 | Trailing slash không được match mặc định | URL chuẩn và route declaration |
| Chỉ lỗi sau gateway | Proxy rewrite/encoding/context path khác | Path bên ngoài và bên trong |
| URL không tồn tại nhưng trả HTML 200 | Fallback/static resource catch-all có thể đã nhận | Handler đã chọn, Content-Type, body |

Quy trình điều tra ngắn:

1. Kiểm tra URL thực client gửi, loại HTTP method và context path.
2. Xem những `@RequestMapping` đang được đăng ký, bao gồm class prefix, method, params và media type.
3. Xác định path **không match pattern** hay **đã match nhưng argument conversion lỗi**.
4. Nếu nhiều route hợp lệ, kiểm tra specificity/ambiguity; không suy luận theo thứ tự method.
5. Với route có `**` hoặc `{*path}`, xem các path con bị catch-all nhận ngoài ý muốn.
6. Kiểm tra status/header/body HTTP thực tế và log handler/exception resolver.
7. Với encoding/redirect/proxy, xác minh hành vi end-to-end.

Có thể thử:

~~~bash
curl -i "http://localhost:8080/api/products/42"
curl -i "http://localhost:8080/api/products/search"
curl -i "http://localhost:8080/api/assets/icons/logo.png"
~~~

Nếu dùng Spring Boot Actuator và cho phép endpoint mappings với quyền phù hợp, `/actuator/mappings` có thể giúp xem các handler đã đăng ký. Không đưa endpoint quản trị ra Internet công khai.

## 12. Tự kiểm tra kiến thức

**Câu 1.** Có hai route `GET /members/latest` và `GET /members/{id}`. Request `GET /members/latest` được chọn thế nào?

**Câu 2.** Vì sao `/files/*.csv` không match `/files/reports/2026.csv`? Muốn capture toàn bộ phần còn lại nên dùng syntax nào?

**Câu 3.** `/users/{id:[0-9]+}` với argument `long id`. Phân biệt kết quả thường thấy khi gọi `/users/abc` và `/users/999999999999999999999`.

**Câu 4.** `/teams/{team}/members/{member}` nhận request `/teams/core/members/lan`. Các giá trị capture là gì?

**Câu 5.** Tại sao `/resources/**/logo.png` không hợp lệ dưới `PathPatternParser`? Nên thiết kế ra sao?

**Câu 6.** Named catch-all `/files/{*path}` nhận `/files/docs/manual.pdf`; `path` là `docs/manual.pdf` hay `/docs/manual.pdf`? Có nên dùng raw value như filesystem path?

**Câu 7.** Regex `[a-z-]+` có nhận `Lập-trình` hay không? Nếu muốn chấp nhận Unicode cần xét điều gì?

### Đáp án giải thích

1. Literal route `/members/latest` cụ thể hơn variable `/members/{id}`, nên thường được ưu tiên.
2. `*` chỉ trong một segment. Dùng `/files/{*path}` để capture nhiều segment, hoặc `/files/**` nếu chỉ cần match.
3. `abc` không match regex nên có thể 404; chuỗi chỉ gồm số nhưng vượt miền giá trị `long` match regex rồi lỗi conversion, thường 400.
4. `team="core"`, `member="lan"`, mỗi biến nhận một segment.
5. `**` catch-all phải ở cuối với `PathPatternParser`. Dùng `/resources/{*path}` có giới hạn và kiểm tra đuôi trong service nếu thật sự cần, hoặc tạo các route cụ thể.
6. `path="/docs/manual.pdf"` có dấu slash đầu; không dùng trực tiếp để mở file. Cần kiểm tra traversal, normalization và quyền.
7. Không, regex ASCII không nhận chữ hoa hay ký tự có dấu. Cần thiết kế slug/Unicode normalization và encoding nhất quán với proxy/container.

## 13. Checklist áp dụng

Khi xây route Spring MVC:

1. Ưu tiên URI tĩnh và `{id}` một segment có ngữ nghĩa rõ.
2. Dùng regex chỉ khi định dạng ID/slug là một phần của hợp đồng URL.
3. Hiểu `?` và `*` chỉ điều khiển ký tự **trong segment**.
4. Đặt `**` hoặc `{*path}` ở cuối pattern và giới hạn namespace.
5. Dự đoán specificity khi literal, variable, wildcard giao nhau; tránh ambiguous routes.
6. Phân biệt path matching với argument conversion để xử lý lỗi đúng.
7. Test cả URL hợp lệ, URL sai regex, conversion failure, nhiều segment và trailing slash.
8. Kiểm thử security và URL encoding trên container/proxy thật nếu triển khai public.

Tư duy chính là: **pattern quyết định route match; argument resolver chuyển dữ liệu đã capture thành Java value; controller xử lý nghiệp vụ; return-value handler tạo HTTP response.** Hiểu ranh giới đó giúp bạn thiết kế REST endpoint chính xác và debug 404/400 theo đúng nguyên nhân.

**Tài liệu chuẩn:** Spring Framework Reference, *Mapping Requests / URI Patterns / Pattern Comparison*: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html ; Spring Framework Reference, *Path Matching*: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/path-matching.html ; Spring MVC reference về `@PathVariable` và `@RequestMapping`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-routing-005">PathPattern, quy tắc chọn ánh xạ cụ thể và lỗi ánh xạ trùng</a>

<details>
<summary>Xem chi tiết</summary>

# PathPattern, quy tắc chọn ánh xạ cụ thể và lỗi ánh xạ trùng trong Spring MVC

## 1. Mục tiêu và phạm vi

Trong Spring MVC, một URL có thể khớp nhiều `@GetMapping` hoặc `@RequestMapping`. Framework phải kiểm tra toàn bộ điều kiện của request, so sánh độ cụ thể của các ánh xạ hợp lệ rồi chọn đúng một controller method. Bài này giúp bạn:

- Hiểu `PathPattern`, `PathPatternParser` và cách chúng so khớp từng đoạn (`path segment`) của URL.
- Phân biệt literal path, biến `{id}`, regex `{id:...}`, ký tự đại diện `?`, `*`, `**` và biến bắt phần đường dẫn còn lại `{*path}`.
- Dự đoán ánh xạ thắng khi nhiều endpoint cùng phù hợp; biết khi nào có lỗi `Ambiguous mapping` lúc khởi động hoặc `Ambiguous handler methods` khi nhận request.
- Viết route không chồng chéo ngoài ý muốn, kiểm chứng bằng HTTP request và `MockMvc`.

**Phạm vi:** controller dựa trên annotation trong **Spring MVC (Servlet stack)**, minh họa với Spring Framework 6.x / Spring Boot 3.x và Java 17+. Từ Spring MVC 6.0, parsed `PathPattern` là cơ chế khớp đường dẫn mặc định. Ứng dụng cũ có thể dùng `AntPathMatcher` do cấu hình/phiên bản khác; không áp nguyên xi mọi kết luận về cú pháp và thứ tự ưu tiên cho chế độ đó.

## 2. Từ request đến handler method

`@RequestMapping` tại class và method được kết hợp thành route hoàn chỉnh. Ví dụ, `@RequestMapping("/api/orders")` trên class cộng với `@GetMapping("/{id}")` trên method tạo endpoint `GET /api/orders/{id}`.

Luồng chọn handler, giản lược:

```text
HTTP request
  → DispatcherServlet
  → RequestMappingHandlerMapping
  → tìm RequestMappingInfo có các điều kiện phù hợp:
      đường dẫn, HTTP method, params, headers, consumes, produces, ...
  → nếu nhiều ứng viên: so sánh độ ưu tiên trong ngữ cảnh request
  → chọn handler tốt nhất nếu duy nhất
  → chuyển đổi @PathVariable, bind tham số, gọi controller method
```

`PathPatternParser` biên dịch các mẫu URL thành `PathPattern` và so sánh với đường dẫn request đã được phân tích (`RequestPath`/`PathContainer`). Việc xử lý từng đoạn đường dẫn giúp tránh một số rủi ro khi giải mã toàn bộ URL thành một chuỗi rồi mới so khớp. `PathPattern` là thành phần **khớp URL**, còn `RequestMappingInfo` bao gồm thêm HTTP method và các điều kiện request khác.

Hai điều cần tách bạch:

1. **Khớp hay không khớp:** một pattern có thể nhận request này và từ chối request khác. Chẳng hạn `/users/{id:[0-9]+}` không khớp `/users/alice`.
2. **Ai được chọn khi cùng khớp:** nếu cả `/users/current` và `/users/{id}` khớp `/users/current`, pattern literal được chọn. So khớp chỉ trả lời câu hỏi “ứng viên nào hợp lệ”; độ cụ thể quyết định thứ tự giữa chúng.

Tên tham số Java, thứ tự khai báo các method trong class, và thứ tự controller được quét **không phải** quy tắc ưu tiên route.

## 3. Đọc nhanh cú pháp PathPattern trước khi so độ cụ thể

Để so hai pattern cùng khớp, trước hết cần biết **mỗi pattern nhận được bao nhiêu path segment**. Với `PathPatternParser` của Spring MVC 6.x, đây là các dạng thiết yếu:

| Pattern | Phạm vi và giá trị capture |
| --- | --- |
| `/users/me` | Literal: chỉ nhận segment `me`. |
| `/users/{id}` | Nhận một segment, đặt giá trị vào biến `id`. |
| `/users/{id:[0-9]+}` | Một segment phải thỏa regex số; regex **giới hạn matching**, không tự tăng điểm ưu tiên khi hai pattern cùng match. |
| `/files/*.png`, `/files/a?.txt` | `*` và `?` chỉ khớp ký tự trong **một** segment (lần lượt nhiều ký tự và đúng một ký tự). |
| `/assets/**` | Catch-all không đặt tên, nhận nhiều segment ở **cuối** path. |
| `/assets/{*path}` | Catch-all có tên, ví dụ `/assets/css/app.css` cho biến `path=/css/app.css`. |

Không đặt `**` hay `{*path}` ở giữa pattern với `PathPatternParser`: `/a/**/b` là mẫu không hợp lệ. Query string không được tính là path segment, nên `?` trong URI request khác `?` trong pattern. Phân biệt thêm lỗi URL **không match regex** với lỗi conversion Java như `@PathVariable long id` không đọc được giá trị ngoài phạm vi `long`.

Phần quan trọng riêng của bài này không phải liệt kê thêm wildcard, mà là **so các ứng viên đã match**, lý giải vì sao literal thắng một biến, vì sao catch-all xếp sau, và phát hiện mapping trùng/không thể phân định. Hãy kiểm thử riêng vấn đề dấu slash cuối, encoding và reverse proxy trên cấu hình thực tế: Spring MVC 6.x mặc định không tự gộp `/users` với `/users/`, còn URL có `%2F` hoặc dấu `;` có thể chịu chính sách container/proxy/security trước khi đến handler.

## 4. So sánh độ cụ thể khi nhiều pattern đều khớp

Với parsed patterns, Spring dùng `PathPattern.SPECIFICITY_COMPARATOR` để sắp xếp các pattern phù hợp. Trực giác thiết kế là **literal chính xác trước, biến hoặc wildcard sau, catch-all cuối**.

Các nguyên tắc cốt lõi:

1. Pattern catch-all như `/**` và `/{*rest}` đứng sau những pattern không phải catch-all. Giữa các catch-all, pattern có phần tiền tố dài/cụ thể hơn thường được ưu tiên.
2. Với các pattern thông thường, Spring tính độ phức tạp dựa trên số biến URI và wildcard (biến `{x}`, dấu `*`, `?` tạo cơ sở so sánh theo các quy tắc parser). Mẫu có **điểm thấp hơn** cụ thể hơn.
3. Khi điểm bằng nhau, mẫu **dài hơn** được ưu tiên; nếu vẫn hòa, mẫu có **nhiều biến URI hơn wildcard** có thể thắng.
4. Nếu so sánh các ứng viên cuối cùng vẫn hòa và cùng khớp một request, Spring **không tùy tiện lấy method khai báo trước**: lỗi chọn handler do ánh xạ mơ hồ sẽ xuất hiện.

Ví dụ với request `GET /users/me`:

| Ứng viên | Khớp? | Diễn giải |
| --- | --- | --- |
| `/users/me` | Có | Literal trùng chính xác, cụ thể nhất |
| `/users/{id}` | Có | Biến một đoạn, tổng quát hơn literal |
| `/users/*` | Có | Wildcard trong một đoạn |
| `/users/**` | Có | Catch-all ở cuối, thấp ưu tiên hơn |
| `/teams/{id}` | Không | Khác tiền tố |

**Kết quả:** `/users/me` thắng. Nếu bỏ route literal, `/users/{id}` thường thắng `/users/*` khi các yếu tố khác hòa.

Không nên tự viết bộ máy phân giải dựa trên một câu “ít wildcard hơn là thắng” cho mọi trường hợp. Spring xét cả `RequestMappingInfo`: HTTP method, `params`, `headers`, `consumes`, `produces` và các điều kiện khác, rồi mới có thứ tự ưu tiên cuối. Một pattern đẹp cũng không thành ứng viên nếu HTTP method hoặc `Content-Type` của request không phù hợp.

### Những điều kiện khác ngoài path

```java
@RestController
@RequestMapping("/reports")
class ReportController {

    @GetMapping(params = "format=csv", produces = "text/csv")
    String csv() {
        return "id,name\n1,Ada\n";
    }

    @GetMapping(params = "format=json", produces = "application/json")
    String json() {
        return "{\"id\":1,\"name\":\"Ada\"}";
    }

    @PostMapping(consumes = "application/json")
    String create(@RequestBody String payload) {
        return "accepted";
    }
}
```

*Đoạn trên minh họa cách khai báo các điều kiện mapping; đưa vào ứng dụng Spring MVC có các import/bootstrapping tương ứng.* Hai method GET cùng `/reports` nhưng khác `params` và `produces` nên không phải ánh xạ trùng hệt nhau. Ví dụ `GET /reports?format=csv` kèm `Accept: text/csv` chọn `csv()`; `POST /reports` kèm `Content-Type: application/json` chọn `create()`.

Phân biệt `@GetMapping(params = "format=csv")` với `@RequestParam String format`: thuộc tính `params` là **điều kiện chọn mapping**; `@RequestParam` chủ yếu phục vụ **bind/kiểm tra tham số sau khi handler đã được chọn**. Tương tự, `@PathVariable long id` không làm route tự ưu tiên “số” hơn route nhận `String`. Muốn chặn chữ ngay trong khâu khớp URL, dùng regex trong pattern hoặc tạo đường dẫn literal khác nhau.

Nếu đường dẫn tồn tại nhưng HTTP method không được hỗ trợ, mặc định Spring MVC thường phản hồi `405 Method Not Allowed`. Sai `Content-Type` so với `consumes` có thể dẫn đến `415 Unsupported Media Type`, `Accept` không phù hợp với `produces` có thể dẫn đến `406 Not Acceptable`; điều kiện `params` không được đáp ứng có thể dẫn đến `400 Bad Request`. Path không có mapping thường là `404 Not Found`. Các handler ngoại lệ, bộ lọc bảo mật và cấu hình ứng dụng có thể thay đổi kết quả quan sát.

## 5. Ví dụ ứng dụng chạy được với Java 17 và Spring Boot 3

Tạo project Maven như sau (Spring Boot 3.3.13 dùng ở đây làm phiên bản minh họa cố định cho Spring Framework 6.x):

```text
path-pattern-demo/
├── pom.xml
└── src/
    ├── main/java/example/PathPatternDemoApplication.java
    └── test/java/example/ProductRoutesControllerTest.java
```

File `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>path-pattern-demo</artifactId>
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

File `src/main/java/example/PathPatternDemoApplication.java`:

```java
package example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class PathPatternDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(PathPatternDemoApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/products")
class ProductRoutesController {

    @GetMapping("/featured")
    String featured() {
        return "featured";
    }

    @GetMapping("/{id:\\d+}")
    String byId(@PathVariable("id") long id) {
        return "id:" + id;
    }

    @GetMapping("/{slug:[a-z][a-z0-9-]*}")
    String bySlug(@PathVariable("slug") String slug) {
        return "slug:" + slug;
    }

    @GetMapping("/files/{*path}")
    String file(@PathVariable("path") String path) {
        return "file:" + path;
    }
}
```

Lệnh thực thi với **JDK 17+ và Maven** đã được cài đặt:

```bash
mvn test
mvn spring-boot:run
```

Ở terminal khác, gửi các request sau:

```bash
curl -s http://localhost:8080/api/products/featured
curl -s http://localhost:8080/api/products/42
curl -s http://localhost:8080/api/products/blue-mug
curl -s http://localhost:8080/api/products/files/guide/intro.pdf
```

Mỗi lệnh trả về đúng body tương ứng (mỗi body không tự thêm ký tự xuống dòng):

```text
featured
id:42
slug:blue-mug
file:/guide/intro.pdf
```

Giải thích:

- `/api/products/featured` đồng thời khớp literal và mẫu slug, nhưng literal thắng.
- `/api/products/42` chỉ khớp mẫu `{id:\\d+}` trong hai mẫu `id`/`slug`; binding `long` diễn ra sau khi route đã chọn.
- `/api/products/blue-mug` khớp mẫu slug; không khớp biểu thức số.
- `/api/products/files/guide/intro.pdf` được bắt bởi `{*path}` trong route con `/files`. Mẫu slug không thể hấp thụ nhiều đoạn đường dẫn.

Với cấu hình mặc định của ví dụ, `GET /api/products/42/` có dấu slash cuối không khớp `GET /api/products/42`; `POST /api/products/42` có thể trả `405` vì chỉ khai báo GET. Khi kiểm thử bằng `curl -i`, hãy xem mã HTTP ngoài body để phân biệt 404, 405 và lỗi điều kiện mapping.

## 6. Hai kiểu lỗi ánh xạ trùng rất khác nhau

### 6.1. Lỗi ngay lúc khởi động: hai handler có ánh xạ đồng nhất

**Ví dụ cố ý sai** dưới đây là một controller thay thế, **không** thêm nó vào project đang chạy ở mục 5:

```java
@RestController
@RequestMapping("/api/duplicate")
class DuplicateController {

    @GetMapping("/{id}")
    String first(@PathVariable String id) {
        return "first";
    }

    @GetMapping("/{id}")
    String second(@PathVariable String id) {
        return "second";
    }
}
```

Cả hai method đăng ký cùng `RequestMappingInfo` (HTTP GET, `/api/duplicate/{id}`, không khác điều kiện nào khác). Khi tạo registry cho các controller, `RequestMappingHandlerMapping` phát hiện xung đột và có thể ném `IllegalStateException` với nội dung liên quan `Ambiguous mapping`/`Cannot map ... There is already ... mapped`. Ứng dụng không khởi động hoàn tất.

Cách xử lý: bỏ endpoint trùng, gộp logic vào một method, hoặc thiết kế lại hợp đồng HTTP để các ánh xạ thực sự khác nhau về đường dẫn/method/điều kiện có ý nghĩa. Đổi **tên Java method** hoặc tên tham số `@PathVariable` nhưng giữ nguyên annotation mapping không giải quyết trường hợp trùng hệt.

### 6.2. Lỗi lúc nhận request: hai mẫu khác nhau nhưng ưu tiên ngang nhau

**Ví dụ cố ý sai**, cũng không thêm vào project đang chạy:

```java
@RestController
@RequestMapping("/api/ambiguous")
class AmbiguousController {

    @GetMapping("/{id}")
    String byId(@PathVariable String id) {
        return "id:" + id;
    }

    @GetMapping("/{slug}")
    String bySlug(@PathVariable String slug) {
        return "slug:" + slug;
    }
}
```

Hai chuỗi pattern `/{id}` và `/{slug}` không giống hệt nhau về tên biến, nên có thể được đăng ký thành hai mapping riêng. Nhưng với `GET /api/ambiguous/123`, **cả hai đều khớp một đoạn**, đều có mức cụ thể tương đương và không có điều kiện nào để phân định. Spring MVC không biết chọn method nào, dẫn tới `IllegalStateException` với thông điệp dạng `Ambiguous handler methods mapped for '/api/ambiguous/123'` khi request đến. Kết quả HTTP thường là 500 nếu không có xử lý ngoại lệ tùy chỉnh.

**Điểm dễ nhầm:** đặt `@PathVariable long id` ở method thứ nhất và `@PathVariable String slug` ở method thứ hai không giải quyết xung đột: chuyển đổi đối số Java chỉ xảy ra sau lựa chọn handler. Việc thêm một regex vào một route cũng chưa chắc tạo ra “độ ưu tiên tuyệt đối” nếu cả hai vẫn cùng khớp; tránh trùng tập URL hoặc định danh route rõ ràng.

Hai cách sửa hợp lý:

```java
// Phương án A: phân biệt URL rõ ràng.
@GetMapping("/by-id/{id}")
String byId(@PathVariable long id) { return "id:" + id; }

@GetMapping("/by-slug/{slug}")
String bySlug(@PathVariable String slug) { return "slug:" + slug; }

// Phương án B: regex có hai tập giá trị tách biệt.
@GetMapping("/{id:\\d+}")
String numericId(@PathVariable long id) { return "id:" + id; }

@GetMapping("/{slug:[a-z][a-z-]*}")
String alphabeticSlug(@PathVariable String slug) { return "slug:" + slug; }
```

*Đây là hai phương án thay thế để đưa vào một controller phù hợp, không phải bốn method cần khai báo đồng thời.*

Nếu tên slug có thể chỉ gồm chữ số (ví dụ `"123"`), hãy xác định quy tắc sản phẩm: ưu tiên dành namespace riêng cho ID/slug thay vì để hai route tranh một URL. Không dựa vào thứ tự code để “vá” ánh xạ mơ hồ.

### 6.3. Checklist chẩn đoán

1. Xác định lỗi xảy ra **trong quá trình startup** hay **chỉ khi gọi một URL**. Startup thường chỉ ra mapping trùng; request-time thường là các mẫu khác nhau nhưng có cùng độ ưu tiên đối với URL cụ thể.
2. Ghi đầy đủ method + path + query string + `Content-Type` + `Accept` + các header liên quan. Đừng kiểm tra mỗi URL.
3. Mở phần mapping của hai handler trong stack trace; cộng prefix ở class với path ở method để so sánh URL cuối cùng.
4. Kiểm tra wildcard và biến có thể nhận cùng một path; đặc biệt `/{id}` vs `/{slug}`, regex chồng lấp, `*` và catch-all.
5. Xem danh sách mappings đã đăng ký (ví dụ endpoint `/actuator/mappings` khi ứng dụng có Spring Boot Actuator và cho phép truy cập) hoặc bật log phù hợp cho `RequestMappingHandlerMapping`.
6. Viết test cho những URL ở vùng giao nhau và cả những URL lẽ ra không được khớp; sửa thiết kế đường dẫn/điều kiện rồi chạy lại.

## 7. Kiểm thử route bằng MockMvc

File `src/test/java/example/ProductRoutesControllerTest.java` (phù hợp với project ở mục 5):

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductRoutesController.class)
class ProductRoutesControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void literalBeatsVariableAndRegexSeparatesIdsFromSlugs() throws Exception {
        mvc.perform(get("/api/products/featured"))
                .andExpect(status().isOk())
                .andExpect(content().string("featured"));

        mvc.perform(get("/api/products/42"))
                .andExpect(status().isOk())
                .andExpect(content().string("id:42"));

        mvc.perform(get("/api/products/blue-mug"))
                .andExpect(status().isOk())
                .andExpect(content().string("slug:blue-mug"));
    }

    @Test
    void catchAllOnlyAppliesUnderFilesPrefix() throws Exception {
        mvc.perform(get("/api/products/files/guide/intro.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().string("file:/guide/intro.pdf"));

        mvc.perform(get("/api/products/42/"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/products/42"))
                .andExpect(status().isMethodNotAllowed());
    }
}
```

Chạy `mvn test`. Các assertion này kiểm tra **handler thực sự được chọn** bằng body, đồng thời kiểm tra trailing slash và method. Với sản phẩm thực tế, bổ sung các trường hợp `params`, `headers`, `consumes`, `produces` và những request có khả năng khớp hai route.

Để tránh test “xanh” giả khi ứng dụng có security, filter, servlet path hoặc cấu hình MVC bổ sung, hãy kết hợp `MockMvc` với một số bài kiểm tra tích hợp chạy trên cấu hình triển khai thực tế. Nếu test tình huống cố ý ambiguous, tách riêng controller lỗi vào test context của nó; không đặt nó trong cùng ứng dụng demo vì có thể khiến context không khởi động.

## 8. Các lỗi tư duy thường gặp và cách phòng tránh

| Nhầm lẫn | Điều đúng hơn |
| --- | --- |
| “Định nghĩa `@GetMapping` trước thì nó thắng.” | Spring so điều kiện và độ cụ thể, không lấy thứ tự source code làm hợp đồng. |
| “`/{id}` và `/{slug}` khác nhau nên chắc chắn không xung đột.” | Hai tên biến khác nhau vẫn khớp cùng một tập path một đoạn; request có thể mơ hồ. |
| “`@PathVariable Long id` làm mapping chỉ nhận số.” | Type conversion diễn ra sau chọn route; để lọc đường dẫn cần regex hoặc phân biệt prefix. |
| “Có regex hẹp hơn luôn thắng biến tổng quát.” | Regex giới hạn việc **khớp**; khi cả hai cùng khớp, phải xét comparator, không suy đoán theo ngữ nghĩa regex. |
| “`*` khớp mọi cấp thư mục.” | `*` ở trong một đoạn; `**`/`{*path}` mới bắt nhiều đoạn và phải ở cuối. |
| “Thêm `/**` ở mọi nơi là an toàn.” | Catch-all dễ làm route thiếu rõ ràng; chỉ dùng khi cần và kiểm thử các path biên. |
| “`GET /a` luôn giống `GET /a/`.” | Spring MVC 6.x mặc định không tự nối trailing slash. |
| “`@RequestParam` tự phân biệt hai `@GetMapping` cùng path.” | Cần điều kiện mapping `params=...` nếu muốn dùng query parameter để chọn handler. |
| “Mọi request không vào controller đều là 404.” | 405/400/406/415 hoặc lỗi bảo mật/bind có thể xuất hiện tùy điều kiện và cấu hình. |

**Quy tắc thiết kế thực dụng:** ưu tiên đường dẫn literal có ý nghĩa; đặt namespace khác nhau cho hai loại tài nguyên dễ chồng lấp; dùng regex để giới hạn biến khi thật sự xác định được miền giá trị; tránh catch-all rộng; bảo đảm mỗi request dự kiến có một handler thắng duy nhất. Đối với hệ thống đã public API, kiểm tra tương thích URL trước khi đổi đường dẫn.

## 9. Tự kiểm tra

1. Với ba mapping GET `/shop/new`, `/shop/{sku}` và `/shop/**`, `GET /shop/new` chọn ai? **Đáp án:** `/shop/new` vì literal cụ thể hơn.
2. `/files/*` có khớp `/files/icons/logo.svg` không? **Không**, vì `*` chỉ trong một segment.
3. Nếu cùng tồn tại `GET /people/{id}` và `GET /people/{name}`, vì sao `GET /people/7` có thể lỗi khi chạy? **Hai pattern cùng khớp, cùng độ cụ thể**, trong khi tên biến không phân biệt giá trị.
4. Thêm `@PathVariable Long id` có chữa được câu 3? **Không**; phải sửa hợp đồng URL hoặc ràng buộc pattern không giao nhau.
5. Hai method cùng `@GetMapping("/sync")` không điều kiện khác nhau gây lỗi lúc nào? **Khi đăng ký mapping lúc khởi động**, thường có lỗi `Ambiguous mapping`.
6. Vì sao `POST /api/products/42` không vào method GET dù path trùng? **HTTP method** là một phần của `RequestMappingInfo`; với cấu hình demo Spring MVC sẽ phản hồi 405.

## 10. Tài liệu gốc để tra cứu

- [Spring Framework — Mapping Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html): URI patterns, chọn pattern cụ thể và các điều kiện mapping.
- [Spring Framework — Servlet Path Matching](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/handlermapping-path.html): parsed `RequestPath`, `PathPatternParser`, khác biệt với `AntPathMatcher` và xử lý URL encoding.
- [PathPatternParser API (Spring 6.2)](https://docs.spring.io/spring-framework/docs/6.2.7/javadoc-api/org/springframework/web/util/pattern/PathPatternParser.html): mặc định case-sensitive và trailing slash.
- [RequestMappingInfo API (Spring 6.2)](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/servlet/mvc/method/RequestMappingInfo.html): tập điều kiện dùng để nhận diện, lọc và so sánh mapping.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-routing-006">Xử lý HEAD, OPTIONS và phân biệt lỗi 404, 405, 406, 415</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý HEAD, OPTIONS và phân biệt lỗi HTTP 404, 405, 406, 415 trong Spring MVC

## Mục tiêu và phạm vi

Sau bài này, bạn có thể giải thích vì sao một phương thức điều khiển dùng `@GetMapping` thường xử lý được `HEAD`, đọc header `Allow` trong phản hồi `OPTIONS`, đồng thời xác định chính xác một yêu cầu sai ở **đường dẫn**, **HTTP method**, **kiểu dữ liệu phản hồi** hay **kiểu dữ liệu gửi lên**. Ví dụ sử dụng Spring MVC trên Servlet stack, Java 17+ và cú pháp Spring Framework 6+/Spring Boot 3.x. Các quy tắc HTTP là nền tảng; phần body lỗi mặc định có thể khác giữa các phiên bản, cấu hình Spring Boot, bộ lọc và máy chủ.

## 1. Luồng chọn handler: nơi phát sinh bốn mã lỗi

Với `@RequestMapping`, Spring MVC chọn handler dựa trên nhiều điều kiện. Để chẩn đoán, hãy kiểm tra theo câu hỏi nghiệp vụ sau:

1. **Đường dẫn:** Có controller/resource nào nhận URL này không? Nếu không có tài nguyên tương ứng, kết quả thường là `404 Not Found`.
2. **Phương thức HTTP:** Đường dẫn phù hợp nhưng không hỗ trợ `GET`, `POST`, `PUT`… mà client gửi? Với mapping controller điển hình, kết quả là `405 Method Not Allowed`; xem header `Allow` để biết phương thức được hỗ trợ.
3. **`Content-Type` của request:** Endpoint yêu cầu nhận JSON nhưng client gửi `text/plain`? Có thể là `415 Unsupported Media Type`.
4. **`Accept` của request:** Client chỉ chấp nhận XML nhưng endpoint chỉ cung cấp JSON? Có thể là `406 Not Acceptable`.

Đây là **mô hình phân loại**, không phải cam kết Spring luôn xét tất cả điều kiện theo thứ tự 1→4; việc chọn mapping, message converter, handler ngoại lệ và filter có thể làm thay đổi mã lỗi xuất hiện khi một request vi phạm đồng thời nhiều điều kiện. Trong xử lý tiêu chuẩn của `RequestMappingInfoHandlerMapping`, các điều kiện `method`, `consumes` và `produces` được xem xét khi không tìm thấy mapping khớp hoàn toàn. Ngoài ra, body request/response còn có thể bị từ chối ở giai đoạn đọc/ghi bằng `HttpMessageConverter`.

| Mã | Tên chuẩn | Câu hỏi chẩn đoán | Dấu hiệu thường gặp |
| --- | --- | --- | --- |
| `404` | Not Found | URL/resource có tồn tại hay được định tuyến không? | Sai đường dẫn, thiếu tiền tố, resource không tìm thấy |
| `405` | Method Not Allowed | URL đúng nhưng method sai? | `POST` vào endpoint chỉ khai báo `GET`; phản hồi có `Allow` |
| `406` | Not Acceptable | Server có representation nào hợp lệ với `Accept` không? | Client đòi `application/xml`, server chỉ `produces = application/json` |
| `415` | Unsupported Media Type | Body client gửi có `Content-Type` mà endpoint/decoder nhận được không? | Gửi `text/plain` vào endpoint `consumes = application/json` |

Hai header rất dễ nhầm:

- `Content-Type` mô tả **loại dữ liệu trong body được gửi** (request) hoặc **loại dữ liệu trong body được trả về** (response).
- `Accept` mô tả **các loại dữ liệu client có thể nhận về**; ví dụ `Accept: application/json`. Nó không nói body request là JSON.
- `consumes` là điều kiện trên kiểu dữ liệu **đầu vào**, thường áp dụng cho POST/PUT/PATCH có body. `produces` là điều kiện trên kiểu dữ liệu **đầu ra**. Spring có thể sử dụng content negotiation và `HttpMessageConverter` ngay cả khi không khai báo hai thuộc tính này.

## 2. HTTP HEAD: lấy metadata, không nhận response body

Theo ngữ nghĩa HTTP, `HEAD` giống `GET` ở chỗ hỏi thông tin về resource nhưng **response không chứa message body**. Header của phản hồi `HEAD` nên tương ứng với `GET`, chẳng hạn `Content-Type`, `ETag`, `Last-Modified` hoặc `Content-Length` khi server có thể xác định. Không nên mặc định mọi server đều trả `Content-Length` hoặc mọi header giống hệt `GET`.

Trong Spring MVC, `@GetMapping` và `@RequestMapping(method = RequestMethod.GET)` **tự hỗ trợ HEAD**. Thông thường bạn không phải viết `@RequestMapping(method = RequestMethod.HEAD)` riêng; Spring/Servlet xử lý việc không gửi body. Điều này có hệ quả:

- Client gửi `HEAD /api/items/5` có thể đi qua cùng controller logic với `GET /api/items/5`; việc đọc database, tính toán hoặc lỗi nghiệp vụ vẫn có thể xảy ra.
- `HEAD` **không tự làm GET rẻ hơn**. Với dữ liệu tạo ra tốn kém, cần cân nhắc caching, metadata hoặc handler `HEAD` riêng khi có lý do đo được.
- `HEAD` đúng nghĩa phải không có response body. Không suy luận rằng HEAD trả `204 No Content`: một HEAD thành công của GET thường có **status 200**, chỉ khác ở việc không gửi body.
- Dùng `curl -I` (hoặc `curl --head`) để thử; tránh `curl -X HEAD` vì khi ép method thủ công, `curl` có thể vẫn đợi body theo cách xử lý khác với chế độ `--head`.

Ví dụ minh họa:

```http
GET /api/items/5 HTTP/1.1
Host: localhost:8080
Accept: application/json
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":5,"name":"Pen"}
```

Cùng tài nguyên với `HEAD`:

```http
HEAD /api/items/5 HTTP/1.1
Host: localhost:8080
```

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

Phần ví dụ HTTP cố tình chỉ nêu các header cần thiết; nhiều máy chủ có thể thêm `Content-Length`, `Date` và các header khác. **Không có JSON/body** sau response HEAD.

Khi cần hành vi riêng, có thể khai báo mapping tường minh cho `HEAD`, nhưng phải kiểm tra tính tương thích với `GET` và không dùng phép viết phản hồi HEAD để tạo hiệu ứng phụ bất ngờ. Tránh coi HEAD là cách kiểm tra URL tồn tại hoàn toàn miễn phí.

## 3. HTTP OPTIONS: hỏi các phương thức được hỗ trợ

`OPTIONS` hỏi khả năng giao tiếp của tài nguyên. Trong Spring MVC, nếu chưa định nghĩa handler OPTIONS riêng, framework thường tự phản hồi bằng header `Allow` được tổng hợp từ các `@RequestMapping` cùng khớp URL. Mapping chỉ khai báo `GET` thường hỗ trợ `GET` và `HEAD`; `OPTIONS` có thể xuất hiện trong `Allow` do xử lý tự động. Nếu một `@RequestMapping` không giới hạn method, danh sách mặc định có thể rất rộng (`GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS`), vì vậy **nên khai báo method tường minh** cho API.

Ví dụ endpoint chỉ hỗ trợ GET:

```http
OPTIONS /api/items/5 HTTP/1.1
Host: localhost:8080
```

Phản hồi điển hình:

```http
HTTP/1.1 200 OK
Allow: GET, HEAD, OPTIONS
```

Thứ tự các method trong `Allow` không phải điều bạn nên dựa vào khi kiểm thử; tùy handler và môi trường, giá trị có thể thay đổi. Nếu endpoint hỗ trợ thêm PUT/DELETE, `Allow` sẽ phản ánh cấu hình mapping tương ứng.

**OPTIONS thông thường khác với CORS preflight**:

- Một preflight của trình duyệt cũng là `OPTIONS`, nhưng thường có `Origin` và `Access-Control-Request-Method`, có thể thêm `Access-Control-Request-Headers`.
- CORS preflight hỏi: “Origin này có được phép gọi method/header như dự kiến không?”; phản hồi được kiểm soát bởi cấu hình CORS, các header như `Access-Control-Allow-Origin`, `Access-Control-Allow-Methods` và `Access-Control-Allow-Headers`.
- Một API có mapping `POST` không có nghĩa trình duyệt từ origin khác được phép POST. CORS hay Spring Security có thể chặn preflight với `403` hoặc phản hồi khác trước khi tới controller.
- Header `Allow` của HTTP không thay thế `Access-Control-Allow-Methods`. Nếu debug lỗi CORS, kiểm tra cấu hình CORS/filter/security chứ không chỉ đọc `@RequestMapping`.

Ví dụ preflight:

```http
OPTIONS /api/items HTTP/1.1
Origin: https://frontend.example
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type
```

Đây là yêu cầu CORS; **không được giả định** nó thành công khi chưa cấu hình origin, method và header được phép.

## 4. Ví dụ controller Java 17+ để tự thử

Dùng một ứng dụng Spring Boot 3.x đã có dependency `spring-boot-starter-web`. Đặt lớp sau tại `src/main/java/com/example/demo/ItemController.java`; package phải nằm trong phạm vi component scan của lớp `@SpringBootApplication`. Không cần endpoint HEAD hoặc OPTIONS riêng cho bài này.

```java
package com.example.demo;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Item find(@PathVariable("id") long id) {
        return new Item(id, "Pen");
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Item> create(@RequestBody Item item) {
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    public record Item(long id, String name) {}
}
```

Đọc mapping:

- `GET /api/items/5` trả JSON cho bản ghi `Item(5, "Pen")`.
- `HEAD /api/items/5` được hỗ trợ nhờ mapping GET.
- `OPTIONS /api/items/5` thường tự trả `Allow` cho resource đó.
- `POST /api/items` nhận **request JSON** và trả **response JSON** với status `201 Created`. Đây chỉ là demo echo dữ liệu; không lưu vào database.
- `PUT /api/items/5` sai method. `GET /api/items/missing/child` sai đường dẫn. `Accept: application/xml` không phù hợp với GET chỉ xuất JSON. `POST /api/items` có `Content-Type: text/plain` không phù hợp với POST chỉ nhận JSON.

Nếu tạo dự án mới, tối thiểu cần một lớp `@SpringBootApplication` có hàm `main`, Java 17+, dependency Web, Maven/Gradle phù hợp. Trong dự án Maven có wrapper:

```bash
./mvnw spring-boot:run
```

Trên Windows PowerShell có thể chạy `.\mvnw.cmd spring-boot:run`. Port mặc định là `8080` nếu chưa đổi `server.port`. Mã controller trên là lớp **đầy đủ có thể biên dịch trong dự án Spring Boot** đã cấu hình dependency; một tệp Java độc lập không thể `javac` mà thiếu Spring libraries.

## 5. Thực hành với curl: sáu tình huống phân biệt

Các lệnh dưới đây chạy khi ứng dụng đã khởi động trên `localhost:8080`. Header khác có thể xuất hiện tùy cấu hình; tập trung vào **HTTP status**, `Allow`, `Content-Type` và việc có/không có body.

**(A) GET thành công, 200:**

```bash
curl -i -H "Accept: application/json" http://localhost:8080/api/items/5
```

Body JSON dự kiến:

```json
{"id":5,"name":"Pen"}
```

**(B) HEAD thành công, 200:**

```bash
curl -I http://localhost:8080/api/items/5
```

Kiểm tra status và response headers; không có body JSON. `Content-Length` có thể được cung cấp tùy cơ chế xử lý và máy chủ.

**(C) OPTIONS, kiểm tra Allow:**

```bash
curl -i -X OPTIONS http://localhost:8080/api/items/5
```

Dự kiến `200 OK` với `Allow` gồm `GET`, `HEAD` (thông thường có `OPTIONS`). Nếu dùng `OPTIONS` kèm header CORS, đó là phép thử khác.

**(D) 404: URL không khớp:**

```bash
curl -i http://localhost:8080/api/items/5/unknown
```

Dự kiến `404 Not Found` vì không có mapping phù hợp đường dẫn này. Spring Boot có thể định tuyến yêu cầu sang bộ xử lý static resource trước khi kết luận không tồn tại; kết quả cuối vẫn có thể là 404 nhưng exception bên trong khác.

**(E) 405: URL đúng, HTTP method sai:**

```bash
curl -i -X PUT http://localhost:8080/api/items/5
```

Dự kiến `405 Method Not Allowed`; theo chuẩn HTTP, response 405 phải có header `Allow` liệt kê phương thức hiện được hỗ trợ cho tài nguyên. Ở controller demo, đó là GET/HEAD và thường OPTIONS. Đừng “sửa” 405 bằng cách đổi đường dẫn khi đường dẫn đã đúng; kiểm tra method.

**(F) 406: Client chỉ chấp nhận XML, server chỉ sinh JSON:**

```bash
curl -i -H "Accept: application/xml" http://localhost:8080/api/items/5
```

Dự kiến `406 Not Acceptable` vì mapping GET khai báo `produces = application/json`. Nếu gửi `Accept: */*` hoặc `Accept: application/json`, representation JSON có thể được chọn. Nếu thêm endpoint hỗ trợ XML hoặc thay cấu hình negotiation/converter, hành vi có thể khác.

**(G) 415: Client gửi loại body sai:**

```bash
curl -i -X POST http://localhost:8080/api/items \
  -H "Content-Type: text/plain" \
  --data "hello"
```

Dự kiến `415 Unsupported Media Type` vì POST khai báo `consumes = application/json`. Dùng request JSON hợp lệ:

```bash
curl -i http://localhost:8080/api/items \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data '{"id":9,"name":"Notebook"}'
```

Dự kiến `201 Created` và body:

```json
{"id":9,"name":"Notebook"}
```

Nếu chạy lệnh shell nhiều dòng: cú pháp `\` là nối dòng cho bash, zsh; với PowerShell có thể viết toàn bộ `curl` trên **một dòng** và gọi `curl.exe` để tránh alias `Invoke-WebRequest` ở môi trường Windows PowerShell cũ. Tránh biến ví dụ minh họa thành khẳng định rằng tất cả môi trường cùng trả một JSON lỗi: chỉ mã HTTP và các header bắt buộc/được định nghĩa là trọng tâm.

## 6. Khi nào Spring MVC trả các lỗi này?

### 404 Not Found

Nếu không có handler/resource phù hợp, Spring MVC hoặc tầng phục vụ tài nguyên có thể dẫn đến 404. Trong các phiên bản Spring mới, handler static resource có thể gây `NoResourceFoundException`; trường hợp không tìm thấy handler có thể dùng `NoHandlerFoundException` tùy cấu hình. Không nên suy ra lớp exception cụ thể chỉ từ status.

Các nguyên nhân thực tế: sai `context-path`, thiếu prefix `@RequestMapping` ở class, sai trailing slash/case, proxy rewrite URL, định tuyến path variable khác dự kiến, file static không tồn tại. Nếu đường dẫn tồn tại ở service nhưng 404 phía gateway, cần kiểm tra cả reverse proxy.

### 405 Method Not Allowed

Đường dẫn khớp với controller mapping nhưng HTTP method không được hỗ trợ. Spring MVC thường biểu diễn trường hợp này bằng `HttpRequestMethodNotSupportedException`. Kiểm tra `Allow`, `@GetMapping`/`@PostMapping` và method thực tế ở network tab; đôi khi HTML form/browser hoặc HTTP client đã thay method. Đừng quên `HEAD` ngầm khi có GET.

Một URL “có tồn tại” theo ứng dụng không đảm bảo mọi loại request tới URL đó đều nhận 405; filters, security, tài nguyên tĩnh hoặc điều kiện mapping khác có thể can thiệp.

### 406 Not Acceptable

Client yêu cầu representation qua `Accept` nhưng server không thể cung cấp loại đó. Spring MVC có thể phát sinh `HttpMediaTypeNotAcceptableException` tại bước chọn mapping `produces` hoặc negotiation/conversion khi ghi response. Kiểm tra `Accept`, `produces`, các message converter đã đăng ký và kiểu object trả về. Trả object Java không tự đảm bảo server hỗ trợ mọi định dạng; ví dụ XML đòi hỏi cấu hình/converter phù hợp.

Ví dụ: `Accept: application/xml` không đồng nghĩa với “body request là XML”. Đây là yêu cầu **response XML**.

### 415 Unsupported Media Type

Request mang `Content-Type` mà mapping `consumes` hoặc message converter đọc body không hỗ trợ. Spring MVC có thể phát sinh `HttpMediaTypeNotSupportedException`. Kiểm tra header `Content-Type` thực gửi, `consumes` và khả năng converter đọc loại dữ liệu đó vào kiểu `@RequestBody`. Trường hợp body là JSON nhưng gắn nhãn `text/plain` vẫn có thể bị 415 trước khi parse.

Ngược lại, nếu header `Content-Type: application/json` đã đúng nhưng **JSON sai cú pháp**, lỗi thường là **400 Bad Request**, ví dụ `HttpMessageNotReadableException`, chứ không phải 415. Không nhầm với `406`: 415 hỏi **server đọc gì từ client**, 406 hỏi **client chấp nhận gì từ server**.

## 7. Bảng chẩn đoán nhanh với cùng bộ endpoint

| Request | Điều kiện chính | Status kỳ vọng ở demo |
| --- | --- | --- |
| `GET /api/items/5`, `Accept: application/json` | Path, method, output phù hợp | `200` |
| `HEAD /api/items/5` | GET mapping tự hỗ trợ HEAD | `200`, không body |
| `OPTIONS /api/items/5` | Query các HTTP method hỗ trợ | Thường `200`, có `Allow` |
| `GET /api/items/5/unknown` | Không có mapping/resource phù hợp | `404` |
| `PUT /api/items/5` | Path có GET nhưng không có PUT | `405`, có `Allow` |
| `GET /api/items/5`, `Accept: application/xml` | Không đáp ứng kiểu response | `406` |
| `POST /api/items`, `Content-Type: text/plain` | Không nhận kiểu request body | `415` |
| `POST /api/items`, `Content-Type: application/json`, JSON lỗi | Kiểu media được nhận nhưng cú pháp body sai | Thường `400` |
| `POST /api/items`, JSON hợp lệ và `Accept: application/json` | Input/output phù hợp | `201` |

Vì `OPTIONS` còn phục vụ preflight và lỗi có thể được tùy chỉnh bởi `@ControllerAdvice`, bảng này mô tả môi trường demo không thêm security/CORS filters đặc biệt.

## 8. Kiểm thử tự động bằng MockMvc

Với dự án Spring Boot đã cài `spring-boot-starter-test`, đặt tệp dưới `src/test/java/com/example/demo/HttpMethodRoutingTest.java`. Test sử dụng controller demo và môi trường Spring Boot chứa nó. Các assertion tập trung vào status và header cốt lõi; không phụ thuộc vào JSON lỗi do Boot tạo ra.

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HttpMethodRoutingTest {

    @Autowired
    MockMvc mvc;

    @Test
    void headUsesGetMappingWithoutBody() throws Exception {
        mvc.perform(head("/api/items/5"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    @Test
    void optionsReportsSupportedMethods() throws Exception {
        mvc.perform(options("/api/items/5"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                HttpHeaders.ALLOW, containsString("GET")));
    }

    @Test
    void missingPathReturns404() throws Exception {
        mvc.perform(get("/api/items/5/unknown"))
            .andExpect(status().isNotFound());
    }

    @Test
    void wrongMethodReturns405() throws Exception {
        mvc.perform(put("/api/items/5"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string(
                HttpHeaders.ALLOW, containsString("GET")));
    }

    @Test
    void incompatibleAcceptReturns406() throws Exception {
        mvc.perform(get("/api/items/5")
                .accept(MediaType.APPLICATION_XML))
            .andExpect(status().isNotAcceptable());
    }

    @Test
    void incompatibleContentTypeReturns415() throws Exception {
        mvc.perform(post("/api/items")
                .contentType(MediaType.TEXT_PLAIN)
                .content("hello"))
            .andExpect(status().isUnsupportedMediaType());
    }
}
```

Chạy `./mvnw test` (Windows: `.\mvnw.cmd test`). Nếu dự án đã có Spring Security hoặc cấu hình CORS riêng, hãy cấu hình MockMvc/test security phù hợp; những bộ lọc có thể trả `401`, `403` hoặc điều hướng khác trước khi logic controller chạy.

## 9. Debug thực tế và lỗi dễ mắc

1. **Xác nhận request thực nhận**: method, URL/path đã qua reverse proxy, query, `Content-Type`, `Accept`, body. Đặc biệt công cụ gọi API có thể tự thêm `Accept: */*` hoặc thay `Content-Type` khi chọn form-data.
2. **Đọc mapping đã đăng ký**: kiểm tra annotation ở class và method; Spring Boot Actuator `/actuator/mappings` (nếu có dependency và được bật/expose) giúp xem mapping thực. Chỉ mở endpoint quản trị theo chính sách an toàn ứng dụng.
3. **Dùng ma trận request tối thiểu**: cùng URL, đổi từng thứ một: method → `Accept` → `Content-Type`. Nếu thay nhiều header/method cùng lúc, mã lỗi hiện ra có thể che lỗi khác.
4. **Nhìn response status và headers trước body**: `Allow` hỗ trợ điều tra 405/OPTIONS; response `Content-Type` cho biết server trả loại nội dung gì. Body thông báo lỗi có thể bị cấu hình bằng `ProblemDetail`, error controller hay gateway.
5. **Tách CORS khỏi mapping**: xem `Origin`/`Access-Control-Request-Method` và chính sách CORS; preflight fail không đồng nghĩa controller không hỗ trợ method.
6. **Tách 415 khỏi JSON parse failure**: `415` là vấn đề media type; `400` thường là body sai định dạng/thiếu trường/chuyển đổi dữ liệu, tùy validation.
7. **Tách 404 khỏi 405**: 404 nghi path/resource; 405 nghi method. Nếu tất cả method đều 404, kiểm tra tiền tố route và deployment trước.
8. **Đừng dùng `@RequestMapping` không khai báo method tùy tiện**: nó có thể khiến `Allow` rộng ngoài ý định và làm API nhận những method không mong muốn.
9. **Tránh tùy chỉnh trả lỗi làm mất header bắt buộc**: khi bắt `HttpRequestMethodNotSupportedException` để chuẩn hóa JSON lỗi, vẫn phải giữ `Allow`. Spring MVC đã có `DefaultHandlerExceptionResolver` và `ResponseEntityExceptionHandler`; ưu tiên giữ semantics chuẩn khi viết `@ControllerAdvice`.
10. **Không đoán “404 luôn là NoHandlerFoundException”**: resource handler, path matching, version framework và custom error handler có thể khác. Dùng log và mapping để truy ra bước lỗi thật.

## 10. Tự kiểm tra kiến thức

- `HEAD /api/items/5` đi vào `@GetMapping` được không? **Có**, Spring MVC hỗ trợ HEAD ngầm; phản hồi không có body, nhưng handler vẫn có thể chạy công việc.
- `OPTIONS` trả header gì để báo những method được hỗ trợ? **`Allow`**. Với CORS preflight phải xét thêm những header `Access-Control-Allow-*` và cấu hình origin.
- URL có mapping GET nhưng gọi PUT: **405** và xem `Allow`. URL không có mapping/resource: thường **404**.
- GET chỉ `produces = application/json` nhưng request `Accept: application/xml`: **406**.
- POST `consumes = application/json` nhưng request `Content-Type: text/plain`: **415**.
- POST gửi `Content-Type: application/json` mà body `{bad json}`: thông thường **400**, vì media type đúng nhưng JSON không đọc được.
- `Accept` và `Content-Type` có thay thế cho nhau được không? **Không**; một bên mô tả dữ liệu client muốn **nhận**, bên kia mô tả body **đang gửi** (trong request).

## Tài liệu tham khảo

- Spring Framework — Mapping Requests (gồm HEAD, OPTIONS, `consumes`, `produces`): https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
- Spring Framework — Error Responses và `ResponseEntityExceptionHandler`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- Spring Framework — MVC Exception Resolvers: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/exceptionhandlers.html
- HTTP Semantics (RFC 9110): https://www.rfc-editor.org/rfc/rfc9110.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-routing-007">@RequestMapping trên interface và cách tổ chức hợp đồng controller</a>

<details>
<summary>Xem chi tiết</summary>

# @RequestMapping trên interface và cách tổ chức hợp đồng controller

## 1. Mục tiêu và phạm vi

Trong Spring MVC, một controller thường dùng `@RestController` hoặc `@Controller` và khai báo `@RequestMapping`, `@GetMapping`, `@PostMapping` trực tiếp trên lớp/phương thức. Khi nhiều nhóm cùng phát triển API, ta có thể **đặt mô tả tuyến HTTP trên một Java interface**, để interface thể hiện hợp đồng HTTP và lớp controller chịu trách nhiệm thực thi.

Sau bài này, bạn có thể:
- Hiểu Spring MVC tìm mapping đặt trên interface như thế nào và vai trò của lớp triển khai.
- Thiết kế hợp đồng nhất quán về đường dẫn, HTTP method, tham số, body, media type và kiểu kết quả.
- Triển khai, chạy và kiểm thử controller chỉ đặt mapping trên interface.
- Nhận diện lỗi thường gặp khi trộn annotation, có nhiều implementation hoặc dùng AOP proxy.

Phạm vi là **Spring MVC (Servlet stack)** với Java 17+ và ví dụ Spring Boot 3.x / Spring Framework 6.x. Nguyên tắc về controller interface cũng áp dụng cho các phiên bản hiện đại khác, nhưng cấu hình proxy và cơ chế quét controller cần được kiểm thử theo phiên bản đang dùng.

## 2. Bản chất của `@RequestMapping` trên interface

`@RequestMapping` mô tả quy tắc ánh xạ một HTTP request tới handler method: `path`, `method`, `params`, `headers`, `consumes`, `produces`. Đặt ở mức kiểu để định nghĩa tiền tố/điều kiện chung, và đặt ở mức phương thức để xác định endpoint cụ thể. `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping` là các annotation tổ hợp dựa trên `@RequestMapping`.

Java interface có thể khai báo các annotation trên kiểu, phương thức và tham số:

~~~java
@RequestMapping("/api/books")
public interface BookApi {
    @GetMapping("/{id}")
    BookResponse get(@PathVariable("id") long id);
}
~~~

Điểm mấu chốt:
1. **Java interface quy định chữ ký phương thức** (tên, tham số, kiểu trả về), còn **Spring MVC diễn giải annotation trong phân cấp kiểu/phương thức** để tạo mapping. Không thể suy luận hành vi này chỉ từ quy tắc kế thừa annotation của Java hoặc từ `@Inherited`.
2. **Interface không tự trở thành controller bean** chỉ vì có `@RequestMapping`. Cần một lớp triển khai được Spring đăng ký, thường bằng `@RestController`.
3. Mapping trên interface không phải endpoint thứ hai. Với **một controller triển khai**, Spring đăng ký các handler method trên bean đó; request được định tuyến tới implementation tương ứng.
4. Interface là **hợp đồng HTTP ở tầng web**. Nó khác service interface (hợp đồng nghiệp vụ) và khác cơ chế tự tạo HTTP client; chỉ khai báo `@RequestMapping` không tự tạo client gửi request.

Sơ đồ xử lý rút gọn:

~~~text
BookApi (interface: @RequestMapping, @GetMapping, @PathVariable)
              │ implements
              ▼
BookController (@RestController: logic thực thi)
              │ component scan + handler detection
              ▼
RequestMappingHandlerMapping đăng ký GET /api/books/{id}
              │ request khớp path + HTTP method + điều kiện
              ▼
RequestMappingHandlerAdapter giải tham số → gọi BookController.get(...)
              │
              ▼
HttpMessageConverter ghi BookResponse thành JSON
~~~

Ở đây `RequestMappingHandlerMapping` dò controller và xây dựng `RequestMappingInfo`; `RequestMappingHandlerAdapter` thực thi handler. Trả về JSON trong ví dụ là nhờ `@RestController` (bao gồm `@ResponseBody`) và bộ chuyển đổi HTTP message phù hợp, chứ không do interface tự tạo JSON.

## 3. Hợp đồng HTTP cần thống nhất những gì?

| Thành phần | Đặt ở đâu trong interface | Ý nghĩa |
| --- | --- | --- |
| Tiền tố tuyến | `@RequestMapping("/api/books")` trên interface | Gom các endpoint dưới cùng tài nguyên |
| HTTP method + đường dẫn | `@GetMapping("/{id}")` / `@PostMapping` trên method | Tách thao tác đọc/tạo và tránh mapping quá rộng |
| Biến đường dẫn | `@PathVariable("id")` trên tham số | Gắn `{id}` trong URL với tham số Java |
| Query/header | `@RequestParam`, `@RequestHeader` nếu cần | Ràng buộc dữ liệu request |
| Request JSON | `@RequestBody` và `consumes = "application/json"` | Xác định dữ liệu vào, hỗ trợ chọn media type |
| Response JSON | `produces = "application/json"` | Xác định dạng phản hồi mà endpoint tạo ra |
| Mã trạng thái | `ResponseEntity<T>` hoặc `@ResponseStatus` | Nêu rõ kết quả thành công và các tình huống lỗi |

Một mapping ở mức kiểu kết hợp với mapping ở mức phương thức (ví dụ `/api/books` + `/{id}` thành `/api/books/{id}`). Các thuộc tính không phải lúc nào cũng “cộng dồn” giống `path`: chẳng hạn `consumes` và `produces` ở method có thể **ghi đè** điều kiện tương ứng ở type. Vì vậy nên xác định rõ điều kiện thực tế bằng kiểm thử.

### Vì sao gom annotation vào một chỗ?

Nếu interface khai báo `@GetMapping` nhưng implementation tự thêm `@PostMapping` hoặc thay `@RequestMapping`, mỗi nơi lại mô tả một phần hợp đồng. Điều đó làm tăng rủi ro mapping không như mong đợi, phụ thuộc việc Spring hợp nhất annotation trong phân cấp kiểu và cách proxy trình bày type tại runtime. **Quy ước an toàn: khi đã chọn controller interface, đặt nhất quán các annotation mapping (và annotation hợp đồng liên quan như `@SessionAttributes` nếu sử dụng) trên interface; implementation chủ yếu giữ `@RestController` và logic xử lý.**

Cũng không đặt `@RequestMapping` và `@GetMapping` cùng trên **cùng một phương thức** như hai mapping độc lập. Chúng cùng thuộc họ mapping annotation; Spring cảnh báo và có thể chỉ sử dụng annotation đầu tiên.

## 4. Ví dụ đầy đủ: contract → controller → HTTP request

Giả sử đã tạo ứng dụng Spring Boot 3.x dùng Java 17+, có dependency `spring-boot-starter-web` và `spring-boot-starter-test` (cho phần kiểm thử). Ví dụ dưới đây dùng bộ nhớ tạm để tập trung vào request mapping, không phụ thuộc database.

Cấu trúc tệp:

~~~text
src/main/java/com/example/demo/
├── DemoApplication.java
├── api/
│   ├── BookApi.java
│   ├── BookResponse.java
│   └── CreateBookRequest.java
└── web/
    └── BookController.java
src/test/java/com/example/demo/web/
└── BookControllerMappingTest.java
~~~

### 4.1. Interface định nghĩa hợp đồng

`src/main/java/com/example/demo/api/BookApi.java`

~~~java
package com.example.demo.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping(path = "/api/books", produces = MediaType.APPLICATION_JSON_VALUE)
public interface BookApi {

    @GetMapping("/{id}")
    ResponseEntity<BookResponse> get(@PathVariable("id") long id);

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BookResponse> create(@RequestBody CreateBookRequest input);
}
~~~

- `@RequestMapping` ở interface xác định tiền tố `/api/books` và response JSON dùng chung.
- `@GetMapping("/{id}")` chỉ nhận GET, lấy `id` từ URL; đặt tên rõ trong `@PathVariable("id")` để không phụ thuộc việc Java compiler có giữ tên tham số hay không.
- `@PostMapping` chỉ nhận POST tại `/api/books`; `consumes` yêu cầu `Content-Type: application/json`.
- `ResponseEntity` cho phép implementation quyết định status và body (ví dụ `200`, `201`, `404`), không trộn logic trạng thái với chữ ký interface.

### 4.2. DTO cho request và response

`src/main/java/com/example/demo/api/BookResponse.java`

~~~java
package com.example.demo.api;

public record BookResponse(long id, String title) {}
~~~

`src/main/java/com/example/demo/api/CreateBookRequest.java`

~~~java
package com.example.demo.api;

public record CreateBookRequest(String title) {}
~~~

Java record phù hợp với DTO ngắn, dữ liệu bất biến. Spring Boot 3.x (với Jackson từ web starter) có thể chuyển JSON tương ứng sang/từ các record này.

### 4.3. Implementation: không lặp lại mapping

`src/main/java/com/example/demo/web/BookController.java`

~~~java
package com.example.demo.web;

import com.example.demo.api.BookApi;
import com.example.demo.api.BookResponse;
import com.example.demo.api.CreateBookRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookController implements BookApi {

    private final AtomicLong nextId = new AtomicLong();
    private final ConcurrentMap<Long, BookResponse> books = new ConcurrentHashMap<>();

    @Override
    public ResponseEntity<BookResponse> get(long id) {
        BookResponse book = books.get(id);
        return book == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(book);
    }

    @Override
    public ResponseEntity<BookResponse> create(CreateBookRequest input) {
        long id = nextId.incrementAndGet();
        BookResponse book = new BookResponse(id, input.title());
        books.put(id, book);
        return ResponseEntity.status(HttpStatus.CREATED).body(book);
    }
}
~~~

Lớp này có `@RestController` để được quét thành web bean và trả response body; các method `get` và `create` **không cần lặp `@GetMapping`, `@PostMapping`, `@RequestBody` hay `@PathVariable`**. Spring MVC nhận metadata request từ interface đã triển khai. `@Override` là kiểm tra của Java compiler, giúp phát hiện implementation sai chữ ký hợp đồng.

`ConcurrentHashMap` và `AtomicLong` chỉ tạo một ví dụ có thể gọi nhiều request, không đại diện cho cách lưu dữ liệu production. Bản demo không ràng buộc `title` phải khác `null` hoặc khác chuỗi rỗng; nếu cần validation, thêm Bean Validation (`@Valid`, constraint của Jakarta và `spring-boot-starter-validation`) cùng xử lý lỗi thích hợp.

### 4.4. Ứng dụng khởi chạy

`src/main/java/com/example/demo/DemoApplication.java`

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

`@SpringBootApplication` nằm ở package cha `com.example.demo`, nên component scan thấy `web.BookController`. Điều này tránh lỗi tạo interface đúng nhưng ứng dụng không phát hiện implementation.

Ví dụ các dependency cần có trong Gradle project Boot:

~~~groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
~~~

Phần dependency giả định dự án đã cấu hình Spring Boot plugin, repository Maven Central và Java toolchain. Chạy tại thư mục dự án có Gradle wrapper:

~~~bash
./gradlew bootRun
~~~

Thử API bằng bash/WSL/macOS terminal:

~~~bash
curl -i -X POST http://localhost:8080/api/books -H 'Content-Type: application/json' -H 'Accept: application/json' -d '{"title":"Spring MVC"}'
curl -i http://localhost:8080/api/books/1 -H 'Accept: application/json'
curl -i http://localhost:8080/api/books/999 -H 'Accept: application/json'
~~~

Nếu ứng dụng mới chạy với bộ nhớ trống và không có request tạo sách nào khác chen vào, response dự kiến lần lượt:

~~~text
POST /api/books          -> 201 Created, body {"id":1,"title":"Spring MVC"}
GET  /api/books/1        -> 200 OK,      body {"id":1,"title":"Spring MVC"}
GET  /api/books/999      -> 404 Not Found
~~~

Status và body trên là kết quả đáng tin của code mẫu; thứ tự header, format lỗi HTTP và log phụ thuộc cấu hình runtime. Gửi POST sai `Content-Type` (ví dụ `text/plain`) có thể nhận `415 Unsupported Media Type` vì điều kiện `consumes` không phù hợp; dùng phương thức không được khai báo cho cùng path thường nhận `405 Method Not Allowed`.

## 5. Kiểm chứng mapping bằng MockMvc

`src/test/java/com/example/demo/web/BookControllerMappingTest.java`

~~~java
package com.example.demo.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
class BookControllerMappingTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void interfaceMappingIsUsedByController() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spring MVC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Spring MVC"));

        mockMvc.perform(get("/api/books/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Spring MVC"));

        mockMvc.perform(get("/api/books/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/books/1"))
                .andExpect(status().isMethodNotAllowed());
    }
}
~~~

Chạy `./gradlew test` trong project đã có cấu hình test JUnit Platform thích hợp. Test không gắn annotation mapping tại implementation mà vẫn yêu cầu route hoạt động, vì thế nó xác nhận chính việc **Spring đọc interface metadata**. Với dự án có Spring Security hoặc global MVC configuration bổ sung, cần thiết lập test slice phù hợp (chẳng hạn cấu hình authentication hoặc mock) để kết quả không bị ảnh hưởng bởi filter ngoài ví dụ.

Thêm các ca kiểm tra theo hợp đồng thực tế: path variable không hợp lệ, thiếu body, `Content-Type` sai, `Accept` không hỗ trợ, query/header bắt buộc và status lỗi. Sau khi nâng cấp Spring, kiểm tra lại registration bằng test hoặc Spring Boot Actuator `/actuator/mappings` (chỉ khi đã bật actuator và cho phép endpoint đó).

## 6. Tổ chức package và trách nhiệm

Một cấu trúc thực tế với API nhỏ có thể là:

~~~text
com.example.catalog
├── api/                 # HTTP contract và DTO request/response
│   ├── BookApi.java
│   └── dto/...
├── web/                 # @RestController implements BookApi, chuyển đổi lỗi HTTP
│   └── BookController.java
├── application/         # Use case/service, logic nghiệp vụ
│   └── BookService.java
└── domain/              # Entity/value object, quy tắc nghiệp vụ
~~~

Hợp đồng `BookApi` nên mô tả **điều client HTTP quan sát được**: route, method, request/response shape, mã trạng thái và ràng buộc giao tiếp cần thiết. `BookController` nhận request, gọi service và chuyển đổi kết quả sang HTTP. `BookService` không cần biết `@GetMapping` hay Servlet API.

Nếu chia API contract sang module thư viện, module triển khai phải có dependency chứa interface và DTO; module web cần dependency Spring MVC để dùng annotation. Không nhất thiết phải tạo module riêng cho ứng dụng nhỏ: lợi ích của hợp đồng là rõ ranh giới và tái sử dụng, nhưng phải cân nhắc số lượng tệp/phụ thuộc phát sinh.

**Quy tắc thay đổi:** sửa `/api/books/{id}`, đổi `@PathVariable`, thêm required header hoặc đổi DTO đều là thay đổi hợp đồng HTTP, có khả năng ảnh hưởng client và test. Cần cập nhật tài liệu API, kiểm thử tích hợp và chính sách tương thích. Không coi việc đổi tên Java method (khi route và chữ ký tương thích) là tương đương đổi HTTP endpoint.

## 7. Trường hợp AOP proxy: lỗi dễ bỏ sót

AOP (ví dụ advice từ `@Transactional` hoặc custom aspect) có thể tạo proxy bao quanh controller. Có hai kiểu phổ biến:

| Kiểu proxy | Cách nhìn thấy controller | Điểm cần chú ý |
| --- | --- | --- |
| Class-based proxy (CGLIB) | Proxy dựa trên lớp controller | Thường phù hợp hơn với controller có interface và annotation ở interface; lưu ý hạn chế của lớp/method `final` |
| Interface-based proxy (JDK dynamic proxy) | Proxy phơi ra interface | Khả năng phát hiện controller và annotation chịu ảnh hưởng kiểu proxy và phiên bản Spring |

**Cảnh báo phiên bản:** kể từ Spring Framework 6.0, khi dùng *interface-based proxy*, Spring MVC **không còn nhận diện controller chỉ dựa vào `@RequestMapping` ở mức type trên interface**. Tài liệu Spring khuyến nghị dùng **class-based proxy** hoặc, nếu phải dùng interface proxy, interface phải có thêm `@Controller`. Đây là trường hợp cụ thể về *controller detection*, không đồng nghĩa mọi `@RequestMapping` trên interface ngừng hoạt động trong Spring 6.

Trong ứng dụng thông thường, hãy giữ `@RestController` ở lớp implementation và ưu tiên đặt transaction ở service. Nếu có AOP trực tiếp trên controller và endpoint bỗng `404` sau khi thêm advice, kiểm tra kiểu proxy, annotation controller, component scan và kết quả đăng ký mapping. Khi đổi cơ chế proxy, chạy lại MockMvc/smoke test thay vì chỉ dựa vào Java compiler.

## 8. Những lỗi thường gặp và hướng xử lý

| Triệu chứng | Nguyên nhân có thể | Cách kiểm tra và xử lý |
| --- | --- | --- |
| `404` cho mọi route dù có `BookApi` | Chưa có bean triển khai, controller nằm ngoài component scan, hoặc gặp proxy detection | Đảm bảo implementation có `@RestController`, quét đúng package, kiểm tra proxy/mappings |
| `404` riêng một URL | Sai tiền tố type-level, path variable hay context path | Đối chiếu `@RequestMapping` interface với `@GetMapping` và request URL |
| `405 Method Not Allowed` | URL có mapping nhưng HTTP method không phù hợp | Dùng `@GetMapping`/`@PostMapping` chính xác và gửi đúng method |
| `415 Unsupported Media Type` | `Content-Type` không thỏa `consumes` | Gửi JSON đúng content type hoặc sửa hợp đồng có chủ đích |
| `406 Not Acceptable` | `Accept` không tương thích `produces` | Đối chiếu media type, message converter và Accept |
| `400` khi bind tham số | Tên `@PathVariable` không khớp hoặc JSON sai shape/kiểu | Đặt tên tường minh, kiểm tra DTO và body |
| Startup báo ambiguous mapping | Hai controller cùng đăng ký path, method và điều kiện trùng nhau | Chỉ có một owner của mỗi endpoint hoặc thiết kế route khác nhau rõ ràng |
| Annotation không như dự tính | Trộn mapping giữa interface/implementation hoặc khai báo nhiều mapping annotation trên một phần tử | Dồn hợp đồng ở interface, loại bỏ annotation mapping trùng và viết test |
| Documentation/codegen thiếu endpoint | Công cụ sinh tài liệu không đọc annotation trong hierarchy giống Spring MVC | Kiểm tra cấu hình và phiên bản công cụ, so tài liệu với mapping runtime |

Không khắc phục `404` bằng cách sao chép mọi annotation xuống implementation trước khi kiểm tra component scan/proxy: làm vậy khiến hợp đồng nằm hai nơi và dễ phát sinh sai lệch.

### Hai implementation của cùng một interface có dùng chung route được không?

Về cú pháp Java, nhiều lớp có thể `implements BookApi`. Nhưng khi **cả hai đều là controller bean** với cùng mapping HTTP, Spring MVC có thể báo ambiguous mapping lúc khởi tạo vì nhiều handler cùng nhận một request. Nếu cần hai phiên bản hoặc hai kênh API, hãy thiết kế **đường dẫn/điều kiện mapping tách biệt ngay ở contract**, hoặc chỉ đăng ký một implementation theo profile/condition phù hợp; không giả định Spring tự chọn implementation giống dependency injection.

### Có nên dùng interface cho mọi controller?

Không bắt buộc. Với một controller đơn giản, đặt `@GetMapping` trực tiếp trên lớp có thể dễ đọc hơn. Interface phát huy giá trị khi cần hợp đồng chung giữa đội API và đội triển khai, kiểm soát thay đổi, sinh tài liệu/kiểm thử theo hợp đồng hoặc chia package/module. Tránh tạo interface chỉ để lặp lại nguyên xi từng method mà không có nhu cầu ranh giới rõ ràng.

### Khác gì `@HttpExchange`?

`@HttpExchange` cùng các annotation như `@GetExchange` có thể định nghĩa HTTP interface để Spring tạo proxy **client**; Spring MVC cũng hỗ trợ các contract này trong một số tình huống server. Tuy nhiên `@RequestMapping` là hợp đồng mapping linh hoạt phía server với tập điều kiện đầy đủ hơn. Không suy luận rằng `BookApi` có `@GetMapping` sẽ tự tạo HTTP client, và không đổi sang `@HttpExchange` chỉ vì muốn tách hợp đồng controller.

## 9. Checklist khi thiết kế hợp đồng controller

1. Xác định interface phục vụ HTTP boundary nào và ai sở hữu từng endpoint.
2. Đặt tiền tố `@RequestMapping` trên interface; dùng annotation theo HTTP method cụ thể cho từng operation.
3. Ghi tường minh tên biến `@PathVariable`/`@RequestParam` khi có giá trị học tập/vận hành; định nghĩa request/response DTO ổn định.
4. Đặt toàn bộ mapping metadata nhất quán trên interface; lớp `@RestController` triển khai logic mà không lặp mapping.
5. Kiểm tra controller bean được scan, chỉ có một mapping chủ sở hữu, và AOP không làm mất controller detection.
6. Viết MockMvc test cho route, verb, body/media type, response status và các tình huống lỗi quan trọng.
7. Khi thay hợp đồng, xem đó là thay đổi API cần review tương thích và tài liệu.

**Ghi nhớ:** interface nêu “HTTP request nào được nhận và contract ra sao”; implementation nêu “xử lý thế nào”. Spring MVC kết nối hai phần bằng việc tìm mapping annotation trong phân cấp controller. Ranh giới này hữu ích khi được giữ nhất quán và luôn có kiểm thử chạy qua HTTP dispatcher thực tế.

## 10. Tài liệu tham khảo

- [Spring Framework — Mapping Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html): `@RequestMapping`, tổ hợp mapping, điều kiện request, `@HttpExchange`.
- [Spring Framework — Controller Declaration / AOP Proxies](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann.html): đăng ký controller, lưu ý proxy từ Spring 6.0.
- [Spring Framework — Handler Methods](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods.html): chữ ký handler, parameter/return handling.
- [`@RequestMapping` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/bind/annotation/RequestMapping.html): khuyến nghị nhất quán mapping annotation khi dùng controller interfaces.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="request-routing-008">Kiểm soát dấu gạch chéo cuối đường dẫn và khác biệt cấu hình theo phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm soát dấu gạch chéo cuối đường dẫn trong Spring MVC và khác biệt giữa các phiên bản

## 1. Vấn đề cần hiểu

Dấu gạch chéo cuối đường dẫn (*trailing slash*) là ký tự `/` ở cuối phần **path** của URL, chẳng hạn:

- `GET /api/users`: không có dấu `/` cuối.
- `GET /api/users/`: có dấu `/` cuối.
- `GET /api/users?page=1` và `GET /api/users/?page=1`: vẫn khác nhau ở **path**; query string `?page=1` không quyết định trailing slash.
- Đường dẫn gốc `/` là trường hợp riêng, không được tùy tiện rút thành chuỗi rỗng.

Với Spring MVC, câu hỏi là một phương thức controller khai báo `@GetMapping("/api/users")` có nhận cả hai URL đầu tiên không. Câu trả lời **phụ thuộc vào phiên bản Spring Framework, cấu hình path matching, các mapping được khai báo và bộ lọc hoặc reverse proxy đặt trước ứng dụng**. Không nên xem hai URL này đương nhiên là tương đương.

Phạm vi bài học: **Spring MVC trên Servlet stack**, gồm ứng dụng Spring Framework thuần và ứng dụng dùng Spring Boot. Spring WebFlux, Spring Cloud Gateway, cấu hình proxy hoặc Servlet container có những cơ chế riêng; không tự suy diễn rằng chúng sử dụng cùng API với MVC.

## 2. Cơ chế định tuyến và nguyên nhân khác biệt

Khi một HTTP request đi vào ứng dụng MVC, proxy và các Servlet filter (nếu có) xử lý trước, sau đó `DispatcherServlet` tìm handler phù hợp, thường thông qua `RequestMappingHandlerMapping`. Spring so path đã nhận với các mẫu trong `@RequestMapping`, `@GetMapping`, `@PostMapping`... và đồng thời xét HTTP method, `params`, `headers`, `consumes` và `produces` khi được khai báo.

Xét controller:

```java
package demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping("/api/users")
    public String listUsers() {
        return "users";
    }
}
```

Đây là một lớp controller trong ứng dụng đã có Spring MVC và bộ khởi động web phù hợp, không phải tệp Java độc lập chạy bằng `java UserController`.

- `GET /api/users` khớp mapping và thông thường trả HTTP 200, body `users`.
- Với mặc định từ **Spring Framework 6.0 trở đi**, `GET /api/users/` **không khớp** mapping trên. Nếu không có mapping/filter/proxy nào khác xử lý, kết quả thường là **404 Not Found**.
- Nếu có Spring Security, reverse proxy, filter chuyển hướng hoặc route khác, trạng thái HTTP quan sát được có thể khác (ví dụ 301/308, 401/403). Vì vậy phải kiểm tra toàn bộ đường đi của request trước khi kết luận.

`PathPatternParser` phân tích mẫu URL thành `PathPattern` và so với path của request. `AntPathMatcher` là chiến lược so khớp chuỗi truyền thống. **Lựa chọn parser và quy tắc chấp nhận slash cuối là hai vấn đề khác nhau**; chỉ đổi sang `AntPathMatcher` không phải giải pháp đáng tin để khôi phục hành vi cũ.

## 3. Bảng mốc phiên bản cần nhớ

| Nền tảng | Mặc định đối với `@GetMapping("/api/users")` khi gọi `/api/users/` | Ghi chú |
| --- | --- | --- |
| Spring Framework 5.x, gồm 5.3 (thường đi với Boot 2.x) | Thường **khớp** nhờ optional trailing-slash match mặc định bật | Kiểm tra cấu hình tùy chỉnh; Boot 2.6 chuyển chiến lược MVC mặc định sang `PathPatternParser` nhưng điều này không đồng nghĩa tắt trailing-slash match. |
| Spring Framework 6.0–6.1 (thường Boot 3.0–3.3) | Mặc định **không khớp** | Từ 6.0, mặc định trailing-slash match đổi từ `true` sang `false` và các API bật lại tính năng được đánh dấu deprecated. |
| Spring Framework 6.2 (thường Boot 3.4–3.5) | Mặc định **không khớp** | Có `UrlHandlerFilter` (giới thiệu từ Framework 6.2) để redirect hoặc wrap request một cách tường minh. API cũ vẫn deprecated. |
| Spring Framework 7.0+ (dòng Boot 4.x) | Mặc định **không khớp** | Hỗ trợ optional trailing-slash match trong path parser **đã bị loại bỏ**; dùng mapping tường minh, redirect/rewrite hoặc `UrlHandlerFilter`. |

Đây là các **mốc phiên bản Framework**, còn Spring Boot quản lý phiên bản Framework thông qua dependency management. Khi cần xác nhận chính xác một dự án, kiểm tra phiên bản Framework thực tế trong dependency tree, không suy từ mã nguồn hoặc một thuộc tính Boot đơn lẻ.

Một số khác biệt dễ nhầm:

1. Spring Framework 5.3 giới thiệu khả năng chọn `PathPatternParser`, trong khi trước đó MVC thường dựa vào `AntPathMatcher`.
2. Spring Boot 2.6 đổi mặc định chiến lược khớp URL sang `PathPatternParser`. Điều này khác với mốc **Spring Framework 6.0 tắt trailing slash match theo mặc định**.
3. `PathMatchConfigurer#setUseTrailingSlashMatch(true)` và `PathPatternParser#setMatchOptionalTrailingSeparator(true)` bị deprecated từ Framework 6.0; **không đưa các API đó vào cấu hình mới** vì đường nâng cấp 7.0 không còn hỗ trợ.
4. Không có một thuộc tính `application.yml` phổ quát tên `spring.mvc.trailing-slash` bảo đảm bật lại cơ chế này ở mọi phiên bản. `spring.mvc.pathmatch.matching-strategy` điều khiển **chiến lược matching** trong các bản Boot hỗ trợ thuộc tính đó, không phải công tắc chuẩn để hợp nhất hai URL.

## 4. Chọn chính sách URL theo mục tiêu của API

### Phương án A — Chỉ có một URL chuẩn (khuyến nghị mặc định)

Thiết kế và công bố `/api/users` là endpoint chuẩn. Giữ một mapping:

```java
@GetMapping("/api/users")
public String listUsers() {
    return "users";
}
```

Client phải dùng URL đúng như tài liệu. Dấu slash cuối không hợp lệ sẽ không khớp mapping theo mặc định của Framework 6+. Ưu điểm là có một đường dẫn thống nhất trong OpenAPI, cache, log, chữ ký request và các rule phân quyền; đánh đổi là các client gửi `/api/users/` cần được cập nhật hoặc nhận lỗi.

**Không được tự ý loại bỏ slash ở cuối tất cả URL** nếu ứng dụng có path đặc thù, URL đã ký, reverse proxy đang rewrite, hoặc hợp đồng API yêu cầu dạng có slash.

### Phương án B — Cho phép cả hai dạng tại một endpoint cụ thể

Khai báo chính xác hai mẫu cần hỗ trợ:

```java
package demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping({"/api/users", "/api/users/"})
    public String listUsers() {
        return "users";
    }
}
```

Với controller trên, `GET /api/users` **và** `GET /api/users/` cùng trả `200 OK` với body `users` trong một ứng dụng không có filter/security chặn chúng. Khác với redirect, hai yêu cầu được xử lý trực tiếp, không có round trip thứ hai.

Dùng phương án này khi chỉ một số endpoint phải duy trì tương thích. Tránh khai báo trùng đường dẫn trong nhiều controller gây ambiguity, tránh nhân đôi toàn bộ API nếu không có yêu cầu rõ ràng. Nếu endpoint nhận nhiều HTTP method, khai báo biến thể cho đúng method tương ứng, thay vì thêm `@RequestMapping` không giới hạn method.

### Phương án C — Chuyển hướng sang URL chuẩn tại proxy hoặc filter

Nếu mục tiêu là **canonical URL**, hãy chuyển từ `/api/users/` sang `/api/users`. Ví dụ một reverse proxy được phép xử lý chính sách slash cuối có thể trả 308 và header `Location: /api/users`. Sau đó client gửi request mới đến URL chuẩn.

- **308 Permanent Redirect**: chuyển hướng lâu dài, giữ HTTP method và request body theo chuẩn; một số client/cache có thể ghi nhớ redirect.
- **307 Temporary Redirect**: chuyển hướng tạm thời, vẫn bảo toàn method và body.
- **301/302**: có lịch sử hành vi client thay đổi method (đặc biệt POST thành GET); không mặc định dùng cho API ghi dữ liệu.
- **Redirect khác với rewrite/wrap**: redirect phát phản hồi 3xx để client gọi lại; rewrite/wrap làm ứng dụng tiếp tục xử lý ngay trong cùng request. Những khác biệt này ảnh hưởng quan sát HTTP, logging, cache và các client không tự theo redirect.

Chọn 308 khi chính sách URL đã ổn định và client hỗ trợ; chọn 307 trong giai đoạn chuyển đổi nếu cần tránh hiệu ứng cache dài hạn. Kiểm tra kỹ các request POST/PUT/PATCH, payload lớn, xác thực và URL ký số.

## 5. `UrlHandlerFilter` từ Spring Framework 6.2

Với Spring MVC Servlet trên Framework **6.2+**, `org.springframework.web.filter.UrlHandlerFilter` cho phép cấu hình từng nhóm path. Ví dụ dưới đây dùng Spring Boot với Spring MVC, yêu cầu phụ thuộc Spring Framework 6.2+:

```java
package demo.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.UrlHandlerFilter;

@Configuration
public class TrailingSlashConfig {

    @Bean
    FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
        UrlHandlerFilter filter = UrlHandlerFilter
                .trailingSlashHandler("/api/**")
                .redirect(HttpStatus.PERMANENT_REDIRECT)
                .build();

        FilterRegistrationBean<UrlHandlerFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
```

Với mapping `@GetMapping("/api/users")` ở phần 2, trong điều kiện chuỗi filter đúng thứ tự:

```text
GET /api/users   -> 200 OK, body: users
GET /api/users/  -> 308 Permanent Redirect
                    Location: /api/users
GET /api/users   -> 200 OK, body: users  (nếu client theo redirect)
```

Đoạn trên minh họa **cấu hình filter và trạng thái mong đợi**, không phải stdout từ một chương trình Java độc lập. Mốc `Ordered.HIGHEST_PRECEDENCE + 20` chỉ là ví dụ về độ ưu tiên; cần xác minh thứ tự filter thực tế của ứng dụng. Tài liệu Spring yêu cầu `UrlHandlerFilter` chạy **sau `ForwardedHeaderFilter`, trước `ServletRequestPathFilter` (nếu có) và trước các security filter**. Nếu hệ thống có reverse proxy hoặc Spring Security, phải xác nhận rằng thứ tự đó thật sự được đáp ứng; không xem một hằng số order là bảo đảm cho mọi cấu hình.

Nếu cần giữ nguyên một request và chuyển path nội bộ, API Servlet dùng `wrapRequest()`:

```java
UrlHandlerFilter filter = UrlHandlerFilter
        .trailingSlashHandler("/api/**")
        .wrapRequest()
        .build();
```

Khi đó client gửi `/api/users/` có thể nhận trực tiếp phản hồi từ handler của `/api/users`, không có 3xx. Đổi lại, ứng dụng cần kiểm tra xem log, filter khác và security matcher có cùng nhìn thấy path đã chuẩn hóa không. **Không đăng ký đồng thời nhiều filter có logic slash chồng chéo**, vì có thể sinh vòng redirect hoặc hành vi khó đoán.

Lưu ý giới hạn của filter:

- Quy tắc `"/api/**"` chỉ tác động đến nhóm path đã cấu hình; không có chủ ý đổi mọi URL của hệ thống.
- Root path `/` được loại trừ khỏi xử lý trailing slash.
- Khi normalization áp dụng, một type-level `@RequestMapping("/")` có thể tạo ra slash không mong muốn; với controller thực sự ở root, tài liệu Spring hướng dẫn dùng `@RequestMapping` không khai báo path khi phù hợp.
- `UrlHandlerFilter` chỉ có từ 6.2; ở Framework 6.0–6.1 chọn mapping tường minh hoặc redirect/rewrite tại hạ tầng hay filter tự viết được kiểm soát. Không sao chép class này vào dự án 6.0 rồi kỳ vọng biên dịch.

## 6. Cấu hình cũ trong quá trình nâng cấp: đọc được nhưng không xây mới

Trong một số dự án Spring Framework 6.x, có thể gặp cấu hình di trú:

```java
// CHỈ MINH HỌA CẤU HÌNH CŨ CỦA FRAMEWORK 6.x.
// Deprecated từ 6.0; không sử dụng cho triển khai mới hoặc Framework 7.
@Override
public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.setUseTrailingSlashMatch(true);
}
```

Hoặc mã trực tiếp với `PathPatternParser#setMatchOptionalTrailingSeparator(true)`. Cả hai cách dựa trên cơ chế matching được đánh dấu deprecated từ 6.0 và bị loại bỏ khi chuyển lên 7.0. Nếu vẫn phải duy trì dự án 6.x, cần xác định chính xác parser/handler mapping đang hoạt động và thử cả hai biến thể URL; không dùng mẹo này như chiến lược nâng cấp dài hạn.

**Không nhầm với** `spring.mvc.pathmatch.matching-strategy=ant-path-matcher` trong các bản Boot cũ hỗ trợ: cài đặt đó chọn engine xử lý mẫu, không tự cho phép trailing slash trên Framework 6+; ở Framework 7 các API matching kiểu chuỗi cũng nằm trong xu hướng loại bỏ/deprecated.

## 7. Bảo mật và nhất quán giữa MVC với Spring Security

Một lý do Spring bỏ mặc định nới lỏng là tránh **bất đồng về diễn giải path** giữa tầng phân quyền và tầng định tuyến. Tưởng tượng một rule bảo vệ `/resources` khác quyền với `/resources/**`. Nếu security matcher đánh giá `/resources/` theo một quy tắc, còn MVC cho phép nó đi vào handler của `/resources`, người dùng có thể nhận quyền khác dự kiến. Đây là ví dụ rủi ro thiết kế, **không khẳng định mọi cấu hình Spring Security đều bị bypass**.

Nguyên tắc triển khai:

1. Áp dụng normalization **trước** bước phân quyền nếu chọn filter/rewrite, để Security và MVC nhìn cùng một path; kiểm tra bằng integration test thật.
2. Rà soát cả path có và không có slash ở API công khai, endpoint admin, health/actuator và static resources; không giả định chúng cùng chính sách.
3. Đảm bảo proxy, gateway, CDN, Spring Security và controller có chung quy ước canonical URL; xét cả chữ hoa/chữ thường, mã hóa `%2F` và slash lặp khi phù hợp.
4. Với redirect, không đánh rơi query string, phương thức hoặc body; kiểm tra `Location` và host/scheme khi ứng dụng đặt sau trusted proxy. Không tin `X-Forwarded-*` từ nguồn không tin cậy.
5. Đối với request không có handler, các security filter có thể trả 401/403 trước khi MVC có cơ hội trả 404; chỉ nhìn status code chưa đủ để xác định quy tắc path matching.

## 8. Kiểm thử hồi quy

Để kiểm tra **chính hành vi path mapping mặc định** độc lập với web server, reverse proxy và filter tùy chỉnh, dùng `MockMvc` standalone. Ví dụ JUnit Jupiter với `spring-test` và các phụ thuộc kiểm thử Spring tương thích phiên bản:

```java
package demo.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    @Test
    void spring6AndLaterDefaultRequiresExactPath() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new UserController())
                .build();

        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(content().string("users"));

        mvc.perform(get("/api/users/"))
                .andExpect(status().isNotFound());
    }
}
```

Bài test này dùng **controller một mapping** ở phần 2, và mong đợi mặc định Framework **6.0+**. Trên dự án 5.x (mặc định cũ), test thứ hai có thể nhận 200; khi dùng controller khai báo **hai mapping tường minh** ở phần 4, phải đổi kỳ vọng thứ hai thành 200. Vì là standalone, bài test này **không chứng minh thứ tự servlet filter, Spring Security, proxy hay `UrlHandlerFilter` ngoài đời thực**.

Sau khi thêm filter hoặc cấu hình Security, cần integration test với web context phù hợp, có đủ filter chain. Ma trận tối thiểu:

| Request | Mặc định 6+ với một mapping | Hai mapping tường minh | Filter redirect 308 |
| --- | --- | --- | --- |
| `GET /api/users` | 200 | 200 | 200 |
| `GET /api/users/` | 404 nếu không có handler khác | 200 | 308, `Location: /api/users` |
| `GET /api/users/?page=1` | 404 nếu không có handler khác | 200 | 308, kiểm tra query được bảo toàn |
| `POST /api/users/` | Tùy mapping POST thực tế (có thể 404/405) | Tùy mapping POST thực tế | Nếu áp dụng quy tắc, kiểm tra 307/308 bảo toàn method/body |
| `GET /` | Tùy root mapping | Tùy root mapping | Không bị filter rút slash của root |

Các giá trị 200 và 404 ở bảng là **tình huống minh họa** cho controller GET trả `users`, không phải cam kết với mọi ứng dụng. Kiểm tra redirect với **client không tự theo redirect** để thấy phản hồi 308 ban đầu; sau đó có thể kiểm tra riêng client có theo redirect.

Lệnh quan sát nhanh khi ứng dụng chạy ở cổng 8080:

```bash
curl -i --max-redirs 0 http://localhost:8080/api/users
curl -i --max-redirs 0 http://localhost:8080/api/users/
curl -i --max-redirs 0 'http://localhost:8080/api/users/?page=1'
```

Đầu ra cần xem là dòng HTTP status và header `Location` (nếu có), không phải chỉ nội dung body. Không dùng `curl -L` ở bước đầu vì nó sẽ tự theo redirect và làm mất dấu vết của phản hồi 3xx.

## 9. Chẩn đoán khi sau nâng cấp gặp 404 hoặc redirect lạ

Thực hiện theo trình tự sau:

1. **Xác định phiên bản thật**: xem Spring Framework, Spring Boot và `spring-webmvc` trong dependency tree; tìm mốc 5.3 → 6.x hoặc 6.x → 7.x.
2. **Xem khai báo handler**: kiểm tra `@RequestMapping` ở class và method, prefix của servlet, path của context và tên URL thực tế; lưu ý `@RequestMapping("/")` tại class level.
3. **Kiểm tra request thực tế**: ghi HTTP method, raw path, query string và header `Location`; so `/api/users` với `/api/users/` trước và sau reverse proxy.
4. **Rà soát cấu hình matching**: xác minh `PathPatternParser`, cấu hình `WebMvcConfigurer`, những API deprecated còn sót, và rule rewrite từ proxy/filter.
5. **Quan sát filter/security**: thứ tự `ForwardedHeaderFilter` → normalization → Spring Security → MVC phải được kiểm chứng, tránh chuyển hướng vòng lặp, sai host/scheme hoặc security rule lệch path.
6. **Khóa hành vi bằng test**: kiểm thử cả hai đường dẫn, tất cả HTTP method được hỗ trợ, query string, body, redirect, auth và client thực tế trước khi phát hành.

Không đánh đồng: **404** thường là không tìm được handler; **405** là đường dẫn có handler nhưng HTTP method không phù hợp; **308** là chuyển hướng; **401/403** thường đến từ lớp bảo mật. Các thành phần và cấu hình có thể thay đổi đường đi nên cần dùng log/filter chain để xác minh nguồn trả mã.

## 10. Bài tập tự kiểm tra

1. Dự án nâng từ Boot 2.7 lên Boot 3.2; `GET /catalog` hoạt động nhưng `GET /catalog/` trả 404. Hãy giải thích mốc thay đổi từ Framework 5.3 sang 6.x, rồi chọn giữa chuẩn hóa URL và khai báo mapping kép.
2. API `POST /orders/` phải được chuyển sang `POST /orders` mà giữ payload JSON. Hãy lý giải vì sao 307/308 phù hợp hơn việc mặc định dùng 301/302; kiểm tra client có hỗ trợ redirect không.
3. Với Framework 6.2+, hãy áp dụng `UrlHandlerFilter` cho `/api/**` và viết integration test chứng minh `/api/users/` redirect 308, query string giữ nguyên, filter chạy trước Security.
4. Trước khi nâng lên Framework 7, tìm và loại bỏ việc phụ thuộc vào `setUseTrailingSlashMatch` và `setMatchOptionalTrailingSeparator`, thay bằng chiến lược URL tường minh.

## Tài liệu chính thức

- [Spring Framework 6.x: hướng dẫn nâng cấp — thay đổi trailing slash match](https://github.com/spring-projects/spring-framework/wiki/Upgrading-to-Spring-Framework-6.x).
- [Spring Framework 7.x: Servlet filters, mục URL Handler](https://docs.spring.io/spring-framework/reference/web/webmvc/filters.html).
- [Javadoc `UrlHandlerFilter` trong Framework 6.2](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/web/filter/UrlHandlerFilter.html).
- [Javadoc `PathPatternParser` trong Framework 6.2](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/web/util/pattern/PathPatternParser.html).
- [Spring Framework 7.0 Release Notes — các API path matching đã bị loại bỏ](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes).
- [Spring Boot 2.7: cấu hình MVC path matching](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/web.html).


</details>

- [Quay lại đầu trang](#back-to-top)
