<a id="back-to-top"></a>

# Thiết lập Spring AOP

## Menu
- [Các thư viện Spring AOP và AspectJ cần cho cấu hình được chọn](#spring-aop-setup-001)
- [Quản lý phiên bản phụ thuộc tương thích với Spring Framework](#spring-aop-setup-002)
- [Bật hỗ trợ @AspectJ bằng @EnableAspectJAutoProxy](#spring-aop-setup-003)
- [Vai trò @Configuration, component scan và đăng ký bean](#spring-aop-setup-004)
- [Cấu hình proxyTargetClass và exposeProxy](#spring-aop-setup-005)
- [Phạm vi hiệu lực cấu hình trong từng ApplicationContext](#spring-aop-setup-006)
- [Phân biệt mặc định proxy của Spring Framework và cấu hình từ Spring Boot](#spring-aop-setup-007)
- [Ứng dụng tối thiểu để xác nhận một advice được thực thi](#spring-aop-setup-008)
- [Kiểm tra cấu hình Java và XML không gây thiết lập chồng chéo](#spring-aop-setup-009)

## <a id="spring-aop-setup-001">Các thư viện Spring AOP và AspectJ cần cho cấu hình được chọn</a>

<details>
<summary>Xem chi tiết</summary>

# Các thư viện Spring AOP và AspectJ cần cho cấu hình được chọn

## 1. Mục tiêu: chọn đúng dependency theo kiểu cấu hình Spring

Khi thiết lập **Spring AOP**, câu hỏi quan trọng không chỉ là “đã thêm thư viện AOP chưa?” mà là **ứng dụng đang sử dụng cơ chế AOP nào**: Spring Boot auto-configuration, Spring Framework cấu hình bằng `@EnableAspectJAutoProxy`, cơ chế proxy thủ công của Spring, hay AspectJ weaving được tích hợp vào Spring. Mỗi lựa chọn có classpath và cách kích hoạt khác nhau.

Bài này chỉ xét việc **chọn và kiểm tra thư viện để sử dụng AOP trong ứng dụng Spring**. Kiến thức AOP tổng quát và kỹ thuật viết aspect bằng AspectJ độc lập với Spring không thuộc phạm vi ở đây.

**Đối với nhu cầu phổ biến nhất** — định nghĩa bean `@Aspect`, khai báo `@Before`, `@Around` và áp dụng advice vào phương thức của các bean Spring qua proxy — bộ thành phần cốt lõi là:

- **Spring AOP**, module `org.springframework:spring-aop`: tạo và vận hành proxy, advisor, interceptor trong Spring.
- **AspectJ weaver library**, module `org.aspectj:aspectjweaver`: cung cấp các kiểu AspectJ và khả năng phân tích, đối sánh biểu thức pointcut để Spring hiểu kiểu khai báo `@AspectJ`.
- **Spring container**, điển hình `org.springframework:spring-context` đối với Spring Framework thuần, hoặc các starter của Spring Boot: đăng ký bean, lifecycle và cấu hình.

Ở cấu hình này, Spring thực hiện **proxy-based AOP**. Tên thư viện `aspectjweaver` không có nghĩa ứng dụng tự động chạy AspectJ compile-time weaving (CTW) hay load-time weaving (LTW).

## 2. Phân biệt các artifact dễ nhầm

| Artifact | Nhóm | Vai trò trong cấu hình Spring | Khi nào cần |
| --- | --- | --- | --- |
| `org.springframework:spring-aop` | Spring Framework | API và hạ tầng proxy AOP, advisor, pointcut, auto-proxy | Cơ bản cho Spring AOP; thường được starter hoặc `spring-context` kéo theo. |
| `org.springframework:spring-context` | Spring Framework | ApplicationContext, quản lý bean và cấu hình Java `@Configuration` | Ứng dụng Spring thuần; thường đã có trong ứng dụng Spring Boot. |
| `org.aspectj:aspectjweaver` | AspectJ | Các class AspectJ và pointcut parser/matcher dùng cho Spring `@AspectJ` | Cần trên classpath khi khai báo Spring aspect bằng `@Aspect`/`@Before`/`@Around` và dùng `@EnableAspectJAutoProxy`. |
| `org.springframework.boot:spring-boot-starter-aop` | Spring Boot 3.x | Starter kéo những dependency cần cho Spring AOP dùng `@AspectJ` | Cách thêm AOP thông thường ở ứng dụng Boot 3.x. |
| `org.springframework.boot:spring-boot-starter-aspectj` | Spring Boot 4.x | Starter dành cho AOP với AspectJ, kéo `spring-aop` và `aspectjweaver` | Cách thêm AOP thông thường ở ứng dụng Boot 4.x. |
| `org.springframework:spring-aspects` | Spring Framework | Bộ aspect do Spring cung cấp cho các trường hợp sử dụng AspectJ weaving như `@Configurable` hoặc transaction aspect tương ứng | Chỉ khi cấu hình thật sự sử dụng các aspect này; **không cần** cho custom `@Aspect` proxy thông thường. |
| `org.springframework:spring-instrument` | Spring Framework | Instrumentation agent của Spring cho các cấu hình load-time weaving phù hợp | Chỉ khi chọn đường LTW dùng agent/instrumentation của Spring. |
| `org.aspectj:aspectjtools` | AspectJ | Công cụ compiler `ajc` cho AspectJ compile-time/post-compile weaving | Chỉ khi build được thiết kế để weaving; không cần cho Spring proxy AOP. |

**Hai ý dễ nhầm nhất:**

1. **Thêm `aspectjweaver` để Spring đọc `@Aspect` không đồng nghĩa bật weaving.** Spring vẫn gọi method thông qua proxy; self-invocation không tự được advice.
2. **`spring-aspects` khác `spring-aop`.** Đừng thêm `spring-aspects` chỉ vì cần `@Aspect`. Custom Spring aspect với `@Aspect` thường sử dụng `spring-aop` và `aspectjweaver` mà không cần `spring-aspects`.

Tùy phiên bản và dependency graph, một số thư viện đã có **bắc cầu (transitive)**. Mục tiêu là xác nhận **runtime classpath chứa đúng module cần thiết**, tránh thêm các phiên bản lẻ xung đột với hệ sinh thái Spring đang quản lý.

## 3. Chọn dependency theo kịch bản

| Cấu hình đã chọn | Dependency nên khai báo | Cách kích hoạt |
| --- | --- | --- |
| **Spring Boot 3.x + custom `@Aspect`** | `spring-boot-starter-aop` | Boot auto-configuration; đăng ký `@Aspect` thành bean `@Component` hoặc `@Bean`. |
| **Spring Boot 4.x + custom `@Aspect`** | `spring-boot-starter-aspectj` | Boot auto-configuration; đăng ký aspect bean. |
| **Spring Framework thuần + `@Aspect`** | `spring-context`, `spring-aop`, `aspectjweaver` (trực tiếp hoặc transitive theo dependency management) | `@EnableAspectJAutoProxy` trong cấu hình Spring; đăng ký aspect bean. |
| **Spring Framework thuần + Spring AOP API kiểu `ProxyFactory`/`MethodInterceptor`** | `spring-aop` cùng các module Spring mà ứng dụng thực sự dùng | Cấu hình proxy/advisor thủ công; có thể không cần `aspectjweaver` nếu không sử dụng parser hoặc annotation AspectJ. |
| **Spring + AspectJ weaving theo yêu cầu đặc biệt** | Spring AOP/integration và `aspectjweaver`; có thể thêm `spring-aspects`, `spring-instrument` hoặc công cụ biên dịch tương ứng | Có cấu hình weaving và agent/compiler riêng; thêm dependency là chưa đủ. |

**Quy tắc lựa chọn:** nếu chỉ cần advice trên **phương thức Spring bean được gọi qua proxy**, hãy chọn ba dòng đầu tùy nền tảng. Chỉ xét weaving khi có yêu cầu kỹ thuật đã xác nhận vượt quá mô hình proxy, chẳng hạn intercept self-invocation hoặc đối tượng ngoài Spring container.

## 4. Spring Boot 3.x: dùng `spring-boot-starter-aop`

Một ứng dụng đang dùng parent/BOM của **Spring Boot 3.x** thường chỉ cần bổ sung **một starter**, bởi starter quản lý chuỗi dependency cần thiết để Spring AOP làm việc. Không cần tự ghi version riêng cho `spring-aop` và `aspectjweaver` nếu chúng đã được Boot quản lý.

### Maven

Trong `pom.xml` có parent hoặc dependency management của Boot 3.x:

```xml
<dependencies>
    <!-- Các starter khác của ứng dụng -->

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
</dependencies>
```

### Gradle Groovy DSL

Trong `build.gradle` đã áp dụng Spring Boot plugin/BOM phù hợp:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

### Gradle Kotlin DSL

Trong `build.gradle.kts` đã áp dụng Spring Boot plugin/BOM:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-aop")
}
```

**Điều kiện đi kèm:** aspect phải được Spring đăng ký thành bean; thường là `@Aspect` **kết hợp** `@Component` trong package nằm dưới component scan, hoặc khai báo bằng `@Bean`. Chỉ gắn `@Aspect` vào một class nhưng không đăng ký bean chưa đủ để Spring áp dụng nó.

Với Boot auto-configuration và thư viện AspectJ phù hợp có trên classpath, hạ tầng aspect auto-proxy được bật tự động. Trong trường hợp chuẩn này, không cần lặp lại `@EnableAspectJAutoProxy`. Boot mặc định sử dụng class-based proxy; có thể thay bằng cấu hình `spring.aop.proxy-target-class=false` khi cần chiến lược JDK interface proxy và đối tượng phù hợp.

## 5. Spring Boot 4.x: dùng `spring-boot-starter-aspectj`

**Tên starter thay đổi theo dòng Spring Boot.** Trong Boot 4.x, starter được tài liệu chính thức liệt kê là `spring-boot-starter-aspectj`, chứa các thành phần cho Spring AOP và AspectJ. Khi nâng cấp project từ Boot 3 lên Boot 4, phải rà lại tên dependency, không chép nguyên cấu hình chỉ dành cho Boot 3.

### Maven

Trong một ứng dụng có parent/BOM của **Boot 4.x**:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aspectj</artifactId>
</dependency>
```

### Gradle Groovy DSL

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aspectj'
}
```

### Gradle Kotlin DSL

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
}
```

Starter tên là `aspectj` **không tự biến `@Aspect` của Spring Boot thành AspectJ weaving**. Ứng dụng chỉ dùng hạ tầng proxy nếu không cấu hình thêm AspectJ weaving. Khả năng và giới hạn self-invocation/đối tượng không phải bean vẫn là của Spring proxy AOP.

**Không chọn starter bằng cách đoán từ chữ “AspectJ”.** Kiểm tra dòng Boot trong `pom.xml`, Gradle plugins hoặc BOM trước, rồi chọn artifact ứng với dòng đang sử dụng.

## 6. Spring Framework thuần: khai báo tường minh Spring AOP và AspectJ

Nếu ứng dụng sử dụng Spring Framework trực tiếp, không có Spring Boot auto-configuration, cần có **classpath phù hợp** và **bật auto-proxy bằng cấu hình Spring**.

Ví dụ `build.gradle` dưới đây là một **bộ phiên bản mẫu xác định** để người học thử Spring 6.1 trên JDK 17, không phải tuyên bố đây là bản mới nhất:

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation platform('org.springframework:spring-framework-bom:6.1.14')

    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'com.example.aopdemo.DemoApp'
}
```

Ở đây:

- `spring-context` cung cấp `AnnotationConfigApplicationContext`, `@Configuration` và quản lý bean.
- `spring-aop` được liệt kê trực tiếp cho mục đích học tập, dù trong một số dependency graph nó đã tới qua `spring-context`; Gradle có thể hợp nhất dependency trùng module.
- `aspectjweaver` cho phép xử lý `@Aspect` và biểu thức pointcut. Nó có version mẫu minh bạch vì **Spring Framework BOM không mặc nhiên là bộ quản lý phiên bản cho mọi thư viện bên ngoài Spring**.
- `@EnableAspectJAutoProxy` là cấu hình cần có cho ví dụ Spring thuần sử dụng `@AspectJ`, khác với Spring Boot được auto-configure.

Với Maven Spring thuần, nguyên tắc cũng như vậy: quản lý version Spring qua BOM hoặc project parent, khai báo các dependency Spring cần thiết và `org.aspectj:aspectjweaver` có version tương thích. Không trộn tùy tiện Spring Framework 6 và 7 hoặc các version do những BOM cạnh tranh quản lý.

### Cấu hình bật proxy trong Spring thuần

```java
package com.example.aopdemo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfig {
    @Bean
    public TransferService transferService() {
        return new TransferService();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
```

Ở ví dụ này, `proxyTargetClass = true` dùng class-based proxy cho `TransferService` không triển khai interface; nếu bỏ tham số và bean có interface, chiến lược proxy có thể khác. `@Bean` bảo đảm bean aspect được đăng ký mà không yêu cầu `@ComponentScan`.

Tùy ứng dụng, XML tương đương cho kích hoạt kiểu `@AspectJ` là `<aop:aspectj-autoproxy />` trong namespace `aop`; nó cũng kích hoạt **Spring proxy auto-configuration**, không kích hoạt AspectJ weaving.

## 7. Ví dụ có thể chạy: kiểm chứng dependency thực sự hoạt động

Để dễ theo dõi cả nội dung dependency và đầu ra, ví dụ nhỏ sử dụng **Spring Boot 3.5.7, Java 17+**, có thể thay parent thành một bản Boot 3.x được dự án quản lý. Đây là một project Maven độc lập, không cần web server.

### Cấu trúc project

```text
spring-aop-dependencies/
├── pom.xml
└── src/main/java/com/example/aopdemo/
    ├── DemoApplication.java
    ├── TransferService.java
    └── TraceAspect.java
```

### `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>spring-aop-dependencies</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
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

### `src/main/java/com/example/aopdemo/DemoApplication.java`

```java
package com.example.aopdemo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(TransferService service) {
        return args -> service.transfer("TR-101");
    }
}
```

### `src/main/java/com/example/aopdemo/TransferService.java`

```java
package com.example.aopdemo;

import org.springframework.stereotype.Service;

@Service
public class TransferService {
    public void transfer(String transactionId) {
        System.out.println("[SERVICE] transfer " + transactionId);
    }
}
```

### `src/main/java/com/example/aopdemo/TraceAspect.java`

```java
package com.example.aopdemo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TraceAspect {
    @Before("execution(public * com.example.aopdemo.TransferService.transfer(..))")
    public void trace(JoinPoint joinPoint) {
        System.out.println("[AOP] before " + joinPoint.getSignature().getName());
    }
}
```

Chạy trong thư mục `spring-aop-dependencies` với JDK 17+ và Maven đã cài:

```bash
mvn spring-boot:run
```

Trong stdout sẽ có các dòng sau theo thứ tự (ngoài banner và log khởi động do Spring Boot sinh ra):

```text
[AOP] before transfer
[SERVICE] transfer TR-101
```

**Vì sao kết quả chứng minh đúng cấu hình?**

1. `spring-boot-starter-aop` cung cấp classpath phục vụ Spring AOP và hỗ trợ `@AspectJ`.
2. Spring Boot kích hoạt cấu hình proxy tương ứng; `TransferService` và `TraceAspect` đều là Spring beans do component scan nhận ra.
3. `@Before` khớp lời gọi từ `CommandLineRunner` đến phương thức `TransferService.transfer()` qua bean Spring, nên dòng `[AOP]` chạy trước dòng `[SERVICE]`.
4. Chương trình không cần Spring MVC, HTTP controller, AspectJ compiler hay weaving agent để minh họa cơ chế này.

Thí nghiệm đối chiếu: khi **tạm bỏ `@Aspect` hoặc bỏ bean aspect khỏi component scan**, code nghiệp vụ `[SERVICE]` vẫn chạy nhưng không còn dòng `[AOP]`. Nếu thay lời gọi `service.transfer(...)` bằng `new TransferService().transfer(...)`, lời gọi đến đối tượng được tạo ngoài container không đi qua proxy nên cũng không kích hoạt advice. Chỉ dùng thí nghiệm này để kiểm tra, rồi trả project về cấu hình ban đầu.

## 8. Khi nào cần `spring-aspects`, `spring-instrument` hoặc công cụ weaving?

Đây là nhánh nâng cao, **khác với setup Spring AOP thông thường**. Việc thêm `spring-aspects` hoặc agent không nên là hành động mặc định.

### 8.1. `spring-aspects` dành cho aspect có sẵn của Spring dùng weaving

`org.springframework:spring-aspects` chứa một số aspect tích hợp do Spring cung cấp, ví dụ hỗ trợ `@Configurable` cho đối tượng được tạo ngoài Spring container và một số mô hình transaction dựa trên AspectJ. Những trường hợp đó cần **cấu hình weaving thực sự** và cơ chế Spring phù hợp.

Không dùng `spring-aspects` để “sửa” vấn đề custom `@Aspect` không chạy. Trước tiên hãy kiểm tra bean registration, proxy, pointcut và đường gọi. Thêm jar `spring-aspects` riêng lẻ sẽ không tự biến custom aspect thành weaving.

### 8.2. `spring-instrument` dành cho đường Spring load-time weaving phù hợp

Khi chủ động chọn **load-time weaving**, có thể cần `org.springframework:spring-instrument` nếu dùng agent instrumentation do Spring cung cấp. Một số cấu hình lại dùng trực tiếp AspectJ weaving agent với `aspectjweaver`. Lựa chọn agent thuộc kiến trúc runtime, không phải bước thiết lập mặc định của Spring AOP.

Nếu chọn LTW, còn phải xem xét `META-INF/aop.xml`, classloader, cách gắn agent vào JVM và quy trình kiểm thử. **Classpath có `aspectjweaver` không đủ để khẳng định LTW đang hoạt động.**

### 8.3. Khi chỉ dùng Spring proxy, không cần `aspectjtools`

`org.aspectj:aspectjtools` chứa compiler `ajc`; đây là công cụ cho build/weaving của AspectJ. Một project chỉ dùng Spring AOP annotation và proxy không cần đưa `aspectjtools` vào runtime dependencies.

Trên một ứng dụng Spring, chỉ cân nhắc bộ công cụ weaving khi đã có yêu cầu thực tế như pointcut không thể biểu đạt bằng Spring proxy AOP, đối tượng phải được can thiệp ngoài container, hoặc cơ chế `@Configurable`/transaction weaving chuyên dụng.

## 9. Kiểm tra dependency graph và classpath thay vì đoán

### Maven

Tại root project:

```bash
mvn dependency:tree
```

Với **Boot 3.x**, kỳ vọng cây dependency có `spring-boot-starter-aop`, `spring-aop` và `aspectjweaver` thông qua các dependency trực tiếp hoặc bắc cầu.

Với **Boot 4.x**, tìm `spring-boot-starter-aspectj`, `spring-aop` và `aspectjweaver`.

Kiểm tra tiếp version được chọn khi thấy nhiều dependency cùng nhóm Spring:

```bash
mvn dependency:tree -Dverbose
```

Đầu ra chính xác tùy phiên bản Maven dependency plugin; `mvn dependency:tree` thường là đủ để xác định artifact có nằm trên cây classpath hay không.

### Gradle

Với Gradle Groovy hoặc Kotlin DSL:

```bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencyInsight --dependency spring-aop --configuration runtimeClasspath
./gradlew dependencyInsight --dependency aspectjweaver --configuration runtimeClasspath
```

Trên Windows PowerShell, thay `./gradlew` bằng `.\gradlew.bat`. `dependencyInsight` cho biết module được chọn theo đường dependency và cơ chế quản lý version nào.

**Đọc kết quả đúng cách:** thấy một artifact ở `compileClasspath` chưa chứng minh nó có mặt tại thời điểm ứng dụng chạy; cần kiểm tra **`runtimeClasspath`**, đặc biệt khi project có custom configurations, excludes hoặc executable packaging.

## 10. Lỗi phổ biến khi chọn thư viện Spring AOP

| Triệu chứng | Điều cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| Không tìm thấy package `org.aspectj.lang.annotation` khi biên dịch | Có thiếu `aspectjweaver`/dependency chứa API AspectJ trên compile classpath? | Thêm đúng starter theo Boot đang dùng hoặc khai báo AspectJ dependency cho Spring thuần; reload dependency. |
| Khởi động lỗi class liên quan `org.aspectj.weaver` hoặc pointcut parser | `aspectjweaver` có thực sự ở runtime classpath? | Kiểm tra tree/insight, exclusions và dependency scope; sửa đúng version do project quản lý. |
| `@Aspect` có nhưng advice không chạy | Aspect có là **Spring bean**? Đã bật auto-proxy với Spring thuần? Pointcut có khớp? Lời gọi đi qua proxy? | Kiểm tra lần lượt bean registration → enablement → pointcut → proxy boundary. |
| Boot 3 lên Boot 4 nhưng khai báo starter cũ | Artifact và version có thuộc cùng dòng Boot hiện tại? | Dùng starter và hướng dẫn chính thức của đúng dòng Boot. |
| Thêm `spring-aspects` nhưng custom advice vẫn không hoạt động | Có đang nhầm thư viện aspect weaving chuyên dụng với Spring proxy? | Quay về `spring-aop` + `aspectjweaver` và kiểm tra enablement/registration. |
| Thêm `aspectjweaver` mà self-invocation vẫn không được advice | Đang dùng Spring proxy hay AspectJ weaving thật sự? | Hiểu giới hạn Spring proxy, ưu tiên tách lời gọi sang bean khác khi phù hợp. |
| `NoSuchMethodError`, `ClassNotFoundException` sau nâng cấp | Có dependency Spring/AspectJ bị ép version không tương thích? | Đối chiếu BOM, dependency graph và loại bỏ override không cần thiết; không đoán nguyên nhân chỉ từ tên exception. |
| Advice chạy hai lần hoặc xuất hiện proxy ngoài dự kiến | Có nhiều cấu hình advisor, aspect hoặc proxy creator cùng tham gia? | Xem lại các bean `@Aspect`, cấu hình giao dịch/AOP, pointcut và thứ tự tạo proxy. |

**Phân biệt lỗi thư viện với lỗi kích hoạt:** nếu code `@Aspect` đã biên dịch, không thể kết luận ngay dependency đủ để advice chạy. Việc advice được thực thi còn cần bean aspect tồn tại trong context, Spring proxy được tạo, pointcut khớp và có lời gọi đi qua proxy.

## 11. Quy trình quyết định cho một project Spring đang có

1. **Xác định môi trường chạy:** Spring Boot 3.x, Boot 4.x, hay Spring Framework thuần. Kiểm tra parent/BOM/plugin mà project thực sự sử dụng.
2. **Xác định mô hình AOP:** Spring proxy `@Aspect` thường gặp, Spring AOP API tự dựng, hay AspectJ weaving được chọn có chủ đích.
3. **Chọn dependency tối thiểu:** Boot 3.x → `spring-boot-starter-aop`; Boot 4.x → `spring-boot-starter-aspectj`; Spring thuần + `@Aspect` → container, `spring-aop`, `aspectjweaver`.
4. **Để dependency management quản lý version:** giữ module Spring cùng hệ version được project/BOM công bố. Tránh trộn version Spring Framework 6/7 hoặc override AspectJ tùy tiện.
5. **Kích hoạt đúng hạ tầng:** Boot auto-configuration; Spring thuần dùng `@EnableAspectJAutoProxy` hay XML tương ứng.
6. **Đăng ký aspect thành Spring bean:** dùng `@Aspect` kết hợp `@Component`/component scan, hoặc `@Bean`.
7. **Kiểm tra runtime classpath và một lời gọi thật:** chạy ví dụ ngắn có thể đối chiếu stdout; nếu advice không chạy, lần lượt kiểm tra bean → pointcut → proxy thay vì thêm thư viện bừa.
8. **Chỉ mở rộng sang weaving nếu có nhu cầu:** ghi rõ cơ chế agent/compiler, thư viện bổ sung và kiểm thử riêng cho đường weaving.

### Nguồn chính thức để đối chiếu theo phiên bản

- Spring Framework — @AspectJ support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Using AspectJ with Spring Applications: https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html
- Spring Boot — Aspect-Oriented Programming: https://docs.spring.io/spring-boot/reference/features/aop.html
- Spring Boot 3.x — Build Systems/starters: https://docs.spring.io/spring-boot/3.4/reference/using/build-systems.html
- Spring Boot 4.x — Build Systems/starters: https://docs.spring.io/spring-boot/4.0/reference/using/build-systems.html

Với **Spring AOP áp dụng vào các bean thông thường**, kết quả thiết lập đúng được xác nhận bằng ba dấu hiệu: classpath chứa thư viện cần thiết, Spring đăng ký và proxy các bean phù hợp, và một lần gọi qua proxy thực sự chạy advice đúng yêu cầu. Thư viện chỉ là điều kiện đầu tiên; cấu hình Spring và đường gọi quyết định hành vi cuối cùng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-setup-002">Quản lý phiên bản phụ thuộc tương thích với Spring Framework</a>

<details>
<summary>Xem chi tiết</summary>

# Quản lý phiên bản phụ thuộc tương thích với Spring Framework khi thiết lập Spring AOP

## 1. Mục tiêu và phạm vi

Khi thiết lập **Spring AOP**, việc khai báo đúng các thư viện mới chỉ là bước đầu. Dự án còn phải dùng **các module Spring Framework tương thích với nhau**, phiên bản AspectJ phù hợp và cơ chế quản lý dependency đúng với cách dự án đang dùng Spring Framework thuần hay Spring Boot.

Bài này tập trung vào **phiên bản dependency phục vụ Spring AOP dựa trên proxy trong Spring Framework**, gồm cách chọn artifact, Maven BOM, Gradle platform, Spring Boot dependency management, kiểm tra dependency graph, xử lý xung đột và nâng cấp có kiểm chứng. Đây là kiến thức cấu hình Spring AOP; không đi sâu vào lý thuyết Aspect chung hoặc cấu hình compiler/weaving của AspectJ.

Sau bài học, bạn có thể:

1. Xác định dependency tối thiểu để dùng `@Aspect` và `@EnableAspectJAutoProxy` trong Spring Framework.
2. Giữ các artifact `org.springframework` cùng một phiên bản thông qua BOM hoặc Spring Boot.
3. Phân biệt dependency giữa **Spring Boot 3.x** và **Spring Boot 4.x** khi bật AOP.
4. Đọc dependency tree để tìm các phiên bản bị kéo vào ngoài ý muốn và sửa ở nơi quản lý phiên bản.
5. Chứng minh bằng một chương trình chạy được rằng các dependency thực sự cho phép advice hoạt động.

## 2. Xác định ai quản lý phiên bản của các artifact AOP

Khi dự án dùng Spring AOP với `@Aspect`, ba thành phần chính thường là **`spring-context`** (container), **`spring-aop`** (hạ tầng proxy) và **`aspectjweaver`** (kiểu annotation, phân tích và đối sánh pointcut `@AspectJ`). Các dependency có thể đi qua starter hoặc dependency bắc cầu; điều phải kiểm tra là **phiên bản thực sự được resolve** trên runtime classpath.

| Nhóm phụ thuộc | Nguồn quản lý phiên bản trong Spring Framework thuần | Nguồn quản lý phiên bản trong Spring Boot |
| --- | --- | --- |
| `org.springframework:spring-context`, `spring-aop` cùng `spring-core`, `spring-beans`, `spring-expression` | Một `spring-framework-bom` thống nhất | Boot parent/BOM đồng bộ các module Spring |
| `org.aspectj:aspectjweaver` | Khai báo phiên bản AspectJ tương thích riêng và kiểm chứng | Boot BOM quản lý phiên bản thông qua starter AOP/AspectJ |
| `org.springframework:spring-aspects` (chỉ dùng cho tích hợp weaving chuyên biệt) | Spring Framework BOM, nếu thực sự có nhu cầu | Boot BOM, nếu được sử dụng |
| `org.aspectj:aspectjtools` (chỉ dùng khi biên dịch/weaving bằng AspectJ) | Version/toolchain của AspectJ, không thuộc Spring Framework BOM | Không cần cho Spring proxy AOP thông thường |

**Điểm mấu chốt của bài quản lý phiên bản:** các module `org.springframework:spring-*` phải đồng bộ theo release Spring Framework, nhưng AspectJ có chu kỳ phát hành và số hiệu riêng. Ví dụ `spring-aop:7.0.9` đi cùng `aspectjweaver:1.9.25.1` là bình thường khi BOM quản lý tổ hợp đó. Sự có mặt của `aspectjweaver` **không tự kích hoạt AspectJ weaving**; ứng dụng bật `@EnableAspectJAutoProxy` vẫn dùng Spring proxy AOP.


## 3. Nguyên tắc chọn phiên bản

### 3.1. Chọn nền tảng trước, sau đó để nền tảng quản lý dependency

- **Ứng dụng Spring Boot:** chọn phiên bản **Spring Boot** phù hợp dự án, để **Spring Boot parent/BOM và plugin quản lý các phiên bản Spring Framework, AspectJ**. Thường không tự chèn `<version>` hoặc `:version` cho từng module AOP.
- **Ứng dụng Spring Framework thuần:** chọn **một phiên bản Spring Framework**, import `org.springframework:spring-framework-bom`, khai báo các module `org.springframework` **không ghi version riêng**; quản lý `org.aspectj:aspectjweaver` bằng phiên bản độc lập phù hợp.
- **Dự án có nhiều module:** quản lý BOM/constraints tại một vị trí thống nhất và kiểm tra resolved dependencies trong từng module chạy thực tế.
- **Khi nâng cấp:** cập nhật một nền tảng rồi chạy biên dịch, kiểm thử ứng dụng và kiểm tra dependency tree; không nâng riêng `spring-aop` lên phiên bản khác với `spring-context`.

### 3.2. Ví dụ những tổ hợp đã được công bố

Bảng minh họa các phiên bản được khai báo trong tài liệu/metadata phát hành. Đây **không phải** quy tắc dự đoán phiên bản tương lai; trong dự án thực tế cần đối chiếu BOM đang dùng.

| Nền tảng | Phiên bản Spring Framework do nền tảng quản lý | Starter Spring AOP | AspectJ Weaver do nền tảng quản lý |
| --- | --- | --- | --- |
| Spring Boot **3.5.16** | **6.2.19** | `org.springframework.boot:spring-boot-starter-aop` | **1.9.25.1** |
| Spring Boot **4.0.8** | **7.0.9** | `org.springframework.boot:spring-boot-starter-aspectj` | Do Boot 4.0.8 BOM quản lý; xem effective dependency graph |

Với Spring Boot **4.0**, starter `spring-boot-starter-aop` được **đổi tên** thành `spring-boot-starter-aspectj`. Không sao chép nguyên dependency của Boot 3 sang dự án Boot 4 rồi giả định artifact cũ vẫn được BOM quản lý. Tên mới **không có nghĩa** mỗi ứng dụng phải cấu hình AspectJ bytecode weaving: starter vẫn cung cấp `spring-aop` và `aspectjweaver` để Spring dùng hỗ trợ `@AspectJ`.

Spring Framework **6.x và 7.x** đều yêu cầu tối thiểu **Java 17**, nhưng Spring 7 còn nâng baseline của một số công nghệ Jakarta liên quan; khi nâng từ Boot 3 sang Boot 4 cần kiểm tra **cả ứng dụng**, không chỉ AOP. Các phiên bản liệt kê dùng để học cách quản lý dependency; không nên nâng cấp theo bảng mà bỏ qua trạng thái hỗ trợ của phiên bản và nhu cầu dự án.

**Nguyên tắc tương thích quan trọng:** Các dependency `org.springframework:spring-*` trong *một ứng dụng* thường nên được căn chỉnh theo **đúng cùng phiên bản release**, ví dụ toàn bộ Spring Framework **7.0.9**. Việc thấy `spring-context:7.0.9` cùng `spring-aop:6.2.19` là dấu hiệu phải kiểm tra, kể cả khi mã vẫn biên dịch.

## 4. Spring Framework thuần với Maven BOM

Khi không dùng Spring Boot, cách tránh lệch phiên bản là nhập **Spring Framework BOM** trong phần `dependencyManagement`. Ví dụ chạy với **Java 17+**, Spring Framework **7.0.9** và AspectJ Weaver **1.9.25.1**:

**`pom.xml` đầy đủ**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aop-version-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework</groupId>
                <artifactId>spring-framework-bom</artifactId>
                <version>7.0.9</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.25.1</version>
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
            </plugin>
        </plugins>
    </build>
</project>
```

Ở đây, cả `spring-context` và `spring-aop` đều không cần ghi version vì BOM quản lý chúng. Các dependency Spring chuyển tiếp như `spring-core`, `spring-beans` cũng được căn chỉnh theo BOM. `aspectjweaver` nằm trong **group khác**, nên Spring Framework BOM không được xem là nguồn xác định version của thư viện này; ta chọn version rõ ràng và kiểm tra tương thích.

### 4.1. Chương trình để kiểm tra thiết lập AOP

Thư mục dự án:

```text
spring-aop-version-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── MessageService.java
    ├── AuditAspect.java
    └── Main.java
```

**`MessageService.java`**

```java
package example;

public class MessageService {
    public void send() {
        System.out.println("Service: send");
    }
}
```

**`AuditAspect.java`**

```java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* example.MessageService.send(..))")
    public void beforeSend() {
        System.out.println("Aspect: before");
    }
}
```

**`AppConfig.java`**

```java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class AppConfig {
    @Bean
    public MessageService messageService() {
        return new MessageService();
    }

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
```

**`Main.java`**

```java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            MessageService service = context.getBean(MessageService.class);
            System.out.println("AOP proxy: " + AopUtils.isAopProxy(service));
            service.send();
        }
    }
}
```

Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
```

Các dòng do `Main` và các phương thức ví dụ in ra, theo thứ tự:

```text
AOP proxy: true
Aspect: before
Service: send
```

**Vì sao ví dụ chứng minh dependency tương thích?** Nếu Maven resolve được các artifact, biên dịch thành công, context khởi động, tự tạo AOP proxy và advice chạy đúng thì đường đi `spring-context` → `spring-aop` → `aspectjweaver` đã hoạt động cho trường hợp này. Kết quả đó **không thay thế** kiểm thử hồi quy toàn ứng dụng sau nâng cấp.

Lưu ý: `@EnableAspectJAutoProxy` chỉ bật xử lý proxy cho context tương ứng; `AuditAspect` được đăng ký qua `@Bean`. Nếu có thư viện trên classpath nhưng thiếu bước bật AOP hoặc chưa đăng ký aspect làm bean thì việc resolve dependency thành công **không tự khiến advice chạy**.

### 4.2. Nếu dùng Gradle thay Maven

Trong dự án Java dùng **Gradle Groovy DSL**, ví dụ nội dung `build.gradle` tương ứng:

```groovy
plugins {
    id 'java'
    id 'application'
}

group = 'example'
version = '1.0.0'

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation platform('org.springframework:spring-framework-bom:7.0.9')
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver:1.9.25.1'
}

application {
    mainClass = 'example.Main'
}
```

Đặt bốn file Java ở cùng đường dẫn như mục 4.1, sau đó chạy:

```bash
./gradlew run
```

Trên Windows PowerShell dùng `./gradlew.bat run`; yêu cầu project có Gradle Wrapper. Kết quả nghiệp vụ vẫn là ba dòng của ví dụ. Với Gradle, `platform(...)` cung cấp **version constraints**, nhưng resolution có thể chọn version cao hơn khi dependency khác đưa vào yêu cầu mạnh hơn; vì vậy **vẫn phải kiểm tra phiên bản thực tế** sau khi resolve. Tránh ép `enforcedPlatform` bừa bãi lên một library được phân phối cho ứng dụng khác.

## 5. Ứng dụng Spring Boot: ưu tiên starter và BOM của Boot

### 5.1. Spring Boot 4.x (ví dụ Maven Boot 4.0.8)

Đây là cấu hình Maven tối giản cho một ứng dụng Boot 4 sử dụng `@Aspect`. Parent đã quản lý version của các dependency cần thiết:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.8</version>
        <relativePath/>
    </parent>

    <groupId>example</groupId>
    <artifactId>boot-aop-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aspectj</artifactId>
        </dependency>
    </dependencies>
</project>
```

Ứng dụng Boot thật vẫn cần các starter khác theo chức năng (web, data, test, v.v.). Điểm cần học ở đây là **cách khai báo AOP và ai sở hữu phiên bản**:

- Parent `spring-boot-starter-parent:4.0.8` dùng dependency management của Boot 4.0.8.
- Starter `spring-boot-starter-aspectj` mang vào `spring-aop` và `aspectjweaver` phù hợp với nền tảng.
- Không chèn `spring-aop:6.x` hoặc `aspectjweaver` phiên bản tự chọn vào ví dụ Boot 4 khi chưa có lý do đã kiểm chứng.
- Spring Boot có auto-configuration hỗ trợ AOP; với AspectJ trên classpath, thông thường **không cần viết thêm `@EnableAspectJAutoProxy`**. Cần đăng ký lớp `@Aspect` thành Spring bean qua `@Component` hoặc `@Bean`.

Nếu dự án **Gradle Boot 4** đã cấu hình Spring Boot plugin và dependency management/BOM chuẩn, phần AOP thường chỉ cần:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aspectj'
}
```

Chỉ bỏ version của starter khi **BOM thực sự đang được áp dụng**; riêng khai báo plugin Spring Boot trong một build tùy biến chưa luôn đồng nghĩa dependency management đã được cấu hình đúng. Kiểm tra resolved graph.

### 5.2. Spring Boot 3.x (ví dụ Boot 3.5.16)

Với ứng dụng đang sử dụng Spring Boot 3, dependency được quản lý bằng Boot 3 parent/BOM và starter có tên cũ:

```xml
<!-- Bên trong <dependencies> của dự án sử dụng Boot 3.5.x BOM/parent -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Bản tương ứng của Gradle Boot 3 (sau khi đã áp dụng Boot BOM):

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

**Không trộn hai thế hệ:** Boot **3.5.16** quản lý Spring Framework **6.2.19**, còn Boot **4.0.8** quản lý Spring Framework **7.0.9**. Không sao chép nguyên cấu hình Spring Framework 7 vào dự án Boot 3 để “chỉ cập nhật AOP”. Khi chuyển Boot 3 → Boot 4, thực hiện kế hoạch nâng cấp framework toàn ứng dụng và đổi starter AOP tương ứng.

### 5.3. Chỉ dùng Spring Boot BOM, không dùng parent

Trong dự án Maven có parent riêng, import BOM của Boot:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>4.0.8</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Sau đó thêm `spring-boot-starter-aspectj` **không ghi version** vào `<dependencies>`. Import BOM **chỉ quản lý phiên bản**, không tự cung cấp plugin biên dịch/chạy, các mặc định của Boot parent, hoặc tạo ra dependency ứng dụng nếu chưa khai báo.

Trong Gradle, có thể dùng `platform('org.springframework.boot:spring-boot-dependencies:4.0.8')` trong `dependencies` để quản lý phiên bản cho các dependency liên quan. Dự án dùng plugin `io.spring.dependency-management` có cú pháp import BOM riêng; không chồng nhiều cách quản lý khi chưa hiểu thứ tự ưu tiên và kết quả resolved.

## 6. Kiểm tra phiên bản **đã resolve**, không chỉ phiên bản đã khai báo

Một file `pom.xml` hoặc `build.gradle` có vẻ đúng vẫn có thể gặp xung đột bởi dependency transitives, parent/BOM khác, constraints, explicit version, plugin hoặc module con.

### 6.1. Maven

Chạy tại đúng Maven module chứa Spring AOP:

```bash
mvn dependency:tree
mvn help:effective-pom
```

Tìm các mục:

```text
org.springframework:spring-aop
org.springframework:spring-context
org.springframework:spring-core
org.springframework:spring-beans
org.aspectj:aspectjweaver
```

Kiểm tra rằng các artifact Spring Framework được resolve **cùng phiên bản** (trong ví dụ Maven thuần là `7.0.9`), và `aspectjweaver` thực sự có trên classpath. Nếu nghi ngờ BOM không được áp dụng, xem `effective-pom` và phần `dependencyManagement`. Chú ý những phiên bản đã bị Maven bỏ qua do version mediation; nhìn riêng dependency trực tiếp chưa đủ.

### 6.2. Gradle

Chạy ở module ứng dụng, ví dụ:

```bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencyInsight --dependency spring-aop --configuration runtimeClasspath
./gradlew dependencyInsight --dependency spring-context --configuration runtimeClasspath
./gradlew dependencyInsight --dependency aspectjweaver --configuration runtimeClasspath
```

Trên Windows dùng `./gradlew.bat` hoặc chạy task với đường dẫn module, ví dụ `:app:dependencyInsight`, khi `app` là Gradle subproject ứng dụng.

Ví dụ ý nghĩa của kết quả giả định:

```text
org.springframework:spring-aop:6.2.19 -> 7.0.9
```

Ký hiệu `->` trong cây phụ thuộc Gradle thể hiện **phiên bản yêu cầu được thay bằng phiên bản resolve**. Hãy tìm **lý do chọn phiên bản** qua `dependencyInsight`; không xem chuỗi này là bằng chứng lỗi tự động nếu tất cả module Spring cuối cùng đã được căn chỉnh và tổ hợp đó được hỗ trợ.

### 6.3. Kiểm tra sự hiện diện và vận hành của AOP

Dù graph hợp lệ, hãy thử `AopUtils.isAopProxy(bean)` trên bean phù hợp hoặc tạo một kiểm thử gọi phương thức có advice. Nếu chương trình lỗi `ClassNotFoundException`, `NoClassDefFoundError` hoặc `NoSuchMethodError`, kiểm tra **class nào đang thiếu**, version thực tế, classpath runtime và bước đóng gói. Các lỗi này **có thể** bắt nguồn từ lệch dependency, nhưng không nên gán nguyên nhân chỉ từ tên exception.

## 7. Các lỗi cấu hình thường gặp và cách sửa

| Hiện tượng | Nguyên nhân đáng kiểm tra | Hướng xử lý |
| --- | --- | --- |
| `NoClassDefFoundError` liên quan `org.aspectj.*` | Thiếu `aspectjweaver` trên classpath lúc chạy, scope sai hoặc packaging loại bỏ dependency | Thêm starter đúng thế hệ Boot hoặc dependency AspectJ trong ứng dụng Spring thuần; kiểm tra runtimeClasspath |
| `NoSuchMethodError` xuất hiện sau khi nâng Spring | Nhiều phiên bản `spring-*` bị trộn hoặc thư viện thứ ba chưa tương thích | Đối chiếu dependency tree; căn chỉnh Spring BOM/nền tảng; kiểm tra lại thư viện tích hợp |
| Maven báo thiếu version cho `spring-boot-starter-aop` trên Boot 4 | Đang dùng artifact cũ không được Boot 4 BOM quản lý | Chuyển sang `spring-boot-starter-aspectj` khi áp dụng Boot 4 BOM |
| Spring context chạy nhưng `@Before` không thực thi | Aspect chưa thành bean, AOP chưa bật, pointcut không khớp hoặc lời gọi bỏ qua proxy | Kiểm tra khai báo aspect, `@EnableAspectJAutoProxy`/Boot auto-configuration, proxy và đường gọi |
| Cùng lúc thấy Spring 6 và Spring 7 trong dependency graph | Override trực tiếp, parent/BOM khác nhau, dependency transitives hoặc resolution rule | Chọn một nền tảng nhất quán, bỏ version override thừa và xem effective graph |
| Build compile được nhưng runtime lỗi trên JDK khác | Baseline Java/bytecode của framework và library không phù hợp môi trường chạy | Kiểm tra `java -version`, cấu hình compiler/toolchain và JVM chạy thực tế |

**Ba sai lầm cần tránh:**

1. **Viết version cho mọi dependency:** dễ dẫn đến `spring-aop`, `spring-context` và các Spring modules khác lệch release. Sử dụng BOM/parent trước.
2. **Cho rằng thêm `spring-aspects` thay thế `spring-aop`:** chúng có vai trò khác nhau; proxy-based Spring AOP không cần spring-aspects mặc định.
3. **Nâng AspectJ hoặc Spring AOP độc lập với nền tảng:** không kết luận tương thích chỉ từ việc Maven/Gradle tải artifact thành công.

## 8. Quy trình nâng cấp Spring AOP an toàn trong dự án

1. **Xác định nguồn quản lý version:** Spring Boot parent/BOM, Spring Framework BOM, Gradle platform, plugin dependency management, corporate parent hoặc version catalog.
2. **Ghi lại graph hiện tại:** xem version của `spring-aop`, `spring-context`, `spring-core`, `spring-beans`, `aspectjweaver`, JDK; nếu multi-module, kiểm tra module chạy.
3. **Chọn một bộ phiên bản được hỗ trợ:** nâng Boot như một nền tảng hoàn chỉnh, hoặc thay một Spring Framework BOM thống nhất khi không dùng Boot; xác minh baseline JDK.
4. **Giữ dependency declaration đơn giản:** xóa override phiên bản không cần thiết; chọn đúng starter theo Boot 3 hay Boot 4.
5. **Resolve và đối chiếu lại:** Maven `dependency:tree`/`help:effective-pom` hoặc Gradle `dependencies`/`dependencyInsight`. Không chỉ dựa vào nội dung file build.
6. **Chạy kiểm thử Spring AOP:** ít nhất một bean có `@Aspect` match và một lời gọi qua proxy; chạy thêm integration tests của ứng dụng để phát hiện thay đổi tương thích.
7. **Chỉ nâng cấp xong khi build và runtime đều đạt:** đảm bảo artifact đóng gói chứa dependency cần thiết và môi trường triển khai dùng Java phù hợp.

### Bài tập vận dụng

Dự án có Boot `4.0.8`, khai báo đồng thời `spring-boot-starter-aop`, `spring-aop:6.2.19` và `aspectjweaver:1.9.25.1`. Hãy xác định điều cần sửa.

**Đáp án:** Dùng starter `spring-boot-starter-aspectj` của Boot 4; bỏ khai báo version `spring-aop:6.2.19` vì Boot 4.0.8 quản lý Spring Framework `7.0.9`; bỏ khai báo `aspectjweaver` thủ công nếu starter đã cung cấp và không có nhu cầu đặc biệt. Cuối cùng, kiểm tra Maven/Gradle resolved graph và chạy test `@Aspect` thực tế. Việc ghim `aspectjweaver:1.9.25.1` có thể trùng version mà BOM quản lý, nhưng tạo thêm một điểm override dễ gây lệch khi nâng Boot về sau.

## 9. Tài liệu gốc để đối chiếu phiên bản

- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): cấu hình Spring proxy và yêu cầu AspectJ trên classpath.
- [Spring Framework — Spring Framework Artifacts](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Artifacts): artifact, dependency và override theo phiên bản.
- [Spring Framework BOM trên Maven Central](https://central.sonatype.com/artifact/org.springframework/spring-framework-bom): xem release BOM thực tế.
- [Spring Boot 3.5 — Managed Dependency Coordinates](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html): Spring và AspectJ được Boot 3.5 quản lý.
- [Spring Boot 4.0 — Managed Dependency Coordinates](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html): Spring và starter được Boot 4.0 quản lý.
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide): thay đổi tên starter AOP/AspectJ.
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html): auto-configuration Spring AOP và proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-setup-003">Bật hỗ trợ @AspectJ bằng @EnableAspectJAutoProxy</a>

<details>
<summary>Xem chi tiết</summary>

# Bật hỗ trợ @AspectJ bằng @EnableAspectJAutoProxy trong Spring

## 1. Mục đích của cấu hình

Khi dùng **Spring Framework** với Java configuration, annotation `@EnableAspectJAutoProxy` yêu cầu Spring nhận diện các **Spring bean** được đánh dấu `@Aspect`, chuyển khai báo pointcut/advice thành advisor của Spring AOP và **tự tạo proxy** cho các bean có method được advisor áp dụng.

Đây là bước thiết lập hạ tầng **Spring AOP sử dụng cú pháp @AspectJ**. Chữ “AspectJ” trong tên annotation nói về **kiểu khai báo aspect**, không có nghĩa ứng dụng tự động dùng AspectJ compile-time weaving hoặc load-time weaving. Khi được bật theo cách này, cơ chế chặn thông thường vẫn là **Spring proxy tại các lời gọi method của Spring-managed bean**.

Ví dụ tối thiểu:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}
```

Đây là **đoạn cấu hình**, chỉ phát huy tác dụng khi `AopConfig` được đăng ký trong một `ApplicationContext` phù hợp; riêng annotation này chưa tạo ra lời gọi advice nếu không có `@Aspect` bean và target bean/pointcut khớp.

**Khi nào cần học cách bật thủ công?** Chủ yếu trong ứng dụng Spring Framework tự cấu hình, những module dùng `AnnotationConfigApplicationContext`, các context con có cấu hình độc lập, hoặc khi cần hiểu rõ kiểu proxy. Với dự án **Spring Boot đã bật AOP auto-configuration**, thông thường không cần lặp lại annotation này; xem mục 7.

## 2. Điều kiện tiên quyết để cấu hình thực sự hoạt động

Để `@EnableAspectJAutoProxy` có tác dụng, phải đồng thời có:

1. **Spring context xử lý annotation cấu hình:** class mang `@Configuration` và `@EnableAspectJAutoProxy` được đăng ký, chẳng hạn `new AnnotationConfigApplicationContext(AopConfig.class)`. Đặt annotation trên class bất kỳ không được Spring nạp sẽ không thay đổi container.
2. **Hạ tầng Spring AOP và thư viện AspectJ pointcut:** phải có `spring-aop` và `org.aspectj:aspectjweaver` tương thích trên classpath. `aspectjweaver` được Spring dùng cho biểu thức pointcut của `@AspectJ`; không cần khởi động JVM với `-javaagent` để chạy ví dụ proxy.
3. **Có aspect được khai báo dưới dạng Spring bean:** `@Aspect` mô tả class là aspect, **không tự làm class đó được component scan đăng ký**. Dùng `@Aspect` + `@Component` (khi có `@ComponentScan` phù hợp) hoặc một `@Bean` trả về instance aspect.
4. **Có target Spring bean:** class nghiệp vụ do container quản lý, không phải object tạo bằng `new` từ code ngoài container.
5. **Pointcut khớp method và lời gọi đi qua proxy:** advice chỉ thực thi nếu bean/method khớp và caller thực sự gọi đến proxy được Spring trả về.

Ví dụ hai cách đăng ký **aspect**, chọn **một** cách cho một instance:

```java
// Cách A: component scan tìm class có cả @Aspect và @Component
@Aspect
@Component
public class LoggingAspect {
    // advice
}
```

```java
// Cách B: đăng ký explicit, cho phép không gắn @Component
@Configuration
@EnableAspectJAutoProxy
class AopConfig {
    @Bean
    LoggingAspect loggingAspect() {
        return new LoggingAspect();
    }
}
```

Các đoạn trên minh họa cách đăng ký, chưa phải chương trình độc lập. Nếu đã đăng ký `LoggingAspect` bằng component scan thì không thêm `@Bean` trùng lặp một cách vô ý. **Không dùng `@Aspect` đơn độc thay cho `@Component`** trong cấu hình component-scan.

## 3. `@EnableAspectJAutoProxy` làm gì trong Spring container?

Khi `ApplicationContext` đọc class `@Configuration`, `@EnableAspectJAutoProxy` import `AspectJAutoProxyRegistrar` để đăng ký cơ chế auto-proxy phù hợp (thường là `AnnotationAwareAspectJAutoProxyCreator`). Spring dùng hạ tầng bean post-processing để xem xét các bean và các advisor xuất phát từ Spring `@Aspect`.

Tổng quan:

```text
@Configuration + @EnableAspectJAutoProxy
             |
             v
Spring đăng ký @AspectJ auto-proxy infrastructure
             |
     Đăng ký @Aspect beans
             |
             v
Tạo / khởi tạo Spring target beans
             |
             v
Xét advisor + pointcut đối với target bean
             |
     +-------+-----------------+
     |                         |
Có advice phù hợp         Không có advice phù hợp
     |                         |
     v                         v
Trả bean dưới dạng proxy   Bean thông thường
     |
Caller gọi method qua proxy
     |
Advice -> target method -> advice/kết quả
```

Các ý nghĩa kỹ thuật cần phân biệt:

- Annotation **bật hạ tầng**; nó **không biến mọi bean thành proxy** và không làm mọi method đều có advice. Điều kiện match còn phụ thuộc pointcut và target.
- Aspect phải là **Spring-managed bean**; một class chỉ có `@Aspect` ngoài context sẽ không được auto-proxy infrastructure sử dụng.
- Việc proxy được tạo diễn ra trong lifecycle container, nên tránh coi phương thức gọi từ constructor hay logic khởi tạo bean là lời gọi được bảo đảm intercept. Tập trung vào lời gọi qua reference bean đã được khởi tạo và inject/tra cứu từ context.
- Khi target được proxy, **lời gọi nội bộ kiểu `this.otherMethod()` không đi vòng qua proxy**, nên advice gắn ở `otherMethod()` có thể không chạy cho đường gọi ấy.
- `@EnableAspectJAutoProxy` tự nó **không bật** `@Transactional`, `@Async`, method security hay cache annotations; các khả năng đó có cơ chế kích hoạt riêng. Chúng có thể cùng tồn tại và dùng cơ sở proxy của Spring.

### Khác biệt với AspectJ weaving

| Câu hỏi | Bật Spring `@AspectJ` bằng `@EnableAspectJAutoProxy` |
| --- | --- |
| Cơ chế chặn | Spring AOP tạo JDK proxy hoặc subclass proxy |
| Đối tượng áp dụng | Bean do Spring quản lý, với method call đi qua proxy |
| Chặn constructor/field access? | Không, nằm ngoài khả năng proxy-based Spring AOP |
| Chặn `this.method()` nội bộ? | Không; lời gọi không quay qua proxy |
| Có cần AspectJ compiler/agent? | Không; cần thư viện hỗ trợ khai báo/pointcut (`aspectjweaver`) trên classpath |
| Có tự bật `@Aspect` ngoài container? | Không; vẫn phải đăng ký bean |

Nếu mục tiêu cần bytecode weaving, cấu hình của bài này là chưa đủ. Đó là một lựa chọn kỹ thuật khác, cần thiết lập riêng.

## 4. Hai tham số quan trọng của annotation

`@EnableAspectJAutoProxy` có hai thuộc tính boolean:

```java
@Configuration
@EnableAspectJAutoProxy(
    proxyTargetClass = true,
    exposeProxy = false
)
public class AopConfig {
}
```

### `proxyTargetClass`

Mặc định của chính `@EnableAspectJAutoProxy` là `false`. Đây là lựa chọn ưu tiên proxy qua interface khi có interface phù hợp; nếu target không có interface để proxy, Spring vẫn có thể phải dùng subclass proxy.

| Giá trị | Cách hiểu | Ảnh hưởng thực tế |
| --- | --- | --- |
| `proxyTargetClass = false` (mặc định của annotation) | Ưu tiên JDK dynamic proxy khi có interface phù hợp | Caller thường sử dụng kiểu interface mà proxy triển khai; các method ngoài interface không được đưa ra qua JDK proxy. |
| `proxyTargetClass = true` | Yêu cầu proxy dựa trên class (Spring dùng CGLIB) | Có thể inject/gọi theo kiểu class nhưng vẫn chịu giới hạn class/method `final`, method `private` và khả năng subclassing. |

Mẫu:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class ClassProxyConfig {
}
```

Điều này **không** sửa được self-invocation. Không lạm dụng proxy dựa trên class khi muốn chặn `private` hoặc `final`: subclass không thể override những method đó. Một số hạn chế JVM/module path cũng có thể ảnh hưởng khả năng tạo subclass proxy.

**Tương tác với cấu hình khác:** trong cùng context, nhiều phần của Spring cùng cấu hình auto-proxy (ví dụ `@EnableTransactionManagement`) có thể hợp nhất thành một auto-proxy creator và dùng thiết lập proxy mạnh hơn. Trong Spring Boot, cấu hình mặc định proxy target class có thể khác mặc định của annotation. Vì vậy, kết luận loại proxy dựa trên **cấu hình thực tế hoặc kiểm tra runtime**, không dựa vào một annotation đơn lẻ.

### `exposeProxy`

Mặc định `false`: Spring không công khai proxy hiện tại qua `AopContext.currentProxy()` để code nghiệp vụ truy cập. Nếu đặt:

```java
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
class ExposedProxyConfig {
}
```

Spring có thể cung cấp proxy hiện tại trong **ngữ cảnh một lời gọi đang đi qua proxy** bằng một `ThreadLocal` của Spring AOP. Đây là cấu hình nâng cao, dùng khi đã hiểu sự phụ thuộc vào context proxy; **không phải điều kiện để advice thông thường hoạt động**.

Ví dụ phương thức tự gọi cần nhận interception:

```java
// Minh họa cách truy cập proxy đã expose; không phải file Java độc lập
((MyService) AopContext.currentProxy()).otherMethod();
```

Đoạn trên còn đòi hỏi import `org.springframework.aop.framework.AopContext`, `MyService` là kiểu có thể truy cập qua proxy, `exposeProxy=true` và đang ở trong lời gọi qua proxy. Ngoài ngữ cảnh ấy, `currentProxy()` có thể ném `IllegalStateException`. Cách thiết kế thường dễ bảo trì hơn là **tách method được intercept sang một Spring bean khác rồi inject**, tạo ranh giới proxy tường minh. ThreadLocal này cũng không tự truyền xuyên thread/task bất đồng bộ.

## 5. Demo Spring Framework thuần: bật @AspectJ bằng annotation

Ví dụ sau chạy được với **Java 17+**, Spring Framework **6.2.19**, Gradle và AspectJ Weaver **1.9.25.1**. Nó chứng minh ba điều: bật hạ tầng bằng `@EnableAspectJAutoProxy`, Spring tạo proxy cho target phù hợp và self-invocation bỏ qua proxy.

### 5.1. Cấu trúc tệp

```text
aspectj-enable-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/lesson/
    ├── Main.java
    ├── AppConfig.java
    ├── BillingService.java
    └── AuditAspect.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'aspectj-enable-demo'
```

**`build.gradle`**

```gradle
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.19'
    implementation 'org.springframework:spring-aop:6.2.19'
    implementation 'org.aspectj:aspectjweaver:1.9.25.1'
}

application {
    mainClass = 'com.example.lesson.Main'
}
```

Đây là ứng dụng Spring Framework độc lập, **không cần Spring Boot**. Một dự án Spring Boot nên để BOM/dependency management của Boot quản lý versions thay vì copy versions cố định này sang project khác.

### 5.2. Tạo Spring configuration

**`AppConfig.java`**

```java
package com.example.lesson;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = Main.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
}
```

`@ComponentScan` tìm `BillingService` và `AuditAspect` trong cùng package. `proxyTargetClass=true` làm ví dụ xác định rõ class proxy, vì `BillingService` không định nghĩa interface. Có thể bỏ tham số trong trường hợp này và Spring vẫn có thể chọn class proxy.

### 5.3. Tạo service làm target

**`BillingService.java`**

```java
package com.example.lesson;

import org.springframework.stereotype.Service;

@Service
public class BillingService {
    public String charge(String orderId) {
        return "charged:" + orderId;
    }

    public String internalCharge(String orderId) {
        // Lời gọi trên chính target, không qua Spring proxy của charge().
        return charge(orderId);
    }
}
```

### 5.4. Tạo Spring-managed aspect

**`AuditAspect.java`**

```java
package com.example.lesson;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    @Before("execution(* com.example.lesson.BillingService.charge(..))")
    public void beforeCharge(JoinPoint joinPoint) {
        System.out.println("intercepted:" + joinPoint.getSignature().getName());
    }
}
```

`@Aspect` đánh dấu class cung cấp advice; `@Component` đưa nó vào component scan. Pointcut nhắm **duy nhất method `BillingService.charge(..)`**. `@Before` in một dòng khi lời gọi đi qua proxy đến đúng method.

### 5.5. Bootstrapping và gọi service qua context

**`Main.java`**

```java
package com.example.lesson;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            BillingService service = context.getBean(BillingService.class);

            System.out.println("isProxy=" + AopUtils.isAopProxy(service));
            System.out.println(service.charge("A-1"));
            System.out.println(service.internalCharge("B-2"));
        }
    }
}
```

Từ thư mục project, chạy bằng Gradle đã cài:

```bash
gradle run --quiet
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows.

