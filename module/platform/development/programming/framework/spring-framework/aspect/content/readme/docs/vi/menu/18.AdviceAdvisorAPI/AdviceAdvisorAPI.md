<a id="back-to-top"></a>

# Advice và Advisor API

## Menu
- [Advice API cấp thấp và quan hệ với cách khai báo @AspectJ](#advice-advisor-api-001)
- [MethodInterceptor và MethodInvocation trong chuỗi gọi](#advice-advisor-api-002)
- [MethodBeforeAdvice, AfterReturningAdvice và ThrowsAdvice](#advice-advisor-api-003)
- [Advisor kết hợp pointcut với advice](#advice-advisor-api-004)
- [DefaultPointcutAdvisor và trường hợp dùng phổ biến](#advice-advisor-api-005)
- [PointcutAdvisor và IntroductionAdvisor](#advice-advisor-api-006)
- [Sắp xếp advisor và kiểm chứng chuỗi interceptor](#advice-advisor-api-007)
- [Advice adapter và khả năng chuyển loại advice sang interceptor](#advice-advisor-api-008)
- [Ranh giới giữa mở rộng hạ tầng và viết aspect nghiệp vụ](#advice-advisor-api-009)

## <a id="advice-advisor-api-001">Advice API cấp thấp và quan hệ với cách khai báo @AspectJ</a>

<details>
<summary>Xem chi tiết</summary>

# Advice API cấp thấp và quan hệ với cách khai báo @AspectJ trong Spring AOP

## 1. Vì sao Spring AOP có cả Advice API và `@Aspect`?

Trong Spring Framework, cùng một yêu cầu như “ghi thời điểm gọi phương thức service”, “theo dõi kết quả” hoặc “xử lý lỗi ở ranh giới dịch vụ” có thể triển khai bằng hai cách:

- **Advice API cấp thấp:** tạo một đối tượng triển khai giao diện như `MethodInterceptor` hoặc `MethodBeforeAdvice`, ghép nó với một `Pointcut` trong `Advisor` rồi gắn vào Spring proxy.
- **Kiểu khai báo `@AspectJ`:** viết class `@Aspect` và method `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After` hoặc `@Around`. Spring phân tích annotation và tạo các advisor/interceptor tương ứng để chạy trên Spring proxy.

Cả hai cách đều thuộc **Spring AOP dựa trên proxy** khi được cấu hình theo cơ chế Spring thông thường. Cách viết `@Aspect` dùng cú pháp annotation do AspectJ cung cấp và dùng thư viện AspectJ để phân tích biểu thức pointcut; **nó không tự kích hoạt AspectJ compile-time weaving hoặc load-time weaving**.

```text
        KIỂU API CẤP THẤP                      KIỂU @AspectJ TRONG SPRING

       Pointcut + Advice                         @Aspect class
                |                                    |
       DefaultPointcutAdvisor              @Around / @Before / ...
                |                                    |
                +----------------+-------------------+
                                 |
                    Spring advisor / interceptor chain
                                 |
                            Spring proxy
                                 |
                           Target method
```

Điều quyết định một lời gọi có bị chặn là **đường đi qua proxy và điều kiện pointcut**. Advice chỉ thực thi khi advisor có mặt trong chuỗi và phương thức tương ứng được chọn. Đây là nền tảng để hiểu tại sao cùng một quy tắc có thể viết bằng hai phong cách khác nhau, nhưng đều cần test tích hợp với Spring proxy.

### Mô hình trách nhiệm

| Thành phần | Chức năng trong Spring AOP | Ví dụ |
| --- | --- | --- |
| `Advice` | Mã thực thi tại một vị trí trong lời gọi | `MethodInterceptor`, `MethodBeforeAdvice` |
| `Pointcut` | Chọn class/method có thể áp dụng advice | `NameMatchMethodPointcut`, `AspectJExpressionPointcut` |
| `Advisor` | Kết hợp advice với điều kiện áp dụng | `DefaultPointcutAdvisor` |
| `ProxyFactory` hoặc auto-proxy creator | Xây proxy, tạo interceptor chain | `ProxyFactory.addAdvisor(...)` |
| `@Aspect` | Khai báo nhiều advice và pointcut bằng annotation | `@Around("execution(...)")` |
| `@EnableAspectJAutoProxy` | Bật Spring xử lý bean có `@Aspect` | Auto-proxy khi dùng Java config |

**Không đồng nhất** `Advice` và `Advisor`: một advice định nghĩa hành vi; một advisor thêm thông tin **nơi áp dụng**. Tương tự, bean mang `@Aspect` không tự là một `MethodInterceptor` do bạn triển khai; Spring dùng adapter/reflective advice và advisor để đưa các method được đánh dấu vào chuỗi Spring AOP.

## 2. Các loại Advice API cấp thấp

### 2.1. Giao diện gốc `Advice`

Giao diện đánh dấu `org.aopalliance.aop.Advice` được nhiều kiểu advice cấp thấp dùng. Nó **không** có phương thức `before()` hay `invoke()` chung để bạn gọi trực tiếp; từng biến thể chuyên biệt quy định callback tương ứng.

Nhóm phổ biến trong Spring:

| Kiểu cấp thấp | Phương thức / cách dùng | Gần với cú pháp @AspectJ | Điều cần lưu ý |
| --- | --- | --- | --- |
| `MethodBeforeAdvice` | `before(Method,Object[],Object)` | `@Before` | Chạy trước target; có thể ném lỗi để chặn chuỗi |
| `AfterReturningAdvice` | `afterReturning(Object,Method,Object[],Object)` | `@AfterReturning` | Chỉ chạy khi phương thức được chặn trả về bình thường; không thay trực tiếp giá trị trả về |
| `ThrowsAdvice` | Các method tên `afterThrowing(...)` theo chữ ký Spring nhận diện | `@AfterThrowing` | Giao diện đánh dấu; không khai báo sẵn method callback |
| `MethodInterceptor` | `invoke(MethodInvocation)` | `@Around` | Có thể xử lý trước/sau, kết quả, exception và quyết định gọi `proceed()` |
| `IntroductionInterceptor` | Hỗ trợ introduction advice | `@DeclareParents` (chức năng liên quan) | Bổ sung giao diện cho proxy; cấu hình phức tạp hơn advisor thường |

`@After` của `@AspectJ` có ý nghĩa **finally**: chạy sau lời gọi dù trả về bình thường hay có exception. Trong API cấp thấp, `MethodInterceptor` dùng `try/finally` là cách trực tiếp thể hiện hành vi này. `AfterReturningAdvice` không phải `@After`.

### 2.2. `MethodBeforeAdvice`

Chữ ký callback:

```java
public void before(Method method, Object[] args, Object target)
        throws Throwable;
```

Method `before(...)` chạy trước khi target thực hiện (nếu advice đã được chọn). Tham số `target` là đối tượng đích, `method` là method được Spring chuyển đến, `args` là các đối số.

Ví dụ **trích đoạn minh họa**, với `events` là danh sách đã khai báo:

```java
MethodBeforeAdvice before = (method, args, target) -> {
    events.add("before:" + method.getName());
};
```

Import `org.springframework.aop.MethodBeforeAdvice`. Nếu `before(...)` ném exception, target thường không được gọi qua interceptor chain ấy. Không cần gọi `proceed()` trong loại advice này vì Spring tự chuyển tiếp nếu callback trả về bình thường.

### 2.3. `AfterReturningAdvice`

Chữ ký callback:

```java
public void afterReturning(
        Object returnValue, Method method, Object[] args, Object target)
        throws Throwable;
```

Được gọi khi lời gọi đi qua advisor trả về bình thường. Dùng để ghi nhận giá trị thành công, ghi metric hoặc log. Nó **không thể thay tham chiếu giá trị trả về mà caller nhận** bằng cách gán lại biến `returnValue`; muốn thay thế kết quả, dùng `MethodInterceptor` hoặc `@Around` và trả đối tượng khác khi hợp đồng cho phép.

Ví dụ minh họa:

```java
AfterReturningAdvice after = (result, method, args, target) -> {
    events.add("returned:" + method.getName() + ":" + result);
};
```

Import `org.springframework.aop.AfterReturningAdvice`. Với phương thức trả `CompletableFuture`, sự kiện “returned” phản ánh **việc nhận future**, không mặc định khẳng định tác vụ bất đồng bộ đã hoàn tất.

### 2.4. `ThrowsAdvice`

Đây là giao diện đánh dấu `org.springframework.aop.ThrowsAdvice`. Spring nhận diện các phương thức có **tên `afterThrowing`** và một trong các chữ ký được hỗ trợ. Ví dụ:

```java
import org.springframework.aop.ThrowsAdvice;

public final class FailureLogAdvice implements ThrowsAdvice {
    public void afterThrowing(IllegalStateException error) {
        System.out.println("failed:" + error.getMessage());
    }
}
```

Ngoài chữ ký một tham số là exception, Spring hỗ trợ dạng bốn tham số gồm `Method`, `Object[]`, đối tượng target và loại `Throwable` phù hợp. Đây là method convention do Spring tìm qua reflection; vì `ThrowsAdvice` là marker interface nên **không viết `@Override` cho `afterThrowing`**.

Trong hệ thống thật, dùng nơi ghi lỗi phù hợp và xác định rõ policy. `ThrowsAdvice` ghi nhận exception chứ không tự sửa lỗi để biến một target thất bại thành kết quả thành công. Nếu cần thay thế lỗi bằng giá trị trả về khác, dùng một `MethodInterceptor` có chủ đích và test hợp đồng của caller.

### 2.5. `MethodInterceptor`: điểm nối linh hoạt nhất

Đây là giao diện thuộc AOP Alliance:

```java
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public final class TraceInterceptor implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        System.out.println("before");
        try {
            Object result = invocation.proceed();
            System.out.println("after");
            return result;
        } catch (Throwable failure) {
            System.out.println("failed");
            throw failure;
        }
    }
}
```

`MethodInvocation` cung cấp `getMethod()`, `getArguments()`, `getThis()` và `proceed()`. `proceed()` thực hiện phần còn lại của chuỗi interceptor đến target. Nếu không gọi `proceed()`, interceptor có thể **chặn hoặc thay thế** lời gọi; chỉ làm thế nếu đó là hợp đồng được thiết kế rõ ràng.

Với `try/finally`, mã trong `finally` chạy dù target có lỗi; với `catch` bạn có thể ghi nhận rồi ném lại exception gốc. Không nuốt exception vô tình vì làm mất ngữ nghĩa nghiệp vụ hoặc phá giao dịch.

## 3. Mối liên hệ trực tiếp với `@AspectJ`

Để đặt hai cách cạnh nhau, giả sử cùng muốn theo dõi các lời gọi `MessageService.greet(..)` và `MessageService.fail(..)`.

### Dùng `MethodInterceptor`

```java
MethodInterceptor trace = invocation -> {
    String name = invocation.getMethod().getName();
    events.add("before:" + name);
    try {
        Object result = invocation.proceed();
        events.add("after:" + name);
        return result;
    } catch (Throwable error) {
        events.add("error:" + name);
        throw error;
    }
};
```

Khi dùng cấp thấp, phải cung cấp thêm **Pointcut** và **Advisor**. Nếu gắn interceptor trực tiếp không kèm pointcut, phạm vi có thể rộng hơn mong muốn.

### Dùng `@Aspect` và `@Around`

```java
@Aspect
public class TraceAspect {
    private final List<String> events;

    public TraceAspect(List<String> events) {
        this.events = events;
    }

    @Around("execution(* example.MessageService.greet(..))"
          + " || execution(* example.MessageService.fail(..))")
    public Object trace(ProceedingJoinPoint pjp) throws Throwable {
        String name = pjp.getSignature().getName();
        events.add("before:" + name);
        try {
            Object result = pjp.proceed();
            events.add("after:" + name);
            return result;
        } catch (Throwable error) {
            events.add("error:" + name);
            throw error;
        }
    }
}
```

Đây là đoạn minh họa trong một chương trình có `MessageService` và bean `TraceAspect`. Trong `@AspectJ`, biểu thức pointcut nằm ngay trên annotation; Spring tạo advisor và interceptor phù hợp khi bật `@EnableAspectJAutoProxy` và đăng ký `TraceAspect` là Spring bean.

**Khác nhau ở API callback:** `MethodInterceptor.invoke(MethodInvocation)` làm việc với giao diện AOP Alliance; `@Around` nhận `ProceedingJoinPoint` từ AspectJ annotation model. Hai đối tượng đều hỗ trợ `proceed()` nhưng **không cùng kiểu** và không thể ép kiểu lẫn nhau. Cả hai đều phải trả giá trị phù hợp để caller nhận đúng kết quả.

## 4. Bảng ánh xạ hành vi và những điểm không tương đương tuyệt đối

| Mong muốn | API cấp thấp | @AspectJ | Ranh giới phải hiểu |
| --- | --- | --- | --- |
| Ghi nhận trước target | `MethodBeforeAdvice` | `@Before` | Nếu advice ném lỗi, target có thể không chạy |
| Quan sát kết quả trả về bình thường | `AfterReturningAdvice` | `@AfterReturning` | Không thay được tham chiếu kết quả chỉ bằng after-returning |
| Quan sát exception | `ThrowsAdvice` | `@AfterThrowing` | Không phải cơ chế chung để xử lý mọi exception từ advisor khác |
| Dọn tài nguyên cả lúc thành công/lỗi | `MethodInterceptor` với `finally` | `@After` hoặc `@Around` với `finally` | `@After` là finally, khác `@AfterReturning` |
| Bọc lời gọi/đổi kết quả/chặn thực thi | `MethodInterceptor` | `@Around` | Có quyền không gọi `proceed()` nên cần dùng thận trọng |
| Chọn điểm áp dụng | `Pointcut` gắn `Advisor` | `@Pointcut`/expression trong advice | Cùng dựa trên Spring proxy trong cấu hình thông thường |
| Thêm interface cho proxy | `IntroductionInterceptor` và introduction advisor | `@DeclareParents` được Spring hỗ trợ trong giới hạn cụ thể | Không cùng cấu hình với ordinary pointcut advisor |

Không nên hiểu bảng này là Spring “chuyển nguyên văn” mọi annotation thành đúng một loại interface cấp thấp tương ứng. Phía trong Spring có các lớp adapter, advisor, join point và chuỗi interceptor. Bảng so sánh **mục đích và thời điểm thực thi**, không áp đặt quan hệ 1–1 giữa các implementation class.

## 5. Ví dụ chạy được: hai cách tạo cùng hành vi trên Spring proxy

Ví dụ sử dụng **Java 17, Spring Framework 6.2.x và Maven**. Một phiên bản gắn advice cấp thấp bằng `ProxyFactory`; phiên bản kia khai báo `@Aspect` và Spring tự tạo proxy. Cả hai chạy trên `MessageService` có ba phương thức:

- `greet(String)`: thành công, được advice ghi nhận;
- `fail()`: ném lỗi, advice ghi nhận và **ném lại lỗi gốc**;
- `ignored()`: ngoài pointcut, không có sự kiện advice.

Mỗi chế độ dùng một `TraceLog` riêng để dễ kiểm chứng, không phụ thuộc global state.

Cấu trúc:

```text
advice-api-comparison/
├── pom.xml
└── src/
    ├── main/java/example/
    │   ├── TraceLog.java
    │   ├── MessageService.java
    │   ├── TraceInterceptor.java
    │   ├── TraceAspect.java
    │   └── App.java
    └── test/java/example/AdviceModesTest.java
```

### 5.1. Maven dependencies

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>advice-api-comparison</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-context</artifactId>
      <version>6.2.8</version>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-aop</artifactId>
      <version>6.2.8</version>
    </dependency>
    <dependency>
      <groupId>org.aspectj</groupId>
      <artifactId>aspectjweaver</artifactId>
      <version>1.9.22</version>
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
        <version>3.13.0</version>
        <artifactId>maven-compiler-plugin</artifactId>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <version>3.5.2</version>
        <artifactId>maven-surefire-plugin</artifactId>
      </plugin>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <version>3.5.0</version>
        <artifactId>exec-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

### 5.2. Target service và nơi lưu sự kiện

`src/main/java/example/TraceLog.java`:

```java
package example;

import java.util.ArrayList;
import java.util.List;

public class TraceLog {
    private final List<String> events = new ArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }
}
```

`TraceLog` dùng `ArrayList` để minh họa thứ tự sự kiện đồng bộ, tuần tự. Nếu chạy đa luồng, hãy chọn cấu trúc đồng bộ/thích hợp hoặc một hệ thống thu thập metrics/log thực tế.

`src/main/java/example/MessageService.java`:

```java
package example;

public class MessageService {
    private final TraceLog log;

    public MessageService(TraceLog log) {
        this.log = log;
    }

    public String greet(String name) {
        log.add("target:greet");
        return "hello:" + name;
    }

    public void fail() {
        log.add("target:fail");
        throw new IllegalStateException("boom");
    }

    public String ignored() {
        log.add("target:ignored");
        return "SKIP";
    }
}
```

Class không `final`, các method public, nên có thể dùng Spring CGLIB-based proxy trong ví dụ. Khi code bên ngoài gọi trực tiếp `new MessageService(log)` mà không đi qua proxy, advice sẽ không chạy.

### 5.3. Advice API cấp thấp

`src/main/java/example/TraceInterceptor.java`:

```java
package example;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class TraceInterceptor implements MethodInterceptor {
    private final TraceLog log;

    public TraceInterceptor(TraceLog log) {
        this.log = log;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String method = invocation.getMethod().getName();
        log.add("before:" + method);
        try {
            Object value = invocation.proceed();
            log.add("after:" + method);
            return value;
        } catch (Throwable error) {
            log.add("error:" + method);
            throw error;
        }
    }
}
```

Điểm cần kiểm chứng: `MethodInterceptor` **trả lại** kết quả gốc hoặc **ném lại** lỗi gốc; nó không thay đổi hợp đồng nghiệp vụ. Nếu mục tiêu chỉ là ghi log trước khi gọi, `MethodBeforeAdvice` có API hẹp hơn và có thể phù hợp hơn.

### 5.4. Bean `@AspectJ` tương đương về hành vi

`src/main/java/example/TraceAspect.java`:

```java
package example;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TraceAspect {
    private final TraceLog log;

    public TraceAspect(TraceLog log) {
        this.log = log;
    }

    @Around(
        "execution(* example.MessageService.greet(..))"
        + " || execution(* example.MessageService.fail(..))"
    )
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getName();
        log.add("before:" + method);
        try {
            Object value = joinPoint.proceed();
            log.add("after:" + method);
            return value;
        } catch (Throwable error) {
            log.add("error:" + method);
            throw error;
        }
    }
}
```

`TraceAspect` có `@Aspect` nhưng cần **được đăng ký là Spring bean**; ví dụ mục tiếp theo khai báo nó bằng `@Bean`. Annotation `@Around` chứa expression chọn hai method, bỏ qua `ignored()`.

### 5.5. Chạy cả hai cấu hình

`src/main/java/example/App.java`:

```java
package example;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class App {
    public record Fixture(MessageService service, TraceLog log) {}

    public static Fixture lowLevelFixture() {
        TraceLog log = new TraceLog();

        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedNames("greet", "fail");

        ProxyFactory factory = new ProxyFactory(new MessageService(log));
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new DefaultPointcutAdvisor(
                pointcut, new TraceInterceptor(log)));

        MessageService proxy = (MessageService) factory.getProxy();
        return new Fixture(proxy, log);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class Config {
        @Bean
        TraceLog traceLog() {
            return new TraceLog();
        }

        @Bean
        MessageService messageService(TraceLog log) {
            return new MessageService(log);
        }

        @Bean
        TraceAspect traceAspect(TraceLog log) {
            return new TraceAspect(log);
        }
    }

    public static void exercise(String label,
                                MessageService service,
                                TraceLog log) {
        System.out.println(label + " greet=" + service.greet("Lan"));
        System.out.println(label + " ignored=" + service.ignored());
        try {
            service.fail();
        } catch (IllegalStateException ex) {
            System.out.println(label + " failure=" + ex.getMessage());
        }
        System.out.println(label + " events=" + log.snapshot());
    }

    public static void main(String[] args) {
        Fixture low = lowLevelFixture();
        exercise("low", low.service(), low.log());

        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            MessageService service = context.getBean(MessageService.class);
            TraceLog log = context.getBean(TraceLog.class);
            exercise("aspect", service, log);
        }
    }
}
```

Ở nhánh cấp thấp, ta tự tạo `NameMatchMethodPointcut`, `DefaultPointcutAdvisor` rồi đưa nó vào `ProxyFactory`. Ở nhánh `@AspectJ`, Spring đọc bean `TraceAspect` nhờ `@EnableAspectJAutoProxy` và tự cấu hình chuỗi interception. Cả hai chỉ chọn `greet` và `fail`.

Chạy trong thư mục dự án:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.App
```

Stdout logic kỳ vọng:

```text
low greet=hello:Lan
low ignored=SKIP
low failure=boom
low events=[before:greet, target:greet, after:greet, target:ignored, before:fail, target:fail, error:fail]
aspect greet=hello:Lan
aspect ignored=SKIP
aspect failure=boom
aspect events=[before:greet, target:greet, after:greet, target:ignored, before:fail, target:fail, error:fail]
```

Từ kết quả:

1. `greet` đi qua proxy nên cả hai phong cách đều ghi `before:greet` và `after:greet`; caller nhận `hello:Lan`.
2. `ignored` nằm ngoài pointcut, target chạy mà không có bản ghi của advice.
3. `fail` đi qua proxy và ném `IllegalStateException("boom")`; advice ghi `error:fail` và ném lại, caller vẫn quan sát lỗi gốc.
4. Dù output đồng nhất, **cách đăng ký advice khác nhau**. Giao diện `MethodInterceptor` không bị biến thành `@Aspect`; mỗi nhánh sử dụng một cơ chế khai báo dẫn đến một Spring interceptor chain.

Ví dụ này **không cần AspectJ weaving**: sự khác nhau nằm ở cách khai báo và Spring tạo proxy. `aspectjweaver` có mặt để hỗ trợ cú pháp/parser `@AspectJ` của Spring.

## 6. Kiểm thử hai cách và giới hạn lời gọi qua proxy

`src/test/java/example/AdviceModesTest.java`:

```java
package example;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdviceModesTest {
    private static void assertMatchingBehavior(
            MessageService service, TraceLog log) {

        assertTrue(AopUtils.isAopProxy(service));
        assertEquals("hello:Lan", service.greet("Lan"));
        assertEquals("SKIP", service.ignored());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, service::fail);
        assertEquals("boom", failure.getMessage());

        assertEquals(List.of(
                "before:greet",
                "target:greet",
                "after:greet",
                "target:ignored",
                "before:fail",
                "target:fail",
                "error:fail"
        ), log.snapshot());
    }

    @Test
    void lowLevelAdviceIsBoundByAdvisorAndPointcut() {
        App.Fixture fixture = App.lowLevelFixture();
        assertMatchingBehavior(fixture.service(), fixture.log());
    }

    @Test
    void annotatedAspectIsAdaptedToProxyAdvice() {
        try (var context =
                new AnnotationConfigApplicationContext(App.Config.class)) {
            assertMatchingBehavior(
                    context.getBean(MessageService.class),
                    context.getBean(TraceLog.class));
        }
    }
}
```

Chạy:

```bash
mvn -q test
```

Test kiểm tra **hành vi public**, không phụ thuộc tên class proxy do Spring sinh ra. Với lời gọi `service.greet()` qua proxy, advice được thực thi; khi target không khớp pointcut, không có thêm event; khi lỗi xảy ra, interceptor giữ exception gốc.

**Một phép thử bổ sung hữu ích:** tạo trực tiếp `new MessageService(new TraceLog())`, gọi `greet`, xác nhận chỉ có `target:greet`. Kết quả đó không phủ nhận pointcut: nó chứng minh lời gọi không đi qua Spring proxy thì không có advice.

## 7. Spring biến `@Aspect` thành những gì?

Điểm quan trọng khi đọc mã hoặc debug ứng dụng:

1. `@Aspect` là **mô tả aspect bằng annotation**. Spring cần tìm bean aspect và phân tích các phương thức advice/pointcut.
2. `@EnableAspectJAutoProxy` (hoặc `<aop:aspectj-autoproxy/>` trong XML) bật xử lý Spring AOP theo phong cách `@AspectJ`.
3. Spring dùng infrastructure như auto-proxy creator, advisor factory và các lớp advice adapter để biến cấu hình annotation thành **advisor áp dụng được lên Spring proxy**.
4. Khi caller gọi proxy, framework chọn chuỗi interceptor phù hợp và thực thi theo thứ tự; nội dung `@Around` gọi `ProceedingJoinPoint.proceed()` để chuyển tiếp.
5. Các advisor khác, chẳng hạn transaction hoặc async, có thể cùng tham gia chuỗi. Khi nhiều advisor cùng khớp, thứ tự là một phần của hành vi cần kiểm thử.

**Lý do Spring vẫn có API cấp thấp:** framework, thư viện hạ tầng hoặc mô-đun muốn tạo/gắn advisor trực tiếp có thể làm việc qua các giao diện `Advice` / `Advisor` mà không cần khai báo mọi quy tắc bằng `@Aspect`. Nhiều ứng dụng nghiệp vụ thường dễ đọc hơn khi dùng `@Aspect`, nhưng điều đó không làm giao diện cấp thấp trở nên không cần thiết.

### `@AspectJ` không đồng nghĩa weaving

| Tình huống | Cơ chế | Phạm vi có thể chặn |
| --- | --- | --- |
| Spring `@Aspect` + `@EnableAspectJAutoProxy` | Spring AOP proxy | Chủ yếu thực thi method khi lời gọi đi qua Spring proxy |
| `ProxyFactory` + `DefaultPointcutAdvisor` | Spring AOP proxy | Cùng giới hạn proxy; advisor được gắn bằng code |
| AspectJ compile-time/load-time weaving được cấu hình riêng | Bytecode weaving của AspectJ | Có thể chặn join point khác tùy AspectJ và cấu hình |

`@AspectJ` là **kiểu khai báo**, không phải cam kết rằng constructor, field access hay self-invocation tự được chặn. Ở chế độ Spring AOP proxy, `this.otherMethod()` trong target thường bỏ qua proxy. Phương thức `private` không được chặn theo cách thông thường; phương thức `final` không thể được override bởi proxy dựa trên lớp.

## 8. Những khác biệt kỹ thuật khi chọn lời khuyên phù hợp

### `@Before` so với `MethodBeforeAdvice`

Hai cách đều chạy trước target. Dạng cấp thấp nhận trực tiếp `Method`, `args`, `target`; dạng annotation có thể nhận `JoinPoint` và các tham số được bind bởi expression. Nếu chỉ cần ghi audit “method đang được gọi”, dùng before advice sẽ rõ ý nghĩa hơn around advice.

### `@AfterReturning` so với `AfterReturningAdvice`

Cả hai quan sát đường trả về bình thường, không tự thay thế tham chiếu kết quả. Trong `@AspectJ`, có thể bind giá trị bằng `@AfterReturning(pointcut="...", returning="result")` và khai báo tham số `result`. Nếu method trả về `CompletableFuture`, cần phân biệt thời điểm **trả future** với lúc **future hoàn tất**.

### `@AfterThrowing` so với `ThrowsAdvice`

Cả hai quan sát lỗi phù hợp; trong kiểu annotation, thuộc tính `throwing` giúp bind exception. `@AfterThrowing` **không phải callback tổng quát cho lỗi từ mọi loại advice khác**; theo hợp đồng Spring/AspectJ, nó nhằm quan sát exception phát sinh ở join point được chọn. Với low-level `MethodInterceptor` bọc chuỗi ở ngoài, `catch(Throwable)` có thể nhìn thấy lỗi từ các interceptor nằm bên trong cũng như từ target, tùy thứ tự advisor.

### `@After` so với `MethodInterceptor`

`@After` là advice kiểu finally, chạy sau khi kết thúc join point bất kể thành công/lỗi. `MethodInterceptor` có thể đạt hiệu quả tương đương bằng `try/finally`, nhưng API này còn có nhiều quyền hơn: thay đối số với `MethodInvocation` phù hợp, thay kết quả hoặc chặn target. Không dùng quanh một method chỉ để ghi một log `before` nếu không cần.

### `@Around` và thứ tự advice

`@Around` có quyền gọi `proceed()` và trả giá trị mới. Với hai aspect cùng chặn method, thứ tự Spring áp dụng dựa trên `@Order`/`Ordered` hoặc thứ tự advisor được cấu hình; aspect có precedence cao chạy trước trên đường đi vào và sau trên đường đi ra. **Không suy đoán thứ tự theo vị trí các method trong source code**. Các loại advice khác nhau trong cùng một `@Aspect` cũng có các quy tắc precedence riêng; test nếu thứ tự ảnh hưởng giao dịch, log, security hoặc exception.

## 9. Cần phân biệt Advice, Advisor, interceptor chain và Auto-proxy creator

Một số hiểu nhầm khi debug:

- **“Tạo bean `MethodInterceptor` là tự động chặn mọi bean.”** Sai: cần đưa advice vào một advisor/chuỗi proxy phù hợp, hoặc có cấu hình infrastructure tự phát hiện nó. `ProxyFactory.addAdvisor(...)` trong ví dụ làm bước này tường minh.
- **“Thêm `@Aspect` là chạy.”** Chưa đủ: cần bean aspect thuộc Spring context và kích hoạt xử lý `@AspectJ`. Tạo instance bằng `new` ở ngoài không làm Spring áp dụng nó.
- **“`@EnableAspectJAutoProxy` biến mọi method thành interception.”** Sai: chỉ method khớp pointcut, được proxy và có khả năng chặn mới có advice.
- **“Pointcut có nhiệm vụ log/validate.”** Sai: pointcut chọn nơi áp dụng; advice mới thực hiện hành vi.
- **“Một `Advisor` luôn chứa nhiều method advice.”** Trong mô hình API, pointcut advisor kết hợp **một Advice object** với phạm vi; trong class `@Aspect` có thể có nhiều method advice, Spring tạo các advisor liên quan.
- **“Gắn cả low-level advisor và `@Aspect` sẽ chỉ chạy một lần.”** Sai: nếu cả hai cùng khớp và đều trong chuỗi, **cả hai** có thể chạy; cần kiểm tra việc chặn trùng, thứ tự và hệ quả như log đôi.
- **“Callback `after-returning` có thể thay giá trị trả về.”** Sai: để thay thế tham chiếu, dùng interceptor/around và trả kết quả khác một cách rõ ràng.

### Đọc chuỗi advisor trên proxy

Spring cung cấp `org.springframework.aop.framework.Advised` để quan sát advisors của proxy Spring khi phù hợp:

```java
if (proxy instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

Đây là trích đoạn gỡ lỗi, cần import `org.springframework.aop.Advisor` và `org.springframework.aop.framework.Advised`, với biến `proxy` là object thật. Việc bean implement `Advised` phụ thuộc cách proxy được tạo, nên không áp đặt nó như một hợp đồng của mọi Java object. Đừng đưa logic phụ thuộc tên implementation class của advisor vào nghiệp vụ.

## 10. Chọn API nào trong dự án Spring?

| Trường hợp | Cách thường phù hợp | Lý do |
| --- | --- | --- |
| Logging/metrics đơn giản trên phương thức nghiệp vụ | `@Aspect` với `@Before`/`@Around` phù hợp | Điểm chặn và hành vi đọc gần nhau |
| Chỉ muốn log trước hoặc log sau thành công | `MethodBeforeAdvice` / `AfterReturningAdvice` khi cấu hình API cấp thấp | Hợp đồng API hẹp, ít quyền can thiệp |
| Viết thư viện tích hợp Spring AOP, advisor được tạo động | `Advice` + `Pointcut` + `DefaultPointcutAdvisor` | Dễ kết nối vào infrastructure, thay pointcut bằng Java |
| Cần bao quanh invocation, đổi kết quả hoặc quản lý exception | `MethodInterceptor` hoặc `@Around` | Quản lý chính xác `proceed()` và kết quả |
| Cần tập hợp nhiều advice cho một concern | `@Aspect` + named `@Pointcut` | Cấu hình tập trung, ít wiring trực tiếp |
| Thêm interface cho proxy | Introduction advisor hoặc `@DeclareParents` | Khác ordinary method pointcut; cần test interface bổ sung |
| Muốn chặn call nội bộ/field/constructor | Đánh giá AspectJ weaving riêng nếu thật sự cần | Spring proxy AOP không chặn loại join point đó |

Một tiêu chí tốt là **chọn biểu diễn ít quyền nhất mà vẫn đáp ứng yêu cầu**. Đo trước khi gọi phương thức có thể dùng `@Before`. Thay kết quả hoặc chặn thực thi đòi around/interceptor, đi kèm trách nhiệm bảo toàn hợp đồng.

## 11. Test các trường hợp khó thường bị bỏ sót

### 11.1. Exception từ target và exception từ advice khác

Trong chuỗi nhiều interceptor, nếu một interceptor bên trong ném lỗi, interceptor bao ngoài có thể nhìn thấy lỗi đó ở `catch`; trong khi đó `@AfterThrowing` được định nghĩa để quan sát lỗi ở join point, không phải tự động quan sát mọi lỗi phát sinh từ advice đi kèm. Test cụ thể theo thứ tự proxy nếu việc thống kê lỗi có vai trò quan trọng.

### 11.2. Transaction và async

Nếu advice chạy cùng `@Transactional`, thứ tự advisor quyết định log/metrics quan sát phần nào của quá trình. `@Async` có thể chuyển thực thi sang một worker khác; advice trên method được chuyển luồng có thể chạy ở worker, và `ThreadLocal` ở caller không tự đi theo. Những điều này là **hành vi của chuỗi Spring proxy và executor**, không phải vì chọn giao diện cấp thấp hay `@AspectJ`.

### 11.3. Thread safety của advice

Các bean advice thường được tái sử dụng ở nhiều lời gọi; không lưu state thay đổi của **một invocation** vào field singleton như `currentStartTime` hoặc `currentUser`. Với around advice, dùng biến cục bộ trong `invoke(...)`/`around(...)` để giữ dữ liệu từng lời gọi. Nếu cần context theo luồng, phải thiết kế propagation và cleanup đúng khi đổi executor.

### 11.4. Giữ nguyên lỗi và kiểu trả về

Một `MethodInterceptor` có thể bỏ qua `proceed()`, hoặc trả object khác; `@Around` cũng vậy. Với method có kiểu trả về cụ thể, trả sai kiểu hoặc `null` cho primitive có thể khiến caller lỗi ở proxy. Khi chỉ ghi log/metrics, cách an toàn thường là trả nguyên kết quả `proceed()` và ném lại nguyên exception.

### 11.5. Kiểm thử self-invocation

Nếu `MessageService.greet()` gọi `this.fail()`, `fail()` được gọi nội bộ trên target và thông thường không đi qua proxy. Vì vậy sẽ không có advice riêng cho lần gọi nội bộ dù pointcut khớp. Tách nghiệp vụ sang Spring bean khác hoặc thiết kế rõ ranh giới proxy nếu phải chặn lời gọi đó.

## 12. Bài thực hành kiểm chứng

Từ dự án ở mục 5:

1. Đổi cấu hình `NameMatchMethodPointcut` thành chỉ chọn `greet`. Chạy `mvn -q test`: test phải thất bại ở phần event `fail` vì phạm vi advisor đã thay đổi.
2. Đổi biểu thức `@Around` chỉ còn `greet`. Kiểm tra cùng hiện tượng đối với nhánh `@AspectJ`. Điều này chứng minh **pointcut trong hai phong cách đều điều khiển nơi chạy advice**.
3. Viết thêm `MethodBeforeAdvice` và `AfterReturningAdvice` cho `greet` bằng hai `DefaultPointcutAdvisor` riêng. Kiểm tra callback trước và sau trả về; tránh giả định callback sau thành công chạy khi target ném exception.
4. Thêm `FailureLogAdvice implements ThrowsAdvice` cho `fail`, rồi test exception được chuyển tới method `afterThrowing` nhưng vẫn tới caller.
5. Dùng `@After` trong `TraceAspect` để ghi sự kiện `finally`, test cả `greet` và `fail`. Chú ý khi nhiều advice cùng match, thứ tự chính xác phải được kiểm chứng theo Spring advisor order.
6. Thử gọi `new MessageService(new TraceLog()).greet("Lan")` và so sánh với proxy. Giải thích vì sao chỉ có sự kiện target.
7. Thêm method trả `CompletableFuture` và quyết định rõ bạn cần ghi sự kiện **khi trả future** hay **khi future hoàn tất**. Thiết kế callback phù hợp, không suy luận rằng after-returning đã đợi task chạy xong.

Học xong, bạn cần phân biệt được **Advice thực thi gì**, **Pointcut chọn nơi nào**, **Advisor gắn chúng lại**, **Spring proxy chạy chain ra sao**, và **`@AspectJ` là một cách khai báo để Spring tạo hạ tầng ấy**. Khi hiểu mối quan hệ, việc đọc mã nguồn các thư viện Spring dùng low-level advisor và code ứng dụng dùng `@Aspect` sẽ nhất quán.

### Tài liệu Spring tham khảo

- Spring Framework — Advice API (các kiểu, lifecycle, MethodInterceptor, ThrowsAdvice): https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html
- Spring Framework — Advisor API: https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html
- Spring Framework — ProxyFactory và API AOP cấp thấp: https://docs.spring.io/spring-framework/reference/core/aop-api.html
- Spring Framework — `@AspectJ` support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Declaring Advice bằng `@Before`/`@Around`/...: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — Spring AOP proxies và self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-advisor-api-002">MethodInterceptor và MethodInvocation trong chuỗi gọi</a>

<details>
<summary>Xem chi tiết</summary>

# MethodInterceptor và MethodInvocation trong chuỗi gọi của Spring AOP

## 1. Mục tiêu và phạm vi

Trong **Spring AOP**, một lời gọi qua proxy có thể đi qua nhiều advice trước khi đến phương thức nghiệp vụ. Với kiểu **around advice theo API của Spring**, mỗi thành phần xử lý quanh lời gọi thường triển khai `org.aopalliance.intercept.MethodInterceptor`; đối tượng `org.aopalliance.intercept.MethodInvocation` đại diện cho *một lượt gọi phương thức đang được xử lý* và cung cấp hàm `proceed()` để chuyển quyền thực thi cho mắt xích kế tiếp.

Bài này tập trung vào cách **Spring Framework xây dựng và chạy interceptor chain**: quan hệ giữa `Pointcut`, `Advisor`, `MethodInterceptor`, `MethodInvocation`, thứ tự trước/sau `proceed()`, biến đổi đối số và kết quả, ngoại lệ và cách đọc trace thực tế. Các khái niệm AOP tổng quát không phải trọng tâm.

Sau bài này, bạn có thể:

- Viết `MethodInterceptor` Spring AOP và gắn vào `ProxyFactory` thông qua `DefaultPointcutAdvisor`.
- Giải thích chính xác vì sao hai interceptor chạy kiểu lồng nhau và vì sao phần “after” chạy theo chiều ngược.
- Phân biệt dữ liệu của `MethodInvocation` với API mở rộng của Spring `ProxyMethodInvocation`.
- Tránh lỗi bỏ `proceed()`, gọi `proceed()` nhiều lần, nuốt ngoại lệ hoặc hiểu nhầm target với proxy.

## 2. Các vai trò trong Spring AOP

| Thành phần | Kiểu/API chính | Trách nhiệm |
| --- | --- | --- |
| **Proxy** | `ProxyFactory`, JDK proxy hoặc class-based proxy | Đón lời gọi từ client và điều phối advice áp dụng |
| **Điều kiện chọn** | `org.springframework.aop.Pointcut` | Quyết định lớp và phương thức nào đủ điều kiện |
| **Liên kết** | `org.springframework.aop.Advisor`, thường là `DefaultPointcutAdvisor` | Ghép `Pointcut` với `Advice` |
| **Around advice** | `org.aopalliance.intercept.MethodInterceptor` | Chạy logic trước/sau, chuyển tiếp hoặc chủ động kết thúc lời gọi |
| **Ngữ cảnh lời gọi** | `org.aopalliance.intercept.MethodInvocation` | Cung cấp method, arguments, target và `proceed()` |
| **Đích cuối** | Phương thức của target | Chạy nghiệp vụ nếu chuỗi được đi đến cuối |

Chữ ký cốt lõi là:

```java
// Hai interface từ AOP Alliance; đây là phần biểu diễn chữ ký API.
public interface MethodInterceptor extends Interceptor {
    Object invoke(MethodInvocation invocation) throws Throwable;
}

public interface MethodInvocation extends Invocation {
    Method getMethod();
}
```

`MethodInvocation` kế thừa qua `Invocation`/`Joinpoint` những thao tác như `getArguments()`, `getThis()`, `getStaticPart()`, `proceed()`. Đây là **đoạn mô tả chữ ký API**, không phải file Java độc lập để chép biên dịch; ví dụ chạy đầy đủ nằm ở phần 4.

Một lời gọi ở phía client được Spring xử lý gần như sau:

```text
Client -> Spring AOP proxy
           |
           +-- Chọn những Advisor có Pointcut phù hợp
           |
           +-- Interceptor A.invoke(invocation)
                 | trước proceed
                 +-- invocation.proceed()
                       +-- Interceptor B.invoke(invocation)
                             | trước proceed
                             +-- invocation.proceed()
                                   +-- Target.method()
                             | sau proceed
                 | sau proceed
           |
Client <- giá trị trả về / ngoại lệ
```

Đây là mô hình **lồng lời gọi**. Spring AOP chạy trong tiến trình Java với proxy; interceptor của ví dụ này không đồng nghĩa với Servlet `HandlerInterceptor` trong Spring MVC.

## 3. Ý nghĩa của invoke() và proceed()

`MethodInterceptor.invoke(MethodInvocation invocation)` trả về `Object` và được phép ném `Throwable`. Phần tử hiện tại có quyền quyết định có gọi `invocation.proceed()` hay không.

Một interceptor “trong suốt” điển hình:

```java
MethodInterceptor tracing = invocation -> {
    System.out.println("before");
    Object result = invocation.proceed();
    System.out.println("after");
    return result;
};
```

Ba điều quan trọng:

1. `before` chạy **trước** khi xuống mắt xích tiếp theo, không nhất thiết trước target ngay lập tức: vẫn có thể còn interceptor khác.
2. `proceed()` chuyển đến **interceptor tiếp theo**, hoặc gọi phương thức target khi đã hết chain. Giá trị trả về sẽ chảy ngược qua những interceptor đang chờ.
3. Nếu interceptor ném ngoại lệ hoặc không gọi `proceed()`, các mắt xích phía sau (và target) sẽ không chạy trên nhánh đó. Nếu `proceed()` ném ngoại lệ thì lệnh `after` ở ví dụ trên sẽ bị bỏ qua.

Bởi vậy phần sau `proceed()` khác với `finally`: muốn dọn tài nguyên bất kể thành công/thất bại, đặt trong `finally`.

```java
MethodInterceptor cleanup = invocation -> {
    System.out.println("enter");
    try {
        return invocation.proceed();
    } finally {
        System.out.println("leave"); // kể cả khi interceptor sau hoặc target ném lỗi
    }
};
```

Ở chain `A -> B -> target`, thứ tự khi thành công là `A-before -> B-before -> target -> B-after -> A-after`. Khi target ném lỗi, nếu A và B dùng `finally`, thứ tự sẽ là `A-before -> B-before -> target throws -> B-finally -> A-finally`, rồi ngoại lệ đi ngược về client nếu không bị chặn.

**Quy tắc vận hành an toàn:** với advice thông thường, mỗi `invoke()` gọi `proceed()` **đúng một lần**, trả về kết quả tương thích với phương thức được intercept và để ngoại lệ truyền đi trừ khi có chủ đích xử lý.

## 4. Ví dụ Java 17+ chạy được: hai interceptor trên cùng một lời gọi

Ví dụ dùng **Spring Framework 6.2.14** và Gradle. Chỉ method `greet(String)` được tư vấn bởi hai `DefaultPointcutAdvisor`; method `ping()` không đi vào hai interceptor. Không cần tạo Spring ApplicationContext để minh họa `ProxyFactory`.

Cấu trúc project:

```text
spring-aop-chain-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/InterceptorChainDemo.java
```

**`settings.gradle`:**

```groovy
rootProject.name = 'spring-aop-chain-demo'
```

**`build.gradle`:**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-aop:6.2.14'
}

application {
    mainClass = 'InterceptorChainDemo'
}
```

**`src/main/java/InterceptorChainDemo.java`:**

```java
import java.util.Locale;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class InterceptorChainDemo {

    public interface GreetingService {
        String greet(String name);
        String ping();
    }

    public static final class DefaultGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            System.out.println("[target] greet: " + name);
            return "Hello, " + name;
        }

        @Override
        public String ping() {
            System.out.println("[target] ping");
            return "pong";
        }
    }

    public static void main(String[] args) {
        NameMatchMethodPointcut onlyGreet = new NameMatchMethodPointcut();
        onlyGreet.setMappedName("greet");

        MethodInterceptor audit = invocation -> {
            String methodName = invocation.getMethod().getName();
            Object firstArgument = invocation.getArguments()[0];
            System.out.println("[audit] before: " + methodName + "("
                    + firstArgument + ")");

            Object result = invocation.proceed();

            System.out.println("[audit] after: " + result);
            return result;
        };

        MethodInterceptor uppercase = invocation -> {
            System.out.println("[uppercase] before");
            String result = (String) invocation.proceed();
            System.out.println("[uppercase] after");
            return result.toUpperCase(Locale.ROOT);
        };

        ProxyFactory factory = new ProxyFactory(new DefaultGreetingService());
        factory.setInterfaces(GreetingService.class);
        factory.addAdvisor(new DefaultPointcutAdvisor(onlyGreet, audit));
        factory.addAdvisor(new DefaultPointcutAdvisor(onlyGreet, uppercase));

        GreetingService proxy = (GreetingService) factory.getProxy();
        System.out.println("result=" + proxy.greet("Lan"));
        System.out.println("ping=" + proxy.ping());
    }
}
```

Chạy tại thư mục project bằng Gradle đã cài hoặc Gradle Wrapper của project:

```bash
gradle run --quiet
# Hoặc: ./gradlew run --quiet  (nếu project có Gradle Wrapper)
```

**Stdout của chương trình** (không tính thông báo môi trường/build tool):

```text
[audit] before: greet(Lan)
[uppercase] before
[target] greet: Lan
[uppercase] after
[audit] after: HELLO, LAN
result=HELLO, LAN
[target] ping
ping=pong
```

Đọc trace từ trên xuống:

1. Client gọi `proxy.greet("Lan")`; proxy chọn hai advisor vì pointcut theo tên `greet` khớp.
2. `audit` nằm trước trong danh sách advisor nên vào `invoke()` trước. `getMethod().getName()` trả về `greet`, `getArguments()[0]` là `Lan`.
3. Khi `audit` gọi `proceed()`, Spring gọi `uppercase`. Interceptor thứ hai log “before” rồi gọi `proceed()`.
4. Đã hết interceptor phù hợp nên Spring chạy `DefaultGreetingService.greet`, nhận `"Hello, Lan"`.
5. `uppercase` nhận giá trị từ `proceed()`, in “after” rồi trả về `"HELLO, LAN"`.
6. `audit` nhận **kết quả đã qua xử lý của `uppercase`**, in “after” và trả kết quả cho client.
7. Client gọi `proxy.ping()`: hai pointcut không khớp nên không có dòng `[audit]` hay `[uppercase]`; target chạy trực tiếp theo đường đi proxy.

`factory.addAdvisor(...)` thêm advisor vào cuối chain. Trong **ví dụ cấu hình trực tiếp này**, thứ tự đăng ký quyết định vòng bên ngoài/bên trong. Nếu đổi hai lệnh `addAdvisor`, thứ tự log và vị trí áp dụng phép chuyển chữ hoa cũng thay đổi theo. Trong hệ thống dùng Spring IoC, thứ tự có thể chịu ảnh hưởng của bộ sắp xếp advisor, `Ordered`/`@Order`, cũng như các advisor hạ tầng: cần xem chain thực tế thay vì suy từ tên bean.

**Điểm cốt lõi:** `MethodInterceptor` có thể thay đổi kết quả bằng cách xử lý giá trị trả về từ `proceed()`. Do đó interceptor ngoài cùng quan sát giá trị mà các interceptor bên trong đã trả ra, không nhất thiết là kết quả gốc của target.

## 5. Các phương thức của MethodInvocation nên biết

| API | Giá trị và ý nghĩa trong Spring AOP | Lưu ý |
| --- | --- | --- |
| `getMethod()` | `java.lang.reflect.Method` mô tả method được gọi qua proxy | Với JDK proxy có thể là method khai báo trên interface, không nhất thiết method cụ thể nhất của target |
| `getArguments()` | Mảng đối số của lượt gọi | Có thể sửa giá trị phần tử để ảnh hưởng phần còn lại của chain và target; không đổi số lượng/kiểu sai chữ ký |
| `getThis()` | Đối tượng target đang giữ điểm gọi | **Không mặc định là AOP proxy**; theo hợp đồng chung có thể `null` trong vài dạng join point |
| `getStaticPart()` | Mô tả phần tĩnh của join point; với method là đối tượng `Method` | Đừng đồng nhất với danh sách các interceptor |
| `proceed()` | Tiếp tục chain; cuối chain sẽ đến target | Chứa logic thực thi, có thể trả kết quả hoặc ném `Throwable` |

**Phân biệt API AOP Alliance và API mở rộng của Spring:**

- `org.aopalliance.intercept.MethodInvocation` cung cấp ngữ cảnh tối thiểu và `proceed()`.
- `org.springframework.aop.ProxyMethodInvocation` **mở rộng** `MethodInvocation`, có thêm `getProxy()`, `setArguments(...)`, `getUserAttribute(...)`, `setUserAttribute(...)`, `invocableClone(...)`.
- `org.springframework.aop.framework.ReflectiveMethodInvocation` là một lớp triển khai của Spring, dùng để thực thi lời gọi qua chuỗi interceptor và đi tới target bằng reflection.

Ví dụ kiểm tra có hỗ trợ API mở rộng trước khi sử dụng:

```java
// Đoạn minh họa bên trong MethodInterceptor.invoke(...).
if (invocation instanceof org.springframework.aop.ProxyMethodInvocation proxyCall) {
    Object proxy = proxyCall.getProxy();
    Object target = invocation.getThis();
    System.out.println("same reference? " + (proxy == target));
}
```

Kết quả so sánh `proxy == target` phụ thuộc ngữ cảnh; với Spring AOP proxy thông thường đây là hai tham chiếu khác nhau. Đoạn này là **minh họa trong một interceptor**, không phải một chương trình độc lập.

`ProxyMethodInvocation.invocableClone()` hỗ trợ tạo bản sao một lượt gọi để có thể thực thi lại phần chain còn lại trong kịch bản đặc biệt. Việc clone là **shallow copy** của cấu trúc lời gọi, không tự làm các đối tượng nghiệp vụ thành bất biến, không ngăn tác dụng phụ và không tạo transaction mới. Không dùng nó làm “retry” mặc định cho ghi database, gửi email hay phát message.

## 6. Truyền dữ liệu, thay đổi đối số và kết quả

### Thay đổi đối số trước khi xuống chain

Vì `getArguments()` cho phép truy cập mảng đối số, interceptor có thể chuẩn hóa tham số trước `proceed()`:

```java
// Đoạn minh họa: advice này chỉ áp dụng cho method có đúng một String.
MethodInterceptor trimFirstArgument = invocation -> {
    Object[] args = invocation.getArguments();
    if (args.length == 1 && args[0] instanceof String s) {
        args[0] = s.trim();
    }
    return invocation.proceed();
};
```

Các interceptor ở **phía sau** cùng target sẽ thấy giá trị đã được thay đổi. Nếu yêu cầu giữ dữ liệu đầu vào nguyên bản để audit, hãy snapshot trước khi sửa. Tránh ép kiểu thiếu kiểm tra, tránh sửa arguments dùng chung qua các luồng, và chỉ thay đổi đối số khi đây là hợp đồng được thiết kế rõ ràng.

`ProxyMethodInvocation.setArguments(Object...)` cũng cho phép đặt mảng đối số trên implementation của Spring, nhưng điều này là API mở rộng; không nên giả định mọi `MethodInvocation` từ hệ sinh thái AOP đều hỗ trợ.

### Thay đổi giá trị trả về

Interceptor có thể bọc, chuẩn hóa, cache hoặc biến đổi giá trị:

```java
// Đoạn minh họa: chỉ áp dụng cho method trả về String.
MethodInterceptor decorate = invocation -> {
    String value = (String) invocation.proceed();
    return "[served] " + value;
};
```

Giá trị cuối phải tương thích kiểu trả về của phương thức: trả `null` cho phương thức có return type nguyên thủy hoặc trả `String` cho method trả `int` có thể gây lỗi khi proxy chuyển kết quả về kiểu mà caller mong đợi. Đừng áp dụng advice ép `String` cho mọi method không có pointcut phù hợp.

### Ngăn hoặc thay thế lời gọi

```java
// Đoạn minh họa: chỉ áp dụng cho method trả về String.
MethodInterceptor fallback = invocation -> {
    if (invocation.getArguments().length == 1
            && "guest".equals(invocation.getArguments()[0])) {
        return "Access denied";
    }
    return invocation.proceed();
};
```

Với nhánh `guest`, không có `proceed()` nên các interceptor đứng **phía sau** và target không chạy; interceptor đứng **phía trước** vẫn tiếp tục phần sau `proceed()` khi giá trị `"Access denied"` quay về. Đây là quyền hạn của **around advice** và cần được kiểm soát chặt, đặc biệt nếu phương thức thực hiện side effect hoặc trả về kiểu khác `String`.

## 7. Ngoại lệ và cleanup trong chuỗi

Giả sử `A` và `B` đều bọc `proceed()`:

```java
MethodInterceptor logging = invocation -> {
    System.out.println("start");
    try {
        Object result = invocation.proceed();
        System.out.println("success");
        return result;
    } catch (Throwable ex) {
        System.out.println("failed: " + ex.getClass().getSimpleName());
        throw ex;
    } finally {
        System.out.println("cleanup");
    }
};
```

Nếu target ném ngoại lệ, interceptor trong cùng sẽ xử lý trước interceptor ngoài. `catch (Throwable)` ở đây minh họa việc rethrow chính xác chữ ký `invoke(...)`; trong nghiệp vụ, chỉ bắt loại ngoại lệ phù hợp, không chuyển lỗi thành thành công tùy tiện. `finally` phù hợp để giải phóng tài nguyên, ghi thời gian và các việc phải xảy ra trong cả hai trường hợp.

Nếu advice muốn xử lý lỗi bằng giá trị dự phòng, cần xác định điều kiện phục hồi, kiểu trả về, khả năng lặp tác dụng phụ, tính nhất quán dữ liệu và hợp đồng transaction. Một interceptor nuốt ngoại lệ có thể làm caller tưởng nghiệp vụ đã thành công.

## 8. Vì sao thứ tự và phạm vi advisor quan trọng?

Khi Spring nhận lời gọi qua AOP proxy, framework xác định danh sách advisor/interceptor phù hợp với phương thức. Interceptor có thứ tự cao hơn ở đầu chain sẽ **vào trước và thoát sau** những interceptor ở bên trong.

```text
Đăng ký: A rồi B

Thành công:
A.before -> B.before -> target -> B.after -> A.after

B ném ngoại lệ:
A.before -> B.before -> B throws -> A.catch/finally -> caller

A chặn trước proceed:
A.before -> A.return -> caller (B và target không chạy)
```

Khi xây proxy thủ công, bạn có thể kiểm tra danh sách advisor từ `org.springframework.aop.framework.Advised`:

```java
// Đoạn minh họa sau khi có proxy:
org.springframework.aop.framework.Advised advised =
        (org.springframework.aop.framework.Advised) proxy;

for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
    System.out.println(advisor.getClass().getSimpleName());
}
```

`getAdvisors()` cho biết các advisor được cấu hình, nhưng **không chứng minh tất cả đều chạy** cho một method: mỗi pointcut phải phù hợp thì advice mới xuất hiện trong chuỗi thực thi của lời gọi đó. Lọc theo method và điều kiện runtime có thể tiếp tục thu hẹp những phần tử được gọi.

Trong ứng dụng Spring thực, các tính năng như transaction, caching và security cũng có thể đưa advice vào đường đi của proxy thông qua advisor tương ứng. Do đó vị trí của một interceptor dùng để log “trước/sau transaction” phải được xác minh dựa trên **thứ tự advisor thật** và đường gọi thật; không thể mặc định mọi advice đứng cùng một tầng.

### Liên hệ với @AspectJ trong Spring

Với mã dùng `@Aspect`/`@Around`, bạn thường thấy `org.aspectj.lang.ProceedingJoinPoint` và `proceed()`. Spring chuyển các khai báo advice thích hợp thành advisor/interceptor tương ứng trong cơ chế Spring AOP, nhưng `ProceedingJoinPoint` **không phải** `MethodInvocation` và không nên ép kiểu qua lại. Bài này hướng dẫn **API programmatic `MethodInterceptor`** khi viết advice/extension Spring AOP; không thay thế tài liệu cú pháp biểu thức `@Pointcut`.

## 9. Lỗi thường gặp và cách xử lý

| Hiện tượng | Nguyên nhân dễ gặp | Hướng xử lý |
| --- | --- | --- |
| Method không chạy, không có log target | Interceptor trả sớm hoặc ném lỗi trước `proceed()` | Kiểm tra mọi nhánh `return`/`throw` và trace `proceed()` |
| Log “after” vắng mặt khi target lỗi | “after” đặt sau `proceed()` nhưng không có `finally` | Dùng `try/finally` cho cleanup |
| Interceptor không chạy ở `ping()` | `Pointcut` chỉ match `greet()` | Phân biệt “advisor có trong proxy” với “advisor áp dụng cho method” |
| Biến đổi kết quả làm client lỗi kiểu | Giá trị do advice trả không hợp return type | Giữ đúng hợp đồng return, pointcut chọn hẹp |
| Thấy `getThis()` không phải proxy | Đây là **target** của join point | Dùng `ProxyMethodInvocation.getProxy()` nếu thực sự cần Spring proxy và invocation hỗ trợ |
| Một lời gọi thực thi target nhiều lần | `proceed()` bị gọi lại trong cùng logic/clone, hoặc retry chủ động | Quy định một lần mặc định; retry phải có chính sách và tính idempotent |
| Advice không intercept lời gọi từ method cùng target | **Self-invocation** qua `this.method()` bỏ qua proxy | Thiết kế đường gọi qua bean/proxy phù hợp hoặc tách trách nhiệm |
| Advice chỉ thấy method thuộc interface | Proxy theo JDK interface | Xét kiểu proxy và method trên interface; nếu cần class-based proxy, hiểu giới hạn final/private |
| Trông như “đảo thứ tự” advice | Advisor đăng ký/sắp xếp khác kỳ vọng | Xem `Advised.getAdvisors()`, cấu hình `Ordered`/`@Order` và trace thực thi |

Ngoài ra, `MethodInterceptor` của AOP Alliance khác hoàn toàn `HandlerInterceptor` dùng xử lý request MVC. `HandlerInterceptor.preHandle/postHandle/afterCompletion` làm việc ở luồng request web, còn `MethodInvocation.proceed()` đi trong **chuỗi advice của lời gọi method qua Spring AOP proxy**.

## 10. Tự kiểm tra

**Câu 1.** Với chain `Audit -> Normalize -> Service`, thứ tự log `before/after` khi không có ngoại lệ là gì?

**Trả lời:** `Audit.before -> Normalize.before -> Service -> Normalize.after -> Audit.after`. Đây là hệ quả trực tiếp của lời gọi lồng nhau qua `proceed()`.

**Câu 2.** Interceptor ngoài gọi `Object result = invocation.proceed()`, nhưng interceptor trong trả kết quả đã biến đổi. Interceptor ngoài nhận kết quả nào?

**Trả lời:** Kết quả do interceptor trong trả về, tức là **giá trị sau biến đổi**. Interceptor ngoài có thể biến đổi tiếp trước khi trả cho client.

**Câu 3.** Nếu advice cần đóng tài nguyên kể cả khi target ném lỗi, nên đặt vào đâu?

**Trả lời:** Khối `finally` bao quanh `proceed()`.

**Câu 4.** Gọi `getThis()` có phải cách chắc chắn để lấy Spring AOP proxy không?

**Trả lời:** Không. `getThis()` cung cấp target. Nếu cần proxy, kiểm tra `invocation instanceof ProxyMethodInvocation` rồi dùng `getProxy()` trong ngữ cảnh Spring hỗ trợ.

**Câu 5.** Vì sao `factory.addAdvice(interceptor)` có thể làm advice chạy ở nhiều method hơn mong muốn?

**Trả lời:** Spring bọc advice thành advisor có pointcut luôn đúng. Muốn chọn hẹp, dùng `DefaultPointcutAdvisor` với `Pointcut` đúng phạm vi rồi `addAdvisor(...)`.

## Tài liệu tham khảo chính thức

- [Spring Framework Reference 6.2 — Advice API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html).
- [Spring Framework Reference 6.2 — ProxyFactory](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html).
- [Spring Framework Reference 6.2 — Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advised.html).
- [Spring Framework Javadoc 6.2 — ProxyMethodInvocation](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/ProxyMethodInvocation.html).
- [Spring Framework Javadoc 6.2 — ReflectiveMethodInvocation](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/framework/ReflectiveMethodInvocation.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-advisor-api-003">MethodBeforeAdvice, AfterReturningAdvice và ThrowsAdvice</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: `MethodBeforeAdvice`, `AfterReturningAdvice` và `ThrowsAdvice`

## Mục tiêu

Bài học tập trung vào **Advice và Advisor API của Spring AOP**: hiểu thời điểm chạy, chữ ký Java, cách gắn vào `PointcutAdvisor`/`ProxyFactory`, tác động khi có ngoại lệ, cách chọn đúng loại advice và giới hạn của proxy. Sau bài học, bạn có thể tự cấu hình cả ba advice bằng API Spring và phân biệt đường đi **trả về bình thường** với đường đi **ném ngoại lệ**.

> Phạm vi: các interface trong `org.springframework.aop`, cách Spring điều phối chúng qua proxy và advisor. Các ý tưởng AOP chung chỉ được đề cập vừa đủ để hiểu Spring; cú pháp annotation `@Before`, `@AfterReturning`, `@AfterThrowing` thuộc một kiểu cấu hình Spring AOP khác và được phân biệt ở cuối bài.

## 1. Ba loại advice trong Spring AOP

| Interface Spring | Callback | Khi chạy | Có thông tin gì? | Giới hạn quan trọng |
| --- | --- | --- | --- | --- |
| `MethodBeforeAdvice` | `before(Method, Object[], Object)` | Trước khi tiếp tục đến phương thức target | `Method`, tham số, target | Không có `proceed()`; muốn ngăn thực thi thì ném ngoại lệ |
| `AfterReturningAdvice` | `afterReturning(Object, Method, Object[], Object)` | Khi phần xử lý được bao quanh đã trả về **bình thường** | Giá trị trả về, `Method`, tham số, target | Callback trả `void`, không thể thay thế giá trị trả về bằng một giá trị mới |
| `ThrowsAdvice` | `afterThrowing(ExceptionType)` hoặc `afterThrowing(Method, Object[], Object, ExceptionType)` | Khi phần xử lý được bao quanh ném ngoại lệ **khớp kiểu handler** | Ngoại lệ và tùy chọn `Method`, tham số, target | Interface là *marker*, phương thức handler được Spring tìm bằng reflection; không tự động phục hồi hoặc nuốt ngoại lệ |

Cả ba là các advice Spring-specific, được Spring AOP chuyển thành interceptor nội bộ khi lắp chuỗi proxy. Chúng không tự định nghĩa tập phương thức sẽ tác động: **`Pointcut`/`Advisor` quyết định phạm vi áp dụng** và cấu hình proxy quyết định lời gọi nào thực sự đi qua AOP.

Kết quả tổng quát với một lời gọi đồng bộ qua proxy:

```text
Gọi proxy.method(...)
        |
        v
MethodBeforeAdvice.before(...)
        |
        v
    Target method
       /     \
  trả về     ném lỗi
    |           |
    v           v
AfterReturning  ThrowsAdvice.afterThrowing(...)
Advice             (nếu kiểu ngoại lệ khớp)
    |           |
    v           v
trả kết quả   ngoại lệ tiếp tục lan truyền
```

Sơ đồ chỉ thể hiện đường đi điển hình khi ba advice được cấu hình đúng phạm vi và theo thứ tự phù hợp. Trong ứng dụng có nhiều advisor, thứ tự interceptor có thể ảnh hưởng callback nào nhìn thấy ngoại lệ phát sinh từ advice khác.

## 2. `MethodBeforeAdvice` — chặn kiểm tra ở đầu lời gọi

Interface `org.springframework.aop.MethodBeforeAdvice` có chữ ký:

```java
void before(Method method, Object[] args, Object target) throws Throwable;
```

- `method`: phương thức đang được gọi qua proxy.
- `args`: mảng đối số của lời gọi; không nên tùy tiện sửa vì nó có thể ảnh hưởng việc thực thi.
- `target`: đối tượng target được proxy bao; có thể `null` trong một số cấu hình target source.
- `throws Throwable`: cho phép callback báo lỗi để dừng việc tiếp tục thực thi. Spring xử lý/bao gói ngoại lệ theo hợp đồng phương thức và cơ chế proxy.

Ví dụ về một kiểm tra có điều kiện:

```java
MethodBeforeAdvice verifyAccount = (method, args, target) -> {
    if (method.getName().equals("submit")
            && args.length > 0
            && args[0] instanceof String accountId
            && accountId.isBlank()) {
        throw new IllegalArgumentException("accountId must not be blank");
    }
};
```

Đây là **đoạn callback minh họa**, không phải chương trình độc lập. Nó cho thấy before advice thích hợp để log đầu vào, xác minh tiền điều kiện hoặc từ chối thực thi trước khi target chạy. Trong hệ thống thật, nên dùng pointcut chỉ chọn đúng method `submit` và validation phù hợp với hợp đồng domain, tránh dựa vào tên phương thức ở mọi lớp.

Điểm khác với `MethodInterceptor` (around advice): callback `before` **không nhận `MethodInvocation` và không gọi `proceed()`**. Nếu callback kết thúc bình thường, Spring tiếp tục chuỗi. Nếu ném ngoại lệ, chuỗi phía sau có thể không tới target. Không dùng `MethodBeforeAdvice` khi bạn cần tự thay đổi kết quả, chủ động gọi lại target hoặc bảo đảm logic cleanup ở cả hai nhánh kết thúc.

## 3. `AfterReturningAdvice` — quan sát lời gọi thành công

Interface `org.springframework.aop.AfterReturningAdvice` khai báo:

```java
void afterReturning(
        Object returnValue,
        Method method,
        Object[] args,
        Object target
) throws Throwable;
```

Spring gọi callback khi lời gọi được advice bao quanh đã hoàn tất **không ném ngoại lệ**. `returnValue` chứa giá trị kết quả (hoặc `null` nếu phương thức trả `void` hay thực sự trả `null`).

Ví dụ:

```java
AfterReturningAdvice auditSuccess = (result, method, args, target) -> {
    System.out.println("Completed " + method.getName() + ": " + result);
};
```

Callback này có thể ghi audit sự kiện thành công, số lượng operation hoàn thành, hoặc metadata của phản hồi. Nó không chạy khi target ném lỗi và lời gọi đó không được biến thành trả về bình thường bởi một interceptor nằm phía trong chuỗi.

**Không sửa được kết quả bằng cách gán lại tham số**: `returnValue = "another value"` chỉ thay đổi biến cục bộ của callback, không thay đổi giá trị proxy trả về. Đối với đối tượng có thể thay đổi trạng thái (*mutable*), việc sửa trực tiếp object được tham chiếu đến có thể tạo tác dụng phụ; không coi đó là cơ chế biến đổi giá trị trả về được Spring bảo đảm. Nếu yêu cầu là thay thế hoặc biến đổi kết quả trả về, hãy dùng `MethodInterceptor` với logic `Object result = invocation.proceed(); return transform(result);` và thiết kế hợp đồng rõ ràng.

**Ngoại lệ phát sinh ngay trong after-returning advice** có thể khiến caller nhận lỗi dù target đã hoàn thành thành công. Vì vậy thao tác phụ như log/metric cần được thiết kế sao cho không vô tình làm hỏng request nghiệp vụ; nếu audit bắt buộc phải thành công, cần biểu đạt chính sách thất bại một cách rõ ràng.

## 4. `ThrowsAdvice` — xử lý sự kiện phương thức ném ngoại lệ

`org.springframework.aop.ThrowsAdvice` **không khai báo phương thức abstract nào**. Đây là một marker interface. Spring tìm bằng reflection các handler có tên chính xác `afterThrowing` trên lớp triển khai.

Hai dạng chữ ký hợp lệ:

```java
// Chỉ cần ngoại lệ:
public void afterThrowing(MyBusinessException ex) {
    // xử lý sự kiện thất bại
}

// Cần thêm thông tin method/args/target:
public void afterThrowing(
        Method method, Object[] args, Object target, MyBusinessException ex) {
    // xử lý sự kiện thất bại
}
```

Ở đây `MyBusinessException` là **lớp ngoại lệ giả định**, các đoạn trên chỉ minh họa signature; không biên dịch độc lập. Phương thức phải là `public`, tên `afterThrowing`, trả `void`, với **một** tham số ngoại lệ hoặc **bốn** tham số đúng thứ tự. Không có dạng hai hay ba tham số. Kiểu tham số cuối là một kiểu con phù hợp của `Throwable`, thường là `Exception`/`RuntimeException` hoặc một ngoại lệ nghiệp vụ cụ thể.

Ví dụ hiện thực:

```java
import java.lang.reflect.Method;
import org.springframework.aop.ThrowsAdvice;

public class FailureAuditAdvice implements ThrowsAdvice {
    public void afterThrowing(IllegalStateException ex) {
        System.out.println("Domain failure: " + ex.getMessage());
    }

    public void afterThrowing(
            Method method, Object[] args, Object target, IllegalArgumentException ex) {
        System.out.println("Invalid input in " + method.getName());
    }
}
```

Đây là **class độc lập về mặt khai báo** khi có dependency `spring-aop`, nhưng chưa được lắp vào proxy nên tự nó chưa intercept gì. Hai handler cho hai nhóm lỗi khác nhau có thể ở cùng class; khi có lỗi, Spring chọn handler tương ứng theo kiểu ngoại lệ. Không cần annotate `@Override` cho `afterThrowing`: marker interface không khai báo method này, nên annotation đó sẽ gây lỗi biên dịch.

**Xử lý không đồng nghĩa với “bắt và nuốt” ngoại lệ.** Nếu `afterThrowing` hoàn thành bình thường, lỗi gốc tiếp tục lan lên caller. Nếu handler lại ném ngoại lệ khác, ngoại lệ mới có thể thay thế ngoại lệ gốc theo hành vi của Spring. Vì vậy cần cân nhắc kỹ nếu handler định ném checked exception không nằm trong khai báo `throws` của phương thức nghiệp vụ.

Nếu không khai báo handler cho kiểu ngoại lệ phát sinh, advice đó không có callback phù hợp để gọi. Ví dụ chỉ có `afterThrowing(IllegalStateException)` sẽ không xử lý `IllegalArgumentException` không liên quan kế thừa.

## 5. Ví dụ đầy đủ: cùng một proxy cho cả ba advice

Ví dụ dùng **Spring AOP API trực tiếp**, không dùng `@Aspect`. Ta chủ động dựng proxy cho một `OrderService`, gắn ba advisor có `Pointcut.TRUE` vào proxy đó, rồi gọi một method thành công và một method thất bại. Trong phạm vi proxy này, cả ba advisor đều áp dụng lên các method interface được gọi qua proxy.

### 5.1. Cấu trúc và phụ thuộc

```text
spring-advice-demo/
├── pom.xml
└── src/main/java/example/SpringAdviceDemo.java
```

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-advice-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.19</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>example.SpringAdviceDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `src/main/java/example/SpringAdviceDemo.java`:

```java
package example;

import java.lang.reflect.Method;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.ThrowsAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;

public class SpringAdviceDemo {

    public interface OrderService {
        String submit(String orderId);
        String approve(String orderId);
    }

    public static class OrderServiceImpl implements OrderService {
        @Override
        public String submit(String orderId) {
            System.out.println("TARGET submit");
            return "accepted-" + orderId;
        }

        @Override
        public String approve(String orderId) {
            System.out.println("TARGET approve");
            throw new IllegalStateException("blocked-" + orderId);
        }
    }

    public static class FailureAdvice implements ThrowsAdvice {
        public void afterThrowing(
                Method method, Object[] args, Object target, IllegalStateException ex) {
            System.out.println("THROWS " + method.getName() + " " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        MethodBeforeAdvice before = (method, arguments, target) ->
                System.out.println("BEFORE " + method.getName());

        AfterReturningAdvice returned = (result, method, arguments, target) ->
                System.out.println("RETURN " + method.getName() + " " + result);

        ProxyFactory factory = new ProxyFactory(new OrderServiceImpl());
        factory.setInterfaces(OrderService.class);

        factory.addAdvisor(new DefaultPointcutAdvisor(Pointcut.TRUE, before));
        factory.addAdvisor(new DefaultPointcutAdvisor(Pointcut.TRUE, returned));
        factory.addAdvisor(new DefaultPointcutAdvisor(
                Pointcut.TRUE, new FailureAdvice()));

        OrderService proxy = (OrderService) factory.getProxy();

        System.out.println("CLIENT " + proxy.submit("O-7"));
        try {
            proxy.approve("O-8");
        } catch (IllegalStateException ex) {
            System.out.println("CLIENT caught " + ex.getMessage());
        }
    }
}
```

Lệnh chạy tại thư mục chứa `pom.xml` (yêu cầu JDK 17+ và Maven):

```bash
mvn -q compile exec:java
```

Phần stdout do **chương trình** tạo ra:

```text
BEFORE submit
TARGET submit
RETURN submit accepted-O-7
CLIENT accepted-O-7
BEFORE approve
TARGET approve
THROWS approve blocked-O-8
CLIENT caught blocked-O-8
```

Các dòng Maven/plugin khác nếu có không thuộc stdout do mã ví dụ in.

### 5.2. Tại sao kết quả lại như vậy?

1. `ProxyFactory` nhận một target `OrderServiceImpl` và tạo proxy cho interface `OrderService`. Chính biến `proxy` mới đi qua advice.
2. Mỗi `DefaultPointcutAdvisor(Pointcut.TRUE, advice)` kết hợp một **pointcut** với một **advice**. `TRUE` nghĩa là không lọc method thêm trong tập lời gọi proxy này.
3. Khi `submit("O-7")` được gọi, before advice chạy trước target. Target trả `accepted-O-7`, after-returning advice nhận kết quả, throws advice không kích hoạt.
4. Khi `approve("O-8")` được gọi, before advice chạy; target ném `IllegalStateException`. Không có trả về bình thường, nên after-returning advice không chạy. Throws advice có handler kiểu `IllegalStateException` và ghi nhận lỗi.
5. Ngoại lệ gốc tiếp tục tới `catch` bên caller. Throws advice dùng để quan sát/xử lý chính sách lỗi, không âm thầm chuyển lỗi thành thành công.
6. Nếu gọi `new OrderServiceImpl().submit("O-7")` trực tiếp, đối tượng mới đó không phải proxy vừa cấu hình nên không có các dòng `BEFORE`/`RETURN`.

**Lưu ý:** ví dụ đã chọn thứ tự ba advisor để dễ theo dõi. Trong ứng dụng thực, khi có nhiều interceptor và advice của chúng có thể ném ngoại lệ, phải kiểm tra thứ tự advisor vì chuỗi interceptor lồng nhau; không suy ra tất cả advice đều nhận cùng một sự kiện theo một thứ tự cố định trong mọi cấu hình.

## 6. `Advice` khác `Advisor` như thế nào?

- **`Advice`** mô tả *điều gì xảy ra* tại thời điểm được kích hoạt. `MethodBeforeAdvice`, `AfterReturningAdvice` và `ThrowsAdvice` là các biến thể advice.
- **`Pointcut`** mô tả *lớp/phương thức nào khớp*.
- **`Advisor`** là thành phần Spring kết hợp chính sách áp dụng với advice. `DefaultPointcutAdvisor` thường là lựa chọn khi cần gắn một advice với một pointcut cụ thể.

Ví dụ thay `Pointcut.TRUE` bằng pointcut chỉ nhận method `submit`:

```java
import org.springframework.aop.support.NameMatchMethodPointcut;

NameMatchMethodPointcut submitOnly = new NameMatchMethodPointcut();
submitOnly.setMappedName("submit");
factory.addAdvisor(new DefaultPointcutAdvisor(submitOnly, before));
```

Đây là **đoạn thay thế** cho lời gọi add advisor của `before` trong ví dụ đầy đủ, không phải yêu cầu thêm trùng advice. Khi đó before advice chỉ chạy cho `submit`; các advisor khác vẫn giữ phạm vi theo cấu hình riêng của chúng.

Spring còn có `DefaultAdvisorAutoProxyCreator` để áp dụng những advisor được khai báo trong application context lên các bean phù hợp. Khi dùng cơ chế này, phải chủ động kiểm soát danh sách bean/proxy và pointcut; khai báo advisor có `Pointcut.TRUE` ở cấp container có thể khiến phạm vi rộng hơn nhiều so với ví dụ `ProxyFactory` bao đúng một target.

## 7. Phân biệt với kiểu `@Aspect` của Spring AOP

Spring hỗ trợ cách khai báo cùng mục tiêu bằng các annotation như `@Before`, `@AfterReturning`, `@AfterThrowing` thông qua cấu hình @AspectJ-style. Tuy nhiên:

| Cách viết | API chính | Khai báo điều kiện khớp | Cơ chế |
| --- | --- | --- | --- |
| Programmatic Spring AOP như bài này | `MethodBeforeAdvice`, `AfterReturningAdvice`, `ThrowsAdvice` | `Pointcut` và `DefaultPointcutAdvisor` | `ProxyFactory` hoặc auto-proxy creator |
| Spring @AspectJ-style | `@Before`, `@AfterReturning`, `@AfterThrowing` | Biểu thức pointcut trong annotation | Spring tạo các advisor/interceptor thông qua hỗ trợ @AspectJ |

Hai cách có thể đạt hành vi tương tự trên phương thức được proxy, nhưng **chữ ký và cách đăng ký không thể dùng thay thế tùy ý**: marker interface `ThrowsAdvice` cần các phương thức `afterThrowing(...)` mà adapter Spring tìm qua reflection; không thể gắn annotation `@AfterThrowing` rồi kỳ vọng nó tự tương đương với một instance `ThrowsAdvice` chưa được khai báo vào advisor.

Đặc biệt, `AfterReturningAdvice` là callback chỉ cho đường kết thúc thành công; nó khác `@After` (finally semantics) trong @AspectJ-style vì `@After` có thể chạy dù phương thức kết thúc bằng ngoại lệ. Nếu cần logic giống `finally` khi dùng programmatic API, hãy cân nhắc `MethodInterceptor` bao `invocation.proceed()` bằng `try/finally`.

## 8. Giới hạn của Spring AOP và các lỗi thường gặp

### Proxy quyết định khả năng can thiệp

Spring AOP dựa trên proxy, nên lời gọi phải **đi qua proxy**. Với JDK dynamic proxy, cần lưu ý các method qua interface được proxy; với proxy dựa trên subclass (CGLIB), các method `final`/`private` không thể được override để advise theo cách đó. Constructor không được advise như method execution. Lời gọi `this.otherMethod()` bên trong cùng target thường đi vòng qua proxy (**self-invocation**). Ba loại advice này không loại bỏ các giới hạn đó.

### Callback thấy sự kiện tại ranh giới lời gọi

Đối với phương thức trả `CompletableFuture` hoặc reactive publisher, việc **trả về đối tượng** một cách bình thường không đồng nghĩa công việc bất đồng bộ sẽ hoàn thành thành công. `AfterReturningAdvice` có thể chạy khi phương thức vừa trả về future/publisher; `ThrowsAdvice` chỉ thấy ngoại lệ ném trong đường đi lời gọi được proxy bao quanh, không mặc nhiên thấy lỗi phát sinh sau này khi future/publisher hoàn tất. Cần cơ chế observability/error-handling dành cho phần asynchronous/reactive tương ứng.

### Chú ý dữ liệu và hiệu năng

Đối số `args` và `returnValue` có thể chứa email, token, thông tin tài chính hoặc dữ liệu nghiệp vụ. Tránh in nguyên đối tượng hay stack trace tùy tiện. Advice có thể được dùng chung cho nhiều lời gọi/luồng; không giữ trạng thái request trong field mutable không đồng bộ của một advice singleton.

### Không nhầm trạng thái nghiệp vụ với trạng thái callback

- **Sai:** `afterReturning` luôn chạy sau khi target được gọi. **Đúng:** callback chạy ở đường trả về bình thường trong vị trí của advisor; nếu lỗi không được xử lý thành thành công thì nó không chạy.
- **Sai:** `ThrowsAdvice` cần `@Override` trên `afterThrowing`. **Đúng:** đây là marker interface; handler được nhận diện theo tên và chữ ký.
- **Sai:** `afterThrowing` viết code log xong sẽ hết ngoại lệ. **Đúng:** nếu handler không ném lỗi mới, lỗi ban đầu vẫn được ném lên trên.
- **Sai:** `MethodBeforeAdvice` được phép gọi `proceed()`. **Đúng:** giao diện này không có `proceed()`; dùng `MethodInterceptor` nếu cần điều khiển luồng.
- **Sai:** advice được tạo bằng `new` sẽ tự động áp lên mọi bean Spring. **Đúng:** phải gắn nó qua advisor/proxy hoặc cơ chế đăng ký Spring phù hợp.
- **Sai:** mọi callback chắc chắn chạy với thứ tự như trong sơ đồ. **Đúng:** thứ tự advisor và việc advice khác ném lỗi có thể đổi đường quan sát; phải kiểm tra chain thực tế.

## 9. Lựa chọn trong ứng dụng Spring

| Nhu cầu | Ưu tiên |
| --- | --- |
| Kiểm tra hoặc log trước lời gọi, dừng nếu tiền điều kiện sai | `MethodBeforeAdvice` và pointcut chọn lọc |
| Đếm số operation hoàn tất thành công, audit kết quả | `AfterReturningAdvice` |
| Audit lỗi theo kiểu exception, bổ sung ngữ cảnh method | `ThrowsAdvice` với `afterThrowing(...)` đúng signature |
| Luôn cleanup cả thành công lẫn thất bại | `MethodInterceptor` với `try/finally` |
| Thay đổi kết quả trả về, xử lý recovery hoặc retry theo chính sách | `MethodInterceptor`/thiết kế service chuyên trách; các advice trong bài không phải công cụ tự động thay kết quả |
| Áp một chính sách chỉ lên một vài service/method | Pointcut/bean selection hẹp; kiểm tra bean nào thực sự được proxy |

Khi ứng dụng có transaction, security, caching hoặc retry advisor, hãy kiểm tra thứ tự can thiệp. Ví dụ, “method trả về bình thường” ở một tầng interceptor chưa chắc trùng với “giao dịch đã commit thành công”; audit về transaction outcome cần đặt tại vị trí đáp ứng chính sách giao dịch, thay vì suy luận từ tên `AfterReturningAdvice`.

## 10. Checklist tự kiểm tra

1. Callback `MethodBeforeAdvice` nhận ba tham số gì? Nó phải gọi `proceed()` không?
2. Vì sao `AfterReturningAdvice` không phù hợp để thay thế giá trị trả về của service?
3. Vì sao class implements `ThrowsAdvice` có thể biên dịch dù interface không khai báo callback?
4. Hai signature `afterThrowing` hợp lệ có bao nhiêu tham số, kiểu cuối cùng biểu diễn điều gì?
5. Trong ví dụ `submit` và `approve`, vì sao chỉ có một nhánh chạy `AfterReturningAdvice`?
6. Nếu code bên trong service gọi `this.submit(...)`, vì sao lời gọi nội bộ có thể không được advise?
7. Nếu một service trả về `CompletableFuture` bình thường rồi future hoàn tất với exception, `ThrowsAdvice` đồng bộ có tự nhận được lỗi đó không?
8. Muốn advice chỉ chạy ở method `approve` trên đúng một bean, cần giới hạn những tầng nào?

## Tài liệu Spring chính thức

- [Advice API in Spring (Spring Framework 6.2)](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html): before, after-returning, throws và around advice cùng chữ ký.
- [Pointcut API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pointcuts.html): `ClassFilter`, `MethodMatcher` và phạm vi áp dụng.
- [Programmatic ProxyFactory](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html): tạo proxy bằng Java.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK proxy, CGLIB và self-invocation.
- [ThrowsAdvice Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/ThrowsAdvice.html): marker interface, handler reflection và kiểu ngoại lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-advisor-api-004">Advisor kết hợp pointcut với advice</a>

<details>
<summary>Xem chi tiết</summary>

# Advisor kết hợp Pointcut với Advice trong Spring AOP

## 1. Mục tiêu

Trong **Spring AOP**, `Pointcut` quyết định **phương thức nào có thể được áp dụng một hành vi**, `Advice` định nghĩa **hành vi đó thực hiện thế nào**, còn `Advisor` là đối tượng mang thông tin kết nối để Spring có thể đưa hành vi vào chuỗi xử lý của proxy. Cụ thể, `PointcutAdvisor` cung cấp cả một `Pointcut` và một `Advice`.

Bài học tập trung hoàn toàn vào **API Spring Framework AOP**: `org.springframework.aop.Advisor`, `PointcutAdvisor`, `org.springframework.aop.support.DefaultPointcutAdvisor`, `org.aopalliance.intercept.MethodInterceptor` và `org.springframework.aop.framework.ProxyFactory`. Các thuật ngữ AOP tổng quát chỉ xuất hiện khi cần hiểu cách dùng API trong Spring.

Sau bài này, bạn có thể:

- Nêu sự khác nhau giữa `Pointcut`, `Advice`, `Advisor` và Spring AOP proxy.
- Khởi tạo `DefaultPointcutAdvisor(pointcut, advice)`, thêm vào `ProxyFactory` rồi gọi phương thức qua proxy.
- Kiểm thử rằng advice chỉ chạy trên những method được pointcut chọn.
- Giải thích vì sao `addAdvice(...)` có phạm vi khác `addAdvisor(...)`.
- Kết hợp nhiều advisor, xác định thứ tự chuỗi interceptor, và chẩn đoán lỗi khi advice không chạy.

## 2. Mô hình thành phần

| Thành phần | Vai trò trong Spring AOP | API điển hình |
| --- | --- | --- |
| `Pointcut` | Lọc lớp đích và phương thức ứng viên | `getClassFilter()`, `getMethodMatcher()` |
| `Advice` | Mô tả xử lý gắn vào lời gọi | `MethodInterceptor.invoke(...)`; `MethodBeforeAdvice.before(...)` |
| `Advisor` | Cung cấp một đối tượng advice | `getAdvice()` |
| `PointcutAdvisor` | Là advisor đồng thời cung cấp pointcut | `getPointcut()`, `getAdvice()` |
| `DefaultPointcutAdvisor` | Hiện thực phổ biến của `PointcutAdvisor` | `new DefaultPointcutAdvisor(pointcut, advice)` |
| `ProxyFactory` | Tạo proxy và gắn advisor vào proxy | `addAdvisor(...)`, `getProxy()` |

```text
 NameMatchMethodPointcut ---------┐
  "checkout"                       ├── DefaultPointcutAdvisor ── ProxyFactory
 MethodInterceptor ---------------┘             (pointcut + advice)       │
  log trước/sau                                                       getProxy()
                                                                          │
Client ─────── gọi phương thức qua Spring AOP proxy ───────────────────────┘
                           │
             pointcut chọn phương thức?
                ┌──────────┴─────────┐
              Có                    Không
                │                     │
          chạy Advice chain      gọi Target method
                │
          gọi Target method
```

Sơ đồ minh họa hành vi khi **client gọi qua proxy**, không phải khi client gọi trực tiếp đối tượng đích. `DefaultPointcutAdvisor` là dữ liệu cấu hình; nó **không tự tạo proxy**, không tự bắt lời gọi Java, và không tự chạy Advice.

### 2.1. API Advisor và PointcutAdvisor

Nhìn ở mức chữ ký chính yếu:

```java
// Chỉ là phác họa các phương thức cần hiểu, không phải source Spring đầy đủ.
interface Advisor {
    org.aopalliance.aop.Advice getAdvice();
}

interface PointcutAdvisor extends Advisor {
    Pointcut getPointcut();
}
```

Giao diện `Advisor` chỉ đảm bảo cung cấp advice; chỉ `PointcutAdvisor` mới cam kết `getPointcut()`. Khi cần kiểm tra điểm cắt, truy cập Advisor qua kiểu `PointcutAdvisor` (hoặc một class thực thi nó).

`DefaultPointcutAdvisor` tương thích với các advice thông thường như `MethodInterceptor`, `MethodBeforeAdvice`, `AfterReturningAdvice`, `ThrowsAdvice`. Ngoại lệ đáng chú ý là **introduction** — cơ chế bổ sung interface lên proxy — sử dụng `IntroductionAdvisor` và các cấu phần riêng; không áp dụng cách ghép `DefaultPointcutAdvisor` một cách máy móc.

### 2.2. Ba constructor/cấu hình thông dụng

```java
// Đã có các biến Pointcut pointcut, Advice advice.

// Một pointcut xác định rõ phương thức được chọn:
DefaultPointcutAdvisor selected = new DefaultPointcutAdvisor(pointcut, advice);

// Chỉ truyền advice: dùng Pointcut.TRUE, tức không lọc phương thức.
DefaultPointcutAdvisor allMethods = new DefaultPointcutAdvisor(advice);

// Constructor rỗng: cấu hình đầy đủ trước khi dùng.
DefaultPointcutAdvisor configured = new DefaultPointcutAdvisor();
configured.setPointcut(pointcut);
configured.setAdvice(advice);
```

Đây là **đoạn minh họa API**, không phải tệp chạy độc lập vì `pointcut` và `advice` chưa khai báo. Cần nhớ: khi constructor chỉ nhận advice, pointcut mặc định là `Pointcut.TRUE`. Điều này có thể khiến advice chạy trên nhiều phương thức hơn dự kiến.

Một `Advisor` ghép **một Advice** với **một Pointcut**; để áp dụng nhiều hành vi có điều kiện riêng, tạo nhiều advisor. Bạn không cần viết lại Advice khi thay phạm vi matching: thay hoặc tái sử dụng pointcut theo nhu cầu.

## 3. Ví dụ Java 17+: Advisor chỉ áp dụng cho `checkout`

Bài toán: đối tượng `OrderService` có hai phương thức `checkout(String)` và `cancel(String)`. Ta muốn in log trước và sau lời gọi `checkout`, nhưng **không** in log advice khi gọi `cancel`. Ở đây, `NameMatchMethodPointcut` chọn theo tên method, `MethodInterceptor` ghi log, còn `DefaultPointcutAdvisor` kết nối hai đối tượng.

### 3.1. Khai báo dependency

Với dự án Spring Boot 3.x, Spring Framework 6.x, Java 17+ và Gradle đã cấu hình sẵn, bổ sung:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
```

Đây là **mảnh cấu hình** cần đặt vào `build.gradle` đang dùng Spring Boot/BOM; không phải một `build.gradle` độc lập. Spring AOP thực thi được bằng `ProxyFactory` mà không cần khởi động Spring Boot ApplicationContext.

### 3.2. Tệp chương trình đầy đủ

Tạo `src/main/java/demo/aop/AdvisorDemo.java`:

```java
package demo.aop;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class AdvisorDemo {

    public interface OrderOperations {
        String checkout(String orderId);
        String cancel(String orderId);
    }

    public static class OrderService implements OrderOperations {
        @Override
        public String checkout(String orderId) {
            System.out.println("target:checkout " + orderId);
            return "PAID:" + orderId;
        }

        @Override
        public String cancel(String orderId) {
            System.out.println("target:cancel " + orderId);
            return "CANCELLED:" + orderId;
        }
    }

    public static void main(String[] args) {
        // Pointcut: lựa chọn method checkout.
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("checkout");

        // Advice: hành vi bao quanh method khi pointcut khớp.
        MethodInterceptor advice = invocation -> {
            String method = invocation.getMethod().getName();
            System.out.println("advice:before " + method);
            Object result = invocation.proceed();
            System.out.println("advice:after " + method);
            return result;
        };

        // Advisor kết hợp điều kiện lựa chọn với hành vi.
        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(pointcut, advice);

        // Tạo Spring AOP proxy, thêm advisor có điều kiện.
        ProxyFactory factory = new ProxyFactory(new OrderService());
        factory.addAdvisor(advisor);
        OrderOperations proxy = (OrderOperations) factory.getProxy();

        System.out.println("result=" + proxy.checkout("A17"));
        System.out.println("result=" + proxy.cancel("A18"));
    }
}
```

Để chạy bằng Gradle, thêm task này vào `build.gradle` của dự án:

```groovy
tasks.register('runAdvisorDemo', JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'demo.aop.AdvisorDemo'
}
```

Chạy từ thư mục chứa Gradle wrapper:

```bash
./gradlew runAdvisorDemo
```

Trên Windows PowerShell dùng `.\gradlew.bat runAdvisorDemo`.

**Kết quả stdout do chương trình in:**

```text
advice:before checkout
target:checkout A17
advice:after checkout
result=PAID:A17
target:cancel A18
result=CANCELLED:A18
```

Diễn giải từng bước:

1. `pointcut.setMappedName("checkout")` khớp tên `checkout`; tên `cancel` không khớp. Ở đây bộ lọc lớp mặc định không giới hạn class cụ thể.
2. `MethodInterceptor` là around advice: chạy trước, gọi `invocation.proceed()` để tiếp tục chuỗi/đi tới target, chạy sau, rồi trả nguyên kết quả. Khi `proceed()` ném ngoại lệ, dòng log sau không chạy nếu không dùng `try/finally`.
3. `DefaultPointcutAdvisor(pointcut, advice)` buộc một hành vi vào một điều kiện.
4. `factory.addAdvisor(advisor)` đưa cấu hình vào Spring AOP proxy; `getProxy()` trả đối tượng cần dùng để các lời gọi được intercept. Vì `OrderService` triển khai `OrderOperations`, proxy có thể được gọi qua interface này.
5. `checkout` đi qua advice, `cancel` được thực thi trên target và giữ nguyên kết quả mà không qua advice đó.

Điểm cần chú ý: `NameMatchMethodPointcut` chọn theo **tên**. Nếu lớp có overload `checkout(String)` và `checkout(long)`, cả hai có thể khớp. Nếu muốn giới hạn chữ ký, sử dụng `StaticMethodMatcherPointcut` hoặc `AspectJExpressionPointcut` với biểu thức có điều kiện phù hợp. `Advice` không bắt buộc phải thay đổi.

## 4. Kiểm thử độc lập API và hành vi proxy bằng JUnit 5

Một ví dụ chạy được chưa đủ để chống cấu hình pointcut quá rộng. Viết ca dương tính (method được chọn), ca âm tính (method không được chọn) và khẳng định cả danh sách sự kiện.

Tạo `src/test/java/demo/aop/AdvisorCompositionTest.java`:

```java
package demo.aop;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

import static org.junit.jupiter.api.Assertions.*;

public class AdvisorCompositionTest {

    public interface OrderOperations {
        String checkout(String orderId);
        String cancel(String orderId);
    }

    public static class OrderService implements OrderOperations {
        private final List<String> events;

        public OrderService(List<String> events) {
            this.events = events;
        }

        @Override
        public String checkout(String orderId) {
            events.add("target:checkout");
            return "PAID:" + orderId;
        }

        @Override
        public String cancel(String orderId) {
            events.add("target:cancel");
            return "CANCELLED:" + orderId;
        }
    }

    @Test
    void advisorContainsExpectedPointcutAndAdvice() throws Exception {
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("checkout");
        MethodInterceptor advice = invocation -> invocation.proceed();

        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(pointcut, advice);

        assertSame(pointcut, advisor.getPointcut());
        assertSame(advice, advisor.getAdvice());

        Pointcut actualPointcut = advisor.getPointcut();
        Method checkout = OrderOperations.class.getMethod(
                "checkout", String.class);
        Method cancel = OrderOperations.class.getMethod(
                "cancel", String.class);

        assertTrue(actualPointcut.getClassFilter().matches(OrderService.class));
        assertTrue(actualPointcut.getMethodMatcher().matches(
                checkout, OrderService.class));
        assertFalse(actualPointcut.getMethodMatcher().matches(
                cancel, OrderService.class));
        assertFalse(actualPointcut.getMethodMatcher().isRuntime());
    }

    @Test
    void advisorRunsOnlyOnMatchingMethodThroughProxy() {
        List<String> events = new ArrayList<>();
        OrderService target = new OrderService(events);

        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("checkout");

        MethodInterceptor advice = invocation -> {
            String name = invocation.getMethod().getName();
            events.add("before:" + name);
            Object result = invocation.proceed();
            events.add("after:" + name);
            return result;
        };

        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(pointcut, advice);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvisor(advisor);

        OrderOperations proxy = (OrderOperations) factory.getProxy();
        assertEquals("PAID:7", proxy.checkout("7"));
        assertEquals("CANCELLED:8", proxy.cancel("8"));

        assertEquals(List.of(
                "before:checkout",
                "target:checkout",
                "after:checkout",
                "target:cancel"), events);
    }

    @Test
    void directTargetCallSkipsProxyAdvice() {
        List<String> events = new ArrayList<>();
        OrderService target = new OrderService(events);
        assertEquals("PAID:9", target.checkout("9"));
        assertEquals(List.of("target:checkout"), events);
    }
}
```

Chạy:

```bash
./gradlew test --tests 'demo.aop.AdvisorCompositionTest'
```

Với Windows PowerShell:

```powershell
.\gradlew.bat test --tests 'demo.aop.AdvisorCompositionTest'
```

Nếu cả ba assertion test vượt qua, Gradle hiển thị `BUILD SUCCESSFUL`. Ví dụ test không tự in log ra stdout: dùng `List<String> events` để so chính xác thứ tự sự kiện. Test đầu tiên xác minh **Advisor giữ đúng các thành phần và Pointcut chọn đúng method**; test thứ hai xác minh **Advice chỉ chạy khi gọi qua proxy vào method khớp**; test thứ ba cho thấy gọi trực tiếp target không tự kích hoạt advisor.

Từ các kết quả này mới suy ra cấu hình advisor của **fixture kiểm thử** hoạt động đúng. Chưa thể suy ra rằng Spring Boot đã tự tạo proxy cho một bean cụ thể trong ứng dụng đang chạy; vấn đề đó cần kiểm thử tích hợp ApplicationContext riêng.

### 4.1. Tại sao phải assert negative case?

Giả sử vô ý thay dòng:

```java
new DefaultPointcutAdvisor(pointcut, advice)
```

thành:

```java
new DefaultPointcutAdvisor(advice)
```

Constructor chỉ có advice sử dụng `Pointcut.TRUE`. Khi đó `cancel()` cũng có thể chạy Advice, khiến dãy events test thứ hai xuất hiện thêm `before:cancel` và `after:cancel`. Việc kiểm tra cả phương thức **không được chọn** giúp phát hiện cấu hình quá rộng.

### 4.2. Tách kiểm thử thành ba tầng

| Tầng | Thao tác | Lỗi phát hiện |
| --- | --- | --- |
| Pointcut | `getClassFilter().matches(clazz)`, `getMethodMatcher().matches(method, clazz)` | Chọn sai lớp/tên/chữ ký method |
| Advisor | `getPointcut()` và `getAdvice()` | Ghép nhầm pointcut hoặc advice instance |
| Proxy | Gọi các method qua `factory.getProxy()` và quan sát events/return values | Advisor chưa gắn, advice không thực thi, chain sai |

Với `MethodMatcher.isRuntime() == true`, việc áp dụng advice còn phụ thuộc `matches(Method, Class<?>, Object... args)` cho từng lời gọi. Kiểm thử riêng các biên đối số trước, rồi kiểm thử qua proxy khi tích hợp. Đừng nhầm “pointcut match tĩnh” với “advice chắc chắn chạy”: lời gọi vẫn phải đi qua Spring AOP proxy và thỏa các điều kiện động nếu có.

## 5. `addAdvisor` và `addAdvice` khác nhau ở đâu?

```java
ProxyFactory factory = new ProxyFactory(target);

// Kết hợp pointcut chọn lọc + advice.
factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, loggingAdvice));

// Advice gắn trực tiếp được Spring bọc trong advisor
// có pointcut tương đương Pointcut.TRUE.
factory.addAdvice(metricsAdvice);
```

Đây là **đoạn code minh họa**, cần khai báo `target`, `pointcut`, `loggingAdvice` và `metricsAdvice` trước khi chạy.

| Cấu hình | Phạm vi |
| --- | --- |
| `addAdvisor(new DefaultPointcutAdvisor(pointcut, advice))` | Chỉ các lời gọi khớp pointcut |
| `addAdvisor(new DefaultPointcutAdvisor(advice))` | Pointcut mặc định `Pointcut.TRUE` |
| `addAdvice(advice)` | Spring bao advice trong advisor match mọi phương thức có thể được proxy chặn |

“Match tất cả” ở đây chỉ nói về các lời gọi phương thức **nằm trong phạm vi proxy Spring AOP**, không phải mọi lời gọi Java trong JVM. Nếu chỉ muốn chặn `checkout`, hãy đưa pointcut lựa chọn của bạn vào `DefaultPointcutAdvisor`.

Spring cũng cung cấp API `Advised.getAdvisors()` để quan sát cấu hình của proxy, `addAdvisor(int, Advisor)` để chèn advisor tại vị trí cụ thể và `removeAdvisor(...)` để tháo advisor khi được phép. Các thao tác này có thể hữu ích trong kiểm thử cấu hình và chẩn đoán, nhưng sửa proxy đang chạy trong production cần xem xét về đồng bộ và tác động hành vi.

## 6. Nhiều Advisor trên cùng proxy

Một lời gọi `checkout` có thể đồng thời khớp advisor ghi log, đo thời gian và kiểm tra quyền. Mỗi advisor chọn phương thức riêng và cung cấp một advice riêng; Spring thiết lập chuỗi interceptor thích hợp.

```java
DefaultPointcutAdvisor loggingAdvisor =
        new DefaultPointcutAdvisor(checkoutPointcut, loggingInterceptor);
DefaultPointcutAdvisor timingAdvisor =
        new DefaultPointcutAdvisor(checkoutPointcut, timingInterceptor);

ProxyFactory factory = new ProxyFactory(orderService);
factory.addAdvisor(loggingAdvisor);
factory.addAdvisor(timingAdvisor);
Object proxy = factory.getProxy();
```

Đoạn trên là **mảnh code minh họa**, với `checkoutPointcut` và các biến khác đã được khai báo. Khi hai advice đều là around interceptor và đều gọi `proceed()`, thứ tự lồng nhau thường được hiểu như:

```text
logging: before
  timing: before
    target.checkout()
  timing: after
logging: after
```

Interceptor đứng trước trong chain có thể bao lấy interceptor đứng sau. Với `ProxyFactory` cấu hình thủ công, kiểm soát thứ tự bằng vị trí `addAdvisor` hoặc chèn `addAdvisor(index, advisor)`. Trong auto-proxy của Spring container, một số cơ chế lựa chọn và sắp xếp advisor có hỗ trợ `Ordered` hoặc `@Order`; không nên nghĩ rằng gọi `setOrder(...)` trên đối tượng đã được thêm vào một `ProxyFactory` sẽ tự sắp xếp lại danh sách.

Chú ý hai tình huống:

- **Một advice không gọi `proceed()`:** các interceptor tiếp theo và target có thể không chạy. Cần test khi cố ý cắt luồng (ví dụ chặn truy cập) thay vì coi đó là hiệu ứng phụ.
- **`proceed()` ném exception:** đoạn sau lời gọi `proceed()` trong interceptor thường bị bỏ qua. Muốn dọn tài nguyên/đo thời gian ngay cả khi lỗi, dùng `try/finally`. Muốn đổi exception, phải thực hiện chủ ý và có test.

Các advice singleton có thể được dùng bởi nhiều request/thread. Tránh giữ trạng thái request hiện tại trong biến instance có thể sửa đổi của interceptor; dùng biến cục bộ trong `invoke` hoặc cơ chế ngữ cảnh phù hợp.

## 7. Trong Spring Boot và `@Aspect`

`ProxyFactory` rất hữu ích khi kiểm thử API và khi cần tạo proxy bằng mã. Trong ứng dụng Spring thông thường, Spring container quản lý bean, auto-proxy infrastructure chọn advisor phù hợp rồi tạo proxy. `@Aspect` với `@Before`, `@Around` hoặc các advice annotations giúp biểu diễn quy tắc lựa chọn/hành vi dạng khai báo; Spring có thể chuyển chúng thành các advisor và interceptor nội bộ.

Ví dụ **mảnh code Spring application**, không phải tệp độc lập:

```java
@Aspect
@Component
public class CheckoutAuditAspect {
    @Before("execution(* com.example.orders.OrderService.checkout(..))")
    public void beforeCheckout() {
        System.out.println("auditing checkout");
    }
}
```

Muốn dùng được cần import `org.aspectj.lang.annotation.Aspect`, `org.aspectj.lang.annotation.Before`, `org.springframework.stereotype.Component`, class `OrderService` thực tế đúng package, dependency AspectJ/Spring AOP và auto-proxy được bật. Spring Boot thường tự cấu hình AOP khi có starter phù hợp; Spring Framework thuần có thể cần `@EnableAspectJAutoProxy` hoặc cấu hình tương đương.

**Mối liên hệ:** cả cấu hình thủ công và khai báo đều dựa vào advisor/advice được proxy xử lý. Không phải viết `new DefaultPointcutAdvisor(...)` là Spring tự áp dụng advisor đó lên mọi bean. Nếu muốn tích hợp bằng API thủ công với container, bạn phải đăng ký/cấu hình nó trong cơ chế tạo proxy tương ứng, rồi kiểm thử xem bean thực sự được advice hay không.

## 8. Các lỗi phổ biến và cách chẩn đoán

### 8.1. Sai phạm vi Pointcut

- Dùng `DefaultPointcutAdvisor(advice)` hoặc `addAdvice(advice)` dù chỉ muốn chặn một method: pointcut mặc định quá rộng.
- Dùng `NameMatchMethodPointcut` với tên `checkout` nhưng không kiểm tra các overload cùng tên: chọn ngoài ý định.
- Chỉ kiểm tra `MethodMatcher` mà bỏ `ClassFilter`: có thể kết luận sai rằng method thuộc lớp ngoài phạm vi vẫn được chọn.
- Dùng `AspectJExpressionPointcut` với expression sai package, class hoặc signature: pointcut có thể luôn trả false; cần kiểm tra độc lập biểu thức bằng reflection và class đích đúng.

### 8.2. Advisor đúng nhưng advice không chạy

- Gọi trực tiếp target bằng `new` thay vì gọi qua proxy từ `ProxyFactory` hoặc Spring context.
- Trong một method của target gọi `this.checkout(...)`: self-invocation có thể đi vòng qua proxy.
- Đã khởi tạo `DefaultPointcutAdvisor` nhưng quên `factory.addAdvisor(advisor)` hoặc quên cấu hình advisor làm bean để cơ chế auto-proxy phát hiện.
- Dựa vào các phương thức private, final hoặc tình huống proxy không hỗ trợ: Spring AOP dựa trên method call qua proxy, JDK dynamic proxy và CGLIB có những giới hạn riêng. Với lớp có interface, ưu tiên lập trình thông qua interface để kiểm thử rõ đường đi.
- Pointcut chọn đúng tĩnh nhưng matcher động loại lời gọi vì đối số hiện tại.

### 8.3. Advice chạy nhưng thay đổi hành vi ngoài dự tính

- Interceptor không gọi `invocation.proceed()` khi cần tiếp tục đến target.
- Interceptor cố ý hoặc vô ý thay đổi giá trị trả về của target.
- Advice ném ngoại lệ, hoặc xử lý exception sai, làm lời gọi thất bại.
- Nhiều advisor tạo ra thứ tự trước/sau khác mong đợi. Viết test events thay vì đoán thứ tự.
- Advice có state dùng chung không an toàn trong môi trường đa luồng.

### 8.4. Quy trình debug gọn và có căn cứ

1. **Đọc pointcut:** `ClassFilter.matches(targetClass)` và `MethodMatcher.matches(method, targetClass)` đúng chưa? Có matcher động không?
2. **Đọc advisor:** `getPointcut()` và `getAdvice()` có đúng đối tượng cần dùng không?
3. **Đọc proxy:** advisor đã thêm vào `ProxyFactory` chưa, bean có do Spring quản lý không, client đang gọi proxy hay target?
4. **Quan sát hành vi:** ca match có advice, ca không match không advice, cả hai giữ đúng return values và exception behavior?
5. **Kiểm tra thứ tự:** khi nhiều advisor cùng match, thứ tự interceptor, `proceed()` và `finally` đúng chưa?

## 9. Bài tập thực hành

**Bài 1 — Mở rộng phạm vi có chủ đích.** Thêm `refund(String)` vào interface và implementation. Dùng `pointcut.setMappedNames("checkout", "refund")`, viết test bảo đảm hai phương thức được log còn `cancel` không.

**Bài 2 — Hai Advice một Pointcut.** Tạo logging interceptor và timing interceptor, mỗi cái trong một `DefaultPointcutAdvisor` nhưng dùng chung pointcut. Thêm hai advisor vào `ProxyFactory`. Test thứ tự events rồi đảo thứ tự đăng ký để quan sát chuỗi lồng nhau.

**Bài 3 — Overload.** Thêm `checkout(long)`, nhận xét vì sao `NameMatchMethodPointcut` chọn cả overload. Sau đó thay bằng `StaticMethodMatcherPointcut` xét `method.getParameterTypes()` để chỉ nhận `checkout(String)`.

**Bài 4 — Direct target so với proxy.** So sánh `target.checkout("X")` với `proxy.checkout("X")` bằng counter/list events. Chỉ ra vì sao constructor của Advisor không làm một lời gọi trực tiếp tự động bị intercept.

### Tiêu chí hoàn thành

Bạn chỉ thực sự hiểu việc kết hợp trong Spring AOP khi trả lời được: **Pointcut chọn method nào? Advice được gọi khi nào? Advisor giữ cặp Pointcut/Advice bằng API nào? Proxy nào thực thi chuỗi?** Và có test khẳng định cả trường hợp **nên** lẫn **không nên** chạy advice.

## 10. Tài liệu chính thức

- [The Advisor API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html)
- [The Pointcut API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html)
- [Advice API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html)
- [Creating AOP Proxies Programmatically with ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html)
- [Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html)
- [DefaultPointcutAdvisor Javadoc (Spring 6.2)](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/support/DefaultPointcutAdvisor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-advisor-api-005">DefaultPointcutAdvisor và trường hợp dùng phổ biến</a>

<details>
<summary>Xem chi tiết</summary>

# DefaultPointcutAdvisor trong Spring AOP và các trường hợp sử dụng phổ biến

## 1. DefaultPointcutAdvisor là gì?

`org.springframework.aop.support.DefaultPointcutAdvisor` là triển khai `PointcutAdvisor` phổ biến của **Spring AOP API**. Một đối tượng của lớp này ghép **một `Pointcut`** (quy tắc chọn phương thức/lớp đích) với **một `Advice`** (hành vi bổ sung khi lời gọi phù hợp đi qua Spring AOP proxy).

Ví dụ: muốn ghi nhật ký trước các phương thức `save*`, ứng dụng có thể tạo `NameMatchMethodPointcut` chọn tên `save*`, tạo `MethodBeforeAdvice` ghi log, ghép cả hai bằng `new DefaultPointcutAdvisor(pointcut, advice)`, rồi cung cấp advisor cho hạ tầng proxy.

```text
                    DefaultPointcutAdvisor
                 ┌──────────┴──────────┐
                 │                     │
           Pointcut                  Advice
     "Chọn phương thức nào?"  "Thực hiện hành vi gì?"
                 └──────────┬──────────┘
                            │
                     Spring AOP proxy
                            │
               Lời gọi phù hợp → advice
                            │
                        target method
```

Trong Spring AOP, advisor **chỉ mô tả cặp pointcut–advice**. Advisor không tự tạo proxy và không tự chạy khi được khởi tạo bằng `new`. Phải gắn nó vào `ProxyFactory`, `ProxyFactoryBean` hoặc một cơ chế auto-proxy của Spring. Bài này tập trung vào API trong Spring Framework và cách ứng dụng sử dụng nó; không triển khai lại khái niệm Aspect chung.

## 2. `DefaultPointcutAdvisor` đóng vai trò gì trong mô hình Advisor?

`DefaultPointcutAdvisor` hiện thực `PointcutAdvisor`: nó giữ **một** `Advice` và **một** `Pointcut` để Spring đưa vào chuỗi của một proxy. Trong `Pointcut`, `ClassFilter` chọn loại target, còn `MethodMatcher` chọn method; matcher động còn có thể xét arguments runtime. `ProxyFactory` hoặc auto-proxy infrastructure chịu trách nhiệm tạo proxy và áp dụng cấu hình đó.

Ví dụ muốn tách cách chọn method khỏi logic kiểm tra tiền điều kiện:

```java
NameMatchMethodPointcut saveMethods = new NameMatchMethodPointcut();
saveMethods.setMappedName("save*");
MethodBeforeAdvice validate = (method, args, target) ->
        System.out.println("Checking " + method.getName());
DefaultPointcutAdvisor advisor =
        new DefaultPointcutAdvisor(saveMethods, validate);
```

Đây là fragment cần imports `NameMatchMethodPointcut`, `MethodBeforeAdvice`, `DefaultPointcutAdvisor` và được gắn vào proxy để có tác dụng. Thay `saveMethods` sẽ đổi phạm vi mà không phải sửa logic `validate`. Muốn hai chính sách độc lập, tạo hai advisor.

Điều riêng của lớp này là **constructor và cấu hình pointcut mặc định** ở phần tiếp theo. Nếu cần **bổ sung interface mới trên proxy**, hãy sử dụng `IntroductionAdvisor` và introduction advice phù hợp; `DefaultPointcutAdvisor` chỉ chọn method cho advice thông thường. Bài *Advisor kết hợp pointcut với advice* trình bày kỹ hơn cách các thành phần ghép thành proxy.

## 3. Constructor và những API cần biết

Các constructor chính:

```java
// 1) Khởi tạo rỗng, cấu hình trước khi đưa vào proxy:
DefaultPointcutAdvisor first = new DefaultPointcutAdvisor();
first.setPointcut(pointcut);
first.setAdvice(advice);

// 2) Chỉ truyền advice: mặc định áp dụng cho mọi phương thức đủ điều kiện:
DefaultPointcutAdvisor second = new DefaultPointcutAdvisor(advice);

// 3) Truyền cả pointcut và advice: rõ ràng và nên ưu tiên:
DefaultPointcutAdvisor third =
        new DefaultPointcutAdvisor(pointcut, advice);
```

Ba cách trong khối trên là **các lựa chọn tương đương về kiểu cấu hình**, với giả định `pointcut` và `advice` đã được khai báo. Không cần tạo cả ba advisor trong ứng dụng thật.

| API | Ý nghĩa thực tế |
| --- | --- |
| `DefaultPointcutAdvisor()` | Tạo advisor chưa có advice; bắt buộc đặt `setAdvice(...)` trước khi sử dụng |
| `DefaultPointcutAdvisor(Advice)` | Dùng `Pointcut.TRUE`, do đó advice áp dụng cho mọi phương thức proxy đủ điều kiện |
| `DefaultPointcutAdvisor(Pointcut, Advice)` | Dùng đúng điều kiện chọn phương thức được cung cấp |
| `setPointcut(Pointcut)`, `getPointcut()` | Đặt/đọc pointcut; mặc định là `Pointcut.TRUE` |
| `setAdvice(Advice)`, `getAdvice()` | Đặt/đọc advice; được kế thừa từ `AbstractGenericPointcutAdvisor` |
| `setOrder(int)`, `getOrder()` | Cấu hình thứ tự advisor khi hạ tầng áp dụng sắp xếp theo Spring `Ordered` |

**Hai điểm cần tránh nhầm lẫn:**

1. `Pointcut.TRUE` **không có nghĩa mọi phương thức Java chắc chắn được chặn**: chỉ những lời gọi mà Spring proxy có khả năng intercept mới đi qua advice; còn phụ thuộc loại proxy và quy tắc truy cập.
2. Constructor chỉ nhận `Advice` hoặc `setPointcut(null)` đều khiến pointcut dùng mặc định `Pointcut.TRUE`. Hãy truyền pointcut cụ thể nếu hành vi phải giới hạn vào một nhóm phương thức.

`DefaultPointcutAdvisor` nhận những pointcut và advice thông thường như `NameMatchMethodPointcut` + `MethodBeforeAdvice`, `JdkRegexpMethodPointcut` + `MethodInterceptor`, hay custom `Pointcut` + `ThrowsAdvice`. **Ngoại lệ:** không sử dụng lớp này cho *introduction advisor* (loại làm proxy có thêm interface); Spring có cơ chế advisor riêng cho introductions.

## 4. Các tình huống áp dụng phổ biến

### 4.1. Audit/log có chọn lọc

- `Pointcut`: chọn `save*`, `update*` hoặc các phương thức cần audit.
- `Advice`: `MethodBeforeAdvice` ghi tên thao tác, hoặc `MethodInterceptor` ghi thời gian/trạng thái hoàn thành.
- `DefaultPointcutAdvisor`: liên kết chúng và được gắn vào proxy.

Thích hợp khi dự án đang sử dụng API `Advisor` trực tiếp, hoặc một thư viện cần cung cấp logic audit có thể cắm vào proxy mà không cần định nghĩa một `@Aspect` riêng.

### 4.2. Đo thời gian hoặc thực thi bao quanh lời gọi

- `Pointcut`: giới hạn các phương thức `find*`, `load*`, hoặc một tập hợp method theo annotation.
- `Advice`: `org.aopalliance.intercept.MethodInterceptor` thực hiện logic trước và sau `invocation.proceed()`.
- Lưu ý sử dụng `try/finally` nếu cần đo thời gian cả khi phương thức ném exception.

Không quên gọi `proceed()` nếu mục đích là quan sát. Nếu không gọi, interceptor có thể thay thế hoặc chặn hẳn lời gọi đến target.

### 4.3. Áp dụng chính sách cho một nhóm phương thức

Ví dụ, có thể ghép một `Pointcut` chọn những phương thức nhạy cảm với một interceptor kiểm tra ngữ cảnh người dùng. Đây là *điểm mở rộng kỹ thuật* của Spring AOP. Với security và transaction ở ứng dụng thật, nên ưu tiên infrastructure Spring Security và Spring Transaction vì chúng xử lý nhiều ngữ nghĩa và trường hợp biên hơn một advisor tự viết.

### 4.4. Đóng gói thành phần AOP tái sử dụng

Một thư viện Spring nội bộ có thể xuất `@Bean DefaultPointcutAdvisor` để hệ thống auto-proxy lựa chọn bean/method phù hợp. Pointcut và advice thay đổi độc lập; logic advice có thể được tái sử dụng với các pointcut khác mà không sửa code service.

### 4.5. Thử nghiệm cơ chế Spring AOP bằng ProxyFactory

`ProxyFactory` cho phép dựng proxy trong ứng dụng console hoặc test nhỏ **mà không cần khởi tạo ApplicationContext**. Cách này rất hữu ích khi muốn thử thứ tự advice, quy tắc match và hành vi của interceptor trước khi tích hợp với cấu hình bean.

## 5. Ví dụ Java 17+ hoàn chỉnh: hai advisor trên cùng proxy

Ví dụ sử dụng **Spring AOP API thuần** để minh họa:

- `save*` → chạy `MethodBeforeAdvice` trước phương thức.
- `find*` → chạy `MethodInterceptor` bao quanh phương thức.
- `delete*` → không khớp pointcut nào, đi thẳng vào target.

Tạo dự án:

```text
default-pointcut-advisor-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/AdvisorDemo.java
```

`settings.gradle`:

```groovy
rootProject.name = 'default-pointcut-advisor-demo'
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

dependencies {
    implementation 'org.springframework:spring-aop:6.2.14'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.AdvisorDemo'
}
```

Dùng phiên bản Spring Framework 6.2.x cố định để ví dụ tái lập được. Nếu đưa code vào Spring Boot thì hãy dùng dependency Spring AOP do Spring Boot quản lý thay vì tự ghép các phiên bản Spring khác nhau.

`src/main/java/demo/AdvisorDemo.java`:

```java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class AdvisorDemo {

    public interface OrderService {
        void saveOrder();
        String findOrder(long id);
        void deleteOrder();
    }

    public static final class DefaultOrderService implements OrderService {
        @Override
        public void saveOrder() {
            System.out.println("[target] saveOrder");
        }

        @Override
        public String findOrder(long id) {
            System.out.println("[target] findOrder " + id);
            return "order-" + id;
        }

        @Override
        public void deleteOrder() {
            System.out.println("[target] deleteOrder");
        }
    }

    public static void main(String[] args) {
        NameMatchMethodPointcut savePointcut =
                new NameMatchMethodPointcut();
        savePointcut.setMappedName("save*");

        MethodBeforeAdvice auditAdvice = (method, arguments, target) ->
                System.out.println("[audit] " + method.getName());

        DefaultPointcutAdvisor saveAdvisor =
                new DefaultPointcutAdvisor(savePointcut, auditAdvice);

        NameMatchMethodPointcut findPointcut =
                new NameMatchMethodPointcut();
        findPointcut.setMappedName("find*");

        MethodInterceptor timingAdvice = invocation -> {
            System.out.println("[around-before] "
                    + invocation.getMethod().getName());
            Object result = invocation.proceed();
            System.out.println("[around-after] "
                    + invocation.getMethod().getName());
            return result;
        };

        DefaultPointcutAdvisor findAdvisor =
                new DefaultPointcutAdvisor(findPointcut, timingAdvice);

        ProxyFactory factory = new ProxyFactory(new DefaultOrderService());
        factory.setInterfaces(OrderService.class);
        factory.addAdvisor(saveAdvisor);
        factory.addAdvisor(findAdvisor);

        OrderService proxy = (OrderService) factory.getProxy();

        proxy.saveOrder();
        String result = proxy.findOrder(7L);
        System.out.println("[caller] " + result);
        proxy.deleteOrder();
    }
}
```

Lệnh chạy (cần JDK 17+ và Gradle; trong dự án có Gradle Wrapper thì thay `gradle` bằng `./gradlew` trên Unix hoặc `gradlew.bat` trên Windows):

```bash
gradle run
```

Stdout **do chương trình in ra**, không kể thông báo nhiệm vụ của Gradle:

```text
[audit] saveOrder
[target] saveOrder
[around-before] findOrder
[target] findOrder 7
[around-after] findOrder
[caller] order-7
[target] deleteOrder
```

**Vì sao có output đó?**

1. `ProxyFactory` bọc `DefaultOrderService` bằng proxy triển khai `OrderService`; lời gọi phải thực hiện trên biến `proxy`.
2. `saveOrder()` phù hợp `save*` nên `auditAdvice` chạy trước target.
3. `findOrder(7L)` phù hợp `find*` nên `timingAdvice` in trước, gọi `proceed()` để target thực thi, rồi in sau; giá trị trả về được chuyển về caller.
4. `deleteOrder()` không phù hợp cả hai pointcut nên chỉ dòng từ target xuất hiện.
5. Hai advisor không phải hai proxy riêng; cả hai cùng được thêm vào factory và Spring chọn interceptor chain phù hợp cho từng method.

Ví dụ dùng nhãn `timingAdvice` để minh họa cấu trúc around interceptor nhưng chưa đo nanosecond. Trong ứng dụng thực tế, có thể đo `System.nanoTime()` trước/sau `proceed()`, tính khoảng chênh lệch và log trong `finally` để bao quát cả lỗi.

## 6. Dùng DefaultPointcutAdvisor với Spring bean và auto-proxy

Trong ứng dụng Spring Framework/Boot, kiểu cấu hình thường gặp là khai báo advisor dưới dạng `@Bean` rồi để hạ tầng AOP tạo proxy cho những bean phù hợp.

Ví dụ **trích đoạn cấu hình** (giả định interface `OrderService` và class `DefaultOrderService` đã có trong project, dự án có `spring-context`, Spring AOP và các dependency cho annotation-based auto-proxy; với Spring Boot thường sử dụng `spring-boot-starter-aop`):

```java
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class OrderAopConfig {

    @Bean
    public OrderService orderService() {
        return new DefaultOrderService();
    }

    @Bean
    public DefaultPointcutAdvisor orderAuditAdvisor() {
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedNames("save*", "delete*");
        pointcut.setClassFilter(
                clazz -> DefaultOrderService.class.isAssignableFrom(clazz)
        );

        MethodBeforeAdvice advice = (method, args, target) ->
                System.out.println("[order-audit] " + method.getName());

        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(pointcut, advice);
        advisor.setOrder(100);
        return advisor;
    }
}
```

Đoạn trên không phải file Java độc lập chạy bằng `javac`: cần package/import các kiểu nghiệp vụ và cấu hình IoC của ứng dụng. Hạ tầng auto-proxy phải hoạt động; `@EnableAspectJAutoProxy` kích hoạt annotation-aware auto-proxying trong cấu hình tương ứng, và có thể cần dependency AspectJ weaver. Spring Boot có thể đã tự cấu hình auto-proxy nếu đã thêm starter AOP, nên kiểm tra cấu hình dự án trước khi bật thêm.

Khi `OrderService` được lấy từ `ApplicationContext`, Spring có thể trả về proxy chứa `orderAuditAdvisor` nếu pointcut match với bean/method. Tự tạo `new DefaultOrderService()` bên ngoài container không dùng proxy đó.

**Trường hợp sử dụng khác:** `DefaultAdvisorAutoProxyCreator` là cơ chế Spring có khả năng quét các bean `Advisor` và tạo proxy cho bean phù hợp. Có thể gặp nó trong cấu hình Spring AOP API truyền thống. Không nên khai báo thêm nhiều loại auto-proxy creator tùy tiện nếu ứng dụng đã có infrastructure AOP hiện hữu.

## 7. Nhiều advisor và thứ tự áp dụng

Một bean có thể phù hợp nhiều advisor (ví dụ một advice ghi audit và một advice đo thời gian đều match `saveOrder`). Spring kết hợp chúng thành interceptor chain. Để kiểm soát thứ tự, `DefaultPointcutAdvisor` có `setOrder(int)` nhờ hỗ trợ Spring `Ordered`.

```java
DefaultPointcutAdvisor auditAdvisor =
        new DefaultPointcutAdvisor(auditPointcut, auditAdvice);
auditAdvisor.setOrder(10);

DefaultPointcutAdvisor metricsAdvisor =
        new DefaultPointcutAdvisor(metricsPointcut, metricsAdvice);
metricsAdvisor.setOrder(20);
```

Đây là đoạn cấu hình rút gọn, phụ thuộc bốn biến đã khai báo. Trong đường chạy có **hạ tầng thực hiện sắp xếp advisor theo order**, số nhỏ hơn được ưu tiên cao hơn. Với around interceptor, advisor ở ngoài thường thực hiện phần *trước* trước rồi phần *sau* sau (theo thứ tự ngược lại khi unwind). Cách kiểm tra đúng là chạy thử chain thực tế, đặc biệt khi xen kẽ advice từ `@Aspect`, transaction hoặc các infrastructure advisor khác.

Không lấy thứ tự khai báo `@Bean` hoặc thứ tự quét component làm cam kết chạy advice. Cũng không kỳ vọng `setOrder(...)` tự đổi thứ tự tùy ý trong mọi trường hợp khi tự `factory.addAdvisor(...)`; với API thủ công, thứ tự các advisor được thêm hoặc chỉ số chèn vào factory cũng có ý nghĩa.

Một số quyết định thiết kế:

- Nếu nhiều method phải chia sẻ *cùng một hành vi*, có thể sử dụng một advisor với pointcut rộng vừa đủ.
- Nếu mỗi nhóm method cần hành vi hoặc điều kiện khác, nên tạo những advisor riêng, tránh một advice lớn chứa nhiều câu `if (method.getName()...)`.
- Chỉ dùng `Pointcut.TRUE` khi thực sự muốn áp dụng trên toàn bộ phương thức mà proxy có thể can thiệp.

## 8. Phân biệt DefaultPointcutAdvisor với các cách cấu hình gần giống

| Cách cấu hình | Khi nào phù hợp | Khác biệt cần nhớ |
| --- | --- | --- |
| `new DefaultPointcutAdvisor(pointcut, advice)` | Cần chọn lớp/phương thức bằng pointcut và gắn advice rõ ràng | Advisor có điều kiện |
| `new DefaultPointcutAdvisor(advice)` | Thực sự muốn chọn mọi phương thức hợp lệ của proxy | Mặc định `Pointcut.TRUE` |
| `proxyFactory.addAdvice(advice)` | Thêm advice rộng trong cấu hình proxy thủ công | API tiện lợi; không cần tự tạo pointcut có điều kiện |
| `proxyFactory.addAdvisor(advisor)` | Gắn advisor đã ghép pointcut/advice | Dùng lại advisor hoặc có nhiều điều kiện khác nhau |
| `@Aspect` + `@Around`/`@Before` | Ứng dụng thích phong cách annotation và biểu thức AspectJ | Spring xử lý chuyển annotation thành infrastructure advisor, người dùng thường không tự tạo `DefaultPointcutAdvisor` |
| `DefaultAdvisorAutoProxyCreator` | Cần tự động áp dụng các `Advisor` bean trong cấu hình AOP kiểu API | Là **bộ tạo proxy**, không phải một pointcut hay advice |

Không nhầm `DefaultPointcutAdvisor` với Spring transaction advisor chuyên dụng. Giao dịch `@Transactional` nên được cấu hình qua hệ thống transaction management của Spring thay vì tạo một advice chỉ ghi log và kỳ vọng nó cung cấp semantics giao dịch.

## 9. Những lỗi phổ biến và cách kiểm tra

| Triệu chứng | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| Tạo `DefaultPointcutAdvisor` xong nhưng không có log | Advisor chưa được gắn vào proxy hoặc infrastructure chưa hoạt động | Kiểm tra `factory.addAdvisor(...)` hoặc auto-proxy creator |
| Advice chạy trên nhiều method ngoài mong muốn | Dùng constructor chỉ nhận `Advice`, điểm cắt mặc định `Pointcut.TRUE` | Truyền pointcut giới hạn chính xác |
| `@Bean` advisor tồn tại nhưng service vẫn là object gốc | Chưa có auto-proxy hoặc gọi object ngoài container | Kích hoạt cơ chế proxy phù hợp và lấy service từ context |
| `this.saveOrder()` không chạy advice | Self-invocation đi thẳng trong target, không đi qua proxy Spring AOP | Di chuyển ranh giới lời gọi qua proxy/bean phù hợp |
| Method `private` hoặc `final` không được advice | Giới hạn proxy subclass/JDK proxy | Kiểm tra kiểu proxy, visibility, final và interface |
| Dùng `NameMatchMethodPointcut` mà overload bị chọn cùng nhau | Matcher chọn theo tên method | Dùng pointcut chi tiết hơn nếu cần phân biệt signature |
| Around advice không gọi target | Quên `invocation.proceed()` | Gọi `proceed()` và trả giá trị khi cần giữ hành vi phương thức |
| Nhầm advisor với introduction | `DefaultPointcutAdvisor` không hỗ trợ introduction | Dùng implementation introduction advisor chuyên biệt |

Cách kiểm tra có hệ thống trong Spring: xác nhận bean thực tế có phải proxy bằng `org.springframework.aop.support.AopUtils.isAopProxy(bean)`; nếu proxy triển khai `Advised`, có thể kiểm tra `((Advised) bean).getAdvisors()`. Sau đó đối chiếu `ClassFilter`, `MethodMatcher` với lớp và phương thức mục tiêu. Hãy dùng các thao tác inspect này trong log/test nội bộ, không biến chúng thành giả định rằng mọi bean đều cho phép ép kiểu `Advised`.

Với Spring AOP dùng proxy, các lời gọi từ chính đối tượng đích sang phương thức khác của cùng đối tượng thường vượt qua proxy; điểm này vẫn đúng cho dù advisor đã cấu hình chính xác.

## 10. Kiểm tra hiểu bài

**Câu 1.** Với `new DefaultPointcutAdvisor(auditAdvice)`, những phương thức nào được chọn?

Đáp án: advisor dùng `Pointcut.TRUE` nên match mọi phương thức **đủ điều kiện đi qua proxy**, trong giới hạn của cơ chế proxy Spring AOP. Không có bộ lọc tên riêng.

**Câu 2.** Có thể ghép `NameMatchMethodPointcut` với `MethodInterceptor` mà không viết class advisor mới không?

Có. `new DefaultPointcutAdvisor(namePointcut, aroundInterceptor)` làm được việc đó. Thêm advisor vào `ProxyFactory` hoặc đăng ký nó trong cơ chế auto-proxy phù hợp.

**Câu 3.** Một phương thức match hai advisor thì Spring bỏ một cái?

Không. Khi cả hai advisor được chọn, Spring có thể thực thi chain của cả hai advice theo cách sắp xếp của proxy. Hãy chú ý thứ tự, việc gọi `proceed()` và xử lý exception trong around interceptor.

**Câu 4.** Khai báo `@Bean DefaultPointcutAdvisor` có tự làm phương thức của object tạo bằng `new` được advice không?

Không. Object tự tạo bên ngoài cơ chế proxy sẽ không tự nhận advisor của Spring container. Cần gọi qua đối tượng proxy.

## 11. Tài liệu Spring chính thức

- [DefaultPointcutAdvisor Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/DefaultPointcutAdvisor.html): constructor, `Pointcut.TRUE`, `setPointcut` và những kiểu advice được hỗ trợ.
- [Advisor API trong Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html): cách ghép pointcut và advice, nhiều advisor trên cùng proxy.
- [ProxyFactory programmatic API](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html): tạo proxy thủ công, `addAdvisor` và `addAdvice`.
- [Auto-proxy facility](https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html): `DefaultAdvisorAutoProxyCreator` và cách áp dụng advisor cho bean.
- [Proxying mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): giới hạn của JDK/CGLIB proxy và self-invocation.

**Ghi nhớ:** Trong Spring AOP, `DefaultPointcutAdvisor` là cách ghép **một điều kiện chọn method** với **một hành vi advice** để proxy áp dụng chính xác nơi cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-advisor-api-006">PointcutAdvisor và IntroductionAdvisor</a>

<details>
<summary>Xem chi tiết</summary>

# PointcutAdvisor và IntroductionAdvisor trong Spring AOP

## 1. Mục tiêu

Trong Spring AOP, `Advisor` là thành phần cấu hình gắn **advice** (hành vi được thực thi) với phạm vi áp dụng của nó. Hai hợp đồng cần phân biệt là:

- **`PointcutAdvisor`:** kết hợp `Pointcut` và `Advice` để áp dụng advice cho **các method execution khớp điều kiện** của target.
- **`IntroductionAdvisor`:** công bố **một hay nhiều interface mới trên AOP proxy**, kèm implementation do introduction advice cung cấp. Kiểm tra phạm vi theo **class**, không theo từng method của target.

Bài học tập trung vào **Spring Framework AOP API**: cách dùng interface, lớp triển khai có sẵn và cách lắp vào `ProxyFactory`. Không mở rộng sang AspectJ weaving hay lý thuyết Aspect nói chung.

Sau khi hoàn thành, bạn sẽ biết cách đọc và cấu hình `DefaultPointcutAdvisor`, `DefaultIntroductionAdvisor`, `DelegatingIntroductionInterceptor`; hiểu vì sao introduction không sử dụng `MethodMatcher`; và triển khai một proxy vừa chặn method nghiệp vụ, vừa bổ sung năng lực cho object.

## 2. Hệ phân cấp API và điểm khác biệt

Các interface chính đều thuộc `org.springframework.aop`, ngoại trừ một số lớp hỗ trợ trong `org.springframework.aop.support`:

```text
org.springframework.aop.Advisor
    ├── org.springframework.aop.PointcutAdvisor
    │       └── getPointcut(): Pointcut
    │               ├── getClassFilter(): ClassFilter
    │               └── getMethodMatcher(): MethodMatcher
    │
    └── org.springframework.aop.IntroductionAdvisor
            ├── getClassFilter(): ClassFilter
            ├── getInterfaces(): Class<?>[]   (từ IntroductionInfo)
            └── validateInterfaces(): void

Cả hai: getAdvice(): Advice
```

`Advisor` có `getAdvice()`; `PointcutAdvisor` bổ sung `getPointcut()`. `IntroductionAdvisor` kế thừa `IntroductionInfo` để công bố danh sách interface mới. Các lớp thường dùng:

- `DefaultPointcutAdvisor`: hiện thực phổ biến của `PointcutAdvisor`, nhận một pointcut và advice.
- `DefaultIntroductionAdvisor`: hiện thực `IntroductionAdvisor`, khai báo những interface được giới thiệu và advice đảm nhiệm chúng.
- `DelegatingIntroductionInterceptor`: implementation tiện dụng của `IntroductionInterceptor`, chuyển tiếp lời gọi interface được giới thiệu cho một object delegate.
- `DelegatePerTargetObjectIntroductionInterceptor`: lựa chọn khi cần mỗi **target object** có một delegate riêng, dù dùng chung interceptor.

| Khía cạnh | `PointcutAdvisor` | `IntroductionAdvisor` |
| --- | --- | --- |
| Câu hỏi nó giải quyết | Method nào được advice thực thi? | Proxy có thể thực hiện thêm interface nào? |
| Đối tượng lựa chọn | Class **và method** thông qua `Pointcut` | Class thông qua `ClassFilter` |
| API quyết định | `getPointcut()` → `ClassFilter`, `MethodMatcher` | `getClassFilter()`, `getInterfaces()`, `validateInterfaces()` |
| Có `MethodMatcher`? | Có, nằm trong `Pointcut` | Không; introduction không match method execution theo cách này |
| Advice điển hình | `MethodInterceptor`, `MethodBeforeAdvice`, `AfterReturningAdvice`, v.v. | `IntroductionInterceptor` / `DynamicIntroductionAdvice` tương ứng |
| Kết quả nhìn thấy từ caller | Lời gọi phù hợp được chặn qua proxy | Proxy có thể ép kiểu sang interface mới và gọi method của interface đó |
| Triển khai tiện dụng | `DefaultPointcutAdvisor` | `DefaultIntroductionAdvisor` |

**Điểm cốt lõi:** cả hai đều cấu hình Spring AOP proxy. Khi dùng introduction, **proxy** implement interface mới; **class nguồn của target không tự biến thành class implement interface đó**. Vì thế `target instanceof NewInterface` có thể là `false`, còn `proxy instanceof NewInterface` là `true`.

## 3. Dùng `PointcutAdvisor`: chọn method để áp dụng advice

`PointcutAdvisor` cung cấp hợp đồng:

```java
public interface PointcutAdvisor extends Advisor {
    Pointcut getPointcut();
}
```

Đây là chữ ký API giản lược; `Advisor` cung cấp `getAdvice()` và `isPerInstance()`. Một `Pointcut` bao gồm:

```java
public interface Pointcut {
    ClassFilter getClassFilter();
    MethodMatcher getMethodMatcher();
}
```

Khi lời gọi đi qua proxy, Spring xét điều kiện class/method và xây dựng interceptor chain phù hợp. Với `MethodMatcher` tĩnh, điều kiện có thể được tái sử dụng/cached. Với matcher động, Spring có thể cần kiểm tra thêm arguments tại thời điểm gọi.

Ví dụ **đoạn cấu hình**, giả định đã có `Advice auditAdvice`:

```java
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
pointcut.setMappedName("placeOrder");

PointcutAdvisor advisor =
        new DefaultPointcutAdvisor(pointcut, auditAdvice);

// Xem phạm vi và advice của advisor:
System.out.println(advisor.getPointcut() != null); // true
System.out.println(advisor.getAdvice() == auditAdvice); // true
```

`NameMatchMethodPointcut` dùng tên method, phù hợp khi ví dụ chỉ cần chặn `placeOrder`. Trong dự án Spring, có thể thay bằng `AspectJExpressionPointcut` với `setExpression("execution(* demo..OrderService.placeOrder(..))")` nếu cần biểu thức theo type/signature/annotation. Việc thay pointcut không làm thay đổi ý nghĩa của `DefaultPointcutAdvisor`.

Spring cũng có các `PointcutAdvisor` chuyên biệt, chẳng hạn `NameMatchMethodPointcutAdvisor` hoặc `RegexpMethodPointcutAdvisor`. Trong ứng dụng thực tế, nhiều cơ chế infrastructure của Spring cũng triển khai `PointcutAdvisor` để đưa advice vào các proxy do Spring quản lý.

## 4. Dùng `IntroductionAdvisor`: công bố interface mới

`IntroductionAdvisor` hoạt động ở **cấp class**:

```java
public interface IntroductionAdvisor extends Advisor, IntroductionInfo {
    ClassFilter getClassFilter();
    void validateInterfaces() throws IllegalArgumentException;
}

public interface IntroductionInfo {
    Class<?>[] getInterfaces();
}
```

Đây là hợp đồng API rút gọn, không phải mã cần tự định nghĩa trong dự án. Diễn giải:

1. `getInterfaces()` trả về các interface mà proxy sẽ giới thiệu.
2. `getClassFilter()` cho biết một target class có nằm trong phạm vi áp dụng introduction hay không.
3. `validateInterfaces()` kiểm tra introduction advice có thể hiện thực những interface đã đăng ký hay không; framework sử dụng bước kiểm tra này khi thêm advisor.
4. `getAdvice()` trả về introduction advice đảm nhiệm lời gọi của interface mới.

**Không có `getPointcut()` hoặc `MethodMatcher` trên `IntroductionAdvisor`**: introduction là việc công bố thêm khả năng trên proxy, không phải chọn vài method cũ để chạy advice. Không dùng `DefaultPointcutAdvisor` để thay thế introduction, dù cả hai cùng triển khai `Advisor`.

Spring cung cấp `DefaultIntroductionAdvisor` và `DelegatingIntroductionInterceptor`. Ví dụ khởi tạo, giả định `TrackingMixin implements Trackable`:

```java
DelegatingIntroductionInterceptor interceptor =
        new DelegatingIntroductionInterceptor(new TrackingMixin());

DefaultIntroductionAdvisor advisor =
        new DefaultIntroductionAdvisor(interceptor, Trackable.class);

// Interface được công bố bởi advisor:
Class<?>[] introducedInterfaces = advisor.getInterfaces();
advisor.validateInterfaces();
```

Đây là **mảnh cấu hình**, chạy được trong chương trình đầy đủ ở phần 5. `DelegatingIntroductionInterceptor` chuyển method thuộc `Trackable` đến `TrackingMixin`; với lời gọi target thông thường, interceptor tiếp tục chuỗi AOP. Constructor `DefaultIntroductionAdvisor(DynamicIntroductionAdvice, Class<?>)` tạo advisor cho interface chỉ định.

### Cấu hình nào thay đổi, cấu hình nào không?

- **Được thay đổi ở proxy:** tập interface mà caller nhìn thấy, và cách interface mới xử lý lời gọi.
- **Không được thay đổi trong class target:** khai báo `implements`, method bytecode, fields của lớp gốc.
- **Không tự động làm bean nào cũng có interface mới:** Spring phải thực sự tạo proxy có gắn `IntroductionAdvisor`, thông qua `ProxyFactory` hoặc một auto-proxy creator được cấu hình và sử dụng advisor đó.

## 5. Ví dụ Java 17 chạy được: một proxy dùng cả hai advisor

Ta có `OrderService` với method `placeOrder` và `getStatus`. `PointcutAdvisor` ghi log khi gọi `placeOrder`. `IntroductionAdvisor` thêm interface `Trackable`, chứa trạng thái bật/tắt theo dõi. Target hoàn toàn không khai báo `Trackable`.

**Cấu trúc dự án:**

```text
spring-advisor-demo/
├── pom.xml
└── src/main/java/demo/
    ├── App.java
    ├── OrderService.java
    ├── OrderServiceImpl.java
    ├── Trackable.java
    └── TrackingMixin.java
```

**`pom.xml`**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-advisor-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.0</version>
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
```

`spring-aop` cung cấp API advisor, pointcut, proxy và các introduction interceptor. Mẫu này sử dụng `NameMatchMethodPointcut`, vì vậy không cần thư viện parser AspectJ cho biểu thức `execution(...)`.

**`src/main/java/demo/OrderService.java`**

```java
package demo;

public interface OrderService {
    String placeOrder(String id);
    String getStatus();
}
```

**`src/main/java/demo/OrderServiceImpl.java`**

```java
package demo;

public class OrderServiceImpl implements OrderService {
    @Override
    public String placeOrder(String id) {
        return "ordered:" + id;
    }

    @Override
    public String getStatus() {
        return "ready";
    }
}
```

**`src/main/java/demo/Trackable.java`**

```java
package demo;

public interface Trackable {
    boolean isTrackingEnabled();
    void setTrackingEnabled(boolean enabled);
}
```

**`src/main/java/demo/TrackingMixin.java`**

```java
package demo;

public class TrackingMixin implements Trackable {
    private boolean enabled;

    @Override
    public boolean isTrackingEnabled() {
        return enabled;
    }

    @Override
    public void setTrackingEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
```

**`src/main/java/demo/App.java`**

```java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class App {
    public static void main(String[] args) {
        OrderServiceImpl target = new OrderServiceImpl();

        // (1) Chọn đúng method nghiệp vụ cần audit.
        NameMatchMethodPointcut methodPointcut =
                new NameMatchMethodPointcut();
        methodPointcut.setMappedName("placeOrder");

        MethodInterceptor audit = invocation -> {
            System.out.println("[audit] " + invocation.getMethod().getName());
            return invocation.proceed();
        };

        PointcutAdvisor pointcutAdvisor =
                new DefaultPointcutAdvisor(methodPointcut, audit);

        // (2) Chuẩn bị interface sẽ xuất hiện trên proxy.
        DelegatingIntroductionInterceptor introduction =
                new DelegatingIntroductionInterceptor(new TrackingMixin());

        DefaultIntroductionAdvisor introductionAdvisor =
                new DefaultIntroductionAdvisor(introduction, Trackable.class);

        // (3) Cả hai advisor được gắn trước khi tạo proxy.
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvisor(pointcutAdvisor);
        factory.addAdvisor(introductionAdvisor);

        OrderService proxy = (OrderService) factory.getProxy();

        // (4) Method selection: chỉ placeOrder được audit.
        System.out.println(proxy.placeOrder("B-01"));
        System.out.println(proxy.getStatus());

        // (5) Interface introduction: proxy, không phải target.
        System.out.println("target Trackable: " + (target instanceof Trackable));
        System.out.println("proxy Trackable: " + (proxy instanceof Trackable));

        Trackable tracking = (Trackable) proxy;
        System.out.println("tracking before: " + tracking.isTrackingEnabled());
        tracking.setTrackingEnabled(true);
        System.out.println("tracking after: " + tracking.isTrackingEnabled());
    }
}
```

Trong thư mục `spring-advisor-demo`, dùng JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
```

**Stdout mong đợi từ mã Java** (không gồm các dòng thông báo riêng của Maven):

```text
[audit] placeOrder
ordered:B-01
ready
target Trackable: false
proxy Trackable: true
tracking before: false
tracking after: true
```

Tại sao kết quả như vậy?

- `placeOrder` khớp `NameMatchMethodPointcut`, nên interceptor in `[audit] placeOrder` trước khi gọi target qua `proceed()`.
- `getStatus` không khớp, nên lời gọi chỉ trả về `ready`.
- `OrderServiceImpl` không hiện thực `Trackable`; kiểm tra `target instanceof Trackable` trả về `false`.
- Khi `DefaultIntroductionAdvisor` được thêm vào `ProxyFactory`, proxy công bố cả `OrderService` và `Trackable`; ép kiểu `(Trackable) proxy` thành công.
- Hai lời gọi `isTrackingEnabled` và `setTrackingEnabled` được giao cho object `TrackingMixin` bên trong introduction interceptor. Giá trị trạng thái được lưu ở mixin.

**Chú ý bản chất của ví dụ:** tạo `ProxyFactory` trực tiếp chứng minh hoạt động Spring AOP API mà không cần `@Aspect`, `@EnableAspectJAutoProxy` hay Spring Boot context. Dùng `ProxyFactory` không đồng nghĩa mọi bean trong một application đều đã được proxy hóa.

## 6. Dòng xử lý của từng advisor

```text
                     new OrderServiceImpl()
                              │
              ProxyFactory + addAdvisor(...)
                  /                       \
       PointcutAdvisor             IntroductionAdvisor
       NameMatchMethodPointcut     DefaultIntroductionAdvisor
       method = "placeOrder"       new interface = Trackable
                  \                       /
                     Spring AOP proxy
                         │        │
           proxy.placeOrder()    ((Trackable) proxy).setTrackingEnabled(...)
                         │        │
                audit interceptor  DelegatingIntroductionInterceptor
                         │        │
               target.placeOrder() TrackingMixin.setTrackingEnabled(...)
```

Trong minh họa trên, **pointcut** xác định method target nào có audit. **Introduction** cung cấp method mới cho kiểu proxy. Hai advisor có thể cùng nằm trong advisor chain; Spring quyết định interceptor nào cần tham gia vào lời gọi, theo loại advisor, phạm vi áp dụng và thứ tự.

Khi có nhiều advice tác động cùng method, **thứ tự** của advisor có thể ảnh hưởng đến kết quả và lúc xử lý exception. Spring hỗ trợ cơ chế ordering; `DefaultIntroductionAdvisor` cung cấp `setOrder(int)`. Với pointcut advisors, Spring hỗ trợ các lớp advisor dựa trên `Ordered`; nên cấu hình thứ tự tường minh nếu các advice phụ thuộc nhau. Đừng suy luận thứ tự nghiệp vụ chỉ từ vị trí dòng cấu hình khi có auto-proxy creator hoặc advisor infrastructure khác tham gia.

## 7. Quản lý vòng đời introduction và trạng thái

Trong ví dụ, `new TrackingMixin()` được truyền cho một `DelegatingIntroductionInterceptor` gắn với một proxy. Với một proxy thì trạng thái `enabled` là trạng thái của mixin đó.

Nếu **chia sẻ cùng một `DelegatingIntroductionInterceptor` cho nhiều target/proxy**, các proxy sẽ sử dụng **cùng delegate**, đồng nghĩa chia sẻ mutable state của mixin. Điều này dễ gây bug:

```java
// Minh họa rủi ro: không phải chương trình độc lập.
TrackingMixin shared = new TrackingMixin();
DelegatingIntroductionInterceptor interceptor =
        new DelegatingIntroductionInterceptor(shared);

// Nếu tái sử dụng interceptor/advisor này cho các proxy khác nhau,
// cả hai có thể cùng tham chiếu "shared".
```

Chọn theo yêu cầu:

- **Stateless behavior:** một introduction delegate được dùng chung có thể hợp lý khi không giữ trạng thái của target.
- **State riêng mỗi proxy:** tạo advisor + interceptor + delegate riêng cho từng proxy, như chương trình đầy đủ.
- **State riêng mỗi target khi dùng chung interceptor:** cân nhắc `DelegatePerTargetObjectIntroductionInterceptor`, có thể tạo delegate khác nhau theo target instance.
- **Concurrency:** nếu một mixin có mutable state và bị nhiều thread truy cập, phải tự giải quyết thread safety; introduction không tự làm trường `boolean enabled` trở nên synchronized/volatile.

Spring reference mô tả introduction advice là dạng thường liên quan đến trạng thái trên từng instance; đây là điểm đặc biệt so với advice audit/logging không giữ trạng thái.

## 8. Cấu hình với Spring IoC container

Khi dùng Spring Framework có container, `PointcutAdvisor` và `IntroductionAdvisor` có thể là bean. Tuy nhiên, **đăng ký advisor bean không tự bảo đảm nó được gắn vào tất cả bean của ứng dụng**. Cần cấu hình cơ chế auto-proxy phù hợp hoặc tạo `ProxyFactory`/`ProxyFactoryBean` có sử dụng advisor đó.

Ví dụ minh họa **chỉ phần cấu hình**, dùng `DefaultAdvisorAutoProxyCreator` khi chủ động muốn Spring tự xét các advisor bean đủ điều kiện:

```java
@Configuration
public class AopConfiguration {
    @Bean
    static DefaultAdvisorAutoProxyCreator advisorAutoProxyCreator() {
        return new DefaultAdvisorAutoProxyCreator();
    }

    // Tạo bean PointcutAdvisor / IntroductionAdvisor và target bean
    // với scope/lifecycle phù hợp trong cấu hình thực tế.
}
```

Đây là fragment minh họa, chưa gồm các import và bean implementation để chạy độc lập. Không cấu hình thêm một auto-proxy creator một cách tùy tiện vào ứng dụng đã có cơ chế Spring AOP khác; cần hiểu cơ chế proxy hiện hành và bảo đảm không trùng proxy. Để học API độc lập và xác minh hai advisor, `ProxyFactory` trong phần 5 là cách rõ ràng nhất.

**So với `@Aspect`:** annotation-based advice biểu diễn advice và pointcut trong một aspect class. Trong khi đó, `PointcutAdvisor`/`IntroductionAdvisor` là cách đóng gói rõ ràng bằng đối tượng API để Spring tạo và cấu hình proxy. Trường hợp introduction có thể xuất hiện qua cơ chế `@DeclareParents` trong Spring AOP, nhưng bài này tập trung vào hợp đồng `IntroductionAdvisor` và các lớp hỗ trợ trực tiếp.

## 9. Giới hạn của proxy và thay đổi cấu hình

1. **Phải gọi qua proxy:** `target.placeOrder(...)` không chạy audit advice. Lời gọi nội bộ `this.placeOrder(...)` trong cùng target cũng bỏ qua interception do Spring proxy tạo.
2. **Interface mới thuộc proxy:** không được ép kiểu thẳng `OrderServiceImpl` thành `Trackable`. Dùng object nhận từ `getProxy()` hoặc bean proxy được Spring cấp.
3. **Thêm introduction trước khi tạo proxy:** interface set ảnh hưởng đến loại proxy được sinh ra. Thêm hoặc gỡ introduction advisor đối với **proxy đã tồn tại** không thể làm proxy cũ có hoặc mất interface tương ứng; để thay đổi tập interface, phải tạo proxy mới.
4. **Giới hạn method interception:** JDK dynamic proxy dựa trên interface, còn class-based proxy có hạn chế với `final` class/method, `private` method và các đường gọi bỏ qua proxy.
5. **Không dùng sai advice:** introduction advice phải đi với `IntroductionAdvisor`; `DefaultPointcutAdvisor` không mang cơ chế công bố interface mới.
6. **Không hứa hẹn đồng nhất identity:** proxy và target là object khác nhau; đừng sử dụng `==` hoặc cast theo target class khi cấu hình proxy có thể là JDK proxy.
7. **Kiểm tra class filter:** `DefaultIntroductionAdvisor` mặc định áp dụng cho mọi target class khi advisor được xét; nếu dùng trong một hệ thống auto-proxy rộng, cần thiết kế phạm vi bean/class để tránh thêm interface ngoài ý muốn.

Đối với proxy đã sinh ra, Spring có interface `Advised` để quan sát danh sách advisor và có thể điều chỉnh một số advisor/interceptor. Nhưng việc sửa advisor của proxy cũ cần xét ràng buộc interface của introduction: thao tác thêm/bỏ introduction không tương đương việc thêm/bỏ một interceptor method thông thường.

## 10. Những lỗi thường gặp

| Triệu chứng | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| `getStatus()` cũng ghi log | `Pointcut` đang match quá rộng | Kiểm tra `NameMatchMethodPointcut` hoặc biểu thức `execution(...)`, giới hạn method thích hợp |
| Ép `(Trackable) target` gây `ClassCastException` | Target class không implement interface | Ép kiểu object **proxy**, kiểm tra `proxy instanceof Trackable` |
| Ép `(Trackable) proxy` thất bại | Introduction advisor chưa được gắn vào proxy đúng cách, hoặc interface không được công bố | Kiểm tra `getInterfaces()`, `validateInterfaces()`, thứ tự cấu hình và `getProxy()` mới |
| Exception khi thêm `DefaultIntroductionAdvisor` | Advice không đáp ứng interface được khai báo | Dùng `IntroductionInterceptor` có delegate thực sự implement interface; chọn constructor thích hợp |
| Method target không chạy audit | Caller đang gọi target trực tiếp hoặc self-invocation | Đi qua Spring AOP proxy và kiểm tra `getPointcut()` |
| Nhiều proxy thay đổi chung trạng thái | Dùng chung `DelegatingIntroductionInterceptor` với delegate mutable | Tạo delegate riêng hoặc dùng `DelegatePerTargetObjectIntroductionInterceptor` |
| Một bean không xuất hiện interface mới | Advisor bean chưa được auto-proxy creator áp dụng | Xác minh infrastructure tạo proxy, bean scope và target eligibility |
| Thêm introduction vào proxy đã tạo không có hiệu lực | Tập interface proxy đã cố định khi proxy được tạo | Cấu hình introduction rồi tạo proxy mới |
| Advice chạy theo thứ tự không mong đợi | Nhiều advisors, ordering không được kiểm soát | Định nghĩa thứ tự advisor rõ ràng và test chuỗi interceptor |

### Checklist thiết kế

- Tôi chỉ muốn chặn một nhóm method có sẵn? → Dùng `PointcutAdvisor` với `Pointcut` phù hợp.
- Tôi muốn caller ép kiểu proxy sang interface mới? → Dùng `IntroductionAdvisor` và implementation của interface đó.
- Tôi đã tạo proxy có gắn đúng advisor trước khi sử dụng?
- Tôi đã xác minh **cả đường gọi khớp lẫn không khớp** của pointcut?
- Tôi đã xác minh `target instanceof ExtraInterface` và `proxy instanceof ExtraInterface` để phân biệt target/proxy?
- Introduction delegate có mutable state không; state cần dùng chung, per-proxy hay per-target?
- Khi đưa vào Spring container, auto-proxy infrastructure và scope có phù hợp không?

## 11. Câu hỏi ôn tập

**Câu 1.** Tại sao `IntroductionAdvisor` có `ClassFilter` nhưng không có `MethodMatcher`?

**Đáp án:** vì Spring AOP introduction bổ sung khả năng implement interface mới cho proxy của một target class. Nó chọn lớp có đủ điều kiện để giới thiệu interface; không xác định một method target đang có có được method advice chặn hay không.

**Câu 2.** Một `OrderServiceImpl` không implement `Trackable` có thể gọi `setTrackingEnabled(true)` không?

**Đáp án:** bản thân `OrderServiceImpl` không có method này. Một proxy của target được cấu hình với `IntroductionAdvisor` có thể implement `Trackable`, và lời gọi qua interface trên proxy được delegate sang `TrackingMixin`.

**Câu 3.** Nếu chia sẻ một `DelegatingIntroductionInterceptor` chứa cùng `TrackingMixin` cho ba proxy thì trạng thái `enabled` thuộc về đâu?

**Đáp án:** nó thuộc object delegate dùng chung; ba proxy có thể cùng quan sát/chỉnh sửa một trạng thái. Nếu cần độc lập, hãy tạo delegate riêng theo proxy hoặc sử dụng giải pháp delegate theo target.

**Câu 4.** Vì sao không nên thử dùng `DefaultPointcutAdvisor` để bổ sung interface?

**Đáp án:** `PointcutAdvisor` chỉ mô tả phạm vi pointcut và advice cho method execution. Công bố và hiện thực interface mới là hợp đồng của `IntroductionAdvisor`/`IntroductionInterceptor`, được Spring xử lý khi xây dựng proxy.

### Tài liệu chính thức

- Spring Framework — Advisor API: https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html
- Spring Framework — Advice API, Introduction Advice: https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html
- Spring Framework — Pointcut API: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html
- Spring Framework — Manipulating advised objects: https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html
- Spring Javadoc — `PointcutAdvisor`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/PointcutAdvisor.html
- Spring Javadoc — `IntroductionAdvisor`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/IntroductionAdvisor.html
- Spring Javadoc — `DefaultIntroductionAdvisor`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/DefaultIntroductionAdvisor.html
- Spring Javadoc — `DelegatingIntroductionInterceptor`: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/DelegatingIntroductionInterceptor.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-advisor-api-007">Sắp xếp advisor và kiểm chứng chuỗi interceptor</a>

<details>
<summary>Xem chi tiết</summary>

# Sắp xếp Advisor và kiểm chứng chuỗi Interceptor trong Spring AOP

## 1. Mục tiêu bài học

Khi nhiều `Advisor` cùng áp dụng cho một phương thức Spring, thứ tự của chúng quyết định interceptor nào nhận lời gọi trước, interceptor nào bao bên ngoài interceptor khác, và advice nào chạy trong các nhánh thành công hoặc lỗi. Nếu kiểm tra chỉ bằng thứ tự dòng `println` của một phía lời gọi, ta rất dễ kết luận sai.

Bài này tập trung vào **Spring AOP proxy và Advice/Advisor API**: `ProxyFactory`, `Advised`, `Advisor`, `Pointcut`, `MethodInterceptor`, `Ordered` và cách Spring xây dựng chuỗi interceptor. Kiến thức cốt lõi cần nắm:

- Phân biệt **thứ tự các advisor được cấu hình** với **chuỗi interceptor thực sự có hiệu lực cho từng phương thức**.
- Thực hiện thứ tự tường minh khi dùng `ProxyFactory.addAdvisor(...)` và hiểu khi nào Spring dùng `@Order` / `Ordered`.
- Đọc đúng thứ tự **đi vào** và **đi ra** của các interceptor bao quanh lời gọi `proceed()`.
- Kiểm chứng bằng `Advised.getAdvisors()`, nhật ký sự kiện thực tế, kết quả trả về và assertion có thứ tự.
- Nhận diện tác động của pointcut, nhiều advice type, ngoại lệ và self-invocation tới chuỗi quan sát được.

## 2. Phân biệt thứ tự được cấu hình với chuỗi thực sự chạy

`Advised.getAdvisors()` trả về danh sách các `Advisor` **đã gắn** trên một proxy. Đây là thông tin cấu hình; không có nghĩa tất cả advisor đều chạy cho mọi method. `PointcutAdvisor` còn có `ClassFilter` và `MethodMatcher`; Spring chỉ đưa advice phù hợp vào chain cho method hiện hành. Với matcher động, arguments runtime có thể làm cùng một method chạy advice ở lần gọi này nhưng bỏ qua ở lần khác.

```text
Configured advisors: A(all), B(only execute), C(all)
proxy.execute() --> A --> B --> C --> target --> C --> B --> A
proxy.ping()    --> A -------> C --> target --> C -----> A
```

Sơ đồ giả định cả ba là around interceptors gọi `proceed()` đúng một lần, với A đứng ngoài cùng. `proceed()` thực thi phần còn lại của chain, đến target khi không còn interceptor; một interceptor có thể short-circuit hoặc ném lỗi trước đó. Nếu callback là `MethodBeforeAdvice`/`ThrowsAdvice`, Spring dùng adapter để đưa nó vào chain nhưng thời điểm callback khác around advice.

Như vậy cần hai loại bằng chứng: **kiểm tra `getAdvisors()`** để xác nhận thứ tự cấu hình, rồi **ghi trace invocation** để biết advisor nào đã thực thi ở lần gọi cụ thể. Bài *MethodInterceptor và MethodInvocation* giải thích cơ chế lồng nhau qua `proceed()`; phần này tập trung vào cách kiểm chứng thứ tự khi advisor được lọc theo pointcut.

## 3. Thứ tự áp dụng: “vào trước” nhưng “ra sau”

Giả sử ba interceptor đều bao quanh target, thứ tự chain từ ngoài vào là A → B → C. Nếu từng interceptor ghi log trước `proceed()` và trong `finally`:

```text
A.enter
  B.enter
    C.enter
      target.run
    C.exit
  B.exit
A.exit
```

| Vị trí | Trên đường vào | Trên đường ra |
| --- | --- | --- |
| A — bên ngoài cùng | Đầu tiên | Cuối cùng |
| B — ở giữa | Thứ hai | Áp chót |
| C — bên trong cùng | Cuối cùng trước target | Đầu tiên sau target |

Điều này xuất phát trực tiếp từ các lời gọi `proceed()` lồng nhau, **không** phải Spring tự đảo danh sách advisor sau khi target chạy. Với advice chỉ chạy trước (`MethodBeforeAdvice` / `@Before`), không có phần logic “exit” đối xứng; với `@AfterReturning`, `@AfterThrowing` và `@After`, thời điểm chạy còn phụ thuộc loại advice và kết quả lời gọi.

Nếu interceptor A bắt ngoại lệ, thay kết quả trả về hoặc không gọi `proceed()`, A có thể ảnh hưởng toàn bộ những interceptor bên trong. Do đó, thứ tự advisor là quyết định hành vi, không chỉ là vấn đề trình bày log.

## 4. Hai đường thiết lập thứ tự trong Spring AOP

### 4.1. Cấu hình `ProxyFactory` bằng tay

Với `ProxyFactory`, bạn trực tiếp quyết định danh sách advisor bằng thứ tự các lệnh `addAdvisor(...)` hoặc vị trí truyền vào `addAdvisor(int pos, Advisor)`. Spring lưu thứ tự này trong đối tượng `Advised`.

```java
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvisor(a);       // index 0
factory.addAdvisor(b);       // index 1
factory.addAdvisor(c);       // index 2

// Có thể chuyển c lên đầu:
factory.removeAdvisor(c);
factory.addAdvisor(0, c);    // thứ tự còn lại: c, a, b
```

Đoạn trên là minh họa, các biến `target`, `a`, `b`, `c` được định nghĩa trong ví dụ chạy đầy đủ ở mục 5. **`setOrder(...)` hoặc `@Order` không tự động sắp xếp lại các lệnh `addAdvisor(...)` của một `ProxyFactory` tự cấu hình.** Muốn thay đổi thứ tự, hãy thêm theo đúng vị trí, thêm có chỉ số, hoặc chủ động sắp xếp danh sách rồi mới đưa vào factory.

### 4.2. Spring tự phát hiện advisor/aspect

Khi dùng Spring container cùng cơ chế auto-proxy, Spring có thể thu thập và **sắp xếp** các advisor/aspect được phát hiện. Giá trị từ `Ordered.getOrder()` và `@Order` giúp chỉ định độ ưu tiên:

- **Giá trị nhỏ hơn ⇒ ưu tiên cao hơn**; ví dụ `@Order(5)` có độ ưu tiên cao hơn `@Order(20)`.
- Với hai `@Aspect` riêng biệt, aspect ưu tiên cao hơn đi vào trước và đi ra sau nếu chúng cùng bao quanh một join point.
- Không nên dựa vào thứ tự phát hiện bean, vị trí khai báo class hoặc hai giá trị `@Order` bằng nhau để tạo hợp đồng thứ tự ổn định.
- Hạ tầng khác như transaction advisor cũng có thứ tự riêng; khi có nhiều advisor hạ tầng, phải kiểm tra chain cuối cùng trên proxy thực tế.

Ví dụ **minh họa trong ứng dụng Spring có bật `@EnableAspectJAutoProxy` và đăng ký hai aspect làm bean**:

```java
@Aspect
@Component
@Order(5)
class TracingAspect {
    @Around("execution(* example.service..*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("tracing.enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("tracing.exit");
        }
    }
}

@Aspect
@Component
@Order(20)
class AuditingAspect {
    @Around("execution(* example.service..*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("audit.enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("audit.exit");
        }
    }
}
```

Đây là **trích đoạn minh họa**, không phải chương trình Java độc lập: nó cần import, Spring context, thư viện AspectJ tương ứng, bean target nằm ở package khớp pointcut và cấu hình bật auto-proxy. Nếu cả hai aspect khớp target và không có advice khác xen giữa, thứ tự quan sát được là `tracing.enter → audit.enter → target → audit.exit → tracing.exit`.

**Lưu ý:** thứ tự giữa các advice khác loại trong **cùng một `@Aspect`** còn theo quy tắc precedence của Spring: ưu tiên theo loại `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`. Do ngữ nghĩa `after finally`, `@After` có thể thực sự được gọi sau `@AfterReturning`/`@AfterThrowing` trong cùng aspect. Nếu có nhiều method cùng một advice type áp dụng tại cùng join point trong cùng aspect, thứ tự của chúng không được bảo đảm bằng vị trí khai báo source; hãy gộp logic hoặc tách ra các aspect có thứ tự rõ ràng. Đừng coi danh sách loại advice như một timeline tuyến tính đơn giản.

## 5. Ví dụ chạy được: kiểm chứng thứ tự và pointcut

Dựng một proxy thủ công với ba advisor:

- **A** dùng `Pointcut.TRUE`: khớp tất cả phương thức.
- **B** dùng `NameMatchMethodPointcut`: chỉ khớp `execute`.
- **C** dùng `Pointcut.TRUE`: khớp tất cả phương thức.

Ta chạy hai cấu hình: `A,B,C` và `C,A,B`, xác minh `Advised.getAdvisors()` bằng **đúng identity** của từng advisor, sau đó so sánh **từng sự kiện thực tế** với thứ tự dự kiến. Các interceptor là around advice nên ghi `.in` trước `proceed()` và `.out` trong `finally`.

Tạo dự án Gradle chạy Java 17+.

**`build.gradle`**:

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-aop:6.1.21'
}

application {
    mainClass = 'example.AdvisorChainDemo'
}
```

**`src/main/java/example/AdvisorChainDemo.java`**:

```java
package example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public final class AdvisorChainDemo {

    public interface JobService {
        String execute(String job);
        String ping();
    }

    static final class JobServiceImpl implements JobService {
        private final List<String> trace;

        JobServiceImpl(List<String> trace) {
            this.trace = trace;
        }

        @Override
        public String execute(String job) {
            trace.add("target.execute(" + job + ")");
            return "done:" + job;
        }

        @Override
        public String ping() {
            trace.add("target.ping");
            return "pong";
        }
    }

    private static MethodInterceptor tracing(String name, List<String> trace) {
        return invocation -> {
            trace.add(name + ".in");
            try {
                return invocation.proceed();
            } finally {
                // Ghi cả khi target hoặc interceptor trong ném ngoại lệ.
                trace.add(name + ".out");
            }
        };
    }

    private static void assertTrace(List<String> actual, List<String> expected) {
        if (!actual.equals(expected)) {
            throw new AssertionError(
                    "Expected: " + expected + "\nActual: " + actual);
        }
    }

    private static void scenario(boolean cFirst) {
        List<String> trace = new ArrayList<>();
        ProxyFactory factory = new ProxyFactory(new JobServiceImpl(trace));
        factory.setInterfaces(JobService.class);
        factory.setProxyTargetClass(false); // JDK interface proxy

        NameMatchMethodPointcut executeOnly = new NameMatchMethodPointcut();
        executeOnly.setMappedName("execute");

        DefaultPointcutAdvisor a =
                new DefaultPointcutAdvisor(Pointcut.TRUE, tracing("A", trace));
        DefaultPointcutAdvisor b =
                new DefaultPointcutAdvisor(executeOnly, tracing("B", trace));
        DefaultPointcutAdvisor c =
                new DefaultPointcutAdvisor(Pointcut.TRUE, tracing("C", trace));

        factory.addAdvisor(a);
        factory.addAdvisor(b);
        factory.addAdvisor(c);

        if (cFirst) {
            factory.removeAdvisor(c);
            factory.addAdvisor(0, c);
        }

        JobService proxy = (JobService) factory.getProxy();

        Advisor[] expectedAdvisors = cFirst
                ? new Advisor[] {c, a, b}
                : new Advisor[] {a, b, c};
        Advisor[] actualAdvisors = ((Advised) proxy).getAdvisors();
        if (!Arrays.equals(expectedAdvisors, actualAdvisors)) {
            throw new AssertionError("Incorrect advisor order");
        }

        System.out.println(cFirst ? "ORDER=C,A,B" : "ORDER=A,B,C");

        String result = proxy.execute("batch-7");
        if (!"done:batch-7".equals(result)) {
            throw new AssertionError("Unexpected return value");
        }

        List<String> expectedExecute = cFirst
                ? List.of(
                        "C.in", "A.in", "B.in",
                        "target.execute(batch-7)",
                        "B.out", "A.out", "C.out")
                : List.of(
                        "A.in", "B.in", "C.in",
                        "target.execute(batch-7)",
                        "C.out", "B.out", "A.out");
        assertTrace(trace, expectedExecute);
        System.out.println(String.join(" > ", trace));

        trace.clear();
        if (!"pong".equals(proxy.ping())) {
            throw new AssertionError("Unexpected ping result");
        }

        List<String> expectedPing = cFirst
                ? List.of("C.in", "A.in", "target.ping", "A.out", "C.out")
                : List.of("A.in", "C.in", "target.ping", "C.out", "A.out");
        assertTrace(trace, expectedPing);
        System.out.println(String.join(" > ", trace));
    }

    public static void main(String[] args) {
        scenario(false);
        scenario(true);
    }
}
```

Chạy tại thư mục chứa `build.gradle`:

```bash
gradle run
```

Nếu dự án có Gradle wrapper, dùng `./gradlew run` trên Linux/macOS hoặc `.\gradlew.bat run` trên Windows PowerShell. Khi các assertion đều qua, phần stdout do `main` sinh ra (không tính log của Gradle) là:

```text
ORDER=A,B,C
A.in > B.in > C.in > target.execute(batch-7) > C.out > B.out > A.out
A.in > C.in > target.ping > C.out > A.out
ORDER=C,A,B
C.in > A.in > B.in > target.execute(batch-7) > B.out > A.out > C.out
C.in > A.in > target.ping > A.out > C.out
```

**Ba kết luận từ lần chạy:**

1. **Thứ tự advisor cấu hình được kiểm tra bằng identity**: `((Advised) proxy).getAdvisors()` phải khớp mảng advisor dự kiến. Nếu thứ tự đăng ký không đúng, chương trình ném `AssertionError` trước khi gọi target.
2. **`execute` khớp A, B, C**: trace chứng minh đường vào theo thứ tự advisor đang lưu và đường ra đảo ngược do `proceed()` lồng nhau. Đưa C lên vị trí 0 làm C bao quanh A và B.
3. **`ping` chỉ khớp A, C**: cả hai cấu hình vẫn chứa B trong `getAdvisors()`, nhưng B không xuất hiện trong trace `ping` vì pointcut tên phương thức đã loại B. Trace và thứ tự toàn cục của advisor là hai bằng chứng khác nhau.

Đây là một chương trình Java độc lập trong dự án Gradle có dependency Spring AOP, còn `assertTrace` cho phép kiểm tra nội dung **và thứ tự từng phần tử**. Việc chạy hai cấu hình trong cùng chương trình giúp tránh nhầm kết quả của một thứ tự với thứ tự khác.

## 6. Nếu advice không phải MethodInterceptor thì sao?

Spring AOP hỗ trợ nhiều loại advice qua Advisor API, chẳng hạn:

| Loại advice trong Spring | Khi chạy | Quan hệ với chain |
| --- | --- | --- |
| `MethodInterceptor` | Bao quanh lời gọi | Tự gọi `invocation.proceed()` |
| `MethodBeforeAdvice` | Trước lời gọi tiếp theo | Spring thích nghi thành interceptor; không tự gọi `proceed()` |
| `AfterReturningAdvice` | Khi phần lời gọi bên trong trở về bình thường | Chỉ chạy nhánh thành công của phần bên trong |
| `ThrowsAdvice` | Khi phần lời gọi bên trong ném exception phù hợp | Phụ thuộc exception và chữ ký handler |
| `@Around` / `@Before` / `@After` trong `@Aspect` | Tùy ngữ nghĩa từng annotation | Spring chuyển thành các interceptor thích hợp khi xây dựng AOP chain |

Ví dụ tạo before advice và bọc trong Advisor:

```java
MethodBeforeAdvice before = (method, args, target) ->
        System.out.println("before " + method.getName());

DefaultPointcutAdvisor beforeAdvisor =
        new DefaultPointcutAdvisor(Pointcut.TRUE, before);

factory.addAdvisor(beforeAdvisor);
```

Đoạn này chỉ minh họa và giả định `factory` là `ProxyFactory` đã tạo, đồng thời có import `org.springframework.aop.MethodBeforeAdvice`. Bạn không gọi `proceed()` trong `MethodBeforeAdvice`; Spring tự sử dụng adapter của advice trong chuỗi interceptor. Vì vậy nếu kiểm chứng chuỗi hỗn hợp, hãy ghi log theo thời điểm **thật sự chạy** của từng advice, không suy luận mỗi advisor đều có hai dòng “enter/exit”.

## 7. Quy trình kiểm chứng khi xảy ra sai thứ tự

Khi một advice chạy bất ngờ, hãy kiểm tra theo thứ tự sau:

1. **Xác nhận đúng proxy:** lời gọi có đi qua Spring proxy không? Có thể dùng `AopUtils.isAopProxy(bean)` khi làm việc với bean trong Spring context, hoặc kiểm tra object nhận từ `ProxyFactory.getProxy()`.
2. **Kiểm tra advisor thực sự đã đăng ký:** ép kiểu sang `Advised` khi proxy hỗ trợ giao diện này, đọc `getAdvisors()`, loại advice và pointcut. Nếu proxy được cấu hình “opaque”, giao diện `Advised` có thể không được lộ ra; hãy dùng factory hoặc công cụ debug phù hợp.
3. **Kiểm tra pointcut theo phương thức:** một advisor có trong `getAdvisors()` chưa chắc khớp phương thức đang gọi. Kiểm tra `ClassFilter`, static `MethodMatcher` và runtime `MethodMatcher` khi áp dụng.
4. **Kiểm tra order source:** với `ProxyFactory` thủ công, kiểm tra thứ tự thêm hoặc index; với Spring auto-proxy, kiểm tra `@Order`/`Ordered` của từng aspect/advisor và mọi advisor hạ tầng.
5. **Ghi trace “vào / target / ra / exception”:** dùng correlation id nếu nhiều request chạy đồng thời, ghi method, đối số cần thiết, kết quả và lỗi theo cách an toàn; kiểm tra đường đi ngược của `proceed()`.
6. **So bằng assertion có thứ tự:** dùng `List.equals(...)` trên danh sách sự kiện kỳ vọng như ví dụ, thay vì chỉ kiểm tra từng dòng có xuất hiện. Kiểm tra thêm trường hợp pointcut không khớp và trường hợp target ném exception.

**Không nên phụ thuộc vào class nội bộ của Spring hoặc cấu trúc cache của `DefaultAdvisorChainFactory` để viết test ứng dụng.** `Advised.getAdvisors()` cùng trace hành vi ở ranh giới proxy là bằng chứng ổn định và dễ bảo trì hơn. Nếu đã có các advisor do Spring container tạo, hãy kiểm tra trên **bean proxy thực tế** thay vì chỉ nhìn code của một `@Aspect`.

## 8. Những trường hợp làm chuỗi trông “sai”

### 8.1. Nhầm thứ tự “ra” với thứ tự “vào”

A ưu tiên cao hơn B nghĩa là A chạy trước **trên đường vào**, nhưng nếu cùng bao quanh target, A hoàn tất **sau B** trên đường ra. `@After` trong cùng một aspect còn có ngữ nghĩa riêng; không dự đoán toàn bộ thứ tự từ giá trị `@Order` duy nhất.

### 8.2. Advice bỏ qua `proceed()`

```java
MethodInterceptor shortCircuit = invocation -> "cached";
```

Khi interceptor này được gọi và không `proceed()`, mọi interceptor nằm phía trong và target không chạy; interceptor phía ngoài vẫn có thể hoàn tất phần `finally`. Đây là tính năng của around advice, nên một trace bị thiếu target không mặc nhiên là sai thứ tự.

### 8.3. Target ném ngoại lệ

Với interceptor ghi `.out` trong `finally` như ví dụ mục 5, các dòng `.out` vẫn được thêm khi ngoại lệ đi ngược qua chuỗi. Tên `.out` chỉ nói rằng đã thoát khỏi interceptor; **không** có nghĩa target thành công. Nếu cần audit thành công, chỉ ghi sau khi `proceed()` trả về bình thường; nếu cần audit lỗi, dùng `catch` rồi ném lại hoặc advice chuyên biệt.

### 8.4. Self-invocation

Lời gọi `this.otherMethod()` từ bên trong target thường không qua Spring AOP proxy, nên interceptor tương ứng không chạy. Kiểm chứng bằng cách gọi qua **proxy**; đừng dùng một lời gọi nội bộ để kết luận advisor bị sắp xếp sai.

### 8.5. Bằng nhau về thứ tự hoặc thay đổi chain lúc chạy

Hai advisor/aspect cùng giá trị order mà không có ràng buộc ưu tiên thêm không tạo hợp đồng thứ tự chặt chẽ. Nếu thay đổi advisor trên proxy có thể sửa được, hãy kiểm tra lại `getAdvisors()` và trace sau khi cập nhật. Với ứng dụng production, nên ưu tiên cấu hình ổn định tại khởi tạo.

### 8.6. Nhầm “advisor đã có” với “advisor đang chạy”

`getAdvisors()` trả **danh sách cấu hình**. Một pointcut động có thể áp dụng cho lời gọi đầu tiên nhưng không áp dụng cho lời gọi thứ hai của cùng method vì giá trị `args` khác nhau. Không thể chỉ dùng `getAdvisors()` để chứng minh những lời gọi nào đã chạy advice; cần trace hoặc quan sát hành vi thực tế.

## 9. Checklist áp dụng vào dự án Spring

- Có nhiều advisor cho một method: xác định thứ tự mong muốn **cả lúc vào và lúc ra**.
- Với `ProxyFactory`: thêm advisor theo index chính xác rồi xem `getAdvisors()`.
- Với auto-proxy / `@Aspect`: dùng thứ tự tường minh qua `@Order` hoặc `Ordered` giữa các aspect; tránh phụ thuộc thứ tự mặc định.
- Nếu có pointcut hẹp: kiểm tra riêng method khớp và không khớp.
- Nếu có before/after/throws advice: không giả định mọi advice đều hoạt động như around interceptor có `proceed()`.
- Kiểm tra target success, exception, short-circuit và self-invocation khi các trường hợp này quan trọng.
- Gắn assertion vào trace để phát hiện thay đổi thứ tự làm thay đổi ngữ nghĩa nghiệp vụ.

## 10. Câu hỏi tự kiểm tra

1. Chain là A → B → C với cả ba interceptor dùng `try/finally` quanh `proceed()`. Thứ tự ra là gì? **C → B → A**.
2. `Advised.getAdvisors()` trả A, B, C; method `ping` không khớp pointcut của B. B có chạy không? **Không**, dù B vẫn có mặt trong danh sách advisor.
3. Với `ProxyFactory` thủ công, gọi `setOrder(1)` trên C có tự chuyển C lên vị trí đầu không? **Không**; cần thêm/chuyển advisor đúng vị trí hoặc tự sắp xếp.
4. Hai `@Aspect` riêng có `@Order(5)` và `@Order(20)`, cùng chạy around advice. Aspect nào vào trước? **5**; và ra **sau** aspect 20.
5. Một around interceptor không gọi `proceed()`: liệu các interceptor phía trong và target có chạy không? **Không** qua nhánh lời gọi đó.
6. Chỉ kiểm tra `getAdvisors()` có đủ chứng minh một interceptor đã chạy cho một lần gọi cụ thể không? **Không**; còn pointcut, đối số runtime và luồng `proceed()`.

## Tài liệu Spring chính thức

- [The Advisor API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html)
- [Advice API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html)
- [Creating AOP Proxies with ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html)
- [Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html)
- [Advice Ordering for @AspectJ in Spring AOP](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html#advice-ordering)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-advisor-api-008">Advice adapter và khả năng chuyển loại advice sang interceptor</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: Advice adapter và khả năng chuyển loại advice sang interceptor

## Mục tiêu bài học

Bài này giải thích **cơ chế chuyển đổi các loại advice của Spring thành chuỗi interceptor** trong Spring AOP, gồm:

- Vì sao API cho phép gắn `MethodBeforeAdvice`, `AfterReturningAdvice`, `ThrowsAdvice` và `MethodInterceptor` vào cùng một proxy.
- Vai trò chính xác của `AdvisorAdapter`, `AdvisorAdapterRegistry`, `DefaultAdvisorAdapterRegistry` và `GlobalAdvisorAdapterRegistry`.
- Các adapter sẵn có, khi nào Spring cần adapter và khi nào advice đã là interceptor.
- Cách đọc, kiểm tra một chuỗi chuyển đổi thực tế bằng Java 17+.
- Cách viết một custom advice cùng custom adapter khi thật sự cần mở rộng Spring AOP.
- Các giới hạn và lỗi thường gặp khi đăng ký adapter, dùng pointcut, chọn phạm vi proxy và xử lý ngoại lệ.

Bài tập trung vào **Spring Framework AOP**, cụ thể nhóm API `org.springframework.aop.framework.adapter`. Không cần đi sâu vào lý thuyết AOP tổng quát hoặc cơ chế weave ngoài Spring AOP.

## 1. Vấn đề Spring cần giải quyết

Trong Spring AOP, lời gọi phương thức đi qua **proxy** được xử lý bởi một chuỗi interceptor. Tuy nhiên, lập trình viên có thể cấu hình nhiều dạng advice:

```java
MethodBeforeAdvice before = (method, args, target) -> {
    System.out.println("Before " + method.getName());
};

AfterReturningAdvice after = (returnValue, method, args, target) -> {
    System.out.println("Returned: " + returnValue);
};

MethodInterceptor around = invocation -> {
    System.out.println("Around before");
    Object result = invocation.proceed();
    System.out.println("Around after");
    return result;
};
```

Các đoạn trên là **minh họa callback**, cần import Spring/AOP Alliance và đặt trong class để biên dịch. Cả ba đều mô tả hành vi tại thời điểm gọi, nhưng chỉ `MethodInterceptor` trực tiếp cung cấp:

```java
Object invoke(MethodInvocation invocation) throws Throwable;
```

Spring cần “ngôn ngữ thực thi chung” để lắp chúng thành chuỗi. **Advice adapter** là thành phần biến một kiểu `Advice` Spring hiểu được thành `MethodInterceptor` có thể chạy trong chuỗi proxy.

Điều này giải thích vì sao một `DefaultPointcutAdvisor` có thể chứa `MethodBeforeAdvice` hoặc `ThrowsAdvice`, trong khi proxy vẫn xử lý lời gọi theo mô hình interceptor. Không cần tự bọc từng before/after advice thành `MethodInterceptor` khi sử dụng các loại Spring đã hỗ trợ.

## 2. Các thành phần và ranh giới trách nhiệm

| Thành phần | Vai trò | API/kiểu quan trọng |
| --- | --- | --- |
| `Advice` | Hành vi bổ sung tại một join point | `org.aopalliance.aop.Advice` (marker) |
| `Advisor` | Cung cấp advice, có thể đi kèm điều kiện áp dụng | `org.springframework.aop.Advisor` |
| `PointcutAdvisor` | Advisor có `Pointcut` để chọn lớp/method phù hợp | `org.springframework.aop.PointcutAdvisor` |
| `MethodInterceptor` | API thực thi từng bước trong chuỗi | `org.aopalliance.intercept.MethodInterceptor` |
| `AdvisorAdapter` | Nhận diện một kiểu advice và chuyển thành interceptor | `supportsAdvice`, `getInterceptor` |
| `AdvisorAdapterRegistry` | Tập hợp adapters, bọc advice thành advisor và lấy interceptor | `wrap`, `getInterceptors`, `registerAdvisorAdapter` |
| `DefaultAdvisorAdapterRegistry` | Registry Spring mặc định với adapter dựng sẵn | class triển khai `AdvisorAdapterRegistry` |
| `GlobalAdvisorAdapterRegistry` | Cung cấp registry chung cho hạ tầng Spring AOP | `getInstance()` |

**Điều cần phân biệt:** adapter **chuyển hành vi advice thành interceptor**, còn pointcut **quyết định một advisor có khớp với lời gọi hay không**. Một adapter không phải công cụ quyết định bean nào sẽ được proxy và cũng không nên tự làm nhiệm vụ match pointcut.

Sơ đồ đường đi khái quát:

```text
Đăng ký advice / advisor
          |
          v
+---------------------------+
| AdvisorAdapterRegistry    |
| getInterceptors(advisor)  |
+---------------------------+
          |
          +-- MethodInterceptor? --------> dùng trực tiếp
          |
          +-- MethodBeforeAdvice? -------> MethodBeforeAdviceInterceptor
          |
          +-- AfterReturningAdvice? -----> AfterReturningAdviceInterceptor
          |
          +-- ThrowsAdvice? -------------> ThrowsAdviceInterceptor
          |
          +-- Custom Advice? ------------> custom AdvisorAdapter (nếu đăng ký)
          |
          v
Chuỗi MethodInterceptor trong Spring AOP proxy
          |
          v
Lời gọi qua proxy, xét Pointcut/ClassFilter/MethodMatcher
          |
          v
Advice tương ứng + target method
```

Sơ đồ lược hóa thứ tự triển khai để thể hiện sự chuyển đổi. Trên thực tế, framework lựa chọn các advisor hợp lệ và xây dựng interceptor chain theo lớp, phương thức và cấu hình proxy; khi có điều kiện runtime, chuỗi có thể có phần tử kiểm tra matching. Adapter chỉ là một mắt xích của quá trình đó.

## 3. Ba phương thức của `AdvisorAdapterRegistry`

Interface `org.springframework.aop.framework.adapter.AdvisorAdapterRegistry` cung cấp ba thao tác chính:

```java
Advisor wrap(Object advice) throws UnknownAdviceTypeException;

MethodInterceptor[] getInterceptors(Advisor advisor)
        throws UnknownAdviceTypeException;

void registerAdvisorAdapter(AdvisorAdapter adapter);
```

**`wrap(Object advice)`:** chuyển một advice được hỗ trợ thành `Advisor`, thường là `DefaultPointcutAdvisor` (pointcut mặc định khớp rộng). Nếu đối số vốn là `Advisor`, trả chính advisor đó. Nếu kiểu truyền vào không phải advice hoặc không được registry hiểu, ném `UnknownAdviceTypeException`. Điểm quan trọng: **bọc advice không có nghĩa là advice đã được gắn vào proxy**.

**`getInterceptors(Advisor advisor)`:** lấy mảng `MethodInterceptor` biểu diễn advice của advisor. Registry có thể trả nhiều interceptor nếu một kiểu advice được nhiều adapter hỗ trợ; với các loại chuẩn của Spring, thông thường là một interceptor tương ứng. Khi advice vốn triển khai `MethodInterceptor`, registry có thể sử dụng trực tiếp, không cần wrapper bổ sung. Phương thức này **không lọc pointcut** và không quyết định method cụ thể nào sẽ chạy.

**`registerAdvisorAdapter(AdvisorAdapter adapter)`:** bổ sung adapter hiểu một loại advice mới. Đây là SPI dành cho tác giả mở rộng Spring AOP; với các before, after-returning, throws advice sẵn có, ứng dụng không cần tự đăng ký adapter.

### Interface `AdvisorAdapter`

```java
public interface AdvisorAdapter {
    boolean supportsAdvice(Advice advice);

    MethodInterceptor getInterceptor(Advisor advisor);
}
```

- `supportsAdvice` cho biết adapter có hiểu kiểu advice đó không.
- `getInterceptor` tạo interceptor thực thi hành vi của advice chứa trong advisor. Chỉ gọi sau khi `supportsAdvice` đã xác nhận phù hợp.
- Với `PointcutAdvisor`, việc match pointcut do **Spring AOP framework** đảm nhiệm; adapter chỉ chuyển advice.

Một adapter viết sai có thể làm advice không bao giờ chạy, chạy sai thời điểm hoặc mất khả năng propagate kết quả/ngoại lệ. Vì vậy hãy dùng adapter sẵn có khi kiểu advice đã được Spring hỗ trợ.

## 4. Các adapter chuẩn trong `DefaultAdvisorAdapterRegistry`

Spring đăng ký các adapter tiêu chuẩn:

| Advice ban đầu | AdvisorAdapter tương ứng | Interceptor được tạo | Hành vi |
| --- | --- | --- | --- |
| `MethodBeforeAdvice` | `MethodBeforeAdviceAdapter` | `MethodBeforeAdviceInterceptor` | Gọi `before(...)` rồi tiếp tục `proceed()` |
| `AfterReturningAdvice` | `AfterReturningAdviceAdapter` | `AfterReturningAdviceInterceptor` | Gọi `proceed()`, sau đó gọi `afterReturning(...)` khi trả về bình thường |
| `ThrowsAdvice` | `ThrowsAdviceAdapter` | `ThrowsAdviceInterceptor` | Gọi `proceed()`, khi ném lỗi thì chọn `afterThrowing(...)` phù hợp |
| `MethodInterceptor` | Không cần adapter riêng | Dùng `MethodInterceptor` ban đầu | Trực tiếp tham gia chuỗi |

Ví dụ logic bên trong trước/sau được diễn giải **gần đúng** như sau (mã giả nhằm hiểu cơ chế, không phải bản chép implementation):

```java
// Ý tưởng của MethodBeforeAdviceInterceptor:
beforeAdvice.before(invocation.getMethod(),
                    invocation.getArguments(),
                    invocation.getThis());
return invocation.proceed();
```

```java
// Ý tưởng của AfterReturningAdviceInterceptor:
Object result = invocation.proceed();
afterReturningAdvice.afterReturning(result,
        invocation.getMethod(),
        invocation.getArguments(),
        invocation.getThis());
return result;
```

```java
// Ý tưởng của ThrowsAdviceInterceptor:
try {
    return invocation.proceed();
} catch (Throwable ex) {
    // Tìm public afterThrowing(...) khớp kiểu ngoại lệ,
    // gọi handler nếu có rồi tiếp tục ném ngoại lệ theo chính sách.
    throw ex;
}
```

Spring tra các phương thức `afterThrowing` theo signature hợp lệ của `ThrowsAdvice`. Handler có thể có một tham số exception hoặc bốn tham số `Method, Object[], Object, ExceptionType`; kiểu exception cuối xác định lỗi được xử lý. Nếu advice không có handler `afterThrowing` phù hợp, Spring có thể từ chối cấu hình advice đó. Cần phân biệt “một advice throws có handler nhưng **lỗi hiện tại không khớp**” với “advice implements marker nhưng **không định nghĩa handler hợp lệ**”.

**Thứ tự advice matters:** giả sử có hai after-returning interceptors lồng nhau, thứ tự thực tế của callback phụ thuộc cách Spring đặt advisor vào chuỗi. Nếu interceptor bên trong ném ngoại lệ, interceptor bên ngoài có thể quan sát ngoại lệ đó theo chính sách của nó. Đừng dựa vào thời điểm chỉ từ tên interface khi đã có nhiều advisor, transaction và retry.

## 5. Ví dụ runnable: quan sát conversion và dùng custom adapter

Ví dụ dùng Spring Framework 6.2.x, JDK 17+, Maven. Nó minh họa một **registry có sẵn các adapter chuẩn**, sau đó đăng ký một adapter tùy chỉnh và gọi service qua proxy. Không cần Spring Boot hoặc `@EnableAspectJAutoProxy` vì dùng `ProxyFactory` trực tiếp.

### 5.1. Cấu trúc dự án

```text
advice-adapter-demo/
├── pom.xml
└── src/main/java/example/AdviceAdapterDemo.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>advice-adapter-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.19</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>example.AdviceAdapterDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/example/AdviceAdapterDemo.java`:

```java
package example;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.ThrowsAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.adapter.AdvisorAdapter;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.support.DefaultPointcutAdvisor;

public class AdviceAdapterDemo {
    public interface Greeting {
        String greet(String name);
    }

    public static class GreetingService implements Greeting {
        @Override
        public String greet(String name) {
            System.out.println("TARGET greet");
            return "Hello " + name;
        }
    }

    public static class FailureAdvice implements ThrowsAdvice {
        public void afterThrowing(IllegalArgumentException ex) {
            System.out.println("FAIL " + ex.getMessage());
        }
    }

    // Loại advice mới: chỉ mô tả dữ liệu và hành vi cần gắn vào lời gọi.
    public record TagAdvice(String tag) implements Advice { }

    public static class TagAdviceAdapter implements AdvisorAdapter {
        @Override
        public boolean supportsAdvice(Advice advice) {
            return advice instanceof TagAdvice;
        }

        @Override
        public MethodInterceptor getInterceptor(Advisor advisor) {
            TagAdvice tagAdvice = (TagAdvice) advisor.getAdvice();
            return invocation -> {
                String method = invocation.getMethod().getName();
                System.out.println(tagAdvice.tag() + " before " + method);
                Object result = invocation.proceed();
                System.out.println(tagAdvice.tag() + " after " + method);
                return result;
            };
        }
    }

    public static void main(String[] args) {
        AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();

        MethodBeforeAdvice before = (method, arguments, target) -> { };
        AfterReturningAdvice after =
                (result, method, arguments, target) -> { };
        ThrowsAdvice throwsAdvice = new FailureAdvice();
        MethodInterceptor around = invocation -> invocation.proceed();

        Advisor beforeAdvisor =
                new DefaultPointcutAdvisor(Pointcut.TRUE, before);
        Advisor afterAdvisor =
                new DefaultPointcutAdvisor(Pointcut.TRUE, after);
        Advisor throwsAdvisor =
                new DefaultPointcutAdvisor(Pointcut.TRUE, throwsAdvice);
        Advisor aroundAdvisor =
                new DefaultPointcutAdvisor(Pointcut.TRUE, around);

        System.out.println(registry.getInterceptors(beforeAdvisor)[0]
                .getClass().getSimpleName());
        System.out.println(registry.getInterceptors(afterAdvisor)[0]
                .getClass().getSimpleName());
        System.out.println(registry.getInterceptors(throwsAdvisor)[0]
                .getClass().getSimpleName());
        System.out.println("Direct: " +
                (registry.getInterceptors(aroundAdvisor)[0] == around));

        registry.registerAdvisorAdapter(new TagAdviceAdapter());

        Advisor customAdvisor =
                new DefaultPointcutAdvisor(Pointcut.TRUE, new TagAdvice("TAG"));
        System.out.println("Custom interceptor count: " +
                registry.getInterceptors(customAdvisor).length);

        ProxyFactory factory = new ProxyFactory(new GreetingService());
        factory.setInterfaces(Greeting.class);
        factory.addAdvisor(customAdvisor);
        Greeting proxy = (Greeting) factory.getProxy();

        System.out.println("CLIENT " + proxy.greet("Lan"));
    }
}
```

Lệnh chạy trong thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

Các dòng do chương trình in ra:

```text
MethodBeforeAdviceInterceptor
AfterReturningAdviceInterceptor
ThrowsAdviceInterceptor
Direct: true
Custom interceptor count: 1
TAG before greet
TARGET greet
TAG after greet
CLIENT Hello Lan
```

### 5.2. Đọc kết quả từng bước

1. `GlobalAdvisorAdapterRegistry.getInstance()` lấy registry chung mà hạ tầng Spring AOP mặc định sử dụng. Ba conversion đầu không cần cấu hình bổ sung vì adapter của `MethodBeforeAdvice`, `AfterReturningAdvice` và `ThrowsAdvice` đã có sẵn.
2. `getInterceptors(beforeAdvisor)` trả một `MethodBeforeAdviceInterceptor`; tương tự hai loại advice chuẩn còn lại.
3. `MethodInterceptor` vốn là kiểu mà chuỗi cần: `getInterceptors(aroundAdvisor)[0] == around` cho `true`. Không có “chuyển đổi bắt buộc” nếu đã sử dụng interface này.
4. `TagAdvice` implements `Advice`, nên là một kiểu advice hợp lệ về mặt marker nhưng **chưa được registry mặc định hiểu**.
5. `TagAdviceAdapter.supportsAdvice` nhận diện `TagAdvice`; `getInterceptor` biến nó thành lambda `MethodInterceptor`. Lambda gọi `proceed()` đúng một lần và giữ nguyên kết quả.
6. `registerAdvisorAdapter` đăng ký khả năng chuyển đổi trước khi proxy cần xây dựng chain. Sau đó `factory.addAdvisor(customAdvisor)` gắn advisor vào proxy của đúng `GreetingService`.
7. `Pointcut.TRUE` chỉ nói advisor khớp các method trong phạm vi proxy. Chính `ProxyFactory` xác định target được bao, `proxy.greet(...)` là lời gọi đi qua AOP. Gọi `new GreetingService().greet("Lan")` trực tiếp sẽ không xuất hiện hai dòng `TAG`.

Ví dụ dùng singleton registry để minh họa đường đi **thực sự của Spring AOP proxy**. Đăng ký adapter vào singleton là thay đổi chung trong JVM/application context đang dùng chung registry, vì vậy chỉ nên làm có chủ đích trong bootstrap ứng dụng; tránh đăng ký lặp khi chạy test. Với nhu cầu chỉ thử conversion thuần túy, có thể khởi tạo riêng `new DefaultAdvisorAdapterRegistry()` và gọi `registerAdvisorAdapter` trên instance đó, nhưng **instance cục bộ không tự động được `ProxyFactory` mặc định sử dụng**.

## 6. Cách Spring dùng registry khi xây interceptor chain

Từ một hoặc nhiều advisor được gắn lên proxy, Spring AOP phải xây chuỗi xử lý cho method hiện tại:

1. **Có proxy và danh sách advisor:** ví dụ `ProxyFactory.addAdvisor(...)` hoặc Spring container auto-proxy một bean theo cấu hình.
2. **Kiểm tra tính phù hợp:** framework xét loại advisor; nếu có pointcut, xét class filter và method matcher với target/method tương ứng.
3. **Chuyển advice tương ứng:** registry cung cấp `MethodInterceptor[]` bằng adapter, hoặc trực tiếp với `MethodInterceptor`. Với điều kiện runtime, Spring còn có thể bao logic kiểm tra match lúc invocation.
4. **Thực thi chain:** lời gọi đi qua interceptor, rồi đến target khi chuỗi tiếp tục. Advice có thể ném lỗi, thay đổi kết quả, hoặc không gọi `proceed()` theo loại và chính sách.

Spring thường cache các chain được xây dựng cho phương thức để tránh lặp lại toàn bộ quá trình thích nghi advice ở mỗi lời gọi. Không nên hiểu “adapter được gọi một lần khi ứng dụng khởi động” là cam kết tuyệt đối: thời điểm tạo chain tùy proxy, class/method, lifecycle và việc cấu hình AOP có thay đổi.

**Điểm tách biệt rất quan trọng:** `AdvisorAdapter.getInterceptor(advisor)` **không được tự match `Pointcut`**. Spring đã có infrastructure để làm việc đó, và matcher có thể kiểm tra class, method hoặc điều kiện runtime. Nếu trộn hai trách nhiệm, adapter tùy chỉnh dễ chạy sai với các pointcut khác nhau hoặc các cấu hình advisor lồng nhau.

## 7. Khi nào nên viết custom advice và adapter?

Trước khi triển khai SPI, hãy hỏi: có nhất thiết phải tạo một **loại advice mới** không?

- Nếu bạn chỉ cần log trước/sau lời gọi, đo thời gian, thay kết quả hay bắt ngoại lệ, dùng trực tiếp `MethodInterceptor`. Đây là lựa chọn nhỏ gọn nhất và đã tương thích với chain.
- Nếu chỉ cần before, after-returning hoặc throws callback riêng, dùng interface chuẩn tương ứng. Spring đã có adapter và thiết lập cơ chế thực thi phù hợp.
- Nếu đang thiết kế một thư viện Spring AOP cung cấp **khái niệm advice chuyên biệt** (ví dụ, một policy object khai báo hành vi hoặc tham số tracing mà adapter dùng để tạo interceptor), custom `Advice` + `AdvisorAdapter` mới có ý nghĩa.

Một custom advice đúng chuẩn SPI:

```java
import org.aopalliance.aop.Advice;

// Custom Advice marker:
public record MarkerAdvice(String label) implements Advice { }
```

Đây là đoạn minh họa độc lập về kiểu, cần đặt trong file `MarkerAdvice.java` của một Maven/Gradle project có `spring-aop` trên classpath (hoặc dependency AOP Alliance phù hợp). Chỉ `implements Advice` **chưa khiến Spring có thể chạy kiểu này**: cần adapter hỗ trợ và đăng ký đúng registry.

Một vài quy tắc khi viết adapter:

- `supportsAdvice` phải đủ hẹp, thường kiểm tra `instanceof MyAdvice`. Nếu trả `true` với mọi advice, registry có thể chọn adapter cho kiểu không tương thích và phát sinh `ClassCastException`.
- `getInterceptor` nên giữ nguyên semantics của advice: trước/sau hay xử lý ngoại lệ như tài liệu của kiểu mới, và trả đúng giá trị `invocation.proceed()` nếu không chủ đích thay đổi kết quả.
- Nếu advice phụ thuộc metadata của target/method, khai thác `MethodInvocation` đúng ranh giới; không giả định target luôn là một bean singleton hoặc gọi `invocation.getThis()` luôn khác `null`.
- Tránh đặt trạng thái mutable dành riêng cho từng request vào field của adapter/interceptor dùng chung khi có nhiều thread.
- Nếu custom advice không được nhận diện, kiểm tra nơi đăng ký adapter **trước khi xây chain**, kiểu `Advice` thực sự, advisor đã gắn vào proxy và lời gọi có đi qua proxy không.

### Đăng ký adapter trong Spring container

Trong hạ tầng container, Spring cung cấp `AdvisorAdapterRegistrationManager` (một `BeanPostProcessor`) để đăng ký các bean `AdvisorAdapter` vào registry, mặc định là `GlobalAdvisorAdapterRegistry`. Đây là SPI nâng cao dành cho tích hợp framework hoặc thư viện; cách cấu hình thực tế phải bảo đảm registration manager được kích hoạt đúng lúc và advisor không bị tạo chain trước khi adapter sẵn sàng.

Nếu bạn không xây dựng extensibility layer, hãy giữ `MethodInterceptor`/advice chuẩn và những cấu hình advisor rõ ràng thay vì đưa một registry toàn cục mutable vào ứng dụng chỉ cho một tác vụ logging.

## 8. Những nhầm lẫn thường gặp

**Nhầm 1: “AdvisorAdapter là Advisor”.** Hai interface khác nhau. `Advisor` giữ advice (và có thể pointcut); `AdvisorAdapter` là chiến lược chuyển kiểu advice thành interceptor. `DefaultPointcutAdvisor` không phải `AdvisorAdapter`.

**Nhầm 2: “Đặt MethodBeforeAdvice vào proxy nghĩa là phải tự gọi before()”.** Spring dùng `MethodBeforeAdviceAdapter` để cung cấp `MethodBeforeAdviceInterceptor`. Interceptor đó gọi callback và tiếp tục chain.

**Nhầm 3: “getInterceptors(...) luôn trả một interceptor”.** API trả mảng vì hỗ trợ mở rộng. Một advice có thể được nhiều adapter hỗ trợ; thay đổi registry có thể ảnh hưởng thứ tự/ số interceptor. Nếu không adapter nào phù hợp, có thể phát sinh `UnknownAdviceTypeException`.

**Nhầm 4: “wrap(advice) là đủ để mọi bean bị intercept”.** `wrap` chỉ tạo hoặc trả advisor. Advice vẫn cần được gắn vào proxy/đăng ký trong cơ chế auto-proxy phù hợp; pointcut và bean selection còn quyết định phạm vi.

**Nhầm 5: “Mọi Advice đều phải được convert”.** `MethodInterceptor` đã là interceptor nên không cần adapter riêng. Đây là lý do quanh advice thường dễ tích hợp trực tiếp.

**Nhầm 6: “Adapter quyết định đúng method nào khớp”.** Việc này do pointcut matcher và AOP infrastructure của Spring đảm nhiệm. `getInterceptor` nhận advisor nhưng không được tự diễn giải pointcut để chọn method.

**Nhầm 7: “Đăng ký adapter trên registry tự tạo là ProxyFactory sẽ thấy”.** Một `DefaultAdvisorAdapterRegistry` cục bộ chỉ có hiệu lực nếu hạ tầng được cấu hình sử dụng đúng instance đó. Ví dụ dùng `GlobalAdvisorAdapterRegistry` để đi với đường mặc định của proxy.

**Nhầm 8: “Adapter loại bỏ giới hạn Spring proxy”.** Không. Các vấn đề về self-invocation, lời gọi trực tiếp vào target, `final`/`private`, JDK dynamic proxy/CGLIB vẫn tồn tại. Advice converter không làm biến đổi những method không đi qua proxy thành join point.

**Nhầm 9: “AfterReturningAdviceInterceptor có thể thay kết quả bằng việc gán vào returnValue”.** Callback `AfterReturningAdvice` là `void`. Nếu cần thay kết quả, hãy dùng `MethodInterceptor` có `return` phù hợp. Với throws advice, callback thường ghi nhận lỗi rồi để ngoại lệ tiếp tục lan truyền; không tự coi nó là cơ chế phục hồi lỗi.

**Nhầm 10: “Tất cả advice chạy ngay khi tương tác với registry”.** `getInterceptors` tạo/nhận interceptor, không tự gọi target. Callback advice chỉ chạy khi có invocation qua proxy thực thi chuỗi phù hợp.

## 9. Bảng chọn API

| Yêu cầu | Cách giải quyết trong Spring AOP |
| --- | --- |
| Chạy hành vi trước target | `MethodBeforeAdvice`; adapter sẵn có |
| Ghi nhận kết quả trả về bình thường | `AfterReturningAdvice`; adapter sẵn có |
| Quan sát ngoại lệ phù hợp | `ThrowsAdvice` với `afterThrowing(...)` hợp lệ; adapter sẵn có |
| Bao toàn bộ lời gọi, đo elapsed time, biến đổi return, dùng `try/finally` | `MethodInterceptor` trực tiếp |
| Áp dụng một advice với pointcut rõ ràng | `DefaultPointcutAdvisor` và `ProxyFactory`/auto-proxy |
| Thêm một loại advice mới cho một thư viện AOP | `Advice` + `AdvisorAdapter` + đăng ký registry |
| Kiểm tra Spring dùng adapter nào | `AdvisorAdapterRegistry.getInterceptors(advisor)` và xem kiểu interceptor |
| Chẩn đoán advice không chạy | Kiểm tra proxy, advisor, pointcut, adapter registration và lời gọi qua proxy |

## 10. Câu hỏi thực hành

1. `MethodBeforeAdvice` không triển khai `MethodInterceptor`. Bằng cơ chế nào nó vẫn được Spring AOP proxy gọi?
2. `AdvisorAdapterRegistry.wrap(advice)` có tự động gắn advice đó vào mọi proxy không?
3. Tại sao `getInterceptors(advisor)` không nên thay thế logic match pointcut?
4. Nếu custom `Advice` được tạo hợp lệ nhưng chưa có adapter, việc gắn advisor có thể gặp lỗi gì?
5. Khi nào nên sử dụng `MethodInterceptor` luôn, thay vì tạo một custom `Advice` và `AdvisorAdapter`?
6. Tại sao thêm adapter vào `new DefaultAdvisorAdapterRegistry()` không bảo đảm proxy mặc định dùng được nó?
7. Nếu hai adapter đều `supportsAdvice` với một loại advice, bạn phải kiểm tra thêm yếu tố nào khi đọc interceptor chain?
8. Vì sao advice adapter không giúp intercept lời gọi `this.innerMethod()` bên trong cùng một target?

## Tài liệu Spring chính thức

- [Defining New Advice Types (Spring Framework 6.2)](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/extensibility.html) — điểm mở rộng mới trong `org.springframework.aop.framework.adapter`.
- [The Advisor API in Spring](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advisor.html) — quan hệ giữa advisor, advice và pointcut.
- [Advice API in Spring](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advice.html) — before, after-returning, throws, interceptor advice.
- [Programmatic ProxyFactory](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html) — khởi tạo và cấu hình proxy.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html) — JDK proxy, CGLIB và self-invocation.
- [AdvisorAdapter SPI (Javadoc)](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/adapter/AdvisorAdapter.html) — chữ ký `supportsAdvice`/`getInterceptor`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advice-advisor-api-009">Ranh giới giữa mở rộng hạ tầng và viết aspect nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Ranh giới giữa mở rộng hạ tầng Spring AOP và viết aspect nghiệp vụ

## 1. Vấn đề cần giải quyết

Trong Spring Framework, hai đoạn mã đều có thể chạy quanh một lời gọi phương thức qua **Spring AOP proxy**, nhưng chúng phục vụ những mục tiêu khác nhau:

- **Mở rộng hạ tầng AOP**: xây cơ chế có thể tái sử dụng cho nhiều bean, như đo thời gian, tracing, tích hợp thư viện hoặc một quy tắc kỹ thuật áp dụng theo metadata. Mã thường làm việc trực tiếp với `Advice`, `Advisor`, `Pointcut`, `MethodInterceptor`, `ProxyFactory` và auto-proxy infrastructure.
- **Aspect nghiệp vụ**: thể hiện chính sách gắn với ngôn ngữ nghiệp vụ và một nhóm thao tác cụ thể, như ghi nhận sự kiện audit nghiệp vụ khi xác nhận đơn hàng thành công. Mã thường dùng `@Aspect`, `@Before`, `@AfterReturning`, `@AfterThrowing`, `@Around`, `@Pointcut` của phong cách @AspectJ được **Spring AOP** hỗ trợ.

**Ranh giới được quyết định bởi trách nhiệm, độ tái sử dụng và điều kiện áp dụng, không chỉ bởi annotation hay API:** một aspect `@Aspect` có thể làm logging hạ tầng, còn một `Advisor` programmatic cũng có thể mang quy tắc nghiệp vụ. Chọn cách tổ chức khiến mã dễ hiểu, dễ kiểm thử và có phạm vi rõ ràng.

Bài này chỉ xét **Spring AOP chạy bằng proxy**. Không đi sâu vào lý thuyết AOP nói chung hay công cụ AspectJ weaving độc lập.

## 2. Hai con đường cấu hình hội tụ ở đâu?

### Đường API hạ tầng

Một cấu hình programmatic thường theo luồng:

```text
MethodInterceptor / MethodBeforeAdvice / AfterReturningAdvice ...
                          +
               Pointcut (ClassFilter + MethodMatcher)
                          |
                 DefaultPointcutAdvisor
                          |
      Bean tự động được tạo proxy / ProxyFactory
                          |
                 Interceptor chain
                          |
                       Target
```

Các kiểu cốt lõi của Spring:

- `org.aopalliance.intercept.MethodInterceptor`: bao quanh lời gọi thông qua `invoke(MethodInvocation)`, thường gọi `invocation.proceed()`.
- `org.springframework.aop.Pointcut`: chọn lớp và method đủ điều kiện.
- `org.springframework.aop.Advisor`: liên kết một advice với thông tin áp dụng; `org.springframework.aop.support.DefaultPointcutAdvisor` là lựa chọn phổ biến.
- `org.springframework.aop.framework.ProxyFactory`: tạo proxy thủ công; trong container, auto-proxy creator có thể nhận các `Advisor` bean và tạo proxy tự động.

Khi cần điều khiển chính xác class filter, method matcher, hay tái dùng `MethodInterceptor` trong một thư viện, các API này làm rõ hợp đồng hạ tầng. Đối với advisor theo annotation có thể dùng `AspectJExpressionPointcut` hoặc các pointcut có sẵn thay vì luôn viết matcher mới.

### Đường aspect khai báo

```text
@Aspect + @Pointcut / @Before / @AfterReturning / @Around ...
                           |
         Spring @AspectJ auto-proxy support
                           |
         Advisor và interceptor tương ứng
                           |
                  Spring AOP proxy
                           |
                      Target method
```

`@EnableAspectJAutoProxy` cho phép Spring phát hiện các aspect bean và tạo proxy cho các bean phù hợp. Cần có `org.aspectj:aspectjweaver` trên classpath để xử lý biểu thức @AspectJ, nhưng **dùng @Aspect trong Spring AOP không có nghĩa là đã bật AspectJ compile-time/load-time weaving**. Cơ chế chặn method vẫn đi qua Spring proxy.

Một `@Aspect` có thể trình bày nghiệp vụ dễ đọc hơn: `@AfterReturning` diễn đạt điều kiện “chỉ khi thao tác thành công” mà không phải viết `try`/`proceed()` trực tiếp.

## 3. Ma trận lựa chọn: tiêu chí thực tế

| Tiêu chí | Mở rộng hạ tầng AOP | Aspect nghiệp vụ |
| --- | --- | --- |
| Câu hỏi chính | “Hệ thống thực thi, quan sát hoặc điều phối method thế nào?” | “Sự kiện/chính sách nghiệp vụ nào xảy ra khi thao tác X chạy?” |
| Phạm vi áp dụng | Nhiều service/module hoặc tích hợp thư viện | Nhóm use case, hành vi và thuật ngữ domain cụ thể |
| Điểm mở rộng thường gặp | `Advisor`, `MethodInterceptor`, `ClassFilter`, `MethodMatcher` | `@Aspect` và các annotation advice |
| Dữ liệu sử dụng | Metadata method, arguments, target, tracing context, thông số kỹ thuật | Khái niệm domain, kết quả nghiệp vụ, annotation nghiệp vụ |
| Chủ thể sở hữu | Platform/framework/infrastructure team | Domain/application team |
| Thay đổi thường xuyên | Khi chính sách kỹ thuật hoặc tích hợp framework thay đổi | Khi quy tắc nghiệp vụ thay đổi |
| Ví dụ | Ghi latency, gắn correlation ID, instrumentation có điểm cắm chung | Ghi nhận audit “đơn hàng đã xác nhận” trên operation đã chọn |
| Kiểm thử | Kiểm tra matcher, thứ tự advisor, exception/return, proxy | Kiểm tra phạm vi method và điều kiện nghiệp vụ của advice |
| Rủi ro | Intercept quá rộng, nhân đôi proxy, xung đột advisor hạ tầng | Giấu logic domain quan trọng, phát sự kiện sai thời điểm commit |

Đây là **quy ước kiến trúc**, không phải ràng buộc Java: logging hoàn toàn có thể viết bằng `@Aspect`. Ngược lại, việc có một `Advisor` không tự khiến một chính sách trở thành hạ tầng.

### Tự hỏi trước khi chọn cách triển khai

1. Nếu xóa hết tên domain khỏi mã mà trách nhiệm vẫn có ý nghĩa (metrics, tracing, retries ở mức transport), đó là dấu hiệu của **hạ tầng**.
2. Nếu mã phải hiểu “đơn đã duyệt”, “giao dịch đủ điều kiện”, “phiếu đã phát hành”, đó là dấu hiệu của **nghiệp vụ**.
3. Nếu logic là **bước bắt buộc quyết định trạng thái giao dịch**, không nên âm thầm giấu nó trong advice chỉ vì có thể intercept. Thường nên đặt trong application/domain service với lời gọi tường minh.
4. Nếu một chức năng đã có cơ chế Spring chuyên dụng như `@Transactional`, caching, security method hoặc observation, hãy đánh giá cơ chế đó trước khi tự chế hạ tầng AOP.

## 4. Khi nào tạo Advisor/MethodInterceptor riêng?

API programmatic có ý nghĩa rõ ràng khi bạn cần một trong những khả năng:

- **Thư viện tích hợp Spring** cung cấp một `Advisor` áp dụng thống nhất cho nhiều bean theo annotation, interface, class hoặc method metadata.
- **Lọc class và method bằng Java API**: `StaticMethodMatcherPointcut`, `NameMatchMethodPointcut`, `ComposablePointcut` hoặc matcher động khi thực sự cần kiểm tra arguments tại runtime.
- **Quản lý chain rõ ràng** qua `Advisor`, thứ tự `Ordered`, vị trí interception, result/exception, hoặc `ProxyFactory` để thử nghiệm độc lập với application context.
- **Thay đổi cơ chế kỹ thuật bằng cấu hình** mà không bắt từng module viết aspect giống nhau.

Hãy ưu tiên dùng pointcut và advisor có sẵn trong Spring. Chỉ tự viết một `MethodMatcher`, `Advisor` hay auto-proxy creator mới khi API hiện tại không thể hiện đúng yêu cầu. Một `@Bean Advisor` thông thường thường đã đủ cho custom interception khi cấu hình auto-proxy thích hợp; không cần tự tạo `BeanPostProcessor` mới chỉ để gắn advice.

**Ví dụ tình huống hạ tầng:** trên mọi method được gắn `@ObservedOperation`, áp một interceptor thu thập số lượng request, kết quả lỗi/thành công và thời lượng. Phần thiết kế thuộc thư viện có thể dùng matcher theo annotation và một advisor dùng chung, không chứa mã biết tên đơn hàng hoặc khách hàng.

## 5. Khi nào viết @Aspect cho nghiệp vụ?

`@Aspect` phù hợp với một **chính sách cắt ngang thuộc application/domain** mà nhiều entry point cần áp dụng nhất quán, đồng thời điều kiện chọn pointcut đủ rõ để người đọc kiểm chứng.

Ví dụ:

- Sau khi một use case `confirmOrder` trả kết quả thành công, ghi một dấu vết audit chứa order ID và trạng thái.
- Trước các operation được đánh dấu bằng annotation nghiệp vụ `@RequiresBusinessApproval`, kiểm tra điều kiện ứng dụng đã được mô hình hóa rõ.
- Theo dõi việc phát sinh một sự kiện domain cần thiết cho audit, nếu độ tin cậy và vòng đời transaction của việc ghi nhận đã được thiết kế riêng.

Ví dụ **không nên thay bằng aspect**:

- Tính tiền, kiểm tra tồn kho, chuyển trạng thái đơn hàng hoặc quyết định cuối cùng có cho phép xác nhận hay không: những bước này là hành vi cốt lõi và nên hiện diện trong luồng use case.
- Ghi sự kiện “đã thanh toán” vào message broker trong `@AfterReturning` rồi giả định transaction DB chắc chắn đã commit. `@AfterReturning` chỉ biết **method trả về bình thường tại vị trí advice**, không tự bảo đảm DB commit và không có cơ chế delivery đáng tin. Nếu cần “sau commit”, cân nhắc `@TransactionalEventListener(phase = AFTER_COMMIT)` phù hợp; khi yêu cầu bảo đảm phát sự kiện bền vững, cần thiết kế outbox hoặc cơ chế tương đương.
- Copy logic validation của service thành nhiều `@Before` rời rạc, khiến phương thức có thể hợp lệ ở đường gọi trực tiếp nhưng sai khi qua proxy.

**Một policy nghiệp vụ cần được nhìn thấy trong code review và test.** Nếu advisor che khuất một quyết định quan trọng, đưa thao tác đó về service hoặc cung cấp contract/annotation cụ thể, tài liệu và test rõ ràng.

## 6. Ví dụ Java 17+: một proxy chứa advisor hạ tầng và aspect nghiệp vụ

Ví dụ sau tạo một ứng dụng **Spring Framework 6.2.14**, không cần Spring Boot, để minh họa hai cách cấu hình cùng hoạt động. `Advisor` kỹ thuật log vòng gọi `submit`; `@Aspect` nghiệp vụ chỉ ghi log khi `submit` hoàn thành bình thường. Hai advice đều được Spring đưa vào AOP proxy. Giá trị `100` và `200` thiết lập thứ tự tương đối có chủ đích.

Cấu trúc:

```text
spring-aop-boundary/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/
    ├── App.java
    ├── AppConfig.java
    ├── WorkPort.java
    ├── WorkService.java
    └── BusinessAuditAspect.java
```

**`settings.gradle`**

```groovy
rootProject.name = 'spring-aop-boundary'
```

**`build.gradle`**

```groovy
plugins {
    id 'java'
    id 'application'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework:spring-context:6.2.14'
    implementation 'org.springframework:spring-aop:6.2.14'
    implementation 'org.aspectj:aspectjweaver:1.9.24'
}

application {
    mainClass = 'demo.App'
}
```

**`src/main/java/demo/WorkPort.java`**

```java
package demo;

public interface WorkPort {
    String submit(String id);
    String status();
}
```

**`src/main/java/demo/WorkService.java`**

```java
package demo;

public class WorkService implements WorkPort {
    @Override
    public String submit(String id) {
        System.out.println("[service] submit " + id);
        return "OK-" + id;
    }

    @Override
    public String status() {
        System.out.println("[service] status");
        return "READY";
    }
}
```

**`src/main/java/demo/BusinessAuditAspect.java`**

```java
package demo;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(200)
public class BusinessAuditAspect {

    @AfterReturning(
            pointcut = "execution(* demo.WorkService.submit(..))",
            returning = "result")
    public void recordSuccessfulSubmission(Object result) {
        System.out.println("[business] confirmed " + result);
    }
}
```

`@AfterReturning` diễn đạt rõ “chỉ ghi sau khi method trả về thành công”. Đây là log minh họa audit nghiệp vụ; trong hệ thống thực, cần thiết kế nơi lưu trữ và tính nhất quán transaction theo yêu cầu.

**`src/main/java/demo/AppConfig.java`**

```java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {

    @Bean
    public WorkPort workService() {
        return new WorkService();
    }

    @Bean
    public BusinessAuditAspect businessAuditAspect() {
        return new BusinessAuditAspect();
    }

    @Bean
    public Advisor infrastructureAdvisor() {
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("submit");

        MethodInterceptor tracing = invocation -> {
            String name = invocation.getMethod().getName();
            System.out.println("[infra] begin " + name);
            try {
                return invocation.proceed();
            } finally {
                System.out.println("[infra] end " + name);
            }
        };

        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(pointcut, tracing);
        advisor.setOrder(100);
        return advisor;
    }
}
```

`Advisor` ghép `NameMatchMethodPointcut` và `MethodInterceptor` theo Spring API. Với `@EnableAspectJAutoProxy`, Spring tìm các advisor bean hợp lệ cùng các advisor sinh từ `@Aspect`, rồi xây interceptor chain cho từng bean cần proxy. Mẫu `NameMatchMethodPointcut` ở đây khớp tên `submit` trên các bean thích hợp trong context; trong hệ thống thực, **giới hạn thêm theo lớp, package hoặc annotation** nếu không muốn áp dụng rộng ngoài service dự kiến.

**`src/main/java/demo/App.java`**

```java
package demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(
                AppConfig.class)) {
            WorkPort work = context.getBean(WorkPort.class);
            System.out.println("result=" + work.submit("ORD-7"));
            System.out.println("status=" + work.status());
        }
    }
}
```

Chạy ở thư mục có `build.gradle`:

```bash
gradle run --quiet
# Nếu project có Gradle wrapper: ./gradlew run --quiet
```

**Stdout của riêng chương trình**, không kể dòng thông báo tùy môi trường/build tool:

```text
[infra] begin submit
[service] submit ORD-7
[business] confirmed OK-ORD-7
[infra] end submit
result=OK-ORD-7
[service] status
status=READY
```

Lý giải:

1. `context.getBean(WorkPort.class)` trả về bean phù hợp; Spring AOP cung cấp proxy vì hai advisor áp dụng cho method `submit`.
2. `infrastructureAdvisor` có order `100`, ưu tiên cao hơn aspect `@Order(200)`: interceptor hạ tầng chạy phần `begin` trước khi chuyển tiếp.
3. `tracing.invoke()` gọi `proceed()`; bên trong Spring xử lý advice từ `BusinessAuditAspect` và gọi `WorkService.submit("ORD-7")`.
4. Sau khi target trả `"OK-ORD-7"`, `@AfterReturning` ghi nhận sự kiện thành công. Interceptor ngoài tiếp tục `finally`, ghi `end`, rồi result đến caller.
5. `status()` không khớp hai điều kiện đã khai báo nên chạy method target mà không có log `infra` hoặc `business`.

Trong ví dụ này, **cả hạ tầng và nghiệp vụ cùng dùng một cơ chế Spring proxy**, khác nhau ở trách nhiệm và cách định nghĩa advice/pointcut. Hai bean cùng tham gia chain chứ không cần tạo hai proxy nối thủ công.

### Điều gì xảy ra khi có exception?

Nếu `WorkService.submit` ném ngoại lệ, `@AfterReturning` **không chạy**, nhưng `finally` trong infrastructure interceptor vẫn chạy. Nếu nghiệp vụ cần ghi nhận thất bại, có thể bổ sung `@AfterThrowing` riêng với hợp đồng lỗi rõ ràng; đừng dùng `@AfterReturning` để diễn đạt cả thành công lẫn thất bại.

### Kiểm tra chính xác thứ tự

Đừng giả định mọi advisor đều tự nhiên có thứ tự. Trong ví dụ, `advisor.setOrder(100)` và `@Order(200)` là tín hiệu rõ ràng; giá trị **nhỏ hơn có precedence cao hơn**. Với around advice, interceptor ở ngoài vào trước và phần sau `proceed()` chạy sau. Trong ứng dụng có transaction/cache/security advisor, cần xem thứ tự thực tế, vì thay order có thể thay đổi việc log xảy ra trong hay ngoài transaction.

Có thể kiểm tra danh sách advisor được gắn vào proxy:

```java
// Minh họa, thêm vào App.main sau khi lấy biến work:
if (work instanceof org.springframework.aop.framework.Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

Đây là đoạn chèn thêm phục vụ debug, **không nằm trong stdout đã công bố**. Một advisor được cấu hình trên proxy vẫn có thể không match mọi method.

## 7. Ranh giới tích hợp của Spring cần giữ rõ

### Không dùng AOP như cơ chế thay thế mọi phần của ứng dụng

`Advisor` hoặc `@Aspect` chỉ phù hợp với điểm cắt ngang method được Spring proxy quản lý. Không nên tạo “magic” để giải quyết mọi hành vi:

- **Điều phối use case** như thanh toán rồi lưu kho là luồng nghiệp vụ phải dễ đọc; gọi các domain/application service trực tiếp giúp quản lý transaction, thứ tự và lỗi rõ hơn.
- **Kích hoạt sau commit** thuộc vòng đời transaction. Advice theo method return không tự bảo đảm commit hoặc phát message chính xác một lần.
- **Điểm cắt khác method execution** (field read/write, constructor invocation, call site bất kỳ) không thuộc phạm vi Spring AOP proxy-based thông thường.

### Proxy boundary và self-invocation

```java
// Đoạn minh họa, không phải file độc lập:
public void createAndSubmit(String id) {
    this.submit(id); // gọi trên cùng target; thường bypass advice của Spring AOP proxy
}
```

Nếu client gọi `proxy.createAndSubmit(...)` và từ target gọi `this.submit(...)`, advice cho `submit` không tự được thực thi qua proxy trong đường gọi nội bộ đó. Điều này áp dụng **cả advisor hạ tầng lẫn aspect nghiệp vụ**. Không dựa vào chúng làm đường kiểm soát duy nhất của một bất biến nghiệp vụ.

Với JDK dynamic proxy, Spring proxy biểu diễn các interface; với class-based proxy, phương thức `final` không thể override để áp advice, và method `private` không thể là điểm intercept hợp lệ. Trong Spring Boot hoặc khi nhiều cài đặt proxy được kết hợp, kiểu proxy thực tế có thể khác thiết lập đơn lẻ; hãy kiểm tra cấu hình của ứng dụng.

### Advice là singleton và trạng thái chia sẻ

Các advisor/advice thông thường được tạo như bean singleton. Tránh ghi `lastOrderId`, `lastResult` hoặc biến thời gian của mỗi request vào field mutable của aspect/interceptor dùng chung: lời gọi concurrent sẽ làm lẫn dữ liệu. Dùng biến cục bộ quanh `proceed()` hoặc context propagation được thiết kế cho hạ tầng. `ThreadLocal` nếu dùng cần xử lý cleanup đúng cách, nhất là khi chạy async/reactive.

### Tránh phụ thuộc vòng và can thiệp bean hạ tầng

Không nên viết một advisor bao trùm `*.*(..)` cho toàn bộ bean, kể cả `@Configuration`, infrastructure bean và advisor/aspect bean. Auto-proxy creator của Spring có các quy tắc loại trừ hạ tầng, nhưng pointcut quá rộng vẫn làm các service thông thường bị intercept ngoài ý định, tăng chi phí và tạo phụ thuộc ngầm.

Không đăng ký thêm `DefaultAdvisorAutoProxyCreator` khi cấu hình `@EnableAspectJAutoProxy` hiện tại đã xử lý advisor bean; nhiều tầng cấu hình auto-proxy cần được quản lý thống nhất, tránh dựa vào thứ tự tạo bean ngẫu nhiên.

## 8. Cách phân định trách nhiệm trong một codebase Spring

Một cách tổ chức dễ kiểm soát:

```text
infrastructure/aop/
  TracingInterceptor
  OperationPointcut
  TracingAdvisorConfiguration

application/order/
  ConfirmOrderService
  OrderPolicy
  OrderAuditAspect
```

Tên package trên chỉ minh họa kiến trúc; Spring không buộc tên thư mục đó.

**Giao diện công khai của extension** nên là annotation/pointcut/config và chính sách lỗi rõ ràng. Module nghiệp vụ không cần biết class cụ thể của `MethodInvocation`, `ReflectiveMethodInvocation` hoặc tự gắn advice vào proxy. Ngược lại, extension hạ tầng không nên import `OrderStatus`, `PaymentResult` hay entity domain nếu không thực sự là adapter chuyên biệt.

Khi một concern giao thoa nhiều tầng, tách “cơ chế” và “chính sách”:

- **Cơ chế**: instrument interceptor dùng chung đo latency, thu outcome và gắn trace id.
- **Chính sách**: chọn operation nào quan trọng, tag nào phản ánh domain, cần báo động khi nào. Có thể cấu hình qua annotation/metadata, hoặc aspect riêng trong module application.

Ví dụ đếm `successful_order_confirmation` có thể là chỉ số nghiệp vụ, nhưng việc xuất metric đến backend monitoring vẫn là hạ tầng. Mỗi phần nên có chủ sở hữu và test riêng.

## 9. Kiểm thử ranh giới và điểm cắt

Một suite kiểm thử có ý nghĩa nên bao gồm:

1. **Tính đúng phạm vi:** method có pointcut match thì advice chạy; method không match thì không; thử cả proxy interface và target class nếu ứng dụng hỗ trợ hai kiểu.
2. **Kết quả và ngoại lệ:** interceptor có `proceed()` một lần cho đường đi bình thường, trả đúng kiểu, không vô tình nuốt exception; `finally` chạy khi lỗi.
3. **Điều kiện nghiệp vụ:** `@AfterReturning` chạy sau kết quả thành công, không chạy khi ném lỗi; không áp audit nhầm operation khác.
4. **Thứ tự advisor:** kiểm tra khi phối hợp với transaction/cache/security; đừng chỉ assert rằng bean Advisor tồn tại.
5. **Self-invocation:** có test thể hiện method nội bộ có được intercept hay không; đặt bất biến quan trọng trong service để không phụ thuộc proxy.
6. **Concurrency và trạng thái:** không rò dữ liệu request giữa các lời gọi đồng thời.

Kiểm thử một `Pointcut` có thể dùng trực tiếp `getClassFilter()`/`getMethodMatcher()`, nhưng để chứng minh interception thực tế, phải gọi **qua proxy hoặc Spring ApplicationContext**. Một unit test gọi thẳng `new WorkService().submit(...)` sẽ không chạy Spring AOP advice, và điều đó không chứng minh pointcut bị hỏng.

## 10. Các sai lầm thường gặp

| Biểu hiện | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| Dùng `@Around` cho mọi thứ | Chọn cơ chế mạnh hơn nhu cầu; dễ bỏ quên `proceed()` | Dùng `@Before`/`@AfterReturning`/`@AfterThrowing` nếu đủ |
| Hạ tầng import entity và trạng thái domain | Extension đang ôm chính sách nghiệp vụ | Tách policy/module application khỏi interceptor dùng chung |
| Quy tắc domain quan trọng nằm kín trong `@Aspect` | Luồng use case khó truy vết, bypass proxy có thể bỏ qua | Đưa invariant/transition vào service rõ ràng |
| `@AfterReturning` bị hiểu là sau DB commit | Thời điểm method return không đồng nghĩa transaction commit | Dùng cơ chế sau commit/outbox khi yêu cầu |
| Advice không chạy khi gọi `this.method()` | Self-invocation bypass Spring proxy | Thiết kế đường gọi qua bean/proxy hoặc giữ nghiệp vụ trực tiếp |
| Advisor áp dụng cho cả method ngoài dự kiến | Pointcut tên quá rộng | Ghép class filter + method matcher hoặc annotation cụ thể |
| Logging chạy ngoài transaction dù kỳ vọng ở trong | Order advisor hạ tầng và transaction advisor không phù hợp | Đặt precedence có chủ đích và trace đường gọi thực |
| Tạo nhiều proxy creator thủ công | Không hiểu container đang bật auto-proxy nào | Tận dụng cấu hình Spring đang có, xác minh chain |
| Lưu kết quả invocation vào field của interceptor singleton | Race condition và lộ dữ liệu giữa request | Dùng biến cục bộ hoặc context được quản lý |
| Gọi trực tiếp target trong test | Không đi qua proxy | Test tích hợp bằng context hoặc `ProxyFactory` |

## 11. Câu hỏi tự kiểm tra

**1. Nếu một mã dùng `@Aspect` để đo latency của tất cả service, đó là nghiệp vụ hay hạ tầng?**

Đó vẫn là **hạ tầng**, bởi trách nhiệm là quan sát hệ thống và không phụ thuộc vào thuật ngữ domain. Kiểu annotation không tự quyết định kiến trúc.

**2. Nếu cần cung cấp một thư viện đánh dấu method bằng annotation riêng để tự gắn interceptor, API nào của Spring phù hợp?**

Có thể tạo `Pointcut` theo metadata, ghép với `MethodInterceptor` thành `Advisor`, đăng ký advisor bean và để Spring auto-proxy áp dụng; cần kiểm tra cách kích hoạt auto-proxy của ứng dụng nhận thư viện.

**3. Tại sao audit nghiệp vụ trong `@AfterReturning` có thể chưa tương ứng với dữ liệu đã commit?**

Bởi advice chạy theo vòng đời **lời gọi method trên proxy** và thứ tự advisor. Transaction có thể hoàn tất ở lớp interceptor khác; method trả về bình thường chưa chắc đã bảo đảm commit tại thời điểm aspect chạy.

**4. Khi hạ tầng advisor order 100 và business aspect order 200 đều áp một lời gọi, thành phần nào vào chain trước?**

Advisor có precedence cao hơn (**số nhỏ hơn**) được đặt ngoài và đi vào trước theo thứ tự minh họa; phần sau `proceed()` của nó chạy sau advice bên trong. Luôn xác nhận thứ tự với những advisor khác trong ứng dụng.

**5. Nếu quy tắc thanh toán bắt buộc phải chạy kể cả khi code gọi trực tiếp `this.pay()`, có nên chỉ để quy tắc đó ở aspect không?**

Không. Spring AOP proxy thường không chặn self-invocation; bất biến quan trọng nên nằm trong luồng nghiệp vụ tường minh.

## Tài liệu chính thức

- [Spring Framework — AOP APIs](https://docs.spring.io/spring-framework/reference/core/aop-api.html).
- [Spring Framework — Advisor API](https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html).
- [Spring Framework — Advice API](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html).
- [Spring Framework — Using auto-proxy](https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html).
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html).
- [Spring Framework — Declaring Advice and Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html).
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).


</details>

- [Quay lại đầu trang](#back-to-top)
