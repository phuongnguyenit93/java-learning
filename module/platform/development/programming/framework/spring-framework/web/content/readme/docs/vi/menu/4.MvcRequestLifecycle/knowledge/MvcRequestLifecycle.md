<a id="back-to-top"></a>

# Luồng xử lý MVC

## Menu
- [Servlet container, Filter chain và điểm vào DispatcherServlet](#mvc-request-lifecycle-001)
- [HandlerMapping: tìm thành phần xử lý phù hợp với yêu cầu](#mvc-request-lifecycle-002)
- [HandlerAdapter: gọi thành phần xử lý và hỗ trợ chữ ký phương thức](#mvc-request-lifecycle-003)
- [Giải quyết tham số, thực thi controller và xử lý giá trị trả về](#mvc-request-lifecycle-004)
- [ModelAndView, ViewResolver và giai đoạn kết xuất giao diện](#mvc-request-lifecycle-005)
- [HandlerExceptionResolver và luồng xử lý khi có ngoại lệ](#mvc-request-lifecycle-006)
- [Các loại dispatch REQUEST, FORWARD, INCLUDE, ERROR và ASYNC](#mvc-request-lifecycle-007)
- [Vòng đời controller singleton và nguy cơ dùng trạng thái chia sẻ](#mvc-request-lifecycle-008)

## <a id="mvc-request-lifecycle-001">Servlet container, Filter chain và điểm vào DispatcherServlet</a>

<details>
<summary>Xem chi tiết</summary>

# Servlet container, Filter chain và điểm vào DispatcherServlet trong Spring Web MVC

## 1. Mục tiêu và phạm vi

Khi trình duyệt gọi `GET /api/hello?name=Lan`, phương thức `@GetMapping` không phải thành phần đầu tiên nhận request. Trong ứng dụng Spring Web MVC chạy trên Servlet stack, **Servlet container** tiếp nhận request; những **Servlet Filter** được đăng ký phù hợp có cơ hội xử lý; request sau đó mới đến **`DispatcherServlet`** để Spring MVC tìm controller và tạo response.

Sau bài này, bạn có thể:
- Mô tả đúng ranh giới giữa HTTP server/Servlet container, Filter chain và Spring MVC.
- Đọc được luồng `client → container → filters → DispatcherServlet → handler → response`, cả nhánh bị chặn và nhánh lỗi.
- Phân biệt Servlet `Filter` với Spring MVC `HandlerInterceptor` và Spring Security filter chain.
- Tự đăng ký filter bằng `FilterRegistrationBean` trong Spring Boot, kiểm soát URL pattern, thứ tự, dispatcher type.
- Hiểu tại sao `OncePerRequestFilter` không có nghĩa đơn giản là chạy đúng một lần trong *mọi* lần dispatch của cả request.
- Kiểm tra luồng bằng ứng dụng Java 17 + Spring MVC thực thi được và test MockMvc.

Bài tập trung vào **Spring Web MVC dựa trên Jakarta Servlet** (Spring Framework 6.x / Spring Boot 3.x), không mô tả WebFlux reactive pipeline. Các ví dụ chạy dùng Spring Boot để khởi động embedded Tomcat; nguyên tắc Servlet/Filter/DispatcherServlet vẫn áp dụng nếu deploy Spring MVC dưới dạng WAR vào container độc lập.

## 2. Ai sở hữu phần nào của request?

```text
             +------------------- HTTP client --------------------+
             | GET /api/hello?name=Lan                          |
             +----------------------------+----------------------+
                                          |
                               (reverse proxy nếu có)
                                          |
                                          v
           +------------ Servlet container (Tomcat) ------------+
           |  Kết nối HTTP, parse request, chọn Servlet mapping |
           |                                                     |
           |  Servlet Filter chain cho dispatch hiện tại         |
           |    Filter A: trước chain.doFilter(...)              |
           |      Filter B: trước chain.doFilter(...)            |
           |        +-------------------------------------+      |
           |        | DispatcherServlet                  |      |
           |        |  HandlerMapping tìm route          |      |
           |        |  HandlerInterceptor.preHandle      |      |
           |        |  HandlerAdapter gọi controller     |      |
           |        |  Chuyển giá trị trả về thành body  |      |
           |        |  (hoặc ViewResolver render view)   |      |
           |        +-------------------------------------+      |
           |      Filter B: sau chain.doFilter(...)              |
           |    Filter A: sau chain.doFilter(...)                |
           +----------------------------+------------------------+
                                        |
                                        v
                                 HTTP response
```

Sơ đồ biểu diễn đường đi **thành công, đồng bộ** của một request được các filter cho đi tiếp. Nó không phải thứ tự cố định cho mọi cấu hình: filter có thể trả response sớm, servlet có thể khác `DispatcherServlet`, exception có thể tạo error dispatch, còn async có thể bổ sung dispatch về sau.

| Tầng | Đơn vị quản lý | Vai trò | Được biết trước thông tin gì? |
|---|---|---|---|
| Servlet container | Tomcat, Jetty, một container Jakarta Servlet tương thích | Nhận HTTP, quản lý Servlet/Filter, mapping, dispatch, request/response object, thread hoặc async lifecycle. | URI, method, headers, mapping và cấu hình container. |
| Servlet `Filter` | Servlet container | Bọc request/response, áp dụng chính sách chung, cho qua bằng `chain.doFilter` hoặc kết thúc sớm. | `HttpServletRequest` và `HttpServletResponse`; chưa cần biết handler Spring MVC. |
| `DispatcherServlet` | Spring MVC; được đăng ký như Servlet với container | Điểm vào tập trung (front controller) của Spring MVC; điều phối tới các thành phần Spring. | Spring `WebApplicationContext`, mappings và cấu hình MVC. |
| `HandlerMapping` | Spring MVC | Tìm handler khớp request; có thể trả kèm interceptor chain. | Những mapping đã đăng ký; method/path/headers/params theo điều kiện mapping. |
| `HandlerAdapter` | Spring MVC | Gọi handler được chọn, resolve arguments và xử lý return value. | Chữ ký phương thức/controller cùng các adapter, resolver, converter. |
| `HandlerInterceptor` | Spring MVC | Hook quanh xử lý handler đã được Spring MVC map, ví dụ `preHandle`, `postHandle`, `afterCompletion`. | Handler được chọn; không thay thế bộ lọc của Servlet container. |

**Không đồng nhất Servlet Filter với Spring MVC interceptor.** Filter chạy ở tầng container, có thể áp dụng cho request không vào controller. Interceptor thuộc Spring MVC, gắn vào chuỗi xử lý handler sau bước tìm mapping; phạm vi callback và exception/async cũng khác.

## 3. Servlet container thực hiện những bước nào?

Đối với HTTP request vào ứng dụng Servlet:

1. Container nhận kết nối/request, parse HTTP, quản lý lifecycle của các object `HttpServletRequest`/`HttpServletResponse`, xác định context path và Servlet mapping.
2. Từ URL pattern, Servlet name, thứ tự filter và **dispatcher type** hiện tại, container chọn những Filter registration phù hợp.
3. Container tạo `FilterChain` và gọi `doFilter(request,response,chain)` của filter đầu tiên. Mỗi filter có quyền gọi `chain.doFilter(...)` để chuyển quyền xử lý cho phần tử tiếp theo.
4. Sau filter cuối, container gọi Servlet được map. Với Spring MVC, thường là `DispatcherServlet.service(...)` và các xử lý framework liên quan.
5. Khi Servlet kết thúc xử lý đồng bộ, lời gọi `chain.doFilter(...)` trả về theo ngăn xếp; mã sau nó trong từng filter được chạy từ trong ra ngoài. Container hoàn tất response; response có thể đã commit trước đó.

**Quan trọng:** "đi vào Servlet" là quá trình container thực hiện Servlet mapping, khác với "chọn controller" do `HandlerMapping` bên trong Spring MVC thực hiện. Một filter có thể chạy trước cả khi Spring xác định có controller nào khớp request hay không. Nếu không có MVC route phù hợp, Spring MVC có thể trả 404; các filter đã được gọi vẫn có thể chạy nhánh `finally` của chúng.

### Mapping của Servlet và mapping của filter

- Servlet mapping `/` thường cho `DispatcherServlet` xử lý route ứng dụng, nhưng cấu hình có thể dùng một prefix khác, ví dụ `/mvc/*`.
- Filter mapping `/*` thường bao phủ các request trong web application. Có thể giới hạn bằng `/api/*` hoặc ánh xạ theo servlet name.
- Filter mapping là cấu hình của **container**, độc lập với `@GetMapping` hay `@RequestMapping`.
- URL có context path, servlet path và path info khác nhau. Đừng nhầm `request.getRequestURI()` (thường chứa context path, không chứa query) với path template Spring MVC hay `getQueryString()`.
- Reverse proxy, load balancer và container có thể xử lý request target hoặc forwarded headers trước khi request đến Spring. Không lấy nguyên header do client tự gửi làm bằng chứng đáng tin về origin/protocol nếu hệ thống proxy chưa cấu hình trust đúng.

### Filter chain là ngăn xếp gọi nhau

Với hai filter A rồi B:

```text
A: before
  B: before
    DispatcherServlet -> Controller -> Response
  B: after
A: after
```

Pseudo-code:

```java
// Đây là pseudo-code về flow, không phải file Java chạy độc lập.
void doFilter(Request request, Response response, FilterChain chain) {
    before(request);
    try {
        chain.doFilter(request, response);
    } finally {
        after(response);
    }
}
```

Nếu B **không gọi** `chain.doFilter`, C hoặc `DispatcherServlet` không được gọi trên nhánh đó; container có thể trả response do B tự tạo. Điều này hợp lệ khi B thực hiện từ chối truy cập, chặn rate limit hay trả CORS preflight response theo chính sách đã thiết kế. Bắt buộc phải xác định status và response cần trả, không đơn giản bỏ qua chain rồi để client nhận kết quả khó hiểu.

### Hủy request, exception và response commit

- `chain.doFilter` có thể ném `ServletException` hoặc `IOException`. `finally` vẫn chạy, nhưng không có nghĩa request đã xử lý thành công.
- Nếu response đã được commit (headers/body đã gửi), filter không thể tùy ý thay status/header hay viết lại body.
- Một filter có thể bọc request/response bằng wrapper để điều chỉnh hành vi, nhưng cần tuân thủ contract Servlet, xử lý stream cẩn thận và tránh đọc cạn request body trước controller.
- Khối `finally` thường phù hợp để giải phóng context/ThreadLocal, đo thời gian và hoàn thành instrumentation; cần tránh log dữ liệu nhạy cảm.

## 4. `DispatcherServlet` — front controller của Spring MVC

`DispatcherServlet` là một Servlet thật, được container gọi **sau các filter đã cho phép request đi tiếp**. Nó không tự tiếp nhận TCP socket, không quyết định thứ tự Servlet filters và không thay container quản lý Servlet lifecycle.

Bên trong Spring MVC, đường xử lý điển hình của request đồng bộ là:

```text
DispatcherServlet
    |
    +--> tìm HandlerExecutionChain bằng HandlerMapping
    |      |-- handler (thường là HandlerMethod)
    |      +-- interceptor(s)
    |
    +--> HandlerInterceptor.preHandle(...)
    |      |-- false: dừng trước khi gọi handler
    |      +-- true: tiếp tục
    |
    +--> HandlerAdapter.handle(...)
    |      +-- resolve @PathVariable / @RequestParam / @RequestBody ...
    |      +-- convert/bind/validate dữ liệu khi cấu hình phù hợp
    |      +-- gọi controller
    |      +-- xử lý return value
    |
    +--> Response path
    |      |-- @ResponseBody/@RestController:
    |      |      HttpMessageConverter ghi JSON/text/...
    |      +-- MVC view:
    |             ModelAndView -> ViewResolver -> render
    |
    +--> postHandle/afterCompletion tương ứng với lifecycle
           hoặc HandlerExceptionResolver khi có exception
```

Các thành phần thường gặp:

| Thành phần MVC | Công dụng |
|---|---|
| `RequestMappingHandlerMapping` | Đọc các mapping kiểu `@GetMapping`, `@PostMapping`, `@RequestMapping` và tìm handler. |
| `RequestMappingHandlerAdapter` | Gọi annotated controller method và dùng argument/return-value resolvers. |
| `HandlerMethodArgumentResolver` | Cấp tham số controller như `@RequestParam`, `@PathVariable`, `@RequestBody`. |
| `HttpMessageConverter` | Chuyển request body thành object hoặc object trả về thành body HTTP, ví dụ JSON. |
| `ViewResolver` | Chọn view để render trong MVC trả trang; API trả JSON thường không cần resolve view. |
| `HandlerExceptionResolver` | Thử giải quyết exception ở tầng MVC; ví dụ `@ExceptionHandler` hoặc `@ControllerAdvice`. |
| `HandlerInterceptor` | Hook quanh handler MVC; chỉ hoạt động trong luồng handling của MVC đã cấu hình. |

Đường xử lý thực tế có thêm nhiều bước, ví dụ multipart, locale, conditional request, async handling, và trường hợp không tìm thấy handler. **Không mặc định** mỗi request đều đi qua toàn bộ callback hoặc đều dùng `ViewResolver`. Với `@RestController` trả `Map`, response body thường được tạo ngay bởi `HttpMessageConverter` khi adapter xử lý return value; `postHandle` không phải điểm tốt để sửa JSON body đã được serialize.

### Nếu không có handler?

Spring MVC có thể trả 404 hoặc ném exception tùy cấu hình và loại route. Tuy nhiên filter đã khớp và gọi `chain.doFilter` vẫn bao quanh request; Spring Security filter hoặc custom filter ở container có thể từ chối trước khi MVC kịp chọn handler. Vì vậy khi gỡ lỗi 401/403, không nên vội kết luận `@GetMapping` sai.

## 5. Ví dụ chạy được: tự quan sát filter trước và sau controller

Ví dụ dùng **Java 17**, Spring Boot **3.5.16**, Spring MVC và Gradle. Spring Boot được dùng để cấu hình embedded Tomcat và đăng ký `DispatcherServlet` tự động. Trong một project dùng Spring Framework MVC thuần, bạn phải tự đăng ký Servlet vào container (bằng `web.xml`, `WebApplicationInitializer` hoặc Servlet initializer tương ứng).

### 5.1. Cấu trúc project

```text
servlet-pipeline-demo/
├── settings.gradle
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/example/pipeline/
    │   │   ├── PipelineApplication.java
    │   │   └── web/
    │   │       ├── HelloController.java
    │   │       ├── TraceFilter.java
    │   │       └── TraceFilterConfig.java
    │   └── resources/application.properties
    └── test/java/com/example/pipeline/
        └── HelloControllerTest.java
```

Nếu tạo project từ https://start.spring.io/, chọn Gradle/Groovy, Java 17, Spring Boot 3.5.x và dependency **Spring Web**; Gradle Wrapper được Initializr tạo sẵn. Các đoạn sau là nội dung hoàn chỉnh cho những file chính; package phải trùng với đường dẫn dưới `src/main/java` hoặc `src/test/java`.

`settings.gradle`:

```groovy
rootProject.name = 'servlet-pipeline-demo'
```

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.16'
    id 'io.spring.dependency-management' version '1.1.7'
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
```

`spring-boot-starter-web` trong Spring Boot 3.5.x kéo vào Spring MVC, Jackson cho JSON và embedded Tomcat. Spring Boot quản lý phiên bản framework/transitive dependencies. Nếu ứng dụng đã dùng một BOM/platform riêng, cần tuân thủ mô hình dependency management của project thay vì copy nguyên cấu hình ví dụ.

`src/main/resources/application.properties`:

```properties
spring.application.name=servlet-pipeline-demo
server.port=8080
```

### 5.2. Lớp khởi động

`src/main/java/com/example/pipeline/PipelineApplication.java`:

```java
package com.example.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(PipelineApplication.class, args);
    }
}
```

`@SpringBootApplication` kích hoạt auto-configuration và component scan từ package `com.example.pipeline` xuống các subpackage. Spring Boot tạo/đăng ký `DispatcherServlet` cùng embedded server khi phát hiện Servlet web stack phù hợp; đây là lý do chúng ta không phải viết `web.xml` trong ví dụ này.

### 5.3. Controller được `DispatcherServlet` tìm thấy

`src/main/java/com/example/pipeline/web/HelloController.java`:

```java
package com.example.pipeline.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, String> hello(
            @RequestParam(name = "name", defaultValue = "bạn") String name) {

        System.out.println("controller: handle name=" + name);
        return Map.of("message", "Xin chào, " + name + "!");
    }
}
```

Với `GET /api/hello?name=Lan`, annotated handler này được `RequestMappingHandlerMapping` lựa chọn và `RequestMappingHandlerAdapter` gọi. Return value `Map` được Jackson-based message converter serialize thành JSON. Không cần `ViewResolver` cho endpoint này.

### 5.4. Viết một Servlet Filter quan sát pipeline

`src/main/java/com/example/pipeline/web/TraceFilter.java`:

```java
package com.example.pipeline.web;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class TraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        System.out.println("filter: before " + request.getMethod()
                + " " + path + " dispatch=" + request.getDispatcherType());

        try {
            chain.doFilter(request, response);
        } finally {
            System.out.println("filter: after status=" + response.getStatus());
        }
    }
}
```

Đây là lớp Java đầy đủ, dùng package `jakarta.servlet.*` tương thích Spring Framework 6.x. `OncePerRequestFilter` cung cấp một triển khai `Filter.doFilter` để xử lý marker đã lọc và quyết định có bỏ qua async/error dispatch theo contract. Với các điều kiện bình thường, code custom chỉ cần override `doFilterInternal`.

Vì sao cần `try/finally`? Nếu controller hoặc filter sau đó ném exception, nhánh cleanup vẫn có cơ hội chạy. Tuy nhiên status nhìn thấy trong `finally` có thể chưa phải status cuối cùng được container ghi sau khi xử lý exception/error dispatch. Không được suy đoán rằng log `status=200` sau một exception chắc chắn chứng minh client nhận HTTP 200.

### 5.5. Đăng ký filter qua `FilterRegistrationBean`

`src/main/java/com/example/pipeline/web/TraceFilterConfig.java`:

```java
package com.example.pipeline.web;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class TraceFilterConfig {

    @Bean
    FilterRegistrationBean<TraceFilter> traceFilterRegistration() {
        FilterRegistrationBean<TraceFilter> registration =
                new FilterRegistrationBean<>();

        registration.setFilter(new TraceFilter());
        registration.setName("traceFilter");
        registration.addUrlPatterns("/api/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
```

Ý nghĩa từng lựa chọn:

- `setFilter(new TraceFilter())` tạo filter instance để Servlet container đăng ký.
- `addUrlPatterns("/api/*")` giới hạn URL pattern thay vì chạy với toàn bộ request. Pattern này là quy tắc Servlet URL matching, không phải Spring MVC `PathPattern`.
- `setDispatcherTypes(DispatcherType.REQUEST)` cho filter quan sát **initial request dispatch** trong ví dụ; không tự chạy ở `ASYNC`, `ERROR` hoặc `FORWARD`.
- `setOrder(Ordered.LOWEST_PRECEDENCE - 10)` đặt độ ưu tiên tương đối của filter registration trong nhóm do Boot quản lý. Giá trị số thấp hơn thường được xếp trước; tuyệt đối không giả định thứ tự với mọi filter của container, security hoặc thư viện mà chưa kiểm tra cấu hình.
- `setName(...)` cho registration tên rõ ràng, thuận tiện khi kiểm tra log/registration.
- `@Bean` đăng ký chính **`FilterRegistrationBean`**. `TraceFilter` được tạo tại đây và **không đánh dấu `@Component`**, tránh nguy cơ đăng ký cùng filter thêm lần nữa qua cơ chế auto-registration của filter bean.

Spring Boot có cơ chế tự đăng ký Servlet `Filter` bean, nhưng khi cần kiểm soát order, dispatcher types và URL patterns thì `FilterRegistrationBean` minh bạch hơn. Đừng dùng cả `@WebFilter`, `@Component` và `FilterRegistrationBean` cho cùng một filter nếu chưa hiểu rõ cách container xử lý các registration chồng nhau.

### 5.6. Chạy thử và quan sát

Nếu project có Gradle Wrapper, ở root project:

```bash
# Linux/macOS
./gradlew test
./gradlew bootRun
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

Ở terminal khác:

```bash
curl -i "http://localhost:8080/api/hello?name=Lan"
```

Trên Windows PowerShell có thể dùng `curl.exe -i "http://localhost:8080/api/hello?name=Lan"` để chắc chắn gọi bản executable curl.

Response body mong đợi:

```json
{"message":"Xin chào, Lan!"}
```

Một chuỗi log ứng dụng có ý nghĩa trong **request GET thành công, đồng bộ** là:

```text
filter: before GET /api/hello dispatch=REQUEST
controller: handle name=Lan
filter: after status=200
```

Các log khác của Spring Boot và container có thể xen giữa; đây là **ba dòng do code ví dụ in ra theo quan hệ thứ tự**, không phải toàn bộ stdout của chương trình. Không kết luận chỉ từ log rằng filter A luôn chạy trước filter B khi có thêm dependency khác hoặc thay đổi registration order.

Gọi endpoint khác không bắt đầu bằng `/api/`, ví dụ `GET /not-found`, sẽ không kích hoạt `TraceFilter` vì pattern ở ví dụ giới hạn `/api/*`. Nếu gọi `GET /api/missing`, filter vẫn có thể in `before` và `after` dù không có controller khớp; Spring MVC xử lý 404 sau khi request đi qua filter.

## 6. Kiểm thử bằng MockMvc

`src/test/java/com/example/pipeline/HelloControllerTest.java`:

```java
package com.example.pipeline;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HelloControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void requestPassesThroughMvcAndReturnsJson() throws Exception {
        mvc.perform(get("/api/hello").param("name", "Lan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xin chào, Lan!"));
    }

    @Test
    void queryParameterDefaultsWhenMissing() throws Exception {
        mvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xin chào, bạn!"));
    }
}
```

`@SpringBootTest` tải Spring context; `@AutoConfigureMockMvc` cấu hình MockMvc. Mặc định, auto-configured MockMvc cũng đăng ký filter beans trong ngữ cảnh thích hợp để kiểm tra request qua lớp web. Đây là test **trong môi trường mock servlet request**, không dùng HTTP socket hay Tomcat thực tế. Test trên không trực tiếp assert dòng log hay container dispatcher mapping, vì vậy nó chủ yếu chứng minh controller mapping + response.

Nếu mục tiêu test chính xác Servlet filter registration, thứ tự, `DispatcherType.ERROR` hoặc async dispatch qua container thực, bổ sung integration test chạy server ngẫu nhiên bằng `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` và HTTP client phù hợp. Hành vi của proxy, Tomcat, filter registrations và servlet dispatcher types chỉ được kiểm chứng đầy đủ trên đường request/container thực.

## 7. Phân biệt Servlet Filter, MVC Interceptor và Spring Security

| Khía cạnh | Servlet Filter | Spring MVC HandlerInterceptor | Spring Security filter chain |
|---|---|---|---|
| Thuộc tầng | Servlet container | Nội bộ Spring MVC | Bên trong một Servlet Filter proxy/chain |
| Điểm chạy | Trước Servlet được gọi, với các dispatcher types được map | Sau khi MVC tìm được handler và trước/sau khi handler xử lý theo callback | Trong Servlet filter chain, thường trước MVC controller |
| Có thể chặn request? | Có, không gọi `chain.doFilter` | Có, trả `false` từ `preHandle` | Có, kiểm tra authentication/authorization và quyết định tiếp tục/trả lỗi |
| Thấy handler method? | Không mặc định | `preHandle(..., Object handler)` có handler được chọn | Không mặc định phụ thuộc mapping MVC, dù có thể tích hợp thêm |
| Ứng dụng điển hình | Logging, correlation ID, wrapping request, encoding, CORS handling | Logic gắn với handler, đo thời gian MVC, bổ sung model/locale | Xác thực, phân quyền, CSRF và security context |

Với Spring Security, `DelegatingFilterProxy` của Servlet có thể chuyển lời gọi sang bean Spring tên `springSecurityFilterChain` (thường là `FilterChainProxy`). Bên trong đó, Spring Security chọn một `SecurityFilterChain` phù hợp với request. Như vậy có **hai khái niệm “chain”**:

```text
Container FilterChain
    |
    +-- filter thông thường
    +-- DelegatingFilterProxy
    |     |
    |     +-- Spring Security FilterChainProxy
    |            +-- SecurityFilterChain chọn theo request
    |                  +-- security filters...
    |
    +-- DispatcherServlet
          +-- Spring MVC HandlerExecutionChain
                +-- MVC interceptors
                +-- controller
```

Spring Security không tự động có mặt trong demo chỉ vì dùng `spring-boot-starter-web`; muốn tích hợp nó, project cần dependency và cấu hình bảo mật tương ứng.

**Không nên** thực hiện security dựa độc nhất vào `HandlerInterceptor`: có request không đến handler MVC, và các quy tắc authentication/authorization thường cần chạy trước lựa chọn handler hoặc trên toàn bộ surface phù hợp. Spring Security cung cấp mô hình filter chain và các extension point chuyên biệt hơn.

### Callback interceptor không trùng callback filter

Một `HandlerInterceptor` có:
- `preHandle(request,response,handler)`: chạy trước handler, có thể trả `false` để dừng xử lý.
- `postHandle(request,response,handler,modelAndView)`: theo sau handler khi luồng phù hợp; với `@ResponseBody`, body có thể đã được converter ghi nên không phải hook sửa body tùy ý.
- `afterCompletion(request,response,handler,exception)`: chạy sau khi kết thúc xử lý MVC trong các điều kiện callback của interceptor, thường dùng cleanup; không cam kết mọi nhánh, ví dụ interceptor trả `false` trước khi được chấp nhận vào execution chain.

Interceptor không thay thế vòng `try/finally` của filter, và filter không thay thế MVC argument/return-value processing.

## 8. Dispatcher types: vì sao cùng request có thể đi qua filter nhiều lần?

`jakarta.servlet.DispatcherType` phân loại cách một Servlet dispatch được gọi. Các giá trị chính:

| Loại | Khi thường xuất hiện |
|---|---|
| `REQUEST` | Client gửi request bình thường đến container. |
| `FORWARD` | Server thực hiện `RequestDispatcher.forward(...)` sang resource/Servlet khác. |
| `INCLUDE` | Server thực hiện `RequestDispatcher.include(...)` để nhúng output của resource khác. |
| `ASYNC` | Servlet async processing dispatch request ở một giai đoạn về sau. |
| `ERROR` | Container thực hiện error dispatch đến error page/Servlet mapping sau một số lỗi hoặc `sendError`. |

**Filter registration** phải bao gồm dispatcher type thích hợp thì container mới gọi filter trên loại dispatch đó. Nếu không chỉ định type, theo Servlet spec hành vi mặc định thường là `REQUEST`; cấu hình của Boot và từng registration có thể bổ sung type. Ví dụ `TraceFilter` chủ động đặt chỉ `REQUEST` để log minh bạch.

`OncePerRequestFilter` cung cấp thêm một lớp kiểm soát chạy một lần theo request dispatch và luồng thực thi. Nó **không tự mở rộng filter registration** để nhận async/error dispatch. Mặc định:
- `shouldNotFilterAsyncDispatch()` trả `true`: bỏ qua các subsequent async dispatch.
- `shouldNotFilterErrorDispatch()` trả `true`: bỏ qua error dispatch tương ứng.
- Nếu muốn xử lý async/error, phải xem **cả hai điều kiện**: registration ở container có map type đó không, và `OncePerRequestFilter` có cho phép chạy nội bộ không.
- Không thể lấy tên lớp "OncePerRequestFilter" để kết luận chính xác *một lần cho toàn bộ HTTP request* khi có multiple dispatch hoặc thread khác nhau; contract thực tế là once per dispatch phù hợp các điều kiện.

Ví dụ cấu hình thêm type vào một filter khác (mang tính mô tả):

```java
registration.setDispatcherTypes(
        DispatcherType.REQUEST,
        DispatcherType.ASYNC,
        DispatcherType.ERROR);
```

Nếu lớp vẫn kế thừa `OncePerRequestFilter` và không override các phương thức `shouldNotFilterAsyncDispatch`/`shouldNotFilterErrorDispatch`, việc thêm dispatcher types **chưa đủ** khiến code `doFilterInternal` chạy cho các dispatch sau. Với bất kỳ filter async nào, còn phải kiểm tra hỗ trợ async của filter/Servlet registration và lifecycle tài nguyên.

### Error dispatch khác MVC exception handling

Có hai tình huống:

1. Handler ném exception, `HandlerExceptionResolver` của Spring MVC xử lý được (ví dụ `@ExceptionHandler` trả `ResponseEntity`). Response có thể được viết ngay trong lần dispatch MVC hiện tại; container không nhất thiết tạo `ERROR` dispatch.
2. Exception không được xử lý tại MVC, hoặc container nhận yêu cầu xử lý lỗi (ví dụ `sendError`). Container có thể thực hiện `ERROR` dispatch sang error mapping, như `/error` của Spring Boot. Đây có thể là một lượt dispatch mới, có mapping filter khác.

Không suy ra rằng mỗi HTTP 4xx/5xx đều đi qua `DispatcherType.ERROR`. Bên cạnh đó, cách container xử lý nested error dispatch có thể khác nhau; hãy kiểm tra thực tế khi xây filter logging/security.

### Async request

Controller MVC có thể trả `Callable<T>`, `DeferredResult<T>` hoặc dùng cơ chế async tương ứng. Initial thread có thể thoát khỏi filter chain trong lúc response **chưa hoàn thành**. Một async dispatch khác có thể diễn ra trên thread khác và tái vào container/DispatcherServlet.

Hệ quả:
- `finally` của initial filter invocation không luôn đánh dấu "response đã gửi xong" trong trường hợp async.
- Giá trị trong `ThreadLocal` không tự truyền sang thread khác; phải quản lý propagation/cleanup theo contract tương ứng.
- Log "trước/sau filter" có thể phản ánh một dispatch thay vì toàn bộ thời gian end-to-end của request.
- Nếu cần đo completion thực của async request, dùng async lifecycle listener hoặc cơ chế quan sát tương thích, thay vì chỉ lấy thời gian giữa hai dòng log của filter đầu tiên.

## 9. Cấu hình thứ tự và thời điểm filter chạy

Khi có nhiều filter, thứ tự là một phần hành vi: encoding, request wrapping, security, caching, metrics hoặc logging có thể cần chạy trước/sau những filter khác. Trong Spring Boot, có thể đặt thứ tự thông qua `FilterRegistrationBean.setOrder(...)` hoặc cơ chế order được hỗ trợ cho filter bean; việc ưu tiên nào phù hợp tùy chức năng.

Mẫu:

```java
registration.addUrlPatterns("/api/*");
registration.setDispatcherTypes(DispatcherType.REQUEST);
registration.setOrder(100);
```

Đây là cấu hình **một** registration cụ thể. Không có đảm bảo rằng filter của bạn là filter đầu tiên trong toàn bộ container chỉ nhờ số `100`; bộ lọc framework và container có thể có registration khác. Để xem filter registrations của Spring Boot, bật logging liên quan web khi cần kiểm tra môi trường:

```properties
logging.level.web=debug
```

Sau khi chẩn đoán, có thể tắt debug để giảm log. Giải thích log dựa trên cấu hình đang chạy và URL pattern, không suy luận rằng số `@Order` trên phương thức `@Bean` sẽ luôn quyết định thứ tự filter; với Spring Boot, khi cần điều khiển order rõ ràng của filter nên đặt qua `FilterRegistrationBean`.

### Đăng ký bằng Servlet API thuần

Không dùng Spring Boot, container có thể đăng ký filter trong `web.xml` hoặc qua Servlet API:

```java
// Mảnh code minh họa ở ServletContext startup, không phải chương trình độc lập.
FilterRegistration.Dynamic registration =
        servletContext.addFilter("traceFilter", new TraceFilter());

registration.addMappingForUrlPatterns(
        java.util.EnumSet.of(DispatcherType.REQUEST),
        false,
        "/api/*");
```

Ở đây `servletContext` có sẵn trong Servlet initialization callback; mã này **không** nằm trong controller và không tự chạy khi paste vào một class tùy ý. Trong Spring Boot embedded container, cơ chế `ServletContextInitializer`/registration bean là điểm tích hợp phù hợp; không giả định Boot sẽ tự chạy mọi `WebApplicationInitializer` của Servlet container như khi deploy một WAR thuần.

## 10. Các nhánh luồng xử lý quan trọng

### A. Request đi tiếp thành công

```text
REQUEST → Filter.doFilter
        → chain.doFilter
        → DispatcherServlet
        → HandlerMapping / HandlerAdapter
        → Controller
        → Converter / View
        → quay ra Filter.finally
        → response
```

Filter nhìn thấy status sau downstream processing nhưng có thể không phải trạng thái kết thúc nếu async/error dispatch diễn ra sau đó.

### B. Filter chủ động từ chối

```text
REQUEST → Authentication/RateLimit Filter
        → kiểm tra thất bại
        → response.setStatus(401/429)
        → ghi response hợp lệ
        → không gọi chain.doFilter
        → kết thúc dispatch
```

Không có điều gì bắt buộc Spring MVC controller phải được gọi trong nhánh này. Đừng đặt logic cleanup chỉ trong controller nếu tài nguyên được cấp ở filter.

### C. Không tìm thấy MVC handler

```text
REQUEST → Filter(s) → DispatcherServlet
                     → HandlerMapping: không tìm thấy
                     → HTTP 404 hoặc nhánh exception tùy cấu hình
        ← Filter(s) unwind
```

Filter vẫn có thể ghi log dù controller không tồn tại. Nếu filter được map riêng `/api/*`, 404 ở `/outside` có thể hoàn toàn không chạy filter đó.

### D. Exception hoặc lỗi HTTP

```text
REQUEST → Filter(s) → DispatcherServlet
                      → Controller throws
                      → HandlerExceptionResolver?
                           |-- handled: response được tạo trong MVC
                           +-- unresolved / sendError:
                                    container có thể ERROR dispatch
```

Filter ở initial request không đương nhiên nhận callback cho error dispatch; có thể cần một filter registration map `ERROR` riêng với logic thích hợp.

### E. Servlet async

```text
REQUEST dispatch → filter → Servlet/Controller bắt đầu async
                  → initial call stack trả về
...
ASYNC dispatch   → một filter chain phù hợp dispatcher types
                  → DispatcherServlet tiếp tục kết quả
                  → hoàn thành response
```

Hình vẽ nhằm giải thích rằng một logical request có thể bao gồm nhiều dispatch; ứng dụng cần test đúng kiểu request, container và filter mapping.

## 11. Lỗi phổ biến và cách xử lý

| Hiện tượng | Nguyên nhân có thể | Kiểm tra / cách sửa |
|---|---|---|
| Không thấy log của `TraceFilter` với `/api/hello` | Filter chưa đăng ký, bean configuration ngoài component scan, URL pattern không khớp, request không vào ứng dụng đang chạy. | Kiểm tra package của `@Configuration`, log registration, cổng/context path và dispatcher type. |
| Log filter nhưng không có log controller | Filter phía trước chặn; Spring Security trả 401/403; handler mapping không tìm thấy; interceptor dừng trước handler. | Kiểm tra HTTP status, order, matcher, logs, path và security config. |
| Một request có nhiều log filter | Filter đăng ký trùng, nhiều dispatch, Servlet forward/error/async, logging từ nhiều filter. | Tìm tất cả registrations, dispatcher types và class/name; kiểm tra logic `OncePerRequestFilter`. |
| `@Order` trên phương thức `@Bean` không đổi order mong đợi | Thứ tự registration của filter cần cấu hình cụ thể; order trên bean method không bảo đảm như dự định. | Dùng `FilterRegistrationBean.setOrder(...)` và kiểm tra registration thực tế. |
| Body tới controller rỗng sau filter | Filter đã đọc input stream trước handler mà không có cơ chế buffering/wrapper đúng. | Tránh consume request body tùy tiện; dùng wrapper và lifecycle phù hợp khi thật sự cần. |
| `after` in ra status 200 dù request cuối trả 500 | Exception/async/error dispatch có thể tạo status cuối sau khi filter hiện tại thoát. | Phân biệt status quan sát tại thời điểm log với response kết thúc. |
| Filter không chạy trong async/error | Registration không map dispatcher type hoặc `OncePerRequestFilter` mặc định bỏ qua. | Kiểm tra cả `setDispatcherTypes` lẫn `shouldNotFilterAsyncDispatch`/`shouldNotFilterErrorDispatch`. |
| `ClassNotFoundException: javax.servlet...` | Dùng API Servlet cũ `javax.servlet` với Spring Framework 6.x yêu cầu `jakarta.servlet`. | Đồng bộ framework/container và imports Jakarta. |
| Có controller nhưng HTTP 404 | Sai Servlet mapping/context path, sai controller route, package scan, application chưa chạy. | Kiểm tra `DispatcherServlet` registration và Spring mapping của handler. |
| Spring Security authentication không chạy với một path | Filter/security matcher hoặc dispatcher types không bao phủ path mong muốn. | Rà soát `SecurityFilterChain` matcher, filter registration và loại dispatch; test đường HTTP thật. |

### Cảnh báo khi triển khai filter dùng cho security và audit

- **Không** log nguyên Authorization/Cookie, password, token, dữ liệu cá nhân hoặc body nhạy cảm.
- **Không** tin thông số `X-Forwarded-*` từ client trực tiếp nếu reverse proxy chưa thiết lập chính sách trust.
- **Không** tự decode URI/path nhiều lần trong filter để quyết định quyền truy cập; proxy, container và MVC có thể giải thích khác nhau.
- **Không** giả định quy tắc bảo mật controller sẽ chạy nếu filter trả response trước.
- Khi dùng `ThreadLocal`/MDC, đặt và xóa theo lifecycle/dispatch phù hợp trong `try/finally`; chú ý thread khác khi async.
- Để kiểm tra authentication/authorization, thường nên dùng extension points của Spring Security thay vì viết filter kiểm tra header thủ công cho production.

## 12. Bài thực hành

1. Chạy ứng dụng `servlet-pipeline-demo`. Gửi `GET /api/hello?name=Lan` rồi đối chiếu ba dòng log filter/controller/filter và body JSON.
2. Thử `GET /api/missing`, giải thích vì sao filter có thể chạy nhưng controller không chạy.
3. Thay `registration.addUrlPatterns("/api/*")` thành `"/debug/*"`, chạy lại và giải thích vì sao log không xuất hiện với `/api/hello` dù controller vẫn trả JSON.
4. Thêm một filter thứ hai trả HTTP `403` với một đường dẫn do bạn chỉ định, bảo đảm không gọi `chain.doFilter` trong nhánh chặn. Quan sát controller không được gọi và giải thích luồng unwind.
5. Tạo controller trả `Callable<String>` hoặc `DeferredResult<String>` để khảo sát async. So sánh lần `REQUEST` với `ASYNC` trên container thật trước/sau khi bạn thay đổi dispatcher mapping, và tránh kết luận dựa trên số lần callback trong MockMvc.

### Câu hỏi tự kiểm tra

**Câu 1. `DispatcherServlet` thuộc container hay Spring MVC?**  
Nó là Servlet **do Spring MVC cung cấp**, nhưng phải được **đăng ký với Servlet container**. Container gọi phương thức Servlet, còn DispatcherServlet điều phối tiếp các handler Spring MVC.

**Câu 2. Vì sao filter có thể chạy với endpoint trả 404?**  
Filter được container chọn theo URL pattern/dispatcher type trước khi DispatcherServlet tìm handler. Filter không cần biết route Spring có tồn tại.

**Câu 3. Sau `chain.doFilter`, status 200 có chắc HTTP client nhận 200 không?**  
Không luôn chắc. Có thể có error dispatch, exception chưa xử lý hoặc async lifecycle tiếp theo. Phải kiểm tra status cuối ở đúng điểm quan sát.

**Câu 4. `OncePerRequestFilter` có ngăn mọi trường hợp chạy lại?**  
Không. Nó quản lý thực thi theo request dispatch/thread và logic tránh gọi trùng; async/error dispatch cùng registration quyết định khi nào code nội bộ được gọi.

**Câu 5. Spring Security filters có phải MVC interceptors không?**  
Không. `FilterChainProxy` của Spring Security làm việc qua Servlet Filter và nằm trước phần xử lý controller MVC thông thường.

## Tài liệu tham khảo chính thức

- Spring Framework — `DispatcherServlet`: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html
- Spring Framework — Processing sequence: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/sequence.html
- Spring Framework — Special bean types (`HandlerMapping`, `HandlerAdapter`, …): https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/special-bean-types.html
- Spring Framework — Exception handling: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/exceptionhandlers.html
- Spring Framework — `OncePerRequestFilter` API: https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html
- Spring Boot — Servlet web applications, registration of filters: https://docs.spring.io/spring-boot/reference/web/servlet.html
- Spring Boot — `FilterRegistrationBean` API (3.5): https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/web/servlet/FilterRegistrationBean.html
- Jakarta Servlet — Filter and DispatcherType contracts: https://jakarta.ee/specifications/servlet/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-request-lifecycle-002">HandlerMapping: tìm thành phần xử lý phù hợp với yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# HandlerMapping: tìm thành phần xử lý phù hợp với yêu cầu trong Spring MVC

## 1. Mục tiêu và vị trí trong luồng MVC

Một HTTP request đi vào `DispatcherServlet` chưa tự biết controller method nào phải chạy. **`HandlerMapping`** là chiến lược giúp Spring MVC tìm **handler phù hợp với request hiện tại**, đồng thời cung cấp chuỗi interceptor liên quan. Mỗi loại `HandlerMapping` hiểu một kiểu khai báo route khác nhau: annotation, URL mapping thủ công, functional endpoint hoặc tài nguyên tĩnh.

Sau bài học, bạn có thể:

1. Giải thích đúng trách nhiệm của `HandlerMapping` trong `DispatcherServlet` và phân biệt với `HandlerAdapter`.
2. Hiểu `HandlerExecutionChain`, `HandlerMethod` và mối liên hệ với `HandlerInterceptor`.
3. Đọc và dự đoán endpoint nào được chọn từ `@RequestMapping`, `@GetMapping`, `@PostMapping` và các điều kiện path, HTTP method, params, headers, consumes, produces.
4. Hiểu quy tắc chọn mapping cụ thể hơn, thứ tự giữa các `HandlerMapping` và tình huống trùng/không tìm được handler.
5. Chạy ví dụ Spring MVC Java 17+, quan sát kết quả bằng HTTP request, kiểm tra route được đăng ký và gỡ lỗi 404/405/406/415.

**Phạm vi:** Spring MVC chạy trên Servlet stack (`spring-webmvc`), minh họa hành vi phổ biến của Spring Framework 6.x / Spring Boot 3.x. Trong Spring WebFlux có các lớp và pipeline dispatch reactive tương ứng nhưng không được dùng thay thế lẫn nhau.

## 2. `HandlerMapping` làm gì trong `DispatcherServlet`?

Hãy theo dõi một request:

```text
Client: GET /api/books/42
               |
          Servlet container
               |
         DispatcherServlet
               |
      [Lần lượt hỏi HandlerMapping]
               |
      HandlerExecutionChain
       + selected handler
       + 0..n interceptors
               |
        HandlerAdapter phù hợp
               |
      gọi controller method
               |
      xử lý giá trị trả về,
      ghi HTTP body / render view
               |
          HTTP response
```

`HandlerMapping` thực hiện việc **tra cứu**, không tự gọi business method. Tại runtime, `DispatcherServlet` duyệt các `HandlerMapping` đã được cấu hình theo thứ tự ưu tiên. Với mỗi mapping, nó gọi:

```java
HandlerExecutionChain getHandler(HttpServletRequest request)
        throws Exception;
```

Đây là **chữ ký khái niệm từ interface `org.springframework.web.servlet.HandlerMapping`**, không phải phương thức bạn thường tự gọi trong controller. Kết quả có hai dạng chính:

- Một `HandlerExecutionChain` chứa handler tìm thấy và các interceptor gắn với request.
- `null` nếu mapping cụ thể **không** nhận request đó, để dispatcher hỏi mapping tiếp theo.

Một handler có thể là `HandlerMethod` đại diện cho một phương thức của `@Controller`, hoặc là kiểu khác như `HttpRequestHandler`, `Controller` kiểu cũ, `HandlerFunction` của MVC functional endpoints. Vì handler có nhiều kiểu, `DispatcherServlet` chuyển kết quả cho **`HandlerAdapter` tương thích** để gọi handler.

**Phân biệt cốt lõi:**

| Thành phần | Câu hỏi mà nó giải quyết | Ví dụ |
| --- | --- | --- |
| `DispatcherServlet` | Ai điều phối toàn bộ HTTP request trong Spring MVC? | Front controller |
| `HandlerMapping` | Request này thuộc handler nào? | `RequestMappingHandlerMapping` |
| `HandlerExecutionChain` | Handler đã chọn và interceptor nào đi kèm? | `HandlerMethod` + một `HandlerInterceptor` |
| `HandlerAdapter` | Làm sao gọi đúng loại handler, resolve tham số và xử lý kết quả? | `RequestMappingHandlerAdapter` |
| `HttpMessageConverter` | Đọc/ghi request hoặc response body theo media type thế nào? | Chuyển Java object thành JSON |
| `ViewResolver` | Nếu kết quả là view name, tìm view để render thế nào? | Resolve template HTML |

Vì thế, lỗi “không tìm thấy handler” xảy ra ở giai đoạn trước khi business method được gọi; lỗi binding, JSON converter hay business exception có thể xảy ra ở các giai đoạn khác.

## 3. Các `HandlerMapping` phổ biến

### 3.1. `RequestMappingHandlerMapping`: mapping theo annotation

Đây là mapping quan trọng nhất với các REST controller dựa trên annotation. Khi application context khởi tạo, Spring phát hiện các handler method từ controller bean và đăng ký metadata về mapping; trong quá trình xử lý request, nó so sánh request với các điều kiện đã đăng ký.

Ví dụ một controller:

```java
package example.routes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping("/latest")
    public String latest() {
        return "latest book";
    }

    @GetMapping("/{id}")
    public String byId(@PathVariable String id) {
        return "book:" + id;
    }
}
```

`@RequestMapping("/api/books")` ở mức class kết hợp với `@GetMapping` ở mức method:

- `GET /api/books/latest` → `BookController.latest()`.
- `GET /api/books/42` → `BookController.byId("42")`.

Khi `GET /api/books/latest` đi vào, cả pattern literal `/latest` và biến `/{id}` có thể tương thích với đoạn path đó, nhưng **pattern literal cụ thể hơn được ưu tiên**, nên handler `latest()` được chọn. Đây không phải vì phương thức `latest()` được viết trước trong source file.

Annotation rút gọn `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping` là các dạng chuyên biệt của `@RequestMapping` theo HTTP method.

### 3.2. `BeanNameUrlHandlerMapping` và `SimpleUrlHandlerMapping`

Các chiến lược này thường gặp trong code Spring MVC truyền thống hoặc infrastructure:

- `BeanNameUrlHandlerMapping` có thể gắn URL với một bean mang tên hoặc alias dạng URL; ngày nay ít được dùng trực tiếp trong REST code annotation.
- `SimpleUrlHandlerMapping` ánh xạ URL pattern đến handler được cấu hình rõ ràng. Spring MVC có thể dùng các mapping theo kiểu này cho một số infrastructure, ví dụ phục vụ static resources hoặc forward tới default servlet tùy cấu hình.

Một snippet cấu hình `SimpleUrlHandlerMapping` dưới đây nhằm giúp nhận diện API, **không phải một ứng dụng chạy độc lập**:

```java
SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
mapping.setUrlMap(java.util.Map.of("/health-legacy",
        (org.springframework.web.HttpRequestHandler) (request, response) -> {
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("ok");
        }));
mapping.setOrder(1);
```

Đoạn này cần các import/dependency Spring MVC, Servlet và phải được Spring đăng ký thành bean thích hợp. Để phản hồi `HttpRequestHandler`, `DispatcherServlet` sẽ cần `HttpRequestHandlerAdapter`. Không nên tự thay thế toàn bộ cơ chế `RequestMappingHandlerMapping` chỉ để định nghĩa REST endpoint đơn giản.

### 3.3. `RouterFunctionMapping`: Spring MVC functional endpoints

Spring MVC còn có WebMvc.fn, cho phép khai báo route bằng hàm. `RouterFunctionMapping` tìm `RouterFunction` có predicate khớp rồi chọn `HandlerFunction` tương ứng; `HandlerFunctionAdapter` sẽ thực thi handler.

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RouterFunctions.route;

@Configuration
public class FunctionalRouteConfig {
    @Bean
    RouterFunction<ServerResponse> greetingRoute() {
        return route()
                .GET("/functional/hello",
                        request -> ServerResponse.ok().body("hello fn"))
                .build();
    }
}
```

Đây là **class cấu hình bổ sung**, cần application MVC đã có. Functional routes trên **Servlet stack** không biến server thành WebFlux. Phân biệt hai cách chọn:

- Với annotated controllers, `RequestMappingHandlerMapping` chọn mapping phù hợp nhất theo các điều kiện và phép so sánh tương ứng.
- Trong **một `RouterFunction` đã tổ hợp**, các functional route được đánh giá theo thứ tự định nghĩa; nên đặt điều kiện cụ thể trước điều kiện rộng.

## 4. Các tiêu chí Spring MVC dùng để so khớp request

`@RequestMapping` và các annotation chuyên biệt có thể mô tả nhiều điều kiện. Một method chỉ được chọn khi **tập điều kiện cần thiết** đều phù hợp, không chỉ vì URL giống nhau.

| Điều kiện | Ví dụ | Kiểm tra gì? |
| --- | --- | --- |
| Path pattern | `@GetMapping("/{id}")` | Đường dẫn tương ứng trong ứng dụng |
| HTTP method | `@GetMapping`, `@PostMapping` | GET hay POST... |
| Query params | `params = "view=compact"` | Query parameter và giá trị |
| Headers | `headers = "X-Client=mobile"` | HTTP header theo điều kiện |
| Consumes | `consumes = "application/json"` | Media type request body, thường từ `Content-Type` |
| Produces | `produces = "application/json"` | Media type response có thể tạo, thương lượng với `Accept` |
| Custom request condition | HandlerMapping mở rộng | Điều kiện do ứng dụng tự bổ sung khi thực sự cần |

**Quan trọng:** `Content-Type` và `Accept` có ý nghĩa khác nhau:

- `Content-Type: application/json` mô tả định dạng **body client gửi lên**. Nó chủ yếu tương tác với `consumes`.
- `Accept: application/json` thể hiện định dạng **client muốn nhận**. Nó chủ yếu tương tác với `produces`.

Ví dụ:

```java
@PostMapping(
    path = "/orders",
    consumes = "application/json",
    produces = "text/plain"
)
public String create(@RequestBody CreateOrderRequest input) {
    return "created:" + input.id();
}
```

Đây là **fragment** nằm trong một `@RestController` với record `CreateOrderRequest(String id)` và cấu hình message converter thích hợp. Điều kiện mapping cho phép yêu cầu có JSON request body và client chấp nhận text response. Nếu `Content-Type` không phù hợp có thể nhận 415; nếu response media type không đáp ứng `Accept` có thể nhận 406.

### 4.1. Query parameter condition

Xét:

```java
@GetMapping(path = "/reports", params = "mode=compact")
public String compact() {
    return "compact";
}

@GetMapping(path = "/reports", params = "!mode")
public String normal() {
    return "normal";
}
```

Đây là **hai method trong cùng controller**. Với `GET /reports?mode=compact`, method thứ nhất phù hợp. Với `GET /reports` (không có query param `mode`), method thứ hai phù hợp. Với `GET /reports?mode=full`, cả hai điều kiện trên đều không khớp; trong cấu hình này Spring có thể trả lỗi điều kiện tham số (thường HTTP 400), **không nên tự kết luận mọi lần không chọn được method đều là 404**.

Các cách biểu diễn khác:

- `params = "debug"`: phải có tham số `debug`.
- `params = "!debug"`: không được có tham số `debug`.
- `params = "mode=compact"`: phải có tham số và giá trị khớp.
- `params = "mode!=compact"`: điều kiện phủ định, theo ngữ nghĩa request condition của Spring.

### 4.2. Điều kiện header và content negotiation

`headers = "X-Tenant=alpha"` so header; `headers = "X-Tenant"` yêu cầu header hiện diện. Không nên dùng `headers` tùy tiện thay cho `consumes`/`produces` khi muốn mô tả media type, vì hai thuộc tính chuyên biệt phản ánh rõ request body format và response content negotiation.

Ví dụ cùng một URL có các biểu diễn kết quả khác nhau có thể đăng ký bằng `produces`, miễn không tạo mapping thực tế mơ hồ. Client nên gửi `Accept` cụ thể khi quan tâm định dạng phản hồi.

## 5. Matching path: literal, biến, wildcard và độ cụ thể

Spring MVC 6.x mặc định sử dụng cơ chế `PathPatternParser` để parse và so khớp URL pattern. Cơ chế `AntPathMatcher` cũ vẫn có trong những ngữ cảnh cấu hình phù hợp, nhưng **không nên suy đoán quy tắc cũ tự động áp dụng** cho Spring 6.x.

Những cú pháp path thường gặp:

| Pattern | Ý nghĩa | Ví dụ khớp |
| --- | --- | --- |
| `/users/new` | Đường dẫn literal | `/users/new` |
| `/users/{id}` | Một path segment và capture `id` | `/users/42` |
| `/assets/*.png` | Wildcard trong một segment | `/assets/icon.png` |
| `/orders/{id:[0-9]+}` | Biến có ràng buộc regex | `/orders/123` |
| `/assets/**` | Không hoặc nhiều segment ở **cuối** pattern | `/assets/css/site.css` |
| `/files/{*path}` | Capture phần path còn lại tại **cuối** pattern | `/files/a/b.txt` |

Với `PathPatternParser`, `**` và `{*path}` chỉ được đặt ở cuối pattern. Pattern kiểu `/a/**/b` không hợp lệ theo các quy tắc của parser đó. Dạng regex `{id:[0-9]+}` giúp tránh việc endpoint dành cho số ID vô tình nhận chuỗi tùy ý; việc **chuyển từ String sang long** diễn ra về sau trong argument resolution/conversion.

Ví dụ đường đi:

```text
Đã đăng ký:
  GET /api/products/latest  -> latest()
  GET /api/products/{id}    -> getById(id)
  GET /api/products/**      -> fallback(...)

Nhận GET /api/products/latest
  -> literal /latest thắng pattern biến và wildcard.

Nhận GET /api/products/123
  -> /{id} cụ thể hơn /**.

Nhận GET /api/products/123/reviews
  -> pattern /** có thể khớp nếu fallback được cấu hình.
```

Trong so sánh path, pattern cụ thể hơn thường được ưu tiên dựa trên wildcard, số biến URI và độ dài. Tuy nhiên, đây **không phải** quy tắc “method nào ở trên file Java thì chạy trước”; phép so sánh `RequestMappingInfo` còn xét các request conditions khác.

### 5.1. URL bên ngoài, context path và servlet path

Một request có thể đi qua nhiều tiền tố:

```text
http://localhost:8080/catalog/api/products/42
                      |       |
                 context path  controller mapping

Nếu DispatcherServlet mapping "/":
  context path: /catalog
  mapping trong controller: /api/products/{id}
  captured id: 42
```

Nếu `DispatcherServlet` được map bằng prefix khác hoặc ứng dụng đổi base path qua server configuration, cần phân tích path mà MVC nhìn thấy trong cấu hình đó. Không đưa context path vào `@RequestMapping` theo thói quen, vì nó thuộc URL triển khai bên ngoài.

### 5.2. Path variable và query param không giống nhau

`/books/42` có thể capture `id=42` qua `@PathVariable`. `/books?id=42` có query parameter `id=42`, được lấy qua `@RequestParam`. Nếu controller chỉ đăng ký `@GetMapping("/books/{id}")`, Spring không tự thay query param cho path variable.

## 6. `HandlerExecutionChain` và interceptor

Sau khi tìm thấy handler, `HandlerMapping` thường trả một `HandlerExecutionChain`. Chain giữ handler và danh sách `HandlerInterceptor` áp dụng cho request. Interceptor có ba callback phổ biến:

- `preHandle(request, response, handler)`: chạy trước handler; trả `true` để tiếp tục hoặc `false` để dừng phần thực thi còn lại.
- `postHandle(request, response, handler, modelAndView)`: chạy sau khi handler thực thi trong các trường hợp phù hợp.
- `afterCompletion(request, response, handler, exception)`: callback khi hoàn tất xử lý request cho các interceptor đã tham gia theo ngữ nghĩa tương ứng.

Luồng đồng bộ đơn giản:

```text
HandlerMapping.getHandler(request)
       |
HandlerExecutionChain
       |
interceptor A: preHandle
interceptor B: preHandle
       |
HandlerAdapter: gọi handler
       |
interceptor B: postHandle
interceptor A: postHandle
       |
rendering (nếu có view)
       |
interceptor B: afterCompletion
interceptor A: afterCompletion
```

Nếu `preHandle` trả `false`, handler không chạy và các callback còn lại tuân theo điều kiện chain đã thực thi. Với async request, callback không hoàn toàn giống sơ đồ đồng bộ; cần dùng `AsyncHandlerInterceptor` nếu cần hành vi async chuyên biệt.

Đặc biệt với `@ResponseBody` và `ResponseEntity`, response body có thể đã được ghi trong `HandlerAdapter` trước `postHandle`. Vì thế, đừng thiết kế interceptor với giả định luôn còn cơ hội thay đổi response body trong `postHandle`; cân nhắc `ResponseBodyAdvice` cho kiểu yêu cầu đó. Đối với security, Spring khuyến nghị cơ chế bảo vệ tích hợp Servlet filter chain/Spring Security thay vì coi interceptor là lớp bảo mật duy nhất.

### 6.1. Ví dụ quan sát handler đã chọn

Một interceptor thực hành có thể in tên handler method được chọn. Class Java sau **chạy trong dự án Spring Boot MVC ở mục 8**, package giống application:

```java
package example.mapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MappingTraceConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SelectedHandlerInterceptor());
    }

    static class SelectedHandlerInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Object handler) {
            if (handler instanceof HandlerMethod method) {
                System.out.println("Selected "
                        + method.getBeanType().getSimpleName()
                        + "#" + method.getMethod().getName());
            }
            return true;
        }
    }
}
```

Ví dụ handler được chọn là `ItemsController.latest()`, dòng console ứng với request đó sẽ có dạng:

```text
Selected ItemsController#latest
```

Vì class được đăng ký qua `WebMvcConfigurer`, interceptor nhận các request do MVC mapping xử lý trong phạm vi cấu hình hiện tại. Handler không nhất thiết luôn là `HandlerMethod`, do đó kiểm tra `instanceof` trước khi ép kiểu.

## 7. Nhiều `HandlerMapping` và thứ tự ưu tiên

`DispatcherServlet` có thể nhận nhiều bean `HandlerMapping`. Chúng được sắp xếp theo thứ tự; Spring thử chúng đến khi một mapping cung cấp chain phù hợp. Trong ngữ nghĩa `Ordered`, số `order` **càng nhỏ thì mức ưu tiên càng cao**.

Ví dụ kiến trúc:

```text
DispatcherServlet
  |
  +-- RequestMappingHandlerMapping  -> controllers annotation
  |
  +-- RouterFunctionMapping         -> MVC.fn routes
  |
  +-- các mapping cho resources     -> static assets
  |
  +-- default servlet forwarding    -> fallback nếu bật
```

**Sơ đồ phân loại, không cố định số `order`**: thứ tự và các bean thực tế phụ thuộc framework version, cấu hình MVC, Spring Boot và customization. Trong một `HandlerMapping`, việc chọn route là bài toán **độ cụ thể của request mapping**; giữa các `HandlerMapping` khác nhau, dispatcher dựa trên **thứ tự mapping bean**. Đừng trộn hai khái niệm.

Một fallback rất rộng như `/**` có thể che khuất endpoint hoặc static resource nếu cấu hình sai ưu tiên. Ngược lại, static resource mapping có thể được kiểm tra sau annotation mapping để controller được ưu tiên khi đúng cấu hình. Không tự đặt `order` quá cao cho custom handler mapping trước khi hiểu tất cả mapping hiện hữu.

## 8. Ví dụ Spring MVC hoàn chỉnh với Java 17+

Ví dụ dùng **Spring Boot 3.4.0**, Java 17 và Spring MVC. Đây là dự án độc lập để thấy cách `RequestMappingHandlerMapping` chọn method qua HTTP request. Chỉ cần Spring Web starter; Spring Boot cung cấp sẵn embedded Servlet container và `DispatcherServlet`.

### 8.1. Cấu trúc

```text
mapping-demo/
├── pom.xml
└── src/
    ├── main/java/example/mapping/
    │   ├── MappingDemoApplication.java
    │   ├── ItemsController.java
    │   └── MappingTraceConfiguration.java
    └── test/java/example/mapping/
        └── ItemsMappingTest.java
```

`MappingTraceConfiguration` là class ở mục 6.1. Nếu không muốn in log handler, có thể không tạo file đó; hành vi chọn route của controller vẫn giữ nguyên.

### 8.2. `pom.xml`

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
        <version>3.4.0</version>
        <relativePath/>
    </parent>

    <groupId>example</groupId>
    <artifactId>mapping-demo</artifactId>
    <version>1.0.0</version>
    <name>mapping-demo</name>

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

Boot quản lý version các dependency Spring phù hợp với Boot 3.4.0. Dùng Maven chạy từ thư mục chứa `pom.xml`. Không tự thêm `@EnableWebMvc` vào demo vì Boot đã tự cấu hình MVC thích hợp.

### 8.3. Điểm khởi chạy

File `src/main/java/example/mapping/MappingDemoApplication.java`:

```java
package example.mapping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MappingDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(MappingDemoApplication.class, args);
    }
}
```

### 8.4. Các route và điều kiện rõ ràng

File `src/main/java/example/mapping/ItemsController.java`:

```java
package example.mapping;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ItemsController {

    @GetMapping(path = "/latest", produces = MediaType.TEXT_PLAIN_VALUE)
    public String latest() {
        return "item:latest";
    }

    @GetMapping(path = "/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String byId(@PathVariable String id) {
        return "item:" + id;
    }

    @GetMapping(params = "!view", produces = MediaType.TEXT_PLAIN_VALUE)
    public String listDefault() {
        return "list:default";
    }

    @GetMapping(params = "view=compact",
                produces = MediaType.TEXT_PLAIN_VALUE)
    public String listCompact() {
        return "list:compact";
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.TEXT_PLAIN_VALUE)
    public String create(@RequestBody NewItem input) {
        return "created:" + input.name();
    }

    public record NewItem(String name) {
    }
}
```

Giải thích:

- `@RequestMapping("/api/items")` trên class là prefix chung.
- `latest()` và `byId(...)` có cùng HTTP method và cùng tiền tố; literal `/latest` được ưu tiên khi khớp.
- `listDefault()` cần **không có** tham số `view`, `listCompact()` cần `view=compact`; chúng không tạo xung đột khi khớp request được thiết kế.
- `create(...)` chỉ xử lý **POST** với `Content-Type: application/json`; body được parse thành record `NewItem` nếu JSON hợp lệ. Nó trả text và phản hồi thành công mặc định là HTTP 200 vì demo không dùng `@ResponseStatus`.
- `@RequestBody` và Jackson chạy sau bước chọn mapping khi `HandlerAdapter` thực thi method; `HandlerMapping` không tự đọc thành record `NewItem`.

### 8.5. Chạy và kiểm chứng bằng HTTP request

Yêu cầu: JDK 17+, Maven, cổng 8080 đang rảnh. Từ thư mục `mapping-demo`:

```bash
mvn test
mvn spring-boot:run
```

Lệnh chạy app sẽ giữ terminal mở; mở terminal khác để gọi:

```bash
curl -s http://localhost:8080/api/items/latest
curl -s http://localhost:8080/api/items/42
curl -s http://localhost:8080/api/items
curl -s "http://localhost:8080/api/items?view=compact"
curl -s -X POST http://localhost:8080/api/items \
  -H "Content-Type: application/json" \
  -H "Accept: text/plain" \
  -d '{"name":"Lan"}'
```

Tương ứng từng command trên, **body** dự kiến:

```text
item:latest
item:42
list:default
list:compact
created:Lan
```

Đây là năm kết quả của **năm request khác nhau**, mỗi response body không nhất thiết có newline ở cuối. Nếu cùng tạo file `MappingTraceConfiguration.java`, stdout server còn có các dòng `Selected ItemsController#...`, giúp quan sát method được chọn.

Thử gửi request có method hoặc media type không tương thích:

```bash
curl -i -X PUT http://localhost:8080/api/items/42

curl -i -X POST http://localhost:8080/api/items \
  -H "Content-Type: text/plain" \
  -d 'Lan'

curl -i http://localhost:8080/api/items/42 \
  -H "Accept: application/xml"
```

Trong demo và cấu hình mặc định:

- `PUT /api/items/42` → thường HTTP **405 Method Not Allowed** (có pattern phù hợp nhưng HTTP method không được hỗ trợ).
- POST có `Content-Type: text/plain` → thường HTTP **415 Unsupported Media Type** do `consumes` không khớp.
- GET cần XML khi handler chỉ `produces=text/plain` → thường HTTP **406 Not Acceptable**.

Body lỗi có thể khác nhau giữa các phiên bản/cấu hình Spring Boot; **mã trạng thái** mới là trọng tâm ở các tình huống này. Các error status là kết quả của quá trình tìm kiếm mapping và xử lý điều kiện, không chứng minh rằng controller business method đã chạy.

### 8.6. Bài test `MockMvc` xác nhận mapping

File `src/test/java/example/mapping/ItemsMappingTest.java`:

```java
package example.mapping;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ItemsMappingTest {

    @Autowired
    MockMvc mvc;

    @Test
    void picksLiteralPathBeforeVariable() throws Exception {
        mvc.perform(get("/api/items/latest"))
                .andExpect(status().isOk())
                .andExpect(content().string("item:latest"));

        mvc.perform(get("/api/items/42"))
                .andExpect(status().isOk())
                .andExpect(content().string("item:42"));
    }

    @Test
    void matchesQueryConditions() throws Exception {
        mvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(content().string("list:default"));

        mvc.perform(get("/api/items").param("view", "compact"))
                .andExpect(status().isOk())
                .andExpect(content().string("list:compact"));
    }

    @Test
    void honorsHttpMethodAndMediaType() throws Exception {
        mvc.perform(post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .content("{\"name\":\"Lan\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("created:Lan"));
    }
}
```

`MockMvc` mô phỏng request qua Spring MVC infrastructure trong test context, **không cần mở network port thật**. Các assertion buộc ứng dụng phải chọn đúng handler, xử lý request và tạo body dự kiến. Chạy riêng `mvn test`; dự kiến Maven báo test pass, nhưng tổng số test/runtime log tùy plugin và môi trường.

## 9. Quan sát danh sách handler mappings ở startup

Bạn có thể inspect mapping đã đăng ký, thay vì đoán từ annotation. Trong ứng dụng demo ở mục 8, thêm class tùy chọn:

```java
package example.mapping;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Component
public class MappingInspector implements ApplicationRunner {
    private final RequestMappingHandlerMapping mapping;

    public MappingInspector(RequestMappingHandlerMapping mapping) {
        this.mapping = mapping;
    }

    @Override
    public void run(ApplicationArguments args) {
        mapping.getHandlerMethods().forEach((info, method) ->
                System.out.println(info + " -> "
                        + method.getBeanType().getSimpleName()
                        + "#" + method.getMethod().getName()));
    }
}
```

Lúc khởi động, code liệt kê metadata `RequestMappingInfo` và `HandlerMethod` đang có trong `RequestMappingHandlerMapping`. Cú pháp `toString()` của `RequestMappingInfo` có thể khác nhau theo Spring version, thứ tự traversal cũng không phải hợp đồng API; **không so sánh nguyên văn stdout hay thứ tự các dòng**. Điều đáng quan sát là mỗi URL/method/điều kiện từ `ItemsController` hiện diện đúng trong registry.

Nếu muốn debug bằng logger:

```properties
logging.level.org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping=TRACE
logging.level.org.springframework.web.servlet.DispatcherServlet=DEBUG
```

Đây là **nội dung mẫu của `src/main/resources/application.properties`**, không phải file Java. Mức TRACE/DEBUG có thể làm log nhiều; dùng tạm cho môi trường học tập và trả log về mức phù hợp khi triển khai. Đọc startup logs, mappings và request logs cùng nhau sẽ cho bằng chứng mạnh hơn chỉ nhìn HTTP status.

## 10. Không tìm thấy hoặc không chọn được handler: phân biệt lỗi

Không nên gộp mọi vấn đề về mapping thành “404”:

| Hiện tượng | Ý nghĩa cần điều tra | Hướng kiểm tra |
| --- | --- | --- |
| 404 Not Found | Không có handler thích hợp cho path hoặc request bị xử lý/fallback theo cấu hình khác | Context path, servlet mapping, controller scan, URL pattern |
| 405 Method Not Allowed | Path có mapping nhưng HTTP method không phù hợp | `@GetMapping` / `@PostMapping`, client method |
| 415 Unsupported Media Type | Request body media type không phù hợp điều kiện `consumes` hoặc converter | `Content-Type`, `consumes`, message converters |
| 406 Not Acceptable | Không đáp ứng định dạng mà client có thể nhận | `Accept`, `produces`, converters |
| 400 Bad Request | Có thể do `params` conditions hoặc request parameter/body không hợp lệ | Query params, type conversion, JSON/binding |
| 500 hoặc lỗi khởi động | Mapping xung đột, cấu hình handler/bean không hợp lệ, lỗi thực thi | Root cause trong startup/runtime logs |

Các mã là **khả năng điển hình** trong Spring MVC; fallback handlers, exception resolvers, security filters hoặc custom error handlers có thể đổi biểu hiện. Thấy một status chưa đủ để suy ra duy nhất nguyên nhân.

### 10.1. Controller không được đăng ký

`@RestController` không đảm bảo class đó được quét. Với Boot, `@SpringBootApplication` bao gồm component scanning từ package tương ứng. Nếu `ItemsController` nằm ngoài package scan, `RequestMappingHandlerMapping` không có mapping nào của nó. Giải pháp là tổ chức package hợp lý hoặc cấu hình scan cụ thể, tránh quét bừa toàn classpath.

### 10.2. Trùng mapping hoặc ambiguous handler methods

Ví dụ hai method trong cùng context cùng khai báo `@GetMapping("/orders/{id}")` với các điều kiện khác không phân biệt được:

```java
@GetMapping("/orders/{id}")
public String first(@PathVariable String id) {
    return "first:" + id;
}

@GetMapping("/orders/{id}")
public String second(@PathVariable String id) {
    return "second:" + id;
}
```

Đây là **fragment cố tình sai**: trong cùng một controller, Spring thường phát hiện mapping trùng khi đăng ký và báo lỗi startup. Với hai pattern khác chuỗi nhưng cùng mức độ cụ thể (ví dụ `/{id}` và `/{name}`), có trường hợp request khớp cả hai và runtime báo **ambiguous handler methods**. Đổi tên Java method, đổi tên biến URI hoặc di chuyển method trong file **không phải cách khử ambiguity đáng tin**. Hãy thiết kế path, method, params, headers hoặc consumes/produces có ý nghĩa, tránh tạo hai mapping cạnh tranh.

### 10.3. Path pattern quá rộng

Một fallback `/**` có thể khớp nhiều request. Trong cùng annotation mapping, pattern cụ thể hơn thường thắng; giữa các mapping bean, thứ tự ưu tiên còn quyết định mapping nào được thử trước. Nếu trang static asset hoặc endpoint health đi sai handler, inspect danh sách `HandlerMapping`, resource configuration và thứ tự `Ordered` thực tế.

### 10.4. Lẫn `HandlerMapping` với `HandlerInterceptor`

Interceptor chỉ chạy trong execution chain sau khi mapping tương ứng chọn handler. Một request không tới dispatcher, bị Servlet filter chặn hoặc không được mapping xử lý có thể không kích hoạt interceptor bạn mong đợi. Nếu cần xử lý bảo mật và logging xuyên suốt từ trước dispatch, hãy đánh giá Servlet filter hoặc Spring Security theo trách nhiệm.

### 10.5. Nhầm routing MVC với WebFlux

Cả hai stack có `@GetMapping`, nhưng MVC dùng `DispatcherServlet`, `RequestMappingHandlerMapping` thuộc package `org.springframework.web.servlet...` và `RequestMappingHandlerAdapter` của Servlet stack. WebFlux dùng `DispatcherHandler` và các thành phần reactive thuộc `org.springframework.web.reactive...`. Dù tên lớp có thể giống nhau, chúng **không thể hoán đổi chỉ vì annotation giống nhau**.

## 11. Bài tập suy luận HandlerMapping

### Bài 1 — chọn literal hay path variable

Giả sử:

```java
@GetMapping("/customers/new")
public String newCustomer() { return "new"; }

@GetMapping("/customers/{id}")
public String customer(@PathVariable String id) { return id; }
```

Dự đoán handler cho `GET /customers/new` và `GET /customers/123`. Vì sao vị trí hai method trong file không quyết định thứ tự? **Gợi ý:** pattern literal cụ thể hơn so với một biến path segment.

### Bài 2 — phân biệt path và HTTP method

Controller chỉ có `@GetMapping("/invoices/{id}")`. Dự đoán hành vi khi client gửi `POST /invoices/10`. Vì sao đây thường là 405 thay vì 404? Đổi thành `@RequestMapping("/invoices/{id}")` không chỉ là đổi tên annotation: nếu không khai báo điều kiện method, phạm vi phương thức HTTP được nhận sẽ rộng hơn, có thể ngoài ý muốn.

### Bài 3 — phân biệt `consumes` và `produces`

Một endpoint nhận JSON và trả plain text. Viết `@PostMapping` với `consumes="application/json"` và `produces="text/plain"`. Dự đoán hậu quả khi client gửi `Content-Type:text/plain`, và khi client gửi `Accept:application/xml`. Trường hợp nào liên quan request body, trường hợp nào liên quan response media type?

### Bài 4 — phân tích chuỗi thực thi

Giải thích vì sao một request có thể đã chọn được handler nhưng vẫn lỗi trước khi body response hoàn tất. Đưa ra hai nguyên nhân xảy ra **sau HandlerMapping**, ví dụ lỗi conversion của `@PathVariable long` hoặc lỗi do service/controller ném exception.

### Bài 5 — thiết kế route tránh ambiguity

Giả sử bạn muốn `GET /reports` mặc định và `GET /reports?view=compact`. Thiết kế hai mapping có query condition rõ ràng, sau đó thử trường hợp `?view=full`. Có cần endpoint fallback nào khác không? Hãy lựa chọn dựa trên API contract mong muốn.

## 12. Tài liệu chính thức để đào sâu

- Tổng quan `DispatcherServlet` và quá trình xử lý: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/sequence.html
- Mapping annotated controller, request conditions và path pattern: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-requestmapping.html
- Servlet MVC `HandlerMapping` và special bean types: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/special-bean-types.html
- Interceptor trong Servlet MVC: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/handlermapping-interceptor.html
- Functional endpoints trên Servlet stack: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-functional.html
- API `RequestMappingHandlerMapping`: https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/RequestMappingHandlerMapping.html

**Ghi nhớ:** `HandlerMapping` quyết định **handler nào** và các interceptor liên quan; `HandlerAdapter` quyết định **cách thực thi handler đó**. Với controller annotation, việc chọn handler phụ thuộc **toàn bộ điều kiện request**, không chỉ path; Spring dùng các quy tắc độ cụ thể và ưu tiên rõ ràng để tránh quyết định theo thứ tự code.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-request-lifecycle-003">HandlerAdapter: gọi thành phần xử lý và hỗ trợ chữ ký phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# HandlerAdapter: gọi thành phần xử lý và hỗ trợ chữ ký phương thức trong Spring MVC

## 1. HandlerAdapter giải quyết vấn đề gì?

Khi một HTTP request đến ứng dụng **Spring Web MVC**, `DispatcherServlet` là điểm điều phối trung tâm. Nó tìm ra *handler* phù hợp với request rồi cần gọi handler ấy. Tuy nhiên, handler có thể được biểu diễn theo nhiều kiểu: một phương thức controller có `@GetMapping`, một đối tượng triển khai `HttpRequestHandler`, hoặc kiểu controller cũ triển khai `Controller`.

`DispatcherServlet` không nên tự viết riêng logic gọi cho từng kiểu handler. Spring tách hai trách nhiệm:

- **`HandlerMapping`:** quyết định *request này khớp với handler nào?*; thường trả một `HandlerExecutionChain` chứa handler và các interceptor liên quan.
- **`HandlerAdapter`:** quyết định *có gọi được handler này không và gọi như thế nào?*; nếu hỗ trợ, nó thực hiện lời gọi với ngữ cảnh HTTP phù hợp, rồi trả kết quả cho bước xử lý tiếp theo.

Với annotated controller hiện đại, implementation thường gặp nhất là **`RequestMappingHandlerAdapter`**. Nó hỗ trợ nhiều chữ ký phương thức controller: tham số từ URI, query, header, body, model, session, Servlet API; chuyển đổi kiểu, binding, validation; và xử lý giá trị trả về thành view, response body hoặc phản hồi bất đồng bộ.

**Ý chính:** `HandlerAdapter` là lớp cầu nối giữa handler do mapping chọn và cơ chế thực thi handler. Nó không có nhiệm vụ tự tìm URL mapping hay thực hiện nghiệp vụ thay controller.

## 2. Vị trí trong vòng đời một request MVC

Ví dụ:

~~~http
GET /api/orders/42?details=true HTTP/1.1
Host: localhost:8080
Accept: application/json
~~~

Luồng cơ bản:

~~~text
HTTP client
    |
    v
Servlet container
    |
    v
Filter chain
    |
    v
DispatcherServlet
    |
    | (1) hỏi các HandlerMapping
    v
RequestMappingHandlerMapping
    |
    | trả HandlerExecutionChain:
    |   HandlerMethod + các HandlerInterceptor
    v
DispatcherServlet
    |
    | (2) chọn HandlerAdapter có supports(handler) == true
    v
RequestMappingHandlerAdapter
    |
    | (3) giải tham số:
    |     @PathVariable id       <- "42" -> long
    |     @RequestParam details   <- "true" -> boolean
    |     HttpServletRequest...  <- ngữ cảnh request
    | (4) gọi phương thức controller
    | (5) xử lý giá trị trả về:
    |     record/Object -> JSON body, hoặc ModelAndView -> view
    v
DispatcherServlet
    |
    | hoàn tất interceptor, exception handling/rendering nếu cần
    v
HTTP response
~~~

Đây là sơ đồ *luồng logic*; nó lược bỏ một số nhánh như multipart, async dispatch, lỗi và tối ưu HTTP cache. Với `@ResponseBody` hoặc `@RestController`, adapter cùng return-value handler thường ghi response body trực tiếp qua converter; không cần tìm view HTML.

Một cách nhìn rõ ràng hơn:

| Thành phần | Câu hỏi nó trả lời | Ví dụ |
| --- | --- | --- |
| `Filter` (Servlet) | Request/response cần xử lý gì trước khi vào MVC? | Security filter, logging |
| `DispatcherServlet` | Ai điều phối pipeline của request? | `doDispatch(...)` |
| `HandlerMapping` | Handler nào phù hợp URI, HTTP method, headers...? | `RequestMappingHandlerMapping` |
| `HandlerInterceptor` | Có cần xử lý trước/sau handler? | `preHandle`, `postHandle` |
| `HandlerAdapter` | Gọi handler đã chọn bằng cách nào? | `RequestMappingHandlerAdapter` |
| `HandlerMethodArgumentResolver` | Tạo từng đối số method từ đâu? | `@PathVariable`, `@RequestParam` |
| `HandlerMethodReturnValueHandler` | Kết quả method sẽ đi đâu? | JSON response, view/model |
| `HttpMessageConverter` | Chuyển body HTTP ↔ Java object như thế nào? | JSON converter dùng Jackson |
| `HandlerExceptionResolver` | Nếu phát sinh lỗi, chuyển thành xử lý/phản hồi nào? | `@ExceptionHandler`, resolver mặc định |
| `ViewResolver` | Tên view tương ứng view nào? | Thymeleaf template |

**Không nhầm `HandlerMapping` và `HandlerAdapter`:** “tìm phương thức controller” và “chuẩn bị đối số rồi gọi phương thức” là hai công việc khác nhau. `HandlerAdapter` nhận handler đã được chọn, chứ không tự chọn endpoint bằng URL.

## 3. Hợp đồng HandlerAdapter

Trong Spring MVC, contract chính nằm tại `org.springframework.web.servlet.HandlerAdapter`. Dạng rút gọn để hiểu ý nghĩa:

~~~java
public interface HandlerAdapter {
    boolean supports(Object handler);

    ModelAndView handle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception;

    long getLastModified(HttpServletRequest request, Object handler);
}
~~~

Đoạn trên minh họa hình dạng contract trên nhánh Spring Framework 6.x; nó không phải một file cần chép vào ứng dụng. Cần các import Jakarta Servlet và `ModelAndView` tương ứng.

- **`supports(handler)`:** báo adapter có hỗ trợ kiểu handler mà mapping trả ra hay không. `DispatcherServlet` có thể có nhiều adapter, và chọn adapter thích hợp.
- **`handle(request, response, handler)`:** thực hiện việc xử lý handler. Với annotated method, quá trình bao gồm chuẩn bị argument, invoke method, rồi xử lý return value.
- **`getLastModified(...)`:** một điểm mở rộng liên quan HTTP cache/Last-Modified của handler; không phải API dùng để chọn mapping. Với annotated `@RequestMapping`, Spring thường sử dụng các cơ chế conditional request chuyên biệt như `WebRequest.checkNotModified(...)`.

Kiểu trả về `ModelAndView` của `handle` có thể **null** khi response đã được xử lý trực tiếp (ví dụ controller trả JSON qua `@ResponseBody`). Điều này **không có nghĩa request thất bại**.

Không nên tự triển khai `HandlerAdapter` cho một `@RestController` bình thường. Spring MVC đã cấu hình những adapter tiêu chuẩn. Tùy biến chữ ký phương thức thông thường chỉ cần thêm **argument resolver** hoặc thành phần hỗ trợ khác.

## 4. Những HandlerAdapter thường gặp

| Handler do HandlerMapping trả về | Adapter hỗ trợ | Mục đích |
| --- | --- | --- |
| `HandlerMethod` của phương thức `@RequestMapping` / `@GetMapping` ... | `RequestMappingHandlerAdapter` | Luồng controller annotation hiện đại |
| Đối tượng thực thi `HttpRequestHandler` | `HttpRequestHandlerAdapter` | Gọi contract `handleRequest(request, response)` |
| Đối tượng thực thi `org.springframework.web.servlet.mvc.Controller` kiểu cũ | `SimpleControllerHandlerAdapter` | Gọi contract `handleRequest(...)` legacy |

`RequestMappingHandlerMapping` thường ánh xạ tới một **`HandlerMethod`**, là đối tượng mô tả bean/controller cùng Java method đã được tìm thấy. Nó khác với việc “đưa nguyên controller class vào adapter”.

Ví dụ cùng ứng dụng có thể có controller REST annotated và handler chuyên dụng cho resource. `DispatcherServlet` sử dụng abstraction adapter để hai kiểu được gọi thống nhất. Tuy nhiên, khi học Spring MVC mới, trọng tâm là `RequestMappingHandlerAdapter`; hai adapter còn lại giúp hiểu vì sao interface này tồn tại.

### Chọn adapter có phải là chọn controller?

Không. Việc chọn endpoint dựa vào mapping đã xảy ra trước đó. Nếu có:

~~~java
@GetMapping("/items/{id}")
public Item getItem(@PathVariable long id) { ... }
~~~

thì `RequestMappingHandlerMapping` đối chiếu path và method rồi trả `HandlerMethod` trỏ tới `getItem`. `RequestMappingHandlerAdapter` nhận `HandlerMethod` ấy, suy ra cách cung cấp `id` và gọi method. Nó **không chạy thuật toán tìm URL lần nữa**.

### Một adapter có thể phục vụ nhiều chữ ký method

Những phương thức sau đều có thể dùng cùng `RequestMappingHandlerAdapter`:

~~~java
@GetMapping("/hello")
String hello(@RequestParam(defaultValue = "Guest") String name) { ... }

@PostMapping("/orders")
ResponseEntity<Order> create(@Valid @RequestBody NewOrder request) { ... }

@GetMapping("/report")
ModelAndView report(Model model) { ... }
~~~

Các dấu `...` là phần thân minh họa nên **không thể biên dịch riêng**. Sự đa dạng tham số/return type được xử lý bằng các danh sách resolver/handler chuyên trách, chứ không phải mỗi chữ ký method cần một implementation `HandlerAdapter` riêng.

## 5. Bên trong RequestMappingHandlerAdapter

Một nhánh điển hình cho annotated method:

~~~text
HandlerMethod
    |
    v
RequestMappingHandlerAdapter
    |
    v
ServletInvocableHandlerMethod / cơ chế invocation
    |
    +-- Argument resolvers (theo từng tham số)
    |     @PathVariable -> URI template variable
    |     @RequestParam  -> query/form parameter
    |     @RequestHeader -> HTTP header
    |     @RequestBody   -> HttpMessageConverter -> Java object
    |     @ModelAttribute -> binder/converter/validator
    |     Servlet request/model/session -> ngữ cảnh framework
    |
    +-- Controller method được gọi với argument đã resolve
    |
    +-- Return-value handlers (theo kiểu trả về/annotation)
          @ResponseBody -> HttpMessageConverter -> HTTP body
          ResponseEntity -> status + header + body
          String view name -> ModelAndView/view rendering
          Callable / DeferredResult -> async processing tương ứng
~~~

Tên class nội bộ hữu ích khi đọc stack trace, nhưng **không phải API mà ứng dụng phải tự gọi**. Spring quản lý chúng cho hầu hết annotated controllers.

### 5.1 Argument resolver lựa chọn theo gì?

Một `HandlerMethodArgumentResolver` có hai thao tác chính:

~~~java
boolean supportsParameter(MethodParameter parameter);

Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory) throws Exception;
~~~

Đây là trích lược signature để đọc hiểu; không phải file interface hoàn chỉnh.

`supportsParameter` hỏi resolver có biết tạo giá trị cho một tham số cụ thể không, dựa vào annotation, kiểu Java và metadata. `resolveArgument` lấy dữ liệu từ HTTP request và/hoặc binder để tạo object truyền vào phương thức controller.

Ví dụ cụ thể:

| Chữ ký controller | Nguồn và bước giải đối số |
| --- | --- |
| `@PathVariable long id` | Lấy biến path rồi chuyển String → long |
| `@RequestParam int page` | Lấy query/form parameter, convert sang int |
| `@RequestHeader("X-Trace-ID") String traceId` | Đọc HTTP header |
| `@RequestBody OrderRequest input` | Chọn message converter theo content type và target type; parse body thành object |
| `@Valid @RequestBody OrderRequest input` | Sau deserialization, chạy validation khi cấu hình provider tương ứng |
| `Model model` | Cấp model phục vụ view hoặc xử lý request |
| `HttpServletRequest request` | Truy cập native Servlet request |

**Quá trình chọn resolver diễn ra cho từng tham số.** Một method có thể đồng thời nhận `@PathVariable`, `@RequestParam`, `@RequestBody` và những dependency context khác.

Với kiểu parameter đơn giản **không có annotation**, MVC có thể dùng fallback xem như request parameter; kiểu phức tạp có thể xem như model attribute. Vì mục tiêu dễ bảo trì, nên dùng annotation tường minh khi nguồn dữ liệu có ý nghĩa.

### 5.2 Binding, conversion và validation nằm ở đâu?

Giả sử request:

~~~http
POST /api/orders HTTP/1.1
Content-Type: application/json

{"item":"Sổ tay","quantity":2}
~~~

Controller khai báo:

~~~java
public ResponseEntity<OrderResponse> create(
        @Valid @RequestBody CreateOrderRequest input) { ... }
~~~

Trình tự khái quát:

1. Resolver nhận ra tham số có `@RequestBody`.
2. Dựa vào `Content-Type: application/json` và target Java type, một `HttpMessageConverter` phù hợp (thường Jackson JSON converter) chuyển bytes thành `CreateOrderRequest`.
3. Có `@Valid` và Bean Validation provider phù hợp thì dữ liệu được kiểm tra ràng buộc.
4. Nếu không có lỗi, method controller nhận object đã có dữ liệu.
5. Nếu deserialize/convert/validate lỗi, framework có thể phát exception trước khi body method chạy; pipeline exception resolver quyết định response.

**Không nhầm vai trò:** `HandlerAdapter` điều phối; argument resolver hiểu nguồn tham số; `HttpMessageConverter` đọc/ghi HTTP body; `WebDataBinder` và hệ thống convert/validate xử lý binding/validation theo từng trường hợp.

`@Valid` cần dependency/provider Jakarta Bean Validation trong project (với Spring Boot thường thêm `spring-boot-starter-validation`). Nếu quên, không được giả định ràng buộc `@NotBlank`/`@Min` đã có hiệu lực.

### 5.3 Return value được diễn giải như thế nào?

| Controller trả về | Xử lý thường gặp |
| --- | --- |
| `OrderResponse` bên trong `@RestController` | Serialize object thành response body (thường JSON) |
| `ResponseEntity<OrderResponse>` | Ghi status/header/body theo response entity |
| `String` từ method của `@Controller` không có `@ResponseBody` | Thường là logical view name |
| `String` từ `@RestController` | Text response body, không phải view name |
| `ModelAndView` | Model và view cho bước rendering |
| `void` / một số giá trị rỗng | Tùy annotation, response state và contract method |
| `Callable<T>`, `DeferredResult<T>` | Tích hợp async Servlet, có dispatch xử lý kết quả ở thời điểm sau |

Các method return-value handler chọn cách diễn giải **theo annotation và type**, vì vậy chỉ xem kiểu Java `String` chưa đủ để suy ra server trả tên view hay chuỗi raw body.

Một số class giúp tìm hiểu internals: `HandlerMethodReturnValueHandler`, `RequestResponseBodyMethodProcessor` và `HttpEntityMethodProcessor`.

## 6. Ví dụ chạy được: nhiều chữ ký method và custom argument resolver

Ví dụ độc lập dưới đây sử dụng **Java 17**, **Spring Boot 3.3.5** (Spring Framework 6.1.x), web stack **Spring MVC**, Jackson và Bean Validation.

Các endpoint:

- `GET /api/orders/{id}?verbose=true`: `@PathVariable`, `@RequestParam` và một tham số tùy biến `@CurrentRequestId` lấy từ header.
- `POST /api/orders`: `@Valid @RequestBody` tạo object, trả `ResponseEntity` với status `201`, header `Location` và JSON body.
- Tham số custom minh họa điểm mở rộng tự nhiên của `RequestMappingHandlerAdapter`: **`HandlerMethodArgumentResolver`**.

Bài demo giữ dữ liệu cố định, không có database, để quan sát riêng cơ chế MVC.

### 6.1 Gradle configuration

Tạo một project Spring Boot riêng, ví dụ `handler-adapter-demo`.

Tệp `settings.gradle`:

~~~groovy
rootProject.name = 'handler-adapter-demo'
~~~

Tệp `build.gradle`:

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
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
~~~

Cần JDK 17+ và Gradle tương thích với Spring Boot plugin. Nếu dự án có Gradle Wrapper, dùng wrapper để chạy.

### 6.2 Java source hoàn chỉnh

Tạo tệp `src/main/java/example/web/HandlerAdapterDemoApplication.java`:

~~~java
package example.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class HandlerAdapterDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(HandlerAdapterDemoApplication.class, args);
    }

    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CurrentRequestId {
    }

    public record OrderInfo(long id, String requestId, boolean verbose) {
    }

    public record CreateOrderRequest(
            @NotBlank String item,
            @Min(1) int quantity) {
    }

    public record CreatedOrder(long id, String item, int quantity) {
    }

    @RestController
    @RequestMapping("/api/orders")
    public static class OrderController {

        @GetMapping("/{id}")
        public OrderInfo get(
                @PathVariable long id,
                @RequestParam(defaultValue = "false") boolean verbose,
                @CurrentRequestId String requestId) {

            return new OrderInfo(id, requestId, verbose);
        }

        @PostMapping
        public ResponseEntity<CreatedOrder> create(
                @Valid @RequestBody CreateOrderRequest input) {

            CreatedOrder created = new CreatedOrder(
                    101L, input.item(), input.quantity());

            return ResponseEntity
                    .created(URI.create("/api/orders/101"))
                    .body(created);
        }
    }

    @Configuration
    public static class MvcConfiguration implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(
                List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new CurrentRequestIdResolver());
        }
    }

    public static class CurrentRequestIdResolver
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentRequestId.class)
                    && parameter.getParameterType() == String.class;
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {

            String id = webRequest.getHeader("X-Request-ID");
            if (id == null || id.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Missing X-Request-ID");
            }
            return id;
        }
    }
}
~~~

**Điểm đáng quan sát:** controller không có code tự parse header trong method `get`. `RequestMappingHandlerAdapter` sử dụng danh sách argument resolver, trong đó có resolver ta đăng ký thông qua `WebMvcConfigurer#addArgumentResolvers`. Khi thấy `@CurrentRequestId String`, resolver đọc header rồi cung cấp tham số thứ ba.

Custom resolver này hoạt động **trong Spring MVC annotated controller**. Nó không phải một Servlet filter, không thay đổi URL mapping và không bắt buộc viết một `HandlerAdapter` mới.

### 6.3 Chạy ứng dụng

Trong thư mục project, với Gradle Wrapper:

~~~bash
./gradlew bootRun
~~~

Trên Windows PowerShell:

~~~powershell
.\gradlew.bat bootRun
~~~

Nếu không có wrapper thì cần Gradle tương thích đã cài; lệnh tương đương là `gradle bootRun`. Mặc định server lắng nghe `http://localhost:8080` nếu cổng chưa được ứng dụng khác sử dụng.

**Thử GET:**

~~~bash
curl -i -H "X-Request-ID: abc-123" \
  "http://localhost:8080/api/orders/42?verbose=true"
~~~

Trên Windows PowerShell, có thể viết một dòng với `curl.exe` để tránh alias và dấu nối dòng shell:

~~~powershell
curl.exe -i -H "X-Request-ID: abc-123" "http://localhost:8080/api/orders/42?verbose=true"
~~~

Kết quả body kỳ vọng khi request thành công:

~~~json
{"id":42,"requestId":"abc-123","verbose":true}
~~~

Status là `200 OK` và body là JSON. Các bước mà adapter phối hợp:

1. `@PathVariable` tạo `long id = 42L`.
2. `@RequestParam` tạo `boolean verbose = true`.
3. Custom `CurrentRequestIdResolver` tạo `String requestId = "abc-123"`.
4. Method `OrderController#get` được gọi bằng ba argument này.
5. Vì `@RestController`, return value handler ghi JSON của `OrderInfo`.

**Thử thiếu header:**

~~~bash
curl -i "http://localhost:8080/api/orders/42"
~~~

Custom resolver ném `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)`, nên status kỳ vọng là `400 Bad Request`. Body lỗi cụ thể phụ thuộc cách Spring Boot cấu hình error response; không cần đoán một JSON cố định.

**Thử POST hợp lệ:**

~~~bash
curl -i -X POST "http://localhost:8080/api/orders" \
  -H "Content-Type: application/json" \
  -d '{"item":"Notebook","quantity":2}'
~~~

Status kỳ vọng: `201 Created`, header `Location: /api/orders/101`, body:

~~~json
{"id":101,"item":"Notebook","quantity":2}
~~~

Đây là dữ liệu demo hard-code; không có thao tác ghi database thật. `@RequestBody` được deserialize rồi `@Valid` kiểm tra record theo annotation.

**Thử POST không hợp lệ:**

~~~bash
curl -i -X POST "http://localhost:8080/api/orders" \
  -H "Content-Type: application/json" \
  -d '{"item":"","quantity":0}'
~~~

`@NotBlank` và `@Min(1)` đều bị vi phạm. Khi starter validation được cấu hình như trên, request phải bị từ chối với status `400 Bad Request` trước khi vào body method `create`. Không suy đoán thứ tự hay format các thông điệp lỗi: chúng có thể thay đổi theo cấu hình error handling.

### 6.4 Vì sao không tự gọi controller bằng Java reflection?

Framework sử dụng reflection/method invocation nội bộ nhưng còn thực hiện nhiều thứ trước/sau lời gọi, như tạo argument, type conversion, validation, async state, ghi status/header/body và exception routing. Nếu tự gọi:

~~~java
// Minh họa ý tưởng, KHÔNG phải cách xử lý HTTP trong ứng dụng:
controller.get(42L, true, "abc-123");
~~~

thì chỉ chạy trực tiếp method Java với các đối số có sẵn; nó **không** tự đọc `@PathVariable`, `@RequestParam` hay header từ `HttpServletRequest`. Đó chính là giá trị của pipeline mà `RequestMappingHandlerAdapter` điều phối.

## 7. Lỗi thường gặp khi chữ ký controller không được giải được

Không phải mọi kiểu parameter đều được giải như lập trình viên dự đoán. Bảng dưới đây giúp phân biệt lỗi mapping, argument resolution, body conversion và validation.

| Triệu chứng | Nguyên nhân có thể | Nơi điều tra đầu tiên |
| --- | --- | --- |
| `404 Not Found` | Request không khớp handler mapping; hoặc static resource xử lý khác | URI, HTTP method, path pattern, mapping registration |
| `405 Method Not Allowed` | Path có mapping nhưng HTTP method không phù hợp | `@GetMapping`/`@PostMapping` và client method |
| `400` do không đọc được query/path value | Thiếu tham số bắt buộc hoặc type conversion thất bại | `@RequestParam`, `@PathVariable` và converter |
| `400` do JSON sai/thiếu body | Jackson parse/conversion lỗi hoặc missing required body | `@RequestBody` và `HttpMessageConverter` |
| `400` do validation | Constraint trên bean/request không đạt | `@Valid`, Bean Validation provider, error handling |
| `415 Unsupported Media Type` | Body có `Content-Type` không được endpoint/converter hỗ trợ | `consumes`, content type, converter |
| `406 Not Acceptable` | Không tạo được response phù hợp `Accept` | `produces`, converter, response negotiation |
| Trả về tên view thay vì text | Method thuộc `@Controller` và không có `@ResponseBody` | Return-value handler/view resolver |
| Không thấy custom resolver chạy | Annotation/type không được `supportsParameter` nhận; sai web stack hoặc cấu hình | `WebMvcConfigurer` và resolver registration |
| Exception trước khi vào method | Adapter không resolve được một argument | Log exception, argument resolver, binder/converter |

**Cần phân biệt:** `HandlerMapping` chọn phương thức trước; lỗi trong bước **lựa chọn handler** khác với lỗi khi **adapter đang chuẩn bị argument hoặc xử lý return value**. Status cuối cùng còn có thể bị ảnh hưởng bởi `HandlerExceptionResolver` hay `@ControllerAdvice`.

### Trường hợp BindingResult

Trong MVC, nếu muốn thu thập lỗi binding/validation ở controller thay vì để exception đi theo đường xử lý mặc định, `BindingResult` / `Errors` phải đặt **ngay sau** argument liên quan:

~~~java
@PostMapping("/forms")
String submit(@Valid @ModelAttribute OrderForm form,
              BindingResult result) {
    if (result.hasErrors()) {
        return "order-form";
    }
    return "order-success";
}
~~~

Đoạn này chỉ minh họa cấu trúc method; `OrderForm` và hai view template chưa được định nghĩa nên không phải chương trình chạy độc lập.

## 8. Tùy biến đúng điểm mở rộng

Khi muốn hỗ trợ chữ ký method mới, hãy chọn điểm mở rộng phù hợp:

- **Thêm loại argument riêng:** triển khai `HandlerMethodArgumentResolver` rồi đăng ký bằng `WebMvcConfigurer.addArgumentResolvers(...)`. Ví dụ `@CurrentTenant`, `@CurrentRequestId`, giá trị đọc từ security context đã xác minh.
- **Thêm chuyển đổi giữa kiểu dữ liệu:** dùng `Converter`, `Formatter` hoặc `ConversionService` khi muốn biến chuỗi/tham số thành kiểu ứng dụng.
- **Thêm quy tắc binding/validation:** sử dụng `@InitBinder`, Bean Validation và các cấu hình binder phù hợp.
- **Xử lý request/response body đặc biệt:** cấu hình hoặc bổ sung `HttpMessageConverter` khi media type và serialization yêu cầu.
- **Tùy biến lỗi controller:** `@ExceptionHandler`, `@RestControllerAdvice` hoặc exception resolver phù hợp.
- **Thêm logic trước/sau handler:** `HandlerInterceptor` nếu thuộc MVC, `Filter` nếu cần tác động ở Servlet layer.
- **Hỗ trợ một kiểu handler hoàn toàn khác:** mới cân nhắc tự viết `HandlerAdapter`/`HandlerMapping` riêng; đây là nhu cầu hạ tầng rất hiếm trong ứng dụng thông thường.

**Một ranh giới quan trọng:** thêm `HandlerMethodArgumentResolver` có thể chưa đủ nếu một built-in resolver được chọn trước cho đúng kiểu/annotation. Cần chọn annotation/kiểu riêng đủ đặc trưng và test thứ tự resolution. Không tùy tiện thay thế toàn bộ danh sách argument resolver/return-value handler vì có thể làm mất những hỗ trợ mặc định của framework.

## 9. Async return value có còn đi qua HandlerAdapter không?

**Có.** Spring MVC sử dụng Servlet async cho một số kiểu return value như `Callable<T>` hoặc `DeferredResult<T>`. Adapter xử lý kết quả ban đầu, tích hợp quản lý async; request có thể được dispatch lại khi dữ liệu sẵn sàng. Cần phân biệt:

- Method controller thực hiện trên thread nào và trả về sớm hay không.
- Async processing có giải phóng thread Servlet ban đầu trong lúc chờ hay không.
- Công việc bên trong `Callable` có thể vẫn blocking trên worker thread.
- Response chưa nhất thiết hoàn thành tại thời điểm method controller lần đầu trả về.

Ví dụ ký hiệu:

~~~java
@GetMapping("/slow")
Callable<String> slow() {
    return () -> service.loadResult();
}
~~~

Đây là đoạn minh họa phụ thuộc `service` chưa khai báo. Nó diễn đạt việc Spring MVC có thể xử lý async return type; không chứng minh `service.loadResult()` là non-blocking.

**Không nhầm với Spring WebFlux.** WebFlux sử dụng một mô hình reactive với pipeline các publisher; dù cũng có annotated controller, internal request handling của WebFlux không dùng chính Servlet MVC `RequestMappingHandlerAdapter` nói ở bài này. Tên khái niệm giống nhau không có nghĩa các class và execution model giống hệt.

## 10. Quan hệ với Interceptor, Filter và ExceptionResolver

Một request MVC có thể có nhiều điểm mở rộng, nhưng mỗi điểm ở vị trí khác nhau:

~~~text
Servlet Filter (bao ngoài)
    |
    v
DispatcherServlet
    |
    +-- HandlerMapping tìm handler + interceptor chain
    |
    +-- interceptor.preHandle(...)
    |
    +-- HandlerAdapter.handle(...)
    |      +-- argument resolution
    |      +-- controller invocation
    |      +-- return-value handling
    |
    +-- interceptor.postHandle(...) (nếu áp dụng)
    |
    +-- render View (nếu có)
    |
    +-- interceptor.afterCompletion(...) (khi vòng xử lý hoàn tất)
    |
    v
Filter chain hoàn tất
~~~

Nếu xảy ra exception, `HandlerExceptionResolver` có thể can thiệp và xử lý lỗi; với async flow, callbacks và các lần dispatch cũng khác luồng đồng bộ rút gọn ở trên. `postHandle` có thể không thay đổi được body JSON đã được ghi bởi response-body handler.

Một ví dụ ứng dụng: cần **request ID** trong controller thì ưu tiên `@RequestHeader` hoặc custom argument resolver (như phần 6); cần **bảo mật toàn hệ thống** nên dùng security filter chain tương ứng; cần **kiểm tra điều kiện của handler** có thể dùng interceptor; cần **lỗi API nhất quán** có thể dùng `@RestControllerAdvice`.

Không đưa logic xác thực phân quyền quan trọng vào custom argument resolver chỉ vì nó đọc header dễ dàng: header do client tự gửi không mặc nhiên đáng tin.

## 11. Debug HandlerAdapter bằng chứng cứ quan sát được

Khi có lỗi “mapping đã nhận nhưng method không chạy”, nên debug theo thứ tự:

1. **Xác nhận URL/method đã được mapping:** xem endpoint mappings và log/request details phù hợp. Nếu không có mapping, tập trung vào `HandlerMapping`, không phải adapter.
2. **Xác nhận handler kiểu gì:** với annotated controller, handler thường là `HandlerMethod`; adapter điển hình là `RequestMappingHandlerAdapter`.
3. **Xác nhận từng đối số:** so sánh `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@RequestBody` với URL, headers, content type và actual payload.
4. **Kiểm tra converter/validation:** type conversion, Jackson, ràng buộc Jakarta Bean Validation, thông báo lỗi sớm.
5. **Nếu method chạy nhưng response sai:** xem `@ResponseBody`/`@RestController`, `ResponseEntity`, `Accept`/`produces`, message converter, view resolver.
6. **Nếu ném exception:** theo luồng `HandlerExceptionResolver` hoặc `@ControllerAdvice`; status cuối cùng có thể không phải status phát sinh trực tiếp từ controller.
7. **Nếu async:** phân biệt lần request đầu, việc chạy task và lần dispatch hoàn thành.

Một số tên class hữu ích khi đọc stack trace Spring MVC 6.x:

~~~text
DispatcherServlet
RequestMappingHandlerMapping
RequestMappingHandlerAdapter
ServletInvocableHandlerMethod
InvocableHandlerMethod
HandlerMethodArgumentResolverComposite
HandlerMethodReturnValueHandlerComposite
RequestResponseBodyMethodProcessor
HttpEntityMethodProcessor
HandlerExceptionResolver
~~~

Không cần thuộc hết tên class. Quan trọng là biết lỗi xảy ra tại **mapping**, **argument conversion/binding**, **invocation**, **return-value conversion** hay **exception rendering**.

### Cách kiểm thử đúng cấp độ

- **`@WebMvcTest` + `MockMvc`:** kiểm tra mapping, các built-in argument resolver, validation và response code; import cấu hình custom resolver nếu cần.
- **Integration test với server:** kiểm tra Servlet filter/security, JSON converter, validation provider và wiring như production.
- **Unit test cho custom resolver:** `supportsParameter` với đúng/sai annotation và type, header có/thiếu.
- **Negative tests:** missing header, invalid number, malformed JSON, failed validation và unacceptable media type.

Nên xác nhận trạng thái HTTP và nội dung body theo hợp đồng ứng dụng; tránh test dựa vào chuỗi error mặc định có thể thay đổi theo bản Spring Boot và cấu hình.

## 12. Những ngộ nhận cần tránh

| Ngộ nhận | Điều đúng cần nhớ |
| --- | --- |
| “DispatcherServlet trực tiếp gọi tất cả loại controller” | DispatcherServlet chọn `HandlerAdapter`; adapter biết cách gọi kiểu handler tương ứng |
| “HandlerAdapter quyết định URL nào khớp” | `HandlerMapping` xác định handler |
| “Mỗi method signature cần một HandlerAdapter riêng” | `RequestMappingHandlerAdapter` dùng argument resolvers/return-value handlers |
| “`@PathVariable` tự đọc request trong controller” | Resolver chuẩn bị đối số trước khi controller được gọi |
| “`@RequestBody` chỉ là ép kiểu bằng reflection” | Nó thường dùng `HttpMessageConverter` để deserialize HTTP body, có thể kèm validation |
| “Controller trả `String` luôn là JSON/text” | Với `@Controller` thường là view name; với `@RestController` thường là response body |
| “Response JSON phải qua ViewResolver” | `@ResponseBody` được ghi trực tiếp qua return-value handler/converter |
| “HTTP 400 từ @Valid là logic trong method” | Validation có thể thất bại trước khi method được gọi |
| “Custom resolver luôn được gọi đầu tiên” | Resolver được chọn theo support và thứ tự cấu hình |
| “Muốn thêm parameter phải tự viết HandlerAdapter” | Thường chỉ cần `HandlerMethodArgumentResolver` |
| “WebFlux cũng chạy Servlet RequestMappingHandlerAdapter này” | WebFlux có stack và adapter implementation riêng |
| “Async nghĩa là mọi I/O không còn blocking” | Async Servlet và non-blocking I/O là hai khái niệm khác nhau |

## 13. Bài tập áp dụng

**Bài 1 — Phân loại trách nhiệm.** Với request `GET /api/products/9?currency=VND` và controller:

~~~java
@GetMapping("/api/products/{id}")
ProductView read(@PathVariable long id,
                 @RequestParam String currency) { ... }
~~~

Hãy chỉ ra thành phần nào (a) xác định method `read`, (b) cung cấp `id` và `currency`, (c) serialize `ProductView` khi method nằm trong `@RestController`.

**Gợi ý:** (a) `RequestMappingHandlerMapping`, (b) các argument resolver do `RequestMappingHandlerAdapter` phối hợp, (c) return-value handler + HTTP message converter.

**Bài 2 — Chẩn đoán status 400.** Client gửi `GET /api/products/abc` nhưng method yêu cầu `@PathVariable long id`. Vì sao body method có thể không chạy?

**Gợi ý:** path khớp nhưng argument resolver không convert được `"abc"` thành `long`, dẫn tới luồng exception handling trước invocation.

**Bài 3 — Mở rộng chữ ký method.** Bạn muốn mọi endpoint nhận `@CurrentTenant Tenant tenant`. Hãy liệt kê tối thiểu phần cần thiết để dùng trong MVC.

**Gợi ý:** annotation dành riêng cho parameter, resolver chọn bằng annotation/type, lấy tenant từ ngữ cảnh đã xác thực, đăng ký resolver với `WebMvcConfigurer` và test đủ trường hợp có/thiếu tenant. Không tin giá trị tenant được gửi trực tiếp từ một header chưa xác thực.

**Bài 4 — Chọn điểm mở rộng.** Muốn log request trước khi controller chạy, muốn trả cùng format JSON lỗi và muốn convert query `sort=NEWEST` sang enum. Nên dùng cùng một HandlerAdapter custom hay ba điểm mở rộng phù hợp?

**Gợi ý:** Filter/Interceptor cho log tùy phạm vi, `@RestControllerAdvice` hoặc exception resolver cho lỗi, converter/enum conversion cho query. Không cần tự viết adapter.

## 14. Checklist khi đọc một chữ ký controller MVC

- [ ] Mapping đã xác định đúng handler và HTTP method.
- [ ] Handler phù hợp `RequestMappingHandlerAdapter` hay adapter khác.
- [ ] Từng parameter có nguồn dữ liệu rõ ràng (path/query/header/body/model/context).
- [ ] Type conversion và binding đúng, không phụ thuộc vào fallback mơ hồ.
- [ ] `@Valid` có provider tương ứng và error handling đã được kiểm thử.
- [ ] `Content-Type` của request phù hợp input converter; `Accept` phù hợp response output.
- [ ] Return type và annotation được hiểu đúng: view, body, status/header hay async.
- [ ] Custom argument resolver được đăng ký và có test cho thứ tự hỗ trợ.
- [ ] Không đặt logic nghiệp vụ hoặc authentication tùy tiện trong adapter/resolver.
- [ ] Khi lỗi, phân biệt `HandlerMapping`, `HandlerAdapter`, converter, validator và exception resolver.

**Kết luận vận dụng:** `HandlerAdapter` cho phép `DispatcherServlet` gọi nhiều kiểu handler mà không phụ thuộc trực tiếp vào cách mỗi handler nhận dữ liệu hoặc trả kết quả. Trong Spring MVC hiện đại, `RequestMappingHandlerAdapter` là trung tâm thực thi annotated controller: nó phối hợp **argument resolvers → controller invocation → return-value handlers**. Hiểu ba bước này giúp thiết kế method signature rõ ràng, thêm tham số đúng điểm mở rộng và debug nhanh nguyên nhân controller chưa chạy hoặc response bị chuyển đổi sai.

### Tài liệu tham khảo chính thức

- [Spring Framework — DispatcherServlet Processing](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/sequence.html)
- [Spring Framework — Special Bean Types](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/special-bean-types.html)
- [Spring Framework — MVC Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html)
- [Spring Framework — MVC Return Values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html)
- [Spring Framework — MVC HTTP Message Converters](https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html)
- [Spring Framework — MVC Config](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config.html)
- [Spring Framework — RequestMappingHandlerAdapter API](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/RequestMappingHandlerAdapter.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-request-lifecycle-004">Giải quyết tham số, thực thi controller và xử lý giá trị trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Giải quyết tham số, thực thi controller và xử lý giá trị trả về trong Spring MVC

Một method trong controller Spring MVC có thể khai báo `@PathVariable long id`, `@RequestParam String sort`, `@RequestBody CreateBookRequest input`, `Model model` hoặc `HttpServletRequest request`. Client không trực tiếp gọi Java method và cũng không tự tạo các đối tượng đó. **Spring MVC chọn một handler, giải quyết từng tham số theo nguồn dữ liệu và quy tắc phù hợp, thực thi method, rồi chuyển giá trị trả về thành HTTP response hoặc model/view.**

Bài học này giải thích ba chặng nối tiếp trong **Spring MVC trên Servlet stack**: *argument resolution* → *controller invocation* → *return value handling*. Đây là cơ chế cốt lõi đằng sau annotation-based controllers của Spring Framework 6.x / Spring Boot 3.x, dùng Java 17+. Spring WebFlux có pipeline và các abstraction khác; không áp nguyên các lớp `RequestMappingHandlerAdapter`, `ServletInvocableHandlerMethod` của MVC sang WebFlux.

## 1. Mục tiêu và bức tranh tổng thể

Sau bài này, bạn có thể:

- Mô tả vai trò của `DispatcherServlet`, `HandlerMapping`, `HandlerAdapter`, `RequestMappingHandlerAdapter` và `HandlerMethod`.
- Giải thích cách `HandlerMethodArgumentResolver` tạo giá trị cho từng tham số: path, query, header, cookie, request body, model hoặc context.
- Phân biệt **chuyển đổi kiểu đơn giản**, **data binding**, **JSON deserialization** và **validation**.
- Hiểu `@Controller` + view name khác `@RestController`/`@ResponseBody` như thế nào.
- Biết cách `HandlerMethodReturnValueHandler`, `HttpMessageConverter`, `ModelAndView`, `ResponseEntity` và exception resolver ảnh hưởng response.
- Đọc một controller thực tế và dự đoán status, headers, body, lỗi 400/404/406/415 và thứ tự các bước.

Sơ đồ cho một request đi qua MVC:

~~~text
HTTP request
    |
    v
Servlet container → Filter chain → DispatcherServlet
                                      |
                                      v
                               HandlerMapping
                       tìm HandlerMethod phù hợp
                                      |
                                      v
                      RequestMappingHandlerAdapter
                                      |
              +-----------------------+---------------------+
              |                                             |
              v                                             |
    HandlerMethodArgumentResolver(s)                        |
    + path/query/header/cookie                              |
    + body ↔ HttpMessageConverter                            |
    + @ModelAttribute ↔ DataBinder                           |
    + validation / BindingResult                             |
              |                                             |
              v                                             |
         Java arguments                                     |
              |                                             |
              v                                             |
     invoke controller method                               |
              |                                             |
              v                                             |
    HandlerMethodReturnValueHandler(s)                       |
    + @ResponseBody/ResponseEntity → converter → HTTP body   |
    + String/ModelAndView → model & view → ViewResolver      |
    + async wrapper → async request processing               |
              |                                             |
              +----------------------+----------------------+
                                     |
                                     v
                          HTTP status + headers + body
~~~

Sơ đồ là mô hình khái quát. Một `HandlerInterceptor`, `@ControllerAdvice`, exception resolver, security filter, async dispatch hay template engine có thể tham gia vào từng bước tùy cấu hình. Không suy luận controller luôn được gọi khi request có status lỗi: nhiều lỗi xuất hiện **trước lúc method chạy**.

## 2. Spring MVC tìm handler rồi mới giải quyết tham số

### 2.1. HandlerMapping và HandlerAdapter là hai trách nhiệm khác nhau

`DispatcherServlet` nhận request đã được Servlet container phân phối. Nó hỏi các `HandlerMapping` để lấy handler và các interceptor liên quan. Đối với annotation-based controllers, `RequestMappingHandlerMapping` thường xác định một `HandlerMethod` đại diện cho một Java method cùng bean controller tương ứng.

Ví dụ:

~~~java
@RestController
@RequestMapping("/api/books")
class BookController {
    @GetMapping("/{id}")
    public String get(@PathVariable long id) {
        return "book-" + id;
    }
}
~~~

Với `GET /api/books/42`, handler matching xét **HTTP method, URL path và các điều kiện của request mapping**. `HandlerMethod` có thông tin như method Java `get` và controller bean cần gọi. `HandlerMapping` không tự parse request body thành DTO để gọi method.

Sau khi tìm handler, `DispatcherServlet` chọn một `HandlerAdapter` có thể thực thi handler đó. Với `HandlerMethod` của annotated controllers, adapter thông thường là **`RequestMappingHandlerAdapter`**. Nhờ abstraction `HandlerAdapter`, `DispatcherServlet` không cần biết chi tiết cách gọi từng kiểu handler.

### 2.2. Trách nhiệm của RequestMappingHandlerAdapter

`RequestMappingHandlerAdapter` chịu trách nhiệm chuẩn bị môi trường gọi annotated controller, bao gồm:

1. Lấy metadata của controller method đã match.
2. Chuẩn bị `ModelAndViewContainer`, model, các `@ModelAttribute` liên quan và binding context.
3. Cung cấp danh sách argument resolvers để dựng từng Java argument.
4. Gọi method qua tầng invocable handler method.
5. Nhờ return-value handlers xử lý đối tượng controller trả về.
6. Cung cấp `ModelAndView` cho `DispatcherServlet` khi cần render view; hoặc đánh dấu response đã được xử lý.

Các lớp liên quan trong Spring MVC gồm `ServletInvocableHandlerMethod`, `InvocableHandlerMethod`, `HandlerMethodArgumentResolverComposite` và `HandlerMethodReturnValueHandlerComposite`. Chúng là **chi tiết nội bộ hữu ích để đọc source/debug**; code ứng dụng thông thường nên lập trình qua annotation và interface extension được hỗ trợ.

**Thứ tự quan trọng:** chọn controller không có nghĩa Spring đã resolve được argument. Ví dụ mapping `GET /books/{id}` có thể khớp path `/books/abc`, nhưng việc chuyển `abc` thành `long` thất bại sau đó và method sẽ không được gọi.

## 3. HandlerMethodArgumentResolver giải quyết tham số như thế nào?

Một `HandlerMethodArgumentResolver` có hai hoạt động chính:

- `supportsParameter(MethodParameter parameter)`: resolver có hiểu cách tạo giá trị cho tham số này không?
- `resolveArgument(...)`: lấy dữ liệu từ request/model/context, chuyển đổi hoặc kết hợp với công cụ khác để tạo argument.

Spring giữ **nhiều resolver** trong một composite. Mỗi parameter của handler được xét để chọn resolver tương ứng; resolver phù hợp đầu tiên theo thứ tự cấu hình sẽ xử lý. Kết quả được đặt đúng vị trí trong mảng argument trước khi Java method được gọi. Resolver có thể đọc metadata annotation, không chỉ nhìn kiểu Java.

Ví dụ chữ ký:

~~~java
@GetMapping("/{id}")
public BookResponse read(
        @PathVariable long id,
        @RequestParam(defaultValue = "vi") String lang,
        @RequestHeader("X-Client-Version") String version,
        HttpServletRequest request) {
    // ...
}
~~~

Với:

~~~http
GET /api/books/42?lang=en HTTP/1.1
Host: localhost:8080
X-Client-Version: 2

~~~

Các argument hình thành độc lập theo nguồn:

| Java argument | Resolver / cơ chế đại diện | Kết quả |
| --- | --- | --- |
| `@PathVariable long id` | URI-template variable + conversion | `42L` |
| `@RequestParam String lang` | Request parameter resolver | `"en"` |
| `@RequestHeader String version` | Header resolver | `"2"` |
| `HttpServletRequest request` | Servlet request argument resolver | Request object hiện tại |

Không phải controller tự xây `HttpServletRequest`; Servlet container tạo request và Spring truyền tham chiếu thích hợp. Không phải mọi đối số đều bắt buộc có annotation: các kiểu `Model`, `Principal`, `Locale`, `HttpServletResponse` có resolvers theo kiểu đối tượng. Tuy nhiên **đánh dấu annotation rõ ràng** giúp tránh nhầm source của dữ liệu.

### 3.1. Các nhóm tham số thường gặp

| Tham số | Nguồn | Điểm cần nhớ |
| --- | --- | --- |
| `@PathVariable` | Biến trong URI template đã match | Có thể convert `String` thành `long`, `UUID`, `Enum`, ... |
| `@RequestParam` | Request parameters: query và tùy trường hợp form/multipart | `required`, `defaultValue`, kiểu đích ảnh hưởng hành vi. |
| `@RequestHeader` | HTTP header | Tên header không phân biệt hoa/thường; convert kiểu khi cần. |
| `@CookieValue` | Request cookies | Cookie có thể thiếu; cần quyết định required/default. |
| `@RequestBody` | HTTP request content | Đọc qua `HttpMessageConverter`, ví dụ Jackson JSON. |
| `@RequestPart` | Một part trong multipart request | Converter có thể xử lý payload của part. |
| `MultipartFile` | Multipart upload đã được cấu hình parse | Không đồng nghĩa mọi request body là multipart. |
| `@ModelAttribute` | Model + request parameters / binding | Dùng `WebDataBinder`, thường cho form/command object. |
| `BindingResult`, `Errors` | Binding/validation errors của argument trước đó | Phải đặt **ngay sau** argument được validate. |
| `Model`, `ModelMap` | MVC model hiện tại | Thường dùng khi render HTML view. |
| `HttpServletRequest`, `HttpServletResponse` | Servlet API | Truy cập request/response thấp hơn. |
| `Principal` | Thông tin principal hiện hành | Kết quả tùy cơ chế security/container. |
| `@RequestAttribute`, `@SessionAttribute` | Request/session attributes | Khác `@RequestParam`; không phải query parameter. |
| `HttpEntity<T>` | Request headers + body | `T` được đọc thông qua message converter. |

`@RequestParam` không bắt buộc ở một số simple-type parameters vì Spring MVC có cơ chế fallback, nhưng code API rõ ràng nên chú thích nguồn dữ liệu. `Optional<T>`, `required=false` và `defaultValue` có thể xử lý trường hợp thiếu tham số, với khác biệt cụ thể tùy annotation.

### 3.2. Path variable và query parameter khác nhau

~~~java
@GetMapping("/books/{bookId}")
public String find(
        @PathVariable long bookId,
        @RequestParam(defaultValue = "full") String view) {
    return bookId + ":" + view;
}
~~~

Gọi `GET /books/7?view=summary` tạo `bookId=7`, `view="summary"`. Nếu thiếu `view`, Spring dùng `"full"` nhờ `defaultValue`. Nếu request là `/books/xyz`, matching có thể chọn method nhưng argument conversion `String → long` thất bại; Spring MVC thường phản hồi **400 Bad Request** thông qua exception handling mặc định.

**Query không thuộc URI path matching**: `?view=summary` được đọc như parameter, còn `{bookId}` là một path segment.

### 3.3. Type conversion khác JSON deserialization

Với `@PathVariable long id` và `@RequestParam int page`, dữ liệu đến từ những chuỗi ngắn. Spring dùng hệ thống chuyển đổi/formatting (`ConversionService`, `Converter`, `Formatter`, `WebDataBinder` tùy cơ chế) để chuyển chuỗi sang kiểu Java.

Với `@RequestBody BookRequest request`, dữ liệu có thể là **JSON nhiều trường**. Spring dùng `HttpMessageConverter` phù hợp với media type, thường là Jackson JSON converter khi có Jackson, để đọc stream bytes và deserialize JSON thành đối tượng.

| Nguồn dữ liệu | Ví dụ | Chủ yếu đi qua |
| --- | --- | --- |
| Path | `/books/42` → `long id` | Type conversion |
| Query | `?page=2` → `int page` | Type conversion |
| Header | `X-Limit: 10` → `int limit` | Header resolver + conversion |
| JSON request body | `{"title":"MVC"}` → `BookRequest` | `HttpMessageConverter` |
| Form parameters | `title=MVC&price=50` → command object | `@ModelAttribute` + `WebDataBinder` |

**Không nhầm `@RequestBody` với `@ModelAttribute`.** `@RequestBody` là đường đọc HTTP body qua converter; `@ModelAttribute` là đường tạo/tìm model object rồi bind các request parameters vào thuộc tính. Với `application/x-www-form-urlencoded`, việc truy cập Servlet request parameters có thể tiêu thụ hoặc ảnh hưởng việc đọc body thô, nên không mặc định dùng cả hai cơ chế để đọc lại cùng một form body.

## 4. @RequestBody, HttpMessageConverter và Content-Type

Ví dụ:

~~~java
@PostMapping(path = "/books", consumes = "application/json")
@ResponseBody
public BookResponse create(@RequestBody CreateBookRequest input) {
    return new BookResponse(1L, input.title());
}
~~~

Đây là **method minh họa cần đặt trong `@Controller` Spring MVC**, cùng khai báo các record DTO tương ứng. Luồng:

1. Request đi vào handler đã match `POST /books`.
2. Resolver cho `@RequestBody` phát hiện target Java type `CreateBookRequest`.
3. Spring chọn `HttpMessageConverter` có thể đọc kiểu đó với `Content-Type: application/json`, ví dụ Jackson converter.
4. Converter deserialize JSON thành instance Java.
5. Controller nhận object và tạo giá trị trả về.
6. Return-value handler của `@ResponseBody` lại chọn converter có thể viết `BookResponse` thành nội dung phản hồi; `Accept`, `produces` và converter capabilities có thể ảnh hưởng content negotiation.

Phân biệt:

- `Content-Type` **trong request** mô tả media type của request content.
- `Accept` **trong request** mô tả media type response mà client mong muốn.
- `Content-Type` **trong response** mô tả representation được server gửi ra.

Lỗi thường gặp:
- JSON sai cú pháp hoặc không thể deserialize theo converter → thường **400 Bad Request** (`HttpMessageNotReadableException`).
- Request media type không được handler/converter hỗ trợ → thường **415 Unsupported Media Type**.
- Client chỉ chấp nhận media type mà server không thể tạo → thường **406 Not Acceptable**.
- `@RequestBody(required=true)` nhưng không có body cần thiết → thường **400**.

Không bắt mọi exception converter rồi biến thành 500: 400/415/406 mang ý nghĩa HTTP riêng. Ngoài ra, không tự kết luận Jackson được cài chỉ vì có spring-web; cần thư viện converter tương ứng trong runtime classpath hoặc cấu hình của ứng dụng.

## 5. @ModelAttribute, DataBinder và validation

Trong ứng dụng render form HTML, Spring thường nhận dữ liệu dưới dạng request parameters. Với DTO model attribute, `WebDataBinder` bind dữ liệu vào object, kết hợp converter/formatter để xử lý kiểu, sau đó có thể validation theo annotation hoặc cấu hình.

Ví dụ một method đặt trong `@Controller`:

~~~java
@PostMapping("/register")
public String register(
        @Valid @ModelAttribute RegistrationForm form,
        BindingResult bindingResult,
        Model model) {
    if (bindingResult.hasErrors()) {
        return "register-form";
    }
    model.addAttribute("message", "Registered " + form.getUsername());
    return "register-result";
}
~~~

Đây là **method minh họa**: `RegistrationForm`, validator/Bean Validation provider và template view phải được định nghĩa trong ứng dụng. Giá trị trả về `"register-form"` và `"register-result"` là **logical view names** khi không có `@ResponseBody`. Spring chưa gửi những chuỗi này trực tiếp vào HTTP body như JSON.

`BindingResult`/ `Errors` phải nằm **ngay sau argument mà nó mô tả**. Nếu dùng `@Valid @ModelAttribute RegistrationForm form` mà thiếu đối số `BindingResult` cần thiết để tự xử lý lỗi, lỗi validation có thể trở thành exception do Spring giải quyết và phương thức không vào nhánh `if` như bạn mong đợi.

### 5.1. @Valid và @Validated đứng ở đâu trong pipeline?

- **Binding/deserialization** đưa dữ liệu vào đối tượng Java.
- **Validation** đánh giá constraints trên đối tượng hoặc method parameters khi đã có đối tượng/giá trị thích hợp.
- **Business validation** kiểm tra các ràng buộc nghiệp vụ phức tạp, có thể cần database/service và thường nằm trong tầng nghiệp vụ.

`@Valid @RequestBody` có thể kích hoạt Bean Validation và lỗi thông thường phát sinh dưới dạng `MethodArgumentNotValidException`, thường được xử lý thành 400. Trong Spring MVC 6.1+, khi có constraints trực tiếp ở method parameter và method validation được áp dụng, `HandlerMethodValidationException` cũng có thể phát sinh. **Không giả định mọi lỗi validation đi qua duy nhất một exception class**, và không nhầm validation với JSON deserialization.

Đối với `@ModelAttribute`, hãy kiểm soát những thuộc tính được bind; không để client tự đặt thuộc tính nhạy cảm chỉ vì nó có setter, như `role`, `isAdmin` hoặc `balance`. Dùng DTO chuyên cho input, cho phép bind những field hợp lệ và áp dụng validation/bảo mật nghiệp vụ độc lập.

### 5.2. BindingResult là dữ liệu lỗi, không phải response status

`BindingResult` giữ thông tin field/global errors để controller tự quyết định xử lý. Việc có `BindingResult` không tự ghi HTTP status 400; nếu controller trả view form với status 200 thì client có thể vẫn nhận 200. Trong REST API, bạn thường cần chuyển các lỗi thành status phù hợp như 400/422 theo hợp đồng được công bố. Phân biệt **thu thập lỗi** với **chọn HTTP response**.

## 6. Gọi controller method thực sự

Sau khi từng argument được giải quyết, Spring gọi phương thức Java đã chọn. Controller là Spring-managed bean (singleton trong cấu hình mặc định), nên **nhiều request có thể gọi cùng instance đồng thời**. Tham số method được tạo riêng cho lần gọi, nhưng các trường instance mutable của singleton controller là dữ liệu chia sẻ; tránh dùng controller field để lưu trạng thái tạm của request.

Một số điểm quan trọng:

1. **Mất thời gian ở resolver cũng là thời gian request.** Converter, validation, binder có thể thất bại trước khi vào method.
2. **Interceptor preHandle** có thể ngăn handler tiếp tục. Exception filter/security cũng có thể trả response sớm.
3. **AOP proxy** có thể bọc controller/service, ảnh hưởng giao dịch, validation, bảo mật hay logging theo cấu hình.
4. **Service call** do controller điều phối, không mặc nhiên được Spring MVC retry hay tự biến thành async.
5. **Exception** phát ra trong resolver, controller hoặc return-value handler có thể được chuyển cho chuỗi `HandlerExceptionResolver`.

Không phải mọi response từ một URI đều do body của controller method đó tạo. Ví dụ lỗi request parsing có thể sinh 400 trước khi method chạy; một filter có thể gửi 401; Spring Security có thể quyết định 403.

## 7. HandlerMethodReturnValueHandler giải thích giá trị trả về

Sau khi controller trả một Java value, Spring chọn **`HandlerMethodReturnValueHandler`** phù hợp theo return type và annotation. Handler này có thể:

- Đánh dấu response đã được xử lý và viết status/headers/body.
- Đưa dữ liệu vào `ModelAndViewContainer` rồi để MVC render view.
- Đăng ký xử lý async để request tiếp tục ở lần dispatch sau.
- Chuẩn hóa status/headers theo `ResponseEntity` hoặc các annotation tương ứng.

`HandlerMethodReturnValueHandler` và `HttpMessageConverter` có vai trò khác nhau. **Return-value handler quyết định cách diễn giải Java return value** (body hay view, headers/status, async...), còn **message converter biến Java object thành bytes của HTTP representation** khi cần viết body.

| Controller trả về | Cách Spring MVC hiểu | Ví dụ |
| --- | --- | --- |
| Object + `@ResponseBody` | Serialize object vào response body | `BookResponse` → JSON |
| Object trong `@RestController` | Tương tự vì `@RestController` bao gồm `@ResponseBody` | JSON API |
| `ResponseEntity<T>` | Chỉ định HTTP status, headers và body | 201 + `Location` + DTO |
| `HttpEntity<T>` | Body + headers phản hồi, không có API đặt status riêng như `ResponseEntity` | Kết quả có header |
| `String` trong `@Controller` không `@ResponseBody` | Logical view name | `"books/list"` |
| `ModelAndView` | Tên/view object cùng model | Render HTML |
| `Model` / `Map` | Thêm attributes vào model; tên view xác định theo quy tắc MVC | Trang HTML |
| `ProblemDetail` / `ErrorResponse` | Mô tả lỗi theo hỗ trợ problem details | Phản hồi lỗi |
| `Callable<T>`, `DeferredResult<T>` | Kết quả async, sẽ xử lý return value khi có kết quả | Long-running request |
| `ResponseBodyEmitter`, `SseEmitter` | Streaming response | SSE |
| `void` | Phụ thuộc annotation/context và mức response đã xử lý | Không tự đồng nghĩa 204 |

**Giá trị Java giống nhau có thể mang ngữ nghĩa HTTP khác nhau** nếu annotation khác nhau.

### 7.1. @Controller với String: view name

~~~java
@Controller
class PageController {
    @GetMapping("/books")
    public String books(Model model) {
        model.addAttribute("heading", "Book catalog");
        return "books/list";
    }
}
~~~

Đây là method/controller minh họa chạy khi app đã cấu hình một view technology như Thymeleaf và có template tương ứng. Chuỗi `"books/list"` được view resolver diễn giải thành một view/template cần render. `Model` chuyển `heading` tới view. Nếu view không tồn tại, bạn có thể nhận lỗi render mặc dù handler method đã chạy thành công.

Với `return "redirect:/books";`, Spring MVC có thể xử lý tiền tố `redirect:` để tạo HTTP redirect, thay vì render một view cùng tên.

### 7.2. @RestController với String: response body

~~~java
@RestController
class TextController {
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
~~~

Với stack có `StringHttpMessageConverter` phù hợp, response body là chuỗi:

~~~text
pong
~~~

Ở đây Spring **không** tìm template tên `pong`. `@RestController` là một meta-annotation mang hiệu ứng `@Controller` và `@ResponseBody`. Tùy configuration/content negotiation, `Content-Type` có thể là `text/plain` hoặc loại tương ứng; không mặc định response là JSON chỉ vì controller có `@RestController`.

### 7.3. ResponseEntity điều khiển cả status, headers và body

~~~java
@PostMapping("/books")
public ResponseEntity<BookResponse> create(@RequestBody CreateBookRequest input) {
    BookResponse result = new BookResponse(42L, input.title());
    return ResponseEntity
            .created(URI.create("/api/books/42"))
            .body(result);
}
~~~

Đoạn này là **method minh họa** cần định nghĩa controller, DTO và import phù hợp. Kết quả có status `201 Created`, header `Location: /api/books/42` và body object do converter serialize. `ResponseEntity.ok(value)` tạo 200, `ResponseEntity.noContent().build()` tạo 204 không có body, `ResponseEntity.notFound().build()` tạo 404 không có body theo ví dụ.

**Đừng cố trả JSON body với 204 No Content.** Phản hồi 204 theo HTTP không mang message content. ResponseEntity không bỏ qua quy tắc HTTP chỉ vì kiểu generic được khai báo.

### 7.4. @ResponseStatus, null và void

`@ResponseStatus(HttpStatus.NO_CONTENT)` có thể đặt status khi handler hoàn tất, nhưng không có nghĩa mọi `void` method đều tự trả 204. Return type `void` hoặc `null` có ngữ nghĩa phụ thuộc các annotation, Servlet response arguments, việc response đã được ghi hay model/view flow. Trong HTML controller, Spring có thể suy ra default view name; trong REST controller, thường không có body.

Nếu tự ghi vào `HttpServletResponse.getWriter()` thì đang tác động response trực tiếp; không nên đồng thời mong message converter viết thêm một object nữa vào cùng stream. Một khi response committed, thay status/header hoặc render trang lỗi có thể không còn thực hiện được.

## 8. Ví dụ Spring Boot 3 chạy được: quan sát đủ ba chặng

Tạo dự án **Spring Boot 3.x / Java 17+** với dependencies:
- `spring-boot-starter-web`: Spring MVC, embedded servlet container và JSON converter thường dùng.
- `spring-boot-starter-validation`: Bean Validation với Jakarta Validation, để `@NotBlank`/ `@Valid` hoạt động.

Với dự án Gradle đã cấu hình Spring Boot plugin, các dependencies tương ứng:

~~~groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
~~~

Đặt toàn bộ code dưới đây vào **`src/main/java/demo/mvc/MvcPipelineApplication.java`**. Ví dụ chạy trong ứng dụng Spring Boot với build/dependency nói trên, không phải chương trình biên dịch bằng `javac` đơn lẻ không có classpath của Spring.

~~~java
package demo.mvc;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class MvcPipelineApplication {
    public static void main(String[] args) {
        SpringApplication.run(MvcPipelineApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/books")
class BookController {

    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, Book> books = new ConcurrentHashMap<>();

    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> get(
            @PathVariable long id,
            @RequestParam(defaultValue = "vi") String lang,
            @RequestHeader(value = "X-Trace-Id",
                           defaultValue = "none") String traceId) {

        Book book = books.get(id);
        if (book == null) {
            return ResponseEntity.notFound().build();
        }
        BookResponse response = new BookResponse(
                book.id(), book.title(), lang, traceId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = "application/json",
                 produces = "application/json")
    public ResponseEntity<BookResponse> create(
            @Valid @RequestBody CreateBookRequest request) {

        long id = sequence.incrementAndGet();
        Book book = new Book(id, request.title());
        books.put(id, book);

        BookResponse response =
                new BookResponse(id, book.title(), "vi", "none");

        return ResponseEntity
                .created(URI.create("/api/books/" + id))
                .body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (books.remove(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    public record CreateBookRequest(@NotBlank String title) {}

    public record Book(long id, String title) {}

    public record BookResponse(
            long id, String title, String lang, String traceId) {}
}
~~~

**Vì sao ví dụ này minh họa đủ pipeline?**

- `@PathVariable`, `@RequestParam` và `@RequestHeader`: resolver lấy URI/query/header rồi convert sang kiểu Java.
- `@Valid @RequestBody`: converter đọc JSON thành record; validation kiểm tra `title` phải là chuỗi không trống/không chỉ whitespace.
- `get`, `create`, `delete`: controller method được gọi sau khi arguments đã được chuẩn bị.
- `ResponseEntity<BookResponse>`: return-value handler đọc status/headers/body; Jackson converter serialize record sang JSON.
- `ResponseEntity<Void>`: return-value handler trả 204 không có body.
- `ConcurrentHashMap` + `AtomicLong`: demo hỗ trợ nhiều request đồng thời ở mức lưu tạm trong bộ nhớ; đây không phải database, không cung cấp transaction/durability.

Ứng dụng mới khởi động không có dữ liệu và `sequence` bắt đầu từ 0; ví dụ HTTP dưới đây giả định chưa có client khác tạo sách.

Chạy bằng Gradle wrapper từ thư mục dự án:

~~~bash
./gradlew bootRun
~~~

Trên Windows PowerShell:

~~~powershell
.\gradlew.bat bootRun
~~~

Sau khi ứng dụng khởi động với port mặc định `8080`, thử tạo sách:

~~~bash
curl -i -X POST "http://localhost:8080/api/books" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -d '{"title":"Spring MVC"}'
~~~

Response kỳ vọng có status `201 Created`, `Location: /api/books/1` và JSON tương đương:

~~~json
{"id":1,"title":"Spring MVC","lang":"vi","traceId":"none"}
~~~

Thứ tự field, khoảng trắng, một số response headers và cách ghi Transfer-Encoding/Content-Length tùy converter/container. Giá trị ID = 1 chỉ đúng khi đây là lần tạo đầu tiên của một process demo mới.

Tiếp tục:

~~~bash
curl -i "http://localhost:8080/api/books/1?lang=en" \
  -H "X-Trace-Id: demo-001" \
  -H "Accept: application/json"
~~~

Response kỳ vọng là 200 và body tương đương:

~~~json
{"id":1,"title":"Spring MVC","lang":"en","traceId":"demo-001"}
~~~

Có thể theo dõi argument lần lượt: `id=1` lấy từ path; `lang="en"` lấy từ query; `traceId="demo-001"` lấy từ header. Giá trị trả về là `BookResponse`, sau đó converter serialize thành JSON.

Thử delete:

~~~bash
curl -i -X DELETE "http://localhost:8080/api/books/1"
~~~

Response kỳ vọng **204 No Content** không có body. Gọi lại `GET /api/books/1` nhận 404 do sách đã bị xóa.

Thử các lỗi input:

~~~bash
curl -i "http://localhost:8080/api/books/abc"

curl -i -X POST "http://localhost:8080/api/books" \
  -H "Content-Type: application/json" \
  -d '{"title":""}'

curl -i -X POST "http://localhost:8080/api/books" \
  -H "Content-Type: text/plain" \
  -d 'just text'
~~~

| Request lỗi | Điểm thất bại | HTTP status thường gặp |
| --- | --- | --- |
| `GET /api/books/abc` | `"abc"` không chuyển thành `long id` | 400 |
| POST JSON với `title=""` | Bean Validation `@NotBlank` | 400 |
| POST `Content-Type: text/plain` | `consumes="application/json"` không match | 415 |
| GET id đã bị xóa | Controller chủ động trả `notFound()` | 404 |

**Lưu ý:** Body lỗi mặc định (JSON/HTML/Problem Details) tùy Spring Boot version, `Accept`, cấu hình error handler và Security. Bảng xác nhận ý nghĩa status, không giả định một response body lỗi duy nhất.

### 8.1. Bóc từng bước một request

Với `POST /api/books` và `{"title":"Spring MVC"}`:

1. Servlet container đưa request cho `DispatcherServlet`.
2. `RequestMappingHandlerMapping` khớp POST, path, `consumes`, `produces` và tìm `BookController.create`.
3. `RequestMappingHandlerAdapter` chọn argument resolver cho `@RequestBody`.
4. `HttpMessageConverter` đọc bytes JSON, tạo `CreateBookRequest("Spring MVC")`.
5. Bean Validation kiểm tra `@NotBlank`; nếu lỗi thì dừng trước controller method.
6. Spring gọi `create(request)`; method tạo `Book`, lưu map và tạo `ResponseEntity`.
7. Return-value handler nhận `ResponseEntity`, đặt status 201 và header Location.
8. JSON converter serialize `BookResponse` thành response body.
9. Container gửi HTTP response. Interceptor/filter và bước hoàn tất request còn có thể tham gia tùy cấu hình.

Đây là **thứ tự logic** giúp hiểu cơ chế, không khẳng định mỗi bước luôn do một class duy nhất xử lý trên mọi cấu hình.

## 9. Exception xảy ra ở đâu và response hình thành thế nào?

Một request có thể thất bại trước, trong hoặc sau method:

| Chặng | Nguyên nhân | Exception/thành phần đại diện | Status thường gặp |
| --- | --- | --- | --- |
| Handler matching | Path không có route | Cơ chế mapping/static resource/no-handler | 404 |
| Handler matching | Method không được chấp nhận | `HttpRequestMethodNotSupportedException` | 405 |
| Handler matching/media type | `consumes` không phù hợp | `HttpMediaTypeNotSupportedException` | 415 |
| Argument resolution | Thiếu `@RequestParam` bắt buộc | `MissingServletRequestParameterException` | 400 |
| Argument resolution | Path/query conversion thất bại | `MethodArgumentTypeMismatchException` | 400 |
| Message conversion | Body JSON không đọc được | `HttpMessageNotReadableException` | 400 |
| Bean Validation | `@Valid @RequestBody` không hợp lệ | `MethodArgumentNotValidException` hoặc lỗi method validation tùy tình huống | 400 |
| Controller business | Không tìm thấy tài nguyên | Controller trả 404 / exception có mapping phù hợp | 404 |
| Return conversion | Không tạo được media type mà client yêu cầu | `HttpMediaTypeNotAcceptableException` | 406 |
| Bất kỳ chặng | Lỗi không xử lý được | Exception mapping / container | Có thể 500 |

Trong MVC, `DispatcherServlet` phối hợp các `HandlerExceptionResolver`. Cơ chế phổ biến gồm `ExceptionHandlerExceptionResolver` (xử lý `@ExceptionHandler`/advice), `ResponseStatusExceptionResolver` (ví dụ `@ResponseStatus`, `ResponseStatusException`) và `DefaultHandlerExceptionResolver` (các lỗi MVC phổ biến). Thứ tự cấu hình và tùy chỉnh quyết định resolver nào xử lý trước.

**Đừng bọc mọi exception rồi trả HTTP 200** chỉ để tránh lỗi ở frontend. Khi tạo API, hãy tách lỗi client có thể khắc phục, lỗi nghiệp vụ đã xác định và lỗi hạ tầng bất ngờ; sử dụng status cùng một schema lỗi ổn định.

### 9.1. @RestControllerAdvice để chuẩn hóa lỗi

Ví dụ minh họa một error handler trong ứng dụng Spring MVC (phải đặt trong component scan):

~~~java
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badInput(
            IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }
}
~~~

Đoạn class này cần import `java.util.Map`, `ResponseEntity`, `@ExceptionHandler`, `@RestControllerAdvice`. Ví dụ chỉ định nghĩa mapping cho một loại exception để minh họa. **Không nên tự động trả nguyên `ex.getMessage()` cho mọi loại exception trong production**, vì có thể làm lộ dữ liệu nội bộ; với lỗi không được tin cậy, dùng message an toàn và log chi tiết ở server.

`@RestControllerAdvice` là cơ chế chọn exception handler trước khi kết quả được viết ra. Nó **không đảm bảo** thay được phản hồi đã committed, cũng không xử lý mọi lỗi trong security filter/container nằm ngoài phạm vi exception resolution của MVC.

## 10. Xử lý giá trị trả về bất đồng bộ

Spring MVC hỗ trợ trả các wrapper async mà vẫn dùng Servlet stack. Ví dụ **method minh họa**:

~~~java
@GetMapping("/reports/{id}")
public Callable<ResponseEntity<String>> report(@PathVariable long id) {
    return () -> ResponseEntity.ok("report-" + id);
}
~~~

Ý nghĩa:
1. Handler method đầu tiên trả một `Callable`, thay vì body cuối ngay lập tức.
2. Spring MVC bắt đầu xử lý async với Servlet async support và một executor thích hợp.
3. Khi `Callable` hoàn thành, kết quả `ResponseEntity<String>` được đưa trở lại pipeline xử lý return value.
4. Response cuối cùng có thể được ghi ở giai đoạn async dispatch.

Đoạn này cần đặt trong controller Spring MVC và import `Callable`, `ResponseEntity`, `@GetMapping`, `@PathVariable`; dùng executor được cấu hình phù hợp với tải thực tế. `DeferredResult<T>` có thể được hoàn tất bởi một callback/thread khác; đó không phải bảo đảm mọi thao tác I/O của MVC đều non-blocking. Nếu timeout hoặc exception xảy ra trong async processing, xử lý lỗi cần xét cả async lifecycle.

Đừng coi `Callable<ResponseEntity<T>>` là một “return type lạ không serialize được”: async mechanism lấy kết quả cuối rồi **tiếp tục dùng return-value handlers** cho `ResponseEntity<T>`.

## 11. Custom argument resolver: khi nào cần?

Nếu ứng dụng thường xuyên cần trích một dữ liệu miền nghiệp vụ từ request (ví dụ tenant ID đã được xác thực), có thể viết `HandlerMethodArgumentResolver` riêng:

~~~java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentTenant {}
~~~

Đoạn annotation cần imports `java.lang.annotation.ElementType`, `Retention`, `RetentionPolicy`, `Target` và đặt ở **`CurrentTenant.java`** nếu là public top-level annotation. Resolver tương ứng sẽ implement `supportsParameter` và `resolveArgument`, có thể đọc trusted context trong request; khai báo qua `WebMvcConfigurer.addArgumentResolvers`.

Ví dụ sử dụng sau khi đã **thật sự đăng ký resolver**:

~~~java
@GetMapping("/tenant-info")
public String tenant(@CurrentTenant String tenantId) {
    return tenantId;
}
~~~

Đây là **mã minh họa, chưa phải chương trình chạy độc lập** vì chưa định nghĩa implementation của resolver hoặc cấu hình bảo mật và chưa gắn vào controller. Không dùng `@CurrentTenant` nếu chưa có resolver hỗ trợ. Khi code cần path/query/header tiêu chuẩn, dùng built-in resolver; tự viết resolver chỉ cho abstraction có ý nghĩa thực sự.

Các lưu ý nếu viết resolver:
- Tránh trùng với resolver mặc định hoặc chọn tham số quá rộng (ví dụ nhận mọi `String`).
- Chỉ dựa vào thông tin tenant/principal đã được **xác thực**; không tin raw `X-Tenant` từ Internet.
- Quyết định xử lý lỗi thiếu hoặc sai tenant bằng exception có HTTP mapping rõ ràng.
- Resolver thường được chia sẻ giữa request, nên không lưu trạng thái từng request trong các mutable instance fields.

## 12. Những nhầm lẫn phổ biến cần sửa

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`@GetMapping` tự truyền tham số vào Java method.” | Handler mapping chọn method; argument resolver chuẩn bị Java arguments. |
| “`@PathVariable long` luôn match thất bại khi URL chứa chữ.” | Handler path có thể match, rồi lỗi xảy ra khi conversion `String → long`. |
| “`@RequestBody` và `@ModelAttribute` giống nhau.” | Một bên đọc message content qua converter, bên kia bind request parameters vào model object. |
| “Có `@Valid` thì controller luôn chạy rồi mới thấy lỗi.” | Validation có thể dừng ở argument resolution trước khi method được gọi. |
| “`BindingResult` đặt đâu cũng được.” | Đặt ngay sau argument mà nó mô tả. |
| “`@RestController` bắt buộc trả JSON.” | Body serialization theo converter và content negotiation; `String` có thể là text. |
| “Return `String` luôn là HTTP body.” | Trong `@Controller` không có `@ResponseBody`, `String` thường là tên view. |
| “Return `void` luôn là 204.” | Status/view/body tùy annotation, context và response đã được xử lý. |
| “`ResponseEntity` tự JSON hóa đối tượng.” | Return-value handler xử lý status/headers; message converter serialize body. |
| “Handler đã match thì chắc chắn controller chạy.” | Resolver, conversion, validation, interceptor hoặc filter có thể ngăn chạy. |
| “Validation lỗi luôn là một loại exception.” | Spring MVC có các đường validation khác nhau, nhất là method validation. |
| “Controller singleton lưu request field là an toàn.” | Mutable field bị chia sẻ giữa requests và cần thiết kế thread-safe. |

## 13. Bài tập tự kiểm tra và đáp án

**Bài 1.** Xét method:

~~~java
@GetMapping("/books/{id}")
public ResponseEntity<BookResponse> get(
        @PathVariable long id,
        @RequestParam(defaultValue = "vi") String lang,
        @RequestHeader("X-Client") String client) {
    // ...
}
~~~

Với `GET /books/7?lang=en` không có header `X-Client`, controller có được gọi không? Lỗi thường xảy ra ở bước nào?

**Bài 2.** Một HTML controller trả `"books/index"`, một REST controller trả `"books/index"`. Hai phản hồi có nhất thiết giống nhau không?

**Bài 3.** POST JSON với `Content-Type: application/json` nhưng body hỏng cú pháp. Spring có nhất thiết vào controller rồi trả 500 không?

**Bài 4.** Controller nhận `@Valid @RequestBody CreateBookRequest body`; body hợp lệ về cú pháp JSON nhưng trường `title` vi phạm `@NotBlank`. Bước nào xử lý lỗi? Mã HTTP thường gặp?

**Bài 5.** Với `ResponseEntity.created(URI.create("/api/books/42")).body(dto)`, thành phần nào đặt status/headers, thành phần nào viết JSON body?

**Bài 6.** Tại sao `Callable<ResponseEntity<String>>` vẫn có thể trả HTTP status và body đúng, dù method ban đầu không trả `ResponseEntity` trực tiếp?

### Đáp án giải thích

1. `@RequestHeader("X-Client")` mặc định required. Không có header này, argument resolution thường báo lỗi thiếu request header, phản hồi 400; method chưa được gọi.
2. Không. HTML `@Controller` có thể coi chuỗi là **view name**; `@RestController` coi nó là **response body**.
3. Không. Converter thường báo `HttpMessageNotReadableException` trong lúc tạo `@RequestBody`; xử lý mặc định thường là 400 trước khi method chạy.
4. Sau khi converter tạo DTO, validation thực thi constraint; Spring thường phản hồi 400 thông qua exception handling nếu không có `BindingResult` phù hợp.
5. `HandlerMethodReturnValueHandler` dành cho `ResponseEntity` áp dụng status 201 và `Location`; `HttpMessageConverter` được chọn serialize `dto` thành JSON.
6. Spring MVC nhận ra wrapper async, chờ kết quả, rồi xử lý `ResponseEntity<String>` bằng return-value mechanism trong giai đoạn tiếp tục xử lý.

## 14. Checklist khi đọc hoặc xây một controller Spring MVC

Khi một endpoint không trả như mong đợi, đọc theo thứ tự:

1. **Handler:** Method/path/headers/consumes/produces có match? `HandlerMapping` chọn method nào?
2. **Arguments:** Từng argument đến từ đâu? Có required/default, conversion, multipart/body converter không?
3. **Binding và validation:** DTO được bind theo cơ chế nào? Có lỗi field/constraint trước method không?
4. **Invocation:** Có filter/interceptor/AOP/service exception? Controller singleton có dùng mutable shared state sai không?
5. **Return value:** `String` là view name hay body? `ResponseEntity` đặt status/header nào? Converter có thể ghi media type client yêu cầu không?
6. **Errors và async:** Exception resolver nào xử lý; response đã committed chưa; có async dispatch/timeout không?
7. **Quan sát thực tế:** Xem network status, headers, body và log/tracing; không kết luận chỉ từ một annotation.

Khi hiểu rõ ba chặng **resolve tham số → gọi controller → xử lý giá trị trả về**, bạn sẽ biết được vì sao một Java method không được gọi, vì sao cùng một `String` lại thành trang HTML hoặc HTTP body, và vì sao một object Java cần message converter trước khi tới client.

**Tài liệu tham khảo chính thức:** Spring Framework MVC *Annotated Controllers – Method Arguments* (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html), *Return Values* (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html), *@RequestBody* (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html), *@ResponseBody* (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responsebody.html) và *ResponseEntity* (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responseentity.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-request-lifecycle-005">ModelAndView, ViewResolver và giai đoạn kết xuất giao diện</a>

<details>
<summary>Xem chi tiết</summary>

# ModelAndView, ViewResolver và giai đoạn kết xuất giao diện trong Spring MVC

## 1. Vấn đề: Controller xử lý xong, HTML đến trình duyệt bằng cách nào?

Một HTTP request tới ứng dụng Spring MVC không kết thúc ngay khi phương thức controller trả về. Với kiểu **server-side rendered HTML**, sau khi controller lấy dữ liệu và quyết định trang cần hiển thị, framework còn phải:

1. Thu thập dữ liệu để hiển thị (**model**).
2. Xác định tên giao diện logic hoặc đối tượng giao diện (**view**).
3. Tìm công cụ/đích có thể xử lý view (**view resolution**).
4. Kết xuất view dựa trên model thành nội dung HTTP response (**rendering**).

Ba khái niệm trung tâm là **`ModelAndView`**, **`ViewResolver`** và **`View`**. Chúng kết hợp với `DispatcherServlet` để hoàn thành phần cuối của luồng MVC.

Ví dụ, trình duyệt gọi:

```http
GET /books HTTP/1.1
Host: localhost:8080
Accept: text/html
```

Controller có thể trả về tên trang `"books"` cùng model gồm tiêu đề và danh sách sách. `ViewResolver` ánh xạ `"books"` tới template như `classpath:/templates/books.html`; engine template thay các biểu thức dữ liệu và tạo HTML để gửi về browser.

**Phân biệt:** Controller trả tên `"books"` không có nghĩa response body chỉ chứa chữ `books`. Với `@Controller` và không có `@ResponseBody`, đây thường là **logical view name**. Với `@RestController` hoặc `@ResponseBody`, kết quả đi qua cơ chế ghi response body, thường **không gọi ViewResolver**.

## 2. Vị trí trong luồng xử lý MVC

```text
Client / Browser
      |
      | HTTP GET /books
      v
Servlet container (Tomcat, Jetty, ...)
      |
      v
DispatcherServlet
      |
      +--> HandlerMapping: tìm handler tương ứng URL + method
      |
      +--> HandlerAdapter: chuẩn bị tham số, gọi controller
      |         |
      |         v
      |   @Controller: xử lý nghiệp vụ, cung cấp model/view
      |         |
      |         v
      |   ModelAndView hoặc thông tin tương đương
      |
      +--> ViewResolver(s): logical name "books" -> View
      |                                           |
      |                                           v
      +-------------------------------------- View.render(...)
                                                  |
                                                  v
                                        HTML / HTTP response
                                                  |
                                                  v
                                               Browser
```

Đây là **luồng đồng bộ tiêu biểu của request tạo HTML**, đã lược bớt filter, interceptor, exception resolver, locale resolution, multipart processing và các khả năng khác. Spring MVC có nhiều kiểu return value và cơ chế bất đồng bộ; không phải request nào cũng đi qua đầy đủ bước tìm view.

Trong Spring MVC, `DispatcherServlet` là **front controller**. Nó phối hợp những thành phần chiến lược như `HandlerMapping`, `HandlerAdapter`, `HandlerExceptionResolver` và `ViewResolver`. Từ controller đến response, framework có nhiệm vụ diễn giải kiểu giá trị trả về và quyết định cách hoàn thành request.

### Quan hệ giữa các lớp

| Thành phần | Công việc | Ví dụ |
|---|---|---|
| `Model` | Chứa dữ liệu để view đọc | `pageTitle`, `books`, `user` |
| Logical view name | Tên view ở mức nghiệp vụ/UI | `"books"`, `"account/profile"` |
| `ModelAndView` | Gói view và dữ liệu model, có thể mang HTTP status | `new ModelAndView("books")` |
| `ViewResolver` | Biến logical view name thành đối tượng `View` | Thymeleaf, JSP, FreeMarker resolver |
| `View` | Kết xuất dữ liệu vào HTTP response | Thymeleaf view, JSP view, RedirectView |
| `DispatcherServlet` | Điều phối request và bước render | Front controller của Spring MVC |

`ModelAndView` không phải HTML đã render. `ViewResolver` cũng không phải template engine: resolver **chọn/giải quyết view**, còn `View` và công nghệ bên dưới mới thực hiện việc kết xuất. Có hệ thống tích hợp những vai trò này trong cùng một thư viện, nhưng về mặt khái niệm cần tách.

## 3. Model là gì?

**Model** biểu diễn các thuộc tính mà controller cung cấp cho view của một request. Trong annotated Spring MVC controller, `org.springframework.ui.Model` là interface hỗ trợ đưa cặp tên–giá trị vào model:

```java
@GetMapping("/greeting")
public String greeting(Model model) {
    model.addAttribute("name", "Lan");
    model.addAttribute("message", "Chào mừng!");
    return "greeting";
}
```

**Đoạn mã này chỉ minh họa một handler**, chưa độc lập chạy được vì thiếu class controller, imports, dependency và template. Trong view `greeting`, các thuộc tính `name`, `message` có thể được dùng để tạo nội dung giao diện.

Cần phân biệt:

- `Model` là API để thêm các thuộc tính dùng trong quá trình render.
- `ModelMap` là một triển khai kiểu map dành cho dữ liệu model.
- `Map<String, Object>` cũng có thể được Spring MVC sử dụng trong một số chữ ký controller.
- `@ModelAttribute` cho phép khai báo/khởi tạo thuộc tính model và hỗ trợ bind dữ liệu; nó không đồng nghĩa với `@RequestBody`.
- Model dùng để render **không mặc nhiên sống qua nhiều HTTP request**; redirect tạo request mới, muốn chuyển thông báo một lần có thể dùng flash attributes, muốn lưu trạng thái phiên cần cơ chế phù hợp khác.

Ví dụ biểu diễn model về mặt khái niệm:

```text
pageTitle -> "Danh mục sách"
books     -> ["Java Core", "Spring MVC"]
notice    -> "Đã lưu thành công"
```

Khi template đọc các giá trị này, nó có thể hiển thị tiêu đề, lặp qua danh sách và hiển thị thông báo. Dữ liệu model có thể là chuỗi, số, collection hoặc đối tượng nghiệp vụ/view DTO. Không nên đổ trực tiếp entity phức tạp chứa dữ liệu bí mật vào model nếu view chỉ cần vài thuộc tính cụ thể.

## 4. ModelAndView là gì?

`org.springframework.web.servlet.ModelAndView` là **đối tượng kết hợp thông tin view và model**, có thể bao gồm cả HTTP status, để Spring MVC thực hiện bước render.

Các cách tạo phổ biến:

```java
ModelAndView mav = new ModelAndView("books");
mav.addObject("pageTitle", "Danh mục sách");
mav.addObject("books", List.of("Java Core", "Spring MVC"));
return mav;
```

Hoặc truyền dữ liệu từ đầu:

```java
return new ModelAndView("books", "pageTitle", "Danh mục sách");
```

Có thể cung cấp chính một đối tượng `View` thay vì tên view, hoặc đặt status phù hợp bằng API `ModelAndView`. Tuy nhiên, trong đa số controller server-side HTML, **tên view logic + model** là cách trình bày dễ hiểu và đủ linh hoạt.

### Các thành phần bên trong

```text
ModelAndView
  ├── View name: "books"             (hoặc một đối tượng View)
  ├── Model:
  │     "pageTitle" = "Danh mục sách"
  │     "books"     = List<String>
  └── HTTP status (tùy chọn)
```

Một số method thường dùng:

| API | Tác dụng |
|---|---|
| `new ModelAndView("books")` | Tạo kết quả với logical view name |
| `addObject("key", value)` | Thêm attribute vào model |
| `addAllObjects(map)` | Thêm nhiều attributes |
| `getViewName()` | Lấy tên view nếu kết quả dùng view name |
| `getView()` | Lấy đối tượng `View` nếu được thiết lập trực tiếp |
| `getModel()` | Lấy các attributes trong model |
| `setViewName("...")` | Thay tên view |
| `setStatus(HttpStatus.OK)` | Gắn HTTP status thích hợp khi cần |

Khi controller trả `ModelAndView`, Spring MVC diễn giải nó thành view và model để phối hợp rendering. Nếu controller trả `String` cùng tham số `Model`, Spring cũng có thể tập hợp thông tin tương đương mà không yêu cầu bạn tự tạo object `ModelAndView`.

### So sánh hai cách biểu đạt

**Cách A: controller trả `ModelAndView`**

```java
@GetMapping("/books")
public ModelAndView books() {
    ModelAndView mav = new ModelAndView("books");
    mav.addObject("pageTitle", "Danh mục sách");
    mav.addObject("books", List.of("Java Core", "Spring MVC"));
    return mav;
}
```

**Cách B: controller trả tên view, model qua tham số**

```java
@GetMapping("/books")
public String books(Model model) {
    model.addAttribute("pageTitle", "Danh mục sách");
    model.addAttribute("books", List.of("Java Core", "Spring MVC"));
    return "books";
}
```

Đây là hai **cách thay thế nhau** để khai báo endpoint và không được đặt đồng thời hai handler `GET /books` trong cùng ứng dụng. Cả hai đoạn là ví dụ method rời, cần nằm trong một `@Controller` có imports và template tương ứng.

Khi logic chỉ thêm vài attribute và chọn một trang, `String + Model` thường gọn hơn. `ModelAndView` thuận tiện khi muốn truyền view và model thành một giá trị, chọn động nhiều view hoặc làm việc với API trả `ModelAndView`.

## 5. ViewResolver: đổi tên logic thành View

Interface `org.springframework.web.servlet.ViewResolver` có phương thức cốt lõi về mặt ý nghĩa:

```java
View resolveViewName(String viewName, Locale locale) throws Exception;
```

Resolver nhận tên `viewName`, locale và tìm cách xác định đối tượng `View` phù hợp. Khi không giải quyết được, theo hợp đồng nó có thể trả `null`, cho phép các resolver khác thử tiếp.

Ví dụ:

```text
Controller trả: "books"
        |
        v
ThymeleafViewResolver
        |
        v
Thymeleaf view "books"
        |
        v
Template: classpath:/templates/books.html
```

**Đường dẫn thực tế phụ thuộc engine và cấu hình.** Không phải mọi `ViewResolver` đều nối tiền tố/hậu tố, và không phải mọi view là file HTML. Một số view đại diện JSP, redirect, tài nguyên hiển thị theo loại nội dung khác, hoặc một bean được cung cấp sẵn.

### 5.1. Các ViewResolver thường gặp

| Resolver | Tình huống sử dụng | Ghi chú |
|---|---|---|
| `ThymeleafViewResolver` | Render template Thymeleaf | Tích hợp template engine của Thymeleaf |
| `InternalResourceViewResolver` | JSP và Servlet forwarding | Nên đặt cuối chuỗi resolver |
| `FreeMarkerViewResolver` | Render FreeMarker | Phụ thuộc cấu hình FreeMarker |
| `BeanNameViewResolver` | Tìm bean `View` theo tên | Hữu ích cho view tùy biến |
| `ContentNegotiatingViewResolver` | Chọn view tương thích với media type client chấp nhận | Kết hợp/ủy quyền các resolver khác |
| `UrlBasedViewResolver` | Tên view ánh xạ theo URL quy ước | Lớp nền cho một số resolver |

**Lưu ý về độ ưu tiên:** Có thể khai báo nhiều resolver và sắp xếp bằng thuộc tính `order` (giá trị order nhỏ hơn được xét sớm hơn). Resolver đầu tiên tìm ra view phù hợp có thể kết thúc quá trình, vì vậy order sai có thể khiến engine mong muốn không được chọn.

`InternalResourceViewResolver` khó xác định JSP đích có tồn tại hay không trước khi forward request, nên trong cấu hình nhiều resolver thường cần đặt nó **sau cùng**. Nếu đặt trước, nó có thể nhận hầu hết view name và cản resolver tiếp theo.

### 5.2. ViewResolver không phải lúc nào cũng cần cấu hình thủ công

Trong Spring Boot MVC khi thêm `spring-boot-starter-thymeleaf`, Boot thường tự cấu hình Thymeleaf view resolver và tìm template dưới `classpath:/templates/`. Tên view `"books"` mặc định tương ứng `templates/books.html`.

Tùy cấu hình, có thể sử dụng các property như:

```properties
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html
spring.thymeleaf.cache=false
```

Các giá trị prefix/suffix trên là mặc định phổ biến của Thymeleaf trong Boot. Thiết lập `cache=false` hữu ích khi sửa template trong môi trường phát triển nhưng thường không nên tắt cache tùy tiện ở production. **Cache của view resolver và cache của template engine không nhất thiết là cùng một cơ chế**.

Spring Framework thuần không có Boot auto-configuration; bạn phải cung cấp cấu hình MVC/template cần thiết. Không nên lấy cấu hình Boot rồi giả định nó tự hoạt động trong một WAR Spring MVC thuần.

### 5.3. Locale và content negotiation

`ViewResolver` nhận `Locale`, nên một số resolver/template có thể lựa chọn nội dung phù hợp ngôn ngữ hoặc khu vực. Locale còn có thể ảnh hưởng cách format ngày/số và message bundle trong template; không nên nhầm locale với đường dẫn view.

Khi có nhiều representation, `ContentNegotiatingViewResolver` có thể kết hợp các resolver và dùng media type yêu cầu (ví dụ header `Accept`) để chọn view tương thích. Tuy nhiên, trả JSON từ `@ResponseBody` thông thường đi qua `HttpMessageConverter` và **không cần ép JSON thành một template view**.

## 6. Bước kết xuất giao diện thực sự xảy ra thế nào?

Sau khi chọn được `View`, `DispatcherServlet` gọi chức năng render của view, về mặt contract có dạng:

```java
void render(
        Map<String, ?> model,
        HttpServletRequest request,
        HttpServletResponse response
) throws Exception;
```

Đây là **chữ ký API để giải thích**, không phải chương trình Java độc lập. Tên import `HttpServletRequest`/`HttpServletResponse` tương ứng Jakarta Servlet trong Spring Framework 6+.

Từng bước điển hình khi render Thymeleaf:

1. `View` nhận model và request/response.
2. Engine tìm/đọc template có tên `books` theo prefix/suffix.
3. Các biểu thức trong template truy cập model (`pageTitle`, `books`).
4. Engine tạo markup HTML, xử lý điều kiện, vòng lặp, fragment, liên kết và escaped output theo cú pháp của engine.
5. Spring và Servlet response ghi nội dung, status và các header thích hợp, ví dụ `Content-Type: text/html;charset=UTF-8` khi được cấu hình.
6. Servlet container gửi các byte response tới client; trình duyệt phân tích HTML và tự thực hiện render giao diện trên màn hình.

**Hai kiểu “render” cần phân biệt:** Spring render **nội dung HTML ở server**; trình duyệt render **giao diện trực quan** từ HTML/CSS/JS ở client. `View.render` không trực tiếp vẽ pixel trên màn hình người dùng.

Ví dụ template Thymeleaf:

```html
<!doctype html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="${pageTitle}">Tiêu đề tạm</title>
</head>
<body>
    <h1 th:text="${pageTitle}">Tiêu đề tạm</h1>
    <ul>
        <li th:each="book : ${books}" th:text="${book}">Sách</li>
    </ul>
</body>
</html>
```

Nếu model có `pageTitle="Danh mục sách"` và `books=["Java Core", "Spring MVC"]`, browser nhận một tài liệu HTML chứa tiêu đề tương ứng và hai phần tử danh sách. Trong HTML sau render, thuộc tính `th:text` và `th:each` đã được engine xử lý; đây không còn là template gốc.

### Vì sao cần quan tâm HTML escaping?

Thymeleaf `th:text` thoát các ký tự có ý nghĩa đặc biệt trong HTML. Ví dụ nếu tên sách chứa `<script>`, dùng `th:text` sẽ hiển thị như văn bản thay vì chèn một thẻ script thực thi. Ngược lại, `th:utext` cho phép HTML không escape và **rủi ro XSS** nếu render dữ liệu không tin cậy mà chưa được kiểm soát/khử độc đúng cách.

Không dùng việc “template tự escape” thay cho authentication, authorization, input validation và chính sách bảo mật ứng dụng. Dữ liệu model chỉ nên chứa những thông tin được phép hiển thị; server-side rendering vẫn có thể làm rò rỉ dữ liệu nếu controller đổ thông tin nhạy cảm vào template.

## 7. Các kiểu giá trị trả về của Controller: khi nào có ViewResolver?

Trong Spring MVC, cách diễn giải giá trị trả về phụ thuộc **annotation và return-value handler**.

| Controller trả | Spring xử lý thông thường | Có dùng ViewResolver? |
|---|---|---|
| `@Controller` trả `String "books"` | Tên view logic, kết hợp model hiện có | Có |
| `@Controller` trả `ModelAndView("books")` | View + model (+ status tùy chọn) | Có nếu cung cấp view name |
| `@Controller` trả đối tượng `View` | Dùng trực tiếp đối tượng view với model | Không cần giải tên view |
| `@Controller` trả `Model`/`Map` | Thêm attributes; có thể suy ra tên view | Có nếu cần render view |
| `@Controller` + `@ResponseBody` | Ghi dữ liệu bằng `HttpMessageConverter` | Không |
| `@RestController` | Mặc định ghi kết quả như response body | Không trong trường hợp thông thường |
| `ResponseEntity<T>` | Chỉ định status, header và body | Không theo đường view thông thường |
| `"redirect:/books"` | Chọn redirect view / HTTP redirect | Có thể dùng chiến lược nhận prefix redirect |
| `"forward:/books"` | Server-side forward đến tài nguyên đích | Phụ thuộc resolver hỗ trợ prefix forward |

**Ví dụ gây nhầm lẫn:**

```java
@Controller
class ExampleController {
    @GetMapping("/a")
    public String a() {
        return "home";            // logical view name: home
    }

    @ResponseBody
    @GetMapping("/b")
    public String b() {
        return "home";            // response body văn bản: home
    }
}
```

Hai method trên được đặt trong một class minh họa, giả sử cấu hình MVC và view `home` tồn tại. Một giá trị Java `String` như nhau có thể dẫn đến hai luồng HTTP khác nhau do annotation quyết định.

**`@RestController` tương đương sự kết hợp ý nghĩa của `@Controller` và `@ResponseBody` ở cấp class.** Nếu muốn render template HTML server-side, lựa chọn tự nhiên là `@Controller` và trả view name/`ModelAndView`, thay vì `@RestController` trả `"books"`.

## 8. Ví dụ hoàn chỉnh: Spring Boot MVC + Thymeleaf

Ví dụ chạy trên **Java 17+**, Maven và Spring Boot 3.3.5 (một phiên bản cố định để minh họa API, không tuyên bố là bản mới nhất). Khi xây dựng sản phẩm mới, cần chọn bản Spring Boot còn được hỗ trợ phù hợp môi trường. Ứng dụng sử dụng `spring-boot-starter-web` để xử lý HTTP MVC và `spring-boot-starter-thymeleaf` để render HTML.

### 8.1. Cấu trúc dự án

```text
mvc-view-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/views/
    │   │   ├── MvcViewApplication.java
    │   │   ├── CatalogService.java
    │   │   └── CatalogController.java
    │   └── resources/
    │       ├── application.properties
    │       └── templates/books.html
    └── test/
        └── java/com/example/views/
            └── CatalogControllerTest.java
```

### 8.2. Maven dependencies

Tạo `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>mvc-view-demo</artifactId>
    <version>1.0.0</version>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
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
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
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

Starter Thymeleaf cung cấp bộ tích hợp engine với Spring MVC. Boot cung cấp `DispatcherServlet`, thiết lập resolver tương ứng và tìm file HTML trong thư mục template mặc định; không cần tự khai báo `InternalResourceViewResolver` cho ví dụ này.

### 8.3. Khởi tạo ứng dụng

Tạo `MvcViewApplication.java`:

```java
package com.example.views;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MvcViewApplication {
    public static void main(String[] args) {
        SpringApplication.run(MvcViewApplication.class, args);
    }
}
```

### 8.4. Service cung cấp dữ liệu

Tạo `CatalogService.java`:

```java
package com.example.views;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    public List<String> listTitles() {
        return List.of("Java Core", "Spring MVC");
    }
}
```

Service dùng danh sách bất biến để ví dụ có output xác định và không cần database. Trong ứng dụng thật, service có thể lấy thông tin từ repository hoặc dịch vụ bên ngoài, nhưng controller vẫn chỉ nên tập trung chuẩn bị dữ liệu cần cho view.

### 8.5. Controller minh họa ba đường xử lý

Tạo `CatalogController.java`:

```java
package com.example.views;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/books")
    public ModelAndView showBooks() {
        ModelAndView mav = new ModelAndView("books");
        mav.addObject("pageTitle", "Danh mục sách");
        mav.addObject("books", catalog.listTitles());
        return mav;
    }

    @GetMapping("/books/alternative")
    public String showBooksAlternative(Model model) {
        model.addAttribute("pageTitle", "Danh mục sách");
        model.addAttribute("books", catalog.listTitles());
        return "books";
    }

    @GetMapping("/books/json")
    @ResponseBody
    public List<String> booksAsJson() {
        return catalog.listTitles();
    }

    @GetMapping("/books/go")
    public String navigateToBooks() {
        return "redirect:/books";
    }
}
```

Bốn endpoint có ý nghĩa khác nhau:

- `GET /books`: tạo `ModelAndView` với view name `books` và model; Spring tìm Thymeleaf view để render.
- `GET /books/alternative`: kết quả HTML tương đương, nhưng controller trả `String` view name và dùng tham số `Model`.
- `GET /books/json`: `@ResponseBody` khiến Spring dùng HTTP message conversion để trả JSON; **không render books.html**.
- `GET /books/go`: tạo HTTP redirect tới `/books`; trình duyệt gửi request mới tới đích.

`List<String>` trên endpoint JSON được chuyển thành JSON qua Jackson trong cấu hình `spring-boot-starter-web`. Ứng dụng không trả các object service hoặc engine template cho client.

### 8.6. Tạo template Thymeleaf

Tạo `src/main/resources/templates/books.html`:

```html
<!doctype html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="${pageTitle}">Tiêu đề mặc định</title>
</head>
<body>
    <h1 th:text="${pageTitle}">Tiêu đề mặc định</h1>
    <ul>
        <li th:each="book : ${books}" th:text="${book}">Tên sách</li>
    </ul>
    <a th:href="@{/books/alternative}">Cách viết bằng Model</a>
</body>
</html>
```

Ở bước render, Thymeleaf:

1. Nhận `pageTitle` và `books` từ model.
2. Thay `th:text` bằng dữ liệu văn bản đã escape theo ngữ cảnh HTML.
3. Lặp qua danh sách `books` bằng `th:each` để tạo hai phần tử `li`.
4. Xây dựng link với `th:href`.
5. Tạo response HTML cho browser.

File `books.html` là template nguồn; lúc chạy browser không cần hiểu cú pháp `th:...`. Engine ở server đã xử lý các thuộc tính đó.

### 8.7. Cấu hình và chạy

Trong `src/main/resources/application.properties`, có thể đặt:

```properties
server.port=8080
spring.thymeleaf.cache=false
```

`cache=false` là tiện ích lúc phát triển để quan sát thay đổi template. Không thêm `@EnableWebMvc` vào ví dụ Boot nếu không có yêu cầu thay thế một phần cấu hình MVC tự động.

Chạy từ thư mục dự án có `pom.xml`:

```bash
mvn spring-boot:run
```

Hoặc xây dựng JAR trước rồi chạy:

```bash
mvn clean package
java -jar target/mvc-view-demo-1.0.0.jar
```

Giả sử server chạy bình thường trên `localhost:8080`, thử:

```bash
curl -i "http://localhost:8080/books"
```

Response dự kiến có status `200 OK`, `Content-Type` tương thích `text/html` và HTML chứa tiêu đề **Danh mục sách**, cùng hai dòng **Java Core** và **Spring MVC**. Không cần khẳng định mọi byte response cố định vì formatting và header phụ thuộc phiên bản/configuration.

```bash
curl -s "http://localhost:8080/books/json"
```

Body JSON dự kiến chính xác khi dùng danh sách trong ví dụ:

```json
["Java Core","Spring MVC"]
```

```bash
curl -i "http://localhost:8080/books/go"
```

Kết quả dự kiến: HTTP `302 Found` với header `Location` trỏ tới `/books`; có thể là URL tuyệt đối hoặc dạng phù hợp cấu hình runtime. `curl` mặc định **không tự theo redirect** nếu không thêm `-L`.

```bash
curl -L "http://localhost:8080/books/go"
```

Khi theo redirect, cuối cùng client nhận HTML của trang `/books`.

### 8.8. Kiểm thử cả view name, model và HTML

Tạo `src/test/java/com/example/views/CatalogControllerTest.java`:

```java
package com.example.views;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void rendersViewFromModelAndView() throws Exception {
        mvc.perform(get("/books"))
           .andExpect(status().isOk())
           .andExpect(view().name("books"))
           .andExpect(model().attribute("pageTitle", "Danh mục sách"))
           .andExpect(model().attribute(
                   "books", List.of("Java Core", "Spring MVC")))
           .andExpect(content().string(containsString("Java Core")))
           .andExpect(content().string(containsString("Spring MVC")));
    }

    @Test
    void writesJsonWithoutTemplateRendering() throws Exception {
        mvc.perform(get("/books/json"))
           .andExpect(status().isOk())
           .andExpect(content().json(
                   "[\"Java Core\",\"Spring MVC\"]"));
    }

    @Test
    void redirectsToBooks() throws Exception {
        mvc.perform(get("/books/go"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/books"));
    }
}
```

Chạy:

```bash
mvn test
```

Các assertion kiểm tra **kết quả có ý nghĩa của từng giai đoạn**: handler chọn đúng view `books`, model chứa hai thuộc tính cần thiết, Thymeleaf thực sự render HTML có nội dung, endpoint JSON tạo JSON, và redirect sinh URL đích. Đây là kiểm thử Spring Boot integration với `MockMvc` chạy qua pipeline MVC mà không cần mở cổng mạng thực. Khi thay đổi context path, security hay template, kỳ vọng test cần cập nhật tương ứng.

## 9. Redirect và forward: đều có thể trả view name đặc biệt

Một `@Controller` có thể trả logical view name bắt đầu bằng `redirect:` hoặc `forward:` tùy cấu hình MVC.

### 9.1. Redirect: client nhận lệnh đi tới URL khác

```java
@GetMapping("/old")
public String oldPage() {
    return "redirect:/books";
}
```

Đây là **method minh họa**; muốn chạy phải đặt trong một controller được đăng ký. Spring thường dùng một `RedirectView` tương đương để tạo phản hồi chuyển hướng. Client nhận mã 3xx và `Location`, sau đó gửi **một request mới** đến URL đích. URL hiển thị trong thanh địa chỉ thường thay đổi khi browser đi theo redirect.

Redirect đặc biệt hữu ích cho mẫu **Post/Redirect/Get (PRG)**: sau khi server xử lý form POST thành công, trả redirect để browser tải trang kết quả bằng GET, giảm nguy cơ người dùng refresh rồi vô tình gửi lại POST.

Ví dụ method mô tả PRG với flash message:

```java
@PostMapping("/orders")
public String submitOrder(
        @RequestParam String title,
        RedirectAttributes redirectAttributes) {
    // Xử lý tạo đơn qua service và kiểm tra lỗi ở đây.
    redirectAttributes.addFlashAttribute(
            "notice", "Đã nhận đơn: " + title);
    return "redirect:/orders";
}
```

Đây là **mảnh minh họa**, không phải endpoint sản xuất hoàn chỉnh: thiếu class, imports, service, kiểm tra đầu vào, authentication/CSRF, lưu đơn và đích `GET /orders`. `RedirectAttributes.addFlashAttribute` cho phép chuyển dữ liệu tạm thời tới request sau redirect mà không phải đưa thông báo vào query string. Flash attributes thường lưu tạm qua cơ chế session/FlashMap; các request đồng thời có thể tạo tình huống cần xử lý cẩn thận.

Không mặc nhiên dùng model của request trước cho request sau; sau redirect cần load dữ liệu lại hoặc sử dụng flash/session theo thiết kế.

### 9.2. Forward: server chuyển tiếp nội bộ

```java
@GetMapping("/legacy")
public String legacyPage() {
    return "forward:/books";
}
```

Đây cũng là method minh họa. Forward dùng cơ chế `RequestDispatcher.forward()` trong Servlet container để chuyển xử lý **nội bộ phía server**, thường giữ cùng request/response và browser không thấy một redirect HTTP 3xx do controller này sinh ra. Forward có các điều kiện/ràng buộc do Servlet API áp dụng, ví dụ response đã commit thường không thể forward.

Không xem redirect và forward là hai cách hoàn toàn tương đương. Redirect tạo request mới, có thể đổi URL và phù hợp PRG; forward nằm trong request hiện tại. Hãy kiểm tra view resolver và servlet mapping nếu dùng tiền tố `forward:`.

## 10. Cách giai đoạn ViewResolver tương tác với lỗi

Một số tình huống khác nhau có thể tạo ra kết quả nhìn giống nhau ở phía browser:

| Hiện tượng | Giai đoạn nghi ngờ | Gợi ý kiểm tra |
|---|---|---|
| `404 Not Found` khi vào `/books` | Mapping handler hoặc URL | Kiểm tra `@GetMapping`, context path, servlet mapping |
| Controller chạy nhưng không tìm được template | Resolve/render view | Kiểm tra view name, `templates/`, suffix, starter |
| `500 Internal Server Error` với lỗi xử lý template | Render | Kiểm tra expression, model attribute, lỗi template |
| Browser hiển thị nguyên chữ `books` | Return value ghi body | Có nhầm `@RestController`/`@ResponseBody` không? |
| Model có dữ liệu nhưng trang trống | Template expression / binding | Kiểm tra tên key và cách truy cập trong template |
| Nhiều ViewResolver, trang bị chọn sai | Chuỗi resolver | Kiểm tra `order` và resolver nhận view quá rộng |
| JSP không tìm thấy dù tên view đúng | JSP path / resolver / packaging | Kiểm tra `InternalResourceViewResolver` và WAR/JAR |
| Redirect làm lộ dữ liệu ở URL | Redirect attributes | Dùng `RedirectAttributes` chọn dữ liệu công khai; flash cho thông báo tạm |

### 10.1. Lỗi view name sai đường dẫn

Ví dụ controller trả `"book-list"`, nhưng tệp là `templates/books.html`. Nếu không có cấu hình ánh xạ đặc biệt, resolver không tìm được template `book-list`. Tạo đúng tên template hoặc sửa logic chọn view cho khớp, đừng chữa bằng cách đổi ngẫu nhiên nhiều property.

### 10.2. Trả nhầm `@RestController`

```java
@RestController
class WrongPageController {
    @GetMapping("/bad-page")
    public String page() {
        return "books";
    }
}
```

Ví dụ này trả response body chữ `books`, **không** yêu cầu Thymeleaf tìm `books.html`. Để server-side HTML, dùng `@Controller` phù hợp và trả tên view; để API trả dữ liệu, giữ `@RestController`.

### 10.3. Trộn web templates với `@ResponseBody`

Nếu đặt `@ResponseBody` lên method trả `ModelAndView`, Spring có thể cố xử lý chính object như body thay vì diễn giải nó như kết quả MVC render view. Không dùng sự kết hợp này để hiển thị HTML; chọn một cơ chế response phù hợp.

### 10.4. JSP và Spring Boot JAR

JSP có yêu cầu đóng gói, container và cấu hình khác Thymeleaf. Spring Boot tài liệu hóa hạn chế khi dùng JSP với embedded Servlet container và executable JAR. Đừng sao chép máy móc cấu hình `InternalResourceViewResolver` từ dự án WAR truyền thống sang Boot JAR; nếu cần JSP, đánh giá cấu trúc triển khai cụ thể. Với ví dụ nhập môn Boot MVC, Thymeleaf thường dễ thực hành hơn.

### 10.5. Dữ liệu không được escape hoặc hiển thị dữ liệu nhạy cảm

Model không phải cơ chế phân quyền. Bảo đảm service/controller kiểm soát dữ liệu được đưa ra view và sử dụng escaping/encoding phù hợp ngữ cảnh. Với Thymeleaf HTML, ưu tiên `th:text` cho text đầu vào thay vì kết xuất HTML không kiểm soát.

## 11. Quan sát và kiểm chứng pipeline rendering

Có thể debug theo chuỗi câu hỏi:

1. **Request có vào đúng `DispatcherServlet` không?** Xem URL, phương thức HTTP và log request mapping.
2. **Controller nào được chọn?** Kiểm tra handler mapping có đúng path/method không.
3. **Controller trả giá trị gì?** `String` view name, `ModelAndView`, `ResponseEntity` hay `@ResponseBody`?
4. **Model có đủ các attribute cần thiết không?** Kiểm tra key, kiểu dữ liệu và giá trị.
5. **Resolver nào xử lý view?** Kiểm tra engine/starter, prefix, suffix, thứ tự resolver và lỗi tìm template.
6. **View render thành gì?** Kiểm tra HTML response, status, `Content-Type`, đặc biệt lỗi expression.
7. **Browser hiển thị khác response?** Kiểm tra CSS, JavaScript, browser cache và công cụ Developer Tools.

Trong môi trường học tập, có thể bật logging Spring MVC chọn lọc để quan sát mapping và view resolution, nhưng không nên log tràn lan dữ liệu người dùng hoặc request body nhạy cảm. Khi debugging, so sánh **HTML ở tab Network → Response** với **DOM sau khi JavaScript chạy** để phân biệt lỗi server template và lỗi client rendering.

Cần nhớ rằng view resolution có thể bị **bỏ qua hoàn toàn** nếu response đã được handler xử lý (ví dụ `@ResponseBody`, `ResponseEntity`, streaming) hoặc controller thực hiện redirect. Không thấy log Thymeleaf trong API JSON là hành vi bình thường.

## 12. Vấn đề thiết kế: ModelAndView có phải lúc nào cũng nên dùng?

Không bắt buộc. Trong annotated Spring MVC, chọn cách thể hiện phù hợp với nhiệm vụ:

- Khi view cố định và chỉ có ít dữ liệu: `String` + `Model` thường ngắn gọn.
- Khi muốn gói model/view thành một object hoặc chọn view động: `ModelAndView` rõ ràng.
- Khi controller tạo response JSON/REST: `@RestController`/`@ResponseBody`/`ResponseEntity` thường phù hợp hơn.
- Khi cần phát một file/stream lớn: cân nhắc cơ chế response hoặc streaming phù hợp, tránh ép thành HTML template.
- Khi cần redirect sau POST: return `redirect:` hoặc `RedirectView` kết hợp `RedirectAttributes`.
- Khi giao diện là SPA được phục vụ tĩnh hoặc qua CDN: Spring MVC có thể chỉ làm backend API; không bắt buộc render template phía server.

Đừng đẩy truy vấn database, gọi dịch vụ ngoài, cập nhật nghiệp vụ hay điều kiện phân quyền phức tạp vào template. Controller nên phối hợp, service xử lý nghiệp vụ, model chuẩn bị dữ liệu trình bày, view chỉ render. Sự phân chia này làm code dễ kiểm thử hơn và giảm lỗi bảo mật.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Controller `@Controller` trả `"profile"` và thêm `user` vào `Model`. Trình duyệt có nhận đúng chuỗi `profile` làm body không?  
**Giải đáp:** Thường không. Spring hiểu chuỗi đó là logical view name, gọi view resolution và render bằng model. Body cuối cùng do view tạo ra.

**Câu 2.** `ModelAndView` có tự biên dịch Thymeleaf template không?  
**Giải đáp:** Không. Nó mang thông tin view và model; `ViewResolver` chọn `View`, sau đó view cùng template engine thực hiện rendering.

**Câu 3.** `@RestController` trả `"books"` thì `books.html` có được render không?  
**Giải đáp:** Không theo cơ chế mặc định; `@RestController` áp dụng `@ResponseBody`, nên chuỗi được ghi vào response body.

**Câu 4.** `GET /books` trả 404, vậy chắc chắn `ViewResolver` sai?  
**Giải đáp:** Không. Có thể request không khớp handler/mapping. Phải kiểm tra routing trước rồi mới đến view resolution.

**Câu 5.** Spring Boot có Thymeleaf starter thì có cần tự tạo một `InternalResourceViewResolver` cho `books.html` không?  
**Giải đáp:** Không. Với thiết lập Thymeleaf mặc định, Boot tự cấu hình resolver phù hợp và nhận template trong `src/main/resources/templates`.

**Câu 6.** `redirect:/books` và `forward:/books` khác nhau cốt lõi thế nào?  
**Giải đáp:** Redirect phản hồi yêu cầu client gửi request mới tới URL đích; forward chuyển request nội bộ trong server khi điều kiện Servlet cho phép.

**Câu 7.** Dữ liệu của `Model` trong request POST có tự còn trong `Model` sau redirect không?  
**Giải đáp:** Không mặc định. Có thể dùng flash attributes để chuyển thông báo tạm thời hoặc nạp lại dữ liệu ở request GET đích.

**Câu 8.** Có thể đặt `InternalResourceViewResolver` ở đầu chuỗi nhiều resolver không?  
**Giải đáp:** Không nên. JSP resolver thường không thể biết chắc tài nguyên có tồn tại trước khi forward, nên nên được đặt cuối để các resolver chuyên biệt có cơ hội xử lý.

## 14. Tài liệu tham khảo chính thức

1. [Spring Framework — DispatcherServlet](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html): front controller và chiến lược MVC.
2. [Spring Framework — View Resolution](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/viewresolver.html): `ViewResolver`, `View`, chuỗi resolver, redirect và forward.
3. [Spring Framework — Return Values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html): các kiểu giá trị controller được Spring MVC hỗ trợ.
4. [Spring Framework — Redirect Attributes / Flash Attributes](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/redirecting-passing-data.html): chuyển dữ liệu khi redirect.
5. [Spring Boot — Servlet Web Applications](https://docs.spring.io/spring-boot/reference/web/servlet.html): auto-configuration của template engines và vị trí template trong Boot.

**Ghi nhớ:** Trong request render HTML phía server, controller chuẩn bị **model** và chỉ định **view**; `ModelAndView` có thể gói hai thứ đó, `ViewResolver` tìm đối tượng `View`, và `View.render` tạo HTTP response. Với `@ResponseBody`/`@RestController`, Spring ghi dữ liệu trực tiếp qua cơ chế message conversion và thường không đi qua view resolver.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-request-lifecycle-006">HandlerExceptionResolver và luồng xử lý khi có ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# HandlerExceptionResolver và luồng xử lý khi có ngoại lệ trong Spring MVC

## 1. Mục tiêu và vị trí của cơ chế xử lý lỗi

Trong Spring Web MVC, một request hợp lệ có thể thất bại ở nhiều giai đoạn: tìm handler, chuyển tham số, đọc JSON, validation, gọi service, thực thi controller, ghi phản hồi hoặc kết xuất view. **HandlerExceptionResolver** là điểm mở rộng của Spring MVC để biến những ngoại lệ được `DispatcherServlet` bắt được thành một kết quả xử lý HTTP có ý nghĩa.

Bài này trả lời các câu hỏi:

- Ngoại lệ đi qua những bước nào trước khi tới `HandlerExceptionResolver`?
- Khi nào `@ExceptionHandler` trong controller được gọi, khi nào `@ControllerAdvice` được gọi?
- `ExceptionHandlerExceptionResolver`, `ResponseStatusExceptionResolver`, `DefaultHandlerExceptionResolver` khác nhau thế nào?
- Kết quả trả về `null`, `ModelAndView` rỗng hoặc có view mang ý nghĩa gì?
- Làm sao tạo lỗi REST API thống nhất bằng `ResponseEntity`/`ProblemDetail` và kiểm thử đúng HTTP status, body?
- Những ngoại lệ nào **không** đi qua chuỗi resolver MVC, hoặc xảy ra khi phản hồi HTTP đã được gửi?

**Phạm vi:** Spring Framework 6.x/Spring Boot 3.x, Java 17+, web stack Spring MVC (Servlet). WebFlux là stack khác, sử dụng `WebExceptionHandler` và các cơ chế xử lý lỗi reactive tương ứng.

## 2. Nơi phát sinh ngoại lệ trong luồng MVC

Hình dưới mô tả trường hợp lỗi xảy ra **trong phạm vi request mà `DispatcherServlet` đang xử lý**:

```text
HTTP client
   |
   v
Servlet container (Tomcat)
   |
   v
Servlet Filter chain
   |
   v
DispatcherServlet
   |
   +--> HandlerMapping        --- lỗi tìm handler/mapping?
   |
   +--> HandlerAdapter
   |      |
   |      +--> argument resolver / binding / validation --- lỗi 400?
   |      |
   |      +--> @Controller -> service/repository -------- lỗi nghiệp vụ?
   |      |
   |      +--> return value / message converter --------- lỗi serialize?
   |
   +-- ngoại lệ được DispatcherServlet bắt trong vùng xử lý
          |
          v
   Danh sách HandlerExceptionResolver (theo order)
          |
          +--> resolver 1: xử lý được? -> response/view
          +--> resolver 2: xử lý được? -> response/view
          +--> resolver 3: xử lý được? -> response/view
          |
          +--> không resolver nào xử lý -> exception thoát ra
                    |
                    v
            Servlet container / error dispatch
```

Khi phần xử lý trong `DispatcherServlet` ném lỗi, Servlet sẽ thử các `HandlerExceptionResolver` đã được đăng ký. Resolver có thể đặt HTTP status, header, body, trả về view lỗi hoặc cho resolver tiếp theo thử.

**Không phải mọi ngoại lệ trong toàn tiến trình đều đi vào đây.** Ví dụ filter nằm trước `DispatcherServlet` ném lỗi khi chưa gọi servlet, lỗi xảy ra trong lúc ứng dụng startup, hay lỗi nền ở scheduler/`@Async` bên ngoài request MVC sẽ cần cơ chế khác. Đây là ranh giới đầu tiên để chẩn đoán chính xác.

## 3. Hợp đồng của HandlerExceptionResolver

Interface quan trọng nằm tại `org.springframework.web.servlet.HandlerExceptionResolver`. Phương thức cốt lõi có dạng:

```java
ModelAndView resolveException(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
);
```

Đây là **chữ ký minh họa từ API**, không phải một chương trình Java độc lập. `handler` có thể là thông tin handler mà framework xác định được; không nên mặc định nó luôn là một phương thức controller đã tìm thấy.

Giá trị trả về có ba ý nghĩa rõ ràng:

| Trả về | Ý nghĩa |
| --- | --- |
| `null` | Resolver này **chưa giải quyết ngoại lệ**; `DispatcherServlet` thử resolver tiếp theo. |
| `new ModelAndView()` rỗng | Ngoại lệ **đã được xử lý**, nhưng không cần render một view MVC; ví dụ resolver đã thiết lập status/body trực tiếp. |
| `ModelAndView` chứa view và model | Ngoại lệ **đã được xử lý** bằng cách render view lỗi, phổ biến trong MVC trả HTML. |

**Lỗi tư duy phổ biến:** `null` không nghĩa là “trả response rỗng”; nó nghĩa là *để resolver khác thử*. Ngược lại, một `ModelAndView` rỗng không yêu cầu framework tiếp tục dò resolver.

Chuỗi resolver có thứ tự (`Ordered`/`@Order` hoặc cấu hình MVC). **Resolver đầu tiên trả kết quả khác `null` sẽ quyết định hướng xử lý**. Vì thế việc viết một custom resolver bắt mọi `Exception` ở ưu tiên quá cao có thể vô tình cướp quyền xử lý của `@ExceptionHandler` và các resolver mặc định.

## 4. Ba resolver tích hợp quan trọng nhất

Khi dùng cấu hình MVC tiêu chuẩn, Spring đăng ký các resolver mặc định; cấu hình thực tế có thể đặt chúng trong `HandlerExceptionResolverComposite`, với thứ tự thông dụng như sau:

| Resolver | Trách nhiệm | Ví dụ |
| --- | --- | --- |
| **`ExceptionHandlerExceptionResolver`** | Tìm và gọi phương thức `@ExceptionHandler` trong controller hoặc controller advice | Chuyển `ProductNotFoundException` thành JSON lỗi 404. |
| **`ResponseStatusExceptionResolver`** | Ánh xạ `@ResponseStatus` và `ResponseStatusException` sang HTTP status / lý do | Một exception được gắn `@ResponseStatus(HttpStatus.NOT_FOUND)`. |
| **`DefaultHandlerExceptionResolver`** | Xử lý ngoại lệ chuẩn do Spring MVC phát sinh và ánh xạ HTTP status/header theo Servlet behavior | Sai HTTP method (405), thiếu tham số, lỗi đọc dữ liệu, v.v. |

Ngoài ra, `SimpleMappingExceptionResolver` có thể ánh xạ loại exception sang tên view lỗi để render HTML trong ứng dụng MVC truyền thống; nó không mặc nhiên là thành phần thích hợp nhất cho REST JSON API.

**Thứ tự này không có nghĩa “mọi exception đều lần lượt được ba lớp xử lý”.** Nếu resolver đầu tiên đã xử lý, các resolver còn lại không được hỏi. Nếu một ứng dụng định nghĩa resolver hoặc tùy biến cấu hình MVC, thứ tự cụ thể cần xem lúc runtime.

### 4.1. ExceptionHandlerExceptionResolver

Đây là bộ máy đằng sau `@ExceptionHandler`. Nó tìm method xử lý phù hợp theo loại ngoại lệ, khai báo controller cục bộ, các advice áp dụng được, và thứ tự advice. Method có thể trả `ResponseEntity`, `ProblemDetail`, body object, `ModelAndView`, tên view và các kiểu trả về MVC hợp lệ khác.

Nó là lựa chọn phổ biến khi cần biến **lỗi nghiệp vụ** thành HTTP contract ổn định: status, error code, message an toàn, correlation ID và những header hợp lý.

### 4.2. ResponseStatusExceptionResolver

Resolver này diễn giải các trường hợp như:

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
class BookNotFoundException extends RuntimeException {
}
```

Hoặc ném `ResponseStatusException` tại một điểm xử lý:

```java
throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Không tìm thấy sách"
);
```

Hai đoạn là **mã minh họa đặt trong ứng dụng đã import Spring HTTP/web classes**. Chúng làm rõ ý định status HTTP nhanh chóng, nhưng không tự bảo đảm một JSON error body thống nhất trong mọi cấu hình/container.

Nếu một `@ExceptionHandler` phù hợp đã bắt ngoại lệ trước, phương thức xử lý đó có thể quyết định response thay cho resolver status. Không giả định `@ResponseStatus` luôn có ưu tiên tuyệt đối.

### 4.3. DefaultHandlerExceptionResolver

Spring MVC phát sinh các ngoại lệ đặc thù cho protocol/mapping, ví dụ:

- `HttpRequestMethodNotSupportedException` khi HTTP method không được hỗ trợ; thường là **405 Method Not Allowed**, có thể kèm header `Allow`.
- `HttpMediaTypeNotSupportedException` khi request gửi `Content-Type` không phù hợp; thường **415 Unsupported Media Type**.
- `HttpMediaTypeNotAcceptableException` khi không thể tạo representation đúng `Accept`; thường **406 Not Acceptable**.
- `MissingServletRequestParameterException` nếu thiếu `@RequestParam(required=true)`; thường **400 Bad Request**.
- `MethodArgumentTypeMismatchException` hoặc các lỗi chuyển đổi type liên quan; thường dẫn tới **400**, nhưng body cụ thể do handler/advice/config quyết định.
- `HttpMessageNotReadableException` khi JSON không parse được; thường **400 Bad Request**.

Nhiều lỗi framework còn có đường đi qua `ResponseEntityExceptionHandler` hoặc `ErrorResponse` để tạo nội dung HTTP chi tiết. Tại runtime, resolver nào được ưu tiên có thể thay đổi response body, nhưng **HTTP status đúng chưa đồng nghĩa JSON body chuẩn hóa**. Có trường hợp resolver chỉ đặt status hay gọi `sendError`, rồi container/Boot điều hướng tới `/error`.

## 5. @ExceptionHandler trong controller: phạm vi cục bộ

Một controller có thể khai báo `@ExceptionHandler` để xử lý ngoại lệ của chính nó:

```java
@RestController
@RequestMapping("/api/invoices")
class InvoiceController {

    @GetMapping("/{id}")
    String byId(@PathVariable long id) {
        if (id == 9) {
            throw new IllegalArgumentException("Mã hóa đơn không hợp lệ");
        }
        return "invoice-" + id;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> onInvalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
```

Đây là **đoạn code controller minh họa**; cần import `@RestController`, `@RequestMapping`, `@GetMapping`, `@PathVariable`, `@ExceptionHandler` và `ResponseEntity` từ Spring. Với `GET /api/invoices/9`, response body là thông báo lỗi và status 400.

Ưu điểm: quy tắc riêng rất gần controller. Nhược điểm: nếu lặp cùng error response ở nhiều controller, mã không nhất quán và khó bảo trì. Khi nhiều controller dùng cùng contract JSON lỗi, nên chuyển quy tắc chung sang `@RestControllerAdvice`.

### Ưu tiên local so với global

Khi cùng một ngoại lệ có handler phù hợp trong controller và trong `@ControllerAdvice` áp dụng cho controller đó, **handler cục bộ được ưu tiên**. Các advice áp dụng toàn cục chỉ được xét khi không có local handler phù hợp.

Trong một controller/advice, độ cụ thể của exception type và matching trên nguyên nhân (`cause`) ảnh hưởng việc chọn method. **Không** tạo hai `@ExceptionHandler` mơ hồ cho cùng exception trong một advice: Spring có thể báo ambiguity khi khởi tạo resolver.

## 6. @ControllerAdvice và @RestControllerAdvice

`@ControllerAdvice` là Spring component chứa logic áp dụng cho nhiều controller, trong đó có `@ExceptionHandler`. `@RestControllerAdvice` tương đương `@ControllerAdvice` kết hợp `@ResponseBody`: các object trả về thường được serialize vào HTTP body thay vì xem là tên view.

| Annotation/cách dùng | Phạm vi | Dạng phản hồi |
| --- | --- | --- |
| `@ExceptionHandler` trong `@Controller` | Controller hiện tại | View, body hoặc `ResponseEntity` theo signature. |
| `@ControllerAdvice` + `@ExceptionHandler` | Nhiều controller được chọn | Phù hợp HTML view hoặc response body khi có `@ResponseBody`/`ResponseEntity`. |
| `@RestControllerAdvice` + `@ExceptionHandler` | Nhiều REST controller được chọn | Trả object/JSON lỗi thuận tiện. |
| `ResponseEntityExceptionHandler` | Base class dùng trong advice | Đã xử lý nhóm exception Spring MVC; cung cấp hook override để thống nhất response. |

Có thể giới hạn advice bằng thuộc tính như `basePackages`, `basePackageClasses`, `annotations`, `assignableTypes`. Không nên tạo một advice chung bắt mọi exception rồi biến mọi lỗi thành 200 hoặc 400.

### Nhiều ControllerAdvice và thứ tự

Spring sắp xếp controller advice theo `Ordered`/`@Order`. Advice ưu tiên cao hơn được xét trước. Trong **cùng advice**, root exception match thường ưu tiên cause match; nhưng **cause match của advice có thứ tự ưu tiên cao** có thể thắng root match ở advice ưu tiên thấp hơn.

Ví dụ dễ hiểu:

- Advice A có `@Order(1)`, bắt `IllegalArgumentException` nằm trong cause chain.
- Advice B có `@Order(10)`, bắt exception ngoài cùng cụ thể hơn.
- Khi A áp dụng và tìm thấy handler phù hợp, A có thể được dùng trước B dù B bắt root exception.

Vì vậy hãy để mapping lỗi nghiệp vụ **cụ thể** trong một advice có phạm vi và order rõ ràng; tránh nhiều advice bắt `Exception.class` ở ưu tiên cao.

## 7. ProblemDetail: một HTTP error contract rõ ràng

Thay vì trả chuỗi lỗi tùy ý hoặc một `Map` với các key không ổn định, Spring Framework 6 hỗ trợ `ProblemDetail` theo chuẩn **RFC 9457** (Problem Details for HTTP APIs).

Một problem response có các trường tiêu chuẩn:

| Trường | Ý nghĩa |
| --- | --- |
| `type` | URI định danh loại vấn đề; mặc định có thể là `about:blank`. |
| `title` | Tiêu đề ngắn của loại vấn đề. |
| `status` | Mã HTTP status tương ứng. |
| `detail` | Giải thích ngắn về trường hợp cụ thể. |
| `instance` | URI định danh request/problem instance khi được cung cấp hoặc suy ra. |
| Extension như `code` | Mã lỗi riêng của hệ thống để client xử lý ổn định. |

Định dạng media type điển hình là `application/problem+json`. JSON có thể chứa thêm trường `instance` hoặc extension tùy cách controller/converter xây response. Client nên dựa vào **HTTP status + mã lỗi ổn định**, không parse từng câu chữ `detail`.

Ví dụ error response minh họa:

```json
{
  "type": "about:blank",
  "title": "Không tìm thấy sản phẩm",
  "status": 404,
  "detail": "Không tìm thấy sản phẩm 42",
  "code": "PRODUCT_NOT_FOUND"
}
```

**Không đưa stack trace, connection string, đường dẫn file hệ thống, SQL nội bộ hoặc dữ liệu riêng tư vào `detail` của lỗi 500.** Log nội bộ có thể giữ thông tin kỹ thuật với quyền truy cập thích hợp và gắn request/correlation ID.

## 8. Bài lab chạy được: Spring Boot + MVC + @RestControllerAdvice

Ta tạo một ứng dụng Spring Boot **3.5.16**, Java 17+, Maven. API có ba loại phản hồi dễ kiểm chứng:

- `GET /api/products/1`: thành công 200.
- `GET /api/products/42`: ném ngoại lệ nghiệp vụ, advice chuyển thành 404 với `ProblemDetail`.
- `GET /api/products/0`: ném `IllegalArgumentException`, advice trả 400.
- `GET /api/products/500`: mô phỏng lỗi nội bộ, advice trả 500 với thông tin công khai đã làm sạch.

Ngoài ra, `GET /api/products/search` khi thiếu tham số `term` tạo lỗi MVC `MissingServletRequestParameterException`; custom advice kế thừa `ResponseEntityExceptionHandler` có thể override hook tương ứng.

**Cấu trúc dự án**:

```text
mvc-exception-demo/
├── pom.xml
└── src/
    ├── main/java/example/errors/
    │   ├── ExceptionDemoApplication.java
    │   ├── ProductController.java
    │   ├── ProductNotFoundException.java
    │   └── ApiExceptionHandler.java
    └── test/java/example/errors/
        └── ExceptionFlowTest.java
```

### 8.1. Maven pom.xml

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

    <groupId>example</groupId>
    <artifactId>mvc-exception-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>

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

Không cần thêm thư viện riêng để dùng `@ExceptionHandler` hoặc `ProblemDetail`: cả hai có trong Spring Web MVC đã được starter kéo vào.

### 8.2. Điểm khởi động

**`src/main/java/example/errors/ExceptionDemoApplication.java`**:

```java
package example.errors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExceptionDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExceptionDemoApplication.class, args);
    }
}
```

`@SpringBootApplication` bật auto-configuration và component scan trong package `example.errors`, vì vậy controller và advice bên dưới sẽ được tìm thấy.

### 8.3. Ngoại lệ miền nghiệp vụ

**`src/main/java/example/errors/ProductNotFoundException.java`**:

```java
package example.errors;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(long id) {
        super("Không tìm thấy sản phẩm " + id);
    }
}
```

Ngoại lệ này biểu diễn lỗi domain “không tìm thấy sản phẩm”; nó **không gắn cứng Servlet hay HTTP status**. Tầng API quyết định ánh xạ sang 404 bằng advice. Nhờ vậy domain không bắt buộc phụ thuộc `ResponseStatusException` của Spring Web.

### 8.4. Controller tạo các trường hợp có lỗi

**`src/main/java/example/errors/ProductController.java`**:

```java
package example.errors;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        if (id == 42) {
            throw new ProductNotFoundException(id);
        }
        if (id <= 0) {
            throw new IllegalArgumentException("id phải lớn hơn 0");
        }
        if (id == 500) {
            throw new IllegalStateException("Chi tiết hệ thống nội bộ");
        }
        return Map.of("id", id, "name", "Notebook");
    }

    @GetMapping("/search")
    public Map<String, String> search(@RequestParam String term) {
        return Map.of("term", term);
    }
}
```

Khi gọi `/api/products/42`, method đã được mapping và gọi tới nhánh ném `ProductNotFoundException`. `DispatcherServlet` giao lỗi cho chuỗi resolver; `ExceptionHandlerExceptionResolver` có thể tìm `@ExceptionHandler(ProductNotFoundException.class)`.

Khi gọi `/api/products/search` thiếu `term`, exception nảy sinh ở **argument binding** trước khi method `search` thực thi. Nhưng vì lỗi vẫn thuộc xử lý MVC, nó có thể được xử lý qua `ResponseEntityExceptionHandler` trong advice.

### 8.5. Advice chứa error contract

**`src/main/java/example/errors/ApiExceptionHandler.java`**:

```java
package example.errors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(ProductNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm",
                ex.getMessage(), "PRODUCT_NOT_FOUND");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> invalid(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ",
                ex.getMessage(), "INVALID_PRODUCT_ID");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> unexpected(IllegalStateException ex) {
        log.error("Xảy ra lỗi nội bộ khi xử lý sản phẩm", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi máy chủ",
                "Không thể hoàn tất yêu cầu", "INTERNAL_ERROR");
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                status, "Thiếu tham số: " + ex.getParameterName());
        body.setTitle("Thiếu tham số bắt buộc");
        body.setProperty("code", "MISSING_PARAMETER");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String title, String detail, String code) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
```

Giải thích:

- `@RestControllerAdvice` khiến các handler lỗi chung áp dụng cho controller phù hợp và phản hồi qua message converters.
- Handler `ProductNotFoundException` trả **404** và code ổn định `PRODUCT_NOT_FOUND`; `IllegalArgumentException` trả **400**.
- Handler `IllegalStateException` log stack trace nội bộ, nhưng trả thông điệp công khai đã làm sạch với **500**. Trong ứng dụng thực, loại exception này chỉ là mô phỏng, và không nên mặc định mọi `IllegalStateException` luôn là lỗi 500 nếu business domain có ngữ nghĩa khác.
- Vì advice **extends `ResponseEntityExceptionHandler`**, muốn sửa phản hồi cho `MissingServletRequestParameterException` ta **override** `handleMissingServletRequestParameter`. Không thêm một `@ExceptionHandler(MissingServletRequestParameterException.class)` riêng trong cùng advice: base class đã đăng ký nhóm exception đó, có thể gây mapping mơ hồ.
- `handleExceptionInternal` giúp giữ cơ chế xử lý response và header framework cho ngoại lệ MVC, thay vì bỏ qua các contract mặc định.
- Đoạn mẫu cố tình **không bật** auto-configuration `spring.mvc.problemdetails.enabled=true`; các endpoint trả `ProblemDetail` trực tiếp vẫn hoạt động. Nếu dùng setting đó, cần đánh giá thêm advice mặc định và thứ tự ưu tiên tương ứng.

Các handler cụ thể sẽ được Spring chọn theo exception type. Trong ví dụ không dùng catch-all `@ExceptionHandler(Exception.class)`, vì catch-all quá rộng dễ che lấp lỗi protocol do framework đã hỗ trợ.

### 8.6. Chạy và quan sát

Từ thư mục chứa `pom.xml`:

```bash
java -version
mvn -version
mvn test
mvn spring-boot:run
```

Giữ ứng dụng chạy ở terminal thứ nhất. Trên terminal thứ hai:

```bash
# 200: sản phẩm hợp lệ
curl -i http://localhost:8080/api/products/1

# 404: sản phẩm không tồn tại
curl -i -H "Accept: application/problem+json" \
  http://localhost:8080/api/products/42

# 400: id không hợp lệ
curl -i -H "Accept: application/problem+json" \
  http://localhost:8080/api/products/0

# 500: lỗi mô phỏng và body công khai đã làm sạch
curl -i -H "Accept: application/problem+json" \
  http://localhost:8080/api/products/500

# 400: thiếu @RequestParam term (framework binding error)
curl -i -H "Accept: application/problem+json" \
  http://localhost:8080/api/products/search
```

Dấu `\` để nối lệnh dùng shell kiểu Bash; trong Windows PowerShell có thể viết mỗi `curl.exe` trên một dòng đầy đủ.

Bảng kết quả mong đợi:

| Request | HTTP status | Dấu hiệu trong body |
| --- | --- | --- |
| `GET /api/products/1` | **200** | JSON có `id=1`, `name="Notebook"`. |
| `GET /api/products/42` | **404** | `code="PRODUCT_NOT_FOUND"`, `detail="Không tìm thấy sản phẩm 42"`. |
| `GET /api/products/0` | **400** | `code="INVALID_PRODUCT_ID"`. |
| `GET /api/products/500` | **500** | `code="INTERNAL_ERROR"`; **không** chứa chuỗi lỗi nội bộ. |
| `GET /api/products/search` | **400** | `code="MISSING_PARAMETER"`, `detail` nhắc tên `term`. |

Không khẳng định chính xác toàn bộ JSON field order hay mọi header của mọi môi trường: serializer/container có thể bổ sung `instance`, header hoặc trường tiêu chuẩn theo configuration. Điều cần kiểm chứng là status, mã lỗi và các trường contract ổn định.

### 8.7. Kiểm thử bằng MockMvc

**`src/test/java/example/errors/ExceptionFlowTest.java`**:

```java
package example.errors;

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
class ExceptionFlowTest {

    @Autowired
    MockMvc mvc;

    @Test
    void productExists() throws Exception {
        mvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Notebook"));
    }

    @Test
    void domainExceptionBecomes404() throws Exception {
        mvc.perform(get("/api/products/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void missingParamIsHandledByBaseClassOverride() throws Exception {
        mvc.perform(get("/api/products/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }

    @Test
    void internalMessageIsHiddenFromClient() throws Exception {
        mvc.perform(get("/api/products/500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail")
                        .value("Không thể hoàn tất yêu cầu"));
    }
}
```

Chạy:

```bash
mvn test
```

Đây là integration test chạy MVC stack trong test ApplicationContext; `MockMvc` không cần khởi động listener HTTP thật. Test chứng minh cả đường success, domain exception, framework binding exception, và chính sách không lộ thông tin nội bộ. Nếu hệ thống có Spring Security filter, authentication/authorization của test cần được cấu hình phù hợp; không nên tắt bảo mật chỉ để test xử lý lỗi.

## 9. Theo dõi từng request trong bài lab

### Trường hợp A: lỗi nghiệp vụ `/api/products/42`

```text
GET /api/products/42
  -> DispatcherServlet
  -> HandlerMapping: ProductController.get(...)
  -> @PathVariable chuyển thành long 42
  -> controller ném ProductNotFoundException
  -> DispatcherServlet gọi chain HandlerExceptionResolver
  -> ExceptionHandlerExceptionResolver:
       tìm @ExceptionHandler(ProductNotFoundException.class)
       gọi ApiExceptionHandler.notFound(...)
  -> ResponseEntity<ProblemDetail>
  -> Jackson serialize body
  -> HTTP 404 + JSON problem response
```

Không cần `try/catch` trong mọi controller. Chính `DispatcherServlet` phối hợp resolver theo contract MVC.

### Trường hợp B: lỗi mapping tham số `/api/products/search`

```text
GET /api/products/search
  -> HandlerMapping: ProductController.search(...)
  -> HandlerAdapter chuẩn bị @RequestParam term
  -> thiếu term -> MissingServletRequestParameterException
  -> ExceptionHandlerExceptionResolver:
       gọi advice kế thừa ResponseEntityExceptionHandler
       hook handleMissingServletRequestParameter(...)
  -> HTTP 400 + ProblemDetail
```

Điểm quan trọng: logic controller `search` **chưa được gọi** khi binding đã thất bại. Ngoại lệ này không phải lỗi business service.

### Trường hợp C: không có handler

`GET /does-not-exist` có thể dẫn đến 404 theo cấu hình mapping/static-resource/Servlet container. Tùy dòng Spring Boot, cấu hình static resources và `DispatcherServlet`, ứng dụng có thể sử dụng `NoHandlerFoundException`, `NoResourceFoundException` hoặc Boot error handling fallback.

**Đừng giả định `@ExceptionHandler(ProductNotFoundException.class)` sẽ xử lý toàn bộ 404.** Một 404 “không tìm thấy route” có nguồn gốc khác lỗi domain “không tìm thấy bản ghi”.

## 10. ResponseEntityExceptionHandler và ErrorResponse

Spring Framework 6 cung cấp `ErrorResponse` cho exception mang thông tin HTTP status, headers và `ProblemDetail` body. `ResponseEntityExceptionHandler` là lớp base giúp xử lý nhóm lỗi Spring MVC và `ErrorResponseException`, có các phương thức `handle...` để tùy chỉnh.

Các hook thường cần biết:

- `handleMethodArgumentNotValid(...)`: lỗi Bean Validation khi binding `@Valid @RequestBody`.
- `handleHttpMessageNotReadable(...)`: request body JSON sai/không đọc được.
- `handleMissingServletRequestParameter(...)`: thiếu query/form parameter.
- `handleTypeMismatch(...)`: chuyển kiểu tham số không thành công.
- `handleHttpRequestMethodNotSupported(...)`: method không được hỗ trợ.
- `handleHttpMediaTypeNotSupported(...)`: request content type không phù hợp.
- `handleExceptionInternal(...)`: xây response nội bộ chung và giữ metadata thích hợp.

Chữ ký override phụ thuộc thế hệ Spring; ví dụ Spring 6 thường nhận `HttpStatusCode` thay vì `HttpStatus` ở hook liên quan. **Luôn đối chiếu chữ ký đúng bản Spring**, dùng `@Override` để compiler phát hiện thay đổi.

### Không thay mọi lỗi framework thành một JSON 400 tùy ý

Một HTTP 405 phải giữ ý nghĩa “method không được hỗ trợ”; 415 phải giữ ý nghĩa Content-Type, 406 là Accept negotiation, và 500 là server error. Nếu override hook, thường dùng `status` framework truyền vào và giữ những header bắt buộc như `Allow`. Đừng tự suy tất cả request lỗi đều là 400.

Ví dụ pseudo-code mô tả cách override có ý thức:

```java
// Minh họa bên trong subclass ResponseEntityExceptionHandler.
// Không phải chương trình Java độc lập.
@Override
protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
        HttpRequestMethodNotSupportedException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(
            status, "HTTP method không được hỗ trợ");
    body.setProperty("code", "METHOD_NOT_ALLOWED");
    return handleExceptionInternal(ex, body, headers, status, request);
}
```

Ta dùng `headers` do framework truyền vào thay vì bỏ qua `Allow`. Những import và base class giống phần lab trước, bổ sung `HttpRequestMethodNotSupportedException` tương ứng.

## 11. Không nhầm @ResponseStatus với response body API

Ví dụ:

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
class ResourceGoneException extends RuntimeException {
    ResourceGoneException(String message) {
        super(message);
    }
}
```

Annotation trên exception nói loại lỗi nên ánh xạ sang status nào. Nó phù hợp với một số trường hợp ngắn gọn, nhưng không tự bảo đảm body có `code`, `type`, `detail` và contract JSON theo thiết kế của API.

Với REST API cần error schema nhất quán, `@RestControllerAdvice` + `ResponseEntity<ProblemDetail>` thường dễ kiểm soát hơn. Với request-specific error status nhỏ, `ResponseStatusException` cho phép ném status ngay tại chỗ, nhưng cần đánh giá cách body được dựng trong cấu hình Spring Boot hiện tại.

**Sai lầm:** cho rằng mọi `@ResponseStatus` exception đều chạy qua `ResponseStatusExceptionResolver` bất kể controller advice. Nếu `@ExceptionHandler` xử lý được ngoại lệ trước, resolver status sẽ không được gọi.

## 12. Mối quan hệ với Spring Boot /error và Servlet ERROR dispatch

Có **hai tầng** thường bị gọi lẫn nhau:

1. **Spring MVC exception resolution**: `DispatcherServlet` thử `HandlerExceptionResolver`. Kết quả có thể đã ghi JSON/body/view hoặc chỉ đặt status và coi exception đã giải quyết.
2. **Servlet container error handling**: khi exception không được MVC xử lý hoặc Servlet gửi/đánh dấu error, container có thể thực hiện **ERROR dispatch** sang trang lỗi. Spring Boot trên Servlet stack thường cấu hình endpoint `/error` (ví dụ `BasicErrorController`) làm fallback.

```text
Ngoại lệ xảy ra trong DispatcherServlet
    |
    +-- HandlerExceptionResolver xử lý
    |      |
    |      +-- response body + status -> client
    |
    +-- không xử lý / error dispatch xảy ra
           |
           v
        container ERROR dispatch
           |
           v
        /error của Boot hoặc error page cấu hình
           |
           v
        response HTML/JSON theo request và config
```

Cấu trúc này giải thích vì sao một API đôi khi trả JSON `ProblemDetail` do advice tạo, nhưng request khác trả JSON mặc định Boot hoặc HTML error page. Đó là **đường đi khác nhau**, không nhất thiết là “Spring phớt lờ handler”.

Tuy nhiên, **không phải mọi response status 4xx/5xx đều chắc chắn kích hoạt `/error`**. Nếu advice đã tự dựng response với status và body, container thường gửi response đó. Cơ chế error dispatch phụ thuộc `sendError`, exception propagation, container và response state; cần quan sát thực tế.

### Bật Problem Details toàn cục trong Boot

Spring Boot có tùy chọn:

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

Tùy chọn này hỗ trợ định dạng Problem Details cho những lỗi MVC được framework xử lý. **Không cần bật** nếu bạn trả `ProblemDetail` trực tiếp trong `@ExceptionHandler` như bài lab. Khi kết hợp với custom `@ControllerAdvice` kế thừa `ResponseEntityExceptionHandler`, cần đánh giá độ ưu tiên và coverage để tránh các handler cạnh tranh hoặc body không thống nhất.

## 13. Những lỗi không nên xử lý bằng HandlerExceptionResolver MVC

| Nguồn lỗi | Vì sao có thể nằm ngoài chuỗi MVC? | Nơi cần xử lý |
| --- | --- | --- |
| Servlet `Filter` ném exception trước khi gọi `DispatcherServlet` | Servlet chưa nhận quyền điều phối request | Filter error policy, Servlet error dispatch; với Spring Security dùng security exception handling/filter chain. |
| Spring Security authentication/authorization | Thường được kiểm soát bởi `ExceptionTranslationFilter` và `AuthenticationEntryPoint`/`AccessDeniedHandler` | Cấu hình Spring Security tương ứng; 401 và 403 đúng contract. |
| `@Async` fire-and-forget | Không có request MVC đang chờ exception | `AsyncUncaughtExceptionHandler`, future completion, logging/observability. |
| `@Scheduled` task | Chạy trên scheduler thread, không có HTTP request | Error handler của scheduler, retries/alert. |
| Lỗi startup/config/database connection lúc khởi tạo | App context chưa sẵn sàng phục vụ HTTP | Startup logs, health/readiness, deploy fail. |
| Response đã committed rồi mới lỗi | Header/status/body đã gửi một phần; không thể thay phản hồi nguyên vẹn | Log, close connection, thiết kế streaming/transaction boundary, metrics. |

**Ngoại lệ từ async MVC request khác với lỗi nền `@Async` độc lập.** Với `Callable`/`DeferredResult`/`WebAsyncTask`, lỗi có thể được tái dispatch về Spring MVC để xử lý bằng resolver trên pha async dispatch. Cần test trường hợp cụ thể, nhất là khi response đã committed hoặc timeout.

## 14. Các lỗi thiết kế và bẫy thực tế

### Bẫy 1: catch Exception rồi trả HTTP 200

```java
// Ví dụ SAI: client không biết yêu cầu thất bại.
try {
    return service.load(id);
} catch (Exception ex) {
    return "ERROR";
}
```

Client HTTP nhìn thấy 200 có thể coi request thành công, retry/caching/monitoring sai. Nên để domain exception đi qua MVC handler và trả status đúng, hoặc chủ động `ResponseEntity.status(...)` cho một nhánh được thiết kế rõ.

### Bẫy 2: một global handler bắt tất cả và trả 400

```java
// Ví dụ SAI: che lỗi server, protocol 405/415, v.v.
@ExceptionHandler(Exception.class)
ResponseEntity<String> everything(Exception ex) {
    return ResponseEntity.badRequest().body(ex.getMessage());
}
```

Lỗi nội bộ bị gắn 400, dữ liệu nhạy cảm trong message bị lộ; các handler framework cụ thể có thể bị che hoặc gây bất ngờ trong resolver ordering. Nếu cần fallback 500, viết mapping rõ ràng, giữ log/correlation ID, đồng thời bảo đảm các lỗi chuẩn đã được xử lý đúng.

### Bẫy 3: thêm @ExceptionHandler trùng base class

Khi `extends ResponseEntityExceptionHandler`, base class có những mapping `@ExceptionHandler` cho nhóm framework exceptions. Nếu subclass thêm `@ExceptionHandler(HttpMessageNotReadableException.class)` thay vì override `handleHttpMessageNotReadable`, có thể gây ambiguous exception handler method. Với exception Spring MVC có sẵn hook, dùng **override hook**.

### Bẫy 4: xử lý HTML view trong REST advice

`@ControllerAdvice` trả một chuỗi `"error"` có thể được coi là tên view, không phải JSON body; `@RestControllerAdvice` hoặc `@ResponseBody` cùng `ResponseEntity` giúp biểu thị rõ kiểu phản hồi.

### Bẫy 5: nhầm null ModelAndView với handled

Trong custom resolver, trả `null` yêu cầu resolver sau tiếp tục. Nếu đã tự ghi status/body nhưng vẫn trả `null`, resolver sau có thể cố viết tiếp, tạo response conflict. Ngược lại trả `new ModelAndView()` để đánh dấu đã xử lý khi response đã được xây đầy đủ.

### Bẫy 6: thử xử lý exception sau response đã gửi

Nếu streaming endpoint đã gửi một phần body, dù advice được gọi, status/headers có thể không sửa được. Không thể biến một response nửa chừng thành JSON lỗi chuẩn như chưa gửi byte nào. Cần thiết kế stream-specific error protocol và logging thích hợp.

### Bẫy 7: nhầm nguyên nhân 404

**404 do route không tồn tại** khác **404 do bản ghi không tồn tại**. Endpoint path sai có thể đi qua resource handler/Boot error fallback, trong khi lỗi domain thường là `ProductNotFoundException` ở controller/service. Test cả hai đường riêng.

### Bẫy 8: bỏ mất thông tin cho client hoặc lộ quá nhiều

Lỗi 400 nên có mã nghiệp vụ ổn định và detail hữu ích (tên field, quy tắc validation) nhưng không tiết lộ dữ liệu nhạy cảm. Lỗi 500 nên trả thông báo chung, không trả stack trace. Backend cần log đủ ngữ cảnh (correlation ID, exception type, relevant request metadata) mà không ghi token/password.

## 15. Chiến lược phân loại HTTP error trong API

| Loại lỗi | HTTP status điển hình | Ví dụ | Handler phù hợp |
| --- | --- | --- | --- |
| Sai input, type, JSON hoặc validation | 400 | `id=-1`, JSON malformed | Advice cụ thể hoặc `ResponseEntityExceptionHandler` hook. |
| Chưa xác thực | 401 | Thiếu bearer token | Security filter/entry point. |
| Có danh tính nhưng không có quyền | 403 | Role không đủ | Spring Security access denied handler hoặc policy ứng dụng phù hợp. |
| Tài nguyên nghiệp vụ không có | 404 | Product ID 42 vắng mặt | Domain exception + `@ExceptionHandler`. |
| Method không hỗ trợ | 405 | POST vào GET-only endpoint | Resolver/framework behavior, giữ `Allow`. |
| Conflict với trạng thái hiện tại | 409 | Trùng mã định danh khi tạo entity | Domain conflict exception/advice. |
| Media type không hỗ trợ | 415 | XML gửi tới JSON-only endpoint | Framework mapping/resolver. |
| Validation không thể áp dụng rule nghiệp vụ | 422 khi API contract chọn | Các trường có type hợp lệ nhưng vi phạm rule | Domain exception/advice; chọn status nhất quán với API. |
| Hệ thống thất bại ngoài dự kiến | 500 | Service không hoạt động, bug | Sanitized fallback error, logging. |
| Phụ thuộc upstream không đáp ứng | 502/503/504 tùy vai trò và ngữ cảnh gateway | Dependency timeout/unavailable | Exception translation ở boundary phù hợp; không mặc định mọi timeout = 504. |

Đây là mapping gợi ý; HTTP contract nên được định nghĩa nhất quán giữa client/server. Riêng 401/403, xử lý nằm trong Spring Security filter chain thường quan trọng hơn global MVC advice.

## 16. Khi nào thực sự viết custom HandlerExceptionResolver?

Phần lớn REST API nên bắt đầu bằng:

- `@ExceptionHandler` cục bộ cho hành vi rất riêng.
- `@RestControllerAdvice` cho lỗi domain chung.
- `ResponseEntityExceptionHandler` để chuẩn hóa nhóm framework exceptions.
- `ProblemDetail` để chuẩn hóa body.

Chỉ viết một `HandlerExceptionResolver` riêng khi có yêu cầu rất đặc biệt, chẳng hạn cần tương thích một cơ chế lỗi cũ, mapping theo một handler type cụ thể hoặc tích hợp với một error pipeline có hợp đồng riêng. Nếu viết custom resolver:

1. Chỉ nhận những exception thuộc trách nhiệm của nó; exception khác trả `null`.
2. Kiểm tra `response.isCommitted()` trước khi chỉnh status/header/body.
3. Giữ HTTP status/header chính xác và tránh ghi hai lần.
4. Chọn `order` có chủ đích; không chặn `ExceptionHandlerExceptionResolver` một cách vô tình.
5. Test đồng thời case resolver xử lý và case resolver phải nhường cho default MVC chain.

Trong Spring MVC cấu hình bằng `WebMvcConfigurer`, `extendHandlerExceptionResolvers(...)` cho phép bổ sung vào danh sách hiện có. Còn `configureHandlerExceptionResolvers(...)` có thể thay đổi hoặc tự thiết lập danh sách theo hợp đồng cấu hình; dùng sai có thể làm mất các resolver mặc định. Vì vậy hãy ưu tiên cơ chế annotation sẵn có nếu nhu cầu chỉ là tạo HTTP JSON lỗi.

## 17. Kiểm thử và quan sát trên môi trường thật

### Kiểm thử chức năng

- Endpoint thành công vẫn trả **2xx** và body bình thường.
- Lỗi nghiệp vụ cụ thể trả status/code/detail mong đợi.
- Lỗi framework (missing param, malformed JSON, unsupported method/media type) trả status phù hợp và không mất header quan trọng.
- Lỗi nội bộ trả 500 **không lộ** stack trace hoặc nội dung nhạy cảm.
- Controller có local `@ExceptionHandler` xử lý khác global advice đúng theo thứ tự.
- Route 404 do không có handler được test riêng, không trộn với domain 404.
- Với async/streaming, test cả trước và sau response commit nếu ứng dụng dùng những mô hình đó.

### Quan sát production

Log stack trace ở phía server cho lỗi 5xx; phân biệt log dự đoán được (ví dụ domain 404/400) với exception bất thường để tránh tạo log nhiễu. Gắn request ID/trace ID qua logging context hay observation system. Ghi metric theo HTTP status, exception type, endpoint pattern; không dùng full URL có ID bí mật làm nhãn metric.

Khi nhận phản ánh “API trả 500 nhưng không vào advice”, trước tiên kiểm tra **lỗi phát sinh ở filter hay dispatcher?**, response đã committed chưa, class advice có được component scan không, exception có được wrapper ở cause chain không, thứ tự advice nào bắt trước và request có bị container chuyển tới `/error` không.

## 18. Câu hỏi tự đánh giá

**Câu 1:** Custom resolver trả `null` sau khi thấy exception thì có nghĩa lỗi được xử lý chưa?  
**Đáp:** Chưa. `null` nghĩa là chưa xử lý; Spring thử resolver tiếp theo.

**Câu 2:** `@ExceptionHandler` trong controller và `@RestControllerAdvice` cùng bắt `IllegalArgumentException` thì ai được ưu tiên?  
**Đáp:** Handler cục bộ trong controller được xét trước nếu phù hợp.

**Câu 3:** Thiếu `@RequestParam`, controller có được gọi rồi mới ném lỗi không?  
**Đáp:** Thường không. Exception có thể phát sinh khi HandlerAdapter chuẩn bị argument trước khi gọi method.

**Câu 4:** Vì sao status 405 có thể có header `Allow`?  
**Đáp:** Đó là thông tin method mà tài nguyên hỗ trợ theo HTTP. Tùy biến handler không nên vứt bỏ header chuẩn này.

**Câu 5:** Có nên tạo `@ExceptionHandler(HttpMessageNotReadableException.class)` trong subclass `ResponseEntityExceptionHandler`?  
**Đáp:** Nên override hook `handleHttpMessageNotReadable` để tránh mapping cạnh tranh với base class.

**Câu 6:** Spring Security filter ném authentication exception trước DispatcherServlet thì global MVC advice luôn bắt được không?  
**Đáp:** Không. Security chain có cơ chế xử lý riêng như `AuthenticationEntryPoint` và `AccessDeniedHandler`.

**Câu 7:** Vì sao API đôi khi trả body mặc định ở `/error` thay vì `ProblemDetail` custom?  
**Đáp:** Ngoại lệ có thể chưa được MVC resolver xử lý, hoặc container thực hiện error dispatch. Cần truy lại luồng xử lý thực tế.

**Câu 8:** Lỗi 500 nên gửi những gì?  
**Đáp:** Status đúng, mã lỗi ổn định, thông điệp an toàn và request/trace ID nếu có; không gửi stack trace, SQL/password hay thông tin hệ thống nội bộ.

## 19. Nguồn đối chiếu

- [Spring Framework — Exceptions / Chain of Resolvers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/exceptionhandlers.html).
- [Spring Framework — @ExceptionHandler methods](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html).
- [Spring Framework — Controller Advice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html).
- [Spring Framework — Error Responses and ProblemDetail](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).
- [Spring Boot 3.5 — Servlet Web Applications / Error Handling](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html).
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457).

**Điểm ghi nhớ:** `HandlerExceptionResolver` giúp `DispatcherServlet` chuyển ngoại lệ trong Spring MVC thành **phản hồi HTTP có chủ đích**. Chọn đúng lớp xử lý theo nguồn lỗi, hiểu thứ tự resolver/advice, và kiểm thử status, body, headers cùng trust boundary sẽ giúp API có error contract ổn định và an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-request-lifecycle-007">Các loại dispatch REQUEST, FORWARD, INCLUDE, ERROR và ASYNC</a>

<details>
<summary>Xem chi tiết</summary>

# Các loại dispatch REQUEST, FORWARD, INCLUDE, ERROR và ASYNC

Trong ứng dụng **Spring Framework Web dùng Spring MVC trên Servlet stack**, một HTTP request có thể đi qua **nhiều lần phân phối (dispatch)** ở tầng Servlet trước khi response hoàn tất. Mỗi lần phân phối được gắn một giá trị thuộc enum `jakarta.servlet.DispatcherType`: `REQUEST`, `FORWARD`, `INCLUDE`, `ERROR` hoặc `ASYNC`.

Đây là **khái niệm của Jakarta Servlet**, không phải tên năm loại HTTP request và cũng không phải năm loại Spring controller. Chúng quyết định cách Servlet container chuyển một request tới Servlet/resource và chọn những Servlet Filter nào chạy trong một lần dispatch. `DispatcherServlet` của Spring MVC là **một Servlet cụ thể** có thể nhận các lần dispatch ấy nếu mapping phù hợp.

Hiểu đúng dispatcher type giúp giải thích vì sao filter có lúc chạy hai lần, có lúc không chạy, vì sao chuyển tiếp phía server khác redirect, vì sao một HTTP 500 không nhất thiết đi qua `ERROR` dispatch, và vì sao async request có thể quay lại `DispatcherServlet` sau khi thread ban đầu đã rời đi.

## 1. Nền tảng: một request có thể có nhiều dispatch

Ví dụ client gửi đúng **một** request:

```http
GET /forward HTTP/1.1
Host: localhost:8080
```

Servlet đích `/forward` dùng `RequestDispatcher.forward(...)` để chuyển cùng request sang `/target`. Về mặt mạng, trình duyệt **không gửi thêm request HTTP** chỉ vì thao tác forward. Nhưng phía container có hai giai đoạn:

```text
Client -- HTTP GET /forward -->
    Servlet container
      └── dispatch REQUEST → Servlet /forward
                               │ RequestDispatcher.forward("/target")
                               ▼
                           dispatch FORWARD → Servlet /target
                                              │
                                              └── response → Client
```

Giá trị `request.getDispatcherType()` đổi theo **lần dispatch đang thực thi**. Không nên nhầm với `request.getMethod()` (`GET`, `POST`...), cũng không nhầm với **thread** hay **URL**. Một dispatch không tương đương một thread; một thread có thể thực hiện các lời gọi dispatch lồng nhau, còn async dispatch có thể xảy ra trên thread khác.

**Ba tầng cần tách:**

| Tầng | Câu hỏi được trả lời | Ví dụ |
| --- | --- | --- |
| HTTP | Client yêu cầu phương thức và tài nguyên nào? | `GET /forward` |
| Servlet dispatch | Container đang phân phối request theo tình huống nào? | `DispatcherType.FORWARD` |
| Spring MVC routing | Trong `DispatcherServlet`, handler nào khớp request? | `@GetMapping("/target")` |

Giá trị `DispatcherType` có thể truy vấn bằng `ServletRequest.getDispatcherType()`. Một filter được đăng ký theo những dispatcher type nhất định; container chọn filter có **mapping URL/Servlet khớp** và **dispatcher type khớp** ở từng lần phân phối.

## 2. Bảng phân biệt đủ năm loại

| DispatcherType | Điều gì khởi phát? | Resource tiếp nhận | Đặc điểm cần nhớ |
| --- | --- | --- | --- |
| `REQUEST` | Container tiếp nhận request ban đầu từ client rồi chọn web component | Servlet/resource khớp mapping ban đầu | Là loại đầu tiên của request; **không đồng nghĩa HTTP GET** |
| `FORWARD` | Code server gọi `RequestDispatcher.forward(request,response)` | Resource đích của forward | Chuyển quyền xử lý phía server; URL trên browser không đổi; response cần chưa committed |
| `INCLUDE` | Code server gọi `RequestDispatcher.include(request,response)` | Resource được nhúng vào output caller | Resource được include đóng góp body; không được thay status/header của response bao ngoài |
| `ERROR` | Container thực hiện cơ chế điều phối đến error page đã cấu hình | Error page/handler đích | Xảy ra khi cơ chế error dispatch thực sự được kích hoạt, **không phải mọi status 4xx/5xx** |
| `ASYNC` | Request ở chế độ async được redispatch bằng `AsyncContext.dispatch(...)` hoặc cơ chế framework tương đương | Resource đích của async dispatch | Có thể đến sau khi thread xử lý ban đầu kết thúc; response vẫn đang mở |

**Chính xác về vòng đời:** `ASYNC` chỉ gắn với **lần phân phối lại** khi có dispatch; lời gọi `startAsync()` ban đầu chưa tự biến dispatch đang chạy thành `ASYNC`. Tương tự, `sendError(500)` không làm cho mọi câu lệnh tiếp theo trong servlet hiện tại tức thì thấy `ERROR`; cơ chế error page của container quyết định bước điều phối tới resource lỗi.

Các ví dụ trong bài sử dụng Spring Boot 3.x/Spring MVC với các API Jakarta (`jakarta.servlet.*`), phù hợp Spring Framework 6.x. Dự án Spring 5.x cũ thường dùng `javax.servlet.*`; không trộn hai namespace.

## 3. REQUEST — lần vào ban đầu

Khi browser hoặc HTTP client gửi request trực tiếp tới web application, Servlet container thực hiện **REQUEST dispatch** ban đầu. Ví dụ:

```java
@Override
protected void doGet(HttpServletRequest request,
                     HttpServletResponse response)
        throws IOException {
    response.setContentType("text/plain;charset=UTF-8");
    response.getWriter().print(request.getDispatcherType());
}
```

Nếu servlet được map tới `/direct` và client gọi `GET /direct` bình thường, body là:

```text
REQUEST
```

`GET`, `POST`, `PUT` vẫn là **HTTP method**, trong khi `REQUEST` là **dispatcher type**. Nếu cùng servlet hỗ trợ `POST`, một HTTP POST ban đầu cũng có dispatcher type `REQUEST`.

Mặc định chuẩn khi khai báo Servlet filter mapping **không chỉ rõ dispatcher types** bằng `web.xml` là `REQUEST`. Tuy nhiên các API đăng ký và framework có thể đặt dispatcher types riêng, vì vậy muốn biết filter thực sự chạy ở đâu phải xem cấu hình đã đăng ký tại runtime.

Spring MVC `DispatcherServlet` tiếp nhận dispatch `REQUEST` nếu mapping của nó trúng URL. Các `HandlerMapping`/`HandlerAdapter` bên trong mới tìm và gọi controller. `REQUEST` không có nghĩa tất cả request đều vào `DispatcherServlet`: có URL do Servlet khác hoặc resource mặc định xử lý.

## 4. FORWARD — chuyển quyền xử lý hoàn toàn phía server

### 4.1. Cơ chế

Một Servlet có thể chuyển sang Servlet/JSP/resource khác thuộc web application bằng:

```java
RequestDispatcher dispatcher =
        request.getRequestDispatcher("/target");
dispatcher.forward(request, response);
```

`/target` là đường dẫn của resource trong context web application theo cách lấy dispatcher trên `ServletRequest`. Container thực hiện dispatch có type **FORWARD** tới resource đích. Caller cần thực hiện forward trước khi response committed; nếu đã committed, `forward()` có thể ném `IllegalStateException`. Nội dung còn trong response buffer chưa committed bị dọn trước khi forward.

Request/response tiếp tục được chuyển phía server (có thể thông qua wrapper). Các request attributes có thể đi cùng request. Đích nhìn thấy thông tin path theo ngữ nghĩa forward và có thể truy cập thông tin request gốc qua các thuộc tính do Servlet specification quy định như `jakarta.servlet.forward.request_uri`.

### 4.2. Forward khác redirect

**Forward:** một request HTTP bên ngoài; browser vẫn hiển thị URL ban đầu. Đích render/xử lý trong server:

```text
Browser GET /old → server forward("/new") → target /new → response
Browser URL: /old
```

**Redirect:** server trả HTTP status 3xx kèm `Location`; client **có thể gửi request mới** tới URL mới:

```http
HTTP/1.1 302 Found
Location: /new
```

```text
Browser GET /old → response 302 → Browser GET /new → new REQUEST dispatch
Browser URL sau điều hướng: /new
```

Một request HTTP mới sau redirect là một **REQUEST dispatch mới** ở server đích, không phải `FORWARD` của request cũ. Đừng dùng redirect thay forward khi cần giữ request attributes; chúng không tự được chuyển sang HTTP request mới.

Trong Spring MVC với controller phục vụ view, có thể thấy:

```java
@GetMapping("/old")
String oldPage() {
    return "forward:/new";
}
```

Đây là **đoạn minh họa** trong MVC controller dùng cơ chế view resolution phù hợp. `forward:` được hiểu như chỉ dẫn forward cho view, không phải một HTTP redirect. Trong `@RestController`, chuỗi trả về thông thường là response body, nên không thể mặc định `"forward:/new"` ở đó sẽ thực hiện forward.

### 4.3. Filter/interceptor ở forward

Một filter map chỉ `REQUEST` có thể chạy ở `/old` nhưng **không chạy thêm cho FORWARD** tại `/new`. Muốn quan sát/bảo vệ cả đích forward, mapping filter cần bao gồm `FORWARD` và khớp resource đích. Nhưng việc thêm `FORWARD` có thể khiến một request đi qua cùng filter nhiều lần; logic ghi audit, rate limit hay mutation cần biết có muốn mỗi dispatch hay mỗi logical HTTP request.

**Tính đúng đắn bảo mật:** không chỉ dựa vào việc “đường dẫn không thể gọi trực tiếp” để bảo vệ resource. Khi filter auth có dispatcher mapping khác nhau, phải đánh giá cả request trực tiếp lẫn forward và điểm kiểm tra authorization thực tế.

## 5. INCLUDE — nhúng output của resource khác

### 5.1. Cơ chế

Servlet A đang tạo response nhưng muốn thêm output do Servlet/JSP B sinh ra:

```java
response.getWriter().print("Trước|");
request.getRequestDispatcher("/fragment")
       .include(request, response);
response.getWriter().print("|Sau");
```

Khi servlet `/fragment` được gọi trong `include(...)`, dispatcher type của nó là **INCLUDE**. Giả sử `/fragment` in `Giữa`, body của response cuối có thể là:

```text
Trước|Giữa|Sau
```

Khác với forward, caller A **tiếp tục** sau khi phần include trả về. Resource được include chủ yếu đóng góp nội dung cho response; nó **không được thay đổi HTTP status hoặc response headers** của response bên ngoài. Những lời gọi thay status/header trong included resource bị container bỏ qua theo quy tắc Servlet.

### 5.2. Request path và attributes khi INCLUDE

Có một điểm dễ gây lỗi: trong `INCLUDE`, các path element của request có thể giữ giá trị theo request của caller; thông tin resource được include được biểu diễn qua attributes như:

- `jakarta.servlet.include.request_uri`
- `jakarta.servlet.include.context_path`
- `jakarta.servlet.include.servlet_path`
- `jakarta.servlet.include.path_info`
- `jakarta.servlet.include.query_string`

Chỉ một số attribute có ý nghĩa/phát sinh tùy mapping. Do đó, nếu debug included resource chỉ bằng `request.getRequestURI()`, bạn có thể hiểu sai resource đang xử lý. Quan sát thêm dispatcher type và các include attributes tương ứng.

`INCLUDE` thường xuất hiện khi lắp ghép output trang ở Servlet/JSP. Với API JSON hiện đại, thường dễ hơn nếu code Java gọi service và tự tạo response thống nhất; không nên ép `include` vào mọi API vì việc tổng hợp response có các ràng buộc status/header/body.

### 5.3. Filter trong INCLUDE

Filter chỉ đăng ký `REQUEST` **không mặc nhiên chạy lại** cho included resource. Filter có mapping `INCLUDE` có thể chạy nếu URL/servlet mapping tương ứng khớp. Hãy nhớ response header do included resource đặt sẽ không có hiệu lực; một filter chạy trong include cũng không thể dựa vào việc tự thay header để cung cấp chính sách response bao ngoài.

## 6. ERROR — dispatch đến error page của container

### 6.1. Điều kiện kích hoạt

`ERROR` phát sinh khi **Servlet container chuyển xử lý sang error resource** theo error page mapping, ví dụ khi:

- Servlet gọi `response.sendError(404)` hay `response.sendError(500)` và container dùng cơ chế error page;
- Có exception chưa được xử lý, container thực hiện error handling đã cấu hình.

Ví dụ file `web.xml` theo cách truyền thống:

```xml
<error-page>
    <error-code>500</error-code>
    <location>/errors/internal</location>
</error-page>
```

Khi container thực hiện dispatch đến `/errors/internal`, resource đích thấy `request.getDispatcherType() == DispatcherType.ERROR`. Mã HTTP cuối, body và khả năng còn ghi response phụ thuộc tình huống, thời điểm commit, mapping và container.

**Không được đồng nhất “HTTP 500” với `ERROR` dispatch**. Một controller có thể tự trả:

```java
return ResponseEntity.status(500).body("Không xử lý được");
```

Đây là cách đặt status/body trong pipeline MVC; không mặc định kích hoạt error page dispatch. Tương tự, `@ExceptionHandler` hoặc `@ControllerAdvice` có thể giải quyết exception ngay bên trong `DispatcherServlet`, không cần thêm `ERROR` dispatch. Cơ chế chuẩn của Spring Boot có endpoint `/error` trong một số cấu hình, nhưng phải xác định đường đi thực tế theo ứng dụng.

### 6.2. Error request attributes

Trong error dispatch, container cung cấp metadata để error handler chẩn đoán, thường gồm các thuộc tính chuẩn:

| Thuộc tính | Ý nghĩa |
| --- | --- |
| `jakarta.servlet.error.status_code` | HTTP status gắn với error |
| `jakarta.servlet.error.exception` | Exception gây lỗi, nếu có |
| `jakarta.servlet.error.exception_type` | Class của exception, nếu có |
| `jakarta.servlet.error.message` | Thông tin lỗi do container cung cấp |
| `jakarta.servlet.error.request_uri` | URI của request gây lỗi |
| `jakarta.servlet.error.servlet_name` | Servlet liên quan, nếu có |

Mã Java lấy thuộc tính mà không giả định kiểu luôn có:

```java
Object status =
        request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
Object originalUri =
        request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
```

Import `jakarta.servlet.RequestDispatcher`. Không trả stack trace, đường dẫn filesystem, token hoặc thông tin nhạy cảm trực tiếp cho client. Ghi log an toàn ở server và cung cấp response lỗi ổn định theo nhu cầu API.

### 6.3. Filter trong ERROR dispatch

Nếu cần audit/error correlation hoặc xử lý request context ở error page, filter phải được map `ERROR` khi mong chờ được gọi trong lần dispatch ấy. Tuy nhiên response có thể đã committed hoặc error processing có thể lồng nhau ở một số container. Không được tự ý thay đổi response status/body từ mọi filter error. Đối với `OncePerRequestFilter`, cần xét cả phương thức `shouldNotFilterErrorDispatch()` và xử lý nested error nếu cần.

## 7. ASYNC — request rời thread ban đầu rồi được dispatch lại

### 7.1. Vòng đời Servlet async

Servlet 3.0+ hỗ trợ async để thread xử lý ban đầu không phải chờ kết quả lâu trên cùng call stack. Khi Servlet/mapping/filter cho phép async, code có thể gọi `startAsync()`:

```text
HTTP request đến
  ↓
REQUEST dispatch vào Servlet
  ↓
startAsync()                         → request được đặt ở chế độ async
  ↓
thread REQUEST trở về container     → response vẫn mở
  ↓
tác vụ nền hoàn tất / dữ liệu sẵn sàng
  ↓
AsyncContext.dispatch("/target")    → ASYNC dispatch
  ↓
Servlet /target hoàn tất response
```

Điểm phân biệt: request đi vào ban đầu vẫn có type **REQUEST**; `startAsync()` chỉ bắt đầu chu kỳ async. `AsyncContext.dispatch(...)` tạo lần phân phối lại type **ASYNC**. Nếu thay bằng `AsyncContext.complete()` thì có thể kết thúc response **không có** redispatch `ASYNC`.

Cơ chế async không có nghĩa “request chắc chắn xử lý trên một thread riêng biệt mãi mãi”. Bộ lập lịch, executor, container và Spring MVC có thể dùng nhiều thread ở các giai đoạn khác nhau. Mọi trạng thái dựa trên `ThreadLocal`, transaction context, MDC/log correlation hoặc security context cần được xử lý theo đúng ranh giới async và chính sách framework; không mặc định chúng tự đi sang thread khác.

### 7.2. ASYNC trong Spring MVC

Khi controller trả về `Callable<T>` hoặc `DeferredResult<T>` trong Spring MVC Servlet stack, framework có thể bắt đầu Servlet async handling, giải phóng thread Servlet ban đầu và **dispatch lại** vào container khi có kết quả. `DispatcherServlet` có thể được gọi lại ở phase `ASYNC` để hoàn tất mapping/serialize và xử lý exception theo pipeline MVC.

Ví dụ minh họa controller Spring MVC:

```java
@RestController
class AsyncGreetingController {

    @GetMapping("/slow-greeting")
    Callable<String> greeting() {
        return () -> "Xin chào từ Callable";
    }
}
```

Đây là **đoạn Spring MVC**, cần import `org.springframework.web.bind.annotation.*` và `java.util.concurrent.Callable`, đặt trong ứng dụng đã có dependency MVC. Nó không phải file Java chạy độc lập bằng `javac` trần.

Trong thực tế, với `Callable`, Spring MVC dùng `AsyncTaskExecutor`; với `DeferredResult`, ứng dụng có thể cung cấp kết quả qua một callback/tác vụ khác. Cần cấu hình executor và timeout phù hợp cho tải thực tế. Trả `Callable` không tự biến I/O blocking thành non-blocking; nếu trong task vẫn gọi I/O blocking, thread chạy task vẫn bị chặn.

**Phân biệt `@Async`** của Spring (kích hoạt gọi method bất đồng bộ qua proxy/executor với điều kiện phù hợp) và **Servlet async dispatch**. Một method gắn `@Async` không tự đảm bảo đã có `DispatcherType.ASYNC` cho HTTP request.

### 7.3. Interceptor trong chu kỳ async

Trong Spring MVC, `AsyncHandlerInterceptor` có callback `afterConcurrentHandlingStarted(...)`. Khi handler bắt đầu async:

- `preHandle(...)` đã có thể chạy ở dispatch ban đầu;
- thay vì `postHandle(...)`/`afterCompletion(...)` của đường đi đồng bộ thông thường, framework gọi `afterConcurrentHandlingStarted(...)` để phần interceptor có cơ hội dọn context thread;
- khi kết quả được dispatch trở lại, framework có thể gọi lại `preHandle`, rồi `postHandle` và `afterCompletion` ở giai đoạn hoàn tất.

Nếu timeout hoặc lỗi mạng làm vòng đời kết thúc mà không có redispatch thông thường, **không mặc định** `afterCompletion`/`postHandle` sẽ được gọi. Dùng callbacks của `DeferredResult`, `WebAsyncManager` và các async processing interceptor phù hợp để xử lý cleanup/thống kê khi cần.

## 8. Filter dispatcher mapping quyết định điều gì chạy lại

Servlet filter chạy ở tầng container, **trước hoặc sau** target Servlet trong filter chain; Spring MVC interceptor chạy **bên trong `DispatcherServlet`** đối với handler mà MVC quản lý. Mỗi lần dispatch, container xây dựng/chọn chain dựa trên dispatcher types và URL/Servlet mapping.

### 8.1. Mapping theo web.xml

Ví dụ muốn filter chạy cho request ban đầu và cho lần forward, nhưng không chạy trong include, error hoặc async:

```xml
<filter-mapping>
    <filter-name>auditFilter</filter-name>
    <url-pattern>/*</url-pattern>
    <dispatcher>REQUEST</dispatcher>
    <dispatcher>FORWARD</dispatcher>
</filter-mapping>
```

Nếu không có `<dispatcher>`, mặc định chuẩn của filter mapping theo deployment descriptor là `REQUEST`. Muốn map `ERROR` hoặc `ASYNC`, cần khai báo phù hợp. Với async còn phải đảm bảo các Servlet/Filter trên đường đi hỗ trợ async (`async-supported`), nếu không việc gọi `startAsync()` có thể thất bại với `IllegalStateException`.

### 8.2. Mapping bằng Java/Spring Boot

Với Spring Boot MVC, có thể đăng ký filter và dispatcher types tường minh bằng `FilterRegistrationBean`:

```java
@Bean
FilterRegistrationBean<MyFilter> myFilter() {
    FilterRegistrationBean<MyFilter> registration =
            new FilterRegistrationBean<>(new MyFilter());

    registration.addUrlPatterns("/*");
    registration.setDispatcherTypes(
            DispatcherType.REQUEST,
            DispatcherType.FORWARD,
            DispatcherType.INCLUDE,
            DispatcherType.ERROR,
            DispatcherType.ASYNC);
    registration.setAsyncSupported(true);
    return registration;
}
```

Đây là **mảnh mã cấu hình minh họa**: cần import `org.springframework.context.annotation.Bean`, `org.springframework.boot.web.servlet.FilterRegistrationBean`, `jakarta.servlet.DispatcherType` và có lớp `MyFilter implements Filter`. Mục 9 cung cấp một dự án đầy đủ.

`setDispatcherTypes(...)` quyết định filter áp dụng ở các dispatch type nào; `setAsyncSupported(true)` nói filter cho phép chu kỳ Servlet async đi qua. **Hai lựa chọn giải quyết hai việc khác nhau**. Chọn đủ năm type không phải luôn tốt nhất: có thể ghi log nhiều lần hoặc áp dụng lại authentication/authorization/response wrapper sai thời điểm.

### 8.3. `OncePerRequestFilter` không bảo đảm “chỉ một lần cho toàn bộ vòng đời HTTP”

Spring cung cấp `OncePerRequestFilter` để hỗ trợ lọc một lần **trong mỗi request dispatch / luồng request đang xử lý theo semantics của class**, dựa trên marker attribute. Một logical request có thể có `REQUEST`, `ASYNC` và `ERROR` dispatch, vì thế không được đọc tên class rồi suy ra toàn bộ HTTP request chỉ chạy filter đúng một lần.

Ở Spring Framework 6.x, mặc định:

- `shouldNotFilterAsyncDispatch()` thường trả `true`: bỏ qua các async dispatch tiếp theo.
- `shouldNotFilterErrorDispatch()` thường trả `true`: bỏ qua error dispatch.
- Có thể override về `false` theo chủ đích để tham gia async/error, **nhưng Servlet filter mapping của container cũng phải bao gồm dispatcher type tương ứng**.
- Trên một số Servlet container, `ERROR` có thể lồng trong `REQUEST`; `doFilterNestedErrorDispatch(...)` là hook riêng khi cần xử lý request/response wrapper đúng cách.

Nếu cần log một **logical HTTP request** đúng một lần, tự xác định policy theo request id hoặc request attribute, đồng thời quyết định có ghi lúc bắt đầu, lúc hoàn tất, hay ở cả hai phase. Nếu cần đếm **mỗi lần dispatch**, đếm theo type thay vì dùng “once” của filter như một quy tắc thống kê nghiệp vụ.

### 8.4. `DispatcherServlet` và Servlet dispatcher type là hai khái niệm riêng

`DispatcherServlet` là Front Controller của Spring MVC; `DispatcherType` là enum của Servlet API. Đúng hoặc sai dispatcher mapping có thể làm filter không chạy trong một forward/error/async dispatch, **ngay cả khi controller Spring MVC xử lý request đúng**.

Một forward có thể đến JSP/Servlet khác và không đi vào controller MVC. Một Servlet ERROR dispatch có thể đến trang lỗi không thuộc `DispatcherServlet`. Tương tự, lỗi đã được `@ExceptionHandler` xử lý bên trong MVC không nhất thiết đi qua ERROR dispatch ở container.

## 9. Thực hành đầy đủ: quan sát cả năm loại bằng một ứng dụng

Ví dụ dùng **Java 17+ và Spring Boot 3.5.7**, `spring-boot-starter-web` (Servlet MVC), chạy với Gradle. Thay vì dựa vào browser hay suy đoán log, chương trình in trực tiếp `request.getDispatcherType()` từ Servlet nhận dispatch và filter. Đây là **ứng dụng Spring Boot dùng Servlet API trực tiếp để minh họa hành vi của Servlet container**, không phải bộ ví dụ controller REST dùng cho thiết kế API production.

### 9.1. Cấu trúc và dependency

Cấu trúc:

```text
dispatch-demo/
  build.gradle
  settings.gradle
  src/main/java/demo/dispatch/DispatchDemoApplication.java
```

`settings.gradle`:

```groovy
rootProject.name = 'dispatch-demo'
```

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.7'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'demo'
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
```

Cần có Gradle wrapper hoặc Gradle tương thích cài sẵn. Nếu đã tạo dự án Spring Initializr với cùng dependency và version, giữ file cấu hình của dự án đó rồi thêm lớp Java bên dưới.

### 9.2. Một file Java chạy được khi đủ Spring dependency

`src/main/java/demo/dispatch/DispatchDemoApplication.java`:

```java
package demo.dispatch;

import java.io.IOException;
import java.util.EnumSet;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

@SpringBootApplication
public class DispatchDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchDemoApplication.class, args);
    }

    @Bean
    ServletRegistrationBean<EntryServlet> entryServlet() {
        var bean = new ServletRegistrationBean<>(
                new EntryServlet(),
                "/direct", "/forward", "/include", "/fail", "/async");
        bean.setName("entry");
        bean.setAsyncSupported(true);
        return bean;
    }

    @Bean
    ServletRegistrationBean<TargetServlet> targetServlet() {
        var bean = new ServletRegistrationBean<>(
                new TargetServlet(), "/target", "/fragment");
        bean.setName("target");
        bean.setAsyncSupported(true);
        return bean;
    }

    @Bean
    ServletRegistrationBean<ErrorServlet> errorServlet() {
        var bean = new ServletRegistrationBean<>(
                new ErrorServlet(), "/error-page");
        bean.setName("errorPage");
        bean.setAsyncSupported(true);
        return bean;
    }

    @Bean
    ErrorPageRegistrar exampleErrorPage() {
        return registry -> registry.addErrorPages(
                new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR,
                        "/error-page"));
    }

    @Bean
    FilterRegistrationBean<DispatchLogFilter> logFilter() {
        var bean = new FilterRegistrationBean<>(
                new DispatchLogFilter());
        bean.addUrlPatterns("/*");
        bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        bean.setAsyncSupported(true);
        return bean;
    }

    static final class DispatchLogFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request,
                             ServletResponse response,
                             FilterChain chain)
                throws IOException, ServletException {
            System.out.println("FILTER " + request.getDispatcherType());
            chain.doFilter(request, response);
        }
    }

    static final class EntryServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request,
                             HttpServletResponse response)
                throws IOException, ServletException {
            response.setContentType("text/plain;charset=UTF-8");

            switch (request.getServletPath()) {
                case "/direct" ->
                    response.getWriter().print(
                            "TYPE=" + request.getDispatcherType());

                case "/forward" ->
                    request.getRequestDispatcher("/target")
                           .forward(request, response);

                case "/include" -> {
                    response.getWriter().print("before|");
                    request.getRequestDispatcher("/fragment")
                           .include(request, response);
                    response.getWriter().print("|after");
                }

                case "/fail" ->
                    response.sendError(500);

                case "/async" -> {
                    AsyncContext context = request.startAsync();
                    context.start(() -> context.dispatch("/target"));
                }

                default -> response.sendError(404);
            }
        }
    }

    static final class TargetServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request,
                             HttpServletResponse response)
                throws IOException {
            response.getWriter().print(
                    "TYPE=" + request.getDispatcherType());
        }
    }

    static final class ErrorServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request,
                             HttpServletResponse response)
                throws IOException {
            response.getWriter().print(
                    "TYPE=" + request.getDispatcherType());
        }
    }
}
```

**Giải thích các cơ chế ảnh hưởng kết quả:**

- `ServletRegistrationBean` đăng ký các Servlet URL cụ thể trong embedded Servlet container. Spring Boot vẫn có thể cung cấp `DispatcherServlet` cho MVC, nhưng những URL demo này được phục vụ bởi **các Servlet đăng ký riêng**.
- `entryServlet()` tạo entry points `/direct`, `/forward`, `/include`, `/fail`, `/async`.
- `targetServlet()` là đích `/target` hoặc `/fragment`; nó in dispatcher type **tại chính đích**, sau khi container phân phối.
- `ErrorPageRegistrar` định tuyến lỗi status 500 tới `/error-page`, cho phép `ErrorServlet` quan sát `ERROR`. Nếu thay đổi error configuration hoặc có response đã committed, kết quả có thể khác.
- Filter đặt `EnumSet.allOf(DispatcherType.class)` để log **mọi loại dispatch** đi qua URL pattern `/*`; `asyncSupported=true` tránh chặn việc khởi động Servlet async ở đường đi thử nghiệm.
- `context.start(...)` dùng cơ chế async của container để gọi `dispatch("/target")`. Servlet ban đầu không viết body sau khi start async; response được hoàn tất ở đích redispatch.
- Lệnh `System.out.println` trong filter phục vụ quan sát, có thể xen kẽ khi nhiều request đồng thời; không dùng nó làm bằng chứng thứ tự toàn cục giữa các request.

### 9.3. Chạy và đọc kết quả

Từ thư mục dự án có Gradle wrapper:

```bash
./gradlew bootRun
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

Ở terminal thứ hai, gọi từng URL:

| Lệnh | Response body dự kiến | Ý nghĩa |
| --- | --- | --- |
| `curl -s http://localhost:8080/direct` | `TYPE=REQUEST` | Lần dispatch từ client |
| `curl -s http://localhost:8080/forward` | `TYPE=FORWARD` | Target nhận server-side forward |
| `curl -s http://localhost:8080/include` | `before\|TYPE=INCLUDE\|after` | Caller tiếp tục sau khi include |
| `curl -s http://localhost:8080/fail` | `TYPE=ERROR` | Error page target nhận ERROR dispatch |
| `curl -s http://localhost:8080/async` | `TYPE=ASYNC` | Target nhận async redispatch |

Trong ô response của lệnh `include`, các ký tự gạch dọc thực tế là `|` (bảng Markdown dùng `\|` để escape ký tự phân cột). Với `/fail`, dùng `curl -i` để thấy HTTP **500**; `-s` chỉ im lặng về thanh tiến trình, không thay đổi status.

Ở console ứng dụng, số log filter dự kiến theo **mỗi request đơn lẻ**:

```text
GET /direct   → FILTER REQUEST
GET /forward  → FILTER REQUEST, FILTER FORWARD
GET /include  → FILTER REQUEST, FILTER INCLUDE
GET /fail     → FILTER REQUEST, FILTER ERROR
GET /async    → FILTER REQUEST, FILTER ASYNC
```

Đây là mô hình log kỳ vọng khi ứng dụng được đăng ký đúng và không có can thiệp từ filter khác; dòng thực tế có thể xen kẽ giữa request, và một số container có khác biệt về thời điểm error dispatch. Đừng xem thứ tự chính xác của log giữa nhiều client concurrent là bất biến.

**Thử thách:** đổi mapping của filter từ `EnumSet.allOf(DispatcherType.class)` sang `EnumSet.of(DispatcherType.REQUEST)` rồi chạy lại. Body đích của forward/include/error/async vẫn có thể cho thấy type mới, nhưng filter **không log** các dispatch bổ sung; đó là cách trực tiếp chứng minh filter mapping quyết định việc filter chạy, không quyết định luôn loại dispatch của target.

Sau đó thử cho filter chỉ chạy `REQUEST` và `ASYNC`. Gọi `/async`, quan sát hai lần log. Nếu bỏ `setAsyncSupported(true)` khỏi filter nằm trên đường request mà servlet gọi `startAsync()`, request có thể thất bại vì toàn bộ chain không cho phép async.

## 10. Trường hợp phức tạp và những giới hạn cần biết

### 10.1. Một request có thể có chuỗi dispatch khác bảng minh họa

Không có quy tắc “một request chỉ có hai dispatch”. Code có thể forward qua nhiều resource, include lồng nhau, error handler có thể được kích hoạt từ đường xử lý khác, và async có thể có nhiều giai đoạn. Servlet specification và container quy định các giới hạn hợp lệ; đừng tạo vòng forward/include vô hạn. Các chu kỳ bất thường dễ dẫn đến lỗi stack, lặp log và khó phân tích.

Nếu mục tiêu là **một lần thực hiện logic nghiệp vụ**, quyết định ở service/transaction/idempotency chứ không dựa duy nhất vào số filter invocations.

### 10.2. Dispatcher type không biểu diễn đầy đủ outcome

`ERROR` chỉ thể hiện một **error-page dispatch**; không bảo đảm status cuối là 500 hoặc exception nào. `ASYNC` chỉ thể hiện redispatch; không bảo đảm tác vụ trước đó thành công. `FORWARD` và `INCLUDE` nói về cách container đưa request tới resource khác; không nói rằng resource đó thuộc Spring MVC.

Muốn chẩn đoán, cần quan sát phối hợp:

- `request.getDispatcherType()`;
- method, URI, servlet path, attributes `jakarta.servlet.forward.*`, `jakarta.servlet.include.*`, `jakarta.servlet.error.*`;
- status response khi hoàn tất;
- Servlet/filter mapping và `asyncSupported`;
- Spring MVC handler mapping, interceptor, exception handler và async callbacks khi request đi qua `DispatcherServlet`.

Không log raw cookie, Authorization header, session ID hoặc dữ liệu nhạy cảm chỉ để debug dispatch.

### 10.3. Filter chạy nhiều lần: chuyện thường gặp

Một số lỗi phát sinh khi filter vô tình giả định chỉ có một dispatch:

1. **Double counting** metric request: `REQUEST` và `ERROR` cùng tăng một counter đại diện cho *một* HTTP request.
2. **Double wrapping** request/response: filter áp dụng wrapper ở `REQUEST` rồi lại xử lý wrapper khác trong `FORWARD`, `INCLUDE` hoặc nested `ERROR`.
3. **Audit duplicate**: một hành động người dùng bị ghi hai bản vì sau `REQUEST` có `ASYNC` redispatch.
4. **MDC/ThreadLocal leak**: context được tạo trên thread ban đầu nhưng không cleanup khi request chuyển async hoặc khi thread container được tái sử dụng.
5. **Authorization gap**: filter chỉ gắn URL hoặc dispatcher type không bao phủ đường tới tài nguyên nhạy cảm.
6. **Status sai trong INCLUDE**: included resource cố set status/header nhưng thay đổi bị bỏ qua.
7. **Lẫn redirect với FORWARD**: client nhận 3xx rồi tạo HTTP request mới, log hiển thị `REQUEST` mới là chính xác.
8. **Hiểu sai 500**: controller trả `ResponseEntity.status(500)` nhưng không có `ERROR` dispatch, vì không đi qua cơ chế error page.

### 10.4. Quy tắc đánh giá thứ tự filter và MVC interceptor

Một cách hình dung:

```text
Servlet container:
    Filter chain đã match dispatcher type
        ↓
    Servlet đang được dispatch tới
        ↓
    nếu là DispatcherServlet của Spring MVC:
        HandlerMapping → HandlerInterceptor → Controller → View/Body
        ↑                                   ↓
        └──────── lifecycle MVC callbacks ─┘
```

Nếu có một `FORWARD` tới resource do MVC quản lý, container có thể xây lại filter chain theo type FORWARD rồi `DispatcherServlet` mới chọn handler khác. Nếu forward đến một JSP hoặc custom Servlet, Spring MVC interceptor của controller **không tự chạy tại đó**.

Đối với async controller, interceptor callback ở dispatch ban đầu và ở async dispatch hoàn tất khác đường đồng bộ, như mục 7.3. Nơi bảo vệ authentication/authorization nên dùng những thành phần bảo mật phù hợp toàn stack thay vì tự giả định mọi Servlet/resource đều đi qua MVC interceptor.

## 11. Checklist thiết kế và tự kiểm tra

**Khi thêm Servlet Filter:**

1. Xác định filter cần xử lý **mỗi HTTP request** hay **mỗi dispatch**.
2. Đăng ký dispatcher types phù hợp (REQUEST, FORWARD, INCLUDE, ERROR, ASYNC).
3. Kiểm tra URL/servlet mapping và thứ tự filter chain.
4. Nếu dùng async, kiểm tra `asyncSupported` trên các thành phần liên quan.
5. Nếu dùng `OncePerRequestFilter`, xác định rõ `shouldNotFilterAsyncDispatch`/`shouldNotFilterErrorDispatch`.
6. Test cả trường hợp thông thường, forward/include, lỗi và async; xác nhận không rò `ThreadLocal` hay nhân đôi metric.

**Tự trả lời:**

1. Tại sao hai request `GET /direct` và `POST /direct` đều có thể là `DispatcherType.REQUEST`?
2. Vì sao `forward("/target")` không đổi URL browser, còn `sendRedirect("/target")` thường dẫn tới REQUEST dispatch mới?
3. Ở `INCLUDE`, vì sao included servlet không thể sửa status/header của response ngoài?
4. Khi controller MVC trả HTTP 500 trực tiếp, vì sao chưa đủ cơ sở nói đã có `ERROR` dispatch?
5. `startAsync()` và `AsyncContext.dispatch(...)` thay đổi vòng đời request khác nhau ở bước nào?
6. Filter mapping chỉ REQUEST có chạy lại trong ASYNC/ERROR không? Phải cấu hình những gì nếu muốn nó chạy?
7. `OncePerRequestFilter` có nghĩa chắc chắn chỉ một lần suốt tất cả dispatch của một HTTP request không?
8. Nếu Spring MVC `Callable` hoàn tất và có redispatch, interceptor cần xử lý cleanup ở giai đoạn nào?
9. Trong ví dụ runnable, vì sao body của `/include` chứa `TYPE=INCLUDE` dù request bên ngoài bắt đầu là `REQUEST`?
10. Vì sao phải phân biệt `DispatcherServlet` với `DispatcherType` khi đọc trace?

**Kết quả cần đạt:** Có thể xác định type của từng dispatch trong một request, dự đoán filter nào tham gia dựa trên dispatcher mapping, chọn `forward`/`include`/`sendError`/`startAsync` đúng mục đích, và giải thích callback của Spring MVC trong chu kỳ async. Đây là nền tảng quan trọng để hiểu luồng xử lý MVC và chẩn đoán những lỗi filter, error page và async khó tái hiện.

## Tài liệu tham khảo

- Jakarta Servlet Specification: https://jakarta.ee/specifications/servlet/6.1/
- Jakarta Servlet API, `ServletRequest.getDispatcherType()`: https://jakarta.ee/specifications/servlet/6.1/apidocs/jakarta.servlet/jakarta/servlet/ServletRequest.html
- Jakarta Servlet API, `RequestDispatcher`: https://jakarta.ee/specifications/servlet/6.1/apidocs/jakarta.servlet/jakarta/servlet/RequestDispatcher.html
- Spring Framework — Asynchronous Requests: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Spring Framework API — `OncePerRequestFilter`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mvc-request-lifecycle-008">Vòng đời controller singleton và nguy cơ dùng trạng thái chia sẻ</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng đời controller singleton và nguy cơ dùng trạng thái chia sẻ trong Spring Web MVC

## 1. Mục tiêu học tập

Trong Spring Web MVC, một `@RestController` thông thường **là Spring bean có scope `singleton`**. Nhiều HTTP request có thể gọi **cùng một instance controller** đồng thời. Đây là một đặc điểm quan trọng của luồng xử lý MVC: Spring không tạo một controller Java mới cho mỗi request chỉ vì nhiều client đang truy cập endpoint.

Sau bài này, bạn có thể:

- Mô tả controller được tạo, khởi tạo, sử dụng và hủy trong vòng đời Spring `ApplicationContext`.
- Phân biệt `singleton` của Spring với singleton GoF, đối tượng "một instance trên toàn JVM" và khái niệm "mỗi request một controller".
- Chỉ ra vì sao biến local trong handler khác về tính chia sẻ so với mutable instance field, static field hoặc mutable dependency.
- Tái hiện một race condition khiến dữ liệu request A xuất hiện trong response của request B.
- Thiết kế controller stateless và chuyển trạng thái đúng nơi: request, session, database hay thành phần đồng bộ phù hợp.
- Phân biệt `@RequestScope`, `@SessionScope`, `prototype`, scoped proxy, `ThreadLocal` và primitive thread-safe.
- Kiểm thử hành vi singleton, request independence và rủi ro concurrency bằng Java 17+ và Spring MVC.

**Thông điệp chính:** `singleton` chỉ xác định **số lượng và phạm vi tồn tại của instance**. Nó **không tự bảo đảm thread safety**. Cách an toàn mặc định là viết controller ít trạng thái, giữ dữ liệu từng request trong tham số/biến local và sử dụng collaborator có hợp đồng concurrency phù hợp.

## 2. Controller trong Spring MVC được tạo như thế nào?

Một lớp được đánh dấu `@Controller` hoặc `@RestController` là ứng viên Spring component. Khi component scanning hoặc cấu hình explicit đăng ký bean definition, Spring container tạo và quản lý instance theo scope của bean.

Ví dụ:

```java
package com.example.lifecycle.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/api/ping")
    public String ping() {
        return "pong";
    }
}
```

Lớp này mặc định dùng **`singleton` scope**, tương tự việc khai báo `@Scope("singleton")` một cách tường minh. Trong một Spring `ApplicationContext`, Spring giữ lại một instance cho **bean definition** của controller và tái sử dụng nó. Cùng một class vẫn có thể xuất hiện trong nhiều bean definitions/contexts khác nhau; "singleton" của Spring có nghĩa **theo container và theo bean**, không phải một object duy nhất trên toàn JVM.

### Vòng đời thông thường

```text
Ứng dụng khởi động
    |
    v
Tạo ApplicationContext và đăng ký bean definitions
    |
    v
Tạo singleton controller (eager theo mặc định, trừ lazy/điều kiện riêng)
    |
    v
Resolve và inject dependencies (constructor, ...)
    |
    v
Bean lifecycle: post-processors, @PostConstruct và callbacks liên quan
    |
    v
Context sẵn sàng nhận request
    |
    +-- HTTP request A --> HandlerMapping/HandlerAdapter --> controller INSTANCE X
    |
    +-- HTTP request B --> HandlerMapping/HandlerAdapter --> controller INSTANCE X
    |
    +-- HTTP request C --> HandlerMapping/HandlerAdapter --> controller INSTANCE X
    |
    v
ApplicationContext đóng
    |
    v
Destroy callbacks của singleton (nếu có), giải phóng tài nguyên quản lý
```

Chi tiết startup có thể thay đổi do lazy initialization, proxy, bean post-processor hoặc context hierarchy. **Không** diễn giải sơ đồ thành "mọi bean đều có `@PostConstruct`" hoặc "mọi bean được tạo tại đúng một thời điểm tuyệt đối". Với controller singleton chuẩn, điều cốt lõi là instance sống qua nhiều request và thường lâu ngang vòng đời context.

`@PostConstruct` chạy ở thời điểm bean initialization, **không chạy cho mỗi HTTP request**. `@PreDestroy` hoặc destruction callback của bean singleton thường liên quan context shutdown, **không chạy khi controller xử lý xong một request**.

### Vì sao Spring MVC dùng chung controller?

Trong Spring MVC, Servlet container điều phối request đến `DispatcherServlet`. `HandlerMapping` chọn handler đã đăng ký; `HandlerAdapter` gọi phương thức tương ứng trên controller bean hiện hữu. Spring container không "new" controller mỗi lần khi handler method được chọn. Nhiều request đồng thời có thể được các servlet threads khác nhau xử lý và gọi cùng một method trên một object:

```text
Request A (thread T1):  controllerInstance.getUser("A")
                                 |
                                 +--> cùng một object
                                 |
Request B (thread T2):  controllerInstance.getUser("B")
```

Thread ở đây chỉ là mô hình trực quan. Với async processing, request có thể đi qua nhiều dispatch hoặc executor khác nhau. Điều quan trọng là **nhiều luồng thực thi có thể truy cập cùng field của object controller**.

## 3. Phân biệt dữ liệu local, field và dữ liệu dùng chung

```java
@RestController
class ExampleController {

    private final GreetingService service;        // một reference của bean
    private String lastName;                       // mutable instance field
    private static long globalCounter;             // static mutable field

    ExampleController(GreetingService service) {
        this.service = service;
    }

    @GetMapping("/api/example")
    String example(@RequestParam String name) {
        String normalized = name.trim();          // local variable
        lastName = normalized;                     // shared state!
        globalCounter++;                           // shared state + race!
        return service.greet(normalized);
    }
}
```

Đây là **đoạn minh họa**: cần imports tương ứng và bean `GreetingService` mới biên dịch trong project Spring. Không nên copy ví dụ này làm production controller.

| Loại dữ liệu | Nơi tồn tại | Nguy cơ khi nhiều request cùng truy cập |
|---|---|---|
| Tham số handler, ví dụ `name` | Mỗi lần phương thức được gọi | Không phải field dùng chung; vẫn có thể trỏ đến mutable object khác. |
| Biến local, ví dụ `normalized` | Mỗi lần gọi phương thức | Không chia sẻ riêng biến local giữa các lần gọi; chỉ trở nên nguy hiểm nếu reference của nó thoát ra môi trường dùng chung. |
| `final` instance field trỏ đến service | Cùng controller singleton | Reference không thể gán lại; bản thân service/object được trỏ đến vẫn có thể mutable và cần thread-safe. |
| Mutable instance field, ví dụ `lastName` | Cùng instance controller | Request khác có thể ghi đè, đọc thấy dữ liệu của nhau hoặc gây data race. |
| `static` field, ví dụ `globalCounter` | Gắn với class/ClassLoader trong JVM | Có thể dùng chung xuyên các controller instances; không trở thành an toàn chỉ vì đổi scope controller. |
| Mutable state trong injected singleton service | Trong service singleton | Rủi ro tương tự, chỉ được chuyển từ controller sang service. |
| Request-scoped bean | Một HTTP request scope hợp lệ | Cách ly giữa các request khác nhau; **không** mặc nhiên bảo vệ nếu nhiều thread bên trong cùng request cùng truy cập object đó. |
| Session-scoped bean | Một HTTP session scope | Nhiều request của **cùng một session** vẫn có thể chạy đồng thời và đụng state. |

Khi controller chỉ gọi các pure/stateless methods và dùng dữ liệu local, việc nhiều thread sử dụng cùng instance **thường an toàn**, vì object không lưu trạng thái riêng của request. Nhưng controller stateless vẫn có thể gọi service, cache hoặc database mà giao thức cập nhật dữ liệu không an toàn; luôn đánh giá toàn bộ chuỗi phụ thuộc.

## 4. Race condition điển hình: một request ghi đè tên của request khác

Xét controller sai:

```java
package com.example.lifecycle.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnsafeGreetingController {

    private String currentName;

    @GetMapping("/api/unsafe")
    public String greet(@RequestParam(name = "name") String name) {
        currentName = name;
        // Một request khác có thể ghi đè currentName tại đây.
        return "Xin chào, " + currentName;
    }
}
```

Với hai request gần nhau, interleaving **có thể** là:

```text
Thread A                             Thread B
----------------------------------   ----------------------------------
GET /api/unsafe?name=An              GET /api/unsafe?name=Bình
currentName = "An"
                                     currentName = "Bình"
return "Xin chào, " + currentName
-> có thể trả "Xin chào, Bình"!
                                     return "Xin chào, Bình"
```

Đây là một lịch chạy có thể xảy ra, không phải kết quả bắt buộc của mọi lần thử HTTP. Lỗi có thể chỉ xuất hiện khi tải cao và rất khó tái hiện bằng gọi URL lần lượt. Ngoài interleaving, field không có cơ chế đồng bộ còn có vấn đề memory visibility theo Java Memory Model: các thread có thể không thấy thay đổi của nhau theo thời điểm mà lập trình viên tưởng tượng.

**Hệ quả nghiệp vụ có thể nghiêm trọng**: nếu lưu `currentUserId`, `tenantId`, `cart`, `accessToken`, `searchCriteria` hay dữ liệu trả về theo user trong field singleton, request của người này có thể dùng nhầm dữ liệu của người khác. Đây là vấn đề data isolation, không chỉ là số đếm bị sai.

### Vì sao `volatile` không phải cách sửa chung?

Đổi thành:

```java
private volatile String currentName;
```

chỉ thêm một số bảo đảm visibility và ordering cho các thao tác read/write trên field. Nó **không** gắn một giá trị với request đã sinh ra giá trị đó. Request A vẫn có thể đọc giá trị B vừa ghi. Ngược lại, `counter++` là chuỗi read–modify–write, nên thêm `volatile` vào counter **không** khiến increment trở thành atomic.

Vấn đề đúng cần giải là: **không đặt dữ liệu riêng của request vào object shared**.

## 5. Thí nghiệm Java 17+: tái hiện lỗi một cách có kiểm soát

Bạn không cần khởi động server để chứng minh bản chất lỗi. File Java độc lập sau dùng hai thread, một instance service chung và `CyclicBarrier` để buộc hai lời gọi cùng ghi state trước khi đọc. Dùng `volatile` ở đây để kết quả hiển thị nhất quán về visibility; `volatile` vẫn không sửa lỗi trộn request.

Tệp `SharedStateRace.java`:

```java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SharedStateRace {

    static final class UnsafeGreeter {
        private volatile String currentName;
        private final CyclicBarrier bothWritten = new CyclicBarrier(2);

        String greet(String name)
                throws InterruptedException, BrokenBarrierException {
            currentName = name;
            bothWritten.await();
            return currentName;
        }
    }

    public static void main(String[] args) throws Exception {
        UnsafeGreeter greeter = new UnsafeGreeter();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> first = pool.submit(() -> greeter.greet("An"));
            Future<String> second = pool.submit(() -> greeter.greet("Bình"));

            String a = first.get();
            String b = second.get();

            System.out.println("Same response: " + a.equals(b));
            System.out.println("Exactly one correct: "
                    + (a.equals("An") != b.equals("Bình")));
        }
    }
}
```

**Chú ý:** `ExecutorService` triển khai `AutoCloseable` từ Java 19; đoạn `try (ExecutorService ...)` cần **Java 19+**, nên để chạy chính xác trên Java 17 như mục tiêu bài, hãy dùng phiên bản bên dưới, gọi `shutdown()` trong `finally`:

```java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SharedStateRace {

    static final class UnsafeGreeter {
        private volatile String currentName;
        private final CyclicBarrier bothWritten = new CyclicBarrier(2);

        String greet(String name)
                throws InterruptedException, BrokenBarrierException {
            currentName = name;
            bothWritten.await();
            return currentName;
        }
    }

    public static void main(String[] args) throws Exception {
        UnsafeGreeter greeter = new UnsafeGreeter();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = pool.submit(() -> greeter.greet("An"));
            Future<String> second = pool.submit(() -> greeter.greet("Bình"));

            String a = first.get();
            String b = second.get();

            System.out.println("Same response: " + a.equals(b));
            System.out.println("Exactly one correct: "
                    + (a.equals("An") != b.equals("Bình")));
        } finally {
            pool.shutdown();
        }
    }
}
```

Tạo file chỉ với **khối mã thứ hai** (bản tương thích Java 17), lưu UTF-8. Chạy:

```bash
javac -encoding UTF-8 SharedStateRace.java
java SharedStateRace
```

Stdout:

```text
Same response: true
Exactly one correct: true
```

Giải thích: mỗi thread ghi tên riêng rồi chờ cả hai thread tới barrier. Sau barrier, cả hai đọc **cùng một field đã bị lần ghi cuối cùng quyết định**. Giá trị cuối có thể là `An` hoặc `Bình`, nhưng hai response giống nhau nên chính xác một trong hai request bị gắn sai tên. Barrier trong thí nghiệm có mục đích tạo interleaving xác định; **đừng đưa barrier vào controller production** để "đồng bộ request".

Sửa logic bằng cách bỏ mutable shared field:

```java
static final class SafeGreeter {
    String greet(String name) {
        String normalized = name.trim();
        return normalized;
    }
}
```

Mỗi lần gọi có `name`/`normalized` riêng. Không cần `volatile`, `synchronized` hay lock để bảo vệ state không tồn tại.

## 6. Spring Web MVC chạy được: thiết kế controller stateless

Phần này tạo ứng dụng Spring Boot 3.5.x + Spring MVC, dùng Java 17, Gradle 8.5. Ví dụ chọn phiên bản cụ thể **Spring Boot 3.5.16**; trong sản phẩm đang triển khai, tuân thủ BOM và đường nâng cấp phiên bản được hỗ trợ. Với Spring Boot 3.5, starter web theo Servlet stack vẫn là `spring-boot-starter-web`.

### 6.1. Cấu trúc project

```text
controller-lifecycle-demo/
├── settings.gradle
├── build.gradle
├── gradlew                      # nếu tạo với Gradle Wrapper/Initializr
├── gradlew.bat
├── gradle/wrapper/...
└── src/
    ├── main/
    │   ├── java/com/example/lifecycle/
    │   │   ├── LifecycleApplication.java
    │   │   ├── service/GreetingService.java
    │   │   └── web/GreetingController.java
    │   └── resources/application.properties
    └── test/java/com/example/lifecycle/
        └── GreetingControllerTest.java
```

Bạn có thể tạo project qua https://start.spring.io với Gradle/Groovy, Java 17 và dependency **Spring Web**, hoặc tạo tệp thủ công rồi generate Gradle Wrapper. Những file `gradlew` và `gradle/wrapper/*` phải do wrapper hoặc Initializr tạo; đừng tạo file `gradle-wrapper.jar` giả.

`settings.gradle`:

```groovy
rootProject.name = 'controller-lifecycle-demo'
```

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.16'
    id 'io.spring.dependency-management' version '1.1.7'
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
```

Spring Boot BOM quản lý phiên bản Spring Framework, Jackson và phần lớn dependency liên quan; đừng tự chọn version `spring-webmvc` khác một cách tùy tiện. `spring-boot-starter-test` mang thư viện JUnit và Spring Test thường dùng để kiểm chứng handler.

### 6.2. Khởi động ứng dụng

`src/main/java/com/example/lifecycle/LifecycleApplication.java`:

```java
package com.example.lifecycle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LifecycleApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifecycleApplication.class, args);
    }
}
```

Đặt `@SpringBootApplication` tại package cha `com.example.lifecycle` để component scan phát hiện service/controller bên dưới. Spring Boot tự cấu hình embedded Servlet container và `DispatcherServlet` khi starter web phù hợp có mặt.

`src/main/resources/application.properties`:

```properties
spring.application.name=controller-lifecycle-demo
server.port=8080
```

### 6.3. Stateless service

`src/main/java/com/example/lifecycle/service/GreetingService.java`:

```java
package com.example.lifecycle.service;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public String greet(String name) {
        String normalized = name.trim();
        return "Xin chào, " + normalized + "!";
    }
}
```

`@Service` mặc định cũng là singleton bean. Service này **an toàn cho concurrent calls theo logic đang trình bày** vì không ghi hay đọc mutable state dùng chung. Các local variables của mỗi lời gọi phương thức không dùng chung với lời gọi khác.

Nếu một ngày thêm `private String lastName;` vào service để "lưu tạm", bạn chỉ đang **dời race condition từ controller sang service**. Stateless cần được kiểm tra cả chuỗi collaborator.

### 6.4. Controller singleton đúng cách

`src/main/java/com/example/lifecycle/web/GreetingController.java`:

```java
package com.example.lifecycle.web;

import java.util.Map;
import com.example.lifecycle.service.GreetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/api/greeting")
    public Map<String, String> greeting(
            @RequestParam(name = "name", defaultValue = "bạn") String name) {

        String message = greetingService.greet(name);
        return Map.of("message", message);
    }
}
```

Trong ví dụ này:

- Spring tạo một `GreetingController` singleton thông thường.
- Constructor injection gắn collaborator cần thiết; `private final` ngăn gán lại reference sau construction.
- `name`, `message` và `Map.of(...)` đều được tạo/sử dụng trong lần gọi handler hiện tại. `Map.of` trả immutable map nên không gây thêm mutable state bị chia sẻ từ phần controller.
- Không có `lastName` hoặc `currentUser` làm instance field. Hai request đồng thời gọi cùng bean nhưng mỗi request tính response từ input riêng.
- `GreetingService` cũng singleton, vẫn ổn vì không lưu dữ liệu request vào field.

**Lưu ý:** `final GreetingService greetingService` bảo vệ reference, **không tự làm cho service thread-safe**. Nếu service chứa mutable `HashMap`, list, cache không đồng bộ hoặc object thay đổi dưới nhiều thread thì vẫn cần thiết kế lại.

### 6.5. Chạy và quan sát response

Trên Windows PowerShell:

```powershell
.\gradlew.bat clean test
.\gradlew.bat bootRun
```

Trên Linux/macOS:

```bash
./gradlew clean test
./gradlew bootRun
```

Ở terminal thứ hai, gửi request:

```bash
curl -i "http://localhost:8080/api/greeting?name=An"
curl -i "http://localhost:8080/api/greeting?name=Binh"
curl -i "http://localhost:8080/api/greeting"
```

Các body JSON tương ứng:

```json
{"message":"Xin chào, An!"}
```

```json
{"message":"Xin chào, Binh!"}
```

```json
{"message":"Xin chào, bạn!"}
```

Status mong đợi là HTTP 200, cùng JSON `Content-Type` do message converter tạo. Header đầy đủ, thứ tự header và log startup phụ thuộc môi trường; ví dụ chỉ cam kết nội dung được code tạo ra.

## 7. Kiểm thử singleton identity và request independence

File `src/test/java/com/example/lifecycle/GreetingControllerTest.java`:

```java
package com.example.lifecycle;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lifecycle.web.GreetingController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class GreetingControllerTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    MockMvc mvc;

    @Test
    void controllerIsSameSingletonBeanInThisContext() {
        GreetingController first = context.getBean(GreetingController.class);
        GreetingController second = context.getBean(GreetingController.class);
        assertSame(first, second);
    }

    @Test
    void independentRequestsProduceIndependentResponses() throws Exception {
        mvc.perform(get("/api/greeting").param("name", "An"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xin chào, An!"));

        mvc.perform(get("/api/greeting").param("name", "Binh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xin chào, Binh!"));

        mvc.perform(get("/api/greeting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xin chào, bạn!"));
    }
}
```

Điểm kiểm chứng:
- `assertSame` chứng minh hai lần lấy **cùng bean definition trong cùng context** trả cùng một reference. Nó không chứng minh trong toàn JVM chỉ có một instance.
- MockMvc kiểm chứng mapping, binding và JSON theo ứng dụng Spring MVC.
- Các MockMvc request ở đây chạy **tuần tự**, nên **chưa chứng minh** một controller không bao giờ có bug concurrency. Phần mã nguồn stateless và thí nghiệm `SharedStateRace` mới giải thích bản chất việc không dùng shared request state.
- Một integration test chạy HTTP concurrent trên server thật có thể phát hiện một số race, nhưng không thể chứng minh tuyệt đối mọi lịch xen kẽ đều an toàn. Đánh giá thiết kế/ownership của mutable state vẫn là quan trọng.

## 8. Quy tắc thiết kế an toàn: state thuộc đâu?

Khi cần lưu dữ liệu, trước tiên xác định **lifecycle và owner** của dữ liệu, rồi chọn nơi lưu phù hợp:

| Loại dữ liệu | Nơi nên để | Ví dụ |
|---|---|---|
| Chỉ dùng trong một lời gọi handler | Tham số/biến local, DTO | `name`, `orderId`, `validatedInput` |
| Gắn với một HTTP request | Request attribute hoặc `@RequestScope` | Request correlation context, dữ liệu tính toán tạm |
| Dành cho một phiên truy cập | Session store / `@SessionScope` có kiểm soát | Wizard form state, tùy chọn giao diện |
| Dữ liệu nghiệp vụ bền vững | Database hoặc nguồn lưu trữ có transaction/consistency | Giỏ hàng bền vững, đơn hàng, số dư |
| Cache dùng chung | Cache API và chính sách đồng bộ/TTL thích hợp | Dữ liệu tham khảo có thể cache |
| Bộ đếm metrics cục bộ của một process | Metrics registry hoặc atomic/adder phù hợp | Số request xử lý trên một instance |
| Cấu hình bất biến | Immutable configuration object hoặc constructor-injected collaborator | Endpoint base URL, quy tắc định dạng |
| Dữ liệu gắn với người dùng đang xác thực | Request/security context được framework quản lý | Principal/authorities cho request hiện tại |

Bảo mật dữ liệu yêu cầu **xác định quyền trên từng request/operation**, không lưu user hiện tại trong `static` hoặc singleton field. Một instance ứng dụng có thể phục vụ nhiều users/tenants đồng thời.

### `@RequestScope` khi thật sự cần đối tượng theo request

Có lúc nhiều thành phần trong cùng một request cần chung một object tạm. Khi đó dùng `@RequestScope`:

```java
package com.example.lifecycle.web;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class RequestMarker {

    private final String id = UUID.randomUUID().toString();

    public String id() {
        return id;
    }
}
```

Controller singleton có thể inject bean này:

```java
package com.example.lifecycle.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestMarkerController {

    private final RequestMarker marker;

    public RequestMarkerController(RequestMarker marker) {
        this.marker = marker;
    }

    @GetMapping("/api/marker")
    public Map<String, String> marker() {
        return Map.of("requestId", marker.id());
    }
}
```

Hai file Java trên là **phần mở rộng độc lập**, đặt cùng source tree của project ở mục 6; không cần thêm thư viện ngoài starter web. `@RequestScope` là shortcut cho request scope và **mặc định sử dụng class-based scoped proxy**. Singleton `RequestMarkerController` giữ một proxy ổn định; tại mỗi request, lời gọi `marker.id()` được chuyển tới **target `RequestMarker` của request hiện tại**. Như vậy constructor injection của singleton không phải tạo request object tại startup.

Khi gọi `/api/marker` hai lần, giá trị UUID trong response **thường khác** do mỗi request có instance marker riêng. UUID là dữ liệu ngẫu nhiên nên không thể ghi stdout chính xác cố định; test tốt có thể kiểm tra id hợp lệ và khác nhau giữa hai request, nếu muốn. Một request có thể gọi `marker.id()` nhiều lần và nhận cùng id vì nó dùng cùng target bean.

**Không cần tự tạo request-scoped controller** cho endpoint CRUD thông thường: request-local variables đủ cho nhiều trường hợp, ít overhead và đơn giản hơn. `@RequestScope` hữu ích khi thật sự cần một collaborator có identity/state theo request.

### `@SessionScope` không tự làm state thread-safe

`@SessionScope` có một instance cho mỗi HTTP session, nhưng **hai request của cùng người dùng** (hai tab trình duyệt, AJAX calls song song) vẫn có thể dùng cùng session bean đồng thời.

Ví dụ:

```java
@Component
@SessionScope
public class WizardState {

    private int step;

    public int advance() {
        return ++step;
    }
}
```

Đây là snippet để phân tích, không phải khuyến nghị dùng cho sản phẩm. `++step` không atomic. Nếu hai request trong cùng session cùng gọi `advance()`, có thể mất một lần tăng. Cần thiết kế cập nhật có đồng bộ hoặc mô hình lưu trữ/trạng thái phù hợp; với hệ thống multi-instance, phải cân nhắc session replication, serialization và lưu trữ tập trung. Hơn nữa, session bean không mặc định là cache toàn cục cho tất cả người dùng.

### `prototype` có phải giải pháp cho controller?

`@Scope("prototype")` yêu cầu Spring tạo object mới mỗi lần container **được yêu cầu lấy bean prototype**, không có nghĩa tự động có một instance mới cho **mỗi HTTP request** trong cơ chế MVC thông thường. Nếu inject prototype vào singleton tại thời điểm khởi tạo, singleton có thể giữ cùng một prototype instance suốt vòng đời của nó. Vì vậy **đừng biến controller sang prototype chỉ để chữa shared request data**. Hãy thiết kế stateless hoặc sử dụng request scope đúng mục đích.

Nếu một singleton cần tạo các object stateful mới cho từng thao tác, có thể dùng `ObjectProvider<T>.getObject()` hoặc factory rõ ràng để cấp một prototype mới **mỗi lần gọi**. Đây là cơ chế lookup bean, không là cơ chế tự động biến prototype thành request-scoped.

## 9. Các biện pháp đồng bộ: biết đúng giới hạn

Có trường hợp state **thực sự thuộc process** và cần shared, ví dụ một metrics counter. Khi đó concurrency primitive có ích, nhưng không nên dùng chúng để vá nhầm đời sống của dữ liệu user.

### `AtomicInteger` và phép tăng số đếm

Sai:

```java
private int count;

public int next() {
    return ++count;
}
```

Hai thread có thể đọc cùng giá trị cũ và mất update. Nếu đây là số đếm **cục bộ cho một instance JVM**:

```java
import java.util.concurrent.atomic.AtomicInteger;

private final AtomicInteger count = new AtomicInteger();

public int next() {
    return count.incrementAndGet();
}
```

`incrementAndGet()` thực hiện tăng atomic trên một `AtomicInteger`. Nhưng counter vẫn chỉ thuộc process; khi chạy 3 replicas hoặc restart, các process có state riêng, không tạo ra chuỗi số ID toàn hệ thống bền vững. Với business IDs hay tiền/tồn kho, dùng database sequence, atomic update, transaction hoặc cơ chế consistency được thiết kế phù hợp.

### `synchronized` / lock

`synchronized` có thể đảm bảo mutual exclusion của một critical section trong **cùng JVM/monitor**. Nhưng:

- Đồng bộ toàn bộ method handler làm nhiều request bị tuần tự hóa, giảm throughput.
- Nếu trả kết quả dựa trên field user global, việc tạo lock không tự làm lifecycle/semantics đúng.
- Lock trong một process không bảo vệ cùng business record khi ứng dụng chạy nhiều instances.
- Lock và state sharing phức tạp dễ gây deadlock, contention và lỗi khó kiểm thử.

**Ưu tiên loại bỏ request-specific state khỏi singleton**, thay vì dùng global lock.

### Concurrent collections và compound operations

`ConcurrentHashMap` giúp nhiều thao tác collection có contract thread-safe. Tuy nhiên chuỗi `containsKey` → `put` vẫn có thể có race ở cấp **nghiệp vụ**. Dùng atomic methods thích hợp như `computeIfAbsent` hoặc `merge` khi semantics yêu cầu; đánh giá function callback và side effects. Một collection thread-safe không tự đảm bảo "kiểm tra rồi cập nhật" trên nhiều resources/database transactions có tính atomic.

### Immutable objects

Một class có `final` fields trỏ tới immutable values (ví dụ `String`, `record` chứa immutable components) thường giúp tránh mutation ngoài ý muốn. Tuy nhiên:
- `final List<T>` chỉ khóa reference, **không** biến `ArrayList` thành immutable.
- Một `record` có field tham chiếu tới mutable object cũng không deep-immutable.
- Stateless controller có thể giữ immutable cấu hình và references đến services, repositories, clients miễn các collaborator được dùng phù hợp với concurrency contract.

## 10. Sai lầm liên quan request context và ThreadLocal

`ThreadLocal` có thể lưu context gắn với một thread, ví dụ correlation information trong logging. Nhưng servlet containers **tái sử dụng worker threads**; không cleanup sẽ để context request cũ rò sang request sau. Với async execution, xử lý có thể chuyển sang thread khác và `ThreadLocal` không tự đi theo request.

Một ví dụ chỉ mang tính minh họa:

```java
private static final ThreadLocal<String> CURRENT_ID = new ThreadLocal<>();

void process(String id) {
    CURRENT_ID.set(id);
    try {
        // xử lý đồng bộ trên thread hiện tại
    } finally {
        CURRENT_ID.remove();
    }
}
```

Đoạn này **không** tạo request-scope thực thụ, không thay thế `SecurityContext`/request attributes và không tự truyền state sang `@Async`, `CompletableFuture` hoặc asynchronous MVC dispatch. Nếu cần context cross-thread, hãy dùng cơ chế propagation được framework/thư viện quan sát/bảo mật cung cấp, với chính sách rõ ràng.

### HttpServletRequest, @RequestParam và Principal

Trong controller, nhận request data qua method parameters là cách trực tiếp:

```java
@GetMapping("/api/me")
public Map<String, String> whoAmI(java.security.Principal principal) {
    return Map.of("name", principal.getName());
}
```

Đây là snippet phụ thuộc việc security principal có mặt. Nếu không cấu hình authentication, `principal` có thể là `null` và ví dụ trên cần xử lý trường hợp đó. Ý ở đây là **đừng lưu principal của request vào field singleton**. Đối tượng được truyền vào handler hoặc lấy qua API security đúng thời điểm thuộc ngữ cảnh request; nó không nên được cache như "người dùng hiện tại" toàn ứng dụng.

## 11. Async controller và virtual threads có làm thay đổi kết luận không?

Không. Dù Servlet container sử dụng platform worker threads, async MVC executor, hoặc mô hình virtual threads trong một số cấu hình, singleton controller vẫn là một object **được nhiều luồng thực thi sử dụng**. Việc đổi loại thread không đổi scope bean.

Ví dụ controller trả `Callable<String>`:

```java
@GetMapping("/api/async-greeting")
public java.util.concurrent.Callable<String> asyncGreeting(
        @RequestParam(name = "name") String name) {

    return () -> "Xin chào, " + name + "!";
}
```

Đây là đoạn controller đặt bên trong `@RestController` hiện hữu. `name` là local/parameter đã được capture theo từng lời gọi, nên không cần field chung. Spring MVC có thể thực thi `Callable` qua configured async executor và thực hiện async dispatch hoàn tất response. Nếu lambda đọc/ghi một mutable field singleton thay vì capture input request, race vẫn tồn tại.

Với request-scoped beans và security/request contexts, async execution có quy tắc propagation/lifecycle riêng; không dựa vào `ThreadLocal` thủ công để suy luận bean luôn hiện diện trên mọi thread. Lập trình theo thread-safe state ownership vẫn là nền tảng.

## 12. Bảng kiểm tra nhanh trong code review

| Dấu hiệu trong controller/service | Câu hỏi cần đặt | Hướng xử lý thường phù hợp |
|---|---|---|
| `private String currentUser;` | Đây có phải giá trị thay đổi theo request/user? | Bỏ field; nhận làm tham số hoặc lấy từ request/security context. |
| `private Map<String, Cart> carts = new HashMap<>();` | Map có được nhiều request truy cập đồng thời, có cần bền vững/multi-node? | Thiết kế repository/store/cache với concurrency và ownership rõ. |
| `private final List<...> cache = new ArrayList<>();` | `final` chỉ khóa reference, list có đang bị mutate? | Dùng immutable snapshot hoặc cache thread-safe phù hợp. |
| `private int requestCount;` và `++requestCount` | Cần số đếm process hay hệ thống? | Metrics registry, atomic primitive hoặc backend counter đúng phạm vi. |
| `@Scope("prototype")` trên controller | Có kỳ vọng một instance mỗi request? | Xem lại semantics; ưu tiên controller stateless. |
| `@SessionScope` có nhiều field mutable | Cùng session có nhiều request song song? | Bảo vệ cập nhật/thiết kế session state rõ. |
| `ThreadLocal` nhưng không có `remove()` | Container có reuse thread? | Cleanup ở `finally` và xác định async propagation. |
| `synchronized` trên handler | Đang dùng lock để vá state đặt sai chỗ? | Di chuyển state đúng owner; nếu cần shared data, chọn atomic operation/transaction. |
| Inject singleton service có mutable field | Controller stateless nhưng service có an toàn không? | Rà soát toàn bộ dependency chain. |
| `static` mutable field | Dữ liệu có bị chia sẻ ngoài dự kiến qua class? | Tránh cho request/user state; cô lập hoặc quản lý rõ process-global state. |

## 13. Bài tập và câu hỏi tự kiểm tra

### Bài tập 1: chỉ ra biến nguy hiểm

```java
@RestController
class CartController {

    private String cartId;
    private final CartService service;

    CartController(CartService service) {
        this.service = service;
    }

    @GetMapping("/api/cart")
    CartDto getCart(@RequestParam String id) {
        cartId = id;
        return service.load(cartId);
    }
}
```

Hãy xác định vì sao response có thể nhầm giỏ hàng khi có request A, B đồng thời. Viết lại handler thành `return service.load(id);` và xóa field `cartId`. Nếu `CartService` lưu `lastLoadedCart` làm mutable singleton field, bài toán vẫn chưa được giải quyết.

### Bài tập 2: thử scope

Thêm `RequestMarker` và `RequestMarkerController` ở mục 8 vào ứng dụng chạy. Gửi hai request `/api/marker`, quan sát UUID khác nhau giữa các request. Trong **một** handler, gọi `marker.id()` hai lần rồi so sánh; chúng dùng cùng request-scoped target khi cùng request còn hiệu lực.

### Bài tập 3: mô hình nhiều instances

Giả sử load balancer gửi requests tới hai ứng dụng độc lập A và B. Mỗi ứng dụng có singleton bean giữ `AtomicInteger counter`. Nếu client gọi `/counter` nhiều lần, counter có bảo đảm tăng toàn cục không? **Không**: A/B có object và giá trị khác nhau. Hãy nêu khi nào nên dùng shared database/metrics backend.

### Câu hỏi thường gặp

**1. Spring có tạo controller mới cho mỗi HTTP request không?**  
Mặc định không. `@RestController` là Spring bean singleton theo mỗi bean definition và context. Request mapping thường gọi cùng instance.

**2. Singleton bean có nghĩa controller không thread-safe?**  
Không mặc nhiên. Singleton stateless hoặc có shared state được quản lý đúng vẫn có thể thread-safe. Rủi ro xảy ra khi nhiều luồng truy cập mutable state mà không có ownership/đồng bộ phù hợp.

**3. Biến local `String name` trong handler có bị request khác ghi đè không?**  
Không theo cách một instance field bị ghi đè; mỗi invocation có local variables riêng. Nhưng một local reference trỏ tới object mutable được chia sẻ vẫn có thể gây race.

**4. Dùng `volatile` cho `currentUser` có an toàn không?**  
Không. Nó không tạo sự cách ly theo request; user A vẫn có thể đọc tên của user B.

**5. Dùng `@RequestScope` cho tất cả controllers có tốt hơn không?**  
Thường không cần. Controller stateless singleton là cấu trúc đơn giản và phù hợp. Chỉ dùng request scope khi thật sự cần object có vòng đời gắn với request.

**6. Inject `prototype` vào singleton có tạo object mới ở mỗi request không?**  
Không. Dependency thường được resolve lúc xây dựng singleton; cần factory/provider nếu muốn tạo prototype mỗi lần gọi.

**7. `@SessionScope` có tránh race giữa các requests cùng user không?**  
Không mặc nhiên. Hai request cùng session có thể đồng thời mutate một bean session-scoped.

**8. Có thể để mọi business state trong singleton service rồi chỉ làm controller stateless?**  
Không nên nếu business state riêng user/request vẫn bị chia sẻ. Hãy dùng local values, request/session scope khi phù hợp, persistence hoặc hệ thống state có thiết kế concurrency đúng.

## 14. Nguồn đọc thêm

- Spring Framework Reference — Bean Scopes, singleton/prototype/request/session, scoped proxies: https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
- Spring Framework Reference — Classpath Scanning and Managed Components, `@RequestScope` và `@SessionScope`: https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html
- Spring Framework Reference — Spring MVC annotated controllers: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html
- Spring Framework Reference — MVC asynchronous requests: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Spring Boot 3.5 — Web applications: https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html
- Java SE 17 — `java.util.concurrent`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html


</details>

- [Quay lại đầu trang](#back-to-top)
