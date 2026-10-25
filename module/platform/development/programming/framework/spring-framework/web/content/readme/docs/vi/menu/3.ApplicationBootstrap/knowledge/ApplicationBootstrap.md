<a id="back-to-top"></a>

# Khởi tạo ứng dụng

## Menu
- [Thiết lập phụ thuộc và cấu trúc ứng dụng Spring Web tối thiểu](#application-bootstrap-001)
- [Đăng ký DispatcherServlet bằng Java và nhận biết cấu hình web.xml](#application-bootstrap-002)
- [WebApplicationContext gốc, ngữ cảnh servlet và quan hệ cha–con](#application-bootstrap-003)
- [Ánh xạ servlet, context path và đường dẫn của ứng dụng](#application-bootstrap-004)
- [@EnableWebMvc, WebMvcConfigurer và cấu hình XML tương đương](#application-bootstrap-005)
- [Phân biệt cấu hình Spring MVC trực tiếp với tự động cấu hình của Spring Boot](#application-bootstrap-006)
- [Triển khai trên Servlet container và phân biệt WAR với máy chủ nhúng](#application-bootstrap-007)

## <a id="application-bootstrap-001">Thiết lập phụ thuộc và cấu trúc ứng dụng Spring Web tối thiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết lập phụ thuộc và cấu trúc ứng dụng Spring Web tối thiểu

## Mục tiêu

Sau bài này, bạn có thể tự dựng một ứng dụng HTTP nhỏ bằng Spring Web MVC, biết vì sao từng dependency xuất hiện, đặt đúng các lớp vào cấu trúc package và chạy thử endpoint. Bạn cũng phân biệt hai cách khởi tạo thường gặp:

1. **Spring Framework Web MVC thuần**: tự khai báo `spring-webmvc`, cấu hình `DispatcherServlet` và triển khai trên Servlet container.
2. **Spring Boot + Spring Web MVC**: dùng starter và auto-configuration để có ứng dụng chạy độc lập với embedded server. Đây là cách triển khai ví dụ chạy được ở phần chính.

Hai cách cùng dựa trên Spring MVC/Servlet stack. Spring Boot là lớp hỗ trợ cấu hình và khởi động ứng dụng, **không phải tên thay thế của Spring MVC**.

## 1. Bức tranh tối thiểu: một HTTP request đi qua đâu?

```text
HTTP client
    |
    | GET /api/hello?name=Ada
    v
Servlet container (embedded Tomcat hoặc container bên ngoài)
    |
    v
Spring MVC DispatcherServlet
    |
    +--> HandlerMapping tìm handler khớp GET /api/hello
    +--> HandlerAdapter gọi phương thức controller
    +--> HttpMessageConverter ghi body JSON/text
    |
    v
HTTP response
```

Các vai trò:

| Thành phần | Trách nhiệm trong ứng dụng tối thiểu |
|---|---|
| Java/JDK | Biên dịch và chạy code ứng dụng. |
| Gradle/Maven | Tải dependencies, biên dịch, test, đóng gói. |
| `spring-web` | Hỗ trợ HTTP, request/response, một số abstraction và tiện ích web. |
| `spring-webmvc` | Bổ sung Spring MVC, `DispatcherServlet`, ánh xạ handler và xử lý request theo Servlet stack; có dependency chuyển tiếp đến `spring-web`. |
| Servlet container | Tiếp nhận kết nối/request HTTP và chạy Servlet; ví dụ Tomcat. |
| `@Controller` / `@RestController` | Lớp xử lý endpoint do Spring quản lý. |
| JSON library / converter | Đổi dữ liệu Java sang JSON và ngược lại, khi endpoint cần JSON. |
| Spring Boot starter/auto-configuration | Gom dependency tương thích, cấu hình phần thông dụng, khởi động embedded server. |

**Điểm phân biệt:** chỉ thêm `spring-webmvc` vào project Java không tự sinh một HTTP server đang lắng nghe cổng 8080. Với Spring MVC thuần, bạn còn phải cấu hình và triển khai Servlet; với Spring Boot web starter, Boot tạo và cấu hình embedded Servlet container theo convention.

## 2. Điều kiện cần và lựa chọn phiên bản nhất quán

Ví dụ chính dùng **Java 17**, **Gradle 8.5**, **Spring Boot 3.5.16** và Spring MVC thuộc Spring Framework 6.2.x do Boot quản lý. Đây là một tổ hợp được tài liệu yêu cầu hệ thống của nhánh Boot 3.5 hỗ trợ: Java từ 17, Gradle 8.4+ trong nhánh 8.x. Không dùng đồng thời Spring Boot 4.x với bộ dependency/Gradle cũ nếu chưa đối chiếu yêu cầu phiên bản mới.

Kiểm tra công cụ:

```bash
java -version
javac -version
gradle -version
```

Nếu project đã có **Gradle Wrapper** (`gradlew` / `gradlew.bat`), dùng wrapper thay cho Gradle cài global. Wrapper khóa phiên bản Gradle để các máy cùng xây dựng một project nhất quán. Cần có JDK, không chỉ JRE, để biên dịch.

Để tạo project mới, có thể tạo các tệp được trình bày dưới đây bằng tay hoặc dùng https://start.spring.io/ với:
- Project: Gradle - Groovy hoặc Maven
- Language: Java
- Spring Boot: một bản thuộc nhánh **3.5.x** tương thích Java và build tool
- Packaging: Jar
- Java: 17
- Dependency: **Spring Web**

Ví dụ này nêu rõ `3.5.16` nhằm tái hiện cấu hình; ở project thực tế, cập nhật lên bản vá đang được hỗ trợ trong nhánh bạn chọn và đồng bộ plugin/BOM theo tài liệu chính thức.

## 3. Cấu trúc thư mục của ứng dụng Gradle tối thiểu

```text
spring-web-minimal/
├── settings.gradle
├── build.gradle
├── gradlew                     # Gradle Wrapper trên Unix/macOS
├── gradlew.bat                 # Gradle Wrapper trên Windows
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/example/webapp/
    │   │       ├── WebApplication.java
    │   │       └── controller/
    │   │           └── HelloController.java
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/
            └── com/example/webapp/
                └── HelloControllerTest.java
```

Các tệp `gradlew`, `gradlew.bat` và `gradle/wrapper/*` được tạo bởi Gradle Wrapper hoặc Spring Initializr; không cần tự viết nội dung nhị phân của `gradle-wrapper.jar`. Trong lần thử tối thiểu, test file có thể được thêm sau. Nếu tạo bằng tay và máy đã có Gradle thích hợp, lệnh `gradle wrapper --gradle-version 8.5` sẽ tạo wrapper; sau đó ưu tiên gọi wrapper.

Giải thích:

- `settings.gradle` đặt tên project.
- `build.gradle` mô tả plugin, repositories, dependency, Java toolchain và tác vụ build.
- `src/main/java` chứa nguồn chương trình; tên package Java khớp cấu trúc thư mục.
- `src/main/resources` chứa cấu hình được đưa vào classpath lúc chạy.
- `src/test/java` tách bài test khỏi code production.
- `WebApplication` đặt ở package gốc `com.example.webapp`; controller bên dưới `com.example.webapp.controller` để **component scan mặc định** của Boot tìm thấy.
- Với project thực tế, thêm package `service`, `repository`, `config` **khi có nhiệm vụ tương ứng**; không cần tạo các lớp giả chỉ để đủ sơ đồ.

## 4. Khai báo dependency bằng Gradle (Groovy DSL)

Tệp `settings.gradle`:

```groovy
rootProject.name = 'spring-web-minimal'
```

Tệp `build.gradle`:

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

**Vì sao khai báo như vậy?**

- `java` bật compile Java và cấu trúc source set tiêu chuẩn.
- `org.springframework.boot` mang các task Boot như `bootRun` và `bootJar`, hỗ trợ đóng gói executable JAR.
- `io.spring.dependency-management` cho phép quản lý phiên bản thư viện theo dependency management của Spring Boot; trong cấu hình này, không ghi phiên bản riêng cho starter.
- `mavenCentral()` là kho tải thư viện đã công bố.
- `spring-boot-starter-web` kéo vào Spring MVC, các thành phần cần để làm web servlet, Jackson cho JSON và Tomcat nhúng mặc định trong nhánh 3.5.
- `spring-boot-starter-test` đưa các thư viện kiểm thử thường dùng (JUnit Jupiter, Spring Test, assertion/mocking tùy starter).
- `useJUnitPlatform()` cho task `test` chạy bộ kiểm thử JUnit Platform.
- Java toolchain giúp thống nhất JDK biên dịch; máy cần cài toolchain tương ứng hoặc cấu hình cơ chế tự tải toolchain.

Đừng thêm đồng thời một phiên bản `spring-webmvc` tùy ý khi đã dùng starter: Spring Boot dependency management đã lựa chọn bộ Spring Framework tương thích. Nếu buộc override một dependency, cần kiểm tra khả năng tương thích, vì cập nhật riêng một module Spring có thể tạo xung đột binary.

### Nếu dùng Maven thay Gradle

Hai build tool là **hai lựa chọn thay thế**, không cần đặt cả `pom.xml` và `build.gradle` cho cùng ví dụ. Tệp `pom.xml` tương đương cơ bản:

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

    <groupId>com.example</groupId>
    <artifactId>spring-web-minimal</artifactId>
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

Maven lấy phiên bản dependency từ parent `spring-boot-starter-parent`; với project Maven thông thường chạy `./mvnw spring-boot:run` hoặc `mvn spring-boot:run` nếu đã cài Maven. Chọn Gradle hay Maven theo hệ thống build của project; logic Spring Web phía Java vẫn như nhau.

## 5. Lớp khởi động ứng dụng

Tệp `src/main/java/com/example/webapp/WebApplication.java`:

```java
package com.example.webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
```

Đây là lớp Java độc lập về cú pháp nhưng **phải build cùng dependencies Spring Boot** mới biên dịch được. `SpringApplication.run` tạo Spring `ApplicationContext`, áp dụng auto-configuration, tìm beans và khởi động embedded server theo dependency trên classpath.

`@SpringBootApplication` kết hợp các vai trò cấu hình ứng dụng, auto-configuration và component scanning. Vì lớp này ở `com.example.webapp`, Spring mặc định phát hiện các bean bên dưới package đó, gồm `com.example.webapp.controller.HelloController`.

Nếu di chuyển lớp khởi động xuống `com.example.webapp.bootstrap` còn controller ở package anh em, scan mặc định có thể bỏ sót controller. Cách đơn giản là giữ lớp khởi động ở package cha chung.

## 6. Controller HTTP tối thiểu

Tệp `src/main/java/com/example/webapp/controller/HelloController.java`:

```java
package com.example.webapp.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, String> hello(
            @RequestParam(defaultValue = "bạn") String name) {
        return Map.of("message", "Xin chào, " + name + "!");
    }
}
```

Giải thích dòng chạy:

1. `@RestController` đánh dấu bean web controller; return value thường được viết vào HTTP response body thông qua `HttpMessageConverter` thay vì hiểu là view name.
2. `@GetMapping("/api/hello")` ánh xạ HTTP GET với path `/api/hello`. Không có controller bean thì mapping này không tồn tại.
3. `@RequestParam` lấy tham số query `name`; khi không cung cấp, `defaultValue = "bạn"` được dùng. Nếu không có tên tham số được compiler giữ lại, cần khai báo tường minh `@RequestParam(name = "name", defaultValue = "bạn")` để tránh phụ thuộc metadata tên tham số. Để ví dụ vững chắc giữa cấu hình build, nên dùng dạng khai báo tường minh như bên dưới.
4. `Map<String, String>` là object Java; Jackson và HTTP message converter của starter chuyển nó sang JSON khi response được xử lý.
5. Một key JSON duy nhất giúp kết quả minh họa ổn định; với nhiều key, đừng dựa vào thứ tự thuộc tính JSON trừ khi cấu hình rõ.

Để tránh phụ thuộc cách biên dịch giữ tên tham số, có thể thay đúng khai báo tham số ở trên thành:

```java
@RequestParam(name = "name", defaultValue = "bạn") String name
```

Trong cả hai trường hợp, client có thể gọi `/api/hello?name=Ada` để nhận dữ liệu cá nhân hóa.

Tệp `src/main/resources/application.properties`:

```properties
spring.application.name=spring-web-minimal
server.port=8080
```

`server.port` là cấu hình **Spring Boot** cho embedded web server; nó không tự có tác dụng nếu bạn chỉ chạy Spring Framework MVC thuần bên trong Tomcat ngoài. Nếu không khai báo port, Boot web app mặc định thường dùng 8080. Không cần tạo `web.xml`, `DispatcherServlet` bean thủ công, hay cấu hình Tomcat bằng Java cho ví dụ Boot này.

### Phiên bản controller hoàn toàn tường minh để copy dùng ngay

Để bài thực hành không lệ thuộc metadata tên tham số, đây là toàn văn controller đã chọn cho ví dụ chạy:

```java
package com.example.webapp.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, String> hello(
            @RequestParam(name = "name", defaultValue = "bạn") String name) {
        return Map.of("message", "Xin chào, " + name + "!");
    }
}
```

Hai khối controller ở mục này minh họa cùng một endpoint; trong project **chỉ tạo một file `HelloController.java`**, với bản tường minh cuối cùng.

## 7. Chạy và xác minh request/response

Ở thư mục gốc project Gradle (nơi có `build.gradle`):

**Windows PowerShell:**

```powershell
.\gradlew.bat clean test
.\gradlew.bat bootRun
```

**Linux/macOS:**

```bash
./gradlew clean test
./gradlew bootRun
```

`bootRun` giữ ứng dụng hoạt động trong terminal; mở terminal thứ hai để gửi request. Ở lần đầu chạy, Gradle có thể cần Internet để tải plugin/dependencies. Đợi ứng dụng thực sự khởi động thành công và server lắng nghe cổng 8080 trước khi thử HTTP.

Dùng `curl.exe` trên Windows, hoặc `curl` trên macOS/Linux:

```powershell
curl.exe -i "http://localhost:8080/api/hello?name=Ada"
```

Ví dụ response body JSON:

```json
{"message":"Xin chào, Ada!"}
```

Header thông thường bao gồm `HTTP/1.1 200` và `Content-Type: application/json` (chi tiết header/status line có thể khác theo server). Khi bỏ query:

```powershell
curl.exe "http://localhost:8080/api/hello"
```

Body mong đợi:

```json
{"message":"Xin chào, bạn!"}
```

Khi gọi `http://localhost:8080/api/missing` và không có handler cho path này, ứng dụng mặc định trả lỗi HTTP 404, dù cấu trúc/JSON lỗi chi tiết phụ thuộc phiên bản và cấu hình.

Đóng gói và chạy bằng executable JAR:

```powershell
.\gradlew.bat bootJar
java -jar build/libs/spring-web-minimal-0.0.1-SNAPSHOT.jar
```

Nếu đổi `rootProject.name` hoặc `version`, tên JAR có thể thay đổi theo cấu hình build. `bootJar` chứa các dependency runtime và cơ chế khởi động cần thiết cho Spring Boot, khác với JAR Java thư viện thông thường.

## 8. Test endpoint bằng Spring Boot + MockMvc

Có thể kiểm tra route và JSON mà không cần mở HTTP server thật trên cổng cố định. Tệp `src/test/java/com/example/webapp/HelloControllerTest.java`:

```java
package com.example.webapp;

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
    MockMvc mockMvc;

    @Test
    void helloUsesQueryParameter() throws Exception {
        mockMvc.perform(get("/api/hello").param("name", "Ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xin chào, Ada!"));
    }

    @Test
    void helloUsesDefaultName() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Xin chào, bạn!"));
    }
}
```

`@SpringBootTest` nạp application context; `@AutoConfigureMockMvc` tạo mock Servlet request layer để kiểm tra mapping và response. `mockMvc.perform(...)` không cần chiếm port 8080. Hai assertion kiểm tra ngữ nghĩa của endpoint, không phụ thuộc log và thứ tự JSON.

Chạy:

```powershell
.\gradlew.bat test
```

Khi thành công, Gradle task `test` kết thúc thành công; nếu chạy lại ngay và không có thay đổi, Gradle có thể báo `UP-TO-DATE`. Không gán một stdout cố định cho toàn bộ Gradle run vì log phụ thuộc phiên bản plugin, JDK và môi trường.

## 9. Spring Framework thuần khác gì? Cấu hình Servlet thủ công

Đây là một **phương án triển khai riêng**, để bạn thấy việc Spring Boot đã tự động làm hộ. Nếu chỉ dùng Spring Framework MVC, cần tối thiểu:

- `org.springframework:spring-webmvc` và dependency chuyển tiếp Spring.
- Servlet API tương ứng ở compile classpath.
- Lớp `@Configuration` có `@EnableWebMvc` + `@ComponentScan`.
- Đăng ký `DispatcherServlet` vào Servlet container bằng `WebApplicationInitializer` hoặc `web.xml`.
- Build WAR, triển khai WAR lên container tương thích (ví dụ Tomcat 10.1 cho Jakarta Servlet 6.0). Container ngoài phải chạy để nhận HTTP.

Ví dụ một **project khác**, dùng Gradle + Spring Framework 6.2.19 và Tomcat 10.1:

```text
spring-mvc-war/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/plainweb/
    ├── WebConfig.java
    ├── WebInitializer.java
    └── HelloController.java
```

`settings.gradle`:

```groovy
rootProject.name = 'spring-mvc-war'
```

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'war'
}

group = 'com.example'
version = '0.0.1'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-webmvc:6.2.19'
    compileOnly 'jakarta.servlet:jakarta.servlet-api:6.0.0'
}
```

Trong ví dụ WAR không dùng Spring Boot: Gradle plugin `war` tạo file WAR; `compileOnly` nghĩa là mã compile cần Servlet API nhưng server bên ngoài sẽ cung cấp runtime Servlet API. Không trộn trực tiếp các class `javax.servlet.*` cũ với Spring Framework 6.x vốn dùng `jakarta.servlet.*`.

`WebConfig.java`:

```java
package com.example.plainweb;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
@ComponentScan(basePackageClasses = WebConfig.class)
public class WebConfig {
}
```

`WebInitializer.java`:

```java
package com.example.plainweb;

import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

public class WebInitializer
        extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return null;
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[] {WebConfig.class};
    }

    @Override
    protected String[] getServletMappings() {
        return new String[] {"/"};
    }
}
```

`HelloController.java` trong project WAR:

```java
package com.example.plainweb;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public String hello() {
        return "Hello from Spring MVC";
    }
}
```

Khi Tomcat phát hiện `WebApplicationInitializer` theo cơ chế Servlet startup, initializer tạo `DispatcherServlet`, sử dụng `WebConfig` để bật các cấu hình MVC và quét bean controller. `@RestController` + trả chuỗi trong ví dụ này tạo **text response**; không yêu cầu Jackson.

Build bằng Gradle:

```bash
./gradlew war
```

Sau đó triển khai `build/libs/spring-mvc-war-0.0.1.war` vào Tomcat 10.1 chạy độc lập. Khi triển khai dưới context path mặc định theo tên WAR, request thường là:

```text
GET http://localhost:8080/spring-mvc-war-0.0.1/api/hello
```

Path thực tế phụ thuộc tên deployment/context path cấu hình trên server; có thể đổi context thành `/` để endpoint thành `/api/hello`. Không dùng `java -jar` để chạy WAR này như Boot JAR, vì không có embedded server/Boot launcher.

### So sánh các cách khởi tạo

| Tiêu chí | Spring Framework MVC thuần | Spring Boot + Spring MVC |
|---|---|---|
| Dependency chính | `spring-webmvc` | `spring-boot-starter-web` trong Boot 3.5 |
| HTTP server | Thường cần Servlet container bên ngoài | Embedded Tomcat mặc định |
| Cấu hình DispatcherServlet | Khai báo initializer hoặc `web.xml` | Boot auto-configuration |
| Khởi động | Deploy WAR lên container | `main()` + `SpringApplication.run` |
| MVC config | `@EnableWebMvc` trong cấu hình Java thuần | Convention + auto-configuration; thường không cần `@EnableWebMvc` |
| Triển khai | WAR truyền thống | Executable JAR thường dùng, có thể cấu hình WAR riêng |
| Khi hữu ích | Hiểu nền tảng, tích hợp hệ thống Servlet cũ | Khởi tạo dịch vụ web nhanh, giảm mã cấu hình |

**Chú ý về `@EnableWebMvc`:** annotation này hợp lý khi bạn tự cấu hình Spring MVC thuần. Với Spring Boot, thêm `@EnableWebMvc` vào cấu hình có thể chuyển quyền quản lý phần lớn MVC config sang bạn và làm mất những tùy chỉnh mặc định của Boot. Khi chỉ cần thêm interceptor, formatter, CORS hoặc resource handler trong Boot, trước hết cân nhắc `WebMvcConfigurer` mà không bật `@EnableWebMvc`.

## 10. Quy tắc tổ chức ứng dụng khi mở rộng

Cấu trúc hai lớp là đủ cho endpoint tối thiểu. Khi yêu cầu nghiệp vụ tăng, giữ trách nhiệm rõ:

```text
com.example.webapp/
├── WebApplication.java
├── controller/
│   └── ProductController.java   # HTTP, request/response, status
├── service/
│   └── ProductService.java      # Quy tắc nghiệp vụ
├── repository/
│   └── ProductRepository.java   # Truy cập dữ liệu nếu có
├── dto/
│   └── ProductResponse.java     # Hợp đồng dữ liệu HTTP
└── config/
    └── WebConfig.java           # Chỉ thêm khi cần custom MVC
```

Đây là sơ đồ gợi ý, **không phải các lớp bắt buộc** để ứng dụng Spring Web chạy. Spring container quản lý object đánh dấu `@Component`, `@Service`, `@Repository` và `@Controller`/`@RestController` khi scan được. Dependency nên được truyền qua constructor; controller chỉ xử lý giao tiếp HTTP, còn business logic phức tạp chuyển vào service để dễ kiểm thử.

### Khi nào dùng các dependency web khác?

- `spring-web` đơn thuần cung cấp nhiều API web/HTTP nhưng **không tương đương** đầy đủ với framework Spring MVC servlet.
- `spring-webmvc` là nền tảng `DispatcherServlet`, handler mappings và annotation controller của Servlet MVC.
- `spring-boot-starter-web` (nhánh Boot 3.5) gom MVC và server cho ứng dụng Servlet chạy độc lập.
- `spring-boot-starter-webflux` phục vụ stack reactive, có mô hình xử lý khác; không thêm chỉ vì muốn tạo REST endpoint đơn giản.
- Jackson chủ yếu cần khi làm JSON; Spring MVC có thể trả văn bản mà không cần Jackson.
- Spring Boot 4.x đã tái tổ chức tên web starter (ví dụ `spring-boot-starter-webmvc`). Không copy file build Boot 3.5 sang Boot 4 rồi kỳ vọng tất cả version/dependency giữ nguyên.

**Dependency tối thiểu** phụ thuộc kiểu triển khai: servlet MVC thuần và Boot MVC không cần cùng một danh sách thư viện khai báo trực tiếp.

## 11. Sự cố thường gặp và cách kiểm tra

| Hiện tượng | Nguyên nhân thường gặp | Bước xử lý |
|---|---|---|
| Build không tải được `spring-boot-starter-web` | Thiếu `mavenCentral()`, sai tọa độ, mạng/proxy, repository credentials. | Kiểm tra `repositories`, internet, `./gradlew dependencies` và lỗi resolve thực tế. |
| `Unsupported class file major version` hoặc toolchain failure | JDK/Gradle/plugin không tương thích, Gradle chạy bằng JDK khác. | Xem `java -version` và `./gradlew --version`; chọn bộ version được hỗ trợ. |
| `Port 8080 was already in use` | Ứng dụng khác giữ cổng 8080. | Dừng process chiếm cổng hoặc đặt `server.port=8081` rồi thử cổng mới. |
| HTTP 404 cho `/api/hello` | Sai path, sai HTTP method, controller nằm ngoài phạm vi scan, ứng dụng chưa chạy hoặc context path khác. | Kiểm tra package của `WebApplication`, annotation, mapping và URL thực tế. |
| HTTP 405 | Có route nhưng không hỗ trợ HTTP method đã gửi. | So khớp GET/POST với annotation. |
| Body trả lỗi chuyển đổi JSON | Thiếu JSON converter hoặc type/serialization lỗi. | Kiểm tra starter, Jackson và loại dữ liệu response. |
| Spring bean không tìm thấy | Package scan không bao phủ bean hoặc chưa đánh dấu stereotype/config. | Đặt app ở root package, kiểm tra bean declarations. |
| `ClassNotFoundException: jakarta.servlet...` | API Servlet vắng mặt lúc chạy WAR trên container hoặc phối phiên bản không tương thích. | Chạy WAR trên container Servlet/Jakarta phù hợp; tránh `javax.servlet` ở Spring 6.x. |
| Tạo WAR thuần nhưng chạy `java -jar` | WAR không có Boot launcher/embedded container. | Triển khai vào Tomcat hoặc đổi thiết kế sang Spring Boot executable JAR. |
| Tự thêm `@EnableWebMvc` trong Boot rồi static resource/MVC behavior thay đổi | Override phần auto-configuration MVC mặc định. | Gỡ annotation nếu không cần nắm toàn quyền MVC; dùng `WebMvcConfigurer` để mở rộng. |

Một số lệnh hữu ích trong project Gradle:

```bash
./gradlew --version
./gradlew dependencies --configuration runtimeClasspath
./gradlew clean test
./gradlew bootRun
```

Lệnh cuối chỉ tồn tại nếu project áp dụng Spring Boot Gradle plugin. Không suy ra nguyên nhân từ mỗi mã lỗi HTTP mà chưa kiểm tra log, mapping, port, context path và môi trường runtime.

## 12. Bài thực hành củng cố

1. Chạy ứng dụng Boot với endpoint `GET /api/hello?name=Ada` và xác nhận JSON.
2. Thêm endpoint `GET /api/ping` trả về một `Map` với key `status` và value `up`. Giữ controller hiện tại hoặc thêm controller mới dưới `com.example.webapp.controller`.
3. Đặt `server.port=8081`, chạy lại và thử đúng cổng; giải thích vì sao đổi port không đổi `@GetMapping`.
4. Di chuyển controller sang package **không nằm dưới** `com.example.webapp` (chỉ thử trong project mẫu), nhận diện nguyên nhân 404, rồi sửa lại cấu trúc package.
5. Nếu muốn khảo sát cơ chế gốc, build ví dụ WAR thuần và deploy lên Tomcat tương thích, đối chiếu context path và cách đăng ký `DispatcherServlet`.

### Tự kiểm tra

**Hỏi:** Thêm `spring-webmvc` vào một project Java đã đủ có HTTP server chưa?  
**Đáp:** Chưa. Spring MVC cần Servlet container và registration của `DispatcherServlet`; Spring Boot web starter hỗ trợ tự động khởi tạo embedded container.

**Hỏi:** Vì sao controller để trong `com.example.webapp.controller` dễ được tìm thấy?  
**Đáp:** `@SpringBootApplication` trên `com.example.webapp.WebApplication` kích hoạt component scan từ package của lớp khởi động xuống subpackages.

**Hỏi:** Có nên thêm `@EnableWebMvc` để endpoint Boot có thể chạy?  
**Đáp:** Không cần trong ví dụ Boot MVC thông thường. Boot đã auto-configure MVC; thêm annotation đó có thể thay đổi cấu hình mặc định.

**Hỏi:** Làm sao bảo đảm mọi máy dùng cùng phiên bản Gradle?  
**Đáp:** Commit Gradle Wrapper và chạy `./gradlew` hoặc `.\gradlew.bat`; đồng thời kiểm soát Java toolchain và phiên bản plugin/dependency.

## Tài liệu chính thức

- Spring Framework — DispatcherServlet: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html
- Spring Framework 6.2 — Servlet initialization: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/container-config.html
- Spring Framework 6.2 — Enable Web MVC configuration: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-config/enable.html
- Spring Boot 3.5 — System Requirements: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Spring Boot 3.5 — Embedded Web Servers: https://docs.spring.io/spring-boot/3.5/how-to/webserver.html
- Spring Initializr: https://start.spring.io/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-bootstrap-002">Đăng ký DispatcherServlet bằng Java và nhận biết cấu hình web.xml</a>

<details>
<summary>Xem chi tiết</summary>

# Đăng ký `DispatcherServlet` bằng Java và nhận biết cấu hình `web.xml`

## 1. Mục tiêu và bối cảnh

Sau bài này, bạn có thể:

1. Giải thích vì sao ứng dụng Spring MVC cần đăng ký `DispatcherServlet` với **Servlet container**.
2. Khởi tạo Spring MVC bằng Java thông qua `AbstractAnnotationConfigDispatcherServletInitializer`, không bắt buộc có `web.xml`.
3. Nhận biết cách viết trực tiếp `WebApplicationInitializer` và `ServletContext.addServlet(...)`.
4. Đọc cấu hình Servlet truyền thống trong `WEB-INF/web.xml` và đối chiếu từng phần với Java configuration.
5. Phân biệt **Servlet registration**, **Spring application context**, **Spring MVC configuration** và **Spring Boot auto-configuration**.
6. Xây dựng, đóng gói WAR, triển khai, gọi endpoint và xác định nguyên nhân một số lỗi khởi tạo thường gặp.

**Phạm vi:** Spring MVC trên **Jakarta Servlet stack**, minh họa bằng Java 17+, Spring Framework 6.2 và Tomcat 10.1. Các phiên bản cũ có thể dùng namespace `javax.servlet.*` và sơ đồ khởi tạo khác; không trộn `javax` và `jakarta` trong cùng ví dụ.

## 2. Tại sao phải đăng ký `DispatcherServlet`?

### 2.1. Servlet container và Spring MVC có trách nhiệm khác nhau

Trong ứng dụng WAR chạy trên Tomcat, Jetty hoặc Servlet container khác:

- **Servlet container** nhận kết nối HTTP, quản lý vòng đời Servlet, Filter, Listener và ánh xạ URL tới Servlet.
- **`DispatcherServlet`** là Servlet trung tâm của Spring MVC. Sau khi container chuyển request đến đây, nó tìm handler/controller phù hợp, xử lý dữ liệu đầu vào và tạo kết quả HTTP.
- **Spring `WebApplicationContext`** giữ các bean Spring (controller, service, converter, `HandlerMapping`, `HandlerAdapter`, …) phục vụ Servlet.

```text
          HTTP GET /mvc-demo/hello
                       |
               Tomcat / Servlet container
                       |
             Servlet URL mapping: "/"
                       |
               DispatcherServlet
                       |
              Spring WebApplicationContext
                       |
      HandlerMapping -> HandlerAdapter -> @GetMapping
                       |
                HelloController
                       |
               HTTP 200 + response body
```

Kể cả khi có một class mang `@RestController`, request **chưa thể đến class đó** nếu Servlet container không khởi tạo/ánh xạ HTTP entry point và Spring không tạo context chứa controller. Nói cách khác, **đăng ký Servlet** và **khai báo controller** là hai công việc khác nhau.

### 2.2. Cơ chế đăng ký Java lúc khởi động

Từ Servlet 3.0, container hỗ trợ đăng ký `Servlet` bằng Java. Trong ứng dụng Spring MVC truyền thống:

1. Khi triển khai WAR, Servlet container dùng cơ chế `ServletContainerInitializer` theo đặc tả Jakarta Servlet để tìm các initializer.
2. Spring cung cấp `SpringServletContainerInitializer`, được tìm thấy nhờ khai báo service-provider trong JAR thích hợp.
3. Initializer của Spring tìm các implementation hợp lệ của `WebApplicationInitializer` trên classpath ứng dụng và gọi `onStartup(ServletContext)`.
4. Mã khởi tạo tạo hoặc chỉ định Spring `WebApplicationContext`, đăng ký `DispatcherServlet`, khai báo URL mapping và thiết lập tải sớm.
5. Container khởi tạo `DispatcherServlet`; Servlet kết nối với Spring MVC infrastructure; HTTP request bắt đầu được điều phối.

Khi dùng `AbstractAnnotationConfigDispatcherServletInitializer`, class cha thực hiện phần đăng ký lặp lại ở bước 4. Vì thế ứng dụng **không cần tự viết `web.xml`** chỉ để khai báo dispatcher.

Lưu ý: cơ chế discovery còn phụ thuộc cấu hình Servlet container, WAR được đóng gói đúng và các initializer không bị vô hiệu hóa/bỏ sót trong quá trình quét. Một class có tên `WebAppInitializer` không đủ; class phải thừa kế/triển khai đúng SPI.

## 3. Cách ưu tiên: `AbstractAnnotationConfigDispatcherServletInitializer`

### 3.1. Ba phương thức quyết định cấu hình

Class `org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer` cung cấp cách khai báo ngắn gọn:

```java
package example.web;

import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

public class WebAppInitializer
        extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return null;
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[] { WebConfig.class };
    }

    @Override
    protected String[] getServletMappings() {
        return new String[] { "/" };
    }
}
```

Ý nghĩa của từng phương thức:

| Phương thức | Giá trị trong ví dụ | Ý nghĩa |
| --- | --- | --- |
| `getRootConfigClasses()` | `null` | Không tạo root Spring context riêng. Bài thực hành nhỏ chỉ cần context của dispatcher. |
| `getServletConfigClasses()` | `WebConfig.class` | Khai báo Java `@Configuration` dùng cho `WebApplicationContext` thuộc `DispatcherServlet`. |
| `getServletMappings()` | `"/"` | Đăng ký dispatcher làm **default Servlet mapping** của ứng dụng web, cho phép ánh xạ các đường dẫn như `/hello`. |

**Điểm dễ nhầm:** `getServletMappings()` không phải là annotation `@GetMapping`. Mapping `"/"` đưa một nhóm request **vào Servlet**; Spring MVC sau đó tiếp tục ghép đường dẫn endpoint dựa trên `@RequestMapping`, `@GetMapping` và cấu hình MVC.

`"/"` là **default mapping** trong Servlet specification; `"/*"` là path-prefix mapping rộng hơn và có thể bắt cả những request vốn cần đi tới JSP hoặc Servlet khác. Đừng đổi hai giá trị này một cách tùy tiện.

### 3.2. Cấu hình Spring MVC với `@EnableWebMvc`

Tạo class `WebConfig` cùng package:

```java
package example.web;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = "example.web")
public class WebConfig {
}
```

- `@Configuration`: khai báo một class cấu hình bean Spring.
- `@EnableWebMvc`: bật cơ chế cấu hình MVC dựa trên Java, đăng ký các thành phần xử lý request phù hợp cho controller mapping, handler adapter và các chức năng MVC nền tảng.
- `@ComponentScan`: tìm controller/component trong package được chọn. Trong dự án lớn, giới hạn package quét đủ hẹp để không vô ý nạp quá nhiều bean.

Ở Spring Framework thuần, `@EnableWebMvc` là cách điển hình để cung cấp cấu hình MVC theo annotation. Trong **Spring Boot MVC**, nếu muốn giữ auto-configuration của Boot, thường **không** thêm `@EnableWebMvc` trừ khi chủ đích tự kiểm soát hoàn toàn cấu hình MVC.

### 3.3. Controller minh họa

```java
package example.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping(path = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    public String hello() {
        return "DispatcherServlet đã nhận yêu cầu";
    }
}
```

`@RestController` bao gồm ngữ nghĩa trả response body; `@GetMapping("/hello")` khai báo **mapping của handler bên trong Spring MVC**. Nội dung là text thuần, nên ví dụ không phụ thuộc một JSON serializer chỉ để trả chuỗi.

Khi WAR được triển khai dưới context path `/mvc-demo`, đường dẫn gọi thực tế là:

```text
http://localhost:8080/mvc-demo/hello
                     |       |
             context path    handler mapping
