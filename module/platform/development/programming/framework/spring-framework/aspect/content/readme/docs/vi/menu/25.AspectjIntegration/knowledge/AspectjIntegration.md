<a id="back-to-top"></a>

# AspectJ trong Spring

## Menu
- [Khi yêu cầu vượt giới hạn method proxy của Spring AOP](#aspectj-integration-001)
- [Phân biệt bật @AspectJ auto-proxy và bật AspectJ weaving](#aspectj-integration-002)
- [Vai trò spring-aspects trong các tích hợp AspectJ do Spring cung cấp](#aspectj-integration-003)
- [Đưa phụ thuộc Spring vào aspect do AspectJ quản lý](#aspectj-integration-004)
- [@Configurable và AnnotationBeanConfigurerAspect trong phạm vi tích hợp Spring](#aspectj-integration-005)
- [LoadTimeWeaver và @EnableLoadTimeWeaving](#aspectj-integration-006)
- [META-INF/aop.xml và giới hạn lớp được weave trong ứng dụng Spring](#aspectj-integration-007)
- [spring-instrument, aspectjweaver và yêu cầu môi trường thực thi](#aspectj-integration-008)
- [ClassLoader và thời điểm lớp được nạp khi dùng LTW](#aspectj-integration-009)
- [Tránh cùng một hành vi bị áp dụng bởi cả proxy và weaving](#aspectj-integration-010)
- [Kiểm chứng cấu hình triển khai và phân biệt lỗi LTW với lỗi auto-proxy](#aspectj-integration-011)

## <a id="aspectj-integration-001">Khi yêu cầu vượt giới hạn method proxy của Spring AOP</a>

<details>
<summary>Xem chi tiết</summary>

# Khi yêu cầu vượt giới hạn method proxy của Spring AOP

## 1. Nhận diện đúng giới hạn trong ứng dụng Spring

Spring AOP mặc định dùng **proxy quanh Spring bean**. Advice chỉ được kích hoạt khi lời gọi đi qua proxy và pointcut khớp với *method execution* trên bean đó. Vì vậy, đôi khi cấu hình `@Aspect` hoặc biểu thức `execution(...)` hoàn toàn đúng nhưng logic vẫn không chạy.

Các tình huống thường gặp:

| Nhu cầu trong ứng dụng Spring | Giới hạn của Spring AOP dựa trên proxy | Hướng xử lý |
| --- | --- | --- |
| Method trong cùng đối tượng gọi method khác qua `this` (*self-invocation*) | Cuộc gọi nội bộ không đi qua proxy; advice của method được gọi không chạy | Ưu tiên tách sang bean khác; cân nhắc AspectJ weaving khi cần giữ mô hình gọi nội bộ |
| Advice phải áp dụng cho đối tượng tạo bằng `new`, không do container quản lý | Đối tượng đó không tự nhận proxy Spring AOP | Cân nhắc đổi cách tạo đối tượng hoặc weaving; chỉ dùng `@Configurable` khi thật sự cần Spring inject vào đối tượng ngoài container |
| Intercept constructor, đọc/ghi field, method call hoặc nhiều loại join point hơn | Spring AOP chỉ hỗ trợ method execution trên Spring bean | Dùng AspectJ weaving với pointcut phù hợp |
| Advice vào private/final method hoặc lớp final | Proxy không thể bảo đảm intercept các phương thức đó; class-based proxy không override được final method | Ưu tiên thiết kế lại ranh giới; weaving có thể can thiệp bytecode ở những vị trí proxy không can thiệp được |
| Gọi method ở giai đoạn khởi tạo trước khi proxy hoạt động | Proxy chưa sẵn sàng, ví dụ lời gọi trong `@PostConstruct` | Chuyển việc khởi tạo sang thời điểm sau khi bean sẵn sàng; nếu cần interception ở bytecode, đánh giá weaving |

**Ví dụ điển hình:** `OrderService.placeOrder()` tự gọi `this.reserveStock()` và `reserveStock()` mang `@Transactional(propagation = REQUIRES_NEW)`. Trong chế độ transaction `PROXY` mặc định, lần gọi nội bộ này **không kích hoạt một interceptor giao dịch mới** cho `reserveStock()`. Chuyển sang CGLIB bằng `proxyTargetClass=true` không giải quyết self-invocation vì cuộc gọi vẫn nằm trong target.

Lưu ý: một method public được gọi từ bean khác qua proxy thường là trường hợp phù hợp nhất cho Spring AOP. Chỉ chọn AspectJ khi có yêu cầu cụ thể mà proxy không thể đáp ứng hợp lý.

## 2. Chọn ranh giới trước khi chuyển sang AspectJ

| Yêu cầu trong ứng dụng | Giải pháp nên thử trước | Khi nào cân nhắc weaving |
| --- | --- | --- |
| Method của Spring bean gọi từ bean khác | Spring AOP proxy + DI | Khi cần join point không phải method execution |
| `this.inner()` bypass advice | Tách collaborator bean hoặc đặt policy ở outer entry | Bắt buộc giữ self-call nhưng vẫn cần advice riêng |
| Object tạo qua `new` muốn dùng Spring dependency | Factory/constructor injection minh bạch | `@Configurable` + `spring-aspects` nếu không thể thay đổi nơi tạo |
| Constructor, field, private/final method | Xem lại policy và thiết kế | CTW/LTW với type và pointcut đủ hẹp |

`@Aspect` mô tả advice; **Spring `@EnableAspectJAutoProxy`** áp dụng qua bean proxy, còn **AspectJ weaving** đưa advice vào bytecode. Đổi JDK proxy sang CGLIB, bật `exposeProxy` hoặc chỉ thêm `aspectjweaver.jar` không tự bật weaving. Self-injection/`AopContext.currentProxy()` có thể chuyển hướng lời gọi một cách có điều kiện, nhưng tăng coupling và rủi ro lifecycle.

Hãy chứng minh bằng test rằng Spring proxy thực sự không đáp ứng ranh giới mong muốn trước khi đổi build/runtime. Bài kế tiếp so sánh chi tiết auto-proxy với weaving.

## 3. Hai hướng dùng weaving trong Spring

**Compile-time/post-compile weaving (CTW):** dùng AspectJ compiler `ajc` xử lý source/class trong build; artifact đã chứa advice woven. Phù hợp khi quản lý được build pipeline và muốn kiểm tra weaving trước khi deploy.

**Load-time weaving (LTW):** AspectJ biến đổi bytecode trước lúc ClassLoader định nghĩa class. Có thể dùng AspectJ agent `-javaagent:aspectjweaver.jar` ở JVM startup, hoặc Spring `@EnableLoadTimeWeaving` với `LoadTimeWeaver`, AspectJ transformer và instrumentation/ClassLoader phù hợp. Trong JVM độc lập, tuyến Spring có thể cần agent `spring-instrument.jar`. `META-INF/aop.xml` khai báo aspects và type filters; class đã nạp trước transformer không tự được weave hồi tố.

Bài demo dưới chọn **AspectJ agent trực tiếp** nên không cần Spring-managed LTW. Các bài về `LoadTimeWeaver`, `aop.xml`, agent và ClassLoader sau đây giải thích điều kiện cần cho từng tuyến. Dù dùng CTW hay LTW, cần runtime dependencies, đúng pointcut và kiểm chứng số lần advice chạy, tránh vô tình chồng với Spring auto-proxy.

## 4. Ví dụ Spring Boot: intercept self-invocation bằng AspectJ LTW

Mục tiêu: `WorkflowService.outer()` gọi `inner()` trong **chính đối tượng**. Advice vẫn chạy trước `inner()` nhờ bytecode weaving, dù không có cuộc gọi qua proxy.

Ví dụ dùng Spring Boot 3.3, Java 21 và Gradle Groovy DSL. Tạo các tệp đúng như cấu trúc sau:

~~~text
aop-demo/
├── settings.gradle
├── build.gradle
└── src/main/
    ├── java/com/example/demo/
    │   ├── DemoApplication.java
    │   ├── WorkflowService.java
    │   └── TraceAspect.java
    └── resources/META-INF/aop.xml
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'aop-demo'
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
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.aspectj:aspectjweaver'
}

// Đặt bản aspectjweaver đã resolve vào đường dẫn cố định cho JVM agent.
tasks.register('stageWeaverAgent', Copy) {
    from configurations.runtimeClasspath
    include 'aspectjweaver-*.jar'
    into layout.buildDirectory.dir('agent')
    rename { 'aspectjweaver.jar' }
}
~~~

`src/main/java/com/example/demo/DemoApplication.java`:

~~~java
package com.example.demo;

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
    CommandLineRunner demo(WorkflowService service) {
        return args -> service.outer();
    }
}
~~~

`src/main/java/com/example/demo/WorkflowService.java`:

~~~java
package com.example.demo;

import org.springframework.stereotype.Service;

@Service
public class WorkflowService {
    public void outer() {
        System.out.println("outer");
        inner(); // Tương đương this.inner(): gọi nội bộ, không qua Spring proxy.
    }

    public void inner() {
        System.out.println("inner");
    }
}
~~~

`src/main/java/com/example/demo/TraceAspect.java`:

~~~java
package com.example.demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(* com.example.demo.WorkflowService.inner(..))")
    public void beforeInner() {
        System.out.println("advice: inner");
    }
}
~~~

`src/main/resources/META-INF/aop.xml`:

~~~xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN"
    "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <aspects>
        <aspect name="com.example.demo.TraceAspect"/>
    </aspects>
    <weaver options="-showWeaveInfo">
        <include within="com.example.demo..*"/>
    </weaver>
</aspectj>
~~~

Chạy từ thư mục dự án có Gradle wrapper (hoặc dùng lệnh `gradle` đã cài với phiên bản tương thích):

~~~bash
./gradlew clean bootJar stageWeaverAgent
java -javaagent:build/agent/aspectjweaver.jar -jar build/libs/aop-demo-0.0.1-SNAPSHOT.jar
~~~

Các dòng **theo thứ tự** liên quan đến ví dụ, ngoài log khởi động của Spring và thông tin weaving từ agent:

~~~text
outer
advice: inner
inner
~~~

Giải thích: `outer()` in trước, sau đó gọi trực tiếp `inner()`. Vì bytecode của `inner()` đã được weave, advice `@Before` in `advice: inner` trước khi thân `inner()` in `inner`. `TraceAspect` **không gắn `@Component`**; ví dụ giao việc tạo aspect cho AspectJ runtime qua `aop.xml`. Không thêm bean aspect này vào Spring auto-proxy để tránh chạy advice hai lần.

Đối chứng: chạy cùng JAR **không có** `-javaagent` và không khai báo aspect thành Spring bean. Khi đó các dòng ứng dụng chỉ còn `outer`, `inner`. Đây là bằng chứng advice từ weaving đang hoạt động trong lần chạy có agent; nếu chạy qua một cấu hình Spring AOP riêng, kết quả có thể khác.

## 5. Trường hợp thực tế trong Spring: `@Transactional` và self-invocation

Spring quản lý giao dịch bằng proxy theo mặc định. Với cấu hình như sau, lời gọi `outer() -> inner()` trong cùng class sẽ không kích hoạt transaction advice riêng cho `inner()`:

~~~java
@Service
public class PaymentService {
    public void outer() {
        inner();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inner() {
        // Công việc cần một transaction riêng.
    }
}
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập: dự án thực tế cần import `Service`, `Transactional`, `Propagation`, transaction manager và hạ tầng truy cập dữ liệu.

Nếu refactor được, đưa `inner()` sang một bean khác và gọi qua bean đã inject. Khi **phải** giữ self-invocation, Spring hỗ trợ chế độ AspectJ cho transaction:

~~~java
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
public class TransactionConfig {
}
~~~

Điều kiện để cấu hình này có tác dụng:

- Có `spring-aspects` chứa `org.springframework.transaction.aspectj.AnnotationTransactionAspect`, các dependency giao dịch cần thiết và một `TransactionManager` được cấu hình.
- CTW hoặc LTW thực sự weave được class chứa `@Transactional`; bật `mode = ASPECTJ` **một mình không đủ**.
- Đặt `@Transactional` trên class triển khai hoặc method triển khai. Đừng chỉ đặt trên interface vì AspectJ không kế thừa annotation ở interface theo cơ chế này.
- Kiểm thử việc mở transaction mới và rollback thực sự, không chỉ suy luận từ log. Cũng cần tránh gọi lồng nhau mà không có lý do nghiệp vụ.

Đây là cấu hình **riêng của transaction**, không tự biến mọi aspect `@Aspect` trong ứng dụng thành woven aspect.

## 6. Spring có thể hỗ trợ những gì khi AspectJ quản lý aspect?

- **IoC và woven aspect:** một aspect singleton do AspectJ tạo có thể được cấu hình bằng Spring qua cơ chế `aspectOf()`/bean factory hoặc cơ chế tích hợp thích hợp; cần đảm bảo chỉ một nơi quản lý advice và vòng đời. Một bean `@Aspect` do Spring quản lý thường phục vụ Spring AOP proxy, không tự động trở thành woven aspect.
- **Đối tượng ngoài container:** `spring-aspects` cung cấp `@Configurable` với `AnnotationBeanConfigurerAspect` và `@EnableSpringConfigured` để Spring tiêm dependency vào đối tượng được tạo ngoài IoC. Điều này đòi hỏi weaving và chỉ nên dùng khi không thể thay đổi cách tạo/lifecycle của đối tượng.
- **Spring LoadTimeWeaver:** cho phép bật AspectJ LTW theo `ApplicationContext`/`ClassLoader` trong môi trường phù hợp, thay vì chỉ có cấu hình Java agent phạm vi JVM.
- **CTW/LTW và Spring AOP đồng tồn tại:** có thể áp dụng cả hai cho những nhóm class khác nhau, nhưng phải thiết kế pointcut và đăng ký aspect rõ ràng. Nếu cùng một advice được áp dụng qua proxy **và** weaving, có thể phát sinh chạy trùng.

Những tích hợp này là lý do chọn AspectJ *trong Spring* khi ranh giới Spring proxy thực sự không đủ; không cần chuyển toàn bộ ứng dụng sang weaving.

## 7. Kiểm tra và xử lý sự cố trước khi đưa vào production

| Triệu chứng | Kiểm tra ưu tiên |
| --- | --- |
| `@Aspect` có nhưng self-invocation không bị intercept | Đang chỉ bật `@EnableAspectJAutoProxy` hoặc Boot AOP starter? Đó là proxy, cần cơ chế weaving thật |
| Agent chạy nhưng không có advice | Kiểm tra đường dẫn Java agent, `META-INF/aop.xml` nằm trên runtime classpath, tên aspect, package `<include>` và điểm nạp class |
| Advice chạy hai lần | Kiểm tra aspect vừa được đăng ký ở `aop.xml` vừa là Spring bean được auto-proxy; kiểm tra các pointcut giao nhau |
| `@Transactional(mode = ASPECTJ)` không hoạt động | Kiểm tra `spring-aspects`, transaction manager, class đã được weave và annotation có nằm trên implementation không |
| Chỉ chạy khi start trên máy cá nhân | So sánh tham số JVM, Java/AspectJ version, đóng gói executable JAR, classloader của application server và phạm vi weaving |
| Bị chậm hoặc xuất hiện cảnh báo/dump | Thu hẹp `<include within="..."/>` đến package sở hữu, bỏ matching quá rộng, kiểm tra log `-showWeaveInfo` |

Khi điều tra, nên có bài kiểm thử tích hợp chứng minh **cùng một method** bị intercept khi gọi từ bean khác và khi gọi qua `this`; đồng thời kiểm thử các đường thất bại như rollback, exception và concurrency nếu advice tác động đến chúng. Trong CI, chạy kiểm thử với **đúng CTW/LTW** như môi trường triển khai, tránh tình trạng test proxy một kiểu nhưng production weaving kiểu khác.

Cần đánh giá thêm AOT/native-image, runtime bytecode transformer, classloader đặc biệt, Java module boundaries và thứ tự instrumentation agent trước khi cam kết dùng LTW: sự hỗ trợ phụ thuộc stack và môi trường chạy cụ thể.

## 8. Checklist ra quyết định

1. Xác định join point cần bắt và ghi một test chứng minh Spring AOP proxy bỏ sót.
2. Thử sửa thiết kế Spring trước: đặt boundary tại bean public method, tách service hoặc thay đổi thời điểm gọi.
3. Nếu cần nhiều loại join point hoặc self-invocation là yêu cầu bắt buộc, chọn CTW hay LTW dựa trên quyền kiểm soát build/JVM.
4. Khoanh vùng package weaving trong `aop.xml`, bảo đảm aspect chỉ được áp dụng một lần.
5. Chạy test tích hợp với cấu hình weaving thật, quan sát advice, rollback (nếu dùng transaction) và xác minh đóng gói production.

**Ghi nhớ:** `@Aspect` chỉ là cách khai báo aspect; **Spring AOP proxy** và **AspectJ bytecode weaving** là hai cơ chế thực thi khác nhau. Khi yêu cầu vượt qua ranh giới method call qua Spring bean proxy, hãy xác định chính xác điểm cần intercept rồi dùng AspectJ weaving có chủ đích.

## Tài liệu chính thức

- Spring Framework — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- Spring Framework — [Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html).
- Spring Framework — [Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html).
- Spring Framework — [Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-integration-002">Phân biệt bật @AspectJ auto-proxy và bật AspectJ weaving</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt bật @AspectJ auto-proxy và bật AspectJ weaving trong Spring

## 1. Hiểu đúng hai cách tích hợp với Spring

Trong Spring Framework, từ **@AspectJ** thường chỉ **cú pháp khai báo aspect bằng annotation** như **@Aspect**, **@Before**, **@Around** và biểu thức pointcut. Nhưng cùng cú pháp này có thể được hai bộ máy hoàn toàn khác nhau xử lý:

- **@AspectJ auto-proxy**: Spring đọc aspect rồi dùng **Spring AOP** để tạo một **proxy** cho các Spring bean phù hợp. Advice được gọi khi luồng đi qua proxy.
- **AspectJ weaving**: trình biên dịch hoặc bộ nạp lớp của AspectJ **đan advice vào bytecode**. Advice hoạt động tại join point của lớp đã weave, kể cả khi lời gọi không đi qua Spring proxy (nếu pointcut và phạm vi weave khớp).

Do đó, **@EnableAspectJAutoProxy không đồng nghĩa bật AspectJ weaving**. Chỉ thấy dependency **aspectjweaver** hoặc annotation **@Aspect** cũng chưa chứng minh rằng bytecode đã được weave.

Phạm vi của bài: cách cấu hình, cơ chế runtime, giới hạn và lựa chọn **trong ứng dụng Spring**; các khái niệm AOP tổng quát được dùng làm nền mà không giải thích lại từ đầu.

## 2. Bảng so sánh trực tiếp

| Khía cạnh | Spring @AspectJ auto-proxy | AspectJ weaving với Spring |
|---|---|---|
| Cơ chế | Spring AOP tạo proxy bọc bean | AspectJ thay đổi bytecode lớp đích |
| Cấu hình chính | **@EnableAspectJAutoProxy**, **<aop:aspectj-autoproxy/>**, hoặc Spring Boot AOP auto-configuration | AspectJ compiler (CTW) hoặc AspectJ weaver (LTW), có thể kết hợp hạ tầng **@EnableLoadTimeWeaving** của Spring |
| Thời điểm | Khi Spring xử lý và cung cấp bean proxy | Lúc build (**compile-time weaving**) hoặc lúc class được load (**load-time weaving**) |
| Đối tượng thường áp dụng | Spring bean thỏa điều kiện áp dụng advice | Bất kỳ lớp phù hợp nào được weave, kể cả đối tượng ngoài IoC container |
| Lời gọi kích hoạt | Phải vào **proxy** | Phải đi tới **join point đã weave** |
| Self-invocation | Gọi **this.method()** thường bỏ qua proxy, không có advice riêng cho method bên trong | Có thể được advice dù gọi nội bộ, nếu execution của method đó được weave |
| Loại join point | Spring AOP chủ yếu hỗ trợ **method execution** của Spring bean | AspectJ hỗ trợ phạm vi rộng hơn: method execution/call, constructor, field access... khi được cấu hình đúng |
| JDK proxy / CGLIB | Có; phụ thuộc interface, proxyTargetClass và cấu hình chung | Không cần Spring AOP proxy cho advice được weave |
| Final/private method | Bị ràng buộc bởi khả năng proxy/intercept; CGLIB không override được final/private | Không bị giới hạn bởi override proxy theo cùng cách, nhưng vẫn phụ thuộc pointcut, weaving và giới hạn bytecode |
| Tệp **META-INF/aop.xml** | Không cần | Thường cần cho AspectJ LTW để khai báo aspect và phạm vi weave |
| Dependencies | Spring AOP và thư viện AspectJ phục vụ việc phân tích annotation/pointcut | AspectJ compiler/weaver và cấu hình runtime/build; **spring-aspects** nếu dùng aspect do Spring cung cấp |
| Vận hành | Thường đơn giản hơn cho logic cắt ngang trên Spring services | Cần quản lý class loading, agent hoặc plugin build, classpath, phạm vi weave và log |

Luồng thực thi khác nhau:

~~~text
Spring AOP:
Caller -> Spring proxy -> Advice -> Target.method()
                                  |
                                  +-> this.otherMethod()
                                      (không quay lại proxy)

AspectJ weaving:
Caller -> Target.class đã weave -> Advice/Target.method()
                                    |
                                    +-> this.otherMethod()
                                        (Advice cũng chạy nếu otherMethod khớp)
~~~

Không nên dùng cùng một aspect vào cùng join point qua cả proxy và weaving một cách vô tình: có thể xảy ra **advice chạy hai lần**.

## 3. Kích hoạt Spring @AspectJ auto-proxy

### 3.1. Java configuration

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}
~~~

Cấu hình này yêu cầu Spring đăng ký bộ xử lý tự tạo proxy cho bean phù hợp với những aspect đã được Spring quản lý. Ví dụ:

~~~java
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LogAspect {
    @Before("execution(* com.example.service..*.*(..))")
    public void beforeServiceMethod() {
        System.out.println("before service method");
    }
}
~~~

Lớp có **@Aspect** phải được đăng ký trong Spring container (ở ví dụ dùng **@Component** và component scan, hoặc có thể dùng **@Bean**). Chỉ để annotation trong một class không được Spring phát hiện sẽ không khiến auto-proxy tự sử dụng class ấy.

XML tương đương khi dùng AOP namespace:

~~~xml
<aop:aspectj-autoproxy/>
~~~

**Lưu ý cốt lõi:** Tất cả cách cấu hình ở trên đều kích hoạt **Spring AOP**, không biên dịch lại lớp và không chạy AspectJ load-time weaver.

### 3.2. Proxy JDK và proxy CGLIB vẫn cùng một cơ chế

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = true)
~~~

Tùy chọn **proxyTargetClass=true** ưu tiên class-based proxy (CGLIB). Giá trị **false** ưu tiên JDK dynamic proxy khi bean có interface phù hợp. Nếu không có interface phù hợp, Spring có thể vẫn dùng class proxy.

Cả hai đều đòi hỏi **lời gọi đi qua proxy**. Bật CGLIB không chữa được self-invocation bằng **this**, và class-based proxy không advice được phương thức **final** bằng cách override. Với JDK proxy, lời gọi phải thực hiện qua phương thức được bộc lộ trên interface của proxy.

~~~java
@EnableAspectJAutoProxy(exposeProxy = true)
~~~

**exposeProxy=true** cho phép dùng **AopContext.currentProxy()** trong phạm vi có proxy hợp lệ. Đây là cơ chế can thiệp vào đường gọi proxy khi thực sự cần; nó **không** bật weaving. Trong thiết kế Spring thông thường, tách logic cần advice ra bean cộng tác để lời gọi đi qua proxy sẽ rõ ràng hơn.

### 3.3. Trường hợp Spring Boot

Trong dự án Spring Boot có hạ tầng AOP tương ứng (ví dụ **spring-boot-starter-aop**), auto-configuration có thể tự bật việc xử lý aspect và tạo proxy; thường không cần tự thêm **@EnableAspectJAutoProxy**.

Spring Boot mặc định dùng class-based/CGLIB proxy khi auto-configuring AOP. Có thể chọn hướng interface proxy bằng:

~~~properties
spring.aop.proxy-target-class=false
~~~

Property này chỉ đổi **kiểu proxy**, hoàn toàn không chuyển ứng dụng sang AspectJ weaving. Cần xác minh dependency, version, điều kiện auto-configuration và các cấu hình AOP/transaction dùng chung trong ứng dụng cụ thể.

## 4. Ví dụ Java 17+ chạy được: self-invocation trên Spring proxy

Ví dụ dưới dùng Java 17+, Maven, Spring Framework **6.2.0** và AspectJ Weaver **1.9.22.1**. Không cần Spring Boot để quan sát sự khác biệt.

Cấu trúc thư mục:

~~~text
proxy-demo/
├── pom.xml
└── src/main/java/demo/ProxyDemo.java
~~~

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>proxy-demo</artifactId>
    <version>1.0.0</version>
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
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**src/main/java/demo/ProxyDemo.java**

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class ProxyDemo {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AopConfig.class)) {
            BillingService service = context.getBean(BillingService.class);

            service.checkout(); // Gọi từ ngoài qua proxy.
            System.out.println("---");
            service.charge();   // Gọi charge trực tiếp qua proxy.
        }
    }
}

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class AopConfig {
    @Bean
    BillingService billingService() {
        return new BillingService();
    }

    @Bean
    TraceAspect traceAspect() {
        return new TraceAspect();
    }
}

@Aspect
class TraceAspect {
    @Before("execution(* demo.BillingService.charge(..))")
    public void beforeCharge() {
        System.out.println("advice: charge");
    }
}

class BillingService {
    public void checkout() {
        System.out.println("checkout");
        charge();
    }

    public void charge() {
        System.out.println("charge");
    }
}
~~~

Chạy trong thư mục **proxy-demo**:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.ProxyDemo
~~~

Các dòng in ra stdout từ chương trình:

~~~text
checkout
charge
---
advice: charge
charge
~~~

**Diễn giải:**

1. Spring tạo proxy cho bean **BillingService** vì pointcut chọn phương thức **charge()**.
2. **checkout()** nhận lời gọi từ proxy nhưng không khớp pointcut, nên bản thân nó không in advice.
3. Từ **checkout()**, **charge()** được gọi qua **this** của target. Lời gọi nội bộ không quay lại proxy, nên chỉ in **charge**.
4. Khi **service.charge()** được gọi trực tiếp từ bên ngoài, lời gọi đi qua proxy nên in **advice: charge** rồi mới in **charge**.

Nếu đổi **proxyTargetClass=true** thành **false** trong ví dụ mà BillingService không triển khai interface, Spring vẫn có thể tạo CGLIB proxy; kết luận về self-invocation không thay đổi.

Ở đây dependency **aspectjweaver** giúp Spring hiểu annotation và pointcut AspectJ. Mẫu này **không có AspectJ CTW/LTW**.

## 5. Kích hoạt AspectJ weaving trong ứng dụng Spring

### 5.1. Compile-time weaving (CTW)

Một bước build dùng **AspectJ compiler (ajc)**, hoặc tích hợp tương đương của công cụ build, tạo bytecode đã được weave. Khi ứng dụng chạy, advice tương ứng đã nằm trong class được triển khai.

Để sử dụng CTW, cần tích hợp AspectJ compilation/weaving vào Maven hoặc Gradle, chọn đúng phiên bản compiler và xác định package/class nào được weave. Chỉ thêm **@EnableAspectJAutoProxy** trong Spring configuration không thực hiện bước này.

### 5.2. Load-time weaving (LTW) bằng AspectJ agent

Trong LTW, AspectJ xử lý bytecode khi class được nạp vào JVM. Giả sử ứng dụng có lớp **demo.BillingService** và một aspect độc lập:

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* demo.BillingService.charge(..))")
    public void beforeCharge() {
        System.out.println("woven advice: charge");
    }
}
~~~

**src/main/resources/META-INF/aop.xml**:

~~~xml
<!DOCTYPE aspectj PUBLIC
    "-//AspectJ//DTD//EN"
    "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <aspects>
        <aspect name="demo.AuditAspect"/>
    </aspects>
    <weaver options="-showWeaveInfo">
        <include within="demo..*"/>
    </weaver>
</aspectj>
~~~

**aop.xml** đăng ký aspect cho AspectJ weaver; **include** chỉ những lớp được xem xét weave. Trong dự án thật nên giới hạn vào package ứng dụng cụ thể để giảm tác động và chi phí biến đổi class.

Cấu hình khởi chạy JVM theo hướng AspectJ agent:

~~~bash
java -javaagent:/absolute/path/aspectjweaver.jar -cp "<app-and-dependency-classpath>" demo.Main
~~~

Đây là **mẫu cấu hình startup**, không phải lệnh có thể chạy nguyên dạng: cần thay đường dẫn agent, classpath và main class thực tế. **AuditAspect**, **BillingService**, **META-INF/aop.xml** cùng các thư viện liên quan phải có trên classpath. Các lớp đích phải được weave **trước khi sử dụng**; lớp đã nạp sớm không tự được weave lại chỉ vì Spring context cấu hình sau đó.

Nếu **BillingService.charge()** được weave với pointcut trên, lời gọi **checkout() → this.charge()** cũng kích hoạt advice cho execution của charge. Không yêu cầu đối tượng phải là Spring AOP proxy. Điều này khác với kết quả mục 4.

Lưu ý: **aspectjweaver.jar** trên classpath **không tự bật** AspectJ weaving. LTW cần cơ chế weaver đang hoạt động (ví dụ agent) và cấu hình aspect phù hợp. Khi đã dùng AspectJ javaagent trực tiếp, không bắt buộc dùng **@EnableLoadTimeWeaving** để agent hoạt động.

### 5.3. Spring hỗ trợ cấu hình LTW

Spring Framework có **LoadTimeWeaver** để đăng ký transformer theo ApplicationContext/ClassLoader. Java config:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableLoadTimeWeaving;

@Configuration
@EnableLoadTimeWeaving(
    aspectjWeaving = EnableLoadTimeWeaving.AspectJWeaving.ENABLED
)
public class WeavingConfig {
}
~~~

XML tương đương:

~~~xml
<context:load-time-weaver aspectj-weaving="on"/>
~~~

Đây là **mảnh cấu hình**, chưa phải một ứng dụng runnable. Ngoài Spring config, cần có AspectJ weaver, **META-INF/aop.xml**, các aspect/class và khả năng instrument class trước lúc nạp. Ở môi trường phù hợp, Spring có thể dùng ClassLoader hỗ trợ transformer. Với JVM độc lập không có ClassLoader đặc biệt, có thể cần **-javaagent:/path/to/spring-instrument.jar** cho cách LTW do Spring điều phối.

Thuộc tính **aspectjWeaving** có các lựa chọn:

- **ENABLED**: bật AspectJ LTW của cơ chế Spring.
- **DISABLED**: tắt AspectJ LTW của cơ chế này.
- **AUTODETECT** (mặc định): Spring dò **META-INF/aop.xml** trên classpath; nếu có sẽ bật theo cấu hình.

Đừng nhầm ba thứ:

~~~text
@EnableAspectJAutoProxy
    -> Spring AOP tự tạo proxy (không weave class).

@EnableLoadTimeWeaving
    -> Spring hỗ trợ LoadTimeWeaver/instrumentation
       (còn cần aspect/weaver/điều kiện class loading).

-javaagent:aspectjweaver.jar
    -> AspectJ LTW qua JVM agent
       (cần aop.xml và các class liên quan).
~~~

Một số hạ tầng Spring khác, như JPA class transformation, cũng có thể dùng **LoadTimeWeaver**. Vì vậy nhìn thấy annotation **@EnableLoadTimeWeaving** riêng lẻ cũng không chứng minh custom advice của ứng dụng đã được weave.

## 6. Liên hệ đặc biệt quan trọng: Spring @Transactional

Mặc định các giao dịch khai báo của Spring thường áp dụng **proxy mode**:

~~~java
@Service
class OrderService {
    public void placeOrder() {
        reserve(); // Lời gọi nội bộ.
    }

    @Transactional
    public void reserve() {
        // Truy cập dữ liệu cần transaction.
    }
}
~~~

Nếu một nơi khác gọi **orderService.placeOrder()**, lời gọi **reserve()** bên trong không đi qua transaction proxy; do đó transaction advice được gắn **chỉ ở reserve()** không tự chạy cho lần gọi nội bộ đó.

Spring có chế độ riêng cho giao dịch:

~~~java
import org.springframework.context.annotation.AdviceMode;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
~~~

Với chế độ này, Spring sử dụng **AspectJ transaction aspect** được cung cấp bởi **spring-aspects**, kết hợp **AspectJ CTW/LTW** đã cấu hình. Khi lớp được weave thành công, transaction advice có thể áp dụng trên method execution dù lời gọi đến từ cùng đối tượng.

**Cảnh báo về phạm vi:** Chế độ **AdviceMode.ASPECTJ** ở **@EnableTransactionManagement** là quyết định **cho transaction advice**, không phải công tắc chung tự động weave tất cả custom **@Aspect** của dự án. Custom aspect vẫn phải được khai báo/cấu hình cho AspectJ weaver nếu muốn chạy bằng weaving.

Đối với nghiệp vụ đơn giản, cân nhắc chuyển phương thức giao dịch vào bean riêng hoặc đặt transaction boundary ở entry point để giữ cơ chế proxy dễ bảo trì.

## 7. Kiểm chứng runtime và gỡ lỗi

### 7.1. Đối với Spring auto-proxy

Dùng API Spring để xác nhận bean có phải AOP proxy:

~~~java
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean(BillingService.class);
System.out.println(AopUtils.isAopProxy(bean));
~~~

Kết quả **true** chỉ ra bean đang là Spring AOP proxy. Kết quả **false** không chứng minh rằng AspectJ weaving đã diễn ra: cũng có thể bean không có aspect phù hợp hoặc bị cấu hình sai.

Kiểm tra aspect có là Spring bean, auto-proxy có bật, pointcut có khớp, target được lấy từ container, caller có thực sự dùng proxy, method có đủ điều kiện bị intercept hay không.

### 7.2. Đối với AspectJ weaving

Kiểm tra JVM agent hoặc hạ tầng **LoadTimeWeaver**, file **META-INF/aop.xml** có trên classpath, aspect đã được khai báo, các class target thuộc **include**, và thời điểm class được nạp. Có thể dùng **-showWeaveInfo** để quan sát thông tin weave từ AspectJ.

Để kiểm tra hành vi, tạo ca **this.inner()** mà pointcut khớp inner và so sánh advice. Nếu advice xuất hiện ở lời gọi nội bộ, kiểm tra rõ đó là weaving hay do một cách gọi qua proxy khác. Không dựa vào tên annotation để kết luận.

### 7.3. Các nhầm lẫn thường gặp

| Hiện tượng | Giải thích hoặc cách xử lý |
|---|---|
| Có @Aspect nhưng advice không chạy | Kiểm tra aspect bean, AOP auto-configuration và pointcut |
| Gọi từ bean khác thì advice chạy, gọi bằng this thì không | Spring proxy không intercept self-invocation |
| Đã bật CGLIB nhưng this.method() vẫn không được advice | CGLIB là loại proxy, không phải weaving |
| Có aspectjweaver trong Maven nhưng chưa có weaving | Cần cấu hình AspectJ CTW hoặc LTW thật sự |
| Có @EnableLoadTimeWeaving nhưng không có advice | Kiểm tra instrument/ClassLoader, AspectJ, aop.xml và classpath |
| Method final/private không chạy advice với proxy | Giới hạn của proxy; không thể suy ra weaving từ proxyTargetClass |
| Pointcut dùng call(...), get(...) hoặc set(...) làm Spring AOP báo lỗi | Đây không phải các designator được Spring AOP hỗ trợ; AspectJ weaving có phạm vi rộng hơn |
| Advice xuất hiện hai lần | Kiểm tra đồng thời proxy và weaving áp dụng cùng join point |
| LTW hoạt động local nhưng không hoạt động production | So sánh javaagent, classpath, package include, class loading và packaging |

## 8. Tiêu chí lựa chọn cho ứng dụng Spring

**Ưu tiên Spring @AspectJ auto-proxy** nếu yêu cầu chủ yếu là logging, tracing, kiểm soát truy cập hoặc xử lý cắt ngang tại **method của Spring bean**. Mô hình proxy thường đủ, cấu hình đơn giản và dễ kiểm thử. Thiết kế lời gọi qua các ranh giới bean rõ ràng giúp tránh bẫy self-invocation.

**Xem xét AspectJ weaving** khi thực sự cần advice cho **self-invocation**, đối tượng ngoài Spring container hoặc những join point Spring AOP không hỗ trợ (constructor/field access...). Đổi lại phải quản trị AspectJ compiler/agent, classpath, phạm vi weave, vòng đời class và khả năng quan sát runtime.

Một checklist ra quyết định ngắn:

1. Đối tượng cần advice có do Spring quản lý không?
2. Lời gọi cần advice đi qua Spring proxy không?
3. Pointcut có thuộc method execution được Spring AOP hỗ trợ không?
4. Có cần can thiệp self-invocation hoặc đối tượng tạo bằng new không?
5. Dự án có thể kiểm soát build/weaver/JVM agent của mọi môi trường chạy không?
6. Đã có phép thử hành vi xác nhận số lần advice chạy chưa?

Nếu ba câu đầu là **có** và không cần những khả năng mở rộng, **Spring AOP auto-proxy** thường hợp lý. Nếu câu 4 là **có**, hãy đánh giá **AspectJ weaving** cùng yêu cầu vận hành tại câu 5.

## 9. Câu hỏi tự kiểm tra

1. Có **@Aspect** và **@EnableAspectJAutoProxy** thì bytecode đã được weave chưa? **Chưa**: đây là Spring AOP proxy.
2. Đổi **proxyTargetClass=true** có chặn được this.method() không? **Không**.
3. Chỉ thêm dependency **aspectjweaver** có tự bật LTW không? **Không**.
4. **@EnableLoadTimeWeaving** tự động chọn mọi class để weave? **Không**, vẫn cần weaver, aspect, metadata và môi trường thích hợp.
5. Spring **@Transactional** với **mode=ASPECTJ** có làm mọi custom aspect dùng weaving? **Không**, nó chỉ chọn cơ chế transaction.
6. Kiểm tra **AopUtils.isAopProxy(bean)** trả false có nghĩa chắc chắn đã weaving? **Không**, cần kiểm tra cấu hình và bằng chứng weave riêng.

## 10. Nguồn tham khảo chính thức

- Spring Framework — @AspectJ support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Declaring a Pointcut: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — Using AspectJ with Spring Applications: https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html
- Spring Framework — Using @Transactional: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- Spring Boot — Aspect-Oriented Programming: https://docs.spring.io/spring-boot/reference/features/aop.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-integration-003">Vai trò spring-aspects trong các tích hợp AspectJ do Spring cung cấp</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò spring-aspects trong các tích hợp AspectJ do Spring cung cấp

## 1. spring-aspects là gì và giải quyết nhu cầu nào?

`spring-aspects` là một module của **Spring Framework**, được phát hành dưới Maven coordinate `org.springframework:spring-aspects`. JAR này đóng gói một số **AspectJ aspects do Spring cung cấp** để kết hợp **AspectJ compile-time weaving hoặc load-time weaving** với dịch vụ Spring IoC, transaction, caching và async.

Điểm khác biệt phải hiểu ngay:

- **Spring AOP proxy**: hạ tầng `spring-aop` tạo proxy cho bean Spring; method phải được gọi *qua proxy* để interceptor/advice chạy. `@EnableAspectJAutoProxy` cho phép viết các aspects dạng `@Aspect` nhưng **vẫn chạy bằng Spring proxy**.
- **AspectJ weaving**: AspectJ compiler hoặc weaver thay đổi bytecode của các class liên quan, nên có thể áp dụng hành vi ở những join point không đi qua Spring proxy, chẳng hạn một method được gọi từ cùng class hoặc một object được tạo bằng `new` (nếu aspect thực sự match join point đó).
- **`spring-aspects`**: cung cấp *aspect implementation* phía Spring cho phương án weaving, ví dụ `AnnotationBeanConfigurerAspect`, `AnnotationTransactionAspect`, `AnnotationCacheAspect`. Đây không phải tên khác của `spring-aop` và cũng không phải bản thân trình biên dịch/weaver.

```text
Spring AOP:
caller -> Spring proxy -> TransactionInterceptor/CacheInterceptor/@Aspect -> target
                    |
                    +-- self-invocation từ target không quay lại proxy

Spring + AspectJ:
AspectJ compile-time hoặc load-time weaving sửa bytecode type đã chọn
-> AnnotationTransactionAspect / AnnotationBeanConfigurerAspect / ...
-> dùng Spring BeanFactory, TransactionManager, CacheManager và Executor
-> có thể xử lý join point không được Spring proxy nhìn thấy
```

Không phải mọi dự án Spring đều cần module này. Với Spring-managed services và method call đi qua proxy, cách dùng `@Transactional`, `@Cacheable`, `@Async` mặc định thường đủ. Chỉ dùng `spring-aspects` khi muốn kết hợp **AspectJ weaving thực sự** với các cơ chế Spring.

## 2. Phân biệt các JAR và vai trò

| Thành phần | Nhà phát hành | Vai trò chính | Có tự kích hoạt weaving? |
| --- | --- | --- | --- |
| `org.springframework:spring-aop` | Spring | Proxy, Advisor, Advice, ProxyFactory và các interceptor Spring AOP | Không |
| `org.springframework:spring-aspects` | Spring | AspectJ aspect implementations tích hợp với Spring IoC/transaction/cache/async | Không |
| `org.aspectj:aspectjweaver` | AspectJ | Weaver và Java agent để weaving khi class được nạp; cũng phục vụ pointcut parsing | Cần bật LTW thực tế, ví dụ `-javaagent` |
| AspectJ compiler `ajc` / `aspectjtools` | AspectJ | Compile-time/post-compile weaving | Chỉ weave khi build thực sự chạy trình biên dịch |
| `spring-tx`, `spring-context`, `spring-context-support`, ... | Spring | Transaction APIs, IoC, cache adapter và infrastructure liên quan | Không |

Khi học Spring AOP với `@Aspect`, có thể cần AspectJ library để parser hiểu pointcut nhưng **không cần** `spring-aspects` chỉ vì dùng annotation `@Aspect`. Hai chiều ngược lại cũng đúng: thêm `spring-aspects` nhưng không cấu hình weaving thì object `new` hoặc self-invocation không tự có hành vi đặc biệt.

## 3. Các AspectJ aspects quan trọng Spring cung cấp

Những lớp sau xuất hiện trong các tích hợp `spring-aspects` của Spring Framework 6.x:

| Spring aspect | Chức năng |
| --- | --- |
| `org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect` | Cấu hình/bơm dependency vào object đánh dấu `@Configurable` được tạo ngoài Spring IoC |
| `org.springframework.transaction.aspectj.AnnotationTransactionAspect` | Đọc Spring `@Transactional` bằng AspectJ weaving và giao việc quản lý transaction cho Spring |
| `org.springframework.transaction.aspectj.JtaAnnotationTransactionAspect` | Cung cấp AspectJ transaction advice cho `jakarta.transaction.Transactional` |
| `org.springframework.cache.aspectj.AnnotationCacheAspect` | AspectJ advice cho Spring cache annotations chuẩn |
| `org.springframework.cache.aspectj.JCacheCacheAspect` | Hỗ trợ cache annotations JCache/JSR-107 khi có dependencies/provider phù hợp |
| `org.springframework.scheduling.aspectj.AnnotationAsyncExecutionAspect` | AspectJ execution advice cho `@Async` |

`spring-aspects` còn có các abstract aspects, như `AbstractBeanConfigurerAspect` và `AbstractTransactionAspect`, để phát triển AspectJ aspect với custom pointcut nhưng sử dụng lại infrastructure Spring. Chúng phục vụ tình huống nâng cao, không phải thứ mỗi service nên tự viết lại.

**Không lẫn vai trò:** `AnnotationTransactionAspect` không tự cung cấp transaction manager; `AnnotationCacheAspect` không tự dựng Redis/Caffeine/TTL; `AnnotationAsyncExecutionAspect` không tự làm mọi executor hay thread context an toàn. Chúng chỉ kết nối woven join point với các dịch vụ và chính sách tương ứng của Spring.

## 4. @Configurable và AnnotationBeanConfigurerAspect: tích hợp cốt lõi

Một Spring-managed bean thường nhận dependency qua constructor hoặc `@Autowired` vì container trực tiếp tạo/quản lý bean đó. Nhưng object do ORM, deserializer, business code hoặc thư viện bên thứ ba tạo bằng `new` không được Spring xử lý lifecycle như bean thông thường.

`@Configurable` của Spring dùng với `AnnotationBeanConfigurerAspect` để **cấu hình instance do bên ngoài tạo** sau khi AspectJ weaving nhận diện việc tạo object. Ví dụ:

```java
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;

@Configurable(autowire = Autowire.BY_TYPE)
public class Invoice {
    private DiscountPolicy discountPolicy;

    public void setDiscountPolicy(DiscountPolicy discountPolicy) {
        this.discountPolicy = discountPolicy;
    }
}
```

Đây là snippet minh họa (type `DiscountPolicy` cần được định nghĩa); sử dụng `Autowire.BY_TYPE` để Spring tìm dependency phù hợp với JavaBean setter. Có thể dùng `@Configurable("invoiceTemplate")` với bean definition mẫu thay cho autowire-by-type. Đừng hiểu `@Configurable` như `@Component`: Spring có thể *cấu hình* object nhưng **không sở hữu toàn bộ vòng đời của object ngoài container**.

### 4.1. Điều kiện bắt buộc

Để `new Invoice()` thực sự được inject:

1. Có `spring-aspects` trên classpath với `AnnotationBeanConfigurerAspect`.
2. Class `Invoice` **được AspectJ weave** qua CTW/LTW; chỉ khai báo annotation là chưa đủ.
3. Spring kết nối aspect với BeanFactory, thường bằng `@EnableSpringConfigured` hoặc XML `<context:spring-configured/>`.
4. Có bean/bean definition hợp lệ để resolve dependency.
5. Object được tạo sau khi Spring/AspectJ đã cấu hình xong aspect.

```java
@Configuration
@EnableSpringConfigured
class AppConfig {
    // Các @Bean dependency mà object @Configurable cần.
}
```

`@EnableSpringConfigured` là `org.springframework.context.annotation.EnableSpringConfigured`. Nó **cung cấp cấu hình Spring IoC cho aspect**, không tự thay thế `-javaagent` hoặc compile-time weaving.

### 4.2. Thời điểm injection

Với `@Configurable` mặc định, cấu hình thường diễn ra **sau constructor initialization**. Dependency có thể chưa tồn tại trong *body* constructor, nên gọi field được inject tại đây có nguy cơ `NullPointerException`. Spring có `@Configurable(preConstruction = true)` cho use case thật sự cần cấu hình trước constructor body, nhưng phải xác minh weaving tương ứng.

Tránh đánh dấu `@Configurable` trên class mà Spring đồng thời tạo như bean thông thường: một instance có thể bị cấu hình **hai lần** (container + AspectJ aspect). Với domain object do container quản lý, constructor injection chuẩn thường đơn giản hơn.

### 4.3. Nhiều ApplicationContext và ClassLoader

`AnnotationBeanConfigurerAspect` dùng AspectJ singleton-aspect semantics. Một ClassLoader có một singleton aspect instance, trong khi ứng dụng có thể có nhiều Spring ApplicationContext cùng ClassLoader. Khi đó phải xác định BeanFactory nào được aspect tham chiếu; context cha thường là vị trí hợp lý nếu nhiều child contexts cần dependency chung. Trong server nhiều web apps, tránh đưa `spring-aspects` vào classloader toàn server khiến ứng dụng vô tình chia sẻ singleton aspect.

## 5. Ví dụ Java 17 chạy được: @Configurable trên object tạo bằng new

Ví dụ dưới đây sử dụng Java 17, Spring Framework 6.2.19, AspectJ Weaver 1.9.24 và Maven. Spring container chỉ tạo bean `Formatter`; `Invoice` được tạo bằng `new Invoice()`. AspectJ LTW giúp Spring inject `Formatter` vào `Invoice` qua setter mặc dù `Invoice` không phải Spring bean.

### 5.1. Layout project

```text
spring-aspects-demo/
  pom.xml
  src/main/java/demo/
    App.java
    Invoice.java
    Formatter.java
```

JAR `spring-aspects` có sẵn `META-INF/aop.xml` khai báo các Spring AspectJ aspects, gồm `AnnotationBeanConfigurerAspect`; project ví dụ không cần tạo bản sao `aop.xml` chỉ để khai báo lại aspect này.

### 5.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-aspects-demo</artifactId>
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
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-aspects</artifactId>
      <version>6.2.19</version>
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
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <version>3.7.1</version>
        <configuration>
          <artifactItems>
            <artifactItem>
              <groupId>org.aspectj</groupId>
              <artifactId>aspectjweaver</artifactId>
              <version>1.9.24</version>
              <outputDirectory>${project.build.directory}</outputDirectory>
              <destFileName>aspectjweaver.jar</destFileName>
            </artifactItem>
          </artifactItems>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.1</version>
        <configuration>
          <executable>java</executable>
          <arguments>
            <argument>-javaagent:${project.build.directory}/aspectjweaver.jar</argument>
            <argument>-cp</argument>
            <classpath/>
            <argument>demo.App</argument>
          </arguments>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

Maven dependency plugin copy `aspectjweaver.jar` vào `target/`. Exec plugin mở một **JVM mới** với `-javaagent`; dùng `exec:exec` (thay vì `exec:java` chạy trong Maven JVM vốn có thể không có agent). Cách này thể hiện rõ prerequisite quan trọng nhất: AspectJ weaver hoạt động ngay từ lúc JVM con bắt đầu tải class.

### 5.3. Formatter.java

`src/main/java/demo/Formatter.java`:

```java
package demo;

public class Formatter {
    public String format(String id) {
        return "invoice=" + id + " (spring configured)";
    }
}
```

### 5.4. Invoice.java

`src/main/java/demo/Invoice.java`:

```java
package demo;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;

@Configurable(autowire = Autowire.BY_TYPE)
public class Invoice {
    private Formatter formatter;

    public void setFormatter(Formatter formatter) {
        this.formatter = formatter;
    }

    public String display(String id) {
        return formatter == null
                ? "NOT_CONFIGURED"
                : formatter.format(id);
    }
}
```

Class `Invoice` không có `@Component`/`@Bean` và không đi qua Spring AOP proxy. `setFormatter` là JavaBean setter để autowire-by-type có thể inject. Chuỗi `NOT_CONFIGURED` nhằm phát hiện việc thiếu weaving hoặc thiếu cấu hình BeanFactory.

### 5.5. App.java

`src/main/java/demo/App.java`:

```java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableSpringConfigured;

public class App {
    @Configuration(proxyBeanMethods = false)
    @EnableSpringConfigured
    public static class Config {
        @Bean
        Formatter formatter() {
            return new Formatter();
        }
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            Invoice invoice = new Invoice();
            System.out.println(invoice.display("A-101"));
        }
    }
}
```

Chạy từ thư mục có `pom.xml` trên JDK 17+ với Maven:

```bash
mvn -q compile dependency:copy exec:exec
```

Dòng `System.out` mong đợi **khi weaving và Spring aspect đã được cấu hình đúng**:

```text
invoice=A-101 (spring configured)
```

Các thông báo thêm của AspectJ/Spring/Maven nếu có không thuộc stdout do code trên sinh. Luồng xử lý:

1. Maven compile ba class; dependency plugin copy file agent.
2. Exec plugin khởi chạy JVM có `-javaagent:.../aspectjweaver.jar`; AspectJ LTW đọc `META-INF/aop.xml` từ `spring-aspects`.
3. `AnnotationConfigApplicationContext` tạo bean `Formatter` và cấu hình `AnnotationBeanConfigurerAspect` qua `@EnableSpringConfigured`.
4. `new Invoice()` được AspectJ intercept ở initialization join point; BeanFactory cấp `Formatter` qua setter.
5. `display` in kết quả đã dùng dependency Spring dù `Invoice` không phải Spring bean.

**Nếu agent không bật**, cùng source có thể vẫn compile nhưng object `Invoice` không nhận injection; `display` sẽ trả `NOT_CONFIGURED`. Đây là khác biệt giữa **có aspect JAR** và **thực sự weave bytecode**.

## 6. Transaction AspectJ mode với @Transactional

Spring proxy mode mặc định dùng `TransactionInterceptor` trên proxy; tự gọi method trong cùng target qua `this` không đi vào interceptor. Nếu cần áp dụng transaction advice trên woven method execution, Spring cho cấu hình:

```java
@Configuration
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
class TxConfig {
    @Bean
    PlatformTransactionManager transactionManager(DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
```

Đây là **mảnh cấu hình**, không phải chương trình độc lập. Cần import `org.springframework.context.annotation.AdviceMode`, `org.springframework.transaction.annotation.EnableTransactionManagement`, `org.springframework.transaction.PlatformTransactionManager`, `org.springframework.jdbc.datasource.DataSourceTransactionManager` và `javax.sql.DataSource`, cùng các dependencies `spring-tx`, `spring-jdbc`, `spring-aspects` và weaving phù hợp.

`AnnotationTransactionAspect` được cung cấp bởi Spring-aspects. Spring vẫn quản lý transaction qua `PlatformTransactionManager`; AspectJ chỉ cung cấp điểm interception ở bytecode, thay vì giới hạn lời gọi đi qua proxy. Khi target class đã woven, self-invocation có thể được xử lý theo transaction semantics, không phụ thuộc việc lời gọi từ cùng object.

**Cần đặt `@Transactional` trên concrete implementation class/method** khi dùng AspectJ weaving. Annotation chỉ đặt trên interface không được AspectJ nhận diện theo đúng quy tắc Java annotation inheritance. AspectJ weaving có thể xử lý cả method không-public đã được annotate nếu pointcut phù hợp; không suy ra `@Transactional` trên mọi private method đều mặc nhiên hoạt động khi annotation không có/weaving bị thiếu.

Nếu dùng chuẩn `jakarta.transaction.Transactional`, Spring-aspects cung cấp `JtaAnnotationTransactionAspect` cho annotation đó. Hãy xác định rõ API annotation và transaction configuration, không coi hai annotations thuộc cùng package.

## 7. AspectJ mode cho caching và @Async

### 7.1. Caching

Mặc định:

```java
@EnableCaching
class CacheConfig {
}
```

Với weaving-based caching:

```java
@EnableCaching(mode = AdviceMode.ASPECTJ)
class CacheConfig {
}
```

Spring-aspects chứa `AnnotationCacheAspect` để nhận biết các Spring annotations chuẩn như `@Cacheable`, `@CachePut`, `@CacheEvict` trên method/class được weave. Cần cấu hình `CacheManager` thích hợp; AspectJ không tự cấp TTL, eviction policy của provider, hoặc distributed storage.

**Giới hạn meta-annotation:** Spring Reference lưu ý Spring-aspects cung cấp aspect cho annotations chuẩn. Nếu ứng dụng định nghĩa custom composed cache annotation, việc áp dụng bằng AspectJ không mặc định được hỗ trợ y hệt proxy mode; có thể cần custom AspectJ aspect hoặc thiết kế lại cách annotate.

### 7.2. Async

Spring-aspects chứa `AnnotationAsyncExecutionAspect` để tích hợp với `@Async` trong mode AspectJ:

```java
@EnableAsync(mode = AdviceMode.ASPECTJ)
class AsyncConfig {
}
```

Cần executor và hạ tầng Spring thích hợp. Việc weave giúp intercept các invocation không qua Spring proxy, nhưng không tự chuyển transaction đang gắn thread sang executor thread, cũng không giải quyết chính sách retry/idempotency hay thread-safety.

### 7.3. JCache

`JCacheCacheAspect` phục vụ JCache/JSR-107 annotations khi ứng dụng có API và cache provider tương thích. Nó không đồng nghĩa mọi Spring CacheManager lập tức trở thành JCache manager. Trong Spring 6, kiểm tra cẩn thận phiên bản API/JCache provider thực tế khi tích hợp.

## 8. Compile-time weaving và load-time weaving

Có hai hướng phổ biến:

**Compile-time/post-compile weaving:** dùng AspectJ compiler `ajc` và build plugin để xử lý class/artifact lúc build. Chỉ thêm `spring-aspects` như Maven dependency rồi chạy `javac` không tự tạo woven class. Cần kiểm tra class files được weave và đảm bảo artifact build chuyển sang môi trường production.

**Load-time weaving (LTW):** AspectJ chỉnh class bytes khi JVM nạp class. Một ứng dụng Java thông thường có thể khởi chạy:

```bash
java -javaagent:/path/to/aspectjweaver.jar -cp "..." demo.App
```

Đây là **cú pháp khung**; phần 5 có một ví dụ Maven có thể dùng để sinh classpath. Thứ tự JVM option rất quan trọng: `-javaagent` phải là tham số của JVM **trước** main class. Class đã tải trước khi weaver cài vào thường không được weave hồi tố bởi LTW tiêu chuẩn.

Spring cũng có `@EnableLoadTimeWeaving` và XML `<context:load-time-weaver/>` để tích hợp `LoadTimeWeaver` với ClassLoader phù hợp. Ở một số môi trường JVM thông thường, `spring-instrument` Java agent hoặc Java instrumentation phù hợp là cần thiết. Không đồng nhất `@EnableLoadTimeWeaving` với `@EnableSpringConfigured` hoặc `@EnableAspectJAutoProxy`:

| Cấu hình | Tác dụng |
| --- | --- |
| `@EnableAspectJAutoProxy` | Đăng ký Spring proxy advisors cho @AspectJ-style Spring AOP |
| `@EnableSpringConfigured` | Cấu hình `AnnotationBeanConfigurerAspect` để dùng Spring BeanFactory |
| `@EnableTransactionManagement(mode = ASPECTJ)` | Chọn AspectJ advice cho transaction annotation |
| `@EnableCaching(mode = ASPECTJ)` | Chọn AspectJ advice cho caching |
| AspectJ `-javaagent` hoặc `ajc` | Thực hiện weaving thật |
| `spring-aspects` dependency | Cung cấp các AspectJ aspect do Spring viết |

### META-INF/aop.xml

AspectJ LTW đọc các file `META-INF/aop.xml` trên classpath để biết aspects nào cần tham gia. Spring-aspects có file aop.xml của nó. Khi ứng dụng tự viết custom AspectJ aspect, có thể khai báo aop.xml riêng:

```xml
<aspectj>
  <weaver options="-showWeaveInfo">
    <include within="demo..*"/>
  </weaver>
  <aspects>
    <aspect name="demo.audit.CustomAuditAspect"/>
  </aspects>
</aspectj>
```

Đây chỉ là **mảnh minh họa cho custom aspect giả định**, không phải file cần thêm vào project ở phần 5. Khi giới hạn weaving packages, phải đảm bảo những type mà Spring-aspects xử lý như `Invoice` vẫn nằm trong phạm vi được weave.

## 9. Điều gì thay đổi so với Spring AOP proxy?

| Trường hợp | Proxy mode | Spring + AspectJ weaving |
| --- | --- | --- |
| Spring bean được gọi qua proxy | Có thể áp dụng advice | Áp dụng khi class/method đã woven và aspect match |
| `this.otherMethod()` trong cùng target | Bỏ qua proxy advice cho lời gọi nội bộ | Woven method execution có thể được intercept |
| Object tạo bằng `new` ngoài container | Không tự nhận Spring AOP/DI behavior | `@Configurable` + BeanConfigurerAspect có thể inject |
| Transaction advice cho annotated private method | Proxy không intercept private method | AspectJ có thể áp dụng nếu annotation/pointcut và weaving đúng |
| Độ phức tạp build/runtime | Chủ yếu IoC/proxy config | Cần theo dõi ajc/LTW/agent, classloading, aspect binding |
| Aspect singleton trong nhiều contexts | Spring bean thường theo context | AspectJ singleton theo ClassLoader có thể liên kết tới một context |

Một lợi ích cụ thể của `spring-aspects` là giữ nguyên policy/abstraction của Spring khi đổi điểm interception từ proxy thành woven join point. Weaving không có nghĩa mọi API quản lý tài nguyên Spring tự biết xử lý các resource độc lập hay thread khác.

## 10. Khi nào nên dùng spring-aspects?

**Cân nhắc dùng** khi:

- Cần inject dependency từ BeanFactory vào object được tạo ngoài Spring mà không thể dễ dàng chuyển object creation sang Spring factory.
- Cần `@Transactional` hoặc `@Cacheable` áp dụng tại self-invocation/non-public method, và không thể tổ chức lại service boundary theo Spring proxy.
- Dự án đã có AspectJ CTW/LTW ổn định và muốn tái sử dụng các aspects do Spring cung cấp thay vì viết lại transaction/caching/async aspect.
- Đã đánh giá được chi phí vận hành, classloader, build pipeline và integration tests của weaving.

**Ưu tiên proxy mode** cho service thông thường có thể tổ chức thành nhiều Spring beans và gọi qua DI. Việc thêm LTW chỉ để tránh tách một service method có thể làm deployment/test/debug phức tạp hơn đáng kể.

Không dùng `spring-aspects` làm giải pháp chung cho vấn đề sai bean scope, transaction manager không được cấu hình, cache key sai, method security thiếu policy hoặc lifecycle của object. Mỗi vấn đề có tầng Spring riêng cần xử lý.

## 11. Những lỗi thường gặp khi triển khai

1. **Có `@Configurable` nhưng không inject:** thiếu agent/ajc, chưa weave đúng class, thiếu `@EnableSpringConfigured` hoặc không có dependency để resolve.
2. **Thêm `spring-aspects` và nghĩ `@EnableAspectJAutoProxy` sẽ weave:** Spring AOP auto proxy dùng @AspectJ declaration style; nó **không** thực hiện AspectJ bytecode weaving.
3. **Đã bật weaving nhưng `@Transactional` self-invocation không đúng:** kiểm tra `mode = ASPECTJ`, annotation trên implementation class/method, target class đã woven và transaction manager.
4. **Giữ các annotation chỉ ở interface:** AspectJ không kế thừa annotation từ interface theo kiểu cần thiết cho Spring transaction/cache aspect; chuyển annotation đến implementation thích hợp.
5. **`@Configurable` trên Spring-managed bean:** bị cấu hình hai lần; kiểm tra init callbacks và tránh đăng ký cùng class ở hai cơ chế.
6. **Inject dependency rồi dùng ngay trong constructor:** mặc định injection của `@Configurable` thường xảy ra sau object initialization; cân nhắc `preConstruction` nếu thực sự cần.
7. **Class được tải quá sớm:** agent không thể retroactively weave class đã được loader định nghĩa; xác minh JVM startup, loader và log weave.
8. **`spring-aspects` version lệch Spring core/tx/context:** đồng bộ phiên bản bằng Spring BOM hoặc khai báo cùng release.
9. **Có nhiều ApplicationContexts trong cùng ClassLoader:** `AnnotationBeanConfigurerAspect` singleton có thể giữ tham chiếu BeanFactory từ context không mong muốn.
10. **Custom cache annotation trong AspectJ mode không hoạt động:** built-in `AnnotationCacheAspect` hỗ trợ annotations chuẩn; xem lại pointcut/aspect nếu dùng meta-annotations tùy biến.
11. **Muốn kiểm tra weaving bằng `AopUtils.isAopProxy()`:** AspectJ weaving không nhất thiết tạo Spring AOP proxy; kết quả `false` chưa nói được object có woven hay không.
12. **Production thiếu JVM agent hoặc build weave step:** local chạy được nhưng deployment class bytes không có aspect behavior; phải kiểm tra pipeline thực tế.
13. **Performance/độ ổn định classloader:** không weave quá rộng, ghi log join point đã woven và theo dõi startup/runtime costs.

## 12. Checklist QA và tự kiểm tra

Trước khi coi tích hợp hoàn tất:

- [ ] Spring Framework modules cùng version, AspectJ version hỗ trợ JDK/classfile hiện tại.
- [ ] Aspect cần dùng có trong `spring-aspects` và runtime classpath.
- [ ] `META-INF/aop.xml` được AspectJ thấy hoặc cấu hình CTW phù hợp.
- [ ] Weaving thật đã bật trước khi target class được tải.
- [ ] BeanFactory/TransactionManager/CacheManager/Executor được Spring cấu hình.
- [ ] `@EnableSpringConfigured` hoặc mode `ASPECTJ` được thiết lập đúng theo feature.
- [ ] Annotation nằm trên concrete class/method khi aspect yêu cầu.
- [ ] Test kết quả của object tạo bằng `new`, self-invocation, exception/rollback, cache hit/miss theo use case.
- [ ] Kiểm tra nhiều contexts/classloaders và side effects nếu có.
- [ ] Chạy test trên JVM startup gần production, tránh chỉ kiểm tra qua Spring proxy.

**Câu hỏi 1:** `spring-aspects` có phải dependency bắt buộc cho mọi `@Aspect` trong Spring?  
**Trả lời:** Không. Spring AOP hỗ trợ @AspectJ style qua proxies; spring-aspects chứa AspectJ aspects do Spring phân phối cho weaving use cases.

**Câu hỏi 2:** `@EnableSpringConfigured` có tự thêm weaving vào JVM?  
**Trả lời:** Không; annotation đó cấu hình BeanFactory cho aspect. Phải có AspectJ CTW/LTW.

**Câu hỏi 3:** Vì sao `new Invoice()` có thể dùng bean `Formatter`?  
**Trả lời:** AspectJ LTW gắn `AnnotationBeanConfigurerAspect` vào initialization join point của `Invoice` để Spring BeanFactory cấu hình dependency.

**Câu hỏi 4:** Đổi `@Transactional` sang `AdviceMode.ASPECTJ` có tự tạo transaction manager?  
**Trả lời:** Không; vẫn cần transaction manager và resource integration hợp lệ.

**Câu hỏi 5:** Vì sao nên đặt `@Transactional` trên implementation khi weave bằng AspectJ?  
**Trả lời:** Java không tự kế thừa method annotation từ interface; AspectJ transaction aspect cần thấy annotation trên implementation.

**Câu hỏi 6:** AspectJ weaving có làm Spring aspect singleton riêng cho từng ApplicationContext?  
**Trả lời:** Không; AspectJ singleton aspect thường theo ClassLoader, nên nhiều ApplicationContexts phải cẩn trọng.

## Tài liệu Spring chính thức

- Spring Framework 6.2 — Using AspectJ with Spring Applications: https://docs.spring.io/spring-framework/reference/6.2/core/aop/using-aspectj.html
- Spring Framework 6.2 — Transaction annotations và mode: https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html
- Spring Framework 6.2 — Declarative cache annotations: https://docs.spring.io/spring-framework/reference/6.2/integration/cache/annotations.html
- Spring Framework — @AspectJ trong Spring AOP: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework source — spring-aspects META-INF/aop.xml: https://github.com/spring-projects/spring-framework/blob/main/spring-aspects/src/main/resources/META-INF/aop.xml


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-integration-004">Đưa phụ thuộc Spring vào aspect do AspectJ quản lý</a>

<details>
<summary>Xem chi tiết</summary>

# Đưa phụ thuộc Spring vào aspect do AspectJ quản lý

## 1. Bài toán và ranh giới trách nhiệm

Một ứng dụng Spring cần áp dụng một aspect bằng **AspectJ compile-time weaving (CTW)** hoặc **load-time weaving (LTW)** để chặn các join point mà Spring AOP proxy không xử lý được. Aspect này lại cần một dịch vụ đang là Spring bean, chẳng hạn `AuditSink`, `Clock`, repository hay configuration service. Khi đó nảy sinh câu hỏi: **ai tạo instance của aspect và ai đưa dependency vào nó?**

Với một aspect do **AspectJ runtime** tạo, `@Autowired` hay `@Component` trên class aspect **không tự động bảo đảm** Spring được quyền tạo và inject vào chính instance đang chạy advice. Cơ chế đúng cần kết nối **instance do AspectJ sở hữu** với **dependency injection do Spring IoC thực hiện**.

Spring Framework hỗ trợ phương án cho aspect AspectJ singleton: đăng ký một bean có `class` là aspect và **`factory-method="aspectOf"`**. Spring gọi `aspectOf()` để **lấy instance do AspectJ quản lý**, sau đó cấu hình các thuộc tính/phụ thuộc của **chính instance đó**. Với aspect có mô hình `perthis`, `pertarget` hoặc các mô hình `per-*` khác, việc cấu hình phức tạp hơn vì không chỉ có một singleton để lấy bằng `aspectOf()` không tham số.

**Phạm vi bài:** trọng tâm là Spring IoC kết hợp aspect AspectJ đang được compiler/weaver quản lý; kiến thức về các loại pointcut chung chỉ nhắc đủ để giải thích wiring. Không nhầm khái niệm với Spring AOP auto-proxy.

## 2. Phân biệt rõ hai trường hợp trước khi inject

| Tiêu chí | Aspect `@Aspect` do Spring AOP sử dụng | Aspect chạy bằng AspectJ weaving |
| --- | --- | --- |
| Ai tạo và giữ instance advice? | Thường Spring tạo aspect bean, Spring AOP trích xuất advice từ bean đó | AspectJ instantiation model và runtime quyết định instance |
| Cơ chế gọi advice | Lời gọi đi qua Spring AOP proxy | Advice đã được weave vào class/join point phù hợp |
| `@Autowired` khi aspect do Spring tạo | Hoạt động như trên Spring bean thông thường | Chỉ hoạt động nếu **instance AspectJ thực tế** được Spring cấu hình đúng |
| Công cụ kích hoạt | `@EnableAspectJAutoProxy` / `<aop:aspectj-autoproxy/>` | AspectJ compiler (`ajc`) hoặc weaver agent + cấu hình weave |
| Có cần `aspectOf()` để Spring lấy singleton aspect do AspectJ tạo? | Thường không | Có trong cách tích hợp singleton chuẩn |
| Hạn chế proxy/self-invocation | Có | Woven advice có thể đi vào join point không thông qua proxy, theo giới hạn và cấu hình weaving |

`@EnableAspectJAutoProxy` **không kích hoạt weaving**. Chữ “AspectJ” ở đây là kiểu khai báo `@AspectJ` mà Spring AOP hiểu để xây **proxy**. Muốn thực sự dùng aspect do AspectJ quản lý, cần CTW/LTW và cơ chế đăng ký riêng.

### Vấn đề khi tạo nhầm instance

Hãy tưởng tượng AspectJ đang dùng instance `AuditAspect#A`, còn `@Bean` của bạn gọi `new AuditAspect()` và Spring inject bean vào instance `AuditAspect#B`. Advice woven tiếp tục chạy trên `#A`; phụ thuộc được inject vào `#B` nên `#A` vẫn `null`.

Sơ đồ phương án đúng với **AspectJ singleton**:

~~~text
AspectJ compiler/weaver + runtime
                  |
            tạo/giữ instance
                  |
            AuditAspect.aspectOf()
                  |
             Spring IoC lấy về
                  |
     setAuditSink(auditSinkBean)
                  |
                  v
       Chính instance advice dùng
          AuditSink do Spring cấp
~~~

Đừng gọi constructor aspect trực tiếp. Với AspectJ singleton đã weave, `aspectOf()` được compiler/weaver hỗ trợ; **`javac` thông thường trên source `@Aspect` chưa weave không tạo ra method này**.

## 3. Lộ trình cơ bản: singleton aspect và Spring IoC

Với singleton aspect, quy trình nên là:

1. **Biên dịch/weave** aspect bằng AspectJ đúng phiên bản hỗ trợ JDK ứng dụng, cùng những class cần advice.
2. Khai báo **dependency bình thường** làm Spring bean, ví dụ `AuditSink`.
3. Đăng ký **bean đại diện aspect** dùng `factory-method="aspectOf"`, đặt property trỏ tới dependency bean.
4. Đảm bảo Spring đã cấu hình instance trước khi join point đầu tiên chạy; có thể dùng `depends-on` cho bean liên quan và tránh pointcut chặn giai đoạn bootstrap.
5. Truy cập business bean qua Spring context hoặc thậm chí tạo object ngoài context tùy yêu cầu weaving: sự có mặt của woven advice **không phụ thuộc** việc object được Spring proxy.

Một fragment XML thể hiện ý chính:

~~~xml
<bean id="auditSink" class="demo.AuditSink"/>

<bean id="auditAspect"
      class="demo.AuditAspect"
      factory-method="aspectOf">
    <property name="sink" ref="auditSink"/>
</bean>
~~~

`factory-method` giúp Spring nhận đúng instance do AspectJ tạo, còn setter `setSink(...)` thực hiện việc inject. Chỉ `<bean class="demo.AuditAspect">` không bảo đảm đúng instance và có thể thất bại vì AspectJ singleton aspect không được thiết kế để `new` bằng Spring.

**Lưu ý:** phương pháp này phụ thuộc AspectJ weaving. XML tự nó không làm `aspectOf()` xuất hiện, không weave class business, không tự bật `@EnableAspectJAutoProxy`.

## 4. Ví dụ Java 17+ hoàn chỉnh: biên dịch AspectJ và inject bằng Spring

Ví dụ dưới đây thực thi **AspectJ compile-time weaving**, rồi dùng `ClassPathXmlApplicationContext` để cấu hình một singleton aspect do AspectJ quản lý. Cách này tách riêng câu hỏi dependency injection khỏi cấu hình Java agent. Nó **không dùng Spring AOP auto-proxy**.

### 4.1. Cấu trúc dự án

~~~text
spring-managed-woven-aspect/
├── settings.gradle
├── build.gradle
└── src/main
    ├── java/demo
    │   ├── Demo.java
    │   ├── OrderService.java
    │   ├── AuditSink.java
    │   └── AuditAspect.java
    └── resources/applicationContext.xml
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-managed-woven-aspect'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'base'
}

repositories {
    mavenCentral()
}

configurations {
    ajcTools
    appLibs
}

dependencies {
    ajcTools 'org.aspectj:aspectjtools:1.9.22.1'
    ajcTools 'org.aspectj:aspectjweaver:1.9.22.1'

    appLibs 'org.aspectj:aspectjrt:1.9.22.1'
    appLibs 'org.springframework:spring-context:6.2.14'
}

def wovenDir = layout.buildDirectory.dir('woven')

tasks.register('weave', JavaExec) {
    classpath = configurations.ajcTools
    mainClass = 'org.aspectj.tools.ajc.Main'

    doFirst {
        wovenDir.get().asFile.mkdirs()
    }

    args '-17',
         '-classpath', configurations.appLibs.asPath,
         '-sourceroots', file('src/main/java').absolutePath,
         '-d', wovenDir.get().asFile.absolutePath,
         '-showWeaveInfo'
}

tasks.register('runDemo', JavaExec) {
    dependsOn 'weave'
    classpath = files(wovenDir, 'src/main/resources') + configurations.appLibs
    mainClass = 'demo.Demo'
}
~~~

Ở đây `ajcTools` dùng AspectJ compiler để xử lý source Java và weave các join point đã match. `appLibs` chứa **runtime**: `aspectjrt` và `spring-context` (Gradle kéo theo các module Spring cần thiết). Không có `@EnableAspectJAutoProxy` vì ví dụ dùng **AspectJ CTW**. `weave` tự biên dịch source, thay cho `javac` mặc định; chạy class từ thư mục `build/woven`.

`src/main/java/demo/AuditSink.java`:

~~~java
package demo;

public final class AuditSink {
    public void record(String orderId) {
        System.out.println("audit: " + orderId);
    }
}
~~~

`src/main/java/demo/OrderService.java`:

~~~java
package demo;

public final class OrderService {
    public void place(String orderId) {
        System.out.println("service: " + orderId);
    }
}
~~~

`src/main/java/demo/AuditAspect.java`:

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private AuditSink sink;

    // Spring IoC sẽ gọi setter trên instance lấy từ aspectOf().
    public void setSink(AuditSink sink) {
        this.sink = sink;
    }

    @Before("execution(* demo.OrderService.place(..))")
    public void beforePlace(JoinPoint joinPoint) {
        if (sink == null) {
            throw new IllegalStateException(
                "AspectJ aspect has not been configured by Spring"
            );
        }
        String orderId = (String) joinPoint.getArgs()[0];
        sink.record(orderId);
    }
}
~~~

`src/main/resources/applicationContext.xml`:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="auditSink" class="demo.AuditSink"/>

    <bean id="auditAspect"
          class="demo.AuditAspect"
          factory-method="aspectOf">
        <property name="sink" ref="auditSink"/>
    </bean>

    <bean id="orderService"
          class="demo.OrderService"
          depends-on="auditAspect"/>
</beans>
~~~

`src/main/java/demo/Demo.java`:

~~~java
package demo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public final class Demo {
    public static void main(String[] args) {
        try (var context =
                 new ClassPathXmlApplicationContext(
                     "applicationContext.xml")) {
            OrderService service =
                    context.getBean("orderService", OrderService.class);
            service.place("order-A");
        }
    }
}
~~~

Chạy JDK 17+ với Gradle phù hợp trong thư mục ví dụ:

~~~bash
gradle runDemo
~~~

Nếu đã có Gradle Wrapper, có thể dùng `./gradlew runDemo` trên macOS/Linux hoặc `gradlew.bat runDemo` trên Windows. **Stdout của chương trình** (bỏ log Gradle và thông tin `-showWeaveInfo` của AspectJ compiler):

~~~text
audit: order-A
service: order-A
~~~

### 4.2. Vì sao ví dụ hoạt động?

1. `ajc` đọc source có `@Aspect` và `@Before`, sinh hạ tầng AspectJ singleton (`aspectOf()`) và weave lời gọi advice vào `OrderService.place(...)`.
2. Khi Spring tạo context, nó khởi tạo `auditSink`, rồi tạo bean `auditAspect` **bằng cách hỏi `AuditAspect.aspectOf()`** thay vì gọi constructor của aspect.
3. Spring gọi `setSink(...)` trên **cùng instance** mà advice AspectJ sẽ sử dụng.
4. `orderService` có `depends-on="auditAspect"` để đảm bảo bean aspect được cấu hình trước khi service bean được khởi tạo hoặc phương thức service được gọi sau bootstrap.
5. `service.place("order-A")` đi qua woven join point; advice lấy `AuditSink` đã inject và ghi log **trước** thân method.
6. Không cần JDK dynamic proxy hay CGLIB. `OrderService` là `final` vẫn có thể được advice bởi CTW vì đây không phải subclass proxy.

**Điều kiện cần:** Build phải thực sự chạy `ajc` và sử dụng class đã weave. Nếu dùng `javac` đơn thuần, cấu hình `factory-method="aspectOf"` thường thất bại vì method do AspectJ sinh ra không có trong bytecode. Đây là một lỗi cấu hình thường gặp.

### 4.3. Kiểm tra bằng dấu hiệu gì?

- Log `-showWeaveInfo` của `ajc` nên xác nhận advice được weave vào method mong muốn; không chỉ dựa trên việc dependency tải thành công.
- Khi context khởi tạo, `auditAspect` phải là instance do `aspectOf()` trả về và property `sink` khác `null`.
- Lúc gọi `place`, output có dòng `audit` **trước** `service`.
- Kiểm thử thêm việc gọi một `new OrderService().place(...)` **sau khi Spring đã cấu hình aspect**. CTW vẫn có thể advice object đó vì bytecode của `OrderService` đã weave dù object không phải Spring bean. Tránh gọi trước context bootstrap, khi dependency chưa được inject.
- Kiểm thử `ApplicationContext` shutdown và tải lại nếu sử dụng hot-reload, tests song song hoặc nhiều context trong một classloader.

Ví dụ minh họa cách wiring và output mong đợi; khi chạy ứng dụng thật cần điều chỉnh Gradle, JDK, deployment, logging và weaving theo dependency graph cụ thể.

## 5. Java @Configuration thay cho XML: lấy đúng singleton aspect

Ý tưởng không phụ thuộc XML. Nếu compiler AspectJ đã sinh `aspectOf()`, có thể đưa **cùng instance** vào Spring IoC thông qua một `@Bean` trả về đối tượng do AspectJ runtime sở hữu. Dùng `org.aspectj.lang.Aspects.aspectOf(...)` giúp Java source có thể biên dịch dù method `aspectOf()` được AspectJ thêm vào bytecode về sau.

~~~java
// Fragment thay thế XML; giả định AuditAspect đã được weave,
// AuditSink đã có @Bean, ApplicationContext được khởi động.
@Configuration
public class AspectConfiguration {

    @Bean
    AuditSink auditSink() {
        return new AuditSink();
    }

    @Bean
    AuditAspect auditAspect(AuditSink sink) {
        AuditAspect aspect =
                org.aspectj.lang.Aspects.aspectOf(AuditAspect.class);
        aspect.setSink(sink);
        return aspect;
    }
}
~~~

Các import Spring cần thiết:

~~~java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
~~~

**Lưu ý triển khai:**

- `Aspects.aspectOf(AuditAspect.class)` thích hợp cho **singleton AspectJ aspect**; không áp dụng máy móc với `perthis`/`pertarget` có API `aspectOf` theo object liên quan.
- Không dùng `new AuditAspect()` trong `@Bean` để “bắt Spring quản lý aspect”: đó không phải instance được AspectJ runtime lựa chọn.
- Nếu Spring dùng `@Autowired` trên chính type aspect, injection chỉ có thể xảy ra khi **instance AspectJ** đi qua quá trình cấu hình bean/post-processor của Spring. Việc Spring có một bean khác cùng class không giúp instance đang execute advice.
- Bean aspect được tạo qua `@Bean` cần sẵn sàng trước những execution join point quan trọng; khi cần thiết, dùng `@DependsOn("auditAspect")` trên bean có liên quan và hạn chế pointcut khi bootstrap.

Đây là **fragment thay thế cách wiring**, không phải chương trình thứ hai hoàn chỉnh; ví dụ mục 4 mới là cấu trúc chạy độc lập.

## 6. Tại sao chỉ thêm @Autowired vào aspect thường không đủ?

Ví dụ chưa đầy đủ:

~~~java
@Aspect
public class BillingAspect {
    @Autowired
    private AuditSink auditSink;

    @Before("execution(* demo..*Service.*(..))")
    public void before() {
        auditSink.record("called");
    }
}
~~~

Spring có thể inject field vào bean mà Spring sở hữu. Nhưng với AspectJ CTW/LTW, **AspectJ** có trách nhiệm tạo instance aspect. Nếu instance không được đăng ký/cấu hình qua Spring IoC, `auditSink` có thể vẫn là `null`. Cần đảm bảo injection nhắm đúng instance và diễn ra đúng thời điểm.

Ba cách hiểu đúng:

1. **Spring AOP `@Aspect` bean**: aspect được tạo bằng `@Component` hoặc `@Bean`, `@EnableAspectJAutoProxy` xử lý bằng proxy. Đây không phải nhu cầu của bài này.
2. **AspectJ singleton**: aspect do AspectJ sở hữu; Spring lấy qua `aspectOf()` rồi inject bằng property setter, `@Autowired` trên bean đã đăng ký hoặc Java `@Bean` trả đúng instance.
3. **AspectJ per-instance**: AspectJ có thể tạo nhiều aspect instance; đăng ký một singleton bean qua `aspectOf()` không giải quyết vấn đề. Cần strategy cấu hình từng instance với weaving/configuration support của Spring, hoặc thay đổi thiết kế để giữ dependency ở một singleton collaborator có lifecycle rõ ràng.

**Không dùng static `ApplicationContext` hoặc `ApplicationContextHolder.getBean(...)` trong mọi advice làm giải pháp mặc định.** Cách đó là service locator toàn cục, dễ lỗi khi nhiều context/classloader, khó unit test và có thể tạo dependency vòng ở lúc khởi tạo.

## 7. AspectJ không phải singleton: perthis / pertarget và @Configurable

AspectJ có những mô hình `perthis`, `pertarget`, `percflow`... Khi instance aspect được liên kết với join point/object/flow, **không thể giả định có một `aspectOf()` không tham số trả ra singleton dùng chung**. Spring documentation chỉ rõ loại này phức tạp hơn; một hướng được Spring mô tả là đăng ký **prototype bean definitions** và dùng cơ chế `@Configurable` của `spring-aspects.jar` để cấu hình các instance do AspectJ tạo.

Điều kiện để dùng hướng `@Configurable`:

- Có dependency `org.springframework:spring-aspects` đúng dòng framework đang chạy.
- Có AspectJ weaving cho các type/constructor cần được cấu hình; chỉ có annotation không kích hoạt injection.
- Cấu hình `AnnotationBeanConfigurerAspect` trong Spring context, ví dụ `@EnableSpringConfigured`, để aspect này có tham chiếu tới `BeanFactory`.
- Xác định bean definition/prototype configuration phù hợp cho instance cần configure.
- Đảm bảo `AnnotationBeanConfigurerAspect` đã được Spring cấu hình **trước** khi AspectJ tạo đối tượng cần inject.
- Test lifecycle, số lượng aspect instance, thread/concurrency và cleanup thực tế.

Đây là **chiến lược nâng cao**, không phải đoạn code “chỉ cần dán `@Configurable` lên mọi aspect là xong”. Khi triển khai per-xxx aspect, hãy tra Javadoc/Reference của đúng Spring/AspectJ version và viết integration test cho thời điểm tạo instance thực tế.

### Đừng nhầm @Configurable cho domain object với aspect

Spring còn có một cơ chế phổ biến: gắn `@Configurable` lên object **được tạo ngoài container** (ví dụ domain object được tạo bằng `new`), rồi dùng `AnnotationBeanConfigurerAspect` đã weave để Spring inject vào object đó. Nó áp dụng cho **object không do Spring khởi tạo**, không phải cách đồng nhất mọi aspect vào Spring singleton.

Ví dụ cấu hình annotation (chỉ là một phần của setup):

~~~java
@Configuration
@EnableSpringConfigured
public class SpringConfiguredSetup {
}
~~~

Đoạn trên cần `org.springframework.context.annotation.Configuration` và `org.springframework.context.annotation.aspectj.EnableSpringConfigured`, `spring-aspects.jar` cùng thiết lập AspectJ weaving thực sự. Nếu object được tạo trước khi bean configurer aspect sẵn sàng, Spring có thể không inject nó và ghi debug log.

**Cảnh báo lifecycle:** Spring khuyên không bật `@Configurable` trên class đã được đăng ký là Spring bean bình thường, vì có thể khiến object bị initialize/configure hai lần (một lần theo container, một lần theo woven configurer). Khi cần inject woven aspect, quyết định rõ cái nào là **AspectJ-owned** và cái nào là **Spring-owned**.

## 8. Biên dịch thời gian build so với load-time weaving

Bài ví dụ chọn **CTW** để việc thực thi có thể quan sát được mà không cần JVM agent. Trong dự án khác, có thể dùng AspectJ **LTW**:

~~~text
CTW:
Java source + @Aspect → ajc → woven .class → chạy ứng dụng

LTW:
Java .class + META-INF/aop.xml
    → AspectJ weaver/agent lúc class load → woven behavior
    → Spring khởi tạo context, cấu hình aspect qua aspectOf()
~~~

| Chủ đề | CTW | LTW |
| --- | --- | --- |
| Lúc weave | Trong build, trước khi chạy JVM | Khi class được ClassLoader tải |
| Công cụ | `aspectjtools`/`ajc` | `aspectjweaver` + cấu hình agent/LTW thích hợp |
| Cấu hình AspectJ aspect | Annotation/aspect source trong build | AspectJ Weaver xác định aspects và target classes từ cấu hình weaving |
| Spring IoC config aspect | `factory-method="aspectOf"` cho singleton vẫn áp dụng | `factory-method="aspectOf"` cho singleton vẫn áp dụng, khi aspect đã được weaver xử lý đúng |
| Rủi ro triển khai | Chạy nhầm output `javac` chưa weave | Quên javaagent, class load trước weaver, `aop.xml` sai, ClassLoader khác |

Với Spring LTW, Spring cung cấp `@EnableLoadTimeWeaving`/`LoadTimeWeaver` integration và nhiều cấu hình ClassLoader, nhưng **chỉ bật annotation Spring chưa đồng nghĩa AspectJ đã weave đúng các type mong muốn**. Kiểm tra `META-INF/aop.xml`, `aspectjweaver.jar`, cơ chế agent/classloader và thời điểm load thực tế. `@EnableAspectJAutoProxy` không thay thế cho những bước đó.

### Tránh advice hai lần khi kết hợp Spring AOP và AspectJ

Một ứng dụng có thể vừa có aspect dùng AspectJ weaving, vừa có aspect Spring AOP proxy. Nếu **cùng một aspect** được cả AspectJ và Spring AOP áp dụng, cùng nghiệp vụ có thể bị chặn nhiều lần.

Spring Reference hướng dẫn với XML `<aop:aspectj-autoproxy/>` có thể dùng `<aop:include name="..."/>` để **giới hạn tập aspect bean** được Spring AOP đọc:

~~~xml
<aop:aspectj-autoproxy>
    <aop:include name="springProxyAspect"/>
</aop:aspectj-autoproxy>
~~~

Đây là **fragment XML** trong namespace `aop`, cần bổ sung namespace/schema hợp lệ cùng bean definitions thực tế. Tập aspect woven bằng AspectJ nên được loại trừ khỏi cấu hình proxy khi không muốn áp dụng hai lần.

Cách nghĩ đúng: `@Aspect` là **kiểu khai báo**, còn Spring AOP proxy và AspectJ weaving là **hai cơ chế runtime khác nhau**. Tập bean, điểm kích hoạt và loại join point phải được thiết kế riêng.

## 9. Thời điểm inject: bootstrap và vòng đời Spring context

Vấn đề khó phát hiện nhất thường không phải thiếu dependency, mà là **advice chạy trước khi Spring cấu hình xong aspect**.

Nếu một method bị weave chạy trong lúc Spring khởi tạo context, lời gọi advice có thể diễn ra khi `sink` còn `null`. Với singleton aspect ví dụ, `depends-on="auditAspect"` bảo đảm bean `orderService` chỉ khởi tạo sau bean aspect; thêm nữa pointcut chỉ match method nghiệp vụ `OrderService.place`, không match các method khởi tạo hạ tầng của Spring.

Nhưng `depends-on` **không phải rào chắn toàn JVM**. Nó không kiểm soát object được tạo ngoài context và gọi từ static initializer trước bootstrap, library khác chạm join point trước khi context sẵn sàng, hoặc một ApplicationContext thứ hai trong cùng ClassLoader.

Các quy tắc triển khai:

1. **Khởi tạo context trước khi phát sinh advised work.** Nếu code startup chủ động chạy `new Service().method()`, sắp xếp lại nơi khởi động.
2. **Thu hẹp pointcut** đến package/business method cần weave; tránh pointcut quá rộng chặn chính Spring/AspectJ infrastructure.
3. **Fail fast bằng thông báo rõ** nếu dependency bắt buộc chưa được cấu hình (như `IllegalStateException` trong ví dụ).
4. Nếu cần phụ thuộc chỉ lúc sử dụng, cân nhắc inject một collaborator/provider với contract rõ ràng; tránh lookup toàn cục ngầm.
5. Khi context đóng, xác định aspect có thể tiếp tục được AspectJ gọi không. AspectJ singleton có thể còn sống trong ClassLoader dù Spring context đã đóng; không được mặc định đồng bộ destruction lifecycle.
6. Nếu app chạy nhiều Spring `ApplicationContext`, kiểm tra instance nào đang cấu hình aspect và tránh ghi đè dependency giữa các context.

## 10. Một AspectJ singleton không đồng nghĩa một singleton trong mỗi Spring context

Theo Spring Reference, singleton aspect của AspectJ có phạm vi tương tự static member: **một instance theo ClassLoader định nghĩa type**, không phải một instance riêng cho mỗi `ApplicationContext`.

Hệ quả:

- Hai Spring contexts trong cùng ClassLoader có thể cùng tham chiếu một singleton aspect, nhưng mỗi context muốn inject một `AuditSink` khác nhau.
- Context khởi tạo sau có thể cấu hình lại cùng property của aspect; context trước vẫn còn các invocation kỳ vọng dependency ban đầu.
- Khi một context đóng, AspectJ runtime có thể vẫn giữ aspect singleton; không tự giả định mọi dependency đã được hủy theo đúng thứ tự.
- Unit test tạo nhiều contexts nối tiếp có thể lộ state giữa test, khác với Spring AOP aspect bean thực sự có scope singleton *trong từng context*.
- Sử dụng nhiều ClassLoaders có thể tạo nhiều singleton aspect khác nhau, tăng yêu cầu tổ chức wiring và weaving.

Nếu hệ thống cần **mỗi context một state aspect độc lập**, singleton AspectJ woven aspect chứa mutable dependency có thể không phù hợp. Nên cân nhắc collaborator tách biệt, chọn context/classloader architecture rõ ràng, hoặc dùng Spring AOP khi nhu cầu join point nằm trong khả năng proxy.

## 11. Lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân khả dĩ | Kiểm tra/cách sửa |
| --- | --- | --- |
| Spring báo không tìm thấy method `aspectOf` | Aspect class chưa được ajc/weaver xử lý, hoặc load nhầm class chưa weave | Kiểm tra output `ajc`, classpath/classloader thực tế; không dùng plain javac output |
| `@Autowired` ở aspect vẫn `null` | Spring chưa cấu hình instance do AspectJ runtime sở hữu | Đăng ký qua `aspectOf()`, inject vào instance ấy trước khi advice chạy |
| Context tạo được bean nhưng advice không chạy | Target chưa weave, pointcut không khớp hoặc đang chạy source/classpath sai | Xem `-showWeaveInfo`, LTW logs, test join point phù hợp |
| Advice chạy nhưng bean phụ thuộc `null` lúc startup | Method bị weave chạy trước context/config aspect | Sắp xếp bootstrap, `depends-on` phù hợp, hạn chế pointcut |
| Advice ghi hai dòng cho một method | Cùng aspect bị cả Spring AOP và AspectJ weave áp dụng | Phân tách tập aspects và cấu hình proxy bằng include/exclusion |
| App chạy Spring AOP, nghĩ đã có weaving | Chỉ bật `@EnableAspectJAutoProxy` | Dùng CTW/LTW thực sự nếu cần AspectJ-owned aspect |
| Khai báo `@Bean new AuditAspect()` làm sai injection | Spring tạo bản khác với AspectJ singleton | Lấy `aspectOf()` hoặc `Aspects.aspectOf(...)` |
| Hoạt động một context, lỗi với hai contexts | AspectJ singleton chia sẻ instance theo ClassLoader | Xem lại ownership, state, injection/reconfiguration |
| `perthis`/`pertarget` không cấu hình qua XML singleton | Instantiation model khác singleton, `aspectOf()` cần ngữ cảnh | Dùng strategy cho non-singleton (`@Configurable`/prototype khi phù hợp), test lifecycle |
| `@Configurable` không làm gì | Chưa weave và/hoặc chưa bật/config `AnnotationBeanConfigurerAspect` | Kiểm tra `spring-aspects`, `@EnableSpringConfigured`, weaving |

### Kiểm thử tối thiểu

- **Weaving:** có bằng chứng advice được weave, chẳng hạn log `-showWeaveInfo`/AspectJ weaver và invocation cụ thể.
- **Đúng instance:** instance mà Spring cấu hình và instance AspectJ runtime gọi là **một** trong singleton model.
- **Dependency:** `AuditSink` được inject trước lời gọi nghiệp vụ đầu tiên.
- **Không trùng advice:** một operation kích hoạt advice đúng số lần mong muốn.
- **Chạy ngoài proxy:** sau bootstrap, object thường được tạo bằng `new` vẫn nhận woven advice nếu class của nó đã weave.
- **Vòng đời:** đóng context, mở lại context, gọi startup sớm và kiểm tra hành vi phù hợp.
- **Nhiều context:** nếu có, test với context A/B có collaborators khác nhau.
- **Nâng version:** khóa cùng dòng Spring và AspectJ phù hợp JDK, chạy integration test CTW/LTW trên bản đích.

Một bài test chỉ assert Spring context có bean `auditAspect` là chưa đủ: nó không chứng minh advice thật sự được weave, dependency nằm trong instance chạy advice hoặc không có advice trùng từ Spring AOP.

## 12. Quy tắc ra quyết định

**Dùng Spring AOP thông thường** khi chỉ cần chặn lời gọi public method qua Spring bean proxy. Spring tạo aspect bean và inject như các Spring bean khác, ít thiết lập hơn.

**Dùng AspectJ weaving + `aspectOf()` + Spring IoC** khi cần cơ chế woven join point nhưng aspect singleton vẫn muốn sử dụng các collaborator Spring (logging, repository, service). Với mục tiêu này, chính **AspectJ tạo instance**, còn **Spring cấu hình nó**.

**Cân nhắc `@Configurable` và prototype bean configuration** cho AspectJ aspect không-singleton hoặc object ngoài Spring IoC, nhưng chỉ khi đã hiểu lifecycle, weaving và BeanFactory wiring; không coi annotation là giải pháp tự động ở mọi nơi.

**Ghi nhớ:** Mấu chốt không nằm ở việc “đặt `@Autowired` vào class `@Aspect`”, mà ở chỗ bảo đảm **đúng instance được AspectJ dùng để thực thi advice** nhận **đúng dependency do Spring quản lý** trước lúc invocation đầu tiên xảy ra.

## Nguồn tham khảo chính thức

- [Spring Framework 6.2 — Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/6.2/core/aop/using-aspectj.html) — mục *Configuring AspectJ Aspects by Using Spring IoC* và *Using AspectJ to Dependency Inject Domain Objects with Spring*.
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html) — phân biệt auto-proxy Spring AOP và AspectJ.
- [Spring Framework 6.2 — Spring AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html).
- [Spring Framework 6.2 — Load-time Weaving with AspectJ](https://docs.spring.io/spring-framework/reference/6.2/core/aop/using-aspectj.html) — các mục LTW, `META-INF/aop.xml`, agent và `LoadTimeWeaver`.
- [AspectJ Development Guide](https://eclipse.dev/aspectj/doc/latest/devguide/index.html) — compiler/weaver và các mô hình instantiation.

Khi triển khai thực tế, đối chiếu Javadoc và cấu hình của **đúng version Spring Framework, AspectJ và JDK** đang sử dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-integration-005">@Configurable và AnnotationBeanConfigurerAspect trong phạm vi tích hợp Spring</a>

<details>
<summary>Xem chi tiết</summary>

# @Configurable và AnnotationBeanConfigurerAspect trong tích hợp AspectJ với Spring

## 1. Bài toán thực tế

Spring IoC chỉ tự động tạo và cấu hình những bean mà container quản lý. Tuy nhiên ứng dụng có những **domain objects không phải Spring beans**: đối tượng được tạo trực tiếp bằng `new`, được framework persistence khôi phục, hoặc hình thành trong những lớp không do BeanFactory quản lý. Chúng vẫn có thể cần một cộng tác viên do Spring quản lý, ví dụ `TaxPolicy`, `RateProvider` hoặc `ValidationService`.

Một service Spring thường dùng constructor injection:

~~~java
@Service
public class OrderService {
    private final TaxPolicy policy;

    public OrderService(TaxPolicy policy) {
        this.policy = policy;
    }
}
~~~

Với object được tạo bằng `new DomainOrder()`, quá trình tạo **không đi qua Spring BeanFactory**. Gắn `@Autowired` lên field không tự nhiên biến object ấy thành Spring bean; `@EnableAspectJAutoProxy` cũng chỉ bật proxy-based Spring AOP cho Spring-managed beans phù hợp.

Spring cung cấp một cơ chế tích hợp **AspectJ weaving với Spring IoC** dành cho trường hợp này:

1. Gắn `@Configurable` lên **class của đối tượng được tạo ngoài container**.
2. AspectJ **weave** các class đó ở build-time hoặc load-time, để chèn logic cấu hình vào quá trình khởi tạo đối tượng.
3. `AnnotationBeanConfigurerAspect` thuộc thư viện `spring-aspects` nhận tín hiệu và nhờ Spring BeanFactory cấu hình instance.
4. `@EnableSpringConfigured` hoặc XML `<context:spring-configured/>` kết nối singleton AspectJ aspect với BeanFactory của Spring ApplicationContext.

Kết quả có thể là:

~~~java
DomainOrder order = new DomainOrder(); // Tự new, không gọi context.getBean.
int total = order.total(3);           // Collaborator do Spring inject, nếu weaving đã bật.
~~~

**Lưu ý:** đối tượng `order` trên **không trở thành Spring-managed bean hoàn chỉnh chỉ vì được cấu hình**. Spring đang áp dụng một phần khả năng inject/configure lên **object đã được khởi tạo ngoài container**, khác với Spring trực tiếp quản lý toàn bộ lifecycle/scope của object đó.

Bài này chỉ giải thích phần **tích hợp AspectJ và Spring**: annotation, Spring-provided aspect, cấu hình IoC, weaving và những giới hạn khi áp dụng vào ứng dụng thực tế. Không thay thế một khóa học AspectJ độc lập.

## 2. Bốn thành phần và điều kiện bắt buộc

| Thành phần | Package/module | Trách nhiệm |
| --- | --- | --- |
| `@Configurable` | `org.springframework.beans.factory.annotation.Configurable`, module `spring-beans` | Đánh dấu type đủ điều kiện được Spring cấu hình, mang metadata `value`, `autowire`, `dependencyCheck`, `preConstruction` |
| `AnnotationBeanConfigurerAspect` | `org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect`, artifact `spring-aspects` | AspectJ aspect bắt sự kiện khởi tạo object thích hợp và gọi BeanFactory cấu hình object |
| `@EnableSpringConfigured` | `org.springframework.context.annotation.aspectj.EnableSpringConfigured` | Đăng ký cấu hình aspect vào Spring context, cho aspect tham chiếu BeanFactory |
| AspectJ compiler/weaver | `org.aspectj:aspectjweaver` và hạ tầng CTW/LTW tương ứng | Biến đổi bytecode để advice của AspectJ aspect thật sự được thi hành trên những object tạo bằng `new` |

Các thành phần **không thay thế cho nhau**:

- Chỉ có `@Configurable` → **không có hiệu ứng injection**.
- Có `@Configurable` và `@EnableSpringConfigured`, nhưng **không weaving** → annotation vẫn không được áp dụng lên `new DomainOrder()`.
- Có weaving và `spring-aspects`, nhưng aspect chưa nhận BeanFactory → không thể cấu hình object theo Spring context thích hợp; có thể thấy log cảnh báo.
- Có đủ annotation, aspect, weaving và context nhưng bean definition hoặc dependency cấu hình sai → creation/configuration có thể thất bại hoặc collaborator vẫn null.

**Khác biệt bản chất:** `AnnotationBeanConfigurerAspect` là **AspectJ aspect** dùng bytecode weaving, không phải chỉ một method interceptor Spring AOP tạo từ `@Aspect` trên Spring proxy. Việc bật `@EnableAspectJAutoProxy` không phải cách bật `@Configurable` trên object `new`.

## 3. Luồng thực thi trong ứng dụng Spring

~~~text
Application startup
  |
  +-- Spring tạo ApplicationContext
  |     +-- @EnableSpringConfigured / <context:spring-configured/>
  |     +-- đăng ký/cấu hình AnnotationBeanConfigurerAspect với BeanFactory
  |     +-- đăng ký các collaborators và bean definition template (nếu dùng)
  |
  +-- AspectJ load-time hoặc compile-time weaving đã kích hoạt
        |
Application code:
  new DomainOrder()
        |
        v
  Class DomainOrder có bytecode được weave cho @Configurable
        |
        +-- Hook AspectJ của AnnotationBeanConfigurerAspect
              |
              +-- resolve BeanWiringInfo từ @Configurable
              +-- nhờ BeanFactory cấu hình existing instance
              |      +-- resolve bean definition template hoặc autowire
              |      +-- inject thuộc tính/field/method phù hợp
              v
        DomainOrder có dependency
              |
              v
        order.total(...) dùng Spring-managed collaborator
~~~

Sơ đồ mô tả **cơ chế tích hợp**, không khẳng định target object trở thành proxy Spring, cũng không bảo đảm các callback/lifecycle của một bean hoàn chỉnh giống như khi Spring sở hữu đối tượng từ lúc tạo.

Mốc **trước hay sau constructor body** còn phụ thuộc `@Configurable(preConstruction = ...)`, được giải thích ở phần sau.

## 4. Các thuộc tính của @Configurable

`@Configurable` áp dụng cho **type**, có runtime retention và được kế thừa theo Java `@Inherited`. Các option chính:

| Option | Default | Ý nghĩa trong tích hợp Spring |
| --- | --- | --- |
| `value` | `""` | Tên bean definition dùng làm **template cấu hình** existing object. Mặc định có thể sử dụng FQCN của annotated class |
| `autowire` | `Autowire.NO` | Yêu cầu autowiring theo `BY_TYPE` hoặc `BY_NAME` nếu dùng cơ chế đó |
| `dependencyCheck` | `false` | Kiểm tra các object-reference properties sau khi wiring theo cơ chế được chọn; **không phải** validation nghiệp vụ toàn diện |
| `preConstruction` | `false` | Khi `true`, cho phép Spring inject trước constructor body của object đã được AspectJ weave |

Ví dụ lấy bean template tên tường minh:

~~~java
import org.springframework.beans.factory.annotation.Configurable;

@Configurable("domainOrder")
public class DomainOrder {
    // Spring tìm bean definition template tên "domainOrder".
}
~~~

Ví dụ yêu cầu autowiring theo type:

~~~java
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Configurable;

@Configurable(autowire = Autowire.BY_TYPE)
public class DomainOrder {
    // Có thể inject qua JavaBean setter theo type.
}
~~~

Ví dụ muốn dependencies có trước khi chạy constructor body:

~~~java
@Configurable(value = "domainOrder", preConstruction = true)
public class DomainOrder {
    // Cần AspectJ weaving có hỗ trợ pre-construction semantics.
}
~~~

Các đoạn trên là **biến thể**, không đặt nhiều khai báo `@Configurable` lên cùng một class. Đừng kỳ vọng `preConstruction=true` hoạt động khi chỉ có proxy Spring AOP: constructor không phải join point được Spring proxy intercept.

### 4.1. Cách chọn bean definition template

Với `@Configurable` không đặt tên, Spring có thể dùng **tên đầy đủ của type** làm tên bean definition template. Nếu khai báo bằng XML:

~~~xml
<bean class="demo.domain.DomainOrder"
      scope="prototype">
    <property name="policy" ref="pricingPolicy"/>
</bean>
~~~

Trong cấu hình XML Spring thông thường, thiếu `id` cho phép sử dụng bean class name mặc định theo cơ chế đặt tên. Điều quan trọng là **tên template thực tế phải khớp metadata mà aspect tìm kiếm**. Việc đặt `scope="prototype"` thường hợp lý cho template dùng cấu hình object riêng, tránh sự nhầm lẫn với một singleton business bean dùng chung.

Một cách dễ đọc hơn là đặt tên tường minh:

~~~java
@Configurable("domainOrder")
public class DomainOrder {
    // ...
}
~~~

~~~xml
<bean id="domainOrder"
      class="demo.domain.DomainOrder"
      scope="prototype">
    <property name="policy" ref="pricingPolicy"/>
</bean>
~~~

**Cảnh báo quan trọng:** object `new DomainOrder()` là object do code gọi `new` tạo ra; Spring dùng **template bean definition** để cấu hình instance đó, không có nghĩa Spring tự tạo ra một đối tượng domain mới rồi thay thế tham chiếu đang có.

### 4.2. autowire, @Autowired và bean template

Nếu dùng `autowire = Autowire.BY_TYPE`, Spring có thể tìm collaborator phù hợp qua setters. `BY_NAME` dựa trên property names.

Spring documentation cũng khuyên nếu có yêu cầu dependency rõ ràng, nên khai báo `@Autowired` hoặc `@Inject` ở field/method khi phù hợp với cấu hình post-processors của BeanFactory. Không đánh đồng `@Autowired` với constructor injection được framework tự áp vào một đối tượng mà **caller đã tự tạo**: constructor vẫn do Java caller chọn và gọi. Cơ chế @Configurable cấu hình một existing object, không gọi lại constructor để tạo object khác theo autowiring.

Các dependency có nhiều candidates phải dùng qualifier, bean name hoặc thiết kế contract rõ ràng; annotation này không tự giải quyết ambiguity. `dependencyCheck=true` kiểm tra thuộc tính tham chiếu theo contract của Spring bean wiring, không thay thế validation như `@NotNull` hoặc kiểm tra domain invariant.

## 5. Sau constructor hay trước constructor?

Mặc định `preConstruction=false`. Theo tài liệu Spring, aspect cấu hình object **sau khi constructor hoàn thành**. Vì vậy:

~~~java
@Configurable("domainOrder")
public class DomainOrder {
    private PricingPolicy policy;

    public DomainOrder() {
        // Với cấu hình mặc định, policy có thể vẫn null ở đây.
    }

    public void setPolicy(PricingPolicy policy) {
        this.policy = policy;
    }
}
~~~

Một lời gọi business method **sau khi `new DomainOrder()` trả về** có thể dùng dependency đã được Spring inject, nhưng **không** sử dụng dependency trong constructor body khi chưa có `preConstruction=true`.

Nếu constructor nhất thiết cần injected dependency:

~~~java
@Configurable(value = "domainOrder", preConstruction = true)
public class DomainOrder {
    // AspectJ weaving có thể cấu hình trước constructor body.
}
~~~

Cấu hình này không phải lúc nào cũng là thiết kế tối ưu: tạo một object domain bằng `new` mà constructor dựa vào context toàn cục khiến unit test và thời điểm Spring initialization khó đoán. Nếu caller có thể nhận dependency một cách tường minh, **constructor injection thông thường** vào một factory/service Spring-managed thường dễ hiểu và kiểm thử hơn.

Với class hierarchy, subclass constructors và initialization order, cần test trên **kiểu đã được weave thực tế**; không suy luận rằng mọi field initializer hoặc mọi constructor overload đều có cùng thời điểm quan sát.

## 6. Ví dụ hoàn chỉnh: new DomainOrder() vẫn nhận Spring dependency

Ví dụ có code Java 17, Spring Framework 6.1.15 và AspectJ weaver 1.9.22.1. Để đơn giản và không phụ thuộc Spring AOP proxy, ta dùng:

- `DomainOrder` là POJO domain được tạo bằng `new`, không mang `@Component`.
- `PricingPolicy` là Spring bean thông thường, có method `total(int)`.
- XML bean definition `domainOrder` đóng vai trò **wiring template**.
- `@EnableSpringConfigured` đăng ký/cấu hình AspectJ `AnnotationBeanConfigurerAspect` với Spring context.
- `spring-aspects` và `aspectjweaver` trên classpath; JVM dùng **`-javaagent`** để load-time weave `DomainOrder`.

### 6.1. Cấu trúc dự án

~~~text
spring-configurable-demo/
├── pom.xml
└── src/main/
    ├── java/demo/
    │   ├── App.java
    │   ├── PricingPolicy.java
    │   └── domain/DomainOrder.java
    └── resources/domain.xml
~~~

Trong demo dùng **aop.xml có sẵn của artifact `spring-aspects`**, nó đã khai báo `AnnotationBeanConfigurerAspect`. Vì vậy ứng dụng không cần copy/paste thêm một `META-INF/aop.xml` trùng khai báo aspect. Với dự án lớn, có thể cung cấp `META-INF/aop.xml` bổ sung để giới hạn packages được weave; khi làm vậy phải hiểu cơ chế merge nhiều aop.xml của AspectJ.

### 6.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-configurable-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.1.15</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aspects</artifactId>
            <version>6.1.15</version>
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
                <configuration>
                    <release>17</release>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <version>3.6.1</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` mang ApplicationContext và annotation-based configuration. `spring-aspects` mang AspectJ aspect thực tế (không chỉ API `@Configurable`). `aspectjweaver` cung cấp Java agent để weave class khi nạp. `maven-dependency-plugin` được dùng để copy dependency jars vào `target/dependency` cho lệnh chạy thuần Java.

Nếu thiếu `spring-aspects`, `AnnotationBeanConfigurerAspect` sẽ không khả dụng. Nếu thiếu Java agent trong ví dụ này, chỉ thêm các dependency **không đủ** để `new DomainOrder()` được inject.

### 6.3. Spring collaborator

**src/main/java/demo/PricingPolicy.java**

~~~java
package demo;

public class PricingPolicy {
    public int total(int units) {
        return units * 10;
    }
}
~~~

Class này là business collaborator đơn giản, không có state mutable. Spring sẽ đăng ký nó qua `@Bean`.

### 6.4. Domain object được tạo ngoài Spring

**src/main/java/demo/domain/DomainOrder.java**

~~~java
package demo.domain;

import demo.PricingPolicy;
import org.springframework.beans.factory.annotation.Configurable;

@Configurable("domainOrder")
public class DomainOrder {

    private PricingPolicy policy;

    public DomainOrder() {
        System.out.println("constructorPolicyPresent=" + (policy != null));
    }

    public void setPolicy(PricingPolicy policy) {
        this.policy = policy;
    }

    public boolean hasPolicy() {
        return policy != null;
    }

    public int total(int units) {
        if (policy == null) {
            throw new IllegalStateException("DomainOrder was not configured");
        }
        return policy.total(units);
    }
}
~~~

Đây là **class không có `@Component` và không được tiêm trực tiếp vào một bean Spring thông qua constructor**. Caller sẽ tự `new` nó. `@Configurable("domainOrder")` cho aspect biết tên template cần sử dụng. Setter `setPolicy` cho phép Spring BeanFactory áp dụng thuộc tính `policy`.

Trong cấu hình mặc định, dòng `constructorPolicyPresent=false` là điều dự kiến: aspect cấu hình object sau khi constructor body chạy. Ngay sau khi `new DomainOrder()` trả về, `hasPolicy()` trở thành true nếu weaving và BeanFactory wiring đã được thiết lập.

### 6.5. Bean definition template

**src/main/resources/domain.xml**

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <bean id="domainOrder" class="demo.domain.DomainOrder"
          scope="prototype">
        <property name="policy" ref="pricingPolicy"/>
    </bean>
</beans>
~~~

Đây là **template**, không phải một lời gọi `context.getBean("domainOrder")` trong luồng chính. `scope="prototype"` giúp tránh eager singleton creation, đồng thời tạo metadata cấu hình cho object hiện hữu. Nếu tự lấy bean `domainOrder` từ container thay vì `new`, Spring có thể khởi tạo một instance khác và khiến việc nhận diện double initialization cần được kiểm tra riêng. Để minh họa đúng cơ chế, ví dụ chỉ `new DomainOrder()`.

### 6.6. Context và chương trình main

**src/main/java/demo/App.java**

~~~java
package demo;

import demo.domain.DomainOrder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.aspectj.EnableSpringConfigured;

@Configuration
@EnableSpringConfigured
@ImportResource("classpath:domain.xml")
public class App {

    @Bean
    public PricingPolicy pricingPolicy() {
        return new PricingPolicy();
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(App.class)) {

            // Context đã refresh: aspect có BeanFactory trước khi new object.
            DomainOrder order = new DomainOrder();
            System.out.println("configuredAfterNew=" + order.hasPolicy());
            System.out.println("total=" + order.total(3));
        }
    }
}
~~~

`@EnableSpringConfigured` ở đây khác `@EnableAspectJAutoProxy`: nó nhập configuration để Spring đăng ký và cấp BeanFactory cho **AspectJ singleton aspect** được tạo theo `aspectOf()`. Aspect vẫn cần weaving agent riêng để xử lý hành vi `new DomainOrder()`.

### 6.7. Chạy có AspectJ load-time weaving

Từ thư mục chứa `pom.xml`, tạo classes và copy runtime dependencies:

~~~bash
mvn -q package dependency:copy-dependencies -DincludeScope=runtime
~~~

**Windows PowerShell / cmd.exe:**

~~~powershell
java -javaagent:target/dependency/aspectjweaver-1.9.22.1.jar -cp "target/classes;target/dependency/*" demo.App
~~~

**Linux/macOS:**

~~~bash
java -javaagent:target/dependency/aspectjweaver-1.9.22.1.jar -cp "target/classes:target/dependency/*" demo.App
~~~

Lưu ý dấu phân tách classpath: Windows dùng `;`, Unix-like dùng `:`. Java agent phải được chỉ định **từ lúc JVM khởi động, trước khi class domain được load**. Các jars trong `target/dependency/*` bao gồm Spring, spring-aspects và AspectJ weaver; artifact `spring-aspects` có sẵn file `META-INF/aop.xml` đăng ký aspect.

**Các dòng `System.out` dự kiến khi cấu hình đúng:**

~~~text
constructorPolicyPresent=false
configuredAfterNew=true
total=30
~~~

Có thể xuất hiện thêm log của Spring, AspectJ weaver hoặc JVM tùy môi trường. Ba dòng trên là kết quả nghiệp vụ dự kiến, không phải khẳng định toàn bộ stdout/stderr chỉ có đúng ba dòng.

**Lý giải:**

1. `AnnotationConfigApplicationContext(App.class)` đăng ký pricingPolicy bean, XML template và Spring-configured AspectJ aspect.
2. Khi code `new DomainOrder()`, bytecode đã được AspectJ weave. Constructor chạy trước injection vì `preConstruction` mặc định false; in `constructorPolicyPresent=false`.
3. Aspect nhận hook sau initialization và nhờ BeanFactory tìm template `domainOrder`, áp dụng property `policy` cho instance vừa tạo.
4. Sau khi toán tử `new` trả về, `order.hasPolicy()` true.
5. `order.total(3)` gọi `PricingPolicy.total`: `3 * 10 = 30`.
6. Không cần `context.getBean(DomainOrder.class)` và không tạo một Spring AOP proxy cho `DomainOrder`.

**Đối chứng thiếu weaving:** nếu chạy `java -cp "target/classes;target/dependency/*" demo.App` **không có `-javaagent`**, hoặc nếu DomainOrder class đã được JVM load trước khi weaver gắn vào, `@Configurable` tự nó không làm setter chạy. Chương trình có thể in `configuredAfterNew=false` và ném `IllegalStateException` khi `total(3)`. Đây là lỗi tích hợp mong đợi, không phải chứng cứ Spring IoC hoạt động sai.

## 7. Trường hợp dùng @Autowired trên object được weave

Bên cạnh bean template, Spring hỗ trợ annotation-driven dependency injection vào những `@Configurable` objects đã được weave. Một biến thể:

~~~java
package demo.domain;

import demo.PricingPolicy;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Autowired;

@Configurable
public class DomainOrder {

    @Autowired
    private PricingPolicy policy;

    public int total(int units) {
        return policy.total(units);
    }
}
~~~

Đây là **class thay thế**, không biên dịch đồng thời với `DomainOrder` ở mục 6 cùng package/tên. Nó giả định `AutowiredAnnotationBeanPostProcessor` hoặc hạ tầng annotation config tương ứng hiện diện trong BeanFactory và có duy nhất collaborator matching. Trong một Spring annotation-configured ApplicationContext thông thường, các bean post-processors cần thiết được đăng ký; cần xác minh integration test trong chính cấu hình ứng dụng.

Với biến thể này, có thể không cần template XML `domainOrder` nếu wiring được xác định hoàn toàn qua autowire metadata, tùy cấu hình resolver. Đừng vừa bỏ bean template vừa để `@Configurable("domainOrder")` trỏ đến một ID không tồn tại mà không kiểm tra lựa chọn wiring — ví dụ mục 6 chọn template rõ ràng để tránh ambiguity.

Có thể viết:

~~~java
@Configurable(autowire = Autowire.BY_TYPE)
public class DomainOrder {
    private PricingPolicy policy;

    public void setPolicy(PricingPolicy policy) {
        this.policy = policy;
    }
}
~~~

Đây là một **biến thể khác**, cần import `org.springframework.beans.factory.annotation.Autowire`. `Autowire.BY_TYPE` dựa trên JavaBean property type, không phải lời hứa sẽ tự gọi constructor theo type. `@Autowired` trên constructor do người dùng tự gọi vẫn là câu chuyện khác với injection vào existing object.

## 8. Cách bật aspect bằng XML trong Spring

Thay vì `@EnableSpringConfigured`, Spring `context` XML namespace hỗ trợ:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
         http://www.springframework.org/schema/beans
         https://www.springframework.org/schema/beans/spring-beans.xsd
         http://www.springframework.org/schema/context
         https://www.springframework.org/schema/context/spring-context.xsd">

    <context:spring-configured/>

    <bean id="pricingPolicy" class="demo.PricingPolicy"/>

    <bean id="domainOrder"
          class="demo.domain.DomainOrder"
          scope="prototype">
        <property name="policy" ref="pricingPolicy"/>
    </bean>
</beans>
~~~

Đây là **cấu hình thay thế** cho Java configuration ở ví dụ chính: không đăng ký cả `@EnableSpringConfigured` và `<context:spring-configured/>` hai lần trong cùng một context mà không có lý do. Để chạy với XML, main có thể tạo `ClassPathXmlApplicationContext`, sau khi context refresh thì `new DomainOrder()`.

**Điểm không được bỏ:** `<context:spring-configured/>` chỉ giúp cấu hình aspect bằng Spring IoC. Bạn vẫn cần `spring-aspects` cùng AspectJ weaving **như trước**. Nó không tương đương `<aop:aspectj-autoproxy/>`, vốn phục vụ Spring proxy-based `@Aspect` advice trên Spring-managed beans.

## 9. Phân biệt với Spring AOP proxy và @EnableLoadTimeWeaving

| Cơ chế | Đối tượng áp dụng | Cơ chế interception | Có tự làm `new DomainOrder()` được inject không? |
| --- | --- | --- | --- |
| `@EnableAspectJAutoProxy` + `@Aspect` | Spring-managed bean proxies | Spring AOP method proxy | Không |
| `ProxyFactory` / `ProxyFactoryBean` | Proxy được tạo/chọn tường minh | Spring AOP method proxy | Không |
| `@Configurable` đơn lẻ | Metadata type | Chưa có aspect/weaving | Không |
| `@EnableSpringConfigured` đơn lẻ | Khởi tạo/cấu hình aspect với IoC | Chưa đủ interception nếu không weave | Không |
| `@Configurable` + `AnnotationBeanConfigurerAspect` + AspectJ weaving + BeanFactory | Domain object ngoài container | AspectJ woven initialization join point | Có, khi bean wiring hợp lệ |
| `@EnableLoadTimeWeaving` | Spring LTW infrastructure | Đăng ký `LoadTimeWeaver` trên loader hỗ trợ | Chỉ là một phần cơ chế; không tự cung cấp aspect hoặc bean configuration |

Spring có thể hỗ trợ load-time weaving qua `@EnableLoadTimeWeaving`, `<context:load-time-weaver/>` hoặc instrumentation agent. Trong ví dụ mục 6 **dùng trực tiếp AspectJ agent** ngay khi JVM khởi động, nên không cần thêm `@EnableLoadTimeWeaving` chỉ để minh họa cơ chế này.

Cần phân biệt `spring-instrument` Java agent (hỗ trợ Spring class loading/instrumentation khi dùng một số cơ chế LTW) với **`aspectjweaver` agent** thực hiện AspectJ weaving. Không thêm hai agents tùy tiện nếu một agent AspectJ đã đủ cho cách chạy đã chọn. Đối với servlet containers, custom class loaders, JPMS và executable jars, khả năng weaving còn phụ thuộc classloader có được instrument trước khi type load và cách classpath được bố trí.

## 10. AspectJ compile-time weaving và load-time weaving

Để `AnnotationBeanConfigurerAspect` có thể intercept khởi tạo objects, class được đánh dấu `@Configurable` **phải được AspectJ weave**:

- **Compile-time weaving (CTW):** AspectJ compiler hoặc build plugin áp dụng aspect vào bytecode trong quá trình build. Khi chạy, runtime vẫn phải có dependencies phù hợp để code woven tham chiếu aspect.
- **Load-time weaving (LTW):** Java agent hoặc weaver phù hợp biến đổi class bytecode **lúc JVM load class**. Không thể kỳ vọng agent gắn sau khi một class đã được load sẽ tự sửa tất cả classes trước đó trong demo.
- **Spring proxy-based AOP:** intercept qua proxy; không thể tự capture constructor/initialization join point của mọi object `new`.

Trong demo chọn LTW để giữ Java compiler chuẩn và nhìn rõ yêu cầu JVM agent. Khi triển khai production, cần cân nhắc build reproducibility, startup overhead, instrumentation permissions và khả năng debug bytecode woven.

Với `spring-aspects`, file `META-INF/aop.xml` đi kèm jar đã khai báo `AnnotationBeanConfigurerAspect` cùng một số Spring-provided aspects khác. Nếu tạo thêm `aop.xml`, không khai báo trùng aspect và xem kỹ loader/package include filters; một file weaving config không tự tạo BeanFactory hoặc template wiring.

## 11. Không gắn @Configurable vào Spring bean thông thường

Spring documentation cảnh báo rõ: nếu một class vừa được Spring container tạo/quản lý như bean, vừa được `@Configurable` aspect cấu hình lúc khởi tạo, có thể xuất hiện **double initialization/configuration**.

Ví dụ phản diện:

~~~java
@Configurable
@Component
public class AccountService {
    @Autowired
    private PricingPolicy policy;
}
~~~

Với class trên, cả BeanFactory và woven `AnnotationBeanConfigurerAspect` có thể tham gia inject/configure cùng instance trong những thời điểm khác nhau. Các biểu hiện có thể gồm setter/callback chạy trùng, state bị gán lại, phụ thuộc circular hoặc lifecycle khó kiểm soát. **Không** áp dụng `@Configurable` lên các `@Service`, `@Component` hay bean class được container trực tiếp tạo theo cách thông thường.

Nên chọn một chủ sở hữu:

- **Spring-managed service:** dùng `@Component`/`@Service`, constructor injection và Spring lifecycle.
- **Object ngoài container:** nếu thực sự không thể thay đổi cách tạo đối tượng, cân nhắc `@Configurable` + AspectJ weaving + Spring BeanFactory.
- **Object domain thuần:** nếu có thể truyền dependency ngay từ đầu, thường đơn giản hơn khi dùng Spring-managed factory/service tạo domain object bằng constructor arguments.

Template bean definition cho `@Configurable` tồn tại để hướng dẫn cấu hình, không có nghĩa ứng dụng nên inject/calls nó như một singleton business bean. Hạn chế nhầm raw target, context-owned bean và object có weaving.

## 12. Timing và ApplicationContext phải sẵn sàng

`AnnotationBeanConfigurerAspect` không tự biết nên lấy BeanFactory nào nếu chưa được Spring cấu hình. Nếu tạo `@Configurable` object **trước khi ApplicationContext đã khởi tạo và register aspect**, weaving có thể bắt sự kiện nhưng không có BeanFactory phù hợp để inject. Spring mô tả trường hợp này với debug log và object **không được cấu hình**.

Các điểm cần kiểm tra:

1. Đảm bảo Spring context đã refresh và cấu hình aspect **trước** khi code tạo domain object cần inject.
2. Đặt domain-object creation sau việc khởi tạo Spring configuration trong luồng bootstrap.
3. Nếu một bean khởi tạo sớm tạo domain objects trong init callback, dùng dependency ordering có chủ đích; Spring XML có thể đặt `depends-on` trỏ tới aspect bean thích hợp.
4. Không trông đợi annotation tự xử lý các domain objects được tạo từ static initializer quá sớm, trước khi aspect biết BeanFactory.
5. Kiểm tra object được tạo bởi ORM/deserialization có thực sự chạy trên **class đã được weave**; cơ chế tạo đối tượng có thể thay đổi lifecycle.
6. Với unit tests dùng `new` ngoài Spring, object có thể không được inject nếu chưa bật aspect hoặc chưa có context, đó là hành vi cần được xử lý bằng test fixtures rõ ràng.

Một ví dụ `depends-on` từ tài liệu Spring dùng bean name của configurer aspect:

~~~xml
<bean id="earlyCreator"
      class="demo.EarlyCreator"
      depends-on="org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect"/>
~~~

Đây là **snippet chỉ minh họa dependency ordering**, không phải toàn bộ XML file chạy độc lập: tên bean configurer phải tương ứng với cách Spring/context version đăng ký, và implementation `EarlyCreator` cần tồn tại. Khi dùng Java configuration, thứ tự của `@Bean`/ApplicationContext và cách lấy aspect vẫn phải kiểm tra qua integration test; không giải quyết bằng cách gọi `aspectOf()` thủ công trong business code.

## 13. Nhiều ApplicationContext và ClassLoader

`AnnotationBeanConfigurerAspect` có AspectJ **singleton instantiation semantics theo ClassLoader**. Nếu nhiều Spring ApplicationContexts được tạo trong cùng classloader, aspect có thể chỉ giữ **một BeanFactory reference** tại một thời điểm. Đây là vấn đề quan trọng với parent/child context, test suites tạo/đóng contexts, nhiều ứng dụng servlet chia sẻ libraries hoặc plugin architectures.

Ví dụ:

~~~text
JVM / classloader
   |
   +-- Shared/parent ApplicationContext
   |       +-- PricingPolicy
   |       +-- AnnotationBeanConfigurerAspect được cấu hình ở đây
   |
   +-- Servlet child context A
   +-- Servlet child context B

new DomainOrder() -> AspectJ singleton dùng BeanFactory của aspect
                      (không tự chọn child context theo request).
~~~

Spring thường khuyên đặt cấu hình aspect trong **shared parent context** nếu collaborator cần inject là các shared services, thay vì đặt trong nhiều child contexts độc lập. Nếu hai web applications không nên chia sẻ aspect state, phải đảm bảo `spring-aspects` được load qua các classloaders tách biệt tương ứng. Đặt `spring-aspects` ở classloader dùng chung của servlet container có thể làm các webapps share singleton aspect ngoài ý muốn.

Đừng suy luận rằng Spring AOP proxy trong mỗi child context tự giải quyết được phạm vi của một **AspectJ singleton aspect**: đây là hai cơ chế khác nhau. Các hệ thống hot-reload và tests song song cần quản lý context/classloader lifecycle rất cẩn thận.

## 14. Khi test thì kiểm tra điều gì?

Một integration test tốt cần phân biệt **cấu hình có hiệu lực** và **cơ chế đang bị bypass**.

| Kịch bản | Kết quả kỳ vọng | Điều cần xác minh |
| --- | --- | --- |
| Weaving agent bật, context đã refresh, template đúng | `new DomainOrder().hasPolicy()` true | Object được cấu hình sau constructor |
| Bỏ `-javaagent` | `hasPolicy()` false, khi dùng dependency có thể lỗi | Annotation không tự thực thi |
| Bỏ `@EnableSpringConfigured` | Weaving có thể diễn ra nhưng không có BeanFactory để cấu hình | Aspect chưa nhận container reference |
| Thiếu `spring-aspects` | Aspect không thể hoạt động | Dependency/classpath có đủ artifact không |
| `@Configurable("unknownName")` | Spring không tìm được template hoặc wiring sai | Metadata và bean definition names |
| Tạo object trước context | Không nhận collaborator theo kỳ vọng | Bootstrap timing/order |
| `preConstruction=false` | Constructor chưa có dependency | Không sử dụng collaborator ngay trong constructor |
| `preConstruction=true` | Dependency có thể sẵn trong constructor body nếu weaving đúng | Test constructor semantics thật |
| Class vừa `@Component` vừa `@Configurable` | Nguy cơ cấu hình/lifecycle hai lần | Chỉ chọn một cơ chế quản lý |
| Có nhiều contexts chung ClassLoader | Aspect có thể trỏ BeanFactory không mong muốn | Parent/child context placement, teardown |

Kiểm thử chính xác phải dùng **process được chạy với/không với agent**; unit test chạy trên một JVM đã weave class có thể bị ảnh hưởng bởi state của aspect/context trước đó. Không dùng `AopUtils.isAopProxy(order)` để kết luận `@Configurable` có hoạt động không: object được cấu hình bằng AspectJ weaving **không nhất thiết là Spring AOP proxy**.

### Assertions mẫu

~~~java
DomainOrder order = new DomainOrder();
assertTrue(order.hasPolicy());
assertEquals(30, order.total(3));
~~~

Đây là snippet JUnit 5, cần imports `org.junit.jupiter.api.Assertions.assertTrue/assertEquals`, test runner JVM phải bật AspectJ weaving và Spring context đã cấu hình. Nó không phải đơn vị test độc lập nếu thiếu test configuration.

### Chẩn đoán bytecode weaving

Với AspectJ LTW, có thể bật thông tin weave trong `META-INF/aop.xml` hoặc tùy chọn weaver phù hợp, chẳng hạn `-showWeaveInfo`, để xem class `DomainOrder` đã được transform. Trên application server, hạn chế bật verbose weaving logs ở production nếu không cần thiết. Nếu cần kiểm tra sâu hơn, so classloader đã load `DomainOrder`, agent startup flags, Spring BeanFactory readiness và các bean definitions đúng tên.

## 15. Lỗi phổ biến và cách sửa

| Triệu chứng | Nguyên nhân có thể | Cách xử lý |
| --- | --- | --- |
| `new DomainOrder()` tạo object có collaborator null | Chỉ thêm `@Configurable`, chưa chạy AspectJ weaving | Kiểm tra CTW/LTW, `spring-aspects`, agent và classloading |
| Có `@EnableSpringConfigured` nhưng không inject | Annotation chỉ cấu hình aspect với Spring, không weave target class | Bật AspectJ weaver trong build/JVM |
| Có `@EnableAspectJAutoProxy` nhưng constructor vẫn không được intercept | Spring proxy-based AOP không intercept `new` object thường | Dùng AspectJ weaving nếu thật sự cần `@Configurable` |
| Agent chạy nhưng không tìm thấy template | `value` hoặc default FQCN không khớp BeanFactory bean definition | Khớp chính xác tên, khai báo template rõ |
| Setter không được gọi | Sai property name hoặc thiếu writable setter/field injection metadata | Xác minh `setPolicy`/`property name="policy"` và wiring |
| BeanFactory không resolve collaborator | Thiếu bean, nhiều beans cùng type, qualifier sai | Khai báo collaborator và resolution policy rõ |
| Constructor gọi dependency nhưng thấy null | `preConstruction=false` | Tránh dùng trong constructor hoặc cân nhắc `preConstruction=true` |
| Setter/init chạy hai lần | Annotated class đồng thời do Spring container tạo/quản lý | Không đặt `@Configurable` trên Spring-managed bean classes |
| Unit test chạy không có ApplicationContext | Aspect chưa nhận BeanFactory | Dùng mock dependency trực tiếp hoặc test integration với context/weaver |
| Nhiều ApplicationContexts và wrong dependency | Singleton aspect dùng BeanFactory không phù hợp | Cấu hình trong shared parent, kiểm soát ClassLoader |
| Object tạo quá sớm chưa được inject | Aspect chưa được cấu hình ở thời điểm object creation | Sắp xếp startup order, `depends-on` phù hợp |
| Weaving overhead/startup lỗi tại module boundary | ClassLoader/JPMS/agent không thể transform target | Test deployment thực, giới hạn packages weave, chọn cấu hình LTW đúng |
| Tưởng mọi field/method `@Autowired` trên `new` đều chạy | Spring bean post-processors không tự xử lý object ngoài container | Dùng aspect + weaving hoặc tạo object qua Spring-aware factory |
| Tự `context.getBean("domainOrder")` trong main rồi coi đó là chứng cứ | Đây là BeanFactory creation bình thường, không kiểm tra `new` | Test đúng `new DomainOrder()` |
| Gọi `@Configurable` trên object sau khi tạo bằng một utility riêng | `@Configurable` không phải method gọi sau này | Dùng weaving hoặc Spring `AutowireCapableBeanFactory` một cách tường minh |

## 16. Khi nào nên dùng, khi nào không nên dùng?

**Nên cân nhắc `@Configurable` + `AnnotationBeanConfigurerAspect` khi:**

- Object bắt buộc được tạo bởi `new` ở các vị trí không thể thay đổi, nhưng vẫn cần Spring collaborator.
- Object do framework ngoài IoC dựng lên, và Spring provided AspectJ integration thực sự phù hợp với lifecycle ấy.
- Ứng dụng đã có AspectJ weaving infrastructure được quản lý rõ; chi phí thêm và giới hạn classloading được chấp nhận.
- Mối quan hệ giữa object domain và Spring collaborator được review/test kỹ, để phụ thuộc không trở thành bí mật.

**Thường không nên dùng nếu:**

- Class đã là Spring `@Component`/`@Service`; dùng DI thông thường là đủ và tránh double configuration.
- Có thể truyền collaborator cho constructor của domain object ngay khi tạo, hoặc sử dụng một Spring-managed factory.
- Chỉ cần AOP method advice cho các Spring beans — trường hợp đó dùng Spring AOP proxy thay vì weaving `@Configurable`.
- Ứng dụng có nhiều ApplicationContexts/classloader phức tạp, cần dependency scope theo request/tenant; cơ chế AspectJ singleton BeanFactory reference có thể khó quản lý.
- Mục tiêu chỉ là làm cho `@Autowired` bỗng hoạt động với mọi object `new` mà không chấp nhận yêu cầu weaver/deployment.

### Một phương án thay thế dễ kiểm thử

~~~java
@Service
public class DomainOrderFactory {
    private final PricingPolicy policy;

    public DomainOrderFactory(PricingPolicy policy) {
        this.policy = policy;
    }

    public DomainOrder create() {
        return new DomainOrder(policy);
    }
}
~~~

Đây là mẫu thiết kế **khác ví dụ đang dùng @Configurable**: `DomainOrder(PricingPolicy)` phải là một constructor tương ứng, và `DomainOrderFactory` do Spring quản lý. Ở thiết kế này, dependency được truyền tường minh, không cần AspectJ weaving. Chọn phương án đó khi bạn có quyền chỉnh code tạo object.

## 17. Tổng kết

`@Configurable` là annotation metadata trên **type được tạo ngoài Spring IoC**. **`AnnotationBeanConfigurerAspect`** từ `spring-aspects` mới là cơ chế tích hợp Spring BeanFactory với quá trình tạo object; để nó làm việc, **target type phải được AspectJ weave** và aspect phải được Spring cấu hình qua `@EnableSpringConfigured` hoặc `<context:spring-configured/>`.

Ghi nhớ ba khác biệt:

1. **Spring bean management**: BeanFactory chủ động tạo và quản lý bean.
2. **Spring AOP proxy mode**: intercept method calls đi qua Spring bean proxy; không làm `new` object thường tự được inject.
3. **Spring + AspectJ `@Configurable` integration**: weave construction/initialization của target type rồi nhờ Spring cấu hình existing object.

Luôn kiểm tra đúng class đã weave, BeanFactory đã sẵn sàng, bean template hoặc `@Autowired` wiring hợp lệ, constructor timing và context/classloader boundary. Tránh gắn `@Configurable` lên Spring bean bình thường để không cấu hình cùng một instance hai lần.

### Tài liệu Spring chính thức

- [Spring Framework — Using AspectJ to Dependency Inject Domain Objects with Spring](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html).
- [Spring Framework — Load-time Weaving with AspectJ](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html#using-aspectj-ltw).
- [Spring Framework 6.1 — @Configurable Javadoc](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/beans/factory/annotation/Configurable.html).
- [Spring Framework 6.1 — @EnableSpringConfigured Javadoc](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/context/annotation/aspectj/EnableSpringConfigured.html).
- [Spring Framework 6.1 — AnnotationBeanConfigurerAspect Javadoc](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/beans/factory/aspectj/AnnotationBeanConfigurerAspect.html).
- [Spring Framework — Annotation-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-integration-006">LoadTimeWeaver và @EnableLoadTimeWeaving</a>

<details>
<summary>Xem chi tiết</summary>

# LoadTimeWeaver và @EnableLoadTimeWeaving trong Spring Framework

## 1. Vấn đề mà Spring cần giải quyết

**Spring AOP proxy** xử lý lời gọi qua proxy của Spring bean. Khi ứng dụng phải áp dụng AspectJ advice lên những join point mà proxy không chạm tới (chẳng hạn đối tượng tạo bằng `new` ngoài IoC, lời gọi nội bộ, constructor hoặc field access được AspectJ hỗ trợ), có thể cần **AspectJ load-time weaving (LTW)**.

*Weaving* là quá trình chỉnh bytecode để cài advice tại join point. Với **LTW**, công việc này diễn ra **khi JVM nạp class**, thay vì lúc biên dịch. Spring Framework tích hợp với việc đăng ký `ClassFileTransformer` thông qua `LoadTimeWeaver`; AspectJ cung cấp transformer thực sự đọc cấu hình weaving và áp advice.

**`@EnableLoadTimeWeaving` là annotation cấu hình hạ tầng Spring.** Nó đăng ký một bean có tên `loadTimeWeaver` trong `ApplicationContext` và có thể đăng ký transformer của AspectJ. Annotation này **không tự định nghĩa aspect, không tự tạo Spring AOP proxy và không đảm bảo class đã load trước đó sẽ được weave**.

Muốn Spring-managed AspectJ LTW chạy được, cần phối hợp đủ:

1. Một Spring `ApplicationContext` có `LoadTimeWeaver` khả dụng.
2. Một `ClassLoader` hoặc JVM instrumentation cho phép thêm transformer.
3. `aspectjweaver.jar` cùng `META-INF/aop.xml` trên classpath và các aspect phù hợp.
4. `AspectJWeaving` được bật (`ENABLED` hoặc `AUTODETECT` phát hiện cấu hình).
5. Class target phải được nạp **sau khi transformer đã được đăng ký**, thuộc phạm vi weave và match pointcut của aspect.

**Phạm vi:** Bài học chuyên về tích hợp AspectJ LTW **trong Spring Framework**, đặc biệt `LoadTimeWeaver`, `@EnableLoadTimeWeaving`, môi trường Spring context, bootstrapping và diagnostics; không trình bày lại lý thuyết AOP chung.

## 2. Ba cơ chế dễ bị nhầm lẫn

| Cơ chế | Công cụ hạ tầng | Thời điểm | Đối tượng/chỗ advice có thể tới |
| --- | --- | --- | --- |
| **Spring AOP proxy** | `@EnableAspectJAutoProxy`, Spring Advisors, JDK/CGLIB proxy | Khi Spring tạo proxy | Spring bean và những invocation **đi qua proxy** |
| **AspectJ LTW** | AspectJ `ClassFileTransformer`, Java agent/ClassLoader, `aop.xml` | Khi JVM **load class** | Join point tương ứng trong class được weave, kể cả target không phải Spring bean |
| **AspectJ CTW** | AspectJ compiler `ajc` | Khi build | Class đã weave trước lúc deploy |
| **AspectJ binary/post-compile weaving** | `ajc` xử lý bytecode/JAR hiện có | Trong build | Artifact sau khi xử lý |

Đặc biệt, **`@EnableAspectJAutoProxy` không bật AspectJ weaving**. Spring dùng các annotation `@Aspect`, `@Before`, `@Around` để định nghĩa advice rồi áp chúng qua **proxy**.

**`@EnableLoadTimeWeaving`** phục vụ ClassLoader transformer, có thể xử lý những method execution mà Spring AOP proxy không bao được, tùy AspectJ pointcut/weaving. Chỉ bật annotation không đảm bảo target được advice; phải nhìn runtime và output để xác nhận.

## 3. Spring LoadTimeWeaver làm gì?

Spring SPI:

~~~java
org.springframework.instrument.classloading.LoadTimeWeaver
~~~

Các khả năng chính:

- `addTransformer(ClassFileTransformer transformer)`: đăng ký transformer vào ClassLoader/JVM instrumentation phù hợp.
- `getInstrumentableClassLoader()`: trả về ClassLoader có khả năng instrument classes.
- `getThrowawayClassLoader()`: ClassLoader tạm cho infrastructure kiểm tra bytecode mà không định nghĩa class trong loader chính, khi implementation hỗ trợ.

Đây là **cơ chế nối Spring với quá trình class loading**, không phải engine xử lý pointcut. Transformer AspectJ được Spring đăng ký là `org.aspectj.weaver.loadtime.ClassPreProcessorAgentAdapter`. Nó đọc metadata AspectJ để quyết định class nào cần weave.

~~~text
Spring ApplicationContext
        |
        +-- @EnableLoadTimeWeaving
                 |
                 v
        bean "loadTimeWeaver"
                 |
                 v
       DefaultContextLoadTimeWeaver
           tìm mechanism hỗ trợ
                 |
        +--------+---------+
        |                  |
    JVM Instrumentation   ClassLoader/container hỗ trợ
        |                  |
        +--------+---------+
                 |
       addTransformer(AspectJ adapter)
                 |
           JVM load target class
                 |
      META-INF/aop.xml + match pointcut
                 |
         bytecode đã được weave
                 |
      thực thi method / woven advice
~~~

Một `LoadTimeWeaver` còn hữu ích cho **JPA entity enhancement** hoặc transformer hạ tầng khác. Do đó việc đăng ký weaver và việc **bật AspectJ** là hai quyết định cấu hình khác nhau.

## 4. Sử dụng @EnableLoadTimeWeaving

Cấu hình tối thiểu:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableLoadTimeWeaving;

@Configuration
@EnableLoadTimeWeaving
public class ApplicationConfiguration {
}
~~~

Đây là fragment của một cấu hình cần được đưa vào Spring `ApplicationContext`, tương đương mục tiêu với XML:

~~~xml
<context:load-time-weaver/>
~~~

(Đối với XML phải có namespace/schema `context` tương ứng.) Spring đăng ký bean tên chuẩn **`loadTimeWeaver`**. Bean tích hợp JPA hoặc hạ tầng khác có thể implement `LoadTimeWeaverAware` để nhận reference này khi được Spring cấu hình.

~~~java
// Fragment, cần định nghĩa thành Spring bean.
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.instrument.classloading.LoadTimeWeaver;

public class MyInfrastructureBean implements LoadTimeWeaverAware {
    private LoadTimeWeaver weaver;

    @Override
    public void setLoadTimeWeaver(LoadTimeWeaver weaver) {
        this.weaver = weaver;
    }
}
~~~

Có được reference đến `LoadTimeWeaver` chứng minh Spring đã có cơ chế cấu hình, **không** chứng minh `aop.xml` đúng, target đã được weave hoặc pointcut đã match.

`@EnableLoadTimeWeaving` import `LoadTimeWeavingConfiguration`. Spring mặc định dùng `DefaultContextLoadTimeWeaver` để tìm implementation theo environment. Đừng dùng `@EnableAspectJAutoProxy` như một cách thay thế; annotation đó tạo proxy, không đăng ký ClassFileTransformer.

## 5. AspectJWeaving: AUTODETECT, ENABLED, DISABLED

Thuộc tính `aspectjWeaving` có ba giá trị enum `EnableLoadTimeWeaving.AspectJWeaving`:

| Chế độ | Hành vi trong Spring | Khi nào hữu ích |
| --- | --- | --- |
| **`AUTODETECT`** (mặc định) | Bật Spring-managed AspectJ LTW nếu classpath có một `META-INF/aop.xml`; nếu không tìm thấy thì tắt | Ứng dụng chỉ cần LTW khi có cấu hình AspectJ |
| **`ENABLED`** | Bật AspectJ transformer qua Spring `LoadTimeWeaver` | Muốn bật tường minh vì chủ động triển khai AspectJ LTW |
| **`DISABLED`** | Không bật **Spring-managed AspectJ transformer**, kể cả có `aop.xml` | Vẫn cần `LoadTimeWeaver` cho JPA hoặc use case khác |

Bật rõ ràng:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableLoadTimeWeaving;
import org.springframework.context.annotation.EnableLoadTimeWeaving.AspectJWeaving;

@Configuration
@EnableLoadTimeWeaving(aspectjWeaving = AspectJWeaving.ENABLED)
public class ApplicationConfiguration {
}
~~~

Tắt weaving của Spring:

~~~java
@EnableLoadTimeWeaving(
    aspectjWeaving = EnableLoadTimeWeaving.AspectJWeaving.DISABLED
)
~~~

Đoạn cuối chỉ là fragment annotation. **Ranh giới quan trọng:** `DISABLED` tắt *transformer do Spring đăng ký*, **không vô hiệu hóa một AspectJ Java agent riêng đã được JVM nạp từ trước** và không hoàn tác bytecode đã weave. Tương tự, `ENABLED` không có nghĩa tất cả class được weave; AspectJ còn giới hạn theo `aop.xml`, pointcut, loader và thời điểm nạp.

## 6. LoadTimeWeaver theo môi trường

`DefaultContextLoadTimeWeaver` có thể sử dụng cơ chế riêng của container, JVM instrumentation hay class loader có API tương thích.

| Environment | Ví dụ mechanism | Điều kiện cần kiểm tra |
| --- | --- | --- |
| Java standalone | `InstrumentationLoadTimeWeaver` | JVM chạy với agent `spring-instrument.jar` hoặc instrumentation khả dụng |
| Apache Tomcat | `TomcatLoadTimeWeaver` | ClassLoader và phiên bản container thực sự hỗ trợ transformer |
| JBoss/WildFly hoặc container tương thích | Weaver tích hợp container | Loader isolation, deployment scanning và việc load class sớm |
| ClassLoader hỗ trợ API phản xạ theo convention | `ReflectiveLoadTimeWeaver` | Không áp dụng cho mọi Java AppClassLoader |
| Custom environment | `LoadTimeWeavingConfigurer` cung cấp weaver | Custom implementation phải thật sự đăng ký transformer đúng loader |

Trong ứng dụng Java thuần, Spring documentation hướng dẫn dùng **Spring Java agent**:

~~~text
-javaagent:/absolute/path/to/spring-instrument.jar
~~~

Agent Spring cung cấp khả năng đăng ký transformer. Nó **khác** AspectJ Java agent. Spring sẽ sử dụng `aspectjweaver.jar` ở runtime để gắn AspectJ transformer nếu đã bật qua `@EnableLoadTimeWeaving`.

Cách dùng **AspectJ agent trực tiếp** là một workflow khác:

~~~text
-javaagent:/absolute/path/to/aspectjweaver.jar
  + META-INF/aop.xml
  → AspectJ tự đăng ký transformer lúc JVM startup.
~~~

Nếu chỉ muốn AspectJ Java agent trực tiếp, **không bắt buộc** phải bật `@EnableLoadTimeWeaving`. Tránh vô tình dùng **cả AspectJ agent và Spring-managed AspectJ transformer** cho cùng phạm vi: điều đó có thể tạo double weaving/diagnostics và behavior khó kiểm soát. Một ứng dụng vẫn có thể cần Spring `LoadTimeWeaver` riêng cho JPA dù AspectJ đã chạy qua agent trực tiếp.

## 7. Ví dụ chạy được: Spring-managed LTW với Java 17+

Mục tiêu của ví dụ:

1. Spring `ApplicationContext` đăng ký `LoadTimeWeaver` qua `@EnableLoadTimeWeaving`.
2. Gradle chạy JVM với **spring-instrument** agent.
3. AspectJ được khai báo trong `META-INF/aop.xml` và đưa lên runtime classpath.
4. `OrderService` được tải **sau** khi Spring đăng ký transformer, bằng `Class.forName`.
5. `OrderService` là class `final`, đối tượng tạo **bên ngoài Spring IoC**, nhưng woven advice vẫn chạy khi gọi `submit()`. Điều đó chứng minh đã dùng **LTW**, không dựa vào Spring AOP proxy.

### 7.1. Cấu trúc

~~~text
spring-ltw-demo/
├── settings.gradle
├── build.gradle
└── src/main
    ├── java/demo
    │   ├── Demo.java
    │   ├── OrderService.java
    │   └── TraceAspect.java
    └── resources/META-INF/aop.xml
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-ltw-demo'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

configurations {
    springAgent
}

dependencies {
    implementation platform('org.springframework:spring-framework-bom:6.2.14')
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'

    springAgent 'org.springframework:spring-instrument:6.2.14'
}

application {
    mainClass = 'demo.Demo'
}

tasks.named('run', JavaExec) {
    doFirst {
        jvmArgs "-javaagent:${configurations.springAgent.singleFile.absolutePath}"
    }
}
~~~

`springAgent` là configuration riêng để lấy đúng artifact javaagent. `aspectjweaver` phải nằm trên classpath ứng dụng vì **Spring** sẽ cài AspectJ transformer. Ví dụ không thêm `-javaagent:aspectjweaver.jar` để tránh áp hai con đường AspectJ LTW cùng lúc.

`src/main/java/demo/TraceAspect.java`:

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(* demo.OrderService.submit(..))")
    public void before() {
        System.out.println("aspect: before");
    }
}
~~~

`src/main/java/demo/OrderService.java`:

~~~java
package demo;

public final class OrderService {
    public void submit() {
        System.out.println("service: submit");
    }
}
~~~

`src/main/resources/META-INF/aop.xml`:

~~~xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN"
    "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <weaver options="-showWeaveInfo">
        <include within="demo.OrderService"/>
    </weaver>
    <aspects>
        <aspect name="demo.TraceAspect"/>
    </aspects>
</aspectj>
~~~

`src/main/java/demo/Demo.java`:

~~~java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableLoadTimeWeaving;
import org.springframework.instrument.classloading.LoadTimeWeaver;

public class Demo {
    @Configuration
    @EnableLoadTimeWeaving(
        aspectjWeaving = EnableLoadTimeWeaving.AspectJWeaving.ENABLED
    )
    public static class Config {
    }

    public static void main(String[] args) throws Exception {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            LoadTimeWeaver weaver =
                    ctx.getBean("loadTimeWeaver", LoadTimeWeaver.class);
            System.out.println("weaver registered: " + (weaver != null));

            // Không import OrderService, không tham chiếu OrderService.class:
            // class chỉ được tải sau khi Spring đã setup transformer.
            Class<?> type = Class.forName("demo.OrderService");
            Object service = type.getConstructor().newInstance();
            type.getMethod("submit").invoke(service);
        }
    }
}
~~~

Chạy với JDK 17+ và Gradle:

~~~bash
gradle run
~~~

Hoặc dùng Gradle Wrapper nếu có: `./gradlew run` (macOS/Linux), `gradlew.bat run` (Windows).

**Stdout logic của chương trình** (bỏ log Gradle, Spring, diagnostic output của AspectJ):

~~~text
weaver registered: true
aspect: before
service: submit
~~~

### 7.2. Phân tích

1. Java process do Gradle khởi chạy nhận `-javaagent:...spring-instrument.jar`. Spring có khả năng đăng ký transformer trên instrumentation.
2. `AnnotationConfigApplicationContext` đọc `@EnableLoadTimeWeaving` của `Config`, tạo `loadTimeWeaver` và đăng ký AspectJ transformer ở chế độ `ENABLED`.
3. AspectJ đọc `META-INF/aop.xml`: chỉ weave class `demo.OrderService`, dùng aspect `demo.TraceAspect`.
4. Sau khi context đã khởi tạo xong, `Class.forName("demo.OrderService")` khiến JVM load class service; class chưa có reference trực tiếp từ `Demo` nên giảm rủi ro load quá sớm.
5. `OrderService` do reflection tạo bên ngoài Spring; không có Spring proxy hoặc bean definition cho nó, nhưng khi gọi `submit()`, woven `@Before` advice in `aspect: before`.
6. `OrderService` là `final`. AspectJ LTW xử lý bytecode nên không cần subclass class này như CGLIB. Nếu không thấy dòng `aspect: before`, hãy tìm lỗi agent, weaving, class loading hoặc pointcut.

**Lưu ý kiểm chứng:** ví dụ mô tả output kỳ vọng theo cấu hình chuẩn; khi chạy trong môi trường cụ thể phải xác minh các dòng weave info, classpath và JDK/Gradle version. Dùng `Class.forName` ở đây chỉ để đảm bảo class load sau transformer; code nghiệp vụ bình thường không bắt buộc phải dùng reflection.

## 8. META-INF/aop.xml là cấu hình AspectJ, không phải Spring bean XML

AspectJ LTW sử dụng resource `META-INF/aop.xml` để xác định **aspect nào** được weave, **class nào** thuộc phạm vi và options chẩn đoán.

~~~xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN"
    "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <weaver options="-showWeaveInfo">
        <include within="com.example.orders..*"/>
        <exclude within="com.example.orders.generated..*"/>
    </weaver>
    <aspects>
        <aspect name="com.example.orders.AuditAspect"/>
    </aspects>
</aspectj>
~~~

Ví dụ này chỉ minh họa metadata của ứng dụng có `com.example.orders.AuditAspect`; muốn dùng thực tế phải có class đó. Pattern `..*` giới hạn package và các subpackage phù hợp. Spring khuyến nghị giới hạn phạm vi weave để tránh ảnh hưởng framework/JDK và giảm overhead.

Trong chế độ `AUTODETECT`, việc tìm thấy file `aop.xml` **chỉ khiến Spring bật AspectJ weaving**. AspectJ vẫn phải đọc được resource, biết aspect nào, target class nào, và pointcut có match hay không. Một file rỗng/không match sẽ không tạo ra advice hữu ích.

## 9. Tại sao class loading order quan trọng nhất?

LTW cần transformer **đã sẵn sàng trước khi JVM định nghĩa class cần weave**. Những hành động sau có thể làm target được load sớm:

- Static initializer hoặc code startup dùng trực tiếp `Target.class`, `new Target()` trước khi Spring context hoàn tất.
- Framework reflection/introspection hoặc component scanning chạm type.
- App server preload class hay package scanning.
- Một bean được tạo quá sớm trong bootstrap.
- Một agent/ClassLoader khác đã định nghĩa class từ trước.

Nếu class đã load trước khi transformer được đăng ký, **chỉ reload bean hay restart một ApplicationContext trong cùng JVM không tự weave lại class đó**. Instrumentation JVM có một số cơ chế retransformation riêng, nhưng không được mặc định Spring-managed LTW sẽ hồi tố weave mọi class.

### Thực hành để tránh load sớm

- Tạo/khởi tạo Spring context trước khi gọi những method được weave, khi lifecycle ứng dụng cho phép.
- Tối thiểu hóa code startup/static initialization tham chiếu class target.
- Chọn loader/container integration tương thích với deployment.
- Bật `-showWeaveInfo` có kiểm soát, xác nhận target **thực sự được weave**.
- Trong ví dụ test tích hợp, dùng class chưa được JVM load trước setup; tránh unit test chạy trước khiến class đã load trong cùng test JVM.
- Với Spring Boot và framework scanner, kiểm chứng timing **trên artifact cuối** vì bootstrap khác standalone Spring context.

## 10. Dùng LoadTimeWeavingConfigurer khi nào?

Một `@Configuration` class có thể implement `LoadTimeWeavingConfigurer` và trả về custom `LoadTimeWeaver`.

~~~java
// Fragment: MyCustomWeaver cần implementation và import thích hợp.
@Configuration
@EnableLoadTimeWeaving
public class CustomConfiguration implements LoadTimeWeavingConfigurer {
    @Override
    public LoadTimeWeaver getLoadTimeWeaver() {
        return new MyCustomWeaver();
    }
}
~~~

Đây là **fragment không độc lập**: cần định nghĩa `MyCustomWeaver` và import `LoadTimeWeavingConfigurer`, `LoadTimeWeaver`. Chỉ triển khai custom nếu cần chỉnh ClassLoader transformer thật sự; không viết nó chỉ để khai báo thêm một `@Aspect` hay đổi pointcut.

Một custom implementation phải trả lời được:

1. Transformer được cài lên **ClassLoader nào**?
2. Có thể add transformer trước khi class mục tiêu load không?
3. Có tương tác với transformer JPA hoặc Java agents khác không?
4. Khi chạy trong app server, phạm vi loader của một app khác được bảo vệ thế nào?
5. Có hỗ trợ `getThrowawayClassLoader()` khi infrastructure cần xem trước bytecode không?

`LoadTimeWeaver` là SPI hạ tầng; business advice vẫn nên được định nghĩa bằng AspectJ aspect phù hợp.

## 11. Sự khác nhau giữa @EnableLoadTimeWeaving và @EnableSpringConfigured

Một chi tiết riêng của Spring cần chú ý:

- XML `<context:load-time-weaver aspectj-weaving="on"/>` có thể ngầm kích hoạt chức năng tương ứng `<context:spring-configured/>`.
- Java annotation `@EnableLoadTimeWeaving(aspectjWeaving = ENABLED)` **không tự bật** `@EnableSpringConfigured`.

Nếu muốn `@Configurable` giúp Spring inject vào **domain object được tạo ngoài IoC** thông qua AspectJ weaving, cần thêm **`spring-aspects`** và annotation cấu hình rõ ràng:

~~~java
// Fragment; cần spring-aspects và AspectJ weaving được cấu hình.
@Configuration
@EnableLoadTimeWeaving(
    aspectjWeaving = EnableLoadTimeWeaving.AspectJWeaving.ENABLED
)
@EnableSpringConfigured
class AppConfig {
}
~~~

Cần import `org.springframework.context.annotation.aspectj.EnableSpringConfigured`. Không chỉ gắn `@Configurable` lên object là đủ để injection xảy ra: cần `AnnotationBeanConfigurerAspect` và cơ chế weaving đúng. Tránh dùng `@Configurable` trên class vốn đã được Spring quản lý như bean bình thường, vì có nguy cơ bị cấu hình/khởi tạo hai lần.

Ngoài ra, khi **AspectJ tạo instance của một aspect singleton** mà aspect cần collaborator là Spring bean, bài toán inject dependency vào **chính AspectJ-owned instance** là một vấn đề vòng đời khác. Spring có thể cấu hình singleton đó thông qua `aspectOf()`. `@EnableLoadTimeWeaving` không tự động inject mọi dependency của mọi woven aspect.

## 12. Spring Boot, ứng dụng độc lập và app server

### Spring Boot

Trong Spring Boot, không giả định task `bootRun` kế thừa mọi JVM args của task Gradle `run` ở ví dụ. Khi chạy bằng `bootRun`, cấu hình Java agent trên **chính task `bootRun`**; khi chạy bằng `java -jar`, thêm JVM argument `-javaagent:...spring-instrument.jar` trước `-jar`.

Với Spring Boot executable JAR, kiểm tra vị trí `aop.xml`, AspectJ runtime dependencies, thứ tự class load, Spring configuration, và classloader có hỗ trợ transformer không. Một sample Java thuần thành công chưa đảm bảo các điều này trong Boot packaging.

### Tomcat/JBoss/WildFly

Các container có thể cung cấp ClassLoader hỗ trợ LTW, nên có trường hợp không cần JVM `spring-instrument` agent. Nhưng container có thể preload classes sớm, có nhiều loader và deployment scopes. Spring-managed LTW cho phép cấu hình theo application context/ClassLoader trong những môi trường tương thích. Luôn kiểm tra phiên bản container và công cụ weave ở runtime.

### Nhiều agents / nhiều context

Nếu vừa dùng Spring-managed AspectJ LTW, vừa cài `aspectjweaver` Java agent, hoặc dùng cả AspectJ weaving và Spring AOP advice trùng nhau, có thể gây advice/logging chạy hai lần, transformer overlap hoặc lỗi khó debug. Tách rõ phạm vi từng cơ chế và test số lần advice chạy.

## 13. Những sai lầm phổ biến và dấu hiệu nhận biết

| Triệu chứng | Nguyên nhân khả dĩ | Bước kiểm tra |
| --- | --- | --- |
| Có `loadTimeWeaver` bean nhưng không thấy advice | Chưa có/cấu hình sai `aop.xml`, aspect, target hoặc target đã load sớm | Bật `-showWeaveInfo`, kiểm tra join point và timing |
| Lỗi không thể add transformer trong JVM standalone | JVM thiếu instrumentation hoặc loader không phù hợp | Thêm đúng `spring-instrument.jar` agent, kiểm tra runtime |
| Bật `@EnableAspectJAutoProxy` nhưng raw target không nhận advice | Spring tạo proxy, không weave bytecode | Dùng CTW/LTW khi yêu cầu vượt proxy |
| `@EnableLoadTimeWeaving` không weave | `AUTODETECT` không thấy `META-INF/aop.xml` | Kiểm tra resource trên runtime classpath hoặc bật `ENABLED` có chủ đích |
| Weave không match service | `aop.xml` include/exclude hoặc pointcut sai | Xem debug/weave information |
| Target đã được load trước bootstrap | Transformer được cài quá muộn | Sửa startup order/container scanning |
| AspectJ advice chạy trùng | Có cả weaving và Spring AOP cùng xử lý | Tách aspects, advisor registration và phạm vi pointcut |
| Dev chạy `gradle run` được, `bootRun` không được | Task Boot không nhận agent arguments | Cấu hình `bootRun.jvmArgs` riêng |
| `@Configurable` không inject | Thiếu `spring-aspects`, `@EnableSpringConfigured` hoặc weaving | Kiểm tra `AnnotationBeanConfigurerAspect` và runtime |
| `DISABLED` mà vẫn thấy woven advice | JVM có AspectJ agent khác/bytecode đã weave từ build | Kiểm tra JVM args và artifact class |
| Test có proxy `false`, kết luận LTW hỏng | Woven object vốn không cần proxy | Kiểm tra woven method behavior thay vì `AopUtils.isAopProxy` |
| Chạy nhiều context bị khác behavior | AspectJ singleton/ClassLoader hoặc transformer scope khác | Kiểm tra loader ownership và initialization |

**Bằng chứng LTW chạy thành công** phải bao gồm: config đăng ký transformer, AspectJ weave information/observable advice, và thời điểm target class được định nghĩa. Chỉ thấy context khởi tạo không có lỗi là chưa đủ.

## 14. Checklist kiểm thử tích hợp

1. Có bean tên `loadTimeWeaver` đúng trong context.
2. JVM/classloader hỗ trợ transformer; không có lỗi instrumentation.
3. AspectJ weaver cùng `META-INF/aop.xml` có trên runtime classpath.
4. `AspectJWeaving` đúng trạng thái `AUTODETECT`, `ENABLED` hoặc `DISABLED` mong muốn.
5. Target class chưa load trước khi transformer được đăng ký.
6. Class filter của `aop.xml` và pointcut match đúng method/join point.
7. Advice chạy số lần đúng, kể cả khi object được tạo ngoài Spring IoC nếu đó là chủ đích.
8. Không advice trùng từ Spring AOP proxy/AspectJ agent riêng.
9. Target class `final`, self-invocation hoặc scenario vượt proxy hoạt động theo chính semantics của AspectJ weaving.
10. Không vô tình weave Spring/JDK infrastructure hay lớp của ứng dụng khác trên cùng JVM.
11. Với `@Configurable`, injection xảy ra đúng lúc và không cấu hình object hai lần.
12. Chạy integration tests trên chính Spring, AspectJ, JDK và deployment packaging dùng trong thực tế.

Đối với lỗi LTW, tiến trình chẩn đoán hợp lý là **JVM arguments → LoadTimeWeaver bean → AspectJ runtime/aop.xml → class loading time → weave info → pointcut match → runtime advice**. Đây là chuỗi xác minh cụ thể, tránh kết luận sai chỉ vì có `@EnableLoadTimeWeaving`.

## 15. Khi nào chọn cơ chế nào?

- Muốn advice các method invocation đi qua Spring bean proxy? **Spring AOP** và `@EnableAspectJAutoProxy` là giải pháp đơn giản hơn.
- Muốn AspectJ weave và muốn Spring kiểm soát transformer trong context/ClassLoader? **`LoadTimeWeaver` + `@EnableLoadTimeWeaving`**, với instrumentation/classloader phù hợp.
- Muốn AspectJ LTW từ JVM startup toàn cục? Cân nhắc **AspectJ Java agent trực tiếp**; không mặc định cần Spring `@EnableLoadTimeWeaving` cho riêng AspectJ.
- Muốn weave trước khi ứng dụng khởi động? Dùng **AspectJ compile-time/binary weaving**, kiểm thử artifact sau build.
- Chỉ muốn Spring hạ tầng như JPA nhận một `LoadTimeWeaver` nhưng không dùng AspectJ? Có thể dùng **`aspectjWeaving = DISABLED`** để tránh bật transformer AspectJ.

**Điều cần nhớ:** `@EnableLoadTimeWeaving` là điểm nối giữa **Spring IoC và class-loading instrumentation**; nó khác `@EnableAspectJAutoProxy`. Spring đăng ký transformer, AspectJ quyết định class/join point nào được weave, và JVM quyết định khi nào class được định nghĩa. Không hiểu ba trách nhiệm đó rất dễ tạo ứng dụng biên dịch/khởi động tốt nhưng advice không chạy.

## Nguồn chính thức

- [Spring Framework Reference 6.2 — AspectJ và Load-time Weaving](https://docs.spring.io/spring-framework/reference/6.2/core/aop/using-aspectj.html).
- [Spring Framework Reference 6.2 — Registering a LoadTimeWeaver](https://docs.spring.io/spring-framework/reference/6.2/core/beans/context-load-time-weaver.html).
- [Spring Framework Javadoc 6.2 — EnableLoadTimeWeaving](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/context/annotation/EnableLoadTimeWeaving.html).
- [Spring Framework Javadoc 6.2 — LoadTimeWeaver](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/instrument/classloading/LoadTimeWeaver.html).
- [Spring Framework Reference 6.2 — Spring AOP @AspectJ proxy support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html).
- [AspectJ — Load-Time Weaving Configuration](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html).

Trong dự án thực tế, hãy xác minh chính xác phiên bản Spring/AspectJ, JDK, Java agent, classloader và thời điểm nạp class; đừng mặc định một cấu hình LTW trong JVM standalone sẽ tự hoạt động giống hệt bên trong ứng dụng Spring Boot hay application server.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-integration-007">META-INF/aop.xml và giới hạn lớp được weave trong ứng dụng Spring</a>

<details>
<summary>Xem chi tiết</summary>

# META-INF/aop.xml và giới hạn lớp được weave trong ứng dụng Spring

## 1. Mục tiêu và phạm vi

Trong ứng dụng Spring, **Spring AOP dùng proxy** và **AspectJ load-time weaving (LTW)** là hai cơ chế khác nhau. Spring AOP thường advise method execution khi lời gọi đi qua Spring bean proxy. AspectJ LTW cho phép weaver biến đổi bytecode của các class được chọn **khi classloader nạp chúng**. Nhờ đó, AspectJ có thể tác động lên method của class không phải Spring bean, object được tạo bằng `new`, và một số lời gọi nội bộ mà Spring proxy không intercept.

**`META-INF/aop.xml` là file cấu hình của AspectJ LTW**, dùng để khai báo aspect, giới hạn tập class được weave, và bật thông tin chẩn đoán. Nó không phải file cấu hình bắt buộc cho một ứng dụng chỉ dùng `@Aspect` với `@EnableAspectJAutoProxy`.

Sau bài học, bạn có thể:
- Phân biệt Spring AOP proxy với AspectJ weaving khi làm việc trong Spring Framework.
- Cấu hình đúng `META-INF/aop.xml` với `<aspects>`, `<weaver>`, `include/exclude`.
- Giải thích vì sao package/type pattern quyết định class được weave mà không phụ thuộc Spring bean name.
- Chạy demo Java 17+ có Spring bean, object `new` và class bị exclude, rồi đọc output.
- Kiểm tra Java agent, classloader, thời điểm load, nhiều file `aop.xml` và hiệu lực của Spring `@EnableLoadTimeWeaving`.
- Tránh các lỗi khi trộn Spring AOP và AspectJ LTW, đặc biệt với transaction, self-invocation và các aspect khác.

Phạm vi chỉ tập trung vào **AspectJ trong ứng dụng Spring**; các thuật ngữ weaving và join point được giải thích đủ cho việc sử dụng, không mở rộng thành toàn bộ giáo trình AspectJ độc lập.

## 2. Spring AOP proxy khác AspectJ LTW thế nào?

| Đặc tính | Spring AOP | AspectJ LTW |
| --- | --- | --- |
| Cơ chế | BeanPostProcessor/auto-proxy creator tạo JDK proxy hoặc CGLIB proxy | AspectJ weaver xử lý bytecode trước khi JVM định nghĩa class |
| Điểm kích hoạt | Method call thực sự đi qua proxy | Join point đã được weave và match pointcut |
| Loại đối tượng | Thông thường Spring-managed beans | Bất kỳ class hợp lệ nào mà weaver nhìn thấy và được phép weave |
| `new SomeClass()` | Không tự động nhận Spring AOP proxy | Có thể nhận advice nếu class đã được LTW weave |
| `this.otherMethod()` | Self-invocation thường bypass Spring proxy | Method execution `otherMethod` có thể được advise khi class đã weave |
| `@EnableAspectJAutoProxy` | Bật Spring AOP proxy theo `@AspectJ` style | Không tự bật LTW |
| `META-INF/aop.xml` | Không cần | Dùng cho cấu hình AspectJ LTW |
| JVM/classloader instrumentation | Không cần AspectJ weaving agent | Cần agent hoặc load-time weaver tương thích được bật trước khi class load |
| Ai tạo aspect | Spring quản lý `@Aspect` như bean trong proxy mode | AspectJ quản lý các aspect được khai báo/khởi tạo qua weaving mode |

Từ **`@AspectJ`** trong tài liệu Spring mô tả phong cách khai báo aspect bằng annotation, **không có nghĩa mọi ứng dụng dùng `@Aspect` đều thực hiện AspectJ bytecode weaving**.

Ngược lại, sử dụng AspectJ LTW không bắt buộc đăng ký aspect như một Spring `@Component`. Có thể tồn tại đồng thời Spring proxy và class đã weave. Khi cùng logic được cấu hình ở cả hai cơ chế, rất dễ ghi log hai lần hoặc áp behavior lặp.

## 3. File META-INF/aop.xml nằm ở đâu?

Trong Gradle/Maven project Java thông thường:

~~~text
src/main/resources/META-INF/aop.xml
~~~

Sau build, đường dẫn resource trên **runtime classpath** phải vẫn là `META-INF/aop.xml`. Ví dụ Gradle sẽ đưa resource vào `build/resources/main/META-INF/aop.xml`; khi tạo JAR, nó được đóng gói theo cấu trúc tương ứng.

Một tệp hoàn chỉnh tối giản:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<aspectj>
    <aspects>
        <aspect name="com.example.aspect.AuditAspect"/>
    </aspects>

    <weaver options="-showWeaveInfo">
        <include within="com.example.service..*"/>
        <exclude within="com.example.service.internal..*"/>
    </weaver>
</aspectj>
~~~

Đọc theo hai ranh giới:
- **`<aspects>`**: khai báo **aspect class nào** tham gia weaving. `name` là fully qualified class name của aspect, **không phải Spring bean name**.
- **`<weaver>`**: giới hạn **target class nào** được AspectJ xem xét weaving, đồng thời cấu hình log/diagnostics.

AspectJ không tự quét classpath để tìm mọi `@Aspect` chỉ vì có một thẻ `<aspects>`. Bạn phải khai báo những aspect cần dùng (hoặc dùng hình thức khai báo cụ thể mà AspectJ hỗ trợ).

`-showWeaveInfo` phát thông báo về việc weave class, hữu ích khi kiểm tra phạm vi. Có thể dùng `-verbose` hoặc `-debug` lúc điều tra, nhưng chúng thường cho log lớn.

Không nhầm với `application.yml` của Spring Boot, `spring.factories`, hay XML của Spring proxy AOP như `<aop:config>`. Các cơ chế có file và lifecycle khác nhau.

## 4. Hai nơi có include/exclude: lọc aspect và lọc target

### 4.1. Include/exclude bên trong <aspects>

~~~xml
<aspectj>
    <aspects>
        <aspect name="com.example.aspect.AuditAspect"/>
        <aspect name="com.example.aspect.MetricAspect"/>

        <include within="com.example.aspect.AuditAspect"/>
    </aspects>
</aspectj>
~~~

Thẻ `include` trên chỉ giới hạn **aspect nào được sử dụng** trong tập đã khai báo. Nó **không** nói chỉ weave target class cùng package với `AuditAspect`.

Khi ứng dụng có nhiều file `META-INF/aop.xml` từ nhiều thư viện, bộ lọc aspect có thể tác động đến những aspect được khai báo trong file khác. Vì vậy, cần kiểm tra toàn bộ cấu hình mà classloader thực sự nhìn thấy trước khi thêm filter aspect mang tính toàn cục.

### 4.2. Include/exclude bên trong <weaver>

~~~xml
<aspectj>
    <aspects>
        <aspect name="com.example.aspect.AuditAspect"/>
    </aspects>
    <weaver>
        <include within="com.example.business..*"/>
        <exclude within="com.example.business.generated..*"/>
    </weaver>
</aspectj>
~~~

Đây là bộ lọc **type được weave**. Với ví dụ này, `com.example.business.OrderService` có thể được weave, còn `com.example.business.generated.MapperImpl` bị exclude, ngay cả khi pointcut của aspect match phương thức trong cả hai class.

Một advice thực sự chạy khi tất cả điều kiện đều phù hợp: aspect đã được weaver biết, class được nạp qua cơ chế weaving, class vượt qua include/exclude, và join point match pointcut.

**Nếu không có `<weaver><include>`**, AspectJ có thể xét nhiều type nhìn thấy bởi weaving classloader (ngoại trừ exclude và các giới hạn riêng của weaver). Với ứng dụng Spring, nên ưu tiên package ứng dụng cụ thể để giảm chi phí và nguy cơ tác động ngoài ý muốn.

### 4.3. Type pattern không phải tên bean hoặc URL

Pattern `com.example.business..*` nói về Java type theo cú pháp AspectJ, không phải bean name `orderService` hay HTTP route `/orders/**`. Một object do `new OrderService()` tạo sẽ được hưởng effect của bytecode đã weave nếu type đó đủ điều kiện, dù nó hoàn toàn không nằm trong Spring `ApplicationContext`.

**Aspect filter** trả lời “aspect nào được dùng”; **weaver filter** trả lời “class nào được xử lý”; **pointcut** trả lời “join point nào nhận advice”. Không thể hoán đổi vai trò của ba thành phần.

## 5. Điều gì giới hạn class được weave trong ứng dụng Spring?

### 5.1. Class phải được nạp sau khi weaver sẵn sàng

LTW chuyển đổi bytecode **khi classloader nạp và định nghĩa class**. Nếu class đã được JVM load trước khi agent/weaver biết đến aspect cần dùng, không được mặc định file `aop.xml` thay đổi class đó hồi tố.

Với JVM agent tiêu chuẩn, cần đưa tùy chọn vào **JVM startup command**:

~~~text
-javaagent:/path/to/aspectjweaver.jar
~~~

Nó là JVM argument, không phải argument truyền cho `main(String[] args)`. Trong Spring Boot, Gradle, IDE, test runner, Docker và application server, phải đặt vào **process JVM thực sự thực thi application code**, không chỉ JVM chạy build tool.

### 5.2. Classloader delegation và visibility

AspectJ weaving diễn ra theo từng classloader. Weaver chỉ weave được class mà weaving classloader tương ứng định nghĩa; class được parent classloader định nghĩa trước đó không mặc nhiên đi qua cùng weaver.

Nếu ứng dụng có nhiều module/classloader, plugin engine, Spring Boot launcher, ứng dụng trong servlet container hoặc test runner, việc `META-INF/aop.xml` có trong một JAR **chưa đủ** để khẳng định classloader của target nhìn thấy nó. Phải kiểm tra resource visibility và thời điểm load.

Một số class hạ tầng của JDK/AspectJ không thể weave tùy ý theo LTW thông thường. Đừng viết include cực rộng với kỳ vọng sửa mọi class `java.*` hoặc `org.aspectj.*`.

### 5.3. Có thể có nhiều META-INF/aop.xml

AspectJ hỗ trợ **nhiều** file `META-INF/aop.xml` được classloader nhìn thấy, và sẽ **hợp nhất** tập aspect, bộ lọc và tùy chọn theo quy tắc của AspectJ. Vì vậy, file trong ứng dụng không chắc là cấu hình duy nhất.

Các hệ quả:
- Một thư viện có thể khai báo aspect hạ tầng riêng.
- Bộ lọc include/exclude ở file khác có thể thay đổi tập type hoặc aspect được áp dụng.
- Tìm chỉ file trong `src/main/resources` có thể bỏ sót cấu hình từ dependency.
- Một concrete aspect được khai báo trùng theo cách không hợp lệ có thể gây lỗi.

Khi debug, kiểm tra classpath/JAR thực tế và log `-showWeaveInfo`. Không lấy Spring bean definitions làm danh sách đầy đủ của những aspect AspectJ đang sử dụng.

### 5.4. Lưu ý inter-type declarations (ITD)

Các hạn chế bằng `<weaver><include/exclude>` và scope của aspect chủ yếu được thiết kế cho **regular join point advice**, chẳng hạn method execution. AspectJ lưu ý **inter-type declaration** có semantics riêng; không nên coi các filter này là biện pháp bảo đảm ITD không thêm member/interface vào type ngoài phạm vi.

Nếu bài toán yêu cầu introduction thêm interface hoặc method, cần kiểm tra **cả** cách triển khai ITD và tập class ảnh hưởng. Đây không phải cơ chế sandbox hoặc ranh giới bảo mật; không dùng `aop.xml` thay thế Java module boundaries hay authorization.

## 6. Ví dụ Java 17+: Spring bean được weave, object new được weave, class khác bị exclude

Ví dụ dưới đây dùng Spring Framework 6.1.x với `AnnotationConfigApplicationContext` và AspectJ agent. Không bật `@EnableAspectJAutoProxy`, để chứng minh log advice đến từ **LTW**, không phải Spring proxy.

Ba target đều có `void work()`:
- `WorkService`: bean Spring, thuộc package được include.
- `PlainWorker`: object mới từ `new`, thuộc package được include.
- `SkippedWorker`: object mới từ `new` ở subpackage bị exclude.

Aspect có pointcut match **cả ba** về chữ ký; chỉ class qua bộ lọc weaver mới nhận woven advice.

### 6.1. Cấu trúc file

~~~text
build.gradle
src/main/resources/META-INF/aop.xml
src/main/java/demo/ltw/
    DemoApplication.java
    aspect/WorkAuditAspect.java
    service/WorkService.java
    service/PlainWorker.java
    service/internal/SkippedWorker.java
~~~

### 6.2. Aspect annotation-style

**`src/main/java/demo/ltw/aspect/WorkAuditAspect.java`**

~~~java
package demo.ltw.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class WorkAuditAspect {

    @Before("execution(void demo.ltw..*.work())")
    public void beforeWork(JoinPoint joinPoint) {
        String type = joinPoint.getSignature()
                               .getDeclaringType()
                               .getSimpleName();
        System.out.println("ltw: before " + type);
    }
}
~~~

Aspect không mang `@Component` và không được khai báo thành Spring bean. Nó được AspectJ weaver biết qua `<aspect name="demo.ltw.aspect.WorkAuditAspect"/>` trong `aop.xml`. Đừng đăng ký lại cùng aspect qua Spring AOP trong ví dụ, vì sẽ làm mờ nguyên nhân phát sinh log.

### 6.3. Spring service và plain Java object

**`src/main/java/demo/ltw/service/WorkService.java`**

~~~java
package demo.ltw.service;

public class WorkService {
    public void work() {
        System.out.println("service: work");
    }
}
~~~

**`src/main/java/demo/ltw/service/PlainWorker.java`**

~~~java
package demo.ltw.service;

public class PlainWorker {
    public void work() {
        System.out.println("plain: work");
    }
}
~~~

**`src/main/java/demo/ltw/service/internal/SkippedWorker.java`**

~~~java
package demo.ltw.service.internal;

public class SkippedWorker {
    public void work() {
        System.out.println("skipped: work");
    }
}
~~~

`SkippedWorker` nằm trong `demo.ltw.service.internal`, match bộ lọc include của package cha nhưng cũng match bộ lọc exclude; exclude sẽ thắng đối với target này.

### 6.4. Spring configuration và chương trình chạy

**`src/main/java/demo/ltw/DemoApplication.java`**

~~~java
package demo.ltw;

import demo.ltw.service.PlainWorker;
import demo.ltw.service.WorkService;
import demo.ltw.service.internal.SkippedWorker;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DemoApplication {

    @Bean
    WorkService workService() {
        return new WorkService();
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(DemoApplication.class)) {

            WorkService service = context.getBean(WorkService.class);
            System.out.println("spring AOP proxy=" + AopUtils.isAopProxy(service));

            service.work();
            new PlainWorker().work();
            new SkippedWorker().work();
        }
    }
}
~~~

`AopUtils.isAopProxy(service)` trong chương trình kiểm tra **Spring AOP proxy**, không kiểm tra xem bytecode class đã được AspectJ weave hay chưa. Vì ta không bật Spring AOP auto-proxy creator, nó trả `false` ngay cả khi AspectJ LTW đã advice method `work()`.

### 6.5. META-INF/aop.xml cho ví dụ

**`src/main/resources/META-INF/aop.xml`**

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<aspectj>
    <aspects>
        <aspect name="demo.ltw.aspect.WorkAuditAspect"/>
    </aspects>

    <weaver options="-showWeaveInfo">
        <include within="demo.ltw.service..*"/>
        <exclude within="demo.ltw.service.internal..*"/>
    </weaver>
</aspectj>
~~~

Điều quan trọng nhất là **`WorkService` và `PlainWorker` được include**, còn `SkippedWorker` bị exclude. Nếu thiếu tệp này trên runtime classpath, javaagent có thể được khởi động nhưng không biết aspect hoặc phạm vi mà bạn mong muốn.

### 6.6. Gradle và AspectJ javaagent

**`build.gradle`**

~~~groovy
plugins {
    id 'java'
    id 'application'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

configurations {
    aspectjAgent
}

dependencies {
    implementation 'org.springframework:spring-context:6.1.14'
    implementation 'org.springframework:spring-aop:6.1.14'
    implementation 'org.aspectj:aspectjrt:1.9.22.1'

    aspectjAgent 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'demo.ltw.DemoApplication'
}

tasks.named('run', JavaExec) {
    doFirst {
        jvmArgs('-javaagent:' + configurations.aspectjAgent.singleFile.absolutePath)
    }
}
~~~

`aspectjAgent` là dependency configuration riêng chứa đúng agent JAR. `run` truyền `-javaagent:...` tới **JVM của DemoApplication**. Source của aspect annotation được `javac` biên dịch; weaver áp advice khi target class được load, không yêu cầu chạy AspectJ compiler `ajc` riêng trước demo này.

Đây là cấu hình minh họa trên Java 17; khi dùng Spring Boot hiện hữu, nên căn theo BOM và chọn phiên bản AspectJ tương thích bytecode/JDK đang dùng. Không ghép một version Spring riêng nếu ứng dụng đã có dependency management.

Chạy trên macOS/Linux:

~~~bash
./gradlew run
~~~

Hoặc Windows:

~~~powershell
.\gradlew.bat run
~~~

**Output của các `System.out.println` trong chương trình**, bỏ qua log Gradle và diagnostic weaving:

~~~text
spring AOP proxy=false
ltw: before WorkService
service: work
ltw: before PlainWorker
plain: work
skipped: work
~~~

Giải thích:
1. `WorkService` là Spring-managed bean, **không cần** Spring AOP proxy vẫn nhận advice khi class được LTW weave.
2. `PlainWorker` không do Spring tạo nhưng class được include, nên nhận advice khi gọi method.
3. `SkippedWorker` có method phù hợp với pointcut nhưng bị loại khỏi weaving, vì thế chỉ in `skipped: work`.
4. Nếu loại bỏ javaagent rồi chạy lại sau khi build bình thường bằng `javac`, các dòng `ltw: before ...` sẽ không có. Chỉ đặt `aop.xml` trên classpath không đủ để biến class thành woven class.

## 7. Thử các cấu hình để thấy phạm vi tác động

### 7.1. Bỏ exclude

Đổi phần `<weaver>` trong tệp XML ở trên thành:

~~~xml
<weaver options="-showWeaveInfo">
    <include within="demo.ltw.service..*"/>
</weaver>
~~~

Đây là **đoạn thay thế**, không phải file XML hoàn chỉnh. Khi bỏ exclude, `SkippedWorker` đủ điều kiện weaving; nếu pointcut match như ví dụ, sẽ có:

~~~text
ltw: before SkippedWorker
skipped: work
~~~

Không mặc định mọi subclass/package đều có advice khi chỉ thay filter; pointcut vẫn phải khớp đúng join point.

### 7.2. Chỉ include WorkService

~~~xml
<weaver options="-showWeaveInfo">
    <include within="demo.ltw.service.WorkService"/>
</weaver>
~~~

Khi đó `PlainWorker` và `SkippedWorker` không ở phạm vi weaver; từ góc nhìn advice method `work()`, chỉ `WorkService` còn ứng viên nhận weaving. Pointcut của aspect giữ nguyên hoàn toàn.

### 7.3. Xóa tên aspect trong danh sách <aspects>

Nếu ứng dụng chỉ có tệp `aop.xml` này và không còn khai báo aspect từ nơi khác, việc bỏ dòng `<aspect name="demo.ltw.aspect.WorkAuditAspect"/>` đồng nghĩa weaver không được khai báo sử dụng aspect ấy; vì vậy sẽ không có dòng advice demo. Đừng dùng **Spring bean name** để thay thế fully qualified aspect class name.

## 8. Spring @EnableLoadTimeWeaving và javaagent: không phải một annotation

Spring Framework có `@EnableLoadTimeWeaving` và `LoadTimeWeaver` để phối hợp bytecode transformation qua classloader hoặc Java instrumentation của môi trường. Nó khác `@EnableAspectJAutoProxy`:

- `@EnableAspectJAutoProxy` tạo **Spring AOP proxy** cho Spring beans dựa trên advisor.
- `@EnableLoadTimeWeaving` yêu cầu Spring kích hoạt/load-time weaving infrastructure, với điều kiện môi trường có instrumentable classloader hoặc instrumentation phù hợp.
- `-javaagent:aspectjweaver.jar` kích hoạt **AspectJ LTW ở mức JVM agent** khi process khởi động.
- Dù kích hoạt bằng cơ chế nào, aspect/type filters của AspectJ vẫn lấy từ cấu hình phù hợp (thường `META-INF/aop.xml`).

Ví dụ annotation Spring dành cho môi trường có `LoadTimeWeaver` tương thích:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableLoadTimeWeaving;

@Configuration
@EnableLoadTimeWeaving(
    aspectjWeaving = EnableLoadTimeWeaving.AspectJWeaving.ENABLED
)
public class WeavingConfiguration {
}
~~~

Đây chỉ là **fragment cấu hình Spring**, không phải project runnable độc lập. Có thể cần `spring-instrument` agent hoặc instrumentation được application server cung cấp; annotation riêng lẻ không bảo đảm weave được những class đã load quá sớm. Không bật chồng nhiều transformer mà chưa chứng minh semantics và kiểm tra classloader.

Với JVM app đơn giản, agent AspectJ riêng như mục 6 thường dễ minh họa. Với app server hoặc nhiều application trong một JVM, Spring-enabled LTW có thể cung cấp ranh giới theo classloader tốt hơn, nhưng đòi hỏi kiểm chứng trên runtime đích.

## 9. Liên hệ Spring transaction và các aspect của Spring

### 9.1. @Transactional: proxy mode và AspectJ mode

`@Transactional` trong Spring thường dựa vào interceptor/advisor quanh Spring bean proxy. Vì vậy self-invocation không đi qua proxy sẽ không kích hoạt transaction advice dạng proxy.

Spring cũng hỗ trợ lựa chọn `AdviceMode.ASPECTJ` cho transaction management:

~~~java
@Configuration
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
public class TransactionConfiguration {
}
~~~

Đoạn trên là **snippet**, cần import `Configuration`, `EnableTransactionManagement` và `AdviceMode` cùng các bean transaction manager phù hợp. AspectJ mode đòi hỏi thêm **`spring-aspects`**, khả năng weaving thật sự và cấu hình phù hợp. Chỉ thêm annotation hoặc file `aop.xml` không đủ để transaction tự hoạt động trên mọi object.

Spring Framework cung cấp một số AspectJ aspect trong JAR `spring-aspects`; JAR này có `META-INF/aop.xml` khai báo các aspect liên quan đến transaction, configuration, caching hoặc một số cơ chế khác tùy phiên bản. **File ứng dụng có thể cùng tồn tại với file trong spring-aspects**, vì AspectJ hợp nhất cấu hình theo classloader.

### 9.2. @Configurable cho object không phải Spring bean

`@Configurable` và `AnnotationBeanConfigurerAspect` có thể giúp Spring cấu hình dependency cho object được tạo ngoài container **khi đã chuẩn bị đầy đủ** weaving và các tích hợp cần thiết. Nhưng `new PlainWorker()` trong demo chỉ được aspect log intercept; nó không tự biến thành singleton bean, cũng không tự nhận dependency injection.

Nếu nhu cầu chỉ là cross-cutting logging/service metrics trên Spring beans, Spring AOP proxy thường đủ và đơn giản hơn. Dùng LTW khi thật sự cần semantics mà proxy không hỗ trợ, đồng thời xác định trước tập target class.

### 9.3. Khi hai cơ chế cùng tham gia

Nếu Spring AOP proxy bao một target class đã được AspectJ weave:
- Lời gọi từ bên ngoài đi qua Spring proxy có thể kích hoạt **cả** proxy advice lẫn woven advice khi method target thực thi.
- Lời gọi `this.method()` bên trong target bỏ qua proxy nhưng method execution được woven vẫn có thể chịu AspectJ advice.
- Sắp thứ tự advice giữa hai tầng cần xác định đường thực thi thực tế. `@Order` trong Spring AOP không phải công tắc tổng quát cho thứ tự của AspectJ weaver.
- Tránh cấu hình cùng một aspect như bean Spring **và** AspectJ woven aspect chỉ vì thấy hai cách đều khả dụng.

## 10. Khắc phục các lỗi thường gặp

| Triệu chứng | Nguyên nhân khả dĩ | Kiểm tra/khắc phục |
| --- | --- | --- |
| Có `aop.xml` nhưng không thấy advice | JVM thiếu weaving agent hoặc classloader LTW chưa hoạt động | Kiểm tra JVM options của **process chạy ứng dụng** |
| Agent có chạy nhưng weaver không thấy aspect | File không nằm trong `META-INF/aop.xml` trên runtime classpath hoặc khai báo `<aspect name>` sai | Kiểm tra resource/JAR và fully qualified aspect class |
| Aspect được đăng ký nhưng class không được weave | Include/exclude không match hoặc class đã load sớm | Xem weaver info, type pattern, thứ tự load |
| Spring bean có aspect mà `new` object không có | Chỉ dùng Spring AOP proxy | Kiểm tra `AopUtils.isAopProxy` và LTW thật sự |
| Class bị weave quá nhiều | Include quá rộng hoặc file `aop.xml` khác cùng hiệu lực | Rà soát tất cả config weaver mà classloader nhìn thấy |
| Lặp advice/log | Aspect vừa được Spring proxy vừa được AspectJ weave | Tách rõ hai cơ chế và scope |
| Chạy được trong IDE nhưng không ở container | Thiếu agent option ở container/Boot launcher hoặc khác classloader | Kiểm tra runtime image, startup args và resource packaging |
| Không weave được bytecode Java mới | AspectJ version cũ không hỗ trợ class-file version | Cập nhật AspectJ phù hợp JDK/compiler target |
| AspectJ advice chạy cả trong self-invocation nhưng Spring advice thì không | Cơ chế bytecode weaving khác proxy boundary | Đánh giá behavior theo từng cơ chế |
| Có spring-aspects nhưng transaction không chạy như dự định | Thiếu mode AspectJ, transaction manager hoặc weaving | Kiểm tra Spring config, agent, aspect list và transaction semantics |

Thứ tự điều tra được khuyến nghị:
1. Xác nhận yêu cầu là **Spring AOP proxy** hay **AspectJ LTW**.
2. Xác nhận JVM agent hoặc Spring/container weaving infrastructure được kích hoạt **trước lúc class load**.
3. Kiểm tra `META-INF/aop.xml` tồn tại trên runtime classpath, kể cả dependency JAR.
4. Kiểm tra aspect name trong `<aspects>` và khả năng load aspect class.
5. Bật `-showWeaveInfo`; xác định class nào được weave và class nào bị bỏ qua.
6. Xác nhận cả weaver type pattern lẫn aspect pointcut.
7. Chạy một trường hợp dương tính (type include) và âm tính (type exclude) như demo.
8. Sau cùng kiểm tra compatibility JDK, Spring, AspectJ, classloader và packaging.

Trong production, hạn chế để `-verbose`/`-debug` vĩnh viễn vì có thể tạo log lớn. Cũng không dùng `aop.xml` như biện pháp security sandbox hoặc kiểm soát truy cập: nó chỉ điều khiển weaving trong những giới hạn AspectJ hỗ trợ.

## 11. Câu hỏi tự kiểm tra

**1. `@EnableAspectJAutoProxy` có tự kích hoạt AspectJ LTW không?**

Không. Nó bật Spring AOP proxy dựa trên `@AspectJ` style. LTW yêu cầu agent hoặc infrastructure load-time weaving.

**2. Có thể weave một object được tạo bằng `new` nếu class không phải Spring bean không?**

Có, nếu class đã được weave đúng cách và pointcut match. Weaving tác động vào class, không yêu cầu object được Spring quản lý.

**3. `<include within="demo.ltw.service..*"/>` bên dưới `<aspects>` có cùng ý nghĩa với bên dưới `<weaver>` không?**

Không. Bên trong `<aspects>` nó lọc **aspect**; bên trong `<weaver>` nó lọc **target class được weave**.

**4. Vì sao `SkippedWorker.work()` không có log advice dù signature match pointcut?**

Class nằm dưới `demo.ltw.service.internal` bị loại bởi `<exclude>` của weaver.

**5. Nếu nhiều dependency có `META-INF/aop.xml` thì chỉ file ứng dụng được dùng?**

Không. AspectJ có thể hợp nhất nhiều file nhìn thấy trong cùng weaving classloader, nên phạm vi hiệu lực có thể rộng hơn một file.

**6. Nếu class đã nạp trước khi agent được bật, chỉ sửa `aop.xml` lúc runtime có bảo đảm class đó thay đổi không?**

Không. LTW chuẩn áp dụng khi class được load; cần cấu hình sẵn từ lúc JVM/classloader khởi tạo.

**7. Chỉ thêm `@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)` đã đủ?**

Chưa. Cần transaction manager, `spring-aspects`, aspect registration và weaving tương thích với runtime.

**8. Có nên dùng include/exclude như cơ chế chắc chắn chặn inter-type declaration ngoài package không?**

Không. ITD có semantics riêng trong AspectJ; scope/filter cho regular advice không phải hàng rào bảo mật hoặc giới hạn tuyệt đối cho mọi ITD.

## 12. Tài liệu tham khảo

- [Spring Framework – Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html): Spring AOP so với AspectJ LTW và tích hợp weaver vào ứng dụng Spring.
- [AspectJ – Load-Time Weaving](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html): `META-INF/aop.xml`, phần `<aspects>`/`<weaver>`, nhiều file, include/exclude, agent và classloader.
- [AspectJ – Development Environment Guide](https://eclipse.dev/aspectj/doc/latest/devguide/devguide.html): các ranh giới type filter, compiler/weaver và ITD.
- [Spring Framework – AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): giới hạn Spring proxy và self-invocation.
- [Spring Framework – Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): lựa chọn mode proxy/AspectJ và transaction interception.
- [Spring Framework – spring-aspects aop.xml](https://github.com/spring-projects/spring-framework/blob/main/spring-aspects/src/main/resources/META-INF/aop.xml): ví dụ một JAR Spring khai báo aspect riêng cho AspectJ LTW.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-integration-008">spring-instrument, aspectjweaver và yêu cầu môi trường thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# `spring-instrument`, `aspectjweaver` và yêu cầu môi trường thực thi trong Spring AspectJ

## 1. Mục tiêu và phạm vi

Khi sử dụng AspectJ cùng Spring Framework, việc khai báo Maven/Gradle dependency chưa đủ để bật **load-time weaving (LTW)**. Cần xét riêng **engine xử lý bytecode**, **cơ chế cung cấp instrumentation**, **cấu hình Spring ApplicationContext**, **classpath/classloader** và **tham số khởi động JVM**.

Hai artifact thường bị nhầm:

- **`org.springframework:spring-instrument`** cung cấp Java agent `spring-instrument.jar`. Agent này làm cho `java.lang.instrument.Instrumentation` khả dụng đối với hỗ trợ LTW của Spring trong các JVM thông thường. Nó **không tự phân tích pointcut AspectJ và không tự weave aspect**.
- **`org.aspectj:aspectjweaver`** chứa AspectJ weaver và các thành phần runtime liên quan; JAR này còn có thể hoạt động như **Java agent của AspectJ**. Đây mới là engine thực hiện weaving dựa trên khai báo trong `META-INF/aop.xml`.

Bài tập trung vào cách **Spring tích hợp AspectJ**: khi nào cần artifact nào, cấu hình thế nào, làm sao chạy với Java 17+, và cách chẩn đoán lỗi trong IDE, máy chủ, Docker hoặc ứng dụng Spring Boot. Cú pháp pointcut và khái niệm AOP tổng quát không phải trọng tâm.

## 2. Chọn đúng artifact theo runtime

| Cơ chế | Kích hoạt | Vai trò thư viện |
| --- | --- | --- |
| Spring `@AspectJ` auto-proxy | Spring tạo proxy cho Spring bean | `spring-aop` + AspectJ pointcut parser; không cần agent |
| Spring-managed AspectJ LTW | `@EnableLoadTimeWeaving`/XML đăng ký transformer | `spring-context`, `spring-aop`, `aspectjweaver`, `aop.xml`; JVM thường có thể cần `spring-instrument.jar` agent |
| AspectJ agent trực tiếp | `-javaagent:aspectjweaver.jar` lúc JVM khởi động | AspectJ vừa làm agent vừa thực hiện weaving, đọc `aop.xml` |
| CTW/post-compile weaving | `ajc` xử lý artifact ở build | Bytecode đã weave + runtime dependencies; không mặc định cần agent |

`spring-aspects` là **tập aspect tích hợp Spring** (`@Configurable`, transaction, caching, async), không phải JAR instrumentation của Spring. Ví dụ bài này chọn tuyến **Spring-managed LTW với spring-instrument** để làm rõ điều kiện hạ tầng.

## 3. Từ Spring agent đến advice được weave

```text
JVM: -javaagent:spring-instrument.jar
 -> Spring InstrumentationSavingAgent công bố Instrumentation
 -> Spring ApplicationContext bật @EnableLoadTimeWeaving hoặc XML
 -> LoadTimeWeaver đăng ký AspectJ ClassFileTransformer
 -> AspectJ đọc META-INF/aop.xml và type filters
 -> JVM định nghĩa target class sau khi transformer sẵn sàng
 -> Woven join point chạy advice
```

Spring chịu trách nhiệm **đăng ký transformer**, còn AspectJ `ClassPreProcessorAgentAdapter` chịu trách nhiệm **biến đổi class bytes**. Container có instrumentable ClassLoader riêng có thể không cần Spring agent. Nếu dùng AspectJ agent trực tiếp, tránh bật thêm tuyến Spring LTW trên cùng tập class khi không có lý do rõ ràng.

Có bean `loadTimeWeaver` hoặc có JAR trong dependency tree chưa chứng minh một method đã weave. Kiểm tra chính JVM chạy ứng dụng, ClassLoader định nghĩa target, aop.xml, `-showWeaveInfo` và test behavior thật.

## 4. Dependency và yêu cầu runtime

| Artifact / cấu hình | Vai trò | Khi nào bắt buộc |
| --- | --- | --- |
| `spring-context` | `ApplicationContext`, annotation/XML cho LTW | Khi cấu hình LTW thông qua Spring |
| `spring-aop` | Hạ tầng hỗ trợ Spring AOP/LTW; được tài liệu Spring liệt kê | Khi dùng hỗ trợ AspectJ LTW của Spring |
| `aspectjweaver` | Class transformer/weaving của AspectJ và runtime liên quan | Khi thực sự chạy AspectJ LTW |
| `spring-instrument` | Agent cấp `Instrumentation` cho Spring | Khi chọn tuyến Spring agent trong JVM thông thường |
| `spring-aspects` | Các aspect Spring cung cấp sẵn (`@Configurable`, transaction AspectJ mode, v.v.) | Chỉ khi dùng các aspect đó |
| `META-INF/aop.xml` | Danh sách aspect và tập class được weave | Đối với cấu hình LTW AspectJ minh họa trong bài |

Checklist môi trường:

- **JDK** phải tương thích với các phiên bản Spring/AspectJ được sử dụng; riêng ví dụ dùng Java 17 và Spring Framework 6.2.x.
- **JAR trên classpath** phải được runtime classloader nhìn thấy. Với Java agent, file JAR còn phải tồn tại ở đường dẫn thật mà lệnh khởi động JVM tham chiếu.
- **`aop.xml`** phải ở `META-INF/aop.xml` trong classpath (hoặc trong JAR được runtime tải). Aspect được biên dịch và tên lớp phải đúng.
- **Context** phải kích hoạt Spring LTW trước khi target được nạp.
- **Classloader** phải cho phép đăng ký transformer qua tích hợp chuyên biệt, reflection fallback thích hợp hoặc `InstrumentationLoadTimeWeaver`.
- **JVM option** `-javaagent:...` phải được truyền cho **chính process thực thi ứng dụng/test**, đặt trước `-cp`, tên main class hoặc `-jar`; dependency Maven không thể thay thế JVM option.
- Đối với **Spring Boot fat JAR**, Docker image hoặc Kubernetes, cần xác nhận agent JAR, `aop.xml` và AspectJ dependencies thực sự có thể truy cập từ ứng dụng lúc chạy; không chỉ kiểm tra classpath IDE.

Tài liệu Spring liệt kê `spring-aop.jar` và `aspectjweaver.jar` là thành phần tối thiểu cho Spring-managed AspectJ LTW, và thêm `spring-instrument.jar` khi sử dụng Spring agent.

## 5. Ví dụ đầy đủ: Spring-managed AspectJ LTW với Java 17+

Ví dụ dùng **Spring 6.2.14**, **AspectJ 1.9.24** và Java 17+. Ta chọn XML Spring context để dễ thấy thời điểm Spring đăng ký transformer trước lúc target `PaymentService` được nạp. Aspect dùng cú pháp `@Aspect` nhưng được **AspectJ weaver** xử lý qua `aop.xml`, không cần bật Spring proxy AOP.

### 5.1. Cấu trúc project

```text
spring-ltw-demo/
├── pom.xml
└── src/main/
    ├── java/demo/
    │   ├── Main.java
    │   ├── PaymentService.java
    │   └── TraceAspect.java
    └── resources/
        ├── beans.xml
        └── META-INF/aop.xml
```

### 5.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-ltw-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.version>6.2.14</spring.version>
        <aspectj.version>1.9.24</aspectj.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>${spring.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>${spring.version}</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>${aspectj.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-instrument</artifactId>
            <version>${spring.version}</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```

Khai báo `spring-instrument` ở đây giúp Maven tải JAR để minh họa cách chạy. Điều quyết định đối với Java agent vẫn là **`-javaagent:đường-dẫn-spring-instrument.jar`**, không phải việc JAR nằm trong dependency list.

### 5.3. `src/main/java/demo/TraceAspect.java`

```java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(void demo.PaymentService.pay())")
    public void beforePayment() {
        System.out.println("[woven] before payment");
    }
}
```

Java compiler biên dịch `@Aspect` class. AspectJ LTW cần `aop.xml` bên dưới để nhận diện class này làm aspect và weave target thích hợp.

### 5.4. `src/main/java/demo/PaymentService.java`

```java
package demo;

public class PaymentService {
    public void pay() {
        System.out.println("payment executed");
    }
}
```

Không cần interface hay Spring proxy đặc biệt cho target này. AspectJ LTW thay đổi bytecode lúc class được định nghĩa.

### 5.5. `src/main/resources/META-INF/aop.xml`

```xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN"
        "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <weaver>
        <include within="demo.PaymentService"/>
    </weaver>
    <aspects>
        <aspect name="demo.TraceAspect"/>
    </aspects>
</aspectj>
```

`<aspects>` liệt kê class aspect theo **fully qualified name**. `<include within="..."/>` xác định lớp mục tiêu được phép weave, giúp giảm tác dụng phụ và chi phí. Trong Maven, tài nguyên sẽ xuất hiện tại `target/classes/META-INF/aop.xml`.

Ở ví dụ này không khai báo `TraceAspect` là Spring bean và không bật `@EnableAspectJAutoProxy`. AspectJ runtime quản lý việc tạo instance aspect LTW; đăng ký cùng một aspect thêm lần nữa qua Spring AOP có thể khiến behavior bị áp dụng trùng.

### 5.6. `src/main/resources/beans.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <context:load-time-weaver aspectj-weaving="on"/>
    <bean id="paymentService" class="demo.PaymentService"/>
</beans>
```

`aspectj-weaving="on"` bật Spring-managed AspectJ LTW. Nếu không khai báo thuộc tính này, mặc định là `autodetect`: Spring chỉ bật AspectJ weaving nếu tìm được `META-INF/aop.xml`.

### 5.7. `src/main/java/demo/Main.java`

```java
package demo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {
    public static void main(String[] args) throws Exception {
        try (var context = new ClassPathXmlApplicationContext("beans.xml")) {
            System.out.println("Spring-managed object:");
            Object bean = context.getBean("paymentService");
            bean.getClass().getMethod("pay").invoke(bean);

            // Tạo object bên ngoài container sau khi Spring đăng ký LTW.
            System.out.println("Non-Spring object:");
            Class<?> target = Class.forName("demo.PaymentService");
            Object external = target.getConstructor().newInstance();
            target.getMethod("pay").invoke(external);
        }
    }
}
```

Ví dụ cố ý dùng **reflection** để tránh tham chiếu trực tiếp `PaymentService` từ `Main` quá sớm. Khi Spring đã đăng ký transformer và target mới được tải, cả bean từ context lẫn instance tạo bên ngoài context đều dùng class đã được weave.

### 5.8. Build và chạy

Trong thư mục chứa `pom.xml`, dùng Maven để biên dịch rồi chép runtime dependencies vào `target/dependency`:

```bash
mvn -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime
```

**Linux/macOS:**

```bash
java -javaagent:target/dependency/spring-instrument-6.2.14.jar \
     -cp "target/classes:target/dependency/*" demo.Main
```

**Windows PowerShell:**

```powershell
java -javaagent:target/dependency/spring-instrument-6.2.14.jar -cp "target/classes;target/dependency/*" demo.Main
```

Dấu `:` phân tách classpath Unix, còn `;` dùng trên Windows. Java hỗ trợ wildcard `*` cho các JAR trực tiếp trong thư mục `target/dependency`.

**Stdout mong đợi** (không kể các log framework nếu môi trường in thêm):

```text
Spring-managed object:
[woven] before payment
payment executed
Non-Spring object:
[woven] before payment
payment executed
```

Hai dòng `[woven]` xác minh weaving đã ảnh hưởng đến đối tượng Spring và object được tạo bên ngoài Spring container. Nếu JVM thiếu Spring agent và classloader không hỗ trợ LTW, quá trình khởi tạo context có thể thất bại vì không đăng ký được transformer. Nếu tên aspect, pointcut hay `aop.xml` sai, có thể ứng dụng chạy nhưng advice không xuất hiện.

**Giới hạn kiểm chứng:** đây là mã mẫu tự chứa, thiết kế cho Java 17+/Maven. Kết quả được suy ra từ cấu hình và code; cần thực sự chạy trên môi trường muốn sử dụng để xác nhận tương thích JDK, AspectJ và classloader.

## 6. Bật LTW bằng Java Configuration

Có thể thay XML bằng cấu hình annotation:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableLoadTimeWeaving;

@Configuration
@EnableLoadTimeWeaving(
    aspectjWeaving = EnableLoadTimeWeaving.AspectJWeaving.ENABLED
)
public class WeavingConfig {
}
```

Đây là **cấu hình thay thế**, không phải một file cần bổ sung bắt buộc vào ví dụ XML. `WeavingConfig` phải được nạp trong `ApplicationContext` trước khi target được tải.

| Java option | XML option | Hành vi |
| --- | --- | --- |
| `AspectJWeaving.ENABLED` | `aspectj-weaving="on"` | Yêu cầu Spring bật AspectJ LTW |
| `AspectJWeaving.DISABLED` | `aspectj-weaving="off"` | Tắt AspectJ LTW do Spring kích hoạt |
| `AspectJWeaving.AUTODETECT` | `aspectj-weaving="autodetect"` | Bật nếu tìm được ít nhất một `META-INF/aop.xml`; mặc định |

Spring đăng ký `loadTimeWeaver` trong context và chọn implementation tùy môi trường. Nếu cần override, dùng `LoadTimeWeavingConfigurer#getLoadTimeWeaver()` hoặc thuộc tính `weaver-class` của XML. Điều này hữu ích trong runtime có classloader riêng, nhưng không nên tùy tiện chọn một implementation không hỗ trợ môi trường.

## 7. Cách chạy khác: dùng agent của AspectJ trực tiếp

AspectJ weaver có Java agent riêng. Ví dụ lệnh trên Linux/macOS:

```bash
java -javaagent:target/dependency/aspectjweaver-1.9.24.jar \
     -cp "target/classes:target/dependency/*" demo.Main
```

Trong tuyến này **AspectJ agent tự đăng ký transformer** và tìm `META-INF/aop.xml`; không cần `spring-instrument.jar` để bật chính AspectJ agent. Tuy nhiên, đoạn `beans.xml` ở ví dụ trước **đang bật Spring LTW**, nên trước khi chuyển sang tuyến trực tiếp cần bỏ dòng `<context:load-time-weaver aspectj-weaving="on"/>` khỏi `beans.xml`. Khi đó Spring chỉ tạo bean, còn AspectJ agent thực hiện LTW ở JVM startup. Với Windows, thay dấu `:` giữa các path bằng `;`.

Không nên dùng nguyên xi cả hai agent cho cùng ví dụ. Chọn tuyến nào còn tùy phạm vi deployment:

- **Spring-managed LTW:** tích hợp với Spring ApplicationContext; có thể kích hoạt theo từng classloader, phù hợp một số application server nhiều ứng dụng.
- **AspectJ agent trực tiếp:** cấu hình agent tại lúc JVM khởi động, thường dễ hình dung với tiến trình ứng dụng chạy độc lập.

Nếu thực sự cần cả hai cơ chế do thành phần thứ ba, cần đánh giá rõ thứ tự transformer, lớp nào do ai xử lý và nguy cơ weave trùng.

## 8. Spring Boot, container và các JVM thực tế

Với một **Spring Boot executable JAR** dùng Spring-managed LTW, hình thức lệnh khởi động là:

```bash
java -javaagent:/opt/agents/spring-instrument.jar -jar app.jar
```

Đường dẫn `/opt/agents/spring-instrument.jar` là ví dụ; phải thay bằng file **thực sự tồn tại**. Spring Boot vẫn phải đăng ký `@EnableLoadTimeWeaving`/XML, có AspectJ weaver và `META-INF/aop.xml`/aspect classes trong runtime classpath. `-javaagent` riêng lẻ không tự kích hoạt các aspect.

| Môi trường | Cần kiểm tra | Lỗi dễ gặp |
| --- | --- | --- |
| Ứng dụng Java/Spring Boot chạy standalone | Đúng JVM option, agent JAR, thư viện và context | Đặt `-javaagent` sau `-jar`, đường dẫn agent không tồn tại |
| Tomcat/JBoss/WildFly | Hỗ trợ LTW của classloader do server cung cấp, isolation theo ứng dụng | Cho rằng mọi phiên bản container tự hỗ trợ hoặc target load trước Spring |
| IDE Run Configuration | JVM options của đúng cấu hình chạy | Chỉ thêm JAR vào classpath, không khởi tạo instrumentation |
| Maven Surefire/Gradle Test | JVM test fork có agent khi integration test dùng LTW | Agent chạy ở JVM build nhưng test fork không có agent |
| Docker/Kubernetes | Image chứa JAR, entrypoint/args truyền option vào JVM, resource được đóng gói | Lệnh start mới ghi đè `JAVA_TOOL_OPTIONS` hoặc thiếu JAR |
| Ứng dụng dùng classloader riêng/JPMS | Quyền truy cập class, module openness, JDK và AspectJ bytecode compatibility | Transformer không thấy class hoặc bytecode version không được hỗ trợ |

Theo tài liệu Spring, Tomcat và JBoss/WildFly có thể cung cấp classloader cho phép Spring cấu hình LTW **không cần** Spring agent trong một số deployment. Nhưng cần kiểm tra phiên bản server và classloader cụ thể; với Java app chung, Spring agent là giải pháp phổ biến hơn. Các implementation được Spring lựa chọn gồm `InstrumentationLoadTimeWeaver` và những implementation phù hợp từng container.

Trong hệ thống nhiều `ApplicationContext` hoặc nhiều ứng dụng chung JVM, đặt `spring-aspects`/aspect class ở tầng classloader dùng chung có thể tạo khác biệt về vòng đời/scope aspect. Nên xác định ranh giới classloader và nơi phân phối JAR trước khi bật LTW trên production.

Với Java modules (JPMS) hoặc JDK mới, nếu gặp lỗi truy cập lớp, hãy đọc stack trace và đối chiếu Spring/AspectJ/JDK đang chạy. Chỉ thêm `--add-opens` cho module/package cụ thể khi thực sự được yêu cầu; không bật hàng loạt JVM flags không có căn cứ.

## 9. Quan hệ với `spring-aspects`, transaction và `@Configurable`

`spring-aspects` **không phải** artifact chứa Java agent Spring. Nó là thư viện đóng gói một số aspect AspectJ do Spring cung cấp:

- `AnnotationBeanConfigurerAspect` có thể kết hợp với `@Configurable` để Spring cấu hình object được tạo **bên ngoài container**, sau khi class liên quan đã được weave.
- `AnnotationTransactionAspect` hỗ trợ transaction trong **AspectJ mode**, có thể dùng khi muốn hỗ trợ join point ngoài giới hạn Spring AOP proxy.

Cần tải `spring-aspects` nếu dùng các chức năng đó, và cấu hình tương ứng (chẳng hạn `@EnableSpringConfigured` cho use case `@Configurable`) cùng weaving phù hợp. Không sử dụng `@Configurable` trên chính bean đã được Spring quản lý nếu điều đó dẫn tới cấu hình/injection hai lần.

`@Transactional` mặc định ở nhiều ứng dụng Spring chạy theo cơ chế proxy; annotation này không đồng nghĩa AspectJ weaving được bật. Việc chuyển sang AspectJ mode cần hiểu rõ transaction aspect và chính sách proxy/weaving của Spring.

Một chức năng khác như JPA class enhancement có thể sử dụng `LoadTimeWeaver`, nhưng không vì thế mà mọi Spring JPA application cần `aspectjweaver` hay AspectJ `aop.xml`. Hãy phân biệt nhu cầu JPA transformation với nhu cầu AspectJ advice.

## 10. Lỗi phổ biến và hướng xử lý

| Biểu hiện | Nguyên nhân có thể | Cách xác minh / khắc phục |
| --- | --- | --- |
| Bean có `@Aspect`, nhưng advice không chạy với object ngoài container | Chỉ bật `@EnableAspectJAutoProxy` (Spring proxy AOP) | Nếu cần LTW, cấu hình Spring LTW hoặc AspectJ agent và `aop.xml` |
| `ClassLoader does NOT provide an 'addTransformer' method` | Spring không có classloader hỗ trợ và không nhận instrumentation | Kiểm tra agent của Spring trong **process JVM thực**, không chỉ dependency |
| `ClassNotFoundException` / `NoClassDefFoundError` chứa class AspectJ | `aspectjweaver` thiếu hoặc ở sai classloader | Xem runtime classpath, Maven dependency tree, JAR đóng gói |
| Có agent, không có advice | Thiếu/sai `META-INF/aop.xml`, sai FQCN aspect/target hoặc pointcut không khớp | Kiểm tra `target/classes/META-INF/aop.xml`, `TraceAspect.class`, tên class |
| Có `aop.xml` nhưng một số class không weave | Class được nạp quá sớm hoặc include lọc sai | Kiểm tra thứ tự bootstrap Spring/class loading và `include within` |
| Chạy ở IDE, lỗi khi deploy | JVM argument không được truyền đến process server, đường dẫn JAR sai | So sánh command line JVM thực tế và artifact trong image |
| `Unsupported class file major version` | Version AspectJ không tương thích bytecode/JDK | Chọn AspectJ version hỗ trợ JDK đang chạy; đối chiếu với Spring |
| Advice bị gọi hai lần | Kết hợp Spring proxy aspect và AspectJ LTW hoặc hai tuyến agent xử lý trùng | Rà soát các cơ chế auto-proxy, LTW, aspect beans và agent |
| Cấu hình transaction không có hiệu lực như mong đợi | Nhầm proxy mode với AspectJ mode hoặc thiếu `spring-aspects` khi dùng aspect của Spring | Xác nhận transaction mode, aspect runtime, cấu hình annotation |

Trình tự debug gợi ý:

1. Kiểm tra `java -version`, Maven dependency versions và lệnh JVM **đang chạy**; xác nhận `-javaagent` đúng đường dẫn nếu chọn Spring agent.
2. Xác minh sự hiện diện của `aspectjweaver.jar`, `spring-aop.jar`, aspect class đã compile, `META-INF/aop.xml` trong artifact runtime.
3. Đối chiếu thứ tự khởi tạo `ApplicationContext`/`LoadTimeWeaver` trước khi target được nạp. Đừng dùng sự thành công của Spring startup làm bằng chứng đã weave.
4. Kiểm tra tên fully qualified của aspect, target và pointcut; chỉ bật weave diagnostics/verbose trong môi trường thử nghiệm khi cần, tránh dump bytecode rộng trên production.
5. Thực hiện test có lời gọi thật qua target. Nếu thử object tạo ngoài Spring cũng thấy advice, đó là tín hiệu hữu ích để phân biệt weaving với Spring proxy.

## 11. Checklist trước khi triển khai

- [ ] Đã xác định **một cơ chế**: Spring AOP proxy, Spring-managed AspectJ LTW hoặc AspectJ agent trực tiếp.
- [ ] Đã kiểm tra tính tương thích Java–Spring–AspectJ và runtime dependencies.
- [ ] Đã đặt `spring-instrument.jar` ở đường dẫn đúng nếu dùng Spring agent.
- [ ] JVM chạy app/test thật có `-javaagent` đúng vị trí trong command line.
- [ ] Spring LTW được bật trước khi nạp target (nếu chọn Spring-managed LTW).
- [ ] `META-INF/aop.xml` đúng vị trí, tên aspect/target chính xác và phạm vi weave đủ hẹp.
- [ ] Cấu hình container/classloader/JVM option đã được kiểm chứng tại môi trường deploy.
- [ ] Đã chạy thử và xác minh advice trên đối tượng mong muốn, không có weave trùng.

**Kết luận:** `spring-instrument` là agent giúp Spring có đường đăng ký transformer trên JVM thích hợp, còn `aspectjweaver` là engine thực hiện AspectJ weaving. Runtime LTW chỉ hoạt động đáng tin khi **agent hoặc classloader hỗ trợ, cấu hình Spring, `aop.xml`, classpath và thời điểm tải lớp** được thiết lập nhất quán.

## 12. Tài liệu tham khảo

- [Spring Framework — Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html): phần *Load-time Weaving with AspectJ in the Spring Framework*, các JAR, `aop.xml`, agent, application server.
- [Spring Framework — Registering a LoadTimeWeaver](https://docs.spring.io/spring-framework/reference/core/beans/context-load-time-weaver.html): annotation và XML kích hoạt LTW.
- [Spring Framework — @AspectJ support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): Spring AOP proxy, phân biệt với AspectJ weaving.
- [Spring Framework 6.2.14 — Context Weaving Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/context/weaving/package-summary.html): `AspectJWeavingEnabler`, `DefaultContextLoadTimeWeaver`.
- [AspectJ Weaver trên Maven Central](https://central.sonatype.com/artifact/org.aspectj/aspectjweaver): weaver và Java agent của AspectJ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-integration-009">ClassLoader và thời điểm lớp được nạp khi dùng LTW</a>

<details>
<summary>Xem chi tiết</summary>

# ClassLoader và thời điểm lớp được nạp khi dùng LTW trong Spring

## 1. Tại sao ClassLoader và thời điểm nạp lớp quyết định việc AspectJ LTW có hiệu lực?

Trong ứng dụng Spring, **AspectJ Load-Time Weaving (LTW)** thay đổi bytecode của lớp **trong quá trình ClassLoader nạp/định nghĩa lớp vào JVM**. Nó khác Spring AOP dựa trên proxy: LTW đưa advice vào bytecode của lớp đích; vì vậy có thể áp dụng cho self-invocation, đối tượng không đi qua Spring proxy hoặc các join point phù hợp khác của AspectJ.

Vấn đề quan trọng nhất: **transformer phải tham gia đúng ClassLoader và được thiết lập trước khi lớp đích được JVM định nghĩa**. Nếu một lớp đã được định nghĩa trước khi transformer AspectJ hoạt động, việc bật LTW về sau thường sẽ không thêm advice vào phiên bản lớp đang chạy. Việc tạo bean mới, gọi lại method, refresh `ApplicationContext` hoặc thêm `@Aspect` không tự biến bytecode đã nạp thành woven bytecode.

Phân biệt rõ ba mốc:

1. **Loading/definition**: ClassLoader đọc bytecode và JVM định nghĩa `Class`; đây là cửa sổ LTW biến đổi bytecode.
2. **Initialization**: JVM chạy các static initializer (`<clinit>`) khi cần; sự kiện này có thể xảy ra sau loading.
3. **Spring bean instantiation/initialization**: Spring tạo object, inject dependency, gọi lifecycle hook; có thể xảy ra muộn hơn cả hai mốc trên.

Do đó, log trong `static { ... }` chỉ phản ánh **thời điểm khởi tạo lớp**, không phải phép đo chính xác thời điểm JVM đã định nghĩa lớp. Bean được đánh dấu `@Lazy` cũng không đảm bảo lớp Java tương ứng chưa được nạp.

### Hai timeline cần thuộc

~~~text
Đúng: AspectJ transformer sẵn sàng trước khi target class được định nghĩa

JVM start
  -> Agent/LoadTimeWeaver thiết lập transformer cho loader liên quan
  -> Loader nhìn thấy META-INF/aop.xml và aspect classes
  -> Loader lấy bytecode TargetService
  -> AspectJ transformer weave bytecode TargetService
  -> JVM defineClass(TargetService)
  -> Spring tạo TargetService bean
  -> Method chạy => advice chạy
~~~

~~~text
Sai: target class đã được định nghĩa trước khi Spring đăng ký transformer

JVM start
  -> Spring/ứng dụng/thư viện kích hoạt loading TargetService
  -> JVM defineClass(TargetService) với bytecode nguyên gốc
  -> Spring ApplicationContext sau đó mới bật LTW
  -> Spring tạo hoặc sử dụng TargetService bean
  -> Method chạy => advice từ LTW có thể không xuất hiện
~~~

**Giới hạn:** Đây là đặc tính của LTW tiêu chuẩn; một số công cụ instrumentation nâng cao có API retransformation/redefinition riêng, nhưng không nên giả định rằng cấu hình AspectJ LTW thông thường sẽ tự weave lại lớp đã nạp.

## 2. ClassLoader nào thực sự chịu trách nhiệm?

Java định danh một lớp bằng **tên đầy đủ + ClassLoader định nghĩa lớp đó**. Hai loader khác nhau có thể nạp hai phiên bản `com.example.service.OrderService` thành hai kiểu không đồng nhất. Điều này có ảnh hưởng trực tiếp khi Spring chạy trong servlet container, application server, plugin host, môi trường nhiều web app hoặc Spring Boot executable JAR.

Với AspectJ LTW, các điểm cần xác minh là:

- **Defining loader**: loader nào thật sự định nghĩa `TargetService`? Transformer mà chỉ được gắn vào child loader sẽ không weave được lớp đã được parent loader định nghĩa.
- **Delegation**: khi parent loader xử lý một class, child loader không được tự ý weave bytecode của class đó. Vì vậy, đặt class mục tiêu trong classpath dùng chung của server có thể vô tình chuyển quyền định nghĩa lớp sang loader khác.
- **Aspect visibility**: aspect được dùng để weave phải nhìn thấy được trong phạm vi loader đang weave, hoặc từ parent loader thích hợp. `META-INF/aop.xml` và aspect class phải khả dụng đúng ClassLoader/context.
- **Cấu hình riêng cho từng loader**: các web application triển khai trong một JVM có thể có loader riêng. Spring-managed LTW cho phép điều khiển ở phạm vi ClassLoader/ApplicationContext thay vì buộc mọi ứng dụng dùng cùng quy tắc weaving.
- **ClassLoader của Spring bean**: gọi `getClass().getClassLoader()` trên object có proxy có thể quan sát loader của **lớp proxy**, không nhất thiết loader định nghĩa lớp nghiệp vụ. Hãy kiểm tra trực tiếp `TargetService.class.getClassLoader()`, và lưu ý rằng việc tham chiếu class literal khi debug có thể tự làm JVM load/resolve lớp.

Ví dụ chẩn đoán Java (đặt tại nơi bạn thực sự cần kiểm tra):

~~~java
ClassLoader tccl = Thread.currentThread().getContextClassLoader();
ClassLoader targetLoader = com.example.service.TargetService.class.getClassLoader();

System.out.println("TCCL        : " + tccl);
System.out.println("TargetLoader: " + targetLoader);
System.out.println("Same loader : " + (tccl == targetLoader));
~~~

Đây là **đoạn minh họa** cần có `TargetService` trong dự án. `TCCL` là thread context ClassLoader, thường phục vụ resource/library lookup; nó **không mặc nhiên bằng** loader thực sự định nghĩa target. Chuỗi hiển thị/định danh loader thay đổi tùy trình chạy.

| Môi trường Spring | Điều thường phải kiểm tra |
| --- | --- |
| Spring Boot chạy `java -jar` | Launcher của Boot dùng cơ chế class loading cho `BOOT-INF/classes` và dependency JAR trong `BOOT-INF/lib`; xác định app loader thực tế và khả năng tìm `aop.xml` |
| WAR triển khai lên Tomcat hoặc WildFly | Web app loader hay shared/server loader định nghĩa target; lớp có được server scan/nạp trước khi Spring context bật weaving không |
| Nhiều `ApplicationContext` | Context nào đăng ký `LoadTimeWeaver`; liệu chúng có dùng chung loader/target class và aspect singleton không |
| Plugin, module hoặc classloader tùy biến | Loader có hỗ trợ thêm transformer hay không, quan hệ parent/child và việc thấy tài nguyên `META-INF/aop.xml` |
| Restart bằng cơ chế devtools/hot reload | Có lớp được nạp lại bởi loader mới hay không; không suy luận khả năng weave chỉ từ việc Spring bean được tạo lại |

## 3. Hai con đường kích hoạt LTW có khác nhau về thời điểm

### 3.1. AspectJ Java agent khi JVM bắt đầu

Cấu hình khởi động:

~~~bash
java -javaagent:/absolute/path/aspectjweaver.jar -jar application.jar
~~~

JVM gọi `premain` của AspectJ agent **trước** `main()` ứng dụng. Vì vậy transformer có cơ hội nhìn thấy các lớp ứng dụng được nạp sớm, kể cả trước khi Spring tạo `ApplicationContext`. Đây là cách dễ bảo đảm thứ tự cho ứng dụng Spring Boot chạy một JVM riêng; vẫn phải thỏa mãn điều kiện về ClassLoader, scope `aop.xml` và visibility của aspect.

`-javaagent` là **tùy chọn JVM**, cần đặt **trước** `-jar` hoặc tên main class. File `aspectjweaver.jar` phải tồn tại dưới dạng JAR độc lập trên filesystem tại thời điểm JVM khởi động; đừng trỏ trực tiếp đến một JAR con chỉ nằm sâu trong `BOOT-INF/lib`.

Agent hoạt động ở cấp JVM, nhưng không đồng nghĩa với việc tất cả lớp trong mọi loader đều bị weave. Loại class nào được can thiệp còn phụ thuộc classloader, giới hạn weaving và cấu hình AspectJ.

### 3.2. Spring-managed load-time weaving

Spring cung cấp `org.springframework.instrument.classloading.LoadTimeWeaver` để đăng ký `ClassFileTransformer` trong phạm vi ứng dụng/classloader thích hợp. Cấu hình Java:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableLoadTimeWeaving;

@Configuration
@EnableLoadTimeWeaving(
    aspectjWeaving = EnableLoadTimeWeaving.AspectJWeaving.ENABLED
)
public class LtwConfiguration {
}
~~~

Đây là **đoạn cấu hình đưa vào ứng dụng Spring**, không phải một chương trình độc lập. Cũng có thể dùng `<context:load-time-weaver/>` trong XML. Hạ tầng Spring sẽ thiết lập `LoadTimeWeaver`/`AspectJWeavingEnabler`; AspectJ `ClassPreProcessorAgentAdapter` là transformer thực hiện weaving.

Ba chế độ của `@EnableLoadTimeWeaving`:

| Giá trị | Ý nghĩa |
| --- | --- |
| `ENABLED` | Yêu cầu bật AspectJ weaving; vẫn cần loader/instrumentation, dependency và cấu hình aspect hoạt động |
| `DISABLED` | Không bật weaving bằng cơ chế Spring-managed này |
| `AUTODETECT` (mặc định) | Chỉ bật nếu Spring tìm thấy ít nhất một `META-INF/aop.xml` phù hợp |

**Điều kiện môi trường:** Một số app-server ClassLoader hỗ trợ đăng ký transformer trực tiếp. Với ứng dụng Java/Spring Boot thông thường, nếu loader không cung cấp khả năng đó, Spring có thể dùng `InstrumentationLoadTimeWeaver` khi JVM khởi động với `-javaagent:/path/to/spring-instrument.jar`. `spring-instrument.jar` cung cấp cầu nối instrumentation để Spring đăng ký transformer; nó **không phải** `aspectjweaver.jar` và không tự thay thế AspectJ weaver. Cần `aspectjweaver` trên classpath cùng `aop.xml`/aspect classes đúng phạm vi.

Thời điểm thiết lập trong phương án này gắn với vòng đời **ApplicationContext**. Nếu target đã bị loader định nghĩa ở đoạn `main()`, trong static initializer, bởi server scanning hoặc một thư viện trước khi `LoadTimeWeaver` được đăng ký, LTW có thể bỏ lỡ target. Chạy JVM với `spring-instrument.jar` từ đầu chỉ giúp có cơ chế đăng ký transformer; nó không tự đăng ký AspectJ transformer trước thời điểm Spring cấu hình.

**Không cấu hình trùng hai đường weaving một cách vô thức:** agent AspectJ trực tiếp và Spring-managed AspectJ LTW có thể cùng tham gia nếu bật cả hai. Chỉ chọn đường cần thiết và kiểm tra tránh advice chạy hai lần.

## 4. Ví dụ Spring Boot chạy được: chứng minh weaving xảy ra trước Spring context

Ví dụ chạy Java 21, Gradle và Spring Boot 3.3.x. Target được **chủ động nạp/khởi tạo trước khi Spring context tồn tại**. Khi dùng AspectJ agent từ lúc JVM start, advice vẫn chạy sau đó vì bytecode đã được weave đúng lúc.

Cấu trúc:

~~~text
ltw-classload/
├── settings.gradle
├── build.gradle
└── src/main/
    ├── java/com/example/ltw/
    │   ├── DemoApplication.java
    │   ├── TargetService.java
    │   └── TraceAspect.java
    └── resources/META-INF/aop.xml
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'ltw-classload'
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
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.aspectj:aspectjweaver'
}

// Tạo file agent độc lập từ dependency đã resolve.
tasks.register('stageAspectjAgent', Copy) {
    from(configurations.runtimeClasspath) {
        include 'aspectjweaver-*.jar'
    }
    into(layout.buildDirectory.dir('agent'))
    rename { 'aspectjweaver.jar' }
}
~~~

`src/main/java/com/example/ltw/DemoApplication.java`:

~~~java
package com.example.ltw;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) throws Exception {
        System.out.println("01 before Spring context");

        // Bắt target khởi tạo ngay tại main, khi Spring chưa tạo context.
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> type = Class.forName(
                "com.example.ltw.TargetService", true, loader);

        System.out.println("03 loader = " + type.getClassLoader());
        System.out.println("04 create Spring context");
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(TargetService service) {
        return args -> {
            System.out.println("05 invoke target bean");
            service.perform();
        };
    }
}
~~~

`src/main/java/com/example/ltw/TargetService.java`:

~~~java
package com.example.ltw;

import org.springframework.stereotype.Service;

@Service
public class TargetService {
    static {
        System.out.println("02 TargetService initialized");
    }

    public void perform() {
        System.out.println("07 TargetService.perform");
    }
}
~~~

`src/main/java/com/example/ltw/TraceAspect.java`:

~~~java
package com.example.ltw;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(* com.example.ltw.TargetService.perform(..))")
    public void beforePerform() {
        System.out.println("06 woven advice");
    }
}
~~~

`src/main/resources/META-INF/aop.xml`:

~~~xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN"
    "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <aspects>
        <aspect name="com.example.ltw.TraceAspect"/>
    </aspects>
    <weaver options="-showWeaveInfo">
        <include within="com.example.ltw..*"/>
    </weaver>
</aspectj>
~~~

Chú ý: `TraceAspect` **không gắn `@Component`**; AspectJ runtime nhận aspect từ `aop.xml`. Đây là ví dụ **weaving**, không dựa vào Spring auto-proxy. Việc đưa thêm aspect này vào danh sách Spring AOP bean có thể làm khó phân biệt hai cơ chế hoặc gây advice chạy lặp nếu cấu hình cùng tác động.

Chạy từ dự án có Gradle wrapper (hoặc thay `./gradlew` bằng lệnh Gradle tương thích đã cài):

~~~bash
./gradlew clean bootJar stageAspectjAgent
java -javaagent:build/agent/aspectjweaver.jar -jar build/libs/ltw-classload-0.0.1-SNAPSHOT.jar
~~~

Trên Windows PowerShell, dùng `.\gradlew.bat` thay cho `./gradlew` nếu cần; tham số `java` còn lại không đổi.

Bỏ qua log khởi động Spring, stack trace JVM không có trong trường hợp chạy thành công và dòng thông tin weave do `-showWeaveInfo` in thêm, các **dòng đánh dấu của chương trình** xuất hiện theo thứ tự:

~~~text
01 before Spring context
02 TargetService initialized
03 loader = <tên/định danh ClassLoader phụ thuộc môi trường>
04 create Spring context
05 invoke target bean
06 woven advice
07 TargetService.perform
~~~

Dòng `03` là mẫu trình bày: JVM in chuỗi đại diện ClassLoader thực tế, không có output cố định. Dòng `02` chứng tỏ lớp đã được **khởi tạo** trước khi context bắt đầu; agent vốn đã đăng ký transformer trước `main`, nên vẫn có thể weave target. Các dòng `06` và `07` chứng minh advice chạy trước thân method.

**Đối chứng:** chạy cùng executable JAR nhưng bỏ `-javaagent`:

~~~bash
java -jar build/libs/ltw-classload-0.0.1-SNAPSHOT.jar
~~~

Khi đó chương trình vẫn in `01`–`05` và `07`, nhưng **không có `06 woven advice`**, vì aspect không được đăng ký như Spring bean và không có LTW. Điều này cũng chỉ ra rằng **thêm dependency `aspectjweaver` vào ứng dụng chưa đủ để bật agent**.

### Hiểu chính xác phần nào là kết quả của thí nghiệm

- AspectJ agent bắt đầu trước `main`; `Class.forName(..., true, loader)` làm target được load/khởi tạo trước Spring context.
- `@Service` được Spring nhận diện và khởi tạo thành bean sau đó; thời điểm tạo bean không phải thời điểm quyết định weaving.
- `TraceAspect` chạy nhờ **bytecode của `TargetService` đã được weave**, không nhờ proxy Spring.
- Để đo **thời điểm load/define** chính xác hơn `static initializer`, dùng log nạp lớp JVM và đối chiếu với log AspectJ (phần tiếp theo).

## 5. Quan sát thật: log ClassLoader, log weaving và kiểm tra package

JDK 17/21 hỗ trợ log class loading qua Unified Logging. Khi chạy thử, thêm `-Xlog:class+load=info` **trước `-jar`**:

~~~bash
java -Xlog:class+load=info -javaagent:build/agent/aspectjweaver.jar \
     -jar build/libs/ltw-classload-0.0.1-SNAPSHOT.jar
~~~

Lệnh bash được ngắt bằng `\`; trên PowerShell có thể viết tất cả trên một dòng. Log class loading rất dài; hãy lọc theo `com.example.ltw.TargetService`, `TraceAspect` và dấu thời gian. Class load log giúp biết JVM nhìn thấy class nào, nguồn nạp và thời điểm; nó **không thay thế** bằng chứng về advice/transformer.

Trong `aop.xml` đã bật `-showWeaveInfo`. Khi cần phân tích sâu hơn, trong môi trường kiểm thử có thể đặt:

~~~bash
java -Daj.weaving.verbose=true -Dorg.aspectj.weaver.showWeaveInfo=true \
     -javaagent:build/agent/aspectjweaver.jar \
     -jar build/libs/ltw-classload-0.0.1-SNAPSHOT.jar
~~~

So sánh bốn bằng chứng cùng một lần chạy:

1. **JVM arguments**: có đúng `-javaagent` hay Spring instrumentation agent hay không; file tồn tại và được JVM mở thành công.
2. **Loader thực tế**: target, aspect, resource `META-INF/aop.xml` có nằm trong loader/classpath dự kiến không.
3. **Weave info**: có thông báo AspectJ áp dụng advice vào **đúng class và method** không; thiếu log trong một chế độ logging chưa đủ kết luận tuyệt đối, nên kiểm tra output method.
4. **Hành vi**: test chạy method có nhận advice đúng một lần không; nếu liên quan transaction, kiểm tra transaction/rollback thực sự.

Trong Spring Boot fat JAR, application classes/resource thường nằm ở `BOOT-INF/classes` còn thư viện ở `BOOT-INF/lib`. Loader ứng dụng của Boot xử lý cấu trúc này; kiểm tra resource qua runtime classloader thay vì chỉ nhìn JAR bằng công cụ zip. Nếu cần chẩn đoán đường dẫn resource, có thể kiểm tra:

~~~java
ClassLoader cl = Thread.currentThread().getContextClassLoader();
var locations = cl.getResources("META-INF/aop.xml");
while (locations.hasMoreElements()) {
    System.out.println("AspectJ config: " + locations.nextElement());
}
~~~

Đoạn trên là minh họa chèn vào ứng dụng đã có `main`; phải thêm `throws Exception` hoặc xử lý `IOException` nếu cần. Có thể có nhiều `aop.xml` từ nhiều dependency; kiểm tra tất cả cấu hình mà loader nhìn thấy để tránh aspect ngoài dự kiến.

## 6. Những cách khiến class được nạp quá sớm trong ứng dụng Spring

| Tình huống | Nguyên nhân ảnh hưởng LTW | Cách xử lý |
| --- | --- | --- |
| `Class.forName("...")` hoặc truy cập static member trong `main` | Target có thể được define trước khi Spring-managed LTW đăng ký transformer | Chuyển thao tác xuống sau bước enable weaving; hoặc bật AspectJ agent từ JVM startup |
| Thư viện gọi target từ static initializer | Việc nạp lớp phát sinh gián tiếp, rất sớm | Tách initialization, xem log class loading; kiểm soát bootstrap |
| Container/application server scan class trước Spring context | Server classpath scanning có thể định nghĩa lớp trước khi Spring LTW được bật | Kiểm tra cơ chế scan của server, cấu hình weaving đủ sớm hoặc dùng agent |
| Target nằm ở shared/parent loader | Spring context con đăng ký transformer cho loader khác | Chỉnh classpath/classloader boundary; bảo đảm transformer nằm ở loader định nghĩa target |
| `@Lazy` trên bean nhưng vẫn thiếu advice | `@Lazy` trì hoãn tạo bean, không cam kết trì hoãn loading/definition | Đối chiếu class load log, không lấy lazy bean làm bằng chứng |
| Chỉ thêm `@EnableAspectJAutoProxy` | Đây là Spring proxy, không bật AspectJ LTW | Dùng cơ chế LTW thật, bảo đảm `aop.xml` và transformer |
| Thêm `@EnableLoadTimeWeaving` rồi không có weaving | Thiếu instrumentation khả dụng, thiếu `aop.xml` trong AUTODETECT, aspect không visible hoặc target đã loaded | Kiểm tra từng điều kiện, bật log, test điểm cắt có thật |
| Một request có advice, request khác không | Có thể đi qua classloader khác, instance/proxy khác, hoặc có path không khớp pointcut | Log defining loader và method target ở cả hai đường, kiểm tra trùng class name |
| Advice xuất hiện hai lần | Có thể cùng chạy Spring AOP proxy và AspectJ weaving hoặc pointcut overlap | Xác minh aspect registration tại Spring và `aop.xml`; thu hẹp phạm vi áp dụng |

Lưu ý về Spring component scanning: nhiều thao tác scan đọc **metadata của file `.class`** mà chưa nhất thiết định nghĩa Java `Class`. Vì vậy, không kết luận "component scan đã chạy thì weaving chắc chắn quá muộn". Hãy tìm **sự kiện class load thật** và thời điểm đăng ký transformer.

## 7. Kiểm tra thứ tự khi dùng Spring-managed LTW

Giả sử ứng dụng có `@EnableLoadTimeWeaving` trong `@Configuration` và chạy với `-javaagent:/path/to/spring-instrument.jar`. Khi `main` gọi `Class.forName(..., true, ...)` **trước `SpringApplication.run(...)`**, target có thể đã được define, nên AspectJ transformer do Spring đăng ký ở bước khởi tạo context sẽ đến quá muộn đối với class đó.

Để tránh lỗi này:

1. Khởi động JVM với instrumentation cần thiết và xác định rõ bạn đang dùng AspectJ agent trực tiếp hay Spring-managed LTW.
2. Đảm bảo `META-INF/aop.xml` có mặt trong runtime loader và aspect đã được khai báo đúng trước khi target được define.
3. Để Spring thiết lập `LoadTimeWeaver` đủ sớm **trước các lần sử dụng target**; tránh eager reference, static bootstrap và thư viện tải lớp chủ động.
4. Kiểm tra target được định nghĩa bởi loader nào; nếu do parent/server loader định nghĩa, cấu hình transformer tại app loader có thể không có tác dụng.
5. Khởi động lại JVM/container sau khi đổi cấu hình để loại bỏ lớp đã nạp trong tiến trình cũ, thay vì chỉ tạo lại bean.
6. Chạy test tích hợp với đúng kiểu đóng gói và cơ chế LTW production sử dụng.

Nếu phải intercept target vốn đã được nạp sớm do startup không kiểm soát được, AspectJ **agent từ JVM startup** hoặc **compile-time/post-compile weaving** thường đáng tin cậy hơn việc hy vọng cấu hình Spring-managed LTW xuất hiện kịp lúc. CTW tạo ra bytecode woven trong artifact, giảm phụ thuộc vào thời điểm runtime loader cài transformer, nhưng cần tích hợp build tương ứng.

## 8. Checklist ngắn khi advice không chạy

- Đó có thật là **AspectJ LTW** hay chỉ là `@Aspect` chạy bằng Spring AOP proxy?
- Target đã **được định nghĩa** trước khi transformer có mặt chưa? Dùng `-Xlog:class+load=info` để kiểm tra.
- **Defining ClassLoader** của target là gì? Có đúng loader mà `LoadTimeWeaver`/agent xử lý không?
- Aspect class và mọi `META-INF/aop.xml` liên quan có **visible** với weaver ở loader này không?
- `<weaver><include within="..."/></weaver>` có lọc mất target không? Pointcut có khớp method/join point thật không?
- JVM có agent đúng loại (`aspectjweaver` hoặc `spring-instrument` theo cách triển khai) và có đặt option trước `-jar` không?
- Có test chứng minh advice chạy **đúng một lần** và log weave đủ rõ, trên cùng môi trường classloader như deployment không?

**Ý chính:** Trong Spring, LTW tác động vào **class bytecode tại lúc ClassLoader định nghĩa lớp**. Muốn advice ổn định, cần kiểm soát *đúng ClassLoader*, *đúng tài nguyên/aspect hiển thị* và *đúng thời điểm cài transformer*. `ApplicationContext` quản lý bean, nhưng **không thể thay thế điều kiện thời điểm nạp lớp của JVM**.

## Tài liệu tham khảo

- [Spring Framework — Using AspectJ with Spring Applications / Load-time Weaving](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)
- [Spring Framework — Registering a LoadTimeWeaver](https://docs.spring.io/spring-framework/reference/core/beans/context-load-time-weaver.html)
- [Spring Framework — @AspectJ support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- [Spring Boot — Nested JARs](https://docs.spring.io/spring-boot/specification/executable-jar/nested-jars.html)
- [AspectJ — Load-Time Weaving / Class Loader rules](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-integration-010">Tránh cùng một hành vi bị áp dụng bởi cả proxy và weaving</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh cùng một hành vi bị áp dụng bởi cả proxy và weaving trong Spring

## 1. Vấn đề cần nhận diện

Một ứng dụng Spring có thể sử dụng đồng thời hai đường thực thi advice:

- **Spring AOP dựa trên proxy:** `@EnableAspectJAutoProxy`, `@Aspect` được quản lý như Spring bean và các advisor/interceptor của framework chặn lời gọi qua bean proxy.
- **AspectJ weaving (compile-time hoặc load-time):** AspectJ thay đổi bytecode của lớp được chọn để chèn advice tại join point. Ví dụ khi load-time weaving (LTW), `aspectjweaver` và `META-INF/aop.xml` xác định những lớp/aspect được dệt.

Cú pháp `@Aspect` giống nhau **không có nghĩa chỉ có một động cơ AOP**. Spring hiểu các annotation của AspectJ để tạo proxy; AspectJ compiler/weaver cũng có thể hiểu chúng để dệt. Khi **cùng một nghiệp vụ cắt ngang** (ví dụ ghi audit, đếm metric, kiểm tra quyền, retry hay mở transaction) được khai báo ở cả hai cơ chế và cùng match một lời gọi, hành vi có thể xảy ra **hai lần**.

**Ví dụ lỗi:** lần gọi `InvoiceService.issue()` tạo một hóa đơn, nhưng listener audit nhận hai event `INVOICE_ISSUED` do một advice đi qua Spring proxy và một advice đã được weave trực tiếp vào `issue()`. Nghiệp vụ target có thể chỉ chạy một lần; trùng audit xảy ra ở lớp hạ tầng. Nếu hành vi là retry hoặc transaction, ảnh hưởng còn phức tạp hơn, không thể suy ra chỉ bằng cách đếm log.

Bài này tập trung vào **Spring kết hợp AspectJ**, cách nhận diện overlap, thí nghiệm tái lập, cơ chế phòng ngừa và tiêu chí kiểm chứng khi cấu hình thực tế thay đổi. Không cần ôn lại các khái niệm AspectJ thuần túy ngoài những phần trực tiếp giúp quản lý Spring AOP.

## 2. Hai đường thực thi có thể cùng can thiệp ở đâu?

```text
Caller
  |
  v
Spring-managed bean (có thể là Spring AOP proxy)
  |
  +-- Spring advisor: proxy.audit             [đường proxy]
  |
  v
InvoiceService.issue() đã được AspectJ weave
  |
  +-- AspectJ advice: woven.audit             [đường bytecode]
  |
  v
Thân method: tạo hóa đơn
```

| Góc quan sát | Spring AOP proxy | AspectJ weaving |
| --- | --- | --- |
| Kích hoạt thường gặp | `@EnableAspectJAutoProxy`, auto-proxy creator, aspect bean | `ajc`/build weaving hoặc LTW (`-javaagent:aspectjweaver.jar`, `aop.xml`, Spring `LoadTimeWeaver` tùy kiểu triển khai) |
| Nơi advice can thiệp | Lời gọi method đi qua proxy Spring | Join point của bytecode đã weave |
| Bean có nhất thiết là proxy? | Có, với join point được Spring AOP xử lý | Không; một class thường cũng có thể đã được weave |
| Có xem được qua `Advised.getAdvisors()`? | Các advisor của Spring proxy thường xem được | Advice do AspectJ weave **không nằm trong danh sách đó** |
| Self-invocation `this.method()` | Thường bỏ qua lời advice của proxy cho method được gọi nội bộ | Vẫn có thể được dệt, nếu pointcut và loại join point phù hợp |
| Hệ quả khi kết hợp | Advice proxy chạy bên ngoài target | Target có thể đã chứa advice woven → cộng dồn hành vi |

Những điều trên không đồng nghĩa chỉ cần thấy `aspectjweaver` trong dependencies là weaving đang chạy. Thư viện này cũng được Spring AOP dùng để phân tích cú pháp pointcut `@AspectJ`. **Dependency có mặt ≠ weaver được kích hoạt**. Ngược lại, chỉ thấy `@EnableAspectJAutoProxy` cũng không có nghĩa dự án đang sử dụng AspectJ LTW.

Spring AOP chỉ hỗ trợ method-execution join points và chủ yếu áp dụng cho Spring-managed beans qua proxy. AspectJ weaving có thể can thiệp nhiều loại join point và không cần một Spring proxy; vì vậy **không thể dùng kết quả `AopUtils.isAopProxy(bean) == false` để kết luận bean không được weave**.

## 3. Những cấu hình dễ gây trùng hành vi

### 3.1. Cùng một `@Aspect` được đăng ký ở cả hai phía

Một lớp viết theo `@AspectJ` có thể được nhận như **Spring aspect bean** (chẳng hạn được đăng ký bằng `@Bean` hoặc component scanning) đồng thời được liệt kê như **AspectJ woven aspect** trong `META-INF/aop.xml`. Nếu hai phía cùng match target method, cùng một ý định audit/logging có thể được thực hiện lặp. Không nên tự kết luận rằng hai phía chia sẻ cùng instance của aspect: cơ chế tạo/quản lý instance của Spring và AspectJ có thể khác nhau.

### 3.2. Hai aspect khác nhau nhưng cùng thực hiện một policy

Không cần trùng class: `SpringAuditAspect` do auto-proxy quản lý và `WovenAuditAspect` do AspectJ quản lý vẫn có thể đều gửi audit cho `InvoiceService.issue()`. Kiểu lỗi này thường xuất hiện khi migration từ proxy sang LTW, khi một thư viện bổ sung weaving, hoặc khi hai module tự triển khai một policy giống nhau.

### 3.3. Chồng lấp khi kết hợp với tiện ích Spring

Một hành vi có thể nằm ở nhiều nơi: advisor transaction/cache/security do Spring hạ tầng tạo, custom Spring aspect hoặc woven aspect tự viết. Ví dụ `@Transactional` hoạt động qua Spring proxy, trong khi một woven aspect riêng cũng thao tác transaction theo chính sách tự xây dựng. Chúng có thể ảnh hưởng nhau. **Không coi mọi trường hợp cùng chạm một method là lỗi:** có thể cần nhiều chính sách khác nhau; vấn đề cần kiểm soát là **hai đường thực thi cùng đảm nhiệm một trách nhiệm**.

### 3.4. Dễ nhầm với trùng do cơ chế khác

Hai log giống nhau **chưa đủ** chứng minh proxy + weaving: có thể do hai advisor Spring, hai lời gọi nghiệp vụ, retry, interceptor HTTP, sự kiện publish hai lần hoặc trùng logger appender. Khi điều tra, phải chứng minh có **hai lần chạy advice cùng ngữ nghĩa** và xác định nguồn mỗi lần.

## 4. Phép thử tái lập: proxy-only, weaving-only và both

Ta cố ý tạo **hai aspect khác nhau cùng ghi audit** khi `InvoiceService.issue()` chạy:

- `ProxyAuditAspect`: là Spring bean, được kích hoạt bằng `@EnableAspectJAutoProxy`.
- `WovenAuditAspect`: **không** là Spring bean, được đăng ký trong `META-INF/aop.xml`, hoạt động khi có AspectJ javaagent.
- `InvoiceService`: chỉ làm một việc, ghi sự kiện `target`.

Cùng một project, chạy ba cấu hình để cô lập đường thực thi. Nhờ ghi nhãn `proxy.audit`, `woven.audit`, `target`, ta phân biệt được việc **advice bị nhân đôi** với việc **target thực thi nhiều lần**.

### 4.1. Dựng dự án Java 17+/Maven

Cấu trúc:

```text
dual-aop-probe/
├── pom.xml
└── src/main/
    ├── java/demo/
    │   ├── Main.java
    │   ├── InvoiceService.java
    │   ├── ProxyAuditAspect.java
    │   └── WovenAuditAspect.java
    └── resources/META-INF/aop.xml
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>dual-aop-probe</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.version>6.2.8</spring.version>
        <aspectj.version>1.9.22.1</aspectj.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>${spring.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>${spring.version}</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>${aspectj.version}</version>
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
                <artifactId>maven-dependency-plugin</artifactId>
                <version>3.7.1</version>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/demo/InvoiceService.java`:

```java
package demo;

public class InvoiceService {
    public void issue() {
        Main.record("target");
    }
}
```

`src/main/java/demo/ProxyAuditAspect.java`:

```java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class ProxyAuditAspect {
    @Before("execution(* demo.InvoiceService.issue(..))")
    public void beforeIssue() {
        Main.record("proxy.audit");
    }
}
```

`src/main/java/demo/WovenAuditAspect.java`:

```java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class WovenAuditAspect {
    @Before("execution(* demo.InvoiceService.issue(..))")
    public void beforeIssue() {
        Main.record("woven.audit");
    }
}
```

Hai advice cùng match `execution(* demo.InvoiceService.issue(..))`, nhưng **không dùng cùng class hoặc cùng Spring bean**. Đây là cách cô lập rõ nguyên nhân trùng policy mà không phụ thuộc vòng đời AspectJ aspect instance và Spring bean.

`src/main/java/demo/Main.java`:

```java
package demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public class Main {
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    public static void record(String event) {
        EVENTS.add(event);
    }

    record Counts(int proxy, int woven, int target) {}

    private static int count(String name) {
        return (int) EVENTS.stream().filter(name::equals).count();
    }

    public static void main(String[] args) {
        if (args.length != 1 ||
            !List.of("proxy", "woven", "both").contains(args[0])) {
            throw new IllegalArgumentException(
                "Usage: demo.Main proxy|woven|both");
        }

        String mode = args[0];
        EVENTS.clear();
        Class<?> config = mode.equals("woven")
            ? WovenOnlyConfig.class
            : ProxyConfig.class;

        try (var context = new AnnotationConfigApplicationContext(config)) {
            InvoiceService service = context.getBean(InvoiceService.class);
            boolean springProxy = AopUtils.isAopProxy(service);
            int advisorCount = service instanceof Advised advised
                ? advised.getAdvisors().length
                : 0;

            service.issue();

            Counts actual = new Counts(
                count("proxy.audit"),
                count("woven.audit"),
                count("target"));
            Counts expected = switch (mode) {
                case "proxy" -> new Counts(1, 0, 1);
                case "woven" -> new Counts(0, 1, 1);
                case "both" -> new Counts(1, 1, 1);
                default -> throw new IllegalStateException(mode);
            };

            System.out.println("mode=" + mode);
            System.out.println("springProxy=" + springProxy);
            System.out.println("springAdvisorCount=" + advisorCount);
            System.out.println("events=" + EVENTS);
            System.out.println("counts=" + actual);

            if (!expected.equals(actual)) {
                throw new AssertionError(
                    "expected=" + expected + ", actual=" + actual);
            }
            System.out.println("assertion=PASS");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BaseConfig {
        @Bean
        InvoiceService invoiceService() {
            return new InvoiceService();
        }
    }

    // Có Spring aspect bean + auto-proxy.
    @Configuration(proxyBeanMethods = false)
    @Import(BaseConfig.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class ProxyConfig {
        @Bean
        ProxyAuditAspect proxyAuditAspect() {
            return new ProxyAuditAspect();
        }
    }

    // Không khai báo Spring aspect bean và không bật Spring auto-proxy.
    @Configuration(proxyBeanMethods = false)
    @Import(BaseConfig.class)
    static class WovenOnlyConfig {}
}
```

Ở đây `WovenAuditAspect` không xuất hiện trong hai class cấu hình Spring và không sử dụng component scanning. Phần weave được kiểm soát độc lập từ tệp dưới đây.

`src/main/resources/META-INF/aop.xml`:

```xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN"
    "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <weaver options="-showWeaveInfo">
        <include within="demo.InvoiceService"/>
    </weaver>
    <aspects>
        <aspect name="demo.WovenAuditAspect"/>
    </aspects>
</aspectj>
```

`<aspects>` liệt kê **chính xác** aspect thuộc quyền quản lý của AspectJ; `<include>` thu hẹp lớp được weave vào `InvoiceService`. Tệp nằm trên runtime classpath nhưng **không tự kích hoạt weaving khi chạy Java thông thường không có weaver agent hoặc cơ chế weaving tương đương**.

**Lưu ý:** dự án dùng `javac` qua Maven Compiler Plugin để biên dịch annotation-style aspects. LTW biến đổi class khi JVM nạp lớp. **Không cần chạy AspectJ compiler `ajc` cho chính fixture này.** Đường compile-time weaving là lựa chọn triển khai khác, không trộn thêm vào phép thử.

### 4.2. Chạy ba trường hợp độc lập

Chạy từ thư mục `dual-aop-probe`. Trên PowerShell/Windows:

```powershell
mvn -q -DskipTests package
mvn -q dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
mvn -q dependency:copy "-Dartifact=org.aspectj:aspectjweaver:1.9.22.1" "-DoutputDirectory=target/agent"

$cp = "target/classes;" + (Get-Content target/classpath.txt -Raw).Trim()

# Spring proxy, không bật AspectJ javaagent.
java -cp $cp demo.Main proxy

# Chỉ AspectJ LTW, không đăng ký Spring proxy aspect.
java "-javaagent:target/agent/aspectjweaver-1.9.22.1.jar" -cp $cp demo.Main woven

# Cùng lúc Spring proxy và AspectJ LTW.
java "-javaagent:target/agent/aspectjweaver-1.9.22.1.jar" -cp $cp demo.Main both
```

Trên Linux/macOS, sau khi chạy ba lệnh `mvn` như trên, dùng dấu `:` phân cách classpath:

```bash
CP="target/classes:$(cat target/classpath.txt)"
java -cp "$CP" demo.Main proxy
java -javaagent:target/agent/aspectjweaver-1.9.22.1.jar -cp "$CP" demo.Main woven
java -javaagent:target/agent/aspectjweaver-1.9.22.1.jar -cp "$CP" demo.Main both
```

Mỗi lệnh chạy trong JVM mới để không giữ class đã được load/weave từ lượt khác. Nếu bỏ agent mà chạy mode `woven` hoặc `both`, assertion báo sai số `woven.audit`, giúp phát hiện môi trường chưa thực sự kích hoạt weaving.

**Kết quả trọng tâm cần kiểm tra:**

| Mode | Spring proxy | `proxy.audit` | `woven.audit` | `target` | Ý nghĩa |
| --- | --- | ---: | ---: | ---: | --- |
| `proxy` | Có | 1 | 0 | 1 | Chỉ Spring aspect can thiệp |
| `woven` | Không (theo fixture này) | 0 | 1 | 1 | Chỉ advice đã weave can thiệp |
| `both` | Có | 1 | 1 | 1 | Hai hành vi audit cùng áp dụng lên một target invocation |

Trong mỗi trường hợp đáp ứng cấu hình, chương trình kết thúc bằng `assertion=PASS`. Dòng `counts=Counts[proxy=1, woven=1, target=1]` là phần ổn định cho mode `both`; `springAdvisorCount`, log diagnostic của weaver và chi tiết hiển thị khác có thể tùy phiên bản. Không dùng thứ tự tuyệt đối của hai label audit như API contract giữa nhiều cơ chế; **đếm số lần theo nguồn** mới là phép thử chính.

Cần đối chiếu `springAdvisorCount` với `AopUtils.isAopProxy`: nó chỉ phản ánh advisor của proxy Spring. Ngay cả khi mode `woven` cho `springAdvisorCount=0`, advice AspectJ vẫn có thể ghi `woven.audit`. Đây là lý do phải đo trace thực thi bên cạnh kiểm tra advisor.

## 5. Ngăn trùng lặp bằng phân quyền sở hữu policy

Cách xử lý bền vững là xác định **cơ chế duy nhất sở hữu từng hành vi cắt ngang trên mỗi tập join point**. Khi một thay đổi cần chuyển từ proxy sang weaving, coi đó là thay đổi đường triển khai cần có rollout/test, không chỉ thay annotation.

| Tình huống | Cách sửa phù hợp | Kiểm chứng |
| --- | --- | --- |
| Chỉ cần chặn lời gọi method qua Spring bean | Giữ Spring advisor; loại cùng policy khỏi `<aspects>`/build weaving nếu có | Không có woven event; Spring aspect chạy một lần |
| Cần advice trên object ngoài container hoặc self-invocation | Chọn AspectJ weaving cho policy đó; bỏ Spring aspect bean/advisor trùng | Woven event đúng một lần khi đi qua và ngoài proxy |
| Bắt buộc dùng cả hai cơ chế vì các concern khác nhau | Phân chia các **policy và target join point** không giao nhau; pointcut rõ phạm vi package/annotation, config aspect rõ nguồn | Với từng entry point, policy chỉ chạy một lần |
| Migration dần từ Spring proxy sang weaving | Gắn feature/config rollout cho policy, tắt nguồn cũ trước hoặc đồng bộ với việc bật nguồn mới; test cả ba mode | Không có giai đoạn cùng xử lý trong cùng instance |
| Bị áp dụng trùng trên production | Xác định chính xác proxy advisor + woven aspect; sửa một nguồn cấu hình có thẩm quyền | Audit/metric/transaction behavior trở về số lần mong muốn |

Trong fixture, hai cách sửa dễ chứng minh nhất:

- **Giữ proxy:** chạy mode `proxy` không có AspectJ agent. Nếu app vẫn dùng weaver cho concern khác, **chỉ loại `WovenAuditAspect` khỏi `aop.xml` hoặc thu hẹp pointcut**; không cần tắt cả weaving.
- **Giữ weaving:** dùng `WovenOnlyConfig`, không đăng ký `ProxyAuditAspect` như Spring bean/advisor. App vẫn có thể có proxy transaction/security phục vụ hành vi **khác**, miễn không có advisor thứ hai thực hiện cùng audit.

**Không dùng `@Order` để “chữa” nhân đôi:** `@Order` xác định thứ tự một số advisor/aspect trong cơ chế tương ứng, nhưng không loại bớt advice. Đổi precedence không khiến hai hành vi cùng match tự gộp lại thành một. Cũng không nên chỉ dựa vào flag thread-local “đã chạy” để bỏ lần sau: bất đồng bộ, retry và nhiều JVM làm cơ chế này dễ sai.

Idempotency key ở tầng nghiệp vụ vẫn hữu ích như lớp phòng vệ, đặc biệt khi audit gửi qua mạng hoặc thao tác có thể bị retry. Tuy nhiên idempotency **không thay thế** việc xác định ownership và bỏ cấu hình advice trùng; nếu hai lời advice cùng khởi tạo transaction hay sửa return value, “chặn ghi audit lần hai” vẫn chưa xử lý đúng hành vi.

## 6. Checklist điều tra trên một ứng dụng Spring thực tế

### Bước 1 — Chốt chính xác hành vi bị lặp

Ghi rõ policy (audit, metrics, authorization, retry…), bean và method, một request/correlation id, thời điểm, thread, kết quả hoặc exception. Đo riêng:

- Số lần caller yêu cầu thao tác.
- Số lần method target thực sự vào thân hàm.
- Số lần **mỗi** advice/policy chạy.

Nếu target chạy hai lần do retry thì đó là loại vấn đề khác với hai advice ghi audit quanh **một** lần target. Dấu vết phải cho phép phân biệt.

### Bước 2 — Kiểm kê các lớp cấu hình AOP

Kiểm tra **đúng tiến trình và artifact đã deploy**, không chỉ xem cấu hình trong source:

1. Spring: `@EnableAspectJAutoProxy`, `<aop:aspectj-autoproxy>`, bean có `@Aspect`, advisor từ `@Transactional`, cache, security và custom interceptors.
2. AspectJ: `META-INF/aop.xml` thực sự trên runtime classpath, `<aspects>`, `<include>`/`<exclude>`; bước `ajc` hoặc plugin weave lúc build; tùy runtime có `-javaagent` hoặc Spring-configured `LoadTimeWeaver`.
3. Điểm vào: caller gọi qua bean proxy hay gọi trực tiếp; với self-invocation, proxy advice có thể vắng mặt còn woven advice vẫn hoạt động.

Đừng coi `aspectjweaver.jar` xuất hiện trong dependency tree là bằng chứng đủ cho LTW: Spring AOP cũng cần các phần của thư viện này khi dùng `@AspectJ` syntax. Cần thêm **dấu hiệu cấu hình/kích hoạt** và trace.

### Bước 3 — Quan sát từ Spring proxy

Đối với bean cụ thể:

```java
Object bean = context.getBean("invoiceService");
System.out.println("isSpringProxy=" + AopUtils.isAopProxy(bean));

if (bean instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName()
            + " -> " + advisor.getAdvice().getClass().getName());
    }
}
```

Đoạn trên là **minh họa trong ứng dụng đã có `ApplicationContext context`**; cần import `org.springframework.aop.Advisor`, `org.springframework.aop.framework.Advised` và `org.springframework.aop.support.AopUtils`. Kết quả `Advised` chỉ hỗ trợ chẩn đoán phần Spring proxy; **không phải API để liệt kê bytecode advice do AspectJ dệt**.

### Bước 4 — Quan sát phía weaving và cô lập từng nguồn

Bật thông tin `-showWeaveInfo` trong cấu hình AspectJ của môi trường thử nghiệm và kiểm tra weaver có xử lý đúng class/method cần thiết không. Với agent Java, kiểm tra JVM khởi chạy thực sự có agent; với Spring LTW, xem cấu hình load-time-weaver và classloader. Nếu compile-time weaving, kiểm tra đầu ra của công đoạn build và dependency artifact cuối.

Tạo thử nghiệm cô lập ba mode như phần 4, giữ nguyên input và một caller; bật/tắt **một nguồn policy** mỗi lần. Nếu bỏ proxy advice làm số lần audit giảm 2→1, rồi bỏ woven advice làm giảm 1→0, đó là bằng chứng phân định hai đường. Sau khi sửa cấu hình, test lại ở cả lời gọi qua proxy và đường gọi không có proxy (khi có nhu cầu weaving cho đường này).

### Bước 5 — Đặt assertion chống hồi quy

Với lời gọi một lần:

```text
target.invocations == 1
audit.events.forSameBusinessKey == 1
proxyAudit.invocations + wovenAudit.invocations == 1
```

Assertion thứ ba chỉ áp dụng khi hai advice đang được so sánh thật sự thể hiện **cùng một audit policy**; nếu chúng có trách nhiệm khác nhau, hãy định nghĩa các assertion độc lập theo policy. Với event-driven processing hoặc retry chủ động, dùng event id/correlation id để xác định số lần kỳ vọng theo hợp đồng hệ thống.

## 7. Lỗi sửa vội thường gặp

1. **Xóa `@Aspect` từ aspect đã weave mà chưa xem `aop.xml`/build config:** có thể làm mất weaving của policy cần giữ. Trước hết xác định cơ chế sở hữu hành vi.
2. **Tắt `@EnableAspectJAutoProxy` toàn ứng dụng:** có thể làm mất các custom Spring aspects cần thiết. Ưu tiên gỡ/chỉnh **đúng aspect bean hoặc advisor trùng**.
3. **Tắt `aspectjweaver` dependency vì thấy tên jar:** thư viện cũng phục vụ parse/match pointcut cho Spring `@AspectJ` style; cách này có thể gây lỗi startup. Chỉ gỡ khi đã xác minh không còn tính năng phụ thuộc.
4. **Xóa `aop.xml` mà không kiểm tra cách weave khác:** compile-time weaving hoặc tệp `aop.xml` đóng gói trong jar khác vẫn có thể tác động lên target.
5. **Dựa duy nhất vào `getAdvisors()` để loại trừ AspectJ:** woven advice không phải phần tử của Spring advisor array.
6. **Thấy hai log rồi kết luận là hai advice:** retry, self-calls qua proxy, hai target invocation hoặc cấu hình logger cũng tạo triệu chứng tương tự.
7. **Chữa bằng `@Order` hoặc “chỉ chạy một lần” trong static boolean:** không sửa ownership, dễ sai với đa luồng, đa request, đa instance.
8. **Thử nghiệm cùng JVM sau khi đổi weaver config:** class đã load/weave có thể giữ kết quả cũ; khởi động JVM/application context sạch cho từng cấu hình.

## 8. Tiêu chí hoàn thành

Có thể kết luận đã loại trừ lỗi **cùng một hành vi bị áp dụng bởi cả proxy và weaving** khi:

- Đã liệt kê được **nguồn policy được Spring quản lý** (bean/advisor/proxy) và **nguồn policy được AspectJ weave** (build/LTW/agent/`aop.xml`) trong artifact và môi trường chạy.
- Trace chỉ ra **số lần chạy từng advice**, số lần target chạy và đúng request/method; không còn nhầm với retry, request trùng hay logging.
- Phép thử cô lập xác nhận các mode proxy-only, weaving-only và both; sau sửa policy thực tế chỉ có **một đường sở hữu** trên mỗi join point mà nó phụ trách.
- Assertion chống hồi quy cho thấy mỗi thao tác nghiệp vụ tạo đúng số audit/event và không đánh mất các Spring advisor khác như transaction hoặc security.
- Các môi trường chạy ứng dụng có cùng policy ownership (dev/test/prod), hoặc sự khác biệt được công bố và kiểm thử rõ ràng.

**Ý chính:** trong Spring, cùng thấy `@Aspect` và AspectJ library chưa nói lên đường thực thi. Hãy theo dõi **Spring proxy/advisor** và **AspectJ weaving configuration** độc lập, dùng trace để xác nhận hành vi thực tế, rồi giao trách nhiệm policy cho một nguồn rõ ràng.

### Tài liệu chính thức

- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): cú pháp `@AspectJ` được Spring áp dụng qua proxy.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): khai báo Spring aspect và auto-proxy.
- [Spring Framework — Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html): AspectJ weaving, LTW, `META-INF/aop.xml`, classloader.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): ranh giới Spring proxy và self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspectj-integration-011">Kiểm chứng cấu hình triển khai và phân biệt lỗi LTW với lỗi auto-proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm chứng cấu hình triển khai và phân biệt lỗi LTW với lỗi auto-proxy trong Spring

## 1. Chẩn đoán dựa trên runtime thay vì annotation

Khi advice không chạy, trước hết phân biệt **Spring proxy path** với **AspectJ LTW bootstrap**. Cả hai dùng được `@Aspect` nhưng khác cơ chế; chỉ có dependency `aspectjweaver`, hoặc chỉ thấy `AopUtils.isAopProxy`, chưa đủ chứng minh class được weave.

- **Proxy:** cần aspect Spring bean, cấu hình auto-proxy, target bean và lời gọi qua proxy; self-invocation có thể bypass.
- **LTW:** cần agent/transformer đúng JVM và ClassLoader, cấu hình `META-INF/aop.xml`, aspect/target visible và target được load sau transformer.
- **Transaction/cache/async mode AspectJ:** cần `spring-aspects`, weaving thật và hạ tầng manager/executor riêng.

Bài này đi từ dấu hiệu log đến phép thử độc lập để phân biệt **proxy hoạt động đúng nhưng call path sai** với **LTW chưa từng weave target**, đồng thời phát hiện double advice do hai cơ chế.

## 2. Chẩn đoán dựa trên dấu hiệu thay vì đoán

| Điều quan sát được | Nghiêng về nguyên nhân nào | Bước xác minh chính |
| --- | --- | --- |
| `AopUtils.isAopProxy(bean) == false` dù mong đợi `@Aspect` | Auto-proxy chưa áp dụng hoặc bean không match advisor | Đọc bean thực tế, `@EnableAspectJAutoProxy`, aspect registration, pointcut |
| `AopUtils.isAopProxy(bean) == true` nhưng advice không chạy | Đi sai qua proxy, self-invocation, advisor/pointcut mismatch, method không intercept được | Xem `Advised.getAdvisors()`, gọi trực tiếp method qua proxy và kiểm tra logs |
| Advice chạy khi gọi từ client nhưng không chạy với `this.inner()` | Dấu hiệu điển hình của Spring AOP proxy | So sánh gọi qua proxy và gọi nội bộ, phân biệt với weaving |
| Object tạo bằng `new` không được `@Configurable` inject | Có thể thiếu Spring-aspects/AspectJ weaving hoặc thiếu cấu hình BeanFactory | Kiểm tra agent/aop.xml/weaveinfo và `@EnableSpringConfigured` |
| `-javaagent` đã có nhưng không xuất hiện weave info | Agent không được JVM nhận, aop.xml bị thiếu, type excluded/load quá sớm hoặc pointcut không match | Xem *JVM startup arguments*, aop.xml, ClassLoader và options |
| Có log `register aspect` nhưng không thấy join point được weave | Aspect được thấy nhưng target type/pointcut không match hoặc bị exclude | Bật `-showWeaveInfo` và `-debug` đúng nhu cầu |
| `@Transactional` self-invocation vẫn không mở giao dịch riêng | Có thể đang ở `AdviceMode.PROXY` hoặc AspectJ transaction aspect chưa weave | Xác minh mode, manager, annotation trên implementation, weaving |
| Chạy local đúng nhưng test/server không chạy advice | JVM/test runner/IDE/container dùng startup options hoặc ClassLoader khác | So startup arguments, classpath, aspect path, order load class |
| Custom aspect chạy hai lần | Có thể vừa proxy advice vừa woven advice hoặc proxy lồng | Kiểm tra cả advisors và weaveinfo trước khi chỉnh code |

Một log bất kỳ như "AspectJ dependency loaded" chỉ xác nhận **library có mặt**. Một dòng "bean is proxy" chỉ chứng minh **Spring proxy được tạo**, chưa chứng minh aspect cụ thể nằm trong advisor chain. Một dòng "registered aspect" chỉ xác nhận **weaver biết aspect**, chưa chứng minh join point đã được woven.

## 3. Checklist bắt buộc cho Spring auto-proxy

Với custom aspect như:

```java
@Aspect
public class TraceAspect {
    @Before("execution(* demo.Work.inner(..))")
    public void before() {
        System.out.println("trace-before inner");
    }
}
```

Đây là snippet rút gọn; ví dụ Java 17 chạy hoàn chỉnh nằm ở phần 6. Spring AOP proxy cần đồng thời:

1. Class mang `@Aspect` được đăng ký như **Spring bean** qua `@Component` + component scan hoặc `@Bean`.
2. `@EnableAspectJAutoProxy` hoặc cấu hình tương đương được kích hoạt trong **đúng ApplicationContext** chứa target/aspect.
3. Spring có `spring-aop` và những AspectJ libraries cần cho việc diễn giải `@Aspect`/pointcut; phần cấu hình này vẫn là **proxy mode**.
4. Target được lấy qua `ApplicationContext`/DI, không phải object tự tạo bằng `new` rồi gọi trực tiếp.
5. Pointcut `execution(* demo.Work.inner(..))` khớp method và type **thực tế**; package/class name chính xác.
6. Lời gọi đi qua Spring proxy; với self-invocation `this.inner()` trên target, advice **không được gọi lần nữa**.
7. Loại proxy tương thích: JDK proxy chủ yếu expose interface; CGLIB proxy qua class không advise được `final`/`private` method.
8. Nếu có nhiều proxy creators/advisors/contexts, kiểm tra bean được ứng dụng **thật sự gọi** chứ không đoán từ annotation.

Với proxy mode, `@EnableAspectJAutoProxy(proxyTargetClass = true)` chọn class-based proxy; nó **không weave bytecode** và không giải quyết self-invocation. `exposeProxy = true` và `AopContext.currentProxy()` chỉ là một workaround gắn code với Spring proxy, không phải cách để kiểm chứng LTW.

### Quan sát proxy/advisors bằng Spring API

```java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("work");

System.out.println("springProxy=" + AopUtils.isAopProxy(bean));
System.out.println("type=" + bean.getClass().getName());

if (bean instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println("advisor=" + advisor);
    }
}
```

Đoạn trên là **snippet debug** để chèn vào vị trí có `ApplicationContext context`. `AopUtils.isAopProxy(...)` có thể trả `false` với woven object hoàn toàn hợp lệ, bởi AspectJ weaving không cần tạo Spring proxy cho object đó. Khi cần xác minh LTW, hãy kiểm tra **weave info** và hành vi của lời gọi trên woven class.

## 4. Checklist bắt buộc cho AspectJ LTW

LTW nghĩa là AspectJ đổi bytecode **trước lúc class được định nghĩa**. Với Spring có hai cách triển khai thường gặp:

- **AspectJ Java agent trực tiếp:** JVM startup có `-javaagent:/path/to/aspectjweaver.jar`; AspectJ xử lý các `META-INF/aop.xml` mà ClassLoader tìm thấy.
- **Spring LoadTimeWeaver integration:** cấu hình `@EnableLoadTimeWeaving` hoặc `<context:load-time-weaver/>` để Spring cài transformer ở ClassLoader được hỗ trợ. Trên JVM chung có thể cần `spring-instrument.jar` agent; trên application server có thể dùng LoadTimeWeaver riêng. Đây là một luồng cấu hình khác và cần kiểm tra ClassLoader/instrumentation thực tế.

Bốn thứ cần độc lập xác minh:

| Lớp kiểm tra | Điều phải có | Bằng chứng đáng tin |
| --- | --- | --- |
| JVM/bootstrap | Agent hoặc instrumentation/classloader hoạt động **trước khi target class load** | Command-line JVM thực tế, log agent/weaver startup |
| AspectJ configuration | `META-INF/aop.xml` được tìm thấy và khai báo đúng aspect | Log `using configuration .../META-INF/aop.xml`, `register aspect ...` |
| Target eligibility | Target type không bị `<include>`/`<exclude>` loại và được load dưới ClassLoader có weaver | Log debug "weaving class" và đường đi ClassLoader đúng |
| Join point matching | Advice pointcut match method/constructor đang được kiểm tra | Log `weaveinfo ... advised by ...` và quan sát output thực tế |

Ví dụ file `src/main/resources/META-INF/aop.xml` của custom AspectJ aspect:

```xml
<aspectj>
  <weaver options="-verbose -showWeaveInfo">
    <include within="demo..*"/>
  </weaver>
  <aspects>
    <aspect name="demo.TraceAspect"/>
  </aspects>
</aspectj>
```

`-verbose` giúp quan sát weaver tìm thấy aop.xml, đăng ký aspect; `-showWeaveInfo` hiển thị join point được advise. Để debug việc class bị exclude, có thể thêm `-debug` tạm thời. Hãy giới hạn weaving trong package ứng dụng khi thích hợp để giảm chi phí và log nhiễu. Không xóa `<include>` tùy tiện để "cứ weave toàn bộ JDK/Spring".

Nếu dùng Spring-provided aspects như `AnnotationBeanConfigurerAspect` cho `@Configurable`, module `spring-aspects` đã có các AspectJ aspects và aop.xml liên quan. Cần phân biệt **có JAR** với việc Spring context cung cấp BeanFactory cho aspect; `@EnableSpringConfigured` phục vụ bước sau, không tự bật weaving.

### Ba mức độ logging AspectJ

```bash
-Daj.weaving.verbose=true
-Dorg.aspectj.weaver.showWeaveInfo=true
-Daj.weaving.debug=true
```

Hai tùy chọn hệ thống **đã được AspectJ document rõ** là `aj.weaving.verbose` và `org.aspectj.weaver.showWeaveInfo`. Với debugging class-level, cách dễ kiểm soát là thêm `-debug` vào `<weaver options="...">` trong `META-INF/aop.xml`. Không nên phụ thuộc vào thuộc tính `aj.weaving.debug` như một interface ổn định nếu chưa kiểm tra tài liệu phiên bản weaver cụ thể; dùng `-debug` trong aop.xml khi cần. Khi bật verbose lâu dài ở production, lượng log có thể rất lớn.

Ví dụ shell (minh họa vị trí JVM options):

```bash
java -Daj.weaving.verbose=true \
     -Dorg.aspectj.weaver.showWeaveInfo=true \
     -javaagent:/path/to/aspectjweaver.jar \
     -cp "target/classes:dependency-jars/*" demo.App
```

Đây là **khung lệnh trên môi trường dùng dấu hai chấm cho classpath**, không phải lệnh chạy ngay trên Windows. Ở Windows, classpath separator là dấu chấm phẩy; Maven launcher ví dụ bên dưới tự xây classpath nên đa nền tảng hơn.

## 5. Các dấu hiệu dễ nhầm trong production

### 5.1. Chỉ thêm aspectjweaver.jar

Có dependency `org.aspectj:aspectjweaver` nhưng **không có `-javaagent` và không cài Spring LTW transformer**: AspectJ APIs có trên classpath, nhưng không có bằng chứng nào rằng custom `@Aspect` hoặc `spring-aspects` đã weave target. Đây thường là lỗi thứ nhất cần loại trừ.

### 5.2. Dùng @EnableAspectJAutoProxy để "bật AspectJ"

`@EnableAspectJAutoProxy` chỉ kích hoạt Spring @AspectJ-style proxying. Annotation này không parse `META-INF/aop.xml` để biến đổi class; nếu aspect của bạn được register như bean, advice có thể chạy **qua proxy** mà không có LTW.

### 5.3. Dùng @EnableLoadTimeWeaving nhưng thiếu instrumentation

`@EnableLoadTimeWeaving` kích hoạt hạ tầng Spring LTW. Nhưng trong môi trường JVM thông thường, classloader phải hỗ trợ transformation; có thể cần `-javaagent:/path/to/spring-instrument.jar`. Đừng nhầm agent này với `aspectjweaver.jar`: Spring agent chủ yếu cung cấp `Instrumentation` cho Spring `InstrumentationLoadTimeWeaver`; AspectJ weaver agent trực tiếp đảm nhiệm AspectJ LTW trên JVM.

### 5.4. Đặt -javaagent sai JVM

IDE, test runner, Surefire, Gradle daemon/Maven JVM, executable jar và container có thể dùng **các JVM khác nhau**. Thêm agent cho JVM chạy Maven không có nghĩa test JVM forked hoặc application JVM được gắn agent, và ngược lại. Luôn kiểm tra process chạy target class và startup arguments thực tế.

### 5.5. Class load trước khi cài transformer

LTW thông thường xử lý class tại lúc load. Class đã được ClassLoader định nghĩa trước khi transformer sẵn sàng sẽ không được weave hồi tố chỉ vì sau đó Spring context bật LTW. Nếu target class được dùng trong static initialization hoặc startup quá sớm, hãy kiểm tra thứ tự khởi động.

### 5.6. Có aop.xml nhưng không thấy matching advice

AspectJ có thể tìm thấy aop.xml và register aspect nhưng không weave join point vì pointcut không khớp FQCN/signature, type bị exclude, hoặc các classes được load bởi ClassLoader không tìm thấy config/aspect. `register aspect` chưa chứng minh `weaveinfo`.

### 5.7. @Aspect chạy hai lần

Nếu cùng advice được **Spring auto-proxy** và **AspectJ LTW** áp dụng cho cùng method, một invocation có thể bị xử lý hai lần. Kiểm tra `Advised.getAdvisors()` và weaveinfo, rồi chọn một mechanism rõ ràng cho concern đó. Đừng chữa triệu chứng bằng cách tắt cả hai mà chưa xác định ai sở hữu advice.

## 6. Ví dụ Java 17 chạy được: cùng một method, proxy và LTW cho kết quả khác nhau

Ví dụ tạo **một source project với hai cách chạy** để có kiểm chứng tái lập được:

- **Proxy mode:** đăng ký Spring `@Aspect` như Spring bean và bật `@EnableAspectJAutoProxy`. Gọi `work.outer()` qua proxy, bên trong `this.inner()` **bỏ qua advice**; gọi `work.inner()` trực tiếp qua proxy thì advice chạy.
- **LTW mode:** không đăng ký Spring auto-proxy. `TraceAspect` được AspectJ agent đăng ký bằng `aop.xml`, woven vào method execution của `Work.inner()`. Cả `this.inner()` và `work.inner()` đều được advice áp dụng. Spring vẫn tạo `Work` bean, nhưng `AopUtils.isAopProxy(work)` là `false`.

Chỉ dùng một mechanism cho mỗi lần chạy để không vô tình áp dụng cùng aspect hai lần.

### 6.1. Cấu trúc thư mục

```text
spring-aop-deployment-check/
  pom.xml
  src/main/java/demo/
    App.java
    Work.java
    TraceAspect.java
  src/main/resources/META-INF/
    aop.xml
```

### 6.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-aop-deployment-check</artifactId>
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
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-aop</artifactId>
      <version>6.2.19</version>
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
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-dependency-plugin</artifactId>
        <version>3.7.1</version>
        <configuration>
          <artifactItems>
            <artifactItem>
              <groupId>org.aspectj</groupId>
              <artifactId>aspectjweaver</artifactId>
              <version>1.9.25.1</version>
              <outputDirectory>${project.build.directory}</outputDirectory>
              <destFileName>aspectjweaver.jar</destFileName>
            </artifactItem>
          </artifactItems>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>exec-maven-plugin</artifactId>
        <version>3.5.1</version>
        <configuration>
          <executable>java</executable>
          <arguments>
            <argument>-javaagent:${project.build.directory}/aspectjweaver.jar</argument>
            <argument>-Ddemo.weave=true</argument>
            <argument>-cp</argument>
            <classpath/>
            <argument>demo.App</argument>
          </arguments>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

Maven `dependency:copy` tạo file `target/aspectjweaver.jar`. Goal `exec:exec` khởi chạy **một JVM riêng** có `-javaagent`; không dùng `exec:java` cho LTW trong ví dụ vì goal đó chạy main trong Maven JVM đã khởi động mà không mang agent mong muốn.

### 6.3. Work.java

`src/main/java/demo/Work.java`:

```java
package demo;

public class Work {
    public void outer() {
        System.out.println("outer");
        this.inner();
    }

    public void inner() {
        System.out.println("inner");
    }
}
```

Class không `final`, hai method public; Spring class-based proxy có thể advise. `this.inner()` cố ý là một lần **self-invocation** để phân biệt proxy mode với woven code.

### 6.4. TraceAspect.java

`src/main/java/demo/TraceAspect.java`:

```java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(* demo.Work.inner(..))")
    public void beforeInner() {
        System.out.println("trace-before inner");
    }
}
```

Cùng một aspect class được dùng **theo hai đường khác nhau**:

- Proxy mode: Spring quản lý instance aspect do `@Bean` cung cấp.
- LTW mode: AspectJ agent đọc khai báo `<aspect name="demo.TraceAspect"/>` trong aop.xml và áp dụng aspect qua weaving; Spring không cần tạo bean aspect để lời gọi chạy.

### 6.5. App.java

`src/main/java/demo/App.java`:

```java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class App {
    @Configuration(proxyBeanMethods = false)
    public static class BaseConfig {
        @Bean
        Work work() {
            return new Work();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class ProxyConfig {
        @Bean
        TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    public static void main(String[] args) {
        boolean weaving = Boolean.getBoolean("demo.weave");

        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(BaseConfig.class);
            if (!weaving) {
                context.register(ProxyConfig.class);
            }
            context.refresh();

            Work work = context.getBean(Work.class);
            System.out.println("springProxy=" + AopUtils.isAopProxy(work));
            work.outer();
            work.inner();
        }
    }
}
```

Khi `demo.weave=false`, chỉ `ProxyConfig` bật auto-proxy và đăng ký aspect Spring. Khi `demo.weave=true`, `ProxyConfig` không đăng ký; aspect hoạt động nếu AspectJ agent weave class. `AopUtils.isAopProxy` là công cụ **kiểm tra Spring proxy**, không dùng để quyết định LTW đã thành công hay chưa.

### 6.6. aop.xml

`src/main/resources/META-INF/aop.xml`:

```xml
<aspectj>
  <weaver options="-verbose -showWeaveInfo">
    <include within="demo..*"/>
  </weaver>
  <aspects>
    <aspect name="demo.TraceAspect"/>
  </aspects>
</aspectj>
```

Spring proxy mode không tự dùng file này. Ở LTW mode, AspectJ agent đọc file từ classpath, đăng ký `TraceAspect` và weave `Work.inner()` nếu type/method match. `<include within="demo..*"/>` chỉ giới hạn các type thuộc package ứng dụng để tránh weave quá rộng.

### 6.7. Chạy proxy mode (không dùng agent)

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
```

Các dòng `System.out` mà mã minh họa tạo theo thứ tự:

```text
springProxy=true
outer
inner
trace-before inner
inner
```

Tại `work.outer()`, proxy nhận lời gọi `outer` rồi chuyển cho target; `this.inner()` trong target không đi qua proxy lần nữa, nên không có dòng `trace-before inner` trước dòng `inner` thứ nhất. Lần `work.inner()` được client gọi trực tiếp qua proxy nên advice chạy.

Vì demo dùng `proxyTargetClass = true` và class `Work` không final, bean proxy có thể được lấy qua `Work.class`. Nếu chuyển sang interface/JDK proxy trong ứng dụng khác, cần inject theo interface.

### 6.8. Chạy AspectJ LTW mode (JVM có agent)

```bash
mvn -q compile dependency:copy exec:exec
```

Các dòng `System.out` mà mã ứng dụng tạo theo thứ tự, **nếu agent đã weave thành công**:

```text
springProxy=false
outer
trace-before inner
inner
trace-before inner
inner
```

Lúc này `work` là Spring bean bình thường **không phải Spring AOP proxy**, nhưng method execution `Work.inner()` đã được woven. Do đó cả `this.inner()` khi gọi từ `outer` và `work.inner()` khi caller gọi bên ngoài đều có AspectJ advice. Ngoài stdout này, agent có thể in verbose/weaveinfo ra console theo `aop.xml`.

**Điều cần xác minh thực tế trong log LTW:** có dấu hiệu aop.xml được đọc, `TraceAspect` được đăng ký, và weaveinfo cho join point `execution(void demo.Work.inner())` hoặc biểu diễn tương đương. Nếu in `springProxy=false` **mà không có các dòng trace**, chưa thể kết luận LTW hoạt động.

### 6.9. Hai phép kiểm chứng độc lập

- **Proxy test:** kiểm tra `springProxy=true`, direct `work.inner()` được intercept, self-invocation không được intercept.
- **LTW test:** kiểm tra `springProxy=false`, *self-invocation vẫn được intercept*, và console có weaveinfo xác nhận AspectJ đã weave method execution.

Nếu chạy cả proxy và agent đồng thời bằng cách đăng ký `ProxyConfig` trong LTW mode, example có thể áp dụng advice trùng. Không dùng cấu hình đó như baseline.

Ví dụ được cấu trúc để kiểm tra lỗi config ngay tại integration boundary. Khi đưa vào project lớn, chọn version Spring/AspectJ phù hợp với BOM/JDK thực tế và chạy hai mode bằng **hai JVM sạch**, tránh kết luận từ một process đã load các class trước.

## 7. Quy trình phân biệt lỗi từng bước trong ứng dụng Spring thực tế

### Bước 1: xác định cơ chế chịu trách nhiệm

Với `@Transactional`, xem `@EnableTransactionManagement(mode = ...)`; với `@Cacheable` xem `@EnableCaching(mode = ...)`; với `@Async` xem `@EnableAsync(mode = ...)`. Mặc định thường là `AdviceMode.PROXY`. `AdviceMode.ASPECTJ` yêu cầu Spring-provided aspect phù hợp, `spring-aspects` và CTW/LTW thật sự.

Với custom `@Aspect`, kiểm tra nó đang là Spring bean để auto-proxy hay được khai báo trong aop.xml để weaving.

### Bước 2: xác minh object và đường gọi

In `AopUtils.isAopProxy(bean)`; với proxy xem `Advised.getAdvisors()`. Gọi trực tiếp method qua bean lấy từ context rồi so với self-invocation. Nếu object không phải Spring bean mà do `new` tạo, proxy infrastructure sẽ không tự biết để intercept.

### Bước 3: xác minh điểm khớp

Dùng pointcut hẹp như `execution(* demo.Work.inner(..))` để loại trừ nhầm package. Với custom annotation, kiểm tra import và vị trí annotation ở **implementation** nếu weaving yêu cầu. Với method non-public/final, phân biệt giới hạn JDK/CGLIB proxy với AspectJ method execution weaving.

### Bước 4: nếu là LTW, kiểm tra bootstrap và classpath

Kiểm tra JVM chứa ứng dụng có `-javaagent` hoặc Spring LoadTimeWeaver tích hợp phù hợp. Xem file `META-INF/aop.xml` có nằm trong runtime classpath không. Nếu chạy executable fat JAR, IDE, WAR hoặc test framework, classloading khác nhau có thể làm config không được thấy. Một AspectJ agent đang chạy ở JVM khác không giúp được process target.

### Bước 5: quan sát registration và weaveinfo

Bật `-verbose -showWeaveInfo` trong aop.xml hoặc đúng JVM properties. Tìm:

1. Weaver nhận đúng configuration: `using configuration ... aop.xml`.
2. `register aspect ...` cho aspect dự kiến.
3. `weaveinfo` chứng minh target join point được advise.
4. Output Java thực tế chứng minh lời gọi đã chạy advice.

Nếu bước 1–2 có nhưng bước 3 không có, kiểm tra `<include>`/`<exclude>`, FQCN, pointcut, classloader và class-load timing trước khi nghi Spring IoC.

### Bước 6: kiểm tra bean infrastructure tương ứng

`spring-aspects` cung cấp `AnnotationBeanConfigurerAspect` cho `@Configurable` nhưng vẫn cần `@EnableSpringConfigured` để nối BeanFactory. `AnnotationTransactionAspect` đòi transaction manager; `AnnotationCacheAspect` cần cache infrastructure/manager; `AnnotationAsyncExecutionAspect` cần executor/configuration. **Weaving thành công** không chứng minh tính năng Spring đã được cấu hình đầy đủ.

### Bước 7: xác minh môi trường triển khai và regression tests

Chạy lại trên JVM startup giống production (agent options và ClassLoader). Test ít nhất: gọi trực tiếp qua proxy, self-invocation, method không khớp pointcut, exception và behavior riêng của transaction/cache nếu dùng. Lưu lệnh chạy đúng trong deployment config; test chỉ kiểm tra reflection annotation không đủ xác nhận advice thực thi.

## 8. Bảng tra kích hoạt ở deployment

| Thành phần tìm thấy | Bằng chứng cần tìm tiếp |
| --- | --- |
| `@EnableAspectJAutoProxy` và aspect Spring bean | Spring proxy/`Advised.getAdvisors()`, direct method call tạo advice event |
| `-javaagent:aspectjweaver.jar` | Agent log, `aop.xml`, target weave info và thực thi join point |
| `@EnableLoadTimeWeaving` và Spring instrumentation | `loadTimeWeaver`, transformer, defining ClassLoader, đúng thứ tự load |
| `ajc` trong build | Bytecode đã woven và artifact deploy đúng |

Proxy bean tồn tại không chứng minh AspectJ LTW; ngược lại woven advice có thể chạy trên object không phải proxy. Với dấu hiệu hai advice cùng áp dụng policy, thử **proxy-only, weaving-only, both** trong ba cấu hình JVM tách biệt để xác định ownership; `spring-instrument` chỉ cung cấp instrumentation trong tuyến Spring, `aspectjweaver` mới thực hiện weaving.

## 9. Với @Transactional/@Cacheable, đừng nhầm lỗi LTW với proxy

Một method anotate `@Transactional` mà transaction không active **không chứng minh** LTW đã hỏng, bởi ứng dụng có thể chưa bao giờ bật AspectJ transaction mode. Trong proxy mode, kiểm tra bean Spring, transaction advisor, `PlatformTransactionManager`, self-invocation và exception/rollback rules.

Ví dụ:

```java
@Configuration
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
class TxAspectjConfig {
    // Transaction manager phải được cung cấp bởi Spring config khác.
}
```

Ở đây cần Spring-aspects, weaving implementation method và transaction manager thực sự quản lý DataSource/JPA tương ứng. `@Transactional` đặt chỉ ở interface không được AspectJ transaction weaving nhận diện giống proxy mode; đặt trên class/method implementation.

Tương tự caching:

```java
@Configuration
@EnableCaching(mode = AdviceMode.ASPECTJ)
class CacheAspectjConfig {
    // CacheManager phải được cấu hình riêng.
}
```

Spring-aspects có `AnnotationCacheAspect` nhưng AspectJ cần weave target class. Nếu `CacheManager` không có, lỗi cấu hình cache không phải lỗi weaver. Nếu cache miss/hit khác dự kiến, kiểm tra key, provider, self-invocation, aspect mode trước khi đổ lỗi pointcut chung.

**Quan trọng về kiểm chứng:** Một advice tự viết có thể chạy từ Spring proxy trong khi `@Transactional` dùng AspectJ mode hoặc ngược lại. Phân tích **theo từng concern** và ai sở hữu advisor/AspectJ aspect; không kết luận cả ứng dụng chỉ có một loại AOP.

## 10. Chẩn đoán nhanh theo thứ tự ít tốn công

| Hiện tượng trong log/test | Đọc kết quả | Sửa chỗ nào trước |
| --- | --- | --- |
| `AopUtils.isAopProxy=false`, không có agent | Không có bằng chứng proxy hay weave | Kiểm tra chọn mode, cấu hình bean/proxy |
| `AopUtils.isAopProxy=true`, advisor đúng, direct call có trace nhưng `this.inner()` không | Proxy hoạt động đúng semantics | Thiết kế bean boundary/đường gọi; không coi là lỗi LTW |
| `springProxy=false`, có weaveinfo và self-invocation có trace | LTW hoạt động | Nếu còn lỗi nghiệp vụ, kiểm tra Spring manager/config |
| `register aspect`, không có weaveinfo match method | Aspect đã được nhận nhưng advice chưa weave join point này | Pointcut/type pattern, includes, classloader |
| Không có `using configuration aop.xml` | Weaver không thấy cấu hình hoặc agent không chạy đúng JVM | Startup flags, classpath, `META-INF/aop.xml` |
| Có weaveinfo nhưng advice vẫn không chạy tại call mong đợi | Có weaving cho một join point; call/test có thể khác đường | Kiểm tra signature và instance/type thực tế |
| Proxy và LTW đều hoạt động, trace lặp hai lần | Cùng concern có thể bị intercept hai lần | Xác định mechanism sở hữu aspect, loại cấu hình trùng |
| Local đúng, server sai | Runtime/classloader/startup khác | So sánh exact JVM args, packaging và weave logs |

Khi diagnostic theo quy trình này, nên lưu **bằng chứng** trong CI/log: Spring bean type, proxy/advisor list nếu liên quan, agent arguments, aop.xml path đã load, aspect registration, weaveinfo và output từ một lời gọi kiểm chứng. Đừng chụp mỗi annotation rồi tuyên bố triển khai hoạt động.

## 11. Lỗi thực tế và cách phòng tránh

### Chạy maven exec:java mà tưởng đã bật -javaagent

`exec:java` chạy trong Maven process; thêm `-javaagent` vào **application arguments** không làm JVM đã chạy tự có transformer. Khi test LTW, dùng `exec:exec` để chạy JVM mới, hoặc cấu hình JVM của IDE/test runner đúng thời điểm.

### Nhầm Spring Boot tự động bật weaving

Spring Boot có thể tự bật proxy và các feature thông qua auto-configuration, nhưng **không suy ra** mọi `@Aspect` đã được AspectJ woven. Kiểm tra `AdviceMode`, JVM flags và aop.xml như với Spring Framework thuần.

### Chỉ nhìn Class.forName hoặc một bean proxy

`Class.forName("demo.Work")` xác nhận class được load, không xác nhận nó được weave; có thể còn khiến class load quá sớm nếu Spring LTW transformer chưa cài. `AopUtils.isAopProxy` xác nhận proxy, không xác nhận AspectJ weaving.

### Điểm cắt sai do class hoặc annotation khác

`execution(* demo.Work.inner(..))` chỉ khớp đúng FQCN/method signature phù hợp. Trong Spring proxy mode, pointcut có thể nhìn target type và proxy method khác nhau tùy JDK/CGLIB. Với AspectJ, phải quan sát **woven join point** thực tế. Đừng dùng `within(..)` quá rộng để che một lỗi nhập FQCN.

### Cấu hình nhiều ApplicationContexts

`@EnableAspectJAutoProxy` áp dụng với bean trong ApplicationContext mà nó quản lý; aspect ở web child context không tự apply lên mọi service trong root context. LTW lại phụ thuộc ClassLoader/instrumentation, có thể phủ rộng hơn context nhưng Spring-provided aspect vẫn cần BeanFactory/manager được cấu hình đúng. Hai ranh giới này khác nhau.

### Chạy agent trên module/JDK/classloader đặc biệt

Nếu có custom ClassLoader, JPMS module restrictions hoặc fat JAR loader, weaver có thể không load/configure target như khi chạy trực tiếp từ `target/classes`. Xem lỗi truy cập module/classloading và dùng các `--add-opens` phù hợp **khi có lỗi thực tế được xác minh**, tránh mở module hàng loạt theo suy đoán.

### Mặc định mong đợi LTW intercept mọi thứ

Một AspectJ aspect chỉ tác động **những join point mà pointcut thực sự match và target được weave**. Không thể kết luận "có agent = mọi final/private method đều advised". Với `spring-aspects`, mỗi aspect có phạm vi riêng; ví dụ transaction advice cần annotation được nhận diện.

## 12. Checklist hoàn thành triển khai

- [ ] Xác định rõ concern dùng **Spring proxy advice** hay **AspectJ woven advice**.
- [ ] Ghi đúng dependency và phiên bản Spring/AspectJ phù hợp JDK.
- [ ] Proxy mode: `@EnableAspectJAutoProxy`, aspect bean, target bean và pointcut đều đúng ApplicationContext.
- [ ] Proxy mode: `AopUtils.isAopProxy` và `Advised.getAdvisors()` xác nhận bean/advisor thực tế.
- [ ] Proxy mode: direct external call và self-invocation cho kết quả đúng semantics.
- [ ] LTW: agent hoặc Spring LoadTimeWeaver thực sự gắn vào **JVM chứa target** trước khi class load.
- [ ] LTW: `META-INF/aop.xml` hiện diện trong runtime classpath và aspect được register.
- [ ] LTW: `-showWeaveInfo` cho thấy join point target thực sự được advised.
- [ ] LTW: behavior test qua self-invocation/object `new` chứng minh việc weave theo use case.
- [ ] Spring-provided AspectJ aspects có BeanFactory/TransactionManager/CacheManager/Executor thích hợp.
- [ ] Kiểm tra không chạy **double advice** do proxy và weaving trùng nhau.
- [ ] Test cùng một command/JVM flags/classloader gần với production, kèm log đủ để tái hiện lỗi.
- [ ] Với nhiều contexts, ghi rõ bean ownership và nơi cấu hình proxy/LTW.
- [ ] Chỉ tuyên bố cấu hình đúng khi có **bằng chứng thực thi**, không dừng ở việc có annotation/dependency.

## 13. Câu hỏi tự kiểm tra

**1. `@EnableAspectJAutoProxy` và `@EnableLoadTimeWeaving` có tương đương không?**  
Không. Cái thứ nhất tạo Spring proxies từ @AspectJ-style aspect beans; cái thứ hai kích hoạt hạ tầng Spring LTW trên ClassLoader có khả năng transformation.

**2. Vì sao `AopUtils.isAopProxy=false` mà advice vẫn chạy trong LTW mode?**  
Vì AspectJ đã biến đổi bytecode của `Work.inner()`; đối tượng không cần Spring proxy mới có advice.

**3. Có `register aspect` trong log đã đủ chứng minh target được woven chưa?**  
Chưa. Cần weaveinfo cho join point cần thiết và test hành vi thực tế.

**4. Trong proxy mode, vì sao gọi `work.inner()` có advice nhưng `this.inner()` từ `outer()` lại không?**  
Vì gọi trong target qua `this` bỏ qua proxy. AspectJ weaving có thể xử lý self-invocation trên woven method execution.

**5. Nếu `@Transactional` self-invocation thất bại, có phải aspectjweaver bị hỏng?**  
Không thể kết luận. Đầu tiên kiểm tra transaction đang ở `AdviceMode.PROXY` hay `ASPECTJ`, sau đó xem manager, weaving và annotation placement.

**6. `-javaagent:aspectjweaver.jar` và `-javaagent:spring-instrument.jar` là cùng một việc?**  
Không. AspectJ agent trực tiếp cung cấp LTW; Spring instrument agent cung cấp instrumentation cho Spring LoadTimeWeaver và cần Spring đăng ký transformer phù hợp.

**7. Có thể xác minh weaving chỉ từ tên class proxy kiểu `$$SpringCGLIB$$` không?**  
Không. Tên proxy nói về Spring proxying, không chứng minh AspectJ bytecode weaving.

## 14. Nguồn chính thức

- Spring Framework 6.2 — Using AspectJ with Spring Applications: https://docs.spring.io/spring-framework/reference/6.2/core/aop/using-aspectj.html
- Spring Framework 6.2 — Proxying mechanisms và self-invocation: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- Spring Framework — Enabling @AspectJ support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — @EnableLoadTimeWeaving Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/context/annotation/EnableLoadTimeWeaving.html
- Spring Framework — AopUtils Javadoc: https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/support/AopUtils.html
- AspectJ — Load-Time Weaving: https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html
- AspectJ — Problem Diagnosis Guide: https://eclipse.dev/aspectj/doc/latest/pdguide/messages.html


</details>

- [Quay lại đầu trang](#back-to-top)