**Ba lời gọi print và một advice tạo đúng bốn dòng sau** (không tính cảnh báo log từ JVM, Gradle hoặc môi trường):

```text
isProxy=true
intercepted:charge
charged:A-1
charged:B-2
```

Giải thích từng dòng:

1. `isProxy=true`: context đã trả về một Spring AOP proxy của `BillingService`, vì bean này có method khớp advisor.
2. `intercepted:charge`: `service.charge("A-1")` gọi từ `Main` vào Spring bean **qua proxy**, nên `@Before` chạy.
3. `charged:A-1`: lời gọi gốc vẫn thực thi và trả về chuỗi từ target.
4. `charged:B-2`: `service.internalCharge("B-2")` đi qua proxy ở entry method nhưng bên trong gọi `this.charge(..)` trực tiếp trên target, nên **không có dòng `intercepted:charge` thứ hai**.

**Đối chứng hợp lệ:** thử bỏ `@EnableAspectJAutoProxy` khỏi `AppConfig` trong project Spring Framework thuần này khi không có cấu hình AOP khác; aspect có thể vẫn là bean, nhưng không được xử lý thành advice của auto-proxy creator. Kết quả ứng dụng lúc này dự kiến:

```text
isProxy=false
charged:A-1
charged:B-2
```

Đó là cách phân biệt rõ **đăng ký aspect bean** và **bật hạ tầng auto-proxy**. Nếu cùng context còn phần khác đã bật AOP, đối chứng này không còn độc lập và có thể vẫn xuất hiện proxy.