```

Đây là đường dẫn **bên ngoài ứng dụng**. Ở bên trong, mapping Servlet `"/"` hoạt động trên đường dẫn sau context path và Spring MVC ánh xạ `/hello`.

## 4. Ví dụ WAR hoàn chỉnh có thể triển khai

Các đoạn dưới đây cùng nhau tạo thành dự án Maven dùng **Spring Framework 6.2.0**, Jakarta Servlet 6.0 và Java 17. Các phiên bản này phục vụ một bài thực hành nhất quán, không có nghĩa bạn phải dùng phiên bản đó cho hệ thống đang vận hành.

### 4.1. Cấu trúc file

```text
mvc-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── example/
                └── web/
                    ├── WebAppInitializer.java
                    ├── WebConfig.java
                    └── HelloController.java
```

**Không tạo `src/main/webapp/WEB-INF/web.xml`** cho nhánh triển khai Java initializer này. Ba file Java dùng nguyên nội dung ở mục 3.

### 4.2. `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>mvc-demo</artifactId>
    <version>1.0.0</version>
    <packaging>war</packaging>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.version>6.2.0</spring.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-webmvc</artifactId>
            <version>${spring.version}</version>
        </dependency>
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <version>6.0.0</version>
            <scope>provided</scope>
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
                <artifactId>maven-war-plugin</artifactId>
                <version>3.4.0</version>
                <configuration>
                    <failOnMissingWebXml>false</failOnMissingWebXml>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Các ý cần nhận biết:

- `spring-webmvc` cung cấp các thành phần Spring MVC, gồm `DispatcherServlet`; dependency transitively cung cấp phần nền tương ứng.
- `jakarta.servlet-api` là **provided** vì Tomcat cung cấp Servlet API khi chạy. Không đóng gói một Servlet API cạnh tranh trong WAR.
- `packaging=war` tạo archive mà Servlet container triển khai.
- `failOnMissingWebXml=false` thể hiện dụng ý không cần descriptor XML trong nhánh Java initializer.
- Mã nguồn dùng `jakarta.servlet.*`, không dùng `javax.servlet.*` như các dự án Servlet đời cũ.

Lưu ý Maven dùng cú pháp property `${spring.version}` trong XML thực tế. Nếu copy từ trình hiển thị làm xuất hiện ký tự `\` trước dấu `$`, hãy bỏ dấu gạch chéo đó; giá trị XML đúng là tên property Maven với cặp dấu `$` và ngoặc nhọn. 

### 4.3. Build, deploy và kiểm chứng

Điều kiện: JDK 17+, Maven và Tomcat 10.1 được cài đặt; Tomcat 10.1 sử dụng Jakarta Servlet 6.0. Tại thư mục dự án:

```bash
mvn clean package
```

Khi build thành công, Maven tạo `target/mvc-demo-1.0.0.war`. Sao chép WAR tới thư mục `webapps` của Tomcat với tên `mvc-demo.war` và chạy Tomcat. Ví dụ môi trường POSIX:

```bash
cp target/mvc-demo-1.0.0.war "$CATALINA_BASE/webapps/mvc-demo.war"
```

Nếu Tomcat dùng `CATALINA_HOME` làm thư mục triển khai và không đặt `CATALINA_BASE` riêng, thay bằng đường dẫn `webapps` thực tế. Trên Windows có thể copy file qua File Explorer. Tomcat cần cấu hình triển khai WAR và phải đang chạy; chỉ chạy `mvn package` không khởi động HTTP server.

Dùng:

```bash
curl --fail-with-body http://localhost:8080/mvc-demo/hello
```

Body trả về kỳ vọng:

```text
DispatcherServlet đã nhận yêu cầu
```

Cổng 8080, context path và host ở đây là **giả định của demo**; nếu tên WAR hoặc connector cấu hình khác, URL sẽ khác. `curl` có thể in response body không kèm một dòng newline, điều đó không đổi nội dung payload.

Đường đi khi chạy đúng:

```text
Tomcat tìm WebApplicationInitializer trong WAR
    -> WebAppInitializer.onStartup(...)
    -> tạo context từ WebConfig
    -> đăng ký DispatcherServlet với mapping "/"
    -> @ComponentScan tìm HelloController
    -> GET /mvc-demo/hello
    -> DispatcherServlet chọn HelloController.hello()
    -> HTTP 200, body text/plain
```

Nếu muốn tự xác minh class nào tham gia, kiểm tra danh sách Servlet registrations lúc startup và log khởi tạo `DispatcherServlet` trên Tomcat. Đừng dùng việc package thành công làm bằng chứng rằng HTTP endpoint đã chạy.

## 5. Cách viết trực tiếp `WebApplicationInitializer`

Thừa kế `AbstractAnnotationConfigDispatcherServletInitializer` tiện lợi nhưng đôi khi cần thấy chính xác Spring giao tiếp với Servlet container thế nào. Lựa chọn tương đương là tự triển khai `org.springframework.web.WebApplicationInitializer`:

```java
package example.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

public class ManualInitializer implements WebApplicationInitializer {

    @Override
    public void onStartup(ServletContext servletContext)
            throws ServletException {

        AnnotationConfigWebApplicationContext context =
                new AnnotationConfigWebApplicationContext();
        context.register(WebConfig.class);

        DispatcherServlet dispatcher = new DispatcherServlet(context);

        ServletRegistration.Dynamic registration =
                servletContext.addServlet("mvc", dispatcher);
        if (registration == null) {
            throw new ServletException("Tên Servlet 'mvc' đã được sử dụng");
        }

        registration.setLoadOnStartup(1);
        registration.addMapping("/");
    }
}
```

Ý nghĩa từng thao tác:

1. Tạo `AnnotationConfigWebApplicationContext`: Spring context lấy cấu hình từ annotation/Java class.
2. `context.register(WebConfig.class)`: đăng ký nguồn cấu hình MVC. Context sẽ được refresh trong vòng đời do dispatcher quản lý khi khởi tạo.
3. `new DispatcherServlet(context)`: liên kết dispatcher với Spring context.
4. `servletContext.addServlet("mvc", dispatcher)`: yêu cầu **Servlet container** đăng ký Servlet có tên `mvc`; trả về đối tượng cấu hình động.
5. `setLoadOnStartup(1)`: đặt thứ tự ưu tiên tải/khởi tạo khi ứng dụng khởi động, thay vì mặc định đợi request đầu tiên.
6. `addMapping("/")`: đăng ký URL pattern ở lớp Servlet.

**Đây là một phương án thay thế `WebAppInitializer` của mục 3.** Để chạy cách này, **xóa/loại khỏi WAR class `WebAppInitializer` cũ**, đổi `ManualInitializer.java` thành file có nội dung trên, giữ `WebConfig` và `HelloController`. Không deploy đồng thời cả hai initializer cùng đăng ký mapping `"/"`; tên khác nhau không khiến hai Servlet trở thành hai front controller hợp lệ cho cùng một URL pattern.

Java Servlet registration có thể cấu hình thêm init-params, async support và multipart config khi bài toán cần. Ví dụ này tập trung vào luồng khởi tạo tối thiểu.

## 6. Nhận biết `WEB-INF/web.xml` truyền thống

### 6.1. Vị trí và vai trò

`web.xml` là **deployment descriptor** của ứng dụng Servlet. Với WAR, nó nằm tại:

```text
src/main/webapp/WEB-INF/web.xml
          |
          v (sau khi đóng gói)
WEB-INF/web.xml trong WAR
```

Descriptor này khai báo Servlet, Servlet mapping, Filter, Listener, init-params, security constraints và một số cấu hình Servlet container khác. Nó được **Servlet container** đọc, không phải một file Spring bean XML thuần.

### 6.2. `web.xml` tương ứng với ví dụ Java trên

Để triển khai **cùng `WebConfig` và `HelloController` nhưng bằng XML**, loại bỏ class `WebAppInitializer`/`ManualInitializer` trong WAR rồi thêm `src/main/webapp/WEB-INF/web.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                             https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">

    <servlet>
        <servlet-name>mvc</servlet-name>
        <servlet-class>
            org.springframework.web.servlet.DispatcherServlet
        </servlet-class>

        <init-param>
            <param-name>contextClass</param-name>
            <param-value>
                org.springframework.web.context.support.AnnotationConfigWebApplicationContext
            </param-value>
        </init-param>

        <init-param>
            <param-name>contextConfigLocation</param-name>
            <param-value>example.web.WebConfig</param-value>
        </init-param>

        <load-on-startup>1</load-on-startup>
    </servlet>

    <servlet-mapping>
        <servlet-name>mvc</servlet-name>
        <url-pattern>/</url-pattern>
    </servlet-mapping>
</web-app>
```

Đây là **XML descriptor cho Servlet 6.0/Jakarta**, không dùng URI schema Java EE/`javax.servlet` cũ. Sau khi deploy như ở mục 4, endpoint vẫn là `GET /mvc-demo/hello`.

Đọc từng thẻ:

| Thành phần XML | Tác dụng | Tương ứng ở Java |
| --- | --- | --- |
| `<servlet>` | Khai báo một Servlet cho container | `servletContext.addServlet(...)` |
| `<servlet-name>mvc</servlet-name>` | Định danh Servlet, liên kết với mapping | Tên `"mvc"` trong `addServlet` |
| `<servlet-class>...DispatcherServlet</servlet-class>` | Chỉ định class Servlet cần tạo | `new DispatcherServlet(context)` |
| `<init-param>contextClass</init-param>` | Dùng `AnnotationConfigWebApplicationContext` thay context XML mặc định | `new AnnotationConfigWebApplicationContext()` |
| `<init-param>contextConfigLocation</init-param>` | Chỉ tới `example.web.WebConfig`, **tên class cấu hình** khi dùng context dạng annotation | `context.register(WebConfig.class)` |
| `<load-on-startup>1</load-on-startup>` | Khởi tạo sớm khi deploy | `registration.setLoadOnStartup(1)` |
| `<servlet-mapping>` và `<url-pattern>/</url-pattern>` | Đăng ký URL pattern | `registration.addMapping("/")` |

**Không nhầm lẫn:** `contextConfigLocation` là một **tên init-param** của Spring. Ý nghĩa giá trị phụ thuộc `contextClass`. Với `XmlWebApplicationContext`, giá trị có thể là đường dẫn bean XML; với `AnnotationConfigWebApplicationContext` trong ví dụ là class `@Configuration`.

### 6.3. Nhận biết XML truyền thống hơn

Trong codebase cũ, bạn có thể gặp cấu hình này:

```xml
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
    <init-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>/WEB-INF/spring/mvc-config.xml</param-value>
    </init-param>
    <load-on-startup>1</load-on-startup>
</servlet>
<servlet-mapping>
    <servlet-name>dispatcher</servlet-name>
    <url-pattern>/</url-pattern>
</servlet-mapping>
```

Đây là **một fragment bên trong `web.xml` hợp lệ**, không phải toàn bộ file độc lập. Nó tham chiếu tới **Spring bean XML** `/WEB-INF/spring/mvc-config.xml`, file khác với descriptor `web.xml`. Khi không khai báo `contextClass` và sử dụng cấu hình Servlet mặc định của `DispatcherServlet`, Spring thường dùng `XmlWebApplicationContext`.

Nhiều dự án còn không ghi `contextConfigLocation`: khi đó `DispatcherServlet` có quy ước tìm một file mặc định theo `servlet-name`, ví dụ `/WEB-INF/dispatcher-servlet.xml`. Không nên đoán rằng mọi dự án đều dùng đường dẫn mặc định; hãy đọc `init-param` và cấu hình context thực tế.

Nếu muốn dùng XML bean configuration, nội dung file `mvc-config.xml` thường đăng ký controller scanning và khả năng annotation MVC qua các phần tử tương ứng như `<context:component-scan>` và `<mvc:annotation-driven>`. Những phần tử này thuộc **Spring bean XML**, không phải thẻ của Servlet descriptor.

### 6.4. Khi nào vẫn thấy `web.xml`?

- Ứng dụng Servlet/Spring MVC cũ chưa chuyển sang Java initializer.
- Nhóm dự án duy trì quy ước deployment descriptor cho nhiều Servlet, Filter, Listener.
- Hệ thống muốn dùng kết hợp descriptor và một số registration lập trình có kiểm soát.

**Không phải:** thiếu `web.xml` luôn là lỗi cấu hình. Với container và WAR phù hợp, Java initializer có thể đăng ký hoàn toàn bằng code. Maven WAR plugin có thể cần cấu hình rõ mục đích không yêu cầu descriptor, như mục 4.

## 7. Hai loại Spring application context

Ở hệ thống MVC lớn có thể tồn tại **root WebApplicationContext** và một hoặc nhiều **child WebApplicationContext** dành cho từng `DispatcherServlet`:

```text
ServletContext (toàn bộ web application)
  |
  +-- Root WebApplicationContext
  |      ├── services
  |      ├── repositories
  |      └── shared infrastructure
  |
  +-- DispatcherServlet "mvc"
            |
            +-- Child WebApplicationContext
                  ├── controllers
                  ├── HandlerMappings
                  ├── HandlerAdapters
                  └── view resolvers / MVC config
```

Trong ví dụ đơn giản, `getRootConfigClasses()` trả `null`, Spring MVC configuration nằm trong context dành cho dispatcher. Nếu muốn tách lớp business và MVC configuration, có thể viết:

```java
@Override
protected Class<?>[] getRootConfigClasses() {
    return new Class<?>[] { RootConfig.class };
}

