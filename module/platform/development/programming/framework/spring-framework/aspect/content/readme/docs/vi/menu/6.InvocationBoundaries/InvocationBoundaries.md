<a id="back-to-top"></a>

# Ranh giới lời gọi

## Menu
- [Lời gọi từ bean khác đi qua proxy và lời gọi trực tiếp vào đối tượng đích](#invocation-boundaries-001)
- [Tự gọi nội bộ qua this và nguyên nhân bỏ qua advice](#invocation-boundaries-002)
- [Tái cấu trúc để chuyển lời gọi sang một bean cộng tác](#invocation-boundaries-003)
- [Tự tiêm tham chiếu proxy và các phụ thuộc phát sinh](#invocation-boundaries-004)
- [AopContext.currentProxy(), exposeProxy và lý do hạn chế sử dụng](#invocation-boundaries-005)
- [Lời gọi từ constructor hoặc callback khởi tạo trước khi proxy sẵn sàng](#invocation-boundaries-006)
- [Đối tượng không được Spring quản lý và giới hạn của proxy tự động](#invocation-boundaries-007)
- [Phương thức kế thừa, interface mặc định và vị trí đặt annotation](#invocation-boundaries-008)
- [Proxy lồng nhau và việc lần theo chuỗi đối tượng](#invocation-boundaries-009)
- [Phân biệt cùng phương thức đích nhưng khác đường đi của lời gọi](#invocation-boundaries-010)

## <a id="invocation-boundaries-001">Lời gọi từ bean khác đi qua proxy và lời gọi trực tiếp vào đối tượng đích</a>

<details>
<summary>Xem chi tiết</summary>

# Lời gọi từ bean khác đi qua proxy và lời gọi trực tiếp vào đối tượng đích

## 1. Vấn đề thực tế: cùng một method nhưng advice có lúc chạy, có lúc không

Trong Spring AOP, việc một method được viết bên trong **Spring bean** hoặc khớp biểu thức **pointcut** chưa đủ để bảo đảm advice sẽ chạy. Điều quyết định còn là **tham chiếu được dùng để thực hiện lời gọi**:

- Caller đang gọi một **Spring AOP proxy** do container cung cấp hay đang gọi **đối tượng target gốc**?
- Nếu caller là **một Spring bean khác**, dependency nó nhận được có phải proxy hay không?
- Nếu target gọi method của chính mình qua `this`, lời gọi có đi ngược ra proxy hay không?
- Nếu code tự tạo object bằng `new`, object đó có từng được Spring AOP auto-proxy creator xử lý hay không?

Một ví dụ điển hình:

```text
ClientBean  --------> OrderService proxy  --------> OrderService target
                       |                              |
                       +-- @Before/@Around advice     +-- business code

ClientBean  --------> raw OrderService target
                       (đi trực tiếp, không qua proxy)
```

Hai lời gọi có thể chạy **cùng code nghiệp vụ trên cùng target instance**, nhưng chỉ đường đi qua proxy mới cho Spring AOP cơ hội thực hiện advice.

**Mấu chốt:** *Spring AOP là proxy-based AOP*. Lời gọi không đi qua proxy thường không kích hoạt advice dù method được chọn bởi pointcut. Bài này tập trung vào **Spring Aspect và Spring-managed bean**, không mở rộng sang lý thuyết AOP chung hoặc AspectJ weaving độc lập.

## 2. Phân biệt caller, proxy và raw target

| Thành phần | Ý nghĩa trong Spring AOP | Điều cần kiểm chứng |
| --- | --- | --- |
| **Calling bean** | Một Spring bean gọi method trên dependency đã inject. | Dependency có thực sự là proxy Spring AOP không? |
| **Spring AOP proxy** | Object trung gian Spring trao cho caller khi bean cần advisor. | `AopUtils.isAopProxy(bean)` có `true` không? |
| **Target (raw object)** | Object chứa method nghiệp vụ thực tế. | Có bị gọi trực tiếp, bỏ qua proxy không? |
| **Aspect bean** | Spring bean khai báo `@Aspect`, có advice/pointcut. | Có đăng ký và được auto-proxy infrastructure nhận diện không? |
| **Advisor/interceptor chain** | Logic mà proxy áp dụng cho method khớp. | Có advice thực thi đúng số lần và thứ tự không? |
| **`this` trong target** | Reference bên trong chính target instance. | Có phải self-invocation bypass proxy không? |

Nếu gọi `clientBean.send()`, method `send()` của client **không cần tự được advice**. Nó có thể gọi dependency `orderService.submit()`, và chính lời gọi thứ hai mới đi qua Spring proxy.

Ngược lại, việc một class được đánh dấu `@Service` **không biến mọi tham chiếu đến instance của class đó thành proxy**. Spring chỉ có thể kiểm soát các instance/reference mà container đã tạo hoặc trả về theo cơ chế của nó.

### 2.1. Ba luồng gọi cơ bản

**Luồng A — từ bean khác qua dependency Spring đã inject:**

```text
CallerBean
   |
   | this.orderService.submit("A")  // field chứa Spring AOP proxy
   v
OrderService proxy
   |
   +-- Advice @Before / @Around nếu pointcut khớp
   |
   v
Raw target.submit("A")
```

**Luồng B — caller có sẵn tham chiếu raw target:**

```text
Caller / test / code giữ raw object
   |
   | rawTarget.submit("B")
   v
Raw target.submit("B")
   |
   +-- Không có Spring AOP proxy trên đường gọi
```

**Luồng C — self-invocation trong target:**

```text
Caller -> proxy -> rawTarget.outer()
                         |
                         | this.submit("C")
                         v
                    rawTarget.submit("C")
                    (lời gọi nội bộ, bypass proxy)
```

Nếu pointcut chỉ chọn `submit()`, advice chạy với luồng A; không chạy ở B; và **không chạy cho lời gọi `submit()` nội bộ** trong C. Nếu pointcut còn chọn `outer()`, lời gọi vào `outer()` có thể được advice, nhưng điều đó **không có nghĩa** lời gọi `submit()` qua `this` được advice lần thứ hai.

## 3. Vì sao bean khác thường nhận được proxy?

Khi dùng cấu hình Spring AOP chuẩn, chẳng hạn `@EnableAspectJAutoProxy` hoặc Spring Boot AOP auto-configuration phù hợp:

1. `ApplicationContext` đăng ký auto-proxy infrastructure, trong đó có `BeanPostProcessor` chuyên phát hiện advisor/aspect.
2. Spring khởi tạo target bean và xử lý vòng đời.
3. Nếu bean đủ điều kiện áp dụng advisor, Spring có thể tạo **JDK dynamic proxy** hoặc **class-based proxy (CGLIB)**.
4. Khi một bean khác inject dependency ấy bằng kiểu phù hợp, container giải quyết bean reference qua cơ chế quản lý của nó. Caller thường nhận **proxy đã được tạo**, không phải raw target mà proxy bao.
5. Lời gọi method từ caller đi vào proxy; advisor phù hợp được thực hiện trước/sau/quanh method target.

Điều này áp dụng cả khi service được khai báo bằng `@Component`/`@Service` và khi service đến từ `@Bean`, nếu instance được Spring quản lý và đủ điều kiện post-processing.

Ví dụ:

```java
@Service
public class CheckoutService {
    private final OrderOperations orders;

    public CheckoutService(OrderOperations orders) {
        this.orders = orders; // Spring inject bean reference (có thể là AOP proxy).
    }

    public void checkout(String id) {
        orders.submit(id); // Qua proxy nếu dependency là Spring AOP proxy.
    }
}
```

Đây là **đoạn code minh họa**, giả định đã khai báo interface `OrderOperations` và một bean implement nó. Kết luận “có advice” còn phụ thuộc aspect được đăng ký, pointcut khớp `submit()`, loại proxy, scope, và dependency thực sự trỏ đến proxy.

**Không phải cứ gọi từ một Spring bean khác là mặc nhiên chạy advice.** Nếu code trong bean khác tự `new OrderService()`, cố tình lấy raw target, hoặc inject bean thuộc context không có auto-proxy tương ứng, lời gọi đó vẫn có thể bypass AOP.

## 4. Thế nào là “gọi trực tiếp vào đối tượng đích”?

Một lời gọi trực tiếp là lời gọi trên **reference đến chính target**, không phải reference đến Spring AOP proxy. Ví dụ:

```java
OrderService raw = new OrderService();
raw.submit("X");
```

Đây là fragment, không phải project độc lập. `raw` ở ví dụ được tạo bằng `new` nên Spring container chưa quản lý và không thể tự áp dụng các advisor Spring AOP lên nó.

Một biến thể quan trọng hơn để debug là **cùng một raw target mà Spring đã proxy**, nhưng code vẫn giữ một reference trực tiếp đến raw instance. Khi đó:

```text
proxy.submit("X")  -> advice -> target.submit("X")
raw.submit("Y")    ------------> target.submit("Y")
```

Cùng target, cùng method, khác đường đi. Vì thế, nếu thấy nghiệp vụ vẫn chạy nhưng audit/logging/transaction advice không xuất hiện, cần kiểm tra **reference của caller** trước khi nghi ngờ pointcut.

Trong production, **không nên cố tình để lộ raw target của singleton bean** nhằm tránh AOP: điều đó phá vỡ khả năng áp dụng chính sách chung nhất quán. Ví dụ chạy ở phần sau cố ý giữ reference raw chỉ để chứng minh cơ chế Spring AOP.

## 5. Ví dụ runnable Java 17+: cùng một target, năm đường gọi

Ví dụ được thiết kế để phân biệt chính xác:

1. Bean `CheckoutFacade` gọi `OrderService` **thông qua proxy được Spring inject**.
2. Code gọi trực tiếp `proxy.submit()`.
3. Code gọi `proxy.submitViaThis()` rồi method target tự gọi `this.submit()`.
4. Code gọi `rawTarget.submit()` trên **cùng target instance đang được proxy**.
5. Code gọi method trên một instance **ngoài Spring container** bằng `new`.

Aspect chỉ advice method `OrderService.submit(..)`; chỉ hai đường đầu chạy advice.

### 5.1. Cấu trúc thư mục

```text
spring-aop-call-boundary/
├── pom.xml
└── src/main/java/com/example/boundary/
    ├── AppConfig.java
    ├── OrderService.java
    ├── CheckoutFacade.java
    ├── RawTargetHolder.java
    ├── AuditAspect.java
    └── DemoApp.java
```

### 5.2. `pom.xml`

Ví dụ dùng **Spring Framework 6.1.14**, `aspectjweaver 1.9.22.1`, JDK 17+ và Maven. Đây là bộ phiên bản **cố định để học**, không phải khẳng định các phiên bản mới nhất.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-aop-call-boundary</artifactId>
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

`spring-context` cung cấp container và kéo Spring AOP trong dependency graph của bộ bản mẫu; `aspectjweaver` cần cho kiểu khai báo `@AspectJ`. Không cần AspectJ compile-time/load-time weaving để chạy ví dụ này.

### 5.3. `src/main/java/com/example/boundary/OrderService.java`

```java
package com.example.boundary;

public class OrderService {

    public void submit(String id) {
        System.out.println("[TARGET] submit " + id);
    }

    public void submitViaThis(String id) {
        this.submit(id);
    }
}
```

`submitViaThis()` gọi `submit()` trên `this` của **raw target**. Nếu được gọi qua proxy, lời gọi vào `submitViaThis()` đến target, nhưng bước gọi tiếp `this.submit()` không quay ngược lại proxy.

### 5.4. `src/main/java/com/example/boundary/CheckoutFacade.java`

```java
package com.example.boundary;

public class CheckoutFacade {
    private final OrderService orders;

    public CheckoutFacade(OrderService orders) {
        this.orders = orders;
    }

    public void checkout(String id) {
        orders.submit(id);
    }

    public OrderService injectedReference() {
        return orders;
    }
}
```

`CheckoutFacade` đóng vai caller bean được container khởi tạo. `OrderService` được constructor-injected; ở cấu hình có aspect và proxy, tham chiếu `orders` là Spring AOP proxy.

### 5.5. `src/main/java/com/example/boundary/RawTargetHolder.java`

```java
package com.example.boundary;

public class RawTargetHolder {
    private OrderService raw;

    public void remember(OrderService target) {
        this.raw = target;
    }

    public OrderService raw() {
        return raw;
    }
}
```

Class này cố ý giữ **raw target reference** trước khi Spring hoàn tất việc cung cấp AOP proxy. Đây là **kỹ thuật thí nghiệm**, không nên dùng trong kiến trúc nghiệp vụ vì dễ bỏ qua audit, transaction hoặc policies khác.

### 5.6. `src/main/java/com/example/boundary/AuditAspect.java`

```java
package com.example.boundary;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Before("execution(public void com.example.boundary.OrderService.submit(..))")
    public void beforeSubmit(JoinPoint joinPoint) {
        calls.incrementAndGet();
        Object id = joinPoint.getArgs()[0];
        System.out.println("[AOP] before submit " + id);
    }

    public int calls() {
        return calls.get();
    }
}
```

Pointcut cố tình chọn **một method duy nhất**: `OrderService.submit(..)`. Nó không chọn `submitViaThis()` hoặc `CheckoutFacade.checkout()`. Điều này giúp phân biệt advice có hoặc không **do đường gọi**, thay vì do có nhiều method vô tình khớp.

`AtomicInteger` dùng để đếm số lần advice thực sự được gọi; kết quả không dựa vào parsing log.

### 5.7. `src/main/java/com/example/boundary/AppConfig.java`

```java
package com.example.boundary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public RawTargetHolder rawTargetHolder() {
        return new RawTargetHolder();
    }

    @Bean
    public OrderService orderService(RawTargetHolder holder) {
        OrderService target = new OrderService();
        holder.remember(target); // Chỉ để đối chiếu raw target với proxy.
        return target;
    }

    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect();
    }

    @Bean
    public CheckoutFacade checkoutFacade(OrderService orderService) {
        return new CheckoutFacade(orderService);
    }
}
```

`@EnableAspectJAutoProxy(proxyTargetClass = true)` dùng **class-based proxy** để `CheckoutFacade` inject `OrderService` theo concrete type thuận tiện cho demo. Trong ứng dụng interface-based, Spring có thể dùng JDK proxy; nguyên tắc **caller đi qua proxy** vẫn không đổi.

Khi `orderService()` tạo target và lưu vào `RawTargetHolder`, Spring còn phải thực hiện các bước post-processing để bọc proxy trước khi một consumer khác inject bean `orderService`. Vì thế, cùng object `target` ban đầu có thể được truy cập qua hai con đường: raw reference và Spring proxy reference.

### 5.8. `src/main/java/com/example/boundary/DemoApp.java`

```java
package com.example.boundary;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApp {
    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            OrderService proxy = context.getBean(OrderService.class);
            RawTargetHolder holder = context.getBean(RawTargetHolder.class);
            OrderService raw = holder.raw();
            CheckoutFacade facade = context.getBean(CheckoutFacade.class);
            AuditAspect aspect = context.getBean(AuditAspect.class);

            System.out.println("proxyIsAop=" + AopUtils.isAopProxy(proxy));
            System.out.println("rawIsAop=" + AopUtils.isAopProxy(raw));
            System.out.println("facadeHasProxy="
                    + (facade.injectedReference() == proxy));
            System.out.println("sameReference=" + (proxy == raw));
            System.out.println("sameTarget="
                    + (((Advised) proxy).getTargetSource().getTarget() == raw));

            System.out.println("-- from another bean --");
            facade.checkout("A");

            System.out.println("-- external through proxy --");
            proxy.submit("B");

            System.out.println("-- self-invocation via target.this --");
            proxy.submitViaThis("C");

            System.out.println("-- direct raw target --");
            raw.submit("D");

            System.out.println("-- outside Spring container --");
            new OrderService().submit("E");

            System.out.println("adviceCalls=" + aspect.calls());
        }
    }
}
```

`Advised.getTargetSource().getTarget()` là **kiểm tra thí nghiệm** để xác minh proxy của bean singleton đang trỏ tới đúng raw target trong `RawTargetHolder`. Đừng làm vậy trong business code để lách chính sách AOP. Với `TargetSource` tùy chỉnh hoặc target theo scope, việc unwrap/nhận một target ổn định có thể không hợp lệ.

### 5.9. Chạy và kết quả dự kiến

Yêu cầu JDK 17+, Maven và kết nối để tải dependency. Tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.boundary.DemoApp
```

Các dòng do code `DemoApp` và aspect in ra, theo thứ tự (không tính log Maven/Spring nếu có):

```text
proxyIsAop=true
rawIsAop=false
facadeHasProxy=true
sameReference=false
sameTarget=true
-- from another bean --
[AOP] before submit A
[TARGET] submit A
-- external through proxy --
[AOP] before submit B
[TARGET] submit B
-- self-invocation via target.this --
[TARGET] submit C
-- direct raw target --
[TARGET] submit D
-- outside Spring container --
[TARGET] submit E
adviceCalls=2
```

**Giải thích từng nhóm đầu ra:**

1. `proxyIsAop=true` nhưng `rawIsAop=false`: cùng nghiệp vụ có **một reference Spring AOP proxy** và **một reference raw target** khác nhau.
2. `facadeHasProxy=true`: Spring đã inject đúng tham chiếu proxy vào caller `CheckoutFacade`.
3. `sameTarget=true`: trong demo singleton này, proxy thực sự bao target object đang giữ trong `RawTargetHolder`. Đây là bằng chứng rằng sự khác biệt advice **không đến từ việc thay service instance**.
4. Lời gọi từ `CheckoutFacade` và lời gọi trực tiếp trên biến `proxy` đều đi qua proxy và khớp pointcut `submit(..)`, nên có hai dòng `[AOP]`.
5. `proxy.submitViaThis("C")` đi vào `submitViaThis`, sau đó `this.submit("C")` gọi ngay trên target; pointcut chọn `submit`, nhưng lời gọi nội bộ không quay về proxy, vì vậy không có dòng `[AOP]`.
6. Lời gọi trên `raw` và instance từ `new` đều không đi qua proxy; chúng chỉ in `[TARGET]`. Counter cuối là `2`, không phải `5`.

Thí nghiệm thể hiện **điểm chặn nằm ở reference/đường gọi**, không phải thuộc tính cố định của method `submit` trong source code.

## 6. Ma trận hành vi: phương thức khớp pointcut chưa đủ

Giả sử chỉ có `@Before` cho `OrderService.submit(..)`:

| Cách gọi | Reference tại thời điểm gọi | Có `@Before` cho `submit`? | Lý do |
| --- | --- | --- | --- |
| `facade.checkout("A")` gọi `orders.submit("A")` | `orders` là proxy do Spring inject | **Có** | Lời gọi cross-bean đi vào Spring proxy. |
| `context.getBean(OrderService.class).submit("B")` | Bean reference là proxy | **Có** | Caller dùng Spring proxy trực tiếp. |
| `proxy.submitViaThis("C")` → `this.submit("C")` | Internal reference là raw target `this` | **Không** cho lời gọi `submit` nội bộ | Self-invocation bypass proxy. |
| `raw.submit("D")` | Raw target reference | **Không** | Không có proxy trên đường gọi. |
| `new OrderService().submit("E")` | Object tự tạo ngoài container | **Không** | Spring chưa quản lý hay auto-proxy object. |
| `@PostConstruct` trong target gọi `this.submit(...)` | Raw target trong init callback | **Không** theo proxy AOP thông thường | Callback khởi tạo xảy ra trước khi proxy bình thường sẵn sàng; lại là lời gọi nội bộ. |
| Bean khác inject interface của service rồi gọi method qua JDK proxy | Interface proxy | **Có** nếu pointcut khớp và method có thể gọi qua interface | Proxy style khác, ranh giới interception vẫn vậy. |

Trong hệ thống thực tế, một lời gọi có thể có **nhiều advisors** và một method khác cũng được advice. Bảng chỉ đếm riêng advice `@Before` trên `submit` như giả thiết, tránh nhầm “không có advice `submit`” với “không có advisor nào hoạt động”.

## 7. Phân biệt “gọi từ bean khác” với “cross-bean thật sự qua proxy”

### 7.1. Caller bean thuộc Spring nhưng tự `new` target

```java
public class CheckoutFacade {
    public void checkout() {
        OrderService service = new OrderService();
        service.submit("X");
    }
}
```

Dù `CheckoutFacade` được đăng ký thành Spring bean, biến `service` trong method này là object **tự tạo**. Không có bean lookup, injection hay proxy Spring AOP. Aspect dành cho `OrderService.submit` không chạy trong lời gọi đó.

**Cách làm đúng:** inject service đã được Spring quản lý qua constructor hoặc truy cập container theo thiết kế được kiểm soát; tránh tự `new` một service cần transaction, audit, caching hoặc authorization advice.

### 7.2. Caller giữ tham chiếu cũ tới raw target

Có thể xảy ra khi code tự tạo object rồi đem object ấy đăng ký vào `BeanFactory`, hoặc một factory/registry chia sẻ raw reference ra ngoài trước khi Spring tạo proxy. Khi đó dù target cũng được Spring quản lý, caller cũ vẫn gọi trên **reference đã giữ**, không tự biến thành proxy sau khi auto-proxy creator chạy.

```text
rawRef = target;              // Giữ object gốc.
springBean = getBean(...);    // Spring có thể trả proxy quanh target.
rawRef.submit();              // Bypass.
springBean.submit();          // Qua proxy nếu advisor khớp.
```

Đây là mô hình chẩn đoán, **không phải khuyến nghị trữ raw target**. Luôn cố gắng thống nhất một đường nhận dependency từ Spring container để tránh chính sách dùng chung chạy lúc có lúc không.

### 7.3. Caller lấy bean thuộc context khác

Một ứng dụng có thể có **parent và child `ApplicationContext`**, hoặc nhiều context độc lập. Auto-proxy configuration là cục bộ cho context tương ứng, nên một bean ngoài context đang bật `@EnableAspectJAutoProxy` có thể không được auto-proxy giống bean trong context đó.

Lời gọi từ bean khác không chứng minh “cùng context” hay “cùng hạ tầng AOP”. Khi debug, xác định rõ context nào sở hữu target và aspect, nơi `@Bean`/`@ComponentScan` đăng ký và cách dependency được resolve.

## 8. Self-invocation: lời gọi không quay lại proxy

Trong demo mục 5, `proxy.submitViaThis("C")` đi vào target, nhưng lệnh `this.submit("C")` bên trong target không quay ra Spring proxy. Do đó advice chỉ nhắm `submit(..)` không chạy ở lượt gọi nội bộ, dù `submit` là public và pointcut khớp. Nếu advisor còn nhắm `submitViaThis`, method ngoài có thể được advice nhưng điều đó không làm `submit` được intercept thêm lần nữa.

Đổi JDK proxy sang CGLIB không khắc phục self-invocation. Khi policy thật sự cần áp dụng cho method được gọi bên trong, nên cân nhắc **đặt boundary ở method ngoài** nếu policy thuộc toàn operation, hoặc **chuyển trách nhiệm sang một Spring collaborator bean** rồi gọi qua dependency được inject. Self-injection có thể liên quan circular dependencies, lifecycle và kiểu proxy; `AopContext.currentProxy()` cần `exposeProxy=true` cùng một AOP invocation đang hoạt động nên chỉ phù hợp ngoại lệ. Các bài tiếp theo phân tích và kiểm chứng riêng từng lựa chọn.

## 9. Ranh giới khởi tạo: bean chưa trao proxy hoàn chỉnh cho caller

Constructor, `@PostConstruct`, `InitializingBean.afterPropertiesSet()` và custom init method của chính target thường chạy **trước khi bean được công bố qua AOP proxy hoàn chỉnh**. Nếu callback gọi `this.submit("INIT")`, đó còn là self-invocation; lời gọi tới `submit` không được advice nhờ Spring AOP proxy. Vì thế chỉ nhìn annotation trên method không đủ để suy ra transaction, audit hay cache interceptor đã chạy.

Một callback trên **bean khác** có thể gọi qua proxy của dependency đã khởi tạo hoàn chỉnh; không nên gộp mọi callback vào cùng kết luận “luôn bypass”. Đối với startup work cần interception, hãy dùng một coordinator khác gọi service qua dependency đã được Spring quản lý ở thời điểm phù hợp, chẳng hạn `ApplicationRunner` trong Spring Boot. Bài riêng về lifecycle giải thích kỹ các mốc và ngoại lệ.

## 10. Ảnh hưởng của JDK proxy và CGLIB tới cross-bean invocation

Nguyên tắc **phải đi qua proxy** áp dụng cho cả hai cơ chế, nhưng kiểu tham chiếu mà bean khác có thể inject/call khác nhau.

| Khía cạnh | JDK dynamic proxy | Class-based (CGLIB) proxy |
| --- | --- | --- |
| Kiểu proxy | Triển khai interface phù hợp | Subclass của target class |
| Inject thường phù hợp | Theo **business interface** | Theo interface hoặc concrete class khi proxy hợp lệ |
| Method có thể gọi qua proxy | Những method thuộc proxied interfaces | Những method mà subclass proxy có thể expose/override theo giới hạn Spring |
| Lời gọi `this.method()` từ target | Bypass proxy | Bypass proxy |
| Caller dùng `new Target()` | Không có Spring AOP proxy | Không có Spring AOP proxy |
| Raw target reference được giữ ngoài proxy | Bypass | Bypass |
| `final`/private methods | Interface proxy intercept ở ranh giới method interface khi khả dụng; private method nội bộ không là contract interface | Không override được final/private method |
| Kiểm tra tại runtime | `AopUtils.isJdkDynamicProxy(bean)` | `AopUtils.isCglibProxy(bean)` |

Ví dụ một service triển khai `OrderOperations` nhưng caller inject concrete `OrderService` trong khi Spring dùng JDK proxy. Điều này có thể gây lỗi resolve dependency theo concrete class vì JDK proxy **không phải instance của implementation class**. Khi đó nên inject qua business interface, hoặc chủ động chọn class-based proxy nếu có nhu cầu và target có thể subclass.

**Đừng đổi proxy type để che lỗi gọi trực tiếp raw target.** Dù JDK hay CGLIB, nếu tham chiếu không đi qua proxy, advice vẫn không có cơ hội chặn method execution.

## 11. Ranh giới interception khi dùng Spring Boot hoặc Spring Framework thuần

### Spring Framework thuần

Thường cần các thư viện Spring Context, Spring AOP và AspectJ hỗ trợ `@AspectJ`, đồng thời khai báo:

```java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}
```

Kèm theo một bean `@Aspect` được đăng ký bằng `@Bean` hoặc `@Aspect` + `@Component` nằm trong component scan. `@Aspect` **không tự biến class thành component** nếu thiếu cơ chế đăng ký bean phù hợp.

### Spring Boot

Boot có auto-configuration của AOP tùy dòng phiên bản/starter và cấu hình. Ví dụ Boot 3.x sử dụng `spring-boot-starter-aop`; Boot 4.x có `spring-boot-starter-aspectj`. Auto-configuration làm cho việc bật auto-proxy thuận tiện hơn, nhưng **không tự thay đổi ranh giới lời gọi của Spring proxy AOP**.

Ngay cả khi `@Transactional`, `@Cacheable` hoặc `@Async` được cấu hình qua Spring proxy, lời gọi nội bộ/đi thẳng raw target vẫn phải được xem xét theo cơ chế riêng của từng subsystem và proxy của nó. Chỉ từ việc một annotation có mặt trên method chưa đủ khẳng định interceptor sẽ chạy.

## 12. Cách kiểm chứng đường gọi trong ứng dụng đang chạy

### 12.1. Bước 1: xác định bean reference của caller

Khi có quyền truy cập `ApplicationContext` ở test hoặc thời điểm chẩn đoán:

```java
Object service = context.getBean("orderService");
System.out.println(org.springframework.aop.support.AopUtils.isAopProxy(service));
```

Đây là fragment cần biến `context`. Nếu `true`, bean trả về là Spring AOP proxy. Nếu `false`, chưa thể kết luận framework lỗi: có thể pointcut không khớp, bean không phải target cần advice, aspect chưa đăng ký hoặc bạn lấy bean ở context khác.

Trong một test, có thể so sánh bằng `==` giữa dependency mà caller đang giữ và reference Spring trả cho cùng singleton bean. Điều đó giúp phát hiện caller đang giữ raw/old reference. **Không so sánh bằng `equals` để kết luận cùng Java reference**, vì Spring proxy có xử lý equality riêng.

### 12.2. Bước 2: kiểm tra advisor trên proxy, không chỉ có proxy

```java
Object bean = context.getBean("orderService");

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

Đây là fragment chẩn đoán; output không cố định theo Spring version và advisor set. Proxy có thể tồn tại do transaction, scoped proxy hoặc một advisor khác; **có proxy không đồng nghĩa `@Before` của bạn khớp method đang gọi**.

### 12.3. Bước 3: so sánh với lời gọi trực tiếp raw target trong một test cô lập

Nếu bạn thật sự cần chứng minh cơ chế, dùng test/demonstration như `RawTargetHolder` ở phần 5. Không lan truyền raw references trong code sản xuất chỉ để debug. Xác nhận:

- Lời gọi qua dependency Spring inject chạy advice.
- Lời gọi trên raw target vẫn thực hiện nghiệp vụ nhưng không có advice.
- Lời gọi self-invocation chỉ gây advice cho **method bên ngoài** nếu method đó cũng khớp pointcut; method bên trong không tự đi qua proxy.
- Kết quả nghiệp vụ, ngoại lệ và state biến đổi của target đúng như mong đợi.

Nếu lời gọi qua proxy không chạy advice, kiểm tra cả pointcut và bean lifecycle thay vì kết luận ngay do self-invocation.

### 12.4. Bước 4: đo bằng counter hoặc test event

Không chỉ tìm một dòng log giống nhau: một API request có thể gọi method hai lần; log từ transaction/filter cũng có thể trông tương tự.

Một kiểm thử tốt nên có counter/event collector ở advice và ở target:

```text
Một lần clientBean -> proxy.submit():
    advice count +1, target count +1

Một lần raw.submit():
    advice count +0, target count +1

Một lần this.submit() trong raw target:
    advice count +0, target count +1
```

Đây là số lần kỳ vọng **với một advice chỉ áp dụng cho `submit`**. Nếu có nhiều advisors hoặc một `@Around` không gọi `proceed()`, cần chỉnh expected counts theo chính sách thực tế.

## 13. Hành vi `@Around` còn làm khác biệt giữa proxy và target rõ hơn

Với `@Before`, raw target bypass chủ yếu làm thiếu thao tác trước method. Với `@Around`, hậu quả có thể nghiêm trọng hơn: khi đi qua proxy, advice **có thể quyết định có gọi target hay không**, thay đổi kết quả trả về hoặc chuyển tiếp ngoại lệ. Khi bypass proxy, toàn bộ khả năng điều phối đó bị bỏ qua.

Ví dụ một method fragment trong aspect:

```java
@Around("execution(public * com.example.boundary.OrderService.submit(..))")
public Object around(org.aspectj.lang.ProceedingJoinPoint pjp)
        throws Throwable {
    long start = System.nanoTime();
    try {
        return pjp.proceed(); // Qua proxy: tiếp tục thực thi target.
    } finally {
        System.out.println("durationNanos=" + (System.nanoTime() - start));
    }
}
```

Đây là **method fragment minh họa**, cần đặt vào một class `@Aspect` đã đăng ký bean và import annotation `Around`. Không thêm đồng thời fragment này vào project phần 5 nếu vẫn muốn giữ nguyên stdout đã công bố.

Với một đường gọi raw target, không có `ProceedingJoinPoint` của Spring AOP cho lần gọi ấy và không có logic đo thời gian trong aspect. Điều tương tự áp dụng cho audit, retry, security check hoặc caching advice được thiết kế dựa trên interception.

**Không tự coi bypass proxy là lỗi performance nhỏ.** Nó có thể khiến một yêu cầu bảo mật hoặc transaction policy không được áp dụng. Nếu một hành vi bắt buộc về tính đúng đắn, nên kết hợp thiết kế API/bean boundary, unit/integration tests và chính sách bảo vệ ngoài AOP khi cần.

## 14. Các lỗi phổ biến và hướng khắc phục

| Triệu chứng | Nguyên nhân có thể | Cách xử lý |
| --- | --- | --- |
| `@Before` chạy khi gọi từ controller, không chạy khi gọi trong cùng service | Controller có reference tới proxy, nhưng service gọi `this.method()` trên raw target | Tách method cần advice sang Spring bean khác; gọi qua proxy được inject. |
| Có `@Aspect` nhưng lời gọi từ bean khác vẫn không được advice | Aspect chưa là Spring bean, proxy chưa được bật, pointcut không khớp, hoặc caller giữ raw reference | Kiểm tra registration → context → AOP creator → proxy → pointcut → đường gọi. |
| Tự tạo service bằng `new` khiến transaction/audit không chạy | Instance ngoài Spring container | Đưa service vào IoC và inject bean Spring quản lý. |
| `@PostConstruct` gọi method có aspect nhưng advice không chạy | Initialization xảy ra trên raw bean trước proxy + self-invocation | Di chuyển invocation sang giai đoạn sau khi context sẵn sàng qua bean khác. |
| Ép `proxyTargetClass=true` nhưng `this.method()` vẫn bypass | CGLIB không đổi bản chất self-invocation của Spring proxy AOP | Không dùng proxy type để sửa call boundary; tách bean hoặc cấu hình đường gọi hợp lệ. |
| Advice có lúc chạy, có lúc không với cùng class | Có nhiều instances/references, một số qua proxy, một số raw/new, hoặc bean khác context | Theo dõi bean origin, `AopUtils`, identity và nơi dependency được resolve. |
| `getBean(ConcreteClass.class)` lỗi khi dùng JDK proxy | Proxy chỉ triển khai interface nên không thuộc concrete class | Inject theo business interface, hoặc chọn class proxy có chủ đích nếu hợp lệ. |
| Bean được proxy nhưng method không chạy advice | Proxy có advisor khác hoặc pointcut không khớp method đó | Liệt kê advisors, đo invocation và kiểm tra pointcut cụ thể. |
| Cố dùng `AopContext.currentProxy()` rồi gặp lỗi | Chưa bật `exposeProxy`, không trong proxy invocation hoặc cast sai type | Ưu tiên tái cấu trúc bean; chỉ dùng cơ chế này khi thật sự cần và được cấu hình đúng. |
| Unit test dùng `new Service()` nhưng kỳ vọng aspect chạy | Test không khởi tạo Spring AOP context | Test Spring AOP bằng integration test có context, hoặc test nghiệp vụ raw riêng không kỳ vọng advice. |

## 15. Quy trình thiết kế ranh giới Spring AOP bền vững

1. **Xác định method nào là điểm chặn có chủ đích**, ví dụ `OrderService.submit()`.
2. **Đặt target thành Spring bean** và đăng ký aspect thành bean trong đúng `ApplicationContext`.
3. **Bật Spring auto-proxy đúng cách**, qua Java config, XML hoặc Spring Boot auto-configuration tương ứng.
4. **Thiết kế public entry point được gọi từ bean khác**, tránh để policy quan trọng phụ thuộc vào self-invocation.
5. **Inject theo kiểu phù hợp với proxy strategy**; với JDK proxy, ưu tiên business interface. Không tự `new` service cần advice.
6. **Không giữ raw references ngoài container**; nếu cần debug, dùng một test cô lập và loại khỏi code sản xuất.
7. **Kiểm tra `AopUtils.isAopProxy` và lời gọi thực tế** trên dependency của caller, không chỉ bean type hoặc annotation.
8. **Đo advice count và target count**, đặc biệt với `@Around`, lỗi/exception, transaction và nhiều advisors.
9. **Kiểm tra các context/scope**, khi service được chuyển module, web child context hoặc dùng prototype/scoped proxy.
10. **Đưa test về call boundary vào regression suite**, để refactor không vô tình chuyển từ proxy reference sang raw target reference.

### Tài liệu chính thức của Spring

- Spring Framework — Proxying Mechanisms, Understanding AOP Proxies: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Declaring an Aspect: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html
- Spring Framework — AOP Concepts: https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html
- Spring Framework — Programmatic AOP Proxy Creation: https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html

**Tóm lại, trong Spring AOP, cùng một target method có thể được gọi theo hai đường: qua Spring proxy hoặc trực tiếp trên raw target. Chỉ đường qua proxy mới thực hiện advisor/advice phù hợp.** Thiết kế bean boundaries và kiểm thử đúng reference của caller chính là cách bảo đảm Spring Aspect được áp dụng nhất quán.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invocation-boundaries-002">Tự gọi nội bộ qua this và nguyên nhân bỏ qua advice</a>

<details>
<summary>Xem chi tiết</summary>

# Tự gọi nội bộ qua `this` và nguyên nhân bỏ qua advice trong Spring AOP

## 1. Mục tiêu của bài học

Khi sử dụng **Spring AOP dựa trên proxy**, một phương thức có thể nhận advice khi được gọi từ một bean khác nhưng **không nhận advice** khi được gọi qua `this` ngay trong lớp của nó. Đây được gọi là **self-invocation** (lời gọi nội bộ lên chính đối tượng).

Ví dụ:

```java
public void placeOrder() {
    this.saveOrder(); // Lời gọi nội bộ, không quay lại Spring AOP proxy
}

public void saveOrder() {
    // Có thể là phương thức mà một advice nhắm tới
}
```

Nếu `placeOrder()` được gọi **qua proxy** thì advice dành cho `placeOrder()` vẫn có thể chạy. Tuy nhiên, khi code đã đi vào **target object** và gọi `this.saveOrder()`, lời gọi thứ hai **không đi qua proxy**, nên advice dành riêng cho `saveOrder()` không được kích hoạt ở lần gọi đó. Nếu client bên ngoài gọi `proxy.saveOrder()` thì advice của `saveOrder()` lại có thể chạy bình thường.

Sau bài học, bạn sẽ:

1. Diễn giải được **ranh giới proxy** trong Spring AOP và lý do self-invocation bỏ qua advice.
2. Dự đoán đúng output khi gọi cùng một method từ bên ngoài và từ bên trong target.
3. Phân biệt self-invocation với các lý do khác khiến advice không chạy (pointcut, bean registration, `final` method, object tạo bằng `new`).
4. Sửa code theo hướng **tách trách nhiệm thành các Spring beans** và gọi xuyên qua proxy.
5. Hiểu những đánh đổi khi dùng **self-injection** hoặc `AopContext.currentProxy()`, và hệ quả thực tế với transaction/cache/async ở chế độ proxy.

Bài chỉ nói về **hành vi Spring Aspect/Spring AOP**; không trình bày lại các khái niệm Aspect tổng quát.

## 2. Hai đối tượng quan trọng: proxy và target

Giả sử ứng dụng có bean `OrderService` phù hợp với một advisor từ lớp `@Aspect`. Spring có thể trả về một **AOP proxy** cho client thay vì trả trực tiếp target.

```text
Lời gọi từ bean/client khác
           |
           v
   OrderService proxy
           |
           +--> Pointcut/advisor khớp phương thức ngoài?
           |         |
           |         +--> chạy advice tương ứng
           |
           v
      Target object
           |
           +--> logic trong method
                  |
                  +--> this.innerMethod()
                              |
                              v
                        Target method trực tiếp
                        (KHÔNG quay lại proxy)
```

Ở đường gọi đầu tiên, client giữ **tham chiếu proxy**. Spring có cơ hội xây chuỗi interceptor/advice cho method được gọi qua proxy. Nhưng bên trong target, `this` trỏ đến **đối tượng đang thực thi logic nghiệp vụ**, không phải một lời gọi mới tới proxy Spring AOP. Việc gọi method qua tham chiếu `this` không tự quay về proxy để xét advisor lần nữa.

Trong Spring AOP, **advice được chèn tại đường gọi đi qua proxy**, không phải tự động được chèn vào mọi lệnh gọi Java bên trong class. Spring dùng `@AspectJ` để mô tả pointcut/advice nhưng lúc chạy vẫn là **Spring AOP proxy-based** nếu không cấu hình weaving riêng.

### 2.1. Vì sao pointcut khớp vẫn không chạy?

Xem ví dụ:

```java
@Before("execution(* example.OrderService.saveOrder(..))")
public void auditSaveOrder() {
    System.out.println("advice: saveOrder");
}
```

Biểu thức pointcut có thể **khớp chính xác** với `OrderService.saveOrder()`. Điều đó mới trả lời câu hỏi “method nào nằm trong phạm vi advisor?”. Còn để advice thực thi, invocation cần đi qua **Spring AOP proxy đang mang advisor ấy**.

`this.saveOrder()` gọi trực tiếp method trên target. Do đó:

- Pointcut **không nhất thiết sai**.
- Aspect **không nhất thiết chưa được đăng ký**.
- Bean **không nhất thiết thiếu proxy**.
- Đúng hơn, **lời gọi cụ thể đã bỏ qua ranh giới proxy**.

Đây là sự khác nhau giữa **khả năng áp dụng advisor** và **lời gọi thật sự đi qua advisor**.

## 3. So sánh các đường gọi

Giả sử cả `outer()` và `inner()` đều public, target là Spring bean đã có AOP proxy và pointcut advisor nhắm tới `inner()`.

| Lời gọi | Có đi qua proxy khi bước vào `inner()`? | Advice dành riêng cho `inner()` có cơ hội chạy? |
| --- | --- | --- |
| Bean khác gọi `service.inner()` trên tham chiếu Spring cung cấp | **Có** | **Có**, nếu pointcut và điều kiện khác khớp |
| Bean khác gọi `service.outer()`, trong `outer()` dùng `this.inner()` | **Không** cho lời gọi `inner()` | **Không** ở lời gọi `inner()` nội bộ |
| Trong `outer()` gọi `inner()` không ghi `this` | **Không** | **Không**; lời gọi ngầm cũng là `this.inner()` |
| Bên ngoài gọi `new Service().inner()` | **Không** | **Không** vì instance không được Spring proxy |
| `outer()` gọi `anotherBean.inner()` bằng tham chiếu bean đã được proxy | **Có** tại bean thứ hai | **Có**, nếu advisor khớp |
| Trong target dùng `AopContext.currentProxy()` rồi gọi method của proxy | **Có**, khi proxy được expose và gọi hợp lệ | **Có**; nhưng cách này gắn chặt business code với Spring AOP |
| Trong target inject chính proxy của mình rồi gọi qua tham chiếu đó | **Có**, nếu Spring thực sự inject proxy phù hợp | **Có**; song có chi phí thiết kế/cấu hình vòng phụ thuộc |

**Phân biệt rõ:** Khi `outer()` được gọi qua proxy, advice nhắm riêng vào `outer()` **vẫn có thể chạy**. Chỉ advice tương ứng với `inner()` bị bỏ qua tại bước gọi nội bộ. Không nên nói ngắn gọn “self-invocation làm tất cả AOP của `outer()` biến mất”.

## 4. Ví dụ Java 17+ chạy được: quan sát ba con đường

Ví dụ dùng **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**, **Java 17+**, Maven và `@EnableAspectJAutoProxy`. Không dùng HTTP server hoặc cơ sở dữ liệu để chỉ quan sát ranh giới Spring AOP.

### 4.1. Cấu trúc project

```text
spring-aop-self-invocation/
├── pom.xml
└── src/main/java/example/
    ├── AppConfig.java
    ├── OrderService.java
    ├── PersistenceService.java
    ├── WorkflowService.java
    ├── TraceAspect.java
    └── Main.java
```

### 4.2. Maven: `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>example</groupId>
    <artifactId>spring-aop-self-invocation</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring.version>6.2.19</spring.version>
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

Trong ứng dụng thực tế, nên quản lý version `spring-*` qua **Spring Framework BOM** hoặc Spring Boot BOM để tránh lệch phiên bản. Ví dụ này khai báo bản cụ thể để có một project độc lập dễ thử nghiệm.

### 4.3. Target tự gọi nội bộ: `OrderService.java`

```java
package example;

public class OrderService {

    public void submit() {
        System.out.println("order: submit");
        this.persist();
    }

    public void persist() {
        System.out.println("order: persist");
    }
}
```

`submit()` gọi `this.persist()` bên trong **cùng target class**. Không có Spring bean lookup hay tham chiếu proxy ở giữa hai method.

### 4.4. Target khác để minh họa hướng sửa: `PersistenceService.java`

```java
package example;

public class PersistenceService {

    public void persist() {
        System.out.println("storage: persist");
    }
}
```

### 4.5. Bean điều phối gọi sang bean khác: `WorkflowService.java`

```java
package example;

public class WorkflowService {

    private final PersistenceService persistenceService;

    public WorkflowService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void submit() {
        System.out.println("workflow: submit");
        persistenceService.persist();
    }
}
```

Khác với `this.persist()` trong `OrderService`, tham chiếu `persistenceService` sẽ nhận **bean do Spring cung cấp**. Nếu bean ấy được proxy, lời gọi có đường đi qua proxy.

### 4.6. Hai advice được khai báo trong Spring aspect: `TraceAspect.java`

```java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {

    @Before("execution(* example.OrderService.persist(..))")
    public void beforeOrderPersist() {
        System.out.println("advice: order persist");
    }

    @Before("execution(* example.PersistenceService.persist(..))")
    public void beforeStoragePersist() {
        System.out.println("advice: storage persist");
    }
}
```

Hai pointcut đều nhắm tới method tên `persist()`, nhưng **khác target class**. Điều này tránh phụ thuộc thứ tự giữa nhiều advice cho cùng một invocation và giúp stdout dễ dự đoán.

### 4.7. Cấu hình Spring: `AppConfig.java`

```java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean
    public OrderService orderService() {
        return new OrderService();
    }

    @Bean
    public PersistenceService persistenceService() {
        return new PersistenceService();
    }

    @Bean
    public WorkflowService workflowService(PersistenceService persistenceService) {
        return new WorkflowService(persistenceService);
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
```

Lưu ý:

- `TraceAspect` có `@Aspect` nhưng không cần `@Component` vì được **đăng ký bằng `@Bean`**.
- `@EnableAspectJAutoProxy` bật Spring AOP auto-proxy cho context; `proxyTargetClass=true` ép dùng **class-based proxy** cho các target phù hợp.
- Cả `OrderService` và `PersistenceService` có method match pointcut và có thể nhận proxy.
- Đổi từ JDK sang CGLIB **không chữa self-invocation**. Chọn CGLIB ở đây chỉ giúp ví dụ dùng trực tiếp concrete type và giải thích rõ điểm này.

### 4.8. Chạy thử: `Main.java`

```java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService order = context.getBean(OrderService.class);
            PersistenceService storage = context.getBean(PersistenceService.class);
            WorkflowService workflow = context.getBean(WorkflowService.class);

            System.out.println("order is proxy: " + AopUtils.isAopProxy(order));
            System.out.println("storage is proxy: " + AopUtils.isAopProxy(storage));

            System.out.println("-- call 1: external direct --");
            order.persist();

            System.out.println("-- call 2: self invocation --");
            order.submit();

            System.out.println("-- call 3: another bean --");
            workflow.submit();
        }
    }
}
```

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
```

**Các dòng do các method trong ví dụ in ra, theo đúng thứ tự:**

```text
order is proxy: true
storage is proxy: true
-- call 1: external direct --
advice: order persist
order: persist
-- call 2: self invocation --
order: submit
order: persist
-- call 3: another bean --
workflow: submit
advice: storage persist
storage: persist
```

### 4.9. Phân tích kết quả

**Lời gọi 1 — `order.persist()`:** `order` là tham chiếu tới **AOP proxy** được Spring trả về. Proxy nhận invocation `persist()`, tìm advice phù hợp trong `TraceAspect`, in `advice: order persist`, rồi gọi target và in `order: persist`.

**Lời gọi 2 — `order.submit()`:** `submit()` được gọi qua proxy, nhưng không có advice nào trong ví dụ nhắm tới `submit()`. Khi target bắt đầu thực thi `submit()`, dòng `order: submit` xuất hiện. Lệnh `this.persist()` bên trong target gọi trực tiếp `persist()`; vì **không đi qua AOP proxy lần thứ hai**, dòng `advice: order persist` **không xuất hiện**. Dòng `order: persist` vẫn xuất hiện — method nghiệp vụ **không bị bỏ qua**, chỉ **advice** bị bỏ qua.

**Lời gọi 3 — `workflow.submit()`:** `WorkflowService` giữ tham chiếu Spring đã inject cho `PersistenceService`. Khi nó gọi `persistenceService.persist()`, invocation đi **qua proxy của một bean khác** nên advice `beforeStoragePersist()` chạy. Đây là cách sửa thiết kế phổ biến: đặt hành vi cần advice vào **Spring service khác** và gọi qua dependency injection.

Kết quả minh họa **chính xác một nguyên nhân bỏ qua advice** mà không sử dụng transaction/cache/database. Nếu bạn bổ sung advisor khác hoặc thay cấu hình, output có thể khác và cần kiểm chứng lại.

## 5. CGLIB vẫn gặp self-invocation: tại sao?

Một hiểu nhầm thường gặp: “JDK proxy chỉ can thiệp interface, vậy bật CGLIB là `this.inner()` sẽ được advice.” Điều này **sai đối với Spring AOP proxy-based**.

Về mặt loại proxy:

- **JDK dynamic proxy:** proxy thực thi interface; caller từ bên ngoài gọi interface method trên proxy.
- **CGLIB proxy:** proxy là subclass có thể can thiệp các method override được; caller bên ngoài gọi method trên proxy.

Nhưng ở cả hai dạng, khi interceptor đã đưa lời gọi vào **target thực hiện business logic**, lời gọi nội bộ qua `this` ở target **không vượt qua proxy boundary**. Spring documentation nêu rõ self-invocation bằng tham chiếu `this`, tường minh hoặc ngầm định, bỏ qua advice.

Ví dụ trong mục 4 đã ép `proxyTargetClass = true`, nghĩa là dùng CGLIB — nhưng `this.persist()` **vẫn không chạy** `beforeOrderPersist()`.

**Ngoài ra, một method `private` hoặc `final` không được Spring AOP CGLIB advice qua cơ chế override.** Đó là một giới hạn *khác* với self-invocation. Với self-invocation, ngay cả khi `inner()` là `public` và hoàn toàn proxy được, đường gọi nội bộ vẫn bỏ qua proxy.

## 6. Cách sửa được ưu tiên: tách bean và gọi qua DI

### 6.1. Thiết kế ban đầu

```java
@Service
public class OrderService {

    public void submit() {
        validate();
        this.persist();
    }

    public void persist() {
        // Code dự kiến được advice riêng.
    }

    private void validate() {
        // ...
    }
}
```

Đây là **snippet minh họa trong ứng dụng Spring có component scanning**, không phải chương trình Java độc lập. Nếu advisor chỉ match `persist()`, lời gọi nội bộ ở đây không kích hoạt advice.

### 6.2. Tách phương thức cần advisor sang bean khác

```java
@Service
public class OrderPersistenceService {

    public void persist() {
        // Logic cần được Spring AOP advice.
    }
}
```

```java
@Service
public class OrderService {

    private final OrderPersistenceService persistence;

    public OrderService(OrderPersistenceService persistence) {
        this.persistence = persistence;
    }

    public void submit() {
        validate();
        persistence.persist(); // Đi qua tham chiếu Spring đã inject.
    }

    private void validate() {
        // ...
    }
}
```

Các snippet trên cần import `org.springframework.stereotype.Service` và cấu hình component scanning/AOP phù hợp. Spring sẽ tạo proxy cho `OrderPersistenceService` khi có advisor match; tham số constructor nhận tham chiếu do Spring cung cấp.

**Vì sao cách này hiệu quả?** Tại điểm `persistence.persist()`, đối tượng được gọi là **dependency bean khác**, có thể là proxy. Lời gọi qua tham chiếu này đi vào Spring AOP interceptor chain rồi mới đến target.

Các lợi ích đi kèm:

- Ranh giới giữa **điều phối** và **tác vụ được advice** rõ hơn.
- Tránh phụ thuộc trực tiếp vào `AopContext`.
- Dễ kiểm thử đơn vị vì `OrderPersistenceService` có thể được mock/fake qua constructor.
- Tránh self-injection và một số vấn đề circular dependency.
- Có thể áp dụng cho những boundary mà `@Transactional`, `@Cacheable`, `@Async` hoặc custom `@Aspect` cùng yêu cầu lời gọi qua proxy.

**Điều kiện:** Dependency phải là **bean Spring thực sự được proxy**, không phải object tự `new`; pointcut vẫn phải match đúng method đó. Không phải cứ tách class là Spring tự có advice.

### 6.3. Tách đúng ranh giới chức năng, không tách máy móc

Không nên tách mọi method private chỉ để chạy AOP. Chỉ cân nhắc tạo bean riêng khi method có **hợp đồng độc lập** cần lời gọi qua proxy, ví dụ một thao tác transactional độc lập, tác vụ async hoặc nghiệp vụ cần audit riêng. Phương thức tính toán nội bộ bình thường có thể tiếp tục là method `private` trong cùng lớp.

## 7. Lựa chọn thứ hai: inject tham chiếu proxy của chính bean

Spring documentation đề cập **self injection** như một lựa chọn khi không tiện tách bean. Ý tưởng là để class giữ **tham chiếu proxy do Spring cung cấp**, sau đó gọi `self.inner()` thay vì `this.inner()`.

Ví dụ minh họa:

```java
@Service
public class OrderService {

    private final ObjectProvider<OrderService> selfProvider;

    public OrderService(ObjectProvider<OrderService> selfProvider) {
        this.selfProvider = selfProvider;
    }

    public void submit() {
        selfProvider.getObject().persist();
    }

    public void persist() {
        // Advice nhắm tới persist() khi gọi qua proxy.
    }
}
```

Đây là **snippet minh họa**, cần import `org.springframework.beans.factory.ObjectProvider` và `org.springframework.stereotype.Service`. Nó không nằm trong dự án chạy độc lập ở mục 4.

`ObjectProvider` trì hoãn việc tìm bean cho đến khi `submit()` được gọi. Nếu context đã khởi tạo xong và `OrderService` được proxy thích hợp, `selfProvider.getObject()` có thể trả **tham chiếu proxy** rồi `persist()` được can thiệp.

**Các điều kiện/rủi ro cần kiểm tra:**

1. Phải có **đúng bean** được resolve từ provider. Nếu nhiều `OrderService` bean cùng type, cần qualifier hoặc cơ chế chọn bean.
2. Không gọi provider quá sớm trong constructor/`@PostConstruct`: lúc đó vòng đời proxy có thể chưa hoàn thành.
3. Tránh circular dependency khó debug, đặc biệt trong Spring Boot hiện đại vốn mặc định hạn chế circular references.
4. Nếu bean được JDK proxy theo interface, đừng kỳ vọng proxy assignable cho concrete class. Khi dùng self proxy theo interface, chọn provider type là **interface được proxy expose**.
5. Self-injection khiến class nhận biết sự hiện diện của container/proxy và thường kém rõ ràng hơn việc tách service.

Nói cách khác, self-injection có thể được dùng trong trường hợp có chủ đích, nhưng **không nên là lời giải mặc định** cho một boundary thiết kế không rõ ràng.

## 8. Lựa chọn cuối cùng: `AopContext.currentProxy()`

Spring cung cấp API `AopContext.currentProxy()` để truy xuất proxy hiện tại theo ngữ cảnh thread khi việc **expose proxy** đã được bật. Tuy nhiên, Spring documentation cảnh báo đây là phương án **không được khuyến khích**, vì business code bị gắn chặt với Spring AOP.

Đoạn minh họa:

```java
// Trong một class OrderService phù hợp, method được gọi qua proxy:
public void submit() {
    ((OrderService) AopContext.currentProxy()).persist();
}
```

Cần import `org.springframework.aop.framework.AopContext`. Với Spring Java configuration, phải bật:

```java
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
```

**Đây là hai snippet bổ sung, không phải chương trình độc lập.** Chúng còn yêu cầu `submit()` ban đầu được gọi **qua proxy** trong một invocation mà Spring expose proxy, và `OrderService` là kiểu có thể cast từ proxy (ví dụ CGLIB trong cấu hình trên).

Các hạn chế:

- `AopContext.currentProxy()` có thể ném `IllegalStateException` khi không có proxy hiện hành được expose (ví dụ gọi từ một target tự `new`, gọi ngoài proxy context hoặc `exposeProxy=false`).
- Bật `exposeProxy=true` tạo thêm coupling và có chi phí; **không giải quyết những lời gọi không đi vào proxy ngay từ đầu**.
- Nếu là **JDK proxy**, cast proxy thành implementation class là sai; cần cast về một interface được expose.
- Nó không biến `private`/`final` method thành join point có thể advice theo Spring proxy.
- Không khuyến nghị dùng API này chỉ để “vá” mọi self-invocation. Khi có thể, hãy **refactor để tránh self-invocation cần advice**.

## 9. Vì sao lấy bean từ ApplicationContext trong method không phải giải pháp tốt?

Có thể gặp kiểu code:

```java
public void submit() {
    applicationContext.getBean(OrderService.class).persist();
}
```

Với context/proxy đúng, lời gọi từ bean được tìm thấy có thể đi qua proxy. Nhưng cách này đưa **service locator** trực tiếp vào business method, làm code phụ thuộc vào `ApplicationContext`, khó unit test và khó nhận diện vòng phụ thuộc. Nếu dùng JDK proxy, việc tra bean theo concrete type còn có thể thất bại.

Ưu tiên constructor injection của một **bean khác có trách nhiệm riêng**. Nếu bắt buộc giữ cùng class, dùng một self-reference được quản lý và tài liệu hóa rõ, thay vì rải `getBean(...)` khắp phương thức nghiệp vụ.

## 10. Liên hệ với `@Transactional`, `@Cacheable` và `@Async`

Các tính năng Spring sau thường dựa vào infrastructure advisors/proxies ở **proxy mode**, dù không yêu cầu bạn tự viết `@Aspect`. Vì vậy chúng cùng chịu vấn đề **self-invocation**.

### 10.1. `@Transactional`: không kích hoạt interceptor mới ở lời gọi nội bộ

Ví dụ minh họa:

```java
@Service
public class PaymentService {

    public void checkout() {
        this.savePayment(); // Không đi qua transaction proxy.
    }

    @Transactional
    public void savePayment() {
        // Mong đợi transaction bắt đầu khi gọi savePayment().
    }
}
```

Nếu gọi `checkout()` từ ngoài, mà `checkout()` **không có transaction riêng** và không có transaction nào khác đang active, annotation `@Transactional` trên `savePayment()` sẽ **không khiến transaction được mở cho lời gọi nội bộ này**. Đó là vì transaction interceptor không được gọi ở bước `this.savePayment()`.

**Sắc thái quan trọng:** Nếu `checkout()` đã được gọi qua một transaction proxy và **có transaction đang active**, code trong `savePayment()` vẫn có thể chạy **bên trong transaction hiện hữu**. Nhưng metadata `@Transactional` **riêng của `savePayment()` không được đánh giá cho invocation nội bộ**, nên không tự mở một boundary transaction mới theo thuộc tính của nó.

Ví dụ:

```java
@Transactional
public void checkout() {
    this.savePayment();
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void savePayment() {
    // ...
}
```

Khi `checkout()` đi qua proxy, transaction bên ngoài được mở. Nhưng `this.savePayment()` **không đi qua interceptor mới**, do đó cấu hình `REQUIRES_NEW` tại `savePayment()` **không tạo transaction mới và không suspend transaction ngoài** trong đường gọi này.

Cần import `org.springframework.transaction.annotation.Transactional` và `org.springframework.transaction.annotation.Propagation`; để hoạt động thực tế còn cần transaction management, transaction manager và resources tương ứng. Đây chỉ là đoạn minh họa, **không** thuộc project Maven độc lập ở mục 4.

**Cách xử lý phù hợp:** Di chuyển thao tác `REQUIRES_NEW` sang một Spring bean khác, inject bean đó và gọi qua transaction proxy. Kiểm thử bằng database/transaction manager thật để xác nhận propagation, commit và rollback.

### 10.2. `@Cacheable`

```java
@Service
public class ProductService {

    public Product refresh(String id) {
        return this.findById(id); // Bỏ qua caching proxy tại bước nội bộ.
    }

    @Cacheable("products")
    public Product findById(String id) {
        // Đọc dữ liệu.
    }
}
```

Nếu `refresh()` gọi `this.findById(id)`, cache interceptor dành cho `findById` không chạy qua bước gọi ấy. Chuyển thao tác truy vấn cache sang một Spring bean khác và gọi qua bean proxy khi cần giữ caching semantics. Snippet cần các class và cache configuration tương ứng, không chạy độc lập.

### 10.3. `@Async`

```java
@Service
public class NotificationService {

    public void register() {
        this.sendEmail(); // Gọi đồng bộ trong cùng target.
    }

    @Async
    public void sendEmail() {
        // Tác vụ dự định chạy qua async interceptor.
    }
}
```

Trong proxy mode, lời gọi nội bộ `this.sendEmail()` **không đi qua async proxy** nên annotation `@Async` trên method này không tự chuyển execution sang executor. Nếu `register()` không có cơ chế async riêng thì `sendEmail()` chạy đồng bộ ngay trên thread gọi. Muốn async đúng, tách sang một Spring bean được `@EnableAsync`/Boot infrastructure xử lý, gọi qua proxy và kiểm chứng thread/exception semantics.

**Ranh giới học tập:** Những ví dụ transaction/cache/async chỉ làm rõ hệ quả của **Spring AOP proxy boundary**, không thay thế các bài học chuyên sâu về transaction, caching hay concurrency.

## 11. Các lời gọi trong vòng đời khởi tạo

Self-invocation không chỉ xuất hiện trong method nghiệp vụ thông thường. Ví dụ:

```java
@PostConstruct
public void init() {
    this.loadData();
}
```

Vì `@PostConstruct` chạy trong vòng đời khởi tạo bean, không nên dựa vào AOP proxy đã hoàn chỉnh để đảm bảo advice cho `loadData()`. Tương tự, gọi method có advice từ **constructor** hoặc từ code được thực thi trước khi container hoàn tất bean lifecycle rất dễ bỏ qua cơ chế interception.

Nếu cần tác vụ khởi động có advice, cân nhắc kích hoạt **sau khi context đã sẵn sàng** thông qua một bean điều phối/phù hợp với lifecycle, rồi gọi bean đích qua Spring reference. Với Spring Boot, có thể dùng `ApplicationRunner`/`CommandLineRunner` khi phù hợp, nhưng cần kiểm tra thứ tự khởi động và transaction/resources.

Không chuyển `this.loadData()` sang `@PostConstruct` rồi kỳ vọng lời gọi sẽ “đi qua proxy” chỉ nhờ có `@Aspect`.

## 12. Self-invocation khác gì với các lỗi AOP khác?

| Triệu chứng | Nguyên nhân cần kiểm tra | Dấu hiệu để phân biệt |
| --- | --- | --- |
| `inner()` chạy advice khi gọi từ ngoài nhưng không chạy khi gọi từ `outer()` | **Self-invocation** qua `this` | Cùng Spring bean/proxy; chỉ đường gọi khác |
| Mọi lời gọi tới `inner()` đều không chạy advice | Aspect chưa thành bean, AOP chưa bật hoặc pointcut không khớp | Kiểm tra aspect bean, auto-proxy config, advisor |
| `AopUtils.isAopProxy(service) == false` | Bean chưa có proxy hoặc đang dùng instance ngoài Spring | Kiểm tra `context.getBean`, registration, advisor match |
| Method `private` hoặc `final` không nhận advice qua class-based Spring proxy | Giới hạn proxy/override method | Gọi từ ngoài cũng không cứu được method không proxy được |
| `inner()` gọi qua proxy nhưng advice vẫn không chạy | Pointcut, method signature, runtime args hoặc advisor không khớp | Kiểm tra `@Before`, class/method, proxy type, runtime match |
| Object tạo bằng `new Service()` không nhận advice | Instance ngoài container, không được Spring bọc proxy | Gọi qua object đó luôn không vào Spring AOP |
| Aspect bean tồn tại nhưng chính aspect không có proxy | Hành vi Spring AOP bình thường | Aspect bean là **nguồn advisor**, không phải target auto-proxy thông thường |
| JDK proxy không cast được sang implementation | Runtime type là interface-based proxy | Inject/call qua interface hoặc dùng class-based proxy có kiểm chứng |
| Đổi `proxyTargetClass = true` mà self-invocation vẫn bỏ qua advice | JDK/CGLIB đều là proxy-based | Cần thay **đường gọi**, không chỉ chiến lược proxy |

### Checklist chẩn đoán nhanh

1. **Lấy bean từ Spring:** `context.getBean(...)` hoặc constructor injection, không tự `new`.
2. **Kiểm tra proxy:** `AopUtils.isAopProxy(bean)`; nhớ rằng proxy tồn tại **không chứng minh** mọi method có advice.
3. **Kiểm tra đường gọi:** dùng debug/log đặt tại `outer()` và `inner()`; có đoạn `this.inner()` hoặc `inner()` ngầm định không?
4. **So sánh:** gọi `inner()` trực tiếp từ caller ngoài trên Spring proxy. Nếu advice xuất hiện ở đường này nhưng mất ở đường nội bộ thì self-invocation là ứng viên nguyên nhân rất mạnh.
5. **Kiểm tra pointcut/advisor:** class, method, visibility, runtime matching, JDK/CGLIB; đừng thay đổi biểu thức khi vấn đề thật nằm ở đường đi.
6. **Refactor và thử lại:** đưa method cần advice qua Spring bean khác, gọi bằng dependency injection, rồi xác minh stdout/transaction/cache/async thực tế.
7. **Test hồi quy:** kiểm tra hành vi nghiệp vụ ngoài việc chỉ đếm log advice; đặc biệt với transaction propagation, rollback và dữ liệu.

## 13. Có nên dùng AspectJ weaving để xử lý self-invocation?

**Spring AOP proxy-based** và **AspectJ weaving** là hai cơ chế khác nhau:

| Spring AOP proxy-based | AspectJ compile-time/load-time weaving |
| --- | --- |
| Tạo proxy để chặn lời gọi đi từ client qua proxy | Thay đổi/đan bytecode của class theo cấu hình weaving |
| Self-invocation `this.inner()` bỏ qua proxy nên advice của `inner()` không chạy | Không chịu giới hạn self-invocation theo cách đó vì advice không phụ thuộc vào việc đi qua Spring proxy |
| Phù hợp cho đa số nhu cầu advice trên Spring-managed beans | Có thể xử lý join point/phạm vi rộng hơn nhưng cần công cụ và cấu hình khác |
| `@EnableAspectJAutoProxy` **không phải** lệnh bật AspectJ weaving | Cần AspectJ compiler hoặc weaver, cấu hình weaving tương ứng |

Spring Framework documentation xác nhận compile-time hoặc load-time AspectJ weaving **không gặp self-invocation issue như Spring AOP proxies**, vì hành vi advice nằm trong bytecode chứ không phụ thuộc một wrapper proxy.

Tuy nhiên, **chỉ thêm `aspectjweaver` hoặc `@Aspect` vào classpath không có nghĩa đã bật weaving**. Trong hầu hết trường hợp nên ưu tiên sửa Spring bean boundary (tách service) thay vì thay toàn bộ cơ chế AOP chỉ vì một lời gọi nội bộ.

Đặc biệt với `@Transactional`, chế độ `AdviceMode.ASPECTJ`/weaving có yêu cầu `spring-aspects` và thiết lập AspectJ khác; nó không tương đương `@EnableAspectJAutoProxy` và không được tự động kích hoạt chỉ nhờ Spring Boot starter AOP/AspectJ. Bài này không hướng dẫn cấu hình weaving đầy đủ vì trọng tâm là Spring proxy boundary.

## 14. Tình huống kiểm tra kiến thức

**Tình huống A.** `service.outer()` được gọi qua Spring AOP proxy. Trong `outer()`, code viết `inner()` thay vì `this.inner()`. Có chạy advice nhắm vào `inner()` không?

**Đáp án:** Không, nếu đó là lời gọi method cùng target: `inner()` viết ngắn vẫn là self-invocation trên `this`, không quay lại proxy.

**Tình huống B.** Đặt `@EnableAspectJAutoProxy(proxyTargetClass=true)` để chuyển JDK proxy sang CGLIB. Điều đó có làm `this.inner()` chạy advice không?

**Đáp án:** Không. CGLIB vẫn là Spring AOP proxy; self-invocation bên trong target tiếp tục bỏ qua proxy.

**Tình huống C.** `outer()` đã được advice và chạy bên trong transaction. Nó gọi `this.inner()`, trong khi `inner()` có `@Transactional(REQUIRES_NEW)`. Có tự tạo transaction mới không?

**Đáp án:** Không đối với lời gọi nội bộ ở proxy mode. `inner()` có thể chạy trong transaction hiện hữu của `outer()`, nhưng propagation `REQUIRES_NEW` của `inner()` không được transaction interceptor đánh giá cho invocation này.

**Tình huống D.** Chuyển `inner()` sang `InnerService` và inject `InnerService` vào `OuterService`. Lúc gọi `innerService.inner()` có chắc chắn advice chạy không?

**Đáp án:** Chỉ khi `InnerService` là Spring bean có proxy/advisor phù hợp, method có thể được advice và caller thực sự gọi qua tham chiếu proxy. Tách class **chưa đủ** nếu không có đăng ký bean/AOP đúng.

**Tình huống E.** `AopUtils.isAopProxy(service)` trả `true`, `this.inner()` vẫn không có log. Điều này có mâu thuẫn không?

**Đáp án:** Không. `isAopProxy` kiểm tra **tham chiếu service** là proxy; lời gọi `this.inner()` trong target không đi qua tham chiếu proxy ấy.

## 15. Nguồn tham khảo Spring chính thức

- [Spring Framework — Proxying Mechanisms (Spring 6.2)](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): giải thích trực tiếp self-invocation qua `this`, các lựa chọn refactor/self injection/`AopContext` và khác biệt với weaving.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): `@EnableAspectJAutoProxy`, AspectJ library trên classpath, auto-proxying Spring beans.
- [Spring Framework — Using @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html): transaction proxy mode, ranh giới external method calls và self-invocation.
- [Spring Framework — Understanding Transaction Proxy Infrastructure](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html): vai trò AOP proxies và transaction interceptors.
- [Spring Framework — AopContext Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/framework/AopContext.html): yêu cầu expose proxy và `currentProxy()`.
- [Spring Framework — AopUtils Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/AopUtils.html): nhận diện Spring AOP proxy và các tiện ích liên quan.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invocation-boundaries-003">Tái cấu trúc để chuyển lời gọi sang một bean cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

# Tái cấu trúc để chuyển lời gọi sang một bean cộng tác trong Spring AOP

## 1. Mục tiêu: tạo ranh giới lời gọi thực sự đi qua Spring proxy

Khi một service Spring gọi method khác **trên chính target instance** bằng `this.method()`, lời gọi đó không quay qua proxy mà Spring AOP tạo. Advice của `@Aspect` trên method được gọi nội bộ có thể **không chạy**, dù cùng method ấy được chặn khi một bean khác gọi tới qua proxy.

Một hướng sửa dễ hiểu và ít phụ thuộc hạ tầng là **tách phần công việc cần interception sang một Spring bean cộng tác**, rồi để service gốc **inject** bean đó và gọi method qua reference Spring-managed.

```text
TRƯỚC (không có boundary proxy cho method nội bộ)

Caller --> Spring proxy của OrderService --> target OrderService.place()
                                               |
                                               +--> this.validate() --X--> proxy
                                                  (không chạy advice dành cho validate)

SAU (boundary proxy của collaborator)

Caller --> Spring bean OrderApplicationService.place()
                         |
                         +--> injected ValidationService proxy
                                      |
                                      +--> advice cho ValidationService.validate()
                                      |
                                      +--> target ValidationService.validate()
```

**Điều kiện then chốt không phải chỉ là có hai class.** Bean cộng tác phải được Spring tạo/quản lý, caller phải giữ **tham chiếu proxy do Spring inject**, method phải được proxy kiểu tương ứng chặn được, pointcut phải match và aspect phải được đăng ký/bật trong cùng context thích hợp.

Bài chỉ tập trung vào **Spring AOP @AspectJ và lời gọi qua Spring bean/proxy**, vì đó là phạm vi của chủ đề Spring Aspect. Tách bean là kỹ thuật tổ chức code, nhưng động cơ và cách kiểm chứng ở đây là **sửa ranh giới interception của Spring**, không phải giới thiệu nguyên lý AOP tổng quát.

## 2. Chẩn đoán đúng self-invocation trước khi refactor

Ví dụ ban đầu:

```java
@Service
public class OrderService {
    public String place(String id) {
        return validate(id); // tương đương this.validate(id)
    }

    public String validate(String id) {
        return "validated:" + id;
    }
}
```

Giả sử Spring có aspect:

```java
@Aspect
@Component
public class ValidationAspect {
    @Before("execution(public * com.example.orders.OrderService.validate(..))")
    public void beforeValidation() {
        System.out.println("audit:validate");
    }
}
```

Hai đoạn là **minh họa**, chưa phải ứng dụng chạy độc lập vì cần đúng package, Spring context, `@EnableAspectJAutoProxy` hoặc Boot AOP auto-configuration và dependencies tương ứng.

Nếu `OrderService` đã được Spring proxy và pointcut match:

- Một bean khác gọi `orderService.validate("A")` qua injected proxy → **có thể** in `audit:validate`.
- Gọi `orderService.place("A")` qua proxy → target thực thi `place`, nhưng trong target lời gọi `this.validate("A")` đi **trực tiếp**, không trở về proxy; advice chỉ nhắm `validate` **không chạy** cho lời gọi ấy.
- `AopUtils.isAopProxy(orderService) == true` không chứng minh `validate` đã được chặn trên mọi đường gọi.
- Chuyển `validate` thành `public` hoặc bật `proxyTargetClass=true` không làm self-invocation quay qua proxy.
- Thêm `@Transactional`, `@Cacheable`, `@Async` hoặc annotation dùng proxy vào method nội bộ cũng không thay thế đường gọi qua proxy; các tính năng ấy còn có cơ chế activation riêng.

**Vì sao cần refactor thay vì thêm một annotation?** Trong mô hình Spring AOP proxy, interceptor áp dụng khi một caller gọi **qua reference proxy**. `this` bên trong target là reference của đối tượng đang thực thi, không phải vòng quay qua proxy Spring. Tài liệu Spring gọi đây là self-invocation bypassing advice.

## 3. Mẫu refactor: chuyển một trách nhiệm sang collaborator

Giữ API nghiệp vụ `place(..)` trên service điều phối, đưa method cần advice sang bean `ValidationService`:

```java
@Service
public class ValidationService {
    public String validate(String id) {
        return "validated:" + id;
    }
}
```

```java
@Service
public class OrderApplicationService {
    private final ValidationService validationService;

    public OrderApplicationService(ValidationService validationService) {
        this.validationService = validationService;
    }

    public String place(String id) {
        return validationService.validate(id);
    }
}
```

Hai đoạn minh họa giả sử chúng ở package được component scan bao phủ. Điểm thay đổi quan trọng:

1. `ValidationService` là **bean riêng**, có thể được Spring áp advisor để tạo proxy.
2. `OrderApplicationService` nhận dependency qua **constructor injection**; không tự gọi `new ValidationService()`.
3. `place(..)` gọi `validationService.validate(..)` trên reference đã inject; nếu reference là Spring AOP proxy, advice của `ValidationService.validate(..)` được đánh giá trước target.
4. API entry của application không phải đổi vì validation nay được ủy quyền sang collaborator.
5. Các rule về dữ liệu, exception và transaction boundary phải được xem lại theo trách nhiệm thực tế, không mặc nhiên giả định refactor thuần cấu trúc bảo toàn mọi semantics.

Spring không cần Spring bean riêng cho **mọi** private helper. Chỉ tách khi method có nhiệm vụ rõ ràng, là entry cần proxy advice, cần tái sử dụng/kiểm thử riêng hoặc tạo boundary đúng cho transaction, validation, audit, metric và security.

### 3.1. Đặt advice vào đúng lớp sau khi tách

Điểm cắt cũ nhắm method của `OrderService` phải được cập nhật nếu muốn advice đi theo phần trách nhiệm được chuyển:

```java
@Aspect
@Component
public class ValidationAspect {
    @Before(
        "execution(public * com.example.orders.ValidationService.validate(..))"
    )
    public void beforeValidation() {
        System.out.println("audit:validate");
    }
}
```

**Không coi thay đổi tên class là chi tiết tùy chọn.** Với pointcut dùng `execution(...)` trên fully-qualified class, sau refactor method đã chuyển sang type khác; nếu không đổi pointcut, advisor sẽ không match collaborator mới. Tương tự, nếu dùng pointcut `within(..)`, `bean(..)` hay annotation trên old service, cần xác nhận biểu thức có còn nhắm đúng bean/method hay không.

Có thể dùng **annotation đặc thù** trên collaborator method để giảm phụ thuộc tên class, nhưng annotation đó cũng phải được Spring AOP pointcut nhận diện ở đúng method thực hiện và vẫn cần caller đi qua proxy. Không nên quét pointcut quá rộng để “bắt bằng được” vì có thể chặn các bean ngoài ý muốn.

## 4. Demo chạy được: trước–sau trong cùng một Spring ApplicationContext

Demo dùng **Java 17+**, **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**, Gradle và Java configuration thuần. Một context chứa cả legacy service lẫn collaborator version để so sánh bằng cùng aspect. Không dùng Spring Boot để tự động bật proxy ngầm: chính `@EnableAspectJAutoProxy` thể hiện điều kiện Spring-specific.

### 4.1. Cấu trúc dự án

```text
spring-aop-collaborator-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/boundary/
    ├── Main.java
    ├── AppConfig.java
    ├── LegacyOrderService.java
    ├── ValidationService.java
    ├── OrderApplicationService.java
    └── TraceAspect.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-aop-collaborator-demo'
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
    mainClass = 'com.example.boundary.Main'
}
```

Spring Framework 6.x chạy trên Java 17+. Trong project Spring Boot thực tế nên để BOM/dependency management chọn versions tương thích, thay vì sao chép các phiên bản demo vào project đang dùng Boot major khác.

### 4.2. Legacy service — lời gọi nội bộ

**`LegacyOrderService.java`**

```java
package com.example.boundary;

public class LegacyOrderService {
    public String place(String id) {
        return validate(id); // self-invocation: bypass advice trên validate
    }

    public String validate(String id) {
        return "validated:" + id;
    }
}
```

Trong class này, `place` và `validate` đều public để thử nghiệm Spring proxy được phép can thiệp theo method signature. **Nguyên nhân thiếu advice không phải do visibility mà do call path.**

### 4.3. Bean cộng tác — method cần advice có entry riêng

**`ValidationService.java`**

```java
package com.example.boundary;

public class ValidationService {
    public String validate(String id) {
        return "validated:" + id;
    }
}
```

### 4.4. Service điều phối — inject collaborator thay vì self-call

**`OrderApplicationService.java`**

```java
package com.example.boundary;

public class OrderApplicationService {
    private final ValidationService validationService;

    public OrderApplicationService(ValidationService validationService) {
        this.validationService = validationService;
    }

    public String place(String id) {
        return validationService.validate(id);
    }
}
```

`OrderApplicationService` không có method `validate`; mọi đường vào `place` dùng duy nhất dependency Spring inject vào constructor.

### 4.5. Aspect đếm điểm cắt thật sự được intercept

**`TraceAspect.java`**

```java
package com.example.boundary;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before(
        "execution(public * com.example.boundary.LegacyOrderService.validate(..))"
        + " || execution(public * com.example.boundary.ValidationService.validate(..))"
    )
    public void beforeValidation(JoinPoint point) {
        System.out.println(
            "advice:" + point.getSignature().getDeclaringType().getSimpleName()
            + "." + point.getSignature().getName()
        );
    }
}
```

Biểu thức pointcut dùng **phép OR** để đối chứng cùng một advice với hai method cụ thể. `getSignature().getDeclaringType().getSimpleName()` giúp nhận diện method chạy advice ở class nào. Không có advice trên `place(..)`, vì mục tiêu test là **method validate(..)` ở hai call paths**.

### 4.6. Spring config — xác định bean và proxy

**`AppConfig.java`**

```java
package com.example.boundary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    TraceAspect traceAspect() {
        return new TraceAspect();
    }

    @Bean
    LegacyOrderService legacyOrderService() {
        return new LegacyOrderService();
    }

    @Bean
    ValidationService validationService() {
        return new ValidationService();
    }

    @Bean
    OrderApplicationService orderApplicationService(
            ValidationService validationService) {
        return new OrderApplicationService(validationService);
    }
}
```

Chính factory method nhận `ValidationService` khiến Spring inject bean đã được xử lý bởi container. Target method `validate` khớp aspect nên `ValidationService` thường được Spring wrap bởi class-based proxy. Không có `new ValidationService()` trong `OrderApplicationService`.

`TraceAspect` là `@Aspect` **Spring-managed bean** do `@Bean` tạo. `@EnableAspectJAutoProxy` cho phép Spring xem advice và auto-proxy các target phù hợp. Cấu hình `proxyTargetClass=true` giúp các target không có interface vẫn được truy xuất theo concrete class trong ví dụ.

### 4.7. Main — quan sát hai đường gọi

**`Main.java`**

```java
package com.example.boundary;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            LegacyOrderService legacy =
                    context.getBean(LegacyOrderService.class);
            ValidationService validation =
                    context.getBean(ValidationService.class);
            OrderApplicationService refactored =
                    context.getBean(OrderApplicationService.class);

            System.out.println("legacyProxy=" + AopUtils.isAopProxy(legacy));
            System.out.println(
                    "collaboratorProxy=" + AopUtils.isAopProxy(validation)
            );

            System.out.println("legacyInternal=" + legacy.place("A"));
            System.out.println("legacyExternal=" + legacy.validate("B"));
            System.out.println("afterRefactor=" + refactored.place("C"));
        }
    }
}
```

Tại thư mục project, chạy:

```bash
gradle run --quiet
```

Nếu đã có Gradle Wrapper: `./gradlew run --quiet` trên Linux/macOS hoặc `gradlew.bat run --quiet` trên Windows.

**Các dòng do `System.out` của demo in ra, theo đúng thứ tự:**

```text
legacyProxy=true
collaboratorProxy=true
legacyInternal=validated:A
advice:LegacyOrderService.validate
legacyExternal=validated:B
advice:ValidationService.validate
afterRefactor=validated:C
```

Đây là output của code ứng dụng, không tính warning/log ngoài ý muốn do JVM/build tool hoặc logging configuration.

### 4.8. Vì sao stdout thể hiện đúng vấn đề và đúng cách sửa?

| Lời gọi | Spring proxy entry thực tế | Kết quả advice trên `validate(..)` |
| --- | --- | --- |
| `legacy.place("A")` | Caller đi qua proxy `LegacyOrderService` cho method `place`, sau đó target dùng `this.validate` | **Không có** `advice:LegacyOrderService.validate` cho case này |
| `legacy.validate("B")` | Caller trực tiếp vào proxy `LegacyOrderService` trên method `validate` | Có `advice:LegacyOrderService.validate` |
| `refactored.place("C")` | Caller vào `OrderApplicationService` (không nhất thiết proxy); service gọi **injected proxy** `ValidationService.validate` | Có `advice:ValidationService.validate` |

Cả hai phép thử `legacyProxy=true` và `collaboratorProxy=true` đều chứng minh **proxy có tồn tại**, nhưng chúng **không thay thế** phép kiểm tra advice trên call path cụ thể. Chính việc advice chỉ xuất hiện ở lời gọi ngoài (legacy) và collaborator (refactored) là bằng chứng cho sự khác biệt.

Method `OrderApplicationService.place` trong demo **không cần AOP proxy** vì không có advisor match vào nó. Điều quan trọng là nó nắm được **dependency reference** tới Spring proxy của `ValidationService`. Đó là lý do “tách bean cộng tác” có hiệu quả mà không cần self-injection hay `AopContext.currentProxy()`.

## 5. Vì sao việc chuyển sang một class Java thường chưa đủ?

Thí dụ **vẫn sai** nếu tự tạo cộng tác viên:

```java
public class OrderApplicationService {
    private final ValidationService validationService =
            new ValidationService(); // không do Spring quản lý

    public String place(String id) {
        return validationService.validate(id);
    }
}
```

Code có hai class thật, nhưng reference không phải Spring-managed proxy. `@Aspect` Spring không tự intercept object tạo bằng `new`; chuyển method sang file mới mà vẫn tự tạo instance chỉ thay đổi cấu trúc, **không tạo ranh giới AOP**.

**Cách đúng** là inject constructor từ container:

```java
public class OrderApplicationService {
    private final ValidationService validationService;

    public OrderApplicationService(ValidationService validationService) {
        this.validationService = validationService;
    }

    public String place(String id) {
        return validationService.validate(id);
    }
}
```

Đăng ký `ValidationService` bằng `@Component`/`@Service` kèm component scan đúng package, hoặc bằng `@Bean`. Caller cũng nên nhận `OrderApplicationService` từ context thay vì `new` một object tự lắp dependency, nhằm nhất quán với lifecycle và cấu hình của ứng dụng.

### 5.1. Hai cách đăng ký collaborator hợp lệ

**Component scan:**

```java
@Service
public class ValidationService {
    public String validate(String id) {
        return "validated:" + id;
    }
}
```

```java
@Service
public class OrderApplicationService {
    private final ValidationService validationService;

    public OrderApplicationService(ValidationService validationService) {
        this.validationService = validationService;
    }

    public String place(String id) {
        return validationService.validate(id);
    }
}
```

Với component scan, `@Service` tạo bean candidates. Nếu aspect class dùng `@Aspect`, cần thêm `@Component` hoặc `@Bean` để chính nó cũng là Spring bean.

**Đăng ký bằng `@Bean`:** như `AppConfig` trong demo hoàn chỉnh, class không cần `@Service` và bean được wiring qua tham số factory method. Chọn một phương thức đăng ký rõ ràng, tránh đồng thời component-scan và `@Bean` tạo hai instance cùng loại gây injection ambiguity hoặc advisor side effect khác dự kiến.

### 5.2. Scope và context phải phù hợp

Bean collaborator cần tồn tại trong context thích hợp. Với ứng dụng có root/child `ApplicationContext`, proxy infrastructure và advisor có phạm vi của context; không tự suy bật AOP ở context này thì mọi target trong context khác đều được proxy.

Một collaborator singleton có thể dùng chung cho nhiều callers; không cần tạo bản sao cho từng caller chỉ để advice chạy. Nếu collaborator chứa mutable request state, việc trở thành singleton bean tạo **rủi ro concurrency riêng**, cần thiết kế stateless hoặc truyền trạng thái qua tham số. Không cất user/order hiện tại vào field của aspect/collaborator singleton.

## 6. Giữ nguyên semantics khi đổi ranh giới bean

Kỹ thuật refactor chỉ an toàn khi xem xét **contract nghiệp vụ** trước và sau, chứ không đơn thuần chuyển code dòng nào sang class nào.

### 6.1. Input, output và exception

- Giữ chữ ký nghiệp vụ mà caller đang dựa vào hoặc có kế hoạch thay đổi API rõ ràng.
- Không vô tình swallow/đổi exception chỉ vì thêm wrapper bean.
- Nếu advice là `@Around` có `proceed()`, giữ đúng số lần gọi target và đúng semantics truyền args/return/exception. Refactor làm advice xuất hiện thêm có thể khiến logic retry, timeout hoặc logging chạy **thực sự** lần đầu; cần review side effects.
- Nếu helper trước đây dùng field private của lớp cũ, **đừng tự động expose mutable field**, mà truyền dữ liệu cần thiết vào collaborator qua method arguments hoặc một dependency rõ ràng.

### 6.2. Transaction boundary có thể đổi

Spring `@Transactional` là một ví dụ điển hình của proxy interception. Cần phân biệt:

```java
@Service
public class ImportService {
    public void runBatch() {
        savePart(); // self-call: transaction advice của savePart không chạy
    }

    @Transactional
    public void savePart() {
        // cập nhật dữ liệu
    }
}
```

Nếu chuyển `savePart()` sang `ImportWriter` Spring bean và inject vào `ImportService`, lời gọi từ `runBatch()` qua injected proxy có thể kích hoạt transaction interceptor **khi transaction management được bật** và có `PlatformTransactionManager` phù hợp.

Điều đó làm **thay đổi semantics thật**, không chỉ khắc phục log advice:

- Với `Propagation.REQUIRED`, nếu outer có transaction thì inner thường tham gia transaction đang có; nếu không, transaction interceptor có thể bắt đầu transaction mới.
- Với `REQUIRES_NEW`, việc qua proxy có thể tạo transaction riêng với khả năng commit/rollback độc lập; trước refactor self-call có thể đã bỏ qua yêu cầu này.
- `@Transactional` trên method của collaborator chỉ hoạt động nếu activation/configuration Spring transaction đầy đủ, method intercept được và caller đi qua proxy. `@EnableAspectJAutoProxy` đơn lẻ **không kích hoạt** `@Transactional`.
- Exception translation/checked exceptions/rollback rules phụ thuộc cấu hình transaction; không suy mọi exception đều rollback.

Đây là **đoạn minh họa** không chứa DB configuration và không phải project runnable. Nếu thay đổi transaction boundary, phải viết integration test với database/test container phù hợp để kiểm chứng rollback/commit theo yêu cầu, thay vì chỉ đếm advice in console.

### 6.3. Security, caching và async cũng có boundary

| Spring capability | Nếu self-call ban đầu bypass proxy | Sau khi chuyển sang injected collaborator |
| --- | --- | --- |
| Spring `@Aspect` audit/metrics | Advice không chạy ở method nội bộ | Advisor có thể chạy trên entry method của collaborator nếu pointcut match |
| `@Transactional` | Inner transaction interceptor có thể bị bỏ qua | Có thể tạo/tham gia transaction theo propagation thực tế |
| `@Cacheable`/`@CacheEvict` | Cache advice của inner method bị bypass | Cache advice có thể hoạt động, thay đổi cache hit/miss/eviction |
| `@Async` | Inner method có thể chạy cùng caller thread thay vì qua executor | Method được gọi qua proxy có thể được submit sang executor; chú ý exception handling/context propagation |
| Method security như `@PreAuthorize` | Inner authorization advice bị bypass | Có thể được thực thi khi method-level security được bật và policy match |

Các tính năng ở bảng **có enable/configuration riêng**, không bật đồng loạt bởi một `@Aspect` annotation. Khi boundary mới làm interception thật sự chạy, hãy kiểm tra thay đổi về transaction, thread, latency, retry, audit logs và bảo mật. Đặc biệt không coi việc “trước đây pass test” là đủ nếu những test đó vô tình bỏ qua policy.

### 6.4. Nếu pointcut dùng bean name hoặc type

Khi chuyển code từ bean `orderService` sang `validationService`:

- `bean(orderService)` không tự match bean mới; cần cập nhật bean name expression nếu muốn advice theo collaborator.
- `within(com.example.orders.OrderService)` hoặc `execution(* OrderService.*(..))` có thể không còn match.
- `@annotation(...)` match annotation trên method thực tế: nếu annotation chuyển sang collaborator method, hãy xác minh type annotation, visibility và interface proxy tương ứng.
- `this(...)` nhắc tới proxy type; `target(...)` nhắc tới target type; JDK proxy và class proxy có thể làm kết quả type matching khác nhau.
- Nếu chặn một interface contract, public method phải thuộc interface được proxy khi dùng JDK dynamic proxy.

Dùng pointcut có scope chặt; sau refactor tránh làm biểu thức quá rộng chỉ để không phải sửa tên lớp.

## 7. Thiết kế ranh giới collaborator tốt

Một cách phân chia hợp lý là:

```text
OrderApplicationService (orchestrator)
  - gọi các bước nghiệp vụ và giữ API use case
  - phối hợp kết quả/exception
  - chỉ chứa logic orchestration cần thiết
          |
          +--> ValidationService (Spring-managed collaborator)
                 - một trách nhiệm dễ gọi qua proxy
                 - public validation operation
                 - pointcut/advisor đúng phạm vi
                 - dependency rõ ràng
```

Các tiêu chí:

1. **Cohesion:** collaborator nên đại diện một trách nhiệm/use case phụ có tên thật (validate, charge, persist, applyRule), không phải lớp `AopWorkaroundService` vô nghĩa.
2. **Constructor injection:** dependency explicit, dễ thay bằng test double và không tạo vòng phụ thuộc ẩn.
3. **Public entry contract:** trong thiết kế phổ biến, method cần interception là public và non-final để tương thích chắc chắn với class proxy. Với JDK proxy, method cần nằm trên interface.
4. **Không cyclic dependencies:** A → B rõ ràng; nếu B lại cần A, cân nhắc tách thêm trách nhiệm chứ đừng dùng circular proxy hoặc self-injection.
5. **Không chuyển toàn bộ private helpers chỉ vì AOP:** helper không cần advice có thể giữ private và được gọi trực tiếp.
6. **Scope phù hợp:** thường là stateless singleton; không lưu request-sensitive mutable data trong collaborator singleton.
7. **Không bỏ sót caller cũ:** cập nhật tất cả call paths cần policy, không chỉ đường gọi mới trong demo.
8. **Giữ xử lý lỗi:** bảo toàn contract ném exception/return value, trừ khi thay đổi đã được quyết định và test.

### Khi service gốc vẫn cần chính advice trên method của nó

Không nhất thiết mọi advice phải chuyển sang collaborator. Nếu concern áp dụng vào **toàn bộ operation `place()`**, thay vì riêng `validate()`, có thể cân nhắc gắn pointcut vào entry public method `place()` vốn đã được caller bên ngoài gọi qua proxy. Đó có thể là boundary tự nhiên hơn khi policy cần bao phủ cả quy trình.

Nhưng nếu yêu cầu là **chỉ validate step** phải có transaction/cache/audit riêng, đưa step đó sang collaborator là cách bảo đảm một entry proxy rõ ràng. Quyết định nên theo semantics mong muốn, không theo một thủ thuật proxy mặc định.

## 8. Những cách né self-invocation và vì sao collaborator thường tốt hơn

### 8.1. Self-injection

Một số code cố tiêm chính proxy vào bean:

```java
@Service
public class RiskyService {
    @Autowired
    private RiskyService self;

    public void outer() {
        self.inner();
    }

    public void inner() {
        // mong advice chạy
    }
}
```

Đây là **mã minh họa**, không thuộc demo runnable. Self-injection có thể kéo theo circular dependency, khó hiểu thời điểm wiring, phụ thuộc config Boot/Spring và không giải quyết phân chia trách nhiệm. Dù có thể dùng proxy khi bean đã init, đây **không phải lựa chọn khuyến khích** cho thiết kế lâu dài.

### 8.2. `AopContext.currentProxy()`

```java
((RiskyService) AopContext.currentProxy()).inner();
```

Đây cũng chỉ là đoạn minh họa: cần `@EnableAspectJAutoProxy(exposeProxy=true)`, đang ở trong lời gọi qua proxy, kiểu cast phù hợp (có thể là interface với JDK proxy), và import `org.springframework.aop.framework.AopContext`. Nó làm code nghiệp vụ phụ thuộc API AOP của Spring, không thể gọi tùy ý trong constructor/`@PostConstruct` và không tự lan qua thread async.

**Khác biệt:** collaborator giữ code nghiệp vụ gần như không cần biết AOP đang được bật; chỉ biết một dependency interface/class được inject. Đây cũng là hướng refactor được tài liệu Spring khuyến nghị trước những phương án tự dùng proxy.

### 8.3. ApplicationContext.getBean chính mình

```java
MyService proxy = applicationContext.getBean(MyService.class);
proxy.inner();
```

Nếu class phải biết `ApplicationContext` để gọi lại chính nó, thiết kế đang tăng coupling vào container và có thể tạo lỗi init/circular dependency. Trong code startup, proxy còn có thể chưa hoàn tất. Trừ trường hợp infrastructure thực sự cần service locator, ưu tiên constructor injection collaborator.

### 8.4. AspectJ weaving

AspectJ compile-time/load-time weaving có cơ chế khác và có thể xử lý lời gọi không đi qua Spring proxy trong một số trường hợp. Tuy nhiên, `@EnableAspectJAutoProxy` **không bật weaving**. Nếu yêu cầu phù hợp với method call qua proxy, tái cấu trúc collaborator thường đơn giản hơn triển khai agent/compiler weaving. Chỉ đánh giá weaving riêng khi thật sự cần chặn hành vi ngoài khả năng proxy như constructor/field access hoặc internal call mà không thể thay cấu trúc.

## 9. Những lỗi Spring thường gặp sau refactor

| Hiện tượng | Nguyên nhân có thể | Cách kiểm tra và xử lý |
| --- | --- | --- |
| Tách class xong advice vẫn không chạy | Collaborator được tạo bằng `new`, không là Spring bean | Đăng ký `@Component`/`@Service` + scan, hoặc `@Bean`; inject từ context |
| Có Spring bean nhưng advice vẫn không chạy | Aspect không là bean, thiếu `@EnableAspectJAutoProxy`/Boot AOP auto-config hoặc pointcut cũ | Liệt kê aspect beans, kiểm tra config, match method/target mới |
| `AopUtils.isAopProxy(collaborator)==false` | Không có advisor match, target không đủ điều kiện proxy, context sai | Kiểm tra pointcut và auto-proxy infrastructure |
| Proxy true nhưng không thấy advice | Caller vẫn tự gọi helper trên target hoặc advice nhắm method khác | Trace call path, chạy test gọi thẳng collaborator qua Spring reference |
| Class final hoặc method final | Class-based Spring proxy không subclass/override được | Refactor entry method hoặc dùng JDK interface proxy nếu thích hợp |
| Inject bằng concrete implementation bị lỗi khi dùng JDK proxy | Proxy chỉ implement interface | Inject theo interface, hoặc cân nhắc class proxy nếu class đủ điều kiện |
| Spring báo circular dependency | Bean A inject B và B lại inject A, hoặc tự inject | Phá vòng, tách trách nhiệm, giữ graph phụ thuộc một chiều |
| Advice chạy hai lần | Pointcut bắt cả outer và inner, nhiều aspect/advisors/bean registrations cùng áp | Kiểm tra log join point, số bean aspect, advisor order và scope |
| Transaction/caching/async hành vi khác trước | Refactor làm method vốn không được proxy nay được intercept | Kiểm thử semantics mong muốn, propagation/cache keys/thread context |
| Logging bị mất sau đổi class | Pointcut phụ thuộc tên type/bean/annotation cũ | Cập nhật và test expression theo collaborator |
| Lời gọi từ `@PostConstruct` vẫn không có advice | Callback trong target initialization chưa đi qua proxy | Chọn startup hook phù hợp và gọi collaborator qua injected proxy sau khởi tạo |
| Test tạo object bằng `new` nhưng kỳ vọng AOP | Test bypass Spring container | Viết integration test có ApplicationContext thực |

### 9.1. Cách kiểm tra chính collaborator có thực sự là proxy

```java
ValidationService bean = context.getBean(ValidationService.class);
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
```

Trong demo, hai kết quả là `true`. Đây là **đoạn minh họa** cần context và import như `Main.java`. Dùng thêm một recorder cho advice để kiểm tra lời gọi `bean.validate(..)` sinh event. **Proxy true không đồng nghĩa advice sẽ chạy trên bất cứ method nào.**

Với interface proxy, dùng `AopUtils.isJdkDynamicProxy(bean)` và tra cứu theo interface. Tránh cast sang concrete class khi runtime bean là JDK proxy.

## 10. Kiểm thử before/after để bảo đảm không đổi nghiệp vụ ngoài ý muốn

Chỉ nhìn stdout không đủ cho production. Một bộ test hợp lý gồm:

1. **Baseline nghiệp vụ:** cùng dữ liệu hợp lệ/không hợp lệ, cả legacy và refactored trả output/exception theo contract dự kiến.
2. **Bean wiring:** context có `ValidationService` và `OrderApplicationService` đúng một bean có chủ đích; dependency là bean Spring.
3. **Target proxy:** `AopUtils.isAopProxy(validationService)` true khi advisor cần thiết được đăng ký.
4. **Interception:** recorder test khẳng định `refactored.place(..)` kích hoạt đúng một event từ collaborator, trong khi legacy self-invocation không có event `validate`.
5. **Pointcut chính xác:** method khác trên collaborator không được advice nếu ngoài phạm vi; tránh advisor match ngoài ý muốn.
6. **Exception path:** lỗi từ collaborator được propagate/translate theo contract, không bị advice nuốt.
7. **Transaction/security/caching:** nếu có, test các hiệu ứng nghiệp vụ thật (rollback/deny/cache hit), không chỉ assert console log.
8. **Concurrent calls:** collaborator/aspect singleton không lưu trạng thái mutable theo request; kiểm tra thread-safety nếu dependency dùng chung.
9. **Context startup:** không tạo circular dependency hoặc gọi sớm trước proxy hoàn thành.
10. **Regression:** những caller cũ vẫn đi qua đúng public API; không có đường gọi bỏ qua collaborator để vi phạm policy.

### 10.1. Integration test bằng Spring context, không dựa vào `new`

Để test thật sự AOP, dùng `AnnotationConfigApplicationContext` như demo hoặc, nếu có Spring Test, `@SpringJUnitConfig` với cấu hình phù hợp. `new ValidationService()` test business method nhưng **không** chứng minh advice vì không có auto-proxy infrastructure.

Một test sink/recorder có thể thay lời `System.out`:

```java
public final class EventRecorder {
    private final java.util.List<String> events =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public void record(String value) {
        events.add(value);
    }

    public java.util.List<String> events() {
        return java.util.List.copyOf(events);
    }
}
```

Đây là **đoạn Java minh họa** không ghép tự động vào demo; muốn chạy test hãy thêm bean `EventRecorder` và inject vào `TraceAspect`, sau đó assert số event và thứ tự trong luồng đồng bộ. Nếu lời gọi chạy qua `@Async`/nhiều thread, thứ tự event giữa các thread không cố định và cần đồng bộ/đợi hoàn thành trước khi assert.

### 10.2. Tránh kiểm thử chống lại cấu hình production

Đôi lúc unit test dùng một mock `ValidationService` trực tiếp trong constructor `OrderApplicationService`. Điều này tốt để kiểm tra orchestration, nhưng mock **không phản ánh Spring proxy**. Phải kết hợp test unit và integration test để chứng minh cả logic nghiệp vụ lẫn behavior của advisor.

Một bài kiểm thử thực tế nên fail nếu ai đó “tối ưu” bằng cách thay injected collaborator bằng `new` hoặc đưa method về self-call mà không biết rằng AOP sẽ bị bỏ qua.

## 11. Áp dụng trong Spring Boot

Với Spring Boot có AOP auto-configuration và dependency phù hợp, thường không cần tự ghi `@EnableAspectJAutoProxy`, nhưng cách refactor không đổi:

```java
@Service
public class OrderApplicationService {
    private final ValidationService validator;

    public OrderApplicationService(ValidationService validator) {
        this.validator = validator;
    }

    public String place(String id) {
        return validator.validate(id);
    }
}
```

```java
@Service
public class ValidationService {
    public String validate(String id) {
        return "validated:" + id;
    }
}
```

Với `@Aspect` class tương ứng có `@Component`, Spring Boot cần dependency AOP tương ứng để kích hoạt proxy. Ví dụ Gradle (chọn theo major version, dùng Boot dependency management):

```gradle
// Spring Boot 3.x
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

```gradle
// Spring Boot 4.x
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aspectj'
}
```

Hai khối **là hai lựa chọn riêng**, không chép đồng thời. Việc có Spring AOP starter không tự tạo aspect bean: `@Aspect` phải được component scan tìm thấy qua `@Component` hoặc được đăng ký bằng `@Bean`. Đặt các classes dưới package scan của `@SpringBootApplication` hoặc import cấu hình phù hợp.

Spring Boot thường chọn class proxy theo `spring.aop.proxy-target-class=true`; nếu dùng interface và muốn JDK proxy, xét `spring.aop.proxy-target-class=false`. Kết quả còn có thể chịu tác động từ cấu hình proxy khác. Hãy kiểm tra proxy thực tế, nhất là khi refactor sang final class, Kotlin class mặc định final hoặc chỉ inject qua interface.

## 12. Quy trình refactor có thể áp dụng cho một codebase thật

### Bước 1 — Xác định đường gọi mất advice

Vẽ đường gọi **từ caller đến target** và đánh dấu reference thuộc Spring bean/proxy hay instance tự tạo. Lấy một case tái hiện được: gọi qua external Spring bean thì advice xuất hiện, gọi nội bộ thì không.

### Bước 2 — Xác định method và concern cần interception

Không mặc định tách mọi helper. Chọn phần công việc cần một Spring proxy boundary **thật sự**: audit của bước validation, transaction độc lập cho write, cache cho lookup, security policy ở public operation.

### Bước 3 — Tách method với contract rõ ràng

Tạo một collaborator có tên nghiệp vụ và public method có thể proxy. Chuyển input cần thiết sang tham số; giữ output/exception contract, tránh chia sẻ mutable state tùy tiện.

### Bước 4 — Đăng ký collaborator vào Spring

Dùng `@Service`/`@Component` trong package scan hoặc `@Bean` trong Spring configuration được nạp. Kiểm tra context có đúng instance/scope mong muốn.

### Bước 5 — Inject qua constructor

Sửa orchestrator để nhận dependency từ Spring, gọi collaborator reference, không tạo mới bằng `new`; tránh ngược chiều phụ thuộc sinh cycle.

### Bước 6 — Cập nhật pointcut và cấu hình AOP

Đổi pointcut nhắm method/type/bean/annotation đúng vị trí mới; aspect phải là Spring bean và Spring AOP support đã bật trong context thích hợp.

### Bước 7 — Chạy test hành vi và advice

Tái hiện trước–sau, dùng `AopUtils` và recorder. Chứng minh advice chạy qua collaborator, không chạy thừa ở phương thức không liên quan; kiểm tra transaction/security/cache/async nếu đang sử dụng.

### Bước 8 — Rà soát hiệu năng và release

Refactor có thể thêm một proxy hop nhưng thường không đáng kể so với IO; tuy vậy hãy đo nếu hot path rất nhạy. Quan trọng hơn, **interception trước đây bị bỏ qua nay thực sự chạy**, nên chi phí logging, transaction, cache/remote calls hoặc retry có thể thay đổi. Triển khai với regression tests và observability để phát hiện các side effect mới.

## 13. Checklist cuối cùng

- [ ] Đã chứng minh tình trạng cũ là **self-invocation** (`this.method()` trên target), không phải thiếu aspect bean/pointcut/enable config?
- [ ] Method cần advice đã chuyển sang một **collaborator Spring bean** với trách nhiệm rõ ràng?
- [ ] Collaborator do Spring tạo (`@Component`/`@Service` + scan hoặc `@Bean`); orchestrator không dùng `new`?
- [ ] Reference collaborator được **inject qua constructor**, không self-injection hay `ApplicationContext.getBean(self)`?
- [ ] Method entry public/non-final hoặc có interface tương thích; kiểu proxy JDK/CGLIB thích hợp?
- [ ] Aspect là Spring bean; `@EnableAspectJAutoProxy`/Boot auto-config được bật đúng context?
- [ ] Pointcut khớp type/method/bean mới; không chặn ngoài phạm vi?
- [ ] Integration test cho thấy advice chạy **một lần đúng chỗ** trên đường gọi refactored?
- [ ] Business output/exception tương thích trước–sau; không thay đổi transaction/cache/security semantics ngoài kế hoạch?
- [ ] Không đưa mutable request state vào collaborator/aspect singleton và không phát sinh circular dependency?
- [ ] Nếu luồng xảy ra trong startup, proxy đã hoàn tất, không dựa vào lời gọi từ constructor hoặc `@PostConstruct` của chính target?
- [ ] Đã kiểm tra mọi entry cũ để không còn đường vòng bypass policy?

**Ghi nhớ:** Cách sửa tự nhiên nhất cho self-invocation trong **Spring AOP proxy** thường là **tách phần công việc cần advice thành một Spring bean cộng tác rồi gọi qua dependency được Spring inject**. Tạo class riêng là chưa đủ; phải tạo được **proxy boundary thực**. Refactor nên bảo toàn contract nghiệp vụ, cập nhật pointcut và kiểm thử các side effects mới khi advice bắt đầu hoạt động.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Proxying Mechanisms, self-invocation và hướng refactor](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/at-aspectj.html)
- [Spring Framework 6.2 — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html)
- [Spring Framework — Annotation-based Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invocation-boundaries-004">Tự tiêm tham chiếu proxy và các phụ thuộc phát sinh</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: tự tiêm tham chiếu proxy và các phụ thuộc phát sinh

## 1. Vì sao có khái niệm self-injection?

Spring AOP là **proxy-based AOP**. Spring tạo proxy cho những Spring-managed beans cần được advisor/advice áp dụng. Khi bean khác gọi một method **qua proxy**, advice có cơ hội chạy. Nhưng khi chính target gọi method của nó qua `this`, cuộc gọi này không đi qua proxy — gọi là *self-invocation*.

```java
@Service
public class ReportService {
    public void generate() {
        this.audit(); // Gọi trên target, không qua Spring AOP proxy.
    }

    public void audit() {
        System.out.println("audit");
    }
}
```

Đoạn này minh họa; chưa phải ứng dụng chạy độc lập. Giả sử Spring có aspect `@Before` cho `audit()`: nếu một consumer gọi `reportService.audit()` qua reference proxy Spring inject, advice có thể chạy. Nhưng `generate()` gọi `this.audit()` trực tiếp nên advice cho `audit()` không chạy. Điều này đúng với cả JDK dynamic proxy lẫn CGLIB trong Spring AOP.

**Self-injection** là kỹ thuật để bean nhận lại reference đến **chính Spring bean** của nó (hy vọng là AOP proxy), rồi chủ động gọi method qua reference đó thay cho `this`:

```java
private ReportOperations self;

public void generate() {
    self.audit(); // Chỉ chạy advice nếu self cuối cùng gọi đúng AOP proxy.
}
```

Đoạn này chỉ minh họa ý tưởng, không có cơ chế inject. Giải pháp này có thể vượt qua self-invocation cho **một lời gọi cụ thể**, nhưng kéo theo quan hệ phụ thuộc vào chính bean đang được khởi tạo. Vì vậy, tài liệu Spring xem self-injection là biện pháp dự phòng; giải pháp thiết kế được ưu tiên là **tách operation cần advice sang một Spring bean khác** rồi gọi qua dependency do Spring inject.

Bài học này chỉ tập trung vào Spring AOP, Spring IoC, proxy, bean lifecycle và các phụ thuộc phát sinh; không bàn luận AOP tổng quát hoặc AspectJ weaving ngoài phạm vi Spring AOP proxy.

## 2. Ba reference dễ bị nhầm

Xét một Spring bean `ReportService implements ReportOperations` có method `generate()` và `audit()`:

1. **Target object:** instance thực sự chứa state và code `ReportService`. Trong target, `this` trỏ tới target.
2. **Spring AOP proxy:** reference mà `ApplicationContext.getBean(ReportOperations.class)` hoặc constructor injection của consumer có thể nhận, khi có advisor phù hợp.
3. **Lazy-resolution proxy:** reference trung gian Spring cấp cho một injection point gắn `@Lazy`; khi caller sử dụng, nó mới tìm dependency thật từ container. Dependency cuối cùng có thể là **Spring AOP proxy**.

```text
Consumer
   |
   v
Spring AOP proxy (ReportOperations)
   |
   v
Target ReportService.generate()
   |
   +-- this.audit()
   |       |
   |       v
   |    Target.audit()          -> KHÔNG đi qua advice
   |
   +-- self.audit()
           |
           v
       @Lazy proxy nếu có
           |
           v
       Resolve dependency trong Spring
           |
           v
       Spring AOP proxy
           |
           +-- advice cho audit(), nếu pointcut khớp
           |
           v
       Target.audit()
```

Điểm mấu chốt: **`@Lazy` injection proxy không đồng nghĩa với AOP proxy**. Chỉ thấy object `self` có dạng proxy không đủ để kết luận có advisor hoặc lời gọi đã chạy advice. Cần kiểm tra bean Spring thực sự được resolve và kết quả method invocation.

Hai kiểu proxy cũng không tự động thay thế lệnh `this.audit()`. Chỉ khi code **đổi sang gọi `self.audit()` trên reference đúng** thì mới có cơ hội đi qua Spring AOP proxy.

## 3. Quy tắc self-injection trong Spring DI

### 3.1. `@Autowired` self reference có thứ tự ưu tiên thấp nhất

Spring Framework cho phép `@Autowired` xét lại chính bean đang được cấu hình như một ứng viên cho dependency. Tuy nhiên, self-injection là một **fallback mechanism**:

- Các dependency/bean ứng viên bình thường được xét **trước** self reference.
- Self reference có **độ ưu tiên thấp nhất**, không phải ứng viên autowire thông thường và không được ưu tiên bằng `@Primary`.
- Với nhiều bean cùng interface, `@Autowired ReportOperations self` có thể không chọn bean hiện tại nếu ứng viên bình thường khác phù hợp.
- Spring cũng có khả năng resolve theo tên trong các tình huống phù hợp; nhưng một field mang tên `self` không nên bị coi là ràng buộc chắc chắn đến bean hiện tại.

Hệ quả: viết một field tên `self` chưa chứng minh đã lấy đúng proxy của chính service. Khi correctness phụ thuộc điều này, cần kiểm tra runtime, xác định bean name/qualifier và viết integration test. Trong thiết kế lâu dài, phụ thuộc vào một collaborator bean thật sẽ rõ ràng hơn.

### 3.2. Vì sao constructor tự inject thường gây cycle?

Ví dụ không nên dùng làm mặc định:

```java
@Service
class ReportService implements ReportOperations {
    private final ReportOperations self;

    ReportService(ReportOperations self) {
        this.self = self;
    }
}
```

Đây là fragment minh họa thiếu các method của interface. Để dựng `ReportService`, Spring phải resolve constructor parameter `ReportOperations`; nếu nó trỏ lại chính bean đó thì bean chưa thể xây dựng xong, tạo một **constructor dependency cycle**. Vòng constructor thông thường không thể khắc phục bằng cơ chế cấp sớm singleton vốn dành cho một số trường hợp field/setter.

Một `@Lazy` đặt **trên constructor parameter** có thể trì hoãn resolution và thay đổi tình huống kỹ thuật, nhưng không làm quan hệ tự phụ thuộc trở thành thiết kế tốt. Không tự inject cùng một kiểu qua constructor chỉ để sửa self-invocation.

### 3.3. Field/setter tự inject không `@Lazy` cũng rủi ro

```java
@Autowired
private ReportOperations self;
```

Tại lúc populate field, target có thể vẫn đang được Spring khởi tạo. Nếu dependency resolve ngay và trỏ lại bean hiện tại, Spring có thể phải xử lý **early bean reference**. Với singleton và một số cấu hình cho phép circular dependency, hạ tầng AOP có thể tham gia bằng `SmartInstantiationAwareBeanPostProcessor.getEarlyBeanReference(...)` để tạo early proxy.

Nhưng:

- Không phải mọi vòng dependency đều được Spring chấp nhận.
- Early reference không phải một lời hứa chung rằng toàn bộ initialization/advice đã sẵn sàng.
- Circular reference với constructor vẫn có những giới hạn riêng.
- Spring Boot từ 2.6 trở đi mặc định không cho phép circular references, trừ khi được cấu hình khác.
- Có thể gặp `BeanCurrentlyInCreationException`, raw-target reference hoặc mismatch với proxy cuối khi thiết kế vòng phức tạp.

Không nên bật `spring.main.allow-circular-references=true` như giải pháp đầu tiên; hãy loại bỏ quan hệ tự phụ thuộc hoặc trì hoãn một dependency có chủ đích khi thật sự cần.

## 4. `@Autowired @Lazy` tại injection point hoạt động thế nào?

Đặt `@Lazy` ở **điểm inject**:

```java
@Service
public class ReportService implements ReportOperations {
    @Autowired
    @Lazy
    private ReportOperations self;

    @Override
    public void generate() {
        this.audit(); // Bypass AOP proxy.
        self.audit(); // Qua lazy-resolution proxy, rồi đến bean do Spring cấp.
    }

    @Override
    public void audit() {
        System.out.println("audit");
    }
}
```

Đây là fragment; ví dụ đầy đủ ở mục 7. Với `@Autowired @Lazy`, Spring tạo **lazy-resolution proxy** và không cần resolve target dependency ngay khi inject field. Khi `self.audit()` chạy lần đầu sau khi bean đã khởi tạo xong, proxy trung gian mới resolve `ReportOperations` từ Spring. Nếu container công bố bean đó dưới dạng AOP proxy và pointcut chọn `audit()`, advice chạy.

**Hai nghĩa của `@Lazy` không giống nhau:**

| Cách đặt | Tác dụng trong Spring |
| --- | --- |
| `@Lazy` trên class `@Service` hoặc method `@Bean` | Yêu cầu trì hoãn **khởi tạo bean** khi có thể |
| `@Lazy` trên field/parameter `@Autowired` | Tạo **lazy-resolution proxy** tại injection point; tìm dependency khi thực sự sử dụng |
| `@Lazy` trên một method business | Không tự bật advice hoặc sửa self-invocation |

Các lưu ý:

- Không gọi `self.audit()` trong constructor, `@PostConstruct` hoặc lúc container chưa có thể cung cấp AOP proxy hoàn chỉnh. Lazy không có nghĩa bean đã initialized.
- Với singleton, lazy proxy thường cache dependency sau lần resolve đầu tiên; với scope khác, cách resolve có thể khác.
- `@Lazy` có thể **trì hoãn cả lỗi cấu hình**: dependency không tồn tại hay không resolve được có thể chỉ lỗi khi lời gọi xảy ra.
- `@Lazy` không bảo đảm aspect được scan, hạ tầng AOP đã bật hoặc pointcut khớp.
- Nếu actual AOP proxy là JDK dynamic proxy, hãy tự inject **interface được proxy thực hiện**; inject concrete class có thể lỗi kiểu hoặc dẫn đến hành vi không mong muốn.
- `@Lazy` không sửa static/private/final method limitations hoặc object do code tự `new`.
- Nếu có nhiều `ReportOperations` bean khác nhau, self-injection fallback có thể chọn một bean khác. Đừng đánh đồng “reference có đúng type” với “reference chính xác của tôi”.

Một lỗi test rất dễ gặp: `AopUtils.isAopProxy(self)` cho kết quả true vì `self` là **injection-point proxy**, không nhất thiết vì underlying bean có Spring AOP advice. Hãy kiểm tra `AopUtils.isAopProxy(context.getBean(ReportOperations.class))` cho bean chính thức và xác nhận các dòng advice in ra từ invocation.

## 5. Những phụ thuộc phát sinh và chi phí thiết kế

### 5.1. Phụ thuộc logic vào lifecycle container

Một method tưởng chỉ gọi business logic nay cần Spring tạo bean, tạo self reference rồi công bố AOP proxy. Gọi sai giai đoạn lifecycle có thể resolve reference khi bean đang được tạo hoặc chưa hoàn tất post-processing.

Ví dụ, không nên gọi self method cần advice từ `@PostConstruct`: callback initialization thường chạy trên target trước khi proxy cuối cùng được công bố. Tốt hơn hãy để operation được gọi từ consumer sau khi context sẵn sàng.

### 5.2. Coupling với Spring DI

Field `@Autowired @Lazy ReportOperations self` là hạ tầng Spring nằm ngay trong class nghiệp vụ. Một unit test đơn giản:

```java
ReportService service = new ReportService();
service.generate();
```

sẽ không nhận field injection của Spring, do đó `self` vẫn null và lời gọi đến nó có thể ném `NullPointerException`. Muốn test phần này phải dùng context hoặc cấu hình dependency thủ công. Đây là chi phí thật so với thiết kế có constructor injection của một collaborator bean độc lập.

### 5.3. Sai dependency khi có nhiều implementation

Giả sử có cả `ReportService implements ReportOperations` và `SpecialReportService implements ReportOperations`. Self-reference không được Spring ưu tiên hơn ứng viên bình thường. Vì thế field tự inject theo interface có thể resolve sang service khác; lời gọi và aspect vẫn chạy, nhưng sai operation hoặc sai state.

Nếu bắt buộc self-inject, phải kiểm tra bean name/type/qualifier trong ứng dụng cụ thể. Tuy nhiên, qualifier và tên bean là thêm một tầng coupling; khi có thể hãy thiết kế hai service có vai trò rõ ràng.

### 5.4. Proxy type ảnh hưởng kiểu của self reference

| Cấu hình AOP | Điểm cần lưu ý |
| --- | --- |
| JDK dynamic proxy | Proxy thực hiện interface, không kế thừa implementation class. Self reference nên dùng interface |
| CGLIB class proxy | Có thể inject theo class khi class có thể subclass; `final`/`private` methods vẫn không được CGLIB advice |
| Spring Framework thuần | `@EnableAspectJAutoProxy(proxyTargetClass=false)` ưu tiên JDK proxy nếu target có interface |
| Spring Boot có AOP auto-config | Thường mặc định class proxy; đặt `spring.aop.proxy-target-class=false` nếu chủ đích ưu tiên interface proxy |

Hai loại proxy đều gặp self-invocation. Đổi loại proxy không khiến `this.audit()` tự quay lại interceptor chain.

### 5.5. Scope làm “self” thiếu ổn định

Với singleton mặc định, một Spring bean reference thường ổn định trong một context. Với prototype/request scope hoặc scoped proxies:

- Mỗi lần container resolve bean có thể tạo target khác hoặc phụ thuộc scope hiện hành.
- Lazy reference có thể resolve theo scope khác; khái niệm “gọi chính instance này” cần kiểm chứng, không suy ra từ tên.
- Khi request scope không tồn tại, lời gọi có thể thất bại.
- Self-reference không tự xử lý thread context hoặc truyền transaction/security state giữa threads.

Không áp một ví dụ singleton cho mọi scope.

## 6. Giải pháp ưu tiên: tách method cần advice sang bean khác

Thay vì `ReportService -> self -> ReportService`, tạo một collaborator bean với một trách nhiệm rõ ràng:

```java
public interface AuditOperations {
    void record(String reportId);
}

@Service
public class AuditService implements AuditOperations {
    @Override
    public void record(String reportId) {
        System.out.println("[audit] " + reportId);
    }
}

@Service
public class ReportService {
    private final AuditOperations audit;

    public ReportService(AuditOperations audit) {
        this.audit = audit;
    }

    public void generate(String reportId) {
        System.out.println("[report] " + reportId);
        audit.record(reportId);
    }
}
```

Đoạn trên minh họa thiết kế; mỗi public type cần file riêng, các class `@Service` cần import `org.springframework.stereotype.Service`, và phải cấu hình Spring AOP/aspect chọn `AuditService.record()`. Consumer gọi `ReportService.generate(...)`, còn method cần advice `record(...)` được gọi **qua dependency do Spring cung cấp**, nên có cơ hội qua AOP proxy.

Điểm mạnh:

1. Tránh vòng phụ thuộc chính nó.
2. Sử dụng **constructor injection** thông thường.
3. Phân tách rõ nơi áp dụng policy/aspect.
4. Kiểm thử service với một fake `AuditOperations` mà không cần Spring container.
5. Không phụ thuộc `@Lazy` trên self field, không phụ thuộc `AopContext.currentProxy()`.
6. Khi đổi JDK/CGLIB, consumer vẫn nên phụ thuộc vào hợp đồng interface.

Không nhất thiết tách mọi helper; nếu advice ở public entry point đã bao trùm toàn bộ operation, hãy giữ private helper nội bộ. Tách bean khi method thật sự cần policy/ràng buộc AOP riêng.

## 7. Ví dụ Java 17 và Spring Framework 6.2 chạy được

Ví dụ dùng Maven, Java 17, Spring Framework 6.2.x, không cần Spring Boot. Có một service `ReportService implements ReportOperations` và aspect `@Before` chỉ chọn method `audit()`. Ta dùng `@Autowired @Lazy` để có self reference mà không resolve ngay trong lúc field injection.

Mục tiêu quan sát: `this.audit()` không chạy advice; `self.audit()` chạy advice sau khi lazy proxy resolve Spring AOP bean; gọi `audit()` trực tiếp trên bean Spring cũng chạy advice.

**Cấu trúc:**

```text
spring-aop-self-injection/
├── pom.xml
└── src/main/java/demo/self/
    ├── AppConfig.java
    ├── ReportOperations.java
    ├── ReportService.java
    ├── AuditAspect.java
    └── Main.java
```

### 7.1. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-self-injection</artifactId>
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

### 7.2. `AppConfig.java`

```java
package demo.self;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
```

Spring scan target bean và aspect trong package `demo.self`. Với target có interface và `proxyTargetClass=false`, ví dụ dùng JDK dynamic proxy.

### 7.3. `ReportOperations.java`

```java
package demo.self;

public interface ReportOperations {
    void generate();
    void audit();
}
```

Cả hai public methods nằm trong interface, nên JDK proxy có thể cung cấp cho caller; chỉ `audit()` được aspect dưới đây advice.

### 7.4. `ReportService.java`

```java
package demo.self;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class ReportService implements ReportOperations {
    @Autowired
    @Lazy
    private ReportOperations self;

    @Override
    public void generate() {
        System.out.println("[service] generate");
        this.audit(); // Không qua Spring AOP proxy.
        self.audit(); // Resolve lazy dependency, gọi Spring AOP proxy.
    }

    @Override
    public void audit() {
        System.out.println("[service] audit");
    }
}
```

Đây là self-injection **có chủ đích để học**, không phải khuyến nghị thiết kế mặc định. Vì `@Lazy` ở injection point, không cần resolve `ReportOperations` ngay khi field đang được inject. Khi `generate()` được gọi sau khi context hoàn tất, lời gọi `self.audit()` resolve bean do Spring công bố.

### 7.5. `AuditAspect.java`

```java
package demo.self;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {
    @Before("execution(* demo.self.ReportService.audit(..))")
    public void beforeAudit() {
        System.out.println("[advice] before audit");
    }
}
```

Aspect có `@Component` để Spring đăng ký thành bean. Pointcut sử dụng target class; Spring có thể match method target ngay cả khi caller giữ interface proxy.

### 7.6. `Main.java`

```java
package demo.self;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            ReportOperations service = context.getBean(ReportOperations.class);

            System.out.println("isAopProxy=" + AopUtils.isAopProxy(service));
            System.out.println("isJdkProxy=" + AopUtils.isJdkDynamicProxy(service));

            service.generate();

            System.out.println("[main] direct proxy call");
            service.audit();
        }
    }
}
```

Lệnh biên dịch/chạy từ thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.self.Main
```

**Stdout từ các lệnh `System.out` trong ví dụ** (không tính log build nếu môi trường phát thêm):

```text
isAopProxy=true
isJdkProxy=true
[service] generate
[service] audit
[advice] before audit
[service] audit
[main] direct proxy call
[advice] before audit
[service] audit
```

Giải thích:

- Hai dòng đầu xác minh bean chính thức lấy từ Spring là AOP proxy và là JDK dynamic proxy trong cấu hình này.
- `service.generate()` đi qua proxy nhưng `generate()` không khớp pointcut; target in `[service] generate`.
- `this.audit()` in `[service] audit` nhưng **không có advice** trước lần đầu.
- `self.audit()` đi qua lazy-resolution proxy đến bean Spring đã có AOP proxy, nên in `[advice] before audit` rồi `[service] audit` lần hai.
- Gọi `service.audit()` trong `Main` cũng đi qua AOP proxy, nên có advice.
- Không cần `exposeProxy=true` trong ví dụ này; self-injection và `AopContext.currentProxy()` là hai kỹ thuật khác nhau.

## 8. Thử nghiệm và các tình huống cần cảnh giác

### 8.1. Bỏ `@Lazy`

Nếu đổi field thành:

```java
@Autowired
private ReportOperations self;
```

Spring có thể phải resolve self reference trong khi target đang được xây dựng, dẫn đến circular dependency hoặc early reference. Ứng dụng Spring Boot với circular references bị cấm có thể thất bại khi khởi tạo. Không có bảo đảm rằng bỏ `@Lazy` vẫn hoạt động trong mọi cấu hình. Hướng xử lý đúng thường là refactor bean.

### 8.2. Đổi `self.audit()` về `this.audit()`

Cả hai lần gọi từ `generate()` sẽ không qua proxy, mặc dù `service.audit()` trong `Main` vẫn chạy advice. Điều này phân biệt **self-invocation** với lỗi pointcut hoặc lỗi đăng ký aspect.

### 8.3. Có thêm implementation thứ hai

Nếu một bean khác cũng triển khai `ReportOperations`, Spring không có quy tắc mặc định “tên field là self thì luôn inject chính tôi”. Self-injection chỉ là fallback; có thể chọn ứng viên thường trước. Khi bắt buộc cần đúng proxy, xác định bean theo tên/qualifier hoặc cân nhắc refactor. Test integration phải chứng minh method chạy trên **đúng bean**.

### 8.4. Bỏ aspect

Nếu bỏ `@Component` trên `AuditAspect` mà không thay bằng `@Bean`, hoặc đổi pointcut không khớp, `self.audit()` vẫn có thể gọi bean Spring nhưng sẽ **không có advice**. Đây không phải lỗi của `@Lazy` mà là không có advisor áp dụng.

### 8.5. Chuyển sang class-based proxy

Trong `AppConfig` thay bằng:

```java
@EnableAspectJAutoProxy(proxyTargetClass = true)
```

Với class không `final`, Spring tạo CGLIB proxy. `isJdkProxy` trong stdout sẽ là `false`, còn việc `this.audit()` bypass và `self.audit()` gọi qua proxy vẫn đúng. Bật CGLIB không xóa ranh giới proxy.

### 8.6. Gọi trong lifecycle callback

Không dùng `self.audit()` trong constructor hay `@PostConstruct` để “tự bật” transaction hoặc audit. Thời điểm đó target có thể chưa trải qua bước auto-proxy cuối cùng; lazy resolution có thể gặp `BeanCurrentlyInCreationException` hoặc reference khác mong đợi. Hãy gọi operation từ Spring-managed consumer sau khi context sẵn sàng.

### 8.7. Dùng `ObjectProvider` thay `@Lazy`

Một phương án resolve theo nhu cầu:

```java
@Service
public class ReportService implements ReportOperations {
    private final ObjectProvider<ReportOperations> provider;

    public ReportService(ObjectProvider<ReportOperations> provider) {
        this.provider = provider;
    }

    @Override
    public void generate() {
        provider.getObject().audit();
    }

    @Override
    public void audit() {
        System.out.println("audit");
    }
}
```

Đây là fragment cần import `org.springframework.beans.factory.ObjectProvider`, `@Service` và type interface đầy đủ. Inject provider không nhất thiết resolve bean ngay, nhưng `getObject()` **không tự bảo đảm** chọn chính bean này khi có nhiều candidates; self-injection vẫn liên quan quy tắc lựa chọn. `ObjectProvider` cũng tăng coupling với DI container. Đây không phải giải pháp được ưu tiên trước refactor.

## 9. So sánh các lựa chọn

| Cách | Khi hữu ích | Giới hạn / rủi ro |
| --- | --- | --- |
| **Tách method sang Spring bean khác và constructor-inject** | Thiết kế chuẩn khi operation cần advice độc lập | Cần phân chia trách nhiệm hợp lý |
| `@Autowired @Lazy` self-reference | Giải pháp tình thế để đi qua proxy nội bộ khi không refactor được | Fallback autowire, lazy proxy ≠ AOP proxy, khó test, lifecycle coupling |
| `@Autowired` self field không lazy | Có thể hoạt động trong vài cấu hình legacy hỗ trợ early singleton reference | Dễ circular dependency, raw bean vs proxy, lỗi lúc khởi tạo |
| Constructor self-injection | Tránh dùng trực tiếp | Constructor dependency cycle; `@Lazy` chỉ trì hoãn chứ không sửa thiết kế |
| `AopContext.currentProxy()` | Giải pháp cuối khi invocation đã qua proxy và có expose | Cần `exposeProxy=true`, Spring-specific, phụ thuộc thread/invocation |
| `ObjectProvider<T>` hoặc `ApplicationContext.getBean(...)` | Khi chủ động cần lookup bean theo thời điểm | Service locator coupling, chọn nhầm bean/ambiguity nếu type không duy nhất |

### `AopContext.currentProxy()` có thay thế self-injection không?

Spring cho phép:

```java
((ReportOperations) AopContext.currentProxy()).audit();
```

nhưng cần đang thực thi trong **invocation qua AOP proxy có `exposeProxy=true`**, ví dụ cấu hình `@EnableAspectJAutoProxy(exposeProxy = true)`. Nếu không có current proxy phù hợp, `AopContext.currentProxy()` ném `IllegalStateException`. Với JDK proxy, phải cast sang interface proxy triển khai, không phải tùy tiện cast sang implementation class.

Cách này ghép business code với Spring AOP; tài liệu Spring khuyên hạn chế và ưu tiên refactor/bean dependency. Nó không làm `private`/`static` methods trở thành interceptable, không sửa lời gọi `this.method()` đã có và không hoạt động trong code được tạo ngoài context.

## 10. Checklist chẩn đoán sự cố

| Triệu chứng | Nguyên nhân/hướng kiểm tra |
| --- | --- |
| `this.inner()` không chạy advice | Đường gọi trên target, bypass Spring proxy; xem có nên tách bean |
| `self.inner()` không chạy advice | Kiểm tra actual bean được lazy resolve, aspect/pointcut, loại AOP proxy |
| `AopUtils.isAopProxy(self)` true nhưng advice không chạy | `self` có thể chỉ là lazy-resolution proxy; kiểm tra bean chính lấy từ context |
| `BeanCurrentlyInCreationException` | Eager self resolution hoặc circular dependency; không dựa vào startup partial bean |
| `NoUniqueBeanDefinitionException`/injection sai bean | Có nhiều implementation, self-injection fallback; kiểm tra name/qualifier |
| `ClassCastException` hay không inject được concrete class | Actual AOP proxy có thể là JDK interface proxy |
| Test bằng `new ReportService()` lỗi NPE | `@Autowired @Lazy` không được inject ngoài Spring container |
| Chạy trong `@PostConstruct` thất bại | Proxy cuối chưa sẵn sàng, tránh gọi self trong init |
| Spring Boot từ chối circular reference | Loại bỏ cycle, đừng bật circular references chỉ để né thiết kế |
| Method `final`/`private`/`static` vẫn không có advice | Giới hạn proxy method; self-injection không vượt khả năng override |
| Có AOP proxy nhưng `audit()` không chạy advice | Kiểm tra pointcut match và method invocation thực sự qua proxy |
| Aspect chỉ có `@Aspect` mà không phải bean | Đăng ký qua `@Component` hoặc `@Bean`, bật Spring AOP |

Thứ tự debug: kiểm tra target/aspect là Spring beans → xác nhận actual Spring AOP proxy (không chỉ lazy proxy) → kiểm tra chọn đúng bean và kiểu interface → xác nhận thời điểm gọi sau initialization → kiểm tra `this` hay `self` → đối chiếu pointcut → refactor nếu có self-cycle không cần thiết.

## 11. Câu hỏi ôn tập

**1. Tại sao `this.audit()` bỏ qua advice?** Vì Spring AOP proxy chỉ chặn lời gọi đi qua reference proxy; `this` ở trong target gọi trực tiếp target.

**2. `@Autowired ReportOperations self` có chắc chọn chính bean đó không?** Không; self-injection chỉ là cơ chế fallback trong Spring, các candidate bình thường được xét trước.

**3. `@Lazy` trên self field có tự kích hoạt Spring AOP không?** Không. Nó tạo một proxy để lazy-resolve dependency, còn AOP proxy cần advisor/pointcut thực sự áp dụng.

**4. Vì sao constructor inject chính service dễ lỗi?** Nó có thể tạo constructor cycle khi bean muốn dựng chính nó trước khi construction kết thúc.

**5. Vì sao không nên gọi `self.audit()` trong `@PostConstruct`?** Khi ấy target có thể chưa được auto-proxy hoàn toàn; lazy resolve có thể gặp bean đang được tạo.

**6. Có cần `exposeProxy=true` để dùng `@Autowired @Lazy` không?** Không. Cấu hình này dùng cho `AopContext.currentProxy()`; lazy self injection làm việc qua dependency resolution.

**7. Lựa chọn được ưu tiên lâu dài?** Tách operation cần advice vào một bean độc lập và gọi qua dependency Spring inject, trừ khi có lý do rất rõ để giữ self-reference.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Proxying Mechanisms và Self-Invocation](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework — Using @Autowired: Self Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/annotation-config/autowired.html)
- [Spring Framework 6.2 — @Lazy API: Injection Point](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/context/annotation/Lazy.html)
- [Spring Framework 6.2 — ContextAnnotationAutowireCandidateResolver API](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/context/annotation/ContextAnnotationAutowireCandidateResolver.html)
- [Spring Framework — Dependency Injection](https://docs.spring.io/spring-framework/reference/6.2/core/beans/dependencies/factory-collaborators.html)
- [Spring Framework — @AspectJ support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invocation-boundaries-005">AopContext.currentProxy(), exposeProxy và lý do hạn chế sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# AopContext.currentProxy(), exposeProxy và lý do hạn chế sử dụng trong Spring AOP

## 1. Mục tiêu của bài

Trong Spring AOP, advice chỉ được áp dụng cho **lời gọi đi qua AOP proxy**. Một service có thể được container trả về dưới dạng proxy, nhưng bên trong chính target, lời gọi **this.method()** lại không quay trở lại proxy. Đây là lý do một phương thức có annotation, hoặc khớp pointcut, vẫn có thể không được advice khi được gọi nội bộ.

Spring cung cấp hai cơ chế liên quan trực tiếp đến tình huống này:

- **AopContext.currentProxy()**: lấy **proxy của lời gọi Spring AOP đang diễn ra** trên thread hiện tại.
- **exposeProxy = true**: cấu hình cho Spring đặt proxy đang hoạt động vào ngữ cảnh thread để currentProxy() có thể lấy ra.

Cơ chế này có thể chuyển một lời gọi nội bộ thành một lời gọi đi qua proxy, nhưng **không phải hướng thiết kế được khuyến nghị**. Nó tạo sự phụ thuộc giữa mã nghiệp vụ với Spring AOP, đòi hỏi cấu hình đặc biệt, phụ thuộc vào đường đi lời gọi và thread, đồng thời có chi phí bổ sung.

Sau bài học, bạn sẽ:

1. Phân biệt **this** của target với **Spring AOP proxy**.
2. Hiểu điều kiện để **AopContext.currentProxy()** trả về proxy hoặc ném **IllegalStateException**.
3. Sử dụng **@EnableAspectJAutoProxy(exposeProxy = true)** trong cấu hình Spring Framework thuần.
4. Chạy ví dụ Java 17+ cho thấy **this.inner()** bỏ qua advice còn **((ServiceApi) AopContext.currentProxy()).inner()** đi qua advice.
5. Nhận diện giới hạn của JDK dynamic proxy, CGLIB, callback, thread mới, self-invocation, final/private và recursion.
6. Ưu tiên refactor thành **hai Spring beans có dependency rõ ràng**, thay vì đưa AopContext vào service.

**Phạm vi:** Chỉ bàn về **Spring Framework proxy-based AOP** và ảnh hưởng của currentProxy/exposeProxy tới lời gọi Spring-managed bean. Không đi sâu vào lý thuyết Aspect tổng quát hoặc AspectJ weaving.

## 2. Vấn đề gốc: self-invocation vượt qua proxy

Giả sử Spring trả về proxy P cho target T:

~~~text
Client
   |
   v
Spring AOP proxy P
   |
   +-- xem xét advisor/pointcut
   +-- chạy advice phù hợp
   |
   v
Target T.outer()
   |
   +-- this.inner() ------> T.inner()
                             |
                             +-- không quay về proxy P
~~~

Khi client gọi **outer()** qua proxy, lời gọi ngoài được Spring AOP xử lý. Nhưng khi **T.outer()** gọi **this.inner()**, biến **this** tham chiếu **target T**, không phải P. Vì vậy advice gắn với **inner()** không nhận thêm một lời gọi proxy để thực thi.

Ví dụ:

~~~java
public class OrderService {
    public void place() {
        this.validate();
    }

    public void validate() {
        // Logic kiểm tra đơn hàng
    }
}
~~~

Đây là **đoạn minh họa self-invocation**, chưa có cấu hình Spring bean hoặc aspect độc lập. Nếu advice chỉ khớp **validate()**, lời gọi từ **place()** vào **this.validate()** sẽ không chạy advice ấy khi Spring sử dụng proxy-based AOP.

Hai ý quan trọng:

- **Bean được proxy** không đồng nghĩa **mọi method invocation** đều đi qua proxy.
- **CGLIB proxy** không tự chữa self-invocation. Dù proxy là subclass, Spring AOP vẫn có ranh giới lời gọi: lời gọi phát sinh ngay trong target và không quay qua proxy sẽ bỏ qua advice.

Khi cần advice trên một thao tác, thiết kế tốt thường là **gọi thao tác đó qua Spring-managed dependency ở một bean khác**.

## 3. AopContext.currentProxy() là gì?

Spring cung cấp API:

~~~java
org.springframework.aop.framework.AopContext.currentProxy()
~~~

Method static này trả về kiểu **Object**, là **AOP proxy đang được Spring expose trên thread hiện tại**. Nó không nhận bean name hoặc target class; vì vậy **không phải API tìm một bean bất kỳ từ ApplicationContext**.

Điều kiện dùng thành công phải đồng thời thỏa:

1. Hiện tại đang ở trong **một lời gọi qua Spring AOP proxy** có cơ chế expose proxy.
2. Proxy ấy được cấu hình **exposeProxy = true**.
3. Lời gọi tới **currentProxy()** xảy ra **trên cùng thread** của invocation được expose, trong lúc invocation đó còn hoạt động.
4. Object trả về là proxy hiện tại; khi cast, kiểu cần tương thích với các interface/class được proxy công bố.

Nếu không có proxy tương ứng, currentProxy() **ném IllegalStateException**, chứ không trả **null** như một tín hiệu có thể tùy ý bỏ qua.

Các ví dụ không thỏa điều kiện:

- Gọi currentProxy() trong **main()**, ngoài bất cứ Spring AOP invocation nào.
- Gọi trong constructor hoặc **@PostConstruct** rồi mặc nhiên chờ proxy hiện tại đã tồn tại.
- Gọi trên target được tạo trực tiếp bằng **new** và được sử dụng ngoài container.
- Gọi từ code chạy ở **thread khác** so với invocation (chẳng hạn công việc được chuyển sang executor) mà không có một proxy invocation mới trên thread đó.
- Proxy được tạo nhưng **exposeProxy** vẫn mặc định **false**.

**AopContext** sử dụng một holder theo **ThreadLocal**. Spring quản lý việc đặt proxy hiện tại, khôi phục proxy cũ sau lời gọi và dọn holder khi phù hợp; ứng dụng không nên tự giả định có thể giữ object đó như một context xuyên thread, xuyên request hoặc dùng sau khi invocation đã kết thúc.

## 4. exposeProxy là gì, bật ở đâu?

**exposeProxy** là thuộc tính của cấu hình proxy Spring AOP; mặc định **false**. Khi bật, Spring công bố proxy đang xử lý invocation để **AopContext.currentProxy()** truy cập được.

Với **Spring Framework thuần** và annotation-based AOP:

~~~java
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
public class AppConfig {
}
~~~

Bạn vẫn cần **đăng ký aspect và service như các Spring beans** qua **@ComponentScan**, **@Bean** hoặc cơ chế tương đương. Annotation trên chỉ là ví dụ phần cấu hình; nó chưa tự tạo service, aspect hay pointcut.

So sánh:

~~~java
@EnableAspectJAutoProxy
~~~

và:

~~~java
@EnableAspectJAutoProxy(exposeProxy = true)
~~~

Cả hai bật Spring AOP auto-proxying trong context tương ứng. Dòng thứ hai **bổ sung việc expose current proxy**. Nó không làm thay đổi ý nghĩa của **@Before**, **@Around**, điều kiện match pointcut, bean registration hay khả năng chặn **private/final** của một class-based proxy.

Một cấu hình khác dễ nhầm:

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = true)
~~~

Thuộc tính **proxyTargetClass** chọn class-based proxy (CGLIB) thay vì mặc định ưu tiên JDK dynamic proxy khi phù hợp. Thuộc tính **exposeProxy** quyết định có cung cấp proxy hiện tại qua AopContext hay không. Hai thuộc tính **độc lập về mục đích**:

~~~java
@EnableAspectJAutoProxy(
    proxyTargetClass = true,
    exposeProxy = true
)
~~~

Nếu dùng **ProxyFactory** theo lối lập trình thay vì annotation-based auto-proxy:

~~~java
factory.setExposeProxy(true);
~~~

là cấu hình tương đương **trên proxy factory đó**. Đoạn này giả sử **factory** là một instance **org.springframework.aop.framework.ProxyFactory** đã được tạo và thiết lập target/advice; không phải file Java độc lập.

## 5. Ví dụ hoàn chỉnh Java 17+: this.inner() và lời gọi lại qua proxy

Ta xây dự án **Spring Framework 6.2.12, Java 17+, Maven 3.9+**. Ví dụ không cần Spring Boot, database hay web server. Một service có ba method:

- **direct()**: gọi **this.inner("direct")**, advice cho inner **không chạy**.
- **viaProxy()**: gọi **((Operations) AopContext.currentProxy()).inner("viaProxy")**, advice cho inner **chạy** khi exposeProxy được bật và lời gọi ngoài đi qua proxy.
- **inner(String)**: method được pointcut lựa chọn.

### 5.1. Cấu trúc thư mục

~~~text
spring-aop-current-proxy/
├── pom.xml
└── src/main/java/demo/
    ├── Operations.java
    ├── OperationsImpl.java
    ├── InnerAspect.java
    ├── AppConfig.java
    └── App.java
~~~

### 5.2. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-current-proxy</artifactId>
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
                    <mainClass>demo.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**spring-context** cung cấp Spring ApplicationContext và kéo theo Spring AOP. **aspectjweaver** cung cấp kiểu AspectJ annotation/pointcut để Spring xử lý **@AspectJ-style advice**. Đây **vẫn là Spring AOP proxy**, không phải AspectJ compile-time hoặc load-time weaving.

### 5.3. Interface của service

**src/main/java/demo/Operations.java**

~~~java
package demo;

public interface Operations {
    void direct();
    void viaProxy();
    void inner(String source);
}
~~~

Đưa cả ba method vào interface để ví dụ hoạt động với **JDK dynamic proxy** mặc định. Khi **AopContext.currentProxy()** trả về JDK proxy, cast sang **Operations** là hợp lệ; cast sang **OperationsImpl** lại không an toàn.

### 5.4. Service triển khai hai đường gọi

**src/main/java/demo/OperationsImpl.java**

~~~java
package demo;

import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;

@Service
public class OperationsImpl implements Operations {

    @Override
    public void direct() {
        System.out.println("OUTER direct");
        this.inner("direct");
    }

    @Override
    public void viaProxy() {
        System.out.println("OUTER viaProxy");
        Operations proxy = (Operations) AopContext.currentProxy();
        proxy.inner("viaProxy");
    }

    @Override
    public void inner(String source) {
        System.out.println("TARGET inner " + source);
    }
}
~~~

Điểm khác biệt duy nhất giữa hai entry methods là **đối tượng nhận lời gọi inner()**:

- Trong **direct()**, receiver là **this**, tức target hiện tại. Không có lời gọi thứ hai đi vào proxy.
- Trong **viaProxy()**, receiver là **proxy** Spring đang expose. Lời gọi inner() quay qua proxy và được advisor xem xét.

Lưu ý: **AopContext.currentProxy()** chỉ có giá trị trong đường gọi có đủ điều kiện như đã mô tả ở mục 3. Vì vậy code này **không còn là POJO độc lập**: nó phụ thuộc cụ thể vào Spring AOP.

### 5.5. Aspect chỉ bắt inner()

**src/main/java/demo/InnerAspect.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class InnerAspect {

    @Before("execution(* demo.OperationsImpl.inner(..))")
    public void beforeInner(JoinPoint joinPoint) {
        Object source = joinPoint.getArgs()[0];
        System.out.println("ADVICE inner " + source);
    }
}
~~~

Đây là một **Spring-managed aspect bean**:

- **@Aspect**: khai báo advice.
- **@Component**: cho component scanning đăng ký bean aspect.
- **@Before**: chỉ match **inner(...)** của implementation; **direct()** và **viaProxy()** không có advice riêng trong ví dụ.

Điều này làm rõ một điểm tinh tế: **viaProxy()** không cần có **@Before advice** riêng để currentProxy() hoạt động, miễn **Operations bean thực sự là proxy** và outer call đang chạy trong invocation của proxy được cấu hình exposeProxy. Spring vẫn đi qua lớp proxy khi client gọi **viaProxy()**, dù danh sách interceptor riêng cho method đó rỗng.

### 5.6. Bật component scan và exposeProxy

**src/main/java/demo/AppConfig.java**

~~~java
package demo;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackages = "demo")
@EnableAspectJAutoProxy(exposeProxy = true)
public class AppConfig {
}
~~~

Cấu hình thực hiện ba việc độc lập:

1. **@Configuration**: lớp cấu hình Java cho Spring.
2. **@ComponentScan**: đăng ký **OperationsImpl** và **InnerAspect** thành Spring beans.
3. **@EnableAspectJAutoProxy(exposeProxy = true)**: bật hạ tầng Spring AOP và công bố proxy hiện tại cho AopContext.

Nếu chỉ giữ **exposeProxy = true** mà không có advisor áp dụng cho service, Spring không mặc nhiên tạo một proxy cho mọi bean. Ví dụ có advisor match inner() nên **OperationsImpl** đủ điều kiện được Spring proxy.

### 5.7. Chạy App và kiểm tra lỗi ngoài AOP invocation

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.aop.framework.AopContext;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            Operations operations = context.getBean(Operations.class);
            System.out.println(
                    "IS_PROXY " + AopUtils.isAopProxy(operations));

            System.out.println("=== direct ===");
            operations.direct();

            System.out.println("=== via proxy ===");
            operations.viaProxy();

            System.out.println("=== outside ===");
            try {
                AopContext.currentProxy();
                System.out.println("OUTSIDE unexpected success");
            } catch (IllegalStateException expected) {
                System.out.println("OUTSIDE IllegalStateException");
            }
        }
    }
}
~~~

Chạy trong thư mục chứa pom.xml:

~~~bash
mvn -q clean compile exec:java
~~~

Các dòng **stdout logic** dự kiến, không tính các thông báo riêng từ Maven hoặc cấu hình logging:

~~~text
IS_PROXY true
=== direct ===
OUTER direct
TARGET inner direct
=== via proxy ===
OUTER viaProxy
ADVICE inner viaProxy
TARGET inner viaProxy
=== outside ===
OUTSIDE IllegalStateException
~~~

Giải thích thứ tự:

1. **IS_PROXY true**: Spring trả bean **Operations** dưới dạng một AOP proxy có advisor áp dụng cho **inner**.
2. Khi gọi **operations.direct()**, Spring đi qua proxy cho method direct; sau đó target gọi **this.inner()**. Không có **ADVICE inner direct**.
3. Khi gọi **operations.viaProxy()**, target truy cập proxy hiện tại và gọi **inner()** trên proxy ấy. Advice chạy trước target nên có **ADVICE inner viaProxy**.
4. Sau khi invocation **viaProxy()** kết thúc, Spring đã khôi phục trạng thái proxy trong ThreadLocal. Code của **main()** không còn ở trong AOP invocation nên **AopContext.currentProxy()** ném **IllegalStateException** dù exposeProxy đã bật trong context.

Dòng **OUTSIDE IllegalStateException** được in bởi catch có chủ đích để minh họa API contract. Trong ứng dụng thật, không nên dựa vào exception như một cách dò bean/proxy tùy tiện.

## 6. Tự kiểm tra tác dụng của exposeProxy

### 6.1. Tắt exposeProxy nhưng giữ AOP

Trong **AppConfig.java**, đổi:

~~~java
@EnableAspectJAutoProxy(exposeProxy = true)
~~~

thành:

~~~java
@EnableAspectJAutoProxy
~~~

Khởi động lại cùng ứng dụng. Spring vẫn tạo AOP proxy để áp dụng advice cho **inner()**, nhưng không expose proxy trong thread.

Khi **operations.direct()** chạy, hành vi vẫn giống trước:

~~~text
=== direct ===
OUTER direct
TARGET inner direct
~~~

Khi **operations.viaProxy()** chạy:

~~~text
=== via proxy ===
OUTER viaProxy
~~~

Sau đó **AopContext.currentProxy() ném IllegalStateException** vì exposeProxy không bật. Trong file **App.java** ở mục 5, không có try/catch bao quanh lời gọi **operations.viaProxy()**, nên **main() sẽ kết thúc bằng lỗi** thay vì in tiếp phần **=== outside ===**. Đó là **kết quả mong đợi của thí nghiệm lỗi**, không phải toàn bộ stdout có thể bảo đảm của một lần chạy Maven.

Muốn tiếp tục chương trình để quan sát cả phần outside, bạn có thể thay riêng lời gọi này bằng:

~~~java
try {
    operations.viaProxy();
} catch (IllegalStateException expected) {
    System.out.println("VIA_PROXY IllegalStateException");
}
~~~

Đây là **đoạn thay thế trong App.main**, không dùng đồng thời với lời gọi cũ. Sau khi kiểm chứng, trả cấu hình về **exposeProxy = true** để chạy lại bản hoàn chỉnh.

Kết luận: **có Spring proxy** và **có proxy hiện tại để currentProxy() lấy** là hai điều kiện khác nhau.

### 6.2. Gọi đối tượng tạo bằng new

Đoạn minh họa:

~~~java
OperationsImpl direct = new OperationsImpl();
direct.direct();
~~~

In:

~~~text
OUTER direct
TARGET inner direct
~~~

Đối tượng đó không được lấy từ Spring ApplicationContext; **direct()** không cần AopContext nên vẫn chạy, nhưng không có Spring AOP advice.

Nếu dùng:

~~~java
direct.viaProxy();
~~~

và không có một AOP invocation phù hợp đang active trên thread, currentProxy() sẽ ném **IllegalStateException**. Tạo object qua **new** không kích hoạt Spring AOP.

### 6.3. Gọi từ thread khác

Ví dụ biểu diễn ý tưởng:

~~~java
Thread thread = new Thread(() -> {
    AopContext.currentProxy();
});
thread.start();
~~~

Đây là **fragment minh họa**, chưa bắt exception ở thread mới. Thread mới **không tự thừa hưởng** AOP proxy đang expose trong ThreadLocal của thread gọi. Nếu không có một AOP proxy invocation mới được tạo trên thread kia, currentProxy() ném **IllegalStateException**.

Các tình huống như **@Async**, executor, CompletableFuture, hoặc asynchronous callback đều cần được phân tích theo thread thực tế. Ngay cả khi proxy được expose ở lời gọi bên ngoài, điều đó **không biến AopContext thành request-scoped hay asynchronous context**.

## 7. Vì sao lời gọi qua AopContext lại kích hoạt advice?

Xét đối tượng đã chạy ở mục 5:

~~~text
App
 |
 v
Operations proxy P: direct()
 |
 v
OperationsImpl target T: direct()
 |
 +-- this.inner() --> T.inner()
                        |
                        +-- advice inner không chạy
~~~

Đường khác:

~~~text
App
 |
 v
Operations proxy P: viaProxy()
 |
 v
OperationsImpl target T: viaProxy()
 |
 +-- AopContext.currentProxy() --> proxy P
                                    |
                                    +-- P.inner("viaProxy")
                                             |
                                             +-- advice inner
                                             |
                                             +-- T.inner("viaProxy")
~~~

Trong trường hợp thứ hai, lời gọi phát sinh từ target **được chuyển thành một lời gọi tới proxy P**. Spring AOP lại nhận một method invocation **inner()** hợp lệ, nên advisor áp dụng được.

Điều này **không thay đổi bytecode của inner()**, không tự sửa pointcut và không kích hoạt advice cho lời gọi trực tiếp **this.inner()** trước đó. Mọi khác biệt nằm ở **đối tượng nhận lời gọi**.

Đó cũng là nguyên nhân cần rất thận trọng với cách viết **currentProxy().someMethod()**: khi gọi nhầm method đang thực thi, bạn có thể tạo một chuỗi recursion không mong muốn.

## 8. Nguy cơ đệ quy vô hạn hoặc gọi advice lặp lại

Giả sử trong **viaProxy()** bạn viết nhầm:

~~~java
@Override
public void viaProxy() {
    Operations proxy = (Operations) AopContext.currentProxy();
    proxy.viaProxy();
}
~~~

Đây là **ví dụ mã sai**, không thay thế file chạy ở mục 5 nếu không chủ đích thử lỗi.

Chuỗi gọi là:

~~~text
proxy.viaProxy()
  -> target.viaProxy()
      -> currentProxy().viaProxy()
          -> target.viaProxy()
              -> currentProxy().viaProxy()
                  -> ...
~~~

Mã tiếp tục gọi chính entry method qua proxy, có thể kết thúc bằng **StackOverflowError**. Nếu method đó cũng được advise, advice còn có thể chạy lặp lại ở mỗi vòng.

Đối với **@Around** advice, còn một nguồn gây lặp khác: tự gọi lại một method được advice qua proxy mà không kiểm soát recursion, hoặc nhầm **ProceedingJoinPoint.proceed()** với việc thực hiện một proxy invocation mới. **proceed()** tiếp tục invocation hiện tại theo chuỗi interceptor; **currentProxy().someMethod()** tạo một lời gọi proxy khác. Hai thao tác không tương đương.

Không thay lời gọi **this** bằng currentProxy một cách máy móc. Trước tiên phải xác định chính xác method nào cần được advice, điểm vào nào an toàn và graph lời gọi sau thay đổi.

## 9. currentProxy() không phải cách lấy target hoặc bean tùy ý

API này trả về **proxy của invocation hiện tại**, không phải một BeanFactory, không phải service locator và không phải target raw.

| Nhu cầu | API phù hợp |
| --- | --- |
| Lấy một Spring bean từ container | Constructor injection, **@Bean** method parameter hoặc **ApplicationContext.getBean(...)** khi có lý do phù hợp |
| Tiếp tục invocation advice hiện tại | **ProceedingJoinPoint.proceed()** trong **@Around** advice |
| Truy cập proxy đang active trong invocation hiện tại | **AopContext.currentProxy()**, chỉ khi exposeProxy được bật |
| Xác minh một object có phải AOP proxy | **AopUtils.isAopProxy(object)** |
| Xác định proxy dùng JDK hay CGLIB | **AopUtils.isJdkDynamicProxy** / **AopUtils.isCglibProxy** |
| Tách logic sang một thao tác có advice rõ ràng | Tạo Spring bean khác và inject dependency |
| Truy cập target cụ thể từ hạ tầng AOP | Chỉ dùng API dành cho hạ tầng khi thật sự cần; không coi currentProxy là target |

Giả sử **currentProxy()** trả về một **JDK dynamic proxy** của **Operations**. Không nên cast:

~~~java
OperationsImpl impl =
        (OperationsImpl) AopContext.currentProxy();
~~~

Với JDK proxy, cast này có thể gây **ClassCastException**. Vì thế ví dụ chính cast về **Operations**, interface thực sự được proxy triển khai.

Với CGLIB, proxy là subclass của target nếu cấu hình và class cho phép; cast về target class có thể hợp lệ, nhưng **điều đó không xóa giới hạn self-invocation, final/private và exposeProxy**.

## 10. Vì sao Spring khuyến nghị hạn chế sử dụng?

Tài liệu Spring Framework về **Proxying Mechanisms** xem việc dùng **AopContext.currentProxy()** là cách nên tránh nếu có lựa chọn hợp lý hơn. Có ba nhóm lý do thực tế.

### 10.1. Coupling giữa nghiệp vụ và Spring AOP

Service bình thường chỉ cần triển khai interface của mình và gọi dependency:

~~~java
public void process() {
    collaborator.run();
}
~~~

Khi chuyển thành:

~~~java
public void process() {
    ((Operations) AopContext.currentProxy()).inner("work");
}
~~~

service đã biết rằng nó phải chạy dưới **Spring AOP proxy** có exposeProxy. Điều này giảm khả năng tái sử dụng class trong môi trường không dùng Spring AOP, gây khó khăn cho unit test thuần Java, và có thể làm code fail bất ngờ khi cấu hình proxy đổi.

**Dependency ngầm** này không nằm trong constructor hoặc interface của service. Người đọc class có thể không biết muốn gọi **process()** phải đi qua một proxy được cấu hình đặc biệt.

### 10.2. Điều kiện runtime mong manh

currentProxy() phụ thuộc vào **thread và invocation hiện tại**. Một service có thể chạy đúng khi được gọi qua controller/injected proxy nhưng lỗi khi:

- được gọi trực tiếp trong unit test;
- được tạo bằng **new**;
- được gọi trong constructor/init callback;
- được gọi từ callback chạy sau khi proxy invocation đã hoàn tất;
- được chuyển sang thread khác;
- được gọi ở context hoặc proxy không bật exposeProxy;
- bị cấu hình lại advisor khiến bean không còn được Spring proxy.

Vấn đề có thể chỉ xuất hiện ở một đường gọi cụ thể, khiến kiểm thử và chẩn đoán khó hơn dependency injection bình thường.

### 10.3. Chi phí và phạm vi tác động của exposeProxy

Spring **không expose proxy theo mặc định**, một phần vì việc cập nhật và khôi phục ThreadLocal cho các proxy invocation có **chi phí bổ sung**. Không nên đưa ra con số phần trăm hiệu năng nếu chưa benchmark tải thực tế.

Bật exposeProxy không phải giải pháp “miễn phí” cho mọi bean được auto-proxy; nên tránh bật rộng chỉ vì một method đang có self-invocation. Điều này đặc biệt quan trọng khi hệ thống có nhiều advice, nesting hoặc throughput lớn.

### 10.4. Khả năng nhầm proxy hiện tại trong chuỗi gọi lồng nhau

Giả sử proxy A gọi một proxy B trên cùng thread, bên trong lời gọi B thì **currentProxy()** trỏ tới **proxy của invocation B đang hoạt động**, không nhất thiết là A. Khi B kết thúc, Spring khôi phục trạng thái proxy trước đó. Code tự ý cast hoặc lưu currentProxy() mà không hiểu ngữ cảnh lồng nhau có thể dùng sai loại proxy.

Vì vậy currentProxy() không tương đương **“lấy proxy của chính class này ở mọi thời điểm”**. Ý nghĩa chính xác của nó là **proxy đang được expose bởi invocation đang hoạt động trên thread hiện tại**.

## 11. Hướng thay thế được ưu tiên: tách thành hai Spring beans

Cách giải quyết ít coupling nhất là **không tự gọi vào method cần AOP trong cùng target**. Thay vào đó, tạo một bean thực hiện method đó, rồi inject bean này vào bean điều phối.

Ví dụ một thiết kế khác cho nghiệp vụ đơn hàng:

~~~text
OrderOrchestrator bean
         |
         | constructor injection
         v
OrderValidation bean/proxy
         |
         +-- @Before advice phù hợp
         |
         v
OrderValidationImpl.validate(...)
~~~

Code Java minh họa đầy đủ các lớp chính:

**src/main/java/demo/ValidationApi.java**

~~~java
package demo;

public interface ValidationApi {
    void validate(String id);
}
~~~

**src/main/java/demo/ValidationService.java**

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class ValidationService implements ValidationApi {

    @Override
    public void validate(String id) {
        System.out.println("VALIDATE " + id);
    }
}
~~~

**src/main/java/demo/OrderOrchestrator.java**

~~~java
package demo;

import org.springframework.stereotype.Service;

@Service
public class OrderOrchestrator {

    private final ValidationApi validation;

    public OrderOrchestrator(ValidationApi validation) {
        this.validation = validation;
    }

    public void process(String id) {
        System.out.println("PROCESS " + id);
        validation.validate(id);
    }
}
~~~

**src/main/java/demo/ValidationAspect.java**

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ValidationAspect {

    @Before("execution(* demo.ValidationService.validate(..))")
    public void beforeValidation() {
        System.out.println("ADVICE validate");
    }
}
~~~

Các lớp trên là **phương án kiến trúc thay thế**, không phải các file cần chèn đồng thời vào project **spring-aop-current-proxy** để tái tạo đúng stdout ở mục 5; nếu bổ sung chúng vào cùng package demo, context sẽ phát hiện thêm beans và advisor.

Với một context có **@ComponentScan("demo")** và **@EnableAspectJAutoProxy** (không cần exposeProxy), khi lấy **OrderOrchestrator** từ Spring và gọi **process("A-1")** qua bean, các dòng logic kỳ vọng là:

~~~text
PROCESS A-1
ADVICE validate
VALIDATE A-1
~~~

Vì sao tốt hơn:

1. **OrderOrchestrator** phụ thuộc vào **ValidationApi**, một hợp đồng Java thông thường.
2. **ValidationService** là một Spring bean độc lập và có thể được proxy khi pointcut khớp.
3. Lời gọi **validation.validate(...)** từ orchestrator đi qua dependency được inject, tức Spring có cơ hội cung cấp **AOP proxy**.
4. Không dùng AopContext, không cần exposeProxy và không phụ thuộc vào ThreadLocal.
5. Unit test có thể truyền fake **ValidationApi** qua constructor mà không khởi động Spring.
6. Trách nhiệm nghiệp vụ và điểm cắt Spring AOP được phân chia rõ ràng.

Đây là lời giải nên ưu tiên ngay cả khi cần thêm một bean hoặc một interface nhỏ.

## 12. Self-injection: một lựa chọn khác nhưng cần thận trọng

Một cách khác là để service nhận tham chiếu tới chính **interface của bean đó** được Spring quản lý, rồi gọi qua tham chiếu này thay cho **this**. Ví dụ **minh họa ý tưởng**:

~~~java
@Service
public class ExampleService implements ExampleApi {

    private final ExampleApi self;

    public ExampleService(@Lazy ExampleApi self) {
        this.self = self;
    }

    @Override
    public void outer() {
        self.inner();
    }

    @Override
    public void inner() {
        // ...
    }
}
~~~

Đoạn fragment trên cần có interface **ExampleApi** và import **@Service**, **@Lazy**, cùng AOP configuration; không phải file thuộc project runnable mục 5. **@Lazy** tại injection point có thể khiến Spring tạo một lazy-resolution proxy rồi resolve bean sau; cách này cần kiểm thử kỹ.

Ưu điểm so với AopContext là không cần gọi static API và không yêu cầu exposeProxy. Tuy nhiên nó đưa **self-reference** vào dependency graph, có thể phát sinh phụ thuộc vòng hoặc các tình huống khởi tạo tinh vi; cũng cần nhận thức rằng bean có thể được proxy theo interface hoặc class. Spring documentation xem đây là một lựa chọn thay thế, nhưng cách **refactor để tránh self-invocation** vẫn sạch hơn.

**Không** tự động thêm self-injection vào mọi service. Đặc biệt, vòng phụ thuộc constructor trực tiếp không được coi là hợp lệ chỉ vì muốn advice chạy cho method nội bộ.

## 13. Một tình huống quan trọng: @Transactional và các advice khác

Spring cũng sử dụng proxy cho nhiều tính năng như declarative transactions, security, caching hoặc custom aspect. Hệ quả self-invocation có thể tương tự khi tính năng ở **proxy mode**:

~~~java
public void outer() {
    this.innerTransactional();
}
~~~

Nếu **innerTransactional()** chỉ được áp dụng transaction advice khi đi qua proxy, lời gọi **this.innerTransactional()** không được proxy advice xử lý như một invocation độc lập. Tuy nhiên, cần phân biệt:

- Nếu **outer()** đã có transaction hoặc advice, lời gọi nội bộ có thể **tiếp tục chạy trong transaction/context đang tồn tại**. “Advice không chạy thêm” không đồng nghĩa “không có transaction nào”.
- Nếu dùng **currentProxy()** để gọi innerTransactional qua proxy, advisor có thể được xét lại; kết quả còn phụ thuộc transaction propagation, annotation metadata, loại proxy và phương thức có interceptable hay không.
- Không nên khuyến nghị bật exposeProxy chỉ để sửa một transaction boundary sai kiến trúc; tách method sang bean khác, xem xét transaction service boundary và test kết quả thực tế.

Ví dụ này **chỉ giải thích hệ quả của ranh giới Spring proxy**, không thay thế tài liệu cấu hình transaction manager hoặc quy tắc của @Transactional.

## 14. Với JDK proxy và CGLIB, currentProxy() khác nhau thế nào?

**AopContext.currentProxy()** trả về **đối tượng proxy thực tế** của invocation, nên kiểu cast hợp lệ phụ thuộc cách Spring tạo proxy.

### 14.1. JDK dynamic proxy

Khi target có interface và Spring chọn JDK proxy, object trả về triển khai những interface mà proxy công bố. Trong ví dụ mục 5, dùng:

~~~java
Operations proxy = (Operations) AopContext.currentProxy();
proxy.inner("X");
~~~

là đúng. Không mặc định cast sang **OperationsImpl**.

JDK dynamic proxy chỉ expose các method thuộc những interface đã được proxy; method public chỉ có trên implementation không tự được công bố bởi interface proxy đó.

### 14.2. CGLIB class-based proxy

Khi dùng:

~~~java
@EnableAspectJAutoProxy(
    proxyTargetClass = true,
    exposeProxy = true
)
~~~

Spring có thể tạo subclass proxy. currentProxy() lúc này có thể được cast theo target class nếu class và cơ chế proxy cho phép; nhưng protected/package-visible còn tuân theo Java visibility, final/private không thể override, và self-invocation vẫn không đi qua proxy.

Không cố khắc phục một method không thể advice chỉ bằng việc bật **exposeProxy**. Nếu method là private hoặc final trên class-based proxy, chuyển receiver thành proxy cũng không làm phép override bất khả thi trở thành hợp lệ.

## 15. Những lỗi thường gặp

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra hoặc xử lý |
| --- | --- | --- |
| **AopContext.currentProxy()** ném IllegalStateException | exposeProxy chưa bật hoặc không có invocation phù hợp trên thread hiện tại | Kiểm tra @EnableAspectJAutoProxy(exposeProxy=true), đường gọi qua proxy và thread |
| Bean là AOP proxy nhưng currentProxy() vẫn thất bại | Có proxy không đồng nghĩa proxy đang được expose | Xác minh cấu hình exposeProxy và thời điểm gọi |
| Chạy được qua Spring, lỗi khi unit test bằng new | Test dùng target raw thay vì Spring-managed proxy | Test logic thuần không phụ thuộc AopContext, hoặc dùng integration test qua context |
| ClassCastException khi cast currentProxy() | JDK proxy không phải subclass của implementation | Cast theo interface proxy thực sự triển khai |
| currentProxy() ở async task thất bại | ThreadLocal không tự truyền sang thread khác | Đặt AOP boundary phù hợp ở thread thực thi hoặc refactor |
| Thấy proxy nhưng lời gọi inner không chạy advice | this.inner() bỏ qua proxy, hoặc pointcut không match | Kiểm tra receiver, pointcut, method visibility và finality |
| currentProxy() được trả về nhưng advice không chạy | Lời gọi mới qua proxy không có advisor phù hợp | Kiểm tra method có match và interceptable không |
| StackOverflowError sau khi dùng currentProxy | Lặp lại lời gọi proxy vào chính entry method | Phân tích call graph, tránh recursion không có điều kiện dừng |
| Advice bị chạy nhiều lần ngoài dự kiến | Gọi lại method qua proxy hoặc nested invocations tạo thêm advice | Đếm proxy entry, xem thứ tự và điều kiện advisor |
| exposeProxy bật nhưng target không được proxy | Không có advisor phù hợp hoặc bean không phải Spring-managed | Kiểm tra bean registration, @Aspect, pointcut |
| Khởi tạo bean lỗi khi self-injection | Vòng dependency hoặc bean chưa sẵn sàng | Refactor thành hai bean độc lập, không dựa vào vòng constructor |
| Dựa vào currentProxy làm trạng thái request | AopContext chỉ gắn với invocation/thread, không phải request context | Dùng request scope hoặc context chuyên dụng nếu thật sự cần |

## 16. Checklist khi bắt buộc cân nhắc currentProxy()

Trước khi chấp nhận giải pháp này, hãy tự trả lời:

1. **Tại sao không thể tách method cần advice sang bean khác?** Nếu có thể refactor, ưu tiên làm thế.
2. **Bean có luôn được lấy qua Spring ApplicationContext không?** Nếu có đường code tự tạo target raw, currentProxy không đáng tin.
3. **Có bảo đảm caller đi qua AOP proxy trước khi method gọi currentProxy() không?** Mặc định interface method nên được expose rõ.
4. **Proxy đã bật exposeProxy chưa?** Bật thuộc tính này không tự tạo advisor hoặc bean.
5. **Code có chạy cùng thread và trong thời gian invocation đang hoạt động không?** Đừng phụ thuộc vào async ThreadLocal propagation.
6. **Kiểu cast có phù hợp JDK/CGLIB không?** Dùng interface proxy công bố khi có thể.
7. **Method được gọi lại có interceptable và pointcut match không?** Private/final hoặc self-invocation khác có thể tiếp tục bỏ qua advice.
8. **Có nguy cơ gọi đệ quy cùng entry method không?** Xem call graph, đặc biệt với @Around, retry và transaction advice.
9. **Có integration test kiểm chứng cả trường hợp bật/tắt exposeProxy không?** Dùng assertion về advice thực tế, không chỉ isAopProxy.
10. **Có cần benchmark nếu lượng gọi rất lớn không?** Expose proxy có chi phí ThreadLocal; đo trong bối cảnh ứng dụng thật.

Nếu vẫn buộc phải dùng, giới hạn đường gọi dựa vào currentProxy vào một chỗ có giải thích rõ ràng, không phổ biến thành coding convention cho toàn bộ service layer.

## 17. Câu hỏi tự đánh giá

**Câu 1. Vì sao this.inner() không chạy @Before cho inner()?**

Vì **this** là target và lời gọi nội bộ không tạo một lời gọi mới đi qua Spring AOP proxy. Advice của inner không được xét riêng cho lời gọi đó.

**Câu 2. Có Spring AOP proxy rồi, tại sao AopContext.currentProxy() vẫn có thể ném lỗi?**

Vì proxy hiện tại phải **được expose** và method phải chạy trong một **AOP invocation trên cùng thread**. Có proxy trong context không đủ.

**Câu 3. @EnableAspectJAutoProxy(exposeProxy = true) có khiến mọi bean thành proxy không?**

Không. Spring vẫn cần các bean được đăng ký và advisor/pointcut phù hợp để tạo proxy tương ứng.

**Câu 4. Với JDK proxy, nên cast kết quả currentProxy() về kiểu nào?**

Về một **interface thực sự được proxy triển khai**, ví dụ **Operations**, không cast tùy tiện về **OperationsImpl**.

**Câu 5. Có nên dùng currentProxy() từ constructor hoặc @PostConstruct không?**

Không mặc định. Những callback đó không bảo đảm đang ở trong một AOP proxy invocation có exposeProxy; còn có rủi ro bean chưa khởi tạo hoàn tất.

**Câu 6. currentProxy() có dùng được trong thread mới tạo từ method đang được proxy không?**

Thread mới không tự nhận ThreadLocal proxy của thread gọi; cần có invocation riêng trên thread ấy hoặc thay thiết kế.

**Câu 7. Vì sao refactor thành hai Spring beans được ưu tiên?**

Vì lời gọi tới dependency được inject có thể đi qua proxy một cách tường minh, giảm coupling với static Spring AOP API, dễ test và không yêu cầu exposeProxy.

**Câu 8. Khi gọi currentProxy().inner(), liệu advice chắc chắn chạy không?**

Không. Method phải có advisor/pointcut khớp, interceptable theo JDK/CGLIB và được gọi qua đúng proxy; exposeProxy chỉ tạo điều kiện **lấy proxy hiện tại**.

**Câu 9. Khi proxy A gọi proxy B lồng nhau, currentProxy() trả proxy nào trong B?**

Nó trả proxy đang được expose bởi **invocation hiện tại** trên thread, tức thường là B trong lúc B hoạt động, rồi Spring khôi phục proxy trước khi B kết thúc.

**Câu 10. CGLIB có xóa vấn đề self-invocation không?**

Không. Class-based proxy không khiến lời gọi trực tiếp **this.method()** trong target tự quay lại Spring AOP proxy.

## 18. Tài liệu Spring Framework chính thức

- **Spring Framework 6.2 — Proxying Mechanisms và self-invocation:** https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- **Spring Framework 6.2.12 — AopContext Javadoc:** https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/aop/framework/AopContext.html
- **Spring Framework — @AspectJ Support:** https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- **Spring Framework — JDK dynamic proxies và CGLIB:** https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- **Spring Framework — Annotation-based AOP configuration:** https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/autoproxy.html

**Kết luận:** **AopContext.currentProxy()** là lối truy cập proxy hiện tại để xử lý một số lời gọi nội bộ trong Spring AOP khi **exposeProxy=true**. Nó có tác dụng ở đúng invocation, thread, proxy type và pointcut, nhưng làm code lệ thuộc chặt vào Spring AOP. **Không dùng làm giải pháp mặc định cho self-invocation**; ưu tiên tách chức năng cần advice sang một Spring bean khác và gọi qua dependency injection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invocation-boundaries-006">Lời gọi từ constructor hoặc callback khởi tạo trước khi proxy sẵn sàng</a>

<details>
<summary>Xem chi tiết</summary>

# Lời gọi từ constructor hoặc callback khởi tạo trước khi Spring AOP proxy sẵn sàng

## 1. Vì sao method có @Aspect advice nhưng chạy lúc khởi tạo lại không được chặn?

Trong Spring AOP, advice chỉ có cơ hội chạy khi một lời gọi method **đi qua Spring AOP proxy** có advisor phù hợp. Trong quá trình tạo một Spring bean, có một khoảng thời gian mà **target object đã tồn tại nhưng proxy cuối cùng chưa được hình thành hoặc chưa được công bố cho các bean khác sử dụng**.

Vì vậy, một lời gọi method từ constructor, `@PostConstruct`, `InitializingBean.afterPropertiesSet()` hoặc custom init method của **chính bean đang được khởi tạo** sẽ không tự đi qua proxy của bean ấy. Nếu method đó có `@Before`, `@Around`, `@Transactional` hay `@Cacheable` được áp dụng qua Spring proxy, không nên mong cơ chế ấy hoạt động chỉ vì method được gọi ngay trong lifecycle hook.

Ví dụ:

```java
public class ReportService {
    public ReportService() {
        generate(); // lời gọi trên target đang được xây dựng
    }

    public void generate() {
        // Có thể khớp pointcut của một Spring @Aspect.
        // Nhưng constructor không gọi qua Spring proxy.
    }
}
```

Đây là đoạn minh họa, không phải chương trình độc lập. Dù Spring có thể tạo proxy cho `ReportService` **sau khi khởi tạo**, điều đó không thay đổi các lời gọi đã xảy ra trước đó.

**Phạm vi bài:** Ranh giới giữa bean lifecycle và Spring AOP theo cơ chế proxy. Ta tập trung vào Spring Framework, không mở rộng sang constructor weaving/AspectJ compiler độc lập. Những vấn đề được đề cập đều gắn trực tiếp với thời điểm Spring tạo proxy, lời gọi nội bộ và việc khởi động ứng dụng.

### Sau bài học, bạn có thể

1. Mô tả thứ tự tạo Spring bean từ constructor đến proxy hoàn chỉnh.
2. Giải thích vì sao các lời gọi từ `@PostConstruct`, `afterPropertiesSet()` và `initMethod` không nhận advice trên chính bean đó.
3. Phân biệt “callback được chạy sau khi dependencies đã được tiêm” với “callback được gọi qua AOP proxy”.
4. Thiết kế bước startup cần AOP bằng một bean điều phối gọi **bean khác qua proxy** sau khi context đã sẵn sàng.
5. Kiểm chứng hành vi bằng ví dụ Java 17 và Spring Framework 6.1 với stdout xác định.
6. Nhận ra các trường hợp ngoại lệ đáng chú ý: gọi sang dependency đã proxy, khởi tạo bean quá sớm, circular reference và callback vào context chưa hoàn chỉnh.

## 2. Trình tự thông thường của một Spring bean có AOP proxy

Với một singleton bean thông thường được tạo qua `ApplicationContext`, có thể hình dung trình tự:

```text
Spring đăng ký bean definitions và BeanPostProcessors
             |
             v
[1] Instantiation: gọi constructor / @Bean factory method
             |
             v
[2] Dependency injection, *Aware callbacks và xử lý thuộc tính
             |
             v
[3] BeanPostProcessor.postProcessBeforeInitialization(...)
       + xử lý @PostConstruct khi có processor tương ứng
             |
             v
[4] InitializingBean.afterPropertiesSet()
             |
             v
[5] Custom init method: @Bean(initMethod=...), init-method...
             |
             v
[6] BeanPostProcessor.postProcessAfterInitialization(...)
       + Spring AOP auto-proxy creator có thể bọc target bằng proxy
             |
             v
[7] Container công bố bean/proxy đã khởi tạo
             |
             v
[8] Bean khác lấy bean từ Spring và gọi method qua proxy
             |
             v
       @Before / @Around / target / @After...
```

Đây là **đường đi phổ biến, giản lược để học**. Spring có nhiều BeanPostProcessor và các trường hợp đặc biệt như early proxy references, FactoryBean, scoped proxy, circular dependencies hay custom target source. Những trường hợp đó có thể bổ sung bước, nhưng không làm thay đổi nguyên tắc cốt lõi: **gọi trực tiếp trên `this` không phải gọi qua Spring AOP proxy**.

### Thời điểm xử lý các callback

| Vị trí | Đối tượng nhận lời gọi | Có được Spring AOP advice cho method của chính bean không? | Lý do |
| --- | --- | --- | --- |
| Constructor của target | Target đang được xây dựng | **Không** | Proxy của bean chưa sẵn sàng; Spring AOP theo proxy cũng không advice constructor execution. |
| `@PostConstruct` trên target | Raw target qua lifecycle processor | **Không** đối với lời gọi trực tiếp trên chính bean | Callback chạy ở giai đoạn before-initialization, trước khi auto-proxy hoàn tất. |
| `InitializingBean.afterPropertiesSet()` | Raw target | **Không** | Đây là init callback của target, không phải lời gọi method từ client thông qua proxy. |
| `@Bean(initMethod = "...")` | Raw target | **Không** | Spring gọi init method trong bước khởi tạo target. |
| Gọi `this.otherMethod()` bên trong một init callback | Chính target | **Không** | Self-invocation đi thẳng đến target, bypass proxy. |
| Gọi method trên **dependency bean khác** đã được Spring cấp proxy | Proxy của dependency | **Có thể có** | Nếu dependency đã được proxy và lời gọi khớp advisor, lời gọi đi qua proxy của **bean khác**. |
| Bean điều phối gọi service được inject sau khi context refresh | Proxy của service | **Có thể có** | Proxy service đã được tạo, lời gọi đi qua object Spring cấp và match pointcut. |

**Điểm cần nhớ:** Câu “init callback chạy trước khi proxy sẵn sàng” nói về **proxy của bean đang khởi tạo**. Nó không có nghĩa *mọi* dependency đều chưa được proxy. Nếu target A đã inject B (một Spring bean proxy hoàn chỉnh) thì A có thể gọi B qua proxy ngay trong `@PostConstruct` của A. Dù vậy, gọi network/database trong lúc context còn khởi tạo có thể đem tới rủi ro về startup order và trạng thái tài nguyên.

## 3. Constructor: tại sao advice không chạy?

### 3.1. Constructor không phải method join point được Spring AOP proxy chặn

Spring AOP hỗ trợ **method execution join points trên Spring-managed beans qua proxy**. Khai báo:

```java
@Aspect
public class TraceAspect {
    @Before("execution(* com.example.service..*.*(..))")
    public void beforeMethod() {
        System.out.println("advice");
    }
}
```

không biến một lời gọi constructor thành method invocation qua proxy. Spring phải **tạo target trước**, sau đó mới có target để proxy.

CGLIB tạo subclass dùng làm proxy và thường sử dụng Objenesis để tránh gọi lại constructor target khi tạo proxy. Vì vậy, không được kết luận “CGLIB proxy sẽ cho constructor chạy qua advice” hoặc “mỗi bean sẽ luôn có hai lần gọi constructor”. Spring AOP theo proxy không thực hiện constructor advice theo nghĩa AspectJ weaving.

### 3.2. Gọi method từ constructor cũng không phải gọi qua proxy

```java
public class InvoiceService {
    public InvoiceService() {
        processInvoice("startup"); // tương đương this.processInvoice(...)
    }

    public void processInvoice(String source) {
        System.out.println(source);
    }
}
```

Dù `processInvoice` là public, non-final và khớp `@Before` pointcut, lời gọi trên `this` trong constructor đang chạy trên **target chưa proxy**. Khi bean được lấy từ Spring sau đó và client gọi `proxy.processInvoice(...)` thì advice mới có cơ hội chạy.

Ngoài vấn đề AOP, việc gọi method có thể override từ constructor còn dễ lộ đối tượng chưa xây dựng hoàn chỉnh. Trong Spring, các dependency tiêm bằng `@Autowired`/`@Value` sau khi constructor chạy chưa sẵn sàng ở thời điểm đó. Constructor nên dùng để khởi tạo trạng thái nội tại, không để kích hoạt nghiệp vụ cần AOP hoặc dependency đã inject.

## 4. @PostConstruct: đã inject dependency chưa đồng nghĩa có proxy của chính mình

### 4.1. Bản chất @PostConstruct trong Spring

Trong Spring Framework 6.x, `@PostConstruct` đến từ package `jakarta.annotation`. Khi hạ tầng xử lý annotation lifecycle được đăng ký, method mang annotation chạy **sau dependency injection** nhưng **trước việc khởi tạo hoàn tất**, ở giai đoạn before-initialization.

```java
import jakarta.annotation.PostConstruct;

public class OrderService {
    @PostConstruct
    public void initialize() {
        saveOrder("from-init"); // self-invocation trên raw target
    }

    public void saveOrder(String id) {
        // Method này có thể khớp @Around/@Before của Spring AOP.
    }
}
```

Đây là đoạn minh họa. Nếu advice trông như không chạy đối với lời gọi `saveOrder` trong `initialize`, điều đó **phù hợp** với Spring proxy semantics. Không nên chỉ sửa pointcut, đổi từ JDK proxy sang CGLIB hoặc tăng `@Order` với kỳ vọng thay đổi ranh giới đó.

### 4.2. @PostConstruct trên chính method được advise thì sao?

Giả sử method vừa có annotation lifecycle vừa có annotation được pointcut nhắm đến:

```java
@PostConstruct
public void setup() {
    System.out.println("setup");
}
```

Spring gọi `setup()` thông qua lifecycle mechanism trên raw bean. Spring không phát lời gọi đó qua AOP proxy chỉ vì method public hoặc có annotation riêng.

Một lời gọi **sau này qua proxy** đến cùng method (nếu API cho phép, có advisor khớp và method proxy được) là tình huống khác; có thể nhận advice. Nhưng không nên thiết kế `@PostConstruct` như API nghiệp vụ để khách hàng gọi lại. Tách method lifecycle và method nghiệp vụ rõ ràng hơn, đồng thời giữ init idempotent nếu có thể.

### 4.3. Gọi dependency khác trong @PostConstruct là trường hợp khác

```java
public final class InitCoordinator {
    private final ReportService reportService;

    public InitCoordinator(ReportService reportService) {
        this.reportService = reportService; // Spring có thể inject proxy
    }

    @PostConstruct
    public void initialize() {
        reportService.generate(); // lời gọi trên dependency, không phải this
    }
}
```

Đây là snippet mô tả, với giả định Spring đăng ký `InitCoordinator` và `ReportService`, đồng thời dependency đã được proxy. Lời gọi `reportService.generate()` **có thể** nhận advice của `ReportService` ngay cả lúc `InitCoordinator` đang ở `@PostConstruct`. Tuy vậy, khác biệt quan trọng là **target bean của lời gọi advice không phải bean đang tự khởi tạo**.

Nếu logic đòi cả ứng dụng đã refresh, transaction infrastructure đã sẵn sàng và các service đang ở trạng thái ổn định, gọi vào dependency ngay trong `@PostConstruct` không phải lựa chọn tốt nhất. Một bước điều phối sau khi context đã khởi động thường dễ kiểm soát hơn.

## 5. InitializingBean và custom init method: cùng một nguyên tắc

Spring có thể chạy nhiều cơ chế khởi tạo cho một bean:

1. `@PostConstruct`.
2. `InitializingBean.afterPropertiesSet()`.
3. Custom init method cấu hình qua `@Bean(initMethod="...")` hoặc cấu hình tương đương.

Nếu một bean khai báo cả ba cơ chế với **ba method khác nhau**, thứ tự trên là thứ tự chuẩn. Tất cả đều là **initialization callbacks trên raw target**.

```java
public class CleanupService implements InitializingBean {
    @PostConstruct
    public void postConstruct() {
        process("postConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        process("afterPropertiesSet");
    }

    public void customInit() {
        process("customInit");
    }

    public void process(String phase) {
        System.out.println(phase);
    }
}
```

Đây là đoạn minh họa chưa gồm import và bean registration; ví dụ đầy đủ ở phần 7. Các lời gọi `process(...)` trên `this` đều bỏ qua proxy của `CleanupService`. Việc `InitializingBean` là một interface của Spring không làm init callback trở thành AOP invocation.

**Không nhầm callback khởi tạo với callback sau khi context refresh:** Method `afterPropertiesSet()` có nghĩa dependency properties đã được thiết lập trên bean, không có nghĩa mọi bean, advisor hoặc tài nguyên toàn ứng dụng đã ở trạng thái sẵn sàng.

## 6. Gọi method sau khi proxy sẵn sàng: ranh giới cần thiết

### 6.1. Gọi qua Spring-managed reference

```text
Trong constructor / @PostConstruct của Service:
    this.perform()
        |
        v
    target.perform()       => không có AOP proxy trong đường gọi

Sau khi context refresh, một bean khác đã inject Service:
    startupRunner -> serviceProxy.perform()
                       |
                       v
                   @Before/@Around...
                       |
                       v
                   target.perform()  => có advice nếu pointcut khớp
```

Một sự kiện context refresh có thể dùng làm **điểm điều phối** sau khi hạ tầng singleton đã được khởi tạo. Với Spring Framework thuần, có thể đăng ký một bean `ApplicationListener<ContextRefreshedEvent>` nhận proxy của service làm dependency và gọi service qua proxy. Với Spring Boot, `ApplicationRunner`/`CommandLineRunner` hoặc `ApplicationReadyEvent` thường được dùng cho tác vụ khởi động sau khi context được refresh, nhưng thời điểm cụ thể và tính thích hợp còn phụ thuộc việc cần chờ runner chạy xong hay chưa.

### 6.2. Hai lỗi có vẻ giống nhau

| Triệu chứng | Nguyên nhân | Cách xác định |
| --- | --- | --- |
| Aspect không chạy trong `@PostConstruct` của service, nhưng chạy khi controller gọi service | Lời gọi nội bộ trước proxy | Thực nghiệm cùng method trước/sau context refresh. |
| Aspect không chạy khi method A gọi `this.methodB()` **sau khi startup đã xong** | Self-invocation đi trên target, không qua proxy | Di chuyển methodB sang bean cộng tác và gọi qua dependency Spring. |

Điểm thứ hai quan trọng: **sau khi proxy được tạo, self-invocation vẫn bypass advice**. Thời điểm khởi tạo làm lỗi dễ xuất hiện hơn, nhưng bản chất proxy boundary luôn quyết định.

### 6.3. Không dùng AopContext.currentProxy() trong constructor để “lấy proxy sớm”

`AopContext.currentProxy()` chỉ có ý nghĩa khi đang thực thi một lời gọi **qua proxy được cấu hình exposeProxy**, ở luồng thực thi tương ứng. Constructor hoặc init callback thông thường của chính bean không ở trong lời gọi AOP proxy đó. Dựa vào API này có thể gây `IllegalStateException` hoặc làm mã gắn chặt vào Spring AOP.

Cũng không nên lưu `this` của target vào một static field hoặc phát tán reference này để “khởi động sớm”: client có thể giữ **raw target reference** và mãi không đi qua proxy, tạo lỗi rất khó truy vết.

## 7. Ví dụ chạy được: constructor → @PostConstruct → afterPropertiesSet → custom init → ContextRefreshedEvent

Ví dụ này dùng **Java 17, Spring Framework 6.1.14, Gradle**. Nó chứng minh bằng output rằng **cùng một method** chỉ được advice khi được gọi qua proxy sau khi bean đã hình thành.

Project Spring Framework thuần có năm lớp Java. Không cần Spring Boot, web server hay database.

### 7.1. Cấu trúc thư mục

```text
spring-aop-init-boundary/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/lifecycle/
    ├── StartupService.java
    ├── StartupAspect.java
    ├── StartupCaller.java
    ├── AppConfig.java
    └── DemoApplication.java
```

### 7.2. Cấu hình build

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-init-boundary'
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
    implementation 'jakarta.annotation:jakarta.annotation-api:2.1.1'
}

application {
    mainClass = 'demo.lifecycle.DemoApplication'
}
```

- `spring-context` cung cấp IoC container, `ApplicationListener`, `ContextRefreshedEvent` và Java-based configuration.
- `spring-aop` cung cấp AOP proxy infrastructure.
- `aspectjweaver` cung cấp những thành phần cần để Spring diễn giải `@Aspect` pointcut expression.
- `jakarta.annotation-api` cung cấp `@PostConstruct` trên Java 17; Spring context đăng ký hạ tầng xử lý annotation khi dependency tương ứng có mặt.

### 7.3. Target có bốn lời gọi cùng một method trong quá trình khởi tạo

`src/main/java/demo/lifecycle/StartupService.java`:

```java
package demo.lifecycle;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.InitializingBean;

public class StartupService implements InitializingBean {

    public StartupService() {
        System.out.println("constructor");
        perform("constructor");
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("postConstruct");
        perform("postConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        System.out.println("afterPropertiesSet");
        perform("afterPropertiesSet");
    }

    public void customInit() {
        System.out.println("customInit");
        perform("customInit");
    }

    public void perform(String origin) {
        System.out.println("target:" + origin);
    }
}
```

Các method lifecycle gọi `perform` trực tiếp trên target. Method `perform(String)` là public và non-final, nên có thể được CGLIB advice khi client thực sự gọi qua proxy sau đó. Biến `origin` giúp ta biết lời gọi bắt nguồn ở giai đoạn nào.

### 7.4. Spring @Aspect theo dõi mọi lần gọi perform qua proxy

`src/main/java/demo/lifecycle/StartupAspect.java`:

```java
package demo.lifecycle;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class StartupAspect {

    @Before("execution(public void demo.lifecycle.StartupService.perform(String))")
    public void beforePerform(JoinPoint joinPoint) {
        String origin = (String) joinPoint.getArgs()[0];
        System.out.println("advice:" + origin);
    }
}
```

`StartupAspect` được Spring xử lý vì lớp có `@Aspect` và sẽ được đăng ký như bean trong `AppConfig`. `@Aspect` không tự động đăng ký bean nếu không có `@Component`, `@Bean` hay cơ chế tương đương.

Pointcut khớp method `StartupService.perform(String)`. Trong cả năm tình huống, method được gọi thực sự, nhưng chỉ những lần gọi **qua Spring proxy** mới kích hoạt advice.

### 7.5. Bean điều phối gọi sau khi context refresh

`src/main/java/demo/lifecycle/StartupCaller.java`:

```java
package demo.lifecycle;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

public class StartupCaller
        implements ApplicationListener<ContextRefreshedEvent> {

    private final StartupService service;

    public StartupCaller(StartupService service) {
        this.service = service;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        System.out.println("contextRefreshed");
        service.perform("contextRefreshed");
    }
}
```

`StartupCaller` không gọi `this.perform(...)`: nó giữ **dependency `StartupService` được Spring cấp**, tức proxy sau khi service được xử lý hoàn chỉnh. Sự kiện `ContextRefreshedEvent` được publish khi context refresh hoàn thành. Demo có một context duy nhất, nên không bị ảnh hưởng bởi trường hợp event truyền lên parent context.

Trong ứng dụng thật, `ContextRefreshedEvent` có thể được phát nhiều lần khi context refresh hoặc có nhiều context. Nếu tác vụ chỉ được phép thực thi một lần, cần idempotency/deduplication và kiểm tra event thuộc context mong muốn. Đừng gắn mọi startup work vào listener mà không cân nhắc đặc tính ấy.

### 7.6. Kích hoạt AOP và đăng ký các bean

`src/main/java/demo/lifecycle/AppConfig.java`:

```java
package demo.lifecycle;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {

    @Bean(initMethod = "customInit")
    public StartupService startupService() {
        return new StartupService();
    }

    @Bean
    public StartupAspect startupAspect() {
        return new StartupAspect();
    }

    @Bean
    public StartupCaller startupCaller(StartupService service) {
        return new StartupCaller(service);
    }
}
```

Cấu hình chủ động ép class-based proxy để ta lấy Spring bean theo kiểu `StartupService`. Không có việc tạo proxy trong constructor `new StartupService()`; Spring xử lý bean trong phần còn lại của lifecycle và áp dụng auto-proxy creator khi phù hợp.

`@Bean(initMethod = "customInit")` khiến Spring gọi `customInit` sau `afterPropertiesSet`. `StartupCaller` được tiêm service bằng container, nên nó nhận Spring-managed reference, không phải một object tự tạo bằng `new`.

### 7.7. Khởi chạy và quan sát

`src/main/java/demo/lifecycle/DemoApplication.java`:

```java
package demo.lifecycle;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class DemoApplication {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {

            StartupService service = context.getBean(StartupService.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            service.perform("external");
        }
    }
}
```

Đặt các file đúng đường dẫn, sau đó chạy từ thư mục chứa `build.gradle`:

```bash
gradle run
```

Nếu dự án đã có Gradle Wrapper, chạy `./gradlew run` (Linux/macOS) hoặc `gradlew.bat run` (Windows).

Các dòng **do code của chương trình in ra** theo đúng thứ tự (không tính log từ Gradle hoặc môi trường):

```text
constructor
target:constructor
postConstruct
target:postConstruct
afterPropertiesSet
target:afterPropertiesSet
customInit
target:customInit
contextRefreshed
advice:contextRefreshed
target:contextRefreshed
proxy=true
advice:external
target:external
```

#### Đọc kết quả

1. Ở constructor, `@PostConstruct`, `afterPropertiesSet()` và `customInit()` đều thấy `target:...` nhưng **không có dòng `advice:...`**. Method `perform` đã chạy, nhưng đường gọi là raw target / `this`.
2. `contextRefreshed` xuất hiện sau bốn callback. Lúc này `StartupCaller` gọi `service.perform` qua Spring-managed reference, nên có `advice:contextRefreshed` trước `target:contextRefreshed`.
3. `proxy=true` xác nhận bean `StartupService` mà `getBean(...)` trả về là Spring AOP proxy.
4. Lời gọi `service.perform("external")` sau khi nhận bean từ container cũng có `advice:external`. Không cần sửa pointcut giữa startup và runtime.
5. Số lần gọi target là **sáu**: bốn lần trong lifecycle và hai lần qua proxy. Số lần advice là **hai**. Chênh lệch này minh chứng điểm chặn phụ thuộc lời gọi qua proxy.

**Lưu ý cách đọc stdout:** Ghi nhận hai dòng `advice:...` không có nghĩa Spring lúc khởi tạo “không nhận ra aspect”. Aspect đã được đăng ký; chỉ những lời gọi khởi tạo trên target không đi qua proxy. Các dòng kết quả giả định AOP được bật như `AppConfig` và các dependency đã được nạp đúng.

## 8. Nếu dùng @Transactional, @Cacheable hoặc @Async thì sao?

Các annotation sau cũng thường hoạt động qua Spring proxy:

| Cơ chế | Lời gọi trực tiếp từ init callback của cùng bean | Rủi ro khi suy luận sai |
| --- | --- | --- |
| Spring `@Aspect` advice | Không đi qua proxy của chính bean | Logging/audit/validation advice không chạy trong init call |
| `@Transactional` với proxy mode | Không đi qua transaction interceptor của chính bean | Không có transaction advice chỉ vì method có annotation |
| `@Cacheable` / `@CacheEvict` với proxy mode | Không đi qua cache interceptor của chính bean | Cache operation không diễn ra như tưởng tượng |
| `@Async` với proxy mode | Không đi qua async interceptor của chính bean | Lời gọi chạy đồng bộ trên thread khởi tạo, không chuyển vào executor |

Ví dụ sai về kỳ vọng:

```java
@PostConstruct
public void init() {
    rebuildCache(); // self-invocation; @Cacheable/@Transactional trên
                    // rebuildCache() không tự được áp dụng qua proxy
}
```

Đó chỉ là snippet minh họa, không có `CacheManager` hoặc transaction manager trong ví dụ chạy ở phần 7. Khi cần giao dịch hoặc caching thật sự trong startup work, hãy đặt nghiệp vụ vào **bean khác đã được Spring proxy** rồi gọi qua dependency tại một thời điểm thích hợp. Hạ tầng transaction/cache tương ứng phải được cấu hình đầy đủ; proxy không tự tạo `TransactionManager` hay `CacheManager`.

**Phân biệt quan trọng:** Tạo transaction bằng API lập trình trực tiếp như `TransactionTemplate` là một cơ chế khác với advice của `@Transactional`; có thể thực thi nếu dependency/hạ tầng phù hợp đã sẵn sàng, nhưng không làm self-invocation trở thành lời gọi qua proxy.

## 9. Khởi tạo quá sớm: bean không đủ điều kiện auto-proxy

Ngoài lỗi “callback của chính bean không có advice”, Spring còn có tình huống **bean được tạo trước khi các BeanPostProcessor cần thiết đăng ký đầy đủ**.

Ví dụ không nên làm:

```java
@Configuration
class EarlyProcessorConfiguration {

    @Bean
    BeanPostProcessor customProcessor(ExpensiveService service) {
        // Phụ thuộc trực tiếp vào ExpensiveService có thể khiến
        // service được khởi tạo quá sớm trong giai đoạn đăng ký BPP.
        return new CustomBeanPostProcessor(service);
    }
}
```

Đây là snippet giải thích vấn đề, **không phải file Java độc lập** vì `ExpensiveService` và `CustomBeanPostProcessor` chưa được khai báo. BeanPostProcessor cần được dựng rất sớm để có thể xử lý các bean khác. Nếu quá trình đăng ký chúng buộc tạo sớm service, service đó có thể không được mọi BeanPostProcessor (bao gồm AOP auto-proxy creator) xử lý. Spring có thể log cảnh báo dạng:

```text
Bean 'someService' ... is not eligible for getting processed
by all BeanPostProcessors (for example: not eligible for auto-proxying).
```

Đây là **dạng thông báo minh họa**; nội dung chính xác tùy phiên bản. Khác với tình huống init callback trên target, ở đây cả **lời gọi service sau startup** cũng có thể không nhận advice nếu bean đã được tạo quá sớm mà không được proxy thích hợp.

### Cách xử lý

- Tránh để BeanPostProcessor phụ thuộc trực tiếp vào những bean nghiệp vụ cần AOP.
- Khi dùng `@Bean` để khai báo BeanPostProcessor, nên để factory method là `static` và trả về kiểu BeanPostProcessor cụ thể hoặc giao diện `BeanPostProcessor` để Spring phát hiện sớm chính xác.
- Tránh gọi `getBean()` vào service tùy ý từ `BeanFactoryPostProcessor` hoặc code đăng ký hạ tầng quá sớm.
- Đọc cảnh báo `not eligible for auto-proxying`, xác định dependency nào khiến bean khởi tạo trước thời điểm phù hợp.
- Kiểm tra lại `AopUtils.isAopProxy(bean)` sau khi sửa cấu hình.

### Còn circular dependency và early proxy references?

Spring có thể cung cấp **early reference** cho một số tình huống phụ thuộc vòng, và auto-proxy creator có nhánh hỗ trợ early proxy reference trong những điều kiện thích hợp. Vì vậy, không thể phát biểu rằng “mọi early reference nhất định là raw target”, hoặc “bật cho phép circular reference chắc chắn giải quyết được advice”.

Giải pháp ổn định thường là **loại bỏ vòng phụ thuộc và việc phát tán `this` trong constructor/init**. Nếu một singleton A nhận B, B nhận lại A, việc A chưa hoàn tất lifecycle khiến hành vi AOP, transaction và cấu hình BeanPostProcessor khó dự đoán và đôi khi gây lỗi khởi tạo context. Đừng dùng circular reference như một cơ chế gọi advice trong `@PostConstruct`.

## 10. Các lựa chọn chạy startup work sau khi proxy đã hoàn thiện

### 10.1. Một bean điều phối lắng nghe ContextRefreshedEvent

Đây là cách đã có trong ví dụ phần 7. Điều phối viên dùng proxy của service, không gọi tự thân service trong constructor:

```text
ContextRefreshedEvent
       |
       v
StartupCaller (ApplicationListener)
       |
       v
StartupService proxy
       |
       v
Spring AOP advice
       |
       v
StartupService target
```

Nên bảo đảm callback **idempotent**, kiểm tra context identity khi có cây context cha–con và không thực thi vô hạn mỗi lần nhận sự kiện. Nếu khởi chạy ngoài Spring Boot, cách tiếp cận này không đòi Boot-specific runner.

### 10.2. SmartInitializingSingleton

Spring có giao diện `SmartInitializingSingleton` với callback `afterSingletonsInstantiated()` khi hoàn thành việc khởi tạo các singleton không lazy thông thường trong BeanFactory. Đây là điểm có ích khi cần tương tác với các bean đã khởi tạo.

Tuy nhiên:
- Callback nằm trên object mà Spring nhận diện là `SmartInitializingSingleton`; nếu chính object đó bị proxy với JDK interface không expose callback, cần kiểm tra cơ chế phát hiện và gọi callback thực tế. Đừng tùy tiện chuyển callback vào service đã proxy.
- Không phải mọi lazy singleton, prototype, request-scoped bean đều đã được tạo.
- Lời gọi `this.advisedMethod()` **vẫn bypass proxy** dù callback xảy ra muộn hơn.

Cách rõ ràng là để một bean điều phối không cần tự advice thực hiện callback, còn service cần AOP được lấy từ Spring như dependency rồi gọi qua proxy.

### 10.3. Spring Boot: ApplicationRunner, CommandLineRunner và ApplicationReadyEvent

Nếu ứng dụng là Spring Boot:
- `ApplicationRunner` và `CommandLineRunner` chạy sau khi context được refresh và Boot gọi runners.
- `ApplicationReadyEvent` được phát gần cuối startup, sau runners theo lifecycle Boot chuẩn.
- Một runner hoặc listener riêng có thể được inject Spring-managed service proxy và gọi method có advice.

Đây là **cơ chế Boot**, không tự tồn tại trong Spring Framework thuần. Dù gọi tại thời điểm nào, **tự gọi `this.method()` trong cùng target vẫn bỏ qua Spring AOP proxy**. Khi thiết kế công việc dài hoặc blocking ở startup, cân nhắc khả năng ứng dụng chưa sẵn sàng nhận request; đừng vô tình chặn lifecycle cần thiết.

### 10.4. Event listener cũng cần đúng ranh giới bean

`@EventListener` hoặc `ApplicationListener` chỉ là cách Spring giao sự kiện, không cam kết “mọi method của listener sẽ qua AOP proxy”. Nếu method handler nằm trên bean bị JDK proxy, nó có thể cần được expose qua interface hoặc dùng cấu hình proxy phù hợp. Nếu trong handler của service gọi `this.someAdvisedMethod()` thì vẫn là self-invocation.

Để giải thích và kiểm tra dễ nhất, **tách listener/runner thành một bean điều phối** và inject service proxy riêng, như ví dụ `StartupCaller`.

## 11. Bảng lỗi thường gặp

| Triệu chứng | Nguyên nhân Spring AOP thường gặp | Cách sửa/kiểm tra |
| --- | --- | --- |
| Method public có `@Before` nhưng gọi từ constructor không in advice | Target còn đang xây dựng, chưa có đường gọi qua proxy | Đưa nghiệp vụ sang bước sau startup thông qua service proxy |
| `@PostConstruct` gọi method `@Transactional` nhưng không thấy transaction interceptor | Lời gọi `this.method()` trên raw target | Tách transaction method sang bean cộng tác và inject/call qua Spring proxy |
| `afterPropertiesSet()` gọi method `@Cacheable` mà lần sau vẫn phải tính lại | Init call bypass cache proxy | Di chuyển thao tác có cache qua bean proxy, kiểm tra cache manager |
| `@Async` method chạy trên main/startup thread khi gọi trong init | Không đi qua async proxy | Dùng runner/listener gọi bean khác sau khi hạ tầng sẵn sàng |
| Advice chạy sau startup nhưng không chạy ngay trong init | Hành vi proxy lifecycle chuẩn | So sánh stdout và `isAopProxy`; không coi đây là pointcut lỗi |
| Advice không chạy cả sau startup; log `not eligible for auto-proxying` | Bean bị khởi tạo quá sớm trong pha BeanPostProcessor | Sửa dependencies của post-processor, tránh tạo sớm bean nghiệp vụ |
| Gọi từ `@PostConstruct` của bean A sang bean B lại có advice | A đang inject **proxy B** đã hoàn tất | Bình thường; phân biệt owner của callback với bean target được gọi |
| Dùng `AopContext.currentProxy()` trong constructor bị lỗi | Không có active exposed proxy ở thời điểm đó | Không dùng như giải pháp khởi tạo; thiết kế bean điều phối |
| Có proxy CGLIB mà `this.method()` vẫn không có advice | Self-invocation là giới hạn của proxy mechanism | Gọi qua một Spring bean dependency/proxy thực sự |
| Listener chạy lặp sau refresh | Nhiều lần refresh, nhiều context hoặc event propagation | Filter đúng context, dùng trạng thái idempotent |
| Method annotated `@PostConstruct` không chạy | Chưa có Jakarta annotation API hoặc lifecycle processor phù hợp; method/config không hợp lệ | Kiểm tra Jakarta package, dependency, registration và lifecycle setup |

## 12. Quy trình chẩn đoán khi advice biến mất trong startup

Thay vì sửa cấu hình ngẫu nhiên, kiểm tra theo thứ tự:

1. **Lời gọi xảy ra ở đâu?** Constructor, `@PostConstruct`, `afterPropertiesSet`, custom init, event listener hay REST/service invocation sau startup?
2. **Bean nào thực sự được gọi?** Nếu là `this` trên target đang khởi tạo, đó không phải Spring AOP proxy. Nếu gọi dependency khác, kiểm tra dependency ấy có proxy chưa.
3. **Bean có được Spring quản lý không?** Dùng `new` tự tạo ngoài context không qua AOP post-processing.
4. **AOP đã được bật và aspect đã là Spring bean chưa?** Với Spring Framework, đăng ký `@EnableAspectJAutoProxy` trong context thích hợp; với Boot, xem AOP auto-configuration và dependency.
5. **Proxy có thật sự được tạo không?** Lấy bean sau startup từ context và kiểm tra `AopUtils.isAopProxy`; kiểm tra thêm JDK/CGLIB khi cần.
6. **Pointcut và method có khớp không?** Kiểm tra package, annotation, method visibility và giới hạn `final`/`private`. Nếu cùng method được advice khi gọi từ ngoài, vấn đề nằm ở call boundary.
7. **Bean có bị tạo quá sớm không?** Xem log BeanPostProcessor và bean definitions, đặc biệt `@Bean` method khai báo custom processors.
8. **Có circular dependency hoặc early reference không?** Tránh dùng early reference làm cách bảo đảm advice; sửa dependency graph.
9. **Nếu đã chuyển sang event/runner, kiểm tra thời điểm và idempotency.** Sự kiện có chạy nhiều lần? Service có thật sự được inject bằng proxy? Runner có chạy trong đúng context không?

## 13. Kết luận cần nhớ

Trong Spring AOP theo proxy, có hai ranh giới độc lập:

- **Ranh giới lifecycle:** Target được tạo và nhận init callback **trước** khi proxy cho bean đó hoàn tất.
- **Ranh giới lời gọi:** Chỉ method invocation **qua Spring AOP proxy** mới có thể được advice; self-invocation trên `this` bỏ qua advice, kể cả sau khi startup xong.

Constructor, `@PostConstruct`, `InitializingBean.afterPropertiesSet()` và custom init method của bean là những nơi tốt để thiết lập **trạng thái nội bộ của bean**, nhưng không phải nơi thích hợp để kỳ vọng AOP advice trên chính bean đó. Với startup work cần logging, transaction, caching hoặc async advice, hãy dùng **bean điều phối** gọi **Spring-managed service proxy** tại thời điểm lifecycle đáp ứng nhu cầu và có cơ chế kiểm soát chạy lặp.

### Tài liệu Spring chính thức

- [Spring Framework — Customizing the Nature of a Bean](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html): initialization callbacks thực thi trên raw bean, thứ tự `@PostConstruct` → `afterPropertiesSet` → custom init.
- [Spring Framework — Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html): `BeanPostProcessor` trước/sau initialization, auto-proxying và bean khởi tạo quá sớm.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): Spring AOP proxy, self-invocation, JDK/CGLIB và Objenesis.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký `@Aspect` và bật `@EnableAspectJAutoProxy`.
- [Spring Framework — ApplicationContext Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html): `ContextRefreshedEvent` và `ApplicationListener`.
- [Spring Framework — SmartInitializingSingleton Javadoc](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/beans/factory/SmartInitializingSingleton.html): callback sau khởi tạo singleton thông thường.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invocation-boundaries-007">Đối tượng không được Spring quản lý và giới hạn của proxy tự động</a>

<details>
<summary>Xem chi tiết</summary>

# Đối tượng không được Spring quản lý và giới hạn của proxy tự động trong Spring AOP

## 1. Ranh giới quan trọng nhất: Spring AOP chỉ tự bọc Spring bean

Spring AOP hoạt động chủ yếu bằng cách **tạo proxy cho những Spring bean có advisor phù hợp**. `@EnableAspectJAutoProxy` bật cơ chế tìm các aspect/advisor và tự bọc bean trong `ApplicationContext`; nó không đăng ký một bộ chặn toàn cục cho mọi lời gọi Java.

Cùng một class, cùng một method và cùng biểu thức pointcut nhưng kết quả khác nhau khi reference có nguồn gốc khác nhau:

- **Reference lấy từ Spring container:** nếu bean được container xử lý và có advisor phù hợp, người gọi nhận Spring AOP proxy; lời gọi qua reference này có thể kích hoạt advice.
- **Reference tạo bằng `new` ở ngoài container:** đối tượng không tự trải qua quy trình `BeanPostProcessor`/auto-proxy creator của context. Gọi method trên reference đó là lời gọi Java bình thường, không tự kích hoạt Spring AOP advice.
- **Một instance được tạo bằng `new` nhưng trả về từ `@Bean`:** đây vẫn là Spring bean sau khi container đăng ký và xử lý kết quả từ factory method; nó **có thể được proxy**. Chính việc Spring quản lý instance, không phải từ khóa `new` đơn lẻ, xác định ranh giới.
- **Giữ một reference trỏ thẳng tới target bên cạnh proxy Spring:** gọi qua reference target thô vẫn có thể bỏ qua advice; cần gọi qua **proxy** Spring trả về.

Ý chính cần nhớ:

> **Pointcut khớp tên class/method chỉ là điều kiện chọn advice; Spring AOP còn cần một Spring-managed bean và lời gọi đi qua proxy.**

Bài học này chỉ nói về Spring AOP và giới hạn auto-proxy. AspectJ compile-time/load-time weaving là cơ chế khác, không tự xuất hiện chỉ nhờ thêm `@Aspect` hoặc thư viện `aspectjweaver`.

## 2. Spring tạo proxy ở giai đoạn nào?

Khi dùng `@EnableAspectJAutoProxy`, Spring đăng ký auto-proxy creator, tiêu biểu là `AnnotationAwareAspectJAutoProxyCreator`. Thành phần này kế thừa hạ tầng `AbstractAutoProxyCreator`, vốn sử dụng các callback của `BeanPostProcessor` trong vòng đời tạo bean.

Luồng khái quát:

~~~text
             ApplicationContext
                     |
           tạo/nhận instance bean
                     |
          đi qua các BeanPostProcessor
                     |
          có advisor phù hợp không?
                  /       \
                Không     Có
                 |         |
              target     tạo proxy
                 |         |
                 +----+----+
                      |
            container cung cấp reference
                      |
          caller gọi method qua reference
                      |
                [nếu là proxy]
                      |
                 advice chain
                      |
                target.method()
~~~

So sánh với lời gọi nằm ngoài container:

~~~text
new Notifier("manual") --> raw Notifier object --> send()
                                                  |
                                                  v
                                               target code

ApplicationContext.getBean(Notifier.class)
          |
          v
      Spring AOP proxy --> @Before advice --> target.send()
~~~

Auto-proxy creator là thành phần **của Spring container**, không phải một hook được cài vào mọi class của JVM. Khi lập trình viên tự gọi `new Notifier(...)` trong một method thông thường, Spring không biết đối tượng đó cần xử lý như một bean mới. Việc annotation hoặc pointcut nhắc tới `Notifier` không biến đối tượng tự tạo thành Spring bean.

Cần phân biệt hai thời điểm:

1. **Tạo bean:** Spring kiểm tra advisor và có thể phát hành proxy thay cho target. Đây là công việc của hạ tầng auto-proxying.
2. **Gọi method:** caller gọi trên proxy thì interceptor/advice được chạy trước/sau/quanh target theo advisor đã khớp. Đối tượng không được proxy sẽ không tự có interceptor chain.

Không phải cứ là Spring bean là chắc chắn có proxy: còn phải có advisor áp dụng, bean đủ điều kiện xử lý và method có thể được chặn qua kiểu proxy tương ứng.

## 3. Các tình huống thường gặp

| Cách có được đối tượng | Có là Spring bean được xử lý bởi container? | Spring AOP tự áp dụng? |
| --- | --- | --- |
| `context.getBean("notifier")` hoặc inject bean bằng constructor | Có, nếu định nghĩa bean tồn tại trong context đó | Có thể; cần advisor khớp và proxy được tạo |
| `@Component` nằm trong package được component scan | Có | Có thể, khi advice/điều kiện proxy phù hợp |
| `@Bean Notifier notifier() { return new Notifier(...); }` | Có; kết quả factory method đi qua quy trình bean | Có thể; `new` bên trong `@Bean` không loại trừ AOP |
| `new Notifier(...)` trong controller/service/helper mà không đăng ký instance | Không | Không tự được Spring AOP auto-proxy |
| `new Notifier(...)` trong `main` bên ngoài `ApplicationContext` | Không | Không tự được Spring AOP auto-proxy |
| Object do thư viện bên ngoài tự tạo, không đưa vào container | Không, theo cách sử dụng đó | Không được auto-proxy chỉ bởi `@EnableAspectJAutoProxy` |
| Đối tượng do custom factory tạo và trả cho Spring qua một bean definition | Có, nếu Spring thực sự quản lý kết quả factory | Có thể; cần xem vòng đời và advisor |
| Prototype bean tạo bởi `context.getBean(...)` mỗi lần | Có; mỗi instance lấy qua container | Có thể được post-process/proxy khi Spring tạo từng instance |
| Một object chỉ gắn `@Component` nhưng được gọi bằng `new` | Reference tự tạo không qua container | Không; annotation không chuyển quyền sở hữu của object |
| Một bean được proxy nhưng code giữ và gọi reference trực tiếp vào target | Reference target thô đứng ngoài đường gọi proxy | Không chạy advice qua reference target thô |

Bảng xét trường hợp sử dụng **Spring AOP theo proxy**. Một kỹ thuật chủ động tạo proxy bằng API Spring hoặc AspectJ weaving là cấu hình riêng, không phải hành vi tự động của `@EnableAspectJAutoProxy`.

## 4. Ví dụ Java 17+: cùng class, chỉ Spring bean kích hoạt advice

Ví dụ chạy được với Spring Framework thuần; không cần Spring Boot, HTTP server, cơ sở dữ liệu hoặc Java agent.

Cấu trúc:

~~~text
spring-aop-boundary/
├── pom.xml
└── src/main/java/demo/
    ├── Demo.java
    ├── Notifier.java
    └── TraceAspect.java
~~~

Yêu cầu JDK 17+, Maven 3.9+; project dùng phiên bản cố định Spring Framework 6.2.0 và AspectJ Weaver 1.9.22 để có cùng cơ sở khi học.

### 4.1. Maven dependencies

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-boundary</artifactId>
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
                    <mainClass>demo.Demo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp `ApplicationContext` và Java config. `spring-aop` cung cấp auto-proxy utilities và proxy infrastructure. `aspectjweaver` cung cấp API/công cụ phân tích kiểu pointcut `@AspectJ` cho Spring; chỉ có dependency đó **không kích hoạt weaving**.

### 4.2. Cùng một lớp Notifier cho hai đối tượng

Tạo `src/main/java/demo/Notifier.java`:

~~~java
package demo;

public class Notifier {
    private final String label;

    public Notifier(String label) {
        this.label = label;
    }

    public void send() {
        System.out.println("TARGET: " + label);
    }
}
~~~

Class `Notifier` không `final`, method `send()` là `public` và có thể được override. Đây là kiểu dễ tạo class-based proxy. `label` chỉ giúp phân biệt đối tượng được container quản lý và object tự tạo; cả hai đều gọi đúng method `Notifier.send()`.

### 4.3. Aspect có bộ đếm chỉ tăng trong advice

Tạo `src/main/java/demo/TraceAspect.java`:

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    private final AtomicInteger adviceCount = new AtomicInteger();

    @Before("execution(* demo.Notifier.send(..))")
    public void beforeSend() {
        adviceCount.incrementAndGet();
        System.out.println("ADVICE: before send");
    }

    public int getAdviceCount() {
        return adviceCount.get();
    }
}
~~~

Điểm cần quan sát:

- `@Aspect` đánh dấu class aspect bằng cú pháp mà Spring AOP hiểu, **không tự đăng ký thành Spring bean**.
- `@Before("execution(* demo.Notifier.send(..))")` khớp method execution `send()` của `Notifier`. Về mặt tên class/method, cả hai instance đều có cùng chữ ký.
- `beforeSend()` in dòng `ADVICE` và tăng counter. Nếu object tự tạo không kích hoạt advice, bộ đếm không tăng khi gọi nó.
- `AtomicInteger` giúp đọc số lần advice thực thi rõ ràng. Ví dụ chạy tuần tự nên không cần hạ tầng concurrency riêng.

### 4.4. Configuration, lời gọi và phép xác nhận

Tạo `src/main/java/demo/Demo.java`:

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class Demo {
    @Bean
    public Notifier notifier() {
        return new Notifier("managed");
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Demo.class)) {
            Notifier managed = context.getBean(Notifier.class);
            Notifier manual = new Notifier("manual");
            TraceAspect aspect = context.getBean(TraceAspect.class);

            boolean managedProxy = AopUtils.isAopProxy(managed);
            boolean manualProxy = AopUtils.isAopProxy(manual);

            System.out.println("MANAGED_PROXY: " + managedProxy);
            System.out.println("MANUAL_PROXY: " + manualProxy);

            managed.send();
            manual.send();

            int calls = aspect.getAdviceCount();
            System.out.println("ADVICE_COUNT: " + calls);

            if (!managedProxy || manualProxy || calls != 1) {
                throw new IllegalStateException(
                        "Spring AOP proxy boundary verification failed");
            }
            System.out.println("CHECK: PASS");
        }
    }
}
~~~

Cơ chế trong chương trình:

1. `@EnableAspectJAutoProxy(proxyTargetClass = true)` đăng ký auto-proxy creator cho Spring context; class-based proxy phù hợp khi `Notifier` không triển khai interface. Cờ này **không** yêu cầu Spring tự nhận diện mọi object được gọi `new`.
2. `@Bean notifier()` **có `new Notifier("managed")` ở bên trong** nhưng kết quả được trả về cho Spring như một bean. Spring có thể xử lý, bọc proxy và công bố reference proxy cho caller; `AopUtils.isAopProxy(managed)` sẽ là `true`.
3. `@Bean traceAspect()` đăng ký instance `TraceAspect` trong chính context, để Spring tìm advisor từ `@Before`.
4. `new Notifier("manual")` trong `main` **không được đưa qua BeanFactory**, nên `AopUtils.isAopProxy(manual)` là `false`. Nó có cùng class, cùng method `send`, nhưng không có proxy.
5. `managed.send()` đi vào proxy rồi advice, sau đó vào target. `manual.send()` chỉ thực thi mã class; không tự đi qua proxy vì pointcut khớp về tên.
6. Điều kiện kiểm tra xác nhận trạng thái proxy trái ngược và **chỉ một lần advice thực sự chạy**. Một dòng `CHECK: PASS` chứng tỏ ứng dụng đã quan sát đúng ranh giới này.

### 4.5. Chạy và đối chiếu output

Chạy từ thư mục có `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chính mã Java trên in ra, theo thứ tự:

~~~text
MANAGED_PROXY: true
MANUAL_PROXY: false
ADVICE: before send
TARGET: managed
TARGET: manual
ADVICE_COUNT: 1
CHECK: PASS
~~~

Nếu Maven/framework in thêm thông báo ngoài chương trình, hãy tập trung vào bảy dòng vừa nêu. Tại sao kết quả như vậy?

- **`MANAGED_PROXY: true`** chứng minh bean do Spring cấp đã được bọc khi advisor khớp.
- **`MANUAL_PROXY: false`** chứng minh object `new` độc lập không trở thành Spring proxy.
- **`ADVICE: before send`** đứng trước `TARGET: managed`, xác nhận phương thức được chặn qua proxy.
- **`TARGET: manual`** xuất hiện **không có dòng `ADVICE` thứ hai**, dù object thuộc cùng class `Notifier`.
- **`ADVICE_COUNT: 1`** là phép đo từ aspect bean; nếu advice đã chạy cho object `manual` thì số đếm phải tăng.
- **`CHECK: PASS`** chỉ được in khi cả trạng thái proxy và số lần advice đều đúng.

Ví dụ cố tình chọn cùng class/method và chạy hai reference cạnh nhau để loại bỏ giả thuyết “method không khớp pointcut”. Khác biệt được tạo bởi **nguồn gốc và cách gọi object**, không phải do code trong target.

## 5. Ba tình huống dễ nhầm khi thấy từ khóa new

### 5.1. new bên trong @Bean vẫn tạo Spring-managed bean

~~~java
@Configuration
class ServiceConfig {
    @Bean
    Notifier notifier() {
        return new Notifier("created-in-bean");
    }
}
~~~

Đây là **đoạn cấu hình trích riêng** (cần import `@Configuration`/`@Bean` và class `Notifier` như ví dụ). Spring container gọi factory method, nhận instance, thực hiện quá trình bean lifecycle và có thể trả AOP proxy. Việc câu lệnh tạo instance dùng `new` không khiến kết quả trở thành “object ngoài container”.

Vì vậy, không kết luận theo luật “có chữ `new` thì Spring AOP không chạy”. Phải hỏi **ai sở hữu instance sau khi tạo** và **reference nào được caller gọi**.

### 5.2. new bên trong một Spring bean không tự đăng ký object con

Ví dụ minh họa:

~~~java
public class DispatchService {
    public void dispatch() {
        Notifier temp = new Notifier("inner-manual");
        temp.send();
    }
}
~~~

Giả sử `DispatchService` là Spring bean và được proxy, object `temp` vẫn chỉ là đối tượng local do phương thức tự tạo. Spring không tự coi nó là một bean mới hoặc tự gắn AOP proxy vào reference `temp`. Method `send` của nó không được advice bởi Spring AOP chỉ vì object mẹ là Spring bean.

Đây là snippet dùng class `Notifier` đã khai báo; để thành chương trình chạy cần đăng ký `DispatchService` trong context và gọi `dispatch()` ở một entry point. Không được suy rằng Spring tự quản lý toàn bộ object graph mà các bean tự tạo ra.

### 5.3. Một object raw được đăng ký làm bean nhưng code giữ reference cũ

Giả sử một instance đã được tạo thủ công và sau đó **được đăng ký vào container theo cách cho phép Spring post-process nó**. Spring có thể công bố AOP proxy cho instance ấy, trong khi mã ngoài vẫn giữ một biến trỏ trực tiếp tới original:

~~~text
  Object target = new Notifier(...)
          |
          +--> biến rawTarget vẫn trỏ vào target
          |
          +--> Spring nhận instance này như một bean
                         |
                         +--> auto-proxy creator trả AOP proxy
                                            |
                                      managedReference
~~~

Gọi `managedReference.send()` có thể chạy advice; gọi `rawTarget.send()` vẫn đi thẳng vào target. Hai reference **không đồng nhất** chỉ vì chúng liên quan cùng object đích. Để bảo đảm advice, ưu tiên lấy/inject reference do container cung cấp và không giữ target thô ở đường gọi nghiệp vụ.

Không giả định rằng gọi `registerSingleton(...)` vào một BeanFactory đã chạy sẽ có cùng vòng đời/post-processing như một bean definition được đăng ký và tạo bình thường; việc đưa đối tượng sẵn có vào container phải đi theo API/lifecycle phù hợp.

## 6. Các giới hạn liên quan đến ranh giới Spring AOP

### 6.1. Có bean chưa đủ; cần lời gọi đi qua proxy

Ngay cả với Spring-managed bean đã có proxy:

~~~java
public class OrderService {
    public void place() {
        this.confirm();
    }

    public void confirm() {
        System.out.println("confirmed");
    }
}
~~~

Nếu advice chỉ chọn `confirm()` và caller gọi `place()` qua Spring proxy, lời gọi nội bộ `this.confirm()` trong target **không quay trở lại proxy**; advice trên `confirm()` có thể không chạy. Đây là **self-invocation**, một nguyên nhân khác với “đối tượng không được Spring quản lý” nhưng cùng liên quan đến ranh giới đường gọi.

Với Spring AOP, giải pháp thiết kế thông dụng là tách `confirm()` sang một bean riêng và gọi bean đó qua dependency Spring inject. Đừng nhầm rằng đổi JDK proxy sang CGLIB proxy hoặc bật `proxyTargetClass = true` sẽ làm `this.confirm()` đi qua proxy.

### 6.2. Class và method phải phù hợp với proxy

Spring AOP tự động xét bean, nhưng proxy có các giới hạn:

- Với **JDK dynamic proxy**, chỉ các phương thức được expose qua các interface của proxy có thể được gọi qua proxy theo kiểu interface; không cast proxy xuống implementation class.
- Với **CGLIB class proxy**, class `final` không thể subclass; method `final`/`private` không thể được override để intercept qua cơ chế subclass proxy.
- Lời gọi đến **static method**, constructor, field access hoặc object không do Spring quản lý không được Spring AOP proxy tự động chặn như một method execution trên Spring bean.
- Nếu bean không có advisor/pointcut phù hợp, Spring không bắt buộc tạo proxy chỉ vì `@EnableAspectJAutoProxy` đang bật.
- Nếu bean bị tạo quá sớm, ngoài đường BeanPostProcessor bình thường, nó có thể không được auto-proxy theo mong đợi. Việc này cần được chẩn đoán riêng với nguyên nhân `new` ngoài container.

Điểm chung: **cùng tồn tại trong JVM với một Spring AOP application không có nghĩa tất cả lời gọi đều đi qua proxy**.

### 6.3. @Autowired, @Transactional hoặc @Cacheable trên object tự new

Khi code viết:

~~~java
public class Processor {
    // Ví dụ minh họa, class này chưa được Spring đăng ký thành bean.
    @org.springframework.beans.factory.annotation.Autowired
    private Notifier notifier;

    public void process() {
        notifier.send();
    }
}
~~~

Nếu caller tự làm `new Processor()`, các callback inject dependency của Spring không tự chạy cho instance đó. `notifier` có thể vẫn là `null`, dẫn đến `NullPointerException` khi `process()` được gọi. Tương tự, các annotation ứng dụng dựa trên Spring AOP như `@Transactional`/`@Cacheable` (trong chế độ proxy) không tự biến object tự tạo thành proxy.

Đây là **snippet minh họa về dependency injection**, không phải một project biên dịch độc lập: nó dùng `Notifier` từ ví dụ và không có main method. Cần phân biệt rõ:

- **DI không chạy trên raw object** là vấn đề container ownership.
- **Advice không chạy trên raw object** là vấn đề Spring AOP proxy.
- **Bean có proxy nhưng tự gọi method nội bộ** là vấn đề đường gọi qua proxy.

Ba trường hợp có dấu hiệu gần nhau nhưng cần cách sửa khác nhau.

## 7. Cách thiết kế để object được Spring quản lý

### 7.1. Đăng ký bằng @Component hoặc @Bean

Nếu service thuộc application layer và cần Spring AOP, đăng ký nó trong `ApplicationContext`:

~~~java
@Component
public class NotificationService {
    public void send() {
        // application logic
    }
}
~~~

Snippet này phải đặt trong tệp `NotificationService.java` có `import org.springframework.stereotype.Component;` và cần component scanning bật cho package chứa class. Với cấu hình dùng `@Bean`, phương pháp ở ví dụ phần 4 rõ ràng hơn vì không phụ thuộc package scanning.

Đảm bảo aspect cũng được đăng ký như bean:

~~~java
@Aspect
@Component
public class MonitoringAspect {
    @Before("execution(* demo.NotificationService.send(..))")
    public void before() {
        System.out.println("advised");
    }
}
~~~

Đây là đoạn minh họa cần các import `org.aspectj.lang.annotation.*` và `org.springframework.stereotype.Component` trong project có component scanning; bản thân `@Aspect` **không đủ** để Spring tự quét class làm bean. Chỉ dùng các snippet này khi application có cấu hình AOP và scanning phù hợp.

### 7.2. Inject service, không tự tạo service bằng new

Thay vì:

~~~java
public class Sender {
    private final Notifier notifier = new Notifier("manual");

    public void doSend() {
        notifier.send();
    }
}
~~~

hãy để Spring inject đúng bean:

~~~java
public class Sender {
    private final Notifier notifier;

    public Sender(Notifier notifier) {
        this.notifier = notifier;
    }

    public void doSend() {
        notifier.send();
    }
}
~~~

Hai đoạn là **hai phiên bản lựa chọn** của một class `Sender`, không đặt đồng thời vào cùng project. Lớp `Sender` cũng cần được khai báo làm Spring bean (bằng `@Bean`/`@Component`) để container thực hiện constructor injection. Với Spring-managed `Notifier` đã được proxy, dependency thông thường của `Sender` là reference proxy; `doSend()` gọi đúng đường để Spring advice chạy.

Sử dụng constructor injection giúp lộ phụ thuộc ở API class, thuận tiện kiểm thử và tránh việc quên đăng ký bean.

### 7.3. Cần một đối tượng mới mỗi lần gọi?

Đừng mặc định phải dùng `new` chỉ vì business logic cần mỗi lần một instance khác nhau. Trong Spring có **prototype scope** và cách lấy object theo nhu cầu từ container:

~~~java
@Configuration
class NotificationConfig {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    Notifier notifier() {
        return new Notifier("prototype");
    }
}
~~~

Đoạn cấu hình là minh họa: cần import `@Configuration`, `@Bean`, `@Scope`, `ConfigurableBeanFactory` cùng class `Notifier`. Mỗi lần Spring container tạo một prototype bean từ definition này, bean đi qua các cơ chế xử lý thích hợp, bao gồm khả năng auto-proxy nếu có advisor.

Một consumer có thể inject `ObjectProvider<Notifier>` và gọi `provider.getObject()` **mỗi khi cần instance mới**. Cần nhớ: một prototype được inject trực tiếp vào singleton thông thường chỉ được resolve tại thời điểm tạo singleton; không tự sinh prototype mới mỗi khi gọi method của singleton.

### 7.4. Nếu đối tượng phải do thư viện ngoài tạo

Với một callback object do SDK/framework khác tự tạo, có các lựa chọn phụ thuộc quyền kiểm soát vòng đời:

1. Nếu kiểm soát được factory, để Spring quản lý factory/result qua bean definition và truyền **proxy reference** tới nơi cần gọi, tránh giữ raw reference cũ.
2. Nếu không thay được factory bên ngoài nhưng caller do ứng dụng quản lý, đặt một **Spring-managed adapter/service** làm điểm gọi có Spring AOP và chuyển yêu cầu tới SDK object bên trong. Advice áp dụng tại **boundary của adapter**, không tự chặn mọi method bên trong SDK object.
3. Nếu thật sự phải advice trực tiếp một object không do Spring quản lý, **Spring AOP có API tạo proxy thủ công** (`AspectJProxyFactory`/`ProxyFactory`) cho target phù hợp. Đây là hành động explicit: caller phải nhận/dùng proxy tạo ra; tự động auto-proxy của `@EnableAspectJAutoProxy` không thực hiện bước này cho mọi raw object. Nếu yêu cầu vượt cả giới hạn proxy, phải đánh giá cơ chế khác với chi phí cấu hình riêng.

Một adapter Spring thường là cách đơn giản nhất để giữ các cross-cutting concern như logging, audit hoặc tracing ở ranh giới ứng dụng mà không cần điều khiển vòng đời nội bộ của thư viện thứ ba.

## 8. Tạo proxy thủ công cho object không được quản lý: giới hạn và ví dụ API

Spring Framework còn có `org.springframework.aop.aspectj.annotation.AspectJProxyFactory` để tạo AOP proxy một cách **chủ động**. Ví dụ minh họa đặt **trong một method Java đã có `Notifier` và `TraceAspect` của phần 4**:

~~~java
Notifier raw = new Notifier("external");

org.springframework.aop.aspectj.annotation.AspectJProxyFactory factory =
        new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(raw);

TraceAspect manualAspect = new TraceAspect();
factory.addAspect(manualAspect);

Notifier proxy = factory.getProxy();
proxy.send();

System.out.println(manualAspect.getAdviceCount()); // 1
~~~

Đây **không phải project chạy độc lập**, mà là đoạn sử dụng thêm API vào project đã có dependency Spring AOP/AspectJ. Do `Notifier` không có interface, Spring có thể tạo class-based proxy phù hợp cho target trong ví dụ. `factory.addAspect(manualAspect)` dùng một instance aspect cụ thể; advice counter được đọc từ **instance này** chứ không phải aspect bean trong `ApplicationContext`.

Khác biệt với auto-proxy:

- Lập trình viên **chủ động chọn target**, **thêm aspect** và **lấy proxy** bằng `factory.getProxy()`.
- Chỉ những lời gọi trên `proxy` mới đi qua advice; gọi `raw.send()` vẫn bỏ qua proxy.
- Proxy thủ công không đồng nghĩa target gốc tự được đăng ký làm Spring bean hoặc có lifecycle/DI của container.
- Khi dùng Spring AOP auto-proxy cho application services thông thường, ưu tiên Spring-managed bean; không nên lặp việc tự tạo proxy thủ công khắp code.

## 9. Chẩn đoán khi advice không chạy cho một instance

Dùng checklist theo đúng thứ tự:

1. **Xác định nơi đối tượng được tạo:** `new` trong controller, factory, SDK, deserializer, callback hoặc lấy từ `context.getBean(...)`? Đặc biệt phân biệt `new` nằm **trong `@Bean` factory** với `new` nằm trong method thường.
2. **Kiểm tra Spring container có quản lý bean không:** tên bean/definition, component scanning, đúng `ApplicationContext` và đúng scope? Có thể cùng class tồn tại cả bean managed lẫn object raw.
3. **Kiểm tra reference thật đang được gọi:** dùng `AopUtils.isAopProxy(reference)` cho reference tại call site, không chỉ cho một instance khác trong context.
4. **Kiểm tra loại proxy và advisor:** `AopUtils.isJdkDynamicProxy`/`isCglibProxy`, pointcut method execution có khớp hay không. Có bean vẫn chưa chắc advice áp dụng.
5. **Xác định ranh giới method invocation:** caller gọi qua proxy, qua target raw hay `this.method()` trên cùng target?
6. **Kiểm tra giới hạn method:** JDK interface, CGLIB `final`/`private`, static method và constructor không phải method execution interceptable như method business thông thường.
7. **Nếu DI cũng thiếu:** object có được Spring inject và init không, hay được tự new? Không dùng sự có mặt của `@Autowired` như bằng chứng container đang quản lý instance.
8. **Nếu bean đã được Spring quản lý nhưng thiếu proxy:** xem auto-proxy creator có bật, aspect có đăng ký làm bean, advisor có khớp và bean có bị khởi tạo quá sớm trước khi BPP đầy đủ không.
9. **Sau khi sửa:** gọi cùng method qua reference Spring cấp, xác nhận dòng advice/counter hoặc integration test. Đừng chỉ kiểm tra việc method nghiệp vụ vẫn trả kết quả bình thường.

Đoạn kiểm tra chèn vào một ứng dụng đã có biến `context`:

~~~java
Object bean = context.getBean("notifier");

System.out.println(
        org.springframework.aop.support.AopUtils.isAopProxy(bean));
System.out.println(
        org.springframework.aop.support.AopUtils.isJdkDynamicProxy(bean));
System.out.println(
        org.springframework.aop.support.AopUtils.isCglibProxy(bean));
~~~

Đoạn này không phải file Java độc lập; mục đích là đo reference Spring **đang thực sự cấp**. Nếu code còn giữ một raw reference khác và gọi vào nó, kết quả đo trên `bean` không nói được lời gọi raw có đi qua advice.

## 10. Những hiểu lầm thường gặp

**“Pointcut ghi đúng package/class/method nên bắt cả đối tượng tạo bằng `new`.”** Sai với proxy Spring AOP. Pointcut chỉ xác định advisor áp dụng cho những join point có thể đi qua proxy; nó không lắp interceptor vào mọi instance của class trong JVM.

**“`@Component` trên class bảo đảm mọi `new ThatClass()` được quản lý.”** Sai. Annotation giúp đăng ký bean definition khi component scanning phù hợp. Một instance khác tự tạo vẫn không tự đi qua Spring IoC.

**“Trong `@Bean` có `new` nên Spring không proxy được.”** Sai. Factory method trả instance cho container; quy trình bean post-processing có thể bọc nó.

**“Bean mẹ đã là proxy nên object nó tự tạo cũng được proxy.”** Sai. Object được tự new trong method là instance riêng; Spring không tự xử lý toàn bộ object graph bên trong method.

**“Spring bean có proxy thì tất cả lời gọi method của nó đều được advice.”** Sai. Self-invocation, loại method, điểm gọi, kiểu proxy và pointcut đều ảnh hưởng kết quả. Một proxy không làm mọi method trở thành join point được chặn.

**“Tôi gọi `getBean(...)` một lần để lấy proxy; reference raw cũ cũng tự thay thành proxy.”** Sai. Variable trỏ thẳng vào target không được biến đổi thành proxy chỉ vì container trả một wrapper cho người khác.

**“Thêm AspectJ Weaver dependency là auto-proxy cả object ngoài Spring.”** Sai. `aspectjweaver` thường là dependency giúp Spring giải thích kiểu `@AspectJ`; muốn AspectJ weaving thực sự cần thiết lập compiler/weaver riêng và cơ chế khác.

## 11. Tự kiểm tra

1. **Hai object `Notifier` cùng class, một được lấy từ Spring context, một được `new` ở main. Chỉ object nào tự động nhận advice?**  
   Object mà container xử lý và xuất ra qua proxy; object `new` độc lập không được Spring AOP tự bao.

2. **Tại sao `return new Notifier(...)` trong một `@Bean` vẫn có thể được AOP?**  
   Vì Spring quản lý **giá trị trả về của bean factory method**, cho nó đi qua BPP/auto-proxy trước khi công bố.

3. **Nếu một object raw được đưa vào proxy thủ công, có phải mọi biến đang trỏ tới object đó đều được advice?**  
   Không. Caller phải sử dụng biến trỏ tới **proxy**; reference raw vẫn gọi trực tiếp target.

4. **Vì sao dùng `@Autowired` trên field của object tự `new` có thể dẫn tới `null`?**  
   Vì Spring chưa hề thực hiện bước dependency injection cho instance đó.

5. **Nếu hai method cùng một Spring bean gọi nhau qua `this`, có chắc cả hai advice chạy không?**  
   Không. Lời gọi nội bộ trên target có thể bypass Spring proxy.

6. **Khi cần nhiều instance được advice, giải pháp Spring-native là gì?**  
   Đăng ký prototype/factory dưới Spring và lấy instance qua container (`ObjectProvider` khi cần), thay vì tạo raw object ngoài BeanFactory.

7. **`AspectJProxyFactory` có nghĩa `@EnableAspectJAutoProxy` tự động làm điều đó cho mọi raw object?**  
   Không. Đây là API tạo proxy **tường minh**; auto-proxy thuộc Spring bean lifecycle.

## 12. Tài liệu chính thức

- [Spring Framework — AOP @AspectJ support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): Spring đọc syntax @AspectJ nhưng runtime AOP vẫn dựa vào proxy.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): auto-proxying cho Spring beans.
- [Spring Framework — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/at-aspectj.html): aspect phải được đăng ký làm bean; `@Aspect` riêng lẻ chưa đủ.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): giới hạn JDK/CGLIB và self-invocation.
- [Spring Framework — Programmatic Creation of @AspectJ Proxies](https://docs.spring.io/spring-framework/reference/core/aop/aspectj-programmatic.html): API `AspectJProxyFactory`.
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html): Spring-managed singleton/prototype và cách yêu cầu instance theo scope.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invocation-boundaries-008">Phương thức kế thừa, interface mặc định và vị trí đặt annotation</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức kế thừa, interface mặc định và vị trí đặt annotation trong Spring AOP

## 1. Mục tiêu và phạm vi

Khi làm việc với Spring AOP, một lời gọi phương thức có thể đi qua **JDK dynamic proxy** hoặc **CGLIB class-based proxy**. Phương thức đó có thể được khai báo trực tiếp trên lớp đích, **kế thừa từ superclass**, hoặc được cung cấp dưới dạng **default method của interface**. Đồng thời, annotation dùng để định nghĩa advice hoặc kích hoạt cơ chế Spring có thể nằm trên interface, implementation, superclass hay method.

Bốn câu hỏi cần tách riêng:

1. **Client có gọi qua Spring proxy không?** Không có đường vào proxy thì Spring AOP không tự chặn lời gọi chỉ vì method có annotation.
2. **Phương thức có nằm trong API mà proxy intercept được không?** Với JDK proxy, cần lời gọi public method được công bố trên interface; với CGLIB, cần method mà subclass proxy có thể override.
3. **Pointcut có khớp execution thực tế không?** Kiểm tra kiểu khai báo method, lớp target, chữ ký, annotation và biểu thức Spring AOP.
4. **Framework lấy annotation ở đâu?** Java reflection, Spring merged-annotation utilities, `@annotation` pointcut và các module như transaction đều có những quy tắc tra cứu metadata không hoàn toàn giống nhau.

Bài này chỉ phân tích các tình huống trên trong **Spring Framework/Spring AOP proxy-based interception**. Những lý thuyết Aspect chung hoặc AspectJ compile-time/load-time weaving không phải trọng tâm.

## 2. Nhận diện ba nguồn gốc phương thức

Ví dụ một interface với default method và một lớp triển khai kế thừa method từ superclass:

```java
public interface CustomerApi {
    String inherited(String id);

    default String fromDefault(String id) {
        return "DEFAULT:" + id;
    }

    String explicit(String id);
}

public class BaseCustomerService {
    public String inherited(String id) {
        return "BASE:" + id;
    }
}

public class CustomerService
        extends BaseCustomerService implements CustomerApi {

    @Override
    public String explicit(String id) {
        return "IMPL:" + id;
    }
}
```

Đây là **ba loại method khác nhau trong cùng một đối tượng đích**:

| Method | Nơi cung cấp code | Vì sao gọi được từ client? |
| --- | --- | --- |
| `inherited(String)` | `BaseCustomerService` (superclass) | `CustomerService` kế thừa method public và thỏa `CustomerApi` |
| `fromDefault(String)` | `CustomerApi` (interface default method) | Interface cung cấp implementation mặc định, class chưa override |
| `explicit(String)` | `CustomerService` | Implementation khai báo/override trực tiếp |

**Vị trí khai báo code không quyết định duy nhất việc có advice.** Spring AOP cần chặn được lời gọi **đi qua proxy** và xác định advisor phù hợp với method. Phương thức kế thừa hoặc default method không tự nằm ngoài AOP chỉ vì code không viết trực tiếp trong class cuối cùng.

Trong Java reflection, có thể quan sát nguồn gốc:

```java
Method m1 = CustomerService.class.getMethod("inherited", String.class);
System.out.println(m1.getDeclaringClass().getSimpleName());
// BaseCustomerService

Method m2 = CustomerApi.class.getMethod("fromDefault", String.class);
System.out.println(m2.isDefault());
// true
```

Đây là các **đoạn minh họa**, cần import `java.lang.reflect.Method` và định nghĩa các kiểu như trên. `getDeclaringClass()` phản ánh class/interface khai báo method được tìm thấy; nó **không trả lời** Spring có tạo proxy hay pointcut đã khớp chưa.

## 3. Với phương thức kế thừa, JDK và CGLIB khác nhau như thế nào?

### 3.1. JDK dynamic proxy

JDK proxy không kế thừa `CustomerService`. Nó triển khai những interface Spring công bố, ví dụ `CustomerApi`. Vì `CustomerApi` có method `inherited(String)`, client có thể gọi nó qua proxy:

```text
Client (CustomerApi)
        |
        v
JDK proxy.inherited("A")
        |
        v
Spring AOP advice / interceptor
        |
        v
CustomerService target
        |
        v
BaseCustomerService.inherited("A")
```

Trong trường hợp này, **implementation nằm ở superclass** nhưng lời gọi bên ngoài vẫn có thể được Spring AOP intercept do method **được khai báo trên interface proxy**.

Nếu superclass có method public `onlyInBase()` nhưng `CustomerApi` **không công bố** method đó, client giữ JDK proxy không thể gọi `onlyInBase()` bằng cách ép JDK proxy sang `CustomerService`. Lời gọi không phải một API được JDK proxy expose; đặt advice trên method không tạo thêm method vào proxy interface.

### 3.2. CGLIB class-based proxy

Với `@EnableAspectJAutoProxy(proxyTargetClass = true)`, Spring có thể tạo proxy bằng subclass của `CustomerService`. Subclass proxy có thể intercept các method đủ điều kiện override, gồm method public **được kế thừa từ superclass**, miễn method/visibility và pointcut phù hợp.

```text
Client (CustomerService)
        |
        v
CGLIB proxy extends CustomerService
        |
        v
Spring AOP advice / interceptor
        |
        v
Target CustomerService
        |
        v
BaseCustomerService.inherited("A")
```

Một method kế thừa bị đánh dấu `final` thì CGLIB **không thể override** để đặt interceptor, bất kể method đó có annotation hay pointcut khớp. Tương tự, `private` method không được kế thừa/override; `final class` không thể làm superclass của CGLIB proxy.

**Kết luận cho method kế thừa:** kiểm tra **API proxy và overrideability**, sau đó kiểm tra **pointcut**. Không dùng câu “method không được override ở implementation nên Spring AOP bỏ qua” làm quy tắc tổng quát.

## 4. Default method của interface dưới góc nhìn Spring AOP

Java interface có thể khai báo:

```java
public interface CustomerApi {
    default String fromDefault(String id) {
        return "DEFAULT:" + id;
    }
}
```

Default method là method **có implementation do interface cung cấp**. Nó khác abstract interface method, nhưng vẫn là method public trong contract của interface.

Với Spring AOP:

- **JDK proxy:** default method thuộc interface được proxy công bố. Một lời gọi từ client qua proxy là lời gọi có thể đi vào invocation handler/AOP chain; Spring sau đó tiếp tục thực thi implementation mặc định trên target nếu không bị advice short-circuit.
- **CGLIB proxy:** proxy là subclass của target. Interface default method có thể xuất hiện như một method kế thừa từ interface; khả năng intercept cụ thể vẫn phụ thuộc điều kiện proxy, target, signature và Spring version. Nếu kết quả quan trọng, phải kiểm tra trong một test context với proxy strategy đã chọn.
- **Không phải do `default` mà tự động có advice:** nếu pointcut không match, lời gọi vẫn chạy bình thường, không có business advice được áp dụng.
- **Default method có thể gọi method khác:** lời gọi phát sinh **sau khi đã chuyển execution vào target** không được tự coi là một lời gọi mới qua Spring proxy. Nếu cần interceptor cho lời gọi thứ hai, tách sang một Spring bean khác để client/collaborator gọi qua proxy.

Đặc biệt, **Spring AOP không áp advice lên việc khai báo interface**. Nó hoạt động ở *method execution* có lời gọi đi qua Spring proxy. Vị trí implementation ở interface không biến `@AspectJ` pointcut thành weaving lên bytecode interface.

### Default method được override thì sao?

```java
interface CustomerApi {
    default String fromDefault(String id) {
        return "DEFAULT:" + id;
    }
}

class CustomerService implements CustomerApi {
    @Override
    public String fromDefault(String id) {
        return "OVERRIDE:" + id;
    }
}
```

Trong đoạn minh họa này, implementation thực thi là `CustomerService.fromDefault(...)`. Không được suy rằng annotation hoặc logic ở interface default method vẫn có hiệu lực giống hệt trong mọi Spring feature; cách đọc annotation tùy matcher/metadata source. Kiểm tra implementation thực tế, proxy method và annotation lookup của tính năng đang dùng.

## 5. Ví dụ Java 17+ chạy được: kế thừa, default method và annotation

Thí nghiệm sử dụng **Spring Framework 6.2.19, Java 17+, Maven**. Nó tạo **JDK dynamic proxy** để giữ đường gọi qua interface thật rõ ràng và không phải dựa vào tên lớp proxy được sinh.

Ba method được gọi từ client:

1. `inherited()`: implementation nằm ở superclass, nhưng interface công bố chữ ký.
2. `fromDefault()`: implementation mặc định nằm ở interface.
3. `marked()`: implementation class trực tiếp override và có custom annotation `@TraceCall`.

Aspect dùng pointcut theo **tên method execution** để dễ thấy cả ba lời gọi đều đi qua proxy; phần sau kiểm tra **vị trí annotation bằng Java reflection**. Cách tách này tránh nhầm rằng pointcut bằng tên và pointcut `@annotation` có cùng thuật toán match metadata.

### 5.1. Cấu trúc dự án

```text
spring-aop-method-boundaries/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── MethodBoundaryDemo.java
```

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-method-boundaries</artifactId>
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
                    <mainClass>demo.MethodBoundaryDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Tệp `src/main/java/demo/MethodBoundaryDemo.java`:

```java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class MethodBoundaryDemo {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface TraceCall {
    }

    public interface CustomerApi {
        String inherited(String id);

        default String fromDefault(String id) {
            System.out.println("TARGET default " + id);
            return "DEFAULT:" + id;
        }

        String marked(String id);
    }

    public static class BaseService {
        public String inherited(String id) {
            System.out.println("TARGET inherited " + id);
            return "BASE:" + id;
        }
    }

    public static class CustomerService
            extends BaseService implements CustomerApi {

        @Override
        @TraceCall
        public String marked(String id) {
            System.out.println("TARGET marked " + id);
            return "MARKED:" + id;
        }
    }

    @Aspect
    public static class TraceAspect {
        @Before(
            "execution(* demo..*.inherited(..)) || " +
            "execution(* demo..*.fromDefault(..)) || " +
            "execution(* demo..*.marked(..))"
        )
        public void trace(JoinPoint joinPoint) {
            System.out.println("ADVICE " + joinPoint.getSignature().getName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class Config {
        @Bean
        public CustomerApi customerApi() {
            return new CustomerService();
        }

        @Bean
        public TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    public static void main(String[] args) throws Exception {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            CustomerApi bean = context.getBean(CustomerApi.class);

            System.out.println("JDK_PROXY="
                    + AopUtils.isJdkDynamicProxy(bean));
            System.out.println("INHERITED_DECLARED_IN="
                    + CustomerService.class
                            .getMethod("inherited", String.class)
                            .getDeclaringClass().getSimpleName());
            System.out.println("INTERFACE_DEFAULT="
                    + CustomerApi.class
                            .getMethod("fromDefault", String.class)
                            .isDefault());
            System.out.println("ANNOTATION_ON_INTERFACE="
                    + CustomerApi.class
                            .getMethod("marked", String.class)
                            .isAnnotationPresent(TraceCall.class));
            System.out.println("ANNOTATION_ON_IMPL="
                    + CustomerService.class
                            .getMethod("marked", String.class)
                            .isAnnotationPresent(TraceCall.class));

            System.out.println("RESULT " + bean.inherited("A"));
            System.out.println("RESULT " + bean.fromDefault("B"));
            System.out.println("RESULT " + bean.marked("C"));
        }
    }
}
```

Chạy trong thư mục dự án:

```bash
mvn -q compile exec:java
```

Các dòng stdout **do chương trình tự in** (thông báo Maven hoặc thư viện logging có thể xuất hiện thêm):

```text
JDK_PROXY=true
INHERITED_DECLARED_IN=BaseService
INTERFACE_DEFAULT=true
ANNOTATION_ON_INTERFACE=false
ANNOTATION_ON_IMPL=true
ADVICE inherited
TARGET inherited A
RESULT BASE:A
ADVICE fromDefault
TARGET default B
RESULT DEFAULT:B
ADVICE marked
TARGET marked C
RESULT MARKED:C
```

Các điểm cần đối chiếu với output:

- `CustomerService` **không khai báo lại** `inherited()`, nhưng method vẫn được gọi qua `CustomerApi`; advice in trước implementation kế thừa từ `BaseService`.
- `fromDefault()` có code trong interface. JDK proxy tiếp nhận lời gọi và Spring có cơ hội áp advisor trước khi method default chạy trên target.
- `TraceCall` nằm tại `CustomerService.marked()`, **không nằm tại `CustomerApi.marked()`**. Hai dòng kiểm tra annotation phản ánh sự khác nhau giữa method declaration trên interface và implementation.
- Code advice ở ví dụ sử dụng `execution(...)` theo **tên method**, nên dòng `ADVICE marked` **chưa chứng minh** một pointcut `@annotation(TraceCall)` sẽ có kết quả giống nhau cho mọi loại proxy/annotation placement.

### 5.2. Chuyển sang CGLIB để kiểm tra

Trong class `Config`, đổi:

```java
@EnableAspectJAutoProxy(proxyTargetClass = true)
```

Sau đó thay điều kiện quan sát proxy bằng:

```java
System.out.println("CGLIB_PROXY="
        + AopUtils.isCglibProxy(bean));
```

Đây là **hai đoạn thay thế nhỏ**, không phải code standalone. Với target class đủ điều kiện kế thừa, Spring có thể tạo CGLIB proxy. Hãy chạy lại ba lời gọi trong cùng context và quan sát trace thay vì mặc định rằng cách đối chiếu interface default method giống hoàn toàn giữa mọi phiên bản proxy.

## 6. Vị trí đặt annotation: Java thực sự nhìn thấy gì?

### 6.1. Annotation ở implementation method

```java
public interface CustomerApi {
    void submit();
}

public class CustomerService implements CustomerApi {
    @TraceCall
    @Override
    public void submit() {
        // xử lý nghiệp vụ
    }
}
```

Đây là phương án rõ ràng nếu bạn muốn khai báo metadata tại nơi chứa **implementation thực sự**. Với JDK proxy, lời gọi client có thể mang Method reflection thuộc interface, trong khi Spring có khả năng tìm **most specific method** trên target class để kiểm tra match.

Dù vậy, đừng giả định mọi custom annotation resolver đều tự tìm từ interface sang implementation. Đặt annotation tại nơi mà **Spring feature đang dùng** chính thức đọc và kiểm tra bằng test.

### 6.2. Annotation chỉ đặt trên interface method

```java
public interface CustomerApi {
    @TraceCall
    void submit();
}

public class CustomerService implements CustomerApi {
    @Override
    public void submit() {
        // xử lý nghiệp vụ
    }
}
```

Java reflection thông thường phân biệt method `CustomerApi.submit()` với method `CustomerService.submit()`. **Method annotation không tự động được “kế thừa” chỉ bởi vì class implements interface**. Do đó, khi framework xử lý annotation trên implementation, nó có thể không thấy annotation ở interface nếu không chủ động tra cứu theo hierarchy/interface.

Điểm này đặc biệt quan trọng nếu pointcut sử dụng `@annotation(...)` hay nếu một framework feature đọc annotation riêng. Không đánh đồng `@TraceCall` và annotation đặc thù được Spring hỗ trợ qua metadata resolver riêng.

### 6.3. Annotation trên method của superclass

```java
public class BaseService {
    @TraceCall
    public void submit() {
        // xử lý gốc
    }
}

public class CustomerService extends BaseService {
    // Không override submit()
}
```

Khi class con **không override**, `CustomerService.class.getMethod("submit")` có thể trả method được khai báo từ superclass và mang annotation đó.

Nếu class con **override** và không gắn lại annotation:

```java
public class CustomerService extends BaseService {
    @Override
    public void submit() {
        // xử lý khác
    }
}
```

thì Method reflection của class con là **method khác** và `isAnnotationPresent(TraceCall.class)` trên Method đó không tự trả `true` chỉ vì method cha có annotation. Spring có thể tìm annotation theo hierarchy với một số tiện ích hoặc tính năng, nhưng cơ chế lookup này phải được kiểm chứng.

### 6.4. `@Inherited` không áp dụng cho method annotation

Trong Java, `java.lang.annotation.Inherited` có tác dụng khi truy vấn annotation **trên class**, và chỉ trong quan hệ superclass/subclass thích hợp. Nó không làm **method annotations** tự thừa kế qua override, cũng không biến interface annotations thành class annotations được kế thừa theo mặc định.

Vì vậy đoạn sau không giải quyết bài toán annotation trên interface method:

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ServiceMarker {
}
```

Đây là **minh họa annotation trên TYPE**, không phải `@TraceCall` dành cho METHOD. Từ khóa `@Inherited` không thay thế chức năng tìm annotation trên method/interface mà Spring có thể cần thực hiện.

## 7. `execution` và `@annotation` trong Spring AOP không giống nhau

Hai pointcut hay bị nhầm:

```java
@Before("execution(* demo..*.submit(..))")
public void bySignature() {
    // chạy cho method execution phù hợp chữ ký
}

@Before("@annotation(demo.TraceCall)")
public void byAnnotation() {
    // chạy cho method execution khớp annotation theo Spring AOP matcher
}
```

Đây là **hai advice minh họa**, không phải chương trình biên dịch độc lập. Nếu đặt trong một `@Aspect` bean, chúng dùng cơ chế match khác nhau:

| Pointcut | Trọng tâm | Điều cần kiểm tra |
| --- | --- | --- |
| `execution(...)` | Chữ ký method execution, modifiers, return type, tên/args/declaring type pattern | Kiểu và method được Spring AOP nhận diện, lời gọi có qua proxy |
| `@annotation(...)` | Annotation trên method execution theo matcher | Annotation xuất hiện ở đâu, target-specific method và lookup semantics |
| `@within(...)` | Kiểu có annotation, giới hạn các execution trong kiểu | Annotation đặt ở class/interface nào; method inherited có nằm trong phạm vi match không |
| `@target(...)` | Class của target object mang annotation được xét | Target class và annotation semantics |
| `this(Type)` / `target(Type)` | Kiểu của proxy / target | JDK proxy không phải subclass của implementation |

**Đừng sửa lỗi `@annotation` không khớp bằng cách ngẫu nhiên chuyển annotation giữa interface và implementation.** Trước hết cần biết Spring lấy Method nào và feature đang dùng (AOP pointcut hay annotation-specific interceptor).

Một cách chẩn đoán là lấy method thuộc interface rồi giải quyết implementation:

```java
Method interfaceMethod =
        CustomerApi.class.getMethod("submit");

Method targetMethod =
        AopUtils.getMostSpecificMethod(
                interfaceMethod, CustomerService.class);
```

Đây là **đoạn API minh họa** (cần import `java.lang.reflect.Method`, `AopUtils`). `getMostSpecificMethod(...)` giúp tìm method trên target class nếu có. Nó **không** có nghĩa Spring luôn sử dụng đúng duy nhất Method đó cho mọi loại annotation hay mọi trường hợp.

## 8. Annotation của Spring framework cần đánh giá theo chính tính năng đó

Không phải mọi annotation gây ra advice đều được xử lý chỉ bằng AspectJ `@annotation` pointcut. Ví dụ:

- **`@Transactional`** được transaction metadata infrastructure giải quyết qua `TransactionAttributeSource`, rồi Spring dùng interceptor/proxy cho transaction. Spring khuyến nghị đặt annotation trên concrete class/method khi có thể để tránh khác biệt khi đổi proxy/processing mode. Các phiên bản Spring hiện đại có thể hỗ trợ một số cách đặt ở interface trong proxy mode; điều đó **không tạo quy luật chung cho custom annotation**.
- **`@Cacheable`** được caching infrastructure xử lý và có cơ chế tìm metadata, điều kiện cache key, proxy riêng. Việc `@Cacheable` ở interface hoặc implementation có hoạt động như mong đợi không cần được xác minh với cấu hình/version của tính năng.
- **`@Async`** cũng có xử lý annotation và proxy/executor riêng. Đừng dùng kết quả `@Async` ở một vị trí annotation để kết luận một `@Aspect` với `@annotation(...)` xử lý metadata y hệt.

Điểm thống nhất ở chế độ **Spring proxy** là lời gọi từ client phải đi vào proxy được cấu hình đúng và method phải đủ điều kiện được chặn.

### Vị trí class annotation và method inherited

Ngay cả với Spring `@Transactional`, tài liệu chỉ ra rằng class-level annotation không mặc nhiên áp cho mọi method **được khai báo ở ancestor class** trong mọi tình huống. Cần xét cách Spring quyết định metadata cho inherited method và phương thức override tại class con. Nếu muốn một hợp đồng rõ ràng, viết test bao phủ method được kế thừa/override thay vì chỉ nhìn annotation trên class con.

## 9. Self-invocation: phương thức kế thừa/default không phải ngoại lệ

Dù code method nằm ở superclass hoặc interface, một lời gọi **bên trong target** vẫn không tự động quay lại Spring proxy.

Ví dụ:

```java
public class CustomerService extends BaseService implements CustomerApi {
    public void outer() {
        this.inherited("A");
    }
}
```

Nếu caller gọi `proxy.outer()` và có advice cho `outer()`, advice ngoài có thể chạy. Nhưng sau khi Spring chuyển tới target, `this.inherited("A")` chạy trực tiếp trên target; advice chỉ khớp `inherited()` **không được chạy thêm** qua đường này.

Tương tự, nếu một default method chạy trên target và gọi một method khác, không được giả định lời gọi nội bộ đó là lời gọi client mới vào proxy. Khi hành vi cần interceptor riêng, nên tách sang Spring bean khác và inject bean reference do container cấp.

Đổi `proxyTargetClass=true` **không tự sửa self-invocation**. Nó chỉ đổi chiến lược tạo proxy.

## 10. Giới hạn theo visibility và vị trí code

| Tình huống | JDK proxy | CGLIB proxy | Cách hiểu |
| --- | --- | --- | --- |
| Public method kế thừa từ superclass và khai báo trên interface | Có thể intercept | Có thể intercept nếu method overrideable | Code nằm ở superclass không phải lý do bỏ qua |
| Public method kế thừa nhưng không công bố trên interface | Không gọi được qua API của JDK proxy | Có thể intercept nếu đủ điều kiện | Phân biệt target API và proxy API |
| Public interface default method | Có thể đi qua proxy | Có thể, cần xác minh override/matching cụ thể | `default` không tự tạo advice |
| `protected` method | Không nằm trong public interface API | Có thể tùy Spring version/visibility | Ưu tiên public boundary rõ ràng |
| `private` method | Không | Không | Không thể override/intercept bằng cơ chế proxy |
| `final` method của target | JDK proxy có thể intercept lời gọi qua interface trong trường hợp tương ứng | Không thể override method đó | Cần xét proxy strategy, không đánh đồng |
| `final class` | Có thể JDK proxy nếu có interface phù hợp | Không thể subclass | Cấu hình ép CGLIB có thể gây lỗi |
| Constructor và field access | Không | Không | Ngoài method-execution boundary của Spring AOP |
| `this.method()` bên trong target | Không vào lại proxy | Không vào lại proxy | Self-invocation bypass advice |

Dòng về `final method` cần đọc cẩn thận: `final` không ngăn **interface method call** đi vào một **JDK proxy**, nhưng ngăn **subclass proxy override method target**. Một JDK proxy và một CGLIB proxy có thể cho kết quả advice khác nhau ở tình huống này.

## 11. Checklist kiểm tra khi advice không chạy

1. **Bean có được Spring quản lý?** Tạo bằng `@Component`/`@Bean` hay ứng dụng đang gọi object `new` trực tiếp?
2. **Có proxy thực sự không?** Dùng `AopUtils.isAopProxy(bean)`, `isJdkDynamicProxy(bean)`, `isCglibProxy(bean)`.
3. **Caller dùng kiểu nào?** Interface hay implementation? JDK proxy chỉ công bố interface được proxy triển khai.
4. **Method thật khai báo ở đâu?** Implementation, superclass, interface default hay method override?
5. **Method có thể bị proxy chặn không?** Public interface method cho JDK; method có thể override và phù hợp visibility cho CGLIB.
6. **Pointcut khớp gì?** `execution` dựa signature, `@annotation` dựa annotation trên method được matcher xét.
7. **Annotation nằm ở đâu?** Method/interface/class/superclass? Có override bỏ annotation không? `@Inherited` có bị hiểu nhầm là method inheritance không?
8. **Đang dùng module Spring nào?** `@Transactional`/`@Cacheable`/`@Async` có metadata resolver và cấu hình riêng; không đồng nhất với custom `@annotation` pointcut.
9. **Có self-invocation không?** `this.method()` và các lời gọi nội bộ trên target không vào lại proxy.
10. **Có nhiều proxy/advisor không?** Cấu hình proxy-target-class, thứ tự advice và pointcut động có thể làm kết quả khác dự đoán.

Một kiểm thử tốt dùng **ApplicationContext thật**, lấy đúng Spring bean reference, xác minh loại proxy, rồi gọi **từ client bên ngoài target**. Kết hợp test cho hai proxy mode nếu ứng dụng hỗ trợ cả JDK và CGLIB.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `CustomerService` kế thừa method public `run()` từ `BaseService` và `CustomerApi` khai báo `run()`. JDK proxy có thể chạy Spring AOP advice khi client gọi `run()` qua interface không?  
**Đáp án:** Có, nếu advisor/pointcut khớp và bean thực sự nhận AOP proxy. Vị trí implementation ở superclass không ngăn interceptor.

**Câu 2.** Cũng method `run()` đó, nhưng interface không khai báo nó. Client giữ JDK proxy có thể ép proxy sang implementation để gọi `run()` không?  
**Đáp án:** Không thể giả định; JDK proxy không phải subclass của implementation. Nếu cần method này qua proxy, cân nhắc API interface hoặc CGLIB khi phù hợp.

**Câu 3.** Default method có implementation sẵn trong interface. Điều đó khiến nó không thể được Spring AOP intercept?  
**Đáp án:** Không. Lời gọi từ client qua proxy vẫn có thể được chặn, tùy advisor matching và phương thức mà proxy hỗ trợ.

**Câu 4.** Một method implementation được gắn `@TraceCall` nhưng interface method không có annotation. Java reflection trên interface method có tự trả true không?  
**Đáp án:** Không. Hai Method declaration khác nhau, annotation đặt ở implementation không tự xuất hiện trên interface method.

**Câu 5.** Method cha có annotation, con override method không khai báo lại annotation. Java method reflection của con có tự kế thừa method annotation nhờ `@Inherited` không?  
**Đáp án:** Không. `@Inherited` dành cho annotation trên class theo quan hệ superclass, không tự áp dụng cho method override.

**Câu 6.** Spring `@Transactional` ở interface hoạt động trong một cấu hình proxy. Có đủ cơ sở kết luận custom pointcut `@annotation(MyMarker)` luôn hoạt động y hệt không?  
**Đáp án:** Không. Spring transaction metadata lookup và AspectJ `@annotation` pointcut không mặc nhiên đồng nhất.

**Câu 7.** `outer()` gọi `this.inherited()` và method kế thừa có advice. Advice đó chạy cho lời gọi nội bộ không?  
**Đáp án:** Không có một lần intercept mới qua proxy. Cần thay đổi đường gọi để đi qua proxy nếu muốn advice được kích hoạt.

## Tài liệu Spring Framework

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): giới hạn JDK/CGLIB, self-invocation và final/private.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): `execution`, `@annotation`, `@within`, `@target` và phạm vi method execution.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): đăng ký aspect bean và bật Spring AOP.
- [Spring Framework 6.2 — Using @Transactional](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html): annotation placement, interface/implementation, method visibility và self-invocation trong transaction proxy mode.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invocation-boundaries-009">Proxy lồng nhau và việc lần theo chuỗi đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Proxy lồng nhau và việc lần theo chuỗi đối tượng trong Spring AOP

## 1. Mục tiêu bài học

Trong Spring AOP, caller đôi khi không gọi trực tiếp đối tượng nghiệp vụ, mà gọi qua nhiều tầng wrapper. Một tình huống quan trọng là **target của một Spring AOP proxy lại là một Spring AOP proxy khác**. Khi đó cần biết lời gọi đi qua tầng nào, interceptor nào chạy ở đâu, và tại sao có thể mất hoặc chạy lặp advice.

Sơ đồ:

~~~text
Caller
  -> Outer Spring AOP proxy
       -> Outer interceptor/advisor
       -> Inner Spring AOP proxy
            -> Inner interceptor/advisor từ @Aspect
            -> TargetService.method()
~~~

Bài học này chỉ nói về **Spring Aspect/Spring AOP dựa trên proxy**, đặc biệt là:

1. Phân biệt **một proxy với nhiều advisors** và **nhiều proxy lồng nhau**.
2. Lần theo từng tầng bằng Spring AOP APIs: AopUtils, Advised, TargetSource và AopProxyUtils.
3. Hiểu thứ tự gọi, proceed, return, exception và các điểm cắt ngắn chuỗi.
4. Tránh truy xuất target động hoặc tự bóc proxy trong code nghiệp vụ.
5. Quan sát qua một ứng dụng Maven/Java 17+ chạy được với @Aspect thật và ProxyFactory.
6. Chẩn đoán khi transaction, cache, scoped proxy hoặc adapter khác cùng tồn tại.

Việc có nhiều lớp @Aspect hoặc nhiều annotation Spring AOP trên một bean **không tự động có nghĩa** Spring đã tạo nhiều proxy lồng nhau. Spring auto-proxy thông thường có thể đưa nhiều advisors vào **một proxy duy nhất**.

## 2. Ba cấu trúc rất khác nhau

### 2.1. Một proxy, nhiều advisors

~~~text
Client
 -> AOP Proxy [Advisor A, Advisor B, Advisor C]
     -> Target
~~~

Proxy có một TargetSource và một danh sách advisors. Nếu bean được áp dụng custom @Aspect và transaction advice, trong một cấu hình thông thường, chúng có thể cùng xuất hiện trên một Spring AOP proxy. Số lượng advisors có thể lớn hơn một nhưng số tầng proxy vẫn chỉ là một.

### 2.2. Hai proxy thật sự lồng nhau

~~~text
Client
 -> Outer Proxy [Advisor A]
     -> Inner Proxy [Advisor B]
         -> Target
~~~

Ở đây target của Outer Proxy là object Inner Proxy. Mỗi proxy có cấu hình riêng; việc gọi phương thức trên Outer Proxy có thể đi qua cả hai chuỗi interceptor.

Nếu kiểm tra:

~~~java
Advised outerAdvised = (Advised) outer;
Advisor[] advisors = outerAdvised.getAdvisors();
~~~

thì mảng đó mô tả advisors **trực tiếp của outer**, không nhất thiết hiển thị advisor trên inner.

### 2.3. Một proxy Spring AOP nằm bên trong wrapper khác

~~~text
Client
 -> Custom JDK proxy hoặc framework adapter
     -> Spring AOP proxy
         -> Target
~~~

Một proxy JDK tự tạo từ java.lang.reflect.Proxy không mặc nhiên là Spring AOP proxy. Một class có tên chứa CGLIB cũng chưa chắc là AOP proxy: Spring còn dùng class enhancement cho @Configuration và nhiều chức năng khác.

**Nguyên tắc:** Phải xác định **thực tế object nào đang được client giữ**, chứ không kết luận từ tên class hay từ việc có nhiều dòng log.

## 3. Những công cụ Spring nên biết

| API | Dùng để quan sát | Giới hạn cần nhớ |
| --- | --- | --- |
| AopUtils.isAopProxy(obj) | Object có là Spring AOP proxy không | Không chứng minh có advice của @Aspect cụ thể |
| AopUtils.isJdkDynamicProxy(obj) | Proxy AOP theo interface/JDK | Không dùng kết quả này để đếm các tầng |
| AopUtils.isCglibProxy(obj) | Proxy AOP class-based | Class được CGLIB tăng cường ngoài AOP là tình huống khác |
| obj instanceof Advised | Object có expose cấu hình advised không | Proxy được cấu hình opaque có thể không cho cast sang Advised |
| Advised.getAdvisors() | Danh sách advisors ở **tầng hiện tại** | Không gộp advisor của inner |
| Advised.getTargetSource() | Abstraction tìm target ở **tầng hiện tại** | Target có thể là singleton, scope, pool hoặc lazy |
| TargetSource.getTargetClass() | Loại target được công bố | Không cần khởi tạo hay truy xuất target thực tế |
| TargetSource.isStatic() | Có trả lại cùng một target cho các lần gọi không | Không bảo đảm đó là một plain object |
| AopProxyUtils.getSingletonTarget(obj) | Đối tượng target ngay bên dưới khi dùng SingletonTargetSource | Trả null khi không thỏa, không tự truy qua dynamic sources |
| AopProxyUtils.ultimateTargetClass(obj) | Loại target sâu nhất theo chuỗi singleton có thể truy an toàn | Không phải công cụ bóc mọi wrapper do thư viện khác tạo |

Các imports quan trọng:

~~~java
import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
~~~

**Phân biệt:** Đọc được target class không có nghĩa object proxy là instance của class đó. Với JDK proxy, kiểu runtime thường là proxy triển khai interface, không phải subclass của implementation.

## 4. Cách Spring xử lý đường gọi qua nested proxies

Giả sử outer và inner đều có around-style interceptor:

~~~text
1. Client gọi outer.method()
2. Outer interceptor bắt đầu và ghi log before
3. Outer invocation.proceed()
4. Inner proxy nhận method call
5. Inner advice chạy nếu pointcut khớp
6. Inner chuyển sang target.method()
7. Target trả kết quả
8. Inner trả kết quả cho outer
9. Outer interceptor ghi log after và trả kết quả cho client
~~~

Một interceptor đặt log trước và sau proceed thể hiện dạng:

~~~java
Object invoke(MethodInvocation invocation) throws Throwable {
    before();
    Object result = invocation.proceed();
    after();
    return result;
}
~~~

Đây là **mẫu minh họa**: before/after là tên pseudocode, không phải chương trình hoàn chỉnh. Nếu proceed ném exception, after nằm sau proceed sẽ không chạy, trừ khi bạn đặt cleanup trong finally. Nếu inner advice short-circuit và không gọi proceed (ví dụ cache hit), target có thể không được gọi.

**@Order không tự thay đổi ranh giới vật lý outer/inner.** @Order chủ yếu tham gia quy tắc sắp xếp advisors khi được framework quản lý trong một chuỗi. Với hai proxy do code đã cấu hình độc lập, outer vẫn là outer; muốn đổi thứ tự tầng phải đổi cấu trúc wrapping.

## 5. Ví dụ Java 17+ chạy được: @Aspect inner + ProxyFactory outer

Chương trình minh họa dùng:

- Spring Framework **6.2.19**, AspectJ Weaver **1.9.25.1**.
- Một Spring bean chứa logic nghiệp vụ.
- Một aspect bean với @Before, được Spring AOP áp dụng lên bean nghiệp vụ.
- Một Spring AOP ProxyFactory tạo **proxy ngoài**, bọc chính proxy trong do context trả ra.
- API quan sát cho biết có hai tầng khác nhau.

Ví dụ này **không dùng AspectJ bytecode weaving**; toàn bộ hành vi được thực hiện qua Spring AOP proxies.

### 5.1. Thư mục

~~~text
spring-nested-aop/
├── pom.xml
└── src/main/java/example/
    ├── GreetingPort.java
    ├── GreetingService.java
    ├── TraceAspect.java
    ├── AppConfig.java
    └── Main.java
~~~

### 5.2. Maven

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-nested-aop</artifactId>
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
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Trong dự án thực tế, nên dùng Spring Framework BOM hoặc Spring Boot BOM để các module spring-* thống nhất phiên bản. Ở đây ghi trực tiếp phiên bản để ví dụ độc lập, không nên sao chép các số version này vào một Spring Boot app đang quản lý phiên bản khác.

### 5.3. Interface và target

**GreetingPort.java**

~~~java
package example;

public interface GreetingPort {
    String greet(String name);
}
~~~

**GreetingService.java**

~~~java
package example;

public class GreetingService implements GreetingPort {

    @Override
    public String greet(String name) {
        System.out.println("target: greet " + name);
        return "hello " + name;
    }
}
~~~

### 5.4. Aspect của Spring

**TraceAspect.java**

~~~java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {

    @Before("execution(* example.GreetingService.greet(..))")
    public void beforeGreet() {
        System.out.println("inner aspect: before");
    }
}
~~~

Aspect khai báo advice trên method target. Class @Aspect này cần được đăng ký thành bean để Spring AOP sử dụng.

### 5.5. Cấu hình tạo inner proxy

**AppConfig.java**

~~~java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {

    @Bean
    public GreetingPort greetingService() {
        return new GreetingService();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }
}
~~~

Spring AOP nhận diện TraceAspect và advisor khớp GreetingService.greet. Với interface GreetingPort và cấu hình không ép class proxy, Spring dùng **JDK dynamic proxy** cho bean greetingService. Đừng nhầm annotation @Aspect với annotation tự đăng ký bean: chính method @Bean đã đăng ký TraceAspect.

### 5.6. Tạo proxy ngoài và kiểm tra object chain

**Main.java**

~~~java
package example;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            GreetingPort inner =
                context.getBean("greetingService", GreetingPort.class);

            ProxyFactory factory = new ProxyFactory();
            factory.setInterfaces(GreetingPort.class);
            factory.setTarget(inner);
            factory.addAdvice((MethodInterceptor) invocation -> {
                System.out.println("outer: before");
                Object result = invocation.proceed();
                System.out.println("outer: after");
                return result;
            });

            GreetingPort outer = (GreetingPort) factory.getProxy();

            System.out.println("inner is AOP proxy: "
                + AopUtils.isAopProxy(inner));
            System.out.println("outer is AOP proxy: "
                + AopUtils.isAopProxy(outer));
            System.out.println("both JDK proxies: "
                + (AopUtils.isJdkDynamicProxy(inner)
                   && AopUtils.isJdkDynamicProxy(outer)));

            Object immediate = AopProxyUtils.getSingletonTarget(outer);
            Object deepest = AopProxyUtils.getSingletonTarget(inner);

            System.out.println("outer wraps inner: "
                + (immediate == inner));
            System.out.println("inner wraps plain target: "
                + (deepest instanceof GreetingService
                   && !AopUtils.isAopProxy(deepest)));
            System.out.println("ultimate target type: "
                + AopProxyUtils.ultimateTargetClass(outer).getSimpleName());

            if (outer instanceof Advised advised) {
                System.out.println("outer advisors: "
                    + advised.getAdvisors().length);
            }

            System.out.println("result: " + outer.greet("Lan"));
        }
    }
}
~~~

Cấu trúc có chủ đích: inner lấy từ Spring container nên nó đã có advice của @Aspect. ProxyFactory được gọi **sau khi** context hoàn tất, và proxy ngoài không đăng ký lại thành Spring bean. Vì thế ví dụ không phụ thuộc vào việc container có auto-proxy thêm lớp proxy ngoài hay không.

### 5.7. Chạy chương trình

Trong thư mục chứa pom.xml:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
~~~

**Các dòng do chương trình in ra, đúng thứ tự:**

~~~text
inner is AOP proxy: true
outer is AOP proxy: true
both JDK proxies: true
outer wraps inner: true
inner wraps plain target: true
ultimate target type: GreetingService
outer advisors: 1
outer: before
inner aspect: before
target: greet Lan
outer: after
result: hello Lan
~~~

Log do Maven/framework phát ra (nếu có) không nằm trong phần stdout cố định của các System.out.println này.

### 5.8. Vì sao output như vậy?

1. **Inner proxy:** Spring tạo advisor từ TraceAspect và bọc GreetingService; AopUtils trả true.
2. **Outer proxy:** ProxyFactory tạo thêm một AOP proxy, có đúng một MethodInterceptor do ví dụ đăng ký.
3. **Hai proxy theo interface:** Cả inner và outer sử dụng JDK dynamic proxies, nhưng chiến lược JDK/CGLIB không quyết định số tầng.
4. **Outer wraps inner:** TargetSource của outer là SingletonTargetSource giữ chính instance inner.
5. **Inner wraps plain target:** TargetSource của inner là SingletonTargetSource giữ GreetingService gốc.
6. **Ultimate target:** AopProxyUtils lần qua singleton layers và xác định lớp đích GreetingService.
7. **Outer advisors: 1:** Chỉ đếm advisor gắn ở tầng outer. Lớp inner vẫn có advisor từ @Aspect.
8. **Đường gọi:** Outer nhận greet trước, in “outer: before”; proceed gọi inner, @Before trên inner in “inner aspect: before”; target in kết quả; inner trả về, outer in “outer: after”; cuối cùng Main in “result”.

Nếu trực tiếp gọi inner.greet("Lan"), outer interceptor không được kích hoạt. Nếu gọi thẳng GreetingService gốc mà không qua proxy thì cả outer và inner advice đều bị bỏ qua.

## 6. Xem advisors của từng tầng

Trong ví dụ, thay vì suy từ log, có thể in advisors/target sources riêng của outer và inner:

~~~java
// Snippet để bổ sung vào Main, không phải file độc lập.
if (outer instanceof Advised advisedOuter) {
    System.out.println("outer advisor count: "
        + advisedOuter.getAdvisors().length);
    System.out.println("outer source: "
        + advisedOuter.getTargetSource().getClass().getSimpleName());
}

if (inner instanceof Advised advisedInner) {
    System.out.println("inner advisor count: "
        + advisedInner.getAdvisors().length);
    System.out.println("inner source: "
        + advisedInner.getTargetSource().getClass().getSimpleName());
}
~~~

Con số advisor và tên class infrastructure của inner có thể khác theo Spring release hoặc các advisor bổ sung, vì vậy không hard-code chúng trong test nghiệp vụ.

**Một proxy có nhiều advisors**: outer.getAdvisors() có thể hiển thị nhiều phần tử, nhưng getSingletonTarget(outer) có thể là đối tượng nghiệp vụ thường.

**Nhiều proxy lồng nhau**: getSingletonTarget(outer) có thể là một AOP proxy khác; khi đó getAdvisors() của outer và inner cần được xem riêng.

Advised là interface của Spring AOP configuration, nhưng không phải mọi proxy đều expose interface này; proxy opaque có thể ẩn nó. Hãy dùng instanceof trước khi cast.

## 7. Lần theo singleton proxy chain an toàn

Spring cung cấp:

~~~java
Object next = AopProxyUtils.getSingletonTarget(proxy);
Class<?> deepType = AopProxyUtils.ultimateTargetClass(proxy);
~~~

getSingletonTarget chỉ trả immediate target khi proxy giữ một SingletonTargetSource. Nếu trả null, điều này **không chứng minh proxy không có target**: target source có thể thuộc loại dynamic hoặc object không phải AOP proxy dạng này.

Đoạn chẩn đoán an toàn hơn so với gọi getTarget() bừa bãi:

~~~java
// Các import cần thêm vào Main.java nếu dùng snippet này:
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;

static void traceSingletonChain(Object object) {
    Set<Object> seen =
        Collections.newSetFromMap(new IdentityHashMap<>());
    Object current = object;
    int depth = 0;

    while (current != null && seen.add(current)) {
        System.out.println("depth=" + depth
            + ", aopProxy=" + AopUtils.isAopProxy(current)
            + ", runtimeType=" + current.getClass().getName());

        Object next = AopProxyUtils.getSingletonTarget(current);
        if (next == null) {
            break;
        }
        current = next;
        depth++;
    }
}
~~~

Đây là **method snippet** để đặt trong một class với các import đã ghi, không phải một chương trình độc lập. Nó có thể nhận diện chuỗi singleton Spring AOP proxies mà không kích hoạt dynamic TargetSource.getTarget().

Dùng **identity-based** Set thay vì HashSet thông thường để tránh phụ thuộc vào equals/hashCode của proxy. Khi trace dừng tại một wrapper không phải Spring AOP hoặc một dynamic target source, hãy báo giới hạn thay vì kết luận “đây chắc chắn là target cuối cùng”.

## 8. TargetSource không phải lúc nào cũng là singleton

Spring AOP proxy có thể dùng những loại target source khác nhau:

| TargetSource | Đặc trưng | Rủi ro khi debug |
| --- | --- | --- |
| SingletonTargetSource | Giữ một đối tượng target | Có thể quan sát một lớp singleton bằng getSingletonTarget |
| PrototypeTargetSource | Có thể tạo/lấy target mới | Gọi getTarget() có thể tạo thêm instance không mong muốn |
| Scoped target source | Lấy target theo request/session/scope | Có thể không có scope hiện hành hoặc trả target khác nhau |
| Pooling target source | Mượn object từ pool | Có thể phải releaseTarget sau khi sử dụng |
| Lazy/hot-swappable target source | Target được tạo muộn hoặc thay thế | Trạng thái có thể thay đổi trong quá trình thực thi |

Vì vậy, **đừng** dùng đoạn sau như cách quan sát mặc định:

~~~java
Object target = ((Advised) proxy).getTargetSource().getTarget();
((GreetingPort) target).greet("Lan");
~~~

Nó có thể bỏ qua outer advice, tạo target mới, đòi scope đang active hoặc gây ảnh hưởng đến vòng đời target. Đặc biệt với resource/pool, việc không gọi releaseTarget theo hợp đồng có thể làm cạn resource. Business code nên tiếp tục sử dụng bean proxy do Spring cung cấp.

Để biết thông tin sơ bộ mà chưa lấy target:

~~~java
TargetSource source = ((Advised) proxy).getTargetSource();
Class<?> declaredTargetType = source.getTargetClass();
boolean constantTarget = source.isStatic();
~~~

Hai lời gọi này giúp nhận diện metadata; chúng không thay thế kiểm tra cấu hình bean thực tế.

## 9. Thứ tự advice, exception và kết thúc sớm

Cấu trúc outer/inner quyết định thứ tự đi **vào** các tầng. Với around-style interceptor:

~~~java
factory.addAdvice((MethodInterceptor) invocation -> {
    System.out.println("outer: begin");
    try {
        return invocation.proceed();
    } finally {
        System.out.println("outer: end");
    }
});
~~~

Đây là **phiên bản thay thế** interceptor trong Main.java, không dùng đồng thời với phiên bản đã viết nếu muốn so output cố định.

- Outer “begin” xuất hiện trước inner vì outer nhận lời gọi trước.
- Nếu inner trả về bình thường, outer thực thi finally rồi trả kết quả.
- Nếu inner/target ném exception, finally của outer vẫn thực thi.
- Nếu một outer around advice trả về giá trị mà **không** gọi proceed, inner/target có thể không chạy.
- Nếu inner có một cache advisor trả kết quả ngay tại tầng đó, target có thể không được gọi, nhưng outer vẫn có thể chạy before/after hoặc finally.

Phân biệt với @Order: sắp xếp advisors trong một proxy và thứ tự **các lớp proxy lồng nhau** không phải cùng một phép sắp xếp.

## 10. Những đường gọi bỏ qua một hoặc nhiều tầng

Giả sử ba tham chiếu tồn tại:

~~~java
GreetingPort outer = ...; // Outer AOP proxy
GreetingPort inner = ...; // Inner AOP proxy
GreetingPort plain = ...; // Target nghiệp vụ (ví dụ minh họa)
~~~

Kết quả:

| Caller gọi | Tầng được đi qua | Tác động |
| --- | --- | --- |
| outer.greet(...) | Outer → Inner → Target | Outer advice và inner @Aspect advice có cơ hội chạy |
| inner.greet(...) | Inner → Target | Không đi qua outer interceptor |
| plain.greet(...) | Target trực tiếp | Bỏ qua cả hai proxy |
| Target dùng this.someMethod() | Lời gọi trực tiếp trong target | Không tự quay lại inner/outer proxy |
| Outer around advice không proceed | Chỉ outer (hoặc các interceptor outer trước đó) | Inner/target không được gọi |

**Self-invocation** vẫn là một giới hạn của Spring AOP, cho dù proxy ngoài hoặc proxy trong là JDK hay CGLIB. Nested proxies không tự tạo ranh giới advice mới cho this.method() nằm bên trong target.

Đừng “unwrap” proxy rồi giữ plain target trong một field để tối ưu hiệu năng khi business behavior phụ thuộc vào transaction, cache, security hay tracing; hành động đó có thể làm mất các lời advice thiết yếu.

## 11. Các nguyên nhân hay dẫn tới nhiều wrapper trong ứng dụng Spring

Một vài trường hợp đáng điều tra:

1. Lập trình viên tạo ProxyFactory để bọc một Spring AOP bean đã có proxy.
2. Bean factory trả object vốn đã là proxy, sau đó một BeanPostProcessor khác bọc thêm.
3. Một framework tích hợp như Spring Data cung cấp repository proxy, đồng thời có thêm AOP advisor hoặc một proxy tùy biến.
4. Scoped proxy đi cùng transaction/cache/custom @Aspect và target source đặc biệt.
5. Một adapter/client wrapper ngoài Spring AOP nhận Spring AOP proxy làm target.
6. AOP infrastructure có nhiều advisor, khiến log **trông giống** nested nhưng thực tế chỉ một proxy.

Các tình huống 2–5 **không phải lúc nào cũng tạo hai tầng proxy riêng**. Spring có thể hợp nhất advisor/cấu hình hoặc sử dụng hạ tầng chung. Bởi vậy chỉ xác nhận nested khi bạn tìm thấy một proxy object có target source chứa **proxy khác**, không dựa vào số annotation hay số log.

## 12. Quy trình chẩn đoán Spring Aspect

### Bước 1: Bắt đầu từ object caller đang giữ

Đừng bắt đầu bằng class nguồn rồi giả định đó cũng là runtime class. Lấy reference từ dependency injection hoặc context, xác minh isAopProxy, isJdkDynamicProxy/isCglibProxy.

### Bước 2: Xem proxy ngoài

Nếu instanceof Advised, đọc getAdvisors, getTargetSource, getTargetClass và isStatic. Không sửa advisor runtime trên production chỉ để xem kết quả.

### Bước 3: Lần theo singleton targets, nếu có

Dùng AopProxyUtils.getSingletonTarget ở từng tầng; dừng khi trả null hoặc khi object không phải Spring AOP proxy tương thích. Có thể dùng ultimateTargetClass để lấy target type sâu nhất trong phạm vi hỗ trợ.

### Bước 4: Vẽ chuỗi object

Ghi rõ:

~~~text
caller -> outerProxy/advisor A -> innerProxy/advisor B -> realTarget
~~~

Hoặc:

~~~text
caller -> oneProxy/advisors A,B -> realTarget
~~~

Đây là hai sơ đồ khác nhau, kéo theo các chẩn đoán khác nhau.

### Bước 5: Theo dõi proceed/return

Log ở những điểm đầu/cuối interceptor. Xác định advice nào gọi proceed, advice nào có thể cache hit/short-circuit, exception được catch hay propagate.

### Bước 6: So với pointcut của @Aspect

Một proxy có tồn tại không có nghĩa mọi method đều khớp. Kiểm tra Spring-managed aspect bean, advisor đúng target/method, kiểu proxy, visibility và đường gọi qua proxy.

### Bước 7: Kiểm tra ranh giới bên ngoài và nội bộ

Có caller nào lấy inner thay outer, lấy plain target, tự new một object hoặc gọi this.method() bên trong target? Các cách gọi đó có thể tạo trace khác nhau dù method nghiệp vụ giống nhau.

### Bước 8: Kiểm thử với context thực tế

Integration test nên gọi từ tham chiếu Spring cấp cho caller, quan sát cả kết quả nghiệp vụ và advice; không chỉ dựa vào số dòng log của target.

## 13. Lỗi thường gặp

| Dấu hiệu | Kết luận vội | Kiểm tra đúng |
| --- | --- | --- |
| Có hai dòng before | Chắc chắn hai proxy | Có thể một proxy với hai advisors |
| Outer chỉ có một advisor | Không có advice ở trong | Phải kiểm tra inner riêng |
| isAopProxy trả true | Chắc chắn từ @Aspect của mình | Có thể proxy do transaction/cache/scope |
| getSingletonTarget trả null | Không có target | Có thể dynamic TargetSource hoặc proxy không phù hợp |
| ultimateTargetClass trả GreetingService | Proxy cast được về GreetingService | JDK proxy vẫn có thể chỉ triển khai GreetingPort |
| @Order không đảo outer và inner | Spring lỗi ordering | Các tầng vật lý khác với advisor order trong một proxy |
| Gọi trực tiếp target mất tracing | Aspect không tồn tại | Đường gọi đã bỏ qua proxy |
| Scoped proxy getTarget thất bại | Bean mất | Có thể scope đang không active |
| Hai lần log một advice | Chắc chắn do nested proxies | Có thể đăng ký aspect bean trùng, nhiều advisor hoặc gọi method hai lần |
| Target không được gọi | Proxy lỗi | Có thể một around advice trả sớm hoặc cache hit |

## 14. Câu hỏi vận dụng

**Câu 1.** Một AOP proxy có ba advisors. Có chắc chắn ba tầng proxy?

**Trả lời:** Không. Có thể cả ba advisors nằm trên đúng một proxy.

**Câu 2.** Outer có một advisor, immediate singleton target của outer là một Spring AOP proxy khác. Có thể kết luận gì?

**Trả lời:** Có ít nhất hai tầng AOP proxy trong chuỗi singleton này. Cần xem advisors/target source của inner để hiểu tiếp.

**Câu 3.** Vì sao outer.getAdvisors() không thấy advice do @Aspect của inner tạo ra?

**Trả lời:** Nó chỉ liệt kê advisors thuộc cấu hình outer, không tự gộp inner advisors.

**Câu 4.** Nếu caller đổi từ outer.greet() sang inner.greet() thì outer advice có chạy không?

**Trả lời:** Không, vì lời gọi bỏ qua outer proxy. Inner advice có thể vẫn chạy.

**Câu 5.** Có nên gọi getTarget() nhiều lần để bóc scoped proxy trong môi trường production?

**Trả lời:** Không. Target source có thể động, phụ thuộc scope và có hợp đồng release/lifecycle. Hãy quan sát metadata hoặc dùng helper singleton khi an toàn.

**Câu 6.** Outer around interceptor không gọi proceed. Có bắt buộc inner và target nhận lời gọi không?

**Trả lời:** Không. Interceptor ngoài có thể short-circuit toàn bộ đường gọi.

## 15. Tài liệu nguồn chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB proxies, proxy boundary và self-invocation.
- [Spring Framework — Creating AOP Proxies Programmatically](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html): ProxyFactory, addAdvice, addAdvisor, getProxy.
- [Spring Framework — Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html): quan sát advisors và cấu hình proxy.
- [Spring Framework — TargetSource Implementations](https://docs.spring.io/spring-framework/reference/core/aop-api/targetsource.html): singleton/dynamic target sources.
- [Spring Javadoc — AopProxyUtils](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/framework/AopProxyUtils.html): getSingletonTarget và ultimateTargetClass.
- [Spring Javadoc — Advised](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/framework/Advised.html): getAdvisors và getTargetSource.
- [Spring Javadoc — AopUtils](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/support/AopUtils.html): cách nhận diện AOP proxy và target class.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="invocation-boundaries-010">Phân biệt cùng phương thức đích nhưng khác đường đi của lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt cùng phương thức đích nhưng khác đường đi của lời gọi trong Spring AOP

## 1. Câu hỏi trọng tâm: cùng method, vì sao advice chạy khác nhau?

Một method nghiệp vụ có thể trả đúng kết quả dù advice không chạy. Khi chẩn đoán Spring AOP, hãy ghi **receiver reference tại chính lệnh gọi**: Spring-injected proxy, `this` trên target, raw target được giữ riêng hay instance tạo bằng `new`. Sau đó mới kiểm tra method/pointcut, proxy type và thời điểm lifecycle.

Bài này tập trung **đối chiếu call paths có kiểm soát và điều tra khi log/transaction/audit biến mất**. Demo dùng cùng method `TaskService.run(String)` với các nhãn input khác nhau để phân biệt output; ba lượt đầu dùng cùng singleton target Spring, lượt `new` là đối chứng instance khác. Khi cần cô lập hoàn toàn ảnh hưởng của input, có thể giữ cùng chuỗi input cho từng lượt thử.

## 2. Vẽ caller → receiver → proxy → target

```text
Bean khác -> injected TaskService proxy -> advisor (nếu match) -> target.run()
Main      -> context TaskService proxy  -> advisor (nếu match) -> target.run()
Main      -> TaskService proxy.outer()  -> target.outer() -> this.run()
Main      -> new TaskService().run()    -> plain target.run()
```

Hai đường đầu vào `run` qua proxy; đường thứ ba chỉ qua proxy ở `outer`, còn `this.run` không quay qua đó; đường cuối hoàn toàn ở ngoài Spring container. Điều này tách **method body executed** khỏi **advice executed**. Có proxy thôi chưa đủ: bean phải có advisor phù hợp, method phải intercept được và call site phải dùng đúng reference. Bảng dưới đây đưa các tình huống lifecycle và proxy type vào cùng phép đối chiếu.

## 3. Đối chiếu cùng method `run(..)` theo đường đi lời gọi

| Đường gọi | Reference chứa ở caller | Có đi qua Spring AOP proxy vào `run(..)`? | Dự đoán với advice chỉ nhắm `run(..)` |
| --- | --- | --- | --- |
| `context.getBean(TaskService.class).run("A")` | Spring bean proxy từ context | Có, nếu bean được proxy và method intercept được | Advice chạy nếu pointcut match. |
| Bean X inject `TaskService` rồi gọi `taskService.run("A")` | Spring-injected proxy | Có | Advice chạy nếu pointcut match. |
| Controller inject `TaskService` rồi gọi `run("A")` | Spring-injected proxy | Có | Advice chạy nếu match. |
| `TaskService.outer()` gọi `this.run("A")` | `this` là target instance đang chạy | **Không**; self-invocation | Advice riêng của `run(..)` bị bypass. |
| `TaskService.outer()` gọi `run("A")` không ghi `this` | Vẫn là lời gọi nội bộ ngầm định trên `this` | **Không** | Kết quả giống dòng trên. |
| `new TaskService().run("A")` | Object thủ công ngoài container | Không có Spring proxy | Spring advice không chạy. |
| `@PostConstruct` trên chính TaskService gọi `run("A")` | Target đang initialization | Không nên coi đây là client call qua proxy hoàn chỉnh | Không trông đợi advice của `run(..)`. |
| `@PostConstruct` trên bean X gọi bean TaskService đã inject và được proxy | Tham chiếu sang **proxy của bean khác** | **Có thể có** | Advice có thể chạy nếu TaskService đã hoàn tất khởi tạo/proxy. |
| Caller dùng JDK proxy nhưng cố gọi method không thuộc interface proxy | Không có entry method tương ứng trên proxy | Không thể gọi trực tiếp theo kiểu interface ấy | Cần điều chỉnh contract hoặc proxy type. |
| Một method `final` trên class-based CGLIB proxy | Class proxy không override được method final | Không intercept method ấy | Advice không chạy dù bean có proxy. |

Bảng dùng các điều kiện đúng của Spring Framework; các trường hợp cụ thể có thể còn chịu tác động từ nhiều advisors, scope, target source và cấu hình hạ tầng. Không suy rằng “tên method giống nhau” thì Spring AOP phải hành xử giống nhau.

### Phân biệt “target method chạy” và “advice chạy”

Trong cả bốn đường gọi (proxy, internal, collaborator, `new`), **mã `run(..)` vẫn có thể chạy và trả cùng chuỗi**. Thứ khác nhau là sự hiện diện của **đoạn interception** trước/sau target method. Vì thế, kiểm tra return value đơn độc có thể khiến lỗi AOP bị bỏ sót: kết quả nghiệp vụ nhìn có vẻ đúng nhưng audit, metrics, transaction hoặc bảo mật dựa trên proxy đã bị bỏ qua.

## 4. Demo Spring Framework đầy đủ: một method đích, bốn đường gọi

Demo độc lập dùng **Java 17+**, **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**, Gradle. Đây là cấu hình Spring Framework thuần để chính `@EnableAspectJAutoProxy` bật hạ tầng; không phụ thuộc Spring Boot auto-configuration.

Mục tiêu: bốn lần thực thi **cùng method Java `TaskService.run(String)`** với cùng logic, nhưng `@Before` advice **chỉ thực thi hai lần** — tương ứng hai đường gọi thực sự đi qua Spring proxy.

### 4.1. Cấu trúc project

```text
spring-aop-call-path-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/callpath/
    ├── AppConfig.java
    ├── TaskService.java
    ├── OtherService.java
    ├── TraceAspect.java
    └── Main.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-aop-call-path-demo'
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
    mainClass = 'com.example.callpath.Main'
}
```

Các version trên xác định một môi trường demo. Với Spring Boot, dùng starter/BOM tương ứng với Boot major version của ứng dụng, không ép version Spring riêng lẻ theo ví dụ này.

### 4.2. Target có phương thức đích và phương thức gọi nội bộ

**`TaskService.java`**

```java
package com.example.callpath;

public class TaskService {
    public String run(String source) {
        System.out.println("target:" + source);
        return "done:" + source;
    }

    public String outer(String source) {
        return this.run(source);
    }
}
```

Cả hai là public method và class không final; vì vậy giới hạn self-invocation không bị lẫn với vấn đề `private` hoặc `final`. Spring sẽ chỉ gắn một advisor của demo vào `run(..)`, **không gắn vào `outer(..)`**.

### 4.3. Bean khác được inject đúng Spring proxy

**`OtherService.java`**

```java
package com.example.callpath;

public class OtherService {
    private final TaskService taskService;

    public OtherService(TaskService taskService) {
        this.taskService = taskService;
    }

    public String callTask(String source) {
        return taskService.run(source);
    }
}
```

Điều cần nhận ra: `OtherService` không dùng `new TaskService()`; Spring config sẽ inject instance đã được xử lý bởi container. Nếu bean `TaskService` có proxy, dependency được inject chính là **proxy reference** phù hợp.

### 4.4. Aspect chỉ nhắm một method

**`TraceAspect.java`**

```java
package com.example.callpath;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    private final AtomicInteger counter = new AtomicInteger();

    @Before("execution(public * com.example.callpath.TaskService.run(..))")
    public void beforeRun() {
        int value = counter.incrementAndGet();
        System.out.println("advice:run#" + value);
    }

    public int invocationCount() {
        return counter.get();
    }
}
```

`@Before` tăng counter **chỉ khi method `run(..)` thực sự đi qua advice**. Counter này dùng để minh họa trong một aspect bean singleton; nó không phải bộ đếm toàn cluster. `AtomicInteger` giúp mỗi lần increment có semantics thread-safe nếu gọi đồng thời.

### 4.5. Cấu hình Spring bean và AOP

**`AppConfig.java`**

```java
package com.example.callpath;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    TraceAspect traceAspect() {
        return new TraceAspect();
    }

    @Bean
    TaskService taskService() {
        return new TaskService();
    }

    @Bean
    OtherService otherService(TaskService taskService) {
        return new OtherService(taskService);
    }
}
```

`TraceAspect` trở thành Spring-managed `@Aspect` bean thông qua `@Bean`, không cần `@Component`. `@EnableAspectJAutoProxy` bật xử lý `@AspectJ` và auto-proxy. `proxyTargetClass=true` chọn class-based proxy để `context.getBean(TaskService.class)` hợp lệ với concrete class không định nghĩa interface.

Hai `@Bean` methods còn lại tạo target và bean caller. Khi Spring tạo `OtherService`, tham số `TaskService` nhận Spring bean/proxy theo cơ chế injection. Đây là mối liên kết quan trọng để đường gọi giữa hai bean thật sự đi qua proxy.

### 4.6. Main in riêng từng đường gọi

**`Main.java`**

```java
package com.example.callpath;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            TaskService task = context.getBean(TaskService.class);
            OtherService other = context.getBean(OtherService.class);
            TraceAspect aspect = context.getBean(TraceAspect.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(task));

            System.out.println("path=direct-proxy");
            System.out.println("result=" + task.run("direct"));

            System.out.println("path=self-invocation");
            System.out.println("result=" + task.outer("internal"));

            System.out.println("path=another-bean");
            System.out.println("result=" + other.callTask("collaborator"));

            System.out.println("path=manual-new");
            TaskService plainObject = new TaskService();
            System.out.println("result=" + plainObject.run("plain"));

            System.out.println("adviceCount=" + aspect.invocationCount());
        }
    }
}
```

### 4.7. Chạy và kiểm tra stdout chính xác

Tại thư mục project, với Gradle đã cài:

```bash
gradle run --quiet
```

Nếu đã có Gradle Wrapper thì dùng `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows).

**Dòng do chương trình in, theo đúng thứ tự** (không tính warning/log của JVM, Gradle hoặc Spring nếu logging environment bật):

```text
proxy=true
path=direct-proxy
advice:run#1
target:direct
result=done:direct
path=self-invocation
target:internal
result=done:internal
path=another-bean
advice:run#2
target:collaborator
result=done:collaborator
path=manual-new
target:plain
result=done:plain
adviceCount=2
```

### 4.8. Đọc từng case theo đường đi thật

**Case 1 — direct-proxy**

`task.run("direct")` được gọi trên bean `TaskService` lấy từ `ApplicationContext`. Bean đã được auto-proxy, pointcut nhắm `run(..)`. Vì vậy có `advice:run#1` trước `target:direct`.

**Case 2 — self-invocation**

`task.outer("internal")` đi qua Spring proxy ở entry `outer(..)`; tuy nhiên aspect **không có advice dành cho `outer(..)`**. Sau khi code vào `TaskService` target, `this.run("internal")` gọi trực tiếp sang method trên chính target, không qua proxy của `TaskService`. Do đó **không có `advice:run#2` ở case này**.

**Case 3 — another-bean**

`other.callTask("collaborator")` chạy logic của `OtherService`. Field `taskService` đã được Spring inject bằng `TaskService` proxy. Lời gọi `taskService.run("collaborator")` đi **vào proxy từ bên ngoài target**, advice chạy. Counter tăng thành `2`, xuất hiện `advice:run#2` rồi `target:collaborator`.

**Case 4 — manual-new**

`plainObject.run("plain")` thực thi đúng thân method Java của `TaskService` nhưng trên object tạo bởi `new` **ngoài Spring container**. Không có proxy AOP ở reference này nên không xuất hiện advice.

**Kết luận từ demo:** cùng method `run(..)` được thực thi bốn lần, target body in `target:...` bốn lần, nhưng `@Before` advice chỉ chạy **hai lần**. Với ba case đầu, `TaskService` target bean được Spring quản lý là một; case cuối cố ý đổi object instance để làm đối chứng.

## 5. Cô lập biến “cùng target” trong phép thử

Để không nhầm việc đổi object với việc đổi đường gọi, hãy so sánh **ba case đầu** của mục 4. `TaskService` do `@Bean` tạo là singleton mặc định: `task.run("direct")` và `other.callTask("collaborator")` đều đi qua proxy tới bean ấy, còn `task.outer("internal")` đi vào cùng target nhưng tự gọi `this.run`. Bộ đếm advice bằng `2` trong khi thân `run` chạy ba lần trên Spring target.

Case `manual-new` chạy trên object khác và chỉ dùng để đối chứng phạm vi container. Một unit test `new TaskService()` có thể xác minh return value nhưng không chứng minh advice Spring đang hoạt động.

## 6. Đổi thời điểm gọi: constructor và init callback

Constructor hay `@PostConstruct` **trên chính TaskService target** gọi `run()` qua `this` trước khi proxy hoàn chỉnh được trao cho caller thông thường; không dùng các callback đó để xác nhận advice nhắm `run()`. Ngược lại, callback của **bean khác** có thể gọi qua proxy của TaskService nếu dependency đã hoàn tất khởi tạo. Đừng dùng `context.getBean` từ chính constructor để “lấy proxy sớm” vì có thể tạo circular dependency hoặc early reference.

Với startup work cần chạy qua advice, dùng một coordinator khác ở mốc context phù hợp, ví dụ `ContextRefreshedEvent` hoặc Boot runner, rồi kiểm chứng **receiver thực tế**. Bài chuyên về lifecycle phân tích sâu thứ tự các callback và điều kiện ngoại lệ.

## 7. Khi advice bọc outer(..) mà inner(..) không được bọc

Một ngộ nhận phổ biến là “caller đã qua proxy ở method ngoài, vậy mọi method bên trong cũng mặc nhiên qua AOP”.

Xét:

```java
@Aspect
@Component
class TwoMethodsAspect {
    @Before(
        "execution(* com.example.callpath.TaskService.outer(..))"
        + " || execution(* com.example.callpath.TaskService.run(..))"
    )
    public void beforeBoth(JoinPoint point) {
        System.out.println("advice:" + point.getSignature().getName());
    }
}
```

Đây là **biến thể minh họa riêng**, cần import `JoinPoint`, `Aspect`, `Before`, `Component` và component scan để được đăng ký; **không thêm vào demo chính**, tránh thay đổi stdout.

Khi caller gọi `taskProxy.outer("A")`:

1. Proxy intercept `outer(..)`, advice cho `outer` có thể chạy.
2. Target thực thi `outer(..)`.
3. `this.run("A")` chạy trực tiếp trên target.
4. Advice nhắm `run(..)` **không tự chạy lần nữa** chỉ vì `outer(..)` đã có advice.

Vì vậy, log có thể cho thấy `advice:outer`, sau đó method `run` vẫn được gọi, nhưng **không có `advice:run`**. Có proxy ở entry không tạo ra một “vòng proxy” tự động cho nested calls.

Ngược lại, nếu `outer(..)` chuyển sang gọi một **injected collaborator bean** có proxy và pointcut match, một advice khác có thể chạy ở ranh giới mới. Đây là ranh giới kiến trúc khác, không phải nested self-call trong cùng target.

## 8. Hệ quả nếu đường gọi bypass interceptor

| Concern được bật phù hợp | Qua proxy và advisor match | Self-call trên target |
| --- | --- | --- |
| Custom `@Aspect` audit | Có thể ghi audit event | Không chạy advice riêng của method inner. |
| `@Transactional` | Áp dụng propagation theo transaction interceptor | Inner không tự kích hoạt interceptor mới; có thể vẫn nằm trong transaction đã tồn tại. |
| `@Cacheable` | Có thể thực hiện cache lookup/put | Inner bỏ qua cache advice. |
| `@Async` hoặc method security | Có thể chạy executor/authorization rule | Không có interception riêng trên inner call. |

Mỗi concern đòi hỏi activation/configuration của chính nó; `@EnableAspectJAutoProxy` không tự bật hết những tính năng này. Nếu refactor đổi đường đi, hãy kiểm thử transaction outcome, cache hit, thread thực thi hoặc quyết định authorization thực tế. Return value giống nhau chưa đủ để chứng minh policy đã chạy.

## 9. Receiver và khả năng intercept: kiểm tra có điều kiện

**JDK dynamic proxy** chỉ expose những method thuộc proxied interface; **CGLIB proxy** có thể expose concrete class method nhưng không override được `final`/`private` methods. Cả hai đều không khiến `this.inner()` tự quay lại proxy. Với reflection, `method.invoke(receiver, args)` chỉ được Spring AOP intercept khi **receiver là proxy** và method callable phù hợp; reflection không tự đảm bảo có hoặc không có advice.

`AopUtils.isAopProxy(bean)`, `isJdkDynamicProxy` và `isCglibProxy` giúp nhận diện reference; `Advised.getAdvisors()` giúp xem chain nếu proxy triển khai `Advised`. Không lấy `equals`, tên runtime class hay sự hiện diện proxy làm bằng chứng một **method cụ thể** đã được advice. Bộ đếm/event recorder ở mục 11 mới kiểm chứng kết quả thực thi.

## 10. Chọn cách sửa theo policy và call site

| Tình huống đã chứng minh | Hướng xử lý | Điều phải kiểm thử lại |
| --- | --- | --- |
| Policy áp dụng cho **toàn operation ngoài** | Nhắm advice vào entry `outer(..)` đang đi qua proxy | Transaction, retry, exception và phạm vi policy có thể thay đổi. |
| Method inner là trách nhiệm nghiệp vụ riêng | Chuyển nó sang **Spring collaborator bean**, inject dependency rồi gọi qua proxy | Bean có được quản lý, pointcut cũ còn match class/method mới, semantics còn đúng. |
| Bất khả kháng phải gọi lại proxy của chính bean | Cân nhắc self-injection với `@Lazy` hoặc `ObjectProvider`; quản lý vòng đời và kiểu proxy | Circular dependencies, scope, và tình huống gọi quá sớm. |
| Bất khả kháng cần `AopContext.currentProxy()` | Chỉ trong proxy invocation đã bật `exposeProxy=true`, cast đúng JDK/CGLIB proxy | `IllegalStateException` khi thiếu context, đệ quy và coupling. |

Chuyển sang AspectJ weaving là thay đổi mô hình interception, cần thiết lập compiler/agent riêng; `@EnableAspectJAutoProxy` không tự tạo weaving. Hai bài riêng về collaborator, self-injection và `AopContext` cung cấp ví dụ sâu; tại đây ưu tiên **xác nhận call path trước và sau sửa**.

## 11. Kiểm tra Spring context đúng cách

### 11.1. Xác nhận bean lấy ra là proxy

```java
TaskService bean = context.getBean(TaskService.class);
System.out.println("isProxy=" + AopUtils.isAopProxy(bean));
System.out.println("isClassProxy=" + AopUtils.isCglibProxy(bean));
```

Đây là đoạn minh họa dùng `context` từ demo và import `AopUtils`. Với `AppConfig` của demo hai dòng lần lượt in `true` và `true`. Không dùng type concrete khi đã cấu hình JDK interface proxy; lúc đó lấy và inject theo interface.

**Proxy tồn tại không đủ kết luận.** Một bean có nhiều advisors, và advisor cần xét có thể không match method đang gọi. Sau bước kiểm tra proxy phải chạy một method và quan sát rõ advice event hoặc recorder.

### 11.2. Đếm invocation của advice

`TraceAspect` trong demo có `AtomicInteger` và method `invocationCount()`. Sau bốn case, `adviceCount=2` trong khi thân `TaskService.run` đã thực thi bốn lần. Đây là một phép đo trực tiếp sự khác biệt giữa **method body executions** và **advised executions**.

Nếu muốn test tự động, có thể thay console bằng một `EventRecorder` Spring bean chứa collection thread-safe, inject recorder vào aspect, gọi các case rồi assert:

- `directProxy` tạo một audit event.
- `selfInvocation` không tạo audit event riêng cho inner method.
- `otherBean` tạo thêm một event.
- `manualNew` không tạo event.
- Business outputs vẫn đúng hợp đồng.

Trong test này, **method event** mới là điều chứng minh AOP chạy, không phải chỉ `AopUtils.isAopProxy`.

### 11.3. Đặt breakpoint ở hai nơi

Đặt breakpoint ở `TraceAspect.beforeRun` và `TaskService.run`. Khi gọi `task.outer("internal")`, debugger vào `run` nhưng không vào `beforeRun` cho lần gọi đó. Khi gọi `task.run("direct")`, debugger vào advice trước target. Khi gọi `other.callTask("collaborator")`, đường đi tương tự direct proxy.

Điều này giúp chứng minh lỗi AOP không đồng nghĩa lỗi gọi method — method body vẫn chạy đúng, chỉ bỏ qua interceptor chain.

### 11.4. Kiểm tra pointcut nếu đường gọi đã qua proxy nhưng vẫn thiếu advice

Nếu một case chắc chắn đi qua Spring bean proxy mà advice không chạy, hãy kiểm tra:

1. Aspect class có `@Aspect` và đã được đăng ký thành bean qua `@Component` hoặc `@Bean` chưa?
2. Trong Spring Framework Java config có `@EnableAspectJAutoProxy` và `aspectjweaver` chưa, hoặc trong Boot AOP auto-configuration có phù hợp không?
3. Pointcut `execution(..)`, `@annotation(..)` hoặc `bean(..)` có nhắm đúng **method/type/bean runtime**?
4. Bean proxy JDK có expose method qua interface không? Class proxy có gặp `final`/`private` không?
5. Client có giữ proxy reference thật hay tự tạo `new`, giữ raw target, gọi nội bộ qua `this`?
6. Có nhiều `ApplicationContext` hoặc nhiều bean instances không? Aspect/advisor được bật ở context nào?
7. Có advice khác nuốt exception, trả sớm, thay đổi args hoặc điều phối async khiến log xuất hiện ở thời điểm khác không?

Đừng thêm `@EnableAspectJAutoProxy` trùng lặp hoặc đổi sang CGLIB khi chưa xác định nguyên nhân.

## 12. Những tình huống dễ suy luận sai

| Quan sát | Kết luận vội thường sai | Diễn giải đúng theo Spring AOP |
| --- | --- | --- |
| Hai lời gọi có cùng source code của target method | “Advice phải chạy giống nhau” | Cần xem một lời gọi qua proxy, lời kia qua `this` hay raw object. |
| `AopUtils.isAopProxy(task)==true` | “Mọi method đều có advice” | Chỉ biết proxy tồn tại; cần check method/pointcut và call path. |
| `outer()` được advice | “`this.inner()` cũng được advice” | Target self-call không quay qua proxy. |
| Thêm `public` vào inner method | “Đã sửa AOP” | Visibility chưa giải quyết self-invocation. |
| Bật `proxyTargetClass=true` | “CGLIB tự chặn internal call” | Vẫn là Spring proxy-based interception và self-call bypass. |
| Bean khác gọi method từ `@PostConstruct` | “Mọi init call đều bypass” | Nếu reference tới target khác đã proxy hoàn chỉnh, advice có thể chạy. |
| Tạo `new TaskService()` bên trong bean | “Vì caller là Spring bean, target cũng được AOP” | Object tự tạo không tự biến thành proxy của Spring. |
| Method có `@Transactional` | “Chắc chắn đang transaction” | Cần activation, proxy boundary và kiểm tra transaction thực tế. |
| Gọi qua reflection | “Reflection luôn bypass/luôn kích hoạt advice” | Phụ thuộc receiver có phải proxy và method được expose hay không. |
| Đổi sang collaborator bean rồi không thấy advice | “Spring AOP không hỗ trợ nhiều bean” | Kiểm tra collaborator thật sự được Spring quản lý và pointcut còn match. |

## 13. Quy trình phân tích một bug thực tế

### Bước 1 — Xác định method cụ thể

Ghi rõ FQCN, tên, signature, annotation, bean name và pointcut. Không dừng ở “method có annotation mà không hoạt động”.

### Bước 2 — Ghi lại đường đi caller → receiver

Vẽ các references: controller/service/coordinator/target; phân biệt `this`, DI proxy, instance `new`, bean từ context. **Receiver** ở đúng lệnh gọi tới method là ai?

### Bước 3 — Kiểm tra loại proxy và khả năng intercept

Dùng `AopUtils` trên bean do context trả về. Nếu JDK proxy, method phải nằm trên interface; nếu CGLIB, method phải override được. Không nhầm giới hạn final/private với self-invocation.

### Bước 4 — Xác nhận aspect bean và matching

Kiểm tra `getBeanNamesForType(TraceAspect.class)`, enable config/AspectJ dependency và pointcut match. Nếu advice chưa được container nhận diện, mọi đường proxy cũng không giúp được.

### Bước 5 — Thử hai call paths có kiểm soát

Từ một test Spring context thật, gọi `taskProxy.run("X")`, rồi gọi `taskProxy.outer("X")` có self-call cùng method. Dùng recorder/counter xác nhận khác biệt. Giữ input giống nhau để cô lập nguyên nhân đường đi.

### Bước 6 — Thay bằng injected collaborator nếu cần

Tách operation cần advice sang một Spring bean khác hoặc đổi boundary cho policy. Cập nhật pointcut theo method mới; tránh object tạo bằng `new` và circular dependency.

### Bước 7 — Test semantics ngoài log

Nếu concern là security, transaction, cache, async, metric, retry, hãy kiểm tra chính hành vi mong muốn, đặc biệt lỗi/rollback/side effects; đừng kết luận từ log advice duy nhất. Khi refactor làm advice bắt đầu chạy, semantics sản xuất có thể thay đổi đáng kể.

### Bước 8 — Rà soát startup và concurrency

Tách constructor/`@PostConstruct` của target ra khỏi entry call bình thường; kiểm tra bean lifecycle. Với async/đa luồng, không đặt giả định tuyệt đối về thứ tự log.

## 14. Checklist tổng kết

- [ ] Đã phân biệt **method body có chạy** với **advice có chạy**?
- [ ] Đã xác định đúng **receiver reference** tại lệnh gọi (`this`, DI proxy, `new`, raw target)?
- [ ] Nếu so hai đường gọi, đã giữ nguyên target method/pointcut/input để tránh nhiễu?
- [ ] Aspect có `@Aspect` và là Spring bean; auto-proxy support được bật?
- [ ] Spring target bean có proxy phù hợp và public/interface entry method intercept được?
- [ ] Đã kiểm tra `this.inner()` và implicit `inner()` đều là self-invocation?
- [ ] Nếu caller là bean khác, dependency có lấy từ Spring container/DI không?
- [ ] Với constructor/`@PostConstruct`, đã tính đến proxy chưa hoàn tất?
- [ ] Đã tránh giả định CGLIB, JDK proxy hoặc reflection tự giải quyết mọi call paths?
- [ ] Nếu chuyển sang collaborator, đã cập nhật pointcut và test exception/return/transaction/security semantics?
- [ ] Đã dùng integration test Spring context + recorder/counter đo đúng số advice invocations?
- [ ] Không dựa vào một thông điệp log hoặc `isAopProxy=true` để kết luận toàn bộ?

**Điểm nhớ:** Trong Spring AOP, **cùng một method đích vẫn có thể có hai hành vi interception khác nhau vì đường đi lời gọi khác nhau**. Spring proxy chỉ áp dụng advice khi lời gọi đi qua proxy thích hợp; `this.method()` và đối tượng ngoài container không có cùng ranh giới ấy. Khi debug, vẽ **caller → receiver → proxy → target**, rồi xác nhận bằng test có context thật.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Proxying Mechanisms / Understanding AOP Proxies](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — Declaring an Aspect](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/at-aspectj.html)
- [Spring Framework 6.2 — Pointcut Declarations](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html)
- [Spring Framework 6.2 — Bean Lifecycle / Initialization](https://docs.spring.io/spring-framework/reference/6.2/core/beans/factory-nature.html)


</details>

- [Quay lại đầu trang](#back-to-top)
