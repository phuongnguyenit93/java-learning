<a id="back-to-top"></a>

# Advice theo sự kiện

## Menu
- [@Before và hành vi trước khi gọi phương thức đích](#advice-lifecycle-001)
- [@AfterReturning và ràng buộc kết quả trả về](#advice-lifecycle-002)
- [@AfterThrowing và ràng buộc kiểu ngoại lệ](#advice-lifecycle-003)
- [@After với ngữ nghĩa finally trong Spring AOP](#advice-lifecycle-004)
- [Phân biệt đường thành công, đường ngoại lệ và kết thúc phương thức](#advice-lifecycle-005)
- [Chọn loại advice ít quyền can thiệp nhất đáp ứng yêu cầu](#advice-lifecycle-006)
- [Giới hạn của @AfterReturning khi muốn thay đối tượng trả về](#advice-lifecycle-007)
- [Giới hạn của @AfterThrowing khi muốn phục hồi lỗi](#advice-lifecycle-008)
- [Chữ ký phương thức advice và cấu hình returning, throwing](#advice-lifecycle-009)
- [Ví dụ ghi nhận lời gọi trên bean Spring với từng loại advice](#advice-lifecycle-010)

## <a id="advice-lifecycle-001">@Before và hành vi trước khi gọi phương thức đích</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP `@Before` — hành vi trước khi thực thi phương thức đích

## 1. Ý nghĩa của `@Before` trong Spring Aspect

`@Before` thuộc `org.aspectj.lang.annotation.Before`, dùng trong Spring AOP theo kiểu `@AspectJ` để khai báo **advice chạy trước một method execution mà pointcut chọn**, khi lời gọi thực tế đi qua **Spring AOP proxy**.

```java
@Aspect
@Component
public class TraceAspect {
    @Before("execution(* demo.before.SubmissionService.submit(..))")
    public void beforeSubmit() {
        System.out.println("[advice] before submit");
    }
}
```

Đoạn trên là **fragment minh họa** chưa gồm imports/package và đối tượng Spring target. Ý nghĩa:

- `@Aspect` đánh dấu class khai báo aspect; `@Component` hoặc phương thức `@Bean` làm aspect trở thành **Spring-managed bean**.
- Expression `execution(* demo.before.SubmissionService.submit(..))` chọn method `submit(..)` ở class đích tương ứng; không chọn tất cả method của ứng dụng.
- Khi một consumer gọi method **qua proxy** và pointcut khớp, Spring chạy `beforeSubmit()` trước khi cho execution tiếp tục đến method đích.
- Nếu advice hoàn thành bình thường, Spring tiếp tục chuỗi interceptor và thông thường đi đến target method, trừ trường hợp interceptor khác thay đổi luồng.
- Nếu `beforeSubmit()` **ném exception**, lời gọi không tiếp tục bình thường đến method đích; exception được truyền lên caller, trừ khi một interceptor khác xử lý nó.

**Phạm vi:** Spring AOP proxy-based, Spring IoC bean, pointcut và `@Aspect`. Đây không phải khái niệm AOP tổng quát hoặc cơ chế AspectJ compile-time/load-time weaving.

## 2. “Trước khi gọi phương thức đích” là trước thời điểm nào?

Trong Spring AOP, join point chính được hỗ trợ là **method execution** trên Spring-managed bean. Advice `@Before` chạy trước khi thân method đích được thực thi trong invocation được proxy chặn, không phải trước mọi thao tác Java dẫn tới method call.

```text
Consumer / controller (được Spring inject service)
             |
             | service.submit("S-001")
             v
       Spring AOP proxy
             |
     kiểm tra advisor/pointcut
             |
       interceptor chain
             |
         @Before advice
             |
       (advice kết thúc bình thường)
             |
             v
       Target.submit("S-001")
             |
             v
          Return / throw
             |
             v
         Quay lại caller
```

Quan sát cụ thể:

1. **Argument expression ở call site** đã được Java đánh giá trước khi method invocation đến proxy. `@Before` không chạy trước việc tạo ra argument, ví dụ `service.submit(buildRequest())`.
2. Target Spring bean đã trải qua khởi tạo và được proxy công bố (theo lifecycle thông thường); `@Before` **không phải** callback trước constructor hay trước `@PostConstruct`.
3. Trong chain có thể có các advice/interceptor khác. `@Before` được chạy **trước target execution**, nhưng không có bảo đảm nó là thành phần đầu tiên chạy trên toàn bộ request.
4. Advice được gọi cho **mỗi invocation được intercept và match**, không chỉ một lần khi context khởi động.
5. Nếu target có method đã match nhưng caller tự gọi `this.submit()` bên trong target, lời gọi này **không đi qua Spring proxy** và `@Before` sẽ không được kích hoạt riêng cho lời gọi nội bộ đó.

Một Spring method có thể được gọi bình thường dù **không** có advice nếu bean không được proxy, pointcut không khớp, hoặc lời gọi bypass proxy.

## 3. Điều kiện để `@Before` hoạt động

Trong một ứng dụng Spring Framework dùng annotation-style AOP, cần tất cả yếu tố sau:

1. **Target là Spring bean**: ví dụ class có `@Service` được component scan hoặc được đăng ký qua `@Bean`.
2. **Aspect cũng là Spring bean**: thường `@Aspect @Component` hoặc `@Bean`. `@Aspect` một mình không đồng nghĩa component scanner tự đăng ký bean.
3. **Hạ tầng Spring AOP được bật**: ví dụ `@EnableAspectJAutoProxy` trong `@Configuration` của Spring Framework thuần; Spring Boot có cơ chế auto-configuration khi dependency thích hợp sẵn có.
4. **Pointcut match phương thức**: phương thức đích phải thuộc class/package, tên, visibility, argument/annotation constraints do expression mô tả.
5. **Lời gọi đi qua proxy**: consumer dùng tham chiếu do Spring cung cấp (qua DI hoặc `ApplicationContext.getBean(...)`), không dùng object tự `new` rồi gọi trực tiếp.
6. **Method có thể intercept bằng loại proxy thực tế**: JDK dynamic proxy chủ yếu chặn public interface methods; CGLIB class proxy phụ thuộc khả năng subclass/override và có giới hạn với `final`/`private`.

### Cấu hình trong Spring Framework thuần

```java
package demo.before;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy
public class AppConfig {
}
```

Với ứng dụng Java 17+ Spring Framework 6.x và `@AspectJ`, thường dùng dependency Spring Context, Spring AOP và `aspectjweaver` để Spring phân tích pointcut. Không cần AspectJ compiler hoặc AspectJ load-time weaver để chạy **Spring AOP proxy**.

Trong Spring Boot, starter AOP có thể bật hạ tầng AOP. Spring Boot thường mặc định dùng CGLIB class-based proxy khi auto-configured AOP; Spring Framework thuần với `@EnableAspectJAutoProxy(proxyTargetClass=false)` ưu tiên JDK proxy nếu target có interface. Dù loại proxy nào, vẫn phải gọi **qua proxy** và có pointcut khớp.

## 4. Cách viết expression cho `@Before`

Giá trị annotation nhận **pointcut expression** trực tiếp hoặc tên của `@Pointcut` đã khai báo.

### 4.1. Inline pointcut

```java
@Before("execution(public * demo.before.SubmissionService.submit(..))")
public void beforeSubmit() {
    System.out.println("before submit");
}
```

Trong biểu thức:

- `execution(...)`: chọn method execution.
- `public`: chỉ public methods.
- `*`: return type bất kỳ.
- `demo.before.SubmissionService.submit`: type và method name.
- `(..)`: chấp nhận mọi danh sách argument types.

Nếu cần chính xác một argument kiểu `String`, có thể dùng:

```java
@Before("execution(* demo.before.SubmissionService.submit(java.lang.String))")
public void beforeSubmitString() {
    System.out.println("before String submit");
}
```

Các snippets này là phương án minh họa riêng, không dùng đồng thời với advice khác của cùng ví dụ nếu không chủ ý chạy nhiều advice.

### 4.2. Dùng named pointcut

```java
@Pointcut("execution(* demo.before.SubmissionService.submit(..))")
private void submissionOperation() {}

@Before("submissionOperation()")
public void auditSubmission() {
    System.out.println("before submission");
}
```

Đây là các method nằm **bên trong aspect**. Method `submissionOperation()` chỉ là tên khai báo pointcut; Java code trong body của nó không được gọi như business logic khi Spring đánh giá pointcut.

Visibility `private` của method `@Pointcut` chỉ ảnh hưởng quyền tham chiếu named pointcut theo luật Java, **không giới hạn target methods thành private**.

### 4.3. Ghép nhiều điều kiện với `&&`, `||`, `!`

```java
@Before(
    "within(demo.before..*)"
  + " && (execution(* submit*(..)) || execution(* cancel*(..)))"
  + " && !execution(* *Internal(..))"
)
public void beforePublicApi(JoinPoint joinPoint) {
    System.out.println(joinPoint.getSignature().getName());
}
```

Snippet này minh họa logic điều kiện. Muốn **chỉ public** hãy thêm `&& execution(public * *(..))`; việc đặt tên `beforePublicApi` không tự tạo ràng buộc visibility trong expression.

Dấu ngoặc quan trọng: `A || B && !C` được hiểu là `A || (B && !C)`, **khác** `(A || B) && !C`. Một điều kiện phủ định thiếu ngoặc có thể loại nhầm hoặc bỏ sót method.

### 4.4. Match theo Spring bean hoặc annotation

Spring có pointcut designator riêng `bean(...)` (lọc theo bean name):

```java
@Before("bean(*SubmissionService) && execution(* submit(..))")
public void beforeNamedBeans() {
    System.out.println("before named bean");
}
```

Đây chỉ là ví dụ expression: bean name thực tế phải khớp mẫu; mặc định `@Service` trên `SubmissionService` thường cho tên bean `submissionService`, khác hoa/thường. Mẫu `*SubmissionService` không tự match bean `submissionService` nếu quy tắc ký tự không phù hợp. Với tên bean theo mặc định, có thể thay bằng `bean(submissionService)`. Do đó phải kiểm tra bean name thực tế.

Với method annotation tùy chỉnh, có thể dùng `@annotation(com.example.Audited)` để chọn những method có annotation được nhận diện theo quy tắc pointcut. Annotation phải là một type hợp lệ, được đăng ký/đặt nơi framework có thể quan sát; ví dụ không tuyên bố custom annotation tự có behavior trước khi có aspect.

## 5. Dữ liệu advice có thể đọc bằng `JoinPoint`

Spring AOP có thể truyền `org.aspectj.lang.JoinPoint` làm **tham số đầu tiên** trong before advice:

```java
@Before("execution(* demo.before.SubmissionService.submit(..))")
public void beforeSubmit(JoinPoint joinPoint) {
    String methodName = joinPoint.getSignature().getName();
    Object[] args = joinPoint.getArgs();
    System.out.println("[advice] " + methodName + ": " + args.length);
}
```

Với `JoinPoint`:

| API | Ý nghĩa trong Spring AOP |
| --- | --- |
| `joinPoint.getSignature()` | Method signature, trong đó có tên method |
| `joinPoint.getArgs()` | Array chứa argument values của invocation hiện tại |
| `joinPoint.getThis()` | Reference **Spring AOP proxy** |
| `joinPoint.getTarget()` | Target object đứng sau proxy |

Lưu ý `getThis()` và `getTarget()` có thể là **hai object khác nhau** khi Spring dùng proxy. Với JDK proxy, `getThis()` có kiểu proxy interface, không phải concrete implementation. Không cast tùy ý sang implementation class.

`getArgs()` cho phép đọc argument để ghi log, metric hoặc kiểm tra; đừng tự ý log mật khẩu, token, dữ liệu cá nhân hoặc toàn bộ payload nhạy cảm. Nó không tạo một contract ổn định để **thay đổi argument mà target sẽ nhận** bằng cách gán lại phần tử trong array do `getArgs()` trả về.

### Bind argument qua `args(...)`

Thay vì tự đọc array, có thể bind argument vào advice parameter:

```java
@Before(
    value = "execution(* demo.before.SubmissionService.submit(..)) && args(orderId)",
    argNames = "orderId"
)
public void beforeSubmit(JoinPoint joinPoint, String orderId) {
    System.out.println("[advice] submit " + orderId);
}
```

Đây là một fragment alternative. `JoinPoint` là tham số đặc biệt đầu tiên; `argNames` ghi rõ cách bind `orderId` cho các trường hợp không có Java parameter-name metadata. Nếu pointcut thực tế có nhiều arguments, cần biểu thức `args(...)` và signature tương ứng. Không suy đoán rằng annotation tự biết tên argument khi không được cung cấp metadata/binding thích hợp.

### Dữ liệu nào không sẵn trước execution?

Before advice không có **giá trị trả về** hoặc **exception do target sắp ném** vì target chưa chạy. Do đó:

- Muốn xử lý return value sau khi method thành công, dùng `@AfterReturning`.
- Muốn xử lý exception do target method ném, dùng `@AfterThrowing`.
- Muốn code chạy sau method dù thành công/thất bại, dùng `@After` với finally semantics.
- Muốn chủ động kiểm soát có `proceed()` hay không, thay arguments khi proceed hoặc thay return value theo contract, cân nhắc `@Around`.

`@Before` là lựa chọn rõ nhất khi chỉ cần chuẩn bị, log, bắt đầu đo, validate một điều kiện và cho method tiếp tục bình thường.

## 6. Nếu `@Before` ném ngoại lệ thì sao?

Điều kiện “advice chạy trước method” dễ khiến người học nghĩ before advice **không thể** ngăn method thực thi. Spring mô tả chính xác: trước advice **không thể chủ động quyết định bỏ qua execution** như `@Around`; **ngoại lệ duy nhất là khi advice ném exception**, khiến luồng execution bình thường bị ngắt.

Ví dụ:

```java
@Before("execution(* demo.before.SubmissionService.submit(..))")
public void validateBefore(JoinPoint jp) {
    String id = (String) jp.getArgs()[0];
    if ("DENY".equals(id)) {
        throw new IllegalArgumentException("blocked by before advice");
    }
}
```

Đoạn này là một phương án advice minh họa, không phải ví dụ đầy đủ độc lập. Khi caller gọi `service.submit("DENY")` qua proxy:

1. Pointcut match và advice bắt đầu.
2. Advice ném `IllegalArgumentException`.
3. Target `submit("DENY")` **không được gọi** trong luồng bình thường của invocation này.
4. Exception được truyền ngược qua interceptor chain, nơi những interceptor bao ngoài có thể quan sát/xử lý nó.
5. Advice `@AfterThrowing` đang áp dụng lên **target method** cũng có hành vi phụ thuộc chuỗi interceptor/advice và loại exception; không nên mặc định mọi after-throwing advice đều có log theo đúng một thứ tự nếu chưa cấu hình ordering.

**Điểm không được nhầm:** ném exception trong before advice có thể từ chối cuộc gọi, nhưng điều này không biến before advice thành `@Around`. Before advice không có `ProceedingJoinPoint.proceed()`, không thể tự chọn return value thay thế cho caller.

Cần thận trọng nếu dùng `@Before` để làm authorization: các phương thức không đi qua proxy (self-invocation, object tự tạo) **không được aspect kiểm tra**. Bảo đảm an ninh nên dùng kiến trúc ranh giới đáng tin và Spring Security hoặc các kiểm soát bổ trợ thích hợp, thay vì coi custom `@Before` là chốt bảo vệ duy nhất.

## 7. Ví dụ chạy được — Java 17 + Spring Framework 6.2 + Maven

Ví dụ dưới đây không dùng Spring Boot để thể hiện rõ điều kiện tạo Spring AOP proxy. Các hành vi được kiểm chứng qua log:

1. `@Before` chạy trước `submit("OK")`.
2. `@Before` có thể **ném exception**, khiến `submit("DENY")` không chạy.
3. Nếu **target tự ném exception** sau khi before advice kết thúc, before advice vẫn đã chạy.
4. Pointcut không khớp `cancel(...)` thì không có advice.
5. Target `runBatch()` tự gọi `this.submit("INNER")` nên lời gọi nội bộ bypass proxy.

### 7.1. Cấu trúc file

```text
spring-before-advice/
├── pom.xml
└── src/main/java/demo/before/
    ├── AppConfig.java
    ├── SubmissionOperations.java
    ├── SubmissionService.java
    ├── AuditAspect.java
    └── Main.java
```

### 7.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-before-advice</artifactId>
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
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
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

Các thư viện này đủ cho ứng dụng Spring Framework thuần dùng `@AspectJ` theo kiểu Spring AOP proxy. Cần Maven và JDK 17+ có khả năng tải dependencies từ repository.

### 7.3. `AppConfig.java`

```java
package demo.before;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
```

`@ComponentScan` phát hiện target `@Service` và aspect `@Component`. `@EnableAspectJAutoProxy` kích hoạt hạ tầng tạo AOP proxy. Với target có interface và `proxyTargetClass=false`, ví dụ dùng **JDK dynamic proxy**.

### 7.4. `SubmissionOperations.java`

```java
package demo.before;

public interface SubmissionOperations {
    void submit(String id);
    void cancel(String id);
    void runBatch();
}
```

Spring có thể tạo JDK proxy triển khai interface này. Consumer nhận dependency theo `SubmissionOperations` chứ không giả định proxy kế thừa class `SubmissionService`.

### 7.5. `SubmissionService.java`

```java
package demo.before;

import org.springframework.stereotype.Service;

@Service
public class SubmissionService implements SubmissionOperations {
    @Override
    public void submit(String id) {
        System.out.println("[target] submit " + id);
        if ("TARGET_FAIL".equals(id)) {
            throw new IllegalStateException("target failed");
        }
    }

    @Override
    public void cancel(String id) {
        System.out.println("[target] cancel " + id);
    }

    @Override
    public void runBatch() {
        System.out.println("[target] batch");
        this.submit("INNER");
    }
}
```

Method `submit("TARGET_FAIL")` **in ra dòng target trước**, rồi ném exception. Điều này cho phép đối chiếu thứ tự `@Before` với logic target. `this.submit("INNER")` là self-invocation, nên sẽ không tự đi qua proxy.

### 7.6. `AuditAspect.java`

```java
package demo.before;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    @Before("execution(* demo.before.SubmissionService.submit(..))")
    public void beforeSubmit(JoinPoint joinPoint) {
        String id = (String) joinPoint.getArgs()[0];

        System.out.println("[advice] before submit " + id);

        if ("DENY".equals(id)) {
            throw new IllegalArgumentException("blocked by before advice");
        }
    }
}
```

Advice có đúng một declaration và chỉ match `submit(..)`. Nó không match `cancel()` hay `runBatch()`. Để minh họa từ chối lời gọi, advice ném exception khi `id` là `"DENY"`. Trong production, không dùng in log dữ liệu người dùng tùy tiện và không xem custom aspect là thay thế đầy đủ cho bảo mật.

### 7.7. `Main.java`

```java
package demo.before;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            SubmissionOperations service =
                    context.getBean(SubmissionOperations.class);

            System.out.println("[main] jdkProxy="
                    + AopUtils.isJdkDynamicProxy(service));

            service.submit("OK");

            try {
                service.submit("DENY");
            } catch (IllegalArgumentException ex) {
                System.out.println("[caller] " + ex.getMessage());
            }

            try {
                service.submit("TARGET_FAIL");
            } catch (IllegalStateException ex) {
                System.out.println("[caller] " + ex.getMessage());
            }

            service.cancel("C-1");
            service.runBatch();
        }
    }
}
```

Chạy từ thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.before.Main
```

**Stdout từ các câu lệnh `System.out` của chương trình** (không tính log Maven/Spring có thể phát sinh tùy môi trường):

```text
[main] jdkProxy=true
[advice] before submit OK
[target] submit OK
[advice] before submit DENY
[caller] blocked by before advice
[advice] before submit TARGET_FAIL
[target] submit TARGET_FAIL
[caller] target failed
[target] cancel C-1
[target] batch
[target] submit INNER
```

### 7.8. Phân tích stdout từng lời gọi

| Lời gọi từ `Main` | Advice `@Before` | Target | Kết quả |
| --- | --- | --- | --- |
| `submit("OK")` | In `before submit OK` | In `submit OK` | Hoàn tất bình thường |
| `submit("DENY")` | In `before submit DENY` rồi ném exception | **Không chạy** | Caller bắt `IllegalArgumentException` |
| `submit("TARGET_FAIL")` | In `before submit TARGET_FAIL` | In `submit TARGET_FAIL` rồi ném exception | Caller bắt `IllegalStateException` |
| `cancel("C-1")` | Không khớp pointcut | In `cancel C-1` | Hoàn tất bình thường |
| `runBatch()` | Không khớp pointcut của chính method này | In `batch` | Gọi nội bộ `this.submit("INNER")` |
| `this.submit("INNER")` trong target | **Không chạy** | In `submit INNER` | Self-invocation bypass proxy |

Ví dụ cố ý chỉ có một advice cho method `submit()` để tránh phụ thuộc vào thứ tự của nhiều before advices. Code không tự chạy advice khi `this.submit()` từ target vì đây là lời gọi không đi qua Spring proxy.

### 7.9. Thử nghiệm tự kiểm tra

**Thử nghiệm A — Đổi pointcut sang `cancel(..)`**

Thay annotation trong `AuditAspect` bằng:

```java
@Before("execution(* demo.before.SubmissionService.cancel(..))")
```

Khi chạy lại, chỉ `cancel("C-1")` từ caller qua proxy mới kích hoạt advice. Chú ý ví dụ hiện tại vẫn có tên log “before submit” và logic DENY: **đó chỉ là nhãn/logic cũ của advice**; khi thực hiện thí nghiệm thật nên đổi tên log và validation tương ứng với `cancel`. Không suy ra dữ liệu log tự đổi theo pointcut.

**Thử nghiệm B — Bỏ `@Component` trên aspect**

Nếu aspect không còn được Spring quản lý qua `@Component` hoặc `@Bean`, advice không được đăng ký. Target vẫn có thể là Spring bean, nhưng không còn before advice này.

**Thử nghiệm C — Dùng `new SubmissionService()`**

```java
SubmissionOperations outsideSpring = new SubmissionService();
outsideSpring.submit("OK");
```

Snippet trên tạo object Java không phải Spring bean proxy. Dòng `[target] submit OK` vẫn xuất hiện, nhưng không có `[advice]` trước nó.

**Thử nghiệm D — Chuyển sang CGLIB**

Thay `@EnableAspectJAutoProxy(proxyTargetClass = true)`. Target không final nên class proxy hợp lệ. Trong `Main`, `isJdkDynamicProxy(service)` sẽ thành `false`, nhưng `@Before` vẫn có thể chạy qua proxy; `this.submit("INNER")` vẫn bypass.

## 8. `@Before` so với các loại advice khác trong Spring

| Advice trong Spring AOP | Thời điểm | Có return/exception của target? | Khả năng kiểm soát |
| --- | --- | --- | --- |
| `@Before` | Trước method execution được pointcut chọn | Chưa có return/exception của target | Không chủ động `proceed()`/thay return; có thể ngắt bằng exception |
| `@AfterReturning` | Sau khi target hoàn tất bình thường | Có thể bind return value | Quan sát kết quả thành công; không dùng như `@Around` để tự thay return |
| `@AfterThrowing` | Khi execution kết thúc bằng exception | Có thể bind exception | Ghi log/thực hiện handling liên quan lỗi |
| `@After` | Sau execution bất kể kết thúc bình thường hay exceptional, theo finally semantics | Không dùng để thay return | Dọn dẹp/tác vụ cuối |
| `@Around` | Bao quanh join point | Có thể quan sát return/exception qua `proceed()` | Chủ động chọn thời điểm/có gọi `proceed()` và return gì |

Mục tiêu trước method **thực sự cần làm gì?**

- Nếu chỉ cần log/metric trước khi target bắt đầu, `@Before` có ý nghĩa rõ ràng.
- Nếu cần đo chính xác duration **bao gồm kết quả method**, một `@Around` với `try/finally` có thể phù hợp hơn.
- Nếu cần đổi return value, bỏ qua method mà không ném exception, hoặc thay arguments khi proceed, đó là hành vi của `@Around`, không phải `@Before`.
- Nếu cần xử lý exception sau target, đừng cố đoán exception tương lai trong before advice; dùng loại advice phù hợp.

Dùng loại advice **ít quyền lực nhất đáp ứng yêu cầu** để dễ đọc và giảm sai sót trong Spring application.

## 9. Thứ tự của nhiều before advices

Khi một method được nhiều aspects/advisors match, bạn không thể suy ra thứ tự log từ vị trí class trong thư mục, thứ tự bean được scan, hoặc thứ tự khai báo source code.

Spring hỗ trợ chỉ định precedence cho các aspect khác nhau bằng `@Order` hoặc interface `Ordered`. **Giá trị order nhỏ hơn thể hiện precedence cao hơn** ở những nơi Spring áp dụng quy tắc ordering này.

```java
@Aspect
@Component
@Order(1)
public class FirstAspect {
    @Before("execution(* demo.before.SubmissionService.submit(..))")
    public void first() {
        System.out.println("[first] before");
    }
}
```

```java
@Aspect
@Component
@Order(2)
public class SecondAspect {
    @Before("execution(* demo.before.SubmissionService.submit(..))")
    public void second() {
        System.out.println("[second] before");
    }
}
```

Đây là hai fragments cần imports `org.springframework.core.annotation.Order`, `org.aspectj.lang.annotation.Aspect`, `org.aspectj.lang.annotation.Before` và `org.springframework.stereotype.Component`. Nếu đăng ký cả hai và target method khớp, aspect precedence cao hơn thường chạy trước trong đường vào. Nhưng còn các interceptor như transaction/security và thứ tự advisor chain cần xét trong hệ thống thật.

Đối với nhiều advices trong **cùng aspect class**, không nên trông cậy vào **thứ tự source code** như một hợp đồng ổn định. Khi order có ý nghĩa nghiệp vụ, hãy tổ chức rõ trách nhiệm và kiểm thử chain thực tế.

**Không đồng nhất `@Order` với thứ tự khởi tạo bean.** Nó dùng trong cơ chế ordering áp dụng, không bảo đảm constructor hay `@PostConstruct` theo thứ tự đó.

## 10. Bẫy thường gặp trong Spring

### 10.1. `@Aspect` chưa phải Spring bean

```java
@Aspect
public class BeforeAspect {
    @Before("execution(* demo.before..*Service.*(..))")
    public void log() {}
}
```

Nếu chỉ tạo class như trên mà không đăng ký nó là Spring bean, AOP auto-proxy creator không nhất thiết sử dụng aspect đó. Cần `@Component` trong phạm vi scan hoặc đăng ký qua `@Bean`. Đừng gọi `new BeforeAspect()` từ code riêng để hy vọng Spring tự biết.

### 10.2. Self-invocation và đối tượng ngoài Spring

```java
public void outer() {
    this.inner(); // Không đi qua AOP proxy.
}
```

Dù `inner()` có public và pointcut match, self-invocation bypass. Giải pháp dễ bảo trì là tách `inner()` thành method của **bean khác**, inject và gọi qua Spring reference, thay vì chỉ bật CGLIB.

### 10.3. Private, static, final và method không công bố qua interface

- Private method không thể được advice bằng JDK proxy hoặc CGLIB.
- Static method không phải instance proxy invocation.
- Với CGLIB, `final method` không thể được override để intercept; `final class` không thể subclass.
- Với JDK proxy, **public method thuộc interface** có thể được intercept dù implementation method là final, vì proxy chặn interface boundary.
- Public method chỉ có ở implementation (không thuộc interface) không được JDK proxy công bố cho consumer interface.

Không thể sửa các ranh giới này chỉ bằng cách viết `@Before("execution(...")` rộng hơn.

### 10.4. Pointcut không khớp target

Nếu dùng `execution(* demo.before.OtherService.submit(..))` nhưng bean thật là `SubmissionService`, Spring không chạy advice cho method mong muốn. Kiểm tra class/package cụ thể, loại proxy, method signature, visibility và điều kiện bổ sung.

### 10.5. Bẫy khi log input trong before advice

Trong production, dùng structured logging và **redaction** cho tham số nhạy cảm. `JoinPoint.getArgs()` có thể chứa đối tượng mutable, stream, file, token hoặc request data lớn. Không gọi `toString()` tùy tiện trên toàn bộ arguments và không dùng logging như cơ chế kiểm soát quyền truy cập.

### 10.6. Ngoại lệ của before advice không phải ngoại lệ của target

Nếu `@Before` ném exception, target chưa chạy. Khi debug cần phân biệt stack trace và log cho biết lỗi phát sinh **từ advice** hay **sau đó trong target**. Nếu lỗi target diễn ra, before advice vẫn đã hoàn thành trước nó. Đừng kết luận “advice không chạy” chỉ vì method cuối cùng ném exception.

### 10.7. `@Before` chạy một lần hay nhiều lần?

Nó chạy trên mỗi matched invocation **qua proxy**. Ví dụ một API endpoint bị gọi 10 lần khiến business method chạy qua proxy 10 lần thì before advice có thể chạy tương ứng 10 lần; không phải “khởi động Spring chạy trước một lần”. Tuy nhiên, nếu nội bộ target gọi cùng method bằng `this`, các lời gọi đó không được tính như lần intercept mới của Spring proxy.

### 10.8. Không mặc định `@Before` và `@Transactional` có order cố định

Spring declarative transaction thường cũng dùng proxy/interceptor. Nếu một before advice nằm **ngoài** transaction interceptor, nó có thể chạy trước khi transaction bắt đầu; nếu nằm **bên trong**, nó có thể chạy khi transaction đã mở. Thứ tự phụ thuộc advisor ordering, không thể tự suy ra chỉ từ việc cả hai annotation cùng tồn tại. Nếu aspect cần transaction đang active, phải thiết kế order rõ ràng và test.

## 11. Checklist chẩn đoán “@Before không chạy”

| Triệu chứng | Cách kiểm tra / giải pháp |
| --- | --- |
| Method service chạy nhưng không có log `@Before` | Kiểm tra target có phải Spring bean, aspect có được đăng ký, `@EnableAspectJAutoProxy` hoặc Boot AOP auto-config |
| `@Aspect` tồn tại nhưng không có advice | Đăng ký aspect qua `@Component`/`@Bean`, bảo đảm component scan đúng package |
| Có AOP proxy nhưng `@Before` không chạy cho method A | Pointcut không match A, hoặc lời gọi A bypass proxy |
| `this.method()` không có advice | Self-invocation, cần gọi qua bean khác được inject |
| `new SomeService().method()` không có advice | Object ngoài container, không có Spring proxy |
| JDK proxy không expose method cụ thể | Inject qua interface chứa method hoặc cân nhắc CGLIB phù hợp |
| CGLIB method `final`/`private` không advice | Không override được; đổi ranh giới method/bean |
| Method bị chặn mà không thấy log target | Before advice có thể ném exception trước target |
| Before log xuất hiện nhưng target ném exception | Đây là hành vi bình thường: before chạy trước target |
| Cần return value trong before advice | Chuyển sang `@AfterReturning` hoặc `@Around` |
| Muốn advice điều chỉnh argument hoặc bỏ qua target | Cân nhắc `@Around` và `ProceedingJoinPoint` |
| Nhiều advice chạy không theo thứ tự mã nguồn | Dùng `@Order`/`Ordered` giữa aspects và integration test |
| Chỉ thấy advice trên direct call mà không có trên lời gọi từ batch | Lời gọi nội bộ trong target dùng `this`, bypass proxy |

**Quy trình điều tra hữu ích:**

1. Xác định bean đích có được tạo/tra cứu bởi Spring chứ không phải `new`.
2. Xác nhận aspect cũng là Spring bean và `@Before` reference pointcut đúng.
3. Kiểm tra auto-proxy creator/enable AOP và `AopUtils.isAopProxy(bean)` trên **reference consumer thực nhận**.
4. Nếu dùng JDK, kiểm tra method nằm trên interface; nếu CGLIB, kiểm tra method có thể override.
5. Kiểm tra caller gọi **qua proxy** hay từ `this` trong target.
6. Đặt điểm quan sát trong advice và method target; thử case bình thường, case advice ném exception và case target ném exception.
7. Nếu nhiều advices/interceptors, kiểm tra ordering thực tế trước khi kết luận sai thứ tự.
8. Giữ logic trong before advice nhỏ, dễ kiểm soát side effects và log an toàn.

## 12. Câu hỏi tự đánh giá

**1. `@Before` chạy trước khi argument của Java call được tạo ra không?** Không. Nó chạy sau khi invocation tới proxy với arguments đã được Java đánh giá, nhưng trước khi method đích bắt đầu thực thi.

**2. Advice `@Before` có thể lấy return value của target để log không?** Không, target chưa chạy. Dùng `@AfterReturning` hoặc `@Around` khi yêu cầu return value.

**3. `@Before` có thể ngăn method chạy không?** Nó không có `proceed()` để chọn bỏ qua execution, nhưng nếu advice **ném exception** thì target không được gọi theo đường execution bình thường.

**4. Vì sao có `@Before` rồi mà `this.submit()` không bị intercept?** Spring AOP chỉ nhận lời gọi qua proxy; `this.submit()` là lời gọi trên target.

**5. `@Aspect` có đủ để Spring tự đăng ký aspect bean không?** Không. Phải đăng ký bằng component scan qua `@Component` hoặc bean definition/`@Bean` phù hợp.

**6. Khi có ba lần gọi `submit()` qua proxy, before advice chạy mấy lần?** Nếu cả ba match và không bị interceptor trước đó bỏ qua, before advice thường chạy ba lần (mỗi invocation một lần); không phải một lần khi khởi động.

**7. `JoinPoint.getThis()` và `getTarget()` có luôn giống nhau không?** Không. Với Spring AOP proxy, `getThis()` biểu diễn proxy, `getTarget()` biểu diễn target; có thể là hai Java objects khác nhau.

**8. Có thể dùng `@Order` để bảo đảm toàn bộ constructor, postconstruct, transaction và before advice theo cùng một thứ tự không?** Không. Ordering advice/advisors khác với lifecycle bean initialization; cần hiểu từng cơ chế và test.

## Tham khảo tài liệu Spring chính thức

- [Spring Framework — Declaring Advice / Before Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework — AOP Concepts, Before Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-defn.html)
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html)
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework — Ordering Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html#advice-ordering)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-002">@AfterReturning và ràng buộc kết quả trả về</a>

<details>
<summary>Xem chi tiết</summary>

# @AfterReturning và ràng buộc kết quả trả về trong Spring AOP

## 1. Mục tiêu và phạm vi

Trong **Spring AOP với cú pháp @AspectJ**, `@AfterReturning` là advice thực thi **sau khi một method execution được pointcut chọn kết thúc bình thường** và quyền điều khiển chuẩn bị trở về caller. Advice có thể chỉ ghi nhận sự kiện, hoặc nhận chính giá trị trả về bằng thuộc tính `returning`.

Ví dụ thực tế: ghi nhận một kết quả tra cứu thành công, cập nhật metric của service call hoàn tất, hoặc quan sát dữ liệu trả về khi một Spring service được gọi **qua AOP proxy**.

Điểm quan trọng của bài không chỉ là thời điểm advice chạy mà còn là:

1. **Khi nào được xem là “return normally”?** Method trả về bình thường (kể cả `null` và method `void` nếu biểu thức/điều kiện advice phù hợp), không phải method thoát ra bằng exception.
2. **Cách viết `@AfterReturning(pointcut = "...", returning = "result")`:** tên `result` phải tương ứng với tham số advice; kiểu tham số trở thành điều kiện giới hạn matching.
3. **`@AfterReturning` có đổi được kết quả về caller không?** Nó không thay thế reference/value trả về. Muốn chủ động đổi kết quả phải thiết kế `@Around` hoặc logic nghiệp vụ khác.
4. **Điều kiện của Spring:** aspect và target phải được container quản lý, có hạ tầng auto-proxy, pointcut match và caller phải đi qua proxy. Tên annotation đúng mà gọi `this.method()` thì vẫn có thể bỏ qua advice.

Bài tập trung vào **Spring Aspect/Spring AOP**: Java configuration, Spring bean, proxy boundary, cách bind return value và những lỗi thường gặp. Không ôn lại AOP tổng quát hoặc chuyển sang AspectJ bytecode weaving.

## 2. Thời điểm @AfterReturning chạy

Một đường gọi đơn giản khi Spring AOP áp advisor:

```text
Consumer / Controller / Service khác
         |
         | service.lookup("A")
         v
Spring AOP proxy
         |
         +-- Pointcut match method?
         |       |
         |       +-- Có: invocation đi qua interceptor chain
         |
         v
Target bean: ResultService.lookup("A")
         |
         +-- Nếu RETURN bình thường
         |        |
         |        +-- @AfterReturning advice được gọi nếu điều kiện return binding hợp lệ
         |        |
         |        +-- Giá trị ban đầu tiếp tục trở về caller
         |
         +-- Nếu THROW exception
                  |
                  +-- @AfterReturning KHÔNG chạy cho lần thoát bằng lỗi ấy
                  |
                  +-- Exception tiếp tục qua proxy/interceptor chain

Lưu ý: Các advice khác trong chain có thể thay đổi thứ tự và outcome.
```

Đây là sơ đồ **khái quát** cho invocation phù hợp; với nhiều advisors, `@Around`, transaction hoặc asynchronous execution, thời điểm caller quan sát kết quả còn phụ thuộc interceptor chain. Spring docs định nghĩa `@AfterReturning` bằng **normal return của join point**, chứ không khẳng định giao dịch dữ liệu đã commit hoặc một tác vụ bất đồng bộ đã kết thúc.

### 2.1. So sánh ngắn các loại advice Spring liên quan

| Annotation | Khi target method execution đã đi qua proxy | Có nhận return value trực tiếp? |
| --- | --- | --- |
| `@Before` | Trước lời gọi target khi pointcut match | Không, vì target chưa trả về. |
| `@AfterReturning` | Sau khi method hoàn tất bình thường | Có, bằng `returning`. |
| `@AfterThrowing` | Khi method ném exception ra ngoài join point | Có thể bind exception bằng `throwing`, không phải normal return value. |
| `@After` | Theo nghĩa after-finally: khi join point thoát bình thường **hoặc** bất thường | Không dùng `returning` để bind kết quả. |
| `@Around` | Bao quanh invocation; có thể dùng `ProceedingJoinPoint.proceed()` | Có thể quan sát và quyết định giá trị trả về caller khi thiết kế đúng. |

**Hai nhầm lẫn cần tránh:**

- `@AfterReturning` **không** có nghĩa “luôn chạy ở cuối method”, vì method ném exception thì advice này không chạy.
- `@After` **không** phải tên khác của `@AfterReturning`: `@After` có semantics tương tự `finally`.

## 3. Cấu hình tối thiểu trong Spring Framework

Annotation đến từ AspectJ annotations API:

```java
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.AfterReturning;
```

Class aspect phải được Spring đăng ký. Ví dụ **phần cấu hình minh họa**, chưa phải project chạy độc lập:

```java
@Aspect
@Component
public class ResultAuditAspect {
    @AfterReturning(
        "execution(public * com.example.orders..*Service.*(..))"
    )
    public void afterNormalReturn() {
        System.out.println("service completed normally");
    }
}
```

```java
@Configuration
@ComponentScan("com.example.orders")
@EnableAspectJAutoProxy
public class AopConfiguration {
}
```

Điều kiện để advice thật sự chạy:

- `@Aspect` khai báo class chứa advice; `@Component` (nằm trong package scan) **hoặc** một `@Bean` factory method đăng ký class đó làm Spring bean.
- `@EnableAspectJAutoProxy` trong Spring Framework Java configuration kích hoạt auto-proxy cho các `@Aspect` bean; thường cần `aspectjweaver` tương thích trên classpath.
- Một method **thuộc Spring-managed target bean** và match `execution(...)` phải được gọi **qua Spring proxy**.
- Với JDK dynamic proxy, caller gọi method được expose trên interface; với class-based proxy, class/method phải proxy được (`final` có giới hạn).
- Code gọi `new Service()` hoặc `this.method()` bên trong target không được Spring AOP intercept theo cách thông thường.

Spring Boot có auto-configuration AOP khi ứng dụng mang dependency phù hợp; điều đó không thay yêu cầu `@Aspect` là Spring bean. Không cần tự bật weaving agent chỉ để dùng `@AfterReturning` theo Spring AOP proxy.

## 4. Cú pháp 1 — không bind kết quả

Dạng gọn nhất:

```java
@AfterReturning(
    "execution(public * com.example.orders..*Service.*(..))"
)
public void afterSuccessfulServiceMethod() {
    // Ghi nhận một lời gọi service kết thúc bình thường.
}
```

Ở đây:

- `@AfterReturning` dùng một biểu thức pointcut theo thuộc tính `value` của annotation.
- Method advice `afterSuccessfulServiceMethod()` **không nhận result**, nên không thể dựa vào chữ ký tham số để ràng buộc kiểu giá trị trả về.
- Method có return type `String`, `int`, object hoặc `void` có thể được chọn nếu **pointcut match method execution** và execution thoát bình thường.
- Một method có lời gọi target thất bại bằng exception sẽ không chạy lời advice này.
- Advice có return type `void` là lựa chọn tự nhiên vì bản thân nó không biến đổi kết quả của target.

Ví dụ `@AfterReturning` không bind phù hợp với metric `successfulMethodReturns++`. Tuy nhiên “method trả bình thường” **không đồng nghĩa** “business transaction thành công”, “HTTP response 2xx” hoặc “database commit chắc chắn thành công”.

## 5. Cú pháp 2 — ràng buộc return value bằng thuộc tính returning

Muốn nhận giá trị mà method trả về:

```java
@AfterReturning(
    pointcut = "execution(public * com.example.orders..*Service.*(..))",
    returning = "result"
)
public void afterReturningValue(Object result) {
    System.out.println("result=" + result);
}
```

**Tên `result` trong `returning = "result"` phải khớp với tên tham số advice** `Object result`. Spring/AspectJ binding đưa giá trị đã trả về vào tham số này. Bài học chính là: `returning` **không phải tên field**, không phải tên biến trong method đích và không phải chuỗi bất kỳ.

Khi muốn chắc việc ánh xạ tham số hoạt động với cấu hình biên dịch/metadata khác nhau, có thể khai báo `argNames`:

```java
@AfterReturning(
    pointcut = "execution(public * com.example.orders..*Service.*(..))",
    returning = "result",
    argNames = "result"
)
public void afterReturningValue(Object result) {
    // Đọc giá trị result nhận từ method return bình thường.
}
```

`argNames` thuộc annotation `@AfterReturning`, được Spring dùng khi xác định tên tham số binding. Nó đặc biệt hữu ích khi xây code Java không giữ parameter names trong bytecode hoặc advice bind nhiều biến. Trong các ví dụ đơn giản Spring có thể suy luận tên, nhưng đừng phụ thuộc vào một cấu hình compiler ngầm định trong bài học/bộ test chuẩn.

### 5.1. Trường hợp returning không trùng tên

```java
// Sai ý nghĩa binding: returning="value" nhưng advice chỉ khai báo "data".
@AfterReturning(
    pointcut = "execution(* com.example.orders..*Service.*(..))",
    returning = "value",
    argNames = "data"
)
public void wrongBinding(Object data) {
}
```

Đây là **đoạn minh họa lỗi**, không chép vào một Spring context cần chạy thành công. Trong cấu hình thực tế, advisor có thể không được khởi tạo vì pointcut/binding không hợp lệ. **Cách sửa** là thống nhất tên: `returning="data"`, `argNames="data"` và tham số `Object data`, hoặc đổi toàn bộ sang `value`.

### 5.2. returning đồng thời là bộ lọc kiểu

**Điểm rất quan trọng:** kiểu khai báo của tham số advice không chỉ phục vụ đọc giá trị; nó còn **giới hạn tập method executions phù hợp với advice**.

```java
@AfterReturning(
    pointcut = "execution(public * com.example.orders..*Service.*(..))",
    returning = "text",
    argNames = "text"
)
public void afterText(String text) {
    // Chỉ những lần return có kiểu tương thích với String mới được binding.
}
```

Nếu cùng biểu thức `execution(public * ...)` bao phủ các method trả `String` và `Integer`:

- Advice với tham số `String text` được dùng cho kết quả tương thích `String`, không được coi là “mọi method trong package”.
- Advice với tham số `Object result` rộng hơn: có thể nhận nhiều kết quả reference và những primitive value được boxing khi phù hợp.
- `void` method **không có giá trị result để bind theo `String`**; nếu muốn đếm method `void` hoàn tất bình thường, dùng advice **không bind return**.

Spring docs mô tả rõ: thêm `returning` làm advice chỉ match những method executions trả giá trị của kiểu đã nêu trong tham số advice. Điều này dễ gây hiểu nhầm khi cùng pointcut có hai advice mà một advice đếm ít lần hơn advice còn lại.

### 5.3. Kết quả là null thì sao?

`null` **không phải exception**: một method trả `null` vẫn hoàn tất bình thường. Nếu dùng advice **không bind result**, nó vẫn có thể chạy theo pointcut. Nếu dùng returning-bound advice với kiểu reference, code nên **xử lý null**:

```java
@AfterReturning(
    pointcut = "execution(public java.lang.String com.example.orders..*Service.*(..))",
    returning = "result",
    argNames = "result"
)
public void afterString(String result) {
    if (result == null) {
        System.out.println("returned:null");
        return;
    }
    System.out.println("returned:length=" + result.length());
}
```

Đây là đoạn minh họa, không phải project độc lập. Với typed binding, việc advisor được chọn còn phụ thuộc vào kiểu trả về được khai báo/được match theo Spring AOP. **Không** dùng `result.length()` trực tiếp mà thiếu null-check chỉ vì phương thức khai báo trả `String`. Nếu method có generic bridge/covariant return hoặc `Object` return type nhưng giá trị thực thay đổi, cần integration test trên đúng Spring version thay vì tự suy luận từ signature.

## 6. Demo chạy được: không bind và bind String cùng một pointcut

Demo Java 17+ dưới đây cho thấy:

1. `@AfterReturning` **không bind** đếm các normal returns của nhiều method types.
2. `@AfterReturning(returning="result")` với tham số `String result` chỉ đếm kiểu kết quả phù hợp.
3. Một method `void` hoàn tất bình thường được tính bởi advice không bind.
4. Method ném exception **không được tính** bởi `@AfterReturning`.
5. `@AfterReturning` không thay đổi giá trị mà caller nhìn thấy.

Môi trường: **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**, **Java 17+**, Gradle. Dùng Spring Framework thuần để minh bạch role của `@EnableAspectJAutoProxy`.

### 6.1. Cấu trúc dự án

```text
spring-after-returning-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/returning/
    ├── Main.java
    ├── AppConfig.java
    ├── ResultService.java
    └── ReturnAspect.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-after-returning-demo'
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
    mainClass = 'com.example.returning.Main'
}
```

Spring Framework 6.x yêu cầu Java 17 hoặc mới hơn. `spring-context` mang hạ tầng tạo container; `spring-aop` và `aspectjweaver` hỗ trợ Spring @AspectJ proxy-based configuration. Khi chuyển sang Spring Boot, dùng dependency management của đúng Boot major version.

### 6.2. Target Spring service với nhiều kiểu outcome

**`ResultService.java`**

```java
package com.example.returning;

public class ResultService {
    public String lookup(String id) {
        return "found:" + id;
    }

    public Integer available() {
        return 7;
    }

    public void warmUp() {
        System.out.println("target:warmUp");
    }

    public String fail() {
        throw new IllegalStateException("missing");
    }
}
```

Bốn method đều public/non-final và class không final. Vì vậy, tình huống này **không bị lẫn với lỗi CGLIB final/private**. Method `fail()` khai báo trả `String` nhưng thực tế ném exception; normal-return advice không chạy cho invocation đó.

### 6.3. Một aspect, hai @AfterReturning advices

**`ReturnAspect.java`**

```java
package com.example.returning;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class ReturnAspect {
    private final AtomicInteger normalReturns = new AtomicInteger();
    private final AtomicInteger stringReturns = new AtomicInteger();

    @AfterReturning(
        "execution(public * com.example.returning.ResultService.*(..))"
    )
    public void afterAnyNormalReturn() {
        normalReturns.incrementAndGet();
    }

    @AfterReturning(
        pointcut = "execution(public * com.example.returning.ResultService.*(..))",
        returning = "result",
        argNames = "result"
    )
    public void afterStringReturn(String result) {
        stringReturns.incrementAndGet();
    }

    public int normalCount() {
        return normalReturns.get();
    }

    public int stringCount() {
        return stringReturns.get();
    }
}
```

Các đếm `AtomicInteger` dùng cho demo; không phải hệ thống metrics production. Advice không in log nên output **không phụ thuộc thứ tự chạy giữa hai advices**. Spring không hứa thứ tự cố định giữa nhiều advice cùng loại trong cùng aspect nếu không có thứ tự được khai báo/áp dụng thích hợp.

**Logic các counters:**

- `lookup("A")`: trả `String` bình thường → `normalReturns` và `stringReturns` cùng tăng.
- `available()`: trả `Integer` bình thường → `normalReturns` tăng; `stringReturns` không tăng vì binding kiểu `String` không tương thích.
- `warmUp()`: `void` hoàn tất bình thường → advice **không bind** tăng `normalReturns`; advice bind `String` không tăng.
- `fail()`: ném `IllegalStateException` → không có normal return; cả hai `@AfterReturning` advices không tăng.

### 6.4. Cấu hình Spring AOP

**`AppConfig.java`**

```java
package com.example.returning;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    ResultService resultService() {
        return new ResultService();
    }

    @Bean
    ReturnAspect returnAspect() {
        return new ReturnAspect();
    }
}
```

`ResultService` là Spring-managed target qua `@Bean`; `ReturnAspect` là Spring-managed `@Aspect` bean, cũng qua `@Bean`. `@EnableAspectJAutoProxy(proxyTargetClass=true)` bật advisor scanning và class proxy. Không cần `@ComponentScan` vì đã khai báo đủ bean cần thiết.

### 6.5. Bootstrap và in số lần advice thực thi

**`Main.java`**

```java
package com.example.returning;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            ResultService service = context.getBean(ResultService.class);
            ReturnAspect aspect = context.getBean(ReturnAspect.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println("lookup=" + service.lookup("A"));
            System.out.println("available=" + service.available());
            service.warmUp();

            try {
                service.fail();
            } catch (IllegalStateException ex) {
                System.out.println("caught=" + ex.getMessage());
            }

            System.out.println("normalReturns=" + aspect.normalCount());
            System.out.println("stringReturns=" + aspect.stringCount());
        }
    }
}
```

Chạy ở thư mục project sau khi cài JDK 17+ và Gradle:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper: `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows.

**Các dòng `System.out` của chương trình theo thứ tự** (không tính logging riêng của JVM/Spring/Gradle):

```text
proxy=true
lookup=found:A
available=7
target:warmUp
caught=missing
normalReturns=3
stringReturns=1
```

### 6.6. Tại sao hai counters khác nhau?

**Cùng pointcut** `execution(public * com.example.returning.ResultService.*(..))` được dùng cho cả hai advice. Nhưng:

| Lần gọi target | Outcome | Advice không bind | Advice bind `String` |
| --- | --- | --- | --- |
| `lookup("A")` | `String`, normal return | Có | Có |
| `available()` | `Integer`, normal return | Có | Không |
| `warmUp()` | `void`, normal return | Có | Không |
| `fail()` | Exception thoát khỏi method | Không | Không |

Sau các lời gọi, `normalReturns=3` và `stringReturns=1`. **Không phải advisor của `String` lỗi**; chính kiểu tham số trong returning-bound advice thu hẹp matching.

Các giá trị returned từ target được giữ nguyên: `lookup=found:A`, `available=7`. `@AfterReturning` ở ví dụ chỉ quan sát và tăng counters, không sửa return value.

Nếu không thấy dòng tương ứng trong ứng dụng thực, kiểm tra trước Spring proxy và bean registration, sau đó mới xem xét `returning` name/type.

## 7. Kết hợp return binding và method argument binding

Với Spring @AspectJ, có thể cần biết **tham số vào** và **kết quả ra** của một lời gọi service. Sử dụng `args(...)` bên trong pointcut và `returning` để bind cả hai.

Ví dụ **minh họa độc lập**, không thêm vào project demo mục 6 nếu muốn giữ nguyên counters/stdout:

```java
@Aspect
public class LookupAuditAspect {
    @AfterReturning(
        pointcut =
            "execution(public java.lang.String com.example.returning.ResultService.lookup(..))"
            + " && args(id)",
        returning = "result",
        argNames = "id,result"
    )
    public void afterLookup(String id, String result) {
        System.out.println("lookup:" + id + "=" + result);
    }
}
```

Ở đây:

- `args(id)` bind tham số đầu vào của method được chọn vào `String id`.
- `returning="result"` bind kết quả trả về vào `String result`.
- `argNames="id,result"` làm thứ tự và tên các binding rõ ràng; nếu thêm tham số `JoinPoint`, cần xem quy tắc tên tham số theo tài liệu Spring.
- Kiểu tham số `String id` còn giúp ràng buộc argument type; method với chữ ký không tương thích sẽ không match.
- `ResultService.lookup("A")` của demo trả `"found:A"`, vì vậy nếu aspect này **được đăng ký thay thế/thêm độc lập trong context phù hợp**, nó in:

```text
lookup:A=found:A
```

Đoạn này chưa là file Java chạy độc lập: cần package, imports, `@Bean`/`@Component` đăng ký aspect, và target/result service trong context.

**Không lưu raw password/token/thông tin nhạy cảm** vào log chỉ vì `args` và `returning` cho phép truy cập. Cần policy che giấu dữ liệu và tránh logging quá nhiều trong hot path.

### 7.1. Khi dùng JoinPoint

Advice có thể khai báo `JoinPoint` để đọc chữ ký method cùng result:

```java
@AfterReturning(
    pointcut = "execution(public java.lang.String com.example.returning.ResultService.lookup(..))",
    returning = "result",
    argNames = "result"
)
public void afterLookup(JoinPoint joinPoint, String result) {
    System.out.println(joinPoint.getSignature().getName() + "=" + result);
}
```

Đây là **đoạn minh họa**, cần import `org.aspectj.lang.JoinPoint` và đặt trong class `@Aspect` được Spring quản lý. Tham số `JoinPoint` đứng đầu có cách bind đặc biệt trong `@AspectJ`. Chỉ sử dụng nó khi cần metadata; nếu chỉ quan sát kiểu result, method advice đơn giản hơn sẽ dễ test.

## 8. @AfterReturning có thay đổi return value của target không?

**Không thể dùng return value của chính advice để thay thế reference/value mà target method đã trả.**

Ví dụ **sai kỳ vọng**:

```java
@AfterReturning(
    pointcut = "execution(public java.lang.String com.example.returning.ResultService.lookup(..))",
    returning = "result",
    argNames = "result"
)
public String tryReplace(String result) {
    return "other:" + result;
}
```

Đây là đoạn **minh họa anti-pattern**, không đưa vào context demo như một cách sửa kết quả. Một advice `@AfterReturning` thông thường cần `void`; dù bạn viết logic tính một chuỗi khác trong body, nó **không biến chuỗi đó thành giá trị mà caller nhận được**. Spring docs nói rõ `@AfterReturning` không thể trả một reference hoàn toàn khác để thay đổi kết quả đã được target chọn.

Ngay cả khi đổi local parameter:

```java
@AfterReturning(
    pointcut = "execution(public java.lang.String com.example.returning.ResultService.lookup(..))",
    returning = "result",
    argNames = "result"
)
public void assignLocally(String result) {
    result = "replacement"; // Chỉ đổi biến local của advice!
}
```

Caller vẫn nhận chuỗi đã được target trả về. Java assignment tới biến local `result` không thay reference mà interceptor chain đang giữ.

### 8.1. Với object mutable: phân biệt quan sát và mutate

Nếu target trả một object mutable và `@AfterReturning` bind object ấy:

```java
@AfterReturning(
    pointcut = "execution(* com.example.orders..*Service.load(..))",
    returning = "result",
    argNames = "result"
)
public void observe(AccountDto result) {
    // Có thể đọc fields của result, nhưng không nên âm thầm sửa DTO.
}
```

Đoạn này **chỉ là minh họa**, giả sử `AccountDto` có thật. Nếu code cố mutate `result` bằng setters, vì nhiều references có thể cùng trỏ tới object, caller **có thể thấy nội dung object bị thay đổi**. Điều đó khác với thay thế reference trả về; nó là side effect trên shared object và có thể gây lỗi khó kiểm soát.

**Khuyến nghị Spring:** dùng `@AfterReturning` cho quan sát/audit/metric sau normal return; nếu nghiệp vụ cần transform result có chủ đích, sử dụng `@Around` một cách tường minh hoặc tốt hơn để business method trả đúng loại dữ liệu mong muốn.

### 8.2. Dùng @Around để quyết định return value

Đoạn sau chỉ minh họa cấu trúc, không ghép vào demo nếu muốn giữ output:

```java
@Around(
    "execution(public java.lang.String com.example.returning.ResultService.lookup(..))"
)
public Object decorateReturn(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    return "wrapped:" + result;
}
```

`ProceedingJoinPoint` đến từ `org.aspectj.lang.ProceedingJoinPoint`. Với method phù hợp, `@Around` có quyền trả một giá trị khác về caller, miễn tuân theo return type/contract thực tế. Nếu method có thể trả `null` hoặc ném exception, code production phải xử lý hai trường hợp đúng semantics.

**Đừng chuyển sang `@Around` chỉ vì muốn đếm normal returns:** `@AfterReturning` đơn giản và chính xác hơn cho observation.

## 9. @AfterReturning với void, null và kiểu đặc biệt

### 9.1. Method void

```java
public void refreshCache() {
    // kết thúc bình thường không có giá trị trả về
}
```

`@AfterReturning("execution(* ...refreshCache(..))")` **không bind return value** có thể chạy khi method hoàn thành bình thường. Nhưng `@AfterReturning(returning="result")` với tham số `String result` không có giá trị `String` để bind cho `void` invocation.

Nếu cần audit “đã chạy xong” của một method `void`, hãy dùng dạng **không bind** như `afterAnyNormalReturn()` trong demo.

### 9.2. Null từ method reference

```java
public String findOptionalText() {
    return null;
}
```

Đây là normal return, không phải exception. Advice **không bind** có thể chạy. Khi bind result vào kiểu reference, code cần null-safe. Nếu return type generic/erasure khác biệt hoặc binding có constraints bổ sung, kiểm tra trực tiếp trên phiên bản Spring đang dùng. Không tự kết luận “return null nghĩa là `@AfterReturning` không chạy”.

### 9.3. Primitive / wrapper

`int`, `long`, `boolean` và các wrappers có thể được biểu diễn theo boxed `Object` ở tầng reflection/interceptor thích hợp; nhưng advice `String result` **không** tương thích với kết quả kiểu `Integer`. Khi cần đo nhiều loại kết quả, có thể chọn `Object result` với điều kiện matching được xác nhận, hoặc chia pointcut/advice theo return type rõ ràng.

Không cast `Object` sang `String` nếu cùng pointcut bao phủ `Integer`, DTO và `null`. Nếu phải phân nhánh runtime, dùng `instanceof` và null-check hợp lý.

### 9.4. Optional

Nếu target trả `Optional<User>`, thứ mà advice nhận là object `Optional`, **không tự unwrap** thành `User`. Một `Optional.empty()` vẫn là normal return; nó không đồng nghĩa exception hay business success. Nếu quan tâm “có dữ liệu”, hãy inspect `Optional.isPresent()` trong code quan sát hoặc thiết kế result policy thích hợp.

### 9.5. CompletableFuture, CompletionStage, Mono và Flux

Nếu method trả `CompletableFuture<T>`, `CompletionStage<T>`, `Mono<T>` hoặc `Flux<T>`, `@AfterReturning` của Spring proxy thường được kích hoạt khi **method trả về wrapper/handle bình thường**, **không phải** khi computation hoặc publisher bất đồng bộ hoàn tất. Advice bind return value sẽ thấy **future hoặc publisher**, không mặc nhiên thấy dữ liệu cuối cùng `T`.

- Với `CompletableFuture`: muốn quan sát completion thành công/thất bại, dùng API như `whenComplete`/`handle` có chủ đích và cần thiết kế threading/context đúng.
- Với Reactor `Mono`/`Flux`: việc tạo publisher khác việc subscribe/completion; quan sát reactive signals cần Reactor operators/instrumentation phù hợp.
- Không gắn tên “businessSuccess” cho mỗi invocation của `@AfterReturning` khi kết quả là async handle.

## 10. @AfterReturning và exception: những trường hợp thực tế

### 10.1. Target ném exception

```java
public String load(String id) {
    throw new IllegalArgumentException("not found");
}
```

Nếu invocation `load` đi qua proxy nhưng ném exception ra khỏi join point, `@AfterReturning` không chạy. Muốn theo dõi exception, dùng `@AfterThrowing`:

```java
@AfterThrowing(
    pointcut = "execution(public * com.example.orders..*Service.*(..))",
    throwing = "failure",
    argNames = "failure"
)
public void afterFailure(Throwable failure) {
    // Log/metric lỗi; không tự đổi failure thành normal result.
}
```

Đây là đoạn minh họa, cần import `AfterThrowing` và class aspect Spring bean. `throwing` tương tự `returning` ở chỗ tên phải bind đúng vào tham số advice; kiểu tham số có thể giới hạn loại exception được chọn.

### 10.2. Method bắt exception rồi return fallback

```java
public String findOrFallback() {
    try {
        return repositoryLookup();
    } catch (RuntimeException ex) {
        return "fallback";
    }
}
```

Đây là đoạn minh họa, `repositoryLookup()` giả định tồn tại. Nếu method **bắt lỗi nội bộ và thực sự return `"fallback"`**, `@AfterReturning` thấy **normal return**. Nó không có nghĩa thao tác gốc chưa từng có lỗi, chỉ rằng lỗi không đi ra khỏi **join point đang được advice**.

Để phân biệt “fallback do lỗi” và “thành công đầy đủ”, có thể thiết kế return type chứa trạng thái domain (`Result<T>`, error code) hoặc dùng metric ở tầng xử lý lỗi. Không biến Spring AOP normal-return thành phép đo business success tuyệt đối.

### 10.3. Advice @AfterReturning tự ném exception

```java
@AfterReturning(
    "execution(public * com.example.orders..*Service.lookup(..))"
)
public void audit() {
    throw new IllegalStateException("audit unavailable");
}
```

Đây là **đoạn minh họa rủi ro**, không thêm vào project demo. Target có thể vừa hoàn thành bình thường nhưng **advice ném exception**, khiến caller **nhận lỗi** qua interceptor chain thay vì kết quả. Spring AOP không tự “nuốt” exception của advice chỉ vì nó chạy sau target.

Do đó, với audit/metrics không critical, hãy xác định chính sách lỗi: log/metric an toàn, không bất ngờ phá transaction hay API contract nếu đó không phải yêu cầu. Nếu audit bắt buộc phải thành công mới trả kết quả, xem xét nghiệp vụ/transaction semantics rõ ràng thay vì làm side effect tùy tiện trong advice.

### 10.4. Transaction commit không đồng nghĩa @AfterReturning

Nếu method có `@Transactional`, order của transaction interceptor và custom aspect có thể làm thời điểm `@AfterReturning` tương đối với commit khác nhau tùy advisor order/propagation. “Target method normal return” **không đủ bằng chứng** DB transaction đã commit. Nếu cần trigger hành động **sau khi commit thành công**, hãy sử dụng cơ chế transaction synchronization/`@TransactionalEventListener(phase = AFTER_COMMIT)` theo cấu hình transaction thực tế.

Nhắc lại: `@Transactional` cần transaction management/transaction manager riêng; chỉ `@EnableAspectJAutoProxy` không tự bật transaction behavior.

## 11. Khi method có nhiều advisors

Một target bean có thể có:

- Custom `@Around` biến đổi return value hoặc bắt exception;
- Spring transaction/cache/security advisor;
- `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After` của cùng hoặc các aspects khác;
- Interceptors từ thư viện quan sát/monitoring.

Vì vậy, khi debug phải hỏi **@AfterReturning đang quan sát normal return của join point ở vị trí nào trong chain?**, và **có interceptor bên ngoài đổi kết quả/lỗi không?**

Theo tài liệu Spring, thứ tự execution của các advice trong một aspect được điều phối dựa trên loại advice; `@After` có semantics after-finally; các advice **cùng một loại** trong cùng aspect không có thứ tự cố định chỉ do chúng xuất hiện ở dòng trước/sau trong source file.

Nếu ứng dụng cần thứ tự giữa **các aspects**, có thể dùng `@Order` hoặc `Ordered` và integration test trên service method thật. Không thiết kế để **hai `@AfterReturning` cùng loại** chạy theo một thứ tự nhất định chỉ vì tên method hay vị trí source.

Trong demo, hai advices dùng counters độc lập và không phụ thuộc vào ordering; đó là lý do stdout tất định.

## 12. Spring Boot và khác biệt giữa khai báo aspect với bật AOP

Với Spring Boot, khi có AOP starter/auto-configuration phù hợp, thường không cần tự viết `@EnableAspectJAutoProxy`. Nhưng phải bảo đảm:

1. `@Aspect` class là Spring bean, thường `@Aspect` + `@Component` nằm trong package được scan, hoặc được trả từ một `@Bean`.
2. Service target cũng nằm trong Spring container.
3. Pointcut expression thực sự match một method có thể intercept.
4. Caller đi qua Spring proxy; không tự gọi `this.method()`.
5. `returning` name và type phù hợp với method outcomes đang kiểm tra.

Dependency Gradle chọn theo **Boot major** và dependency management của Boot, không dùng đồng thời:

```gradle
// Spring Boot 3.x
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

```gradle
// Spring Boot 4.x
implementation 'org.springframework.boot:spring-boot-starter-aspectj'
```

Trong Boot, class-based proxy thường được chọn theo `spring.aop.proxy-target-class=true`. Nếu dùng JDK interface proxy (`spring.aop.proxy-target-class=false`), method cần được expose trên interface. Nếu target có method `final` và CGLIB, Spring không intercept method đó. Dù chọn proxy loại nào, self-invocation vẫn không quay lại proxy theo cơ chế Spring AOP thông thường.

## 13. Phương pháp kiểm thử chính xác

### 13.1. Kiểm tra bean có Spring proxy chưa

```java
ResultService bean = context.getBean(ResultService.class);
System.out.println(AopUtils.isAopProxy(bean));
```

Đoạn minh họa dùng `context` và import `AopUtils` như `Main.java`. Kết quả true giúp xác nhận Spring trả proxy, **không chứng minh riêng `@AfterReturning` đã match**. Cần thêm recorder/counter.

### 13.2. Tách kiểm thử pointcut và type binding

Một integration test cho demo cần assert:

- Gọi `lookup("A")` qua proxy làm **normal** và **string** counters tăng.
- Gọi `available()` làm **normal** tăng nhưng **string** không tăng.
- Gọi `warmUp()` làm **normal** tăng.
- Gọi `fail()` ném exception nhưng không làm hai counters tăng.
- Kết quả return mà caller nhận đúng giá trị target trả; `@AfterReturning` không thay.
- Một lần gọi qua `new ResultService()` ở ngoài Spring không làm counters của Spring aspect thay đổi.

Để tránh phụ thuộc log, có thể viết test bằng assertion trực tiếp:

```java
try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
    ResultService service = context.getBean(ResultService.class);
    ReturnAspect aspect = context.getBean(ReturnAspect.class);

    service.lookup("X");
    service.available();
    service.warmUp();

    try {
        service.fail();
    } catch (IllegalStateException expected) {
        // Target ném lỗi, @AfterReturning không được tính cho lần gọi này.
    }

    if (aspect.normalCount() != 3 || aspect.stringCount() != 1) {
        throw new AssertionError("Wrong @AfterReturning counters");
    }
}
```

Đây là **đoạn test minh họa** có thể dùng trong package của demo với import `AnnotationConfigApplicationContext`; nó tạo một context mới, không thay `Main` và không nhằm tạo stdout ở mục 6.5. Trong test production, thay try/catch thủ công bằng JUnit `assertThrows` và test assertion framework được quản lý dependency.

### 13.3. Vì sao không nên test chỉ bằng mock hoặc new?

`new ReturnAspect()` + gọi trực tiếp `afterStringReturn("x")` chỉ test Java method của aspect, không chứng minh Spring đã đọc `@AfterReturning` và bind result đúng kiểu. `new ResultService()` thì không đi qua proxy; dù method trả về bình thường, Spring advice không có cơ hội chạy.

**Đúng mục tiêu:** integration test phải khởi tạo context với aspect bean, auto-proxy support, Spring target bean, gọi method qua Spring reference và xác nhận event/counter.

## 14. Bảng lỗi thường gặp

| Hiện tượng | Nguyên nhân có khả năng | Cách xác minh trong Spring |
| --- | --- | --- |
| Không có advice sau normal return | Aspect không là Spring bean, thiếu AOP enable/config, pointcut không match hoặc caller không qua proxy | Liệt kê beans, check `AopUtils.isAopProxy`, gọi public method qua Spring reference. |
| Advice không có tham số chạy, advice `returning` không chạy | Kiểu tham số binding không tương thích với returned value/declared return type | So sánh `Object` vs `String`, signature các methods, test từng type. |
| Startup lỗi khi thêm `returning` | `returning` name khác tên tham số, `argNames` sai, pointcut binding mâu thuẫn | Xem root cause của Spring/AspectJ binding, thống nhất tên và kiểu. |
| `@AfterReturning` vẫn chạy dù business fallback là lỗi | Method đã catch exception và return bình thường | Xem join point thật và business outcome; không đồng nhất normal return với success domain. |
| Advice không chạy trên method ném lỗi | Đây là behavior đúng của `@AfterReturning` | Dùng `@AfterThrowing` để theo dõi exception; `@After` nếu cần after-finally. |
| Advice không chạy khi method `void` | Advice đang bind `String` trong khi method không trả giá trị, hoặc pointcut không match | Dùng dạng không bind result, kiểm tra interception. |
| `@AfterReturning` có log nhưng return về client không đổi | `@AfterReturning` không thay reference trả về | Nếu thật sự cần transform, xem `@Around` hoặc business method. |
| Advice nhận `null` rồi lỗi NPE | Code tự dereference result không null-safe | Null-check; test method khai báo reference return. |
| Số advice invocations không bằng số request HTTP | Có cache/async/retry, nhiều method, self-call hoặc pointcut chỉ match một số bean | Đếm invocation ở method entry, không dùng request count làm proxy. |
| `@AfterReturning` chạy khi future được trả về nhưng task sau đó fail | Advice quan sát wrapper return, không phải async completion | Quan sát future completion/Reactive signals bằng API tương ứng. |
| Method là `final` và Spring dùng CGLIB | Proxy không override/intercept được method | Đổi entry method/class, hoặc JDK interface proxy khi thiết kế cho phép. |
| Gọi từ cùng service qua `this.method()` | Self-invocation bypass proxy | Tách sang bean cộng tác Spring và inject proxy, hoặc đặt advice ở entry operation phù hợp. |
| Metrics/audit trong advice tự throw | Caller có thể nhận exception dù target return bình thường | Chọn error policy rõ, test cả advice failures. |

## 15. Quy trình áp dụng vào dự án

1. **Chọn sự kiện đúng:** cần quan sát **normal method return** hay business success/transaction commit/async completion? Nếu không phải normal return, có thể cần cơ chế khác.
2. **Chọn tập method bằng pointcut:** giới hạn package, class, method và proxy contract. Tránh `execution(* *(..))` quá rộng.
3. **Chọn có bind hay không:** chỉ cần đếm normal returns thì dùng `@AfterReturning("...")`; cần đọc data thì dùng `returning`.
4. **Nếu bind, chọn type có chủ đích:** `String` giúp chỉ chọn kết quả text; `Object` linh hoạt hơn nhưng cần null/type checking.
5. **Đặt tên tham số nhất quán:** `returning="result"` khớp `result` của advice; thêm `argNames` khi cần.
6. **Đăng ký aspect với Spring:** `@Component` hoặc `@Bean`, đúng scope/context; bật Spring AOP support.
7. **Kiểm tra proxy boundary:** target là Spring bean, method chặn được, external call qua proxy. Loại trừ self-invocation/`new`.
8. **Viết test cho nhiều outcomes:** successful return, wrong return type, `void`, `null`, exception, async wrapper khi có liên quan.
9. **Xem lại side effects:** advice không thay return reference; audit/metrics có thể ném lỗi hoặc tác động performance/transaction.
10. **Chỉ tuyên bố success theo đúng nghĩa:** normal return của method không bảo đảm commit, delivery, authorization hay end-to-end success.

## 16. Checklist cuối bài

- [ ] Biểu thức pointcut chọn đúng Spring target bean/method cần quan sát?
- [ ] Aspect có `@Aspect` và được Spring đăng ký bằng `@Component`/`@Bean`?
- [ ] Spring @AspectJ proxy support đã bật và có AspectJ Weaver phù hợp?
- [ ] Caller thực sự gọi target qua Spring proxy; không phải `new` hoặc `this.method()`?
- [ ] Đã phân biệt normal return, caught exception, thrown exception và async completion?
- [ ] Nếu dùng `returning`, tên khớp tên parameter advice và `argNames` nếu được khai báo?
- [ ] Advice parameter type có chủ đích và tương thích với return values cần match?
- [ ] `void`/`null`/primitive/`Optional` được test khi có trong nghiệp vụ?
- [ ] Không cố thay thế returned reference bằng giá trị return của `@AfterReturning`?
- [ ] Side effects trên mutable returned objects, audit logging và exception trong advice được kiểm soát?
- [ ] Nếu còn transaction/cache/security/async advisors, đã xem xét thứ tự và semantics thực tế?
- [ ] Integration test xác nhận advice events/counters đúng số lần và business result không đổi?

**Điểm nhớ:** Trong Spring AOP, `@AfterReturning` dành cho **lời gọi qua proxy đã hoàn tất bình thường**. `returning="result"` cho phép advice **nhận giá trị trả về** và **ràng buộc thêm theo kiểu tham số**, nên một advice bind `String` có thể chạy ít lần hơn advice không bind dù cả hai dùng chung pointcut. Nó **không thay kết quả**; nếu muốn transform, dùng một thiết kế khác như `@Around`. Luôn kiểm tra đúng Spring bean, proxy, method outcome và contract nghiệp vụ.

## Tài liệu tham khảo chính thức

- [Spring Framework 6.2 — Declaring Advice / After Returning Advice và return binding](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Proxying Mechanisms, self-invocation, JDK và CGLIB](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — Advice API / AfterReturningAdvice](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html)
- [Spring Framework 6.2 — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-003">@AfterThrowing và ràng buộc kiểu ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# `@AfterThrowing` và ràng buộc kiểu ngoại lệ trong Spring AOP

## 1. `@AfterThrowing` dùng để làm gì?

Trong Spring AOP, `@AfterThrowing` khai báo **advice được chạy khi một method execution được pointcut chọn kết thúc bằng cách ném exception**. Nó thường phục vụ log lỗi, ghi metric, audit sự cố hoặc gửi tín hiệu quan sát *sau khi* method đích thất bại.

Ví dụ:

```java
@Aspect
@Component
public class FailureAspect {

    @AfterThrowing(
        pointcut = "execution(* com.example.app.service..*.*(..))",
        throwing = "error"
    )
    public void recordFailure(Exception error) {
        System.out.println("SERVICE FAILED: " + error.getMessage());
    }
}
```

Đây là **đoạn code Spring AOP minh họa**, chưa phải chương trình chạy độc lập: cần imports, Spring context và các service beans thật.

Trong đoạn này:

- `@Aspect` đánh dấu class khai báo advice; `@Component` giúp component scanning đăng ký aspect thành Spring bean khi package được scan.
- `pointcut` chỉ định những method executions thuộc nhóm service có thể được chọn.
- `throwing = "error"` **bind ngoại lệ được ném** vào tham số có tên `error` của advice.
- Kiểu tham số `Exception` còn đóng vai trò **lọc loại ngoại lệ**. Advice này không nhận những throwable không phải subtype của `Exception`, chẳng hạn một số `Error`.

**Kết luận cốt lõi:** `@AfterThrowing` kết hợp **điều kiện method execution** và **điều kiện loại exception thoát khỏi method**. Chỉ khi cả hai phù hợp, advice mới chạy.

Bài chỉ tập trung vào Spring Framework và Spring AOP proxy-based advice; không mở rộng sang kiến thức Exception hoặc Aspect tổng quát.

## 2. Luồng chạy khi method ném ngoại lệ

Hình dung client gọi Spring bean thông qua proxy:

```text
Client
  |
  | service.placeOrder(...)
  v
Spring AOP proxy
  |
  +--> các advice/interceptor phù hợp
  |
  v
Target.placeOrder(...)
  |
  +--> xử lý thành công --> return
  |                         |
  |                         +--> @AfterThrowing KHÔNG chạy
  |
  +--> throw IllegalArgumentException
                            |
                            v
               @AfterThrowing có pointcut
               và kiểu exception tương thích?
                      /              \
                    CÓ              KHÔNG
                     |                 |
                     v                 |
               advice chạy            |
                     |                 |
                     +--------+--------+
                              |
                              v
                  Exception tiếp tục ra caller
                   nếu advice không thay đổi nó
```

Với cách sử dụng thông thường, Spring gọi `@AfterThrowing` **khi target execution thoát bằng exception phù hợp**, rồi exception tiếp tục đi ra phía client. Method advice không cần gọi `proceed()`.

### Trường hợp không kích hoạt

`@AfterThrowing` không chạy khi:

1. Method trả về bình thường, dù giá trị trả về là `null`, `false` hay một object báo thất bại.
2. Method tự bắt exception bên trong, xử lý xong rồi **trả về bình thường**.
3. Exception được ném nhưng **pointcut không khớp method execution**.
4. Exception thoát ra không tương thích với **kiểu tham số của advice**.
5. Lời gọi không đi qua Spring AOP proxy, ví dụ object tự tạo bằng `new` hoặc một lời gọi nội bộ `this.method()` trên target.
6. Một interceptor phía ngoài short-circuit trước khi đến join point, khiến method đích không được thực thi.

**Lưu ý quan trọng từ tài liệu Spring:** `@AfterThrowing` không phải callback bắt tất cả lỗi trong interceptor chain. Về ngữ nghĩa, nó xử lý lỗi xuất phát từ **join point (method đích)**, không được hiểu là cơ chế tự bắt exception được ném bởi `@After` hoặc `@AfterReturning` advice đi kèm.

## 3. Cú pháp của annotation và tham số `throwing`

### 3.1. Chỉ muốn biết method thất bại

Nếu chỉ cần thực hiện side-effect khi method ném exception, không cần lấy exception:

```java
@AfterThrowing("execution(* com.example.app.service..*.*(..))")
public void countFailure() {
    System.out.println("FAILED");
}
```

Đây là **method mẫu** phải đặt trong bean `@Aspect`. Nó áp dụng cho bất kỳ loại throwable nào có thể làm method execution đã chọn kết thúc bất thường, trong phạm vi semantics của advice.

### 3.2. Cần truy cập đối tượng exception

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    throwing = "cause"
)
public void afterFailure(Throwable cause) {
    System.out.println(cause.getClass().getSimpleName());
}
```

Trong Spring, chuỗi `"cause"` ở thuộc tính `throwing` phải khớp **tên tham số** nhận exception của Java advice method. Đây là **tên binding variable**, không phải tên class Exception và không phải một tên tùy ý Spring bỏ qua.

Có thể chỉ rõ `argNames` để tăng độ rõ ràng khi compiler không lưu parameter names:

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    throwing = "cause",
    argNames = "cause"
)
public void afterFailure(Throwable cause) {
    System.out.println(cause.getMessage());
}
```

**Ý nghĩa của kiểu `Throwable`:** đây là supertype của các nhóm `Exception` và `Error` nên **không thu hẹp exception type** như các tham số cụ thể hơn. Tuy nhiên method vẫn phải phù hợp với pointcut, đi qua proxy và kết thúc bằng throwable.

### 3.3. `throwing` khác `throws` của method Java

So sánh:

```java
public void importFile() throws IOException {
    // ...
}
```

với:

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.importFile(..))",
    throwing = "error"
)
public void onImportError(IOException error) {
    // ...
}
```

`throws IOException` trong chữ ký method Java là **khai báo khả năng ném checked exception**. `throwing = "error"` trong advice là **binding name** cho throwable thực tế thoát khỏi lời gọi. Kiểu `IOException` trong advice là điều kiện match theo kiểu runtime, không phải chỉ là việc Java method có khai báo `throws IOException` hay không.

Một method có `throws IOException` nhưng trả về bình thường **không kích hoạt** advice. Ngược lại, một unchecked exception không cần nằm trong `throws` vẫn có thể được advice bắt match theo loại.

## 4. Ràng buộc kiểu ngoại lệ hoạt động ra sao?

Khi dùng `throwing`, **kiểu khai báo của advice parameter** thu hẹp các exception có thể match:

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    throwing = "error"
)
public void onInvalidInput(IllegalArgumentException error) {
    System.out.println("INVALID: " + error.getMessage());
}
```

Nếu method đích ném `new IllegalArgumentException(...)`, advice trên có thể chạy; nếu ném `new IllegalStateException(...)`, advice đó **không chạy**, dù cả hai đều là `RuntimeException`.

| Kiểu tham số advice | Những throwable tương thích về kiểu | Ví dụ không match |
| --- | --- | --- |
| `Throwable` | Bất kỳ subtype của `Throwable` | Không có subclass của `Throwable` nào bị loại chỉ vì kiểu |
| `Exception` | Checked exceptions, `RuntimeException` và các subtype | `Error` không phải `Exception` |
| `RuntimeException` | Các runtime exceptions và subtype | `IOException`, `Error` |
| `IllegalArgumentException` | `IllegalArgumentException` và subclass | `IllegalStateException` |
| `IOException` | `IOException` và subclass | `IllegalArgumentException` |
| `FileNotFoundException` | `FileNotFoundException` và subclass | `IOException` không thuộc subtype `FileNotFoundException` |

**So sánh bằng quan hệ kế thừa, không nhất thiết trùng class chính xác.** Ví dụ nếu `MissingFieldException extends IllegalArgumentException`, advice nhận `IllegalArgumentException` vẫn có thể được gọi khi runtime throwable là `MissingFieldException`.

Chú ý: điều kiện runtime exception type **khác** điều kiện subtype/class của target service trong `target(Type)` hoặc `this(Type)`. `throwing` nói về đối tượng throwable rời khỏi join point, không nói về class của target bean.

## 5. Demo Java 17+: match theo `IllegalArgumentException` và `IOException`

Ví dụ này tạo một Spring AOP proxy cho service và sử dụng **hai method advice có pointcut khác nhau** để tránh nhầm thứ tự giữa các advice:

- `onIllegalArgument(...)` chọn execution của `process(..)`, chỉ nhận `IllegalArgumentException`.
- `onIOException(...)` chọn execution của `load(..)`, chỉ nhận `IOException`.
- Method `process("ok")` thành công.
- Method `process("handled")` có exception nội bộ nhưng **bắt lỗi trước khi return**; advice không chạy.
- Method `process("bad")` ném `IllegalArgumentException`, advice thứ nhất chạy.
- Method `process("state")` ném `IllegalStateException`, advice thứ nhất **không** chạy vì không đúng subtype.
- Method `load()` ném checked `IOException`, advice thứ hai chạy.

Ứng dụng sử dụng **Java 17+, Maven, Spring Framework 6.2.6**; không cần Spring Boot. Dùng `@Bean` để Spring đăng ký các bean một cách tường minh.

### 5.1. Cấu trúc tệp

```text
spring-aop-after-throwing/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── AfterThrowingDemo.java
```

### 5.2. Tệp `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-after-throwing</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.6</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.6</version>
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
                <configuration>
                    <mainClass>demo.AfterThrowingDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 5.3. Tệp `src/main/java/demo/AfterThrowingDemo.java`

```java
package demo;

import java.io.IOException;

import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class AfterThrowingDemo {

    public interface TaskService {
        String process(String scenario);
        void load() throws IOException;
    }

    public static class TaskServiceImpl implements TaskService {
        @Override
        public String process(String scenario) {
            System.out.println("TARGET process " + scenario);

            if ("bad".equals(scenario)) {
                throw new IllegalArgumentException("missing id");
            }

            if ("state".equals(scenario)) {
                throw new IllegalStateException("unavailable");
            }

            if ("handled".equals(scenario)) {
                try {
                    throw new IllegalArgumentException("internal");
                } catch (IllegalArgumentException ex) {
                    return "RECOVERED";
                }
            }

            return "OK";
        }

        @Override
        public void load() throws IOException {
            System.out.println("TARGET load");
            throw new IOException("cannot read file");
        }
    }

    @Aspect
    public static class FailureAspect {
        @AfterThrowing(
            pointcut = "execution(* demo..*.process(..))",
            throwing = "failure",
            argNames = "failure"
        )
        public void onIllegalArgument(IllegalArgumentException failure) {
            System.out.println("ADVICE illegal " + failure.getMessage());
        }

        @AfterThrowing(
            pointcut = "execution(* demo..*.load(..))",
            throwing = "failure",
            argNames = "failure"
        )
        public void onIOException(IOException failure) {
            System.out.println("ADVICE io " + failure.getMessage());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class Config {
        @Bean
        public TaskService taskService() {
            return new TaskServiceImpl();
        }

        @Bean
        public FailureAspect failureAspect() {
            return new FailureAspect();
        }
    }

    private static void callProcess(TaskService service, String scenario) {
        try {
            System.out.println(
                "CLIENT result=" + service.process(scenario)
            );
        } catch (RuntimeException ex) {
            System.out.println(
                "CLIENT caught " + ex.getClass().getSimpleName()
                        + ":" + ex.getMessage()
            );
        }
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {

            TaskService service = context.getBean(TaskService.class);
            System.out.println(
                "AOP_PROXY=" + AopUtils.isAopProxy(service)
            );

            callProcess(service, "ok");
            callProcess(service, "handled");
            callProcess(service, "bad");
            callProcess(service, "state");

            try {
                service.load();
            } catch (IOException ex) {
                System.out.println(
                    "CLIENT caught IOException:" + ex.getMessage()
                );
            }
        }
    }
}
```

### 5.4. Chạy project

Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

Các dòng **do chương trình tự in, theo thứ tự** (không tính logging Maven/Spring nếu có):

```text
AOP_PROXY=true
TARGET process ok
CLIENT result=OK
TARGET process handled
CLIENT result=RECOVERED
TARGET process bad
ADVICE illegal missing id
CLIENT caught IllegalArgumentException:missing id
TARGET process state
CLIENT caught IllegalStateException:unavailable
TARGET load
ADVICE io cannot read file
CLIENT caught IOException:cannot read file
```

### 5.5. Giải thích từng nhánh

**Nhánh `ok`:** target trả bình thường, nên không có `@AfterThrowing`. Điều này vẫn đúng nếu method trả `null` hoặc một object thể hiện lỗi ở mức nghiệp vụ mà không ném exception.

**Nhánh `handled`:** target đã ném `IllegalArgumentException` **trong nội bộ**, nhưng tự bắt rồi trả `"RECOVERED"`. Từ ranh giới method execution mà Spring AOP thấy, method kết thúc bình thường, nên advice không nhận exception nội bộ đó.

**Nhánh `bad`:** target ném `IllegalArgumentException`. Pointcut `process(..)` khớp, kiểu tham số `IllegalArgumentException` phù hợp, nên `onIllegalArgument(...)` chạy trước khi caller nhận exception. Advice chỉ log rồi return `void`; nó **không nuốt lỗi**. Vì vậy caller vẫn bắt được đúng exception.

**Nhánh `state`:** target ném `IllegalStateException`. Đây là subtype của `RuntimeException` nhưng **không phải** subtype của `IllegalArgumentException`. `onIllegalArgument(...)` không chạy; exception vẫn thoát ra client.

**Nhánh `load`:** target ném `IOException`, method đã khai báo `throws IOException` cho Java compiler. Advice `onIOException(IOException failure)` chạy vì cả method matching và thrown exception type đều phù hợp.

Ví dụ có hai `@AfterThrowing` advices trong một aspect nhưng chúng khớp **hai method khác nhau**, nên không phụ thuộc thứ tự bất định của nhiều advice cùng loại trên một join point.

## 6. Hiểu sâu: exception hierarchy và cách chọn tham số

Một bảng ví dụ về quan hệ kiểu:

```text
Throwable
├── Error
└── Exception
    ├── IOException
    │   └── FileNotFoundException
    └── RuntimeException
        ├── IllegalArgumentException
        └── IllegalStateException
```

Khi khai báo:

```java
@AfterThrowing(
    pointcut = "execution(* com.example..service..*.*(..))",
    throwing = "failure"
)
public void onFailure(RuntimeException failure) {
    System.out.println(failure.getClass().getName());
}
```

advice được chọn cho những method match pointcut và ném **RuntimeException hoặc subclass**, chẳng hạn `IllegalArgumentException` / `IllegalStateException`. Nhưng nó không chạy cho checked `IOException`.

Nếu cần phân biệt nhiều nhóm mà không tranh chấp thứ tự advice, có thể:

1. Dùng một advice với tham số kiểu rộng `Throwable` và phân nhánh xử lý theo subtype khi cần.
2. Dùng nhiều aspects với pointcut/throwing type riêng, xác lập thứ tự bằng `@Order` nếu cùng một join point có nhiều advice match.
3. Chọn kiểu đủ hẹp nếu behavior chỉ phục vụ một nhóm ngoại lệ.

Ví dụ một advice chung:

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    throwing = "failure"
)
public void report(Throwable failure) {
    if (failure instanceof IllegalArgumentException) {
        System.out.println("BAD-INPUT");
    } else if (failure instanceof IOException) {
        System.out.println("IO");
    } else {
        System.out.println("OTHER-FAILURE");
    }
}
```

Đây là **snippet** cho bean `@Aspect`, không phải chương trình standalone. Đừng dùng `Throwable` theo phản xạ nếu hệ thống chỉ quan tâm đến nhóm `Exception`; bạn có thể vô tình log các `Error` nghiêm trọng mà không có chiến lược ứng phó phù hợp.

### Subclass exception vẫn match

```java
public class InvalidOrderException extends IllegalArgumentException {
    public InvalidOrderException(String message) {
        super(message);
    }
}
```

Khi method đích ném `InvalidOrderException`, advice có tham số `IllegalArgumentException` **vẫn match** vì subtype hợp lệ. Việc binding không đòi thrown exception có class chính xác bằng parameter type.

## 7. `@AfterThrowing` không phải khối `catch` xử lý lỗi

Một hiểu lầm thường gặp: đặt `@AfterThrowing` để **bắt lỗi rồi trả fallback cho caller**.

```java
@AfterThrowing(
    pointcut = "execution(* com.example..*.find(..))",
    throwing = "error"
)
public void recover(IllegalStateException error) {
    System.out.println("FALLBACK?");
}
```

Đây là **snippet không có logic fallback**. Method advice trả `void` và không có quyền thay thế kết quả của target bằng một giá trị return của advice. Nó được thiết kế để chạy sau khi join point thoát bằng lỗi.

Nếu cần **chặn exception, chuyển đổi exception hoặc trả fallback**, dùng `@Around`:

```java
@Around("execution(* com.example.app.service..*.find(..))")
public Object fallback(ProceedingJoinPoint joinPoint) throws Throwable {
    try {
        return joinPoint.proceed();
    } catch (IllegalStateException ex) {
        return "FALLBACK";
    }
}
```

Đoạn `@Around` trên là **mẫu minh họa**: cần đặt trong `@Aspect` bean và kiểu return `String` phải tương thích với method target. Nó cho phép lời gọi đi tiếp bằng `proceed()`, bắt exception được ném từ phía trong và trả một giá trị khác.

Nếu around advice nuốt exception ở một vị trí trong chain, advice `@AfterThrowing` nằm **ngoài** nó có thể không quan sát được lỗi gốc, vì phần phía trong đã trả bình thường. Do đó khi có nhiều advisors, cần xét **thứ tự nesting** và xem mỗi advice nhìn thấy kết quả/exception nào.

### Việc ném một exception khác trong advice

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.save(..))",
    throwing = "error"
)
public void logFailure(RuntimeException error) {
    // Có thể log, gửi metric; tránh tùy tiện throw exception mới ở đây.
}
```

Nếu advice tự ném exception, exception mới có thể ảnh hưởng đường lỗi client nhận. Đây không phải cách mặc định để wrap/recover lỗi nghiệp vụ. Khi cần ánh xạ exception rõ ràng, `@Around` làm chủ control flow thường dễ đọc và dễ kiểm thử hơn.

## 8. So sánh `@AfterThrowing` với những loại advice khác

| Advice | Khi nào chạy trong Spring AOP? | Có bind exception? | Có điều khiển flow bằng `proceed()`? |
| --- | --- | --- | --- |
| `@Before` | Trước matched method execution | Không phải binding throwable từ method outcome | Không |
| `@AfterReturning` | Method execution trả bình thường | Không, bind return qua `returning` nếu cần | Không |
| `@AfterThrowing` | Method execution ném exception phù hợp | **Có, qua `throwing`** | Không |
| `@After` | Sau method execution, bất kể normal hay exceptional exit | Không phải dạng bind exception như `@AfterThrowing` | Không |
| `@Around` | Bao quanh phần execution/interceptors phía trong | Có thể tự `catch` exception từ `proceed()` | **Có** |

Trong Spring, `@After` được dùng theo ý nghĩa **finally**: phù hợp với cleanup luôn phải chạy. `@AfterThrowing` chỉ chạy trên nhánh lỗi, thích hợp cho error-only logging/metrics. Nếu cần biến lỗi thành kết quả hoặc thực hiện retry, cân nhắc around advice.

### Thứ tự các advice có ảnh hưởng

Ví dụ nếu một method được nhiều aspects advice:

```text
Outer @Around: enter
    Target: throws IllegalArgumentException
    Inner @AfterThrowing: log error (nếu điều kiện khớp)
Outer @Around: catch -> trả fallback
Client: nhận fallback
```

Đây là **một kịch bản thứ tự có thể xảy ra**, không phải thứ tự mặc định cố định. Nếu `@Around` xử lý exception trước khi một outer `@AfterThrowing` nhận nó, outer advice có thể không chạy. Spring cho phép `@Order` / `Ordered` để xác định precedence giữa các aspects, nhưng cần kiểm thử với thứ tự cấu hình hiệu lực.

Không dựa vào thứ tự khai báo hai methods `@AfterThrowing` cùng loại trong cùng aspect để dự đoán thứ tự execution nếu chúng match cùng một join point. Tài liệu Spring nêu không có thứ tự ổn định dựa vào thứ tự source Java cho advice cùng loại; khi thứ tự quan trọng, refactor hoặc tách aspects và khai báo order.

## 9. Những lỗi binding thường gặp

### 9.1. Tên trong `throwing` không trùng tên advice parameter

Sai:

```java
@AfterThrowing(
    pointcut = "execution(* com.example..*.save(..))",
    throwing = "cause"
)
public void onFailure(IOException error) {
    // Tên trong throwing và tham số không đồng nhất.
}
```

Đây là **mã sai để minh họa**. Spring cần bind `cause` nhưng Java method khai báo tham số `error`. Nếu tên không được resolve/khớp đúng, context có thể báo lỗi binding/pointcut khi tạo advisor hoặc gọi.

Đúng:

```java
@AfterThrowing(
    pointcut = "execution(* com.example..*.save(..))",
    throwing = "error",
    argNames = "error"
)
public void onFailure(IOException error) {
    // Có thể đọc exception được bind.
}
```

Với tham số khác như `JoinPoint` hoặc nhiều binding, phải xét đầy đủ `argNames` và số lượng/kiểu tham số, tránh cho Spring suy tên sai khi Java bytecode không lưu parameter names.

### 9.2. Dùng method parameter type không phải Throwable

Không viết advice `throwing` nhận `String` rồi kỳ vọng `error.getMessage()` hay tự convert thrown exception thành chuỗi. Với `@AfterThrowing`, tham số `throwing` phải dùng kiểu phù hợp throwable mà bạn muốn match.

### 9.3. Dùng kiểu hẹp hơn exception thực tế

Ví dụ `onFailure(FileNotFoundException e)` không chạy với mọi `IOException`. Nó chỉ chạy khi runtime exception là `FileNotFoundException` hoặc subtype. Đây là feature chọn theo kiểu, không phải bug của Spring.

### 9.4. Quên đăng ký aspect thành Spring bean

Chỉ có `@Aspect` trên class không bảo đảm Spring tự quét và đăng ký. Có thể kết hợp `@Component` và component-scan hoặc đăng ký qua `@Bean`; đồng thời kích hoạt xử lý @AspectJ bằng `@EnableAspectJAutoProxy` trong Spring Framework thuần hoặc auto-configuration Spring Boot phù hợp.

### 9.5. Pointcut không khớp tên method/package

Nếu exception ném đúng loại nhưng `execution(...)` chỉ chọn một package khác, advice vẫn không chạy. Khi debug, tạm sử dụng một pointcut method rõ ràng rồi thu hẹp theo package/annotation để xác định điều kiện nào không match.

### 9.6. Phương thức tự bắt exception

Đây là ngữ nghĩa chuẩn của method execution, không phải lỗi binding. Spring AOP chỉ thấy **method kết thúc bình thường**, vì vậy không chạy `@AfterThrowing` cho exception đã được catch trong target rồi trả thành công.

## 10. Ứng dụng Spring thực tế: audit, metrics, transaction, async

### 10.1. Audit và metrics trên nhánh lỗi

Một advice có thể ghi sự kiện theo kiểu exception:

```java
@AfterThrowing(
    pointcut = "execution(public * com.example.app.service..*.*(..))",
    throwing = "error"
)
public void recordFailure(Exception error) {
    metrics.increment("service.failure", error.getClass().getSimpleName());
}
```

Đây là **mẫu kiến trúc**, cần một dependency `metrics` được inject qua Spring và API recorder phù hợp. Không nên giả định thư viện metrics nào cũng có chữ ký `increment(...)` như minh họa.

Nếu aspect gọi một collaborator được advice khác, cần kiểm tra pointcut không vô tình bao phủ chính collaborator đó và tạo vòng gọi lặp. Nên giới hạn pointcut đúng service boundary.

### 10.2. Spring transaction và ngoại lệ

Một method có `@Transactional` thường được intercept bởi transaction advisor riêng. `@AfterThrowing` của bạn chỉ là một advisor khác nếu pointcut khớp; **thời điểm log xảy ra so với transaction rollback** phụ thuộc thứ tự advisors. Không suy rằng cứ `@AfterThrowing` chạy thì transaction đã rollback.

Quy tắc rollback của Spring transaction còn tùy loại exception và cấu hình `rollbackFor` / `noRollbackFor`. `@AfterThrowing` không tự cấu hình rollback.

### 10.3. Checked exception và JDK dynamic proxy

Khi dùng JDK dynamic proxy, method có checked `IOException` được khai báo trên interface là cách trực tiếp để caller bắt `IOException` đúng contract. Với các checked exceptions **không khai báo** trên interface method mà bị đẩy qua một JDK dynamic proxy, Java proxy semantics có thể bọc chúng thành `UndeclaredThrowableException`. Điều đó khác với việc `@AfterThrowing` **nhìn thấy throwable tại target execution** và bind theo runtime type.

Tránh dùng ý tưởng “unchecked/checked được quyết định bởi `@AfterThrowing`”. Annotation này không làm Java bỏ luật khai báo checked exceptions.

### 10.4. Async execution

Nếu có `@Async`, method thực có thể chạy trên thread của executor và kết quả được biểu diễn qua `Future` / `CompletableFuture` hoặc handler riêng cho `void`. Một exception xảy ra trên async thread **không tự tương đương** với exception đồng bộ thoát ra từ lời gọi proxy mà advice phía ngoài nhìn thấy. Khi audit lỗi async, phải xét vị trí advisor, contract của async method và cơ chế completion/error handling của future.

### 10.5. Retry và fallback

Nếu dùng retry advice (ví dụ thông qua một `@Around` hoặc Spring Retry phù hợp), **một lời gọi từ client có thể dẫn đến nhiều lần target execution**. Một `@AfterThrowing` nằm bên trong retry loop có thể được gọi ở mỗi thất bại, trong khi advice nằm ngoài loop chỉ thấy lỗi cuối sau khi retry cạn. Khi metric cần “số attempt” hay “số failed client requests”, phải cấu hình đúng thứ tự advisors hoặc đếm tại đúng ranh giới.

## 11. Tối ưu thiết kế advice xử lý sự kiện lỗi

Một `@AfterThrowing` tốt thường:

1. **Giữ side-effect gọn và có giới hạn**, như log/metric/audit; không gọi network sync dài nếu đường lỗi latency quan trọng.
2. **Không chứa dữ liệu nhạy cảm không cần thiết** trong log. `Exception.getMessage()` có thể chứa input do client cung cấp hoặc chi tiết database/path nội bộ.
3. **Không ném exception mới tùy tiện** chỉ vì logging/metric thất bại. Nếu telemetry thất bại cũng phá error path, nguyên nhân thật có thể bị che khuất.
4. **Giữ pointcut đủ hẹp**, ví dụ chỉ public service methods, tránh ghi lặp trên proxy hoặc hạ tầng không chủ đích.
5. **Tránh duplicate logs** trên mỗi tầng service nếu pointcut phủ quá rộng và cùng một exception đi qua nhiều proxy calls. Chọn nơi ghi nhận phù hợp (một service boundary hoặc outer request boundary) và đưa correlation ID khi cần.
6. **Chọn exception type đúng ý định**: `IllegalArgumentException` cho nhóm validation cụ thể, `RuntimeException` cho runtime failure, `Throwable` chỉ nếu thật sự cần cả `Error`.
7. **Kiểm tra đường đi proxy** bằng integration test, đặc biệt với self-invocation và lựa chọn JDK/CGLIB.
8. **Có positive/negative test**: test lỗi đúng subtype, sai subtype, successful return và exception đã xử lý trong target.

Nếu muốn biến đổi exception thành API response, đặt xử lý ở tầng thích hợp (ví dụ exception handler web layer) thay vì trông chờ `@AfterThrowing` trả một giá trị mới cho method đã thất bại.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Method được advice trả về `"ERROR"` nhưng không ném exception. `@AfterThrowing` chạy không?  
**Đáp án:** Không. Đây là normal return ở Spring AOP method execution level.

**Câu 2.** Advice dùng `throwing="ex"`, nhưng method Java khai báo tham số `IOException error`. Có vấn đề gì?  
**Đáp án:** Tên binding không khớp. Cần `throwing="error"` hoặc đổi tên advice parameter tương ứng, kết hợp `argNames` nếu cần.

**Câu 3.** `@AfterThrowing` với tham số `IllegalArgumentException` có nhận `IllegalStateException` không?  
**Đáp án:** Không. Hai class là những nhánh khác nhau của `RuntimeException`.

**Câu 4.** Một `InvalidOrderException extends IllegalArgumentException` được target ném ra. Advice nhận `IllegalArgumentException` có match không?  
**Đáp án:** Có, miễn method execution và điều kiện pointcut phù hợp.

**Câu 5.** Target bắt `IOException` rồi trả `false`. Advice nhận `IOException` có chạy không?  
**Đáp án:** Không. Exception không thoát ra khỏi method execution.

**Câu 6.** Dùng `@AfterThrowing` để trả string `"FALLBACK"` cho caller được không?  
**Đáp án:** Không theo contract advice này. Muốn điều khiển return/exception flow, dùng `@Around` hoặc cách xử lý tại tầng nghiệp vụ phù hợp.

**Câu 7.** Khi method `process()` gọi `this.load()` trong cùng target, lời gọi `load()` có được Spring AOP advice riêng không?  
**Đáp án:** Không có proxy boundary mới nếu là self-invocation; advice chỉ khớp `load()` không tự chạy cho lời gọi nội bộ ấy.

**Câu 8.** Advice `@AfterThrowing` chạy thì chắc chắn transaction đã rollback chưa?  
**Đáp án:** Chưa thể kết luận. Cần xem transaction advisor order và rollback rules.

**Câu 9.** Một advice dùng `Throwable` và một advice dùng `RuntimeException` đều match lỗi `IllegalArgumentException`. Có thể dựa vào thứ tự khai báo source để xác định thứ tự của chúng không?  
**Đáp án:** Không. Nếu thứ tự quan trọng, hãy thiết kế/tách aspect rõ ràng và khai báo precedence thích hợp.

## 13. Checklist chẩn đoán nhanh

| Hiện tượng | Kiểm tra trước |
| --- | --- |
| Advice không chạy khi method fail | Method có đi qua proxy? Pointcut `execution(...)` có đúng package/method? |
| Một loại exception chạy, loại khác không | Kiểu tham số của `throwing` có phải supertype của thrown exception không? |
| Advice nhận exception khác dự đoán | Có interceptor phía trong wrap exception? Có proxy JDK/checked-exception behavior? |
| Advice không chạy dù bên trong method có `throw` | Exception có bị catch trước khi method thoát? |
| Spring báo lỗi binding | Tên `throwing`, method parameter, `argNames` có đồng nhất không? |
| Log lỗi bị nhân đôi | Pointcut phủ nhiều service boundaries hoặc retry nhiều attempts? |
| Method đổi từ trả exception sang fallback | Có `@Around` ở trong đã catch/return thành công? |
| Lỗi cuối bị thay bằng lỗi log | Advice có tự throw exception khi logging/metric thất bại không? |
| Advice chạy nhưng rollback không như nghĩ | Xem transaction ordering và rollback rules; `@AfterThrowing` không điều khiển chúng |
| Chỉ xảy ra khác biệt ở async methods | Kiểm tra executor, future completion và thứ tự async/advisor |

## 14. Tài liệu Spring Framework chính thức

- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `@AfterThrowing`, `throwing` binding, exception type filtering, after-finally/around và precedence.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxies, self-invocation và giới hạn phương thức.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): bật xử lý Spring AOP @AspectJ style và đăng ký aspect bean.
- [Spring Framework — AspectJ Advice API and Parameters](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): bind tham số advice, `argNames` và method arguments.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-004">@After với ngữ nghĩa finally trong Spring AOP</a>

<details>
<summary>Xem chi tiết</summary>

# @After với ngữ nghĩa finally trong Spring AOP

## 1. Kết quả học tập và phạm vi

Trong **Spring AOP dùng annotation kiểu @AspectJ**, `@After` định nghĩa **after (finally) advice**: advice chạy khi một **method execution khớp pointcut** kết thúc, bất kể method trả về thành công hay ném ngoại lệ ra ngoài. Đây là ngữ nghĩa *tương tự khối `finally` của Java* — một hook để dọn dẹp hoặc thực hiện công việc hậu xử lý cần xảy ra ở cả hai nhánh.

Ví dụ Spring Aspect:

~~~java
@Aspect
public class CleanupAspect {
    @After("execution(* example.TaskService.run*(..))")
    public void afterTask() {
        System.out.println("cleanup: method finished");
    }
}
~~~

Nếu `runOk()` trả về bình thường, lời advice vẫn chạy. Nếu `runFail()` ném exception, advice cũng chạy **khi lời gọi khớp thực sự đi qua Spring AOP proxy**. Bản thân `@After` không phân loại thành công/thất bại, không trả về kết quả thay method và cũng không phải một exception handler có chức năng tự chữa lỗi.

Sau bài, người học cần:

1. Mô tả chính xác khi nào `@After` được kích hoạt trên **Spring-managed target bean**.
2. Phân biệt `@After` với `@Before`, `@AfterReturning`, `@AfterThrowing` và `@Around` trong ứng dụng Spring.
3. Dự đoán kết quả của **thành công, exception thoát khỏi method và exception bị bắt ngay trong method**.
4. Sử dụng `JoinPoint` để log tên method mà không nhầm với việc lấy return value/exception.
5. Hiểu giới hạn Spring **proxy boundary**, thứ tự advice, các trường hợp advice tự ném exception hoặc method trả về giá trị bất đồng bộ.
6. Viết và chạy một dự án Maven/Java 17+ độc lập chứng minh ngữ nghĩa này.

**Special Note của module:** chỉ tập trung vào **Spring Aspect/Spring AOP**; kiến thức Aspect tổng quát nằm ở chủ đề khác. Các ví dụ có @Aspect dùng **Spring AOP proxy-based**, không cần cấu hình AspectJ compile-time hoặc load-time weaving.

## 2. Ngữ nghĩa `@After` theo tài liệu Spring Framework

Đọc một advice như sau:

~~~java
@After("execution(* example.TaskService.run*(..))")
public void afterCompletion(JoinPoint jp) {
    System.out.println("finished: " + jp.getSignature().getName());
}
~~~

Điều kiện đầu tiên là expression phải match **method execution** trên **target bean được Spring AOP can thiệp**. Điều kiện thứ hai là caller phải gọi method **qua Spring AOP proxy**, để interceptor/advisor tương ứng có thể được thực thi.

Nếu hai điều kiện thỏa, khi method execution **thoát theo một trong hai hướng**, advice chạy:

~~~text
Caller --> Spring AOP proxy --> target method
             |                    |
             |        return bình thường hoặc throw exception
             |                    |
             +--> @After advice <--+
             |
             +--> caller nhận kết quả hoặc exception
~~~

Hãy hiểu cụm "finally" chính xác:

- **Trả về thành công:** `@After` chạy sau khi target method hoàn tất, trước khi caller nhận kết quả từ đường gọi proxy.
- **Ném exception ra ngoài:** `@After` vẫn chạy trong quá trình stack unwinding của lời gọi đang được advice.
- **Bắt exception ngay bên trong method:** từ góc nhìn proxy, method có thể **trả về bình thường**; `@After` vẫn chạy, nhưng không thể tự suy ra rằng một exception đã từng xuất hiện và bị bắt trong target.
- **Method không được thực thi qua proxy:** không có lời gọi advisory phù hợp, nên `@After` của Spring AOP không được kích hoạt cho đường gọi đó.
- **Caller quyết định bắt exception hay không:** đây là bước nằm ngoài lời gọi advice; `@After` không tự thay hành vi lan truyền exception.

Spring documentation mô tả `@After` là after-finally advice, thường dùng cho releasing resources và các công việc tương tự. Không nên dùng nó như thay thế tổng quát cho mọi cơ chế vòng đời tài nguyên hoặc transaction.

## 3. So sánh các loại advice trên cùng một method

| Annotation | Khi nào chạy? | Có biết nhánh cụ thể không? | Vai trò phù hợp |
| --- | --- | --- | --- |
| `@Before` | Trước khi method execution phù hợp được chạy qua proxy | Chưa có kết quả hay exception của method | Kiểm tra/tracing trước khi gọi |
| `@AfterReturning` | Sau khi method execution **trả về bình thường** | Có thể bind return value bằng `returning` | Hậu xử lý cho nhánh thành công |
| `@AfterThrowing` | Khi method execution **ném exception thoát ra ngoài** | Có thể bind throwable bằng `throwing` | Ghi nhận thất bại theo loại exception |
| **`@After`** | **Sau method execution, cả return và throw** | Không tự nhận tham số `returning` hoặc `throwing` | Dọn dẹp/hậu xử lý cần luôn thực hiện |
| `@Around` | Bao quanh method execution nếu invocation đi vào advice | Chủ động gọi `proceed()`, có thể quan sát cả return/throw | Kiểm soát luồng, timing với state per-invocation, retry/short-circuit |

Hai điểm dễ nhầm:

1. **`@After` không phải tên viết ngắn của `@AfterReturning`.** Khi target ném exception, `@AfterReturning` không chạy, nhưng `@After` vẫn chạy.
2. **`@AfterThrowing` không tự nuốt exception.** Nó là callback ở nhánh thất bại của join point; việc log một exception không biến lời gọi thành return thành công.

Spring khuyến nghị chọn **loại advice ít quyền lực nhất đủ cho mục đích**. Nếu chỉ cần thực thi logic sau khi method hoàn tất theo cả hai hướng, `@After` rõ ràng hơn `@Around` chỉ để dựng `try/finally`. Ngược lại, nếu phải đo thời gian bằng biến `startTime` riêng cho mỗi invocation hoặc kiểm soát return/throw, `@Around` với `try/finally` có thể phù hợp hơn.

## 4. Spring AOP làm gì với `@After` lúc chạy?

Để advice trở thành một phần đường gọi, ứng dụng phải có đầy đủ:

1. **Một Spring-managed aspect bean:** class có `@Aspect` và được đăng ký bằng `@Component` trong package được scan, `@Bean` hoặc bean definition khác.
2. **Hỗ trợ Spring AOP đang bật:** trong Spring Framework thuần thường dùng `@EnableAspectJAutoProxy` và classpath gồm các dependency cần thiết.
3. **Một advisor/pointcut phù hợp:** Spring đọc metadata `@After` và xác định method execution nào cần được advice.
4. **Một target bean có proxy hợp lệ:** Spring auto-proxy creator có thể bọc target bằng JDK dynamic proxy hoặc CGLIB tùy cấu hình.
5. **Lời gọi thực sự vượt qua ranh giới proxy:** client nhận bean reference do Spring cung cấp và gọi method tương ứng.

Luồng khái niệm:

~~~text
@Aspect bean
   -> Spring phát hiện @After + expression
   -> Advisor/interceptor phù hợp với target class/method
   -> Spring AOP proxy cho target bean
   -> Client gọi method qua proxy
   -> Target trả về hoặc ném exception
   -> @After advice chạy theo finally semantics
   -> Proxy hoàn tất lời gọi tới client
~~~

**`@Aspect` tự nó không tự đăng ký bean**; nếu thiếu `@Component`/`@Bean`, Spring có thể không tìm thấy aspect để tạo advisor. Và chỉ có `@After` trên một phương thức Java bất kỳ nhưng class không có `@Aspect` cũng không tự biến method thành Spring AOP advice.

Đừng nhầm việc đặt dependency `aspectjweaver` trong POM với việc bật weaving. Bài này dùng **Spring AOP proxy**, không dệt bytecode của target.

## 5. Ví dụ Java 17+ chạy được: success, failure, caught và self-invocation

Dự án sau sử dụng:

- **Java 17+** và Maven.
- Spring Framework **6.2.19**, AspectJ Weaver **1.9.25.1**.
- Một `TaskService` là Spring bean với các method trả về bình thường, ném exception và bắt exception nội bộ.
- Một `LifecycleAspect` khai báo **cả `@AfterReturning`, `@AfterThrowing` và `@After` trong cùng aspect**. Điều này cho phép quan sát thứ tự theo Spring 6.2.
- Một lời gọi nội bộ `this.runOk()` để làm rõ giới hạn proxy.

Không dùng database, HTTP server, thread pool hay transaction manager.

### 5.1. Cấu trúc thư mục

~~~text
spring-after-finally-demo/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── TaskService.java
    ├── LifecycleAspect.java
    └── Main.java
~~~

### 5.2. Maven `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>spring-after-finally-demo</artifactId>
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
                <version>6.2.19</version>
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
~~~

Spring Framework BOM giúp các module Spring sử dụng cùng bộ phiên bản. Nếu dự án dùng Spring Boot, ưu tiên BOM/parent và dependency do Boot quản lý thay vì chép những version này vào một dự án đang có cấu hình khác.

### 5.3. Target `TaskService.java`

~~~java
package example;

public class TaskService {

    public String runOk() {
        System.out.println("target: ok");
        return "OK";
    }

    public String runFail() {
        System.out.println("target: fail");
        throw new IllegalStateException("boom");
    }

    public String runRecovered() {
        System.out.println("target: recovered begin");
        try {
            throw new IllegalArgumentException("temporary");
        } catch (IllegalArgumentException ex) {
            System.out.println("target: caught temporary");
            return "RECOVERED";
        }
    }

    public String callInternally() {
        System.out.println("target: callInternally");
        return this.runOk();
    }
}
~~~

Đây là class Java thông thường. `runOk`, `runFail` và `runRecovered` đều có tên bắt đầu bằng `run`; `callInternally` thì không. Nó gọi `this.runOk()` từ bên trong target nên **không quay qua Spring AOP proxy**.

### 5.4. Aspect `LifecycleAspect.java`

~~~java
package example;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class LifecycleAspect {

    private static final String METHODS =
        "execution(* example.TaskService.run*(..))";

    @AfterReturning(pointcut = METHODS, returning = "value")
    public void afterSuccess(JoinPoint jp, Object value) {
        System.out.println("after-returning: "
            + jp.getSignature().getName() + " => " + value);
    }

    @AfterThrowing(pointcut = METHODS, throwing = "problem")
    public void afterFailure(JoinPoint jp, Throwable problem) {
        System.out.println("after-throwing: "
            + jp.getSignature().getName() + " => " + problem.getMessage());
    }

    @After(METHODS)
    public void afterFinally(JoinPoint jp) {
        System.out.println("after-finally: "
            + jp.getSignature().getName());
    }
}
~~~

**Ý nghĩa từng phần:**

- `METHODS` là một hằng chuỗi compile-time, được sử dụng trong ba annotation, giúp bảo đảm chúng cùng nhắm đúng biểu thức.
- `@AfterReturning(... returning = "value")` bind giá trị trả về bình thường vào tham số có tên `value`.
- `@AfterThrowing(... throwing = "problem")` bind exception thoát khỏi target vào tham số `problem`; ở ví dụ dùng `Throwable` để không giới hạn một subtype exception duy nhất.
- `@After(METHODS)` không có `returning` hoặc `throwing`: nó không tự nhận thông tin hai outcome dưới dạng một binding như hai loại advice còn lại.
- `JoinPoint jp` cho phép biết method signature; ta chỉ log **tên method**, không in tất cả method arguments vì có thể chứa dữ liệu nhạy cảm.

**Tại sao đặt ba advice trong cùng aspect?** Spring Framework 6.2 document có quy tắc cho thứ tự advice **khác loại trong cùng một `@Aspect`**; trong các trường hợp mẫu, after-returning/after-throwing được quan sát **trước** after-finally. Nếu tách thành ba aspects độc lập mà không định nghĩa precedence, không nên cam kết những dòng log từ các aspect xuất hiện theo cùng thứ tự.

### 5.5. Cấu hình Spring `AppConfig.java`

~~~java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public TaskService taskService() {
        return new TaskService();
    }

    @Bean
    public LifecycleAspect lifecycleAspect() {
        return new LifecycleAspect();
    }
}
~~~

- `TaskService` được đăng ký làm **Spring bean** bằng `@Bean`.
- `LifecycleAspect` cũng là bean; Spring nhận diện `@Aspect` của nó và khai báo advice.
- `@EnableAspectJAutoProxy(proxyTargetClass = true)` bật AOP và chọn class-based proxy (CGLIB) khi cần, để ứng dụng gọi trực tiếp concrete `TaskService`.
- **Chuyển từ JDK proxy sang CGLIB không làm `this.runOk()` tự có advice.** Đường gọi nội bộ vẫn bỏ qua proxy.

### 5.6. Chương trình `Main.java`

~~~java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskService service = context.getBean(TaskService.class);

            System.out.println("proxy: " + AopUtils.isAopProxy(service));

            System.out.println("-- success --");
            System.out.println("caller: " + service.runOk());

            System.out.println("-- target throws --");
            try {
                service.runFail();
            } catch (IllegalStateException ex) {
                System.out.println("caller caught: " + ex.getMessage());
            }

            System.out.println("-- caught inside target --");
            System.out.println("caller: " + service.runRecovered());

            System.out.println("-- self invocation --");
            System.out.println("caller: " + service.callInternally());
        }
    }
}
~~~

Chạy từ thư mục chứa `pom.xml`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
~~~

### 5.7. Kết quả mong đợi

Với các file trên, **những dòng do ví dụ in ra**, theo thứ tự:

~~~text
proxy: true
-- success --
target: ok
after-returning: runOk => OK
after-finally: runOk
caller: OK
-- target throws --
target: fail
after-throwing: runFail => boom
after-finally: runFail
caller caught: boom
-- caught inside target --
target: recovered begin
target: caught temporary
after-returning: runRecovered => RECOVERED
after-finally: runRecovered
caller: RECOVERED
-- self invocation --
target: callInternally
target: ok
caller: OK
~~~

Maven hoặc framework có thể xuất thêm log riêng; bảng trên chỉ liệt kê các `System.out.println` trong chương trình minh họa.

### 5.8. Đọc kết quả

**Case 1 — `runOk()` trả về thành công:**

1. Caller gọi `service.runOk()` trên **Spring AOP proxy**.
2. Target in `target: ok`, trả về chuỗi `OK`.
3. `@AfterReturning` chạy và in kết quả vì method trả về bình thường.
4. `@After` chạy ở nhánh finally dù không có exception.
5. Caller in `caller: OK` sau khi lời gọi proxy hoàn tất.

**Case 2 — `runFail()` ném exception:**

1. Target in `target: fail` rồi ném `IllegalStateException("boom")`.
2. `@AfterReturning` **không chạy**.
3. `@AfterThrowing` chạy và nhận thông tin exception.
4. `@After` vẫn chạy vì method đã thoát qua nhánh throw.
5. Caller bắt exception ban đầu và in `caller caught: boom`. Advice không tự nuốt exception.

**Case 3 — exception bị bắt trong `runRecovered()`:**

1. Target bắt một `IllegalArgumentException` ngay trong chính method.
2. Method trả về `RECOVERED` **bình thường**.
3. Spring AOP nhìn thấy **normal return**. Vì vậy `@AfterReturning` chạy, còn `@AfterThrowing` không chạy.
4. `@After` vẫn chạy. Chỉ đọc log `@After` **không thể suy ra** liệu có exception đã được catch bên trong target.

**Case 4 — `callInternally()` và `this.runOk()`:**

- `callInternally()` không match expression `run*`.
- Trong target, `this.runOk()` chạy trực tiếp, **không qua proxy**, nên không thấy các dòng after-returning/after-finally của `runOk` ở case này.
- Điều này không có nghĩa pointcut `runOk()` sai: nó đã chạy advice khi được client gọi trực tiếp **qua proxy** ở case 1.

## 6. `@After` thật sự tương đương `finally` ở mức nào?

Cú pháp Java quen thuộc:

~~~java
try {
    operation();
} finally {
    cleanup();
}
~~~

Tương tự, after-finally advice chạy ở bước method được advice hoàn tất, **bất kể normal return hay exception exit**. Tuy vậy, có hai khác biệt lớn:

### 6.1. Nó nằm bên ngoài method, tại Spring AOP interception boundary

`@After` chỉ có tác dụng khi Spring AOP đã tạo ra advisor và invocation **đi qua proxy**. Một khối `finally` nằm **ngay trong thân method Java** chạy theo ngữ nghĩa Java dù object có thuộc Spring container hay không.

Ví dụ:

~~~java
public void runCleanupLocally() {
    try {
        doWork();
    } finally {
        localResource.close();
    }
}
~~~

Đoạn code **chỉ minh họa**, giả định các identifier `doWork` và `localResource` đã được khai báo. Java `finally` ở đây được kiểm soát trực tiếp bởi owner của tài nguyên. Ngược lại, Spring `@After` không chạy khi bạn gọi target ngoài proxy hoặc self-invocation bỏ qua proxy.

### 6.2. Advice là một lời gọi Java và bản thân cũng có thể ném exception

Nếu code trong `@After` ném exception, kết quả caller nhận được có thể bị thay đổi: một lời gọi target vốn thành công có thể chuyển thành thất bại, hoặc ngoại lệ gốc có thể bị **che khuất** bởi exception từ advice.

Ví dụ **không nên dùng trong cleanup mặc định**:

~~~java
@After("execution(* example.TaskService.run*(..))")
public void riskyCleanup() {
    throw new IllegalStateException("cleanup failure");
}
~~~

Nếu advice này chạy và tự ném exception, Spring AOP không tự hứa sẽ bảo toàn outcome gốc. Hãy thiết kế cleanup sao cho đáng tin cậy, quan sát được thất bại, và kiểm tra nghiệp vụ có được phép propagate lỗi cleanup không.

**Không nên nuốt lỗi bừa bãi.** Có những hệ thống bắt buộc phải dừng khi cleanup thất bại, nhưng quyết định đó phải được định nghĩa rõ trong hợp đồng nghiệp vụ. Đừng biến một hook ghi log/metrics thành nguồn lỗi mới ngoài ý muốn.

## 7. Dùng `JoinPoint` như thế nào trong `@After`?

Một advice có thể nhận `org.aspectj.lang.JoinPoint` để quan sát thông tin method execution:

~~~java
@After("execution(* example.TaskService.run*(..))")
public void afterCompletion(JoinPoint jp) {
    String method = jp.getSignature().getName();
    System.out.println("completed: " + method);
}
~~~

Các thông tin thường dùng:

| Cách gọi | Ý nghĩa trong Spring AOP |
| --- | --- |
| `jp.getSignature().getName()` | Tên method được match |
| `jp.getSignature().getDeclaringTypeName()` | Tên type gắn với method signature |
| `jp.getArgs()` | Mảng đối số được trình bày cho join point |
| `jp.getThis()` | Proxy object tại join point, khi API/cấu hình cho phép quan sát |
| `jp.getTarget()` | Target object phía sau proxy (có thể khác proxy) |

**Không coi `getArgs()` là dữ liệu mặc định an toàn để log.** Đối số có thể chứa mật khẩu, token, số thẻ hoặc thông tin cá nhân. Trong advice dùng chung, ưu tiên tên operation, request/correlation ID được cho phép, metric labels có cardinality thấp và các trường đã được lọc.

**`JoinPoint` không phải `ProceedingJoinPoint`:** chỉ `@Around` mới dùng `ProceedingJoinPoint.proceed()` để quyết định chuyển tiếp invocation. `@After` không gọi `proceed()`, cũng không nên can thiệp trực tiếp vào việc thực thi method từ một after-finally callback.

### 7.1. `@After` không bind return value/exception như các annotation chuyên biệt

Không có thuộc tính `returning` hoặc `throwing` trên `@After`. Nếu yêu cầu nghiệp vụ là:

- **Chỉ log dữ liệu trả về thành công:** dùng `@AfterReturning(pointcut=..., returning="value")`.
- **Chỉ xử lý exception đi ra từ target:** dùng `@AfterThrowing(pointcut=..., throwing="problem")`.
- **Luôn giải phóng trạng thái sau execution, không cần phân biệt outcome:** dùng `@After`.
- **Cần biết cả outcome lẫn thời gian thực thi trong cùng một state per-invocation:** cân nhắc `@Around` với `try/catch/finally` rõ ràng.

Đừng cố suy outcome bằng cách nhìn vào tên method hoặc bằng một field mutable dùng chung của singleton aspect; nhiều lời gọi concurrent có thể can thiệp lẫn nhau.

## 8. Thứ tự giữa `@After`, `@AfterReturning` và `@AfterThrowing`

Spring Framework mô tả quy tắc precedence khi **nhiều advice khác loại trong cùng một @Aspect** cùng áp dụng một join point. Trong Spring Framework 6.2, `@After` có **finally semantics** và về thời điểm thực thi thực tế được gọi **sau `@AfterReturning` hoặc `@AfterThrowing`** trong chính aspect ấy.

Do đó trong ví dụ mục 5:

~~~text
Trả về bình thường:
  target -> @AfterReturning -> @After -> caller

Ném exception:
  target -> @AfterThrowing -> @After -> caller bắt exception
~~~

Nhưng đừng từ sơ đồ trên suy ra một quy tắc cứng cho **mọi advice ở mọi aspect**:

- Khi advice thuộc **hai aspect khác nhau**, precedence giữa aspects phụ thuộc `@Order` hoặc `Ordered` và quy tắc vào/ra interceptor chain.
- Nếu nhiều advice **cùng loại** trên một join point trong **cùng aspect**, thứ tự không nên được suy từ thứ tự khai báo method trong file Java.
- Spring AOP còn có thể có infrastructure advisors do transaction/caching/security, vì vậy kết quả ở ứng dụng thật có thêm tầng ngoài.
- Trong câu chuyện về "finally", **thứ tự thực thi thực tế** khác với việc liệt kê mức độ precedence trong tài liệu. Nên test version/cấu hình cụ thể khi order ảnh hưởng business behavior.
- `@AfterThrowing` không phải callback chung để bắt exception do một advice `@After` hoặc `@AfterReturning` khác ném ra. Mỗi advice type có ngữ nghĩa riêng quanh join point.

### 8.1. `@Order` không làm `@After` thành success-only

Có thể dùng `@Order(10)` trên một aspect để điều chỉnh precedence so với aspect khác, nhưng bản thân `@After` vẫn chạy cho **normal và exceptional exit** của matched method invocation. `@Order` không đổi tập method được pointcut match và không biến `@After` thành `@AfterReturning`.

## 9. Cleanup phù hợp và cleanup không phù hợp

### 9.1. Những tác vụ phù hợp với `@After`

- Ghi nhận rằng một **operation đã kết thúc** mà không cần đọc return value/exception.
- Xóa một marker theo thread/correlation context **do một thành phần phối hợp có vòng đời xác định** đặt trước đó.
- Giải phóng trạng thái/handle **do aspect hoặc hạ tầng AOP tương ứng quản lý**, nếu ownership rõ ràng và có thể cleanup ở cả hai nhánh.
- Đánh dấu kết thúc một instrumentation scope khi bản thân advice không cần lưu thời điểm bắt đầu cùng invocation.
- Hỗ trợ operational log ở mức method/operation, không thay đổi nghiệp vụ.

Với các ví dụ này, cần kiểm tra việc tạo, sở hữu và giải phóng tài nguyên; Spring `@After` không tự sinh hoặc quản lý tài nguyên giúp bạn.

### 9.2. Những trường hợp không nên lạm dụng

**Không dùng `@After` để quyết định chỉ-on-success.** Nếu cleanup là hành vi chỉ được phép sau khi commit thành công, `@After` ở method boundary không chứng minh một transaction đã commit. Với `@Transactional`, thời điểm transaction interceptor commit và advice @Aspect của bạn còn phụ thuộc thứ tự advisors.

**Không dùng `@After` để retry hoặc sửa giá trị trả về.** Những cơ chế ấy cần thiết kế `@Around`/chính sách retry, không phải callback after-finally không có `proceed()`.

**Không giải phóng tài nguyên không thuộc quyền sở hữu.** Ví dụ, khóa được giữ bởi caller qua nhiều method hoặc connection thuộc transaction manager không nên bị đóng bừa bãi từ một aspect chung.

**Không giả định mọi lời gọi method sẽ kích hoạt cleanup.** Self-invocation hoặc một lời gọi đến object được tạo bằng `new` bên ngoài Spring sẽ bỏ qua Spring AOP proxy.

### 9.3. Nếu cần mở và đóng resource cho riêng mỗi invocation

Khi cần tạo một handle trước lời gọi và đảm bảo đóng **chính handle ấy** sau lời gọi, `@Around` với biến cục bộ và `try/finally` thường rõ ràng hơn:

~~~java
// Snippet minh họa bên trong một Spring-managed @Aspect; không chạy độc lập.
@Around("execution(* example.TaskService.run*(..))")
public Object withScope(ProceedingJoinPoint pjp) throws Throwable {
    ScopeHandle handle = scopeManager.open();
    try {
        return pjp.proceed();
    } finally {
        handle.close();
    }
}
~~~

Các type `ScopeHandle` và `scopeManager` ở đây là abstraction minh họa, cần ứng dụng cung cấp. `@Around` được cân nhắc vì có **biến handle riêng cho từng lời gọi**, không phải vì `@After` kém an toàn trong mọi tình huống.

Nếu resource được mở **bên trong target method**, một khối `try-with-resources` ngay tại nơi tạo resource có thể là cách đúng hơn, dễ đọc hơn và không phụ thuộc AOP proxy boundary.

## 10. Giới hạn của "luôn chạy": proxy và đường gọi

Từ "finally" cần hiểu kèm tiền đề: **một invocation đã match và thực sự vào chuỗi Spring AOP**. `@After` không phải hook toàn cục chạy mỗi khi JVM xử lý bất kỳ method cùng tên nào.

### 10.1. Self-invocation qua `this`

~~~java
public void outer() {
    this.runOk();
}
~~~

Nếu `outer()` được gọi qua proxy và không có advice riêng cho `outer()`, target bắt đầu thực thi `outer()`; sau đó `this.runOk()` vẫn gọi nội bộ target. Advice nhắm `runOk()` **không chạy** cho lời gọi nội bộ ấy.

**Đổi sang CGLIB cũng không tự sửa self-invocation.** Đây là giới hạn Spring AOP proxy-based ở cả hai chiến lược JDK và CGLIB.

Cách sửa thông dụng: tách hành vi cần được advice sang một Spring bean khác, inject bean đó và gọi qua Spring-managed proxy. Không thêm `AopContext.currentProxy()` hoặc self-injection chỉ để tránh refactor nếu không hiểu tác động tới coupling/circular dependencies.

### 10.2. Object tự tạo với `new`

~~~java
TaskService service = new TaskService();
service.runFail();
~~~

Đây là Java object ngoài context; mặc dù trong mã nguồn có một Spring `@Aspect` tương ứng, **instance này không được Spring AOP auto-proxy**. `@After` không chạy cho lời gọi trên nó.

### 10.3. Pointcut không match

Nếu advice khai báo:

~~~java
@After("execution(* example.TaskService.run*(..))")
~~~

thì `TaskService.callInternally()` không thỏa method name pattern `run*`. Không có after advice cho invocation `callInternally()` chỉ vì bean là proxy.

### 10.4. Chặn sớm ở interceptor/advice ngoài

Một `@Around` advice ở tầng ngoài có thể **trả về mà không gọi `proceed()`**. Khi đó method execution phía trong không xảy ra; advice gắn với execution đó cũng không nhất thiết được gọi. Ví dụ cache hit có thể kết thúc tại một interceptor ngoài.

Tương tự, nếu một `@Before` advice phía ngoài ném exception trước khi chuỗi gọi đến target/@After, không có bảo đảm mọi after-finally advice ở các tầng trong đều đã được vào và phải chạy. Hãy phân biệt exception **do target method ném sau khi invocation tới target** với exception phát sinh **trước khi target được gọi**.

### 10.5. JVM/process termination

Dù Java `finally` cũng có những giới hạn (ví dụ process bị dừng cưỡng bức), Spring `@After` càng không nên bị mô tả là "bất biến trong mọi hoàn cảnh". Khi JVM crash, kill -9, mất máy hay container dừng bất thường, đừng kỳ vọng một AOP advice dọn dẹp vẫn chạy.

## 11. Hệ quả khi method trả về giá trị bất đồng bộ hoặc reactive

Ngữ nghĩa after-finally của Spring AOP gắn với **method execution được proxy can thiệp**, không phải một khái niệm business “công việc đã hoàn tất vĩnh viễn”.

Ví dụ trả về CompletableFuture:

~~~java
// Snippet trong một Spring-managed target bean, không phải chương trình độc lập.
public CompletableFuture<String> runAsyncStyle() {
    return CompletableFuture.supplyAsync(() -> performWork());
}
~~~

Nếu method **trả về CompletableFuture nhanh** và công việc chạy bên trong future vẫn đang tiếp diễn, `@After` của method execution có thể chạy **trước khi future hoàn thành**. Nó quan sát **thời điểm method đã trả về object Future**, không phải một lời hứa rằng asynchronous work đã thành công/thất bại.

Tương tự, với code reactive trả về `Mono` hoặc `Flux`, việc tạo publisher và thực thi pipeline khi subscribe có thể ở hai thời điểm khác nhau. `@After` trên method tạo publisher **không mặc nhiên chạy vào thời điểm subscription hoàn tất**.

**Cách thiết kế:** Nếu cần cleanup gắn với **vòng đời future/publisher**, hãy dùng callback/completion handler hoặc operator tương ứng (ví dụ CompletableFuture.whenComplete, Reactor.doFinally khi dùng Reactor), không coi `@After` của method factory là completion signal.

Các ví dụ về future/reactive chỉ nhằm nói rõ giới hạn của **Spring AOP advice timing**, không phải bài học chi tiết về concurrency hay reactive programming.

## 12. Spring Boot và cách bật advice

Trong Spring Framework thuần, cách bật quen thuộc là:

~~~java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}
~~~

Đây là snippet minh họa, cần các imports Spring và một ApplicationContext phù hợp. Class `@Aspect` vẫn cần được đăng ký thành **Spring bean** để advisor được khám phá.

Trong ứng dụng Spring Boot:

- **Boot 3.x** thường sử dụng `spring-boot-starter-aop`.
- **Boot 4.x** sử dụng `spring-boot-starter-aspectj` theo tên starter mới.
- Nếu classpath và Spring Boot AOP auto-configuration hoạt động, không nhất thiết phải viết `@EnableAspectJAutoProxy` thủ công.
- `@Aspect @Component` phải nằm trong phạm vi component scanning; hoặc có một method `@Bean` đăng ký aspect.
- `spring.aop.proxy-target-class` điều chỉnh JDK/CGLIB strategy, **không đổi ngữ nghĩa finally của `@After`**.
- Nếu dự án tắt AOP auto-configuration hoặc loại dependency cần thiết, annotation `@After` trong mã nguồn không tự làm lời gọi bị can thiệp.

Điều quan trọng: framework có hỗ trợ AOP **không đồng nghĩa** mọi bean có proxy hay mọi method có `@After` advice. Cần advisor match đúng bean và invocation đi qua proxy.

## 13. Chẩn đoán `@After` không chạy hoặc chạy bất ngờ

| Hiện tượng | Khả năng nguyên nhân | Kiểm tra/sửa |
| --- | --- | --- |
| Method trả về thành công nhưng không thấy `@After` | Aspect không phải bean hoặc AOP chưa bật | Kiểm tra bean registration, @ComponentScan, @EnableAspectJAutoProxy/Boot auto-config |
| Method ném exception nhưng không thấy `@After` | Lời gọi không qua proxy, không match pointcut hoặc target chưa được gọi | Kiểm tra Spring bean reference, signature, interceptor chain |
| Gọi trực tiếp qua Spring bean có log; gọi bằng `this.method()` không có | **Self-invocation** | Tách method cần advice sang bean khác và gọi qua DI |
| Chỉ `@AfterReturning` chạy khi thành công | Đây là hành vi đúng | Dùng `@After` nếu cần cả hai outcome |
| `@AfterThrowing` không chạy khi exception được target bắt | Method kết thúc bình thường, không có throwable thoát ra | Phân biệt exception nội bộ với exception thoát method |
| `@After` in log sau exception nhưng caller vẫn thấy exception | Đây là hành vi đúng | @After không tự nuốt exception; caller xử lý tiếp |
| `@After` ném lỗi và caller mất kết quả thành công | Exception phát sinh trong chính advice | Thiết kế cleanup/logging không làm thay đổi hợp đồng ngoài ý muốn |
| `@After` có log cho method ngoài phạm vi | Pointcut quá rộng | Thu hẹp package, class, method, bean hoặc annotation |
| Nhìn `AopUtils.isAopProxy(bean) == true` nhưng advice không chạy | Proxy có thể do advisor khác tạo hoặc method không match | Kiểm tra advisor và pointcut cụ thể |
| Log `@After` đến trước/ sau transaction commit khác dự đoán | Thứ tự infrastructure advisors và @Aspect advice | Kiểm tra @Order/Ordered và integration test transaction thật |
| `@After` chạy trước khi background job kết thúc | Method trả future/publisher đã kết thúc sớm | Theo dõi completion của async/reactive work thay vì method return |
| Có hai dòng `@After` cho một lời gọi | Hai aspects/advisors cùng match, trùng bean registration hoặc nested proxies | Kiểm tra danh sách aspect beans và advisor chain |

### Checklist kiểm tra nhanh

1. **Có aspect bean chưa?** `ApplicationContext.getBeanNamesForType(LifecycleAspect.class)`.
2. **AOP đã bật?** Spring Framework: `@EnableAspectJAutoProxy`; Spring Boot: auto-configuration/starter theo version.
3. **Có target bean/proxy thực sự?** `AopUtils.isAopProxy(context.getBean(TaskService.class))`; kết quả `true` không đủ để khẳng định method match.
4. **Pointcut match method?** Kiểm tra package, method name pattern, overloaded method và visibility.
5. **Caller đang giữ proxy?** Không dùng object tự `new`, tránh lấy underlying target rồi gọi trực tiếp.
6. **Có self-invocation?** Xem những lời gọi `this.runX()` trong target.
7. **Có around advice short-circuit?** Kiểm tra `proceed()` có thực sự được gọi hay không.
8. **Advice tự ném exception?** Đừng nhầm exception của advice với exception của target.
9. **Có nhiều aspects/infrastructure advisors?** Điều tra thứ tự trước khi cam kết stdout.
10. **Có tác vụ async?** Method-return time không phải completion time của job.

## 14. Tự kiểm tra

**Câu 1.** `@After("execution(* TaskService.run*(..))")` có chạy khi `runFail()` ném `IllegalStateException` ra ngoài không?

**Đáp án:** Có, nếu invocation match và đi qua Spring AOP proxy như trong ví dụ. After-finally advice chạy cho exceptional exit của method.

**Câu 2.** `runRecovered()` bắt lỗi nội bộ rồi trả về `"RECOVERED"`. `@AfterThrowing` có chạy không? `@After` thì sao?

**Đáp án:** `@AfterThrowing` không chạy vì method không ném exception ra ngoài. `@After` chạy vì method execution đã kết thúc bình thường.

**Câu 3.** Có thể dùng `@After` với tham số `returning="value"` để lấy return value không?

**Đáp án:** Không. Thuộc tính `returning` là của `@AfterReturning`. Với `@After`, dùng `JoinPoint` để quan sát signature/arguments nhưng không tự nhận return value binding như success advice.

**Câu 4.** Method `runOk()` được gọi qua `this.runOk()` bên trong target. `@After` chạy không?

**Đáp án:** Không cho lời gọi nội bộ đó vì nó bỏ qua Spring AOP proxy. Gọi trực tiếp `service.runOk()` trên Spring proxy ở một caller khác lại có thể kích hoạt advice.

**Câu 5.** Một `@After` advice ném `IllegalStateException` sau khi target đã thành công. Caller còn chắc chắn nhận kết quả thành công không?

**Đáp án:** Không. Exception từ advice có thể biến lời gọi ngoài thành thất bại hoặc che khuất exception gốc nếu có. Dọn dẹp phải có chính sách xử lý lỗi rõ ràng.

**Câu 6.** Cần tính thời gian chạy một method bằng biến start riêng cho từng invocation. Nên dùng hai field mutable trong singleton aspect và `@Before`/`@After` không?

**Đáp án:** Không nên, vì nhiều invocation concurrent có thể ghi đè state. Dùng `@Around` với biến cục bộ `start` và `try/finally` hoặc instrumentation scope được quản lý riêng cho từng invocation.

**Câu 7.** Method trả về `CompletableFuture`, Future chưa xong nhưng `@After` đã chạy. Có sai không?

**Đáp án:** Không. Method execution có thể đã trả Future; Spring @After ở boundary method không tự chờ Future hoàn tất.

**Câu 8.** Có ba advice @After, @AfterReturning, @AfterThrowing trong cùng aspect. Có nên dựa vào thứ tự khai báo các method trong source file không?

**Đáp án:** Không. Hãy dựa trên quy tắc Spring cho advice type và chạy integration test. Với Spring 6.2, after-finally advice thực tế chạy sau after-returning hoặc after-throwing cùng aspect ở những invocation tương ứng.

## 15. Tài liệu Spring chính thức

- [Spring Framework 6.2 — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): phần "After (Finally) Advice", phân biệt after-returning/after-throwing, `JoinPoint` và thứ tự advice.
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): phạm vi Spring AOP proxy và các loại advice.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): bật `@EnableAspectJAutoProxy`, yêu cầu aspect bean và dependency AspectJ.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB proxies, self-invocation và ranh giới interception.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): điều kiện method execution và expression.
- [Spring Framework — Programmatic Advice API](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html): hợp đồng advice và lưu ý khi advice tự ném exception.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-005">Phân biệt đường thành công, đường ngoại lệ và kết thúc phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt đường thành công, đường ngoại lệ và kết thúc phương thức trong Spring AOP

## 1. Cùng một Spring method nhưng có ba kết cục cần quan sát

Một Spring service có thể **trả kết quả bình thường**, **ném ngoại lệ ra cho caller** hoặc **kết thúc theo một trong hai cách trên và cần dọn dẹp/ghi nhận việc đã kết thúc**. Khi dùng Spring Aspect để log, đo metric, audit hoặc theo dõi lỗi, cần chọn đúng loại advice ứng với kết cục mà hệ thống thật sự muốn biết.

Ví dụ một method `checkout(String id)` có các đường:

```text
Client
  |
  v
Spring AOP proxy
  |
  +-- @Before: khi lời gọi sắp được thực thi
  |
  v
Target checkout(...)
  |
  +-- return "OK" ---------> đường hoàn thành bình thường
  |                           +-- @AfterReturning
  |                           +-- @After (finally)
  |
  +-- throw Exception ------> đường kết thúc bằng ngoại lệ
                              +-- @AfterThrowing
                              +-- @After (finally)
```

`@After` không phải dạng viết khác của `@AfterReturning`: nó có semantics gần `finally` của Java, chạy khi **method execution phù hợp kết thúc**, bất kể hoàn thành bình thường hay có ngoại lệ thoát ra. `@AfterReturning` chỉ phản ánh **normal completion**; `@AfterThrowing` chỉ phản ánh **exceptional completion**.

Trong Spring AOP, các advice này được nối thành **interceptor chain trên Spring-managed proxy**. Một điều kiện tối quan trọng: **pointcut có khớp mà lời gọi không đi qua proxy thì advice vẫn không chạy**. Lời gọi `this.method()` giữa hai method của cùng target, constructor hoặc đối tượng được tạo bằng `new` không tự nhận Spring AOP advice.

**Phạm vi bài:** Cơ chế advice dựa trên sự kiện hoàn tất **method execution trong Spring Framework / Spring `@Aspect`**. Không mở rộng sang AspectJ weaving của constructor hoặc field join points ngoài Spring.

### Mục tiêu học tập

Sau bài, bạn có thể:

1. Phân biệt chính xác `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After` và `@Around`.
2. Hiểu “thành công” trong `@AfterReturning` là **không ném ngoại lệ**, không đồng nghĩa kết quả kinh doanh đã đạt mục tiêu.
3. Hiểu một exception **bị bắt và xử lý bên trong target** không phải “exceptional completion” của proxy invocation.
4. Bind giá trị trả về bằng `returning` và bind ngoại lệ bằng `throwing`, tránh vô tình thu hẹp type.
5. Chạy ví dụ Java 17/Spring Framework với kết quả bình thường, `null`, checked exception và unchecked exception.
6. Chẩn đoán tại sao cùng một invocation có `@After` nhưng chỉ có một trong `@AfterReturning` hoặc `@AfterThrowing`; khi nào `@Around` thay đổi kết cục.

## 2. Bảng ngữ nghĩa các advice trong Spring

| Advice | Khi nào chạy? | Có thấy normal return? | Có thấy exception? | Vai trò chính |
| --- | --- | --- | --- | --- |
| `@Before` | Trước method execution khớp, khi proxy chain đi tới advice | Chưa biết kết quả | Chưa biết kết quả | Kiểm tra tiền điều kiện, bắt đầu log/metric đơn giản |
| `@AfterReturning` | Method execution khớp **trả về bình thường** | Có, có thể bind giá trị | Không chạy khi exception thoát ra | Ghi nhận hoàn thành, đọc kết quả, metric success |
| `@AfterThrowing` | Method execution khớp **thoát ra bằng ngoại lệ** | Không chạy khi trả bình thường | Có, có thể bind exception | Ghi nhận failure/exception, cảnh báo |
| `@After` | Khi method execution khớp kết thúc theo một trong hai đường | Có chạy | Có chạy | Cleanup, ghi nhận “completed”, giải phóng trạng thái |
| `@Around` | Bao quanh method invocation và kiểm soát có gọi `proceed()` hay không | Có thể quan sát/chỉnh kết quả | Có thể catch/rethrow/chuyển ngoại lệ | Tracing duration, thử lại có kiểm soát, fallback có chủ đích |

Bảng diễn đạt outcome của **method execution đã đi qua những advisor phù hợp**; nó không đảm bảo callback được chạy trong các tình huống hủy process đột ngột, lỗi VM nghiêm trọng hoặc khi một advisor *khác* short-circuit trước khi chain đi đến advice tương ứng.

### Một cách nhớ ngắn

```text
Target return normally?
    YES -> @AfterReturning; @After
    NO  -> nếu exception thoát ra -> @AfterThrowing; @After

@After: không chọn một đường; quan sát kết thúc chung.
@Around: có thể tự tạo đường đi bằng proceed / return / throw.
```

Trong cùng một invocation đã chạy tới các advice này, `@AfterReturning` và `@AfterThrowing` đại diện hai điều kiện kết thúc khác nhau; **không phải hai hook cần chạy đồng thời**.

## 3. @AfterReturning: hoàn thành bình thường, không nhất thiết “nghiệp vụ thành công”

Định nghĩa:

```java
@Aspect
public class CompletionAspect {

    @AfterReturning(
        pointcut = "execution(* com.example.app.service..*.*(..))"
    )
    public void completedNormally() {
        // Method execution đã return bình thường.
    }
}
```

### 3.1. Ba kết quả vẫn là normal return

Ví dụ method có thể:

```java
public String evaluate(String mode) {
    if ("ok".equals(mode)) {
        return "OK";
    }
    if ("missing".equals(mode)) {
        return null;
    }
    return "DECLINED";
}
```

Cả ba trường hợp trên đều **return bình thường**:
- `"OK"` là thành công theo hợp đồng nghiệp vụ;
- `null` có thể là “không có dữ liệu”, nhưng vẫn return bình thường;
- `"DECLINED"` có thể là “từ chối thanh toán”, nhưng vẫn không phải exception.

`@AfterReturning` chạy cho cả ba khi invocation khớp và đi qua proxy. Vì thế đặt tên metric là `technical_normal_completion` thường chính xác hơn coi mọi lần `@AfterReturning` đều là `business_success`.

Nếu một method `void` kết thúc mà không ném exception, đó cũng là **normal completion**; advice `@AfterReturning` không có ràng buộc `returning` vẫn có thể chạy.

### 3.2. Bind giá trị trả về với returning

```java
@AfterReturning(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    returning = "result",
    argNames = "result"
)
public void captureResult(Object result) {
    System.out.println("return=" + result);
}
```

Đây là snippet minh họa trong một lớp `@Aspect`; cần import và cấu hình thật để chạy. Tên `result` trong `returning` phải khớp tên tham số advice. `argNames` làm tên binding tường minh khi biên dịch Java không bảo toàn parameter names theo kỳ vọng.

Vì tham số advice là `Object`, nó chấp nhận đa số giá trị trả về; nếu thay thành `String`, việc bind có thể **thu hẹp phạm vi advice** tới những kết quả/method phù hợp với kiểu `String`. Đừng vô tình ràng buộc kiểu chỉ để in một giá trị.

**Giới hạn:** `@AfterReturning` có thể quan sát hoặc tương tác với object kết quả, nhưng **không được dùng để thay reference trả về** cho caller bằng cách `return` giá trị khác từ advice. Nếu yêu cầu là biến đổi kết quả trả ra cho caller, dùng `@Around` với `ProceedingJoinPoint.proceed()` và trả lại giá trị mong muốn.

## 4. @AfterThrowing: ngoại lệ phải thoát khỏi join point

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    throwing = "error",
    argNames = "error"
)
public void captureFailure(Throwable error) {
    System.out.println("exception=" +
        error.getClass().getSimpleName());
}
```

Trong Spring AOP, `@AfterThrowing` chạy khi method execution khớp **kết thúc bằng exception được ném ra ngoài**. `throwing` có thể bind exception object cho advice.

### 4.1. Checked và unchecked exceptions

Ví dụ:

```java
public String load(String mode) throws IOException {
    if ("checked".equals(mode)) {
        throw new IOException("offline");
    }
    if ("unchecked".equals(mode)) {
        throw new IllegalStateException("broken");
    }
    return "OK";
}
```

Nếu các exception thực sự thoát khỏi method execution và lời gọi đi qua proxy, `@AfterThrowing` có `Throwable error` có thể quan sát **cả `IOException` và `IllegalStateException`**. Exception không nhất thiết phải là `RuntimeException`.

Ngược lại, nếu advice khai báo:

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.service..*.*(..))",
    throwing = "error",
    argNames = "error"
)
public void captureIllegalState(IllegalStateException error) {
    // Chỉ khớp kiểu ngoại lệ thích hợp.
}
```

thì ràng buộc `IllegalStateException` làm advice không chạy cho `IOException`. Đây là **lọc theo loại exception**, không có nghĩa Spring chỉ hỗ trợ unchecked exceptions.

### 4.2. Exception được target tự bắt thì không phải exceptional completion

```java
public String loadSafely() {
    try {
        return fetchRemote();
    } catch (IOException ex) {
        return "FALLBACK";
    }
}
```

Đây là snippet giả định `fetchRemote()` có thể ném `IOException`. Nếu target bắt exception bên trong và return `"FALLBACK"`, từ góc nhìn method execution do proxy quan sát, kết cục là **normal return**:
- `@AfterReturning` có thể chạy;
- `@AfterThrowing` trên `loadSafely` không chạy vì `IOException` không thoát khỏi method;
- `@After` vẫn chạy.

Nếu nghiệp vụ cần quan sát mỗi exception được **bắt nội bộ**, Spring AOP proxy không tự chặn từng câu lệnh `throw`/`catch` bên trong method. Hãy log tại catch block hoặc thiết kế instrumentation thích hợp.

### 4.3. @AfterThrowing không phải bộ xử lý ngoại lệ tổng quát

Nó chỉ nhận những exception thuộc lời gọi method execution tương ứng. Tài liệu Spring nêu rõ `@AfterThrowing` không phải callback tổng quát cho exception do advice `@After`/`@AfterReturning` khác ném ra. Cũng không thể dùng kiểu `void` advice này để **swallow exception** và trả kết quả mới như một fallback thông thường.

Muốn thay đổi exception và kết quả do caller nhìn thấy, dùng `@Around` khi thật sự cần, cân nhắc tác động lên transaction và các advice khác.

## 5. @After: kết thúc bình thường hay ngoại lệ đều cần đi qua

```java
@After("execution(* com.example.app.service..*.*(..))")
public void afterAnyCompletion() {
    System.out.println("completed");
}
```

`@After` trong `@AspectJ` tương đương ý tưởng **finally**:

```java
try {
    // target method execution
} finally {
    // cleanup
}
```

Nó thích hợp cho việc:
- Gỡ trạng thái local của tracing;
- Ghi metric tổng lượt kết thúc;
- Giải phóng tài nguyên thuộc phạm vi advice, nếu ownership rõ;
- Bỏ dấu hiệu “đang xử lý” được thiết lập trước đó.

### 5.1. Không phải nơi kết luận method “success”

Nếu increment counter `completed` trong `@After`, counter ấy có thể bao gồm **cả method trả bình thường và method ném exception**. Không đặt tên là `successfulRequests` nếu giá trị đến từ `@After`.

Một phép kiểm tra logic:

```text
totalCompleted ≈ normalReturns + thrownCompletions
```

Đẳng thức có ý nghĩa khi ba counter quan sát **cùng tập method invocation**, tất cả advice đều được áp dụng, không có short-circuit/lỗi trong advice và không có code thay đổi outcome ngoài phạm vi quan sát. Trong một proxy chain phức tạp hoặc hệ thống concurrent, phải tính toán theo design thực tế.

### 5.2. @After và finally của Java không phải bảo đảm tuyệt đối

`@After` chạy khi execution đến advice và hoàn tất qua những đường return/throw. Nếu JVM bị `System.exit`, process bị kill, thread chết bởi sự kiện nghiêm trọng hoặc một advisor đứng **ngoài** chain đã bỏ qua việc proceed, callback có thể không được gọi. Tương tự, nếu không có AOP proxy hoặc pointcut không khớp, không có hook để chạy.

Dùng `try/finally` ngay trong code sở hữu tài nguyên nếu cần bảo đảm giải phóng trong phạm vi method nội bộ, không giả định `@After` thay thế mọi quy tắc quản lý tài nguyên.

## 6. Thứ tự advice: điều dễ bị hiểu sai

Trong cùng một `@Aspect`, Spring áp dụng precedence theo loại advice. Tài liệu Spring Framework mô tả thứ tự loại advice từ mức precedence cao đến thấp: `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`. **Nhưng `@After` thực tế được gọi *sau* `@AfterReturning` hoặc `@AfterThrowing` của cùng aspect**, phù hợp semantics “finally”.

Không nên đọc bảng precedence rồi kết luận rằng log `@After` luôn in **trước** log `@AfterReturning`: đó là nhầm giữa thứ tự interceptor chain và thứ tự callback khi unwind.

Nếu advice nằm ở **hai aspect khác nhau**, khi cả hai cùng match một join point, thứ tự không được đảm bảo nếu không cấu hình `@Order`/`Ordered`. Aspect có order value thấp hơn thường có precedence cao hơn: đi vào trước và đi ra sau.

### Minh họa đường normal completion

```text
proxy.method()
    |
    +-- @Before            [đường đi vào]
    |
    +-- target returns     [normal completion]
    |
    +-- @AfterReturning    [nhánh normal]
    |
    +-- @After             [finally, cùng aspect]
    |
    +-- caller sees return
```

### Minh họa đường exceptional completion

```text
proxy.method()
    |
    +-- @Before
    |
    +-- target throws
    |
    +-- @AfterThrowing     [nhánh exception]
    |
    +-- @After             [finally, cùng aspect]
    |
    +-- caller receives exception
```

Hai flow trên giúp phân biệt **ý nghĩa các sự kiện** trong Spring AOP; chúng không thay thế việc inspect advisor ordering khi có nhiều aspect, transaction, cache, retry hoặc `@Around` chuyên biệt.

## 7. Ví dụ Spring Framework chạy được: bốn đường kết thúc

Ví dụ sau dùng:
- **Java 17+**;
- **Spring Framework 6.1.14**;
- **AspectJ Weaver 1.9.22.1**;
- **Gradle**;
- Một `@Aspect` ghi các bộ đếm advice.

Ta chủ động **không in log từ bên trong advice** mà tăng counter và in snapshot sau mỗi method invocation. Cách này chứng minh đủ ba loại outcome mà không phụ thuộc vào chi tiết thứ tự in log giữa các advice.

Bốn input:
1. `"ok"` → return `"OK"`;
2. `"empty"` → return `null`;
3. `"unchecked"` → throw `IllegalStateException`;
4. `"checked"` → throw `IOException`.

### 7.1. Cấu trúc thư mục

```text
spring-aop-outcomes/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/outcomes/
    ├── OutcomeService.java
    ├── OutcomeAspect.java
    ├── AppConfig.java
    └── DemoApplication.java
```

### 7.2. Gradle configuration

Tệp `settings.gradle`:

```groovy
rootProject.name = 'spring-aop-outcomes'
```

Tệp `build.gradle`:

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
    mainClass = 'demo.outcomes.DemoApplication'
}
```

`spring-context` cung cấp IoC/configuration; `spring-aop` chứa proxy infrastructure; `aspectjweaver` hỗ trợ parser cho pointcut trong `@Aspect`.

### 7.3. Target method với bốn kết cục

Tệp `src/main/java/demo/outcomes/OutcomeService.java`:

```java
package demo.outcomes;

import java.io.IOException;

public class OutcomeService {

    public String evaluate(String mode) throws IOException {
        System.out.println("target:" + mode);

        if ("ok".equals(mode)) {
            return "OK";
        }
        if ("empty".equals(mode)) {
            return null;
        }
        if ("unchecked".equals(mode)) {
            throw new IllegalStateException("broken");
        }
        if ("checked".equals(mode)) {
            throw new IOException("offline");
        }
        return "UNKNOWN";
    }
}
```

`OutcomeService.evaluate` là public non-final method, có thể được CGLIB proxy chặn. Method khai báo `throws IOException` để checked exception **được phép thoát ra**; `IllegalStateException` là unchecked.

`return null` vẫn là normal completion. Code ngoài `evaluate` là caller chịu trách nhiệm phân biệt exception và return value.

### 7.4. Aspect đếm before, after-returning, after-throwing, after

Tệp `src/main/java/demo/outcomes/OutcomeAspect.java`:

```java
package demo.outcomes;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class OutcomeAspect {
    private final AtomicInteger before = new AtomicInteger();
    private final AtomicInteger normal = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger finished = new AtomicInteger();

    @Before("execution(public * demo.outcomes.OutcomeService.evaluate(..))")
    public void onEnter() {
        before.incrementAndGet();
    }

    @AfterReturning(
        pointcut = "execution(public * demo.outcomes.OutcomeService.evaluate(..))",
        returning = "result",
        argNames = "result"
    )
    public void onNormalReturn(Object result) {
        normal.incrementAndGet();
        // result có thể là null; không mặc định kết quả == thành công nghiệp vụ.
    }

    @AfterThrowing(
        pointcut = "execution(public * demo.outcomes.OutcomeService.evaluate(..))",
        throwing = "error",
        argNames = "error"
    )
    public void onException(Throwable error) {
        failed.incrementAndGet();
        // error có thể là checked hoặc unchecked exception.
    }

    @After("execution(public * demo.outcomes.OutcomeService.evaluate(..))")
    public void onAnyCompletion() {
        finished.incrementAndGet();
    }

    public String counts() {
        return "before=" + before.get()
            + " returned=" + normal.get()
            + " thrown=" + failed.get()
            + " after=" + finished.get();
    }
}
```

Giải thích điểm quyết định:
- `onEnter()` được chạy khi proxy đưa invocation qua `@Before`.
- `onNormalReturn(Object result)` dùng `Object` để không tự ràng buộc quá hẹp vào một return class cụ thể; `null` vẫn normal.
- `onException(Throwable error)` dùng `Throwable` để bao cả `IOException` và `IllegalStateException`. Nếu đổi thành `IllegalStateException`, bộ đếm không còn đại diện mọi exception.
- `onAnyCompletion()` đếm tất cả lời gọi đã kết thúc qua advice `@After`.
- Các counter là `AtomicInteger` để tránh lỗi tăng số khi nhiều thread sử dụng cùng singleton aspect; demo gọi tuần tự để stdout xác định.

Các method advice **không trả kết quả thay cho target**, cũng không swallow exception. Chúng chỉ đo lường sự kiện.

### 7.5. Đăng ký bean và bật Spring AOP

Tệp `src/main/java/demo/outcomes/AppConfig.java`:

```java
package demo.outcomes;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public OutcomeService outcomeService() {
        return new OutcomeService();
    }

    @Bean
    public OutcomeAspect outcomeAspect() {
        return new OutcomeAspect();
    }
}
```

`@Aspect` **không tự** đăng ký Spring bean. Trong ví dụ, `@Bean` cung cấp aspect instance; `@EnableAspectJAutoProxy` kích hoạt hạ tầng phân tích advisor. `proxyTargetClass=true` chọn class-based proxy để lấy service theo `OutcomeService.class`.

### 7.6. Main gọi bốn tình huống qua proxy

Tệp `src/main/java/demo/outcomes/DemoApplication.java`:

```java
package demo.outcomes;

import java.io.IOException;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            OutcomeService service = context.getBean(OutcomeService.class);
            OutcomeAspect aspect = context.getBean(OutcomeAspect.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));

            invoke(service, aspect, "ok");
            invoke(service, aspect, "empty");
            invoke(service, aspect, "unchecked");
            invoke(service, aspect, "checked");
        }
    }

    private static void invoke(
            OutcomeService service, OutcomeAspect aspect, String mode) {

        try {
            String result = service.evaluate(mode);
            System.out.println("caller:" + mode + "=" + result);
        } catch (IOException | IllegalStateException ex) {
            System.out.println(
                "caller:" + mode + " threw "
                + ex.getClass().getSimpleName()
                + ":" + ex.getMessage()
            );
        }
        System.out.println("counts " + aspect.counts());
    }
}
```

Lệnh chạy từ thư mục `spring-aop-outcomes`:

```bash
gradle run
```

Nếu project có Gradle Wrapper, có thể dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

### 7.7. Stdout kỳ vọng

Những dòng dưới đây là **stdout từ Java code**, không tính Gradle logs:

```text
proxy=true
target:ok
caller:ok=OK
counts before=1 returned=1 thrown=0 after=1
target:empty
caller:empty=null
counts before=2 returned=2 thrown=0 after=2
target:unchecked
caller:unchecked threw IllegalStateException:broken
counts before=3 returned=2 thrown=1 after=3
target:checked
caller:checked threw IOException:offline
counts before=4 returned=2 thrown=2 after=4
```

Kết quả được hiểu như sau:

| Sau input | Tổng `@Before` | Tổng `@AfterReturning` | Tổng `@AfterThrowing` | Tổng `@After` |
| --- | ---: | ---: | ---: | ---: |
| `ok` | 1 | 1 | 0 | 1 |
| `empty` | 2 | 2 | 0 | 2 |
| `unchecked` | 3 | 2 | 1 | 3 |
| `checked` | 4 | 2 | 2 | 4 |

- `empty` vẫn tăng `returned` vì `null` không phải exception.
- `unchecked` và `checked` đều tăng `thrown` vì advice bind `Throwable`.
- `after` tăng ở cả bốn lượt vì mỗi invocation kết thúc theo một trong hai đường.
- Caller chỉ in `caller:mode=...` trên normal completion; với exception nó đi vào `catch`. Aspect không chặn hoặc thay đổi exception.
- `proxy=true` xác nhận lời gọi từ `main` đi qua Spring-managed proxy; nếu `new OutcomeService()` bên ngoài Spring, các counter sẽ không tăng.

Đây là phép quan sát bằng số đếm để tránh hiểu lầm về precedence của advice khi đọc những log in liên tiếp.

## 8. @Around: loại advice có thể thay đổi đường kết thúc

`@Around` có quyền rộng hơn bốn advice còn lại vì nó kiểm soát `ProceedingJoinPoint.proceed()`.

Một `@Around` dùng để đo thời gian và **giữ nguyên** outcome:

```java
@Around("execution(* com.example.app.service..*.*(..))")
public Object measure(ProceedingJoinPoint pjp) throws Throwable {
    long start = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - start;
        System.out.println("durationNanos=" + elapsed);
    }
}
```

Đây là snippet minh họa trong lớp `@Aspect`, cần import `@Around`/`ProceedingJoinPoint` và có Spring bean/service khớp. `proceed()` được gọi **đúng một lần**; nếu target return thì value được trả về cho caller, nếu ném exception thì exception vẫn đi ra ngoài sau `finally`.

### 8.1. Return kết quả khác

```java
@Around("execution(* com.example.app.service..*.load*(..))")
public Object fallback(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException ex) {
        return "FALLBACK";
    }
}
```

Đây là snippet **cố tình biến đổi outcome**: đối với caller của proxy, trường hợp `IllegalStateException` do inner chain/target ném ra đã được catch và chuyển thành **normal return**. Điều này có thể thay đổi cách những advice **nằm ngoài** `@Around` phân loại lần gọi. Những advice **nằm bên trong** đã nhìn thấy exception từ target trước khi `@Around` catch có thể vẫn ghi nhận failure.

Không đưa snippet `fallback` vào project chạy ở mục 7 trừ khi sửa cả kiểu method, cấu hình aspect và expected stdout tương ứng. Nó minh họa một lựa chọn chính sách khác, không phải cách dùng mặc định.

### 8.2. Không gọi proceed()

```java
@Around("execution(* com.example.app.service..*.load*(..))")
public Object shortCircuit(ProceedingJoinPoint pjp) {
    return "CACHED";
}
```

Nếu advice này thực sự được áp dụng và **không gọi `proceed()`**, method target phía trong không chạy. Những advisor ở phía trong cũng có thể không chạy. Ví dụ, logging/transaction/caching đứng ở những vị trí khác nhau trong interceptor chain sẽ quan sát outcome khác nhau.

**Kết luận kỹ thuật:** “Target có return/throw gì” và “caller của proxy thấy return/throw gì” không phải luôn giống nhau khi có `@Around` hoặc interceptor có chức năng short-circuit. Phải chỉ rõ **ràng buộc quan sát ở vị trí nào trong chain**.

### 8.3. Nên ưu tiên advice ít quyền nhất

Nếu chỉ cần đếm normal return, dùng `@AfterReturning`. Nếu chỉ cần log exception thoát ra, dùng `@AfterThrowing`. Nếu chỉ cần cleanup, dùng `@After`. Chỉ dùng `@Around` khi cần truy cập cả trước và sau **trong một scope** hoặc cần điều khiển có tiếp tục chain hay không; mỗi lần quên `proceed()` có thể làm mất việc chạy target.

## 9. Tác động của Spring transaction, cache và retry

Khi một service có nhiều advisor, outcome phụ thuộc thứ tự interceptor.

### 9.1. Transaction

Nếu method `@Transactional` bị exception và exception thoát ra ngoài transaction interceptor, quy tắc rollback của Spring transaction có thể áp dụng theo **loại exception và cấu hình rollback**. Mặc định rollback thường xảy ra với `RuntimeException`/`Error`, không mặc định rollback với checked exception nếu không có `rollbackFor` phù hợp.

**Không suy ra** rằng cứ `@AfterThrowing` chạy thì database transaction chắc chắn rollback: hai cơ chế có rule khác nhau. Nếu một `@Around` catch exception và trả fallback **trước** khi transaction interceptor quan sát nó, transaction có thể xem invocation là bình thường tùy advisor ordering. Đây là lý do không swallow exception tùy tiện trong tracing aspect.

### 9.2. Caching

Một cache interceptor đứng ngoài advice có thể trả cache hit mà không chạy target và những advice phía trong. Khi đó:
- Bạn có thể không thấy `@AfterReturning` của inner aspect cho cache hit, vì advisor không được đi tới.
- Nếu advice đứng ngoài caching, nó có thể thấy normal return từ cache.
- Cùng một method signature và cùng một target bean không đảm bảo các advice có cùng số lượt chạy khi các advisor khác short-circuit.

### 9.3. Retry

Với retry interceptor hoặc một `@Around` cố ý gọi `proceed()` nhiều lần, một lời gọi ở caller có thể tương ứng **nhiều lần thử target**. Advice ở ngoài hoặc trong retry sẽ quan sát “một lần cuối cùng thành công” hay “nhiều lần throw trước đó” khác nhau.

Để metric có nghĩa, phải định nghĩa rõ:
- `attempt_failure`: lần thử target bị exception;
- `request_failure`: lời gọi cuối cùng ra ngoài proxy vẫn thất bại;
- `request_completed`: lời gọi hoàn tất bất kể outcome;
- `business_rejected`: target trả kết quả từ chối nhưng không ném exception.

Spring `@AfterReturning`/`@AfterThrowing` tự nó không quyết định metric thuộc lớp nào nếu bạn chưa xác định advisor order.

## 10. Ma trận phân loại outcome của cùng một Spring method

Phân biệt normal completion, exceptional completion và total completion bằng ba counter cùng áp dụng lên một tập invocation, cùng proxy boundary và cùng pointcut. Các giá trị dưới đây là một lượt lời gọi đã match đủ advice:

| Target outcome | Normal return | Throwing | Finally |
| --- | ---: | ---: | ---: |
| Trả chuỗi OK | 1 | 0 | 1 |
| Trả null | 1 | 0 | 1 |
| Trả DECLINED | 1 | 0 | 1 |
| Catch IOException rồi trả fallback | 1 | 0 | 1 |
| Ném IllegalStateException ra ngoài | 0 | 1 | 1 |
| Ném IOException ra ngoài | 0 | 1 | 1 |

Return DECLINED là business refusal nhưng vẫn là normal method completion. Nếu advice @AfterThrowing chỉ bind RuntimeException thì riêng nhánh IOException không được nó đếm; nếu @AfterReturning chỉ bind String, method void và return type khác có thể bị loại.

## 11. Khi nào công thức completed = normal + throwing sai?

Công thức chỉ hữu ích khi ba advice quan sát cùng một invocation boundary, không có short-circuit hay advice ném lỗi, và hai advice phân loại không bị thu hẹp bởi type binding khác nhau. Nếu có transaction, cache, retry hoặc @Around, kiểm tra thứ tự interceptor: một fallback trong around advice có thể chuyển exception bên trong thành normal return ở tầng ngoài.

Quy trình kiểm chứng: xác nhận outcome thực của target; kiểm tra proxy và pointcut; kiểm tra tên/kiểu returning hoặc throwing; sau cùng đối chiếu metric business với method completion. Đừng dùng normal return làm bằng chứng transaction đã commit hoặc Future đã hoàn tất.

## 12. Checklist khi thiết kế Spring Aspect đo outcome

- [ ] Chỉ dùng `@AfterReturning` để ghi nhận **normal completion**, không coi mọi giá trị trả về là business success.
- [ ] Dùng `@AfterThrowing` cho exception thoát khỏi method execution; lựa chọn `Throwable` hay subtype có chủ đích.
- [ ] Dùng `@After` cho cleanup hoặc total completed, không dùng để khẳng định request thành công.
- [ ] Dùng `@Before` cho bước trước khi gọi, không kết luận outcome tại đây.
- [ ] Chỉ dùng `@Around` khi cần can thiệp flow hoặc giữ scope trước/sau cùng một invocation, và kiểm soát `proceed()`.
- [ ] Kiểm tra outcome `return null`, return object, checked và unchecked exception.
- [ ] Kiểm tra khi target tự catch exception và return fallback.
- [ ] Kiểm tra sự khác nhau giữa outcome của target và outcome mà **caller proxy** thấy khi có cache/retry/fallback.
- [ ] Test qua Spring-managed proxy, không dùng `new`/`this` để chứng minh AOP.
- [ ] Nếu aspect concurrent, dùng dữ liệu local mỗi invocation hoặc primitive thread-safe cho counters.
- [ ] Tránh advice throwing exception không cần thiết vì có thể thay đổi exception/result mà business code nhìn thấy.
- [ ] Với transaction, tách quy tắc rollback và business status khỏi khái niệm “after throwing”.

## 13. Kết luận

Trong Spring Aspect, ba “sự kiện sau method” mang ngữ nghĩa khác nhau:

- **`@AfterReturning`:** phương thức kết thúc bình thường; kết quả có thể là object, `null`, mã trạng thái từ chối, hoặc không có giá trị ở method `void`.
- **`@AfterThrowing`:** phương thức kết thúc bằng exception thoát ra ngoài; checked hay unchecked đều có thể quan sát nếu type binding phù hợp.
- **`@After`:** phương thức kết thúc theo bất kỳ đường nào và advice này đã được áp dụng; semantics như `finally`.

`@Around` là công cụ mạnh hơn: nó có thể tiếp tục, bỏ qua, lặp lại hoặc chuyển kết quả/exception, làm thay đổi outcome tại những vị trí khác nhau trong Spring interceptor chain. Vì vậy **định nghĩa rõ vị trí đo lường và business policy** trước khi gắn nhãn success/failure.

Để áp dụng đúng, cần đồng thời kiểm tra **pointcut**, **Spring-managed proxy boundary** và **outcome thực sự của invocation**. Không một annotation advice riêng lẻ nào thay thế được hiểu biết về cả ba yếu tố đó.

### Tài liệu Spring chính thức

- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After`, `@Around`, binding và ordering.
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): các loại advice và cách Spring áp dụng method-execution join point.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký `@Aspect` và bật Spring auto-proxy.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, self-invocation, proxy boundary.
- [Spring Framework — Declarative Transaction Rolling Back](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html): exception types và rollback rules, độc lập với việc advice đo throwing completion.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-006">Chọn loại advice ít quyền can thiệp nhất đáp ứng yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn loại advice ít quyền can thiệp nhất đáp ứng yêu cầu trong Spring AOP

## 1. Nguyên tắc thiết kế

Trong Spring AOP, một aspect có thể gắn hành vi vào **method execution của Spring bean** bằng các loại advice khác nhau: `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After` và `@Around`.

**Quy tắc thiết kế được Spring Framework khuyến nghị: chọn loại advice ít quyền can thiệp nhất nhưng vẫn thực hiện đúng yêu cầu.** Ví dụ, chỉ cần ghi log trước khi method chạy thì dùng `@Before`, không dùng `@Around` để chủ động gọi `proceed()`. Chỉ cần ghi nhận giá trị trả về thành công thì dùng `@AfterReturning`, không tự triển khai một wrapper toàn bộ quá trình gọi.

Vì sao? Advice có quyền can thiệp càng lớn thì càng nhiều khả năng gây lỗi:

- Bỏ quên `proceed()` làm business method không chạy.
- Gọi `proceed()` nhiều lần làm ghi dữ liệu hoặc gửi sự kiện lặp.
- Trả sai kiểu hoặc nuốt exception thay đổi hợp đồng service.
- Ghi đè kết quả của method hoặc gây bất ngờ trong transaction/cache.
- Dùng cùng một advice cho quá nhiều tình huống khiến khó kiểm soát thứ tự và dễ vi phạm nguyên tắc đơn trách nhiệm.

Tuy nhiên, **“ít quyền” không phải thứ tự xếp hạng tuyến tính tuyệt đối** của năm annotation. Mỗi loại ứng với **thời điểm và dữ liệu** nó cần quan sát. Việc lựa chọn trước tiên dựa trên yêu cầu: trước khi chạy, sau khi thành công, khi có exception, khi kết thúc bất kể kết quả, hay cần chủ động điều khiển việc thực thi.

**Phạm vi bài:** cách chọn advice đúng trong **Spring Framework AOP**, nơi các Spring bean được bọc bằng JDK/CGLIB proxy và advice chạy khi method call đi qua proxy. Không phân tích lại lý thuyết AspectJ weaving độc lập.

## 2. Bảng quyết định chọn advice

| Yêu cầu | Advice ưu tiên | Quyền can thiệp tương ứng | Điều cần tránh |
| --- | --- | --- | --- |
| Ghi audit/log trước khi gọi service method | `@Before` | Chạy trước, quan sát method/arguments; không điều khiển `proceed()` | Không dùng `@Around` chỉ để in một dòng trước method |
| Kiểm tra đầu vào và **từ chối bằng cách ném lỗi** trước khi xử lý | `@Before` có thể đủ | Có thể ném exception, ngăn method tiếp tục | Đừng nghĩ `@Before` hoàn toàn không thể làm thay đổi luồng chạy |
| Ghi số liệu khi method **trả về bình thường** | `@AfterReturning` | Nhận kết quả trả về và quan sát sau thành công | Không mong nó chạy khi method ném exception |
| Ghi log giá trị thực sự được trả về | `@AfterReturning(returning = "...")` | Bind return value theo type | Không cố trả một reference khác từ advice để thay kết quả |
| Ghi metrics/lỗi khi target **ném exception** | `@AfterThrowing` | Quan sát exception mà target ném | Không coi nó là `catch` có thể tự nuốt lỗi của target |
| Giải phóng tài nguyên/cập nhật trạng thái khi method kết thúc bằng mọi cách | `@After` | Hành vi kiểu `finally` ở join point | Không dùng khi chỉ muốn chạy sau thành công |
| Đo thời gian bằng một biến bắt đầu và một biến kết thúc **trong cùng lượt gọi** | `@Around` có cơ sở sử dụng | Bọc trước và sau; có thể giữ local state an toàn theo invocation | Phải gọi `proceed()`, thường kèm `finally` |
| Cần trả kết quả cache mà **không gọi target** | `@Around`, hoặc tốt hơn là hạ tầng cache của Spring nếu phù hợp | Có thể bỏ qua target hoặc thay kết quả | Không tự viết cache trừ khi hạ tầng chuẩn không đáp ứng |
| Cần điều chỉnh arguments/return value, retry có kiểm soát hoặc shortcut | `@Around` nếu thực sự bắt buộc | Can thiệp mạnh nhất vào method invocation | Không bỏ qua idempotency, exception và giao dịch |
| Chỉ cần hành vi transaction/cache/security đã có framework hỗ trợ | Ưu tiên `@Transactional`/`@Cacheable`/cơ chế tích hợp phù hợp | Để Spring quản lý advisor chuyên dụng | Không tự dùng `@Around` để tái hiện transaction manager hoặc cache |

Một câu hỏi giúp quyết định nhanh:

> **Có cần tự quyết định method gốc được gọi lúc nào, bao nhiêu lần, với tham số nào và trả về cái gì không?** Nếu **không**, ưu tiên advice cụ thể (`@Before`, `@AfterReturning`, `@AfterThrowing` hoặc `@After`). Nếu **có**, `@Around` có thể cần thiết, nhưng phải giải thích được vì sao loại hẹp hơn không đáp ứng.

## 3. Hiểu chính xác năm loại advice trong Spring

### 3.1. `@Before`: chỉ cần xử lý trước method

~~~java
@Before("execution(* demo.OrderService.begin(..))")
public void beforeBegin() {
    System.out.println("BEFORE: begin");
}
~~~

Advice chạy **trước** một method execution khớp pointcut trên Spring proxy. Dùng cho audit marker, tracing context, quan sát tên method hoặc kiểm tra điều kiện đầu vào có thể ném exception để từ chối.

Quy tắc quan trọng:

- Không có `ProceedingJoinPoint` hay quyền tự gọi `proceed()`.
- Nếu `@Before` hoàn thành bình thường, Spring AOP tiếp tục lời gọi tới advice tiếp theo/target.
- **Nếu `@Before` ném exception**, method đích có thể không chạy; vì vậy loại này không phải “hoàn toàn không thể thay đổi hành vi”. Sử dụng nó cho validation có chủ ý, không ném lỗi vô tình từ logging.
- Muốn đọc argument, có thể nhận `JoinPoint` làm tham số đầu hoặc bind bằng `args(...)`; không cần chuyển sang `@Around` chỉ để đọc.
- Chỉ log trước khi thực hiện không có lý do bắt buộc phải bọc kết quả hay exception.

Ví dụ một audit logger không đáng tin có thể ném exception do formatter/remote sink; nếu điều đó không được phép làm fail business request, hãy đặt chính sách xử lý riêng trong advice, không vô thức để lỗi logging trở thành lỗi nghiệp vụ.

### 3.2. `@AfterReturning`: chỉ xử lý khi method thành công

~~~java
@AfterReturning(
    pointcut = "execution(String demo.OrderService.quote(..))",
    returning = "result",
    argNames = "result"
)
public void afterQuote(String result) {
    System.out.println("AFTER_RETURNING: " + result);
}
~~~

Method advice chỉ được gọi khi method đích **trả về bình thường**, có thể cả khi kết quả là `null` nếu type binding phù hợp. Trong ví dụ, `returning = "result"` bind giá trị method trả về vào biến advice tên `result`. `argNames` làm việc liên kết tên tham số tường minh hơn.

Đây là lựa chọn phù hợp khi:

- Cần đếm số lần thành công;
- Muốn quan sát return value để audit hoặc cập nhật metrics;
- Cần log mã kết quả sau khi business logic đã chạy.

Giới hạn then chốt: **không thể thay hẳn reference được trả về bằng một reference mới thông qua giá trị `return` của `@AfterReturning`**. Method advice loại này thường có kiểu trả về `void`. Nếu nó nhận một **object mutable**, về mặt Java có thể sửa state của object đang được trả về (nguy cơ side effect), nhưng đây không phải cùng việc thay object reference cho caller. Nếu yêu cầu bắt buộc **transform kết quả trả về**, hãy cân nhắc thiết kế lại service hoặc dùng `@Around` khi hợp lý.

`@AfterReturning` không chạy khi method đích kết thúc bằng exception. Nếu yêu cầu quan sát mọi kết quả, phải chọn hoặc kết hợp loại advice khác thích hợp.

### 3.3. `@AfterThrowing`: chỉ xử lý khi target ném exception

~~~java
@AfterThrowing(
    pointcut = "execution(* demo.OrderService.fail(..))",
    throwing = "error",
    argNames = "error"
)
public void afterFailure(IllegalStateException error) {
    System.out.println("AFTER_THROWING: " + error.getMessage());
}
~~~

Advice chạy khi method đích được advice **thoát bằng exception phù hợp**. Thuộc tính `throwing` gắn exception vào tham số method advice, và **type của tham số cũng lọc exception**. Trong ví dụ, chỉ exception thuộc `IllegalStateException` (hoặc subtype) được bind; muốn bắt nhiều loại để chỉ ghi log, có thể dùng `Throwable`, nhưng nên rõ ràng về mục tiêu.

Điểm thường nhầm:

- `@AfterThrowing` **không tương đương** một khối `catch` làm exception biến mất. Sau khi advice hoàn thành bình thường, exception của target vẫn được propagate tới caller.
- Nếu muốn **recover và trả về kết quả thay thế**, `@AfterThrowing` không có quyền làm việc đó; cân nhắc bắt exception ở business/service boundary hoặc `@Around` nếu thực sự cần xử lý xuyên các bean.
- Theo tài liệu Spring, `@AfterThrowing` được định nghĩa để nhận exception phát ra từ **target join point**, không xem nó như listener cho mọi exception do các `@After`/`@AfterReturning` khác ném.
- Nếu method target tự `try/catch` và không rethrow, phía Spring AOP quan sát một lần trở về bình thường, không phải thrown exit.

Hãy phân biệt “ghi nhận thất bại” với “xử lý thất bại”: loại advice chỉ nên được trao quyền mà requirement thực sự cần.

### 3.4. `@After`: dọn dẹp kiểu finally, cho mọi đường kết thúc

~~~java
@After("execution(* demo.OrderService.release(..))")
public void afterRelease() {
    System.out.println("AFTER: release");
}
~~~

`@After` (after-finally advice) chạy khi method execution khớp **kết thúc bằng return hoặc exception**. Loại này thích hợp cho:

- Dừng một marker theo dõi hoặc thực hiện cleanup không phụ thuộc thành công/thất bại;
- Cập nhật số lần một method hoàn tất bất kể outcome;
- Release resource đã được quản lý theo một contract xác định.

**Không dùng `@After` chỉ để log giao dịch thành công.** Nếu method ném lỗi, `@After` vẫn chạy; log `SUCCESS` trong advice này sẽ tạo sự thật sai. Dùng `@AfterReturning` cho successful outcome và `@AfterThrowing` cho failure outcome nếu cần phân biệt.

`@After` tương tự khối Java `finally`, nhưng vẫn là Spring AOP advice và chỉ áp dụng qua proxy. Nếu bản thân việc cleanup của advice ném exception, nó có thể ảnh hưởng kết quả caller quan sát; hãy làm cleanup an toàn và đừng nhầm việc có `@After` với bảo đảm giải phóng mọi resource trong mọi tình huống JVM/process.

### 3.5. `@Around`: chỉ dùng khi cần kiểm soát invocation

~~~java
@Around("execution(* demo.OrderService.guard(..))")
public Object aroundGuard(ProceedingJoinPoint point) throws Throwable {
    boolean allowed = (Boolean) point.getArgs()[0];
    if (!allowed) {
        return "blocked";
    }
    return point.proceed();
}
~~~

`@Around` bao lấy method execution khớp; tham số đầu là `ProceedingJoinPoint`. Nó có khả năng:

- Làm việc trước và sau target;
- **Không gọi `proceed()`**, khiến target bị bỏ qua;
- Gọi `proceed()` một lần hoặc nhiều lần (khả năng retry); phải hiểu side effects;
- Thay argument cho target bằng `proceed(Object[])` trong các điều kiện API phù hợp;
- Bắt/rethrow/đổi exception hoặc trả một kết quả khác cho caller;
- Giữ local state xuyên trước/sau trong cùng invocation, ví dụ start/end time.

Đổi lại, rủi ro tăng mạnh. Để **không làm thay đổi hợp đồng business**, một `@Around` kiểu wrapper thường phải:

1. Khởi tạo dữ liệu trước lời gọi (ví dụ timer);
2. **Thực hiện `point.proceed()` chính xác một lần** nếu cần target chạy bình thường;
3. Trả **đúng `Object`** do `proceed()` trả về;
4. Không vô ý nuốt hoặc thay exception;
5. Dùng `finally` khi cleanup phải chạy dù method ném lỗi.

Ví dụ đo thời gian với cleanup đúng cả hai đường:

~~~java
@Around("execution(* demo..service..*.*(..))")
public Object measure(ProceedingJoinPoint point) throws Throwable {
    long started = System.nanoTime();
    try {
        return point.proceed();
    } finally {
        long elapsedNanos = System.nanoTime() - started;
        // Ghi metric ở đây; không thay kết quả hoặc exception từ target.
    }
}
~~~

Đây là **snippet minh họa**, không phải phương án bổ sung trực tiếp vào ví dụ hoạt động bên dưới; cần `@Aspect` bean và đúng package. `System.nanoTime()` đo khoảng thời gian, không phải wall-clock timestamp. Khi timer chỉ cần start/end của một method trong cùng context, `@Around` có cơ sở sử dụng vì các advice trước/sau tách riêng khó truyền local state per-invocation một cách đơn giản và an toàn.

Không viết `@Around` trả `void` cho method có return value nếu muốn giữ kết quả; advice quanh nên trả `Object` và trả kết quả của `proceed()` để giữ hợp đồng. Nếu chủ động trả giá trị thay thế, bảo đảm kiểu kết quả đúng với return type của method và caller.

## 4. Tại sao “loại nhỏ nhất” làm code an toàn hơn?

Một `@Around` có thể viết được mọi điều kiện ở bảng trên, nhưng nó cũng có những trách nhiệm mà các loại hẹp hơn không yêu cầu:

~~~java
@Around("execution(* demo.OrderService.begin(..))")
public Object tooPowerful(ProceedingJoinPoint point) throws Throwable {
    System.out.println("BEFORE: begin");
    return point.proceed();
}
~~~

Còn với nhu cầu chỉ ghi log trước method:

~~~java
@Before("execution(* demo.OrderService.begin(..))")
public void sufficient() {
    System.out.println("BEFORE: begin");
}
~~~

Hai snippet là **hai cách thay thế nhau** cho cùng nhu cầu, không nên thêm cả hai vào cùng một aspect để tránh log trùng. Dạng `@Before` rõ mục đích và không phải giữ kết quả, exception hay gọi `proceed()`.

Điều tương tự áp dụng cho `@AfterReturning`: một `@Around` có thể gọi target rồi in return value, nhưng sẽ phải chịu trách nhiệm chuyển đúng kết quả/exception. Một `@AfterReturning` chỉ chạy trên nhánh thành công và framework làm phần còn lại.

Nguyên tắc này gần với **least privilege** ở cấp API: mỗi advice được trao đúng cơ hội quan sát và thay đổi cần thiết, không phải thêm quyền chỉ vì API mạnh hơn dễ lạm dụng.

## 5. Ví dụ Spring Framework Java 17+ đủ năm loại advice

Project minh họa **năm nhu cầu khác nhau** qua năm method riêng biệt. Ta cố ý thiết kế các pointcut **không trùng nhau** để có output dễ dự đoán và không nhập nhằng thứ tự advice giữa các loại. Chương trình còn đếm số lần advice để chứng minh logic đúng.

Yêu cầu: Java 17+, Maven 3.9+, Spring Framework 6.2.0, AspectJ Weaver 1.9.22. Đây là phiên bản cố định để minh họa (không khẳng định mới nhất), chạy bằng Spring Framework thuần, không dùng Spring Boot hay AspectJ weaving.

Cấu trúc project:

~~~text
spring-advice-least-power/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── OrderService.java
    └── ChoiceAspect.java
~~~

### 5.1. Maven dependencies

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-advice-least-power</artifactId>
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
                    <mainClass>demo.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cho Spring container/Java configuration; `spring-aop` chứa AOP proxy/`AopUtils`; `aspectjweaver` cung cấp thư viện cho cú pháp pointcut `@AspectJ` do Spring phân tích. **Dependency `aspectjweaver` không tự tạo AspectJ compiler/load-time weaving.**

### 5.2. Target có phương thức thành công, thất bại và cần điều khiển

Tạo `src/main/java/demo/OrderService.java`:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;

public class OrderService {
    private final AtomicInteger guardExecutions = new AtomicInteger();

    public void begin() {
        System.out.println("TARGET: begin");
    }

    public String quote() {
        System.out.println("TARGET: quote");
        return "USD-10";
    }

    public void fail() {
        System.out.println("TARGET: fail");
        throw new IllegalStateException("declined");
    }

    public void release(boolean failure) {
        System.out.println("TARGET: release " +
                (failure ? "failure" : "success"));
        if (failure) {
            throw new IllegalArgumentException("release-failed");
        }
    }

    public String guard(boolean allowed) {
        guardExecutions.incrementAndGet();
        System.out.println("TARGET: guard");
        return "accepted";
    }

    public int getGuardExecutions() {
        return guardExecutions.get();
    }
}
~~~

`begin()` đại diện cho trường hợp chỉ cần log trước thực thi. `quote()` trả `String` thành công. `fail()` cố tình ném `IllegalStateException` để kiểm tra `@AfterThrowing`. `release(boolean)` được gọi hai lần (trả về bình thường và ném exception) để kiểm chứng `@After` kiểu finally. `guard(boolean)` sẽ do `@Around` quyết định có gọi target không; bộ đếm `guardExecutions` nằm **trong target** giúp xác nhận nhánh shortcut thực sự bỏ qua target.

Đây là class Java đầy đủ, không cần `@Service` khi dùng `@Bean` để đăng ký trong config. Class không `final` và method có thể override, giúp Spring tạo CGLIB class proxy.

### 5.3. Aspect với pointcut riêng cho từng nhu cầu

Tạo `src/main/java/demo/ChoiceAspect.java`:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class ChoiceAspect {
    private final AtomicInteger beforeCalls = new AtomicInteger();
    private final AtomicInteger returningCalls = new AtomicInteger();
    private final AtomicInteger throwingCalls = new AtomicInteger();
    private final AtomicInteger finallyCalls = new AtomicInteger();
    private final AtomicInteger aroundCalls = new AtomicInteger();

    @Before("execution(* demo.OrderService.begin(..))")
    public void beforeBegin() {
        beforeCalls.incrementAndGet();
        System.out.println("BEFORE: begin");
    }

    @AfterReturning(
        pointcut = "execution(String demo.OrderService.quote(..))",
        returning = "result",
        argNames = "result"
    )
    public void afterQuote(String result) {
        returningCalls.incrementAndGet();
        System.out.println("AFTER_RETURNING: " + result);
    }

    @AfterThrowing(
        pointcut = "execution(* demo.OrderService.fail(..))",
        throwing = "error",
        argNames = "error"
    )
    public void afterFail(IllegalStateException error) {
        throwingCalls.incrementAndGet();
        System.out.println("AFTER_THROWING: " + error.getMessage());
    }

    @After("execution(* demo.OrderService.release(..))")
    public void afterRelease() {
        finallyCalls.incrementAndGet();
        System.out.println("AFTER: release");
    }

    @Around("execution(* demo.OrderService.guard(..))")
    public Object aroundGuard(ProceedingJoinPoint point) throws Throwable {
        aroundCalls.incrementAndGet();
        boolean allowed = (Boolean) point.getArgs()[0];
        if (!allowed) {
            System.out.println("AROUND: short-circuit");
            return "blocked";
        }
        System.out.println("AROUND: proceed");
        return point.proceed();
    }

    public int getBeforeCalls() {
        return beforeCalls.get();
    }

    public int getReturningCalls() {
        return returningCalls.get();
    }

    public int getThrowingCalls() {
        return throwingCalls.get();
    }

    public int getFinallyCalls() {
        return finallyCalls.get();
    }

    public int getAroundCalls() {
        return aroundCalls.get();
    }
}
~~~

Điểm cần quan sát:

1. **`@Before`** chỉ đánh dấu trước method `begin`; không giữ return value hoặc exception.
2. **`@AfterReturning`** chỉ chạy cho `quote` khi trả về thành công, bind `result` như một `String` và chỉ đọc/in giá trị.
3. **`@AfterThrowing`** chỉ chạy cho `fail` khi exception phù hợp; không catch/nuốt exception thay cho caller.
4. **`@After`** chạy cho `release` ở cả lần bình thường và lần bị exception.
5. **`@Around`** chỉ áp dụng cho `guard` bởi vì yêu cầu ở đây là có thể **bỏ qua target và trả `"blocked"`** khi input không được phép. Với input `true`, nó gọi `point.proceed()` và trả lại chính kết quả từ target.
6. Các pointcut tương ứng **tách biệt theo tên method**, nên không dựa vào thứ tự của nhiều advice cùng match một join point. Đây là cách cố ý giữ stdout đáng tin và giúp người học hiểu quyền của từng loại.
7. Mỗi advice có counter riêng; ngoài stdout, chương trình sẽ kiểm chứng số lần gọi. Không cần `ThreadLocal`/state toàn cục trong demo tuần tự.

Nếu chỉ muốn **kiểm tra được phép rồi từ chối bằng exception trước method**, `@Before` có thể đủ. `@Around` ở ví dụ `guard` được chọn vì requirement là **trả `"blocked"` nhưng không chạy target**, tức thay đổi flow/return value theo chủ đích. Đây là khác biệt quan trọng giữa chọn advice “vì tiện” và chọn advice do yêu cầu.

### 5.4. Khởi tạo Spring context và xác minh

Tạo `src/main/java/demo/App.java`:

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class App {
    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public ChoiceAspect choiceAspect() {
        return new ChoiceAspect();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(App.class)) {
            OrderService service = context.getBean(OrderService.class);
            ChoiceAspect aspect = context.getBean(ChoiceAspect.class);

            boolean proxied = AopUtils.isAopProxy(service);
            System.out.println("PROXY: " + proxied);

            service.begin();

            String quote = service.quote();
            System.out.println("RESULT_QUOTE: " + quote);

            try {
                service.fail();
            } catch (IllegalStateException ex) {
                System.out.println("CAUGHT: " + ex.getMessage());
            }

            service.release(false);
            try {
                service.release(true);
            } catch (IllegalArgumentException ex) {
                System.out.println("CAUGHT: " + ex.getMessage());
            }

            String blocked = service.guard(false);
            System.out.println("RESULT_BLOCKED: " + blocked);

            String allowed = service.guard(true);
            System.out.println("RESULT_ALLOWED: " + allowed);

            int before = aspect.getBeforeCalls();
            int returning = aspect.getReturningCalls();
            int throwing = aspect.getThrowingCalls();
            int finished = aspect.getFinallyCalls();
            int around = aspect.getAroundCalls();
            int actualGuards = service.getGuardExecutions();

            System.out.println("COUNTS: before=" + before
                    + ",returning=" + returning
                    + ",throwing=" + throwing
                    + ",finally=" + finished
                    + ",around=" + around
                    + ",targetGuard=" + actualGuards);

            if (!proxied || before != 1 || returning != 1
                    || throwing != 1 || finished != 2
                    || around != 2 || actualGuards != 1
                    || !"USD-10".equals(quote)
                    || !"blocked".equals(blocked)
                    || !"accepted".equals(allowed)) {
                throw new IllegalStateException(
                        "Unexpected Spring AOP advice behavior");
            }

            System.out.println("CHECK: PASS");
        }
    }
}
~~~

Giải thích từng phần:

- `@EnableAspectJAutoProxy(proxyTargetClass = true)` đăng ký Spring auto-proxy creator, cho phép class proxy cho `OrderService`. Bean chỉ nhận proxy nếu advisor phù hợp, không phải mọi bean đều bị proxy hóa.
- `@Bean choiceAspect()` đăng ký `ChoiceAspect` vào context; **`@Aspect` riêng lẻ không tự trở thành Spring bean**.
- `service` là bean được lấy qua `ApplicationContext`, nên lời gọi bình thường đến những method khớp pointcut đi qua Spring AOP proxy.
- Các khối `try/catch` ở **caller** chứng minh exception từ target vẫn truyền tới caller sau khi `@AfterThrowing`/`@After` chạy.
- `service.getGuardExecutions()` đo số lần target `guard` được gọi: hai lần caller gọi qua proxy nhưng chỉ một lần target chạy, do `@Around` short-circuit ở lần đầu.
- Mỗi counter trong `ChoiceAspect` xác nhận semantics của từng advice, không chỉ nhìn thứ tự log.
- Điều kiện `if` cuối bảo đảm chương trình **ném lỗi nếu kết quả không đúng**, rồi mới in `CHECK: PASS`.

### 5.5. Lệnh chạy và output

Tại thư mục `spring-advice-least-power`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do mã Java chủ động in ra trong ví dụ:

~~~text
PROXY: true
BEFORE: begin
TARGET: begin
TARGET: quote
AFTER_RETURNING: USD-10
RESULT_QUOTE: USD-10
TARGET: fail
AFTER_THROWING: declined
CAUGHT: declined
TARGET: release success
AFTER: release
TARGET: release failure
AFTER: release
CAUGHT: release-failed
AROUND: short-circuit
RESULT_BLOCKED: blocked
AROUND: proceed
TARGET: guard
RESULT_ALLOWED: accepted
COUNTS: before=1,returning=1,throwing=1,finally=2,around=2,targetGuard=1
CHECK: PASS
~~~

Maven, SLF4J hoặc log framework có thể sinh thêm dòng ngoài ví dụ. Bản trên chỉ liệt kê `System.out.println` từ code. Nếu output lệch, kiểm tra aspect được đăng ký làm bean, Spring AOP bật, pointcut khớp, caller có đi qua proxy hay không, và code có ném exception bất ngờ trong advice không.

Từ output, kiểm chứng đúng **năm đặc trưng**:

- `BEFORE` xuất hiện trước `TARGET: begin`.
- `AFTER_RETURNING` xuất hiện sau `TARGET: quote` và nhận `USD-10`.
- `AFTER_THROWING` xuất hiện sau `TARGET: fail`, nhưng caller vẫn bắt được `declined`.
- `AFTER: release` xuất hiện cả khi `release(false)` thành công và khi `release(true)` ném exception.
- `AROUND: short-circuit` khiến `guard(false)` không in `TARGET: guard`; `guard(true)` có `proceed()` nên in `TARGET: guard`. Bộ đếm `targetGuard=1` xác nhận target thực thi đúng một lần.

Điều này chứng minh **tính phù hợp của loại advice với yêu cầu**, không chỉ khả năng viết ra cùng một hành vi bằng `@Around` cho mọi case.

## 6. Chọn advice cho từng yêu cầu thực tế

### 6.1. Ghi audit/tracing trước tác vụ

**Yêu cầu:** “Mỗi lần gọi `OrderService.submit(...)` qua Spring proxy, ghi nhận ai gọi và tham số.”

Lựa chọn: `@Before` có `JoinPoint` nếu cần argument/signature:

~~~java
@Before("execution(* demo.OrderService.submit(..))")
public void beforeSubmit(JoinPoint point) {
    String method = point.getSignature().getName();
    Object[] args = point.getArgs();
    // Ghi dữ liệu đã lọc, không log token/secret/PII tùy tiện.
}
~~~

Đoạn snippet cần đặt trong `@Aspect` và import `org.aspectj.lang.JoinPoint`; `OrderService.submit` là tên method minh họa, không có trong project chạy phần 5. Nó dùng `JoinPoint` (đọc thông tin), **không cần `ProceedingJoinPoint`**.

### 6.2. Đếm kết quả thành công

**Yêu cầu:** “Chỉ tăng successfulCounter nếu service thực sự trả kết quả.”

Lựa chọn: `@AfterReturning`. Một `@After` sẽ đếm cả thất bại; một `@Before` sẽ đếm cả những lời gọi sau đó ném exception. Nếu cần log return value, bind `returning`. Đây là ví dụ điển hình cho việc **semantics đầu ra quyết định loại advice**.

### 6.3. Ghi lỗi nhưng không thay exception

**Yêu cầu:** “Khi service ném lỗi nghiệp vụ, ghi mã lỗi để dashboard nhận biết; caller vẫn xử lý exception.”

Lựa chọn: `@AfterThrowing` với `throwing` typed binding. Đảm bảo logic log không ném lỗi mới làm thay đổi luồng caller. Không dùng `@Around` để `catch` rồi vô tình `return null` làm mất exception gốc.

### 6.4. Dọn dẹp dù thành công hay thất bại

**Yêu cầu:** “Luôn đánh dấu kết thúc một hoạt động được advice, kể cả khi method ném exception.”

Lựa chọn: `@After`. Phải phân biệt với tài nguyên có vòng đời từ trước invocation: nếu chỉ `@After` nhận biết resource không rõ nguồn tạo, đừng giả định nó có thể release an toàn. Với resource sinh ra ngay trong advice, một `@Around` có `try/finally` **có thể cần** để giữ handle local; với resource do business method sở hữu, để business method hoặc container quản lý lifecycle tại tầng đúng.

### 6.5. Chặn target nhưng trả fallback thay exception

**Yêu cầu:** “Nếu access policy trả false, không gọi target; trả đối tượng fallback có kiểu hợp lệ.”

Lựa chọn: `@Around` có lý do rõ ràng. `@Before` có thể ném exception để chặn nhưng **không thể tự trả fallback value thay target**. Nếu fallback là requirement, `@Around` có thể quyết định không gọi `proceed()`. Vẫn nên xem cơ chế security/caching được framework hỗ trợ trước khi tự triển khai chính sách xuyên ứng dụng.

### 6.6. Retry khi thất bại

**Yêu cầu:** “Một lời gọi idempotent có thể thử lại tối đa hai lần theo policy.”

Chỉ `@Around` mới trực tiếp có thể gọi `proceed()` nhiều lần trong cùng wrapper, nhưng **không có nghĩa luôn nên tự viết retry aspect**. Các method ghi DB/gửi thông báo/thanh toán thường không idempotent; retry có thể gây double writes/double charges. Cân nhắc cơ chế retry chuyên biệt (ví dụ Spring Retry), transaction boundary, backoff, timeout và idempotency key; phải định nghĩa được exception nào được retry. Nếu requirement không cần retry, tuyệt đối không thêm quyền gọi lại target.

### 6.7. Transaction và cache

Spring Framework có những module/annotation cấu hình transaction và cache dựa trên advisor phù hợp. Nếu mục tiêu thuần túy là `@Transactional` hoặc `@Cacheable` behavior chuẩn, **dùng tích hợp Spring tương ứng** thay vì tự dùng `@Around` để tái xây dựng transaction lifecycle, key semantics hoặc cache invalidation.

`@Around` phù hợp khi có yêu cầu cross-cutting đặc thù chưa được các extension point/framework đảm nhiệm, không phải khi chỉ muốn “một annotation chung”.

## 7. Thứ tự nhiều advice và tác động ngoài ý muốn

Ví dụ chính cố tình chọn **pointcut tách biệt cho từng loại advice**. Trong ứng dụng lớn, một join point có thể đồng thời khớp nhiều advice, kể cả từ nhiều aspects.

- Nếu các advice thuộc **những aspects khác nhau**, khi không chỉ định precedence, thứ tự có thể không xác định. Có thể dùng `@Order` hoặc `Ordered` để cấu hình thứ tự ở cấp aspect.
- Advice có precedence cao hơn thường chạy **trước khi đi vào** target và chạy **sau khi đi ra**; do đó thứ tự xuất hiện trong source code **không** bảo đảm thứ tự ở runtime.
- Spring/AspectJ có quy tắc nội bộ cho các loại advice trong cùng aspect; `@After` có semantics *after finally*, nên dù có precedence nội bộ cụ thể vẫn thường xuất hiện sau các advice trả về/throwing khi cùng match. **Đừng dựa vào thứ tự khai báo method trong file Java.**
- Hai advice cùng loại cùng match trong một aspect có thể không có thứ tự xác định chỉ vì nằm trên hay dưới nhau trong file nguồn.
- `@AfterThrowing` không được thiết kế như callback bắt exception do `@After`/`@AfterReturning` companion advice ném; xem scope exception ở **target method**.

Trong tình huống cần thứ tự mạnh, tạo **integration test** gọi đúng bean proxy, assert event trace và cấu hình ordering tường minh; đừng suy từ tên annotation hoặc vị trí khai báo.

Không cần tạo nhiều advice chỉ để “phòng xa” nếu nghiệp vụ thực sự cần một loại. Advice càng nhiều thì càng khó reason về tác động đến return/exception/transaction.

## 8. Kiểm tra điều kiện áp dụng trước khi chốt loại advice

Chọn đúng annotation chưa bảo đảm advice chạy: Spring chỉ áp dụng cho method invocation được proxy tiếp nhận và pointcut khớp. @Around không sửa được self-invocation qua this, đối tượng ngoài container, final/private method của class-based proxy, constructor hoặc field access.

Đặt sự kiện mong muốn tại đúng boundary: nếu cần sau transaction commit, dùng transaction synchronization hoặc sự kiện transaction phù hợp; nếu cần Future/Reactive completion, dùng completion callbacks/signals. @AfterReturning chỉ cho biết invocation trả bình thường. Nếu cần transformation hoặc fallback, cân nhắc explicit service/mapper hoặc @Around có kiểm soát. Transaction, security và cache nên ưu tiên extension point chính thức của Spring.

## 9. Review yêu cầu, lựa chọn và rủi ro

| Nhu cầu | Công cụ ưu tiên | Lỗi dễ gặp |
| --- | --- | --- |
| Log trước lời gọi | @Before | Dùng @Around nhưng quên proceed |
| Quan sát kết quả trả bình thường | @AfterReturning | Mong advice thay returned reference |
| Đo ngoại lệ theo subtype | @AfterThrowing | Mong advice swallow exception |
| Cleanup trên return và throw | @After | Nhầm finally với commit thành công |
| Đo duration theo từng invocation | @Around + local try/finally | Lưu start trong field aspect singleton |
| Retry/fallback | Business policy hoặc @Around có test | Thực thi side effects lặp thiếu idempotency |
| Chặn lời gọi nội bộ | Tách bean và inject proxy | Chỉ đổi JDK thành CGLIB |
| Theo dõi async task | Future/Reactive completion hook | Xem wrapper return là task completion |

Mỗi quyết định cần ghi rõ điểm gọi, proxy mode, pointcut, return/exception contract và thứ tự với các advisor hạ tầng. Đây là tiêu chí review quan trọng hơn chọn advice theo tên annotation.

## 10. Bài tập lựa chọn loại advice

Với từng yêu cầu, hãy chọn **loại hẹp nhất** rồi giải thích:

1. “Ghi tên method trước khi thực thi trên bean service.”  
   **`@Before`.** Không cần giữ kết quả hoặc tự gọi target.
2. “Chỉ tăng counter khi call hoàn tất thành công.”  
   **`@AfterReturning`.** `@After` sẽ đếm cả exception.
3. “Báo metrics lỗi với message của `IllegalStateException`; giữ exception tới caller.”  
   **`@AfterThrowing(throwing="error")`** và typed parameter tương ứng.
4. “Dọn marker/metric cả khi return và khi exception.”  
   **`@After`.** Nếu cần giữ local handle tạo ra trước invocation thì `@Around` có `try/finally` có thể phù hợp hơn.
5. “Đọc argument rồi từ chối bằng exception nếu policy cấm.”  
   **`@Before` có thể đủ.** Không nhất thiết phải kiểm soát `proceed()`.
6. “Trả cache hit mà không gọi target; cache miss thì gọi target.”  
   **`@Around`** có thể cần, nhưng ưu tiên `@Cacheable` và framework config nếu đáp ứng được.
7. “Ghi thời gian đúng của mỗi invocation, kể cả exception, bằng local started time.”  
   **`@Around`** với `try/finally` là cách trực tiếp; bảo đảm `proceed()` đúng một lần.
8. “Khi method ném exception, trả một giá trị fallback hợp lệ thay vì rethrow.”  
   **`@Around`** nếu đó là requirement ở cross-cutting boundary; `@AfterThrowing` không thay exception thành normal return.
9. “Ghi log đối tượng kết quả trả về, không sửa nó.”  
   **`@AfterReturning`** và bind `returning`.
10. “Bắt cả constructor/field access ở bất kỳ object nào.”  
    **Không advice Spring AOP nào đáp ứng.** Đây là giới hạn proxy method execution; cần đánh giá yêu cầu/cơ chế khác, không chọn `@Around` chỉ vì nó mạnh nhất.

## 11. Checklist trước khi review code Spring Aspect

- [ ] Requirement về **thời điểm và outcome** được ghi rõ trước khi chọn annotation advice.
- [ ] `@Before` được ưu tiên khi chỉ cần chạy trước method, không tự bọc `proceed()`.
- [ ] `@AfterReturning` dùng cho success; `@AfterThrowing` cho thrown exception; `@After` cho finally.
- [ ] Không dùng `@Around` trừ khi cần giữ local state trước/sau hoặc thay đổi invocation/return/fallback theo yêu cầu rõ ràng.
- [ ] Với `@Around`, tất cả nhánh có chính sách `proceed()`, return type, side effects và exception nhất quán.
- [ ] Advice không tự gây lỗi ngoài dự kiến khiến business contract đổi.
- [ ] Aspect và target được đăng ký làm Spring beans; lời gọi qua Spring AOP proxy.
- [ ] Pointcut đủ hẹp, không match nhầm method hoặc bean khác.
- [ ] Có kiểm thử cả normal return, thrown exception, case không match và self-invocation nếu liên quan.
- [ ] Không dựa vào thứ tự source code của nhiều advice; cấu hình ordering rõ ràng khi thực sự cần.
- [ ] Ưu tiên API sẵn có của Spring cho transaction/cache/security thay vì tự xây một `@Around` khó bảo trì.

## 12. Tài liệu chính thức của Spring Framework

- [Spring Framework 6.2 — AOP Concepts](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-defn.html): các loại advice và nguyên tắc dùng **least powerful advice** đáp ứng yêu cầu.
- [Spring Framework 6.2 — Declaring Advice](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): semantics `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After`, `@Around`, return/exception binding và precedence.
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): đăng ký hỗ trợ Spring AOP kiểu `@AspectJ`.
- [Spring Framework 6.2 — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): chọn method executions trong Spring bean.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxy, self-invocation và giới hạn method.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-007">Giới hạn của @AfterReturning khi muốn thay đối tượng trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của `@AfterReturning` khi muốn thay đối tượng trả về trong Spring AOP

## 1. Vấn đề: đã lấy được return value nhưng có thay được object caller nhận không?

Trong Spring AOP, `@AfterReturning` là **advice chạy khi một method execution được chọn kết thúc bình thường**. Nó thường dùng để ghi log kết quả, audit, cập nhật metric hoặc kiểm tra giá trị trả về.

Ví dụ:

```java
@AfterReturning(
    pointcut = "execution(* com.example.order.OrderService.find(..))",
    returning = "result",
    argNames = "result"
)
public void afterFind(Object result) {
    // Quan sát kết quả từ phương thức được advice.
}
```

Đây là **method fragment** cần đặt bên trong một class `@Aspect` đã được Spring quản lý. Thuộc tính `returning = "result"` bind giá trị được method trả về vào tham số `result` của advice.

Câu hỏi dễ gây nhầm lẫn: nếu trong advice viết `result = new Result(...)`, caller có nhận object mới không?

**Không.** Tài liệu Spring Framework nêu rõ `@AfterReturning` **không thể trả về một reference hoàn toàn khác** cho caller. Tham số được bind chỉ giúp advice **quan sát giá trị đã trả về**; gán lại biến Java cục bộ trong advice không thay thế return value nằm trong interceptor chain.

Tuy nhiên, nếu return value là một **object mutable** và advice sửa trạng thái của chính object đó, caller vẫn có thể thấy trạng thái đã thay đổi, vì advice và caller đang tham chiếu cùng object. Đây là **mutation của object đang được trả về**, không phải **thay object được trả về**. Sự phân biệt này rất quan trọng khi thiết kế Spring Aspect.

Nếu yêu cầu thật sự là **thay object/reference kết quả**, hãy dùng `@Around` (hoặc thiết kế lại logic return trong service/mapper), vì `@Around` có thể gọi `ProceedingJoinPoint.proceed()` rồi **trả về một object khác** cho caller.

Bài này chỉ tập trung vào **advice Spring `@AspectJ` trên Spring-managed beans qua proxy**, theo phạm vi Spring Aspect. Không bàn các cơ chế AspectJ weaving độc lập.

## 2. So sánh ba thao tác nhìn gần giống nhau

Giả sử method đích trả về:

```java
ResultBox returned = service.load("A");
```

Trong `@AfterReturning`:

| Thao tác | Ví dụ trong advice | Caller nhìn thấy gì? |
| --- | --- | --- |
| **Đọc kết quả** | `System.out.println(result)` | Object/reference đã được target trả về, không đổi vì việc đọc. |
| **Sửa trạng thái object mutable** | `result.setStatus("checked")` | Có thể thấy `status` mới trên **chính object cũ**. |
| **Gán lại biến parameter** | `result = new ResultBox("other")` | **Không nhận object mới**; phép gán chỉ đổi local variable của advice. |
| **Thử `return result` từ `@AfterReturning`** | Đổi advice từ `void` sang trả object để mong thay kết quả | Không phải cơ chế thay return value của Spring `@AfterReturning`; không nên dùng. |
| **`@Around` trả object mới** | `return new ResultBox("other")` | Caller nhận object được `@Around` trả, nếu method contract và chain cho phép. |

Hãy tách hai khái niệm:

- **Reference identity**: `a == b` kiểm tra có cùng Java object không.
- **Object state**: `a.getStatus()` cho biết dữ liệu hiện tại trong object.

Một `@AfterReturning` có thể tác động đến **state** nếu object mutable; nó **không có quyền thay thế reference cuối cùng** qua phép gán lại tham số advice.

## 3. Spring `@AfterReturning` chạy vào thời điểm nào?

Luồng thông thường của một lời gọi qua Spring AOP proxy:

```text
Calling bean / ApplicationContext client
                 |
                 v
         Spring AOP proxy
                 |
          advisor chain
                 |
                 v
         target business method
                 |
                 +--> return resultRef (normal completion)
                 |
                 v
        @AfterReturning advice
         |       |       |
         |       |       +-- log/audit/metric
         |       +---------- có thể mutate resultRef nếu object mutable
         +------------------ local assignment KHÔNG thay returned ref
                 |
                 v
            caller nhận resultRef
```

Sơ đồ này mô tả một `@AfterReturning` phù hợp, trong một chuỗi advice đơn giản. Trong ứng dụng có nhiều `@Around`, `@AfterReturning` hoặc advisor khác, **thứ tự và object cuối cùng có thể bị thay bởi một `@Around` bên ngoài**. Điều đó **không thay đổi** giới hạn của bản thân `@AfterReturning`.

Các điều kiện cần nhớ:

1. **Chỉ chạy khi method được chọn trả về bình thường.** Nếu target method ném exception đi ra ngoài, `@AfterReturning` không phải callback dành cho trường hợp đó. Nếu cần xử lý exception, xem `@AfterThrowing` hoặc `@Around` tùy yêu cầu.
2. **Chỉ có hiệu lực nếu lời gọi đi qua Spring AOP proxy phù hợp.** `new Service().run()` hoặc self-invocation `this.run()` thường bỏ qua advice.
3. **`@AfterReturning` có thể được khai báo không bind return value.** Cần biết method chạy thành công nhưng không cần result thì dùng `@AfterReturning("pointcut...") public void afterSuccess()`.
4. **Binding theo `returning` có điều kiện kiểu.** Tham số advice càng cụ thể, tập những return values/method executions có thể match càng hẹp.
5. **Thay thế giá trị trả về** và **quan sát giá trị trả về** là hai trách nhiệm khác nhau; hãy chọn loại advice tối thiểu đáp ứng mục tiêu.

## 4. `returning` bind đối số thế nào và những giới hạn thường gặp?

Ví dụ Spring `@Aspect`:

```java
@AfterReturning(
    pointcut = "execution(public * com.example.report.ReportService.load(..))",
    returning = "result",
    argNames = "result"
)
public void observe(ResultBox result) {
    System.out.println(result.getValue());
}
```

Ý nghĩa:

- `pointcut`: chọn method execution được advice.
- `returning`: tên của **parameter trong method advice**, ở đây là `result`.
- `argNames`: chỉ rõ tên binding để Spring không phải phụ thuộc hoàn toàn vào compiler parameter metadata.
- `ResultBox`: kiểu tham số advice; Spring dùng nó để ràng buộc các return values tương thích.
- `observe(...)`: chạy **sau normal return**; không nhận `ProceedingJoinPoint` và không có cơ chế `return` thay thế kết quả.

Đoạn này chỉ là fragment: cần imports, class `ResultBox`, class `ReportService`, annotation `@Aspect` và Spring configuration.

### 4.1. Binding bằng `Object` và bằng kiểu cụ thể

```java
@AfterReturning(
    pointcut = "execution(* com.example.report.ReportService.*(..))",
    returning = "value",
    argNames = "value"
)
public void observeAnything(Object value) {
    // Đọc result của những lời gọi return phù hợp.
}
```

so với:

```java
@AfterReturning(
    pointcut = "execution(* com.example.report.ReportService.*(..))",
    returning = "box",
    argNames = "box"
)
public void observeBoxes(ResultBox box) {
    // Chỉ áp dụng khi kết quả có kiểu tương thích ResultBox.
}
```

**Hai advice fragments này là lựa chọn thay thế, không phải yêu cầu đăng ký cùng lúc.** Binding không đơn thuần ép kiểu rồi hy vọng an toàn: kiểu của parameter advice ảnh hưởng đến matching. Với `void`, `null`, primitive boxing hoặc generic return types, nên chạy integration test trên phiên bản Spring thực tế thay vì mặc định mọi kiểu return đều bind được như nhau.

### 4.2. Chữ `returning` không phải tên biến ở target

Target:

```java
public ResultBox load() {
    ResultBox internal = new ResultBox("value");
    return internal;
}
```

Aspect:

```java
@AfterReturning(
    pointcut = "execution(* com.example.report.ReportService.load(..))",
    returning = "answer",
    argNames = "answer"
)
public void after(ResultBox answer) {}
```

Điều này hoàn toàn hợp lệ về mặt **tên**: `answer` không phải trùng với `internal`. Thuộc tính `returning` chỉ phải tương ứng tham số **của advice**, và giá trị binding lấy từ object target return.

### 4.3. Không thể sửa bằng cách đổi return type của advice

Một thử nghiệm sai:

```java
@AfterReturning(
    pointcut = "execution(* com.example.report.ReportService.load(..))",
    returning = "result"
)
public ResultBox after(ResultBox result) {
    return new ResultBox("replacement");
}
```

**Đây là anti-pattern minh họa, không phải code khuyến nghị chạy.** Return value của method advice `@AfterReturning` không được dùng làm return value của method target. Spring có thể yêu cầu các dạng signature cụ thể; dù Java method có trả được `ResultBox`, nó **không cung cấp một hợp đồng thay thế reference** tương tự `@Around`.

Nên khai báo advice `void` và đặt logic transformation tại đúng tầng.

## 5. Sửa mutable object khác hoàn toàn với thay reference

Giả sử một service trả về một object mutable:

```java
public final class ResultBox {
    private String value;

    public ResultBox(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
```

Target:

```java
ResultBox original = new ResultBox("origin");
return original;
```

Advice:

```java
@AfterReturning(
    pointcut = "execution(* com.example.report.ReportService.load(..))",
    returning = "result",
    argNames = "result"
)
public void after(ResultBox result) {
    result.setValue("changed");          // Mutation: caller có thể thấy.
    result = new ResultBox("new-object"); // Rebinding: caller KHÔNG nhận ref mới.
}
```

Đây là những fragments có thể đặt vào project Spring hoàn chỉnh. Hai thao tác trong cùng advice có hệ quả riêng:

- `setValue("changed")` đổi dữ liệu **trong instance đã có**.
- `result = new ResultBox(...)` chỉ đổi tham số Java **cục bộ** của advice.
- Caller nhận **cùng reference object trước mutation**, và `getValue()` có thể trả `"changed"`.
- Nếu caller đang giữ reference tới object ấy từ nơi khác, bên đó cũng có thể thấy mutation; đây là shared mutable state, không phải một “bản copy đã sửa”.

**Có thể mutate không có nghĩa nên mutate.** Một aspect dùng chung âm thầm sửa DTO, entity hoặc collection có thể phá invariant, thay dữ liệu cache, gây race condition và làm logic nghiệp vụ khó hiểu. Nếu chính sách thật sự cần đổi response, ưu tiên thiết kế transformation rõ ràng bằng `@Around` hoặc một service/mapper chuyên trách.

### 5.1. Object immutable: gán lại càng không có tác dụng

Với `String`, record bất biến hoặc DTO immutable:

```java
@AfterReturning(
    pointcut = "execution(* com.example.report.ReportService.message(..))",
    returning = "text",
    argNames = "text"
)
public void afterMessage(String text) {
    text = "rewritten:" + text;
}
```

`String` immutable nên không có `setValue()` để đổi nội dung object cũ. Phép gán lại `text` chỉ tạo hoặc trỏ đến một `String` khác trong phạm vi local của advice. **Caller vẫn nhận `String` mà target (hoặc advisor thích hợp khác) quyết định trả về**.

Với collection mutable, `list.add(...)` trong `@AfterReturning` có thể làm caller thấy thêm phần tử; nhưng `list = new ArrayList<>(...)` không thay reference của kết quả. Nếu collection unmodifiable/immutable, cố mutation còn có thể ném exception. Không viết `@AfterReturning` thay dữ liệu kiểu này theo giả định mọi return type đều mutable.

## 6. Muốn thay object trả về thì dùng `@Around`

Theo Spring Framework, `@Around` nhận **`ProceedingJoinPoint`** và bao quanh lời gọi method. Nó có thể:

1. Thực hiện logic trước method.
2. Gọi `proceed()` để cho target (hoặc phần chain bên trong) thực thi.
3. Quan sát object được trả về.
4. Trả về **chính object đó**, object đã wrap, object đã biến đổi, hoặc object thay thế khác cho caller.

Ví dụ fragment:

```java
@Around("execution(* com.example.report.ReportService.load(..))")
public Object replaceResult(org.aspectj.lang.ProceedingJoinPoint pjp)
        throws Throwable {

    ResultBox original = (ResultBox) pjp.proceed();
    ResultBox replacement =
            new ResultBox("wrapped:" + original.getValue());

    return replacement; // Return value mà caller nhìn thấy.
}
```

Ngược với `@AfterReturning`, **giá trị return của `@Around` chính là giá trị Spring proxy trả cho caller**, miễn phù hợp với contract method và các advisors bao ngoài.

Spring cũng cho phép `@Around` không gọi `proceed()` trong những trường hợp có chủ đích như cache hit; tuy nhiên khi mục tiêu là **transform kết quả của method**, thông thường **phải gọi `proceed()` đúng số lần** và xử lý return/exception rõ ràng.

### 6.1. Các ràng buộc quan trọng của `@Around`

- **Đúng kiểu return:** nếu method khai báo trả `ResultBox`, không được trả một `String` bất kỳ rồi mong Java caller hoạt động bình thường. Spring/JVM có thể ném lỗi kiểu khi proxy trả kết quả không tương thích.
- **`null` và primitive:** khi method return primitive, return null thường không hợp lệ; cẩn thận boxing/unboxing.
- **Giữ contract nghiệp vụ:** wrapping/transforming response có thể thay identity, equality và serialization, khiến cache hoặc API consumers thay đổi hành vi.
- **Exception:** `proceed()` có thể ném `Throwable`. Chỉ thay đổi/nuốt exception nếu policy yêu cầu và đã được test.
- **Đi qua Spring proxy:** `@Around` cũng không tự áp dụng cho object được tạo bằng `new` hoặc lời gọi self-invocation `this.method()`.
- **Không phải lúc nào cũng cần `@Around`:** nếu chỉ ghi log/metrics sau thành công, giữ `@AfterReturning` là phù hợp và dễ bảo trì hơn.

## 7. Ví dụ chạy được Java 17+: chứng minh identity và replacement

Ví dụ dùng Spring Framework **6.1.14**, `aspectjweaver` **1.9.22.1**, Maven và JDK 17+. Đây là phiên bản mẫu cố định; code dùng **Spring proxy-based AOP**, không bật AspectJ weaving.

Nó thực hiện ba trường hợp trên **cùng một Spring service proxy**:

1. `loadPlain("A")`: target trả mutable `ResultBox`; `@AfterReturning` **mutate object** rồi gán lại biến local. Caller vẫn nhận reference gốc đã đổi state.
2. `loadWrapped("B")`: `@Around` gọi target rồi **trả về object khác**. Caller thấy reference mới.
3. `message("C")`: `@AfterReturning` quan sát `String` và gán lại biến local. Caller vẫn nhận `String` gốc.

### 7.1. Cấu trúc project

```text
spring-after-returning-limit/
├── pom.xml
└── src/main/java/com/example/returnadvice/
    ├── ResultBox.java
    ├── ReportService.java
    ├── ResultAspect.java
    ├── AppConfig.java
    └── DemoApp.java
```

### 7.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-after-returning-limit</artifactId>
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

`spring-context` quản lý `ApplicationContext` và mang dependency Spring AOP phù hợp với bộ Spring đã chọn; `aspectjweaver` hỗ trợ parser/matching cho `@Aspect` pointcut expressions. Không cần cấu hình `-javaagent` hay AspectJ compiler.

### 7.3. `src/main/java/com/example/returnadvice/ResultBox.java`

```java
package com.example.returnadvice;

public final class ResultBox {
    private String value;

    public ResultBox(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
```

`ResultBox` được thiết kế **mutable**. Class `final` này là **return value**, không phải Spring target bean cần CGLIB subclassing; việc class `ResultBox` là `final` không ngăn proxy bao quanh `ReportService`.

### 7.4. `src/main/java/com/example/returnadvice/ReportService.java`

```java
package com.example.returnadvice;

public class ReportService {
    private ResultBox lastPlain;
    private ResultBox lastWrapped;

    public ResultBox loadPlain(String id) {
        lastPlain = new ResultBox("plain:" + id);
        System.out.println("[TARGET plain] " + lastPlain.getValue());
        return lastPlain;
    }

    public ResultBox loadWrapped(String id) {
        lastWrapped = new ResultBox("source:" + id);
        System.out.println("[TARGET wrapped] " + lastWrapped.getValue());
        return lastWrapped;
    }

    public String message(String id) {
        return "text:" + id;
    }

    public ResultBox rawPlainReference() {
        return lastPlain;
    }

    public ResultBox rawWrappedReference() {
        return lastWrapped;
    }
}
```

Hai method `rawPlainReference()` và `rawWrappedReference()` chỉ để **thí nghiệm so sánh reference** mà target đã tạo; chúng không expose raw target của chính `ReportService`. Ứng dụng thực tế không nên cố giữ mutable response state trong singleton service nếu nhiều thread/request có thể gọi đồng thời. Ví dụ chạy tuần tự để so sánh chính xác hai tham chiếu.

### 7.5. `src/main/java/com/example/returnadvice/ResultAspect.java`

```java
package com.example.returnadvice;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class ResultAspect {
    private final AtomicInteger afterBoxCalls = new AtomicInteger();
    private final AtomicInteger afterTextCalls = new AtomicInteger();
    private final AtomicInteger aroundCalls = new AtomicInteger();

    @AfterReturning(
        pointcut = "execution(public com.example.returnadvice.ResultBox "
                 + "com.example.returnadvice.ReportService.loadPlain(..))",
        returning = "result",
        argNames = "result"
    )
    public void afterPlain(ResultBox result) {
        afterBoxCalls.incrementAndGet();

        // Mutation của chính object target đã trả về:
        result.setValue("observed:" + result.getValue());

        // Chỉ thay đổi local variable trong advice:
        result = new ResultBox("local-only");
        System.out.println("[AFTER box] local=" + result.getValue());
    }

    @AfterReturning(
        pointcut = "execution(public java.lang.String "
                 + "com.example.returnadvice.ReportService.message(..))",
        returning = "text",
        argNames = "text"
    )
    public void afterMessage(String text) {
        afterTextCalls.incrementAndGet();
        text = "rewritten:" + text;
        System.out.println("[AFTER text] local=" + text);
    }

    @Around(
        "execution(public com.example.returnadvice.ResultBox "
        + "com.example.returnadvice.ReportService.loadWrapped(..))"
    )
    public Object aroundWrapped(ProceedingJoinPoint pjp) throws Throwable {
        aroundCalls.incrementAndGet();

        ResultBox source = (ResultBox) pjp.proceed();
        ResultBox replacement = new ResultBox("replaced:" + source.getValue());
        System.out.println("[AROUND] new-reference");
        return replacement;
    }

    public int afterBoxCalls() {
        return afterBoxCalls.get();
    }

    public int afterTextCalls() {
        return afterTextCalls.get();
    }

    public int aroundCalls() {
        return aroundCalls.get();
    }
}
```

Các pointcuts được giới hạn theo **đúng một method** để tránh thứ tự của nhiều advices làm sai phép so sánh:

- `@AfterReturning` số một chỉ chọn `loadPlain`.
- `@AfterReturning` số hai chỉ chọn `message`.
- `@Around` chỉ chọn `loadWrapped`.

Vì chúng **không cùng match một method**, stdout trong demo không phụ thuộc vào thứ tự giữa `@Around` và `@AfterReturning` trên một lời gọi. `AtomicInteger` đo số lần từng advice được thực thi; `argNames` giúp Spring bind tên `result`/`text` một cách tường minh.

### 7.6. `src/main/java/com/example/returnadvice/AppConfig.java`

```java
package com.example.returnadvice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    public ReportService reportService() {
        return new ReportService();
    }

    @Bean
    public ResultAspect resultAspect() {
        return new ResultAspect();
    }
}
```

`@EnableAspectJAutoProxy` bật hỗ trợ Spring `@AspectJ` proxy; `proxyTargetClass=true` dùng CGLIB cho `ReportService` không triển khai business interface. `@Aspect` đơn lẻ không đăng ký bean; `@Bean resultAspect()` làm việc đó.

### 7.7. `src/main/java/com/example/returnadvice/DemoApp.java`

```java
package com.example.returnadvice;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            ReportService service = context.getBean(ReportService.class);
            ResultAspect aspect = context.getBean(ResultAspect.class);

            System.out.println("isAopProxy=" + AopUtils.isAopProxy(service));

            ResultBox plain = service.loadPlain("A");
            System.out.println("plainCaller=" + plain.getValue());
            System.out.println("plainSameReference="
                    + (plain == service.rawPlainReference()));

            ResultBox wrapped = service.loadWrapped("B");
            System.out.println("wrappedCaller=" + wrapped.getValue());
            System.out.println("wrappedSource="
                    + service.rawWrappedReference().getValue());
            System.out.println("wrappedSameReference="
                    + (wrapped == service.rawWrappedReference()));

            String message = service.message("C");
            System.out.println("messageCaller=" + message);

            System.out.println("afterBoxCalls=" + aspect.afterBoxCalls());
            System.out.println("afterTextCalls=" + aspect.afterTextCalls());
            System.out.println("aroundCalls=" + aspect.aroundCalls());
        }
    }
}
```

Tất cả calls đều qua **Spring bean proxy** lấy từ `ApplicationContext`; demo không sử dụng `new ReportService()` bên ngoài Spring. Hai getter trên proxy không có advice nào match nên chỉ trả dữ liệu đang lưu trong raw target.

### 7.8. Chạy Maven và kết quả kỳ vọng

Trong thư mục chứa `pom.xml`, với JDK 17+:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.returnadvice.DemoApp
```

Các dòng được in bởi code ví dụ, theo thứ tự (không bao gồm thông báo Maven/SLF4J nếu có):

```text
isAopProxy=true
[TARGET plain] plain:A
[AFTER box] local=local-only
plainCaller=observed:plain:A
plainSameReference=true
[TARGET wrapped] source:B
[AROUND] new-reference
wrappedCaller=replaced:source:B
wrappedSource=source:B
wrappedSameReference=false
[AFTER text] local=rewritten:text:C
messageCaller=text:C
afterBoxCalls=1
afterTextCalls=1
aroundCalls=1
```

**Đọc kết quả theo từng kỹ thuật:**

1. `isAopProxy=true`: target service do Spring quản lý và đã được bọc bởi AOP proxy.
2. `[TARGET plain]` xảy ra trước `[AFTER box]` vì `@AfterReturning` chỉ thực thi sau khi `loadPlain` trả về bình thường.
3. `[AFTER box] local=local-only` chứng minh phép gán `result = new ResultBox(...)` đã thay local variable bên trong advice. Nhưng `plainCaller=observed:plain:A` chứng minh caller **không nhận object `local-only`**.
4. `plainSameReference=true`: caller nhận **cùng reference** mà target đã return; advice chỉ mutate `value` của object đó.
5. `wrappedSameReference=false`: `@Around` đã tạo và return **reference khác** với object `loadWrapped` gốc. `wrappedSource=source:B` cho thấy source object vẫn giữ state cũ.
6. `messageCaller=text:C`: gán lại biến `text` trong `@AfterReturning` không làm caller nhận `"rewritten:text:C"`.
7. Các counters bằng `1` vì mỗi advice chỉ khớp một trong ba lần gọi đã định.

Điểm học tập cốt lõi nằm ở **so sánh identity bằng `==`**, không chỉ nhìn chuỗi `getValue()`. Một object mutable có thể có nội dung thay đổi mà reference hoàn toàn không đổi.

*Ghi chú xác minh:* lệnh và stdout trên được thiết kế và rà soát theo các Spring API đã nêu; khi dùng trong project thực, nên chạy Maven trên JDK và Spring version mục tiêu để xác nhận môi trường, không suy từ ví dụ rằng các plugin/logging luôn phát sinh cùng thông báo phụ.

## 8. Những trường hợp không nên dùng `@AfterReturning` để “biến đổi kết quả”

### 8.1. Đổi DTO sang DTO khác

Ví dụ service trả `OrderDto`, nhưng muốn thêm một trường và trả một **new immutable DTO**:

```java
@AfterReturning(
    pointcut = "execution(* com.example..OrderService.get(..))",
    returning = "dto"
)
public void after(OrderDto dto) {
    dto = new OrderDto(dto.id(), "audited");
}
```

Đây là **anti-pattern fragment**; `OrderDto` phải được định nghĩa trong ứng dụng. `dto = new ...` chỉ đổi local parameter của advice, không thay object caller nhận. Nếu `OrderDto` là immutable record, cách đúng thường là **service/mapper tạo DTO cần trả**, hoặc `@Around` thực sự `return new OrderDto(...)`.

### 8.2. Mask dữ liệu nhạy cảm

Dùng `@AfterReturning` rồi mutate object đang trả về để che dữ liệu có thể gây tác động phụ nếu object đó còn được giữ trong cache, persistence context hoặc một reference khác. Hướng an toàn hơn thường là tạo **response DTO mới** và return từ một tầng mapping minh bạch; nếu transformation phải là cross-cutting concern, `@Around` có thể phù hợp nhưng cần giới hạn pointcut và kiểm thử bảo mật.

Không coi `@AfterReturning` là cơ chế sanitizer tự động của mọi response: Spring AOP chỉ chặn method đi qua proxy, không tự chặn mọi trường hợp self-invocation hoặc object được tạo ngoài container.

### 8.3. Wrap collection hoặc thay immutable value

- `Collections.unmodifiableList(source)` tạo một **wrapper reference khác** (không nên cố `list = wrapper` trong `@AfterReturning`).
- `List.copyOf(source)` tạo một giá trị collection thích hợp theo contract, không nên giả định nó luôn là cùng reference.
- `String`, `BigDecimal`, Java record bất biến: phải tạo/return giá trị mới nếu muốn thay nội dung.
- Nếu object mutable, mutation trong `@AfterReturning` còn có thể tạo race conditions, lỗi cache hoặc dirty tracking ngoài ý định.

Nếu biến đổi return value là chức năng chính, `@Around` hoặc explicit mapper là sự lựa chọn có hợp đồng rõ hơn.

## 9. `@AfterReturning` có phù hợp với metrics, audit và validation không?

Rất phù hợp khi chỉ cần **quan sát sự kiện thành công**.

| Mục đích | `@AfterReturning` phù hợp? | Ghi chú |
| --- | --- | --- |
| Đếm số lần method trả về bình thường | Có | Advice chạy sau normal return, không bao gồm target ném exception. |
| Ghi event audit về type/ID của kết quả | Có | Cẩn thận thông tin nhạy cảm và `toString()` gây side effects. |
| Ghi metric giá trị trả về | Có | Dùng metadata cần thiết, không mutate object nếu không có yêu cầu. |
| Kiểm tra invariant và log cảnh báo | Có | Việc ném exception từ advice có thể làm caller không nhận normal return; phải là quyết định thiết kế. |
| Thêm một field vào object mutable tại chỗ | Về kỹ thuật có thể | Là state mutation, không phải đổi reference; cân nhắc side effects và concurrency. |
| Thay `ResultBox` bằng object mới | Không | Dùng `@Around` hoặc đổi logic target. |
| Thay `String` return bằng chuỗi khác | Không | `String` immutable; gán lại local không thay return. |
| Trả kết quả từ cache mà không chạy target | Không | `@Around` có thể quyết định không `proceed()`. |
| Can thiệp vào exception của target | Không phải vai trò chính | Dùng `@AfterThrowing` cho quan sát lỗi, `@Around` nếu phải chuyển hóa theo policy. |

Một quy tắc thực dụng của Spring: **chọn loại advice ít quyền nhất đủ đáp ứng nhu cầu**. Nếu chỉ cần kiểm tra kết quả để ghi log, `@AfterReturning` dễ đọc và ít làm thay đổi luồng điều khiển hơn `@Around`. Chỉ nâng lên `@Around` khi thực sự cần thay output, skip/retry method hoặc quản lý hành vi quanh invocation.

## 10. Các trường hợp biên dễ khiến kết luận sai

### 10.1. Method ném exception

`@AfterReturning` không chạy sau khi target method kết thúc bằng exception. Nếu một `@Around` nội bộ bắt exception và trả fallback value, advice `@AfterReturning` ở vị trí thích hợp trong interceptor chain có thể nhìn thấy **normal return từ phần chain phía trong**. Vì vậy nếu có nhiều advisors lồng nhau, cần test trên **chain thực tế**, không chỉ trên một method đơn lẻ.

`@AfterThrowing` dùng để quan sát exception được ném từ join point theo hợp đồng Spring, nhưng nó cũng **không phải** một cơ chế trực tiếp return replacement value. Muốn chuyển exception thành fallback value cho caller, thường cần `@Around` hoặc xử lý explicit ở application boundary.

### 10.2. `void`, primitive và `null`

- Advice `@AfterReturning("execution(* ..SomeService.doWork(..))")` không bind result vẫn có thể phù hợp cho một `void` method hoàn thành bình thường.
- Khi có `returning="result"` và tham số `ResultBox result`, runtime type filter của binding cần được xem xét; method `void` và các giá trị không tương thích sẽ không hành xử như method return `ResultBox`.
- Với `null`, cần test behavior trên Spring version thật và tuyệt đối không gọi `result.getValue()` khi chưa bảo đảm `result` khác null.
- Primitive methods thường liên quan boxing/unboxing tại interceptor boundary; advice với tham số primitive/wrapper cần được kiểm tra trong integration test.
- Nếu `@Around` thay giá trị, hãy trả về kiểu hợp lệ cho declared method signature, đặc biệt tránh `null` cho primitive return.

Đừng dùng `Object` hoặc `ResultBox` trong một advice chung rồi ngầm coi nó là mọi trường hợp `void`, `null`, primitive và generic cùng lúc.

### 10.3. Reactive hoặc asynchronous return types

Với `CompletionStage<T>`/`CompletableFuture<T>` hay `Mono<T>`/`Flux<T>`, target method thường **trả về wrapper/handle** trước khi kết quả bất đồng bộ thực sự hoàn tất hoặc publisher được subscribe. `@AfterReturning` của Spring proxy AOP gắn với **normal return của Java method**, không tự trở thành callback “sau khi HTTP response hoàn tất” hoặc “sau khi future hoàn thành”.

Do đó:

- `@AfterReturning(returning="future")` có thể nhận **`CompletableFuture`**, không phải giá trị `T` sẽ được tính về sau.
- `@AfterReturning` không tự xử lý lỗi phát sinh trong quá trình future hoàn thành hoặc reactive stream phát tín hiệu.
- Muốn transform giá trị bất đồng bộ cần dùng đúng API `thenApply`, Reactor `map`/`onErrorResume`... ở tầng thích hợp; hoặc dùng `@Around` nếu chủ đích phải thay wrapper/reference trả cho caller.
- Phải kiểm tra riêng transaction/security/tracing semantics nếu ứng dụng sử dụng các subsystem Spring có tích hợp async/reactive đặc biệt.

Đây là ranh giới giữa **Java method return** và **asynchronous completion**, không phải một lỗi riêng của biểu thức `returning`.

### 10.4. `@AfterReturning` và `@Around` cùng match một method

Một `@Around` có thể thay return reference. Nếu cả `@AfterReturning` cũng match, **return value mà nó nhìn thấy tùy vào vị trí tương đối của advice trong chain**, không phải luôn luôn object gốc cuối cùng hoặc luôn object thay thế. Dùng `@Order`/Advisor ordering khi cần, đồng thời integration-test thứ tự thật, vì thứ tự của nhiều advices trong cùng aspect có quy tắc riêng.

Không đặt hai transformations cùng trách nhiệm trong nhiều aspects rồi hy vọng chúng áp dụng theo “source order”. Khi mục đích chính là return replacement, nên xác định **một nơi sở hữu transformation policy** để tránh wrap hai lần.

### 10.5. Self-invocation hoặc gọi raw object

```java
public class ReportService {
    public ResultBox outer() {
        return loadPlain("internal");
    }

    public ResultBox loadPlain(String id) {
        return new ResultBox(id);
    }
}
```

Đây là fragment minh họa. Nếu caller gọi `proxy.outer()`, nhưng target tự gọi `this.loadPlain()`, lời gọi nội bộ không đi qua proxy và `@AfterReturning` nhắm `loadPlain` không chạy nhờ interception thông thường. Đổi `@AfterReturning` thành `@Around` **không sửa được self-invocation**; cần đặt đường gọi qua Spring proxy phù hợp hoặc thiết kế lại bean boundary.

## 11. Kiểm thử đúng điều cần chứng minh

Khi xây dựng tests cho return-value advice trong Spring AOP, phải phân biệt:

1. **Có Spring proxy không?** Xác minh `AopUtils.isAopProxy(service)` và context/bean registration.
2. **Pointcut có khớp đúng method không?** Có thể có proxy do advisors khác nhưng advice này không chạy.
3. **Advice được gọi khi nào?** Dùng counter/event để phân biệt normal return với exception exit.
4. **Return reference có đổi không?** Sử dụng `==` trên hai references thích hợp, không dùng chỉ `equals`.
5. **State có đổi không?** Kiểm tra fields/properties của **chính object đã trả về** sau khi advice chạy.
6. **Caller nhìn thấy gì?** Assert trực tiếp return value tại call site, không chỉ log `result` trong advice.
7. **`@Around` có trả đúng type/exception không?** Test primitive, `null`, và các method có overload nếu cần.
8. **Ảnh hưởng side effects:** mutate object có làm đổi cached/shared instance hay entity được quản lý không?
9. **Thread safety:** aspect singleton và target singleton có thể được gọi đồng thời; không lưu per-call state vào mutable instance fields nếu không có cơ chế phù hợp.
10. **Boundary:** test cross-bean invocation qua Spring proxy, không dùng `new` target để kỳ vọng Aspect chạy.

Ví dụ assertions cho demo:

```java
ResultBox plain = service.loadPlain("A");
assert plain == service.rawPlainReference();
assert "observed:plain:A".equals(plain.getValue());

ResultBox wrapped = service.loadWrapped("B");
assert wrapped != service.rawWrappedReference();
assert "replaced:source:B".equals(wrapped.getValue());

String text = service.message("C");
assert "text:C".equals(text);
```

Đây là **test fragment**, giả định có biến `service` là Spring proxy, và Java assertions được enable khi chạy (ví dụ `-ea`). Nếu dùng JUnit, nên dùng `assertSame`/`assertNotSame` để kiểm tra identity và `assertEquals` cho state/value.

Quan trọng: `equals()` có thể được custom override; `assertEquals` không chứng minh return reference giữ nguyên. Khi yêu cầu là thay hay không thay **đối tượng trả về**, phép kiểm identity là bắt buộc.

## 12. Các lỗi phổ biến và cách sửa

| Triệu chứng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Trong advice thấy `result = new Dto(...)` nhưng caller vẫn nhận Dto cũ | Gán lại local parameter trong `@AfterReturning` | Dùng `@Around` return reference mới hoặc tạo DTO ở service/mapper. |
| Advice sửa được field và tưởng đã thay object | State mutation của mutable instance | So `==`, kiểm tra cả reference và state; tránh mutate không chủ đích. |
| `@AfterReturning` không chạy khi target ném lỗi | Nó chỉ chạy khi normal return | Dùng `@AfterThrowing` cho quan sát lỗi hoặc `@Around` khi phải thay exception behavior. |
| Binding `returning="dto"` không resolve | Advice không có parameter `dto`, hoặc `argNames`/metadata sai | Đồng bộ tên binding với parameter advice và kiểm tra signature. |
| Advice dùng `ResultBox` không chạy với method trả kiểu khác | Typed return binding hạn chế matching | Chọn đúng type hoặc `Object` nếu thực sự cần một điểm chặn rộng. |
| `@Around` trả object không đúng kiểu khai báo | Method signature/return contract bị vi phạm | Return object compatible; integration-test callers và exceptions. |
| `@Around` quên gọi `proceed()` và nghiệp vụ không chạy | Advice đã thay luồng điều khiển | Gọi `proceed()` khi transformation cần result gốc; skip chỉ khi được thiết kế rõ. |
| Advice sửa cache/entity/list ngoài dự kiến | Mutable return object đang được chia sẻ | Chọn mapping/immutable return hoặc defensive copy và `@Around`. |
| Cùng method nhưng advice lúc chạy lúc không | Caller đi qua proxy khác nhau, hoặc pointcut/binding type khác | Kiểm tra Spring context, `AopUtils`, pointcut và invocation path. |
| `@AfterReturning` chạy khi method trả future nhưng chưa có dữ liệu | Return của Java method khác completion của future | Dùng async/reactive operators tại đúng tầng; không chờ `@AfterReturning` tự xử lý hoàn tất. |
| Lỗi sau khi thêm cả `@Around` và `@AfterReturning` | Advice ordering/return type mismatch | Giới hạn trách nhiệm, kiểm thử chain và dùng ordering rõ ràng. |
| Muốn thay `String` bằng gán lại tham số advice | `String` immutable và binding local | Dùng `@Around` hoặc đổi logic target để return `String` mới. |

## 13. Chọn giải pháp theo yêu cầu thực tế

**Chỉ muốn biết target đã hoàn thành và trả gì?** Dùng `@AfterReturning` để đo/ghi log/audit, kết hợp typed `returning` khi cần. Giữ advice không có side effects trên return object nếu có thể.

**Chỉ cần xác thực return value, không thay nó?** `@AfterReturning` có thể đọc rồi ghi cảnh báo. Nếu policy thực sự muốn ném lỗi khi vi phạm invariant, cần nêu rõ đây là hành vi làm caller thất bại sau khi method đã chạy; tránh dùng cho silent normalization.

**Muốn trả một immutable DTO/wrapper khác?** Ưu tiên mapping rõ ràng tại tầng service/adapter. Nếu là chính sách ngang nhiều beans và phải can thiệp tại proxy boundary, dùng `@Around` để return object khác có kiểu tương thích.

**Muốn sửa dữ liệu ngay trong mutable object?** Về kỹ thuật `@AfterReturning` có thể làm, nhưng cần quyết định về shared state, persistence dirty tracking, cache consistency, concurrency, ownership và tests. Đây không phải phương án an toàn mặc định chỉ vì code ít hơn `@Around`.

**Muốn thay giá trị một `CompletionStage` hoặc `Mono` khi xử lý xong?** Dùng operator trên asynchronous/reactive API, hoặc `@Around` trả một wrapper đã transform; không nhầm normal return của Java method với completion event.

### Tài liệu tham khảo Spring

- Spring Framework — Declaring Advice (After Returning, Around): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — Advice API and AfterReturningAdvice: https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html
- Spring Framework — AspectJAfterReturningAdvice Javadoc: https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/aspectj/AspectJAfterReturningAdvice.html

**Kết luận:** `@AfterReturning` được thiết kế để **quan sát một lần return thành công**, không phải thay reference trả về. Mutate object mutable chỉ đổi **trạng thái của reference cũ**, còn gán `result = new ...` trong advice không đến được caller. Để thực sự **thay object caller nhận**, chọn `@Around` với `return replacement` và kiểm thử cả type, identity, state và Spring proxy boundary.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-008">Giới hạn của @AfterThrowing khi muốn phục hồi lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: giới hạn của `@AfterThrowing` khi muốn phục hồi lỗi

## 1. Câu hỏi thực tế: ghi nhận lỗi có đồng nghĩa với xử lý lỗi?

Trong Spring Framework, `@AfterThrowing` là một loại advice thuộc `@AspectJ` style. Nó được gọi khi một **method execution được pointcut chọn kết thúc bất thường bằng cách ném exception** trong luồng Spring AOP proxy có thể intercept.

Điều quan trọng nhất: **`@AfterThrowing` có thể quan sát exception, nhưng không phải cơ chế bắt exception rồi trả một kết quả thành công thay cho target.**

```java
@Aspect
@Component
public class FailureAspect {
    @AfterThrowing(
        pointcut = "execution(* demo.recovery.CatalogService.find(..))",
        throwing = "ex"
    )
    public void onFailure(IllegalStateException ex) {
        System.out.println("[afterThrowing] " + ex.getMessage());
        // Kết thúc advice bình thường không làm exception của target biến mất.
    }
}
```

Đây là fragment; imports, service và chương trình hoàn chỉnh ở mục 7. Khi một caller gọi `find("DOWN")` **qua proxy**, method target ném `IllegalStateException`:

1. Spring AOP cho lời gọi đi đến target.
2. Target kết thúc bằng exception phù hợp với `@AfterThrowing`.
3. Advice nhận exception và có thể log, ghi metric, báo lỗi, làm tác vụ bổ trợ.
4. Nếu advice tự hoàn tất bình thường, **exception ban đầu vẫn tiếp tục được truyền ra caller**.
5. `onFailure(...)` là method Java `void`; nó không cung cấp cơ chế `return "DEFAULT"` để thay kết quả của `find(...)`.

Đừng dựa vào tên method như `doRecoveryActions` trong một số ví dụ tài liệu mà suy ra advice này tự chữa lành luồng exception. “Recovery action” có thể là **side effect** (ghi nhật ký/sự kiện/cảnh báo), không phải **biến lời gọi thất bại thành lời gọi thành công**.

Bài tập trung hoàn toàn vào **Spring Aspect, Spring AOP proxy và advice semantics**. Những kiến thức AOP chung hoặc AspectJ bytecode weaving không thuộc phạm vi.

## 2. Vị trí của `@AfterThrowing` trong luồng execution

```text
Caller gọi Spring bean proxy
           |
           v
    Spring AOP proxy
           |
   Advisor/pointcut match
           |
           v
    Target.find("DOWN")
           |
           +-- ném IllegalStateException
                      |
                      v
               @AfterThrowing
               - đọc exception
               - log / alert / metrics
               - chạy xong bình thường
                      |
                      v
              exception đi tiếp
                      |
                      v
              Caller nhận lỗi
```

Nếu method target **trả về bình thường**, `@AfterThrowing` không chạy vì không có exceptional method exit. Điều này đúng cả khi method có logic “xử lý lỗi” nội bộ, miễn cuối cùng nó vẫn return bình thường.

```java
public String find(String key) {
    try {
        return externalStore.load(key);
    } catch (IllegalStateException ex) {
        return "LOCAL-DEFAULT";
    }
}
```

Đoạn trên là fragment giả định có `externalStore`; vì exception được **target tự bắt rồi trả về bình thường**, một advice `@AfterThrowing` áp lên `find()` không được gọi cho execution này. Nếu cần quan sát mọi kết quả, xem `@AfterReturning`, `@After` hoặc `@Around` theo yêu cầu.

### “Lỗi” khác với “trả giá trị biểu thị lỗi”

Nếu target trả `null`, `Optional.empty()`, `Result.failure(...)` hoặc HTTP response báo lỗi **mà không ném exception ra khỏi execution được AOP quan sát**, `@AfterThrowing` sẽ không tự chạy. Nó quan sát **exceptional exit** của join point, không đọc business status trong return value.

## 3. Tại sao không thể dùng `@AfterThrowing` để nuốt exception?

Hãy so sánh ba tình huống sau.

### 3.1. Advice log exception rồi return bình thường

```java
@AfterThrowing(
    pointcut = "execution(* demo.recovery.CatalogService.find(..))",
    throwing = "ex"
)
public void logFailure(IllegalStateException ex) {
    System.out.println("failed: " + ex.getMessage());
}
```

Thân advice return kiểu `void` và kết thúc bình thường. **Target đã ném exception**; Spring không nhận một giá trị thành công mới từ advice. Do đó caller vẫn nhận exception.

Không thể sửa bằng cách thay `void logFailure` thành một method `String logFailure(...)` rồi `return "DEFAULT"`. Đây không phải contract thay thế return value của `@AfterThrowing`. Spring dùng `@Around`, hoặc cơ chế xử lý lỗi ở tầng caller/service, khi cần phục hồi thật sự.

### 3.2. Advice tự ném exception khác

```java
@AfterThrowing(
    pointcut = "execution(* demo.recovery.CatalogService.find(..))",
    throwing = "ex"
)
public void translate(IllegalStateException ex) {
    throw new CatalogUnavailableException("catalog offline", ex);
}
```

Đây là fragment, giả định `CatalogUnavailableException` là exception type thực tế của ứng dụng. Advice có thể làm một exception mới được truyền qua interceptor chain thay cho exception cũ. **Đây vẫn là kết thúc bằng lỗi**, không phải phục hồi thành công.

Nếu cần translation có ý nghĩa ổn định, hãy cân nhắc exception mapping ở service boundary hoặc infrastructure layer phù hợp. Trong Spring, các cơ chế như repository exception translation, transaction và web exception resolution có contract riêng; chúng không đồng nghĩa với `@AfterThrowing` có thể trả fallback.

**Lưu ý quan trọng:** exception mới do advice ném có thể che đi original exception nếu không giữ `cause`. Nếu buộc phải đổi exception, nên truyền original làm cause và chọn exception type tương thích signature/caller contract. Với JDK dynamic proxy, unchecked exception và checked exception không được khai báo có hành vi khác nhau.

### 3.3. Advice bắt exception bằng `try/catch` của chính nó

```java
@AfterThrowing(
    pointcut = "execution(* demo.recovery.CatalogService.find(..))",
    throwing = "ex"
)
public void observe(IllegalStateException ex) {
    try {
        diagnosticService.record(ex);
    } catch (RuntimeException loggingFailure) {
        // Không để lỗi của subsystem quan sát che lỗi nghiệp vụ gốc.
    }
}
```

Đây là fragment sử dụng collaborator `diagnosticService` giả định. `try/catch` bên trong advice chỉ bắt exception phát sinh từ **chính thao tác được gọi trong advice** (ví dụ ghi metric thất bại). Nó không thể quay lại thời điểm trước khi target ném lỗi để “bắt lại” exception của target và thay đổi kết quả invocation.

Dù advice swallow lỗi của `diagnosticService.record(...)`, original exception của target **vẫn đi về caller**.

## 4. `throwing` vừa bind exception vừa giới hạn loại exception

Một declaration:

```java
@AfterThrowing(
    pointcut = "execution(* demo.recovery.CatalogService.find(..))",
    throwing = "ex"
)
public void onIllegalState(IllegalStateException ex) {
    System.out.println("state error: " + ex.getMessage());
}
```

có hai hiệu ứng liên quan:

1. `throwing = "ex"` liên kết giá trị exception với tham số advice tên `ex`.
2. Kiểu tham số `IllegalStateException` làm advice chỉ chạy khi exception thrown **có kiểu phù hợp** (bao gồm subclass phù hợp).

Một `IllegalArgumentException` cũng là `RuntimeException` nhưng **không phải** `IllegalStateException`, nên advice trên không nhận lỗi đó. Nếu muốn quan sát nhiều loại exception, có thể dùng `Throwable`:

```java
@AfterThrowing(
    pointcut = "execution(* demo.recovery.CatalogService.find(..))",
    throwing = "ex"
)
public void observeAll(Throwable ex) {
    System.out.println(ex.getClass().getSimpleName());
}
```

Tuy nhiên, “mọi exception” ở đây vẫn là **những exceptional exits thuộc phạm vi join point/advice mà Spring AOP proxy quan sát được**. Nó không trở thành global exception handler của toàn JVM hoặc của tất cả framework interceptors.

### 4.1. Tên binding phải khớp chính xác

```java
@AfterThrowing(pointcut = "execution(* demo.recovery..*Service.*(..))",
               throwing = "failure")
public void logFailure(Throwable failure) {
    System.out.println(failure.getMessage());
}
```

`throwing="failure"` phải chỉ tới tham số `failure` của method advice. Nếu annotation dùng `throwing="ex"` nhưng method chỉ có tham số tên `failure`, Spring có thể lỗi khi tạo advice/resolve parameter binding.

Các dự án build không giữ Java parameter names có thể cần khai báo `argNames` rõ ràng, ví dụ:

```java
@AfterThrowing(
    pointcut = "execution(* demo.recovery.CatalogService.find(..))",
    throwing = "failure",
    argNames = "failure"
)
public void report(Throwable failure) {
    System.out.println(failure.getMessage());
}
```

Khi thêm `JoinPoint`, nó thường là tham số đầu tiên đặc biệt:

```java
@AfterThrowing(
    pointcut = "execution(* demo.recovery.CatalogService.find(..))",
    throwing = "ex",
    argNames = "ex"
)
public void report(JoinPoint joinPoint, Throwable ex) {
    System.out.println(joinPoint.getSignature().getName()
            + " failed: " + ex.getClass().getSimpleName());
}
```

Hai đoạn là phương án minh họa, không đồng thời dùng trong cùng ví dụ chạy nếu không muốn log lặp.

### 4.2. Advice không phải callback “catch mọi lỗi”

Tài liệu Spring lưu ý `@AfterThrowing` **không phải general exception-handling callback**. Cụ thể, advice này được định nghĩa để nhận exceptional exit từ **user-declared target method (join point)**, không phải cứ một `@After` hay `@AfterReturning` đi cùng ném exception thì mặc định `@AfterThrowing` sẽ được gọi để xử lý nó.

Với nhiều aspects và interceptor chain phức tạp, exception do một advisor khác ném có thể đi qua tầng bao ngoài khác nhau tùy precedence. Vì vậy không xây thiết kế dựa trên giả định “mọi exception ở bất kỳ đâu đều đến đây”. Hãy xác định cụ thể **method đang được advice**, **nguồn exception**, và **thứ tự advisors**.

## 5. Nếu cần thực sự phục hồi: cách làm phù hợp

### 5.1. Xử lý trong target hoặc caller

Nếu fallback là một phần nghiệp vụ của operation, có thể xử lý trực tiếp trong service method:

```java
public String findWithFallback(String key) {
    try {
        return externalStore.load(key);
    } catch (IllegalStateException ex) {
        return "DEFAULT";
    }
}
```

Đây là fragment giả định có `externalStore`. Lời gọi hoàn tất bình thường nên một `@AfterThrowing` trên `findWithFallback()` sẽ **không chạy** cho lỗi đã bị bắt bên trong. Cách này rõ ràng khi fallback thuộc nghiệp vụ và không cần một cross-cutting policy dùng chung.

Tương tự, caller có thể tự dùng `try/catch` bao quanh lời gọi Spring bean proxy và trả kết quả khác, miễn contract cho phép:

```java
try {
    return catalog.find(key);
} catch (IllegalStateException ex) {
    return "DEFAULT";
}
```

Đây là fragment bên trong một method trả `String`; `catalog` là Spring-managed dependency. Khi method target đã ném lỗi, `@AfterThrowing` bên trong proxy thường có cơ hội quan sát lỗi trước khi exception đi đến caller bắt, còn caller chịu trách nhiệm quyết định fallback.

### 5.2. Dùng `@Around` để bao quanh và điều khiển `proceed()`

Trong Spring AOP, `@Around` nhận `ProceedingJoinPoint`; nó có quyền:

- Gọi `proceed()` để thực thi phần còn lại của chain/target.
- Bắt exception ném ra từ `proceed()`.
- Trả về một giá trị fallback **tương thích với kiểu trả về của method**.
- Nếu requirement cho phép, bỏ qua target, retry theo chính sách rõ ràng hoặc thay return value.

```java
@Around("execution(* demo.recovery.CatalogService.findWithFallback(..))")
public Object recover(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (IllegalStateException ex) {
        return "DEFAULT";
    }
}
```

Nếu method `findWithFallback(..)` trả `String`, `"DEFAULT"` là giá trị hợp lệ. Nhưng nếu return type là primitive `int`, `void`, `CompletableFuture<T>` hay một domain-specific result, fallback phải phù hợp contract, nếu không có thể lỗi kiểu/unboxing hoặc làm hỏng semantics.

**Không nhầm `@Around` catch exception với việc transaction tự phục hồi:** Nếu transaction interceptor ở bên trong advice đã đánh dấu rollback-only, bắt exception phía ngoài rồi trả bình thường không có nghĩa transaction có thể commit. Tương tự, trong reactive/asynchronous methods, lỗi có thể xuất hiện trong publisher/future **sau khi method đã return**; `@AfterThrowing` ở method execution boundary không đương nhiên nhận lỗi bất đồng bộ đó.

### 5.3. Chọn retry hay fallback với chính sách rõ ràng

Một retry chỉ hợp lý khi operation **an toàn để thực hiện lại**, có timeout/backoff/circuit breaker phù hợp và không gây duplicate side effects. Không tự gọi `proceed()` lặp nhiều lần trong `@AfterThrowing` vì nó không được cấp `ProceedingJoinPoint`. Nếu cần retries, cân nhắc Spring Retry hoặc Spring Framework resilience facilities phù hợp thay vì viết mã thủ công thiếu safeguards.

Với một lệnh ghi tiền, đặt hàng hay gửi email, retry vô hạn hoặc trả fallback giả “thành công” có thể nguy hiểm hơn ném lỗi. Chọn policy theo **contract nghiệp vụ**; chỉ dùng `@Around` khi có lý do rõ ràng và kiểm thử.

## 6. Bảng so sánh các loại advice trong trường hợp lỗi

| Advice | Khi nào chạy trong Spring AOP? | Có thể làm gì với lỗi? | Có thay exception thành return thành công? |
| --- | --- | --- | --- |
| `@Before` | Trước method execution được proxy intercept | Kiểm tra/log trước; tự ném exception để ngăn execution | Không theo cơ chế `proceed()`; không có return của target |
| `@AfterReturning` | Khi execution kết thúc bình thường | Quan sát return value | Không dùng để return một reference hoàn toàn mới thay target |
| `@AfterThrowing` | Khi execution kết thúc bằng exception phù hợp | Log, metric, alert, quan sát exception; có thể ném exception khác | **Không** — không swallow lỗi hoặc return fallback |
| `@After` | Khi execution kết thúc theo finally semantics | Cleanup bất kể thành công/thất bại | Không phải cơ chế recovery |
| `@Around` | Bao quanh method execution | Catch exception từ `proceed()`, retry/bỏ qua nếu phù hợp, return fallback | **Có thể** nếu contract cho phép và chain/transaction phù hợp |

Ví dụ một advice `@AfterThrowing` ghi log rồi hoàn tất không làm lời gọi trở nên “successful”; để trả thành công cần một điểm **bao quanh** exceptional execution hoặc xử lý lỗi ở caller/target phù hợp.

## 7. Dự án Java 17 + Spring Framework 6.2 chạy được

Ví dụ Spring Framework thuần, không dùng Spring Boot. Hai lời gọi được cố tình tách trên **hai target methods khác nhau** để quan sát rõ:

- `find()` có `@AfterThrowing` cho `IllegalStateException`: advice log nhưng **caller vẫn nhận exception**.
- `findWithFallback()` có `@Around`: advice catch `IllegalStateException` và trả `"DEFAULT"`.
- `findFromInside()` gọi `this.find()` từ target: **bypass Spring proxy**, dù `find()` có pointcut match, nên `@AfterThrowing` **không chạy** cho lời gọi nội bộ.
- `find("OK")` thành công: `@AfterThrowing` không chạy.

### 7.1. Cấu trúc thư mục

```text
spring-after-throwing-recovery/
├── pom.xml
└── src/main/java/demo/recovery/
    ├── AppConfig.java
    ├── CatalogOperations.java
    ├── CatalogService.java
    ├── RecoveryAspect.java
    └── Main.java
```

### 7.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-after-throwing-recovery</artifactId>
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
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
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

### 7.3. `AppConfig.java`

```java
package demo.recovery;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
```

`@ComponentScan` tìm các component trong package `demo.recovery`. `@EnableAspectJAutoProxy` bật Spring AOP auto-proxy creator. Với `CatalogService implements CatalogOperations` và `proxyTargetClass=false`, ví dụ sử dụng **JDK dynamic proxy**.

### 7.4. `CatalogOperations.java`

```java
package demo.recovery;

public interface CatalogOperations {
    String find(String key);
    String findWithFallback(String key);
    String findFromInside(String key);
}
```

Cả ba business methods đều được công bố qua interface để caller có thể gọi trên JDK proxy.

### 7.5. `CatalogService.java`

```java
package demo.recovery;

import org.springframework.stereotype.Service;

@Service
public class CatalogService implements CatalogOperations {
    @Override
    public String find(String key) {
        System.out.println("[target] find " + key);
        if ("DOWN".equals(key)) {
            throw new IllegalStateException("storage unavailable");
        }
        return "ITEM-" + key;
    }

    @Override
    public String findWithFallback(String key) {
        System.out.println("[target] findWithFallback " + key);
        if ("DOWN".equals(key)) {
            throw new IllegalStateException("storage unavailable");
        }
        return "ITEM-" + key;
    }

    @Override
    public String findFromInside(String key) {
        System.out.println("[target] findFromInside " + key);
        try {
            return this.find(key); // Không đi qua Spring AOP proxy.
        } catch (IllegalStateException ex) {
            System.out.println("[target] internal fallback");
            return "LOCAL-DEFAULT";
        }
    }
}
```

Service minh họa ba đường execution:

- `find` có thể ném `IllegalStateException` ra ngoài target.
- `findWithFallback` bản thân target không phục hồi; việc catch sẽ do `@Around` đảm nhận.
- `findFromInside` tự catch lỗi của `this.find()`. Lời gọi đó không đi qua proxy, nên `@AfterThrowing` trên `find()` không nhận nó.

### 7.6. `RecoveryAspect.java`

```java
package demo.recovery;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RecoveryAspect {
    @AfterThrowing(
        pointcut = "execution(* demo.recovery.CatalogService.find(..))",
        throwing = "ex"
    )
    public void onFindFailure(IllegalStateException ex) {
        System.out.println("[afterThrowing] " + ex.getMessage());
    }

    @Around("execution(* demo.recovery.CatalogService.findWithFallback(..))")
    public Object recoverFind(ProceedingJoinPoint pjp) throws Throwable {
        try {
            return pjp.proceed();
        } catch (IllegalStateException ex) {
            System.out.println("[around] recovered " + ex.getMessage());
            return "DEFAULT";
        }
    }
}
```

Hai advices có **pointcuts tách biệt**, không cạnh tranh precedence trên cùng invocation:

- `@AfterThrowing` áp lên `find(..)`, log lỗi và kết thúc bình thường; lỗi **vẫn truyền tới caller**.
- `@Around` áp lên `findWithFallback(..)`, catch exception từ `proceed()` và trả `"DEFAULT"` (hợp lệ vì method trả `String`).
- Cả hai chỉ áp dụng nếu method call đi qua Spring proxy.
- `@Component` làm aspect được đăng ký Spring bean; `@Aspect` một mình chưa đủ.

### 7.7. `Main.java`

```java
package demo.recovery;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            CatalogOperations catalog =
                    context.getBean(CatalogOperations.class);

            System.out.println("jdkProxy="
                    + AopUtils.isJdkDynamicProxy(catalog));

            System.out.println("success=" + catalog.find("OK"));

            try {
                catalog.find("DOWN");
            } catch (IllegalStateException ex) {
                System.out.println("caller caught=" + ex.getMessage());
            }

            System.out.println("fallback="
                    + catalog.findWithFallback("DOWN"));
            System.out.println("internal="
                    + catalog.findFromInside("DOWN"));
            System.out.println("fallbackSuccess="
                    + catalog.findWithFallback("OK"));
        }
    }
}
```

Chạy từ thư mục chứa `pom.xml` bằng Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.recovery.Main
```

Các dòng `System.out` theo thứ tự trong ví dụ (không tính log Maven/Spring riêng nếu có):

```text
jdkProxy=true
[target] find OK
success=ITEM-OK
[target] find DOWN
[afterThrowing] storage unavailable
caller caught=storage unavailable
[target] findWithFallback DOWN
[around] recovered storage unavailable
fallback=DEFAULT
[target] findFromInside DOWN
[target] find DOWN
[target] internal fallback
internal=LOCAL-DEFAULT
[target] findWithFallback OK
fallbackSuccess=ITEM-OK
```

### 7.8. Phân tích kết quả

| Thao tác | Target | `@AfterThrowing` | `@Around` | Caller nhận |
| --- | --- | --- | --- | --- |
| `catalog.find("OK")` | Return `"ITEM-OK"` | Không chạy | Không áp dụng | `"ITEM-OK"` |
| `catalog.find("DOWN")` | Ném `IllegalStateException` | Log `storage unavailable` | Không áp dụng | **Exception** |
| `catalog.findWithFallback("DOWN")` | Ném exception | Không match pointcut này | Catch và return `"DEFAULT"` | **Giá trị thành công `"DEFAULT"`** |
| `catalog.findFromInside("DOWN")` | Bắt lỗi nội bộ từ `this.find()` | Không thấy nested `find` vì self-invocation | Không áp dụng | `"LOCAL-DEFAULT"` |
| `catalog.findWithFallback("OK")` | Return `"ITEM-OK"` | Không match | Trả nguyên kết quả `proceed()` | `"ITEM-OK"` |

**Đọc hai dòng quan trọng:**

```text
[afterThrowing] storage unavailable
caller caught=storage unavailable
```

Chúng chứng minh advice đã **quan sát lỗi** nhưng chưa **phục hồi**. Ngược lại:

```text
[around] recovered storage unavailable
fallback=DEFAULT
```

là một example về **catch + return fallback** trong around advice.

Không có lời gọi bất đồng bộ, không có transactions và hai advices chọn hai method khác nhau, nên ví dụ không phụ thuộc thứ tự chạy giữa những advices khác nhau trên cùng một join point.

## 8. Vì sao `@AfterThrowing` có thể không chạy dù đã xảy ra lỗi?

### 8.1. Target catch lỗi rồi return bình thường

```java
public String find(String key) {
    try {
        return gateway.load(key);
    } catch (IllegalStateException ex) {
        return "CACHED";
    }
}
```

Fragment giả định có `gateway`. Method execution `find` kết thúc bình thường, nên `@AfterThrowing` **không kích hoạt** cho exception đã bị target bắt. Nếu muốn advice xử lý lỗi xảy ra **bên trong method đã được catch**, phải đổi nơi đặt boundary hoặc event/reporting contract; Spring proxy không có khả năng theo dõi mọi lệnh `throw` được catch trong target.

### 8.2. Ngoại lệ không khớp kiểu tham số

```java
@AfterThrowing(pointcut = "execution(* demo.recovery..*Service.*(..))",
               throwing = "ex")
public void observe(IllegalStateException ex) {
    // Chỉ bắt các exception phù hợp kiểu IllegalStateException.
}
```

Nếu method ném `IllegalArgumentException`, advice này không nhận. Muốn quan sát nhiều loại, cân nhắc `Throwable`, nhưng tránh log nhạy cảm hoặc đánh đồng mọi exception với lỗi nghiệp vụ.

### 8.3. Lời gọi không đi qua Spring AOP proxy

```java
new CatalogService().find("DOWN");
```

Đây là object tự tạo, không phải Spring bean proxy; advice của Spring AOP **không chạy**. Tương tự, self-invocation `this.find("DOWN")` từ target không tạo interception mới.

### 8.4. Pointcut sai signature, package hoặc bean

Một expression `execution(* demo.other.CatalogService.find(..))` không khớp target ở `demo.recovery`. Hoặc class aspect chỉ có `@Aspect` nhưng chưa được đăng ký Spring bean qua `@Component`/`@Bean`. Khi đó Spring không có advisor đúng để chạy `@AfterThrowing`.

### 8.5. Phương thức không được proxy intercept

Với JDK proxy, method phải thuộc các public interface methods mà proxy công bố. Với CGLIB, `private`/`final` methods không thể được advice theo cơ chế override và `final class` không thể subclass. `static` method không được gọi qua instance proxy.

### 8.6. Exception xuất hiện sau khi method return

Một method trả `CompletableFuture<T>` hoặc reactive `Mono<T>` có thể **return bình thường** một wrapper trong khi lỗi chỉ xảy ra khi future hoàn tất hoặc publisher được subscribe. `@AfterThrowing` cho method execution thông thường không tự biết về mọi lỗi xảy ra sau thời điểm return. Muốn xử lý lỗi async/reactive, dùng API thích hợp như completion handlers, Reactor error operators hoặc hỗ trợ framework tương ứng.

### 8.7. Một advice khác đã phục hồi lỗi trước khi exception tới điểm quan sát

Khi nhiều advisors cùng áp dụng, một `@Around` có thể bắt exception từ phần chain bên trong và trả bình thường; advisor bên ngoài chỉ thấy kết quả bình thường. Tùy precedence, exception có thể đã được một advice bên trong quan sát trước khi outer advice recover. Không đưa ra quy tắc tuyệt đối như “`@AfterThrowing` luôn chạy trước/luôn chạy sau `@Around`” nếu chưa xét cụ thể advisor ordering.

## 9. Giới hạn khi kết hợp với transactions, retries và web errors

### 9.1. `@Transactional` và rollback

Spring declarative transaction thường được triển khai bằng interceptor. Nếu method ném exception đi qua transaction interceptor, Spring có thể đánh dấu rollback theo quy tắc rollback, rồi một outer `@Around` mới catch exception và trả fallback. **Catch phía ngoài không làm rollback flag tự biến mất.**

Nếu catch trước khi exception vượt qua transaction boundary, transaction interceptor có thể chỉ thấy return bình thường; tuy nhiên transaction đã có thể bị marked rollback-only ở những luồng khác. Khi cần rollback/recovery rõ ràng, đặt transaction boundary và policy thứ tự advisor có chủ đích, thêm integration tests kiểm tra trạng thái commit/rollback thật.

Đặc biệt, không dùng một `@AfterThrowing` chỉ để tuyên bố “đã xử lý lỗi nên transaction sẽ commit”. Nó chỉ là advice theo exceptional execution và không khôi phục giao dịch đã rollback.

### 9.2. Retry: rủi ro side effects

Nếu muốn tự động gọi lại operation, `@AfterThrowing` không có `ProceedingJoinPoint` nên không điều khiển lại target execution. Dùng một cơ chế retry phù hợp (ví dụ Spring Retry khi dự án dùng) hoặc `@Around` nhưng bảo đảm:

- operation là idempotent hoặc có cơ chế chống trùng;
- giới hạn số lần, backoff, timeout;
- logging/metrics tránh trùng lặp khó kiểm soát;
- transaction boundaries phù hợp với từng lần thử;
- exception cuối cùng có contract rõ ràng.

Đừng tự động retry một method chuyển tiền/ghi đơn hàng chỉ vì nhìn thấy exception trong aspect.

### 9.3. HTTP exception response không phải Spring AOP recovery

Trong Spring MVC, một lỗi từ service có thể được xử lý ở ranh giới web bằng `@ExceptionHandler`/`@ControllerAdvice` hoặc các cơ chế xử lý exception khác. Điều đó chuyển **exception thành HTTP response** cho client, nhưng không có nghĩa `@AfterThrowing` của service đã return fallback cho service caller.

Các trách nhiệm này khác nhau:

- Spring AOP `@AfterThrowing`: quan sát **exceptional method execution**.
- Spring MVC exception resolver: chuyển lỗi ứng dụng thành **web response** thích hợp.
- Service/caller/`@Around`: có thể quyết định **domain fallback** nếu contract cho phép.

## 10. Checklist chẩn đoán

| Triệu chứng | Nguyên nhân có thể | Hướng giải quyết |
| --- | --- | --- |
| Advice in log lỗi nhưng caller vẫn nhận exception | Đây là hành vi đúng của `@AfterThrowing` | Nếu cần fallback, xử lý ở caller/target hoặc `@Around` |
| Thử `return "DEFAULT"` trong after-throwing method | Không phải contract thay return của advice này | Dùng `@Around` và kiểu return đúng |
| Advice ném exception khác thì không còn thấy lỗi gốc | Exception của advice có thể che lỗi ban đầu | Truyền original làm cause, giữ exception contract |
| `@AfterThrowing` không chạy với `IllegalArgumentException` | Tham số bind kiểu `IllegalStateException` lọc exception | Sửa kiểu ràng buộc hoặc xử lý exception tương ứng |
| `@AfterThrowing` không chạy khi target đã catch | Execution return bình thường | Di chuyển error-handling boundary |
| `@AfterThrowing` không chạy ở `this.method()` | Self-invocation bypass proxy | Tách method sang bean khác và inject |
| `@AfterThrowing` không chạy trên object `new` | Đối tượng ngoài Spring container | Dùng Spring-managed bean |
| Advice không thấy exception từ một `@AfterReturning` khác | `@AfterThrowing` không phải global exception callback | Xét đúng join point/source/ordering |
| `@AfterThrowing` không thấy lỗi của `CompletableFuture` | Method đã return bình thường trước lúc future fail | Dùng completion/error handlers |
| `@Around` trả fallback nhưng transaction vẫn rollback | Transaction boundary đã xử lý exception hoặc rollback-only | Thiết kế advisor/transaction ordering và test rollback |
| Dùng `Throwable` nhưng log lỗi bị lặp | Nhiều aspects, retries, nhiều proxy boundaries | Phân định một nơi log/observability, tránh double count |
| `@AfterThrowing` chạy nhầm phương thức | Pointcut quá rộng hoặc bean target khác | Thu hẹp expression, kiểm tra bean/target |
| Không có Spring proxy | Aspect/target chưa là Spring beans, auto-proxy chưa bật | Kiểm tra `@Aspect @Component` và `@EnableAspectJAutoProxy`/Boot AOP |

### Quy trình kiểm tra có hệ thống

1. Xác minh target và aspect cùng được Spring quản lý; hạ tầng Spring AOP đã kích hoạt.
2. Trên reference thực tế từ DI/context, xác minh `AopUtils.isAopProxy(...)`.
3. Xác minh method được caller gọi qua proxy, không phải `this` hoặc object tạo bằng `new`.
4. Kiểm tra pointcut và kiểu exception của `throwing` binding.
5. Phân biệt exception **thoát khỏi target method** với exception đã được target catch hoặc async error sau return.
6. Kiểm tra `@AfterThrowing` có chỉ log hay có ném exception mới.
7. Nếu muốn caller nhận fallback thành công, chọn điểm xử lý exception phù hợp: target, caller hoặc `@Around`.
8. Với transactions, retries, async, web exceptions, test behavior ở boundary tương ứng, không suy đoán từ một dòng log advice.

## 11. Câu hỏi tự đánh giá

**1. `@AfterThrowing` log exception rồi return bình thường; caller nhận gì?** Caller vẫn nhận exception từ target (trừ khi một interceptor ở tầng khác xử lý nó). Advice không tự chuyển exception thành return thành công.

**2. Có thể khai báo method `@AfterThrowing` trả `String` để thay return value không?** Không. Advice này không có contract điều khiển `proceed()`/return kết quả của join point; dùng `@Around` khi cần phục hồi.

**3. Vì sao `@AfterThrowing(..., throwing="ex")` với `IllegalStateException ex` không chạy khi target ném `IllegalArgumentException`?** Vì kiểu exception tham số advice giới hạn matching.

**4. Nếu method target tự catch và return `"CACHED"`, `@AfterThrowing` trên method đó có chạy không?** Không. Execution đã kết thúc bình thường; exception không thoát ra ngoài join point.

**5. Sự khác biệt giữa `@Around` và `@AfterThrowing` về recovery?** `@Around` có thể catch exception từ `proceed()` rồi trả fallback hợp lệ; `@AfterThrowing` chủ yếu quan sát exceptional execution và không tự chuyển thành success.

**6. Nếu `@AfterThrowing` ném exception mới, đó có phải recovery không?** Không. Caller vẫn nhận lỗi, dù loại lỗi có thể khác; cần giữ original cause khi chuyển đổi exception.

**7. Tại sao self-invocation `this.find()` không được `@AfterThrowing` chặn?** Lời gọi không qua Spring AOP proxy, dù pointcut có thể match method signature.

**8. `@AfterThrowing` có tự bắt mọi exception của một `@AfterReturning` đi cùng không?** Không nên giả định. Spring nêu rõ nó không phải callback xử lý exception tổng quát; cần xét nguồn exception và advisor chain.

**9. Một `CompletableFuture` thất bại sau khi method đã return sẽ tự kích hoạt `@AfterThrowing` của method đó không?** Không cho một method execution đã trả future bình thường; cần error-handling mechanism của async/future.

## Tài liệu Spring chính thức

- [Spring Framework — Declaring Advice, After Throwing Advice và Around Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Spring Framework — AOP Concepts: After Throwing Advice](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html)
- [Spring Framework — Spring AOP Advice API (Throws Advice)](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html)
- [Spring Framework — Proxying Mechanisms và self-invocation](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- [Spring Framework — Declarative Transaction Rollback](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-009">Chữ ký phương thức advice và cấu hình returning, throwing</a>

<details>
<summary>Xem chi tiết</summary>

# Chữ ký phương thức advice và cấu hình returning, throwing trong Spring AOP

## 1. Bài toán và kết quả cần nắm

Với **Spring AOP dùng cú pháp @AspectJ**, class có `@Aspect` khai báo các advice như `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing` và `@Around`. Chữ ký Java của **phương thức advice** quyết định Spring có thể truyền cho advice những dữ liệu nào: thông tin `JoinPoint`, argument của target, return value, exception hoặc quyền `proceed()`.

Hai thuộc tính đặc biệt:

- **`returning = "result"`** của `@AfterReturning`: đưa **giá trị trả về khi target kết thúc bình thường** vào một tham số advice tên `result`; **kiểu tham số** cũng ràng buộc tập kết quả được chọn.
- **`throwing = "error"`** của `@AfterThrowing`: đưa **exception thoát ra khỏi target method execution** vào một tham số advice tên `error`; **kiểu tham số** ràng buộc loại exception được chọn.

Đây không phải hai tham số đặt tên tùy ý. Chúng tham gia vào **binding và matching** của Spring @AspectJ; thiếu tên hoặc tên/kiểu không phù hợp có thể khiến advice không áp dụng hoặc ứng dụng lỗi khi tạo advisor.

**Điểm mấu chốt:** `returning` và `throwing` không thuộc Java method signature của *target*; chúng là metadata trên annotation của *advice* và phải khớp **tham số Java của advice**.

Bài chỉ tập trung Spring Aspect: đăng ký Spring-managed aspect bean, Spring proxy, cú pháp advice, parameter binding, kết quả thực tế, lỗi cấu hình và kiểm thử. Không lặp lại lý thuyết AOP chung hay dùng AspectJ bytecode weaving.

## 2. Phân biệt method của target và method của advice

Hai methods dưới đây làm hai nhiệm vụ khác nhau:

```java
// Method của Spring target bean
public String find(String id) {
    return "found:" + id;
}
```

```java
// Method advice nằm trong Spring-managed @Aspect
@AfterReturning(
    pointcut = "execution(public String com.example.advice.OutcomeService.find(..))",
    returning = "result",
    argNames = "result"
)
public void afterFind(String result) {
    System.out.println("result=" + result);
}
```

Đây là các đoạn minh họa rút gọn; muốn chạy thực tế cần đúng packages/imports, config và bean registration.

| Thành phần | Ở đâu? | Ý nghĩa |
| --- | --- | --- |
| `String find(String id)` | Target service | Khai báo nghiệp vụ: input `id`, output `String`. |
| `execution(...)` | Pointcut trên advice | Chọn target method executions có thể được Spring proxy intercept. |
| `returning = "result"` | Attribute của `@AfterReturning` | Bind giá trị trả về vào tham số advice tên `result`. |
| `String result` | Chữ ký Java của advice | Spring truyền return value tương thích `String`; type này còn giới hạn matching. |
| `void afterFind(...)` | Chữ ký Java của advice | Advice phục vụ quan sát; giá trị trả về từ advice không thay giá trị target trả. |
| `argNames = "result"` | Annotation của advice | Làm tên biến binding tường minh khi cần. |

Mục tiêu của `@AfterReturning` là chạy **sau normal return**, không cần đặt tên advice giống target method. Method advice có thể được đặt tên `observeResult`, `recordSuccess`, `afterFind` hoặc tên Java hợp lệ bất kỳ.

### 2.1. Hai nguồn giới hạn matching

Một lời advice muốn chạy cần đáp ứng **cả hai**:

1. **Pointcut expression**: chọn đúng execution của method Spring-managed target.
2. **Binding condition** (nếu có): `returning`/`throwing` và kiểu tham số advice tương thích với outcome.

Vì thế, **hai advice dùng cùng pointcut** có thể chạy số lần khác nhau nếu một advice dùng `returning = "text"` với `String text`, còn advice kia không bind kết quả hoặc dùng `Object result`.

## 3. Bảng chữ ký advice thường dùng trong Spring

| Annotation | Chữ ký advice Java thông dụng | Data được bind | Điều kiện execution |
| --- | --- | --- | --- |
| `@Before("...")` | `public void before()` | Không cần tham số | Ngay trước invocation match. |
| `@Before("...")` | `public void before(JoinPoint jp)` | Metadata method/args, proxy/target | Khi join point sắp được gọi. |
| `@After("...")` | `public void afterFinally()` | Có thể lấy `JoinPoint` | Sau khi join point thoát, bình thường hoặc exception. |
| `@AfterReturning("...")` | `public void afterNormal()` | Không bind return | Method thoát bình thường. |
| `@AfterReturning(pointcut="...", returning="value")` | `public void afterReturn(String value)` | Giá trị trả về kiểu tương thích `String` | Normal return và type binding phù hợp. |
| `@AfterThrowing("...")` | `public void afterThrow()` | Không bind exception | Method thoát bằng exception. |
| `@AfterThrowing(pointcut="...", throwing="error")` | `public void afterThrow(IllegalArgumentException error)` | Exception tương thích `IllegalArgumentException` | Throwing exit đúng kiểu exception. |
| `@Around("...")` | `public Object around(ProceedingJoinPoint pjp) throws Throwable` | Quyền gọi `proceed()` và quyết định kết quả | Bao quanh invocation. |

Các chữ ký là **mẫu phổ biến**, không phải khẳng định chỉ có đúng một chữ ký hợp lệ cho mỗi advice. Spring @AspectJ hỗ trợ binding argument, annotation, `this`/`target` và `JoinPoint` khi expression/tham số tương ứng.

**Quan trọng:** Spring docs khuyên dùng **dạng advice ít quyền nhất đủ đáp ứng yêu cầu**. Nếu chỉ đọc exception, dùng `@AfterThrowing`; nếu chỉ đọc return, dùng `@AfterReturning`. Đừng thay mọi advice thành `@Around` nếu không có nhu cầu quyết định khi nào/how target được thực thi.

## 4. @AfterReturning: hai chữ ký khác nhau

### 4.1. Không có returning: chỉ quan sát normal completion

```java
@AfterReturning(
    "execution(public * com.example.advice.OutcomeService.*(..))"
)
public void afterAnyNormalReturn() {
    normalReturns.incrementAndGet();
}
```

- `@AfterReturning` chạy khi method được chọn **kết thúc bình thường**.
- Không có `returning` nên advice **không nhận giá trị** để inspect.
- Tập method được xét rộng: một `void` method cũng có thể kết thúc bình thường và được tính.
- Nếu target ném exception thoát ra khỏi method, advice này không chạy.

Với `@AfterReturning`, “return normally” là **outcome của method execution**, không tự động đồng nghĩa “đơn hàng được chấp nhận”, “HTTP status 2xx”, “transaction đã commit” hoặc “future hoàn tất thành công”.

### 4.2. Có returning: nhận return value

```java
@AfterReturning(
    pointcut = "execution(public * com.example.advice.OutcomeService.*(..))",
    returning = "value",
    argNames = "value"
)
public void afterString(String value) {
    System.out.println("text=" + value);
}
```

- `value` sau `returning` **chỉ tới** tham số Java `String value` của advice.
- `String` là một **ràng buộc kiểu**; advice không nhận kết quả `Integer` hoặc `void` như thể đó là `String`.
- Nếu dùng `Object value`, matching rộng hơn đối với những normal return values tương thích kiểu `Object`.
- `argNames` tạo ánh xạ tên explicit, tránh phụ thuộc vào build flags/thông tin tên tham số Java.
- Để nhận thêm `JoinPoint`, khai báo nó **đầu danh sách tham số** theo convention Spring; không dùng `ProceedingJoinPoint` ở advice thông thường.

### 4.3. Tên returning phải khớp tên tham số

Ví dụ sai:

```java
@AfterReturning(
    pointcut = "execution(* com.example.advice.OutcomeService.*(..))",
    returning = "data",
    argNames = "result"
)
public void wrongAdvice(String result) {
}
```

Đây là **đoạn minh họa cấu hình sai**, không thêm vào một context cần chạy thành công. `returning` nói `data`, còn declaration/`argNames` cung cấp `result`; Spring/AspectJ không có binding hợp lệ cho `data`, có thể lỗi khi khởi tạo advice/advisor.

Cách đúng có thể là:

```java
@AfterReturning(
    pointcut = "execution(* com.example.advice.OutcomeService.*(..))",
    returning = "result",
    argNames = "result"
)
public void correctAdvice(String result) {
    // ...
}
```

Không dùng tên method của target, tên field hay một alias tự nghĩ để điền `returning`. Dùng **tên tham số advice** sẽ nhận return value.

## 5. @AfterThrowing: hai chữ ký khác nhau

### 5.1. Không có throwing: sự kiện exceptional exit

```java
@AfterThrowing(
    "execution(public * com.example.advice.OutcomeService.*(..))"
)
public void afterAnyThrownExit() {
    failures.incrementAndGet();
}
```

Advice không có tham số này có thể ghi một failure event khi target method execution được chọn **thoát ra bằng exception**. Nó không biết exception object là gì nếu không bind dữ liệu tương ứng.

Nó **không chạy** khi target tự bắt lỗi và return fallback bình thường. `@AfterThrowing` quan sát **lỗi thoát khỏi join point**, không phải mọi exception từng được ném trong call stack.

### 5.2. Có throwing: nhận exception và lọc theo subtype

```java
@AfterThrowing(
    pointcut = "execution(public * com.example.advice.OutcomeService.*(..))",
    throwing = "problem",
    argNames = "problem"
)
public void afterIllegalArgument(IllegalArgumentException problem) {
    System.out.println("badArg=" + problem.getMessage());
}
```

- `throwing="problem"` bind exception vào **`problem`** của method advice.
- `IllegalArgumentException` giới hạn matching cho instance của kiểu đó (hoặc subtype).
- Nếu muốn chọn nhiều lỗi hơn, có thể dùng `RuntimeException`, `Exception` hoặc `Throwable`, tùy semantics và test.
- Trong Java, `IllegalArgumentException extends RuntimeException`; method ném `IllegalStateException` không match binding `IllegalArgumentException`.
- Tham số exception advice **không phải** object error response và không phải kiểu return value. `throwing` không thay cho `returning`.

### 5.3. @AfterThrowing không phải try/catch handler tổng quát

`@AfterThrowing` **quan sát** một exceptional return. Nó không nhận một `ProceedingJoinPoint` để `proceed()` hoặc tự chuyển một lỗi thành bình thường chỉ bằng return.

Nếu chỉ log exception rồi advice kết thúc bình thường, exception gốc vẫn đi tiếp đến caller. Nếu advice **tự ném exception khác**, caller có thể nhận exception mới thay cho exception gốc — là thay đổi behavior nguy hiểm, không nên dùng để “xử lý lỗi”.

Theo tài liệu Spring, `@AfterThrowing` tập trung vào lỗi từ **user-declared target join point**, không phải một callback bắt mọi exception phát sinh từ một advice `@After`/`@AfterReturning` đồng hành trong cùng aspect.

**Nếu muốn phục hồi hoặc trả fallback cho caller**, hãy xử lý trong business boundary hoặc dùng `@Around` với một `try/catch` thực sự và contract rõ ràng. Đừng gắn `@AfterThrowing` rồi kỳ vọng return của advice thay thế lỗi.

## 6. Các đặc điểm Java method signature cần hiểu

### 6.1. Return type của method advice

Các advice kiểu `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing` **nên khai báo `void`** vì return từ chính advice không được Spring dùng để thay kết quả business method.

`@Around` khác: thường dùng `Object` return type, nhận `ProceedingJoinPoint` và dùng `proceed()`. Giá trị `return` từ `@Around` chính là giá trị mà caller có thể nhìn thấy khi interceptor chain hoàn tất.

```java
@Around("execution(* com.example.advice.OutcomeService.find(..))")
public Object aroundFind(ProceedingJoinPoint pjp) throws Throwable {
    Object result = pjp.proceed();
    return result;
}
```

Đây là đoạn minh họa, cần import `org.aspectj.lang.ProceedingJoinPoint`, nằm trong Spring-managed `@Aspect`. Nếu return từ `@Around` không tương thích với declared return type của target method, ứng dụng có thể lỗi runtime.

### 6.2. Visibility của advice method

Advice methods trong ví dụ để `public`, giúp dễ đọc và giảm rủi ro truy cập khi Spring gọi chúng qua framework infrastructure. Đây là **visibility của method chứa logic advice**, **không phải** visibility của Spring target method trong `execution(public * ...)`.

Không nhầm với visibility của một **named `@Pointcut` method** dùng để tái sử dụng biểu thức. `@Pointcut` có thể được định nghĩa `private`/`protected`/`public` theo phạm vi reuse; điều đó không trực tiếp biến target method thành private hay public.

### 6.3. JoinPoint có thể đứng đầu method advice

```java
@Before("execution(public * com.example.advice.OutcomeService.*(..))")
public void beforeCall(JoinPoint jp) {
    System.out.println(jp.getSignature().getName());
}
```

`JoinPoint` đến từ `org.aspectj.lang.JoinPoint`. Spring có thể truyền metadata của intercepted method execution, target và args.

Ví dụ kết hợp `JoinPoint` và returning:

```java
@AfterReturning(
    pointcut = "execution(public String com.example.advice.OutcomeService.find(..))",
    returning = "result",
    argNames = "result"
)
public void afterFind(JoinPoint jp, String result) {
    System.out.println(jp.getSignature().getName() + "=" + result);
}
```

**Lưu ý:** tham số `JoinPoint` đầu tiên có binding đặc biệt; khi dùng `argNames`, Spring docs cho phép bỏ qua tên `JoinPoint` đầu tiên trong danh sách `argNames`. Không lấy `JoinPoint` làm argument business method.

`JoinPoint.getArgs()` là các arguments được chuyển đến invocation; đó là dữ liệu có thể chứa thông tin nhạy cảm. Log theo whitelist/redaction, không log password/token chỉ vì có thể truy cập.

### 6.4. ProceedingJoinPoint chỉ dành cho @Around

```java
@Around("execution(* com.example.advice.OutcomeService.*(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    return pjp.proceed();
}
```

Các advice ngoài `@Around` không nên khai báo `ProceedingJoinPoint` rồi kỳ vọng có thể `proceed()`. Mục tiêu của `@Before`/`@AfterReturning`/`@AfterThrowing` là observation ở thời điểm được định nghĩa, không thay thế call flow.

### 6.5. Một method advice không dùng cả returning và throwing

`@AfterReturning` có `returning`, `@AfterThrowing` có `throwing`. Chúng là **hai annotations khác nhau**, với outcomes **loại trừ nhau cho cùng một execution**: method kết thúc bình thường hoặc exception thoát ra.

Không tồn tại cấu hình `@AfterReturning(returning="result", throwing="error")` theo Spring @AspectJ. Khi cần ghi cả hai nhánh, dùng **hai advice methods** hoặc `@Around` tùy mục tiêu.

## 7. Java 17+ demo đầy đủ: 6 lời gọi, 6 phép đếm advice

Demo này dùng **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**, **Java 17+**, Gradle và Spring Java configuration thuần. Ví dụ kiểm chứng cùng một pointcut trên nhiều methods nhưng binding khác nhau.

Để output đáng tin, advice **chỉ tăng counters** (không in trong advice). Vì thế chương trình không phụ thuộc thứ tự thực thi của những advice cùng join point.

Mục tiêu:

- `@Before` đếm tất cả sáu method executions qua proxy.
- `@After` đếm sáu lần kết thúc theo after-finally.
- `@AfterReturning` không bind đếm bốn normal returns.
- `@AfterReturning` bind `String` đếm hai text returns.
- `@AfterThrowing` bind `IllegalArgumentException` đếm một lỗi đúng subtype.
- `@AfterThrowing` bind `Throwable` đếm hai exceptional exits.

### 7.1. Thư mục dự án

```text
spring-advice-signatures-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/advice/
    ├── Main.java
    ├── AppConfig.java
    ├── OutcomeService.java
    └── OutcomeAspect.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-advice-signatures-demo'
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
    mainClass = 'com.example.advice.Main'
}
```

Spring Framework 6.x yêu cầu Java 17+. Với Spring Boot production, để BOM/dependency management của Boot chọn các versions Spring đồng bộ thay vì dùng lại thủ công các version của demo.

### 7.2. Target bean: các outcomes tách biệt

**`OutcomeService.java`**

```java
package com.example.advice;

public class OutcomeService {
    public String find(String id) {
        return "found:" + id;
    }

    public Integer count() {
        return 3;
    }

    public void refresh() {
        System.out.println("target:refresh");
    }

    public String rejected() {
        throw new IllegalArgumentException("bad-id");
    }

    public String crashed() {
        throw new IllegalStateException("storage-down");
    }

    public String recovered() {
        try {
            throw new IllegalArgumentException("transient");
        } catch (IllegalArgumentException ex) {
            return "fallback";
        }
    }
}
```

Tất cả methods đều `public`, không final, và class không final. Do đó các case được tách theo **outcome** và **binding** chứ không bị lẫn với hạn chế CGLIB final/private.

`recovered()` minh họa một exception đã bị bắt **bên trong target**: kết quả cuối cùng của method execution là normal return `"fallback"`, vì vậy `@AfterThrowing` trên `recovered` **không** chạy.

### 7.3. Aspect: signatures khác nhau trên cùng pointcut

**`OutcomeAspect.java`**

```java
package com.example.advice;

import java.util.concurrent.atomic.AtomicInteger;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class OutcomeAspect {
    private static final String SERVICE =
            "execution(public * com.example.advice.OutcomeService.*(..))";

    private final AtomicInteger before = new AtomicInteger();
    private final AtomicInteger afterFinally = new AtomicInteger();
    private final AtomicInteger normal = new AtomicInteger();
    private final AtomicInteger textReturn = new AtomicInteger();
    private final AtomicInteger illegalArgument = new AtomicInteger();
    private final AtomicInteger anyThrowable = new AtomicInteger();

    @Before(SERVICE)
    public void beforeMethod(JoinPoint jp) {
        before.incrementAndGet();
    }

    @After(SERVICE)
    public void afterMethod() {
        afterFinally.incrementAndGet();
    }

    @AfterReturning(SERVICE)
    public void afterNormal() {
        normal.incrementAndGet();
    }

    @AfterReturning(
        pointcut = SERVICE,
        returning = "value",
        argNames = "value"
    )
    public void afterString(String value) {
        textReturn.incrementAndGet();
    }

    @AfterThrowing(
        pointcut = SERVICE,
        throwing = "failure",
        argNames = "failure"
    )
    public void afterBadArgument(IllegalArgumentException failure) {
        illegalArgument.incrementAndGet();
    }

    @AfterThrowing(
        pointcut = SERVICE,
        throwing = "error",
        argNames = "error"
    )
    public void afterAnyFailure(Throwable error) {
        anyThrowable.incrementAndGet();
    }

    public int beforeCount() { return before.get(); }
    public int afterCount() { return afterFinally.get(); }
    public int normalCount() { return normal.get(); }
    public int textCount() { return textReturn.get(); }
    public int argumentErrorCount() { return illegalArgument.get(); }
    public int throwableCount() { return anyThrowable.get(); }
}
```

Các biến counter là **instance fields trong một aspect bean**, nên dùng `AtomicInteger` để tránh mất cập nhật nếu multiple threads gọi service. Đây chỉ là instrumentation demo trong một JVM, **không** thay thế metric registry/telemetry đa instance production.

Tất cả pointcuts dùng chung chuỗi `SERVICE`, do đó sự khác nhau của counters phản ánh **event semantics** và **ràng buộc returning/throwing type**.

**Vì sao `JoinPoint jp` không xuất hiện trong `argNames` của `@Before`?** Nó là tham số đặc biệt, Spring/AspectJ cung cấp theo quy tắc advice signature. Trong ví dụ, `jp` chưa được sử dụng để in signature, nhưng việc truyền vào cho thấy một advice có thể truy cập metadata join point. Nếu code style bật compiler warning cho tham số không dùng, có thể xóa tham số và import `JoinPoint` mà không thay các phép đếm; ở bản demo giữ nguyên để minh họa chữ ký.

### 7.4. Đăng ký Spring bean và bật @AspectJ

**`AppConfig.java`**

```java
package com.example.advice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    OutcomeService outcomeService() {
        return new OutcomeService();
    }

    @Bean
    OutcomeAspect outcomeAspect() {
        return new OutcomeAspect();
    }
}
```

`@Aspect` không tự động tạo Spring bean; `@Bean` trên `AppConfig` tạo instance `OutcomeAspect` được Spring quản lý. `@EnableAspectJAutoProxy` nhận diện advice metadata và thiết lập AOP proxies cho target beans match advisor.

`proxyTargetClass=true` khiến main tra cứu `OutcomeService` bằng concrete type được. Đây là lựa chọn cho demo, không phải điều kiện để `returning`/`throwing` hoạt động; có thể dùng JDK interface proxy nếu target expose suitable interface và Spring được cấu hình tương ứng.

### 7.5. Chạy calls và kiểm chứng số lần advice

**`Main.java`**

```java
package com.example.advice;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            OutcomeService service = context.getBean(OutcomeService.class);
            OutcomeAspect aspect = context.getBean(OutcomeAspect.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println("find=" + service.find("X"));
            System.out.println("count=" + service.count());
            service.refresh();

            try {
                service.rejected();
            } catch (IllegalArgumentException ex) {
                System.out.println("caught=" + ex.getMessage());
            }

            try {
                service.crashed();
            } catch (IllegalStateException ex) {
                System.out.println("caught=" + ex.getMessage());
            }

            System.out.println("recovered=" + service.recovered());

            System.out.println("before=" + aspect.beforeCount());
            System.out.println("normal=" + aspect.normalCount());
            System.out.println("string=" + aspect.textCount());
            System.out.println("illegalArgument=" + aspect.argumentErrorCount());
            System.out.println("anyThrowable=" + aspect.throwableCount());
            System.out.println("afterFinally=" + aspect.afterCount());
        }
    }
}
```

Chạy với Gradle đã cài và JDK 17+:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper thì dùng `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows.

**Stdout do chương trình in, theo thứ tự** (không tính log JVM/Spring/Gradle do môi trường bật):

```text
proxy=true
find=found:X
count=3
target:refresh
caught=bad-id
caught=storage-down
recovered=fallback
before=6
normal=4
string=2
illegalArgument=1
anyThrowable=2
afterFinally=6
```

### 7.6. Giải thích theo từng invocation

| Lời gọi qua Spring proxy | Outcome của target method | `@Before` | `@AfterReturning` không bind | `@AfterReturning` bind String | `@AfterThrowing` bind IllegalArgumentException | `@AfterThrowing` bind Throwable | `@After` |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `find("X")` | Return `"found:X"` | 1 | 1 | 1 | 0 | 0 | 1 |
| `count()` | Return `Integer(3)` | 1 | 1 | 0 | 0 | 0 | 1 |
| `refresh()` | `void`, normal exit | 1 | 1 | 0 | 0 | 0 | 1 |
| `rejected()` | Throw `IllegalArgumentException` | 1 | 0 | 0 | 1 | 1 | 1 |
| `crashed()` | Throw `IllegalStateException` | 1 | 0 | 0 | 0 | 1 | 1 |
| `recovered()` | Catch nội bộ, return `"fallback"` | 1 | 1 | 1 | 0 | 0 | 1 |
| **Tổng** | Sáu method executions | **6** | **4** | **2** | **1** | **2** | **6** |

Đây là **các giá trị tổng sau khi tất cả lời gọi hoàn tất**, không phải số lượng object bean, số HTTP requests hay số transaction commits. Cùng một execution ném `IllegalArgumentException` có thể được **cả hai @AfterThrowing advice** quan sát vì `IllegalArgumentException` cũng là `Throwable`. Advice thứ hai không phải retry invocation của target.

Lưu ý: không khóa thứ tự giữa hai `@AfterThrowing` advices; chúng chỉ thay đổi counters độc lập nên stdout đã chọn không phụ thuộc order.

## 8. Binding thêm method argument với args(...)

Nhiều ứng dụng muốn log input và kết quả hoặc input và exception của **cùng lời gọi**. Spring @AspectJ hỗ trợ binding target args trong pointcut bằng `args(...)`.

### 8.1. Input và return value

```java
@AfterReturning(
    pointcut =
        "execution(public String com.example.advice.OutcomeService.find(..))"
        + " && args(id)",
    returning = "result",
    argNames = "id,result"
)
public void afterFind(String id, String result) {
    System.out.println("find(" + id + ")=" + result);
}
```

Đây là **đoạn minh họa** nằm trong Spring-managed `@Aspect`. `args(id)` bind target argument vào `String id`. `returning="result"` bind normal return vào `String result`. `argNames="id,result"` nói rõ tên hai advice parameters.

Với `find("X")`, nếu thêm advice này thành bean riêng hoặc tạm thay code demo để chạy độc lập, nó có thể in:

```text
find(X)=found:X
```

**Đừng thêm advice này nguyên trạng vào demo muốn giữ stdout ở mục 7.5.** Nó tạo thêm output.

Nếu method có nhiều args, có thể dùng `args(id,..)` để bind tham số đầu hoặc `args(first,second)` với method có đúng hai arguments, tùy matching mong muốn. Số lượng/kiểu Java advice parameters phải tương ứng với các tên binding.

### 8.2. Input và exception

```java
@AfterThrowing(
    pointcut =
        "execution(public String com.example.advice.OutcomeService.find(..))"
        + " && args(id)",
    throwing = "error",
    argNames = "id,error"
)
public void afterFindFailure(String id, RuntimeException error) {
    System.out.println("failed:" + id + ":" + error.getClass().getSimpleName());
}
```

Đây cũng là **minh họa**. `find(..)` trong demo không ném lỗi cho input `"X"`, nên lời advice này **không chạy** ở case đó. Nếu target được thay đổi để ném exception phù hợp, và expression match, Spring mới bind `RuntimeException error`.

Điểm chính: **returning** và **throwing** bind hai kinds of outcomes khác nhau. Muốn dùng cả hai data sources, viết hai advices có cùng `args` nhưng khác outcome annotation, hoặc dùng `@Around` khi thực sự cần một interception body xử lý cả hai đường.

## 9. Kiểu tham số binding tạo ra ràng buộc matching

### 9.1. Object, String, Number và void

```java
// Chọn các normal returns có kiểu tương thích Object.
@AfterReturning(
    pointcut = "execution(* com.example.advice.OutcomeService.*(..))",
    returning = "result",
    argNames = "result"
)
public void afterObject(Object result) {
    // ...
}
```

```java
// Chọn các normal returns có kiểu tương thích String.
@AfterReturning(
    pointcut = "execution(* com.example.advice.OutcomeService.*(..))",
    returning = "result",
    argNames = "result"
)
public void afterText(String result) {
    // ...
}
```

Hai đoạn riêng ở trên **không** được ghép vào demo `OutcomeAspect` nếu muốn giữ các counters. Chúng cho thấy **`String` là điều kiện chặt hơn `Object`** đối với return binding. Method `void` không có `String` value; nếu chỉ cần đếm `void` normal completion, dùng `@AfterReturning` không bind.

Với generic/covariant return types, bridge methods, `null` và target declared return type `Object` nhưng runtime value thay đổi, matching có thể có chi tiết phụ thuộc cách Spring/AspectJ kiểm tra type. **Hãy test phiên bản Spring cụ thể** trước khi dùng typed-returning advice làm policy nghiệp vụ quan trọng.

### 9.2. Throwable, Exception và RuntimeException

```java
@AfterThrowing(
    pointcut = "execution(* com.example.advice.OutcomeService.*(..))",
    throwing = "error",
    argNames = "error"
)
public void afterFailure(Throwable error) {
    // Có thể quan sát RuntimeException, checked Exception hoặc Error theo matching.
}
```

`Throwable` là broad type, phù hợp khi cần telemetry các exceptional exits. `Exception` không bắt các `Error` như `OutOfMemoryError`; `RuntimeException` chỉ match unchecked runtime exceptions và subclasses. `IllegalArgumentException` là một subclass cụ thể.

**Không lạm dụng catch-all metrics để nuốt errors.** Advice `@AfterThrowing` không phải nơi chữa `OutOfMemoryError` hay đưa exception fatal thành normal business outcome.

### 9.3. Null từ target

Một method:

```java
public String optionalName() {
    return null;
}
```

kết thúc **bình thường**, nên `@AfterReturning` không bind được dùng nếu pointcut match. Với returning-bound reference type, **đọc result bằng null-safe logic** vì invocation có thể tạo `null`. Không gọi `result.length()` vô điều kiện.

Nếu khái niệm cần kiểm tra là “có dữ liệu” thay vì “method hoàn tất bình thường”, nên dùng `Optional`, business result object hoặc policy trong service; không gán mặc định `@AfterReturning` bằng nghiệp vụ thành công.

### 9.4. Future và reactive return

Spring @AspectJ advice nhìn vào **method execution được proxy**. Với `CompletableFuture<T>` hoặc `Mono<T>`, target có thể **trả future/publisher wrapper bình thường** ngay cả khi tác vụ/subscription sau đó lỗi.

Khi ấy:

- `@AfterReturning(returning="value")` bind **future/publisher object** (nếu parameter type match), không tự bind `T` sau khi computation hoàn tất.
- `@AfterThrowing` trên method proxy **không tự chạy** cho lỗi bất đồng bộ phát sinh sau khi method đã trả wrapper thành công.
- Nếu cần completion semantics, dùng completion callback như `whenComplete` cho future hoặc Reactor operators/instrumentation phù hợp; đừng đồng nhất wrapper return với eventual result.

## 10. Chữ ký quyết định dữ liệu được bind, không quyết định quyền thay outcome

| Advice signature | Điều Spring có thể cung cấp |
| --- | --- |
| void onReturn(String value), returning=value | Normal result tương thích String; không đổi reference trả caller |
| void onFailure(IOException error), throwing=error | Exception tương thích IOException; không trả fallback |
| Object around(ProceedingJoinPoint pjp) throws Throwable | Có quyền proceed rồi trả kết quả hoặc ném lỗi phù hợp contract |

Phần phân tích object identity, mutable state và replacement thuộc bài riêng về @AfterReturning; phần phục hồi exception thuộc bài riêng về @AfterThrowing. Thay return type của advice thông thường thành String hoặc Object không biến nó thành @Around.

## 11. Tách lỗi tên binding khỏi lỗi type matching

Với returning=result, argNames=result và advice method nhận String result, cả ba phải nhất quán. Nếu chỉ sửa argNames thành value, việc ánh xạ sai tên dù pointcut đúng; đây có thể là lỗi tạo advisor/binding.

Tương tự, throwing=failure với Throwable failure nhận loại exception rộng, còn IOException failure chỉ nhận IOException hoặc subtype. Khi test nên tách ba điều kiện: đúng target/pointcut, đúng tên tham số và đúng return/exception type. Trong một lời gọi ném lỗi, returning advice không chạy dù tham số return có kiểu hợp lệ.

## 12. Bài toán JoinPoint và binding annotation

Spring @AspectJ cũng hỗ trợ bind annotation, receiver/target và method args.

### 12.1. Advice chỉ cần tên method

```java
@Before("execution(public * com.example.advice.OutcomeService.*(..))")
public void before(JoinPoint jp) {
    String name = jp.getSignature().getName();
    System.out.println("enter:" + name);
}
```

Một `JoinPoint` đầu tiên là hợp lệ đối với non-around advice. Không dùng `ProceedingJoinPoint` ở đây vì `@Before` không điều khiển tiến trình `proceed()`.

### 12.2. Advice cần annotation của target method

Giả sử có annotation:

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedOperation {
    String category();
}
```

Đoạn minh họa cần Java imports `ElementType`, `Retention`, `RetentionPolicy`, `Target` và được đặt ở package thích hợp.

Binding:

```java
@Before(
    value = "@annotation(marker)",
    argNames = "marker"
)
public void beforeAudited(AuditedOperation marker) {
    System.out.println(marker.category());
}
```

Advice nhận `AuditedOperation` từ annotation trên method. `argNames` khớp tham số `marker`. Đây **không phải** `returning`/`throwing`, nhưng cùng mô hình binding theo tên/type của Spring @AspectJ. Đừng trộn annotation trên class (`@within`/`@target`) với annotation trên method (`@annotation`) nếu pointcut yêu cầu khác.

### 12.3. Khi nào cần argNames?

Spring có cơ chế khám phá argument names từ:

- Annotation `argNames` trên advice/`@Pointcut` khi được khai báo explicit.
- Java parameter name metadata nếu build giữ lại thông tin thích hợp.
- Các quy tắc suy luận binding của Spring @AspectJ trong một số trường hợp.

Để README/demo độc lập, `argNames` giúp code ít phụ thuộc build flags. Nhưng `argNames` không **cứu** một pointcut sai cú pháp hoặc type mismatch. Khi có nhiều parameters, thứ tự names phải tương ứng các parameters bind của advice.

Spring đặc biệt nhận diện `JoinPoint` tham số đầu tiên mà không cần kể tên nó trong `argNames`. Với `@Around`, `ProceedingJoinPoint` là tham số đầu tiên tương ứng.

## 13. Nhiều advice trên cùng một target: thứ tự và ngoại lệ

Một service method có thể đồng thời được:

- Spring-managed custom aspects audit/metrics;
- Transaction/cache/security proxy interceptors;
- Hai hay nhiều advice thuộc một `@Aspect`;
- `@Around` từ các frameworks khác.

**Advice ordering có thể ảnh hưởng loại outcome mà một advisor nhìn thấy.** `@Order`/`Ordered` giúp xác định precedence giữa các aspects, nhưng không nên suy rằng thứ tự khai báo code trong file luôn là thứ tự chạy giữa hai advice cùng loại.

Spring docs lưu ý:

- Cùng một join point, precedence quyết định thứ tự “vào” và “ra”.
- `@After` có nghĩa after-finally; nó có thể chạy sau `@AfterReturning`/`@AfterThrowing` theo semantics cleanup.
- Hai advice **cùng loại trong cùng aspect** không được đảm bảo có thứ tự cố định chỉ dựa theo vị trí source.
- `@AfterThrowing` của một aspect không phải callback bắt exception do `@AfterReturning`/`@After` đồng hành ném ra.

Ví dụ demo cố tình dùng **counters, không in log bên trong advice** để tránh khóa thứ tự giữa hai `@AfterThrowing` advice. Khi cần sequence chính xác để đáp ứng business policy, tách aspects có precedence rõ ràng hoặc sử dụng một advice body có điều khiển minh bạch, kèm integration test.

## 14. Spring Boot: điểm giống và điều cần kiểm tra

Với Spring Boot AOP auto-configuration khi dependencies phù hợp, thường không cần tự thêm `@EnableAspectJAutoProxy`. Nhưng quy tắc chữ ký advice **không thay đổi**:

1. Aspect có `@Aspect` **và** phải được Spring đăng ký làm bean qua `@Component` (nằm trong component scan) hoặc `@Bean`.
2. Target service là Spring-managed bean.
3. Advice annotations và `returning`/`throwing` names trùng advice parameters.
4. Type binding khớp normal return hoặc thrown exception.
5. Caller thực sự dùng Spring proxy; không dùng `new` hoặc self-invocation `this.method()`.
6. Spring Boot proxy configuration/JDK vs CGLIB không làm final methods trở nên interceptable khi dùng CGLIB.

Ví dụ dependencies theo Boot major (giả sử project dùng dependency management của Boot):

```gradle
// Boot 3.x
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

```gradle
// Boot 4.x
implementation 'org.springframework.boot:spring-boot-starter-aspectj'
```

Đây là **hai lựa chọn theo major version**, không chép cả hai vào cùng project. Boot 3/4 có thể khác nhau về API chuyển tiếp, nhưng `@AfterReturning` và `@AfterThrowing` vẫn thuộc `org.aspectj.lang.annotation` trong Spring @AspectJ support.

Nếu Boot đặt `spring.aop.proxy-target-class=true`, Spring thường dùng class proxy; cần kiểm tra method/class `final`, package scan và cấu hình AOP tổng hợp.

## 15. Lỗi phổ biến về chữ ký advice và binding

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| Startup lỗi khi thêm `returning` | String `returning` không match tên tham số advice, hoặc `argNames` sai | Dùng tên giống nhau và types hợp lệ. |
| Startup lỗi khi thêm `throwing` | Không có parameter name được bind, hoặc annotation/pointcut sai | `throwing="error"` và `Throwable error`/subtype phù hợp. |
| `@AfterReturning` không bind vẫn chạy, bản bind `String` không chạy | Target trả kiểu khác `String`, `void` hoặc có mismatch | Kiểm tra declared/actual result types, dùng type chủ đích và integration test. |
| Advice `@AfterThrowing(IllegalArgumentException)` không chạy trên `IllegalStateException` | Kiểu exception không tương thích | Dùng subtype matching đúng yêu cầu, hoặc `RuntimeException`/`Throwable` rộng hơn. |
| Method ném lỗi nội bộ nhưng `@AfterThrowing` không chạy | Target bắt lỗi trước khi thoát join point | Quan sát outcome cuối cùng; nếu return fallback thì đó là normal return. |
| Một method có hai exception advices chạy | `IllegalArgumentException` cũng là `Throwable` | Không gọi là advice bị lặp; đó là hai advisors/bindings hợp lệ. |
| `@AfterReturning` không đổi kết quả dù advice trả/assign chuỗi khác | Advice này không thay returned reference | Dùng `@Around` hoặc sửa business method nếu cần đổi kết quả. |
| `@AfterThrowing` chỉ log nhưng exception vẫn được caller nhận | Đây là semantics đúng | Nếu cần recovery, dùng explicit handler hoặc `@Around` với contract đúng. |
| Advice chỉ thấy future/publisher thay vì data cuối cùng | Interception ở lúc method trả wrapper, không phải async completion | Theo dõi completion/reactive signal bằng API tương ứng. |
| `JoinPoint` đứng sai vị trí hoặc `argNames` sai thứ tự | Binding không phù hợp convention | Đưa `JoinPoint` đầu tiên; đặt names cho các biến binding còn lại. |
| `ProceedingJoinPoint` dùng trên `@Before` | `proceed()` là quyền của `@Around` | Đổi sang `JoinPoint` hoặc thực sự dùng `@Around`. |
| Có aspect bean nhưng advice không được gọi | AOP chưa bật, pointcut không match, target không qua proxy hoặc final/private | Kiểm tra bean/proxy/method boundary và actual outcome. |
| Advice trên phương thức inner không chạy từ outer | `this.inner()` bỏ qua proxy | Tách sang collaborator bean có injected proxy nếu thật sự cần interception. |
| Transaction rollback/commit không như kỳ vọng | Advice order và exception handling tác động chain | Test transaction với config thực; không suy từ normal-return log. |
| Advice tự ném exception và business call bắt đầu fail | Lỗi trong advice đi qua proxy tới caller | Thiết kế error policy cho telemetry/audit rõ ràng, tránh nuốt/đổi outcome bất ngờ. |

## 16. Một quy trình kiểm thử Spring AOP có ý nghĩa

### 16.1. Cấu hình context thật

Viết integration test sử dụng `AnnotationConfigApplicationContext`, Spring Test `@SpringJUnitConfig` hoặc `@SpringBootTest` đúng với project. Unit test gọi trực tiếp `new OutcomeAspect().afterString(...)` **không chứng minh Spring binding hoạt động**.

### 16.2. Kiểm tra proxy trước

```java
OutcomeService service = context.getBean(OutcomeService.class);
System.out.println(AopUtils.isAopProxy(service));
```

Trong demo kết quả `true`. Nhưng **proxy true không bảo đảm mọi advice method match**. Tiếp tục kiểm tra event/counters riêng cho các outcomes.

### 16.3. Kiểm chứng tất cả nhánh

- Normal String return → `afterNormal` và `afterString`.
- Normal Integer return → `afterNormal`, không có `afterString`.
- Normal void completion → `afterNormal`, không có `afterString`.
- IllegalArgumentException → `afterBadArgument` và `afterAnyFailure`.
- IllegalStateException → chỉ `afterAnyFailure`.
- Exception bị catch trong target rồi return fallback → normal-return advice, **không** exceptional advice.
- External Spring proxy call vs `new`/`this` internal call → xác nhận boundary.

### 16.4. Tự viết assertion không phụ thuộc stdout advice

```java
try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
    OutcomeService service = context.getBean(OutcomeService.class);
    OutcomeAspect aspect = context.getBean(OutcomeAspect.class);

    service.find("X");
    service.count();
    service.refresh();
    try { service.rejected(); } catch (IllegalArgumentException ignored) {}
    try { service.crashed(); } catch (IllegalStateException ignored) {}
    service.recovered();

    if (aspect.beforeCount() != 6
            || aspect.normalCount() != 4
            || aspect.textCount() != 2
            || aspect.argumentErrorCount() != 1
            || aspect.throwableCount() != 2
            || aspect.afterCount() != 6) {
        throw new AssertionError("Advice binding or outcome counts differ");
    }
}
```

Đoạn này dùng các class của demo và cần import `AnnotationConfigApplicationContext`; nó chạy **trên một context mới**, không ghép vào `Main` nếu muốn giữ output mẫu. Trong project thực tế có thể thay `if` bằng JUnit assertions.

### 16.5. Đối với dữ liệu nhạy cảm và concurrency

- Đừng log toàn bộ `JoinPoint.getArgs()` hoặc result DTO chứa token, password, thông tin thanh toán.
- Aspect mặc định thường là singleton Spring bean, nên field mutable phải thread-safe hoặc tốt hơn là stateless.
- `AtomicInteger` chỉ bảo vệ một phép đếm; nó không bảo vệ một tập hợp nhiều fields cập nhật cùng nhau.
- Nếu advice gọi IO/remote service, cần timeout/error policy và quan sát performance tác động lên method call.
- Với `@AfterThrowing`, đừng bắt tất cả `Throwable` rồi dùng nó để quyết định “lỗi nào nên nuốt”. Binding quan sát không phải recovery policy.

## 17. Checklist cuối cùng

- [ ] Đã xác định rõ **chữ ký của target method** và **chữ ký của advice method** là hai thứ khác nhau?
- [ ] Aspect có `@Aspect` và được Spring đăng ký bean qua `@Component` hoặc `@Bean`?
- [ ] Hạ tầng Spring AOP @AspectJ và AspectJ Weaver được cấu hình đúng?
- [ ] `@Before`/`@After`/`@AfterReturning`/`@AfterThrowing` thường khai báo `void`?
- [ ] `@Around` có `ProceedingJoinPoint` đầu tiên, gọi `proceed()` phù hợp, return `Object` hoặc kiểu hợp lệ?
- [ ] Nếu dùng `@AfterReturning(returning="value")`, có tham số advice tương ứng `value`?
- [ ] Nếu dùng `@AfterThrowing(throwing="error")`, có tham số exception tương ứng `error`?
- [ ] Kiểu tham số `String`/`Throwable`/subtype có đúng tập outcomes cần match?
- [ ] Nếu dùng `argNames`, tên/thứ tự tương thích với các binding parameters và quy tắc đặc biệt của `JoinPoint`?
- [ ] Đã phân biệt normal return, caught exception, thrown exception, `void`, `null` và async-wrapper return?
- [ ] Không dùng `@AfterReturning` để thay returned reference hoặc `@AfterThrowing` để nuốt exception?
- [ ] Target thực sự đi qua Spring proxy; method proxy intercept được, không phải self-invocation?
- [ ] Nếu có nhiều advice, không phụ thuộc ordering ngẫu nhiên của hai advices cùng loại?
- [ ] Integration test xác nhận return bindings và exception bindings trên actual Spring-managed calls?
- [ ] Telemetry/advice có null-safety, privacy và error policy rõ ràng?

**Điểm nhớ:** Trong Spring AOP, `returning` của `@AfterReturning` và `throwing` của `@AfterThrowing` **phải trỏ đúng tham số của method advice**. Java type của tham số **lọc kết quả/exception được match**, còn `argNames` giúp Spring xác định binding tường minh. `JoinPoint` cung cấp metadata, `ProceedingJoinPoint` dành cho `@Around`. Mọi advice chỉ phát huy tác dụng khi **Spring bean, proxy và pointcut** đúng, và việc quan sát outcome không tự cho phép thay kết quả hoặc xử lý lỗi.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Declaring Advice: @AfterReturning, @AfterThrowing, @Around, advice parameters và ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — Proxying Mechanisms / self-invocation](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — Advice API (phân biệt @AspectJ và chương trình hóa advisors)](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-lifecycle-010">Ví dụ ghi nhận lời gọi trên bean Spring với từng loại advice</a>

<details>
<summary>Xem chi tiết</summary>

# Ví dụ ghi nhận lời gọi trên Spring bean bằng từng loại advice

## 1. Mục tiêu bài học

Trong Spring Framework, một `@Aspect` có thể khai báo advice để **ghi nhận việc gọi phương thức của Spring bean** ở nhiều thời điểm: trước lời gọi, khi trả về thành công, khi ném ngoại lệ, khi kết thúc bất kể kết quả và trong toàn bộ quá trình thực thi. Các loại tương ứng:

- `@Before` — trước method execution.
- `@AfterReturning` — khi method execution hoàn thành bình thường.
- `@AfterThrowing` — khi method execution thoát bằng exception phù hợp.
- `@After` — sau method execution theo ngữ nghĩa *finally*.
- `@Around` — bao quanh method execution; có thể tự quyết định có gọi tiếp hay không qua `ProceedingJoinPoint.proceed()`.

Bài tập trung vào **cách dùng năm loại advice trong Spring AOP**, Spring-managed beans, pointcut áp dụng và log quan sát được. Không trình bày lại lý thuyết Aspect tổng quát hay AspectJ weaving ngoài Spring.

**Điều quan trọng đầu tiên:** Spring AOP là cơ chế **proxy-based**. Mọi trace trong bài được tạo khi một client gọi method **trên Spring bean proxy**, không phải khi gọi object được tạo thủ công bằng `new` ở ngoài container.

## 2. Một lời gọi qua Spring AOP proxy diễn ra thế nào?

```text
Client
  |
  | gọi method trên bean nhận từ ApplicationContext
  v
Spring AOP proxy
  |
  | chọn advisor/interceptor có pointcut khớp method
  v
Advice chain
  |
  | chạy logic BEFORE/AROUND ở nhánh vào
  v
Target.method()
  |
  | normal return hoặc exception
  v
Advice chain
  |
  | AFTER_RETURNING / AFTER_THROWING / AFTER / AROUND tương ứng
  v
Client nhận result hoặc exception
```

Trong Spring, `@Aspect` class phải được **đăng ký thành Spring bean** để các advice được nhận diện (ví dụ `@Component` được quét hoặc `@Bean` trong cấu hình). Với Spring Framework thuần, có thể bật xử lý `@AspectJ` bằng `@EnableAspectJAutoProxy`.

Khi aspect có pointcut:

```java
@Before("execution(* demo.service..*.*(..))")
public void beforeAnyServiceCall() {
    System.out.println("BEFORE service");
}
```

Spring không chèn mã vào mọi class theo cách compile-time weaving. Nó tạo proxy cho các target beans có advisors phù hợp; **lời gọi đi qua proxy** mới kích hoạt advice. Snippet trên chỉ là một method cần đặt trong Spring-managed `@Aspect` bean, không phải chương trình Java độc lập.

### Năm loại advice trong một bảng

| Loại advice | Sự kiện nhận biết | Có thể quan sát | Có quyền gọi `proceed()`? |
| --- | --- | --- | --- |
| `@Before` | Trước method target | Join point signature, arguments, proxy/target | Không |
| `@AfterReturning` | Method trả bình thường | Return value qua `returning` | Không |
| `@AfterThrowing` | Method ném ngoại lệ phù hợp | Throwable qua `throwing` | Không |
| `@After` | Sau method, dù thành công/lỗi | Cleanup/log kết thúc | Không |
| `@Around` | Bao quanh toàn bộ thực thi còn lại | Trước, sau, return hoặc exception | **Có**, qua `ProceedingJoinPoint` |

**Chọn loại advice đơn giản nhất đáp ứng mục tiêu.** Nếu chỉ cần in một dòng trước method, ưu tiên `@Before` thay vì `@Around`, vì around advice có thể vô tình chặn luôn lời gọi nếu quên `proceed()`.

## 3. Ví dụ 1 — ghi nhận trước lời gọi với `@Before`

Một `@Before` advice chạy **trước khi method được chọn thực thi**.

```java
@Aspect
public class CallTraceAspect {
    @Before("execution(* demo..*.beforeCase(..))")
    public void beforeCall(JoinPoint jp) {
        System.out.println("BEFORE " + jp.getSignature().getName());
    }
}
```

Trong đoạn minh họa này, `JoinPoint.getSignature().getName()` cho biết tên method tại join point; có thể lấy `jp.getArgs()` để xem tham số khi cần.

**Ví dụ đường thành công:**

```text
BEFORE beforeCase
TARGET beforeCase false
CLIENT value=BEFORE-OK
```

**Ví dụ đường lỗi:**

```text
BEFORE beforeCase
TARGET beforeCase true
CLIENT caught IllegalStateException:before failed
```

Điểm cần nhớ:

1. `@Before` chạy trước target **ở cả nhánh thành công và nhánh thất bại**, nếu lời gọi đã đi qua proxy và match pointcut.
2. Advice có thể ném exception làm lời gọi dừng lại; nhưng nó không dùng `proceed()` để quyết định phần còn lại như `@Around`.
3. Nếu target tự gọi `this.beforeCase(...)` trong nội bộ, lời gọi đó không tự vào lại proxy; do đó không có một lần `@Before` mới cho riêng lời gọi nội bộ.
4. Khi log arguments, tránh đưa mật khẩu, access token hoặc dữ liệu nhạy cảm vào log.

Các code blocks ngắn ở từng mục là **mã minh họa**, còn ví dụ Maven hoàn chỉnh có thể chạy được đặt ở mục 8.

## 4. Ví dụ 2 — chỉ ghi khi thành công với `@AfterReturning`

`@AfterReturning` chạy khi **method execution kết thúc bình thường**, kể cả method trả `null` (với binding tương thích), chứ không dựa vào tên status code hoặc giá trị return có nghĩa “thất bại” ở nghiệp vụ.

```java
@AfterReturning(
    pointcut = "execution(* demo..*.successCase(..))",
    returning = "result",
    argNames = "result"
)
public void afterSuccess(String result) {
    System.out.println("AFTER_RETURNING result=" + result);
}
```

Ở đây:

- `pointcut` chọn method execution.
- `returning = "result"` bind kết quả vào tham số có tên `result`.
- Kiểu `String` của tham số cũng **giới hạn kiểu giá trị trả về** có thể match. Nếu muốn nhận kiểu rộng hơn, dùng `Object`.
- `argNames` chỉ rõ binding name, tránh phụ thuộc vào khả năng suy tên tham số từ bytecode.

**Nhánh thành công:**

```text
TARGET successCase false
AFTER_RETURNING result=SUCCESS-OK
CLIENT value=SUCCESS-OK
```

**Nhánh ném lỗi:**

```text
TARGET successCase true
CLIENT caught IllegalStateException:return failed
```

Ở nhánh lỗi, dòng `AFTER_RETURNING` không xuất hiện. Một method trả về `"FAIL"` nhưng không ném exception vẫn là **normal return** và có thể kích hoạt `@AfterReturning`.

### `@AfterReturning` không dùng để thay return value

Method `afterSuccess(...)` là một advice nhận kết quả, không phải một factory method thay kết quả của target. Trả một value mới từ advice này **không thay đổi reference mà client nhận**. Nếu cần đổi kết quả (ví dụ fallback/cache), dùng `@Around`.

## 5. Ví dụ 3 — ghi ngoại lệ với `@AfterThrowing`

`@AfterThrowing` chạy khi execution được chọn **thoát bằng exception**; thuộc tính `throwing` cho phép bind đối tượng exception và giới hạn kiểu nó nhận.

```java
@AfterThrowing(
    pointcut = "execution(* demo..*.throwingCase(..))",
    throwing = "error",
    argNames = "error"
)
public void afterInvalid(IllegalArgumentException error) {
    System.out.println(
        "AFTER_THROWING "
            + error.getClass().getSimpleName()
            + ":" + error.getMessage()
    );
}
```

Trong ví dụ, advice chỉ nhận `IllegalArgumentException` hoặc subclass, không nhận `IllegalStateException`, dù cả hai đều là runtime exceptions. Nếu muốn quan sát mọi loại throwable, có thể dùng `Throwable` (và cần xử lý trường hợp `Error` phù hợp).

**Ngoại lệ đúng kiểu:**

```text
TARGET throwingCase argument
AFTER_THROWING IllegalArgumentException:bad argument
CLIENT caught IllegalArgumentException:bad argument
```

**Ngoại lệ khác kiểu:**

```text
TARGET throwingCase state
CLIENT caught IllegalStateException:bad state
```

**Không có ngoại lệ:**

```text
TARGET throwingCase ok
CLIENT value=THROW-OK
```

Spring gọi advice cho exception phù hợp; method advice return `void` **không làm caller hết nhận exception**. `@AfterThrowing` dành cho ghi log/metrics, không phải cơ chế thay thế việc catch/recover bằng result mới.

Một ngoại lệ được target tự catch rồi trả bình thường **không kích hoạt** `@AfterThrowing`, vì nó không thoát khỏi method execution tại ranh giới Spring AOP.

## 6. Ví dụ 4 — luôn ghi khi kết thúc với `@After`

`@After` trong Spring @AspectJ AOP mang ý nghĩa **finally advice**: nó được thực thi bất kể method được chọn kết thúc bình thường hay bằng exception.

```java
@After("execution(* demo..*.finallyCase(..))")
public void afterCompletion(JoinPoint jp) {
    System.out.println("AFTER_FINALLY " + jp.getSignature().getName());
}
```

**Khi trả thành công:**

```text
TARGET finallyCase false
AFTER_FINALLY finallyCase
CLIENT value=FINALLY-OK
```

**Khi ném exception:**

```text
TARGET finallyCase true
AFTER_FINALLY finallyCase
CLIENT caught IllegalStateException:finally failed
```

`@After` thích hợp cho việc ghi nhận “đã kết thúc method execution”, giải phóng tài nguyên theo contract đã xác định, hoặc metric số lần thực thi. Nó **không cho biết kết quả bình thường hay lỗi theo mặc định**; muốn riêng từng nhánh, dùng `@AfterReturning` / `@AfterThrowing` hoặc `@Around`.

**Khác với `@AfterThrowing`:** `@After` vẫn chạy khi target trả bình thường. **Khác với `@AfterReturning`:** `@After` vẫn chạy khi target ném exception.

## 7. Ví dụ 5 — bao quanh và ghi cả hai nhánh với `@Around`

`@Around` có khả năng ghi trước, ghi sau, quan sát giá trị và lỗi, đồng thời kiểm soát phần chain tiếp theo bằng `ProceedingJoinPoint.proceed()`.

```java
@Around("execution(* demo..*.aroundCase(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("AROUND enter");
    try {
        Object value = pjp.proceed();
        System.out.println("AROUND return " + value);
        return value;
    } catch (Throwable error) {
        System.out.println(
            "AROUND throw " + error.getClass().getSimpleName()
                + ":" + error.getMessage()
        );
        throw error;
    } finally {
        System.out.println("AROUND finally");
    }
}
```

**Khi target thành công:**

```text
AROUND enter
TARGET aroundCase false
AROUND return AROUND-OK
AROUND finally
CLIENT value=AROUND-OK
```

**Khi target ném exception:**

```text
AROUND enter
TARGET aroundCase true
AROUND throw IllegalStateException:around failed
AROUND finally
CLIENT caught IllegalStateException:around failed
```

Điểm nổi bật:

- `pjp.proceed()` **tiếp tục qua các interceptor còn lại** trong chuỗi, cuối cùng đến target nếu không có interceptor nào short-circuit.
- Nếu bỏ `proceed()` mà return ngay, các advisor phía trong và target có thể **không thực thi**. Đây là điểm khác biệt lớn với những loại advice còn lại.
- Nếu `proceed()` bị gọi nhiều lần, phần chuỗi còn lại có thể được thực thi nhiều lần. Điều này chỉ phù hợp những trường hợp thiết kế rõ như retry có kiểm soát; không tùy tiện gọi lặp cho các method ghi dữ liệu.
- Advice trả `Object` nên hoàn trả kết quả thực của `proceed()` (hoặc kết quả khác nếu có chủ đích và tương thích với signature target).
- `catch Throwable` giúp ghi cả `Exception` và `Error` trong đoạn minh họa, **không nên nuốt lỗi nghiêm trọng**; ví dụ ném lại không thay đổi flow.

Nếu chỉ cần một log trước method, `@Before` ngắn và an toàn hơn `@Around`. Chỉ dùng around khi thật sự cần điều phối logic trước/sau, thay return value, fallback hoặc timer có trạng thái cục bộ.

## 8. Project Java 17+ chạy độc lập — năm advice với trace tách biệt

Để không phải đoán thứ tự khi **nhiều loại advice cùng match một method**, demo này cố ý dùng **mỗi loại advice cho một nhóm method riêng**. Nhờ đó stdout minh họa năm contract độc lập, dễ kiểm thử. Cuối bài sẽ giải thích cách nhiều advice tương tác trên cùng một method thực tế.

Môi trường: **Java 17+, Maven, Spring Framework 6.2.6** với `spring-context`, `spring-aop` và `aspectjweaver`. Spring bean được đăng ký qua `@Bean`, aspect dùng `@Aspect` và `@EnableAspectJAutoProxy`.

### 8.1. Cấu trúc file

```text
spring-advice-tracing/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── AdviceTracingDemo.java
```

### 8.2. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-advice-tracing</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
            <version>6.2.6</version>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.6</version>
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
                <configuration>
                    <mainClass>demo.AdviceTracingDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 8.3. Tệp src/main/java/demo/AdviceTracingDemo.java

```java
package demo;

import java.util.function.Supplier;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class AdviceTracingDemo {

    public interface TaskService {
        String beforeCase(boolean fail);
        String successCase(boolean fail);
        String throwingCase(String kind);
        String finallyCase(boolean fail);
        String aroundCase(boolean fail);
    }

    public static class TaskServiceImpl implements TaskService {

        @Override
        public String beforeCase(boolean fail) {
            System.out.println("TARGET beforeCase " + fail);
            if (fail) {
                throw new IllegalStateException("before failed");
            }
            return "BEFORE-OK";
        }

        @Override
        public String successCase(boolean fail) {
            System.out.println("TARGET successCase " + fail);
            if (fail) {
                throw new IllegalStateException("return failed");
            }
            return "SUCCESS-OK";
        }

        @Override
        public String throwingCase(String kind) {
            System.out.println("TARGET throwingCase " + kind);
            if ("argument".equals(kind)) {
                throw new IllegalArgumentException("bad argument");
            }
            if ("state".equals(kind)) {
                throw new IllegalStateException("bad state");
            }
            return "THROW-OK";
        }

        @Override
        public String finallyCase(boolean fail) {
            System.out.println("TARGET finallyCase " + fail);
            if (fail) {
                throw new IllegalStateException("finally failed");
            }
            return "FINALLY-OK";
        }

        @Override
        public String aroundCase(boolean fail) {
            System.out.println("TARGET aroundCase " + fail);
            if (fail) {
                throw new IllegalStateException("around failed");
            }
            return "AROUND-OK";
        }
    }

    @Aspect
    public static class TraceAspect {

        @Before("execution(* demo..*.beforeCase(..))")
        public void before(JoinPoint jp) {
            System.out.println("BEFORE " + jp.getSignature().getName());
        }

        @AfterReturning(
            pointcut = "execution(* demo..*.successCase(..))",
            returning = "result",
            argNames = "result"
        )
        public void afterReturning(String result) {
            System.out.println("AFTER_RETURNING result=" + result);
        }

        @AfterThrowing(
            pointcut = "execution(* demo..*.throwingCase(..))",
            throwing = "error",
            argNames = "error"
        )
        public void afterThrowing(IllegalArgumentException error) {
            System.out.println(
                "AFTER_THROWING "
                    + error.getClass().getSimpleName()
                    + ":" + error.getMessage()
            );
        }

        @After("execution(* demo..*.finallyCase(..))")
        public void after(JoinPoint jp) {
            System.out.println(
                "AFTER_FINALLY " + jp.getSignature().getName()
            );
        }

        @Around("execution(* demo..*.aroundCase(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("AROUND enter");
            try {
                Object value = pjp.proceed();
                System.out.println("AROUND return " + value);
                return value;
            } catch (Throwable error) {
                System.out.println(
                    "AROUND throw "
                        + error.getClass().getSimpleName()
                        + ":" + error.getMessage()
                );
                throw error;
            } finally {
                System.out.println("AROUND finally");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class Config {

        @Bean
        public TaskService taskService() {
            return new TaskServiceImpl();
        }

        @Bean
        public TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    static void runCase(String label, Supplier<String> action) {
        System.out.println("=== " + label + " ===");
        try {
            System.out.println("CLIENT value=" + action.get());
        } catch (RuntimeException error) {
            System.out.println(
                "CLIENT caught "
                    + error.getClass().getSimpleName()
                    + ":" + error.getMessage()
            );
        }
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            TaskService service = context.getBean(TaskService.class);

            System.out.println(
                "AOP_PROXY=" + AopUtils.isAopProxy(service)
            );
            System.out.println(
                "JDK_PROXY=" + AopUtils.isJdkDynamicProxy(service)
            );

            runCase("BEFORE SUCCESS", () -> service.beforeCase(false));
            runCase("BEFORE FAILURE", () -> service.beforeCase(true));

            runCase("AFTER RETURNING SUCCESS",
                () -> service.successCase(false));
            runCase("AFTER RETURNING FAILURE",
                () -> service.successCase(true));

            runCase("AFTER THROWING INVALID",
                () -> service.throwingCase("argument"));
            runCase("AFTER THROWING STATE",
                () -> service.throwingCase("state"));
            runCase("AFTER THROWING SUCCESS",
                () -> service.throwingCase("ok"));

            runCase("AFTER FINALLY SUCCESS",
                () -> service.finallyCase(false));
            runCase("AFTER FINALLY FAILURE",
                () -> service.finallyCase(true));

            runCase("AROUND SUCCESS", () -> service.aroundCase(false));
            runCase("AROUND FAILURE", () -> service.aroundCase(true));
        }
    }
}
```

### 8.4. Lệnh chạy

Ở thư mục `spring-advice-tracing`:

```bash
mvn -q compile exec:java
```

Các dòng stdout **do chính chương trình in**, theo thứ tự kỳ vọng (không tính thông báo riêng từ Maven hoặc framework logging):

```text
AOP_PROXY=true
JDK_PROXY=true
=== BEFORE SUCCESS ===
BEFORE beforeCase
TARGET beforeCase false
CLIENT value=BEFORE-OK
=== BEFORE FAILURE ===
BEFORE beforeCase
TARGET beforeCase true
CLIENT caught IllegalStateException:before failed
=== AFTER RETURNING SUCCESS ===
TARGET successCase false
AFTER_RETURNING result=SUCCESS-OK
CLIENT value=SUCCESS-OK
=== AFTER RETURNING FAILURE ===
TARGET successCase true
CLIENT caught IllegalStateException:return failed
=== AFTER THROWING INVALID ===
TARGET throwingCase argument
AFTER_THROWING IllegalArgumentException:bad argument
CLIENT caught IllegalArgumentException:bad argument
=== AFTER THROWING STATE ===
TARGET throwingCase state
CLIENT caught IllegalStateException:bad state
=== AFTER THROWING SUCCESS ===
TARGET throwingCase ok
CLIENT value=THROW-OK
=== AFTER FINALLY SUCCESS ===
TARGET finallyCase false
AFTER_FINALLY finallyCase
CLIENT value=FINALLY-OK
=== AFTER FINALLY FAILURE ===
TARGET finallyCase true
AFTER_FINALLY finallyCase
CLIENT caught IllegalStateException:finally failed
=== AROUND SUCCESS ===
AROUND enter
TARGET aroundCase false
AROUND return AROUND-OK
AROUND finally
CLIENT value=AROUND-OK
=== AROUND FAILURE ===
AROUND enter
TARGET aroundCase true
AROUND throw IllegalStateException:around failed
AROUND finally
CLIENT caught IllegalStateException:around failed
```

### 8.5. Vì sao ví dụ tạo ra các trace này?

1. `Config` đăng ký `TaskServiceImpl` dưới interface `TaskService`. `@EnableAspectJAutoProxy(proxyTargetClass=false)` cho phép Spring dùng JDK dynamic proxy khi target có interface.
2. `TraceAspect` là Spring bean và mang `@Aspect`, nên Spring tìm được cả năm advice methods.
3. Mỗi method của service có **tên khác nhau** và mỗi advice có pointcut chỉ chọn đúng tên đó. Do vậy chỉ một loại advice áp dụng cho từng test case, không phải phụ thuộc thứ tự ngẫu nhiên giữa advice classes.
4. Caller luôn lấy `TaskService` từ context. Lời gọi `service.beforeCase(...)` và các lời gọi khác đều qua **Spring proxy**, không gọi trực tiếp `new TaskServiceImpl()`.
5. Với `@Before`, advice luôn in trước target khi method được match, kể cả target sẽ ném lỗi.
6. Với `@AfterReturning`, nếu target ném exception thì không có `AFTER_RETURNING`.
7. Với `@AfterThrowing(IllegalArgumentException)`, `IllegalStateException` không thỏa điều kiện type và không chạy advice.
8. Với `@After`, advice luôn in sau target, cả đường thành công và lỗi.
9. Với `@Around`, lời gọi `pjp.proceed()` dẫn đến target; khi thành công chạy nhánh return, khi lỗi chạy catch; `finally` luôn chạy trước khi client nhận kết quả/lỗi.

**Lưu ý về việc chạy:** đây là mã nguồn Maven độc lập và stdout dự kiến từ logic ví dụ. Nếu dự án thực tế có thêm advisor/aspect khác, logging hoặc proxy configuration khác, trace có thể bổ sung hoặc đổi thứ tự. Chỉ so khớp các dòng do chương trình của bài này in.

## 9. Muốn dùng đủ năm loại advice trên cùng một method thì sao?

Trong thực tế, có thể áp nhiều loại advice lên cùng một method, ví dụ:

```java
@Aspect
public class CombinedTraceAspect {
    @Before("execution(* com.example.app.service..*.save(..))")
    public void before() {
        System.out.println("BEFORE");
    }

    @AfterReturning("execution(* com.example.app.service..*.save(..))")
    public void success() {
        System.out.println("RETURNED");
    }

    @AfterThrowing("execution(* com.example.app.service..*.save(..))")
    public void failure() {
        System.out.println("THREW");
    }

    @After("execution(* com.example.app.service..*.save(..))")
    public void after() {
        System.out.println("FINALLY");
    }
}
```

Đây là **snippet minh họa**, chưa có `save(..)` service để biên dịch/chạy độc lập. Nó cho thấy Spring có thể đặt nhiều advice cùng một pointcut, nhưng cần hiểu thứ tự:

- **Đường vào:** advice chạy theo precedence trong interceptor chain; `@Before` thực thi trước phần target.
- **Đường ra bình thường:** `@AfterReturning` chạy, `@AfterThrowing` không chạy; `@After` theo nghĩa finally.
- **Đường ra lỗi:** `@AfterThrowing` chạy với ngoại lệ phù hợp, `@AfterReturning` không chạy; `@After` vẫn chạy.
- **Thứ tự giữa các aspects khác nhau:** không mặc định ổn định nếu không cấu hình; dùng `@Order` hoặc `Ordered` để xác định precedence.
- **Trong cùng aspect:** Spring quy định thứ tự ưu tiên theo loại advice (`@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`); `@After` có ngữ nghĩa finally nên thực tế hoàn tất **sau** after-returning/after-throwing khi các advice đó áp dụng.
- **Hai advice cùng loại trong cùng aspect:** Java reflection không đảm bảo thứ tự khai báo source; không dùng vị trí method trong file để suy order.

Nếu cần ví dụ mà stdout phải **chính xác tuyệt đối** để đưa vào README/test, cách ở mục 8 (các method độc lập) dễ kiểm chứng hơn. Nếu cần quan sát nested chain thực tế, đặt `@Order` cho các aspects và có integration test xác minh thứ tự gọi.

### Ưu tiên advice cao khác thời điểm chạy trước/sau

Với hai `@Around` advice A và B cùng match target, A ưu tiên cao hơn:

```text
A enter
  B enter
    TARGET
  B exit
A exit
```

Như vậy “ưu tiên cao” nghĩa là **đi vào trước, đi ra sau**. Shared pointcut hoặc đặt class theo thứ tự alphabetical không tự cung cấp precedence.

## 10. Ghi nhận thông tin join point đúng cách

Trong Spring AOP, advice có thể nhận `JoinPoint` làm tham số đầu tiên (riêng `@Around` dùng `ProceedingJoinPoint`). Các thông tin quan trọng:

```java
@Before("execution(public * com.example.app.service..*.*(..))")
public void log(JoinPoint jp) {
    String method = jp.getSignature().getName();
    Object proxy = jp.getThis();
    Object target = jp.getTarget();
    Object[] arguments = jp.getArgs();

    System.out.println("METHOD " + method);
    System.out.println("PROXY=" + proxy.getClass().getSimpleName());
    System.out.println("TARGET=" + target.getClass().getSimpleName());
    System.out.println("ARG_COUNT=" + arguments.length);
}
```

Đây là **snippet**, không phải file Java chạy độc lập. Nó có thể hỗ trợ debug:

- `jp.getSignature()`: thông tin method/join point.
- `jp.getArgs()`: arguments của invocation.
- `jp.getThis()`: Spring AOP proxy object.
- `jp.getTarget()`: target object đứng sau proxy.

Đừng đưa toàn bộ `arguments` vào log production mà không lọc. Nếu tham số chứa PII, password, token, file data hoặc object graph lớn, cần che/mask hoặc chỉ ghi metadata cần thiết. `toString()` trên input objects cũng có thể gây lộ dữ liệu hoặc thực hiện side effects ngoài ý muốn.

Khi log exception, dùng stacktrace khi cần debug, nhưng tránh lặp cùng lỗi ở nhiều tầng. Một service call qua nhiều proxy/advice có thể phát sinh nhiều sự kiện log cho cùng một exception.

## 11. Thí nghiệm đối chứng trên trace của project

Thử từng thay đổi độc lập đối với ví dụ ở mục 8 rồi phục hồi code trước khi thử tiếp:

| Thay đổi | Quan sát | Nguyên nhân |
| --- | --- | --- |
| Bỏ EnableAspectJAutoProxy trong Spring Framework thuần | Log target vẫn còn, log advice biến mất | Hạ tầng proxy chưa bật |
| Ngừng đăng ký TraceAspect bean | Log aspect biến mất | @Aspect không tự tạo Spring bean |
| Gọi new TaskServiceImpl() ngoài context | Chỉ in target | Raw object không có AOP proxy |
| Đổi pointcut beforeCase sang method khác | Before marker mất ở beforeCase | Method không còn match pointcut |
| Gọi this.innerMethod() bên trong target | Không có lần advice riêng | Self-invocation bypass proxy |
| Cho @Around return sớm không gọi proceed | Target không chạy | Around short-circuit |

Với mỗi lần thử, kiểm tra ba biến quan sát: target có chạy không, advice nào ghi log, caller nhận return/exception gì. Cách đó cô lập proxy, pointcut, outcome và thứ tự chain tốt hơn việc gắn đủ năm loại advice vào tất cả service methods.

Nếu còn transaction/cache/retry advisor, thực hiện integration test trên ApplicationContext thật và ghi events bằng recorder thay vì chỉ dựa vào thứ tự các dòng stdout.

## 12. Đưa tracing Spring AOP vào hệ thống thật

### 12.1. Log ít nhưng đủ để quan sát

Thông tin thường có ích:

- Method name và một operation identifier an toàn.
- Kết quả thuộc nhóm thành công/lỗi.
- Thời gian xử lý nếu đo trong `@Around`, không hardcode duration dự kiến.
- Trace/correlation ID do request context cung cấp.
- Exception class (và message đã được lọc hoặc che thông tin nhạy cảm).

**Không ghi trực tiếp credentials, token, dữ liệu cá nhân, toàn bộ request payload hoặc secrets** chỉ vì `JoinPoint.getArgs()` có thể truy cập chúng.

### 12.2. Dùng logger thay cho `System.out` khi triển khai production

Trong demo, `System.out.println` giúp xác định stdout. Ở dự án thực tế, nên dùng SLF4J/logging framework theo cấu hình ứng dụng:

```java
private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(TraceAspect.class);

@Before("execution(* com.example.app.service..*.*(..))")
public void logCall(JoinPoint jp) {
    log.debug("Invoking {}", jp.getSignature().toShortString());
}
```

Đây là **đoạn minh họa bổ sung**, cần SLF4J dependency, `TraceAspect` class và các imports thích hợp. Ưu tiên structured logging, levels và sampling để tránh tốn tài nguyên do advice chạy thường xuyên.

### 12.3. Không suy rollback từ advice

Một `@Transactional` method có thể được hạ tầng transaction tạo advisor riêng. `@AfterThrowing` chỉ quan sát exception phù hợp; nó **không tự cấu hình rollback**. Việc log xảy ra trước/sau commit hoặc rollback còn phụ thuộc vào thứ tự advisor của Spring.

### 12.4. Chú ý async và completion signals

Với `@Async`, một method có thể trả `CompletableFuture`, `Future` hoặc chạy trên thread khác. `@AfterReturning` ở một ranh giới proxy có thể quan sát **future object được trả về**, không đồng nghĩa tác vụ bất đồng bộ đã hoàn thành thành công. Muốn ghi thời điểm completion, cần quan sát completion stage hoặc cơ chế async-specific thích hợp.

### 12.5. Dùng integration test thay vì chỉ đọc code

Test tốt nên:

1. Dựng Spring context với aspect bean và target bean thật.
2. Assert `AopUtils.isAopProxy(service)` là `true`.
3. Gọi từ bean reference đã inject, không dùng `new` target object.
4. Kiểm tra case thành công, exception đúng/sai loại, và around return/throw.
5. Dùng recorder/spy hoặc capture log có kiểm soát để kiểm tra **advice nào đã chạy**, không chỉ assert business result.
6. Khi nhiều advice cùng match, cấu hình thứ tự rõ ràng và assert trace theo thứ tự đó.

## 13. Tự kiểm tra

**1.** Method trả `"FAIL"` mà không ném exception, `@AfterThrowing` có chạy không?  
**Trả lời:** Không; method đã trả bình thường. `@AfterReturning` có thể chạy.

**2.** `@AfterReturning(returning="value")` mà advice parameter đặt tên `result` có hợp lệ không?  
**Trả lời:** Tên binding phải khớp, hoặc cần cấu hình/khai báo tương thích; dễ nhất là dùng cùng tên `value` trong attribute và parameter (có thể thêm `argNames`).

**3.** `@AfterThrowing` nhận `IllegalArgumentException` có chạy khi target ném `IllegalStateException`?  
**Trả lời:** Không, vì không có quan hệ subtype phù hợp.

**4.** `@After` sẽ chạy khi target ném exception và không return bình thường?  
**Trả lời:** Có, vì semantics của `@After` tương tự finally.

**5.** `@Before` có gọi `proceed()` để tiếp tục target không?  
**Trả lời:** Không. Chỉ `@Around` dùng `ProceedingJoinPoint.proceed()`; Spring tự tiếp tục sau before advice nếu advice không ném lỗi.

**6.** Trong `@Around`, bỏ `proceed()` nhưng return một String, chuyện gì xảy ra?  
**Trả lời:** Có thể short-circuit phần chain còn lại và target; caller nhận giá trị advice trả nếu compatible.

**7.** Hai aspects cùng match một method nhưng không có `@Order`. Có nên dự đoán thứ tự log từ thứ tự class được khai báo trong code không?  
**Trả lời:** Không. Thứ tự tương đối giữa aspects cần được quy định bằng precedence nếu quan trọng.

**8.** Target service gọi `this.beforeCase()` trong một method khác của chính nó. Advice `@Before` chỉ khớp `beforeCase()` có chạy lại không?  
**Trả lời:** Không có proxy entry mới, nên Spring AOP không intercept lời gọi nội bộ đó.

**9.** Một `@Around` catch exception và trả fallback. `@AfterThrowing` nằm phía ngoài có chắc thấy exception gốc không?  
**Trả lời:** Không. Nếu inner advice đã xử lý và trả về bình thường, outer advice có thể chỉ thấy normal return.

**10.** Khi chuyển Spring Framework thuần sang Spring Boot, có nên giữ nguyên mọi giả định về JDK/CGLIB proxy default?  
**Trả lời:** Không. Boot auto-configuration có thể mặc định class-based proxy; nên kiểm tra cấu hình và runtime object thực tế.

## 14. Tài liệu Spring Framework chính thức

- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): ví dụ và semantics của cả `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After` và `@Around`, binding và precedence.
- [Spring Framework — AOP Concepts](https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html): các loại advice và method-execution join points.
- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB proxies, self-invocation và giới hạn interception.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): kích hoạt AOP và đăng ký aspect beans.


</details>

- [Quay lại đầu trang](#back-to-top)