@Override
protected Class<?>[] getServletConfigClasses() {
    return new Class<?>[] { WebConfig.class };
}
```

Đây chỉ là **fragment minh họa**: ứng dụng muốn chạy phải định nghĩa `RootConfig` và giới hạn component scan phù hợp. Trong context hierarchy, bean trong child thường nhìn thấy bean từ parent; chiều ngược lại **không tự động**. Nếu quét một controller vào root context thay vì servlet context, servlet context con có thể không đăng ký handler mapping như dự kiến.

Dạng `web.xml` tương ứng thường có `ContextLoaderListener` để khởi tạo root context; cấu hình root qua `<context-param>`, còn dispatcher child qua `<servlet><init-param>...`. Đây là **hai phạm vi cấu hình** dù dùng cùng tên `contextConfigLocation`. Không mặc định thêm `ContextLoaderListener` nếu ứng dụng không cần root context riêng.

## 8. Java initializer và `web.xml`: đối chiếu vai trò

| Góc nhìn | Java initializer | `web.xml` |
| --- | --- | --- |
| Nơi khai báo | Java class trong WAR, implements `WebApplicationInitializer` | `WEB-INF/web.xml` |
| Cơ chế container kích hoạt | Phát hiện `ServletContainerInitializer` rồi gọi `onStartup` | Container phân tích deployment descriptor |
| Cấu hình `DispatcherServlet` | `addServlet(...)` hoặc abstract initializer | `<servlet>` + `<servlet-class>` |
| URL mapping | `addMapping("/")` hoặc `getServletMappings()` | `<servlet-mapping><url-pattern>/</url-pattern>` |
| Init-param | `registration.setInitParameter(...)` hoặc logic trong context | `<init-param>` |
| Nạp sớm | `setLoadOnStartup(1)` | `<load-on-startup>1</load-on-startup>` |
| Spring Java configuration | `WebConfig.class` | `AnnotationConfigWebApplicationContext` + class name |
| Root context khi cần | `getRootConfigClasses()` | `ContextLoaderListener` cùng root context config |
| Phù hợp | Dự án Java config trên Servlet stack | Hệ thống legacy hoặc descriptor-centric |

Hai phương pháp đều dẫn tới **một `DispatcherServlet` được Servlet container quản lý**. Java initializer cũng không có nghĩa bỏ qua Servlet specification: nó sử dụng chính API động của specification đó.

## 9. Spring Boot khởi tạo khác như thế nào?

Với Spring Boot Web MVC có embedded Servlet container, điểm vào thường là `@SpringBootApplication` và `SpringApplication.run(...)`. Boot tự thiết lập nhiều phần của servlet web server, context, dispatcher registration và cấu hình MVC dựa trên các điều kiện classpath/bean/property.

Ví dụ **chỉ để nhận diện phong cách Boot**, độc lập với WAR Framework thuần ở mục 4:

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

- Với `spring-boot-starter-web`, ứng dụng điển hình không cần tự viết `WebAppInitializer` hoặc `web.xml`.
- Trong Boot, có thể dùng `ServletRegistrationBean` hoặc `FilterRegistrationBean` khi thật sự phải tự khai báo Servlet/Filter riêng; cách này thuộc **Spring Boot API**, không giống triển khai `WebApplicationInitializer` cho WAR Spring Framework thuần.
- Khi đóng gói WAR để đưa vào Servlet container bên ngoài, Boot có workflow thích hợp (chẳng hạn `SpringBootServletInitializer`) và phụ thuộc container theo scope phù hợp. Đừng lấy bộ ba class Framework thuần trong bài rồi giả định đó là cấu hình mặc định cho Boot executable JAR.
- Spring WebFlux sử dụng cơ chế HTTP dispatch reactive; đừng áp dụng `DispatcherServlet` cho server WebFlux thuần.

## 10. Lỗi thường gặp và cách xác minh

### 10.1. Lỗi 404 dù đã viết `@GetMapping`

Kiểm tra theo thứ tự:

1. Đúng host/port và **context path** của WAR chưa? `/mvc-demo/hello` khác `/hello` khi deploy dưới tên `mvc-demo.war`.
2. WAR đã triển khai thành công và container thực sự phát hiện `WebAppInitializer` chưa?
3. Có mapping `"/"` cho đúng `DispatcherServlet` không?
4. `WebConfig` có `@EnableWebMvc` và `@ComponentScan` bao gồm package controller không?
5. `@GetMapping` có đúng đường dẫn và HTTP method không?
6. Có Servlet khác chiếm mapping hoặc filter/security chặn request không?

Nếu bạn gọi `GET /mvc-demo/hello` mà nhận 404, phải xác định 404 từ **Tomcat trước khi vào Spring** hay từ **Spring MVC sau khi không tìm ra handler**; startup log, mapping registration và request log giúp phân biệt.

### 10.2. Ứng dụng không phát hiện initializer

Các nguyên nhân có thể xảy ra:

- Class initializer không nằm trong WAR `WEB-INF/classes` hoặc không được quét.
- Class không implement `WebApplicationInitializer` / không extends initializer trừu tượng.
- Dependency Spring MVC không được đóng gói phù hợp hoặc cơ chế container initializer bị hạn chế bởi cấu hình deployment.
- Dùng sai Servlet container / namespace `javax` versus `jakarta`.
- Có lỗi khởi tạo bean/Servlet khác khiến startup thất bại; xem **lỗi gốc đầu tiên** trong server log.

Kiểm tra nội dung WAR bằng công cụ JDK:

```bash
jar tf target/mvc-demo-1.0.0.war
```

Bạn cần thấy ít nhất class `WEB-INF/classes/example/web/WebAppInitializer.class` và các JAR Spring liên quan trong `WEB-INF/lib`. Kiểm tra theo đường dẫn class thực tế, không dựa vào tên file Java trên máy phát triển.

### 10.3. Lỗi trùng `DispatcherServlet` hoặc xung đột mapping

Thường do giữ cả `web.xml` và Java initializer đăng ký cùng URL, hoặc để nhiều initializer cùng tồn tại. Việc Servlet container cho phép cấu hình Java kết hợp XML **không có nghĩa cứ đăng ký trùng là hợp lệ**. Hãy xác định duy nhất cơ chế sở hữu mỗi Servlet name/mapping và loại bỏ khai báo chồng chéo.

### 10.4. Sai `contextConfigLocation`

- Trong `web.xml` mặc định XML context: giá trị kiểu `/WEB-INF/mvc-config.xml` là đường dẫn tới **Spring bean configuration XML**.
- Trong `web.xml` chọn `AnnotationConfigWebApplicationContext`: giá trị `example.web.WebConfig` là **tên class cấu hình**.
- Trong Java initializer: `getServletConfigClasses()` thường trả **Class<?>[]**, không phải tên file XML.
- `getRootConfigClasses()` và `getServletConfigClasses()` trộn sai package scan có thể dẫn tới không tìm thấy controller hoặc bean lặp.

### 10.5. Trộn các thế hệ `javax` và `jakarta`

Spring Framework 6 và Tomcat 10.1 làm việc với Jakarta Servlet; các ứng dụng Spring MVC 5/Tomcat 9 thường thuộc họ `javax.servlet.*`. Nếu đổi namespace một nửa, lỗi có thể là `ClassNotFoundException`, `NoClassDefFoundError`, `NoSuchMethodError` hoặc Servlet không khởi tạo. Đồng bộ framework, dependency, container và import source code; **không chữa bằng cách thêm song song hai Servlet API cạnh tranh**.

### 10.6. Dùng `@EnableWebMvc` sai bối cảnh

Trong Framework thuần, `@EnableWebMvc` là cách phổ biến để thiết lập Java MVC configuration. Trong Spring Boot, nó có thể vô hiệu hóa một số cơ chế MVC auto-configuration mà Boot tự cung cấp. Xem dự án là **WAR Framework thuần hay Boot** trước khi copy code.

## 11. Thực hành tự kiểm tra

**Bài 1 — xác định lớp mapping:** Cho `getServletMappings() = new String[]{"/api/*"}` và controller có `@GetMapping("/hello")`, WAR chạy tại context path `/mvc-demo`. Request thường có dạng `GET /mvc-demo/api/hello`. Vì `/api/*` là mapping Servlet, tiền tố `/api` là phần đường dẫn Servlet; còn `/hello` là mapping controller tương ứng. Hãy giải thích vì sao gọi `/mvc-demo/hello` không chạm tới dispatcher đó.

**Bài 2 — chuyển đổi cấu hình:** Từ Java `getServletMappings() = new String[]{"/"}`, `getServletConfigClasses() = {WebConfig.class}`, `getRootConfigClasses() = null`, viết descriptor `web.xml` khai báo `DispatcherServlet`, `contextClass` và `contextConfigLocation` sao cho không cần root context. So lại với bảng ở mục 6.2.

**Bài 3 — chẩn đoán lỗi:** WAR có controller và `WebConfig`, nhưng servlet container không log việc đăng ký dispatcher. Nêu ba điều cần kiểm tra trước khi sửa `@GetMapping`: class initializer trong WAR, cơ chế discovery, và compatibility Servlet/Spring.

**Bài 4 — thiết kế context:** Nếu có hai `DispatcherServlet` riêng, mỗi servlet có controller khác nhau nhưng dùng chung service, hãy mô tả việc đặt service vào root context và MVC infrastructure vào servlet context con. Phân biệt khi nào cần `ContextLoaderListener`.

### Câu hỏi ghi nhớ

- **`DispatcherServlet` do ai đăng ký?** Servlet container nhận declaration từ `web.xml` hoặc Java registration do `WebApplicationInitializer` thực hiện.
- **`WebApplicationInitializer` là gì?** SPI Spring được tự động phát hiện qua cơ chế initializer của Servlet container để cấu hình `ServletContext` lập trình.
- **`@EnableWebMvc` có thay cho `addServlet` không?** Không. Nó cấu hình các bean MVC; vẫn cần Servlet container biết `DispatcherServlet`.
- **Có bắt buộc `web.xml` không?** Không, nếu Java registration và các điều kiện startup của Servlet container được đáp ứng.
- **`contextConfigLocation` có luôn chỉ tới XML không?** Không; với `AnnotationConfigWebApplicationContext` nó có thể chỉ tới Java `@Configuration` class.
- **Một ứng dụng Boot với `spring-boot-starter-web` có thường cần Java initializer trong bài không?** Không. Boot cung cấp quy trình bootstrap/auto-configuration riêng.

## 12. Tham khảo chính thức

- DispatcherServlet và cấu hình Java/XML: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet.html
- Servlet Config, `WebApplicationInitializer`: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/container-config.html
- Context hierarchy: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/context-hierarchy.html
- Cấu hình Spring MVC bằng Java: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-servlet/config.html
- `WebApplicationInitializer` API: https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/web/WebApplicationInitializer.html

**Cần nhớ:** `web.xml` và `WebApplicationInitializer` đều giúp Servlet container biết `DispatcherServlet` tồn tại và xử lý URL nào. Spring `WebConfig` quyết định **dispatcher sẽ tìm các MVC bean/controller bằng cách nào**. Hai tầng trách nhiệm này cần được hiểu tách biệt khi khởi tạo và gỡ lỗi Spring MVC.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-bootstrap-003">WebApplicationContext gốc, ngữ cảnh servlet và quan hệ cha–con</a>

<details>
<summary>Xem chi tiết</summary>

# WebApplicationContext gốc, ngữ cảnh servlet và quan hệ cha–con

## 1. Vấn đề cần hiểu khi khởi tạo ứng dụng Spring MVC

Khi phát triển ứng dụng Spring MVC, bạn sẽ gặp nhiều đối tượng có tên gần giống nhau: `ApplicationContext`, `WebApplicationContext`, `ServletContext`, `DispatcherServlet` và `ContextLoaderListener`. Chúng có chức năng khác nhau. Nếu không phân biệt, rất dễ gặp lỗi controller không được ánh xạ, service không được inject, cùng một bean bị khởi tạo hai lần hoặc cấu hình MVC bị đặt sai chỗ.

Một ứng dụng web có thể tổ chức **một context Spring duy nhất** hoặc **một hệ phân cấp context cha–con**. Trong kiểu phân cấp cổ điển:

- **Root `WebApplicationContext`** (ngữ cảnh gốc) là nơi chứa các bean dùng chung cho ứng dụng, ví dụ service, repository, data source, transaction manager hoặc cấu hình hạ tầng.
- **Servlet-specific `WebApplicationContext`** (ngữ cảnh dành riêng cho một `DispatcherServlet`) thường chứa controller, handler mapping, view resolver, message converter và cấu hình web của servlet đó.
- Root là **parent**; servlet context là **child**. Child có thể tra cứu bean của parent, còn parent không mặc nhiên thấy bean chỉ được khai báo trong child.
- Một ứng dụng có thể có nhiều `DispatcherServlet`, mỗi servlet có child context riêng, cùng tham chiếu một root context.

**Điểm quan trọng:** Root–child là **một lựa chọn tổ chức** của Spring MVC trên Servlet stack; đây không phải điều kiện bắt buộc của mọi ứng dụng Spring Web, đặc biệt trong các ứng dụng Spring Boot hiện đại.

## 2. Phân biệt năm khái niệm thường bị nhầm

| Thành phần | Thuộc hệ thống nào? | Trách nhiệm | Có giữ bean Spring? |
|---|---|---|---|
| `ApplicationContext` | Spring Core | IoC container: tạo, quản lý, liên kết các bean; quản lý cấu hình, sự kiện, lifecycle | Có |
| `WebApplicationContext` | Spring Web | Mở rộng `ApplicationContext` cho môi trường web Servlet; truy cập `ServletContext` và các đối tượng liên quan | Có |
| `ServletContext` | Jakarta Servlet API | Đại diện môi trường của web application trong Servlet container; lưu attributes và thông tin ứng dụng web | Không phải Spring IoC container |
| `DispatcherServlet` | Spring MVC | Front controller nhận HTTP request, tìm handler, phối hợp các chiến lược MVC và tạo response | Không phải IoC container; sử dụng một `WebApplicationContext` |
| `ContextLoaderListener` | Spring Web trên Servlet | Khởi tạo và hủy root `WebApplicationContext` theo vòng đời web application | Không phải container, mà là thành phần bootstrap |

`WebApplicationContext` là interface mở rộng `ApplicationContext`, không phải một bean đặc biệt duy nhất có tên "webApplicationContext". Có nhiều cách triển khai, ví dụ `AnnotationConfigWebApplicationContext` và `XmlWebApplicationContext`. Cùng một ứng dụng có thể sở hữu nhiều context Spring, nhưng **mỗi context có bean registry và phạm vi quản lý riêng**.

`ServletContext` thường được Servlet container (ví dụ Tomcat) cấp cho một web application. Nó không phải servlet-specific Spring child context. Cụm “servlet context” trong chủ đề bài học cần hiểu rõ theo ngữ cảnh: **`ServletContext`** (API Jakarta) khác với **`WebApplicationContext` của `DispatcherServlet`** (Spring). Tên gần giống nhau nhưng đối tượng và trách nhiệm khác nhau.

## 3. Sơ đồ root context và servlet child context

```text
                    Servlet container (ví dụ Tomcat)
                                  |
                      ServletContext (Jakarta)
                                  |
                    +-------------+-------------+
                    |                           |
          ContextLoaderListener           Servlet registrations
                    |                           |
                    v                    +------+------+
       Root WebApplicationContext         |             |
       (các bean dùng chung)              v             v
            - OrderService           DispatcherServlet A  DispatcherServlet B
            - OrderRepository             |             |
            - DataSource                  v             v
            - TransactionManager       Child A        Child B
                    ^                  (web MVC)       (web MVC)
                    |                     |             |
                    +------ parent -------+             |
                    +-------- parent -------------------+
                                          |             |
                                    controllers A  controllers B
                                    mappings A     mappings B
                                    views A        views B
```

Mũi tên `parent` mô tả quan hệ Spring `ApplicationContext.getParent()`, **không phải** quan hệ kế thừa class Java. Mỗi child vẫn có bean definitions, scope và lifecycle của riêng nó; chỉ việc tra cứu và sử dụng bean có thể đi lên parent.

Nếu chỉ có một `DispatcherServlet`, mô hình vẫn tương tự:

```text
Root WebApplicationContext
    ├── UserService
    └── UserRepository
          ↑ parent của
Servlet WebApplicationContext
    ├── UserController
    ├── HandlerMapping
    ├── HandlerAdapter
    └── ViewResolver
          ↑ được dùng bởi
     DispatcherServlet
```

**Mục đích phân tách:** Các servlet có thể dùng chung tầng nghiệp vụ nhưng độc lập về endpoint, view hoặc cấu hình web. Ví dụ một servlet phục vụ vùng người dùng, một servlet phục vụ vùng quản trị. Không nên tạo nhiều servlet hoặc nhiều context chỉ để “kiến trúc trông rõ tầng” khi một context đã đủ.

## 4. Root WebApplicationContext là gì?

Root context là `WebApplicationContext` cấp ứng dụng web, thường được khởi tạo sớm bởi `ContextLoaderListener` trong kiểu triển khai WAR trên Servlet container. Nó có thể chứa:

- Service nghiệp vụ: `CustomerService`, `OrderService`, v.v.
- Repository và các dependency của tầng truy cập dữ liệu.
- DataSource, transaction manager, client gọi hệ thống ngoài.
- Các bean hạ tầng cần được chia sẻ giữa các servlet context.

Ví dụ cấu hình root bằng Java:

```java
package com.example.hierarchy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RootConfig {
    @Bean
    public GreetingService greetingService() {
        return new GreetingService();
    }
}
```

`GreetingService` là một class Java thông thường; trong ví dụ này `@Bean` đăng ký nó vào **root context**. Việc root có tên “root” không khiến mọi class tự động trở thành bean trong root: bean xuất hiện ở context nào phụ thuộc vào cấu hình được nạp ở context đó.

Khi root tồn tại, Spring đặt tham chiếu root `WebApplicationContext` trong attribute của Jakarta `ServletContext`. Để tra cứu root từ thành phần servlet cấp thấp, Spring cung cấp `WebApplicationContextUtils.getWebApplicationContext(servletContext)` hoặc `getRequiredWebApplicationContext(servletContext)`; thao tác này lấy **root**, không phải tự chọn child của mọi `DispatcherServlet`.

### Root context không bắt buộc

Một ứng dụng nhỏ có thể chỉ có servlet-specific `WebApplicationContext`. Khi không cần chia sẻ hạ tầng giữa nhiều servlet, việc tách root và child có thể làm cấu hình phức tạp không cần thiết. `AbstractAnnotationConfigDispatcherServletInitializer` cho phép trả `null` ở `getRootConfigClasses()` để không tạo root riêng.

Ngược lại, khi đặt tất cả cấu hình ở root và trả `null` tại `getServletConfigClasses()`, Spring MVC cũng hỗ trợ cách tổ chức không cần phân tách web và shared config thành hai nhóm; tuy nhiên cần hiểu cách initializer và `DispatcherServlet` sử dụng context tương ứng. Khi học quan hệ cha–con, nên dùng hai cấu hình tách biệt để dễ quan sát.

## 5. Servlet-specific WebApplicationContext là gì?

`DispatcherServlet` sử dụng một `WebApplicationContext` dành cho chính nó. Đây là nơi điển hình để cấu hình:

- Controller (`@Controller`, `@RestController`).
- `HandlerMapping` và `HandlerAdapter` để phát hiện, gọi phương thức xử lý HTTP.
- View resolver, template/view configuration.
- HTTP message converters, formatters, validators.
- Các cấu hình MVC như `@EnableWebMvc`, `WebMvcConfigurer`.

Ví dụ cấu hình child:

```java
package com.example.hierarchy;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
public class WebConfig {
    @Bean
    public GreetingController greetingController(
            GreetingService greetingService,
            ApplicationContext applicationContext) {
        return new GreetingController(greetingService, applicationContext);
    }
}
```

Ở đây:

1. `WebConfig` được đăng ký vào **child**.
2. `GreetingController` trở thành bean của child.
3. `GreetingService` chỉ được đăng ký ở root nhưng được truyền vào bean controller trong child nhờ tra cứu qua parent.
4. `ApplicationContext` được inject khi tạo bean ở child là context đang tạo bean đó; có thể dùng để quan sát `getParent()`.

Trong cấu hình cổ điển, mỗi `DispatcherServlet` có thể nhận một child context khác nhau, điều này cho phép hai servlet có bộ controller/view riêng. Việc một servlet quản lý route nào còn tùy **Servlet mapping** (ví dụ `/api/*` hay `/admin/*`) và các request mappings bên trong Spring MVC.

### Đừng mặc định controller trong root sẽ được DispatcherServlet phát hiện

Handler mapping mặc định của Spring MVC thường dò controller trong **context của chính DispatcherServlet**. Các bean từ parent có thể được inject vào child, nhưng việc tìm **handler methods** trong ancestor context không được bật mặc định ở các handler mapping phổ biến. Do đó, để rõ ràng và tránh hành vi phụ thuộc cấu hình, hãy đặt controller và cấu hình MVC ở child; đặt service/repository dùng chung ở root.

Nếu vô tình cấu hình component scan bao trùm toàn bộ package ở cả root và child, cùng một component có thể được tạo thành **hai bean khác nhau**, dẫn đến service state riêng, khởi tạo tài nguyên trùng hoặc mapping khó đoán.

## 6. Quan hệ cha–con: cách Spring tra cứu bean

Đặc điểm cốt lõi của phân cấp `ApplicationContext` là **tra cứu đi từ child lên parent** khi child không có bean phù hợp.

Xét cây bean:

```text
ROOT
  bean: auditService
  bean: greetingService

CHILD /api
  bean: apiController
  bean: greetingFormatter

CHILD /admin
  bean: adminController
  bean: adminViewResolver
```

Các kết luận quan trọng:

| Thao tác | Kết quả thường thấy |
|---|---|
| `/api` child lấy `greetingService` | Có thể tìm được bean ở root |
| `/admin` child lấy `auditService` | Có thể tìm được bean ở root |
| Root tìm `apiController` | Không tự nhìn xuống child để tìm |
| `/api` child tìm `adminController` | Không tự nhìn sang sibling child |
| Child có bean cùng tên với bean ở root | Bean local cùng tên có thể che bean ở parent trong đường tra cứu của child |

Nếu child chứa bean tên `greetingService` và root cũng chứa `greetingService`, tìm theo tên từ child thường lấy bean local. Tuy nhiên, **tra cứu theo kiểu (`getBean(SomeType.class)` hoặc autowiring) có thêm quy tắc candidate, primary, qualifier và độ ưu tiên**; đừng suy diễn mọi trường hợp trùng tên và trùng kiểu đều giải quyết như nhau. Đặt bean name rõ ràng, giới hạn component scan và dùng `@Qualifier` khi thật sự cần.

Bean singleton cũng phải hiểu theo phạm vi **một container**: `singleton` nghĩa là một instance cho mỗi bean definition trong một `ApplicationContext`, không tự bảo đảm chỉ có một object duy nhất trong toàn JVM. Hai context cùng scan và đăng ký `@Service` có thể có hai singleton khác nhau.

### Parent có thể bị thay đổi động không?

Về mặt API, context có cơ chế thiết lập parent trong một số giai đoạn khởi tạo. Trong thực tế, nên xác định cấu trúc phân cấp **trước khi refresh context** và giữ ổn định. Thay parent của context đang vận hành hoặc tùy ý đóng root trong khi child còn dùng bean của root có thể gây hành vi khó kiểm soát.

## 7. Ai khởi tạo root và child, theo thứ tự nào?

Trong ứng dụng WAR kiểu Spring MVC không dùng bootstrap của Spring Boot, Servlet container bắt đầu vòng đời web application và cơ chế startup thường diễn ra như sau:

1. Servlet container khởi tạo `ServletContext`.
2. Spring Servlet initializer hoặc cấu hình `web.xml` đăng ký `ContextLoaderListener` và một hay nhiều `DispatcherServlet`.
3. Khi `ContextLoaderListener` được gọi theo vòng đời khởi động của web application, nó tạo và `refresh` root `WebApplicationContext`, đăng ký các bean chung và công bố root qua `ServletContext`.
4. `DispatcherServlet` được khởi tạo; nó sử dụng hoặc tạo `WebApplicationContext` của mình, thiết lập quan hệ parent khi có root và khởi tạo các chiến lược MVC cần thiết.
5. Servlet container ánh xạ URL theo Servlet mapping; request đến `DispatcherServlet` được xử lý bằng bean MVC ở context của servlet, có thể inject bean từ root.
6. Khi web application dừng, các thành phần khởi tạo context tương ứng dọn lifecycle và đóng các context mà chúng quản lý.

Thứ tự và chi tiết startup còn phụ thuộc cách đăng ký servlet, `load-on-startup` và chiến lược bootstrap. Đây là một luồng điển hình, không phải hướng dẫn tự gọi `refresh()` trong controller hoặc tạo `ApplicationContext` mỗi request.

### ContextLoaderListener và DispatcherServlet có phải cùng một đối tượng?

Không. `ContextLoaderListener` là **listener** cấp web application để bootstrap root; `DispatcherServlet` là **servlet** xử lý HTTP và có context riêng. Cả hai có thể tham gia vào quá trình startup nhưng có trách nhiệm độc lập. Nếu bạn chỉ dùng một context, có thể không cần `ContextLoaderListener` riêng.

## 8. Ví dụ chạy được: một root context và một servlet child

Ví dụ dưới đây minh họa chính xác đường truyền dependency và cơ chế tìm parent. Nó sử dụng **Spring Framework 6.2.x**, **Java 17+**, **Jakarta Servlet 6** và triển khai dưới dạng **WAR trên Tomcat 10.1** hoặc container tương thích. Đây là kiểu bootstrap Servlet truyền thống, không sử dụng Spring Boot.

Cấu trúc dự án Maven:

```text
spring-context-hierarchy-demo/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/example/hierarchy/
                ├── AppInitializer.java
                ├── RootConfig.java
                ├── WebConfig.java
                ├── GreetingService.java
                └── GreetingController.java
```

### 8.1. Khai báo dependency và đóng gói WAR

Tệp `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-context-hierarchy-demo</artifactId>
    <version>1.0.0</version>
    <packaging>war</packaging>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.version>6.2.18</spring.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-webmvc</artifactId>
            <version>${spring.version}</version>
        </dependency>
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <version>6.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>context-demo</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-war-plugin</artifactId>
                <version>3.4.0</version>
                <configuration>
                    <failOnMissingWebXml>false</failOnMissingWebXml>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

`spring-webmvc` kéo theo các thành phần Spring MVC cần thiết. `jakarta.servlet-api` có scope `provided` vì Tomcat cung cấp Servlet API khi chạy. Không cần `web.xml` vì Spring có cơ chế initializer theo Servlet specification.

### 8.2. Tạo service dùng chung và root config

Tệp `GreetingService.java`:

```java
package com.example.hierarchy;

public class GreetingService {
    public String greet(String name) {
        return "Hello " + name;
    }
}
```

Tệp `RootConfig.java`:

```java
package com.example.hierarchy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RootConfig {
    @Bean
    public GreetingService greetingService() {
        return new GreetingService();
    }
}
```

`GreetingService` được đăng ký **duy nhất trong root**. Không cần `@Service` vì được khai báo bằng `@Bean`. Cách này tránh scan trùng và giúp việc phân biệt bean owner rõ ràng.

### 8.3. Tạo controller thuộc child

Tệp `GreetingController.java`:

```java
package com.example.hierarchy;

import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {
    private final GreetingService service;
    private final ApplicationContext context;

    public GreetingController(
            GreetingService service,
            ApplicationContext context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping(value = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
    public String hello(@RequestParam(defaultValue = "Lan") String name) {
        return service.greet(name);
    }

    @GetMapping(value = "/context", produces = MediaType.TEXT_PLAIN_VALUE)
    public String contextInfo() {
        ApplicationContext parent = context.getParent();
        return "hasParent=" + (parent != null)
                + ", parentHasService="
                + (parent != null && parent.containsLocalBean("greetingService"))
                + ", childHasController="
                + context.containsLocalBean("greetingController");
    }
}
```

Tệp `WebConfig.java`:

```java
package com.example.hierarchy;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
public class WebConfig {
    @Bean
    public GreetingController greetingController(
            GreetingService service,
            ApplicationContext context) {
        return new GreetingController(service, context);
    }
}
```

Giải thích:

- `@EnableWebMvc` đăng ký hạ tầng MVC trong **servlet child context**.
- `GreetingController` là bean được định nghĩa tại child qua `@Bean`, đồng thời có annotation `@RestController`/`@GetMapping` để Spring MVC nhận diện HTTP handler.
- `GreetingService` không tồn tại cục bộ trong child; dependency injection đi lên root để lấy bean.
- Endpoint `/context` cho phép kiểm tra trực tiếp quan hệ parent và vị trí đăng ký bean.
- Do chỉ trả chuỗi text, ví dụ không yêu cầu cấu hình Jackson/JSON serialization.

### 8.4. Đăng ký cả hai context

Tệp `AppInitializer.java`:

```java
package com.example.hierarchy;

import org.springframework.web.servlet.support
        .AbstractAnnotationConfigDispatcherServletInitializer;

public class AppInitializer
        extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[] { RootConfig.class };
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[] { WebConfig.class };
    }

    @Override
    protected String[] getServletMappings() {
        return new String[] { "/" };
    }
}
```

`AbstractAnnotationConfigDispatcherServletInitializer` hỗ trợ đăng ký `DispatcherServlet` và các context cấu hình Java. Khi `getRootConfigClasses()` trả về `RootConfig.class`, Spring tạo root context; khi `getServletConfigClasses()` trả về `WebConfig.class`, Spring tạo servlet context với root làm parent. Servlet mapping `"/"` đưa các URL của ứng dụng tới `DispatcherServlet` này.

**Chú ý cú pháp import:** Java cho phép ngắt dòng giữa các token phân tách bởi whitespace, nhưng không được ngắt giữa một tên định danh đơn. Để dễ đọc và copy, import có thể viết trên một dòng như sau:

```java
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;
```

### 8.5. Biên dịch, triển khai và quan sát kết quả

Đặt các tệp vào đúng package `com.example.hierarchy` và chạy từ thư mục chứa `pom.xml`:

```bash
mvn -q -DskipTests package
```

Maven tạo WAR tại `target/context-demo.war`. Sao chép WAR vào thư mục `webapps` của Tomcat 10.1 và khởi động Tomcat. Nếu ứng dụng được triển khai với context path mặc định dựa trên tên WAR `/context-demo` và Tomcat đang nghe cổng 8080, gọi:

```bash
curl -s "http://localhost:8080/context-demo/hello?name=Lan"
```

Nội dung phản hồi dự kiến:

```text
Hello Lan
```

Tiếp tục kiểm tra cấu trúc context:

```bash
curl -s "http://localhost:8080/context-demo/context"
```

Nội dung phản hồi dự kiến:

```text
hasParent=true, parentHasService=true, childHasController=true
```

Đây là các giá trị dự kiến khi WAR triển khai đúng, context mapping giữ nguyên, cấu hình không bị sửa và request chưa bị filter/proxy khác can thiệp. Khi truy cập ứng dụng triển khai ở context path khác, thay tiền tố `/context-demo` tương ứng.

Cách tự đọc kết quả:

- `hasParent=true`: controller đang ở child có parent.
- `parentHasService=true`: `greetingService` được đăng ký **cục bộ trong root**, không phải một bean tình cờ được tạo ở child.
- `childHasController=true`: `greetingController` được đăng ký cục bộ trong context mà `DispatcherServlet` sử dụng.
- `/hello` chạy được: child sử dụng thành công dependency `GreetingService` từ parent.

Ví dụ dùng Spring MVC trên Servlet container; không dùng `javac`/ `java` đơn lẻ để chạy một class độc lập vì phải có các dependency, quy trình đóng gói WAR và Servlet container.

## 9. Cách cấu hình tương đương bằng web.xml

Trong các hệ thống truyền thống hoặc legacy, root context và servlet context được khai báo trong `web.xml`. Ví dụ rút gọn sau minh họa cấu hình dựa trên XML (các file `/WEB-INF/root-context.xml` và `/WEB-INF/api-servlet.xml` phải tồn tại và có cấu hình bean hợp lệ):

```xml
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         version="6.0">
    <context-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>/WEB-INF/root-context.xml</param-value>
    </context-param>

    <listener>
        <listener-class>
            org.springframework.web.context.ContextLoaderListener
        </listener-class>
    </listener>

    <servlet>
        <servlet-name>api</servlet-name>
        <servlet-class>
            org.springframework.web.servlet.DispatcherServlet
        </servlet-class>
        <init-param>
            <param-name>contextConfigLocation</param-name>
            <param-value>/WEB-INF/api-servlet.xml</param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>
    </servlet>

    <servlet-mapping>
        <servlet-name>api</servlet-name>
        <url-pattern>/api/*</url-pattern>
    </servlet-mapping>
</web-app>
```

Đây là **mảnh cấu hình giải thích**, không phải ví dụ chạy độc lập: nó yêu cầu hai tệp XML Spring tương ứng và một ứng dụng WAR đầy đủ. Chức năng khác nhau:

- `context-param/contextConfigLocation` ở cấp `web-app` hướng dẫn `ContextLoaderListener` nạp root.
- `init-param/contextConfigLocation` bên trong `<servlet>` hướng dẫn `DispatcherServlet` nạp cấu hình riêng của nó.
- `servlet-mapping` quyết định phần URI do servlet đó phục vụ.

**Không trộn cả `web.xml` và Java initializer để cùng đăng ký root/servlet một cách vô ý.** Nếu dự án đã dùng một cơ chế bootstrap, kiểm tra cấu hình hiện tại trước khi thêm cơ chế thứ hai.

## 10. Nếu có hai DispatcherServlet thì chia sẻ và cách ly ra sao?

Giả sử ứng dụng có:

```text
Root:  sharedService, repository, transactionManager
   |
   +-- Child A (DispatcherServlet "public")
   |     +-- PublicController
   |     +-- các cấu hình MVC public
   |
   +-- Child B (DispatcherServlet "admin")
         +-- AdminController
         +-- các cấu hình MVC admin
```

`PublicController` và `AdminController` có thể inject cùng bean `sharedService` từ root. Nếu cả hai servlet không đăng ký lại bean cùng tên/kiểu, chúng tham chiếu cùng đối tượng root singleton. Nhưng `PublicController` không thể tùy ý lấy `AdminController` chỉ bằng cách lookup trong Child A.

Cần thiết kế các Servlet URL mappings không gây mâu thuẫn, ví dụ `/public/*` và `/admin/*`. Bên trong từng servlet, path mapping của controller được đánh giá theo cách ánh xạ request vào servlet đó. Chức năng phân quyền không tự được thực thi chỉ vì có servlet riêng; vẫn cần cơ chế security phù hợp.

**Tại sao không đặt tất cả controller vào root?** Vì handler discovery và cấu hình web của mỗi servlet cần rõ ràng, đồng thời root nên chỉ giữ phần thực sự cần chia sẻ. Nếu root chứa cả các controller với mapping giống nhau và child bật quét ancestor handlers tùy biến, có thể tạo ra mapping trùng hoặc khó dự đoán.

## 11. Spring Boot thường có kiến trúc context khác

Nhiều tài liệu về Spring MVC truyền thống mô tả bắt buộc có `ContextLoaderListener` root và `DispatcherServlet` child. Cách hiểu đó **không đúng cho mọi ứng dụng Spring Boot**.

Trong Spring Boot Servlet web application thông thường:

- Boot khởi động Spring application context và embedded Servlet container theo cơ chế cấu hình của Boot.
- Boot tự đăng ký và cấu hình `DispatcherServlet` cùng các thành phần MVC cần thiết.
- Controller, service, repository thường cùng nằm trong **một application context** được component scan từ gói chứa class `@SpringBootApplication`.
- Thường không cần tự khai báo `ContextLoaderListener` hoặc viết `AbstractAnnotationConfigDispatcherServletInitializer`.
- Một `DispatcherServlet` trong Boot không có nghĩa mặc nhiên sẽ có một servlet child context độc lập giống sơ đồ WAR cổ điển.

Ví dụ Boot đơn giản:

```java
@SpringBootApplication
public class WebApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
```

Đây là **đoạn minh họa**, chưa chạy độc lập vì lược bỏ imports, dependency, classpath và cấu hình dự án. Khi dùng Spring Boot, hãy kiểm tra context thật sự được tạo thay vì chỉ suy diễn từ tên `DispatcherServlet`.

Có thể có context hierarchy trong một ứng dụng Boot nếu được cấu hình rõ ràng cho một nhu cầu đặc biệt, nhưng đó là lựa chọn chủ động và đòi hỏi hiểu quyền sở hữu context, component scan và bootstrap. Với ứng dụng thông thường, một context đơn giản là đủ.

## 12. Tình huống lỗi phổ biến và cách kiểm tra

### 12.1. Controller không được tìm thấy, request trả 404

Khả năng thường gặp:

- Controller chỉ được đăng ký ở root trong khi servlet child tìm handler ở context local.
- Root/child scan sai package hoặc config class không được nạp vào context dự định.
- Servlet mapping không khớp URL (ví dụ servlet phục vụ `/api/*` nhưng request được gửi đến đường dẫn khác).
- `@GetMapping` và HTTP method/path không khớp, hoặc ứng dụng có context path khác.

Cách kiểm tra là đối chiếu URL gồm **application context path + servlet mapping + controller mapping**; kiểm tra bean controller nằm ở context nào và log startup của Spring MVC.

### 12.2. NoSuchBeanDefinitionException khi inject service

Ví dụ controller ở child cần `OrderService` nhưng không tìm thấy. Các nguyên nhân có thể là `OrderService` chưa được đăng ký, root config chưa được nạp, bean được tạo ở sibling child hoặc package scan không bao gồm class đó.

Đừng vội thêm `@ComponentScan` tràn lan vào child để “cho tìm thấy service”. Nếu service cần dùng chung, đăng ký tại root và xác minh child tham chiếu đúng parent.

### 12.3. Service bị khởi tạo hai lần

Nếu root và child cùng `@ComponentScan("com.example")`, cùng một `@Service` có thể được tạo ở cả hai nơi. Kiểm tra:

- Phạm vi package do mỗi config scan.
- Bean names và bean definitions cục bộ (`containsLocalBean`).
- `ApplicationContext.getParent()` để biết bean trong parent.
- Các `@Bean` factory methods có khai báo trùng không.

Giới hạn component scan theo mục đích: root quét thành phần dùng chung; child quét controller/cấu hình MVC. Trong dự án lớn, có thể dùng `includeFilters`, `excludeFilters` hoặc tách package rõ ràng.

### 12.4. Một bean ở root cố inject controller ở child

Parent **không tra cứu xuống child theo cơ chế thông thường**, nên injection có thể thất bại. Đây thường là dấu hiệu đảo ngược phụ thuộc kiến trúc: service ở root không nên phụ thuộc trực tiếp vào HTTP controller. Dùng service abstraction hoặc ứng dụng event/message phù hợp để tầng nghiệp vụ độc lập với web.

### 12.5. Cấu hình MVC bị đặt nhầm context

`@EnableWebMvc`, các `HandlerMapping`, `HandlerAdapter`, `ViewResolver` thường nên thuộc child của servlet cần sử dụng chúng trong cấu trúc phân cấp. Nếu chỉ có một context, chúng nằm trong context đó. Đừng xem `@EnableWebMvc` là annotation phải thêm vào mọi context.

Đặc biệt với Spring Boot, việc tự thêm `@EnableWebMvc` có thể thay đổi cơ chế cấu hình MVC tự động của Boot. Chỉ bật chủ động khi hiểu và muốn tự kiểm soát cấu hình MVC.

### 12.6. Web scope không hoạt động như dự kiến

`request` và `session` là các scope gắn với web request/session, khác singleton của context. Việc sử dụng scope web đòi hỏi môi trường web thích hợp và truy cập trong phạm vi HTTP request tương ứng. Có root/child không tự làm cho mọi thread bất kỳ có request scope hoạt động; tác vụ nền hoặc thread tự tạo thường không có request attributes của một HTTP request.

### 12.7. Việc gọi getBean(...) cho kết quả khó hiểu

Các phương thức như `containsBean`, `containsLocalBean`, `getBean`, `getBeanNamesForType` có semantics khác nhau với context hierarchy. Đặc biệt, việc tìm bean names chỉ cục bộ có thể không bao gồm ancestor beans. Khi debug, luôn ghi rõ bạn đang xem **root** hay **child** và API tra cứu có tính đến ancestors hay không. Spring cũng có tiện ích `BeanFactoryUtils` để tra cứu theo phân cấp trong những trường hợp cần.

## 13. Tự kiểm tra hiểu biết

**Câu 1.** Trong cấu hình WAR hai context, `CustomerController` ở child cần `CustomerService` ở root. Có thể inject được không?  
**Giải đáp:** Có, nếu service được đăng ký đúng root, child liên kết đúng parent và không có xung đột bean candidate.

**Câu 2.** `CustomerService` ở root có thể inject trực tiếp `CustomerController` chỉ tồn tại ở child không?  
**Giải đáp:** Không theo cơ chế parent lookup thông thường. Parent không tự truy cập bean chỉ tồn tại trong child.

**Câu 3.** Nếu có hai `DispatcherServlet`, có bắt buộc có hai root context không?  
**Giải đáp:** Không. Một root có thể được chia sẻ bởi nhiều servlet-specific child contexts.

**Câu 4.** `ServletContext` của Tomcat và `WebApplicationContext` của Spring có phải cùng kiểu container không?  
**Giải đáp:** Không. `ServletContext` thuộc Jakarta Servlet API; `WebApplicationContext` là Spring IoC container có khả năng tương tác với môi trường Servlet.

**Câu 5.** Một ứng dụng Spring Boot REST có `@RestController` và `@Service` có bắt buộc phải cài `ContextLoaderListener` và child context riêng không?  
**Giải đáp:** Không. Boot thông thường tự bootstrap ứng dụng web và các thành phần MVC, thường chỉ cần một application context.

**Câu 6.** `@Service` được scan ở cả root và child có bảo đảm chỉ một singleton trên toàn ứng dụng không?  
**Giải đáp:** Không. Singleton được quản lý theo bean definition/context; scan trùng có thể tạo hai instance.

**Câu 7.** Một `DispatcherServlet` có thể tự động gọi controller chỉ tồn tại trong child của servlet khác không?  
**Giải đáp:** Không. Hai child là sibling, không có cơ chế tự tìm bean của nhau.

## 14. Tài liệu tham chiếu

- [Spring Framework Reference — DispatcherServlet](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html): vai trò `DispatcherServlet`, bootstrap ứng dụng web và liên kết Servlet environment.
- [Spring Framework Reference — Context Hierarchy](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/context-hierarchy.html): mô hình root và servlet-specific child, chia sẻ bean, Java initializer và cấu hình tương đương qua `web.xml`.
- [Spring Framework Reference — Servlet Config](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/container-config.html): đăng ký `DispatcherServlet` bằng cấu hình Java.
- [Spring Framework Reference — ApplicationContext Hierarchies in Tests](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/hierarchies.html): cách kiểm tra hierarchy trong integration tests.
- [Javadoc — AbstractAnnotationConfigDispatcherServletInitializer](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/web/servlet/support/AbstractAnnotationConfigDispatcherServletInitializer.html): API cấu hình root/servlet và hành vi khi không dùng root riêng.

**Ghi nhớ:** `WebApplicationContext` là Spring IoC container dành cho môi trường web; `ServletContext` là đối tượng Servlet API. Trong mô hình hierarchy của Spring MVC, root giữ bean dùng chung và mỗi `DispatcherServlet` có thể có child context giữ bean web riêng; child thấy parent, parent không tự thấy child. Cấu trúc này hữu ích trong WAR/multi-servlet, còn ứng dụng Spring Boot thông thường thường hoạt động tốt với một application context.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-bootstrap-004">Ánh xạ servlet, context path và đường dẫn của ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Ánh xạ servlet, context path và đường dẫn của ứng dụng trong Spring Web

Khi client gọi `http://localhost:8080/store/api/books/42?lang=vi`, không phải toàn bộ chuỗi `/store/api/books/42` đều thuộc về `@GetMapping`. Servlet container phải xác định **ứng dụng web (context)**, tiếp theo chọn **servlet nhận request**, rồi Spring MVC mới tìm **controller handler**. Hiểu ba tầng này giúp cấu hình URL chính xác, triển khai WAR/JAR, xử lý reverse proxy và chẩn đoán những lỗi 404/405 tưởng như vô lý.

Bài học tập trung vào **Spring MVC chạy trên Jakarta Servlet stack**, phù hợp kiến trúc Spring Framework 6 / Spring Boot 3, Java 17+. Spring WebFlux là một web stack khác; không dùng cấu trúc `DispatcherServlet` của MVC để suy luận cho WebFlux.

## 1. Mục tiêu và ba tầng ánh xạ

Sau bài này, bạn có thể:

- Phân biệt **context path**, **URL pattern của servlet** và **mapping của controller**.
- Hiểu vì sao các pattern `/`, `/*`, `/api/*`, `*.do` khác nhau và servlet nào được chọn.
- Đọc chính xác `getRequestURI()`, `getContextPath()`, `getServletPath()`, `getPathInfo()` và `getQueryString()`.
- Cấu hình `server.servlet.context-path`, hiểu ý nghĩa của `spring.mvc.servlet.path` và viết `@RequestMapping` đúng vị trí.
- Biết cách servlet được đăng ký lúc khởi tạo ứng dụng: Spring Boot embedded server hoặc WAR trên container độc lập.
- Kiểm tra endpoint thực tế bằng HTTP client và phân biệt lỗi đến từ container, Spring MVC hay reverse proxy.

Mô hình tổng quan:

~~~text
Client
  GET /store/api/books/42?lang=vi
               |
               v
Servlet container (Tomcat, Jetty, ...)
  chọn web application theo context path: /store
               |
               v
Servlet URL mapping
  chọn DispatcherServlet theo URL pattern, ví dụ "/"
               |
               v
Spring MVC DispatcherServlet
  chọn handler cho GET + "/api/books/42"
               |
               v
Controller method
  @GetMapping("/{id}") → id=42
               |
               v
Response: HTTP status + headers + body
~~~

**Nguyên tắc:** URL ngoài trình duyệt chịu ảnh hưởng bởi **server origin**, **context path**, **servlet mapping** và **route của ứng dụng**. Query string là dữ liệu truy vấn, không phải phần để servlet container so khớp URL mapping.

## 2. Phân tích URL trước khi gắn annotation

Ví dụ giả định:

~~~text
http://localhost:8080/store/api/books/42?lang=vi#details
|______ origin _____| |context| |controller path| |query| fragment
                     /store    /api/books/42    lang=vi
~~~

Cách ghi trên chỉ minh họa bố cục; trong URL thật **origin** gồm scheme `http`, host `localhost` và port `8080` (nếu có). `#details` là fragment phía client, **không được gửi trong HTTP request-target**. Khi `DispatcherServlet` được map `/`, servlet không thêm một đoạn path riêng vào URL; request đến controller nhờ path `/api/books/42`.

| Thành phần | Giá trị ví dụ | Ai quyết định? |
| --- | --- | --- |
| Scheme, host, port | `http://localhost:8080` | Server/network, client, và có thể cả reverse proxy |
| Context path | `/store` | Cấu hình triển khai web application/servlet container |
| Servlet mapping | `/` | Servlet registration; trường hợp này không thêm prefix vào URL |
| Controller mapping | `/api/books/42` | Spring MVC `@RequestMapping` + `@GetMapping` |
| Query string | `lang=vi` | Client; được tách ra khỏi path mapping |
| Fragment | `#details` | Client sử dụng, không chuyển lên HTTP server |

Nếu `DispatcherServlet` được map theo prefix `/mvc/*`, URL ứng dụng có thể là `/store/mvc/api/books/42`. Khi ấy **`/mvc` thuộc servlet mapping**, còn handler Spring MVC tiếp tục ánh xạ phần bên trong servlet là `/api/books/42`.

### 2.1. Context path là gì?

**Servlet context** đại diện cho một web application được triển khai trong container. Context path là tiền tố URL mà container dùng để xác định application tương ứng. Ví dụ cùng host có thể phục vụ hai ứng dụng độc lập:

~~~text
https://example.test/shop/...   → web application "shop"
https://example.test/admin/...  → web application "admin"
~~~

Trong một ứng dụng có context path `/shop`, request đến `/shop/api/items` được chuyển vào ứng dụng đó rồi mới kiểm tra servlet mapping. Context path **không phải một `@RequestMapping` của controller**.

Theo Servlet specification, ứng dụng ở context root có `request.getContextPath()` bằng **chuỗi rỗng `""`**, dù người dùng thường nói ứng dụng chạy ở đường dẫn gốc `/`. Khi cấu hình context path `/shop`, `getContextPath()` thường là `"/shop"`. Không nên mặc định chuỗi gốc được trả về là `"/"`.

### 2.2. Servlet mapping là gì?

Trong mỗi context, servlet container đăng ký các servlet với tên và một hoặc nhiều URL pattern, ví dụ `/api/*`, `*.action`, `/health` hoặc `/`. Khi request đi vào ứng dụng, container áp dụng quy tắc Servlet URL matching để quyết định **servlet nào được gọi**. Đây là một tầng **trước Spring MVC controller mapping**.

Trong Spring MVC, servlet trọng tâm là `org.springframework.web.servlet.DispatcherServlet`, đóng vai trò *front controller*. Nó nhận request đã được container chọn, rồi chuyển việc cho `HandlerMapping`, `HandlerAdapter`, controller và các thành phần Spring MVC khác.

### 2.3. Controller mapping là gì?

`@RequestMapping` tại cấp class, `@GetMapping`, `@PostMapping` và các annotation tương tự chọn **handler method trong Spring MVC**. Ví dụ:

~~~java
@RestController
@RequestMapping("/api/books")
class BookController {
    @GetMapping("/{id}")
    public String read(@PathVariable long id) {
        return "book-" + id;
    }
}
~~~

Đây là **đoạn controller minh họa**, cần đặt trong ứng dụng Spring MVC, import annotation và có component scanning. Mapping hiệu lực của method là `GET /api/books/{id}` trong phạm vi `DispatcherServlet`; không tự cộng `/store` vào annotation.

## 3. Servlet URL mapping theo đặc tả Jakarta Servlet

Servlet specification cho phép một servlet đăng ký nhiều dạng URL pattern. Container chọn theo mức ưu tiên sau, trong phạm vi context đã xác định:

1. **Exact match**: khớp chính xác đường dẫn, ví dụ `/health`.
2. **Longest path-prefix match**: chọn prefix dài nhất trong các pattern dạng `/path/*`.
3. **Extension match**: so khớp phần mở rộng, ví dụ `*.jsp` hoặc `*.action`.
4. **Default servlet mapping**: pattern `/` được dùng khi không có pattern ưu tiên hơn phù hợp.

Đây là **quy tắc chọn servlet**, không phải thứ tự ưu tiên giữa các method `@GetMapping` trong Spring MVC.

| Pattern | Loại | Ví dụ được khớp | Ghi chú |
| --- | --- | --- | --- |
| `/health` | Exact | `/health` | Không tự khớp `/health/details`. |
| `/api/*` | Path-prefix | `/api/books`, `/api/books/42` | Mọi đường dẫn bên dưới prefix thích hợp; có thể cạnh tranh với exact/prefix dài hơn. |
| `/api/admin/*` | Path-prefix dài hơn | `/api/admin/users` | Ưu tiên hơn `/api/*` nếu cùng khớp. |
| `*.action` | Extension | `/orders/create.action` | Chọn theo hậu tố khi exact/prefix chưa khớp. |
| `/` | Default mapping | Đường dẫn không thuộc mapping ưu tiên hơn | **Khác `/*`**, không phải pattern path-prefix thông thường. |
| `/*` | Path-prefix bao quát | Gần như mọi đường dẫn trong context | Có thể chặn đường tới JSP/default servlet hoặc servlet khác nếu không có exact/prefix ưu tiên. |
| `""` | Context-root exact | Request tới context root | Trường hợp đặc biệt; khác `/`. |

**`/` và `/*` là điểm dễ sai nhất.** Nếu mapping `DispatcherServlet` bằng `/`, nó hoạt động như default servlet mapping và những servlet khác với exact/prefix/extension mapping vẫn có thể nhận request theo thứ tự ưu tiên. Nếu mapping bằng `/*`, đây là một path-prefix mapping rộng: nó có thể bắt cả những request từng đi tới extension mapping/JSP, gây 404 và xung đột xử lý. Khi xây một Spring MVC app thông thường, **mapping `/` là lựa chọn mặc định phù hợp**.

### 3.1. Ví dụ hai servlet tranh quyền nhận request

Giả định một web application tại `/store` có:

~~~text
catalogServlet  → /api/*
adminServlet    → /api/admin/*
statusServlet   → /health
jspServlet      → *.jsp
defaultServlet  → /
~~~

Các URL được chọn như sau:

| Request path không tính `/store` | Servlet được chọn | Lý do |
| --- | --- | --- |
| `/health` | `statusServlet` | Exact match |
| `/api/books/1` | `catalogServlet` | Prefix `/api/*` |
| `/api/admin/users` | `adminServlet` | Prefix dài hơn |
| `/views/home.jsp` | `jspServlet` | Extension match |
| `/images/logo.png` | `defaultServlet` | Không có match ưu tiên hơn |

Kết quả trên **chỉ nói servlet nào được gọi**, không chứng minh servlet đó sẽ trả 200: nó còn phải xử lý được method, tài nguyên và nghiệp vụ tương ứng.

### 3.2. Cấu trúc request path trong Servlet API

Servlet API tách request URI thành các phần. Trong ngữ cảnh thông thường (không xét các sai khác mã hóa/ký tự đặc biệt), có thể hình dung:

~~~text
requestURI ≈ contextPath + servletPath + (pathInfo nếu có)
~~~

Không dùng phương trình đó như phép ghép chuỗi thô với mọi URL: `getRequestURI()` giữ URI chưa được giải mã theo cách khác với `getServletPath()`/`getPathInfo()`; container có quy tắc giải mã, chuẩn hóa và từ chối URI đáng ngờ.

| API | Ý nghĩa | Có bao gồm query? |
| --- | --- | --- |
| `request.getRequestURI()` | Phần path của URI request, thường bao gồm context path | **Không** |
| `request.getContextPath()` | Tiền tố context của ứng dụng; root context trả chuỗi rỗng | Không |
| `request.getServletPath()` | Phần path liên quan đến mapping đã chọn của servlet | Không |
| `request.getPathInfo()` | Phần path nằm ngoài servlet path trong một số loại mapping, hoặc `null` | Không |
| `request.getQueryString()` | Query nguyên bản sau dấu `?`, hoặc `null` | Chỉ chứa query |
| `request.getRequestURL()` | URL request được container dựng, không chứa query string | Không |

Ví dụ **`DispatcherServlet` map `/mvc/*`**, context path `/store`:

~~~text
GET /store/mvc/api/books/42?lang=vi HTTP/1.1

getRequestURI()   = "/store/mvc/api/books/42"
getContextPath()  = "/store"
getServletPath()  = "/mvc"
getPathInfo()     = "/api/books/42"
getQueryString()  = "lang=vi"
~~~

Ví dụ **`DispatcherServlet` map `/`**, vẫn context path `/store`:

~~~text
GET /store/api/books/42?lang=vi HTTP/1.1

getRequestURI()   = "/store/api/books/42"
getContextPath()  = "/store"
getServletPath()  = "/api/books/42"
getPathInfo()     = null
getQueryString()  = "lang=vi"
~~~

Sự khác biệt ở `servletPath`/`pathInfo` là **hệ quả của servlet mapping type**, không có nghĩa đường dẫn controller phải thay đổi. Bạn có thể kiểm tra chính xác mapping hiện tại bằng `request.getHttpServletMapping()` trong Jakarta Servlet 4.0+.

## 4. Spring MVC định tuyến sau khi Servlet container chọn DispatcherServlet

Một request qua nhiều tầng:

~~~text
HTTP request
  → Servlet container xác định context
  → Servlet mapping xác định DispatcherServlet
  → Filter chain chạy theo mapping/dispatcher type đã cấu hình
  → DispatcherServlet
      → HandlerMapping chọn handler (method, path và điều kiện khác)
      → HandlerAdapter gọi handler
      → controller / service
      → HttpMessageConverter hoặc view rendering
  → HTTP response
~~~

Ví dụ controller:

~~~java
@RestController
@RequestMapping("/api")
class ApiController {
    @GetMapping("/books/{id}")
    public String find(@PathVariable long id) {
        return "book-" + id;
    }
}
~~~

Mapping handler là `GET /api/books/{id}`. Với context path `/store` và dispatcher mapping `/`, client gọi `GET /store/api/books/42`. Trong trường hợp prefix `/mvc/*` của dispatcher, URL dự kiến là `GET /store/mvc/api/books/42`. Spring MVC cố xác định path **nằm bên trong phạm vi servlet** để so khớp handler, chứ không yêu cầu `@GetMapping` viết cả tên context.

Spring MVC còn xét điều kiện bổ sung của mapping: HTTP method, `params`, `headers`, `consumes`, `produces` khi được chỉ định. Vì vậy đúng path chưa chắc đủ: gửi POST vào handler chỉ nhận GET thường dẫn tới 405, còn media type/Accept không phù hợp có thể dẫn tới 415/406, tùy ngữ cảnh.

### 4.1. PathPatternParser, ký tự mã hóa và servlet prefix

Spring MVC phiên bản hiện đại hỗ trợ `PathPatternParser` để so khớp **theo từng segment**, tránh một số vấn đề khi giải mã toàn bộ path rồi so chuỗi. Từ Spring Framework 6, đây là chiến lược mặc định của Spring MVC. Tuy nhiên **phiên bản Spring Boot và cách cấu hình `spring.mvc.servlet.path` có thể có ràng buộc tương thích**; một số nhánh Boot 3 ghi rõ `PathPatternParser` mặc định không tương thích với servlet prefix mapping theo cơ chế của Boot. Nếu bắt buộc dùng prefix, cần đối chiếu tài liệu của **phiên bản Boot cụ thể**, chọn chiến lược matching được hỗ trợ và chạy kiểm thử routing thay vì cho rằng mọi cấu hình sẽ hoạt động giống nhau.

Không tự xây route bằng:

~~~java
// Tránh giả định đơn giản hóa này trong code production:
String path = request.getRequestURI()
                     .substring(request.getContextPath().length());
~~~

Đoạn trên chỉ minh họa **cách làm nên tránh**, không phải ví dụ API an toàn: nó chưa giải quyết servlet prefix, encoded slash, matrix variables, proxy path rewriting và những trường hợp Servlet API trả giá trị đã giải mã/chuẩn hóa khác nhau. Trong Spring MVC hãy ưu tiên `@RequestMapping`, `@PathVariable`, `@RequestParam` và API xây dựng URI chính thức của framework.

## 5. Cấu hình context path khi chạy Spring Boot

Spring Boot với embedded Servlet container tự khởi tạo web server và đăng ký `DispatcherServlet` khi có starter MVC thích hợp. Trong ứng dụng Spring Boot 3 thông thường, DispatcherServlet được map tại `/`. Context path mặc định ở root nên URL không có tiền tố ứng dụng riêng.

Để đặt **context path**:

~~~properties
# src/main/resources/application.properties
server.port=8080
server.servlet.context-path=/store
~~~

Hoặc YAML tương đương:

~~~yaml
server:
  port: 8080
  servlet:
    context-path: /store
~~~

Với controller mapping `/api/books/{id}`, URL public trực tiếp sẽ là:

~~~text
http://localhost:8080/store/api/books/42
~~~

**Không thêm `/store` vào `@RequestMapping`**. Nếu bạn viết `@RequestMapping("/store/api/books")` trong khi đã cấu hình `server.servlet.context-path=/store`, route trong ứng dụng sẽ bị cộng thành `/store/store/api/books/... ` và client gọi URL dự kiến sẽ gặp lỗi. Sửa bằng cách tách đúng tầng cấu hình, không nhân đôi prefix.

Tài liệu cấu hình thuộc Spring Boot, không phải thuộc tính chuẩn bắt buộc của mọi ứng dụng Spring Framework chạy trên container bên ngoài. Khi triển khai WAR vào một Tomcat độc lập, **container/deployment** có thể quyết định context path theo cấu hình, tên WAR hoặc descriptor; không nên giả định `server.servlet.context-path` luôn có hiệu lực giống executable JAR với embedded server.

### 5.1. Spring MVC servlet path: một tầng khác

Spring Boot có thuộc tính:

~~~properties
spring.mvc.servlet.path=/mvc
~~~

Về ý nghĩa, thuộc tính này dùng để đổi đường dẫn **đăng ký DispatcherServlet** dưới prefix, ví dụ từ `/` sang một mapping tương ứng `/mvc/*`. Nếu đồng thời có `server.servlet.context-path=/store`, handler `@GetMapping("/api/books/{id}")` sẽ được truy cập theo URL dự kiến:

~~~text
http://localhost:8080/store/mvc/api/books/42
~~~

Chỉ dùng cấu hình này khi thực sự cần **không gian URL dành riêng cho DispatcherServlet** hoặc phải dùng thêm servlet khác. Không lẫn:

| Thành phần | Thuộc tính/annotation | Tác động |
| --- | --- | --- |
| Web application context | `server.servlet.context-path=/store` | Prefix áp dụng cho toàn bộ web application |
| DispatcherServlet registration | `spring.mvc.servlet.path=/mvc` | Prefix cho các URL tới servlet MVC (nếu phiên bản/chiến lược matching hỗ trợ) |
| Controller route | `@RequestMapping("/api/books")` | Path bên trong Spring MVC |
| URL port | `server.port=8080` | Cổng lắng nghe của embedded server |

**Lưu ý tương thích:** ở một số bản Spring Boot 3.x, `spring.mvc.pathmatch.matching-strategy=path-pattern-parser` không làm việc cùng servlet prefix mapping; tài liệu Boot chỉ rõ giới hạn đó. Nếu gặp lỗi khởi tạo hoặc route không match khi đặt `spring.mvc.servlet.path`, kiểm tra đúng tài liệu phiên bản, xem lựa chọn `ant-path-matcher` được hỗ trợ hay không hoặc thiết kế lại mapping dùng context path và controller route. Không áp dụng sửa cấu hình này máy móc cho mọi bản Spring.

### 5.2. API path prefix không nhất thiết là servlet path

Giả sử chỉ cần các endpoint có URL `/api/v1/books`, bạn có thể đơn giản viết:

~~~java
@RestController
@RequestMapping("/api/v1/books")
class BookController {
    @GetMapping("/{id}")
    public String get(@PathVariable long id) {
        return "book-" + id;
    }
}
~~~

Nếu server ở root context và dispatcher mặc định `/`, URL là `/api/v1/books/42`. Không cần đổi `spring.mvc.servlet.path`. Ngược lại, nếu đổi servlet path chỉ để thêm `/api`, bạn đã tác động đến **mọi route xử lý bởi DispatcherServlet**, kể cả các route không thuộc nhóm API, MVC view/error handling hay tài nguyên động tùy cách cấu hình. Hãy chọn thay đổi nhỏ nhất đúng mục đích.

## 6. Ví dụ Spring Boot 3 hoàn chỉnh: tự xem cách path được chia

Đây là ví dụ **Java 17+** trong dự án Spring Boot 3 có dependency `spring-boot-starter-web`. Tệp `src/main/java/demo/web/WebPathApplication.java` chứa một public main class và một controller package-private; cả hai đều nằm trong package `demo.web`, bảo đảm component scanning tìm được controller.

~~~java
package demo.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class WebPathApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebPathApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/books")
class BookController {

    @GetMapping("/{id}")
    public PathResult describe(
            @PathVariable long id,
            @RequestParam(defaultValue = "vi") String lang,
            HttpServletRequest request) {

        return new PathResult(
                id,
                lang,
                request.getRequestURI(),
                request.getContextPath(),
                request.getServletPath(),
                request.getPathInfo(),
                request.getQueryString()
        );
    }

    record PathResult(
            long id,
            String lang,
            String requestURI,
            String contextPath,
            String servletPath,
            String pathInfo,
            String queryString
    ) {}
}
~~~

Ví dụ này không dùng external database. `@RestController` khiến Spring MVC serialize `PathResult` thành JSON thông qua `HttpMessageConverter` thích hợp (Jackson khi dùng starter web mặc định). `HttpServletRequest` được Spring đưa vào handler method từ request thực tế.

Tạo tệp `src/main/resources/application.properties`:

~~~properties
server.port=8080
server.servlet.context-path=/store
~~~

Giữ **`spring.mvc.servlet.path` không cấu hình**, tức dùng dispatcher mapping mặc định. Nếu dự án Gradle đã được cấu hình Spring Boot plugin và starter web, từ thư mục project chạy:

~~~bash
./gradlew bootRun
~~~

Trên Windows PowerShell dùng:

~~~powershell
.\gradlew.bat bootRun
~~~

Lệnh trên chỉ đúng nếu **Gradle project đã có wrapper và cấu hình Spring Boot phù hợp**; đoạn Java đơn lẻ không chạy bằng `javac WebPathApplication.java` vì phụ thuộc thư viện Spring/Servlet.

Gọi endpoint bằng curl:

~~~bash
curl -i "http://localhost:8080/store/api/books/42?lang=vi"
~~~

Phản hồi thành công có **HTTP 200** và JSON tương đương:

~~~json
{
  "id": 42,
  "lang": "vi",
  "requestURI": "/store/api/books/42",
  "contextPath": "/store",
  "servletPath": "/api/books/42",
  "pathInfo": null,
  "queryString": "lang=vi"
}
~~~

Thứ tự field/whitespace/headers JSON thực tế có thể thay đổi theo cấu hình Jackson/container, nhưng các giá trị path ở đây được kỳ vọng với URL đơn giản, context `/store` và dispatcher map `/`. Hãy chú ý ba điểm:

1. `requestURI` chứa `/store` nhưng **không chứa** `?lang=vi`.
2. `contextPath` là `/store`, còn `servletPath` là toàn bộ path bên trong context khi DispatcherServlet dùng default mapping `/`.
3. `pathInfo` là `null` trong trường hợp default mapping đó; giá trị của nó **không phải** `"/42"`.

Nếu thay URL bằng:

~~~bash
curl -i "http://localhost:8080/api/books/42?lang=vi"
~~~

Bạn đang thiếu `/store`, nên request thông thường **không đi vào context mong muốn** và nhận phản hồi lỗi tương ứng (thường là 404). Nếu thay bằng `/store/api/books/42` không có query, `lang` sẽ là `"vi"` nhờ giá trị mặc định trong `@RequestParam`, nhưng `queryString` sẽ là `null`.

### 6.1. Thí nghiệm đổi context path

Thay `server.servlet.context-path=/store` thành:

~~~properties
server.servlet.context-path=/catalog
~~~

Khởi động lại ứng dụng. Đường dẫn hiện hợp lệ:

~~~text
http://localhost:8080/catalog/api/books/42?lang=vi
~~~

Controller **không thay đổi một ký tự nào**. Kết quả sẽ có `contextPath="/catalog"`, `requestURI="/catalog/api/books/42"`, còn `servletPath="/api/books/42"`. Đây là phép thử trực quan cho việc **context path thuộc container**, không thuộc route của controller.

### 6.2. Thí nghiệm quan sát servlet mapping prefix (tùy phiên bản)

Giả định DispatcherServlet được đăng ký với pattern `/mvc/*` và hệ thống Spring MVC đã cấu hình tương thích với prefix mapping. Khi đó:

~~~text
Request            /store/mvc/api/books/42?lang=vi
Context path       /store
Servlet path       /mvc
Path info          /api/books/42
Controller route   /api/books/{id}
~~~

Những giá trị này minh họa **Servlet specification**. Không nên bật `spring.mvc.servlet.path=/mvc` trong ứng dụng đang chạy mà chưa đọc lưu ý tương thích với chiến lược matching nêu ở mục 5.1.

## 7. Đăng ký DispatcherServlet khi khởi tạo ứng dụng

Một servlet chỉ được nhận request sau khi **được container đăng ký**, có tên/instance và URL mapping. Có hai môi trường triển khai phổ biến.

### 7.1. Spring Boot executable JAR với embedded Servlet container

Thông thường:

1. Chạy `SpringApplication.run(...)`.
2. Spring Boot khởi tạo `ApplicationContext` và embedded server nếu ứng dụng là Servlet web app.
3. Boot auto-configure `DispatcherServlet` và đăng ký servlet với container, mặc định tại `/`.
4. Container bắt đầu nhận request ở cổng cấu hình.
5. Spring MVC sử dụng các handler mapping của `ApplicationContext` để tìm controller.

Khi dùng starter MVC chuẩn, **không cần tự khai báo `DispatcherServlet` trong `web.xml`**. Nếu đã có các đăng ký servlet tùy chỉnh hoặc cấu hình MVC đặc biệt, đừng giả định mặc định Boot còn nguyên.

### 7.2. Ứng dụng Spring MVC truyền thống triển khai WAR

Servlet container bên ngoài có thể đọc `WEB-INF/web.xml` để đăng ký DispatcherServlet. Ví dụ tối giản minh họa servlet mapping:

~~~xml
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
    <load-on-startup>1</load-on-startup>
</servlet>

<servlet-mapping>
    <servlet-name>dispatcher</servlet-name>
    <url-pattern>/</url-pattern>
</servlet-mapping>
~~~

**Chỉ là đoạn khai báo liên quan đến mapping**, chưa phải `web.xml` hoàn chỉnh có đầy đủ schema, Spring context/config và cấu hình ứng dụng. Trong dự án hiện đại có thể dùng Java initializer thay XML. Điểm cốt lõi là `url-pattern` chọn servlet ở cấp Servlet container, còn `@RequestMapping` chọn controller ở cấp Spring.

Với Java configuration, Spring MVC hỗ trợ `WebApplicationInitializer` và các lớp tiện ích như `AbstractAnnotationConfigDispatcherServletInitializer`. Một ví dụ initializer cho WAR:

~~~java
package demo.web;

import org.springframework.web.servlet.support
        .AbstractAnnotationConfigDispatcherServletInitializer;

public class WebInitializer
        extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return null;
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[] { WebMvcConfig.class };
    }

    @Override
    protected String[] getServletMappings() {
        return new String[] { "/" };
    }
}
~~~

Ở đây `WebMvcConfig` là **lớp cấu hình Spring MVC của ứng dụng phải có sẵn**, ví dụ `@Configuration`, `@EnableWebMvc`, `@ComponentScan`; ví dụ initializer không được coi là tệp Java độc lập chạy ngay khi thiếu lớp đó. Chỉ sử dụng **một cơ chế registration chủ động, nhất quán**; việc vừa đăng ký cùng servlet qua XML vừa qua initializer có thể gây xung đột.

### 7.3. Đăng ký một servlet phụ bên cạnh DispatcherServlet

Spring Boot có thể đăng ký thêm `Servlet` qua `ServletRegistrationBean` khi cần một endpoint servlet truyền thống độc lập. Đoạn dưới là **ví dụ class cấu hình** đặt trong Spring Boot Servlet application có servlet API:

~~~java
package demo.web;

import java.io.IOException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ExtraServletConfig {

    @Bean
    ServletRegistrationBean<HttpServlet> extraServlet() {
        HttpServlet servlet = new HttpServlet() {
            @Override
            protected void doGet(
                    HttpServletRequest request,
                    HttpServletResponse response) throws IOException {
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("legacy servlet");
            }
        };

        return new ServletRegistrationBean<>(servlet, "/legacy/*");
    }
}
~~~

Nếu context path là `/store`, URL `/store/legacy/test` được container ánh xạ vào servlet này nhờ prefix `/legacy/*`. Theo Servlet matching, `/legacy/*` được ưu tiên hơn default mapping `/` của DispatcherServlet. Response body minh họa chính xác:

~~~text
legacy servlet
~~~

Không tạo `@GetMapping("/legacy/test")` cho request này chỉ để nó hoạt động; ở ví dụ này **servlet phụ đã nhận request trước**, Spring MVC controller không tham gia. Cần nhớ `Filter` cũng có mapping riêng và có thể áp dụng với cả hai servlet, tùy cách đăng ký.

## 8. Phân biệt servlet mapping và Filter mapping

`Filter` (ví dụ logging, authentication, CORS handling tùy hệ thống) có thể được đăng ký với URL pattern hoặc servlet name. Đây là **chuỗi filter của container**, không phải `@RequestMapping` và cũng không quyết định controller method trực tiếp.

Ví dụ:

~~~text
Filter mapping: /*          → chạy cho đường dẫn phù hợp trong context
DispatcherServlet: /         → servlet MVC mặc định
LegacyServlet: /legacy/*     → servlet riêng
~~~

Khi gọi `/store/legacy/test`, filter match `/*` vẫn có thể chạy; sau đó container chọn LegacyServlet. Cũng cùng URL pattern `/*`, nhưng dùng cho **filter mapping** có ý nghĩa khác việc đăng ký **DispatcherServlet mapping** bằng `/*`.

Filter không tự được áp dụng cho mọi loại dispatch nếu chưa cấu hình: `REQUEST`, `FORWARD`, `INCLUDE`, `ASYNC`, `ERROR` có thể ảnh hưởng. Hãy kiểm tra `DispatcherType` và thứ tự chain thực tế khi xử lý lỗi, forward hay async.

## 9. Reverse proxy, ingress và đường dẫn ngoài ứng dụng

Khi triển khai thật, URL trình duyệt thấy có thể **khác URL mà container nhận**. Ví dụ:

~~~text
Client:
  https://example.test/platform/store/api/books/42
       |
       v
Reverse proxy:
  bỏ tiền tố /platform
  forward tới http://backend:8080/store/api/books/42
       |
       v
Spring Boot:
  context path = /store
  DispatcherServlet = /
  controller mapping = /api/books/{id}
~~~

Trong ví dụ này, `/platform` thuộc **route của proxy**, không tự động trở thành `request.getContextPath()`. Nếu app tự sinh link redirect theo URL nội bộ và không biết external prefix, client có thể bị dẫn tới URL thiếu `/platform`.

Proxy có thể truyền metadata như `Forwarded`, `X-Forwarded-Proto`, `X-Forwarded-Host`, `X-Forwarded-Prefix`. Spring Boot Servlet app có các cơ chế như `server.forward-headers-strategy` (ví dụ `NATIVE` hoặc `FRAMEWORK`) để xử lý forwarded headers khi môi trường cho phép. **Chỉ tin các forwarded headers từ reverse proxy đáng tin cậy**: nếu client ngoài Internet tự đặt được chúng, việc tạo redirect hoặc xác định host/scheme có thể sai hoặc không an toàn.

Đừng đồng thời thêm `/platform` vào cả ingress rewrite, context path và controller mapping chỉ vì thấy URL browser có prefix đó. Hãy xác định **proxy có giữ hay bỏ prefix** trước tiên, rồi cấu hình một nguồn sở hữu rõ ràng.

### 9.1. Static resources và root path

Trong Spring Boot MVC, static resources có thể được phục vụ từ các thư mục classpath tiêu chuẩn như `/static`, `/public` theo các cấu hình mặc định; handler mapping cho static resources có thể dùng pattern `/**`. Điều này là **Spring MVC resource handler mapping**, khác với **default servlet URL mapping `/`** ở Servlet container.

Vì vậy request trả 404 không luôn có nghĩa “không có servlet”: có thể request đã đi qua DispatcherServlet, bị handler static resources nhận rồi phát hiện file không tồn tại. Những khác biệt này ảnh hưởng exception được phát ra và cách `@ControllerAdvice` quan sát 404.

## 10. Quy trình debug khi path trả 404 hoặc 405

Giả sử bạn mong đợi `GET /store/api/books/42`:

1. **Kiểm tra origin và context path.** Server đang lắng nghe cổng nào? App đã deploy tại `/store`, `/` hay một tên WAR khác?
2. **Kiểm tra servlet registration/mapping.** DispatcherServlet đang map `/`, `/mvc/*` hay `/*`? Có servlet khác với exact/prefix/extension mapping ưu tiên hơn không?
3. **Kiểm tra controller mapping thực.** Tổng của class-level và method-level `@RequestMapping` là gì? Annotation có đúng method/params/headers/consumes/produces không?
4. **So request path sau proxy.** Proxy giữ hay xóa prefix? Có rewritten URL, trailing slash, encoded path hoặc route gateway không?
5. **Xem response thực tế.** 404 thường liên quan path/tài nguyên không match; 405 cho biết method không được phép trong ngữ cảnh tương ứng; 401/403 có thể đến từ security filter trước khi vào controller.
6. **Đối chiếu logs/tracing và handler mappings.** Nếu dùng Spring Boot Actuator và endpoint mappings được bật/cấp quyền phù hợp, có thể xem mapping đã đăng ký. Không công khai thông tin này ngoài môi trường quản trị.

Thử với curl:

~~~bash
curl -i "http://localhost:8080/store/api/books/42?lang=vi"
curl -i "http://localhost:8080/api/books/42?lang=vi"
curl -i -X POST "http://localhost:8080/store/api/books/42"
~~~

Với controller ví dụ chỉ có `@GetMapping`:
- Request đầu thường thành công 200.
- Request thứ hai thiếu `/store` nên thường 404.
- Request thứ ba có đúng URI trong context nhưng method không được controller hỗ trợ; thường dẫn tới 405 (nếu không có filter/proxy/handler khác can thiệp).

**Đừng chỉ nhìn body lỗi dạng JSON.** Trạng thái HTTP, `Allow` header trong 405, cấu hình security, route proxy và log về handler được chọn giúp xác định tầng nào đã từ chối request.

### 10.1. Những lỗi hay gặp và cách sửa

| Dấu hiệu | Nguyên nhân khả dĩ | Cách xử lý |
| --- | --- | --- |
| Controller đúng nhưng `/api/...` trả 404 | Đã đặt `server.servlet.context-path=/store` | Gọi `/store/api/...`, không đổi controller. |
| URL có `/store/store/api/...` | Đã cộng context prefix vào cả controller | Bỏ `/store` khỏi handler mapping. |
| Prefix `/mvc` làm route không match | Servlet prefix khác với path của controller hoặc xung đột matching strategy | Kiểm tra registration, Servlet API path parts và compatibility phiên bản. |
| JSP/extension routes đột nhiên hỏng | Dùng dispatcher mapping `/*` quá rộng | Xem lại default mapping `/` và thứ tự servlet match. |
| Endpoint nhận GET nhưng không nhận POST | Method mapping không hỗ trợ POST | Dùng đúng method hoặc thêm handler POST theo hợp đồng API. |
| Dịch vụ sau ingress redirect sai hostname/path | Forwarded headers hoặc rewrite chưa đúng | Kiểm tra external/internal URL, trusted proxy và strategy xử lý forwarding. |
| Static file trả 404 dù controller khác hoạt động | Static resource location/pattern không phù hợp | Kiểm tra resource handler và vị trí file. |
| `getServletPath()` không như kỳ vọng | Servlet mapping khác `/` | Đọc `getHttpServletMapping()`, `getPathInfo()`, không suy từ URL bằng tay. |

## 11. Bài tập thực hành và đáp án

**Bài 1.** Ứng dụng có `server.servlet.context-path=/academy`, DispatcherServlet mapping `/`, controller:

~~~java
@RestController
@RequestMapping("/courses")
class CourseController {
    @GetMapping("/{id}")
    public String get(@PathVariable long id) {
        return "course-" + id;
    }
}
~~~

URL nào gọi được method cho id 7? Nếu app đang ở origin `http://localhost:8080` và không có route khác can thiệp, body là gì?

**Bài 2.** Với context path `/academy` và DispatcherServlet URL pattern `/mvc/*`, request là `/academy/mvc/courses/7?lang=vi`. Hãy cho các giá trị `requestURI`, `contextPath`, `servletPath`, `pathInfo`, `queryString`.

**Bài 3.** Trong cùng application, Servlet A map `/`, Servlet B map `*.jsp`, Servlet C map `/api/*`. Servlet nào nhận `/api/books`, `/view.jsp`, `/favicon.ico`?

**Bài 4.** Tại sao đổi `@RequestMapping("/courses")` thành `@RequestMapping("/academy/courses")` không phải cách đúng để xử lý context path `/academy`?

**Bài 5.** Proxy phục vụ `/platform/academy/courses/7` nhưng forward nội bộ thành `/academy/courses/7`. Khi đọc `getContextPath()` trong app sẽ thu được `/platform/academy` hay `/academy` theo request nội bộ thông thường? App cần cân nhắc thêm vấn đề gì khi tạo external links?

### Đáp án có giải thích

1. URL `http://localhost:8080/academy/courses/7`. Body text `course-7` (với converter/cấu hình mặc định hỗ trợ `String` response); route không viết context path vào annotation.
2. `requestURI="/academy/mvc/courses/7"`, `contextPath="/academy"`, `servletPath="/mvc"`, `pathInfo="/courses/7"`, `queryString="lang=vi"`, với path đơn giản theo Servlet specification.
3. `/api/books` do C nhận (prefix mapping); `/view.jsp` do B nhận (extension mapping); `/favicon.ico` do A nhận (default mapping).
4. Vì context path đã do container áp dụng. Viết thêm `/academy` trong controller sẽ sinh đường dẫn `/academy/academy/courses/7` trong cấu hình đó.
5. Theo request nội bộ thông thường, context path là `/academy`, không phải `/platform/academy`. Khi sinh link cần nhận diện external URL, prefix rewrite và xử lý forwarded headers từ proxy tin cậy.

## 12. Checklist tư duy khi xây URL trong Spring Web

Để xác định URL chính xác, lần lượt trả lời:
1. **Ứng dụng chạy ở đâu?** Scheme/host/port nào client truy cập; proxy rewrite ra sao?
2. **Application nào nhận request?** Context path thực tế là gì?
3. **Servlet nào nhận request?** URL pattern đăng ký của DispatcherServlet hay servlet phụ là gì?
4. **Handler nào được chọn?** Controller class/method mapping và các điều kiện HTTP có khớp?
5. **Thực tế container đã thấy gì?** Kiểm tra Servlet API path components, mapping type, request URL và logs.

Bốn nguyên tắc cần giữ: **context path** là tiền tố của ứng dụng web; **servlet mapping** chọn servlet ở tầng container; **controller mapping** chọn handler bên trong Spring MVC; **proxy path** là một tầng độc lập có thể rewrite URL. Khi tách đúng trách nhiệm, việc triển khai JAR/WAR, đổi prefix và tìm lỗi routing sẽ nhất quán hơn rất nhiều.

**Tài liệu chuẩn đối chiếu:** Jakarta Servlet Specification 6.0, mục *Request Path Elements* và *Mapping Requests to Servlets* (https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0.html); Spring Framework MVC *DispatcherServlet* và *Path Matching* (https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html); Spring Boot Reference *Servlet Web Applications*, *Spring MVC* và *Embedded Web Servers* (https://docs.spring.io/spring-boot/reference/web/servlet.html; https://docs.spring.io/spring-boot/how-to/spring-mvc.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-bootstrap-005">@EnableWebMvc, WebMvcConfigurer và cấu hình XML tương đương</a>

<details>
<summary>Xem chi tiết</summary>

# @EnableWebMvc, WebMvcConfigurer và cấu hình XML tương đương trong Spring MVC

Khi đọc dự án Spring Web, có thể gặp ba cách khai báo cấu hình: **Java config với @EnableWebMvc**, **các callback của WebMvcConfigurer**, hoặc **XML có phần tử mvc:annotation-driven và các phần tử mvc khác**. Chúng cùng giải quyết cấu hình Spring MVC nhưng **không có cùng vai trò**. Đặc biệt, trong ứng dụng **Spring Boot**, gắn @EnableWebMvc không phải bước bắt buộc và thường làm mất một phần tiện ích MVC mà Boot đang tự cấu hình.

Bài học này phân biệt từng thành phần, chỉ ra vị trí cấu hình trong quá trình khởi động Servlet, đưa ra ví dụ Java chạy được, đối chiếu Java–XML theo từng chức năng và cách chọn cấu hình phù hợp khi dùng hoặc không dùng Spring Boot.

## 1. Bức tranh tổng thể

Spring MVC là web framework trên **Servlet stack**. Để ứng dụng nhận request HTTP và gọi controller, phải có cả **Servlet tiếp nhận request**, **ApplicationContext chứa MVC infrastructure** và **controller/handler**.

~~~text
HTTP client
    |
Servlet container: Tomcat / Jetty ...
    |
Servlet mapping và filter chain
    |
DispatcherServlet  <-- được đăng ký qua Boot, initializer hoặc web.xml
    |
Spring WebApplicationContext của servlet
    |
    +-- HandlerMapping: tìm @GetMapping, @PostMapping...
    +-- HandlerAdapter: gọi controller, binding tham số
    +-- HandlerExceptionResolver: xử lý exception
    +-- HttpMessageConverter: đọc/ghi JSON, text, ...
    +-- ViewResolver / resource handler / interceptor / CORS...
    |
Controller -> Service -> response
~~~

Ba vai trò cần nhớ:

1. **@EnableWebMvc:** bật bộ **cấu hình hạ tầng Spring MVC dựa trên Java** trong ApplicationContext; đứng phía cấu hình web stack, không trực tiếp khởi tạo HTTP server hoặc tự đăng ký DispatcherServlet.
2. **WebMvcConfigurer:** giao diện callback để **tùy chỉnh cấu hình MVC**, như interceptor, formatter, static resource, CORS, view controller, path matching. Có thể dùng cùng @EnableWebMvc khi tự quản lý MVC, hoặc dùng riêng trong Spring Boot để giữ auto-configuration.
3. **XML namespace mvc:** biểu diễn nhiều cấu hình tương đương về chức năng bằng XML. Phần tử **mvc:annotation-driven** đóng vai trò bật phần lớn cơ sở hạ tầng tương tự @EnableWebMvc; **mvc:interceptors**, **mvc:resources**, **mvc:cors**... tương ứng với các callback ở Java config.

**Tách hai câu hỏi:** Ai đăng ký và phục vụ **DispatcherServlet**? Ai tạo/tùy chỉnh các **MVC beans** mà servlet sử dụng? Tập trung vào vế thứ hai sẽ hiểu đúng @EnableWebMvc và WebMvcConfigurer.

## 2. Bảng nhận diện nhanh

| Thành phần | Tác dụng chính | Khi dùng phù hợp | Điều dễ nhầm |
|---|---|---|---|
| **@EnableWebMvc** | Import cấu hình MVC nền tảng vào Spring context | Spring MVC thuần, muốn chủ động bật framework infrastructure; Boot khi cố ý tự kiểm soát MVC | Không phải annotation bắt buộc cho mọi ứng dụng Boot |
| **WebMvcConfigurer** | Cho phép thêm/sửa cấu hình MVC qua các phương thức callback | Boot muốn bổ sung MVC mà vẫn dùng các mặc định của Boot; hoặc cùng @EnableWebMvc trong MVC thuần | Tự implement interface không tự bật DispatcherServlet hay khởi động server |
| **mvc:annotation-driven** | Bật MVC annotation support và các thành phần liên quan qua XML | Ứng dụng Spring MVC dùng cấu hình XML | Không tự đăng ký servlet trong web.xml |
| **mvc:interceptors** | Khai báo MVC interceptors | Spring MVC XML | Không thay cho Servlet Filter hay Spring Security |
| **mvc:resources** | Ánh xạ URL tới nguồn static resource | Spring MVC XML | Không mặc định chỉ tới mọi thư mục và mọi resource của Boot |
| **mvc:cors** | Cấu hình CORS theo URL pattern | Spring MVC XML | CORS không phải xác thực/phân quyền |
| **mvc:view-controller** | Route tới view hoặc redirect/forward không cần controller method | Spring MVC XML | Không dùng để xử lý nghiệp vụ API phức tạp |
| **Spring Boot MVC auto-configuration** | Thiết lập sẵn phần lớn MVC + các tích hợp riêng của Boot | Ứng dụng Boot dùng Spring Web | Bị ảnh hưởng khi bật @EnableWebMvc hoặc khai báo cơ sở hạ tầng MVC tùy biến cấp thấp |

## 3. @EnableWebMvc thực sự làm gì?

Một cấu hình Java cơ bản của **Spring Framework MVC không dùng Boot**:

~~~java
package example.mvc;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
public class WebConfig {
}
~~~

**Ý nghĩa từng annotation:**

- **@Configuration:** đăng ký lớp làm nguồn định nghĩa Spring beans.
- **@EnableWebMvc:** kích hoạt Java-based MVC configuration, thông qua cơ chế import **DelegatingWebMvcConfiguration**. Cấu hình này đăng ký nhiều thành phần cần thiết để các controller dựa trên @RequestMapping hoạt động.
- Đây là **mã cấu hình minh họa, chưa phải ứng dụng HTTP chạy độc lập**. Muốn nhận request phải có servlet container, DispatcherServlet được đăng ký và controller nằm trong context phù hợp.

DelegatingWebMvcConfiguration là lớp cấu hình dựa trên **WebMvcConfigurationSupport**, đồng thời thu thập các bean **WebMvcConfigurer** đã đăng ký rồi gọi các callback thích hợp. Những thành phần MVC mặc định được thiết lập theo khả năng thư viện trên classpath và cấu hình thực tế; ví dụ cần có thư viện JSON thích hợp để chuyển đổi object thành JSON.

### Không phải các việc @EnableWebMvc làm

- Không khởi động Tomcat hay bất kỳ cổng HTTP nào.
- Không thay cho **DispatcherServlet registration**.
- Không tự động quét mọi controller ở mọi package.
- Không tự tải thêm Jackson, JSP, Thymeleaf hoặc dependency khác từ Maven.
- Không có nghĩa “ứng dụng hiện đã chạy Spring Boot”.
- Không áp dụng cho **Spring WebFlux**; WebFlux có mô hình cấu hình khác.

### Tại sao Spring Boot thường không cần @EnableWebMvc?

Spring Boot đưa vào **WebMvcAutoConfiguration** khi điều kiện về web stack/classpath thích hợp. Nó tự cung cấp cấu hình MVC cùng những tiện ích như resource handling, view resolvers, converter/formatter integration và hỗ trợ các properties tương ứng.

Nếu thêm **@EnableWebMvc** vào một dự án Boot MVC, bạn chuyển hướng sang **toàn quyền cấu hình MVC theo Spring Framework**; phần lớn MVC auto-configuration đặc thù của Boot **không còn được áp dụng như trước**. Controller có thể vẫn chạy, khiến lỗi khó nhận ra; nhưng các mặc định như phục vụ tài nguyên tĩnh, nội dung JSON, các customizations theo properties có thể thay đổi và cần chủ động cấu hình lại.

**Quy tắc thực dụng:** Trong Spring Boot, nếu chỉ cần thêm CORS, interceptor, formatter, resource mapping hoặc view controller, hãy dùng **@Configuration implements WebMvcConfigurer**, **không thêm @EnableWebMvc**.

## 4. WebMvcConfigurer: cơ chế tùy chỉnh có chọn lọc

**WebMvcConfigurer** là interface chứa các phương thức mặc định (default methods), nên lớp triển khai chỉ override phần cần dùng.

~~~java
package example.mvc;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // Override đúng các callback MVC cần dùng.
}
~~~

Trong đoạn trên, lớp vẫn phải được **đăng ký trong Spring ApplicationContext**. @Configuration kết hợp component scan/explicit import là cách thường dùng. Nếu lớp không được Spring phát hiện, các callback sẽ không chạy. Một class chỉ “implements WebMvcConfigurer” nhưng không phải bean sẽ không tự được áp dụng.

### Những callback quan trọng

| Callback Java | Chức năng | XML tương ứng ở mức chức năng |
|---|---|---|
| **addInterceptors** | Chèn HandlerInterceptor cho URL patterns | mvc:interceptors |
| **addResourceHandlers** | Ánh xạ URL tới static resource locations và cache | mvc:resources |
| **addCorsMappings** | CORS policy theo path | mvc:cors |
| **addViewControllers** | URL trực tiếp tới view/redirect | mvc:view-controller |
| **addFormatters** | Đăng ký Converter/Formatter dùng cho MVC binding | mvc:annotation-driven với conversion-service và converter beans |
| **configureContentNegotiation** | Điều chỉnh cách xác định requested media types | Thuộc tính/phần tử content-negotiation tương ứng tùy schema/phiên bản |
| **configureViewResolvers** | Cấu hình ViewResolver | Bean ViewResolver hoặc phần tử mvc:view-resolvers tùy phiên bản |
| **configureMessageConverters / extendMessageConverters** | Kiểm soát cách đọc/ghi HTTP body | mvc:annotation-driven với message-converters |
| **configurePathMatch** | Tùy chỉnh cách MVC khớp path | Một số tùy chọn XML/schema có thể khác; không luôn ánh xạ 1:1 |
| **addArgumentResolvers** | Bổ sung resolver cho tham số controller | mvc:annotation-driven/argument-resolvers theo schema tương ứng |
| **addReturnValueHandlers** | Bổ sung handler cho kiểu return value | Cấu hình nâng cao, không luôn có một phần tử XML trực tiếp |

**Đây là bảng tương đương mục tiêu sử dụng**, không hứa rằng Java callback và XML element có tập thuộc tính hoặc thời điểm xử lý giống hệt nhau trên mọi phiên bản Spring.

### Callback chỉ hiệu lực trong MVC infrastructure

Khi có @EnableWebMvc, **DelegatingWebMvcConfiguration** tìm các WebMvcConfigurer beans. Trong Spring Boot MVC, auto-configuration cũng tích hợp các WebMvcConfigurer để thu thập phần bổ sung. Nếu chỉ tạo interface nhưng không có MVC infrastructure thực sự, sẽ không có handler/service web để sử dụng cấu hình.

**Có thể có nhiều WebMvcConfigurer beans.** Các đóng góp thường được Spring phối hợp, nhưng nếu hai bean cùng thay đổi một cấu hình có thứ tự/ưu tiên, cần kiểm tra thứ tự và xung đột. Đừng chia cấu hình tùy tiện rồi mặc định kết quả luôn giống nhau.

## 5. Ví dụ Spring Boot MVC hoàn chỉnh: tùy chỉnh mà giữ mặc định Boot

Đây là ví dụ **chạy được** với Java 17+, Maven và ứng dụng Spring Boot có dependency **Spring Web**. Tạo một dự án **MVC** từ Spring Initializr, chọn Java, Maven, Spring Web. Với Boot 3.x, starter thông dụng là **spring-boot-starter-web**; với Boot 4.x, starter MVC là **spring-boot-starter-webmvc**. Cứ để Initializr sinh pom.xml tương ứng với phiên bản đã chọn.

Sử dụng package **example.demo** cho tất cả file Java (hoặc đổi nhất quán package trong ví dụ cho trùng dự án), đảm bảo lớp @SpringBootApplication nằm ở package gốc bao trùm controller/config. Ví dụ không cần database, template engine hay Spring Security.

Cấu trúc:

~~~text
src/main/
  java/example/demo/
    DemoApplication.java
    DemoController.java
    MarkerInterceptor.java
    WebConfig.java
  resources/
    manual/
      hello.txt
~~~

### 5.1. Application entry point

**src/main/java/example/demo/DemoApplication.java**

~~~java
package example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
~~~

**Giải thích:** SpringApplication tạo ApplicationContext, Boot kích hoạt các auto-configuration phù hợp với dependency MVC và đăng ký embedded HTTP server. Không cần đặt @EnableWebMvc vào đây.

### 5.2. Controller để quan sát kết quả

**src/main/java/example/demo/DemoController.java**

~~~java
package example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/greet")
    public Message greet(
            @RequestParam(defaultValue = "Lan") String name) {
        return new Message("Xin chào " + name);
    }

    public record Message(String message) {
    }
}
~~~

**Giải thích:** @RestController kết hợp @Controller và @ResponseBody, record Message được Spring/Jackson ghi thành JSON. Đây là **HTTP controller**, không phải phương thức cấu hình MVC.

### 5.3. Interceptor có hành vi quan sát được

**src/main/java/example/demo/MarkerInterceptor.java**

~~~java
package example.demo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class MarkerInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        response.setHeader("X-Mvc-Config", "applied");
        return true;
    }
}
~~~

**Giải thích:** preHandle trả **true** để request đi tiếp tới handler. Header X-Mvc-Config chỉ giúp kiểm chứng interceptor đã chạy; trong ứng dụng thực tế có thể thay bằng đo thời gian, correlation/tracing hay logic cross-cutting phù hợp. **Đừng dùng MVC interceptor làm lớp bảo vệ phân quyền chính**; cân nhắc Spring Security/Servlet filter chain.

### 5.4. WebMvcConfigurer thêm bốn loại cấu hình

**src/main/java/example/demo/WebConfig.java**

~~~java
package example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MarkerInterceptor())
                .addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/docs/**")
                .addResourceLocations("classpath:/manual/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET")
                .allowedHeaders("Accept", "Content-Type");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController(
                "/welcome", "/api/greet");
    }
}
~~~

**Giải thích theo callback:**

- **addInterceptors:** chỉ các URL /api/** đi qua MarkerInterceptor; /docs/hello.txt không nhận header X-Mvc-Config do pattern không khớp.
- **addResourceHandlers:** URI /docs/hello.txt đọc resource **classpath:/manual/hello.txt**; URL mapping và resource location là hai khái niệm khác nhau.
- **addCorsMappings:** chỉ cho browser origin **http://localhost:5173** truy cập /api/** bằng các method phù hợp. Ví dụ không bật credentials.
- **addViewControllers:** /welcome trả về redirect tới /api/greet mà không cần một controller method riêng.
- **Không có @EnableWebMvc.** Boot vẫn là bên sở hữu MVC auto-configuration của ứng dụng mẫu.

### 5.5. Resource để thử phục vụ file

**src/main/resources/manual/hello.txt** chứa chính xác:

~~~text
Tai nguyen tinh tu WebMvcConfigurer
~~~

### 5.6. Khởi động và kiểm chứng

Tại thư mục gốc của dự án Spring Initializr (có pom.xml và Maven wrapper), chạy:

~~~bash
# macOS / Linux
./mvnw spring-boot:run
~~~

Trên Windows, dùng lệnh tương ứng:

~~~powershell
.\mvnw.cmd spring-boot:run
~~~

Ở terminal khác, gọi:

~~~bash
curl -i "http://localhost:8080/api/greet?name=An"
~~~

Response dự kiến có status 200, header **X-Mvc-Config: applied** và JSON body:

~~~json
{"message":"Xin chào An"}
~~~

Một số HTTP response headers khác và thứ tự có thể khác nhau theo server/Boot.

Kiểm tra static resource:

~~~bash
curl -i "http://localhost:8080/docs/hello.txt"
~~~

Body dự kiến:

~~~text
Tai nguyen tinh tu WebMvcConfigurer
~~~

Kiểm tra redirect:

~~~bash
curl -i "http://localhost:8080/welcome"
~~~

Status dự kiến thuộc nhóm **3xx**, cụ thể trong cấu hình redirect mặc định thường là **302**, và Location chỉ tới /api/greet (có thể được server biểu diễn thành URL tuyệt đối). Thêm **-L** để curl theo redirect nếu muốn thấy JSON cuối.

Kiểm tra CORS từ origin được phép:

~~~bash
curl -i \
  -H "Origin: http://localhost:5173" \
  "http://localhost:8080/api/greet"
~~~

Kỳ vọng response có **Access-Control-Allow-Origin: http://localhost:5173**. Với origin không được cho phép, browser không được trao quyền đọc cross-origin response theo policy này. **curl không thực thi chính sách CORS của browser**, nên curl vẫn có thể hiển thị response khi gọi trực tiếp; không dùng khả năng curl gửi request để kết luận CORS đã cho phép browser.

Mẫu chạy này chứng minh rằng một WebMvcConfigurer có thể bổ sung interceptor, static mapping, CORS và redirect trong ứng dụng Boot MVC **mà không phải chiếm toàn quyền thiết lập MVC**.

## 6. Spring MVC thuần với Java Config: cần @EnableWebMvc và bootstrap Servlet

Đối với **Spring Framework MVC không dùng Spring Boot**, thông thường cần bật MVC infrastructure và đăng ký DispatcherServlet với servlet container.

Java config ví dụ:

~~~java
package example.plainmvc;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
@ComponentScan("example.plainmvc")
public class WebConfig implements WebMvcConfigurer {
    // Có thể override addInterceptors(), addResourceHandlers()...
}
~~~

**Đây là đoạn cấu hình cho một ứng dụng Servlet khác, không phải thay thế file WebConfig của ứng dụng Boot ở phần trước.** Lớp @ComponentScan tìm controller/services trong package nêu ra. @EnableWebMvc bật MVC infrastructure và tập hợp các WebMvcConfigurer beans.

Có thể đăng ký DispatcherServlet bằng Java initializer:

~~~java
package example.plainmvc;

import org.springframework.web.servlet.support
        .AbstractAnnotationConfigDispatcherServletInitializer;

public class WebAppInitializer
        extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return null;
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[] { WebConfig.class };
    }

    @Override
    protected String[] getServletMappings() {
        return new String[] { "/" };
    }
}
~~~

**Phụ thuộc môi trường:** cần đóng gói ứng dụng theo mô hình servlet web app, cung cấp **spring-webmvc**, các dependency cần dùng và container hỗ trợ Jakarta Servlet tương thích với thế hệ Spring đang chạy. Với Spring Framework 6, dùng **jakarta.servlet.***, không dùng **javax.servlet.*** của thế hệ cũ. Đây là bộ khung bootstrap thực tế nhưng **chưa phải project WAR độc lập đầy đủ**: còn cần cấu hình build, controller và triển khai đến container. Không chạy trực tiếp bằng javac/java vì chưa có dependency/classpath và servlet runtime.

### Vai trò của initializer

- **getRootConfigClasses():** cấu hình context gốc (nếu cần). Ví dụ trả null để tập trung vào một web context.
- **getServletConfigClasses():** cấu hình của **DispatcherServlet WebApplicationContext**, chứa MVC beans/controller.
- **getServletMappings():** ánh xạ servlet tới "/", phục vụ controller theo URL.
- Container phát hiện WebApplicationInitializer qua cơ chế Servlet startup tương ứng và đăng ký DispatcherServlet.

**Vị trí context rất quan trọng:** MVC infrastructure và controller thường thuộc context của DispatcherServlet. Root ApplicationContext có thể chứa service/repository dùng chung; bean ở child context thấy bean của parent, còn parent không tự nhìn thấy bean của child. Đặt @EnableWebMvc vào context không đúng có thể khiến DispatcherServlet không thấy hạ tầng mong đợi hoặc tạo hai bản cấu hình khó debug.

## 7. Cấu hình XML tương đương: mvc:annotation-driven và web.xml

Trong ứng dụng Spring MVC dùng XML, vai trò bật annotation MVC tương ứng với @EnableWebMvc là:

~~~xml
<mvc:annotation-driven/>
~~~

Để XML có nghĩa và chạy được khi được nạp, cần namespace beans, mvc, context và cấu hình **DispatcherServlet**. Ví dụ **WEB-INF/spring-mvc.xml** dưới đây thể hiện cấu hình MVC hoàn chỉnh về mặt XML; controller và lớp interceptor được giả định có sẵn trong classpath của một dự án Servlet.

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:mvc="http://www.springframework.org/schema/mvc"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/mvc
           https://www.springframework.org/schema/mvc/spring-mvc.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <!-- Phát hiện @Controller, @Service và @Component. -->
    <context:component-scan base-package="example.plainmvc"/>

    <!-- Bật MVC annotation infrastructure. -->
    <mvc:annotation-driven/>

    <!-- Tương ứng addInterceptors(...). -->
    <mvc:interceptors>
        <mvc:interceptor>
            <mvc:mapping path="/api/**"/>
            <bean class="example.plainmvc.MarkerInterceptor"/>
        </mvc:interceptor>
    </mvc:interceptors>

    <!-- Tương ứng addResourceHandlers(...). -->
    <mvc:resources mapping="/docs/**"
                   location="classpath:/manual/"/>

    <!-- Tương ứng addViewControllers(...). -->
    <mvc:view-controller path="/welcome"
                         view-name="redirect:/api/greet"/>

    <!-- Tương ứng addCorsMappings(...). -->
    <mvc:cors>
        <mvc:mapping path="/api/**"
                     allowed-origins="http://localhost:5173"
                     allowed-methods="GET"
                     allowed-headers="Accept, Content-Type"/>
    </mvc:cors>
</beans>
~~~

Các class **example.plainmvc.MarkerInterceptor** và các controller cần được tạo tương ứng trong project XML; không nên lấy nguyên class package **example.demo** từ dự án Boot ở phần 5 mà bỏ qua khác biệt package. Chẳng hạn có thể viết MarkerInterceptor cùng mã ở ví dụ Boot rồi đổi package thành **example.plainmvc**. XML này là **bản đối chiếu cấu hình**, không phải ứng dụng runnable độc lập nếu thiếu các lớp Java và container.

Nếu chưa có Java initializer, một ứng dụng Servlet có thể khai báo servlet bằng **WEB-INF/web.xml**. Mẫu web.xml cho Servlet 6.0/Jakarta API:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
             https://jakarta.ee/xml/ns/jakartaee
             https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">

    <servlet>
        <servlet-name>dispatcher</servlet-name>
        <servlet-class>
            org.springframework.web.servlet.DispatcherServlet
        </servlet-class>
        <init-param>
            <param-name>contextConfigLocation</param-name>
            <param-value>/WEB-INF/spring-mvc.xml</param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>
    </servlet>

    <servlet-mapping>
        <servlet-name>dispatcher</servlet-name>
        <url-pattern>/</url-pattern>
    </servlet-mapping>
</web-app>
~~~

**Giải thích:**

- Servlet container tạo **DispatcherServlet** cho mapping "/".
- DispatcherServlet đọc contextConfigLocation và khởi tạo **WebApplicationContext** từ spring-mvc.xml.
- Phần tử **mvc:annotation-driven** bật các bean cần cho @RequestMapping, @RequestBody, @ResponseBody, converter/validation phù hợp.
- **context:component-scan** tìm controller và các bean Java.
- XML cấu hình interceptors/resources/CORS/view-controller được áp dụng trong MVC context ấy.

Nếu dùng **AbstractAnnotationConfigDispatcherServletInitializer** (Java) thì không cần đồng thời cấu hình **cùng một DispatcherServlet** lại qua web.xml; cấu hình cả hai không có chủ ý có thể dẫn đến servlet/context lặp, mapping đụng nhau hoặc ứng dụng khởi động sai.

## 8. Đối chiếu trực tiếp Java Config và XML theo hành vi

### 8.1. Bật annotation-based MVC

Java:

~~~java
@Configuration
@EnableWebMvc
public class WebConfig {
}
~~~

XML:

~~~xml
<mvc:annotation-driven/>
~~~

Cả hai bật các thành phần MVC cơ bản trong bối cảnh Spring MVC thuần. **Nhưng** @Configuration/@EnableWebMvc tự chúng không khai báo component scanning; XML mvc:annotation-driven cũng không. Muốn controller được tìm thấy phải dùng @ComponentScan, context:component-scan hoặc đăng ký bean bằng cách khác.

### 8.2. Đăng ký interceptor

Java, trong lớp WebMvcConfigurer:

~~~java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new MarkerInterceptor())
            .addPathPatterns("/api/**");
}
~~~

XML:

~~~xml
<mvc:interceptors>
    <mvc:interceptor>
        <mvc:mapping path="/api/**"/>
        <bean class="example.plainmvc.MarkerInterceptor"/>
    </mvc:interceptor>
</mvc:interceptors>
~~~

Hai đoạn **tương đương về mục tiêu** khi class interceptor có cùng logic. Tuy nhiên có khác biệt kỹ thuật: XML mvc:interceptors khai báo **MappedInterceptor** beans có thể được phát hiện bởi các HandlerMapping khác trong context, còn callback Java đăng ký qua các HandlerMapping do MVC Java config quản lý. Khi tích hợp framework bổ sung, cần kiểm tra phạm vi áp dụng thay vì cho rằng luôn giống hệt.

**Không nhầm HandlerInterceptor với Servlet Filter**: Filter chạy trong servlet container filter chain, có thể bao phủ URL trước DispatcherServlet; HandlerInterceptor gắn với các handler MVC sau HandlerMapping. An ninh phân quyền nên giao cho lớp xử lý phù hợp, thường Spring Security/filter chain.

### 8.3. Phục vụ tài nguyên tĩnh

Java:

~~~java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/docs/**")
            .addResourceLocations("classpath:/manual/");
}
~~~

XML:

~~~xml
<mvc:resources mapping="/docs/**"
               location="classpath:/manual/"/>
~~~

Nếu file ở **src/main/resources/manual/hello.txt** thì request **/docs/hello.txt** trả file ấy khi resource được đóng gói lên classpath. Không lẫn **đường dẫn URL** /docs với **địa chỉ resource** classpath:/manual/.