## 6. Cách kiểm chứng cấu hình khi advice không chạy

Từng kiểm tra sau nhằm xác định nguyên nhân cụ thể, thay vì thêm annotation ngẫu nhiên.

| Biểu hiện | Cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| Không có dòng advice, `isAopProxy(bean) == false` | Class `@Configuration` chứa `@EnableAspectJAutoProxy` có được nạp trong context hiện tại không? | Đưa config đúng vào `@Import`, `@ComponentScan` hoặc `AnnotationConfigApplicationContext`. |
| Class có `@Aspect` nhưng không có advice | Aspect có là bean không? Có `@Component` và scan đúng package, hoặc `@Bean`? | Đăng ký aspect bean rõ ràng. |
| Startup lỗi thiếu class AspectJ | Có `org.aspectj:aspectjweaver` tương thích không? | Thêm dependency ở classpath đúng module/runtime. |
| `isAopProxy(bean)==false` dù hạ tầng đã bật | Pointcut có match method/class? Target có là Spring bean? | Sửa biểu thức/annotation và test một method có thể proxy. |
| Advice chạy cho lời gọi từ bean khác nhưng không chạy khi `this.method()` | Self-invocation bỏ qua proxy. | Tách nghiệp vụ sang Spring bean khác hoặc đổi ranh giới gọi. |
| Có proxy, nhưng ép cast sang concrete class bị lỗi | Có đang dùng JDK proxy theo interface không? | Inject bằng interface hoặc cân nhắc class-based proxy nếu thích hợp. |
| Proxy không chặn method như mong đợi | Method `private`/`final`, class `final`, method không public với JDK proxy? | Chọn public service contract intercept được; không trông cậy vào subclass để override `final`/`private`. |
| Có advice nhưng log xuất hiện nhiều lần | Có nhiều bean aspect/advisor trùng, nhiều rule match, hoặc nhiều context? | Kiểm tra bean registration, advisor chain, tránh đăng ký trùng aspect. |
| Chạy trong Spring Boot nhưng bỏ `@EnableAspectJAutoProxy` vẫn có advice | Boot đang tự bật AOP khi đủ classpath. | Đây là hành vi mong đợi; xem phần cấu hình Boot bên dưới. |

Để kiểm tra proxy khi debug:

```java
// Đoạn minh họa bên trong code có thể truy cập Spring bean
boolean anyProxy = AopUtils.isAopProxy(service);
boolean jdkProxy = AopUtils.isJdkDynamicProxy(service);
boolean cglibProxy = AopUtils.isCglibProxy(service);
```

Import `org.springframework.aop.support.AopUtils`. Kết quả `isAopProxy` chỉ cho biết **bean có proxy hay không**, không khẳng định mọi method match pointcut. Kiểm tra advice bằng một hành vi quan sát cụ thể hoặc integration test chạy với context thật.

### Kiểm thử mẫu với context thực

Một kiểm thử phù hợp cần khởi tạo `AnnotationConfigApplicationContext` hoặc `@SpringJUnitConfig` để sử dụng bean từ Spring container, rồi assert cả **việc có proxy** và **hiệu ứng advice**. Test tạo `new BillingService()` chỉ kiểm tra code target, **không kiểm tra interception**. Khi aspect in console, demo dùng output cho dễ theo dõi; trong kiểm thử thực tế, nên thay `System.out` bằng recorder/mock/test sink có thể assert số lần advice chạy.

## 7. Spring Boot: vì sao thường không cần ghi @EnableAspectJAutoProxy?

Khi ứng dụng **Spring Boot** có dependency AspectJ/Spring AOP phù hợp và không tắt AOP auto-configuration, Boot tự cấu hình AOP tương đương việc bật `@EnableAspectJAutoProxy`. Do đó, một ứng dụng Boot điển hình có thể dùng `@Aspect` + `@Component` ngay mà không đặt annotation `@EnableAspectJAutoProxy` lên class main.

Ví dụ dependency **chọn theo major version của Boot**:

```gradle
// Trong Spring Boot 3.x, với plugin/BOM Boot đã cấu hình
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

```gradle
// Trong Spring Boot 4.x, với plugin/BOM Boot đã cấu hình
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aspectj'
}
```

Chọn **đúng một cấu hình phù hợp phiên bản project**; hai khối trên là **ví dụ thay thế**, không đưa cả hai vào cùng `build.gradle`. Với Boot, dependency management quản lý version tương thích. Đừng hiểu tên `starter-aspectj` trong Boot 4.x là bằng chứng tự động bật AspectJ bytecode weaving: `@AspectJ` trong Spring AOP vẫn thường chạy qua proxy.

Cấu hình Boot liên quan:

```properties
# Thông thường mặc định auto-configure AOP khi đáp ứng điều kiện
spring.aop.auto=true

