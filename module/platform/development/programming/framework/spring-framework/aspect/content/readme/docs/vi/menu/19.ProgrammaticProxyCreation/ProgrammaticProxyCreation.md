<a id="back-to-top"></a>

# Tạo Proxy bằng API

## Menu
- [ProxyFactory để tạo proxy theo chương trình](#programmatic-proxy-creation-001)
- [Thêm target, interface, advice và advisor](#programmatic-proxy-creation-002)
- [Tạo proxy với class loader phù hợp](#programmatic-proxy-creation-003)
- [ProxyFactoryBean và việc khai báo qua Spring container](#programmatic-proxy-creation-004)
- [Phân biệt FactoryBean với đối tượng proxy mà nó cung cấp](#programmatic-proxy-creation-005)
- [interceptorNames và thứ tự cấu hình trong ProxyFactoryBean](#programmatic-proxy-creation-006)
- [Cấu hình proxy lớp hoặc proxy interface](#programmatic-proxy-creation-007)
- [Tạo proxy ngoài IoC và trách nhiệm quản lý phụ thuộc, vòng đời](#programmatic-proxy-creation-008)
- [Tránh bọc thêm proxy khi bean đã được tăng cường](#programmatic-proxy-creation-009)
- [Kiểm thử proxy tự tạo theo cùng hợp đồng với bean proxy tự động](#programmatic-proxy-creation-010)

## <a id="programmatic-proxy-creation-001">ProxyFactory để tạo proxy theo chương trình</a>

<details>
<summary>Xem chi tiết</summary>

# ProxyFactory để tạo proxy theo chương trình trong Spring AOP

## 1. Vì sao cần tạo Spring AOP proxy bằng mã Java?

Trong một ứng dụng Spring, nhiều proxy được tạo tự động khi ta bật `@EnableAspectJAutoProxy`, `@EnableTransactionManagement` hoặc cấu hình những thành phần tương ứng. Nhưng đôi khi mã hạ tầng cần **chủ động tạo một proxy cho đúng một đối tượng**: gắn interceptor trong bài thử, chọn pointcut lúc chạy, xây adapter cho đối tượng cũ hoặc thử nghiệm chuỗi advisor trước khi tích hợp vào ApplicationContext.

`org.springframework.aop.framework.ProxyFactory` giải quyết trường hợp đó. Ta đưa đối tượng đích (target) vào factory, thêm `Advice` hoặc `Advisor`, chọn kiểu proxy phù hợp, rồi gọi `getProxy()` để lấy đối tượng cần đưa cho caller sử dụng.

```text
BillingService target
        |
        v
new ProxyFactory(target)
        |
        +-- Pointcut + Advice -> Advisor
        +-- setProxyTargetClass(...) [nếu cần]
        |
        v
factory.getProxy()
        |
        v
BillingService proxy ---- lời gọi phù hợp ----> Interceptor ----> Target
```

Với `ProxyFactory`, **không bắt buộc khởi tạo Spring IoC container**. Đối tượng được tạo vẫn là **Spring AOP proxy** do Spring Framework triển khai. Nó tuân theo giới hạn proxy: advice chỉ chạy với lời gọi đi qua proxy, không tự chặn `this.method()` từ bên trong target.

Bài này tập trung vào cách Spring xây proxy theo chương trình, lựa chọn JDK/CGLIB, gắn advice/advisor, tương tác với pointcut, giới hạn vòng đời và cách kiểm thử. Các nguyên tắc AOP tổng quát chỉ được nhắc đến khi cần giải thích cách dùng API Spring.

## 2. Bốn bước tối thiểu

Ví dụ theo API Spring:

```java
BillingService target = new BillingServiceImpl();

ProxyFactory factory = new ProxyFactory(target);
factory.addAdvice((MethodInterceptor) invocation -> {
    System.out.println("before " + invocation.getMethod().getName());
    try {
        return invocation.proceed();
    } finally {
        System.out.println("after");
    }
});

BillingService proxy = (BillingService) factory.getProxy();
proxy.pay("A-01", 100);
```

Đây là **đoạn minh họa**, với `BillingService`, `BillingServiceImpl` và `pay` được định nghĩa ở ứng dụng. Bản runnable đầy đủ ở mục 6.

- `new ProxyFactory(target)`: cung cấp đối tượng đích sẽ nhận lời gọi.
- `addAdvice(...)`: thêm một advice trực tiếp; Spring tự gắn nó vào phạm vi áp dụng chung (trừ khi loại advice có ràng buộc riêng).
- `invocation.proceed()`: tiếp tục chuỗi interceptor và cuối cùng gọi target; nếu bỏ, target có thể không chạy.
- `getProxy()`: trả proxy. **Caller phải dùng proxy**, không phải biến `target` lúc đầu.

Với nhiều interceptor, thứ tự thêm vào factory và thứ tự advisor quyết định thứ tự bao quanh lời gọi. Interceptor ở ngoài sẽ chạy phần “before” trước, phần “after” sau interceptor ở trong.

### `ProxyFactory` kế thừa khả năng từ đâu?

| Thành phần | Vai trò |
| --- | --- |
| `ProxyFactory` | API tiện dụng để tạo proxy bằng code, cung cấp `getProxy()` |
| `ProxyCreatorSupport` / `AdvisedSupport` | Lưu target, interfaces, advisors và cấu hình liên quan |
| `ProxyConfig` | Các tùy chọn như `proxyTargetClass`, `exposeProxy`, `frozen` |
| `Advice` | Hành vi khi method được chặn, thường là `MethodInterceptor` |
| `Advisor` | Gắn `Advice` với pointcut/metadata áp dụng |
| `Pointcut` | Điều kiện chọn target class và method |
| `TargetSource` | Chiến lược cung cấp target; mặc định thường là một target cố định |

`ProxyFactory` là công cụ cấu hình **một proxy**. Nó không tự quét toàn bộ bean của ApplicationContext như auto-proxy creator. Nếu ứng dụng cần tự động proxy nhiều bean, sử dụng cấu hình Spring AOP ở mức container thường phù hợp hơn.

## 3. `addAdvice` khác `addAdvisor` thế nào?

Hai API đều gắn hành vi vào **chính proxy đang xây dựng**, nhưng phạm vi chọn method khác nhau:

| API | Tác dụng | Khi dùng |
| --- | --- | --- |
| `factory.addAdvice(advice)` | Thêm một `Advice` áp dụng rộng cho các method proxy có thể intercept | Tracing toàn proxy hoặc interceptor áp dụng chung |
| `factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, advice))` | Kết hợp `Advice` với `Pointcut` chỉ chọn method/class phù hợp | Audit riêng `pay`, đo `save*`, kiểm soát chính xác join point |

Ví dụ dưới đây chỉ gắn audit với tên method `pay`:

```java
NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
pointcut.setMappedName("pay");
factory.addAdvisor(new DefaultPointcutAdvisor(
        pointcut,
        (MethodInterceptor) invocation -> {
            System.out.println("audit:pay");
            return invocation.proceed();
        }));
```

Đây là fragment cần imports Spring AOP/AOP Alliance và biến `factory` đã cấu hình; project đầy đủ ở mục 6. Đặt tên advisor không đồng nghĩa đăng ký một `@Aspect` bean. Bài tiếp theo về **target, interface, advice và advisor** giải thích từng bước, các interface API và cách test method được/không được chọn.

## 4. Chọn JDK dynamic proxy hay proxy dựa trên lớp

`ProxyFactory` có thể tạo hai kiểu proxy:

- **JDK dynamic proxy** công khai các interface cấu hình hoặc được nhận diện từ target. Caller nên làm việc qua interface; không cast JDK proxy về class implementation.
- **CGLIB class-based proxy** tạo subclass của target khi `factory.setProxyTargetClass(true)`; caller có thể dùng kiểu concrete nếu class đủ điều kiện subclass.

```java
ProxyFactory factory = new ProxyFactory(target);
factory.setProxyTargetClass(true);
Object classProxy = factory.getProxy();
```

Fragment cần biến `target` đã có. Chế độ subclass không intercept được `private` hoặc `final` method; `final` class không thể được subclass. Cả JDK và class proxy đều không tự chặn lời gọi nội bộ `this.method()` trên target. Bài **Cấu hình proxy lớp hoặc proxy interface** phân tích lựa chọn API theo nhu cầu caller, giới hạn proxy và ví dụ chạy đối chứng.

## 5. Tạo proxy và quản lý target đúng cách

### Đường đi qua proxy

```text
Caller:
  BillingService service = factory.getProxy();
  service.pay("A", 100);
       |
       v
JDK/CGLIB Spring proxy
       |
       +-- Advisor 1 có khớp? -> Interceptor 1
       +-- Advisor 2 có khớp? -> Interceptor 2
       |
       v
BillingServiceImpl.pay(...)
```

Nếu caller giữ `target` và gọi `target.pay(...)`, advice của proxy **không có cơ hội chạy**. Cần đưa proxy cho thành phần sử dụng; trong dependency injection phải inject **proxy bean**, không phải một bean target khác vô tình được đăng ký song song.

### `getProxy()` trả về điều gì?

`getProxy()` trả một đối tượng được tạo theo cấu hình đang có của `ProxyFactory`. Không nên giả định class name ổn định hoặc luôn có thể ép sang implementation. Cách xác nhận là dùng:

```java
AopUtils.isAopProxy(proxy);
AopUtils.isJdkDynamicProxy(proxy);
AopUtils.isCglibProxy(proxy);
```

Import `org.springframework.aop.support.AopUtils`. Đây là các phép kiểm tra **loại proxy do Spring tạo**, hữu ích trong test hoặc chẩn đoán; không dùng tên class sinh ra làm một phần hợp đồng ứng dụng.

### Có thể thay đổi cấu hình sau khi tạo proxy không?

`ProxyFactory` hỗ trợ bổ sung/xóa advisor và thiết lập nhiều thuộc tính. Proxy Spring thường cũng có giao diện quản lý `Advised`; tuy nhiên việc sửa chuỗi advisor sau khi proxy đã được chia sẻ làm thay đổi hành vi đang chạy và có thể gây khó kiểm soát. Cấu hình đầy đủ trước `getProxy()` là cách đơn giản nhất.

`setFrozen(true)` khóa các thay đổi cấu hình advisor nhất định trên proxy. Không dùng frozen như cơ chế bảo mật tuyệt đối, và không thử thay đổi advisor trên proxy trong khi nhiều thread đang gọi nếu chưa có lý do/quy trình đồng bộ rõ ràng.

### Một target hay một chuỗi target?

Với `new ProxyFactory(target)`, factory bọc một target đã có. Nếu cần chọn target theo request hay tạo target mỗi lần, Spring có `TargetSource` riêng. Đó là cơ chế nâng cao, có hệ quả với vòng đời, thread safety và giải phóng target; không cần cho proxy thông thường.

## 6. Ví dụ độc lập: ProxyFactory, pointcut và hai kiểu proxy

Ví dụ này chạy với **Java 17+, Spring Framework 6.2.8, Maven và JUnit 5**. Nó tạo hai proxy trên hai target độc lập, mỗi proxy có interceptor chỉ áp dụng cho method `pay`. Method `status()` phải đi thẳng tới target. Lỗi trong `pay` phải được ghi nhận nhưng giữ nguyên exception gốc.

Cấu trúc:

```text
proxy-factory-demo/
├── pom.xml
└── src/
    ├── main/java/example/ProxyFactoryDemo.java
    └── test/java/example/ProxyFactoryDemoTest.java
```

### 6.1. File `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>proxy-factory-demo</artifactId>
  <version>1.0.0</version>

  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-aop</artifactId>
      <version>6.2.8</version>
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
        <version>3.5.0</version>
      </plugin>
    </plugins>
  </build>
</project>
```

### 6.2. File `src/main/java/example/ProxyFactoryDemo.java`

```java
package example;

import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class ProxyFactoryDemo {

    public interface BillingService {
        String pay(String orderId, int cents);
        String status();
    }

    public static class Recorder {
        private final List<String> events = new ArrayList<>();

        public void add(String event) {
            events.add(event);
        }

        public List<String> snapshot() {
            return List.copyOf(events);
        }
    }

    // Không final để ví dụ CGLIB proxy có thể tạo subclass.
    public static class BillingServiceImpl implements BillingService {
        private final Recorder recorder;

        public BillingServiceImpl(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public String pay(String orderId, int cents) {
            if (cents <= 0) {
                recorder.add("target:reject:" + orderId);
                throw new IllegalArgumentException("cents must be positive");
            }
            recorder.add("target:pay:" + orderId + ":" + cents);
            return "PAID:" + orderId + ":" + cents;
        }

        @Override
        public String status() {
            recorder.add("target:status");
            return "READY";
        }
    }

    public record Scenario(BillingService service, Recorder recorder) {}

    private static ProxyFactory configuredFactory(Recorder recorder) {
        BillingServiceImpl target = new BillingServiceImpl(recorder);
        ProxyFactory factory = new ProxyFactory(target);

        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedNames("pay");

        MethodInterceptor trace = invocation -> {
            String name = invocation.getMethod().getName();
            recorder.add("before:" + name);
            try {
                Object value = invocation.proceed();
                recorder.add("after:" + name);
                return value;
            } catch (Throwable error) {
                recorder.add("error:" + name);
                throw error;
            }
        };

        factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, trace));
        return factory;
    }

    public static Scenario jdkScenario() {
        Recorder recorder = new Recorder();
        ProxyFactory factory = configuredFactory(recorder);
        factory.setInterfaces(BillingService.class);
        factory.setProxyTargetClass(false);
        BillingService proxy = (BillingService) factory.getProxy();
        return new Scenario(proxy, recorder);
    }

    public static Scenario classScenario() {
        Recorder recorder = new Recorder();
        ProxyFactory factory = configuredFactory(recorder);
        factory.setProxyTargetClass(true);
        BillingServiceImpl proxy = (BillingServiceImpl) factory.getProxy();
        return new Scenario(proxy, recorder);
    }

    private static void run(String label, Scenario scenario) {
        BillingService service = scenario.service();

        System.out.println(label + ".jdk="
                + AopUtils.isJdkDynamicProxy(service));
        System.out.println(label + ".cglib="
                + AopUtils.isCglibProxy(service));
        System.out.println(label + ".result=" + service.pay("A-1", 150));
        System.out.println(label + ".status=" + service.status());

        try {
            service.pay("B-2", -1);
        } catch (IllegalArgumentException error) {
            System.out.println(label + ".error=" + error.getMessage());
        }
        System.out.println(label + ".events="
                + scenario.recorder().snapshot());
    }

    public static void main(String[] args) {
        run("jdk", jdkScenario());
        run("class", classScenario());
    }
}
```

Dùng `record Scenario` để trả cùng lúc proxy và recorder phục vụ kiểm thử; ở ứng dụng thật, nên quản lý chúng qua dependency injection hoặc thành phần hạ tầng phù hợp.

**Bốn điểm cần quan sát trong mã:**

1. `configuredFactory(...)` đặt **target** và **advisor** một lần. Hai scenario chỉ khác chính sách tạo proxy.
2. `NameMatchMethodPointcut` chọn tên `pay`. Vì vậy `status()` không sinh `before:status`.
3. `MethodInterceptor.invoke(...)` dùng `proceed()` để bảo toàn kết quả của target và dùng `catch` để ghi nhận exception trước khi ném lại.
4. `jdkScenario()` chỉ trả interface `BillingService`, còn `classScenario()` chứng minh có thể ép sang `BillingServiceImpl` khi tạo class-based proxy.

### 6.3. Chạy và đối chiếu output

Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.ProxyFactoryDemo
```

Stdout của logic ví dụ (không tính log môi trường Maven nếu có):

```text
jdk.jdk=true
jdk.cglib=false
jdk.result=PAID:A-1:150
jdk.status=READY
jdk.error=cents must be positive
jdk.events=[before:pay, target:pay:A-1:150, after:pay, target:status, before:pay, target:reject:B-2, error:pay]
class.jdk=false
class.cglib=true
class.result=PAID:A-1:150
class.status=READY
class.error=cents must be positive
class.events=[before:pay, target:pay:A-1:150, after:pay, target:status, before:pay, target:reject:B-2, error:pay]
```

Bằng chứng:

- `AopUtils.isJdkDynamicProxy` và `isCglibProxy` chứng minh hai cơ chế proxy khác nhau.
- Kết quả `PAID:A-1:150` chứng minh interceptor gọi `proceed()` và không thay giá trị trả về.
- Hai mảng sự kiện **giống nhau** vì cả hai proxy được gắn advisor cùng pointcut. `status` không bị advice chặn.
- Lỗi `cents must be positive` vẫn đến caller. Spring proxy không được dùng để nuốt exception một cách ngầm định.

Lưu ý đây là chương trình Java chạy tuần tự. `Recorder` dùng `ArrayList` để minh họa; trong ứng dụng có nhiều thread phải chọn cấu trúc lưu trữ và đồng bộ phù hợp.

## 7. Kiểm thử Spring ProxyFactory bằng JUnit 5

`src/test/java/example/ProxyFactoryDemoTest.java`:

```java
package example;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyFactoryDemoTest {

    private void assertInterception(ProxyFactoryDemo.Scenario scenario) {
        var service = scenario.service();

        assertTrue(AopUtils.isAopProxy(service));
        assertEquals("PAID:A-1:150", service.pay("A-1", 150));
        assertEquals("READY", service.status());

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class,
                        () -> service.pay("B-2", -1));
        assertEquals("cents must be positive", failure.getMessage());

        assertEquals(List.of(
                "before:pay",
                "target:pay:A-1:150",
                "after:pay",
                "target:status",
                "before:pay",
                "target:reject:B-2",
                "error:pay"
        ), scenario.recorder().snapshot());
    }

    @Test
    void jdkProxyOnlyExposesInterfaceAndAppliesAdvisor() {
        var scenario = ProxyFactoryDemo.jdkScenario();
        assertTrue(AopUtils.isJdkDynamicProxy(scenario.service()));
        assertFalse(AopUtils.isCglibProxy(scenario.service()));
        assertFalse(scenario.service()
                instanceof ProxyFactoryDemo.BillingServiceImpl);
        assertInterception(scenario);
    }

    @Test
    void classProxyExposesImplementationAndAppliesAdvisor() {
        var scenario = ProxyFactoryDemo.classScenario();
        assertTrue(AopUtils.isCglibProxy(scenario.service()));
        assertFalse(AopUtils.isJdkDynamicProxy(scenario.service()));
        assertTrue(scenario.service()
                instanceof ProxyFactoryDemo.BillingServiceImpl);
        assertInterception(scenario);
    }

    @Test
    void rawTargetBypassesAdvisor() {
        var recorder = new ProxyFactoryDemo.Recorder();
        var target = new ProxyFactoryDemo.BillingServiceImpl(recorder);

        assertEquals("PAID:X:200", target.pay("X", 200));
        assertEquals(List.of("target:pay:X:200"), recorder.snapshot());
        assertFalse(AopUtils.isAopProxy(target));
    }
}
```

Chạy:

```bash
mvn -q test
```

Bảng ý nghĩa kiểm thử:

| Test | Điều đã được chứng minh |
| --- | --- |
| `jdkProxyOnlyExposesInterfaceAndAppliesAdvisor` | Proxy JDK, advice chỉ chạy khi `pay` khớp, bảo toàn kết quả và exception |
| `classProxyExposesImplementationAndAppliesAdvisor` | CGLIB proxy vẫn thực hiện advisor giống nhau, có thể ép sang implementation |
| `rawTargetBypassesAdvisor` | Gọi target trực tiếp không kích hoạt advice, dù method trùng chữ ký |

Test không dựa vào tên class sinh ra như `$Proxy12` hoặc `$$SpringCGLIB$$`, vì đó không phải hợp đồng ổn định. `AopUtils` và sự kiện hành vi đáng tin hơn.

## 8. Gắn nhiều advisor và ý nghĩa của thứ tự

Một proxy có thể chứa nhiều advisor:

```java
factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, first));
factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, second));
```

Đây là trích đoạn cần `factory`, `pointcut`, `first`, `second` đã được khai báo hợp lệ. Với hai `MethodInterceptor` có cùng pointcut, cách quan sát thường là:

```text
first:before
second:before
target
second:after
first:after
```

Thứ tự trên giả định `first` được đặt ngoài `second` như thứ tự thêm advisor ở đoạn code và mỗi interceptor đều gọi `proceed()` một lần. Nếu interceptor thứ nhất từ chối/ném exception **trước** `proceed()`, các interceptor ở trong và target không chạy.

`ProxyFactory` hỗ trợ nhiều advisor, có thể thêm tại vị trí cụ thể; `Advised` cho phép quan sát/điều chỉnh chuỗi khi proxy hỗ trợ. Tuy nhiên, đừng phụ thuộc vào một danh sách interceptor được cập nhật liên tục trong lúc nhiều luồng đang chạy. Nên cấu hình hoàn chỉnh rồi công bố proxy.

Nếu cần thứ tự vững chắc giữa transaction, security, metrics và audit trong ứng dụng Spring lớn, hãy thiết kế thành một hợp đồng được test, đồng thời hiểu rằng nhiều advisor có thể đến từ auto-proxy infrastructure chứ không chỉ factory do bạn tạo thủ công.

## 9. Khi nào nên dùng ProxyFactory, ProxyFactoryBean hay AspectJProxyFactory?

| Công cụ Spring | Công dụng | Lựa chọn điển hình |
| --- | --- | --- |
| `ProxyFactory` | Tạo Spring proxy theo chương trình bằng target và advice/advisor | Test, adapter, hạ tầng tạo proxy chủ động |
| `ProxyFactoryBean` | Tạo proxy thông qua Spring `FactoryBean` và cấu hình bean factory | Ứng dụng IoC cần điều khiển advisor/target bằng bean configuration |
| `AspectJProxyFactory` | Tạo proxy theo chương trình từ các object/class `@Aspect` | Muốn gắn một hoặc nhiều `@Aspect` mà không dùng auto-proxy toàn container |
| `@EnableAspectJAutoProxy` / `<aop:aspectj-autoproxy/>` | Spring tự tìm aspect bean và tạo proxy cho các bean phù hợp | Nhiều service cùng dùng một chính sách `@Aspect` |
| Spring `@Transactional` / `@Async` infrastructure | Tạo advisor/proxy theo tính năng framework | Giao dịch hoặc bất đồng bộ trên Spring beans |

Ví dụ `AspectJProxyFactory` (trích đoạn; chỉ chạy khi `TraceAspect` là aspect hợp lệ):

```java
AspectJProxyFactory factory = new AspectJProxyFactory(target);
factory.addAspect(traceAspect);
Object proxy = factory.getProxy();
```

`AspectJProxyFactory` thuộc `org.springframework.aop.aspectj.annotation`, khác `ProxyFactory` trực tiếp. Nó đọc `@Aspect` thay vì bạn tự cung cấp một `MethodInterceptor` và `DefaultPointcutAdvisor`. Nếu không có `aspectjweaver` khi dùng biểu thức AspectJ, cần bổ sung dependency tương ứng.

**Đừng nhầm Spring AOP proxy với AspectJ weaving.** Kể cả `AspectJProxyFactory`, đây vẫn là cách tạo proxy do Spring cung cấp; không tự mở rộng sang constructor, field access hoặc self-invocation.

## 10. Tích hợp một proxy do ProxyFactory tạo vào Spring container

Ví dụ `ProxyFactory` thuần chạy không cần Spring IoC. Nhưng trong ứng dụng Spring, nếu muốn thành phần khác inject cùng proxy, có thể khai báo `@Bean` trả về **proxy**:

```java
@Configuration(proxyBeanMethods = false)
class BillingConfiguration {
    @Bean
    BillingService billingService() {
        Recorder recorder = new Recorder();
        BillingServiceImpl target = new BillingServiceImpl(recorder);

        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(BillingService.class);
        factory.addAdvice((MethodInterceptor) invocation ->
                invocation.proceed());
        return (BillingService) factory.getProxy();
    }
}
```

Đây là **đoạn minh họa**, cần các lớp `BillingService`, `BillingServiceImpl`, `Recorder` từ mục 6 và các import Spring Context/AOP phù hợp; phải bổ sung dependency `spring-context` nếu muốn chạy trong ApplicationContext. Trong sản phẩm thực tế, recorder và target nên được quản lý theo bean lifecycle/DI có chủ đích, không tạo object không quan sát được trong method `@Bean`.

Nếu cùng target được đăng ký như một bean riêng và bị inject trực tiếp ở nơi khác, những nơi đó có thể bypass proxy. Khi trộn proxy tự tạo với Spring auto-proxy, có thể phát sinh nhiều lớp proxy lồng nhau và advice chạy lặp, do đó cần kiểm tra `Advised`/`AopUtils` và chuỗi thực tế.

Với cấu hình phổ biến, Spring khuyến nghị để IoC quản lý việc tạo proxy/advisor; `ProxyFactory` thuận tiện nhất cho những tình huống **chủ động** tạo proxy mà không muốn thay đổi cơ chế proxying toàn ứng dụng.

## 11. Các lỗi hay gặp khi sử dụng ProxyFactory

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Interceptor không chạy | Caller vẫn giữ target, không gọi proxy | Trả/inject đối tượng từ `factory.getProxy()` |
| `ClassCastException` khi ép về class implementation | JDK proxy chỉ triển khai interface | Giữ tham chiếu qua interface hoặc dùng class-based proxy có lý do |
| `status()` bị audit dù chỉ cần `pay()` | Dùng `addAdvice` với phạm vi rộng | Gắn `DefaultPointcutAdvisor` với pointcut phù hợp |
| Một method tự gọi method khác không kích hoạt advice | Self-invocation bypass proxy | Tách điểm vào qua proxy/bean khác nếu cần |
| Advice chạy đôi | Chồng `ProxyFactory` và auto-proxy, hoặc nhiều advisor trùng | Kiểm tra chain, tránh đăng ký trùng |
| Target không chạy / caller nhận `null` | `MethodInterceptor` quên `proceed()` hoặc trả sai kết quả | Giữ nguyên hợp đồng return/exception |
| `final` method/class không được advice qua subclass | Giới hạn CGLIB | Chọn interface-based khi phù hợp, hoặc đổi thiết kế |
| Advice gây race condition | Lưu state mỗi lời gọi vào field chung | Dùng biến cục bộ hoặc cấu trúc thread-safe |
| Code test dựa vào tên proxy class | Tên proxy được tạo động | Dùng `AopUtils` và test hành vi |
| Proxy không nhận được dependencies/lifecycle dự kiến | Tạo target ngoài Spring IoC | Tự quản lý đầy đủ hoặc để container tạo bean/proxy |
| Thay advisor sau khi proxy đã chia sẻ gây hành vi bất ngờ | Chuỗi interception thay đổi lúc chạy | Xây cấu hình xong trước khi công bố proxy |

### Lưu ý đặc biệt về trạng thái

Một `MethodInterceptor` hoặc `Advice` có thể được dùng lại qua nhiều lời gọi và nhiều luồng. Đừng lưu `startTime`, `currentUser`, `traceId` của từng invocation vào field mutable của advice singleton. Dùng biến cục bộ trong `invoke(...)` và xử lý context bất đồng bộ đúng cơ chế nếu cần.

### Một số tùy chọn nâng cao

- `setExposeProxy(true)` có thể cho phép gọi `AopContext.currentProxy()` trong ngữ cảnh interception phù hợp, nhưng phụ thuộc vào `ThreadLocal` và gắn chặt code nghiệp vụ với Spring AOP. Ưu tiên thiết kế qua bean boundary thay cho dựa vào proxy hiện tại.
- `setFrozen(true)` khóa việc thay đổi cấu hình advisor của proxy theo API hỗ trợ. Nó không biến đối tượng target thành immutable.
- `setOptimize(true)` là tùy chọn tối ưu chuyên sâu đối với cơ chế proxy dựa trên lớp; không nên bật chỉ vì muốn “nhanh hơn” khi chưa đo và hiểu hành vi.
- `getProxy(ClassLoader)` cho phép cung cấp class loader thích hợp trong môi trường đặc biệt. Trừ khi có nhu cầu plugin/module/classloader rõ ràng, `getProxy()` thường đủ.
- `TargetSource` phù hợp nếu target không phải một object cố định. Cần quản lý vòng đời và khả năng đồng thời riêng.

## 12. Bài tập thực hành

1. Chạy `mvn -q test` và xác nhận cả proxy JDK, CGLIB và raw target cho kết quả như dự kiến.
2. Thay `pointcut.setMappedNames("pay")` thành `pointcut.setMappedNames("pay", "status")`. Dự đoán test nào thất bại và giải thích vì sao `status()` bây giờ có `before:status`/`after:status`.
3. Thay `factory.addAdvisor(...)` bằng `factory.addAdvice(trace)`. Quan sát phạm vi chặn mở rộng và sửa test để phản ánh hợp đồng mới.
4. Thêm interceptor thứ hai chỉ áp dụng cho `pay` và ghi sự kiện `second:before`/`second:after`. Thử đổi thứ tự advisor và so sánh chuỗi sự kiện.
5. Giữ cấu hình JDK nhưng cố ép proxy thành `BillingServiceImpl` trong một test `assertThrows(ClassCastException.class, ...)`. Giải thích vì sao interface mới là hợp đồng mà proxy JDK triển khai.
6. Thêm `BillingServiceImpl.payTwice(...)` gọi `this.pay(...)` và tạo pointcut phù hợp. Xác định khi nào lời gọi nội bộ không đi qua proxy, rồi thiết kế lại để caller gọi thông qua một bean boundary.
7. Tạo cấu hình `@Bean` trả proxy và một service khác inject `BillingService`. Kiểm tra toàn bộ lời gọi đi qua proxy thay vì nhận target gốc.

### Tiêu chí tự kiểm tra

Bạn cần giải thích được lý do chọn `ProxyFactory`, từng bước từ target tới proxy, khác biệt `addAdvice`/`addAdvisor`, điểm cần quan sát khi dùng JDK/CGLIB, ý nghĩa `proceed()`, cách Spring AOP giới hạn lời gọi có thể chặn và cách test chứng minh proxy thực sự được sử dụng.

### Tài liệu Spring chính thức

- Creating AOP Proxies Programmatically with the ProxyFactory: https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html
- Proxying Mechanisms và self-invocation: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Pointcut API: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html
- Spring Advisor API: https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html
- ProxyFactoryBean (khi cần tích hợp với IoC): https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html
- AspectJProxyFactory (chương trình tạo proxy từ `@Aspect`): https://docs.spring.io/spring-framework/reference/core/aop/aspectj-programmatic.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="programmatic-proxy-creation-002">Thêm target, interface, advice và advisor</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm target, interface, advice và advisor khi tạo Spring AOP proxy bằng API

## 1. Mục tiêu của bài

`ProxyFactory` trong `org.springframework.aop.framework` cho phép tạo một **Spring AOP proxy bằng Java API**. Bốn bước quan trọng là:

1. Gắn **target** — đối tượng thực sự xử lý nghiệp vụ — vào factory.
2. Khai báo **interface** mà proxy sẽ công khai cho mã gọi.
3. Thêm **advice** chạy trên lời gọi đi qua proxy.
4. Thêm **advisor** để gắn advice với một pointcut giới hạn lớp/phương thức áp dụng.

Ví dụ muốn mọi lần gọi qua `OrderService` được trace, nhưng chỉ `saveOrder()` được audit:

```text
Application
     |
     v
Spring AOP proxy (OrderService)
     |
     +-- Advice trace: mọi phương thức phù hợp của proxy
     |
     +-- Advisor audit: Pointcut tên "save*" + Advice audit
     |
     v
Target OrderServiceImpl
```

Proxy xử lý chain các interceptor/advice rồi chuyển lời gọi đến target. Chỉ tạo `Advice` hoặc `Advisor` bằng `new` là chưa đủ: **nó phải được thêm vào cấu hình proxy, và mã gọi phải sử dụng proxy**. Đây là cơ chế cụ thể của Spring AOP; bài không đi vào lý thuyết Aspect tổng quát.

## 2. Các kiểu Spring cần biết

| Thành phần | Spring API | Vai trò thực tế |
| --- | --- | --- |
| Factory | `org.springframework.aop.framework.ProxyFactory` | Thu thập cấu hình và tạo proxy thông qua `getProxy()` |
| Target | Đối tượng nghiệp vụ bất kỳ | Nhận lời gọi sau khi chain advice cho phép đi tiếp |
| Interface | Java interface nghiệp vụ | Hợp đồng mà proxy JDK triển khai và mã gọi sử dụng |
| Advice | `org.aopalliance.aop.Advice` | Giao diện gốc cho nhiều kiểu Spring AOP advice |
| Around advice | `org.aopalliance.intercept.MethodInterceptor` | Chạy trước/sau `invocation.proceed()`, có thể kiểm soát việc gọi target |
| Before advice | `org.springframework.aop.MethodBeforeAdvice` | Chạy trước lời gọi target |
| Advisor | `org.springframework.aop.Advisor` | Đóng gói một advice trong cấu hình Spring AOP |
| Pointcut advisor | `org.springframework.aop.support.DefaultPointcutAdvisor` | Ghép `Pointcut` với một advice để chọn phương thức |
| Name-based pointcut | `org.springframework.aop.support.NameMatchMethodPointcut` | Chọn theo tên method, ví dụ `save*` |
| Proxy introspection | `org.springframework.aop.support.AopUtils`, `org.springframework.aop.framework.Advised` | Xác định proxy và kiểm tra advisor đã được cấu hình |

`ProxyFactory` kế thừa các phương thức cấu hình từ `AdvisedSupport`/`ProxyConfig`. Khi đọc tài liệu, các API như `addAdvice`, `addAdvisor`, `setTarget` có thể được liệt kê ở lớp cha thay vì chỉ dưới trang Javadoc của `ProxyFactory`.

## 3. Gắn target vào ProxyFactory

Target là đối tượng đang có phương thức nghiệp vụ:

```java
OrderServiceImpl target = new OrderServiceImpl();

ProxyFactory factory = new ProxyFactory();
factory.setTarget(target);
```

Đây là đoạn mã minh họa: các lớp nghiệp vụ được định nghĩa đầy đủ ở phần 7. `setTarget(target)` cấu hình một target cố định cho proxy. Spring AOP proxy tiếp nhận lời gọi và cuối cùng chuyển đến đối tượng đó (nếu advice không chặn hoặc thay đổi lời gọi).

Spring cũng cung cấp constructor tiện lợi:

```java
ProxyFactory factory = new ProxyFactory(new OrderServiceImpl());
```

Khi truyền target qua constructor, Spring có thể xác định các interface nghiệp vụ mà đối tượng triển khai để cấu hình proxy. Cách `new ProxyFactory(); setTarget(...); addInterface(...)` phù hợp khi muốn trình bày rõ từng bước hoặc cần chỉ định interface cụ thể.

**Không nhầm proxy với target:**

```java
OrderService target = new OrderServiceImpl();
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvice(traceAdvice);
OrderService proxy = (OrderService) factory.getProxy();

// Đi qua proxy nên có cơ hội chạy advice:
proxy.saveOrder();

// Gọi thẳng target, bỏ qua Spring AOP proxy:
target.saveOrder();
```

Các đoạn ở phần 3–6 là **fragment API** dùng biến và kiểu được khai báo ở ví dụ đầy đủ. Đừng chạy riêng lẻ bằng `javac`.

Target không bắt buộc là Spring bean nếu bạn đang dùng `ProxyFactory` trực tiếp. Trong một ứng dụng IoC, Spring cũng có thể tạo và quản lý proxy cho các bean theo cơ chế auto-proxy; đó là một đường cấu hình khác.

## 4. Thêm interface vào proxy

Phần lớn Java service có interface nghiệp vụ. `addInterface(Class<?>)` đăng ký thêm interface mà proxy sẽ công khai; `setInterfaces(Class<?>...)` thiết lập lại tập interface cấu hình:

```java
ProxyFactory factory = new ProxyFactory();
factory.setTarget(new OrderServiceImpl());
factory.addInterface(OrderService.class);

// Nếu cần đặt trọn tập interface một lần:
// factory.setInterfaces(OrderService.class, Auditable.class);
```

Ở ví dụ trên, `OrderServiceImpl` phải đáp ứng contract `OrderService`; thêm interface không tự tạo code triển khai nghiệp vụ tương ứng. Khi muốn thêm *hành vi và interface mới* cho proxy (introduction), cần introduction advisor chuyên biệt, chứ `addInterface(...)` kết hợp với target không hỗ trợ interface đó không thay thế được implementation.

Khi dùng JDK dynamic proxy:

- Mã gọi thường nhận đối tượng theo **interface**, chẳng hạn `OrderService proxy`.
- Các phương thức chỉ tồn tại trên concrete class, không được khai báo trên interface proxy, không thể gọi thông qua biến kiểu interface đó.
- Có thể kiểm tra bằng `AopUtils.isJdkDynamicProxy(proxy)`.
- Thêm interface trước `getProxy()`. Một proxy đã tạo không tự thay đổi tập interface chỉ vì factory được cấu hình lại sau đó.

**Phân biệt nhanh:**

| API | Ý nghĩa |
| --- | --- |
| `factory.addInterface(OrderService.class)` | Thêm một interface vào tập interface proxy đang cấu hình |
| `factory.setInterfaces(A.class, B.class)` | Đặt tập interface proxy thành tập được chỉ định |
| `factory.setProxyTargetClass(true)` | Yêu cầu proxy dựa trên lớp đích (CGLIB), kể cả khi target có interface |
| `factory.getProxy()` | Tạo ra object proxy theo cấu hình hiện tại |

Nếu target không triển khai interface và không có interface proxy thích hợp, Spring AOP có thể dùng class-based proxy qua CGLIB. Khi ứng dụng buộc proxy theo class, nên biết rằng lớp `final` không thể tạo subclass proxy và method `final`/`private` không thể được CGLIB advice theo cơ chế override.

## 5. Thêm advice trực tiếp

Cách ngắn nhất khi muốn hành vi áp dụng rộng là `factory.addAdvice(advice)`. Ví dụ `MethodInterceptor`:

```java
MethodInterceptor traceAdvice = invocation -> {
    String method = invocation.getMethod().getName();
    System.out.println("[trace-before] " + method);

    try {
        return invocation.proceed();
    } finally {
        System.out.println("[trace-after] " + method);
    }
};

factory.addAdvice(traceAdvice);
```

`invocation.proceed()` đi tiếp trong interceptor chain để cuối cùng gọi target. `finally` khiến dòng after được ghi cả khi downstream ném exception. Khi dùng `MethodInterceptor` cho audit/metrics, phải quyết định rõ thời điểm và điều kiện trả kết quả, ném lỗi hoặc bỏ qua `proceed()`.

**Phạm vi mặc định của `addAdvice`:** Spring bọc advice vào một `DefaultPointcutAdvisor` với `Pointcut.TRUE` (pointcut khớp mọi lớp/phương thức). Vì vậy advice sẽ được xét cho mọi phương thức mà proxy có thể intercept. Đây là lựa chọn phù hợp với tracing chung, nhưng có thể quá rộng với kiểm soát truy cập, audit nghiệp vụ, hoặc một nhóm method cụ thể.

Một số kiểu advice:

- `MethodBeforeAdvice`: trước method, không có khả năng chủ động gọi `proceed()`.
- `AfterReturningAdvice`: sau khi method kết thúc bình thường.
- `ThrowsAdvice`: xử lý trường hợp lời gọi ném exception theo các callback tương ứng.
- `MethodInterceptor`: around advice, có quyền thực hiện logic trước/sau và quyết định gọi `proceed()`.

Cả các kiểu advice hợp lệ này đều có thể được Spring chuyển thành interceptor chain thích hợp. Trong các tình huống cần pointcut, nên dùng `addAdvisor` thay vì tự kiểm tra tên method bên trong advice.

## 6. Thêm advisor có pointcut giới hạn

`factory.addAdvisor(advisor)` gắn một Spring `Advisor` đã được tạo sẵn. Với `DefaultPointcutAdvisor`, cấu hình thường gồm một pointcut và một advice:

```java
NameMatchMethodPointcut savePointcut = new NameMatchMethodPointcut();
savePointcut.setMappedName("save*");

MethodBeforeAdvice auditAdvice = (method, args, target) ->
        System.out.println("[audit] " + method.getName());

DefaultPointcutAdvisor saveAdvisor =
        new DefaultPointcutAdvisor(savePointcut, auditAdvice);

factory.addAdvisor(saveAdvisor);
```

Với mẫu `save*`, các method như `saveOrder`, `saveCustomer` phù hợp, còn `findOrder` không phù hợp. Dấu `*` là wildcard đơn giản của Spring, không phải regex; tên method được so khớp nên overload cùng tên cũng có thể cùng được chọn.

Khi một lời gọi khớp pointcut, Spring thêm advice của advisor vào chain phù hợp. Một lời gọi **không khớp** advisor này vẫn có thể chạy những advice chung hoặc advisor khác đã được thêm vào factory.

### 6.1. addAdvice so với addAdvisor

| Tiêu chí | `addAdvice(advice)` | `addAdvisor(advisor)` |
| --- | --- | --- |
| Đầu vào | Một đối tượng `Advice` | Một đối tượng `Advisor` |
| Pointcut | Spring bọc với `Pointcut.TRUE` | Dùng pointcut của advisor (hoặc cấu hình advisor khác) |
| Dùng khi | Trace/metrics chung trên các lời gọi proxy | Audit chỉ `save*`, logging chỉ lớp/phương thức chọn lọc |
| Kết quả inspect | `Advised.getAdvisors()` vẫn có advisor được Spring tạo | `Advised.getAdvisors()` có advisor đã truyền vào |
| Sai lầm dễ gặp | Áp dụng rộng ngoài ý muốn | Pointcut không khớp, dẫn đến advice không chạy |

**Lưu ý:** `addAdvice` không phải cách bỏ qua mô hình advisor; Spring vẫn dùng advisor để tổ chức advice trong chain. Hai cách khác nhau chủ yếu ở **cách bạn chỉ định phạm vi áp dụng**.

## 7. Ví dụ Java 17+ hoàn chỉnh: target → interface → advice → advisor → proxy

Dự án sử dụng Spring AOP API và Gradle. Không yêu cầu Spring Boot, `ApplicationContext`, `@Aspect` hoặc AspectJ weaving để chạy ví dụ này.

Cấu trúc thư mục:

```text
proxy-api-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/ProxyApiDemo.java
```

Tệp `settings.gradle`:

```groovy
rootProject.name = 'proxy-api-demo'
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

dependencies {
    implementation 'org.springframework:spring-aop:6.2.19'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.ProxyApiDemo'
}
```

Spring 6.2.x hỗ trợ Java 17 trở lên. Với dự án Spring Boot, hãy để BOM/dependency management của Boot quản lý phiên bản Spring AOP, thay vì chép dependency version độc lập từ ví dụ.

Tệp `src/main/java/demo/ProxyApiDemo.java`:

```java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class ProxyApiDemo {

    public interface OrderService {
        void saveOrder(long orderId);
        String findOrder(long orderId);
    }

    public static class OrderServiceImpl implements OrderService {
        @Override
        public void saveOrder(long orderId) {
            System.out.println("[target] saveOrder: " + orderId);
        }

        @Override
        public String findOrder(long orderId) {
            System.out.println("[target] findOrder: " + orderId);
            return "order-" + orderId;
        }
    }

    public static void main(String[] args) {
        // 1. Target: logic nghiệp vụ thực tế.
        OrderServiceImpl target = new OrderServiceImpl();

        // 2. ProxyFactory và interface xuất ra cho caller.
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(target);
        factory.addInterface(OrderService.class);

        // 3. Advice chạy với mọi lời gọi qua proxy.
        MethodInterceptor traceAdvice = invocation -> {
            String name = invocation.getMethod().getName();
            System.out.println("[trace-before] " + name);
            try {
                return invocation.proceed();
            } finally {
                System.out.println("[trace-after] " + name);
            }
        };
        factory.addAdvice(traceAdvice);

        // 4. Advisor chỉ audit phương thức có tên bắt đầu bằng save.
        NameMatchMethodPointcut savePointcut =
                new NameMatchMethodPointcut();
        savePointcut.setMappedName("save*");

        MethodBeforeAdvice auditAdvice = (method, methodArgs, object) ->
                System.out.println("[audit] " + method.getName());

        DefaultPointcutAdvisor saveAdvisor =
                new DefaultPointcutAdvisor(savePointcut, auditAdvice);
        factory.addAdvisor(saveAdvisor);

        // 5. Tạo và sử dụng proxy, không gọi trực tiếp target.
        OrderService proxy = (OrderService) factory.getProxy();

        System.out.println("JDK proxy: " + AopUtils.isJdkDynamicProxy(proxy));
        System.out.println("Advisors: " + ((Advised) proxy).getAdvisors().length);

        proxy.saveOrder(7L);
        String found = proxy.findOrder(8L);
        System.out.println("[caller] " + found);
    }
}
```

Đặt các file vào đúng đường dẫn, cài JDK 17+ và Gradle rồi chạy:

```bash
gradle run
```

Nếu dự án có Gradle Wrapper, trên Linux/macOS dùng `./gradlew run`; trên Windows dùng `gradlew.bat run`. Stdout sau là **phần do chương trình tự in**, không bao gồm thông báo Gradle:

```text
JDK proxy: true
Advisors: 2
[trace-before] saveOrder
[audit] saveOrder
[target] saveOrder: 7
[trace-after] saveOrder
[trace-before] findOrder
[target] findOrder: 8
[trace-after] findOrder
[caller] order-8
```

**Giải thích kết quả:**

1. Vì `OrderServiceImpl` thực hiện `OrderService` và factory đã thêm interface này, Spring có thể tạo JDK dynamic proxy; `AopUtils.isJdkDynamicProxy(...)` trả về `true`.
2. `addAdvice(traceAdvice)` được Spring đóng gói vào một advisor áp dụng rộng. `addAdvisor(saveAdvisor)` thêm advisor có điều kiện `save*`; do đó `Advised.getAdvisors().length` là `2`.
3. Với `saveOrder(7)`, trace interceptor được thêm trước chạy outermost, sau đó before advice audit, cuối cùng target; phần `finally` của trace chạy khi target hoàn tất.
4. Với `findOrder(8)`, `save*` không khớp nên không có `[audit]`; trace chung vẫn chạy.
5. `found` nhận `order-8` vì around advice chuyển tiếp kết quả của `invocation.proceed()` về caller.

**Điểm quan sát thứ tự:** `factory.addAdvice(...)` trước `factory.addAdvisor(...)` khiến trace interceptor bao bọc audit trong ví dụ thủ công này. Nếu thay đổi vị trí advisor hoặc dùng cơ chế Spring tự sắp xếp advisor, phải kiểm tra thứ tự thực tế của chain.

## 8. Các biến thể cấu hình thường dùng

### 8.1. Constructor nhận target thay vì setTarget

```java
ProxyFactory factory = new ProxyFactory(new OrderServiceImpl());
factory.addAdvice(traceAdvice);
factory.addAdvisor(saveAdvisor);
OrderService proxy = (OrderService) factory.getProxy();
```

Với target triển khai `OrderService`, Spring nhận diện interface tương ứng; có thể gọi `setInterfaces(OrderService.class)` nếu muốn giới hạn tập interface được công khai. Đây vẫn là **proxy được tạo bằng Spring AOP API**.

### 8.2. Chỉ thêm advice có pointcut

```java
ProxyFactory factory = new ProxyFactory(new OrderServiceImpl());

NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
pointcut.setMappedName("save*");

MethodBeforeAdvice advice = (method, args, target) ->
        System.out.println("[audit] " + method.getName());

factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, advice));
OrderService proxy = (OrderService) factory.getProxy();
```

Khi không muốn advice chung, không cần gọi `addAdvice`; chỉ advisor chọn lọc là đủ. Như vậy ít overhead logic hơn trên các method không cần audit.

### 8.3. Buộc proxy theo lớp

```java
ProxyFactory factory = new ProxyFactory();
factory.setTarget(new OrderServiceImpl());
factory.setProxyTargetClass(true);
factory.addAdvice(traceAdvice);

OrderServiceImpl proxy = (OrderServiceImpl) factory.getProxy();
```

Lựa chọn này hữu ích khi cần chặn phương thức public trên class không có trong interface. Tuy vậy, phương thức `final`, `private` không thể bị CGLIB override để advice; lớp target `final` không thể trở thành superclass của proxy. Mọi ví dụ ở phần này là fragment minh họa và giả định các biến advice/kiểu target có sẵn.

### 8.4. Thêm nhiều interface

```java
factory.setInterfaces(OrderService.class, ReportingService.class);
```

Khi dùng JDK proxy theo các interface nghiệp vụ thông thường, target phải triển khai đúng các contract mà proxy cần gọi. Không khai báo interface chỉ để ép kiểu nếu target không có implementation; muốn bổ sung interface và hành vi mới cần infrastructure Spring *introduction advice* phù hợp.

## 9. Lời gọi thực sự đi qua proxy

Điều kiện cần để Spring AOP xử lý `Advice` và `Advisor` là lời gọi **đi qua proxy**. Ví dụ:

```java
OrderServiceImpl target = new OrderServiceImpl();
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvice(traceAdvice);
OrderService proxy = (OrderService) factory.getProxy();

proxy.saveOrder(1L);   // qua Spring proxy, traceAdvice chạy
target.saveOrder(2L);  // gọi trực tiếp target, không chạy traceAdvice
```

Self-invocation cũng có cùng vấn đề: nếu trong method `checkout()` của target gọi `this.saveOrder()` thì cuộc gọi thứ hai không quay qua proxy bên ngoài để kích hoạt một advisor riêng cho `saveOrder`.

Trong ứng dụng IoC, lấy đối tượng do container trả về bằng interface kiểu `OrderService` để bảo đảm đang dùng đúng bean/proxy được cấu hình. Nếu tự `new OrderServiceImpl()` ở caller, nó vẫn là đối tượng bình thường.

## 10. Thứ tự và vòng đời cấu hình

Một cấu hình `ProxyFactory` có thể chứa nhiều advisor. Với factory thủ công, trình tự gọi `addAdvice`/`addAdvisor` và overload có vị trí (ví dụ `addAdvisor(int, advisor)`) quyết định thứ tự chain. Một around interceptor nằm ngoài có dạng:

```text
around A — before
    around B — before
        target method
    around B — after
around A — after
```

Spring còn có `Ordered`/`@Order` khi dùng hạ tầng lựa chọn, sắp xếp advisor, nên cần phân biệt **thứ tự được đưa vào ProxyFactory thủ công** với **thứ tự do auto-proxy infrastructure sắp xếp**.

Nên cấu hình target, interface, advice và advisor **trước `getProxy()`**. Spring hỗ trợ một số thay đổi advisor qua `Advised` ngay cả sau khi proxy được tạo (nếu proxy chưa bị `frozen`), nhưng điều đó khác việc thay đổi các interface đã công khai. Không thiết kế ứng dụng dựa trên việc liên tục sửa chain đang hoạt động nếu không có lý do và kiểm thử cụ thể.

Kiểm tra nhanh:

```java
boolean jdk = AopUtils.isJdkDynamicProxy(proxy);
boolean cglib = AopUtils.isCglibProxy(proxy);
int advisorCount = ((Advised) proxy).getAdvisors().length;
```

Đây là fragment để inspect một Spring proxy có thể cast sang `Advised`. Với proxy factory ở phần 7, `jdk` là `true` và `advisorCount` là `2`. Thông tin kiểu proxy giúp giải thích vì sao một method không thuộc interface hoặc không thể override không được advice.

## 11. Sai lầm thường gặp

| Hiện tượng | Nguyên nhân trong Spring AOP | Cách giải quyết |
| --- | --- | --- |
| Không thấy log advice | Gọi target trực tiếp | Gọi đối tượng từ `factory.getProxy()` |
| `ClassCastException` khi cast proxy về implementation | JDK dynamic proxy triển khai interface, không phải subclass của target | Cast về interface hoặc cân nhắc class-based proxy |
| `addAdvice` chạy cả trên `find*` và `save*` | Spring dùng pointcut mặc định `Pointcut.TRUE` | Tạo `DefaultPointcutAdvisor` với pointcut cụ thể |
| Advisor có mặt nhưng advice không chạy | `ClassFilter`/`MethodMatcher` của pointcut không khớp | Kiểm tra method, target class và mẫu pointcut |
| Around advice bỏ qua target | Quên gọi `invocation.proceed()` | Gọi `proceed()` và giữ nguyên giá trị/exception theo mục tiêu |
| Self-invocation không chạy advice | `this.method()` đi trực tiếp bên trong target | Tách bean/đi qua proxy theo thiết kế |
| `final` class/method không proxy được bằng CGLIB | Proxy theo class cần kế thừa và override | Dùng interface proxy khi phù hợp, hoặc sửa khả năng mở rộng của class/method |
| Thêm interface không có hành vi tương ứng | `addInterface` chỉ khai báo contract của proxy | Bảo đảm target triển khai contract hoặc dùng introduction advisor |
| Nhầm `getProxy()` với `getTarget()` | Target và proxy là hai object với cách xử lý khác nhau | Sử dụng proxy cho caller, target chỉ để cấu hình |
| Thứ tự log bất ngờ | Nhiều advice/advisor tạo thành chain lồng nhau | Kiểm tra thứ tự `addAdvisor` hoặc cơ chế sắp xếp advisor đang dùng |

Để chẩn đoán, kiểm tra bốn điểm theo thứ tự: **(1) caller đang giữ proxy hay target, (2) loại JDK/CGLIB proxy, (3) tập interface/adapter hợp lệ, (4) danh sách advisor và pointcut match**. Chỉ khi bốn điểm đúng mới tìm tiếp trong logic advice.

## 12. Khi nào dùng API thủ công thay vì cấu hình Spring IoC?

`ProxyFactory` phù hợp cho:

- Bài thực hành cần thấy rõ target → interface → advice/advisor → proxy.
- Một thư viện hoặc adapter tạo proxy cho đối tượng runtime không do `ApplicationContext` quản lý.
- Test nhỏ để cô lập pointcut/interceptor chain, hoặc tích hợp một target cụ thể.

Trong các ứng dụng Spring Boot thông thường, `@Aspect`, bean `Advisor` cùng auto-proxy facility, hoặc Spring-managed infrastructure (transaction, cache, security...) thường thuận tiện hơn cho vòng đời bean. Dù dùng cách nào, các giới hạn Spring AOP proxy về interception và self-invocation vẫn cần được hiểu đúng.

### Câu hỏi tự kiểm tra

1. Nếu chỉ dùng `factory.addAdvice(traceAdvice)`, vì sao `findOrder()` cũng được trace? Vì Spring mặc định ghép advice với `Pointcut.TRUE`.
2. Nếu thêm `DefaultPointcutAdvisor(savePointcut, auditAdvice)`, `findOrder()` có chạy audit không? Không, vì tên `findOrder` không khớp `save*`.
3. Nếu tạo proxy bằng JDK và giữ trong biến `OrderService`, có gọi được method chỉ khai báo trên `OrderServiceImpl` không? Không thông qua interface proxy này.
4. Nếu gọi `target.saveOrder(...)` thay vì `proxy.saveOrder(...)`, có chạy advice không? Không, vì lời gọi không đi qua proxy.
5. Nếu muốn proxy triển khai thêm một interface mà target không có, chỉ `addInterface` đã đủ chưa? Chưa; cần introduction mechanism phù hợp.

## 13. Tài liệu chính thức của Spring Framework

- [Creating AOP proxies programmatically with ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html): ví dụ chính thức của `ProxyFactory`, `addAdvice` và `addAdvisor`.
- [Manipulating advised objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html): `Advised`, số lượng và cách quản lý advisor, mặc định `Pointcut.TRUE` khi thêm advice.
- [Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html): `ClassFilter`, `MethodMatcher` và pointcut chọn lọc.
- [Proxying mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK dynamic proxy, CGLIB và self-invocation.
- [ProxyFactory Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/ProxyFactory.html): constructor và các phương thức kế thừa để cấu hình proxy.
- [AdvisedSupport Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/AdvisedSupport.html): các phương thức quản lý advisor, advice, target và interface.

**Điểm cốt lõi:** `setTarget` xác định đối tượng nghiệp vụ; `addInterface` xác định giao diện JDK proxy công khai; `addAdvice` thêm hành vi áp dụng rộng; `addAdvisor` chọn điều kiện và advice cụ thể. `getProxy()` tạo object mà mã ứng dụng phải gọi để Spring AOP thực sự có hiệu lực.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="programmatic-proxy-creation-003">Tạo proxy với class loader phù hợp</a>

<details>
<summary>Xem chi tiết</summary>


# Tạo Spring AOP proxy với ClassLoader phù hợp

## 1. Mục tiêu và tình huống

Trong Spring AOP, khi tạo proxy bằng API như `ProxyFactory`, ứng dụng có thể chỉ định **ClassLoader sẽ dùng để tạo lớp proxy**. Với một ứng dụng có classpath đơn giản, Spring thường tự chọn loader phù hợp; trong plugin architecture, ứng dụng servlet, module container, môi trường devtools hoặc các module được nạp độc lập, chọn loader sai có thể khiến proxy không thể được tạo hoặc tạo được nhưng không cast được về interface mà caller mong đợi.

Ví dụ: module API định nghĩa `BillingService` trong một loader dùng chung, còn `BillingServiceImpl` do plugin loader nạp. Spring phải tạo proxy có thể nhìn thấy interface mà caller đang dùng. Nếu một loader không thể resolve `BillingService`, JDK proxy không thể đảm bảo contract tương ứng; nếu tồn tại hai bản `BillingService` cùng tên nhưng đến từ hai loader không tương thích, hai `Class<?>` vẫn là **hai kiểu khác nhau**.

Bài này tập trung vào **tạo proxy qua Spring AOP API**: `ProxyFactory`, `AopProxy`, `ProxyFactoryBean`, JDK dynamic proxy và class-based proxy của Spring. Chỉ nhắc kiến thức Java class loader ở những điểm ảnh hưởng trực tiếp đến Spring proxy.

Sau bài này, bạn có thể:

1. Xác định khi nào cần truyền `ClassLoader` vào `ProxyFactory.getProxy(ClassLoader)`.
2. Chọn loader tương thích với interface proxy, target type và Spring infrastructure.
3. Phân biệt việc **chọn class loader** với việc **chọn chiến lược JDK proxy hay class-based proxy**.
4. Kiểm tra tính hiển thị của interface trước khi tạo proxy, giữ đúng type identity sau khi tạo.
5. Chẩn đoán các lỗi `ClassNotFoundException`, `IllegalArgumentException`, `ClassCastException` và lỗi class-based proxy trong môi trường nhiều loader.

## 2. ClassLoader giữ vai trò gì khi Spring tạo proxy?

Spring AOP hoạt động tại ranh giới **method invocation đi qua proxy**. Khi tạo đối tượng proxy, framework phải định nghĩa hoặc nạp một lớp proxy:

- **JDK dynamic proxy:** tạo lớp triển khai các interface đã cấu hình và chuyển các method call vào Spring invocation handler. Loader dùng để định nghĩa proxy phải nhìn thấy các interface của proxy (và đáp ứng các ràng buộc visibility/module của JDK).
- **Class-based proxy:** Spring dùng cơ chế tạo subclass của target class. Loader/module/package visibility phải phù hợp để lớp sinh ra tham chiếu và mở rộng target, truy cập các type cần thiết, cùng tương tác với hạ tầng Spring.
- **Spring AOP advice/advisor:** là phần xử lý invocation được kết nối với proxy. Chọn loader chỉ tác động đến khả năng tạo/sử dụng proxy class; nó **không** tự kích hoạt aspect, sửa pointcut, thay đổi thứ tự advisor hay mở rộng những join point mà Spring AOP hỗ trợ.

Sơ đồ quyết định:

~~~text
Target + interfaces + advisors
           |
           v
       ProxyFactory
           |
           +-- Chọn JDK interface proxy?
           |       |
           |       +-- Proxy class implements interfaces
           |       +-- Loader phải resolve được interfaces
           |
           +-- Chọn class-based proxy?
                   |
                   +-- Proxy là subclass của target type
                   +-- Loader/module visibility phải tương thích với target
           |
           v
      Proxy object
           |
           +-- Caller nhận đúng interface/type identity?
           +-- Gọi qua proxy -> Spring interceptors -> target
~~~

Lỗi class loader có thể xảy ra **lúc tạo proxy**, hoặc muộn hơn khi caller cố cast/lookup một type không cùng identity. Chẩn đoán hai thời điểm này riêng biệt.

## 3. API Spring dùng để chọn class loader

| API trong Spring AOP | Ý nghĩa |
| --- | --- |
| `ProxyFactory.getProxy()` | Tạo proxy dùng lựa chọn loader mặc định của Spring/runtime |
| `ProxyFactory.getProxy(ClassLoader loader)` | Yêu cầu tạo proxy với loader được cung cấp (khi cần tạo lớp proxy) |
| `ProxyFactory.getProxyClass(ClassLoader loader)` | Xác định lớp proxy với cấu hình/loader được cung cấp; có ở Spring Framework 6+ |
| `AopProxy.getProxy()` / `AopProxy.getProxy(loader)` | API mức thấp sau khi cấu hình AOP proxy |
| `ProxyFactoryBean.setProxyClassLoader(ClassLoader loader)` | Ghi đè loader sinh proxy của một `ProxyFactoryBean` cụ thể |
| `ProxyFactoryBean.setBeanClassLoader(...)` | Callback `BeanClassLoaderAware` do container cung cấp; không nên gọi thủ công trong bean wiring thường ngày |

Ở `ProxyFactory`, overload quan trọng là:

~~~java
ProxyFactory factory = new ProxyFactory(target);
Object proxy = factory.getProxy(apiInterface.getClassLoader());
~~~

Đây là snippet minh họa: `target` và `apiInterface` đã được khai báo. Kết quả không phải lúc nào cũng nên ép về target class: nếu factory tạo JDK proxy, proxy implement **interface**, không phải subclass của implementation.

`getProxy()` thường phù hợp khi Spring có đủ thông tin từ môi trường/class loading thông thường. Overload `getProxy(ClassLoader)` hữu ích khi caller hoặc module host **biết chính xác loader có thể thấy API contract**, hoặc mặc định dựa trên thread context class loader (TCCL) không đúng trong một luồng thực thi riêng.

**Không xem `null` và không truyền tham số là hoàn toàn tương đương.** Theo hợp đồng `AopProxy`/Spring, `getProxy()` dùng default loader được framework chọn (thường liên quan TCCL); `getProxy(null)` chuyển lựa chọn xuống low-level proxy facility, có thể sử dụng mặc định khác. Với tình huống phức tạp, truyền loader tường minh thay vì dựa vào giả định này.

## 4. Chọn loader phù hợp: các quy tắc thực hành

### 4.1. Khi có interface public làm hợp đồng

Với JDK proxy, một lựa chọn ban đầu tốt là loader định nghĩa **interface mà caller cần cast**:

~~~java
ClassLoader apiLoader = PaymentApi.class.getClassLoader();

ProxyFactory factory = new ProxyFactory(paymentImplementation);
factory.setInterfaces(PaymentApi.class);
factory.setProxyTargetClass(false); // Chọn interface-based proxy.
PaymentApi proxy = (PaymentApi) factory.getProxy(apiLoader);
~~~

Đây là mã minh họa trong method đang có `PaymentApi` và `paymentImplementation`; ví dụ chạy đầy đủ có tất cả class ở mục 6.

Điều kiện cốt lõi: `apiLoader` phải thấy đúng `PaymentApi`, không chỉ một class cùng tên. Trong setup phức tạp, còn phải xem xét các interface bổ sung của proxy (ví dụ interface được giới thiệu bởi introduction advisor), visibility của hạ tầng Spring, và quy tắc non-public interface/module của JDK. `PaymentApi.class.getClassLoader()` là lựa chọn hợp lý khi chỉ dùng một public API interface, **không phải quy tắc bất biến** cho mọi app server/plugin.

### 4.2. Trong môi trường nhiều ClassLoader

Nếu plugin loader nạp implementation, còn parent/shared loader nạp interface chung:

~~~text
Host / API loader
    +-- example.api.PaymentApi
    +-- Spring AOP libraries
          |
          +-- Plugin loader (child)
                +-- example.plugin.PaymentServiceImpl
~~~

Một JDK proxy triển khai `PaymentApi` thường có thể dùng loader nhìn thấy đúng interface API. Nếu target implementation chỉ nằm ở plugin loader, JDK proxy **không nhất thiết cần định nghĩa chính proxy trong loader của target**, vì proxy class triển khai interface và invocation handler giữ tham chiếu đến target. Tuy nhiên signature liên quan các type khác, advisors, và module boundaries vẫn cần được kiểm tra trong setup thực tế.

Nếu interface được khai báo riêng trong **plugin**, loader của host có thể không nhìn thấy nó. Khi đó cần cân nhắc loader mà caller và Spring proxy đều có thể truy cập; hoặc đưa interface contract chung lên classloader boundary mà hai bên thực sự chia sẻ.

### 4.3. Khi dùng class-based proxy

Khi `setProxyTargetClass(true)`, Spring tạo proxy dạng subclass. Loader của API interface có thể **không đủ** vì subclass còn phải liên kết tới target implementation và các type mà target dùng:

~~~java
ProxyFactory factory = new ProxyFactory(target);
factory.setProxyTargetClass(true);
Object proxy = factory.getProxy(target.getClass().getClassLoader());
~~~

Đoạn trên chỉ minh họa điểm bắt đầu; phải kiểm tra module/package access, class final, method final/private, JVM restrictions và các cơ chế xác định loader của Spring. Không ép class-based proxy khi target thuộc plugin/module kín mà loader của proxy không thể truy cập target. Final class không thể bị subclass; final method không thể được advice bằng cách override; private method không phải join point có thể override như phương thức public.

### 4.4. Không tự sửa TCCL toàn cục để tạo một proxy

Thread context class loader có thể bị thay đổi bởi server, thread pool hoặc plugin host. Nó là một **mặc định phụ thuộc ngữ cảnh**, không phải bảo đảm loader đúng. Tránh sửa TCCL rồi quên phục hồi; hành vi có thể rò sang công việc kế tiếp của thread pool. Nếu biết loader cụ thể, truyền trực tiếp vào `getProxy(loader)`; nếu phải đổi TCCL cho một API khác, phục hồi trong `finally`.

## 5. Kiểm tra tính hiển thị và identity của interface

Một phép kiểm tra hữu ích trước khi tạo JDK proxy:

~~~java
ClassLoader chosenLoader = PaymentApi.class.getClassLoader();

Class<?> seen =
        Class.forName(PaymentApi.class.getName(), false, chosenLoader);

if (seen != PaymentApi.class) {
    throw new IllegalStateException("Incompatible API class identity");
}
~~~

Đây là snippet minh họa có `PaymentApi` được định nghĩa và method bao quanh khai báo `throws ClassNotFoundException` hoặc xử lý checked exception. `Class.forName(..., false, loader)` **không chủ động chạy static initializer** nhờ `initialize=false`, nhưng có thể thực hiện hoạt động class loading/linking tương ứng.

Phép kiểm tra `seen == PaymentApi.class` xác minh *cùng Class object*, bao gồm danh tính loader. Chỉ so `seen.getName().equals(PaymentApi.class.getName())` là thiếu vì hai loader khác nhau có thể nạp hai type cùng tên.

Tuy nhiên, một interface hiển thị đúng **chưa đủ để chứng minh** mọi interface khác, module, target, advice và Spring AOP infrastructure đều tương thích. Dùng thêm integration test qua chính class loader của ứng dụng thực tế.

## 6. Ví dụ hoàn chỉnh: ProxyFactory với ClassLoader tường minh

Ví dụ Java 17 và Spring Framework 6.1.x, sử dụng `ProxyFactory`, `MethodInterceptor` và **JDK dynamic proxy**. `TaskApi` là interface do ứng dụng khai báo; target thực thi method; advice ghi log trước/sau; proxy sinh ra với loader của `TaskApi`.

Cấu trúc:

~~~text
spring-aop-loader-demo/
├── pom.xml
└── src/main/java/demo/LoaderDemo.java
~~~

### 6.1. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-loader-demo</artifactId>
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
                    <mainClass>demo.LoaderDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-aop` cung cấp `ProxyFactory`, `AopUtils` và Spring AOP proxy runtime; các dependency cần thiết được Maven xử lý qua POM. Ví dụ tạo advisor programmatically và **không parse biểu thức AspectJ `@Aspect`**, vì thế không cần thêm `aspectjweaver` riêng chỉ để thực hiện đoạn mã này.

### 6.2. src/main/java/demo/LoaderDemo.java

~~~java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public class LoaderDemo {

    public interface TaskApi {
        String run(String value);
    }

    public static class TaskService implements TaskApi {
        @Override
        public String run(String value) {
            System.out.println("target: " + value);
            return "processed " + value;
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        TaskService target = new TaskService();
        ClassLoader apiLoader = TaskApi.class.getClassLoader();

        Class<?> visibleApi =
                Class.forName(TaskApi.class.getName(), false, apiLoader);
        if (visibleApi != TaskApi.class) {
            throw new IllegalStateException("Wrong API ClassLoader");
        }

        MethodInterceptor logging = invocation -> {
            System.out.println("advice: before");
            Object result = invocation.proceed();
            System.out.println("advice: after");
            return result;
        };

        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(target);
        factory.setInterfaces(TaskApi.class);
        factory.setProxyTargetClass(false);
        factory.addAdvice(logging);

        TaskApi proxy = (TaskApi) factory.getProxy(apiLoader);

        System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(proxy));
        System.out.println("sameLoader="
                + (proxy.getClass().getClassLoader() == apiLoader));
        System.out.println("sameContract=" + (proxy instanceof TaskApi));

        System.out.println("result=" + proxy.run("alpha"));
    }
}
~~~

Lệnh chạy:

~~~bash
mvn -q compile exec:java
~~~

Output từ các lệnh `System.out.println` của ví dụ:

~~~text
jdkProxy=true
sameLoader=true
sameContract=true
advice: before
target: alpha
advice: after
result=processed alpha
~~~

Kết quả có thể đi kèm log framework/Maven tùy môi trường. Trong **mô hình classpath đơn và JDK proxy đã cấu hình**, `sameLoader=true` cho thấy proxy class được định nghĩa bởi loader được truyền; `sameContract=true` cho thấy caller nhận đúng interface Java. Đây là quan sát riêng của ví dụ, không phải bảo đảm mọi proxy type hoặc mọi loader custom đều có `getClass().getClassLoader()==requestedLoader`.

**Giải thích đường thực thi:**

1. `TaskApi.class.getClassLoader()` chọn loader của API contract được caller dùng.
2. `Class.forName(..., false, apiLoader)` xác minh contract có thể resolve ra đúng `Class` object.
3. `factory.setInterfaces(TaskApi.class)` thiết lập interface proxy; `setProxyTargetClass(false)` yêu cầu JDK interface proxy trong trường hợp này.
4. `factory.addAdvice(logging)` nối Spring AOP `MethodInterceptor` vào proxy, không cần `@Aspect` vì dùng API programmatic.
5. `getProxy(apiLoader)` sinh proxy; gọi `proxy.run("alpha")` đi qua advice rồi đến target.
6. Advice gọi `invocation.proceed()` một lần và giữ nguyên return value.

Để thấy rằng class loader **không tự bật AOP**, bỏ dòng `factory.addAdvice(logging)` sẽ không còn log advice dù vẫn có proxy nếu cấu hình factory tạo proxy. Ngược lại, chỉ gọi `target.run(...)` đi trực tiếp đến target và không qua advice.

## 7. Nếu dùng ProxyFactoryBean trong Spring container

`ProxyFactoryBean` là `FactoryBean` cung cấp một Spring AOP proxy do bean factory quản lý. Nó triển khai `BeanClassLoaderAware`: Spring container thường cung cấp **bean ClassLoader**, đồng thời bean này hỗ trợ `setProxyClassLoader(ClassLoader)` khi muốn yêu cầu loader riêng cho proxy cụ thể.

Ý nghĩa của hai API:

| Thuộc tính/callback | Ai cung cấp? | Mục đích |
| --- | --- | --- |
| `setBeanClassLoader(loader)` | Container Spring khi bean được quản lý | Thông báo loader mặc định mà BeanFactory đang dùng để nạp bean classes |
| `setProxyClassLoader(loader)` | Cấu hình chuyên biệt nếu cần | Chỉ định loader dùng để sinh proxy được FactoryBean cung cấp |
| `getObject()` | Caller qua Spring container | Lấy đối tượng proxy đã được tạo |
| `getObjectType()` | Spring FactoryBean contract | Mô tả kiểu object mà FactoryBean tạo, để container hỗ trợ type lookup |

Ví dụ cấu hình programmatic theo định hướng bean factory (snippet minh họa):

~~~java
ProxyFactoryBean factoryBean = new ProxyFactoryBean();
factoryBean.setProxyInterfaces(new Class<?>[] { TaskApi.class });
factoryBean.setTarget(new TaskService());
factoryBean.setProxyClassLoader(TaskApi.class.getClassLoader());
~~~

Snipppet này giả định đã có `TaskApi`, `TaskService` và import `org.springframework.aop.framework.ProxyFactoryBean`. Trong ứng dụng thật, FactoryBean cũng cần advisor/interceptor nếu muốn advice, được đăng ký/cung cấp theo cơ chế Spring; việc khởi tạo FactoryBean bằng `new` ngoài BeanFactory **không** nhận callback `BeanClassLoaderAware` tự động.

Trong XML hoặc annotation-based container configuration, tốt nhất dựa trên loader của BeanFactory nếu classpath đơn giản. Chỉ dùng `setProxyClassLoader` khi đã xác định riêng proxy nào cần visibility khác và có bằng chứng từ test hoặc lỗi thực tế. Việc đổi loader của một proxy không làm mọi bean khác đổi loader, cũng không làm các module dùng chung tự động trở nên tương thích.

## 8. So sánh JDK proxy và class-based proxy trong bài toán loader

| Câu hỏi | JDK interface proxy | Spring class-based proxy |
| --- | --- | --- |
| Lớp proxy là gì? | Class được sinh để implement interfaces | Subclass của target class |
| Loader tối thiểu cần thấy gì? | Các proxy interfaces, thêm hạ tầng liên quan và quy tắc JDK/module | Target class và type liên quan; phải phù hợp để subclass/define class |
| Caller nên cast thành? | Interface đã cấu hình | Target type phù hợp (nếu proxy hợp lệ) |
| Có cần target class không final? | Không cần subclass target | Có, class phải kế thừa được |
| Final method có được advice bằng subclass? | Method được gọi qua interface proxy vẫn có thể advice tùy contract | Final method không thể bị subclass override để advice |
| Rủi ro chủ yếu trong plugin loader? | Không nhìn thấy interface đúng identity | Không nhìn thấy target hoặc vấn đề access/module/package |
| API lựa chọn loader | `getProxy(loader)` | `getProxy(loader)` (Spring xử lý backend class proxy riêng) |

**Không đánh đồng lựa chọn loader và lựa chọn proxy strategy.** Truyền `TaskApi.class.getClassLoader()` không ép Spring chọn JDK proxy; lựa chọn strategy đến từ interface, `proxyTargetClass` và cấu hình AOP factory. Ngược lại, `setProxyTargetClass(true)` không đảm bảo class-based proxy hoạt động nếu target là final class hoặc module/package access không cho phép tạo subclass.

Trong nhiều dự án, ưu tiên **API interface ổn định và JDK proxy** khi đó đã là contract tự nhiên. Không tạo interface chỉ để né vấn đề loader nếu ứng dụng thật sự cần intercept method không thuộc interface.

## 9. Những lỗi ClassLoader hay gặp và cách xác minh

### 9.1. Interface không visible với loader được chọn

Nếu loader không load/resolve được interface, JDK dynamic proxy có thể không tạo được lớp proxy và phát sinh `IllegalArgumentException`, `ClassNotFoundException` trong kiểm tra thủ công hoặc ngoại lệ được Spring bọc lại. Tên và nội dung exception cụ thể phụ thuộc Java/Spring version, module và loại proxy.

Kiểm tra trước:

~~~java
Class<?> actual = Class.forName(api.getName(), false, candidateLoader);
boolean sameClass = actual == api;
~~~

Đoạn trên là snippet với các biến `api` (`Class<?>`) và `candidateLoader` đã được khai báo. Nếu `Class.forName` thất bại hoặc `sameClass` là `false`, loader không phù hợp với **API identity** dự kiến.

Đừng chọn `ClassLoader.getSystemClassLoader()` chỉ vì nó dễ truy cập. Trong plugin/server environment, system loader có thể nhìn thấy Spring library nhưng không thấy application/plugin API.

### 9.2. Hai type cùng tên nhưng khác loader

Ví dụ:

~~~text
Host loader:   com.example.api.TaskApi  (Class A)
Plugin loader: com.example.api.TaskApi  (Class B)

Class A.getName().equals(Class B.getName()) == true
Class A == Class B                               == false
~~~

Đây là **mô hình mô tả**, không phải đoạn Java biên dịch độc lập. Nếu caller mong đợi Class A còn proxy implement Class B, việc cast về A có thể thất bại dù tên interface giống nhau. Cách xử lý thường là đưa API contract vào module dùng chung, thống nhất delegation/classloading policy và tránh đóng gói trùng API jar vào cả host lẫn plugin.

Việc thay class loader tùy tiện tại `getProxy()` không tự hợp nhất hai Class objects đã được load khác nhau.

### 9.3. Interface không public hoặc module access bị giới hạn

JDK có các ràng buộc đối với interface visibility và cách đặt package/module cho proxy triển khai non-public interfaces. Trong JPMS, module readability/exports/opens liên quan; với class-based proxy còn cần khả năng tạo subclass và truy cập target. Một loader nhìn thấy tên class không đảm bảo mọi phép tạo proxy hợp lệ.

Kiểm tra public API interface, class loader delegation, JPMS module path và log nguyên nhân gốc; đừng khắc phục bằng cách mở rộng toàn bộ module access khi chưa rõ lý do. Khi cần thiết, tạo lớp proxy và gọi nó trong integration test thực tế.

### 9.4. Class-based proxy của target không thể subclass

Nếu `setProxyTargetClass(true)` với final class/final method hoặc target trong package/module không cho phép proxy generation, lỗi không thể giải quyết chỉ bằng đổi từ parent loader sang TCCL. Cần xem lại proxy strategy, target class, method visibility và những ràng buộc Java module.

### 9.5. Advice không chạy dù proxy đã tạo được

Nếu `getProxy(loader)` thành công nhưng không có log/advice, nguyên nhân thường là thiếu advisor, pointcut không match, gọi vào target trực tiếp, hoặc self-invocation `this.otherMethod()`. Lỗi này **không nhất thiết liên quan ClassLoader**. Hãy xác nhận có `AopUtils.isAopProxy(proxy)`, kiểm tra advisor chain, và chắc chắn caller gọi proxy.

## 10. Chẩn đoán trực tiếp trong Spring

Dùng một routine nhỏ để đọc thông tin về target, API interface và proxy:

~~~java
import org.springframework.aop.support.AopUtils;

static void inspectProxy(Object proxy, Class<?> contract) {
    System.out.println("isSpringAopProxy="
            + AopUtils.isAopProxy(proxy));
    System.out.println("isJdkProxy="
            + AopUtils.isJdkDynamicProxy(proxy));
    System.out.println("isClassProxy="
            + AopUtils.isCglibProxy(proxy));

    System.out.println("contractLoader=" + contract.getClassLoader());
    System.out.println("proxyLoader="
            + proxy.getClass().getClassLoader());
    System.out.println("implementsContract=" + contract.isInstance(proxy));
}
~~~

Đây là **snippet minh họa trong một class Java**; cần gọi `inspectProxy(proxy, TaskApi.class)`. Những `println` về ClassLoader thường cho ra tên/toString phụ thuộc JVM, loader và môi trường, vì thế không cung cấp một output cố định cho chúng. `contract.isInstance(proxy)` có giá trị hơn so tên lớp bằng chuỗi vì nó kiểm tra tương thích kiểu ở runtime.

Nếu có Spring container, hãy lấy chính bean mà caller sử dụng:

~~~java
Object bean = context.getBean("taskService");
inspectProxy(bean, TaskApi.class);
~~~

Snippet này giả định `context` đã được khai báo và bean ID `taskService` tồn tại. Đừng tạo một target bằng `new` rồi dùng nó làm bằng chứng rằng AOP proxy trong ApplicationContext hoạt động.

### Checklist khi gặp lỗi trên production/plugin server

1. Ghi rõ **class loader của API contract**, target implementation và proxy class. Không chỉ ghi class names.
2. Kiểm tra `Class.forName(api.getName(), false, candidateLoader) == api`.
3. Kiểm tra mọi interface bổ sung mà proxy thực sự phải implement và các dependency/module boundaries cần thiết.
4. Xác minh chiến lược proxy: JDK hay class-based; caller phải dùng contract phù hợp.
5. Kiểm tra `AopUtils.isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy` và `api.isInstance(proxy)`.
6. Gọi một method test qua proxy và xác minh advice chạy đúng một lần, target nhận đúng arguments/return/exception.
7. Nếu dùng nhiều plugin loaders, test cùng deployment topology với production (hot reload, unload, context restarts), không chỉ với unit test một classpath.
8. Không cache proxy/classloader sống lâu hơn vòng đời plugin hoặc ApplicationContext khi hot redeploy; giữ reference cũ có thể gây classloader retention.

## 11. Phân biệt loader của Spring BeanFactory, TCCL và API type

| Nguồn loader | Phù hợp khi | Giới hạn |
| --- | --- | --- |
| `ApiContract.class.getClassLoader()` | Public shared interface contract; caller cũng dùng chính Class object đó | Có thể không thấy plugin-private interfaces hoặc target class của class-based proxy |
| BeanFactory bean ClassLoader | Bean và AOP config do cùng Spring container quản lý | Parent/child contexts hoặc plugin runtimes có thể có loader khác |
| `Thread.currentThread().getContextClassLoader()` | Container quy định TCCL cho job/thread hiện tại | Có thể bị thay đổi theo thread pool, request hoặc plugin host |
| `target.getClass().getClassLoader()` | Class-based proxy cần nhìn thấy concrete target | Loader target không chắc resolve được API interface caller dùng |
| System/platform/bootstrap loader | Chỉ khi thực sự resolve đúng mọi type cần thiết | Thường không thấy type do app/plugin loader tải |

Một trường hợp quan trọng: `SomeApi.class.getClassLoader()` có thể trả `null` nếu type do bootstrap loader định nghĩa; đó không phải mặc định thích hợp cho toàn bộ Spring infrastructure. Trong trường hợp này, xét loader thích hợp của interface ứng dụng hoặc BeanFactory, rồi xác minh bằng phép thử runtime.

Tài liệu Spring API nhấn mạnh `getProxy(null)` có thể dùng default khác với overload `getProxy()`. Đừng dùng `null` như một cách bí mật để “tự chọn loader tốt nhất” trong mọi deployment.

## 12. Tình huống kiểm thử nên có

| Trường hợp | Kiểm tra | Kết luận mong đợi |
| --- | --- | --- |
| API và target trong một app classloader | `getProxy(Api.class.getClassLoader())` | Proxy tạo thành công, implements đúng API, advice chạy |
| Hai bean target được gọi qua cùng API interface | Chỉ định interfaces và loader đúng | Caller cast bằng API chung; identity được giữ |
| Host classloader không thấy plugin-private interface | Phép `Class.forName` với host loader | Không dùng host loader để định nghĩa JDK proxy cho interface đó |
| Hai bản cùng class name do hai plugin loaders nạp | So sánh `Class<?>` object bằng `==` | Hai type khác nhau dù FQCN giống nhau |
| JDK proxy nhưng caller cast concrete target class | Cast sang implementation | Có thể `ClassCastException`; cast bằng interface đã cấu hình |
| Class-based proxy của final target | Bật `proxyTargetClass` | Lỗi/hạn chế subclassing; chọn thiết kế proxy phù hợp |
| Default TCCL thay đổi theo worker thread | So loader actual và requested | Dùng explicit loader nếu default sai |
| Proxy tạo được nhưng advice không chạy | Kiểm tra advisor/pointcut và đường gọi | Xác định vấn đề AOP configuration chứ không suy đoán lỗi loader |
| Plugin unload/redeploy | Kiểm tra bean/proxy references cũ | Tránh giữ proxy dẫn đến giữ sống class loader cũ |

## 13. Các lỗi thiết kế cần tránh

- **Cố ép JDK proxy thành implementation class.** Interface proxy không phải instance của concrete target.
- **Chọn loader bằng tên class hoặc bằng `getSystemClassLoader()` một cách máy móc.** Type identity là tổ hợp class name và defining loader; visibility có thể khác từng container.
- **Tưởng `getProxy(loader)` biến Spring AOP thành bytecode weaving.** Nó chỉ chọn loader dùng tạo proxy, không chặn field/constructor/self-invocation.
- **Ép `proxyTargetClass=true` để “chữa” ClassCastException mà không xét final/module restrictions.** Điều này đổi cơ chế proxy và có thể gây vấn đề khác.
- **Tin rằng loader của target luôn đúng cho JDK proxy.** Nếu caller dùng API do loader khác định nghĩa, phải bảo đảm proxy implement chính Class object mà caller mong đợi.
- **Thay TCCL ở một thread pool mà không phục hồi.** Job sau có thể tạo proxy dưới loader sai.
- **Giữ proxy qua nhiều lần hot reload.** Proxy có thể giữ Class, advisors, target và loader cũ; cần đồng bộ vòng đời với context/plugin.
- **Xem một phép kiểm tra Class.forName là bằng chứng đủ.** Ngoài interface chính còn các interface được introduced, access rules, target/method eligibility và hạ tầng Spring.
- **Tự dự đoán message của exception là ổn định giữa JDK/Spring versions.** Xem root cause, type identity và điều kiện tái hiện cụ thể.

## 14. Quy trình lựa chọn nhanh

1. Xác định public API interface mà caller thực sự nhận, và nơi interface được load.
2. Chọn interface-based Spring proxy khi phù hợp với contract; cấu hình `setInterfaces(...)` và `setProxyTargetClass(false)`.
3. Chọn loader có thể resolve chính xác tất cả proxy interfaces; thử `Api.class.getClassLoader()` và kiểm chứng `Class.forName(..., false, loader) == Api.class`.
4. Nếu cần class-based proxy, kiểm tra target class loader/module/package và giới hạn final/private trước khi quyết định loader.
5. Gọi `ProxyFactory.getProxy(loader)` bằng loader đã kiểm tra. Với `ProxyFactoryBean`, thông thường để BeanFactory chọn loader; chỉ đặt `setProxyClassLoader` khi cần.
6. Kiểm thử bằng `AopUtils`, `api.isInstance(proxy)` và một invocation qua proxy. Bảo đảm advice/target/return/exception đúng.
7. Kiểm tra deployment với các classloader thực tế khi có plugin, app server, hot-reload hoặc phân cấp ApplicationContext.

**Ghi nhớ:** Spring AOP proxy phải được tạo trong một class-loading context nhìn thấy đúng **type contract** của caller, đồng thời đáp ứng ràng buộc của JDK proxy hoặc subclass-based proxy. `getProxy(ClassLoader)` giúp chọn context đó; bản thân lựa chọn ClassLoader không thay thế việc khai báo advisor và không vượt qua giới hạn proxy của Spring.

### Tham khảo Spring chính thức

- [ProxyFactory — Spring AOP API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/ProxyFactory.html) — `getProxy()`, `getProxy(ClassLoader)`, `getProxyClass(ClassLoader)`.
- [AopProxy — Spring AOP API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/AopProxy.html) — sự khác biệt giữa các overload `getProxy`.
- [ProxyFactoryBean — Spring AOP API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/ProxyFactoryBean.html) — `setProxyClassLoader` và bean class loader.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html) — cơ chế JDK/CGLIB, self-invocation và giới hạn proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="programmatic-proxy-creation-004">ProxyFactoryBean và việc khai báo qua Spring container</a>

<details>
<summary>Xem chi tiết</summary>

# ProxyFactoryBean và việc khai báo Spring AOP proxy qua Spring container

## 1. Mục tiêu và bối cảnh

Khi ứng dụng Spring muốn chủ động tạo một **Spring AOP proxy** nhưng vẫn để Spring IoC quản lý target, advice và advisor, ta có thể dùng `org.springframework.aop.framework.ProxyFactoryBean`. Đây là một `FactoryBean<Object>`: bean definition của nó tạo ra **một đối tượng proxy** để những bean khác inject hoặc gọi. Spring container giữ vai trò đăng ký các đối tượng cấu hình, resolve tên advisor, quản lý vòng đời và cung cấp proxy theo contract của bean.

Khác với `ProxyFactory` tạo proxy bằng code Java trực tiếp, `ProxyFactoryBean` phù hợp khi muốn khai báo cấu hình (thường XML) để lựa chọn target và danh sách advice/advisor theo bean name. Nó có thể làm việc với `ApplicationContext` hoặc `BeanFactory`, nhưng cần nhớ rằng **đăng ký factory không đồng nghĩa mọi object có class target đó đều được advice**: chỉ những lời gọi đi qua proxy được factory cung cấp mới có cơ hội đi qua Spring AOP interceptor chain.

Ví dụ có `OrderServiceImpl` và hai hành vi cắt ngang:

- Trace interceptor ghi dòng before/after cho **mọi method** được gọi qua proxy.
- Quote advisor chỉ áp dụng cho method `quote(...)` bằng một Spring `Pointcut`.

Một `ProxyFactoryBean` có thể tạo bean proxy `orderService` chứa cả hai. Bất kỳ component nào inject đúng proxy đó sẽ đi qua chain theo thứ tự đã cấu hình; nếu component tự `new OrderServiceImpl()` hoặc inject `orderTarget` gốc, advice của proxy sẽ không chạy.

Bài này chỉ tập trung vào **Spring AOP**: `ProxyFactoryBean`, việc khai báo qua Spring container, điểm khác với `ProxyFactory`, cách đọc advisor chain, lựa chọn JDK/CGLIB, singleton/prototype và những lỗi wiring thường gặp.

## 2. ProxyFactoryBean khác FactoryBean thông thường ở đâu?

`FactoryBean<T>` của Spring cung cấp một phương thức `getObject()` để tạo/trả một object khác với chính factory. `ProxyFactoryBean` dùng cơ chế này để tạo ra **AOP proxy**.

~~~text
Spring ApplicationContext
  |
  +-- bean "orderTarget" : demo.OrderServiceImpl
  |
  +-- bean "traceInterceptor" : demo.TraceInterceptor
  +-- bean "quoteAdvisor" : Spring DefaultPointcutAdvisor
  |
  +-- bean definition "orderService" : ProxyFactoryBean
            |
            +-- target / targetName / targetSource
            +-- proxyInterfaces = demo.OrderService
            +-- interceptorNames = [traceInterceptor, quoteAdvisor]
            |
            +-- getObject() -> Spring AOP proxy
                                   |
                                   +-- Trace advice
                                   +-- Quote advisor (nếu pointcut match)
                                   +-- orderTarget method
~~~

Trong ApplicationContext thông thường:

~~~java
Object proxy = context.getBean("orderService");
Object factory = context.getBean("&orderService");
~~~

- `getBean("orderService")` trả **đối tượng mà `ProxyFactoryBean.getObject()` cung cấp**, tức Spring AOP proxy.
- `getBean("&orderService")` trả **chính FactoryBean** nhờ prefix đặc biệt `&` của Spring; khi dùng nhiều factory beans, nguyên tắc dereference này vẫn tương tự.
- `getBean("orderTarget")` trả **raw target** nếu target được đăng ký riêng dưới tên đó; gọi trực tiếp nó không đi qua proxy `orderService`.
- Với các bean khác, inject theo interface `OrderService` có thể gặp nhiều candidates nếu cả `orderTarget` và proxy đều cùng type. Dùng `@Qualifier`, `@Primary`, tên bean, hoặc khai báo target làm inner bean để tránh inject nhầm.

`ProxyFactoryBean` không phải một annotation scanner để tự tìm mọi target. Nó là **một factory cụ thể** với cấu hình advisor/target riêng. Nó có quyền truy cập owning BeanFactory qua `BeanFactoryAware` và class loader qua `BeanClassLoaderAware`, nhờ vậy có thể resolve `interceptorNames` và tạo proxy được container quản lý.

## 3. Những thuộc tính cần biết

| Thuộc tính trên ProxyFactoryBean | Ý nghĩa thực tế | Lưu ý |
| --- | --- | --- |
| `target` | Gán target object trực tiếp, thường qua `ref` hoặc inner bean | Object được proxy có thể bị truy xuất ngay khi container tạo factory |
| `targetName` | Tên target bean trong BeanFactory | Hữu ích nếu muốn factory resolve target theo tên |
| `targetSource` | Chỉ rõ Spring `TargetSource` | Dùng khi cần lazy/pooling/prototype target semantics thay cho một target cố định |
| `proxyInterfaces` | Danh sách các interface proxy cần expose | Dùng tên interface đầy đủ trong XML; phù hợp JDK proxy |
| `interceptorNames` | Mảng tên các Advice/Advisor bean | **Danh sách chuỗi**, thứ tự quan trọng, không phải danh sách `<ref>` |
| `proxyTargetClass` | `true` để yêu cầu class-based proxy | Target phải subclass được; final/private methods không được intercept theo cơ chế subclass |
| `autodetectInterfaces` | Cho phép tự phát hiện interfaces nếu không khai báo `proxyInterfaces` | Mặc định `true`; đừng dựa vào mặc định nếu contract muốn expose cần chặt chẽ |
| `singleton` | Factory cung cấp cùng proxy reference hay tạo proxy mới mỗi lần lấy | Mặc định `true`; phân biệt với scope/lifecycle của target |
| `frozen` | Đóng băng cấu hình AOP proxy để không sửa danh sách advisors sau khi tạo | Không đồng nghĩa immutable business object |
| `exposeProxy` | Có cho target truy cập current Spring proxy trong invocation hay không | Đòi ThreadLocal và tạo coupling; tránh dùng như giải pháp mặc định cho self-invocation |
| `proxyClassLoader` | Loader để sinh lớp proxy | Hữu ích với app server/module/plugin nhiều loader; phải nhìn thấy đúng interfaces |

Không cần đặt tất cả thuộc tính. Một cấu hình dễ hiểu nên chọn **một** nguồn target phù hợp (`target`, `targetName` hoặc `targetSource`), một chiến lược proxy rõ ràng và các advice/advisor có mục đích.

### 3.1. target, targetName và targetSource

Cách phổ biến nhất là:

~~~xml
<property name="target" ref="orderTarget"/>
~~~

Tức FactoryBean nhận một Spring bean instance đã được resolve. Nếu cần khai báo bằng tên, dùng:

~~~xml
<property name="targetName" value="orderTarget"/>
~~~

Còn `targetSource` nhận một bean implement `org.springframework.aop.TargetSource`, ví dụ `LazyInitTargetSource`, `PrototypeTargetSource` hoặc các target source phù hợp khác. `TargetSource` có semantics riêng về thời điểm và cách lấy target. Không đặt `target`, `targetName` và `targetSource` cùng lúc để mô tả ba target độc lập.

Tránh cách cũ cho target làm **phần tử cuối** của `interceptorNames`: Spring Javadoc đã deprecate lối khai báo đó và khuyên dùng `targetName` hoặc `target`/`targetSource` thay thế. Với cách khai báo target riêng, **mọi tên trong `interceptorNames` phải trỏ tới advice/advisor**, không được chèn tên target vào cuối danh sách.

### 3.2. interceptorNames: tại sao phải là tên string?

~~~xml
<property name="interceptorNames">
    <list>
        <value>traceInterceptor</value>
        <value>quoteAdvisor</value>
    </list>
</property>
~~~

Khi một `ProxyFactoryBean` cần tạo proxy, nó hỏi BeanFactory về bean theo từng tên; Spring có thể lấy đúng bean theo scope, kể cả advice prototype khi factory `singleton=false`. Dùng `<ref bean="traceInterceptor"/>` thay `<value>traceInterceptor</value>` trong `interceptorNames` là **không đúng contract** của thuộc tính `String[]`.

Một `MethodInterceptor` là Advice đã có thể được liệt kê trực tiếp. Một `Advisor` thì gói Advice với `Pointcut`/điều kiện áp dụng. Hãy dùng Advisor khi advice chỉ chạy trên một tập method, thay vì bắt interceptor tự viết logic lọc tên method cho mọi invocation.

### 3.3. Ordering của interceptor chain

Giả sử hai tên trong `interceptorNames` theo thứ tự `traceInterceptor`, `quoteAdvisor`, và cả hai match method `quote`:

~~~text
traceInterceptor.before
  quoteAdvisor.advice.before
    orderTarget.quote
  quoteAdvisor.advice.after
traceInterceptor.after
~~~

Interceptor được liệt kê trước nhận cơ hội chặn lời gọi trước. Nếu một around interceptor không gọi `proceed()` thì các interceptor bên trong và target method có thể không chạy. Một Advisor chỉ áp dụng khi pointcut match, vì thế method `status` sẽ không đi qua `quoteAdvisor` ở ví dụ đầy đủ.

Các cấu hình Spring rộng hơn có thể có auto-proxy infrastructure hoặc nhiều advisors với ordering khác. Đừng xem danh sách của **một** `ProxyFactoryBean` là thứ tự toàn bộ cross-cutting features của ứng dụng nếu bean còn được wrapping bởi những proxy khác.

## 4. Sơ đồ mẫu XML tối thiểu

~~~xml
<bean id="orderTarget" class="demo.OrderServiceImpl"/>

<bean id="traceInterceptor" class="demo.TraceInterceptor"/>

<bean id="orderService"
      class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="proxyInterfaces" value="demo.OrderService"/>
    <property name="target" ref="orderTarget"/>
    <property name="interceptorNames">
        <list>
            <value>traceInterceptor</value>
        </list>
    </property>
</bean>
~~~

Đây là phần **wiring rút gọn** cần đặt vào tài liệu `<beans>` có namespace/schema chính xác và phải có class `demo.OrderService`, `demo.OrderServiceImpl`, `demo.TraceInterceptor`. Trong ví dụ hoàn chỉnh dưới đây, ta bổ sung một `quoteAdvisor` chỉ match method `quote`.

**Vai trò từng bean:**

- `orderTarget`: class nghiệp vụ chưa được AOP-advised khi gọi trực tiếp.
- `traceInterceptor`: object implement `MethodInterceptor`.
- `orderService`: tên **proxy bean** mà callers nên dùng; một `ProxyFactoryBean` đứng sau tên ấy để tạo proxy.
- `proxyInterfaces`: contract Java interface mà Spring JDK proxy implement.
- `target`: nơi Spring chuyển business invocation sau advice.
- `interceptorNames`: xác định những advice/advisor thực sự tham gia chain.

Không cần `<aop:config>` hoặc `@Aspect` để tạo proxy theo API này. Đây là cách cấu hình trực tiếp cấp thấp của **Spring AOP**, vẫn có thể hoạt động song song với BeanFactory và Dependency Injection.

## 5. Ví dụ hoàn chỉnh Java 17 + Maven + Spring XML

Ví dụ sử dụng Spring Framework 6.1.15 và Java 17. Mục tiêu là thấy trực tiếp:

1. Spring `ApplicationContext` tạo proxy từ `ProxyFactoryBean`.
2. `getBean("orderService")` trả Spring JDK AOP proxy.
3. `getBean("&orderService")` trả `ProxyFactoryBean`.
4. Hai lần lấy proxy với mặc định singleton trả cùng reference.
5. `TraceInterceptor` chạy trước/sau mọi method; `QuoteInterceptor` chỉ được advisor cho phép chạy trên `quote(...)`.
6. Gọi `orderTarget` trực tiếp không kích hoạt hai advice của proxy.

### 5.1. Cấu trúc dự án

~~~text
proxyfactorybean-demo/
├── pom.xml
└── src/main/
    ├── java/demo/
    │   ├── App.java
    │   ├── OrderService.java
    │   ├── OrderServiceImpl.java
    │   ├── TraceInterceptor.java
    │   └── QuoteInterceptor.java
    └── resources/applicationContext.xml
~~~

### 5.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>proxyfactorybean-demo</artifactId>
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
                    <mainClass>demo.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp `ClassPathXmlApplicationContext`, `spring-aop` cung cấp `ProxyFactoryBean`, `Advisor`, `AopUtils` và interceptor infrastructure. `MethodInterceptor` thuộc AOP Alliance API được Spring quản lý qua dependencies. Ví dụ này **không dùng AspectJ expression parser** nên không cần tự thêm `aspectjweaver` để chạy.

### 5.3. Service interface

**src/main/java/demo/OrderService.java**

~~~java
package demo;

public interface OrderService {
    String quote(String id);
    String status();
}
~~~

Mọi method caller dự kiến gọi qua JDK proxy phải nằm trong interface được expose. Không nên inject target concrete class khi proxy là JDK interface proxy.

### 5.4. Target implementation

**src/main/java/demo/OrderServiceImpl.java**

~~~java
package demo;

public class OrderServiceImpl implements OrderService {

    @Override
    public String quote(String id) {
        System.out.println("target: quote " + id);
        return "QUOTED " + id;
    }

    @Override
    public String status() {
        System.out.println("target: status");
        return "READY";
    }
}
~~~

Không cần `@Component` vì XML sẽ tự khai báo bean target. Nếu vừa bật component scanning vừa khai báo class này nhiều lần, hãy xác minh bean IDs và candidates để tránh inject nhầm target thô.

### 5.5. Interceptor áp dụng mọi method

**src/main/java/demo/TraceInterceptor.java**

~~~java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class TraceInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String method = invocation.getMethod().getName();
        System.out.println("trace: before " + method);

        try {
            Object result = invocation.proceed();
            System.out.println("trace: after " + method);
            return result;
        } catch (Throwable ex) {
            System.out.println("trace: error " + method);
            throw ex;
        }
    }
}
~~~

`MethodInterceptor` nhận `MethodInvocation`. Lệnh `proceed()` phải được gọi đúng ý đồ để method chain đi tiếp tới các advisors bên trong và target. Mọi code sau `proceed()` không chạy nếu exception bị ném và không được catch/handle.

### 5.6. Interceptor chỉ cho method quote

**src/main/java/demo/QuoteInterceptor.java**

~~~java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class QuoteInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        System.out.println("quote: before");
        try {
            Object result = invocation.proceed();
            System.out.println("quote: after");
            return result;
        } catch (Throwable ex) {
            System.out.println("quote: error");
            throw ex;
        }
    }
}
~~~

Bản thân interceptor này không tự check method name. **Advisor** được cấu hình bằng `NameMatchMethodPointcut` sẽ quyết định method nào match, sau đó mới gọi `QuoteInterceptor`.

### 5.7. applicationContext.xml

**src/main/resources/applicationContext.xml**

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Target raw, nếu gọi trực tiếp sẽ bypass proxy advice. -->
    <bean id="orderTarget" class="demo.OrderServiceImpl"/>

    <!-- Advice áp dụng cho mọi method qua proxy. -->
    <bean id="traceInterceptor" class="demo.TraceInterceptor"/>

    <!-- Advice thứ hai chỉ chạy trên method tên quote. -->
    <bean id="quoteInterceptor" class="demo.QuoteInterceptor"/>

    <bean id="quotePointcut"
          class="org.springframework.aop.support.NameMatchMethodPointcut">
        <property name="mappedName" value="quote"/>
    </bean>

    <bean id="quoteAdvisor"
          class="org.springframework.aop.support.DefaultPointcutAdvisor">
        <property name="pointcut" ref="quotePointcut"/>
        <property name="advice" ref="quoteInterceptor"/>
    </bean>

    <!-- Consumer sẽ inject/getBean theo id orderService. -->
    <bean id="orderService"
          class="org.springframework.aop.framework.ProxyFactoryBean">
        <property name="proxyInterfaces" value="demo.OrderService"/>
        <property name="target" ref="orderTarget"/>
        <property name="proxyTargetClass" value="false"/>
        <property name="singleton" value="true"/>
        <property name="interceptorNames">
            <list>
                <value>traceInterceptor</value>
                <value>quoteAdvisor</value>
            </list>
        </property>
    </bean>
</beans>
~~~

Giải thích các quyết định:

- Dùng `NameMatchMethodPointcut` thay vì điểm cắt AspectJ để minh họa trực tiếp **Spring AOP API**; `mappedName=quote` chỉ match method `quote`.
- `DefaultPointcutAdvisor` gói `quotePointcut` và `quoteInterceptor`; nó được nêu trong `interceptorNames` bằng **tên bean string**.
- `traceInterceptor` không có pointcut giới hạn nên áp dụng các method invocation phù hợp với Spring proxy interceptor chain, gồm `quote` và `status` trong ví dụ.
- `interceptorNames` đặt `traceInterceptor` trước `quoteAdvisor`, nên trace ghi before trước quote, ghi after sau quote.
- `proxyInterfaces=demo.OrderService` và `proxyTargetClass=false` chọn **Spring JDK interface proxy**.
- `singleton=true` là mặc định, nhưng khai báo tường minh để phục vụ quan sát vòng đời ở bài.
- Target được khai báo với ID riêng `orderTarget` để minh họa nguy cơ gọi raw target. Trong dự án thực tế, cần hạn chế consumer vô tình inject ID này.

### 5.8. Chương trình main

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class App {

    public static void main(String[] args) {
        try (var context =
                new ClassPathXmlApplicationContext("applicationContext.xml")) {

            OrderService service =
                    context.getBean("orderService", OrderService.class);

            Object second = context.getBean("orderService");
            ProxyFactoryBean factory =
                    context.getBean("&orderService", ProxyFactoryBean.class);

            System.out.println("springProxy=" + AopUtils.isAopProxy(service));
            System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(service));
            System.out.println("sameProxy=" + (service == second));
            System.out.println("factoryType="
                    + factory.getClass().getSimpleName());

            System.out.println("-- quote through proxy --");
            System.out.println("result=" + service.quote("A-1"));

            System.out.println("-- status through proxy --");
            System.out.println("status=" + service.status());

            OrderService raw =
                    context.getBean("orderTarget", OrderService.class);

            System.out.println("-- raw target direct call --");
            System.out.println("direct=" + raw.quote("B-2"));
        }
    }
}
~~~

Từ thư mục chứa `pom.xml`, chạy:

~~~bash
mvn -q compile exec:java
~~~

Các dòng ứng dụng `System.out` dự kiến theo thứ tự:

~~~text
springProxy=true
jdkProxy=true
sameProxy=true
factoryType=ProxyFactoryBean
-- quote through proxy --
trace: before quote
quote: before
target: quote A-1
quote: after
trace: after quote
result=QUOTED A-1
-- status through proxy --
trace: before status
target: status
trace: after status
status=READY
-- raw target direct call --
target: quote B-2
direct=QUOTED B-2
~~~

Maven và framework có thể in thêm log; bảng kết quả chỉ ghi những dòng do source Java trong ví dụ tự in.

**Vì sao output như vậy?**

1. `AopUtils.isAopProxy(service)` trả true vì đối tượng `orderService` là Spring AOP proxy.
2. `AopUtils.isJdkDynamicProxy(service)` true do cấu hình interface-based proxy.
3. Hai lần `context.getBean("orderService")` trả cùng proxy instance nhờ `singleton=true`.
4. `"&orderService"` dereference để lấy bản thân `ProxyFactoryBean`, nên simple class name là `ProxyFactoryBean`.
5. `quote("A-1")` match cả trace advice và quote advisor; lệnh `proceed()` tạo thứ tự lồng nhau như output.
6. `status()` không match `quotePointcut`; chỉ trace chạy quanh target `status`.
7. `raw.quote("B-2")` chạy target method nhưng không có trace hay quote advice vì đã **bỏ qua proxy**.

Đây là ví dụ minh họa Spring AOP một context, không có `@Aspect` hoặc auto-proxy creator khác; các advisor trong ví dụ được quản lý hoàn toàn theo cấu hình `ProxyFactoryBean`.

## 6. Phân biệt ProxyFactoryBean với ProxyFactory và auto-proxy

| Công cụ | Ai tạo proxy? | Cách cấu hình | Khi nào sử dụng |
| --- | --- | --- | --- |
| `ProxyFactory` | Code Java gọi `getProxy()` | Programmatic: `setTarget`, `addAdvisor`, `addAdvice` | Test, code hạ tầng hoặc cần tạo proxy chủ động ngoài IoC |
| `ProxyFactoryBean` | Spring BeanFactory gọi `getObject()` qua `FactoryBean` | Bean definition XML/IoC, `interceptorNames` | Muốn expose một bean proxy cụ thể, kiểm soát thứ tự/cấu hình từ container |
| `@EnableAspectJAutoProxy` + `@Aspect` | Spring auto-proxy creator xử lý beans phù hợp | Annotation và pointcut expressions | Thông dụng khi có nhiều aspects áp dụng theo cross-cutting rules |
| `<aop:config>` | Spring AOP namespace infrastructure | XML schema-based pointcuts/aspects | Muốn khai báo AOP theo schema thay vì khai báo ProxyFactoryBean trực tiếp |

Các cơ chế có chung nguyên lý Spring AOP proxy/advisor. **Không đồng thời cấu hình cùng logic advice hai lần** bằng `ProxyFactoryBean` và auto-proxy creator mà không kiểm tra: bean có thể bị bọc hai lớp proxy hoặc chạy advice hai lần, tùy cách hạ tầng/pointcuts match.

Nếu đang xây ứng dụng Spring với các aspect theo nhóm package, `@Aspect`/schema AOP thường ngắn gọn và dễ bảo trì. `ProxyFactoryBean` đặc biệt hữu ích khi cần expose một **proxy bean rõ ràng**, quản lý advisors như các Spring beans riêng biệt, hoặc tích hợp một cấu hình hạ tầng AOP cấp thấp.

## 7. ProxyFactoryBean singleton và prototype

Mặc định `ProxyFactoryBean.singleton = true`: Spring trả cùng proxy reference cho các lần truy xuất thông thường. Cần tách ba khái niệm:

- **Scope của FactoryBean definition**: bản thân bean `ProxyFactoryBean` có scope trong BeanFactory.
- **`FactoryBean.isSingleton()`**: factory công bố sản phẩm là singleton hay không.
- **Scope và cơ chế của target/advice beans**: singleton, prototype hoặc TargetSource có scope/semantics riêng.

Vì vậy `singleton=false` trên ProxyFactoryBean không phải lời hứa “mỗi invocation có target mới”. Nó chỉ nói các lần lấy **proxy product** có thể là instance khác nhau. Nếu target được cấu hình bằng `<property name="target" ref="orderTarget"/>` mà `orderTarget` là singleton, nhiều proxy products có thể **chia sẻ cùng một target object**.

Ví dụ định hướng cấu hình:

~~~xml
<bean id="orderService"
      class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="singleton" value="false"/>
    <property name="targetName" value="orderTarget"/>
    <property name="proxyInterfaces" value="demo.OrderService"/>
    <property name="interceptorNames">
        <list>
            <value>traceInterceptor</value>
        </list>
    </property>
</bean>
~~~

Đây là **biến thể minh họa**, thay cho bean `orderService` chính của mục 5; không khai báo trùng ID trong một context. Nếu `orderTarget` vẫn singleton thì dù proxy product mới được tạo, target có thể vẫn một instance. Nếu cần **target mới theo invocation**, dùng `TargetSource` phù hợp (ví dụ `PrototypeTargetSource`), không chỉ thay `singleton=false`.

Một advice **stateful** cũng cần cân nhắc scope/lifecycle. Nếu `interceptorNames` trỏ tới một advice prototype và muốn mỗi proxy product có advice instance riêng, kết hợp scope advice với `singleton=false` theo contract Spring. Không dùng singleton advice có mutable state per-request không thread-safe.

### Cách kiểm thử

~~~java
Object first = context.getBean("orderService");
Object second = context.getBean("orderService");
System.out.println(first == second);
~~~

Với ví dụ mục 5 và `singleton=true`, kết quả là `true`. Với cấu hình trả prototype proxy, kết quả có thể là `false`, nhưng không suy ra `first` và `second` có target khác nhau mà chưa kiểm tra `TargetSource`/scope.

## 8. JDK dynamic proxy hay Spring CGLIB?

`ProxyFactoryBean` có thể tạo JDK proxy qua interfaces hoặc class-based proxy; chọn đúng cơ chế là vấn đề **contract**, không chỉ vấn đề performance.

| Điều kiện cấu hình | Loại proxy thường được Spring tạo | Hệ quả |
| --- | --- | --- |
| `proxyInterfaces` chỉ rõ một hoặc nhiều interface, `proxyTargetClass=false` | JDK dynamic proxy | Caller cần dùng interfaces được expose |
| `proxyInterfaces` không đặt nhưng target có interfaces, `autodetectInterfaces=true` | JDK dynamic proxy (tự phát hiện interfaces) | Không kiểm soát rõ danh sách; có thể là default hữu ích |
| `proxyTargetClass=true` | CGLIB class-based proxy | Proxy là subclass của target class phù hợp |
| Target không có interface | CGLIB class-based proxy | JDK proxy không thể proxy class không có interface |

**Giới hạn JDK:** proxy implement interfaces đã khai báo/chọn, không phải một instance của concrete implementation. Cố cast JDK proxy sang `OrderServiceImpl` có thể gây `ClassCastException`. Nếu consumer muốn gọi method không có trên proxy interfaces, method đó không nằm trong contract của JDK proxy.

**Giới hạn CGLIB:** không proxy được final class bằng subclass; final methods và private methods không thể được advice bằng override. Module/package visibility cũng có thể ảnh hưởng class-based proxying. Việc dùng `proxyTargetClass=true` không giải quyết self-invocation.

**Tối ưu thực tế:** nên chọn theo API contract và khả năng proxy đúng target. Spring documentation không coi khác biệt performance giữa JDK/CGLIB là lý do quyết định mặc định. Đo trên workload của ứng dụng nếu có yêu cầu latency đặc biệt.

## 9. Cách tránh exposed raw target và ambiguity injection

Ví dụ mục 5 cố tình đặt target tên `orderTarget` để minh họa. Một ứng dụng thật có thể có hai bean cùng interface: proxy `orderService` và target `orderTarget`. Nếu autowire chỉ theo type:

~~~java
public OrderController(OrderService orderService) {
    this.orderService = orderService;
}
~~~

Spring có thể cần bean name, `@Qualifier("orderService")` hoặc `@Primary` để xác định ứng viên. Nếu caller vô tình nhận `orderTarget`, toàn bộ advice của proxy `orderService` bị bypass.

Một cách cấu hình ngăn lấy nhầm raw target là **đặt target làm anonymous inner bean**:

~~~xml
<bean id="orderService"
      class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="proxyInterfaces" value="demo.OrderService"/>
    <property name="target">
        <bean class="demo.OrderServiceImpl"/>
    </property>
    <property name="interceptorNames">
        <list>
            <value>traceInterceptor</value>
            <value>quoteAdvisor</value>
        </list>
    </property>
</bean>
~~~

Đây là **biến thể thay cho cấu hình ở mục 5**, với hai advice/advisor đã được khai báo bên ngoài. Inner bean không có một top-level bean ID để consumer vô tình `getBean("orderTarget")`. Nó cũng làm bản khai báo proxy dễ đọc theo hướng “một bean public contract”.

Khi cần target được nhiều proxy chia sẻ hoặc cần inspect raw target trong một số test, top-level target bean vẫn hợp lý. Khi đó hãy dùng `autowire-candidate="false"` (nếu cần) và qualifiers/boundaries rõ ràng, thay vì giả định Spring luôn chọn proxy.

## 10. Global advisors với interceptorNames wildcard

Spring `ProxyFactoryBean` hỗ trợ quy ước kết thúc tên bằng dấu `*` để tự đưa nhiều advice/advisor beans có name prefix phù hợp vào interceptor chain, ví dụ:

~~~xml
<bean id="globalAudit"
      class="demo.TraceInterceptor"/>

<bean id="globalQuote"
      class="org.springframework.aop.support.DefaultPointcutAdvisor">
    <property name="advice" ref="quoteInterceptor"/>
    <property name="pointcut" ref="quotePointcut"/>
</bean>

<bean id="orderService"
      class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="target" ref="orderTarget"/>
    <property name="proxyInterfaces" value="demo.OrderService"/>
    <property name="interceptorNames">
        <list>
            <value>global*</value>
        </list>
    </property>
</bean>
~~~

Đây là **biến thể riêng**: nếu dùng nó, phải khai báo đúng các bean `quoteInterceptor`, `quotePointcut`, `orderTarget`; không đặt hai `orderService` trong cùng file. Wildcard là prefix của **tên advisor/advice beans**, không phải tên business methods và không phải Spring `execution(...)` pointcut pattern.

Cơ chế này hữu ích khi cần chung một bộ advisors cho nhiều ProxyFactoryBean. Tuy nhiên, thêm một bean mới có prefix `global` có thể vô tình làm thay đổi chain nhiều proxies. Để tránh cấu hình “tự động lan rộng”, dùng tên cụ thể, convention rõ và integration test cho các advisors thực sự được áp dụng.

`interceptorNames` không phải cách tự động thêm mọi `@Aspect` vào proxy; đó là danh sách bean names rõ ràng để FactoryBean lookup và xếp chain.

## 11. Quan sát ProxyFactoryBean và advisor chain trong runtime

Khi cần chẩn đoán, kiểm tra **object mà caller thực sự đang dùng**:

~~~java
Object service = context.getBean("orderService");

System.out.println(AopUtils.isAopProxy(service));
System.out.println(AopUtils.isJdkDynamicProxy(service));
System.out.println(AopUtils.isCglibProxy(service));
~~~

Snippet cần biến `context` và import `org.springframework.aop.support.AopUtils`. Nếu object là Spring JDK proxy thì `isAopProxy` và `isJdkDynamicProxy` true. Nếu class-based, `isCglibProxy` true; nếu raw target, cả ba thường false.

Để xem các advisors được expose qua `Advised`:

~~~java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;

static void inspectAdvisors(Object bean) {
    if (bean instanceof Advised advised) {
        for (Advisor advisor : advised.getAdvisors()) {
            System.out.println(advisor.getClass().getName());
        }
    }
}
~~~

Đây là một snippet method để đặt trong Java class. Một proxy được cấu hình opaque có thể không expose `Advised`; đừng ép cast nếu `instanceof` false. Danh sách advisors tồn tại cũng chưa đảm bảo mọi method đều match — ví dụ `quoteAdvisor` chỉ chạy cho `quote`, không cho `status`.

Khi truy cập FactoryBean:

~~~java
ProxyFactoryBean factory =
        context.getBean("&orderService", ProxyFactoryBean.class);
System.out.println(factory.isSingleton());
~~~

Đừng dùng `context.getBean("orderService", ProxyFactoryBean.class)`: tên đó trả **proxy product**, nên lookup type là FactoryBean có thể thất bại. Nếu chỉ cần nghiệp vụ, lấy `OrderService` từ context; tránh để các controller/service phụ thuộc `ProxyFactoryBean`.

### Tránh lạm dụng unwrap target

Với proxy implement `Advised`, có thể xem `getTargetSource()`, nhưng gọi `getTarget()` để lấy target thô rồi chạy nghiệp vụ sẽ bỏ qua advisors của proxy. Với pooled/scoped/lazy target source còn có lifecycle acquire/release riêng, nên việc unwrap không đúng cách có thể gây lỗi khác. Để chẩn đoán, ưu tiên `AopUtils.getTargetClass(proxy)` và danh sách advisors.

## 12. Những lỗi cấu hình thường gặp

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `getBean("orderService")` không phải ProxyFactoryBean | Đây là hành vi đúng của `FactoryBean`, product được trả về | Dùng `&orderService` để lấy FactoryBean |
| `getBean("&orderService")` không phải OrderService | `&` dereference factory, không trả service | Inject/getBean bằng tên không có `&` cho business caller |
| Advice không chạy khi gọi target | Caller dùng raw target hoặc `new` | Inject/gọi proxy `orderService` |
| ApplicationContext không tạo bean | `interceptorNames` sai tên, advice không implement contract, proxy interface FQCN sai | Kiểm tra IDs, interfaces và root cause bean creation |
| `interceptorNames` dùng `<ref>` | Thuộc tính chờ bean name strings | Dùng `<value>advisorName</value>` |
| Có `target` và tên target ở cuối `interceptorNames` | Cấu hình target trùng/sai; cách đặt cuối đã deprecated | Giữ target riêng, danh sách chỉ gồm advisor/advice bean names |
| Method `status` không chạy `quoteAdvisor` | Pointcut chỉ match `quote` — đây là hành vi đúng | Kiểm tra `NameMatchMethodPointcut.mappedName` |
| Cast JDK proxy sang concrete class thất bại | Proxy không subclass target | Inject/làm việc theo interface `OrderService` |
| `singleton=false` nhưng trạng thái target vẫn chung | Nhiều proxy products dùng singleton target | Chọn target scope/`TargetSource` đúng |
| `proxyTargetClass=true` với final target | Không thể tạo CGLIB subclass phù hợp | Sửa thiết kế target hoặc dùng interface-based proxy |
| Thêm global advisor khiến log tăng ở nhiều nơi | Wildcard `global*` match advisor bean mới | Kiểm soát prefix, dùng danh sách cụ thể |
| Tưởng `frozen=true` bảo vệ state target | Frozen chỉ khóa AOP configuration | Bảo vệ business state bằng contract/concurrency riêng |
| `@Aspect` và ProxyFactoryBean cùng advise service | Có thể double-proxy hoặc duplicate advice | Kiểm tra và loại cấu hình chồng lặp |
| Self-invocation không chạy advice | `this.method()` trong target không qua proxy | Tách bean/gọi qua Spring-managed proxy |

### Kiểm tra theo trình tự

1. **Bean definition:** đúng class `org.springframework.aop.framework.ProxyFactoryBean`, đặt ID proxy khác target chưa?
2. **Interfaces/target:** `proxyInterfaces` chứa đúng FQCN, target implement interface hoặc tương thích proxy strategy?
3. **Advisors:** `interceptorNames` là strings và tất cả ID thực sự tồn tại trong BeanFactory?
4. **Order:** advisor ngoài/đứng trước có gọi `proceed()` không? Pointcut có match method dự kiến không?
5. **FactoryBean lookup:** `getBean(id)` trả proxy, `getBean("&" + id)` trả FactoryBean?
6. **Proxy identification:** `AopUtils.isAopProxy` và `isJdkDynamicProxy`/`isCglibProxy` có phù hợp cấu hình?
7. **Caller path:** controller/test gọi bean proxy hay target `new`/raw target? Có self-invocation?
8. **Lifecycle:** singleton/prototype scope của proxy, target và advice có đúng nhu cầu thread-safety?
9. **Đa hạ tầng:** có auto-proxy creator khác tạo thêm lớp proxy hoặc advisor trùng không?
10. **Integration test:** gọi một method match và một method không match rồi xác nhận stdout/side effects/chains.

## 13. Khi nào nên chọn ProxyFactoryBean?

Nên dùng nếu cần:

- Định nghĩa **một proxy bean cụ thể** theo interface/target rõ ràng và inject nó như bean bình thường.
- Quản lý `MethodInterceptor`/`Advisor` qua DI, thay đổi cấu hình interceptor chain mà không chỉnh business target.
- Giới hạn advice cho một vài target beans mà vẫn cần điểm nối với `BeanFactory` và `TargetSource`.
- Điều khiển properties AOP factory cấp thấp: singleton/prototype proxy, frozen configuration, custom class loader, target source đặc biệt.
- Giải thích/kiểm thử tường minh đường proxy của một Spring service mà không cần annotation-based aspect discovery.

Thường chọn `@Aspect` + `@EnableAspectJAutoProxy` hoặc Spring XML schema AOP nếu mục tiêu là **các cross-cutting aspects chung** cho nhiều beans theo package/annotation pointcut mà không cần khai báo thủ công từng `ProxyFactoryBean`.

Nếu muốn tạo proxy không thông qua IoC, dùng `ProxyFactory`. Các lựa chọn này không loại trừ nhau về nền tảng Spring AOP; chúng chỉ khác **nơi khai báo và nơi kiểm soát proxy**.

## 14. Tổng kết

`ProxyFactoryBean` kết nối Spring AOP với Spring IoC bằng cách khai báo một **FactoryBean tạo proxy**: target, interface proxy, advisors và thứ tự advice đều được quản lý bởi BeanFactory. Khi consumer inject `orderService`, nó nhận **proxy product**, không phải `ProxyFactoryBean`; muốn inspect chính factory thì dùng `&orderService`. `interceptorNames` là **tên bean dưới dạng chuỗi**, thứ tự quyết định chuỗi interceptor cho các method match.

Điều cần nhớ nhất trong ứng dụng thật: **chỉ các lời gọi đi qua Spring proxy mới kích hoạt advice của proxy ấy**. Giữ API interface và bean ID rõ ràng, tránh cho consumer nhận raw target, kiểm thử cả JDK/CGLIB strategy và thứ tự advisors. Với các mục tiêu thường gặp, ưu tiên cơ chế cấu hình AOP ngắn gọn hơn; dùng `ProxyFactoryBean` khi thực sự cần quyền kiểm soát hạ tầng proxy ở cấp Spring container.

### Tài liệu Spring chính thức

- [Spring Framework — Using the ProxyFactoryBean to Create AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html).
- [Spring Framework 6.1 — ProxyFactoryBean Javadoc](https://docs.spring.io/spring-framework/docs/6.1.11/javadoc-api/org/springframework/aop/framework/ProxyFactoryBean.html).
- [Spring Framework — Creating AOP Proxies Programmatically with ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html).
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- [Spring Framework — Advice API](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="programmatic-proxy-creation-005">Phân biệt FactoryBean với đối tượng proxy mà nó cung cấp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt FactoryBean với đối tượng proxy mà nó cung cấp trong Spring AOP

## 1. Tại sao dễ nhầm?

Trong Spring AOP, `org.springframework.aop.framework.ProxyFactoryBean` là một bean **dùng để tạo AOP proxy**. Bean này triển khai `org.springframework.beans.factory.FactoryBean<Object>`. Điều đặc biệt là khi khai báo nó dưới một tên, chẳng hạn `greeter`, Spring container mặc định trả về **sản phẩm do FactoryBean tạo** (AOP proxy), chứ không trả về bản thân `ProxyFactoryBean`.

Vì thế có ít nhất ba đối tượng cần tách bạch:

1. **Factory:** đối tượng `ProxyFactoryBean` nắm cấu hình target, advisor/advice, loại proxy, scope sản phẩm.
2. **Product:** đối tượng **AOP proxy** được `ProxyFactoryBean.getObject()` cung cấp cho client khi tra cứu bean bằng tên thông thường.
3. **Target:** đối tượng nghiệp vụ thật mà proxy chuyển lời gọi đến sau khi chạy interceptor chain.

Đây là kiến thức cụ thể của **Spring AOP và Spring IoC**; mục tiêu của bài là hiểu cơ chế tạo và lấy proxy trong Spring, không mở rộng sang các dạng proxy hoặc Aspect ngoài Spring.

Sau bài học, bạn có thể đọc đúng kết quả của `ApplicationContext.getBean(...)`, truy xuất factory với tiền tố `&`, kiểm tra đối tượng nhận được, hiểu sự khác nhau giữa vòng đời factory và vòng đời proxy, cũng như tránh vô tình dùng target trực tiếp khiến advice bị bỏ qua.

## 2. Sơ đồ hai tầng: Spring container và AOP proxy

Giả sử Spring đăng ký một bean tên `greeter` với class là `ProxyFactoryBean`.

```text
       ApplicationContext / BeanFactory
                      |
              bean name: "greeter"
                      |
          +-----------+-----------+
          |                       |
    getBean("greeter")      getBean("&greeter")
          |                       |
          v                       v
   FactoryBean.getObject()   ProxyFactoryBean
          |                  (đối tượng factory)
          v
    AOP PROXY [product]
          |
    MethodInterceptor(s)
          |
          v
    GreeterImpl [target]
```

Có thể đọc theo hai câu hỏi khác nhau:

- **“Ứng dụng sẽ gọi nghiệp vụ qua đối tượng nào?”** Dùng `getBean("greeter")`; nhận AOP proxy mang API của dịch vụ.
- **“Tôi muốn xem hoặc cấu hình đối tượng làm nhiệm vụ tạo proxy?”** Dùng `getBean("&greeter")`; nhận chính `ProxyFactoryBean`.

Dấu `&` là **tiền tố tra cứu đặc biệt của Spring BeanFactory** (`BeanFactory.FACTORY_BEAN_PREFIX`); nó không phải một phần của tên method nghiệp vụ, không phải ký hiệu AOP pointcut và không phải toán tử Java.

### Hợp đồng FactoryBean<T>

`FactoryBean<T>` là một giao diện Spring IoC, với các phương thức cốt lõi:

```java
T getObject() throws Exception;
Class<?> getObjectType();
boolean isSingleton();
```

- `getObject()` cung cấp sản phẩm. Với `ProxyFactoryBean`, sản phẩm là proxy Spring AOP.
- `getObjectType()` cho container biết kiểu của sản phẩm, giúp hỗ trợ tra cứu theo kiểu và autowiring nếu xác định được.
- `isSingleton()` mô tả tính singleton của **sản phẩm** do factory cung cấp. Nó không đồng nghĩa với việc đối tượng `ProxyFactoryBean` được khai báo là singleton trong container.

Không phải cứ một class có tên “Factory” thì Spring tự động áp dụng cơ chế này: class phải triển khai giao diện `FactoryBean` và được đăng ký trong BeanFactory. `ProxyFactoryBean` là triển khai chuyên dụng của Spring AOP.

## 3. So sánh chính xác các đối tượng

| Đối tượng | Cách lấy | Vai trò | Có chạy AOP advice khi gọi service method qua nó? |
| --- | --- | --- | --- |
| `ProxyFactoryBean` (factory) | `context.getBean("&greeter")` | Giữ cấu hình và cung cấp proxy qua `getObject()` | Không phải service proxy; không gọi method nghiệp vụ trên factory |
| AOP proxy (product) | `context.getBean("greeter")` | Nhận cuộc gọi client, áp dụng interceptor chain, chuyển đến target | **Có**, nếu method/pointcut/advisor khớp và lời gọi đi qua proxy |
| Target thực `GreeterImpl` | Đối tượng được cấu hình bằng `setTarget(...)` hoặc target bean riêng | Chứa logic nghiệp vụ | **Không tự chạy advice** nếu bị gọi trực tiếp ngoài proxy |
| `ProxyFactory` (khác class) | `new ProxyFactory(target)`, sau đó `getProxy()` | API lập trình tạo proxy, không tự mang hợp đồng `FactoryBean` | Sản phẩm của `getProxy()` có thể áp dụng advice |

Hai tên dễ nhầm: `ProxyFactoryBean` và `ProxyFactory` đều thuộc `org.springframework.aop.framework` nhưng có vai trò khác:

- `ProxyFactoryBean` là **FactoryBean được Spring container quản lý**. Khi tham chiếu bean bằng tên thông thường, container xử lý tính gián tiếp từ factory tới proxy.
- `ProxyFactory` phù hợp khi muốn tạo proxy trực tiếp bằng code; bạn nhận proxy qua `factory.getProxy()` rồi tự sử dụng hoặc đăng ký đối tượng đó. Bản thân `ProxyFactory` không phải sản phẩm của `FactoryBean.getObject()`.

Một `@Bean` trả về `ProxyFactoryBean` có thể khiến tên method gây hiểu nhầm: chẳng hạn method Java tên `greeterFactory()` nhưng khai báo `@Bean("greeter")` thì **tên đăng ký là `greeter`**. Tra cứu bằng tên luôn dựa trên BeanFactory, không dựa vào tên class hay tên biến Java.

## 4. Ví dụ đầy đủ Java 17+ với Spring ApplicationContext

Ví dụ sau dùng `AnnotationConfigApplicationContext` để chứng minh bằng **identity**, kiểu đối tượng và lời gọi advice. Chương trình khai báo hai `ProxyFactoryBean`: một factory trả về proxy singleton (mặc định), một factory trả về proxy mới mỗi lần tra cứu. Cả hai đều được tạo và quản lý bởi cùng một Spring context.

### 4.1. Tạo dự án Gradle

**`settings.gradle`:**

```groovy
rootProject.name = 'spring-aop-factorybean-demo'
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
    implementation 'org.springframework:spring-context:6.1.21'
}

application {
    mainClass = 'example.FactoryBeanDemo'
}
```

`spring-context` kéo theo các thành phần Spring cần cho ví dụ Spring AOP; trong dự án thực tế, quản lý version thống nhất bằng Spring BOM hoặc Spring Boot dependency management để tránh trộn phiên bản module Spring.

### 4.2. Chương trình chạy được

**`src/main/java/example/FactoryBeanDemo.java`:**

```java
package example;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class FactoryBeanDemo {

    public interface Greeter {
        String hello(String name);
    }

    static final class GreeterImpl implements Greeter {
        @Override
        public String hello(String name) {
            System.out.println("TARGET hello " + name);
            return "Xin chao " + name;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AppConfig {

        @Bean("loggingAdvice")
        MethodInterceptor loggingAdvice() {
            return invocation -> {
                System.out.println("ADVICE before");
                try {
                    return invocation.proceed();
                } finally {
                    System.out.println("ADVICE after");
                }
            };
        }

        private ProxyFactoryBean configuredFactory(boolean singletonProduct) {
            ProxyFactoryBean factory = new ProxyFactoryBean();
            factory.setTarget(new GreeterImpl());
            factory.setInterfaces(Greeter.class);
            factory.setInterceptorNames("loggingAdvice");
            factory.setSingleton(singletonProduct);
            return factory;
        }

        @Bean("greeter")
        ProxyFactoryBean greeterFactory() {
            return configuredFactory(true);
        }

        @Bean("freshGreeter")
        ProxyFactoryBean freshGreeterFactory() {
            return configuredFactory(false);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            Object product1 = context.getBean("greeter");
            Object product2 = context.getBean("greeter");
            Object factory1 = context.getBean("&greeter");
            Object factory2 = context.getBean("&greeter");

            if (!(factory1 instanceof ProxyFactoryBean)) {
                throw new AssertionError("Expected ProxyFactoryBean");
            }
            if (!(product1 instanceof Greeter) || !AopUtils.isAopProxy(product1)) {
                throw new AssertionError("Expected Greeter AOP proxy");
            }

            System.out.println("product.isProxy=" + AopUtils.isAopProxy(product1));
            System.out.println("product.isFactory=" + (product1 instanceof ProxyFactoryBean));
            System.out.println("factory.isFactory=" + (factory1 instanceof ProxyFactoryBean));
            System.out.println("product.same=" + (product1 == product2));
            System.out.println("factory.same=" + (factory1 == factory2));
            System.out.println("product.equalsFactory=" + (product1 == factory1));

            Object fresh1 = context.getBean("freshGreeter");
            Object fresh2 = context.getBean("freshGreeter");
            Object freshFactory1 = context.getBean("&freshGreeter");
            Object freshFactory2 = context.getBean("&freshGreeter");

            System.out.println("fresh.product.same=" + (fresh1 == fresh2));
            System.out.println("fresh.factory.same=" + (freshFactory1 == freshFactory2));
            System.out.println("fresh.product.singleton=" + context.isSingleton("freshGreeter"));

            String result = ((Greeter) product1).hello("Lan");
            System.out.println("result=" + result);
        }
    }
}
```

Chạy từ thư mục có `build.gradle`:

```bash
gradle run
```

Nếu dự án dùng Gradle Wrapper, thay bằng `./gradlew run` (Linux/macOS) hoặc `.\gradlew.bat run` (PowerShell trên Windows). Phần **stdout của chương trình** (không tính log Gradle) là:

```text
product.isProxy=true
product.isFactory=false
factory.isFactory=true
product.same=true
factory.same=true
product.equalsFactory=false
fresh.product.same=false
fresh.factory.same=true
fresh.product.singleton=false
ADVICE before
TARGET hello Lan
ADVICE after
result=Xin chao Lan
```

### 4.3. Giải thích những gì chương trình chứng minh

1. `context.getBean("greeter")` lấy **AOP proxy**. `AopUtils.isAopProxy(product1)` trả `true`, và proxy triển khai interface `Greeter`. Nó không phải `ProxyFactoryBean`.
2. `context.getBean("&greeter")` lấy **factory** đang tạo proxy. Vì factory là bean có scope singleton mặc định, hai lần tra cứu có cùng identity.
3. Với `setSingleton(true)`, sản phẩm của `ProxyFactoryBean` được dùng lại: `product1 == product2` là `true`.
4. Với `setSingleton(false)`, cùng một factory singleton có thể tạo **nhiều proxy khác nhau**: `fresh1 == fresh2` là `false`, nhưng hai lần lấy `&freshGreeter` vẫn nhận cùng factory.
5. `context.isSingleton("freshGreeter")` là `false` vì đang hỏi **sản phẩm**. Muốn hỏi factory có phải singleton bean không, dùng `context.isSingleton("&freshGreeter")`, hoặc kiểm tra scope/identity trực tiếp.
6. Chỉ khi client gọi `((Greeter) product1).hello("Lan")` qua proxy thì `MethodInterceptor` chạy quanh target. Ba dòng `ADVICE before`, `TARGET hello Lan`, `ADVICE after` chứng minh đây là một cuộc gọi AOP thực sự.

**Phân biệt thêm:** `setSingleton(false)` tạo proxy product mới qua factory, **không nhất thiết tạo target mới**. Ở ví dụ này, `setTarget(new GreeterImpl())` được gọi một lần khi tạo từng `ProxyFactoryBean`, nên các proxy do **cùng** factory tạo có thể dùng chung target. Nếu cần target mới theo từng lượt, hãy xem `TargetSource`/bean scope phù hợp; chỉ đổi cờ singleton của factory là chưa đủ để tạo target riêng.

## 5. “FactoryBean singleton” khác “proxy singleton” thế nào?

Có hai cơ chế vòng đời cùng xuất hiện:

**(A) Scope của bean factory trong container:** `@Bean("greeter")` mặc định có singleton scope, nên mỗi lần `getBean("&greeter")` trả về cùng một đối tượng `ProxyFactoryBean` do BeanFactory quản lý.

**(B) Singleton của sản phẩm từ FactoryBean:** `ProxyFactoryBean.setSingleton(true/false)` điều khiển việc `getObject()` cung cấp lại cùng proxy hay tạo proxy khác.

| Factory bean trong container | Sản phẩm proxy | Kết quả |
| --- | --- | --- |
| Factory singleton (mặc định) | `setSingleton(true)` (mặc định) | Cùng factory, cùng proxy |
| Factory singleton | `setSingleton(false)` | Cùng factory, các proxy product có identity khác nhau |
| Factory có scope prototype | Tùy cài đặt sản phẩm | Mỗi lượt tạo factory cũng có thể tạo cấu hình và target mới; xét riêng vòng đời factory và product |

Với một `FactoryBean` có sản phẩm singleton, Spring container có thể cache sản phẩm do `getObject()` trả về. Chi tiết cache thuộc hạ tầng BeanFactory; bài học không cần giả định bao nhiêu lần `getObject()` được gọi bên trong. Thứ nên dựa vào là **hợp đồng scope và identity nhận được khi tra cứu**.

Cũng không nên diễn dịch `setSingleton(false)` của `ProxyFactoryBean` thành “target sẽ được nhân bản” hay “target của mỗi request độc lập”. Target do `TargetSource` quản lý. Ví dụ mục 4 có chủ ý dùng target gắn cố định để làm rõ sự khác nhau này.

## 6. Nếu gọi getObject() trực tiếp thì sao?

`ProxyFactoryBean.getObject()` là phương thức cung cấp proxy, nhưng khi sử dụng nó như Spring bean, cách thông thường là để container gọi gián tiếp thông qua `getBean("greeter")`. Nếu lấy factory qua `getBean("&greeter")` rồi tự gọi `getObject()`, bạn đang can thiệp ở lớp factory; cách gọi này không phải API lấy service cho mã nghiệp vụ.

Ví dụ minh họa, giả sử `context` là `ApplicationContext` đang hoạt động:

```java
Greeter service = context.getBean("greeter", Greeter.class);
ProxyFactoryBean factory = context.getBean("&greeter", ProxyFactoryBean.class);

Object productFromFactory = factory.getObject(); // API factory, không gọi method nghiệp vụ
service.hello("Lan");                           // cuộc gọi qua AOP proxy
```

Trong trường hợp sản phẩm là singleton, `getObject()` trả proxy singleton mà factory quản lý. Với sản phẩm không phải singleton, gọi `getObject()` trực tiếp cũng có thể tạo proxy mới. Đừng thiết kế ứng dụng dựa trên việc trộn tra cứu container với `getObject()` thủ công khi không có nhu cầu cấu hình/kiểm thử factory rõ ràng.

**Điểm đáng nhớ:** class `ProxyFactoryBean` không sở hữu method nghiệp vụ `hello(...)`. Để gọi `hello(...)`, bạn cần **proxy/target triển khai `Greeter`**, không phải factory.

## 7. Cấu hình FactoryBean bằng interceptorNames và target

`ProxyFactoryBean` hỗ trợ các thuộc tính Spring AOP quan trọng:

- `target` hoặc `targetName`: xác định đối tượng/lấy đối tượng mà proxy sẽ chuyển tiếp lời gọi. Nếu cần tham chiếu một bean target trong container, dùng cấu hình target phù hợp.
- `proxyInterfaces` / `setInterfaces(...)`: xác định interface của proxy. Với interface và `proxyTargetClass=false`, Spring có thể tạo JDK dynamic proxy. Khi yêu cầu class proxy (`proxyTargetClass=true`), Spring dùng proxy dựa trên subclass khi có thể.
- `interceptorNames`: **tên** các bean advisor/advice trong cùng BeanFactory; thứ tự các tên có ý nghĩa đối với chuỗi interceptor. Các thành phần advisor, advice cũng có thể do IoC container quản lý.
- `singleton`: chọn tái sử dụng một proxy product hoặc tạo các proxy product mới.
- `frozen`, `exposeProxy` và những cấu hình kế thừa: phục vụ các nhu cầu AOP cụ thể; chỉ bật khi hiểu rõ ảnh hưởng.

Trong ví dụ Java:

```java
factory.setTarget(new GreeterImpl());
factory.setInterfaces(Greeter.class);
factory.setInterceptorNames("loggingAdvice");
factory.setSingleton(true);
```

`loggingAdvice` là **tên bean**, không phải chuỗi tùy ý và không phải tên phương thức sẽ bị advice. `ProxyFactoryBean` giải quyết tên này trong BeanFactory. Nếu gõ sai tên bean hoặc đối tượng tương ứng không phải loại advice/advisor hỗ trợ, việc khởi tạo/tạo proxy có thể thất bại.

Khi dùng cấu hình XML Spring AOP cũ, hình dạng tương tự:

```xml
<bean id="greeter" class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="proxyInterfaces" value="example.Greeter"/>
    <property name="target" ref="greeterTarget"/>
    <property name="interceptorNames">
        <list>
            <value>loggingAdvice</value>
        </list>
    </property>
</bean>
```

Đây là **mảnh cấu hình minh họa**: để chạy được, context cần có các bean `greeterTarget`, `loggingAdvice` và interface `example.Greeter` thực tế. Khi dùng Java config ở mục 4, không cần file XML này.

Khi cấu hình target riêng như bean `greeterTarget`, việc gọi `getBean("greeterTarget")` sẽ trả về **target thô** nếu nó không được proxy bởi cơ chế khác. Gọi trực tiếp target như vậy có thể bỏ qua advice của proxy `greeter`; vì thế mã nghiệp vụ nên inject bean tên `greeter` hoặc inject đúng kiểu dịch vụ được expose qua proxy.

## 8. Nhận diện trong ứng dụng thực tế

### 8.1. Inject service hay inject factory?

Nhu cầu nghiệp vụ thường là:

```java
// Ví dụ bên trong một bean Spring khác:
@Autowired
Greeter greeter;
```

Cú pháp trên là **mảnh minh họa** và cần import `org.springframework.beans.factory.annotation.Autowired`. Spring sẽ cố inject **sản phẩm proxy theo kiểu Greeter** nếu có đúng bean đủ điều kiện. Nếu nhiều bean cùng kiểu, cần `@Qualifier` hoặc bean name để phân giải.

Để nhận **chính factory**, mã quản trị/cấu hình/kiểm thử có thể dùng `context.getBean("&greeter", ProxyFactoryBean.class)`. Không nên inject `ProxyFactoryBean` rồi gọi method nghiệp vụ trên nó: factory là đối tượng tạo proxy, không phải API nghiệp vụ.

Một `FactoryBean` có thể báo `getObjectType()` để container biết kiểu của sản phẩm khi chưa tạo proxy. Vì vậy kiểu khai báo của method `@Bean` trả về `ProxyFactoryBean` không có nghĩa tất cả injection theo kiểu của tên bean sẽ nhận factory.

### 8.2. Thấy tên bean `greeter` trong cấu hình nhưng nhận class lạ

Proxy tạo bởi JDK là một class proxy do JVM sinh ra, nên `product.getClass()` không nhất thiết là `GreeterImpl.class`; nếu dùng proxy subclass, class runtime có thể là subclass do Spring tạo. Kiểm tra đúng ý định bằng:

```java
Object bean = context.getBean("greeter");
boolean advised = AopUtils.isAopProxy(bean);
boolean supportsApi = bean instanceof Greeter;
```

Đoạn minh họa giả định đã có `context`, `Greeter` và import `AopUtils` như mục 4. Không dựa vào tên class proxy chứa ký hiệu đặc biệt vì tên class generated là chi tiết triển khai.

### 8.3. Tại sao advice không chạy dù target chạy?

Kiểm tra từng bước:

1. Có gọi **đối tượng proxy** qua `getBean("greeter")` hoặc injection thích hợp không, hay đang gọi `new GreeterImpl()` / bean target trực tiếp?
2. Bean tra cứu được có phải Spring AOP proxy không (`AopUtils.isAopProxy(...)`)?
3. Factory có đăng ký đúng advice/advisor, đúng bean name trong `interceptorNames` hoặc danh sách advisor không?
4. Advisor có match method và class của target không? Với pointcut động, đối số có làm matcher trả `false` không?
5. Có lời gọi **self-invocation** như `this.hello(...)` từ một method khác trong cùng target không? Lời gọi này thường không đi qua proxy.
6. Nếu proxy theo JDK interface, code có đang gọi method qua interface mà proxy expose không?

Một factory tồn tại không tự chứng minh rằng advice chạy. Chỉ cuộc gọi đi qua **proxy có advisor phù hợp** mới chứng minh được hành vi AOP.

### 8.4. Dùng dấu & sai vị trí

`&` chỉ dùng khi bạn **cố ý lấy factory**. Ví dụ:

```java
context.getBean("greeter");   // sản phẩm proxy
context.getBean("&greeter");  // factory (ProxyFactoryBean)
```

Nếu tên bean thông thường không do `FactoryBean` quản lý, việc dùng tiền tố `&` không có nghĩa tự biến bean đó thành factory. Spring có thể báo lỗi kiểu `BeanIsNotAFactoryException` khi yêu cầu factory ở một bean không phải FactoryBean.

### 8.5. Nhầm ProxyFactoryBean với FactoryBean nói chung

`FactoryBean` là cơ chế Spring IoC có thể cung cấp rất nhiều loại sản phẩm, không nhất thiết là AOP proxy. `ProxyFactoryBean` chỉ là **một triển khai dành riêng cho Spring AOP**. Tính chất “tra cứu tên bean nhận sản phẩm; tra cứu `&name` nhận factory” đến từ **hợp đồng FactoryBean**; tính chất “sản phẩm là proxy chạy interceptor chain” đến từ **ProxyFactoryBean/Spring AOP**.

## 9. Bài thực hành kiểm chứng thêm

Dựa trên chương trình mục 4, thử theo thứ tự:

1. Đổi `factory.setSingleton(true)` của bean `greeter` thành `false`. Khi chạy lại, dự kiến `product.same=false`; `factory.same=true` vì scope của bean factory không đổi.
2. Bỏ `factory.setInterceptorNames("loggingAdvice")`. Với `ProxyFactoryBean`, danh sách interceptorNames là cấu hình quan trọng trong môi trường BeanFactory; kết quả có thể là proxy không có advice hoặc cấu hình không hợp lệ tùy cách factory được khởi tạo. Kiểm chứng bằng trace và đọc thông báo lỗi thực tế, **không** suy luận rằng thay đổi này khiến target mất đi.
3. Tạo một target bean riêng để so sánh `getBean("greeterTarget")` với `getBean("greeter")`. Target thô không mang chain của proxy `greeter`; khi gọi cùng method, hãy quan sát sự khác biệt của dòng `ADVICE`.
4. Đổi vị trí của hai advisor trong `setInterceptorNames("firstAdvice", "secondAdvice")` sau khi đăng ký cả hai bean. Ghi dòng `before` và `after`; lời gọi interceptor được lồng nhau nên trên đường ra thứ tự sẽ đảo.
5. So sánh trực tiếp `context.isSingleton("freshGreeter")` với `context.isSingleton("&freshGreeter")` để củng cố khác biệt giữa scope sản phẩm và scope factory.

Mỗi thử nghiệm nên dự đoán identity và trace **trước**, rồi kiểm tra bằng `==`, `AopUtils.isAopProxy` và log của advice **sau**. Các đoạn mở rộng là bài thực hành dựa trên cấu hình đầy đủ, không phải lời cam kết mọi thay đổi cấu hình đều khởi tạo thành công.

## 10. Câu hỏi tự kiểm tra

1. Đăng ký `@Bean("orderService")` trả `ProxyFactoryBean`. `getBean("orderService")` trả về gì? **AOP proxy product**.
2. Muốn lấy chính `ProxyFactoryBean` đó để xem cấu hình? **`getBean("&orderService")`**.
3. `factory.setSingleton(false)` có bắt buộc mỗi proxy mới chứa target mới không? **Không**. Factory có thể tạo nhiều proxy dùng chung một target.
4. Factory bean singleton có đồng nghĩa sản phẩm cũng singleton không? **Không**; phải xét `FactoryBean.isSingleton()` hoặc `ProxyFactoryBean.setSingleton(...)`.
5. `ProxyFactoryBean` có phải `ProxyFactory` không? **Không**. Cái đầu là FactoryBean của Spring container, cái sau là API lập trình tạo proxy trực tiếp.
6. Gọi `new GreeterImpl().hello("Lan")` có chạy `loggingAdvice` của proxy `greeter` không? **Không**, vì không đi qua proxy.
7. Cách nhanh nhất để kiểm chứng object nhận từ Spring có phải AOP proxy? **`AopUtils.isAopProxy(bean)`**, kết hợp kiểm tra interface được expose và trace lời gọi.

## 11. Tài liệu chính thức

- [Spring Reference — Using the ProxyFactoryBean to Create AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html)
- [Spring Reference — Creating AOP Proxies with ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html)
- [Spring Reference — Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html)
- [Spring Framework — ProxyFactoryBean API](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/aop/framework/ProxyFactoryBean.html)
- [Spring Framework — FactoryBean API](https://docs.spring.io/spring-framework/docs/6.1.21/javadoc-api/org/springframework/beans/factory/FactoryBean.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="programmatic-proxy-creation-006">interceptorNames và thứ tự cấu hình trong ProxyFactoryBean</a>

<details>
<summary>Xem chi tiết</summary>

# interceptorNames và thứ tự cấu hình trong ProxyFactoryBean (Spring AOP)

## 1. Vì sao phải quan tâm đến `interceptorNames`?

Khi tạo proxy thông qua Spring container bằng `org.springframework.aop.framework.ProxyFactoryBean`, thuộc tính **`interceptorNames`** là danh sách **tên Spring bean** của các `Advice`, `MethodInterceptor` hoặc `Advisor` cần được gắn vào proxy. **Thứ tự các tên có ý nghĩa:** interceptor/advisor xuất hiện trước sẽ được xử lý trước khi lời gọi đi tiếp vào chuỗi, miễn là pointcut của advisor đó khớp phương thức đang gọi.

Ví dụ một phương thức `checkout()` cần vừa ghi log vừa được áp dụng một advisor kiểm tra nghiệp vụ: nếu logging ở đầu danh sách, nó có thể bao ngoài các interceptor phía sau. Khi logic sau `proceed()` chạy, thứ tự kết thúc thường ngược lại thứ tự bắt đầu.

Bài này chỉ đi sâu vào **Spring AOP với `ProxyFactoryBean`**, không mở rộng sang lý thuyết AspectJ weaving, hoặc thứ tự annotation `@Order` giữa các method advice của một `@Aspect`. Đây là cơ chế proxy được cấu hình bằng Spring Framework API/bean definitions.

Sau khi học, bạn có thể:

- Phân biệt `ProxyFactoryBean` và `ProxyFactory`, hiểu tại sao `interceptorNames` chứa chuỗi tên bean thay vì object references.
- Cấu hình `interceptorNames` bằng XML hoặc Java, đọc và điều chỉnh thứ tự advisor.
- Dự đoán trình tự `before → target → after` khi nhiều `MethodInterceptor` lồng nhau.
- Sử dụng mẫu `global*` để gom các bean theo tiền tố mà không nhầm với matching method của pointcut.
- Kiểm thử thứ tự advisor bằng `Advised.getAdvisors()` và kết quả thực thi qua Spring proxy.
- Hiểu hệ quả của scope `singleton`/`prototype`, lời gọi trực tiếp target và các trường hợp cấu hình sai.

## 2. Vai trò của `ProxyFactoryBean` trong Spring container

`ProxyFactoryBean` là một `FactoryBean<Object>`: Spring container quản lý đối tượng factory này, còn khi client lấy bean theo tên thông thường, container trả về **AOP proxy** được tạo bởi factory. Ta cấu hình target, các interface proxy và danh sách advice/advisor theo tên bean.

```text
Spring BeanFactory / ApplicationContext
    |
    |-- "orderTarget" ------------------------> OrderService thật
    |-- "traceInterceptor" ------------------> MethodInterceptor bean
    |-- "checkoutAdvisor" ------------------> PointcutAdvisor bean
    |
    └-- "orderProxy" ------------------------> ProxyFactoryBean
            target = orderTarget
            interceptorNames = ["traceInterceptor", "checkoutAdvisor"]
            |
            └── getObject() -----------------> Spring AOP proxy
                                                  |
Client: context.getBean("orderProxy") → proxy.checkout() → advisor chain → target
```

**Hai cách lấy bean:**

- `context.getBean("orderProxy")`: nhận **proxy** do `FactoryBean` sản xuất.
- `context.getBean("&orderProxy")`: nhận chính **`ProxyFactoryBean`** (tiền tố `&` là quy ước của Spring `FactoryBean`), thuận tiện để xem cấu hình.

`ProxyFactoryBean` khác `ProxyFactory`: `ProxyFactory` tạo proxy trực tiếp bằng Java code rồi gọi `getProxy()`; `ProxyFactoryBean` kết nối với Spring `BeanFactory`, nhận danh sách tên bean qua `setInterceptorNames(...)`, giải quyết các bean đó và cung cấp proxy dưới một bean name.

### 2.1. Dữ liệu mà `interceptorNames` nhận

Trong Spring, chữ ký tương ứng là:

```java
// API Spring Framework: org.springframework.aop.framework.ProxyFactoryBean
public void setInterceptorNames(String... interceptorNames);
```

Đây là trích **chữ ký**, không phải một chương trình Java độc lập.

Một số giá trị hợp lệ về mặt ý nghĩa:

| Phần tử | Là gì trong BeanFactory? | Tác dụng |
| --- | --- | --- |
| `"auditInterceptor"` | Bean triển khai `MethodInterceptor` | Spring bao advice thành advisor phù hợp và gắn vào proxy |
| `"checkoutAdvisor"` | Bean triển khai `Advisor`, ví dụ `DefaultPointcutAdvisor` | Spring dùng pointcut của advisor để chọn method |
| `"beforeAdvice"` | Bean advice có kiểu được Spring hỗ trợ | Spring dùng advisor adapter nếu phù hợp |
| `"global*"` | Mẫu tên bean kết thúc bằng `*` | Mở rộng các bean interceptor/advisor có tên bắt đầu bằng `global` |
| `"*"` | Mẫu không có tiền tố | Có thể thu thập mọi bean advice/advisor phù hợp; cần tránh mở rộng ngoài ý muốn |

Các tên được tra trong bean factory hiện tại, cũng có thể tra trong ancestor factory theo cơ chế Spring. Đây là **tên bean trong container**, không phải tên class, tên phương thức Java hay biểu thức pointcut.

Spring lưu các tên dưới dạng `String[]` vì việc tra cứu bean khi xây dựng proxy quan trọng với scope: ví dụ `ProxyFactoryBean.singleton=false` có thể tạo nhiều proxy và, nếu advice/target được định nghĩa đúng dạng prototype, cần lấy instance tương ứng cho mỗi proxy. Nếu nhét object reference cố định vào danh sách, ý nghĩa vòng đời của một số cấu hình sẽ bị mất.

**Chú ý:** `interceptorNames` không phải nơi khai báo target khi đã dùng `target`, `targetName` hoặc `targetSource`. Một số API cũ cho phép tên target đứng cuối danh sách, nhưng cách này không được khuyến khích và đã được đánh dấu deprecated trong Javadoc mới. Hãy cấu hình target ở thuộc tính riêng.

## 3. Thứ tự được thực thi như thế nào?

Giả sử hai advice dạng `MethodInterceptor` đều khớp `checkout()` và mỗi interceptor gọi `invocation.proceed()` một lần:

```text
interceptorNames = ["outerInterceptor", "innerInterceptor"]

Client
  └─ outer:before
       └─ inner:before
            └─ target.checkout()
       └─ inner:after
  └─ outer:after
```

Interceptor đứng **đầu chain** chạy phần trước `proceed()` sớm hơn, rồi bao quanh interceptor đứng phía sau. Đó là cơ sở giải thích thứ tự log đảo chiều khi kết thúc.

Nếu đảo thứ tự:

```text
interceptorNames = ["innerInterceptor", "outerInterceptor"]

Client
  └─ inner:before
       └─ outer:before
            └─ target.checkout()
       └─ outer:after
  └─ inner:after
```

Tuy nhiên, mỗi `PointcutAdvisor` còn kiểm tra pointcut. Một advisor không khớp method sẽ bị loại khỏi chain của lời gọi đó. Nếu `outerInterceptor` là interceptor trực tiếp, không có pointcut chọn lọc, nó có thể chạy với cả `checkout()` và `cancel()`; nếu `innerInterceptor` được bọc bởi một advisor chỉ chọn `checkout()`, nó sẽ không chạy ở `cancel()`.

Để có kết quả “after” ngay cả khi target ném ngoại lệ, dùng `try/finally` bao quanh `proceed()`. Nếu chỉ đặt một dòng sau `proceed()` mà không có `finally`, dòng đó **không chạy** khi `proceed()` ném exception.

## 4. Ví dụ có thể chạy: cấu hình `ProxyFactoryBean` bằng XML

Ví dụ Spring Framework 6.x, Java 17+; có ba lớp Java, một file XML và chương trình chính. Không cần `@Aspect`, không cần auto-proxy và không cần một web server để chứng minh `interceptorNames`.

### 4.1. Dependencies

Nếu dự án đang dùng Spring Boot 3.x và Spring dependency management, có thể sử dụng đoạn Gradle:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
```

Đây là đoạn **thêm vào `build.gradle` đã cấu hình sẵn**, không phải toàn bộ bản build. `spring-boot-starter-aop` cung cấp các Spring AOP API; cấu hình XML bên dưới được `spring-context` xử lý. Nếu không dùng Boot, cần khai báo các Spring modules và phiên bản tương thích cho `spring-context`, `spring-aop`, cũng như thư viện kiểm thử.

### 4.2. Giao diện dịch vụ

Tạo `src/main/java/demo/aop/OrderOperations.java`:

```java
package demo.aop;

public interface OrderOperations {
    String checkout(String orderId);
    String cancel(String orderId);
}
```

### 4.3. Target thực tế

Tạo `src/main/java/demo/aop/OrderService.java`:

```java
package demo.aop;

public class OrderService implements OrderOperations {
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
```

### 4.4. Một Advice có nhãn để quan sát thứ tự

Tạo `src/main/java/demo/aop/TraceInterceptor.java`:

```java
package demo.aop;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class TraceInterceptor implements MethodInterceptor {

    private final String label;

    public TraceInterceptor(String label) {
        this.label = label;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String method = invocation.getMethod().getName();
        System.out.println(label + ":before " + method);
        try {
            return invocation.proceed();
        } finally {
            System.out.println(label + ":after " + method);
        }
    }
}
```

`TraceInterceptor` không phụ thuộc vào tên method; nó ghi log cho bất cứ lời gọi nào mà advisor chứa nó được chọn. Chỉ có `Pointcut` trong `checkoutAdvisor` bên dưới mới làm interceptor “inner” bỏ qua `cancel()`.

### 4.5. Bean definitions và `interceptorNames`

Tạo `src/main/resources/aop-interceptor-order.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd">

    <!-- Dịch vụ thật, chưa qua proxy. -->
    <bean id="orderTarget" class="demo.aop.OrderService"/>

    <!-- Hai interceptor là các bean riêng, được trỏ tới bằng tên. -->
    <bean id="outerInterceptor" class="demo.aop.TraceInterceptor">
        <constructor-arg value="outer"/>
    </bean>

    <bean id="innerInterceptor" class="demo.aop.TraceInterceptor">
        <constructor-arg value="inner"/>
    </bean>

    <!-- Pointcut chỉ khớp tên phương thức checkout. -->
    <bean id="checkoutPointcut"
          class="org.springframework.aop.support.NameMatchMethodPointcut">
        <property name="mappedName" value="checkout"/>
    </bean>

    <!-- PointcutAdvisor = checkoutPointcut + innerInterceptor. -->
    <bean id="checkoutAdvisor"
          class="org.springframework.aop.support.DefaultPointcutAdvisor">
        <property name="pointcut" ref="checkoutPointcut"/>
        <property name="advice" ref="innerInterceptor"/>
    </bean>

    <bean id="orderProxy"
          class="org.springframework.aop.framework.ProxyFactoryBean">
        <property name="target" ref="orderTarget"/>
        <property name="proxyInterfaces">
            <list><value>demo.aop.OrderOperations</value></list>
        </property>
        <property name="interceptorNames">
            <list>
                <value>outerInterceptor</value>
                <value>checkoutAdvisor</value>
            </list>
        </property>
    </bean>
</beans>
```

Đọc kỹ XML:

1. `orderTarget` là target thực; `orderProxy` là `ProxyFactoryBean` tạo proxy.
2. `outerInterceptor` và `innerInterceptor` đều là Spring beans. `checkoutAdvisor` tham chiếu đến `innerInterceptor` qua thuộc tính `advice`.
3. `interceptorNames` chứa **`outerInterceptor`** rồi **`checkoutAdvisor`**, tức tên của một interceptor trực tiếp và một advisor, **không** chứa `innerInterceptor` riêng. Nếu thêm `innerInterceptor` lần nữa như một mục độc lập, hành vi inner có thể bị gọi hai lần trên `checkout` và bị gọi cả trên `cancel`.
4. `target` dùng `ref="orderTarget"` vì đây là một tham chiếu bean thực sự. Trái lại, các mục `interceptorNames` là **giá trị chuỗi** `<value>...` vì bean names được Spring giải quyết theo cơ chế riêng.
5. `proxyInterfaces` chỉ rõ interface của proxy. Khi client dùng `OrderOperations`, không cần ép kiểu proxy sang `OrderService` cụ thể. Interface khác proxy target class có thể ảnh hưởng kiểu proxy và những phương thức khả dụng.
6. Không có `@EnableAspectJAutoProxy` bởi cách này cấu hình proxy trực tiếp với `ProxyFactoryBean`.

### 4.6. Lớp chạy chương trình

Tạo `src/main/java/demo/aop/RunAdvisorOrder.java`:

```java
package demo.aop;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RunAdvisorOrder {

    public static void main(String[] args) {
        try (var context =
                     new ClassPathXmlApplicationContext("aop-interceptor-order.xml")) {
            OrderOperations service =
                    context.getBean("orderProxy", OrderOperations.class);

            System.out.println("result=" + service.checkout("A17"));
            System.out.println("result=" + service.cancel("A18"));
        }
    }
}
```

Để chạy bằng Gradle, bổ sung task vào file build đang có:

```groovy
tasks.register('runAdvisorOrder', JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'demo.aop.RunAdvisorOrder'
}
```

Lệnh thực thi từ thư mục chứa Gradle wrapper:

```bash
./gradlew runAdvisorOrder
```

Trên Windows PowerShell: `.\\gradlew.bat runAdvisorOrder`.

**Các dòng stdout do chương trình in theo thứ tự** (bỏ qua log hệ thống nếu cấu hình logging của dự án tự in thêm):

```text
outer:before checkout
inner:before checkout
target:checkout A17
inner:after checkout
outer:after checkout
result=PAID:A17
outer:before cancel
target:cancel A18
outer:after cancel
result=CANCELLED:A18
```

Đây là kết quả từ hai lựa chọn:

- `checkout()` khớp advisor chọn lọc, nên gọi cả outer và inner.
- `cancel()` không khớp `checkoutAdvisor`, nên chỉ gọi outer (vì interceptor trực tiếp áp dụng cho các method có thể chặn trên proxy).

## 5. Đảo thứ tự trong `interceptorNames`

Không phải thay đổi Java source. Chỉ đổi hai phần tử trong `aop-interceptor-order.xml`:

```xml
<property name="interceptorNames">
    <list>
        <value>checkoutAdvisor</value>
        <value>outerInterceptor</value>
    </list>
</property>
```

Với `checkout()`, khi cả hai đều khớp, thứ tự lời gọi đổi thành:

```text
inner:before checkout
outer:before checkout
target:checkout A17
outer:after checkout
inner:after checkout
result=PAID:A17
```

Các dòng của `cancel()` vẫn là:

```text
outer:before cancel
target:cancel A18
outer:after cancel
result=CANCELLED:A18
```

Đảo vị trí một `PointcutAdvisor` chỉ làm thay đổi thứ tự xử lý trên những phương thức **mà advisor đó khớp**. Không thể dựa vào một lần gọi `cancel` để kết luận thứ tự trên `checkout`.

**Lưu ý:** Ví dụ trên dùng danh sách **tên bean tường minh**, nên việc thay đổi thứ tự rất dễ dự đoán. Khi có wildcard `global*`, nội dung của nhóm được mở rộng theo các bean hiện có; thứ tự trong nhóm còn phụ thuộc các quy tắc sắp xếp của Spring và `Ordered` nếu các advisor tương ứng cung cấp order. Đừng tự kết luận wildcard luôn có thứ tự theo chữ cái.

## 6. Mở rộng danh sách bằng hậu tố `*`

Spring hỗ trợ giá trị `interceptorNames` kết thúc bằng dấu `*` như một **mẫu tên bean**. Ví dụ:

```xml
<bean id="globalAudit"
      class="demo.aop.TraceInterceptor">
    <constructor-arg value="audit"/>
</bean>
<bean id="globalMetrics"
      class="demo.aop.TraceInterceptor">
    <constructor-arg value="metrics"/>
</bean>

<bean id="orderProxy"
      class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="target" ref="orderTarget"/>
    <property name="interceptorNames">
        <list>
            <value>global*</value>
            <value>checkoutAdvisor</value>
        </list>
    </property>
</bean>
```

Đây là **mảnh XML minh họa để thay đổi cấu hình đã có**; các bean `orderTarget` và `checkoutAdvisor` cần được khai báo như ví dụ chính, đồng thời cấu hình `proxyInterfaces` nếu muốn ép kiểu interface. Nhóm `global*` có thể mở rộng thành các advice/advisor bean phù hợp có tên bắt đầu với `global`. Sử dụng `*` không có tiền tố dễ chọn quá nhiều bean, nhất là khi application context chứa nhiều advisor của các module khác.

Phân biệt hai loại matching:

- `global*` trong `interceptorNames`: tìm **tên bean advice/advisor** trong Spring BeanFactory.
- `NameMatchMethodPointcut` hay `AspectJExpressionPointcut`: chọn **phương thức** của target khi proxy xử lý lời gọi.

Hai cơ chế hoạt động ở hai tầng khác nhau. `global*` không phải pointcut chọn tên method bắt đầu bằng `global`.

### 6.1. Thứ tự bên trong nhóm wildcard

Javadoc của `ProxyFactoryBean` mô tả các global interceptors/advisors được mở rộng từ tiền tố tên và có thể được sắp xếp bằng kết quả `getOrder()` khi triển khai `org.springframework.core.Ordered`. Vì vậy:

- Danh sách tường minh `"outerInterceptor", "checkoutAdvisor"`: thứ tự các mục được chỉ định trực tiếp.
- `"global*"`: tập bean thực tế được lấy theo tên; nếu muốn thứ tự có hợp đồng rõ, ưu tiên cấu hình tường minh hoặc dùng các advisor triển khai `Ordered` và kiểm thử thứ tự đã được dựng.
- Nếu trong cùng một proxy có cả wildcard và các mục tường minh, hãy xem vị trí **mục wildcard sau khi mở rộng** trong chuỗi tổng thể; đừng cho rằng bean dùng làm target cũng sẽ bị wildcard chọn.

Khi bổ sung thêm một bean tên `globalSecurity`, proxy có thể nhận thêm advisor/interceptor tương ứng theo mẫu. Vì vậy nên chọn tiền tố ít nguy cơ trùng tên, nhất là trong hệ thống nhiều module.

## 7. Kiểm chứng thứ tự bằng JUnit và Spring `Advised`

Spring AOP proxy thường cho phép truy cập giao diện `org.springframework.aop.framework.Advised` để xem danh sách advisor đã gắn, trừ trường hợp proxy được cấu hình theo các chế độ làm ẩn interface này. Ví dụ XML chính không bật chế độ opaque, nên có thể kiểm chứng chuỗi cấu hình mà không thay đổi production code.

Tạo `src/test/java/demo/aop/ProxyFactoryBeanOrderTest.java`:

```java
package demo.aop;

import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class ProxyFactoryBeanOrderTest {

    @Test
    void orderAndMatchingAgreeWithConfiguration() {
        try (var context =
                     new ClassPathXmlApplicationContext("aop-interceptor-order.xml")) {

            OrderOperations service =
                    context.getBean("orderProxy", OrderOperations.class);

            // Cùng bean name nhưng dấu & lấy chính FactoryBean.
            ProxyFactoryBean factory =
                    context.getBean("&orderProxy", ProxyFactoryBean.class);
            assertNotNull(factory);

            Advised advised = (Advised) service;
            Advisor[] chain = advised.getAdvisors();

            // XML gốc gắn trực tiếp outer, sau đó checkoutAdvisor.
            assertEquals(2, chain.length);
            assertSame(context.getBean("outerInterceptor"),
                    chain[0].getAdvice());
            assertSame(context.getBean("checkoutAdvisor"),
                    chain[1]);

            assertTrue(chain[1] instanceof PointcutAdvisor);
            PointcutAdvisor selected = (PointcutAdvisor) chain[1];

            assertTrue(selected.getPointcut().getMethodMatcher().matches(
                    OrderOperations.class.getMethod("checkout", String.class),
                    OrderService.class));
            assertFalse(selected.getPointcut().getMethodMatcher().matches(
                    OrderOperations.class.getMethod("cancel", String.class),
                    OrderService.class));

            assertEquals("PAID:T1", service.checkout("T1"));
            assertEquals("CANCELLED:T2", service.cancel("T2"));
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
```

Chạy:

```bash
./gradlew test --tests 'demo.aop.ProxyFactoryBeanOrderTest'
```

Trên Windows: `.\\gradlew.bat test --tests 'demo.aop.ProxyFactoryBeanOrderTest'`.

Khi ba thành phần XML/Java đã được bố trí đúng và mọi assertion pass, Gradle báo `BUILD SUCCESSFUL`. Test này xác minh: proxy mang đúng hai advisor theo thứ tự; advisor thứ hai chọn `checkout` và loại `cancel`; các phương thức trả kết quả phù hợp.

**Lưu ý với test:** Nếu bạn đã đổi `interceptorNames` theo mục 5, thì thứ tự `chain[0]`/`chain[1]` trong assertion phải được cập nhật tương ứng. Nếu đang kiểm thử nhóm `global*`, không nên hard-code thứ tự chưa kiểm chứng mà cần quyết định hợp đồng `Ordered` rõ ràng.

### 7.1. Khi nào vẫn cần kiểm thử trace thực tế?

`Advised.getAdvisors()` giúp xem danh sách advisor, nhưng chưa bảo đảm những interceptor cụ thể đều chạy trên một phương thức. Mỗi `PointcutAdvisor` có thể bị loại trên lời gọi không khớp, và một interceptor có thể dừng chuỗi do không gọi `proceed()`. Vì vậy, bổ sung một test thu thập sự kiện before/after/target vào `List<String>` hoặc bắt stdout trong môi trường kiểm thử riêng khi cần so thứ tự thực thi; đặc biệt cần test ca **có match** và **không match**.

Không suy luận rằng Spring sẽ kích hoạt advisor khi gọi thẳng:

```java
new OrderService().checkout("T1");
```

Đây là lời gọi lên target Java thông thường, không đi qua proxy, nên không sử dụng chain do `ProxyFactoryBean` cấu hình.

## 8. Scope, thứ tự và lifecycle

### 8.1. `ProxyFactoryBean.singleton` mặc định

`ProxyFactoryBean` mặc định sản xuất một **singleton proxy**. Lấy bean `"orderProxy"` nhiều lần thường nhận cùng proxy. Đặt `singleton=false` cho phép tạo proxy khác nhau khi lấy nhiều lần, đồng thời có thể lấy các đối tượng advice/target prototype độc lập nếu **bean definitions và cách tham chiếu** được cấu hình để làm điều đó.

Đừng đồng nhất “mỗi lần lấy proxy mới” với “mọi dependency của nó tự động thành object mới”: một `target` singleton đã được resolve thành reference vẫn là singleton, và một Advice bean singleton vẫn dùng chung.

### 8.2. Vì sao phải dùng tên bean thay vì reference trong `interceptorNames`?

Với advice prototype, Spring cần có cơ hội tra bean từ factory tại thời điểm cần dùng. Danh sách tên giúp `ProxyFactoryBean` quản lý việc lấy instance theo scope. Cấu hình trực tiếp object references trong chính `interceptorNames` không phù hợp hợp đồng chuỗi tên và có thể làm mất semantics độc lập của prototype advice.

Ví dụ khai báo một advisor prototype:

```xml
<bean id="prototypeAdvisor"
      class="org.springframework.aop.support.DefaultPointcutAdvisor"
      scope="prototype">
    <property name="pointcut" ref="checkoutPointcut"/>
    <property name="advice" ref="innerInterceptor"/>
</bean>

<bean id="orderProxy"
      class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="targetName" value="orderTarget"/>
    <property name="singleton" value="false"/>
    <property name="interceptorNames">
        <list><value>prototypeAdvisor</value></list>
    </property>
</bean>
```

Đây là **đoạn XML riêng để minh họa vòng đời**, không được dán thêm đồng thời với một bean khác cùng id `orderProxy`. `targetName` được khai báo riêng để Spring giải quyết target theo tên thay vì dùng target instance cố định. Độc lập hoàn toàn hay không còn tùy phạm vi của `orderTarget`/`innerInterceptor` (trong ví dụ, nếu chúng là singleton thì những phần đó vẫn được chia sẻ).

### 8.3. Chuyện gì xảy ra khi chỉnh danh sách sau khi khởi tạo?

Với `ProxyFactoryBean` đã tạo singleton proxy, thay đổi cấu hình advisor có thể ảnh hưởng chain, nhưng không nên coi việc sửa XML trên đĩa là tự động cập nhật live proxy. Ứng dụng thường cần xây dựng lại ApplicationContext để nạp cấu hình mới. Nếu chủ đích thay advisor động qua API `Advised`, hãy kiểm soát tác động lên lời gọi đang chạy và kiểm thử trước.

## 9. Lỗi phổ biến và checklist debug

| Triệu chứng | Nguyên nhân có thể | Cách kiểm tra |
| --- | --- | --- |
| Bean khởi tạo lỗi vì không tìm thấy interceptor | Tên trong `interceptorNames` không trùng bean id hoặc không nằm trong BeanFactory phù hợp | Kiểm tra bean name, không dùng tên Java class |
| Chuỗi log trước/sau bị đảo | Thứ tự các mục trong `interceptorNames` đã đổi | Đọc danh sách XML và `Advised.getAdvisors()` |
| Advice chạy hai lần | Advice vừa được gắn trực tiếp vừa nằm trong `PointcutAdvisor` hoặc được chọn thêm bởi wildcard | Liệt kê đầy đủ advisor chain, tránh trùng tên/matching |
| `cancel()` cũng chạy advice chọn lọc | Đưa `innerInterceptor` trực tiếp vào list thay vì `checkoutAdvisor` | Giữ advisor có pointcut; test negative case |
| Thêm bean mới làm xuất hiện advice lạ | `global*` chọn thêm bean tên cùng tiền tố | Dùng tiền tố chặt hoặc liệt kê tường minh |
| Không thể ép kiểu proxy về class cụ thể | Đang dùng JDK proxy qua interface | Ép kiểu về interface đã định nghĩa; xác định chiến lược proxy |
| Không thấy advice khi gọi method | Gọi target trực tiếp, self-invocation, method không match hoặc proxy có giới hạn | Xác nhận lời gọi qua bean proxy, test `PointcutAdvisor` |
| Không đạt kỳ vọng tách biệt trạng thái | Singleton proxy/advice/target được tái sử dụng | Kiểm tra `singleton`, bean scope, `targetName` và thời điểm resolve |
| Nhầm thứ tự wildcard | Giả định sắp xếp theo tên thay cho hợp đồng ordering | Xem `Ordered` và kiểm chứng danh sách sau mở rộng |

Trình tự debug thực dụng:

1. **Nguồn cấu hình:** xác định `orderProxy` thật sự là `ProxyFactoryBean`, target đúng, `interceptorNames` chứa đúng **bean names**.
2. **Chuỗi advisor:** lấy proxy qua `getBean("orderProxy")` rồi cast `Advised` để đọc `getAdvisors()`; lấy factory qua `getBean("&orderProxy")` nếu cần xem cấu hình.
3. **Pointcut:** mỗi `PointcutAdvisor` có thể lọc class/method. Kiểm thử `getClassFilter()` và `getMethodMatcher()` riêng nếu behavior ngoài dự kiến.
4. **Thứ tự:** xét trước và sau `proceed()`, thử đảo hai tên tường minh và so log.
5. **Wildcard:** liệt kê bean thực sự khớp tiền tố; nếu cần thứ tự cố định, thay wildcard bằng danh sách rõ ràng hoặc thiết lập chính sách `Ordered`.
6. **Vòng đời:** khi dùng prototype, kiểm tra scope của proxy, advisor, advice và target; việc chọn `singleton=false` một mình chưa bảo đảm mọi object đều mới.

## 10. Bài tập

**Bài 1 — Đảo thứ tự.** Dùng cấu hình XML chính, đổi thành `checkoutAdvisor` rồi `outerInterceptor`; dự đoán đầy đủ log của `checkout("A17")` trước khi chạy và so với stdout.

**Bài 2 — Loại bỏ advice toàn cục.** Xóa `outerInterceptor` khỏi `interceptorNames` nhưng giữ `checkoutAdvisor`. Chứng minh `cancel("A18")` chỉ in log từ target, trong khi `checkout("A17")` vẫn chạy inner.

**Bài 3 — Trùng interceptor.** Thêm `innerInterceptor` vào cuối danh sách bên cạnh `checkoutAdvisor`. Dự đoán sự khác biệt giữa `checkout` và `cancel`; sau đó loại trùng để bảo đảm mỗi hành vi chỉ gắn nơi đã định.

**Bài 4 — Nhóm global.** Đổi tên hai interceptor thành `globalAudit` và `globalMetrics`, dùng `global*` trong `interceptorNames`. Quan sát chain thực tế. Nếu yêu cầu thứ tự ổn định, thay thế bằng tên tường minh hoặc xác nhận `Ordered` trong các advisor.

**Bài 5 — Proxy và FactoryBean.** Từ cùng ApplicationContext, so kiểu đối tượng của `getBean("orderProxy")` và `getBean("&orderProxy")`. Giải thích tại sao chỉ proxy mới là đối tượng nghiệp vụ nên được client gọi.

**Tiêu chí hoàn thành:** Có thể giải thích và chạy được trình tự “`interceptorNames` → resolve bean → advisor chain → Spring proxy → target”, chỉ rõ vị trí tên bean, hành vi của pointcut, thứ tự before/after, ý nghĩa của wildcard và giới hạn scope.

## 11. Tài liệu Spring Framework

- [Using the ProxyFactoryBean to Create AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html) — `interceptorNames`, thứ tự, global advisors, singleton và scope.
- [ProxyFactoryBean Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/framework/ProxyFactoryBean.html) — `setInterceptorNames`, `setTargetName` và các thuộc tính proxy.
- [The Advisor API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html) — kết hợp pointcut/advice trong advisor.
- [Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html) — `Advised`, `getAdvisors()` và thao tác advisor chain.
- [Advice API in Spring](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html) — `MethodInterceptor` và `proceed()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="programmatic-proxy-creation-007">Cấu hình proxy lớp hoặc proxy interface</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: Cấu hình proxy lớp hoặc proxy interface bằng API

## Mục tiêu học tập

Trong Spring AOP, bạn có thể chọn cách tạo proxy **theo interface** bằng JDK dynamic proxy hoặc **theo lớp cụ thể** bằng CGLIB. Lựa chọn này ảnh hưởng trực tiếp đến kiểu dữ liệu có thể dùng tại nơi gọi, phương thức nào có thể được advice và những giới hạn khi phương thức gọi lẫn nhau trong cùng object.

Sau bài này, bạn sẽ:

- Sử dụng `org.springframework.aop.framework.ProxyFactory` để tạo JDK dynamic proxy hoặc CGLIB proxy.
- Hiểu ý nghĩa của `setInterfaces(...)`, `setProxyTargetClass(true/false)` và cơ chế tự nhận diện interface.
- Phân biệt **kiểu proxy**, **tập phương thức có thể gọi qua proxy** và **tập phương thức thực sự được advice**.
- Nhận biết các giới hạn `final`, `private`, self-invocation, cách ép kiểu và các tình huống thay đổi cấu hình AOP.
- Kiểm tra loại proxy bằng `AopUtils` và viết bài test khẳng định hành vi thay vì dựa vào tên class sinh ra lúc chạy.

Bài tập trung vào **Spring AOP sử dụng proxy trong Spring Framework**. Đây là cấu hình thực tế của Spring, không phải bài tổng quan về lập trình hướng khía cạnh hoặc bytecode weaving.

## 1. Hai cơ chế proxy của Spring AOP

Spring AOP có hai chiến lược tạo proxy phổ biến:

| Tiêu chí | JDK dynamic proxy (proxy interface) | CGLIB (proxy lớp) |
| --- | --- | --- |
| Cơ chế | Tạo object triển khai các interface được chọn | Tạo subclass tại runtime của lớp target |
| Khi thường được chọn | Target có interface và không bật ép class proxy | Target không có business interface hoặc ép class proxy |
| Kiểu có thể ép tại caller | Các interface mà proxy triển khai | Lớp target và các interface liên quan |
| Phương thức truy cập qua proxy | Các method thuộc interface được proxy | Các method có thể override/advise trên class proxy |
| Có thể ép về lớp implementation? | **Không** | **Có** |
| Lớp target `final` | Có thể proxy qua interface nếu phù hợp | **Không** thể kế thừa class final |
| Phương thức `final` | Interface proxy vẫn có thể intercept lời gọi qua interface của target, dù implementation method là final | Không thể advice bằng override method final |
| Phương thức `private` | Không phải method trên interface được expose để advice | Không override được nên không được advice bằng CGLIB |
| Tự gọi `this.method()` trong cùng target | Thường đi vòng qua proxy | Thường đi vòng qua proxy |

CGLIB được Spring đóng gói trong `spring-core`, nên với Spring AOP 6.x thông thường không cần thêm riêng một dependency CGLIB. **Cả hai cơ chế đều là proxy**: việc ép dùng CGLIB không biến Spring AOP thành compile-time hoặc load-time weaving.

### Quy tắc mặc định

Với cấu hình Spring AOP thông thường:

1. Nếu target có một hoặc nhiều interface phù hợp, Spring thường dùng **JDK dynamic proxy**.
2. Nếu target không có interface để proxy, Spring dùng **CGLIB**.
3. Nếu bật `proxyTargetClass = true`, Spring ưu tiên **CGLIB**, kể cả khi target có interface.

Đây là quy tắc của Spring AOP core khi tạo proxy phù hợp. **Spring Boot và cấu hình auto-proxy của một ứng dụng có thể đặt mặc định riêng**; vì vậy không được suy luận kiểu proxy thực tế chỉ từ việc service có interface. Bài này dùng `ProxyFactory` để kiểm soát rõ lựa chọn ở từng trường hợp.

## 2. API `ProxyFactory`: chọn target, interface, cách tạo proxy

Import chính:

~~~java
import org.springframework.aop.framework.ProxyFactory;
~~~

### 2.1. Proxy theo interface

~~~java
OrderService target = new OrderServiceImpl();

ProxyFactory factory = new ProxyFactory(target);
factory.setInterfaces(OrderService.class);
factory.setProxyTargetClass(false);
// factory.addAdvice(...), hoặc factory.addAdvisor(...)

OrderService proxy = (OrderService) factory.getProxy();
~~~

Đây là **đoạn minh họa**, giả định `OrderService` là interface và `OrderServiceImpl` implements interface đó. Có ba quyết định:

- `new ProxyFactory(target)` cung cấp object cần bao. Constructor này còn có khả năng nhận diện các interface của target.
- `setInterfaces(OrderService.class)` khai báo rõ interface sẽ được expose qua proxy. Nếu muốn expose nhiều interface, truyền các interface đó vào lời gọi thích hợp.
- `setProxyTargetClass(false)` giữ chế độ không ép proxy lớp. Với interface vừa cấu hình, cơ chế bình thường là JDK proxy.

Sau đó, caller nhận một object **implement `OrderService`**; đó không phải subclass của `OrderServiceImpl`. Câu lệnh `(OrderServiceImpl) proxy` sẽ thất bại với `ClassCastException` nếu runtime sử dụng JDK dynamic proxy.

### 2.2. Proxy theo lớp

~~~java
OrderServiceImpl target = new OrderServiceImpl();

ProxyFactory factory = new ProxyFactory(target);
factory.setProxyTargetClass(true);
factory.addAdvice(/* MethodInterceptor */ interceptor);

OrderServiceImpl proxy = (OrderServiceImpl) factory.getProxy();
~~~

Đây cũng là đoạn minh họa; `interceptor` cần được định nghĩa trước. `setProxyTargetClass(true)` yêu cầu tạo proxy dựa trên target class. Proxy CGLIB có thể được sử dụng như `OrderServiceImpl` (và vẫn có thể được gọi qua interface khi class implement interface).

**Thứ tự ưu tiên:** nếu bạn vừa `setInterfaces(...)` vừa `setProxyTargetClass(true)`, cờ ép proxy lớp vẫn có hiệu lực; danh sách interface không biến CGLIB trở lại thành JDK dynamic proxy.

### 2.3. Target không triển khai interface

~~~java
ProxyFactory factory = new ProxyFactory(new PlainService());
factory.addAdvice(interceptor);
PlainService proxy = (PlainService) factory.getProxy();
~~~

Đây là đoạn minh họa giả định `PlainService` là lớp concrete không triển khai interface nghiệp vụ, và `interceptor` đã được khai báo. Khi không có interface phù hợp để proxy, Spring tự chuyển sang proxy lớp theo CGLIB ngay cả khi không gọi `setProxyTargetClass(true)`.

**Không nhầm `setProxyTargetClass(false)` với “bắt buộc phải dùng JDK proxy”.** Giá trị `false` chỉ có nghĩa là bạn không ép class proxy; nếu không có interface để JDK proxy, Spring vẫn cần class proxy.

## 3. Ví dụ Java 17+ chạy được: cùng target, hai loại proxy

Ví dụ dùng Spring Framework 6.2.19, Java 17+, Maven. Một service vừa có method khai báo trên interface vừa có method chỉ ở lớp implementation. Ví dụ cũng thể hiện phương thức `outer()` tự gọi `localOnly()` và vì sao advice chỉ chạy một lần cho đường gọi đó.

### Cấu trúc

~~~text
spring-proxy-choice/
├── pom.xml
└── src/main/java/example/ProxyChoiceDemo.java
~~~

### Tệp `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-proxy-choice</artifactId>
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
                    <mainClass>example.ProxyChoiceDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### Tệp `src/main/java/example/ProxyChoiceDemo.java`

~~~java
package example;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public class ProxyChoiceDemo {

    public interface Catalog {
        String find(String sku);
    }

    // Không phải final: CGLIB có thể tạo subclass.
    public static class CatalogService implements Catalog {
        @Override
        public String find(String sku) {
            return "product:" + sku;
        }

        // Method này KHÔNG có trong interface Catalog.
        public String localOnly() {
            return "local";
        }

        public String outer() {
            // Lời gọi đi qua this của target, không đi qua proxy.
            return "outer>" + localOnly();
        }
    }

    public static class PlainService {
        public String ping() {
            return "pong";
        }
    }

    public static void main(String[] args) {
        MethodInterceptor trace = invocation -> {
            System.out.println("ADVICE " + invocation.getMethod().getName());
            return invocation.proceed();
        };

        // A. JDK dynamic proxy được khai báo rõ theo Catalog.
        ProxyFactory interfaceFactory = new ProxyFactory(new CatalogService());
        interfaceFactory.setInterfaces(Catalog.class);
        interfaceFactory.setProxyTargetClass(false);
        interfaceFactory.addAdvice(trace);
        Object jdkObject = interfaceFactory.getProxy();

        System.out.println("JDK=" + AopUtils.isJdkDynamicProxy(jdkObject));
        System.out.println("JDK as Catalog=" + (jdkObject instanceof Catalog));
        System.out.println("JDK as CatalogService=" +
                (jdkObject instanceof CatalogService));
        System.out.println("JDK result=" + ((Catalog) jdkObject).find("A1"));

        // B. CGLIB subclass proxy cùng một loại target.
        ProxyFactory classFactory = new ProxyFactory(new CatalogService());
        classFactory.setProxyTargetClass(true);
        classFactory.addAdvice(trace);
        CatalogService classProxy = (CatalogService) classFactory.getProxy();

        System.out.println("CGLIB=" + AopUtils.isCglibProxy(classProxy));
        System.out.println("CGLIB as CatalogService=" +
                (classProxy instanceof CatalogService));
        System.out.println("CGLIB result=" + classProxy.find("B2"));
        System.out.println("CGLIB local=" + classProxy.localOnly());
        System.out.println("CGLIB outer=" + classProxy.outer());

        // C. Target không có interface -> Spring tự dùng class proxy.
        ProxyFactory plainFactory = new ProxyFactory(new PlainService());
        plainFactory.addAdvice(trace);
        PlainService plainProxy = (PlainService) plainFactory.getProxy();

        System.out.println("NO_INTERFACE_CGLIB=" +
                AopUtils.isCglibProxy(plainProxy));
        System.out.println("NO_INTERFACE result=" + plainProxy.ping());
    }
}
~~~

### Chạy

~~~bash
mvn -q compile exec:java
~~~

Đầu ra từ những lệnh `System.out.println` trong chương trình:

~~~text
JDK=true
JDK as Catalog=true
JDK as CatalogService=false
ADVICE find
JDK result=product:A1
CGLIB=true
CGLIB as CatalogService=true
ADVICE find
CGLIB result=product:B2
ADVICE localOnly
CGLIB local=local
ADVICE outer
CGLIB outer=outer>local
NO_INTERFACE_CGLIB=true
ADVICE ping
NO_INTERFACE result=pong
~~~

Maven/JVM có thể in thêm thông báo công cụ bên ngoài các dòng do chương trình tạo ra. Các tên class sinh động ở runtime có thể thay đổi theo JDK/Spring, nên ví dụ kiểm tra bằng API `AopUtils` thay vì in tên proxy và so sánh một chuỗi tên cố định.

### Phân tích kết quả

**A. JDK proxy**

- `interfaceFactory.setInterfaces(Catalog.class)` chỉ expose giao diện `Catalog`. Đối tượng proxy `instanceof Catalog` nhưng **không** `instanceof CatalogService`.
- `((Catalog) jdkObject).find("A1")` là lời gọi qua proxy nên `trace.invoke(...)` in `ADVICE find` rồi tiếp tục đến target.
- `CatalogService.localOnly()` không nằm trên `Catalog`. Bạn không thể gọi `localOnly()` từ biến `Catalog` hay cast JDK proxy thành `CatalogService` để gọi nó. Nếu cần method đó qua proxy, hãy bổ sung một interface phù hợp hoặc chọn class proxy tùy thiết kế.

**B. CGLIB proxy**

- `classFactory.setProxyTargetClass(true)` tạo subclass của `CatalogService`. Bởi vậy ép về `CatalogService` hợp lệ.
- Các public method `find` và `localOnly` đủ điều kiện được intercept khi gọi **trực tiếp qua classProxy**. `trace` in cả hai tên.
- `classProxy.outer()` đi qua proxy, nên có `ADVICE outer`. Nhưng trong thân `outer`, target gọi `this.localOnly()`; lời gọi nội bộ đó không đi qua proxy. Vì vậy **không có** thêm `ADVICE localOnly` giữa `ADVICE outer` và kết quả `outer>local`.
- Chuyển từ JDK sang CGLIB **không sửa vấn đề self-invocation**. Nếu cần advice ở phương thức thứ hai, hãy thiết kế lời gọi đi qua một bean/proxy thích hợp; chỉ dùng `AopContext.currentProxy()` trong những tình huống đặc biệt vì tạo phụ thuộc vào AOP infrastructure.

**C. Không có interface**

- `PlainService` không khai báo interface nghiệp vụ, nên Spring dùng CGLIB trong trường hợp này dù code không gọi `setProxyTargetClass(true)`.
- Có thể cast proxy về `PlainService` và intercept `ping()`.

**Giới hạn của ví dụ:** lời gọi `addAdvice(trace)` thêm interceptor áp dụng rộng trong **phạm vi proxy vừa dựng**. Với dự án thực, cần gắn advisor có pointcut chỉ chọn class/method phù hợp nếu chính sách advice không áp lên tất cả method.

## 4. Cách chọn proxy bằng `ProxyFactory`: ma trận quyết định

Bảng dưới áp dụng cho proxy được tạo bằng Spring AOP core khi không có cơ chế cấu hình đặc biệt khác can thiệp:

| Target | `setInterfaces(...)` | `setProxyTargetClass` | Loại proxy dự kiến | Kiểu caller nên dùng |
| --- | --- | --- | --- | --- |
| Có interface `Api` | Chỉ định `Api` | `false` | JDK dynamic proxy | `Api` |
| Có interface `Api` | Được auto-detect | `false` | JDK dynamic proxy | Interface được proxy |
| Có interface `Api` | Có hoặc không | `true` | CGLIB class proxy | Lớp implementation hoặc interface |
| Không có business interface | Không | `false` / để mặc định | CGLIB class proxy | Lớp concrete |
| Không có business interface | Không | `true` | CGLIB class proxy | Lớp concrete |

### Mẹo dùng `setInterfaces`

- Hãy dùng `setInterfaces(ServiceApi.class)` khi bạn muốn **hợp đồng sử dụng qua interface rõ ràng** và giới hạn những interface được expose.
- Đừng nhầm “target class implements interface X” với “JDK proxy chắc chắn expose mọi method của implementation”: proxy chỉ expose những interface được tạo cho nó.
- Việc chỉ định interface không thay đổi target object; nếu interface không mô tả hành vi bạn cần gọi, bạn phải sửa hợp đồng interface hoặc lựa chọn khác.
- Khi target có nhiều interface, Spring có thể tự nhận diện; nếu cần kiểm soát API công khai, khai báo danh sách interface một cách chủ động.

### Khi nào bật `setProxyTargetClass(true)`?

Hợp lý khi:

1. Bạn cần truyền proxy vào một API nhận **kiểu lớp concrete** thay vì interface.
2. Target có public/protected method không được khai báo trên interface nhưng cần advice qua class proxy.
3. Target không có interface và bạn đang xây proxy cho class có thể kế thừa.

Cần cân nhắc kỹ khi class hoặc method bị `final`, cần xử lý constructor/visibility hoặc muốn duy trì ranh giới interface rõ ràng. CGLIB không làm tăng chất lượng thiết kế một cách tự động: chọn nó khi có yêu cầu kỹ thuật cụ thể.

## 5. Ranh giới giữa “gọi được” và “được advice”

Trong Spring AOP, để advice chạy tại một lời gọi, phải thỏa ít nhất những điều kiện sau:

1. **Đúng đối tượng:** caller đang giữ **proxy**, không phải một tham chiếu target được lấy trực tiếp bằng `new` hoặc nội bộ.
2. **Lời gọi có thể đi qua proxy:** với JDK proxy, method được truy cập qua interface proxy; với CGLIB, method tương thích với cơ chế subclass/override.
3. **Advisor/pointcut phù hợp:** lớp và method được Spring AOP kiểm tra phải match điều kiện của advisor.
4. **Interceptor chain cho phép tiếp tục:** nếu một interceptor trước đó ném lỗi hoặc không gọi `proceed()`, target/advice ở phía sau có thể không chạy.

Ví dụ:

~~~java
// Minh họa điều kiện lựa chọn method, đặt trong code đã có ProxyFactory:
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;

NameMatchMethodPointcut onlyFind = new NameMatchMethodPointcut();
onlyFind.setMappedName("find");
factory.addAdvisor(new DefaultPointcutAdvisor(onlyFind, trace));
~~~

Đoạn này **không phải chương trình chạy độc lập**, và là phương án **thay thế** cho lời gọi `factory.addAdvice(trace)` khi cần chọn lọc. Nếu chỉ `find` match, việc gọi `localOnly` qua CGLIB proxy vẫn hợp lệ về mặt method nhưng **không kích hoạt advice đó**.

**Điểm quan trọng:** chuyển từ interface proxy sang class proxy không làm `Pointcut.TRUE`, `NameMatchMethodPointcut` hay các advisor khác đổi ý nghĩa. Nó thay đổi khả năng proxy bắt được lời gọi theo loại object/method; pointcut tiếp tục quyết định advisor có áp dụng cho lời gọi đủ điều kiện đó không.

## 6. Cấu hình proxy với Spring container: cùng mục đích, đường áp dụng khác nhau

`ProxyFactory` tạo proxy trực tiếp mà không cần `ApplicationContext`. Khi làm việc với bean trong container, Spring còn có `ProxyFactoryBean` hoặc các cơ chế auto-proxy hỗ trợ AOP qua cấu hình.

### 6.1. `ProxyFactoryBean`

`org.springframework.aop.framework.ProxyFactoryBean` là một `FactoryBean` tạo proxy từ target và cấu hình advisors/interceptors.

Các thuộc tính có liên quan:

- `target` hoặc cấu hình target source: xác định đối tượng được proxy.
- `proxyInterfaces`: những interface cần expose nếu sử dụng proxy theo interface.
- `proxyTargetClass`: ép class proxy khi đặt `true`.
- `interceptorNames`: danh sách tên bean advice/advisor, với thứ tự ảnh hưởng chuỗi interceptor.

**Lưu ý sự khác biệt API:** `ProxyFactory.setInterfaces(Class<?>...)` nhận các `Class` object; thuộc tính `proxyInterfaces` của `ProxyFactoryBean` ở dạng cấu hình bean có thể nhận các tên interface tùy cơ chế binding. Cả hai cùng nhằm xác định tập interface được expose nhưng là hai cách dùng khác nhau.

Đoạn XML minh họa lựa chọn JDK proxy theo interface:

~~~xml
<bean id="checkoutTarget" class="example.CheckoutServiceImpl"/>

<bean id="checkoutProxy" class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="target" ref="checkoutTarget"/>
    <property name="proxyInterfaces" value="example.CheckoutService"/>
    <property name="interceptorNames">
        <list>
            <value>auditAdvisor</value>
        </list>
    </property>
</bean>
~~~

Đây là **fragment XML phụ thuộc vào những bean/kiểu khai báo bên ngoài** (`example.CheckoutServiceImpl`, `example.CheckoutService`, `auditAdvisor`), không phải cấu hình độc lập có thể chạy ngay. Nó cho thấy `proxyInterfaces` rõ ràng; nếu muốn class proxy, thêm `<property name="proxyTargetClass" value="true"/>` (điều kiện lớp cho phép subclass). Cần bảo đảm advisor bean tồn tại và pointcut chọn đúng method.

### 6.2. Auto-proxy và `@EnableAspectJAutoProxy`

Trong cấu hình Spring AOP dạng annotation, ví dụ:

~~~java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class AopConfig {
}
~~~

Đoạn này cần import `org.springframework.context.annotation.Configuration` và `org.springframework.context.annotation.EnableAspectJAutoProxy`, chưa phải chương trình độc lập. Thuộc tính `proxyTargetClass = true` ép proxy lớp cho cơ chế auto-proxy tương ứng. Nếu để mặc định theo interface, kết quả còn phụ thuộc vào thiết lập cuối cùng của hạ tầng auto-proxy được sử dụng trong ứng dụng.

**Spring Boot có thể đặt mặc định class proxy** tùy phiên bản/cấu hình, ví dụ thuộc tính `spring.aop.proxy-target-class`. Vì vậy không suy ra “Spring dùng JDK proxy” chỉ từ một service implement interface khi ứng dụng Boot có AOP auto-configuration. Hãy kiểm tra cấu hình đã hợp nhất và proxy runtime.

Các cấu hình `@EnableAspectJAutoProxy`, `@EnableTransactionManagement` và thành phần auto-proxy liên quan có thể ảnh hưởng cùng hệ thống proxy trong context; khi nhiều cấu hình cùng tồn tại, một lựa chọn ép class proxy có thể có phạm vi rộng hơn bạn tưởng. **`ProxyFactory` dựng riêng một target trong ví dụ sẽ không tự thiết lập mặc định cho mọi bean của container.**

## 7. Giới hạn thực tế khi dùng CGLIB

### 7.1. `final`, `private` và khả năng override

CGLIB tạo subclass runtime, nên:

- **Class `final`:** không thể subclass để tạo CGLIB class proxy.
- **Method `final`:** subclass không thể override, nên advice theo CGLIB không chặn được lời gọi tới method đó.
- **Method `private`:** không thể override, nên không thể advise bằng class proxy.
- **Method package-private không nhìn thấy từ subclass** trong một số trường hợp, đặc biệt inherited method từ superclass ở package khác, cũng không thể advise theo cách thông thường.

Với JDK dynamic proxy, method thuộc interface được chuyển qua invocation handler của proxy; vì vậy `final` trên implementation method **không tự ngăn advice** khi lời gọi được thực hiện qua interface được proxy. Đây là sự khác biệt cần hiểu chính xác khi chuyển chiến lược.

Ví dụ minh họa **không chạy độc lập**:

~~~java
public final class FinalService implements BusinessApi {
    @Override
    public final String run() {
        return "ok";
    }
}
~~~

Nếu `BusinessApi` là interface phù hợp, **JDK proxy** vẫn có thể dùng để advice `BusinessApi.run()`. Ngược lại, ép **CGLIB class proxy** cho `FinalService` thất bại vì không thể tạo subclass của class final.

### 7.2. Self-invocation

~~~java
public void outer() {
    this.inner();
}
~~~

Đoạn trên là **fragment minh họa**. Khi caller gọi `proxy.outer()`, target thực hiện `outer`. Lệnh `this.inner()` trong target đi qua `this` của target, không qua proxy. Vì vậy advice ở `inner` không tự chạy chỉ bằng việc đổi `setProxyTargetClass(false)` sang `true`.

Giải pháp thường tốt hơn là tách logic cần advice sang một bean riêng và gọi bean đó qua Spring-managed proxy, hoặc tổ chức ranh giới service rõ ràng. Có thể sử dụng `AopContext.currentProxy()` khi bật `exposeProxy` nhưng làm mã nghiệp vụ phụ thuộc vào AOP infrastructure và không phải lựa chọn mặc định.

### 7.3. Constructor và vòng đời

Spring AOP chủ yếu advice **method execution qua proxy**, không tự biến constructor thành join point được advice. Khi dùng CGLIB, Spring thường dùng Objenesis để tránh gọi constructor thêm một lần chỉ vì tạo proxy; không nên xây logic dựa vào dự đoán số lượt constructor sẽ chạy trong mọi runtime.

Hãy tránh gọi method có thể bị advice từ constructor hoặc dựa vào trạng thái initialization chưa hoàn chỉnh. Với target chứa mutable state, nhớ rằng proxy CGLIB làm nhiệm vụ chuyển lời gọi đến target theo cơ chế Spring; không dựa vào việc đọc trực tiếp field của proxy để quan sát trạng thái target.

## 8. Cách kiểm tra loại proxy trong ứng dụng thực

Spring có các utility:

~~~java
import org.springframework.aop.support.AopUtils;

boolean aopProxy = AopUtils.isAopProxy(bean);
boolean jdkProxy = AopUtils.isJdkDynamicProxy(bean);
boolean cglibProxy = AopUtils.isCglibProxy(bean);
~~~

Các dòng trên là **fragment minh họa** với biến `bean` đã tồn tại. `AopUtils.isAopProxy` trả về liệu object có phải Spring AOP proxy theo cơ chế Spring nhận diện hay không. Hai phương thức còn lại giúp phân biệt đường tạo proxy.

Nếu cần kiểm tra sâu hơn, có thể kiểm tra:

- Bean trả về từ context có đúng là proxy được inject hay không.
- `bean instanceof RequiredApi` hoặc `bean instanceof ConcreteService` có phù hợp với nơi sử dụng hay không.
- Advisor/pointcut áp dụng, thứ tự interceptor và các method nào có thể được intercept.
- Method được gọi **qua proxy** thật hay là lời gọi từ chính target.
- Log/audit/result thực tế cho phương thức interface-only, class-only, final và self-invocation.

Trong kiểm thử Spring có thể dùng `AopTestUtils.getTargetObject(proxy)` của `spring-test` để lấy target phục vụ việc kiểm tra chuyên biệt, nhưng không nên lấy target ra rồi gọi trực tiếp để chứng minh advice hoạt động; điều đó đi vòng qua proxy.

## 9. Những lỗi dễ gặp khi chọn loại proxy

| Dấu hiệu | Nguyên nhân hay gặp | Cách xử lý |
| --- | --- | --- |
| `ClassCastException` khi cast proxy sang implementation | Đang dùng JDK dynamic proxy | Inject/cast theo interface hoặc cấu hình class proxy khi thực sự cần |
| Method có trên implementation nhưng không gọi được qua biến interface proxy | Method không khai báo trong interface được proxy | Bổ sung hợp đồng interface phù hợp hoặc dùng class proxy |
| `Cannot subclass final class` hoặc lỗi tạo CGLIB proxy | Target class là `final` | Cân nhắc interface-based proxy, bỏ final nếu thiết kế cho phép |
| Advice không chạy trên method `final` với CGLIB | Không thể override method final | Dùng interface proxy nếu phù hợp, hoặc thay thiết kế method |
| `proxy.outer()` có advice nhưng `this.inner()` không | Self-invocation đi qua target | Tách bean/lời gọi phải đi qua proxy |
| Có proxy nhưng method vẫn không được advice | Pointcut không match, loại method không thể proxy hoặc chuỗi bị chặn | Kiểm tra pointcut, kiểu proxy, thứ tự advisor và đường lời gọi |
| Trong Spring Boot dùng CGLIB dù service có interface | Cấu hình auto-proxy có thể ép class proxy | Kiểm tra `spring.aop.proxy-target-class` và cấu hình AOP thực tế |
| Chạy test được với `ProxyFactory` nhưng bean trong context khác loại proxy | Context có chính sách auto-proxy riêng | Test bằng chính bean inject từ context và kiểm tra `AopUtils` |

### Không dùng kiểu proxy làm tiêu chí duy nhất

Lựa chọn JDK hay CGLIB không chứng minh rằng advice đã chạy. Một proxy có thể tồn tại nhưng method không match pointcut, method chưa được expose qua interface, hoặc lời gọi đã bypass proxy. Vì vậy test phải kết hợp **kiểu proxy**, **kiểu có thể cast**, **method được gọi** và **kết quả advice**.

## 10. Quy trình lựa chọn và kiểm chứng

1. **Xác định contract:** caller cần API interface hay cần dùng implementation?
2. **Chọn kiểu proxy:** khi caller làm việc qua interface, ưu tiên giao diện rõ ràng; khi cần class-only methods hoặc kiểu concrete, cân nhắc class proxy.
3. **Kiểm tra khả năng kế thừa:** với CGLIB, lớp không được final và method cần advice phải override được.
4. **Cấu hình bằng API:** `ProxyFactory(target)`, `setInterfaces(...)` hoặc `setProxyTargetClass(true)`, thêm advice/advisor rồi gọi `getProxy()`.
5. **Giới hạn pointcut:** chọn những method thực sự cần advice; `Pointcut.TRUE` là toàn bộ phạm vi proxy, không phải chỉ những method quan trọng.
6. **Đảm bảo đi qua proxy:** nơi gọi sử dụng object trả bởi `getProxy()` hoặc bean proxy từ context.
7. **Kiểm thử:** dùng `AopUtils` để xác định loại proxy, gọi method qua proxy và xác minh advice, exception, giá trị trả về; kiểm tra riêng self-invocation.
8. **Đối chiếu với container:** nếu chuyển sang Spring Boot/auto-proxy, kiểm tra cấu hình thực tế vì mặc định và phạm vi có thể thay đổi.

## 11. Câu hỏi tự đánh giá

1. Một `OrderServiceImpl implements OrderService` được đưa vào `new ProxyFactory(target)`. Với cấu hình mặc định và interface được nhận diện, proxy thường thuộc loại nào?
2. Vì sao có thể ép CGLIB proxy về `OrderServiceImpl` nhưng không nên ép JDK dynamic proxy về implementation?
3. `factory.setProxyTargetClass(false)` có bảo đảm Spring luôn dùng JDK proxy nếu target không có interface không?
4. Nếu `setInterfaces(OrderService.class)` và `setProxyTargetClass(true)` đều được gọi, lựa chọn nào quyết định class proxy?
5. Method `final` trên implementation có ngăn JDK proxy intercept phương thức tương ứng trên interface không? Vì sao với CGLIB lại khác?
6. Vì sao `classProxy.localOnly()` được advice nhưng `classProxy.outer()` gọi `this.localOnly()` bên trong lại không tạo thêm advice?
7. Bạn sẽ dùng `AopUtils` kiểm tra kiểu proxy thế nào và cần kiểm tra thêm điều gì để xác nhận advice đã chạy?
8. Vì sao ứng dụng Spring Boot có thể dùng CGLIB dù target implement interface, trong khi `ProxyFactory` dựng đơn lẻ mặc định dùng JDK proxy?

## 12. Tài liệu Spring chính thức

- [Creating AOP Proxies Programmatically with ProxyFactory](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html) — cách tạo proxy trực tiếp, đăng ký advice/advisor và auto-detect interface.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html) — sự khác biệt giữa JDK dynamic proxy và CGLIB, self-invocation, `final`/`private`.
- [Using ProxyFactoryBean to Create AOP Proxies](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pfb.html) — `proxyInterfaces`, `proxyTargetClass` và thứ tự interceptor trong Spring container.
- [Spring Framework AopUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/support/AopUtils.html) — kiểm tra một object có phải Spring AOP proxy hay không và loại proxy.
- [Spring Framework ProxyFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/framework/ProxyFactory.html) — Java API tạo và cấu hình proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="programmatic-proxy-creation-008">Tạo proxy ngoài IoC và trách nhiệm quản lý phụ thuộc, vòng đời</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo Spring AOP proxy ngoài IoC: quản lý phụ thuộc và vòng đời

## 1. Bài toán và phạm vi

Spring AOP cho phép tạo proxy **theo cách lập trình** bằng `org.springframework.aop.framework.ProxyFactory` mà không cần khởi động `ApplicationContext`. Cách này hữu ích khi một chương trình Java, thư viện hoặc adapter muốn đưa advice vào những đối tượng được tạo thủ công.

Tuy nhiên, `ProxyFactory` **chỉ đảm nhiệm phần AOP proxy**. Khi không có Spring IoC container quản lý đối tượng, ứng dụng phải tự:

- Tạo và liên kết các phụ thuộc mà target cần.
- Khởi tạo trạng thái/tài nguyên của target và các phụ thuộc trước khi sử dụng.
- Xác định ai sở hữu đối tượng được dùng chung, ai có quyền đóng tài nguyên và thời điểm đóng.
- Giữ đối tượng proxy làm đường truy cập nghiệp vụ cho caller.
- Xử lý lỗi trong quá trình khởi tạo và thực hiện dọn dẹp khi công việc kết thúc.

Ví dụ, một `OrderService` cần `OrderRepository` để lưu dữ liệu. Việc gọi `factory.setTarget(orderService)` không tự tạo repository, không tự thực hiện dependency injection hay gọi lifecycle callbacks kiểu Spring bean. Nếu repository dùng kết nối hoặc file cần đóng, đó là trách nhiệm của bên đã tạo và sở hữu tài nguyên.

Bài này tập trung vào **hành vi cụ thể của Spring AOP khi tạo proxy ngoài IoC**. Các chi tiết chung về Aspect không thuộc phạm vi.

## 2. ProxyFactory tạo ra những gì và không tạo ra những gì?

Quy trình tối thiểu:

```text
Mã ứng dụng (owner)
  ├── tạo repository / resource
  ├── khởi tạo resource
  ├── tạo target và truyền dependency
  ├── tạo ProxyFactory
  │     ├── setTarget(target)
  │     ├── addInterface(Service.class) nếu cần
  │     └── addAdvisor(pointcut + advice)
  ├── getProxy() → đưa proxy cho caller
  └── cuối vòng đời → dừng công việc, đóng resource mình sở hữu
```

Spring AOP làm các việc này:

1. Nhận target hoặc `TargetSource` cùng cấu hình proxy.
2. Nhận các `Advice`/`Advisor` để xây dựng interceptor chain.
3. Tạo đối tượng proxy phù hợp, thường là **JDK dynamic proxy** nếu dùng interface hoặc **CGLIB proxy** nếu proxy theo lớp.
4. Với lời gọi đi qua proxy và được pointcut chọn, chạy advice trước/sau/quanh lời gọi target.

Spring AOP **không tự thực hiện** các bước mà Spring IoC thường đảm nhiệm cho bean, như giải quyết `@Autowired`, tiêm `@Value`, áp dụng `BeanPostProcessor` hoặc tự động gọi callback khởi tạo/hủy bean khi bạn chỉ làm `new` và `ProxyFactory`. `getProxy()` cũng không phải `ApplicationContext.refresh()` hay `close()`.

Điểm quan trọng: Spring AOP vẫn là thư viện Spring, dù **không dùng Spring container**. Một proxy lập trình vẫn có thể sử dụng đầy đủ interface `Advice`, `Advisor`, `Pointcut` của Spring AOP.

## 3. Vai trò của target, proxy, dependency và resource

| Đối tượng | Ví dụ | Được tạo bởi | Ai quản lý vòng đời trong ví dụ ngoài IoC? |
| --- | --- | --- | --- |
| Dependency | `OrderRepository`, connection pool | Mã ứng dụng hoặc nhà cung cấp tài nguyên | Bên tạo/sở hữu dependency, theo hợp đồng thư viện |
| Target | `OrderServiceImpl` | `new OrderServiceImpl(repository)` | Mã ứng dụng; target không thành Spring bean chỉ do được proxy |
| Advice | `MethodBeforeAdvice` | Lambda/class do ứng dụng tạo | Mã ứng dụng; nếu advice giữ resource, cũng phải dọn dẹp resource đó |
| Advisor | `DefaultPointcutAdvisor` | Mã ứng dụng | Cấu hình AOP được gắn vào factory/proxy |
| Proxy | `(OrderService) factory.getProxy()` | Spring AOP | Caller sử dụng; owner điều phối việc ngừng dùng proxy |
| Factory | `ProxyFactory` | Mã ứng dụng | Đối tượng cấu hình; không phải container quản lý vòng đời nghiệp vụ |

**Phân biệt “sở hữu” và “tham chiếu”:** proxy có thể giữ tham chiếu đến target, target giữ tham chiếu repository, nhưng điều đó không có nghĩa việc GC hoặc bỏ biến proxy sẽ chủ động đóng repository. Nếu bên tạo đã trao quyền sở hữu tài nguyên cho component khác, phải ghi rõ hợp đồng bàn giao và tránh đóng hai lần.

Một sơ đồ gọi phương thức:

```text
Caller
  │  service.save("A1")
  ▼
OrderService proxy
  │  NameMatchMethodPointcut("save*") → MethodBeforeAdvice
  ▼
OrderServiceImpl target
  │  repository.save("A1")
  ▼
Repository đã được tạo và mở bằng mã ứng dụng
```

Khi method được gọi trực tiếp qua `target.save(...)`, advice trên proxy không chạy. Khi `target` tự gọi `this.anotherMethod()`, đó cũng là self-invocation bỏ qua proxy cho lời gọi nội bộ.

## 4. Khởi tạo dependency thủ công trước khi tạo proxy

Với `ProxyFactory` ngoài IoC, cách rõ ràng là **constructor injection bằng Java thông thường**:

```java
OrderRepository repository = new JdbcOrderRepository(dataSource);
OrderService target = new OrderServiceImpl(repository);

ProxyFactory factory = new ProxyFactory();
factory.setTarget(target);
factory.addInterface(OrderService.class);
OrderService proxy = (OrderService) factory.getProxy();
```

Đây chỉ là **đoạn cấu hình minh họa**; các kiểu `OrderRepository`, `JdbcOrderRepository` và `dataSource` đại diện cho ứng dụng. Đoạn này không phải một chương trình biên dịch độc lập. Điều kiện tiên quyết là bên tạo đã có `dataSource` hợp lệ và biết ai sẽ đóng `dataSource`.

Với các phụ thuộc bắt buộc, ưu tiên truyền qua constructor và kiểm tra `Objects.requireNonNull(...)`. Điều đó làm ranh giới khởi tạo rõ ràng và tránh việc service bị gọi khi repository chưa được gắn.

**Không nhầm với dependency injection của Spring:**

```java
class OrderServiceImpl {
    @Autowired
    private OrderRepository repository;
}
```

Nếu chỉ chạy `new OrderServiceImpl()` ngoài container rồi tạo `ProxyFactory`, `repository` **không tự được tiêm**. Annotation `@Autowired` không phải cơ chế tự chạy trong quá trình tạo proxy. Muốn dùng đối tượng do Spring tiêm phụ thuộc, hãy lấy bean đã được tạo từ `ApplicationContext` (khi thật sự có container), hoặc dùng API quản lý bean của Spring có chủ đích. Việc gọi `ProxyFactory` tự thân không kích hoạt các bước đó.

## 5. Trách nhiệm quản lý vòng đời khi không có ApplicationContext

Trong mô hình IoC, container có thể chạy callback như `@PostConstruct`/`@PreDestroy`, `InitializingBean.afterPropertiesSet()`, `DisposableBean.destroy()`, hay init/destroy method được khai báo cho bean. Các callback này gắn với **Spring bean lifecycle**.

Với target tạo bởi `new` và proxy tạo thủ công:

- Không tự phát sinh callback `@PostConstruct`, `@PreDestroy`, `afterPropertiesSet()` hoặc `destroy()` chỉ vì có proxy.
- Nếu target yêu cầu `start()`/`initialize()`, bên tạo phải gọi đúng lúc hoặc có cơ chế khởi tạo riêng của thư viện.
- Nếu dependency có `AutoCloseable.close()`, bên sở hữu phải sắp xếp gọi `close()` khi không còn ai sử dụng.
- `getProxy()` không trả về đối tượng “quản lý resource tự động”. Không nên giả định ép kiểu proxy sang `AutoCloseable` là đủ để tự giải phóng mọi thứ bên dưới.
- Không nên chỉ dựa vào finalizer/GC hoặc shutdown hook để thay cho việc quản lý tài nguyên có cấu trúc.

Các thứ cần làm rõ trong thiết kế thực tế:

1. **Điều kiện bắt đầu:** dependency nào phải được mở trước khi service có thể chạy?
2. **Cách phát hành proxy:** cho caller nhận proxy sau khi đã lắp đầy đủ advice và dependency.
3. **Phạm vi chia sẻ:** một proxy/target có dùng giữa nhiều thread hay nhiều request không? Nếu có, trạng thái của target và repository phải an toàn khi truy cập đồng thời.
4. **Thứ tự đóng:** ngừng nhận lời gọi mới, chờ xử lý đang chạy nếu cần, rồi đóng tài nguyên mà owner thực sự sở hữu.
5. **Đường lỗi:** nếu tạo proxy hoặc khởi tạo target thất bại sau khi đã mở resource, phải đóng những tài nguyên đã mở thành công.

Cơ chế `try-with-resources` là một cách hữu ích cho đơn vị sở hữu tài nguyên triển khai `AutoCloseable`. Bài thực hành ở phần 7 dùng một wrapper sở hữu cả proxy và repository để thực hiện điều này rõ ràng.

## 6. API tạo proxy bên ngoài IoC

Ví dụ Spring AOP API cơ bản:

```java
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

OrderService target = new OrderServiceImpl(repository);

NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
pointcut.setMappedName("save*");

MethodBeforeAdvice advice = (method, args, object) ->
        System.out.println("[audit] " + method.getName());

ProxyFactory factory = new ProxyFactory();
factory.setTarget(target);
factory.addInterface(OrderService.class);
factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, advice));
OrderService proxy = (OrderService) factory.getProxy();
```

Đây là **fragment** sử dụng các kiểu nghiệp vụ chưa được khai báo trong chính đoạn mã này. `setTarget` cung cấp đối tượng nghiệp vụ, `addInterface` công khai interface của proxy và `addAdvisor` gắn pointcut với advice.

Một cách viết ngắn hơn:

```java
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvisor(new DefaultPointcutAdvisor(pointcut, advice));
OrderService proxy = (OrderService) factory.getProxy();
```

Constructor nhận target thường tự nhận diện các interface mà target triển khai. Khi cần kiểm soát chính xác interface có thể thấy qua proxy, dùng cấu hình `addInterface`/`setInterfaces` phù hợp. Nếu chọn `setProxyTargetClass(true)`, cần chú ý giới hạn CGLIB với class/method `final`.

Không nhầm `addAdvice(advice)` và `addAdvisor(pointcutAdvisor)`: `addAdvice` được Spring đóng gói với `Pointcut.TRUE` nên có thể áp dụng rộng cho các phương thức được proxy chặn; `addAdvisor` cho phép dùng điều kiện pointcut cụ thể.

## 7. Ví dụ Java 17+ hoàn chỉnh: tự lắp dependency, proxy và cleanup

Ví dụ chạy trên Java 17+, Spring Framework 6.2 và Gradle. Nó thể hiện:

- Một repository giả lập tài nguyên cần `start()` và `close()`.
- Một service nhận repository qua constructor.
- Một `NameMatchMethodPointcut` chỉ audit method `save*`.
- Một `ProxyScope` sở hữu vòng đời repository, cung cấp proxy cho caller.
- Đóng tài nguyên ngay cả khi body của `try` ném ngoại lệ; dọn dẹp tài nguyên nếu bước dựng proxy thất bại.

Cấu trúc:

```text
manual-proxy-lifecycle/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/ManualProxyLifecycleDemo.java
```

Tệp `settings.gradle`:

```groovy
rootProject.name = 'manual-proxy-lifecycle'
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

dependencies {
    implementation 'org.springframework:spring-aop:6.2.19'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.ManualProxyLifecycleDemo'
}
```

Spring AOP có dependency transitives do Gradle giải quyết. Nếu chuyển đoạn này sang dự án Spring Boot, nên để phiên bản Spring Framework do Boot quản lý để các thành phần đồng bộ.

Tệp `src/main/java/demo/ManualProxyLifecycleDemo.java`:

```java
package demo;

import java.util.Objects;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class ManualProxyLifecycleDemo {

    public interface OrderService {
        void save(String id);
        String find(String id);
    }

    // Giả lập resource có vòng đời, ví dụ một client giữ kết nối.
    static final class Repository implements AutoCloseable {
        private boolean active;

        void start() {
            if (active) {
                throw new IllegalStateException("Already started");
            }
            active = true;
            System.out.println("[repo] started");
        }

        void save(String id) {
            checkActive();
            System.out.println("[repo] saved " + id);
        }

        String find(String id) {
            checkActive();
            System.out.println("[repo] found " + id);
            return "order-" + id;
        }

        private void checkActive() {
            if (!active) {
                throw new IllegalStateException("Repository is closed");
            }
        }

        @Override
        public void close() {
            if (active) {
                active = false;
                System.out.println("[repo] closed");
            }
        }
    }

    static final class OrderServiceImpl implements OrderService {
        private final Repository repository;

        OrderServiceImpl(Repository repository) {
            this.repository = Objects.requireNonNull(repository);
        }

        @Override
        public void save(String id) {
            repository.save(id);
        }

        @Override
        public String find(String id) {
            return repository.find(id);
        }
    }

    // Scope sở hữu resource, còn caller chỉ được nhận service proxy.
    static final class ProxyScope implements AutoCloseable {
        private final Repository repository;
        private final OrderService service;
        private boolean closed;

        private ProxyScope(Repository repository, OrderService service) {
            this.repository = repository;
            this.service = service;
        }

        static ProxyScope open() {
            Repository repository = new Repository();
            try {
                repository.start();

                // Chủ động tiêm dependency bằng constructor.
                OrderService target = new OrderServiceImpl(repository);

                // Cấu hình AOP không cần ApplicationContext.
                NameMatchMethodPointcut pointcut =
                        new NameMatchMethodPointcut();
                pointcut.setMappedName("save*");

                MethodBeforeAdvice audit = (method, args, object) ->
                        System.out.println("[audit] " + method.getName());

                ProxyFactory factory = new ProxyFactory();
                factory.setTarget(target);
                factory.addInterface(OrderService.class);
                factory.addAdvisor(
                        new DefaultPointcutAdvisor(pointcut, audit));

                OrderService proxy =
                        (OrderService) factory.getProxy();

                return new ProxyScope(repository, proxy);
            } catch (RuntimeException | Error ex) {
                // Nếu lắp target/proxy lỗi, không bỏ quên resource.
                repository.close();
                throw ex;
            }
        }

        OrderService service() {
            if (closed) {
                throw new IllegalStateException("Scope is closed");
            }
            return service;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                repository.close();
            }
        }
    }

    public static void main(String[] args) {
        try (ProxyScope scope = ProxyScope.open()) {
            OrderService service = scope.service();

            service.save("A1");
            String result = service.find("A1");
            System.out.println("[caller] " + result);
        }
    }
}
```

Chạy từ thư mục dự án:

```bash
gradle run
```

Nếu dự án đã có Gradle Wrapper, dùng `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

Stdout chương trình, không gồm thông báo của Gradle:

```text
[repo] started
[audit] save
[repo] saved A1
[repo] found A1
[caller] order-A1
[repo] closed
```

Diễn giải kết quả:

1. `ProxyScope.open()` tạo `Repository` rồi gọi `start()`; không có `@PostConstruct` hay Spring IoC làm thay.
2. `new OrderServiceImpl(repository)` gắn phụ thuộc qua constructor, nên target sẵn sàng sử dụng trước khi tạo proxy.
3. `ProxyFactory` gắn target, khai báo interface `OrderService` và advisor chỉ chọn tên `save*`. Phương thức `save("A1")` đi qua proxy nên dòng `[audit] save` xuất hiện trước `[repo] saved A1`.
4. `find("A1")` không khớp pointcut `save*`, nên chỉ repository in `[repo] found A1` và caller in kết quả trả về.
5. Kết thúc block `try-with-resources` gọi `ProxyScope.close()`, từ đó đóng repository một lần.
6. Nếu việc tạo service/proxy ném `RuntimeException` hoặc `Error` trong `open()`, nhánh `catch` đóng repository trước khi ném tiếp. Đây là điểm quan trọng vì chưa có scope trả về để caller đóng.

Ở ví dụ này, `Repository` là resource giả lập để output ổn định. Trong ứng dụng thật, phương thức `close()` có thể giải phóng socket, executor hoặc client mà owner thực sự sở hữu. Nếu công việc diễn ra song song, cần chính sách ngừng nhận request/chờ tác vụ đang chạy trước khi đóng, và các biến trạng thái cần được đồng bộ thích hợp.

## 8. Khi có nhiều phụ thuộc và dọn dẹp theo thứ tự

Một target có thể phụ thuộc vào `HttpClient`, connection pool, event dispatcher hoặc thread pool. Bên dựng proxy phải biết phụ thuộc nào là **tài nguyên tự tạo** và phụ thuộc nào được **mượn** từ bên khác.

Ví dụ chuỗi sở hữu:

```text
ApplicationOwner
  ├── owns ExecutorService
  ├── owns RepositoryClient
  └── owns Target + AOP proxy
          └── borrows RepositoryClient
```

Nguyên tắc:

- Khởi tạo đầy đủ dependency trước khi công bố proxy cho nơi khác.
- Một resource do bạn tạo và sở hữu phải được đóng theo hợp đồng API của nó; resource mượn thì trả lại/không tự đóng nếu bên cho mượn quản lý.
- Đóng theo **thứ tự ngược với việc phụ thuộc vào nhau** nếu việc dừng tầng trên cần tầng dưới còn hoạt động.
- Nếu tạo resource B thất bại sau khi A đã mở, hãy đóng A trong nhánh xử lý lỗi. `try-with-resources` lồng nhau hoặc một scope `AutoCloseable` là cách triển khai.
- Nếu nhiều proxy chia sẻ một repository, đóng ở owner chia sẻ cuối cùng sau khi mọi consumer ngừng dùng, thay vì để mỗi proxy tự đóng cùng một repository.
- Khi resource thực sự dùng nhiều thread, việc đóng và đọc/ghi cần cơ chế concurrency safety của thư viện/resource; `ProxyFactory` không bổ sung thread safety cho target.

**Không suy ra** rằng `ProxyFactory.getProxy()` có method “destroy target” hay tự tìm và gọi mọi `close()`. `ProxyFactory` tạo proxy, còn ownership của tài nguyên thuộc cấu trúc ứng dụng hoặc một `TargetSource`/infrastructure chuyên biệt nếu bạn cố ý sử dụng.

## 9. Trường hợp kết hợp Spring IoC và ProxyFactory thủ công

Có hai kịch bản phải phân biệt:

### 9.1. Target được tạo hoàn toàn ngoài IoC

```java
Repository repository = new Repository();
repository.start();
OrderService target = new OrderServiceImpl(repository);

ProxyFactory factory = new ProxyFactory(target);
factory.addAdvisor(auditAdvisor);
OrderService service = (OrderService) factory.getProxy();
```

Ở đây, chủ sở hữu trực tiếp chịu trách nhiệm dependency và resource lifecycle. Các biến `Repository`, `OrderService` và `auditAdvisor` giả định đã có trong project; đoạn này **không độc lập**.

### 9.2. Target là Spring-managed bean, proxy mới được dựng bằng tay

```java
OrderService managedTarget =
        applicationContext.getBean(OrderService.class);

ProxyFactory factory = new ProxyFactory(managedTarget);
factory.addAdvice(extraAdvice);
OrderService decorated = (OrderService) factory.getProxy();
```

Lúc này, **Spring IoC vẫn sở hữu vòng đời của bean** mà `applicationContext` cấp. Bên tạo proxy chỉ sở hữu lớp trang trí AOP bổ sung và tài nguyên riêng nếu có; không nên tự `close()` hoặc `destroy()` bean được context quản lý. Nếu `managedTarget` vốn đã là một proxy AOP của Spring, việc bọc tiếp có thể tạo **hai lớp proxy**, dẫn đến thứ tự advice khó hiểu hoặc áp dụng advice hai lần. Kiểm tra `AopUtils.isAopProxy(managedTarget)` và kiến trúc AOP hiện hành trước khi bọc.

Nếu muốn Spring tự quản lý target, dependency injection, lifecycle và auto-proxy cho một ứng dụng lâu dài, nên khai báo bean/`Advisor` và hạ tầng AOP trong container thay vì dựng toàn bộ bằng tay.

## 10. Những giới hạn của Spring AOP proxy ngoài IoC

| Trường hợp | Hành vi đúng | Điều cần làm |
| --- | --- | --- |
| Caller giữ trực tiếp `target` | Không đi qua advice trên proxy | Chỉ công bố interface proxy cho caller |
| Target gọi `this.save()` nội bộ | Self-invocation không quay qua proxy | Tách ranh giới bean/service hoặc tổ chức lại đường gọi |
| Method `private`/`final` với CGLIB | Không thể intercept theo override thông thường | Kiểm tra thiết kế target, kiểu proxy |
| Proxy JDK, caller muốn cast sang implementation | JDK proxy thường không phải subclass target | Dùng interface proxy hoặc chọn class-based proxy có chủ đích |
| Chỉ thêm `@Autowired` lên field của target `new` | Không tự được inject | Truyền dependency qua constructor/manual composition |
| Target có `@PostConstruct`/`@PreDestroy` | Không tự được gọi bởi `ProxyFactory` thuần | Dùng lifecycle thủ công hoặc container thật |
| Không có reference đến proxy nhưng resource còn sống | GC không đồng nghĩa resource đã `close()` | Ràng buộc cleanup qua owner, `try-with-resources` |
| Nhiều caller dùng chung cùng proxy/target | Truy cập đồng thời có thể xảy ra | Bảo đảm thread safety của target và dependency |
| Advice phụ thuộc chính resource đã đóng | Advice có thể lỗi khi proxy vẫn được gọi | Ngừng sử dụng proxy trước khi đóng dependency |
| Đóng target lấy từ `ApplicationContext` | Có thể đóng nhầm tài nguyên container còn cần | Tôn trọng ownership của container |

Còn một giới hạn về cấu hình: Spring AOP chỉ chặn được những lời gọi hợp lệ đi qua proxy; `Pointcut` có khớp tên cũng không biến cuộc gọi trực tiếp bên trong đối tượng thành lời gọi proxy. Việc dùng API thủ công không thay đổi quy tắc này.

## 11. Cách kiểm tra proxy và tư duy chẩn đoán

Khi muốn biết vì sao advice không chạy hoặc vì sao target có resource sai trạng thái, kiểm tra riêng **phần AOP** và **phần lifecycle**:

**AOP:**

```java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

boolean isProxy = AopUtils.isAopProxy(service);
boolean jdkProxy = AopUtils.isJdkDynamicProxy(service);

if (service instanceof Advised advised) {
    System.out.println(advised.getAdvisors().length);
}
```

Đây là đoạn kiểm tra minh họa, biến `service` giả định đã có. Không ép kiểu mọi đối tượng thành `Advised` nếu chưa biết đó là Spring proxy triển khai interface này.

**Lifecycle/ownership:**

- Dependency được tạo ở đâu? Ai đã gọi `start()`/constructor thiết lập tài nguyên?
- Target nhận đúng object thông qua constructor/setter chưa?
- Proxy được tạo và đưa cho caller sau khi resource sẵn sàng chưa?
- Ai là owner, ai đóng tài nguyên, và sau khi đóng còn request nào có thể chạy không?
- Khi tạo proxy lỗi nửa chừng, những gì đã mở đã được dọn dẹp chưa?
- Có trộn một bean do container sở hữu với resource tự tạo khiến cùng một đối tượng bị đóng hai lần không?

Không có method Spring AOP nào thay thế được việc trả lời rõ các câu hỏi ownership này.

## 12. Checklist áp dụng cho dự án Spring thật

Trước khi quyết định tạo `ProxyFactory` bên ngoài IoC, nên xác định:

1. **Lý do cần manual proxy:** adapter chạy ngoài context, unit test, ứng dụng Java không dùng container, hay một object được tạo động.
2. **Đường đi dependency:** tất cả dependency bắt buộc được cấp rõ ràng; tránh mong đợi annotation injection tự chạy.
3. **Điểm bắt đầu vòng đời:** tạo/mở tài nguyên trước khi công bố proxy; thất bại phải rollback những resource đã sở hữu.
4. **Hợp đồng cho caller:** đưa proxy qua interface; không làm lộ target thô nếu caller phải luôn chịu advice.
5. **Điểm dừng vòng đời:** ai chịu trách nhiệm ngừng gọi proxy, đóng target/dependency và xử lý lời gọi đang chạy.
6. **Kiểm thử behavior:** ít nhất xác nhận advice chạy khi gọi proxy, không chạy khi bypass proxy, pointcut không áp dụng sai method và cleanup xảy ra cả nhánh success/error.
7. **Tích hợp IoC về sau:** nếu chuyển sang Spring-managed bean, xem lại phân công ownership, thứ tự callback và nguy cơ proxy bọc proxy.

Một thiết kế đơn giản và an toàn thường là có một **factory/owner** tạo dependency và target, ghép `ProxyFactory` một lần, công bố proxy khi hoàn chỉnh, và có một phương thức đóng rõ ràng cho toàn bộ phạm vi sử dụng.

## 13. Tài liệu Spring chính thức

- [Creating AOP Proxies Programmatically with ProxyFactory](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html): Spring khẳng định có thể tạo AOP proxy không phụ thuộc Spring IoC; các API thêm advice/advisor.
- [Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advised.html): `Advised`, quản lý advisor/advice và proxy đã tạo.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB và self-invocation.
- [Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html): lifecycle callback thuộc quy trình quản lý bean của container.
- [ProxyFactory Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/framework/ProxyFactory.html): API `ProxyFactory`.

**Ghi nhớ:** `ProxyFactory` cung cấp đường gọi có advice; nó không nhận thay vai trò quản lý dependency hay lifecycle của Spring IoC. Khi tạo proxy ngoài container, hãy quản lý target và những resource thuộc sở hữu của mình bằng một ranh giới vòng đời tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="programmatic-proxy-creation-009">Tránh bọc thêm proxy khi bean đã được tăng cường</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh bọc thêm proxy khi bean đã được tăng cường trong Spring AOP

## 1. Khi nào xảy ra việc bọc proxy lần nữa?

Trong Spring, một bean có thể đã được Spring AOP tăng cường trước khi mã của chúng ta nhận được nó. Ví dụ, bean đã được xử lý bởi `@EnableAspectJAutoProxy`, một `Advisor` dùng cho `@Transactional`, hoặc một cơ chế tạo Spring AOP proxy khác. Nếu tiếp tục truyền chính bean proxy đó vào `new ProxyFactory(bean)` rồi gọi `getProxy()`, ta có thể tạo **proxy ngoài bao lấy proxy trong**:

```text
Client
  -> Proxy B (advice bổ sung thủ công)
      -> Proxy A (advice có sẵn của Spring)
          -> Bean đích
```

Cả hai lớp có thể cùng chặn một lời gọi. Việc lồng proxy đôi khi là chủ ý, nhưng nếu vô tình thì dễ dẫn đến chạy lặp logic logging/audit, khó xác định thứ tự advice, sai giả định về kiểu bean, hoặc khó chẩn đoán giao dịch và exception. **Không phải cứ thấy hai advice là có hai proxy:** một Spring AOP proxy hoàn toàn có thể chứa nhiều `Advisor` trong *cùng một chuỗi interceptor*.

Phạm vi bài này là xử lý **Spring AOP proxy khi dùng API tạo proxy và cơ chế bean của Spring**; không đi sâu vào khái niệm aspect tổng quát hay cách proxy của Java thuần.

## 2. Kiểm tra proxy theo API của Spring

| API | Ý nghĩa thực tế |
| --- | --- |
| `AopUtils.isAopProxy(bean)` | Bean hiện tại có phải Spring AOP proxy hay không, bất kể dùng JDK dynamic proxy hay CGLIB. Đây là kiểm tra đầu tiên trước khi tự tạo proxy. |
| `AopUtils.isJdkDynamicProxy(bean)` | Nhận diện Spring AOP proxy dựa trên JDK dynamic proxy. |
| `AopUtils.isCglibProxy(bean)` | Nhận diện Spring AOP proxy dựa trên CGLIB. |
| `bean instanceof Advised` | Proxy cho phép truy vấn cấu hình các advisor đang có và, nếu không bị khóa, có thể thêm/bớt advisor. |
| `((Advised) bean).getAdvisors()` | Liệt kê các advisor trên *lớp proxy đang kiểm tra* để chẩn đoán trùng lặp. |
| `((Advised) bean).isFrozen()` | Xác định cấu hình advice đã bị khóa; không được cố thêm advisor khi `true`. |

`AopUtils.isAopProxy(bean)` **khác** kiểm tra `Proxy.isProxyClass(bean.getClass())` của JDK: hàm JDK chỉ nhìn kiểu proxy động Java và không bao quát CGLIB, cũng không xác định một proxy có thuộc hạ tầng Spring AOP hay không. `bean.getClass()` cũng không phải cách đáng tin để suy ra lớp triển khai nghiệp vụ; khi cần kiểm tra loại đích cho chẩn đoán, có thể dùng `AopUtils.getTargetClass(bean)`.

Cần phân biệt hai câu hỏi:

1. **“Bean đã là Spring AOP proxy chưa?”** Nếu mục tiêu chỉ là đảm bảo có proxy, hãy trả lại bean nguyên trạng.
2. **“Advisor cụ thể của tôi đã có trên proxy chưa?”** Nếu nghiệp vụ bắt buộc phải gắn thêm một advisor, chỉ kiểm tra `isAopProxy` rồi bỏ qua là chưa đủ. Hãy quản lý advisor ở một nơi duy nhất, hoặc cân nhắc thêm advisor vào proxy hiện hữu khi được hỗ trợ.

## 3. Cách đơn giản: chỉ tạo proxy khi chưa có

Ví dụ sau dùng trực tiếp `ProxyFactory`, không phụ thuộc vào `ApplicationContext`. Ý tưởng là biến bước tăng cường thành thao tác **idempotent**: gọi lại với kết quả cũ không tạo thêm một lớp proxy mới.

Tạo `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aop-no-double-proxy</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-aop</artifactId>
            <version>6.2.12</version>
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
            </plugin>
        </plugins>
    </build>
</project>
```

Tạo `src/main/java/NoDoubleProxyDemo.java`:

```java
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public class NoDoubleProxyDemo {

    public interface GreetingService {
        String greet(String name);
    }

    public static class DefaultGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Xin chào " + name;
        }
    }

    // Nếu đối tượng đã là Spring AOP proxy, giữ đúng instance đó.
    static Object proxyOnlyOnce(Object candidate) {
        if (AopUtils.isAopProxy(candidate)) {
            return candidate;
        }

        ProxyFactory factory = new ProxyFactory(candidate);
        factory.addAdvice((MethodInterceptor) invocation -> {
            System.out.println("before: " + invocation.getMethod().getName());
            return invocation.proceed();
        });
        return factory.getProxy();
    }

    public static void main(String[] args) {
        GreetingService target = new DefaultGreetingService();

        Object first = proxyOnlyOnce(target);
        Object second = proxyOnlyOnce(first);

        System.out.println("same instance: " + (first == second));
        System.out.println("is Spring AOP proxy: " + AopUtils.isAopProxy(second));
        System.out.println(((GreetingService) second).greet("Lan"));
    }
}
```

Chạy từ thư mục chứa `pom.xml` với Java 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=NoDoubleProxyDemo
```

Kết quả chuẩn (không kể thông báo riêng của Maven):

```text
same instance: true
is Spring AOP proxy: true
before: greet
Xin chào Lan
```

- `first == second` là `true` vì lần thứ hai trả lại chính đối tượng proxy, không gọi `getProxy()` lần nữa.
- Dòng `before: greet` chỉ xuất hiện một lần. `ProxyFactory` tự nhận diện interface của target để tạo proxy phù hợp trong ví dụ này.
- Hàm cố ý có hợp đồng **“đã là Spring AOP proxy thì giữ nguyên”**, không có hợp đồng “mọi advice tôi mong muốn chắc chắn đều đang có”. Không dùng nhầm hàm này khi yêu cầu thật sự là thêm advice mới cho proxy đã tồn tại.

## 4. Bean đã được proxy nhưng vẫn cần thêm advisor thì sao?

Trong ứng dụng thực tế, một bean có thể đã có proxy cho transaction, trong khi phần mở rộng cần gắn thêm advice đo thời gian. Khi đó, chỉ `return bean` sẽ bỏ sót advice đo thời gian. Ưu tiên khai báo advice/pointcut thành một `Advisor` do Spring quản lý và để **cùng cơ chế auto-proxy** lắp chúng thành một chuỗi. Cách này giúp quyết định tập advisor và thứ tự tại lúc bean được khởi tạo, thay vì can thiệp bất ngờ sau đó.

Nếu bắt buộc phải thao tác ở mức API, Spring có giao diện `Advised`. Mẫu sau nhận **cùng một instance `Advisor` được quản lý ổn định** qua các lần gọi:

```java
import java.util.Arrays;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public final class AdvisorInstaller {
    private AdvisorInstaller() {}

    public static Object ensureAdvisor(Object bean, Advisor requiredAdvisor) {
        if (bean instanceof Advised advised) {
            boolean alreadyPresent = Arrays.stream(advised.getAdvisors())
                    .anyMatch(existing -> existing == requiredAdvisor);

            if (!alreadyPresent) {
                if (advised.isFrozen()) {
                    throw new IllegalStateException(
                            "Spring AOP proxy bị frozen; không thể thêm advisor");
                }
                advised.addAdvisor(requiredAdvisor);
            }
            return bean; // giữ nguyên proxy hiện hữu
        }

        if (AopUtils.isAopProxy(bean)) {
            // Có thể là proxy với cấu hình không phơi bày qua Advised.
            throw new IllegalStateException(
                    "Proxy Spring AOP hiện hữu không cung cấp Advised");
        }

        ProxyFactory factory = new ProxyFactory(bean);
        factory.addAdvisor(requiredAdvisor);
        return factory.getProxy();
    }
}
```

Đoạn `AdvisorInstaller` là **thành phần minh họa tích hợp**, cần đặt trong project có Spring AOP và truyền vào một `Advisor` thực tế; nó không phải chương trình `main` chạy độc lập.

Các điều kiện quan trọng khi áp dụng:

- So sánh `existing == requiredAdvisor` chỉ kiểm tra **cùng đối tượng advisor**. Nó đạt tính idempotent khi caller luôn truyền cùng một advisor singleton. Nếu mỗi lần khởi tạo `new DefaultPointcutAdvisor(...)`, phép kiểm tra này không phát hiện hai advisor khác instance nhưng cùng chức năng. Khi đó phải quản lý một định danh hoặc quy tắc tương đương rõ ràng ở tầng cấu hình.
- `Advised` có thể có cấu hình bị `frozen`; đừng lặng lẽ thêm proxy ngoài để né giới hạn. Nếu proxy không triển khai `Advised`, cũng đừng giả định có thể sửa chuỗi interceptor từ bên ngoài.
- Thêm/bớt advisor trên proxy đang phục vụ request có thể ảnh hưởng thứ tự advice và hành vi tại runtime. Hãy làm trong giai đoạn cấu hình/khởi tạo khi có thể, tránh sửa cấu hình AOP trong lúc có truy cập đồng thời.
- Không tùy tiện bổ sung *introduction advisor* cho proxy đã tạo, vì thay đổi giao diện của proxy sau khi tạo không được hỗ trợ như thêm advice thông thường.
- Kiểm tra “có advisor” phải theo **ý nghĩa thật của yêu cầu**. Một advisor logging đang có không đồng nghĩa với một advisor authorization hoặc transaction khác.

## 5. Khi làm trong Spring Container và BeanPostProcessor

Một điểm dễ mắc lỗi là tự triển khai `BeanPostProcessor#postProcessAfterInitialization` rồi tạo `ProxyFactory` cho mọi bean theo tên, trong khi `AnnotationAwareAspectJAutoProxyCreator` hoặc hạ tầng transaction đã tăng cường bean đó.

Ví dụ về **bước gác an toàn** khi thật sự sở hữu logic hậu xử lý riêng:

```java
@Override
public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!"reportService".equals(beanName)) {
        return bean;
    }
    if (AopUtils.isAopProxy(bean)) {
        return bean;
    }

    ProxyFactory factory = new ProxyFactory(bean);
    factory.addAdvisor(reportAdvisor);
    return factory.getProxy();
}
```

Đây chỉ là **phần thân phương thức** của một `BeanPostProcessor` có sẵn trường `reportAdvisor`; không phải class hoàn chỉnh. Điều kiện trên phù hợp **chỉ nếu yêu cầu là tránh tạo proxy thứ hai**, và chấp nhận rằng bean đã có Spring AOP proxy có thể chưa chứa `reportAdvisor`. Nếu advisor đó bắt buộc, hãy đăng ký advisor vào cấu hình Spring AOP để auto-proxy creator xét nó cùng các advisor còn lại, hoặc dùng hướng `Advised` có kiểm tra như mục 4.

Khi thiết kế `BeanPostProcessor`, còn cần lưu ý phạm vi bean được xử lý (tránh tự proxy hạ tầng Spring, chính advisor hoặc aspect), vòng đời tạo bean và trật tự các post-processor. Việc thay đổi thứ tự của hai post-processor tự tạo proxy có thể thay đổi kết quả cuối cùng; thêm một lệnh `isAopProxy` không thay thế được thiết kế hạ tầng AOP nhất quán.

Với cấu hình `@EnableAspectJAutoProxy`, `@EnableTransactionManagement` và các tính năng AOP của Spring, nên ưu tiên dùng cấu hình advisor của container thay vì tự xếp nhiều lớp `ProxyFactory`. Mức độ hợp nhất các auto-proxy creator và chính sách chọn JDK/CGLIB có thể phụ thuộc phiên bản Spring và cấu hình ứng dụng; không mặc định rằng mọi cơ chế proxy tùy biến đều tự động hợp nhất.

## 6. Kiểm chứng và chẩn đoán

Khi nghi có tăng cường trùng lặp, hãy kiểm tra **cùng một bean thực tế được lấy từ container** trước và sau điểm xử lý:

```java
Object service = applicationContext.getBean("reportService");

System.out.println("is proxy = " + AopUtils.isAopProxy(service));
System.out.println("target class = " + AopUtils.getTargetClass(service));

if (service instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
```

Đoạn trên là **fragment chẩn đoán** (giả định đã có `applicationContext`), không phải ví dụ chạy độc lập. Khi thấy nhiều advisor trong kết quả, chưa thể kết luận có nhiều lớp proxy: nhiều advisor là trường hợp bình thường trên **một** proxy. Ngược lại, `isAopProxy(bean) == true` cũng không cho biết có bao nhiêu lớp bao ngoài.

Các phép thử có ý nghĩa hơn:

1. **Kiểm tra tính idempotent:** đưa bean thường qua bước tăng cường hai lần; nếu hàm có hợp đồng “proxy một lần”, cả hai kết quả phải là cùng instance (`first == second`).
2. **Kiểm tra số lần advice chạy:** gọi một phương thức public được pointcut chọn và đếm invocation của advice; nếu mong một lần thì phải đúng một lần. Đừng suy đoán chỉ từ số lượng advisor.
3. **Kiểm tra container:** lấy bean theo interface, kiểm tra loại proxy và danh sách advisor. Với JDK proxy, ép trực tiếp sang class triển khai có thể gây `ClassCastException`; hãy sử dụng interface đã được proxy công bố.
4. **Kiểm tra tính năng có sẵn:** sau khi sửa cách tạo proxy, vẫn phải xác nhận transaction, authorization, logging hoặc aspect đang dùng còn hoạt động đúng. “Không có proxy thứ hai” không có nghĩa “không có rủi ro mất advice”.

Không nên chữa lỗi bằng cách tự gọi `((Advised) proxy).getTargetSource().getTarget()` rồi bọc lại “target gốc”. `TargetSource` có thể động, có quy tắc mượn/trả target và vòng đời riêng; việc tháo proxy tùy tiện còn có thể bỏ qua advice quan trọng trên proxy cũ. Tương tự, gọi nội bộ qua `this.method()` vẫn không đi qua proxy Spring AOP; tạo thêm lớp proxy không giải quyết bản chất của self-invocation.

## 7. Checklist khi triển khai

- Xác định nơi **duy nhất** tạo/đăng ký advice cho bean: `@Aspect`/`Advisor` trong container, `ProxyFactoryBean`, `ProxyFactory` hoặc post-processor tùy biến.
- Trước khi gọi `getProxy()` cho một **đối tượng đã nhận từ Spring**, kiểm tra `AopUtils.isAopProxy(bean)`.
- Xác định rõ yêu cầu là “chỉ cần proxy” hay “phải có advisor X”; đừng dùng kiểm tra thứ nhất để thay thế kiểm tra thứ hai.
- Với proxy `Advised`, cân nhắc danh sách advisor và `isFrozen()`; sử dụng advisor ổn định để phát hiện việc đăng ký lặp.
- Kiểm thử một lời gọi qua bean lấy từ container: identity, số lần advice chạy, thứ tự advice và các chức năng Spring đang phụ thuộc vào proxy.
- Ưu tiên để một cơ chế Spring AOP quản lý chuỗi advisor; chỉ dùng proxy lồng nhau khi đó là quyết định thiết kế có chủ đích và đã có kiểm thử.

**Tài liệu Spring chính thức:** [Creating AOP Proxies Programmatically with ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html), [Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html), [Using the auto-proxy facility](https://docs.spring.io/spring-framework/reference/core/aop-api/autoproxy.html), [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="programmatic-proxy-creation-010">Kiểm thử proxy tự tạo theo cùng hợp đồng với bean proxy tự động</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử proxy tự tạo theo cùng hợp đồng với bean proxy tự động trong Spring AOP

## 1. Mục tiêu: kiểm chứng hành vi AOP, không chỉ kiểm tra proxy có tồn tại

Spring Framework cho phép tạo AOP proxy theo hai hướng:

1. **Tự tạo theo chương trình:** dùng `ProxyFactory`, cung cấp target và một hoặc nhiều `Advisor`/`Advice`, sau đó gọi `getProxy()`.
2. **Spring tự tạo cho bean:** đăng ký service và một bean `@Aspect` trong `ApplicationContext`, bật `@EnableAspectJAutoProxy`; Spring xác định bean phù hợp và áp dụng advisor trên proxy.

Một lỗi kiểm thử hay gặp là chỉ kiểm tra `AopUtils.isAopProxy(service)` hoặc log một lần, rồi kết luận hai cơ chế tương đương. Thực tế, proxy có thể được tạo nhưng chọn sai method; một advice có thể chạy hai lần; self-invocation có thể bỏ qua advice; exception có thể bị nuốt; hoặc JDK proxy và class-based proxy có API phơi bày khác nhau.

Vì vậy mục tiêu của bài này là **định nghĩa một hợp đồng quan sát được (observable contract)** và chạy **cùng một bộ assertion** trên cả proxy tự tạo và bean proxy tự động, thay vì viết hai bài test không đồng nhất.

```text
                                 CÙNG HỢP ĐỒNG
               +-----------------------------------------+
               | Gọi pay qua proxy: trước -> target -> sau|
               | Lỗi: trước -> target lỗi -> ghi lỗi, ném |
               | status không có advice                    |
               | self-invocation không tự được intercept   |
               +-------------------+---------------------+
                                   |
                        cùng bộ contract test
                         /               \
          ProxyFactory + Advisor       @Aspect + auto-proxy
                         \               /
                         JDK hoặc class proxy
```

**Phạm vi:** Spring AOP dựa trên proxy. Bài không suy rộng sang khả năng weaving của AspectJ, không coi annotation `@Aspect` tự nó chặn được lời gọi nội bộ, constructor hay field access.

## 2. “Cùng hợp đồng” nghĩa là gì?

Một hợp đồng nên mô tả **những gì caller và hệ thống quan sát**, không mô tả cách Spring tổ chức lớp proxy bên trong.

Ví dụ, ứng dụng có giao diện `PaymentService` với method `pay(String,int)`, `status()` và `payFromInside(String,int)`:

| Tình huống | Hợp đồng chung cần đúng ở hai cơ chế |
| --- | --- |
| Gọi `pay("A", 5)` qua proxy | Advice ghi `before:pay`, target ghi `target:pay:A:5`, advice ghi `after:pay`; trả `receipt:A:5` |
| Gọi `status()` qua proxy | Không khớp pointcut `pay`; chỉ có `target:status` |
| Gọi `pay("B", 0)` | Target ném `IllegalArgumentException`; advice ghi `error:pay`, exception vẫn tới caller |
| Gọi `payFromInside("C", 7)` | Lần gọi `this.pay(...)` bên trong target không đi qua proxy; chỉ có sự kiện target |
| Dùng target thô `new DefaultPaymentService(...)` | Không có advice nếu caller gọi trực tiếp target |
| Dùng proxy JDK | Proxy triển khai `PaymentService`, không bị yêu cầu ép về implementation |
| Dùng class-based proxy | Proxy có thể được xem là `DefaultPaymentService` trong ví dụ, vẫn thỏa hành vi AOP |

Cùng hợp đồng **không có nghĩa** cùng tên class proxy, cùng danh sách advisor nội bộ, cùng cách Spring lifecycle quản lý object, hoặc cùng số lần framework gọi hàm kiểm tra pointcut.

Test tốt cần tách:

- **Hợp đồng chức năng:** return value, exception, số lần/thứ tự advice thực sự quan sát, method nào được chọn.
- **Hợp đồng kỹ thuật có chủ ý:** proxy JDK hay CGLIB, có thể cast sang kiểu nào, caller có dùng proxy hay không.
- **Chi tiết triển khai không nên khóa cứng:** tên generated class (`$Proxy...`, `$$SpringCGLIB$$...`), thứ tự quét bean không ảnh hưởng kết quả, hoặc số lần gọi matcher nội bộ.

## 3. Kiến trúc hai nhánh cần so sánh

### Nhánh A: tự tạo `ProxyFactory`

```text
new DefaultPaymentService(trace)
          |
new ProxyFactory(target)
          |
AspectJExpressionPointcut
          + TraceInterceptor
          |
DefaultPointcutAdvisor
          |
factory.getProxy()
          |
PaymentService (proxy)
```

Nhánh này **không cần `ApplicationContext`**. Điểm chặn phải được ghép với advice và gắn vào factory. Trong bài ta dùng `AspectJExpressionPointcut`, để cú pháp chọn method **giống hệt** biểu thức ở nhánh `@Aspect`.

### Nhánh B: bean proxy tự động

```text
ApplicationContext
   + @Bean PaymentService -> DefaultPaymentService
   + @Bean TraceAspect (có @Aspect, @Around)
   + @EnableAspectJAutoProxy
                |
     Spring auto-proxy creator
                |
      PaymentService (proxy)
```

Ở nhánh này Spring quản lý bean và tự chuyển `@Around` thành advice/advisor thích hợp. **`@Aspect` phải là bean của Spring**; chỉ tạo `new TraceAspect(...)` bên ngoài context không làm aspect chạy trên Spring beans.

### Điều kiện để so sánh công bằng

Hai nhánh phải có cùng:

1. Target business logic và kiểu method.
2. Biểu thức pointcut hoặc tập lời gọi khớp tương đương.
3. Advice behavior: ghi trước, thành công, thất bại; gọi `proceed()` đúng một lần.
4. Thứ tự advisor có thể so sánh được (ví dụ này chỉ có một tracing advisor).
5. Kiểu proxy được chọn rõ ràng khi muốn kiểm thử JDK so với class-based.
6. Recorder/log riêng cho từng scenario, tránh test nhiễm sự kiện nhau.

Nếu một nhánh có transaction/async/security advisor bổ sung, kết quả quan sát có thể khác do thứ tự hoặc ranh giới advice. Khi đó nên kiểm chứng **hợp đồng thực tế mới** thay vì buộc hai cơ chế có cùng mảng event một cách máy móc.

## 4. Cùng một biểu thức pointcut cho cả hai phía

Để giảm nguy cơ một nhánh chọn `pay` còn nhánh kia chọn mọi method bắt đầu bằng `pay`, ví dụ đặt expression vào một hằng Java:

```java
public static final String PAY_EXECUTION =
        "execution(* example.DefaultPaymentService.pay(..))";
```

- Với `ProxyFactory`: gắn expression vào `AspectJExpressionPointcut` rồi tạo `DefaultPointcutAdvisor`.
- Với `@Aspect`: dùng chính hằng compile-time đó trong `@Around(AopPolicy.PAY_EXECUTION)`.
- Với test: kiểm chứng **cả method được chọn và method không được chọn**, dù đã chia sẻ hằng; một expression chung vẫn có thể sai về ý nghĩa.

Spring sử dụng thư viện AspectJ để phân tích biểu thức `@AspectJ`; cả hai nhánh ở đây vẫn là **Spring AOP proxy**, không phải AspectJ weaving.

## 5. Ví dụ Java 17 hoàn chỉnh: bốn trường hợp kiểm thử

Bộ ví dụ chạy với **Java 17+, Spring Framework 6.2.8, AspectJ Weaver 1.9.22 và JUnit Jupiter 5.11.4**. Ta kiểm tra bốn mode:

| Case | Ai tạo proxy? | Loại proxy |
| --- | --- | --- |
| Manual JDK | `ProxyFactory` | JDK dynamic proxy |
| Manual class | `ProxyFactory` | Proxy dựa trên lớp (CGLIB) |
| Auto JDK | Spring `ApplicationContext`/`@Aspect` | JDK dynamic proxy |
| Auto class | Spring `ApplicationContext`/`@Aspect` | Proxy dựa trên lớp (CGLIB) |

Các mode sử dụng target và hợp đồng giống nhau, nhưng vòng đời object khác nhau. Mỗi lần tạo fixture dùng `Trace` riêng.

Cấu trúc dự án:

```text
proxy-contract-test/
├── pom.xml
└── src/
    ├── main/java/example/
    │   ├── AopPolicy.java
    │   ├── Trace.java
    │   ├── PaymentService.java
    │   ├── DefaultPaymentService.java
    │   ├── TraceInterceptor.java
    │   ├── TraceAspect.java
    │   ├── ProxyCases.java
    │   └── App.java
    └── test/java/example/ProxyContractTest.java
```

### 5.1. Maven `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>proxy-contract-test</artifactId>
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
        <version>3.5.0</version>
      </plugin>
    </plugins>
  </build>
</project>
```

`spring-context` cung cấp Java configuration và `ApplicationContext`; `spring-aop` cung cấp `ProxyFactory`/advisor; `aspectjweaver` phục vụ phân tích biểu thức pointcut. `JUnit Jupiter` và Surefire chạy contract test.

### 5.2. Expression duy nhất — `src/main/java/example/AopPolicy.java`

```java
package example;

public final class AopPolicy {
    private AopPolicy() {}

    public static final String PAY_EXECUTION =
            "execution(* example.DefaultPaymentService.pay(..))";
}
```

Annotation Java yêu cầu tham số kiểu String của `@Around` là **hằng tại compile time**; `public static final String` khởi tạo bằng một string literal thỏa điều kiện đó. Nếu hằng đổi, cả manual pointcut lẫn `@Around` được biên dịch từ cùng policy.

### 5.3. Hợp đồng service và recorder

`src/main/java/example/Trace.java`:

```java
package example;

import java.util.ArrayList;
import java.util.List;

public final class Trace {
    private final List<String> events = new ArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }
}
```

`src/main/java/example/PaymentService.java`:

```java
package example;

public interface PaymentService {
    String pay(String id, int amount);
    String status();
    String payFromInside(String id, int amount);
}
```

`src/main/java/example/DefaultPaymentService.java`:

```java
package example;

public class DefaultPaymentService implements PaymentService {
    private final Trace trace;

    public DefaultPaymentService(Trace trace) {
        this.trace = trace;
    }

    @Override
    public String pay(String id, int amount) {
        if (amount <= 0) {
            trace.add("target:reject:" + id);
            throw new IllegalArgumentException("amount must be positive");
        }

        trace.add("target:pay:" + id + ":" + amount);
        return "receipt:" + id + ":" + amount;
    }

    @Override
    public String status() {
        trace.add("target:status");
        return "READY";
    }

    @Override
    public String payFromInside(String id, int amount) {
        trace.add("target:internal");
        return this.pay(id, amount); // Self-invocation không qua Spring proxy.
    }
}
```

`payFromInside` tồn tại để chứng minh một **giới hạn chung** của hai nhánh Spring AOP, chứ không phải để đề xuất gọi method nội bộ nhằm tránh advice trong production. `Trace` dùng `ArrayList` vì bài kiểm thử chạy tuần tự; nếu nhiều thread dùng cùng recorder, cần thiết kế cấu trúc thread-safe.

### 5.4. Advice tự tạo — `TraceInterceptor.java`

`src/main/java/example/TraceInterceptor.java`:

```java
package example;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public final class TraceInterceptor implements MethodInterceptor {
    private final Trace trace;

    public TraceInterceptor(Trace trace) {
        this.trace = trace;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String method = invocation.getMethod().getName();
        trace.add("before:" + method);

        try {
            Object value = invocation.proceed();
            trace.add("after:" + method);
            return value;
        } catch (Throwable error) {
            trace.add("error:" + method);
            throw error;
        }
    }
}
```

`MethodInterceptor` chỉ ghi thành công sau khi `proceed()` trả về; khi lỗi được ném, ghi `error` và ném lại chính exception. Advice không nuốt lỗi, không gọi target hai lần và không đổi giá trị trả về.

### 5.5. Advice tự động — `TraceAspect.java`

`src/main/java/example/TraceAspect.java`:

```java
package example;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public final class TraceAspect {
    private final Trace trace;

    public TraceAspect(Trace trace) {
        this.trace = trace;
    }

    @Around(AopPolicy.PAY_EXECUTION)
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getName();
        trace.add("before:" + method);

        try {
            Object value = joinPoint.proceed();
            trace.add("after:" + method);
            return value;
        } catch (Throwable error) {
            trace.add("error:" + method);
            throw error;
        }
    }
}
```

Hành vi của `TraceAspect` khớp `TraceInterceptor` theo hợp đồng bên ngoài, nhưng **API callback khác**: `ProceedingJoinPoint` so với `MethodInvocation`. Bean `@Aspect` tự nó không chạy nếu không được Spring phát hiện; mục tiếp theo sẽ đăng ký nó và bật auto-proxy.

### 5.6. Tạo bốn mode trong `ProxyCases.java`

`src/main/java/example/ProxyCases.java`:

```java
package example;

import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public final class ProxyCases {
    private ProxyCases() {}

    public record Scenario(PaymentService service, Trace trace) {}

    public static Scenario manual(boolean classProxy) {
        Trace trace = new Trace();
        DefaultPaymentService target = new DefaultPaymentService(trace);

        AspectJExpressionPointcut pointcut =
                new AspectJExpressionPointcut();
        pointcut.setExpression(AopPolicy.PAY_EXECUTION);

        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(PaymentService.class);
        factory.setProxyTargetClass(classProxy);

        factory.addAdvisor(new DefaultPointcutAdvisor(
                pointcut, new TraceInterceptor(trace)));

        return new Scenario((PaymentService) factory.getProxy(), trace);
    }

    @Configuration(proxyBeanMethods = false)
    public static class Beans {
        @Bean
        Trace trace() {
            return new Trace();
        }

        @Bean
        PaymentService paymentService(Trace trace) {
            return new DefaultPaymentService(trace);
        }

        @Bean
        TraceAspect traceAspect(Trace trace) {
            return new TraceAspect(trace);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    @Import(Beans.class)
    public static class AutoJdkConfig {}

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(Beans.class)
    public static class AutoClassConfig {}
}
```

Tại đây:

- `manual(false)` chọn **JDK proxy**, `manual(true)` chọn **class-based proxy**. Hai mode dùng cùng pointcut và cùng interceptor, nhưng target/recorder được tạo riêng.
- `Beans` khai báo target service và aspect như Spring beans. Hai cấu hình `AutoJdkConfig` và `AutoClassConfig` import cùng bean definitions, bật `@EnableAspectJAutoProxy` với lựa chọn proxy tương ứng.
- **Mỗi auto mode phải tạo một `AnnotationConfigApplicationContext` độc lập**, để chế độ class proxy của cấu hình này không ảnh hưởng cấu hình kia qua một auto-proxy creator dùng chung trong cùng context.
- Spring dùng target thực tế `DefaultPaymentService` để khớp pointcut, dù bean method được khai báo kiểu trả về `PaymentService`.
- Cùng một `AopPolicy.PAY_EXECUTION` hạn chế sự sai lệch giữa hai nhánh. Nếu method của service thay tên/chữ ký, contract test phải được cập nhật dựa trên thay đổi yêu cầu thật sự.

### 5.7. Chương trình chạy hai mode JDK, `App.java`

`src/main/java/example/App.java`:

```java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {
    private static void show(String label, ProxyCases.Scenario caseData) {
        PaymentService service = caseData.service();

        System.out.println(label + ".proxy=" + AopUtils.isAopProxy(service));
        System.out.println(label + ".jdk=" + AopUtils.isJdkDynamicProxy(service));
        System.out.println(label + ".pay=" + service.pay("A", 5));
        System.out.println(label + ".status=" + service.status());

        try {
            service.pay("B", 0);
        } catch (IllegalArgumentException error) {
            System.out.println(label + ".error=" + error.getMessage());
        }

        System.out.println(label + ".events=" + caseData.trace().snapshot());
    }

    public static void main(String[] args) {
        show("manual", ProxyCases.manual(false));

        try (var context = new AnnotationConfigApplicationContext(
                ProxyCases.AutoJdkConfig.class)) {
            show("auto", new ProxyCases.Scenario(
                    context.getBean(PaymentService.class),
                    context.getBean(Trace.class)));
        }
    }
}
```

Chạy từ thư mục `proxy-contract-test`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.App
```

Stdout của logic ví dụ (bỏ qua thông báo Maven nếu có):

```text
manual.proxy=true
manual.jdk=true
manual.pay=receipt:A:5
manual.status=READY
manual.error=amount must be positive
manual.events=[before:pay, target:pay:A:5, after:pay, target:status, before:pay, target:reject:B, error:pay]
auto.proxy=true
auto.jdk=true
auto.pay=receipt:A:5
auto.status=READY
auto.error=amount must be positive
auto.events=[before:pay, target:pay:A:5, after:pay, target:status, before:pay, target:reject:B, error:pay]
```

Ý nghĩa: hai con đường cùng tạo Spring proxy JDK, cùng chọn đúng `pay`, bảo toàn giá trị trả về và exception. Đường tạo bean/aspect khác nhau nhưng caller nhìn thấy một hợp đồng giống nhau. Đây là **output kỳ vọng theo thiết kế mã ví dụ**; khi chạy tại môi trường thật, xác nhận bằng test và không coi tên generated proxy class là yếu tố quyết định.

## 6. Một bộ contract test chạy trên cả bốn chế độ

Chìa khóa của bài là một phương thức assertion duy nhất `assertCommonContract`; từng test chỉ tạo fixture theo con đường khác nhau.

`src/test/java/example/ProxyContractTest.java`:

```java
package example;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyContractTest {

    private static void assertCommonContract(
            PaymentService service, Trace trace) {

        assertTrue(AopUtils.isAopProxy(service));
        assertEquals("receipt:A:5", service.pay("A", 5));
        assertEquals("READY", service.status());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.pay("B", 0));
        assertEquals("amount must be positive", error.getMessage());

        assertEquals(List.of(
                "before:pay",
                "target:pay:A:5",
                "after:pay",
                "target:status",
                "before:pay",
                "target:reject:B",
                "error:pay"
        ), trace.snapshot());
    }

    @Test
    void manualJdkProxySatisfiesContract() {
        var fixture = ProxyCases.manual(false);

        assertTrue(AopUtils.isJdkDynamicProxy(fixture.service()));
        assertFalse(fixture.service() instanceof DefaultPaymentService);
        assertCommonContract(fixture.service(), fixture.trace());
    }

    @Test
    void manualClassProxySatisfiesContract() {
        var fixture = ProxyCases.manual(true);

        assertTrue(AopUtils.isCglibProxy(fixture.service()));
        assertTrue(fixture.service() instanceof DefaultPaymentService);
        assertCommonContract(fixture.service(), fixture.trace());
    }

    @Test
    void automaticallyProxiedJdkBeanSatisfiesContract() {
        try (var context = new AnnotationConfigApplicationContext(
                ProxyCases.AutoJdkConfig.class)) {

            PaymentService service = context.getBean(PaymentService.class);
            Trace trace = context.getBean(Trace.class);

            assertTrue(AopUtils.isJdkDynamicProxy(service));
            assertFalse(service instanceof DefaultPaymentService);
            assertCommonContract(service, trace);
        }
    }

    @Test
    void automaticallyProxiedClassBeanSatisfiesContract() {
        try (var context = new AnnotationConfigApplicationContext(
                ProxyCases.AutoClassConfig.class)) {

            PaymentService service = context.getBean(PaymentService.class);
            Trace trace = context.getBean(Trace.class);

            assertTrue(AopUtils.isCglibProxy(service));
            assertTrue(service instanceof DefaultPaymentService);
            assertCommonContract(service, trace);
        }
    }

    @Test
    void internalCallBypassesAdviceInManualAndAutomaticProxy() {
        var manual = ProxyCases.manual(false);

        assertEquals("receipt:X:7",
                manual.service().payFromInside("X", 7));
        assertEquals(List.of(
                "target:internal",
                "target:pay:X:7"), manual.trace().snapshot());

        try (var context = new AnnotationConfigApplicationContext(
                ProxyCases.AutoJdkConfig.class)) {

            PaymentService service = context.getBean(PaymentService.class);
            Trace trace = context.getBean(Trace.class);

            assertEquals("receipt:X:7", service.payFromInside("X", 7));
            assertEquals(List.of(
                "target:internal",
                "target:pay:X:7"), trace.snapshot());
        }
    }

    @Test
    void rawTargetHasNoAdvice() {
        Trace trace = new Trace();
        PaymentService target = new DefaultPaymentService(trace);

        assertFalse(AopUtils.isAopProxy(target));
        assertEquals("receipt:Z:3", target.pay("Z", 3));
        assertEquals(List.of("target:pay:Z:3"), trace.snapshot());
    }
}
```

Chạy kiểm thử:

```bash
mvn -q test
```

**Các assertion mới là hợp đồng:** không phụ thuộc log stdout của Maven, không chụp snapshot tên lớp proxy. Nếu hai mode trả cùng dữ liệu nhưng một mode có lời gọi advice trùng, danh sách event sẽ phát hiện. Nếu một mode quên `proceed()`, kết quả hoặc sự kiện target không khớp.

### Vì sao test nên dùng context riêng?

Một auto-proxy creator có thể chịu ảnh hưởng bởi nhiều `@EnableAspectJAutoProxy`/`<aop:config/>` trong cùng ApplicationContext. Đặc biệt, tùy chọn ép class-based proxy có thể tác động tới **toàn context**, không chỉ một bean. Dùng context độc lập cho case JDK và class là cách làm thí nghiệm rõ ràng. Spring Boot cũng có thể cấu hình mặc định loại proxy khác Spring Framework thuần, nên test phải kiểm chứng hoặc kiểm soát lựa chọn này thay vì mặc định đoán.

### Vì sao một hợp đồng nhưng có bốn test?

- Contract về kết quả, exception, điểm chặn là phần **tái sử dụng**.
- Contract về **proxy type** được xác nhận bằng assert riêng vì JDK và class-based không cùng kiểu Java runtime.
- Hai mode manual/auto khác cơ chế đăng ký advisor, nên cần chạy assertion trên cả hai.
- Test self-invocation và raw target là **negative tests**, giúp ngăn kết luận sai rằng mọi lời gọi cùng tên đều được advice.

## 7. Cách kiểm tra tương đương mà không kết luận quá mức

Một proxy tự tạo và một bean auto-proxy có thể cho **cùng output** trong bài nhưng vẫn khác:

| Vấn đề | ProxyFactory thủ công | Auto-proxy trong Spring context |
| --- | --- | --- |
| Nơi cấu hình advice | `addAdvisor(...)`/`addAdvice(...)` trong Java | Bean `@Aspect`, annotation, auto-proxy creator |
| Ai giữ target/lifecycle | Code gọi factory quản lý | Spring bean factory/context quản lý |
| Những bean được chọn | Proxy được tạo cho target chỉ định | Spring xem xét các bean đủ điều kiện |
| Pointcut | Cung cấp qua `Pointcut`/`Advisor` | Spring chuyển biểu thức `@AspectJ` thành advisor |
| Khả năng chạy trùng | Nếu thêm advisor hai lần | Nếu có nhiều advisor/aspect cùng khớp hoặc chồng proxy |
| Kiểu proxy | `setInterfaces(...)`, `setProxyTargetClass(...)` | `@EnableAspectJAutoProxy(proxyTargetClass=...)` và các config khác |
| Lời gọi bypass | Target thô, self-invocation | Tương tự, vì cũng dựa trên proxy |
| Test vòng đời bean | Không có IoC nếu chỉ dùng factory | Context close, dependency injection, bean lifecycle |
| Phạm vi tương thích | Không tự nhận các advisor tự động của container | Có thể cùng tồn tại transaction/security/async advisor |

**Kết luận chính xác:** “Hai chế độ thỏa cùng hợp đồng cho những lời gọi đã kiểm thử”, không phải “mọi chi tiết Spring AOP hoàn toàn giống nhau”. Khi production có thêm `@Transactional`, `@Async` hay nhiều advisor, cần thêm test để xác định thứ tự, exception và thread boundary.

## 8. Kiểm thử những điểm khác biệt quan trọng

### 8.1. Tính đúng đắn của pointcut

Nếu manual dùng `NameMatchMethodPointcut` với `"pay*"` còn auto dùng `execution(* ...pay(..))`, một phương thức `payLater` có thể bị advice ở một phía nhưng không ở phía kia. Chia sẻ `AopPolicy.PAY_EXECUTION` như bài này giảm sai lệch, nhưng **test method được chọn và method bị loại** vẫn cần thiết.

Tập test tối thiểu cho mỗi pointcut nên gồm một method phải khớp, một method không khớp, và overload/subclass/interface nếu API có những trường hợp đó. Dù cùng expression, cấu hình advisor và proxy type có thể quyết định method thực tế Spring nhìn thấy.

### 8.2. JDK proxy khác class-based proxy ở API Java

JDK proxy triển khai giao diện và không phải subtype của implementation. Không được viết test `assertTrue(service instanceof DefaultPaymentService)` cho một JDK proxy. Với CGLIB class-based proxy của đối tượng không final, assert này có thể đúng. Hợp đồng giao diện `PaymentService` phải hoạt động trên cả hai.

Nếu pointcut tham chiếu method chỉ có ở implementation mà không có trên interface, JDK proxy không thể cho caller gọi method đó qua interface. Khi đổi JDK↔class proxy, cần xác định **API public nào caller thực sự sử dụng**.

### 8.3. Exception và lời gọi advice

Khi target ném lỗi, around interceptor trong ví dụ ghi `error:pay` và ném tiếp cùng exception. Nếu một aspect khác bao ngoài bắt rồi thay exception, hành vi caller có thể khác; test cần tạo bối cảnh đúng thứ tự advisor khi có nhiều aspect.

Nếu muốn “after” chạy cả thành công lẫn thất bại, phải dùng `finally` (hoặc advice `@After` thích hợp), không dùng riêng hành vi after-returning vốn chỉ chạy khi hoàn thành bình thường. Trong bài này `after:pay` được định nghĩa là **sau thành công**, nên không xuất hiện ở lời gọi thất bại.

### 8.4. Self-invocation có thể đánh lừa test

`payFromInside` gọi `this.pay(...)` trên target. Method ngoài được gọi qua proxy, nhưng lời gọi bên trong dùng `this`, do đó không qua interceptor chain của proxy. Vì vậy cả hai nhánh chỉ ghi:

```text
[target:internal, target:pay:X:7]
```

Đừng viết test chỉ gọi `payFromInside` rồi đòi thấy `before:pay`; đó không phải hợp đồng mặc định của Spring AOP proxy. Nếu nghiệp vụ bắt buộc advice trên lời gọi ấy, thiết kế lại ranh giới giữa các Spring beans hoặc cân nhắc cơ chế phù hợp, thay vì giả định proxy tự khắc phục.

### 8.5. Số lần thực thi và tính độc lập giữa các test

- Đảm bảo mỗi scenario có `Trace` riêng; không dùng danh sách `static` toàn cục chung cho test.
- Mỗi test có target độc lập, không dựa vào thứ tự chạy JUnit.
- Nếu advice có field mutable ghi dữ liệu theo invocation, tránh race condition. Dùng biến cục bộ trong `invoke` hoặc `around`; recorder production cần thread safety.
- Nếu một lời gọi đáng lẽ chỉ có một cặp before/after, assert cả danh sách sẽ bắt hiện tượng **duplicate proxy/duplicate advisor**.
- Không dùng `verify` số lần matcher nội bộ chạy như hợp đồng: Spring có thể cache thông tin match theo target class/method.

## 9. Phép thử âm tính (negative test) là bắt buộc

Một bài test “pay trả đúng receipt” có thể pass dù AOP **chưa chạy**. Bài trên tránh false positive bằng cách kiểm tra event `before`/`after` bên cạnh kết quả. Nhưng vẫn cần các test âm tính:

| Negative test | Lỗi sẽ lộ ra |
| --- | --- |
| Gọi `status` ngoài pointcut | Pointcut quá rộng, advisor gắn sai |
| Gọi target thô | Test nhầm raw target với proxy |
| Gọi self-invocation | Giả định không đúng về Spring proxy |
| Gọi `pay` lỗi | Advice nuốt exception hoặc bỏ qua error path |
| So sánh JDK proxy với implementation type | Test phụ thuộc class thay vì interface |
| Tạo auto context thiếu `@EnableAspectJAutoProxy` | Bean `@Aspect` không được kích hoạt |
| Tạo auto context thiếu bean `TraceAspect` | Aspect tồn tại trong source nhưng không được Spring đăng ký |
| Gắn thêm cùng advisor hai lần | Event before/after bị nhân đôi |
| Chọn method qua pointcut khác nhau | Hai nhánh không còn thật sự cùng phạm vi chặn |

Để xác minh test phát hiện lỗi, có thể cố tình làm một thay đổi nhỏ rồi chạy test: bỏ `factory.addAdvisor` ở manual hoặc bỏ `@EnableAspectJAutoProxy` ở auto. Contract test sẽ phát hiện thiếu before/after, dù method nghiệp vụ vẫn trả receipt.

**Không được dùng một “kết quả đúng” từ target để tuyên bố advice đã hoạt động**. Luôn có một dấu hiệu đo được riêng của advice và kiểm chứng method không khớp không sinh dấu hiệu ấy.

## 10. Gỡ lỗi khi hai nhánh không khớp

### Bước 1: xác nhận caller đang dùng proxy

```java
AopUtils.isAopProxy(service);
AopUtils.isJdkDynamicProxy(service);
AopUtils.isCglibProxy(service);
```

Khi manual tạo proxy bằng `getProxy()` nhưng đưa target gốc cho caller, `isAopProxy` của target sẽ false. Khi auto, lấy service từ `context.getBean(PaymentService.class)` chứ không tạo `new DefaultPaymentService(...)`.

### Bước 2: xác nhận pointcut chọn cùng method

Phía manual, kiểm tra advisor đã được `addAdvisor` và expression đã đặt đúng. Phía auto, xác nhận `TraceAspect` nằm trong context, `@EnableAspectJAutoProxy` có hiệu lực, và annotation `@Around` có expression đúng.

Có thể quan sát advisors khi proxy triển khai `Advised`:

```java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;

if (service instanceof Advised advised) {
    for (Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getSimpleName());
    }
}
```

Đoạn trên để debug; **đừng assert chính xác tên class internal**. Manual advisor và advisor được Spring sinh từ `@Aspect` có thể khác loại dù hành vi bên ngoài tương đương.

### Bước 3: kiểm tra thứ tự và exception

Nếu nhiều advisor cùng áp dụng, thứ tự bao quanh có thể dẫn đến output khác nhau. Với around advice:

```text
Outer.before
Inner.before
Target
Inner.after
Outer.after
```

Thứ tự trên giả định cả hai interceptor đều gọi `proceed()` và không có exception. Khi có lỗi, đường đi thành công không chạy; logic `catch`/`finally` quyết định sự kiện nào xuất hiện. Trong production, transaction, security và async có thể tạo thêm advisor và ranh giới thực thi nên cần test phản ánh thứ tự mong muốn.

### Bước 4: phân biệt lỗi AOP với lỗi business method

Nếu cả hai nhánh đều ghi `target:reject:B` và ném cùng `IllegalArgumentException`, service đang giữ hợp đồng. Nếu thiếu `before:pay`, vấn đề thường nằm ở proxy/pointcut/advisor. Nếu có `before:pay` nhưng không có `target:reject:B`, kiểm tra interceptor có gọi `proceed()` hay không.

### Bước 5: xác định Spring context và test isolation

Để so sánh auto-JDK/auto-class, tránh khai báo cả hai `@EnableAspectJAutoProxy` với thiết lập trái nhau trong **cùng context**. Spring có thể hợp nhất cấu hình auto-proxy creator, lựa chọn class-based proxy áp dụng rộng hơn dự kiến. Hai context độc lập giúp test nói đúng điều kiện mà nó thiết lập.

## 11. Khi nào một phép kiểm thử “cùng hợp đồng” chưa đủ?

Contract test là tầng nền, không thay thế những kiểm thử riêng theo đặc thù từng cách:

- **Manual `ProxyFactory`:** kiểm thử việc caller thật sự nhận proxy, cấu hình `TargetSource` nếu dùng, các advisor được thêm đúng một lần và vòng đời target nếu tạo ngoài IoC.
- **Auto `@Aspect`:** kiểm thử bean discovery, điều kiện auto-configuration, `@Bean`/`@Component`/`@Import`, proxy type thực tế và tác động từ những advisor framework khác.
- **Spring Boot:** kiểm tra cấu hình proxy default từ môi trường thay vì giả định giống Spring Framework thuần.
- **Giao dịch và bảo mật:** một hợp đồng AOP đơn giản về trace không đủ kết luận phương thức đã được kiểm tra quyền hay transaction rollback chính xác.
- **Bất đồng bộ/reactive:** `proceed()` có thể trả `CompletableFuture` hoặc publisher trước khi công việc thực tế hoàn tất; định nghĩa lại “after success” nếu yêu cầu quan sát lúc task hoàn tất.
- **Performance:** proxy JDK và class có overhead khác nhau trong từng điều kiện; contract test về đúng/sai không phải benchmark. Đo riêng ở workload đại diện nếu hiệu năng quan trọng.

**Quan trọng:** “Cùng hợp đồng” phải được hiểu trong **phạm vi nghiệp vụ được mô tả và kiểm thử**. Việc test pass không chứng minh hai cơ chế Spring hoàn toàn giống nhau ở mọi lời gọi hoặc ngữ cảnh runtime.

## 12. Bài tập mở rộng

1. Đổi `AopPolicy.PAY_EXECUTION` thành expression chỉ khớp `status`. Chạy `mvn -q test` và xác định assertion nào bắt lỗi trong cả manual/auto.
2. Thêm overload `pay(String)`. Xác định expression hiện tại khớp những overload nào và thêm test để khóa phạm vi mong muốn; nếu chỉ cần `pay(String,int)` thì viết pointcut rõ chữ ký.
3. Bỏ `factory.addAdvisor(...)` ở manual hoặc bỏ bean `TraceAspect` ở auto. Chứng minh business method vẫn có thể chạy nhưng contract test AOP thất bại.
4. Thêm interceptor thứ hai cho manual và aspect thứ hai cho auto. Thiết kế precedence rõ ràng và kiểm tra event ở success/error path, tránh dựa vào tên phương thức hay thứ tự source code.
5. Đổi `payFromInside` sang gọi qua một service bean khác do Spring quản lý, rồi kiểm chứng advice xuất hiện khi lời gọi thực sự qua proxy.
6. Thêm case test cho class proxy khi method `final` hoặc target class `final`; phân biệt không thể tạo proxy class với không thể advise một method cụ thể.
7. Thay `Trace` bằng recorder thread-safe và gọi đồng thời; tránh khẳng định event toàn cục giữ thứ tự giữa các thread, nhưng đảm bảo mỗi invocation có sự kiện tương ứng.

### Checklist kết thúc

- [ ] Hai nhánh dùng cùng business contract và policy pointcut có chủ đích.
- [ ] Cả hai đều cung cấp **proxy** cho caller; proxy type được kiểm tra nếu là yêu cầu.
- [ ] Cùng bộ assertion chạy trên manual JDK/class và auto JDK/class.
- [ ] Chứng minh advice trước/sau thành công, lỗi ném tiếp, result giữ nguyên.
- [ ] Có test method không match, raw target và self-invocation.
- [ ] Không dựa vào tên generated proxy hoặc số lần framework gọi matcher.
- [ ] Context/test data độc lập; không nhiễm advisor hoặc event từ case khác.
- [ ] Nếu có transaction/security/async, kiểm thử riêng các ranh giới ấy.

### Tài liệu chính thức của Spring

- Tạo proxy theo chương trình bằng `ProxyFactory`: https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html
- Spring `@AspectJ` support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Pointcut expression trong Spring AOP: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Cơ chế proxy JDK/CGLIB và self-invocation: https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- Advisor API: https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html
- Pointcut API: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html


</details>

- [Quay lại đầu trang](#back-to-top)