Spring Boot MVC có các resource locations và welcome page mặc định riêng. Thêm @EnableWebMvc có thể thay đổi hành vi serving static content; trong Boot hãy ưu tiên dùng **WebMvcConfigurer** hoặc Boot properties tương ứng khi chỉ cần điều chỉnh.

### 8.4. CORS toàn cục cho MVC

Java:

~~~java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET")
            .allowedHeaders("Accept", "Content-Type");
}
~~~

XML:

~~~xml
<mvc:cors>
    <mvc:mapping path="/api/**"
                 allowed-origins="http://localhost:5173"
                 allowed-methods="GET"
                 allowed-headers="Accept, Content-Type"/>
</mvc:cors>
~~~

Nếu browser gọi từ một origin khác, CORS policy có thể khiến trình duyệt chặn truy cập response hoặc preflight bị từ chối. Không được dùng CORS để xác thực người dùng. Nếu dùng cookies hoặc credentials, phải cấu hình cụ thể origin được tin cậy, credentials và bảo vệ CSRF/authorization theo đúng trường hợp; không kết hợp credentials với wildcard origin "*" tùy tiện.

### 8.5. View controller/redirect

Java:

~~~java
@Override
public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController(
            "/welcome", "/api/greet");
}
~~~

XML theo mục tiêu tương tự:

