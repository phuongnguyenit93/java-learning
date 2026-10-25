<a id="back-to-top"></a>

# Chẩn đoán lỗi

## Menu
- [Aspect chưa được đăng ký bean hoặc chưa bật auto-proxy](#aop-troubleshooting-001)
- [Pointcut không khớp do chữ ký, package, tên bean hoặc annotation](#aop-troubleshooting-002)
- [Phương thức đúng nhưng lời gọi không đi qua proxy](#aop-troubleshooting-003)
- [Bean được khởi tạo sớm hoặc lấy từ sai ApplicationContext](#aop-troubleshooting-004)
- [Ép kiểu sai do cơ chế proxy không như dự kiến](#aop-troubleshooting-005)
- [final/private hoặc class loader làm proxy không hoạt động](#aop-troubleshooting-006)
- [Lỗi binding tham số, argNames hoặc thiếu tên tham số](#aop-troubleshooting-007)
- [Advice chạy nhiều lần do proxy lồng hoặc cấu hình trùng](#aop-troubleshooting-008)
- [Thứ tự khác kỳ vọng giữa nhiều aspect và advisor](#aop-troubleshooting-009)
- [Ngoại lệ bị che khuất hoặc giá trị trả về bị đổi bởi @Around](#aop-troubleshooting-010)
- [Breakpoint, nhật ký hạ tầng Spring và kiểm tra danh sách advisor](#aop-troubleshooting-011)
- [Quy trình lần theo bean → proxy → pointcut → advice → phương thức đích](#aop-troubleshooting-012)

## <a id="aop-troubleshooting-001">Aspect chưa được đăng ký bean hoặc chưa bật auto-proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Aspect chưa được đăng ký bean hoặc chưa bật auto-proxy trong Spring AOP

## 1. Triệu chứng và nguyên nhân chính

Bạn có một class đánh dấu `@Aspect`, khai báo advice bằng `@Before`, `@Around`, `@AfterReturning` hoặc `@AfterThrowing`, nhưng khi gọi service thì **advice không chạy**. Business method vẫn trả kết quả bình thường, không có log, counter, audit hay hành vi mà aspect phải thực hiện.

Trước khi sửa expression của pointcut, hãy kiểm tra **hai điều kiện hạ tầng độc lập**:

1. **Aspect phải được đăng ký thành bean trong Spring `ApplicationContext` thích hợp.** Annotation `@Aspect` **không tự làm class được component scanner tìm thấy**. Muốn quét tự động, aspect cần stereotype như `@Component` và package phải nằm trong vùng scan; hoặc đăng ký trực tiếp bằng `@Bean`, XML hay import một configuration tương ứng.
2. **Spring phải bật `@AspectJ` auto-proxy.** Trong Spring Framework thuần, thường dùng `@EnableAspectJAutoProxy` hoặc `<aop:aspectj-autoproxy/>`; trong Spring Boot có thể do AOP auto-configuration bật. Chỉ có aspect bean, nhưng không bật auto-proxy, sẽ không tự tạo Spring AOP proxy để áp advice.

Sau hai điều kiện, còn phải xác minh target là **Spring bean được proxy**, pointcut **match method thực tế** và **lời gọi đi qua proxy**. Một raw object tạo bằng `new` hoặc self-invocation không tự được Spring AOP intercept.

> Trọng tâm bài: chẩn đoán riêng cơ chế đăng ký Spring `@Aspect` và kích hoạt Spring AOP auto-proxy. Đây là cơ chế **Spring AOP proxy**, khác AspectJ compile-time/load-time weaving. Không đi sâu AOP tổng quát.

## 2. Luồng phải hoàn chỉnh để một advice thực thi

~~~text
Class có @Aspect + @Before/@Around/...
              |
              v
Aspect được Spring đăng ký làm bean?
  @Component + component scan
  hoặc @Bean / @Import / XML
              |
              v
@EnableAspectJAutoProxy được bật
  hoặc <aop:aspectj-autoproxy/>
  hoặc Boot auto-configuration phù hợp
              |
              v
Spring tạo advisors từ aspect bean
              |
              v
Target cũng là Spring bean
              |
              v
Method match pointcut và có thể được proxy
              |
              v
Client nhận đúng bean PROXY
              |
              v
Gọi proxy.method()
              |
              v
Advisor chain thực thi advice -> target method
~~~

Bảng chẩn đoán:

| Aspect là bean? | `@AspectJ` auto-proxy bật? | Hành vi dự kiến từ riêng aspect này |
| --- | --- | --- |
| Không | Không | Không có aspect advisor, không tạo proxy từ aspect này |
| Không | Có | Spring có infrastructure nhưng **không có aspect bean để đọc advice** |
| Có | Không | Aspect hiện diện như một Spring object, nhưng **không tự được áp lên target bằng proxy** |
| Có | Có | Đủ điều kiện đăng ký; vẫn cần **pointcut match**, proxy hợp lệ và lời gọi qua proxy |

Một `isAopProxy(bean) == true` **không chứng minh** chính aspect này chạy: bean có thể được proxy bởi transaction/caching/security hoặc advisor khác. Hãy assert thêm side effect của advice.

## 3. Sai lầm thứ nhất: nhầm @Aspect với @Component

Class sau **chưa tự đăng ký làm bean**:

~~~java
package demo.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* demo.service..*(..))")
    public void log() {
        System.out.println("audit advice");
    }
}
~~~

`@Aspect` là thông tin để Spring **nhận diện một aspect bean**; nó không phải annotation stereotype do component scanner mặc định xem như một Spring component.

### Sửa cách 1: @Component + component scanning

~~~java
package demo.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    @Before("execution(* demo.service..*(..))")
    public void log() {
        System.out.println("audit advice");
    }
}
~~~

Và nạp đúng các package:

~~~java
@Configuration
@ComponentScan(basePackages = {"demo.aop", "demo.service"})
@EnableAspectJAutoProxy
public class ApplicationConfig {
}
~~~

Đây là **fragment cấu hình**, cần imports tương ứng trong `org.springframework.context.annotation`. Nếu chỉ scan `demo.service` thì `AuditAspect` ở `demo.aop` vẫn vắng mặt.

### Sửa cách 2: @Bean hoặc @Import

~~~java
@Configuration
@EnableAspectJAutoProxy
public class ApplicationConfig {
    @Bean
    AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
~~~

Khi một class aspect được tạo bởi `@Bean`, không cần gắn thêm `@Component`. Điều quan trọng là **configuration này thực sự được Spring load**. Có file `ApplicationConfig.java` trong source tree không đồng nghĩa nó đã được import vào context.

Ví dụ tách module:

~~~java
@Configuration
@Import({BusinessConfiguration.class, AspectConfiguration.class})
@EnableAspectJAutoProxy
public class MainConfiguration {
}
~~~

Đoạn trên là **fragment**, giả định hai class configuration đã tồn tại. `@Import` là cách rõ ràng để gom các bean definitions.

### Sửa cách 3: XML

~~~xml
<bean id="auditAspect" class="demo.aop.AuditAspect"/>
<aop:aspectj-autoproxy/>
~~~

Đây là fragment XML phải nằm trong file `<beans>` có namespace/schema AOP đúng. `<bean>` chỉ giải quyết registration; phần `<aop:aspectj-autoproxy/>` mới giải quyết auto-proxy.

**Cảnh báo:** không tự đăng ký cùng một aspect nhiều lần bằng `@Component` lẫn `@Bean` trừ khi có chủ đích. Nhiều aspect instances có thể làm cùng advice chạy trùng, hoặc gây lỗi tên bean và quản lý state.

## 4. Sai lầm thứ hai: aspect bean tồn tại nhưng auto-proxy không bật

Một class được Spring quản lý không mặc nhiên là advice:

~~~java
@Configuration
public class AdviceConfig {
    @Bean
    AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
~~~

Trong một Spring context tối giản **không có** `@EnableAspectJAutoProxy`, không có `<aop:aspectj-autoproxy/>` và không có auto-proxy creator tương đương được bật bởi nơi khác, aspect chỉ là bean thông thường. Spring không tự chuyển các annotation advice thành proxy.

Cấu hình đúng trong Spring Framework:

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class AopConfiguration {
}
~~~

`@EnableAspectJAutoProxy` cấu hình Spring `@AspectJ`-aware auto-proxying, dùng advisor để tạo proxy cho target bean **nếu có method match**. Nó **không tự đăng ký tất cả class có `@Aspect` trên classpath**.

Ứng dụng cần thư viện AspectJ để Spring phân tích và match các pointcut annotation; ví dụ dependency:

~~~groovy
implementation 'org.aspectj:aspectjweaver:1.9.22.1'
~~~

Tên artifact `aspectjweaver` không biến cấu hình ở đây thành AspectJ bytecode weaving: Spring vẫn tạo **JDK/CGLIB proxy**. Không cần chạy `ajc` hoặc `-javaagent:aspectjweaver.jar` cho ví dụ Spring AOP thuần.

### Các thuộc tính đáng phân biệt

- `proxyTargetClass=false`: không ép tạo class-based proxy; target implement interface có thể dùng JDK proxy.
- `proxyTargetClass=true`: yêu cầu class-based proxy, có ràng buộc với `final class`, `final method`, `private method` và khả năng subclass/override.
- `exposeProxy=true`: cho phép `AopContext.currentProxy()` khi invocation đang diễn ra qua proxy và infrastructure đã expose; không mặc định và **không sửa lỗi aspect thiếu bean**.
- Spring infrastructure có thể hợp nhất auto-proxy configuration từ nhiều nguồn; không nhất thiết chỉ có một annotation quyết định proxy thực tế. Hãy nhìn vào **proxy bean và hành vi runtime**.

### Parent/child ApplicationContext

`@EnableAspectJAutoProxy` có hiệu lực trong **ApplicationContext nơi cấu hình được khai báo**. Nếu app web có root context, servlet child context hoặc nhiều contexts cho modules, bật ở root không tự động bảo đảm bean chỉ được định nghĩa trong child có cùng auto-proxy setup. Cần xác định chính context tạo target, chứa aspect bean và đăng ký auto-proxy.

## 5. Spring Boot: không nhất thiết phải viết @EnableAspectJAutoProxy

Spring Boot có AOP auto-configuration. Nếu điều kiện dependency/configuration phù hợp và AspectJ có trên classpath, Boot **có thể tự kích hoạt `@AspectJ` auto-proxy**. Vì vậy, không thấy annotation `@EnableAspectJAutoProxy` trong mã nguồn Boot **chưa đủ chứng minh AOP chưa bật**.

Các thuộc tính quan trọng:

~~~properties
spring.aop.auto=true
spring.aop.proxy-target-class=true
~~~

Trong những dòng Boot hiện đại có AOP auto-configuration:

- `spring.aop.auto` điều khiển việc Boot tự bật AOP (`false` có thể ngăn **Boot auto-configuration** tạo auto-proxy creator).
- `spring.aop.proxy-target-class` chọn class-based hay interface-based proxy; **không phải** công tắc kích hoạt advice.
- Một config tự bật `@EnableAspectJAutoProxy` có thể vẫn áp dụng dù Boot auto-configuration bị tắt: phải xem toàn bộ các nguồn cấu hình.
- Aspect vẫn phải thành Spring bean, qua component scan, `@Bean`, `@Import` hoặc XML.
- `@SpringBootApplication` có vùng component scan bắt đầu từ package của main configuration. Aspect ở package anh em/module ngoài vùng này có thể không được quét.

Đừng tự thêm annotation/proxy creator lặp ở nhiều nơi để thử sửa lỗi. Với Boot hãy kiểm tra `AopAutoConfiguration` trong condition evaluation report, dependency AOP theo version và `ApplicationContext` chứa service.

## 6. Ví dụ Java 17+ kiểm chứng hai lỗi độc lập

Ví dụ dùng **Spring Framework thuần**, không dùng Boot để tránh auto-configuration vô tình bật AOP. Tạo ba context:

1. **MissingAspect:** auto-proxy đã bật, nhưng không có aspect bean.
2. **MissingAutoProxy:** aspect bean đã đăng ký, nhưng auto-proxy chưa bật.
3. **Working:** có cả aspect bean và auto-proxy. Khi gọi đúng service proxy, advice tăng counter.

Có thêm test gọi raw object để phân biệt **thiếu hạ tầng** với **gọi sai reference**.

### 6.1. Cấu trúc project

~~~text
spring-aop-diagnostics/
├── settings.gradle
├── build.gradle
└── src
    ├── main/java/demo
    │   ├── OrderApi.java
    │   ├── OrderService.java
    │   ├── AdviceCounter.java
    │   ├── AuditAspect.java
    │   └── AopConfigs.java
    └── test/java/demo
        └── AopDiagnosticsTest.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-aop-diagnostics'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation platform('org.springframework:spring-framework-bom:6.2.14')
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.11.4'
}

test {
    useJUnitPlatform()
}
~~~

`src/main/java/demo/OrderApi.java`:

~~~java
package demo;

public interface OrderApi {
    String submit(String id);
}
~~~

`src/main/java/demo/OrderService.java`:

~~~java
package demo;

public final class OrderService implements OrderApi {
    @Override
    public String submit(String id) {
        return "saved:" + id;
    }
}
~~~

`src/main/java/demo/AdviceCounter.java`:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

public final class AdviceCounter {
    private final AtomicInteger total = new AtomicInteger();

    public void increment() {
        total.incrementAndGet();
    }

    public int get() {
        return total.get();
    }
}
~~~

`src/main/java/demo/AuditAspect.java`:

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private final AdviceCounter counter;

    public AuditAspect(AdviceCounter counter) {
        this.counter = counter;
    }

    @Before("execution(* demo.OrderService.submit(..))")
    public void beforeSubmit() {
        counter.increment();
    }
}
~~~

`AuditAspect` **không có `@Component`** vì ta đăng ký nó bằng `@Bean` trong config, minh họa không cần đồng thời dùng cả hai cơ chế.

`src/main/java/demo/AopConfigs.java`:

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public final class AopConfigs {
    private AopConfigs() {
    }

    @Configuration
    public static class Core {
        @Bean
        public AdviceCounter adviceCounter() {
            return new AdviceCounter();
        }

        @Bean
        public OrderApi orderApi() {
            return new OrderService();
        }
    }

    @Configuration
    public static class AspectBean {
        @Bean
        public AuditAspect auditAspect(AdviceCounter counter) {
            return new AuditAspect(counter);
        }
    }

    // Có AOP infrastructure nhưng thiếu aspect bean.
    @Configuration
    @Import(Core.class)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class MissingAspect {
    }

    // Aspect bean tồn tại nhưng không bật AOP auto-proxy.
    @Configuration
    @Import({Core.class, AspectBean.class})
    public static class MissingAutoProxy {
    }

    // Đủ cả hai thành phần.
    @Configuration
    @Import({Core.class, AspectBean.class})
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class Working {
    }
}
~~~

`src/test/java/demo/AopDiagnosticsTest.java`:

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

public class AopDiagnosticsTest {

    @Test
    void aspectNotRegistered() {
        try (var ctx = new AnnotationConfigApplicationContext(
                AopConfigs.MissingAspect.class)) {
            assertTrue(ctx.getBeansOfType(AuditAspect.class).isEmpty());

            OrderApi service = ctx.getBean(OrderApi.class);
            AdviceCounter counter = ctx.getBean(AdviceCounter.class);

            assertFalse(AopUtils.isAopProxy(service));
            assertEquals("saved:A", service.submit("A"));
            assertEquals(0, counter.get());
        }
    }

    @Test
    void aspectBeanWithoutAutoProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(
                AopConfigs.MissingAutoProxy.class)) {
            assertEquals(1, ctx.getBeansOfType(AuditAspect.class).size());

            OrderApi service = ctx.getBean(OrderApi.class);
            AdviceCounter counter = ctx.getBean(AdviceCounter.class);

            assertFalse(AopUtils.isAopProxy(service));
            assertEquals("saved:B", service.submit("B"));
            assertEquals(0, counter.get());
        }
    }

    @Test
    void correctContextRunsAdviceThroughProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(
                AopConfigs.Working.class)) {
            assertEquals(1, ctx.getBeansOfType(AuditAspect.class).size());

            OrderApi service = ctx.getBean(OrderApi.class);
            AdviceCounter counter = ctx.getBean(AdviceCounter.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertTrue(AopUtils.isJdkDynamicProxy(service));

            assertEquals("saved:C", service.submit("C"));
            assertEquals(1, counter.get());
        }
    }

    @Test
    void rawObjectDoesNotUseSpringProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(
                AopConfigs.Working.class)) {
            AdviceCounter counter = ctx.getBean(AdviceCounter.class);

            OrderApi raw = new OrderService();
            assertFalse(AopUtils.isAopProxy(raw));
            assertEquals("saved:D", raw.submit("D"));
            assertEquals(0, counter.get());

            OrderApi proxied = ctx.getBean(OrderApi.class);
            assertEquals("saved:D", proxied.submit("D"));
            assertEquals(1, counter.get());
        }
    }
}
~~~

**Lệnh chạy** với JDK 17+ và Gradle:

~~~bash
gradle test
~~~

Nếu đã có wrapper, dùng `./gradlew test` trên Linux/macOS hoặc `gradlew.bat test` trên Windows. **Kỳ vọng:** Gradle báo `BUILD SUCCESSFUL` và **4 test pass**. Test dùng assertions thay vì log; đây là kết quả dự kiến, **chưa phải một lần chạy thực tế được ghi nhận**.

### 6.2. Đọc từng case

| Case | Aspect bean? | Auto-proxy? | Target là AOP proxy? | Counter sau 1 lần submit |
| --- | --- | --- | --- | --- |
| `MissingAspect` | Không | Có | Không, trong context tối giản này | 0 |
| `MissingAutoProxy` | Có | Không | Không, trong context tối giản này | 0 |
| `Working` | Có | Có | Có, JDK proxy | 1 |
| Raw `new OrderService()` trong `Working` | Aspect đã có ở context | Có ở context | **Không** cho raw instance | 0 cho raw call; 1 sau call qua proxy |

Chỉ `Working` có aspect advisor đúng và target proxy. `OrderService` được khai báo `final` nhưng nó implement interface `OrderApi`, vì vậy **JDK proxy vẫn hoạt động**; Spring không cần subclass `OrderService`. Nếu thay bằng CGLIB, `final` trở thành rào cản, đây là vấn đề proxy type khác với registration.

Điểm then chốt của ví dụ là việc test kiểm chứng **hai preconditions riêng**, rồi dùng một phép gọi thực trên proxy để kiểm chứng kết quả, thay vì đoán từ số annotations.

## 7. Chẩn đoán từng bước trong ứng dụng thật

### Bước 1 — Xác định đúng Spring context

Service target và aspect được tạo ở context nào? Với Spring MVC, root/servlet contexts hoặc module context, cần xác minh cả parent/child. Test lấy bean từ `context.getBean(...)`, không tự tạo service mới.

### Bước 2 — Hỏi Spring aspect bean có tồn tại hay không

~~~java
// Fragment khi đã có ApplicationContext ctx.
String[] beanNames = ctx.getBeanNamesForType(AuditAspect.class);
System.out.println(java.util.Arrays.toString(beanNames));
~~~

Nếu danh sách rỗng, hãy kiểm tra:

- Thiếu `@Component` khi định dùng component scanning.
- Package aspect nằm ngoài `@ComponentScan` hoặc scan root của Boot.
- Có `@Bean` nhưng configuration chứa nó chưa được scan/`@Import`.
- `@Profile`, `@Conditional`, `@ConditionalOnProperty`, dependency conditions không match.
- Đang hỏi nhầm `ApplicationContext` hoặc aspect chỉ được đăng ký trong context khác.

Nếu có bean, **chưa xong**: auto-proxy vẫn có thể vắng mặt.

### Bước 3 — Xác minh cơ chế auto-proxy đã được bật

Trong Spring Framework Java config, tìm `@EnableAspectJAutoProxy` được nạp vào context đó. Trong XML, tìm `<aop:aspectj-autoproxy/>`. Với Boot, kiểm tra AOP auto-configuration cùng `spring.aop.auto` và dependencies.

Cẩn trọng với những cơ chế proxy khác: `@Transactional`, `@EnableAsync`, caching... có thể tạo infrastructure riêng; sự tồn tại của chúng **không đồng nghĩa advice từ `@Aspect` đã được đăng ký đúng**.

### Bước 4 — Kiểm tra target bean và proxy

~~~java
// Fragment: OrderApi bean lấy từ context.
OrderApi bean = ctx.getBean(OrderApi.class);
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.getTargetClass(bean));
~~~

Với target đáng lẽ phải được khuyên mà `isAopProxy=false`, kiểm tra pointcut, class/method, final/visibility và cấu hình proxy. Nếu `true` nhưng aspect không chạy, có thể proxy được tạo bởi advisor khác.

### Bước 5 — Gọi **qua proxy** và assert advice riêng

Không chỉ test `service.submit("A")` trả `"saved:A"`; raw object cũng có thể trả kết quả ấy. Trong test, ghi side effect vào collaborator/counter/probe rồi assert advice thực sự tăng counter, được gọi đúng nhánh và đúng số lần.

### Bước 6 — Tạo negative control

So sánh lời gọi raw target (`new OrderService()`) với bean proxy, hoặc chọn method chắc chắn không match pointcut. Điều này giúp phát hiện test đặt nhầm reference hoặc assertion phụ thuộc state cũ.

## 8. Nếu đủ cả hai điều kiện mà advice vẫn không chạy?

Khi aspect là bean và auto-proxy đã bật, nguyên nhân tiếp theo thường là:

| Tình huống | Triệu chứng | Cách chẩn đoán |
| --- | --- | --- |
| Pointcut không match | Có config đúng nhưng method không được advise | Xem class thực, tên method, package, annotations, `execution(...)` |
| Gọi raw object | Không có advice, dù context có proxy | Dùng bean inject hoặc `getBean(...)` |
| Self-invocation `this.method()` | Method nội bộ bỏ qua AOP proxy | Tách method sang bean khác hoặc gọi qua proxy từ ngoài |
| JDK proxy bị cast sang concrete class | `ClassCastException`/injection type mismatch | Inject interface mà proxy implement |
| CGLIB và `final`/`private` method | Không thể override/intercept như kỳ vọng | Đổi kiểu proxy/thiết kế method phù hợp |
| Proxy do transaction/cache tạo | `isAopProxy=true` nhưng advice hiện tại không chạy | Assert side effect của aspect và kiểm tra advisors |
| Aspect bean/target khác context | Bean lookup có kết quả khác nhau | Kiểm tra registration scope, parent/child |
| `@AfterReturning` trên method throw | Không ghi log success | Đây là đúng behavior; xem `@AfterThrowing` |
| Future lỗi sau method return | Không có `@AfterThrowing` cho completion | Quan sát Future completion riêng nếu cần |
| `@Async` và advisor order | Advice chạy ở worker/trễ hơn kỳ vọng | Kiểm tra executor, thread, Future, đồng bộ test |
| Multiple bean instances/state | Probe thấy count bằng 0 | Đảm bảo aspect dùng đúng counter/collaborator |

**Chỉ sửa pointcut sau khi registration và auto-proxy đã được chứng minh hợp lệ.** Chạy business method thành công không chứng minh có advice.

## 9. Dùng Spring AOP diagnostics một cách an toàn

Spring hỗ trợ `org.springframework.aop.support.AopUtils`:

- `isAopProxy(object)`: bean có phải Spring AOP proxy không?
- `isJdkDynamicProxy(object)`, `isCglibProxy(object)`: kiểm tra kiểu proxy khi liên quan class/interface.
- `getTargetClass(object)`: lấy target class từ object/proxy phù hợp.

Với proxy implement `org.springframework.aop.framework.Advised`, có thể xem advisors:

~~~java
// Fragment chẩn đoán; service đã được lấy từ Spring context.
if (service instanceof org.springframework.aop.framework.Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
~~~

Không assert cứng số lượng advisor trong test nghiệp vụ: các Spring infrastructures khác có thể thêm advisor. Để xác nhận một aspect, ưu tiên behavior/probe.

Trong Spring Boot, có thể bật logging có chọn lọc:

~~~properties
logging.level.org.springframework.aop=TRACE
logging.level.org.springframework.beans.factory.support=DEBUG
logging.level.org.springframework.context.annotation=DEBUG
~~~

Các thông báo log cụ thể tùy Spring version, không dùng chúng thay thế integration assertions. Ngoài ra, auto-proxy creator có một số **internal bean names** trong vài phiên bản; không hard-code tên nội bộ đó thành business contract.

## 10. Các lỗi dễ tự tạo khi sửa nhanh

- **Thêm `@Component` nhưng package không được scan:** vẫn không có bean.
- **Chỉ thêm `@EnableAspectJAutoProxy` rồi không đăng ký aspect:** vẫn không có advisor.
- **Thêm cả `@Component` và `@Bean` cho một class aspect:** có nguy cơ trùng aspect hoặc tạo hai state instances.
- **Bật `proxyTargetClass=true` để “ép advice chạy”**: không sửa registration, còn có thể lỗi nếu target là `final`.
- **Tự `new Service()` trong test hoặc production:** raw object không đi qua Spring AOP proxy.
- **Nhầm Spring Boot với Spring Framework thuần:** Boot có auto-configuration nên việc thiếu annotation tường minh không nhất thiết là lỗi.
- **Nhầm `@AspectJ` proxy support với AspectJ weaving:** `@EnableAspectJAutoProxy` không weave bytecode.
- **Dựa vào `AopUtils.isAopProxy` một mình:** bean có thể có proxy của concern khác.
- **Bỏ qua Active Profile/Conditional:** test dùng profile A, production dùng profile B nên aspect bean không được tạo.
- **Đặt `@EnableAspectJAutoProxy` sai ApplicationContext:** target ở child context có thể không được xử lý.
- **Chỉnh `@Order` trước khi có proxy:** thứ tự advice không có ý nghĩa khi advice chưa được đăng ký.
- **Tăng log nhưng không viết test:** có thể chỉ tạo nhiều log, không chứng minh pointcut áp đúng.

## 11. Quy trình chẩn đoán tóm lược

~~~text
Advice không chạy
        |
        v
Aspect bean có trong đúng context?
  NO -> sửa @Component/scan, @Bean, @Import, conditions.
  YES
        |
        v
@AspectJ auto-proxy thực sự bật?
  NO -> @EnableAspectJAutoProxy / XML / Boot auto-config.
  YES
        |
        v
Target bean được proxy và method match?
  NO -> kiểm tra pointcut, class/interface/visibility.
  YES
        |
        v
Client gọi vào Spring proxy?
  NO -> không dùng new/raw target/self-invocation.
  YES
        |
        v
Advice tạo side effect/counter đúng?
  NO -> kiểm tra advisor order, lỗi/return, async/state.
  YES -> Spring AOP advice hoạt động như mong muốn.
~~~

**Ghi nhớ:** `@Aspect` **không tự đăng ký bean**, và một aspect bean **không tự bật auto-proxy**. Phải xác minh độc lập **registration**, **auto-proxy activation**, **pointcut/proxy match** và **lời gọi thực sự qua proxy**. Bốn test với các cấu hình cố ý thiếu một thành phần là cách học và chẩn đoán hai loại lỗi này hiệu quả hơn việc thay đổi annotation ngẫu nhiên.

## Tài liệu đối chiếu Spring

- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html)
- [Spring Framework — AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Declaring Pointcuts](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html)
- [Spring Boot — Common Application Properties](https://docs.spring.io/spring-boot/appendix/application-properties/)
- [Spring Framework 6.2 — AopUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/AopUtils.html)
- [Spring Framework 6.2 — @EnableAspectJAutoProxy Javadoc](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/context/annotation/EnableAspectJAutoProxy.html)

Khi triển khai, đối chiếu version Spring/Boot, dependencies và phạm vi component scanning của đúng ứng dụng; sau mỗi thay đổi, giữ integration test gọi qua proxy và kiểm tra side effect của advice.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-002">Pointcut không khớp do chữ ký, package, tên bean hoặc annotation</a>

<details>
<summary>Xem chi tiết</summary>

# Pointcut không khớp do chữ ký, package, tên bean hoặc annotation trong Spring AOP

## 1. Vì sao advice không chạy dù Spring đã khởi động thành công?

Một tình huống quen thuộc khi dùng **@Aspect** trong Spring: ứng dụng chạy bình thường, service vẫn trả dữ liệu đúng, nhưng **@Before**, **@AfterReturning** hoặc **@Around** không thực thi ở method mình mong đợi. Không phải mọi trường hợp đều do thiếu **@EnableAspectJAutoProxy**. Có thể Spring AOP đã được bật, aspect đã là Spring bean, target đã được proxy, nhưng **pointcut không khớp với method hoặc bean thật**.

Bài này tập trung chẩn đoán bốn nguyên nhân theo đề bài:

1. **Chữ ký method**: sai tên, kiểu trả về, số lượng/kiểu tham số, overload hoặc kiểu khai báo trong biểu thức **execution(...)**.
2. **Package / kiểu lớp**: sai đường dẫn package hoặc nhầm **\*** và **..** trong **execution(...)**, **within(...)**.
3. **Tên Spring bean**: **bean(...)** khớp với **tên bean trong ApplicationContext**, không khớp tên class một cách tự động.
4. **Annotation**: **@annotation(...)** tìm annotation trên method; annotation đặt sai vị trí, thiếu runtime retention hoặc khác vị trí giữa interface/implementation có thể làm phép match không như kỳ vọng.

**Phạm vi:** cách Spring AOP diễn giải pointcut và kiểm thử nó trên Spring bean/proxy. Không mở rộng thành một bài tổng quan AOP hoặc hướng dẫn AspectJ weaving độc lập.

Có một phân biệt quan trọng ngay từ đầu:

> **Pointcut không khớp** nghĩa là một method/bean không nằm trong tập join point được advice chọn. **Pointcut khớp nhưng lời gọi không đi qua Spring proxy** cũng làm advice không chạy, nhưng đó là lỗi đường gọi. Hai nhóm cần được kiểm tra riêng.

## 2. Điều kiện để advice của Spring Aspect được kích hoạt

Một request điển hình:

~~~text
Client
  |
  v
Spring-managed bean reference (proxy)
  |
  +-- advisor / pointcut xét method, target class,
  |   tên bean, annotation, điều kiện runtime
  |        |
  |        +-- match     -> advice -> target method
  |        |
  |        +-- no match  -> target method
  |
  +-- Nếu client có target gốc hoặc gọi this.method()
      -> bỏ qua interception tại Spring proxy
~~~

Trước khi thay biểu thức pointcut, kiểm tra:

- Aspect có **@Aspect** và được đăng ký thành **Spring bean** qua **@Component**/component scan hoặc **@Bean**?
- Auto-proxy đã được bật qua **@EnableAspectJAutoProxy** hoặc Spring Boot AOP auto-configuration?
- Target có thực sự được lấy từ **ApplicationContext** và bị AOP proxy không?
- Caller gọi qua proxy hay target tự gọi **this.method()**?
- Method có thuộc khả năng intercept của JDK/CGLIB proxy không (đặc biệt với final/private, hoặc method không nằm trên interface JDK proxy)?
- Biểu thức pointcut có thật sự match với **class, signature, bean name và annotation** của method được gọi?

Đối với Spring AOP proxy, join point chủ yếu là **method execution trên Spring bean**. Các designator kiểu AspectJ như **call(...)**, **get(...)**, **set(...)** không thuộc tập pointcut Spring AOP proxy hỗ trợ. Nếu cố dùng designator không hỗ trợ, cấu hình có thể báo lỗi khi parse thay vì âm thầm bỏ qua advice.

## 3. Chẩn đoán lỗi do chữ ký method

### 3.1. Đọc đúng biểu thức execution

Cú pháp điển hình:

~~~text
execution(modifiers-pattern? return-type-pattern
          declaring-type-pattern?method-name(parameter-pattern)
          throws-pattern?)
~~~

Ví dụ lớp cần advice:

~~~java
package demo.aop.orders;

public class OrderService {
    public String place(String orderCode) { return "OK:" + orderCode; }
    public String place(int quantity)      { return "Q:" + quantity; }
    public void cancel()                  { }
}
~~~

Một số biểu thức Spring AOP:

| Pointcut | Những method phù hợp với ví dụ |
|---|---|
| **execution(public java.lang.String demo.aop.orders.OrderService.place(java.lang.String))** | Chỉ **place(String)** |
| **execution(* demo.aop.orders.OrderService.place(..))** | Cả **place(String)** và **place(int)** |
| **execution(* demo.aop.orders.OrderService.place())** | Không có method nào: yêu cầu **0 tham số** |
| **execution(* demo.aop.orders.OrderService.place(*))** | Cả hai overload một tham số |
| **execution(* demo.aop.orders.OrderService.place(java.lang.Integer))** | Không khớp overload khai báo **int** |
| **execution(void demo.aop.orders.OrderService.place(..))** | Không khớp, vì hai overload trả **String** |
| **execution(* demo.aop.orders.OrderService.cancel())** | **cancel()** |

**Các lỗi hay gặp:**

- **()** chỉ method **không có tham số**; **(..)** khớp **0 hoặc nhiều tham số**; **(*)** khớp đúng **một tham số kiểu bất kỳ**.
- Method tên **place** không tự match **placeOrder**. Nếu muốn tiền tố, dùng **place\*(..)**.
- Kiểu khai báo **int** khác kiểu wrapper **java.lang.Integer**. **execution(...)** dựa vào khai báo signature; nó không tự áp dụng quy tắc boxing như khi gọi Java method.
- Kiểu trả về **void** khác **String**, dù method có in log hay thay đổi state giống nhau.
- Method overload có thể có return giống nhau nhưng khác tham số. Dùng tên method đơn lẻ không đủ để nhận diện.
- Chữ ký method của interface/proxy và method triển khai target có thể khác về mặt reflection hoặc annotation. Khi debug, xem method được Spring xét sau khi resolve **most-specific target method** thay vì chỉ in **proxy.getClass()**.

Nên bắt đầu bằng pointcut có phạm vi tương đối rộng để kiểm tra wiring, chẳng hạn:

~~~java
@Before("execution(* demo.aop.orders.OrderService.place(..))")
~~~

Sau khi chứng minh advice chạy, thêm ràng buộc return type, parameter, annotation và bean name **từng điều kiện một** rồi chạy lại test. Việc đổi pointcut rộng trong lúc điều tra chỉ dành cho môi trường test; đừng để biểu thức rộng không cần thiết trong production.

### 3.2. Đừng nhầm execution signature với args runtime

~~~java
@Pointcut("execution(* demo.aop.orders.OrderService.process(java.io.Serializable))")
void declaredSerializableParameter() {}

@Pointcut("args(java.io.Serializable)")
void runtimeSerializableArgument() {}
~~~

Hai biểu thức trên **không tương đương**. **execution(...)** xét kiểu được khai báo trong signature. **args(...)** kiểm tra kiểu của đối số thực tế tại runtime trong phạm vi invocation. Chẳng hạn method khai báo **Object** nhưng nhận đối tượng **String** có thể thỏa **args(java.io.Serializable)** mà không thỏa biểu thức **execution** đòi method khai báo parameter **Serializable**.

Khi test pointcut theo kiểu đối số, cần phân biệt rõ loại so khớp mong muốn. Không đổi sang **args(...)** chỉ để che lỗi signature khi yêu cầu thật là bắt một method cụ thể.

## 4. Chẩn đoán lỗi do package hoặc class pattern

Spring bean thật nằm tại:

~~~text
demo.aop.orders.OrderService
demo.aop.orders.internal.OrderRepositoryFacade
demo.aop.billing.BillingService
~~~

So sánh:

| Biểu thức | Phạm vi kiểu |
|---|---|
| **within(demo.aop.orders.\*)** | Các kiểu trực tiếp trong package **demo.aop.orders** |
| **within(demo.aop.orders..\*)** | Các kiểu trong **demo.aop.orders** và package con |
| **execution(* demo.aop.orders.\*.\*(..))** | Các method phù hợp thuộc các kiểu trực tiếp trong package **orders** |
| **execution(* demo.aop.orders..\*.\*(..))** | Các method phù hợp trong package **orders** và package con |
| **within(demo.aop.order..\*)** | Không khớp lớp trong **orders** vì tên package sai |

Một quy tắc Spring Aspect phổ biến:

~~~java
@Pointcut("within(demo.aop.orders..*)")
void inOrderPackage() {}

@Pointcut("execution(public * *(..))")
void publicMethodExecution() {}

@Before("inOrderPackage() && publicMethodExecution()")
public void beforeOrderMethod() {
    // Lúc runtime chạy trước method Spring bean được chọn.
}
~~~

Dạng **within(...)** hạn chế theo **type/package**. Với Spring AOP, nó vẫn chỉ áp dụng cho **method execution** mà Spring có thể intercept. Đừng suy ra rằng **within(...)** khiến Spring bắt được constructor, field access hoặc lời gọi nội bộ.

**Cách xác minh:** mở đúng file class biên dịch thực tế và đọc dòng **package ...;**. Không suy ra package từ tên thư mục module, Maven artifactId, Spring bean name hoặc package của interface mà bỏ qua lớp triển khai. Nếu class đã được proxy, lấy **AopUtils.getTargetClass(bean)** để biết target class thay vì chỉ xem tên lớp proxy sinh ra.

Ví dụ một service chuyển từ **demo.aop.orders** sang **demo.aop.billing** nhưng biểu thức vẫn là **within(demo.aop.orders..\*)**: bean vẫn tồn tại, được dependency injection bình thường, nhưng rule package sẽ không match service vừa chuyển.

## 5. Chẩn đoán lỗi do bean(...): tên bean là dữ liệu runtime của Spring

**bean(...)** là designator mở rộng của **Spring AOP**, so khớp bean theo **tên trong BeanFactory/ApplicationContext**, không theo tên lớp Java hay package.

~~~java
@Bean("primaryOrderService")
OrderService primary(AuditLog log) {
    return new OrderService(log, "primary");
}
~~~

Với bean trên:

~~~java
@Before("bean(primaryOrderService) && " +
        "execution(* demo.aop.orders.OrderService.place(..))")
public void beforePrimaryPlace() {
    // Chỉ áp dụng cho bean được đặt tên primaryOrderService.
}
~~~

Những biểu thức dễ nhầm:

- **bean(primaryOrderService)**: khớp đúng bean được khai báo tên **primaryOrderService**.
- **bean(*OrderService)**: dùng wildcard **\*** theo tên bean, khớp các tên kết thúc bằng **OrderService**, nếu chúng thực sự được đăng ký như vậy.
- **bean(OrderService)**: không có nghĩa "mọi bean có Java class OrderService". Nó chỉ có ý nghĩa nếu BeanFactory có tên bean **OrderService** tương ứng.
- Nếu **@Bean** không chỉ định name, tên bean thường lấy từ **tên method @Bean**. Nếu dùng **@Component**, tên mặc định thường được suy ra từ tên class theo quy tắc đặt tên bean của Spring. Không đoán, hãy đọc từ context.

Đo tên đang có:

~~~java
System.out.println(context.containsBean("primaryOrderService"));
System.out.println(context.getBeanNamesForType(OrderService.class).length);
~~~

Đoạn trên là mã minh họa trong môi trường có biến **context** là ApplicationContext thực; không phải chương trình độc lập. Với JDK proxy, kết quả tìm bean theo concrete type còn phụ thuộc proxy và metadata kiểu, nên nếu cần xác minh identity hãy ưu tiên tên bean đã biết và inspect target class.

**Đặc biệt:** **bean(...)** thuộc Spring AOP proxy và không phải một pointcut designator của native AspectJ weaving. Nếu dự án chuyển cơ chế advice từ proxy sang weaving, đừng mặc nhiên giữ nguyên rule **bean(...)**.

## 6. Chẩn đoán lỗi do @annotation

Giả sử cần đánh dấu những method xử lý nghiệp vụ đặc biệt:

**demo.aop.TrackAction**

~~~java
package demo.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackAction {
}
~~~

Đặt annotation lên **method mà aspect cần nhận diện**:

~~~java
package demo.aop.orders;

import demo.aop.TrackAction;

public class OrderService {
    @TrackAction
    public String place(String orderCode) {
        return "OK:" + orderCode;
    }

    public String place(int quantity) {
        return "Q:" + quantity;
    }
}
~~~

Aspect:

~~~java
@Before("@annotation(demo.aop.TrackAction)")
public void beforeTrackedAction() {
    // Chạy với method execution có @TrackAction phù hợp.
}
~~~

**Các tình huống khiến không match:**

1. **Thiếu @Retention(RUNTIME)**: annotation không có mặt ở runtime theo cách Spring/AspectJ matcher cần; mặc định retention là **CLASS**.
2. **Sai @Target**: annotation được khai báo dành cho type, nhưng pointcut dùng **@annotation** muốn nhận diện method. Nếu annotation ở class, cần xem **@within** hoặc **@target** tùy ngữ nghĩa.
3. **Annotation đặt trên overload khác**: **place(String)** có **@TrackAction**, nhưng **place(int)** không có. Điểm gọi của overload thứ hai không tự có annotation.
4. **Đặt annotation lên interface nhưng gọi implementation method**: JDK proxy và CGLIB có cách bộc lộ method khác nhau; annotation method không tự "kế thừa" từ interface sang method triển khai theo Java reflection. Spring có các cơ chế tìm annotation qua hierarchy trong một số framework feature, nhưng **không nên dựa vào điều đó cho custom @annotation pointcut mà chưa test proxy mode thực tế**. Đặt annotation nhất quán trên implementation được Spring advice và viết integration test cho cả kiểu proxy đang sử dụng.
5. **Nhầm annotation khác package**: hai annotation cùng simple name nhưng khác fully qualified name là hai kiểu khác nhau.
6. **Tự gọi bằng this**: ngay cả khi **@annotation** có thể match method, lời gọi nội bộ bỏ qua proxy vẫn không chạy advice.

So sánh ranh giới:

| Pointcut | Ý nghĩa trong Spring AOP |
|---|---|
| **@annotation(demo.aop.TrackAction)** | Method được thực thi có annotation tương ứng |
| **@within(demo.aop.TrackGroup)** | Method execution trong những kiểu có annotation type tương ứng |
| **@target(demo.aop.TrackGroup)** | Target object thuộc class có annotation tương ứng |
| **@args(demo.aop.SomeArgMarker)** | Loại runtime của đối số có annotation tương ứng |

Không thay **@annotation** thành **@target** để "sửa cho chạy" nếu yêu cầu thật là đánh dấu **một số method riêng biệt**. Làm vậy có thể advice nhầm mọi method trong class.

## 7. Ví dụ thực hành Spring AOP: tái hiện đủ bốn loại pointcut

Ví dụ dùng **Java 17+**, **Spring Framework 6.2.0**, **JUnit Jupiter 5.11.3**, **AspectJ Weaver 1.9.22.1**. Đây là Spring ApplicationContext tối thiểu, không cần database hay HTTP server. Aspect ghi một sự kiện vào **AuditLog**; target cũng ghi sự kiện, để biết rule nào chạy khi gọi method nào.

Sử dụng bốn advice độc lập trong cùng aspect:

- **signature**: chỉ **OrderService.place(String)**.
- **package**: các public method trong package **demo.aop.orders** hoặc package con.
- **bean**: chỉ **primaryOrderService.place(...)**.
- **annotation**: method có **@TrackAction**.

Cấu trúc dự án:

~~~text
spring-pointcut-diagnostics/
├── pom.xml
└── src/
    ├── main/java/demo/aop/
    │   ├── AuditLog.java
    │   ├── TrackAction.java
    │   ├── DiagnosticAspect.java
    │   ├── AopConfig.java
    │   ├── orders/OrderService.java
    │   └── billing/BillingService.java
    └── test/java/demo/aop/
        └── PointcutDiagnosticsTest.java
~~~

### 7.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-pointcut-diagnostics</artifactId>
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
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-test</artifactId>
            <version>6.2.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.3</version>
            <scope>test</scope>
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
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 7.2. Dữ liệu chẩn đoán và annotation

**src/main/java/demo/aop/AuditLog.java**

~~~java
package demo.aop;

import java.util.ArrayList;
import java.util.List;

public class AuditLog {
    private final List<String> events = new ArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
~~~

**src/main/java/demo/aop/TrackAction.java**

~~~java
package demo.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TrackAction {
}
~~~

### 7.3. Hai service ở hai package khác nhau

**src/main/java/demo/aop/orders/OrderService.java**

~~~java
package demo.aop.orders;

import demo.aop.AuditLog;
import demo.aop.TrackAction;

public class OrderService {
    private final AuditLog log;
    private final String label;

    public OrderService(AuditLog log, String label) {
        this.log = log;
        this.label = label;
    }

    @TrackAction
    public String place(String orderCode) {
        log.add("target:" + label + ":place(String)");
        return "OK:" + orderCode;
    }

    public String place(int quantity) {
        log.add("target:" + label + ":place(int)");
        return "Q:" + quantity;
    }

    public String cancel() {
        log.add("target:" + label + ":cancel");
        return "CANCELLED";
    }
}
~~~

**src/main/java/demo/aop/billing/BillingService.java**

~~~java
package demo.aop.billing;

import demo.aop.AuditLog;

public class BillingService {
    private final AuditLog log;

    public BillingService(AuditLog log) {
        this.log = log;
    }

    public String place(String code) {
        log.add("target:billing:place(String)");
        return "BILL:" + code;
    }
}
~~~

Dù **BillingService.place(String)** có cùng tên và kiểu parameter với **OrderService.place(String)**, nó thuộc package và target type khác; đây là ca âm tính quan trọng để phát hiện pointcut quá rộng.

### 7.4. Aspect gồm bốn rule có thể phân biệt

**src/main/java/demo/aop/DiagnosticAspect.java**

~~~java
package demo.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class DiagnosticAspect {
    private final AuditLog log;

    public DiagnosticAspect(AuditLog log) {
        this.log = log;
    }

    // Khớp đúng overload place(String) và kiểu trả về String.
    @Before(
        "execution(public java.lang.String " +
        "demo.aop.orders.OrderService.place(java.lang.String))"
    )
    public void onSignature() {
        log.add("pointcut:signature");
    }

    // Chỉ các public method trong package orders và package con.
    @Before(
        "within(demo.aop.orders..*) && " +
        "execution(public * *(..))"
    )
    public void onPackage() {
        log.add("pointcut:package");
    }

    // Chỉ bean có tên primaryOrderService, với các overload place.
    @Before(
        "bean(primaryOrderService) && " +
        "execution(* demo.aop.orders.OrderService.place(..))"
    )
    public void onBean() {
        log.add("pointcut:bean");
    }

    // Chỉ method có annotation TrackAction.
    @Before("@annotation(demo.aop.TrackAction)")
    public void onAnnotation() {
        log.add("pointcut:annotation");
    }
}
~~~

Không giả định **thứ tự giữa các @Before advices** khi nhiều advice cùng match một method. Trong test ta so sánh **tập sự kiện, gồm đúng số lượng**, vì thứ tự giữa các advice không phải yêu cầu bài học. Target được gọi sau các advice kiểu **@Before**, nhưng chi tiết thứ tự các advice không được khóa cứng theo tên method/aspect.

### 7.5. Spring AOP configuration

**src/main/java/demo/aop/AopConfig.java**

~~~java
package demo.aop;

import demo.aop.billing.BillingService;
import demo.aop.orders.OrderService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfig {
    @Bean
    public AuditLog auditLog() {
        return new AuditLog();
    }

    @Bean("primaryOrderService")
    public OrderService primary(AuditLog log) {
        return new OrderService(log, "primary");
    }

    @Bean("secondaryOrderService")
    public OrderService secondary(AuditLog log) {
        return new OrderService(log, "secondary");
    }

    @Bean
    public BillingService billingService(AuditLog log) {
        return new BillingService(log);
    }

    @Bean
    public DiagnosticAspect diagnosticAspect(AuditLog log) {
        return new DiagnosticAspect(log);
    }
}
~~~

**Điểm quan trọng:** Hai OrderService beans **cùng một Java class** nhưng tên bean khác nhau. Nhờ đó ta tách được hành vi **bean(primaryOrderService)** khỏi **execution(...)** chỉ xét type/method. **proxyTargetClass=true** giúp inject theo concrete class trong test; nó không bật AspectJ weaving.

## 8. Integration test phân biệt từng lỗi pointcut

**src/test/java/demo/aop/PointcutDiagnosticsTest.java**

~~~java
package demo.aop;

import demo.aop.billing.BillingService;
import demo.aop.orders.OrderService;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(AopConfig.class)
class PointcutDiagnosticsTest {
    @Autowired
    @Qualifier("primaryOrderService")
    OrderService primary;

    @Autowired
    @Qualifier("secondaryOrderService")
    OrderService secondary;

    @Autowired
    BillingService billing;

    @Autowired
    AuditLog log;

    @BeforeEach
    void clearEvents() {
        log.clear();
    }

    private void assertEvents(String... expected) {
        assertEquals(
            Arrays.stream(expected).sorted().toList(),
            log.snapshot().stream().sorted().toList()
        );
    }

    @Test
    void aspectIsWiredThroughSpringAopProxy() {
        assertTrue(AopUtils.isAopProxy(primary));
        assertTrue(AopUtils.isAopProxy(secondary));
        assertFalse(AopUtils.isAopProxy(billing));
    }

    @Test
    void methodSignatureMustMatchReturnTypeAndOverload() {
        assertEquals("OK:A-1", secondary.place("A-1"));
        assertEvents(
            "pointcut:signature",
            "pointcut:package",
            "pointcut:annotation",
            "target:secondary:place(String)"
        );

        log.clear();

        assertEquals("Q:7", secondary.place(7));
        assertEvents(
            "pointcut:package",
            "target:secondary:place(int)"
        );
    }

    @Test
    void packagePatternExcludesBillingService() {
        assertEquals("BILL:B-1", billing.place("B-1"));
        assertEvents("target:billing:place(String)");

        log.clear();

        assertEquals("CANCELLED", primary.cancel());
        assertEvents(
            "pointcut:package",
            "target:primary:cancel"
        );
    }

    @Test
    void beanNameDistinguishesInstancesOfSameClass() {
        assertEquals("OK:P-1", primary.place("P-1"));
        assertEvents(
            "pointcut:signature",
            "pointcut:package",
            "pointcut:bean",
            "pointcut:annotation",
            "target:primary:place(String)"
        );

        log.clear();

        assertEquals("OK:S-1", secondary.place("S-1"));
        assertEvents(
            "pointcut:signature",
            "pointcut:package",
            "pointcut:annotation",
            "target:secondary:place(String)"
        );
    }

    @Test
    void annotationIsPresentOnOnlyOneOverload() {
        assertEquals("Q:9", secondary.place(9));
        assertEvents(
            "pointcut:package",
            "target:secondary:place(int)"
        );

        log.clear();

        assertEquals("OK:X-1", secondary.place("X-1"));
        assertEvents(
            "pointcut:signature",
            "pointcut:package",
            "pointcut:annotation",
            "target:secondary:place(String)"
        );
    }
}
~~~

### 8.1. Test chứng minh điều gì?

**aspectIsWiredThroughSpringAopProxy** xác nhận Spring có tạo proxy cho hai bean Orders. **BillingService** không có advisor nào match nên ở cấu hình ví dụ tối thiểu, nó giữ object thường.

**methodSignatureMustMatchReturnTypeAndOverload** cho thấy method overload **place(String)** khớp ba rule signature/package/annotation, trong khi **place(int)** chỉ khớp rule package. Việc call vẫn trả giá trị đúng chứng minh target chạy nhưng advice không nhất thiết match.

**packagePatternExcludesBillingService** kiểm tra **BillingService.place(String)** nằm ngoài package orders. Nó chỉ ghi target event dù method cùng tên/parameter. **primary.cancel()** nằm trong package orders nên nhận package advice dù không khớp signature/annotation/bean rule.

**beanNameDistinguishesInstancesOfSameClass** đối chiếu hai bean cùng target class. Chỉ **primary** có thêm **pointcut:bean**.

**annotationIsPresentOnOnlyOneOverload** chỉ ra **@TrackAction** ở place(String) không tự áp dụng cho place(int).

**Tại sao phải so sánh sự kiện thay vì chỉ dùng AopUtils.isAopProxy?** Một bean có thể được proxy vì **rule khác**, trong khi rule đang nghi vấn không match. Ví dụ **secondary** có proxy nhờ rule package/signature/annotation, nhưng **bean(primaryOrderService)** không chạy trên nó. Proxy tồn tại **không chứng minh mọi pointcut đều khớp**.

### 8.2. Chạy ví dụ

Trong thư mục **spring-pointcut-diagnostics**:

~~~bash
mvn test
~~~

Ví dụ định nghĩa **5 JUnit test methods**. Kết quả mong đợi theo cấu hình và assertions:

~~~text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
~~~

Đây là **kết quả mong đợi**, không phải bản sao stdout từ một lượt Maven đã thực thi. Maven có thể in các phần log khác tùy môi trường.

Có thể chạy riêng test class:

~~~bash
mvn -Dtest=PointcutDiagnosticsTest test
~~~

Nếu thay đổi một biểu thức pointcut để tái hiện lỗi, chạy lại và xem chính xác assertion bị sai. Chẳng hạn:

- Đổi **OrderService.place(java.lang.String)** thành **OrderService.place(java.lang.Integer)**: event **pointcut:signature** biến mất ở ca String.
- Đổi **within(demo.aop.orders..\*)** thành **within(demo.aop.billing..\*)**: event **pointcut:package** biến mất trên Orders và có thể xuất hiện trên Billing.
- Đổi **bean(primaryOrderService)** thành **bean(unknownOrderService)**: event **pointcut:bean** không còn.
- Chuyển **@TrackAction** từ **place(String)** sang **place(int)**: event **pointcut:annotation** chuyển sang overload tương ứng.

Không giữ những biến đổi thử lỗi trong source thật; chúng chỉ phục vụ kiểm chứng bằng test.

## 9. Quy trình chẩn đoán theo thứ tự ít tốn công nhất

### Bước 1: Xác nhận bean và runtime proxy

Trong integration test có ApplicationContext:

~~~java
Object bean = context.getBean("primaryOrderService");

System.out.println(context.containsBean("primaryOrderService"));
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.getTargetClass(bean).getName());
~~~

Đây là đoạn dùng biến **context** đã có; riêng nó không phải Java class chạy độc lập. Kết quả dự kiến trong cấu hình ví dụ:

~~~text
true
true
demo.aop.orders.OrderService
~~~

Nếu **containsBean=false**, kiểm tra **@Bean** name, profile, conditional configuration hoặc component scan. Nếu bean tồn tại nhưng **isAopProxy=false**, kiểm tra aspect đã đăng ký, auto-proxy, pointcut và loại bean. Nếu proxy=true nhưng advice không ghi sự kiện, tiếp tục phân rã điều kiện match.

### Bước 2: Tách pointcut phức tạp thành điều kiện nhỏ

Giả sử rule ban đầu:

~~~java
@Before(
    "execution(public * demo.aop.orders..*.place(..)) && " +
    "bean(primaryOrderService) && " +
    "@annotation(demo.aop.TrackAction)"
)
~~~

Một điều kiện sai làm toàn bộ biểu thức **&&** không khớp. Trong môi trường test, hãy kiểm tra tuần tự:

~~~java
// Chẩn đoán chữ ký + package:
// execution(public * demo.aop.orders..*.place(..))

// Chẩn đoán tên bean:
// bean(primaryOrderService)

// Chẩn đoán annotation:
// @annotation(demo.aop.TrackAction)
~~~

Đây là ba **mảnh biểu thức** để kiểm tra riêng, không phải cả ba advice production nên dùng cùng lúc. Khi mỗi điều kiện chạy đúng trên test bean/method tương ứng, ghép lại **&&** và kiểm tra kết quả hợp thành. Nếu yêu cầu là "một trong các điều kiện", cần dùng **||** một cách có chủ đích; đổi **&&** sang **||** để chữa triệu chứng có thể làm advice lan quá rộng.

### Bước 3: Phân biệt "không match" với "không đi qua proxy"

~~~java
public void outer() {
    inner(); // tương đương this.inner()
}

@TrackAction
public void inner() {
    // ...
}
~~~

Nếu client gọi **outer()** từ ngoài qua proxy rồi target gọi **this.inner()**, annotation pointcut có thể match **inner()** khi gọi từ ngoài, nhưng advice của Spring proxy **không** chạy trên lời gọi nội bộ ấy. Đừng đổi biểu thức annotation để sửa vấn đề self-invocation; hãy sửa đường gọi qua bean cộng tác/proxy hoặc cân nhắc AspectJ weaving khi yêu cầu thực sự cần.

Với JDK dynamic proxy, client thường chỉ thấy method trên interface; với class-based/CGLIB proxy, final/private method không thể được advice theo cơ chế override proxy. Những giới hạn đó cũng cần loại trừ trước khi kết luận pointcut sai.

### Bước 4: Kiểm tra đúng target method và annotation bằng reflection

~~~java
import java.lang.reflect.Method;
import org.springframework.aop.support.AopUtils;

Class<?> targetClass = AopUtils.getTargetClass(bean);
Method declared = targetClass.getMethod("place", String.class);

System.out.println(declared);
System.out.println(declared.isAnnotationPresent(TrackAction.class));
~~~

Với **primaryOrderService** của ví dụ, method **place(String)** trên target class có **@TrackAction** nên dòng thứ hai in **true**. Kiểm tra này hữu ích nhưng không thay thế integration test AOP, vì **metadata method** chỉ là một thành phần của phép match; bean name, proxy route và advisor còn phải đúng.

Khi method được khai báo trên interface, có generic bridge method hoặc annotation ở hierarchy khác, hãy kiểm tra cả method được Spring resolve trên **target class**. Tránh dùng **proxy.getClass().getDeclaredMethod(...)** rồi xem đó là toàn bộ sự thật về annotation, đặc biệt với JDK proxy generated class.

### Bước 5: Ghi lại bảng đối chiếu rule/expected/matched

| Call path | signature | package | bean(primary) | annotation |
|---|---:|---:|---:|---:|
| **primary.place(String)** qua proxy | Có | Có | Có | Có |
| **secondary.place(String)** qua proxy | Có | Có | Không | Có |
| **secondary.place(int)** qua proxy | Không | Có | Không | Không |
| **primary.cancel()** qua proxy | Không | Có | Không | Không |
| **billing.place(String)** từ bean billing | Không | Không | Không | Không |

Nếu kết quả không khớp bảng, theo dấu **một điều kiện tại một thời điểm**. Đây là cách phân tích đúng nguyên nhân, tránh mở rộng pointcut vô ý.

## 10. Những lỗi chẩn đoán phổ biến trong dự án Spring

| Hiện tượng | Nguyên nhân có khả năng | Cách xác minh / xử lý |
|---|---|---|
| Đổi tên method mà advice biến mất | **execution** đang cố định tên method cũ | Kiểm tra method signature sau refactor và cập nhật pointcut có chủ đích |
| Một overload có advice, overload khác không | Pointcut giới hạn kiểu/số lượng tham số hoặc annotation chỉ ở một overload | Test từng overload riêng; phân biệt **()**, **(*)**, **(..)** |
| Trả về String nhưng pointcut yêu cầu void | Sai return-type pattern | Kiểm tra return type khai báo, hoặc dùng **\*** nếu không cần giới hạn |
| Package con không có advice | Dùng **.\*** thay vì **..\*** | Kiểm tra package của target class; test class trong subpackage |
| Class chuyển package sau refactor | Pointcut vẫn trỏ package cũ | So sánh **package** trong class với **within/execution** |
| Bean cùng type nhưng chỉ một bean chạy | Rule **bean(name)** chỉ match instance theo tên | Lấy tên thật trong ApplicationContext, kiểm tra wildcard/name |
| @annotation không chạy | Annotation sai vị trí, retention, method overload, interface/implementation | Kiểm tra **@Retention(RUNTIME)**, **@Target(METHOD)** và target method |
| AopUtils.isAopProxy=true nhưng rule không chạy | Proxy có thể do advisor khác; rule đang xét không match | Ghi effect riêng cho từng advice, test đúng method |
| Aspect class có @Aspect nhưng không được gọi | Aspect chưa là Spring bean hoặc AOP infrastructure chưa bật | Kiểm tra component scan/@Bean và cấu hình auto-proxy |
| Advice chạy khi gọi service từ bean khác nhưng không chạy nội bộ | **this.method()** bỏ qua proxy | Kiểm tra call path, tách bean hoặc điều chỉnh boundary |
| CGLIB không advice method final/private | Không thể override method đó | Thiết kế method interceptable hoặc thay cơ chế |
| Viết **call(...)**/**get(...)** rồi Spring báo lỗi | Designator không thuộc Spring AOP proxy | Dùng method execution phù hợp hoặc AspectJ weaving |
| Unit test advice pass nhưng ứng dụng không chạy advice | Unit test không khởi tạo Spring proxy | Viết integration test **@SpringJUnitConfig** hoặc **@SpringBootTest** |
| Test chạy riêng đúng, chạy suite sai | Mutable singleton state trong cached ApplicationContext | Reset test fixture, tránh phụ thuộc thứ tự/parallelism |

## 11. Thực hành tốt để pointcut bền vững trong Spring

### 11.1. Dùng pointcut được đặt tên khi biểu thức phức tạp

~~~java
@Aspect
public class OrderPointcuts {
    @Pointcut("execution(public * demo.aop.orders..*.*(..))")
    public void orderPublicMethods() {}

    @Pointcut("bean(primaryOrderService)")
    public void primaryOrderBean() {}

    @Before("orderPublicMethods() && primaryOrderBean()")
    public void auditPrimaryOrder() {
        // ...
    }
}
~~~

Đoạn này là **minh họa logic cấu hình**, không phải chương trình chạy độc lập. Trong ứng dụng thật, aspect cần được đăng ký thành Spring bean. Việc đặt tên làm rõ từng điều kiện hơn so với một chuỗi pointcut dài và giúp tạo test độc lập cho từng nhóm rule.

### 11.2. Kiểm tra cả ca dương tính và âm tính

Một test pointcut tốt trả lời cả hai câu hỏi:

- **Nên match ở đâu?** Gọi đúng method/bean qua proxy, assert advice effect hiện diện.
- **Không nên match ở đâu?** Gọi overload khác, package khác, bean khác hoặc method không có annotation; assert advice effect vắng mặt.

Chỉ kiểm tra một ca dương tính có thể bỏ sót pointcut quá rộng. Đặc biệt với logging, metrics, auditing và security, phạm vi quá rộng có thể làm advice chạy ngoài nơi mong muốn.

### 11.3. Tránh assert chi tiết không ổn định

Không assert tên class generated proxy, thứ tự các advice không có quy định ordering, hay những dòng console log phụ thuộc môi trường. Dùng collaborator như **AuditLog**, và API **AopUtils** nếu cần xác định proxy. Khi có nhiều advisor và thứ tự thật sự quan trọng, định nghĩa ordering rõ ràng theo API Spring rồi test contract đó.

### 11.4. Phân biệt Spring AOP proxy và AspectJ weaving

Spring có thể hiểu annotation **@AspectJ** nhờ thư viện AspectJ nhưng vẫn chạy bằng **Spring AOP proxy**. Bài này kiểm tra phương thức execution của Spring bean và designator **bean(...)** riêng của Spring. Nếu ứng dụng thực sự dùng AspectJ compile-time/load-time weaving, phương pháp cấu hình và phạm vi join point khác; không lấy việc **AopUtils.isAopProxy=false** làm kết luận rằng advice woven không tồn tại.

## 12. Checklist điều tra nhanh

- [ ] Đúng bean trong ApplicationContext, đúng bean name và profile?
- [ ] Aspect là Spring bean và AOP auto-proxy đã bật?
- [ ] Caller gọi bean proxy thay vì target gốc hoặc **this.method()**?
- [ ] Kiểu proxy phù hợp với method cần intercept?
- [ ] Method name, return type, overload và parameter pattern đúng?
- [ ] Package của target class đúng; **\*** và **..** được dùng có chủ đích?
- [ ] **bean(...)** đang so tên bean thực tế, không suy ra từ tên class?
- [ ] **@annotation(...)** có đúng annotation type, **RUNTIME** retention và method placement?
- [ ] Biểu thức nhiều điều kiện **&&** đã được kiểm tra từng phần?
- [ ] Có integration test dương tính và âm tính cho từng loại pointcut?
- [ ] Kết quả test dựa trên effect của advice và target, không chỉ proxy tồn tại?

## 13. Tự kiểm tra

**Câu 1.** Vì sao **execution(* demo.aop.orders.OrderService.place(java.lang.Integer))** không match **place(int)**?

Vì biểu thức **execution** so kiểu khai báo trong chữ ký; **Integer** và **int** là hai kiểu khác nhau.

**Câu 2.** **within(demo.aop.orders.\*)** và **within(demo.aop.orders..\*)** khác nhau ở đâu?

Dạng thứ nhất chọn type trực tiếp trong package; dạng thứ hai chọn thêm các subpackages.

**Câu 3.** Có hai Spring beans cùng class OrderService, tại sao **bean(primaryOrderService)** chỉ match một bean?

Vì **bean(...)** so khớp **Spring bean name**, không chỉ Java type.

**Câu 4.** Vì sao đặt **@TrackAction** trên class không làm **@annotation(TrackAction)** match mọi method?

Vì **@annotation** kiểm tra annotation trên method execution. Annotation type/class có designator khác như **@within/@target** tùy trường hợp.

**Câu 5.** **AopUtils.isAopProxy(bean)=true** có chứng minh **@Before** đang xét đã chạy không?

Không. Proxy có thể do advisor khác tạo; cần gọi method và assert effect của advice cụ thể.

**Câu 6.** Làm thế nào phân biệt không match và self-invocation?

Gọi method trực tiếp từ bên ngoài qua Spring proxy, so kết quả với lời gọi nội bộ **this.method()** và kiểm tra method signature/pointcut bằng test dương tính.

## 14. Tài liệu tham khảo chính thức

- Spring Framework — Declaring a Pointcut: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework — @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — Pointcut API in Spring: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html
- Spring Framework — TestContext Framework: https://docs.spring.io/spring-framework/reference/testing/testcontext-framework.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-003">Phương thức đúng nhưng lời gọi không đi qua proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức đúng nhưng lời gọi không đi qua proxy — chẩn đoán lỗi Spring AOP

## 1. Tình huống: pointcut đúng, annotation đúng, nhưng advice không chạy

Bạn đã kiểm tra cú pháp `@Aspect`, `@Around`, `@Before` hoặc annotation như `@Transactional`, `@Cacheable`. Method có tên và signature đúng, bean vẫn làm nghiệp vụ bình thường, nhưng không thấy log của advice hoặc side effect mà advice đáng lẽ phải tạo.

Trong **Spring AOP ở proxy mode**, điều kiện thiết yếu là **lời gọi phải đi qua Spring AOP proxy**. Spring không biến mọi lời gọi Java thành join point được chặn. Đối tượng thực thi nghiệp vụ (target object) và đối tượng đại diện cho client (proxy) có vai trò khác nhau:

```text
ĐƯỜNG ĐÚNG:

caller --> Spring AOP proxy --> advisor/advice chain --> target.method(...)
                         \--> advice có cơ hội chạy khi pointcut match

ĐƯỜNG BỎ QUA PROXY:

caller --> target object.method(...)
target.outer() --> this.inner()
caller --> new Target().inner()

Không có lượt đi qua proxy tại inner():
Spring AOP advice dành cho inner() không được gọi.
```

**Quy tắc chẩn đoán:** một method phù hợp pointcut chưa đủ. Cần có đồng thời **bean được Spring quản lý**, **advisor phù hợp thực sự nằm trên proxy**, và **client gọi qua chính proxy đó**. Nếu điểm nào không đạt, method vẫn có thể chạy đúng nghiệp vụ nhưng advice không chạy.

Bài tập trung riêng vào **Spring AOP proxy** và các cơ chế Spring dựa trên proxy, không lặp lại toàn bộ lý thuyết AOP hay thay thế bằng AspectJ weaving.

## 2. Ba lớp cần phân biệt khi debug

| Lớp | Vai trò | Bằng chứng cần kiểm tra |
| --- | --- | --- |
| **Target** | Class Java chứa mã nghiệp vụ, chẳng hạn `PaymentServiceImpl` | Method tồn tại và chạy được khi gọi thông thường |
| **Proxy** | Đối tượng Spring trả cho client nếu bean cần AOP advice | `AopUtils.isAopProxy(bean)` trả `true` khi đúng cơ chế |
| **Advisor / interceptor** | Xác định pointcut, thứ tự và advice quanh invocation | `Advised.getAdvisors()`, log trong advice và kiểm thử hành vi |

Một lời gọi hợp lệ phải có đường đi:

1. Spring tạo target bean.
2. Auto-proxy infrastructure xem target có phù hợp advisor không.
3. Nếu phù hợp, Spring cung cấp proxy như tham chiếu client sử dụng.
4. Client gọi method **trên proxy**.
5. Proxy tra chuỗi interceptor phù hợp method.
6. Advice thực hiện logic, thường gọi `proceed()` để target chạy.
7. Target có thể gọi các method của chính nó; những lần gọi qua `this` này **không tự quay lại proxy**.

**Hai kết luận dễ nhầm:**

- `AopUtils.isAopProxy(bean) == true` chứng minh đối tượng là Spring AOP proxy, nhưng **không chứng minh** advisor bạn quan tâm match method đang gọi.
- `AopUtils.isAopProxy(bean) == false` chưa luôn có nghĩa ứng dụng sai: bean có thể không cần AOP, hoặc dùng **AspectJ bytecode weaving** thay vì Spring proxy. Ở bài này, kỳ vọng là **Spring AOP proxy mode**.

## 3. Self-invocation: nguyên nhân điển hình nhất

Ví dụ:

```java
@Service
public class ReceiptService {

    public void process() {
        this.validate(); // Gọi trực tiếp trong target
    }

    @Track
    public void validate() {
        // Kiểm tra dữ liệu...
    }
}
```

Đây là snippet minh họa; `@Track` giả định là annotation riêng và có aspect dùng annotation đó. Kể cả khi client gọi `receiptService.process()` qua Spring proxy, khi execution tới `process()` trên **target object**, lệnh `this.validate()` gọi thẳng target. Proxy không nhận một invocation riêng cho `validate()`; lời gọi này không kích hoạt advice chỉ match `validate()`.

Tương tự, lời gọi ẩn `validate()` không ghi `this.` cũng là self-invocation. Việc method `validate()` là `public`, có annotation đúng và có pointcut `execution(...)` khớp **không** thay đổi điều đó.

```text
Client -> proxy.process()
          -> target.process()
              -> this.validate()
                 -> target.validate()  [không có proxy.validate()]
```

Các tính năng Spring dựa trên proxy như transaction, caching và async gặp cùng nguyên tắc:

- `@Transactional` trên `inner()` không tự bắt đầu một giao dịch riêng nếu chỉ được gọi bằng `this.inner()`.
- `@Cacheable` trên `load()` không tạo cache lookup cho lần self-invocation.
- `@Async` trên `send()` không tự chuyển việc sang executor cho lần gọi nội bộ đó.
- Custom Spring `@Aspect` match `inner()` không tự chạy trước/sau self-invocation.

**Lưu ý:** nếu `outer()` đã có một transaction do proxy thiết lập, lời gọi `this.inner()` vẫn có thể thực thi *bên trong transaction đang hoạt động*; chỉ là **annotation transaction riêng trên inner không được intercept lại**. Vì vậy không thể dùng `isActualTransactionActive() == true` để kết luận advice của `inner()` thực sự chạy.

## 4. Khi nào lời gọi không đi qua Spring proxy?

### 4.1. Tạo đối tượng bằng new

```java
PaymentService service = new PaymentServiceImpl();
service.pay(); // Không qua Spring context, không có AOP proxy
```

Đây là object Java thường. Annotation `@Service`, `@Aspect` hoặc `@Transactional` trên class không khiến `new` tự tạo Spring AOP proxy. Cách đúng là inject Spring bean hoặc lấy bean từ `ApplicationContext` (ưu tiên DI).

### 4.2. Truyền target/raw reference thay vì proxy reference

Một custom factory, code tự bọc bằng `ProxyFactory` hoặc plugin có thể giữ reference tới **target gốc** và dùng nó về sau, dù nơi khác đã có proxy cho cùng target. Khi đó hai client nhìn cùng nghiệp vụ nhưng chỉ client giữ proxy được advise.

Nếu `ProxyFactory factory = new ProxyFactory(target)`, object `target` và `factory.getProxy()` là **hai reference khác nhau**. Chỉ reference proxy gọi qua advisor chain.

### 4.3. Self-invocation

`this.otherMethod()`, gọi method private helper hoặc từ method này gọi method khác trên chính instance bỏ qua một lần interceptor mới. Đây không phải lỗi pointcut parser; đây là **giới hạn thiết kế** của Spring AOP proxy.

### 4.4. Method không thể được proxy intercept

Với class-based proxy (CGLIB), class `final` không thể subclass; method `final` không thể override để advise; method `private` không thể override nên không được chặn. Với JDK proxy, client gọi thông qua các interface mà proxy expose; method chỉ có trên concrete class và không nằm trên interface không thể gọi qua JDK proxy bằng cách cast sang concrete target.

Đây là **giới hạn proxyability** khác với self-invocation. Không nên kết luận tất cả đều do `this`.

### 4.5. Bean chưa được Spring auto-proxy hoặc ở context khác

Aspect class có `@Aspect` nhưng không được đăng ký làm Spring bean, `@EnableAspectJAutoProxy` chưa bật ở đúng `ApplicationContext`, bean được tạo trước khi auto-proxy BeanPostProcessor xử lý, hoặc đang làm việc với parent/child contexts khác nhau đều có thể khiến không tồn tại proxy như kỳ vọng.

Mặc định, `@EnableAspectJAutoProxy` áp dụng vào ApplicationContext nơi nó được khai báo. Aspect đặt ở child web context không nhất thiết tự áp dụng lên service nằm ở root context; phải xác nhận cơ chế và bean ownership ở đúng context.

### 4.6. Lời gọi ở giai đoạn khởi tạo bean

Một method `@PostConstruct` hoặc code constructor khởi động việc gọi method có advice thường diễn ra lúc proxy chưa hoàn tất setup. Không nên dựa vào Spring AOP interception của các lời gọi nội bộ trong constructor hoặc initialization callback.

### 4.7. Method invocation trên object khác với bean đã inspect

Trong hệ thống có nhiều bean cùng interface, nhiều scopes, custom proxies hoặc multiple contexts, việc inspect `context.getBean("a")` nhưng luồng nghiệp vụ lại gọi một reference `new` hay bean `b` dẫn tới kết luận sai. Nên log identity/type/bean name của **reference thực sự được client gọi**, không chỉ một bean có cùng class.

## 5. Ví dụ Maven + Java 17 chạy được: so sánh bốn đường gọi

Ứng dụng dưới đây có Spring AOP aspect chặn riêng `WorkService.inner()`:

1. `work.outer()` đi qua proxy cho `outer()`, nhưng `outer()` gọi `this.inner()` trên target → **không có advice riêng cho inner**.
2. `work.inner()` trực tiếp qua proxy → advice chạy.
3. `coordinator.execute()` gọi `inner()` trên một `WorkService` Spring bean được inject → advice chạy.
4. `new WorkServiceImpl(events).inner()` → không được proxy, advice không chạy.

Dự án dùng Java 17, Spring Framework 6.2.19, AspectJ Weaver 1.9.24 và Maven. Không cần Spring Boot hay AspectJ compile-time/load-time weaving.

### 5.1. Cấu trúc project

```text
spring-proxy-diagnosis/
  pom.xml
  src/main/java/demo/
    App.java
    WorkService.java
    WorkServiceImpl.java
    Coordinator.java
    TraceEvents.java
    TraceAspect.java
  src/test/java/demo/
    ProxyDiagnosisTest.java
```

### 5.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-proxy-diagnosis</artifactId>
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
      <version>1.9.24</version>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-test</artifactId>
      <version>6.2.19</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.11.4</version>
      <scope>test</scope>
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
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.2</version>
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

`spring-context` đem theo thành phần IoC cần thiết; `spring-aop` là proxy infrastructure; `aspectjweaver` ở đây được Spring dùng để diễn giải khai báo @AspectJ. Dependency weaver **không tự bật LTW**.

### 5.3. WorkService.java

`src/main/java/demo/WorkService.java`:

```java
package demo;

public interface WorkService {
    void outer();
    void inner();
}
```

Interface giúp Spring có thể chọn JDK dynamic proxy. Client sẽ inject `WorkService`, không cast proxy sang `WorkServiceImpl`.

### 5.4. TraceEvents.java

`src/main/java/demo/TraceEvents.java`:

```java
package demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TraceEvents {
    private final List<String> events = new CopyOnWriteArrayList<>();

    public void add(String value) {
        events.add(value);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
```

Recorder đơn giản làm hành vi kiểm chứng được bằng output/JUnit assertions. Trong ví dụ tuần tự, không có race condition; `CopyOnWriteArrayList` không có nghĩa reset event state có isolation nếu tự bật JUnit parallel execution.

### 5.5. WorkServiceImpl.java

`src/main/java/demo/WorkServiceImpl.java`:

```java
package demo;

public class WorkServiceImpl implements WorkService {
    private final TraceEvents events;

    public WorkServiceImpl(TraceEvents events) {
        this.events = events;
    }

    @Override
    public void outer() {
        events.add("target:outer");
        this.inner(); // Self-invocation: KHÔNG đi qua Spring proxy.
    }

    @Override
    public void inner() {
        events.add("target:inner");
    }
}
```

Class và methods đều public/non-final, loại trừ nguyên nhân method không proxy được. Cố tình gọi `this.inner()` trong `outer` để tập trung vào **đường đi reference**.

### 5.6. TraceAspect.java

`src/main/java/demo/TraceAspect.java`:

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TraceAspect {
    private final TraceEvents events;

    public TraceAspect(TraceEvents events) {
        this.events = events;
    }

    @Around("execution(* demo.WorkService.inner(..))")
    public Object aroundInner(ProceedingJoinPoint pjp) throws Throwable {
        events.add("aop:before");
        try {
            return pjp.proceed();
        } finally {
            events.add("aop:after");
        }
    }
}
```

Pointcut chỉ chọn method `inner()` qua hợp đồng `WorkService`. Trong khi client gọi `work.outer()`, proxy có thể có advisor áp dụng cho `inner` nhưng advisor ấy không intercept `outer`. Quan trọng là invocation **đến inner** không qua proxy khi gọi `this.inner()` trong target.

Việc dùng `try/finally` giữ `aop:after` ngay cả nếu method ném exception. Trong bài chỉ có method `void` và không ném lỗi, nên output deterministic.

### 5.7. Coordinator.java: giải pháp tách bean

`src/main/java/demo/Coordinator.java`:

```java
package demo;

public class Coordinator {
    private final WorkService work;
    private final TraceEvents events;

    public Coordinator(WorkService work, TraceEvents events) {
        this.work = work;
        this.events = events;
    }

    public void execute() {
        events.add("coordinator");
        work.inner(); // Reference Spring-injected: đi qua proxy.
    }
}
```

`Coordinator` là Spring bean khác, nhận `WorkService` qua constructor injection. Khi nó gọi `work.inner()`, đó là lời gọi qua reference được Spring inject (proxy), không phải `this.inner()` trong target.

### 5.8. App.java

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
    @EnableAspectJAutoProxy
    public static class Config {
        @Bean
        TraceEvents traceEvents() {
            return new TraceEvents();
        }

        @Bean
        WorkService workService(TraceEvents events) {
            return new WorkServiceImpl(events);
        }

        @Bean
        TraceAspect traceAspect(TraceEvents events) {
            return new TraceAspect(events);
        }

        @Bean
        Coordinator coordinator(WorkService work, TraceEvents events) {
            return new Coordinator(work, events);
        }
    }

    private static void showAndClear(TraceEvents events) {
        for (String event : events.snapshot()) {
            System.out.println(event);
        }
        events.clear();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            WorkService work = context.getBean(WorkService.class);
            Coordinator coordinator = context.getBean(Coordinator.class);
            TraceEvents events = context.getBean(TraceEvents.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(work));
            System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(work));

            System.out.println("-- outer --");
            work.outer();
            showAndClear(events);

            System.out.println("-- direct through proxy --");
            work.inner();
            showAndClear(events);

            System.out.println("-- via another bean --");
            coordinator.execute();
            showAndClear(events);

            System.out.println("-- manually new target --");
            new WorkServiceImpl(events).inner();
            showAndClear(events);
        }
    }
}
```

Không sử dụng `@ComponentScan` vì tất cả bean được đăng ký tường minh bằng `@Bean`. `@EnableAspectJAutoProxy` được đặt trên `Config` thuộc cùng context. `TraceAspect` được Spring tạo làm bean, `WorkServiceImpl` cũng được Spring quản lý dưới interface `WorkService`.

### 5.9. Chạy và đọc output

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
```

Các dòng do `System.out` trong ví dụ sinh ra (không tính log riêng của Maven):

```text
proxy=true
jdkProxy=true
-- outer --
target:outer
target:inner
-- direct through proxy --
aop:before
target:inner
aop:after
-- via another bean --
coordinator
aop:before
target:inner
aop:after
-- manually new target --
target:inner
```

Phân tích từng phần:

- `proxy=true` và `jdkProxy=true`: object `work` là Spring AOP JDK dynamic proxy.
- `work.outer()`: không có `aop:before` hoặc `aop:after` vì advice chỉ chọn `inner`, còn lần gọi `this.inner()` không qua proxy.
- `work.inner()`: client gọi đúng method trên proxy, nên có `aop:before` và `aop:after`.
- `coordinator.execute()`: một bean khác dùng reference `WorkService` được Spring inject, nên advice chạy.
- `new WorkServiceImpl(events).inner()`: target tự tạo, không đi qua Spring proxy, nên chỉ có `target:inner`.

**Đây là output dự kiến dựa trên mã ví dụ và Spring proxy semantics**; cần chạy lệnh trên môi trường có Maven/JDK/dependencies để xác nhận build/runtime cụ thể.

## 6. Integration test với JUnit 5 và Spring TestContext

Kiểm thử đường đi là cách nhanh nhất phát hiện regression khi refactor service hoặc thay cấu hình AOP. Dùng `@SpringJUnitConfig` nạp đúng `App.Config` và assert **events** thay vì chỉ dựa vào console.

`src/test/java/demo/ProxyDiagnosisTest.java`:

```java
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = App.Config.class)
class ProxyDiagnosisTest {
    @Autowired
    WorkService work;

    @Autowired
    Coordinator coordinator;

    @Autowired
    TraceEvents events;

    @BeforeEach
    void reset() {
        events.clear();
    }

    @Test
    void springBeanIsActuallyProxied() {
        assertTrue(AopUtils.isAopProxy(work));
        assertTrue(AopUtils.isJdkDynamicProxy(work));
        assertFalse(work instanceof WorkServiceImpl);
    }

    @Test
    void selfInvocationRunsTargetButSkipsInnerAdvice() {
        work.outer();

        assertEquals(List.of("target:outer", "target:inner"),
                events.snapshot());
    }

    @Test
    void externalInvocationThroughProxyRunsAdvice() {
        work.inner();

        assertEquals(List.of("aop:before", "target:inner", "aop:after"),
                events.snapshot());
    }

    @Test
    void collaboratorUsesInjectedProxy() {
        coordinator.execute();

        assertEquals(List.of(
                "coordinator", "aop:before",
                "target:inner", "aop:after"
        ), events.snapshot());
    }

    @Test
    void manuallyCreatedTargetIsNotAdvised() {
        new WorkServiceImpl(events).inner();

        assertEquals(List.of("target:inner"), events.snapshot());
    }
}
```

Chạy:

```bash
mvn -q test
```

Dự kiến 5 test pass, exit code `0` khi dependencies/JDK đúng. Bỏ `-q` nếu muốn xem tổng kết:

```bash
mvn test
```

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

Dòng trên là tổng kết kỳ vọng trong Surefire, không bao gồm tiền tố tên test class hay thông tin môi trường. Chưa cần AspectJ compiler/agent hoặc Spring Boot để chạy tests này.

### Ý nghĩa từng test

| Test | Tín hiệu |
| --- | --- |
| `springBeanIsActuallyProxied` | Context cấp đúng JDK Spring AOP proxy |
| `selfInvocationRunsTargetButSkipsInnerAdvice` | Lời gọi target `this.inner()` bỏ qua advisor |
| `externalInvocationThroughProxyRunsAdvice` | Positive case: pointcut/advice/call-through-proxy thực sự hoạt động |
| `collaboratorUsesInjectedProxy` | Cách khắc phục bằng tách bean không bỏ lỡ advice |
| `manuallyCreatedTargetIsNotAdvised` | Negative case: gọi object Java mới không thể kích hoạt Spring AOP |

Đừng chỉ mock `TraceAspect.aroundInner()` rồi kiểm tra method Java chạy đúng: cách đó bỏ qua cơ chế Spring đăng ký advisor và chọn proxy. Integration test phải dùng **Spring-managed reference**.

## 7. Cách xác minh proxy và advisor của bean thật

Khi chẩn đoán ứng dụng sản xuất, trước hết truy ra **reference mà caller thực sự sử dụng**. Với bean lấy từ context, có thể in:

```java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("workService");

System.out.println("isAopProxy=" + AopUtils.isAopProxy(bean));
System.out.println("isJdkProxy=" + AopUtils.isJdkDynamicProxy(bean));
System.out.println("isCglibProxy=" + AopUtils.isCglibProxy(bean));

if (bean instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println("advisor=" + advisor);
    }
}
```

Đây là **snippet debug đặt ở scope có `context`**, không phải một ứng dụng độc lập. Đọc nó theo đúng thứ tự:

1. `isAopProxy=false`: không có Spring AOP proxy trên reference này. Cần xác định bean có thuộc context đúng, aspect đã đăng ký, pointcut có match, auto-proxy đã bật chưa; hoặc reference thực tế là object `new`.
2. `isAopProxy=true`: có proxy, **chưa đủ** khẳng định advisor của aspect đang quan tâm.
3. `Advised.getAdvisors()`: cho biết chuỗi advisors được gắn vào proxy. Kiểm tra advice/pointcut có mặt, thứ tự và loại advisor. Một số proxy có thể được cấu hình không expose `Advised`; đừng coi instanceof false là kết luận tuyệt đối "không có proxy".
4. Nếu advisor đúng nhưng advice không chạy, so sánh method/signature và **đường gọi**. Test gọi trực tiếp method qua proxy rồi so với self-invocation.
5. Đối với JDK proxy, interface trên proxy và class target không phải cùng kiểu object; đừng cố `((WorkServiceImpl) work).inner()`.

Cần kiểm tra bean qua tên chính xác và qualifier trong ứng dụng có nhiều implementations, vì `context.getBean(WorkService.class)` có thể bị ambiguity nếu nhiều bean.

## 8. Bảng chẩn đoán theo triệu chứng

| Triệu chứng | Nguyên nhân khả dĩ | Cách xác minh / khắc phục |
| --- | --- | --- |
| Client ngoài gọi method có advice, nhưng caller trong cùng class không có | Self-invocation qua `this` | Tách method cần interception sang bean khác và inject proxy |
| Một object có advice, object tự `new` cùng class không có | Tự khởi tạo target | Inject bean thay cho `new` |
| `isAopProxy=false` trên Spring bean | Chưa đăng ký aspect, thiếu auto-proxy, pointcut không match hoặc context khác | Kiểm tra `@Aspect` bean, `@EnableAspectJAutoProxy` và context |
| `isAopProxy=true` nhưng advice cụ thể không chạy | Proxy có advisor khác, method không match, hoặc lời gọi không tới proxy cho method đó | Xem `Advised.getAdvisors()`, kiểm tra path |
| `@Transactional(REQUIRES_NEW)` trên inner không có transaction mới | Self-invocation | Gọi qua bean khác, kiểm thử transaction manager và propagation |
| `@Cacheable` không cache cho lời gọi nội bộ | Self-invocation | Tách bean/gọi qua cache proxy, kiểm tra key/cache manager |
| `@Async` gọi nội bộ vẫn đồng bộ | Self-invocation | Gọi qua Spring bean proxy khác và executor đúng |
| Class `final` hoặc method `final` với class proxy | Không thể subclass/override để chặn | Thiết kế non-final, interface proxy hoặc đổi kiến trúc |
| `@PostConstruct` gọi method có advice nhưng không chạy | Lời gọi diễn ra trước khi proxy hoàn tất | Di chuyển việc kích hoạt ra sau startup hoặc qua một bean điều phối |
| Advice chạy hai lần | Có thể proxy lồng nhau, advice đăng ký trùng hoặc proxy cộng AspectJ weaving | Inspect advisors và weaving thực tế |
| App chạy đúng trong test nhỏ nhưng sai khi deploy | Reference/context/proxy mode khác | Log bean reference và cấu hình tại JVM đang chạy |

Lưu ý: `@Transactional`/`@Cacheable`/`@Async` cần **hạ tầng Spring riêng**, ngoài việc custom `@Aspect` đã đăng ký. Một test `@Before` chạy không chứng minh transaction manager/cache manager/executor đã cấu hình đúng. Nên xác minh từng concern với các phép thử thích hợp.

## 9. Phương án khắc phục: ưu tiên thiết kế lại bean boundary

### Phương án A — Tách trách nhiệm sang Spring bean khác (ưu tiên)

Trước:

```java
@Service
class ExportService {
    public void export() {
        this.loadCached(); // bypass cache proxy
    }

    @Cacheable("documents")
    public String loadCached() {
        return "value";
    }
}
```

Sau:

```java
@Service
class DocumentLoader {
    @Cacheable("documents")
    public String loadCached() {
        return "value";
    }
}

@Service
class ExportService {
    private final DocumentLoader loader;

    ExportService(DocumentLoader loader) {
        this.loader = loader;
    }

    public void export() {
        loader.loadCached(); // Reference Spring-injected, proxy được dùng.
    }
}
```

Các snippet trên chỉ mô tả cách tổ chức class; để chạy caching phải có `@EnableCaching` và `CacheManager` thích hợp. Tách bean làm rõ ranh giới và giúp test độc lập hơn. Nếu class loader bị proxy qua interface, inject theo interface để tránh lỗi ép kiểu.

### Phương án B — Self-injection có kiểm soát

Spring Reference đề cập khả năng inject một **self reference trỏ tới proxy** rồi gọi qua reference đó thay vì `this`. Ví dụ minh họa:

```java
@Service
class JobService implements JobOperations {
    private final ObjectProvider<JobOperations> self;

    JobService(ObjectProvider<JobOperations> self) {
        this.self = self;
    }

    @Override
    public void outer() {
        self.getObject().inner();
    }

    @Override
    public void inner() {
        // Method cần advice
    }
}
```

Đây chỉ là snippet ý tưởng (cần interface `JobOperations` và import `org.springframework.beans.factory.ObjectProvider`). `ObjectProvider` hoãn resolve reference đến lúc gọi method, giảm rủi ro resolve proxy quá sớm trong constructor. Tuy nhiên self-injection **phụ thuộc vòng đời bean, khả năng resolve unique candidate, proxy type và Spring context**, nên phải test kỹ; không coi đây là default pattern hay lý do để bật circular references bừa bãi. Tách bean thường sạch hơn.

### Phương án C — AopContext.currentProxy() (ít khuyến nghị)

Spring cho phép expose proxy:

```java
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
class AopConfig {
}
```

Khi caller đã đi qua proxy có exposeProxy, code bên trong có thể:

```java
((JobOperations) AopContext.currentProxy()).inner();
```

Cần import `org.springframework.aop.framework.AopContext`. Đây **không phải lời gọi hợp lệ ở mọi thời điểm**: khi không có exposed proxy gắn với thread hiện tại, `AopContext.currentProxy()` ném `IllegalStateException`. Nó còn gắn logic nghiệp vụ với Spring AOP runtime và proxy interface, gây khó test/refactor. Chỉ dùng như phương án cuối khi đã cân nhắc tách bean/self-injection.

Quan trọng: `exposeProxy = true` **không tự chuyển** các lời gọi `this.inner()` thành lời gọi proxy. Code phải **chủ động** lấy và dùng proxy reference.

### Phương án D — AspectJ weaving khi yêu cầu vượt giới hạn proxy

AspectJ compile-time hoặc load-time weaving xử lý bytecode của những class/method khớp, nên self-invocation không gặp **giới hạn proxy bypass** tương tự. Nhưng đây là cơ chế khác, cần cấu hình `ajc`/LTW, `-javaagent` hoặc Spring LoadTimeWeaver, và aspect/mode đúng. Chuyển sang AspectJ không phải câu trả lời mặc định cho một service có thể tách thành hai bean.

`@EnableAspectJAutoProxy(proxyTargetClass = true)` **không** phải AspectJ weaving; nó chỉ đổi cách tạo Spring AOP proxy sang class-based proxy, self-invocation vẫn bypass.

## 10. Những cách sửa sai thường gặp

**"Đổi @Before thành @Around là được":** Sai với lỗi đường gọi không qua proxy. Các advice Spring AOP đều cần interception qua proxy.

**"Thêm @Order cao hơn":** `@Order` giúp sắp xếp advisor khi đã có interception, không thể đưa lời gọi `this.inner()` qua proxy.

**"Đổi proxyTargetClass = true":** Class proxy không tự intercept self-invocation; còn thêm giới hạn `final`/`private`.

**"Thêm @Component vào mọi class":** Một class trở thành bean không đảm bảo **client đang dùng bean từ context**. `new` ở chỗ khác vẫn tạo object không qua proxy.

**"Thêm @Transactional vào outer rồi kỳ vọng inner có propagation riêng":** Transaction outer có thể active, nhưng `inner` không được advice riêng áp dụng khi gọi qua `this`. Test phải phân biệt đang ở transaction nào và propagation có chạy không.

**"Ép kiểu proxy về implementation":** Với JDK dynamic proxy, bean proxy thường implement interface chứ không là subclass của `WorkServiceImpl`. Inject theo interface hoặc đánh giá proxy mode trước.

**"Dùng @PostConstruct gọi thẳng method để kích hoạt advice":** Bean có thể chưa được bọc proxy khi chạy lifecycle callback. Khởi động công việc qua listener/runner/coordinator khác sau khi context sẵn sàng, tùy yêu cầu.

**"Tắt proxy và dùng object raw để dễ debug":** Test bằng target raw giúp cô lập nghiệp vụ, nhưng không thay thế kiểm thử sự tham gia của Spring AOP.

## 11. Quy trình debug 7 bước trong dự án thật

**Bước 1 — Xác định hành vi bị thiếu.** Ghi chính xác aspect/advice nào phải chạy, method signature/annotation và kết quả kỳ vọng. Ví dụ: `@Before` không log, cache không hit hay transaction mới không bắt đầu?

**Bước 2 — Lấy reference thật từ client.** Kiểm tra biến được gọi có đến từ constructor injection, `ApplicationContext.getBean(...)`, factory tự tạo, hay `new`. Nếu có nhiều beans, ghi bean name/qualifier.

**Bước 3 — Kiểm tra Spring proxy.** Dùng `AopUtils.isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy` trên chính reference đó. Không dựa vào annotation trên class để tự kết luận.

**Bước 4 — Kiểm tra advisor chain.** Với proxy implements `Advised`, đọc `getAdvisors()`. Nếu thiếu advisor mong đợi, kiểm tra aspect có được đăng ký bean, `@EnableAspectJAutoProxy`, pointcut type pattern, context và auto-proxy config.

**Bước 5 — So sánh gọi ngoài và self-invocation.** Gọi `service.inner()` trực tiếp qua proxy trong một test; sau đó gọi `service.outer()` dẫn tới `this.inner()`. Nếu chỉ lượt đầu có advice, nghi vấn self-invocation đã được xác định rất rõ.

**Bước 6 — Kiểm tra proxyability và context.** Method có public/override-able không? JDK interface có method cần gọi không? Bean có ở root/child context phù hợp? Có code khởi chạy quá sớm trong constructor/`@PostConstruct` không?

**Bước 7 — Sửa boundary rồi thêm regression test.** Ưu tiên tách bean và inject proxy. Viết tests kiểm chứng cả đường gọi đúng, đường gọi bypass và behavior downstream (transaction commit/cache hit/async thread) nếu đó là mục đích thực.

Lưu một ví dụ nhỏ tương tự phần 5 làm diagnostic baseline thường hữu ích hơn việc liên tục thay annotation trên code sản xuất.

## 12. Phân biệt với lỗi pointcut không match và lỗi advisor khác

Ba lỗi sau có thể cho **cùng dấu hiệu bên ngoài** "không thấy log":

| Lỗi | Proxy tồn tại? | Advisor quan tâm tồn tại? | Invocation đi qua proxy? | Hướng sửa |
| --- | --- | --- | --- | --- |
| **Không có auto-proxy phù hợp** | Không, hoặc khác reference | Không | Không | Kiểm tra bean/aspect/config/context |
| **Pointcut hoặc method không khớp** | Có thể có | Có thể có, nhưng không match method | Có | Sửa pointcut, annotation placement, method signature |
| **Lời gọi bypass proxy** | Có thể có proxy đúng ở nơi khác | Có thể có và match | **Không** tại method đang xét | Sửa reference/đường gọi, tách bean |

Một test gọi qua proxy và thấy advice thành công chứng minh **pointcut + advisor hoạt động cho lượt đó**; nếu đường gọi khác vẫn không thấy advice, nên điều tra reference/call graph hơn là sửa regex pointcut.

Đối với `@Around`, advice có thể được gọi nhưng **không gọi `proceed()`**, làm target không chạy. Đó là trường hợp ngược lại: lời gọi đã vào proxy nhưng advisor cố ý (hoặc sai sót) short-circuit. Phải phân biệt bằng log ở trước/sau `proceed()` và trong target.

## 13. Tương tác với Spring @Transactional, @Cacheable, @Async

### @Transactional

Ví dụ:

```java
@Service
public class BookingService {
    @Transactional
    public void book() {
        this.reserve(); // Không kích hoạt advisor @Transactional riêng trên reserve.
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve() {
        // ...
    }
}
```

Nếu client gọi `book()` qua proxy, transaction của `book` có thể tồn tại. Nhưng `this.reserve()` **không đi qua proxy**, nên `REQUIRES_NEW` trên `reserve` không mở transaction mới. Đây là lỗi nghiệp vụ nghiêm trọng hơn thiếu log: rollback/commit boundary khác dự kiến. Khi test, kiểm tra trạng thái database và transaction boundary thực, không chỉ boolean "có transaction".

### @Cacheable

```java
@Service
public class PriceService {
    public String quote(String sku) {
        return this.loadPrice(sku);
    }

    @Cacheable("prices")
    public String loadPrice(String sku) {
        return "price:" + sku;
    }
}
```

Nếu gọi `quote` qua Spring proxy, lời gọi nội bộ `this.loadPrice` không vào cache interceptor. Gọi `loadPrice` qua proxy trực tiếp thì caching mới có cơ hội chạy. Cần `@EnableCaching`, `CacheManager` phù hợp và cache key/eviction đúng; đây là các snippet minh họa, không phải chương trình độc lập.

### @Async

```java
@Service
public class MailService {
    public void notifyUser() {
        this.sendAsync();
    }

    @Async
    public void sendAsync() {
        // ...
    }
}
```

`sendAsync()` được gọi qua `this` sẽ thực hiện như Java call bình thường trên cùng thread hiện tại. Cần gọi qua Spring async proxy và cấu hình `@EnableAsync`/executor đúng; self-invocation không tự dispatch sang thread khác.

### Custom Spring @Aspect và hạ tầng khác

Một bean có thể có cả custom advisor, transaction advisor, cache advisor hoặc security advisor. Chúng có thể sắp thành chuỗi trên cùng proxy. `@Order` điều chỉnh **thứ tự** khi đi qua proxy; nó không sửa self-invocation. Khi cache hit, một advice nằm bên trong cache interceptor có thể không được chạy dù caller đã đi qua proxy; đó là **short-circuit của advisor khác**, không phải bypass proxy. Test cần phân biệt bằng advisor chain và đường gọi cụ thể.

## 14. Checklist kiểm thử sau khi sửa

- [ ] Bean gọi thực tế được inject từ Spring context; không dùng object `new` ngoài ý muốn.
- [ ] Spring `@Aspect` là bean; đã bật auto-proxy trong đúng context.
- [ ] `AopUtils.isAopProxy(reference)` phù hợp với expectation, và advisor mong đợi có mặt khi inspect.
- [ ] Có test gọi **qua proxy** và kiểm tra advice chạy đúng một lần.
- [ ] Có test self-invocation hoặc raw target làm đối chứng, nếu bug xuất phát từ đó.
- [ ] Đã sửa đường gọi bằng tách bean (ưu tiên) hoặc giải pháp khác được giải thích rõ.
- [ ] Không trộn lẫn test của `@Around`, transaction/cache/async với giả định các advisor đều cùng precedence.
- [ ] Proxy mode JDK/CGLIB phù hợp interface, method visibility và tính `final` của class/method.
- [ ] Không dựa vào constructor hoặc `@PostConstruct` để kích hoạt interception.
- [ ] Khi service có `@Transactional(REQUIRES_NEW)`, test transaction boundary thực thay vì chỉ test log.
- [ ] Khi có `@Cacheable`, test cache miss/hit qua proxy, không chỉ assert method return.
- [ ] Khi có `@Async`, test task được dispatch và await kết quả đúng cách.
- [ ] Tài liệu hóa bean ownership, proxy config và call path trong trường hợp context phức tạp.

## 15. Tự kiểm tra

**1. Tại sao `work.outer()` đi qua proxy mà `this.inner()` vẫn không có advice?**  
Vì proxy intercept lời gọi **outer**, nhưng khi target chạy `outer()`, `this.inner()` là lời gọi nội bộ trực tiếp trên target, không quay qua proxy một lần nữa.

**2. `proxyTargetClass = true` có sửa self-invocation không?**  
Không. Nó chọn class-based proxy; self-invocation vẫn bypass.

**3. Vì sao `new WorkServiceImpl(events).inner()` không tạo aop events?**  
Vì object đó không được Spring trả về như AOP proxy, dù class giống hệt target của Spring bean.

**4. Làm thế nào để đo đúng advisor đang nằm trên proxy?**  
Kiểm tra `AopUtils.isAopProxy` và, nếu bean implements `Advised`, duyệt `getAdvisors()`, sau đó test behavior thực tế.

**5. Vì sao thay `@Before` thành `@Around` chưa chắc hết lỗi?**  
Cả hai cần lời gọi đi qua Spring proxy. Đổi advice type không sửa call graph.

**6. Khi nào `AopContext.currentProxy()` dùng được?**  
Khi có một lượt gọi đang chạy qua proxy được cấu hình `exposeProxy=true` trong thread hiện tại; không nên dùng nó như giải pháp mặc định.

**7. Nếu `@Transactional` trên `inner` không áp dụng nhưng giao dịch vẫn active, có mâu thuẫn không?**  
Không. Transaction active có thể do method ngoài tạo. Annotation của inner chưa chắc được intercept.

**8. Cách sửa lâu bền nhất trong đa số tình huống self-invocation là gì?**  
Tách method cần advice sang Spring bean khác và inject dependency để lời gọi đi qua proxy rõ ràng.

## Tài liệu Spring chính thức

- Spring Framework 6.2 — Proxying Mechanisms và Self Invocation: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- Spring Framework 6.2 — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html
- Spring Framework — AOP Proxies: https://docs.spring.io/spring-framework/reference/core/aop/introduction-proxies.html
- Spring Framework — @Transactional proxy mode: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- Spring Framework — Cache annotations: https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html
- Spring Framework — Async execution: https://docs.spring.io/spring-framework/reference/integration/scheduling.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-004">Bean được khởi tạo sớm hoặc lấy từ sai ApplicationContext</a>

<details>
<summary>Xem chi tiết</summary>

# Bean được khởi tạo sớm hoặc lấy từ sai ApplicationContext — chẩn đoán Spring AOP

## 1. Triệu chứng: Có @Aspect nhưng advice không chạy

Một Spring service được khai báo là bean, có `@Aspect` và pointcut trông đúng, nhưng lời gọi method không xuất hiện trong advice hoặc annotation như `@Transactional`/`@Cacheable` không có tác dụng. Ngoài những nguyên nhân quen thuộc như self-invocation, final method, pointcut không match hay gọi object bằng `new`, có **hai nhóm lỗi thuộc lifecycle và phạm vi container** rất dễ bị bỏ sót:

1. **Bean được khởi tạo quá sớm**, trước khi hạ tầng `BeanPostProcessor` của Spring AOP hoàn tất đăng ký. Bean singleton có thể tồn tại trong container nhưng không nhận auto-proxy.
2. **Đối tượng lấy từ sai `ApplicationContext`**. Bạn cấu hình `@EnableAspectJAutoProxy` và `@Aspect` trong một context, trong khi bean nghiệp vụ thực sự thuộc một context khác, thường là quan hệ **parent/root context và child web context**.

Điểm cốt lõi: **"Là Spring bean" chưa đủ để kết luận nó có Spring AOP proxy.** Phải kiểm tra *ai tạo bean*, *bean được khởi tạo lúc nào*, *hạ tầng nào có mặt ở chính context đó*, *reference caller nắm là proxy hay target*, và *method invocation có thực sự đi qua proxy không*.

Bài này giới hạn vào **Spring Aspect/Spring AOP**, cụ thể là chẩn đoán auto-proxy và cách quan sát `ApplicationContext`. Các khái niệm container chung chỉ giải thích khi cần để tìm nguyên nhân.

## 2. Cơ chế: BeanPostProcessor đóng vai trò gì trong Spring AOP?

Với `@EnableAspectJAutoProxy`, Spring đăng ký hạ tầng auto-proxy phù hợp, trong đó có auto-proxy creator thuộc hệ `BeanPostProcessor` (BPP). Auto-proxy creator xem xét các Spring bean khi chúng được tạo, tìm advisor/aspect thích hợp và có thể **trả một proxy thay cho target**.

Luồng bình thường có dạng:

~~~text
ApplicationContext.refresh()
  |
  +-- Đăng ký / xử lý metadata và bean definitions
  |
  +-- Chạy các BeanFactoryPostProcessor
  |
  +-- Phát hiện và đăng ký các BeanPostProcessor
  |     (bao gồm Spring AOP auto-proxy creator)
  |
  +-- Khởi tạo các singleton thường còn lại
        |
        +-- create target bean
        +-- dependency injection
        +-- initialization callbacks
        +-- BeanPostProcessor.afterInitialization(...)
              |
              +-- AOP auto-proxy creator xem xét advisor
                    ├-- nếu match → trả proxy
                    └-- nếu không match → có thể giữ target
~~~

Đây là sơ đồ đơn giản hóa để hiểu **thời điểm đăng ký BPP**. Spring còn có xử lý đặc biệt cho circular reference, infrastructure beans, `FactoryBean`, early proxy references và các loại post-processor có thứ tự khác nhau. Đừng hiểu sơ đồ là lịch trình cứng cho mọi loại bean.

Nếu một component gọi `beanFactory.getBean("paymentService")` **ngay trong giai đoạn `BeanFactoryPostProcessor`**, Spring buộc phải tạo bean đó trước thời điểm đăng ký toàn bộ BPP thông thường. Auto-proxy creator chưa kịp post-process object ấy; singleton sớm có thể được cache nguyên dạng. Khi context khởi tạo xong, gọi `getBean("paymentService")` lần nữa thường chỉ trả lại singleton đã có, **không tự chạy lại toàn bộ lifecycle để thêm proxy**.

### BeanFactoryPostProcessor khác BeanPostProcessor

| Cơ chế | Thời điểm / đối tượng | Liên hệ AOP |
| --- | --- | --- |
| `BeanDefinitionRegistryPostProcessor` | Xử lý đăng ký bean definitions sớm | Giúp Spring biết metadata / definitions trước khi tạo bean |
| `BeanFactoryPostProcessor` | Điều chỉnh bean definitions trước giai đoạn khởi tạo bean bình thường | **Không nên gọi `getBean()` để ép tạo application bean** tại đây |
| `BeanPostProcessor` | Xử lý từng instance trong bean creation lifecycle | Hạ tầng Spring AOP auto-proxy nằm ở nhóm này |
| `@PostConstruct` / init method | Chạy trong giai đoạn khởi tạo **target** | Thường trước bước bọc AOP proxy cuối cùng; không mặc nhiên được advise |
| Spring AOP advice | Chạy khi caller gọi method **qua proxy** đã được xây dựng | Không chạy chỉ vì bean definition có tồn tại |

Một bean được **khởi tạo sớm** không đồng nghĩa **mọi kiểu early reference đều không có proxy**: Spring có cơ chế `getEarlyBeanReference(...)` để xử lý một số circular dependencies. Trường hợp lỗi đang xét là **bean được instantiate trước khi các BPP/auto-proxy infrastructure cần thiết đăng ký**. Phân biệt hai hiện tượng để tránh sửa sai nguyên nhân.

## 3. Những tình huống tạo bean quá sớm

### 3.1. BeanFactoryPostProcessor gọi getBean() sai mục đích

`BeanFactoryPostProcessor` chủ yếu được thiết kế để đọc/chỉnh sửa **bean definitions**, không phải dùng như chỗ chạy nghiệp vụ. Ví dụ **anti-pattern**:

~~~java
@Bean
static BeanFactoryPostProcessor prematureProcessor() {
    return beanFactory -> {
        // Không làm trong ứng dụng thực tế:
        // ép khởi tạo service trước lúc tất cả BeanPostProcessor sẵn sàng.
        beanFactory.getBean("orderService");
    };
}
~~~

Đoạn code này chỉ là **mẫu lỗi**, không phải lời khuyên cấu hình. Nếu `orderService` cần Spring AOP proxy, việc tạo nó vào giai đoạn ấy có thể làm advice không hoạt động. `static @Bean` giúp tránh phải tạo sớm configuration class, nhưng **không làm hợp lệ** hành vi ép lấy service ngay bên trong processor.

Cách đúng hơn là thao tác với `BeanDefinition`/metadata trong BFPP; chuyển công việc cần gọi service sang một lifecycle hook muộn hơn như `ApplicationRunner`, `CommandLineRunner` hoặc xử lý sau khi context sẵn sàng, tùy yêu cầu và framework.

### 3.2. BeanPostProcessor inject trực tiếp service nghiệp vụ

`BeanPostProcessor` được tạo trong giai đoạn chuẩn bị bộ post-processor. Nếu constructor của BPP inject `PaymentService`, service có thể bị tạo **trong pha đăng ký BPP**, trước khi auto-proxy creator được thiết lập đầy đủ. Theo tài liệu Spring, bản thân các BPP và những bean chúng trực tiếp phụ thuộc thường không đủ điều kiện để tất cả BPP khác xử lý (ví dụ auto-proxying).

Mẫu dễ gây lỗi:

~~~java
public class EarlyObserver implements BeanPostProcessor {
    private final PaymentService service;

    public EarlyObserver(PaymentService service) {
        this.service = service; // có thể ép khởi tạo PaymentService quá sớm
    }
}
~~~

Đây là **snippet minh họa**, giả định `PaymentService` và import `BeanPostProcessor` tồn tại trong ứng dụng. Spring có thể phát cảnh báo:

~~~text
Bean 'paymentService' of type [...] is not eligible for getting
processed by all BeanPostProcessors (for example: not eligible
for auto-proxying)
~~~

Nội dung/level log chính xác thay đổi theo phiên bản và logging configuration; **đó là dấu hiệu chẩn đoán, không phải thông báo duy nhất bắt buộc luôn xuất hiện**.

Thay vì inject trực tiếp application service vào BPP, nên giữ processor tối giản, không gọi nghiệp vụ khi đăng ký; chỉ lấy bean lazily ở giai đoạn cho phép nếu thật sự cần. Khi dùng `@Bean` tạo BPP, nên khai báo method `static`, return type đúng `BeanPostProcessor` hoặc implementation BPP, và hạn chế dependency.

### 3.3. @Configuration và @Bean khiến dây chuyền khởi tạo quá sớm

Một method `@Bean` non-static trả `BeanFactoryPostProcessor` hoặc `BeanPostProcessor` có thể đòi Spring instantiate class `@Configuration` sớm để gọi method ấy. Từ đó các dependency của configuration class cũng có thể bị khởi tạo sớm hoặc annotation processing của configuration class không hoàn tất đúng lifecycle.

Vì vậy:
- Với `@Bean` định nghĩa post-processor, ưu tiên **`static`** khi phù hợp, và khai báo **return type nhận diện được là BPP/BFPP**.
- Không đặt logic gọi service trong constructor/initializer của configuration hoặc post-processor.
- Không phụ thuộc advice trên method đang chạy **trong quá trình tạo chính proxy**; gọi như vậy dễ tạo vòng phụ thuộc.

### 3.4. Eager type matching và FactoryBean

Một số thao tác tìm bean theo type, đặc biệt trong container tùy biến hoặc tích hợp `FactoryBean`, có thể dẫn đến khởi tạo bean sớm để xác định type. Khi debug, kiểm tra liệu thư viện/processor nào đang gọi `getBeansOfType(...)`, `getBean(...)` hoặc khởi tạo `FactoryBean` ở startup.

Không kết luận mọi lời gọi `getBeanNamesForType(...)` đều instantiate bean: hành vi phụ thuộc API overload, flag `allowEagerInit` và loại bean. Nếu chỉ cần metadata cho chẩn đoán, cân nhắc overload có `allowEagerInit=false` trong tình huống thích hợp và **tránh gọi API gây instantiate trong BFPP/BPP phase**.

## 4. Ví dụ chạy được: tạo một bean quá sớm và một bean đúng thời điểm

Ví dụ dùng Java 17, Spring Framework 6.1.x và AspectJ annotations do Spring AOP hiểu. Một chương trình console duy nhất tạo ra hai scenario:
- Cùng một `ApplicationContext` có bean `earlyService` bị lấy quá sớm từ BFPP và bean `normalService` được tạo đúng pha, chỉ bean thứ hai nhận proxy.
- Một parent context không có AOP và child context có AOP; child lấy bean của parent vẫn không biến parent bean thành proxy, còn bean local của child nhận proxy.

Các scenario cố ý tách context để quan sát rõ. Đây là **mô phỏng lỗi để kiểm thử**, không phải mẫu thiết kế được khuyên áp dụng trong production.

### 4.1. Cấu trúc project

~~~text
build.gradle
src/main/java/demo/diagnostics/
    TaskService.java
    TraceAspect.java
    AopConfiguration.java
    DiagnosticApplication.java
~~~

### 4.2. Spring service target

**`src/main/java/demo/diagnostics/TaskService.java`**

~~~java
package demo.diagnostics;

public class TaskService {

    private final String label;

    public TaskService(String label) {
        this.label = label;
    }

    public void work() {
        System.out.println("target: " + label);
    }
}
~~~

Class là public, method `work()` public và không final. Như vậy ta loại bỏ các nguyên nhân khác như CGLIB không override được final method, visibility hoặc pointcut sai.

### 4.3. Spring @Aspect

**`src/main/java/demo/diagnostics/TraceAspect.java`**

~~~java
package demo.diagnostics;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {

    @Before("execution(* demo.diagnostics.TaskService.work(..))")
    public void beforeWork() {
        System.out.println("advice: before work");
    }
}
~~~

Aspect khớp method `TaskService.work()`. Nó **chỉ chạy trên Spring AOP proxy eligible**, không tự sửa class `TaskService`. Aspect phải được đăng ký làm Spring bean trong context tạo proxy, ví dụ như `registerBean(TraceAspect.class)` dưới đây.

### 4.4. Cấu hình AOP của một context

**`src/main/java/demo/diagnostics/AopConfiguration.java`**

~~~java
package demo.diagnostics;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfiguration {
}
~~~

Chúng ta bật class-based (CGLIB) proxy để caller có thể lấy theo `TaskService.class` dù target không có interface nghiệp vụ. Trong ứng dụng thực tế, JDK proxy cũng phù hợp khi consumer truy cập qua business interface và cấu hình tương ứng.

**Chú ý scope:** `@EnableAspectJAutoProxy` chỉ ảnh hưởng hạ tầng được đăng ký trong context đó. Trong một hệ có root/child contexts, bật annotation chỉ ở child không làm BPP ở child quay sang post-process toàn bộ parent beans.

### 4.5. Chương trình main minh họa cả hai lỗi

**`src/main/java/demo/diagnostics/DiagnosticApplication.java`**

~~~java
package demo.diagnostics;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DiagnosticApplication {

    public static void main(String[] args) {
        earlyCreationScenario();
        wrongContextScenario();
    }

    private static void earlyCreationScenario() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {

            context.register(AopConfiguration.class);
            context.registerBean(TraceAspect.class);

            context.registerBean(
                "earlyService", TaskService.class,
                () -> new TaskService("early")
            );
            context.registerBean(
                "normalService", TaskService.class,
                () -> new TaskService("normal")
            );

            // CỐ Ý SAI: ép tạo application bean trong BFPP phase.
            context.addBeanFactoryPostProcessor(
                beanFactory -> beanFactory.getBean("earlyService")
            );

            context.refresh();

            TaskService early =
                context.getBean("earlyService", TaskService.class);
            TaskService normal =
                context.getBean("normalService", TaskService.class);

            System.out.println(
                "early proxied=" + AopUtils.isAopProxy(early)
            );
            System.out.println(
                "normal proxied=" + AopUtils.isAopProxy(normal)
            );

            early.work();
            normal.work();
        }
    }

    private static void wrongContextScenario() {
        try (AnnotationConfigApplicationContext parent =
                     new AnnotationConfigApplicationContext()) {

            parent.registerBean(
                "parentService", TaskService.class,
                () -> new TaskService("parent")
            );
            parent.refresh();

            try (AnnotationConfigApplicationContext child =
                         new AnnotationConfigApplicationContext()) {

                child.setParent(parent);
                child.register(AopConfiguration.class);
                child.registerBean(TraceAspect.class);
                child.registerBean(
                    "childService", TaskService.class,
                    () -> new TaskService("child")
                );
                child.refresh();

                TaskService inherited =
                    child.getBean("parentService", TaskService.class);
                TaskService local =
                    child.getBean("childService", TaskService.class);

                System.out.println(
                    "parent same bean=" +
                    (inherited == parent.getBean("parentService"))
                );
                System.out.println(
                    "parent proxied=" + AopUtils.isAopProxy(inherited)
                );
                System.out.println(
                    "child proxied=" + AopUtils.isAopProxy(local)
                );

                inherited.work();
                local.work();
            }
        }
    }
}
~~~

Đọc kỹ hai tình huống:

**Tình huống A — same context nhưng tạo trước BPP:**
1. `AopConfiguration` và `TraceAspect` được đăng ký dưới dạng definitions.
2. `addBeanFactoryPostProcessor(...)` đặt một BFPP chạy lúc `refresh()`, **trước pha đăng ký thông thường của BPP**.
3. BFPP gọi `getBean("earlyService")` và ép tạo singleton `earlyService` quá sớm.
4. `normalService` không bị BFPP đụng vào và được tạo sau khi hạ tầng Spring AOP đã sẵn sàng.
5. `early.work()` chạy target trực tiếp; `normal.work()` đi qua CGLIB proxy và được advice.

**Tình huống B — parent và child ApplicationContext:**
1. `parent` sở hữu bean `parentService`, nhưng không khai báo `AopConfiguration` hoặc `TraceAspect`.
2. `child` sở hữu `AopConfiguration`, `TraceAspect` và `childService`.
3. Tìm `parentService` qua `child.getBean(...)` trả **object thuộc parent context**, không tạo bản sao, không chạy child BPP trên parent bean.
4. Vì thế `inherited` và `parent.getBean("parentService")` là cùng reference; parent bean không được proxy bởi child.
5. Bean `childService` thực sự thuộc child, có auto-proxy creator/aspect trong cùng context nên nhận proxy.

### 4.6. Gradle dependencies và lệnh chạy

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

dependencies {
    implementation 'org.springframework:spring-context:6.1.14'
    implementation 'org.springframework:spring-aop:6.1.14'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'
}

application {
    mainClass = 'demo.diagnostics.DiagnosticApplication'
}
~~~

`spring-context` và `spring-aop` cung cấp IoC/proxy APIs; `aspectjweaver` cung cấp `@Aspect`/`@Before` và hạ tầng phân tích pointcut mà Spring AOP sử dụng. **Đây là Spring AOP proxy, không phải AspectJ load-time weaving**: không cần `-javaagent` hay `META-INF/aop.xml`.

Chạy với Gradle wrapper:

~~~bash
./gradlew run
~~~

Trên Windows:

~~~powershell
.\gradlew.bat run
~~~

Các dòng `System.out.println` do chương trình chủ động in ra (không tính log Gradle, Spring hoặc cảnh báo khởi tạo sớm):

~~~text
early proxied=false
normal proxied=true
target: early
advice: before work
target: normal
parent same bean=true
parent proxied=false
child proxied=true
target: parent
advice: before work
target: child
~~~

**Kết quả cần giải thích đúng:** `earlyService` và `parentService` **đều là Spring beans**. Nhưng một bean đã được tạo trước khi BPP đủ điều kiện tham gia, bean kia được tạo trong **context không có AOP setup**. Cùng method `work()`, cùng aspect signature, nhưng chỉ proxy thuộc đúng lifecycle/context mới có advice.

Ví dụ sử dụng một anti-pattern có chủ đích để tạo triệu chứng. Không copy BFPP ép `getBean()` vào application production.

## 5. Kiểm tra bằng assertions thay vì chỉ đoán từ log

Trong một test có `ApplicationContext` thực, kiểm tra cả **đối tượng** và **hành vi advice**:

~~~java
TaskService rawEarly =
    context.getBean("earlyService", TaskService.class);
TaskService proxiedNormal =
    context.getBean("normalService", TaskService.class);

assertFalse(AopUtils.isAopProxy(rawEarly));
assertTrue(AopUtils.isAopProxy(proxiedNormal));
assertTrue(AopUtils.isCglibProxy(proxiedNormal));
~~~

Đây là **snippet cho JUnit test** có biến `context` và imports `AopUtils`, `assertTrue`, `assertFalse`. Nó không phải một test class độc lập. Test cần kiểm chứng **advice thật sự được gọi**, chẳng hạn recorder trong advice hoặc test-visible counter, để phân biệt "có proxy" với "advisor có match method này".

Các assertions tương ứng với parent/child context:

~~~java
Object fromParent = parent.getBean("parentService");
Object fromChildLookup = child.getBean("parentService");
Object definedInChild = child.getBean("childService");

assertSame(fromParent, fromChildLookup);
assertFalse(AopUtils.isAopProxy(fromParent));
assertTrue(AopUtils.isAopProxy(definedInChild));
~~~

Không dùng assertion `bean != null` để tuyên bố AOP hoạt động. Nếu `@Aspect` chưa được đăng ký hoặc pointcut sai, bean vẫn có thể khởi tạo và trả giá trị business bình thường.

Với vấn đề timing/registration, tích hợp test cần dựng **context giống production**. Một unit test gọi `new TaskService(...)` hoàn toàn không thể tái hiện BPP registration hay context hierarchy.

## 6. Sai ApplicationContext cụ thể là sai như thế nào?

### 6.1. Root context và DispatcherServlet child context trong Spring MVC

Ứng dụng Spring MVC truyền thống có thể có **root `WebApplicationContext`** và một hoặc nhiều **child web contexts** gắn với `DispatcherServlet`.

~~~text
ROOT ApplicationContext
  ├── orderService
  ├── transactionManager
  └── repositoryBeans

CHILD DispatcherServlet ApplicationContext
  ├── controller
  ├── mvcInfrastructure
  ├── @EnableAspectJAutoProxy
  └── auditAspect
        |
        └── Controller inject/getBean(orderService)
            → lookup có thể nhận bean từ ROOT
            → CHILD auto-proxy creator KHÔNG post-process bean ROOT
~~~

Nếu aspect đăng ký ở child còn service được root tạo trước đó, child có thể **nhìn thấy** root service thông qua parent lookup nhưng không biến nó thành bean thuộc child. BPP của từng container chỉ post-process bean được container ấy tạo.

**Không đơn giản kết luận có bao nhiêu context từ số lượng `@Configuration`.** Nhiều configuration classes có thể chung một `ApplicationContext`. Chỉ khi thực sự tồn tại nhiều container (root/child, nhiều dispatcher, nhiều test contexts, plugin contexts, bootstrap context tùy framework) mới có lỗi ranh giới này.

Với Spring Boot application thông thường, nhiều lớp cấu hình vẫn có thể ở **cùng một context**, không nhất thiết xuất hiện root/child như Servlet deployment truyền thống. Hãy **đo/kiểm tra `ApplicationContext` runtime** chứ không phỏng đoán.

### 6.2. @EnableAspectJAutoProxy là cấu hình theo context

Spring đăng ký auto-proxy infrastructure trong context được cấu hình. Việc đặt `@EnableAspectJAutoProxy` trong một child context không phải tùy chọn toàn JVM. Tương tự, một `@Aspect` thành bean trong parent không mặc nhiên có cùng advisor setup trong mọi child context.

Lựa chọn tốt là đặt **AOP configuration/aspect tại context sở hữu các target beans cần advise**, theo cấu trúc application. Nếu cần advise service ở root và controller ở child, có thể phải cân nhắc cấu hình phù hợp ở từng context riêng, thay vì kỳ vọng BPP của child tự làm thay parent.

Tránh đăng ký cùng aspect hai lần một cách vô thức hoặc đưa bean business vào cả parent lẫn child: dễ tạo bean trùng tên, shadowing, confusing proxy ownership, state và transaction boundaries.

### 6.3. Bean shadowing khi tên trùng ở parent/child

Nếu parent và child **đều có bean tên `orderService`**, `child.getBean("orderService")` thường chọn bean local của child, trong khi `parent.getBean("orderService")` chọn bean trong parent.

Hai instance có thể cùng class nhưng:
- Đi qua hai tập BPP khác nhau.
- Có hoặc không có AOP proxy khác nhau.
- Chứa state khác nhau.
- Dùng transaction manager/repository wiring khác nhau.

Vì thế khi debug tên bean, đừng chỉ log `getClass().getName()`. Hãy đồng thời kiểm tra **bean definition owner**, reference identity và danh sách advisor.

## 7. Bộ công cụ chẩn đoán Spring AOP

### 7.1. Xác nhận proxy type và target class

Trong một ứng dụng đang chạy, với object thật mà caller giữ:

~~~java
Object bean = context.getBean("orderService");

System.out.println("proxy=" + AopUtils.isAopProxy(bean));
System.out.println("jdk=" + AopUtils.isJdkDynamicProxy(bean));
System.out.println("cglib=" + AopUtils.isCglibProxy(bean));
System.out.println("targetClass=" + AopUtils.getTargetClass(bean).getName());
System.out.println("runtimeClass=" + bean.getClass().getName());
System.out.println("identity=" + System.identityHashCode(bean));
~~~

Đây là snippet chẩn đoán với import `org.springframework.aop.support.AopUtils` và biến `context` trỏ đến `ApplicationContext` thực. **`getTargetClass` chỉ nói về kiểu target, không chứng minh bean đã nhận một advisor cụ thể.**

Với object implements `org.springframework.aop.framework.Advised` (đa số Spring AOP proxy thông thường nếu không bị cấu hình opaque), có thể xem:

~~~java
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
~~~

Danh sách advisor giúp phân biệt "có proxy vì transaction" với "đã có đúng custom aspect cần tìm". Nếu bean là JDK proxy, không được mặc định ép kiểu về concrete implementation class; inject qua interface phù hợp.

### 7.2. Xem context đang sở hữu bean

Cần phân biệt **lookup nhìn thấy được** và **bean definition thuộc container đó**:

~~~java
ConfigurableApplicationContext active = /* context đang dùng */;

System.out.println("context id=" + active.getId());
System.out.println("parent=" +
    (active.getParent() == null ? "none" : active.getParent().getId()));
System.out.println("visible=" + active.containsBean("orderService"));
System.out.println("localDefinition=" +
    active.getBeanFactory().containsBeanDefinition("orderService"));
System.out.println("localSingleton=" +
    active.getBeanFactory().containsSingleton("orderService"));
~~~

Đây là **snippet**, cần import `ConfigurableApplicationContext` và có object `active` do ứng dụng cấp. **`containsBean`** có thể thành true qua parent hierarchy, trong khi `containsBeanDefinition` trên bean factory local là false. Một singleton được Spring đăng ký trực tiếp đôi khi không có bean definition, vì vậy cần xem **cả local singleton registry** nếu đang điều tra trường hợp registerSingleton/custom factory.

Nếu có parent context, in tương tự `parent.getId()` và kiểm tra `parent.getBeanFactory()` (nếu parent cũng là `ConfigurableApplicationContext`). Cùng bean name không đồng nghĩa cùng instance/context.

### 7.3. Đọc log đăng ký BeanPostProcessor và auto-proxy

Những dấu hiệu quan trọng:
- Log có cụm `not eligible for getting processed by all BeanPostProcessors` hoặc `not eligible for auto-proxying`.
- Stack trace startup cho thấy BFPP/BPP/custom configuration gọi `getBean(...)` quá sớm.
- Auto-proxy infrastructure chưa được đăng ký trong context tạo service.
- Bean được khởi tạo trước khi aspect/advisor được Spring đăng ký hoàn chỉnh.
- Bean nằm trong parent context, nhưng AOP infrastructure nằm ở child context.

Tùy Spring version/logging backend, có thể bật log `DEBUG`/`TRACE` có mục tiêu cho `org.springframework.aop.framework.autoproxy`, `org.springframework.beans.factory.support` hoặc các class BPP liên quan trong phiên điều tra. **Không hứa rằng mọi Spring version đều có cùng message** hoặc mọi early bean đều luôn log warning.

Không log dữ liệu nhạy cảm từ method arguments hay in toàn bộ bean definitions vào production logs nếu không cần. Chỉ log bean name, class, context identity và advisor ở phạm vi có kiểm soát.

## 8. Bảng phân biệt nguyên nhân tương tự nhau

| Hiện tượng | Khả năng chính | Kiểm tra phân biệt |
| --- | --- | --- |
| `getBean(...)` có object nhưng advice không chạy | Bean chưa được proxy hoặc advisor không match | `isAopProxy`, `getAdvisors`, pointcut |
| Một bean cụ thể không proxied, bean cùng type khác proxied | Khởi tạo sớm, eligibility, scope hoặc config khác nhau | Creation timeline, BPP log, bean name, context |
| Parent bean lấy qua child không proxied | Child BPP không xử lý parent-owned bean | `child.getBean(...) == parent.getBean(...)` |
| `@Aspect` ở child, service ở root | Auto-proxy/advisor không ở container sở hữu service | Context hierarchy và bean definition owner |
| Aspect bean tồn tại nhưng không advice service | Advisor chưa được auto-proxy creator dùng, sai pointcut hoặc khác context | Inspect advisor + actual target type |
| Lời gọi `this.inner()` không advice | Self-invocation bypass proxy | So sánh direct proxy call với internal call |
| Final/private method không advice | CGLIB override/visibility giới hạn | Kiểm tra proxy strategy, modifier |
| Object tạo bằng `new` không advice | Không qua Spring container/proxy | So sánh reference raw với injected bean |
| Bean bị lấy trước `refresh()` xong | Có thể bị early init trước BPP | Log/trace bean creation; không tự gọi getBean thêm |
| Controller có advice nhưng service không | Có thể config AOP/bean ownership khác nhau | Kiểm tra root vs servlet child contexts |
| Chỉ một test fail trong suite | Context caching, test config hierarchy hoặc bean override | `@ContextHierarchy`, test contexts, bean names |
| `isAopProxy=true` nhưng custom advice không chạy | Có thể proxy vì một advisor **khác** | Kiểm tra advisor list/pointcut và đường gọi method |

Điều cần tránh là sửa mọi trường hợp bằng `proxyTargetClass=true`. Chọn CGLIB thay JDK proxy **không thể chữa** lỗi bean được instantiate trước auto-proxy creator hoặc bean thuộc sai context. Cũng đừng bật AspectJ LTW chỉ vì thiếu một advisor; trước tiên chẩn đoán lifecycle và ownership.

## 9. Cách sửa đúng khi bean được khởi tạo sớm

1. **Dời logic cần service ra khỏi BFPP/BPP startup phase.** Không dùng `getBean("service")` trong `BeanFactoryPostProcessor` để làm việc nghiệp vụ.
2. **Giảm dependencies của BPP.** Ưu tiên BPP độc lập, được tạo sớm, không inject trực tiếp service muốn auto-proxy. Nếu cần lấy dependency trong callback, dùng cơ chế lazy/resolution phù hợp, nhưng vẫn phải chứng minh không phát sinh init sớm khi BPP được tạo.
3. **Dùng `static @Bean` cho processor factory methods** và khai báo return type cụ thể hoặc `BeanPostProcessor`/`BeanFactoryPostProcessor` để Spring nhận diện trước khi instantiate configuration class.
4. **Tách application initialization khỏi infrastructure creation.** Công việc sau startup có thể đặt vào `ApplicationRunner`/`CommandLineRunner` ở Boot hoặc hook sau `refresh()` khi thực sự cần.
5. **Đảm bảo aspect/advisor/auto-proxy config được đăng ký đủ sớm trong chính container** sở hữu target, trước khi target bean được khởi tạo bình thường.
6. **Viết integration test tái hiện.** Giữ một positive control bean cùng type được tạo đúng pha và kiểm tra `AopUtils.isAopProxy`/advice events; không chỉ khẳng định method trả kết quả.

**Điều quan trọng về existing singleton:** nếu singleton đã được tạo **raw** trong context đang chạy, việc thêm một `@Aspect` hoặc `@EnableAspectJAutoProxy` sau đó không bảo đảm container thay thế object cũ bằng proxy và cập nhật mọi reference đã inject. Thường cần **chỉnh cấu hình lifecycle rồi tạo lại `ApplicationContext` trong test hoặc restart deployment** theo quy trình an toàn; không tùy tiện gọi `destroySingleton` trên bean production đang được tham chiếu.

## 10. Cách sửa khi lấy bean từ sai ApplicationContext

1. **Xác định owner:** log `ApplicationContext.getId()`/parent ID, `containsBeanDefinition`, `containsSingleton` và so reference identity.
2. **Đặt aspect và AOP infrastructure ở context sở hữu target.** Với service ở root, cấu hình AOP cho root; với bean thực sự ở servlet child, xét cấu hình child.
3. **Không tạo lại business bean cùng tên ở child chỉ để chữa AOP** nếu điều đó thay đổi singleton/state/transaction manager. Giải pháp chính là sắp xếp đúng bean configuration.
4. **Kiểm tra component-scan boundaries**: scanner của child có thể quét luôn service package trong khi root cũng quét, tạo bean trùng ở hai context; hoặc scanner không quét aspect ở nơi bạn nghĩ.
5. **Với nhiều DispatcherServlet**: mỗi servlet có thể có child context khác nhau. Aspect/bean đăng ký trong một child không tự xuất hiện trong child khác.
6. **Với Spring TestContext**: nếu test dùng `@ContextHierarchy`, `@SpringJUnitConfig` hoặc context slice khác production, kiểm tra config ở từng level và nơi bean được đăng ký.
7. **Sau khi sửa**, test cả lời gọi bean từ root và từ child; kỳ vọng cụ thể tùy container nào thực sự chứa bean và AOP config.

Đặc biệt, **`child.getBean("rootService")` không có nghĩa child sở hữu `rootService`**. Parent bean không được child post-processor bọc lại chỉ vì lookup bắt đầu từ child.

## 11. Các trường hợp cần phân biệt để không chẩn đoán nhầm

### 11.1. Bean tạo sớm vs circular dependency early proxy

Spring có thể đưa **early reference** ra ngoài để giải quyết một số vòng phụ thuộc, và auto-proxy creator có hook tạo **early proxy reference** trong hoàn cảnh được hỗ trợ. Không phải mọi "đối tượng được tạo sớm" đều là raw. Hãy dùng evidence: object cuối cùng có phải AOP proxy không, startup warning, và lúc BPP đăng ký.

**Trường hợp anti-pattern BFPP của mục 4** tạo bean trước giai đoạn BPP, nên là ví dụ có chủ đích của **premature initialization**. Nó khác circular reference được Spring xử lý có kiểm soát.

### 11.2. @PostConstruct và self-invocation

Method `@PostConstruct` thường được chạy trong initialization phase, trước khi normal AOP proxy wrapping hoàn tất. Nếu init callback gọi `this.businessMethod()`, callback đó không phải bằng chứng advice trên `businessMethod` sẽ chạy. Điều này có thể xảy ra với bean **sau đó vẫn được proxy đúng**, nên không tự kết luận "bean bị tạo quá sớm" chỉ từ việc advice không chạy **trong @PostConstruct**.

### 11.3. Spring AOP vs AspectJ weaving

`@EnableAspectJAutoProxy` bật Spring AOP auto-proxy, không có nghĩa AspectJ load-time weaving đang hoạt động. AspectJ weaving có thể advise object không phải Spring bean hoặc self-invocation, nhưng có hạ tầng và giới hạn riêng.

Nếu một object `new` bất ngờ có advice, hoặc self-invocation thực sự bị intercept trong test, kiểm tra có AspectJ weaver/javaagent hay cơ chế instrumentation khác không. Bài này chỉ mô phỏng **Spring proxy AOP**.

### 11.4. Proxy tồn tại nhưng không có custom advisor

Một bean có thể nhận proxy vì `@Transactional` hoặc một advisor khác mà **chưa hề match custom @Aspect**. Do đó:
- `AopUtils.isAopProxy(bean)=true` xác nhận proxy tồn tại, chưa chứng minh *đúng advice cần tìm* đã có.
- Kiểm tra `Advised.getAdvisors()`, `AspectJExpressionPointcut`, actual target type và method signature.
- Nếu method ném exception hoặc trả reactive publisher, phân biệt thời điểm method invocation với signal/subscription.

### 11.5. @Lazy không đảm bảo sửa mọi lỗi

Đánh dấu bean `@Lazy` có thể trì hoãn creation, nhưng nếu BFPP/BPP vẫn chủ động `getBean(...)` thì **lazy bean vẫn bị ép tạo**. `@Lazy` không thể thay thế việc loại bỏ thiết kế gọi getBean sai pha.

### 11.6. @Order không chuyển BPP sang context khác

`@Order`/`PriorityOrdered` điều khiển một số thứ tự post-processor, nhưng không biến BPP của child thành BPP của parent. BPP được **scoped per container**. Đồng thời, `@Order` trên aspect nói về advice precedence, không tự sửa **thời điểm bean đã được tạo** trước khi auto-proxy creator tham gia.

## 12. Checklist điều tra theo thứ tự ít rủi ro

~~~text
Advice không chạy trên Spring bean
   |
   +-- 1. Reference thật có phải Spring AOP proxy?
   |        ├-- Không → xác định bean lifecycle/context
   |        └-- Có    → kiểm tra advisor + pointcut + call path
   |
   +-- 2. Bean do context nào sở hữu?
   |        ├-- parent/root → AOP config có ở parent/root không?
   |        └-- child/local → AOP config có ở child/local không?
   |
   +-- 3. Bean được tạo ở pha nào?
   |        ├-- trong BFPP/BPP registration → khả năng premature init
   |        └-- sau đủ BPP → xét advisor eligibility, pointcut
   |
   +-- 4. Có warning "not eligible for all BeanPostProcessors"?
   |        ├-- Có → lần theo ai buộc getBean sớm
   |        └-- Không → chưa loại trừ các nguyên nhân khác
   |
   +-- 5. Caller có gọi qua proxy?
   |        ├-- this.method(), new Target(), raw target → bypass
   |        └-- injected Spring reference → kiểm tra actual advisor
   |
   +-- 6. Sửa cấu hình / lifecycle và chạy integration test lại
~~~

**Nguyên tắc an toàn:** kiểm tra bằng API read-only và log trước; không gọi `getBean()` hàng loạt khi context đang trong giai đoạn startup, không tự thay đổi bean factory runtime nhằm "ép tạo lại proxy".

## 13. Câu hỏi tự kiểm tra

**1. Tại sao một bean singleton lấy từ Spring vẫn có thể không phải AOP proxy?**

Vì nó có thể được tạo trước khi auto-proxy creator/BPP được đăng ký đầy đủ, hoặc context sở hữu bean không có hạ tầng/aspect cần thiết. Ngoài ra, không phải mọi bean đều match advisor.

**2. Vì sao gọi getBean() trong BeanFactoryPostProcessor nguy hiểm đối với Spring AOP?**

BFPP chạy trước giai đoạn đăng ký BPP bình thường. Ép tạo application bean lúc ấy có thể khiến bean bỏ lỡ auto-proxy processing.

**3. Root context có service, child context có @Aspect và @EnableAspectJAutoProxy. Child getBean(service) có chắc nhận proxy không?**

Không. Child lookup có thể trả bean do root sở hữu. Child BPP không post-process parent-owned bean.

**4. Bean trong child được advise nhưng bean cùng class trong parent không được advise; nên kiểm tra gì?**

Context ownership, nơi aspect và auto-proxy creator đăng ký, thời điểm bean creation, proxy type và advisor list. Không suy luận chỉ từ class type.

**5. Dùng @Lazy cho service có chữa hoàn toàn premature initialization không?**

Không. Nếu BFPP/BPP vẫn gọi `getBean()` thì lazy initialization vẫn bị phá và service được instantiate sớm.

**6. @PostConstruct gọi business method không được advice, nhưng sau startup bean là proxy; điều đó có mâu thuẫn không?**

Không. Init callback diễn ra trong initialization lifecycle trước khi proxy cuối cùng được áp dụng; lời gọi `this` nội bộ cũng bypass proxy.

**7. isAopProxy(bean)=true có chứng minh mọi custom aspect đang hoạt động không?**

Không. Bean có thể proxied vì advisor khác. Cần kiểm tra advisor và lời gọi method thật có match pointcut.

**8. Làm sao xác nhận child.getBean("service") đang trả bean của parent?**

So reference identity với `parent.getBean("service")`, cùng lúc xem bean definition/local singleton ownership trong từng context.

**9. Sửa config AOP sau khi bean raw đã khởi tạo có tự làm mọi reference thành proxy không?**

Không được bảo đảm. Cần sửa lifecycle/config, tạo lại context trong test hoặc triển khai restart an toàn để singleton mới được xử lý đúng.

## 14. Tham khảo Spring chính thức

- [Container Extension Points — BeanPostProcessor và early initialization](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html): BPP phase, cảnh báo `not eligible for auto-proxying`, processor dependencies, giới hạn theo container và BFPP anti-pattern.
- [Spring AOP @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): đăng ký aspect và cơ chế Spring AOP proxy.
- [Spring AOP Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, self-invocation và giới hạn proxy.
- [Using the Auto-Proxy Facility](https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html): auto-proxy creator và eligibility.
- [Bean Scopes and Singleton per Container](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): singleton theo từng container, không phải singleton toàn JVM.
- [AnnotationConfigApplicationContext API](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/context/annotation/AnnotationConfigApplicationContext.html): cách đăng ký bean definitions và `refresh()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-005">Ép kiểu sai do cơ chế proxy không như dự kiến</a>

<details>
<summary>Xem chi tiết</summary>

# Ép kiểu sai do cơ chế proxy không như dự kiến trong Spring AOP

## 1. Triệu chứng: Spring bean chạy được nhưng ép kiểu thất bại

Một service có thể được đăng ký bằng `@Component` hoặc `@Bean`, được Spring AOP gắn advice và sử dụng bình thường qua interface. Tuy nhiên, khi ép đối tượng lấy từ `ApplicationContext` sang lớp triển khai, ứng dụng lại gặp `ClassCastException`:

```java
BillingService service = context.getBean(BillingService.class);
service.pay("INV-1"); // Có thể chạy bình thường, advice vẫn hoạt động.

// Dòng sau có thể ném ClassCastException nếu Spring tạo JDK proxy:
BillingServiceImpl impl = (BillingServiceImpl) service;
```

Điểm gây hiểu lầm là **lớp target thực sự là `BillingServiceImpl`**, nhưng object mà Spring trả cho caller có thể là **proxy**, không phải instance mang cùng quan hệ kế thừa với target. Với JDK dynamic proxy, proxy **triển khai interface** của target; nó **không kế thừa lớp triển khai**. Vì vậy, ép kiểu xuống `BillingServiceImpl` có thể thất bại dù mục tiêu thật phía sau proxy có đúng lớp đó.

Bài này tập trung vào **chẩn đoán và khắc phục lỗi ép kiểu do Spring AOP proxy**, gồm JDK dynamic proxy, class-based (CGLIB) proxy, cấu hình `proxyTargetClass`, injection theo type, Spring Boot, các giới hạn khi chọn proxy theo lớp và cách kiểm thử. Đây là lỗi Spring AOP cụ thể, không cần ôn lý thuyết AOP hoặc AspectJ weaving tổng quát.

Sau bài, bạn sẽ phân biệt được:

- Đối tượng **target** mà Spring định tư vấn (advise).
- Đối tượng **proxy** thực tế nằm trong container.
- **Business interface** mà JDK proxy công bố.
- **Concrete implementation class** của target.
- Cấu hình nào có thể khiến Spring tạo loại proxy khác với dự đoán.

## 2. Vì sao cơ chế proxy quyết định khả năng cast?

Spring AOP dùng **proxy-based AOP**. Ở cơ chế phổ biến của Spring Framework:

| Đặc điểm | JDK dynamic proxy | Class-based proxy (CGLIB) |
| --- | --- | --- |
| Bản chất object được tiêm/trả về | Lớp proxy của JDK triển khai interface | Lớp con runtime của lớp target |
| Có thể ép sang business interface? | Có, nếu interface nằm trong danh sách proxy triển khai | Có, nếu target triển khai interface |
| Có thể ép sang implementation class? | **Không**: proxy không kế thừa implementation | **Có** đối với proxy lớp được tạo thành công |
| Spring AOP thường dùng khi nào? | Target có interface và không bị ép class proxy | Target không có interface phù hợp hoặc cấu hình `proxyTargetClass=true` |
| Có bắt được method chỉ thuộc implementation không? | Không thể gọi method đó qua reference interface nếu không thuộc interface proxy | Có thể gọi method public thích hợp qua class proxy; việc advise còn phụ thuộc pointcut và giới hạn override |
| Giới hạn nổi bật | Không thể inject/cast JDK proxy thành target implementation | Không thể subclass lớp `final`; `final`/`private` methods không thể được advise bằng cơ chế override |

Ví dụ hình dung:

```text
Interface BillingService
    ▲
    │ implements
BillingServiceImpl (target object)
    ▲
    │ extends chỉ trong trường hợp CGLIB
BillingServiceImpl$$SpringCGLIB$$... (class-based proxy)


BillingService (interface)
    ▲
    │ implements
jdk.proxy...$Proxy... (JDK dynamic proxy)
    └── chứa cơ chế gọi đến BillingServiceImpl nhưng KHÔNG extends BillingServiceImpl
```

Khi viết:

```java
BillingServiceImpl concrete = (BillingServiceImpl) proxy;
```

JVM kiểm tra **runtime type của `proxy`**, không kiểm tra kiểu của target đằng sau nó. Nếu object proxy thuộc loại JDK dynamic proxy, phép cast bị từ chối bằng `ClassCastException`.

**Quan trọng:** `AopUtils.getTargetClass(proxy)` có thể trả về `BillingServiceImpl.class` dù chính object `proxy` **không** cast được sang `BillingServiceImpl`. “Target class là gì?” và “Proxy object có phải instance của class đó không?” là **hai câu hỏi khác nhau**.

## 3. Nguồn gốc thực tế của lỗi

### 3.1. Cast thủ công xuống implementation

```java
Object bean = context.getBean("billingService");
BillingServiceImpl service = (BillingServiceImpl) bean; // Sai nếu bean là JDK proxy
```

Đây là tình huống trực tiếp nhất. Class target có tồn tại và có bean tên `billingService`, nhưng object trả về là proxy.

### 3.2. Inject implementation thay vì interface

```java
@Component
public class BillingController {
    private final BillingServiceImpl service; // Có thể không tương thích JDK proxy

    public BillingController(BillingServiceImpl service) {
        this.service = service;
    }
}
```

Khi bean đích đã được Spring AOP thay bằng JDK proxy, kiểu implementation không còn assignable từ runtime proxy. Tùy kiểu lookup/đường tạo bean và phiên bản Spring, lỗi có thể biểu hiện là `BeanNotOfRequiredTypeException`, `NoSuchBeanDefinitionException` theo type hoặc `UnsatisfiedDependencyException`/`BeanCreationException` bọc nguyên nhân. Đừng dựa vào *một* tên exception duy nhất; cần đọc nested `Caused by` và loại proxy thật.

### 3.3. Lấy bean với required type không tương thích

```java
// Minh họa lỗi, không chạy chung với ví dụ thành công phía dưới:
BillingServiceImpl concrete =
        context.getBean("billingService", BillingServiceImpl.class);
```

Với JDK proxy, cách lấy theo tên và required type có thể phát hiện bean có **runtime type** khác kiểu yêu cầu rồi ném `BeanNotOfRequiredTypeException`.

### 3.4. Môi trường khác nhau chọn loại proxy khác nhau

Code chạy tốt trên môi trường dùng class-based proxy nhưng thất bại sau khi:

- chuyển cấu hình từ `proxyTargetClass=true` sang `false`;
- chạy cùng module trong Spring Framework application có lựa chọn mặc định khác Spring Boot;
- thêm/bớt interface làm chiến lược proxy thay đổi;
- kết hợp nhiều cấu hình AOP, transaction hoặc security có flag ép class proxy;
- tách bean sang `ApplicationContext` khác có cơ chế auto-proxy khác.

Hãy đo **proxy của đúng bean ở đúng environment** trước khi kết luận.

## 4. Quy trình chẩn đoán có thể lặp lại

Bắt đầu bằng object được trả từ **Spring container**, rồi kiểm tra:

```java
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("billingService");

System.out.println("isAopProxy=" + AopUtils.isAopProxy(bean));
System.out.println("isJdkDynamicProxy=" + AopUtils.isJdkDynamicProxy(bean));
System.out.println("isCglibProxy=" + AopUtils.isCglibProxy(bean));
System.out.println("runtimeClass=" + bean.getClass().getName());
System.out.println("targetClass=" + AopUtils.getTargetClass(bean).getName());
System.out.println("isConcrete=" + (bean instanceof BillingServiceImpl));
```

Đây là **đoạn chẩn đoán đặt trong chương trình đã có `context`**, không phải file Java chạy độc lập.

| Kết quả | Ý nghĩa |
| --- | --- |
| `isAopProxy=false` | Object không được Spring AOP nhận diện là AOP proxy; kiểm tra registration, advice và điểm gọi trước khi điều tra ép kiểu |
| `isJdkDynamicProxy=true` | Object là JDK proxy; nếu không có quan hệ kế thừa implementation, không thể cast sang lớp target |
| `isCglibProxy=true` | Object là class-based proxy; kiểm tra implementation class, `final` và pointcut nếu advice không chạy |
| `getTargetClass(...) == BillingServiceImpl.class` | Spring xác định class đích; **không khẳng định** object proxy cast được sang class đó |
| `bean instanceof BillingServiceImpl` | Phép kiểm tra type runtime an toàn trước cast; `false` nghĩa là không được cast trực tiếp |

Không phụ thuộc tên runtime class cụ thể như `jdk.proxy2.$Proxy17` hay hậu tố `$$SpringCGLIB$$0`: tên có thể thay đổi theo JVM, Spring version và thứ tự tạo proxy. Các helper của `AopUtils` phù hợp hơn khi viết test.

**Không “sửa” bằng cách unwrap target để gọi trực tiếp:** có thể đi vòng qua advice cho transaction, security, logging hay caching. Nếu buộc lấy target phục vụ debug, chỉ dùng đúng công cụ AOP ở phạm vi chẩn đoán, không thay bean được inject bằng target trong logic nghiệp vụ.

## 5. Ví dụ Java 17+ chạy được: hai cấu hình, hai kiểu proxy

Ví dụ dùng **Spring Framework 6.2.14**, **AspectJ 1.9.24** và **Java 17**. Ta giữ nguyên service, aspect, bean name, chỉ đổi flag `proxyTargetClass`. Chạy cả hai context tách biệt để không bị cấu hình auto-proxy của context này làm thay đổi context kia.

### 5.1. Cấu trúc

```text
spring-proxy-cast-demo/
├── pom.xml
└── src/main/java/demo/
    ├── BillingService.java
    ├── BillingServiceImpl.java
    ├── AuditAspect.java
    ├── JdkProxyConfig.java
    ├── ClassProxyConfig.java
    └── Main.java
```

### 5.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-proxy-cast-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.14</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.14</version>
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
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Ví dụ này là **Spring AOP proxy**, không dùng AspectJ load-time weaving. `aspectjweaver` cần thiết để `@EnableAspectJAutoProxy` xử lý cú pháp pointcut `@AspectJ`.

### 5.3. `src/main/java/demo/BillingService.java`

```java
package demo;

public interface BillingService {
    String pay(String invoiceId);
}
```

### 5.4. `src/main/java/demo/BillingServiceImpl.java`

```java
package demo;

public class BillingServiceImpl implements BillingService {
    @Override
    public String pay(String invoiceId) {
        return "paid:" + invoiceId;
    }
}
```

Lớp đích **không final**, nên class-based proxy có thể tạo subclass. Interface `BillingService` cho phép JDK proxy được chọn khi không bật class-based proxy.

### 5.5. `src/main/java/demo/AuditAspect.java`

```java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* demo..*.pay(..))")
    public void audit(JoinPoint joinPoint) {
        System.out.println("audit before: " + joinPoint.getSignature().getName());
    }
}
```

Advice sẽ in dòng `audit before: pay` cho mỗi lời gọi `pay(...)` đi qua Spring AOP proxy. Pointcut dùng cho **ví dụ trong package `demo`**, không áp dụng rộng cho dự án production.

### 5.6. `src/main/java/demo/JdkProxyConfig.java`

```java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class JdkProxyConfig {
    @Bean("billingService")
    BillingService billingService() {
        return new BillingServiceImpl();
    }

    @Bean
    AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
```

`proxyTargetClass=false` cho phép Spring sử dụng JDK dynamic proxy khi target có interface thích hợp. Lưu ý cấu hình này có hiệu lực trong **ApplicationContext nơi nó được đăng ký**; các cơ chế AOP khác trong cùng context có thể thống nhất flag theo cấu hình mạnh hơn.

### 5.7. `src/main/java/demo/ClassProxyConfig.java`

```java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ClassProxyConfig {
    @Bean("billingService")
    BillingService billingService() {
        return new BillingServiceImpl();
    }

    @Bean
    AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
```

Điểm thay đổi duy nhất là `proxyTargetClass=true`: Spring yêu cầu class-based proxy, nên object được trả về là một subclass của `BillingServiceImpl` (khi tạo proxy thành công).

### 5.8. `src/main/java/demo/Main.java`

```java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        inspect("JDK proxy", JdkProxyConfig.class);
        inspect("Class proxy", ClassProxyConfig.class);
    }

    private static void inspect(String title, Class<?> config) {
        try (var context = new AnnotationConfigApplicationContext(config)) {
            Object bean = context.getBean("billingService");

            System.out.println("=== " + title + " ===");
            System.out.println("aop=" + AopUtils.isAopProxy(bean));
            System.out.println("jdk=" + AopUtils.isJdkDynamicProxy(bean));
            System.out.println("cglib=" + AopUtils.isCglibProxy(bean));
            System.out.println("target=" + AopUtils.getTargetClass(bean).getSimpleName());
            System.out.println("concrete=" + (bean instanceof BillingServiceImpl));

            // Cả hai proxy đều dùng được qua business interface.
            BillingService service = (BillingService) bean;
            System.out.println(service.pay("INV-1"));

            try {
                BillingServiceImpl impl = (BillingServiceImpl) bean;
                System.out.println("cast=OK");
                System.out.println(impl.pay("INV-2"));
            } catch (ClassCastException ex) {
                System.out.println("cast=FAILED (ClassCastException)");
            }
        }
    }
}
```

**Chạy trong thư mục project:**

```bash
mvn -q compile exec:java
```

**Stdout dự kiến** (không tính log bổ sung của Maven/Spring nếu có):

```text
=== JDK proxy ===
aop=true
jdk=true
cglib=false
target=BillingServiceImpl
concrete=false
audit before: pay
paid:INV-1
cast=FAILED (ClassCastException)
=== Class proxy ===
aop=true
jdk=false
cglib=true
target=BillingServiceImpl
concrete=true
audit before: pay
paid:INV-1
cast=OK
audit before: pay
paid:INV-2
```

Giải thích:

1. Cả hai nhánh đều có `aop=true`: có Spring AOP advice, ứng dụng **không bị mất AOP** trong nhánh JDK proxy.
2. Với JDK proxy, `concrete=false` nên ép sang `BillingServiceImpl` thất bại. `target=BillingServiceImpl` chỉ là thông tin target class do Spring xác định.
3. Với class-based proxy, `concrete=true`, nên cast thực hiện được và advice vẫn chạy trong hai lời gọi `pay()`.
4. Nếu chương trình vô tình dùng `new BillingServiceImpl().pay(...)`, sẽ không có dòng `audit before: pay`, bởi lời gọi đó không qua Spring proxy.

Trong bài này, việc bắt `ClassCastException` nhằm **minh họa nguyên nhân**, không phải cách khắc phục. Code nghiệp vụ thực tế nên chọn type phù hợp ngay từ thiết kế.

## 6. Cách sửa ưu tiên: sử dụng interface trong injection và API

Nếu controller chỉ cần hợp đồng `BillingService`, hãy khai báo phụ thuộc theo interface:

```java
// Minh họa component ở một ứng dụng Spring có BillingService bean.
import org.springframework.stereotype.Component;

@Component
public class BillingController {
    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    public String submit(String invoiceId) {
        return billingService.pay(invoiceId);
    }
}
```

Đoạn trên là **snippet tích hợp** vào project Spring, không phải một file độc lập ở cấu trúc ví dụ trước. Import `BillingService` theo package thật của ứng dụng nếu khác package.

Ưu điểm: caller không buộc phải biết Spring dùng JDK proxy hay CGLIB proxy. Cả hai đều có thể thực thi `BillingService` nếu proxy công bố interface đó. Nhờ vậy, thay đổi cấu hình logging, transaction, security, cache hay aspect khác ít gây lỗi cast cho caller.

Nếu caller cần một phương thức chỉ có trong implementation, hãy xem có phải phương thức **nghiệp vụ được phép công khai** hay không. Nếu có, cân nhắc đưa hợp đồng đó vào một interface phù hợp hoặc tách thành service riêng, thay vì buộc toàn bộ ứng dụng phải cast proxy sang implementation.

**Lưu ý:** interface injection không tự giải quyết được tình huống bean AOP không hề triển khai interface mong muốn hoặc có nhiều bean cùng interface; khi đó cần sửa hợp đồng, qualifier hoặc cấu hình bean. Đừng trộn lỗi đó với lỗi cast do proxy.

## 7. Khi thật sự cần implementation: yêu cầu class-based proxy

Trong Spring Framework thuần:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class AopConfiguration {
}
```

Đây là đoạn **cấu hình minh họa**, dùng trong đúng application context. XML tương đương:

```xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
```

Trong Spring Boot có auto-configuration Spring AOP, property thường dùng là:

```properties
spring.aop.proxy-target-class=true
```

Spring Boot auto-configuration **mặc định ưu tiên class-based proxy** khi AOP được bật; để chọn JDK proxies, đặt:

```properties
spring.aop.proxy-target-class=false
```

Tuy nhiên, đừng lấy mặc định Spring Boot áp cho **mọi** Spring Framework application. Hãy đọc cấu hình đang chạy và đo object thực tế bằng `AopUtils`.

**Rủi ro nếu ép CGLIB để sửa cast:**

- `final class` không thể được subclass để tạo class-based proxy. Cấu hình có thể thất bại khi khởi tạo bean.
- `final method`, `private method` hoặc một số method không thể override sẽ **không được Spring AOP advise** qua cơ chế subclass.
- Giới hạn Java module system, package visibility hoặc classloader có thể làm proxy creation thất bại.
- Cấu hình `proxyTargetClass=true` có thể ảnh hưởng các proxy khác trong cùng context. Spring thường hợp nhất các auto-proxy creator và chọn các thiết lập mạnh nhất từ `@EnableAspectJAutoProxy`, `@EnableTransactionManagement`, `<aop:config>`... Do đó một thay đổi chỉ để sửa một cast có thể làm loại proxy của **nhiều bean khác** thay đổi.
- CGLIB giúp assignable sang implementation khi proxy được tạo thành công, nhưng **không sửa vấn đề self-invocation**; lời gọi nội bộ qua `this` vẫn thường bỏ qua proxy.

Vì vậy, dùng interface là lựa chọn nên ưu tiên nếu hợp đồng nghiệp vụ cho phép. Chỉ ép class-based proxy sau khi đã xác nhận yêu cầu kỹ thuật thực sự và kiểm tra ảnh hưởng toàn ứng dụng.

## 8. Phân biệt các thông báo lỗi tương tự

| Hiện tượng | Cách đọc đúng | Hướng xử lý |
| --- | --- | --- |
| `ClassCastException: jdk.proxy...$Proxy... cannot be cast to BillingServiceImpl` | Runtime bean là JDK proxy không kế thừa implementation | Sử dụng interface; chỉ cân nhắc CGLIB nếu bắt buộc |
| `BeanNotOfRequiredTypeException` khi `getBean("billingService", BillingServiceImpl.class)` | Bean tồn tại, nhưng object trả về không assignable sang implementation | Kiểm tra loại proxy và sửa required type |
| `UnsatisfiedDependencyException` của bean nhận constructor parameter implementation | Không inject được dependency type mong muốn; nguyên nhân bên trong có thể là JDK proxy | Đọc chuỗi `Caused by`, kiểm tra bean name/type/proxy |
| `NoSuchBeanDefinitionException` khi tìm theo implementation class | Container không tìm được bean assignable với class yêu cầu | Kiểm tra đăng ký bean và runtime proxy type; dùng interface nếu phù hợp |
| `IllegalArgumentException: Cannot subclass final class` hoặc lỗi tương tự từ CGLIB | Đã yêu cầu class proxy cho lớp không cho phép subclass | Loại bỏ final nếu an toàn, hoặc chuyển sang interface/JDK proxy |
| Advice không chạy dù cast thành công | Type cast chỉ xác minh type; không chứng minh pointcut/advice khớp | Kiểm tra `AopUtils`, advisor, lời gọi có đi qua proxy hay không |
| Chỉ có lỗi trên production | Classpath, feature flags, Boot AOP property, cấu hình transaction/security khác môi trường | In proxy diagnostic trong đúng runtime và so cấu hình |

Một ví dụ exception thường gặp (hình thức minh họa; tên package/index proxy thay đổi theo runtime):

```text
java.lang.ClassCastException:
  class jdk.proxy2.$Proxy15 cannot be cast to class demo.BillingServiceImpl
```

Không nên xử lý lỗi này bằng các phép cast liên tiếp, `Object` hoặc `@SuppressWarnings`. Kiểm tra type runtime và hợp đồng API để sửa nơi yêu cầu type không tương thích.

## 9. Unit/integration test tránh lỗi quay lại

Sử dụng test chạy trên **Spring context thật** và gọi bean sau post-processing. Ví dụ dưới đây dùng JUnit 5, là **file thử nghiệm bổ sung** nếu thêm dependency JUnit vào project ví dụ:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.11.4</version>
    <scope>test</scope>
</dependency>
```

Thêm Maven Surefire plugin nếu project chưa có runner JUnit 5 phù hợp:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.2</version>
</plugin>
```

**`src/test/java/demo/ProxyCastingTest.java`:**

```java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class ProxyCastingTest {
    @Test
    void jdkProxySupportsInterfaceButNotConcreteCast() {
        try (var ctx = new AnnotationConfigApplicationContext(JdkProxyConfig.class)) {
            Object bean = ctx.getBean("billingService");

            assertTrue(AopUtils.isAopProxy(bean));
            assertTrue(AopUtils.isJdkDynamicProxy(bean));
            assertInstanceOf(BillingService.class, bean);
            assertFalse(bean instanceof BillingServiceImpl);
            assertEquals("paid:TEST", ((BillingService) bean).pay("TEST"));
            assertThrows(ClassCastException.class,
                    () -> { BillingServiceImpl impl = (BillingServiceImpl) bean; });
        }
    }

    @Test
    void classProxySupportsConcreteCast() {
        try (var ctx = new AnnotationConfigApplicationContext(ClassProxyConfig.class)) {
            Object bean = ctx.getBean("billingService");

            assertTrue(AopUtils.isAopProxy(bean));
            assertTrue(AopUtils.isCglibProxy(bean));
            assertInstanceOf(BillingServiceImpl.class, bean);
            assertEquals("paid:TEST", ((BillingServiceImpl) bean).pay("TEST"));
        }
    }
}
```

Hai phép thử xác nhận **hình dạng proxy và contract**. Muốn kiểm tra thêm rằng advice thực sự chạy, hãy dùng một test aspect có counter được inject hoặc một spy thích hợp rồi assert số invocation; chỉ kiểm tra `isAopProxy` **không chứng minh một advice cụ thể đã chạy**.

**Lệnh kiểm thử:** sau khi thêm JUnit dependency và Surefire plugin vào `pom.xml`:

```bash
mvn test
```

Khi code đúng và dependencies khả dụng, hai test dự kiến pass; nếu chạy với log Maven đầy đủ, báo cáo cuối có **2 tests, 0 failures, 0 errors**.

## 10. Một số tình huống nâng cao khi debug Spring Aspect

### 10.1. Lỗi ép kiểu ở method nhận implementation

```java
void process(BillingServiceImpl impl) {
    impl.pay("INV-3");
}
```

Nếu truyền JDK proxy vào method đó, nó không assignable cho parameter. Nên đổi API nhận `BillingService` nếu method chỉ cần hợp đồng interface. Khi đổi contract, rà soát cả constructor injection, setter, factory method return type và collection type (`List<BillingServiceImpl>` không thể chứa JDK proxies theo ý định).

### 10.2. `@Bean` khai báo concrete return type chưa bảo đảm runtime cast

Một factory method trả `BillingServiceImpl` giúp mô tả kiểu target lúc tạo bean, nhưng sau AOP post-processing, Spring có thể thay object trả ra bằng **JDK proxy**. Khi kiểm tra cast, phải xem **runtime object**, không chỉ method return type hoặc annotation.

### 10.3. Aspect chạy trong đúng ApplicationContext

`@EnableAspectJAutoProxy` có hiệu lực trong application context nơi nó được khai báo. Trong ứng dụng nhiều context (root/child, web dispatcher contexts), một bean có thể không bị xử lý AOP bởi cấu hình nằm ở context khác. Nếu `AopUtils.isAopProxy(bean)` bất ngờ là `false`, kiểm tra context đang sở hữu bean và cấu hình aspect tương ứng trước khi đổi loại proxy.

### 10.4. AspectJ weaving là cơ chế khác

Nếu ứng dụng thực sự dùng AspectJ compile-time/load-time weaving, bytecode target bị weave; việc chọn JDK dynamic proxy hoặc CGLIB ở **Spring proxy AOP** không mô tả tất cả lời gọi trong hệ thống. Bài này xét đối tượng Spring AOP trả về; khi có cả weaving và Spring proxy, cần chẩn đoán từng lớp cơ chế riêng.

### 10.5. Không suy luận “CGLIB” từ một mình tên class

Spring thường tạo proxy có tên lớp chứa `$$SpringCGLIB$$`, còn JDK proxy có tên `jdk.proxy...`. Đây chỉ là dấu hiệu chẩn đoán bằng mắt, không phải API ổn định. Ưu tiên `AopUtils.isJdkDynamicProxy`, `AopUtils.isCglibProxy`, `AopUtils.getTargetClass` và kiểm tra `instanceof`.

## 11. Quy trình xử lý một lỗi production

Khi gặp cast/injection exception liên quan AOP:

1. **Đọc stack trace gốc và nested cause**: tại dòng nào yêu cầu implementation, cast thủ công hay constructor injection?
2. **Xác định bean đúng**: tên bean, context sở hữu, interface nào service công bố, advice nào có thể kích hoạt AOP.
3. **Kiểm tra object sau Spring post-processing** bằng `AopUtils` và `instanceof`, không dựa vào kiểu đã khai báo trong `@Bean` hoặc IDE.
4. **Xác minh cấu hình proxy**: Spring Framework hay Spring Boot, `proxyTargetClass`, `spring.aop.proxy-target-class`, transaction/security/AOP configuration có thể ảnh hưởng nhau.
5. **Sửa dependency type về interface** nếu business contract cho phép; nếu buộc dùng concrete, đánh giá class-based proxy cùng các giới hạn `final`/module/access.
6. **Kiểm thử lại qua ApplicationContext**: xác nhận gọi được method nghiệp vụ, advice chạy và bean inject được trong cấu hình thật.
7. **Regression test hai chiến lược proxy** nếu thư viện/mô-đun phải dùng được cả Spring Framework app và Spring Boot app.

**Tư duy chẩn đoán:** một bean tên `billingService` và một target class `BillingServiceImpl` không quyết định runtime object có cast được sang `BillingServiceImpl` hay không. Quyết định nằm ở **class/interface mà proxy thực sự công bố**. Giữ caller phụ thuộc vào interface giúp tránh loại lỗi này và vẫn bảo toàn lời gọi qua Spring AOP proxy.

## 12. Tài liệu tham khảo của Spring

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK proxy, CGLIB proxy, `final`, self-invocation và cấu hình proxy.
- [Spring Framework 6.2 — AOP Proxies](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-proxies.html): mặc định proxy theo interface và khi cần proxy theo class.
- [Spring Framework — `@EnableAspectJAutoProxy` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/annotation/EnableAspectJAutoProxy.html): các thuộc tính `proxyTargetClass`, `exposeProxy`.
- [Spring Framework — `AopUtils` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/AopUtils.html): helper nhận diện proxy và target class.
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html): auto-configuration và property `spring.aop.proxy-target-class`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-006">final/private hoặc class loader làm proxy không hoạt động</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán Spring AOP khi final/private hoặc ClassLoader làm proxy không hoạt động

## 1. Hiểu đúng triệu chứng trước khi sửa

Trong Spring AOP, advice có thể không chạy dù class đã có `@Aspect`, pointcut có vẻ hợp lệ và ứng dụng vẫn khởi động. Hai nhóm nguyên nhân cần phân biệt:

- **Giới hạn của proxy:** JDK dynamic proxy chỉ phơi bày các method của interface được proxy; class-based proxy (CGLIB) dựa trên subclass nên không thể override method `final` hoặc `private`, và không thể subclass một class `final`. Ngoài ra, lời gọi tự thân (`this.method()`) không đi qua proxy.
- **ClassLoader và khả năng nhìn thấy kiểu:** môi trường có nhiều loader có thể khiến interface/target/aspect không nhìn thấy nhau, làm proxy không tạo được, không inject được hoặc cast thất bại. Trong một số tình huống, người quan sát gọi nhầm **target object** hay bean của context khác rồi kết luận AOP không hoạt động.

Mục tiêu chẩn đoán là trả lời theo thứ tự: **Có Spring bean phù hợp? Có advisor phù hợp? Có proxy? Proxy loại nào? Reference lúc gọi có thật sự là proxy? Method có thể được intercept theo cơ chế ấy? Interface và target có được nạp bằng ClassLoader tương thích?**

Bài tập trung vào Spring AOP và cách Spring tạo proxy; không giảng lại lý thuyết AspectJ nói chung. Nếu phải dùng AspectJ weaving thì đó là quyết định riêng, chỉ nên cân nhắc sau khi chứng minh giới hạn Spring proxy thật sự không thể đáp ứng thiết kế.

### Dấu hiệu thường gặp

| Dấu hiệu | Giả thuyết cần kiểm chứng | Bằng chứng |
| --- | --- | --- |
| `@Before` không chạy với method `final` | Class-based proxy không override được method | `AopUtils.isCglibProxy(bean)`; class/method modifiers |
| `@Before` vẫn chạy với implementation method `final` | Bean được gọi qua **JDK interface proxy** | `AopUtils.isJdkDynamicProxy(bean)`; method có trong interface |
| `@Before` không chạy cho method `private` | Private method không thuộc đường gọi intercept qua Spring proxy | Method modifier và caller; so sánh với public proxy method |
| Không tạo được proxy, ứng dụng fail lúc tạo bean | Target class `final` nhưng bị buộc CGLIB, hoặc cấu hình/classloader không tương thích | Exception gốc, proxyTargetClass, loader của target |
| Bean proxy không inject theo concrete class | JDK dynamic proxy chỉ là implementation của các interface proxy, không phải subclass của target | Kiểu bean từ `context.getBean(...)` và `AopUtils.isJdkDynamicProxy` |
| `ClassCastException` dù tên class trông giống nhau | Cùng tên nhị phân nhưng hai ClassLoader khác nhau tạo hai `Class` không tương thích | So sánh `Class<?>` identity và defining ClassLoader |
| `NoClassDefFoundError` / lỗi proxy interface không visible | Loader tạo proxy không nhìn thấy interface/dependency cần thiết | Root cause, classpath/module path và loader thực |
| Một lời gọi được advice, lời gọi khác không | Một reference là proxy; reference khác là raw target hoặc đi qua `this` | Đối chiếu đường gọi và `AopUtils.isAopProxy` |

**Cần tránh kết luận sai:** “method `final` luôn không có advice” là phát biểu quá rộng. Một **JDK proxy** intercept lời gọi đến method interface, sau đó mới invoke method implementation, dù implementation method được khai báo `final`. Điều kiện quan trọng là **cách gọi phải đi qua proxy của interface**. Ngược lại, **CGLIB** cần override method target nên `final` làm mất điểm intercept đó.

## 2. JDK dynamic proxy và CGLIB: khác biệt quyết định

Spring chọn loại proxy theo target, interface và cấu hình AOP hiện hành.

| Khía cạnh | JDK dynamic proxy | Class-based proxy (CGLIB) |
| --- | --- | --- |
| Cơ chế | Proxy implements interface; method interface được dispatch qua invocation handler | Tạo subclass của target class và override method có thể override |
| Target có interface | Thường được dùng khi `proxyTargetClass=false` | Dùng khi `proxyTargetClass=true` hoặc có cấu hình ép class proxy |
| Target không có interface | Không đại diện đầy đủ API class chỉ bằng JDK interface proxy | Spring thường phải dùng class-based proxy |
| Implementation class `final` | **Có thể** proxy qua interface, miễn các điều kiện khác phù hợp | Không subclass được class `final` |
| Implementation method `final` | **Có thể** advice lời gọi qua method interface | Không intercept bằng override method `final` |
| Method `private` | Không có trong public interface proxy | Không override được, không advice bằng Spring proxy |
| Method chỉ nằm ở implementation, không nằm trên interface | Không gọi được qua JDK proxy dưới kiểu interface | Có thể advice nếu method và class cho phép subclass/override |
| Injection bằng concrete implementation type | JDK proxy thường không assignable về implementation class | Proxy subclass thường assignable về target class |
| Giới hạn Java module / loader | Interface cần visible và tuân theo proxy rules của JDK | Cần loader/module cho phép định nghĩa subclass và truy cập cần thiết |

Cấu hình Java:

~~~java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
class JdkProxyConfiguration {
    // Aspect bean và service bean sẽ được đăng ký ở đây.
}
~~~

Hoặc yêu cầu class-based proxy:

~~~java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class CglibProxyConfiguration {
}
~~~

Các đoạn trên là **mảnh cấu hình minh họa**, không phải chương trình độc lập. XML tương ứng khi dùng annotation-style aspect:

~~~xml
<aop:aspectj-autoproxy proxy-target-class="false"/>
<aop:aspectj-autoproxy proxy-target-class="true"/>
~~~

Đây là **hai lựa chọn thay thế nhau**, không đặt đồng thời hai tag đó vào cùng cấu hình chỉ để thử. Với Spring Boot có AOP starter, thuộc tính `spring.aop.proxy-target-class` và các hạ tầng AOP/transaction khác có thể ảnh hưởng lựa chọn cuối cùng; nhiều cấu hình auto-proxy trong cùng context có thể hợp nhất theo tùy chọn mạnh nhất. **Quan sát proxy thực tế**, thay vì mặc định loại proxy từ một annotation lẻ.

## 3. Test runnable: final implementation method có advice qua JDK proxy, nhưng final/private không qua CGLIB

Ví dụ này dùng Spring Framework trong Spring Boot dependency management, **Java 21, Gradle và JUnit 5**. Nó chạy hai Spring `ApplicationContext` tách biệt: một context chỉ dùng JDK proxy, một context ép CGLIB. Aspect/pointcut được Spring quản lý thật; test dựa trên số lần advice nhận được.

### 3.1. Cấu trúc dự án

~~~text
spring-aop-proxy-limits/
├── settings.gradle
├── build.gradle
└── src/test/java/com/example/limits/ProxyLimitsTest.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-aop-proxy-limits'
~~~

`build.gradle`:

~~~groovy
plugins {
    id 'java'
}

group = 'com.example'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation platform('org.springframework.boot:spring-boot-dependencies:3.3.5')
    testImplementation 'org.springframework.boot:spring-boot-starter-aop'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
~~~

### 3.2. Test và fixtures trong một file

Lưu nguyên văn nội dung sau vào `src/test/java/com/example/limits/ProxyLimitsTest.java`. Các class/package-private type bên dưới thuộc cùng file, nên chỉ `ProxyLimitsTest` có modifier `public`.

~~~java
package com.example.limits;

import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyLimitsTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class JdkConfig {
        @Bean
        FinalPort finalPort() {
            return new FinalMethodTarget();
        }

        @Bean
        Probe probe() {
            return new Probe();
        }

        @Bean
        LimitsAspect limitsAspect(Probe probe) {
            return new LimitsAspect(probe);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CglibConfig {
        @Bean
        ConcreteService concreteService() {
            return new ConcreteService();
        }

        @Bean
        Probe probe() {
            return new Probe();
        }

        @Bean
        LimitsAspect limitsAspect(Probe probe) {
            return new LimitsAspect(probe);
        }
    }

    @Test
    void jdkProxyInterceptsFinalImplementationMethod() {
        try (var context =
                new AnnotationConfigApplicationContext(JdkConfig.class)) {

            FinalPort service = context.getBean(FinalPort.class);
            Probe probe = context.getBean(Probe.class);

            assertTrue(AopUtils.isJdkDynamicProxy(service));
            assertFalse(service instanceof FinalMethodTarget);

            assertEquals("FINAL_OK", service.execute());
            assertEquals(1, probe.count());
        }
    }

    @Test
    void cglibCannotInterceptFinalOrPrivateMethods() {
        try (var context =
                new AnnotationConfigApplicationContext(CglibConfig.class)) {

            ConcreteService service =
                context.getBean(ConcreteService.class);
            Probe probe = context.getBean(Probe.class);

            assertTrue(AopUtils.isCglibProxy(service));

            assertEquals("OPEN", service.open());
            assertEquals(1, probe.count());

            assertEquals("LOCKED", service.locked());
            assertEquals(1, probe.count());

            assertEquals("HIDDEN", service.callHidden());
            assertEquals(1, probe.count());
        }
    }
}

interface FinalPort {
    String execute();
}

class FinalMethodTarget implements FinalPort {
    @Override
    public final String execute() {
        return "FINAL_OK";
    }
}

class ConcreteService {
    public String open() {
        return "OPEN";
    }

    public final String locked() {
        return "LOCKED";
    }

    public String callHidden() {
        return hidden();
    }

    private String hidden() {
        return "HIDDEN";
    }
}

class Probe {
    private final AtomicInteger adviceCount = new AtomicInteger();

    void hit() {
        adviceCount.incrementAndGet();
    }

    int count() {
        return adviceCount.get();
    }
}

@Aspect
class LimitsAspect {
    private final Probe probe;

    LimitsAspect(Probe probe) {
        this.probe = probe;
    }

    @Before(
        "execution(* com.example.limits.FinalPort.execute(..))"
        + " || execution(* com.example.limits.ConcreteService.open(..))"
        + " || execution(* com.example.limits.ConcreteService.locked(..))"
        + " || execution(* com.example.limits.ConcreteService.hidden(..))"
    )
    public void beforeTarget() {
        probe.hit();
    }
}
~~~

Chạy từ thư mục chứa dự án. Nếu có Gradle wrapper, thay `gradle` bằng `./gradlew` (Windows có thể dùng `.\gradlew.bat`):

~~~bash
gradle test --tests com.example.limits.ProxyLimitsTest
~~~

**Kỳ vọng kiểm thử:** 2 JUnit tests pass với cấu hình và dependency phù hợp. Gradle có thể tải dependency lần đầu, nhưng bản thân thí nghiệm không cần database hoặc Spring Boot web server. Đây là mã hướng dẫn cần được **chạy trên môi trường của người học**; không suy diễn một stdout cố định của Gradle.

Đọc kết quả:

| Context | Lần gọi | Advice count sau lời gọi | Lý do |
| --- | --- | --- | --- |
| JDK `FinalPort` | `service.execute()` | 1 | Interface method đi qua invocation handler dù implementation method `final` |
| CGLIB `ConcreteService` | `service.open()` | 1 | Public method override được |
| CGLIB `ConcreteService` | `service.locked()` | Vẫn 1 | Method `final` không thể override |
| CGLIB `ConcreteService` | `service.callHidden()` | Vẫn 1 | Public wrapper không nằm trong pointcut; private `hidden()` không intercept qua proxy |

**Lưu ý:** CGLIB có thể in cảnh báo rằng method final không thể được proxy. Cảnh báo đó là tín hiệu hỗ trợ chẩn đoán, không thay thế assertions. Trong ví dụ, `locked()` chỉ trả constant nên lời gọi trên proxy minh họa hạn chế advice mà không dựa vào state target bên trong method final.

### 3.3. Vì sao hai phép thử này không mâu thuẫn?

JDK proxy chặn invocation tại **method của interface**. Modifier `final` trên method implementation không cấm Java proxy gọi implementation bên trong invocation handler. Tuy vậy, một lời gọi nội bộ `this.execute()` bên trong target vẫn bypass Spring proxy.

CGLIB tạo **subclass** của `ConcreteService`; method `open()` override được nên Spring chèn interceptor. Method `locked()` không override được, còn `hidden()` là private method chỉ có thể được gọi từ bytecode target. Spring proxy không có điểm intercept ở hai method này.

Khi dự án đổi `proxyTargetClass` hoặc một cấu hình Spring Boot vô tình buộc class-based proxy, một method trước đây được advice qua JDK interface proxy có thể mất advice nếu nó là implementation method `final`. Luôn thêm regression test cho đường gọi thật trước khi thay kiểu proxy.

## 4. Vì sao final class có thể làm ứng dụng không khởi động?

Ví dụ:

~~~java
public final class ReceiptService {
    public void issue() {
        // Business logic
    }
}
~~~

Nếu bean `ReceiptService` cần một advice và Spring buộc **CGLIB subclass proxy**, Spring không thể tạo subclass vì class `final`. Lỗi khởi tạo bean/proxy thường báo không tạo được proxy, không thể subclass final class hoặc không thể khởi tạo proxy type; exception bao bên ngoài phụ thuộc Spring version và đường cấu hình.

Có hai hướng giải quyết thiết kế:

1. **Có interface phù hợp:** expose service qua interface và dùng JDK proxy (`proxyTargetClass=false`), inject theo interface. Khi target class `final` nhưng method được gọi trên interface của proxy, class implementation không cần bị subclass.
2. **Cần class-based proxy:** bỏ `final` nếu có quyền thay đổi thiết kế và đảm bảo method cần advice có thể override. Nếu không được sửa contract/object model, xem xét cách đặt AOP boundary trên bean khác, hoặc đánh giá weaving riêng với test đầy đủ.

Không nên xóa `final` chỉ để “hết lỗi” khi modifier đó bảo vệ một invariant quan trọng; trước hết cần kiểm tra aspect đang áp dụng cho bean đó có thật sự cần thiết không.

Ngoài `final`, method package-private trong superclass thuộc **package khác** có thể không visible với subclass proxy và về thực chất không thể override ở vị trí proxy. Với JPMS/module-path, khả năng định nghĩa subclass/truy cập reflection cũng có thể bị giới hạn; đọc root cause và cân nhắc cách packaging phù hợp thay vì mở toàn bộ module một cách tùy tiện.

## 5. Method private, self-invocation và proxy reference

Xét bean Spring sau:

~~~java
@Service
public class InvoiceService {
    public void pay() {
        validate();
        persist();
    }

    private void validate() {
        // Không advice được qua Spring AOP proxy.
    }

    public void persist() {
        // Public, nhưng gọi từ pay() qua this, nên bypass proxy.
    }
}
~~~

Đây là **mã minh họa**, cần import `org.springframework.stereotype.Service` và hạ tầng ứng dụng cụ thể. Nó cho thấy ba tình huống:

- `pay()`: khi được gọi **từ bean khác thông qua Spring proxy**, advice khớp `pay` có thể chạy.
- `validate()`: private method không override, không thể được intercept bằng Spring AOP proxy.
- `persist()`: public method có thể advice khi gọi **qua Spring proxy từ bên ngoài**; lời gọi nội bộ trực tiếp `this.persist()` trong `pay()` không đi qua proxy, nên advice dành riêng cho `persist` không chạy.

Việc bật `proxyTargetClass=true` **không giải quyết self-invocation**. Giải pháp dễ kiểm soát là tách hành vi cần advice sang một Spring bean khác, dùng dependency injection, rồi gọi phương thức public của bean đó qua proxy. Khi liên quan `@Transactional`, đây còn là vấn đề transaction boundary chứ không chỉ ghi log.

Nếu dùng `@Aspect` mà quên đăng ký aspect thành Spring bean hoặc quên kích hoạt auto-proxy, mọi ví dụ đều không chạy advice. `@Aspect` đơn độc chỉ đánh dấu class; một bean `@Aspect` trong context có hạ tầng AOP mới có tác dụng. Tránh kết luận final/private là nguyên nhân trước khi kiểm tra điều kiện cơ bản này.

## 6. ClassLoader: ba kiểu lỗi rất dễ nhầm với lỗi pointcut

### 6.1. Cùng tên class không đồng nghĩa cùng kiểu Java

Định danh lớp tại runtime bao gồm **tên nhị phân và defining ClassLoader**. Hai loader khác nhau có thể định nghĩa hai class đều tên `com.example.payments.PaymentPort`; dù source code nhìn giống hệt, JVM vẫn xem đó là hai kiểu khác nhau.

Khi cấu hình có nhiều application/plugin/classloader:

~~~text
Parent loader
  ├── Spring libraries, shared API (có thể)
  ├── Child loader A: PaymentPort, PaymentService (application A)
  └── Child loader B: PaymentPort, PaymentService (application B)
~~~

Nếu A và B mỗi bên nạp một bản `PaymentPort` bằng loader riêng, interface mà JDK proxy A implement **không thể ép kiểu** sang interface `PaymentPort` do loader B định nghĩa. Dấu hiệu có thể là:

- `ClassCastException` dù hai tên lớp in ra giống hệt;
- Không inject được bean theo type nếu config/caller dùng hai bản `Class<?>`;
- JDK proxy creation thất bại vì interface không visible với proxy ClassLoader đã chọn;
- `LinkageError` khi quan hệ loader và chữ ký type không nhất quán.

Đây là các khả năng cần xác minh, **không phải mọi ClassCastException đều do ClassLoader**. Xem stack trace đầy đủ, module và ClassLoader trong message khi JVM cung cấp.

### 6.2. Proxy loader nhìn thấy interface nhưng không nhìn thấy đủ dependency

JDK proxy phải định nghĩa được lớp proxy implements các interface của target; các interface đó phải nhìn thấy theo quy tắc Java dynamic proxy. Trong môi trường Spring Boot executable JAR, app server, WAR, plugin, OSGi-like host hoặc custom loader, việc đặt interface ở child loader nhưng chọn loader của parent để tạo proxy có thể sai.

Với CGLIB, Spring cần định nghĩa subclass tương thích với loader của target và tuân thủ package/module access. Nếu dependency chỉ thấy ở một loader khác hoặc Java module không cho phép truy cập cần thiết, việc tạo subclass cũng có thể thất bại.

Nguyên tắc xử lý:

1. Xác định ai tạo `ApplicationContext` và ClassLoader nào đang được dùng cho bean factory.
2. Xác định **defining loader của target class**, của interface và của aspect.
3. Phân biệt thread context ClassLoader (**TCCL**) với defining loader; chúng có thể khác nhau.
4. Kiểm tra package/module visibility, dependency duplicates và thứ tự delegation của loader.
5. Đảm bảo interface/API được chia sẻ qua loader chung khi nhiều module cần giao tiếp bằng cùng type; tránh đóng gói lặp bản API vào nhiều child loader khi phải cast qua ranh giới.

### 6.3. Khác ClassLoader với lỗi LTW

**Spring AOP proxy** được tạo bởi Spring khi quản lý bean; không cần AspectJ bytecode weaving. **AspectJ Load-Time Weaving (LTW)** can thiệp bytecode lúc lớp được load và có quy tắc ClassLoader/thời điểm riêng. Nếu log cho thấy agent LTW không weave vào một class, đó là đường chẩn đoán khác; đừng bật `-javaagent` để sửa một lỗi JDK proxy không thấy interface khi chưa xác định nguyên nhân.

Việc `TargetService` đã được JVM load **không mặc nhiên khiến Spring AOP proxy không thể hoạt động**. Spring AOP có thể tạo proxy quanh bean sau khi lớp target đã load. Điều kiện “transformer có trước khi lớp được định nghĩa” thuộc về LTW, không phải điều kiện phổ quát của Spring proxy.

## 7. Bộ công cụ chẩn đoán trực tiếp trên Spring bean

Khi xảy ra lỗi, lấy object từ **chính ApplicationContext đang phục vụ lời gọi**, rồi quan sát:

~~~java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("paymentService");

System.out.println("bean type: " + bean.getClass().getName());
System.out.println("is AOP proxy: " + AopUtils.isAopProxy(bean));
System.out.println("is JDK proxy: " + AopUtils.isJdkDynamicProxy(bean));
System.out.println("is CGLIB proxy: " + AopUtils.isCglibProxy(bean));

Class<?> targetType = AopUtils.getTargetClass(bean);
System.out.println("target type: " + targetType.getName());
System.out.println("proxy loader: " + bean.getClass().getClassLoader());
System.out.println("target loader: " + targetType.getClassLoader());
System.out.println("TCCL: " + Thread.currentThread().getContextClassLoader());

if (bean instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println("advisor: " + advisor.getClass().getName());
    }
}
~~~

Đây là **đoạn gắn vào test/debug có biến `context`**, không phải ứng dụng độc lập; cần xác định bean name từ project thật. Output loader phụ thuộc runtime nên **không thể dùng một chuỗi tên loader cố định** làm assertion đáng tin.

Diễn giải:

- `isAopProxy=false`: cần kiểm tra bean đã tạo bởi Spring chưa, có AOP starter/`@EnableAspectJAutoProxy` chưa, `@Aspect` là bean chưa và có advisor thực sự phù hợp không. Không suy ra ngay method final/private.
- `isJdkDynamicProxy=true`: inject/cast nên dựa trên interface được proxy. Nếu method không nằm trên interface, không thể gọi nó qua interface proxy như một method proxy thông thường.
- `isCglibProxy=true`: kiểm tra `final`/`private`, khả năng subclass, visibility và module access.
- `advised.getAdvisors()` hỗ trợ xác định advisor đã attach; bản thân việc có advisor **không chứng minh** một method cụ thể sẽ khớp pointcut.
- `AopUtils.getTargetClass(bean)` cho biết target type do Spring AOP biết; đây không phải lệnh tự động mở/unpack mọi nested proxy. Nếu target là proxy khác hoặc `TargetSource` tùy biến, cần kiểm tra thêm.

### Kiểm tra identity của interface qua ClassLoader

Nếu nghi ngờ có cùng tên type nhưng khác loader, đối chiếu các `Class<?>`:

~~~java
Class<?> apiExpected = com.example.payments.PaymentPort.class;
Class<?> apiFromBeanLoader = Class.forName(
        apiExpected.getName(), false, bean.getClass().getClassLoader());

System.out.println("same Class object: "
        + (apiExpected == apiFromBeanLoader));
System.out.println("expected loader: "
        + apiExpected.getClassLoader());
System.out.println("resolved loader: "
        + apiFromBeanLoader.getClassLoader());
~~~

Đây cũng là **đoạn chẩn đoán**, cần class `PaymentPort` có thật trong dự án, biến `bean` từ Spring context và xử lý `ClassNotFoundException` hoặc khai báo `throws Exception`. Một kết quả `false` cho thấy hai object `Class` khác nhau; nếu `Class.forName` thất bại thì loader đang kiểm tra không resolve được tên kiểu ấy trong phạm vi tương ứng.

Lưu ý: `Class.forName(..., false, ...)` yêu cầu loader resolve class, nên là phép kiểm tra **sự nhìn thấy type** chứ không phải cách đo lại toàn bộ lịch sử class loading. Tránh dùng reflexive test này ở hot path production.

### Kiểm tra proxy reference với cùng một bean

~~~java
Object fromContext = context.getBean("paymentService");
System.out.println(AopUtils.isAopProxy(fromContext));

Object raw = new com.example.payments.PaymentService();
System.out.println(AopUtils.isAopProxy(raw));
~~~

Đoạn minh họa giả định `PaymentService` có constructor không tham số. Object được tạo bằng `new` không tự có AOP proxy chỉ vì class có `@Service`; việc gọi method trên `raw` không đo được advice của Spring-managed bean. Cũng phải chú ý khi debug `this` bên trong target vì `this` ở đó là target, không nhất thiết là proxy bên ngoài.

## 8. Quy trình khoanh vùng nguyên nhân theo thứ tự

### Bước 1: kiểm chứng đúng bean/context

Từ điểm gọi bị lỗi, log bean name, active profile, `ApplicationContext` và object reference thực. Nếu hệ thống có root/child context, bean cùng tên hoặc module plugin, phải xác định context sở hữu bean và config AOP được kích hoạt ở context nào. `@EnableAspectJAutoProxy` áp dụng cho context mà nó được khai báo; có thể cần cấu hình riêng ở child context.

### Bước 2: kiểm chứng AOP đã được kích hoạt

Đảm bảo dependency AOP phù hợp, Spring tự động tạo proxy và aspect là Spring bean. Ở Java config có `@EnableAspectJAutoProxy`; trong Spring Boot có `spring-boot-starter-aop` cùng auto-config tương ứng; trong XML có `<aop:aspectj-autoproxy/>` hoặc advisor được cấu hình qua namespace AOP. Chạy **một positive control** trên public method dễ advice để chứng minh hạ tầng hoạt động.

### Bước 3: xác nhận proxy type thay vì đoán

Dùng `AopUtils.isJdkDynamicProxy`, `AopUtils.isCglibProxy` và kiểm tra `proxyTargetClass`/Boot property/auto-proxy creator hợp nhất. Đối chiếu interface được proxy, return type của `@Bean` và cách inject vào caller.

### Bước 4: đối chiếu modifiers và đường gọi

Kiểm tra `Modifier.isFinal(method.getModifiers())`, `Modifier.isPrivate(method.getModifiers())` và `Modifier.isFinal(targetClass.getModifiers())`. Với JDK proxy, kiểm tra lời gọi có qua interface method hay không. Với CGLIB, kiểm tra method có override được và có bị Java module visibility cản trở không. Sau đó vẽ ngắn đường gọi `controller -> proxy -> target.method -> this.anotherMethod` để tìm chỗ bypass.

### Bước 5: đối chiếu ClassLoader và root cause

In defining loader của interface, target, proxy và TCCL; đọc stack trace nguyên gốc, không chỉ exception wrapper của `BeanCreationException`. Nếu class loader khác nhau, xác nhận thực tế có **hai định nghĩa của cùng binary name** hay chỉ có hai loader tham gia delegation về cùng parent class.

### Bước 6: chạy ma trận hồi quy

Viết test tích hợp trong Spring context thật cho cả:
- Method public, nonfinal gọi qua proxy: **advice phải chạy**.
- Method final qua CGLIB: **advice không chạy**.
- Method final implementation gọi qua JDK interface proxy: **advice có thể chạy**; cần test cụ thể.
- Method private và `this` self-invocation: **không được xem là intercept bởi Spring proxy**.
- Trường hợp nhiều loader: injection/cast phải thành công khi interface dùng chung đúng; negative control cho class identity khác loader khi dự án thực sự có cấu hình đó.

## 9. Các cách sửa đúng nguyên nhân

| Nguyên nhân đã được chứng minh | Cách sửa ưu tiên | Rủi ro khi sửa sai |
| --- | --- | --- |
| CGLIB target class `final` | Giữ interface và dùng JDK proxy nếu hợp đồng cho phép; hoặc đổi thiết kế lớp | Chuyển proxy type có thể làm code inject bằng concrete type hỏng |
| CGLIB method `final` cần advice | Dời boundary ra public method có thể override; hoặc expose hành vi qua interface và thử JDK proxy | Bỏ `final` không kiểm thử có thể phá invariant |
| Method `private` cần advice | Đưa thao tác cần advice vào public method của bean khác qua Spring proxy | Đổi visibility một cách cơ học có thể lộ API ngoài ý muốn |
| Self-invocation | Tách trách nhiệm sang bean khác rồi inject/call qua proxy | Tự inject chính nó hoặc `AopContext.currentProxy()` thêm coupling/vòng đời phức tạp |
| JDK proxy không inject được concrete type | Inject qua interface, kiểm tra tên/qualifier bean | Ép CGLIB có thể đụng class/method final |
| Interface/API bị nạp hai bản ở các loader | Đưa contract dùng chung sang parent/shared loader hoặc quy ước API xuyên loader | Chỉnh classpath bừa bãi có thể phát sinh version conflict |
| Proxy subclass lỗi Java module access | Kiểm tra module exports/opens, package/class visibility, loader và cấu hình chạy | Mở module quá rộng tạo rủi ro bảo trì/bảo mật |
| Advisor không được đăng ký | Đăng ký `@Aspect` thành Spring bean, bật Spring AOP đúng context | Thêm cả XML và Java config trùng có thể khiến advice chạy hai lần |

Nếu yêu cầu thực sự cần advice trực tiếp ở private/final method hoặc self-invocation mà không thể thay đổi service boundary, **AspectJ compile-time/load-time weaving** có thể là một lựa chọn khác. Tuy nhiên, nó đi theo cơ chế bytecode riêng: cần tích hợp build/agent, chọn package weave, kiểm tra thời điểm ClassLoader nạp lớp và test thực tế. Không kỳ vọng chỉ thêm `@Aspect` hoặc `aspectjweaver` dependency sẽ tự đổi Spring AOP thành weaving.

## 10. Checklist nghiệm thu việc chẩn đoán

1. Đã xác minh Spring bean đúng context, có proxy/adjacent advisor và positive test chạy advice.
2. Đã xác định chính xác proxy JDK hay CGLIB; không suy ra từ tên class do thư viện sinh ra.
3. Đã xác nhận method/class modifiers và đường gọi qua proxy hay `this`/raw target.
4. Đã kiểm tra interface/target/proxy ClassLoader khi có lỗi visibility/cast hoặc môi trường nhiều loader.
5. Đã viết ít nhất một negative control để chứng minh advice **không** chạy ở method/bean ngoài phạm vi.
6. Sau sửa lỗi, test với cấu hình Spring Boot/Java/XML, classpath/module path và kiểu deploy tương ứng production.
7. Với aspect có tác dụng nghiệp vụ (transaction/security/audit), đã kiểm tra cả rollback, exception và side effect chứ không chỉ đếm lần advice.

**Ghi nhớ:** Spring AOP intercept tại **proxy boundary**. `final`/`private` gây giới hạn khác nhau tùy JDK proxy hoặc CGLIB; ClassLoader quyết định việc proxy nhìn thấy và dùng cùng type hay không. Kiểm tra **proxy thật, modifiers, đường gọi và class identity** trước khi chọn cách sửa.

## Tài liệu chính thức

- Spring Framework — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- Spring Framework — [Introduction to Spring AOP proxies](https://docs.spring.io/spring-framework/reference/core/aop/introduction-proxies.html)
- Spring Framework — [Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/enable.html)
- Spring Framework — [AspectJ support in Spring](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- Spring Framework — [AspectJ with Spring (weaving is separate)](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-007">Lỗi binding tham số, argNames hoặc thiếu tên tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán lỗi binding tham số, `argNames` hoặc thiếu tên tham số trong Spring AOP

## 1. Khi nào lỗi xuất hiện?

Spring AOP hỗ trợ truyền trực tiếp **đối số method**, **annotation**, **giá trị trả về** và **exception** vào tham số của advice được khai báo bằng `@Aspect`. Khi Spring không ánh xạ được tên biến xuất hiện trong biểu thức pointcut với chữ ký phương thức advice, ứng dụng có thể thất bại khi khởi tạo context, khi dựng advisor hoặc khi xử lý lời gọi.

Ví dụ một lỗi dễ gặp:

```java
@Aspect
class AuditAspect {
    @Before("execution(* submit(..)) && args(requestId)")
    public void before(String id) {
        System.out.println(id);
    }
}
```

Biểu thức dùng biến `requestId` trong `args(requestId)`, còn tham số Java của advice tên `id`. Nếu Spring đọc được tên tham số `id` qua metadata, nó không tự coi `id` là `requestId`. Nếu metadata không có, Spring có thể thử suy luận tên biến; kết quả phụ thuộc số lượng và kiểu binding. Vì vậy **không dựa vào suy luận tình cờ**. Để rõ ràng, sửa thành:

```java
@Before(
    value = "execution(* submit(..)) && args(requestId)",
    argNames = "requestId"
)
public void before(String id) {
    System.out.println(id);
}
```

Ở đây `argNames` đặt tên **cho vị trí tham số của advice**, nên tham số Java `id` được gắn với tên `requestId` của pointcut. Một lựa chọn khác là đặt tên tham số Java thành `requestId` và biên dịch lớp aspect bằng `-parameters`.

**Phạm vi bài:** chẩn đoán các lỗi binding trong `@AspectJ` annotation style **do Spring AOP proxy xử lý**. Trọng tâm là Spring xác định tham số advice, cấu hình build, cách sửa và kiểm thử trong `ApplicationContext`, không ôn lại AspectJ thuần túy.

## 2. Phân biệt ba loại tên để không sửa nhầm

Có ba lớp tên độc lập:

| Vị trí | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Tham số method nghiệp vụ | `submit(String orderCode)` | Tên trong source của method target; Spring AOP `args(x)` không bắt buộc dùng tên này |
| Biến binding trong pointcut | `args(code)`, `@annotation(marker)` | Tên biến mà biểu thức pointcut cung cấp giá trị |
| Tham số method advice | `void before(String id, Tracked tracked)` | Vị trí/kiểu mà Spring phải ánh xạ tới các biến binding |

Ví dụ đúng:

```java
public String submit(String orderCode) { /* ... */ }

@Before(
    value = "execution(* submit(..)) && args(code)",
    argNames = "code"
)
public void before(String anyName) { /* ... */ }
```

Nó có thể hợp lệ vì `args(code)` lấy đối số ở vị trí được match, còn `argNames="code"` ánh xạ biến đó với tham số advice đầu tiên. Không yêu cầu `orderCode`, `code` và `anyName` trùng tên.

Ngược lại, **nếu `argNames` không được khai báo**, Spring cần khám phá tên tham số của advice bằng cơ chế phù hợp. Việc method nghiệp vụ có tên tham số đầy đủ không tự bổ sung metadata cho lớp aspect đã biên dịch.

### 2.1. Các binding được Spring AOP hỗ trợ

- `args(code,..)`: ràng buộc đối số thực tế của phương thức được gọi với biến `code`, trong trường hợp này là đối số đầu tiên. `..` cho phép còn các đối số phía sau.
- `@annotation(marker)`: ràng buộc object annotation trên method khớp pointcut với `marker`; kiểu tham số advice phải là kiểu annotation phù hợp.
- `target(bean)` và `this(proxy)`: ràng buộc target hoặc Spring AOP proxy; hai tham chiếu có thể khác loại khi dùng JDK dynamic proxy.
- `@target`, `@within`, `@args`: có thể ràng buộc annotation tương ứng nếu pointcut phù hợp; **`@args` khác `args`**.
- `returning = "result"` trong `@AfterReturning`: ràng buộc giá trị được trả về bình thường, đồng thời có thể giới hạn kiểu return được xử lý.
- `throwing = "error"` trong `@AfterThrowing`: ràng buộc exception và có thể giới hạn kiểu exception advice nhận.

`execution(* submit(..))` chỉ chọn join point; bản thân nó **không khai báo biến binding**. Vì vậy viết một advice có thêm `String code` nhưng pointcut không có `args(code)`, annotation binding hay nguồn phù hợp khác có thể dẫn đến lỗi liên kết tham số.

## 3. Spring xác định tên tham số advice như thế nào?

Spring hỗ trợ những chiến lược sau, lần lượt thử cho đến khi tìm được tên phù hợp:

1. **`argNames` trong annotation `@Before`, `@Around`, `@AfterReturning`, `@AfterThrowing` hoặc `@Pointcut`.** Tên tường minh, hữu ích khi build không xuất metadata hoặc binding phức tạp.
2. **Kotlin reflection** khi dùng Kotlin và thư viện reflection liên quan có trên classpath.
3. **Java standard reflection** dựa vào `Method.getParameters()[i].getName()`, hoạt động đúng với `javac -parameters`. Đây là hướng khuyến nghị cho Java hiện đại.
4. **Suy luận từ pointcut expression**, cũng như `returning`/`throwing`, khi trường hợp đủ đơn giản để Spring xác định một cách rõ ràng.

Nếu không có chiến lược nào xác định được binding, Spring sẽ báo lỗi. Tùy cấu hình và phiên bản, triệu chứng có thể là `IllegalArgumentException`, `IllegalStateException`, lỗi phát sinh khi tạo bean/advisor hoặc thông báo từ bộ giải tên tham số nói rằng cần `argNames`. Không đặt test phụ thuộc nguyên văn exception message nội bộ.

**Một lỗi chẩn đoán thường gặp:** bật `-g`/debug symbols rồi cho rằng chắc chắn giải quyết được Spring hiện đại. Với Java và Spring Framework 6.1+, cách được khuyến nghị là biên dịch `-parameters`; không dựa vào local-variable-table debug metadata như bản Spring cũ. Cũng không chỉ thêm `-parameters` cho module chứa service: lớp `@Aspect` và lớp định nghĩa named pointcut cần được biên dịch với cấu hình phù hợp.

### 3.1. Maven — giữ metadata tên tham số Java

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <configuration>
        <release>17</release>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

Đặt trong `<build><plugins>...</plugins></build>` của `pom.xml`. Nếu aspect nằm trong module phụ thuộc được biên dịch trước, cấu hình cần áp dụng ở **module tạo ra file `.class` của aspect**, không chỉ module chạy Spring Boot.

### 3.2. Gradle — cả JavaCompile chính và test nếu cần

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['-parameters']
}
```

Đối với Gradle Kotlin DSL:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}
```

Các đoạn Gradle trên là **fragment build script**, không phải chương trình Java độc lập. Nếu dự án đã thiết lập `options.compilerArgs` ở plugin chung, xác minh cờ được truyền khi compile chính lớp aspect trong dependency/build thực tế.

### 3.3. Tự kiểm tra `.class` sau biên dịch

```bash
javap -v -classpath target/test-classes 'lab.BindingAdviceTest$BindingAspect'
```

Trong kết quả `javap`, tìm phần `MethodParameters:` của method advice và tên biến mong muốn. Đường `target/test-classes` là ví dụ Maven cho class nằm trong test source; với build thật có thể là `target/classes`, `build/classes/java/main` hoặc một jar dependency. Nếu chạy `javap` trên đúng class mà không thấy metadata này, `-parameters` có thể chưa được áp dụng (trừ khi dùng chiến lược tường minh `argNames`).

Không chỉ nhìn source code IDE để kết luận tên tham số đã được giữ ở runtime. Đối với Java reflection, `Parameter.isNamePresent()` trả `true` khi tên đã được lưu trong metadata tương ứng.

## 4. Dùng `argNames` đúng cách

### 4.1. Một đối số

```java
@Before(
    value = "bean(probeService) && execution(* submit(..)) && args(code)",
    argNames = "code"
)
public void log(String suppliedId) {
    // suppliedId nhận giá trị đã bind với code
}
```

`argNames` liệt kê **tên biến theo đúng vị trí tham số advice**; ở đây không bắt buộc tên `suppliedId` trong Java trùng `code`.

### 4.2. Đối số cộng annotation

```java
@Before(
    value = "bean(probeService) && execution(* submit(..))"
          + " && args(code) && @annotation(marker)",
    argNames = "code,marker"
)
public void audit(String suppliedId, Tracked tracked) {
    // code -> suppliedId; marker -> tracked
}
```

`Tracked` phải là annotation có kiểu phù hợp và thường dùng `@Retention(RetentionPolicy.RUNTIME)` để có thể bind annotation ở runtime. Nếu đảo thứ tự tham số Java, cũng phải đảo thứ tự `argNames` tương ứng. Đặt thiếu tên hay thêm tên không tương ứng có thể khiến Spring không tạo được advisor.

### 4.3. `JoinPoint` và `ProceedingJoinPoint` ở vị trí đầu

Spring xử lý đặc biệt **tham số đầu tiên** có kiểu `JoinPoint`, `ProceedingJoinPoint` hoặc `JoinPoint.StaticPart`: có thể **bỏ tên của nó khỏi `argNames`**.

```java
@Before(
    value = "bean(probeService) && execution(* submit(..)) && args(code)",
    argNames = "code"
)
public void audit(JoinPoint joinPoint, String suppliedId) {
    // joinPoint là tham số đặc biệt, "code" map tới suppliedId
}
```

Đối với `@Around`, tham số đầu tiên cần là `ProceedingJoinPoint` để gọi `proceed()`:

```java
@Around(
    value = "bean(probeService) && execution(* submit(..)) && args(code)",
    argNames = "code"
)
public Object trace(ProceedingJoinPoint pjp, String suppliedId)
        throws Throwable {
    return pjp.proceed();
}
```

Đây là **các fragment bên trong `@Aspect`**, nên cần import AspectJ phù hợp và bean/method Spring tương ứng; không biên dịch độc lập từng fragment.

### 4.4. `returning` và `throwing`

```java
@AfterReturning(
    pointcut = "bean(probeService) && execution(* submit(..)) && args(code)",
    returning = "result",
    argNames = "code,result"
)
public void afterSuccess(String suppliedId, String output) {
    // "code" -> suppliedId; "result" -> output
}

@AfterThrowing(
    pointcut = "bean(probeService) && execution(* submit(..)) && args(code)",
    throwing = "failure",
    argNames = "code,failure"
)
public void afterFailure(String suppliedId, IllegalStateException error) {
    // "code" -> suppliedId; "failure" -> error
}
```

Tên `result`/`failure` phải được Spring ánh xạ tới đúng tham số method advice. `String output` chỉ match kiểu return phù hợp, còn `IllegalStateException error` chỉ được bind khi exception có kiểu tương thích. Nếu muốn nhận rộng hơn, dùng kiểu phù hợp như `Object` cho kết quả trả về hoặc `Throwable` cho exception. `@AfterReturning` không phải nơi thay thế return value của target; muốn kiểm soát kết quả trả về hãy xem `@Around`.

## 5. Bài test chạy được: xác nhận binding thay vì chỉ xác nhận Spring khởi động

Ví dụ sử dụng **Java 17**, Spring Framework **6.2.8**, AspectJ annotation API qua `aspectjweaver`, Maven và JUnit Jupiter. Toàn bộ `@Aspect` được đăng ký như Spring bean. Ví dụ cố ý dùng **`argNames` tường minh** để người học có thể kiểm chứng binding mà không phụ thuộc cờ `-parameters`.

### 5.1. Cấu trúc

```text
spring-aop-binding-probe/
├── pom.xml
└── src/test/java/lab/BindingAdviceTest.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lab</groupId>
    <artifactId>spring-aop-binding-probe</artifactId>
    <version>1.0-SNAPSHOT</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.version>6.2.8</spring.version>
        <junit.version>5.11.4</junit.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>${spring.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>${spring.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
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
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/test/java/lab/BindingAdviceTest.java`:

```java
package lab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.junit.jupiter.api.Assertions.*;

class BindingAdviceTest {

    @Test
    void binds_argument_and_method_annotation() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = ctx.getBean(ProbeService.class);
            Trace trace = ctx.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("ok:A-7", service.submit("A-7"));
            assertEquals(List.of(
                "before:A-7:SAVE",
                "target:A-7",
                "returned:A-7:ok:A-7"
            ), trace.snapshot());
        }
    }

    @Test
    void binds_thrown_exception_to_named_parameter() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = ctx.getBean(ProbeService.class);
            Trace trace = ctx.getBean(Trace.class);

            IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.submit("ERR")
            );
            assertEquals("rejected:ERR", error.getMessage());
            assertEquals(List.of(
                "before:ERR:SAVE",
                "target:ERR",
                "thrown:ERR:rejected:ERR"
            ), trace.snapshot());
        }
    }

    @Test
    void unrelated_method_does_not_match_bound_pointcut() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            ProbeService service = ctx.getBean(ProbeService.class);
            Trace trace = ctx.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("OTHER", service.other());
            assertEquals(List.of("target.other"), trace.snapshot());
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Tracked {
        String value();
    }

    public static class Trace {
        private final List<String> events = new ArrayList<>();

        public void add(String event) {
            events.add(event);
        }

        public List<String> snapshot() {
            return List.copyOf(events);
        }
    }

    public static class ProbeService {
        private final Trace trace;

        public ProbeService(Trace trace) {
            this.trace = trace;
        }

        @Tracked("SAVE")
        public String submit(String orderCode) {
            trace.add("target:" + orderCode);
            if ("ERR".equals(orderCode)) {
                throw new IllegalStateException("rejected:" + orderCode);
            }
            return "ok:" + orderCode;
        }

        public String other() {
            trace.add("target.other");
            return "OTHER";
        }
    }

    @Aspect
    public static class BindingAspect {
        private final Trace trace;

        public BindingAspect(Trace trace) {
            this.trace = trace;
        }

        @Before(
            value = "bean(probeService) && execution(* submit(..))"
                  + " && args(code) && @annotation(marker)",
            argNames = "code,marker"
        )
        public void before(String incoming, Tracked tagged) {
            trace.add("before:" + incoming + ":" + tagged.value());
        }

        @AfterReturning(
            pointcut = "bean(probeService) && execution(* submit(..))"
                     + " && args(code)",
            returning = "result",
            argNames = "code,result"
        )
        public void afterSuccess(String incoming, String returnedValue) {
            trace.add("returned:" + incoming + ":" + returnedValue);
        }

        @AfterThrowing(
            pointcut = "bean(probeService) && execution(* submit(..))"
                     + " && args(code)",
            throwing = "failure",
            argNames = "code,failure"
        )
        public void afterFailure(
                String incoming,
                IllegalStateException thrownException) {
            trace.add("thrown:" + incoming + ":" +
                thrownException.getMessage());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class Config {
        @Bean
        public Trace trace() {
            return new Trace();
        }

        @Bean
        public ProbeService probeService(Trace trace) {
            return new ProbeService(trace);
        }

        @Bean
        public BindingAspect bindingAspect(Trace trace) {
            return new BindingAspect(trace);
        }
    }
}
```

### 5.2. Chạy và hiểu kết quả

```bash
mvn test
```

Maven Surefire chạy ba test; **kỳ vọng** cả ba PASS, với trace tương ứng:

```text
submit("A-7"):
before:A-7:SAVE
target:A-7
returned:A-7:ok:A-7

submit("ERR"):
before:ERR:SAVE
target:ERR
thrown:ERR:rejected:ERR

other():
target.other
```

Đây là **danh sách event kỳ vọng trong assertion**, không phải stdout literal của Maven. Ví dụ không in trace ra console; JUnit so sánh trực tiếp `List<String>`.

Những điều test chứng minh:

- `AopUtils.isAopProxy(service)` xác nhận target được gọi qua Spring AOP proxy.
- `args(code)` ánh xạ **giá trị truyền vào target** thành biến `code`; `argNames="code,marker"` đưa giá trị này vào tham số `incoming` của advice.
- `@annotation(marker)` ánh xạ annotation `Tracked` trên target method thành tham số `tagged`, nên `tagged.value()` trả `SAVE`.
- `returning="result"` bind giá trị trả về khi chạy bình thường; `throwing="failure"` bind `IllegalStateException` khi method ném exception.
- Method `other()` không match `execution(* submit(..))`, nên không có event advice mặc dù bean vẫn là AOP proxy.

Tất cả advice đặt trong Spring-managed bean `bindingAspect`. Không có AspectJ weaving trong thử nghiệm. Việc `<parameters>true</parameters>` không xuất hiện trong `pom.xml` là có chủ ý để **minh họa `argNames` tường minh**; có thể bật cờ này như cách phòng ngừa chung ở project thật.

## 6. Thử nghiệm đối chứng để nhận diện đúng kiểu lỗi

Sau khi baseline PASS, thay **một điều kiện mỗi lượt**, chạy `mvn test` lại và quan sát:

| Thay đổi | Điều được kiểm tra | Kết quả dự đoán |
| --- | --- | --- |
| Bỏ `argNames` khỏi `@Before` khi class thiếu metadata tên Java | Spring phải tự suy luận `code` và `marker` từ pointcut | Có thể lỗi binding khi inference không xác định rõ; không xem việc suy luận thành công là đảm bảo cho mọi signature |
| Đổi `argNames="code,marker"` thành `"marker,code"` | Tên bị gán sai vị trí/kiểu tham số | Spring có thể từ chối advisor hoặc binding thất bại; kiểm tra lỗi gốc |
| Đổi `@annotation(marker)` thành `@annotation(otherMarker)`, giữ `argNames="code,marker"` | Biểu thức không còn ràng buộc biến đã khai báo | Lỗi parse/binding hoặc advisor không được tạo đúng |
| Xóa `args(code)`, vẫn giữ tham số `String incoming` | Không còn nguồn bind cho `code` | Không thể dựa vào đối số method để tự điền advice parameter |
| Đổi `returning="result"` thành `"response"`, giữ `argNames="code,result"` | Tên return value không có ánh xạ nhất quán | Thất bại binding hoặc advice không hoạt động như kỳ vọng |
| Đổi `throwing="failure"` thành `"problem"`, giữ `argNames="code,failure"` | Tên exception sai | Thất bại binding hoặc advisor không hoạt động như kỳ vọng |
| Dùng `@AfterThrowing` nhận `IllegalArgumentException` khi target ném `IllegalStateException` | Kiểu exception không tương thích | Advice exception không chạy dù method vẫn ném lỗi |
| Gọi `new ProbeService(trace).submit(...)` trực tiếp | Không đi qua proxy | Chỉ có event target, không có advice; đây không phải lỗi binding |

**Không coi một exception message duy nhất là chuẩn bất biến:** thuật toán suy luận, bước tạo advisor và thông điệp có thể khác phiên bản Spring. Tiêu chí mạnh hơn là context khởi động, bean có proxy đúng, và trace có/không có advice phù hợp như giả thuyết.

## 7. Lỗi với named pointcut `@Pointcut`

Binding có thể đi qua hai tầng: khai báo pointcut có tham số, rồi advice gọi pointcut theo tên. Mỗi tầng cần ánh xạ nhất quán.

Ví dụ **fragment sử dụng bên trong một `@Aspect` đã đăng ký trong Spring**:

```java
@Pointcut(
    value = "bean(probeService) && execution(* submit(..)) && args(code)",
    argNames = "code"
)
public void submitted(String requestCode) {}

@Before(value = "submitted(code)", argNames = "code")
public void beforeSubmit(String incoming) {
    // "code" được truyền từ named pointcut đến advice
}
```

Trong bước đầu, `@Pointcut(argNames="code")` ánh xạ biến `code` với tham số Java `requestCode` của method khai báo pointcut. Trong bước hai, `submitted(code)` công bố biến `code` cho advice, và `argNames="code"` ánh xạ vào `incoming`.

Các lỗi hay gặp: quên tham số của named pointcut; gọi `submitted()` trong khi signature nhận một argument; truyền biến sai vị trí; áp dụng `argNames` chỉ lên advice nhưng bỏ mặc pointcut method có nhiều tham số thiếu metadata. Kiểm tra từng tầng, bắt đầu từ biểu thức gốc rồi mở rộng tới advice gọi lại.

## 8. Phân loại triệu chứng và cách sửa trong ứng dụng Spring

### 8.1. Spring context không khởi tạo được

Nếu lỗi xuất hiện lúc tạo `AnnotationConfigApplicationContext` hoặc Spring Boot startup, ưu tiên kiểm tra:

1. Stack trace nguyên nhân sâu nhất liên quan Spring AOP pointcut, advisor hoặc method advice — không chỉ thông báo `BeanCreationException` ở ngoài.
2. `@Aspect` đã được đăng ký đúng là Spring bean chưa, và `@EnableAspectJAutoProxy`/auto-proxy creator có hiệu lực trong context phù hợp không.
3. Các biến trong `args(...)`, `@annotation(...)`, `target(...)` có **khai báo nguồn binding**, đúng kiểu và đúng số lượng không.
4. Thứ tự tên trong `argNames` có đúng vị trí từng tham số advice, bao gồm cả `returning`/`throwing` nếu dùng không.
5. Nếu không dùng `argNames`, file class của **chính aspect** có metadata `-parameters` không.

Nếu hai hoặc nhiều biến có cùng kiểu và bộ suy luận không thể biết tên nào ứng với vị trí nào, hãy khai báo `argNames` tường minh. Đổi kiểu tham số để “lừa” thuật toán suy luận là cách khắc phục không ổn định và khó bảo trì.

### 8.2. Context khởi động nhưng advice không chạy

Khi context vẫn khởi động bình thường, **đầu tiên hãy phân biệt pointcut không match với binding không thành công**:

- Bean lấy qua `ApplicationContext.getBean(...)` có phải AOP proxy không?
- Có gọi từ bên ngoài qua proxy hay đang `this.submit(...)` (self-invocation)?
- `execution(* submit(..))` có match method đang gọi không? Tên method, package/class, visibility, proxy type và đối số có phù hợp?
- `args(code)` có match **đối số runtime** ở vị trí đã chọn không? `args` khác với kiểm tra kiểu khai báo ở `execution`.
- `@annotation(marker)` có match annotation trên method Spring AOP nhìn thấy không? Đặc biệt chú ý method interface/implementation và việc annotation nằm ở đâu.
- `@AfterReturning` chỉ chạy khi target return bình thường; `@AfterThrowing` chỉ chạy trên nhánh ném exception với kiểu phù hợp.

Không nên thêm `argNames` nếu điểm cắt không bao giờ match; nó chỉ giúp ánh xạ tên biến khi có binding hợp lệ.

### 8.3. Tham số advice nhận sai giá trị/nhầm kiểu

Khi nhiều tham số cùng kiểu, đảo thứ tự `argNames` có thể khiến trace nhận nhầm dữ liệu mà không sinh lỗi rõ ràng. Phép thử nên assert **giá trị riêng từng binding** (ví dụ `"A-7"` và `"SAVE"`), không chỉ `adviceInvoked=true`. Dữ liệu sentinel khác nhau giúp nhận ra đảo chỗ.

Khi kết hợp `JoinPoint` hoặc `ProceedingJoinPoint`, nhớ cơ chế bỏ tên **tham số đầu đặc biệt**. Nếu khai báo nó ở vị trí bất thường hoặc đưa thêm tên sai vào danh sách, mapping có thể thất bại.

### 8.4. Chỉ lỗi ở bản đóng gói hoặc trong CI

Kiểm tra sự khác biệt giữa:

- Java/Maven/Gradle compiler flags trong IDE và CI.
- Module tạo `.class` của aspect, đặc biệt khi aspect được đưa vào jar riêng.
- Build cache hoặc dependency cũ chưa build lại.
- Annotation processor/bytecode instrumentation làm thay đổi metadata.
- Cấu hình compiler của source set test và main.
- Khác phiên bản Spring, AspectJ annotation library giữa môi trường thử và môi trường chạy.

Dùng `javap -v` trên đúng artifact deploy để xác thực `MethodParameters`. Khi cần debug pointcut matching, bật log Spring AOP ở phạm vi test; tránh in token, số tài khoản hoặc payload nhạy cảm vào trace.

## 9. Quy trình khắc phục có thể lặp lại

```text
Có lỗi binding hoặc advice không chạy
              |
              v
Bean Spring có proxy và lời gọi qua proxy?
     | không                  | có
     v                        v
Sửa bean/pointcut/      Xác định nơi lỗi:
self-invocation         startup hay invocation
                              |
                              v
                  Điểm cắt có match method?
                     | không       | có
                     v             v
               Sửa phạm vi     Kiểm tra nguồn biến:
               pointcut        args/@annotation/
                               returning/throwing
                                   |
                                   v
                         Chữ ký + argNames khớp?
                            | không      | có
                            v            v
                      Sửa mapping   Kiểm tra runtime
                         hoặc       type, exception,
                      -parameters   return value, trace
                                   |
                                   v
                            Test lại bằng proxy
```

Bản checklist ngắn để áp dụng cho mỗi lỗi:

1. Ghi lại pointcut đầy đủ, chữ ký advice, giá trị `argNames`, `returning`/`throwing`, tên Spring bean và method target thật.
2. Đối chiếu **từng biến binding** với đúng nguồn và đúng tham số advice, đánh dấu `JoinPoint` đầu nếu có.
3. Chọn **một trong hai hướng chính**: bật `-parameters` ở module chứa aspect hoặc chỉ rõ `argNames` đầy đủ; với case mơ hồ, ưu tiên mapping tường minh.
4. Build sạch module liên quan và kiểm tra `.class` thực tế bằng `javap` khi cần; không suy đoán metadata từ source.
5. Tạo test Spring context tối thiểu, inject target từ container, assert `AopUtils.isAopProxy`, gọi method thành công/thất bại và assert giá trị advice nhận được.
6. Thêm ca negative pointcut không match và ca kiểu exception không tương thích khi giúp phân biệt thiếu binding với thiếu join point.
7. Sau khi PASS ở fixture, chạy integration test trên cấu hình Spring Boot thật; kiểm tra sự hiện diện của advisor transaction/security/cache nếu chúng tham gia cùng method.

## 10. Lưu ý quan trọng khi sửa lỗi

| Ngộ nhận | Điều cần làm |
| --- | --- |
| Tên tham số method nghiệp vụ bắt buộc bằng tên trong `args(...)` | Không: xác định biến pointcut và mapping advice; tên method target là độc lập |
| Đã bật `-parameters` cho app nên dependency aspect chắc chắn ổn | Kiểm tra chính artifact chứa `@Aspect` |
| `argNames` phải giống tên tham số Java trong source | `argNames` là mapping theo vị trí; có thể khác tên source khi khai báo đúng |
| `JoinPoint` đầu phải luôn xuất hiện trong `argNames` | Có thể bỏ tên của tham số đặc biệt đầu tiên |
| `@annotation` giống `args` | Một cái bind annotation, một cái bind đối số runtime |
| `returning="result"` tự tạo biến `result` | Phải có tham số advice nhận binding phù hợp |
| Có `@Aspect` thì tự có proxy | Cần aspect Spring bean và auto-proxy configuration tương ứng |
| Không log gì nghĩa là tên tham số sai | Kiểm tra proxy/pointcut/self-invocation trước |
| Lỗi chỉ do Spring, không do cách biên dịch | Thiếu `MethodParameters` rất thường do build flags/module artifact |
| Dùng reflection `getArgs()` là luôn tốt hơn | Có thể dùng `JoinPoint.getArgs()` khi thật sự cần xử lý tổng quát; typed binding rõ ràng vẫn là lựa chọn tốt cho advice có hợp đồng cụ thể |

## 11. Tiêu chí hoàn thành

Một lỗi binding trong Spring AOP được xem là xử lý đúng khi:

1. Spring context tạo thành công với `@Aspect` được đăng ký trong đúng phạm vi.
2. Advice nhận **chính xác giá trị và kiểu** của các binding qua `args`, annotation, `returning` và `throwing`, có assertion trên dữ liệu đầu vào cụ thể.
3. Tên `argNames` và chữ ký advice/pointcut thống nhất, hoặc metadata tham số `-parameters` được chứng minh có trong artifact.
4. Ca đối chứng không match chỉ chạy target, không kích hoạt advice ngoài phạm vi.
5. Test chạy qua Spring proxy thật và kiểm tra cả nhánh thành công lẫn exception.
6. Cấu hình compiler giống nhau giữa local, CI và artifact được chạy.

**Kết luận:** Để sửa lỗi binding tham số trong Spring AOP, hãy theo chuỗi **pointcut variable → tên `argNames`/metadata → vị trí/kiểu advice parameter → trace thực tế**. Trước khi sửa compiler flags hoặc advice signature, xác minh proxy và pointcut đã match đúng Spring bean/method. Kết quả cuối phải được chứng minh bằng một lời gọi thật qua proxy với giá trị binding kiểm tra được.

### Tài liệu chính thức

- [Spring Framework — Declaring Advice: Advice Parameters](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `args`, `@annotation`, binding `returning`/`throwing`, xác định tên và `argNames`.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): pointcut designators, named pointcuts và matching method.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký `@Aspect` qua Spring bean và auto-proxy.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): giới hạn Spring AOP proxy và self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-008">Advice chạy nhiều lần do proxy lồng hoặc cấu hình trùng</a>

<details>
<summary>Xem chi tiết</summary>

# Chẩn đoán advice chạy nhiều lần do proxy lồng hoặc cấu hình trùng trong Spring AOP

## 1. Hiện tượng cần phân biệt

Trong Spring AOP, một method được gọi một lần từ controller nhưng log của `@Before` hoặc `@Around` lại xuất hiện hai, ba lần. Đôi khi method nghiệp vụ **chỉ chạy một lần**, đôi khi chính method nghiệp vụ cũng chạy nhiều lần. Hai kết quả ấy có nguyên nhân và cách khắc phục rất khác nhau.

Các tình huống thường gặp:

1. **Proxy lồng (nested proxies):** một Spring AOP proxy lại được dùng làm target của Spring AOP proxy khác; cùng một advice/logic logging có thể hiện diện ở cả hai lớp.
2. **Advisor/advice đăng ký trùng trong một proxy:** một `MethodInterceptor` hoặc hai `@Aspect` beans cùng hành vi được đưa vào một interceptor chain.
3. **Cấu hình proxy thủ công trùng với auto-proxy:** dự án có `ProxyFactory`/`ProxyFactoryBean`, trong khi Spring cũng dùng `@EnableAspectJAutoProxy`, schema-based AOP hoặc auto-proxy creator khác; trong một số wiring, bean được bọc thêm.
4. **Lời gọi được lặp có chủ đích:** `@Retryable`, vòng lặp của một `@Around` advice hoặc chính caller thực hiện nhiều lần; mỗi attempt có thể kích hoạt advice hợp lệ.
5. **Hai lời gọi nghiệp vụ riêng biệt bị nhầm là một:** controller gọi hai entry points, method được gọi đệ quy, listener nhận hai events hoặc một request được retry từ tầng HTTP.

**Mục tiêu quan trọng nhất** là xác định **số lần đi vào proxy**, **số lượt advice được invoke** và **số lượt target method thực sự chạy**. Không xóa bớt aspect chỉ vì thấy hai dòng log giống nhau.

Bài tập trung vào việc chẩn đoán **Spring AOP proxy-based interception**: `AopUtils`, `Advised`, `Advisor`, `TargetSource`, `ProxyFactory`, auto-proxy creator và những tương tác cấu hình Spring. Những nội dung về Java proxy hoặc AspectJ được đề cập chỉ trong phạm vi giúp chẩn đoán Spring Aspect.

## 2. Sơ đồ ba loại lặp rất khác nhau

### 2.1. Hai lớp proxy cùng áp dụng advice

~~~text
Caller gọi MỘT lần
         |
         v
 [Outer Spring proxy]
         |
         +-- Audit advice: before (lần 1)
         |
         v
 [Inner Spring proxy]
         |
         +-- Audit advice: before (lần 2)
         |
         v
 [Business target.method()]
         |   chạy MỘT lần
         |
         v
 [Inner advice: after]
         |
         v
 [Outer advice: after]
         |
         v
 Caller nhận một kết quả
~~~

Ở trường hợp này, một proxy làm target của proxy ngoài. Nếu hai lớp cùng chứa logic audit, hai `before` là **hai advisor invocations riêng**, mặc dù chỉ có một business method execution. Hãy xác minh cả hai lớp thật sự là Spring AOP proxy trước khi kết luận nested proxy.

### 2.2. Một proxy chứa hai advisors giống nhau

~~~text
Caller -> [Một Spring proxy]
                |
                +-- Advisor #1 -> Audit.before
                +-- Advisor #2 -> Audit.before
                |
                +-- Business target.method() một lần
                |
                +-- Advisor #2 -> Audit.after
                +-- Advisor #1 -> Audit.after
~~~

Không cần proxy lồng mới có log hai lần. Một `Advised.getAdvisors()` của **proxy ngoài cùng** có thể có hai advisor entries tương ứng cùng advice instance hoặc hai advice objects khác nhau có chức năng giống nhau.

Lưu ý: hai advisors khác nhau cùng ghi log là chuyện hợp lệ nếu mỗi advisor phục vụ một mục đích. “Trùng” là quyết định của yêu cầu nghiệp vụ, không phải chỉ vì có cùng interface `MethodInterceptor`.

### 2.3. Một advisor gọi target nhiều lần

~~~text
Caller -> Spring proxy
              |
              +-- Retry/Around advice (MỘT lần vào advice ngoài)
                       |
                       +-- proceed() lần 1 -> target.method() -> exception
                       +-- proceed() lần 2 -> target.method() -> success
              |
              +-- Caller nhận kết quả
~~~

Lúc này target method có thể chạy **hai lần** dù lớp proxy không bị nhân đôi. Với retry, số target invocations thường tương ứng attempts. Với vòng `proceed()` thủ công, cùng một `@Around` cũng có thể cố tình chạy join point nhiều lần. Đừng xóa advisor hợp lệ nếu yêu cầu là retry các lỗi tạm thời.

## 3. Ma trận chẩn đoán từ số đếm

| Quan sát trong một lần gọi entrypoint | Nguyên nhân cần điều tra đầu tiên | Điểm kiểm tra |
| --- | --- | --- |
| Advice vào 2 lần, target 1 lần | Hai advice entries hoặc hai proxy layers | `Advised.getAdvisors()`, kiểm tra singleton target có phải proxy |
| Advice vào 1 lần, target 2 lần | Around/retry advice gọi `proceed()` nhiều lần, target được gọi thêm từ code | Retry metrics, `proceed()` count, caller path |
| Advice vào 2 lần, target 2 lần | Nested proxies kết hợp retry, caller 2 lần, hoặc hai proxy calls | Correlation ID riêng cho caller/proxy/attempt |
| Hai log y hệt, nhưng interceptor counter 1 | Log được ghi hai lần trong cùng advice, hoặc logger có hai appenders | Đếm `invoke()` bằng counter, kiểm tra logging configuration |
| `AopUtils.isAopProxy(bean)==false` mà log vẫn trùng | Lời gọi thật đi qua bean khác/proxy khác, hoặc duplicate ở tầng không phải Spring AOP | Kiểm tra object mà caller thực sự dùng |
| Có 2 advisors cùng loại nhưng advice chỉ chạy 1 lần | Pointcut của một advisor không match, hoặc chain bị short-circuit | Method matcher, `proceed()`, advice ordering |
| Test chỉ tăng counter ở `@Before`, không có target execution count | Không phân biệt proxy layer với retry/caller | Đo cả advice và target bằng probes riêng |

**Hai khái niệm không đồng nghĩa:** “advice được gọi hai lần” và “tác dụng phụ được thực hiện hai lần”. Ví dụ một `@Before` audit được ghi hai lần có thể dẫn đến double audit records dù target chỉ charge tiền một lần; chiều ngược lại, retry target có thể gọi remote payment hai lần dù `@Around` ở ngoài chỉ vào một lần. Cần đo đúng vị trí nghiệp vụ có side effect.

## 4. Vì sao Spring có thể tạo proxy lồng?

### 4.1. Dùng ProxyFactory bọc một proxy đã tồn tại

Ví dụ đơn giản:

~~~java
TaskApi first = createSpringProxy(rawTarget, auditAdvice);
TaskApi second = createSpringProxy(first, auditAdvice);
~~~

Đây là snippet minh họa với method/variables có sẵn; ví dụ chạy đầy đủ ở phần 6. `first` đã là proxy, nhưng `second` tiếp tục tạo **proxy của proxy**. Một call `second.execute(...)` đi qua advice của `second` rồi advice của `first`.

Spring không tự coi hai instance `ProxyFactory` độc lập này là cùng một factory phải được hợp nhất. Đây là trách nhiệm của code tạo proxy: **kiểm tra xem target được đưa vào factory đã là proxy phù hợp chưa**.

### 4.2. Trộn khai báo proxy thủ công với auto-proxy

Một dự án có thể định nghĩa:

~~~xml
<bean id="orderService"
      class="org.springframework.aop.framework.ProxyFactoryBean">
    <!-- target, proxyInterfaces, interceptorNames -->
</bean>
~~~

đồng thời bật:

~~~java
@EnableAspectJAutoProxy
~~~

Nếu bean product đã là AOP proxy và một cơ chế auto-proxy khác tiếp tục bọc đối tượng ấy, **có khả năng** xuất hiện nhiều lớp proxy, tùy bean definitions, post-processors và advisor eligibility. Không được nói rằng chỉ cần đồng thời có `ProxyFactoryBean` và `@EnableAspectJAutoProxy` thì Spring chắc chắn nhân đôi mọi lời gọi: phải xem **bean thật** nhận từ container và chain thực tế.

Trường hợp khác: một `BeanPostProcessor` tự viết trả về `ProxyFactory.getProxy()` quanh bean vốn đã được xử lý bởi auto-proxy creator. Khi thứ tự post-processors thay đổi, outer/inner proxy có thể khác so với mong đợi.

### 4.3. Cần phân biệt cơ chế hợp nhất auto-proxy creator

Spring documentation nêu: nhiều cấu hình `<aop:config/>`, `<aop:aspectj-autoproxy/>` và `<tx:annotation-driven/>` trong cùng infrastructure có thể được **hợp nhất thành một auto-proxy creator**, áp dụng các tùy chọn proxy mạnh nhất (ví dụ `proxy-target-class="true"`).

Vì vậy:

- **Không** kết luận “có hai `@Enable...` tức luôn có hai proxy”.
- **Không** coi `@EnableTransactionManagement` cộng `@EnableAspectJAutoProxy` tự động là lỗi double advice. Chúng có thể thêm những advisors **khác nhau** vào cùng một proxy.
- **Không** coi đổi `proxyTargetClass` là biện pháp tự động loại duplicate. Tham số này thay đổi proxy strategy JDK/CGLIB, không phải cơ chế deduplicate advisor registrations.
- Nếu dùng thêm `BeanNameAutoProxyCreator`, `DefaultAdvisorAutoProxyCreator` custom hoặc `ProxyFactoryBean` thủ công, phải kiểm tra tương tác thực tế; Spring schema documentation cảnh báo tránh trộn những auto-proxy creators riêng với schema-style AOP thiếu kiểm soát.

Điều đáng chẩn đoán là **cấu trúc proxy và danh sách advisors runtime**, không phải chỉ số lượng annotations/config files được grep thấy.

## 5. Vì sao cùng một advice có thể đăng ký trùng?

### 5.1. Cùng interceptor thêm hai lần

~~~java
ProxyFactory factory = new ProxyFactory(rawTarget);
factory.addAdvice(audit);
factory.addAdvice(audit);
TaskApi proxy = (TaskApi) factory.getProxy();
~~~

Nếu cùng một `MethodInterceptor` được thêm hai lần, Spring có thể đưa nó vào **hai vị trí** trong advisor chain. Mỗi vị trí gọi `invoke()` riêng, dù chỉ dùng một Java advice instance.

### 5.2. Hai Spring @Aspect beans cùng logic

~~~java
@Aspect
public class AuditAspect {
    @Before("execution(* demo..*Service.execute(..))")
    public void before() {
        System.out.println("audit");
    }
}
~~~

Nếu class `AuditAspect` được đăng ký **hai bean instances khác nhau**, chẳng hạn:

~~~java
@Bean("auditAspectOne")
AuditAspect auditAspectOne() {
    return new AuditAspect();
}

@Bean("auditAspectTwo")
AuditAspect auditAspectTwo() {
    return new AuditAspect();
}
~~~

hai aspect beans có thể tạo **hai sets advisors cùng matching**. Các snippet này cần được đặt vào class `@Configuration` thích hợp, imports `@Aspect`, `@Before`, `@Bean`, và `@EnableAspectJAutoProxy` khi test; chúng là **ví dụ sai chủ đích**, không phải file chạy độc lập.

Tình huống thực tế gồm: aspect class được `@ComponentScan` đăng ký rồi lại `@Bean` với tên khác; import cùng cấu hình tạo hai bean IDs; parent/child contexts dùng hai beans thực sự khác nhau; hoặc hai modules cung cấp aspects có cùng pointcut. Cần kiểm tra bean definitions **theo tên và instance identity** trong đúng context thay vì mặc định tất cả lần import `@Configuration` đều tạo bean duplicate (Spring còn có cơ chế hợp nhất/đăng ký chống trùng theo bean name).

### 5.3. XML advisor và @Aspect advice cùng mục đích

Ví dụ một `<aop:advisor>` ghi audit và một `@Aspect` cũng log cùng target method. Cả hai có thể được coi là hợp lệ từ góc nhìn Spring: **hai advisors khác nhau đều match**. Thứ tự `@Order` chỉ điều khiển nesting, **không xóa một advisor**.

Không cần có hai proxies cho trường hợp này. Inspect `Advised.getAdvisors()` để xem có nhiều advisors cùng responsibility trên một proxy hay không.

### 5.4. AspectJ weaving và Spring AOP proxy cùng tác động

Nếu ứng dụng chủ động dùng AspectJ compile-time/load-time weaving và đồng thời tạo Spring proxy có advice tương đương, một join point có thể bị xử lý bởi **hai cơ chế interception khác nhau**. `AopUtils` chỉ quan sát Spring AOP proxies; nó không kiểm kê đầy đủ bytecode weaving.

Đây là tình huống **chỉ cần xét nếu dự án thực sự bật CTW/LTW**. `aspectjweaver` nằm trên classpath để Spring parse `@AspectJ` expressions **không tự chứng minh** rằng ứng dụng đã bật AspectJ weaving. Tránh quy kết thiếu căn cứ.

## 6. Ví dụ Java 17 chạy được: single proxy, nested proxy và duplicate advice

Dự án dưới đây dùng trực tiếp **Spring `ProxyFactory`** để tái hiện ba trường hợp, hoàn toàn tách khỏi component scan và những bean khác. Mỗi case tạo **target và counter mới**, nên kết quả không bị lẫn giữa các trường hợp.

- Case A: một proxy với một advice → `adviceCalls=1`, `targetCalls=1`.
- Case B: một proxy bọc một proxy khác; cùng advice instance được đặt ở cả hai → `adviceCalls=2`, `targetCalls=1`.
- Case C: **một** proxy nhưng cùng advice được thêm hai lần → `adviceCalls=2`, `targetCalls=1`.

### 6.1. Cấu trúc project

~~~text
spring-aop-duplicate-debug/
├── pom.xml
└── src/
    ├── main/java/demo/DuplicateAdviceDemo.java
    └── test/java/demo/DuplicateAdviceTest.java
~~~

### 6.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-duplicate-debug</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.1.15</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
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
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>demo.DuplicateAdviceDemo</mainClass>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-aop` cung cấp `ProxyFactory`, `AopUtils`, `Advised`, AOP Alliance `MethodInterceptor` và hạ tầng JDK proxy cần thiết. Demo programmatic API **không parse biểu thức `@AspectJ`**, nên không cần bổ sung AspectJ Weaver cho mã ở phần này. JUnit Jupiter dùng để assert số lần execution; không cần Spring Boot hay database.

### 6.3. Chương trình main

**src/main/java/demo/DuplicateAdviceDemo.java**

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public final class DuplicateAdviceDemo {

    public interface TaskApi {
        String execute(String id);
    }

    public static final class TaskService implements TaskApi {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public String execute(String id) {
            executions.incrementAndGet();
            System.out.println("target " + id);
            return "done " + id;
        }

        public int executions() {
            return executions.get();
        }
    }

    public static final class Audit implements MethodInterceptor {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public Object invoke(
                org.aopalliance.intercept.MethodInvocation invocation)
                throws Throwable {
            invocations.incrementAndGet();
            System.out.println("audit before");
            try {
                Object result = invocation.proceed();
                System.out.println("audit after");
                return result;
            } catch (Throwable error) {
                System.out.println("audit error");
                throw error;
            }
        }

        public int invocations() {
            return invocations.get();
        }
    }

    public static TaskApi oneLayer(TaskApi target, Audit audit) {
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(target);
        factory.setInterfaces(TaskApi.class);
        factory.setProxyTargetClass(false);
        factory.addAdvice(audit);
        return (TaskApi) factory.getProxy();
    }

    public static TaskApi twoEntriesOneLayer(TaskApi target, Audit audit) {
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(target);
        factory.setInterfaces(TaskApi.class);
        factory.setProxyTargetClass(false);
        factory.addAdvice(audit);
        factory.addAdvice(audit); // CỐ Ý: cùng advice hai lần.
        return (TaskApi) factory.getProxy();
    }

    public static void main(String[] args) {
        // Case A: một proxy, một advice.
        TaskService rawA = new TaskService();
        Audit auditA = new Audit();
        TaskApi single = oneLayer(rawA, auditA);

        System.out.println("=== single ===");
        System.out.println("result=" + single.execute("A"));
        System.out.println("adviceCalls=" + auditA.invocations());
        System.out.println("targetCalls=" + rawA.executions());

        // Case B: outer proxy target = inner proxy.
        TaskService rawB = new TaskService();
        Audit auditB = new Audit();
        TaskApi inner = oneLayer(rawB, auditB);
        TaskApi outer = oneLayer(inner, auditB);

        System.out.println("=== nested ===");
        System.out.println("result=" + outer.execute("B"));
        System.out.println("adviceCalls=" + auditB.invocations());
        System.out.println("targetCalls=" + rawB.executions());
        System.out.println("outerAdvisors="
                + ((Advised) outer).getAdvisors().length);
        System.out.println("innerIsProxy="
                + AopUtils.isAopProxy(
                    AopProxyUtils.getSingletonTarget(outer)));

        // Case C: một proxy chứa hai advice entries.
        TaskService rawC = new TaskService();
        Audit auditC = new Audit();
        TaskApi duplicate = twoEntriesOneLayer(rawC, auditC);

        System.out.println("=== duplicate-advisors ===");
        System.out.println("result=" + duplicate.execute("C"));
        System.out.println("adviceCalls=" + auditC.invocations());
        System.out.println("targetCalls=" + rawC.executions());
        System.out.println("proxyAdvisors="
                + ((Advised) duplicate).getAdvisors().length);
    }
}
~~~

**Vì sao ví dụ cố tình dùng một advice instance hai lần?** Nó chứng minh rằng đếm **Java object instances của advice** không bằng đếm **vị trí của advice trong chain**. Cùng `Audit auditB` nằm ở outer và inner proxy khiến `auditB.invocations()` tăng hai. Case C còn trực tiếp cho thấy một `Advised` có hai advisor entries dù chỉ một AOP proxy.

`TaskService` được đánh dấu `final` có chủ đích nhưng proxy dựa trên **`TaskApi` interface với JDK dynamic proxy**, không tạo subclass của target, nên hợp lệ trong ví dụ. Nếu đổi sang CGLIB `setProxyTargetClass(true)` để proxy một final class thì không còn hợp lệ.

### 6.4. Chạy và output kỳ vọng

~~~bash
mvn -q compile exec:java
~~~

Những dòng in từ chương trình:

~~~text
=== single ===
audit before
target A
audit after
result=done A
adviceCalls=1
targetCalls=1
=== nested ===
audit before
audit before
target B
audit after
audit after
result=done B
adviceCalls=2
targetCalls=1
outerAdvisors=1
innerIsProxy=true
=== duplicate-advisors ===
audit before
audit before
target C
audit after
audit after
result=done C
adviceCalls=2
targetCalls=1
proxyAdvisors=2
~~~

Maven hoặc logging có thể in thêm thông báo; các dòng trên là **stdout do chương trình tự in** trong setup thông thường. Không dùng generated proxy class names làm một phần expected output vì chúng thay đổi theo JDK/Spring version.

**Điểm mấu chốt:**

- Case B có `outerAdvisors=1` nhưng advice chạy hai lần vì **outer target là inner proxy**, mà inner proxy có advisor riêng.
- Case C có `proxyAdvisors=2`, chỉ **một proxy** là đủ cho advice chạy hai lần.
- Cả B và C đều `targetCalls=1`, nên nếu production có double billing, nguyên nhân không thể được khẳng định chỉ từ hai dòng `audit before`; phải đo thật số lần gọi đến payment gateway/target.
- `AopProxyUtils.getSingletonTarget(outer)` an toàn trong mô hình này vì outer `ProxyFactory` giữ static singleton target source; nó trả `inner`, và `AopUtils.isAopProxy(inner)` là `true`.

### 6.5. JUnit tests kiểm soát regression

**src/test/java/demo/DuplicateAdviceTest.java**

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateAdviceTest {

    @Test
    void oneProxyOneAdviceOneTargetExecution() {
        var raw = new DuplicateAdviceDemo.TaskService();
        var audit = new DuplicateAdviceDemo.Audit();

        var proxy = DuplicateAdviceDemo.oneLayer(raw, audit);
        assertEquals("done X", proxy.execute("X"));

        assertTrue(AopUtils.isAopProxy(proxy));
        assertEquals(1, audit.invocations());
        assertEquals(1, raw.executions());
        assertEquals(1, ((Advised) proxy).getAdvisors().length);
    }

    @Test
    void nestedProxiesInvokeAdviceTwiceButTargetOnce() {
        var raw = new DuplicateAdviceDemo.TaskService();
        var audit = new DuplicateAdviceDemo.Audit();

        var inner = DuplicateAdviceDemo.oneLayer(raw, audit);
        var outer = DuplicateAdviceDemo.oneLayer(inner, audit);

        assertEquals("done Y", outer.execute("Y"));
        assertEquals(2, audit.invocations());
        assertEquals(1, raw.executions());

        assertEquals(1, ((Advised) outer).getAdvisors().length);
        assertTrue(AopUtils.isAopProxy(
                AopProxyUtils.getSingletonTarget(outer)));
    }

    @Test
    void duplicateAdviceEntriesHaveSameSymptomsWithoutNestedProxy() {
        var raw = new DuplicateAdviceDemo.TaskService();
        var audit = new DuplicateAdviceDemo.Audit();

        var proxy = DuplicateAdviceDemo.twoEntriesOneLayer(raw, audit);

        assertEquals("done Z", proxy.execute("Z"));
        assertEquals(2, audit.invocations());
        assertEquals(1, raw.executions());
        assertEquals(2, ((Advised) proxy).getAdvisors().length);

        // Target là raw service, không phải một Spring AOP proxy.
        assertFalse(AopUtils.isAopProxy(
                AopProxyUtils.getSingletonTarget(proxy)));
    }
}
~~~

Chạy:

~~~bash
mvn -q test
~~~

Báo cáo Surefire dự kiến cho class này khi tests chạy thành công:

~~~text
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
~~~

Các giá trị ở trên là **kết quả kỳ vọng từ logic demo**, không phải một lời khẳng định rằng Maven đã được chạy trên máy hiện tại. Test chứng minh chính xác sự khác nhau giữa cấu trúc **một proxy có hai advisors** và **hai proxies mỗi proxy có một advisor**.

## 7. Cách chẩn đoán trên bean Spring đang chạy thật

Với Spring-managed bean `orderService`, hãy bắt đầu từ **object mà caller nhận qua DI**:

~~~java
Object bean = applicationContext.getBean("orderService");

System.out.println("isAopProxy="
        + AopUtils.isAopProxy(bean));
System.out.println("isJdkProxy="
        + AopUtils.isJdkDynamicProxy(bean));
System.out.println("isCglibProxy="
        + AopUtils.isCglibProxy(bean));
System.out.println("ultimateTargetClass="
        + AopProxyUtils.ultimateTargetClass(bean).getName());
~~~

Đây là snippet với `applicationContext` đã được khai báo, imports `org.springframework.aop.support.AopUtils` và `org.springframework.aop.framework.AopProxyUtils`. Đừng inspect một raw `new OrderService()` khác instance mà production caller thật sự sử dụng.

`AopUtils.isAopProxy(bean)` chỉ nói object ngoài cùng là proxy Spring; nó **không cho biết số lớp lồng**. `AopProxyUtils.ultimateTargetClass(bean)` cố gắng tìm target class cuối cùng qua các singleton proxy layers mà không cần tùy tiện đánh thức scoped/lazy targets. Kết quả class type đúng **cũng không chứng minh** không có proxy lồng.

### 7.1. Đếm advisors trên một lớp proxy

~~~java
if (bean instanceof Advised advised) {
    Advisor[] advisors = advised.getAdvisors();

    System.out.println("advisors=" + advisors.length);
    for (int i = 0; i < advisors.length; i++) {
        System.out.println(i + ": " + advisors[i]);
    }
}
~~~

Snippet cần imports `org.springframework.aop.framework.Advised` và `org.springframework.aop.Advisor`. Một số Spring proxies được cấu hình opaque có thể không expose `Advised`, do đó phải kiểm tra `instanceof` thay vì ép cast.

Dòng `advisor.toString()` phục vụ **debug**, không phải ổn định để assert; tên class/bean/pointcut có thể thay đổi. Nếu thấy hai advisors có chức năng tương tự, xác định nguồn đăng ký của từng advisor từ config/bean definitions. Hai advisors được liệt kê cũng chưa chắc **cả hai match method đang xét**.

### 7.2. Kiểm tra proxy layers an toàn qua singleton targets

Dùng `AopProxyUtils.getSingletonTarget(Object)` để kiểm tra xem target **trực tiếp** của proxy là singleton Spring proxy khác hay không:

~~~java
Object inner = AopProxyUtils.getSingletonTarget(bean);

if (inner != null) {
    System.out.println("innerIsProxy=" + AopUtils.isAopProxy(inner));
}
~~~

Nếu `innerIsProxy=true`, đây là bằng chứng mạnh có một proxy nằm sau proxy hiện tại trong **cơ chế SingletonTargetSource**. Nếu `inner==null`, không có nghĩa hoàn toàn không thể có proxy lồng: bean có thể dùng một **dynamic/scoped/pooled TargetSource** hoặc không expose cấu trúc phù hợp.

Có thể lặp qua các lớp tĩnh, thêm giới hạn độ sâu:

~~~java
static void inspectStaticLayers(Object bean) {
    Object current = bean;

    for (int depth = 0; depth < 8; depth++) {
        if (!(current instanceof Advised advised)) {
            System.out.println("stop: non-Advised target");
            break;
        }

        System.out.println("layer=" + depth
            + ", advisors=" + advised.getAdvisors().length);

        Object next = AopProxyUtils.getSingletonTarget(current);
        if (next == null || next == current) {
            System.out.println("stop: no further singleton target");
            break;
        }
        current = next;
    }
}
~~~

Đây là method minh họa để đặt trong Java class có imports `Advised`, `AopProxyUtils`. Giới hạn `depth < 8` để tránh vòng lặp vô hạn nếu một cấu hình lỗi tạo chu trình bất thường. Với ứng dụng phức tạp có thể tăng mức độ diagnostics, nhưng không tùy tiện gọi `TargetSource.getTarget()` cho mỗi proxy để “unwrap tới tận cùng”: lazy/scoped/pooling target sources có lifecycle acquire/release, và một lệnh inspect có thể khởi tạo bean ngoài dự kiến.

**Phân biệt JDK và CGLIB:** cả hai đều có thể là lớp ngoài hoặc trong. Không dựa vào tên class chứa `$Proxy`/`CGLIB` để kết luận nested; `AopUtils`, `Advised`, target source và advisor list đáng tin hơn.

## 8. Chẩn đoán source configuration mà không suy đoán

Một bảng kiểm ngắn, theo thứ tự xác suất/cost:

| Tìm trong cấu hình Spring | Rủi ro phát sinh | Cách xác minh |
| --- | --- | --- |
| `@Aspect` vừa `@Component` vừa có `@Bean` cùng type | Hai aspect beans, hai sets advisors | Liệt kê aspect beans theo **tên**, không chỉ theo type |
| Cùng audit advice được gắn trong `ProxyFactory.addAdvice` hai lần | Hai entries trong cùng chain | `((Advised) proxy).getAdvisors()` |
| `ProxyFactoryBean.interceptorNames` chứa trùng tên advisor | Advisor có thể được thêm hai lần | Đọc danh sách tên đã khai báo, kiểm tra chain |
| Manual `ProxyFactory` bọc bean lấy từ `ApplicationContext` | Bean có thể đã là proxy, tạo nested proxy | Kiểm tra `AopUtils.isAopProxy(target)` **trước** `getProxy()` |
| `ProxyFactoryBean` + auto-proxy infrastructure | Có thể wrapping thêm tùy bean/post-processor | Inspect outer proxy và static singleton inner target |
| XML `<aop:aspect>` và `@Aspect` cùng logging | Hai advisors/cấu hình khác nhau match cùng method | Advisor list và test method cụ thể |
| Multiple `@Enable...` + Spring transaction | Có thể nhiều legitimate advisors, nhưng auto-proxy creators thường hợp nhất | Xem advisor roles, không coi 2 annotations = 2 proxies |
| BeanNameAutoProxyCreator custom cùng schema AOP | Cơ chế wrapping/eligibility có thể giao thoa | Kiểm tra BeanPostProcessors và bean objects thực |
| Retry advice / vòng lặp `proceed()` | Nhiều target invocations trong một proxy call | Correlate attempts, exception, `proceed()` |
| AspectJ LTW/CTW đang bật thật | Advice tại bytecode + proxy advice có thể cùng tác động | JVM agent/build weaving config và proxy advisors |
| Hai log appenders hoặc logging bridge lặp | Log line bị nhân đôi mà advice không chạy hai lần | Increment counter ngay trong advice, inspect logger config |

**Đừng dựa vào bean count đơn thuần:** một bean `@Aspect` có thể đóng góp nhiều advice methods, vì thế `getAdvisors().length` lớn hơn một là bình thường. Một transaction advisor cùng một audit advisor cũng là hai chức năng hợp lệ.

## 9. Ngăn proxy lồng khi tạo ProxyFactory programmatically

Nếu code hạ tầng nhận `Object candidate` có thể đã được Spring IoC proxy, cần có policy rõ:

~~~java
Object candidate = applicationContext.getBean("taskService");

if (AopUtils.isAopProxy(candidate)) {
    // Đừng mặc nhiên ProxyFactory(candidate) thêm một vòng advice nữa.
    // Xem Advised/Advisor chain và chính sách ownership trước.
}
~~~

Đây là snippet minh họa với `applicationContext` đã có. Không có một API generic “nếu đã có proxy thì hãy bỏ hết mọi proxy” an toàn cho mọi trường hợp: nhiều proxy layers đôi khi được dùng có chủ đích để tổ chức transaction/security/scoped targets. Nhưng nếu bạn muốn **chỉ một audit advisor** cho một bean, hãy đặt ownership của advice vào **một chỗ** thay vì thêm bất cứ khi nào gặp instance.

Một hướng khác khi đã có AOP proxy hỗ trợ `Advised`: có thể **quan sát và thay đổi advisor list** của proxy nếu config cho phép, thay vì tạo outer proxy. Tuy nhiên thay đổi runtime advisor list là thao tác hạ tầng nhạy cảm (frozen configuration, ordering, shared beans, concurrency), không nên mặc định làm trong controller/business code.

Với application sử dụng Spring auto-proxying, thường tốt hơn để **một cơ chế auto-proxy được container quản lý** thay vì tạo manual `ProxyFactory` bên trong từng `@Bean` rồi để auto-proxy creator xử lý tiếp. Với bean cần proxy thủ công đặc biệt, hạn chế lặp lại cùng pointcut/advice ở auto-proxy configuration.

### Một quy tắc ownership có ích

~~~text
Audit cross-cutting concern:
  CHỈ @AspectJ auto-proxy infrastructure sở hữu AuditAspect.

Custom per-bean proxy:
  CHỈ factory riêng sở hữu những advisors riêng, không thêm AuditAspect lần nữa.

Transactions:
  Spring transaction infrastructure sở hữu TransactionInterceptor.
  Không tự add lại TransactionInterceptor vào proxy thủ công trừ khi có
  một lý do được thiết kế và kiểm thử chặt chẽ.
~~~

Đừng xử lý duplicate audit bằng `@Order`: order sắp xếp advisors; nó **không xóa** advisors bị đăng ký hai lần.

## 10. Những trường hợp lặp advice là hợp lệ

### 10.1. Spring Retry cố tình lặp method

Nếu `@Retryable(maxAttempts = 3)` áp dụng cho method và hai attempts đầu thất bại, target có thể được gọi ba lần. `@Before` advice **bên trong** retry chain cũng có thể chạy ba lần; `@Around` nằm **bên ngoài** retry chain có thể chỉ vào một lần.

Do vậy counter ghi trong aspect có thể là **per-attempt**, không nhất thiết **per-business-request**. Để xác minh retry và transaction semantics, ghi thêm `RetryContext`, correlation ID, exception type và transaction boundary; không xóa retry advisor chỉ vì metrics đếm nhiều.

### 10.2. Method thực sự được gọi hai lần

Một controller gọi `service.validate()` trước rồi `service.submit()`; nếu pointcut `execution(* demo..*Service.*(..))` match cả hai, advice chạy hai lần đúng. Tương tự một request có hai handlers, hai events, hoặc application-level retries có thể tạo nhiều method invocations riêng.

Kiểm tra **method name + arguments + request correlation ID**. Chỉ dựa vào log “before” không đủ phân biệt hai method calls.

### 10.3. Một quanh-advice quyết định short-circuit hoặc re-invoke

`@Around`/AOP Alliance `MethodInterceptor` có thể gọi `proceed()` một lần, không gọi, hoặc gọi nhiều lần tùy logic. Ví dụ:

~~~java
@Around("execution(* demo..*Service.execute(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (TransientBusinessException ex) {
        return pjp.proceed(); // thử lại chủ động, có thể có side effects
    }
}
~~~

Đây là **snippet minh họa** cần imports `@Around`, `ProceedingJoinPoint`, class exception và Spring AOP configuration; không phải chương trình độc lập. Việc này có thể là thiết kế retry nhưng cũng dễ sai nếu operation không idempotent hoặc transaction chưa được reset giữa attempts.

### 10.4. Khác nhau giữa hai advice methods cùng một aspect

Một `@Around` và một `@Before` trong cùng aspect match cùng method sẽ cùng thực thi; nếu cả hai log “started”, đó có thể là chủ đích, không phải duplicate proxy. Hãy gắn label riêng cho từng advice method và kiểm tra `@Order`/advice precedence.

## 11. Rủi ro nghiệp vụ của double advice

| Advice bị lặp | Hậu quả có thể | Biện pháp kiểm soát |
| --- | --- | --- |
| Logging/audit `@Before` | Double audit record, metrics sai | Một nguồn đăng ký aspect, định danh advisor, idempotent audit nếu cần |
| Authorization advice | Kiểm tra quyền hai lần, overhead; có thể behavior lệch nếu state thay đổi giữa checks | Một method-security infrastructure có ownership rõ |
| `@Transactional` | Hai transaction interceptors; propagation/rollback semantics khó suy luận theo chain | Không gắn transaction advisor thủ công trùng; test transaction boundary |
| `@Cacheable` | Hai cache advice layers, key/cache lookup lồng nhau | Một cache policy chủ sở hữu, kiểm thử hit/miss |
| `@Retryable` | Attempts có thể bị nhân lên khi hai retry chains cùng match | Một retry policy, giới hạn attempts, idempotency và retry budget |
| Trace/metrics advice | Nhiều spans/timer recordings cho cùng logical operation | Định nghĩa scope (proxy-entry vs attempt vs target), tránh double-register |
| Custom notification/email advice | Gửi thông báo hai lần dù target chạy một lần | Outbox/deduplication, side effect theo business operation ID |
| Around advice đổi arguments/result | Biến đổi nhiều lần, ví dụ encode/transform lặp | Idempotent transform hoặc chỉ apply ở một boundary |

Tình huống nhạy cảm nhất là **advice có side effect**. Nếu duplicated audit advice tạo hai records, bạn có thể gặp duplicate dù `targetCalls=1`. Ngược lại, nếu target chứa HTTP POST hoặc thanh toán và retry khiến `targetCalls=2`, không được coi việc xóa một audit log là đã ngăn double payment. Hãy sử dụng correlation/idempotency key tại service boundary và integration test phù hợp.

## 12. Instrumentation để tìm đúng chỗ nhân đôi

Để kết luận bằng bằng chứng, trong môi trường test/diagnostics có thể ghi các trường:

~~~text
requestId         : rq-17
beanName          : orderService
method            : execute
proxyClass        : [runtime class]        (chỉ phục vụ debug)
advisorIdentifier : auditAspect.before
proxyLayer        : 0 / 1
attempt           : 1 / 2 / 3 (nếu dùng retry)
targetExecution   : 1
event             : before / after / error
~~~

Các giá trị trên là **ví dụ format log**, không phải output cố định của Spring. Một `requestId` hoặc `operationId` nên giữ nguyên suốt hành trình request, còn attempt số tăng khi thực sự retry. Nếu dùng MDC/trace context, chú ý async/reactive boundaries có thể cần truyền context riêng.

Có thể dùng một `AtomicInteger` test-only để đo lời gọi advice; ở **target thật** đo lần method execution bằng một counter khác. Cả hai nên reset cho từng test/correlation ID. Trong production, dùng metrics/tracing đã có và tránh biến instrumentation thành nguồn side effect mới.

### Phân biệt log duplicate với advice duplicate

Nếu advice có:

~~~java
System.out.println("audit begin");
~~~

và console chỉ xuất hiện hai dòng, vẫn chưa biết do `invoke()` chạy hai lần hay do logger được cấu hình nhiều appenders. Một phép đếm nguyên tử bên trong method advice và unit/integration test gọi một lần sẽ giúp loại trừ khả năng logging configuration nhân bản output.

Nếu log vào Kafka/DB trong advice, chính log sink có thể at-least-once delivery. Khi đó **số records trên sink** khác với **số lần interceptor.invoke()**; cần kết hợp cả hai phép đo.

## 13. Kiểm thử Spring-managed bean để bắt lỗi thực tế

Mẫu kiểm thử với Spring `AnnotationConfigApplicationContext`:

~~~java
try (var context =
         new AnnotationConfigApplicationContext(AopConfig.class)) {
    TaskApi bean = context.getBean(TaskApi.class);

    assertTrue(AopUtils.isAopProxy(bean));
    assertEquals("done A-1", bean.execute("A-1"));

    // Ngoài proxy identity, assert đúng adviceInvocationCount
    // và targetExecutionCount từ các test beans/recorders.
}
~~~

Snippet này giả định `AopConfig`, `TaskApi`, static JUnit imports và các recorder đã tồn tại, không phải chương trình độc lập. Mấu chốt là **test phải lấy chính bean mà caller production sẽ dùng**, không `new` một target bên ngoài context. Nếu bean được tạo qua `ProxyFactoryBean`, nhớ `getBean("id")` là proxy product, `getBean("&id")` là factory; đừng inspect nhầm object.

Một bộ regression tests đáng có:

1. Bean phải là Spring AOP proxy khi pointcut match.
2. Số proxy layers hợp với kiến trúc dự kiến (thường một đối với service bình thường).
3. Số **advisors cùng trách nhiệm** trên mỗi layer là đúng, không trùng ngoài ý định.
4. Một method có pointcut match → advice chạy đúng số lần yêu cầu.
5. Method không match → advice không chạy.
6. Một external proxy invocation → target chạy đúng số lần, ngoại trừ retry được khai báo rõ.
7. Khi method ném exception → `@AfterThrowing`/`@Around` có chính xác hành vi expected; không vô ý retry.
8. Khi đã bật Spring Retry → số attempts/transactions/side effects được kiểm tra riêng.
9. Khi bật caching → cache hit có thể không vào target; test không coi zero target execution là lỗi.
10. Khi thêm một XML/Java config hoặc scan package mới → test không bị tăng advisor count ngoài chủ đích.
11. Với async/reactive flows, test chờ hoàn tất và kiểm tra đúng thread/subscription/correlation context.
12. Với bean scoped/prototype, test không tự unwrap `TargetSource.getTarget()` khiến lifecycle thay đổi.

### Negative tests cho duplicate configuration

Một bài test hữu ích không chỉ khẳng định “một advice chạy một lần”, mà còn **cố ý thêm advisor hai lần** như ví dụ phần 6 để chứng minh test sẽ fail nếu cấu hình production vô tình duplicate. Sau đó khôi phục đúng một advisor và xác nhận counter trở lại một. Đây là cách kiểm chứng monitoring có đủ sức phát hiện regression.

## 14. Những cách sửa sai thường gặp

| Cách sửa thiếu chính xác | Vì sao không giải quyết gốc | Hướng sửa có bằng chứng |
| --- | --- | --- |
| Xóa ngẫu nhiên một `@EnableAspectJAutoProxy` | Auto-proxy creator có thể đã được hợp nhất; duplicate nằm ở advisors khác | Xác định thực tế outer/inner proxy, danh sách advisors, registrations |
| Bật `proxyTargetClass=true` | Chỉ đổi JDK/CGLIB strategy, không dedup advisors | Giữ proxy mode theo contract, sửa ownership advisor |
| Thêm `@Order(0)` cho aspect | Chỉ thay thứ tự thực thi, không xóa advisor | Bỏ duplicate bean/config/advisor registration |
| Xóa mọi retry annotation | Có thể phá chính sách xử lý transient failure | Đo attempts và chỉ sửa duplicate retry policy |
| Dựa vào `bean.getClass().getName()` | Generated proxy name không biểu diễn chain đầy đủ | `AopUtils`, `Advised`, `AopProxyUtils.getSingletonTarget` |
| Thấy hai log → kết luận double database write | Advice count khác target/DB commit count | Đo target calls và database state |
| Gọi `getTargetSource().getTarget()` để khám phá mọi layer | Dynamic targets có lifecycle, có thể tạo instance hay phá scope | Dùng `getSingletonTarget` / `ultimateTargetClass` trước |
| Unwrap ra raw target rồi gọi để “né double advice” | Bỏ qua transaction/cache/security advisors hợp lệ | Sửa cấu hình ở nguồn, tiếp tục gọi qua proxy |
| Thêm cờ `boolean alreadyLogged` vào singleton aspect | State giữa requests có thể rò và không thread-safe | Sửa chain/ownership, nếu cần dedupe dùng operation ID |
| Giữ hai aspect beans nhưng đổi message log khác | Chỉ che hiện tượng, hai advice vẫn chạy | Loại registration trùng hoặc tách trách nhiệm có chủ đích |
| Sửa `@Around` để luôn `proceed()` hai lần | Tạo duplicate target execution | Một `proceed()` mặc định; retry phải có policy/idempotency |
| Coi `getAdvisors().length > 1` là lỗi | Nhiều legitimate advisors cùng proxy là bình thường | Phân loại advisor role và method matcher |

## 15. Quy trình điều tra production theo thứ tự an toàn

1. **Xác định một logical operation duy nhất** bằng request/correlation ID, method name và argument đã được mask thông tin nhạy cảm.
2. **Đo riêng** số entrypoint invocations, advice invocations, target executions, remote side effects và DB commits.
3. **Xác minh bean reference đúng** tại caller; dùng `AopUtils.isAopProxy`.
4. **Liệt kê advisors trên proxy ngoài cùng** nếu bean hỗ trợ `Advised`; kiểm tra cùng role/pointcut có bị đăng ký hai lần không.
5. **Kiểm tra inner singleton target** qua `AopProxyUtils.getSingletonTarget`; nếu inner cũng là proxy, kiểm tra tiếp từng layer tĩnh.
6. **Truy ngược nguồn đăng ký**: `@Aspect` bean names, component scanning, `@Bean`, XML schema, `ProxyFactoryBean`, `ProxyFactory`, BeanPostProcessors.
7. **Loại trừ trường hợp hợp lệ**: retry/backoff, two methods match same pointcut, explicit repeated `proceed()`, multiple caller requests.
8. **Không tháo raw target để workaround**; giữ Spring security, transaction, caching và lifecycle.
9. **Thiết lập một owner cho mỗi cross-cutting concern**, xóa đúng registration/advisor gây trùng thay vì xóa một annotation tùy tiện.
10. **Chạy regression test** với cả positive/negative pointcuts, errors, retry và side effects; xác minh target count và advisor count độc lập.
11. **Quan sát hậu sửa trên môi trường thật**, chú ý Spring context hierarchies, refresh/redeploy và cấu hình khác nhau giữa profiles.
12. **Nếu còn khác biệt**, ghi rõ Spring Framework/Boot versions và cấu hình proxy strategy; không suy đoán theo ví dụ từ phiên bản khác.

**Thực hành tốt:** giữ mỗi một concern như audit, transaction, cache, retry có **một chủ sở hữu cấu hình** và test rõ vai trò. Spring có thể hợp nhất các auto-proxy infrastructures nhưng không thể suy ra rằng **mọi advice/advisor do ứng dụng đăng ký** cũng được tự động deduplicate theo mục đích nghiệp vụ.

## 16. Kết luận

Khi Spring advice xuất hiện nhiều lần, đầu tiên hãy tách ba hiện tượng: **nhiều proxy layers**, **nhiều advice entries trong cùng proxy**, và **nhiều target executions do retry/caller logic**. Chúng có thể cùng tạo hai dòng `before`, nhưng chỉ loại thứ ba nhất thiết có thể làm business target chạy hai lần.

`AopUtils` cho biết bean có phải Spring proxy; `Advised.getAdvisors()` giúp kiểm tra advisor chain từng layer; `AopProxyUtils.getSingletonTarget()` và `ultimateTargetClass()` hỗ trợ nhận diện nested singleton proxies mà không tùy tiện unwrap dynamic target sources. Kết hợp các API ấy với **counters ở advice và target**, truy nguồn bean/configuration và regression tests để sửa đúng chỗ. Không giải quyết bằng `@Order`, đổi JDK/CGLIB hoặc gọi raw target: những cách ấy không loại registration trùng và có thể phá các Spring advisors hợp lệ.

### Tham khảo Spring chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html) — các kiểu proxy, self-invocation, và cơ chế hợp nhất auto-proxy creator.
- [Spring Framework — Using the auto-proxy facility](https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html) — `BeanNameAutoProxyCreator`, `DefaultAdvisorAutoProxyCreator`.
- [Spring Framework — Creating AOP Proxies Programmatically](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html) — `ProxyFactory.addAdvice`, `addAdvisor`.
- [Spring Framework — Schema-based AOP](https://docs.spring.io/spring-framework/reference/core/aop/schema.html) — tránh trộn auto-proxy creators và thứ tự advice.
- [Spring Framework 6.1 — AopProxyUtils Javadoc](https://www.springframework.org/spring/docs/6.1.3/javadoc-api/org/springframework/aop/framework/AopProxyUtils.html).
- [Spring Framework 6.1 — Advised Javadoc](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/aop/framework/Advised.html).
- [Spring Framework — Advice API](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-009">Thứ tự khác kỳ vọng giữa nhiều aspect và advisor</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự khác kỳ vọng giữa nhiều aspect và advisor trong Spring AOP

## 1. Dấu hiệu: tất cả aspect đều chạy, nhưng trình tự không như tưởng tượng

Trong Spring AOP, có thể có nhiều `@Aspect` tự viết cùng áp dụng cho một service method. Phương thức đó còn có thể mang `@Transactional`, `@Cacheable` hoặc được các cơ chế Spring khác áp dụng interceptor. Khi đó, **thứ tự thực thi không đơn giản là thứ tự các class được scan, thứ tự khai báo @Bean, hay thứ tự các dòng @Before trong source code**.

Các dấu hiệu thường gặp:

- Log của aspect B xuất hiện trước aspect A dù A được khai báo trước B.
- Một `@AfterReturning` chạy trước lúc transaction commit, dù tên annotation nghe như "sau khi hoàn tất".
- Advice theo dõi hiệu năng không chạy khi phương thức được trả từ cache.
- Khi xảy ra exception, các đoạn `finally` được in theo thứ tự ngược với đoạn `before`.
- Hai advice cùng loại trong cùng một `@Aspect` có thứ tự khác kỳ vọng hoặc thay đổi sau khi refactor.
- Đổi `@Order` cho một aspect nhưng luồng thực tế không đổi, vì lời gọi không qua proxy, pointcut không match, advisor khác đã short-circuit, hoặc có nhiều lớp proxy.

Muốn debug chính xác, phải trả lời hai câu hỏi:

1. **Có những advisor nào thực sự nằm trên proxy và khớp invocation?**
2. **Chúng đứng ở đâu trong chuỗi interceptor, và từng interceptor có tiếp tục gọi `proceed()` không?**

Đây là vấn đề cụ thể của **Spring AOP runtime và các Spring infrastructure advisors**. Bài không đào sâu lý thuyết AspectJ ngoài phạm vi cần để giải thích Spring.

## 2. Quy tắc precedence mà Spring AOP áp dụng

Spring AOP sử dụng quy tắc ordering tương thích với mô hình của AspectJ:

- **Precedence cao hơn chạy trước khi đi vào** method.
- **Precedence cao hơn chạy sau cùng khi thoát ra** method, bởi nó nằm phía ngoài.
- Với `@Order(value)` hoặc `Ordered.getOrder()`, **giá trị số nhỏ hơn = precedence cao hơn**.
- Khi hai **aspect khác nhau** match cùng một join point mà không chỉ định order, thứ tự của chúng **không được bảo đảm**.
- Với các advisor/order bằng nhau, đừng dựa vào thứ tự đăng ký bean, component scanning hay sự trùng hợp của một lần chạy để xây business invariant.

Ví dụ ba advisor lần lượt có order 10, 100, 200:

```text
client
  -> Advisor(order=10): before
    -> Advisor(order=100): before
      -> Advisor(order=200): before
        -> target.method()
      <- Advisor(order=200): after
    <- Advisor(order=100): after
  <- Advisor(order=10): after
client
```

Bảng thứ tự:

| Advisor | Order | Lượt đi vào | Lượt đi ra |
| --- | ---: | --- | --- |
| A | 10 | Thứ nhất | Cuối cùng |
| B | 100 | Thứ hai | Thứ hai |
| C | 200 | Thứ ba | Thứ nhất |

`@Order(1)` **không có nghĩa advice sẽ là hành động cuối cùng**. Nó có nghĩa advice nằm ngoài trong mô hình nesting, nên chạy đầu lúc vào và cuối lúc ra nếu advice có phần xử lý sau `proceed()`.

## 3. @Around tạo cấu trúc lồng nhau, không phải một danh sách sự kiện phẳng

Giả sử hai Spring aspects đều có `@Around` match `demo.OrderService.place(..)`:

```java
@Aspect
@Order(10)
public class OuterAspect {
    @Around("execution(* demo.OrderService.place(..))")
    public Object wrap(ProceedingJoinPoint jp) throws Throwable {
        System.out.println("outer:before");
        try {
            return jp.proceed();
        } finally {
            System.out.println("outer:finally");
        }
    }
}
```

```java
@Aspect
@Order(20)
public class InnerAspect {
    @Around("execution(* demo.OrderService.place(..))")
    public Object wrap(ProceedingJoinPoint jp) throws Throwable {
        System.out.println("inner:before");
        try {
            return jp.proceed();
        } finally {
            System.out.println("inner:finally");
        }
    }
}
```

Hai đoạn trên là **snippets minh họa**, giả định class/import/beans và method target tồn tại. Khi service được gọi **qua proxy** và cả hai aspects thực sự match, thứ tự mô hình là:

```text
outer:before
inner:before
[target.place()]
inner:finally
outer:finally
```

Vì `try/finally`, phần `finally` cũng chạy khi method hoặc inner advisor ném exception. Không nên diễn giải các dòng `finally` thành "đã commit transaction", "đã lưu database thành công", hay "mọi downstream side effect đã hoàn tất" nếu không biết chính xác advisor transaction/cache nằm đâu.

Nếu `@Around` bên ngoài **không gọi `jp.proceed()`**, toàn bộ advisor bên trong và method target bị short-circuit. Nếu một cache advisor ở ngoài trả cached value, không có lý do gì inner audit advice buộc phải chạy; đó có thể là hành vi đúng.

## 4. Cấu hình @Order đúng trong Spring

Với `@AspectJ` style Spring AOP, đặt `@Order` ở **class aspect**:

```java
@Aspect
@Component
@Order(50)
public class MetricsAspect {
    @Around("execution(* demo..service.*.*(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long started = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long nanos = System.nanoTime() - started;
            // Record metric (không coi nanos là thời gian DB commit nếu
            // transaction advisor đứng ngoài aspect).
        }
    }
}
```

Snippet này không độc lập; cần Spring AOP config, `@ComponentScan` hoặc đăng ký `@Bean`, imports từ `org.aspectj.lang`, `org.aspectj.lang.annotation`, `org.springframework.core.annotation.Order` và `org.springframework.stereotype.Component`.

Bạn cũng có thể cho aspect class `implements org.springframework.core.Ordered` và triển khai `getOrder()`. Nếu dùng cả interface và annotation, phải hiểu precedence từ `Ordered` runtime; tốt nhất **chỉ có một nguồn order rõ ràng**. Không dùng `@Order` trên method advice để giả định nó là một contract ordering độc lập tương tự Spring aspect class ordering.

**Điểm đặc biệt của Spring:** annotation `@Order` có thể ảnh hưởng nhiều cơ chế sắp thứ tự bean. Nhưng với Spring AOP, quan trọng là advisor order thực sự được xây từ aspect và được xếp vào advisor chain, không phải chỉ nhìn annotation trên file Java.

## 5. Nhiều advice bên trong cùng một @Aspect

Spring Reference xác định precedence mặc định giữa **các loại advice khác nhau trong cùng một @Aspect**:

1. `@Around`
2. `@Before`
3. `@After`
4. `@AfterReturning`
5. `@AfterThrowing`

Đó là thứ tự **conceptual precedence**, không phải một danh sách tuyệt đối các log theo chiều thời gian. `@After` có tính chất **finally**, nên về mặt thời điểm thực thi nó thường chạy sau các advice `@AfterReturning` hoặc `@AfterThrowing` trong cùng aspect, dù conceptual precedence được liệt kê như trên.

Quan trọng hơn: nếu hai methods **cùng loại advice** (ví dụ hai `@Before`) nằm trong **cùng `@Aspect`** và đều match join point, **thứ tự giữa chúng không được bảo đảm**. Java reflection không cung cấp thứ tự khai báo method trong source như một contract để Spring dựa vào.

Ví dụ rủi ro:

```java
@Aspect
public class AmbiguousAspect {
    @Before("execution(* demo.TaskService.run(..))")
    public void validate() {
        // ...
    }

    @Before("execution(* demo.TaskService.run(..))")
    public void record() {
        // ...
    }
}
```

Không thể dựa vào việc `validate()` được viết trước `record()` để khẳng định nó luôn chạy trước. Nếu bắt buộc có thứ tự:

- Gom logic cùng một concern vào **một advice method** và gọi helper theo thứ tự minh bạch.
- Hoặc tách thành **hai class `@Aspect`**, gắn `@Order` khác nhau và integration-test qua Spring proxy.
- Tránh tạo hai advice cùng loại cùng pointcut rồi cố điều khiển thứ tự bằng cách đổi vị trí source code.

**Không nên nhầm XML schema-based AOP với @AspectJ-style annotations:** `<aop:aspect>` có quy tắc thứ tự nội bộ dựa trên thứ tự advice elements được khai báo trong XML; không suy rộng nguyên quy tắc đó sang các methods cùng loại trong Java `@Aspect`.

## 6. Transaction advisor: thứ tự ảnh hưởng boundary thực thi

`@Transactional` của Spring (proxy mode) sử dụng Spring transaction advice/interceptor. Hạ tầng này là một phần của chuỗi Spring AOP bên cạnh các custom `@Aspect`.

Có thể đặt order của transaction advisor:

```java
@Configuration
@EnableTransactionManagement(order = 100)
class TransactionConfig {
    // Cần PlatformTransactionManager bean phù hợp.
}
```

Đây là snippet cấu hình; không phải project độc lập. Nếu ta có custom aspect `@Order(50)` và custom aspect khác `@Order(150)` cùng match service method:

```text
order=50      OuterAspect: before
  order=100     TransactionInterceptor: mở/join giao dịch
    order=150     InnerAspect: before
                  target @Transactional method
    order=150     InnerAspect: after/finally
  order=100     TransactionInterceptor: commit/rollback
order=50      OuterAspect: after/finally
```

Với **transaction mới** được bắt đầu tại method này:

- `OuterAspect` đi vào khi giao dịch method chưa bắt đầu; khi đi ra thì transaction interceptor đã hoàn tất commit/rollback.
- `InnerAspect` đi vào sau khi transaction interceptor đã bắt đầu giao dịch; phần `finally` của nó chạy **trước** khi interceptor commit/rollback.
- `@AfterReturning` thuộc inner aspect không chứng minh transaction đã commit.
- Nếu caller đã có transaction, thì outer aspect cũng có thể thấy transaction active. `@Order` không tự biến `REQUIRED` thành `REQUIRES_NEW`.

Mặc định `@EnableTransactionManagement.order` là `Ordered.LOWEST_PRECEDENCE`. Nếu custom aspect là `@Order(1)` còn transaction giữ order mặc định, custom aspect thường nằm **ngoài** transaction advice. Muốn aspect chạy trong transaction, chỉ đặt `@Order(200)` mà không đổi order transaction thường **không đủ**, bởi 200 vẫn nhỏ hơn LOWEST_PRECEDENCE.

**Rollback và exception:** Inner aspect nuốt exception trước khi exception đến transaction interceptor có thể khiến transaction interceptor quan sát normal return và commit (trừ rollback-only/rules khác); outer aspect bắt exception sau khi transaction interceptor rollback không thể "đổi rollback thành commit" sau đó. Điều này giải thích vì sao thay advisor order đôi khi làm lộ lỗi nghiệp vụ chứ không chỉ đổi thứ tự log.

Khi thật sự cần xử lý sau commit, dùng transaction lifecycle mechanism như `@TransactionalEventListener(phase = AFTER_COMMIT)` hoặc `TransactionSynchronization`, không suy đoán từ `@AfterReturning`.

## 7. Caching advisor: thứ tự quyết định advice có chạy khi cache hit không

Spring Cache tạo caching advisor/interceptor cho `@Cacheable` khi `@EnableCaching` có hiệu lực. Có thể cấu hình:

```java
@Configuration
@EnableCaching(order = 100)
class CacheConfig {
    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("prices");
    }
}
```

Các imports phù hợp thuộc `org.springframework.cache`, `org.springframework.cache.annotation`, `org.springframework.cache.concurrent` và `org.springframework.context.annotation`. Đây là snippet minh họa. `ConcurrentMapCacheManager` chỉ phù hợp demo/cache local đơn giản, không tự có TTL hoặc distributed coherence.

Với outer logging aspect `@Order(50)` và inner trace aspect `@Order(150)`:

```text
Lượt cache miss:
  outer:before
    caching advisor: miss -> tiếp tục
      inner:before
        target @Cacheable method
      inner:after
    caching advisor: store result
  outer:after

Lượt cache hit:
  outer:before
    caching advisor: hit -> trả cached result, KHÔNG đi tiếp
  outer:after
```

Nếu bạn muốn **đếm mọi lời gọi client** kể cả cache hit, metrics aspect thường phải ở ngoài caching advisor. Nếu bạn muốn chỉ đo **chi phí thực sự tính toán trên cache miss**, aspect có thể ở trong caching advisor. Hai kết quả khác nhau đều có thể đúng; điều sai là giả định mọi aspect luôn chạy ở mọi cache hit.

**Order mặc định của `@EnableCaching`:** `Ordered.LOWEST_PRECEDENCE`, vì vậy hãy chỉ định `order` khi sự tương tác với custom aspect thực sự cần precedence ổn định.

## 8. Ví dụ Java 17 chạy được: hai @Aspect và một cache advisor

Project dưới đây kết hợp:

- `OuterAspect`: `@Order(50)`, ngoài cùng.
- Spring Cache advisor: `@EnableCaching(order=100)`, ở giữa cho `price`.
- `InnerAspect`: `@Order(150)`, trong cùng khi cache miss.
- Target `PriceServiceImpl` có `@Cacheable` trên method `price(String)` và method `fail(String)` ném exception.

Kết quả minh họa rõ **nested invocation**, **cache short-circuit** và **unwind trong trường hợp exception**. Mọi lời gọi đều lấy bean từ Spring context và đi qua proxy. Java 17 + Maven + Spring Framework 6.2.19 + JUnit 5; **không cần AspectJ compile-time hoặc load-time weaving**.

### 8.1. Layout

```text
spring-advisor-order-demo/
  pom.xml
  src/main/java/demo/
    App.java
    Events.java
    PriceService.java
    PriceServiceImpl.java
    OuterAspect.java
    InnerAspect.java
  src/test/java/demo/
    AdvisorOrderTest.java
```

### 8.2. pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>demo</groupId>
  <artifactId>spring-advisor-order-demo</artifactId>
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
      <version>1.9.24</version>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-test</artifactId>
      <version>6.2.19</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.11.4</version>
      <scope>test</scope>
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
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.2</version>
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

`spring-context` cung cấp infrastructure Spring Cache (cùng các phụ thuộc), `spring-aop` cung cấp proxy, `aspectjweaver` được dùng cho parser `@AspectJ`. JUnit Jupiter và `spring-test` phục vụ integration test.

### 8.3. Events.java

`src/main/java/demo/Events.java`:

```java
package demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Events {
    private final List<String> values = new CopyOnWriteArrayList<>();

    public void add(String event) {
        values.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(values);
    }

    public void clear() {
        values.clear();
    }
}
```

Recorder giúp test thứ tự qua giá trị chính xác, không lệ thuộc log DEBUG hay timestamps. Ứng dụng demo chạy tuần tự; không chia sẻ singleton recorder giữa nhiều test chạy song song mà không có isolation.

### 8.4. PriceService.java

`src/main/java/demo/PriceService.java`:

```java
package demo;

public interface PriceService {
    String price(String sku);
    String fail(String sku);
}
```

Client inject theo interface, giúp hoạt động với Spring JDK proxy mặc định.

### 8.5. PriceServiceImpl.java

`src/main/java/demo/PriceServiceImpl.java`:

```java
package demo;

import org.springframework.cache.annotation.Cacheable;

public class PriceServiceImpl implements PriceService {
    private final Events events;

    public PriceServiceImpl(Events events) {
        this.events = events;
    }

    @Override
    @Cacheable(cacheNames = "prices", key = "#p0")
    public String price(String sku) {
        events.add("target:price:" + sku);
        return "P-" + sku;
    }

    @Override
    public String fail(String sku) {
        events.add("target:fail:" + sku);
        throw new IllegalStateException("boom:" + sku);
    }
}
```

`@Cacheable` chỉ áp dụng cho `price`; `fail` không bị cache advisor chặn theo annotation. Cache key dùng `#p0` là tham số đầu tiên, không phụ thuộc debug parameter name metadata.

### 8.6. OuterAspect.java

`src/main/java/demo/OuterAspect.java`:

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(50)
public class OuterAspect {
    private final Events events;

    public OuterAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.PriceService.*(..))")
    public Object wrap(ProceedingJoinPoint pjp) throws Throwable {
        events.add("outer:enter");
        try {
            return pjp.proceed();
        } finally {
            events.add("outer:exit");
        }
    }
}
```

`@Order(50)` có precedence cao hơn caching advisor order 100. `OuterAspect` ghi event bất kể cache hit/miss; `finally` chạy cả khi target ném exception.

### 8.7. InnerAspect.java

`src/main/java/demo/InnerAspect.java`:

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(150)
public class InnerAspect {
    private final Events events;

    public InnerAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.PriceService.*(..))")
    public Object wrap(ProceedingJoinPoint pjp) throws Throwable {
        events.add("inner:enter");
        try {
            return pjp.proceed();
        } finally {
            events.add("inner:exit");
        }
    }
}
```

`InnerAspect` chỉ chạy khi phần chuỗi trước nó tiếp tục invocation. Với cache hit, caching interceptor thường trả ngay nên inner không được gọi. Với `fail`, không có caching operation, nên cả hai aspects vẫn chạy.

### 8.8. App.java

`src/main/java/demo/App.java`:

```java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class App {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @EnableCaching(order = 100)
    public static class Config {
        @Bean
        Events events() {
            return new Events();
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("prices");
        }

        @Bean
        PriceService priceService(Events events) {
            return new PriceServiceImpl(events);
        }

        @Bean
        OuterAspect outerAspect(Events events) {
            return new OuterAspect(events);
        }

        @Bean
        InnerAspect innerAspect(Events events) {
            return new InnerAspect(events);
        }
    }

    private static void printAndClear(Events events) {
        for (String event : events.snapshot()) {
            System.out.println(event);
        }
        events.clear();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            PriceService service = context.getBean(PriceService.class);
            Events events = context.getBean(Events.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));

            System.out.println("-- first call --");
            System.out.println("first=" + service.price("A"));
            printAndClear(events);

            System.out.println("-- cache hit --");
            System.out.println("second=" + service.price("A"));
            printAndClear(events);

            System.out.println("-- failed call --");
            try {
                service.fail("Z");
            } catch (IllegalStateException ex) {
                System.out.println("caught=" + ex.getMessage());
            }
            printAndClear(events);
        }
    }
}
```

Config bật cả `@EnableAspectJAutoProxy` lẫn `@EnableCaching(order=100)` trong cùng ApplicationContext. Các aspects được Spring tạo bằng `@Bean`, không cần component scan.

### 8.9. Chạy ứng dụng và kết quả

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
```

Output `System.out` dự kiến (không tính log nền của Maven/Spring):

```text
proxy=true
-- first call --
first=P-A
outer:enter
inner:enter
target:price:A
inner:exit
outer:exit
-- cache hit --
second=P-A
outer:enter
outer:exit
-- failed call --
caught=boom:Z
outer:enter
inner:enter
target:fail:Z
inner:exit
outer:exit
```

Lưu ý code in `first=...` và `second=...` **sau** khi method proxy đã kết thúc, nên log event được ghi trước đó nhưng chỉ được hiển thị tiếp theo bởi `printAndClear`. Thứ tự **event bên trong** mới mô tả chính xác sự nesting; không lấy vị trí dòng `first=P-A` làm bằng chứng nó chạy trước outer aspect.

Kết quả giải thích:

1. First call: cache miss → outer + inner + target, sau đó unwind theo thứ tự ngược.
2. Second call: cache hit → outer chạy, cache trả value mà không gọi inner hoặc target.
3. Failed call: target ném `IllegalStateException`; inner/outer `finally` vẫn chạy, caller bắt đúng exception sau khi proxy hoàn tất.
4. `proxy=true` xác nhận Spring đã trả AOP proxy; không cần AspectJ weaving.

## 9. Viết regression tests để khóa đúng thứ tự

`src/test/java/demo/AdvisorOrderTest.java`:

```java
package demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = App.Config.class)
class AdvisorOrderTest {
    @Autowired
    PriceService service;

    @Autowired
    Events events;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        events.clear();
        cacheManager.getCache("prices").clear();
    }

    @Test
    void aopProxyIsPresent() {
        assertTrue(AopUtils.isAopProxy(service));
    }

    @Test
    void cacheMissRunsBothAspectsInDeclaredOrder() {
        assertEquals("P-A", service.price("A"));
        assertEquals(List.of(
            "outer:enter",
            "inner:enter",
            "target:price:A",
            "inner:exit",
            "outer:exit"
        ), events.snapshot());
    }

    @Test
    void cacheHitShortCircuitsInnerAspectAndTarget() {
        assertEquals("P-A", service.price("A"));
        events.clear();

        assertEquals("P-A", service.price("A"));
        assertEquals(List.of("outer:enter", "outer:exit"),
                events.snapshot());
    }

    @Test
    void failureUnwindsBothFinallyBlocks() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.fail("Z")
        );

        assertEquals("boom:Z", ex.getMessage());
        assertEquals(List.of(
            "outer:enter",
            "inner:enter",
            "target:fail:Z",
            "inner:exit",
            "outer:exit"
        ), events.snapshot());
    }
}
```

Chạy:

```bash
mvn -q test
```

Nếu dự án được đặt đúng file/dependency theo hướng dẫn, kết quả kỳ vọng là **4 tests pass, 0 failures/errors**, process exit code 0. Để xem số tests từ Surefire:

```bash
mvn test
```

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

Đây là kết quả **dự kiến**, không phải báo cáo đã thực thi Maven trong một môi trường cụ thể.

Các test tránh khẳng định thứ tự không được bảo đảm: chúng chỉ assert sequence đã chủ động cấu hình với ba precedence khác nhau `50`, `100` và `150`. Trước mỗi test, `Events` và cache được reset; nếu bật parallel tests trên cùng singleton context, phải bổ sung test isolation thích hợp.

## 10. Debug advisor chain thực tế trên Spring bean

Khi hiện tượng không giống ví dụ, hãy inspect **chính bean reference mà client gọi**, không chỉ xem source code của aspects. Dùng:

```java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;

Object bean = context.getBean("priceService");

System.out.println("isAopProxy=" + AopUtils.isAopProxy(bean));

if (bean instanceof Advised advised) {
    Advisor[] advisors = advised.getAdvisors();
    for (int i = 0; i < advisors.length; i++) {
        Advisor advisor = advisors[i];
        String order = advisor instanceof Ordered ordered
                ? String.valueOf(ordered.getOrder())
                : "not exposed via Ordered";
        System.out.println(i + ": " + advisor + " order=" + order);
    }
}
```

Đây là snippet debug cần biến `context` trong scope, không phải chương trình chạy độc lập. Mục đích là xác định:

- Có thực sự là Spring proxy không?
- Có advisor của từng custom aspect và caching/transaction infrastructure không?
- Vị trí tương đối của advisors trong chain là gì?
- Bean có bị wrap thêm bởi custom `ProxyFactory` hoặc proxy nhiều tầng không?
- Method đang test có thực sự match pointcut của advisor hay không?

**Giới hạn của cách đọc danh sách:** `Advised.getAdvisors()` liệt kê advisor gắn trên proxy nhưng không có nghĩa **mọi advisor đều chạy trên mọi method**. Advisor có thể chỉ match một số method; một advisor phía ngoài có thể short-circuit, nên các advisors phía trong không được gọi. `order="not exposed"` không có nghĩa advisor không có thứ tự; có thể thứ tự do hạ tầng xử lý theo cách khác. Đừng coi `toString()` của advisor là API ổn định để parse ra semantics.

## 11. Khi thứ tự nhìn thấy khác @Order, kiểm tra gì trước?

### 11.1. Pointcut không match invocation hiện tại

Aspect thứ hai có `@Order(10)` nhưng pointcut chỉ match method khác. Khi gọi method đang quan sát, aspect đó không chạy, nên trông giống "Spring bỏ qua @Order". Hãy kiểm tra signature, target class và interface; dùng positive/negative integration tests.

### 11.2. Lời gọi không qua proxy

`this.inner()`, object tạo bằng `new` hoặc reference đến target gốc không đi qua advisor chain. `@Order` chỉ sắp thứ tự của **advisors đã được thực thi qua proxy**, không cứu được invocation bypass.

### 11.3. Spring cache hoặc security short-circuit

`@Cacheable` có thể trả cached result, method security có thể từ chối invocation trước target, một `@Around` custom có thể không gọi `proceed()`. Advice nằm phía trong sẽ không chạy. Đây có thể là behavior đúng.

### 11.4. Các advice cùng loại trong một aspect

Hai `@Before` cùng `@Aspect` có thứ tự không xác định; `@Order` trên class aspect chỉ sắp precedence **so với aspects khác**, không cho bạn hai slot precedence độc lập giữa từng advice cùng loại.

### 11.5. Bằng nhau hoặc để default

Nếu `@Order` đều là 100, hoặc custom aspect ở default `LOWEST_PRECEDENCE` giống transaction/cache infrastructure, không được ngầm hiểu thứ tự theo component scan. Đặt **giá trị order khác nhau có chủ đích** cho các thành phần mà correctness phụ thuộc.

### 11.6. Có nhiều loại proxy hoặc auto-proxy configuration

Hệ thống có `@EnableAspectJAutoProxy`, transaction/cache auto-proxy advisors và các custom `BeanPostProcessor`/`ProxyFactory`. Trong cấu hình thông thường Spring có thể gom advisors thành **một proxy**, nhưng một số cấu hình thủ công có thể tạo nested proxies. Ở trường hợp nested proxy, cần debug từng lớp; `@Order` trong một chain không tự sắp các proxy lồng nhau như một danh sách đơn giản.

### 11.7. AspectJ weaving không giống Spring proxy advisor ordering

Nếu đồng thời dùng Spring proxy và AspectJ CTW/LTW, các woven advice có thể chạy bên ngoài hoặc bên trong proxy boundary tùy join point/call graph. `@Order` của Spring AOP không điều khiển một cách tổng quát tất cả woven aspects. Đừng trộn hai cơ chế rồi suy diễn một global advisor order.

### 11.8. Async và thread boundary

`@Async` có thể chuyển thực thi sang executor thread; log từ nhiều threads khác nhau có thể xen kẽ. Không suy từ timestamps hoặc interleaving giữa threads để kết luận Spring đã phá thứ tự đồng bộ. Khi test, kiểm tra thread name, future completion và cơ chế async interceptor/configuration.

## 12. Quy trình chẩn đoán theo từng bước

1. **Khoanh đúng method.** Ghi FQCN, interface method, annotation và pointcut thực sự match.
2. **Lấy đúng bean reference.** Đảm bảo client nhận bean từ Spring IoC, không phải `new` hoặc raw target.
3. **Xác minh proxy.** Dùng `AopUtils.isAopProxy` và xác định JDK/CGLIB nếu cần; nhớ self-invocation không được intercept.
4. **Liệt kê advisors.** Với `Advised`, xem `getAdvisors()`, loại advisor, thứ tự tương đối và pointcut match. Đừng quên infrastructure advisors của cache/transaction/security.
5. **Đối chiếu order configuration.** So `@Order`/`Ordered` của custom aspects và các tham số `order` trên `@EnableCaching`, `@EnableTransactionManagement`.
6. **Vẽ sơ đồ nesting.** Chỉ ra vị trí advice đang chạy trước và sau `proceed()`. Nếu có cache/security, xem nơi có thể short-circuit.
7. **Chạy ba đường kiểm thử.** Case bình thường, case ngoại lệ và case cache hit/short-circuit nếu có; assert cả log events lẫn kết quả nghiệp vụ.
8. **Chỉ thay cấu hình có kiểm soát.** Đổi một order value, kiểm tra event sequence; không thay hàng loạt @Aspect/@Order rồi đoán.
9. **Bảo vệ bằng regression test.** Khóa contract quan trọng: audit trước transaction hay sau commit, metrics có đo cache hits không, auth có chạy trước side effect không.
10. **Kiểm tra proxy layering và threads** nếu logs vẫn không giải thích được.

Một câu trả lời đúng trong Spring không phải luôn "cho @Order nhỏ nhất". Đó là **vẽ được chain thực tế và chứng minh concern cần nằm ở đâu**.

## 13. Thứ tự giữa validation, security, cache và transaction

Không phải hạ tầng nào cũng nên đặt order theo cùng một mục đích:

| Concern | Vị trí thường mong muốn | Điều cần lưu ý |
| --- | --- | --- |
| Validation đầu vào không cần DB | Trước khi mở transaction (nếu có thể) | Tránh tốn transaction cho request invalid |
| Authorization | Trước operation nhạy cảm | Advisor security có thể deny và short-circuit |
| Đo thời gian tổng cộng | Bên ngoài transaction/cache | Bao gồm cả cache hit và chi phí interceptor |
| Đo thời gian query thật | Trong cache advisor | Cache hit sẽ không đi vào phần đo |
| Audit nếu muốn thông tin transaction đang active | Trong transaction advisor | `@AfterReturning` bên trong chưa chắc sau commit |
| Thông báo chỉ khi commit thành công | Transaction synchronization hoặc transactional event | Không thay bằng một `@AfterReturning` đặt "sau" theo suy đoán |
| Retry | Thứ tự so với transaction phải được thiết kế | Retry bên ngoài transaction có thể tạo transaction mỗi attempt; idempotency vẫn quan trọng |

Spring Security method authorization advisors, Spring cache/transaction infrastructure và custom `@Aspect` có chính sách order riêng. Một số infrastructure dùng `PriorityOrdered` hoặc tổ chức proxy/chain đặc thù; **đừng giả định một con số @Order ở mọi loại object tạo ra đúng cùng semantics trong mọi framework module**. Hãy kiểm tra tài liệu của module, advisors thực tế và behavior tests trên context dự án.

## 14. Những anti-pattern làm debugging khó hơn

- **Dựa vào component scan order:** scan/bean registration không phải hợp đồng execution order của advice.
- **Tạo nhiều `@Before` cùng loại trong một aspect rồi bắt thứ tự:** reflection không đảm bảo source declaration order.
- **Gán nhiều giá trị order bằng nhau cho logic cần precedence:** dễ thay đổi khi refactor hoặc bật thêm infrastructure.
- **Đặt @Order trên advice method và tưởng nó tách được precedence khỏi class aspect:** có thể không được Spring AOP dùng như bạn kỳ vọng.
- **Đọc log `@AfterReturning` và coi như DB đã commit:** transaction advisor có thể đứng ngoài.
- **Nhầm cache hit với aspect bị vô hiệu:** cache interceptor có thể short-circuit phần phía trong.
- **Nuốt exception trong `@Around`:** có thể đổi rollback semantics và khiến outer advisor nhận kết quả bình thường.
- **Không gọi `proceed()` ngoài ý muốn:** mọi advisor/target phía trong biến mất khỏi log.
- **Thử sửa bằng `proxyTargetClass=true`:** đổi loại proxy không tự sắp xếp order hoặc thay self-invocation.
- **Chỉ unit test method của aspect mà không dựng Spring context:** không kiểm tra được advisors và execution chain thật.

## 15. Checklist QA sau khi sửa thứ tự

- [ ] Từng aspect là Spring bean, AOP được bật ở đúng ApplicationContext.
- [ ] Method thực sự được gọi qua Spring proxy và pointcut match.
- [ ] `@Order`/`Ordered` của từng aspect rõ ràng; các order cần phân biệt không trùng nhau.
- [ ] `@EnableCaching.order` và `@EnableTransactionManagement.order` phù hợp với ý định thiết kế.
- [ ] Danh sách `Advised.getAdvisors()` được kiểm tra nếu cần giải thích thực tế.
- [ ] Sơ đồ nesting ghi rõ điểm bắt đầu/kết thúc transaction và cache hit/short-circuit.
- [ ] Có test về thứ tự vào/ra cho case bình thường; không đoán dựa trên dòng source code.
- [ ] Có test exception/finally và kiểm chứng exception vẫn tới caller đúng contract.
- [ ] Có test cache hit/miss nếu caching có thể bỏ qua advice/target.
- [ ] Có negative test pointcut khi cần tránh apply quá rộng.
- [ ] Với JDK proxy/CGLIB, kiểm tra method visibility, type và self-invocation.
- [ ] Với async, tách kiểm tra thread scheduling khỏi thứ tự synchronous advisors.
- [ ] Không bật cả AspectJ weaving và Spring proxy cho cùng custom aspect khi không chủ động xử lý duplicate interception.

## 16. Câu hỏi tự kiểm tra

**1. Giữa `@Order(5)` và `@Order(50)`, aspect nào vào trước, ra sau?**  
Aspect order=5 có precedence cao hơn: vào trước và thoát ra sau nếu nó bao quanh invocation.

**2. Vì sao `@Order(150)` có thể chạy trong cache advisor `order=100` nhưng không chạy trên cache hit?**  
Cache advisor có precedence cao hơn. Khi cache hit, nó có thể trả cached value mà không gọi `proceed()` đến inner aspect/target.

**3. Vì sao `@AfterReturning` chạy rồi mà transaction vẫn có thể chưa commit?**  
Vì advice có thể nằm bên trong transaction interceptor; commit xảy ra sau khi interceptor nhận normal return.

**4. Có thể dùng source order của hai `@Before` cùng `@Aspect` để quy định thứ tự không?**  
Không. Thứ tự giữa advice cùng loại trong một aspect không được đảm bảo; gom logic hoặc tách aspect rồi đặt `@Order`.

**5. Nếu custom aspect `@Order(1)` mà `@Transactional` để order mặc định, custom aspect thường ở đâu?**  
Nó thường ở ngoài transaction advisor, vì transaction order mặc định là `Ordered.LOWEST_PRECEDENCE`.

**6. `AopUtils.isAopProxy=true` đã đủ để khẳng định inner aspect được gọi không?**  
Không. Cần biết advisor nào có mặt, pointcut match, có lời gọi qua proxy và có interceptor nào short-circuit không.

**7. Lỗi thứ tự có thể xuất hiện dù @Order đúng không?**  
Có. Ví dụ self-invocation, không match pointcut, cache hit, nhiều tầng proxy hoặc interleaving async threads; khi đó vấn đề không nằm ở số order đơn thuần.

## Tài liệu chính thức của Spring

- Spring Framework — Declaring Advice, Advice Ordering: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework 6.2 — Using @Transactional (order, proxy mode): https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html
- Spring Framework — Advising Transactional Operations: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/applying-more-than-just-tx-advice.html
- Spring Framework — Declarative Caching (order): https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html
- Spring Framework — AOP API / Advisors: https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-010">Ngoại lệ bị che khuất hoặc giá trị trả về bị đổi bởi @Around</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ bị che khuất hoặc giá trị trả về bị đổi bởi @Around trong Spring AOP

## 1. Nhận diện đúng sự cố

Một service Spring có thể chạy bình thường khi gọi trực tiếp nhưng lại có hành vi khó hiểu khi được gọi qua bean do Spring cung cấp:

- **Service ném ngoại lệ nhưng caller không thấy ngoại lệ**; thay vào đó caller nhận **null**, một chuỗi mặc định hoặc một kết quả thành công giả.
- **Service trả đúng dữ liệu nhưng caller nhận giá trị khác** sau khi Spring AOP advice chạy.
- **Method đích không chạy** vì advice bỏ qua **ProceedingJoinPoint.proceed()**; caller vẫn nhận một kết quả từ advice.
- **Ngoại lệ gốc bị thay thế** bởi ngoại lệ do code logging, cleanup, chuyển kiểu hoặc logic fallback gây ra.
- Test unit của service vẫn pass, nhưng khi service chạy trong **ApplicationContext**, hành vi qua proxy lại khác.

Trọng tâm chẩn đoán của bài là **@Around trong Spring Aspect**: cách Spring AOP gọi advice qua proxy, cách advice truyền kết quả và ngoại lệ từ **proceed()**, cách kiểm chứng bằng test và cách sửa lỗi mà vẫn giữ hợp đồng method.

**Nguyên tắc quan trọng:** Với Spring AOP, lời gọi tới một method được advice có thể trả về **giá trị từ @Around**, chứ không nhất thiết là giá trị mà target method đã tạo. Nếu **@Around** nuốt ngoại lệ thì lời gọi có thể hoàn tất bình thường dù target đã thất bại.

Bài không đi sâu lý thuyết AOP chung; các khái niệm được giải thích theo hành vi của Spring bean và proxy.

## 2. @Around có thể thay đổi kết quả như thế nào?

Một advice thường có cấu trúc:

~~~java
@Aspect
public class ObservationAspect {
    @Around("execution(* demo.service.OrderService.submit(..))")
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {
        Object value = pjp.proceed();
        return value;
    }
}
~~~

Đây là **mảnh code minh họa**, không phải một ứng dụng Java độc lập. Spring phải đăng ký aspect dưới dạng bean và bật Spring AOP auto-proxy, hoặc có cấu hình tương đương. **proceed()** đưa điều khiển tới advisor kế tiếp, rồi tới target method khi không còn advisor ở phía trong.

Luồng đơn giản:

~~~text
Caller
  |
  v
Spring AOP proxy
  |
  v
@Around: before
  |
  +-- pjp.proceed()
        |
        +-- advisor tiếp theo (nếu có)
        |
        +-- target method
               |
               +-- trả value --------------+
               |                           |
               +-- ném Throwable ----------|-------+
                                           |       |
                                  @Around xử lý    |
                                           |       |
                    return value mới / value gốc   |
                    hoặc throw exception mới / gốc
                                           |
                                           v
                                         Caller
~~~

Một advice **@Around** được phép:

1. Gọi **proceed()** đúng một lần và trả về đúng kết quả.
2. Gọi **proceed()** rồi **thay đổi giá trị trả về**.
3. Bắt Throwable từ **proceed()** và **ném tiếp** hoặc **trả fallback**.
4. **Không gọi proceed()**, nghĩa là chặn hoàn toàn lời gọi đến phần còn lại của chuỗi/target.
5. Gọi **proceed() nhiều lần** (ví dụ một cơ chế retry có chủ đích).

Đây đều là khả năng của API. Trong một advice chỉ làm logging, tracing hoặc đo thời gian, thường mong muốn **proceed một lần, trả nguyên kết quả và ném nguyên ngoại lệ**. Cần có test để bảo vệ hợp đồng này.

### 2.1. Bốn thành phần phải theo dõi

| Thành phần | Vai trò | Dấu hiệu sai |
|---|---|---|
| **ProceedingJoinPoint.proceed()** | Tiếp tục chuỗi interceptor và gọi target khi đến lượt | Bị bỏ quên, gọi nhiều lần ngoài ý định |
| **Object result** | Kết quả nhận từ phần còn lại của chain | Bị thay bằng default, wrapper hoặc null |
| **Throwable exception** | Lỗi được ném từ target hoặc advisor nằm bên trong | Bị catch nhưng không rethrow; bị wrap sai hoặc thay bởi lỗi khác |
| **return/throw của @Around** | Kết quả cuối cùng trả về caller đang ở ngoài advice | Không phản ánh hợp đồng service ban đầu |

Đừng chỉ đặt breakpoint trong target rồi thấy target trả đúng để kết luận Spring trả đúng. Cần đặt breakpoint tại **dòng return của advice**, bởi caller nhận giá trị của toàn chuỗi proxy.

## 3. Các mẫu code che khuất ngoại lệ

### 3.1. Catch rồi trả null

~~~java
@Around("execution(* demo.service.OrderService.submit(..))")
public Object broken(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (Exception ex) {
        log.error("submit failed", ex);
        return null; // Ngoại lệ đã bị chuyển thành kết quả bình thường.
    }
}
~~~

Ở đây **catch** không đưa exception ra ngoài. Nếu target ném **IllegalStateException**, caller có thể nhận **null** mà không biết thao tác thất bại. Với method trả primitive như **int**, proxy/advice có thể phát sinh lỗi khác do không thể dùng null như primitive hợp lệ.

**Cách sửa với advice chỉ quan sát lỗi:**

~~~java
@Around("execution(* demo.service.OrderService.submit(..))")
public Object safe(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (Throwable ex) {
        log.error("submit failed", ex);
        throw ex; // Giữ nguyên đối tượng throwable.
    }
}
~~~

Dùng **throw ex** khi muốn bảo toàn loại exception, cause, stack trace và identity của ngoại lệ gốc; tránh **throw new RuntimeException(ex.getMessage())** vì làm mất type, cause nếu không truyền vào, và ngữ cảnh xử lý.

**Lưu ý về phạm vi catch:** Trong Spring AOP, **proceed()** có thể ném **Throwable**, nên chữ ký advice thường là **throws Throwable**. Bắt **Throwable** để log rồi **ném lại** là một pattern quan sát đầy đủ. Không biến mọi **Error** thành thành công. Nếu chỉ có lý do bắt các Exception thông thường, dùng kiểu bắt phù hợp nhưng phải bảo đảm Throwable ngoài phạm vi catch vẫn tự propagate.

### 3.2. Catch và trả fallback sai hợp đồng

~~~java
@Around("execution(* demo.service.OrderService.submit(..))")
public Object swallow(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException ex) {
        return "SUCCESS"; // Dễ tạo tín hiệu thành công giả.
    }
}
~~~

Một fallback đôi khi hợp lệ, chẳng hạn cho hệ thống đọc cache không quan trọng. Nhưng nếu service cam kết báo thất bại cho giao dịch thanh toán, trả **SUCCESS** gây sai nghĩa nghiệp vụ. Phải xác định rõ lỗi nào được fallback, giá trị fallback có tương thích hợp đồng và caller có nhận được tín hiệu thất bại hay không.

Nếu hợp đồng yêu cầu chuyển một ngoại lệ kỹ thuật thành ngoại lệ nghiệp vụ, hãy chuyển có chủ đích và giữ cause:

~~~java
catch (RemoteTimeoutException ex) {
    throw new PaymentUnavailableException("Payment gateway unavailable", ex);
}
~~~

Đây là **mảnh minh họa**: hai lớp exception tùy thuộc dự án, không phải API có sẵn của Java/Spring. Tránh ghi chúng vào dự án ví dụ ở phần dưới nếu chưa khai báo class tương ứng.

### 3.3. Return trong finally lấn át exception

~~~java
@Around("execution(* demo.service.OrderService.submit(..))")
public Object brokenFinally(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } finally {
        return "DEFAULT"; // Sai: ghi đè cả return và Throwable đang thoát ra.
    }
}
~~~

**return** trong **finally** có thể che khuất kết quả hoặc exception đang được truyền ra. Nếu target ném lỗi, **finally** thực thi trước khi exception ra ngoài; **return "DEFAULT"** khiến exception không tới caller.

Thay bằng cleanup không có **return** hoặc **throw**:

~~~java
@Around("execution(* demo.service.OrderService.submit(..))")
public Object timed(ProceedingJoinPoint pjp) throws Throwable {
    long start = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - start;
        metrics.recordDuration(elapsed); // metrics là collaborator minh họa.
    }
}
~~~

**Điểm cần cẩn trọng:** Nếu chính **metrics.recordDuration(...)** ném lỗi trong **finally**, lỗi đó có thể **thay thế** exception gốc hoặc phá kết quả thành công. Với metric best-effort, cần cô lập lỗi của hệ thống quan sát, ví dụ catch riêng quanh thao tác ghi metric và xử lý theo policy. Không blanket catch toàn bộ lỗi nghiệp vụ.

### 3.4. Ném ngoại lệ mới mà không giữ cause

~~~java
catch (Exception ex) {
    throw new IllegalStateException("Operation failed");
}
~~~

Cách này làm mất **cause**, khiến debug khó tìm stack trace của lỗi từ target. Nếu thực sự cần chuẩn hóa exception:

~~~java
catch (Exception ex) {
    throw new IllegalStateException("Operation failed", ex);
}
~~~

Hai cách đều **thay type** và caller có thể thấy khác trước. Chỉ chuẩn hóa exception theo một API contract rõ ràng, không làm vậy ngẫu nhiên trong cross-cutting advice.

## 4. Các mẫu làm đổi giá trị trả về

### 4.1. Quên trả kết quả của proceed()

~~~java
@Around("execution(* demo.service.OrderService.submit(..))")
public Object brokenReturn(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    log.debug("Target returned {}", result);
    return "OK"; // Caller không nhận result.
}
~~~

Nếu target trả **"ORDER:123"**, caller nhận **"OK"**. Unit test target có thể vẫn hoàn toàn đúng. Với logging-only advice, sửa thành **return result**.

### 4.2. Khai báo advice trả void

~~~java
@Around("execution(* demo.service.OrderService.submit(..))")
public void brokenVoid(ProceedingJoinPoint pjp) throws Throwable {
    pjp.proceed();
}
~~~

Theo cơ chế Spring @AspectJ, **@Around** nên khai báo **Object** làm kiểu trả về. Nếu advice trả **void**, kết quả được caller quan sát qua chuỗi advice có thể thành **null** thay vì kết quả của target. Khi target trả primitive, hậu quả có thể là lỗi tại proxy/caller; khi target trả reference type, caller có thể nhận **null** không mong đợi.

Sửa đúng:

~~~java
@Around("execution(* demo.service.OrderService.submit(..))")
public Object observe(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
~~~

### 4.3. Thay kiểu trả về không tương thích

~~~java
@Around("execution(* demo.service.AmountService.calculate(..))")
public Object brokenType(ProceedingJoinPoint pjp) throws Throwable {
    pjp.proceed();
    return "100"; // Nếu method khai báo long, đây là String, không phải long.
}
~~~

Đây là mảnh minh họa giả định **AmountService.calculate(...)** trả **long**. Giá trị String không thể thay thế **Long/long** đúng hợp đồng; lời gọi có thể thất bại do cast/unboxing hoặc cơ chế chuyển kiểu của proxy. Với **Optional<T>**, trả thẳng **T** thay vì **Optional<T>** cũng sai kiểu. Với **ResponseEntity<T>**, trả **T** thay vì **ResponseEntity<T>** làm hỏng hợp đồng ở lớp web.

Quy tắc cho advice thuần quan sát: **return pjp.proceed()**. Nếu advice cần biến đổi kết quả, phải thiết kế rõ kiểu, điều kiện biến đổi, invariant và test contract.

### 4.4. Return đúng kiểu nhưng sai ý nghĩa

Các trường hợp khó phát hiện hơn kiểu không tương thích:

- Target trả danh sách 3 phần tử, advice vô tình filter còn 2.
- Target trả **false**, advice trả **true**.
- Target trả identifier **A**, advice gắn prefix thành **B** nhưng client dùng identifier làm khóa.
- Target trả **Page<T>**, advice thay bằng list thiếu thông tin pagination.

Tất cả vẫn có thể biên dịch và chạy, nhưng nghiệp vụ sai. Vì vậy test phải assert **giá trị chính xác**, không chỉ **assertNotNull** hoặc kiểm tra class của kết quả.

## 5. Dấu hiệu để tách lỗi @Around khỏi lỗi proxy, pointcut

| Quan sát | Nghi vấn hợp lý |
|---|---|
| Gọi **new Service()** trả đúng, gọi qua Spring bean trả khác | Có advice/proxy, interceptor hoặc layer khác can thiệp |
| Target có log **throw** nhưng controller nhận kết quả | @Around bắt lỗi rồi trả fallback; cũng có thể layer ngoài xử lý ngoại lệ |
| Target trả đúng nhưng caller nhận **null** | @Around trả null/void hoặc đổi kiểu; cũng xem serialization/mapper ở layer ngoài |
| Không thấy target chạy, caller vẫn nhận giá trị | Có advice bỏ qua **proceed()**, short-circuit/caching hoặc caller chưa vào đúng target |
| Ngoại lệ class/cause bị đổi | @Around wrap/replace, finally ném lỗi mới hoặc cơ chế exception translation khác |
| Advice không chạy khi service tự gọi method | Self-invocation bỏ qua Spring proxy; không phải return masking của advice |
| Unit test gọi advice trực tiếp pass, integration test fail | Khác biệt ở proxy wiring, chuỗi advisor, context hoặc actual invocation |

Đừng vội thay pointcut thành biểu thức rộng hơn. Trước tiên xác nhận method được advice có thật sự được gọi **qua Spring proxy**.

## 6. Ví dụ hoàn chỉnh: so sánh @Around an toàn và @Around gây lỗi

Ví dụ sử dụng **Java 17+**, **Spring Framework 6.2.0**, **JUnit Jupiter 5.11.3**, **AspectJ Weaver 1.9.22.1** và Maven. Hai **ApplicationContext tách biệt** dùng cùng target service, nhưng đăng ký hai aspect khác nhau:

- **SafeAroundAspect**: quan sát kết quả và exception, **trả nguyên kết quả** hoặc **ném lại exception**.
- **FaultyAroundAspect**: cố tình **đổi result** thành chuỗi có prefix, hoặc **nuốt IllegalStateException** để trả fallback.

Hai context độc lập tránh việc cả hai aspect đồng thời advice một method và làm kết quả test khó giải thích.

Cấu trúc:

~~~text
spring-around-diagnostics/
├── pom.xml
└── src/
    ├── main/java/demo/around/
    │   ├── Events.java
    │   ├── OrderService.java
    │   ├── SafeAroundAspect.java
    │   ├── FaultyAroundAspect.java
    │   ├── SafeConfig.java
    │   └── FaultyConfig.java
    └── test/java/demo/around/
        └── AroundDiagnosticsTest.java
~~~

### 6.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-around-diagnostics</artifactId>
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
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.0</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.3</version>
            <scope>test</scope>
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
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**spring-context** quản lý ApplicationContext; **spring-aop** cung cấp proxy; **aspectjweaver** cung cấp parser/annotation @AspectJ cho Spring AOP; **junit-jupiter** chạy test. Sự hiện diện của AspectJ Weaver trong dependency **không có nghĩa đã bật AspectJ compile-time/load-time weaving**.

### 6.2. Dữ liệu quan sát

Tệp **src/main/java/demo/around/Events.java**:

~~~java
package demo.around;

import java.util.ArrayList;
import java.util.List;

public class Events {
    private final List<String> events = new ArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }
}
~~~

Events là recorder in-memory để test thứ tự advice/target. Mỗi test mở context mới nên instance này không chia sẻ giữa các test. Đây là fixture cho lời gọi tuần tự; trong hệ thống nhiều thread, recorder cần bảo đảm thread-safety hoặc dùng cơ chế quan sát thích hợp.

### 6.3. Service đại diện cho nghiệp vụ

Tệp **src/main/java/demo/around/OrderService.java**:

~~~java
package demo.around;

public class OrderService {
    private final Events events;

    public OrderService(Events events) {
        this.events = events;
    }

    public String submit(String code) {
        events.add("target:" + code);

        if ("fail".equals(code)) {
            throw new IllegalStateException("order rejected");
        }

        return "accepted:" + code;
    }
}
~~~

Service có hợp đồng đơn giản: input bình thường thì trả **accepted:<code>**; input **fail** thì ném **IllegalStateException("order rejected")**. Test trực tiếp target phải thấy đúng hợp đồng đó.

### 6.4. Aspect an toàn: trả nguyên value, ném nguyên Throwable

Tệp **src/main/java/demo/around/SafeAroundAspect.java**:

~~~java
package demo.around;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class SafeAroundAspect {
    private final Events events;

    public SafeAroundAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.around.OrderService.submit(..))")
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {
        events.add("safe:before");

        try {
            Object result = pjp.proceed();
            events.add("safe:after");
            return result;
        } catch (Throwable ex) {
            events.add("safe:throw:" + ex.getClass().getSimpleName());
            throw ex;
        } finally {
            events.add("safe:finally");
        }
    }
}
~~~

Với nhánh thành công, advice ghi before → cho target chạy → ghi after → trả **result**. Với nhánh lỗi, advice ghi before → target ném → ghi throw → **throw ex** → finally; caller vẫn nhận exception. Không có **return** trong finally.

**Giới hạn của ví dụ:** recorder **Events.add(...)** được thiết kế không ném lỗi. Trong hệ thống thực, logger/metric/exporter có thể ném; xử lý riêng lỗi của instrumentation theo policy để không làm hỏng result/exception của nghiệp vụ.

### 6.5. Aspect lỗi: đổi result và nuốt exception

Tệp **src/main/java/demo/around/FaultyAroundAspect.java**:

~~~java
package demo.around;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class FaultyAroundAspect {
    private final Events events;

    public FaultyAroundAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.around.OrderService.submit(..))")
    public Object replaceOrSwallow(ProceedingJoinPoint pjp)
            throws Throwable {
        events.add("faulty:before");

        try {
            Object result = pjp.proceed();
            events.add("faulty:replace");
            return "masked:" + result; // Đổi kết quả thành công.
        } catch (IllegalStateException ex) {
            events.add("faulty:swallow");
            return "fallback"; // Nuốt lỗi từ target/proceed().
        }
    }
}
~~~

**Cố ý sai để chẩn đoán:** với input bình thường, target trả **accepted:ok** nhưng caller nhận **masked:accepted:ok**. Với input **fail**, target ném **IllegalStateException** nhưng caller không nhận exception; caller nhận chuỗi **fallback**.

Trong bài học về logging/tracing, hai hành vi trên là **vi phạm contract** của service. Không dùng aspect lỗi này trong production.

### 6.6. Hai Spring ApplicationContext tách biệt

Tệp **src/main/java/demo/around/SafeConfig.java**:

~~~java
package demo.around;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class SafeConfig {
    @Bean
    public Events events() {
        return new Events();
    }

    @Bean
    public OrderService orderService(Events events) {
        return new OrderService(events);
    }

    @Bean
    public SafeAroundAspect safeAroundAspect(Events events) {
        return new SafeAroundAspect(events);
    }
}
~~~

Tệp **src/main/java/demo/around/FaultyConfig.java**:

~~~java
package demo.around;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class FaultyConfig {
    @Bean
    public Events events() {
        return new Events();
    }

    @Bean
    public OrderService orderService(Events events) {
        return new OrderService(events);
    }

    @Bean
    public FaultyAroundAspect faultyAroundAspect(Events events) {
        return new FaultyAroundAspect(events);
    }
}
~~~

**@EnableAspectJAutoProxy** kích hoạt Spring AOP auto-proxy trong mỗi context. **proxyTargetClass=true** dùng class-based/CGLIB proxy, giúp test lấy **OrderService.class** trực tiếp. Nó **không bật AspectJ weaving**.

**@Aspect** trên lớp advice không tự đăng ký Spring bean; **@Bean** trong hai config mới thực sự đưa từng aspect vào container.

## 7. Integration test xác nhận ngoại lệ và giá trị trả về

Tệp **src/test/java/demo/around/AroundDiagnosticsTest.java**:

~~~java
package demo.around;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AroundDiagnosticsTest {
    @Test
    void safeAdvicePreservesSuccessfulReturnValue() {
        try (var context =
                new AnnotationConfigApplicationContext(SafeConfig.class)) {
            OrderService service = context.getBean(OrderService.class);
            Events events = context.getBean(Events.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("accepted:ok", service.submit("ok"));
            assertEquals(
                List.of(
                    "safe:before",
                    "target:ok",
                    "safe:after",
                    "safe:finally"
                ),
                events.snapshot()
            );
        }
    }

    @Test
    void safeAdvicePropagatesOriginalFailure() {
        try (var context =
                new AnnotationConfigApplicationContext(SafeConfig.class)) {
            OrderService service = context.getBean(OrderService.class);
            Events events = context.getBean(Events.class);

            IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.submit("fail")
            );

            assertEquals("order rejected", failure.getMessage());
            assertEquals(
                List.of(
                    "safe:before",
                    "target:fail",
                    "safe:throw:IllegalStateException",
                    "safe:finally"
                ),
                events.snapshot()
            );
        }
    }

    @Test
    void faultyAdviceReplacesSuccessfulReturnValue() {
        try (var context =
                new AnnotationConfigApplicationContext(FaultyConfig.class)) {
            OrderService service = context.getBean(OrderService.class);
            Events events = context.getBean(Events.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("masked:accepted:ok", service.submit("ok"));
            assertEquals(
                List.of(
                    "faulty:before",
                    "target:ok",
                    "faulty:replace"
                ),
                events.snapshot()
            );
        }
    }

    @Test
    void faultyAdviceSwallowsTargetException() {
        try (var context =
                new AnnotationConfigApplicationContext(FaultyConfig.class)) {
            OrderService service = context.getBean(OrderService.class);
            Events events = context.getBean(Events.class);

            assertEquals("fallback", service.submit("fail"));
            assertEquals(
                List.of(
                    "faulty:before",
                    "target:fail",
                    "faulty:swallow"
                ),
                events.snapshot()
            );
        }
    }

    @Test
    void directTargetCallHasNoSpringAdvice() {
        Events events = new Events();
        OrderService service = new OrderService(events);

        assertEquals("accepted:ok", service.submit("ok"));
        assertEquals(List.of("target:ok"), events.snapshot());
    }
}
~~~

### 7.1. Giải thích từng test

**safeAdvicePreservesSuccessfulReturnValue** kiểm tra Spring đã tạo proxy, advice đã chạy, target đã chạy, và caller vẫn nhận đúng **accepted:ok**.

**safeAdvicePropagatesOriginalFailure** khẳng định caller nhận **IllegalStateException** với thông điệp gốc. Recorder cho thấy **proceed()** vào target rồi exception đi ngược ra advice; **finally** vẫn chạy.

**faultyAdviceReplacesSuccessfulReturnValue** xác nhận target trả thành công nhưng **@Around** thay result thành **masked:accepted:ok**.

**faultyAdviceSwallowsTargetException** xác nhận caller thấy kết quả bình thường **fallback** dù target đã ném exception. Việc không dùng **assertThrows** ở test này là **cố ý**: ta đang tái hiện một lỗi runtime, không khẳng định fallback là cách xử lý đúng.

**directTargetCallHasNoSpringAdvice** phân biệt đường gọi Java trực tiếp với đường gọi qua Spring proxy. Tạo object bằng **new** không được Spring AOP advice trong ví dụ; nhờ test này ta tách bạch lỗi trong service và hành vi do aspect thêm vào.

### 7.2. Chạy và kết quả kỳ vọng

Trong thư mục **spring-around-diagnostics**, chạy:

~~~bash
mvn test
~~~

Suite định nghĩa **5 JUnit test methods**; với cấu hình và assertions ở trên, kết quả kỳ vọng trong Maven Surefire:

~~~text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
~~~

Đây là **kết quả dự kiến theo logic ví dụ**, không phải log từ một lượt Maven thực thi. Khi chẩn đoán hệ thống thật, quan trọng hơn số test pass là test khẳng định rõ **contract mà service phải giữ**.

Muốn chỉ tập trung ca che exception:

~~~bash
mvn -Dtest=AroundDiagnosticsTest#faultyAdviceSwallowsTargetException test
~~~

Hoặc ca mất giá trị trả về:

~~~bash
mvn -Dtest=AroundDiagnosticsTest#faultyAdviceReplacesSuccessfulReturnValue test
~~~

Các ca lỗi đang pass vì chúng **xác minh lỗi có xảy ra**. Khi sửa **FaultyAroundAspect**, hãy đổi kỳ vọng của test hồi quy sang **accepted:ok** và **assertThrows(IllegalStateException.class, ...)** để tránh tái phát hành vi sai.

## 8. Cách sửa và tạo regression test đúng ý nghĩa nghiệp vụ

Nếu aspect chỉ để logging/tracing, hãy dùng pattern bảo toàn:

~~~java
@Around("execution(* demo.around.OrderService.submit(..))")
public Object preserveContract(ProceedingJoinPoint pjp) throws Throwable {
    long started = System.nanoTime();

    try {
        return pjp.proceed();
    } finally {
        long elapsedNanos = System.nanoTime() - started;
        // Ghi metric best-effort ở đây, không return và không throw sai ngữ cảnh.
    }
}
~~~

Đây là mảnh minh họa thay thế advice; không thêm vào context đồng thời với các aspect ví dụ nếu không chủ đích test chuỗi advisor.

Trường hợp cần ghi riêng exception:

~~~java
@Around("execution(* demo.around.OrderService.submit(..))")
public Object preserveFailure(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (Throwable ex) {
        // Ghi exception; logic quan sát cần không che mất ex.
        throw ex;
    }
}
~~~

Phương pháp regression:

1. Dùng **ApplicationContext** có config AOP giống production hoặc sát nhất với phạm vi đang test.
2. Lấy bean qua **context.getBean(...)**, không tự **new** target.
3. Kiểm tra bean proxy bằng **AopUtils.isAopProxy(...)** nếu test cần chứng minh AOP thực sự được kích hoạt.
4. Gọi case thành công, assert **return value chính xác**, không chỉ **not null**.
5. Gọi case thất bại, assert **type, message và cause** theo hợp đồng. Nếu phải bảo toàn chính đối tượng exception, đặt exception cố định trong fixture và **assertSame(...)**.
6. Theo dõi **số lần target thực thi** để phát hiện advice quên proceed hoặc gọi proceed nhiều lần.
7. Với nhiều advisor, kiểm tra các trường hợp kết hợp advisor theo config thật, thay vì chỉ unit test advice riêng lẻ.

Unit test gọi trực tiếp **safeAspect.observe(mockProceedingJoinPoint)** có thể kiểm tra logic try/catch/return; **integration test qua Spring proxy** mới chứng minh advice thực tế được gắn vào đúng bean và method.

## 9. Các nguồn làm che ngoại lệ ngoài lệnh catch rõ ràng

### 9.1. Quên gọi proceed()

~~~java
@Around("execution(* demo.around.OrderService.submit(..))")
public Object skipTarget(ProceedingJoinPoint pjp) {
    return "accepted:cached";
}
~~~

Lời gọi mục tiêu **không chạy**. Đây có thể là thiết kế đúng của caching, nhưng là lỗi nghiêm trọng nếu aspect được viết để đo thời gian. Gọi một lần/không lần/nhiều lần phải là **quyết định có chủ đích**, có contract và test.

### 9.2. Proceed nhiều lần tạo side effect lặp

~~~java
@Around("execution(* demo.around.OrderService.submit(..))")
public Object callTwice(ProceedingJoinPoint pjp) throws Throwable {
    pjp.proceed();
    return pjp.proceed(); // Target có thể được gọi hai lần.
}
~~~

Nếu target gửi email, ghi DB hoặc tạo thanh toán, lần thứ hai có thể gây trùng giao dịch. Nếu retry thực sự cần thiết, chỉ retry lỗi cụ thể, điều kiện idempotency/transaction phải rõ và phép thử xác nhận số lần gọi, rollback, kết quả cũng như exception cuối cùng. Không thêm retry vào mọi advice chỉ để "tăng độ tin cậy".

### 9.3. Cleanup/metric ném ngoại lệ trong finally

~~~java
try {
    return pjp.proceed();
} finally {
    exporter.flush(); // Nếu flush() ném, exception này có thể lấn át lỗi gốc.
}
~~~

Giữ dữ liệu quan sát và lỗi nghiệp vụ tách biệt. Nếu exporter có thể fail nhưng telemetry được định nghĩa là best-effort, log lỗi exporter theo kênh dự phòng và không thay thế kết quả/nguyên nhân lỗi gốc. Nếu exporter là thành phần bắt buộc khiến request phải fail, điều đó phải được công bố như một phần của contract, không giấu trong advice.

### 9.4. Log exception nhưng đánh mất stack trace

~~~java
catch (Exception ex) {
    log.error("Failed: {}", ex.getMessage());
    throw new IllegalStateException("Failed");
}
~~~

Dòng này vừa log thiếu stack trace vừa mất cause. Với logging-only aspect, thông thường nên log exception object và **throw ex**. Với exception translation có chủ đích, giữ **ex** làm cause của exception mới.

### 9.5. around advice tác động khác với @AfterReturning và @AfterThrowing

- **@AfterReturning** quan sát method/advice bên trong hoàn tất bình thường. Nó không thay thế giá trị caller nhận chỉ bằng cách gán lại biến **returning** trong advice.
- **@AfterThrowing** quan sát exception thoát ra ở vị trí tương ứng trong chuỗi advice.
- **@Around** có quyền quyết định kết quả/exception của phần chuỗi bên trong.

Nếu có nhiều aspect, thứ tự **advisor** quyết định một **@AfterThrowing** có nhìn thấy exception trước khi một **@Around** ở lớp ngoài nuốt nó hay không. Việc **@AfterThrowing** log được lỗi không bảo đảm caller cuối cùng cũng nhận exception.

## 10. Thứ tự advisor và tác động tới @Transactional

Trong Spring có thể đồng thời dùng aspect ứng dụng, **@Transactional**, security, retry, metrics và logging. Kết quả caller nhận phụ thuộc **thứ tự thực thi**:

~~~text
Trường hợp A: @Around ở ngoài Transaction interceptor

Caller
  -> Outer @Around
       -> Transaction interceptor
            -> Target throws E
            -> Transaction xử lý E theo rollback rules
       -> Outer @Around catch E và return fallback
  -> Caller nhận fallback
~~~

Trong trường hợp này, transaction interceptor **đã quan sát E** nếu E thoát ra từ target đến transaction interceptor, nên rollback có thể được đánh giá trước khi @Around bên ngoài nuốt lỗi. Caller vẫn có thể nhận fallback gây hiểu nhầm rằng thao tác đã thành công.

~~~text
Trường hợp B: Transaction interceptor ở ngoài @Around

Caller
  -> Transaction interceptor
       -> Inner @Around
            -> Target throws E
            -> Inner @Around catch E và return fallback
       -> Transaction interceptor thấy normal return
  -> Caller nhận fallback
~~~

Ở trường hợp B, transaction interceptor có thể không nhìn thấy E; từ góc nhìn interceptor, lời gọi bên trong kết thúc bình thường nên quy tắc rollback theo exception không được kích hoạt vì E đó. Tuy nhiên transaction vẫn có thể rollback do **rollback-only** hoặc điều kiện khác; không được kết luận mọi lần đều commit.

Thứ tự giữa các aspect có thể cấu hình qua **@Order** hoặc **Ordered** khi cần. Transaction advisor cũng có ordering riêng. Giá trị order nhỏ hơn thường có precedence cao hơn: chạy sớm hơn khi đi vào, chạy muộn hơn khi đi ra. Nếu ordering là một phần contract, phải kiểm tra **thứ tự advisor thực tế** trong Spring ApplicationContext; không suy đoán từ thứ tự các annotation trong mã nguồn.

**Hệ quả vận hành:** một aspect nuốt exception có thể làm controller, transaction, retry, monitoring và caller nhận tín hiệu sai. Vì vậy sửa root cause tại advice; tránh bù bằng cách thêm catch ở nhiều tầng.

## 11. Các loại kết quả và exception cần chú ý

### 11.1. Primitive và boxed type

Nếu service khai báo:

~~~java
public int count() {
    return 7;
}
~~~

thì result qua **proceed()** là **Integer** boxed. Một advice trả **null** hoặc **String "7"** thay vì giá trị tương thích có thể làm proxy/caller thất bại. Nếu lời gọi lỗi xuất hiện như lỗi cast/unboxing, kiểm tra **return** của around advice trước khi suy ra target return sai.

### 11.2. void method

Với target trả **void**, **proceed()** thường cho advice thấy giá trị tương ứng **null**, nhưng một @Around trả **Object** vẫn hợp lệ theo mô hình Spring. Không áp dụng kết luận "return null là lỗi" cho mọi method void; vấn đề là **hợp đồng khai báo của method được advice** và loại giá trị caller kỳ vọng.

### 11.3. Optional và ResponseEntity

Một method khai báo **Optional<Order>** phải trả **Optional<Order>**, không phải bản thân **Order**. Method Spring MVC khai báo **ResponseEntity<Order>** phải giữ cấu trúc status/header/body nếu advice không được giao nhiệm vụ biến đổi response.

Một **@Around** tổng quát viết cho nhiều method rất dễ trả fallback sai kiểu. Nếu fallback thực sự cần, hãy hạn chế pointcut cho các contract tương thích và test từng kiểu trả về quan trọng.

### 11.4. CompletableFuture và reactive Publisher

Nếu method trả **CompletableFuture<T>**, nó có thể **trả future bình thường**, trong khi lỗi chỉ xuất hiện **sau đó** dưới dạng future hoàn tất ngoại lệ. Một **try/catch** đồng bộ quanh **pjp.proceed()** **không tự bắt được lỗi asynchronous xảy ra sau khi proceed() đã trả**.

Tương tự, method trả **Mono<T>** hoặc **Flux<T>** trong Spring WebFlux thường chỉ tạo publisher khi gọi; tín hiệu error có thể xuất hiện lúc subscription. Muốn quan sát tín hiệu đó, dùng operator phù hợp (**doOnError**, **doFinally**, v.v.) và giữ nguyên publisher hoặc chủ đích tạo publisher tương đương. Đừng ngầm biến asynchronous error thành lỗi đồng bộ.

Ví dụ Java **CompletableFuture** (mảnh minh họa):

~~~java
Object returned = pjp.proceed();
if (returned instanceof java.util.concurrent.CompletableFuture<?> future) {
    // Future có thể hoàn tất exceptionally sau khi advice return.
    // Gắn observer đúng contract; không nuốt exception hay đổi return type.
}
return returned;
~~~

Không ép **join()** hoặc **get()** trong một @Around giám sát nếu không có yêu cầu, vì điều đó có thể block thread và thay đổi loại ngoại lệ/cách thực thi của ứng dụng.

### 11.5. Checked exception và signature

**ProceedingJoinPoint.proceed()** khai báo **throws Throwable**. Nếu around advice chuyển unchecked/checked exception theo cách không tương thích với method interface trên JDK proxy, caller có thể thấy **UndeclaredThrowableException** hoặc wrapper khác. Khi debug, kiểm tra **cause chain**, target method signature, JDK/CGLIB proxy và loại exception gốc trước khi quy lỗi cho nghiệp vụ.

## 12. Quy trình chẩn đoán tại chỗ trong Spring

### Bước 1: Kiểm tra proxy và điểm gọi

Trong môi trường có Spring context:

~~~java
Object service = context.getBean(OrderService.class);

System.out.println(
    org.springframework.aop.support.AopUtils.isAopProxy(service)
);
System.out.println(
    org.springframework.aop.support.AopUtils.getTargetClass(service)
);
~~~

Đây là **đoạn minh họa** dùng biến context đã có, không phải ứng dụng chạy độc lập. Nếu isAopProxy=false, trước tiên kiểm tra aspect bean, AOP enablement và pointcut. Nếu true, tiếp tục theo dõi chính xác chain.

### Bước 2: Đặt dấu mốc ở ba vị trí

1. Trước **pjp.proceed()**.
2. Ngay **sau pjp.proceed()** và trước **return**.
3. Trong **catch/finally**, kèm exception type/cause và dữ liệu trace.

Đồng thời đặt dấu mốc trong **target method**. Quan sát cả giá trị **target trả** lẫn giá trị **advice trả** và exception ở ranh giới proxy. Không log dữ liệu nhạy cảm, credential hoặc payload giao dịch toàn phần trong quá trình chẩn đoán.

### Bước 3: Kiểm tra code path nào trả về

Tìm những pattern:

~~~text
catch (...) { return null; }
catch (...) { return fallback; }
finally { return ...; }
pjp.proceed(); return otherValue;
public void around(...);
return null;
throw new Exception(...); // Không preserve cause
return pjp.proceed();      // Thường là mẫu bảo toàn.
~~~

Nếu advice có nhiều nhánh, viết test cho **success**, **target exception**, **exception từ collaborator**, **fallback được phép**, và **return type** tương ứng.

### Bước 4: Xác minh nhiều advisor và ordering

Đừng cho rằng chỉ có custom **@Aspect**. Spring transaction, caching, async, method security hoặc một library khác có thể thêm advisor. Kiểm tra AOP proxy, advisor chain và thứ tự configuration khi chúng ảnh hưởng tới kết quả. Nếu có nhiều @Around cùng match, mỗi advice chỉ thấy kết quả của phần chuỗi nằm bên trong nó; giá trị cuối cùng caller thấy có thể đã bị biến đổi nhiều lần.

### Bước 5: Viết assertion theo contract bên ngoài

Test thành công phải kiểm tra giá trị người dùng cuối nhận được. Test thất bại phải xác minh exception **tới được boundary cần nhận**, chứ không chỉ đã được logger trong target ghi lại. Thêm counter test với cùng target gọi trực tiếp nếu cần phân biệt lỗi logic service và lỗi do Spring AOP.

## 13. Bảng sự cố và cách xử lý

| Triệu chứng | Nguyên nhân thường gặp | Cách kiểm chứng/sửa |
|---|---|---|
| Caller nhận null dù target trả String | Advice trả void/null hoặc quên **return result** | Assert exact return value qua proxy; trả **pjp.proceed()** |
| Caller nhận fallback dù target throw | **catch** trong @Around nuốt exception | AssertThrows ở boundary; rethrow hoặc fallback có contract rõ |
| Caller thấy lỗi khác với lỗi target | Throw từ **finally**, logging/metric, hoặc exception translation sai | Kiểm tra cause chain; cô lập lỗi quan sát, bảo toàn cause |
| Target không chạy nhưng caller thành công | @Around không gọi **proceed()** | Kiểm tra counter/side effect của target; chỉ short-circuit có chủ đích |
| Target chạy hai lần | @Around gọi **proceed()** nhiều lần | Kiểm tra số lần gọi; thiết kế retry với điều kiện chặt |
| Primitive method lỗi khi qua proxy | Advice trả **null** hoặc object sai kiểu | Kiểm tra declared return type và giá trị thực tế từ advice |
| Có @AfterThrowing log nhưng controller không thấy lỗi | @Around nằm ngoài có thể nuốt exception | Xem advisor ordering và phép thử boundary |
| Transaction không rollback như kỳ vọng | Exception bị bắt trước khi vượt qua transaction advice, tùy order | Kiểm tra thứ tự advisor, rollback rules và rollback-only |
| Method chạy bình thường qua new nhưng sai qua context | Proxy/advice đang đổi behavior | So sánh direct call và proxy call, assert effect |
| Future hoàn tất exceptionally nhưng around catch không chạy | Exception phát sinh sau khi **proceed()** trả future | Quan sát completion thay vì chỉ try/catch đồng bộ |
| Reactive chain lỗi sau subscribe | Error signal đến sau khi publisher đã return | Dùng operator quan sát error và giữ reactive contract |
| Không có advice khi gọi nội bộ | Self-invocation đi qua **this** | Kiểm tra đường gọi, thiết kế bean boundary phù hợp |
| Test advice riêng pass nhưng ứng dụng sai | Unit test không kiểm chứng Spring wiring/advisor ordering | Thêm integration test với ApplicationContext và bean proxy |

## 14. Quy tắc kiểm thử tránh tái phát

- [ ] Advice quan sát có lời gọi **proceed()** theo đúng số lần đã thiết kế.
- [ ] Nhánh thành công giữ **đúng giá trị và kiểu trả về**.
- [ ] Nhánh lỗi truyền **đúng exception** hoặc thực hiện translation có contract và cause rõ ràng.
- [ ] Không có **return** trong **finally** che exception/result.
- [ ] Logger/metric/cleanup không vô tình thay đổi outcome nghiệp vụ.
- [ ] Có integration test qua **Spring ApplicationContext**; bean được lấy từ container.
- [ ] Test khẳng định pointcut/advisor thực sự chạy và target vẫn được thực thi.
- [ ] Test bao gồm case thành công, thất bại, null/primitive hoặc kiểu đặc biệt nếu có trong API.
- [ ] Khi có transaction/retry/security advisors, thứ tự tương tác đã được kiểm chứng.
- [ ] Với Future/Mono/Flux, đã kiểm tra kết quả **asynchronous/reactive** thay vì chỉ kết quả return đồng bộ.
- [ ] Nếu cache/fallback/retry được phép short-circuit hoặc đổi result, có quy tắc và test riêng.
- [ ] Không nhầm Spring @AspectJ proxy với AspectJ weaving và không dựa vào self-invocation để đánh giá advice.

## 15. Câu hỏi tự kiểm tra

**1. Target ném IllegalStateException nhưng @Around catch rồi return "OK". Caller nhận gì?**

Trong đường gọi Spring AOP thông thường, caller sẽ nhận **"OK"**, không thấy IllegalStateException đó. Đây chỉ đúng nếu fallback được contract cho phép; nếu không là lỗi che khuất ngoại lệ.

**2. Target trả "accepted:A", advice chạy proceed() nhưng return "DONE". Giá trị caller nhận?**

Caller nhận **"DONE"**. Giá trị từ target đã bị thay tại ranh giới @Around.

**3. Vì sao return trong finally nguy hiểm?**

Vì nó có thể ghi đè kết quả đang return hoặc ngoại lệ đang được throw từ try/catch, làm che khuất nguyên nhân thất bại.

**4. Dùng @AfterReturning thay @Around có thể tự gán lại return value của caller không?**

Không. **@AfterReturning** chủ yếu quan sát kết quả hoàn tất bình thường; gán lại biến advice parameter không thay thế value ở caller. **@Around** có quyền trả một value khác.

**5. Ngoại lệ bị @Around bên trong nuốt có luôn làm @Transactional commit không?**

Không thể kết luận tuyệt đối. Kết quả phụ thuộc thứ tự advisor, vị trí catch, rollback rules, rollback-only và transaction boundary.

**6. CompletableFuture hoàn tất exceptionally sau khi advice return thì try/catch quanh proceed() có bắt được lỗi đó không?**

Không, vì lỗi xuất hiện bất đồng bộ sau khi proceed() đã hoàn thành bình thường bằng cách trả future.

**7. Test nào chứng minh wiring Spring AOP?**

Test khởi tạo **ApplicationContext**, lấy bean từ context, gọi method qua proxy và kiểm tra kết quả/exceptions tại caller cùng side effect của advice/target.

## 16. Tham khảo chính thức

- Spring Framework — Declaring Advice, đặc biệt Around Advice: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — Advice API và MethodInterceptor: https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Advice Ordering: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/ordering.html
- Spring Framework — Declarative Transaction Rollback: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html
- Spring Framework — Testing: https://docs.spring.io/spring-framework/reference/testing/index.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-011">Breakpoint, nhật ký hạ tầng Spring và kiểm tra danh sách advisor</a>

<details>
<summary>Xem chi tiết</summary>

# Breakpoint, nhật ký hạ tầng Spring và kiểm tra danh sách advisor

## 1. Mục tiêu chẩn đoán Spring AOP

Khi một phương thức trong Spring Framework không đi qua advice như dự kiến, có ba câu hỏi cần trả lời theo đúng thứ tự:

1. **Spring có tạo AOP proxy cho bean này không?** Nếu không, cần tìm nguyên nhân ở cách đăng ký aspect/bean, context, scope, lifecycle hoặc điều kiện chọn target.
2. **Proxy đang giữ những `Advisor`/`Advice` nào?** Nếu không có advisor mong muốn, phải kiểm tra quá trình Spring phát hiện aspect, dựng advisor và chọn advisor đủ điều kiện cho bean.
3. **Phương thức thực tế đi qua proxy và đi vào interceptor chain không?** Ngay cả khi advisor xuất hiện trong proxy, pointcut vẫn có thể không khớp method/arguments, hoặc caller đã bỏ qua proxy.

Bài này hướng dẫn ba cách kiểm tra bổ trợ nhau trong **Spring AOP**: đặt breakpoint vào hạ tầng auto-proxy và invocation, bật log cho đúng logger của Spring, và đọc danh sách `Advisor` trên proxy thông qua `org.springframework.aop.framework.Advised`.

Các kỹ thuật áp dụng chủ yếu cho Spring Framework 6.x, Spring Boot sử dụng Spring AOP, `@Aspect` + `@EnableAspectJAutoProxy`, `@Transactional`, caching và những cơ chế Spring dựa trên proxy. Đây là bài **chẩn đoán Spring AOP**; không đi vào bytecode weaving AspectJ hoặc lý thuyết AOP chung.

## 2. Bản đồ điểm kiểm tra

```text
ApplicationContext khởi tạo
    |
    +-- Đọc @Configuration / @Component / @Aspect
    |
    +-- AutoProxyCreator (BeanPostProcessor)
    |      |
    |      +-- Xem bean có đủ điều kiện proxy?
    |      +-- Thu thập candidate advisors
    |      +-- Lọc/sắp thứ tự applicable advisors
    |      +-- Tạo JDK dynamic proxy hoặc class-based proxy
    |
    +-- context.getBean("orderService")
           |
           +-- AopUtils.isAopProxy(bean)?
           +-- ((Advised) bean).getAdvisors()
           |
           +-- Caller gọi service.place("42")
                  |
                  +-- Có thật sự đi qua proxy?
                  +-- Spring dựng interceptor chain cho method + targetClass
                  +-- Advice trước / quanh method
                  +-- Target method
                  +-- Advice sau / xử lý kết quả / exception
```

| Triệu chứng | Điểm kiểm tra trước tiên | Hành động tiếp theo |
| --- | --- | --- |
| `AopUtils.isAopProxy(bean) == false` | Spring context, auto-proxy creator, `wrapIfNecessary` | Xem bean có được xử lý, có advice/applicable advisor hay bị skip |
| Proxy có thật nhưng thiếu advisor | `Advised.getAdvisors()`, candidate/advisor filtering | Kiểm tra aspect đăng ký, pointcut, class filter và thứ tự khởi tạo |
| Advisor có trong proxy, method vẫn không có advice | Điểm gọi và interceptor-chain construction | Kiểm tra self-invocation, private/final, pointcut method matcher và runtime args |
| Advice có chạy nhưng thứ tự khác dự đoán | Danh sách advisor và runtime interceptor chain | Xem `@Order`, `Ordered`, advisor của transaction/security/cache và advice type |
| Lỗi chỉ xảy ra trên một environment | Proxy type, context, profiles, condition, Spring/Boot version | So sánh cùng bean và cùng lời gọi ở hai môi trường |

**Phân biệt quan trọng:** danh sách `Advised.getAdvisors()` là cấu hình advisor gắn vào proxy. Nó **không phải một bản ghi** cho biết advisor nào vừa thực thi trong một lần gọi. Với advisor có pointcut, Spring còn phải xét class, method và đôi khi argument của invocation.

## 3. Phân biệt `Advice`, `Advisor` và interceptor chain của Spring

Trong API Spring AOP:

- **Advice**: hành vi thực thi, chẳng hạn `MethodInterceptor`, `@Before`, `@AfterReturning` được Spring thích nghi thành advice object.
- **Pointcut**: điều kiện chọn join point mà advisor có thể áp dụng.
- **Advisor**: kết hợp advice với pointcut hoặc chính sách áp dụng. Một advisor có thể là `PointcutAdvisor`, `IntroductionAdvisor` hoặc loại hạ tầng khác.
- **Proxy**: object caller nhận từ Spring container; quyết định đường gọi có đi vào AOP hay không.
- **Method interceptor chain**: chuỗi interceptor cụ thể do Spring tạo cho **method + target class** khi proxy nhận lời gọi. Nó có thể chỉ chứa một phần các advisor đã gắn.

Ví dụ một proxy của `orderService` có ba advisor (trong đó có advisor cho transaction và hai advice của application) không bảo đảm cả ba chạy cho `place(String)`. Một `PointcutAdvisor` có thể được proxy giữ nhưng không áp dụng với mọi method.

Với Spring-managed `@Aspect`, advisor còn có các object nội bộ để đảm bảo ngữ cảnh invocation và thứ tự advice. Vì vậy, đừng assert tổng số advisor bằng một hằng số dựa vào cảm giác hoặc dựa vào số annotation `@Before`/`@Around` trong source; hãy in **loại advisor, loại advice và pointcut** đang được gắn.

## 4. Ví dụ Java 17+ hoàn chỉnh để quan sát proxy và advisor

Ví dụ không dùng Spring Boot, nhằm cô lập đúng hạ tầng Spring AOP. Môi trường mẫu: Java 17, Maven, Spring Framework 6.2.14 và AspectJ Weaver 1.9.24 (thư viện hỗ trợ cú pháp pointcut cho Spring AOP, **không bật load-time weaving**).

### 4.1. Cấu trúc

```text
spring-aop-diagnostics/
├── pom.xml
└── src/main/
    ├── java/demo/
    │   ├── OrderService.java
    │   ├── DefaultOrderService.java
    │   ├── AuditAspect.java
    │   ├── AopConfiguration.java
    │   └── Main.java
    └── resources/
        └── logback.xml
```

### 4.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-diagnostics</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.14</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.14</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.24</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.5.18</version>
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
                <version>3.5.1</version>
                <configuration>
                    <mainClass>demo.Main</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Ở đây Logback dùng SLF4J làm backend ghi log. Spring Framework đi qua `spring-jcl`, có khả năng tích hợp với logging hiện diện trên classpath. Không đưa `spring-instrument` vào dependencies vì đây là **Spring AOP dựa trên proxy**, không phải AspectJ LTW.

### 4.3. `src/main/java/demo/OrderService.java`

```java
package demo;

public interface OrderService {
    String place(String orderId);
}
```

### 4.4. `src/main/java/demo/DefaultOrderService.java`

```java
package demo;

public class DefaultOrderService implements OrderService {
    @Override
    public String place(String orderId) {
        System.out.println("TARGET: " + orderId);
        return "accepted:" + orderId;
    }
}
```

### 4.5. `src/main/java/demo/AuditAspect.java`

```java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* demo..*.place(..))")
    public void before(JoinPoint jp) {
        System.out.println("BEFORE: " + jp.getSignature().getName());
    }

    @AfterReturning(
        pointcut = "execution(* demo..*.place(..))",
        returning = "result"
    )
    public void after(String result) {
        System.out.println("AFTER: " + result);
    }
}
```

Hai advice đều nhắm vào lời gọi `place(...)` phù hợp pointcut. `@AfterReturning` chỉ thực thi khi method trả về bình thường; điểm `returning="result"` ràng buộc tên tham số của advice, và trong ví dụ này kiểu `String` khớp giá trị trả về từ `place`.

### 4.6. `src/main/java/demo/AopConfiguration.java`

```java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AopConfiguration {
    @Bean("orderService")
    public OrderService orderService() {
        return new DefaultOrderService();
    }

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
```

`@EnableAspectJAutoProxy` bật auto-proxy creator trong context hiện tại. `proxyTargetClass=false` cho phép Spring ưu tiên JDK dynamic proxy vì target triển khai `OrderService`. Điều này giúp ví dụ minh họa cách lấy advisor **không cần cast proxy sang `DefaultOrderService`**.

### 4.7. `src/main/java/demo/Main.java`

```java
package demo;

import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AopConfiguration.class)) {
            Object bean = context.getBean("orderService");

            System.out.println("PROXY=" + AopUtils.isAopProxy(bean));
            System.out.println("JDK=" + AopUtils.isJdkDynamicProxy(bean));
            System.out.println("TARGET_CLASS="
                    + AopUtils.getTargetClass(bean).getSimpleName());

            if (bean instanceof Advised advised) {
                Advisor[] advisors = advised.getAdvisors();
                System.out.println("HAS_ADVISOR=" + (advisors.length > 0));

                for (int i = 0; i < advisors.length; i++) {
                    Advisor advisor = advisors[i];
                    String adviceType =
                            advisor.getAdvice().getClass().getSimpleName();
                    System.out.println("ADVISOR[" + i + "]="
                            + advisor.getClass().getSimpleName()
                            + " -> " + adviceType);

                    if (advisor instanceof PointcutAdvisor pa) {
                        System.out.println("  POINTCUT=" + pa.getPointcut());
                    }
                }
            } else {
                System.out.println("ADVISOR_INSPECTION_UNAVAILABLE");
            }

            OrderService service = (OrderService) bean;
            System.out.println("RESULT=" + service.place("42"));
        }
    }
}
```

**Chạy:**

```bash
mvn -q compile exec:java
```

**Những dòng stdout quan trọng, theo thứ tự tương đối** (không ghi cố định số lượng, tên class hay thứ tự của advisor vì có thể thay đổi theo Spring version):

```text
PROXY=true
JDK=true
TARGET_CLASS=DefaultOrderService
HAS_ADVISOR=true
... các dòng ADVISOR[i]=... và POINTCUT=... ...
BEFORE: place
TARGET: 42
AFTER: accepted:42
RESULT=accepted:42
```

Khối trên mô tả **mẫu stdout**: dấu `...` là chỉ dẫn bỏ qua các dòng advisor thay đổi theo runtime, không phải dòng chương trình tự in ra. Các dòng `PROXY`, `JDK`, `TARGET_CLASS`, `HAS_ADVISOR`, `BEFORE`, `TARGET`, `AFTER`, `RESULT` là những giá trị được mong đợi khi cấu hình hoạt động. Log của Spring được ghi ra stderr trong cấu hình bên dưới và không có thứ tự cố định so với stdout.

**Tại sao `Advised`?** `org.springframework.aop.framework.Advised` là giao diện hạ tầng mà Spring AOP proxy thường công bố để xem cấu hình advisors. `Advised.getAdvisors()` trả về một mảng advisor đang gắn vào proxy. Tránh cast trực tiếp không kiểm tra vì object có thể chưa được proxied, là proxy opaque hoặc là proxy do cơ chế khác tạo.

`AopUtils.getTargetClass(bean)` giúp xem class đích; kết quả này **không có nghĩa** JDK proxy có thể cast sang `DefaultOrderService`. Lời gọi hợp lệ ở ví dụ dùng `OrderService`.

## 5. Bật nhật ký hạ tầng Spring AOP có kiểm soát

Thêm **`src/main/resources/logback.xml`** để quan sát lúc Spring tìm advisor, chọn proxy và khởi tạo bean:

```xml
<configuration>
    <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
        <target>System.err</target>
        <encoder>
            <pattern>%level %logger{60} - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="org.springframework.aop.framework.autoproxy"
            level="TRACE"/>
    <logger name="org.springframework.aop.aspectj"
            level="DEBUG"/>
    <logger name="org.springframework.beans.factory.support"
            level="INFO"/>

    <root level="WARN">
        <appender-ref ref="STDERR"/>
    </root>
</configuration>
```

Log cụ thể xuất hiện phụ thuộc Spring version, logger cấp lớp, cách tạo proxy và từng nhánh được thực thi. Bật `TRACE` giúp mở rộng dữ liệu trong thời gian chẩn đoán, nhưng **không bảo đảm** Spring sẽ in đầy đủ danh sách advisors hay một dòng log cho mọi invocation.

Nếu dùng Spring Boot, có thể bật tạm các logger bằng `application.properties`:

```properties
logging.level.org.springframework.aop.framework.autoproxy=TRACE
logging.level.org.springframework.aop.aspectj=DEBUG
logging.level.org.springframework.beans.factory.support=DEBUG
```

Đây là cấu hình **thay thế dành cho Spring Boot**, không phải file `application.properties` cần bổ sung vào project Java thuần ở mục 4. Boot có thể tích hợp với hệ thống logging do starter cung cấp; cú pháp profile, config hoặc log output tùy Boot runtime.

Các nhóm logger có ích:

| Logger/category | Cần quan sát |
| --- | --- |
| `org.springframework.aop.framework.autoproxy` | Hạ tầng tìm advisor, lựa chọn bean cần proxy, đường auto-proxy |
| `org.springframework.aop.aspectj` | Những thành phần tích hợp advice/pointcut `@AspectJ` |
| `org.springframework.aop.framework` | Một số hoạt động liên quan proxy factory, interceptor và cấu hình |
| `org.springframework.beans.factory.support` | Vòng đời tạo bean, dependency và khởi tạo infrastructure bean |
| `org.springframework.transaction.interceptor` | Khi lỗi liên quan `@Transactional` và transaction advisor (nếu ứng dụng có transaction) |
| `org.springframework.cache.interceptor` | Khi lỗi liên quan cache advice (nếu có cache) |

Nên bắt đầu bằng **một vài category hẹp** trên môi trường thử nghiệm, tái hiện đúng một request, rồi nâng mức sang DEBUG/TRACE nếu thiếu dữ liệu. Log `TRACE` của AOP và bean creation có thể rất lớn, chứa method/bean name và đôi khi thông tin nhạy cảm theo cách ứng dụng cấu hình log; cần tắt sau khi chẩn đoán. Với vấn đề advice *không chạy*, kết hợp log với `Advised.getAdvisors()` và breakpoint thường nhanh hơn đọc toàn bộ log container.

## 6. Đặt breakpoint ở đâu để tìm nguyên nhân?

Các tên lớp/phương thức dưới đây thuộc **mã nguồn Spring Framework 6.x**; hãy attach source JAR đúng phiên bản Maven/Gradle đang chạy. Những tên nội bộ có thể thay đổi theo Spring version; chúng **không phải API để code ứng dụng phụ thuộc vào**.

### 6.1. Giai đoạn Spring quyết định có tạo proxy

Đặt breakpoint theo thứ tự:

1. `org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#postProcessAfterInitialization`: xem bean đi qua `BeanPostProcessor` sau khi tạo instance. Quan sát `bean` và `beanName`.
2. `AbstractAutoProxyCreator#wrapIfNecessary`: xem Spring có skip target vì infrastructure bean, cache hoặc các điều kiện loại trừ không.
3. `AbstractAutoProxyCreator#getAdvicesAndAdvisorsForBean`: xác định lời gọi tìm advice/advisor cho class và bean name. Một số implementation dùng cơ chế override thích hợp.
4. `org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator#findEligibleAdvisors`: quan sát danh sách advisor phù hợp với class target và kết quả sắp xếp.
5. `org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#createProxy`: xem proxy được dựng với target/interface/advisor như thế nào.

Trong ứng dụng có `@Aspect`, lớp `AnnotationAwareAspectJAutoProxyCreator` tham gia phát hiện các advisor từ `@Aspect` và chọn bean đủ điều kiện. Breakpoint ở `findCandidateAdvisors` (trên lớp này) hoặc `findEligibleAdvisors` (trên lớp cha thích hợp) giúp phân biệt “không tìm thấy aspect” với “tìm thấy advisor nhưng không áp dụng cho target”.

**Chú ý:** một aspect bean tự nó có thể được Spring bỏ qua khi proxy hóa; `infrastructure class` không được xử lý như business target bình thường. Đừng đặt breakpoint rồi thấy một lượt `wrapIfNecessary` trả về target mà kết luận mọi AOP đều tắt: hãy lọc đúng **`beanName` của target**.

### 6.2. Giai đoạn caller gọi phương thức

Nếu target đã proxied và có advisor nhưng advice không thực thi:

- Với JDK proxy, đặt breakpoint vào `org.springframework.aop.framework.JdkDynamicAopProxy#invoke`.
- Với class-based proxy, xem `org.springframework.aop.framework.CglibAopProxy` và interceptor thích hợp (chẳng hạn `DynamicAdvisedInterceptor#intercept`).
- Kiểm tra đoạn tạo chain qua `AdvisedSupport#getInterceptorsAndDynamicInterceptionAdvice` hoặc code tương ứng của phiên bản đang chạy.
- Đặt breakpoint ở `org.springframework.aop.framework.ReflectiveMethodInvocation#proceed` để thấy Spring duyệt danh sách interceptors; các component thực tế có thể bao gồm wrapper thích nghi advice, chain của advisor và adapter hạ tầng.
- Đặt breakpoint vào **chính method advice của ứng dụng**, ví dụ `AuditAspect.before`, để so sánh với đường gọi thật.

Nếu breakpoint trong `AuditAspect.before` **không dừng** nhưng breakpoint ở `JdkDynamicAopProxy#invoke` **có dừng**, cần soi method invocation, pointcut/method matcher và interceptor chain. Nếu cả breakpoint proxy và advice **không dừng** nhưng target vẫn thực thi, nghi ngờ caller đang gọi thẳng target, self-invocation, bean khác hoặc một đường thực thi ngoài context.

### 6.3. Debugger nên xem những biến nào?

| Vị trí | Biến/thông tin | Câu hỏi |
| --- | --- | --- |
| `wrapIfNecessary` | `beanName`, `bean.getClass()`, trạng thái skip/cache | Có xét đúng bean cần AOP? |
| `findEligibleAdvisors` | candidate advisors, applicable advisors | Có advice nào được chọn cho target class? |
| `createProxy` | interfaces, `proxyTargetClass`, advisors, target source | Spring định tạo JDK proxy hay subclass proxy? |
| `JdkDynamicAopProxy.invoke` / CGLIB interceptor | `method`, `args`, `targetClass`, proxy | Caller có đi vào AOP proxy? |
| `ReflectiveMethodInvocation.proceed` | chain, current index, `method` | Advice cụ thể có nằm trong chain của method này? |
| Aspect advice của ứng dụng | join point signature, exception/result, thread | Advice đã chạy và đang thấy đúng lời gọi? |

Tránh đặt breakpoint cho tất cả bean trên một ứng dụng lớn vì mỗi `BeanPostProcessor` có thể được gọi rất nhiều lần. Trong IDE, hãy dùng **conditional breakpoint** với `beanName.equals("orderService")` ở vị trí biến `beanName` khả dụng; một breakpoint điều kiện sai tên local có thể gây nhiễu hoặc dừng không đúng chỗ.

## 7. Kiểm tra danh sách advisor trên một bean thật

`Advised.getAdvisors()` chỉ đọc cấu hình proxy; phù hợp cho diagnostics, automated tests và breakpoint watch expression.

Snippet có thể dùng trong code debug (khi biến `bean` là object nhận từ Spring context):

```java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

System.out.println("proxy=" + AopUtils.isAopProxy(bean));

if (bean instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println("advisor=" + advisor.getClass().getName());
        System.out.println("  advice=" + advisor.getAdvice().getClass().getName());
    }
}
```

Khi phân tích một **method cụ thể**, bước tiếp theo là kiểm tra `PointcutAdvisor`. Một phép kiểm tra tĩnh mang tính định hướng:

```java
import java.lang.reflect.Method;
import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.support.AopUtils;

// Fragment đặt ở nơi có biến bean là Spring AOP proxy.
if (bean instanceof Advised advised) {
    Class<?> targetClass = AopUtils.getTargetClass(bean);
    Method interfaceMethod =
            OrderService.class.getMethod("place", String.class);
    Method targetMethod =
            AopUtils.getMostSpecificMethod(interfaceMethod, targetClass);

    for (Advisor advisor : advised.getAdvisors()) {
        if (advisor instanceof PointcutAdvisor pa) {
            boolean classMatches =
                    pa.getPointcut().getClassFilter().matches(targetClass);
            boolean methodMatches =
                    pa.getPointcut().getMethodMatcher()
                      .matches(targetMethod, targetClass);

            System.out.println("class=" + classMatches
                    + ", method=" + methodMatches
                    + ", advice=" + advisor.getAdvice().getClass().getSimpleName());
        }
    }
}
```

Đây là **snippet trong chương trình đã import và có `bean`, `OrderService`**, không phải file Java độc lập.

**Giới hạn rất quan trọng:** `MethodMatcher.matches(Method, Class<?>)` là kiểm tra tĩnh. Nếu `MethodMatcher.isRuntime()` là `true`, Spring còn gọi overload `matches(Method, Class<?>, Object...)` với argument của invocation. Thêm vào đó, advisors dạng introduction và hạ tầng có semantics khác `PointcutAdvisor`. Vì vậy kết quả `class=true, method=true` là **bằng chứng tương thích tĩnh**, chưa chứng minh advice chắc chắn đã chạy với argument cụ thể. Bằng chứng cuối cùng là breakpoint ở chain/advice hoặc một assertion integration test qua proxy.

Không gọi các API chỉnh sửa như `addAdvisor`, `removeAdvisor` hay `replaceAdvisor` chỉ để chẩn đoán production: thay đổi đó tác động cấu hình AOP runtime. Bài chỉ sử dụng các phép đọc `getAdvisors()`.

## 8. Khi danh sách advisor không như mong đợi

### 8.1. Proxy có nhưng không thấy advice cần tìm

Kiểm tra lần lượt:

- `@EnableAspectJAutoProxy` có được đăng ký trong context sở hữu target bean không? Với Spring Boot có thể có auto-configuration nhưng vẫn phải kiểm tra cấu hình thực.
- Lớp aspect có `@Aspect` **và** được Spring đăng ký thành bean (`@Component`, `@Bean` hoặc cách phù hợp) không?
- Điểm cắt có khớp **method signature và target class** không? Package, overload, visibility, class/interface và modifier có thể ảnh hưởng.
- Bean có được tạo quá sớm, bỏ qua một số `BeanPostProcessor`, hoặc có vòng phụ thuộc/`BeanFactoryPostProcessor` làm hạ tầng hoạt động khác thường không?
- Có advisor khác với precedence cao hơn hoặc cơ chế infrastructure khác không? Thứ tự của các advisor có thể do `@Order`, `Ordered` và quy tắc riêng quyết định.

**Đừng đánh đồng thiếu advisor với lỗi exception trong advice**: nếu advice chưa được gắn vào proxy, logic trong advice không thể chạy. Cần sửa registration/pointcut/proxy selection trước.

### 8.2. Có advisor nhưng breakpoint advice không dừng

Kiểm tra:

1. `AopUtils.isAopProxy(bean)` trên **chính object caller đang cầm**.
2. Caller có sử dụng `this.method()`, object được tự `new` hoặc reference target được lấy bằng cách unwrap proxy không? Các đường này thường bỏ qua Spring AOP.
3. Method có bị hạn chế bởi proxy type không? JDK proxy chỉ nhận những method công bố bởi interface; class proxy không thể advise `final`/`private` methods qua subclass override.
4. Pointcut có điều kiện runtime theo arguments, annotation, method matcher hay loại lời gọi không?
5. Nếu dùng `@Async`, transaction, caching hoặc security, method có thực sự chạy trên thread/tầng proxy bạn đang debug không?
6. Nếu có nhiều `ApplicationContext`, breakpoint có đang quan sát đúng context của service không?

Cùng một ứng dụng có thể có nhiều proxy lồng nhau khi nhiều infrastructure tác động. Đừng chỉ kiểm tra bean class của object target bên trong rồi kết luận caller đã đi qua mọi lớp proxy.

### 8.3. Advisor xuất hiện nhưng thứ tự advice gây bất ngờ

`Advised.getAdvisors()` cho thấy thứ tự advisor **được lưu trên proxy**, thường có ích khi xem precedence. Tuy nhiên:

- `@Before`, `@Around`, `@AfterReturning` có cách thích nghi và chu trình gọi khác nhau.
- Một advisor có thể không nằm trong chain của method đang xét vì pointcut không khớp.
- Interceptor dạng **outer/inner** có thể tạo thứ tự log vào/ra dạng đối xứng, không đơn giản là thứ tự các dòng array.
- `@Order` trên aspect/advisor ảnh hưởng precedence trong những quy tắc Spring hỗ trợ; thứ tự advice trong cùng aspect còn phụ thuộc semantics riêng và Spring version.

Để xác định thực tế, xem cả array advisor **và** chain được dựng cho đúng method, sau đó chạy một invocation thử với log hoặc breakpoint. Không sửa thứ tự tùy tiện khi chưa hiểu tác động lên transaction boundary và exception handling.

## 9. Test nhỏ để bảo vệ cấu hình AOP

Một integration test có giá trị nên chứng minh đồng thời (1) Spring trả về proxy, (2) có advisor mong đợi, (3) lời gọi thật sự kích hoạt advice.

Có thể bổ sung JUnit 5 vào `pom.xml`:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.11.4</version>
    <scope>test</scope>
</dependency>
```

Nếu Maven chưa được cấu hình runner phù hợp, bổ sung plugin trong `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.2</version>
</plugin>
```

Ví dụ test **`src/test/java/demo/AopDiagnosticsTest.java`**:

```java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class AopDiagnosticsTest {
    @Test
    void proxyContainsAdvisorsAndInvokesTarget() {
        try (var context =
                new AnnotationConfigApplicationContext(AopConfiguration.class)) {
            Object bean = context.getBean("orderService");

            assertTrue(AopUtils.isAopProxy(bean));
            assertInstanceOf(Advised.class, bean);
            Advised advised = (Advised) bean;
            assertTrue(advised.getAdvisors().length > 0);

            OrderService service = (OrderService) bean;
            assertEquals("accepted:42", service.place("42"));
        }
    }
}
```

Lệnh chạy:

```bash
mvn test
```

Test trên kiểm tra có proxy, có advisor và target trả kết quả; **nó chưa xác nhận advice cụ thể đã được chạy**. Để test chính xác advice, đổi `AuditAspect` trong một test fixture riêng để tăng `AtomicInteger` qua `@Before` rồi assert counter sau `service.place("42")`; có thể dùng test application context riêng hoặc spy phù hợp. Dựa duy nhất vào `getAdvisors().length > 0` là chưa đủ nếu hệ thống còn advisor của transaction/cache.

Không cần viết test cho mọi tên logger DEBUG: output log nội bộ có thể đổi giữa các Spring patch/minor versions. Test nên khóa **hành vi cần bảo vệ**, còn log và breakpoint phục vụ điều tra.

## 10. Lỗi khi dùng debugger hoặc log

| Sai sót | Vì sao kết luận sai | Cách khắc phục |
| --- | --- | --- |
| Breakpoint vào aspect không dừng nên kết luận aspect chưa được đăng ký | Advisor có thể đã đăng ký nhưng method không đi qua proxy/pointcut | Xem lần lượt AopUtils → Advised → interceptor chain → aspect |
| In `bean.getClass()` rồi tưởng đó là target class | Với JDK proxy class là `jdk.proxy...`, class proxy cũng có tên generated | Dùng `AopUtils.getTargetClass(bean)` và kiểm tra proxy type |
| Cast JDK proxy sang class implementation để debug | JDK dynamic proxy không kế thừa implementation | Truy cập qua business interface hoặc `Advised` |
| Tin rằng tất cả `getAdvisors()` chạy cho mọi method | Pointcut filtering có thể loại advice cho method cụ thể | Soi method matcher và runtime chain |
| Bật TRACE toàn Spring trong production dài ngày | Log lớn, khó tìm tín hiệu và có thể lộ ngữ cảnh nhạy cảm | Bật category hẹp trong thời gian ngắn, có sampling/retention phù hợp |
| Breakpoint vào `wrapIfNecessary` nhưng dừng ở aspect bean | Auto-proxy creator xử lý nhiều bean, một số infrastructure sẽ skip | Đặt điều kiện đúng `beanName` của business bean |
| Chỉ kiểm tra khi context bootstrap | Lỗi có thể ở đường gọi và self-invocation | Debug thêm `JdkDynamicAopProxy.invoke` hoặc CGLIB interceptor |
| Dựa vào số thứ tự advisor cố định | Spring có thể gắn advisor hạ tầng bổ sung | In tên class/pointcut, viết assertion theo hành vi |

## 11. Quy trình điều tra một incident Spring AOP

Giả sử `OrderService.place("42")` chạy và trả kết quả đúng nhưng audit advice không ghi nhận:

1. **Xác định đúng bean và context:** in tên bean, `AopUtils.isAopProxy` và `AopUtils.getTargetClass`. Nếu false, tập trung vào registration, auto-proxy creator và lifecycle.
2. **Đọc advisor array:** khi proxy là `Advised`, in `getAdvisors()` cùng advice type và pointcut. Nếu không có advisor audit, kiểm tra aspect bean và điểm cắt.
3. **Bật logger hẹp:** `org.springframework.aop.framework.autoproxy` ở DEBUG/TRACE trên môi trường tái hiện; kiểm tra lỗi trong lúc context dựng bean.
4. **Đặt breakpoint chọn proxy:** lọc đúng `beanName` trong `wrapIfNecessary`, xem candidate/applicable advisors. Nếu Spring skip thì xác định vì sao.
5. **Đặt breakpoint invocation:** với JDK proxy vào `JdkDynamicAopProxy.invoke`, với class proxy vào CGLIB interceptor; nếu breakpoint không dừng, tìm đường gọi bỏ qua proxy.
6. **Kiểm tra interceptor chain:** với method `place` và argument thật, xem có audit interceptor không. Nếu không, điều tra pointcut; nếu có, step vào advice để xem exception hoặc điều kiện riêng.
7. **Chạy một integration test regression:** test qua Spring context thật và assert side effect của advice, không chỉ assert method return value.
8. **Thu hẹp log sau khi xong:** trả logger về mức bình thường và ghi lại nguyên nhân cụ thể, ví dụ “caller gọi self-invocation nên không đi qua proxy”.

**Kết quả chẩn đoán có giá trị** phải nêu được một mắt xích bị thiếu: *bean chưa được proxied*, *advisor không được chọn*, *pointcut không khớp method*, *caller bỏ qua proxy*, hoặc *advice đã chạy nhưng hành vi sau đó sai*. Các nhánh này dẫn đến cách sửa khác nhau.

## 12. Tài liệu Spring chính thức

- [Spring Framework — Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advised.html): API `Advised`, `getAdvisors()` và cách quản lý cấu hình proxy.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/class-based proxy, giới hạn và self-invocation.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): đăng ký aspect và quy tắc cấu hình Spring.
- [Spring Framework — AOP API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api.html): advisor, advice và pointcut từ API của Spring.
- [Spring Framework Javadoc — `AopUtils`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/AopUtils.html): các phương thức nhận diện proxy và target class.
- [Spring Framework Javadoc — `AbstractAutoProxyCreator`](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.html): `wrapIfNecessary`, tạo proxy và extension points phục vụ breakpoint.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-troubleshooting-012">Quy trình lần theo bean → proxy → pointcut → advice → phương thức đích</a>

<details>
<summary>Xem chi tiết</summary>

# Quy trình lần theo bean → proxy → pointcut → advice → phương thức đích trong Spring AOP

## 1. Mục tiêu: chẩn đoán chính xác một lời gọi Spring AOP

Khi đặt `@Before`, `@Around`, `@AfterReturning` hoặc `@AfterThrowing` lên một `@Aspect` nhưng advice không chạy, log xuất hiện không đúng thứ tự, hay business method chạy dù tưởng đã bị chặn, không nên sửa pointcut theo phỏng đoán. Trong Spring AOP, cần lần theo **đúng chuỗi trách nhiệm của một lời gọi**:

~~~text
Spring bean definition / ApplicationContext
                  |
                  v
        Bean reference của caller
                  |
                  v
      Spring AOP proxy có tồn tại?
                  |
                  v
        Advisors / pointcut match?
                  |
                  v
       Interceptor / advice có chạy?
                  |
                  v
     proceed() có tới target method?
                  |
                  v
       Kết quả / ngoại lệ trả về
~~~

Một lỗi ở **chặng trước** có thể khiến mọi chặng sau không xảy ra. Chẳng hạn `@Aspect` được khai báo đúng nhưng không được đăng ký thành bean, thì sửa biểu thức `execution(...)` không giải quyết được; bean được proxy nhưng caller cầm raw target, thì thêm log vào advice cũng không có tác dụng.

**Phạm vi:** quy trình chẩn đoán *Spring Framework Aspect/Spring AOP*, bao gồm bean wiring, lựa chọn JDK/CGLIB proxy, điều kiện pointcut, advisor chain, advice runtime và target execution. Đây không phải bài diễn giải AOP/AspectJ tổng quát; những API liên quan chỉ được giới thiệu để quan sát Spring hoạt động.

### Điều cần chứng minh ở từng chặng

1. **Bean:** đúng bean ID và đúng `ApplicationContext`; cả target và aspect đã được đăng ký?
2. **Proxy:** object *caller thực sự gọi* có phải Spring AOP proxy, kiểu JDK hay CGLIB, và target class nào?
3. **Pointcut:** advisor có tồn tại trên proxy, class/method có phù hợp điều kiện tĩnh, điều kiện động có được thỏa khi gọi?
4. **Advice:** advice method/interceptor nào được gọi, theo thứ tự nào, có bỏ `proceed()`, thay đối số/kết quả hoặc ném ngoại lệ?
5. **Target:** target method nào chạy, chạy mấy lần, trên object nào, có return/throw đúng và có self-invocation/direct-target bypass không?

## 2. Nhớ giới hạn Spring AOP trước khi chẩn đoán

Spring AOP chặn **lời gọi method đi qua Spring proxy**, không intercept mọi lời gọi Java. Trước khi lần theo năm chặng, dùng các tiêu chí sau làm điều kiện đầu vào:

| Điều cần xác minh | Kết luận nếu không đạt |
| --- | --- |
| Caller nhận đúng Spring bean/proxy thay vì tự `new` target | Phải sửa wiring hoặc hướng gọi trước khi chỉnh pointcut |
| Aspect được đăng ký thành bean, auto-proxy hoạt động trong **context sở hữu target** | Chưa thể kết luận expression `@Around` sai |
| Method thực sự đi qua proxy, không phải `this.inner()` | Self-invocation cần đổi bean boundary/call path |
| Interface JDK proxy hoặc class-based proxy expose được method | Kiểm tra cast, `final`, `private`, visibility và ClassLoader |
| Advisor được chọn và chain tiếp tục tới target | Cache/security có thể short-circuit; `@Around` có thể không gọi `proceed()` |

`@Aspect` không tự đăng ký bean, và thư viện AspectJ Weaver trên classpath **không tự kích hoạt weaving**. Không suy từ `isAopProxy=true` rằng chính advice đang điều tra đã khớp. Các bài trước phân tích riêng từng lỗi; ở đây mục tiêu là thu thập bằng chứng theo đúng thứ tự năm chặng.

## 3. Chặng 1 — xác định bean đang được gọi

### 3.1. Bắt đầu từ caller, không bắt đầu từ @Aspect

Tại điểm controller, scheduler, listener hoặc một Spring service gọi dependency, ghi lại:

- Tên bean dự kiến (ví dụ `paymentService`).
- Kiểu interface được inject (ví dụ `PaymentApi`) và kiểu implementation thực tế.
- Context nào cung cấp bean: root context, web child context, test context hoặc context của module khác.
- Caller nhận bean thông qua constructor injection, `ApplicationContext.getBean(...)`, `FactoryBean`, hay tự `new`.
- Method, arguments (ẩn dữ liệu nhạy cảm), thread và correlation ID thực sự dùng.

Một ví dụ quan sát khi đã có `ApplicationContext context`:

~~~java
Object candidate = context.getBean("paymentService");
System.out.println("beanType=" + candidate.getClass().getName());
System.out.println("isSpringAopProxy=" + AopUtils.isAopProxy(candidate));
~~~

Đây là **snippet chẩn đoán**, cần import `org.springframework.aop.support.AopUtils`. Tên class trả về có thể chứa mã nhận diện proxy được sinh bởi Spring/JDK, nhưng **không được parse tên class làm căn cứ duy nhất**.

Nếu bean name sai, context khác với production, hoặc `getBean` trả một raw target khác với object caller giữ, cần sửa **wiring** trước. Một điều quan trọng trong test: `context.getBean("paymentService")` chỉ chứng minh context có một bean cùng tên; cần chắc chắn **caller thực sự inject chính reference ấy**.

### 3.2. Bean đích và @Aspect là hai registrations riêng

Ví dụ:

~~~java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
    @Bean
    public PaymentApi paymentService() {
        return new PaymentService();
    }

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
~~~

Đây là snippet minh họa, các Java types và constructor cần tồn tại. `@EnableAspectJAutoProxy` bật auto-proxy infrastructure; `@Bean auditAspect` làm `@Aspect` class trở thành Spring bean. Chỉ viết `@Aspect` mà không `@Component`/`@Bean` sẽ không tự tạo instance aspect trong context này.

Nếu dùng `@ComponentScan`, phải kiểm tra package scan thực tế có chứa aspect. Nếu vừa `@Component` vừa `@Bean` cùng aspect ở hai bean names, có thể sinh advisors trùng. Tránh mở rộng scan toàn hệ thống khi muốn tái hiện lỗi trong một context tối thiểu.

### 3.3. Diagnostic bean names

~~~java
String[] names = context.getBeanNamesForType(PaymentApi.class);
System.out.println(java.util.Arrays.toString(names));
~~~

Đây là snippet trong Spring `ApplicationContext context`; danh sách có thể bao gồm nhiều bean implement interface. `@Primary`/`@Qualifier` và fallback resolution có thể khiến caller nhận bean khác với giả định. Không sửa pointcut cho tới khi xác minh đúng **bean ID**, **injected reference** và **target type**.

Nếu dùng `ProxyFactoryBean`, `getBean("service")` thông thường trả **proxy product**; `getBean("&service")` trả **FactoryBean**. Đừng nhầm factory object với proxy rồi kết luận Spring AOP chưa chạy.

## 4. Chặng 2 — xác minh Spring proxy và chiến lược proxying

Các phép kiểm tra Spring hỗ trợ:

~~~java
Object bean = context.getBean("paymentService");

System.out.println("aop=" + AopUtils.isAopProxy(bean));
System.out.println("jdk=" + AopUtils.isJdkDynamicProxy(bean));
System.out.println("cglib=" + AopUtils.isCglibProxy(bean));
System.out.println("targetType="
        + AopUtils.getTargetClass(bean).getName());
~~~

Các phương thức trên thuộc `org.springframework.aop.support.AopUtils`. Nếu bean đang được Spring proxy, `isAopProxy=true` thường là điều kiện đầu tiên phải thấy. Nhưng **proxy tồn tại không chứng minh một method cụ thể match pointcut hoặc advice đã chạy**.

| Kết quả | Diễn giải cần kiểm tra |
| --- | --- |
| `isAopProxy=false` | Có thể bean chưa được advise, chưa bật auto-proxy, pointcut không eligible, bean bị test mock thay thế, hoặc caller giữ raw target |
| `isJdkDynamicProxy=true` | Dùng interface proxy; caller phải gọi method expose qua interfaces |
| `isCglibProxy=true` | Dùng class-based proxy; kiểm tra `final`, visibility và các giới hạn subclass |
| `getTargetClass` khác `bean.getClass()` | Bình thường khi bean là proxy; điều kiện type trong pointcut cần xét target class thực, không so tên generated proxy |
| `isAopProxy=true` nhưng advice bạn quan tâm không chạy | Tiếp tục kiểm tra advisor chain, class/method matching, runtime arguments và caller path |

### 4.1. Các nguyên nhân không tạo proxy

Khi `AopUtils.isAopProxy(bean)` trả `false`, khoanh vùng theo **bằng chứng thiếu** thay vì đổi ngẫu nhiên `proxyTargetClass`:

| Nhóm kiểm tra | Bằng chứng nên lấy |
| --- | --- |
| Aspect/auto-proxy thiếu | Bean names của aspect, configuration của context sở hữu target |
| Bean tạo quá sớm | Thời điểm đăng ký `BeanPostProcessor`, cảnh báo `not eligible for auto-proxying` |
| Bean reference sai | Context owner, mock/bean override, raw object caller thực sự giữ |
| Advisor/pointcut không đủ điều kiện | Target class, method, pointcut class filter và method matcher |
| Proxy strategy không phù hợp | Interface được expose, `final`/visibility của method, root cause tạo proxy |

Nếu caller đang nhìn một bean của **parent context**, đừng kỳ vọng auto-proxy creator thuộc child tự động bọc lại bean đó. Nếu `isAopProxy=true` nhưng advice vắng, chuyển sang chặng 3 để kiểm tra đúng advisor/pointcut; một proxy transaction/cache không tự bảo đảm custom aspect cũng hiện diện.

### 4.2. Nested proxy và target source

Nếu proxy expose `Advised`, có thể đọc metadata và advisors. Với **singleton target**:

~~~java
Object inner = AopProxyUtils.getSingletonTarget(bean);
if (inner != null) {
    System.out.println("innerIsAopProxy="
            + AopUtils.isAopProxy(inner));
}
~~~

Cần import `org.springframework.aop.framework.AopProxyUtils`. Nếu `innerIsAopProxy=true`, bean có thể có hai lớp Spring AOP proxy. `null` không chứng minh chắc chắn chỉ có một lớp: scoped/lazy/pooled `TargetSource` có thể không expose singleton target.

**Không** gọi `((Advised) bean).getTargetSource().getTarget()` chỉ để inspect: thao tác ấy có thể kích hoạt lazy-init, lấy pooled target hoặc phá contract acquire/release. Trong quá trình chẩn đoán chỉ đọc proxy metadata khi có thể.

## 5. Chặng 3 — tìm advisor và kiểm tra pointcut

### 5.1. Quan sát advisors có mặt trên proxy hay không

~~~java
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    org.springframework.aop.Advisor[] advisors = advised.getAdvisors();

    for (int i = 0; i < advisors.length; i++) {
        System.out.println(i + " => " + advisors[i]);
    }
}
~~~

Đây là snippet cần biến `bean` và các Spring AOP classes. Proxy được cấu hình `opaque` có thể không expose `Advised` dù đang là AOP proxy. Nếu `bean instanceof Advised` false, đừng ép cast; quay về bean configuration/logs hoặc test context.

Danh sách advisors cho thấy **những cấu hình đã được gắn vào proxy**, không có nghĩa mọi advisor trong danh sách sẽ chạy ở mỗi method invocation. Spring có thể chèn infrastructure advisor như `ExposeInvocationInterceptor` hoặc advisors từ transaction/security/cache; vì vậy không so số lượng cứng với số methods trong `@Aspect`.

### 5.2. Cấu trúc PointcutAdvisor

Spring `Pointcut` có hai phần cơ bản:

- `ClassFilter`: target class nào có thể được advise.
- `MethodMatcher`: method nào match; có matcher chỉ dựa trên method/class (*static*) và matcher cần arguments runtime (*dynamic*).

Với `PointcutAdvisor`, có thể quan sát trực tiếp:

~~~java
if (advisor instanceof org.springframework.aop.PointcutAdvisor pa) {
    org.springframework.aop.Pointcut pointcut = pa.getPointcut();

    boolean classMatches =
            pointcut.getClassFilter().matches(targetClass);

    boolean methodMatches =
            pointcut.getMethodMatcher().matches(method, targetClass);

    System.out.println("class=" + classMatches);
    System.out.println("method=" + methodMatches);
}
~~~

Đây là snippet **không độc lập**: `advisor`, `targetClass`, `method` cần lấy trong cùng ngữ cảnh; `method` là đúng Java `Method` cần kiểm tra. `MethodMatcher.matches(method, targetClass)` cung cấp bước **static matching**; nếu matcher có `isRuntime() == true`, bạn **còn phải** kiểm tra điều kiện arguments ở thời điểm lời gọi.

Đối với dynamic matcher:

~~~java
var matcher = pointcut.getMethodMatcher();
if (matcher.isRuntime()) {
    boolean withArgs = matcher.matches(method, targetClass, actualArgs);
    System.out.println("runtime=" + withArgs);
}
~~~

Đoạn này chỉ minh họa khi đã có `pointcut`, `method`, `targetClass` và `Object[] actualArgs`. Không dùng `new Object[0]` để kết luận về một method thật được gọi với arguments khác.

### 5.3. AopUtils.canApply dùng để chẩn đoán advisor eligibility

Spring cung cấp:

~~~java
boolean can = AopUtils.canApply(
        pointcut, targetClass
);
~~~

Điều này có thể giúp kiểm tra `Pointcut` có khả năng áp dụng lên một **target class** không. Tuy nhiên, `canApply=true` không chứng minh một invocation cụ thể thoả **runtime matcher** hoặc rằng caller đã đi qua proxy. Chẩn đoán chính xác vẫn cần method/arguments thực và event ghi từ advice.

Một `IntroductionAdvisor` bổ sung interface ở mức type sử dụng class filter và introduced interfaces, **không phải** method pointcut thông thường. Nếu đang kiểm tra introduction, cần xem contract `IntroductionAdvisor` thay vì cưỡng ép mọi advisor thành `PointcutAdvisor`.

### 5.4. Những sai sót hay gặp trong @AspectJ expressions

| Pointcut | Ý nghĩa trong Spring AOP | Cách xác minh |
| --- | --- | --- |
| `execution(* demo.service.PaymentService.pay(..))` | Method `pay` của kiểu được chỉ định phù hợp | Kiểm tra FQCN, overloads, method signature, target class |
| `within(demo.service..*)` | Giới hạn method executions theo kiểu/phạm vi package | Class/package target thật có match không |
| `bean(*Service)` | Spring-only designator chọn bean name theo pattern | Bean ID thực tế khác target class name |
| `@annotation(demo.annotation.Audited)` | Method execution có annotation phù hợp | Annotation nằm ở method implementation hay interface? Proxy mode và metadata |
| `this(demo.api.PaymentApi)` | Proxy reference implement interface đó | JDK/CGLIB proxy type được expose |
| `target(demo.service.PaymentService)` | Target object thuộc target type | Không nhầm proxy class với target class |
| `args(java.lang.String)` | Runtime argument type constraints phù hợp | So đúng argument thực tế, kể cả null/overloads |

Với `execution`, `(..)` là 0 hoặc nhiều parameters, còn `()` là **không có parameter nào**. Một biểu thức nhắm `pay()` sẽ không match `pay(String id)`. Với `bean(name)`, dấu `*` là wildcard trên **Spring bean name**, không phải tên Java class.

**Chú ý:** pointcut designators như `call`, `get`, `set`, `cflow` không phải method-execution designators mà Spring proxy AOP hỗ trợ. Không dùng cú pháp của AspectJ weaving rồi mặc định Spring AOP proxy sẽ intercept field accesses hoặc constructor calls.

Khi một `@Pointcut` đặt trên method private trong aspect, đó là method *định nghĩa named pointcut*, không có nghĩa Spring AOP có thể advice mọi `private` business method. Hai phạm vi này cần được phân biệt.

## 6. Chặng 4 — xác minh advice thực sự thực thi

Sau khi xác minh advisor và pointcut, cần gắn quan sát **ngay trong advice method** và đối chiếu với caller method:

~~~java
@Around("execution(* demo.service.PaymentService.pay(..))")
public Object audit(ProceedingJoinPoint pjp) throws Throwable {
    log.add("advice:before");
    try {
        Object result = pjp.proceed();
        log.add("advice:after");
        return result;
    } catch (Throwable ex) {
        log.add("advice:error");
        throw ex;
    }
}
~~~

Đây là snippet cần class `@Aspect`, import `org.aspectj.lang.ProceedingJoinPoint`, `org.aspectj.lang.annotation.Around`, biến `log` và Spring configuration phù hợp. `proceed()` đi tiếp qua chain tới target khi không có advice khác short-circuit.

### 6.1. Phân biệt các kết quả runtime

- **Không thấy before:** có thể chưa đi qua proxy, không có advisor, pointcut không match, advice ở outer chain bị chặn, hoặc caller không gọi method dự kiến.
- **Có before, không có target:** một `@Around` có thể không gọi `proceed()`, có exception trước khi target chạy, cache/security advice trả hoặc ném kết quả sớm, hay có lỗi khi resolve target.
- **Có before và target, không có after:** target/advice phía trong có thể ném exception; dùng `catch`/ `finally` hoặc `@AfterThrowing` theo đúng semantics.
- **Có after nhưng caller nhận giá trị bất thường:** around advice có thể thay result, outer advisor có thể transform return, hoặc caller đang quan sát cache value.
- **Nhiều before nhưng target một lần:** kiểm tra nested proxies, duplicate advisors và những advice ở các lớp khác nhau.
- **Before một lần, target nhiều lần:** kiểm tra retry advice hoặc `proceed()` lặp; một around advice ở outer boundary có thể chỉ chạy một lần trong nhiều attempts.

Đừng dùng mỗi `System.out.println` để kết luận nếu logger có nhiều appenders hoặc log sink có at-least-once delivery. Một counter/test recorder trong advice và counter riêng trong target giúp phân biệt bản chất.

### 6.2. Advice ordering

Nếu có `@Before` và `@Around` ở nhiều aspects, Spring dùng advisor ordering/`@Order` và loại advice để thiết lập nesting. Giá trị order thấp hơn thường có ưu tiên cao hơn; nhưng **thứ tự không phải công cụ chữa pointcut không match**. `@Order` không xóa advisor bị đăng ký trùng và không khiến target tự được proxy.

Với retry, transaction hoặc caching, interceptor có thể làm thay đổi việc target được gọi. Khi debug phải biết advice đang nằm **trước hay sau** những advisors ấy trong chain. Một transaction advice chạy đúng cũng không tự chứng minh audit advice của bạn đã match.

## 7. Chặng 5 — đến đúng target method và giữ nguyên semantics

Khi `proceed()` tới đích, method body thực sự bắt đầu chạy. Nhưng target được chọn bởi `TargetSource`, có thể là singleton, prototype, pooled, scoped hay lazy; vì thế đừng suy luận class/identity từ generated proxy class.

Nếu chỉ muốn chẩn đoán **target class**, ưu tiên:

~~~java
Class<?> targetType = AopUtils.getTargetClass(proxy);
System.out.println(targetType.getName());
~~~

Không lấy raw target rồi gọi method để “thử” vì cách đó **bỏ qua chính Spring advice đang cần kiểm tra**. Với target source đặc biệt, `getTarget()` có thể tạo instance, kích hoạt lazy loading hoặc yêu cầu release theo lifecycle contract.

### 7.1. Self-invocation

~~~java
@Service
public class CheckoutService {

    public void place(String id) {
        charge(id);  // tương đương this.charge(id)
    }

    @Transactional
    public void charge(String id) {
        // ...
    }
}
~~~

Đây là snippet minh họa với transaction manager và transaction proxy được cấu hình. Nếu caller gọi `place` qua proxy, method `place` thực thi trên target và lời gọi nội bộ `this.charge` **không đi lại qua proxy**, nên không có lượt transaction advice riêng của `charge`. Nếu một transaction đã được mở bởi method ngoài thì code có thể vẫn chạy trong transaction đó; vấn đề là **advice của charge không được intercept lần nữa**.

Cách sửa được ưu tiên là tách `charge` sang một Spring bean khác rồi inject proxy ấy vào workflow. `AopContext.currentProxy()` chỉ là phương án cuối, cần `exposeProxy=true`, có coupling với Spring và có thể lỗi nếu không trong một proxy invocation thích hợp.

### 7.2. Direct-target bypass

Đoạn:

~~~java
PaymentService raw = new PaymentService();
raw.pay("X");
~~~

không đi qua Spring AOP proxy; cũng vậy nếu ai đó unwrap `Advised.getTargetSource().getTarget()` rồi gọi raw target. Đừng kết luận Spring bỏ qua advice vì bug nếu test đã chủ động bypass container/proxy.

### 7.3. Sự khác biệt giữa exception ở target và exception ở advice

Một exception được ném trong `@Around` **trước `proceed()`** làm target không chạy; exception do target ném có thể đi qua `@AfterThrowing`/`catch` của advice. Để debug, đặt recorder ở cả hai phía; test xác nhận exception type/message không bị aspect nuốt hay thay bất thường.

## 8. Ví dụ hoàn chỉnh Java 17 + Spring Framework 6.1.15

Ví dụ này dùng **Spring `AnnotationConfigApplicationContext` thật** với target, `@Aspect` và recorder; không cần Spring Boot hoặc database.

Điểm kiểm chứng:

1. Context tạo Spring JDK proxy cho bean `taskService`.
2. `AopUtils` xác nhận proxy và target class.
3. `AspectJExpressionPointcut` cho thấy `execute(String)` match còn `status()` không match.
4. Caller gọi `execute("A")` → advice before → target → advice after.
5. Caller gọi `status()` → target thôi.
6. Caller gọi `execute("BAD")` → advice before → target ném exception → advice error; exception giữ nguyên.

### 8.1. Cấu trúc dự án

~~~text
spring-aop-trace-pipeline/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── TaskApi.java
    │   ├── EventLog.java
    │   ├── TaskService.java
    │   ├── TraceAspect.java
    │   ├── AppConfig.java
    │   └── TraceDemo.java
    └── test/java/demo/
        └── TracePipelineTest.java
~~~

### 8.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-trace-pipeline</artifactId>
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
            <artifactId>spring-aop</artifactId>
            <version>6.1.15</version>
        </dependency>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjweaver</artifactId>
            <version>1.9.22.1</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration><release>17</release></configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>demo.TraceDemo</mainClass>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp ApplicationContext/`@Configuration`; `spring-aop` chứa proxy và introspection APIs; `aspectjweaver` phục vụ @AspectJ pointcut expression matching trong Spring AOP; JUnit dùng để kiểm thử kết quả. Demo **không bật compile-time/load-time AspectJ weaving**.

### 8.3. Contract nghiệp vụ

**src/main/java/demo/TaskApi.java**

~~~java
package demo;

public interface TaskApi {
    String execute(String id);
    String status();
}
~~~

JDK proxy expose `TaskApi`. Lấy bean theo interface thay vì ép sang `TaskService` implementation.

### 8.4. Event recorder

**src/main/java/demo/EventLog.java**

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public class EventLog {
    private final List<String> events = new ArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
~~~

Recorder này dùng trong demo/test chạy đơn luồng. Nếu test bất đồng bộ, thay collection bằng thread-safe implementation hoặc đồng bộ rõ ràng. `snapshot()` giữ nguyên thứ tự ghi để assert đường đi lời gọi.

### 8.5. Target method

**src/main/java/demo/TaskService.java**

~~~java
package demo;

public class TaskService implements TaskApi {
    private final EventLog log;

    public TaskService(EventLog log) {
        this.log = log;
    }

    @Override
    public String execute(String id) {
        log.add("target:execute:" + id);

        if ("BAD".equals(id)) {
            throw new IllegalArgumentException("bad-id");
        }

        return "ok:" + id;
    }

    @Override
    public String status() {
        log.add("target:status");
        return "READY";
    }
}
~~~

Target không mang `@Service`/ `@Component` vì `AppConfig` đăng ký trực tiếp một bean bằng `@Bean`. Nhờ đó test không scan các class khác và dễ nhìn rằng bean nào được proxy.

### 8.6. Aspect thật

**src/main/java/demo/TraceAspect.java**

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TraceAspect {
    private final EventLog log;

    public TraceAspect(EventLog log) {
        this.log = log;
    }

    @Around("execution(* demo.TaskService.execute(..))")
    public Object aroundExecute(ProceedingJoinPoint pjp)
            throws Throwable {
        log.add("advice:before");

        try {
            Object result = pjp.proceed();
            log.add("advice:after");
            return result;
        } catch (Throwable ex) {
            log.add("advice:error");
            throw ex;
        }
    }
}
~~~

`@Around` chỉ match `TaskService.execute(..)`; `status()` là negative-control method. Việc **giữ nguyên result** và **rethrow exception** là điều quan trọng: một advice sai có thể nuốt lỗi hoặc đổi business semantics dù pointcut match đúng.

### 8.7. Spring configuration tối thiểu

**src/main/java/demo/AppConfig.java**

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {

    @Bean
    EventLog eventLog() {
        return new EventLog();
    }

    @Bean
    TaskApi taskService(EventLog log) {
        return new TaskService(log);
    }

    @Bean
    TraceAspect traceAspect(EventLog log) {
        return new TraceAspect(log);
    }
}
~~~

Chỉ đăng ký ba beans của ứng dụng: recorder, target và aspect. Infrastructure khác do Spring `@EnableAspectJAutoProxy` đăng ký. `proxyTargetClass=false` và target có interface nên dùng JDK proxy trong ví dụ này.

### 8.8. Chương trình main: quan sát năm chặng

**src/main/java/demo/TraceDemo.java**

~~~java
package demo;

import java.lang.reflect.Method;

import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class TraceDemo {

    public static void main(String[] args) throws Exception {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskApi service = context.getBean("taskService", TaskApi.class);
            EventLog log = context.getBean(EventLog.class);

            // 1. Bean → 2. Proxy
            System.out.println("aop=" + AopUtils.isAopProxy(service));
            System.out.println("jdk="
                    + AopUtils.isJdkDynamicProxy(service));
            System.out.println("targetType="
                    + AopUtils.getTargetClass(service).getName());

            // 3. Kiểm tra class/method pointcut một cách chủ động.
            AspectJExpressionPointcut pointcut =
                    new AspectJExpressionPointcut();
            pointcut.setExpression(
                    "execution(* demo.TaskService.execute(..))"
            );
            Method execute = TaskService.class.getMethod(
                    "execute", String.class);
            Method status = TaskService.class.getMethod("status");

            System.out.println("matchExecute="
                    + pointcut.matches(execute, TaskService.class));
            System.out.println("matchStatus="
                    + pointcut.matches(status, TaskService.class));

            // Xem advisor chain nhưng không giả định một số lượng cứng.
            if (service instanceof Advised advised) {
                System.out.println("hasAdvisors="
                        + (advised.getAdvisors().length > 0));
            }

            // 4. Advice → 5. Target
            System.out.println("executeResult="
                    + service.execute("A"));
            System.out.println("statusResult="
                    + service.status());

            try {
                service.execute("BAD");
            } catch (IllegalArgumentException error) {
                System.out.println("caught=" + error.getMessage());
            }

            System.out.println("events="
                    + String.join("|", log.snapshot()));
        }
    }
}
~~~

`AspectJExpressionPointcut` ở đây được tạo để **minh họa static class/method matching** cho đúng biểu thức của `TraceAspect`. Nó không tự đăng ký một advisor mới vào proxy. `pointcut.matches(...)` không phải phép chứng minh rằng advice đã chạy — vì vậy chương trình còn gọi business methods qua **Spring bean proxy** và xem `EventLog`.

### 8.9. Chạy và kết quả

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chương trình tự in theo đúng cấu hình demo:

~~~text
aop=true
jdk=true
targetType=demo.TaskService
matchExecute=true
matchStatus=false
hasAdvisors=true
executeResult=ok:A
statusResult=READY
caught=bad-id
events=advice:before|target:execute:A|advice:after|target:status|advice:before|target:execute:BAD|advice:error
~~~

Giải thích lần lượt:

1. `aop=true`, `jdk=true`: caller thực sự có Spring JDK AOP proxy.
2. `targetType=demo.TaskService`: target class thực tế được dùng để xét `execution(...)`, không phải generated proxy class.
3. `matchExecute=true`, `matchStatus=false`: static pointcut check đúng phương thức dự kiến.
4. `hasAdvisors=true`: Spring proxy có advisors (không giả định mọi advisor đều match mọi method).
5. `execute("A")`: advice before → target → advice after; kết quả `ok:A`.
6. `status()`: chỉ target ghi event `target:status`; không match audit pointcut.
7. `execute("BAD")`: advice before → target ném exception → advice error; caller nhận `bad-id`.

Những dòng trên là **kết quả dự kiến từ source code**, không phải khẳng định Maven đã được chạy trên hệ thống đang làm bài. Maven/Spring logging có thể in thêm thông tin trong môi trường khác.

## 9. JUnit 5 kiểm tra cả positive và negative paths

**src/test/java/demo/TracePipelineTest.java**

~~~java
package demo;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

class TracePipelineTest {

    @Configuration(proxyBeanMethods = false)
    static class WithoutAopConfig {

        @Bean
        EventLog eventLog() {
            return new EventLog();
        }

        @Bean
        TaskApi taskService(EventLog log) {
            return new TaskService(log);
        }

        @Bean
        TraceAspect traceAspect(EventLog log) {
            return new TraceAspect(log);
        }
    }

    @Test
    void proxyExecutesAdviceAndTargetInOrder() {
        try (var ctx =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskApi service = ctx.getBean(TaskApi.class);
            EventLog log = ctx.getBean(EventLog.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("ok:Q1", service.execute("Q1"));
            assertEquals(
                    List.of("advice:before",
                            "target:execute:Q1",
                            "advice:after"),
                    log.snapshot());
        }
    }

    @Test
    void nonMatchingMethodExecutesTargetWithoutAdvice() {
        try (var ctx =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskApi service = ctx.getBean(TaskApi.class);
            EventLog log = ctx.getBean(EventLog.class);

            assertEquals("READY", service.status());
            assertEquals(List.of("target:status"), log.snapshot());
        }
    }

    @Test
    void advicePreservesExceptionFromTarget() {
        try (var ctx =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskApi service = ctx.getBean(TaskApi.class);
            EventLog log = ctx.getBean(EventLog.class);

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.execute("BAD")
            );
            assertEquals("bad-id", error.getMessage());
            assertEquals(
                    List.of("advice:before",
                            "target:execute:BAD",
                            "advice:error"),
                    log.snapshot());
        }
    }

    @Test
    void aspectBeanAloneWithoutEnableDoesNotCreateProxy() {
        try (var ctx =
                new AnnotationConfigApplicationContext(WithoutAopConfig.class)) {
            TaskApi service = ctx.getBean(TaskApi.class);
            EventLog log = ctx.getBean(EventLog.class);

            assertFalse(AopUtils.isAopProxy(service));
            assertEquals("ok:RAW", service.execute("RAW"));
            assertEquals(
                    List.of("target:execute:RAW"),
                    log.snapshot());
        }
    }

    @Test
    void rawNewBypassesSpringProxyEvenWhenContextExists() {
        EventLog log = new EventLog();
        TaskApi raw = new TaskService(log);

        assertFalse(AopUtils.isAopProxy(raw));
        assertEquals("ok:RAW", raw.execute("RAW"));
        assertEquals(
                List.of("target:execute:RAW"),
                log.snapshot());
    }
}
~~~

Các tests cố ý **tạo context riêng** qua `try-with-resources` để cô lập mutable recorder state. Test `WithoutAopConfig` vẫn đăng ký aspect bean nhưng **không bật `@EnableAspectJAutoProxy`**; nó là negative control chứng minh `@Aspect` class có mặt không đủ làm Spring tự advice target. Test `rawNew...` chứng minh caller bỏ qua Spring IoC không được proxy.

Chạy:

~~~bash
mvn -q test
~~~

Bộ test dự kiến gồm năm methods, không có failures nếu code compile và Spring configuration đúng:

~~~text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
~~~

Đây là **kỳ vọng của mã mẫu**, không phải báo cáo Surefire thực thi trên máy ở lượt này. Xem báo cáo `target/surefire-reports/demo.TracePipelineTest.txt` nếu cần đối chiếu trong môi trường riêng.

## 10. Quy trình debug có thể áp dụng vào ứng dụng thật

Sử dụng chuỗi sau và **chỉ đi tiếp khi chặng trước đã được xác minh**:

### Bước A — xác định ngữ cảnh và bean reference

- Chọn một request hoặc một test có correlation ID duy nhất.
- Xác định caller đang gọi method nào, args nào, thread nào, đã vào Spring context nào.
- Ghi `getBeanNamesForType` và injection qualifiers nếu có nhiều beans cùng contract.
- Kiểm tra target và aspect đều là beans trong context đang xử lý.
- Xác minh không phải raw `new`, FactoryBean dereference nhầm hoặc mock replacement.

**Bằng chứng cần có:** đúng bean ID, đúng injected reference, đúng context và một method call có thể tái hiện.

### Bước B — kiểm tra proxy object

- `AopUtils.isAopProxy(bean)`, `isJdkDynamicProxy`, `isCglibProxy`.
- `AopUtils.getTargetClass(bean)` và contract interface/concrete class của caller.
- Nếu proxy bị lồng, thử `AopProxyUtils.getSingletonTarget` trên static singleton target source.
- Kiểm tra auto-proxy creator đã sẵn sàng trong bean lifecycle.

**Bằng chứng cần có:** caller dùng Spring proxy dự kiến và hiểu rõ proxy strategy. Nếu không, dừng ở wiring/proxy lifecycle trước khi sửa advice.

### Bước C — kiểm tra pointcut/advisor eligibility

- `Advised.getAdvisors()` khi được hỗ trợ.
- Với `PointcutAdvisor`, xem `ClassFilter` và `MethodMatcher`; xét dynamic matcher nếu `isRuntime()`.
- Kiểm tra tên bean trong `bean(...)`, class FQCN trong `execution(...)`, annotation placement, overloads và method visibility.
- Đừng suy diễn chỉ từ `AopUtils.canApply`; nó chỉ cho biết **có khả năng** một pointcut áp dụng với class.

**Bằng chứng cần có:** advisor quan tâm hiện diện trên đúng proxy và target class/method/arguments thực có thể match.

### Bước D — chứng minh advice runtime

- Đặt recorder/counter tại entry/exit/error của advice.
- Ghi `joinPoint.getSignature()` và arguments đã che dữ liệu nhạy cảm.
- Xem `proceed()` có được gọi và bao nhiêu lần.
- Nếu có multiple advisors, kiểm tra order và trường hợp outer advice short-circuit/throw.

**Bằng chứng cần có:** xác định advice chạy hay không, chạy bao nhiêu lần, có tới `proceed()` hay không.

### Bước E — chứng minh target invocation và hậu quả

- Counter trong target method, hoặc repository/gateway mock đúng ranh giới để biết số lần gọi thật.
- Assert return/exception và business state.
- Kiểm tra self-invocation, raw-target bypass, cache hit, retry attempts, async execution và lifecycle of target source.
- Với transaction/caching/security, bổ sung test chuyên dụng để xem rollback/cache/permission behavior chứ không chỉ assert AOP proxy.

**Bằng chứng cần có:** target chạy đúng số lần, return/throw đúng, advice không gây side effect ngoài chủ đích.

## 11. Ma trận triệu chứng → chặng cần quay lại

| Triệu chứng | Chặng thường sai | Cách kiểm tra tiếp |
| --- | --- | --- |
| `NoSuchBeanDefinitionException` | Bean | Đúng bean name/context/scan/`@Bean`? |
| `NoUniqueBeanDefinitionException` | Bean | `@Qualifier`, `@Primary`, interface candidates |
| Có aspect class nhưng không advice | Bean/proxy | Aspect có Spring bean không, `@EnableAspectJAutoProxy` đã được nạp? |
| Caller dùng `new Service()` | Bean/proxy | Thay bằng injected Spring bean; test raw vs proxy |
| `isAopProxy=false` | Proxy/eligibility | Auto-proxy registration, pointcut coverage, early instantiation, mock override |
| JDK proxy cast sang implementation lỗi | Proxy strategy | Inject interface, không ép concrete class |
| `isAopProxy=true` nhưng advice vắng | Pointcut/advisor | Inspect `Advised`, class/method matching, bean name, arguments |
| Method private/final không được advise | Proxy capability | Xem JDK interface contract/CGLIB override limitations |
| `this.inner()` bypass advice | Target call path | Tách Spring beans, gọi qua injected proxy |
| Advice before có nhưng target không chạy | Advice/proceed/outer advisors | `proceed()`, cache/security short-circuit, exception |
| Target ném lỗi nhưng advice after không có | Advice control flow | `@AfterReturning` không chạy khi throw; dùng `@AfterThrowing`/`finally` đúng |
| Advice log hai lần, target một lần | Proxy/advisor | Nested proxies, duplicate advisors, AOP config overlap |
| Advice một lần, target ba lần | Advice/retry | Retry chain, repeated `proceed()`, target counter |
| Annotation trên interface nhưng không match | Pointcut metadata/proxy | Kiểm tra annotation placement và method resolution thực tế |
| Service method có `@Transactional` mà không mở transaction mới | Advice/target path | Self-invocation, outer transaction, proxy transaction management |
| Test mock service không có advice | Bean override | Mock collaborator, giữ real advised target |
| Scoped/lazy target thay đổi khi inspect | TargetSource lifecycle | Không gọi raw `getTarget()` tùy tiện |
| Runtime match khác static prediction | Pointcut | `isRuntime()`, args, type/annotation runtime |
| Có nhiều advisors nhưng không cùng chạy | Advisor eligibility | Pointcut mỗi advisor có thể khác; order và short-circuit |

## 12. Tránh bẫy khi quan sát Spring AOP

### 12.1. Đừng gọi getTarget() để kiểm tra mọi thứ

Khi một proxy dùng `LazyInitTargetSource`, `PrototypeTargetSource`, pooling hoặc scoped target source, việc đọc raw target có thể kích hoạt khởi tạo/tài nguyên mà production path chưa sử dụng hoặc đòi `releaseTarget()`. Dùng `AopUtils.getTargetClass`, `Advised.getAdvisors` và static `getSingletonTarget` khi phù hợp.

### 12.2. Đừng suy luận “có proxy” là “pointcut match”

Spring proxy có thể chứa transaction advisor, nhưng `@Around` audit aspect của bạn không tồn tại hoặc không match. Vì vậy `isAopProxy=true` chỉ hoàn thành chặng **proxy**, không phải năm chặng.

### 12.3. Đừng thử trực tiếp target rồi so với expected advice

Nếu test lấy `AopTestUtils.getUltimateTargetObject(proxy)` để setup Mockito spy, việc gọi raw target chỉ chứng minh method business chạy. Để kiểm thử advice, hãy gọi **outer Spring proxy**. Với dynamic target sources, unwrap tới cùng có thể không an toàn.

### 12.4. Đừng dùng println mà quên correlation ID

Một HTTP request có thể gây nhiều method invocations, async tasks hoặc retries. Ghi `requestId`, `beanName`, method, `attempt` và stage. Hai log "before" có thể thuộc **hai requests khác nhau**, không nhất thiết duplicate advice.

### 12.5. Đừng bật class-based proxy như một sửa lỗi phổ thông

`proxyTargetClass=true` chỉ thay đổi JDK/CGLIB proxy strategy, có thể làm ứng dụng khác semantics: class final, method final/private, visibility, JPMS. Nó không bổ sung `@Aspect` bean, không sửa context sai và không giải quyết self-invocation.

### 12.6. Đừng thay đổi @Order khi thiếu advisor

`@Order` điều chỉnh thứ tự advisors đã có, **không khiến một aspect bean tự đăng ký** hoặc pointcut bất hợp lệ trở thành hợp lệ. Bước kiểm tra phải đi từ bean → proxy → advisor trước khi xét ordering.

### 12.7. Đừng tin debugger gọi method là hoàn toàn vô hại

Một số IDE đánh giá `toString()`, `equals()`, properties/getters, hoặc object inspectors trên proxy và có thể kích hoạt advice, lazy-loading hoặc transaction metadata. Khi điều tra những counters nhạy cảm, tránh debugger evaluation có side effect và dùng recorder rõ ràng.

## 13. Checklist dùng trước khi mở bug hoặc code review

- [ ] Tôi biết đúng **Spring ApplicationContext** và bean ID được caller sử dụng.
- [ ] Tôi đã xác minh target bean và aspect bean được đăng ký thực sự.
- [ ] Tôi không đang dùng raw `new` object, một bean mock thay thế, hoặc `&FactoryBean` nhầm.
- [ ] Tôi đã kiểm tra `AopUtils.isAopProxy` trên **actual caller reference**.
- [ ] Tôi biết proxy strategy và interface/method visibility phù hợp.
- [ ] Tôi đã kiểm tra `Advised.getAdvisors()` hoặc cấu hình tương đương nếu proxy không expose `Advised`.
- [ ] Tôi biết pointcut nào match class/method, có dynamic matcher nào cần runtime arguments.
- [ ] Tôi đã kiểm tra bean name pattern, overloads và annotation placement.
- [ ] Tôi có recorder độc lập trong advice method cho before/after/error.
- [ ] Tôi biết advice có gọi `proceed()` và bao nhiêu lần.
- [ ] Tôi có counter/quan sát độc lập ở target method.
- [ ] Tôi đã loại trừ self-invocation, raw-target bypass và nested proxies ngoài ý muốn.
- [ ] Tôi đã xem cache/retry/transaction/security advisor có đổi đường đi invocation không.
- [ ] Tôi có ít nhất một positive test (method match) và một negative test (method không match).
- [ ] Test kiểm tra đúng return/exception/business effect, không chỉ nhìn generated proxy class.
- [ ] Tôi đã giữ nguyên lifecycle và không unwrap dynamic `TargetSource` để debug.
- [ ] Tôi có một regression test cố định để tránh bug quay lại sau khi thay cấu hình Spring.

## 14. Kết luận

Quy trình chẩn đoán đáng tin nhất với Spring Aspect là **bean → proxy → pointcut → advice → phương thức đích**. Mỗi chặng có một câu hỏi và một bằng chứng cần thu thập. Nếu bean/aspect không được đăng ký, đừng vội sửa pointcut; nếu caller chưa gọi proxy, đừng vội sửa advice; nếu pointcut không match, đừng kết luận target code sai; nếu advice chạy nhưng target không, hãy kiểm tra `proceed()`, outer advisors và short-circuit; nếu target chạy nhưng advice không, hãy quay lại lời gọi `this` hoặc raw target.

`AopUtils`, `Advised`, `PointcutAdvisor`, `ClassFilter`, `MethodMatcher` và một context kiểm thử nhỏ giúp nhìn đúng từng ranh giới của **Spring proxy-based AOP**. Hãy luôn kết hợp **proxy metadata**, **đối chiếu pointcut** và **behavioral assertions**, thay vì chỉ đọc annotations hoặc log rời rạc.

### Tài liệu chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- [Spring Framework — Declaring Pointcuts with @AspectJ](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html).
- [Spring Framework — Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html).
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html).
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html).
- [Spring Framework — Creating AOP Proxies Programmatically](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html).
- [Spring Framework Javadoc — AopUtils](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/aop/support/AopUtils.html).
- [Spring Framework Javadoc — Advised](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/aop/framework/Advised.html).


</details>

- [Quay lại đầu trang](#back-to-top)
