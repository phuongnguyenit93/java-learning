<a id="back-to-top"></a>

# Phản hồi REST

## Menu
- [@ResponseBody và khác biệt với tên view trả về từ @Controller](#rest-responses-001)
- [ResponseEntity: điều khiển status, header và body](#rest-responses-002)
- [@ResponseStatus, phản hồi không có nội dung và trường hợp body null](#rest-responses-003)
- [Tạo tài nguyên với 201 Created và header Location](#rest-responses-004)
- [Thiết kế DTO phản hồi và tách mô hình HTTP khỏi đối tượng lưu trữ](#rest-responses-005)
- [Phân trang, sắp xếp, bộ lọc và hợp đồng danh sách kết quả](#rest-responses-006)
- [Thiết kế cập nhật PUT/PATCH và quy tắc xử lý yêu cầu lặp lại](#rest-responses-007)
- [Giữ hợp đồng API ổn định khi thay đổi cấu trúc dữ liệu](#rest-responses-008)

## <a id="rest-responses-001">@ResponseBody và khác biệt với tên view trả về từ @Controller</a>

<details>
<summary>Xem chi tiết</summary>

# @ResponseBody và khác biệt với tên view trả về từ @Controller

## 1. Mục tiêu: cùng một String, hai cách hiểu hoàn toàn khác nhau

Giả sử controller có hai method đều trả về `"hello"`. Trong Spring MVC (Servlet stack), ý nghĩa HTTP khác nhau tùy cơ chế xử lý giá trị trả về:

~~~java
@Controller
class PageController {
    @GetMapping("/page")
    public String page() {
        return "hello";
    }
}
~~~

Ở đây `"hello"` là **tên view**. Spring chọn một `ViewResolver` thích hợp, tìm template/view tương ứng và **render kết quả** vào HTTP response. Nếu không tìm được view, request có thể thất bại tùy cấu hình view technology. Giá trị return **không tự được gửi nguyên văn** dưới dạng response body.

~~~java
@Controller
class TextController {
    @GetMapping("/text")
    @ResponseBody
    public String text() {
        return "hello";
    }
}
~~~

Ở đây `"hello"` là **nội dung response body**, không qua cơ chế tìm view. `StringHttpMessageConverter` thường viết nó thành text theo media type được chọn.

Và khi đặt `@RestController` lên lớp, ý nghĩa của `@ResponseBody` được áp dụng mặc định cho các handler:

~~~java
@RestController
class ApiController {
    @GetMapping("/api/hello")
    public String hello() {
        return "hello";
    }
}
~~~

Cả ba fragment chỉ minh họa annotation và ý nghĩa return value; phần **ví dụ ứng dụng hoàn chỉnh** bên dưới có đầy đủ file/import/test.

**Bài học cần đạt:**
1. Phân biệt `@Controller`, `@ResponseBody` và `@RestController`.
2. Hiểu Spring MVC quyết định khi nào dùng `ViewResolver` và khi nào dùng `HttpMessageConverter`.
3. Biết `String`, DTO, `ResponseEntity`, `ModelAndView`, `redirect:` được xử lý thế nào.
4. Viết controller và MockMvc test xác nhận body, media type, model/view, redirect.
5. Chẩn đoán các lỗi trả nhầm tên view, render nhầm text, sai Content-Type hoặc bị HTTP 406.

Ví dụ hướng đến Java 17+ và Spring Boot 3.x / Spring Framework 6.x; các nguyên tắc cốt lõi vẫn có giá trị ở Spring MVC hiện đại. Không lấy ví dụ WebFlux để suy ra chính xác tên implementation ở Servlet stack.

## 2. Hai nhánh xử lý phản hồi trong Spring MVC

### 2.1. Khởi đầu giống nhau, kết thúc khác nhau

Khi nhận HTTP request, Spring MVC:
1. `DispatcherServlet` phối hợp `HandlerMapping` xác định handler.
2. `HandlerAdapter` phân giải tham số và gọi controller method.
3. Một `HandlerMethodReturnValueHandler` phù hợp với **kiểu giá trị trả về và metadata annotation** xử lý kết quả.
4. Nếu là model/view, Spring resolve và render view; nếu là body, converter serialize/write dữ liệu.

~~~text
HTTP GET /...
     │
     ▼
DispatcherServlet → HandlerMapping → Controller method
                                         │
                          return "hello" │
                                         ▼
                        HandlerMethodReturnValueHandler
                                  │                │
                     @Controller, String       @ResponseBody
                     (view name)               hoặc @RestController
                                  │                │
                                  ▼                ▼
                            ViewResolver      HttpMessageConverter
                                  │                │
                                  ▼                ▼
                            View.render()     write HTTP response body
                                  │                │
                                  └────────┬───────┘
                                           ▼
                                     HTTP response
~~~

Đây là sơ đồ giản lược để hiểu hai nhánh. Trong runtime, các resolver/handler cụ thể, filter, interceptor, exception handler và view technology ảnh hưởng kết quả; Spring **không tự đoán “String có vẻ là HTML” để chọn view**.

### 2.2. `@Controller` nói gì?

`@Controller` là stereotype đánh dấu thành phần MVC được component scan phát hiện; nó **không tuyên bố rằng mọi return value là JSON**.

Ví dụ:

~~~java
@Controller
class ShopPageController {
    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("title", "Products");
        return "products";
    }
}
~~~

`"products"` được xem là tên view, `Model` chứa dữ liệu đưa vào quá trình render. Với Thymeleaf và cấu hình chuẩn, template có thể nằm ở `classpath:/templates/products.html`; với JSP hoặc view resolver khác, đường dẫn/tên file có thể thay đổi.

Không phải mọi method trong `@Controller` đều bắt buộc trả view: nếu trả `ResponseEntity<?>`, gắn `@ResponseBody`, hoặc tự ghi `HttpServletResponse`, Spring có thể xử lý response trực tiếp.

### 2.3. `@ResponseBody` thay đổi điều gì?

`@ResponseBody` bảo Spring xử lý **return value của method như dữ liệu gửi vào response body**, thông qua `HttpMessageConverter`. Có thể đặt annotation lên:
- **Một method** trong `@Controller`, giúp controller có cả trang HTML và endpoint API.
- **Lớp `@Controller`**, tác động lên các handler method tương ứng.

Annotation này **không có nghĩa chắc chắn là JSON**. Dạng biểu diễn phụ thuộc **kiểu Java**, các converter được cấu hình, điều kiện `produces`, header `Accept` và negotiation. `String` thường là `text/plain`, DTO thường là JSON nếu Jackson converter có sẵn và content negotiation phù hợp, `byte[]` có converter khác.

`@ResponseBody` cũng không tự tạo đối tượng response. Giá trị `null`/`void` có xử lý riêng theo signature và có thể kết thúc với body rỗng; không dùng `return null` để biểu thị một status code cụ thể.

### 2.4. `@RestController` thực chất là gì?

`@RestController` là annotation tổng hợp mang ý nghĩa của **`@Controller` + `@ResponseBody` ở cấp lớp**. Nó phù hợp khi handler chủ yếu là REST API, vì không phải lặp `@ResponseBody` trên từng method:

~~~java
@RestController
@RequestMapping("/api")
class ProductApi {
    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
~~~

Response body là `ok`; `"ok"` không phải tên template. Vì thế **không nên đưa các method trả tên view vào một `@RestController`** rồi mong Spring tự chuyển sang HTML. Nếu muốn trang HTML, dùng `@Controller` phù hợp hoặc trả hẳn nội dung HTML với media type tương ứng (một cách thiết kế khác).

## 3. Bảng so sánh các kiểu return phổ biến

| Controller và return value | Cách Spring MVC diễn giải | Phần trả cho client |
| --- | --- | --- |
| `@Controller` + `String "home"` | Tên view `home` | HTML hoặc kết quả do view render |
| `@Controller` + `String "redirect:/login"` | Tên view đặc biệt để redirect | HTTP redirect, thường 302 |
| `@Controller` + `String "forward:/internal"` | Tên view đặc biệt để forward | Servlet forward theo cấu hình |
| `@Controller` + `@ResponseBody String "home"` | Nội dung body | Chuỗi `home`, không tìm template |
| `@Controller` + `@ResponseBody ProductDto` | Body qua converter | Thường JSON nếu có Jackson |
| `@RestController` + `String "redirect:/login"` | Chuỗi body | Literal `redirect:/login`, không phải redirect |
| `@RestController` + `ProductDto` | Body qua converter | Thường JSON |
| `@Controller` + `ResponseEntity<ProductDto>` | Phản hồi HTTP đầy đủ, không cần `@ResponseBody` | Status, headers và body được chuyển đổi |
| `@Controller` + `ModelAndView` | View + model (và tùy chọn status) | Render view |
| `@Controller` + `Model` hoặc `Map` | Cập nhật model, tìm tên view ngầm nếu cần | Render view sau khi resolve |

Bảng áp dụng trường hợp handler bình thường, chưa xét custom return handlers. Điểm hay bị bỏ qua: `ResponseEntity<T>` là **return type đặc biệt**; ngay cả khi class chỉ có `@Controller`, nó vẫn có thể ghi response body mà không cần `@ResponseBody`.

### 3.1. Tại sao `String` đặc biệt?

Spring MVC có handler giải thích một `String`/`CharSequence` là tên logical view khi không có `@ResponseBody` và không được return handler khác xử lý trước. `@ResponseBody` làm route qua handler viết body: `StringHttpMessageConverter` ghi ký tự. Vậy **cùng kiểu Java `String` không đủ để suy luận hành vi HTTP**.

Ví dụ dễ sai:

~~~java
@Controller
class MistakeController {
    @GetMapping("/api/status")
    public String status() {
        return "READY";
    }
}
~~~

Dù developer muốn trả `READY` cho API, Spring sẽ đi tìm **view `READY`**. Nếu không có template/view tương ứng, client có thể nhận lỗi server hoặc cơ chế fallback riêng của ứng dụng, thay vì text `READY`. Cách sửa là thêm `@ResponseBody` hoặc dùng `@RestController` nếu toàn bộ class là API.

### 3.2. `ResponseEntity` kiểm soát nhiều hơn body

~~~java
@GetMapping("/api/orders/{id}")
public ResponseEntity<OrderDto> get(@PathVariable long id) {
    OrderDto order = lookup(id);
    if (order == null) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(order);
}
~~~

Đây là **fragment** vì `OrderDto` và `lookup` phụ thuộc ứng dụng. Trả `ResponseEntity.ok(order)` thiết lập status 200; `notFound().build()` trả 404 không body. Không cần thêm `@ResponseBody` để Spring xử lý `ResponseEntity`. Dùng `ResponseEntity.created(uri).body(dto)` khi muốn 201 kèm header `Location`.

`@ResponseStatus` có thể chỉ định status cho một handler, nhưng không hỗ trợ linh hoạt theo từng nhánh logic như `ResponseEntity`. Đừng đồng nhất **có body** với **HTTP 200**: vẫn có thể trả 201/204/404 tùy kiểu xử lý, và với 204 thì body phải được bỏ.

## 4. `HttpMessageConverter`: tại sao object lại thành JSON?

Khi Spring chọn nhánh response body, `RequestResponseBodyMethodProcessor` hoặc return handler tương ứng phối hợp với `HttpMessageConverter` để **serialize** value.

Ví dụ mapping:

~~~java
@GetMapping(path = "/api/products/7", produces = "application/json")
@ResponseBody
public ProductDto product() {
    return new ProductDto(7L, "Spring MVC");
}
~~~

`ProductDto` trong fragment là record/POJO được định nghĩa ở ứng dụng. Với `spring-boot-starter-web` và Jackson mặc định, body thường là:

~~~json
{"id":7,"name":"Spring MVC"}
~~~

Với cùng nội dung nếu viết:

~~~java
@GetMapping(path = "/api/products/title", produces = "text/plain")
@ResponseBody
public String title() {
    return "Spring MVC";
}
~~~

Body là:

~~~text
Spring MVC
~~~

**Không có cú pháp dấu ngoặc kép JSON trong response text trên**. Chuỗi `String` không tự được JSON encode chỉ vì method đặt trong `@RestController`. Cần phân biệt:
- `StringHttpMessageConverter`: thường xử lý `String` thành text.
- Jackson JSON converter, trong Spring Framework 6.x thường là `MappingJackson2HttpMessageConverter`: có thể serialize record/POJO sang JSON.
- `ByteArrayHttpMessageConverter`, `ResourceHttpMessageConverter` và converter khác: đáp ứng các kiểu body tương ứng.
- Converter có thể được tùy chỉnh; kết quả thực tế phụ thuộc HTTP negotiation và application configuration.

### 4.1. `Content-Type`, `Accept`, `produces`

- `Content-Type` trong **response** mô tả định dạng dữ liệu server gửi.
- `Accept` trong **request** nói client có thể nhận những định dạng nào.
- `@GetMapping(produces = "...")` ràng buộc representation của handler và tham gia chọn mapping/negotiation.
- `@PostMapping(consumes = "...")` ràng buộc Content-Type của **request body**, không quyết định response luôn là JSON.

Nếu client gửi `Accept: application/xml` mà endpoint chỉ có mapping sản xuất `application/json`, Spring có thể trả **406 Not Acceptable**. Nếu client gửi JSON request body cho method chỉ `consumes=text/plain`, có thể gặp **415 Unsupported Media Type**. Không có nghĩa mọi method trả DTO đều luôn JSON: cần converter và media type tương thích.

**Lỗi nguy hiểm:** ép `produces="application/json"` lên method trả `String` nhưng chỉ trả literal `hello` mà không có dấu ngoặc kép JSON. Vì converter cho `String` có thể ghi chuỗi thô với media type JSON, client có thể nhận payload **không phải JSON hợp lệ**. Nếu muốn trả JSON string, hãy kiểm soát serialization thật rõ; với API thông thường, nên trả **DTO/record** thay vì cố dùng `String` như body JSON.

### 4.2. Nội dung object trả về chưa chắc đúng như Java debugger

Object Java có thể chứa `LocalDate`, enum, nested record, object references, Jackson annotations và lazy JPA proxies. Cấu hình serialization quyết định tên field, định dạng date, null handling và lỗi vòng tham chiếu. **Không trả thẳng entity nhạy cảm** rồi giả định JSON chỉ chứa field mong muốn; định nghĩa DTO response ổn định.

## 5. Ví dụ hoàn chỉnh: HTML view, text body và JSON body trong cùng ứng dụng

Ví dụ Java 17 + Spring Boot 3.x sử dụng **Thymeleaf** để chứng minh view name hoạt động và Spring MVC/Jackson để trả dữ liệu API. Đây là **một ứng dụng duy nhất** có ba loại endpoint để có thể so sánh trực tiếp.

Cấu trúc project:

~~~text
response-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/example/responsedemo/
    │   │   ├── ResponseDemoApplication.java
    │   │   └── web/
    │   │       ├── GreetingPageController.java
    │   │       ├── GreetingApiController.java
    │   │       └── GreetingDto.java
    │   └── resources/templates/
    │       └── greeting.html
    └── test/java/com/example/responsedemo/web/
        └── GreetingControllerTest.java
~~~

### 5.1. Gradle dependencies

`settings.gradle`:

~~~groovy
rootProject.name = 'response-demo'
~~~

`build.gradle` (minh họa project Spring Boot 3.3.5, Java 17; có thể dùng version 3.x được dự án đang quản lý):

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

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
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

`spring-boot-starter-web` cung cấp Spring MVC và JSON converter thông thường; `spring-boot-starter-thymeleaf` cung cấp view technology, view resolver và template rendering. Nếu bỏ Thymeleaf mà không cung cấp một view resolver khác, return `"greeting"` **không đủ** để render HTML.

Ví dụ chạy bằng Gradle wrapper có sẵn trong project:

~~~bash
./gradlew bootRun
~~~

Trên Windows PowerShell/CMD có thể dùng `.\gradlew.bat bootRun`. Các lệnh trên giả định wrapper và network dependency cần thiết đã sẵn sàng; không phải lệnh `javac` cho code phụ thuộc Spring, vì Gradle giải quyết classpath.

### 5.2. Spring Boot entry point

`src/main/java/com/example/responsedemo/ResponseDemoApplication.java`:

~~~java
package com.example.responsedemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ResponseDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResponseDemoApplication.class, args);
    }
}
~~~

Nằm ở package cha để component scan thấy controllers tại `web`.

### 5.3. `@Controller` trả tên view

`src/main/java/com/example/responsedemo/web/GreetingPageController.java`:

~~~java
package com.example.responsedemo.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GreetingPageController {

    @GetMapping("/pages/greeting")
    public String greeting(
            @RequestParam(name = "name", defaultValue = "Guest") String name,
            Model model) {

        model.addAttribute("name", name);
        return "greeting";
    }

    @GetMapping("/pages/home")
    public String home() {
        return "redirect:/pages/greeting?name=Guest";
    }
}
~~~

- Trả `"greeting"` để Spring tìm view/template; không gửi literal `greeting`.
- `Model` đưa `name` vào quá trình render.
- `"redirect:/..."` là tiền tố đặc biệt được các view resolver phù hợp dùng để phát HTTP redirect. Nó không được in làm nội dung trang.
- Code HTML render là kết quả **template + model**, khác Java return value.

### 5.4. Thymeleaf template cho view

`src/main/resources/templates/greeting.html`:

~~~html
<!doctype html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Greeting page</title>
</head>
<body>
    <h1 th:text="'Hello ' + ${name}">Hello Guest</h1>
    <p>This page is rendered from a Thymeleaf template.</p>
</body>
</html>
~~~

Với `GET /pages/greeting?name=Lan`, Thymeleaf sẽ render phần heading thành:

~~~html
<h1>Hello Lan</h1>
~~~

`th:text` xử lý escaped text khi chèn dữ liệu, tránh coi tên người dùng như HTML tùy ý. Tránh dùng các cách đưa dữ liệu chưa tin cậy vào template dưới dạng unescaped HTML trừ khi đã có policy làm sạch thích hợp.

### 5.5. DTO cho REST response

`src/main/java/com/example/responsedemo/web/GreetingDto.java`:

~~~java
package com.example.responsedemo.web;

public record GreetingDto(long id, String greeting) {
}
~~~

Record bất biến giúp định nghĩa shape JSON rõ ràng trong demo.

### 5.6. `@RestController` trả JSON/text và dùng `ResponseEntity`

`src/main/java/com/example/responsedemo/web/GreetingApiController.java`:

~~~java
package com.example.responsedemo.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GreetingApiController {

    @GetMapping(path = "/greeting", produces = MediaType.APPLICATION_JSON_VALUE)
    public GreetingDto greeting() {
        return new GreetingDto(7L, "Hello Lan");
    }

    @GetMapping(path = "/message", produces = MediaType.TEXT_PLAIN_VALUE)
    public String message() {
        return "Hello Lan";
    }

    @GetMapping(path = "/response", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GreetingDto> response() {
        return ResponseEntity.ok(new GreetingDto(8L, "Hello from ResponseEntity"));
    }

    @GetMapping(path = "/redirect-text", produces = MediaType.TEXT_PLAIN_VALUE)
    public String redirectText() {
        return "redirect:/pages/greeting";
    }
}
~~~

Quan sát:
- `GET /api/greeting` dùng Jackson viết JSON record.
- `GET /api/message` viết chính chuỗi `Hello Lan` vào body, không tìm template.
- `GET /api/response` trả status 200 và body JSON theo `ResponseEntity`.
- `GET /api/redirect-text` trả chuỗi literal `redirect:/pages/greeting`, **không** phát 302. `@RestController` đã bật body semantics ở cấp lớp.

### 5.7. Ví dụ `@Controller` chỉ bật body ở một method

Để kết hợp một controller phục vụ HTML và text, có thể thêm method sau **vào `GreetingPageController`**, cùng với các imports cần thiết:

~~~java
// Fragment đưa vào @Controller đã có:
@GetMapping(path = "/pages/health", produces = MediaType.TEXT_PLAIN_VALUE)
@ResponseBody
public String health() {
    return "UP";
}
~~~

Thêm imports:

~~~java
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ResponseBody;
~~~

Sau đó `GET /pages/health` sẽ trả text `UP`. Phần fragment không tự biên dịch riêng vì nó phải nằm trong class `GreetingPageController`; đây là lựa chọn thiết kế, không cần bổ sung nếu project muốn giữ web-page controller thuần view.

Nếu toàn lớp đều trả dữ liệu máy đọc, dùng `@RestController` rõ nghĩa hơn. Nếu chỉ một số method cần body trong controller phục vụ trang, dùng method-level `@ResponseBody` để thể hiện ngoại lệ.

## 6. Kiểm tra qua HTTP: cùng dữ liệu, khác response

Sau khi khởi động ứng dụng:

~~~bash
curl -i 'http://localhost:8080/pages/greeting?name=Lan'
curl -i 'http://localhost:8080/api/greeting'
curl -i 'http://localhost:8080/api/message'
curl -i 'http://localhost:8080/api/redirect-text'
curl -i 'http://localhost:8080/pages/home'
~~~

Các kết quả chính:

| URL | HTTP mặc định | Dạng response | Nội dung đáng quan sát |
| --- | --- | --- | --- |
| `/pages/greeting?name=Lan` | 200 | `text/html` | HTML từ `greeting.html`, có `<h1>Hello Lan</h1>` |
| `/api/greeting` | 200 | `application/json` | `{"id":7,"greeting":"Hello Lan"}` |
| `/api/message` | 200 | `text/plain` | `Hello Lan` |
| `/api/redirect-text` | 200 | `text/plain` | `redirect:/pages/greeting` |
| `/pages/home` | 302 thường gặp | header `Location` | Browser chuyển đến `/pages/greeting?name=Guest` |

Đây là kết quả theo code demo và cấu hình Spring Boot/Thymeleaf mặc định. Thứ tự header, charset và định dạng HTML đầy đủ có thể thay đổi theo template/version. Với redirect, dùng `curl -i` để thấy status/header và **không thêm `-L`** nếu muốn quan sát 302 trước khi client tự follow.

`@ResponseBody` không yêu cầu status mặc định là 201 hay 204; nếu method bình thường hoàn thành và không thay status, Spring thường trả 200. Hãy trả status có chủ đích khi xây API tạo/xóa tài nguyên.

## 7. MockMvc: kiểm tra view, body, media type và redirect

Viết test dựa trên toàn bộ ứng dụng để có Thymeleaf view resolver thực sự. `src/test/java/com/example/responsedemo/web/GreetingControllerTest.java`:

~~~java
package com.example.responsedemo.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class GreetingControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void controllerStringIsViewName() throws Exception {
        mvc.perform(get("/pages/greeting").param("name", "Lan"))
                .andExpect(status().isOk())
                .andExpect(view().name("greeting"))
                .andExpect(model().attribute("name", "Lan"))
                .andExpect(content().string(containsString("<h1>Hello Lan</h1>")));
    }

    @Test
    void restControllerDtoIsJsonBody() throws Exception {
        mvc.perform(get("/api/greeting").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.greeting").value("Hello Lan"));
    }

    @Test
    void restControllerStringIsPlainTextBody() throws Exception {
        mvc.perform(get("/api/message").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Hello Lan"));
    }

    @Test
    void redirectPrefixIsLiteralTextInRestController() throws Exception {
        mvc.perform(get("/api/redirect-text"))
                .andExpect(status().isOk())
                .andExpect(content().string("redirect:/pages/greeting"));
    }

    @Test
    void redirectPrefixInMvcControllerTriggersRedirect() throws Exception {
        mvc.perform(get("/pages/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pages/greeting?name=Guest"));
    }

    @Test
    void incompatibleAcceptOnJsonMappingIsNotAcceptable() throws Exception {
        mvc.perform(get("/api/greeting").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }
}
~~~

Chạy:

~~~bash
./gradlew test
~~~

Mẫu test cố ý kiểm tra **cả trạng thái, media type, view name, model và body**. Nếu chỉ kiểm tra status 200, một route trả nhầm body string thành tên view vẫn có thể bị bỏ sót nếu view đó tình cờ tồn tại.

Một vài lưu ý:
- `@SpringBootTest` khởi tạo context đầy đủ để view resolver/template có mặt; kiểm tra view bằng `@WebMvcTest` riêng có thể yêu cầu thêm auto-configuration/beans phù hợp.
- Nếu ứng dụng có Spring Security, filters, hoặc nhiều `@ControllerAdvice`, MockMvc phải cấu hình chứng thực và error contract tương ứng. Đây là thay đổi của môi trường test, không phải bản chất `@ResponseBody`.
- Test `406` có thể phụ thuộc tùy chỉnh content negotiation/converter. Với `produces=application/json` rõ ràng như demo, `Accept=application/xml` không tương thích nên 406 là kỳ vọng hợp lý.
- Không dựa vào nội dung HTML đầy đủ hay thứ tự HTTP header như hợp đồng bất biến; tập trung vào nội dung và metadata quan trọng.

## 8. Khi cần redirect thật từ một API

Sai nếu dùng literal view prefix trong REST method:

~~~java
@RestController
class WrongRedirectController {
    @GetMapping("/wrong-redirect")
    public String wrongRedirect() {
        return "redirect:/pages/greeting";
    }
}
~~~

Kết quả sẽ là **body chuỗi**, không phải HTTP redirect. Để có 302/303 và `Location` cụ thể, dùng `ResponseEntity` trong REST controller:

~~~java
// Fragment trong @RestController đã import các class tương ứng:
@GetMapping("/go")
public ResponseEntity<Void> go() {
    return ResponseEntity.status(302)
            .location(URI.create("/pages/greeting"))
            .build();
}
~~~

Cần import `java.net.URI`, `org.springframework.http.ResponseEntity`. 302 chỉ là ví dụ; sau POST khi muốn chuyển client đến tài nguyên khác, **303 See Other** thường thể hiện rõ yêu cầu dùng GET ở URL đích. Không dùng Java `String` để đoán status HTTP.

Với page controller, `return "redirect:/..."` đơn giản, gắn với flow MVC/view. Với REST API, trả `ResponseEntity` và URL/Location rõ ràng giúp client HTTP hiểu ngay protocol semantics.

`forward:` trong `@Controller` cũng có ý nghĩa view special prefix đối với cấu hình view resolver hỗ trợ nó. Khi trả từ `@RestController` dưới dạng `String`, đây chỉ là literal body. Forward là server-side request dispatch, **khác HTTP redirect** mà trình duyệt nhìn thấy.

## 9. Những trường hợp dễ gây nhầm lẫn khác

### 9.1. Model/View và body không thay thế nhau

~~~java
@Controller
class ReportController {
    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("count", 5);
        return "reports";
    }
}
~~~

`model.addAttribute` phục vụ view rendering, **không tự tạo JSON body** chứa `{"count":5}`. Muốn API JSON thì trả DTO, `Map` dưới `@ResponseBody`, hoặc `ResponseEntity`; còn `@Controller` trả `Map` mà không bật body semantics có thể được hiểu là bổ sung model attributes và chọn view.

### 9.2. `ModelAndView` và `View` là kiểu đặc biệt

`ModelAndView` thường mang view name (hoặc `View` instance), model và optional status. Nó có return handler riêng. Không khẳng định rằng **mọi** đối tượng Java trả từ `@RestController` đều luôn bị JSON serialize: framework xét **các return handler đặc biệt** trước tùy chữ ký/annotation. Tuy nhiên, để mã dễ đọc, các handler nhằm render template nên thuộc `@Controller`, còn API DTO/body nên thuộc `@RestController`.

### 9.3. `void`, `null` và servlet response

Method return `void` hoặc `null` có nhiều ngữ cảnh: đã tự ghi `HttpServletResponse`, có `@ResponseStatus`, đã đáp ứng ETag/Last-Modified hoặc chuyển sang default view name. Với REST handler, body có thể rỗng. Vì vậy **không coi `null` là lệnh trả 404 hay 204**. Trả `ResponseEntity.noContent().build()` cho 204, `ResponseEntity.notFound().build()` cho 404 khi đó là ý định API.

### 9.4. `@RequestBody` và `@ResponseBody` khác chiều

| Annotation | Vị trí chủ yếu | Dòng dữ liệu |
| --- | --- | --- |
| `@RequestBody` | Tham số handler | **HTTP request body → Java object** qua converter |
| `@ResponseBody` | Method/class controller | **Java return value → HTTP response body** qua converter |

Tương tự tên gọi nhưng không đồng nghĩa. Có thể dùng cả hai trên một REST endpoint POST: nhận JSON request và xuất JSON response, nhưng chỉ cần `@RestController` để thay `@ResponseBody` mức lớp.

### 9.5. Có cần template engine cho tất cả `@Controller`?

Không. `@Controller` chỉ là loại component web. Template engine hoặc view resolver cần thiết **khi method dùng view rendering**. Một `@Controller` thuần nhận request rồi trả `ResponseEntity` vẫn hoạt động mà không có Thymeleaf. Nếu method trả tên view nhưng project không có view technology được cấu hình phù hợp, đó mới là chỗ cần kiểm tra resolver/template.

### 9.6. Vì sao REST endpoint trả string nhưng Postman hiển thị JSON?

Một số công cụ UI cố parse/display theo header hoặc tự suy đoán. Hãy kiểm tra **byte body thực tế** và `Content-Type` qua curl hoặc integration test. `"hello"` dưới Java `String` có thể được ghi là `hello` không có dấu ngoặc kép; JSON scalar string hợp lệ phải có dấu ngoặc kép, ví dụ `"hello"` (nội dung HTTP có cặp dấu quote).

Nếu client bắt buộc nhận cấu trúc JSON, dùng DTO như `new MessageDto("hello")` → `{"message":"hello"}`, cách này rõ ràng hơn với API contract, công cụ sinh tài liệu và versioning.

## 10. Lỗi thường gặp và cách chẩn đoán

| Biểu hiện | Nguyên nhân thường gặp | Kiểm tra / sửa |
| --- | --- | --- |
| Trả `"ok"` nhưng gặp lỗi view/template | `@Controller` trả `String` được hiểu là tên view | Thêm `@ResponseBody` hoặc dùng `@RestController` cho API |
| Muốn HTML nhưng thấy chữ `home` | `@RestController` hoặc `@ResponseBody` khiến literal string được ghi body | Dùng `@Controller`, đảm bảo view resolver/template |
| Trả `"redirect:/login"` nhưng status 200 và body literal | Return nằm trong `@RestController` | Dùng `@Controller` redirect view hoặc `ResponseEntity` status + Location |
| Không tìm thấy template / HTTP 500 | Sai tên view, template path, thiếu Thymeleaf/JSP config | Đối chiếu `return "viewName"` với resolver/template |
| DTO không thành JSON | Thiếu Jackson/converter, lỗi serialize, media type không phù hợp | Kiểm dependency/converter, DTO và `Accept`/`produces` |
| 406 khi request JSON API | `Accept` không tương thích `produces` | Chỉnh `Accept` hoặc representation được hỗ trợ |
| Response hiển thị text trong khi header tuyên bố JSON | Trả `String` raw hoặc custom converter/produces lệch | Dùng DTO để serializer tạo JSON hợp lệ |
| JSON vô tình lộ thông tin nội bộ | Trả entity trực tiếp với các property không mong muốn | Tạo response DTO, kiểm soát serialization |
| Body rỗng nhưng frontend mong object | Method return `null`/`void` hoặc response 204 | Thiết kế status và response schema rõ ràng |
| Endpoint có response body nhưng class là `@Controller` | Method trả `ResponseEntity` hoặc có `@ResponseBody` | Đây là cơ chế hợp lệ; đọc return type/annotation trước khi kết luận |

Khi debug, kiểm tra cùng lúc **annotation ở type và method**, **return type**, **status**, **Content-Type**, **body bytes**, **view resolver** và **request Accept**. Những thành phần này thường giải thích hành vi mà chỉ nhìn Java `return` sẽ bỏ sót.

## 11. Chọn cách thiết kế theo ý định ứng dụng

**Ứng dụng server-rendered HTML:** dùng `@Controller`, method trả logical view name hoặc `ModelAndView`, dùng `Model` và template/view technology. Nếu vài endpoint nhỏ phải trả text/data, đặt `@ResponseBody` trên từng method tương ứng.

**REST API trả DTO:** dùng `@RestController` để mặc định serialize body; trả record/DTO và dùng `ResponseEntity<T>` khi cần điều khiển status/header. Định nghĩa `produces` khi API phải công bố media type cụ thể; viết test tương thích `Accept`.

**Controller hỗn hợp HTML/API:** có thể dùng `@Controller` cho template và `@ResponseBody`/`ResponseEntity` cho endpoints dữ liệu, nhưng nếu phạm vi đủ lớn, tách thành page controller và API controller giúp hành vi hiển nhiên hơn. Không cố dùng những chuỗi có tiền tố `redirect:` trong API controller để điều khiển HTTP.

**Lưu ý bảo mật:** View technologies và JSON converter có cách escape/serialize khác nhau. Thymeleaf `th:text` escape HTML; JSON serialization xử lý escaping theo chuẩn JSON. Không nối HTML trực tiếp từ dữ liệu request rồi trả `@ResponseBody String` và cho rằng nó có cùng các bảo vệ của template.

## 12. Tóm lược qua câu hỏi tự kiểm tra

**Câu 1.** Vì sao `@Controller` trả `"home"` không có `@ResponseBody` thường không gửi body `home`?

**Trả lời:** `String` được giải thích là logical view name, cần `ViewResolver` và view render.

**Câu 2.** `@RestController` có đồng nghĩa response luôn là JSON không?

**Trả lời:** Không. Nó kích hoạt body semantics ở cấp lớp; converter + type + media negotiation quyết định representation. `String` có thể là plain text.

**Câu 3.** `@Controller` trả `ResponseEntity<MyDto>` có phải thêm `@ResponseBody` không?

**Trả lời:** Không. `ResponseEntity` có return value handling riêng cho status, headers và body.

**Câu 4.** Làm sao redirect thực sự từ `@RestController`?

**Trả lời:** Dùng status 3xx và header `Location`, ví dụ `ResponseEntity.status(302).location(uri).build()`.

**Câu 5.** Một DTO Java được serialize JSON bởi thành phần nào?

**Trả lời:** Trong Spring MVC, return handler cho response body gọi `HttpMessageConverter`, thường Jackson JSON converter trong web starter.

**Câu 6.** Muốn chắc chắn một endpoint là view, cần test gì?

**Trả lời:** Kiểm `view().name(...)`, model attributes và HTML render; với body endpoint kiểm byte text/JSON, Content-Type và status.

## 13. Tài liệu chính thức

- [Spring Framework 6.2 — `@ResponseBody`](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/responsebody.html): annotation ở method/class, `HttpMessageConverter` và `@RestController`.
- [Spring Framework 6.2 — Return Values](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/return-types.html): các kiểu `String` view name, `View`, `ModelAndView`, `ResponseEntity`, `@ResponseBody`.
- [Spring Framework 6.2 — HTTP Message Conversion](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/message-converters.html): các converter và kiểu body.
- [Spring Framework 6.2 — Mapping Requests](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-requestmapping.html): `produces`, `consumes` và HTTP content negotiation.
- [Spring Framework 6.2 — View Resolution](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-core/viewresolver.html): vai trò `ViewResolver` trong render HTML.
- [Spring Boot — Spring MVC with Template Engines](https://docs.spring.io/spring-boot/reference/web/servlet.html): Spring MVC auto-configuration và template engines.

**Nguyên tắc chốt:** khi method trả `String`, đừng đoán behavior từ Java type. Hãy xác định **return handler được chọn**: `@Controller` thường đưa `String` đến **ViewResolver**, còn `@ResponseBody`/`@RestController` đưa return value đến **HttpMessageConverter**. Từ đó mới suy ra response thực tế và viết test đúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rest-responses-002">ResponseEntity: điều khiển status, header và body</a>

<details>
<summary>Xem chi tiết</summary>

# ResponseEntity: điều khiển status, header và body trong Spring MVC

> **Phạm vi:** Spring Web MVC (Servlet stack), ví dụ Java 17+ với Spring Boot 3.x/Spring Framework 6.x. Chủ đề chính là `org.springframework.http.ResponseEntity<T>`: một kiểu trả về của controller biểu diễn **mã trạng thái HTTP, tập header và body** trong cùng một giá trị. Bài viết hướng dẫn các builder method, HTTP semantics, content negotiation, xử lý tài nguyên thành công/thất bại, response không body, file và cache, rồi áp dụng vào một REST controller chạy được.

## 1. Vì sao REST endpoint cần kiểm soát toàn bộ response?

Một API không chỉ gửi dữ liệu JSON; nó còn cần nói cho client biết thao tác **đã thành công, tạo tài nguyên mới, không tìm thấy, đã xóa hay yêu cầu sửa lại**. HTTP response có ba phần quan trọng:

```http
HTTP/1.1 201 Created
Location: /api/books/11
Content-Type: application/json
X-Resource-Type: book

{"id":11,"title":"Clean Code","author":"Robert Martin"}
```

- **Status** `201 Created`: cho biết server đã tạo tài nguyên.
- **Header** `Location`: cung cấp URI tài nguyên vừa tạo. `Content-Type` mô tả định dạng body; các header khác bổ sung metadata.
- **Body**: nội dung tài nguyên hoặc thông báo lỗi, có thể không tồn tại tùy trường hợp.

Nếu method đơn thuần trả `Book` từ `@RestController`, Spring thường trả `200 OK` và serialize object thành JSON qua converter. Điều đó tốt cho GET đơn giản, nhưng chưa biểu đạt đủ các trường hợp `201`, `204`, `404`, custom headers hoặc cache semantics.

**`ResponseEntity<T>` gom cả ba thành phần thành một giá trị return rõ ràng.** Nó kế thừa `HttpEntity<T>` (chứa headers và body) và bổ sung status. Trong Spring MVC, body thường được ghi bằng `HttpMessageConverter` thích hợp; `ResponseEntity` không tự serialize JSON bằng cách ghép chuỗi.

## 2. Bắt đầu với cú pháp cơ bản

```java
@GetMapping("/hello")
public ResponseEntity<String> hello() {
    return ResponseEntity.ok("Xin chào");
}
```

Trong `@RestController`, ví dụ trả `200 OK` với body chuỗi `Xin chào`. Dữ liệu có thể là `String`, DTO, `List<Dto>`, `byte[]`, `Resource` hoặc kiểu khác được một converter hỗ trợ.

Ba cách tạo cùng mục tiêu status + headers + body:

```java
// 1. Builder API: thường dễ đọc nhất.
ResponseEntity<String> first = ResponseEntity
        .status(HttpStatus.ACCEPTED)
        .header("X-Job-Id", "j-42")
        .body("queued");

// 2. Constructor: hữu ích khi đã có HttpHeaders.
HttpHeaders headers = new HttpHeaders();
headers.set("X-Job-Id", "j-42");
ResponseEntity<String> second = new ResponseEntity<>(
        "queued", headers, HttpStatus.ACCEPTED);

// 3. Tạo nhanh kết quả 200.
ResponseEntity<String> third = ResponseEntity.ok("queued");
```

Đây là những **statement minh họa** đặt trong method Java, không phải chương trình Java độc lập. Các kiểu cần import `org.springframework.http.ResponseEntity`, `HttpHeaders`, `HttpStatus`. `first` và `second` đều có status 202/header `X-Job-Id`/body `queued`; `third` trả 200 và không thêm header đó.

Các phương thức builder nổi bật:

| Cách viết | HTTP status mặc định hoặc mục tiêu | Trả body? |
| --- | --- | --- |
| `ResponseEntity.ok(body)` | `200 OK` | Có |
| `ResponseEntity.ok().header(...).body(body)` | `200 OK` | Có |
| `ResponseEntity.status(HttpStatus.ACCEPTED).body(body)` | `202 Accepted` | Có |
| `ResponseEntity.created(uri).body(body)` | `201 Created` và `Location` | Có |
| `ResponseEntity.noContent().build()` | `204 No Content` | Không |
| `ResponseEntity.badRequest().body(error)` | `400 Bad Request` | Có |
| `ResponseEntity.notFound().build()` | `404 Not Found` | Không |
| `ResponseEntity.status(HttpStatus.CONFLICT).body(error)` | `409 Conflict` | Có |
| `ResponseEntity.status(HttpStatus.SEE_OTHER).location(uri).build()` | `303 See Other` và `Location` | Không trong ví dụ |
| `ResponseEntity.of(optional)` | `200` nếu có giá trị, `404` nếu rỗng | Theo Optional |

Các method trên thể hiện **những kiểu response khác nhau**. Không chọn status theo sở thích: mã phải khớp kết quả thao tác và hợp đồng API.

### 2.1. `ResponseEntity<T>` là kiểu dữ liệu có generic

```java
ResponseEntity<BookDto> bookResponse;
ResponseEntity<List<BookDto>> booksResponse;
ResponseEntity<String> textResponse;
ResponseEntity<Void> noBodyResponse;
ResponseEntity<byte[]> binaryResponse;
```

Generic `T` mô tả kiểu body ở compile time. Nó giúp IDE, người viết code và test hiểu hợp đồng trả về. `ResponseEntity<Void>` đặc biệt hữu ích cho endpoint trả status/header nhưng không có body, như `204 No Content`. Nếu một method cần trả `BookDto` hoặc `ApiError` tùy nhánh, có thể trả `ResponseEntity<?>` hoặc thiết kế exception handler/error abstraction nhất quán; đừng ép tất cả response thành chuỗi thô.

## 3. Status code và hành vi REST đúng ngữ nghĩa

| Mã | Ngữ nghĩa điển hình | Ví dụ |
| --- | --- | --- |
| `200 OK` | Đọc hoặc cập nhật thành công và có representation trả về | GET sách, PUT trả object |
| `201 Created` | Tạo được tài nguyên mới | POST tạo sách kèm `Location` |
| `202 Accepted` | Đã tiếp nhận, xử lý chưa chắc hoàn tất | Đưa job vào hàng đợi |
| `204 No Content` | Thành công và không gửi body | DELETE thành công |
| `301/302/303/307/308` | Điều hướng sang URI khác, mỗi mã có quy tắc method khác nhau | `303 See Other` sau POST |
| `304 Not Modified` | Điều kiện cache cho biết không cần gửi representation | Conditional GET/HEAD |
| `400 Bad Request` | Request không hợp lệ về cú pháp/đầu vào | JSON sai định dạng |
| `401 Unauthorized` | Chưa có xác thực hợp lệ; thường kèm challenge khi áp dụng | Authentication bắt buộc |
| `403 Forbidden` | Danh tính không được phép thao tác | Thiếu quyền |
| `404 Not Found` | Không có tài nguyên tương ứng hoặc không tiết lộ sự tồn tại | GET ID không có |
| `409 Conflict` | Xung đột trạng thái tài nguyên | Trùng mã duy nhất |
| `412 Precondition Failed` | HTTP precondition thất bại | `If-Match`/ETag không khớp |
| `422 Unprocessable Content` | Body có cấu trúc hợp lệ nhưng nội dung không thể xử lý theo policy | Validation nghiệp vụ nếu API chọn 422 |
| `500 Internal Server Error` | Lỗi server không được xử lý thành phản hồi cụ thể | Không cố trả 200 cho lỗi hệ thống |

Lưu ý `202` **không đồng nghĩa tác vụ đã thành công**; với xử lý bất đồng bộ, trả job ID và cung cấp API theo dõi trạng thái khi phù hợp. `204`/`304` không có response body theo semantics HTTP: dùng `build()`, không cố thêm JSON body. Với lỗi đầu vào, thống nhất quy ước trả 400 hay 422 và cách biểu diễn lỗi; Spring có các default error handling riêng có thể khác thiết kế cụ thể của API.

## 4. Builder API: ba thành phần được thiết lập ra sao?

### 4.1. Status có sẵn hoặc status bất kỳ

```java
ResponseEntity<String> ok = ResponseEntity.ok("done");
ResponseEntity<String> accepted = ResponseEntity
        .status(HttpStatus.ACCEPTED)
        .body("processing");
ResponseEntity<String> limited = ResponseEntity
        .status(429)
        .header("Retry-After", "30")
        .body("try later");
```

`ResponseEntity.status(...)` nhận mã trạng thái hợp lệ; các constant `HttpStatus` giúp biểu đạt ý định. Trong Spring Framework 6+, `HttpStatusCode` là abstraction cho status; các overload/builder tương thích khác nhau theo version. Chọn `HttpStatus` hoặc integer để code minh họa đơn giản.

### 4.2. Header tự định nghĩa

```java
return ResponseEntity.ok()
        .header("X-Trace-Id", traceId)
        .header("X-API-Version", "1")
        .body(dto);
```

Các header phải được tạo từ dữ liệu đáng tin cậy hoặc đã kiểm tra; không phản chiếu tùy ý chuỗi client gửi vào header. Header `X-Trace-Id` là ví dụ metadata, không mang vai trò xác thực. Nếu JavaScript chạy từ origin khác cần đọc custom response header, ngoài header đó còn phải cấu hình CORS `Access-Control-Expose-Headers` tương ứng (khi policy cho phép).

### 4.3. Dùng builder tiện lợi cho header chuẩn

```java
return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .cacheControl(CacheControl.noStore())
        .header("X-Source", "catalog")
        .body(bookDto);
```

- `contentType(MediaType.APPLICATION_JSON)` đặt `Content-Type: application/json`.
- `cacheControl(CacheControl.noStore())` hướng dẫn cache không lưu representation này.
- `header("X-Source", "catalog")` thêm header tùy biến.
- `body(bookDto)` kết thúc builder và tạo `ResponseEntity<BookDto>`.

Các builder tiện lợi khác gồm `location(URI)`, `contentLength(long)`, `allow(HttpMethod...)`, `eTag(String)`, `lastModified(...)` và `headers(HttpHeaders)` theo nhu cầu. Đừng khai báo `Content-Length` đoán mò cho JSON do converter tạo vì serialization có thể biến đổi byte so với độ dài ký tự; để server quyết định khi phù hợp.

### 4.4. HTTP body vẫn cần converter

`ResponseEntity<String>` thường dùng `StringHttpMessageConverter`; `ResponseEntity<BookDto>` thường dùng Jackson JSON converter khi media type là JSON; `ResponseEntity<byte[]>` dùng converter byte array; `ResponseEntity<Resource>` dùng resource converter phù hợp. Việc viết:

```java
ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body("not valid JSON");
```

không biến chuỗi `not valid JSON` thành JSON hợp lệ. Media type là **cam kết định dạng**, không phải lệnh tự động serialize lại String. Trả object DTO thay vì tự nối chuỗi JSON để converter serialize đúng.

## 5. Tạo mới tài nguyên: `201 Created` và `Location`

Một POST tạo sách thường có quá trình:

1. Nhận request DTO chứa field mà client được phép gửi.
2. Kiểm tra input và quyền tạo.
3. Tạo ID mới và lưu thành công.
4. Trả `201 Created`; header `Location` trỏ đến URI của sách; body có thể là representation vừa tạo.

Ví dụ method:

```java
@PostMapping("/books")
public ResponseEntity<BookDto> create(@RequestBody CreateBookRequest input) {
    BookDto saved = service.create(input); // Service do ứng dụng cung cấp
    URI uri = URI.create("/books/" + saved.id());

    return ResponseEntity.created(uri).body(saved);
}
```

Đây là method **minh họa**, phụ thuộc `BookDto`, `CreateBookRequest` và `service` chưa khai báo; xem chương trình độc lập ở mục 10 để chạy ngay. Trong ví dụ này `Location` là relative reference hợp lệ. Nếu cần absolute URL hoặc ứng dụng có context path/proxy, tạo URI theo cấu hình đáng tin cậy và chính sách external base URL thay vì tự tin tưởng header forward từ client.

Không trả 201 khi chưa tạo xong tài nguyên, ví dụ job chỉ mới xếp hàng: cân nhắc `202 Accepted` và URI theo dõi. Đừng đồng nhất `Location` với đường dẫn file nội bộ trên server.

## 6. GET: 200 có body, 404 khi không có

Đối với GET có thể trả object khi có và 404 khi vắng:

```java
@GetMapping("/books/{id}")
public ResponseEntity<BookDto> get(@PathVariable long id) {
    return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
}
```

Đây cũng là **method minh họa**. Hàm `service.findById(id)` giả định trả `Optional<BookDto>`. Một lựa chọn ngắn:

```java
@GetMapping("/books/{id}")
public ResponseEntity<BookDto> get(@PathVariable long id) {
    return ResponseEntity.of(service.findById(id));
}
```

`ResponseEntity.of(Optional<T>)` trả status 200 với body khi Optional có giá trị, ngược lại 404 không body. Phương án này đơn giản nhưng không trả error DTO tùy biến cho 404. Trong API thực tế, chọn một chiến lược error nhất quán (exception handler, `ProblemDetail`, hoặc body error riêng) thay vì viết mỗi endpoint một dạng ngẫu nhiên.

### 6.1. Khi nào 404 có body?

```java
if (book == null) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiError("Book not found"));
}
```

Trả body JSON mô tả lỗi là hợp lệ, miễn status vẫn là 404. Khi method có hai body type khác nhau, khai báo `ResponseEntity<?>` hoặc dùng mô hình error response thống nhất. Không bao giờ trả `200 OK` với `{"error":"not found"}` cho một tình huống đáng lẽ là 404 chỉ để “đỡ lỗi” phía client.

## 7. PUT, PATCH, DELETE: chọn 200, 204, 404 theo kết quả

Với thao tác cập nhật:

```java
@PutMapping("/books/{id}")
public ResponseEntity<BookDto> replace(
        @PathVariable long id,
        @RequestBody UpdateBookRequest input) {
    Optional<BookDto> updated = service.replace(id, input);
    return updated.map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
}
```

Đây là ví dụ service không độc lập. Trả `200` nếu muốn gửi representation sau cập nhật; `204` nếu cập nhật thành công nhưng không cần body; `404` nếu bản ghi không tồn tại và API chọn không tự tạo mới với PUT.

DELETE:

```java
@DeleteMapping("/books/{id}")
public ResponseEntity<Void> delete(@PathVariable long id) {
    boolean existed = service.deleteIfExists(id);
    return existed
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
}
```

API có thể lựa chọn semantics idempotent cho DELETE theo policy: gọi xóa lặp lại không tạo hiệu ứng ngoài ý muốn, nhưng status cho lần gọi sau **có thể là 204 hoặc 404** tùy hợp đồng. Đừng coi hai status ấy tương đương về ý nghĩa; công bố rõ cho client.

**Đúng với `204`:** body phải rỗng. Không viết `ResponseEntity.noContent().build()` rồi cố thêm JSON thông điệp thành công. Nếu cần body, dùng status thích hợp như 200.

## 8. Thông tin lỗi có cấu trúc: `ProblemDetail` và status

Spring Framework 6 có `org.springframework.http.ProblemDetail` phục vụ phản hồi lỗi theo Problem Details (hiện nay RFC 9457):

```java
@GetMapping("/inventory/{id}")
public ResponseEntity<ProblemDetail> inventory(@PathVariable long id) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "Không tìm thấy sản phẩm " + id);
    problem.setTitle("Product not found");
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(problem);
}
```

Đây là **method minh họa** trong controller, không chạy cùng demo nếu chưa chép vào lớp phù hợp. Với hệ sinh thái Spring MVC hỗ trợ ProblemDetail, response có thể được render dưới `application/problem+json`. Đảm bảo status HTTP và `problem.status` nhất quán; không đặt status body 404 rồi trả HTTP 200. Đừng đưa stacktrace, SQL, khóa API hoặc chi tiết nội bộ vào error body cho client.

Nếu Spring MVC và ứng dụng đã có `@RestControllerAdvice` / `@ExceptionHandler` thống nhất lỗi, không cần mọi method xây error body thủ công. Với validation, Spring có thể ném exception trước khi controller method chạy; xử lý ở advice phù hợp, tránh nghĩ rằng mọi lỗi sẽ đi qua nhánh `if` của method.

## 9. Headers quan trọng trong REST response

### 9.1. `Content-Type` và `Accept`

Request header `Accept: application/json` cho biết client muốn nhận JSON. `@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)` hạn chế representation route cho phép. Nếu client chỉ chấp nhận XML nhưng server không có XML representation, thường trả **406 Not Acceptable**.

Response header `Content-Type: application/json` cho biết **body thực tế** có định dạng JSON. Dùng đúng DTO/converter thay vì đổi header để “đánh lừa” client. Request `Content-Type` lại mô tả **body gửi lên**, ví dụ POST `application/json`; nếu route `consumes` không khớp, có thể trả **415 Unsupported Media Type**.

### 9.2. `Location`

`Location` phổ biến với `201 Created` hoặc response redirect. Với `201` nó biểu thị URI liên quan đến tài nguyên mới; với `303 See Other` nó hướng dẫn client chuyển tới tài nguyên khác theo semantics HTTP. Hãy tạo URI bằng dữ liệu đã chuẩn hóa và chính sách host tin cậy để tránh open redirect hoặc host-header injection.

### 9.3. `Cache-Control`, `ETag`, `Last-Modified`

Một GET của tài nguyên có thể trả:

```java
return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
        .eTag("\"book-v7\"")
        .body(book);
```

Đây là **đoạn code minh họa**: cần `java.util.concurrent.TimeUnit`, `org.springframework.http.CacheControl`; `book` phải được khai báo. `ETag` là validator cho **phiên bản representation cụ thể**, không nên đặt một tag cố định nếu nội dung có thể thay đổi. Builder `eTag(...)` chấp nhận giá trị hợp lệ có dấu nháy hoặc tự xử lý dấu nháy theo quy tắc của phiên bản Spring.

Khi client gửi conditional request `If-None-Match` hoặc `If-Modified-Since`, Spring MVC có thể đánh giá validator và trả **304 Not Modified** (không body) nếu các điều kiện phù hợp, tùy cách request được xử lý. `ETag` giúp tối ưu tải mạng; `Cache-Control` xác định chính sách lưu cache. `Cache-Control: no-store` phù hợp với phản hồi nhạy cảm cần tránh lưu trữ; `private` không đồng nghĩa với bảo mật dữ liệu tuyệt đối.

Với cập nhật có điều kiện, `If-Match` và `412 Precondition Failed` có thể ngăn lost update, nhưng **chỉ đặt ETag trên GET không tự bảo đảm concurrency**. Cần xử lý precondition/phiên bản khi PUT/PATCH/DELETE, kiểm thử các trường hợp xung đột. Spring có hỗ trợ HTTP conditional request ở tầng MVC; persistence vẫn cần cơ chế concurrency của chính nó.

### 9.4. `Content-Disposition` cho file

```java
return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"example.txt\"")
        .body(resource);
```

Đây là snippet dùng `org.springframework.core.io.Resource` và `HttpHeaders`; Spring chọn resource converter phù hợp. Không dùng tên file do client nhập trực tiếp trong header nếu chưa xử lý an toàn. Không nạp toàn bộ file lớn vào byte array nếu có thể dùng Resource/streaming phù hợp và kiểm soát quyền tải file.

## 10. Ví dụ hoàn chỉnh: REST Book API chạy với Java 17+ và Spring Boot 3.x

Ví dụ sử dụng **bộ nhớ trong ứng dụng** để quan sát chính xác status/header/body mà không phụ thuộc database hay security. Sau khi restart, dữ liệu đặt lại. Tạo Spring Boot 3.x project trên [Spring Initializr](https://start.spring.io/) với Java 17+ và dependency **Spring Web**. Dùng Maven wrapper do Initializr sinh.

Đặt toàn bộ mã dưới đây vào `src/main/java/com/example/demo/DemoApplication.java`. File có một public class và các top-level package-private types, hợp lệ trên Java 17:

```java
package com.example.demo;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

record Book(long id, String title, String author) {
}

record BookInput(String title, String author) {
}

record ApiError(String message) {
}

@RestController
@RequestMapping("/api/books")
class BookController {

    private final Map<Long, Book> books = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(11);

    BookController() {
        books.put(10L, new Book(10, "Java Basics", "Minh"));
    }

    @GetMapping(path = "/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("id") long id) {
        Book book = books.get(id);
        if (book == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("Book " + id + " not found"));
        }

        return ResponseEntity.ok()
                .header("X-Book-Id", Long.toString(id))
                .cacheControl(CacheControl.noStore())
                .body(book);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Book> create(@RequestBody BookInput input) {
        long id = nextId.getAndIncrement();
        Book saved = new Book(id, input.title(), input.author());
        books.put(id, saved);

        URI location = URI.create("/api/books/" + id);
        return ResponseEntity.created(location)
                .header("X-Resource-Type", "book")
                .body(saved);
    }

    @PutMapping(path = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Book> replace(
            @PathVariable("id") long id,
            @RequestBody BookInput input) {

        if (!books.containsKey(id)) {
            return ResponseEntity.notFound().build();
        }

        Book saved = new Book(id, input.title(), input.author());
        books.put(id, saved);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        if (books.remove(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/text",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> text() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "text/plain;charset=UTF-8"))
                .body("Book API ready");
    }

    @GetMapping(path = "/download",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> download() {
        Resource file = new ByteArrayResource(
                "BOOK\n".getBytes(StandardCharsets.UTF_8));

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"book.txt\"")
                .body(file);
    }

    @GetMapping("/redirect")
    public ResponseEntity<Void> redirect() {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create("/api/books/10"))
                .build();
    }
}
```

**Giải thích thiết kế:**

- `get` trả 200/JSON + `X-Book-Id` và `Cache-Control: no-store` khi tìm thấy; trả 404 + `ApiError` nếu không có.
- `create` cấp ID mới, lưu vào map, trả 201 + `Location` + `X-Resource-Type` + JSON body. `URI.create("/api/books/" + id)` dùng **relative URI** thích hợp cho demo tại context path rỗng; ứng dụng có prefix/proxy cần xây URI tương ứng.
- `replace` chỉ cập nhật ID đã tồn tại, trả 200 + representation mới hoặc 404 không body.
- `delete` trả 204 không body khi vừa xóa; gọi lại nhận 404 theo policy của demo.
- `text` chứng minh `ResponseEntity<String>` có thể trả text/plain; `download` dùng `ResponseEntity<Resource>` để gửi file; `redirect` trả 303 + Location.
- `ConcurrentHashMap` và `AtomicLong` phục vụ **demo một tiến trình**; không phải persistence, transaction, authorization hay giải pháp đồng bộ cập nhật production.
- Demo không có validation đầu vào; code thực tế cần request DTO kèm ràng buộc, Bean Validation, kiểm tra quyền và xử lý domain exception.

### 10.1. Chạy chương trình

Từ thư mục gốc dự án Maven do Spring Initializr tạo:

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows PowerShell hoặc Command Prompt
mvnw.cmd spring-boot:run
```

Khi ứng dụng khởi động, server mặc định nghe `http://localhost:8080` (nếu chưa đổi `server.port` hoặc context path). Chạy mỗi nhóm request theo thứ tự và trên trạng thái mới khởi động để so sánh kết quả chính xác.

### 10.2. GET thành công: 200 + JSON + headers

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/api/books/10
```

Phần quan trọng của response:

```http
HTTP/1.1 200
Content-Type: application/json
X-Book-Id: 10
Cache-Control: no-store

{"id":10,"title":"Java Basics","author":"Minh"}
```

Thứ tự header, dấu cách và các header do container thêm có thể khác; body JSON dự kiến có ba field theo cấu trúc record. `-i` hiển thị status và headers; `Content-Length` có thể do server quyết định.

### 10.3. GET không tồn tại: 404 + JSON lỗi

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/api/books/999
```

Status **404 Not Found**; body từ `ApiError`:

```json
{"message":"Book 999 not found"}
```

Nếu client gửi `Accept` không tương thích với `produces=application/json`, Spring có thể chọn xử lý 406 trước khi gọi controller; đừng coi kết quả 404 là bắt buộc cho mọi header.

### 10.4. POST tạo mới: 201 + Location + JSON

```bash
curl -i -X POST http://localhost:8080/api/books \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  --data-binary '{"title":"Clean Code","author":"Robert Martin"}'
```

Trên lần đầu POST sau khi khởi động, server cấp ID `11` và trả:

```http
HTTP/1.1 201
Location: /api/books/11
X-Resource-Type: book
Content-Type: application/json

{"id":11,"title":"Clean Code","author":"Robert Martin"}
```

Các field/header có thể được format/thứ tự khác tùy runtime; status, Location, custom header và nội dung JSON là điều cần kiểm chứng. Tiếp đó `GET /api/books/11` có thể trả object vừa tạo. Nếu POST thêm lần nữa, ID sẽ tăng; đừng giả định ID luôn là `11`.

### 10.5. PUT thay thế: 200 hoặc 404

```bash
curl -i -X PUT http://localhost:8080/api/books/10 \
  -H 'Content-Type: application/json' \
  --data-binary '{"title":"Spring MVC","author":"Lan"}'
```

Status **200 OK**, body:

```json
{"id":10,"title":"Spring MVC","author":"Lan"}
```

Nếu PUT tới ID `999` (chưa được tạo), demo trả **404 không body**. Đây là policy của endpoint: nhiều API có thể chọn PUT tạo tài nguyên tại URI đích nếu hợp đồng cho phép; lúc đó logic và status cần thiết kế khác.

### 10.6. DELETE: 204 hoàn toàn không có body

```bash
curl -i -X DELETE http://localhost:8080/api/books/10
```

Lần xóa đầu sau khi book `10` còn tồn tại: status **204 No Content**, không body. Lần xóa thứ hai: **404 Not Found**, không body theo code demo. Nếu chạy lại GET `/api/books/10` sau DELETE thì nhận 404 với `ApiError`.

### 10.7. Text, file và redirect

Text:

```bash
curl -i http://localhost:8080/api/books/text
```

Status 200; header `Content-Type: text/plain;charset=UTF-8`, body:

```text
Book API ready
```

File:

```bash
curl -i http://localhost:8080/api/books/download
```

Status 200; `Content-Type: application/octet-stream`, `Content-Disposition: attachment; filename="book.txt"`, body chính xác **5 byte** gồm `BOOK` và newline. Lưu bằng `curl -o book.txt http://localhost:8080/api/books/download` để kiểm tra file riêng, thay vì dùng `-i` khi muốn nội dung binary sạch.

Redirect:

```bash
curl -i http://localhost:8080/api/books/redirect
```

Status **303 See Other**, header `Location: /api/books/10`, không body. Không dùng `curl -L` khi muốn quan sát response redirect ban đầu vì `-L` làm cURL đi tiếp sang URI được chỉ định.

Với Windows PowerShell, dùng `curl.exe` và viết mỗi lệnh trên một dòng (dấu `\` nối dòng ở Bash không có tác dụng tương đương trong PowerShell).

## 11. So sánh `ResponseEntity` với các cách trả response khác

| Cách trả về | Điều khiển status | Điều khiển headers | Body | Thích hợp |
| --- | --- | --- | --- | --- |
| DTO trực tiếp từ `@RestController` | Thường 200 mặc định | Thường phụ thuộc lớp khác | Có | GET đơn giản |
| `@ResponseStatus(HttpStatus.CREATED)` | Status thường **cố định** theo annotation | Không trực tiếp qua annotation này | Có thể có | Method luôn một status |
| `ResponseEntity<T>` | Linh hoạt theo kết quả từng nhánh | Có | Có/không | REST endpoints |
| `HttpEntity<T>` | Không tự khai báo status riêng | Có | Có | Response chỉ cần headers/body |
| `HttpServletResponse` | Có, thao tác thấp cấp | Có | Tự ghi hoặc framework xử lý theo pattern | Servlet streaming/integration |
| Ném exception + `@ExceptionHandler` | Qua handler lỗi | Có thể cấu hình | Có thể có | Chuẩn hóa lỗi toàn API |

Ví dụ annotation:

```java
@ResponseStatus(HttpStatus.CREATED)
@PostMapping("/simple")
public BookDto create(@RequestBody BookInput input) {
    return service.create(input);
}
```

Đây là code **minh họa**, yêu cầu `service` do ứng dụng định nghĩa. `@ResponseStatus` tiện khi toàn method luôn cùng status, nhưng khi cùng handler có thể trả 201, 400, 409 tùy trạng thái, `ResponseEntity` hoặc exception mapping thể hiện linh hoạt hơn.

**Lưu ý:** `ResponseEntity` có thể trả từ `@Controller` thông thường, không bắt buộc class gắn `@RestController`. Spring nhận diện kiểu return và viết HTTP response tương ứng, không coi nó là model/view name.

## 12. Test thật status + header + body bằng MockMvc

Trong ứng dụng Spring MVC, `MockMvc` giúp test kết quả HTTP mà không cần khởi chạy server mạng thật. Spring Boot test thường cần dependency `spring-boot-starter-test`. Ví dụ kiểm thử endpoint GET trên ứng dụng ở mục 10:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void getBookReturnsStatusHeaderAndBody() throws Exception {
        mvc.perform(get("/api/books/10")
                        .accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Book-Id", "10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Java Basics"));
    }

    @Test
    void missingBookReturns404() throws Exception {
        mvc.perform(get("/api/books/999")
                        .accept("application/json"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Book 999 not found"));
    }
}
```

Lưu ở `src/test/java/com/example/demo/BookControllerTest.java`. Đây là **file test riêng** theo JUnit 5; không đặt cùng `DemoApplication.java`. Chạy:

```bash
# macOS / Linux
./mvnw test

# Windows
mvnw.cmd test
```

Hai test đọc trạng thái mới từ controller trong Spring test context, không gọi DELETE; không phụ thuộc thao tác cURL thực hiện ở một process khác. Nếu cấu hình security được thêm sau này, test phải xử lý security filter/token/role tương ứng.

Khi test endpoint tạo/xóa, nên xác nhận các nhánh `201` + `Location`, `204` + body rỗng, `404` và `406/415` khi sử dụng consumes/produces. Test header + body cùng nhau giúp ngăn trường hợp trả đúng JSON nhưng status sai.

## 13. Lỗi thường gặp khi dùng ResponseEntity

| Lỗi | Vấn đề | Cách sửa |
| --- | --- | --- |
| Trả `ResponseEntity.ok(errorDto)` khi lỗi | Client thấy 200 dù thao tác thất bại | Chọn status 4xx/5xx thích hợp |
| POST tạo xong nhưng luôn trả 200 | Mất tín hiệu Created cho client | `ResponseEntity.created(location).body(saved)` |
| Trả `204` kèm JSON | Vi phạm ý nghĩa no-content | Dùng `build()` hoặc chọn 200 nếu có body |
| Tự nối chuỗi JSON | Sai escaping/schema, khó kiểm thử | Trả DTO qua converter |
| Đặt `Content-Type: application/json` cho String không hợp lệ | Client parse lỗi | Dùng object JSON hoặc định dạng đúng |
| Tự điền `Content-Length` theo `String.length()` | Số ký tự không bằng số byte UTF-8 | Để framework tính khi phù hợp |
| Header `Location` dùng host từ input không tin cậy | Rủi ro open redirect/host injection | Tạo URI bằng base URL/forwarded-header policy tin cậy |
| Response cache nhầm nội dung riêng tư | Có thể lưu/được chia sẻ ngoài ý muốn | `CacheControl.noStore()` hoặc private policy thích hợp |
| Gán ETag cố định cho dữ liệu biến đổi | Client nhận 304 cũ hoặc state sai | Tính validator theo phiên bản/representation |
| Dùng `ResponseEntity<?>` khắp code vô cớ | Hợp đồng body khó đọc | Ưu tiên `ResponseEntity<Dto>` và error handler |
| Trả 404 cho mọi exception | Che giấu bug và làm client hiểu sai | Phân loại lỗi nghiệp vụ, validation, hệ thống |
| Lẫn response thủ công với converter | Ghi body hai lần, header đã commit | Chọn rõ response strategy cho method |

## 14. Những điểm cần nhớ khi triển khai production

- **Tách request DTO và response DTO:** `ResponseEntity` không lọc field nhạy cảm. Không trả trực tiếp entity chứa password hash, tenant secrets, internal notes chỉ vì serializer tiện.
- **Kiểm tra quyền trước khi tạo body:** status 403/404 tùy chính sách không được quyết định chỉ từ dữ liệu client gửi. Ownership và authorization vẫn cần ở service/security layer.
- **HTTP header là một phần hợp đồng API:** các header `Location`, `ETag`, `Retry-After`, `Cache-Control` và `Content-Disposition` cần đúng mục đích. Custom header có thể bị proxy/caching/CORS policy ảnh hưởng.
- **Chú ý phương thức HEAD và conditional GET:** không mặc định HEAD phải có body; `304` cũng không mang representation body.
- **Đừng ép response đã được commit:** ghi trực tiếp vào `HttpServletResponse` rồi đổi status/header sau flush có thể không có tác dụng; với `ResponseEntity` nên để Spring chịu trách nhiệm serialize body.
- **Tránh response quá lớn:** `ResponseEntity<byte[]>` giữ dữ liệu trong RAM; dùng `Resource`/streaming hoặc phân trang khi phù hợp.
- **Đồng bộ status và payload lỗi:** nếu body theo RFC 9457 chứa `status`, trường này cần phù hợp HTTP status; không lộ stacktrace hay dữ liệu nhạy cảm.

### Checklist chọn kết quả cho handler

1. Request hợp lệ và được phép xử lý chưa? Nếu chưa, chọn status 4xx đúng nguyên nhân và error contract.
2. Tài nguyên có tồn tại/được tạo xong không? GET có thể 200/404; POST đã tạo có thể 201/Location; job chưa hoàn tất có thể 202.
3. Có body có ích không? Nếu không, chọn response không body như 204 khi ngữ nghĩa phù hợp.
4. Client yêu cầu media type nào qua `Accept`? `produces` và converter có hỗ trợ không?
5. Có cần header chuẩn như `Location`, `Cache-Control`, `ETag`, `Content-Disposition`?
6. Test đã kiểm tra **status + header + body (hoặc body rỗng)** và những nhánh lỗi chưa?

## 15. Tự kiểm tra

**Câu 1.** Trong `@RestController`, cần trả 201, header `Location: /api/orders/5`, JSON object `OrderDto`. Viết thế nào?

```java
return ResponseEntity
        .created(URI.create("/api/orders/5"))
        .body(orderDto);
```

Đoạn trên yêu cầu `orderDto` được tạo/lưu thành công. `created` tự đặt status 201 và Location; DTO đi qua converter.

**Câu 2.** Khi DELETE thành công nhưng không trả nội dung, nên dùng `ResponseEntity.ok("deleted")` hay `ResponseEntity.noContent().build()`?

**Đáp án:** Dùng `noContent().build()` nếu API muốn thể hiện thành công không body, tức 204. Có thể chọn 200 kèm representation theo hợp đồng khác, nhưng không được đính body vào 204.

**Câu 3.** Tại sao `ResponseEntity.status(404).body(error)` khác `ResponseEntity.ok(error)`?

**Đáp án:** Body có thể giống nhau nhưng HTTP status quyết định cách client, cache, proxy, monitoring hiểu kết quả. Trả 200 cho lỗi 404 là sai ngữ nghĩa.

**Câu 4.** `ResponseEntity<String>` và `ResponseEntity<MyDto>` có nhất thiết sử dụng cùng converter không?

**Đáp án:** Không. Converter phụ thuộc kiểu Java và media type; chuỗi thường dùng string converter, DTO JSON thường dùng Jackson converter.

**Câu 5.** Nếu POST trả 201 nhưng resource chưa được tạo mà chỉ mới vào hàng đợi, mã nào đáng cân nhắc hơn?

**Đáp án:** `202 Accepted` kèm metadata/job tracking phù hợp, vì xử lý chưa hoàn tất.

**Câu 6.** Chỉ cần gắn `eTag("v1")` lên GET là PUT tự chống ghi đè phiên bản cũ phải không?

**Đáp án:** Không. Phải có xử lý HTTP precondition như `If-Match`/`412` hoặc concurrency control ở service/database; ETag GET đơn lẻ không khóa cập nhật.

## 16. Tài liệu chính thức

- [Spring Framework — `ResponseEntity`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html): cú pháp builder, status/header/body, Resource và async.
- [Spring Framework — Controller Return Values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html): `ResponseEntity`, `HttpEntity`, `@ResponseBody` và các return type khác.
- [Spring Framework — HTTP Caching](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-caching.html): `Cache-Control`, `ETag` và conditional HTTP.
- [Spring Framework — HTTP Message Converters](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html): cách converter serialize body theo kiểu Java/media type.
- [Spring Framework — Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html): `ProblemDetail` và error handling trong REST.
- [Spring Framework — Request Mapping](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html): `consumes`, `produces` và content negotiation.
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110): định nghĩa các status/header HTTP chuẩn.
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457): khuôn dạng body lỗi có cấu trúc.

**Ghi nhớ cốt lõi:** trong Spring MVC, `ResponseEntity<T>` là cách khai báo rõ **status + headers + body** cho từng nhánh REST response. Chọn status theo kết quả thực tế, dùng builder cho header tiêu chuẩn, để converter ghi đúng biểu diễn của `T`, và kiểm chứng đủ cả ba phần bằng HTTP/MockMvc test.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rest-responses-003">@ResponseStatus, phản hồi không có nội dung và trường hợp body null</a>

<details>
<summary>Xem chi tiết</summary>

# @ResponseStatus, phản hồi không có nội dung và trường hợp body null trong Spring MVC

## 1. Mục tiêu

Trong REST API, **HTTP status** và **HTTP response body** là hai yếu tố riêng. Một handler có thể thành công với body JSON, thành công nhưng không có body, hoặc không tìm thấy tài nguyên. Trong Spring MVC, việc trả `null` từ controller **không tự động** có nghĩa là HTTP 204 hay 404. Bài học giải thích cụ thể hành vi này, cách sử dụng `@ResponseStatus` và `ResponseEntity`, cách phân biệt *body vắng mặt* với *giá trị JSON `null`*, cũng như cách kiểm thử kết quả trên dây HTTP.

Sau bài này, bạn có thể:

- Gắn status cố định cho handler bằng `@ResponseStatus` và hiểu giới hạn của `reason`.
- Chọn rõ HTTP 200, 201, 202, 204 và 404 theo tình huống REST.
- Dùng `void`, `ResponseEntity<Void>`, `ResponseEntity.noContent().build()` và `ResponseEntity.notFound().build()` hợp lý.
- Phân biệt `return null`, `ResponseEntity.ok().body(null)`, JSON literal `null`, và property có giá trị null trong JSON object.
- Tránh gửi nội dung cho response 204; viết MockMvc tests xác nhận status và body thực tế.

Ví dụ được thiết kế cho **Spring MVC Servlet stack, Java 17+, Spring Boot 3.x**. Các nguyên tắc HTTP không phụ thuộc riêng Spring. Nếu dự án dùng Spring WebFlux, các API reactive như `Mono` và `ServerResponse` có quy tắc vận hành riêng; không áp dụng máy móc các ví dụ Servlet MVC.

## 2. HTTP status khác với việc body có hay không

Xem các response minh họa:

~~~http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":1,"name":"Spring"}
~~~

~~~http
HTTP/1.1 200 OK

~~~

~~~http
HTTP/1.1 204 No Content

~~~

Response thứ hai có **200 OK, body rỗng**. Response thứ ba có **204 No Content**, tức một thông điệp ngữ nghĩa mạnh hơn: yêu cầu đã được xử lý thành công và không có nội dung response. Hai kết quả không tương đương chỉ vì đều không hiển thị dữ liệu sau header.

| Status | Ý nghĩa thường dùng | Response body |
| --- | --- | --- |
| `200 OK` | Request thành công; có thể trả representation hoặc response rỗng theo contract cụ thể | Có thể có hoặc không |
| `201 Created` | Tài nguyên đã được tạo | Có thể chứa representation; thường thêm `Location` |
| `202 Accepted` | Đã chấp nhận xử lý, chưa nhất thiết xử lý xong | Có thể có thông tin tiến độ hoặc rỗng |
| `204 No Content` | Thành công, không gửi message content | **Không được chứa body** |
| `404 Not Found` | Không tìm thấy tài nguyên hoặc không tiết lộ sự tồn tại | Có thể có body lỗi, tùy thiết kế |
| `304 Not Modified` | Phản hồi điều kiện/cache, không trả representation | Không có message content; không phải thành công upload |
| `205 Reset Content` | Thành công và yêu cầu user agent reset view | Không được tạo content |
  
Theo RFC 9110, response **1xx, 204 và 304 không có message content**; request `HEAD` cũng không có response content dù có thể trả metadata. Đặc biệt **204 không được chứa `Content-Length`**. Không tạo JSON `{}`, `[]` hay `null` rồi tự đặt status 204, vì như vậy gửi cả body vào một trạng thái cấm body.

`200` với body rỗng đôi khi hợp lệ, nhưng nếu endpoint đang hứa trả một object JSON thì nó dễ khiến client nhầm. Khi tài nguyên không tồn tại, API thường nên trả `404` rõ ràng thay vì `200` rỗng; khi delete đã hoàn tất và không trả tài nguyên, `204` thường diễn đạt ý định tốt hơn.

## 3. `@ResponseStatus` đặt status tĩnh cho handler

Annotation `org.springframework.web.bind.annotation.ResponseStatus` có thể được đặt lên một handler method hoặc class exception. Trên handler method, nó khai báo status thường dùng cho mọi kết quả thành công đi theo nhánh method đó.

~~~java
@PostMapping("/orders")
@ResponseStatus(HttpStatus.CREATED)
public OrderView create(@RequestBody CreateOrderRequest request) {
    return new OrderView(42L, request.product());
}
~~~

Đây là **đoạn minh họa bên trong controller**, chưa phải chương trình chạy độc lập. Nếu handler xử lý thành công, response thường có **201 Created** và JSON của `OrderView`. `@ResponseStatus(HttpStatus.CREATED)` tương đương `@ResponseStatus(code = HttpStatus.CREATED)`; thuộc tính `value` là alias của `code`.

Khai báo status cho endpoint không tạo tài nguyên hoặc representation:

~~~java
@DeleteMapping("/orders/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable long id) {
    // Service xóa (hoặc bảo đảm tài nguyên không còn tồn tại).
}
~~~

**Kết quả hợp lý:** 204 với body rỗng, nếu xóa thành công. `void` chỉ là kiểu trả về Java; **204 được chỉ định bởi `@ResponseStatus`**.

Một ví dụ khác:

~~~java
@PostMapping("/jobs")
@ResponseStatus(HttpStatus.ACCEPTED)
public void submitJob(@RequestBody JobRequest job) {
    // Chấp nhận và đẩy job vào hàng đợi để xử lý sau.
}
~~~

Status `202 Accepted` chỉ nên dùng khi hệ thống **thực sự** có cơ chế tiếp nhận công việc bất đồng bộ; status này không bảo đảm đã xử lý xong. Đây cũng là đoạn minh họa, cần DTO, hàng đợi và xử lý lỗi thích hợp.

### Các đặc điểm quan trọng

- `@ResponseStatus` khai báo status **tĩnh**. Nếu cùng một endpoint đôi khi trả 200, đôi khi trả 404 hoặc 201, nên dùng `ResponseEntity`, `ResponseStatusException` hoặc exception handler thích hợp.
- Có thể đặt annotation ở mức controller class, từ đó áp dụng cho những handler phù hợp của class. Tuy nhiên cách đó dễ gán status không hợp lý cho nhiều endpoint; thường ưu tiên gắn trực tiếp từng method.
- Nếu handler ném exception, annotation status thành công trên method không có nghĩa exception vẫn trả status đó. Spring xử lý exception qua các exception resolver.
- Không dùng `@ResponseStatus` như thay thế cho kiểm tra quyền truy cập, validation hoặc transaction; nó chủ yếu là metadata của response.

### Tránh `reason` đối với REST JSON

Ví dụ sau **chỉ để nhận diện rủi ro, không nên áp dụng cho REST endpoint trả JSON**:

~~~java
@ResponseStatus(
        code = HttpStatus.BAD_REQUEST,
        reason = "Invalid request")
~~~

Khi `reason` khác chuỗi rỗng, Spring MVC dùng `HttpServletResponse.sendError(...)`. Nó có thể kích hoạt cơ chế error page của Servlet container hoặc Spring Boot và không cho handler viết response body theo cách thông thường; body cuối có thể là HTML, JSON lỗi hoặc cấu trúc khác tùy error handling. **Không hứa hẹn một JSON body cố định** từ `reason`.

Với API cần body lỗi có cấu trúc rõ, hãy dùng `ResponseEntity`, `ProblemDetail` và/hoặc `@RestControllerAdvice`. Điều này còn giúp client xử lý `status`, `type`, `detail` theo contract đã thống nhất.

### Không trộn tùy tiện `@ResponseStatus` với `ResponseEntity`

`ResponseEntity` cho phép chủ động cung cấp status, headers và body, trong khi `@ResponseStatus` cung cấp status tĩnh. Trong Spring Framework 6.x, status khai báo trên `ResponseEntity` có thể được ghi sau và có hiệu lực so với status annotation. **Không dựa vào thứ tự áp dụng để tạo logic phức tạp.** Với method trả `ResponseEntity`, hãy đặt status ngay trong object đó và bỏ annotation để người đọc hiểu chính xác.

## 4. `void` và response không có nội dung

Xét ba mẫu:

~~~java
// Mẫu A: không đặt status tường minh.
@GetMapping("/ping")
public void ping() {
}

// Mẫu B: đặt 204 bằng @ResponseStatus.
@DeleteMapping("/tasks/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void delete(@PathVariable long id) {
}

// Mẫu C: ResponseEntity thể hiện status và body rỗng.
@GetMapping("/health-ack")
public ResponseEntity<Void> acknowledge() {
    return ResponseEntity.ok().build();
}
~~~

Đây là **các handler minh họa đặt trong `@RestController`**; thiếu import, class và xử lý nghiệp vụ. Trong Spring MVC REST thông thường:

- A: status **200** mặc định, không có body; không tự động là 204.
- B: status **204**, không có body; status 204 đến từ annotation.
- C: status **200**, không có body, do `ResponseEntity.ok().build()` khai báo.

Với yêu cầu xóa thành công không cần response representation, có thể chọn:

~~~java
public ResponseEntity<Void> delete() {
    return ResponseEntity.noContent().build();
}
~~~

Dạng này hữu ích khi cùng handler có các nhánh kết quả khác nhau, nhưng ví dụ chỉ là **phần thân minh họa**; trong controller phải có mapping và xử lý xóa trước khi trả. `ResponseEntity<Void>` cho biết kiểu payload không dự kiến tồn tại. Nếu cần custom headers dù không có body, `ResponseEntity` cũng thuận tiện.

**Không tự suy ra status từ việc không viết body.** Framework nhìn vào annotation, `ResponseEntity`, exception và trạng thái HTTP response. Hãy chủ động khai báo status để giữ REST contract ổn định.

## 5. Body null: bốn trường hợp rất dễ nhầm

### 5.1. `return null` từ handler `@RestController`

~~~java
@GetMapping("/users/{id}/naive")
public UserDto findUser(@PathVariable long id) {
    return null;
}
~~~

Trong Spring MVC với `@RestController`/`@ResponseBody` thông thường, một giá trị trả về `null` **không tạo JSON text `null`**. Nếu không có nơi khác đặt status, response thường vẫn **200 OK** với body rỗng. Đây là một lỗi thiết kế phổ biến khi repository/service báo không tìm thấy bằng `null` mà controller truyền nguyên kết quả về client.

Với `@Controller` kiểu HTML view (không có `@ResponseBody`), `null`/`void` có thể còn tham gia cơ chế chọn view mặc định. Vì vậy luôn xét loại controller và cách Spring xử lý return value trước khi kết luận.

### 5.2. `ResponseEntity.ok().body(null)`

~~~java
@GetMapping("/users/empty")
public ResponseEntity<UserDto> emptyOk() {
    return ResponseEntity.ok().body(null);
}
~~~

Đoạn này cũng thường tạo **200 OK với body vắng mặt**, vì payload trong `ResponseEntity` là `null`. Header có thể khác tùy config; điều quan trọng là không có bốn byte `null` được ghi ra.

`ResponseEntity.ok().build()` diễn đạt “OK không có body” rõ hơn, còn `ResponseEntity.noContent().build()` diễn đạt “No Content” khi contract đòi 204.

**Đừng trả chính object `ResponseEntity` bằng `null`** rồi kỳ vọng client nhận 404. Việc không có `ResponseEntity` đồng nghĩa bạn cũng không khai báo status/headers một cách rõ ràng.

### 5.3. JSON literal `null`

JSON literal `null` là một payload hợp lệ gồm **4 byte UTF-8** (`n`, `u`, `l`, `l`). Đó là **body có nội dung**, khác body có độ dài bằng 0.

~~~http
HTTP/1.1 200 OK
Content-Type: application/json

null
~~~

Nếu contract cố ý yêu cầu một JSON null literal, có thể tạo body `"null"` với media type JSON một cách tường minh:

~~~java
@GetMapping("/literal-null")
public ResponseEntity<String> literalNull() {
    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body("null");
}
~~~

Đây là ví dụ đơn giản về bytes của payload; dùng trong ứng dụng thật **chỉ khi API contract thực sự định nghĩa JSON literal `null`**. Đừng dùng cách này để che việc resource không tồn tại. Với các object JSON thông thường, để Jackson serialize một DTO thật và kiểm thử representation là đủ.

### 5.4. Property null bên trong một JSON object

~~~json
{"id":1,"name":null}
~~~

Ở đây response body **không rỗng**: JSON object tồn tại, chỉ property `name` có giá trị null. Ví dụ với Jackson mặc định của Spring Boot 3.x, DTO có `name == null` thường được ghi thành thuộc tính JSON null. Nếu cấu hình `@JsonInclude(Include.NON_NULL)` hoặc `spring.jackson.default-property-inclusion=non_null`, thuộc tính đó có thể bị lược bỏ:

~~~json
{"id":1}
~~~

Ba dạng `{"name":null}`, `{}` và **không có body** là những contract khác nhau. Client cần kiểm tra status trước, sau đó chỉ parse JSON khi response thực sự có representation hợp lệ.

### Bảng so sánh

| Controller trả về | Status điển hình | Nội dung body |
| --- | --- | --- |
| `return new UserDto(...)` trong `@RestController` | 200 | JSON object |
| `return null` trong `@RestController` | 200 nếu không có status khác | Không có body |
| `ResponseEntity.ok().body(null)` | 200 | Không có body |
| `ResponseEntity.ok().build()` | 200 | Không có body |
| `ResponseEntity.noContent().build()` | 204 | Không có body |
| `ResponseEntity.notFound().build()` | 404 | Không có body |
| `ResponseEntity.ok().contentType(APPLICATION_JSON).body("null")` | 200 | JSON literal `null` |
| DTO có property null | 200 | JSON object có `null` hoặc lược bỏ property tùy Jackson |

Các kết quả trên giả sử **không có filter, advice, interceptor hay servlet response logic tùy chỉnh** thay đổi status/body.

## 6. Khi không tìm thấy tài nguyên: dùng 404, không dựa vào null

Với API lấy resource `GET /users/42`, có thể lấy kết quả kiểu `Optional<UserDto>` từ service và biểu diễn “không tìm thấy” thành 404:

~~~java
@GetMapping("/users/{id}")
public ResponseEntity<UserDto> find(@PathVariable long id) {
    Optional<UserDto> found = service.findById(id);
    return ResponseEntity.of(found);
}
~~~

Đây là code minh họa trong controller có `service` được inject và import `Optional`; không phải file độc lập.

`ResponseEntity.of(Optional<T>)` (trong các phiên bản Spring Framework 6.x) trả 200 cùng body khi `Optional` chứa giá trị, hoặc 404 không có body khi `Optional.empty()`. Cũng có thể viết rõ:

~~~java
UserDto user = service.findByIdOrNull(id);
if (user == null) {
    return ResponseEntity.notFound().build();
}
return ResponseEntity.ok(user);
~~~

Đoạn này cũng chỉ là minh họa, với kiểu trả về `ResponseEntity<UserDto>` và `service` có method thích hợp.

Nếu “không tìm thấy” là ngoại lệ nghiệp vụ, có thể ném:

~~~java
throw new ResponseStatusException(
        HttpStatus.NOT_FOUND, "User does not exist");
~~~

Spring MVC sẽ xử lý thành **404** nếu không có handler khác override; body lỗi phụ thuộc cấu hình Spring Boot, Problem Details và các exception handlers. Đừng giả định luôn có hoặc luôn không có JSON body.

## 7. Ví dụ Spring Boot 3.x chạy được

Tạo project bằng Spring Initializr: **Java 17+**, **Maven**, **Spring Boot 3.x**, dependencies `spring-boot-starter-web` và `spring-boot-starter-test`. Các class dưới đây ở package `com.example.demo`. Ứng dụng minh họa dữ liệu cố định để status có thể kiểm thử xác định; nó **không lưu/xóa dữ liệu thật** và không có Spring Security.

Cấu trúc:

~~~text
src/main/java/com/example/demo/DemoApplication.java
src/main/java/com/example/demo/StatusController.java
src/test/java/com/example/demo/StatusControllerTest.java
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

### 7.2. `StatusController.java`

~~~java
package com.example.demo;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/items")
public class StatusController {

    // Nguồn dữ liệu giả lập: chỉ id=1 tồn tại.
    private Item find(long id) {
        return id == 1L ? new Item(1L, "Spring") : null;
    }

    // Tình huống cố ý chưa tốt: không tìm thấy -> 200 với body rỗng.
    @GetMapping("/{id}")
    public Item naiveFind(@PathVariable long id) {
        return find(id);
    }

    // Contract rõ: có tài nguyên -> 200; không có -> 404.
    @GetMapping("/{id}/checked")
    public ResponseEntity<Item> checkedFind(@PathVariable long id) {
        Item item = find(id);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }

    // Annotation đặt status cố định: tạo thành công -> 201 và body.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Item create(@RequestBody ItemRequest request) {
        return new Item(2L, request.name());
    }

    // Tương đương hướng tạo có Location, linh hoạt bằng ResponseEntity.
    @PostMapping("/with-location")
    public ResponseEntity<Item> createWithLocation(
            @RequestBody ItemRequest request) {
        Item created = new Item(2L, request.name());
        return ResponseEntity
                .created(URI.create("/api/items/2"))
                .body(created);
    }

    // Xóa giả lập thành công -> 204, body rỗng.
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        // Demo không có repository; production phải xử lý nghiệp vụ.
    }

    // Không có body vẫn có thể là 200.
    @GetMapping("/empty-ok")
    public ResponseEntity<Void> emptyOk() {
        return ResponseEntity.ok().build();
    }

    // null payload trong ResponseEntity -> 200 với body rỗng.
    @GetMapping("/null-body")
    public ResponseEntity<Item> nullBody() {
        return ResponseEntity.ok().body(null);
    }

    // Cố ý gửi JSON literal null, khác body rỗng.
    @GetMapping("/literal-null")
    public ResponseEntity<String> literalNull() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body("null");
    }

    // JSON object có property null.
    @GetMapping("/null-property")
    public ItemWithNullableProperty nullProperty() {
        return new ItemWithNullableProperty(1L, null);
    }

    // Một nhánh 404 qua exception thay cho ResponseEntity.
    @GetMapping("/{id}/required")
    public Item required(@PathVariable long id) {
        Item item = find(id);
        if (item == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Item does not exist");
        }
        return item;
    }

    public record Item(long id, String name) {}
    public record ItemRequest(String name) {}
    public record ItemWithNullableProperty(long id, String name) {}
}
~~~

**Điểm cần hiểu:**

- `find(1)` tạo DTO; `find(99)` trả `null`. Đây là dữ liệu giả để minh họa, không phải repository thật.
- `GET /api/items/99` cố tình trả `null` từ `@RestController`, minh họa **200 rỗng dù tài nguyên không tồn tại**. Không sử dụng mẫu naive này làm API production.
- `GET /api/items/99/checked` chủ động trả **404**. Với id 1, method trả DTO **200 JSON**.
- `POST /api/items` dùng `@ResponseStatus(CREATED)` để đặt **201** cố định. `POST /api/items/with-location` dùng `ResponseEntity.created(...)` để đặt **201** và header `Location: /api/items/2`.
- `DELETE /api/items/1` là demo no-op: response **204** do annotation. Trong dự án thật, chỉ trả thành công sau khi service xử lý đúng; trường hợp xóa id không tồn tại có thể quyết định 204 hoặc 404 theo hợp đồng API.
- `GET /api/items/empty-ok` và `GET /api/items/null-body` đều có **200, không có body**. `GET /api/items/literal-null` có **200 với body bốn byte `null`**.
- `GET /api/items/null-property` trả object; với Jackson mặc định Boot 3.x, JSON có property `name` mang null.
- `GET /api/items/99/required` dùng `ResponseStatusException` để phản hồi **404**; nội dung lỗi tùy thuộc cấu hình.

**Đây là bài dạy về response contract.** Ví dụ tạo/xóa chỉ minh họa status, không giả lập tính đúng đắn của database, giao dịch hay quyền truy cập.

## 8. Gọi API với curl và đọc response thực tế

Chạy trong thư mục có `pom.xml`:

~~~bash
./mvnw spring-boot:run
~~~

Trên Windows dùng `mvnw.cmd spring-boot:run`; nếu không có Maven Wrapper, chạy `mvn spring-boot:run` sau khi cài Maven.

### 8.1. Xem status 200 có body và 200 không body

~~~bash
curl -i http://localhost:8080/api/items/1
curl -i http://localhost:8080/api/items/99
curl -i http://localhost:8080/api/items/empty-ok
curl -i http://localhost:8080/api/items/null-body
~~~

Phản hồi đầu là **200** với JSON:

~~~json
{"id":1,"name":"Spring"}
~~~

Ba phản hồi tiếp theo đều là **200** với body rỗng trong cấu hình mẫu. Đây là bằng chứng quan trọng rằng status 200 **không nói lên** body có JSON hay không.

### 8.2. Xem 404 và 204 rõ ràng

~~~bash
curl -i http://localhost:8080/api/items/99/checked
curl -i -X DELETE http://localhost:8080/api/items/1
~~~

Dòng đầu trả **404 Not Found** với body rỗng do `ResponseEntity.notFound().build()`. Dòng thứ hai trả **204 No Content** mà không gửi body. Đừng thử `response.json()` trên fetch response 204 vì không có JSON để parse.

### 8.3. Status 201 khi tạo resource

~~~bash
curl -i -X POST "http://localhost:8080/api/items"   -H "Content-Type: application/json"   --data-binary '{"name":"MVC"}'
~~~

Status **201 Created**, body:

~~~json
{"id":2,"name":"MVC"}
~~~

Nếu dùng `/api/items/with-location`, status cũng **201**, cộng header `Location: /api/items/2`. URI trong ví dụ là relative URI hợp lệ trong header Location; service thực tế cần trỏ tới resource có thể truy cập, không trả một link giả.

### 8.4. So sánh JSON `null` và body rỗng

~~~bash
curl -i http://localhost:8080/api/items/literal-null
curl -i http://localhost:8080/api/items/null-property
~~~

Kết quả có nội dung lần lượt là:

~~~json
null
~~~

~~~json
{"id":1,"name":null}
~~~

Khác với `/api/items/null-body`, cả hai response trên có **JSON payload thực sự**. Có thể xem bằng `curl -i` hoặc tab Network trong trình duyệt, nhưng không kết luận chỉ qua việc terminal hiển thị trắng vì có thể còn các byte newline/whitespace. Khi cần kiểm chứng chặt, test `MockMvc` với `content().string("")` và `content().string("null")`.

Các lệnh multiline dùng ký tự `\` của Linux/macOS/Git Bash. Trong PowerShell, dùng `curl.exe` và viết tham số trên một dòng hoặc thay bằng cú pháp nối dòng đúng của PowerShell; cách quote chuỗi JSON cần điều chỉnh theo shell.

## 9. Kiểm thử MockMvc: so đồng thời status, body và Location

Bổ sung `spring-boot-starter-test`. Tạo **`src/test/java/com/example/demo/StatusControllerTest.java`**:

~~~java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void successReturns200WithJson() throws Exception {
        mockMvc.perform(get("/api/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Spring"));
    }

    @Test
    void naiveMissingResourceIs200WithEmptyBody() throws Exception {
        mockMvc.perform(get("/api/items/99"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void missingResourceHandledExplicitlyIs404() throws Exception {
        mockMvc.perform(get("/api/items/99/checked"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void deleteReturns204WithoutBody() throws Exception {
        mockMvc.perform(delete("/api/items/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void createReturns201AndJson() throws Exception {
        mockMvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MVC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("MVC"));
    }

    @Test
    void createWithLocationReturns201AndLocation() throws Exception {
        mockMvc.perform(post("/api/items/with-location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MVC\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.LOCATION, "/api/items/2"))
                .andExpect(jsonPath("$.name").value("MVC"));
    }

    @Test
    void emptyOkAndNullBodyRemain200WithNoBody() throws Exception {
        mockMvc.perform(get("/api/items/empty-ok"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/items/null-body"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void literalNullIsFourCharactersNotEmptyBody() throws Exception {
        mockMvc.perform(get("/api/items/literal-null"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(content().string("null"));
    }

    @Test
    void nullPropertyIsStillAJsonObject() throws Exception {
        mockMvc.perform(get("/api/items/null-property"))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"id\":1,\"name\":null}"));
    }

    @Test
    void responseStatusExceptionMapsTo404() throws Exception {
        mockMvc.perform(get("/api/items/99/required"))
                .andExpect(status().isNotFound());
    }
}
~~~

Chạy:

~~~bash
./mvnw test
~~~

Các test kiểm tra cả **status** và **body**; nếu chỉ assert status 200, rất dễ bỏ sót endpoint cần trả JSON nhưng lại trả rỗng. Test `literalNullIsFourCharactersNotEmptyBody` trực tiếp phân biệt payload `null` (4 ký tự ASCII) với chuỗi rỗng. Test có `@WebMvcTest` chỉ dựng MVC test slice với controller này, không mở server thật.

Test `responseStatusExceptionMapsTo404` chỉ xác nhận mã 404 vì body lỗi của Spring Boot phụ thuộc auto-configuration và tính năng Problem Details; test không giả định thông điệp lỗi sẽ xuất hiện trong JSON cụ thể.

## 10. `@ResponseStatus` đặt trên lớp exception và xử lý lỗi REST

Bạn có thể định nghĩa exception mang status cố định:

~~~java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
~~~

Đoạn này là **class Java minh họa**, cần import `ResponseStatus`, `HttpStatus`; và file `ResourceNotFoundException.java` riêng. Khi handler ném exception, `ResponseStatusExceptionResolver` có thể xử lý thành 404. Nhưng `@ResponseStatus` **trên exception class** sử dụng cơ chế `sendError`; server hoặc error controller có thể tạo body lỗi. Vì vậy không nên suy ra “404 luôn trống” hay “404 luôn có cùng JSON”.

Nếu muốn JSON có cấu trúc nhất quán, chọn xử lý rõ:

~~~java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            ResourceNotFoundException ex) {

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND, "Resource not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
~~~

Đây là **class minh họa riêng**, cần import `RestControllerAdvice`, `ExceptionHandler`, `ResponseEntity`, `ProblemDetail`, `HttpStatus` và exception tương ứng. Spring Framework 6.x hỗ trợ `ProblemDetail` biểu diễn thông tin lỗi theo RFC 9457. Không gửi stack trace, dữ liệu nội bộ hoặc message nhạy cảm vào response chỉ vì exception có thông tin đó.

Khi xử lý status động, lựa chọn giữa `ResponseEntity`, `ResponseStatusException` và `@ExceptionHandler` phụ thuộc thiết kế API:
- Nếu muốn nhánh local rõ ngay trong handler, `ResponseEntity` thường đơn giản.
- Nếu thiếu resource là ngoại lệ ứng dụng, có thể ném exception và xử lý tập trung.
- Nếu chỉ cần status tĩnh của một hành động thành công, `@ResponseStatus` trên method ngắn gọn.

## 11. Response 204 trong HTTP client và frontend

Ví dụ một frontend gọi DELETE:

~~~javascript
async function deleteItem(id) {
  const response = await fetch("/api/items/" + id, {
    method: "DELETE"
  });

  if (response.status === 204) {
    return; // Không gọi response.json(): không có body.
  }
  if (response.status === 404) {
    throw new Error("Item does not exist");
  }
  if (!response.ok) {
    throw new Error("Delete failed: " + response.status);
  }
}
~~~

Không phải mọi response có status 2xx đều chứa JSON; `response.ok` trong Fetch chỉ kiểm tra phạm vi status 200–299. `response.json()` trên body rỗng có thể ném `SyntaxError`, kể cả request thành công. Client có thể thiết kế logic theo API contract: 204 thì không parse; 200 có body JSON thì parse; 404 xử lý không tồn tại.

Nếu API contract cho phép `200` với body rỗng, frontend phải kiểm tra nội dung trước khi parse (hoặc tốt hơn là thống nhất contract để loại bỏ mơ hồ). Không dựa hoàn toàn vào header `Content-Length`, bởi HTTP có nhiều cách framing response và header đó có thể vắng mặt dù body tồn tại.

Một số client API dùng kiểu `Optional<T>` hoặc `T | null` để biểu diễn “không tồn tại” ở tầng application. **Không mặc nhiên** map các kiểu đó sang 200 empty response; phía HTTP nên quyết định semantics 404/204/200 rõ ràng.

## 12. Lựa chọn status khi update, delete, create

| Tình huống REST | Status được dùng phổ biến | Cách Spring MVC |
| --- | --- | --- |
| GET có tài nguyên | 200 kèm JSON | Trả DTO hoặc `ResponseEntity.ok(dto)` |
| GET không tìm thấy | 404 | `ResponseEntity.notFound().build()` |
| POST tạo tài nguyên ngay | 201 | `@ResponseStatus(CREATED)` hoặc `ResponseEntity.created(uri).body(dto)` |
| POST nhận job chờ xử lý | 202 | `ResponseEntity.accepted().build()` hoặc trả thông tin job |
| PUT/PATCH cập nhật và trả object | 200 kèm JSON | `ResponseEntity.ok(dto)` |
| PUT/PATCH cập nhật, không trả object | 204 | `ResponseEntity.noContent().build()` |
| DELETE thành công, không trả object | 204 | `@ResponseStatus(NO_CONTENT) void` hoặc `noContent().build()` |
| DELETE tài nguyên vắng mặt | Theo contract: 404 hoặc 204 idempotent | Viết nhánh có chủ đích, kiểm thử |

**Idempotency** của DELETE nghĩa là lặp lại cùng thao tác có hiệu ứng trạng thái mong muốn như lần đầu, **không** bắt buộc mọi lần gọi phải trả cùng status. Một API có thể trả 204 khi xóa lần đầu và 404 khi xóa lại; miễn hợp đồng thể hiện rõ và ổn định. HTTP 204 có thể kèm metadata hợp lệ như ETag trong một số ngữ cảnh nhưng không được kèm body.

201 thường nên kèm `Location` tới resource mới, nhất là khi server cấp ID. Trong ví dụ demo, dữ liệu không được lưu thật; dự án thực tế phải tạo resource rồi cung cấp URI hoạt động.

## 13. Những lỗi thường gặp

### Lỗi A: Trả `null` và tưởng Spring tự chọn 404

~~~java
@GetMapping("/{id}")
public UserDto get(@PathVariable long id) {
    return service.findByIdOrNull(id);
}
~~~

Nếu service trả `null`, `@RestController` có thể tạo **200 rỗng**. **Cách sửa:** chuyển sang `ResponseEntity.notFound().build()` hoặc exception 404 có chủ đích.

### Lỗi B: Thêm `@ResponseStatus(NO_CONTENT)` nhưng vẫn trả dữ liệu

~~~java
@ResponseStatus(HttpStatus.NO_CONTENT)
@GetMapping("/value")
public UserDto value() {
    return new UserDto(1L, "Lan");
}
~~~

Đây là đoạn minh họa lỗi, không phải code để chạy: HTTP 204 **không được chứa message content**. Việc response có cố ghi/loại bỏ body ra sao tùy lớp server nhưng thiết kế này là sai contract. **Cách sửa:** trả `void`/không body với 204 hoặc dùng 200 và dữ liệu thực.

### Lỗi C: Dùng `@ResponseStatus(reason="...")` làm JSON error

`reason` đi qua `sendError`, có thể dẫn tới trang lỗi hoặc response khác dự kiến. **Cách sửa:** dùng `ResponseEntity<ProblemDetail>`, `@RestControllerAdvice` và kiểm thử response JSON lỗi.

### Lỗi D: Nhầm `200 + body rỗng` với JSON `null`

HTTP 200 với body rỗng có **0 byte**. JSON literal `null` có **4 byte**; body `{"data":null}` còn dài hơn. **Cách sửa:** test `content().string("")` hoặc `content().string("null")`, không chỉ nhìn status.

### Lỗi E: Gọi `response.json()` sau DELETE 204

Frontend ném lỗi parse JSON dù HTTP request thành công. **Cách sửa:** kiểm tra status 204 và tránh parse payload.

### Lỗi F: Trả `ResponseEntity.ok().body(null)` khi không tìm thấy

API báo thành công 200, không cho client phân biệt thiếu resource. **Cách sửa:** trả 404 nếu API xác định GET không có tài nguyên.

### Lỗi G: Trộn status tĩnh và status động

Method vừa có `@ResponseStatus` vừa trả `ResponseEntity` với status khác làm code khó hiểu và phụ thuộc cơ chế xử lý return value. **Cách sửa:** dùng một cơ chế làm nguồn chân lý cho status của endpoint.

### Lỗi H: Test chỉ kiểm tra status

Test chỉ `isOk()` vẫn PASS dù JSON body bị mất. **Cách sửa:** kiểm tra JSON schema, property chính, `Content-Type`, body rỗng hay `Location` tương ứng.

## 14. Tự kiểm tra kiến thức

**Câu 1.** Controller trả `null` cho `@GetMapping` trong `@RestController`; API có tự trả 204 không?

**Trả lời:** Không. Trong cấu hình MVC thông thường, nếu không đặt status khác, response thường là **200 với body rỗng**.

**Câu 2.** Có thể dùng `@ResponseStatus(HttpStatus.NO_CONTENT)` cho method trả một JSON object không?

**Trả lời:** Không nên: 204 cấm message content. Muốn trả JSON object hãy dùng 200 hoặc 201 phù hợp. Muốn 204, bỏ body.

**Câu 3.** `ResponseEntity.ok().body(null)` khác `ResponseEntity.ok().contentType(APPLICATION_JSON).body("null")` như thế nào?

**Trả lời:** Cái đầu có 200 với body vắng mặt. Cái sau có 200 với payload JSON literal `null` dài bốn byte, nếu dùng String converter tiêu chuẩn.

**Câu 4.** Đâu là khác biệt giữa `@ResponseStatus(CREATED)` và `ResponseEntity.created(location)`?

**Trả lời:** Annotation đặt status 201 cố định cho handler; `ResponseEntity.created(...)` đồng thời biểu diễn status 201 và header `Location`, có thể kèm body.

**Câu 5.** Tại sao test 204 cần kiểm tra body?

**Trả lời:** Vì status và payload là hai khía cạnh riêng; cần chắc chắn API không cố trả dữ liệu trong trạng thái cấm body và client không parse JSON sau 204.

## 15. Checklist triển khai

1. Định nghĩa trước contract của mỗi endpoint: status thành công, status không tìm thấy, có body hay không, và shape JSON nếu có.
2. Dùng `@ResponseStatus` trên handler khi status thành công là cố định; dùng `ResponseEntity` khi phải quyết định status/headers/body theo kết quả.
3. Với không có nội dung: đặt `204` tường minh và không tạo payload. Với `200` rỗng: chỉ dùng nếu contract thật sự cần.
4. Với dữ liệu không tồn tại: không để `null` trôi ra ngoài `@RestController` rồi biến thành 200 không rõ nghĩa.
5. Phân biệt body không tồn tại, JSON literal `null` và property null trong JSON; đối chiếu cấu hình Jackson.
6. Dùng `@RestControllerAdvice`/`ProblemDetail` khi cần chuẩn hóa lỗi; tránh `reason` của `@ResponseStatus` cho REST JSON.
7. Kiểm thử **status + body + headers** và các nhánh bất thường (404, 204, parse JSON rỗng ở frontend).

## Tham khảo chính thức

- [Spring Framework 6.2 — Controller Return Values](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/return-types.html)
- [Spring Framework 6.2 — ResponseEntity](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/responseentity.html)
- [Spring Framework — @ResponseBody](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-methods/responsebody.html)
- [Spring Framework 6.2 — @ResponseStatus Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/bind/annotation/ResponseStatus.html)
- [Spring Framework — REST Error Responses](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-ann-rest-exceptions.html)
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rest-responses-004">Tạo tài nguyên với 201 Created và header Location</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo tài nguyên với HTTP 201 Created và header Location trong Spring MVC

## 1. Tình huống: một yêu cầu POST tạo ra tài nguyên mới

Giả sử một ứng dụng REST quản lý sách. Client gửi:

```http
POST /api/books HTTP/1.1
Host: api.example.com
Content-Type: application/json
Accept: application/json

{"title":"Spring MVC thực hành"}
```

Server kiểm tra dữ liệu, tạo bản ghi với ID do server cấp (ví dụ `1001`) và phản hồi:

```http
HTTP/1.1 201 Created
Location: /api/books/1001
Content-Type: application/json

{"id":1001,"title":"Spring MVC thực hành"}
```

Phản hồi này truyền đạt **ba điều độc lập nhưng bổ trợ nhau**:

1. **`201 Created`**: HTTP request đã hoàn tất và dẫn đến một hay nhiều tài nguyên mới được tạo.
2. **`Location: /api/books/1001`**: URI của **tài nguyên chính** được tạo bởi request; client có thể dùng URI này trong một request mới nếu server hỗ trợ truy xuất ở URI đó.
3. **JSON response body**: biểu diễn hoặc thông tin tóm tắt tài nguyên mới, chẳng hạn ID và tiêu đề. Body **có thể có hoặc không**; `201` không bắt buộc JSON body.

Một client có thể tiếp tục:

```http
GET /api/books/1001 HTTP/1.1
Host: api.example.com
Accept: application/json
```

Nếu API hỗ trợ GET tài nguyên đó và tài nguyên vẫn tồn tại, phản hồi thường là `200 OK` với biểu diễn tương ứng. **Đừng suy ra rằng mọi `Location` luôn là URL GET được phép truy cập**: quyền, trạng thái hoặc contract riêng có thể giới hạn; API cần công bố rõ điều này.

Mục tiêu bài: hiểu ngữ nghĩa HTTP chuẩn, chọn response Spring MVC thích hợp, tạo URI đúng, bảo vệ hợp đồng REST và viết kiểm thử xác nhận HTTP status/header/body.

## 2. Ngữ nghĩa chính xác theo RFC 9110

RFC 9110, mục **15.3.2 — 201 Created**, mô tả trạng thái này khi request đã thành công và tạo **một hoặc nhiều tài nguyên**. Tài nguyên chính được xác định bởi header `Location` nếu hiện diện; nếu không, tài nguyên chính được xác định bởi **target URI** của request.

Điều đó kéo theo các nguyên tắc:

- `201` là phản hồi **thành công** (nhóm 2xx), **không phải chuyển hướng** (nhóm 3xx). HTTP client **không tự động GET `Location` chỉ vì status là 201**.
- `Location` nên trỏ tới tài nguyên chính được tạo, ví dụ `/api/books/1001`, không phải luôn trỏ về collection nhận POST `/api/books`.
- `Location` có thể chứa **URI tuyệt đối** (`https://api.example.com/api/books/1001`) hoặc **relative URI reference** (`/api/books/1001`). RFC 9110 cho phép URI-reference; client resolve relative Location so với URI request.
- `201` **không đòi hỏi** có body; body nếu có thường mô tả kết quả hành động hoặc tài nguyên mới. Khi muốn gửi JSON, phải đảm bảo serialization và `Content-Type` tương ứng.
- Server nên tạo URI có ý nghĩa bền vững, không làm client phải suy diễn vị trí từ một ID trả riêng trong body.
- Nếu POST tạo nhiều tài nguyên, `Location` chỉ đại diện **primary resource**; body có thể liệt kê những tài nguyên khác theo hợp đồng đã công bố.
- Header `Location` không phải cam kết rằng mọi thao tác với tài nguyên đều được cấp quyền hoặc rằng tài nguyên luôn tồn tại mãi mãi.

**Ví dụ phản hồi 201 không có body hợp lệ:**

```http
HTTP/1.1 201 Created
Location: /api/books/1001
Content-Length: 0
```

Tuy nhiên, với `POST /api/books`, **nên trả `Location`** vì client cần biết URI tài nguyên mới. Nếu bỏ Location, fallback của chuẩn sang target URI `/api/books` thường không phản ánh mục tiêu định danh bản ghi con theo thiết kế REST thông dụng. Đây là lý do header này có giá trị dù status 201 về mặt cú pháp không bắt buộc luôn có Location.

### `Location` khác `Content-Location`

| Header | Trả lời câu hỏi | Trong một 201 thông dụng |
| --- | --- | --- |
| `Location` | “URI tài nguyên chính được tạo ở đâu?” | `/api/books/1001` |
| `Content-Location` | “Biểu diễn trong **body hiện tại** tương ứng với URI nào?” | Có thể là `/api/books/1001` nếu body là biểu diễn hiện tại của tài nguyên |

Theo RFC 9110, với **201**, khi `Content-Location` trùng `Location`, thông điệp mang biểu diễn hiện tại của tài nguyên được tạo. Không cần tự thêm `Content-Location` cho mọi 201; chỉ dùng khi ý nghĩa representation và cache/content negotiation yêu cầu.

Đừng nhầm `Location` của **201** với `Location` của **301/302/303/307/308**: ở phản hồi 3xx, Location chỉ mục tiêu redirect; còn 201 không kích hoạt redirect.

## 3. Chọn đúng HTTP status cho thao tác tạo/sửa

| Hoạt động đã xảy ra | Mã phù hợp | Ý nghĩa |
| --- | --- | --- |
| POST tạo thành công tài nguyên mới | **201 Created** | Công việc tạo đã hoàn tất; thường kèm Location |
| POST thực hiện hành động nhưng không tạo tài nguyên mới | **200 OK** hoặc **204 No Content** | Theo hợp đồng và có body hay không |
| POST tiếp nhận yêu cầu, đang xử lý bất đồng bộ, chưa hoàn tất | **202 Accepted** | Đã nhận xử lý, chưa bảo đảm tạo thành công |
| PUT tại URI chưa tồn tại, tạo tài nguyên thành công | **201 Created** | Tạo tài nguyên tại target URI |
| PUT thay thế/sửa tài nguyên đã tồn tại | **200 OK** hoặc **204 No Content** | Tùy có trả nội dung |
| Tài nguyên không được tạo vì lỗi dữ liệu | **400 Bad Request** hoặc **422 Unprocessable Content** | Chọn theo cách API phân loại lỗi |
| Tạo xung đột với tài nguyên/trạng thái hiện có | **409 Conflict** | Ví dụ unique key trùng |
| Client thiếu xác thực hoặc không đủ quyền | **401 Unauthorized** / **403 Forbidden** | Không coi là tạo thành công |
| Tạo xong nhưng muốn điều hướng client GET tài nguyên khác | **303 See Other**, tùy use case | Đây là một lựa chọn redirect có chủ đích, khác với 201 |

**Đặc biệt với PUT:** RFC 9110 yêu cầu nếu PUT thành công và **trước đó không có biểu diễn hiện tại của target resource** thì server báo **201 Created**. Nếu target đã có và được cập nhật, thường dùng 200/204. Không đánh đồng mọi HTTP PUT với 200 hoặc mọi HTTP POST với 201.

**Với xử lý bất đồng bộ:** chỉ trả 201 khi việc tạo tài nguyên đã được xác nhận hoàn tất theo hợp đồng. Nếu request mới chỉ được xếp hàng và có thể thất bại sau đó, thông thường trả 202 cùng cơ chế kiểm tra trạng thái job. `Location` trong 202 (nếu có) có thể trỏ tới tài nguyên trạng thái xử lý; không ngầm hiểu nó đã là tài nguyên kết quả như trong 201.

## 4. Spring MVC có API nào để trả 201 + Location?

Cách thông dụng, rõ ràng là **`ResponseEntity.created(URI location)`** thuộc `org.springframework.http.ResponseEntity`:

```java
URI location = URI.create("/api/books/1001");

return ResponseEntity
        .created(location)
        .body(new BookResponse(1001L, "Spring MVC thực hành"));
```

Đây là **fragment trong method controller**. Khi gọi, builder tạo:

- `HttpStatus.CREATED` = 201.
- Header `Location` từ đối tượng `java.net.URI` đã truyền.
- Body được serialize bằng `HttpMessageConverter` thích hợp, ví dụ Jackson JSON.

Builder này tồn tại trong Spring Framework 5, 6 và 7; đây là API ổn định nên ưu tiên thay vì tự thêm status/header mỗi lần.

Các lựa chọn hợp lệ khác:

```java
// Chỉ trả status 201 + Location; không trả body:
return ResponseEntity.created(location).build();

// Tự chỉ định status và header:
return ResponseEntity.status(HttpStatus.CREATED)
        .location(location)
        .body(response);
```

Với method trả `ResponseEntity<BookResponse>`, hai đoạn trên là **phương án thay thế**, không đặt chung một method. Đoạn `build()` dùng khi API công bố 201 không có body.

**Chú ý `@ResponseStatus(HttpStatus.CREATED)`**:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public BookResponse create(@RequestBody BookRequest body) {
    return service.create(body);
}
```

Annotation này đặt **status 201**, **không tự suy ra URI hoặc header `Location`**. Nếu cần Location, bạn phải set header bằng cách khác hoặc trả `ResponseEntity.created(uri)`. Cũng không dùng `ResponseEntity.ok(...)` sau khi tạo, trừ khi contract chủ động quy định 200.

## 5. Ví dụ Spring Boot 3.x + Java 17 hoàn chỉnh

Ví dụ API sau có **POST tạo sách** và **GET theo ID** để kiểm chứng rằng `Location` thực sự trỏ đến một tài nguyên mới. Lưu trữ bằng `ConcurrentHashMap` trong bộ nhớ cho mục đích học tập; một ứng dụng production cần repository/database và transaction đúng.

Baseline:

- Java 17 trở lên.
- Spring Boot 3.x, Spring Framework 6.x, Spring MVC Servlet.
- Spring Boot starters: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test`.
- Namespace `jakarta.validation.*` (Boot 3), Jackson được starter web cấu hình.

### 5.1. Dependencies

Trong dự án Gradle đã có Boot plugin, repository, Java toolchain:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Đoạn trên là **fragment `build.gradle`** chứ không phải toàn bộ file Gradle độc lập. Với Maven, dùng các starter tương ứng do Spring Boot BOM quản lý.

### 5.2. Controller tạo và đọc sách

Tạo file **`src/main/java/demo/created/CreatedApplication.java`**:

```java
package demo.created;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
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
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class CreatedApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreatedApplication.class, args);
    }
}

record CreateBookRequest(@NotBlank String title) {}

record BookResponse(long id, String title) {}

@RestController
@RequestMapping("/api/books")
class BookController {

    private final AtomicLong nextId = new AtomicLong(1000);
    private final ConcurrentMap<Long, BookResponse> books =
            new ConcurrentHashMap<>();

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BookResponse> create(
            @Valid @RequestBody CreateBookRequest request) {

        long id = nextId.incrementAndGet();
        BookResponse saved = new BookResponse(id, request.title());
        books.put(id, saved);

        URI location = URI.create("/api/books/" + id);
        return ResponseEntity.created(location).body(saved);
    }

    @GetMapping(
            path = "/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    BookResponse findById(@PathVariable long id) {
        BookResponse result = books.get(id);
        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Book not found");
        }
        return result;
    }
}
```

Đây là **một tệp Java hợp lệ** với đúng một public class (`CreatedApplication`). Hai record và controller không có `public` nên có thể khai báo cùng tệp; Spring Boot component scan từ package `demo.created` sẽ nhận controller.

Các quyết định trong ví dụ:

1. `@NotBlank` và `@Valid` ngăn POST với `title=null`, `""` hoặc chỉ có khoảng trắng. Đây là validation cú pháp/đầu vào, không phải toàn bộ business rules.
2. `AtomicLong` cung cấp ID khác nhau trong một JVM khi nhiều request đồng thời; sau restart ID có thể lặp vì ví dụ chỉ lưu trong bộ nhớ.
3. `books.put(id, saved)` ghi tài nguyên **trước khi trả 201**. Trạng thái 201 chỉ có ý nghĩa khi server thực sự xử lý tạo thành công theo mô hình đang dùng.
4. `URI.create("/api/books/" + id)` xây **Location tương đối theo root của origin**. Vì ID là số server sinh, không có dữ liệu chuỗi chưa encode chèn trực tiếp vào URI.
5. `ResponseEntity.created(location).body(saved)` trả đúng `201` + `Location` + JSON DTO. API không trả JPA Entity nội bộ.
6. `GET /api/books/{id}` trả 200 nếu có sách, ném `ResponseStatusException(404)` nếu không tồn tại. Client có thể dựa vào Location để truy vấn.
7. Ví dụ giả định web app triển khai ở **root context path**; nếu chạy dưới `/myapp` hoặc sau gateway đổi prefix, phải điều chỉnh chính sách xây URI ở phần 8.

**Không xem ConcurrentHashMap là database:** không có persistence, transaction, uniqueness toàn hệ thống, audit, backup hoặc chia sẻ state giữa nhiều instance. Nếu dùng DB, cần lưu transactionally rồi xây Location dựa trên ID thực tế.

### 5.3. Chạy và thử với curl

Khởi động project Gradle:

```bash
./gradlew bootRun
```

Gửi POST:

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"title":"Spring MVC thực hành"}'
```

Trong ứng dụng demo mới khởi động và chưa có POST nào khác, phản hồi điển hình:

```http
HTTP/1.1 201
Location: /api/books/1001
Content-Type: application/json

{"id":1001,"title":"Spring MVC thực hành"}
```

Header khác như `Date`, `Connection`, `Content-Length` hoặc charset được bỏ khỏi ví dụ. **ID 1001 chỉ đúng nếu chưa có request tạo trước đó**; thứ tự field trong JSON không phải cam kết. Thực tế cần lấy Location từ phản hồi, không đoán ID.

Đọc tài nguyên:

```bash
curl -i 'http://localhost:8080/api/books/1001'
```

Kết quả thông thường:

```http
HTTP/1.1 200
Content-Type: application/json

{"id":1001,"title":"Spring MVC thực hành"}
```

Kiểm tra body không hợp lệ:

```bash
curl -i -X POST 'http://localhost:8080/api/books' \
  -H 'Content-Type: application/json' \
  -d '{"title":"   "}'
```

Thông thường nhận **400 Bad Request** do `@NotBlank`; không có sách nào được tạo và **không trả Location mới**. JSON error body cụ thể phụ thuộc `@ControllerAdvice`/Boot error configuration và không phải một phần cố định của ví dụ.

Nếu dùng Postman, Bruno hoặc fetch trong browser, cần kiểm tra trực tiếp **status và response header Location**. Một `201` không tự động làm công cụ điều hướng sang trang mới.

## 6. Kiểm thử đúng contract POST → 201 + Location → GET

Kiểm thử cần xác nhận đồng thời:

- POST dữ liệu hợp lệ trả `201 Created`.
- Có header `Location` đúng dạng.
- Body chứa dữ liệu đã lưu và ID do server tạo.
- GET tới URI nhận từ Location trả tài nguyên mới.
- POST không hợp lệ trả lỗi và **không báo thành công**.

Trong dự án Boot 3 ở phần 5, tạo **`src/test/java/demo/created/CreatedApplicationTests.java`**:

```java
package demo.created;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CreatedApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void createsResourceAndCanRetrieveAtLocation() throws Exception {
        MvcResult created = mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Spring MVC"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Spring MVC"))
            .andReturn();

        String location =
                created.getResponse().getHeader(HttpHeaders.LOCATION);
        assertNotNull(location);
        assertTrue(location.matches("/api/books/[0-9]+"));

        mvc.perform(get(location).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Spring MVC"));
    }

    @Test
    void invalidInputDoesNotCreateResource() throws Exception {
        MvcResult invalid = mvc.perform(post("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"   "}
                    """))
            .andExpect(status().isBadRequest())
            .andReturn();

        String location =
                invalid.getResponse().getHeader(HttpHeaders.LOCATION);
        org.junit.jupiter.api.Assertions.assertNull(location);
    }

    @Test
    void absentResourceReturns404() throws Exception {
        mvc.perform(get("/api/books/9223372036854775807"))
            .andExpect(status().isNotFound());
    }
}
```

Chạy:

```bash
./gradlew test
```

Với bản demo dùng Boot 3, `spring-boot-starter-test` và `spring-boot-starter-validation` như mô tả, **ba bài test dự kiến pass**. Đây là kỳ vọng theo code, chưa phải output từ một lần chạy cụ thể. Các test không lệ thuộc ID cố định giữa các bài; vì vậy có thể chạy lặp lại khi `AtomicLong` tiếp tục tăng.

Hạn chế của `MockMvc`: nó chạy qua Spring MVC với Spring test mock request/response, **không đi qua reverse proxy hoặc network browser thực tế**. Để xác minh absolute URL/forwarded headers, cần thêm integration test trong môi trường có proxy tin cậy và base URL production.

### Test khi không trả body

Nếu API chủ động chọn 201 không body, đổi method thành:

```java
ResponseEntity<Void> create(...) {
    URI location = ...;
    return ResponseEntity.created(location).build();
}
```

Đây là fragment thiết kế khác, dấu `...` chỉ là placeholder của chữ ký/logic, **không phải Java compilable độc lập**. Khi test, bỏ assertion JSON body; giữ assertion `status().isCreated()` và header `Location`. Có thể dùng `content().string("")` nếu contract thực sự yêu cầu body rỗng.

## 7. Xây URI Location: đường dẫn tương đối hay URL tuyệt đối?

### 7.1. Relative Location

```java
URI location = URI.create("/api/books/" + saved.id());
return ResponseEntity.created(location).body(saved);
```

Đây là lựa chọn đơn giản khi API có root path cố định trên cùng origin. RFC 9110 cho phép `Location` là **URI-reference**, không bắt buộc chứa scheme/host. Client đang gửi POST tới `https://api.example.com/api/books` có thể resolve `/api/books/1001` thành `https://api.example.com/api/books/1001`.

Ưu điểm: không lấy hostname từ một header không đáng tin, giảm vấn đề HTTP/HTTPS khi sau proxy. Rủi ro: URI bắt đầu bằng `/` trỏ về **root của host**, vì thế nếu API có context path `/catalog` hoặc gateway công bố prefix `/v1`, Location phải bao gồm prefix công khai thích hợp (ví dụ `/catalog/api/books/1001`). Nếu cần URL dưới **chính thư mục URI hiện tại**, hiểu đúng quy tắc resolve của relative path như `1001` so với path kết thúc slash; tránh ghép chuỗi tùy tiện.

### 7.2. Absolute Location do ứng dụng biết trước

Nếu API được công bố ở hostname chuẩn cố định, có thể dựng từ cấu hình được quản lý:

```java
// Chỉ minh họa: giá trị này nên đến từ config tin cậy.
URI location = URI.create(
        "https://api.example.com/api/books/" + saved.id());
```

Lựa chọn này hữu ích khi service chạy sau reverse proxy và phải đảm bảo Location trỏ tới hostname public. Không hardcode hostname môi trường dev/staging trong business logic; đưa base URL vào configuration. Không dùng request `Host` hoặc `X-Forwarded-Host` không được xác thực để tự dựng Location tuyệt đối, vì có thể tạo link độc hại trong response (*host-header poisoning*).

### 7.3. Sử dụng `ServletUriComponentsBuilder` trong Spring MVC

Trong ứng dụng Servlet, khi muốn dựng URL dựa trên request hiện tại:

```java
import java.net.URI;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

URI location = ServletUriComponentsBuilder
        .fromCurrentRequestUri()
        .pathSegment("{id}")
        .buildAndExpand(saved.id())
        .toUri();
```

Đây là **fragment trong request-handling method**, giả định request hiện tại là `POST /api/books`, không có trailing slash/prefix đặc biệt và biến `saved` đã có. Khi gọi, builder dùng scheme/host/path từ request, nối path segment ID, nên URL có thể dạng `http://localhost:8080/api/books/1001`. Cách này thường tự giữ context path theo request đã được Servlet container cung cấp.

Các lưu ý:

- Nếu request path đã kết thúc bằng `/`, cần xem đầu ra thực tế để tránh canonical URL có slash kép/ngoài ý muốn.
- Reverse proxy có thể đổi scheme/host/prefix; cấu hình trusted forwarded headers và `ForwardedHeaderFilter`/cơ chế container thích hợp nếu cần. Không tin headers do client công khai gửi trực tiếp.
- `fromCurrentRequestUri()` dùng request URI, không thêm query string để tạo Location; nên kiểm tra kết quả nếu request có `?source=...`.
- Khi ID là **chuỗi slug** lấy từ input thay vì số server cấp, phải encode theo **URI path segment**, tránh chèn thẳng slash, `?`, `#` hoặc percent-encoded string chưa kiểm soát.

### 7.4. URI path segment có ký tự đặc biệt

Nếu identifier là chuỗi, ưu tiên URI builder có template variable:

```java
import java.net.URI;
import org.springframework.web.util.UriComponentsBuilder;

String slug = "spring mvc/advanced";
URI location = UriComponentsBuilder
        .fromPath("/api/books")
        .pathSegment("{slug}")
        .encode()
        .buildAndExpand(slug)
        .toUri();
```

Trong tình huống này slash **nằm trong giá trị slug**, nên được encode như dữ liệu của **một segment**, không tách thành cấp đường dẫn mới. Nếu hệ thống chủ động cho phép slug nhiều segment, đó là thiết kế route khác và cần được nêu rõ. Giá trị `slug` chỉ minh họa; trong API thật, kiểm tra tính hợp lệ và uniqueness tại nơi cấp định danh.

**Không dựng Location bằng nối chuỗi từ dữ liệu request không kiểm soát**. Đặc biệt tránh gắn input nguyên bản vào URL có thể chứa `#`, `?` hoặc chuỗi gây thao túng path.

## 8. Điều gì cần hoàn tất trước khi trả 201?

`201` có ý nghĩa quan sát được khi tài nguyên **đã tạo thành công** theo hợp đồng. Với repository hoặc database:

1. Validate input.
2. Thực thi business rules và authorization.
3. Tạo entity mới, persist qua transaction thích hợp.
4. Nhận identifier thực đã được hệ thống lưu trữ cấp.
5. Xây URI của tài nguyên từ identifier đó.
6. Trả `201 Created` cùng `Location` và body khi cần.

Pseudocode:

```java
@PostMapping
ResponseEntity<BookView> create(@Valid @RequestBody CreateBookRequest dto) {
    Book saved = bookService.createBook(dto); // service quản lý transaction
    URI location = URI.create("/api/books/" + saved.getId());
    return ResponseEntity.created(location)
            .body(BookView.from(saved));
}
```

Đây là **fragment thiết kế service/DTO**, không tự biên dịch: `Book`, `bookService` và `BookView.from` là thành phần application phải định nghĩa. Điểm mấu chốt là location lấy từ **ID đã được cấp và xác nhận lưu**. Không trả 201 chỉ vì đã *bắt đầu* gọi lưu, rồi chạy công việc thật trong background có thể thất bại.

Đối với một transaction database thông thường, bảo đảm lỗi commit được xử lý trước khi chốt thành công HTTP. Nếu dùng outbox/eventual consistency, định nghĩa rõ tài nguyên nào đã tạo: ví dụ request/task resource có thể được tạo ngay (201 cho task), trong khi sản phẩm cuối còn xử lý (202/khác theo thiết kế). Không báo tài nguyên cuối đã tạo nếu mới gửi event.

### ID do client cung cấp bằng PUT

Với `PUT /api/books/book-abc`, URI đã do client chỉ định:

- Nếu tài nguyên `book-abc` chưa tồn tại và PUT tạo mới: **201 Created**.
- Nếu đã tồn tại và PUT cập nhật/thay thế: **200 OK** với body hoặc **204 No Content**.
- `Location` trong 201 có thể là URI đích `/api/books/book-abc` để làm rõ tài nguyên mới; tùy hợp đồng.

**Không dùng POST và PUT như từ đồng nghĩa.** POST yêu cầu server xử lý theo semantics của collection/resource, thường để server cấp URI; PUT yêu cầu trạng thái tại **target URI đã biết** và là phương thức idempotent theo HTTP semantics, dù side effects phụ trợ cần được thiết kế cẩn thận.

## 9. POST, retry và nguy cơ tạo trùng

POST **không mặc định idempotent**. Nếu client gửi POST tạo tài nguyên rồi network timeout trước khi nhận 201, client có thể không biết server đã tạo thành công. Gửi lại POST giống hệt có thể tạo **một tài nguyên thứ hai**, ví dụ ID 1002, và trả thêm một 201 khác.

```text
Client                          Server
  | POST /api/books              |
  |----------------------------->| tạo id=1001
  |          201 Location        |
  |<----------[timeout]----------| phản hồi bị mất
  | POST /api/books (retry)      |
  |----------------------------->| tạo id=1002 nếu không chống trùng
  | 201 Location: /api/books/1002|
  |<-----------------------------|
```

Phương án thường dùng: **Idempotency-Key** do client sinh cho một ý định tạo mới, kết hợp với kho lưu idempotency ở server. Một thiết kế tốt cần:

- Scope khóa theo user/tenant, endpoint/action, không dùng chung bừa bãi toàn hệ thống.
- Giới hạn thời gian sống; lưu khóa và kết quả một cách atomic hoặc có cơ chế coordination phù hợp.
- Nếu cùng khóa và cùng payload được gửi lại, trả kết quả đã ghi nhận (có thể gồm cùng `201`, `Location` và body), theo hợp đồng API.
- Nếu cùng khóa nhưng payload khác, báo xung đột (`409` hay lỗi phù hợp) thay vì âm thầm tái sử dụng kết quả.
- Giữ unique constraint/business constraints trong DB để tránh race condition.
- Không coi chỉ việc thêm header `Idempotency-Key` là đã có idempotency; backend phải triển khai semantics.

Đây là một mẫu thiết kế API, không phải khả năng tự động của `ResponseEntity.created(...)` hoặc Spring MVC. Với thao tác tạo có chi phí cao như thanh toán hoặc cấp tài nguyên, idempotency và xử lý retry là phần quan trọng của hợp đồng.

## 10. Khi nào Location không được truy xuất ngay?

Ngay cả khi `201` đúng về mặt ngữ nghĩa, client có thể không GET được Location tức thì do các yếu tố như:

- API không hỗ trợ GET tài nguyên vừa tạo vì policy/authorization (nên tài liệu cần nói rõ).
- Tài nguyên được persist nhưng bản đọc qua replica/search index cập nhật chậm; đây là **eventual consistency**, cần thiết kế lại kỳ vọng read-after-write hoặc cung cấp endpoint đảm bảo nhất quán.
- API gateway có prefix route khác, Location sai context path hoặc scheme/host.
- Backend tạo resource ở một service khác nhưng chỉ trả URI nội bộ không công khai.
- URI có ID chứa ký tự đặc biệt nhưng chưa được percent-encode đúng.
- Tài nguyên được tạo thành công nhưng ngay sau đó bị xóa hoặc user mất quyền đọc.

Nếu hệ thống hứa “GET ngay lập tức đến Location sau 201 sẽ thấy tài nguyên” thì cần kỹ thuật đủ đáp ứng (read-your-writes) và integration test để giữ cam kết. Trong hệ thống eventual consistency, chỉ trả 201 vẫn có thể đúng nếu tài nguyên thực sự đã được tạo, nhưng **contract cần giải thích khi nào có thể đọc và cách client theo dõi**.

## 11. Sai sót thường gặp trong Spring MVC

### A. Dùng `ResponseEntity.ok` mặc định sau khi tạo

```java
// Không thể hiện semantics "đã tạo tài nguyên mới".
return ResponseEntity.ok(saved);
```

Cách sửa cho endpoint POST tạo tài nguyên:

```java
return ResponseEntity.created(location).body(saved);
```

Không phải mọi POST đều cần 201; các POST điều khiển tác vụ hoặc query có thể trả 200/204. Cần xét **kết quả thực tế của operation**.

### B. Có 201 nhưng không có Location khi ID do server cấp

```java
@ResponseStatus(HttpStatus.CREATED)
@PostMapping
public BookResponse create(@RequestBody CreateBookRequest request) {
    // ...
}
```

Đoạn này không tự tạo Location. Nếu POST vào collection và tạo item con, client không có URI canonical rõ ràng. Chuyển sang `ResponseEntity.created(...)` hoặc tự thêm header dựa trên ID lưu thật.

### C. Header Location trỏ sai

Sai ví dụ: `Location: /api/books` sau khi server vừa tạo `/api/books/1001`. Client có thể hiểu collection là tài nguyên chính. Nếu cần trỏ client đến collection để tham khảo thêm, có thể đặt link ở response body hoặc header `Link` với relation phù hợp, nhưng `Location` của 201 nên xác định đúng primary created resource.

### D. Tạo URI trước khi có ID

```java
long id = 1001L; // hardcode nhầm từ tài liệu demo
repository.save(entity);
return ResponseEntity.created(URI.create("/api/books/" + id)).build();
```

Trong production, ID có thể được DB/generator cấp; hardcode hoặc dự đoán ID trước save là sai. Truyền ID được lưu thật, có xử lý lỗi persistence.

### E. Sử dụng địa chỉ server nội bộ hoặc tin Host từ client

Ví dụ server ở `http://10.0.0.15:8080` sau public proxy `https://api.example.com` nhưng trả Location nội bộ: client bên ngoài không truy cập được, có nguy cơ lộ topology. Phân biệt:
- URI relative với prefix public đúng.
- Absolute URI lấy từ cấu hình external base URL tin cậy.
- Forwarded headers được trust đúng proxy chain nếu dựng URI từ request.

### F. Trả 201 dù validation, authorization hoặc DB insert thất bại

Một lỗi trong transaction sau khi tạo response body có thể khiến API tuyên bố sai trạng thái. Không chỉ nhìn vào method return trong một service asynchronous; phải chắc chắn công việc tạo đã đạt điều kiện “hoàn tất” được contract định nghĩa.

### G. Nhầm 201 với 303

`303 See Other` có mục đích chuyển client qua một URI khác, thường để GET biểu diễn kết quả POST (ví dụ redirect-after-post). `201` mô tả tài nguyên được tạo và Location xác định URI đó, nhưng client **không tự redirect**. Không trộn semantics khi test.

### H. Trả JPA entity thẳng vào JSON body

Entity có thể gồm field nội bộ, lazy relationship, vòng tham chiếu hoặc thông tin nhạy cảm. Nên chọn response DTO (`BookResponse`) với các field công khai và giữ chuẩn output JSON riêng.

## 12. Thiết kế response body và thông tin đi kèm

Body trong 201 thường là lựa chọn thiết kế sản phẩm; có vài mẫu phổ biến:

| Mẫu | Ví dụ | Khi phù hợp |
| --- | --- | --- |
| `201` + Location + full DTO | `{"id":1001,"title":"Spring MVC"}` | Client cần sử dụng ngay tài nguyên mới |
| `201` + Location + summary DTO | `{"id":1001}` | Muốn payload nhẹ, client tự GET khi cần |
| `201` + Location + không body | Không có body | Client chỉ cần URI, schema API quy định vậy |
| `201` + Location + metadata | DTO kèm trạng thái ban đầu hoặc version | Tạo thành công nhưng workflow có các bước tiếp theo |

Các header bổ sung:

- **`ETag`**: nếu được cung cấp và phản ánh representation hiện tại, client có thể dùng conditional requests như `If-Match` khi cập nhật; giúp tránh lost update. Không tự động được tạo khi gọi `ResponseEntity.created`.
- **`Content-Location`**: chỉ ra URI của representation trong body như đã giải thích ở phần 2.
- **`Cache-Control`**: nếu response chứa dữ liệu nhạy cảm hoặc chính sách caching cụ thể; không tự suy rằng mọi 201 có thể cache theo default.
- **`Link`**: có thể nêu tài nguyên liên quan như collection, owner hay next action, theo media type/API contract.

Ví dụ Java method có version nếu service cung cấp:

```java
return ResponseEntity.created(location)
        .eTag("\"v1\"")
        .body(saved);
```

Đây là fragment trong method controller, chỉ hợp lệ nếu `saved` và `location` đã định nghĩa, đồng thời version token `"v1"` thực sự tương ứng representation lưu. ETag trong ví dụ chỉ để thấy cú pháp; **không hardcode một ETag cho mọi tài nguyên** trong ứng dụng thật.

## 13. Phân biệt lỗi HTTP khi POST tạo mới

Trong ứng dụng thật nên có API error contract rõ ràng. Ví dụ:

| Trường hợp | HTTP status thường chọn | Lý do |
| --- | --- | --- |
| JSON malformed | `400 Bad Request` | Không deserialize được request |
| Thiếu `title` bắt buộc | `400 Bad Request` | Vi phạm validation khi binding |
| `Content-Type: text/plain` nhưng endpoint chỉ consumes JSON | `415 Unsupported Media Type` | Converter/mapping không hỗ trợ media type |
| `Accept: application/xml` nhưng endpoint chỉ produces JSON | `406 Not Acceptable` | Không có representation phù hợp |
| Unique key của tên sách bị trùng, hợp đồng không cho trùng | `409 Conflict` | Trạng thái hiện tại xung đột |
| User chưa đăng nhập / không đủ quyền | `401` / `403` | Security từ chối |
| Service phụ trợ tạm thời không sẵn sàng | `503 Service Unavailable` khi phù hợp | Không báo tạo thành công |
| POST tạo được sách | `201 Created` | Cần Location của sách mới |
| POST enqueue tác vụ chưa hoàn tất | `202 Accepted` | Theo dõi trạng thái bất đồng bộ |

Không nên “fix” mọi trường hợp về HTTP 200/201 rồi đưa `success=false` trong body. Client, proxy, monitoring và SDK dựa nhiều vào status; dùng status đúng giúp hợp đồng rõ và hệ sinh thái HTTP vận hành chính xác.

### Xử lý exception trong Spring MVC

Với REST API, `@RestControllerAdvice` và `@ExceptionHandler` (hoặc `ResponseEntityExceptionHandler`/`ProblemDetail` từ Spring Framework 6+) có thể thống nhất response lỗi. Tuy nhiên:
- Không thêm `Location` tài nguyên mới vào response lỗi chưa tạo gì.
- Không gửi stack trace, SQL statement hay ID nội bộ nhạy cảm về client.
- Khi unique constraint race xảy ra, phân loại lỗi DB cụ thể thành conflict chỉ khi chắc chắn phù hợp, không chuyển toàn bộ `DataIntegrityViolationException` sang 409 mù quáng.
- Nếu idempotency request đã tạo trước đó nhưng response bị mất, xử lý retry theo contract để client lấy lại URI tài nguyên cũ.

## 14. Phiên bản Spring và sự tương thích của API

| Mốc | `ResponseEntity.created(URI)` | Ghi chú |
| --- | --- | --- |
| Spring Framework 5.x, Boot 2.x | Có | Ứng dụng Boot 2 thường dùng `javax.validation` |
| Spring Framework 6.x, Boot 3.x | Có | Ví dụ đầy đủ bên trên dùng `jakarta.validation`, Java 17+ |
| Spring Framework 7.x, Boot 4.x | Có | Builder 201 + Location vẫn tồn tại; một số module/starter và hệ sinh thái Jackson/validation có thay đổi |

Cơ chế 201/Location thuộc **HTTP semantics**, không phụ thuộc Jackson 2 hay Jackson 3. Nếu migrate Boot 3 → Boot 4, cần cập nhật imports/dependencies theo framework, nhưng API cấp HTTP `ResponseEntity.created(location)` vẫn giúp biểu diễn hợp đồng rõ ràng.

Mốc Spring Framework 6.0/7.0 có thể đổi hành vi URL matching hoặc converter, nên cần chạy lại test URI trong `Location` và GET endpoint. **Không tự tạo URI có slash cuối** rồi mặc định rằng router `@GetMapping("/api/books/{id}")` sẽ nhận cả hai dạng khi cấu hình path matching không cho phép.

## 15. Checklist kiểm tra trước khi công bố endpoint tạo mới

- [ ] Endpoint thực sự tạo tài nguyên trước khi trả `201`; nếu chỉ enqueue, cân nhắc `202`.
- [ ] Trả `ResponseEntity.created(location)` hoặc tương đương; Location xác định đúng primary resource.
- [ ] Location là URI reference hợp lệ, có encode path segments và base path/public prefix đúng.
- [ ] Location không leak private host, không thể bị giả mạo qua Host/Forwarded headers không đáng tin.
- [ ] Nếu API cam kết GET tại Location, endpoint GET có thể đọc tài nguyên đúng ID theo consistency policy.
- [ ] Contract body rõ: trả DTO đầy đủ, summary hay rỗng; `Content-Type` đúng khi body JSON.
- [ ] Validation/auth/DB conflict thất bại thì không trả 201; bảo vệ uniqueness và transaction.
- [ ] Retry POST được thiết kế có chủ ý, nhất là thao tác thanh toán hoặc tạo có chi phí lớn.
- [ ] Có test `POST → 201 + Location → GET`, test invalid 400, conflict, auth và proxy prefix khi cần.
- [ ] Không hardcode ID/host của ví dụ vào production.
- [ ] Đã cập nhật OpenAPI/SDK và tài liệu client về response 201, header Location, body và các lỗi.

## 16. Bài tập và câu hỏi tự đánh giá

1. **HTTP semantics:** POST tạo thành công một tài nguyên tại `/api/tasks/42`. Vì sao `201` cần đi kèm Location để client dễ sử dụng? `201` có bắt buộc body JSON không?
2. **Phân biệt redirect:** Vì sao trình duyệt không tự GET `Location` trên phản hồi `201` nhưng có thể chuyển hướng sau `303`?
3. **Spring API:** Viết controller `create` trả `ResponseEntity.created(uri).body(dto)` và một GET matching. Kiểm thử status, header và body.
4. **Validation:** Khi `title=""` gây 400, liệu Location trỏ tới `/api/books/1001` có phù hợp không? Giải thích.
5. **URI encoding:** Nếu slug tài nguyên là `"spring web/101"`, tại sao ghép trực tiếp thành path là sai? Dùng builder với `pathSegment`.
6. **Reverse proxy:** App chạy nội bộ `http://localhost:8080` nhưng public URL là `https://api.example.com/v1`. Hãy thiết kế Location nhất quán và kiểm thử forwarded-header trust policy.
7. **Retry:** Client timeout sau POST, gửi lại và tạo bản ghi thứ hai. Hãy thiết kế một Idempotency-Key contract xử lý cùng key/cùng payload/khác payload.
8. **Transaction:** DB phát sinh unique conflict lúc commit. Nếu controller đã chuẩn bị response 201 nhưng commit thất bại, client cần nhận status nào và vì sao?
9. **201 hay 202:** API nhận yêu cầu tạo báo cáo, thực tế cần vài phút mới xây xong. Nếu có job resource được tạo ngay, status nào mô tả **job**? Status nào mô tả **báo cáo cuối** đang chờ? Cần phân biệt hai tài nguyên.
10. **PUT:** Với `PUT /api/books/client-id-1`, lần đầu tạo mới và lần hai cập nhật, hãy chọn status theo RFC 9110 và viết test cho hai lần gọi.

## 17. Tài liệu tham khảo chính thức

- [RFC 9110, `15.3.2 — 201 Created](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.2): ngữ nghĩa 201 và primary resource.
- [RFC 9110, `10.2.2 — Location](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.2): URI-reference, ý nghĩa Location của 201 và 3xx.
- [RFC 9110, `8.7 — Content-Location](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.7): khác biệt giữa Location và Content-Location.
- [RFC 9110, `9.3.4 — PUT](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.4): thành công khi tạo mới so với cập nhật target resource.
- [Spring Framework 6.x Javadoc — ResponseEntity](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/http/ResponseEntity.html): `created(URI)` và các builder HTTP.
- [Spring Framework 7 Javadoc — ResponseEntity](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/ResponseEntity.html): `created(URI)` vẫn hiện diện.
- [Spring Framework — Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html): phản hồi `ResponseEntity` trong MVC.
- [Spring Framework — ServletUriComponentsBuilder](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/servlet/support/ServletUriComponentsBuilder.html): dựng URI từ request Servlet.
- [Spring Framework — UriComponentsBuilder](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/web/util/UriComponentsBuilder.html): xử lý template, path segment và encoding.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rest-responses-005">Thiết kế DTO phản hồi và tách mô hình HTTP khỏi đối tượng lưu trữ</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế DTO phản hồi và tách mô hình HTTP khỏi đối tượng lưu trữ trong Spring MVC

## 1. Mục tiêu bài học

Khi xây dựng API REST bằng Spring MVC, đối tượng Java đọc/ghi trong database thường có nhiều thông tin **không thuộc hợp đồng HTTP**: khóa ngoại, trạng thái nội bộ, metadata quản trị, trường bí mật, lazy relations hoặc tên cột phục vụ tối ưu lưu trữ. Trả thẳng JPA entity từ `@RestController` khiến dữ liệu và cấu trúc API bị phụ thuộc chặt vào schema và hành vi ORM.

Mục tiêu là thiết kế **Response DTO (Data Transfer Object)** như một mô hình biểu diễn công khai, có chủ đích, và giữ xử lý HTTP ở controller thay vì rải `ResponseEntity`, status hoặc Servlet API vào tầng lưu trữ.

Sau bài này, bạn có thể:

- Phân biệt domain model, persistence entity, request DTO, response DTO và HTTP representation.
- Chọn đúng trường, cấu trúc, kiểu dữ liệu, nullability và định dạng cho response mà client thực sự cần.
- Chuyển entity → DTO tại ranh giới service/controller bằng mapper rõ ràng; không lộ field nội bộ hay đồ thị quan hệ JPA.
- Dùng `@RestController`, `@ResponseBody`, `ResponseEntity<T>`, `Location`, status `200`/`201`/`204`/`404` và xử lý lỗi phù hợp.
- Hiểu các hệ quả về transaction, lazy loading, N+1 query, phân trang, security, versioning và kiểm thử.
- Thực hành với **Java 17+, Spring Framework 6.x/Spring Boot 3.x, Spring Data JPA và H2**.

Các ví dụ runnable bên dưới giả định dự án Spring Boot có dependency: **Spring Web**, **Spring Data JPA**, **Validation**, **H2 Database**, **Spring Boot Test**. Nếu ứng dụng đang sử dụng Spring Boot 4/Spring Framework 7, nhiều nguyên tắc vẫn giữ nguyên, nhưng hãy đối chiếu dependency và API test theo phiên bản đang dùng.

## 2. Các mô hình cần tách

Đừng dùng từ “model” cho mọi thứ mà không xác định vai trò:

| Loại | Trách nhiệm | Ví dụ | Có nên trả trực tiếp ra HTTP? |
| --- | --- | --- | --- |
| **Persistence entity** | Ánh xạ database, quan hệ, ID, version, lazy loading | `CustomerEntity` có `internalMemo` | Thông thường **không** |
| **Domain model / business object** | Quy tắc nghiệp vụ, trạng thái và bất biến | `Customer`, `Money`, `Order` | Thường ánh xạ sang DTO |
| **Request DTO** | Hợp đồng dữ liệu client được phép gửi | `CreateCustomerRequest` | Chỉ dùng cho input; phải validate |
| **Response DTO** | Hợp đồng dữ liệu client được phép nhận | `CustomerResponse` | **Có**, là dạng biểu diễn công khai |
| **HTTP envelope** | Status, headers, media type, cache, Location | `ResponseEntity<CustomerResponse>` | Thuộc ranh giới HTTP/controller |
| **Error response** | Cấu trúc lỗi công khai, mã lỗi, message an toàn | `ProblemDetail` hoặc `ApiError` | Có, với chính sách riêng |

Trong demo, một đối tượng lưu trữ có thể bao gồm `id`, `displayName`, `email`, `internalMemo`, `createdAt`. Response DTO chỉ gồm `id`, `displayName`, `email`, `createdAt`; `internalMemo` không phải dữ liệu public.

### Sơ đồ ranh giới

```text
                  HTTP / JSON
                      |
                      v
           CustomerController
         @RequestBody request DTO
      @Valid / status / headers / URI
                      |
                      v
             CustomerService
          nghiệp vụ + transaction
          entity -> response DTO
                      |
                      v
           CustomerRepository
                JPA/Hibernate
                      |
                      v
                   Database

      Response DTO -> HttpMessageConverter/Jackson
                  -> HTTP body
```

Một cách tổ chức thường gặp là mapper riêng (`CustomerMapper`), có thể được gọi trong service hoặc application layer. Điểm cốt lõi là **mapping có chủ đích trước khi object đi qua HTTP serialization**. Controller có thể giữ vai trò mapping nếu ứng dụng nhỏ và dependency hướng đúng; tuy nhiên không nên đưa `HttpServletRequest`, `ResponseEntity` hoặc status HTTP xuống repository.

## 3. Vì sao không trả JPA entity trực tiếp?

### 3.1. Rủi ro lộ dữ liệu

```java
// Ví dụ cần tránh nếu entity chứa trường nội bộ:
@GetMapping("/customers/{id}")
public CustomerEntity find(@PathVariable long id) {
    return repository.findById(id).orElseThrow();
}
```

Jackson có thể serialize các property có getter được nhận diện; thêm getter/field trong entity ở tương lai có thể vô tình xuất hiện trong JSON. Những trường như `passwordHash`, `resetToken`, `secretKey`, `internalMemo`, `riskLevel` hoặc thông tin riêng của tenant tuyệt đối không nên được kiểm soát bằng “hy vọng client không biết chúng tồn tại”.

`@JsonIgnore` trên entity có thể chặn một số field nhưng khiến **schema persistence phải mang trách nhiệm HTTP contract**. Nó cũng không giải quyết toàn bộ vấn đề: một entity có thể cần những view response khác nhau theo endpoint, user role và API version.

**DTO hạn chế bề mặt dữ liệu có thể serialize; nó không thay thế authorization.** Trước khi ánh xạ DTO, service vẫn cần kiểm tra người gọi có quyền đọc resource và từng field nhạy cảm hay không. Nếu endpoint trả email, hãy xác định rõ vai trò nào được phép thấy email.

### 3.2. Lộ chi tiết database và mất ổn định hợp đồng

Entity thường thay đổi theo hiệu năng/schema: rename cột, tách bảng, thêm quan hệ `@OneToMany`, đánh chỉ mục, thêm trường `@Version`. Nếu response = entity, thay đổi lưu trữ có thể phá client. DTO tạo một lớp chống phụ thuộc, cho phép:

- Rename field lưu trữ nhưng giữ JSON `displayName`.
- Tổng hợp dữ liệu từ nhiều bảng thành một response đơn giản.
- Ẩn ID nội bộ hoặc khóa ngoại mà client không cần.
- Thay đổi JPA/Hibernate mà không bắt client thay cấu trúc.
- Giữ phiên bản API cũ bằng mapper cũ khi thay đổi domain.

### 3.3. Lazy loading, vòng lặp JSON và query khó kiểm soát

Ví dụ entity `OrderEntity` có quan hệ `CustomerEntity` và `OrderLineEntity`; trả entity có thể khiến Jackson cố đọc quan hệ lazy sau khi transaction kết thúc và gặp `LazyInitializationException`, hoặc chạy thêm truy vấn không kiểm soát. Quan hệ hai chiều (`Order -> Customer -> Orders -> ...`) có thể dẫn đến vòng lặp serialize, JSON khổng lồ, hoặc cần annotation Jackson gây coupling.

Cách đúng:

1. Xác định chính xác dữ liệu response cần, ví dụ `OrderResponse(id, customerName, List<LineResponse>)`.
2. Fetch dữ liệu bằng query/fetch plan phù hợp **trong transaction** hoặc projection trực tiếp.
3. Map sang DTO, chỉ gồm trường mong muốn.
4. Trả DTO ra khỏi service khi đã hoàn tất truy cập persistence.

Spring Boot có cơ chế **Open Session in View** trong một số cấu hình servlet/JPA, nhưng không nên dựa vào việc session còn mở để Jackson vô tình phát truy vấn database lúc render response. DTO giúp tách thời điểm truy vấn và render, nhưng **không tự sửa N+1**: bạn vẫn phải kiểm tra fetch plan.

### 3.4. Không dùng entity làm request DTO

Input từ client có thể chứa `id`, `role`, `isAdmin`, `ownerId` dù API chỉ định cho phép đổi `displayName`. Nếu bind trực tiếp vào entity và gọi `save`, bạn có nguy cơ **mass assignment/overposting**. Request DTO với trường được phép chỉnh sửa là **allowlist ở hợp đồng đầu vào**; sau đó vẫn cần authorize thao tác và validate.

## 4. Response DTO nên có những trường nào?

Thiết kế từ **nhu cầu của client và cam kết API** trước, sau đó mới quyết định map từ database.

Ví dụ JSON hợp lý cho một customer:

```json
{
  "id": 42,
  "displayName": "Lan",
  "email": "lan@example.com",
  "createdAt": "2026-10-21T04:00:00Z"
}
```

Các lựa chọn cần cân nhắc:

- **Tên field ổn định:** `displayName` nên mang ngữ nghĩa client hiểu, không lệ thuộc tên cột `full_name_v2`.
- **Kiểu dữ liệu đúng:** số là JSON number nếu thực sự là quantity an toàn; identifier dài vượt giới hạn chính xác của JavaScript đôi khi nên là string trong API.
- **Thời gian:** `Instant` thường serialize thành ISO-8601 khi Boot/Jackson Java Time module được cấu hình; nêu rõ UTC và timezone. Trong ví dụ, `"Z"` biểu thị UTC.
- **Tiền tệ:** dùng chuỗi thập phân hoặc object gồm `amount` và `currency` theo hợp đồng; tránh dùng `double` cho số tiền nếu đòi độ chính xác.
- **Null/missing:** JSON `"field":null` và không có key có thể mang ý nghĩa khác; chuẩn hóa API và kiểm thử cấu hình Jackson `NON_NULL`/`NON_EMPTY` nếu dùng.
- **Danh sách:** cân nhắc `[]` thay vì `null` cho collection rỗng nếu hợp đồng quy định; đừng để ORM collection proxy xuất trực tiếp.
- **Trường dẫn xuất:** các URL hay field tổng hợp nên tính ở nơi hiểu ngữ nghĩa/public routing; không bắt entity lưu mọi thứ client thấy.
- **Quyền truy cập:** DTO của admin có thể khác DTO công khai; một DTO có quá nhiều field nullable theo role thường khiến contract khó hiểu.
- **JSON root:** chọn dạng object rõ ràng; định dạng của từng endpoint nên độc lập với chi tiết `ResponseEntity` Java.

Một response DTO là **API schema có vòng đời riêng**, không chỉ “bản copy của entity có ít field hơn”.

## 5. Java record, class và mức bất biến

Java 17 `record` phù hợp cho DTO nhỏ, bất biến về tham chiếu trường:

```java
public record CustomerResponse(
        Long id,
        String displayName,
        String email,
        java.time.Instant createdAt) {}
```

Ưu điểm: cấu trúc ngắn gọn, generated constructor/accessors/`equals`/`hashCode`/`toString`, dễ test; Jackson trong Boot hiện đại hỗ trợ record. Nhưng record không làm đồ thị nested tự động bất biến: nếu chứa `List<MutableItem>` thì vẫn cần copy bất biến hoặc DTO nested tương ứng.

Class DTO phù hợp nếu framework/serializer đặc thù yêu cầu constructor/setter, có nhu cầu tùy biến cao hoặc bạn cần kiểm soát backwards compatibility khác. **Không nên chọn record làm JPA entity tiêu chuẩn**: entity JPA có yêu cầu về constructor và model quản lý vòng đời; record thích hợp với DTO/projection hơn.

Tách input/output:

```java
public record CreateCustomerRequest(
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 100)
        String displayName,

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Email
        @jakarta.validation.constraints.Size(max = 254)
        String email) {}
```

Request DTO có constraint vì kiểm soát **dữ liệu được nhập**. Response DTO mô tả **dữ liệu công khai được trả**; các constraint output (nếu áp dụng) có mục đích khác. Đừng đưa field generated `id` hoặc nội bộ `internalMemo` vào `CreateCustomerRequest` nếu client không được đặt chúng.

## 6. Mapper: entity -> response DTO

Mapper đơn giản, rõ ràng:

```java
public final class CustomerMapper {
    private CustomerMapper() {}

    public static CustomerResponse toResponse(CustomerEntity entity) {
        return new CustomerResponse(
                entity.getId(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getCreatedAt());
    }
}
```

Đây là **đoạn trích minh họa** dùng các lớp `CustomerEntity` và `CustomerResponse` được định nghĩa ở phần ví dụ runnable; không tự chạy riêng.

Ưu điểm của mapper explicit:

- Review dễ thấy chính xác trường nào rời khỏi tầng dữ liệu.
- Thêm trường entity không tự mở rộng JSON.
- Có nơi chuyển enum nội bộ thành chuỗi hợp đồng, chuyển thời gian, đơn vị hay nested DTO.
- Test mapper riêng khi mapping phức tạp, nhất là khi field mới/optional xuất hiện.

Với nhiều DTO, MapStruct có thể sinh mapper tại compile time và phát hiện lỗi mapping sớm. Dù dùng thư viện, vẫn phải xem generated behavior, cấu hình unmapped fields và chính sách fields nhạy cảm. **Không map hai chiều vô điều kiện**: request DTO → entity thường cần logic create/update khác nhau; “đảo ngược” response mapper có thể cho phép client sửa field không hợp lệ.

Khi mapping nested collection:

```java
List<LineResponse> lines = order.getLines().stream()
        .map(line -> new LineResponse(
                line.getProductCode(), line.getQuantity()))
        .toList();
```

Đây là **minh họa trong một method nơi `order` và các kiểu DTO đã tồn tại**. Việc gọi `getLines()` có thể kích hoạt lazy query; fetch dữ liệu có kế hoạch trước rồi mới map, tránh N+1 khi xử lý danh sách orders.

## 7. ResponseEntity<T>: nơi đặt quyết định HTTP

Spring MVC hỗ trợ:

- Trả `CustomerResponse` trực tiếp từ `@RestController`: thông thường framework serialize body và trả `200 OK` khi thành công.
- Trả `ResponseEntity<CustomerResponse>`: điều khiển thêm status/headers, ví dụ `201 Created` với `Location`.
- Trả `ResponseEntity<Void>`: khi không có body, ví dụ `204 No Content` cho DELETE thành công.
- Trả `ResponseEntity.of(Optional<CustomerResponse>)`: `Optional.of(dto)` cho `200` kèm body; `Optional.empty()` cho `404` với body rỗng.

Ví dụ controller quyết định status:

```java
@PostMapping("/customers")
public ResponseEntity<CustomerResponse> create(
        @Valid @RequestBody CreateCustomerRequest request) {
    CustomerResponse response = service.create(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();
    return ResponseEntity.created(location).body(response);
}
```

Đoạn trên minh họa trong controller có imports, `service` và lớp DTO đã định nghĩa; bản runnable đầy đủ ở phần sau.

**201 Created** phù hợp khi server tạo resource mới. Header `Location` nên trỏ đến URI resource; response có thể kèm body DTO mới. **200 OK** thích hợp cho GET thành công hoặc thao tác trả dữ liệu tùy hợp đồng. **204 No Content** phải không có body response; tránh đặt JSON vào status 204. **404 Not Found** khi resource không tìm thấy (sau kiểm tra quyền theo chính sách). **400 Bad Request** thường cho payload JSON sai, thiếu required input/validation error. **409 Conflict** khi vi phạm trạng thái/điều kiện đồng thời của tài nguyên nếu có hợp đồng tương ứng.

Không nhúng `HttpStatus`/`ResponseEntity` vào JPA entity hay repository. Service thuần nghiệp vụ có thể trả DTO/Result/Optional, hoặc ném exception nghiệp vụ; controller/advice chuyển kết quả thành HTTP semantics.

### Có nhất thiết service phải trả response DTO?

Không bắt buộc. Hai kiến trúc đều có thể hợp lý:

1. Service/application query trả `CustomerResponse` trực tiếp vì use case phục vụ API và có quyền biết projection public. Controller chỉ lo HTTP.
2. Service/domain trả object nghiệp vụ, adapter/controller mapper thành DTO. Hợp lý khi cùng logic dùng cho message consumer, batch job hay transport khác.

Điều quan trọng là **dependency hướng đúng** và dữ liệu persistence không bị serialize trực tiếp. Nếu service trả response DTO, đừng để loại DTO đó mang `ResponseEntity` hay Servlet API; như vậy service vẫn không bị trói vào HTTP transport.

## 8. Ví dụ runnable: Spring Boot 3 + Spring MVC + JPA + H2

Ví dụ minh họa hệ thống customer:

- `CustomerEntity` là mô hình lưu trữ chứa trường nội bộ `internalMemo`.
- `CreateCustomerRequest` chỉ cho phép client gửi `displayName` và `email`.
- `CustomerResponse` chỉ công khai `id`, `displayName`, `email`, `createdAt`.
- `CustomerRepository` lưu/đọc entity bằng Spring Data JPA.
- `CustomerService` thực thi transaction, chuẩn hóa theo quy tắc demo và tạo response DTO.
- `CustomerController` xử lý request và trả `201 + Location` hoặc `200/404`.

Tạo dự án từ Spring Initializr với **Java 17+, Spring Boot 3.x**, Maven, dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `h2` (runtime), `spring-boot-starter-test` (test). Không cần cơ sở dữ liệu bên ngoài khi dùng H2 embedded; ví dụ dùng JPA schema auto-ddl chỉ để học, **không bật chế độ auto-ddl tùy tiện trên production**.

### 8.1. Khởi động ứng dụng

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

### 8.2. Entity lưu trữ (không expose trực tiếp)

Tệp `src/main/java/com/example/demo/CustomerEntity.java`:

```java
package com.example.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "customers")
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private String internalMemo;

    @Column(nullable = false)
    private Instant createdAt;

    protected CustomerEntity() {
        // JPA cần no-args constructor.
    }

    public CustomerEntity(
            String displayName, String email, String internalMemo) {
        this.displayName = displayName;
        this.email = email;
        this.internalMemo = internalMemo;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getInternalMemo() { return internalMemo; }
    public Instant getCreatedAt() { return createdAt; }
}
```

Dù entity có `getInternalMemo()`, mapper/response DTO không mang trường này ra JSON. Entity là lớp managed bởi JPA, không mang `@JsonIgnore` hoặc annotation HTTP. Việc đặt internal memo ở đây chỉ để chứng minh sự tách biệt; trong production cần chính sách lưu trữ/quyền truy cập với dữ liệu nhạy cảm.

### 8.3. Repository

Tệp `src/main/java/com/example/demo/CustomerRepository.java`:

```java
package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository
        extends JpaRepository<CustomerEntity, Long> {
}
```

Repository giải quyết persistence, trả `CustomerEntity`/`Optional<CustomerEntity>`. Nó không biết `ResponseEntity`, `HttpStatus`, `Location`, media type hay status code.

### 8.4. Request DTO và response DTO

Tệp `src/main/java/com/example/demo/CreateCustomerRequest.java`:

```java
package com.example.demo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Email @Size(max = 254) String email) {
}
```

Tệp `src/main/java/com/example/demo/CustomerResponse.java`:

```java
package com.example.demo;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String displayName,
        String email,
        Instant createdAt) {
}
```

DTO có cấu trúc rõ, không phụ thuộc Hibernate. `@Valid` kiểm tra request khi controller khai báo nó. Constraint này kiểm tra cú pháp cơ bản; email có hợp lệ về nghiệp vụ (ví dụ unique và ownership) hay không là việc khác của service/database.

### 8.5. Service và mapping tại transaction boundary

Tệp `src/main/java/com/example/demo/CustomerService.java`:

```java
package com.example.demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        // Chính sách normalize này chỉ là ví dụ nghiệp vụ.
        String name = request.displayName().strip();
        String email = request.email().strip();

        CustomerEntity entity =
                new CustomerEntity(name, email, "INTERNAL_ONLY");
        CustomerEntity saved = repository.save(entity);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Optional<CustomerResponse> findById(long id) {
        return repository.findById(id).map(this::toResponse);
    }

    private CustomerResponse toResponse(CustomerEntity entity) {
        return new CustomerResponse(
                entity.getId(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getCreatedAt());
    }
}
```

Giải thích:

- `@Transactional` áp dụng cho use case ghi, `readOnly=true` cho use case đọc. Đây là hint tối ưu và ranh giới transaction, không thay thế phân quyền.
- `repository.save` trả entity có ID đã được tạo theo chiến lược IDENTITY dùng trong demo.
- Mapper `toResponse` diễn ra khi entity còn nằm trong use case/transaction, tránh đưa entity live ra serializer.
- `internalMemo` **không xuất hiện** trong response.
- `Optional<CustomerResponse>` diễn đạt có/không có customer mà không kéo `HttpStatus` vào service.
- Ví dụ `strip()` đầu vào là quyết định normalization của ứng dụng; production phải định nghĩa nó trong API contract. Đừng âm thầm lowercase email local-part nếu chưa có chính sách phù hợp.
- Đoạn demo chưa thực hiện kiểm tra tenant/authorization; trong ứng dụng thực phải thêm logic truy cập vào service/query trước khi trả DTO.

### 8.6. Controller chịu trách nhiệm HTTP

Tệp `src/main/java/com/example/demo/CustomerController.java`:

```java
package com.example.demo;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CreateCustomerRequest request) {

        CustomerResponse result = service.create(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(result.id())
                .toUri();

        return ResponseEntity.created(location).body(result);
    }

    @GetMapping(
        path = "/{id}",
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CustomerResponse> find(
            @PathVariable("id") long id) {
        return ResponseEntity.of(service.findById(id));
    }
}
```

Controller không biết entity có field `internalMemo` hay DB dùng H2. Nó chỉ nhận DTO, chuyển cho service, xây `Location` và status. `ResponseEntity.of(Optional)` tự chọn `200` với body hoặc `404` khi `Optional.empty()`; response 404 ở ví dụ không có body tùy biến.

**Lưu ý với reverse proxy:** `ServletUriComponentsBuilder.fromCurrentRequestUri()` dựa trên URI request mà ứng dụng nhìn thấy; khi server đứng sau gateway/proxy, cấu hình forwarded headers chuẩn và tin cậy mới giúp `Location` phản ánh public URL. Không tự tin tưởng header `X-Forwarded-*` từ client không đáng tin. Nếu muốn đường dẫn tương đối, xác định hợp đồng URL và cách phát Location tương ứng.

### 8.7. Cấu hình database demo

Tệp `src/main/resources/application.properties` cho ứng dụng **học tập**:

```properties
spring.datasource.url=jdbc:h2:mem:customers;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.open-in-view=false
```

- `create-drop` chỉ phù hợp môi trường test/demo vì xóa/tạo schema theo vòng đời ứng dụng.
- `open-in-view=false` giúp thực hành ranh giới transaction rõ ràng; mapper chuyển object trước khi trả response. Không dựa vào lazy database reads trong giai đoạn render.
- Trong production, dùng migration có quản lý (Flyway/Liquibase) và database thật, không dựa vào embedded H2 hoặc tự động drop schema.

Chạy Maven Wrapper đã tạo sẵn:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Lưu ý các lớp Spring/JPA không thể biên dịch độc lập với `javac` nếu không có dependencies. Tệp/command ở phần này đủ để định hình ứng dụng **khi đã tạo project Boot chứa dependencies và Maven Wrapper nêu trên**.

## 9. HTTP request/response của ví dụ

### 9.1. Tạo customer: status 201 và Location

```bash
curl -i http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  --data '{"displayName":"Lan","email":"lan@example.com"}'
```

Ví dụ response (ID/timestamp là minh họa, khác nhau mỗi lần chạy):

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/customers/1
Content-Type: application/json

{"id":1,"displayName":"Lan","email":"lan@example.com","createdAt":"2026-10-21T04:00:00Z"}
```

Các thuộc tính `id` và `createdAt` do server tạo; không lấy cứng giá trị minh họa làm test. **Không có `internalMemo`**, dù DB lưu trường đó. `Location` trỏ đến URI resource mới được tạo.

### 9.2. GET một customer

```bash
curl -i -H "Accept: application/json" \
  http://localhost:8080/api/customers/1
```

Nếu customer id=1 tồn tại, status `200` và body dùng cùng cấu trúc `CustomerResponse`. Nếu không có record id=9999:

```bash
curl -i http://localhost:8080/api/customers/9999
```

Dự kiến `404 Not Found`, không có JSON DTO; `ResponseEntity.of(Optional.empty())` thường tạo body rỗng. Không đồng nhất “không tìm thấy” với “chưa đăng nhập/không có quyền”: trong sản phẩm thật, chiến lược 403 hay che tồn tại tài nguyên bằng 404 là quyết định security/contract riêng.

### 9.3. Validation input, không tạo record

```bash
curl -i http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  --data '{"displayName":"","email":"invalid"}'
```

Với `spring-boot-starter-validation`, `@Valid` trên request DTO từ chối tên trống/email sai cú pháp. Mặc định thường là `400 Bad Request` (`MethodArgumentNotValidException`). **Không cam kết định dạng body lỗi**; Spring Boot có thể trả JSON lỗi khác nhau tùy cấu hình `ProblemDetail`/ControllerAdvice.

Với `Content-Type: text/plain` gửi vào endpoint chỉ `consumes=application/json`, thường `415 Unsupported Media Type`, xảy ra ở điều kiện media type trước validation. Với `Accept: application/xml` nhưng endpoint chỉ `produces=application/json`, thường `406 Not Acceptable`. Đây là vấn đề giao thức, không phải lỗi entity mapping.

## 10. Kiểm thử tích hợp MockMvc: chứng minh ranh giới

Dùng dependency `spring-boot-starter-test` (JUnit Jupiter, MockMvc). Test tích hợp sau chạy với H2 cấu hình demo và bao phủ **HTTP response contract**. Đặt tại `src/test/java/com/example/demo/CustomerControllerTest.java`:

```java
package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void createsResourceWithLocationAndPublicDtoOnly() throws Exception {
        MvcResult created = mvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(
                    "{\"displayName\":\"Lan\",\"email\":\"lan@example.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.displayName").value("Lan"))
            .andExpect(jsonPath("$.email").value("lan@example.com"))
            .andExpect(jsonPath("$.internalMemo").doesNotExist())
            .andReturn();

        Number id = JsonPath.read(
                created.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/customers/{id}", id.longValue())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.intValue()))
            .andExpect(jsonPath("$.internalMemo").doesNotExist());
    }

    @Test
    void missingCustomerReturns404() throws Exception {
        mvc.perform(get("/api/customers/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidInputReturns400() throws Exception {
        mvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"\",\"email\":\"bad\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void wrongRequestMediaTypeReturns415() throws Exception {
        mvc.perform(post("/api/customers")
                .contentType(MediaType.TEXT_PLAIN)
                .content("Lan"))
            .andExpect(status().isUnsupportedMediaType());
    }
}
```

Chạy:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

Đây là **mẫu code kiểm thử**, không phải khẳng định đã thực thi trong project hiện tại. Nếu dự án đã bật Spring Security, CSRF, tenant filter, Jackson serialization hoặc DB constraint riêng, phải cấu hình test và assertions đúng với môi trường đó. Trong test trên, `@Transactional` giúp các thay đổi DB trên thread test/MockMvc thường được rollback sau test; HTTP server thật dùng transaction khác nên không suy từ test này rằng mọi end-to-end request đều tự rollback.

### Test thêm khi hợp đồng phức tạp

- Kiểm tra JSON **không có field nội bộ** bằng `jsonPath("$.internalMemo").doesNotExist()`. Đây là test regression quan trọng khi entity về sau thêm getters.
- Kiểm tra `Location` thực sự trỏ tới URI có thể GET, không chỉ header tồn tại.
- Kiểm tra status/response shape khi data không tồn tại, input validation lỗi, requester không đủ quyền.
- Kiểm tra ngày `createdAt` có thể parse theo ISO-8601, thay vì so sánh timestamp bằng chuỗi cố định.
- Nếu có nested DTO, test không serialize quan hệ ngoài danh sách công khai.
- Với list endpoint, test query count/pagination để tránh N+1 khi dữ liệu tăng.

## 11. Mapping DTO khi có quan hệ: tránh vòng lặp và N+1

Giả sử entity `OrderEntity` chứa `@ManyToOne(fetch=LAZY) CustomerEntity customer` và `@OneToMany List<OrderLineEntity> lines`. Một response hợp đồng có thể là:

```java
public record OrderResponse(
        Long id,
        CustomerSummary customer,
        java.util.List<LineResponse> lines) {}

public record CustomerSummary(Long id, String displayName) {}

public record LineResponse(
        String productCode, int quantity) {}
```

Những record này **là đoạn thiết kế DTO minh họa**, không phải tệp độc lập có package/import hoàn chỉnh. Mapper quyết định chỉ đưa `customer.id`, `customer.displayName` và vài field của line ra ngoài; không serialize `customer.orders`, `orderLine.order` hay field quản trị.

Cẩn thận:

- Đọc `order.getCustomer().getDisplayName()` hoặc `order.getLines()` có thể kích hoạt lazy query. Cần fetch plan phù hợp theo use case.
- Với list 100 orders, mỗi order lại truy vấn customer/lines có thể thành N+1 dù response DTO rất nhỏ.
- Dùng **fetch join**, `@EntityGraph`, projection hoặc truy vấn riêng theo batch tùy cardinality và pagination. Fetch join collection cùng pageable có thể tạo duplicate/khó phân trang; đừng tự thêm `JOIN FETCH` mọi nơi.
- Map trong transaction nơi quan hệ có thể được truy cập an toàn, rồi trả DTO. `open-in-view=false` giúp phát hiện phụ thuộc lazy khi vượt ranh giới.
- Không đưa `HibernateProxy`/`PersistentCollection` ra JSON như đối tượng DTO.

Đặt JSON nested ở mức độ vừa đủ; client có thể gọi endpoint tài nguyên liên quan nếu dữ liệu quá lớn hoặc quyền đọc khác nhau.

## 12. DTO projection giúp tối ưu việc truy vấn

Mapping entity đầy đủ → DTO là giải pháp rõ ràng, nhưng có thể tốn RAM/query khi chỉ cần vài cột. Spring Data JPA hỗ trợ **class-based DTO projections**, có thể dùng Java record nếu constructor phù hợp.

Ví dụ JPQL constructor expression:

```java
public interface CustomerQueryRepository
        extends org.springframework.data.repository.Repository<CustomerEntity, Long> {

    @org.springframework.data.jpa.repository.Query(
        "select new com.example.demo.CustomerResponse(" +
        "c.id, c.displayName, c.email, c.createdAt) " +
        "from CustomerEntity c where c.id = :id")
    java.util.Optional<CustomerResponse> findPublicById(
            @org.springframework.data.repository.query.Param("id") Long id);
}
```

Đây là **tệp minh họa** cần đặt đúng package, có `CustomerEntity`/`CustomerResponse` đã khai báo; có thể thêm thành repository riêng nếu muốn thử. Ý tưởng là query lấy trực tiếp các cột DTO cần, thay vì nạp entity đầy đủ rồi map. JPQL `new` cần **tên class đầy đủ**, constructor phù hợp đúng thứ tự và kiểu; không phải mọi native query tự map vào record được.

Lựa chọn:

| Cách đọc | Khi phù hợp | Điều cần theo dõi |
| --- | --- | --- |
| Entity → manual mapper | Domain logic cần entity, response nhỏ | Lazy loading, N+1, field nội bộ |
| Entity → MapStruct mapper | Mapping nhiều field, DTO lặp lại | Cấu hình unmapped fields và nested mapping |
| Spring Data interface projection | Chỉ cần một vài property/query | Nested projection có thể join sâu tùy cấu trúc |
| Class/record DTO projection | Query đọc thuần, chỉ vài cột | Constructor mapping, JPQL/native query |
| Truy vấn SQL tối ưu + mapping thủ công | Read-heavy endpoint rất phức tạp | SQL/database coupling và test |

**Projection không thay authorization**: nếu truy vấn projection chứa email, dữ liệu vẫn đã được lấy từ DB, dù DTO khác entity. Áp điều kiện tenant/user ở query hoặc service theo chính sách; ưu tiên giới hạn dữ liệu sớm nhất có thể.

## 13. List response và phân trang

Trả nguyên `List<CustomerEntity>` cho hàng chục nghìn bản ghi khiến response nặng và lộ schema. Khi API trả danh sách, dùng dạng payload đã thiết kế:

```json
{
  "items": [
    {"id":1,"displayName":"Lan","email":"lan@example.com",
     "createdAt":"2026-10-21T04:00:00Z"}
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1
}
```

Một DTO riêng:

```java
public record PageResponse<T>(
        java.util.List<T> items,
        int page,
        int size,
        long totalItems) {}
```

Đoạn này là **thiết kế DTO độc lập có thể đặt trong một tệp Java**, nhưng chưa phải controller runnable. Dùng `Pageable`/`Page<Entity>` ở repository, map mỗi entity thành response DTO rồi đóng gói các thông số paging. Tài liệu API phải nói `page` bắt đầu từ 0 hay 1, thứ tự sort ổn định và maximum `size`. Tránh expose trực tiếp cấu trúc `PageImpl` hoặc Hibernate internals chỉ vì chúng có thể serialize.

Khi phân trang:

- Đảm bảo sort có tie-breaker ổn định nếu muốn tránh mất/trùng bản ghi do nhiều hàng có cùng timestamp.
- Dùng kích thước trang tối đa, limit dữ liệu trả về và projection nếu phù hợp.
- Với dữ liệu lớn/thay đổi nhanh, cân nhắc cursor pagination; không bắt buộc nếu nhu cầu đơn giản.
- Xem log/query metrics; DTO không tự giảm số row query hoặc query N+1.

## 14. Nullability, Optional, định dạng và thay đổi hợp đồng

### 14.1. Null và dữ liệu chưa có

Ví dụ customer có `phoneNumber` tùy chọn. API có thể trả:

```json
{"phoneNumber":null}
```

hoặc bỏ hẳn `phoneNumber` tùy hợp đồng. Jackson có thể được cấu hình `@JsonInclude(Include.NON_NULL)` để bỏ field null; tuy nhiên đừng thay đổi chính sách chỉ để response “đẹp hơn”, vì client có thể phân biệt missing và explicit null.

- Thiết kế DTO nói rõ field nào required, field nào optional.
- Tránh để `Optional<T>` làm field DTO chỉ để biểu thị nullable; với nhiều serializer/config, behavior không rõ bằng trường nullable và schema tường minh.
- `record` không cấm thành phần có `null`. Nếu client không được phép nhận null, kiểm tra bất biến ở mapper/service hoặc bằng contract test.
- Dùng `Instant` cho thời điểm UTC; `LocalDate` cho ngày không có timezone; không trả epoch string khó hiểu khi API yêu cầu ISO-8601.
- Không đưa `BigDecimal` qua `double` rồi đánh mất độ chính xác; mô tả precision/scale cho giá tiền.

### 14.2. CREATE/PUT/PATCH cần DTO riêng

Request và response DTO thường **không đối xứng**:

- `CreateCustomerRequest` không có `id`, `createdAt` vì server sinh chúng.
- `UpdateCustomerRequest` có thể yêu cầu toàn bộ field người dùng sửa được.
- `PatchCustomerRequest` thường cần phân biệt field **vắng mặt**, explicit `null` và giá trị mới. Plain nullable Java field trong DTO có thể gộp missing/null sau Jackson deserialization.
- `CustomerResponse` chứa metadata để client biết kết quả, nhưng không buộc client gửi lại mọi field.

Ví dụ PATCH:

| Input JSON | Ý nghĩa ví dụ |
| --- | --- |
| `{}` | Không thay đổi email |
| `{"email":null}` | Xóa email nếu policy cho phép |
| `{"email":""}` | Đặt chuỗi rỗng hoặc validation error |
| `{"email":"new@example.com"}` | Thay email |

Không dùng quy tắc `if (request.email() != null) update` nếu hợp đồng yêu cầu explicit null để xóa: cách đó đã mất phân biệt missing/null. Có thể dùng `JsonNode`/presence tracking/JSON Merge Patch theo hợp đồng. Trong JSON Merge Patch (RFC 7396), null trong patch có semantics xóa member. Đây là thiết kế **input**, không nên làm thay đổi response DTO ngẫu nhiên.

### 14.3. DTO là hợp đồng cần versioning

Thay đổi tương đối an toàn có thể là **thêm field tùy chọn** nếu client chịu được unknown fields; thay đổi tên/kiểu field hoặc ý nghĩa giá trị có thể phá client. Một số client deserialize nghiêm ngặt nên ngay cả thêm field cũng cần đánh giá. Các thay đổi có ý nghĩa lớn cần chiến lược tương thích: endpoint version, media type version hoặc ra DTO mới theo contract.

Không dùng tên field dựa trên entity như `customerV3TableId` nếu client không cần biết. Khi refactor database, ưu tiên giữ nguyên JSON contract; mapping layer chịu trách nhiệm bắc cầu.

## 15. Nơi xử lý lỗi HTTP: service kết quả nghiệp vụ, controller/advice response

Trong ví dụ, `service.findById` trả `Optional<CustomerResponse>` và controller trả `ResponseEntity.of(...)`: đây là thiết kế đơn giản cho 404.

Ứng dụng lớn có thể dùng exception nghiệp vụ như `CustomerNotFound`, `CustomerConflict`, rồi ánh xạ tại `@RestControllerAdvice` thành status/body lỗi chuẩn. Lúc đó:

- Service nêu vấn đề **nghiệp vụ**, không rải `new ResponseStatusException(HttpStatus.NOT_FOUND, ...)` ở sâu trong repository nếu muốn giữ khả năng tái sử dụng nhiều transport.
- Controller/advice quyết định `404`, `409`, `400`, headers, `ProblemDetail` và định dạng thông báo an toàn.
- Validation của `@Valid @RequestBody` thường gây `MethodArgumentNotValidException` và 400 trước khi vào service; không phải mọi lỗi nghiệp vụ đều là validation annotation.
- JSON sai cú pháp gây lỗi đọc body (thường 400) trước khi tạo request DTO; `consumes` không khớp thường 415.
- Ngoại lệ hạ tầng/database không nên bị bắt bằng `catch (Exception) { return 400; }`, vì lỗi nội bộ 500 sẽ bị báo nhầm là lỗi client.

Spring Framework hỗ trợ `ProblemDetail` (RFC 9457) và `ResponseEntityExceptionHandler` để tùy chỉnh lỗi có cấu trúc. Khi tạo body lỗi, hạn chế `detail` chứa thông tin nhạy cảm hay tên bảng/query. Mã lỗi public và correlation ID giúp client debug mà không để lộ internals.

## 16. Bảo mật DTO: chỉ chọn field chưa đủ

Bảo vệ dữ liệu có hai bước độc lập nhưng liên quan:

1. **Authorization trước hoặc trong truy vấn:** user hiện tại có quyền đọc customer này không? Có áp tenant ID và phạm vi hàng dữ liệu đúng không?
2. **Field-level visibility:** sau khi có quyền đọc resource, user có được nhận email, địa chỉ, giá mua nội bộ hoặc trạng thái gian lận không?

DTO đảm bảo không serializing những trường **không được khai báo trong DTO đó**. Nếu mapping lấy thêm email cho user không có quyền xem, DTO vẫn lộ email. Với nhiều nhóm quyền, dùng response schema khác nhau hoặc mapper phân quyền rõ ràng; đừng serialize một “admin response” rồi dựa hoàn toàn vào client ẩn field.

Các rủi ro thực tế:

- **Mass assignment** khi bind entity từ JSON đầu vào.
- **Broken Object Level Authorization (BOLA)** khi client đổi `/customers/42` thành ID người khác.
- **Sensitive Data Exposure** khi API trả raw entity hoặc log body.
- **Inconsistent projection** khi GET dùng DTO an toàn nhưng endpoint list/export trả thẳng entity.
- **Caching dữ liệu cá nhân:** `Cache-Control` và private/public cache phải phù hợp; DTO không tự giải quyết cache leak.
- **Lỗi debug:** `toString()` của DTO hoặc entity có thể in email/token vào log; cân nhắc masking.

Test nên bao phủ các endpoint của cùng resource (create/get/list/export), không chỉ một GET happy-path.

## 17. Hiệu năng và transaction boundary

DTO và entity separation thường cải thiện tính kiểm soát nhưng không tự động làm API nhanh hơn. Để đo:

- **Số query SQL:** N+1 có thể xuất hiện ở bước mapping quan hệ.
- **Kích thước dữ liệu lấy từ DB:** nếu chỉ cần vài cột, projection có thể giảm I/O.
- **Kích thước JSON:** không đưa quan hệ/metadata thừa vào response.
- **Độ sâu nested data:** mô hình cây quá lớn làm serialization tốn CPU/RAM.
- **Transaction duration:** tránh giữ transaction mở khi response được serialize/chờ mạng; map DTO trong transaction rồi đóng.
- **Pagination:** giới hạn số bản ghi và kết hợp sort ổn định.
- **Cache:** DTO public ổn định thuận lợi cho conditional requests (`ETag`/`Last-Modified`) nếu có chiến lược nhất quán.

Ví dụ status HTTP `304 Not Modified` liên quan cache validator của representation, khác chuyện object lưu trữ có thay đổi hay không. Không gắn raw `@Version` của JPA làm HTTP `ETag` mà chưa xem xét quyền riêng tư, biến thể response và semantics (ETag mạnh/yếu). ETag nên đại diện cho representation mà client nhận theo hợp đồng.

## 18. Anti-pattern và cách thay thế

| Anti-pattern | Vấn đề | Giải pháp |
| --- | --- | --- |
| `@GetMapping` trả `CustomerEntity` | Có thể lộ field, lazy proxy, vòng lặp quan hệ | Map sang `CustomerResponse` |
| `CreateCustomerRequest` dùng nguyên entity | Overposting, lẫn ID/server fields | Input DTO chỉ có field client được phép sửa |
| Repository trả `ResponseEntity<?>` | Persistence phụ thuộc HTTP | Repository trả entity/projection; controller quyết định status |
| Service trả `HttpServletResponse` | Trói business logic vào Servlet | Service trả kết quả/DTO; controller/advice lo HTTP |
| Mapping `BeanUtils.copyProperties` vô điều kiện | Dễ copy field ngoài chủ đích, mất kiểm soát | Mapper explicit/MapStruct với rule rõ |
| Một DTO “God Object” cho mọi endpoint | Quá nhiều field null/khó phân quyền | DTO chuyên biệt theo use case/public role |
| DTO nested chứa JPA entity | Vẫn lộ lazy/proxy/field nội bộ | Nested DTO cũng phải public-only |
| Tạo DTO sau khi transaction đóng rồi đọc lazy field | LazyInitializationException | Fetch/mapping trong transaction |
| Bật Open Session in View chỉ để fix serialize | Che phụ thuộc truy vấn khi render | Tối ưu fetch và mapping explicit |
| Trả toàn `Page<Entity>` | Lộ implementation shape và entity | Page response DTO có contract ổn định |
| Đổi tên field JSON theo mỗi schema migration | Phá client | Giữ contract và đổi mapper |
| Bỏ qua tenant check vì DTO đã bỏ field bí mật | Vẫn có thể lộ data của người khác | Authorization + field visibility |
| Trả `204` với body DTO | Vi phạm semantics response no-content | Dùng `200/201` nếu có body, hoặc `204` body rỗng |
| Trả `500` như lỗi validation cho mọi request | Client khó xử lý và che lỗi thật | Phân loại lỗi request/business/server đúng tầng |

## 19. Checklist review thiết kế response API

### Hợp đồng dữ liệu

1. JSON có đúng field mà client cần và không có field nội bộ không?
2. Tên field, kiểu số, timestamp, enum, null và collection có được định nghĩa ổn định không?
3. Request DTO chỉ chứa những giá trị client được phép gửi? Response DTO chỉ chứa dữ liệu client được phép nhận?
4. DTO nested có mức sâu/hạn mức phù hợp không? Có field nào lặp, quá rộng hoặc đòi quan hệ lazy vô ý?
5. Thay đổi schema DB có thể diễn ra mà không đổi JSON response không?

### Luồng ứng dụng và HTTP

6. Controller chịu trách nhiệm `ResponseEntity`, `Location`, HTTP status/headers không?
7. Service/repository có tránh phụ thuộc trực tiếp `HttpServletRequest`, `ResponseEntity`, `HttpStatus` khi không cần không?
8. `201` kèm `Location` chính xác khi tạo resource? GET thành công 200? Missing resource 404? 204 không body?
9. Validation, lỗi JSON, business conflict và server failure có response semantics riêng?
10. Có kiểm thử response schema và loại trừ các field riêng tư không?

### Data và security

11. Fetch plan/projection đã được đo để tránh N+1?
12. DTO được tạo trước khi trả ra khỏi transaction nếu cần đọc lazy data?
13. Authorization theo resource/tenant và field-level đã được áp?
14. Endpoint list/export cũng tuân theo DTO policy giống endpoint detail?
15. Log/cache/header có thể rò rỉ dữ liệu công khai chưa được phép không?

## 20. Câu hỏi tự kiểm tra

**1.** Vì sao không nên trả JPA entity trực tiếp từ `@RestController`?

**Đáp án:** Entity có thể lộ field nội bộ, ràng buộc API vào schema persistence, kích hoạt lazy query/vòng lặp JSON, gây khó khăn khi versioning. Response DTO xác định rõ contract công khai.

**2.** Input DTO và response DTO khác nhau thế nào?

**Đáp án:** Input DTO chỉ chứa field client được phép gửi và cần validate; response DTO chứa field server cam kết trả. Các giá trị `id`/`createdAt` do server sinh thường chỉ xuất hiện trong response.

**3.** Service có nhất thiết phải trả `ResponseEntity<CustomerResponse>`?

**Đáp án:** Không. Nó có thể trả `CustomerResponse`, `Optional<CustomerResponse>` hoặc kết quả nghiệp vụ. HTTP status/headers nên được xử lý tại controller/advice.

**4.** Làm sao trả 201 và Location sau POST create?

**Đáp án:** Tạo DTO chứa ID resource, xây URI, dùng `ResponseEntity.created(location).body(dto)`.

**5.** Có DTO rồi thì lazy loading/N+1 tự hết không?

**Đáp án:** Không. Mapper vẫn có thể đọc lazy relations và gây truy vấn thêm. Cần transaction boundary và fetch/query phù hợp.

**6.** Chỉ cần DTO không có field `internalMemo` là đủ bảo mật không?

**Đáp án:** Không. DTO chặn field đó khỏi serialization, nhưng phải kiểm tra người gọi có quyền đọc resource và những field DTO chứa (như email).

**7.** Khi truy vấn chỉ cần ID và tên, có nhất thiết fetch cả entity?

**Đáp án:** Không. Có thể dùng Spring Data JPA DTO projections/JPQL constructor expressions khi phù hợp, đồng thời đảm bảo query áp đúng authorization.

**8.** Nếu không tìm thấy record, service trả `Optional.empty()`, controller `ResponseEntity.of(...)` làm gì?

**Đáp án:** Trả HTTP 404; nếu có DTO thì trả 200 và body DTO.

**9.** Vì sao không dùng cùng một nullable DTO cho CREATE và PATCH khi explicit null có nghĩa “xóa”?

**Đáp án:** JSON field missing và explicit null có thể thành cùng Java null khi deserialize, làm mất ý định. PATCH cần cơ chế presence tracking hoặc chuẩn patch rõ ràng.

**10.** Mapper dùng cách copy tự động có thay thế review bảo mật không?

**Đáp án:** Không. Cần giới hạn field rõ ràng, policy theo role, test response công khai và đánh giá từng trường khi thêm mới.

## 21. Tài liệu tham khảo

- Spring Framework — `@ResponseBody` và serializing response: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responsebody.html
- Spring Framework — `ResponseEntity`, status/headers/body: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html
- Spring Framework — `@RequestBody` và validation: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html
- Spring Framework — MVC Controller Return Types: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html
- Spring Framework — Error Responses / ProblemDetail: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- Spring Data JPA — Projections: https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html
- Spring Data JPA — Transactionality: https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html
- Jakarta Persistence specification: https://jakarta.ee/specifications/persistence/
- RFC 9110 — HTTP Semantics (200, 201, 204, 404, Location, caching): https://www.rfc-editor.org/rfc/rfc9110.html
- RFC 9457 — Problem Details for HTTP APIs: https://www.rfc-editor.org/rfc/rfc9457.html
- OWASP — API Security Top 10: https://owasp.org/API-Security/editions/2023/en/0x11-t10/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rest-responses-006">Phân trang, sắp xếp, bộ lọc và hợp đồng danh sách kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Phân trang, sắp xếp, bộ lọc và hợp đồng danh sách kết quả trong Spring MVC

## 1. Bài toán: một endpoint trả danh sách cần cam kết điều gì?

Giả sử client gọi API danh sách sách:

~~~http
GET /api/books?page=0&size=2&sort=createdAt,desc&category=spring&available=true HTTP/1.1
Accept: application/json
~~~

Một thiết kế hợp lý cần trả lời đồng thời:
- **Bộ lọc (filtering):** những bản ghi nào đủ điều kiện xuất hiện, cách so khớp, cách diễn giải tham số vắng mặt hoặc rỗng?
- **Sắp xếp (sorting):** bản ghi nào đứng trước, có thứ tự mặc định không, có một khóa phân xử cuối để thứ tự ổn định không?
- **Phân trang (pagination):** client xin trang số mấy, số phần tử tối đa, trang đầu tính từ 0 hay 1, còn trang tiếp không?
- **Hợp đồng kết quả (list response contract):** dữ liệu, metadata, tổng phần tử, hướng điều hướng và lỗi có dạng JSON nào, ổn định qua các phiên bản không?
- **Giới hạn tài nguyên:** size tối đa bao nhiêu; ai giới hạn offset lớn, danh sách rỗng và chi phí đếm tổng?
- **Tính nhất quán:** tổng số bản ghi và nội dung trang được quan sát tại thời điểm nào; dữ liệu thay đổi giữa các request làm trang dịch chuyển ra sao?

Trong Spring MVC, `@RestController` và `@RequestParam` giải quyết phần **nhận HTTP input và tạo HTTP response**; Spring Data (nếu có) hỗ trợ `Pageable`/`Sort`/`Page` và repository query. **Spring MVC thuần không tự truy vấn database hay tự quy định toàn bộ cấu trúc JSON danh sách.** Hợp đồng là quyết định của API, cần định nghĩa và kiểm thử.

Ví dụ bài học dùng Java 17+, Spring Boot 3.x / Spring Framework 6.x. Có một ứng dụng runnable **không phụ thuộc database** để phân biệt rõ contract HTTP; phần sau chỉ ra cách nối Spring Data JPA và những vấn đề khi dữ liệu rất lớn.

## 2. Thiết kế URL trước khi viết controller

### 2.1. Một hợp đồng query minh bạch

Chọn endpoint:

~~~text
GET /api/books
  ?page=0
  &size=20
  &sort=createdAt,desc
  &category=spring
  &available=true
  &q=security
~~~

Định nghĩa chính thức cho ví dụ:

| Tham số | Kiểu | Mặc định/ý nghĩa | Giới hạn hoặc quy tắc |
| --- | --- | --- | --- |
| `page` | số nguyên | `0`, **zero-based** | Không âm |
| `size` | số nguyên | `20` | Từ 1 đến 50 |
| `sort` | chuỗi `field,direction` | `createdAt,desc` | `field ∈ {id,title,createdAt}`, `direction ∈ {asc,desc}` |
| `category` | chuỗi tùy chọn | Không lọc | So sánh không phân biệt hoa/thường, khớp cả giá trị |
| `available` | boolean tùy chọn | Không lọc | `true` hoặc `false` theo converter |
| `q` | chuỗi tùy chọn | Không lọc | Chuỗi con trong `title`, không phân biệt hoa/thường |

Đây là **hợp đồng do ứng dụng tự định nghĩa**. Không mặc nhiên gắn “size mặc định 20” hay sort format trên với mọi ứng dụng Spring: cấu hình Spring Data web và annotation `@PageableDefault` có thể khác.

Trong URL, các tham số cùng cấp thường kết hợp theo logic **AND**: ví dụ `category=spring&available=true` chỉ lấy sách vừa thuộc spring vừa đang available. Với tìm kiếm `q`, ta ghi rõ thuộc tính nào được tìm; đừng để client tin rằng `q` tìm trong mọi trường nếu server chỉ tìm tên.

Các tham số đầu vào thuộc **query string**, không phải JSON request body. Phần danh sách không cần `POST` chỉ để có bộ lọc đơn giản; với filter rất phức tạp và payload lớn, có thể định nghĩa endpoint search riêng, nhưng cần hợp đồng HTTP/cache rõ ràng.

### 2.2. Tránh nhập nhằng: index bắt đầu từ 0 hay 1?

Với zero-based:
- `page=0` là trang đầu.
- `page=1` là trang thứ hai.
- `size=2` là tối đa hai phần tử mỗi trang.
- Trong thuật toán offset, vị trí bắt đầu là `page × size`, ví dụ `page=2&size=10` bắt đầu từ offset 20.

Có API dùng one-based (`page=1` là trang đầu). Cả hai được nếu **công bố rõ** và response trả lại index tương ứng; không đổi quy ước trong một API hiện hữu mà không version/migration. `PageRequest.of(page,size)` của Spring Data dùng **zero-based**, dù web resolver có thể được cấu hình nhận one-based ở biên HTTP.

### 2.3. Giới hạn size và không cho request tùy ý ngốn tài nguyên

Giá trị `size=1000000` không được phép chỉ vì client gửi được một số nguyên lớn. Áp dụng:
- Size tối thiểu 1; đừng chia cho 0.
- Size tối đa được công bố, trong ví dụ là 50.
- Page không âm; tính `page × size` bằng kiểu `long` để tránh overflow `int`.
- Giới hạn quyền và số lượng request bằng API gateway/rate limiting khi cần.
- Cân nhắc giới hạn độ dài query, số điều kiện lọc, số cột sort, độ sâu offset; tránh wildcard/filter vô hạn.

**Chính sách lỗi của bài:** tham số đã parse nhưng vi phạm hợp đồng (size=0, field sort không có trong allowlist) → HTTP `400 Bad Request` kèm mã lỗi rõ nghĩa. Lỗi chuyển chuỗi `page=abc` sang `int` cũng thường được Spring MVC trả 400 trước khi vào method.

## 3. Phân trang theo offset: thuật toán, metadata và giới hạn

### 3.1. Phân trang phải dựa trên kết quả đã lọc và sắp xếp

Thứ tự xử lý về mặt ngữ nghĩa:

~~~text
Data source
   │
   ▼
Lọc theo category, available, q
   │
   ▼
Sắp xếp theo khóa được cho phép
   │
   ▼
Thêm khóa phân xử ổn định (ví dụ id)
   │
   ▼
Tính totalElements / totalPages nếu contract cần
   │
   ▼
Lấy cửa sổ offset..offset+size
   │
   ▼
Map entity/domain → response DTO
   │
   ▼
Trả items + page metadata + sort + filters
~~~

Nếu phân trang **trước khi lọc**, một trang có thể nhận rất ít kết quả dù các trang khác vẫn chứa bản ghi phù hợp. Nếu không có thứ tự rõ ràng, database có thể trả hai trang chồng lấn hoặc bỏ sót bản ghi khi execution plan thay đổi.

Với truy vấn SQL, ý tưởng (không phải câu SQL có thể áp cho mọi dialect) là:

~~~sql
SELECT id, title, category, created_at, available
FROM books
WHERE (:category IS NULL OR category = :category)
  AND (:available IS NULL OR available = :available)
ORDER BY created_at DESC, id ASC
LIMIT :size OFFSET :offset;
~~~

Đoạn SQL chỉ minh họa phép lọc/sort/offset. Với production nên tạo SQL có điều kiện động hoặc query builder tương ứng để tối ưu index; nhiều điều kiện `OR :param IS NULL` có thể làm query planner khó tối ưu. **Không tự nối input của client vào `ORDER BY`**: phải map tên sort từ allowlist sang thuộc tính/cột hợp lệ.

### 3.2. Công thức trang

Nếu số bản ghi phù hợp là `totalElements` và `size > 0`:

~~~text
offset      = page * size
totalPages  = totalElements == 0 ? 0 : ceil(totalElements / size)
hasNext     = page + 1 < totalPages
hasPrevious = page > 0 (chính sách có thể xét thêm totalElements)
~~~

Để tránh số thực và overflow, Java có thể tính `totalPages = (totalElements == 0 ? 0 : 1 + (totalElements - 1) / size)` với `long` phù hợp. API phải nhất quán ý nghĩa `totalPages` khi danh sách trống: trong ví dụ này **bằng 0**, `items=[]` và `totalElements=0`.

Ví dụ có 21 kết quả, size 10: `totalPages=3`, page 0 có 10, page 1 có 10, page 2 có 1. Trang `page=3` nằm ngoài phạm vi. Có thể chọn **200 với `items=[]`** hoặc **404/400** tùy hợp đồng sản phẩm; ở ví dụ này chọn **200 và trang rỗng**, giữ tổng số bản ghi và index requested để client tự quyết định quay lại trang trước.

### 3.3. Phần tử được trả khác với kích thước yêu cầu

- `size`: số phần tử **tối đa** client yêu cầu trên một trang.
- `numberOfElements`: số phần tử **thực sự** trong response hiện tại; có thể 0 hoặc nhỏ hơn size.
- `totalElements`: tổng phần tử **đáp ứng filter** (không phải tổng dữ liệu trong bảng).
- `totalPages`: số trang của tập **đã lọc** với size được yêu cầu.

Không dùng `items.length` thay `totalElements` khi giao diện cần tổng số bản ghi. Ngược lại, nếu API chỉ cần nút Next và dữ liệu quá lớn, việc tính `totalElements` có thể không đáng chi phí.

## 4. Sắp xếp đúng: allowlist, nhiều khóa và thứ tự ổn định

### 4.1. Cột nào được phép sort?

Một API không nên để client chọn tùy ý mọi property của entity. Ví dụ public contract cho phép:
- `id`: định danh tăng theo hệ thống demo.
- `title`: tên hiển thị.
- `createdAt`: thời điểm tạo.

Các property nội bộ như `passwordHash`, `internalScore`, `tenantId`, hoặc những phép join tốn kém không được tự động trở thành lựa chọn sort. Trong Spring Data, `Sort.Order` mang tên property, nhưng **resolver không phải cơ chế bảo mật/allowlist cho ứng dụng**; cần xác nhận lựa chọn client có hợp lệ rồi chuyển tới repository.

Nếu cần giữ API field name độc lập entity field name, dùng mapping tường minh:

~~~text
API "createdAt" → entity property "createdAt"
API "title"     → entity property "title"
API "id"        → entity property "id"
Khác ba lựa chọn  → 400 BAD_QUERY
~~~

### 4.2. Vì sao phải có khóa phụ phân xử?

Giả sử `ORDER BY created_at DESC` và ba sách có cùng timestamp. Database được quyền trả ba bản ghi cùng khóa theo thứ tự bất kỳ. Khi dùng `LIMIT/OFFSET`, hai request liên tiếp có thể phân trang không ổn định. Dùng:

~~~sql
ORDER BY created_at DESC, id ASC
~~~

`id` unique giúp tạo **thứ tự toàn phần** cho cùng snapshot dữ liệu. Chú ý: khóa phụ ổn định giải quyết tie trong một tập dữ liệu; nó **không xóa** vấn đề thêm/xóa bản ghi giữa hai lần lấy trang offset.

Khi client sort `title,asc`, ví dụ giữ mặc định phụ `id ASC`. Khi muốn hỗ trợ nhiều sort field của client, hãy công bố rõ thứ tự ưu tiên: `sort=createdAt,desc&sort=id,asc` trong Spring Data web thường thể hiện khóa thứ nhất rồi khóa thứ hai. **Ví dụ runnable bên dưới chủ ý chỉ hỗ trợ một trường sort chính**, tự thêm id ASC để đơn giản hóa kiểm thử.

### 4.3. Chữ hoa, dấu tiếng Việt, NULL và time zone

Thứ tự xếp chuỗi phụ thuộc collation của database và đối chiếu chữ hoa/thường; `String.CASE_INSENSITIVE_ORDER` trong Java không tương đương mọi collation SQL. Nếu API yêu cầu quy tắc tiếng Việt, phải quyết định locale/collation ở tầng lưu trữ và kiểm thử với dữ liệu dấu. Không hứa thứ tự giống nhau giữa Java in-memory, PostgreSQL/MySQL và client JavaScript.

Với thời gian, lưu `Instant`/UTC ở tầng dữ liệu nếu đó là timestamp tuyệt đối, dùng ISO-8601 có offset/UTC ở HTTP contract, xác định cách so sánh. Với nullable sort keys, công bố NULLS FIRST/LAST nếu client cần ổn định; thêm unique tie-breaker và index tương ứng.

## 5. Bộ lọc: semantics, null, tìm kiếm và bảo mật

### 5.1. Cần phân biệt không gửi, gửi rỗng và gửi giá trị

Ba request:

~~~text
GET /api/books
GET /api/books?category=
GET /api/books?category=spring
~~~

Có thể mang ba ý nghĩa tùy contract. Trong ví dụ này, `category` null hoặc chỉ whitespace đều được chuẩn hóa thành “không lọc”; `category=spring` thì lọc chính xác chuỗi category, không phân biệt hoa/thường.

Với `available`: `null` = không lọc, `true` = chỉ có hàng available, `false` = chỉ có hàng unavailable. Không dùng primitive `boolean` với default `false` nếu cần phân biệt **vắng mặt** và **chỉ lấy false**.

Với `q`: sau trim, tìm chuỗi con trên `title`, không phân biệt hoa/thường. Đây là phép tìm đơn giản cho demo, **không tự động là full-text search**, fuzzy search, tokenizer hoặc bỏ dấu tiếng Việt.

### 5.2. Filter nào kết hợp AND, filter nào OR?

Nếu hỗ trợ `category=java&category=spring`, cần ghi rõ semantics: OR giữa hai giá trị category, nhưng AND với `available=true` chẳng hạn. Ví dụ runnable chỉ nhận **một category**, vì mặc định nhiều tham số trùng tên trong HTTP có thể được resolver tổng hợp theo cách không mong muốn khi binding sang `String`.

Quy tắc hiển thị trên UI phải khớp backend: tổng số bản ghi, số trang và sort đều tính **trong tập sau khi áp dụng toàn bộ filters**. Khi user thay filter, UI thường nên quay về page 0 để không đứng ở trang vượt phạm vi mới.

### 5.3. Chống query abuse và tránh mở dữ liệu không có quyền

Input do client gửi không đáng tin: cần kiểm tra giá trị hợp lệ, kích thước filter, số lượng điều kiện, allowlist sort, và cấu hình timeout/index. **Filter quyền truy cập/tenant** phải do server xác định từ principal/context và luôn được áp dụng trong repository query, không tin tham số client như `tenantId=...` để quyết định quyền.

Khi truy vấn database, dùng parameter binding của JPA/PreparedStatement/DSL thay vì nội suy các giá trị lọc thành SQL. Đừng ghép `ORDER BY ` + raw sort field từ client. SQL injection không phải rủi ro duy nhất: sort theo cột nội bộ hoặc join quá tốn kém cũng gây tổn hại hiệu năng và lộ cấu trúc.

## 6. Định nghĩa hợp đồng JSON danh sách kết quả

Một JSON envelope rõ nghĩa giúp frontend không phải đoán thông tin từ framework:

~~~json
{
  "items": [
    {"id": 106, "title": "Testing Spring", "category": "spring", "createdAt": "2025-02-15", "available": false},
    {"id": 105, "title": "Spring Security", "category": "spring", "createdAt": "2024-06-01", "available": true}
  ],
  "page": {
    "number": 0,
    "size": 2,
    "numberOfElements": 2,
    "totalElements": 4,
    "totalPages": 2,
    "first": true,
    "last": false,
    "hasNext": true,
    "hasPrevious": false
  },
  "sort": {"field": "createdAt", "direction": "desc"},
  "filters": {"q": null, "category": "spring", "available": null}
}
~~~

Đây là **hợp đồng do bài học tự định nghĩa**, không phải JSON mặc định của `Page<T>`. Chính vì thế controller nên map dữ liệu ra DTO/envelope riêng.

Các điểm cần thống nhất:
- `items` **luôn là mảng**, kể cả không có phần tử. Tránh `null` hoặc vắng field không có lý do.
- `page.number` dùng zero-based như query.
- `totalElements` là tổng trên tập đã lọc, không phải toàn catalog.
- `totalPages` bằng 0 khi `totalElements=0` trong hợp đồng này.
- `first` / `last` dựa trên quy ước của API. Có thể đặt cả hai `true` cho empty dataset ở page 0, nếu lựa chọn này được ghi rõ.
- `sort` trả lại sort **đã xác nhận và chuẩn hóa**; `filters` trả lại điều kiện thực tế.
- Với `available=false`, phải giữ boolean `false`, không nhầm với filter không có (`null`).

Một response trang ngoài phạm vi vẫn trả HTTP **200** và:

~~~json
{
  "items": [],
  "page": {
    "number": 10, "size": 2, "numberOfElements": 0,
    "totalElements": 4, "totalPages": 2,
    "first": false, "last": true,
    "hasNext": false, "hasPrevious": true
  },
  "sort": {"field": "createdAt", "direction": "desc"},
  "filters": {"q": null, "category": "spring", "available": null}
}
~~~

`last=true` trong trang out-of-range thể hiện “không còn trang kế tiếp”, **không có nghĩa page 10 là trang hợp lệ cuối cùng**. Vì vậy frontend nên dựa thêm `totalPages` khi render link. Có ứng dụng chọn 404 cho page out-of-range; nên xác định chính sách và test, không thay đổi ngầm khi refactor.

### 6.1. Có nên trả `Page<Entity>` trực tiếp?

Spring Data `Page<T>` hữu ích **trong Java application/service/repository**; nhưng việc serialize trực tiếp một `PageImpl` thành JSON có thể khiến frontend phụ thuộc vào chi tiết implementation của thư viện. Spring Data có cảnh báo về tính ổn định của format này; từ Spring Data 3.3 có `org.springframework.data.web.PagedModel` và `PageSerializationMode.VIA_DTO` làm một lựa chọn representation ổn định hơn.

Những phương án hợp lý:
1. **Tự thiết kế DTO envelope** như ví dụ, kiểm soát tuyệt đối tên field/compatibility.
2. Dùng `PagedModel` / `VIA_DTO` khi format chuẩn của Spring Data đáp ứng contract, kiểm tra version và field shape thực tế.
3. Dùng Spring HATEOAS khi muốn navigation links theo hypermedia, thay vì tự chế nhiều URL và đoán link.

Không assume `Page<T>` phải luôn xuất ra trường `content` hoặc `pageable` như nhau qua mọi Spring Data release. Hãy snapshot/test JSON API công khai trước khi nâng cấp dependency.

### 6.2. Đưa link điều hướng hay chỉ metadata?

Với REST list contract, có thể bổ sung:

~~~json
{
  "links": {
    "self": "/api/books?page=0&size=2&sort=createdAt%2Cdesc&category=spring",
    "next": "/api/books?page=1&size=2&sort=createdAt%2Cdesc&category=spring"
  }
}
~~~

Trên đây là **fragment minh họa thêm trường `links`**; không phải response runnable bên dưới. Khi tạo links, phải giữ đủ filters, sort và size; URL-encode đúng, không tin raw user-supplied Host nếu tạo absolute links sau reverse proxy. Nếu `hasNext=false`, có thể bỏ `next` thay vì đặt đường dẫn sai.

## 7. Ví dụ runnable: Spring Boot REST catalog dùng danh sách trong bộ nhớ

Để kiểm tra HTTP semantics độc lập với database, ví dụ sau dùng sáu bản ghi cố định và thực hiện filter → sort → page. Trong môi trường thật, chuyển bộ lọc/sort/page xuống database (tránh fetch toàn bộ rows rồi sort trong heap).

Cấu trúc:

~~~text
catalog-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/java/com/example/catalog/
    │   ├── CatalogApplication.java
    │   └── web/
    │       ├── BookController.java
    │       ├── BookCatalogService.java
    │       └── CatalogErrorHandler.java
    └── test/java/com/example/catalog/web/
        └── BookControllerTest.java
~~~

### 7.1. Gradle và Spring Boot

`settings.gradle`:

~~~groovy
rootProject.name = 'catalog-demo'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.5'
    id 'io.spring.dependency-management' version '1.1.6'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

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
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

Không cần `spring-boot-starter-data-jpa` trong ví dụ này; pagination là thuật toán của service trong bộ nhớ. Điều này giúp thấy `@RequestParam` và JSON DTO hoạt động hoàn toàn bằng Spring MVC. Project Gradle cần wrapper thích hợp, ví dụ Gradle 8.x chạy với Java 17.

`src/main/java/com/example/catalog/CatalogApplication.java`:

~~~java
package com.example.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CatalogApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
~~~

### 7.2. Controller: định nghĩa contract ở HTTP boundary

`src/main/java/com/example/catalog/web/BookController.java`:

~~~java
package com.example.catalog.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookCatalogService service;

    public BookController(BookCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<BookItem> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "available", required = false) Boolean available,
            @RequestParam(name = "q", required = false) String q) {

        return service.find(page, size, sort, category, available, q);
    }

    public record BookItem(
            long id, String title, String category,
            String createdAt, boolean available) {
    }

    public record PageMeta(
            int number, int size, int numberOfElements,
            long totalElements, long totalPages,
            boolean first, boolean last, boolean hasNext, boolean hasPrevious) {
    }

    public record SortMeta(String field, String direction) {
    }

    public record FilterMeta(String q, String category, Boolean available) {
    }

    public record PageResponse<T>(
            List<T> items, PageMeta page,
            SortMeta sort, FilterMeta filters) {
    }
}
~~~

Ở đây:
- `@RestController` serialize `PageResponse` bằng Jackson thành JSON.
- `@RequestParam` nhận thông tin HTTP và chuyển `page`/`size` thành `int`, `available` thành `Boolean`.
- Controller chỉ điều phối. Service chịu trách nhiệm validate chính sách page/size/sort, normalize filter, chọn dữ liệu và đóng metadata.
- Các record định nghĩa **JSON response contract** cụ thể, không expose repository entity hoặc framework internals.
- `PageMeta.totalElements`/`totalPages` dùng `long` để phù hợp dataset lớn về mặt JSON; demo in-memory hiển nhiên nhỏ.

### 7.3. Service: lọc → sort → phân trang → DTO

`src/main/java/com/example/catalog/web/BookCatalogService.java`:

~~~java
package com.example.catalog.web;

import com.example.catalog.web.BookController.BookItem;
import com.example.catalog.web.BookController.FilterMeta;
import com.example.catalog.web.BookController.PageMeta;
import com.example.catalog.web.BookController.PageResponse;
import com.example.catalog.web.BookController.SortMeta;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class BookCatalogService {

    private record Book(
            long id, String title, String category,
            LocalDate createdAt, boolean available) {
    }

    private static final List<Book> BOOKS = List.of(
            new Book(101, "Java Basics", "java",
                    LocalDate.parse("2023-05-01"), true),
            new Book(102, "Spring MVC", "spring",
                    LocalDate.parse("2024-01-10"), true),
            new Book(103, "Spring Data", "spring",
                    LocalDate.parse("2024-03-12"), false),
            new Book(104, "Java Concurrency", "java",
                    LocalDate.parse("2024-05-20"), true),
            new Book(105, "Spring Security", "spring",
                    LocalDate.parse("2024-06-01"), true),
            new Book(106, "Testing Spring", "spring",
                    LocalDate.parse("2025-02-15"), false)
    );

    private record SortSelection(String field, String direction) {
    }

    public PageResponse<BookItem> find(
            int page, int size, String rawSort,
            String category, Boolean available, String q) {

        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("size must be between 1 and 50");
        }

        SortSelection sort = parseSort(rawSort);
        String categoryFilter = normalize(category);
        String qFilter = normalize(q);

        Comparator<Book> primary = switch (sort.field()) {
            case "id" -> Comparator.comparingLong(Book::id);
            case "title" ->
                    Comparator.comparing(Book::title, String.CASE_INSENSITIVE_ORDER);
            case "createdAt" -> Comparator.comparing(Book::createdAt);
            default -> throw new IllegalStateException("Unexpected sort field");
        };

        if ("desc".equals(sort.direction())) {
            primary = primary.reversed();
        }

        // ID duy nhất là tie-breaker để thứ tự xác định.
        Comparator<Book> order = primary.thenComparingLong(Book::id);

        List<Book> matched = BOOKS.stream()
                .filter(book -> categoryFilter == null
                        || book.category().equalsIgnoreCase(categoryFilter))
                .filter(book -> available == null
                        || book.available() == available)
                .filter(book -> qFilter == null
                        || book.title().toLowerCase(Locale.ROOT)
                                .contains(qFilter.toLowerCase(Locale.ROOT)))
                .sorted(order)
                .toList();

        long totalElements = matched.size();
        long totalPages = totalElements == 0
                ? 0 : 1 + (totalElements - 1) / size;
        long start = (long) page * size;

        List<BookItem> items;
        if (start >= totalElements) {
            items = List.of();
        } else {
            int from = (int) start;
            int to = (int) Math.min(totalElements, start + size);
            items = matched.subList(from, to).stream()
                    .map(book -> new BookItem(
                            book.id(), book.title(), book.category(),
                            book.createdAt().toString(), book.available()))
                    .toList();
        }

        boolean hasNext = (long) page + 1 < totalPages;
        boolean hasPrevious = page > 0 && totalElements > 0;
        PageMeta pageMeta = new PageMeta(
                page, size, items.size(),
                totalElements, totalPages,
                page == 0,
                !hasNext,
                hasNext,
                hasPrevious);

        return new PageResponse<>(
                items,
                pageMeta,
                new SortMeta(sort.field(), sort.direction()),
                new FilterMeta(qFilter, categoryFilter, available));
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    private static SortSelection parseSort(String raw) {
        String[] parts = raw.split(",", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "sort must have format field,direction");
        }

        String field = parts[0].trim();
        String direction = parts[1].trim().toLowerCase(Locale.ROOT);
        if (!Set.of("id", "title", "createdAt").contains(field)
                || !Set.of("asc", "desc").contains(direction)) {
            throw new IllegalArgumentException(
                    "sort field or direction is not supported");
        }
        return new SortSelection(field, direction);
    }
}
~~~

**Giải thích các lựa chọn có chủ đích:**
1. `BOOKS` là fixture bất biến: dễ tái hiện kết quả. Production phải filter/sort/page **trong database** thay vì kéo toàn bộ bảng vào JVM.
2. `Boolean available` phân biệt `null` (không lọc) với `false`.
3. `normalize` biến blank và whitespace thành không lọc, nhưng không tự chuẩn hóa nội dung của `category` trong metadata thành chữ thường; so khớp dữ liệu dùng `equalsIgnoreCase`.
4. `parseSort` kiểm tra field/direction theo allowlist. Có sort thứ hai tự động `id ASC` để kết quả xác định khi giá trị sort chính bằng nhau.
5. `start` dùng `long` để tránh overflow phép nhân `int`. Chỉ cast về `int` khi `start < matched.size()`, an toàn cho `List` trong JVM.
6. Khi không có dữ liệu, `totalPages=0`, `first=true` nếu page=0 và `last=true` vì không còn trang sau.
7. `hasPrevious` là metadata cho hành vi client trong ví dụ; ở page ngoài phạm vi mà dữ liệu vẫn tồn tại, vẫn có thể quay về page trước.

Hàm `String.CASE_INSENSITIVE_ORDER` phục vụ Java demo; database production phải có collation/sort key tương thích với API contract. Nếu có nhiều records đồng timestamp, tie-breaker ID duy nhất giải quyết trùng thứ tự.

### 7.4. Trả lỗi input rõ ràng

`src/main/java/com/example/catalog/web/CatalogErrorHandler.java`:

~~~java
package com.example.catalog.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CatalogErrorHandler {

    public record ErrorBody(String code, String message) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> badQuery(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody("BAD_QUERY", ex.getMessage()));
    }
}
~~~

Đây là handler **cho các lỗi IllegalArgumentException do service ném**; ứng dụng production nên dùng exception domain cụ thể (ví dụ `InvalidListQueryException`), tránh biến mọi `IllegalArgumentException` từ bug logic thành 400.

Lỗi `page=xyz` trước khi vào service là lỗi chuyển kiểu của Spring MVC; nó thường trả **400** theo cơ chế exception mặc định, không phải JSON `ErrorBody` do method trên tạo. Muốn mọi lỗi cùng một schema, override các hook của `ResponseEntityExceptionHandler` hoặc dùng advice riêng cho conversion/binding và test.

**Lưu ý bảo mật:** không nên luôn trả raw exception message nếu exception có thể chứa SQL, internal identifier hay dữ liệu bí mật. Trong ví dụ, message do allowlist/validation tạo và không chứa payload nhạy cảm; production nên dùng message code an toàn và correlation ID.

## 8. Gọi thử API và hiểu kết quả

Chạy trong project có Gradle wrapper và Java 17:

~~~bash
./gradlew bootRun
~~~

Trên Windows có thể chạy `.\gradlew.bat bootRun`. Vì đây là ứng dụng Spring Boot, không chạy đơn lẻ bằng `javac BookController.java` mà thiếu classpath Spring.

### 8.1. Trang đầu cho category spring

~~~bash
curl -s 'http://localhost:8080/api/books?page=0&size=2&category=spring'
~~~

Sort mặc định `createdAt,desc`. Bốn sách spring theo thứ tự là:
1. ID `106` — Testing Spring (2025-02-15).
2. ID `105` — Spring Security (2024-06-01).
3. ID `103` — Spring Data (2024-03-12).
4. ID `102` — Spring MVC (2024-01-10).

Vì size=2, trang đầu có **106, 105**; `totalElements=4`, `totalPages=2`, `hasNext=true`, `numberOfElements=2`.

~~~json
{
  "items": [
    {"id":106,"title":"Testing Spring","category":"spring","createdAt":"2025-02-15","available":false},
    {"id":105,"title":"Spring Security","category":"spring","createdAt":"2024-06-01","available":true}
  ],
  "page": {
    "number":0,"size":2,"numberOfElements":2,
    "totalElements":4,"totalPages":2,
    "first":true,"last":false,"hasNext":true,"hasPrevious":false
  },
  "sort":{"field":"createdAt","direction":"desc"},
  "filters":{"q":null,"category":"spring","available":null}
}
~~~

### 8.2. Trang hai và lọc thêm available

~~~bash
curl -s 'http://localhost:8080/api/books?page=1&size=2&category=spring'
curl -s 'http://localhost:8080/api/books?page=0&size=2&category=spring&available=true'
~~~

Request đầu trả IDs `103`, `102`, `hasNext=false`, `hasPrevious=true`. Request thứ hai chỉ giữ IDs `105`, `102`; `totalElements=2` và `totalPages=1`. **Không giữ total=4 sau khi áp thêm filter available**.

### 8.3. Đổi sort không thay số phần tử khớp bộ lọc

~~~bash
curl -s 'http://localhost:8080/api/books?page=0&size=2&category=spring&sort=title,asc'
~~~

Thứ tự tiêu đề theo sort Java demo:
- `Spring Data` (103).
- `Spring MVC` (102).
- `Spring Security` (105).
- `Testing Spring` (106).

Page 0 nhận `103,102`. `totalElements` vẫn là 4: sort **đổi thứ tự**, không thêm/bớt bản ghi.

### 8.4. Trang vượt phạm vi và bộ lọc không có kết quả

~~~bash
curl -s 'http://localhost:8080/api/books?page=10&size=2&category=spring'
curl -s 'http://localhost:8080/api/books?q=NoSuchBook'
~~~

Đầu tiên trả HTTP **200**, `items=[]`, `number=10`, `totalElements=4`, `totalPages=2`. Thứ hai trả HTTP **200**, `items=[]`, `totalElements=0`, `totalPages=0`, vì không có sách tên chứa NoSuchBook.

Đây là trạng thái hợp lệ với một truy vấn danh sách. Trả 404 cho “không tìm thấy bản ghi đơn” (`GET /api/books/{id}`) là vấn đề khác với danh sách trống.

### 8.5. Giá trị sai contract

~~~bash
curl -i 'http://localhost:8080/api/books?page=-1'
curl -i 'http://localhost:8080/api/books?size=0'
curl -i 'http://localhost:8080/api/books?size=1000'
curl -i 'http://localhost:8080/api/books?sort=secret,asc'
curl -i 'http://localhost:8080/api/books?page=abc'
~~~

Bốn request đầu gây `IllegalArgumentException` từ service và được advice trả **400** với code `BAD_QUERY`. Request cuối sai chuyển kiểu `int`, Spring MVC cũng thường trả **400** nhưng body có thể khác vì đi qua exception handler mặc định.

**Không lẳng lặng clamp size=1000 thành 50 nếu hợp đồng yêu cầu báo 400.** Một sản phẩm khác có thể chủ động clamp; việc quan trọng là client biết chính sách và response báo lại size được thực thi.

## 9. Kiểm thử MockMvc cho toàn bộ hợp đồng danh sách

`src/test/java/com/example/catalog/web/BookControllerTest.java`:

~~~java
package com.example.catalog.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void filtersBeforePagingAndReturnsTotalForFilteredSet() throws Exception {
        mvc.perform(get("/api/books")
                        .param("page", "0")
                        .param("size", "2")
                        .param("category", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(106))
                .andExpect(jsonPath("$.items[1].id").value(105))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.hasNext").value(true))
                .andExpect(jsonPath("$.sort.field").value("createdAt"));
    }

    @Test
    void nextPageKeepsStableOrder() throws Exception {
        mvc.perform(get("/api/books")
                        .param("page", "1")
                        .param("size", "2")
                        .param("category", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(103))
                .andExpect(jsonPath("$.items[1].id").value(102))
                .andExpect(jsonPath("$.page.hasNext").value(false))
                .andExpect(jsonPath("$.page.hasPrevious").value(true));
    }

    @Test
    void availableFalseIsARealFilter() throws Exception {
        mvc.perform(get("/api/books")
                        .param("category", "spring")
                        .param("available", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(106))
                .andExpect(jsonPath("$.items[1].id").value(103))
                .andExpect(jsonPath("$.filters.available").value(false));
    }

    @Test
    void sortingByTitleChangesOrderNotTotal() throws Exception {
        mvc.perform(get("/api/books")
                        .param("category", "spring")
                        .param("size", "2")
                        .param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(103))
                .andExpect(jsonPath("$.items[1].id").value(102))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.sort.direction").value("asc"));
    }

    @Test
    void outOfRangePageReturnsEmptyArrayAndOriginalTotal() throws Exception {
        mvc.perform(get("/api/books")
                        .param("page", "10")
                        .param("size", "2")
                        .param("category", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.number").value(10))
                .andExpect(jsonPath("$.page.hasNext").value(false));
    }

    @Test
    void noMatchesReturnsZeroTotals() throws Exception {
        mvc.perform(get("/api/books").param("q", "NoSuchBook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.totalPages").value(0));
    }

    @Test
    void invalidPaginationAndSortReturn400() throws Exception {
        mvc.perform(get("/api/books").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_QUERY"));

        mvc.perform(get("/api/books").param("size", "0"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/books").param("size", "51"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/books").param("sort", "privateField,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parameterConversionFailureIs400() throws Exception {
        mvc.perform(get("/api/books").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }
}
~~~

Chạy toàn bộ test:

~~~bash
./gradlew test
~~~

Các test này cố ý kiểm tra **thứ tự phần tử**, **tổng sau khi lọc**, **trang ngoài phạm vi**, **boolean false** và **error status**. Chỉ assert số lượng item mà không kiểm thứ tự không bắt được lỗi sort. Chỉ assert total trên request không filter không bắt được lỗi “count trước filter”.

Lưu ý về phạm vi xác minh: code test được trình bày cho app demo. Việc có filter bảo mật hoặc cấu hình MVC riêng trong project thật có thể yêu cầu thêm setup trong MockMvc. Test in-memory **không chứng minh tính đúng đắn của SQL, index hoặc tính nhất quán khi có giao dịch đồng thời**.

## 10. Khi có Spring Data: `Pageable`, `Sort`, `Page` và `Slice`

### 10.1. Spring MVC nhận `Pageable` thông qua Spring Data web support

Trong ứng dụng đã cấu hình Spring Data web (thường qua các starter Spring Data/Boot auto-configuration tương ứng), controller có thể nhận trực tiếp:

~~~java
@GetMapping("/api/books")
public Page<BookEntity> find(
        @PageableDefault(size = 20, sort = "createdAt",
                         direction = Sort.Direction.DESC)
        Pageable pageable) {
    return repository.findAll(pageable);
}
~~~

**Đây là fragment không độc lập:** cần imports `Page`, `Pageable`, `PageableDefault`, `Sort`, khai báo entity/repository và cấu hình Data/JPA; cũng **không phải lời khuyên trả `Page<Entity>` trực tiếp ra API công khai**. Hãy map `Page<BookEntity>` thành DTO và envelope như mục 6.

Spring Data web resolver thường hiểu:
- `page`: index zero-based, mặc định 0.
- `size`: page size, thường default 20 ở resolver (có thể tùy chỉnh).
- `sort=title,asc`: sort field/direction.
- `sort=createdAt,desc&sort=id,asc`: nhiều directive.

`@PageableDefault` thay default cho tham số controller; có thể dùng `@SortDefault` để cấu hình nhiều sort mặc định. Resolver còn hỗ trợ `setMaxPageSize` và `setOneIndexedParameters` khi cấu hình. Không suy luận rằng annotation `@PageableDefault(size=20)` tự tạo allowlist sort hoặc đã đáp ứng mọi chính sách bảo mật.

### 10.2. Repository query với `Pageable`

Một Spring Data JPA repository điển hình có thể khai báo:

~~~java
// Fragment, chỉ đúng khi đã có BookEntity và JPA repository setup.
interface BookRepository extends JpaRepository<BookEntity, Long> {
    Page<BookEntity> findByCategoryIgnoreCase(
            String category, Pageable pageable);
}
~~~

Dự án thật phải ghép bộ lọc optional `q`, `available` và phân quyền vào cùng query trước khi paging. Có thể dùng `Specification`, Criteria API, Querydsl hoặc repository method phù hợp. **Không** làm:
1. `repository.findAll()` tải hết bảng.
2. `stream().filter(...)` trong JVM.
3. `subList(...)` trả một trang.

Cách đó có thể chấp nhận với một fixture sáu bản ghi để dạy contract, nhưng sai về quy mô và chi phí đối với production database.

### 10.3. `Page` khác `Slice` thế nào?

| Trả về | Biết totalElements/totalPages? | Biết còn trang tiếp? | Chi phí thường gặp |
| --- | --- | --- | --- |
| `Page<T>` | Có, dựa query tổng khi cần | Có | Có thể cần `COUNT(...)` tốn chi phí |
| `Slice<T>` | Không cần | Có (`hasNext`) | Thường truy vấn size + 1 để xác định còn |
| `List<T>` với `Pageable` | Không có metadata page | Không trực tiếp | Chỉ lấy vùng dữ liệu theo limit/offset |
| `Window<T>` với scrolling | Không tập trung vào total count | Có | Hỗ trợ offset/keyset scrolling tùy store |

`Page` hữu ích cho UI muốn “Trang 2 trên 37” hoặc cần tổng số kết quả sau filter. `Slice` phù hợp với “Load more” khi chỉ cần biết còn dữ liệu. Không thêm COUNT query chỉ vì muốn trang HTML có nút Next; kiểm tra UX contract thực sự cần.

Tùy repository và query, Spring Data có thể tối ưu/suy ra total count ở một số trường hợp, vì vậy không khẳng định **mọi** `Page` luôn thực hiện đúng hai câu SQL.

### 10.4. Tạo JSON ổn định từ `Page`

Ví dụ ý tưởng trong service:

~~~java
// Fragment cần các kiểu DTO và repository của ứng dụng.
Page<BookEntity> entityPage = repository.findAll(validatedPageable);

List<BookDto> items = entityPage.getContent().stream()
        .map(BookDto::fromEntity)
        .toList();

// Tạo response DTO do ứng dụng quản lý:
// items, page number/size, numberOfElements,
// entityPage.getTotalElements(), entityPage.getTotalPages(),
// entityPage.hasNext(), applied sort, effective filters.
~~~

Nếu dùng `org.springframework.data.web.PagedModel` (Spring Data 3.3+) hoặc `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`, hãy kiểm tra representation thực tế bằng integration test và versioned docs. Tên `PagedModel` cũng xuất hiện trong thư viện Spring HATEOAS; **đó không phải cùng một class**.

## 11. Offset pagination không bảo toàn snapshot khi dữ liệu thay đổi

### 11.1. Vấn đề khi có bản ghi mới

Giả sử sort mới nhất trước:

~~~text
Ở thời điểm T1:  [A, B, C, D, E, F]
page=0,size=2 -> [A, B]
~~~

Trước khi client gọi trang kế tiếp, một bản ghi `X` mới xuất hiện đầu danh sách:

~~~text
Ở thời điểm T2:  [X, A, B, C, D, E, F]
page=1,size=2 -> [B, C]
~~~

Client đã thấy `B` ở cả hai trang. Khi bản ghi bị xóa hoặc điều kiện lọc thay đổi, có thể xảy ra **bỏ sót**. Khóa phụ `id` làm thứ tự xác định tại mỗi thời điểm nhưng **không cố định snapshot giữa hai request**.

Các cách xử lý tùy nhu cầu:
- UI tra cứu thông thường: chấp nhận dữ liệu thay đổi và cung cấp nút Refresh.
- Export/report yêu cầu tập nhất quán: dùng snapshot/cursor token hoặc một transaction/snapshot strategy phù hợp, không giữ transaction HTTP quá dài vô hạn.
- Feed vô hạn, dữ liệu lớn, thứ tự thời gian: cân nhắc cursor/keyset pagination.

### 11.2. Keyset/cursor pagination

Thay vì `OFFSET 100000`, cursor ghi nhận khóa của phần tử cuối đã trả:

~~~text
GET /api/events?limit=20&after=opaqueCursor
~~~

Ví dụ data ordered `createdAt DESC, id DESC`, request tiếp theo chỉ lấy các bản ghi **đứng sau tuple cuối** theo thứ tự đó. Conceptual SQL:

~~~sql
SELECT id, created_at, payload
FROM events
WHERE (created_at < :last_created_at)
   OR (created_at = :last_created_at AND id < :last_id)
ORDER BY created_at DESC, id DESC
LIMIT :limit_plus_one;
~~~

Đây là SQL minh họa keyset cho hai khóa non-null và một database có phép so sánh phù hợp. Cần composite index theo filter/order và xử lý đúng NULL/collation. Không dùng raw query string của client thay cho token tin cậy.

Cursor public API nên:
- Opaque (có thể base64url encode token có phiên bản, keyset values, sort, filter fingerprint, hết hạn); nếu cursor ảnh hưởng quyền truy cập, cần bảo vệ khỏi giả mạo.
- Gắn với **đúng filters và sort**; cursor không được dùng cho một truy vấn khác.
- Có giới hạn `limit` và expiry/restart behavior rõ ràng.
- Trả `items`, `nextCursor`, `hasNext`; **không hứa `totalPages`** khi không đếm toàn bộ tập.

Cursor không tự tạo snapshot bất biến. Nó giúp duyệt theo vị trí ổn định hơn khi có insert đứng trước cursor, nhưng các update/delete làm thay đổi sort/filter vẫn có thể ảnh hưởng lần duyệt.

### 11.3. So sánh để chọn

| Yêu cầu sản phẩm | Offset (`page,size`) | Cursor/keyset |
| --- | --- | --- |
| Nhảy thẳng đến trang 19 | Thuận tiện | Thường không thuận tiện |
| Hiển thị “tổng 1.239 kết quả” | `Page`/COUNT phù hợp | Cần count riêng nếu muốn |
| Feed “Load more” liên tục | Dùng được nhưng dễ dịch trang khi có ghi dữ liệu | Thường tốt hơn với sort/keyset ổn định |
| Offset rất sâu trên bảng lớn | Có thể tốn chi phí | Có thể tốt hơn khi index phù hợp |
| Client cần lưu URL trang cố định | Dễ diễn đạt | Cần token và chính sách hạn |
| Dữ liệu đổi giữa hai request | Không đảm bảo snapshot | Cũng không đảm bảo snapshot tuyệt đối |

Không đổi sang cursor chỉ vì có API danh sách: chọn theo volume, nhu cầu UX, chi phí query và tính nhất quán thực tế.

## 12. Hiệu năng, index và độ tin cậy của total

### 12.1. Count query có thể nặng

Một request `Page<Book>` trong Spring Data thường truy vấn dữ liệu trang và có thể cần count cho `totalElements`. COUNT trên tập join/filter lớn có thể đắt, nhất là với nhiều joins, phân quyền, distinct hoặc truy vấn full-text. Dùng `Slice` nếu không cần tổng, hoặc thiết kế API cho biết tổng chỉ khi được yêu cầu (ví dụ `includeTotal=true`) nếu điều đó phù hợp.

Đừng trả metadata `totalElements=items.size()` để né count nhưng vẫn giữ tên field: đây là thay đổi ngữ nghĩa làm frontend tính trang sai. Nếu tổng không thể cung cấp chính xác, có thể bỏ field, dùng `totalApproximate` với tên và cam kết rõ, hoặc chuyển sang slice/cursor contract.

### 12.2. Index nên phục vụ bộ lọc và sắp xếp

Nếu thường lọc `category` rồi sort `createdAt DESC,id DESC`, cân nhắc index composite bắt đầu bằng điều kiện chọn lọc phù hợp và các khóa sort (tùy database/cardinality/query workload). Test bằng `EXPLAIN` và dữ liệu gần production. Tạo index cho mọi tổ hợp filter/sort có thể khiến ghi dữ liệu chậm và tăng storage; đo workload thay vì đoán.

Câu truy vấn không có `ORDER BY` không có thứ tự cam kết, kể cả khi hôm nay tình cờ theo primary key. Nếu dùng sort trên expression hoặc field join, cần kiểm chi phí và giới hạn cho phép.

### 12.3. Tính nhất quán của total và items

Khi data thay đổi giữa câu query lấy items và câu query count, hai kết quả có thể phản ánh hai thời điểm khác nhau. Isolation level, transaction snapshot và logic persistence quyết định độ nhất quán; `Page<T>` không tự hứa snapshot tuyệt đối giữa các HTTP requests.

Frontend nên có khả năng xử lý:
- `items=[]` nhưng `totalElements>0` nếu user đã đứng quá trang cuối.
- Giảm tổng khi filter đổi.
- Bản ghi bị xóa trước khi user bấm Next.
- Trang trước/tiếp thay đổi nếu có ghi đồng thời.

Tài liệu sản phẩm nên công bố **total là con số tại thời điểm truy vấn** và có thể thay đổi, trừ khi API cung cấp snapshot semantics rõ ràng.

## 13. Hợp đồng lỗi, HTTP cache và API evolution

### 13.1. Một bộ status nên nhất quán

| Request hoặc tình trạng | Status đề xuất cho ví dụ | Ý nghĩa |
| --- | --- | --- |
| Danh sách có dữ liệu | `200 OK` | `items` và metadata |
| Bộ lọc hợp lệ nhưng không khớp | `200 OK` | `items=[]`, `totalElements=0` |
| Page ngoài số trang nhưng input hợp lệ | `200 OK` | Trả `items=[]` theo policy đã công bố |
| `page=-1`, `size=0`, unknown sort | `400 Bad Request` | Sai contract query |
| `page=abc` | `400 Bad Request` | Lỗi chuyển kiểu/binding |
| Chưa xác thực/quyền không đủ | `401`/`403` | Theo cơ chế auth/authorization |
| Query/DB lỗi thật sự phía server | `5xx` phù hợp | Không ngụy trang thành danh sách trống |

Bảng này là **chính sách của ứng dụng ví dụ**, không phải lệnh bắt buộc mọi API. Quan trọng là các nhóm lỗi có mã máy đọc được và test, tránh frontend suy luận `[]` đồng nghĩa service thất bại.

### 13.2. Bộ lọc là một phần cache key

Nếu dùng HTTP/CDN caching cho GET, cache key cần phân biệt query params ảnh hưởng kết quả (`page`, `size`, `sort`, tất cả filters, tenant/visibility nếu có). Không cache response cá nhân trên shared cache mà không có chính sách authorization/cache headers phù hợp.

`ETag` hoặc `Last-Modified` có thể hỗ trợ conditional GET khi có version tài nguyên phù hợp. Tuy nhiên tính `ETag` bằng việc serialize toàn bộ dataset cho mỗi request có thể làm cache vô dụng; thiết kế theo lifecycle dữ liệu.

### 13.3. Backward compatibility

Những thay đổi có thể phá client:
- Đổi `page` zero-based thành one-based.
- Đổi default size/sort dẫn tới vị trí item thay đổi.
- Rename/remove fields hoặc đổi kiểu `totalElements` thành chuỗi.
- Thay field sort công khai bằng tên entity field mới.
- Đổi semantics của filter `q` từ contains sang exact match.
- Thay JSON envelope tự định nghĩa bằng Jackson serialization của `PageImpl`.

Những thay đổi thường an toàn hơn nếu có kiểm thử:
- Thêm field metadata tùy chọn khi client bỏ qua unknown properties.
- Thêm filter optional với default “không lọc”.
- Thêm lựa chọn sort có allowlist, giữ các lựa chọn cũ.

Đối với API nhiều consumer, công bố OpenAPI response schema, chuẩn hóa error schema và kiểm tra contract tests ở CI. Đừng để đổi library Spring Data ngầm kéo theo đổi shape public response.

## 14. Những lỗi phổ biến và cách chẩn đoán

| Triệu chứng | Nguyên nhân thường gặp | Cách khắc phục |
| --- | --- | --- |
| Item lặp ở hai trang | Sort thiếu unique tie-breaker hoặc dữ liệu thay đổi | Thêm khóa sort phụ, cân nhắc cursor/snapshot |
| Trả 20 item trên request size=5 | Không truyền limit/`Pageable` xuống query | Assert size và kiểm query thực |
| Total sai khi lọc | COUNT tính trên toàn bảng hoặc điều kiện count khác data query | Dùng cùng filter/tenant scope cho count |
| Sort tùy ý truy cập property nội bộ | Không có allowlist | Map public field name sang property hợp lệ |
| `size=0` gây chia cho 0 | Không validate size | Kiểm 1..MAX trước tính metadata |
| `page × size` âm/sai | Overflow `int` | Validate page/size và nhân trên `long` |
| Trang cuối rỗng dù UI nghĩ còn | Dữ liệu thay đổi hoặc total cũ | Refresh, xử lý out-of-range và theo dõi version |
| API trả `PageImpl` JSON đổi shape sau nâng Spring Data | Expose representation framework nội bộ | Dùng stable envelope hoặc PagedModel có test |
| COUNT query quá lâu | Trả `Page` khi UI chỉ cần Next | Cân nhắc `Slice` hoặc cursor |
| `q` không tìm có dấu như mong đợi | So khớp chữ/collation không nhất quán | Xác định search/collation contract |
| Chạy in-memory ổn nhưng DB phân trang sai | Lọc/sort trong JVM sau khi limit hoặc thiếu ORDER BY | Chuyển filter/sort/page vào query, test integration |
| 200 với `items=[]` che lỗi server | Catch exception rồi trả list rỗng | Phân biệt empty dataset với lỗi hạ tầng |
| Client gửi `sort=title,desc` nhưng metadata nói asc | Dùng default khi build response | Trả effective sort đã normalize và test |

Khi gặp lỗi, lấy **một URL cụ thể** cùng filter, sort, index, size, quyền user và snapshot dữ liệu để tái hiện. Kiểm tra SQL thực thi (`WHERE`/`ORDER BY`/`LIMIT OFFSET`), Java mapping và JSON envelope, không chỉ nhìn response mảng.

## 15. Checklist thiết kế và tự đánh giá

Trước khi public danh sách API:
1. Ghi rõ query names, page base, default size, max size và behavior tham số lỗi.
2. Xác định điều kiện lọc, case sensitivity, blank/null semantics, AND/OR, timezone và quyền truy cập.
3. Chọn allowlist sort với thứ tự mặc định và **unique tie-breaker**.
4. Chỉ phân trang sau khi đã filter/sort ở **nguồn dữ liệu**, ưu tiên database cho dataset lớn.
5. Chọn giữa `Page` (có totals), `Slice` (hasNext) và cursor (load-more/large feed).
6. Định nghĩa JSON envelope tự sở hữu; `items=[]` khi rỗng, metadata theo **tập đã lọc**.
7. Thống nhất xử lý page vượt phạm vi và `totalPages=0`, 400 conversion/validation và status lỗi hệ thống.
8. Kiểm thử nhiều trang, sort tie, available=false, blank filter, out-of-range, dữ liệu rỗng, size biên và quyền/tenant.
9. Với DB, đo count/offset sâu, EXPLAIN index, kiểm tra query trên dữ liệu thay đổi.
10. Giữ backward compatibility của field names, metadata và default behavior; test JSON công khai khi nâng framework.

### Câu hỏi tự kiểm tra

**1.** Client gửi `page=2&size=10` trên API zero-based. Offset là bao nhiêu?

**Trả lời:** 20; đây là trang thứ ba nếu trang đầu là 0.

**2.** Vì sao `totalElements` không bằng `items.length`?

**Trả lời:** `items.length` chỉ đếm phần tử trang đang trả; `totalElements` đếm toàn bộ bản ghi khớp filter nếu API cung cấp tổng.

**3.** Sort `createdAt DESC` đã đủ ổn định khi nhiều record trùng thời điểm chưa?

**Trả lời:** Chưa. Thêm khóa unique, ví dụ `id ASC`, để tạo thứ tự toàn phần trong cùng snapshot.

**4.** Có nên dùng `Page<T>` hay `Slice<T>` cho feed chỉ có nút “Tải thêm”?

**Trả lời:** `Slice<T>` thường hợp lý hơn nếu không cần total count; cursor/keyset đáng cân nhắc nếu offset sâu hoặc dữ liệu đổi thường xuyên.

**5.** Trả thẳng `PageImpl<Entity>` cho frontend có rủi ro gì?

**Trả lời:** JSON dễ phụ thuộc vào cấu trúc serialization của Spring Data, có thể đổi qua version; entity cũng có thể lộ field. Nên dùng DTO/envelope ổn định hoặc cơ chế representation được hỗ trợ và kiểm thử.

**6.** Vì sao cần phân biệt `available=null` với `available=false`?

**Trả lời:** null là không lọc; false nghĩa là chỉ lấy bản ghi không available. Gộp hai trường hợp sẽ lọc sai và tính total sai.

## 16. Tài liệu chính thức

- [Spring Framework — `@RequestParam` trong Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html): query/form parameters và conversion.
- [Spring Framework — `@ResponseBody`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responsebody.html): serialize return value bằng HTTP message converters.
- [Spring Data Commons — Web support, Pageable và Sort](https://docs.spring.io/spring-data/commons/reference/repositories/core-extensions.html): `page`, `size`, `sort`, resolver, customizer và `@PageableDefault`.
- [Spring Data JPA — Paging/Sorting, `Page`, `Slice` và Scrolling](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html): query count, offset/keyset và trade-off.
- [Spring Data — `PageableDefault` Javadoc](https://docs.spring.io/spring-data/commons/docs/3.3.5/api/org/springframework/data/web/PageableDefault.html): default page/size/sort cho method parameters.
- [Spring Data — `PageSerializationMode` Javadoc](https://docs.spring.io/spring-data/commons/reference/api/java/org/springframework/data/web/config/EnableSpringDataWebSupport.PageSerializationMode.html): `DIRECT` và `VIA_DTO`.
- [Spring Data — `PagedModel` Javadoc](https://docs.spring.io/spring-data/commons/reference/api/java/org/springframework/data/web/PagedModel.html): stable DTO representation của `Page`.
- [Spring Data REST — Paging and Sorting](https://docs.spring.io/spring-data/rest/reference/paging-and-sorting.html): lưu ý đây là cơ chế REST repository exposure riêng, không tự áp cho mọi `@RestController` do bạn viết.

**Ghi nhớ:** một danh sách REST đáng tin cậy phải có **filter rõ nghĩa, thứ tự xác định, phân trang có giới hạn và response contract ổn định**. Spring MVC nhận tham số và ghi response; Spring Data hỗ trợ query/paging khi có repository, còn ý nghĩa public API, tính nhất quán và chính sách lỗi luôn phải được ứng dụng quyết định và kiểm thử.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rest-responses-007">Thiết kế cập nhật PUT/PATCH và quy tắc xử lý yêu cầu lặp lại</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế cập nhật PUT/PATCH và quy tắc xử lý yêu cầu lặp lại trong Spring MVC

## 1. Mục tiêu và tình huống mở đầu

Một client mở tài liệu tại `GET /api/documents/1`, sửa tiêu đề rồi nhấn Lưu. Server xử lý thay đổi thành công nhưng kết nối bị ngắt trước khi response quay về. Client **không biết** bản cập nhật đã được áp dụng hay chưa. Nếu gửi lại yêu cầu, liệu nội dung sẽ giữ nguyên, bị áp dụng hai lần, hay ghi đè cập nhật của người khác?

Để trả lời, phải thiết kế **ngữ nghĩa cập nhật**, **quy tắc yêu cầu lặp lại** và **bảo vệ khi nhiều client sửa đồng thời**. Bài học này giúp bạn:

- Phân biệt `PUT` với `PATCH` theo HTTP semantics, đặc biệt **thay thế toàn bộ** và **cập nhật một phần**.
- Hiểu **idempotency**: gọi nhiều lần cùng một yêu cầu có cùng hiệu ứng mong muốn lên tài nguyên như gọi một lần; phân biệt hiệu ứng cuối cùng với response, log và request ID.
- Nhận biết `PATCH` có thể idempotent hoặc không, tùy thao tác và cách server triển khai; hiểu JSON Merge Patch và JSON Patch.
- Dùng **ETag + If-Match** để tránh **lost update**; phân biệt `412 Precondition Failed`, `428 Precondition Required` và `409 Conflict`.
- Chọn response 200/204/201, phân biệt absent/null, xác định retry an toàn và tránh lặp side effect.
- Xây dựng Spring Boot 3.x / Java 17+ demo có endpoint `GET`, `PUT`, `PATCH`, kiểm tra điều kiện cập nhật và test `MockMvc`.

**Phạm vi:** thiết kế REST API và triển khai cơ bản trong **Spring MVC (Servlet stack)**. Các ví dụ tuân tinh thần RFC 9110 (HTTP Semantics), RFC 5789 (PATCH), RFC 7396 (JSON Merge Patch) và RFC 6902 (JSON Patch). Quy tắc idempotency là thuộc tính của **hiệu ứng HTTP request được định nghĩa**, không đồng nghĩa với việc server chỉ xử lý request một lần vật lý.

## 2. PUT và PATCH: khác nhau ở hợp đồng cập nhật

Ví dụ tài liệu hiện tại:

```json
{
  "title": "Hướng dẫn Spring",
  "description": "Bản nháp"
}
```

### 2.1. PUT: gửi biểu diễn mới cho tài nguyên đích

Client muốn thay toàn bộ biểu diễn có thể gửi:

```http
PUT /api/documents/1 HTTP/1.1
Content-Type: application/json

{"title":"Hướng dẫn Spring MVC","description":"Bản hoàn chỉnh"}
```

Ý nghĩa HTTP của `PUT`: yêu cầu server **tạo hoặc thay trạng thái của tài nguyên đích bằng biểu diễn đi kèm**. Nếu endpoint đã tồn tại và được cập nhật thành công, response thường là **200 OK** (có body) hoặc **204 No Content** (không body). Nếu tài nguyên **chưa tồn tại và PUT tạo mới thành công**, theo HTTP semantics server **phải trả 201 Created**.

**Không áp dụng “chỉ sửa các trường hiện diện” cho PUT** nếu API được mô tả là thay thế toàn bộ. Chẳng hạn:

```http
PUT /api/documents/1 HTTP/1.1
Content-Type: application/json

{"title":"Hướng dẫn Spring MVC"}
```

Với tài nguyên hai trường như trên, việc `description` bị bỏ qua **không có nghĩa ngầm giữ nguyên "Bản nháp"**. Hợp đồng PUT phải định nghĩa rõ: coi trường vắng mặt là giá trị mặc định/null, bỏ trường đó khỏi representation hoặc từ chối request thiếu field bắt buộc. Client cần gửi biểu diễn đầy đủ theo schema. Một số API thực tế gọi endpoint là PUT nhưng triển khai như partial update; đó là hợp đồng riêng dễ gây nhầm và nên được sửa hoặc công bố rõ.

**Điểm tinh tế:** HTTP PUT nói về **biểu diễn của tài nguyên**, không bắt server phải lưu byte-for-byte request body vào một dòng DB. Nhưng server cần duy trì ngữ nghĩa tài nguyên. Các thuộc tính do server sở hữu như `id`, `createdAt` thường không được client cho phép sửa; contract phải cho biết chúng có thuộc representation có thể PUT hay không.

### 2.2. PATCH: gửi tài liệu mô tả thay đổi

Để chỉ thay tiêu đề và giữ `description`, dùng `PATCH` với patch document có định dạng đã thống nhất:

```http
PATCH /api/documents/1 HTTP/1.1
Content-Type: application/merge-patch+json

{"title":"Hướng dẫn Spring MVC"}
```

Với **JSON Merge Patch** (RFC 7396), field không xuất hiện được giữ nguyên; field có mặt được thay/cập nhật theo quy tắc Merge Patch. Dữ liệu cũ:

```json
{"title":"Hướng dẫn Spring","description":"Bản nháp"}
```

Dữ liệu sau:

```json
{"title":"Hướng dẫn Spring MVC","description":"Bản nháp"}
```

Không phải mọi `PATCH` đều dùng cùng cú pháp. Server phải chọn/khai báo **patch media type**, ví dụ `application/merge-patch+json` hoặc `application/json-patch+json`; một endpoint tự đặt `PATCH` với `application/json` cần tự định nghĩa semantics. Không mặc định rằng mọi JSON object gửi với PATCH đều tự động có chức năng “chỉ cập nhật field được gửi”.

### 2.3. Bảng so sánh

| Câu hỏi | PUT | PATCH |
| --- | --- | --- |
| Request body biểu thị gì? | Biểu diễn mới cho toàn tài nguyên | Tập thay đổi/patch document |
| Trường không xuất hiện | Theo schema của biểu diễn thay thế; không mặc định giữ cũ | Theo định dạng patch; Merge Patch giữ nguyên |
| Có thể tạo tài nguyên? | Có, nếu server hỗ trợ PUT tại URI đó | Có thể trong một số hợp đồng PATCH/patch format, không phải nghĩa mặc định cần giả định |
| Có idempotent theo định nghĩa HTTP? | **Có** | **Không mặc định**, có thể được thiết kế idempotent |
| Có cần xử lý đồng thời? | Có | Có; một số patch đặc biệt nhạy cảm với thay đổi giữa chừng |
| Success status thường gặp | 200/204; 201 nếu tạo mới | 200/204 (hoặc khác nếu hợp đồng cần) |
| Body phù hợp | Biểu diễn tài nguyên | `application/merge-patch+json` hoặc `application/json-patch+json` |
| Mối nguy phổ biến | Thiếu field vô tình xóa giá trị, ghi đè thay đổi mới | Thiếu phân biệt absent/null, patch trên bản cũ, lặp thao tác cộng/dời |

## 3. Idempotency là gì?

Trong HTTP, một phương thức **idempotent** nếu hiệu ứng mong muốn lên trạng thái server của **nhiều request giống nhau** bằng hiệu ứng của **một request**. `GET`, `HEAD`, `OPTIONS`, `TRACE` là safe (và idempotent); `PUT` và `DELETE` là idempotent nhưng **không safe** vì có thể thay đổi trạng thái. `POST`, `PATCH` không được định nghĩa là idempotent mặc định.

Cách nghĩ dễ nhớ:

```text
SET title = "Hoàn chỉnh"         → có thể idempotent
INCREMENT views BY 1            → không idempotent
APPEND "A" vào danh sách        → không idempotent (nếu không chống lặp)
DELETE tài nguyên ID=1          → idempotent về hiệu ứng đích
POST tạo một giao dịch mới     → có thể tạo thêm bản ghi khi retry
```

Ở đây `PUT` giống thao tác **đặt trạng thái mong muốn**. Gửi cùng body nhiều lần đến cùng tài nguyên, không có thao tác cạnh tranh hoặc ràng buộc phiên bản làm thay đổi kết quả, sẽ có trạng thái tài nguyên giống nhau sau mỗi lần áp dụng.

### 3.1. Idempotent không đồng nghĩa response giống hệt

Ví dụ:

1. `DELETE /api/documents/1` lần đầu: 204; tài nguyên bị xóa.
2. `DELETE /api/documents/1` lần hai: 404 vì tài nguyên đã không còn.
3. **Hiệu ứng cuối:** tài nguyên vẫn không tồn tại; `DELETE` vẫn là idempotent.

Tương tự, `PUT` có điều kiện `If-Match: "v1"` có thể thành công lần đầu, rồi trả 412 ở lần retry vì version đã đổi sang `"v2"`. **Điều này không làm PUT mất tính idempotent**: request lặp lại không ghi đè hoặc nhân đôi thay đổi. Response không nhất thiết giữ nguyên status/body/header.

Server có thể ghi access log mới, metric mới hoặc thực hiện một số side effect phục vụ quản trị sau mỗi request mà không làm thay đổi định nghĩa idempotent của phương thức. Tuy nhiên, **side effect thuộc nghiệp vụ** như gửi hai hóa đơn, trừ tiền hai lần hoặc bắn hai sự kiện giao hàng thì **cần cơ chế chống lặp rõ ràng**. Không dựa riêng vào nhãn PUT để suy ra exactly-once cho toàn bộ quy trình.

### 3.2. Bảng suy luận yêu cầu lặp

| Request | Lần đầu | Lặp lại nguyên request | Idempotent về hiệu ứng? |
| --- | --- | --- | --- |
| `PUT /docs/1` body cố định | Đặt tài liệu thành X | Vẫn X | Có |
| `PATCH` set `title="X"` | Đặt title thành X | Vẫn X | Có thể có |
| `PATCH` `increment count by 1` | count 5 → 6 | 6 → 7 | Không |
| `PATCH` JSON Patch `replace /title "X"` | title thành X | Vẫn X, nếu path tồn tại | Có thể có |
| `PATCH` JSON Patch `add /items/- "A"` | Append phần tử | Append thêm lần nữa | Không |
| `DELETE /docs/1` | Xóa tài liệu | Không có thêm tài liệu nào bị xóa | Có |
| `POST /payments` body cố định | Tạo giao dịch | Có thể tạo thêm giao dịch | Không mặc định |

**Giới hạn:** điều kiện tiền đề, side effect, tài nguyên liên quan và hợp đồng API quyết định tính idempotent thật sự. Không nên nói “PATCH luôn không idempotent”: RFC 5789 cho phép client thiết kế PATCH theo cách idempotent, và nên dùng điều kiện cập nhật để giảm xung đột.

### 3.3. Vì sao idempotency quan trọng cho retry?

Các sự cố có thể xảy ra sau khi request rời client:

- Kết nối đứt trước khi server nhận body.
- Server hoàn tất transaction nhưng response mất trên đường về.
- Gateway timeout rồi client hoặc proxy retry.
- Mobile app gửi lại do trạng thái mạng không chắc chắn.

Client khó phân biệt **“không thực thi”** với **“thực thi rồi nhưng mất phản hồi”**. Vì vậy, thiết kế PUT/PATCH cần quy tắc xác nhận kết quả; mọi thao tác không-idempotent cần nhận diện duplicate và kiểm tra hậu trạng thái trước khi chạy lại.

**Retry chỉ an toàn nếu thỏa hợp đồng.** Với PATCH thao tác cộng dồn, server phải có điều kiện/chốt chống lặp hoặc client không được tự động resend mù quáng.

## 4. Hai chuẩn phổ biến cho JSON PATCH document

### 4.1. JSON Merge Patch (RFC 7396)

Media type: `application/merge-patch+json`.

Cho tài nguyên:

```json
{
  "title": "Draft",
  "description": "Need review",
  "labels": ["java", "rest"],
  "settings": {"visible": false, "theme": "light"}
}
```

Gửi:

```http
PATCH /api/documents/1 HTTP/1.1
Content-Type: application/merge-patch+json

{
  "title": "Final",
  "description": null,
  "settings": {"visible": true}
}
```

Kết quả:

```json
{
  "title": "Final",
  "labels": ["java", "rest"],
  "settings": {"visible": true, "theme": "light"}
}
```

Quy tắc:

- **Vắng mặt:** giữ nguyên giá trị cũ.
- **Có giá trị khác null:** thêm/thay thế; với nested object, Merge Patch xử lý đệ quy.
- **Có giá trị null:** **xóa field** khỏi object đích, không phải “đặt field thành JSON null” theo nghĩa dữ liệu thông thường.
- **Array:** thường thay toàn bộ array; Merge Patch không hỗ trợ thay một phần tử theo chỉ số.
- **Patch không phải object:** thay toàn bộ target bằng giá trị patch. Một API có thể từ chối điều này theo schema tài nguyên, nhưng phải công bố rõ.
- **Áp dụng lại cùng Merge Patch thuần túy** lên trạng thái cuối thường không đổi dữ liệu nữa; tuy nhiên version/side effect của implementation cần được thiết kế đúng, và có thể gặp xung đột với cập nhật xen giữa.

Merge Patch phù hợp với resource có cấu trúc object và khi null không phải một giá trị cần lưu phân biệt với **xóa key**. Nếu domain cho phép field lưu JSON null hợp lệ, Merge Patch dễ mơ hồ; chọn JSON Patch hoặc hợp đồng chuyên biệt.

### 4.2. JSON Patch (RFC 6902)

Media type: `application/json-patch+json`. Body là **một mảng thao tác có thứ tự**:

```http
PATCH /api/documents/1 HTTP/1.1
Content-Type: application/json-patch+json

[
  {"op":"test","path":"/title","value":"Draft"},
  {"op":"replace","path":"/title","value":"Final"},
  {"op":"remove","path":"/description"}
]
```

Các operation trong RFC 6902:

| Operation | Tác dụng điển hình | Khi lặp lại |
| --- | --- | --- |
| `add` | Thêm/ghi vào object key hoặc chèn phần tử array | Thêm object key thường ổn; chèn array có thể lặp |
| `remove` | Xóa một vị trí tồn tại | Lần lặp có thể báo lỗi vì path không còn |
| `replace` | Thay giá trị ở path tồn tại | Thường giữ cùng giá trị khi lặp |
| `move` | Dời giá trị từ `from` sang `path` | Phụ thuộc cấu trúc sau lần đầu |
| `copy` | Sao chép từ `from` sang `path` | Phụ thuộc cấu trúc đích |
| `test` | Kiểm tra giá trị tại path; sai thì toàn patch thất bại | Hữu ích như tiền điều kiện trong patch |

Path sử dụng **JSON Pointer** (RFC 6901); ký tự `~` và `/` trong tên property phải escape thành `~0` và `~1`. Chỉ số array và `-` (vị trí append) có ngữ nghĩa riêng. Ví dụ:

```json
[
  {"op":"add","path":"/labels/-","value":"spring"}
]
```

Nếu không chống lặp, gửi lại request này sẽ append thêm `"spring"` một lần nữa. JSON Patch **không tự nhiên idempotent** chỉ vì HTTP method là PATCH.

RFC 5789 yêu cầu **áp dụng patch atomically**: nếu một operation thất bại giữa danh sách, server không được công khai một representation cập nhật dở và không được giữ một phần thay đổi. Triển khai production cần transaction/rollback hoặc cơ chế tương đương cho mọi cập nhật liên quan.

### 4.3. PATCH với body JSON tự định nghĩa

Nhiều API chọn:

```http
PATCH /api/orders/42 HTTP/1.1
Content-Type: application/json

{"status":"SHIPPED"}
```

Cách này có thể hợp lệ nếu API **định nghĩa rõ** body là “các field được phép thay đổi, field vắng mặt giữ nguyên, null nghĩa X”. Nhưng không tự gọi nó là RFC 7396 nếu không tuân đầy đủ Merge Patch (ví dụ null được lưu thành null, hoặc array được merge theo index). Nên cân nhắc vendor media type và tài liệu OpenAPI/contract.

Khi cần thay đổi quan hệ nghiệp vụ như “thu tiền”, “xác nhận”, “thêm đơn vị hàng”, một **command endpoint POST** với idempotency key đôi khi biểu đạt hành vi rõ hơn việc dùng PATCH mơ hồ. Chọn method theo ngữ nghĩa tài nguyên, không theo độ dễ của controller annotation.

## 5. Vấn đề khó: cập nhật đồng thời và lost update

Giả sử trạng thái ban đầu:

```json
{"title":"Draft","description":"Original"}
```

Hai client cùng GET lúc 10:00:

1. Client A đổi `title` thành `"Published"` rồi PUT full object.
2. Client B vẫn giữ bản cũ, đổi `description` thành `"Revised"` rồi PUT full object.
3. Nếu server nhận B sau A mà **không kiểm tra phiên bản**, body B có thể chứa `title="Draft"`, vô tình ghi đè `"Published"` của A.

Đây là **lost update**, có thể xảy ra dù PUT hoàn toàn idempotent. Idempotency giải quyết rủi ro **lặp cùng yêu cầu**, không tự giải quyết việc **hai yêu cầu hợp lệ khác nhau cạnh tranh**.

PATCH giảm nguy cơ ghi đè field không liên quan nếu nó chỉ thay field gửi lên, nhưng vẫn có thể xung đột logic; hai client cùng sửa `description`, một người dựa vào version cũ, hoặc một JSON Patch thao tác chỉ số array bị thay đổi sẽ có vấn đề. Vì vậy cả PUT lẫn PATCH cần cơ chế concurrency control.

### 5.1. ETag + If-Match (optimistic concurrency)

Một cách HTTP chuẩn là trả **strong ETag** khi GET tài nguyên:

```http
GET /api/documents/1 HTTP/1.1

HTTP/1.1 200 OK
ETag: "v7"
Content-Type: application/json

{"title":"Draft","description":"Original"}
```

Client gửi phiên bản mà mình vừa đọc:

```http
PUT /api/documents/1 HTTP/1.1
If-Match: "v7"
Content-Type: application/json

{"title":"Published","description":"Original"}
```

Server thực hiện một bước **kiểm tra và cập nhật nguyên tử**:

```text
Tài nguyên có current ETag "v7"?
  ├── Có → xác thực input, ghi bản cập nhật, chuyển version mới
  └── Không → KHÔNG ghi đè, trả 412 Precondition Failed
```

Nếu A đã cập nhật thành `"v8"` thì PUT cũ của B với `If-Match: "v7"` bị từ chối bằng **412**, thay vì tự động đè A. B cần GET trạng thái mới và quyết định merge/edit lại.

**Điều quan trọng:**

- `If-Match` dùng **strong comparison**. Weak ETag như `W/"v7"` không đủ điều kiện so khớp strong với `"v7"`.
- `If-Match: *` yêu cầu tài nguyên có representation hiện tại, **không** có nghĩa kiểm tra phiên bản cụ thể; nó không ngăn lost update từ bản cũ.
- `If-Match` có thể chứa danh sách entity tags hợp lệ. Ví dụ chạy ở mục 8 **cố ý chỉ hỗ trợ một tag mạnh đúng nguyên dạng** để code tập trung; production phải tuân toàn bộ cú pháp/preconditions theo HTTP spec.
- Nếu client không gửi điều kiện và chính sách API bắt buộc optimistic concurrency, trả **428 Precondition Required**. Nếu đã gửi nhưng tag sai, trả **412 Precondition Failed**.
- Không dùng `If-None-Match` thay cho `If-Match` khi bảo vệ một cập nhật đang tồn tại; `If-None-Match: *` chủ yếu hữu ích để **tạo mới nếu chưa tồn tại**.
- Một entity tag không nhất thiết là số version hiển thị; có thể là hash representation hoặc opaque token, miễn chính sách strong validator đúng. Không so sánh ETag như số bằng dấu lớn hơn/nhỏ hơn.

### 5.2. ETag có khác @Version trong JPA?

`@Version` của Jakarta Persistence là optimistic locking ở **lớp lưu trữ**; ETag/If-Match là hợp đồng **HTTP**. Chúng có thể phối hợp:

```text
HTTP GET  → đọc hàng (id=1, version=7) → tạo ETag "v7"
HTTP PUT  → nhận If-Match "v7"
          → transaction kiểm tra DB version=7
          → UPDATE ... WHERE id=1 AND version=7
          → cập nhật version=8
          → commit
HTTP GET  → ETag "v8"
```

Không chỉ làm:

```java
if (incomingTag.equals(currentTag)) {
    // đọc xong, một transaction khác có thể xen vào ở đây
    repository.save(entity);
}
```

Check-then-write không có transaction/locking/CAS là **race condition**. Với nhiều instance ứng dụng, `synchronized` trên một JVM cũng không đủ vì mỗi instance có lock riêng. Hãy dùng DB transaction, optimistic locking/version, compare-and-swap hoặc atomic operation của storage.

### 5.3. Retry sau khi request đã thành công nhưng response mất

Ví dụ:

```text
Trạng thái trước: ETag "v7"
Client gửi: PUT body X + If-Match "v7"
Server: commit X, tăng tag thành "v8"; response bị mất
Client retry NGUYÊN request: PUT body X + If-Match "v7"
Server: thấy tag "v8" → 412, KHÔNG ghi lần hai
Client: GET mới, đối chiếu nội dung và quyết định kết quả
```

Đây là phản hồi an toàn. Không gọi HTTP 412 là “chắc chắn thất bại toàn bộ thao tác từ đầu”; có thể request trước đó **đã thành công**. Client nên GET/đối chiếu trạng thái hoặc dùng cơ chế idempotency result tracking nếu cần biết chắc kết quả của một hành động nghiệp vụ.

HTTP cho phép một số trường hợp server trả 2xx nếu xác định trạng thái đã giống yêu cầu cũ, nhưng **không được tùy tiện ghi đè trạng thái mới** và không nên trả ETag mới sai ngữ nghĩa. Để dễ giải thích, demo sử dụng 412 nghiêm ngặt nếu tag không còn khớp.

## 6. Quy tắc thiết kế response/status cho cập nhật

| HTTP status | Dùng khi nào | Gợi ý response |
| --- | --- | --- |
| **200 OK** | PUT/PATCH thành công và trả representation hoặc metadata | JSON body phản ánh trạng thái mới |
| **204 No Content** | PUT/PATCH thành công, client không cần body | **Không có body**; có thể trả metadata phù hợp |
| **201 Created** | PUT tạo mới resource tại URI | `Location` khi hữu ích; trả representation nếu cần |
| **400 Bad Request** | Body sai cú pháp, thiếu field bắt buộc, input contract không hợp lệ | Mã lỗi/chi tiết an toàn |
| **404 Not Found** | Tài nguyên đích không có, API không cho PUT create | Thông báo resource không tồn tại |
| **409 Conflict** | Xung đột trạng thái nghiệp vụ hoặc patch conflict không được biểu diễn bằng precondition HTTP | Mã lỗi và hướng fetch/reconcile |
| **412 Precondition Failed** | `If-Match`/điều kiện HTTP được gửi nhưng thất bại | Yêu cầu client đọc lại trạng thái |
| **415 Unsupported Media Type** | Không hỗ trợ patch content type | Có thể quảng bá patch format cho client |
| **422 Unprocessable Content** | Hiểu content type/cú pháp nhưng không xử lý được chỉ dẫn về nghĩa, nếu hợp đồng chọn status này | Mô tả lỗi semantic phù hợp |
| **428 Precondition Required** | API đòi gửi điều kiện cập nhật để tránh lost update | Hướng dẫn client dùng If-Match |

Không gán tất cả lỗi PATCH là 409. Nếu header `If-Match` được đánh giá và không khớp, **412** biểu đạt rõ hơn. Nếu API chủ động yêu cầu header nhưng thiếu, **428** là lựa chọn đúng. Với validation thông thường, Spring MVC thường trả **400**, còn sử dụng **422** là quyết định contract chứ không mặc định Spring tự áp dụng.

**ETag trong response PUT:** HTTP đặt điều kiện nghiêm về gửi validator sau PUT thành công: không trả `ETag`/`Last-Modified` nếu request representation đã bị server biến đổi khi lưu và validator không phản ánh chính xác biểu diễn được lưu. Demo bên dưới **không gửi ETag trong response PUT**; client gọi GET để nhận validator mới. Trong GET, ETag được trả cho representation hiện tại. Với PATCH, server cũng có thể trả thông tin version sau cập nhật nếu được tính đúng.

### 6.1. Công bố patch formats

RFC 5789 định nghĩa header `Accept-Patch`, thường có thể quảng bá trong response `OPTIONS`:

```http
OPTIONS /api/documents/1 HTTP/1.1

HTTP/1.1 204 No Content
Allow: GET, PUT, PATCH, OPTIONS
Accept-Patch: application/merge-patch+json, application/json-patch+json
```

Đây là **ví dụ HTTP do server giả định hỗ trợ cả hai chuẩn**; đừng trả danh sách format chưa triển khai. Trong Spring MVC, chỉ khai báo `@PatchMapping(consumes = ...)` chưa chắc tự sinh header `Accept-Patch` như ví dụ. Nếu muốn quảng bá, cấu hình endpoint/response phù hợp và kiểm thử.

## 7. Thiết kế DTO, absent/null và bảo mật khi cập nhật

Với PUT thay thế tài nguyên, một DTO đủ điều kiện thường có field rõ ràng. Với PATCH partial, cần phân biệt ít nhất **ba trạng thái**:

1. Field **vắng mặt** → không chạm tới.
2. Field **hiện diện với giá trị** → thay thành giá trị đó.
3. Field **hiện diện với null** → tùy định dạng: JSON Merge Patch **xóa key**, còn DTO partial tự định nghĩa có thể xem như “đặt null”, “xóa dữ liệu” hoặc “không cho phép”.

Nếu bind PATCH trực tiếp vào:

```java
record PatchProfile(String nickname, String phone) {}
```

thì cả JSON `{}` và `{"phone":null}` thường cùng tạo `phone == null`, làm mất thông tin “client không gửi” và “client yêu cầu xóa”. Nếu cần phân biệt, dùng `JsonNode`/`Map` có kiểm tra key hiện diện, hoặc wrapper ba trạng thái chuyên dụng được ánh xạ rõ ràng.

**Validation khác PUT/PATCH:** PUT thường dùng constraint cho toàn representation mới. PATCH thường validate **patch document**, áp dụng patch lên object tạm, rồi validate **trạng thái kết quả toàn tài nguyên** trước commit. Không chỉ validate từng field đơn lẻ, vì thay một field có thể phá bất biến liên trường.

**Bảo mật:**

- Chỉ cho phép sửa những field đã được công bố; từ chối `role`, `isAdmin`, `balance`, `createdAt` nếu client không có quyền. Không bind thẳng request vào persistence entity có setters rộng.
- Kiểm tra authorization với **resource ID** và từng field/hành động; ETag không thay xác thực/phân quyền.
- Giới hạn kích thước patch document, số operation, độ sâu JSON, đường dẫn JSON Pointer/array indices.
- Áp dụng patch atomically trong transaction; kiểm tra quy tắc nghiệp vụ và concurrency trước commit.
- Tránh phản hồi lại dữ liệu nhạy cảm hoặc stack trace từ Jackson/JPA.

## 8. Demo Spring Boot 3 + Java 17: PUT/PATCH + ETag/If-Match

Ứng dụng này quản lý đúng **một tài liệu** tại `/api/documents/1`, lưu **trong bộ nhớ một JVM** để minh họa ngữ nghĩa và request flow. Không có database, authentication, multi-node synchronization. Code dùng `synchronized` để kiểm tra version và cập nhật nguyên tử trên **một instance controller**; phần 10 giải thích cách chuyển sang storage thật.

Demo hỗ trợ:

- `GET /api/documents/1` trả representation và strong ETag `"v1"`, `"v2"`...
- `PUT /api/documents/1` với toàn bộ representation và `If-Match`, cùng trạng thái thì không tăng version.
- `PATCH /api/documents/1` chỉ nhận `application/merge-patch+json` cho hai field phẳng `title` và `description`. Bỏ field nghĩa giữ nguyên; `description:null` nghĩa xóa khỏi representation. `title` phải là chuỗi không trắng và không được xóa.
- Từ chối unknown field/kiểu JSON sai, đảm bảo **không update một phần khi request lỗi**.
- Trả 404 khi ID khác 1, 428 nếu thiếu `If-Match`, 412 nếu stale tag, 400 nếu input không hợp lệ.

Đây là **subset có schema tài liệu cố định** của JSON Merge Patch: body phải là object và chỉ được chứa hai field được phép. Nó không phải implementation JSON Merge Patch tổng quát cho mọi cấu trúc JSON/array hoặc mọi resource.

### 8.1. Cấu trúc project

```text
document-update-demo/
├── pom.xml
└── src/
    ├── main/java/example/DocumentUpdateApplication.java
    └── test/java/example/DocumentControllerTest.java
```

### 8.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>document-update-demo</artifactId>
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

### 8.3. DocumentUpdateApplication.java (chương trình chạy được)

Toàn bộ class/record nằm trong file `src/main/java/example/DocumentUpdateApplication.java`. Java cho phép một `public` top-level class trùng tên file và các class package-private khác.

```java
package example;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DocumentUpdateApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentUpdateApplication.class, args);
    }
}

// Null description được bỏ qua khi serialize response JSON.
// Với PUT, đây là representation mới; bỏ description sẽ làm nó thành null.
@JsonInclude(JsonInclude.Include.NON_NULL)
record Document(@NotBlank String title, String description) {}

// Body lỗi đơn giản, chỉ chứa mã do demo định nghĩa.
record ApiProblem(String code) {}

@RestController
@RequestMapping("/api/documents")
class DocumentController {

    private Document current = new Document("Draft", "Initial description");
    private long version = 1L;

    private String currentTag() {
        return "\"v" + version + "\"";
    }

    private ResponseEntity<?> guard(long id, String ifMatch) {
        if (id != 1L) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiProblem("NOT_FOUND"));
        }
        if (ifMatch == null || ifMatch.isBlank()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(new ApiProblem("PRECONDITION_REQUIRED"));
        }
        // Demo chỉ nhận một strong ETag dạng "vN".
        // Sản phẩm thực cần xử lý danh sách tags và các precondition
        // đầy đủ theo RFC 9110.
        if (!ifMatch.equals(currentTag())) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(new ApiProblem("STALE_VERSION"));
        }
        return null;
    }

    @GetMapping("/{id}")
    public synchronized ResponseEntity<?> get(@PathVariable long id) {
        if (id != 1L) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiProblem("NOT_FOUND"));
        }
        return ResponseEntity.ok()
                .eTag(currentTag())
                .body(current);
    }

    @PutMapping(path = "/{id}",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    public synchronized ResponseEntity<?> put(
            @PathVariable long id,
            @RequestHeader(value = "If-Match", required = false)
            String ifMatch,
            @Valid @RequestBody Document replacement) {

        ResponseEntity<?> precondition = guard(id, ifMatch);
        if (precondition != null) {
            return precondition;
        }

        // PUT gửi representation thay thế. Nếu body giống trạng thái
        // hiện tại thì không tạo thêm một phiên bản chỉ vì retry.
        if (!Objects.equals(current, replacement)) {
            current = replacement;
            version++;
        }

        // GET tiếp theo cung cấp ETag mới. Không tự động đưa
        // validator vào response PUT khi server có thể biến đổi dữ liệu.
        return ResponseEntity.ok(current);
    }

    @PatchMapping(path = "/{id}",
                  consumes = "application/merge-patch+json")
    public synchronized ResponseEntity<?> patch(
            @PathVariable long id,
            @RequestHeader(value = "If-Match", required = false)
            String ifMatch,
            @RequestBody JsonNode patchDocument) {

        ResponseEntity<?> precondition = guard(id, ifMatch);
        if (precondition != null) {
            return precondition;
        }

        if (!patchDocument.isObject()) {
            return ResponseEntity.badRequest()
                    .body(new ApiProblem("INVALID_PATCH_DOCUMENT"));
        }

        // Xây dựng candidate trước, không sửa current giữa chừng.
        String newTitle = current.title();
        String newDescription = current.description();

        Iterator<Map.Entry<String, JsonNode>> entries =
                patchDocument.fields();

        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String field = entry.getKey();
            JsonNode value = entry.getValue();

            if (field.equals("title")) {
                if (!value.isTextual() || value.asText().isBlank()) {
                    return ResponseEntity.badRequest()
                            .body(new ApiProblem("INVALID_TITLE"));
                }
                newTitle = value.asText();
            } else if (field.equals("description")) {
                if (!value.isNull() && !value.isTextual()) {
                    return ResponseEntity.badRequest()
                            .body(new ApiProblem("INVALID_DESCRIPTION"));
                }
                // Merge Patch: null nghĩa xóa property khỏi representation.
                newDescription = value.isNull() ? null : value.asText();
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiProblem("UNKNOWN_PATCH_FIELD"));
            }
        }

        Document candidate = new Document(newTitle, newDescription);

        // Commit duy nhất khi toàn bộ patch đã qua kiểm tra.
        if (!candidate.equals(current)) {
            current = candidate;
            version++;
        }

        return ResponseEntity.ok(current);
    }
}
```

**Cơ chế quan trọng:**

- `currentTag()` là token mạnh được bao trong dấu `"..."`. Đây là implementation minh họa: version đổi khi representation thay đổi; các instance khác sẽ không dùng chung bộ đếm nếu chuyển thành multi-node.
- `guard()` dùng tag đọc được khi GET, yêu cầu header trước PUT/PATCH và ngăn request stale. `If-Match` trong demo dùng **so sánh chuỗi đơn**; production cần parser/so sánh HTTP precondition đầy đủ.
- PUT nhận `Document` có `@NotBlank title`; body đủ điều kiện cập nhật toàn representation. `description` bị thiếu trở thành `null` và không được giữ ngầm. `@JsonInclude(NON_NULL)` giúp biểu diễn GET bỏ field description khi nó đã bị xóa.
- PATCH nhận `JsonNode` để phân biệt **field vắng mặt** với **field có null**. Title không được xóa vì ràng buộc domain; description null xóa field khỏi JSON output.
- Cả PUT và PATCH chỉ tăng version nếu `Document` cuối khác bản hiện tại. Patch lỗi trả 400 trước khi gán `current`, thỏa atomicity cho tài nguyên trong-memory này.
- `synchronized` làm check và mutation có cùng lock trong một JVM. Đây không phải phương án multi-instance production.
- API chọn chính sách **update-only** cho ID=1, không hỗ trợ PUT-create; ID khác được trả 404 theo hợp đồng demo.

### 8.4. Chạy và thử với curl

Cần JDK 17+ và Maven; tại thư mục chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Đầu tiên đọc tài nguyên và ETag:

```bash
curl -i http://localhost:8080/api/documents/1
```

Response minh họa (có thể kèm các header khác do HTTP server):

```http
HTTP/1.1 200 OK
ETag: "v1"
Content-Type: application/json

{"title":"Draft","description":"Initial description"}
```

**PUT thay thế**:

```bash
curl -i -X PUT http://localhost:8080/api/documents/1 \
  -H 'Content-Type: application/json' \
  -H 'If-Match: "v1"' \
  -d '{"title":"Published","description":"Final description"}'
```

Server trả **200** với body:

```json
{"title":"Published","description":"Final description"}
```

GET lại:

```bash
curl -i http://localhost:8080/api/documents/1
```

Kỳ vọng ETag `"v2"` vì representation đã thay đổi. Nếu retry đúng nguyên PUT cùng `If-Match: "v1"`, server trả **412**:

```json
{"code":"STALE_VERSION"}
```

Tài nguyên vẫn là `{"title":"Published","description":"Final description"}`; không có thao tác ghi lần thứ hai.

**PATCH chỉ xóa description**:

```bash
curl -i -X PATCH http://localhost:8080/api/documents/1 \
  -H 'Content-Type: application/merge-patch+json' \
  -H 'If-Match: "v2"' \
  -d '{"description":null}'
```

Body thành công:

```json
{"title":"Published"}
```

GET lại có ETag `"v3"` và body không có `description`. Trường vắng mặt trong PATCH (ví dụ `{"title":"Final"}`) sẽ giữ description, trong khi `"description":null` chủ ý loại bỏ nó.

**Thiếu điều kiện cập nhật**:

```bash
curl -i -X PATCH http://localhost:8080/api/documents/1 \
  -H 'Content-Type: application/merge-patch+json' \
  -d '{"title":"Danger"}'
```

Kỳ vọng **428** với:

```json
{"code":"PRECONDITION_REQUIRED"}
```

Lưu ý: đoạn `curl` trên dùng dấu nối dòng của shell POSIX; PowerShell có thể dùng `curl.exe` và gõ một dòng, hoặc thay cú pháp multiline tương ứng.

### 8.5. MockMvc: chứng minh thay thế, partial update, retry và precondition

File `src/test/java/example/DocumentControllerTest.java`:

```java
package example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DocumentControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void getExposesInitialStrongEtag() throws Exception {
        mvc.perform(get("/api/documents/1"))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"v1\""))
            .andExpect(jsonPath("$.title").value("Draft"));
    }

    @Test
    void putReplacesAndRetryWithStaleEtagDoesNotOverwrite() throws Exception {
        String body =
                "{\"title\":\"Published\","
              + "\"description\":\"Final description\"}";

        mvc.perform(put("/api/documents/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("If-Match", "\"v1\"")
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Published"));

        mvc.perform(get("/api/documents/1"))
            .andExpect(header().string("ETag", "\"v2\""));

        mvc.perform(put("/api/documents/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("If-Match", "\"v1\"")
                    .content(body))
            .andExpect(status().isPreconditionFailed())
            .andExpect(jsonPath("$.code").value("STALE_VERSION"));

        mvc.perform(get("/api/documents/1"))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", "\"v2\""))
            .andExpect(jsonPath("$.description")
                    .value("Final description"));
    }

    @Test
    void putWithSameRepresentationHasNoAdditionalEffect() throws Exception {
        String same =
                "{\"title\":\"Draft\","
              + "\"description\":\"Initial description\"}";

        for (int n = 0; n < 2; n++) {
            mvc.perform(put("/api/documents/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("If-Match", "\"v1\"")
                        .content(same))
                .andExpect(status().isOk());
        }

        mvc.perform(get("/api/documents/1"))
            .andExpect(header().string("ETag", "\"v1\""));
    }

    @Test
    void patchDistinguishesOmittedFieldAndExplicitNull() throws Exception {
        mvc.perform(patch("/api/documents/1")
                    .contentType("application/merge-patch+json")
                    .header("If-Match", "\"v1\"")
                    .content("{\"title\":\"Published\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Published"))
            .andExpect(jsonPath("$.description")
                    .value("Initial description"));

        mvc.perform(get("/api/documents/1"))
            .andExpect(header().string("ETag", "\"v2\""));

        mvc.perform(patch("/api/documents/1")
                    .contentType("application/merge-patch+json")
                    .header("If-Match", "\"v2\"")
                    .content("{\"description\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").doesNotExist());

        mvc.perform(get("/api/documents/1"))
            .andExpect(header().string("ETag", "\"v3\""))
            .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void patchWithNoStateChangeCanRepeatWithSameEtag() throws Exception {
        for (int n = 0; n < 2; n++) {
            mvc.perform(patch("/api/documents/1")
                        .contentType("application/merge-patch+json")
                        .header("If-Match", "\"v1\"")
                        .content("{\"title\":\"Draft\"}"))
                .andExpect(status().isOk());
        }

        mvc.perform(get("/api/documents/1"))
            .andExpect(header().string("ETag", "\"v1\""));
    }

    @Test
    void incompletePutReplacesAbsentOptionalField() throws Exception {
        mvc.perform(put("/api/documents/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("If-Match", "\"v1\"")
                    .content("{\"title\":\"Only title\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Only title"))
            .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void missingConditionIs428AndLeavesStateIntact() throws Exception {
        mvc.perform(put("/api/documents/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Other\", \"description\":\"Text\"}"))
            .andExpect(status().is(428))
            .andExpect(jsonPath("$.code")
                    .value("PRECONDITION_REQUIRED"));

        mvc.perform(get("/api/documents/1"))
            .andExpect(header().string("ETag", "\"v1\""));
    }

    @Test
    void invalidPatchFailsAtomically() throws Exception {
        mvc.perform(patch("/api/documents/1")
                    .contentType("application/merge-patch+json")
                    .header("If-Match", "\"v1\"")
                    .content(
                        "{\"title\":\"Changed\", \"privileged\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code")
                    .value("UNKNOWN_PATCH_FIELD"));

        mvc.perform(get("/api/documents/1"))
            .andExpect(header().string("ETag", "\"v1\""))
            .andExpect(jsonPath("$.title").value("Draft"));
    }

    @Test
    void invalidPutDataCannotReplaceResource() throws Exception {
        mvc.perform(put("/api/documents/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("If-Match", "\"v1\"")
                    .content(
                        "{\"title\":\"\", \"description\":\"Ignored\"}"))
            .andExpect(status().isBadRequest());

        mvc.perform(get("/api/documents/1"))
            .andExpect(header().string("ETag", "\"v1\""));
    }

    @Test
    void unsupportedPatchMediaTypeIs415() throws Exception {
        mvc.perform(patch("/api/documents/1")
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("If-Match", "\"v1\"")
                    .content("title=Changed"))
            .andExpect(status().isUnsupportedMediaType());
    }
}
```

Chạy `mvn test`. Mỗi test có Spring context mới nhờ `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` để reset bộ nhớ ban đầu về `"v1"`; cách này đơn giản cho ví dụ nhỏ nhưng tương đối tốn chi phí khi test nhiều. Với project thực tế, thay bằng test fixture reset dữ liệu một cách có kiểm soát và kiểm thử cả luồng transaction/concurrency ở persistence layer.

Các test xác nhận **trạng thái cuối cùng** sau update/retry. Chỉ assert status 200 không đủ để chứng minh idempotency hoặc không có lost update.

## 9. Thiết kế retry khi không chắc lần trước đã thành công

### 9.1. Retry PUT

Nếu PUT được thiết kế đúng ngữ nghĩa thay thế, request lặp cùng body thường đưa trạng thái về cùng biểu diễn mong muốn. Với If-Match:

1. Gửi PUT có `If-Match: "vN"`.
2. Nếu nhận **200/204**, cập nhật local cache hoặc GET lại nếu cần token phiên bản mới.
3. Nếu timeout và retry request, có thể nhận **412** vì lần đầu đã commit. Không coi 412 là bằng chứng “chưa lần nào thành công”; GET và đối chiếu.
4. Nếu trạng thái mới trùng mục tiêu, xử lý như “đã đạt trạng thái mong muốn” khi workflow cho phép. Nếu khác, hiển thị conflict/đề nghị merge.
5. Không tự động bỏ `If-Match` khi gặp 412 để “ép ghi cho thành công”; làm vậy có thể ghi đè người khác.

### 9.2. Retry PATCH

Phân loại patch:

- **Patch theo giá trị mục tiêu:** `{"title":"Final"}` với Merge Patch có thể chạy nhiều lần mà trạng thái nội dung không đổi sau lần đầu; vẫn dùng If-Match để tránh cập nhật trên phiên bản cũ.
- **Patch phụ thuộc trạng thái:** JSON Patch có `test` hoặc `remove`; retry có thể trả lỗi vì base đã đổi; cần GET rồi tính lại.
- **Patch cộng dồn:** `{"increment":1}` là hợp đồng command riêng không phải RFC Merge Patch thuần túy; retry mù sẽ nhân đôi.
- **Patch append:** JSON Patch thêm vào `/items/-` có thể lặp phần tử; cần token chống lặp hoặc chính sách về item identity.

Nếu PATCH không idempotent, hãy dùng **idempotency key** nếu server hỗ trợ, hoặc thay thiết kế thành “set trạng thái mong muốn” / item resource có URI ổn định, hoặc dùng precondition kèm logic reconcile. Đừng suy ra chỉ gửi `If-Match` là sẽ tự trả lại response giống hệt khi retry: If-Match chỉ bảo vệ **version trước khi áp dụng**, không lưu kết quả của request cũ.

### 9.3. Idempotency-Key cho thao tác không-idempotent

Ví dụ nghiệp vụ thêm một khoản thanh toán:

```http
POST /api/payments HTTP/1.1
Idempotency-Key: 58f147f6-6b4d-4f8b-9b2e-1a1c4ef9a53a
Content-Type: application/json

{"orderId":42,"amount":150000}
```

Đây là **một hợp đồng do API triển khai**. Header không tự tạo idempotency khi server không có cơ chế xử lý. Backend cần:

1. Chuẩn hóa phạm vi khóa (user/tenant + operation + key) và request fingerprint; cùng key với payload khác → lỗi phù hợp.
2. Dành chỗ cho key một cách **nguyên tử** với giao dịch, tránh hai request đồng thời cùng thực hiện.
3. Lưu trạng thái in-progress/succeeded/failed và kết quả có thể replay theo chính sách; không thực hiện side effect lần hai khi key đã hoàn tất.
4. Thiết lập thời hạn lưu, cơ chế khôi phục khi process crash, timeout và retry.
5. Với external event/payment, kết hợp transaction/outbox, provider idempotency key hoặc deduplication ở consumer. Không chỉ lưu key trong một `HashMap` của process nếu cần bảo đảm đa instance.

Có thể áp dụng cùng nguyên tắc cho PATCH có thao tác append/increment nếu hợp đồng nghiệp vụ cần exactly-once-effect. Điều này không biến toàn bộ phương thức PATCH thành idempotent theo HTTP một cách tự động; nó làm **một operation cụ thể** an toàn hơn khi lặp.

## 10. Từ demo một JVM sang Spring MVC production

Demo dùng `synchronized` để minh họa cập nhật nguyên tử; triển khai production phải phối hợp storage, validation, authorization và event.

### 10.1. Cách dùng JPA @Version

Entity minh họa:

```java
@Entity
class DocumentEntity {
    @Id
    private Long id;

    @Version
    private long version;

    private String title;
    private String description;

    // Constructors, getters và setters được lược bỏ;
    // đây là đoạn minh họa không độc lập.
}
```

Trên GET, đọc version gắn với representation và tạo ETag. Trên PUT/PATCH, verify client If-Match khớp version và thực hiện thay đổi trong transaction. Hibernate/JPA optimistic locking sẽ kiểm tra version khi flush/commit; khi có `OptimisticLockException` hoặc `ObjectOptimisticLockingFailureException`, cần phản hồi phù hợp với hợp đồng (ví dụ 412 nếu điều kiện If-Match không còn hợp lệ, hoặc 409 trong xung đột khác). Việc map exception vào status cần dựa trên **nguyên nhân và ngữ nghĩa điều kiện**, không tự động xem mọi DB exception là 412.

### 10.2. Đồng nhất kiểm tra version và tác động phụ

Một quy trình production nên là:

```text
Authentication/Authorization
   → Xác định tài nguyên & permission
   → Parse và validate representation / patch document
   → Bắt đầu transaction
       → Đọc và khóa/CAS theo version
       → Đánh giá If-Match
       → Tạo trạng thái dự kiến
       → Validate bất biến toàn tài nguyên
       → Lưu state + version mới nếu thay đổi
       → Ghi outbox event trong cùng transaction nếu cần
   → Commit
   → Trả response / ETag (nếu thỏa HTTP spec)
   → Outbox publisher gửi event, consumer có deduplication
```

Với storage hỗ trợ compare-and-swap, có thể dùng ý tưởng:

```sql
UPDATE documents
SET title = :new_title,
    description = :new_description,
    version = version + 1
WHERE id = :id AND version = :expected_version;
```

Nếu số hàng cập nhật bằng 0, kiểm tra xem tài nguyên không còn tồn tại hay đã đổi version rồi trả 404/412 phù hợp. Đoạn SQL chỉ minh họa conditional update; để giữ **không tạo version mới nếu PUT cùng dữ liệu**, phải bổ sung bước so sánh trạng thái mong muốn với hiện tại dưới cùng cơ chế lock/CAS hoặc chọn chính sách version phù hợp và chứng minh retry không tạo nghiệp vụ lặp ngoài ý muốn.

### 10.3. Validation sau khi áp dụng patch

Đừng sửa entity đang được persistence context quản lý, flush sớm rồi mới kiểm tra một field khác trong patch. Một mẫu an toàn:

1. Phân tích patch vào cấu trúc **tạm**, kiểm tra field nào được phép và type/null rules.
2. Tạo **candidate copy** của trạng thái resource dựa trên phiên bản đã xác thực.
3. Áp dụng **toàn bộ** patch vào candidate.
4. Chạy Bean Validation + bất biến domain trên **candidate cuối cùng**.
5. Nếu hợp lệ, ghi toàn bộ thay đổi cùng transaction; nếu bất kỳ bước nào thất bại, rollback tất cả.
6. Không phát event hoặc thay trạng thái phụ khi transaction chưa commit thành công.

Với PATCH JSON Patch gồm nhiều operation, atomicity đặc biệt quan trọng: nếu operation thứ ba thất bại thì hai operation đầu **không** được tồn tại dưới dạng một phần cập nhật.

### 10.4. ETag phải mô tả representation nào?

ETag là validator của **representation**, không phải trường ngẫu nhiên trong DB. Nếu cùng resource có nhiều representation theo media type, ngôn ngữ hoặc encoding, cần cẩn thận về validators/variant handling. ETag không nên chỉ chứa số version DB nếu representation có thể thay đổi mà version ấy không tăng hoặc nếu cùng tag được gán cho các representation khác byte khác nhau và được coi là strong.

Trong demo, representation là hai field JSON trong một JVM, version tăng khi dữ liệu thay đổi; vì vậy ETag mô phỏng strong validator trong phạm vi cố định. Sản phẩm thực cần đảm bảo validator thực sự đúng với response GET, cập nhật khi có thay đổi ảnh hưởng representation và tuân HTTP preconditions.

## 11. Lỗi thiết kế thường gặp

| Sai lầm | Rủi ro | Cách xử lý |
| --- | --- | --- |
| Dùng PUT để merge những field có trong body mà không công bố | Client tưởng thay toàn bộ nhưng dữ liệu cũ vẫn tồn tại | Tách rõ full replacement và partial patch |
| Dùng PATCH nhưng không định nghĩa media type/semantics | Không biết null/omitted/array nghĩa gì | Chọn JSON Merge Patch, JSON Patch hoặc contract riêng |
| Cho rằng mọi PATCH không-idempotent | Bỏ lỡ cách update theo trạng thái mong muốn | Phân loại operation và bảo đảm hiệu ứng khi retry |
| Cho rằng mọi PATCH đều idempotent | Append/increment bị nhân đôi | Dùng precondition/idempotency key hoặc đổi mô hình |
| Chỉ thêm `synchronized` rồi deploy nhiều instance | Hai instance vẫn ghi đè lẫn nhau | DB transaction/version/CAS |
| Chỉ kiểm tra If-Match trước transaction | Race giữa kiểm tra và ghi | Conditional write nguyên tử |
| Trả 409 khi ETag đã sai | Client khó phân biệt HTTP precondition | Trả 412 khi If-Match thất bại |
| Bỏ If-Match khi retry bị 412 | Ghi đè thay đổi của người khác | GET, reconcile, gửi request mới có tag mới |
| Trả 428 khi tag có nhưng sai | Nhầm thiếu điều kiện với điều kiện thất bại | 428 thiếu, 412 mismatch |
| Dùng `If-Match: *` để chống lost update | Chỉ kiểm tra tài nguyên tồn tại | Sử dụng tag phiên bản mạnh cụ thể |
| PUT thiếu field vẫn giữ giá trị cũ mà không khai báo | Không thật sự full replacement | DTO PUT rõ contract và validate đầy đủ |
| JSON Merge Patch `null` nghĩa “lưu null” | Sai RFC 7396, có thể xóa ngoài ý muốn | Null là xóa field; nếu cần JSON null, chọn định dạng khác |
| JSON Patch `add /items/-` được retry mù | Append trùng phần tử | Dùng chống lặp hoặc element identity |
| PATCH áp dụng từng operation rồi return lỗi | Tài nguyên bị cập nhật dở | Transaction/rollback toàn bộ |
| Chỉ validate mỗi field trong patch | Bỏ lọt bất biến liên trường | Validate candidate sau merge |
| Đưa entity có `role`/`balance` vào binder | Mass assignment/escalation | DTO, allowlist, authorization |
| Trả ETag sau PUT mà request data bị biến đổi | Vi phạm điều kiện về validator trong HTTP | Chỉ phát validator khi đủ điều kiện, hoặc cho client GET lại |
| Retry tạo thêm event/email/payment | Trùng side effect nghiệp vụ | Outbox + dedup/idempotency key |
| Coi response 412 sau timeout là “chắc chắn chưa commit” | Người dùng thao tác lặp hoặc xung đột sai | GET trạng thái thực tế và reconcile |

## 12. Bài tập tự kiểm tra

1. **PUT** `{"title":"A","description":"B"}` hai lần vào cùng URL, không có cập nhật cạnh tranh. State cuối khác nhau không? **Không**; mục tiêu đều là A/B. Nếu dùng If-Match cũ lần hai có thể trả 412, nhưng không ghi đè.
2. **PUT** chỉ `{"title":"A"}` mà endpoint khai báo thay toàn bộ. Server có nên tự giữ `description` không? **Không mặc định**. Bỏ field nghĩa theo schema toàn representation hoặc lỗi nếu bắt buộc.
3. **PATCH Merge Patch** gửi `{"description":null}`. Có phải đặt description thành JSON null? **Không theo RFC 7396**; đó là yêu cầu xóa member.
4. **PATCH Merge Patch** gửi `{}`. Điều gì xảy ra? **Không yêu cầu thay đổi field nào**; state kết quả bằng trước (nếu input hợp lệ và server không thêm side effect nghiệp vụ).
5. **PATCH JSON Patch** `add` vào `/items/-` bị retry. Vấn đề? **Có thể append lần hai**, nên không được xem idempotent khi thiếu chống lặp.
6. Nếu Client A và B đều GET ETag `"v7"`, A cập nhật trước lên `"v8"`, B PUT cùng `If-Match: "v7"` thì sao? **412**, không ghi đè A.
7. Nếu API yêu cầu If-Match nhưng request PUT không có thì status? **428 Precondition Required** theo chính sách API.
8. Nếu request có If-Match nhưng không còn khớp thì status? **412 Precondition Failed**.
9. Gửi `If-Match: *` có chống được lost update không? **Không đủ**, vì chỉ kiểm tra tài nguyên tồn tại.
10. HTTP 204 có body JSON không? **Không**; nếu muốn gửi representation hãy dùng 200.
11. PUT tạo tài nguyên chưa tồn tại thành công cần status nào theo HTTP? **201 Created**.
12. Tại sao `PATCH` không bảo đảm an toàn đồng thời dù chỉ gửi một field? **Vì có thể đụng đúng field người khác sửa hoặc phụ thuộc base-state cũ**, nên vẫn cần concurrency control.
13. Một service gửi email mỗi khi xử lý PUT dù state không đổi. Có vấn đề gì? **Có**, về hiệu ứng nghiệp vụ khi retry; cần giới hạn email theo transition thực và dedup/outbox.
14. Có thể triển khai `synchronized` trên controller để tránh lost update trên 5 instance Spring Boot không? **Không**; lock đó chỉ nằm trong từng JVM.
15. Khi PATCH có 5 operation, operation cuối sai, 4 operation đầu đã commit có đúng RFC 5789 không? **Không**, patch phải atomic.
16. Nếu body PATCH là `application/json` nhưng server chỉ hỗ trợ `application/merge-patch+json`, status phù hợp? **415 Unsupported Media Type**.
17. Sau timeout, retry PUT bị 412. Có thể khẳng định lần đầu thất bại? **Không**; GET kiểm tra trạng thái hiện tại trước khi quyết định thao tác kế.
18. Nếu database lưu version=8 nhưng GET representation thay đổi từ nguồn phụ ngoài DB, strong ETag `"v8"` có còn đúng không? **Chưa chắc**; validator phải thay đổi theo representation thực tế.

## 13. Nguồn chính thức và tài liệu tham khảo

- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110): ngữ nghĩa PUT, idempotency, status codes, ETag và điều kiện `If-Match`.
- [RFC 5789 — PATCH Method for HTTP](https://www.rfc-editor.org/rfc/rfc5789): ngữ nghĩa PATCH, atomicity, idempotency tùy thiết kế, `Accept-Patch`.
- [RFC 7396 — JSON Merge Patch](https://www.rfc-editor.org/rfc/rfc7396): cấu trúc JSON Merge Patch, absent/null, xử lý object và array.
- [RFC 6902 — JSON Patch](https://www.rfc-editor.org/rfc/rfc6902): `add`, `remove`, `replace`, `move`, `copy`, `test`.
- [RFC 6901 — JSON Pointer](https://www.rfc-editor.org/rfc/rfc6901): cú pháp path trong JSON Patch.
- [RFC 6585 — Additional HTTP Status Codes](https://www.rfc-editor.org/rfc/rfc6585): status 428 Precondition Required.
- [Spring Framework — Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods.html): `@PutMapping`, `@PatchMapping`, request mapping.
- [Spring Framework — HTTP Message Converters](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html): chọn converter theo `Content-Type`, binding JSON vào DTO.
- [Spring Data JPA — Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html): cơ chế khóa ở persistence layer; kết hợp với version/transaction phù hợp.

**Ghi nhớ:** `PUT` diễn tả **trạng thái tài nguyên cần có sau cập nhật** và mang tính idempotent theo HTTP; `PATCH` diễn tả **thay đổi cần áp dụng**, có idempotent hay không phụ thuộc format và operation. **ETag/If-Match** ngăn ghi đè khi dùng dữ liệu cũ; **idempotency key/dedup** bảo vệ những hiệu ứng nghiệp vụ không được lặp. Kiểm chứng bằng trạng thái sau request và sau retry, không chỉ bằng một mã HTTP trả về.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="rest-responses-008">Giữ hợp đồng API ổn định khi thay đổi cấu trúc dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ hợp đồng API ổn định khi thay đổi cấu trúc dữ liệu trong Spring MVC

## 1. Hợp đồng API là lời cam kết với client

Một REST API vẫn có thể chạy thành công trong backend sau khi thay đổi DTO, entity hoặc cấu trúc database nhưng **client cũ lại ngừng hoạt động**. Điều này xảy ra khi cấu trúc hoặc ý nghĩa dữ liệu trên HTTP thay đổi ngoài những gì client đã được cam kết.

Ví dụ hợp đồng JSON ban đầu:

```http
GET /api/v1/products/7 HTTP/1.1
Accept: application/json
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"id":7,"name":"Notebook","price":129000}
```

Client có thể đang dựa vào các giả định sau:

- URL và HTTP method vẫn là `GET /api/v1/products/{id}`.
- Khi thành công, status là `200` và body là JSON object.
- Field `id` là số định danh ổn định.
- Field `name` luôn có nội dung tên sản phẩm.
- Field `price` là một con số có nghĩa là **đồng Việt Nam (VND)**, không bất ngờ trở thành chuỗi hoặc cent.
- Khi không tồn tại sản phẩm, endpoint trả `404` theo một cách đã được tài liệu hóa.
- Client không bắt buộc phải parse field mới, và server không tùy ý xóa field đã công bố.

Giả sử backend đổi tên Java property từ `name` sang `displayName`, đổi tiền từ `BigDecimal` sang `Money` và chuyển database sang bảng mới. Các thay đổi nội bộ này **không nhất thiết buộc JSON cũ phải đổi**. API contract là lớp biểu diễn công khai, độc lập với domain model và persistence schema.

Nếu server lặng lẽ thay JSON cũ bằng:

```json
{
  "id": 7,
  "displayName": "Notebook",
  "pricing": {"amount": 129000, "currency": "VND"}
}
```

thì client tìm `response.name` và `response.price` sẽ bị hỏng. Đây là thay đổi **breaking** cho các client phụ thuộc vào field cũ, dù dữ liệu vẫn nói về cùng một sản phẩm.

**Trọng tâm của bài:** quản lý sự tiến hóa của cấu trúc request/response trong **Spring Web MVC** sao cho hệ thống phát triển được mà không làm gián đoạn consumer đang sử dụng hợp đồng được hỗ trợ. Cơ chế áp dụng gồm request/response DTO, JSON serialization bằng Jackson, phân phiên bản, adapter mapping, tài liệu OpenAPI và các bài kiểm thử hợp đồng.

## 2. “Hợp đồng” bao gồm những gì ngoài JSON field?

Một API contract không chỉ là tên class hoặc DTO. Cần mô tả các yếu tố client quan sát được:

| Mặt hợp đồng | Ví dụ | Điều cần giữ ổn định |
| --- | --- | --- |
| Endpoint | `GET /api/v1/products/7` | URL, path params, HTTP method |
| Request headers | `Content-Type`, `Accept`, auth headers | Header bắt buộc, media types hỗ trợ |
| Request body | `{"name":"Notebook"}` | Field names, types, required/optional, constraints |
| Response status | `200`, `201`, `400`, `404` | Ngữ nghĩa thành công/thất bại |
| Response body | `{"id":7,"name":"Notebook"}` | JSON shape, kiểu, nullability và ý nghĩa |
| Error shape | `{"code":"PRODUCT_NOT_FOUND",...}` | Trường ổn định mà client đọc được |
| Pagination | `items`, `nextCursor` | Shape, ordering, cursor/offset semantics |
| Time, money, ID | ISO-8601, VND, opaque ID | Đơn vị, timezone, độ chính xác, định dạng |
| Headers response | `ETag`, `Location`, cache headers | Client caching, redirect/creation semantics |
| Behavioral rules | Giá, tồn kho, sort | Quy tắc nghiệp vụ có ảnh hưởng đến client |

Ví dụ, giữ nguyên JSON key `price` nhưng đổi ý nghĩa từ **VND** sang **cent** vẫn là breaking change. Giữ nguyên tên enum `ACTIVE` nhưng diễn giải trạng thái khác trước cũng có thể phá client. Thay response code từ `404` sang `200` với body lỗi có thể khiến consumer coi lỗi thành thành công.

**Phân biệt hai hướng tương thích:**

- **Backward compatibility của server mới:** request từ client cũ vẫn được chấp nhận và response mà client cũ hiểu vẫn giữ đúng contract.
- **Forward compatibility của client:** client được thiết kế để có thể bỏ qua những field mới, xử lý enum value chưa biết, và không phụ thuộc thứ tự key JSON, nếu contract cho phép.

Một server muốn thêm response field vào cùng phiên bản cần biết **chính sách extensibility** đã công bố và kiểm chứng client có thể chịu được. Đối với client deserialize strict hoặc SDK code-generated đóng schema, “chỉ thêm field mới” vẫn có thể gây lỗi. Vì vậy, khả năng tương thích là **tính chất của cả producer và consumer**, không chỉ của JSON nhìn bằng mắt.

## 3. Phân loại thay đổi cấu trúc dữ liệu

| Thay đổi | Thường an toàn không? | Điều kiện/khuyến nghị |
| --- | --- | --- |
| Đổi tên Java field nội bộ, JSON key giữ nguyên | Thường có | Dùng DTO và mapping/`@JsonProperty`; test wire format |
| Thêm field optional vào response | Có điều kiện | Client bỏ qua unknown fields và contract cho phép mở rộng |
| Thêm field optional vào request | Có điều kiện | Server phải dùng default hợp lý khi client cũ không gửi |
| Thêm endpoint mới | Thường có | Endpoint cũ vẫn hoạt động |
| Đổi thứ tự các key trong JSON object | Thường có | JSON object không đảm bảo thứ tự ngữ nghĩa |
| Xóa/đổi tên response field đã công bố | Breaking | Giữ v1 hoặc tạo v2 |
| Đổi kiểu `price: number` thành `price: string` | Breaking | Consumer parser và logic có thể hỏng |
| Đổi flat field thành nested object | Breaking | Ví dụ `price` thành `pricing.amount` |
| Đổi nghĩa/đơn vị giá trị giữ nguyên key | Breaking | Phải version và mô tả rõ |
| Thêm required request field | Breaking | Client cũ thiếu field; chuyển thành optional/default hoặc v2 |
| Chuyển optional field thành required | Breaking | Request cũ từng hợp lệ có thể bị từ chối |
| Chuyển non-null thành nullable / ngược lại | Có thể breaking | Consumer dereference hoặc validation phụ thuộc |
| Thêm enum response value | Có điều kiện | Client có xử lý unknown enum value không? |
| Thay status/error code hoặc error schema | Có thể breaking | Giữ semantics và công bố migration |
| Áp dụng pagination bắt buộc cho endpoint trả toàn bộ list | Có thể breaking | Client cũ có thể không tải đủ dữ liệu |
| Thay ordering của mảng kết quả | Có thể breaking | Nếu contract từng hứa sort theo tiêu chí cụ thể |
| Siết validation, giảm size limit hoặc rate limit | Có thể breaking | Request hợp lệ trước đây có thể thất bại |

Bảng là **đánh giá theo tác động**, không phải quy tắc tự động cho mọi client. Microsoft và các hệ sinh thái API lớn khác nhấn mạnh rằng định nghĩa breaking change phải rõ ràng: có nơi xem thêm JSON field là tương thích, có nơi xem đó là thay đổi hợp đồng. Hãy chọn một chính sách, ghi vào tài liệu và áp dụng nhất quán.

### Tính ổn định của “shape” và của “meaning”

Xem hai phản hồi:

```json
{"amount": 15.75, "currency": "USD"}
```

```json
{"amount": 1575, "currency": "USD"}
```

Cùng tên field, cùng JSON number, nhưng một API dùng **đô la**, API kia dùng **cent**. Nếu chuyển đổi mà không đổi contract thì client có thể tính tiền sai 100 lần. Vì vậy tài liệu phải mô tả **đơn vị, rounding, range và ý nghĩa**, ngoài JSON type.

Tương tự, `null`, vắng field, chuỗi rỗng `""`, số `0` và mảng rỗng `[]` không phải luôn tương đương. Client có thể phân biệt “không có”, “chưa biết” và “có giá trị rỗng”; thay một loại bằng loại khác cần đánh giá như thay đổi contract.

## 4. Không dùng JPA entity/domain object trực tiếp làm public API

Một nguyên nhân phổ biến của breaking changes là để JSON response phản chiếu toàn bộ object nội bộ:

```java
@GetMapping("/users/{id}")
public UserEntity getUser(@PathVariable long id) {
    return repository.findById(id).orElseThrow();
}
```

Đây là **ví dụ phản diện**, chỉ minh họa nguyên tắc, không phải chương trình độc lập. Nếu `UserEntity` thay column/property, thêm relationship hoặc chứa `passwordHash`, cấu trúc JSON có thể vô tình thay đổi hoặc lộ dữ liệu. Lazy loading/circular references cũng có thể gây lỗi serialize.

Thiết kế ổn định hơn:

```text
Database schema / JPA entities
           |
           v
Domain model + business services
           |
           v
API mapper cho từng contract
           |
     +-----+-----+
     |           |
     v           v
ProductV1Dto   ProductV2Dto
     |           |
     v           v
JSON v1       JSON v2
```

**Input DTO** cũng tách riêng với **response DTO**. Input chỉ có những field mà client được phép cung cấp; response chỉ lộ những trường đã cam kết. `record` của Java 17 thuận tiện để mô tả cấu trúc bất biến, nhưng việc dùng record **không tự chứng minh API không đổi**: tên component, kiểu, annotations Jackson và cấu hình mapper đều ảnh hưởng wire format.

Đừng dùng reflection copy tự động toàn bộ entity sang DTO nếu việc bổ sung property có thể lặng lẽ làm đổi JSON. Mapper tường minh tuy nhiều dòng hơn nhưng dễ review và test.

## 5. Cơ chế giữ tên JSON khi đổi tên biến Java

Spring MVC dùng `HttpMessageConverter` để đọc/ghi body, thường là Jackson JSON converter trong ứng dụng Spring Boot Web. Nếu refactor Java property `name` thành `displayName` nhưng muốn client vẫn nhìn thấy JSON `"name"`, có thể khai báo:

```java
public record ProductV1(
        long id,
        @JsonProperty("name") String displayName,
        BigDecimal price) {}
```

Đây là snippet cần imports `com.fasterxml.jackson.annotation.JsonProperty` và `java.math.BigDecimal`. Object `new ProductV1(7L, "Notebook", new BigDecimal("129000"))` được Jackson serialize thành JSON có **key `name`**, không thành `displayName`. Annotation cố định tên bên ngoài; Java code có thể dùng `displayName()`.

Một trường hợp khác là **chấp nhận một tên thay thế ở request input** trong giai đoạn chuyển tiếp:

```java
public record PreviewRequest(
        @JsonAlias("displayName") String name,
        BigDecimal price) {}
```

Với Jackson 2, `@JsonAlias` cho phép deserialize từ `"displayName"` hoặc tên chính `"name"`, nhưng **không đổi tên được ghi khi serialize**: tên chính vẫn là `name`. Điều này hữu ích khi chỉ thay đổi cách client gửi một trường và muốn server hỗ trợ cả hai. Tuy nhiên:

- Không coi `@JsonAlias` là một chiến lược versioning đầy đủ: nó không xử lý nested structure, kiểu mới, đơn vị đo hay business semantics khác.
- Nếu cả `name` và `displayName` xuất hiện trong một JSON, thứ tự input có thể làm giá trị sau ghi đè giá trị trước. Hãy xác định chính sách từ chối payload mơ hồ nếu hệ thống yêu cầu, thay vì mặc định chấp nhận cả hai.
- Đừng dùng alias để vô tình mở thêm những field client không được phép điều khiển.
- Giữ annotation, mapper settings và contract tests cùng version API.

Một cách khác để giữ cấu trúc cũ là xây DTO riêng và map tường minh. Đó thường là giải pháp dễ kiểm soát hơn khi thay đổi lớn.

## 6. Ví dụ chạy được: duy trì v1 và mở rộng v2 trong Spring MVC

Mục tiêu minh họa:

- `GET /api/v1/products/{id}` giữ JSON cũ có `id`, `name`, `price`, ngay cả khi Java DTO dùng component `displayName`.
- `GET /api/v2/products/{id}` công bố representation mới có `displayName` và `pricing: {amount, currency}`, kèm `sku`.
- `POST /api/v1/products/preview` chấp nhận request field `name` hoặc alias `displayName` nhưng vẫn trả JSON field `name`.
- Kiểm thử `MockMvc` chặn regression khiến `v1` bị đổi shape ngoài ý muốn.

Dữ liệu lưu trữ chỉ được giả lập bằng object trong bộ nhớ; ví dụ không có database, authentication, distributed cache hay nghiệp vụ thương mại thật.

### 6.1. Cấu trúc Maven

```text
api-contract-demo/
├── pom.xml
└── src/
    ├── main/java/example/contract/
    │   ├── ApiContractApplication.java
    │   └── ProductController.java
    └── test/java/example/contract/
        └── ProductContractTest.java
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

    <groupId>example</groupId>
    <artifactId>api-contract-demo</artifactId>
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

**`src/main/java/example/contract/ApiContractApplication.java`**

```java
package example.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiContractApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiContractApplication.class, args);
    }
}
```

### 6.2. Hai DTO response với một dữ liệu nghiệp vụ

**`src/main/java/example/contract/ProductController.java`**

```java
package example.contract;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
public class ProductController {

    // Mô hình nội bộ; không trả thẳng ra HTTP.
    private record Product(
            long id, String name, BigDecimal price, String sku) {}

    // Contract JSON v1: id, name, price.
    // Java component đã đổi thành displayName, JSON vẫn giữ "name".
    public record ProductV1(
            long id,
            @JsonProperty("name") String displayName,
            BigDecimal price) {}

    // Contract JSON v2: id, displayName, pricing, sku.
    public record Money(BigDecimal amount, String currency) {}

    public record ProductV2(
            long id, String displayName, Money pricing, String sku) {}

    // Hỗ trợ alias phía request trong quá trình chuyển tiếp.
    public record PreviewRequest(
            @NotBlank @JsonAlias("displayName") String name,
            @NotNull @DecimalMin("0.01") BigDecimal price) {}

    // Output v1 tiếp tục giữ tên cũ.
    public record PreviewResponse(String name, BigDecimal price) {}

    @GetMapping(path = "/api/v1/products/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ProductV1 getV1(@PathVariable("id") long id) {
        Product product = load(id);
        return new ProductV1(
                product.id(), product.name(), product.price());
    }

    @GetMapping(path = "/api/v2/products/{id}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ProductV2 getV2(@PathVariable("id") long id) {
        Product product = load(id);
        return new ProductV2(
                product.id(),
                product.name(),
                new Money(product.price(), "VND"),
                product.sku());
    }

    @PostMapping(
            path = "/api/v1/products/preview",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PreviewResponse preview(
            @Valid @RequestBody PreviewRequest input) {
        return new PreviewResponse(input.name(), input.price());
    }

    private Product load(long id) {
        if (id != 7L) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Product not found");
        }
        return new Product(
                7L, "Notebook", new BigDecimal("129000"), "NB-007");
    }
}
```

**Những quyết định làm nên tính ổn định:**

1. `Product` là mô hình nội bộ. Nó không tự xuất hiện ở HTTP response.
2. `ProductV1` dùng component Java `displayName` nhưng `@JsonProperty("name")` giữ JSON key `name`. Vì client v1 còn đọc tên cũ, chỉ refactor Java không gây breaking change.
3. `ProductV2` là response contract **mới**: nested `pricing` và `currency`, không giả vờ rằng client v1 hiểu representation này.
4. Cả v1 và v2 dùng cùng một hàm `load` để tránh tạo hai nguồn dữ liệu thật; phần khác nhau chỉ là **API mapping**.
5. `PreviewRequest` chỉ chứa các trường client được phép gửi. `@Valid` + Jakarta Validation kiểm tra text không trống, giá có mặt và giá ít nhất `0.01`. Không cho client chỉnh `sku`, `id` hay quyền truy cập qua DTO này.
6. `@JsonAlias` chỉ làm mềm **đầu vào**. Response preview vẫn dùng `PreviewResponse.name`, vì vậy client cũ không bị đổi response field.
7. Ví dụ giả lập sản phẩm duy nhất ID `7`. ID khác nhận `404` từ `ResponseStatusException`. Trong ứng dụng thật cần service, persistence và authorization đúng tài nguyên.

### 6.3. Gửi request và so sánh response

Chạy project từ thư mục chứa `pom.xml`:

```bash
mvn test
mvn spring-boot:run
```

Hai lệnh được thực hiện tuần tự. `mvn test` cần hoàn thành với status thành công trước khi chạy ứng dụng. Khi Spring Boot khởi động thành công ở cổng mặc định `8080`:

**API v1 giữ nguyên JSON:**

```bash
curl -i \
  -H 'Accept: application/json' \
  http://localhost:8080/api/v1/products/7
```

Response status và body (bỏ các HTTP headers không liên quan):

```http
HTTP/1.1 200
Content-Type: application/json

{"id":7,"name":"Notebook","price":129000}
```

Tên `name` xuất hiện nhờ `@JsonProperty` dù Java record component có tên `displayName`. Đừng dựa vào thứ tự các JSON object keys khi xây client.

**API v2 có cấu trúc mới:**

```bash
curl -i \
  -H 'Accept: application/json' \
  http://localhost:8080/api/v2/products/7
```

Body chính:

```json
{
  "id": 7,
  "displayName": "Notebook",
  "pricing": {"amount": 129000, "currency": "VND"},
  "sku": "NB-007"
}
```

Khoảng trắng/xuống dòng trong JSON ở trên được trình bày cho dễ đọc; server có thể trả một dòng. Đây là hai **contract song song**, không phải client v1 tự động đọc được v2.

**Request từ client cũ vẫn hoạt động:**

```bash
curl -i -X POST http://localhost:8080/api/v1/products/preview \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -d '{"name":"Notebook","price":129000}'
```

Body:

```json
{"name":"Notebook","price":129000}
```

**Request dùng alias mới vẫn được chấp nhận:**

```bash
curl -i -X POST http://localhost:8080/api/v1/products/preview \
  -H 'Content-Type: application/json' \
  -d '{"displayName":"Notebook","price":129000}'
```

Body vẫn là:

```json
{"name":"Notebook","price":129000}
```

Đó là ví dụ về **tolerant input** có kiểm soát, không phải chính sách chấp nhận tùy ý mọi field unknown. Nếu request chứa cả `name` và `displayName`, cần định nghĩa một quy tắc rõ ràng (ví dụ trả `400` khi hai giá trị trùng property) trong một phiên bản thực; ví dụ chỉ minh họa client gửi **một** trong hai tên.

**ID không tồn tại:**

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/api/v1/products/999
```

Status thường là `404 Not Found`. JSON lỗi chi tiết do exception handler, Spring Boot error configuration hoặc Spring MVC `ProblemDetail` tùy môi trường. **Không phụ thuộc vào một error body ngẫu nhiên chưa được tài liệu hóa**; nếu error response là hợp đồng, hãy xây và kiểm thử schema lỗi riêng.

## 7. Contract tests bằng MockMvc: kiểm tra shape thật trên HTTP

Chỉ unit-test Java DTO hoặc mapper là chưa đủ. Jackson config, controller annotation, converter, media type và response status cũng tham gia tạo HTTP contract. Một test gửi request qua `MockMvc` sẽ bắt được nhiều regression tại đường HTTP mà không cần bật một cổng server thực.

**`src/test/java/example/contract/ProductContractTest.java`**

```java
package example.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductContractTest {

    @Autowired
    MockMvc mvc;

    @Test
    void v1KeepsPublishedNamesAndShape() throws Exception {
        mvc.perform(get("/api/v1/products/7")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.name").value("Notebook"))
            .andExpect(jsonPath("$.price").value(129000))
            .andExpect(jsonPath("$.displayName").doesNotExist())
            .andExpect(jsonPath("$.pricing").doesNotExist());
    }

    @Test
    void v2PublishesNewStructure() throws Exception {
        mvc.perform(get("/api/v2/products/7")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Notebook"))
            .andExpect(jsonPath("$.pricing.amount").value(129000))
            .andExpect(jsonPath("$.pricing.currency").value("VND"))
            .andExpect(jsonPath("$.sku").value("NB-007"))
            .andExpect(jsonPath("$.name").doesNotExist())
            .andExpect(jsonPath("$.price").doesNotExist());
    }

    @Test
    void requestOldNameStillWorks() throws Exception {
        mvc.perform(post("/api/v1/products/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Notebook\",\"price\":129000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Notebook"))
            .andExpect(jsonPath("$.displayName").doesNotExist());
    }

    @Test
    void requestAliasDoesNotRenameV1Response() throws Exception {
        mvc.perform(post("/api/v1/products/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Notebook\",\"price\":129000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Notebook"))
            .andExpect(jsonPath("$.displayName").doesNotExist());
    }

    @Test
    void invalidRequestIsRejected() throws Exception {
        mvc.perform(post("/api/v1/products/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"price\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingProductHasNotFoundStatus() throws Exception {
        mvc.perform(get("/api/v1/products/999"))
            .andExpect(status().isNotFound());
    }
}
```

Chạy:

```bash
mvn test
```

Kết quả thành công có ý nghĩa: tất cả các trường hợp test trên pass và Maven kết thúc với exit code `0`. Thông báo tổng kết thường có dạng `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`; số thời gian chạy, các log và thứ tự test tùy môi trường.

**Phạm vi của test:** những test này giữ **ví dụ** v1, v2 và các đường lỗi nêu trên. Trong dự án thật, bổ sung tests về request thiếu field, null, unknown fields, enum, BigDecimal precision, thời gian, pagination, authorization và response lỗi. Cần giữ contract fixtures từ một bản API đã được duyệt để phát hiện thay đổi vô ý; test được tạo mới từ code hiện hành mà không so với contract cũ có thể “đúng đồng thời với một thay đổi gây hỏng”.

## 8. Cách tiến hóa mà vẫn phục vụ client cũ

Một quy trình hiệu quả là **expand → migrate → contract**:

1. **Kiểm kê consumer và hợp đồng hiện hữu.** Liệt kê endpoint, field, kiểu, required/nullable, unit, error codes, phân trang, các SDK và ứng dụng mobile không thể cập nhật tức thời. Xác định client nào cần giữ.
2. **Xác định thay đổi có thể ảnh hưởng client.** Đừng đánh giá chỉ từ diff Java class; hãy so sánh JSON/HTTP schema cũ và mới, đồng thời so sánh ngữ nghĩa. Với thay đổi lớn, đánh dấu breaking.
3. **Expand khả năng server mà không xóa v1.** Thêm v2 hoặc một tính năng tương thích; ánh xạ cả hai DTO từ cùng domain model. Khi đổi request field nhỏ, có thể tạm chấp nhận alias cũ/mới với quy tắc mơ hồ được xử lý rõ.
4. **Phát hành tài liệu, SDK và ví dụ mới.** Công bố JSON mẫu, OpenAPI schema, unit/timezone, API error contract và đường nâng cấp từ v1 lên v2. Với đối tác ngoài tổ chức, kế hoạch hỗ trợ version cũ phải có thời hạn.
5. **Migrate các client theo đợt.** Dùng traffic analytics, metrics hoặc version headers để đánh giá còn consumer v1; đừng giả định “backend đã deploy” đồng nghĩa client đã nâng cấp.
6. **Theo dõi lỗi contract thực tế.** Quan sát tỉ lệ `400`/`404`/`406`, lỗi deserialize phía client, việc dùng alias cũ; tránh log payload nhạy cảm. Hỗ trợ rollback trong khoảng chuyển đổi.
7. **Chỉ retire hợp đồng cũ theo chính sách deprecation đã công bố.** Thực hiện khi đã xác nhận client còn lại và mốc hỗ trợ. Việc loại bỏ endpoint v1 là thay đổi mang tác động bên ngoài, cần phối hợp release.

Khi triển khai rolling deployment nhiều instance, server cũ và mới có thể cùng tồn tại. Một migration chỉ thành công nếu dữ liệu chung/database/message events chịu được cả hai phiên bản trong giai đoạn chuyển tiếp. Đây là lý do nên giữ tương thích **cả luồng đọc và ghi** khi cập nhật schema chung, thay vì nâng database rồi ngay lập tức xóa field cũ.

### Tình huống đổi field name mà chưa cần v2

Nếu chỉ đổi Java class cho dễ hiểu, sử dụng `@JsonProperty("oldName")` hoặc API mapper để giữ nguyên JSON. Không tự động tạo version mới cho **mọi** refactor nội bộ.

### Tình huống cần v2

Nếu biến `price: 129000` thành `pricing: {amount:129000,currency:"VND"}`, client cũ không thể suy ra đường dẫn mới. Một v2 song song là lựa chọn dễ giải thích. Đừng xóa `price` khỏi v1 chỉ vì v2 đã có.

### Tình huống thêm optional field

Nếu hợp đồng nói client phải bỏ qua unknown response fields, thêm `description` (optional) thường được chấp nhận:

```json
{"id":7,"name":"Notebook","price":129000,"description":"A5"}
```

Tuy nhiên, client generated từ schema đóng có thể fail khi gặp field lạ. Vì vậy CI nên kiểm tra behavior consumer, và tổ chức phải công bố chính sách mở rộng schema thay vì coi additive luôn tuyệt đối an toàn.

## 9. Chọn cách version API

| Cách | Ví dụ | Điểm mạnh | Điểm cần quản lý |
| --- | --- | --- | --- |
| Version trong URL | `/api/v1/products`, `/api/v2/products` | Dễ nhìn và test, routing rõ | URL song song, nhiều mappings |
| Version qua header | `X-Api-Version: 2` | URI ổn định | Client/proxy/cache phải xử lý header |
| Version qua media type | `Accept: application/vnd.example.product-v2+json` | Gắn version với representation | Nội dung Content Negotiation phức tạp hơn |
| Version qua query | `?version=2` | Dễ thử nghiệm | Policy cache, documentation và routing cần nhất quán |

Trong Spring MVC 6.1, version qua URL dễ trình bày với các `@GetMapping` riêng. Không dựa vào annotation versioning chuyên biệt của các Spring major versions ra đời sau để làm ví dụ Boot 3.3.5 chạy được.

Hãy định nghĩa **version scope** rõ: version của toàn API, resource representation, hay một endpoint contract? Nếu chỉ thay một field trong một loại product, có thể dùng resource-specific versioning theo chính sách dự án; nhưng tránh một hỗn hợp khó dự đoán giữa URL, header và query. Không lấy nhãn `v2` làm bằng chứng rằng mọi breaking change trong tương lai của v2 đều được phép.

### Deprecation không đồng nghĩa với xóa ngay

Khi có v2, v1 có thể được đánh dấu deprecated trong tài liệu/API catalog, gửi tín hiệu qua headers tiêu chuẩn phù hợp nếu tổ chức sử dụng, và có kế hoạch sunset. Tránh công bố một thời hạn mà đội vận hành không cam kết thực hiện được. Mọi client cần có hướng di chuyển, ví dụ mapping:

| v1 | v2 |
| --- | --- |
| `name` | `displayName` |
| `price` (VND) | `pricing.amount` (VND) |
| Không có currency field | `pricing.currency = "VND"` |
| Không có SKU trong v1 output | `sku` được bổ sung ở v2 |

Bảng chỉ minh họa dữ liệu của ví dụ; hợp đồng thực tế còn phải mô tả nullability, units, error semantics và các endpoint khác.

## 10. Các vùng dễ gây breaking change âm thầm

### 10.1. Required, optional, null và absent

Đầu vào JSON:

```json
{}
```

```json
{"description":null}
```

```json
{"description":""}
```

Ba dữ liệu trên có thể có ý nghĩa khác nhau: không yêu cầu thay đổi, xóa mô tả, đặt mô tả thành chuỗi rỗng. Khi thiết kế PATCH cần biểu diễn rõ trạng thái **absent** và **present null**; DTO thông thường chỉ có `String description` có thể gộp cả hai thành Java `null`. Cân nhắc JSON Merge Patch, JSON Patch hoặc command DTO chứa thông tin field presence nếu nhu cầu thật sự cần.

Đừng thêm `@NotNull` vào trường mà client cũ có thể không gửi rồi coi đó là thay đổi tương thích. Để thêm field mới cho request v1, thường phải để optional và quy định default không thay đổi hành vi trước đây.

### 10.2. Kiểu số và độ chính xác

`Integer`, `Long`, `BigDecimal` và floating-point có behavior khác nhau. JavaScript clients có vùng số nguyên an toàn giới hạn; IDs lớn có thể cần **chuỗi định danh opaque** nếu contract ngay từ đầu đã quy định vậy. Không tự đổi từ JSON number sang string trong endpoint đang phục vụ.

Với tiền, ưu tiên mô tả chính xác `amount`, `currency`, đơn vị và quy tắc rounding. `BigDecimal` tránh nhiều lỗi nhị phân của `double` nhưng không tự giải quyết hợp đồng tiền. Nếu đổi đơn vị từ VND sang đơn vị nhỏ nhất hoặc USD, đó là thay đổi ngữ nghĩa.

### 10.3. Date/time và timezone

`"2026-10-21"` (local date), `"2026-10-21T04:00:00Z"` (instant) và một chuỗi timestamp không timezone không thể thay thế nhau tùy ý. Hợp đồng nên chọn kiểu và mô tả timezone (ví dụ UTC), độ chính xác fractional seconds và định dạng ISO-8601 cụ thể. Nâng cấu hình Jackson cho Java Time types có thể làm JSON đổi biểu diễn; hãy có test serialized fixtures khi nâng thư viện.

### 10.4. Enum mở rộng

Giả sử client có:

```java
switch (status) {
    case ACTIVE -> showActive();
    case INACTIVE -> showInactive();
}
```

Đây là **snippet minh họa**, yêu cầu `status` là enum và các hàm được định nghĩa trong code client. Thêm `SUSPENDED` trong response có thể làm client không xử lý được, đặc biệt khi enum parser nghiêm ngặt. Với enum được phép phát triển, thiết kế unknown fallback ngay từ đầu và mô tả chính sách forward compatibility. Với loại enum đóng, thêm value có thể xem là breaking.

### 10.5. Pagination và mảng

Một API từng trả toàn bộ:

```json
[{"id":1},{"id":2},{"id":3}]
```

nếu chuyển sang:

```json
{"items":[{"id":1},{"id":2}],"nextCursor":"abc"}
```

sẽ đổi kiểu top-level từ array sang object và thiếu các phần tử mà client cũ chờ đợi. Đây là breaking change, kể cả khi lý do là tăng hiệu năng. Nếu cần pagination mới, dùng v2 hoặc một endpoint mới; xác định page size, stable ordering, cursor semantics và điều gì xảy ra khi tài nguyên thay đổi trong lúc phân trang.

### 10.6. Schema lỗi và status

Client thường xử lý lỗi theo `status` và machine-readable `code`, không chỉ thông điệp người dùng. Đổi `404` thành `200` với `{"success":false}` hoặc đổi `error.code` thành `reason` có thể khiến retry logic và UI sai. Thiết kế error response ổn định, có mã lỗi hữu ích, và không lộ internal stack trace. Nếu dùng `ProblemDetail` theo RFC 9457, hãy xác định rõ contract của các extension fields và cách consumer đọc `type`/`status`/`detail`; không trộn nhiều schema lỗi tùy ý.

### 10.7. Configuration Jackson và response advice

Một lần thay `ObjectMapper` hoặc global `@JsonInclude`, naming strategy, timezone, property visibility, polymorphic typing hay `ResponseBodyAdvice` có thể đổi JSON **của mọi endpoint**. Trước khi áp dụng, hãy kiểm thử representative endpoints ở từng version.

`@JsonAlias` chỉ hỗ trợ deserialize aliases; `@JsonProperty` kiểm soát property name; `@JsonIgnore` có thể làm field biến mất; `@JsonInclude(Include.NON_NULL)` có thể chuyển field explicit null thành field absent. Không gọi những annotation này là “nội bộ” khi chúng ảnh hưởng wire contract.

## 11. Ghi hợp đồng bằng OpenAPI và kiểm thử ở CI

OpenAPI mô tả endpoints, operations, parameters, request/response media types, status codes và JSON schemas. Một đoạn schema v1 minh họa:

```yaml
openapi: 3.0.3
info:
  title: Products API
  version: 1.0.0
paths:
  /api/v1/products/{id}:
    get:
      parameters:
        - in: path
          name: id
          required: true
          schema:
            type: integer
            format: int64
      responses:
        "200":
          description: Product v1
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ProductV1"
        "404":
          description: Product not found
components:
  schemas:
    ProductV1:
      type: object
      required: [id, name, price]
      properties:
        id: {type: integer, format: int64}
        name: {type: string}
        price:
          type: number
          description: Amount in VND
```

Đây là **đoạn OpenAPI minh họa contract cho một endpoint**; nó không mô tả toàn bộ project ở mục 6 hoặc hoàn chỉnh error schema. Trong code thật, cân nhắc validation constraints, nullable, examples, response headers, auth và mọi error status.

Một pipeline kiểm soát thay đổi có thể:

1. Lưu schema đã phát hành làm baseline, không tự cập nhật baseline chỉ để test pass.
2. Generate/OpenAPI từ bản mới hoặc xây schema contract versioned.
3. Chạy **schema diff/compatibility check** để phát hiện field bị xóa/đổi tên/đổi kiểu, required biến động, endpoint bị mất, status đổi.
4. Chạy `MockMvc` integration tests với request/response fixtures cũ.
5. Chạy **consumer-driven contract tests** với những giả định thật của các client quan trọng.
6. Chặn merge/deploy hoặc yêu cầu version bump khi gặp breaking changes chưa được phê duyệt.
7. Khi release, cập nhật docs, changelog, SDK và hướng migration.

Chỉ so sánh OpenAPI schema chưa đủ: đổi đơn vị từ VND sang USD, thay sort order hay cách tính tổng vẫn có thể giữ cùng schema JSON. Phải có tests ngữ nghĩa nghiệp vụ và consumer tests.

### Snapshot test có nên so sánh nguyên chuỗi JSON?

Chụp nguyên body và so sánh string từng ký tự có thể bắt field thay đổi, nhưng sẽ thất bại vô ích khi thứ tự JSON object keys hoặc whitespace đổi. Thường nên parse JSON và so sánh **cây JSON/JSONPath theo hợp đồng**. Với những field contract có order cố định (như mảng đã sort), hãy kiểm tra đúng thứ tự mảng. Với currency/number/time, assert cả ý nghĩa và giá trị chuẩn.

### Producer tests và consumer tests khác nhau

Producer tests hỏi: “Server còn trả đúng những thứ đã hứa?” Consumer tests hỏi: “Client thực tế còn chạy được với response/request của server mới?” Nếu chỉ test server mới theo DTO mới, một rename làm hỏng client cũ có thể bị bỏ sót. Khi nhiều team sở hữu nhiều client, cần phối hợp contract fixtures/chạy test trên cả hai phía.

## 12. Quy trình review một thay đổi DTO/JSON

Ví dụ developer đề xuất PR: “Chuyển `Product.price` sang `Product.pricing.amount` vì backend đổi Money model.”

Hãy kiểm tra theo chuỗi:

- **Public contract hiện nay:** API nào đang trả `price`? v1/v2, mobile, SDK, partner?
- **Diff wire format:** JSON thực tế trước/sau khác key/type/meaning gì? Cả thành công lẫn lỗi?
- **Tương thích phía consumer:** nếu client cũ deserialize response mới, điểm nào có thể fail?
- **Phương án không breaking:** giữ v1 DTO mapper trả `price`, thêm v2 cho `pricing`.
- **Request compatibility:** client cũ gửi field nào? Trường nào vừa được đánh dấu required?
- **Status và metadata:** `Content-Type`, status, pagination, error codes có đổi không?
- **Security:** entity mới có fields nhạy cảm bị JSON serializer lộ vô tình không?
- **Tests:** v1 fixtures vẫn pass, v2 fixtures mới pass, validation/error cases phù hợp?
- **Release:** docs và thời gian hỗ trợ v1 có rõ không? Telemetry theo dõi client cũ?

Ví dụ diff phù hợp:

```diff
- // V1 controller trả thẳng domain Product.
- return productService.find(id);
+ // V1 controller giữ hợp đồng JSON cũ qua mapping.
+ Product product = productService.find(id);
+ return new ProductV1(
+         product.getId(),
+         product.getDisplayName(),
+         product.getMoney().getAmount());
```

Đây là **diff giải thích kiến trúc**, phụ thuộc các class service/domain giả định và không phải đoạn có thể paste vào project chạy ở mục 6. Nó cho thấy điểm cần bảo vệ là representation ở ranh giới HTTP, không phải tên method nội bộ.

## 13. Một số quy tắc thực hành

1. **Version hợp đồng công khai, không version theo database schema.** Một schema mới có thể phục vụ cả v1/v2 thông qua mapper.
2. **Giữ tách biệt input DTO và output DTO.** Vừa tránh over-posting vừa kiểm soát response fields.
3. **Định nghĩa “tương thích” bằng consumer thực.** Cho phép unknown fields/enum fallback khi đã công bố; đừng giả định SDK nào cũng tolerant.
4. **Không đổi tên, kiểu, đơn vị hoặc semantic của field v1 tùy tiện.** Nếu phải đổi, tạo v2 có migration.
5. **Tạo field mới là optional khi cần tương thích với request cũ**, với default giữ logic cũ; đừng thêm required request fields âm thầm.
6. **Giữ quy tắc null/absent/empty nhất quán.** Đặc biệt quan trọng với PATCH và các DTO có primitive defaults.
7. **Hạn chế global Jackson settings và response wrappers.** Mỗi thay đổi toàn cục đều cần contract regression tests.
8. **Công bố mốc deprecated/sunset có thể thực thi được.** Đo usage của version cũ trước khi loại bỏ.
9. **Test status, headers, content type, body và error contract.** Không chỉ assert controller trả đúng class Java.
10. **Giữ tính bảo mật ở tầng service.** DTO hẹp, validation và authorization là các nhiệm vụ độc lập; không dựa vào Jackson annotation để thực hiện phân quyền.

## 14. Bài tập tự kiểm tra

1. Backend đổi Java field `displayName` nhưng client vẫn cần JSON `name`. Bạn có thể dùng `@JsonProperty` ra sao, và khi nào mapper DTO rõ ràng hơn?
2. Vì sao thêm field response thường an toàn hơn xóa field, nhưng không bảo đảm an toàn với mọi client?
3. Client cũ gửi request không có `currency`. Làm thế nào thêm currency mà không biến request cũ thành `400`?
4. Giữ `price` là JSON number nhưng đổi đơn vị tiền sang cents. Hợp đồng có giữ nguyên không? Vì sao?
5. `@JsonAlias("displayName")` có khiến response tự xuất `displayName` thay cho `name` không?
6. Vì sao thay array response bằng object `{"items":[...]}` có thể là breaking change?
7. Có cần thay URL từ `/v1` sang `/v2` khi chỉ đổi tên biến nội bộ không?
8. Contract test của một controller nên so sánh những yếu tố nào bên ngoài class Java?
9. Khi hai phiên bản server cùng chạy trong rolling deploy, những thay đổi database/message schema nào có thể làm instance cũ bị hỏng?
10. Nếu `null` và vắng field từng mang hai ý nghĩa, một DTO Java `String` đơn giản có luôn giữ được sự khác biệt này không?

**Gợi ý đáp án:** (1) Cố định JSON key hoặc map DTO; (2) strict clients và closed SDK có thể từ chối unknown fields; (3) optional/default giữ behavior cũ hoặc version mới; (4) không, semantic thay đổi; (5) không, alias dùng khi đọc; (6) top-level type và phân trang thay đổi; (7) không cần nếu wire contract vẫn y nguyên; (8) URL/method/status/headers/media type/shape/meaning/error; (9) schema chỉ hỗ trợ instance mới có thể phá bản cũ; (10) không, cần presence-aware representation khi quan trọng.

## 15. Tài liệu tham khảo

- [Spring MVC: ResponseBody và HttpMessageConverter](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responsebody.html)
- [Spring Framework: Jackson JSON](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/jackson.html)
- [Spring Boot 3.3: Testing Spring Boot Applications và MockMvc](https://docs.spring.io/spring-boot/3.3/reference/testing/spring-boot-applications.html)
- [Jackson 2: JsonAlias Javadoc](https://fasterxml.github.io/jackson-annotations/javadoc/2.12/com/fasterxml/jackson/annotation/JsonAlias.html)
- [Microsoft: Web API Design Best Practices](https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-design)
- [Microsoft: API Design and Versioning](https://learn.microsoft.com/en-us/azure/architecture/microservices/design/api-design)
- [OpenAPI Specification 3.0.3](https://spec.openapis.org/oas/v3.0.3)
- [GitHub REST API: Breaking Changes](https://docs.github.com/en/rest/about-the-rest-api/breaking-changes)

Bài học giới hạn trong **ổn định hợp đồng REST API khi thay đổi cấu trúc dữ liệu** và những kỹ thuật Spring MVC có giá trị trực tiếp cho nhiệm vụ đó. Chương trình minh họa dùng Java 17 và Spring Boot 3.3.5; các chiến lược compatibility/versioning là quy tắc thiết kế cần được quyết định theo hợp đồng và consumer thực tế.


</details>

- [Quay lại đầu trang](#back-to-top)