~~~xml
<mvc:view-controller path="/welcome"
                     view-name="redirect:/api/greet"/>
~~~

Cả hai cho phép đường dẫn /welcome trả redirect mà không viết business controller. Không nên định nghĩa một annotated controller và view-controller cùng tranh quyền sở hữu một route, vì mapping ưu tiên và status lỗi có thể không như người viết mong đợi.

### 8.6. Formatter/Converter và validation

Java:

~~~java
@Override
public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(String.class, Code.class,
            value -> new Code(value.trim()));
}
~~~

Đây là **method minh họa** trong WebMvcConfigurer, yêu cầu có kiểu **Code** được định nghĩa và import FormatterRegistry phù hợp, không phải đoạn Java độc lập. Nếu muốn tự chạy, có thể khai báo:

~~~java
public record Code(String value) {
}
~~~

Khi phương thức controller nhận @RequestParam Code, formatter/converter có thể chuyển text tham số thành đối tượng Code. Đây là **conversion khi binding HTTP tham số**, khác với JSON deserialization của @RequestBody.

XML dùng conversion service tùy biến gắn vào annotation-driven:

~~~xml
<mvc:annotation-driven conversion-service="conversionService"/>

<bean id="conversionService"
      class="org.springframework.format.support.FormattingConversionServiceFactoryBean">
    <property name="converters">
        <set>
            <bean class="example.plainmvc.StringToCodeConverter"/>
        </set>
    </property>