# Boot thường dùng subclass-based proxy theo mặc định
spring.aop.proxy-target-class=true
```

Đổi sang:

```properties
spring.aop.proxy-target-class=false
```

để Boot ưu tiên JDK interface proxy khi phù hợp. Thuộc tính `spring.aop.auto=false` vô hiệu hóa **phần AOP auto-configuration của Boot**, không phải mệnh lệnh xóa mọi auto-proxy creator do các cấu hình Spring khác tạo ra. Nếu ứng dụng tự thêm `@EnableAspectJAutoProxy`, Boot không còn là nơi duy nhất quyết định việc AOP có được bật hay không. Khi muốn chủ động quản lý AOP thủ công trong Boot, kiểm tra **tổng hợp** các cấu hình thực tế, tránh đặt hai cơ chế như các nút bật/tắt độc lập.

**Bảng đối chiếu:**

| Môi trường | Cách bật hỗ trợ `@AspectJ` | Điểm cần nhớ |
| --- | --- | --- |
| Spring Framework Java config độc lập | Thêm `@EnableAspectJAutoProxy` vào `@Configuration` được nạp | Phải có `aspectjweaver`, Spring AOP, aspect bean và target bean. |
| Spring Boot có AOP auto-configuration | Thường **không cần** khai báo thủ công | Dùng starter đúng major version và `spring.aop.*` nếu cần. |
| Cấu hình Spring XML truyền thống | `<aop:aspectj-autoproxy/>` | Cùng mục tiêu bật Spring AOP @AspectJ theo kiểu XML. |
| Nhiều ApplicationContext riêng | Đặt enable config tại context nơi các bean cần advice sống | Annotation có phạm vi ở local context, không tự lan tới mọi context khác. |

### Phạm vi nhiều ApplicationContext

Spring có thể có **root context** và các **child context** độc lập (ví dụ mô hình web context cũ). Javadoc của `@EnableAspectJAutoProxy` quy định annotation áp dụng trong **local application context**. Nếu cần proxy ở nhiều context, phải cấu hình enable trong từng context tương ứng và bảo đảm aspect/target bean có mặt đúng chỗ. Không suy ra bật trên một context thì mọi bean ở context khác đều được advisor xử lý.

## 8. Sai lầm phổ biến khi thiết lập

1. **Nhầm `@Aspect` với Spring stereotype.** `@Aspect` không thay thế `@Component`/`@Bean`; aspect không có trong context thì không được dùng.
2. **Bật annotation nhưng thiếu `aspectjweaver`.** `@EnableAspectJAutoProxy` cần thư viện này để hỗ trợ khai báo pointcut.
3. **Thêm annotation vào class không được Spring quét/nạp.** Chỉ annotation nằm trong cấu hình có hiệu lực của context mới kích hoạt hạ tầng.
4. **Cho rằng mọi bean đều thành proxy.** Bean không được advisor áp dụng có thể giữ nguyên object.
5. **Dùng `proxyTargetClass=true` để mong chặn `this.method()`.** Self-invocation vẫn bỏ qua proxy dù chọn JDK hay subclass proxy.
6. **Bật `exposeProxy=true` để “sửa” mọi lời gọi.** Thuộc tính chỉ công khai proxy hiện tại trong ngữ cảnh hỗ trợ; không làm code bên ngoài container tự được intercept.
7. **Dùng `@EnableAspectJAutoProxy` như annotation bật transaction/cache/async.** Những khả năng đó có cấu hình activation riêng và có thể tương tác với auto-proxy, cần xem xét từng trường hợp.
8. **Coi `@EnableAspectJAutoProxy` là AspectJ weaving.** Nó bật cơ chế Spring proxy-based @AspectJ; không chặn constructor/field hay object tùy ý.
9. **Ghi annotation lặp lại trong Boot mà không xem auto-config.** Thường không cần và dễ gây nhầm lẫn lúc xử lý cấu hình proxy.
10. **Quên thứ tự advisor khi kết hợp nhiều Spring interception.** `@Order` trên aspect và order của các advisor khác có thể thay đổi thời điểm advice chạy so với transaction/security; hãy kiểm tra thứ tự thực tế trước khi suy luận điều kiện “thành công” hoặc rollback.

## 9. Checklist hoàn tất

- [ ] Class `@Configuration` mang `@EnableAspectJAutoProxy` được nạp vào đúng Spring `ApplicationContext`.
- [ ] Có Spring AOP và AspectJ Weaver tương thích trong dependencies; không vô tình trộn Spring Framework/Boot versions.
- [ ] Class `@Aspect` đã là Spring bean qua `@Component` + scan hoặc `@Bean`.
- [ ] Target method thuộc Spring bean; pointcut match chính xác phạm vi mong muốn.
- [ ] Caller nhận bean thông qua DI/context, gọi **qua proxy**; đã kiểm tra `this.method()` và object tạo bằng `new`.
- [ ] `proxyTargetClass` hợp với kiểu bean/API, `final`, `private`, interface và cách inject.
- [ ] Chỉ bật `exposeProxy` khi có trường hợp thực tế cần thiết và hiểu rõ ngữ cảnh `ThreadLocal`.
- [ ] Với Boot, đã kiểm tra AOP auto-configuration và `spring.aop.auto`/`spring.aop.proxy-target-class` trước khi thêm enable thủ công.
- [ ] Nếu có nhiều context, đã bật ở đúng nơi.
- [ ] Đã chạy integration test xác nhận proxy **và** side effect advice; kiểm tra đường gọi bình thường, self-invocation và trường hợp pointcut không match.

**Điểm nhớ:** `@EnableAspectJAutoProxy` giải quyết bước **cho phép Spring xử lý `@Aspect` bean và tạo proxy cho target phù hợp** trong một context. Nó không tự đăng ký mọi aspect, không biến tất cả bean thành proxy, không vượt giới hạn self-invocation và không cần khai báo lại khi Spring Boot đã auto-configure đúng.

## Tài liệu Spring tham khảo

- [Spring Framework — EnableAspectJAutoProxy Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/EnableAspectJAutoProxy.html)
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Boot — AOP Auto-configuration](https://docs.spring.io/spring-boot/reference/features/aop.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-setup-004">Vai trò @Configuration, component scan và đăng ký bean</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò @Configuration, component scan và đăng ký bean khi thiết lập Spring AOP

## 1. Mục tiêu bài học

Trong Spring Framework, viết một lớp có **@Aspect** mới chỉ khai báo logic can thiệp. Để advice thực sự chạy khi ứng dụng gọi phương thức của một service, **ApplicationContext** phải biết cả **service bean**, **aspect bean** và phải có **hạ tầng tạo Spring AOP proxy**. Bài này tập trung vào ba thao tác cấu hình liên kết các phần đó:

1. Dùng **@Configuration** để cung cấp cấu hình Java cho container và đặt **@EnableAspectJAutoProxy**.
2. Dùng **@ComponentScan** để tự phát hiện các lớp Spring component như **@Service**, **@Component**.
3. Dùng **@Bean** để đăng ký tường minh service, aspect và client khi không muốn dùng component scanning.

Kết quả cần đạt: giải thích được **vì sao một aspect đã viết đúng vẫn không chạy nếu chưa đăng ký bean**, phân biệt việc **đưa bean vào context** với việc **bean có được bọc proxy hay không**, và biết kiểm tra bean thực tế mà client nhận từ Spring.

**Phạm vi:** Ví dụ dùng **Spring AOP dạng proxy trong ApplicationContext**; không đi sâu vào lý thuyết AOP tổng quát, AspectJ compile-time/load-time weaving hay các framework AOP khác. Tên gọi **@AspectJ** trong tài liệu Spring chỉ kiểu khai báo aspect qua annotation; Spring vẫn tạo proxy theo cơ chế của Spring AOP.

## 2. Bức tranh cấu hình: ba điều kiện cần

Hãy tưởng tượng ứng dụng có **PaymentService** và muốn ghi dấu vết trước khi xử lý thanh toán. Ba điều kiện không thể thay thế lẫn nhau:

| Thành phần | Nhiệm vụ trong Spring | Nếu thiếu |
| --- | --- | --- |
| **Cấu hình ApplicationContext** | Nạp lớp cấu hình, kích hoạt hỗ trợ **@AspectJ** bằng **@EnableAspectJAutoProxy** | Aspect có thể là bean nhưng không có hạ tầng tự tạo proxy cho advice của nó |
| **Đăng ký PaymentService** | Tạo bean target mà client có thể lấy/inject từ container | Không có Spring-managed service để Spring đưa qua auto-proxying |
| **Đăng ký AuditAspect** | Cho Spring biết bean chứa **@Aspect** và advice | Hạ tầng AOP không tìm thấy aspect để xây dựng advisor tương ứng |

Một service đã được đăng ký **không mặc nhiên luôn là proxy**. Spring xét các advisor có thể áp dụng cho bean và có thể trả về proxy nếu bean đủ điều kiện. Với lời gọi tới phương thức phù hợp, luồng chạy là:

~~~text
ApplicationContext
  |
  +-- PaymentServiceImpl (@Service) --------------------+
  |                                                    |
  +-- AuditAspect (@Aspect + @Component) -- advice -----+-- Spring auto-proxy creator
  |                                                            |
  +-- PaymentClient (@Component)                                |
          |                                                     |
          +-- inject PaymentService <--- proxy <----------------+
                                             |
PaymentClient.checkout() --> proxy --> advice --> PaymentServiceImpl.pay()
~~~

Các annotation **@Service** và **@Component** phục vụ phát hiện/đăng ký bean; **@Aspect** phục vụ khai báo aspect; **@EnableAspectJAutoProxy** kích hoạt cơ chế Spring đọc khai báo đó và xây dựng proxy. Đây là các trách nhiệm khác nhau.

## 3. Dự án thực hành Spring Framework thuần

Ta dùng **Java 17+**, **Maven 3.9+**, **Spring Framework 6.2.12**. Không cần Spring Boot, HTTP server hay cơ sở dữ liệu. Ví dụ được tổ chức sao cho từng class có thể biên dịch cùng nhau.

~~~text
spring-aop-config-demo/
├── pom.xml
└── src/main/java/example/
    ├── App.java
    ├── AppConfig.java
    ├── PaymentService.java
    ├── PaymentServiceImpl.java
    ├── AuditAspect.java
    └── PaymentClient.java
~~~

**pom.xml**

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aop-config-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.12</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.24</version>
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
                    <mainClass>example.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**spring-context** cung cấp container cấu hình annotation và kéo theo những module Spring cần thiết, bao gồm **spring-aop**. **aspectjweaver** cần có trong classpath để Spring xử lý các annotation và biểu thức pointcut kiểu **@AspectJ**. Điều này **không có nghĩa** ví dụ đang dùng AspectJ weaving: các lời gọi vẫn được Spring AOP chặn qua proxy.

### 3.1. Định nghĩa hợp đồng service

**src/main/java/example/PaymentService.java**

~~~java
package example;

public interface PaymentService {
    String pay(String orderId);
}
~~~

### 3.2. Khai báo service bằng @Service

**src/main/java/example/PaymentServiceImpl.java**

~~~java
package example;

import org.springframework.stereotype.Service;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Override
    public String pay(String orderId) {
        System.out.println("SERVICE pay " + orderId);
        return "ok:" + orderId;
    }
}
~~~

**@Service** là một stereotype annotation của Spring và là một dạng chuyên biệt của **@Component**. Khi package chứa lớp được quét, container sẽ đăng ký một **bean definition** cho **PaymentServiceImpl**. Với quy tắc đặt tên mặc định, bean có tên **paymentServiceImpl**.

Lưu ý: JVM không tự tạo Spring bean chỉ vì lớp mang **@Service**. Phải có một cơ chế đăng ký nó vào container: component scan, phương thức **@Bean**, hoặc đăng ký bằng API của context.

### 3.3. Khai báo aspect thành một Spring bean

**src/main/java/example/AuditAspect.java**

~~~java
package example;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    @Before("execution(* example.PaymentServiceImpl.pay(..))")
    public void beforePay(JoinPoint joinPoint) {
        Object orderId = joinPoint.getArgs()[0];
        System.out.println("ASPECT before " + orderId);
    }
}
~~~

Hai annotation có nhiệm vụ độc lập:

- **@Aspect**: Spring nhận ra lớp là nơi định nghĩa advice và biểu thức chọn phương thức; ở đây **@Before** áp dụng cho **PaymentServiceImpl.pay(...)**.
- **@Component**: đưa **AuditAspect** vào danh sách ứng viên cho component scanning, từ đó tạo bean **auditAspect** trong context.

Nếu chỉ còn **@Aspect** mà bỏ **@Component**, component scanner thông thường không tự coi lớp đó là component. Khi ấy **@EnableAspectJAutoProxy** vẫn có thể đã bật, nhưng aspect này không được đăng ký nên không tham gia can thiệp.

Aspect phải được **đăng ký như một Spring bean**: có thể dùng **@Component** hoặc một phương thức **@Bean**. Không cần thêm **@Service** lên lớp aspect. Ngoài ra, bản thân bean mang **@Aspect** không phải đối tượng mà Spring AOP dùng để advice bởi các aspect khác.

### 3.4. Tạo client nhận service từ container

**src/main/java/example/PaymentClient.java**

~~~java
package example;

import org.springframework.stereotype.Component;

@Component
public class PaymentClient {

    private final PaymentService paymentService;

    public PaymentClient(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void checkout(String orderId) {
        String result = paymentService.pay(orderId);
        System.out.println("CLIENT result " + result);
    }
}
~~~

Vì class có một constructor, Spring có thể thực hiện constructor injection mà không cần ghi **@Autowired**. Spring tìm **PaymentService** theo kiểu dữ liệu trong context; với ví dụ này chỉ có một bean phù hợp.

Từ góc nhìn **PaymentClient**, dependency là **PaymentService**. Nếu bean đủ điều kiện được Spring tạo AOP proxy, chính proxy ấy được inject vào client. Tự viết **new PaymentServiceImpl()** trong constructor của client sẽ bỏ qua quá trình đăng ký và proxy của Spring.

### 3.5. Vai trò của @Configuration, @ComponentScan và @EnableAspectJAutoProxy

**src/main/java/example/AppConfig.java**

~~~java
package example;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackages = "example")
@EnableAspectJAutoProxy
public class AppConfig {
}
~~~

Đọc từng dòng:

**@Configuration** đánh dấu lớp là nguồn cấu hình Java của Spring. Trong ví dụ, **AppConfig** được truyền trực tiếp cho **AnnotationConfigApplicationContext**. Lớp cấu hình có thể chứa các phương thức **@Bean**, nhập cấu hình qua **@Import** hoặc khai báo cách scan và bật tính năng container. Chỉ có **@Configuration** không tự làm mọi lớp cùng package trở thành bean.

**@ComponentScan(basePackages = "example")** yêu cầu Spring tìm các component phù hợp dưới package **example** và các subpackage. Nhờ đó, Spring phát hiện **PaymentServiceImpl** nhờ **@Service**, **AuditAspect** và **PaymentClient** nhờ **@Component**. Nếu component nằm ở package ngoài phạm vi scan mà không được đăng ký theo cách khác, nó không nằm trong context.

**@EnableAspectJAutoProxy** đăng ký hạ tầng Spring AOP tự động xét các bean và các advisor khai báo qua **@AspectJ**, tạo proxy khi cần. Annotation này **không thay thế component scan** và **không tự tạo AuditAspect bean từ class chưa được đăng ký**.

Đây là **Spring Framework thuần** nên bật AOP một cách tường minh bằng annotation trên. Trong dự án Spring Boot có starter và auto-configuration phù hợp, phần bật proxy có thể được cung cấp tự động; cần kiểm tra cấu hình Boot thay vì mặc định rằng ví dụ thuần Spring và Boot hoàn toàn giống nhau.

### 3.6. Tạo ApplicationContext, kiểm tra bean và gọi service

**src/main/java/example/App.java**

~~~java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            System.out.println(
                    "HAS_SERVICE " + context.containsBean("paymentServiceImpl"));
            System.out.println(
                    "HAS_ASPECT " + context.containsBean("auditAspect"));

            PaymentService service = context.getBean(PaymentService.class);
            System.out.println("IS_PROXY " + AopUtils.isAopProxy(service));

            PaymentClient client = context.getBean(PaymentClient.class);
            client.checkout("ORD-7");
        }
    }
}
~~~

Chạy từ thư mục có **pom.xml**:

~~~bash
mvn -q clean compile exec:java
~~~

Đầu ra logic kỳ vọng, bỏ qua thông điệp của Maven và môi trường chạy:

~~~text
HAS_SERVICE true
HAS_ASPECT true
IS_PROXY true
ASPECT before ORD-7
SERVICE pay ORD-7
CLIENT result ok:ORD-7
~~~

Ý nghĩa của ba dòng đầu:

- **HAS_SERVICE true**: component scanning đã đăng ký service theo tên mặc định.
- **HAS_ASPECT true**: aspect cũng đã được đăng ký là bean; chỉ có **@Aspect** sẽ không bảo đảm điều này.
- **IS_PROXY true**: đối tượng **PaymentService** mà context trả về đã được Spring AOP bọc proxy. Việc có bean không tương đương việc có proxy; ví dụ đủ điều kiện vì có advice khớp **pay(...)**.

Ba dòng còn lại cho thấy client gọi qua proxy, advice **@Before** chạy trước phương thức đích, rồi service trả kết quả như bình thường.

## 4. Tại sao component scanning và @Configuration không tự động bật AOP?

Ba trường hợp rất dễ bị lẫn:

| Cấu hình thực tế | Service là bean? | Aspect là bean? | Có advice của AuditAspect chạy? |
| --- | --- | --- | --- |
| **@ComponentScan** nhưng không bật **@EnableAspectJAutoProxy** | Có | Có | Không, trong ví dụ Spring Framework thuần này |
| **@EnableAspectJAutoProxy** nhưng không scan và không khai báo bean nào | Không | Không | Không |
| **@Configuration + @ComponentScan + @EnableAspectJAutoProxy** như ví dụ | Có | Có | Có, với lời gọi khớp pointcut đi qua proxy |
| **@Configuration + @Bean** khai báo cả service và aspect, đồng thời bật AOP | Có | Có | Có, nếu advisor phù hợp |

**@Configuration** chỉ nói với Spring rằng lớp là một nguồn định nghĩa bean và cấu hình. **@ComponentScan** chỉ đăng ký những component phù hợp với vùng quét và bộ lọc. **@EnableAspectJAutoProxy** mới là phần nối hạ tầng advice/proxy vào Spring container. Không annotation nào trong ba annotation này có thể thay thế toàn bộ công việc của hai annotation còn lại.

Một chi tiết khác: **@Configuration** được xem là một component khi scanner quét tới nó, nhưng trong ví dụ chính ta nạp **AppConfig.class** trực tiếp vào context. Vì vậy việc nạp lớp cấu hình chính không phụ thuộc vào việc scanner tình cờ phát hiện lớp ấy.

## 5. Cách thay thế: đăng ký tất cả bean bằng @Bean

Component scan thuận tiện khi các lớp do dự án sở hữu có stereotype annotation. Với các class không thể sửa, muốn đặt tên bean chủ động hoặc cần xây dựng bean qua constructor/factory, có thể dùng **@Bean**.

**Thí nghiệm độc lập:** thay **toàn bộ nội dung** của **src/main/java/example/AppConfig.java** bằng phiên bản sau, thay vì thêm một lớp **@Configuration** mới vào package đang được scan. Nhờ vậy không vô tình đăng ký trùng service và aspect.

~~~java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class AppConfig {

    @Bean
    public PaymentService paymentService() {
        return new PaymentServiceImpl();
    }

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }

    @Bean
    public PaymentClient paymentClient(PaymentService paymentService) {
        return new PaymentClient(paymentService);
    }
}
~~~

Không còn **@ComponentScan**. **PaymentServiceImpl**, **AuditAspect** và **PaymentClient** vẫn có các annotation cũ trong file Java nhưng chỉ được container đăng ký vì các phương thức **@Bean**.

Những điểm cần nhận ra:

1. **@Bean public PaymentService paymentService()** đăng ký bean với tên mặc định **paymentService**, không phải **paymentServiceImpl**. Spring quản lý đối tượng được factory method trả về và vẫn có thể bọc nó bằng AOP proxy.
2. **@Bean public AuditAspect auditAspect()** đăng ký instance chứa khai báo **@Aspect**. Không bắt buộc class **AuditAspect** phải có **@Component** trong đường đăng ký bằng **@Bean**.
3. **paymentClient(PaymentService paymentService)** để Spring resolve tham số qua dependency injection. Client nhận bean **PaymentService** đã được container xử lý, không cần tự gọi factory method.
4. **@EnableAspectJAutoProxy** vẫn phải tồn tại trong cấu hình Spring Framework thuần. **@Bean** chỉ đăng ký đối tượng; nó không tự bật AOP.
5. Việc method **paymentService()** dùng **new PaymentServiceImpl()** là hợp lệ vì **new** xảy ra **bên trong phương thức factory @Bean** và đối tượng trả về được container quản lý. Ngược lại, **new PaymentServiceImpl()** ở mã client rồi dùng trực tiếp sẽ không tự được Spring proxy.

Để chạy lại **App.java**, đổi dòng kiểm tra tên bean từ:

~~~java
context.containsBean("paymentServiceImpl")
~~~

thành:

~~~java
context.containsBean("paymentService")
~~~

Chạy lại lệnh Maven ở mục 3.6; các dòng logic vẫn như cũ, bao gồm **IS_PROXY true**, **ASPECT before ORD-7**, **SERVICE pay ORD-7**. Nếu để nguyên câu kiểm tra tên cũ, **HAS_SERVICE** sẽ là **false** dù service thực sự đã được đăng ký dưới tên **paymentService**. Vì vậy phải phân biệt **kiểm tra đúng tên bean** với **kết luận có hay không có bean theo kiểu**.

Khôi phục **AppConfig.java** của mục 3.5 khi muốn quay lại phiên bản component scan ban đầu. Không nên đồng thời giữ cả hai con đường khai báo cùng một service/aspect nếu chưa có chủ đích quản lý nhiều bean.

## 6. Spring tạo proxy ở giai đoạn nào?

Trong ví dụ, luồng cấu hình có thể tách thành các bước để chẩn đoán:

~~~text
new AnnotationConfigApplicationContext(AppConfig.class)
          |
          v
Đọc AppConfig và các annotation cấu hình
          |
          +-- @ComponentScan -> bean definitions:
          |      paymentServiceImpl, auditAspect, paymentClient
          |
          +-- @EnableAspectJAutoProxy
          |      -> đăng ký hạ tầng auto-proxy creator
          |
          v
Tạo và khởi tạo các bean cần thiết
          |
          +-- tìm aspect bean, đọc @Before và pointcut
          +-- xác định advisor áp dụng cho PaymentServiceImpl
          +-- xử lý bean bằng AOP BeanPostProcessor
          |
          v
Expose PaymentService qua proxy cho các dependency/client
          |
          v
PaymentClient.checkout() -> PaymentService proxy
          -> AuditAspect.beforePay() -> PaymentServiceImpl.pay()
~~~

Sơ đồ mô tả quan hệ trách nhiệm, không biểu diễn đầy đủ mọi callback hoặc một thứ tự khởi tạo cố định cho tất cả bean. Nền tảng của auto-proxying là các thành phần **BeanPostProcessor** của Spring; chúng có thể thay đối tượng bean được expose bằng proxy sau khi bean trải qua quá trình khởi tạo thích hợp.

Có hai kết luận thực hành:

- **Bean definition** có nghĩa Spring biết cách tạo/quản lý bean. Nó chưa đủ để suy ra client nhận proxy.
- **AOP proxy** là lớp trung gian mà Spring có thể cung cấp cho client nếu có advisor phù hợp và hạ tầng AOP hoạt động. Advisor quyết định liệu lời gọi tới một method có đi qua advice hay không.

Trong ví dụ này, vì **PaymentServiceImpl** triển khai **PaymentService**, cấu hình Spring Framework thuần mặc định có thể dùng **JDK dynamic proxy** theo interface. Client nên inject theo **PaymentService**, như đoạn mã trên. Muốn ép proxy dựa trên class có thể dùng **@EnableAspectJAutoProxy(proxyTargetClass = true)**; điều đó thay đổi kiểu proxy, không thay thế việc đăng ký bean. Các cấu hình proxy khác trong cùng context hoặc cấu hình Spring Boot cũng có thể ảnh hưởng loại proxy thực tế.

## 7. Phân biệt @Configuration enhancement với Spring AOP proxy

Một nguồn gây hiểu nhầm là **@Configuration** cũng có hành vi liên quan đến proxy/subclass. Trong cấu hình đầy đủ mặc định **@Configuration(proxyBeanMethods = true)**, Spring có thể tăng cường lớp cấu hình bằng CGLIB để các lời gọi giữa phương thức **@Bean** tuân theo ngữ nghĩa singleton của container. Đây là **cơ chế hỗ trợ cấu hình bean**, khác với AOP proxy dùng để chặn **PaymentService.pay(...)**.

Ví dụ hai phương thức factory:

~~~java
@Configuration
public class ExtraConfig {
    @Bean
    public SharedStore sharedStore() {
        return new SharedStore();
    }

    @Bean
    public ReportService reportService() {
        return new ReportService(sharedStore());
    }
}
~~~

Đây là **mã minh họa**, giả sử đã có hai lớp **SharedStore** và **ReportService** tương ứng; không phải file bắt buộc của dự án chạy ở trên. Với **@Configuration** mặc định, Spring bảo đảm việc tham chiếu bean theo phương thức cấu hình nhất quán với bean được quản lý. Nếu chuyển thành **@Configuration(proxyBeanMethods = false)**, việc trực tiếp gọi **sharedStore()** từ phương thức khác có thể tạo instance Java mới ngoài ngữ nghĩa gọi bean qua container. Khi đó nên dùng tham số dependency injection:

~~~java
@Bean
public ReportService reportService(SharedStore sharedStore) {
    return new ReportService(sharedStore);
}
~~~

Hai đoạn code này nhằm làm rõ **@Configuration và @Bean**; chúng không làm thay đổi pointcut hoặc behavior AOP của **PaymentService** ở ví dụ chính. Không suy luận rằng vì **AppConfig** đã có CGLIB enhancement thì service chắc chắn được tạo Spring AOP proxy.

## 8. Chẩn đoán bằng những thí nghiệm nhỏ

### 8.1. Aspect có @Aspect nhưng không được đăng ký

Trong phiên bản **component scan** ở mục 3.5, bỏ **@Component** khỏi **AuditAspect** và giữ **@Aspect**. Sau đó tạo mới ApplicationContext bằng cách chạy lại ứng dụng. Kỳ vọng:

~~~text
HAS_SERVICE true
HAS_ASPECT false
IS_PROXY false
SERVICE pay ORD-7
CLIENT result ok:ORD-7
~~~

Kết quả này áp dụng cho ví dụ chưa có advisor khác. Nếu ứng dụng thật có thêm advisor khác, bean có thể vẫn là proxy dù **AuditAspect** hiện tại không được đăng ký.

Cách sửa: khôi phục **@Component**, hoặc đăng ký bean aspect qua **@Bean** như mục 5; đồng thời xác minh package scan.

### 8.2. Aspect đã được scan nhưng AOP chưa bật

Ở phiên bản component scan, bỏ **@EnableAspectJAutoProxy** khỏi **AppConfig**, giữ **@ComponentScan** và **@Component** trên aspect. Khi chạy lại:

~~~text
HAS_SERVICE true
HAS_ASPECT true
IS_PROXY false
SERVICE pay ORD-7
CLIENT result ok:ORD-7
~~~

Trong Spring Framework thuần ở đây, thiếu hạ tầng AOP làm advice không được cài vào proxy; **@Aspect** tự nó không khiến advice chạy.

### 8.3. Service nằm ngoài vùng scan

Nếu chuyển **PaymentServiceImpl** sang package khác nhưng giữ **@ComponentScan(basePackages = "example")** và không đăng ký nó bằng cách khác, bean **PaymentService** không được phát hiện. Khi Spring tạo **PaymentClient**, dependency **PaymentService** không thể được giải quyết; context thường lỗi khi khởi tạo thay vì chạy tới dòng **HAS_SERVICE**. Cần mở rộng vùng scan đúng phạm vi hoặc khai báo service bằng **@Bean**.

Đừng mở scan tùy tiện sang toàn bộ ứng dụng lớn chỉ để sửa một lớp bị thiếu; kiểm tra package thực và cấu trúc module trước.

### 8.4. Tự tạo service ngoài ApplicationContext

Thay cho lời gọi đến Spring bean, mã bên ngoài tự tạo:

~~~java
PaymentService direct = new PaymentServiceImpl();
System.out.println("DIRECT " + direct.pay("ORD-8"));
~~~

Đầu ra của phần bổ sung này:

~~~text
SERVICE pay ORD-8
DIRECT ok:ORD-8
~~~

Không có log **ASPECT before** vì instance **direct** không phải bean được Spring xử lý. So sánh với cách gọi **context.getBean(PaymentService.class)** hoặc injection vào **PaymentClient**.

### 8.5. Client lấy service đúng nhưng gọi nội bộ không qua proxy

Nếu trong **PaymentServiceImpl** có một phương thức tự gọi **this.pay(...)**, lời gọi nội bộ đó không quay lại proxy của Spring. Đây là giới hạn của Spring AOP dựa trên proxy, không phải lỗi component scanning hay mất đăng ký bean.

Nếu cần advice cho thao tác nội bộ, một thiết kế dễ kiểm soát là tách phần thao tác sang Spring bean khác rồi inject/gọi qua dependency của container.

## 9. Lỗi cấu hình phổ biến

| Triệu chứng | Nguyên nhân thường gặp | Cách xác minh |
| --- | --- | --- |
| Aspect không chạy dù lớp có **@Aspect** | Chưa tạo aspect bean hoặc thiếu **@EnableAspectJAutoProxy** | Kiểm tra **containsBean**, cấu hình context và dependency AspectJ |
| Không inject được service | Package chưa scan, thiếu **@Service**, hoặc chưa có **@Bean** | Kiểm tra package, bean names và bean definitions |
| **NoUniqueBeanDefinitionException** | Nhiều bean cùng thỏa mãn kiểu cần inject | Xác định bean đúng, dùng **@Qualifier** hoặc **@Primary** khi phù hợp |
| Lấy bean bằng implementation class thất bại | JDK proxy chỉ triển khai service interface | Inject/lấy bean theo **PaymentService** |
| Aspect có bean nhưng phương thức không được chặn | Pointcut không khớp hoặc lời gọi bỏ qua proxy | So khớp tên package/class/method; quan sát đường đi lời gọi |
| Có proxy nhưng không có log của aspect mong muốn | Proxy có thể do advisor khác tạo ra | Kiểm tra đúng aspect bean và pointcut, không chỉ **AopUtils.isAopProxy** |
| Chạy code từ **new** mà không có advice | Instance không được tạo và expose qua Spring container | Lấy/inject bean từ context |
| Bị đăng ký trùng service hoặc aspect | Đồng thời khai báo cả scan và **@Bean** cho cùng logic | Chọn một đường đăng ký hoặc quản lý rõ nhiều bean |
| Dùng nhiều ApplicationContext, advice chỉ chạy ở một nơi | AOP configuration gắn với context chứa bean liên quan | Xác định config và service nằm ở context nào |
| Lỗi lúc phân tích **@Aspect**/pointcut | Thiếu **aspectjweaver** hoặc dependency không tương thích | Kiểm tra Maven dependency tree và phiên bản Spring đang chạy |

Với các ứng dụng dùng nhiều advice, cần chú ý độ ưu tiên và thứ tự áp dụng; **@Order** có thể dùng khi thứ tự là yêu cầu cụ thể. Điều đó là cấu hình advice sau khi đã giải quyết đúng việc đăng ký bean và tạo proxy.

## 10. Các câu hỏi tự kiểm tra

**Câu 1. Có @Configuration và @ComponentScan, tại sao @Before không chạy?**

Vì hai annotation này cho phép Spring nạp cấu hình và đăng ký component, nhưng trong ví dụ Spring Framework thuần cần thêm **@EnableAspectJAutoProxy** để xử lý **@Aspect** và tạo proxy theo advisor.

**Câu 2. AuditAspect chỉ có @Aspect, có được component scan tự phát hiện như một bean không?**

Không. Phải đồng thời có **@Component** (hoặc stereotype Spring đủ điều kiện scan) hoặc dùng cách đăng ký tường minh như **@Bean**.

**Câu 3. Có bắt buộc mọi Spring AOP aspect phải đăng ký bằng component scan không?**

Không. Một **@Bean** method trả về **AuditAspect** cũng cho container một aspect bean để hạ tầng AOP sử dụng.

**Câu 4. Tại sao trong @Bean được phép new PaymentServiceImpl() nhưng new ở client lại bỏ qua AOP?**

**@Bean** trả instance cho container quản lý, container có thể đưa instance đó qua bước auto-proxy rồi expose bean cho client. Instance new trực tiếp ở client không qua pipeline đó.

**Câu 5. Sự khác biệt giữa bean và proxy là gì trong ví dụ?**

Bean là đối tượng mà Spring quản lý; proxy là đối tượng trung gian có thể được Spring expose cho client để áp dụng advice. Bean được đăng ký không tự bảo đảm rằng mỗi method của nó đều bị advice.

**Câu 6. Tại sao có tên paymentService ở cấu hình @Bean, còn ở component scan là paymentServiceImpl?**

Mặc định bean từ **@Bean** lấy tên theo phương thức factory; bean từ component scan thường lấy tên lớp theo quy tắc naming của Spring. Tên khác không nhất thiết có nghĩa là loại service khác.

**Câu 7. Bằng chứng nào đủ tốt cho việc setup thành công?**

Cần quan sát ít nhất: bean service có trong context, aspect bean có trong context, service do context trả về là AOP proxy khi phù hợp, và **@Before** thực sự chạy trong lời gọi service qua dependency của Spring.

## 11. Checklist áp dụng cho một module Spring AOP

1. Xác định service cần can thiệp là bean trong **ApplicationContext** nào.
2. Chọn một đường đăng ký phù hợp: **@Service + @ComponentScan** hoặc **@Bean**.
3. Bảo đảm class **@Aspect** cũng được đăng ký thành bean, không chỉ gắn annotation.
4. Bật hỗ trợ auto-proxy trong context tương ứng (**@EnableAspectJAutoProxy** với ví dụ Framework thuần).
5. Kiểm tra biểu thức pointcut khớp method của target.
6. Inject service từ container theo interface khi dùng JDK proxy.
7. Kiểm tra hành vi qua lời gọi thực tế; xác nhận các giới hạn như self-invocation.

## 12. Tài liệu Spring đối chiếu

- Spring Framework Reference — Java-based Container Configuration: https://docs.spring.io/spring-framework/reference/core/beans/java.html
- Spring Framework Reference — @Bean: https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html
- Spring Framework Reference — @Configuration: https://docs.spring.io/spring-framework/reference/core/beans/java/configuration-annotation.html
- Spring Framework Reference — @AspectJ support và enabling auto-proxy: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework Reference — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html

**Kết luận kỹ thuật:** trong Spring AOP, **@Configuration** nạp cấu hình, **@ComponentScan/@Bean** làm cho service và aspect trở thành Spring-managed beans, còn **@EnableAspectJAutoProxy** cho phép Spring tạo proxy và áp dụng advice theo pointcut. Ba trách nhiệm kết hợp mới làm ví dụ can thiệp lời gọi service hoạt động.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-setup-005">Cấu hình proxyTargetClass và exposeProxy</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: cấu hình `proxyTargetClass` và `exposeProxy`

## 1. Hai thuộc tính giải quyết hai vấn đề khác nhau

Trong Spring Framework, khi bật AOP bằng `@EnableAspectJAutoProxy`, có hai thuộc tính thường gặp:

```java
@Configuration
@EnableAspectJAutoProxy(
    proxyTargetClass = false,
    exposeProxy = false
)
class AopConfig {
}
```

Đây là **đoạn cấu hình minh họa**, chưa phải ứng dụng chạy độc lập. Hai thuộc tính đều mặc định `false` tại **annotation `@EnableAspectJAutoProxy` của Spring Framework**. Tuy nhiên, Spring Boot thường tự cấu hình **class-based proxy** theo mặc định; cần phân biệt mặc định của annotation với mặc định của Spring Boot.

| Thuộc tính | Câu hỏi mà nó trả lời | `false` | `true` |
| --- | --- | --- | --- |
| `proxyTargetClass` | Spring tạo loại AOP proxy nào? | Ưu tiên JDK dynamic proxy nếu target có interface; nếu không có interface thì dùng class-based proxy | Ép Spring dùng class-based (CGLIB) proxy cho target class có thể proxy |
| `exposeProxy` | Code đang chạy trong một lời gọi AOP có thể truy cập **proxy hiện tại** qua `AopContext.currentProxy()` hay không? | Không công bố current proxy; thông thường sẽ ném `IllegalStateException` nếu cố lấy | Công bố current proxy trong phạm vi lời gọi qua proxy hiện tại; có thể dùng `AopContext.currentProxy()` |

Hai cờ **độc lập về ý nghĩa**. Bật `proxyTargetClass=true` không làm `AopContext.currentProxy()` hoạt động. Bật `exposeProxy=true` không tự đổi JDK proxy thành CGLIB. Cả hai cũng **không khiến mọi bean được proxy**: Spring AOP chỉ tạo proxy khi hạ tầng advice/advisor cần áp dụng cho bean tương ứng.

## 2. Hiểu rõ `proxyTargetClass` trong Spring AOP

Spring AOP dùng proxy cho **các lời gọi phương thức trên Spring-managed bean**. Khi target triển khai ít nhất một interface, hai cách tạo proxy có khác biệt lớn:

### `proxyTargetClass=false`: ưu tiên interface-based proxy

Ví dụ target:

```java
public interface PaymentService {
    void pay();
}

public class PaymentServiceImpl implements PaymentService {
    @Override
    public void pay() {
        System.out.println("pay");
    }
}
```

Nếu bean này được advisor chọn và `proxyTargetClass=false`, Spring thường tạo **JDK dynamic proxy**:

- Proxy triển khai `PaymentService`, nên inject hoặc lấy bean qua **interface** là cách tự nhiên.
- Proxy không phải subclass của `PaymentServiceImpl`. Ép kiểu sang `PaymentServiceImpl` hoặc inject trực tiếp theo implementation class có thể gây lỗi kiểu bean.
- Qua interface proxy, caller chỉ gọi trực tiếp được các phương thức được công bố bởi interface mà proxy triển khai. Một public method chỉ có trên implementation nhưng không thuộc proxy interface sẽ không được expose qua kiểu đó.
- Nếu target **không có interface phù hợp**, Spring vẫn cần dùng class-based proxy; `false` không có nghĩa là luôn JDK proxy.

### `proxyTargetClass=true`: ép class-based proxy

Spring tạo một subclass CGLIB của target (khi có thể). Ngay cả target có interface, loại AOP proxy vẫn hướng đến class:

- Điểm inject có thể khai báo theo concrete target class vì proxy là subclass của class đó.
- Một phương thức có thể được advice mà không cần nằm trên interface, miễn lời gọi **đi qua proxy**, advisor khớp và phương thức có thể bị intercept.
- Target class `final` không thể được tạo subclass proxy. Các phương thức `final` và `private` không thể bị override để advice qua CGLIB; phương thức không đủ khả năng truy cập cũng có thể bị giới hạn.
- Proxy CGLIB không biến self-invocation `this.method()` thành lời gọi qua proxy; vấn đề gọi nội bộ vẫn tồn tại.

**Lưu ý:** `proxyTargetClass=true` chỉ chọn **kiểu proxy** cho bean cần proxy. Nó không yêu cầu Spring chuyển tất cả bean thành CGLIB proxy và không liên quan đến AspectJ weaving.

### Bảng chọn nhanh

| Tình huống | `proxyTargetClass=false` | `proxyTargetClass=true` |
| --- | --- | --- |
| Target có interface và được AOP advice | Thường là JDK proxy | CGLIB proxy |
| Target không có interface | Class-based proxy | Class-based proxy |
| Consumer inject theo interface | Phù hợp | Phù hợp |
| Consumer bắt buộc inject implementation class | Có thể lỗi nếu dùng JDK proxy | Thường hợp lệ nếu class có thể subclass |
| Muốn advice public method chỉ khai báo trên implementation | JDK proxy không expose method đó qua interface | Có thể qua class proxy khi gọi từ bên ngoài |
| Target class `final` | JDK proxy vẫn có thể proxy các interface phù hợp | Không thể subclass final class |
| Target `this.work()` gọi nội bộ | Bỏ qua advice | Vẫn bỏ qua advice |

## 3. `exposeProxy` là gì?

Spring AOP mặc định không cung cấp một API cho business code tự lấy proxy hiện hành. Nếu bật:

```java
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
class AopConfig {
}
```

Spring có thể tạm lưu **proxy đang thực hiện lời gọi** trong `ThreadLocal` cho thời gian lời gọi qua AOP proxy. Từ bên trong target của lời gọi đó, code có thể lấy proxy bằng:

```java
Object proxy = AopContext.currentProxy();
```

Trong ví dụ, dòng lấy proxy là **mã minh họa** và chỉ hợp lệ nếu đang thực sự ở trong lời gọi qua proxy có cấu hình expose. Nếu chạy từ main, constructor, `@PostConstruct`, một object tạo bằng `new`, hoặc một thread khác không có invocation context, `AopContext.currentProxy()` sẽ không tìm được proxy phù hợp và ném `IllegalStateException`.

### Tại sao người ta dùng `exposeProxy=true`?

Spring AOP không chặn self-invocation thông thường:

```java
public void outer() {
    this.inner(); // Gọi trực tiếp target, không qua proxy.
}
```

Nếu buộc phải đưa lời gọi `inner()` **quay lại qua proxy**, có thể làm:

```java
public void outer() {
    ((MyService) AopContext.currentProxy()).inner();
}
```

Trong đó `MyService` là interface mà proxy JDK triển khai (hoặc loại tương thích với proxy CGLIB). Caller bên ngoài phải gọi `outer()` qua Spring AOP proxy trước; method `outer()` đang chạy phải có current proxy được expose. Đây là cách xử lý **có chủ đích** cho một tình huống hẹp.

Bật `exposeProxy=true` **không tự sửa** lệnh `this.inner()`. Chỉ lời gọi qua tham chiếu proxy như `((MyService) AopContext.currentProxy()).inner()` mới có cơ hội qua advice. Nó cũng không buộc advisor áp dụng cho `inner()` nếu pointcut không khớp.

### Chi phí và giới hạn

- `AopContext.currentProxy()` dựa trên `ThreadLocal`, chỉ có ý nghĩa trong phạm vi execution của lời gọi AOP đang diễn ra; Spring phục hồi trạng thái cũ khi thoát invocation.
- Spring cần thiết lập/khôi phục thông tin proxy ở mỗi lời gọi liên quan, tạo một ít overhead. Chỉ bật khi có nhu cầu thực sự.
- Nếu thực thi chuyển sang thread khác, ví dụ gọi bất đồng bộ hoặc thread pool, không giả định proxy hiện hành tự được truyền theo.
- Business code phụ thuộc trực tiếp Spring AOP, khó unit test thuần và làm cách gọi không qua Spring dễ lỗi.
- Lấy proxy rồi ép kiểu sai có thể gây `ClassCastException`, đặc biệt với JDK proxy không triển khai concrete class.

**Hướng thiết kế ưu tiên:** tách phương thức cần advice sang một bean khác và constructor-inject bean đó; consumer gọi qua dependency do Spring cấp. Cách này giữ business logic ít phụ thuộc hạ tầng AOP hơn. Chỉ dùng `exposeProxy` khi đã hiểu các ràng buộc và cần kiểm soát self-invocation một cách có chủ đích.

## 4. Cấu hình bằng annotation, XML và Spring Boot

### Spring Framework thuần

```java
@Configuration
@EnableAspectJAutoProxy(
    proxyTargetClass = true,
    exposeProxy = true
)
class AppConfig {
    // @Bean registrations hoặc @ComponentScan
}
```

Đây là ví dụ **cấu hình riêng lẻ**. `@EnableAspectJAutoProxy` chỉ tác động trong application context mà annotation được đăng ký. Với các application context phân cấp, cần kiểm tra từng context thực tế thay vì giả định một annotation ở context cha sẽ bật AOP trên mọi context con.

Aspect muốn Spring phát hiện cần đăng ký thành bean qua `@Component` + `@Aspect` hoặc qua `@Bean` trả về một đối tượng `@Aspect`. Với `@EnableAspectJAutoProxy` cần thư viện `aspectjweaver` để dùng cú pháp @AspectJ; không cần áp dụng bytecode weaving để chạy Spring proxy AOP.

Trong XML tương đương:

```xml
<aop:aspectj-autoproxy
    proxy-target-class="true"
    expose-proxy="true"/>