</bean>
~~~

**Yêu cầu bổ sung:** lớp StringToCodeConverter phải được cài đặt là Spring Converter<String, Code> và có constructor thích hợp; XML trên là ví dụ tích hợp, chưa đủ để chạy độc lập. Cấu hình validator có thể khai báo với thuộc tính validator của mvc:annotation-driven tùy schema; hỗ trợ Bean Validation còn phụ thuộc provider/dependency và configuration.

### 8.7. HTTP message converters và content negotiation

Trong Java config **Spring Framework 6.x**, phương thức:

~~~java
@Override
public void extendMessageConverters(
        List<HttpMessageConverter<?>> converters) {
    // Bổ sung hoặc tinh chỉnh danh sách converter đã thiết lập.
}
~~~

là một hook điều chỉnh. **configureMessageConverters(List...)** có semantics khác: việc tự cung cấp converter có thể làm mất đăng ký mặc định trong Spring Framework thuần nếu bạn không chủ động bổ sung lại.

Trong XML có thể cấu hình message converters trong **mvc:annotation-driven**:

~~~xml
<mvc:annotation-driven>
    <mvc:message-converters register-defaults="true">
        <!-- Bean converter tùy chỉnh có thể thêm ở đây. -->
    </mvc:message-converters>
</mvc:annotation-driven>
~~~

Đây là **đoạn cấu hình minh họa**; không phải yêu cầu bắt buộc. Hãy chỉ tạo converter bean khi thực sự cần. Trong Boot, cơ chế **HttpMessageConverters** và việc đăng ký converter bean của Boot thường phù hợp hơn việc tự thay thế toàn bộ danh sách.

**Lưu ý phiên bản:** API cấu hình message converters đang thay đổi giữa Spring Framework 6.x và 7.x; ở Spring 7, các hook nhận **List<HttpMessageConverter<?>>** đã bị deprecated để ưu tiên builder-based API. Khi nâng cấp cần đọc Javadoc cùng phiên bản thay vì áp dụng nguyên xi ví dụ 6.x.

XML namespace MVC tiếp tục là cách đọc/duy trì nhiều codebase cũ, nhưng **từ Spring Framework 7.0, hỗ trợ XML MVC namespace đã được đánh dấu deprecated**; tài liệu Spring cũng lưu ý XML config không tiếp tục được cập nhật để bắt kịp mọi tùy chọn Java config mới. Với dự án mới, ưu tiên Java config; khi bảo trì XML, cân nhắc lộ trình di chuyển từng phần có kiểm chứng.

## 9. Vì sao @EnableWebMvc có thể gây lỗi trong Spring Boot?

Giả sử bạn bắt đầu với ứng dụng Boot MVC có resource:

~~~text
src/main/resources/static/css/site.css
~~~

Khi dùng auto-configuration của Boot, URL **/css/site.css** thường được phục vụ theo resource mapping mặc định. Sau đó một nhà phát triển thêm:

~~~java
@Configuration
@EnableWebMvc
public class CustomWebConfig implements WebMvcConfigurer {
    // Chỉ muốn đăng ký một interceptor.
}
~~~

Ứng dụng có thể **vẫn khởi động** và controller JSON vẫn chạy, nhưng một phần cấu hình MVC do Boot cung cấp (bao gồm static resource mapping hoặc các customizations liên quan) có thể **không còn hoạt động như trước**. Vì vậy không nên coi việc bật @EnableWebMvc như “bật thêm một tiện ích” nhỏ.

### Cách sửa khi chỉ muốn thêm interceptor

~~~java
@Configuration
public class CustomWebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MarkerInterceptor());
    }
}
~~~

Đây là **mã minh họa** cần import và định nghĩa MarkerInterceptor như ví dụ ở phần 5. Điểm thay đổi cốt lõi là **bỏ @EnableWebMvc**, giữ cấu hình MVC auto của Boot rồi áp dụng callback.

**@EnableWebMvc có phải luôn là lỗi?** Không. Khi ứng dụng Boot có yêu cầu đặc biệt và đội phát triển **cố ý tự quản lý toàn bộ Spring MVC**, có thể dùng @EnableWebMvc, nhưng phải hiểu các phần do Boot không còn cung cấp tự động và khai báo/kiểm thử lại những gì cần thiết.

### extends WebMvcConfigurationSupport cũng là lựa chọn toàn quyền

~~~java
@Configuration
public class FullControlMvcConfig
        extends WebMvcConfigurationSupport {
    // Có quyền override các hooks và beans hạ tầng cấp thấp.
}
~~~

Đây là **đoạn minh họa không hoàn chỉnh**. Việc có bean WebMvcConfigurationSupport có thể khiến điều kiện auto-configuration MVC của Boot không còn đáp ứng. Không dùng cách này chỉ để thêm CORS hoặc formatter.

Nếu cần tùy biến sâu **RequestMappingHandlerMapping**, **RequestMappingHandlerAdapter** hoặc **ExceptionHandlerExceptionResolver** nhưng vẫn giữ MVC customizations của Boot, xem cơ chế **WebMvcRegistrations** của Spring Boot. Đây là nhánh nâng cao, cần kiểm chứng toàn bộ kết hợp framework/Boot cùng phiên bản.

## 10. So sánh đường đi cấu hình ở Boot và Spring MVC thuần

~~~text
A. Spring Boot + Spring MVC
@SpringBootApplication
      |
SpringApplication + AutoConfiguration
      |
WebMvcAutoConfiguration, DispatcherServletAutoConfiguration
      |
WebMvcConfigurer beans bổ sung callback (KHÔNG @EnableWebMvc)
      |
DispatcherServlet -> HandlerMapping / Adapter / Converter...
      |
Controller

B. Spring Framework MVC thuần — Java
Servlet container
      |
WebApplicationInitializer đăng ký DispatcherServlet
      |
@Configuration + @EnableWebMvc
      |
DelegatingWebMvcConfiguration nhận WebMvcConfigurer beans
      |
MVC infrastructure -> Controller

C. Spring Framework MVC thuần — XML
Servlet container
      |
web.xml đăng ký DispatcherServlet và XML config location
      |
spring-mvc.xml:
  context:component-scan
  mvc:annotation-driven
  mvc:interceptors / resources / cors / view-controller...
      |
MVC infrastructure -> Controller
~~~

**A** là đường hiện đại, ít cấu hình thủ công. **B/C** cho thấy những bộ phận Boot đã tự cung cấp hoặc thay bằng auto-configuration. Khi debug, hãy xác định dự án thuộc nhánh nào trước khi chỉnh annotation/XML.

## 11. Kiểm thử cấu hình thay vì đoán hiệu lực

### Bài thử HTTP cho ví dụ Boot

Sau khi chạy ứng dụng mẫu ở phần 5:

1. GET **/api/greet?name=An** phải trả JSON thông điệp chào và header **X-Mvc-Config: applied**.
2. GET **/docs/hello.txt** phải trả nội dung file từ classpath.
3. GET **/welcome** phải redirect đến **/api/greet**.
4. Request có **Origin: http://localhost:5173** tới /api/greet phải nhận CORS header tương ứng.
5. Origin khác không được mặc nhiên cho phép truy cập cross-origin trong browser.
6. Kiểm tra behavior khi đổi/sửa cấu hình; không thử nghiệm bằng cách giữ đồng thời nhiều lớp cấu hình trùng trách nhiệm.

### Khi muốn viết test tự động

Với Spring Boot 3.x dùng **spring-boot-starter-test**, có thể kiểm tra endpoint bằng **@SpringBootTest + @AutoConfigureMockMvc** hoặc dùng các test slices phù hợp. Test MVC có hai lớp ý nghĩa:

- **Slice test @WebMvcTest:** tập trung controller và một phần MVC infrastructure; không bảo đảm tải mọi bean cấu hình tùy chỉnh của toàn ứng dụng. Nếu test cần một WebMvcConfigurer/interceptor cụ thể, phải chủ động import/thiết lập trong slice.
- **Full-context test @SpringBootTest:** phù hợp để xác nhận integration của auto-configuration, converter, handler mapping, MVC config. Khi kết hợp MockMvc, cần annotation/config tương ứng.

Một test tự động minh họa cho ứng dụng Boot ở phần 5:

~~~java
package example.demo;

import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
        .AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MvcConfigTest {

    @Autowired
    MockMvc mvc;

    @Test
    void interceptorAndControllerAreConfigured() throws Exception {
        mvc.perform(get("/api/greet").param("name", "An"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "X-Mvc-Config", "applied"))
                .andExpect(jsonPath("$.message")
                        .value("Xin chào An"));
    }
}
~~~

Đây là **lớp test độc lập bên trong project Boot 3.x của ví dụ**, đặt ở **src/test/java/example/demo/MvcConfigTest.java**, yêu cầu dependency Spring Boot Test và JUnit/Jackson/JsonPath qua starter test. Nếu dùng Boot 4.x, package test auto-configuration có thể khác; tạo test từ dependency và import đúng version tương ứng. Để chạy:

~~~bash
# macOS / Linux
./mvnw test
~~~

Trên Windows:

~~~powershell
.\mvnw.cmd test
~~~

**Kỳ vọng:** test pass nếu project khai báo đúng dependencies, package scan và các file của ví dụ. Không nên ghi log chạy thử cụ thể khi chưa thực sự chạy trong môi trường đó.

### Debug khi cấu hình không có hiệu lực

- Kiểm tra **WebConfig có thực sự là bean không**: package, component scan, @Import hoặc XML registration.
- Kiểm tra ứng dụng **Servlet MVC hay WebFlux**: WebMvcConfigurer không dùng để cấu hình WebFlux; reactive stack có **WebFluxConfigurer**.
- Kiểm tra có vô tình thêm **@EnableWebMvc** hoặc extends **WebMvcConfigurationSupport** trong Boot không.
- Đọc **auto-configuration condition report** (Boot có thể hiển thị qua startup debug/Actuator conditions tùy setup) để xác định WebMvcAutoConfiguration có chạy.
- Kiểm tra servlet **context path, servlet mapping và handler patterns** trước khi kết luận addInterceptors/addResourceHandlers không hoạt động.
- Nếu dùng XML, kiểm tra XML được **DispatcherServlet context** thực sự nạp, namespace và schema đúng, các class tham chiếu có trong classpath.
- Khi có nhiều DispatcherServlet hoặc nhiều ApplicationContexts, xác định context sở hữu controller và config.
- Kiểm tra xung đột **@CrossOrigin với global CORS**, nhiều converters, view resolvers hoặc static URL patterns.
- Kiểm tra lỗi app khởi động và exception chi tiết trước khi đổi framework version.
- Đừng dùng interceptor để bảo vệ quyền truy cập; khi security filter xử lý request trước MVC, phản hồi có thể được tạo trước khi interceptor chạy.

## 12. Lưu ý khi di chuyển XML sang Java config

Di chuyển cấu hình cần **đối chiếu hành vi**, không chỉ thay cú pháp:

1. Xác định servlet mapping, WebApplicationContext, root context và nơi đang load XML.
2. Lập danh sách những chức năng trong XML: annotation-driven, scan, interceptors, formatter, validation, converter, view resolver, resources, CORS, redirect và cấu hình nâng cao.
3. Với ứng dụng **Spring MVC thuần**, chuyển mvc:annotation-driven sang **@EnableWebMvc**, các phần tử bổ sung sang WebMvcConfigurer hoặc beans tương ứng; vẫn phải bootstrap DispatcherServlet.
4. Với ứng dụng **Spring Boot**, tận dụng MVC auto-configuration trước; chuyển các tùy biến hữu ích sang **WebMvcConfigurer không gắn @EnableWebMvc**, không bê nguyên annotation từ cấu hình thuần vào một cách máy móc.
5. Nếu XML có custom message converters, mapping rules hoặc handler adapter, so sánh các default và thử các request JSON/XML, validation/error cases sau di chuyển.
6. So sánh security/filter order, CORS preflight, static resources, cache headers, redirect URLs và view rendering.
7. Có thể tồn tại cấu hình XML và Java **cùng lúc**, nhưng tránh đăng ký trùng servlet, component scan, handler mappings, converter/interceptor vì kết quả phụ thuộc ownership và precedence.
8. Giữ phiên bản nhất quán: Spring 5/javax.servlet và Spring 6+/jakarta.servlet là hai thế hệ API khác nhau; nâng cấp cùng thư viện/container thích hợp.

Một ví dụ bảng kiểm cấu hình:

| Trước (XML) | Sau (Java/Boot) | Test cần giữ |
|---|---|---|
| mvc:annotation-driven | @EnableWebMvc (thuần); Boot MVC auto-config | Annotation controller, binding, JSON |
| context:component-scan | @ComponentScan hoặc @SpringBootApplication scan | Controller/service được đăng ký |
| mvc:interceptors | addInterceptors | Header/logging và include/exclude URL |
| mvc:resources | addResourceHandlers hoặc Boot static mapping | File có thể GET, content type/cache đúng |
| mvc:view-controller | addViewControllers | Redirect/forward và status |
| mvc:cors | addCorsMappings / @CrossOrigin | Allowed Origin, preflight và credentials |
| conversion-service | addFormatters / Converter beans | Query/form binding và validation |
| ViewResolver beans | configureViewResolvers / Boot view integration | HTML view được render |
| Custom HttpMessageConverters | Custom converter/Boot HttpMessageConverters | JSON/XML, Accept, error bodies |

## 13. Những hiểu lầm và lỗi phổ biến

**“@EnableWebMvc để bật mọi tính năng web trong Spring Boot.”** Không đúng. Boot MVC thường đã bật chức năng đó; @EnableWebMvc có thể làm mất các customizations tự động của Boot.

**“WebMvcConfigurer thay thế DispatcherServlet.”** Không đúng. Configurer chỉ mô tả callbacks cho MVC infrastructure. Servlet vẫn cần được Boot, initializer hoặc web.xml đăng ký.

**“Có @EnableWebMvc là controller sẽ tự được scan.”** Không đúng. Phải có component scan hoặc bean registration thích hợp.

**“@EnableWebMvc và mvc:annotation-driven là hai hệ hoàn toàn không liên quan.”** Không đúng. Cả hai bật hỗ trợ annotation-based MVC theo hai kiểu cấu hình; khác biệt ở cách khai báo và phạm vi tính năng/schema cụ thể.

**“XML mvc:annotation-driven đăng ký DispatcherServlet.”** Không đúng. Servlet registration là một bước riêng.

**“Thêm một converter bằng configureMessageConverters không ảnh hưởng defaults.”** Có thể sai trong Java MVC configuration thế hệ 6.x; phải phân biệt thay thế với mở rộng. Đọc Javadoc đúng version. Trong Boot nên ưu tiên cơ chế message converter integration của Boot nếu muốn giữ mặc định.

**“WebMvcConfigurer áp dụng cho WebFlux vì cả hai đều dùng Spring Web.”** Sai. WebFlux dùng WebFluxConfigurer và reactive codecs; MVC dùng Servlet stack.

**“Interceptors là nơi lý tưởng để thực hiện authorization.”** Sai trong nhiều kiến trúc; MVC interceptors có thể không khớp toàn bộ đường đi request hoặc security requirements. Dùng Spring Security/filter chain phù hợp.

**“CORS giúp bảo vệ API khỏi client ngoài.”** Không đúng. CORS là chính sách browser đọc cross-origin response; client không phải browser có thể gửi HTTP request bình thường. Authentication/authorization vẫn cần thiết.

**“Có thể chuyển XML sang Java theo tìm-thay chuỗi.”** Không đáng tin. Phải xét scope của context, thứ tự bean/configurer, converters, servlet mapping, Boot defaults và testing.

**“Spring MVC XML namespace luôn được phát triển ngang bằng Java config ở mọi bản tương lai.”** Không đúng. Kể từ Spring Framework 7.0, MVC XML namespace deprecated, và các tính năng mới không nhất thiết được phản ánh đầy đủ ở XML.

## 14. Tự kiểm tra

1. **Trong Spring Boot, chỉ muốn thêm interceptor, dùng gì?** @Configuration implements WebMvcConfigurer; thông thường không dùng @EnableWebMvc.
2. **Trong Spring MVC thuần, @EnableWebMvc có tự mở cổng HTTP không?** Không; cần DispatcherServlet registration và container.
3. **XML nào gần với @EnableWebMvc nhất?** mvc:annotation-driven, đặt trong XML thuộc context MVC phù hợp.
4. **XML nào tương ứng addResourceHandlers?** mvc:resources.
5. **Vì sao dự án Boot bị 404 cho file static sau khi thêm @EnableWebMvc?** Có thể Boot MVC auto-configuration bị thay bằng cấu hình do ứng dụng tự quản lý, dẫn đến thiếu resource mapping cần thiết.
6. **WebMvcConfigurer có cần @Component/@Configuration không?** Bản triển khai phải trở thành bean Spring; annotation hoặc explicit registration là cách thông dụng.
7. **Đang dùng Spring WebFlux có được dùng WebMvcConfigurer không?** Không để cấu hình reactive stack; xem WebFluxConfigurer.
8. **Hai DispatcherServlet có mặc định chung một WebApplicationContext không?** Không; mỗi servlet có thể có child context riêng, cần hiểu bean visibility.
9. **XML + Java config có thể cùng tồn tại không?** Có, nhưng phải quản lý rõ ownership và tránh khai báo trùng.
10. **Khác biệt giữa configureMessageConverters và extendMessageConverters trong Spring 6.x?** Một bên cấu hình danh sách có thể làm mất defaults; một bên cho phép mở rộng/điều chỉnh danh sách đã cấu hình.
11. **Khi nào cân nhắc WebMvcConfigurationSupport/WebMvcRegistrations?** Khi có nhu cầu cấu hình hạ tầng cấp thấp rõ ràng; kiểm tra ảnh hưởng đến Boot auto-configuration.
12. **Khi di chuyển XML sang Boot, có nên chuyển nguyên mvc:annotation-driven thành @EnableWebMvc?** Thường không; dùng Boot MVC auto-config và chuyển các customizations cần giữ sang WebMvcConfigurer.

## 15. Tài liệu chính thức

- [Spring Framework — Enable MVC Configuration](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/enable.html): @EnableWebMvc, mvc:annotation-driven, lưu ý với Boot và deprecation XML ở Spring 7.
- [Spring Framework — MVC Config API](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/customize.html): WebMvcConfigurer.
- [Spring Framework — Interceptors](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html): mapping interceptor và khác biệt Java/XML.
- [Spring Framework — Static Resources](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/static-resources.html): resource handlers.
- [Spring Framework — View Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/view-controller.html): view controller và redirect.
- [Spring Framework — CORS](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html): cors mappings và browser policy.
- [Spring Framework — Message Converters](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/message-converters.html): converter customization, lưu ý version API.
- [Spring Framework — Advanced Java Config](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/advanced-java.html): DelegatingWebMvcConfiguration và WebMvcConfigurationSupport.
- [Spring Boot 3.3 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.3/reference/web/servlet.html): MVC auto-configuration, WebMvcConfigurer không @EnableWebMvc và tùy biến sâu.
- [Spring Framework — Web MVC Reference](https://docs.spring.io/spring-framework/reference/web/webmvc.html): toàn bộ vòng đời và kiến trúc Spring MVC.

**Ghi nhớ:** **@EnableWebMvc bật MVC Java infrastructure**, **WebMvcConfigurer thêm các callback tùy chỉnh**, **mvc:annotation-driven và các mvc:* khác là tuyến XML**, còn **Spring Boot thường tự cấu hình MVC và chỉ cần WebMvcConfigurer để mở rộng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-bootstrap-006">Phân biệt cấu hình Spring MVC trực tiếp với tự động cấu hình của Spring Boot</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt cấu hình Spring MVC trực tiếp với tự động cấu hình của Spring Boot

Cùng một endpoint Spring MVC có thể chạy trong ứng dụng **Spring Framework cấu hình trực tiếp** hoặc trong ứng dụng **Spring Boot có tự động cấu hình (auto-configuration)**. Cả hai vẫn dùng các thành phần cốt lõi của Spring MVC như `DispatcherServlet`, `HandlerMapping`, `HandlerAdapter`, `HttpMessageConverter` và controller. Điểm khác nhau chính là **ai đăng ký, kết nối và cung cấp cấu hình mặc định** cho các thành phần đó.

Bài học này thuộc phần *Khởi tạo ứng dụng*: mục tiêu là hiểu trình tự khởi tạo, trách nhiệm cấu hình, ranh giới giữa Spring Framework và Spring Boot, cách giữ các mặc định hữu ích của Boot khi mở rộng Spring MVC, và khi nào nên tự nắm quyền cấu hình.

## 1. Phân biệt ba lớp trách nhiệm

Trước tiên tách ba lớp dễ bị đánh đồng:

1. **Spring Framework IoC container** (`ApplicationContext`, `@Configuration`, `@Bean`, `@ComponentScan`) tạo, quản lý và nối các bean. Nó không tự đồng nghĩa với một HTTP server.
2. **Spring Web MVC** là framework web dựa trên Servlet API. Nó cung cấp `DispatcherServlet` và hệ thống ánh xạ request tới controller, chuyển đổi dữ liệu, xử lý lỗi, view, v.v. Để tiếp nhận request HTTP, `DispatcherServlet` cần được đăng ký trong một Servlet container.
3. **Spring Boot** hỗ trợ khởi động ứng dụng và áp dụng **cấu hình có điều kiện** dựa trên classpath, loại ứng dụng, các bean đã tồn tại và property. Với ứng dụng Servlet có thư viện phù hợp, Boot có thể cấu hình web server nhúng, đăng ký `DispatcherServlet` và bổ sung các mặc định Spring MVC.

```text
                       SPRING MVC CỐT LÕI
                          DispatcherServlet
                           │            │
                        Mapping       Adapter
                           │            │
                         Controller → Service
                           │
                         Response

       SPRING FRAMEWORK TRỰC TIẾP                  SPRING BOOT
       ──────────────────────────                  ───────────
       Bạn đăng ký Servlet                         SpringApplication.run(...)
       Bạn bật cấu hình MVC                         Boot quyết định cấu hình áp dụng
       Bạn chọn và cấu hình container              Dùng embedded server khi phù hợp
       Bạn bổ sung tích hợp theo nhu cầu            Tự cung cấp nhiều mặc định
```

**Không có hai “loại Spring MVC” khác nhau**: Boot không thay `DispatcherServlet` bằng một bộ điều phối riêng. Controller viết bằng `@RestController` và `@GetMapping` vẫn sử dụng API Spring MVC. Spring Boot có thể tạo ứng dụng chạy dạng JAR với server nhúng, nhưng cũng có thể triển khai kiểu WAR trong môi trường thích hợp. Ứng dụng Spring Framework thuần cũng có thể dùng server nhúng nếu người phát triển tự cấu hình; ví dụ WAR bên dưới được chọn để làm rõ trách nhiệm đăng ký Servlet.

## 2. Cấu hình Spring MVC trực tiếp: tự lắp ghép tầng web

### 2.1. Việc bắt buộc phải có

Một ứng dụng Spring MVC truyền thống, dùng Servlet container bên ngoài, cần trả lời:

- **Servlet container nào** sẽ tiếp nhận kết nối HTTP? Ví dụ Apache Tomcat hỗ trợ phiên bản Jakarta Servlet phù hợp.
- **`DispatcherServlet` được tạo và map vào URL nào?** Ví dụ `/` hoặc `/api/*`.
- **`WebApplicationContext` chứa những bean nào?** Bao gồm controller/service và cấu hình MVC.
- **Ai đăng ký cấu hình hạ tầng MVC?** Thường dùng `@EnableWebMvc`, tương đương ý tưởng `<mvc:annotation-driven/>` của cấu hình XML.
- **Các tính năng ngoài mặc định cần gì?** JSON converter, resource handler, view resolver, multipart, exception advice, filter, interceptor, CORS... Tùy dependency và cấu hình, mỗi khả năng có điều kiện khác nhau.

`@EnableWebMvc` giúp đăng ký nhiều bean hạ tầng MVC như request mapping, argument resolver và message converter thích hợp với dependency. Nó **không tự khởi chạy Tomcat** và **không thay thế việc đăng ký `DispatcherServlet`**.

Có thể đăng ký `DispatcherServlet` bằng `web.xml`, `WebApplicationInitializer` hoặc lớp tiện ích `AbstractAnnotationConfigDispatcherServletInitializer`. Một root context và một Servlet context có thể được dùng theo kiến trúc phân tầng; không bắt buộc phải tạo cả hai khi ứng dụng nhỏ.

### 2.2. Ví dụ A — Spring MVC trực tiếp, đóng gói WAR

Ví dụ cố định nền tảng để tránh lẫn API: **Java 17+, Spring Framework 6.2.x, Jakarta Servlet 6.0 và Tomcat 10.1**. Tạo dự án Gradle riêng tên `manual-mvc` (có Gradle wrapper); file `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'war'
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
    implementation 'org.springframework:spring-webmvc:6.2.19'
    compileOnly 'jakarta.servlet:jakarta.servlet-api:6.0.0'
}
```

`spring-webmvc` chứa hạ tầng Spring MVC; Servlet API chỉ cần khi biên dịch vì Tomcat sẽ cung cấp nó lúc chạy. Ví dụ trả chuỗi thuần để **không đòi hỏi thư viện JSON**. Nếu muốn `record` được serialize thành JSON, cần một converter/dependency thích hợp và kiểm tra cấu hình của MVC context.

File `src/main/java/demo/manual/WebConfig.java`:

```java
package demo.manual;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
@ComponentScan(basePackageClasses = WebConfig.class)
public class WebConfig {
}
```

Giải thích: `@Configuration` khai báo lớp cấu hình cho Spring container; `@ComponentScan` tìm controller và các thành phần nằm trong package `demo.manual` (kể cả package con); `@EnableWebMvc` nhập cấu hình hạ tầng MVC. Ba nhiệm vụ này không tương đương nhau.

File `src/main/java/demo/manual/AppInitializer.java`:

```java
package demo.manual;

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
        return new Class<?>[] { WebConfig.class };
    }

    @Override
    protected String[] getServletMappings() {
        return new String[] { "/" };
    }
}
```

Lớp này đăng ký `DispatcherServlet`, khởi tạo Servlet web application context với `WebConfig` và map Servlet tại `/`. Không có root context riêng trong ví dụ: controller và MVC infrastructure cùng nằm trong context của Servlet. **Mapping `/` là mapping trong web application**, không tự xóa context path khi đóng gói WAR.

File `src/main/java/demo/manual/GreetingController.java`:

```java
package demo.manual;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    @GetMapping("/greet")
    public String greet(
            @RequestParam(defaultValue = "Lan") String name) {
        return "Xin chào, " + name + "!";
    }
}
```

Đây là **ba file Java đầy đủ** trong dự án Gradle ở trên. Với wrapper đã có:

```bash
./gradlew clean war
```

Trên Windows PowerShell có thể chạy:

```powershell
.\gradlew.bat clean war
```

Triển khai file WAR từ `build/libs/` lên Tomcat 10.1 (chẳng hạn đặt tên triển khai là `manual-mvc.war`). Khởi động Tomcat và kiểm tra:

```bash
curl -i 'http://localhost:8080/manual-mvc/greet?name=Lan'
```

Khi deploy dưới context path `/manual-mvc`, response thành công có status **200 OK** và body văn bản:

```text
Xin chào, Lan!
```

Tên WAR và context path thực tế phụ thuộc cách bạn triển khai. Nếu WAR được triển khai thành root application, đường dẫn sẽ là `/greet`. Đây là ví dụ **runnable khi có Gradle/JDK/Tomcat như mô tả**, không phải `javac` đơn lẻ vì cần các dependency và Servlet container.

**Dòng khởi động trong mô hình trực tiếp:**

```text
Tomcat khởi động
  → phát hiện WebApplicationInitializer
  → AppInitializer đăng ký DispatcherServlet mapping "/"
  → WebApplicationContext được tạo
  → WebConfig kích hoạt MVC infrastructure + component scan
  → GreetingController trở thành bean/handler
  → GET /greet → DispatcherServlet → controller → String response
```

Ở đây container Servlet chủ động gọi quá trình khởi tạo web application. Đó là điểm khác với `SpringApplication.run` của Boot trong kiểu chạy JAR thông dụng.

### 2.3. Cấu hình trực tiếp không phải viết mọi thứ từ đầu

`@EnableWebMvc` cung cấp một baseline thực dụng cho Spring MVC. Bạn vẫn có thể cài `WebMvcConfigurer` để tùy chỉnh interceptor, view controller, formatter, CORS, resource handler, v.v. Ví dụ:

```java
@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/start", "/greet");
    }
}
```

Đây là **minh họa cấu hình thay thế cho `WebConfig`**, cần import đúng các kiểu của Spring và giữ `@ComponentScan` nếu controller nằm ngoài khai báo bean. Không chép đè nguyên xi ví dụ rút gọn này vào dự án trên rồi quên component scan. Với cách đăng ký thủ công, bạn chịu trách nhiệm bảo đảm mọi phần cần thiết của ứng dụng đều được cấu hình tương thích.

## 3. Spring Boot: tự động cấu hình có điều kiện

### 3.1. Boot khởi tạo ứng dụng từ đâu?

`@SpringBootApplication` gộp ba nhóm hành vi chính:

- `@SpringBootConfiguration`: cho phép lớp ứng dụng tham gia cấu hình Spring, có ý nghĩa tương ứng với `@Configuration` trong kiểu ứng dụng này.
- `@EnableAutoConfiguration`: yêu cầu Boot xem xét các auto-configuration phù hợp.
- `@ComponentScan`: tìm các `@Component`, `@Controller`, `@Service`, `@Configuration`... ở package của application class và các package con theo mặc định.

`SpringApplication.run(...)` dựng Spring application context, xác định loại ứng dụng/web environment và kích hoạt chuỗi khởi tạo tương ứng. Với dependency Servlet MVC và server nhúng phù hợp, Boot có thể tạo embedded web server, đăng ký `DispatcherServlet` và lắp phần hạ tầng MVC.

Auto-configuration **không phải** “Boot quét mọi JAR rồi tạo mọi bean bất kể bạn có gì”. Các cấu hình được chọn khi thỏa điều kiện như:

| Điều kiện/đầu vào | Ý nghĩa trong quá trình cấu hình |
| --- | --- |
| `@EnableAutoConfiguration` / `@SpringBootApplication` | Bật cơ chế xét các auto-configuration |
| Classpath | Có class tương ứng như Servlet, DispatcherServlet và thư viện web |
| Loại ứng dụng | Servlet web application, reactive web application hoặc ứng dụng không web |
| Bean hiện có | Một số mặc định tự rút lui khi ứng dụng đã khai báo bean cùng trách nhiệm |
| Property | Bật/tắt/chỉnh hành vi khi có hỗ trợ |
| Các điều kiện khác | Ví dụ tài nguyên, cấu hình/bean khác và thứ tự áp dụng |

Các annotation dạng `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnWebApplication`, `@ConditionalOnProperty` là cơ chế điển hình. Một starter thường gom dependency phù hợp, trong khi **auto-configuration** là logic đăng ký bean có điều kiện; hai khái niệm liên quan nhưng không đồng nhất.

### 3.2. Ví dụ B — Spring Boot MVC với server nhúng

Để so sánh cùng endpoint `/greet`, tạo **một dự án riêng** tên `boot-mvc` với **Java 17+ và Spring Boot 3.5.7** (phiên bản cố định để ví dụ có thể tái lập; trong dự án thực hãy theo dòng phiên bản còn được hỗ trợ). File `build.gradle`:

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

Không cần ghi trực tiếp version cho `spring-webmvc`, Servlet API hay embedded Tomcat vì hệ thống quản lý dependency của Boot phối hợp các phiên bản được hỗ trợ. File `src/main/java/demo/boot/BootMvcApplication.java`:

```java
package demo.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BootMvcApplication {
    public static void main(String[] args) {
        SpringApplication.run(BootMvcApplication.class, args);
    }
}
```

File `src/main/java/demo/boot/GreetingController.java`:

```java
package demo.boot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    @GetMapping("/greet")
    public String greet(
            @RequestParam(defaultValue = "Lan") String name) {
        return "Xin chào, " + name + "!";
    }
}
```

Với wrapper Gradle đã có:

```bash
./gradlew bootRun
```

Trên Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

Mở terminal khác:

```bash
curl -i 'http://localhost:8080/greet?name=Lan'
```

Status mong đợi **200 OK**, body:

```text
Xin chào, Lan!
```

Bạn cũng có thể đóng gói bằng `./gradlew bootJar` rồi chạy file JAR sinh ra trong `build/libs/` với `java -jar <ten-file-jar>`. Endpoint dùng đường dẫn `/greet` vì ở ví dụ JAR nhúng này **context path mặc định là gốc**. Nếu cấu hình context path khác, URL sẽ thay đổi.

**Dòng khởi tạo trong Boot JAR:**

```text
main()
  → SpringApplication.run(BootMvcApplication.class, args)
  → quét package demo.boot và các package con
  → xét các auto-configuration theo dependency/condition
  → tạo Servlet web application context + embedded server (nếu phù hợp)
  → đăng ký DispatcherServlet và MVC infrastructure
  → /greet được map tới GreetingController
  → HTTP request → cùng cơ chế xử lý của Spring MVC
```

`@SpringBootApplication` **không tự tạo endpoint**: controller vẫn phải được Spring tìm thấy và có mapping phù hợp. Nếu dời `GreetingController` ra ngoài package được scan mà không cấu hình lại, có thể nhận 404 dù ứng dụng Boot vẫn khởi động.

### 3.3. Boot tự cung cấp mặc định MVC gì?

Trong ứng dụng Servlet MVC Boot 3.x đủ dependency, những khả năng thường được cấu hình sẵn gồm:

- `DispatcherServlet` registration và các thành phần MVC phối hợp.
- Nhiều `HttpMessageConverter`, bao gồm JSON khi thư viện thích hợp hiện diện trên classpath.
- Hỗ trợ resource tĩnh từ các vị trí quy ước (chẳng hạn `classpath:/static/`, `classpath:/public/`) khi tính năng đang được bật.
- Một số `ViewResolver`, content negotiation, binding/conversion và hỗ trợ error handling theo cấu hình của Boot.
- Thiết lập qua property thay vì đăng ký lại toàn bộ cấu hình bằng Java code, ở những điểm Boot cho phép.

**Mặc định phụ thuộc phiên bản/điều kiện**. Không giả định rằng mọi converter, view resolver, security filter hay CORS rule sẽ xuất hiện trong mọi ứng dụng. Ví dụ Boot có starter web không đồng nghĩa API được xác thực hoặc CSRF/CORS đã cấu hình theo yêu cầu nghiệp vụ.

## 4. Bảng so sánh trực tiếp

| Tiêu chí | Spring MVC cấu hình trực tiếp | Spring Boot MVC tự động cấu hình |
| --- | --- | --- |
| Bộ máy xử lý request | `DispatcherServlet` của Spring MVC | Chính `DispatcherServlet` của Spring MVC |
| Ai kích hoạt khởi tạo web | Servlet container gọi initializer hoặc `web.xml`; có thể tự dựng embedded server | `SpringApplication.run` phối hợp auto-config và web server phù hợp |
| Ai đăng ký `DispatcherServlet` | Người phát triển đăng ký hoặc dùng initializer | Boot có auto-configuration đăng ký trong trường hợp đáp ứng điều kiện |
| Bật MVC infrastructure | Thường `@EnableWebMvc` hoặc XML tương đương | MVC auto-config của Boot cung cấp nền tảng, không cần `@EnableWebMvc` |
| Controller | `@Controller`, `@RestController`, `@RequestMapping`... | Cùng các annotation/controller đó |
| Component scan | Tự khai báo hoặc import các bean cần thiết | `@SpringBootApplication` cung cấp scan từ package gốc |
| Cấu hình mặc định | Spring MVC baseline cộng các thành phần bạn thêm | Baseline của Spring MVC cộng nhiều mặc định Boot, có điều kiện |
| Dependency | Tự chọn/đồng bộ thư viện | Dùng starter và quản lý phiên bản phụ thuộc tương thích |
| Đóng gói thường gặp | WAR trên container bên ngoài hoặc tự cấu hình embedded | JAR với embedded server; vẫn có thể làm WAR |
| Thay đổi cấu hình | Cấu hình trực tiếp theo nhu cầu | Property, custom bean, `WebMvcConfigurer`; hoặc chủ động takeover |
| Nhu cầu kiểm thử | Chú ý initializer/context, môi trường Servlet và dependency | Có hỗ trợ test slice/auto-config từ Boot, nhưng vẫn cần kiểm tra behavior thực |

Giữ lại một điểm: “ít dòng cấu hình hơn” không đồng nghĩa “framework ít hoạt động hơn”. Boot làm nhiều công việc đăng ký/configuration thay bạn, nhưng request vẫn đi qua Spring MVC.

## 5. Ba mức độ can thiệp vào MVC khi dùng Spring Boot

Đây là phần quan trọng khi dự án đã có Boot nhưng cần một chút tùy chỉnh.

### Mức 1 — Giữ Boot auto-config, đổi bằng property

Ưu tiên dùng property khi Boot có hỗ trợ rõ. Ví dụ đặt cổng chạy và prefix context path trong `src/main/resources/application.yml`:

```yaml
server:
  port: 9090
  servlet:
    context-path: /demo
```

Với ví dụ Boot, URL thành `http://localhost:9090/demo/greet?name=Lan`. Việc đổi context path là trách nhiệm container/server configuration; controller vẫn `@GetMapping("/greet")`.

Một số property liên quan `spring.mvc.*` hoặc `spring.web.*` có thể tùy chỉnh MVC. Tên và ý nghĩa property thay đổi giữa các dòng Boot; hãy đối chiếu reference của **đúng version** đang dùng. Không cần tạo lại nguyên một `DispatcherServlet` chỉ để đổi port hay context path.

### Mức 2 — Thêm WebMvcConfigurer, vẫn giữ Boot auto-config

Khi muốn tùy chỉnh MVC mà không thay nền tảng Boot, tạo class cấu hình triển khai `WebMvcConfigurer` **không gắn `@EnableWebMvc`**. Trong dự án Boot ví dụ, thêm file `src/main/java/demo/boot/MvcExtras.java`:

```java
package demo.boot;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation
        .ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation
        .WebMvcConfigurer;

@Configuration
public class MvcExtras implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/start", "/greet");
    }
}
```

Khởi động lại và thử:

```bash
curl -i 'http://localhost:8080/start'
```

Một response redirect hợp lệ sẽ hướng tới `/greet` với status 3xx (thường là 302). Thử `curl -i -L ...` để theo redirect. Dùng `WebMvcConfigurer` để bổ sung interceptor, formatter, view controller hoặc CORS mapping là cách thông dụng để **mở rộng** MVC trong ứng dụng Boot. Với route/controller cụ thể, code nghiệp vụ vẫn nằm ở controller/service.

Điều quan trọng: `WebMvcConfigurer` là **extension point**, không phải dấu hiệu yêu cầu tắt toàn bộ auto-configuration. Một hoặc nhiều configurer có thể đóng góp tùy chỉnh; cần cẩn thận khi các cấu hình trùng/đè nhau.

### Mức 3 — Dùng @EnableWebMvc để tự nắm phần cấu hình MVC

Nếu thêm vào ứng dụng Boot:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
public class ManualMvcOwnership {
}
```

bạn đang nói: **Tôi muốn tự quản lý cấu hình hạ tầng Spring MVC, thay vì dùng phần MVC auto-configuration của Boot.**

Trong Boot 3.x, `WebMvcAutoConfiguration` được áp dụng có điều kiện khi ứng dụng Servlet có các lớp MVC thích hợp **và không có bean kiểu `WebMvcConfigurationSupport`**. `@EnableWebMvc` kích hoạt cấu hình MVC xây dựng trên cơ chế đó, khiến điều kiện tương ứng không còn phù hợp. Boot sẽ nhường phần MVC auto-config cho cấu hình người phát triển.

**Hệ quả cần phân biệt chính xác:**

- Các bean Spring MVC cơ bản do `@EnableWebMvc` cung cấp vẫn có thể hoạt động, nên endpoint `@GetMapping` **không nhất thiết biến mất**.
- Một số tiện ích/mặc định **chỉ do Boot MVC auto-config cung cấp** có thể mất hiệu lực hoặc cần bạn tái cấu hình: ví dụ phục vụ tài nguyên tĩnh theo quy ước hay cách tích hợp resolver/converter nhất định.
- Điều này **không tắt tất cả auto-configuration của Spring Boot**: embedded server, data source, các phần độc lập khác vẫn có thể được cấu hình nếu thỏa điều kiện tương ứng.
- Nếu muốn cấu hình thủ công triệt để, bạn chịu trách nhiệm xác định lại behavior mong đợi cho converter, static resources, formatter, view resolver, CORS và các phần MVC liên quan.

Ví dụ tình huống: `/greet` vẫn trả chuỗi nhưng `/style.css` được phục vụ theo cơ chế resource mặc định trước đó bắt đầu trả 404 sau khi bạn thêm `@EnableWebMvc`. Đừng chỉ nhìn thấy 404 rồi suy ra `DispatcherServlet` hỏng; hãy kiểm tra resource handler và điều kiện của MVC auto-config.

### 5.1. `WebMvcConfigurer` không đồng nghĩa với `WebMvcConfigurationSupport`

Tên giống nhau dễ gây nhầm:

- `WebMvcConfigurer`: **interface extension point**, có thể dùng trong Boot để bổ sung cấu hình **trong khi vẫn giữ** MVC auto-config.
- `WebMvcConfigurationSupport`: **class hạ tầng MVC**; sự tồn tại của bean kiểu này là điều kiện để Boot MVC auto-config back off trong Boot 3.x.
- `@EnableWebMvc`: nhập cấu hình MVC dựa trên `DelegatingWebMvcConfiguration` (một nhánh của `WebMvcConfigurationSupport`), nên có tác động ở mức sở hữu cấu hình MVC.
- Đừng tự tạo subclass `WebMvcConfigurationSupport` chỉ để thêm một interceptor hay formatter thông thường. Dùng `WebMvcConfigurer` khi tính năng được extension point hỗ trợ.

Với nhu cầu thay riêng các thành phần như `RequestMappingHandlerMapping`, `RequestMappingHandlerAdapter` hay `ExceptionHandlerExceptionResolver`, Boot 3.x còn có các extension point như `WebMvcRegistrations`; đây là tình huống chuyên sâu, không cần dùng cho một cấu hình MVC đơn giản.

## 6. “Back off” thực chất là gì?

**Back off** nghĩa là một auto-configuration **bỏ qua một phần hoặc toàn bộ cấu hình mặc định có điều kiện** khi phát hiện ứng dụng đã cung cấp thành phần có cùng vai trò. Nó không tự động có nghĩa Boot dừng chạy, xóa bean của người dùng hoặc mọi auto-config đều bị vô hiệu.

Ví dụ minh họa nguyên tắc bằng mã cấu hình tùy biến của riêng bạn:

```java
@AutoConfiguration
@ConditionalOnClass(SmtpClient.class)
class MailExampleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MailSender.class)
    MailSender defaultMailSender() {
        return new DefaultMailSender();
    }
}
```

Đây là **mã giả minh họa điều kiện**, không phải ví dụ Java độc lập: `SmtpClient`, `MailSender`, `DefaultMailSender` là các kiểu giả định, không phải API Spring tiêu chuẩn. Nếu `SmtpClient` không có trên classpath hoặc đã có bean `MailSender`, phương thức cấu hình mặc định sẽ không đăng ký bean. Hình thức annotation và nguyên tắc là thật; tên class trong ví dụ này chỉ dùng để dễ hiểu.

Áp dụng trở lại Spring MVC:

```text
Auto-configuration được cân nhắc
  │
  ├── Thiếu Servlet/web MVC trên classpath? → Không áp dụng phần MVC
  │
  ├── Không phải Servlet web application?   → Không áp dụng phần Servlet MVC
  │
  ├── Đã có WebMvcConfigurationSupport?      → WebMvcAutoConfiguration back off
  │
  └── Các điều kiện tương ứng đều hợp lệ     → Đăng ký MVC defaults của Boot
```

Các điều kiện là đặc tả của **một phiên bản Boot cụ thể**, không phải quy tắc cố định cho mọi thế hệ Spring. Không suy từ việc có một `@Bean` bất kỳ rằng tất cả bean MVC mặc định sẽ bị loại.

## 7. Điều gì xảy ra khi request đến?

Cùng request `GET /greet?name=Lan`, dù cách khởi tạo khác, pipeline Spring MVC tương đồng:

1. HTTP request tới Servlet container qua host, port và context path.
2. Servlet container chuyển request cho `DispatcherServlet` đã được đăng ký.
3. `HandlerMapping` tìm handler tương ứng với URL, method và các điều kiện mapping khác.
4. `HandlerAdapter` thực thi controller method, xử lý argument như `@RequestParam`.
5. Controller trả về `String`; do sử dụng `@RestController`, giá trị được đưa vào HTTP response body qua cơ chế MVC phù hợp.
6. Server gửi status, header và body về client.

```text
                   manual WAR                Boot JAR
                  ┌────────────┐            ┌────────────┐
Browser → HTTP →  │ Tomcat WAR │            │ Embedded   │ ← HTTP ← Browser
                  │ + Servlet  │            │ Servlet    │
                  └─────┬──────┘            └──────┬─────┘
                        │                          │
                        ▼                          ▼
                  DispatcherServlet          DispatcherServlet
                        │                          │
                        └────────────┬─────────────┘
                                     ▼
                            @GetMapping("/greet")
                                     ▼
                          "Xin chào, Lan!"
```

Khác nhau về deploy và default config không có nghĩa một endpoint tự động nhanh hơn/chậm hơn endpoint kia. Hiệu năng thực còn phụ thuộc dependency, bộ nhớ, threading, converter, logic nghiệp vụ, I/O và cấu hình deployment. Không lấy việc hai cách trả giống body để kết luận vòng đời bean hoặc cấu hình middleware cũng hoàn toàn giống nhau.

## 8. Chẩn đoán khi “Boot không tự cấu hình như dự kiến”

### 8.1. Dùng Condition Evaluation Report

Spring Boot có thể hiển thị report lý do auto-configuration được chọn hoặc bị bỏ qua khi bật debug:

```bash
./gradlew bootRun --args='--debug'
```

Hoặc khi chạy JAR:

```bash
java -jar build/libs/boot-mvc-1.0.0.jar --debug
```

Tên JAR trong lệnh thứ hai là **ví dụ tương ứng với tên project/version demo**; nếu file thực tế có tên khác, thay bằng đường dẫn từ `build/libs/`.

Trong output, tìm phần **CONDITIONS EVALUATION REPORT** và các entry liên quan `WebMvcAutoConfiguration`, `DispatcherServletAutoConfiguration`, web server. Report giúp trả lời: auto-config nào match, điều kiện nào không match, class hay bean nào gây back off. Cần đọc đúng phần positive/negative matches; log debug không đảm bảo tự sửa lỗi.

Nếu có thêm Spring Boot Actuator và cấu hình endpoint phù hợp, `/actuator/conditions` có thể cung cấp thông tin tương tự; endpoint này **không mặc nhiên được bật công khai** và cần quản lý quyền truy cập.

### 8.2. Bảng tình huống thường gặp

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| Boot app chạy nhưng endpoint 404 | Controller nằm ngoài package scan, sai method/path/context path | Xác nhận `@SpringBootApplication` package, mapping và context path |
| Lỗi không tìm được Servlet/DispatcherServlet | Thiếu dependency MVC/Servlet hoặc dùng sai loại web app | Xem dependency graph, starter và conditions report |
| WAR manual trả 404 | `DispatcherServlet` chưa được đăng ký, sai WAR context path hoặc thiếu scan | Xem log Tomcat, `AppInitializer`, mapping `/`, URL thực tế |
| WAR manual lỗi class `javax.servlet.*` / `jakarta.servlet.*` | Kết hợp sai đời Spring/Servlet container | Với Spring 6 dùng `jakarta.*` và container tương thích |
| Sau khi thêm `@EnableWebMvc`, CSS/JS tĩnh không còn hoạt động | Boot MVC auto-config đã nhường quyền; resource handler thiếu | Xem conditions report, bỏ annotation nếu không cần takeover |
| JSON serialize/deserialize lỗi | Converter/Jackson không có, MIME type không hợp hoặc binding lỗi | Kiểm tra dependency, `Content-Type`, message converter |
| Thêm `WebMvcConfigurer` nhưng interceptor không chạy | Config ngoài component scan, pattern sai, request không đi qua chain mong đợi | Xác nhận bean đã đăng ký và request mapping |
| Đã định nghĩa bean mà Boot vẫn cấu hình thứ khác | Bean không cùng kiểu/điều kiện; cấu hình chỉ back off một phần | Đọc điều kiện của auto-config cụ thể |
| Tưởng `@SpringBootApplication` thay `@RestController` | Annotation ứng dụng chỉ bootstrap/scan/auto-config | Controller cần bean + mapping thực tế |
| Đổi `server.port` nhưng gọi cổng cũ | Port đã thay, config khác override, hoặc app chạy với profile khác | Xem log web server và effective configuration |

### 8.3. Quy trình kiểm tra ngắn

Khi có lỗi startup hoặc routing, hãy đi theo thứ tự:

1. **Dependency:** có đúng Servlet MVC stack không (`spring-webmvc`, starter web) và không trộn nhầm API `javax.*`/`jakarta.*`?
2. **Bootstrap:** container đã chạy? `SpringApplication.run`/initializer đã được gọi và `DispatcherServlet` đã đăng ký?
3. **Context:** controller bean có được tạo trong đúng application context chưa?
4. **Mapping:** method, URL, context path và servlet mapping thực tế có khớp request không?
5. **MVC configuration:** ứng dụng có `@EnableWebMvc`/`WebMvcConfigurationSupport` gây back off không? `WebMvcConfigurer` có đăng ký đúng không?
6. **Converter/resource/error behavior:** endpoint hoạt động nhưng JSON, static file hoặc view bị lỗi là vấn đề khác với thiếu Servlet.
7. **Condition report:** dùng dữ liệu lúc chạy để xác định cái gì đã match thay vì đoán theo annotation.

## 9. Chọn cách cấu hình theo nhu cầu

**Dùng Spring MVC trực tiếp** khi bạn cần tích hợp framework vào môi trường Servlet có sẵn, tự quản chặt lifecycle và hệ thống bean, hoặc đang học/duy trì ứng dụng Spring MVC không dùng Boot. Bạn cần hiểu rõ container, đăng ký Servlet, dependency, context hierarchy và các thành phần MVC.

**Dùng Spring Boot MVC** khi muốn ứng dụng web khởi động nhất quán, dependency dễ quản lý và tận dụng các cấu hình mặc định có điều kiện. Đây thường là cách thuận tiện cho service mới, nhưng bạn vẫn cần hiểu cơ chế gốc để debug.

**Trong ứng dụng Boot đã tồn tại**, chọn mức thay đổi nhỏ nhất phù hợp: property cho thiết lập sẵn có; `WebMvcConfigurer` cho nhiều tùy chỉnh MVC; `@EnableWebMvc` chỉ khi thực sự muốn nhận trách nhiệm quản lý phần lớn cấu hình MVC. Tùy chỉnh `@Bean` có thể chỉ khiến một phần auto-config nhường chỗ; phải kiểm tra điều kiện cụ thể.

## 10. Sai lầm thường gặp và câu hỏi tự kiểm tra

**Sai lầm 1:** “`@EnableWebMvc` là annotation bắt buộc trong mọi Spring Boot web app.” Thực tế Boot MVC auto-config thường thay nhu cầu đó; thêm `@EnableWebMvc` có thể vô hiệu phần cấu hình MVC do Boot cung cấp.

**Sai lầm 2:** “Spring Boot tự động cấu hình nên không có `DispatcherServlet`.” Spring MVC trong Boot vẫn dùng `DispatcherServlet`; Boot chỉ phụ trách đăng ký và thiết lập nhiều phần.

**Sai lầm 3:** “Có `@RestController` là endpoint tự hoạt động.” Lớp phải được đăng ký bean, có MVC infrastructure và được Servlet container chuyển request tới.

**Sai lầm 4:** “`WebMvcConfigurer` sẽ tắt auto-config.” `WebMvcConfigurer` là cơ chế bổ sung cấu hình; `@EnableWebMvc` hoặc bean hạ tầng sở hữu MVC mới là tình huống cần đặc biệt xem xét.

**Sai lầm 5:** “Spring MVC thuần bắt buộc phải dùng XML và server ngoài; Boot luôn chỉ tạo executable JAR.” Cả Java config/initializer và `web.xml` đều hợp lệ. Spring MVC có thể được tự gắn với embedded server; Boot cũng hỗ trợ WAR khi đúng cấu hình.

**Sai lầm 6:** “Auto-config luôn ghi đè bean tự khai báo.” Nhiều auto-config đặt điều kiện về bean đã tồn tại, nhưng không phải mọi trường hợp đều giống nhau. Quyền ưu tiên cần xem source/conditions của cấu hình cụ thể.

Tự kiểm tra:

1. Vì sao `@EnableWebMvc` không tự mở port 8080? Thành phần nào thực sự nhận kết nối HTTP?
2. Trong ví dụ manual WAR, lớp nào đăng ký `DispatcherServlet` và lớp nào kích hoạt MVC infrastructure?
3. Trong ví dụ Boot, `@SpringBootApplication` gồm ba hành vi chính nào? Điều kiện nào quyết định MVC auto-config được áp dụng?
4. Hai endpoint `/greet` có cùng controller logic nhưng context path khác nhau khi chạy WAR và Boot JAR ra sao?
5. Nếu chỉ muốn thêm redirect `/start → /greet`, vì sao `WebMvcConfigurer` phù hợp hơn việc takeover bằng `@EnableWebMvc`?
6. Nếu một static file ngừng hoạt động sau khi thêm `@EnableWebMvc`, hãy mô tả nguyên nhân có thể có và cách chứng minh bằng conditions report.
7. Giải thích sự khác nhau giữa `@ConditionalOnMissingBean`, `WebMvcConfigurer`, `WebMvcConfigurationSupport` và khái niệm **back off**.
8. Nếu xóa starter web và chỉ giữ một số thư viện Spring cơ bản, vì sao không nên giả định Boot vẫn tạo `DispatcherServlet` và web server?

**Đạt yêu cầu** khi bạn có thể vẽ luồng khởi tạo cả hai cách, chạy hai endpoint, chỉ ra đúng nơi `DispatcherServlet` được đăng ký, chọn được property/`WebMvcConfigurer`/`@EnableWebMvc` theo nhu cầu và sử dụng condition report để xác minh một giả thuyết cấu hình.

### Tài liệu kỹ thuật để đối chiếu với phiên bản đang dùng

- Spring Framework — DispatcherServlet: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html
- Spring Framework — Enable MVC Configuration: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/enable.html
- Spring Boot 3.5 — Servlet Web Applications / Spring MVC Auto-configuration: https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html
- Spring Boot — Auto-configuration: https://docs.spring.io/spring-boot/reference/using/auto-configuration.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="application-bootstrap-007">Triển khai trên Servlet container và phân biệt WAR với máy chủ nhúng</a>

<details>
<summary>Xem chi tiết</summary>

# Triển khai trên Servlet container và phân biệt WAR với máy chủ nhúng

## 1. Bài toán: ứng dụng Spring MVC thực sự chạy ở đâu?

Spring MVC xử lý request HTTP thông qua **Servlet API**, nhưng mã ứng dụng không tự mở socket và thực thi giao thức HTTP. Một **Servlet container** (ví dụ Apache Tomcat hoặc Jetty) nhận kết nối, tạo request/response, quản lý vòng đời Servlet/Filter và gọi `DispatcherServlet` của Spring MVC để điều phối đến controller.

Có hai kiểu triển khai phổ biến:

1. **Máy chủ nhúng (embedded server)**: ứng dụng Spring Boot đóng gói thành executable JAR và tự khởi động Tomcat/Jetty khi chạy `java -jar`. Tiến trình Java của ứng dụng sở hữu máy chủ HTTP và vòng đời của nó.
2. **Servlet container bên ngoài (external/traditional container)**: người vận hành khởi động Tomcat/Jetty trước; ứng dụng được đóng gói thành **WAR** và triển khai vào container đó. Container quản lý web application, context path và vòng đời Servlet theo hợp đồng triển khai.

Chúng có thể phục vụ cùng một REST endpoint và cùng sử dụng Spring MVC. Điểm khác nhau nằm ở **cách khởi động, đóng gói phụ thuộc, quyền quản lý cổng HTTP, context path và vận hành**.

```text
Cách A — Spring Boot executable JAR + Tomcat nhúng

shell / systemd / container runtime
             |
             v
     java -jar app.jar
             |
             v
  SpringApplication.run(...)
             |
             v
 ApplicationContext + Tomcat nhúng
             |
             v
    DispatcherServlet -> Controller

Cách B — WAR trên Tomcat bên ngoài

shell / systemd / container runtime
             |
             v
        Start Tomcat
             |
             v
   Tomcat deploy app.war
             |
             v
   SpringBootServletInitializer
             |
             v
 ApplicationContext + DispatcherServlet
             |
             v
          Controller
```

**Quan trọng:** Spring MVC là web framework; **Servlet container** là môi trường phục vụ request; **WAR/JAR** là dạng artifact. “WAR” không đồng nghĩa “Spring MVC cũ”; một WAR cấu hình đúng vẫn có thể chạy bằng Spring Boot hiện đại.

## 2. Servlet container chịu trách nhiệm gì?

Servlet container triển khai Jakarta Servlet specification và cung cấp môi trường chạy cho một hoặc nhiều web application. Những trách nhiệm chính:

- Mở listener HTTP/HTTPS, nhận request và chuyển thành `HttpServletRequest`/`HttpServletResponse`.
- Tạo và quản lý `ServletContext`, các Servlet, Filter, Listener theo vòng đời triển khai.
- Xử lý request mapping cấp Servlet, dispatcher type, session, timeout, async servlet và các yêu cầu khác trong phạm vi đặc tả Servlet.
- Phân vùng ứng dụng theo **context path** (chẳng hạn `/shop` và `/admin`), thường sử dụng classloader ứng dụng riêng.
- Khi undeploy/redeploy, dừng web application, giải phóng resource trong phạm vi có thể và nạp phiên bản mới. Các kết nối, thread hoặc static state do ứng dụng quản lý sai vẫn có thể gây rò rỉ tài nguyên.

Spring MVC bổ sung tầng điều phối:

```text
HTTP request
    |
    v
Servlet container
    |
    v
Filter chain
    |
    v
DispatcherServlet (một Servlet của Spring MVC)
    |
    +--> HandlerMapping
    +--> HandlerAdapter -> @Controller / @RestController
    +--> MessageConverter / ViewResolver
    +--> ExceptionResolver
    |
    v