```

Cần namespace AOP và bean definitions tương ứng; fragment XML trên chưa phải một file cấu hình XML hoàn chỉnh.

### Spring Boot

Spring Boot có AOP auto-configuration khi dependency cần thiết hiện diện. Khi dùng `spring-boot-starter-aop`, cấu hình ứng dụng thông thường **không cần tự thêm** `@EnableAspectJAutoProxy`.

Trong `application.properties`:

```properties
# Spring Boot mặc định ưu tiên CGLIB.
spring.aop.proxy-target-class=true

# Đổi sang JDK dynamic proxy nếu target có interface phù hợp:
# spring.aop.proxy-target-class=false
```

Trong Spring Boot, `spring.aop.proxy-target-class` có giá trị mặc định `true`. Spring Framework thuần với `@EnableAspectJAutoProxy` không đặt đối số lại có mặc định `proxyTargetClass=false`. Đây là nguyên nhân thường gặp của sự khác biệt giữa hai ứng dụng khi inject theo concrete class.

Spring Boot cũng có thuộc tính `spring.aop.auto=true` để cho phép auto-configure AOP; `false` tắt **auto-configuration** đó, nhưng không ngăn một cơ chế kích hoạt AOP khác được khai báo tường minh. Đừng nhầm nó với `proxy-target-class`.

Muốn sử dụng `exposeProxy=true` trong cấu hình sử dụng annotation, khai báo rõ:

```java
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
class ExplicitAopConfig {
}
```

Khi kết hợp với Spring Boot, hãy nhớ Spring có thể **gộp cấu hình của các auto-proxy creator**, nên loại proxy thực tế còn chịu ảnh hưởng của các cấu hình khác trong cùng context. Nếu Boot đang ép `proxyTargetClass=true`, chỉ thêm `@EnableAspectJAutoProxy(proxyTargetClass=false)` không bảo đảm được JDK proxy; hãy kiểm tra giá trị `spring.aop.proxy-target-class` và các khai báo auto-proxy còn lại.

### Các cấu hình có thể tác động lẫn nhau

Spring hợp nhất một số cơ chế tạo AOP proxy. Chẳng hạn `@EnableAspectJAutoProxy(proxyTargetClass=true)` và `@EnableTransactionManagement(proxyTargetClass=true)` có thể làm toàn bộ các bean được những hạ tầng proxy liên quan tạo bằng class-based proxy theo cùng cấu hình mạnh nhất. Vì vậy, đừng kết luận proxy chỉ từ annotation đặt ngay cạnh bean. Xác minh bằng `AopUtils` sau khi khởi tạo context.

## 5. Dự án Java 17+ chạy được: quan sát cả hai thuộc tính

Ví dụ dưới đây dùng **Spring Framework 6.2.x**, Maven và Java 17. Có một `TaskService` được auto-proxy vì `TraceAspect` áp dụng advice cho `work()`. Phương thức `run()` gọi `work()` lần đầu qua `this` và lần thứ hai qua `AopContext.currentProxy()`. Ban đầu dùng **JDK proxy** với `proxyTargetClass=false` và bật `exposeProxy=true`.

Cấu trúc file:

```text
spring-aop-flags/
├── pom.xml
└── src/main/java/demo/aop/
    ├── App.java
    ├── AppConfig.java
    ├── TaskService.java
    ├── TaskServiceImpl.java
    └── TraceAspect.java
```

**`pom.xml`**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-flags</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.18</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.18</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.24</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.1</version>
            </plugin>
        </plugins>
    </build>
</project>
```

**`src/main/java/demo/aop/TaskService.java`**

```java
package demo.aop;

public interface TaskService {
    void run();
    void work();
}
```

**`src/main/java/demo/aop/TaskServiceImpl.java`**

```java
package demo.aop;

import org.springframework.aop.framework.AopContext;

public class TaskServiceImpl implements TaskService {
    @Override
    public void run() {
        System.out.println("run()");
        this.work(); // Bỏ qua proxy: sẽ không chạy advice.

        // Đây là lối gọi qua proxy với exposeProxy=true:
        ((TaskService) AopContext.currentProxy()).work();
    }

    @Override
    public void work() {
        System.out.println("work()");
    }
}
```

**`src/main/java/demo/aop/TraceAspect.java`**

```java
package demo.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(* demo.aop.TaskServiceImpl.work(..))")
    public void beforeWork() {
        System.out.println("[advice] before work()");
    }
}
```

**`src/main/java/demo/aop/AppConfig.java`**

```java
package demo.aop;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false, exposeProxy = true)
public class AppConfig {
    @Bean
    TaskService taskService() {
        return new TaskServiceImpl();
    }

    @Bean
    TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
```

Aspect được đăng ký rõ bằng `@Bean`, vì vậy không cần `@ComponentScan`. Cấu hình `@EnableAspectJAutoProxy` phát hiện `@Aspect` trên `TraceAspect` và tạo AOP proxy cho `TaskService`.

**`src/main/java/demo/aop/App.java`**

```java
package demo.aop;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskService service = context.getBean(TaskService.class);

            System.out.println("isAopProxy=" + AopUtils.isAopProxy(service));
            System.out.println("isJdkDynamicProxy=" + AopUtils.isJdkDynamicProxy(service));
            System.out.println("isCglibProxy=" + AopUtils.isCglibProxy(service));

            service.run();
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.aop.App
```

**Những dòng stdout do ví dụ in ra** (bỏ qua thông tin/log của công cụ build nếu có):

```text
isAopProxy=true
isJdkDynamicProxy=true
isCglibProxy=false
run()
work()
[advice] before work()
work()
```

Giải thích:

1. `TaskServiceImpl` có interface, và `proxyTargetClass=false` nên Spring chọn JDK dynamic proxy cho bean cần advice.
2. `service.run()` là lời gọi từ bên ngoài đi qua proxy. Vì `exposeProxy=true`, proxy hiện tại sẵn có trong `run()`.
3. `this.work()` là lời gọi trực tiếp trên target. Dòng `work()` đầu tiên không có `[advice]` đứng trước.
4. `((TaskService) AopContext.currentProxy()).work()` gửi lời gọi trở lại qua proxy; pointcut `work(..)` khớp nên advice chạy trước lần `work()` thứ hai.

### Thí nghiệm A: thay `proxyTargetClass=true`

Trong `AppConfig.java` đổi:

```java
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
```

Biên dịch/chạy lại cùng lệnh. Với target có thể subclass như ví dụ, ba dòng kiểm tra đầu trở thành:

```text
isAopProxy=true
isJdkDynamicProxy=false
isCglibProxy=true
```

Các dòng `run()`, `work()` và advice theo cùng quy tắc. Đổi loại proxy **không** làm `this.work()` đi qua advice. Với proxy CGLIB, caller có thể dùng concrete class nếu cần; với JDK proxy, tốt nhất lấy bean qua `TaskService`.

### Thí nghiệm B: thay `exposeProxy=false`

Giữ `proxyTargetClass=false` nhưng đổi:

```java
@EnableAspectJAutoProxy(proxyTargetClass = false, exposeProxy = false)
```

Ở đoạn `service.run()`, dòng `this.work()` đầu tiên vẫn chạy; khi tới `AopContext.currentProxy()`, ứng dụng **ném `IllegalStateException`** do Spring không expose current proxy. Tắt expose không có nghĩa advice đã biến mất: nếu `App.main()` gọi trực tiếp `service.work()` qua proxy, advice vẫn hoạt động bình thường. Để chạy thành công khi expose=false, hãy bỏ đoạn gọi `AopContext.currentProxy()` hoặc thiết kế lại lời gọi giữa các bean.

### Thí nghiệm C: so sánh kiểu inject

Nếu đặt `proxyTargetClass=false`, việc lấy bean bằng:

```java
TaskService service = context.getBean(TaskService.class);
```

là lựa chọn đúng, còn lấy qua `TaskServiceImpl.class` có thể thất bại do JDK proxy không phải instance của implementation. Khi `proxyTargetClass=true` và class không final, context có thể cấp proxy tương thích với `TaskServiceImpl`. Hãy khai báo dependency theo interface khi thiết kế cho hợp đồng nghiệp vụ; dùng CGLIB khi có lý do rõ ràng cho proxy theo class.

## 6. Tình huống thực tế và lỗi thường gặp

| Triệu chứng | Cách lý giải và xử lý |
| --- | --- |
| `AopUtils.isAopProxy(bean)` là `false` | Kiểm tra bean có thực sự được advisor chọn, aspect đã được đăng ký, và auto-proxy đã bật. Hai cờ không tự tạo proxy nếu không có advice áp dụng. |
| `@EnableAspectJAutoProxy(proxyTargetClass=false)` nhưng runtime vẫn CGLIB | Spring Boot có thể mặc định đặt `spring.aop.proxy-target-class=true`, hoặc có một cấu hình auto-proxy khác dùng class proxy. Kiểm tra cấu hình hợp nhất. |
| Lỗi kiểu khi inject implementation | Có thể đang dùng JDK dynamic proxy. Inject theo interface hoặc xem lại lý do bắt buộc dùng class proxy. |
| Lỗi tạo proxy cho `final class` | CGLIB phải tạo subclass. Thay thiết kế class/method hoặc dùng interface proxy nếu phù hợp. |
| Advice không chạy khi target dùng `this.method()` | Self-invocation bỏ qua proxy dù `proxyTargetClass=true`; chuyển lời gọi qua bean khác hoặc dùng exposed proxy có cân nhắc. |
| `AopContext.currentProxy()` báo `IllegalStateException` | Expose chưa bật hoặc đang ở ngoài invocation qua proxy; đừng gọi tại constructor, `@PostConstruct`, từ `new` hoặc từ thread khác. |
| Ép `(TaskServiceImpl) AopContext.currentProxy()` bị `ClassCastException` | Proxy thực tế là JDK proxy của interface; ép về interface được proxy triển khai hoặc thay cấu hình có cơ sở. |
| Bật `exposeProxy=true` nhưng `this.method()` vẫn không có advice | Expose chỉ mở quyền **lấy** proxy; tự gọi trực tiếp vẫn bypass. Phải gọi thực sự trên reference proxy. |
| Bật `proxyTargetClass=true` để mong intercept private/final method | CGLIB không override được private/final method. Đưa điểm advice đến method có thể được proxy hoặc thay ranh giới service. |
| Trong request async, `currentProxy()` bỗng không có | Context hiện hành lưu theo thread; không tự truyền qua thread khác. Đưa thao tác AOP sang một Spring bean và gọi qua dependency thích hợp. |

### Khi nào dùng cấu hình nào?

- **Spring Framework thuần, DI theo interface và không cần truy cập proxy hiện hành:** thường bắt đầu bằng `@EnableAspectJAutoProxy` với mặc định của nó. Giữ `exposeProxy=false`.
- **Cần class-based proxy vì hợp đồng ứng dụng thực sự dựa vào class và class có thể được proxy:** cân nhắc `proxyTargetClass=true`, kiểm tra method visibility, `final`, scope và cấu hình thống nhất.
- **Cần gọi nội bộ đi qua advice:** ưu tiên đổi cấu trúc thành hai Spring bean. Nếu bất khả thi, bật `exposeProxy=true` và gọi qua `AopContext.currentProxy()` trong invocation hợp lệ, đồng thời chấp nhận phụ thuộc vào Spring AOP.
- **Dự án Spring Boot:** đọc `spring.aop.proxy-target-class` trước khi kết luận về JDK/CGLIB; Boot mặc định chọn class-based proxy. Tránh vô tình có nhiều cấu hình proxy mâu thuẫn.
- **Debug hoặc viết test tích hợp:** kiểm tra đối tượng Spring thực sự cấp bằng `AopUtils.isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy` và kiểm tra advice với lời gọi qua DI.

## 7. Câu hỏi ôn tập

**1. `proxyTargetClass=true` có buộc tất cả Spring beans thành CGLIB proxy?** Không. Nó ép **kiểu proxy** cho các bean thực sự được AOP proxy; bean không có advisor phù hợp vẫn có thể giữ nguyên.

**2. Vì sao cùng code nhưng Spring Framework thuần là JDK proxy còn Spring Boot là CGLIB?** Vì `@EnableAspectJAutoProxy` mặc định `proxyTargetClass=false`, trong khi Spring Boot auto-configuration mặc định `spring.aop.proxy-target-class=true`.

**3. `exposeProxy=true` có chữa self-invocation không?** Không tự động. Nó cho phép lấy proxy đang thực hiện invocation; caller phải gọi method thông qua proxy ấy.

**4. Khi nào `AopContext.currentProxy()` có thể ném `IllegalStateException`?** Khi không có current proxy được expose trong thread và invocation hiện hành, chẳng hạn expose=false hoặc gọi ngoài proxy.

**5. Làm thế nào xác định loại proxy thực tế?** Dùng `AopUtils.isJdkDynamicProxy(bean)` / `AopUtils.isCglibProxy(bean)` trên bean lấy trực tiếp từ context hoặc DI, rồi đối chiếu cấu hình hợp nhất.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework — `@EnableAspectJAutoProxy` API](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/context/annotation/EnableAspectJAutoProxy.html)
- [Spring Framework — `AopContext` API](https://docs.spring.io/spring-framework/docs/6.2.18/javadoc-api/org/springframework/aop/framework/AopContext.html)
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html)
- [Spring Boot — Application Properties (spring.aop)](https://docs.spring.io/spring-boot/appendix/application-properties/index.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-setup-006">Phạm vi hiệu lực cấu hình trong từng ApplicationContext</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi hiệu lực cấu hình Spring AOP trong từng ApplicationContext

## 1. Vấn đề cần hiểu khi thiết lập Spring AOP

Một ứng dụng Spring có thể chỉ có một `ApplicationContext`, nhưng cũng có thể có **nhiều context độc lập hoặc có quan hệ cha–con**. Khi dùng `@EnableAspectJAutoProxy`, câu hỏi then chốt không phải chỉ là “đã bật AOP trong ứng dụng chưa?”, mà là **AOP được bật trong context nào và bean cần proxy do context nào tạo ra?**

**Quy tắc cốt lõi:** Trong cấu hình Spring Framework tường minh, `@EnableAspectJAutoProxy` áp dụng cho **`ApplicationContext` cục bộ chứa cấu hình đó**. Nếu có nhiều context và muốn từng context tự tạo proxy cho bean của nó, cần kích hoạt hỗ trợ `@AspectJ` trong các context tương ứng. Kích hoạt ở cha **không tự lan** xuống con; kích hoạt ở con **không xử lý lại** bean do cha tạo.

Bài này chỉ xét **Spring AOP theo cơ chế Spring proxy** và cách cấu hình nó trong Spring IoC, đặc biệt là `@EnableAspectJAutoProxy`. Không cần ôn lại các khái niệm AOP tổng quát.

Sau bài học, bạn sẽ:
- Xác định context sở hữu bean, context chứa hạ tầng auto-proxy và context chứa aspect.
- Dự đoán bean nào thực sự có proxy khi dùng hai `ApplicationContext`.
- Phân biệt **khả năng nhìn thấy bean/aspect qua quan hệ cha–con** với **khả năng hậu xử lý bean để tạo proxy**.
- Viết ví dụ kiểm chứng bằng `AopUtils.isAopProxy(...)`.
- Áp dụng nguyên tắc cho Spring MVC nhiều context và Spring Boot có AOP auto-configuration.

## 2. `@EnableAspectJAutoProxy` làm gì trong một context?

Trong ứng dụng Spring Framework thuần, cấu hình sau cho phép Spring đọc các Spring bean đã khai báo `@Aspect`, dựng advisor và tự tạo AOP proxy khi cần:

```java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}
```

Cấu hình này cần thư viện `aspectjweaver` trên classpath để Spring phân tích các biểu thức pointcut của `@AspectJ`. Thêm `@Aspect` lên một lớp **chưa đủ** để có aspect bean: phải đăng ký lớp bằng `@Component`, `@Bean` hoặc một cách đăng ký Spring bean phù hợp.

Trong context đã bật hỗ trợ, hạ tầng Spring đăng ký một **auto-proxy creator** (thuộc nhóm `BeanPostProcessor`). Khi context tạo bean, bộ xử lý này xem xét các advisor thích hợp và có thể trả về proxy thay vì target trực tiếp.

Sơ đồ một context:

```text
ApplicationContext A
├── @Configuration + @EnableAspectJAutoProxy
│    └── đăng ký AOP auto-proxy creator trong A
├── @Bean / @Component: AuditAspect
│    └── Spring phân tích các advice/pointcut thành advisor
└── @Bean / @Service: BillingService
     └── A tạo BillingService → A áp dụng bean post-processors
                                     └── có advisor phù hợp?
                                           ├── có → trả proxy
                                           └── không → trả bean gốc
```

Điều kiện để advice thực sự chạy là **phương thức khớp pointcut và lời gọi đi qua proxy**. Bật AOP không bắt buộc mọi bean có proxy và không biến method call nội bộ trên `this` thành lời gọi qua proxy.

### Những cấu hình cùng một context

Nếu nhiều lớp `@Configuration` trong **cùng một context** khai báo các tùy chọn auto-proxy, Spring hợp nhất hạ tầng auto-proxy phù hợp trong context đó; các tùy chọn như `proxyTargetClass` có thể tác động lên cấu hình proxy chung của context. Điều này **không biến** các context trong cây cha–con thành một hạ tầng AOP duy nhất.

## 3. Context cha–con: hai phạm vi khác nhau

Ví dụ có hai context:

```text
Parent ApplicationContext
├── parentService     (được parent tạo)
├── parentAspect      (được parent đăng ký)
└── parent AOP creator?  ← chỉ có nếu parent bật AOP

                 ↑ child tra cứu được một số bean từ parent
                 │
Child ApplicationContext
├── childService      (được child tạo)
├── childAspect       (được child đăng ký)
└── child AOP creator?   ← chỉ có nếu child bật AOP
```

Trong quan hệ này:
1. **Bean do parent định nghĩa và khởi tạo** được xử lý bởi các `BeanPostProcessor` của parent. Nếu parent tạo một Spring AOP proxy, child khi tra cứu bean parent sẽ nhận chính proxy đó.
2. **Bean do child định nghĩa và khởi tạo** được xử lý bởi các `BeanPostProcessor` của child. Bộ xử lý chỉ nằm ở parent không tự hậu xử lý bean child.
3. Child thông thường có thể tra cứu bean của parent nếu tên/kiểu phù hợp và không bị che khuất bởi định nghĩa local. Parent không tra cứu ngược được bean chỉ định nghĩa trong child.
4. **Có thể nhìn thấy aspect/advisor từ tổ tiên không có nghĩa auto-proxy creator từ tổ tiên chạy trong child.** Ngược lại, auto-proxy creator nằm trong child có thể dùng advisor/aspect mà cơ chế tìm kiếm bean của nó nhìn thấy ở parent để advise bean **do child tạo**, nếu điều kiện pointcut khớp. Đây là hai khía cạnh riêng.
5. Nếu trong child đăng ký bean trùng tên bean parent, bean local thường che bean parent trong các lần tra cứu theo tên. Không suy ra hai bean trở thành cùng một instance hay có cùng proxy.

**Vì sao?** `BeanPostProcessor` hoạt động **theo từng bean factory/container**. AOP auto-proxy creator là một `BeanPostProcessor`. Khi child gọi `getBean(...)` và lấy được bean do parent quản lý, việc tìm thấy bean đó không khiến child dùng auto-proxy creator của mình để tạo lại hoặc bọc lại instance thuộc parent.

Đây là khác biệt giữa **ranh giới sở hữu bean** và **phạm vi tra cứu bean**.

## 4. Ma trận bật/tắt AOP ở parent và child

Để ma trận có thể kiểm chứng, giả sử:
- Parent sở hữu một bean `ParentService` và một aspect với pointcut khớp riêng `ParentService.work()`.
- Child sở hữu một bean `ChildService` và một aspect với pointcut khớp riêng `ChildService.work()`.
- Cả hai service là class có method `public`, không `final`, và được gọi từ bên ngoài qua bean lấy từ Spring.
- Không có auto-proxy creator nào khác được cấu hình; vì vậy kết quả trong bảng chỉ nói về **AOP được bật qua `@EnableAspectJAutoProxy`**.

| Bật AOP tại parent | Bật AOP tại child | `ParentService` có Spring AOP proxy? | `ChildService` có Spring AOP proxy? | Giải thích |
| --- | --- | --- | --- | --- |
| Không | Không | Không | Không | Không context nào có auto-proxy creator cho `@AspectJ`. |
| Có | Không | Có | Không | Parent xử lý bean của parent; child không có auto-proxy creator tương ứng. |
| Không | Có | Không | Có | Child xử lý bean của child; bean parent không bị child proxy ngược trở lại. |
| Có | Có | Có | Có | Mỗi context chủ động xử lý bean của chính mình. |

Ma trận nêu **trạng thái proxy** trong mô hình cô lập trên. Ở hệ thống thực, các cơ chế khác (ví dụ giao dịch khai báo hoặc một cấu hình AOP khác) cũng có thể tạo proxy; hãy kiểm tra các bean cụ thể thay vì suy diễn chỉ từ sự hiện diện của `@EnableAspectJAutoProxy`.

### Trường hợp dễ hiểu nhầm

Giả sử **chỉ child bật AOP**, rồi làm:

```java
ParentService inherited = childContext.getBean(ParentService.class);
```

Biến `inherited` vẫn trỏ tới bean (hoặc proxy nếu có) mà parent đã tạo. Nó **không tự được chuyển thành proxy của child**. Ngược lại, nếu parent đã bật AOP và trả về proxy cho `ParentService`, child nhìn thấy proxy parent đó ngay cả khi child không bật AOP.

## 5. Ví dụ Java chạy độc lập: kiểm chứng cả bốn trường hợp

Ví dụ dùng **Java 17+, Spring Framework 6.1.14 và Gradle**. Đây là chương trình Spring Framework thuần, cố tình **không dùng Spring Boot**, nhằm kiểm soát rõ context nào có `@EnableAspectJAutoProxy`. Các lớp không dùng component scanning để không vô tình đăng ký bean vào sai context.

### 5.1. Tạo project Gradle

Cấu trúc:

```text
spring-aop-context-scope/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/scope/
    ├── ContextScopeDemo.java
    ├── ParentService.java
    ├── ChildService.java
    ├── ParentTrace.java
    └── ChildTrace.java
```

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-context-scope'
```

`build.gradle`:

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation 'org.springframework:spring-context:6.1.14'
    implementation 'org.springframework:spring-aop:6.1.14'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'demo.scope.ContextScopeDemo'
}
```

`spring-aop` chứa hạ tầng tạo proxy; `spring-context` cung cấp `ApplicationContext` và cấu hình Java; `aspectjweaver` là thư viện Spring cần để xử lý `@AspectJ` và pointcut expression. Sự có mặt của ba dependency **chưa tự bật** `@EnableAspectJAutoProxy` trong ví dụ Framework thuần này.

### 5.2. Hai service thuộc hai context

`src/main/java/demo/scope/ParentService.java`:

```java
package demo.scope;

public class ParentService {
    public String work() {
        return "parent-result";
    }
}
```

`src/main/java/demo/scope/ChildService.java`:

```java
package demo.scope;

public class ChildService {
    public String work() {
        return "child-result";
    }
}
```

### 5.3. Hai aspect có điểm khớp tách biệt

`src/main/java/demo/scope/ParentTrace.java`:

```java
package demo.scope;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class ParentTrace {
    @Before("execution(public * demo.scope.ParentService.work(..))")
    public void beforeWork() {
        System.out.println("advice:parent");
    }
}
```

`src/main/java/demo/scope/ChildTrace.java`:

```java
package demo.scope;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class ChildTrace {
    @Before("execution(public * demo.scope.ChildService.work(..))")
    public void beforeWork() {
        System.out.println("advice:child");
    }
}
```

Hai aspect là các lớp `@Aspect` bình thường; chúng sẽ được đưa vào Spring bằng `@Bean` trong phần sau. Pointcut của parent không khớp `ChildService` và ngược lại. Vì vậy, kể cả khi child nhìn thấy `ParentTrace` thông qua parent, log cho `ChildService` vẫn chỉ do `ChildTrace` tạo ra. Cách thiết kế này giúp thấy rõ **proxy được tạo trong context nào**, không phụ thuộc vào giả định sai rằng child hoàn toàn không nhìn thấy aspect parent.

### 5.4. Đăng ký bean và cấu hình bật AOP theo từng context

`src/main/java/demo/scope/ContextScopeDemo.java`:

```java
package demo.scope;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class ContextScopeDemo {

    @Configuration(proxyBeanMethods = false)
    static class ParentBeans {
        @Bean
        ParentService parentService() {
            return new ParentService();
        }

        @Bean
        ParentTrace parentTrace() {
            return new ParentTrace();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ChildBeans {
        @Bean
        ChildService childService() {
            return new ChildService();
        }

        @Bean
        ChildTrace childTrace() {
            return new ChildTrace();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class EnableParentAop {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class EnableChildAop {
    }

    public static void main(String[] args) {
        runCase("NONE", false, false);
        runCase("PARENT_ONLY", true, false);
        runCase("CHILD_ONLY", false, true);
        runCase("BOTH", true, true);
    }

    private static void runCase(String name,
                                boolean enableParent,
                                boolean enableChild) {
        try (AnnotationConfigApplicationContext parent =
                 new AnnotationConfigApplicationContext()) {

            parent.register(ParentBeans.class);
            if (enableParent) {
                parent.register(EnableParentAop.class);
            }
            parent.refresh();

            try (AnnotationConfigApplicationContext child =
                     new AnnotationConfigApplicationContext()) {

                child.setParent(parent);
                child.register(ChildBeans.class);
                if (enableChild) {
                    child.register(EnableChildAop.class);
                }
                child.refresh();

                ParentService p = parent.getBean(ParentService.class);
                ChildService c = child.getBean(ChildService.class);
                ParentService inherited =
                    child.getBean(ParentService.class);

                System.out.printf(
                    "%s parentProxy=%s childProxy=%s sameParent=%s%n",
                    name,
                    AopUtils.isAopProxy(p),
                    AopUtils.isAopProxy(c),
                    p == inherited
                );

                System.out.println("parent=" + p.work());
                System.out.println("child=" + c.work());
            }
        }
    }
}
```

**Giải thích chính xác vai trò từng lớp:**
- `ParentBeans` đăng ký `ParentService` và `ParentTrace` **vào parent**, bất kể parent có bật AOP hay không.
- `ChildBeans` đăng ký `ChildService` và `ChildTrace` **vào child**.
- `EnableParentAop` và `EnableChildAop` có cùng annotation, nhưng được đăng ký vào **hai context khác nhau**, nên có hai phạm vi cấu hình độc lập.
- `child.setParent(parent)` thiết lập quyền tra cứu bean của parent từ child; nó không khiến auto-proxy creator trở thành một `BeanPostProcessor` dùng chung.
- `AopUtils.isAopProxy(...)` cho biết đối tượng được lấy ra có phải Spring AOP proxy hay không. So sánh `p == inherited` chứng minh child lấy lại **đúng instance do parent cung cấp**.

### 5.5. Chạy chương trình và đối chiếu kết quả

Từ thư mục chứa `build.gradle`:

```bash
gradle run
```

Hoặc, nếu dự án đã có Gradle Wrapper, dùng `./gradlew run` (Linux/macOS) hay `gradlew.bat run` (Windows).

Các dòng do **mã ứng dụng** in ra (không kể thông tin build của Gradle hay logger môi trường) theo thứ tự:

```text
NONE parentProxy=false childProxy=false sameParent=true
parent=parent-result
child=child-result
PARENT_ONLY parentProxy=true childProxy=false sameParent=true
advice:parent
parent=parent-result
child=child-result
CHILD_ONLY parentProxy=false childProxy=true sameParent=true
parent=parent-result
advice:child
child=child-result
BOTH parentProxy=true childProxy=true sameParent=true
advice:parent
parent=parent-result
advice:child
child=child-result
```

**Đọc kết quả:**
- `NONE`: cả hai service vẫn là Spring bean, nhưng không có AOP proxy vì chưa bật auto-proxy.
- `PARENT_ONLY`: advice của parent xuất hiện cho `ParentService`; `ChildService` vẫn là bean thường, dù child có cha đã bật AOP.
- `CHILD_ONLY`: `ChildService` có advice; `ParentService` không bị child xử lý lại và `sameParent=true`.
- `BOTH`: mỗi context tạo proxy cho service do nó sở hữu, và mỗi advice xuất hiện đúng tại lời gọi khớp pointcut.

Ví dụ này minh họa **ranh giới cấu hình `@AspectJ`**, không phải cách chạy AOP cho object được tạo bằng `new` ngoài Spring. Các lời gọi đều dùng bean được container trả về.

## 6. Ứng dụng vào Spring MVC với root context và DispatcherServlet

Trong ứng dụng web Spring MVC cấu hình kiểu truyền thống, có thể có:

```text
Root WebApplicationContext
├── Service, Repository, shared infrastructure
├── @Aspect dùng cho service
└── RootAopConfig (@EnableAspectJAutoProxy)
          |
          +--> DispatcherServlet A child WebApplicationContext
          |    ├── Controller của servlet A
          |    └── ServletAopConfig (@EnableAspectJAutoProxy) nếu cần
          |
          +--> DispatcherServlet B child WebApplicationContext
               ├── Controller của servlet B
               └── ServletAopConfig (@EnableAspectJAutoProxy) nếu cần
```

Nếu pointcut nhắm đến **service được tạo trong root**, cấu hình Spring AOP nên tồn tại trong **root context** để root có thể tạo proxy cho service. Nếu pointcut nhắm đến **controller do context của `DispatcherServlet` tạo**, hãy bảo đảm context của servlet đó cũng có hỗ trợ auto-proxy phù hợp.

Cấu hình `@EnableAspectJAutoProxy` **chỉ ở root** sẽ không khiến toàn bộ controller thuộc các servlet child tự nhận proxy. Ngược lại, chỉ đặt annotation ở child cũng không có tác dụng hồi tố lên service đã được root tạo.

Một mẫu cấu hình rút gọn, trong đó mỗi config được **nạp vào đúng context**:

```java
// Nạp trong root context (minh họa cấu hình, không phải ứng dụng độc lập).
@Configuration
@EnableAspectJAutoProxy
public class RootAopConfig {
}

// Nạp trong context của một DispatcherServlet nếu cần
// advise các bean do chính servlet context đó tạo.
@Configuration
@EnableAspectJAutoProxy
public class ServletAopConfig {
}
```

Không đăng ký trùng lặp các service/controller/aspect ngoài ý muốn vào nhiều context chỉ để “bật AOP toàn cục”. Cần xác định trước bean nào là của root, bean nào là của từng servlet và điểm cắt có khớp bean mong muốn hay không.

### Kiểm tra phạm vi thật sự

Có thể ghi log hoặc assert trực tiếp:

```java
Object service = rootContext.getBean("orderService");
Object controller = servletContext.getBean("orderController");

System.out.println(AopUtils.isAopProxy(service));
System.out.println(AopUtils.isAopProxy(controller));
```

Đoạn trên chỉ minh họa cách kiểm tra, giả sử hai bean có các tên đã nêu và đã được đăng ký. Một kết quả `false` **không tự chứng minh cấu hình bị lỗi**: có thể pointcut không khớp, không có advisor tương ứng, hoặc bean không thể được proxy theo cơ chế đang chọn.

## 7. Spring Boot: bật tự động không làm mất ranh giới context

Spring Boot có **AOP auto-configuration**. Khi có các dependency AspectJ phù hợp (thường qua `spring-boot-starter-aop`), Boot có thể bật `@AspectJ` auto-proxy tự động mà không yêu cầu ứng dụng viết `@EnableAspectJAutoProxy` thủ công.

Ví dụ dependency Gradle của ứng dụng Boot:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

Với Spring Boot 3.3:
- `spring.aop.auto=true` là hành vi auto-configuration mặc định khi điều kiện đáp ứng; đặt `spring.aop.auto=false` để vô hiệu cơ chế AOP auto-configuration này.
- `spring.aop.proxy-target-class=true` là mặc định proxy theo lớp của Boot; đặt `false` để ưu tiên JDK proxy khi phù hợp.
- Khi ứng dụng chỉ có **một** context, thường không phải suy nghĩ về nhiều phạm vi container.
- Nếu hệ thống tạo **nhiều** context (ví dụ context con hoặc cấu trúc web tùy biến), hãy kiểm tra **auto-configuration nào thực sự chạy ở từng context**, bean nào thuộc context nào và proxy nào được tạo ở đó. Không nên mặc định rằng vì Boot đã bật AOP trong context chính nên AOP tự áp dụng cho mọi context tự tạo.

**Không nhầm `@EnableAspectJAutoProxy` với Spring Boot property:** annotation là cách bật tường minh ở một context; Boot property điều khiển phần auto-configuration do Boot quản lý. Đặt `spring.aop.auto=false` không phải cam kết tắt mọi proxy do các cấu hình thủ công hoặc cơ chế Spring khác đã đăng ký.

## 8. Các cấu hình và tình huống dễ sai

| Tình huống | Ngộ nhận thường gặp | Cách xác định/sửa |
| --- | --- | --- |
| `@EnableAspectJAutoProxy` ở parent; aspect/target ở child | “Child tự nhận AOP từ parent” | Kiểm tra auto-proxy creator trong child; kích hoạt AOP đúng child khi cần advise bean child. |
| `@EnableAspectJAutoProxy` chỉ ở child; target bean từ parent | “Child sẽ bọc proxy thêm cho bean parent” | Xác định context sở hữu bean; cấu hình AOP ở parent nếu cần proxy lúc parent tạo bean. |
| Aspect ở parent, target ở child | “Không thể dùng aspect parent vì khác context” | Phân biệt khả năng child nhìn thấy bean/aspect tổ tiên và việc child có auto-proxy creator; kiểm tra pointcut cụ thể. |
| Aspect chỉ ở child, target ở parent | “Parent tìm thấy aspect child” | Parent không tự nhìn thấy child; đưa aspect/logic advisor phù hợp vào parent nếu target thuộc parent. |
| Hai child context cùng parent | “Bật AOP ở child A cũng bật cho child B” | Mỗi child có BeanFactory riêng; cấu hình độc lập ở từng child theo nhu cầu. |
| Cùng tên bean ở parent/child | “`getBean` ở child chắc chắn trả bean parent” | Xem định nghĩa bean local, bean shadowing và context thực sự tạo instance. |
| Chuyển `@Configuration` AOP sang component scan khác | “Bean vẫn ở context cũ” | Xác minh phạm vi `@ComponentScan` / `@Import`, nơi từng config được đăng ký và nơi bean được tạo. |
| `isAopProxy(bean) == false` | “`@EnableAspectJAutoProxy` không hoạt động” | Kiểm tra aspect bean, pointcut, method visibility, thời điểm bean tạo và hạ tầng proxy khác. |
| `isAopProxy(bean) == true` nhưng advice không in | “Proxy luôn chạy mọi advice” | Xác minh pointcut/method và lời gọi có qua proxy; kiểm tra self-invocation. |

### Tại sao không chỉ kiểm tra annotation?

`@EnableAspectJAutoProxy` có thể được đặt trên config nhưng config **không được register trong context đang xét**. Ngoài ra, một service có thể được tạo bởi parent rồi inject vào child; quan sát nơi inject không xác định được nơi tạo proxy. Khi cần kết luận, phải nhìn vào **runtime bean và context sở hữu**, không chỉ vào package hoặc vị trí đặt lớp Java.

### Điều chỉnh `proxyTargetClass` hoặc `exposeProxy` có lan qua context không?

Các thuộc tính như:

```java
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
```

được khai báo cho hạ tầng trong context tương ứng. `proxyTargetClass=true` ưu tiên proxy theo lớp; `exposeProxy=true` cho phép truy cập proxy hiện hành qua `AopContext` khi đang ở trong lời gọi proxy phù hợp. Chúng **không** khiến auto-proxy creator trong context này hậu xử lý bean do context khác tạo, và `exposeProxy` không tạo ra một “proxy toàn ứng dụng” dùng chung giữa các context.

Hạn chế của proxy như self-invocation, `final` và `private` vẫn áp dụng bất kể cách tổ chức context; chúng thuộc cơ chế Spring AOP theo proxy, không phải lỗi kế thừa cấu hình.

## 9. Quy trình chẩn đoán khi gặp AOP không chạy trong nhiều context

1. **Liệt kê context:** Có bao nhiêu `ApplicationContext`? Context nào là parent, child hoặc sibling? Context nào đã `refresh()`?
2. **Xác định chủ sở hữu bean:** Bean target định nghĩa và được tạo ở context nào? Nếu lấy qua child, kiểm tra có phải bean inherited từ parent không.
3. **Kiểm tra bật AOP:** `@EnableAspectJAutoProxy` được nạp vào context nào? Với Boot, AOP auto-configuration có chạy tại đó không?
4. **Kiểm tra aspect/advisor:** Aspect bean tồn tại và được auto-proxy creator tương ứng tìm thấy không? Pointcut có khớp đúng phương thức target không?
5. **Kiểm tra proxy thực tế:** Dùng `AopUtils.isAopProxy`; nếu cần có thể dùng `AopUtils.isJdkDynamicProxy` / `AopUtils.isCglibProxy`.
6. **Kiểm tra đường gọi:** Client gọi qua proxy Spring hay trực tiếp target/`this`? Method có phù hợp với proxy type không?
7. **Kiểm tra thời điểm:** Thay đổi cấu hình sau khi context đã tạo bean không mặc nhiên khiến bean đó được tự động proxy lại; sửa cấu hình khởi tạo và chạy lại context để kiểm chứng.

Khi debug sâu một bean proxy, có thể kiểm tra các `Advisor` mà Spring gắn vào đối tượng nếu proxy triển khai giao diện `org.springframework.aop.framework.Advised`:

```java
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
```

Đây là đoạn kiểm tra dùng khi có biến `bean` đã lấy từ Spring; không phải chương trình Java độc lập. Nó giúp phân biệt “bean có proxy” với “proxy có đúng advisor mong đợi”. Một số proxy hoặc cấu hình đặc biệt có thể không cho xem/chỉnh advisor qua giao diện này, vì thế kết luận nên dựa trên cả thực nghiệm hành vi.

## 10. Kết luận thực hành

Trong Spring, **cấu hình auto-proxy thuộc về từng `ApplicationContext`**, còn **bean, aspect và advisor có các quy tắc sở hữu/tra cứu riêng**. Với cây cha–con, hãy nhớ:

- Bật `@EnableAspectJAutoProxy` tại **context tạo target bean cần advise**.
- Đừng coi việc child tra cứu được bean parent là bằng chứng child tạo lại proxy cho bean đó.
- Khi cần advise bean thuộc nhiều context, kiểm tra cơ chế AOP ở **mỗi context có bean mục tiêu** và xác định aspect/advisor nào được context đó nhìn thấy.
- Dùng `AopUtils.isAopProxy(...)` và lời gọi method qua bean Spring để xác minh giả thuyết bằng runtime.

### Tài liệu Spring chính thức

- [Spring Framework 6.0 Javadoc — `@EnableAspectJAutoProxy`](https://docs.spring.io/spring-framework/docs/6.0.23/javadoc-api/org/springframework/context/annotation/EnableAspectJAutoProxy.html): ghi rõ annotation chỉ áp dụng cho local application context, kể cả ví dụ root/`DispatcherServlet`.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): bật hỗ trợ `@AspectJ` và yêu cầu dependency.
- [Spring Framework — Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html): phạm vi per-container của `BeanPostProcessor`.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): cơ chế proxy, cấu hình proxy type và giới hạn.
- [Spring Boot 3.3 — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/3.3/reference/features/aop.html): AOP auto-configuration của Boot.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-setup-007">Phân biệt mặc định proxy của Spring Framework và cấu hình từ Spring Boot</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt mặc định proxy của Spring Framework và cấu hình từ Spring Boot

## 1. Bài toán: cùng một Spring bean, vì sao có hai kiểu proxy?

Khi dùng Spring AOP, một bean thỏa điều kiện advice có thể được thay bằng **AOP proxy**. Điều dễ nhầm là cấu hình mặc định của **Spring Framework thuần** và **Spring Boot auto-configuration** khác nhau:

- Trong **Spring Framework**, cơ chế proxy mặc định chọn **JDK dynamic proxy** nếu target triển khai interface; nếu target không có interface phù hợp, Spring dùng **class-based proxy (CGLIB)**.
- Trong **Spring Boot**, auto-configuration AOP mặc định đặt `spring.aop.proxy-target-class=true`, tức ưu tiên **class-based proxy (CGLIB)**, kể cả target có interface.
- Với ứng dụng Boot, đặt `spring.aop.proxy-target-class=false` cho phép Spring dùng **JDK dynamic proxy khi target có interface**; nếu target không có interface, class-based proxy vẫn cần thiết.

Đây là khác biệt của **cấu hình Spring AOP**, không phải hai hệ thống AOP khác nhau. Boot xây dựng trên Spring Framework và bật một lựa chọn proxy khác trong phần auto-configuration.

**Phạm vi bài:** cách bật AOP, cơ chế chọn JDK/CGLIB, cấu hình Boot/Framework, kiểm tra thực tế và các hệ quả với bean Spring. Các khái niệm Aspect tổng quát không được mở rộng ở đây.

## 2. Bảng so sánh mặc định

Giả sử Spring AOP thực sự cần tạo proxy cho bean (có advisor áp dụng; bean do Spring quản lý):

| Target bean | Spring Framework thuần, cấu hình mặc định | Spring Boot có AOP auto-configuration mặc định | Boot với `spring.aop.proxy-target-class=false` |
| --- | --- | --- | --- |
| `PaymentServiceImpl implements PaymentService` | JDK dynamic proxy | CGLIB class-based proxy | JDK dynamic proxy |
| `ReportService` không implements interface | CGLIB class-based proxy | CGLIB class-based proxy | CGLIB class-based proxy |
| `final class` cần class-based proxy | Không thể tạo proxy subclass bình thường | Không thể tạo proxy subclass bình thường | Nếu không có interface để proxy, vẫn không thể tạo proxy subclass |
| Bean không có advisor nào áp dụng | Thường không có AOP proxy | Thường không có AOP proxy | Thường không có AOP proxy |

**Điều kiện quan trọng:** bảng nói về proxy tạo bởi cơ chế Spring AOP được cấu hình như mô tả. Một ứng dụng có thể có các loại proxy khác (ví dụ scoped proxies, proxy tùy biến hoặc hạ tầng khác), và một cấu hình mạnh hơn có thể làm thay đổi lựa chọn cuối cùng. Không suy loại proxy chỉ từ việc project có hoặc không có Spring Boot.

```text
                     Có cần Spring AOP proxy không?
                                  |
                           Có advisor khớp
                                  |
                             +----v----+
                             | Target  |
                             +----+----+
                                  |
                +-----------------+--------------------+
                |                                      |
       class-based được yêu cầu?                     Không
        (Boot thường true)                             |
                |                                      v
                v                           Target có interface?
             CGLIB                              /          \
                                             Có              Không
                                             |                 |
                                             v                 v
                                      JDK dynamic proxy       CGLIB
```

Sơ đồ mô tả quy tắc chung khi chưa có cấu hình đặc biệt hoặc override khác. `proxy-target-class=false` **không** có nghĩa “cấm CGLIB”; nó có nghĩa “không ép mọi bean dùng target-class proxy”.

## 3. Mặc định của Spring Framework thuần

Khi cấu hình `@EnableAspectJAutoProxy` trong ứng dụng Spring Framework dùng `AnnotationConfigApplicationContext`, thuộc tính `proxyTargetClass` mặc định là `false`:

```java
@Configuration
@EnableAspectJAutoProxy // proxyTargetClass mặc định false
class AopConfig {
}
```

Dạng ghi rõ ý định:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
class AopConfig {
}
```

Với cấu hình này:

1. Bean triển khai một hoặc nhiều interface: proxy thường là đối tượng JDK dynamic proxy, chỉ triển khai các interface được proxy công bố.
2. Bean không có interface có thể proxy qua JDK: Spring dùng proxy dựa trên lớp để có thể áp dụng advice lên method phù hợp.
3. Nếu chủ động đặt `@EnableAspectJAutoProxy(proxyTargetClass = true)`: Spring chọn class-based proxy, cả khi bean triển khai interface.

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class ClassProxyConfig {
}
```

**Bật auto-proxy không đồng nghĩa mọi bean đều bị proxy.** `@EnableAspectJAutoProxy` bật xử lý advisor/aspect; Spring còn phải tìm thấy advice phù hợp với bean. Một lớp chỉ có `@Aspect` mà không được đăng ký thành Spring bean sẽ không tự kích hoạt advice.

Với XML, thuộc tính tương ứng là `<aop:aspectj-autoproxy proxy-target-class="true"/>`. Điều này cũng chỉ ảnh hưởng cơ chế tạo proxy; nó không mở rộng Spring AOP thành chặn constructor hoặc truy cập field.

### Vì sao Spring Framework ưu tiên JDK khi có interface?

JDK proxy triển khai interface mà caller sử dụng; caller gọi qua interface và Spring chèn advice trước khi chuyển đến target. Điều này phù hợp với code inject theo abstraction:

```java
public interface PaymentService {
    String pay(String id);
}

// Dùng interface trong client:
private final PaymentService paymentService;
```

Một JDK proxy **không phải instance của implementation class**. Vì vậy inject/ép kiểu theo `PaymentServiceImpl` có thể thất bại ngay cả khi inject `PaymentService` hoàn toàn ổn.

## 4. Mặc định Spring Boot và điều kiện auto-configuration

Spring Boot có cấu hình AOP tự động. Trong các phiên bản Spring Boot 3.x và 4.x được tài liệu chính thức mô tả, các giá trị thông dụng là:

```properties
spring.aop.auto=true
spring.aop.proxy-target-class=true
```

Hai property giải quyết **hai câu hỏi khác nhau**:

- `spring.aop.auto`: Boot có tự động bật hỗ trợ `@AspectJ` auto-proxy khi điều kiện classpath phù hợp hay không. Mặc định `true`.
- `spring.aop.proxy-target-class`: khi Boot thiết lập AOP auto-proxy, có **ưu tiên proxy bằng subclass của target class** hay không. Mặc định `true`.

Thường chỉ cần thêm dependency `spring-boot-starter-aop` vào dự án Spring Boot; starter đưa hỗ trợ Spring AOP cùng các thư viện AspectJ cần cho kiểu pointcut `@AspectJ` lên classpath. Boot có thể tự bật auto-proxy khi AspectJ hiện diện, nên **không cần tự khai báo `@EnableAspectJAutoProxy` trong trường hợp tiêu chuẩn**.

Ví dụ Maven sử dụng Spring Boot 3.3.x:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Đây là **đoạn dependency** dành cho một project đã quản lý version qua Boot parent hoặc BOM. Không tự thêm version vào dependency này nếu Boot dependency management đã quản lý nó.

Cách đổi về lựa chọn JDK proxy cho target có interface trong `src/main/resources/application.properties`:

```properties
spring.aop.proxy-target-class=false
```

Hoặc `application.yml`:

```yaml
spring:
  aop:
    proxy-target-class: false
```

Với `false`, cần nhớ rằng target không có interface vẫn phải sử dụng class-based proxy nếu muốn Spring AOP chặn method của nó.

### Vậy có nên thêm `@EnableAspectJAutoProxy` vào ứng dụng Boot?

Thông thường **không cần** nếu auto-configuration AOP của Boot đã hoạt động. Có thể khai báo tường minh khi ứng dụng cần kiểm soát riêng, nhưng kết quả phải đánh giá trên **toàn bộ cấu hình auto-proxy của application context**.

Một lỗi dễ mắc:

```java
// Trong ứng dụng Boot vẫn để mặc định
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
class ExplicitAopConfig {
}
```

Nhiều người kỳ vọng đoạn trên ép mọi bean quay về JDK. Tuy nhiên Spring có cơ chế **hợp nhất / nâng cấp auto-proxy creator**; cấu hình `proxyTargetClass=true` từ một nơi khác có thể thắng cấu hình `false`. Để chọn JDK trong Boot tiêu chuẩn, cấu hình trực tiếp `spring.aop.proxy-target-class=false` và tránh nơi khác ép target-class proxy; xác minh loại proxy sau khi context khởi động.

Ngược lại, đặt `spring.aop.auto=false` chỉ **tắt việc Boot tự động bật @AspectJ auto-proxy**. Nó không phải nút “cấm toàn bộ proxy” cho ứng dụng: cấu hình `@EnableAspectJAutoProxy` tường minh, `@EnableTransactionManagement`, proxy tùy biến hoặc cơ chế hạ tầng khác vẫn có thể tạo proxy theo điều kiện riêng.

## 5. Thí nghiệm chạy được: cùng bean trong Spring Framework và Boot

Thí nghiệm sử dụng **Java 17+, Maven, Spring Boot 3.3.13** (bao gồm Spring Framework 6.x theo dependency management của Boot). Có **một service triển khai interface** và **một aspect**. Hai context được khởi chạy riêng biệt trong cùng chương trình:

- `CORE`: `AnnotationConfigApplicationContext` + `@EnableAspectJAutoProxy(proxyTargetClass=false)`; không bật Boot auto-configuration.
- `BOOT`: `SpringApplicationBuilder` + Boot `@EnableAutoConfiguration`; nhận giá trị `spring.aop.proxy-target-class` từ property/environment.
- Cùng một service và aspect được khai báo trong `SharedBeans`, để khác biệt đến từ cấu hình context chứ không phải khác biệt code nghiệp vụ.

Cấu trúc:

```text
spring-proxy-defaults/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── example/
                └── aop/
                    └── ProxyDefaultsDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-proxy-defaults</artifactId>
    <version>1.0-SNAPSHOT</version>

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
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/example/aop/ProxyDefaultsDemo.java`:

```java
package example.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public class ProxyDefaultsDemo {

    public interface PaymentService {
        String pay(String orderId);
    }

    public static class PaymentServiceImpl implements PaymentService {
        @Override
        public String pay(String orderId) {
            System.out.println("TARGET " + orderId);
            return "PAID:" + orderId;
        }
    }

    @Aspect
    public static class AuditAspect {
        @Before("execution(* example.aop..*.pay(..))")
        public void beforePay(JoinPoint joinPoint) {
            System.out.println("ADVICE " + joinPoint.getSignature().getName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SharedBeans {
        @Bean
        PaymentService paymentService() {
            return new PaymentServiceImpl();
        }

        @Bean
        AuditAspect auditAspect() {
            return new AuditAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    @Import(SharedBeans.class)
    public static class CoreConfig {
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(SharedBeans.class)
    public static class BootConfig {
    }

    private static void inspect(String mode, Object bean) {
        System.out.println(mode
                + " aop=" + AopUtils.isAopProxy(bean)
                + ", jdk=" + AopUtils.isJdkDynamicProxy(bean)
                + ", cglib=" + AopUtils.isCglibProxy(bean));
    }

    public static void main(String[] args) {
        try (var core = new AnnotationConfigApplicationContext(CoreConfig.class)) {
            PaymentService bean = core.getBean(PaymentService.class);
            inspect("CORE", bean);
            System.out.println("CORE_RESULT " + bean.pay("A01"));
        }

        try (var boot = new SpringApplicationBuilder(BootConfig.class)
                .bannerMode(Banner.Mode.OFF)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run(args)) {
            PaymentService bean = boot.getBean(PaymentService.class);
            inspect("BOOT", bean);
            System.out.println("BOOT_RESULT " + bean.pay("B02"));
        }
    }
}
```

**Lần 1 — Boot với mặc định:**

```bash
mvn -q compile exec:java -Dexec.mainClass=example.aop.ProxyDefaultsDemo
```

Các dòng quan sát trọng tâm từ chương trình:

```text
CORE aop=true, jdk=true, cglib=false
ADVICE pay
TARGET A01
CORE_RESULT PAID:A01
BOOT aop=true, jdk=false, cglib=true
ADVICE pay
TARGET B02
BOOT_RESULT PAID:B02
```

**Lần 2 — yêu cầu Boot chọn JDK proxy khi có interface:**

```bash
mvn -q compile exec:java -Dexec.mainClass=example.aop.ProxyDefaultsDemo -Dexec.args=--spring.aop.proxy-target-class=false
```

Các dòng quan sát trọng tâm:

```text
CORE aop=true, jdk=true, cglib=false
ADVICE pay
TARGET A01
CORE_RESULT PAID:A01
BOOT aop=true, jdk=true, cglib=false
ADVICE pay
TARGET B02
BOOT_RESULT PAID:B02
```

Các đoạn trên liệt kê dòng do chương trình tự in; Boot hoặc Maven có thể có thêm logging tùy môi trường. `--spring.aop.proxy-target-class=false` được truyền tới `SpringApplicationBuilder.run(args)` của context Boot; context `CORE` không sử dụng cơ chế cấu hình property của Boot.

**Vì sao thử nghiệm chứng minh được khác biệt?** Hai context có cùng target type `PaymentServiceImpl implements PaymentService`, cùng `@Aspect`, cùng pointcut. Chỉ cơ chế đăng ký AOP khác: Spring Framework thuần dùng mặc định interface proxy, còn Boot áp giá trị property mặc định của mình. `AopUtils` đo loại proxy trên **bean lấy từ container**, không kiểm tra class `PaymentServiceImpl` tạo bằng `new`.

## 6. Khi đổi kiểu proxy, điều gì thực sự thay đổi?

### 6.1. Khả năng inject và ép kiểu

Với **JDK dynamic proxy**:

```java
PaymentService service = context.getBean(PaymentService.class);
// OK: proxy triển khai interface PaymentService.

// Không giả định mã sau hợp lệ:
PaymentServiceImpl impl = (PaymentServiceImpl) service;
```

Cast sang implementation ở dòng thứ hai thường ném `ClassCastException` nếu `service` là JDK proxy. Với **CGLIB class-based proxy**, proxy là subclass của implementation (nếu class có thể subclass), nên tham chiếu kiểu implementation có thể hoạt động.

Vì vậy, khi thay `spring.aop.proxy-target-class=true` thành `false` trong Boot, cần kiểm tra các nơi inject trực tiếp implementation hoặc dùng `getBean(Implementation.class)`. Việc cấu hình AOP có thể ảnh hưởng khả năng resolve bean theo kiểu mà application sử dụng.

### 6.2. Phương thức có thể được advice

- JDK proxy **công bố interface**; caller thông thường gọi qua interface đã được proxy.
- CGLIB proxy dựa trên subclass, có thể xử lý lời gọi tới phương thức của target class phù hợp với giới hạn proxying, kể cả phương thức public không khai báo trên business interface.
- `final class` không thể tạo CGLIB subclass; `final method` không thể được advice qua override; `private method` và phương thức không nhìn thấy từ subclass cũng không thể advice bằng cách đó.
- **Đổi sang CGLIB không giải quyết self-invocation**: `this.someMethod()` bên trong target vẫn không đi qua Spring proxy một lần nữa.

Với phương thức package-private/protected, những điều kiện về visibility, kiểu proxy và phiên bản Spring có thể quyết định kết quả; đừng diễn dịch “CGLIB chặn mọi method” như một lời hứa tuyệt đối.

### 6.3. Hiệu năng và tương thích

Không nên chọn JDK hay CGLIB chỉ bằng giả định rằng kiểu nào nhanh hơn. Chi phí advice, reflection/dispatch, JVM tối ưu hóa và workload thực tế đều có thể lớn hơn chênh lệch proxy. Hãy dùng mặc định phù hợp, ưu tiên API ổn định, và benchmark trên ứng dụng cụ thể khi thật sự có yêu cầu hiệu năng.

Class-based proxy còn có thể bị ảnh hưởng bởi module-path và Java module access vì cần sinh subclass. Những kiểu nằm trong module/package không mở để truy cập cần kiểm tra giới hạn JVM tương ứng. Kết quả lỗi thường liên quan khả năng subclass/access, không phải biểu thức pointcut.

## 7. Auto-configuration chồng nhau: vì sao đặt `false` đôi khi vẫn là CGLIB?

Trong ứng dụng Spring có thể tồn tại nhiều nguồn cấu hình auto-proxy như `@EnableAspectJAutoProxy`, hạ tầng transaction, các thành phần XML và cấu hình khác. Spring thường thống nhất chúng về một auto-proxy creator, với các thiết lập mạnh hơn như `proxyTargetClass=true` có thể chi phối toàn cục. Vì vậy:

```java
// Ví dụ một nơi ép class-based proxy:
@EnableAspectJAutoProxy(proxyTargetClass = true)
class ForceClassProxy {
}
```

Nếu bean cấu hình này được nạp, việc ứng dụng Boot đặt `spring.aop.proxy-target-class=false` không có nghĩa chắc chắn mọi proxy lập tức thành JDK. Cần tìm mọi nơi ép target-class proxy trong **application context cụ thể**, không chỉ xem một dòng property.

Lưu ý theo phiên bản: Spring Framework **7.0 trở lên** còn có `@Proxyable` để chọn proxy type cho từng bean trong những trường hợp được hỗ trợ; quy tắc này là khả năng mới và **không áp dụng mặc nhiên cho Spring Framework 6.x / Spring Boot 3.x** của ví dụ. Nếu dùng phiên bản đó, phải xem tài liệu đúng release để xác định độ ưu tiên cấu hình.

### Property được nạp từ đâu?

Trong Boot, property có thể được khai báo qua `application.properties`, `application.yml`, profile-specific configuration, biến môi trường, tham số command line và các nguồn `Environment` khác. Giá trị thực tế sau áp dụng thứ tự ưu tiên có thể khác file bạn đang mở.

Ví dụ biến môi trường chuẩn theo tên property:

```bash
SPRING_AOP_PROXY_TARGET_CLASS=false
```

Với ví dụ chạy dòng lệnh ở phần 5, giá trị `--spring.aop.proxy-target-class=false` chỉ tác động đến context Boot nhận `args`. Nếu đang chẩn đoán ứng dụng triển khai thật, kiểm tra giá trị của `Environment.getProperty("spring.aop.proxy-target-class")` và kết hợp kiểm tra bean proxy; đừng dựa duy nhất vào file cấu hình.

### Kiểm tra trạng thái hỗ trợ AOP

- `spring.aop.auto=true` và có AspectJ trên classpath: Boot có thể tự bật xử lý `@AspectJ`.
- `spring.aop.auto=false`: dừng phần Boot tự động thêm xử lý `@AspectJ`; các nguồn cấu hình AOP tường minh/hạ tầng khác vẫn có thể tạo proxy.
- Thiếu `spring-boot-starter-aop` không đủ để kết luận AOP bất khả thi; thư viện liên quan có thể vào classpath từ dependency khác. Kiểm tra dependency tree và cấu hình hiệu lực.
- Một bean không có AOP proxy vẫn có thể hoạt động nghiệp vụ bình thường, chỉ không đi qua chuỗi advice AOP tương ứng.

## 8. Quy trình điều tra một ứng dụng thực tế

Khi một project Boot mặc định ra CGLIB nhưng bạn kỳ vọng JDK, hoặc ngược lại:

1. **Xác định môi trường:** Spring Framework thuần hay Spring Boot? Phiên bản cụ thể? `@EnableAutoConfiguration` thực sự được chạy ở context nào?
2. **Kiểm tra classpath:** có starter AOP/AspectJ và advisor hoặc aspect bean phù hợp không?
3. **Kiểm tra cấu hình có hiệu lực:** `spring.aop.auto`, `spring.aop.proxy-target-class` và nơi khai báo `@EnableAspectJAutoProxy(proxyTargetClass=...)` / `@EnableTransactionManagement`.
4. **Xác định target có interface nào:** nếu không có, cấu hình `false` vẫn có thể cho CGLIB.
5. **Lấy đúng bean từ context:** dùng `AopUtils.isAopProxy(bean)`, `AopUtils.isJdkDynamicProxy(bean)` và `AopUtils.isCglibProxy(bean)`.
6. **Kiểm tra cách inject:** ứng dụng đang yêu cầu bean bằng interface hay implementation? Sau khi đổi loại proxy, cast và injection có tương thích không?
7. **Xem đường đi lời gọi:** self-invocation hay tạo object bằng `new` không đi qua proxy, dù proxy JDK hay CGLIB.
8. **Kiểm tra với test context thật:** thử cả property `true`/`false` và assert loại proxy cùng hành vi advice. Đừng test bằng cách gọi đối tượng thô ngoài Spring.

Ví dụ assert trong integration test, với bean đã xác nhận thực sự được áp dụng Spring AOP:

```java
Object bean = applicationContext.getBean(PaymentService.class);
assertTrue(AopUtils.isAopProxy(bean));
assertTrue(AopUtils.isJdkDynamicProxy(bean)); // Khi kỳ vọng JDK
assertFalse(AopUtils.isCglibProxy(bean));
```

Đây là **đoạn mã kiểm thử minh họa**, cần context/test imports và assertion library, không phải chương trình độc lập. Đổi kỳ vọng sang `isCglibProxy` khi test cấu hình Boot mặc định.

## 9. Lỗi nhận định thường gặp

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “Spring Boot cũng phải dùng JDK proxy nếu class implements interface vì Spring Framework mặc định vậy.” | Boot có thể thay lựa chọn mặc định qua AOP auto-configuration; mặc định phổ biến là CGLIB. |
| “Đặt `spring.aop.proxy-target-class=false` là toàn bộ AOP dùng JDK.” | Chỉ chọn JDK khi có interface và không có cấu hình khác ép class-based proxy. |
| “`@EnableAspectJAutoProxy(proxyTargetClass=false)` chắc chắn ghi đè mặc định Boot true.” | Nhiều thiết lập auto-proxy có thể hợp nhất; `true` từ nơi khác có thể chi phối. |
| “Spring Boot bắt buộc khai báo `@EnableAspectJAutoProxy`.” | Với starter/classpath phù hợp, Boot đã tự bật @AspectJ auto-proxy. |
| “Đổi sang CGLIB thì gọi `this.foo()` sẽ được advice.” | Self-invocation vẫn bỏ qua lời gọi qua proxy. |
| “`spring.aop.auto=false` tắt mọi cơ chế tạo proxy trong ứng dụng.” | Nó chỉ chi phối auto-configuration AOP của Boot; cấu hình và hạ tầng khác có thể tạo proxy. |
| “Bean implements interface thì nhất định là JDK proxy.” | Còn phụ thuộc cấu hình `proxyTargetClass`, auto-proxy creator và phiên bản framework. |
| “Có `@Aspect` tức là mọi bean chắc chắn có proxy.” | Aspect phải được đăng ký và pointcut/advisor phải áp dụng cho bean. |

## 10. Câu hỏi tự kiểm tra

**Câu 1.** `UserServiceImpl implements UserService`, ứng dụng Spring Framework thuần có `@EnableAspectJAutoProxy` mặc định và pointcut phù hợp. Loại proxy dự kiến?  
**Đáp án:** JDK dynamic proxy.

**Câu 2.** Cùng bean đó trong ứng dụng Boot có `spring-boot-starter-aop`, AOP auto-configuration và không ghi đè cấu hình. Loại proxy dự kiến?  
**Đáp án:** CGLIB class-based proxy theo mặc định của Boot.

**Câu 3.** Đặt `spring.aop.proxy-target-class=false` trong Boot; bean `ReportService` không implements interface. Kết quả?  
**Đáp án:** Spring vẫn cần class-based proxy nếu advisor áp dụng; `false` không “ép JDK” khi không có interface.

**Câu 4.** Đã để Boot `spring.aop.proxy-target-class=false` nhưng bean vẫn là CGLIB. Nên kiểm tra gì?  
**Đáp án:** Target có interface không; property có hiệu lực không; có cấu hình khác ép `proxyTargetClass=true` không; bean có phải AOP proxy đúng context không; có loại proxy khác không.

**Câu 5.** Đổi từ Boot CGLIB sang JDK, một controller inject trực tiếp `UserServiceImpl` bị lỗi nhưng inject `UserService` thì ổn. Vì sao?  
**Đáp án:** JDK proxy chỉ triển khai interface, không phải subtype của implementation class.

**Câu 6.** `spring.aop.auto=false` có bảo đảm không còn proxy cho transaction hoặc các cấu hình AOP khác?  
**Đáp án:** Không. Đây là cờ cho phần tự động cấu hình @AspectJ AOP của Boot; cấu hình và hạ tầng khác có thể tạo proxy.

## 11. Tài liệu chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): quy tắc JDK/CGLIB, giới hạn `final` và hành vi cấu hình auto-proxy.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): `@EnableAspectJAutoProxy` và đăng ký aspect bean.
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html): Boot mặc định CGLIB và cách chuyển sang JDK bằng `spring.aop.proxy-target-class=false`.
- [Spring Boot — Common Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html): giá trị mặc định `spring.aop.auto` và `spring.aop.proxy-target-class`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-setup-008">Ứng dụng tối thiểu để xác nhận một advice được thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Ứng dụng Spring AOP tối thiểu để xác nhận một advice được thực thi