HTTP response
```

Bản thân `DispatcherServlet` không thay thế container: nó là thành phần **được đăng ký trong container**, dù container được nhúng hay do một máy chủ khác khởi động.

## 3. Embedded server là gì?

Máy chủ nhúng là **thư viện Servlet container nằm trong dependency/runtime của ứng dụng** và được tạo khi tiến trình ứng dụng chạy.

Với Spring Boot 3.5:

- `spring-boot-starter-web` thường kéo theo `spring-boot-starter-tomcat` theo mặc định.
- Spring Boot auto-configuration tạo `ServletWebServerApplicationContext` và dùng `ServletWebServerFactory` phù hợp để khởi động Tomcat/Jetty/Undertow nhúng.
- `SpringApplication.run(...)` tạo application context, đăng ký DispatcherServlet cùng Servlet/Filter/Listener, rồi máy chủ phục vụ request.
- `server.port`, `server.address`, `server.servlet.context-path` và những cấu hình server khác điều khiển **server nhúng** theo khả năng được hỗ trợ.

Ví dụ cấu hình:

```yaml
# src/main/resources/application.yml
server:
  port: 8085
  servlet:
    context-path: /catalog
```

Với executable JAR có controller `@GetMapping("/books")`, endpoint bên ngoài là `http://localhost:8085/catalog/books`. Context path `/catalog` thuộc Servlet context, còn `/books` là đường dẫn controller.

Ưu điểm thực tế: artifact dễ chạy, mỗi ứng dụng tự chủ version container và dependency, có thể đóng gói thành container image hoặc chạy bằng service manager; mở rộng quy mô bằng cách chạy thêm instance sau load balancer. Điều này **không buộc** mọi hệ thống phải dùng microservice.

Hạn chế cần quản trị: mỗi tiến trình có resource/cổng/server lifecycle riêng; vận hành phải chuẩn hóa cấu hình, logs, shutdown, health checks và cập nhật bản vá bảo mật của container nhúng qua phiên bản dependency/build.

## 4. WAR và external Servlet container là gì?

**WAR (Web Application Archive)** là gói web application theo cấu trúc Java/Jakarta EE được Servlet container hiểu. Với triển khai truyền thống:

1. Build file `app.war`.
2. Đưa artifact tới khu vực deployment của Tomcat/Jetty hoặc dùng công cụ deploy của server.
3. Container phát hiện gói, thiết lập web application context, load class/resources và khởi tạo Spring MVC.
4. Request tới context path của WAR được chuyển qua filter chain tới `DispatcherServlet`.

Tomcat có thể chạy nhiều WAR cùng tiến trình, ví dụ:

```text
Tomcat process (HTTP :8080)
  |
  +-- /shop    -> shop.war    -> Spring MVC app A
  |
  +-- /admin   -> admin.war   -> Spring MVC app B
  |
  +-- /reports -> reports.war -> Spring MVC app C
```

Mỗi WAR thường có web application classloader riêng và ServletContext riêng. Tuy nhiên chúng vẫn **chia sẻ cùng JVM/container process**: CPU, memory, thread capacity, các chính sách container, cổng và rủi ro ảnh hưởng lẫn nhau. Vì vậy “nhiều WAR trong một Tomcat” không tự có mức cô lập bằng nhiều tiến trình/container độc lập.

Ưu điểm: phù hợp tổ chức có nền tảng Java application server do đội vận hành quản lý, triển khai/redeploy ứng dụng vào server hiện hữu, dùng các chính sách container chung. Đánh đổi: phải quản lý compatibility giữa WAR và container, cấu hình deployment, classloader, shared libraries và vòng đời server; nâng cấp container ảnh hưởng nhiều ứng dụng trên cùng instance.

**WAR không có nghĩa là tất cả thư viện đều được container cung cấp.** Container thường cung cấp Servlet API và implementation container; các dependency nghiệp vụ, Spring Framework và phần lớn dependency của ứng dụng vẫn được đóng gói trong WAR. Chính xác cái nào dùng `provided` cần quyết định theo hợp đồng môi trường đích.

## 5. So sánh executable JAR, deployable WAR và executable WAR

| Tiêu chí | Executable JAR + server nhúng | WAR trên container ngoài | Executable WAR của Spring Boot |
| --- | --- | --- | --- |
| Ai bắt đầu server HTTP? | `main`/Spring Boot của ứng dụng | Tomcat/Jetty bên ngoài | Khi `java -jar`: Spring Boot; khi deploy: container ngoài |
| Artifact | `.jar` có Boot loader và dependencies | `.war` có cấu trúc web application | `.war` được Spring Boot repackaging |
| Lệnh/mô hình | `java -jar app.jar` | Deploy WAR theo server, khởi động server | `java -jar app.war` **hoặc** deploy WAR |
| Servlet container runtime | Được đóng gói cho execution | Do server bên ngoài cung cấp | `WEB-INF/lib-provided` cho execution; bên ngoài dùng container của server |
| HTTP port | Thường từ `server.port` | Do container ngoài cấu hình | Phụ thuộc mode chạy |
| Context path | `server.servlet.context-path` của server nhúng | Thường do deployment/context của server quyết định | Phụ thuộc mode chạy |
| Độc lập tiến trình | Một app thường một JVM/process | Nhiều WAR có thể chung JVM | Tùy mode |
| Nâng cấp container | Cập nhật dependency và rebuild artifact | Đội vận hành nâng cấp server | Mode nào dùng container đó |
| Rủi ro vận hành cần kiểm | JVM/process, port, build image và lifecycle | Shared server, classloading, context path, server patch | Phải kiểm thử **cả hai mode** nếu hỗ trợ cả hai |

**Phân biệt quan trọng:** WAR là **format**; embedded/external là **mode chạy**. Spring Boot có thể xây **executable WAR** triển khai vào Tomcat ngoài và cũng chạy được bằng `java -jar app.war` khi dependency container được đóng gói đúng.

## 6. Cấu trúc đóng gói và classpath

Một Spring Boot executable JAR thường có dạng:

```text
catalog.jar
├── META-INF/MANIFEST.MF
├── BOOT-INF/
│   ├── classes/             # class và resource của ứng dụng
│   └── lib/                 # dependencies
└── ...                      # Spring Boot loader
```

WAR triển khai lên Servlet container có cấu trúc cơ bản:

```text
catalog.war
├── META-INF/
└── WEB-INF/
    ├── classes/             # class và resource của ứng dụng
    ├── lib/                 # thư viện chạy trong external container
    └── lib-provided/        # phần Boot executable WAR, nếu có
```

`WEB-INF/lib-provided` là chi tiết hữu ích của **executable WAR do Spring Boot plugin đóng gói**, không phải yêu cầu chung của mọi WAR: nó cho phép `java -jar catalog.war` tìm được embedded Tomcat, trong khi external Servlet container không đưa thư viện ở thư mục này vào ứng dụng theo cách thông thường.

Nếu nhét Tomcat embedded implementation vào `WEB-INF/lib` khi deploy WAR lên Tomcat ngoài, có thể xảy ra trùng implementation, sai classloader hoặc xung đột dependency. Trong Maven, đặt starter Tomcat ở scope `provided`; Spring Boot plugin vẫn có thể đóng gói vào `lib-provided` cho executable WAR. Ở Gradle, dùng cấu hình `providedRuntime` phù hợp với plugin WAR/Boot.

**Không nhầm** `provided` với “không cần Servlet API lúc biên dịch”: application vẫn cần compile classpath có API thích hợp. Ý nghĩa là dependency đó do môi trường chạy **bên ngoài** cung cấp khi deploy WAR.

## 7. Ví dụ hoàn chỉnh: một codebase chạy WAR trên Tomcat ngoài hoặc trực tiếp bằng java -jar

Ví dụ sử dụng **Java 17+, Spring Boot 3.5.16, Maven 3.6.3+ và Tomcat 10.1**. Đây là một tổ hợp tương thích trong dòng Boot 3.5: Spring Framework 6.x dùng `jakarta.servlet.*`, Tomcat 10.1 triển khai Jakarta Servlet 6.0. Người đọc không phải tự cài Servlet API bằng một JAR rời.

**Cấu trúc dự án:**

```text
deploy-demo/
├── pom.xml
└── src/main/java/example/deploy/
    ├── DeployDemoApplication.java
    └── HelloController.java
```

### 7.1. Maven pom.xml: packaging WAR và Tomcat provided

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
    <artifactId>deploy-demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>war</packaging>

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
            <artifactId>spring-boot-starter-tomcat</artifactId>
            <scope>provided</scope>
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

`spring-boot-starter-web` mang Spring MVC và mặc định tham chiếu starter Tomcat. Khai báo trực tiếp `spring-boot-starter-tomcat` với `provided` khiến container implementation không nằm trong dependency runtime tiêu chuẩn khi deploy vào container ngoài. Parent và Boot Maven Plugin xử lý đóng gói executable WAR phù hợp.

### 7.2. Application class và điểm khởi động thứ hai

**`src/main/java/example/deploy/DeployDemoApplication.java`:**

```java
package example.deploy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class DeployDemoApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(DeployDemoApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(DeployDemoApplication.class, args);
    }
}
```

Hai entry point có vai trò khác nhau:

- **Chạy trực tiếp `java -jar`:** `main(String[])` được Spring Boot launcher gọi và khởi động SpringApplication cùng embedded Tomcat.
- **Deploy lên Tomcat ngoài:** container tích hợp với Servlet `WebApplicationInitializer`; `SpringBootServletInitializer` đóng vai trò bridge để tạo Spring ApplicationContext từ lifecycle của Servlet container. `configure(SpringApplicationBuilder)` xác định source configuration.

`SpringBootServletInitializer` là kiểu hỗ trợ **traditional WAR deployment**. Với executable JAR thông thường, không cần kế thừa class này; để nguyên trong một codebase dùng cả hai mode cũng được.

### 7.3. Controller không cần biết loại packaging

**`src/main/java/example/deploy/HelloController.java`:**

```java
package example.deploy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello from Spring MVC";
    }

    @GetMapping("/servlet")
    public String servlet(HttpServletRequest request) {
        int major = request.getServletContext().getMajorVersion();
        int minor = request.getServletContext().getMinorVersion();
        return "Servlet " + major + "." + minor;
    }
}
```

Controller tương tự trong cả hai deployment mode. `HttpServletRequest` là kiểu **Jakarta** nên tương thích Spring 6/Tomcat 10.1. Endpoint `/servlet` lấy phiên bản đặc tả Servlet do container runtime công bố.

### 7.4. Build và chạy WAR ở chế độ nhúng

Đứng tại thư mục có `pom.xml`:

```bash
java -version
mvn -version
mvn clean package
java -jar target/deploy-demo-0.0.1-SNAPSHOT.war
```

Chạy thử bằng terminal khác:

```bash
curl -s http://localhost:8080/hello
curl -s http://localhost:8080/servlet
```

Output body với Tomcat 10.1 nhúng mặc định của Boot 3.5:

```text
Hello from Spring MVC
Servlet 6.0
```

Hai dòng là kết quả **của hai lệnh `curl` liên tiếp**, không phải một response chung. Dùng `curl.exe` trong PowerShell nếu alias `curl` không hoạt động như mong muốn.

Vì artifact là executable WAR được Boot repackaging, `java -jar ...war` được hỗ trợ; đây là tính năng của Spring Boot build plugin, không áp dụng mặc định cho mọi WAR thuần Java.

### 7.5. Deploy cùng WAR lên Tomcat 10.1 bên ngoài

Giả sử bạn đã cài **Tomcat 10.1** và chạy nó trên Java 17+. Tắt instance nhúng ở `:8080` trước để tránh tranh cổng. Các bước trên một Tomcat local tiêu chuẩn:

1. Build `mvn clean package` và lấy `target/deploy-demo-0.0.1-SNAPSHOT.war`.
2. Đặt vào `$CATALINA_BASE/webapps/catalog.war`, hoặc sử dụng giao diện/cơ chế deploy chính thức của container. `CATALINA_BASE` là đường dẫn cấu hình instance Tomcat (không nhất thiết trùng `CATALINA_HOME`).
3. Khởi động Tomcat bằng script được cung cấp (ví dụ `bin/catalina.sh run` trên Linux/macOS hoặc `bin\catalina.bat run` trên Windows), tùy cấu hình instance.
4. Kiểm tra log triển khai; nếu context path mặc định xuất phát từ tên WAR là `catalog` và Tomcat lắng nghe tại `:8080`, gọi:

```bash
curl -s http://localhost:8080/catalog/hello
curl -s http://localhost:8080/catalog/servlet
```

Output body dự kiến:

```text
Hello from Spring MVC
Servlet 6.0
```

Tại sao URL ở chế độ này có `/catalog`? Với cơ chế auto-deploy tiêu chuẩn của Tomcat, WAR `catalog.war` thường được triển khai dưới context path `/catalog`. Nếu deploy thành `ROOT.war` hoặc định nghĩa context descriptor/virtual host khác, đường dẫn thay đổi theo cấu hình container. Không hardcode `/catalog` vào `@GetMapping` chỉ để “đi theo WAR”.

Cổng `8080` trong ví dụ WAR bên ngoài thuộc **Tomcat config**. Nếu đặt `server.port=8085` trong Spring Boot application properties của WAR, bạn không nên kỳ vọng nó thay cổng lắng nghe của Tomcat ngoài; cổng thuộc container đích.

### 7.6. Cùng controller nhưng chuyển về executable JAR

Khi chỉ muốn chạy embedded Tomcat qua executable JAR, thay phần packaging trong `pom.xml` thành:

```xml
<packaging>jar</packaging>
```

Đồng thời xóa dependency `spring-boot-starter-tomcat` có `scope` là `provided`, giữ `spring-boot-starter-web` với Tomcat ở runtime bình thường. Class `DeployDemoApplication` vẫn biên dịch và chạy được (dù `SpringBootServletInitializer` khi đó không cần thiết). Sau đó:

```bash
mvn clean package
java -jar target/deploy-demo-0.0.1-SNAPSHOT.jar
```

Với server mặc định `:8080`, `/hello` vẫn trả:

```text
Hello from Spring MVC
```

Sự khác biệt nằm ở cách đóng gói/container lifecycle. Endpoint Spring MVC và business logic không cần chỉnh theo loại artifact nếu chúng không phụ thuộc cấu hình deployment.

## 8. Nếu dùng Gradle thay vì Maven

Ví dụ ý tưởng cho Spring Boot **3.5** với Gradle Groovy DSL, giữ phần `repositories`, plugin versions và cấu hình Java theo dự án đang dùng:

```groovy
plugins {
    id 'java'
    id 'war'
    id 'org.springframework.boot' version '3.5.16'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'example'
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
    providedRuntime 'org.springframework.boot:spring-boot-starter-tomcat'
}
```

Với WAR plugin và Boot plugin, task `bootWar` tạo executable WAR:

```bash
./gradlew bootWar
java -jar build/libs/deploy-demo-0.0.1-SNAPSHOT.war
```

Tên artifact cụ thể chịu ảnh hưởng cấu hình project name/archive name, nên xác minh dưới `build/libs` trước khi chạy. Để làm executable JAR thay vì WAR, cấu hình Java/Boot phù hợp, bỏ `war`/`providedRuntime`, sử dụng `bootJar` và giữ Tomcat mặc định ở runtime.

**Không thay `providedRuntime` bằng `compileOnly` theo thói quen**: dependency chỉ `compileOnly` có thể biến mất khỏi test runtime classpath, làm test web tích hợp không chạy được; `providedRuntime` dành cho trường hợp external runtime cung cấp container và Boot vẫn cần tạo executable WAR.

## 9. Vì sao WAR cần SpringBootServletInitializer nhưng JAR thường không cần?

Đây là vấn đề **ai kích hoạt Spring ApplicationContext**:

```text
Executable JAR / executable WAR qua java -jar
    |
    +--> main(String[])
          |
          +--> SpringApplication.run(...)
                |
                +--> tạo ApplicationContext và embedded Servlet container


External Tomcat deploy WAR
    |
    +--> tìm Servlet WebApplicationInitializer
          |
          +--> SpringBootServletInitializer.configure(...)
                |
                +--> tạo ApplicationContext trong ServletContext có sẵn
                      |
                      +--> đăng ký DispatcherServlet/Filter beans
```

Khi container bên ngoài đã cung cấp `ServletContext` và listener HTTP, Spring không nên tự tạo một Tomcat thứ hai. Tương tự, khi chạy embedded server thì Spring Boot phải tạo container để request đi vào.

Nếu một Spring MVC app **không dùng Spring Boot**, người viết vẫn có thể tạo WAR và đăng ký `DispatcherServlet` bằng `web.xml` hoặc cấu hình Java như `AbstractAnnotationConfigDispatcherServletInitializer`. `SpringBootServletInitializer` là công cụ riêng của **Spring Boot** giúp nối mô hình WAR truyền thống với `SpringApplication`.

**Không phải mọi ứng dụng `@SpringBootApplication` đều triển khai được dưới external Servlet container chỉ bằng cách đổi đuôi `.jar` thành `.war`.** Cần thực sự tạo WAR đúng cấu trúc, đúng initializer, đúng scope container dependency và tương thích phiên bản runtime.

## 10. Tương thích Spring / Jakarta Servlet / JDK / container

Servlet container và WAR phải dùng **cùng họ API mà Spring yêu cầu**.

| Tổ hợp minh họa | Servlet API/namespace | Điều kiện chính |
| --- | --- | --- |
| Spring Boot 2.7 / Spring Framework 5.3 + Tomcat 9 | Servlet 4.0, `javax.servlet.*` | Dòng cũ dùng Java EE/`javax`. |
| Spring Boot 3.5 / Spring Framework 6.2 + Tomcat 10.1 | Servlet 6.0, `jakarta.servlet.*` | Java 17+ cho Boot 3.5; container triển khai Jakarta Servlet 6.0. |
| Spring Boot 3.5 deploy lên container Servlet 5.0+ khác | `jakarta.servlet.*` | Spring Boot 3.5 tuyên bố có thể triển khai tới Servlet 5.0+ compatible container; vẫn phải kiểm tra server/vendor/version cụ thể. |

**Tomcat 9 không phù hợp với WAR Spring 6/Boot 3** vì Tomcat 9 cung cấp `javax.servlet`, trong khi Spring 6 cần `jakarta.servlet`. Đây là thay đổi package/type ở mức biên dịch và bytecode; không thể giải quyết bằng việc chỉ nâng Java hay thêm cả hai Servlet API JAR.

**Java version tối thiểu** không chỉ do Tomcat quyết định. Spring Boot 3.5.16 yêu cầu Java 17+. Máy chạy external Tomcat phải đáp ứng **mức cao nhất** của server, application và libraries. Dự án build bằng Java 21 mà production JVM chỉ có Java 17 có thể gặp `UnsupportedClassVersionError` nếu không cấu hình compiler target phù hợp.

Các container hỗ trợ theo tài liệu Spring Boot 3.5.16 cho embedded mode gồm **Tomcat 10.1 (Servlet 6.0)**, **Jetty 12.0 (Servlet 6.0)** và **Undertow 2.3 (Servlet 6.0)**. Không suy rộng các version này sang Spring Boot 4.x mà không đọc tài liệu thế hệ mới.

## 11. Context path, port, profile và cấu hình theo môi trường

### Context path

- **Executable JAR/embedded container:** thường điều khiển bằng `server.servlet.context-path=/catalog`, cộng với các mapping ở controller.
- **WAR ngoài Tomcat:** context path thường do tên WAR, context descriptor hoặc cấu hình deployment của container quyết định. Không nên coi thuộc tính `server.servlet.context-path` là nguồn chân lý cho context path của external container.
- **Reverse proxy:** có thể xuất bản app ra `https://site.example/catalog`, trong khi container nội bộ có context khác. Cần phối hợp rewrite path, forwarded prefix và URL generation; WAR/JAR không tự giải quyết chuyện public URL.

### Port và listener

- **Embedded:** `server.port=8085` có thể đổi cổng listener của app khi bật embedded web server.
- **External:** Tomcat ngoài mở listener theo `conf/server.xml`, environment/container config hoặc tooling của instance đó; cấu hình port nằm ở tầng Tomcat, không thuộc từng WAR.
- Hai WAR trong cùng Tomcat chia sẻ HTTP listener nhưng thường có các context path khác nhau.

### Profile, environment và resource

`application.properties`/`application.yml`, Spring profiles và externalized configuration vẫn dùng được trong cả hai mode, nhưng nguồn cấu hình, environment variables, logging, filesystem và resource path do môi trường triển khai quyết định. Đừng dùng đường dẫn file tương đối như thể ứng dụng luôn được chạy từ thư mục chứa artifact.

Với WAR, JSP hay resource theo `ServletContext` có thể chịu hạn chế khác executable JAR. Tài liệu Spring Boot ghi rõ JSP **không được hỗ trợ trong executable JAR**; với Tomcat/Jetty, dùng WAR theo mode tương thích khi cần JSP. Các dự án REST trả JSON thường ít phụ thuộc JSP hơn.

## 12. Vận hành: deployment, scaling, shutdown và quan sát lỗi

### Triển khai và nâng cấp

**Executable JAR:** đưa artifact/container image mới vào một process/instance mới; dùng service manager/orchestrator hoặc script đáng tin cậy để điều khiển process. Rolling deployment thực hiện ở load balancer/orchestrator khi có nhiều instance.

**WAR trên external container:** deploy/undeploy/redeploy WAR trong instance Tomcat/Jetty theo cơ chế container; một Tomcat có thể đang phục vụ nhiều ứng dụng. Rollback cũng phải xét các dependency chia sẻ, cache và tài nguyên ngoài process.

Không nên hứa “redeploy WAR không gây gián đoạn” chỉ vì không khởi động lại Tomcat: request đang chạy, session, async jobs và resource của web application có thể bị ảnh hưởng, tùy strategy/container.

### Memory, thread, session, classloader

- Dùng nhiều WAR trong một JVM có thể tiết kiệm một phần overhead, nhưng các ứng dụng cùng cạnh tranh thread, heap, GC và native memory. Một WAR bị memory leak có thể ảnh hưởng toàn server.
- Mỗi embedded JAR thường có JVM/process riêng, giúp tách lỗi process và nâng cấp runtime độc lập; đổi lại tổng số process và mức tiêu thụ tài nguyên có thể tăng.
- HTTP session in-memory không tự chia sẻ giữa các JVM/instance. Khi scale ngang, cần đánh giá sticky sessions hoặc session store ngoài (ví dụ Spring Session) tùy yêu cầu.
- Đừng tạo thread, scheduler, connection pool hoặc singleton global không được hủy khi stop ứng dụng; WAR redeploy liên tục có thể gây classloader leak.

### Logs và health

Ở embedded mode, Spring Boot logs và lifecycle gắn trực tiếp với app process. Trong external mode, cần đọc **cả log container và log ứng dụng** (startup deployment, classloading, request mapping). Để làm readiness/liveness hoặc theo dõi metrics, nên sử dụng cơ chế health/observability phù hợp với môi trường; đừng nhầm “Tomcat process còn sống” với “tất cả WAR đang khỏe”.

## 13. Những lỗi triển khai thường gặp

| Triệu chứng | Nguyên nhân thường gặp | Kiểm tra và hướng xử lý |
| --- | --- | --- |
| `java -jar app.war` báo thiếu main manifest/loader | WAR thuần chưa được Spring Boot repackaging thành executable archive | Kiểm tra Spring Boot build plugin, task/package và manifest. |
| WAR deploy nhưng endpoint 404 ở `/hello` | Context path không phải root | Thử `/catalog/hello` nếu deploy `catalog.war`; kiểm tra deployment log/context config. |
| `ClassNotFoundException: javax.servlet...` hoặc `jakarta.servlet...` | Spring/framework/library và Servlet container khác namespace/version | Đồng bộ Spring, Servlet API và container; tìm dependency cũ. |
| `NoSuchMethodError` / `LinkageError` lúc startup | Conflict giữa thư viện trong WAR và API/implementation container | Xem dependency tree, `WEB-INF/lib`, shared libs của server và version compatibility. |
| Tomcat ngoài đang nghe `:8080` dù cấu hình `server.port=9090` trong WAR | Cổng thuộc external container | Chỉnh connector/listener ở Tomcat thay vì trong WAR. |
| `Address already in use: bind` khi chạy executable JAR | Cổng embedded đã bị process khác dùng | Đổi `server.port` hoặc dừng process chiếm cổng. |
| WAR deploy không gọi `@SpringBootApplication` | Thiếu hoặc sai `SpringBootServletInitializer`/initializer discovery, gói không đúng | Kiểm tra class application, `configure`, packaging WAR và startup logs. |
| WAR startup bị `UnsupportedClassVersionError` | Production JVM thấp hơn bytecode Spring/app/dependency | Kiểm tra Java runtime của **Tomcat process** và target compiler. |
| REST chạy nhưng JSP không hiển thị trong JAR | Hạn chế JSP của executable JAR | Xem hướng dẫn Spring Boot; khi cần JSP với Tomcat/Jetty thường dùng WAR. |
| App chạy qua reverse proxy nhưng redirect sai host/HTTP | Forwarded headers và public URL chưa được cấu hình/trust đúng | Kiểm tra proxy, context path và forwarded-header strategy; không liên quan trực tiếp đuôi file artifact. |
| WAR redeploy nhiều lần tăng heap/class count | Thread/resource/classloader leak | Dừng thread executor, đóng pool/listener, xem memory/thread dump và Tomcat logs. |

### Lệnh chẩn đoán hữu ích

```bash
# Kiểm tra Java/Maven đang chạy build
java -version
mvn -version

# Đóng gói và xem những file vừa build
mvn clean package

# Xem cấu trúc archive không cần giải nén
jar tf target/deploy-demo-0.0.1-SNAPSHOT.war

# Tìm dependency Tomcat trong Maven tree
mvn dependency:tree -Dincludes=org.springframework.boot:spring-boot-starter-tomcat

# Gọi endpoint HTTP; không tự follow redirect
curl -i http://localhost:8080/hello
curl -i http://localhost:8080/catalog/hello
```

Hai lệnh `curl` cuối cùng minh họa **hai mode/contexts** khác nhau; không giả định cả hai phải trả HTTP 200 cùng lúc. Với một WAR ngoài Tomcat, nếu context không phải `/catalog` thì thay đường dẫn theo deployment thực tế.

Để kiểm tra chính xác artifact Maven đã chọn, có thể dùng `jar tf` tìm `WEB-INF/classes/`, `WEB-INF/lib/` và `WEB-INF/lib-provided/`; kiểm tra log Spring/Servlet container để biết initializer và context path đã được thiết lập hay chưa.

## 14. Khi nào nên chọn cách nào?

**Chọn executable JAR + embedded Tomcat** khi muốn một runtime thống nhất cùng artifact, container image/service độc lập, mở rộng instance linh hoạt hoặc không có yêu cầu sử dụng shared application server. Đây là đường đi phổ biến cho Spring Boot REST applications mới.

**Chọn WAR trên container ngoài** khi doanh nghiệp/hệ thống cần chuẩn triển khai WAR vào Tomcat/Jetty có sẵn, vận hành tập trung nhiều web app trong một server, hoặc có yêu cầu tích hợp Servlet deployment/JSP/classloader cụ thể. Cần ưu tiên test trên đúng container vendor và version đích.

**Chọn executable WAR** khi cần duy trì **cả hai cách** (deploy truyền thống và chạy riêng để kiểm thử/vận hành) với một artifact; kiểm chứng cả `java -jar .war` lẫn WAR deployment. Tránh giữ hai mode lâu dài nếu chỉ một mode được dùng, vì tăng số cấu hình và kịch bản phải kiểm thử.

Quyết định dựa trên tiêu chí thật: hợp đồng vận hành, isolation, quản lý JVM, yêu cầu JSP/WAR, quy trình deploy, cách scale, môi trường mạng và phiên bản Servlet container; không dựa vào giả định WAR luôn cũ còn JAR luôn mới.

## 15. Tự đánh giá kiến thức

**Câu 1.** Một executable JAR Spring Boot có dùng Servlet API không?  
**Trả lời:** Có, nếu ứng dụng là Spring Web MVC chạy trên Tomcat/Jetty/Undertow nhúng. Container nằm trong tiến trình ứng dụng nhưng vẫn triển khai Servlet API.

**Câu 2.** Vì sao khi deploy WAR cần `SpringBootServletInitializer`?  
**Trả lời:** Container ngoài đã được khởi động; initializer cung cấp cầu nối từ vòng đời Servlet deployment đến việc tạo Spring Boot ApplicationContext. Với `java -jar`, `main` làm việc khởi động.

**Câu 3.** Tại sao không để `spring-boot-starter-tomcat` ở runtime thông thường trong WAR?  
**Trả lời:** Container ngoài đã cung cấp Servlet implementation. Đánh dấu `provided`/`providedRuntime` giúp tránh đóng gói container runtime như dependency deploy thông thường; executable WAR của Boot có cơ chế `lib-provided` riêng.

**Câu 4.** Nếu WAR tên `catalog.war` và Tomcat lắng nghe `:8080`, controller map `/hello` thường được gọi tại đâu?  
**Trả lời:** Với Tomcat auto-deploy mặc định và không có cấu hình context khác: `http://localhost:8080/catalog/hello`. Context path do deployment quyết định.

**Câu 5.** Spring Boot 3.5 WAR có deploy lên Tomcat 9 được không nếu dùng Java 21?  
**Trả lời:** Không. Java 21 không sửa được bất đồng Jakarta Servlet: Tomcat 9 thuộc `javax.servlet`, Spring Boot 3.x dùng `jakarta.servlet`.

**Câu 6.** Một executable WAR chạy được bằng `java -jar` có tự chứng minh deploy lên Tomcat ngoài thành công không?  
**Trả lời:** Không. Mode ngoài có container version, context path, shared libraries và lifecycle khác; cần kiểm thử trên đúng container đích.

## 16. Nguồn tham khảo chính thức

- [Spring Boot 3.5 — Traditional Deployment](https://docs.spring.io/spring-boot/3.5/how-to/deployment/traditional-deployment.html): `SpringBootServletInitializer`, `<packaging>war</packaging>`, Maven `provided`, Gradle `providedRuntime` và executable WAR.
- [Spring Boot 3.5 — Embedded Web Servers](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html): lựa chọn Tomcat/Jetty/Undertow và cấu hình server.
- [Spring Boot 3.5 — Servlet Web Applications](https://docs.spring.io/spring-boot/3.5/reference/web/servlet.html): mô hình Servlet, DispatcherServlet và giới hạn JSP.
- [Spring Boot 3.5 — System Requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html): JDK, phiên bản Servlet container và khả năng deploy.
- [SpringBootServletInitializer API](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/web/servlet/support/SpringBootServletInitializer.html): bridge giữa traditional WAR và SpringApplication.
- [Spring Boot 3.5 — Running Your Application](https://docs.spring.io/spring-boot/3.5/reference/using/running-your-application.html): chạy artifact và vòng đời ứng dụng.
- [Apache Tomcat — Which Version?](https://tomcat.apache.org/whichversion.html): đối chiếu Tomcat và Servlet/Jakarta EE.

**Điểm ghi nhớ:** controller Spring MVC có thể giữ nguyên khi đổi deployment mode, nhưng phải xác định rõ **ai tạo Servlet container, ai quản lý cổng/context path và classpath nào được sử dụng trong artifact so với runtime thực tế**.

</details>

- [Quay lại đầu trang](#back-to-top)