## 1. Mục tiêu thực hành

Xây dựng một chương trình Java 17+ chỉ dùng Spring Framework, không cần web server hay cơ sở dữ liệu, để xác nhận **Spring AOP đã gọi một `@Before` advice** trước phương thức của một Spring bean.

Đầu ra cần chứng minh đủ bốn việc:

1. `ApplicationContext` chứa service và aspect đã đăng ký.
2. Đối tượng service lấy từ Spring là AOP proxy, kiểm tra bằng `AopUtils.isAopProxy(...)`.
3. Khi gọi method qua proxy, dòng `ADVICE` xuất hiện **trước** dòng `SERVICE`.
4. Bộ đếm được tăng **ngay trong advice** có giá trị `1`; chương trình chỉ in `CHECK: PASS` sau khi kiểm tra cả proxy lẫn bộ đếm.

Các annotation `@Aspect` và `@Before` có cú pháp của AspectJ nhưng được **Spring AOP** phân tích và triển khai dưới dạng proxy. Bài này chỉ kiểm tra Spring Aspect; không kích hoạt AspectJ compiler hay load-time weaving. Thư viện AspectJ hiện diện trong classpath không đồng nghĩa lớp Java đã được weave.

## 2. Chuẩn bị Maven project

Cấu trúc đầy đủ:

~~~text
spring-aop-check/
├── pom.xml
└── src/main/java/example/
    ├── App.java
    ├── NotificationService.java
    └── TraceAspect.java
~~~

Yêu cầu JDK 17 trở lên và Maven 3.9+. Các phiên bản thư viện dưới đây được cố định để ví dụ có thể tái lập; không phải lời khẳng định về bản mới nhất.

Nội dung `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aop-check</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.0</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22</version>
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
                    <mainClass>example.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp container và các annotation cấu hình bean; `spring-aop` cung cấp hạ tầng proxy cùng `AopUtils`. `aspectjweaver` cung cấp thư viện để Spring xử lý kiểu khai báo `@AspectJ` và pointcut. **Không cần tham số `-javaagent`** để chạy Spring AOP trong ví dụ này.

## 3. Service có method để advice can thiệp

Tạo `src/main/java/example/NotificationService.java`:

~~~java
package example;

public class NotificationService {
    public void deliver(String recipient) {
        System.out.println("SERVICE: Delivered to " + recipient);
    }
}
~~~

Class và method này đều là `public`, không `final`. Điều này thuận lợi cho class-based proxy được bật ở phần cấu hình. Không gắn `@Component` bởi `App` sẽ đăng ký service bằng `@Bean`, giúp nhìn rõ đối tượng nào thuộc Spring container.

## 4. Aspect dùng `@Before` và bộ đếm

Tạo `src/main/java/example/TraceAspect.java`:

~~~java
package example;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    private final AtomicInteger adviceInvocations = new AtomicInteger();

    @Before("execution(* example.NotificationService.deliver(..))")
    public void beforeDeliver() {
        adviceInvocations.incrementAndGet();
        System.out.println("ADVICE: before deliver");
    }

    public int getAdviceInvocations() {
        return adviceInvocations.get();
    }
}
~~~

Giải thích dưới góc độ **Spring AOP**:

- `@Aspect` thông báo lớp chứa pointcut/advice theo cách Spring hiểu; **không tự biến lớp thành Spring bean**.
- `execution(* example.NotificationService.deliver(..))` chọn method execution của `deliver` trong class cụ thể. Dấu `*` chấp nhận mọi kiểu trả về; `(..)` chấp nhận danh sách đối số.
- `@Before` làm Spring gọi `beforeDeliver()` khi lời gọi method khớp **đi qua proxy**. Dòng `ADVICE` chỉ được in tại đây, không nằm trong service.
- `adviceInvocations` chỉ được tăng trong method advice. Sau một lời gọi hợp lệ, giá trị `1` chứng tỏ Spring đã kích hoạt advice thật. `AtomicInteger` là cách đếm đơn giản, an toàn nếu sau này có nhiều thread, dù ví dụ hiện tại chạy tuần tự.
- Method `getAdviceInvocations()` cho phép mã chính lấy bằng chứng từ **chính bean aspect** đã đăng ký trong container.

## 5. Bật Spring AOP và gọi bean qua context

Tạo `src/main/java/example/App.java`:

~~~java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class App {
    @Bean
    public NotificationService notificationService() {
        return new NotificationService();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            NotificationService service =
                    context.getBean(NotificationService.class);
            TraceAspect aspect = context.getBean(TraceAspect.class);

            boolean proxy = AopUtils.isAopProxy(service);
            System.out.println("PROXY: " + proxy);

            service.deliver("Spring");

            int count = aspect.getAdviceInvocations();
            System.out.println("ADVICE_COUNT: " + count);

            if (!proxy || count != 1) {
                throw new IllegalStateException(
                        "Spring AOP advice was not executed exactly once");
            }
            System.out.println("CHECK: PASS");
        }
    }
}
~~~

**Tại sao từng phần lại cần thiết?**

1. `@Configuration` khai báo lớp cấu hình Java; `proxyBeanMethods = false` chỉ điều khiển việc proxy hóa các phương thức `@Bean` của configuration class, **không vô hiệu hóa AOP trên service**.
2. `@EnableAspectJAutoProxy(proxyTargetClass = true)` đăng ký cơ chế Spring AOP, cho phép dùng class-based (CGLIB) proxy. Chọn class proxy để có thể lấy bean bằng `NotificationService.class` mà không cần thêm interface. Dùng `proxyTargetClass = true` **không** có nghĩa AspectJ weaving đã bật.
3. `@Bean notificationService()` và `@Bean traceAspect()` đăng ký cả đối tượng đích lẫn aspect. `@Aspect` riêng lẻ là chưa đủ.
4. `AnnotationConfigApplicationContext(App.class)` khởi tạo Spring container. `getBean(NotificationService.class)` trả bean mà Spring cung cấp, có thể là proxy nếu advisor khớp.
5. `AopUtils.isAopProxy(service)` cho biết Spring đã tạo proxy, nhưng chưa đủ một mình để khẳng định advice này đã chạy; vì vậy chương trình còn đo số lần advice.
6. `service.deliver("Spring")` là lời gọi **từ mã bên ngoài đối tượng đích vào Spring bean**. Lời gọi qua proxy được so khớp pointcut trước khi vào method service.
7. Kiểm tra `!proxy || count != 1` khiến chương trình thất bại nếu thiếu một bằng chứng. Chỉ khi đủ điều kiện mới in `CHECK: PASS`.

Tất cả ba lớp đều có `package example;` và nằm đúng đường dẫn tương ứng, nên có thể biên dịch chung bằng Maven.

### Chạy chương trình

Tại thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do mã ví dụ tạo ra, theo đúng thứ tự:

~~~text
PROXY: true
ADVICE: before deliver
SERVICE: Delivered to Spring
ADVICE_COUNT: 1
CHECK: PASS
~~~

Maven hoặc môi trường có thể phát thêm thông báo riêng; năm dòng trên là đầu ra của chương trình cần kiểm tra.

- `PROXY: true` chứng minh service bean đã được proxy.
- `ADVICE: before deliver` **đứng trước** `SERVICE: Delivered to Spring` chứng minh advice `@Before` thực thi trước target method.
- `ADVICE_COUNT: 1` chứng minh instance `TraceAspect` trong Spring ghi nhận đúng một lần advice chạy.
- `CHECK: PASS` cho biết chương trình đã tự kiểm tra cả `proxy` lẫn `count`. Nếu một trong hai sai, `IllegalStateException` xuất hiện thay cho dòng thành công.

Dòng chảy tại runtime:

~~~text
main()
  |
  +--> AnnotationConfigApplicationContext(App.class)
  |       +--> NotificationService bean được Spring proxy
  |       +--> TraceAspect bean có @Before advisor
  |
  +--> service.deliver("Spring")
           |
           v
       Spring proxy -- pointcut khớp execution(...deliver(..))
           |
           v
       TraceAspect.beforeDeliver()    -> count tăng, in ADVICE
           |
           v
       NotificationService.deliver()   -> in SERVICE
           |
           v
       main() -> đọc count, kiểm tra, in CHECK: PASS
~~~

Trong luồng trên, Spring AOP intercept method execution trên bean bằng **proxy**; không biên dịch lại hoặc weave bytecode của `NotificationService`.

## 6. Phép đối chứng để xác nhận nguyên nhân

Thay đổi **một điều tại một thời điểm** và khôi phục trước phép thử tiếp theo:

| Thay đổi | Kết quả dự kiến | Kết luận |
| --- | --- | --- |
| Giữ nguyên mã mẫu | `PROXY: true`, `ADVICE_COUNT: 1`, `CHECK: PASS` | Đã tạo proxy, advice chạy đúng một lần |
| Bỏ `@EnableAspectJAutoProxy` trong ứng dụng Maven này | Không có proxy AOP do ví dụ tự tạo; count = 0, chương trình ném exception | Có bean và `@Aspect` vẫn cần hạ tầng auto-proxy |
| Bỏ `@Bean traceAspect()` | `context.getBean(TraceAspect.class)` thất bại | `@Aspect` không đồng nghĩa bean tự động xuất hiện trong context |
| Đổi pointcut thành `execution(* example.NotificationService.notThere(..))` | Advice không khớp `deliver`; count = 0, không đạt CHECK | Pointcut phải khớp method thực được gọi |
| Tự gọi `new NotificationService().deliver("Direct")` ngoài context | In SERVICE nhưng không in ADVICE, không tăng count Spring aspect | Đối tượng được tạo ngoài container không đi qua proxy |

Các dự đoán về việc bỏ auto-proxy áp dụng cho **project tối thiểu chỉ có cấu hình này**. Ứng dụng Spring Boot có starter AOP hoặc cấu hình khác có thể đã tự bật auto-proxy, nên cần xác minh bối cảnh trước khi kết luận.

Nếu muốn kiểm chứng bằng debugger, đặt breakpoint ở `TraceAspect.beforeDeliver()` và chạy qua `service.deliver("Spring")`. Breakpoint dừng trong advice là bằng chứng trực tiếp, nhưng dấu hiệu stdout kết hợp bộ đếm đã đủ cho mục tiêu bài này.

## 7. Tích hợp quy trình vào project Spring thực tế

Khi chuyển từ ví dụ sang project Spring có sẵn:

1. Chọn method `public` của một service **do Spring quản lý**, được caller khác gọi qua container. Giới hạn biểu thức pointcut vào package/service muốn kiểm tra để tránh log ngoài ý muốn.
2. Đảm bảo có cơ chế bật AOP: `@EnableAspectJAutoProxy` cho Spring Framework dùng cấu hình Java, hoặc Spring Boot AOP auto-configuration trong ứng dụng Boot.
3. Đăng ký aspect như Spring bean (`@Bean` hoặc `@Component` được scan), rồi đặt `@Before` với biểu thức `execution(...)` khớp đúng method.
4. Thêm một dấu hiệu có thể kiểm chứng bên trong advice: log marker duy nhất hoặc bộ đếm dùng trong môi trường thử nghiệm.
5. Gọi từ một bean/caller thực tế, xác nhận proxy, advice và hành vi của method. Trong integration test có thể dùng assertion tương đương `count == 1` thay cho dựa vào đọc log thủ công.

Trong Spring Boot, dependency `spring-boot-starter-aop` thường cung cấp thư viện và auto-configuration cần thiết; bạn vẫn phải đăng ký aspect. Ví dụ dùng Spring Framework thuần ở đây để người học thấy rõ điểm bật AOP, không phụ thuộc cấu hình tự động của Boot.

Đối với ứng dụng có nhiều advisor như transaction, security hoặc caching, hãy phân biệt hai câu hỏi: “bean có được proxy không?” và “**advice cụ thể của mình** có khớp lời gọi này không?”. `AopUtils.isAopProxy` trả lời câu đầu, bộ đếm/log trong `beforeDeliver()` trả lời câu sau.

## 8. Các lỗi phổ biến và cách xử lý

**Không thấy dòng ADVICE:**

- Kiểm tra `TraceAspect` đã đăng ký vào cùng `ApplicationContext` với service chưa. Chỉ có `@Aspect` mà không có `@Bean` hay component scanning là chưa đủ.
- Xem `@EnableAspectJAutoProxy` có nằm trong configuration thực sự được nạp không; với Boot kiểm tra starter và auto-configuration hiện tại.
- Soát biểu thức `execution(...)`: đúng package `example`, class `NotificationService` và method `deliver`. Spring AOP chủ yếu áp dụng advice cho method execution qua proxy.
- Xác nhận bạn gọi `service` lấy bằng `context.getBean(...)`; đối tượng tạo bởi `new` không tự được Spring proxy.
- Nếu method của service gọi `this.otherMethod()` trên chính target, đó là self-invocation và có thể bỏ qua advice trên method nội bộ. Ví dụ này cố ý gọi từ `main` để tránh nhầm lẫn.
- Với class proxy, `final` method, `private` method hoặc class `final` không thể được can thiệp theo cách subclass proxy; phương thức mẫu là `public` và không `final`.

**Có `PROXY: true` nhưng counter không tăng:**

Proxy tồn tại không đảm bảo `@Before` này được kích hoạt. Pointcut có thể không khớp; method bạn gọi có thể khác method mong muốn; bean có thể được proxy vì advisor khác. Kiểm tra đúng aspect bean đang đọc số đếm và đúng luồng lời gọi đi qua proxy.

**Lỗi khi biên dịch hoặc khởi động:**

- `ClassNotFoundException` / `NoClassDefFoundError` liên quan AspectJ: xem Maven đã tải dependency `aspectjweaver`, phiên bản và classpath. Bài này không cần bật `-javaagent` để chữa lỗi.
- `NoSuchBeanDefinitionException`: kiểm tra `@Bean`/`@Configuration` và class cấu hình được nạp vào context.
- `Cannot subclass final class` hoặc lỗi proxy method: kiểm tra các giới hạn CGLIB nói trên. Tránh suy ra rằng chuyển sang weaving là bước bắt buộc cho ví dụ tối thiểu.
- Nếu thay bằng JDK dynamic proxy trên interface, bean thường được lấy qua interface; không mặc định cast proxy về implementation. Project gốc dùng `proxyTargetClass = true` để giảm biến số này.

**Counter lớn hơn 1:** xác minh `deliver` chỉ được gọi một lần, không vô tình chạy nhiều luồng/test. Nếu có nhiều aspect, cần tách marker của từng advice; nhiều dòng log không mặc nhiên có nghĩa một advice chạy nhiều lần. Trong mã mẫu, bộ đếm được tăng tại duy nhất một method advice.

## 9. Checklist hoàn thành

- [ ] Maven biên dịch ba tệp Java trong đúng package `example`.
- [ ] `App` có `@Configuration`, `@EnableAspectJAutoProxy` và hai phương thức `@Bean`.
- [ ] `TraceAspect` có `@Aspect` và `@Before` pointcut khớp `deliver`.
- [ ] Service được lấy qua `ApplicationContext`; không gọi đối tượng tự tạo `new`.
- [ ] Đầu ra có `PROXY: true`.
- [ ] `ADVICE` xuất hiện trước `SERVICE`.
- [ ] `ADVICE_COUNT: 1` và `CHECK: PASS` được in.
- [ ] Hiểu rằng kết quả chứng minh **Spring AOP proxy đã chạy advice**, không chứng minh AspectJ weaving.

## Tài liệu đối chiếu chính thức

- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html).
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html).
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html).
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-setup-009">Kiểm tra cấu hình Java và XML không gây thiết lập chồng chéo</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra cấu hình Java và XML không gây thiết lập chồng chéo trong Spring AOP

## 1. Vì sao phải kiểm tra khi ứng dụng Spring dùng cả Java config và XML?

Một ứng dụng Spring lâu năm có thể đồng thời chứa:

- Cấu hình Java bằng `@Configuration`, `@EnableAspectJAutoProxy`, `@Bean` hoặc `@ComponentScan`.
- Cấu hình XML với `<aop:aspectj-autoproxy/>`, `<aop:config>`, `<aop:advisor>`, `<bean>` và `<context:component-scan/>`.
- Cấu hình tự động của Spring Boot hoặc những tính năng Spring khác cũng sử dụng proxy, chẳng hạn transaction interception.

Việc tồn tại song song **không mặc nhiên sai**: Spring cho phép kết hợp nhiều cách định nghĩa bean, aspect và advisor. Tuy nhiên, khi chuyển dần từ XML sang Java config, rất dễ giữ lại khai báo cũ khiến khó xác định ai chịu trách nhiệm bật proxy, một aspect được đăng ký mấy lần, hoặc một phương thức có hai advice thực hiện cùng chức năng.

Mục tiêu của bước kiểm tra là trả lời **ba câu hỏi riêng biệt**:

1. **Auto-proxy:** cùng một Spring `ApplicationContext` có bao nhiêu cơ chế tạo proxy đang tham gia, và cấu hình proxy thực tế là gì?
2. **Aspect bean:** mỗi aspect dự định sử dụng có được đăng ký đúng một lần theo tên/loại bean hay không?
3. **Advice/advisor:** với một method nghiệp vụ, có bao nhiêu advice thực sự khớp, theo thứ tự nào, và hành vi có bị lặp không?

**Không được suy ra “advice chạy hai lần” chỉ từ việc thấy cả `@EnableAspectJAutoProxy` và `<aop:aspectj-autoproxy/>`.** Spring hợp nhất các khai báo auto-proxy chuẩn vào một hạ tầng thống nhất trong cùng context; sự lặp cần được chứng minh ở mức aspect/advisor và kết quả chạy.

Bài này tập trung vào **cấu hình Spring AOP trên Spring-managed beans**. Các cơ chế AspectJ weaving thuần túy và lý thuyết AOP tổng quát nằm ngoài phạm vi.

## 2. Các khai báo Java/XML có thể gặp và ý nghĩa của chúng

| Khai báo | Nơi xuất hiện | Chức năng trong Spring AOP |
| --- | --- | --- |
| `@EnableAspectJAutoProxy` | Class `@Configuration` | Bật nhận diện các aspect bean kiểu `@Aspect` và tự tạo proxy cho bean phù hợp trong application context liên quan. |
| `<aop:aspectj-autoproxy/>` | XML namespace `aop` | Bật cùng kiểu hỗ trợ `@AspectJ` bằng XML. |
| `<aop:config>` | XML namespace `aop` | Khai báo pointcut, aspect và advisor trực tiếp trong XML; cũng tham gia hệ thống auto-proxy. |
| `@Bean` trả về class `@Aspect` | Java config | Đăng ký **một bean aspect** cho Spring phát hiện. |
| `@Aspect` + `@Component` | Class aspect được component scan | Một cách khác đăng ký bean aspect; `@Aspect` đơn lẻ không phải annotation phát hiện component. |
| `<bean class="...MyAspect"/>` | XML | Đăng ký một instance aspect dưới một bean id, nếu class mang `@Aspect`. |
| `<aop:aspect ref="...">` | XML | Dùng một bean hiện có làm nơi chứa các phương thức advice được khai báo qua schema XML. |
| `@ImportResource("classpath:...xml")` | Java config | Đưa bean definitions từ XML vào context Java; đây là **đường nạp** XML, không tự bật Spring AOP. |
| `<context:component-scan ... />` | XML | Quét component bean; có thể trùng phạm vi với `@ComponentScan` bên Java. |

Phân biệt rõ: **khai báo bật AOP**, **khai báo bean aspect**, và **định nghĩa advice** là ba việc khác nhau. Một cách bật AOP có thể dùng chung cho nhiều aspect; một aspect bean khác tên nhưng cùng class có thể tạo ra thêm một tập advice; hai định nghĩa advice khác nhau có thể cùng khớp một phương thức.

## 3. Spring hợp nhất auto-proxy creator: hiểu đúng trước khi dọn cấu hình

Spring Framework ghi rõ các `<aop:config/>`, `<aop:aspectj-autoproxy/>` và `<tx:annotation-driven/>` trong cùng context được điều phối qua **một auto-proxy creator hợp nhất**, với mức thiết lập proxy mạnh nhất trong những yêu cầu cùng tham gia. Các annotation cấu hình tương ứng như `@EnableAspectJAutoProxy` và `@EnableTransactionManagement` cũng tham gia cơ chế hợp nhất này.

Ví dụ tại **cùng một context**:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AopJavaConfig {
}
```

```xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
```

Với cách kết hợp trên, yêu cầu XML dùng class-based proxy (`true`) có thể **nâng mức lựa chọn chung lên class-based proxy**. Vì vậy chỉ nhìn annotation Java và thấy `false` không đủ kết luận rằng Spring sẽ dùng JDK dynamic proxy. Ngược lại, không nên đếm số dòng bật AOP rồi kết luận từng dòng tạo thêm một lớp proxy độc lập.

Một số lưu ý khi đọc kết quả:

- **“Hợp nhất auto-proxy creator” không có nghĩa mọi aspect hoặc advisor đều bị loại trùng.** Hai aspect bean hoặc hai advice hợp lệ cùng khớp có thể khiến hành vi được thực hiện nhiều lần.
- **`proxyTargetClass = true` không làm Spring chặn được mọi phương thức.** Class/method `final`, phương thức `private`, và self-invocation vẫn có giới hạn của proxy AOP.
- **Phạm vi context quan trọng.** Một parent context và một child context là hai miền bean/proxy khác nhau; cấu hình ở parent không đương nhiên áp dụng như nhau với bean chỉ thuộc child context.
- **Spring Boot có thể bật Spring AOP qua auto-configuration** khi các dependency và điều kiện phù hợp. Khai báo `@EnableAspectJAutoProxy` thêm vào một ứng dụng Boot đang tự cấu hình AOP có thể dư thừa và còn thay đổi thuộc tính proxy dự kiến.

**Mục tiêu bảo trì** là xác định được một nơi sở hữu cấu hình proxy cho mỗi context, dù Spring có cơ chế tự hợp nhất để bảo vệ các khai báo trùng.

## 4. Ví dụ Spring Framework 6: trộn Java và XML đúng cách

Ví dụ dưới đây dùng **Java 17+**, Spring Framework **6.1.14**, AspectJ `aspectjweaver` **1.9.22.1** và Maven. Đây là phiên bản mẫu cố định để minh họa, không phải phát biểu về bản Spring mới nhất.

Ta chọn một quy ước rõ ràng:

- **Java config bật `@EnableAspectJAutoProxy` và đăng ký aspect**.
- **XML chỉ định nghĩa service bean** kế thừa từ hệ thống cũ.
- Chỉ có **một** `TraceAspect` bean; lời gọi đến `OrderService` đi qua Spring proxy.

Cấu trúc thư mục:

```text
spring-aop-java-xml-check/
├── pom.xml
└── src/main/
    ├── java/com/example/demo/
    │   ├── DemoApp.java
    │   ├── AppConfig.java
    │   ├── OrderService.java
    │   └── TraceAspect.java
    └── resources/legacy-beans.xml
```

### 4.1. Khai báo Maven

**`pom.xml`**:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-aop-java-xml-check</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.14</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.1.14</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
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
            </plugin>
        </plugins>
    </build>
</project>
```

`spring-context` quản lý bean và cấu hình; `spring-aop` triển khai cơ chế proxy; `aspectjweaver` cung cấp hỗ trợ cho biểu thức pointcut `@AspectJ`. Trong ứng dụng đã có Spring Boot starter AOP tương ứng, thông thường không cần liệt kê thủ công cả ba artifact.

### 4.2. XML giữ lại bean nghiệp vụ

**`src/main/resources/legacy-beans.xml`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="orderService" class="com.example.demo.OrderService"/>
</beans>
```

XML trên không khai báo `<aop:aspectj-autoproxy/>`, `<aop:config>` hoặc aspect bean. Mục đích của nó chỉ là đăng ký bean cũ có tên `orderService`.

### 4.3. Java config chịu trách nhiệm bật AOP

**`src/main/java/com/example/demo/AppConfig.java`**:

```java
package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ImportResource("classpath:legacy-beans.xml")
public class AppConfig {
    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
```

`@ImportResource` chỉ yêu cầu Spring nạp file XML để có `orderService`. Bean `traceAspect` được khai báo **đúng một lần bằng Java**. Annotation `@EnableAspectJAutoProxy(proxyTargetClass = true)` kích hoạt proxy theo class nên ví dụ lấy được bean dưới kiểu `OrderService` dù class không triển khai interface.

### 4.4. Service và aspect

**`src/main/java/com/example/demo/OrderService.java`**:

```java
package com.example.demo;

public class OrderService {
    public void place(String id) {
        System.out.println("[SERVICE] order " + id);
    }
}
```

**`src/main/java/com/example/demo/TraceAspect.java`**:

```java
package com.example.demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Before("execution(public * com.example.demo.OrderService.place(..))")
    public void beforePlace() {
        calls.incrementAndGet();
        System.out.println("[ASPECT] before place");
    }

    public int count() {
        return calls.get();
    }
}
```

`@Aspect` đánh dấu class là aspect, còn `@Bean` ở `AppConfig` đưa đối tượng vào Spring container. Không cần gắn thêm `@Component` cho `TraceAspect` trong ví dụ này.

### 4.5. Kiểm tra proxy, số aspect bean và số lần advice

**`src/main/java/com/example/demo/DemoApp.java`**:

```java
package com.example.demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService service = context.getBean(OrderService.class);
            TraceAspect aspect = context.getBean(TraceAspect.class);

            System.out.println("[CHECK] isProxy=" + AopUtils.isAopProxy(service));
            System.out.println("[CHECK] aspectBeans="
                    + context.getBeansOfType(TraceAspect.class).size());

            service.place("ORD-1");

            System.out.println("[CHECK] adviceCalls=" + aspect.count());
        }
    }
}
```

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.demo.DemoApp
```

Các dòng chương trình tự in ra theo đúng thứ tự:

```text
[CHECK] isProxy=true
[CHECK] aspectBeans=1
[ASPECT] before place
[SERVICE] order ORD-1
[CHECK] adviceCalls=1
```

**Diễn giải từng phép kiểm tra:**

- `isProxy=true`: `OrderService` mà caller lấy ra thực sự là Spring AOP proxy, không phải object tự `new`.
- `aspectBeans=1`: có đúng một bean kiểu `TraceAspect` trong context; đếm bean quan trọng hơn đếm số file cấu hình.
- `adviceCalls=1`: đúng một lần gọi advice sau một lần gọi phương thức qua proxy. Đây là kiểm tra **quan sát được** giúp phát hiện điều kiện chồng chéo.
- Dòng nghiệp vụ vẫn chạy sau advice: Spring AOP đã áp dụng hành vi chung mà không cần sửa `OrderService`.

`AopUtils.isAopProxy` là công cụ quan sát proxy; trong hệ thống thực tế cần bổ sung kiểm thử số lần advice và kết quả nghiệp vụ, vì chỉ có proxy chưa chứng minh cấu hình không trùng logic.

## 5. Hai tình huống chồng chéo: một trường hợp thừa, một trường hợp thực sự lặp hành vi

### 5.1. Bật auto-proxy lần nữa trong XML: dư thừa, chưa chứng minh advice lặp

Giả sử `AppConfig` ở trên đã chứa `@EnableAspectJAutoProxy(proxyTargetClass = true)`, nhưng file XML cũ còn có:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
         http://www.springframework.org/schema/beans
         https://www.springframework.org/schema/beans/spring-beans.xsd
         http://www.springframework.org/schema/aop
         https://www.springframework.org/schema/aop/spring-aop.xsd">

    <aop:aspectj-autoproxy proxy-target-class="true"/>

    <bean id="orderService" class="com.example.demo.OrderService"/>
</beans>
```

Việc thêm `<aop:aspectj-autoproxy/>` như trên là **khai báo bật AOP thứ hai**. Trong cùng context, Spring hợp nhất hỗ trợ auto-proxy thông thường; riêng tình huống này **không tự tạo ra hai aspect bean** và không phải căn cứ đủ để nói lời gọi sẽ chạy `beforePlace()` hai lần.

Tuy nhiên, cách cấu hình này gây khó bảo trì và có thể che khuất việc chọn `proxy-target-class`. Khi Java config đã là nơi bật AOP chính thức, **xóa dòng XML dư thừa** sau khi kiểm tra mọi context phụ thuộc, giữ XML chỉ cho bean definitions. Nếu thực tế XML mới là nơi được chọn để sở hữu AOP, làm ngược lại: chỉ giữ XML activation và loại annotation Java dư thừa trong context đó.

Không chỉnh đồng thời hai nơi theo một quy tắc “mỗi nơi một chút” vì người đọc rất khó xác định chính sách proxy cuối cùng.

### 5.2. Đăng ký cùng class aspect thành hai bean: advice có thể chạy hai lần

Giữ nguyên `@Bean traceAspect()` bên Java, rồi **thêm** vào `legacy-beans.xml`:

```xml
<bean id="traceAspectXml" class="com.example.demo.TraceAspect"/>
```

Khi đó tồn tại hai bean riêng biệt:

```text
Java:  @Bean traceAspect()               --> traceAspect
XML:   <bean id="traceAspectXml" .../>    --> traceAspectXml
```

Cả hai đều là bean thuộc class có `@Aspect` và cùng chứa `@Before` khớp `OrderService.place()`. Dù Spring chỉ dùng một cơ chế auto-proxy thống nhất, nó có thể tạo **hai advisor tương ứng với hai aspect instances**. Mỗi lần gọi `place()` qua proxy sẽ có hai dòng `[ASPECT] before place`; vì là hai instance độc lập, `aspect.count()` của một instance riêng lẻ **không phải tổng số lần advice trên cả hai instance**.

Thay phép kiểm `aspect.count()` bằng một bộ đếm được chia sẻ hoặc thu thập event để chứng minh số lần thực thi khi đánh giá nhiều instances. Nếu chạy nguyên `DemoApp` mà không đổi, phần `context.getBean(TraceAspect.class)` còn có thể ném `NoUniqueBeanDefinitionException`, do có hai bean cùng kiểu; đây là một dấu hiệu khác cho thấy cấu hình đã lặp đăng ký.

**Cách sửa:** xác định nơi sở hữu bean aspect, xóa đăng ký còn lại. Nếu class đã có `@Component` và được cả Java `@ComponentScan` lẫn XML `<context:component-scan/>` quét, hãy kiểm tra bean name thực tế. Cùng một bean name có thể bị xử lý như một định nghĩa trùng hoặc gây lỗi override; **khác bean name** có thể tạo hai instances. Không nên khẳng định mọi lần quét lặp tự sinh ra hai bean: kết quả phụ thuộc naming, metadata và chính sách ghi đè bean definitions.

### 5.3. Hai định nghĩa advice cùng mục đích

Một biến thể khác là:

- Java aspect có `@Before` ghi một audit event;
- XML `<aop:config>` lại có `<aop:advisor>` hoặc `<aop:aspect>` chọn đúng method và ghi cùng audit event.

Dù chỉ có một bean aspect Java, hệ thống vẫn có **hai advice chức năng tương tự**. Spring hỗ trợ phối hợp cả annotation-based aspect lẫn schema-defined advisor; việc chúng cùng tồn tại hợp lệ về mặt Spring, nhưng phải kiểm tra xem đó là hai chính sách khác nhau được chủ đích hay là mã chuyển đổi chưa dọn xong.

**Không sửa bằng cách tắt toàn bộ AOP.** Hãy tìm đúng khai báo advice bị trùng chức năng và thống nhất một nguồn sở hữu cho chính sách đó.

## 6. Danh sách điểm cần tìm khi kiểm kê Spring AOP configuration

Khi codebase dùng cả Java và XML, lần lượt tìm các nhóm ký hiệu sau trong **các file cấu hình thực sự được nạp bởi application context**.

| Nhóm | Trong Java | Trong XML | Điều cần xác nhận |
| --- | --- | --- | --- |
| Bật proxy | `@EnableAspectJAutoProxy` | `<aop:aspectj-autoproxy>`, `<aop:config>` | Ai sở hữu chính sách proxy trong context? Có thiết lập `proxyTargetClass` xung đột? |
| Nạp cấu hình | `@Import`, `@ImportResource`, `@ComponentScan` | `<import>`, `<context:component-scan>` | Một file hoặc bean có bị nạp/đăng ký theo nhiều con đường? |
| Đăng ký aspect | `@Bean`, `@Component` + `@Aspect` | `<bean class="...">` | Có bao nhiêu bean name/instance cho mỗi aspect? |
| Gắn advice | `@Before`, `@After`, `@Around`... | `<aop:aspect>`, `<aop:advisor>` | Có nhiều advice khớp cùng method và cùng chức năng? |
| Tính năng dùng chung proxy | `@EnableTransactionManagement` | `<tx:annotation-driven>` | Thiết lập class-based proxy có ảnh hưởng đến các interceptor khác? |
| Auto-configuration | Spring Boot starter, properties và cấu hình Java | XML legacy được import | Boot đã tự kích hoạt AOP trước khi thêm annotation/XML thủ công chưa? |

**Cách tìm trong repository** (ví dụ khi có `rg`):

```bash
rg -n 'EnableAspectJAutoProxy|ImportResource|ComponentScan|@Aspect|@Bean' src/main/java
rg -n 'aspectj-autoproxy|aop:config|aop:advisor|aop:aspect|context:component-scan|<import' src/main/resources
```

Tìm được một đoạn text chưa đủ kết luận nó có hiệu lực: profile có thể chưa bật, file XML có thể không được import, package có thể nằm ngoài component scan, hoặc cấu hình thuộc một context khác. Bước kiểm kê phải truy ngược **điểm khởi tạo context**, danh sách config được nạp, profile và điều kiện kích hoạt.

Với Spring Boot, đối chiếu `spring.aop.auto`, `spring.aop.proxy-target-class` và auto-configuration phù hợp với phiên bản Boot đang chạy. Khi muốn ngừng AOP, không nên chỉ đặt một property rồi bỏ qua các cấu hình `@EnableAspectJAutoProxy` do code tự bật.

## 7. Kiểm tra trực tiếp Spring container khi nghi có trùng

### 7.1. Đếm bean aspect theo type và name

Trong môi trường kiểm thử, ví dụ:

```java
String[] names = context.getBeanNamesForType(TraceAspect.class);
System.out.println(java.util.Arrays.toString(names));
```

Kết quả dự kiến với bài mẫu:

```text
[traceAspect]
```

Nếu có `traceAspectXml` cùng tồn tại, có thể thấy hai tên. **Không dựa vào thứ tự in tên khi kiểm thử nhiều bean**; hãy so theo tập tên hoặc số lượng.

Lưu ý: `getBeansOfType` có thể khởi tạo các singleton tương ứng, còn việc lấy định nghĩa bean name chỉ phản ánh context được hỏi. Khi hệ thống có context cha/con, phải kiểm kê đúng cả hai.

### 7.2. Quan sát proxy và các advisor đang áp dụng

Nếu bean là Spring AOP proxy hỗ trợ giao diện `Advised`, có thể liệt kê các advisor:

```java
Object bean = context.getBean("orderService");
System.out.println(org.springframework.aop.support.AopUtils.isAopProxy(bean));

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName()
                + " -> " + advisor.getAdvice().getClass().getName());
    }
}
```

Đây là **đoạn mã chẩn đoán** dùng trong test hoặc môi trường kiểm tra, không phải bản mở rộng của `DemoApp` cần stdout chính xác. Advisor nội bộ có thể bao gồm nhiều loại hỗ trợ, và thứ tự/class tên phụ thuộc version. Đếm số advisor **không tương đương** với đếm số advice chạy trên một method: phải xét pointcut và lần gọi cụ thể.

### 7.3. Kiểm thử một lần gọi với số lần advice kỳ vọng

Một kiểm thử hữu ích nên có:

1. Một bean đích chắc chắn khớp pointcut.
2. Một bean/phương thức không khớp để kiểm tra advice không chạy ngoài phạm vi.
3. Bộ đếm hoặc event collector cho **tổng** lần chạy của chính sách audit/tracing.
4. Kiểm tra giá trị trả về/ngoại lệ của nghiệp vụ và thứ tự khi có nhiều advice khác loại.
5. Lặp lại với các Spring profiles/configuration combinations được triển khai thực tế.

Kỳ vọng ví dụ đơn giản: **một lần `service.place("ORD-1")` → một audit event → một lần chạy nghiệp vụ**. Trường hợp khác như hai chính sách khác nhau cùng advice một method có thể hợp lệ; kiểm thử phải nêu rõ số lần dự kiến cho **từng trách nhiệm**, thay vì mặc định mọi method chỉ được một advice.

## 8. Phân biệt các triệu chứng dễ nhầm

| Triệu chứng | Giải thích có thể xảy ra | Cách xác minh |
| --- | --- | --- |
| Có `@EnableAspectJAutoProxy` và `<aop:aspectj-autoproxy/>` | Khai báo bật proxy dư; Spring thường hợp nhất thành một creator | Kiểm tra context và config thực tế; đừng kết luận log lặp nếu chưa đo. |
| Advice chạy hai lần | Hai aspect beans, hai advisors, hoặc hai interception layers thực sự | Đếm bean, liệt kê advisor, ghi số lần advice bằng counter chung. |
| Inject aspect bằng type báo không duy nhất | Cùng class aspect được đăng ký dưới nhiều bean names | `getBeanNamesForType(MyAspect.class)`, kiểm tra `@Bean`, `@Component` và XML `<bean>`. |
| Không có advice dù có XML | XML chưa được import, aspect không là bean, pointcut không khớp, hoặc caller đi vòng qua proxy | Kiểm tra đường nạp XML, bean registry, `AopUtils.isAopProxy` và đường gọi. |
| Proxy là CGLIB dù annotation Java đặt `proxyTargetClass=false` | XML hoặc cấu hình khác có thể yêu cầu `proxy-target-class=true` | Kiểm kê mọi proxy setting trong cùng context, kể cả transaction AOP. |
| Tưởng AOP chạy hai lần vì có nhiều dòng log giống nhau | Một request có thể gọi method hai lần hoặc log ở nhiều tầng | Gắn request/correlation id và đo counter tại advice thay vì so mỗi chuỗi log. |
| Boot báo lỗi trùng tên bean | Hai định nghĩa sử dụng cùng name; chính sách override của Boot có thể từ chối | Đọc exception gốc và vị trí đăng ký; đặt một nguồn sở hữu duy nhất. |
| Advice ở web context chạy nhưng bean khác không chạy | Khác application context hoặc lời gọi không qua bean proxy | Kiểm tra parent/child context, vị trí đăng ký aspect và proxy bean đích. |

Đặc biệt, khi thấy hai tầng proxy hay nhiều interceptors khác nhau, không nên tự động xóa một tầng nếu chưa hiểu transaction, security hoặc async đang thực thi nhiệm vụ gì. Mục tiêu là loại bỏ cấu hình **trùng trách nhiệm**.

## 9. Chiến lược dọn Java/XML từng bước mà vẫn giữ hành vi

Một cách chuyển đổi cấu hình an toàn:

1. **Xác định chính xác các context đang chạy.** Phân biệt root, servlet context hoặc module context nếu ứng dụng có nhiều tầng.
2. **Chọn chủ sở hữu cấu hình Spring AOP cho từng context.** Ví dụ Java config bật `@EnableAspectJAutoProxy`, XML chỉ giữ beans chưa chuyển đổi.
3. **Liệt kê aspect beans theo tên/type**, loại những đăng ký trùng chức năng. `@ComponentScan`, `@Bean` và XML `<bean>` cần được kiểm tra như ba nguồn khác nhau.
4. **Đối chiếu pointcut/advice ở Java và XML.** Với hai advice cùng mục đích, chọn một định nghĩa phù hợp với hướng duy trì của dự án; không thay đổi chính sách nghiệp vụ ngoài phạm vi.
5. **So sánh lựa chọn proxy trước/sau.** Kiểm tra `proxyTargetClass`, interface injection, `final` methods và việc sử dụng `@Transactional` nếu có, vì thay đổi chế độ proxy có thể ảnh hưởng đến khả năng intercept.
6. **Chạy kiểm thử có quan sát.** Đảm bảo mỗi hành vi dùng chung chạy đúng số lần, nghiệp vụ cho kết quả cũ và các trường hợp ngoại lệ vẫn đúng.
7. **Chỉ khi đã xác nhận tương đương, xóa khai báo thừa** khỏi vị trí không còn sở hữu cấu hình. Giữ test để việc bổ sung XML/Java trong tương lai không tái tạo trùng lặp.

Đối với một project Spring Boot đang dùng starter Spring AOP, thường nên dựa vào auto-configuration được quản lý theo dòng Boot, tránh bật lại thủ công trừ khi ứng dụng có lý do riêng và đã kiểm thử tác động. Với Spring Framework thuần, chọn một trong hai cơ chế bật auto-proxy Java/XML cho cùng context và ghi rõ nơi được duy trì.

## 10. Tự kiểm tra cuối bài

- Nếu Java và XML đều bật Spring AOP, **liệu advice chắc chắn chạy hai lần?** Không. Phải kiểm tra beans và advisors; Spring có cơ chế hợp nhất auto-proxy creator.
- Nếu chỉ có một auto-proxy creator, **liệu aspect không thể bị trùng?** Không. Hai aspect beans hoặc hai advisors cùng pointcut có thể cùng chạy.
- Nếu có `@Aspect`, **đã đủ cho Spring nhận diện?** Không. Class phải được đăng ký thành Spring bean và có hỗ trợ auto-proxy phù hợp.
- Nếu `proxy-target-class=true` trong XML và `false` trong Java, **có thể chỉ đọc Java rồi tin là JDK proxy?** Không. Các lựa chọn proxy có thể được hợp nhất theo thiết lập mạnh hơn.
- Nếu tests thấy `isAopProxy=true`, **đã chứng minh advice chạy một lần?** Chưa. Cần đếm sự kiện advice khớp trên một lần gọi cụ thể.

### Tài liệu tham khảo Spring Framework

- Spring AOP: Proxying mechanisms — https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Enabling @AspectJ support — https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Declaring an aspect — https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html
- Schema-based AOP support — https://docs.spring.io/spring-framework/reference/core/aop/schema.html
- Mixing aspect types — https://docs.spring.io/spring-framework/reference/core/aop/mixing-styles.html

Trong Spring AOP, cấu hình Java/XML được xem là nhất quán khi **đường nạp context xác định, nơi bật proxy rõ ràng, mỗi aspect chỉ được đăng ký đúng số instance theo thiết kế và mỗi chính sách advice chạy đúng số lần trên bean đích**. Kiểm tra cùng lúc cả cấu hình và hành vi chạy sẽ phân biệt được một khai báo dư vô hại với một lỗi nhân đôi thực sự.


</details>

- [Quay lại đầu trang](#back-to-top)
