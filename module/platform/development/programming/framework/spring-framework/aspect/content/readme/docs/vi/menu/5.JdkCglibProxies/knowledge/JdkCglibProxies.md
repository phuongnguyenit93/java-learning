<a id="back-to-top"></a>

# JDK và CGLIB

## Menu
- [Proxy JDK dựa trên interface và bề mặt phương thức được cung cấp](#jdk-cglib-proxies-001)
- [Proxy CGLIB dựa trên lớp con của lớp đích](#jdk-cglib-proxies-002)
- [Quy tắc chọn cơ chế proxy trong Spring Framework](#jdk-cglib-proxies-003)
- [Ép proxy lớp bằng proxyTargetClass và tác động toàn cấu hình](#jdk-cglib-proxies-004)
- [Giới hạn lớp final và phương thức final khi dùng CGLIB](#jdk-cglib-proxies-005)
- [Phương thức private, static và lời gọi không thể ghi đè](#jdk-cglib-proxies-006)
- [Phạm vi public, protected và package-visible theo cơ chế proxy](#jdk-cglib-proxies-007)
- [Kiểu tham chiếu khi tiêm phụ thuộc qua interface hoặc lớp cụ thể](#jdk-cglib-proxies-008)
- [Phân biệt lớp proxy, lớp đích và kiểu khai báo của bean](#jdk-cglib-proxies-009)
- [Hạn chế liên quan đến Java Module System và quyền truy cập lớp](#jdk-cglib-proxies-010)
- [equals, hashCode, toString và hành vi cần kiểm chứng trên proxy](#jdk-cglib-proxies-011)
- [Tình huống lỗi ép kiểu khi chuyển cơ chế proxy](#jdk-cglib-proxies-012)

## <a id="jdk-cglib-proxies-001">Proxy JDK dựa trên interface và bề mặt phương thức được cung cấp</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: JDK dynamic proxy dựa trên interface và bề mặt phương thức được cung cấp

## 1. Vai trò của JDK proxy trong Spring AOP

Trong Spring AOP, **JDK dynamic proxy** là proxy Java được Spring tạo để chặn lời gọi đến **các phương thức public được công bố qua interface của bean**. Nếu Spring chọn JDK proxy cho một target có interface, caller nhận đối tượng proxy **triển khai interface**, còn đối tượng nghiệp vụ thật nằm phía sau proxy.

Ví dụ:

```java
public interface BillingPort {
    void pay(String orderId);
}

public class BillingService implements BillingPort {
    @Override
    public void pay(String orderId) {
        System.out.println("paid " + orderId);
    }

    public void implementationOnly() {
        System.out.println("internal API");
    }
}
```

Đây là fragment minh họa, chưa phải chương trình chạy độc lập. Với **JDK proxy cho `BillingService`**, caller có thể dùng `BillingPort.pay(...)` trên reference proxy. Phương thức `implementationOnly()` chỉ khai báo ở implementation nên **không được JDK proxy cung cấp qua interface**; ngay cả khi public và pointcut mô tả nó, Spring AOP qua JDK proxy cũng không có lời gọi bên ngoài nào đến phương thức này để intercept.

Luồng xử lý:

```text
Spring IoC container
    |
    +-- đăng ký bean target BillingService
    |           |
    |           v
    |      khởi tạo target
    |           |
    |       Spring AOP
    |           |
    |    advisor/pointcut khớp
    |           |
    |   JDK proxy implements BillingPort
    |           |
    +------ inject proxy vào consumer
                |
          port.pay("A1")
                |
            JDK proxy
                |
         interceptor/advice
                |
       BillingService.pay("A1")
```

**Điều kiện cần nhớ:** không phải cứ có interface là Spring tự tạo proxy. Target cần là Spring bean, hạ tầng AOP phải được cấu hình và có advisor/advice áp dụng. JDK proxy chỉ là **cách tạo proxy khi cần**, không phải cơ chế đăng ký bean.

Bài này chỉ phân tích JDK dynamic proxy trong **Spring AOP dựa trên proxy**. Không mở rộng sang các cơ chế weaving bytecode của AspectJ.

## 2. “Dựa trên interface” có nghĩa chính xác là gì?

### 2.1. Proxy triển khai interface, không kế thừa implementation class

Khi cấu hình chọn JDK proxy, object mà Spring công bố thường có các tính chất:

- `proxy instanceof BillingPort` là `true`: proxy thực hiện hợp đồng interface.
- `proxy instanceof BillingService` là `false`: JDK proxy không phải subclass của `BillingService`.
- Nếu ép `(BillingService) proxy`, một runtime cast không phù hợp sẽ ném `ClassCastException`.
- Nếu bean được inject vào constructor nhận `BillingPort`, kiểu tương thích. Nếu constructor bắt buộc nhận `BillingService` trong khi Spring trả JDK proxy, việc resolve dependency có thể thất bại do không tương thích kiểu.

Trong ứng dụng Spring, mã consumer nên nhận **interface mà proxy thực sự triển khai**:

```java
@Component
public class CheckoutHandler {
    private final BillingPort billing;

    public CheckoutHandler(BillingPort billing) {
        this.billing = billing;
    }

    public void checkout(String orderId) {
        billing.pay(orderId);
    }
}
```

Đoạn này minh họa constructor injection. Một constructor duy nhất thường không cần `@Autowired`. Muốn JDK proxy hoạt động, `CheckoutHandler` và `BillingService` phải được container quản lý và Spring AOP phải được bật.

### 2.2. Proxy hỗ trợ những phương thức business nào?

**Bề mặt phương thức business** của JDK proxy được xác định bởi các interface mà proxy được cấu hình để triển khai:

| Phương thức / thành viên | Gọi được từ reference JDK proxy? | Ý nghĩa với Spring AOP |
| --- | --- | --- |
| `public void pay(...)` khai báo trong `BillingPort` | Có | Có thể bị advice nếu pointcut khớp và caller đi qua proxy |
| `default String channel()` trong interface | Có | Là public interface method; có thể được advice khi có pointcut tương ứng |
| Method kế thừa từ superinterface | Có | Thuộc hợp đồng public interface của proxy |
| Một method từ interface thứ hai mà proxy cũng triển khai | Có | Spring có thể proxy nhiều interface; consumer phải có kiểu phù hợp để gọi |
| `public void implementationOnly()` chỉ nằm trên class target | Không, qua bề mặt interface JDK proxy | Pointcut đúng tên cũng không làm proxy tự xuất bản method này |
| Method `protected` hoặc package-private chỉ có trên implementation | Không | Không phải public interface contract của JDK proxy |
| Method `private` của target | Không | Không được expose và không thể gọi bên ngoài qua JDK proxy |
| `static` method trên interface/class | Không như lời gọi instance proxy | Gọi theo tên type; không đi qua AOP instance proxy |
| `private` helper method trong interface | Không từ bên ngoài | Chỉ được dùng nội bộ interface; không phải API của proxy |

**Ngoại lệ quan trọng về phạm vi quan sát:** Một JDK proxy là một Java object nên còn có các phương thức thuộc `Object` và đôi khi các interface hạ tầng Spring AOP. Không nên phát biểu rằng `proxy.getClass().getMethods()` chứa **đúng và chỉ đúng** các method trong một interface nghiệp vụ. Phần cần quan tâm là **những phương thức ứng dụng có thể gọi theo hợp đồng business interface**.

Không phải cứ method nằm trong interface là advice chắc chắn chạy: nó còn phải được advisor/pointcut chọn. Interface quyết định **method có thể được caller gọi trên proxy**; pointcut quyết định **lời gọi nào được advice**.

### 2.3. Nhiều interface và kế thừa interface

Một target có thể triển khai nhiều interface:

```java
interface PaymentOperations {
    void pay(String id);
}

interface AuditOperations {
    void audit(String id);
}

class PaymentEngine implements PaymentOperations, AuditOperations {
    public void pay(String id) { /* ... */ }
    public void audit(String id) { /* ... */ }
    public void internalRebuild() { /* ... */ }
}
```

Nếu Spring dùng JDK proxy mặc định cho các interface của target, proxy có thể triển khai cả `PaymentOperations` và `AuditOperations`. Vì vậy:

- Consumer theo `PaymentOperations` gọi `pay`; consumer theo `AuditOperations` gọi `audit`.
- Consumer đang giữ biến kiểu `PaymentOperations` không thể gọi `audit` qua biến đó trừ khi đổi kiểu sang interface tương thích mà proxy thực sự thực hiện.
- `internalRebuild` không thuộc hai interface nên không thể gọi qua JDK proxy.
- Với cấu hình Spring AOP cấp thấp hoặc giới hạn exposed interfaces có chủ đích, danh sách interface được proxy thực sự cung cấp có thể được tùy chỉnh. Hãy kiểm tra object được inject thay vì giả định.

Bề mặt API của JDK proxy không phải **toàn bộ public methods** của implementation class. Đây là điểm khác biệt quan trọng với class-based proxy (CGLIB).

## 3. Spring chọn JDK proxy như thế nào?

### 3.1. Spring Framework thuần với `@EnableAspectJAutoProxy`

Khi dùng cách khai báo `@Aspect`, cần đăng ký aspect làm Spring bean và kích hoạt auto-proxy:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
@ComponentScan("demo.springjdk")
class AopConfiguration {
}
```

Đây là snippet cấu hình. Trong Spring Framework thuần, `proxyTargetClass=false` là mặc định của `@EnableAspectJAutoProxy`; với target có interface phù hợp, Spring thường tạo JDK proxy. Nếu target không có interface, Spring có thể dùng CGLIB dù giá trị cấu hình là `false`.

Để dùng `@Aspect` trong Spring Framework thuần cần các dependency Spring Context/AOP và thư viện AspectJ cho việc phân tích pointcut. Không cần chạy AspectJ compiler hoặc load-time weaving để dùng Spring AOP proxy.

### 3.2. Spring Boot có mặc định khác

Spring Boot có AOP auto-configuration khi dependency thích hợp (chẳng hạn `spring-boot-starter-aop`) hiện diện. Theo mặc định, Boot ưu tiên **CGLIB class-based proxies**. Nếu muốn JDK proxy đối với bean có interface, cấu hình:

```properties
spring.aop.proxy-target-class=false
```

Trong Spring Framework thuần, `@EnableAspectJAutoProxy(proxyTargetClass=false)` thường đã đủ để ưu tiên JDK proxy. Trong Spring Boot, hãy kiểm tra property trên và các cấu hình proxy khác trước khi kết luận loại proxy.

Spring có thể hợp nhất nhiều yêu cầu auto-proxy cùng context; cấu hình ép class-based proxy từ một thành phần khác có thể khiến giá trị thực tế khác mong đợi. Vì thế `AopUtils.isJdkDynamicProxy(bean)` là phép kiểm tra thực nghiệm tốt hơn việc chỉ đọc một annotation.

### 3.3. So sánh đúng phạm vi với CGLIB

| Tiêu chí | JDK proxy trong Spring AOP | CGLIB trong Spring AOP |
| --- | --- | --- |
| Hình thức | Tạo object triển khai interface | Tạo subclass của target class |
| Cần interface trên target? | Có interface để công bố qua JDK proxy | Không bắt buộc |
| Consumer nên inject | Interface | Interface hoặc class tương thích |
| Method public chỉ có ở implementation | Không được proxy công bố | Có thể được intercept nếu lời gọi đi qua proxy và phương thức có thể override |
| Target là `final class` | Có thể dùng JDK proxy nếu target triển khai interface thích hợp | Không subclass được `final class` |
| Target có `final` implementation method công bố trong interface | JDK proxy vẫn có thể intercept lời gọi **vào interface** do không cần override target method | Không override được final method nên không advice theo cách class proxy |
| Self-invocation `this.method()` | Bỏ qua proxy | Cũng bỏ qua proxy |

Chọn loại proxy vì **hợp đồng dependency và bề mặt method cần advice**; tránh ép CGLIB chỉ để sửa một lỗi injection nếu interface đã mô tả đúng API nghiệp vụ.

## 4. Điểm dễ nhầm giữa “interface method” và “pointcut match”

### 4.1. Pointcut có thể nói đến implementation method được interface công bố

Spring AOP có thể xử lý pointcut mô tả method thực thi trên concrete target:

```java
@Before("execution(* demo.springjdk.BillingService.pay(..))")
public void beforePay() {
    System.out.println("[aop] before pay");
}
```

Dù caller đang gọi `BillingPort.pay(...)` qua JDK proxy, method thực thi đằng sau là `BillingService.pay(...)`. Spring dùng thông tin target class khi đánh giá pointcut; nên biểu thức dựa trên concrete implementation như trên vẫn có thể khớp.

Ngược lại, nếu pointcut chọn `BillingService.implementationOnly()` mà method ấy **không có trên proxy interface**, không có lời gọi bên ngoài qua JDK proxy để kích hoạt advice cho nó. Đổi riêng pointcut không mở rộng bề mặt interface của JDK proxy.

### 4.2. `this` và `target` trong Spring AOP không nhất thiết cùng kiểu

Trong Spring AOP, `this(...)` trong biểu thức pointcut diễn tả **proxy object**; `target(...)` diễn tả **target object**. Với JDK proxy cho `BillingService implements BillingPort`:

- `this(demo.springjdk.BillingPort)` có thể khớp vì proxy triển khai `BillingPort`.
- `this(demo.springjdk.BillingService)` không khớp đối với JDK proxy vì proxy không kế thừa class ấy.
- `target(demo.springjdk.BillingService)` có thể khớp vì đối tượng thực thi là target class.

Đây là ý nghĩa **cụ thể của Spring proxy**. Cần hiểu chúng khi chuyển một cấu hình AOP từ CGLIB sang JDK proxy hoặc ngược lại.

### 4.3. Annotation trên interface và implementation

Khi làm việc với các annotation được Spring xử lý qua AOP như `@Transactional` hoặc custom marker kết hợp `@annotation`, đừng coi việc annotation nằm trên interface và nằm trên implementation là luôn tương đương trong **mọi cấu hình proxy, advice và pointcut**.

- Một advice có thể kiểm tra annotation trên method và target class theo quy tắc riêng.
- Một số hạ tầng Spring (ví dụ transaction metadata) tìm annotation theo cả method triển khai và class.
- Một biểu thức pointcut cụ thể như `@annotation(...)` có cách match riêng; cần kiểm tra thực tế khi annotation được đặt ở interface nhưng method thực thi ở class.
- Đặt annotation tại nơi framework liên quan có thể nhận diện rõ ràng và viết integration test cho lời gọi qua proxy.

Phạm vi của JDK proxy vẫn dựa vào interface được công bố, bất kể annotation xuất hiện trên method hay class nào.

## 5. Ví dụ đầy đủ: interface công bố hai method, implementation còn method thứ ba

Ví dụ dùng **Java 17**, **Maven** và **Spring Framework 6.2.x** không cần Spring Boot. Hạ tầng Spring AOP tạo **JDK proxy** cho bean `BillingService` có interface `BillingPort`. Người dùng sẽ quan sát:

1. Reference được Spring inject là JDK proxy và không phải instance của concrete class.
2. Lời gọi `pay(...)` trên proxy chạy advice.
3. Method `implementationOnly(...)` có trên target nhưng không có trong bề mặt JDK proxy.
4. Lời gọi `this.pay(...)` bên trong target bỏ qua proxy (self-invocation).

Cấu trúc file:

```text
spring-jdk-proxy/
├── pom.xml
└── src/main/java/demo/springjdk/
    ├── AppConfig.java
    ├── BillingPort.java
    ├── BillingService.java
    ├── BillingAspect.java
    ├── ClientJob.java
    └── Main.java
```

### 5.1. Tệp `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-jdk-proxy</artifactId>
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

### 5.2. Tệp `src/main/java/demo/springjdk/BillingPort.java`

```java
package demo.springjdk;

public interface BillingPort {
    void pay(String orderId);
    void submit(String orderId);
}
```

Hai phương thức public này thuộc **API nghiệp vụ của proxy**. Vì được interface công bố, cả `pay` và `submit` đều có thể được gọi trên reference `BillingPort`; tuy nhiên aspect bên dưới chỉ advice `pay`.

### 5.3. Tệp `src/main/java/demo/springjdk/BillingService.java`

```java
package demo.springjdk;

import org.springframework.stereotype.Service;

@Service
public class BillingService implements BillingPort {
    @Override
    public void pay(String orderId) {
        System.out.println("[service] pay " + orderId);
    }

    @Override
    public void submit(String orderId) {
        System.out.println("[service] submit " + orderId);
        this.pay(orderId); // Self-invocation: không đi qua AOP proxy.
    }

    public void implementationOnly(String orderId) {
        System.out.println("[service] implementationOnly " + orderId);
    }
}
```

Lưu ý: `implementationOnly` là public method của class, nhưng **không nằm trong `BillingPort`**; bởi vậy JDK proxy không expose method này ra business surface.

### 5.4. Tệp `src/main/java/demo/springjdk/BillingAspect.java`

```java
package demo.springjdk;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class BillingAspect {
    @Before("execution(* demo.springjdk.BillingService.pay(..))")
    public void beforePay() {
        System.out.println("[aspect] before pay");
    }
}
```

Aspect được Spring component scan phát hiện vì có `@Component`. Biểu thức `execution` chọn method `pay` trên target class. Các method không khớp, ví dụ `submit`, sẽ không được advice chỉ vì cùng nằm trên proxy.

### 5.5. Tệp `src/main/java/demo/springjdk/AppConfig.java`

```java
package demo.springjdk;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {
}
```

Spring scan toàn bộ package chứa `AppConfig`. `BillingPort` là interface, còn `BillingService` có `@Service` nên được đăng ký bean. `@EnableAspectJAutoProxy` kích hoạt hạ tầng AOP, và `proxyTargetClass=false` ưu tiên JDK dynamic proxy cho bean này.

### 5.6. Tệp `src/main/java/demo/springjdk/ClientJob.java`

```java
package demo.springjdk;

import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

@Component
public class ClientJob {
    private final BillingPort billing;

    public ClientJob(BillingPort billing) {
        this.billing = billing;
        System.out.println("[client] injectedJdkProxy="
                + AopUtils.isJdkDynamicProxy(billing));
    }

    public void run() {
        System.out.println("[client] run");
        billing.pay("ORD-1");
        billing.submit("ORD-2");
    }
}
```

`ClientJob` yêu cầu dependency qua **interface**, nên reference JDK proxy tương thích với constructor. `pay` chạy advice; `submit` không được aspect này chọn, còn lời gọi `this.pay(...)` trong `submit` bypass proxy.

### 5.7. Tệp `src/main/java/demo/springjdk/Main.java`

```java
package demo.springjdk;

import java.util.Arrays;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            BillingPort port = context.getBean(BillingPort.class);

            System.out.println("[main] isJdkProxy="
                    + AopUtils.isJdkDynamicProxy(port));
            System.out.println("[main] isImplementation="
                    + (port instanceof BillingService));

            boolean exposesOnlyMethod =
                    Arrays.stream(port.getClass().getMethods())
                            .anyMatch(m -> m.getName().equals("implementationOnly"));
            System.out.println("[main] extraVisible=" + exposesOnlyMethod);

            context.getBean(ClientJob.class).run();

            // Tạo target Java riêng; không thuộc Spring AOP proxy.
            new BillingService().implementationOnly("ORD-3");
        }
    }
}
```

Dùng reflection ở đây chỉ để **quan sát proxy surface**, không phải cách business code nên gọi service. `getMethods()` cũng chứa những method khác từ Java Object và framework interfaces; phép kiểm tra chỉ hỏi riêng tên `implementationOnly`.

### 5.8. Lệnh chạy và kết quả

Trong thư mục có `pom.xml`, chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.springjdk.Main
```

Những dòng `System.out` của chương trình (không tính log của Maven hay Spring nếu môi trường in thêm):

```text
[client] injectedJdkProxy=true
[main] isJdkProxy=true
[main] isImplementation=false
[main] extraVisible=false
[client] run
[aspect] before pay
[service] pay ORD-1
[service] submit ORD-2
[service] pay ORD-2
[service] implementationOnly ORD-3
```

**Giải thích theo thứ tự:**

1. Khi tạo singleton `ClientJob`, Spring resolve `BillingPort` và inject proxy. Constructor in `injectedJdkProxy=true`.
2. `context.getBean(BillingPort.class)` nhận cùng loại reference Spring quản lý; `isJdkProxy=true` xác minh loại proxy thực tế.
3. `isImplementation=false` cho thấy object proxy không phải instance của concrete class; `extraVisible=false` cho thấy method chỉ trên implementation không được xuất bản trên JDK proxy.
4. `billing.pay("ORD-1")` qua JDK proxy khiến advice in `before pay`, rồi target thực thi `pay`.
5. `billing.submit("ORD-2")` đi vào proxy nhưng aspect không chọn `submit`. Target `submit` dùng `this.pay("ORD-2")`, bỏ qua proxy, nên **không có advice** trước dòng `pay ORD-2`.
6. `new BillingService().implementationOnly("ORD-3")` gọi phương thức trực tiếp trên một object Java tự tạo. Method vẫn chạy, nhưng không có Spring AOP advice.

**Điểm cần phân biệt:** dòng cuối không chứng minh `implementationOnly` không thể chạy trong Java. Nó chỉ minh họa bề mặt JDK proxy của bean và việc tạo object riêng không chịu interception của Spring AOP.

## 6. Thử nghiệm mở rộng và kết quả cần quan sát

### 6.1. Muốn advice `implementationOnly` thì đổi gì?

Nếu vẫn giữ JDK proxy, chỉ thêm:

```java
@Before("execution(* demo.springjdk.BillingService.implementationOnly(..))")
public void beforeImplementationOnly() {
    System.out.println("[aspect] before implementationOnly");
}
```

vào `BillingAspect` sẽ **không đủ** để caller truy cập implementation-only method qua `BillingPort`. Snippet này là phần method bổ sung của aspect, không phải file Java chạy độc lập.

Hai hướng thiết kế hợp lệ:

1. **Đưa method vào interface nghiệp vụ thích hợp**, ví dụ thêm `void implementationOnly(String id)` vào `BillingPort` nếu nó thực sự thuộc API công khai. Khi ấy JDK proxy có thể expose method, pointcut phù hợp có thể advice lời gọi qua proxy.
2. **Dùng class-based proxy** cho những trường hợp cố ý expose public method của concrete class, miễn class/method có thể proxy. Với Spring Framework thuần, có thể cấu hình `@EnableAspectJAutoProxy(proxyTargetClass = true)`. Cân nhắc thiết kế interface trước khi ép loại proxy.

Thêm method vào interface là thay đổi hợp đồng API; không nên làm chỉ để đưa một helper nội bộ vào AOP.

### 6.2. Đổi sang CGLIB để so sánh surface

Trong `AppConfig` đổi:

```java
@EnableAspectJAutoProxy(proxyTargetClass = true)
```

Với class `BillingService` không `final`, Spring tạo class-based proxy. Hai kết quả đầu khi kiểm tra sẽ khác:

```text
[main] isJdkProxy=false
[main] isImplementation=true
```

Và phép kiểm tra `implementationOnly` trong `Main` thành `extraVisible=true` vì proxy subclass kế thừa method public này. Để xác nhận advice cho method mới, cần thêm pointcut phù hợp **và gọi qua Spring proxy**; không phải cứ CGLIB là mọi method đều có advice.

Lưu ý đoạn constructor `ClientJob` sẽ in `injectedJdkProxy=false`, vì proxy class-based không được `isJdkDynamicProxy` nhận diện.

### 6.3. Đổi consumer sang inject implementation khi đang JDK proxy

Nếu thay constructor:

```java
public ClientJob(BillingService billing) {
    // ...
}
```

trong khi Spring đang tạo JDK proxy, có thể gặp lỗi tạo bean hoặc resolve dependency do object proxy không là `BillingService`. Đây là lỗi **hợp đồng kiểu**, không phải do pointcut `pay`. Cách ưu tiên: giữ `BillingPort` là kiểu dependency. Nếu cần concrete class thật sự, xem xét class proxy và giới hạn của nó.

### 6.4. Nếu class target là `final`?

Một `final class BillingService implements BillingPort` vẫn có thể được Spring AOP áp dụng **JDK interface proxy**, bởi proxy không cần subclass target. Tương tự, một implementation method `final` nhưng được public interface khai báo có thể bị advice khi caller gọi qua JDK proxy: proxy chặn lời gọi ở interface boundary, rồi ủy quyền đến target method.

Với CGLIB, final class không thể subclass và final method không thể override để intercept. Đây là khác biệt thiết kế có ý nghĩa, dù không nên cố tình lạm dụng `final` chỉ để chọn một công nghệ proxy.

### 6.5. Nếu gọi method từ chính target?

Ngay cả khi method nằm trong interface, tự gọi `this.pay()` bên trong target vẫn **không đi qua proxy**, nên advice không tự chạy. Có thể chuyển nghiệp vụ cần intercept sang Spring bean khác và inject dependency; đây là cách rõ ràng hơn so với lạm dụng `AopContext.currentProxy()`.

## 7. Những lưu ý khi thiết kế interface cho Spring AOP

### Chỉ đưa hành vi public cần thiết vào interface

Một interface nhỏ, ổn định, mô tả ranh giới nghiệp vụ làm consumer phụ thuộc ít hơn vào implementation. Đừng expose method hỗ trợ kỹ thuật/private concern chỉ để có thể advice qua JDK proxy.

Nếu ứng dụng có nhiều interface, hãy chú ý bean thực sự được Spring công bố có những interface nào; các interface kỹ thuật có thể vô tình làm API của proxy rộng hơn dự kiến. Hãy dùng kiểu interface cần thiết ngay tại điểm injection.

### Lưu ý default method của interface

Một interface có thể khai báo default method:

```java
public interface BillingPort {
    void pay(String orderId);

    default String providerName() {
        return "default";
    }
}
```

`providerName()` là public instance method thuộc interface nên caller của JDK proxy có thể gọi nó. Việc advice chạy vẫn phụ thuộc pointcut và lời gọi qua proxy. Trong trường hợp default method gọi một method khác trong cùng interface, cần kiểm tra thực tế đường dispatch/proxy trong Spring phiên bản sử dụng; không suy đoán advice chỉ từ việc method là `default`.

Ngược lại, `static` methods không phải instance method trên proxy; private interface helpers không thuộc API public.

### Exceptions, overload và generics vẫn theo hợp đồng Java

JDK proxy thể hiện signature của các interface. Khi caller giữ reference kiểu `BillingPort`, compiler kiểm tra tham số, kiểu trả về và checked exceptions theo interface ấy. Overloaded method cần được khai báo đủ trên interface để caller chọn overload tương ứng.

Spring AOP có thể thực hiện advice trên từng overload nếu pointcut khớp; việc generics được biên dịch thành erased types/bridge methods có thể ảnh hưởng cách một pointcut hoặc reflection nhìn method. Với tình huống phức tạp, viết integration test trên **bean được DI thực tế**, thay vì suy luận từ source signature một cách máy móc.

### Proxy không biến object tạo bằng `new` thành Spring bean

Nếu code sử dụng `new BillingService()` và giữ object ấy, Spring không tự cấp JDK proxy. Ngược lại, phương thức `@Bean` **được Spring gọi** có thể dùng `new BillingService()` rồi trả về object cho container; Spring vẫn có cơ hội hậu xử lý và tạo proxy. Điểm quyết định là reference object được quản lý và công bố qua container.

### Phân biệt proxy interface và target class khi debug

Dùng công cụ Spring:

```java
AopUtils.isAopProxy(bean);
AopUtils.isJdkDynamicProxy(bean);
AopUtils.isCglibProxy(bean);
```

Đây là các lời gọi API minh họa trên một biến `bean` lấy từ context. Không nên dựa vào tên class runtime, hậu tố của proxy hoặc việc `getClass()==Target.class` khi cần xác định chế độ AOP. Nếu aspect không chạy, kiểm tra cả **đường gọi** (qua proxy hay self-invocation), pointcut, giới hạn surface và cấu hình bean.

## 8. Checklist khắc phục sự cố thực tế

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra / xử lý |
| --- | --- | --- |
| Inject interface chạy, inject class cụ thể thất bại | Bean là JDK proxy chỉ triển khai interface | Dùng interface ở injection point; kiểm tra `AopUtils.isJdkDynamicProxy` |
| Service có public helper nhưng không gọi được trên proxy | Helper chỉ có ở implementation | Đưa vào interface nếu thật sự là API, hoặc cân nhắc class proxy |
| Tăng phạm vi pointcut vẫn không intercept implementation-only method | Method không được JDK proxy expose | Xác định trước surface của proxy và cách caller gọi |
| Có `@Aspect` nhưng bean không thành proxy | Aspect chưa được đăng ký thành Spring bean, chưa bật AOP hoặc pointcut không khớp | Kiểm tra `@Component`/`@Bean`, `@EnableAspectJAutoProxy` và context |
| Tưởng mặc định Spring Boot là JDK proxy | Boot thường mặc định CGLIB | Xem `spring.aop.proxy-target-class=false` và xác minh proxy |
| Spring Framework đã đặt `proxyTargetClass=false` nhưng vẫn CGLIB | Target không có interface hoặc có cấu hình auto-proxy khác ưu tiên class proxy | Kiểm tra target interfaces và các cấu hình chung |
| Interface có `pay` nhưng advice không chạy khi `this.pay()` | Self-invocation bỏ qua proxy | Gọi qua bean khác được DI |
| Aspect dùng `this(ImplementationClass)` không khớp | Với JDK proxy, `this` là proxy không kế thừa implementation | Xem `this(Interface)` hoặc `target(ImplementationClass)` phù hợp |
| Method `final` vẫn được advice qua JDK proxy | JDK proxy intercept interface method, không override target | Phân biệt giới hạn JDK proxy với CGLIB |
| API có nhiều interface nhưng consumer chỉ thấy một nhóm method | Kiểu biến tham chiếu chỉ công bố interface mà nó khai báo | Dùng kiểu interface phù hợp; xác minh proxy thực hiện các interface cần thiết |

Quy trình phân tích một lỗi cụ thể: xác định target và aspect có thuộc Spring container → xác nhận có proxy → xác nhận proxy là JDK hay CGLIB → kiểm tra caller giữ reference kiểu nào → so sánh method được gọi với interface proxy công bố → kiểm tra pointcut → kiểm tra lời gọi có đi qua proxy hay không.

## 9. Câu hỏi tự kiểm tra

**1. Một class có ba public methods nhưng interface chỉ khai báo một. Với JDK proxy, caller gọi được mấy public method nghiệp vụ qua interface ấy?** Một method theo hợp đồng interface đó (ngoài những method khác của `Object`/hạ tầng nếu xét reflection). Hai public method chỉ có ở implementation không tự được JDK proxy công bố.

**2. Vì sao `getBean(BillingPort.class)` thường hoạt động nhưng yêu cầu bean theo `BillingService.class` có thể lỗi?** Vì JDK proxy triển khai interface, không phải subclass của implementation.

**3. Một method implementation đánh dấu `final` có nhất thiết ngăn AOP advice qua JDK proxy không?** Không. Nếu method được interface công bố và caller gọi qua JDK proxy, việc final trên target không cản trở interception tại interface boundary.

**4. Có thể để pointcut nhắc tới concrete implementation khi đang dùng JDK proxy không?** Có. Spring xét thông tin target class khi match pointcut; nhưng chỉ những lời gọi qua các method mà proxy thực sự công bố mới được intercept.

**5. Bật `proxyTargetClass=false` có bảo đảm 100% là JDK proxy không?** Không, còn phụ thuộc target có interface, các cấu hình auto-proxy khác và loại bean. Kiểm tra reference thực tế với `AopUtils`.

**6. Interface có method `pay`; `submit()` bên trong target gọi `this.pay()`. Advice cho `pay` có chạy không?** Không qua self-invocation đó. Đưa lời gọi qua một reference Spring proxy khi thực sự cần advice.

## Tài liệu Spring chính thức

- [Spring Framework — AOP Proxies](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-proxies.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework — @AspectJ support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-002">Proxy CGLIB dựa trên lớp con của lớp đích</a>

<details>
<summary>Xem chi tiết</summary>

# Proxy CGLIB dựa trên lớp con của lớp đích trong Spring AOP

## 1. CGLIB proxy của Spring AOP là gì?

Trong Spring AOP, một bean được áp dụng advice có thể được trả về cho client dưới dạng **proxy**. Khi Spring chọn **CGLIB (class-based proxy)**, proxy là một lớp **được sinh tại runtime và kế thừa lớp đích (target class)**, thay vì chỉ triển khai các interface như JDK dynamic proxy.

Ví dụ một bean có kiểu `OrderService`:

```java
public class OrderService {
    public String submit(String id) {
        return "OK:" + id;
    }
}
```

Nếu Spring AOP áp dụng advice cho `submit()` và dùng CGLIB, cấu trúc có thể hình dung:

```text
        Spring ApplicationContext
                  |
                  | cấp bean reference
                  v
       OrderService$$SpringCGLIB$$...
             (proxy subclass)
                  |
                  | extends
                  v
             OrderService
              (target type)

Client --> proxy.submit("A01")
              |
              +--> chạy interceptor/advice phù hợp
              |
              +--> delegate tới target OrderService.submit(...)
              |
              +--> trả giá trị / ném exception cho client
```

Tên class proxy thực tế do Spring sinh và **không phải API ổn định**; đừng kiểm tra proxy bằng cách so khớp chuỗi tên class. Dùng `AopUtils.isCglibProxy(bean)` để quan sát.

Điểm cốt lõi: **proxy có quan hệ kế thừa với target type nhưng Spring AOP vẫn tách ý niệm proxy và đối tượng target**. Khi interceptor tiếp tục lời gọi, thông thường nó ủy quyền việc thực thi nghiệp vụ tới đối tượng target được Spring quản lý. Đừng suy rằng mọi lệnh chạy trong phương thức target đều chạy trên chính object proxy chỉ vì kiểu của proxy là subclass.

**Phạm vi:** giải thích Spring AOP class-based proxy, cách Spring sinh và dùng subclass, lúc nào cấu hình CGLIB, method nào được chặn và các giới hạn thực tế. Không mở rộng sang công nghệ weaving hoặc lý thuyết Aspect tổng quát.

## 2. Vì sao Spring cần proxy dựa trên lớp con?

Spring AOP thường dùng một trong hai loại proxy:

| Tiêu chí | JDK dynamic proxy | CGLIB class-based proxy |
| --- | --- | --- |
| Nền tảng | API proxy của JDK | Bộ sinh subclass được Spring dùng qua `spring-core` |
| Quan hệ kiểu | Triển khai các interface được công bố | Kế thừa lớp target (nếu có thể kế thừa) |
| Target không có business interface | Không đủ interface để cung cấp API nghiệp vụ qua JDK proxy | Thường là lựa chọn cần thiết |
| Có thể gọi qua biến kiểu implementation? | Thường không, vì proxy không phải implementation | Có, vì proxy là subtype của implementation |
| Method không khai báo trong interface | Không gọi được qua interface của JDK proxy | Có thể được chặn nếu method đủ điều kiện proxy và pointcut khớp |
| Giới hạn đặc trưng | Caller chỉ sử dụng interface của proxy | Không thể subclass `final class`; không override `final/private method` |

Nếu target **không triển khai interface**, Spring AOP có thể tạo CGLIB proxy để client vẫn inject/gọi bean theo lớp cụ thể. Nếu target có interface, Spring Framework thuần thường ưu tiên JDK proxy khi chưa yêu cầu target-class proxy. Nhưng ứng dụng có thể **ép CGLIB** ngay cả khi có interface.

Không nên xem CGLIB là phương tiện tự động “chặn mọi lời gọi trong class”. Phạm vi của **Spring AOP vẫn là method execution đi qua Spring proxy**.

## 3. Spring tạo subclass và ủy quyền lời gọi ra sao?

Một sơ đồ lớp giúp phân biệt **lớp proxy** với **target instance**:

```text
   OrderService (lớp Java bạn viết)
      ^
      | extends
      |
   OrderService$$SpringCGLIB$$... (lớp sinh runtime)
      ^
      |
   bean reference mà client nhận từ Spring

   Proxy -- intercept public overrideable method -->
        MethodInterceptor / advisor chain
                |
                +-- proceed --> target instance của OrderService
                |
                +-- kết quả / exception --> client
```

CGLIB sinh subclass để override các method mà nó có thể can thiệp. Trong method override sinh ra, Spring tích hợp logic định tuyến tới AOP interceptor chain. Nếu có advisor/pointcut phù hợp cho method execution hiện tại, advice được thực thi ở ranh giới đó; nếu advice tiếp tục, lời gọi được chuyển tới target.

Đây chỉ là **mô hình khái niệm**, không phải mã nguồn subclass thực tế. Spring dùng các thành phần nội bộ, callback và tối ưu riêng để sinh code; client không phải viết subclass hoặc interceptor bằng tay.

Một `@Around` advice minh họa:

```java
@Around("execution(* com.example.app.service..*.save(..))")
public Object trace(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("BEFORE " + pjp.getSignature().getName());
    Object result = pjp.proceed();
    System.out.println("AFTER " + pjp.getSignature().getName());
    return result;
}
```

Đây là **method minh họa** cần được đặt trong Spring bean `@Aspect` và có các import cần thiết. `pjp.proceed()` nghĩa là **tiếp tục qua phần còn lại của interceptor chain**, cuối cùng tới target nếu không bị short-circuit. Nó không có nghĩa “gọi thẳng target và bỏ qua mọi advice khác”.

Nếu method ném exception, exception truyền ngược qua chain; các advice ngoài có thể quan sát, ném lại hoặc xử lý theo hợp đồng của chúng. CGLIB không thay đổi ngữ nghĩa cơ bản này so với JDK proxy.

## 4. Khi nào Spring chọn CGLIB proxy?

### 4.1. Target class không có interface

Một Spring bean được khai báo như sau:

```java
@Service
public class InvoiceService {
    public String createInvoice(String id) {
        return "INV:" + id;
    }
}
```

Nếu advisor áp dụng cho `createInvoice()` và Spring AOP cần tạo proxy, **không có business interface**, Spring có thể dùng CGLIB class-based proxy. Đoạn class này là **mã minh họa**, chưa phải chương trình độc lập vì cần import Spring và context chứa bean.

### 4.2. Chủ động ép class-based proxy

Trong Spring Framework thuần, bật @AspectJ auto-proxy và yêu cầu target-class proxy:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfig {
}
```

Trong XML:

```xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
```

Trong Spring Boot khi phần AOP auto-configuration hoạt động, property thường dùng:

```properties
spring.aop.proxy-target-class=true
```

Boot 3.x dùng class-based proxy làm lựa chọn mặc định cho Spring AOP auto-configuration; có thể chọn `false` để trở về chiến lược dựa trên interface khi bean có interface. **Chỉ riêng `false` không cấm CGLIB** cho bean không có interface.

Spring có thể hợp nhất nhiều cấu hình auto-proxy creator. Một cấu hình `proxyTargetClass=true` ở nơi khác có thể chi phối toàn context; đừng kết luận loại proxy chỉ từ một annotation/property rời rạc.

### 4.3. Không phải mọi bean đều nhận proxy

CGLIB chỉ được dùng trong **ngữ cảnh thực sự tạo proxy**. Một bean không có advisor phù hợp có thể tiếp tục là object thông thường. Sự tồn tại của dependency `spring-aop` hoặc `@EnableAspectJAutoProxy` **không tự chứng minh** mọi bean đều là proxy.

Cũng cần tách biệt **Spring AOP CGLIB proxy** với cơ chế tăng cường class cấu hình `@Configuration(proxyBeanMethods=true)`. Cả hai có thể liên quan đến sinh subclass, nhưng mục đích khác nhau: một bên chặn method execution theo advisor, bên kia điều phối lời gọi giữa các `@Bean` methods. Đừng đánh đồng chúng khi dùng `AopUtils.isAopProxy(...)`.

## 5. Thí nghiệm chạy được: CGLIB subclass, advice và self-invocation

Ví dụ sử dụng **Java 17+, Maven, Spring Framework 6.2.19**. Có một service **không khai báo interface**, hai method nghiệp vụ thường và một method `final`. Một aspect advice cả `quote()` và `finalStatus()`, nhưng CGLIB chỉ có thể chặn lời gọi tới method có thể override.

Kết quả cho thấy:

1. Bean do Spring cấp đúng là Spring AOP CGLIB proxy.
2. Client có thể giữ bean bằng **kiểu lớp cụ thể** `PricingService`.
3. Lời gọi client → proxy → `quote()` chạy advice trước target.
4. `process()` ở target gọi `this.quote("INNER")`; lời gọi **không quay lại proxy**, nên không chạy advice `quote()` lần hai.
5. `finalStatus()` không được subclass override, nên không có advice dù pointcut ghi rõ tên method đó. Đây không phải lời hứa chung cho các proxy công nghệ khác.

Cấu trúc project:

```text
spring-cglib-proxy/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── CglibProxyDemo.java
```

**Tệp `pom.xml`:**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-cglib-proxy</artifactId>
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
                    <mainClass>demo.CglibProxyDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Tệp `src/main/java/demo/CglibProxyDemo.java`:**

```java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class CglibProxyDemo {
    public static class PricingService {
        public String quote(String code) {
            System.out.println("TARGET quote " + code);
            return "PRICE:" + code;
        }

        public void process() {
            System.out.println("TARGET process");
            this.quote("INNER");
        }

        public final void finalStatus() {
            System.out.println("FINAL status");
        }
    }

    @Aspect
    public static class TraceAspect {
        @Before(
            "execution(* demo..*.quote(..)) || " +
            "execution(* demo..*.finalStatus(..))"
        )
        public void beforeCall(JoinPoint joinPoint) {
            System.out.println("ADVICE " + joinPoint.getSignature().getName());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class Config {
        @Bean
        public PricingService pricingService() {
            return new PricingService();
        }

        @Bean
        public TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    public static void main(String[] args) {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            PricingService service = context.getBean(PricingService.class);

            System.out.println("AOP_PROXY=" + AopUtils.isAopProxy(service));
            System.out.println("CGLIB_PROXY=" + AopUtils.isCglibProxy(service));
            System.out.println("IS_PRICING_SERVICE="
                    + (service instanceof PricingService));

            System.out.println("=== OUTSIDE CALL ===");
            System.out.println("RESULT=" + service.quote("OUTER"));

            System.out.println("=== SELF INVOCATION ===");
            service.process();

            System.out.println("=== FINAL METHOD ===");
            service.finalStatus();
        }
    }
}
```

Chạy ở thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

**Các dòng stdout do chính chương trình in theo thứ tự** (Maven/Spring có thể phát sinh logging riêng):

```text
AOP_PROXY=true
CGLIB_PROXY=true
IS_PRICING_SERVICE=true
=== OUTSIDE CALL ===
ADVICE quote
TARGET quote OUTER
RESULT=PRICE:OUTER
=== SELF INVOCATION ===
TARGET process
TARGET quote INNER
=== FINAL METHOD ===
FINAL status
```

Giải thích các dấu vết:

- `@EnableAspectJAutoProxy(proxyTargetClass=true)` yêu cầu target-class proxy cho bean được advice.
- `@Bean TraceAspect` khiến aspect thực sự là Spring bean; `@Aspect` đơn lẻ không đủ để container tự đăng ký class.
- Proxy của `PricingService` là subclass, do đó đối tượng được trả qua `getBean(PricingService.class)` là instance của `PricingService`.
- Khi client gọi `quote("OUTER")` trên proxy, `@Before` chạy trước target method, rồi kết quả target trả về được in tại client.
- Khi target thực thi `process()` và gọi `this.quote("INNER")`, lời gọi trực tiếp trên target **bỏ qua proxy**, nên không có dòng `ADVICE quote` cho `INNER`.
- Method `finalStatus()` được khai báo `final`, không thể bị CGLIB subclass override; nó chạy trực tiếp và **không** xuất hiện `ADVICE finalStatus`. Method này cố tình **không dùng instance fields** để tránh một vấn đề quan trọng của final method trên proxy được nêu ở phần sau.

Cơ chế giới hạn CGLIB ở đây là một thuộc tính của **Spring AOP proxy-based interception**, không phải của cú pháp pointcut. Dù pointcut có khớp lời khai báo method, không có interceptor hook ở một method không thể override.

## 6. Những method nào CGLIB proxy có thể chặn?

| Thành phần trong target class | Có thể được Spring AOP CGLIB advice? | Lý do |
| --- | --- | --- |
| `public` method không `final` | Có, nếu lời gọi qua proxy và pointcut khớp | Proxy subclass có thể override |
| `protected` method không `final` | Có thể, tùy điểm gọi và pointcut | Proxy có khả năng override method visible |
| Package-private method | Có thể nếu method visible đối với subclass và phù hợp với framework | Visibility có thể giới hạn override |
| `final` instance method | **Không** | Subclass không thể override |
| `private` method | **Không** | Method private không được kế thừa/override |
| Method trong `final class` | **Không thể dựng CGLIB subclass** | Class `final` không thể bị kế thừa |
| `static` method | Không phải method execution được chặn theo kiểu instance proxy | Static call không dispatch qua instance override |
| Constructor | Không | Spring AOP không advice constructor execution bằng proxy |
| Method target tự gọi bằng `this` | **Không có lần intercept mới** | Lời gọi không đi vào proxy |
| Method gọi trên đối tượng tạo bằng `new` ở ngoài container | Không tự được Spring AOP advice | Không có Spring proxy tham gia |

Với method không public, phải kiểm tra **visibility thực tế, biểu thức pointcut, kiểu proxy và phiên bản Spring**. Spring có khả năng xử lý một số lời gọi protected/package-visible qua CGLIB, nhưng thiết kế service thông thường nên đặt điểm cắt bên ngoài ở các phương thức public rõ ràng.

### 6.1. Vì sao `final class` là vấn đề?

Ví dụ:

```java
public final class FinalReportService {
    public void generate() {
        System.out.println("generated");
    }
}
```

Java không cho class khác `extends FinalReportService`, nên Spring **không thể tạo CGLIB subclass** cho bean đó. Nếu bean bắt buộc phải được proxy bằng class-based proxy, việc tạo proxy có thể thất bại ngay trong quá trình khởi tạo context. Chỉ bỏ `final` khỏi class, thay cách thiết kế bean, hoặc dùng một chiến lược interface-based phù hợp mới giải quyết được nguyên nhân trực tiếp.

**Không suy rằng class có interface sẽ tự được JDK proxy nếu cấu hình đang ép CGLIB.** Cần xem chế độ proxy thực sự có hiệu lực.

### 6.2. Vì sao `final method` đặc biệt nguy hiểm với trạng thái instance?

Một CGLIB AOP proxy thường giữ một **target object riêng**. Với method override được, proxy chuyển execution qua interceptor về target. Với method `final`, subclass không override được, nên client gọi method đó **trên proxy instance trực tiếp**.

Điều này có thể tạo khác biệt về **instance fields** nếu mã final method truy cập trạng thái khởi tạo trong constructor của target: đừng mặc định final method trên proxy sẽ thấy cùng state như method đã delegate vào target. Mô hình dễ gặp:

```java
class StatefulService {
    private String token;

    StatefulService() {
        token = "READY";
    }

    public final String token() {
        return token;
    }
}
```

Đây là **mã phân tích rủi ro, không phải chương trình standalone**. Spring thường tạo proxy instance qua **Objenesis** để không phải chạy constructor của target lần thứ hai. Vì vậy, dữ liệu instance field trên proxy ở method `final` có thể không phải dữ liệu của target. Không dùng việc gọi final method trên CGLIB proxy làm hợp đồng tin cậy.

Cách an toàn hơn là để method cần Spring AOP chặn có thể override, tránh class `final` trong target cần proxy, hoặc định vị hành vi cần chặn ở interface/service khác với chiến lược proxy phù hợp.

### 6.3. `private` và `static` method

`private` method thuộc phạm vi trực tiếp của class, không được override; Spring AOP CGLIB không có cách thay nó bằng method override của proxy. `static` method liên kết với class thay vì dispatch qua instance; đặt annotation lên static method cũng không làm nó trở thành join point Spring AOP mà proxy chặn.

Muốn có advice tại ranh giới nghiệp vụ, đặt hành vi cần can thiệp trên method phù hợp của Spring-managed bean và gọi method đó qua proxy.

## 7. Proxy subclass vẫn có self-invocation

Đây là điểm dễ gây nhầm khi nghe rằng CGLIB “kế thừa target class”.

```text
Client ------------------> CGLIB proxy
                              |
                           intercept
                              |
                              v
                         target.outer()
                              |
                              | this.inner()
                              v
                         target.inner()
                     (không quay về proxy)
```

Khi target đã thực thi `outer()`, tham chiếu `this` trong target trỏ tới target; lời gọi `this.inner()` đi thẳng qua dispatch bình thường của object đó, không trở lại entry point của proxy.

Đặt `proxyTargetClass=true` **không tự giải quyết** self-invocation, kể cả `inner()` là public và có pointcut phù hợp. Điều này ảnh hưởng trực tiếp các cơ chế dùng Spring AOP như `@Transactional`, `@Cacheable` và các application advice.

### Cách thiết kế để lời gọi đi qua proxy

Thông thường nên tách method cần advice sang một Spring bean chuyên trách, rồi inject bean đó:

```java
@Service
public class PriceCalculator {
    @Transactional
    public void calculateAndSave() {
        // xử lý được áp transaction khi gọi qua proxy
    }
}

@Service
public class OrderProcessor {
    private final PriceCalculator calculator;

    public OrderProcessor(PriceCalculator calculator) {
        this.calculator = calculator;
    }

    public void processOrder() {
        calculator.calculateAndSave(); // gọi qua bean reference do Spring inject
    }
}
```

Đây là **mẫu thiết kế minh họa**, cần import, context có transaction management và database/transaction manager phù hợp để áp dụng thật. Mấu chốt là `OrderProcessor` sử dụng bean reference do Spring cấp, nên lời gọi có thể đi qua proxy `PriceCalculator`.

Kỹ thuật self-injection hoặc `AopContext.currentProxy()` với `exposeProxy=true` tồn tại nhưng làm code phụ thuộc chi tiết Spring AOP; thường chỉ nên dùng khi không thể refactor ranh giới bean.

## 8. Constructor: CGLIB proxy có gọi constructor hai lần không?

Spring Framework hiện đại thường dùng **Objenesis** để tạo proxy object mà không cần gọi constructor của target thêm lần nữa. Do đó, trường hợp bình thường **không có hai lần chạy constructor của target** chỉ vì có CGLIB AOP proxy.

Ví dụ quan sát sai thường thấy:

```java
public class DocumentService {
    public DocumentService() {
        System.out.println("CONSTRUCTOR");
    }

    public void generate() {
        System.out.println("GENERATE");
    }
}
```

Không nên mặc định đầu ra luôn có hai dòng `CONSTRUCTOR` khi Spring tạo CGLIB proxy. Trong một số môi trường JVM hạn chế constructor bypass, Spring có thể fallback và sinh thêm constructor call cùng debug log; đây là trường hợp ngoại lệ được tài liệu Spring nêu.

**Hệ quả thực tế:** constructor nên làm công việc khởi tạo thuần túy, tránh phụ thuộc vào sự tồn tại của proxy hoặc advice tại thời điểm constructor chạy. Spring AOP không dùng proxy để advice các method được gọi trong constructor trước khi bean proxy sẵn sàng.

## 9. Module system và khả năng kế thừa class

CGLIB cần sinh subclass và truy cập các method/class của target. Vì vậy, với Java Platform Module System (JPMS), Java access rules có thể ngăn proxy một số class hoặc package.

Ví dụ thường được Spring nêu: tạo class-based proxy cho class trong package `java.lang` khi triển khai theo module path có hạn chế mạnh; việc mở package bằng tham số như:

```text
--add-opens=java.base/java.lang=ALL-UNNAMED
```

chỉ có ý nghĩa trong những cấu hình JVM phù hợp, và không thể tùy tiện dùng để mở package cho một named module theo cách tương tự. Đừng thêm JVM flag đại trà khi chưa có lỗi cụ thể và chưa xác định target package, module boundary, kiểu deployment.

Đối với service class của ứng dụng, kiểm tra class có `final` hay không, các method có thể override hay không, phạm vi truy cập package/module và kiểu classloader nếu Spring báo lỗi tạo CGLIB subclass.

## 10. CGLIB proxy và kiểu của bean khi inject

Một lợi thế dễ quan sát của class-based proxy là bean có thể được inject theo **concrete class**:

```java
@Service
public class CustomerService {
    public String find(String id) {
        return "CUSTOMER:" + id;
    }
}

@RestController
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }
}
```

Đây là **mã minh họa cần Spring Boot Web imports và method handler phù hợp**. Nếu Spring AOP tạo CGLIB proxy cho `CustomerService`, proxy là subclass của `CustomerService`, nên về mặt kiểu có thể được cấp cho field/constructor parameter `CustomerService`.

Khi đổi sang JDK dynamic proxy, bean thường chỉ công bố interface, và nơi inject implementation class có thể thất bại. Để giảm phụ thuộc loại proxy, thiết kế interface nghiệp vụ rõ ràng khi phù hợp; nhưng nếu ứng dụng thực sự cần dùng class-based proxy, hãy bảo đảm class được kế thừa hợp lệ.

Lưu ý: **khả năng inject theo concrete class không phải bằng chứng bean chắc chắn đã được advice**. Bean thông thường cũng có kiểu concrete; xác minh bằng `AopUtils.isCglibProxy(bean)`.

## 11. Cách kiểm tra loại proxy thật khi chạy

Nếu đã có `ApplicationContext` và class service phù hợp:

```java
Object bean = context.getBean(PricingService.class);

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(bean instanceof PricingService);
```

Đây là **đoạn bổ sung** cho ví dụ runnable ở mục 5. Với cấu hình CGLIB và target bean được advice như ví dụ, các dòng là:

```text
true
true
false
true
```

Không dùng `bean.getClass() == PricingService.class` để kiểm tra target type: lớp proxy là subclass sinh động, nên so sánh class chính xác có thể cho `false`. Khi cần class đích, Spring cung cấp các tiện ích như `AopUtils.getTargetClass(bean)`. Tuy vậy, việc lấy target thật từ proxy hoặc can thiệp trực tiếp thường không cần trong code nghiệp vụ.

Trong ứng dụng có nhiều auto-proxy creator hoặc proxy đặc biệt, **kết quả runtime là bằng chứng chính**. Đừng suy loại proxy từ tên starter hoặc chỉ một property khi không biết config có hiệu lực.

## 12. Những nhận định sai thường gặp

| Nhận định | Thực tế trong Spring AOP |
| --- | --- |
| “CGLIB tạo một object giống target, không có quan hệ kiểu.” | Proxy là subclass của target class (khi tạo subclass hợp lệ). |
| “CGLIB có thể chặn mọi method trong class.” | Không: `final`, `private`, `static` và những method không đủ điều kiện đều có giới hạn. |
| “Đổi từ JDK sang CGLIB thì `this.inner()` được advice.” | Không: self-invocation vẫn bỏ qua proxy. |
| “Dùng CGLIB thì constructor target sẽ chạy hai lần.” | Spring thường dùng Objenesis để tránh constructor thứ hai; trường hợp fallback là ngoại lệ. |
| “CGLIB proxy không khác target instance.” | Cần phân biệt proxy object và target object; trạng thái method final có thể đặc biệt nguy hiểm. |
| “Class `final` vẫn ổn nếu pointcut chỉ match method public.” | Không thể subclass `final class` bằng CGLIB. |
| “Có dependency Spring AOP thì mọi service đều là CGLIB proxy.” | Chỉ những bean cần proxy và đủ điều kiện mới được auto-proxy. |
| “Kiểm tra tên lớp chứa `CGLIB` là phương pháp đáng tin.” | Dùng `AopUtils.isCglibProxy(bean)`; tên lớp runtime không phải contract. |
| “CGLIB bắt buộc thêm dependency riêng.” | CGLIB đã được Spring repackaged trong `spring-core` cho cơ chế proxy này. |
| “Mọi loại subclass do Spring tạo đều là Spring AOP CGLIB proxy.” | Subclass cho `@Configuration` và class-based AOP proxy phục vụ mục đích khác nhau. |

## 13. Checklist xử lý sự cố

1. **Bean có thực sự do Spring quản lý?** Đảm bảo caller dùng bean được inject/lấy từ context thay vì `new` đối tượng trực tiếp.
2. **Có advisor phù hợp không?** Nếu không có advisor, một bean thông thường không nhất thiết bị proxy.
3. **Cấu hình target-class proxy đã hiệu lực chưa?** Kiểm tra `@EnableAspectJAutoProxy(proxyTargetClass=true)`, `spring.aop.proxy-target-class` và cấu hình AOP/transaction khác.
4. **Class có `final` không?** Nếu có, Spring không tạo được CGLIB subclass cho class đó.
5. **Method có `final/private/static` không?** Nếu có, không kỳ vọng Spring AOP advice method bằng cơ chế override.
6. **Có self-invocation không?** Kiểm tra lời gọi `this.method()` bên trong target; đó không phải một lần gọi mới đi qua proxy.
7. **Java visibility và module permissions hợp lệ không?** Xem package access, inherited package-private method, module path và exception khi tạo subclass.
8. **Có truy cập instance state trong final method không?** Nếu có, kiểm tra rủi ro object proxy khác target state; ưu tiên thiết kế lại ranh giới.
9. **Loại proxy lúc chạy là gì?** Dùng `AopUtils.isAopProxy` / `isCglibProxy` / `isJdkDynamicProxy` và kiểm tra điểm gọi thực tế.
10. **Aspect đã đăng ký chưa?** Class `@Aspect` phải là Spring bean, và cơ chế @AspectJ auto-proxy cần được bật phù hợp.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Target `InventoryService` không implements interface, có một method public `reserve()` và một Spring AOP advisor khớp method đó. Proxy dự kiến là loại nào?  
**Đáp án:** CGLIB class-based proxy vì không có interface nghiệp vụ phù hợp cho JDK proxy.

**Câu 2.** Target implements interface, nhưng đặt `proxyTargetClass=true`. Proxy dự kiến là gì?  
**Đáp án:** CGLIB, miễn target class đủ điều kiện tạo subclass và không có cấu hình/ngoại lệ khác làm thay đổi kết quả.

**Câu 3.** Vì sao gọi `this.reserve()` bên trong `InventoryService` không chạy advice trên `reserve()`, dù Spring đang dùng CGLIB?  
**Đáp án:** Lời gọi nội bộ thực hiện trên target, không quay lại proxy. CGLIB không tự chèn advice vào bytecode target.

**Câu 4.** Tại sao `final void status()` không được advice bởi CGLIB?  
**Đáp án:** Subclass sinh runtime không thể override một method `final`.

**Câu 5.** Có thể dùng `AopUtils.isCglibProxy(bean)` để kiểm chứng bean được Spring AOP proxy bằng CGLIB không?  
**Đáp án:** Có. Nhưng còn phải kiểm tra method cụ thể và đường đi lời gọi để biết advice có chạy hay không.

**Câu 6.** Constructor in một dòng và bean được CGLIB proxy. Có luôn in hai dòng không?  
**Đáp án:** Không. Spring hiện đại thường dùng Objenesis, nên target constructor không bị gọi hai lần trong điều kiện thông thường.

**Câu 7.** Một method `final` đọc instance field gán từ constructor; có nên tin rằng nó luôn đọc cùng dữ liệu target trên CGLIB AOP proxy?  
**Đáp án:** Không. Final method không được delegate theo interceptor như method override được; proxy và target có thể có state instance khác nhau.

## 15. Tài liệu Spring Framework

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): lựa chọn JDK/CGLIB, final/private, Objenesis, module system và self-invocation.
- [Spring Framework — Introduction to Spring AOP](https://docs.spring.io/spring-framework/reference/core/aop/introduction-proxies.html): quan hệ proxy với target và giới hạn method execution.
- [Spring Framework — Using ProxyFactoryBean](https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html): tạo proxy cho class và nguyên lý subclass delegate.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): method matching khi dùng CGLIB trong Spring AOP.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-003">Quy tắc chọn cơ chế proxy trong Spring Framework</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc chọn cơ chế proxy trong Spring Framework

## 1. Câu hỏi cần trả lời trước khi cấu hình Spring AOP

Khi Spring AOP áp dụng một advice cho Spring bean, caller thường không nhận trực tiếp target object: container cung cấp một **proxy** để chuyển lời gọi qua interceptor chain. Spring có hai kiểu proxy chủ yếu:

- **JDK dynamic proxy:** proxy triển khai các interface cần thiết của target.
- **Class-based proxy (CGLIB):** proxy là subclass được sinh ra từ class đích.

Điều người học phải xác định là **Spring quyết định chọn kiểu nào, cấu hình nào có thể thay đổi quyết định đó, và hậu quả với những method được advice hoặc những kiểu có thể inject**.

Trong Spring Framework thuần, quy tắc mặc định thường gặp là:

> Nếu target có interface phù hợp để proxy, Spring ưu tiên **JDK dynamic proxy**. Nếu target không có interface để proxy, Spring dùng **class-based proxy (CGLIB)**. Khi `proxyTargetClass = true`, Spring **ép dùng class-based proxy**, kể cả nếu target có interface.

Đây là **quy tắc cho cơ chế Spring AOP proxy trong cấu hình thông thường**, không phải khẳng định mọi dạng proxy trong Spring (scoped proxy, custom `TargetSource`, proxy được lập trình bằng `ProxyFactory`) đều có lựa chọn giống hệt nhau.

**Spring Boot khác Spring Framework thuần ở mặc định cấu hình:** auto-configuration của Boot thông thường đặt **class-based proxy làm mặc định**. Nếu muốn JDK proxy ở Boot, dùng `spring.aop.proxy-target-class=false` và bảo đảm target có interface phù hợp.

Bài này tập trung vào lựa chọn proxy của **Spring AOP trên Spring-managed beans**, không triển khai kiến thức proxy Java thuần hoặc AspectJ weaving độc lập với Spring.

## 2. Vì sao Spring cần chọn giữa hai loại proxy?

Trong Spring AOP, một aspect hoặc Spring advisor mô tả hành vi cần áp dụng cho lời gọi phương thức. Auto-proxy creator kiểm tra bean đang được tạo, tìm các advisor phù hợp và tạo proxy nếu cần. Việc chọn JDK/CGLIB xảy ra khi **tạo proxy**, trước khi caller thực sự gọi method.

```text
ApplicationContext đăng ký bean definitions
                    |
                    v
    Tạo target bean + xác định advisors
                    |
                    v
     Target có cần Spring AOP proxy?
             /                \
           Không               Có
            |                   |
      Bean thông thường   Có ép class proxy?
                            /          \
                          Có           Không
                           |             |
                         CGLIB     Target có interface
                                        phù hợp?
                                       /        \
                                      Có        Không
                                       |          |
                                      JDK        CGLIB
                                       \          /
                                        v        v
                                   Caller nhận proxy
                                        |
                                        v
                                  method invocation
                                        |
                                        v
                                 advice -> target
```

Sơ đồ thể hiện **quy tắc chọn mặc định và việc ép class proxy** ở Spring Framework trong một môi trường chưa có cấu hình override khác. Spring còn xét interfaces được cấu hình tường minh, hạ tầng transaction/AOP đồng thời, chế độ `optimize`, kiểu proxy riêng và giới hạn class đích. Vì vậy kết quả thực tế phải được đo trên bean trong `ApplicationContext`.

Quan trọng: **có interface không có nghĩa bean chắc chắn bị proxy**. Spring phải có advisor/advice có thể áp dụng; nếu không có nhu cầu AOP, bean có thể được trả về nguyên vẹn.

## 3. Bảng quyết định thực tế

| Bối cảnh Spring AOP | Target implement business interface? | Thiết lập class proxy | Kết quả thông thường |
| --- | --- | --- | --- |
| Spring Framework thuần, mặc định | Có | `proxyTargetClass=false` | **JDK dynamic proxy**. |
| Spring Framework thuần, mặc định | Không | `proxyTargetClass=false` | **CGLIB**, vì JDK proxy không thể đại diện cho một class không có interface phù hợp. |
| Spring Framework thuần, ép class | Có hoặc không | `proxyTargetClass=true` | **CGLIB**, nếu class có thể subclass/proxy. |
| Spring Boot auto-configuration mặc định | Có hoặc không | Mặc định class-based | **CGLIB**, khi bean thực sự cần AOP proxy và có thể proxy. |
| Spring Boot đặt `spring.aop.proxy-target-class=false` | Có | Cho phép interface proxy | Thường **JDK dynamic proxy**, nếu không có cấu hình khác ép class proxy. |
| Spring Boot đặt `spring.aop.proxy-target-class=false` | Không | Cho phép mặc định Spring quyết định | **CGLIB**, vì thiếu interface phù hợp cho JDK proxy. |
| Spring proxy factory tùy chỉnh | Tùy target | Tùy danh sách `proxyInterfaces`, `proxyTargetClass`, `TargetSource` | Phải đọc cấu hình factory; không suy từ bảng mặc định. |

Đặt `proxyTargetClass=false` **không đồng nghĩa ép dùng JDK proxy bằng mọi giá**. Nó chỉ không bắt buộc class-based proxy; Spring vẫn phải chọn cách hợp lệ cho target.

Nếu không có advisor nào áp dụng, `AopUtils.isAopProxy(bean)` có thể là `false` bất kể class đó có interface hay Spring Boot đã cấu hình AOP.

## 4. Cách nhận diện JDK proxy và CGLIB proxy trong Spring

### 4.1. JDK dynamic proxy: giao diện là contract để caller đi qua proxy

Ví dụ interface:

```java
public interface PaymentOperations {
    String pay(String orderId);
}
```

Class triển khai:

```java
public class PaymentService implements PaymentOperations {
    @Override
    public String pay(String orderId) {
        return "paid:" + orderId;
    }

    public String implementationOnly() {
        return "internal";
    }
}
```

Hai đoạn trên là **fragments minh họa**, không có package/import; ví dụ chạy hoàn chỉnh nằm ở phần 8.

Khi Spring tạo **JDK dynamic proxy** cho `PaymentService` theo `PaymentOperations`:

- Caller có thể nhận proxy qua `PaymentOperations` và gọi `pay()`.
- Proxy **không phải instance của `PaymentService`**, do đó cast proxy sang concrete implementation thường thất bại.
- `implementationOnly()` **không có trong interface `PaymentOperations`**, nên không thể được gọi qua tham chiếu JDK proxy đó.
- Việc Spring AOP có thể advice một method còn tùy method thuộc contract interface, pointcut và đường gọi qua proxy; không suy rằng cứ có method trong implementation thì caller đã truy cập được trên JDK proxy.

Tại ranh giới dependency injection, Spring khuyến khích inject **kiểu interface** khi chọn interface proxy. Nếu một component lại yêu cầu `PaymentService` theo kiểu implementation, proxy JDK không đáp ứng trực tiếp yêu cầu type đó; ứng dụng có thể gặp lỗi inject/lookup tùy kiểu khai báo.

### 4.2. CGLIB: class proxy kế thừa class đích

Với class-based proxy, Spring tạo một lớp con ở runtime. Vì proxy là subclass của `PaymentService`, nên biểu thức `proxy instanceof PaymentService` có thể là `true`, và các method có thể override được có thể nhận advice qua proxy.

Điểm giới hạn:

- Class **`final` không thể bị subclass** để tạo CGLIB class proxy.
- Method **`final` không thể bị override**, vì vậy không thể được advice theo mô hình class proxy.
- Method **`private` không thể override**, vì vậy không phải điểm chặn khả dụng.
- Method không nhìn thấy với subclass (ví dụ package-private trên superclass khác package) không thể được advice bằng class proxy.
- Constructor không phải method execution được Spring AOP proxy bao quanh; self-invocation trong target vẫn bỏ qua proxy.

**Không cần khai báo dependency CGLIB riêng chỉ để bật Spring AOP class proxy**: Spring Framework đã tích hợp biến thể CGLIB trong `spring-core`. Khi dùng `@Aspect`, vẫn cần những dependency Spring AOP và hỗ trợ annotation/pointcut phù hợp, thường qua Spring Boot starter hoặc `spring-context` và `aspectjweaver`.

### 4.3. Trường hợp class final nhưng có interface

Một class `final` implement interface có thể hoạt động qua **JDK interface proxy** vì proxy không cần subclass class đó. Method triển khai là `final` cũng không nhất thiết cản trở interception qua JDK proxy: proxy chặn lời gọi ở **interface**, sau đó ủy quyền đến target.

Tuy nhiên, nếu cấu hình đã **ép class proxy bằng `proxyTargetClass=true`**, việc target là class `final` có thể khiến Spring không tạo được proxy và ứng dụng thất bại khi khởi tạo. Do đó, không sử dụng `proxyTargetClass=true` làm “cách sửa mọi lỗi AOP” mà không xem class hierarchy.

## 5. Thiết lập quyết định proxy trong Spring Framework thuần

### 5.1. Annotation `@EnableAspectJAutoProxy`

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class InterfaceProxyConfig {
    // @Bean definitions hoặc @ComponentScan
}
```

Khi không ép class proxy, các bean có interface phù hợp thường được proxy bằng JDK; các bean không có interface phù hợp phải dùng CGLIB nếu Spring cần proxy.

Ép class-based:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ClassProxyConfig {
    // @Bean definitions hoặc @ComponentScan
}
```

Đây là hai **config fragments thay thế nhau**, không nên kích hoạt đồng thời rồi đoán annotation nào “thắng”. Cấu hình Spring còn xét những nơi bật AOP/transaction khác trong context.

### 5.2. XML namespace `aop`

Với XML đã khai báo đúng namespace `aop`:

```xml
<aop:aspectj-autoproxy proxy-target-class="false"/>
```

hoặc:

```xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
```

Nếu dùng Spring AOP XML schema advisors:

```xml
<aop:config proxy-target-class="true">
    <!-- Các advisor/aspect XML của ứng dụng -->
</aop:config>
```

Các đoạn trên là **fragments XML**, không phải toàn bộ tài liệu XML có namespace/schema. Thuộc tính `proxy-target-class` có tác dụng tương tự việc yêu cầu dùng class-based proxy, nhưng phải hiểu rằng Spring có thể hợp nhất cấu hình proxy với những tính năng khác trong cùng context.

### 5.3. Khi cấu hình AOP được hợp nhất

Nếu trong cùng `ApplicationContext` có cả thiết lập interface proxy (`false`) và một yêu cầu class proxy (`true`) từ Java config, XML AOP hoặc hạ tầng transaction, Spring có thể hợp nhất chúng và áp dụng **thiết lập mạnh hơn (`true`)** cho auto-proxy creator. Vì vậy, bảng chọn proxy ở mục 3 chỉ đúng sau khi xác minh cấu hình đang có hiệu lực. Nên kiểm tra tất cả nguồn cấu hình liên quan và loại proxy runtime bằng `AopUtils`, nhất là khi dùng Spring Framework và Spring Boot cùng với transaction AOP.

## 6. Spring Boot: vì sao kết quả mặc định khác Spring Framework thuần?

Spring Boot cung cấp **auto-configuration** cho Spring AOP và mặc định thường dùng class-based proxy. Cấu hình ví dụ trong `application.properties`:

```properties
# Dùng interface-based proxy khi khả thi:
spring.aop.proxy-target-class=false
```

Nếu không có cấu hình khác ép class-based, Spring sẽ cân nhắc JDK proxy cho target có interface. Nếu target không implement interface thích hợp, Spring vẫn cần CGLIB khi phải tạo proxy.

Với cấu hình muốn dùng class-based:

```properties
spring.aop.proxy-target-class=true
```

Không nên áp dụng lời khuyên “Spring luôn chọn JDK trước” một cách máy móc cho ứng dụng Spring Boot: **mặc định do Boot cấu hình có thể khác mặc định Spring Framework core**.

Một số điểm cần xác minh:

1. Ứng dụng đã thêm starter AOP phù hợp với dòng Spring Boot được sử dụng (ví dụ Boot 3.x dùng `spring-boot-starter-aop`; Boot 4.x có `spring-boot-starter-aspectj`).
2. Bean aspect đã được Spring quản lý, qua `@Aspect` + `@Component` hoặc `@Bean`; annotation `@Aspect` một mình không đăng ký bean.
3. Có cấu hình Java/XML khác như `@EnableAspectJAutoProxy(proxyTargetClass=true)` hoặc transaction auto-proxy ghi đè ý định chọn JDK hay không.
4. Bean thực sự được proxy vì có advisor; nếu không có advisor phù hợp thì so sánh JDK/CGLIB trên bean đó có thể không có ý nghĩa.

**Giới hạn của property:** `spring.aop.proxy-target-class` điều chỉnh auto-configuration của Boot. Nó không bảo đảm đổi mọi custom proxy do thư viện khác tự tạo hoặc mọi scoped proxy đã khai báo.

## 7. Spring Framework 7: điều chỉnh proxy theo từng bean khi thích hợp

Spring Framework **7.0+** bổ sung khả năng chọn loại proxy cho bean cụ thể bằng **`@Proxyable`** với các lựa chọn như `INTERFACES` và `TARGET_CLASS`, theo tài liệu chính thức. Đây là khả năng của **dòng 7**, không phải annotation có sẵn ở Spring Framework 6.1/6.2.

Khả năng này hữu ích khi toàn ứng dụng có mặc định class proxy nhưng một bean cụ thể cần interface proxy, hoặc ngược lại. Cần đối chiếu đúng phiên bản Spring hiện tại và contract của bean trước khi dùng; đừng sao chép cấu hình Spring 7 vào một project đang dùng Spring 6.

Bài thực hành ở phần tiếp theo **cố định Spring Framework 6.1.14** nhằm minh họa quy tắc phổ biến của `proxyTargetClass`, không sử dụng `@Proxyable`.

## 8. Ví dụ Java 17+ chạy được: so sánh JDK và CGLIB trên cùng target

Ví dụ chạy **hai `AnnotationConfigApplicationContext` độc lập** với cùng các bean definitions và aspect:

- Context thứ nhất: `proxyTargetClass=false`; `PaymentService` có interface nên dùng JDK, `ReportService` không có interface nên dùng CGLIB.
- Context thứ hai: `proxyTargetClass=true`; cả hai service dùng CGLIB.
- `TraceAspect` áp dụng `@Before` lên hai method; các lời gọi service đều đi qua proxy.
- Đọc kết quả bằng `AopUtils` thay vì đoán từ tên class được sinh tự động.

### 8.1. Cấu trúc thư mục

```text
spring-proxy-selection/
├── pom.xml
└── src/main/java/com/example/proxy/
    ├── PaymentOperations.java
    ├── PaymentService.java
    ├── ReportService.java
    ├── TraceAspect.java
    └── DemoApp.java
```

### 8.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-proxy-selection</artifactId>
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

`spring-context` đã kéo các module Spring nền tảng trong graph tương ứng, bao gồm Spring AOP; `aspectjweaver` hỗ trợ Spring đọc `@Aspect` và pointcut expressions. Chọn phiên bản bằng BOM/dependency management phù hợp khi chuyển ví dụ vào ứng dụng thực tế; không trộn ngẫu nhiên những dòng Spring Framework.

### 8.3. `src/main/java/com/example/proxy/PaymentOperations.java`

```java
package com.example.proxy;

public interface PaymentOperations {
    String pay(String orderId);
}
```

### 8.4. `src/main/java/com/example/proxy/PaymentService.java`

```java
package com.example.proxy;

public class PaymentService implements PaymentOperations {

    @Override
    public String pay(String orderId) {
        return "paid:" + orderId;
    }

    public String implementationOnly() {
        return "implementation-only";
    }
}
```

### 8.5. `src/main/java/com/example/proxy/ReportService.java`

```java
package com.example.proxy;

public class ReportService {
    public String generate(String id) {
        return "report:" + id;
    }
}
```

`ReportService` cố ý không implement interface nghiệp vụ nào để thấy Spring phải dùng class-based proxy khi method của nó được advice.

### 8.6. `src/main/java/com/example/proxy/TraceAspect.java`

```java
package com.example.proxy;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {

    @Before(
        "execution(public * com.example.proxy.PaymentService.pay(..))"
        + " || execution(public * com.example.proxy.ReportService.generate(..))"
    )
    public void before(JoinPoint joinPoint) {
        System.out.println("[AOP] " + joinPoint.getSignature().getName());
    }
}
```

Biểu thức `execution(...)` được Spring AOP kiểm tra theo method/target class. Cả hai method đều public; advice không chạy cho `PaymentService.implementationOnly()` vì pointcut không chọn method đó.

### 8.7. `src/main/java/com/example/proxy/DemoApp.java`

```java
package com.example.proxy;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public class DemoApp {

    @Configuration
    static class Beans {
        @Bean
        PaymentOperations paymentOperations() {
            return new PaymentService();
        }

        @Bean
        ReportService reportService() {
            return new ReportService();
        }

        @Bean
        TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    @Configuration
    @Import(Beans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class DefaultProxyConfig {
    }

    @Configuration
    @Import(Beans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class ClassProxyConfig {
    }

    private static void run(String label, Class<?> configClass) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(configClass)) {

            PaymentOperations payment =
                    context.getBean(PaymentOperations.class);
            ReportService report = context.getBean(ReportService.class);

            System.out.println("[" + label + "] paymentJdk="
                    + AopUtils.isJdkDynamicProxy(payment));
            System.out.println("[" + label + "] paymentCglib="
                    + AopUtils.isCglibProxy(payment));
            System.out.println("[" + label + "] reportCglib="
                    + AopUtils.isCglibProxy(report));
            System.out.println("[" + label + "] paymentIsConcrete="
                    + (payment instanceof PaymentService));

            System.out.println("[" + label + "] "
                    + payment.pay("P-1"));
            System.out.println("[" + label + "] "
                    + report.generate("R-1"));
        }
    }

    public static void main(String[] args) {
        run("DEFAULT", DefaultProxyConfig.class);
        run("CLASS", ClassProxyConfig.class);
    }
}
```

Hai context độc lập bảo đảm thiết lập `proxyTargetClass=false` và `proxyTargetClass=true` **không hợp nhất vào cùng một context**, tránh làm sai phép so sánh. `Beans` đóng vai trò lớp cấu hình dùng chung nhưng được import riêng vào từng context.

### 8.8. Chạy project và đối chiếu stdout

Tại thư mục `spring-proxy-selection` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.proxy.DemoApp
```

Các dòng do chính chương trình in ra theo thứ tự, không tính thông báo Maven/SLF4J tùy môi trường:

```text
[DEFAULT] paymentJdk=true
[DEFAULT] paymentCglib=false
[DEFAULT] reportCglib=true
[DEFAULT] paymentIsConcrete=false
[AOP] pay
[DEFAULT] paid:P-1
[AOP] generate
[DEFAULT] report:R-1
[CLASS] paymentJdk=false
[CLASS] paymentCglib=true
[CLASS] reportCglib=true
[CLASS] paymentIsConcrete=true
[AOP] pay
[CLASS] paid:P-1
[AOP] generate
[CLASS] report:R-1
```

**Giải thích:**

1. `PaymentService` implement `PaymentOperations`; trong context DEFAULT, Spring có thể dùng JDK proxy để cung cấp `PaymentOperations`. Vì thế `paymentJdk=true` và `paymentIsConcrete=false`.
2. `ReportService` không có business interface, nhưng advice khớp `generate()`; Spring tạo CGLIB subclass proxy cho bean này ngay cả khi cấu hình DEFAULT không ép class proxy.
3. Trong context CLASS, `proxyTargetClass=true` ép class proxy cho mọi bean đích cần AOP, nên `PaymentService` được bọc bằng CGLIB và `payment instanceof PaymentService` đúng.
4. Cả hai context đều có một bean `TraceAspect`, và cả hai method cần áp dụng advice đều thực thi qua proxy. **Chọn JDK hay CGLIB thay đổi loại proxy và các method/kiểu có thể thấy qua proxy; nó không tự làm thay đổi nội dung nghiệp vụ `paid:P-1` hay `report:R-1`.**
5. Không có dòng log advice cho `implementationOnly()`: pointcut không chọn method đó, dù khi dùng CGLIB method này có thể được gọi qua reference kiểu `PaymentService` nếu application được thiết kế truy cập như vậy.

**Lưu ý thực nghiệm:** nếu đổi `PaymentOperations paymentOperations()` thành return type `PaymentService`, cách Spring suy đoán kiểu lúc tạo bean có thể khác, nhưng quy tắc proxy chủ yếu vẫn dựa trên target class, interfaces và `proxyTargetClass`. Tránh phụ thuộc vào một kiểu implement khi bạn chủ động chọn JDK proxy.

## 9. Chiến lược proxy và khả năng inject bean

Đây là tác động dễ nhận ra nhất khi đổi cơ chế proxy trong một ứng dụng Spring.

### 9.1. Inject theo interface khi dùng JDK proxy

Ví dụ Spring-managed client:

```java
public class CheckoutController {
    private final PaymentOperations payment;

    public CheckoutController(PaymentOperations payment) {
        this.payment = payment;
    }
}
```

Fragment này an toàn theo contract interface khi Spring bean được proxy bằng JDK; không cần client biết `PaymentService` là implementation thật.

Ngược lại:

```java
public class CheckoutController {
    private final PaymentService payment;

    public CheckoutController(PaymentService payment) {
        this.payment = payment;
    }
}
```

Nếu bean được thay bằng **JDK proxy** chỉ triển khai `PaymentOperations`, injection yêu cầu concrete `PaymentService` có thể không thỏa mãn. Điều chỉnh injection sang interface hoặc cân nhắc class-based proxy có chủ đích; không phải mọi ứng dụng đều cần `proxyTargetClass=true`.

### 9.2. Interface method và implementation-only method

Khi dùng JDK proxy, caller theo contract `PaymentOperations` chỉ truy cập được `pay()`, không có `implementationOnly()`. Trong Spring AOP, nếu chỉ method ở implementation được advice nhưng caller không có đường gọi method đó qua interface proxy, hành vi cần thiết sẽ không xuất hiện theo cách mong muốn.

Hướng thiết kế thường là:

- Đưa method nghiệp vụ cần được gọi từ bên ngoài vào interface thích hợp.
- Nếu ứng dụng nhất thiết phải expose concrete type hoặc method không thuộc interface, chọn class-based proxy sau khi kiểm tra giới hạn class/final method.
- Nếu target chỉ có method nội bộ, nhận thức rằng self-invocation vẫn không đi qua proxy kể cả khi chọn CGLIB.

### 9.3. CGLIB không “sửa” được self-invocation

```java
public class PaymentService {
    public void checkout() {
        audit(); // this.audit(), không đi lại qua proxy.
    }

    public void audit() {
        // Phương thức cần advice.
    }
}
```

Đây là fragment minh họa. Khi caller gọi `proxy.checkout()`, Spring có thể intercept `checkout()`; nhưng bên trong target, `this.audit()` không tự đi lại qua proxy để kích hoạt advice của `audit()`. **Ép CGLIB cũng không giải quyết được self-invocation**. Thông thường nên tách trách nhiệm cần advice sang bean khác và gọi qua dependency Spring quản lý.

## 10. Phân biệt proxy lựa chọn bởi AOP với các proxy khác của Spring

Ứng dụng Spring có thể chứa nhiều cơ chế proxy:

| Cơ chế | Mục đích | Có áp dụng chính xác quy tắc ở bài này? |
| --- | --- | --- |
| Spring AOP auto-proxy cho `@Aspect` | Intercept lời gọi Spring bean methods theo advisors | Có, theo cấu hình AOP/auto-proxy creator. |
| Transaction advisor/proxy | `@Transactional` qua proxy | Dùng hạ tầng proxy gần gũi; cấu hình chung có thể ảnh hưởng lựa chọn. |
| Scoped proxy | Inject bean ngắn vòng đời vào bean dài vòng đời | Có cài đặt lựa chọn interface/class riêng; không đoán từ `@EnableAspectJAutoProxy` đơn lẻ. |
| `ProxyFactory` hoặc `ProxyFactoryBean` tự cấu hình | Tạo proxy theo yêu cầu cho một target cụ thể | Dựa vào `proxyTargetClass`, `proxyInterfaces` và cấu hình factory cụ thể. |
| AspectJ compile-time/load-time weaving | Can thiệp bằng weaving | **Không phải** JDK/CGLIB Spring proxy theo cách bài này mô tả. |

Một AOP proxy có thể chứa nhiều advisor (audit, transaction, security...), và Spring thường hợp nhất cấu hình auto-proxy creator chuẩn. Vì vậy **thấy bean là CGLIB proxy không chứng minh aspect audit cụ thể đã áp dụng**, và **thấy hai cấu hình bật AOP không chứng minh có hai proxy riêng**.

## 11. Kiểm tra loại proxy trong ứng dụng thực tế

Với bean đã lấy từ context:

```java
Object bean = context.getBean("paymentOperations");

System.out.println(org.springframework.aop.support.AopUtils.isAopProxy(bean));
System.out.println(org.springframework.aop.support.AopUtils.isJdkDynamicProxy(bean));
System.out.println(org.springframework.aop.support.AopUtils.isCglibProxy(bean));
```

Đây là fragment trong một chương trình có biến `context`, không phải Java file độc lập. Ba API trả lời ba câu hỏi khác nhau:

- `isAopProxy`: có phải Spring AOP proxy theo kiểm tra Spring?
- `isJdkDynamicProxy`: có phải proxy dựa trên JDK dynamic proxy?
- `isCglibProxy`: có phải Spring CGLIB class proxy?

Có thể xem thêm advisors trong `Advised` và chạy thực tế method cần kiểm tra. Không dùng `bean.getClass().getName()` như bằng chứng duy nhất: tên lớp proxy được sinh ra là chi tiết runtime, có thể thay đổi theo version.

Một test nên xác minh **cả loại proxy và hợp đồng hành vi**:

1. Aspect và target thực sự nằm trong context và hạ tầng auto-proxy đã được bật.
2. Bean có advisor áp dụng và `AopUtils` trả đúng kiểu proxy dự kiến.
3. Lookup/injection theo type interface/concrete đáp ứng thiết kế.
4. Advice chạy đúng số lần với method khớp, không chạy cho method không khớp.
5. Giá trị trả về và ngoại lệ của nghiệp vụ không bị đổi ngoài ý muốn.
6. Các method `final`, `private`, self-invocation hoặc outside-context không được coi là điểm chặn Spring AOP bình thường.
7. Nếu có nhiều context hoặc cấu hình Java/XML/Boot, xác nhận proxy type ở **context thực sự sở hữu bean**, không chỉ ở test context đơn lẻ.

## 12. Những lỗi thường gặp khi chọn proxy

| Dấu hiệu | Khả năng xảy ra | Hướng kiểm tra/sửa |
| --- | --- | --- |
| `@Autowired PaymentService` thất bại nhưng inject `PaymentOperations` được | Bean được thay bằng JDK proxy không phải implementation class. | Dùng interface injection, hoặc chuyển sang class proxy khi có lý do. |
| Đặt `proxyTargetClass=false` nhưng bean vẫn dùng CGLIB | Target không có interface, hoặc hạ tầng khác đã yêu cầu class proxy. | Kiểm tra interface và tất cả proxy configs trong context. |
| Dùng Spring Boot mà bean có interface vẫn là CGLIB | Boot auto-configuration mặc định ưu tiên class proxy. | Kiểm tra `spring.aop.proxy-target-class` và cấu hình AOP khác. |
| Ép CGLIB nhưng class `final` gây lỗi | Không thể subclass class `final`. | Thiết kế interface proxy hoặc bỏ ràng buộc `final` khi hợp lý. |
| Method `final` không được advice bằng CGLIB | Proxy không thể override method đó. | Chuyển điểm chặn sang method có thể override hoặc interface proxy tương thích. |
| Có `@Aspect` nhưng không tạo JDK/CGLIB proxy | Aspect chưa là bean, AOP chưa bật, hoặc không có advisor match. | Kiểm tra registration, auto-proxy creator, pointcut. |
| Tự gọi method trong cùng class mà advice không chạy | Self-invocation bypass proxy. | Gọi qua Spring-managed bean khác; đổi JDK/CGLIB không tự sửa lỗi. |
| Phương thức tồn tại trên implementation nhưng không gọi được qua JDK proxy | Không nằm trong proxy interface. | Đưa contract vào interface hoặc chọn class proxy có chủ đích. |
| Có dòng `proxyTargetClass=false` nhưng CGLIB vẫn được ép | XML/transaction/AOP config khác yêu cầu `true`. | Kiểm kê context; cấu hình unified auto-proxy creator dùng thiết lập mạnh hơn. |
| Dùng Java module system gặp vấn đề khi proxy class đặc biệt | CGLIB bị hạn chế bởi module visibility/open packages. | Xem class/module cần proxy; tránh mặc định ép subclass cho class không mở được. |
| Thấy class name lạ chứa `$$SpringCGLIB$$` | Đây có thể là proxy class hợp lệ của Spring. | Dùng `AopUtils` và kiểm tra advisor/method; không coi riêng class name là lỗi. |

## 13. Quy trình quyết định cho một project Spring cụ thể

Khi thêm một aspect hoặc sửa một bug proxy:

1. **Xác định có cần AOP proxy không.** Có Spring-managed target bean và advisor/pointcut nào thực sự khớp?
2. **Kiểm tra môi trường chạy:** Spring Framework thuần, Spring Boot hay custom proxy factory; đọc đúng defaults của môi trường đó.
3. **Xem interface contract:** service có business interface và caller có đang inject theo interface không?
4. **Chọn khả năng proxy phù hợp:** mặc định của Framework thường JDK nếu có interface; dùng CGLIB nếu không có interface hoặc khi được ép và class cho phép.
5. **Rà soát toàn bộ proxy settings của context:** `@EnableAspectJAutoProxy`, XML `<aop:aspectj-autoproxy>`, `<aop:config>`, transaction support, Spring Boot property; không xem mỗi một annotation.
6. **Kiểm tra giới hạn loại proxy:** concrete injection, implementation-only methods, class/method `final`, `private`, Java modules và self-invocation.
7. **Xác nhận bằng `AopUtils` và một lần gọi thật:** proxy type, đúng advice, giá trị trả về, exception, số lần gọi.
8. **Giữ lựa chọn có lý do:** chỉ ép CGLIB khi cần concrete type/class methods và class cho phép subclass; interface proxy phù hợp khi API contract tập trung vào interface.

### Tài liệu Spring chính thức

- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — AOP Proxies: https://docs.spring.io/spring-framework/reference/core/aop/introduction-proxies.html
- Spring Framework — @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — ProxyFactoryBean and JDK/CGLIB selection: https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html
- Spring Boot — Aspect-Oriented Programming: https://docs.spring.io/spring-boot/reference/features/aop.html

**Kết quả cần đạt không phải “càng nhiều CGLIB càng tốt” hay “luôn JDK cho nhanh”, mà là chọn được proxy Spring AOP phù hợp với interface contract, cách inject bean, pointcut và cơ chế cấu hình đang hoạt động.** Một khi đã xác nhận loại proxy và lời gọi thật, bạn sẽ phân biệt được lỗi proxy selection với lỗi đăng ký aspect, pointcut hoặc self-invocation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-004">Ép proxy lớp bằng proxyTargetClass và tác động toàn cấu hình</a>

<details>
<summary>Xem chi tiết</summary>

# Ép proxy lớp bằng proxyTargetClass và tác động toàn cấu hình Spring AOP

## 1. `proxyTargetClass` giải quyết vấn đề gì?

Spring AOP can thiệp vào lời gọi method của **Spring bean qua proxy**. Đối với bean cần áp dụng advisor, Spring thường cân nhắc hai dạng proxy:

- **JDK dynamic proxy**: khi target triển khai interface phù hợp, proxy thực thi các interface; người gọi làm việc qua kiểu interface. Một proxy kiểu này không phải instance của implementation class.
- **CGLIB class-based proxy**: Spring sinh một lớp con của target class. Proxy có thể gán cho kiểu class đích và có thể intercept những method đủ điều kiện của class, kể cả method `public` không được khai báo trên interface.

Giá trị `proxyTargetClass = true` **yêu cầu Spring dùng proxy theo lớp đích** thay vì ưu tiên proxy interface. Trong Java config hỗ trợ Spring AOP kiểu `@AspectJ`:

~~~java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class AopConfiguration {
}
~~~

Đây là **đoạn cấu hình minh họa**, dùng trong một cấu hình Spring thực tế có các import `org.springframework.context.annotation.*`. Nó bật auto-proxying bằng class proxy cho các bean đủ điều kiện trong context. `proxyTargetClass = false` cho phép Spring lựa chọn proxy interface khi thích hợp; **không ép JDK proxy** cho một class không có interface.

Điều quan trọng: `proxyTargetClass` là **thiết lập cơ chế tạo proxy của Spring**. Nó không biến mọi bean thành proxy, không tạo thêm advisor, không thay thế pointcut, không tự đăng ký `@Aspect` thành bean và không kích hoạt AspectJ weaving.

## 2. Mặc định so với ép dùng CGLIB

Giả sử `CheckoutService implements CheckoutPort` và có method `checkout(...)` được Spring AOP advice:

| Trường hợp | Loại proxy dự kiến | Hệ quả đối với caller |
| --- | --- | --- |
| Spring Framework thuần; `@EnableAspectJAutoProxy` không ép class proxy; bean có interface | Thường là JDK dynamic proxy | Dùng `CheckoutPort`, không cast proxy thành `CheckoutService` |
| Bean cần AOP nhưng không có business interface | CGLIB class proxy, kể cả khi không bật `proxyTargetClass` | Proxy có thể gán cho implementation class nếu class có thể subclass |
| `@EnableAspectJAutoProxy(proxyTargetClass = true)` | CGLIB class proxy cho bean được auto-proxy creator chọn | Có thể inject/lấy theo implementation type nếu method/class cho phép |
| Một context chứa cả `proxyTargetClass = false` và `proxyTargetClass = true` trên các cấu hình AOP được hợp nhất | **Cấu hình mạnh hơn (`true`) thắng** | Không thể dùng một annotation `false` khác để buộc các bean trong cùng creator trở về JDK proxy |
| Bean không có bất cứ advisor/pointcut nào áp dụng | Thường giữ nguyên target, không có AOP proxy | `AopUtils.isAopProxy(bean)` là `false`, dù cấu hình ép class proxy đang bật |

Bảng trên nói về **Spring AOP trong Spring Framework**, không coi `proxyTargetClass = false` là mệnh lệnh “mọi nơi phải dùng JDK”. Kết quả cuối cùng còn tùy phạm vi context, auto-configuration và cách tạo từng proxy.

Trong **Spring Boot**, tự cấu hình AOP thường ưu tiên **CGLIB theo mặc định**. Thuộc tính:

~~~properties
spring.aop.proxy-target-class=false
~~~

cho phép chọn JDK proxy khi interface phù hợp trong cấu hình Boot được quản lý tương ứng. Khi ứng dụng có khai báo riêng yêu cầu `proxyTargetClass = true` hoặc hạ tầng proxy khác, hãy kiểm tra cấu hình thực tế; đừng đoán chỉ từ property. Không chuyển kết luận về mặc định của Spring Framework thuần sang Spring Boot mà không xét auto-configuration.

## 3. Vì sao một khai báo có thể tác động toàn cấu hình?

Đây là phần dễ bỏ sót nhất của bài học. Trong một **ApplicationContext**, các cấu hình Spring AOP và transaction dựa trên auto-proxy thường được **hợp nhất vào hạ tầng auto-proxy creator dùng chung**, với thiết lập proxy mạnh nhất được áp dụng. Theo tài liệu Spring Framework 6.2, cả các khai báo Java tương ứng như `@EnableAspectJAutoProxy` và `@EnableTransactionManagement` đều có thuộc tính `proxyTargetClass` và được xét theo cơ chế hợp nhất đó.

Ví dụ ý tưởng:

~~~java
@Configuration
@EnableAspectJAutoProxy
class LoggingAopConfig {
    // Aspect beans được đăng ký ở nơi phù hợp.
}

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class ForceClassProxyConfig {
}
~~~

Nếu hai cấu hình trên **được đăng ký trong cùng một context**, Spring không coi chúng như hai vùng độc lập, mỗi vùng chỉ áp dụng flag cho “bean thuộc config của mình”. Cờ `true` khiến auto-proxy creator hợp nhất ưu tiên **class proxy cho các bean cần được auto-proxy** trong phạm vi đó. Cấu hình thứ hai không cần tự khai báo lại toàn bộ aspect mới có ảnh hưởng.

Tương tự, trong Spring Framework 6.2:

~~~java
@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
class TransactionConfiguration {
}
~~~

Nếu dùng cùng context/hạ tầng hợp nhất với AOP, cấu hình transaction này có thể làm **loại proxy của các bean được Spring AOP advice** thay đổi, dù tác giả chỉ định ép class proxy vì mục tiêu transaction. Đoạn transaction trên chỉ minh họa cờ; một ứng dụng giao dịch chạy thật vẫn phải có transaction manager và hạ tầng tương ứng.

Với XML, nhóm cấu hình liên quan có cùng ý nghĩa:

~~~xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
<aop:config proxy-target-class="true"/>
<tx:annotation-driven proxy-target-class="true"/>
~~~

Các dòng XML là **những lựa chọn khai báo được trích riêng**; không phải file XML đầy đủ để chạy độc lập. Tài liệu Spring mô tả việc hợp nhất cấu hình mạnh nhất giữa `<aop:config/>`, `<aop:aspectj-autoproxy/>` và `<tx:annotation-driven/>`.

Cần hiểu chữ **“toàn cấu hình”** chính xác:

1. **Trong cùng context và cùng hạ tầng auto-proxy được hợp nhất**, lựa chọn class proxy có thể có tác dụng vượt ra ngoài vị trí khai báo ban đầu.
2. **Không có nghĩa mọi bean bị proxy**: chỉ bean có advisor phù hợp, đủ điều kiện và do container quản lý mới có khả năng được auto-proxy.
3. **Không có nghĩa toàn JVM**: `ApplicationContext` khác (đặc biệt parent/child context) có thể có đăng ký BPP/auto-proxy riêng. Đừng suy từ cấu hình của một context sang context khác.
4. **Không có nghĩa thay đổi proxy đã tạo tại runtime**: hiệu lực được áp dụng trong quá trình cấu hình và tạo bean/proxy. Việc đổi annotation sau khi bean đã khởi tạo không tự tạo lại proxy của singleton.

Trong Spring Framework **7.0 trở lên**, các tài liệu còn mô tả cơ chế mặc định hợp nhất rộng hơn và khả năng override theo bean với `@Proxyable`. Bài này chọn mô hình Spring Framework **6.2.x** để làm rõ thuộc tính `proxyTargetClass` và tác động hạ tầng AOP/transaction; không giả định các tính năng mới có trong 6.2.

## 4. Luồng xử lý khi Spring quyết định kiểu proxy

Với `@EnableAspectJAutoProxy`, Spring đăng ký auto-proxy creator, tiêu biểu `AnnotationAwareAspectJAutoProxyCreator`. Đây là một `BeanPostProcessor`/hạ tầng kế thừa từ `AbstractAutoProxyCreator`. Nó phát hiện advisor từ Spring bean `@Aspect`, tìm bean phù hợp và trả AOP proxy thay bean gốc khi cần.

~~~text
@Configuration / @EnableAspectJAutoProxy
          |
          v
Đăng ký / hợp nhất auto-proxy creator
          |
          +--> tổng hợp proxyTargetClass từ cấu hình liên quan
          |
          v
Tạo từng Spring bean đủ điều kiện
          |
          v
Tìm advisors/pointcut khớp method của bean
          |
          +--> không khớp ------> trả target thường
          |
          +--> khớp
                  |
                  v
          Kiểm tra proxyTargetClass, interface, proxy settings
                  |
                  +--> JDK dynamic proxy (khi phù hợp và không ép class)
                  |
                  +--> CGLIB subclass proxy (khi ép class hoặc cần proxy class)
                               |
                               v
                      Caller -> proxy -> advice -> target
~~~

**Đổi loại proxy không đổi mục tiêu của advice**: biểu thức pointcut vẫn dùng để xác định method được advice; proxy chỉ là cách chuyển hướng lời gọi. Có thể có advisor từ logging, transaction, cache hoặc security cùng tham gia một chain; loại proxy ảnh hưởng các method/kiểu đối tượng có thể được expose, chứ không trực tiếp làm từng advisor đúng hay sai.

Hai biểu thức nên phân biệt:

- `proxyTargetClass = true` — chọn class-based proxy.
- `exposeProxy = true` — một tùy chọn khác của `@EnableAspectJAutoProxy`, cho phép truy xuất proxy hiện hành qua `AopContext.currentProxy()` trong những điều kiện thích hợp. Không nên bật để giải quyết việc chọn JDK/CGLIB proxy; nó phục vụ vấn đề khác và gắn chặt mã vào Spring.

Ngoài ra, `@Configuration(proxyBeanMethods = false)` quyết định xử lý các factory method `@Bean` của configuration class. Cờ này **không tương đương** `@EnableAspectJAutoProxy(proxyTargetClass = false)`.

## 5. Project Java 17+ kiểm chứng tác động trên nhiều bean

Ta dùng hai service **đều triển khai interface**, cùng có advice `@Before`. Chạy hai `ApplicationContext` độc lập trong **cùng một chương trình**:

- Context `BASE` chỉ nạp cấu hình AOP thông thường: JDK proxy được dùng cho hai bean.
- Context `FORCED` nạp **cùng cấu hình đó cộng thêm** cấu hình `proxyTargetClass = true`: cả hai bean được CGLIB proxy.

Cách đối chứng này chứng minh tác động hạ tầng lên nhiều bean. Hai context độc lập để so sánh rõ, còn trong từng context cấu hình được hợp nhất theo các annotation đăng ký.

Cấu trúc:

~~~text
spring-proxy-target-class/
├── pom.xml
└── src/main/java/example/
    ├── App.java
    ├── CheckoutPort.java
    ├── CheckoutService.java
    ├── ReportPort.java
    ├── ReportService.java
    └── AuditAspect.java
~~~

Yêu cầu JDK 17 trở lên, Maven 3.9+ và có thể resolve Maven artifacts. Ví dụ cố định phiên bản **Spring Framework 6.2.0** và **AspectJ Weaver 1.9.22** cho khả năng tái lập, không khẳng định đây là bản mới nhất.

### 5.1. Maven build

Tạo `pom.xml`:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-proxy-target-class</artifactId>
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

`spring-context` cung cấp `ApplicationContext`/configuration, `spring-aop` chứa proxy utilities và AOP infrastructure, `aspectjweaver` hỗ trợ Spring phân tích cú pháp `@AspectJ`. Dependency AspectJ không tự bật compiler/weaver và không yêu cầu `-javaagent` cho ví dụ proxy AOP này.

### 5.2. Hai interface cùng hai implementation

`src/main/java/example/CheckoutPort.java`:

~~~java
package example;

public interface CheckoutPort {
    void checkout(String item);
}
~~~

`src/main/java/example/CheckoutService.java`:

~~~java
package example;

public class CheckoutService implements CheckoutPort {
    @Override
    public void checkout(String item) {
        System.out.println("CHECKOUT: " + item);
    }

    public void implementationOnly() {
        System.out.println("IMPLEMENTATION: checkout");
    }
}
~~~

`src/main/java/example/ReportPort.java`:

~~~java
package example;

public interface ReportPort {
    void generate();
}
~~~

`src/main/java/example/ReportService.java`:

~~~java
package example;

public class ReportService implements ReportPort {
    @Override
    public void generate() {
        System.out.println("REPORT: generated");
    }
}
~~~

Vì cả hai implementation đều có interface, Spring Framework auto-proxy trong cấu hình `BASE` có thể chọn JDK dynamic proxy. Method `CheckoutService.implementationOnly()` không có trên `CheckoutPort`: ví dụ có thể dùng nó để giải thích tính khác nhau giữa **kiểu interface được expose** và **lớp proxy theo target**. Trong mã chạy chính, ta chỉ gọi `checkout()` và `generate()` theo interface cho cả hai trường hợp để so sánh cùng một lời gọi.

### 5.3. Aspect dùng Spring AOP để chọn cả hai service

`src/main/java/example/AuditAspect.java`:

~~~java
package example;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* example.CheckoutService.checkout(..))"
          + " || execution(* example.ReportService.generate(..))")
    public void before(JoinPoint point) {
        System.out.println("ADVICE: " + point.getSignature().getName());
    }
}
~~~

Điều quan trọng là cả hai bean có **advisor áp dụng được**. Vì thế chúng là những bean cần proxy. `@Aspect` không tự tạo bean; cấu hình `App` bên dưới dùng `@Bean` để đăng ký `AuditAspect` vào container.

### 5.4. Hai cấu hình cùng context và mã kiểm chứng

`src/main/java/example/App.java`:

~~~java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class App {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class BaseConfig {
        @Bean
        public CheckoutService checkoutService() {
            return new CheckoutService();
        }

        @Bean
        public ReportService reportService() {
            return new ReportService();
        }

        @Bean
        public AuditAspect auditAspect() {
            return new AuditAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class ForceClassProxyConfig {
    }

    private static void inspect(
            String label,
            AnnotationConfigApplicationContext context,
            boolean forced) {
        Object checkout = context.getBean("checkoutService");
        Object report = context.getBean("reportService");

        boolean checkoutJdk = AopUtils.isJdkDynamicProxy(checkout);
        boolean reportJdk = AopUtils.isJdkDynamicProxy(report);
        boolean checkoutCglib = AopUtils.isCglibProxy(checkout);
        boolean reportCglib = AopUtils.isCglibProxy(report);
        boolean assignableToClass = checkout instanceof CheckoutService;

        System.out.println(label + "_CHECKOUT_JDK: " + checkoutJdk);
        System.out.println(label + "_REPORT_JDK: " + reportJdk);
        System.out.println(label + "_CHECKOUT_CGLIB: " + checkoutCglib);
        System.out.println(label + "_REPORT_CGLIB: " + reportCglib);
        System.out.println(label + "_IS_CHECKOUT_CLASS: " + assignableToClass);

        boolean correct = forced
                ? checkoutCglib && reportCglib
                        && !checkoutJdk && !reportJdk && assignableToClass
                : checkoutJdk && reportJdk
                        && !checkoutCglib && !reportCglib && !assignableToClass;

        if (!correct) {
            throw new IllegalStateException(
                    "Unexpected proxy type in context " + label);
        }

        ((CheckoutPort) checkout).checkout("book");
        ((ReportPort) report).generate();
    }

    public static void main(String[] args) {
        try (var base =
                new AnnotationConfigApplicationContext(BaseConfig.class)) {
            inspect("BASE", base, false);
        }

        try (var forced = new AnnotationConfigApplicationContext(
                BaseConfig.class, ForceClassProxyConfig.class)) {
            inspect("FORCED", forced, true);
        }

        System.out.println("CHECK: PASS");
    }
}
~~~

Phân tích hai lần khởi tạo context:

1. `new AnnotationConfigApplicationContext(BaseConfig.class)` chỉ thấy `@EnableAspectJAutoProxy(proxyTargetClass = false)`. Vì cả hai service đều triển khai interface và có advice áp dụng, Spring Framework 6.2 chọn **JDK dynamic proxy** cho chúng.
2. `new AnnotationConfigApplicationContext(BaseConfig.class, ForceClassProxyConfig.class)` nạp hai cấu hình cùng một context. Dù `BaseConfig` ghi `false`, `ForceClassProxyConfig` ghi `true`; creator được hợp nhất và dùng **class-based proxy** cho **cả hai service**, không chỉ cho bean có mặt trong `ForceClassProxyConfig` (thực ra config này không khai báo service bean nào).
3. Cả JDK proxy lẫn CGLIB proxy đều implement `CheckoutPort` và `ReportPort` ở đây, nên lời gọi qua interface dùng chung mà không phải thay business code.
4. `instanceof CheckoutService` là `false` với JDK proxy, `true` với CGLIB proxy; điều này cho thấy ảnh hưởng đến các injection point dùng implementation class.
5. `AopUtils.isJdkDynamicProxy(...)` và `AopUtils.isCglibProxy(...)` kiểm tra cơ chế thực tế, có giá trị hơn việc suy đoán từ annotation.
6. Chương trình ném `IllegalStateException` nếu loại proxy không đúng, chỉ in `CHECK: PASS` khi hai context thỏa các kỳ vọng. Điểm khác biệt của bài học nằm ở **proxy class selection**; advice đều chạy ở cả hai context.

### 5.5. Lệnh chạy và stdout

Chạy tại thư mục project:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chính `App`, `AuditAspect` và các service in ra, theo đúng thứ tự:

~~~text
BASE_CHECKOUT_JDK: true
BASE_REPORT_JDK: true
BASE_CHECKOUT_CGLIB: false
BASE_REPORT_CGLIB: false
BASE_IS_CHECKOUT_CLASS: false
ADVICE: checkout
CHECKOUT: book
ADVICE: generate
REPORT: generated
FORCED_CHECKOUT_JDK: false
FORCED_REPORT_JDK: false
FORCED_CHECKOUT_CGLIB: true
FORCED_REPORT_CGLIB: true
FORCED_IS_CHECKOUT_CLASS: true
ADVICE: checkout
CHECKOUT: book
ADVICE: generate
REPORT: generated
CHECK: PASS
~~~

Maven hoặc logging của framework có thể tạo thêm dòng bên ngoài; bản trên liệt kê **chỉ stdout do mã ví dụ chủ động in ra**.

Từ kết quả, có thể kết luận:

- Cùng hai service, cùng advice, cùng business calls nhưng **khác loại proxy** khi cấu hình trong context thay đổi.
- Chỉ thêm `ForceClassProxyConfig` làm **cả hai bean đủ điều kiện** chuyển sang CGLIB dù config ép không khai báo bean cụ thể.
- `CHECK: PASS` được in sau kiểm tra loại proxy, chứ không phải phép thử xem mọi bean Spring đều có proxy.
- Bài đã xác nhận cơ chế Spring AOP. **Không có AspectJ compile-time hay load-time weaving** trong project này.

## 6. Những hệ quả thực tế của việc ép proxy lớp

### 6.1. Kiểu injection và cast

Nếu service triển khai `CheckoutPort`:

~~~java
@Autowired
private CheckoutPort checkout;
~~~

Đây là kiểu injection hoạt động với JDK proxy và CGLIB proxy (khi bean duy nhất/được chọn đúng). Nếu viết:

~~~java
@Autowired
private CheckoutService checkout;
~~~

Khi bean thực tế là JDK dynamic proxy, đối tượng proxy **không phải** instance của `CheckoutService`; việc inject theo class có thể thất bại vì không phù hợp kiểu. CGLIB proxy là lớp con của `CheckoutService` nên trong điều kiện class proxy tạo được, kiểu này có thể được inject. Hai snippet trên là **minh họa field injection trong class Spring-managed**, không phải tệp Java độc lập; trong ứng dụng thật, constructor injection thường dễ kiểm soát dependency hơn.

**Đừng ép toàn bộ hạ tầng CGLIB chỉ để tránh thiết kế interface** nếu phần lớn dependency dùng interface một cách rõ ràng và ổn định. Khi có nhu cầu thực sự gọi method chỉ nằm trên implementation, hãy cân nhắc trước khi chọn class proxy cho toàn context.

### 6.2. Method không thuộc interface

JDK proxy không expose method `implementationOnly()` của `CheckoutService` vì `CheckoutPort` không khai báo nó. Với class proxy, method đó có thể truy cập qua `CheckoutService` (nếu không có giới hạn method/class).

Tuy nhiên, **truy cập được method không đồng nghĩa method sẽ được advice**. Pointcut trong ví dụ chỉ khớp `checkout(...)` và `generate(...)`. Muốn `implementationOnly()` cũng chạy advice thì phải mở rộng pointcut hoặc tạo advisor phù hợp; `proxyTargetClass = true` tự nó không thêm advice.

### 6.3. Giới hạn class proxy

| Đặc điểm target | Điều cần nhớ khi dùng CGLIB |
| --- | --- |
| `final class` | Không thể tạo subclass proxy cho chính class đó |
| `final method` | Không thể override/intercept method đó qua CGLIB proxy |
| `private method` | Không thể override; Spring AOP proxy không advice nó theo cách thông thường |
| Method không nhìn thấy từ lớp con | Có thể không intercept được; ví dụ một method package-private ở parent class thuộc package khác |
| Object được khởi tạo bằng `new` ngoài Spring | Không tự nhận proxy chỉ vì cấu hình `proxyTargetClass` |
| Self-invocation `this.otherMethod()` trong target | Vẫn bỏ qua lời gọi qua proxy, kể cả CGLIB |
| JVM module path / hạn chế module accessibility | Một số class không cho phép subclass proxy nếu package không mở phù hợp |
| Constructor của class proxy | Spring thường dùng Objenesis để tránh gọi constructor đích hai lần; tùy giới hạn JVM có thể có ngoại lệ/diagnostic |

Những giới hạn này bắt nguồn từ kỹ thuật **lớp con** và cách Spring AOP gọi target. Dùng CGLIB không mang lại khả năng chặn mọi field access/constructor như AspectJ weaving. Đừng chuyển flag sang `true` chỉ vì muốn khắc phục self-invocation: giải pháp ở cấp thiết kế là gọi qua bean proxy (thường tách service) hoặc chọn cơ chế phù hợp khác nếu yêu cầu vượt phạm vi Spring AOP.

### 6.4. Ảnh hưởng với transaction và các advisor khác

Một project Spring có thể dùng `@Transactional`, cache, security và `@Aspect` logging cùng lúc. Các cơ chế này có thể sử dụng Spring AOP proxy/advisor. Trong Spring Framework 6.2, cấu hình Spring AOP và transaction với cờ `proxyTargetClass` có thể hợp nhất về **global auto-proxy creator settings** trong cùng context.

Điều đó **không** có nghĩa mọi annotation tự động tham gia advice hay mọi cơ chế @Async có cùng quy tắc ở mọi phiên bản. Đừng mở rộng kết luận vượt hỗ trợ framework/version đang dùng; khi nâng Spring 7.x, đọc tài liệu về cách hợp nhất proxy processors có thay đổi.

Trước khi bật ép class proxy cho application có transaction, hãy kiểm tra tất cả target cần proxy: class/method `final`, kiểu injection và hành vi khi nhiều advisor cùng áp dụng. Một thay đổi cấu hình nhỏ có thể đổi kiểu runtime của nhiều bean.

## 7. Cấu hình theo các kiểu Spring phổ biến

### Java configuration thuần Spring

~~~java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfig {
}
~~~

Có thể dùng một configuration Java được Spring load. Aspect cần là bean, chẳng hạn có cả `@Aspect` và `@Component` dưới component scanning, hoặc được tạo bằng `@Bean` như ví dụ chính.

### Spring Boot auto-configuration

~~~properties
spring.aop.proxy-target-class=true
~~~

Spring Boot thường đã cấu hình AOP nếu dependency cần thiết có trên classpath; trong nhiều phiên bản mặc định của Boot, CGLIB cũng đã bật. Property `false` là cách yêu cầu proxy interface trong thiết lập tương ứng của Boot, nhưng không nên coi nó có quyền ghi đè bất kỳ cấu hình AOP riêng biệt nào vốn ép `true`. Kiểm tra `AopUtils` và cấu hình thực tế.

### XML AOP

~~~xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
~~~

Đây là phần tử thuộc namespace AOP cần khai báo trong XML cấu hình Spring, không phải tệp XML độc lập. Nếu cùng context có `<tx:annotation-driven/>`, hãy nhớ quy tắc kết hợp mạnh nhất thay vì giả định namespace nào chỉ ảnh hưởng namespace đó.

### Kiểm tra trực tiếp tại runtime

Trong method Java có biến `context` thuộc Spring `ApplicationContext`:

~~~java
Object bean = context.getBean("checkoutService");

System.out.println(org.springframework.aop.support.AopUtils.isAopProxy(bean));
System.out.println(org.springframework.aop.support.AopUtils.isJdkDynamicProxy(bean));
System.out.println(org.springframework.aop.support.AopUtils.isCglibProxy(bean));
~~~

Đoạn trên **cắm vào ứng dụng đã tồn tại**, không phải chương trình biên dịch riêng. Nếu đối tượng thực sự có AOP proxy, hàm đầu tiên phải `true`; hai hàm sau giúp phân biệt loại. Không suy luận loại proxy từ việc class có interface hay không khi cấu hình application đã phức tạp.

## 8. Các nhầm lẫn phổ biến

**“`proxyTargetClass = true` chỉ ảnh hưởng bean khai báo trong configuration chứa annotation.”** Sai với cơ chế auto-proxy dùng chung trong cùng context: các bean khác đủ điều kiện advisor vẫn có thể chịu cấu hình mạnh nhất, như `ReportService` trong ví dụ.

**“`proxyTargetClass = false` sẽ ép mọi service thành JDK proxy.”** Sai. Nếu class không có interface để proxy, Spring vẫn có thể dùng CGLIB. Nếu trong context có cấu hình liên quan ép `true`, cờ `false` ở nơi khác không xóa được thiết lập mạnh hơn.

**“Bật `proxyTargetClass` là tất cả bean sẽ có proxy.”** Sai. Spring AOP vẫn cần advisor/pointcut khớp và bean đủ điều kiện xử lý. Cấu hình `true` chỉ chọn kiểu **khi phải tạo proxy**.

**“CGLIB xử lý được `final`, `private`, constructor hoặc `this.save()`.”** Sai. CGLIB là subclass proxy nên không override được method `final`/`private`; lời gọi nội bộ trên `this` không đi qua proxy của Spring AOP.

**“CGLIB là AspectJ weaving.”** Sai. CGLIB là kỹ thuật sinh subclass proxy ở runtime; AspectJ weaving là cơ chế biến đổi bytecode theo compiler/weaver riêng. Cùng syntax `@AspectJ` không đồng nhất hai cơ chế.

**“Thay property của Boot sang `false` chắc chắn đã tắt mọi class proxy.”** Sai khi ứng dụng còn cấu hình Spring AOP/transaction yêu cầu class proxy hoặc khi bean không có interface. Hãy xác nhận lại bằng quan sát proxy thực tế.

**“Đã dùng CGLIB thì không cần interface nữa.”** Không bắt buộc loại interface khỏi thiết kế. Interface vẫn hữu ích cho API, kiểm thử, định nghĩa contract. Lựa chọn JDK hay CGLIB là quyết định proxy kỹ thuật, nên dựa trên nhu cầu thực.

## 9. Quy trình chẩn đoán khi thay đổi `proxyTargetClass`

1. **Xác nhận đúng context và profile:** config nào thực sự được nạp, Spring Framework/Spring Boot version nào, có parent/child contexts không.
2. **Liệt kê nguồn thiết lập ép class proxy:** `@EnableAspectJAutoProxy`, `@EnableTransactionManagement`, XML AOP/transaction, Boot `spring.aop.proxy-target-class` và các hạ tầng proxy liên quan.
3. **Xác định bean cần proxy:** aspect/advisor nào áp dụng, pointcut có khớp method qua Spring bean không. Không có advisor thì việc đổi flag có thể không hiện ra.
4. **Đo loại proxy tại runtime:** `AopUtils.isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy` trên reference nhận từ context.
5. **Kiểm tra tính tương thích của target:** `final`, `private`, visibility, interface và module accessibility trước khi ép CGLIB.
6. **Đối chiếu kiểu injection và code cast:** một bean từng là JDK proxy nay là subclass proxy có thể thay đổi giả định trong các điểm inject hoặc code reflection; tập trung vào contract cần expose.
7. **Kiểm tra hành vi advice thật:** gọi method qua proxy rồi quan sát log/counter hoặc integration test; proxy đúng loại nhưng pointcut không khớp vẫn không chạy advice.
8. **Sau khi đổi cấu hình, khởi tạo lại context để kiểm chứng:** không dựa vào proxy singleton đã được tạo từ cấu hình cũ.
9. **Đánh giá tác động đến bean khác:** nhất là khi application có AOP và transaction dùng chung hạ tầng; test thêm bean không nằm trong configuration đã chỉnh nhưng có advisor.

## 10. Câu hỏi tự kiểm tra

1. Khi `CheckoutService implements CheckoutPort`, vì sao `proxyTargetClass = false` thường cho JDK proxy nhưng `true` cho CGLIB?  
   Vì `false` cho phép cơ chế chọn interface proxy khi phù hợp, còn `true` yêu cầu subclass proxy theo target class.

2. Bật `proxyTargetClass = true` có khiến một bean không khớp advisor thành proxy không?  
   Không. Flag điều khiển **loại** proxy, không sinh ra yêu cầu advice mới.

3. Hai configuration cùng một `ApplicationContext`, một cấu hình ghi `false` và một ghi `true` thì kết quả nào?  
   Với auto-proxy creator hợp nhất, **thiết lập mạnh nhất (`true`)** được áp dụng cho các bean cần proxy trong context.

4. Vì sao mã inject implementation class có thể hỏng khi đổi từ CGLIB về JDK proxy?  
   JDK proxy chỉ thực thi interface nên không phải instance của implementation class.

5. Có thể chữa self-invocation bằng `proxyTargetClass = true` không?  
   Không. `this.method()` trên target vẫn không đi qua Spring proxy.

6. Một ứng dụng dùng Spring Boot mặc định class proxy nhưng project Maven Spring thuần dùng JDK proxy; có mâu thuẫn không?  
   Không. Spring Boot có auto-configuration và default riêng; cần xét thiết lập hiệu lực của từng application.

## Tài liệu chính thức

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): lựa chọn JDK/CGLIB, giới hạn class proxy và cơ chế cấu hình mạnh nhất.
- [Spring Framework 6.2 — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): Spring dùng syntax AspectJ trong AOP runtime dựa trên proxy.
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): bật auto-proxy creator.
- [Spring Framework 6.2 — AOP Proxies](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-proxies.html): lý do JDK proxy và class proxy có phạm vi method khác nhau.
- [Spring Boot — AOP auto-configuration](https://docs.spring.io/spring-boot/reference/features/aop.html): mặc định Boot và thuộc tính `spring.aop.proxy-target-class`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-005">Giới hạn lớp final và phương thức final khi dùng CGLIB</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn lớp final và phương thức final khi dùng CGLIB trong Spring AOP

## 1. Vấn đề cần nhận diện

Trong Spring AOP, một target bean có thể được Spring tạo **JDK dynamic proxy** qua interface, hoặc **class-based proxy** bằng CGLIB. Khi cấu hình Spring dùng class-based proxy, Spring phải tạo một **subclass** của class đích để intercept các phương thức có thể override.

Hai giới hạn quan trọng xuất phát trực tiếp từ cách Spring sử dụng CGLIB:

- **`final class`:** không thể tạo subclass, nên **không thể tạo CGLIB AOP proxy** cho class ấy. Khi bean thực sự cần áp advisor và Spring buộc class proxy, quá trình tạo proxy/khởi tạo context có thể thất bại.
- **`final method` trong class không final:** Spring vẫn có thể tạo CGLIB proxy cho class, **nhưng không thể intercept/advice chính method final** vì subclass không override được. Các method khác không final vẫn có thể được advice.

Bài tập trung vào **hành vi Spring AOP**, điều kiện phát sinh lỗi, cách cấu hình và kiểm chứng proxy. Những khái niệm Java `final` cơ bản chỉ được nhắc ở mức cần thiết để hiểu ràng buộc của proxy Spring.

Điểm cần nhớ ngay: **“Bean được proxy” khác “mọi method trên bean đều được advise”.** Nếu chỉ kiểm tra `AopUtils.isAopProxy(bean) == true`, bạn chưa chứng minh `finalMethod()` có advisor chạy.

## 2. Spring tạo JDK proxy và CGLIB proxy trong trường hợp nào?

| Điều kiện trong Spring | Kiểu proxy có thể chọn | Hệ quả với `final` |
| --- | --- | --- |
| Target implement một business interface, `proxyTargetClass=false`, cấu hình không ép class proxy | Thường **JDK dynamic proxy**, object proxy implement interface | Target class có thể là `final`; Java class target không cần bị subclass. Method public `final` của target có thể được advice **qua phương thức interface được proxy**, nếu advisor match. |
| Target không có interface phù hợp | **CGLIB/class-based proxy** được sử dụng khi cần proxy | Class không thể `final`; `final` method không thể được advice qua subclass. |
| `@EnableAspectJAutoProxy(proxyTargetClass=true)` hoặc cấu hình ép class proxy | **CGLIB** dù target có interface | `final class` không được subclass; `final method` bỏ qua interception. |
| Spring Boot dùng class proxy theo cấu hình mặc định | Thường class-based, tùy `spring.aop.proxy-target-class` và các cấu hình liên quan | Một target `final` có thể gặp giới hạn CGLIB dù đã khai báo interface. |
| Đổi sang JDK proxy mà caller chỉ biết concrete implementation | Caller không thể inject/cast JDK proxy thành implementation class | Cần inject bằng interface; JDK proxy chỉ expose các phương thức thuộc interface được proxy. |

Trong **Spring Framework** cấu hình `@EnableAspectJAutoProxy`, tham số `proxyTargetClass` mặc định là `false`; với target có business interface, Spring thường ưu tiên JDK proxy. Với **Spring Boot** có AOP auto-configuration, thuộc tính `spring.aop.proxy-target-class` thường mặc định `true`, nên cần kiểm tra môi trường cụ thể.

### Sơ đồ đường đi lời gọi

```text
CGLIB cho class không final:

Client
  |
  v
ClassService$$SpringCGLIB$$...
  ├─ open()         -> method override được -> advice -> target.open()
  └─ final locked() -> không override được -> không đi qua advice

CGLIB cho final class:

final class FinalService
  |
  └─ Spring không thể tạo subclass => class-based proxy không khả thi

JDK interface proxy cho final target class:

Client (giữ interface ServicePort)
  |
  v
jdk.proxy...$Proxy
  |
  +-- ServicePort.call() -> advice -> final target implementation.call()
```

Sơ đồ CGLIB là mô hình khái niệm của interception; cơ chế proxy chi tiết do Spring quản lý. Đừng dựa vào tên class proxy sinh tự động, vì tên có thể thay đổi theo phiên bản runtime.

## 3. Vì sao `final class` và `final method` gây hai hiện tượng khác nhau?

### 3.1. Toàn bộ class `final`: thường lỗi khi bắt buộc tạo CGLIB proxy

Giả sử trong Spring:

```java
public final class FinalInvoiceService {
    public String issue(String id) {
        return "issued:" + id;
    }
}
```

Một advisor muốn chặn `issue(..)`, nhưng `@EnableAspectJAutoProxy(proxyTargetClass=true)` ép Spring tạo class proxy. Vì class là `final`, CGLIB không thể tạo subclass. Khi Spring **thực sự phải áp AOP proxy** cho bean này, ứng dụng có thể báo lỗi dạng `AopConfigException` / `BeanCreationException`, nguyên nhân sâu thường nói không thể subclass `final class`.

**Không khẳng định Spring luôn lỗi chỉ vì có `final` bean.** Nếu bean `final` không được bất kỳ advisor nào nhắm đến và Spring không cần tạo proxy, bean vẫn có thể hoạt động bình thường. Lỗi xuất hiện ở nhu cầu class-proxy thực tế, không phải khi compiler đọc từ khóa `final`.

### 3.2. Class không final, một method `final`: proxy tồn tại nhưng advice bỏ qua method đó

```java
public class InvoiceService {
    public String issue(String id) {
        return "issued:" + id;
    }

    public final String summary(String id) {
        return "summary:" + id;
    }
}
```

Một CGLIB proxy có thể override `issue(..)` để chạy advisor, nhưng **không thể override** `summary(..)`. Bởi vậy, pointcut có thể phù hợp **về tên/chữ ký** mà lời gọi `summary()` vẫn **không kích hoạt Spring AOP advice**.

Đây là lỗi nguy hiểm hơn vì **ứng dụng có thể vẫn khởi động**, nhiều phương thức được advice như mong đợi, riêng `final` method không có side effect của advice.

Cần đặc biệt cẩn trọng khi dùng `@Transactional`, `@Cacheable`, method security hoặc `@Async` theo cơ chế proxy: một final method dưới class proxy có thể không đi qua interceptor mà người viết code tưởng là đã kích hoạt. Không dùng trạng thái “bean là AOP proxy” để suy rằng transaction/security/cache trên final method đã an toàn.

### 3.3. Không chỉ có `final`

Cùng nguyên lý subclass:

- `private` method không thể override nên không thể advice bằng CGLIB.
- Method không nhìn thấy từ subclass, ví dụ package-private ở parent class khác package, cũng có thể không được intercept.
- `this.otherMethod()` bên trong target là **self-invocation**: không quay qua proxy; bỏ qua advice ngay cả khi `otherMethod()` không final.
- Constructor invocation hoặc field read/write không trở thành method join point của Spring AOP proxy.
- Java Platform Module System có thể giới hạn class-proxy đối với package không được mở; đó là ràng buộc riêng, không thể giải quyết bằng việc xóa `final` đơn thuần.

Các vấn đề trên khác nhau về nguyên nhân. Đừng tự động bật `proxyTargetClass=true` hoặc `exposeProxy=true` khi gặp method không có advice.

## 4. Chạy demo CGLIB: advice chạy ở method thường, không chạy ở method final

Đây là một dự án **Spring Framework thuần** với **Java 17+**, **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**. Ví dụ dùng Java configuration với `@EnableAspectJAutoProxy` nên không phụ thuộc Spring Boot auto-configuration.

### 4.1. Cấu trúc project

```text
spring-final-proxy-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/com/example/finalproxy/
    ├── App.java
    ├── ClassProxyConfig.java
    ├── CglibService.java
    └── TraceAspect.java
```

**`settings.gradle`**

```gradle
rootProject.name = 'spring-final-proxy-demo'
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
    mainClass = 'com.example.finalproxy.App'
}
```

Trong Spring Framework 6.x, CGLIB đã được Spring repackaged trong `spring-core`; không phải tự thêm một dependency CGLIB riêng. `aspectjweaver` là dependency dùng bởi Spring AOP cho `@AspectJ` pointcut parsing, **không có nghĩa ứng dụng dùng AspectJ load-time weaving**.

### 4.2. Target class không final, có một method final

**`CglibService.java`**

```java
package com.example.finalproxy;

public class CglibService {
    public String open(String id) {
        return "open:" + id;
    }

    public final String locked(String id) {
        return "locked:" + id;
    }
}
```

`open(..)` có thể được subclass override. `locked(..)` không thể override. Để quan sát giới hạn một cách rõ ràng, cả hai method trả về chuỗi đơn giản, không phụ thuộc instance fields.

**Một lưu ý nâng cao:** một final method được gọi trực tiếp trên CGLIB proxy có thể truy cập trạng thái trên **proxy object** thay vì đường dispatch được Spring dùng để gọi target. Do đó, khi class có state, việc gọi final method trên proxy có thể gây hành vi gây ngạc nhiên, thậm chí thấy giá trị field chưa được khởi tạo như mong đợi. Không chỉ là “thiếu log advice”; tránh triển khai bean stateful rồi kỳ vọng final method trên CGLIB proxy hoạt động giống mọi public method khác.

### 4.3. Đăng ký Spring aspect và pointcut

**`TraceAspect.java`**

```java
package com.example.finalproxy;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(public * com.example.finalproxy.CglibService.*(..))")
    public void beforeService(JoinPoint joinPoint) {
        System.out.println("advice:" + joinPoint.getSignature().getName());
    }
}
```

Biểu thức `execution(...)` bao trùm **chữ ký của cả `open(..)` và `locked(..)`**, nhưng advice chỉ thực sự chạy khi invocation được Spring proxy intercept. Đây chính là khác biệt mà ví dụ muốn minh họa.

### 4.4. Bật class-based proxy

**`ClassProxyConfig.java`**

```java
package com.example.finalproxy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ClassProxyConfig {
    @Bean
    TraceAspect traceAspect() {
        return new TraceAspect();
    }

    @Bean
    CglibService cglibService() {
        return new CglibService();
    }
}
```

`TraceAspect` là Spring bean nhờ `@Bean`, không cần `@Component`. `@EnableAspectJAutoProxy(proxyTargetClass=true)` yêu cầu class proxy cho bean phù hợp. `CglibService` không `final`, nên Spring có thể tạo subclass proxy.

### 4.5. Main, lệnh chạy và stdout

**`App.java`**

```java
package com.example.finalproxy;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {
    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(ClassProxyConfig.class)) {
            CglibService service = context.getBean(CglibService.class);

            System.out.println(
                "classProxy=" + AopUtils.isCglibProxy(service)
            );
            System.out.println(service.open("A"));
            System.out.println(service.locked("B"));
        }
    }
}
```

Chạy tại thư mục dự án, với Gradle đã cài:

```bash
gradle run --quiet
```

Nếu có Gradle Wrapper: `./gradlew run --quiet` (Linux/macOS) hoặc `gradlew.bat run --quiet` (Windows).

**Các dòng do code chương trình in ra theo đúng thứ tự** (không tính thông báo logger/build tool):

```text
classProxy=true
advice:open
open:A
locked:B
```

Giải thích:

1. `classProxy=true`: Spring trả về một CGLIB Spring AOP proxy.
2. `advice:open`: `open(..)` không final, CGLIB override và chuyển qua advice.
3. `open:A`: phương thức target vẫn được gọi và trả kết quả.
4. `locked:B`: `locked(..)` vẫn được gọi nhưng **không có `advice:locked`**, dù pointcut match signature của nó.

Nếu đổi cả class thành `final` nhưng vẫn giữ class proxy và advisor phù hợp, hãy coi đó là **thử nghiệm lỗi startup riêng**, không phải chỉnh sửa cần làm để ví dụ chuẩn chạy được.

## 5. Khi target class final: cân nhắc JDK interface proxy

JDK dynamic proxy khác CGLIB ở một điểm quyết định: Spring tạo **object implement interface** rồi delegate vào target. Nó **không cần subclass** của target. Vì vậy, một target class `final` vẫn có thể làm việc với Spring AOP nếu:

- Target **implement business interface** phù hợp.
- Cấu hình Spring sử dụng interface-based proxy (`proxyTargetClass=false` và không bị phần cấu hình khác ép class-based proxy).
- Caller lấy/inject bean bằng **interface type**.
- Advice match lời gọi method trên proxy interface; nếu một method chỉ có trên implementation nhưng không thuộc interface được proxy thì caller không thể gọi nó qua JDK proxy theo cách thông thường.

### 5.1. Interface và class final

Bổ sung **ba file** vào cùng package `com.example.finalproxy` của dự án trên.

**`Api.java`**

```java
package com.example.finalproxy;

public interface Api {
    String call(String id);
}
```

**`FinalApi.java`**

```java
package com.example.finalproxy;

public final class FinalApi implements Api {
    @Override
    public final String call(String id) {
        return "api:" + id;
    }
}
```

Ở đây **cả class và method implementation đều final**; nhưng JDK proxy implement `Api` và chặn `Api.call(..)` **trước khi delegate** tới target. `final` của target không ngăn interface proxy intercept lời gọi.

**`InterfaceProxyConfig.java`**

```java
package com.example.finalproxy;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class InterfaceProxyConfig {
    @Bean
    Api api() {
        return new FinalApi();
    }

    @Bean
    ApiTraceAspect apiTraceAspect() {
        return new ApiTraceAspect();
    }

    @Aspect
    public static class ApiTraceAspect {
        @Before("execution(* com.example.finalproxy.Api+.call(..))")
        public void beforeCall() {
            System.out.println("advice:api");
        }
    }
}
```

`Api+` trong biểu thức AspectJ chọn interface `Api` và các subtype/implementation phù hợp. `@Bean` đăng ký aspect vào Spring context; `proxyTargetClass=false` bảo đảm ta không chủ động ép class proxy cho target. **Không import cả `ClassProxyConfig` và `InterfaceProxyConfig` vào cùng một context**, vì các thiết lập auto-proxy có thể hợp nhất và thiết lập ép class proxy sẽ thắng.

### 5.2. Main riêng cho JDK proxy

**`InterfaceApp.java`**

```java
package com.example.finalproxy;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class InterfaceApp {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(
                InterfaceProxyConfig.class)) {
            Api service = context.getBean(Api.class);

            System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(service));
            System.out.println(service.call("C"));
        }
    }
}
```

Tạo task Gradle bổ sung trong `build.gradle`:

```gradle
tasks.register('runJdkProxy', JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'com.example.finalproxy.InterfaceApp'
}
```

Chạy:

```bash
gradle runJdkProxy --quiet
```

**Stdout của demo JDK proxy:**

```text
jdkProxy=true
advice:api
api:C
```

Dù `FinalApi` và `FinalApi.call(..)` đều `final`, advice có thể chạy vì entry call đi qua **JDK interface proxy**, không phải một subclass của `FinalApi`.

Không thử:

```java
FinalApi service = context.getBean(FinalApi.class);
```

trong cấu hình JDK proxy này và mong có target bean gán trực tiếp được theo implementation type. Spring trả về một proxy implement `Api`; kiểu runtime của proxy không phải subclass `FinalApi`. Nếu client cần inject bằng concrete class, phải thay đổi thiết kế/chọn loại proxy phù hợp; target `final` khiến class proxy không khả thi.

**Giới hạn:** JDK proxy chỉ can thiệp tại **method trên interface của proxy**. Một `final` method khác chỉ được khai báo ở implementation class, không nằm trong interface, không được đưa ra trên JDK proxy cho caller sử dụng.

## 6. Thử nghiệm thất bại khi ép CGLIB cho final class

Đây là **đoạn minh họa tình huống thất bại**, không thêm nó vào project chạy chuẩn ở mục 4 hoặc mục 5:

```java
// Ví dụ cố tình sai: final class bị yêu cầu tạo class proxy
public final class FinalBillingService {
    public String bill(String id) {
        return id;
    }
}

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class InvalidConfig {
    @Bean
    FinalBillingService finalBillingService() {
        return new FinalBillingService();
    }

    @Bean
    Object tracingAspect() {
        // Chỉ là placeholder giải thích, KHÔNG dùng làm @Aspect thật.
        return new Object();
    }
}
```

Đoạn trên **chỉ minh họa class final và cấu hình ép proxy; chưa phải test gây lỗi** vì chưa có advisor match `FinalBillingService`. Muốn tái hiện lỗi, cần một **Spring advisor thực sự match bean final**. Ví dụ logic:

```java
@Aspect
class BillingAspect {
    @Before("execution(* FinalBillingService.bill(..))")
    public void beforeBilling() {
        // ...
    }
}
```

Đây cũng là **cú pháp minh họa**, không phải file độc lập: type phải dùng tên fully qualified đúng package và class aspect phải được đăng ký thành Spring bean.

Với advisor thực sự áp dụng, Spring phải proxy bean cuối cùng ấy; CGLIB không thể subclass class `final`. Kết quả phụ thuộc phiên bản và đường cấu hình nên **không khóa một thông báo exception nguyên văn**; kiểm tra chuỗi `BeanCreationException` / `AopConfigException` và root cause liên quan tới class cuối cùng `final`.

**Quan trọng:** Không dùng `@EnableAspectJAutoProxy(proxyTargetClass=false)` để “tự sửa” mọi `final class`. Nếu class không có interface thì Spring vẫn cần class proxy; nếu ứng dụng đồng thời dùng các cấu hình ép class proxy (ví dụ transaction/cache AOP hoặc một context Boot) thì thiết lập thực tế vẫn có thể là CGLIB.

## 7. Cấu hình của Spring Boot và các auto-proxy advisors

Với **Spring Boot** có AOP auto-configuration, property thường gặp:

```properties
# Spring Boot AOP: mặc định thường là true
spring.aop.proxy-target-class=true
```

Đối với target `final` đã triển khai interface, có thể xét:

```properties
spring.aop.proxy-target-class=false
```

để ưu tiên JDK proxy khi hạ tầng và các cấu hình khác cho phép. **Kiểm tra proxy thực tế bằng `AopUtils.isJdkDynamicProxy`/`isCglibProxy`**, vì một Spring context có thể hợp nhất các yêu cầu auto-proxy từ nhiều nơi và lấy cấu hình proxy mạnh hơn.

Dependency chọn theo Spring Boot major version (ví dụ Gradle đã dùng BOM/dependency management của Boot):

```gradle
// Boot 3.x
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

```gradle
// Boot 4.x
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aspectj'
}
```

Hai khối là **phương án theo phiên bản**, không áp dụng cùng lúc. CGLIB được Spring tích hợp qua `spring-core`; không cần khai báo riêng dependency `cglib:cglib` cho Spring AOP hiện đại.

Những annotation Spring thường sử dụng proxy/interceptor và chịu rủi ro final method tương tự khi ở chế độ class proxy:

| Spring feature | Nguy cơ với method final trên CGLIB proxy | Kiểm tra đúng |
| --- | --- | --- |
| `@Transactional` | Transaction interceptor không đi qua method final như mong đợi | Quan sát transaction đang hoạt động, kết quả rollback/commit, và đặt annotation trên entry method intercept được. |
| `@Cacheable`/`@CacheEvict` | Cache advice có thể không chạy ở method final | Kiểm tra cache hit/miss/eviction thực tế, không chỉ xem annotation. |
| `@Async` | Invocation final không chuyển vào async interceptor | Kiểm tra thread, loại return và completion thực tế. |
| Method security như `@PreAuthorize` | Security advice có thể bị bỏ qua ở method final | Cần test quyền truy cập qua đúng Spring proxy; trường hợp này đặc biệt nghiêm trọng. |
| Custom `@Aspect` logging/metrics/audit | Mất logging/audit ở method final | Kiểm tra event quan sát khi gọi method qua container. |

Các annotation trên **đều cần phần cấu hình activation riêng** (transaction management, cache, async, method security). `@EnableAspectJAutoProxy` chỉ bật hỗ trợ Spring AOP `@AspectJ`, không thay cho cấu hình của mọi tính năng khác.

Khi cần ràng buộc bảo mật, luôn có kiểm thử từ bên ngoài với quyền không hợp lệ và xác minh method được bảo vệ; một trace log thiếu hoặc đủ không thay thế bài test authorization.

## 8. Cách xử lý khi hệ thống buộc phải có final class

### Lựa chọn A — Giữ final, dùng interface proxy nếu đáp ứng được

Phù hợp khi:

- Public API được diễn tả bởi một interface rõ ràng.
- Caller inject/tra cứu theo interface.
- Những method cần interception nằm trong interface.
- Không có cấu hình khác buộc class-based proxy.
- Việc dùng JDK proxy đã được kiểm thử với tính năng AOP cụ thể của ứng dụng.

Ví dụ một Spring bean `final` implement `PaymentPort`. Client inject `PaymentPort`, và Spring dùng JDK proxy để chặn method trên contract. **Không dựa vào final implementation type để inject**.

### Lựa chọn B — Cho phép subclass, method cần advice không final

Nếu ứng dụng cần class proxy hoặc API chưa có interface, hãy thiết kế target class có thể subclass:

```java
public class PaymentService {
    public Receipt pay(String orderId) {
        // Public method được phép override, phù hợp entry method AOP.
        return new Receipt(orderId);
    }
}
```

Đoạn code minh họa, giả sử `Receipt` là type tự định nghĩa trong project. Để code test/ứng dụng thật hoạt động, cần thêm Spring bean registration và hạ tầng proxy tương ứng.

Bỏ `final` chỉ ở **entry method cần advice**, giữ tính bất biến nghiệp vụ bằng thiết kế dependency và encapsulation thay vì khóa method bị Spring cần override. Không cần bỏ `final` khỏi mọi helper nội bộ nếu chúng không được advisor chặn và không đi qua proxy.

### Lựa chọn C — Explicit decorator/service boundary

Khi class không thể sửa, không có interface, hoặc cần rõ trình tự thay vì interception ngầm:

```java
public final class FixedGateway {
    public String send(String text) {
        return "sent:" + text;
    }
}

public class AuditedGateway {
    private final FixedGateway delegate;

    public AuditedGateway(FixedGateway delegate) {
        this.delegate = delegate;
    }

    public String send(String text) {
        System.out.println("audit:send");
        return delegate.send(text);
    }
}
```

Đây là **đoạn minh họa về cấu trúc**, không phải ví dụ AOP chạy độc lập. Đăng ký `AuditedGateway` thành Spring bean và inject vào callers sẽ có một ranh giới tường minh để logging/audit. Nếu vẫn muốn Spring AOP trên decorator, bản thân lớp decorator và method entry phải proxy được.

### Lựa chọn D — Cần interception ngoài khả năng proxy

Nếu yêu cầu thực sự là chặn constructor, field access hoặc self-invocation trong class không thể sửa, Spring AOP proxy không đủ. Có thể cân nhắc **AspectJ compile-time/load-time weaving** với cấu hình riêng hoặc chuyển yêu cầu thành lời gọi explicit. Đây không phải tính năng được bật chỉ bằng `@EnableAspectJAutoProxy`. Cần đánh giá phức tạp triển khai, khả năng gỡ lỗi và giới hạn bytecode trước khi chọn weaving.

## 9. Lưu ý riêng với Kotlin, record và những kiểu class đặc biệt

- **Kotlin:** class/method mặc định là final nếu không khai báo `open`. Trong ứng dụng Spring dùng class proxy, có thể sử dụng Kotlin `kotlin-spring` compiler plugin (một preset của `all-open`) để mở những class/method mang annotation Spring thích hợp; hoặc thiết kế interface rõ ràng và dùng JDK proxy. Cần kiểm tra kết quả build/bytecode thực tế, đặc biệt với custom annotation không thuộc danh sách plugin xử lý.
- **Java record:** record là final class; không thể subclass để tạo CGLIB AOP proxy. Record thích hợp làm immutable data carrier hơn là business service Spring cần method interception.
- **Java sealed hierarchy:** một subclass proxy sinh động có thể gặp ràng buộc permitted subclasses; đừng suy rằng mọi sealed class đều proxy được. Kiểm tra loại proxy/thực nghiệm trên phiên bản Spring và JVM đang dùng.
- **JDK module path:** CGLIB đôi khi cần truy cập mở tới package; giới hạn module access là nguyên nhân độc lập với `final`.
- **Methods được khai báo final do bytecode tool khác:** Lombok, Kotlin compiler hoặc thư viện sinh bytecode có thể khiến class/method là final dù source code không thể hiện như kỳ vọng. Khi khó chẩn đoán, xem bytecode bằng `javap -p -v` và xác nhận modifiers.

Không dùng cùng một lời giải cho mọi nguyên nhân. Ví dụ Kotlin all-open giải quyết ràng buộc override ở build-time đối với các class được mở; nó không tự sửa self-invocation.

## 10. Chẩn đoán bằng Spring APIs

### 10.1. Kiểm tra loại proxy

```java
Object bean = context.getBean("serviceBean");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
```

Đoạn code **minh họa**, cần import `org.springframework.aop.support.AopUtils` và có biến `context` phù hợp. Diễn giải:

- `isAopProxy`: object do container trả về là Spring AOP proxy hay không.
- `isJdkDynamicProxy`: proxy được tạo theo interface JDK.
- `isCglibProxy`: proxy class-based do Spring tạo.
- **Cả ba không xác nhận một method final đã được advice.** Hãy gọi method qua bean proxy và kiểm tra side effect/recorder.

### 10.2. Kiểm tra modifiers bằng reflection

```java
import java.lang.reflect.Modifier;

Class<?> type = CglibService.class;

System.out.println("classFinal=" + Modifier.isFinal(type.getModifiers()));
System.out.println(
    "lockedFinal=" + Modifier.isFinal(
        type.getMethod("locked", String.class).getModifiers()
    )
);
```

Đây là **đoạn Java minh họa**, không phải chương trình độc lập vì dựa trên `CglibService` của ví dụ. Trong demo, nó in:

```text
classFinal=false
lockedFinal=true
```

Lấy modifiers của **target type** trước/sau khi kiểm tra proxy: runtime type của một CGLIB proxy có thể mang tên sinh động, không phải class nguồn. Muốn biết target class thực, có thể sử dụng `AopUtils.getTargetClass(bean)`.

### 10.3. Ma trận kiểm chứng thực tế

| Kiểm chứng | Kỳ vọng với CGLIB | Kỳ vọng với JDK proxy |
| --- | --- | --- |
| Bean target class `final`, có advisor match | Không thể tạo class subclass proxy | Có thể hoạt động nếu target implement interface phù hợp. |
| Bean target class không final, method `final` match advisor | Proxy có thể tồn tại, nhưng final method không được advice | Nếu lời gọi vào method interface được proxy và advisor match: có thể advice dù implementation final. |
| Method nằm ngoài interface | CGLIB có thể intercept nếu override được | Không expose method ấy qua JDK proxy. |
| Self-invocation `this.method()` | Không chạy advice cho lời gọi nội bộ | Không chạy advice cho lời gọi nội bộ. |
| `AopUtils.isAopProxy(bean)` | Có thể true dù method final không được advice | Có thể true nhưng pointcut method còn phải match. |
| Caller inject theo implementation | Thường phù hợp khi class subclassable | Không nên; proxy chỉ implement interface. |

### 10.4. Checklist tìm lỗi trong dự án

1. Xác định Spring bean và **method entry** được caller gọi: annotation nằm ở đâu, target là bean nào?
2. Gọi `context.getBean(...)` theo type được hỗ trợ, xác nhận **kiểu proxy thực tế** trước khi nhận định.
3. Dùng reflection/bytecode xem **target class** có final, **entry method** có final/private hay không.
4. Xem tất cả cấu hình ảnh hưởng proxy: `@EnableAspectJAutoProxy`, `@EnableTransactionManagement`, Boot `spring.aop.proxy-target-class` và những module tạo advisor khác.
5. Xem caller có đi qua proxy hay tự gọi `this.method()`, hoặc gọi target object được tạo bằng `new`.
6. Kiểm tra method có thuộc interface nếu dùng JDK proxy; client nên inject interface.
7. Gọi một method bình thường và method final có cùng policy qua Spring bean; assert **advice xuất hiện đúng nơi**.
8. Với bảo mật/transaction/cache, test hậu quả nghiệp vụ thực tế (deny, rollback, hit/miss), không dựa vào log proxy.
9. Nếu không có cách proxy thích hợp, refactor entry point hoặc thay bằng decorator/lời gọi explicit.

## 11. Những nhầm lẫn gây lỗi khó phát hiện

1. **“Có `@Aspect`, pointcut match là mọi method đều được intercept.”** Spring AOP còn phụ thuộc proxy, chữ ký method và đường gọi.
2. **“Bật `proxyTargetClass=true` để chặn được cả method final.”** Sai: chọn class proxy khiến `final` trở thành giới hạn override trực tiếp.
3. **“Class final luôn không thể có Spring AOP.”** Sai: JDK dynamic proxy có thể bao quanh một **interface** của final target class.
4. **“Chỉ cần class có interface là Spring sẽ dùng JDK proxy.”** Chưa đủ: cấu hình ép CGLIB và Boot AOP default có thể thay đổi lựa chọn.
5. **“Final method gây lỗi khởi động giống final class.”** Thường sai: class còn subclass được, nên proxy có thể tạo; riêng final method bị bỏ qua interception.
6. **“Mọi lời gọi đến final method qua CGLIB proxy đều an toàn vì method gốc vẫn chạy.”** Không nên suy như vậy, nhất là với stateful target; final method không được Spring override/delegate theo đường advice và có thể thấy state trên proxy khác dự kiến.
7. **“CGLIB proxy = AspectJ weaving.”** Sai: vẫn là Spring proxy, không tự chặn bytecode bên trong target hoặc constructor.
8. **“Thử thấy `isAopProxy=true` thì `@Transactional` chắc chắn có tác dụng.”** Sai: cần kiểm tra actual invocation và kết quả giao dịch.
9. **“Không thấy advice do final method thì thêm `exposeProxy=true`.”** Không đúng nguyên nhân: expose proxy không biến một final method thành override-able.
10. **“Bỏ final trong Kotlin source luôn đủ.”** Cần xem compiler plugin, Java bytecode, method visibility, proxy config, interface injection và call path.

## 12. Quy tắc ra quyết định

- **Muốn giữ class `final` và vẫn dùng Spring AOP?** Thiết kế interface làm contract, chọn JDK proxy, inject bằng interface và xác nhận cấu hình thực tế không ép CGLIB.
- **Muốn class-based Spring proxy?** Class phải subclass được; method cần advice phải override được, không `final`/`private`, caller phải đi qua proxy.
- **Đang thấy bean là proxy nhưng policy trên final method không hoạt động?** Đây là kết quả có thể dự đoán của CGLIB; đổi entry boundary trước khi tăng thêm annotation.
- **Cần chặn nội bộ/field/constructor?** Spring proxy không đáp ứng; cân nhắc thiết kế khác hoặc AspectJ weaving riêng với cấu hình phù hợp.
- **Policy liên quan bảo mật hoặc transaction?** Đòi hỏi integration test xác nhận hành vi nghiệp vụ, đặc biệt kiểm tra các method final và cách inject bean.

**Ghi nhớ:** CGLIB của Spring AOP **cần subclass**. `final class` khiến việc tạo class proxy không thể thực hiện; `final method` khiến riêng method đó không được advice, dù bean vẫn có proxy. **JDK interface proxy** có thể giải quyết trường hợp final target khi API và cấu hình phù hợp, nhưng không giải quyết self-invocation hoặc method ngoài interface.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Proxying Mechanisms (giới hạn final và JDK/CGLIB)](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html)
- [Spring Framework 6.2 — AOP Proxies](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-proxies.html)
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework 6.2 — ProxyFactoryBean and JDK/CGLIB](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pfb.html)
- [Spring Boot — Spring AOP auto-configuration](https://docs.spring.io/spring-boot/reference/features/aop.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-006">Phương thức private, static và lời gọi không thể ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: phương thức private, static và lời gọi không thể ghi đè

## 1. Vấn đề: có aspect và pointcut nhưng advice vẫn không chạy

Spring AOP là cơ chế **proxy-based AOP** dành cho các lời gọi phương thức trên bean do Spring quản lý. Khi một service được bọc bằng proxy, lời gọi từ consumer đi qua proxy có thể bị advisor/advice chặn. Có những lời gọi mà proxy **không thể quan sát**, dù biểu thức pointcut nhìn qua có vẻ đúng:

- Phương thức `private` không nằm trên bề mặt phương thức có thể gọi từ bên ngoài và không thể bị subclass override.
- Phương thức `static` thuộc về class; lời gọi theo tên class không phải lời gọi instance qua Spring AOP proxy.
- Phương thức instance `final` không thể bị CGLIB subclass override; với **JDK interface proxy**, quy tắc có khác: proxy vẫn có thể chặn lời gọi thông qua method của interface, ngay cả khi implementation method là `final`.
- Class `final` không thể được CGLIB subclass để tạo proxy. Nếu class triển khai interface phù hợp, Spring có thể chọn JDK interface proxy.
- Method có visibility ngăn subclass truy cập/override, ví dụ package-private method kế thừa từ class thuộc package khác, có thể không được CGLIB advice.
- **Self-invocation** như `this.publicMethod()` bỏ qua proxy, dù method đó public và có thể override. Đây là nguyên nhân **khác** với việc method không thể override.

Cần phân biệt hai câu hỏi:

1. **Proxy có thể tạo điểm chặn cho method này không?** Phụ thuộc JDK proxy hay CGLIB, interface, visibility và khả năng override.
2. **Lời gọi cụ thể có đi qua proxy không?** Phụ thuộc reference mà caller dùng. Một method public bình thường cũng không được advice nếu target tự gọi qua `this`.

Bài này tập trung hoàn toàn vào **giới hạn trong Spring AOP** và cách xử lý khi dùng Spring IoC, dependency injection, Spring AOP proxy; không mở rộng sang giải thích AOP tổng quát.

## 2. Spring AOP chặn lời gọi ở đâu?

### 2.1. Đường gọi qua proxy

```text
Consumer (Spring bean hoặc code lấy bean từ ApplicationContext)
            |
            | service.publicOperation()
            v
        Spring AOP proxy
            |
            | kiểm tra advisor/pointcut
            v
        Interceptor / advice
            |
            v
        Target.publicOperation()
```

Target và proxy có thể là các đối tượng khác nhau. Khi bean khác inject service, Spring cấp **tham chiếu bean do container công bố**; nếu được AOP bọc, đó thường là proxy. Với một target tự tạo bằng `new` bên ngoài container, không có quá trình xuất bản Spring AOP proxy này.

### 2.2. Những đường gọi bypass proxy

```text
Target.publicOperation()
      |
      +---- this.otherPublic()   ---> target trực tiếp
      |
      +---- privateHelper()      ---> target trực tiếp
      |
      +---- finalMethod()        ---> target trực tiếp
      |
      +---- SomeClass.staticOp() ---> class, không phải proxy instance
```

**Self-invocation:** Một method đi qua proxy ở đầu luồng không có nghĩa mọi method nó gọi nội bộ cũng qua proxy. Khi target đã nhận điều khiển, lời gọi `this.otherPublic()` là lời gọi trên target, không quay lại Spring proxy.

**Khả năng override:** Riêng CGLIB tạo subclass để intercept các lời gọi phù hợp. Nó không thể override `private` và `final` methods. Nhưng một method public có thể override vẫn có thể bị bỏ qua advice do self-invocation.

**Giới hạn pointcut:** Spring AOP chỉ hỗ trợ **method execution join points trên Spring beans**, không hỗ trợ các join point kiểu `call`, `get`, `set`, constructor execution, hoặc static initialization trong cách dùng @AspectJ pointcut của Spring AOP. Một chuỗi pointcut có vẻ nhắm đến method nào đó không giúp proxy chặn một lời gọi mà proxy không nhận được.

## 3. Phương thức private: không có đường gọi qua Spring proxy

Xem service:

```java
@Service
public class AccountService {
    public void register(String id) {
        validate(id); // private method trên chính target
        System.out.println("Registered " + id);
    }

    private void validate(String id) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("Empty id");
        }
    }
}
```

Đây là **fragment minh họa**, không phải ứng dụng chạy độc lập. Nếu một aspect được đặt để advice `register(...)`, lời gọi từ bean khác vào `register(...)` qua Spring proxy có thể được intercept. Nhưng `validate(...)` là `private`:

- Caller bên ngoài không thể gọi trực tiếp `validate` qua public API của service.
- JDK proxy chỉ có thể công bố các method public trên interface; `private` method của target không thuộc bề mặt này.
- CGLIB proxy subclass không thể override một private method. Do đó không thể dùng advice của Spring AOP proxy để chặn `validate()`.
- Thêm annotation vào private method hoặc viết pointcut chỉ nhắm vào method ấy **không biến** lời gọi private thành lời gọi proxy.
- Reflection gọi private method trên target cũng không tự khiến nó đi qua AOP proxy.

**Hướng xử lý:** Nếu chỉ cần kiểm tra input nội bộ, cứ giữ helper private và để advice áp dụng cho **public service boundary**. Nếu logic private thật sự cần một advice riêng như transaction/caching/audit, hãy cân nhắc đưa logic ấy thành public method của **một Spring bean khác**, sau đó inject bean đó và gọi từ service.

Ví dụ:

```java
@Service
public class RegistrationService {
    private final ValidatorService validator;

    public RegistrationService(ValidatorService validator) {
        this.validator = validator;
    }

    public void register(String id) {
        validator.validate(id); // Lời gọi qua bean được Spring inject.
    }
}

@Service
public class ValidatorService {
    public void validate(String id) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("Empty id");
        }
    }
}
```

Hai class trên là fragment thiết kế, cần import `org.springframework.stereotype.Service` và đặt trong các tệp thích hợp. Lời gọi sang `ValidatorService` có thể đi qua Spring AOP proxy nếu aspect/advisor chọn method `validate`. Tách bean nên dựa trên trách nhiệm hợp lý, không tạo thêm bean chỉ để làm mất encapsulation nếu advice ở public boundary đã đủ.

## 4. Phương thức static: lời gọi gắn với class, không có instance proxy

Trong Java, `static` method được gọi theo class:

```java
public class FeeRules {
    public static long calculate(long subtotal) {
        return subtotal * 5 / 100;
    }
}
```

Lời gọi `FeeRules.calculate(1000)` không đi vào một Spring bean proxy. Dù `FeeRules` hoặc một object khác được đăng ký thành bean, static method vẫn gắn với **class**.

Với Spring AOP proxy:

- **JDK dynamic proxy** chặn các lời gọi method trên đối tượng proxy qua các interface. Static interface method không phải instance method được kế thừa bởi implementing class và không phải method trên proxy instance.
- **CGLIB proxy** là subclass của target. Static methods trong Java bị **ẩn** (method hiding), không được override đa hình; không thể dùng override interception để advice một static method.
- Annotation lên static method, hoặc `execution(* ...staticMethod(..))` trong một pointcut, không làm xuất hiện một Spring proxy interception point cho lời gọi static.
- `@Component` trên class chứa static helper không đổi cách Java dispatch lời gọi static.

Nếu muốn bổ sung logging, cache, transaction hoặc policy thông qua Spring AOP, chuyển hành vi cần intercept thành **instance method của Spring-managed bean**:

```java
@Service
public class FeeService {
    public long calculate(long subtotal) {
        return subtotal * 5 / 100;
    }
}
```

Sau đó consumer nhận `FeeService` bằng constructor injection và gọi qua reference do Spring cung cấp. Nếu static method chỉ là utility thuần, không có nhu cầu advice, giữ static là lựa chọn hoàn toàn bình thường.

**Điểm dễ hiểu sai:** Không phải “static không hoạt động trong Spring”. Static methods vẫn chạy Java bình thường; chúng chỉ không đi qua **Spring AOP proxy method interception**.

## 5. Final method và final class: khác biệt giữa JDK proxy và CGLIB

### 5.1. Với CGLIB: cần subclass và override

Spring dùng CGLIB class-based proxy khi target không có interface thích hợp hoặc cấu hình buộc dùng class proxy. Về cơ chế, proxy là một subclass của target; do đó:

- `final class` không thể bị subclass, nên không thể tạo **CGLIB proxy** cho class ấy.
- `final instance method` không thể bị subclass override, nên **không được advice qua CGLIB**.
- `private method` cũng không thể override.
- Method không thể truy cập từ subclass, ví dụ một package-private method khai báo trong supertype thuộc package khác, cũng không thể được proxy subclass intercept theo cách thông thường.

Ví dụ:

```java
public class JobService {
    public void run() {
        System.out.println("run");
    }

    public final void locked() {
        System.out.println("locked");
    }
}
```

Với CGLIB proxy, `run()` có thể advice nếu lời gọi từ bên ngoài qua proxy và advisor khớp. `locked()` vẫn chạy theo logic Java, nhưng không có advice của proxy kiểu subclass. Chỉ đổi tên pointcut không thể sửa giới hạn `final`.

Một điểm thực hành quan trọng: với proxy CGLIB, lời gọi đến method `final` có thể thực thi trực tiếp method được kế thừa trên **đối tượng proxy subclass**, không đi qua interceptor/delegation đến target như các method được override. Nếu method final truy cập mutable fields hoặc dependency đã khởi tạo ở target, behavior có thể gây bất ngờ trong một số cấu hình proxy. Đừng thiết kế nghiệp vụ dựa vào lời gọi final method trên class proxy; nếu cần advice, hãy chọn method có thể proxy hoặc đổi ranh giới bean.

### 5.2. Với JDK proxy: final method của implementation có thể được advice

JDK dynamic proxy **không phải subclass của target**. Nó tạo object triển khai các interface mà Spring chọn proxy, rồi xử lý lời gọi public interface method trước khi chuyển cho target. Vì vậy một `final` method trên target **không cản trở** intercept lời gọi qua interface tương ứng.

```java
public interface JobPort {
    void locked();
}

public final class FinalJobService implements JobPort {
    @Override
    public final void locked() {
        System.out.println("locked");
    }
}
```

Đây là fragment độc lập về kiểu Java nhưng chưa chứa Spring context/aspect. Nếu `FinalJobService` được đăng ký làm Spring bean, có advisor cho `locked()` và được dùng qua **JDK proxy của `JobPort`**, advice có thể chạy mặc dù class và implementation method đều `final`.

Không được suy ra rằng “mọi phương thức final đều không thể được Spring AOP advice”. Câu đúng là **CGLIB không thể advice final method theo cơ chế override**, còn JDK proxy có thể intercept public interface method trước khi đến target final implementation.

Nếu target class là `final` nhưng không có interface phù hợp, Spring không thể tạo subclass proxy để advice phương thức của class đó; khi bean thật sự cần class-based proxy, quá trình tạo proxy có thể thất bại.

### 5.3. Bảng quyết định

| Loại phương thức hoặc class | JDK dynamic proxy của Spring | CGLIB class-based proxy của Spring |
| --- | --- | --- |
| Public method khai báo trên interface được proxy thực hiện | Có thể advice nếu pointcut khớp | Có thể advice nếu method có thể override |
| Public method chỉ có trên concrete class | Không được JDK proxy công bố qua interface | Có thể advice nếu method có thể override |
| Private method của target | Không | Không |
| Static method của target/interface | Không | Không |
| Public final method được khai báo trên interface | Có thể advice qua lời gọi interface | Không thể override để advice |
| Final class triển khai interface | Có thể tạo JDK proxy qua interface | Không subclass được class |
| Final class không có interface phù hợp | Không có bề mặt interface proxy thích hợp | Không subclass được |
| Protected/package-private instance method trên target | Không thuộc public interface proxy surface | Có thể tùy quyền truy cập/khả năng override; không xem là public API |
| Package-private method khai báo ở supertype khác package | Không nằm trên public interface | Có thể không truy cập/override được |
| Public, non-final method nhưng `this.method()` | Không advice lời gọi self-invocation | Cũng không advice lời gọi self-invocation |

*Bảng giả định target thuộc Spring container, cơ chế AOP được bật và có advisor thích hợp. “Có thể advice” luôn cần lời gọi qua proxy; không phải bảo đảm advice tự chạy.*

## 6. Self-invocation và trường hợp “method hoàn toàn override được”

Đây là lỗi thường gặp nhất khiến người học nhầm với giới hạn `private`/`final`:

```java
@Service
public class TransferService {
    public void transfer() {
        this.audit(); // Vẫn bypass Spring proxy.
    }

    public void audit() {
        System.out.println("audit");
    }
}
```

`audit()` là public, non-final và có thể override. Nếu một consumer gọi `transferService.audit()` trên Spring proxy, advice cho `audit()` có thể chạy. Nhưng khi target gọi `this.audit()`, lời gọi trên **target** không quay qua proxy. Cả JDK và CGLIB proxy đều có giới hạn self-invocation này trong Spring AOP.

Phân biệt chính xác:

- **Private/final/static:** có giới hạn bản chất của kiểu proxy hoặc Java dispatch, nên ngay cả một lời gọi tương tự từ ngoài cũng không tạo được interception phù hợp qua CGLIB/instance proxy.
- **Self-invocation:** method vẫn có thể advice qua lời gọi **từ một bean khác đến proxy**, nhưng cuộc gọi từ chính target không đi qua proxy.
- **Constructor và initialization callbacks:** logic chạy trong quá trình tạo target, trước khi bean được dùng qua proxy cuối cùng; không nên kỳ vọng `@Transactional` hoặc advice khác được kích hoạt từ `@PostConstruct` hoặc constructor.

**Giải pháp ưu tiên:** tách method cần policy riêng sang một Spring bean có trách nhiệm rõ ràng, inject bằng constructor và gọi qua reference đó. Với nhu cầu đặc thù, `AopContext.currentProxy()` có thể cho code biết proxy hiện tại nếu cấu hình `exposeProxy=true` và lời gọi đang ở trong một invocation qua proxy; cách này gắn business code với Spring AOP, không biến private/static/final method thành interceptable.

## 7. Ví dụ hoàn chỉnh chạy được — Spring Framework 6.2 và Java 17

Ví dụ cho thấy một target service dùng **CGLIB proxy**, có method public có thể override, private helper, final method, static method và lời gọi public method qua `this`. Pointcut cố tình rộng để quan sát chính xác **lời gọi nào Spring AOP proxy chặn được**.

Cấu trúc:

```text
spring-method-limits/
├── pom.xml
└── src/main/java/demo/limits/
    ├── AppConfig.java
    ├── VisibilityService.java
    ├── TraceAspect.java
    └── Demo.java
```

### 7.1. Maven dependencies

Tệp `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-method-limits</artifactId>
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

Dự án dùng Spring Framework thuần, không cần Spring Boot. `aspectjweaver` cung cấp phần hỗ trợ đọc @AspectJ pointcut cho Spring AOP. Không phải bật AspectJ compiler/load-time weaving để chạy ví dụ này.

### 7.2. Cấu hình auto-proxy

Tệp `src/main/java/demo/limits/AppConfig.java`:

```java
package demo.limits;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@ComponentScan(basePackageClasses = AppConfig.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
}
```

`proxyTargetClass=true` ép class-based proxy. Target bên dưới không có interface; ví dụ dùng CGLIB để thể hiện rõ giới hạn override. `@ComponentScan` quét package `demo.limits`, tìm target và aspect.

### 7.3. Target có public/private/final/static methods

Tệp `src/main/java/demo/limits/VisibilityService.java`:

```java
package demo.limits;

import org.springframework.stereotype.Service;

@Service
public class VisibilityService {
    public void publicAction() {
        System.out.println("[target] publicAction");
    }

    public void route() {
        System.out.println("[target] route");
        this.publicAction(); // Public nhưng self-invocation.
        privateAction();      // Private, không override được.
        finalAction();        // Final, không override được.
        staticAction();       // Static, không qua proxy.
    }

    private void privateAction() {
        System.out.println("[target] privateAction");
    }

    public final void finalAction() {
        System.out.println("[target] finalAction");
    }

    public static void staticAction() {
        System.out.println("[target] staticAction");
    }
}
```

Class không `final` nên CGLIB có thể tạo subclass. `publicAction()` và `route()` có thể override. `finalAction()` được public để caller có thể thử gọi từ bên ngoài; nó vẫn không thể bị CGLIB advice.

### 7.4. Aspect có pointcut rộng

Tệp `src/main/java/demo/limits/TraceAspect.java`:

```java
package demo.limits;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TraceAspect {
    @Before("execution(* demo.limits.VisibilityService.*(..))")
    public void before(JoinPoint joinPoint) {
        System.out.println("[advice] " + joinPoint.getSignature().getName());
    }
}
```

Biểu thức `execution(* ...VisibilityService.*(..))` chọn các method matching signature của class ở mức pointcut, nhưng **không thể biến lời gọi private/static/final thành lời gọi được CGLIB proxy intercept**. Hai lời gọi vào public, non-final method từ bên ngoài qua proxy mới tạo advice trong ví dụ này.

### 7.5. Main lấy Spring bean và thực hiện các lời gọi

Tệp `src/main/java/demo/limits/Demo.java`:

```java
package demo.limits;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Demo {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            VisibilityService service = context.getBean(VisibilityService.class);

            System.out.println("cglibProxy="
                    + AopUtils.isCglibProxy(service));

            service.publicAction();
            service.route();
            service.finalAction();
            VisibilityService.staticAction();
        }
    }
}
```

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.limits.Demo
```

Các dòng chuẩn do chương trình in (Maven/Spring có thể phát thêm log/cảnh báo):

```text
cglibProxy=true
[advice] publicAction
[target] publicAction
[advice] route
[target] route
[target] publicAction
[target] privateAction
[target] finalAction
[target] staticAction
[target] finalAction
[target] staticAction
```

**Đọc kết quả theo từng đường gọi:**

1. `AopUtils.isCglibProxy(service)` trả `true` vì service được advice và `proxyTargetClass=true`.
2. `service.publicAction()` từ `Demo` gọi qua proxy. `@Before` in `[advice] publicAction` trước target.
3. `service.route()` cũng gọi qua proxy. Advice in `[advice] route`.
4. Trong `route()`, lời gọi `this.publicAction()` **không đi qua proxy**, dù public và override được: chỉ in `[target] publicAction`.
5. `privateAction()` không thể override hay được CGLIB advice; chỉ in phần target.
6. `finalAction()` không thể CGLIB override. Lời gọi trong `route()` và lời gọi trực tiếp từ `Demo` đều **không** in thêm advice.
7. `staticAction()` được gọi trong `route()` và trực tiếp theo tên class. Cả hai là Java static method calls, không phải proxy instance invocation.

Vì ví dụ không giữ state trong target, thứ tự output không bị nhiễu bởi các khác biệt thực thể target/proxy đối với final method. **Không suy rộng** rằng có thể dùng final method an toàn để truy cập mọi state của CGLIB proxied object.

### 7.6. Thử thay pointcut chỉ chọn final method

Nếu đổi advice thành:

```java
@Before("execution(* demo.limits.VisibilityService.finalAction(..))")
public void beforeFinal() {
    System.out.println("[advice] final");
}
```

đây là snippet **thay thế method trong `TraceAspect`**, không phải source file riêng. Spring không thể intercept lời gọi final method bằng CGLIB. Trong trường hợp chỉ có advisor này và không advisor nào chặn method có thể proxy khác, đừng giả định một bean chắc chắn nhận proxy. Cấu hình AOP và việc advisor chọn candidate bean có thể tác động quyết định tạo proxy; dù có tạo proxy, `finalAction()` vẫn không được override/advice.

### 7.7. Thử static hoặc private method bằng annotation

Một phương thức private như:

```java
@Transactional
private void storeAudit() {
    // ...
}
```

hoặc static như:

```java
@Transactional
public static void storeAudit() {
    // ...
}
```

là **fragment minh họa** và cần import `org.springframework.transaction.annotation.Transactional` trong ứng dụng dùng module transaction. Khi Spring transaction được cấu hình ở chế độ **proxy** thông thường, annotation đó **không làm Spring AOP chặn** private/static method. Trước khi kết luận một transaction annotation “bị lỗi”, cần kiểm tra method visibility, đường gọi qua proxy và các ranh giới bean.

## 8. Ví dụ phụ: JDK proxy vẫn advice được implementation final method

Vì bài học nằm trong nhóm JDK và CGLIB, cần phân biệt ngoại lệ quan trọng đối với `final`. Không dùng cùng class `VisibilityService` ở ví dụ trên để minh họa JDK proxy vì class đó không có interface.

Mã Java sau là **fragment minh họa** hai tệp kiểu/interface:

```java
public interface WorkPort {
    void finish();
}

public final class FinalWorker implements WorkPort {
    @Override
    public final void finish() {
        System.out.println("finished");
    }
}
```

Đăng ký `FinalWorker` làm Spring bean, bật AOP chọn JDK proxy (Spring Framework thuần dùng `@EnableAspectJAutoProxy(proxyTargetClass=false)`), có một advisor khớp `finish()`, và inject `WorkPort` vào consumer. Khi consumer gọi `port.finish()`, interceptor chạy **trên interface proxy trước khi delegate** đến target. Vì JDK proxy không cần subclass `FinalWorker`, `final class` và `final method` của implementation không ngăn advice.

Tuy nhiên, nếu consumer đòi inject `FinalWorker` trực tiếp khi Spring trả JDK proxy, có thể lỗi kiểu, vì proxy không phải instance của concrete class. Nên dùng interface `WorkPort` làm hợp đồng dependency.

**Không bao giờ áp dụng kết luận của JDK proxy cho CGLIB:** CGLIB vẫn không thể subclass final class hay override final method.

## 9. Khi cần advice cho hành vi đang nằm trong private/static/final method

### 9.1. Đặt advice lên public entry point

Nếu requirement là “mọi lần gọi đăng ký tài khoản phải được ghi audit”, hãy advice public method `RegistrationService.register()`. Nó có thể gọi nhiều helper private, nhưng lời gọi đến entry point đã được advice trước/sau toàn bộ thao tác theo thời điểm advice được cấu hình. Đây thường là giải pháp tối thiểu và dễ kiểm soát nhất.

Nếu cần transaction quanh chuỗi thao tác, đặt transaction boundary ở public service method được bean khác gọi qua proxy, thay vì trên helper private được gọi nội bộ.

### 9.2. Tách policy boundary thành bean riêng

Nếu một helper cần chính sách riêng, có thể tách thành bean chuyên trách:

```java
@Service
public class ApprovalService {
    private final AuditService auditService;

    public ApprovalService(AuditService auditService) {
        this.auditService = auditService;
    }

    public void approve(String id) {
        auditService.record(id); // Method invocation qua dependency Spring.
    }
}

@Service
public class AuditService {
    public void record(String id) {
        System.out.println("audit " + id);
    }
}
```

Đây là fragment minh họa thiết kế, cần các imports/package và cấu hình Spring thích hợp. Khi `AuditService` được proxy do advisor khớp `record()`, lời gọi qua dependency được chặn. Cách này còn giúp test độc lập và tránh coupling với `AopContext.currentProxy()`.

### 9.3. Chọn JDK proxy nếu hợp đồng interface phù hợp

Nếu target class phải `final` do yêu cầu thiết kế nhưng vẫn có public interface, cân nhắc Spring AOP dựa trên JDK proxy. Spring Boot tự cấu hình AOP thường mặc định CGLIB; nếu muốn ưu tiên JDK proxy, có thể đặt:

```properties
spring.aop.proxy-target-class=false
```

Trong Spring Framework thuần, `@EnableAspectJAutoProxy(proxyTargetClass=false)` là mặc định của annotation. Giá trị `false` không tự bảo đảm JDK proxy nếu bean không có interface hoặc có cấu hình auto-proxy khác buộc class-based proxy.

Dùng `AopUtils.isJdkDynamicProxy(bean)` và `AopUtils.isCglibProxy(bean)` kiểm tra runtime trên reference được Spring inject/getBean; không dựa hoàn toàn vào tên class hoặc một annotation đơn lẻ.

### 9.4. Khi nào cần cơ chế ngoài Spring AOP proxy?

Nếu requirement bắt buộc intercept lời gọi private/static/constructor/field access hoặc self-invocation ở mức bytecode, **Spring AOP proxy không hỗ trợ**. Đó là một phạm vi khác, có thể yêu cầu giải pháp compile-time/load-time AspectJ weaving với cấu hình khác, hoặc thay đổi thiết kế để biểu diễn operation qua public Spring bean boundary. Không thể đạt cùng kết quả chỉ bằng đổi pointcut hoặc bật `proxyTargetClass=true`.

## 10. Chẩn đoán khi “pointcut đúng nhưng advice không chạy”

| Dấu hiệu | Nguyên nhân có thể xảy ra | Hướng kiểm tra |
| --- | --- | --- |
| Method `private` có `@Transactional` nhưng transaction không mở | Private method không nằm trên proxy surface | Đặt boundary trên method proxyable hoặc tách bean |
| Method `static` có pointcut khớp về tên nhưng advice không chạy | Static call không qua instance proxy | Đổi sang instance method trên Spring bean nếu cần advice |
| `public final` method trên CGLIB proxy không advice | Không thể override final method | Bỏ final nếu thiết kế cho phép, dùng JDK interface proxy, hoặc tách bean |
| Target class `final` và buộc class proxy | CGLIB không subclass được | Dùng interface/JDK proxy hoặc thay thiết kế |
| Public non-final method gọi qua `this` không advice | Self-invocation bypass proxy | Gọi qua bean khác được DI |
| Method package-private trong base class khác package không advice | Không visible/override được trong subclass proxy | Đặt API tại method public/protected phù hợp |
| Có interface nhưng runtime lại CGLIB | Spring Boot mặc định class-based proxy hoặc có cấu hình gộp | Kiểm tra `spring.aop.proxy-target-class` và `AopUtils` |
| Tự `new Service()` rồi gọi method | Object không do Spring quản lý | Inject bean từ container thay vì tự tạo target |
| `@Aspect` có mặt nhưng không có advice nào chạy | Aspect chưa thành Spring bean, AOP chưa bật, hoặc pointcut không khớp | Xác minh `@Component`/`@Bean` và `@EnableAspectJAutoProxy`/Boot AOP |
| Advice chạy trên `outer()` nhưng không chạy trên method gọi nội bộ | Caller đi vào proxy một lần, sau đó target dùng `this` | Xem đường gọi thực tế, tách thành bean riêng khi cần |

### Quy trình kiểm tra có thể lặp lại

1. Xác nhận target là Spring-managed bean và aspect đã được đăng ký, hạ tầng AOP đã bật.
2. Lấy **reference thực tế** tại injection point hoặc bằng `ApplicationContext.getBean(...)`.
3. Kiểm tra với `AopUtils.isAopProxy(bean)`; nếu cần, phân biệt `isJdkDynamicProxy` và `isCglibProxy`.
4. Xác định method là instance hay static, public/protected/private, final hay có thể override, và target class có final hay không.
5. Nếu JDK proxy, kiểm tra method có thuộc interface mà proxy công bố hay không; nếu CGLIB, kiểm tra subclass visibility/overridability.
6. Theo dõi **đường gọi**: từ consumer qua proxy hay qua `this`/object `new`/callback khởi tạo.
7. Chỉ sau đó mới điều chỉnh pointcut. Đừng mở rộng pointcut để chữa lỗi thuộc về đường gọi hoặc kiểu proxy.

## 11. Tự kiểm tra

**1. Tại sao CGLIB không advice được một private method dù biểu thức `execution(* my.pkg.Service.helper(..))` có vẻ chính xác?** Vì subclass proxy không thể override private method và không thể nhận một lời gọi private thông qua proxy.

**2. Một public final implementation method có luôn nằm ngoài Spring AOP không?** Không. JDK proxy có thể intercept public interface invocation đến final implementation. CGLIB thì không override được final method.

**3. Một service có public instance method, nhưng aspect không chạy khi gọi từ method khác cùng class. Vì sao?** Self-invocation đi trên target `this` nên không qua proxy; không liên quan đến method có final hay không.

**4. Bật `proxyTargetClass=true` có giúp intercept static method không?** Không. Static method không được override và không là instance invocation qua proxy.

**5. Vì sao `final class` có thể chạy AOP qua JDK proxy nhưng không qua CGLIB?** JDK proxy triển khai interface; CGLIB cần tạo subclass của target, điều Java cấm với final class.

**6. Khi nào nên tách logic private thành bean riêng?** Khi phần nghiệp vụ ấy thực sự cần policy riêng qua Spring AOP và có ranh giới trách nhiệm rõ ràng; nếu chỉ là helper nội bộ, advice ở public entry point thường đã hợp lý.

## Tài liệu Spring chính thức

- [Spring Framework — Proxying Mechanisms (JDK, CGLIB, private/final và self-invocation)](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — Declaring a Pointcut (phạm vi method execution join points)](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- [Spring Framework — ProxyFactoryBean và class-based proxy](https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html)
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-007">Phạm vi public, protected và package-visible theo cơ chế proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi public, protected và package-visible theo cơ chế proxy trong Spring AOP

## 1. Mục tiêu

Spring AOP can thiệp vào lời gọi phương thức **đi qua Spring proxy**. Tuy nhiên, việc một phương thức có thể được advise hay không còn phụ thuộc vào **loại proxy**, **visibility của phương thức**, **kiểu dữ liệu mà client có thể gọi** và **cách lời gọi đi vào bean**.

Bài này giải quyết trực tiếp câu hỏi: với cùng một service bean trong Spring, lời gọi tới phương thức **public**, **protected** và **package-visible (package-private)** khác nhau thế nào khi Spring dùng **JDK dynamic proxy** hoặc **CGLIB proxy**?

Sau bài học, bạn có thể:

- Phân biệt chính xác vùng phương thức Spring AOP có thể chặn theo từng loại proxy.
- Giải thích vì sao phương thức public có trên implementation nhưng **không khai báo trong interface** không có trên JDK proxy.
- Giải thích điều kiện để class-based CGLIB proxy chặn được protected và package-visible.
- Nhận diện private, final, static, package-visible không thể override trong cây kế thừa và lời gọi nội bộ bỏ qua proxy.
- Viết, chạy và đối chiếu một ví dụ Spring Framework thuần có cả JDK proxy và CGLIB proxy.
- Tránh nhầm quy tắc của **Spring AOP @AspectJ advice** với một số quy tắc riêng của **@Transactional**.

**Phạm vi:** Chỉ xét **proxy-based Spring AOP**, Spring-managed service bean và advice khai báo bằng **@Aspect**. Những kiến thức AOP tổng quát hoặc AspectJ weaving không phải nội dung chính.

## 2. Hai kiểu Spring AOP proxy

Spring AOP lựa chọn một trong hai cơ chế tạo proxy cho target được quản lý:

1. **JDK dynamic proxy**: proxy triển khai interface, chặn những lời gọi **public interface methods** đã được công bố qua proxy. Với cấu hình Spring Framework thuần mặc định, đây là kiểu thường dùng khi target triển khai ít nhất một interface thích hợp.
2. **CGLIB proxy**: proxy là một subclass tạo động của target class. Cơ chế này có thể chặn các method mà subclass **có quyền override** và caller **thật sự gọi thông qua proxy**. Có thể chọn kiểu này khi không có interface hoặc chủ động bật **proxyTargetClass = true**.

Ví dụ:

~~~text
                        Spring ApplicationContext
                                   |
                       target: InventoryService
                                   |
                  +----------------+----------------+
                  |                                 |
              JDK proxy                         CGLIB proxy
        implements InventoryApi          extends InventoryService
                  |                                 |
         public interface API             public, protected,
                                         package-visible hợp lệ
                  |                                 |
                  +----------- client --------------+
                                  |
                        advice -> target
~~~

Sơ đồ thể hiện **điểm có thể intercept**, không có nghĩa CGLIB tự chặn mọi Java call trong target. **Self-invocation** vẫn đi thẳng tới target.

## 3. Bảng quy tắc visibility

| Loại phương thức | JDK dynamic proxy | CGLIB class-based proxy |
| --- | --- | --- |
| **public** và được khai báo trong interface mà proxy triển khai | Có thể chặn qua interface proxy | Có thể chặn qua class proxy |
| **public** chỉ có trên implementation, không nằm trong interface proxy | Không được expose qua JDK interface proxy | Có thể chặn nếu không final và được gọi qua proxy |
| **protected** | Không có trên JDK interface proxy | Có thể chặn nếu overridable và caller truy cập hợp lệ qua proxy |
| **package-visible** khai báo trong chính target class, proxy subclass ở cùng package | Không có trên JDK interface proxy | Có thể chặn khi Java visibility cho phép override |
| **package-visible** kế thừa từ superclass thuộc **package khác** | Không có trên JDK interface proxy | Không thể advise theo cách subclass thông thường vì phương thức **không visible để override** |
| **private** | Không thể expose qua interface như một instance business method | Không thể override, nên không advise được |
| **final instance method** | Nếu public và có trên interface, JDK proxy vẫn có thể chặn **lời gọi qua interface proxy** | Không override được, nên không advise được bằng CGLIB |
| **static method** | Không phải lời gọi instance đi qua JDK proxy | Không phải method instance override của proxy; Spring AOP không chặn lời gọi static |
| **constructor** | Không phải method-execution call trên proxy | Spring AOP proxy không áp dụng advice lên constructor execution |

**“Có thể chặn” không có nghĩa chắc chắn được chặn.** Spring còn cần có advisor khớp với method, bean phải thực sự được expose qua proxy và caller phải gọi trên proxy. Các method bị loại theo visibility/finality không trở thành interceptable chỉ nhờ biểu thức pointcut trông có vẻ khớp.

Một điểm tinh tế: **final** ở implementation không nhất thiết ngăn **JDK dynamic proxy** chặn lời gọi **public interface method**. JDK proxy xử lý lời gọi ở interface phía ngoài target, không subclass/override phương thức final của implementation. CGLIB thì cần override trên subclass nên chịu ràng buộc final.

## 4. Public qua interface: phạm vi chuẩn của JDK proxy

Xét:

~~~java
package demo;

public interface InventoryApi {
    void check();
}
~~~

Và implementation:

~~~java
package demo;

public class InventoryService implements InventoryApi {

    @Override
    public void check() {
        System.out.println("TARGET check");
    }

    public void reindex() {
        System.out.println("TARGET reindex");
    }
}
~~~

Nếu Spring tạo JDK proxy, mã client thường nhận:

~~~java
InventoryApi service = context.getBean(InventoryApi.class);
service.check();
~~~

Lời gọi **check()** có thể đi qua advice; nhưng **service.reindex()** không biên dịch vì kiểu **InventoryApi** không có method này. Ngay cả khi target thật có public **reindex()**, một JDK proxy chỉ triển khai interface được proxy không tự xuất toàn bộ method của implementation.

Không sửa bằng cách ép kiểu:

~~~java
InventoryService concrete = (InventoryService) service;
~~~

Với một JDK dynamic proxy điển hình, phép cast đó dẫn đến **ClassCastException** vì proxy **không phải subclass InventoryService**. Nếu cần gọi **reindex()** qua interface proxy, hãy thiết kế một interface phù hợp; nếu chủ đích cần proxy các method chỉ có trên class, cân nhắc class-based proxy và những giới hạn tương ứng.

**Một public method có trong interface vẫn cần lời gọi đi qua proxy.** Nếu từ bên trong InventoryService có **this.check()**, Spring AOP thông thường không advice lời gọi nội bộ ấy như một invocation riêng.

## 5. CGLIB và khả năng override phương thức

Class-based proxy dùng một subclass runtime của target để intercept. Vì thế nó chịu các quy tắc override của Java:

- **public**: thường có thể override khi không final, và gọi ngoài proxy thuận tiện.
- **protected**: có thể override; từ code ở cùng package hoặc subclass phù hợp, có thể gọi qua proxy và được chặn.
- **package-visible**: có thể override nếu method thật sự visible từ subclass. Trường hợp target và proxy được định nghĩa trong cùng package cho phép; nếu method ở superclass package khác, nó có thể **effectively private** đối với subclass ở package target.
- **private**: không được thừa kế như method overridable; CGLIB không thể override.
- **final**: không cho subclass override; class **final** không thể tạo CGLIB subclass.
- **static**: là lời gọi liên kết với class, không được override như instance method.

Lưu ý thêm: muốn gọi một **protected** hay **package-visible** method của bean từ một class bên ngoài, code gọi vẫn phải thỏa **quy tắc Java access control**. Việc Spring dùng CGLIB không khiến protected hoặc package-visible trở thành public. Do đó ví dụ runnable bên dưới để class **App** và service **cùng package demo**.

Một câu giải thích dễ nhớ: **JDK proxy giới hạn theo interface được expose; CGLIB giới hạn theo khả năng subclass và override.** Cả hai còn bị ràng buộc bởi đường đi lời gọi.

## 6. Ví dụ Maven chạy được: cùng service, hai kiểu proxy

Ta tạo hai **ApplicationContext độc lập**. Context thứ nhất bật JDK proxy, context thứ hai bật CGLIB. Cả hai đều dùng cùng class **VisibilityService**, cùng pointcut, cùng cấu trúc service. Vì tạo hai context độc lập, không phải thay đổi annotation giữa chừng hoặc trộn hai chế độ proxy trong một container.

Yêu cầu: **Java 17+**, **Maven 3.9+**, **Spring Framework 6.2.12**. Đây là ứng dụng console Spring thuần, không cần Spring Boot.

~~~text
spring-aop-method-visibility/
├── pom.xml
└── src/main/java/demo/
    ├── ServiceApi.java
    ├── VisibilityService.java
    ├── VisibilityAspect.java
    └── App.java
~~~

### 6.1. pom.xml

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-method-visibility</artifactId>
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

**spring-context** mang theo các module core container và Spring AOP cần thiết. **aspectjweaver** cung cấp các kiểu AspectJ annotation và pointcut expression mà Spring AOP sử dụng; ví dụ này vẫn **proxy-based**, không dùng compile-time hay load-time weaving.

### 6.2. Interface chỉ công bố public methods

**src/main/java/demo/ServiceApi.java**

~~~java
package demo;

public interface ServiceApi {
    void publicWork();
    void internalFlow();
}
~~~

Interface Java **public** chỉ công bố **publicWork()** và **internalFlow()** cho client JDK proxy. Interface không chứa protected hoặc package-visible abstract instance methods theo nghĩa hợp đồng API. Ngoài ra, không có **protectedWork()** hay **packageWork()** trên interface.

### 6.3. Service chứa đủ các mức visibility

**src/main/java/demo/VisibilityService.java**

~~~java
package demo;

public class VisibilityService implements ServiceApi {

    @Override
    public void publicWork() {
        System.out.println("TARGET publicWork");
    }

    public void implementationOnly() {
        System.out.println("TARGET implementationOnly");
    }

    protected void protectedWork() {
        System.out.println("TARGET protectedWork");
    }

    void packageWork() {
        System.out.println("TARGET packageWork");
    }

    private void privateWork() {
        System.out.println("TARGET privateWork");
    }

    @Override
    public void internalFlow() {
        System.out.println("TARGET internalFlow");
        this.protectedWork();
        this.packageWork();
        this.privateWork();
    }
}
~~~

Ở đây:

- **publicWork()**: public và nằm trong **ServiceApi**.
- **implementationOnly()**: public nhưng không nằm trong interface.
- **protectedWork()**: protected, callable từ **App** trong cùng package demo.
- **packageWork()**: package-visible do không có từ khóa access modifier, callable từ **App** trong cùng package.
- **privateWork()**: private, chỉ gọi trong class; không thể gọi trực tiếp trên Spring proxy từ App.
- **internalFlow()**: public và nằm trong interface, nhưng gọi protected, package-visible và private method bằng **this**. Cả ba lời gọi nội bộ này không tự đi qua proxy.

### 6.4. Aspect ghi nhận tên method trước khi vào target

**src/main/java/demo/VisibilityAspect.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class VisibilityAspect {

    @Before("execution(* demo.VisibilityService.*(..))")
    public void before(JoinPoint joinPoint) {
        System.out.println(
                "ADVICE " + joinPoint.getSignature().getName());
    }
}
~~~

**@Before** được Spring AOP gọi khi một **method execution** khớp pointcut và invocation đi qua proxy. Mẫu **execution(* demo.VisibilityService.*(..))** cố ý không ghi từ khóa public, vì bài muốn kiểm tra cả protected và package-visible.

Biểu thức pointcut có thể biểu diễn tên một method nhưng **không mở rộng khả năng kỹ thuật của proxy**. Nếu CGLIB không override được một method, hoặc method được gọi trực tiếp qua **this**, advice sẽ không chạy cho lời gọi đó.

Aspect được đăng ký bằng phương thức **@Bean** trong cấu hình nên không cần **@Component**; chỉ gắn **@Aspect** mà không đăng ký như Spring bean sẽ không đủ.

### 6.5. Chạy hai ApplicationContext độc lập

**src/main/java/demo/App.java**

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class App {

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class JdkConfig {

        @Bean
        public ServiceApi service() {
            return new VisibilityService();
        }

        @Bean
        public VisibilityAspect visibilityAspect() {
            return new VisibilityAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CglibConfig {

        @Bean
        public VisibilityService service() {
            return new VisibilityService();
        }

        @Bean
        public VisibilityAspect visibilityAspect() {
            return new VisibilityAspect();
        }
    }

    public static void main(String[] args) {
        try (var context =
                     new AnnotationConfigApplicationContext(JdkConfig.class)) {

            ServiceApi service = context.getBean(ServiceApi.class);

            System.out.println("=== JDK ===");
            System.out.println(
                    "JDK_PROXY " + AopUtils.isJdkDynamicProxy(service));

            service.publicWork();
            service.internalFlow();
        }

        try (var context =
                     new AnnotationConfigApplicationContext(CglibConfig.class)) {

            VisibilityService service =
                    context.getBean(VisibilityService.class);

            System.out.println("=== CGLIB ===");
            System.out.println(
                    "CGLIB_PROXY " + AopUtils.isCglibProxy(service));

            service.publicWork();
            service.implementationOnly();
            service.protectedWork();
            service.packageWork();
            service.internalFlow();
        }
    }
}
~~~

Giải thích cấu hình:

1. **JdkConfig**: trả bean dưới hợp đồng **ServiceApi**, đồng thời **@EnableAspectJAutoProxy** mặc định ưu tiên JDK dynamic proxy khi target có interface. Client lấy service bằng interface.
2. **CglibConfig**: bật **@EnableAspectJAutoProxy(proxyTargetClass = true)**, chủ động chọn class-based proxy. Client lấy bằng **VisibilityService.class** nên truy cập được protected/package-visible trong cùng package.
3. Hai context cùng đăng ký **VisibilityAspect** nhưng **mỗi context có instance riêng**, không phải hai aspect bean trong một context.
4. **@Bean** tạo **VisibilityService** làm Spring-managed bean. Lời gọi qua bean do container trả về mới có đường đi Spring AOP proxy.
5. **@Configuration(proxyBeanMethods = false)** chỉ xử lý việc interceptor các phương thức **@Bean** trong class cấu hình; nó **không hủy chế độ AOP** cho service, vì **@EnableAspectJAutoProxy** vẫn được bật.
6. **@Before** là advice đơn giản, có đầu ra in theo tên method để thấy chính xác method nào được chặn.

Trong project **Spring Boot**, cấu hình chọn loại proxy có thể bị Boot AOP auto-configuration tác động; ví dụ này là **Spring Framework thuần** để so sánh chính xác hai chế độ.

## 7. Chạy và đọc stdout

Trong thư mục dự án chứa pom.xml, chạy:

~~~bash
mvn -q clean compile exec:java
~~~

Đầu ra logic kỳ vọng, bỏ qua thông điệp Maven và logging của framework:

~~~text
=== JDK ===
JDK_PROXY true
ADVICE publicWork
TARGET publicWork
ADVICE internalFlow
TARGET internalFlow
TARGET protectedWork
TARGET packageWork
TARGET privateWork
=== CGLIB ===
CGLIB_PROXY true
ADVICE publicWork
TARGET publicWork
ADVICE implementationOnly
TARGET implementationOnly
ADVICE protectedWork
TARGET protectedWork
ADVICE packageWork
TARGET packageWork
ADVICE internalFlow
TARGET internalFlow
TARGET protectedWork
TARGET packageWork
TARGET privateWork
~~~

### 7.1. Quan sát JDK

- **JDK_PROXY true** cho thấy Spring trả về JDK dynamic proxy.
- **publicWork()** được khai báo trên interface, vì vậy client gọi qua proxy và advice xuất hiện trước target.
- **internalFlow()** cũng là public interface method nên lời gọi bên ngoài được advice.
- Bên trong **internalFlow()**, các method protected/package-visible/private được gọi trên **this** của target; không có các dòng **ADVICE protectedWork**, **ADVICE packageWork** hoặc **ADVICE privateWork**.
- **implementationOnly()** không thể gọi từ biến **ServiceApi**. Điểm này là giới hạn API của JDK proxy, không phải lỗi pointcut.

### 7.2. Quan sát CGLIB

- **CGLIB_PROXY true** cho thấy Spring trả về class-based proxy của VisibilityService.
- **publicWork()** được chặn tương tự JDK.
- **implementationOnly()** được chặn khi gọi từ App qua class proxy, dù method không có trên interface.
- **protectedWork()** và **packageWork()** được chặn vì có thể override trong proxy subclass và App ở cùng package **demo** gọi trực tiếp trên proxy.
- **internalFlow()** được chặn ở lời gọi bên ngoài. Tuy nhiên, ba lời gọi **this.protectedWork()**, **this.packageWork()**, **this.privateWork()** bên trong target vẫn không qua proxy, nên chỉ có dòng **TARGET**.

**Điểm mấu chốt:** “CGLIB có thể advise protected và package-visible” là phát biểu về **khả năng intercept lời gọi qua proxy**, không có nghĩa mọi lần thực thi các method đó trên target đều tạo một lần advice.

## 8. Vì sao gọi internalFlow() không làm ba advice bên trong chạy?

Spring AOP sử dụng proxy để chặn lời gọi từ bên ngoài:

~~~text
App
 |
 v
CGLIB proxy
 |
 +-- advice: internalFlow
 |
 v
VisibilityService.internalFlow()        <- target
 |
 +-- this.protectedWork() ------------- trực tiếp vào target
 +-- this.packageWork() --------------- trực tiếp vào target
 +-- this.privateWork() --------------- trực tiếp vào target
~~~

Từ khóa **this** trong thân **VisibilityService** tham chiếu object target đang chạy; các lời gọi nội bộ không quay trở lại proxy.

Cả hai câu dưới đây đều có thể đúng đồng thời:

- **App gọi service.protectedWork() qua class-based proxy** → advice chạy.
- **internalFlow() gọi this.protectedWork() bên trong target** → advice cho protectedWork không chạy.

Muốn kiểm tra chính xác đường đi, phải biết **đối tượng nào được caller giữ**. Đừng chỉ nhìn visibility hoặc annotation trên method.

Cách thiết kế dễ kiểm soát khi một thao tác cần advice là **đặt thao tác đó ở một Spring bean khác** và gọi qua dependency được inject. Không nên tìm cách vá self-invocation bằng cast hoặc tự tạo instance target.

## 9. Package-visible: trường hợp cùng package và khác package

Phương thức package-visible không có từ khóa **public/protected/private**:

~~~java
package demo;

public class LocalService {
    void refreshCache() {
        System.out.println("refresh");
    }
}
~~~

Trong cùng package demo, một Spring CGLIB subclass có thể override **refreshCache()** nếu không final; khi một caller hợp lệ ở cùng package gọi **qua proxy**, Spring AOP có thể chạy advice.

Nhưng xét inheritance qua hai package:

~~~text
src/main/java/
├── base/
│   └── BaseService.java     // package base
└── demo/
    └── DerivedService.java  // package demo, extends BaseService
~~~

**base/BaseService.java**

~~~java
package base;

public class BaseService {
    void hiddenOperation() {
        System.out.println("BASE hiddenOperation");
    }
}
~~~

**demo/DerivedService.java**

~~~java
package demo;

import base.BaseService;

public class DerivedService extends BaseService {
    public void run() {
        System.out.println("DERIVED run");
    }
}
~~~

**hiddenOperation()** là package-visible của **base**, không visible cho subclass ở package **demo**. Nó không thể được subclass CGLIB ở package demo override theo cách thông thường; đối với tình huống này, nó **effectively private** từ góc nhìn subclass. Vì vậy không được kết luận “mọi package-private method đều được CGLIB chặn”.

Hai file ở mục này là **ví dụ minh họa Java visibility**, chưa gắn với class App hoặc cấu hình Spring ở mục 6, và không thể gọi hiddenOperation trực tiếp từ DerivedService ở package demo.

Nếu nghiệp vụ thực sự cần lời gọi từ bên ngoài đi qua advice, nên dùng public method là điểm vào rõ ràng. Protected/package-visible có giá trị ở phạm vi hẹp nhưng đòi hỏi cả Java access control lẫn khả năng proxy override.

## 10. Những giới hạn không phải do mức visibility

Khi so sánh public/protected/package-visible, cần kiểm tra thêm **khả năng override** và **đường gọi**:

- CGLIB không thể tạo subclass của `final class`, không thể override `final`/`private` instance methods; các method này không được advice qua class-based proxy. Một method `public` vẫn có thể không được advice nếu nó `final`.
- JDK proxy lại có thể chặn một public interface method dù phần implementation được khai báo `final`, vì việc chặn diễn ra ở interface boundary chứ không cần override target method.
- `static` methods không dispatch qua instance proxy; constructor cũng không phải method-execution join point của Spring AOP.
- Ngay cả method public có thể override vẫn bỏ qua advice khi target tự gọi `this.method()` thay vì client gọi qua proxy.

Những điều kiện trên độc lập với việc caller có quyền truy cập protected/package-visible hay không. Muốn xác nhận một lời gọi được advice, hãy kiểm tra đầy đủ loại proxy, khả năng override, pointcut và đường đi của invocation.

## 11. Pointcut có thể giới hạn public, nhưng không ép protected thành public

Trong aspect ở mục 6:

~~~java
@Before("execution(* demo.VisibilityService.*(..))")
public void before(JoinPoint joinPoint) {
    // ...
}
~~~

Biểu thức **execution** không chỉ định modifier, vì thế về mặt pointcut nó có thể mô tả nhiều mức visibility. **Proxy vẫn là giới hạn thực thi**.

Nếu muốn chỉ bắt các public method:

~~~java
@Before("execution(public * demo.VisibilityService.*(..))")
public void beforePublic(JoinPoint joinPoint) {
    // ...
}
~~~

Đây là **biến thể pointcut minh họa**; khi thử, thay annotation/method trong aspect hiện tại hoặc tạo context thử riêng, tránh vừa giữ advice cũ vừa thêm advice mới rồi nhầm số log.

Khi chạy với CGLIB:

- publicWork, implementationOnly và internalFlow có thể khớp **public**.
- protectedWork và packageWork **không** khớp biểu thức có modifier **public**.
- privateWork vẫn không thể được Spring AOP proxy advise dù bạn viết một pointcut có tên của nó.

Trong AspectJ pointcut syntax, **visibility của phương thức @Pointcut được dùng để tái sử dụng biểu thức** và **visibility của target method trong execution()** là hai việc khác nhau. Việc đặt method khai báo **@Pointcut** là private không tự thu hẹp những join point mà biểu thức của pointcut có thể match.

## 12. Phân biệt quy tắc kỹ thuật của AOP với @Transactional

Spring thường dùng các proxy tương tự cho **@Transactional**, **@Async** và một số tính năng khác. Tuy vậy, mỗi tính năng còn có **quy tắc chọn method và metadata riêng**. Không được suy ra chỉ từ cơ chế CGLIB rằng một annotation trên mọi method không-public sẽ luôn có hiệu lực.

Ví dụ với **Spring Framework 6+**, hỗ trợ transaction annotation có thể xử lý **protected** và **package-visible** trên **class-based proxy** theo mặc định; với **interface-based proxy**, method transaction phải **public và công bố trên interface**. Cấu hình transaction còn phụ thuộc **@EnableTransactionManagement**, transaction manager và các quy tắc nhận diện transaction metadata.

Đối với **Spring AOP @AspectJ @Before** trong bài này, điểm cốt lõi là Spring đánh giá **execution pointcut** trên method invocation có thể intercept. **@Transactional** là một ví dụ khác sử dụng proxy nhưng có thêm lớp quy tắc riêng. Khi kiểm thử một tính năng cụ thể, cần đọc tài liệu của tính năng đó và không đồng nhất “có proxy” với “đã áp dụng transaction/advice”.

## 13. CGLIB không tự mở rộng quyền truy cập của caller

Giả sử **App** chuyển sang package khác (ví dụ **client**) và không kế thừa VisibilityService, Java không cho biên dịch lời gọi:

~~~java
service.protectedWork();
service.packageWork();
~~~

Kể cả nếu **service** là class-based proxy, source code bên ngoài package vẫn phải tuân thủ quy tắc truy cập Java. Việc Spring bọc bean không biến protected/package-visible thành API công khai.

Do đó, trong thiết kế ứng dụng lớn:

- Public service method thường là contract dễ dùng và dễ test nhất.
- Protected thường dành cho subclass extension hooks; gọi qua proxy cần đúng ngữ cảnh Java.
- Package-visible hợp lý cho nội bộ package; không phải API công bố giữa module/package.
- Nếu muốn phương thức được áp dụng advice từ module khác, cân nhắc tạo một public entry point trên Spring-managed bean, với pointcut và dependency rõ ràng.

## 14. Trường hợp method không nằm trong interface JDK proxy

Trong **VisibilityService** có public **implementationOnly()**, nhưng **ServiceApi** không khai báo method này. Dưới JDK proxy:

~~~java
ServiceApi proxy = context.getBean(ServiceApi.class);
proxy.publicWork();       // Hợp lệ
// proxy.implementationOnly(); // Không biên dịch
~~~

Hai hướng thiết kế:

1. **Bổ sung method vào một interface phù hợp** nếu nó thực sự thuộc hợp đồng nghiệp vụ cần expose. Khi đó JDK proxy có thể công bố method.
2. **Chọn CGLIB** nếu cần expose các public method của implementation mà không qua interface, đồng thời chấp nhận ràng buộc inheritance và final/private.

Không dùng **ProxyTargetClass** chỉ để sửa một phép cast mà không cân nhắc thiết kế API. Nếu cùng một bean được người dùng khác inject theo interface, thay đổi từ JDK sang CGLIB có thể ảnh hưởng đến các phép kiểm tra kiểu, pointcut và behavior phụ thuộc class.

## 15. Những hiểu lầm thường gặp trong Spring AOP

| Nhận định | Đánh giá đúng |
| --- | --- |
| “Spring AOP chỉ chặn public method trong mọi trường hợp.” | Sai về mặt tổng quát: **CGLIB** có thể chặn protected/package-visible nếu phương thức overridable, caller hợp lệ và đi qua proxy |
| “Dùng @Aspect có thể chặn mọi private method.” | Sai: Spring AOP proxy không override private method |
| “CGLIB chặn protected bất kể ai gọi.” | Sai: lời gọi phải đi qua proxy; Java visibility vẫn áp dụng |
| “Package-private được chặn dù method khai báo ở superclass package khác.” | Sai: nếu không visible để override từ proxy subclass, method effectively private |
| “Phương thức final luôn không thể được advice ở mọi loại proxy.” | Sai: final chặn **CGLIB override**; JDK interface proxy vẫn có thể advice public interface call |
| “Method là public trong implementation nên JDK proxy đều thấy.” | Sai: JDK proxy expose những interface methods của proxy, không phải toàn bộ implementation |
| “CGLIB chặn cả lệnh this.protectedWork().” | Sai: self-invocation trên target không qua proxy |
| “@EnableAspectJAutoProxy làm service tự được đăng ký bean.” | Sai: vẫn cần component scan, @Bean hoặc cơ chế đăng ký bean khác |
| “AopUtils.isAopProxy(bean) = true chứng tỏ method này được advice.” | Chưa đủ: cần xác minh pointcut và lời gọi thực tế |
| “Protected method có thể gọi từ class ở package bất kỳ nếu đã bật proxyTargetClass.” | Sai: Java access control không đổi |
| “Thêm public vào execution pointcut sẽ chặn protected.” | Sai: modifier public làm pointcut hẹp lại |
| “Chọn CGLIB sẽ khắc phục mọi lỗi thiếu transaction hoặc advice.” | Sai: có thể do đăng ký bean, pointcut, metadata hay self-invocation |

## 16. Quy trình điều tra khi thiếu advice

Thực hiện theo thứ tự dưới đây để tránh suy đoán:

### Bước 1 — Xác nhận bean lấy từ ApplicationContext

~~~java
Object service = context.getBean("service");
System.out.println(
        org.springframework.aop.support.AopUtils.isAopProxy(service));
~~~

Nếu tự **new VisibilityService()** rồi gọi trực tiếp, Spring AOP không tự chặn instance đó. Đoạn mã là fragment debug cần có biến **context** hợp lệ.

### Bước 2 — Xác định loại proxy

~~~java
System.out.println(
        org.springframework.aop.support.AopUtils.isJdkDynamicProxy(service));
System.out.println(
        org.springframework.aop.support.AopUtils.isCglibProxy(service));
~~~

Nếu JDK proxy, xác minh phương thức muốn gọi **có trên interface được expose**. Nếu CGLIB proxy, xác minh method có thể override và caller truy cập hợp lệ.

### Bước 3 — Kiểm tra pointcut

Ví dụ wildcard **execution(* demo.VisibilityService.*(..))** có thể match public/protected/package-visible phù hợp. Nếu pointcut ghi **execution(public ...)**, protected/package-visible bị loại theo biểu thức.

Ngoài ra, package/class trong pointcut phải khớp service thật. Đừng đoán rằng advice chạy khi chỉ thay visibility.

### Bước 4 — Kiểm tra đường gọi

Đặt log tại **@Before**, rồi so với log trong target. Nếu log advice chỉ xuất hiện khi gọi từ App nhưng thiếu khi target gọi **this.method()**, đó là self-invocation, không phải lỗi Spring không nhìn thấy method.

### Bước 5 — Xem xét final, private và cây kế thừa

- Class final không tạo CGLIB subclass.
- Method final hoặc private không override được.
- Package-visible method ở superclass thuộc package khác có thể không visible đối với proxy subclass.
- Nếu method được gọi theo kiểu static, nó không qua instance proxy.

### Bước 6 — Xác minh cấu hình

Có thể dùng:

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = true)
~~~

để yêu cầu class-based proxy trong context tương ứng. Đồng thời phải đăng ký **@Aspect** như Spring bean qua **@Component** hoặc **@Bean**. Annotation trên class không đủ nếu bean không nằm trong ApplicationContext.

## 17. Một số bài tập kiểm chứng

**Bài tập A — Chỉ public qua CGLIB.** Thay pointcut bằng **execution(public * demo.VisibilityService.*(..))** và chạy lại context CGLIB. Quan sát advice chỉ xuất hiện trước những public methods được gọi qua proxy; protected/package-visible vẫn in dòng target nhưng không có advice của pointcut public-only.

**Bài tập B — Thêm public implementation-only vào interface.** Thêm **void implementationOnly()** vào ServiceApi, rồi gọi nó ở phần JDK. Quan sát JDK proxy bây giờ công bố method qua interface và có thể chạy advice cho lời gọi đó.

**Bài tập C — Biến protectedWork thành final.** Không thể khai báo đồng thời một method final trong base và override nó ở subclass; CGLIB không advice cho protectedWork bằng cách override. So sánh việc chặn **publicWork()** vẫn hoạt động. Đây là bài tập thay đổi source rồi chạy lại, không phải thay đổi nên áp dụng vào service production.

**Bài tập D — Dời App sang package khác.** Thử gọi protectedWork/packageWork trong App ở package khác để thấy lỗi biên dịch do Java visibility. CGLIB không thay thế quy tắc access control.

**Bài tập E — Gọi lại từ internalFlow().** Đếm số dòng advice trước protectedWork hoặc packageWork khi gọi trực tiếp qua proxy, rồi so với gọi nội bộ. Giải thích sự khác biệt theo đường đi của invocation.

## 18. Các câu hỏi tự đánh giá

**1. JDK dynamic proxy có thể advise một public method nằm trong implementation nhưng không thuộc interface được proxy không?**

Không theo đường gọi qua JDK interface proxy đó: proxy không expose method implementation-only. Nếu muốn gọi qua proxy, cần thay API interface hoặc chọn class-based proxy.

**2. CGLIB có thể advise một protected method không?**

Có, khi method có thể override, nằm trong vùng visible của proxy subclass, pointcut phù hợp và caller thật sự gọi qua proxy trong ngữ cảnh Java hợp lệ.

**3. Package-visible nào không thể được CGLIB advise dù cùng tên method?**

Một ví dụ là package-visible method khai báo trong superclass ở **package khác**, khiến proxy subclass không thể override vì method effectively private từ góc nhìn subclass.

**4. Tại sao @Before xuất hiện khi App gọi protectedWork qua proxy nhưng không xuất hiện khi internalFlow gọi this.protectedWork?**

Vì App gọi qua proxy; lời gọi **this** trong target là lời gọi nội bộ trực tiếp, không quay lại proxy.

**5. Một final public method triển khai interface có luôn nằm ngoài Spring AOP không?**

Không. JDK interface proxy có thể advice public interface call dù implementation method final. Giới hạn final trực tiếp ngăn cơ chế override của CGLIB.

**6. Đặt pointcut rộng trên cả class có làm private method trở thành join point được proxy chặn không?**

Không. Việc biểu thức có thể ghi tên method không vượt qua hạn chế override hoặc cấu trúc lời gọi proxy của Spring AOP.

**7. Nếu service bị proxy nhưng không có advice ở package-visible method, cần kiểm tra điều gì?**

Xác định proxy là JDK hay CGLIB, method có visible và overridable không, caller gọi qua proxy hay **this**, và pointcut có match hay không.

**8. Để xây API Spring service ổn định giữa JDK và CGLIB nên làm gì?**

Ưu tiên các **public service methods** có hợp đồng rõ ràng; nếu theo interface, khai báo method cần chặn trong interface. Chỉ dựa vào protected/package-visible interception khi thực sự cần và đã có kiểm thử cho kiểu proxy đang sử dụng.

## 19. Tài liệu Spring chính thức

- Spring Framework 6.2 — **Proxying Mechanisms** (JDK proxy, CGLIB, final/private/effectively-private và self-invocation): https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html
- Spring Framework — **Declaring a Pointcut** (public interface methods, public/protected/package-visible trên CGLIB và execution modifier): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework — **AOP Proxies**: https://docs.spring.io/spring-framework/reference/core/aop/introduction-proxies.html
- Spring Framework — **@AspectJ Support**: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
- Spring Framework — **Using @Transactional** (quy tắc visibility riêng của transaction annotation trong proxy mode): https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html

**Kết luận kỹ thuật:** Phạm vi Spring AOP được quyết định bởi **loại proxy + khả năng expose/override method + pointcut + đường đi lời gọi**. Với **JDK proxy**, client dùng các public interface methods. Với **CGLIB**, lời gọi qua proxy có thể được advice trên public, protected và package-visible methods khi Java cho phép override và truy cập; private/final/static hoặc self-invocation không thể được xử lý theo cùng cách proxy đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-008">Kiểu tham chiếu khi tiêm phụ thuộc qua interface hoặc lớp cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu tham chiếu khi tiêm phụ thuộc qua interface hoặc lớp cụ thể trong Spring AOP

## 1. Tại sao Spring AOP ảnh hưởng đến kiểu được inject?

Một ứng dụng có thể khai báo `PaymentServiceImpl implements PaymentService` và tiêm dependency ở hai cách:

```java
private final PaymentService paymentService;         // theo interface
private final PaymentServiceImpl paymentServiceImpl; // theo lớp triển khai
```

Khi **Spring AOP auto-proxy** áp dụng advice cho bean ấy, object container cung cấp cho các bean khác có thể **không còn là instance trực tiếp của `PaymentServiceImpl`**. Tùy chiến lược proxy:

- **JDK dynamic proxy:** object proxy implements các interface được expose, ví dụ `PaymentService`, nhưng **không extends `PaymentServiceImpl`**. Inject theo interface phù hợp; yêu cầu ép kiểu hoặc inject theo implementation class có thể thất bại.
- **CGLIB class-based proxy:** object proxy là **subclass** của `PaymentServiceImpl`. Vì vậy proxy có thể được gán cho cả interface `PaymentService` và concrete type `PaymentServiceImpl`, miễn là việc tạo proxy subclass hợp lệ.

Bài này tập trung vào **Spring AOP và dependency injection**: chọn kiểu constructor parameter/field, ảnh hưởng của cấu hình `proxyTargetClass`, biểu hiện khi runtime, và cách debug. Lý thuyết Aspect chung không phải trọng tâm.

## 2. Minh họa đối tượng thật và object được Spring cung cấp

Giả sử:

```java
public interface PaymentService {
    void pay();
}

public class PaymentServiceImpl implements PaymentService {
    @Override
    public void pay() {
        // xử lý thanh toán
    }
}
```

Hai hình thức Spring có thể tạo khi có advisor khớp:

```text
                      Spring IoC container
                              |
                  target: PaymentServiceImpl
                              |
           +------------------+------------------+
           |                                     |
      JDK proxy                              CGLIB proxy
   implements PaymentService          extends PaymentServiceImpl
           |                                     |
     instanceof PaymentService: true       instanceof PaymentService: true
     instanceof PaymentServiceImpl: false  instanceof PaymentServiceImpl: true
           |                                     |
        advice -> target.pay()                  advice -> target.pay()
```

Hai cột đều là **Spring AOP proxy** nếu được tạo bởi Spring AOP. Điều khác biệt quan trọng với dependency injection là **các kiểu mà object proxy có thể được gán cho**.

| Phép thử đối với object được trả từ context | JDK proxy của `PaymentService` | CGLIB proxy của `PaymentServiceImpl` |
| --- | --- | --- |
| `proxy instanceof PaymentService` | `true` | `true` |
| `proxy instanceof PaymentServiceImpl` | `false` | `true` |
| `AopUtils.isAopProxy(proxy)` | `true` | `true` |
| `AopUtils.isJdkDynamicProxy(proxy)` | `true` | `false` |
| `AopUtils.isCglibProxy(proxy)` | `false` | `true` |
| Có thể tham chiếu theo interface `PaymentService` | Có | Có |
| Có thể tham chiếu theo concrete `PaymentServiceImpl` | **Không** bằng chính JDK proxy ấy | Có, nếu class proxy được tạo thành công |

Bảng giả định AOP proxy đã được tạo cho đúng bean, không có các proxy lồng nhau hay bean replacement bất thường. **Không phải mọi Spring bean đều có AOP proxy:** nếu không có advisor áp dụng, Spring có thể trả target gốc; khi đó dependency injection theo concrete class vẫn có thể hoạt động dù dự án cấu hình `proxyTargetClass=false`.

## 3. JDK dynamic proxy: tiêm qua interface

### 3.1. Cách Spring chọn JDK proxy

Trong cấu hình Spring AOP điển hình, nếu target implements interface và **không bị ép tạo class-based proxy**, Spring có thể dùng JDK dynamic proxy. Proxy này triển khai **business interface** cùng một số interface nội bộ Spring, nhưng không kế thừa implementation class.

Ví dụ constructor injection được khuyến nghị:

```java
@Service
public class CheckoutController {
    private final PaymentService paymentService;

    public CheckoutController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
}
```

Đây là **đoạn minh họa**, giả định `PaymentService` và bean triển khai đã được đăng ký, `@Service` được component scan và đúng một bean `PaymentService` có thể tiêm.

Khi Spring cung cấp JDK proxy cho bean triển khai, nó vẫn là một `PaymentService`. Code gọi `paymentService.pay()` thông qua tham chiếu interface, giúp lời gọi đi qua proxy và advisor tương ứng.

**Không nên làm:**

```java
PaymentServiceImpl impl =
    (PaymentServiceImpl) context.getBean("paymentService");
```

Nếu bean được bọc bằng JDK proxy, phép cast này có thể ném `ClassCastException`. `getBean("paymentService", PaymentServiceImpl.class)` có thể ném `BeanNotOfRequiredTypeException` vì kiểu **object proxy được trả ra** không tương thích với `PaymentServiceImpl`.

### 3.2. Điều gì xảy ra khi inject trực tiếp implementation?

```java
@Service
public class CheckoutController {
    private final PaymentServiceImpl implementation;

    public CheckoutController(PaymentServiceImpl implementation) {
        this.implementation = implementation;
    }
}
```

Nếu Spring cố cung cấp **JDK proxy** cho bean `PaymentServiceImpl`, dependency trên yêu cầu **concrete type** không được chính JDK proxy đáp ứng. Việc tạo application context có thể thất bại với lỗi dependency resolution, chẳng hạn `UnsatisfiedDependencyException`, `BeanNotOfRequiredTypeException`, hoặc một exception chứa nguyên nhân kiểu không tương thích.

Lỗi chi tiết phụ thuộc cách khai báo bean, thời điểm resolve dependency và phiên bản Spring. Không nên dự đoán duy nhất một exception cho mọi dự án; bản chất lỗi là **proxy không assignable cho kiểu được yêu cầu**.

**Sửa đúng tại hợp đồng API**: chuyển constructor/field sang interface nếu caller chỉ cần những method thuộc interface. Nếu caller thực sự cần API của implementation class, cân nhắc **class-based proxy**, thiết kế lại interface để expose chức năng hợp lý, hoặc tách trách nhiệm sang bean khác.

### 3.3. Method chỉ có trong implementation

```java
public interface PaymentService {
    void pay();
}

public class PaymentServiceImpl implements PaymentService {
    @Override
    public void pay() {}

    public void retryFailedPayments() {}
}
```

`retryFailedPayments()` không nằm trong `PaymentService`. Với JDK proxy được khai báo theo `PaymentService`, caller **không thể** gọi `retryFailedPayments()` qua interface đó. Khi cần AOP advice cho method này, bạn cần quyết định liệu method nên thuộc một **interface phù hợp** hay cần chiến lược class proxy. Không dùng cast về implementation vì JDK proxy không phải implementation class.

## 4. CGLIB class-based proxy: tiêm được qua lớp cụ thể

Khi Spring AOP sử dụng CGLIB, một lớp con của target được sinh lúc chạy. Vì subclass là một instance hợp lệ của lớp cha, Spring có thể cung cấp nó cho constructor đang yêu cầu `PaymentServiceImpl`:

```java
@Service
public class ReconciliationJob {
    private final PaymentServiceImpl paymentService;

    public ReconciliationJob(PaymentServiceImpl paymentService) {
        this.paymentService = paymentService;
    }

    public void run() {
        paymentService.pay();
    }
}
```

Đoạn này **có thể hoạt động** nếu proxy thực tế là class-based, type hợp lệ và chỉ có một bean phù hợp.

Tuy nhiên, CGLIB có giới hạn quan trọng:

- **`final class`:** không thể subclass, nên không thể tạo CGLIB AOP proxy của chính class đó.
- **`final method`:** không thể override; lời gọi method ấy không được Spring CGLIB advice thông qua cơ chế override.
- **`private method`:** không thể override, không thể advice bằng Spring proxy.
- **Method không nhìn thấy đối với subclass** (chẳng hạn package-private ở parent thuộc package khác) cũng không được can thiệp.
- **Self-invocation:** `this.method()` bên trong target vẫn bỏ qua proxy, dù proxy dùng CGLIB.
- Việc CGLIB được Spring đóng gói trong `spring-core` nghĩa là ứng dụng Spring Framework hiện đại thường không cần khai báo dependency `cglib` riêng để dùng class-based Spring AOP.

**Kết luận thiết kế:** Inject concrete type có thể hữu ích trong codebase legacy hoặc khi không có interface ổn định, nhưng làm contract phụ thuộc vào cấu trúc implementation và lựa chọn proxy. Inject interface thường dễ giữ ổn định hơn khi refactor hay thay loại proxy.

## 5. Ví dụ hoàn chỉnh: hai context, cùng target, hai kiểu injection

Dự án này dùng **Java 17+**, **Spring Framework 6.2.19**, **AspectJ Weaver 1.9.25.1**, Maven. Nó cố ý tạo **hai `AnnotationConfigApplicationContext` riêng biệt** để cấu hình ép proxy không ảnh hưởng lẫn nhau:

- **Context A:** `@EnableAspectJAutoProxy(proxyTargetClass = false)` → JDK proxy cho target có interface; client tiêm interface.
- **Context B:** `@EnableAspectJAutoProxy(proxyTargetClass = true)` → CGLIB proxy; client tiêm implementation class.

### 5.1. Cấu trúc

```text
proxy-type-injection/
├── pom.xml
└── src/main/java/example/
    ├── GreetingPort.java
    ├── GreetingService.java
    ├── TraceAspect.java
    ├── InterfaceClient.java
    ├── ConcreteClient.java
    ├── JdkConfig.java
    ├── ClassConfig.java
    └── Main.java
```

### 5.2. Maven

**`pom.xml`**:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>proxy-type-injection</artifactId>
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

### 5.3. Business interface và implementation

**`GreetingPort.java`**:

```java
package example;

public interface GreetingPort {
    String greet(String name);
}
```

**`GreetingService.java`**:

```java
package example;

public class GreetingService implements GreetingPort {
    @Override
    public String greet(String name) {
        return "Xin chào " + name;
    }
}
```

### 5.4. Một `@Aspect` dùng chung cho hai context

**`TraceAspect.java`**:

```java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    @Before("execution(* example.GreetingService.greet(..))")
    public void beforeGreet() {
        System.out.println("advice: before greet");
    }
}
```

Pointcut áp dụng lên **method `GreetingService.greet`**. Lớp aspect được đăng ký làm bean riêng trong cả hai context bằng `@Bean`; chỉ `@Aspect` trên class **không tự đăng ký bean**.

### 5.5. Client phụ thuộc interface hoặc concrete class

**`InterfaceClient.java`**:

```java
package example;

public class InterfaceClient {
    private final GreetingPort service;

    public InterfaceClient(GreetingPort service) {
        this.service = service;
    }

    public void run() {
        System.out.println("interface client: " + service.greet("Lan"));
    }
}
```

**`ConcreteClient.java`**:

```java
package example;

public class ConcreteClient {
    private final GreetingService service;

    public ConcreteClient(GreetingService service) {
        this.service = service;
    }

    public void run() {
        System.out.println("concrete client: " + service.greet("Minh"));
    }
}
```

Hai client không tự tra context hoặc cast proxy. Mỗi client nhận dependency bằng constructor, để Spring resolve phù hợp với kiểu đã khai báo.

### 5.6. Context A: JDK proxy, inject interface

**`JdkConfig.java`**:

```java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class JdkConfig {
    @Bean
    public GreetingPort greetingService() {
        return new GreetingService();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }

    @Bean
    public InterfaceClient interfaceClient(GreetingPort service) {
        return new InterfaceClient(service);
    }
}
```

Bean `greetingService` được khai báo bằng **return type `GreetingPort`**. Với Spring AOP proxy JDK, object cuối cùng vẫn implements interface này. Interface client được inject qua chính tham chiếu do Spring cung cấp nên lời gọi nhận advice.

### 5.7. Context B: CGLIB proxy, inject concrete class

**`ClassConfig.java`**:

```java
package example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ClassConfig {
    @Bean
    public GreetingService greetingService() {
        return new GreetingService();
    }

    @Bean
    public TraceAspect traceAspect() {
        return new TraceAspect();
    }

    @Bean
    public ConcreteClient concreteClient(GreetingService service) {
        return new ConcreteClient(service);
    }
}
```

Spring được chỉ thị dùng **class-based proxy**, nên object được inject vào `ConcreteClient` là một subtype hợp lệ của `GreetingService`, miễn lớp này proxy được.

### 5.8. Chạy và xác minh kiểu object

**`Main.java`**:

```java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(JdkConfig.class)) {
            Object bean = context.getBean("greetingService");

            System.out.println("jdk proxy: " + AopUtils.isJdkDynamicProxy(bean));
            System.out.println("jdk is concrete: " + (bean instanceof GreetingService));

            try {
                context.getBean("greetingService", GreetingService.class);
                System.out.println("concrete lookup: succeeded");
            } catch (BeanNotOfRequiredTypeException ex) {
                System.out.println("concrete lookup: failed");
            }

            context.getBean(InterfaceClient.class).run();
        }

        try (var context = new AnnotationConfigApplicationContext(ClassConfig.class)) {
            Object bean = context.getBean("greetingService");

            System.out.println("cglib proxy: " + AopUtils.isCglibProxy(bean));
            System.out.println("cglib is concrete: " + (bean instanceof GreetingService));
            context.getBean(ConcreteClient.class).run();
        }
    }
}
```

Chạy từ thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.Main
```

Các dòng do `Main` và `TraceAspect`/clients in ra trong cấu hình ví dụ:

```text
jdk proxy: true
jdk is concrete: false
concrete lookup: failed
advice: before greet
interface client: Xin chào Lan
cglib proxy: true
cglib is concrete: true
advice: before greet
concrete client: Xin chào Minh
```

**Giải thích kết quả:**

1. Spring AOP tìm `TraceAspect`, thấy pointcut khớp `GreetingService.greet` và tạo AOP proxy cho bean đích.
2. Trong `JdkConfig`, proxy triển khai `GreetingPort` nhưng không extends `GreetingService`. Vì vậy lookup theo `GreetingService.class` bị từ chối, còn constructor cần `GreetingPort` nhận được dependency phù hợp.
3. Khi `InterfaceClient.run()` gọi `service.greet("Lan")`, lời gọi qua **JDK proxy** chạy advice trước khi method đích trả chuỗi.
4. Trong `ClassConfig`, proxy là subclass CGLIB của `GreetingService`; `ConcreteClient` được inject theo concrete type và vẫn gọi qua proxy.
5. Mỗi `ApplicationContext` có bean và cấu hình riêng, nên ví dụ không bị cấu hình `proxyTargetClass=true` của một context ép context kia sang CGLIB.

Ví dụ có **một advisor phù hợp với một method**. Nếu thêm advisor hoặc auto-proxy infrastructure khác, kiểu proxy và thứ tự advice có thể thay đổi; khi đó phải kiểm tra trạng thái thực tế.

## 6. Vấn đề của `@Autowired`: kiểu inject là một hợp đồng

### 6.1. Chọn interface khi caller chỉ cần API nghiệp vụ

```java
@Service
public class InvoiceController {
    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }
}
```

Ví dụ này giả định `InvoiceService` là **interface** và Spring cung cấp đúng một candidate bean (hoặc có `@Qualifier`/`@Primary` khi cần). Đây là cách giảm ràng buộc giữa controller và chi tiết implementation/proxy.

Dù Spring cấu hình JDK hay CGLIB proxy, cả hai đều có thể thực thi interface ấy. Việc dùng interface **không tự bảo đảm có AOP**: method phải match advisor và được gọi qua bean proxy.

### 6.2. Khi nào inject concrete class vẫn hợp lệ?

Inject concrete class hợp lệ nếu thực tế container trả về object tương thích. Ví dụ:

- Bean **không bị proxy** và object gốc thuộc implementation class.
- Bean dùng **CGLIB proxy**, không gặp giới hạn class/method.
- Project có quy ước class-based proxy rõ ràng và test kiểm chứng cho các bean liên quan.

Điều đáng tránh là viết code phụ thuộc vào concrete type trong khi **không kiểm soát chiến lược proxy**. Một cấu hình AOP hoặc Spring Boot property thay đổi có thể khiến cùng code inject thất bại.

### 6.3. Kiểu trả về của `@Bean` có liên quan nhưng không thay thế kiểu proxy

Với Java configuration:

```java
@Bean
public PaymentService paymentService() {
    return new PaymentServiceImpl();
}
```

Spring có metadata kiểu từ khai báo `@Bean`; runtime instance có thể là `PaymentServiceImpl` trước khi post-processing. Nhưng nếu sau đó Spring tạo **JDK proxy**, object cuối cùng chỉ assignable cho interface được proxy expose.

Ngược lại:

```java
@Bean
public PaymentServiceImpl paymentService() {
    return new PaymentServiceImpl();
}
```

Khai báo return type cụ thể **không ép Spring AOP phải dùng CGLIB**. Cấu hình `proxyTargetClass` và khả năng proxy mới quyết định chiến lược. Không dựa vào kiểu trả về factory method để cast một JDK proxy thành implementation.

## 7. Cấu hình chiến lược proxy trong Spring

### 7.1. Spring Framework thuần

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class InterfaceProxyConfig {
}
```

Đối với target có business interface, ví dụ này **ưu tiên JDK proxy** theo quy tắc Spring AOP. `false` không bảo đảm mọi bean đều dùng JDK: nếu target không có interface phù hợp, Spring có thể cần class-based proxy.

Khi cần CGLIB:

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class ClassProxyConfig {
}
```

Điều này chọn class-based proxy cho các bean AOP phù hợp trong phạm vi cấu hình. Không phải cách để biến một class `final` thành proxy được.

### 7.2. Spring Boot

Theo tài liệu Spring Boot, auto-configuration AOP **mặc định chọn class-based (CGLIB) proxy**. Để ưu tiên JDK proxy khi target có interface:

```properties
spring.aop.proxy-target-class=false
```

Muốn giữ mặc định class-based hoặc khai báo rõ:

```properties
spring.aop.proxy-target-class=true
```

Khi dùng AspectJ trên classpath và auto-configuration được bật, Spring Boot thường không cần thêm `@EnableAspectJAutoProxy` để hoạt động. Tuy nhiên, aspect vẫn phải đăng ký thành Spring bean.

Một khác biệt quan trọng: **Spring Framework thuần** thường mặc định dùng JDK proxy nếu có interface; **Spring Boot auto-configuration** mặc định dùng CGLIB. Đừng áp dụng kết quả của một ứng dụng Boot cho mọi ứng dụng Spring Framework thuần.

### 7.3. Cấu hình proxy có thể ảnh hưởng tới hạ tầng AOP khác

Spring có thể hợp nhất một số cấu hình auto-proxy creator, và thuộc tính **`proxyTargetClass=true` mạnh hơn** có thể ảnh hưởng tới các proxy do transaction hoặc AOP infrastructure khác tạo ra trong cùng context. Vì vậy:

- Tránh thêm `@EnableAspectJAutoProxy(proxyTargetClass=true)` chỉ để “chữa” một injection error nếu điều đó đổi proxy strategy toàn ứng dụng.
- Xem `@EnableTransactionManagement`, `@EnableCaching`, các auto-configurations và global proxy settings đang hoạt động.
- Kiểm tra **object thực tế** bằng `AopUtils.isJdkDynamicProxy(...)`/`isCglibProxy(...)` sau mọi thay đổi.

Spring Framework 7 còn có một số lựa chọn proxy-target cụ thể theo bean (ví dụ `@Proxyable` trong phiên bản hỗ trợ); không giả định các API mới đó sẵn có trong Spring 6.2 của ví dụ.

## 8. Các trường hợp dễ gây nhầm lẫn

### 8.1. Có nhiều implementation cùng interface

```java
public interface PaymentService { void pay(); }
public class CardPaymentService implements PaymentService { /* ... */ }
public class BankPaymentService implements PaymentService { /* ... */ }
```

Nếu cả hai được đăng ký làm Spring beans, constructor chỉ nhận `PaymentService` có thể **không xác định được bean nào**. Đây là vấn đề **candidate resolution**, không phải lỗi JDK/CGLIB proxy. Có thể dùng `@Qualifier`, `@Primary` hoặc tập hợp `List<PaymentService>` nếu yêu cầu nghiệp vụ phù hợp.

**Ví dụ minh họa:**

```java
public CheckoutController(@Qualifier("cardPaymentService") PaymentService service) {
    this.service = service;
}
```

Đây là snippet constructor, cần import `@Qualifier` và các type liên quan; không phải chương trình độc lập.

### 8.2. JDK proxy nhưng vẫn “thấy” target class

`AopUtils.getTargetClass(proxy)` có thể trả về `PaymentServiceImpl.class` ngay khi:

```java
proxy instanceof PaymentServiceImpl // false với JDK proxy
```

Không có mâu thuẫn: **target class** là lớp đứng sau proxy, còn **runtime type của proxy** quyết định việc assign/cast/inject. Đừng dùng `getTargetClass` làm lý do ép kiểu proxy thành target implementation.

### 8.3. Bean name không chữa lỗi assignability

`@Qualifier("paymentService")` giúp **chọn bean theo qualifier/name**, nhưng không làm JDK proxy tự trở thành subtype `PaymentServiceImpl`:

```java
// Vẫn không tương thích nếu Spring đang trả JDK proxy:
public CheckoutController(
        @Qualifier("paymentService") PaymentServiceImpl service) {
    // ...
}
```

Nếu cần inject implementation class, hãy đảm bảo **proxy class-based thật sự được tạo** hoặc thiết kế lại API thay vì chỉ thêm qualifier.

### 8.4. Class không implements interface

Một target không implements business interface không thể được JDK dynamic proxy đại diện qua API business của nó; trong Spring AOP thông thường cần class-based proxy. `proxyTargetClass=false` chỉ có nghĩa không **ép** dùng CGLIB cho các target có interface, chứ không thể tạo JDK proxy cho một class bất kỳ.

### 8.5. Interface default methods và generic method

JDK proxy expose các method interface, kể cả một số default method, nhưng việc advice match vẫn phụ thuộc expression, implementation và đường gọi qua proxy. Khi dùng generics/bridge methods hoặc overload, hãy kiểm tra **method signature thực tế** thay vì suy diễn từ tên. Chọn kiểu injection theo interface không thay thế kiểm tra pointcut.

### 8.6. Không “lấy target ra” để tránh cast lỗi

Có thể bạn thấy cách lấy underlying target qua `Advised.getTargetSource().getTarget()` rồi ép kiểu về implementation. Cách này **bỏ qua proxy**, có nguy cơ bỏ qua transaction, cache, security hoặc `@Aspect` advice. Một số target source còn gắn với scope và có thể cung cấp target khác theo từng lần truy xuất.

Nếu caller cần một hành vi nghiệp vụ, hãy expose hành vi đó qua interface/bean được Spring cung cấp thay vì tháo proxy để gọi trực tiếp target.

### 8.7. Spring Data, scoped proxy và các proxy khác

Một Spring Data repository thường được dùng theo **repository interface**; nhưng đó có thể là proxy phục vụ truy vấn/implementation của Spring Data, không nhất thiết là Spring AOP proxy tạo từ một `@Aspect` cụ thể.

Scoped proxies có thể dùng Spring AOP machinery để tìm target đúng scope. Khi đang sửa lỗi injection, cần xác định **proxy của tính năng nào**; không kết luận chỉ từ một tên class sinh động. Nguyên tắc chung vẫn là **thực tế proxy assignable cho kiểu nào**.

## 9. Checklist chẩn đoán lỗi inject khi có AOP

### Bước 1: đọc exception và kiểu được yêu cầu

Ví dụ:

```text
BeanNotOfRequiredTypeException
Required type: example.GreetingService
Actual type: jdk.proxy...$Proxy...
```

Đây là **minh họa cấu trúc thông điệp**, không phải nguyên văn exception cố định của mọi phiên bản JDK/Spring. Nếu thấy actual type là JDK proxy, hãy kiểm tra concrete dependency hoặc cast.

### Bước 2: kiểm tra proxy mà context trả về

```java
// Snippet đặt trong mã có ApplicationContext context.
Object bean = context.getBean("paymentService");
System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
System.out.println(bean.getClass().getName());
System.out.println(AopUtils.getTargetClass(bean).getName());
```

Các import liên quan là `org.springframework.aop.support.AopUtils` và `org.springframework.context.ApplicationContext`. Không hard-code tên class proxy sinh động vào business code hoặc vào kiểm thử: JDK/Spring có thể thay đổi cách đặt tên.

### Bước 3: so sánh các interface của proxy với constructor parameter

- Nếu bean là JDK proxy, parameter là **interface được proxy expose**?
- Nếu parameter là concrete class, có lý do bắt buộc không?
- Nếu BeanFactory tìm nhiều candidates, hãy xử lý ambiguity riêng bằng qualifier/primary.
- Nếu class-based proxy đang được yêu cầu, class/method có `final`, `private` hay giới hạn truy cập không?

### Bước 4: kiểm tra cấu hình proxy

- Trong Spring Framework thuần, có `@EnableAspectJAutoProxy(proxyTargetClass=...)` hoặc XML AOP/auto-proxy settings nào?
- Trong Boot, `spring.aop.proxy-target-class` đang nhận giá trị nào, và auto-configuration có được bật không?
- Transaction/cache/security và những auto-proxy infrastructure khác có đang tham gia cấu hình proxy không?

### Bước 5: kiểm chứng hành vi advice

Sau khi sửa được injection, vẫn phải **gọi một method khớp pointcut qua bean reference** để xác nhận advice thực thi. Thành công về kiểu không đảm bảo lời gọi đã qua proxy: gọi `new ServiceImpl()` hoặc self-invocation `this.pay()` vẫn có thể bỏ qua advice.

## 10. Cách lựa chọn trong dự án Spring AOP

| Yêu cầu thực tế | Hướng tiếp cận |
| --- | --- |
| Caller chỉ cần hành vi đã có trên interface | **Inject interface**, phù hợp với cả JDK và CGLIB proxy |
| Có interface, muốn thay đổi implementation linh hoạt | **Inject interface** và cấu hình bean resolution bằng qualifier nếu cần |
| Cần method nằm ngoài interface | Cân nhắc bổ sung method vào một interface phù hợp hoặc thiết kế một bean chuyên trách; dùng class-based proxy khi có lý do |
| Ứng dụng legacy chủ yếu inject concrete classes | Có thể chọn CGLIB với kiểm thử đầy đủ cho giới hạn `final` và tác động cấu hình toàn context |
| Bị `ClassCastException` từ JDK proxy → implementation | **Không cast**; nhận diện proxy và chuyển dependency sang interface tương thích |
| Bean có nhiều implementation cùng interface | Xử lý bằng `@Qualifier`/`@Primary` hoặc một chiến lược chọn bean, không đổi proxy tùy tiện |
| Thêm aspect khiến injection concrete đang hoạt động trở thành lỗi | Kiểm tra bean trước đây chưa có proxy; advisor mới khiến Spring tạo JDK proxy, từ đó lộ ra sự phụ thuộc kiểu cụ thể |
| Cần test chính xác AOP | Test kiểu proxy và lời gọi qua **bean từ context**, không test riêng `new Target()` |

Điều đáng nhớ nhất: **Dependency injection nhận object mà Spring thực tế cung cấp, không nhất thiết là object target ban đầu.** Khi Spring AOP tạo JDK proxy, contract đối với caller là interface; khi tạo CGLIB proxy, contract có thể bao gồm concrete class. Chọn kiểu dependency theo API mà caller cần và xác minh loại proxy đang chạy.

## 11. Tự kiểm tra

**Câu 1.** `OrderServiceImpl implements OrderService`. Spring tạo JDK proxy cho `OrderServiceImpl`. Constructor `CheckoutController(OrderServiceImpl service)` có tương thích với object proxy không?

**Đáp án:** Không. JDK proxy không extends implementation class. Dùng `CheckoutController(OrderService service)` nếu API đó đáp ứng nhu cầu.

**Câu 2.** Cùng bean chuyển sang CGLIB proxy; constructor nhận `OrderServiceImpl` có thể hoạt động không?

**Đáp án:** Có, nếu `OrderServiceImpl` được proxy subclass hợp lệ và Spring có thể resolve đúng bean. Tuy nhiên các phương thức `final/private` vẫn không được can thiệp theo cơ chế override.

**Câu 3.** `AopUtils.getTargetClass(bean)` là `OrderServiceImpl.class`. Có thể cast `bean` thành `OrderServiceImpl` không?

**Đáp án:** Chưa thể kết luận. Cần kiểm tra runtime proxy type; nếu bean là JDK proxy, cast vẫn sai.

**Câu 4.** Một ứng dụng Spring Boot có `spring.aop.proxy-target-class=false`, bean có interface và pointcut khớp. Cần tiêm theo loại nào để không phụ thuộc vào class-based proxy?

**Đáp án:** Tiêm qua interface được proxy expose.

**Câu 5.** Bean được inject bằng concrete type hợp lệ, nhưng `this.pay()` trong cùng target không chạy `@Before`. Có phải CGLIB proxy bị lỗi?

**Đáp án:** Không. Đây là **self-invocation**: lời gọi nội bộ qua `this` bỏ qua proxy Spring AOP, bất kể JDK hay CGLIB.

## 12. Tài liệu Spring chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK dynamic proxies và CGLIB, `proxy-target-class`, `final` methods, giới hạn `this`.
- [Spring Framework — AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/introduction-proxies.html): quy tắc chọn JDK proxy khi có interface và dùng CGLIB khi cần proxy theo class.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): `@EnableAspectJAutoProxy` và điều kiện aspect bean.
- [Spring Boot — Aspect-Oriented Programming](https://docs.spring.io/spring-boot/reference/features/aop.html): cấu hình `spring.aop.proxy-target-class` và lựa chọn mặc định.
- [Spring Framework — AopUtils](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/AopUtils.html): cách nhận diện proxy, target class.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-009">Phân biệt lớp proxy, lớp đích và kiểu khai báo của bean</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt lớp proxy, lớp đích và kiểu khai báo của bean trong Spring AOP

## 1. Vì sao cần phân biệt ba kiểu?

Trong ứng dụng Spring, một bean có thể được khai báo bằng một interface, được tạo từ một class cụ thể, nhưng đối tượng mà client nhận từ `ApplicationContext` lại thuộc **lớp proxy được sinh trong runtime**. Các khái niệm này có nhiệm vụ khác nhau:

1. **Kiểu khai báo của bean (declared bean type):** kiểu xuất hiện trong bean definition, ví dụ return type `PaymentService` của một phương thức `@Bean`. Spring dùng thông tin khai báo này cho type prediction, xác định candidate injection và các xử lý trước khi bean được tạo.
2. **Lớp đích (target class):** lớp của đối tượng thật thực thi nghiệp vụ sau khi Spring AOP cho interceptor tiếp tục lời gọi, ví dụ `PaymentServiceImpl`.
3. **Lớp proxy (proxy class):** lớp runtime của đối tượng được Spring trả cho client khi bean bị AOP proxy, ví dụ JDK dynamic proxy class hoặc subclass CGLIB được tạo tại runtime.

Đừng kết luận ba kiểu này là một chỉ vì chúng đại diện cho cùng một bean name. **Declared type không tự chọn cơ chế proxy; runtime proxy class không nhất thiết là target class; injection type của client cũng là một khái niệm riêng.**

Bài tập trung vào **Spring AOP proxy-based interception**, container, `@Bean` và Java dependency injection. Không mở rộng các nguyên lý Aspect tổng quát hay AspectJ weaving.

## 2. Ví dụ một bean có ba kiểu

Giả sử có interface và implementation:

```java
public interface PaymentService {
    String pay(String orderId);
}

public class PaymentServiceImpl implements PaymentService {
    @Override
    public String pay(String orderId) {
        return "PAID:" + orderId;
    }
}
```

Đăng ký Spring bean:

```java
@Configuration
public class PaymentConfig {
    @Bean
    public PaymentService paymentService() {
        return new PaymentServiceImpl();
    }
}
```

Đây là các đoạn khai báo minh họa; phần 5 có project chạy được. Từ hai đoạn trên:

- `PaymentService` là **kiểu khai báo** trong chữ ký `@Bean`.
- `PaymentServiceImpl` là **lớp đích** do method `@Bean` tạo ra.
- Nếu Spring AOP áp advice lên bean, reference mà consumer nhận sẽ là **proxy**: lớp runtime của reference đó có thể khác cả hai kiểu vừa nêu.

```text
@Bean PaymentService paymentService()
           |
           | declared bean type: PaymentService
           v
    Spring BeanFactory
           |
           | gọi factory method
           v
  new PaymentServiceImpl()
           |
           | target class: PaymentServiceImpl
           v
   Spring AOP auto-proxy
          / \
         /   \
        v     v
   JDK proxy   CGLIB proxy
   implements  extends
   interface   implementation
        \       /
         \     /
          v   v
  Client nhận bean reference,
  gọi pay() qua proxy rồi tới target
```

**Một lưu ý quan trọng:** Spring có thể thực thi `BeanPostProcessor` sau khi tạo object từ `@Bean` để thay object được cấp cho consumer bằng proxy. Do đó method `@Bean` có thể thật sự trả implementation, trong khi `context.getBean(...)` lại trả proxy.

## 3. So sánh theo kiểu proxy

Cùng một target `PaymentServiceImpl implements PaymentService` có advisor Spring AOP phù hợp:

| Thuộc tính | JDK dynamic proxy | CGLIB class-based proxy |
| --- | --- | --- |
| Lớp proxy runtime | Lớp sinh bởi JDK, implement interface được công bố | Subclass runtime của target class |
| Lớp đích | `PaymentServiceImpl` | `PaymentServiceImpl` |
| `@Bean` return type ví dụ | `PaymentService` | `PaymentService` |
| `bean instanceof PaymentService` | `true` | `true` |
| `bean instanceof PaymentServiceImpl` | `false` | `true` |
| `bean.getClass() == PaymentServiceImpl.class` | `false` | `false` |
| `AopUtils.isJdkDynamicProxy(bean)` | `true` | `false` |
| `AopUtils.isCglibProxy(bean)` | `false` | `true` |
| `AopUtils.getTargetClass(bean)` | `PaymentServiceImpl.class` | `PaymentServiceImpl.class` |

Trong bảng, bean **đã được Spring AOP tạo proxy**. Nếu không có advisor phù hợp, bean có thể được trả trực tiếp là `PaymentServiceImpl`, khi đó các kết quả liên quan proxy thay đổi.

### 3.1. JDK proxy có interface nhưng không phải implementation

Với JDK dynamic proxy, Spring tạo object implement các interface được expose. Vì vậy:

```java
PaymentService service = context.getBean(PaymentService.class);
// Phù hợp với interface của JDK proxy.

// Không được giả định kiểu ép sau luôn hợp lệ:
PaymentServiceImpl impl = (PaymentServiceImpl) service;
```

Khi `service` là JDK proxy thực tế, dòng cast thường ném `ClassCastException`. Việc target phía sau proxy thuộc `PaymentServiceImpl` **không làm proxy trở thành subtype của implementation**.

### 3.2. CGLIB proxy kế thừa target class

Với CGLIB, Spring tạo subclass để intercept method có thể override:

```text
PaymentServiceImpl             <- target class
        ^
        | extends
        |
Generated CGLIB proxy class    <- proxy runtime class
        |
        +-- intercept call via advisor chain
        |
        +-- delegate to target PaymentServiceImpl instance
```

CGLIB proxy `instanceof PaymentServiceImpl` là `true`, nhưng `proxy.getClass() == PaymentServiceImpl.class` vẫn là `false`. Hai kiểm tra trả lời **hai câu hỏi khác nhau**: quan hệ kế thừa và lớp runtime chính xác.

Spring AOP proxy có object target riêng trong cấu hình tiêu chuẩn. Do vậy, sau khi target thực thi một method và gọi `this.otherMethod()`, lời gọi nội bộ không quay lại proxy, dù proxy là subclass CGLIB.

## 4. Kiểu khai báo của bean khác kiểu injection point

Nhìn lại một phương thức factory:

```java
@Bean
PaymentService paymentService() {
    return new PaymentServiceImpl();
}
```

Có ít nhất hai thứ dễ nhầm:

- **Return type `PaymentService`:** thông tin ở bean definition. Trước khi singleton được khởi tạo, Spring thường sử dụng kiểu này để dự đoán candidate của `@Autowired` hoặc `getBeanNamesForType(...)`.
- **Object thực tế `new PaymentServiceImpl()`:** chỉ được biết hoàn toàn khi factory method chạy. Sau đó Spring có thể áp AOP proxy.

Đối với client:

```java
@Service
class CheckoutService {
    private final PaymentService paymentService;

    CheckoutService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
}
```

Đây là đoạn sử dụng Spring DI minh họa, cần import tương ứng. Constructor parameter ở đây là **injection point type**. Khi kiểu injection là interface, cả JDK và CGLIB AOP proxy phù hợp đều có thể được inject.

Nếu constructor lại yêu cầu `PaymentServiceImpl`, phải xét hai điều kiện **riêng**:

1. BeanFactory có nhận diện bean này là candidate của kiểu implementation ở thời điểm giải quyết dependency không? Return type `@Bean` khai báo interface có thể giới hạn dự đoán kiểu trước khi singleton tồn tại.
2. **Object cuối cùng** có assignable tới implementation không? JDK proxy **không phải** `PaymentServiceImpl`; CGLIB proxy là subclass của `PaymentServiceImpl` trong trường hợp được tạo thành công.

Khai báo `@Bean PaymentServiceImpl paymentService()` làm thông tin type prediction cụ thể hơn, **nhưng không tự ép AOP chọn CGLIB**. Nếu AOP vẫn chọn JDK proxy thì final bean reference không thể ép sang `PaymentServiceImpl`.

## 5. Chương trình hoàn chỉnh: cùng target, hai loại proxy

Ví dụ sử dụng **Java 17+, Maven, Spring Framework 6.2.19**. Hai context được tạo lần lượt và hoàn toàn độc lập:

- `JdkConfig` kích hoạt `@EnableAspectJAutoProxy(proxyTargetClass=false)`.
- `CglibConfig` kích hoạt `@EnableAspectJAutoProxy(proxyTargetClass=true)`.
- Cả hai context import cùng `SharedBeans`; method `@Bean` trả kiểu interface, trong khi implementation là một class cụ thể.
- `TraceAspect` có advice trước `deliver()` để Spring thực sự cần tạo AOP proxy.

Cấu trúc project:

```text
spring-proxy-bean-types/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── demo/
                └── BeanTypeDemo.java
```

### 5.1. Tệp pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-proxy-bean-types</artifactId>
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
                    <mainClass>demo.BeanTypeDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 5.2. Tệp src/main/java/demo/BeanTypeDemo.java

```java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public class BeanTypeDemo {

    public interface MessageService {
        String deliver(String id);
    }

    public static class MessageServiceImpl implements MessageService {
        @Override
        public String deliver(String id) {
            System.out.println("TARGET deliver " + id);
            return "ACK:" + id;
        }
    }

    @Aspect
    public static class TraceAspect {
        @Before("execution(* demo..*.deliver(..))")
        public void beforeCall() {
            System.out.println("ADVICE deliver");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SharedBeans {
        @Bean
        public MessageService messageService() {
            return new MessageServiceImpl();
        }

        @Bean
        public TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    @Import(SharedBeans.class)
    public static class JdkConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(SharedBeans.class)
    public static class CglibConfig {
    }

    static void inspect(String label, Object bean) {
        System.out.println(label + " aop=" + AopUtils.isAopProxy(bean));
        System.out.println(label + " jdk="
                + AopUtils.isJdkDynamicProxy(bean));
        System.out.println(label + " cglib="
                + AopUtils.isCglibProxy(bean));
        System.out.println(label + " proxy-is-impl="
                + (bean instanceof MessageServiceImpl));
        System.out.println(label + " target="
                + AopUtils.getTargetClass(bean).getSimpleName());
    }

    public static void main(String[] args) {
        try (var jdk =
                new AnnotationConfigApplicationContext(JdkConfig.class)) {
            MessageService bean = jdk.getBean(MessageService.class);
            inspect("JDK", bean);
            System.out.println("JDK result=" + bean.deliver("A"));
        }

        try (var cglib =
                new AnnotationConfigApplicationContext(CglibConfig.class)) {
            MessageService bean = cglib.getBean(MessageService.class);
            inspect("CGLIB", bean);
            System.out.println("CGLIB result=" + bean.deliver("B"));
        }
    }
}
```

Chạy tại thư mục chứa `pom.xml`:

```bash
mvn -q compile exec:java
```

**Các dòng do chương trình in theo đúng thứ tự** (chưa tính log của Maven hoặc Spring):

```text
JDK aop=true
JDK jdk=true
JDK cglib=false
JDK proxy-is-impl=false
JDK target=MessageServiceImpl
ADVICE deliver
TARGET deliver A
JDK result=ACK:A
CGLIB aop=true
CGLIB jdk=false
CGLIB cglib=true
CGLIB proxy-is-impl=true
CGLIB target=MessageServiceImpl
ADVICE deliver
TARGET deliver B
CGLIB result=ACK:B
```

### 5.3. Đọc kết quả

Ở **cả hai context**, `SharedBeans.messageService()` khai báo return type `MessageService` và tạo target `MessageServiceImpl`. Nhưng:

- **JDK context:** `isJdkDynamicProxy=true`, object proxy không phải instance của `MessageServiceImpl`; gọi method qua interface vẫn chạy advice.
- **CGLIB context:** `isCglibProxy=true`, proxy là subclass của `MessageServiceImpl`; object proxy là instance của implementation về mặt Java type.
- **Cả hai:** `getTargetClass(bean)` trả tên `MessageServiceImpl` và advice chạy trên `deliver()`.

Cơ chế chọn proxy thay đổi mà không hề thay chữ ký `@Bean`. Đồng thời, một proxy dùng interface không có nghĩa target class đã biến thành interface.

### 5.4. Thí nghiệm thay đổi declared type

Thay đúng một phương thức trong `SharedBeans`:

```java
@Bean
public MessageServiceImpl messageService() {
    return new MessageServiceImpl();
}
```

Đây là **phiên bản thay thế**, không khai báo đồng thời với bản cũ trong cùng context. Khi đó declared type cụ thể hơn, giúp type prediction theo implementation. Nhưng nếu `JdkConfig` vẫn dùng JDK proxy, `MessageServiceImpl` vẫn không phải kiểu của runtime proxy. Nếu application tiêm implementation, nó có thể thất bại sau post-processing. Phải xem cả khai báo bean **và** kiểu proxy hiệu lực.

## 6. Spring suy kiểu bean tại những thời điểm nào?

Trong ứng dụng thực, câu hỏi “Spring biết bean có type nào?” phụ thuộc vào **thời điểm**:

| Giai đoạn | Thông tin quan trọng |
| --- | --- |
| Biên dịch Java | Signature `@Bean` và kiểu biến tại client |
| Đăng ký bean definition | Declared return type, class metadata và các điều kiện đăng ký |
| Dự đoán candidate injection | Kiểu có thể suy ra từ bean definition, factory method, một số BeanPostProcessor |
| Tạo target instance | Runtime type thực sự do `@Bean` hoặc constructor tạo |
| Spring AOP tạo proxy | Proxy strategy; kiểu được công bố cho client |
| Client gọi method | Object reference thực tế; method execution đi qua proxy/interceptor nếu phù hợp |

Ví dụ `@Bean PaymentService service()` làm Spring biết interface trước khi singleton khởi tạo. Spring có thể chỉ biết implementation sau khi method được gọi để tạo instance. Tài liệu Spring cảnh báo type prediction theo kiểu implementation chưa được khai báo có thể phụ thuộc thời điểm singleton ra đời.

Do đó, **truy vấn bean names theo implementation class** lúc khởi động có thể khác sau khi khởi tạo. Điều này cần phân tích theo context thực tế; không nên dựa trên một lần gọi `getBeanNamesForType(Implementation.class)` như kết luận tuyệt đối.

Chọn declared return type theo nhu cầu: interface khi consumer chỉ cần contract, kiểu cụ thể khi container cần dự đoán type cho điểm inject cụ thể; nhưng nếu muốn inject implementation khi có Spring AOP, còn phải bảo đảm proxy cuối cùng tương thích kiểu ấy.

## 7. Dùng công cụ Spring nào để nhận diện đúng lớp?

### 7.1. Runtime class

```java
Object bean = context.getBean("messageService");
Class<?> runtimeClass = bean.getClass();
```

Đây là lớp thực tế của reference mà client nhận. Nếu bean đã được AOP proxy, đây thường là class do JVM/Spring sinh động, **không phải target implementation class**. Tên lớp sinh ra không phải API ổn định; đừng viết logic phụ thuộc vào tiền tố như `$Proxy` hoặc `$$SpringCGLIB$$`.

### 7.2. Kiểm tra Spring AOP proxy và target class

```java
boolean aop = AopUtils.isAopProxy(bean);
boolean jdk = AopUtils.isJdkDynamicProxy(bean);
boolean cglib = AopUtils.isCglibProxy(bean);
Class<?> targetClass = AopUtils.getTargetClass(bean);
```

Đây là **đoạn minh họa thêm** cho ví dụ phần 5, cần `import org.springframework.aop.support.AopUtils;`. Các câu trả lời khác nhau:

- `isAopProxy` hỏi object có phải Spring AOP proxy không.
- `isJdkDynamicProxy` và `isCglibProxy` giúp phân biệt chiến lược tạo proxy.
- `getTargetClass` trả target class mà Spring AOP biết; với object thường, trả class của object đó.

`AopProxyUtils.ultimateTargetClass(bean)` có thể hữu ích khi có những proxy lồng nhau với static target source, nhưng không cần thiết để chẩn đoán ví dụ một proxy thông thường. Cũng không nên nhầm nó với thao tác lấy một object target rồi bypass AOP.

### 7.3. Khi nào không nên dùng ClassUtils.getUserClass?

Tiện ích `ClassUtils.getUserClass(...)` hữu ích để nhận diện class gốc khi đối tượng đã được CGLIB-enhance. Nhưng với **JDK dynamic proxy**, đừng giả định nó luôn suy ra business implementation nằm sau proxy. Muốn tìm target class của Spring AOP proxy, hãy dùng `AopUtils.getTargetClass(bean)`.

### 7.4. BeanFactory.getType không nhất thiết bằng bean.getClass

`applicationContext.getType("messageService")` hỏi Spring về kiểu của bean dưới góc nhìn BeanFactory tại thời điểm truy vấn. `context.getBean("messageService").getClass()` hỏi lớp runtime của object hiện tại. Việc trả về khác nhau có thể liên quan `@Bean` declaration, lifecycle, proxying, `FactoryBean` và type prediction.

Khi chẩn đoán injection không tìm được candidate, kiểm tra cả bean definition lẫn object cuối cùng. Khi chẩn đoán advice, kiểm tra proxy và target class từ Spring AOP utils.

## 8. Những hệ quả thường gặp trong Spring AOP

### 8.1. Proxy JDK làm injection concrete implementation thất bại

Đoạn consumer sau:

```java
@Service
public class BillingFacade {
    private final PaymentServiceImpl paymentService;

    public BillingFacade(PaymentServiceImpl paymentService) {
        this.paymentService = paymentService;
    }
}
```

là **mẫu cần import/scan**, không phải file độc lập. Nếu bean cuối cùng là JDK proxy, constructor yêu cầu `PaymentServiceImpl` không tương thích Java type. Tùy giai đoạn kiểm tra và kiểu khai báo bean, lỗi có thể xuất hiện khi chọn candidate hoặc sau khi Spring tạo proxy.

Thông thường nên inject interface mà client thực sự dùng:

```java
public BillingFacade(PaymentService paymentService) {
    this.paymentService = paymentService;
}
```

Không nên chuyển sang CGLIB chỉ để né một lần cast nếu interface đã đủ phục vụ nghiệp vụ.

### 8.2. CGLIB giữ subtype nhưng vẫn bị giới hạn interception

`proxy instanceof Implementation` là `true` không chứng minh mọi method trên implementation được advice. CGLIB không thể override `final`, `private` method; không thể tạo subclass cho `final class`. Ngoài ra, method `this.inner()` bên trong target không tự đi qua proxy một lần nữa.

### 8.3. Target không có business interface

Một target class không có interface thường cần CGLIB nếu Spring AOP tạo proxy. Cấu hình `proxyTargetClass=false` chỉ có ý nghĩa “không ép class proxy ở nơi interface proxy khả thi”, không phải “cấm CGLIB”.

### 8.4. Spring Framework khác Spring Boot về mặc định

Trong Spring Framework thuần, AOP mặc định chọn JDK proxy nếu target có interface. Spring Boot có thể kích hoạt class-based proxy mặc định qua auto-configuration AOP; trong cấu hình phổ biến property `spring.aop.proxy-target-class=true`. Muốn về chiến lược interface-based khi phù hợp, dùng:

```properties
spring.aop.proxy-target-class=false
```

Nhưng nếu nhiều cấu hình auto-proxy cùng tồn tại, lựa chọn target-class proxy từ nơi khác có thể chi phối. Vì vậy **xác minh trên bean runtime** đáng tin hơn chỉ đọc một property.

### 8.5. CGLIB của @Configuration không nhất thiết là AOP proxy

Một `@Configuration(proxyBeanMethods=true)` có thể được Spring enhance bằng subclass để điều phối lời gọi tới `@Bean` methods. Điều đó khác với proxy do Spring AOP tạo để chạy advisor. Không suy `AopUtils.isAopProxy(bean) == true` chỉ vì tên class chứa chuỗi `CGLIB`.

## 9. Quy trình chẩn đoán lỗi kiểu và proxy

Khi ứng dụng lỗi sau khi thêm `@Aspect` hoặc đổi proxy strategy:

1. **Xác định bean name và declaration**: return type của `@Bean` hoặc class của `@Component` đang là gì?
2. **Xác định lớp thật do factory tạo**: `new ...Impl()` hay component class nào?
3. **Kiểm tra advisor thực sự áp dụng không**: có aspect bean/pointcut nào chọn method của bean?
4. **Lấy bean từ chính ApplicationContext**: không dùng object tự tạo bằng `new` để kết luận về Spring proxy.
5. **Đo loại proxy**: `AopUtils.isAopProxy/isJdkDynamicProxy/isCglibProxy`.
6. **Đo target class**: `AopUtils.getTargetClass(bean)`.
7. **So kiểu injection point**: interface hay implementation? `type.isInstance(bean)` có đúng không?
8. **Đối chiếu type prediction**: `@Bean` return type có quá chung chung cho các injection point đặc biệt không?
9. **Xem cấu hình proxy**: `@EnableAspectJAutoProxy(proxyTargetClass=...)`, Boot properties và các auto-proxy creator khác.
10. **Kiểm tra đường gọi**: có đi qua proxy không, có self-invocation hay method `final/private` không?

Ghi ngắn gọn năm dữ liệu sau khi debug: declared type, target class, runtime proxy kind, requested injection type và method invocation path. Chúng giải quyết phần lớn nhầm lẫn mà không phụ thuộc tên proxy do Spring/JVM sinh.

## 10. Câu hỏi tự kiểm tra

**1.** `@Bean PaymentService paymentService()` trả `new PaymentServiceImpl()`; JDK AOP proxy được tạo. `bean.getClass() == PaymentServiceImpl.class` có đúng không?  
**Trả lời:** Không. `getClass()` trả lớp proxy runtime.

**2.** Cùng bean trên dùng CGLIB. `bean instanceof PaymentServiceImpl` là gì?  
**Trả lời:** `true` vì CGLIB proxy kế thừa target implementation.

**3.** JDK proxy không extends implementation. `AopUtils.getTargetClass(bean)` có phải luôn chỉ là interface không?  
**Trả lời:** Không. Trong ví dụ, Spring AOP biết target implementation là `PaymentServiceImpl`.

**4.** Đổi return type thành `@Bean PaymentServiceImpl paymentService()` có bắt buộc Spring dùng CGLIB không?  
**Trả lời:** Không. Return type khai báo và proxy strategy được quyết định bởi các cơ chế khác nhau.

**5.** Một dependency inject `PaymentServiceImpl`, bean AOP dùng JDK proxy. Lỗi chính là gì?  
**Trả lời:** JDK proxy không assignable sang implementation class; còn declared return type ảnh hưởng việc tìm candidate ở giai đoạn dự đoán kiểu.

**6.** Bean dùng CGLIB và `proxy instanceof PaymentServiceImpl` trả `true`. Có chắc method `private void audit()` chạy advice không?  
**Trả lời:** Không; private method không thể được subclass override.

**7.** Có được dựa vào tiền tố class name để nhận diện JDK/CGLIB proxy không?  
**Trả lời:** Không. Ưu tiên tiện ích `AopUtils` và kiểm tra type assignability theo Java.

## Tài liệu Spring chính thức

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB, quan hệ target và các giới hạn proxy.
- [Spring Framework — Using the @Bean Annotation](https://docs.spring.io/spring-framework/reference/core/beans/java/bean-annotation.html): declared return type và type prediction.
- [Spring Framework — AopUtils Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/aop/support/AopUtils.html): các hàm kiểm tra Spring AOP proxy và target class.
- [Spring Framework 6.2 — AOP Proxies](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-proxies.html): lựa chọn interface/class proxies.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-010">Hạn chế liên quan đến Java Module System và quyền truy cập lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Hạn chế của Spring AOP với Java Module System và quyền truy cập lớp (JDK Proxy & CGLIB)

## 1. Bối cảnh: vì sao cùng một aspect chạy trên classpath nhưng lỗi trên module path?

Spring AOP chặn lời gọi phương thức thông qua **proxy**. Một Spring bean có thể được bao bởi:

- **JDK dynamic proxy**: một đối tượng triển khai các interface của target. Client gọi phương thức thông qua interface.
- **Class-based proxy (CGLIB)**: Spring tạo một subclass ở runtime để override những phương thức có thể chặn được.

Khi đưa ứng dụng Java 9+ lên **Java Platform Module System (JPMS)** bằng `module-info.java` và `--module-path`, Java kiểm soát thêm **module nào đọc module nào**, **package nào được truy cập công khai**, **package nào được truy cập sâu qua reflection** và **runtime có quyền định nghĩa lớp proxy ở đâu**. Vì vậy, lỗi AOP có thể chỉ xuất hiện khi triển khai theo named module, dù cùng source chạy được trên classpath.

Trong Spring, câu hỏi đúng là:

1. Spring đang dùng JDK proxy hay CGLIB?
2. Bean, interface, lớp cấu hình và aspect thuộc package/module nào?
3. Target class/method có thể truy cập hoặc override không?
4. Các package cần thiết đã `exports` hoặc `opens` đúng **module tiêu thụ** chưa?
5. Runtime đã cấp phép tạo class động ở classloader/module phù hợp chưa?
6. Lời gọi có đi qua Spring AOP proxy không?

**Phạm vi bài:** Spring Framework `@AspectJ` theo proxy, hệ quả JPMS và quyền truy cập của target/advice. Đây không phải bài giới thiệu JPMS tổng quát hoặc AspectJ bytecode weaving độc lập.

## 2. Phân biệt bốn loại rào cản

| Loại rào cản | Ví dụ | Liên hệ Spring AOP |
| --- | --- | --- |
| **Java language / class visibility** | Class không `public`; method `private`; method `final`; package-private method không được subclass ở package khác nhìn thấy | Dù module đã `opens`, CGLIB vẫn không override `final` hoặc `private`; JDK proxy chỉ cung cấp method qua interface của proxy. |
| **JPMS readability / exports** | Module A không `requires` module B; package chứa public API chưa được `exports` | Code ở module khác có thể không compile hoặc không truy cập trực tiếp interface/public class; JDK proxy không tự xóa các giới hạn ở API phía client. |
| **JPMS deep reflection / opens** | Spring cần phản chiếu gọi constructor hoặc method không public trong package đóng | Có thể gặp `InaccessibleObjectException` hoặc lỗi truy cập liên quan; cần mở đúng package cho module thực sự dùng reflection. |
| **Runtime-generated class / ClassLoader** | CGLIB cần định nghĩa một subclass động trong môi trường module chặt | Có thể không tạo được proxy, dù method là public; `--add-opens` không chữa được mọi giới hạn classloader hoặc module membership. |

Một điểm thường bị bỏ qua: **`exports` và `opens` giải quyết hai quyền khác nhau**. Cả hai cũng **không biến** method `private`, `final` thành method mà CGLIB có thể override.

## 3. Spring quyết định JDK hay CGLIB như thế nào?

Trong cấu hình Spring AOP thông thường, nếu target triển khai interface phù hợp thì Spring có thể dùng JDK dynamic proxy; nếu không có interface hoặc cấu hình buộc proxy theo lớp, Spring dùng CGLIB.

### 3.1. Cấu hình trên Spring Framework

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
class JdkProxyConfiguration {
    // Ưu tiên JDK proxy khi target có interface phù hợp.
}
```

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class ClassProxyConfiguration {
    // Buộc proxy theo lớp khi bean cần Spring AOP proxy.
}
```

Đây là **hai đoạn minh họa độc lập**; không đăng ký cả hai vào một context chỉ để thử hai chế độ. Khi nhiều cấu hình Spring trong cùng context yêu cầu proxy, một `proxyTargetClass=true` ở cơ chế khác như transaction hoặc caching có thể chi phối lựa chọn thực tế.

### 3.2. Spring Boot có thể khác cấu hình Spring Framework thuần

Với Spring Boot 3.3 có AOP auto-configuration phù hợp (thường qua `spring-boot-starter-aop`), class-based proxy thường là mặc định. Có thể đổi định hướng bằng:

```properties
spring.aop.proxy-target-class=false
```

Thuộc tính này không phủ quyết mọi cấu hình tường minh khác trong context. Cần kiểm tra **runtime proxy thực tế**, không đoán chỉ từ một property.

### 3.3. Kiểm tra bằng API của Spring

```java
Object bean = context.getBean("gateway");

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
```

Đoạn trên là **snippet chẩn đoán**: giả sử biến `context` tồn tại và bean tên `gateway` đã đăng ký. `AopUtils` thuộc `org.springframework.aop.support`. Nếu không có advisor khớp hoặc bean không qua Spring, có thể không tạo AOP proxy dù `@EnableAspectJAutoProxy` đã bật.

## 4. JDK dynamic proxy dưới JPMS: điều gì được phép, điều gì không?

### 4.1. JDK proxy dựa vào interface, không cần subclass target

Giả sử:

```java
package demo.orders.api;

public interface OrderGateway {
    String submit(String id);
}
```

```java
package demo.orders.internal;

public final class DefaultOrderGateway
        implements demo.orders.api.OrderGateway {

    @Override
    public String submit(String id) {
        return "stored:" + id;
    }
}
```

Nếu client được tiêm **`OrderGateway`**, một JDK proxy có thể triển khai interface đó dù target class là `final`. CGLIB sẽ không thể tạo subclass của `DefaultOrderGateway` do lớp này `final`.

Tuy nhiên, JDK proxy chỉ trình bày những interface mà proxy triển khai:
- Client không thể cast một JDK proxy thành `DefaultOrderGateway`.
- Phương thức chỉ có trên class và **không được khai báo trên interface proxy** không thể gọi thông qua interface đó.
- Các method được advice vẫn phải được gọi qua proxy; self-invocation từ target không được chặn.
- Interface và chữ ký method vẫn phải có khả năng truy cập hợp lệ trong bối cảnh module của client.

### 4.2. JPMS kiểm soát khả năng nhìn thấy interface từ module khác

Ví dụ mô hình module:

```java
// module-info.java của module cung cấp API
module demo.orders {
    exports demo.orders.api;

    // Những requires/opens khác phụ thuộc cấu trúc Spring thực tế.
}
```

`exports demo.orders.api` cho phép module khác có quan hệ đọc hợp lệ tham chiếu public type trong package API. Nó **không tự mở quyền deep reflection cho mọi lớp trong package internal**.

Nếu `OrderGateway` không được export cho module client, code client ở module khác có thể không dùng được interface như một API biên dịch thông thường. Do đó, đừng dựa vào “JDK proxy sẽ xuất hiện” để bỏ qua thiết kế module boundary.

### 4.3. Không đơn giản hóa quá mức quy tắc JDK proxy

Theo JDK 17, lớp proxy tạo bởi `java.lang.reflect.Proxy` có quy tắc chọn package/module tùy khả năng truy cập của các proxy interfaces. Đặc biệt:
- Public interface trong package được export/open có cách đặt proxy class phù hợp với khả năng truy cập đã công bố.
- Public interface trong package không export có thể dẫn đến proxy class thuộc **dynamic module với package không export**.
- Các interface không public có hạn chế nghiêm ngặt về package/module chung nếu cùng được proxy.
- Có proxy class **không đồng nghĩa** client có thể phản chiếu constructor hoặc truy cập mọi type chứa trong method signature.

Bởi vậy, khuyến nghị thực tế với Spring là **public business interface ở package được export cho module cần sử dụng**, inject theo interface và để Spring quản lý proxy. Không tự suy đoán tên module hoặc tên class proxy tạo ra ở runtime.

## 5. CGLIB và JPMS: subclass động cần những quyền gì?

### 5.1. CGLIB bị ràng buộc bởi khả năng kế thừa và override

Proxy theo lớp thường dựa trên subclass động của target class.

| Target hoặc method | JDK interface proxy | CGLIB class proxy |
| --- | --- | --- |
| Target class `final` nhưng có public interface | Có thể proxy qua interface | Không subclass được class `final` |
| Method `final` trong target | Interface method có thể được JDK proxy chặn | Không override được method `final` |
| Method `private` | Không nằm trên interface proxy để gọi | Không override được |
| Method package-private của superclass ở package khác | Không trực tiếp là interface API | Không nhìn thấy để override như method thường |
| Public non-final method | Nếu method được expose qua interface | Có thể override nếu class/proxy generation hợp lệ |
| Method chỉ được gọi bằng `this.otherMethod()` trong target | Bỏ qua proxy | Bỏ qua proxy |

Hai trường hợp cuối không liên quan JPMS: đó là giới hạn cơ bản của Spring AOP proxy. Việc thêm `opens` hoặc `--add-opens` **không khắc phục self-invocation hoặc non-overridable methods**.

### 5.2. Module path có thể cản định nghĩa subclass

Trong Java 9+, class được định nghĩa ở runtime phải tuân theo hệ thống module và classloader. Spring CGLIB có đường tạo subclass thường liên quan tới `MethodHandles.Lookup#defineClass` hoặc đường phản chiếu dự phòng, tùy môi trường. `Lookup#defineClass` chỉ được dùng trong những điều kiện package/loader thích hợp. Khi module hoặc classloader không cho phép định nghĩa lớp proxy ở vị trí cần thiết, proxy generation có thể thất bại.

Ví dụ được tài liệu Spring nêu rõ là **cố tạo CGLIB proxy cho lớp trong `java.lang` khi triển khai trên module path**. Đây không phải tình huống một service thông thường của ứng dụng; nó cho thấy có giới hạn thật của module system với subclass động.

Tài liệu Spring nói trường hợp fallback trên classpath có thể cần JVM option:

```text
--add-opens=java.base/java.lang=ALL-UNNAMED
```

**Không dùng option đó như công thức chữa mọi lỗi module path:**
- `ALL-UNNAMED` chỉ mở cho các **unnamed modules**, không mở cho mọi named module.
- Với module được đặt tên, tên đích mở quyền cần được xét cụ thể; ngay cả khi mở package đúng, giới hạn tạo class ở classloader/module không phù hợp vẫn có thể còn.
- Mở `java.base/java.lang` rộng chỉ để ép proxy một lớp JDK cốt lõi thường là dấu hiệu nên đổi thiết kế proxy. Đừng áp dụng `--add-opens=java.base/java.lang=ALL-UNNAMED` cho toàn ứng dụng nếu lỗi thực sự thuộc package của chính ứng dụng.

### 5.3. Lỗi có thể xuất hiện lúc khởi tạo Spring context

Nếu Spring đã thấy aspect advisor và cần class proxy cho bean nhưng không thể generate subclass, context có thể khởi tạo thất bại với lỗi liên quan `AopConfigException`, `CodeGenerationException`, `IllegalAccessException` hoặc `InaccessibleObjectException`; tên exception phụ thuộc call stack và phiên bản.

Không đọc **chỉ** dòng lỗi ngoài cùng. Hãy xem nested `Caused by` để xác định:
- Module nào chứa lớp bị từ chối truy cập?
- Package nào không được `opens` hoặc `exports`?
- Thành phần nào đang thử reflective access hoặc define class?
- Bean nào cần CGLIB proxy, vì sao Spring chọn class proxy?
- Có thể chuyển bean sang JDK proxy hay không?

## 6. `exports`, `opens`, `--add-exports`, `--add-opens`: chọn đúng công cụ

| Cú pháp | Quyền cấp | Ví dụ dùng trong bối cảnh Spring |
| --- | --- | --- |
| `exports p;` | Cho mọi module **đọc được** truy cập public type/public member thuộc package `p` bằng truy cập bình thường | Export business API interface cho module client |
| `exports p to m;` | Cho module `m` truy cập public API của package | Giới hạn API dành riêng cho một module |
| `opens p;` | Cho các module khác deep reflection tại runtime vào package `p` | Framework cần phản chiếu constructor/field/method trong app |
| `opens p to m;` | Cấp deep reflection có chọn lọc cho module `m` | Mở package cho module Spring thực sự cần truy cập |
| `--add-exports=A/p=B` | Thêm export cho run/compile phù hợp với cấu hình | Biện pháp tương thích khi không sửa được descriptor của module `A` |
| `--add-opens=A/p=B` | Thêm quyền deep reflection ở runtime | Khắc phục lỗi truy cập cụ thể khi quyền mở package còn thiếu |

**Nguyên tắc:** `exports` không đồng nghĩa `opens`. Quyền truy cập public API không tự cho Spring phép `setAccessible(true)` đối với non-public members. Ngược lại, `opens` mở quyền phản chiếu ở runtime nhưng không có vai trò thay thế API export cho việc biên dịch module client.

### 6.1. Ví dụ mô hình `module-info.java` cho ứng dụng có Spring AOP

Đoạn sau **minh họa thiết kế module**, không phải một project JPMS độc lập có thể dán nguyên vào ví dụ Gradle classpath ở phần 8. Tên package/module Spring và danh sách `requires` phải được điều chỉnh theo module thực tế, dependency và tính năng đang dùng.

```java
module demo.orders {
    requires spring.context;
    requires spring.beans;
    requires spring.aop;

    // Business interface được client module nhìn thấy.
    exports demo.orders.api;

    // Spring được phép phản chiếu vào vùng triển khai.
    // Chọn danh sách module đích bằng evidence từ stack trace
    // và phụ thuộc Spring thực tế.
    opens demo.orders.internal
        to spring.core, spring.beans, spring.context, spring.aop;
}
```

Ở đây:
- `exports demo.orders.api` đặt interface ở API boundary.
- `demo.orders.internal` không trở thành public API chỉ vì Spring có thể reflect vào nó.
- `opens ... to ...` là một **qualified opens**: chỉ liệt kê các module cần thiết thay vì mở cho toàn bộ thế giới.
- Cấu hình Spring có thể còn cần mở package chứa `@Configuration`, aspect hoặc những class được tạo/khởi tạo bằng reflection. Phải kiểm tra package thật, không mặc định chỉ cần mở `internal`.
- Phần khai báo module không thể đảm bảo proxy CGLIB được tạo thành công trong mọi classloader hoặc cho mọi target class; kiểm tra đặc điểm class và runtime.

### 6.2. Ví dụ JVM option khi một module đóng package của ứng dụng

Với ứng dụng named module `demo.orders`, nếu runtime báo Spring module `spring.core` thiếu quyền deep reflection vào `demo.orders.internal`, một lựa chọn tương đương với việc sửa module descriptor (khi đang kiểm tra) là:

```bash
java --add-opens=demo.orders/demo.orders.internal=spring.core \
     --module-path "<module-path-thuc-te>" \
     --module demo.orders/demo.orders.Main
```

Đây là **lệnh minh họa cấu trúc**, không phải lệnh chạy hoàn chỉnh: `<module-path-thuc-te>` phải được thay bằng module path thật và `demo.orders.Main` phải tồn tại trong ứng dụng. Không dùng lệnh này như kết quả đã test cho mọi Spring app. Nếu cần nhiều module Spring khác nhau có reflective access, phải xác minh từng module đích từ exception cụ thể.

Khi có quyền chỉnh source của ứng dụng, `module-info.java` là nơi mô tả quyền ổn định dễ review hơn so với thêm nhiều JVM flags không rõ mục đích.

## 7. Ví dụ Spring AOP chạy được để so sánh JDK proxy và CGLIB

Để kiểm tra **proxy type** trước khi đưa JPMS vào, ví dụ sau chạy theo **classpath**, dùng Java 17 và Spring Framework 6.1.14. Nó cố tình dùng hai context **tách biệt**: một context chỉ cho phép JDK proxy khi có interface và một context ép CGLIB. Không có `module-info.java` trong project này.

### 7.1. Cấu trúc file

```text
spring-aop-proxy-access/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/jpms/
    ├── Gateway.java
    ├── GatewayImpl.java
    ├── TraceAspect.java
    └── Demo.java
```

`settings.gradle`:

```groovy
rootProject.name = 'spring-aop-proxy-access'
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
    mainClass = 'demo.jpms.Demo'
}
```

### 7.2. Interface cho JDK proxy

`src/main/java/demo/jpms/Gateway.java`:

```java
package demo.jpms;

public interface Gateway {
    String transfer(String id);
}
```

### 7.3. Target triển khai phương thức

`src/main/java/demo/jpms/GatewayImpl.java`:

```java
package demo.jpms;

public class GatewayImpl implements Gateway {

    @Override
    public String transfer(String id) {
        System.out.println("target:" + id);
        return "ok:" + id;
    }
}
```

Target không `final` và `transfer` là public non-final, nên có thể được proxy theo lớp hoặc interface. Điều này tách biệt được vấn đề **kiểu proxy** khỏi các lỗi do `final`.

### 7.4. Aspect làm dấu hiệu advice đã chạy qua proxy

`src/main/java/demo/jpms/TraceAspect.java`:

```java
package demo.jpms;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {

    @Before("execution(public * demo.jpms.GatewayImpl.transfer(..))")
    public void beforeTransfer(JoinPoint joinPoint) {
        System.out.println("aspect:" + joinPoint.getSignature().getName());
    }
}
```

`TraceAspect` là Spring `@Aspect`. Bài dùng pointcut khớp method của **target class**; Spring sẽ áp dụng advice cho lời gọi đi qua proxy. Aspect được đăng ký bằng `@Bean` ở `Demo`, không cần `@Component`.

### 7.5. Hai context với lựa chọn proxy khác nhau

`src/main/java/demo/jpms/Demo.java`:

```java
package demo.jpms;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public class Demo {

    @Configuration(proxyBeanMethods = false)
    public static class CommonBeans {

        @Bean
        public Gateway gateway() {
            return new GatewayImpl();
        }

        @Bean
        public TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CommonBeans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class JdkConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(CommonBeans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class CglibConfig {
    }

    public static void main(String[] args) {
        show("JDK", JdkConfig.class);
        show("CGLIB", CglibConfig.class);
    }

    private static void show(String label, Class<?> config) {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(config)) {

            Gateway bean = context.getBean(Gateway.class);

            System.out.println(label + " jdk=" +
                    AopUtils.isJdkDynamicProxy(bean) +
                    " cglib=" + AopUtils.isCglibProxy(bean) +
                    " implementsClass=" + (bean instanceof GatewayImpl));

            String result = bean.transfer(label.toLowerCase());
            System.out.println(label + " result=" + result);
        }
    }
}
```

Hai `ApplicationContext` được tạo mới rồi đóng riêng, giúp cấu hình `proxyTargetClass=true` của case CGLIB không ảnh hưởng case JDK. `@Import(CommonBeans.class)` đăng ký bean cần thiết trong context đang xét; `@EnableAspectJAutoProxy` tạo hạ tầng AOP của từng context.

### 7.6. Chạy và đối chiếu stdout

Tại thư mục chứa `build.gradle`, dùng Gradle cài đặt sẵn:

```bash
gradle run
```

Nếu project đã có Gradle Wrapper thì dùng `./gradlew run` trên Linux/macOS hoặc `gradlew.bat run` trên Windows.

Các dòng **do Java code in ra**, không tính Gradle/logging của môi trường:

```text
JDK jdk=true cglib=false implementsClass=false
aspect:transfer
target:jdk
JDK result=ok:jdk
CGLIB jdk=false cglib=true implementsClass=true
aspect:transfer
target:cglib
CGLIB result=ok:cglib
```

Ý nghĩa:
- `implementsClass=false` cho JDK proxy: object là implementation của `Gateway`, nhưng **không là instance của `GatewayImpl`**. Trên module path, việc có public exported API interface thuận lợi cho cách sử dụng này.
- `implementsClass=true` cho CGLIB: proxy là subclass của `GatewayImpl`. Khi chuyển sang named modules, việc tạo subclass và quyền truy cập vào target class trở thành vấn đề cần kiểm tra.
- Hai case đều chạy `aspect:transfer`: khác proxy type **không làm mất advice**, miễn pointcut phù hợp và lời gọi đi qua proxy.
- Bài ví dụ classpath trên **chưa chứng minh cấu hình JPMS thật đã đủ `exports`/`opens`**; cần kiểm thử tiếp khi đóng gói vào named module.

## 8. Chuyển sang module path: checklist kiểm tra có hệ thống

Khi ứng dụng chạy ở classpath nhưng lỗi ở module path, tiến hành theo thứ tự để xác định nguyên nhân thực:

### Bước 1 — Xác định module và cách chạy

Kiểm tra có `module-info.java` và thực sự khởi chạy bằng `--module-path` / `--module` hay không. Đừng suy từ việc dùng JDK 17 rằng tất cả application classes đã thuộc named modules. Các ứng dụng chạy bằng classpath thông thường thuộc unnamed module và có ranh giới truy cập khác.

### Bước 2 — Xác định bean cần proxy và proxy type

Từ lỗi nested cause hoặc điểm startup thất bại, tìm bean Spring đang được advice. Nếu context khởi động được thì inspect:

```java
Object bean = context.getBean("gateway");
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
```

Nếu Spring đang tạo CGLIB proxy cho class mà bạn có thể expose qua interface, cân nhắc thiết kế interface và đặt `proxyTargetClass=false` **trong toàn bộ cấu hình liên quan**. Việc một Spring subsystem khác yêu cầu class-based proxy có thể ghi đè lựa chọn riêng ở `@EnableAspectJAutoProxy`.

### Bước 3 — Kiểm tra method/class trước khi sửa modules

Trước cả `--add-opens`, xem target có `final` class hoặc `final`/`private` method không. Với CGLIB, đây là giới hạn kế thừa Java và không thể đổi bằng JPMS flag. Với JDK proxy, bảo đảm method cần gọi được khai báo trên interface mà proxy triển khai.

### Bước 4 — Kiểm tra tính đọc được và exported API

Nếu lỗi compile-time hoặc `IllegalAccessError` cho public API, xem:
- Client module có `requires <api-module>` phù hợp không?
- Package chứa public interface/class có `exports` cho client không?
- Interface trong một package không export có vô tình được dùng làm API của service module khác không?

Đừng giải quyết nhu cầu public API bằng cách thêm `opens` rồi kỳ vọng Java compiler xem package đó như đã export.

### Bước 5 — Kiểm tra deep reflection

Nếu `InaccessibleObjectException` thông báo một module `does not "opens ..."`, đọc đủ tên **module nguồn, package, module đích**. Xem nơi Spring đang reflect:
- `@Configuration` và bean factory method;
- non-public constructor/field/method của Spring-managed bean;
- method/annotation mà Spring AOP hoặc cơ chế Spring khác phải introspect.

Thêm `opens package to moduleA, moduleB` vào module descriptor **khi có quyền chỉnh source** và phù hợp nhu cầu. Với môi trường không sửa được descriptor, dùng `--add-opens` có phạm vi hẹp để xác nhận giả thuyết trước.

### Bước 6 — Nếu vẫn không tạo được CGLIB class, xét ClassLoader

Khi lỗi liên quan `defineClass`, `MethodHandles.Lookup` hoặc package JDK core, kiểm tra target được nạp bởi classloader nào và lớp proxy cần được định nghĩa ở đâu. Trong JPMS chặt, **mở package chưa bảo đảm có quyền tạo lớp trong classloader không liên quan**. Spring Framework từng ghi nhận giới hạn này đối với một số kiểu JDK core; giải pháp thiết kế thường là tránh subclass chúng, chuyển proxy sang interface hoặc thay đổi boundary của Spring bean.

### Bước 7 — Kiểm tra lời gọi có thực sự đi qua proxy không

Sau khi startup thành công, nếu advice vẫn không chạy:
- Bean đã được Spring quản lý và `@Aspect` đã đăng ký thành bean?
- AOP auto-proxy được bật trong **đúng `ApplicationContext`**?
- Pointcut có khớp method của target?
- Client gọi từ ngoài qua proxy hay gọi `this.method()` trong target?
- Method có thực sự được JDK interface proxy expose hoặc CGLIB override?

**JPMS chỉ là một lớp rào cản.** Một lỗi Spring AOP xảy ra trên module path vẫn có thể do cấu hình pointcut hoặc self-invocation bình thường.

## 9. So sánh hướng xử lý theo từng tình huống

| Tình huống | Giải pháp nên xem trước | Điều không nên làm |
| --- | --- | --- |
| Chỉ cần advice trên các method công khai trong interface | Giữ interface public, export package API và thử JDK proxy | Ép CGLIB cho một `final` implementation không thể kế thừa |
| Bean không có interface và cần CGLIB | Bảo đảm class không `final`, method override được, và quyền module/runtime cho phép tạo subclass | Dùng `--add-opens` như cách mở khóa `final` |
| Spring báo không reflect được non-public constructor/method | Mở đúng package cho module Spring cần thiết bằng `opens`/`--add-opens` | Export mọi package nội bộ hoặc mở rộng `ALL-UNNAMED` tùy tiện |
| Compile module client không tham chiếu được interface | Thiết kế `exports` API và `requires` đúng | Chỉ `opens` mà không export API |
| JDK proxy không cast được sang implementation class | Inject/lookup theo interface; xem cơ chế nào đang quyết định proxy type | Cast ép hoặc phụ thuộc tên class proxy ngẫu nhiên |
| Spring cố subclass một JDK core class dưới named module | Đánh giá lại target/proxy design, dùng interface/adapter do ứng dụng sở hữu | Mở toàn bộ `java.base` với kỳ vọng sửa mọi hạn chế |
| Lời gọi nội bộ không được aspect advice | Đưa boundary sang bean cộng tác và gọi qua Spring proxy | Sửa `module-info.java` khi vấn đề là self-invocation |
| Spring Boot dùng CGLIB dù đã đặt AOP `false` | Xem property, transaction/caching settings và proxy thực tế trong context | Suy luận theo một annotation mà bỏ qua cấu hình khác |

## 10. Thiết kế Spring AOP thân thiện JPMS

Một kiến trúc rõ ràng thường có:

```text
module demo.orders
│
├── demo.orders.api
│   └── OrderGateway          [public interface, exported khi cần]
│
├── demo.orders.internal
│   └── DefaultOrderGateway   [class triển khai, giữ riêng]
│
├── demo.orders.aop
│   └── AuditAspect           [Spring bean @Aspect]
│
└── demo.orders.config
    └── SpringConfiguration   [@Configuration, register bean]

module-info.java:
  requires những module thật sự dùng;
  exports các package API cần cho client;
  opens các package Spring cần reflect tới module cụ thể;
  không mở phần nội bộ nếu không có nhu cầu.
```

Khi Spring có thể dùng JDK proxy qua interface API, bạn thường giảm được áp lực phải subclass implementation class. Tuy nhiên, điều đó **không loại bỏ** nhu cầu reflection của Spring IoC (nếu cấu hình/constructor/member của bean cần được inspect) hoặc việc client module phải đọc module cung cấp API.

Ngược lại, nếu thật sự cần advice method không nằm trên interface, bạn phải đánh giá class-based proxy theo cả hai trục: *khả năng Java override* và *khả năng module/runtime tạo subclass*. Đừng đánh đổi JPMS encapsulation chỉ để thuận tiện cast bean về class triển khai mà không có yêu cầu kỹ thuật chính đáng.

## 11. Những điều cần ghi nhớ

- **Spring AOP là proxy-based**: JDK proxy đứng ở interface; CGLIB đứng ở subclass. Method phải được gọi qua proxy thì advice mới chạy.
- **`module-info.java` tăng tính đóng gói**: `requires`, `exports` và `opens` có vai trò khác nhau; chỉ mở đúng package/module mà Spring cần.
- **CGLIB chịu hai loại hạn chế**: Java không cho override `private`/`final` và JPMS có thể ngăn định nghĩa subclass động.
- **JDK proxy không phải “bỏ qua JPMS”**: public API interface và quyền truy cập giữa các module vẫn phải hợp lệ; JDK tự có quy tắc package/module cho proxy class.
- **`--add-opens=java.base/java.lang=ALL-UNNAMED` không phải thuốc chữa chung**: nó nhắm vào unnamed modules và không giải quyết mọi trường hợp named module hoặc classloader.
- **Luôn kiểm tra runtime thực**: proxy type, nested exception, context sở hữu bean, advisor match và đường gọi; từ đó chỉnh module descriptor hoặc thiết kế proxy một cách có căn cứ.

### Tài liệu tham khảo

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB, `final`/`private`, giới hạn Java Module System, self-invocation.
- [Spring Framework — Component Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html): quyền `exports`/`opens` khi dùng Spring trên module path.
- [JDK 17 — java.lang.reflect.Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): quy tắc package/module của JDK dynamic proxy class.
- [Dev.java — Opening for Reflection](https://dev.java/learn/organizing/modules/opening-for-reflection/): khác biệt `exports`/`opens` và quyền truy cập phản chiếu.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): khai báo `@Aspect` và bật Spring AOP.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-011">equals, hashCode, toString và hành vi cần kiểm chứng trên proxy</a>

<details>
<summary>Xem chi tiết</summary>

# equals, hashCode, toString và hành vi cần kiểm chứng trên proxy Spring AOP

## 1. Vì sao ba phương thức Object cần được kiểm tra khi dùng Spring AOP?

Một Spring bean nghiệp vụ có thể được `ApplicationContext` trao cho caller dưới dạng **AOP proxy**, thường là **JDK dynamic proxy** hoặc **class-based proxy (CGLIB)**. Vì vậy, lời gọi `equals(Object)`, `hashCode()` và `toString()` trên biến được inject không nhất thiết có cùng đường đi hoặc cùng kết quả với lời gọi trực tiếp trên **raw target**.

Đây là vấn đề của **Spring AOP và Spring-managed beans**, không phải một bài tổng quan về hợp đồng `Object` của Java. Điều cần biết khi cấu hình hoặc debug aspect là:

- Hai tham chiếu lấy từ context đang trỏ tới cùng **Spring proxy** hay tới các proxy khác?
- `equals()` có đang so sánh cấu hình proxy hoặc sử dụng quy tắc đặc biệt của Spring thay vì so sánh dữ liệu nghiệp vụ bên trong target?
- `hashCode()` có ổn định cho một proxy hay không; có an toàn nếu dùng proxy làm key trong `HashMap`/`HashSet`?
- `toString()` được dispatch thế nào; in log một proxy có kích hoạt advice hoặc truy cập dữ liệu nhạy cảm hay không?
- Kết quả có khác khi đổi từ JDK proxy sang CGLIB, hoặc khi target override ba phương thức này?
- Có thể dùng những phép kiểm tra nào **mà không suy luận sai từ tên class proxy hoặc một vài kết quả tình cờ**?

Kết luận định hướng: **Spring AOP proxy có danh tính và lớp hành vi riêng**. Không đồng nhất `proxy.equals(other)` với `target.equals(other)`, không đồng nhất `proxy.hashCode()` với `target.hashCode()`, và không coi chuỗi `proxy.toString()` là bằng chứng đầy đủ về cấu trúc proxy.

## 2. Nhắc lại ranh giới giữa proxy và target

```text
  Bean gọi từ ApplicationContext
             |
             v
       Spring AOP proxy
        /           \
JDK interface      CGLIB subclass
       \             /
        \           /
         v         v
  Spring interceptor/advisor handling
             |
             v
       Target bean method
```

Với Spring Framework thuần, target có business interface thường dẫn tới JDK proxy; nếu không có interface phù hợp thì class-based proxy. Thiết lập `proxyTargetClass = true` yêu cầu CGLIB; Spring Boot có thể chọn mặc định khác theo cấu hình auto-proxy.

Điều quan trọng cho ba phương thức `Object` là **caller gọi trên đối tượng proxy**, và Spring quyết định có xử lý riêng hay chuyển tiếp xuống target. Đường đi chính xác còn phụ thuộc phiên bản Spring, loại proxy, interfaces được proxy, `Advisor`, `TargetSource`, method override và các tối ưu hạ tầng.

Không nên dùng công thức “mọi lời gọi trên proxy đều đi qua target rồi mới có kết quả” hoặc “mọi method trên proxy đều kích hoạt advice”. Cả hai đều bỏ qua cơ chế dispatch thực sự của Spring AOP.

## 3. `equals(Object)` trên Spring AOP proxy

### 3.1. Trường hợp JDK dynamic proxy

Trong triển khai `JdkDynamicAopProxy` của Spring, `equals` có **nhánh xử lý riêng** khi không có một interface được proxy khai báo method `equals(Object)` tương ứng. Khi đó `equals` được xử lý bởi phần proxy, thay vì mặc định gọi thẳng `equals` của target.

Ý nghĩa thực tế:

- `proxy.equals(proxy)` phải trả `true` (tính phản xạ).
- Việc hai proxies cùng bao một target object **không tự bảo đảm** `proxy1 == proxy2` hoặc `proxy1.equals(proxy2)`.
- Spring có thể so sánh các đặc tính cấu hình proxy như **các interface, advisor chain và `TargetSource`** khi xác định hai AOP proxies có tương đương theo cơ chế Spring hay không.
- `proxy.equals(rawTarget)` không có nghĩa giống `rawTarget.equals(rawTarget)`.
- Khi interface được proxy **có khai báo lại `equals(Object)`**, nhánh dispatch của JDK proxy có thể khác; cần kiểm tra đúng phiên bản và cấu hình thay vì mặc định áp dụng một quy tắc cho mọi interface.

Không phải lúc nào Spring cũng dùng quy tắc **identity của Java object** cho `equals` trên proxy. Nhưng cũng không có bảo đảm rằng nó sẽ áp dụng **domain equality của entity** theo logic do target tự định nghĩa. Đây là lý do nên viết test cho trường hợp thực tế thay vì đoán.

### 3.2. Trường hợp CGLIB class proxy

Spring CGLIB AOP có **EqualsInterceptor** dành cho `equals(Object)` khi method có thể được override và interceptor được cài đặt phù hợp. Nó có thể xử lý so sánh theo danh tính proxy và cấu hình proxy/advisor/target source, không nhất thiết gọi `target.equals(...)`.

Ví dụ hai Spring proxies có cùng class runtime hoặc cùng loại target **chưa đủ** để kết luận chúng `equals` nhau. Số bean, scope, target source và advisor configuration đều là thông tin có liên quan.

Một **cảnh báo kỹ thuật**: class-based proxy không override được method `final` (bao gồm `final equals`), và không thể subclass class `final`. Nếu bean có các ràng buộc đó, không thiết kế test dựa trên giả định mọi lời gọi Object methods sẽ được Spring CGLIB intercept bình thường.

### 3.3. Không lấy domain equality của target làm hợp đồng proxy

Giả sử target chứa `id = "INV-10"` và override `equals` để so sánh theo id. Điều này **không chứng minh** hai references tới AOP proxies sẽ so sánh bằng nhau chỉ vì hai raw targets có cùng id.

```java
// Fragment minh họa; các biến lấy từ cấu hình Spring thực tế:
boolean sameReference = proxyA == proxyB;
boolean sameByProxyEquals = proxyA.equals(proxyB);
boolean sameBusinessId = idA.equals(idB);
```

Ba biểu thức kiểm tra **ba quan hệ khác nhau**:

1. `==`: cùng một Java object reference.
2. `equals` trên AOP proxy: semantics thực tế của Spring proxy trong cấu hình cụ thể.
3. So sánh **business key**: domain identity mà nghiệp vụ chủ động lựa chọn.

Nếu chương trình cần gom các giao dịch theo business id, hãy lấy **id bất biến** để làm key, hoặc dùng một value object chuyên biệt; đừng lấy sự bằng nhau của các AOP proxies làm tiêu chuẩn nghiệp vụ mặc định.

## 4. `hashCode()` trên Spring AOP proxy

`hashCode()` có quan hệ trực tiếp với `equals()`: nếu hai đối tượng được coi là bằng nhau, hash code cần bằng nhau. Một AOP proxy cũng phải tôn trọng hợp đồng này trong phạm vi semantics mà Spring áp dụng.

### 4.1. JDK proxy

Trong `JdkDynamicAopProxy` của Spring, nếu không có proxied interface nào khai báo `hashCode()` thì Spring có **nhánh xử lý `hashCode` của proxy**. Triển khai Spring có thể tính giá trị dựa trên loại handler/đặc điểm `TargetSource`, không phải lấy nguyên `hashCode()` của target.

**Không assert một số nguyên hash cố định.** Hash code phụ thuộc đối tượng, identity/cấu hình/implementation và có thể khác giữa lần chạy JVM. Phép kiểm hợp lý là tính ổn định trong lúc proxy và các dữ liệu liên quan không thay đổi.

### 4.2. CGLIB proxy

Trong cơ chế Spring CGLIB thông thường, `HashCodeInterceptor` chuyên trách xử lý `hashCode()` trên proxy. Điều này đi cùng xử lý đặc biệt của `EqualsInterceptor`.

Khi target implement `hashCode` dựa trên business id, `proxy.hashCode()` **không được giả định** là giá trị hash do target tính. Không assert `proxy.hashCode() == rawTarget.hashCode()` trừ khi đó là hợp đồng cụ thể đã được xác minh trên nền tảng đang triển khai và thực sự cần.

### 4.3. Dùng proxy làm key cho `HashMap` hoặc `HashSet`

Một proxy được tạo và sử dụng ổn định có thể làm key theo semantics của nó, nhưng cần cân nhắc:

- **Scope:** singleton bean thường trả cùng Spring reference trong một context; prototype beans tạo các instances khác nhau; scoped proxy có thể là một reference ổn định trong khi target phía dưới thay đổi theo request.
- **Thay đổi proxy configuration:** hai proxies khác advisors, interfaces hoặc target sources có thể không có equality tương đương.
- **Mutability:** tránh thay đổi trạng thái/cấu hình tham gia `equals`/`hashCode` trong thời gian object nằm trong một hash collection.
- **Mục đích key:** nếu business key là `orderId`, lưu `orderId`, không lưu `OrderService` proxy và suy ra business identity từ nó.
- **Cross-context:** singleton theo Spring context không có nghĩa cùng reference hoặc cùng proxy equality ở context khác.

Mẫu phù hợp cho bộ nhớ cache nghiệp vụ:

```java
Map<String, String> resultsByOrderId = new HashMap<>();
resultsByOrderId.put("ORD-7", "processed");
```

Đây là fragment cần import `java.util.Map` và `java.util.HashMap`. Giá trị key `"ORD-7"` có ý nghĩa nghiệp vụ rõ hơn nhiều so với `OrderService` proxy.

## 5. `toString()` trên Spring AOP proxy

Khác với việc Spring AOP có nhánh riêng dành cho `equals/hashCode`, **`toString()` thường được xử lý theo đường dispatch method của proxy**. Khi target override `toString()`, Spring proxy có thể chuyển lời gọi đến target hoặc đi qua interceptor chain theo cấu hình.

Không nên gộp ba method vào một quy tắc “Spring luôn override mọi Object method theo cùng cách”.

Các điểm quan trọng:

- `proxy.toString()` có thể cho chuỗi mà target khai báo, nhưng **không phải API bảo đảm trả kiểu class runtime của proxy**.
- Một `toString()` mặc định hoặc được override có thể hiển thị giá trị khác nhau tùy class target, proxy style và advice hiện có.
- Nếu pointcut/advisor được thiết kế **có thể khớp `toString()`**, lời gọi có khả năng bị can thiệp; một pointcut **chỉ** chọn `OrderService.process(..)` thì không tự chuyển mọi lời gọi `toString()` thành `process` advice.
- `toString()` không phải cơ chế quản lý lifecycle và không phải công cụ chuẩn để liệt kê advisor chain.
- In một proxy bằng `logger.info("bean={}", bean)` hoặc nối chuỗi có thể dẫn tới lời gọi `toString()`; nếu target `toString()` truy cập lazy data, remote service hoặc thông tin nhạy cảm, logging có thể tốn chi phí và rò rỉ dữ liệu.

Ưu tiên logging **metadata được chọn rõ** (bean name, class target từ API Spring chẩn đoán, request id đã được kiểm soát) thay vì log toàn bộ `toString()` của một bean có trạng thái.

### 5.1. `getClass().getName()` khác `toString()`

```java
String runtimeClass = proxy.getClass().getName();
String printable = proxy.toString();
```

- `runtimeClass`: tên class thực sự của object đang giữ; có thể là lớp `jdk.proxy...` hoặc lớp sinh bằng Spring CGLIB.
- `printable`: chuỗi do dispatch `toString()` quyết định; có thể từ target hoặc advice tùy cấu hình.

Hai giá trị không cần trùng nhau. Tên generated proxy class có thể thay đổi theo JDK/Spring version nên không nên assert chính xác toàn bộ tên class. Dùng `AopUtils.isJdkDynamicProxy`, `AopUtils.isCglibProxy` và `AopUtils.getTargetClass` để kiểm tra đúng mục đích.

## 6. Bảng so sánh khi kiểm tra hành vi Object methods

| Phép kiểm | JDK Spring AOP proxy | CGLIB Spring AOP proxy | Cách kiểm tra an toàn |
| --- | --- | --- | --- |
| `proxy == proxy` | `true` | `true` | Phép kiểm identity Java. |
| `proxy.equals(proxy)` | Kỳ vọng `true` | Kỳ vọng `true` | Assert phản xạ trên bean thật từ context. |
| `proxy.hashCode()` gọi hai lần | Kỳ vọng ổn định khi state/config không đổi | Kỳ vọng ổn định khi state/config không đổi | So sánh hai giá trị trong cùng test; không hardcode số. |
| `proxy.equals(rawTarget)` | Không mặc định bằng `true` | Không mặc định bằng `true` | Đo trong context cụ thể; tránh dựa vào raw-target identity. |
| `proxy1.equals(proxy2)` | Phụ thuộc semantics/cấu hình | Phụ thuộc semantics/cấu hình | Kiểm tra rõ các proxies/target sources, không phỏng đoán. |
| `proxy.toString()` | Có thể dispatch tới target | Có thể dispatch tới target | Đặt mục đích `toString` rõ ràng, kiểm tra output và advice. |
| `proxy.getClass()` | JDK generated proxy class | CGLIB generated subclass | Dùng `AopUtils` thay vì dựa vào tên generated class. |
| `proxy instanceof ConcreteService` | Thường `false` nếu dùng interface-based proxy | Thường `true` nếu class proxy hợp lệ | Test injection/cast theo hợp đồng interface hoặc class. |

Bảng mô tả **Spring AOP theo proxy thông thường**. Mỗi assert về kết quả business cần chạy trên **Spring version, bean scope, advice chain và proxy settings** của dự án. Không coi một giá trị quan sát ở project demo là hợp đồng tương thích vĩnh viễn của Spring internals.

## 7. Ví dụ Java 17+ chạy được: đo ba phương thức trên JDK và CGLIB

Ví dụ xây dựng **hai `ApplicationContext` độc lập** trên cùng business interface và aspect:

- Context `JDK` dùng `@EnableAspectJAutoProxy(proxyTargetClass=false)`.
- Context `CGLIB` dùng `@EnableAspectJAutoProxy(proxyTargetClass=true)`.
- `ReportOperations` là business interface; `ReportService` implement interface đó.
- Aspect chỉ advice `runReport()`, **không chọn `equals`, `hashCode`, `toString`**.
- `ReportService.toString()` trả chuỗi cố định nhằm quan sát dispatch mà không phụ thuộc giá trị hash hay tên class được sinh.

Để tránh khẳng định những equality internals không được hứa hẹn, chương trình chỉ in các đặc tính ổn định/có thể giải thích: type proxy, phản xạ `equals`, hash ổn định qua hai lần gọi, `toString`, và số lần advice.

### 7.1. Cấu trúc project

```text
spring-proxy-object-methods/
├── pom.xml
└── src/main/java/com/example/proxycheck/
    ├── ReportOperations.java
    ├── ReportService.java
    ├── TraceAspect.java
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
    <artifactId>spring-proxy-object-methods</artifactId>
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

`spring-context` quản lý bean và kéo theo Spring AOP trong dependency graph của bản mẫu, còn `aspectjweaver` cung cấp phần thư viện cần thiết để Spring hiểu khai báo `@Aspect`/pointcut. Mã này dùng **Spring proxy AOP**, không cấu hình AspectJ weaving agent.

### 7.3. `src/main/java/com/example/proxycheck/ReportOperations.java`

```java
package com.example.proxycheck;

public interface ReportOperations {
    String runReport(String reportId);
}
```

Interface **không tự khai báo** `equals(Object)` hoặc `hashCode()`: đây là trường hợp phổ biến để quan sát xử lý riêng của Spring JDK proxy.

### 7.4. `src/main/java/com/example/proxycheck/ReportService.java`

```java
package com.example.proxycheck;

public class ReportService implements ReportOperations {

    @Override
    public String runReport(String reportId) {
        System.out.println("[TARGET] run " + reportId);
        return "report:" + reportId;
    }

    @Override
    public String toString() {
        return "ReportService(target)";
    }
}
```

Target chỉ override `toString`. `equals` và `hashCode` giữ cách triển khai từ `Object`, giúp bài demo tập trung vào khác biệt **proxy handling** mà không trộn thêm domain equality.

### 7.5. `src/main/java/com/example/proxycheck/TraceAspect.java`

```java
package com.example.proxycheck;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TraceAspect {
    private final AtomicInteger count = new AtomicInteger();

    @Before("execution(public * com.example.proxycheck.ReportService.runReport(..))")
    public void beforeRun() {
        count.incrementAndGet();
        System.out.println("[ADVICE] before runReport");
    }

    public int count() {
        return count.get();
    }
}
```

Pointcut chọn rõ một method nghiệp vụ. Việc gọi `equals`, `hashCode` và `toString` **không nhằm kích hoạt advice này**. `AtomicInteger` giúp kiểm tra chính xác số lời gọi trong ví dụ.

### 7.6. `src/main/java/com/example/proxycheck/DemoApp.java`

```java
package com.example.proxycheck;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

public class DemoApp {

    @Configuration
    static class SharedBeans {
        @Bean
        ReportOperations reportOperations() {
            return new ReportService();
        }

        @Bean
        TraceAspect traceAspect() {
            return new TraceAspect();
        }
    }

    @Configuration
    @Import(SharedBeans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class JdkConfig {
    }

    @Configuration
    @Import(SharedBeans.class)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CglibConfig {
    }

    private static void inspect(String label, Class<?> configClass) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(configClass)) {

            ReportOperations proxy = context.getBean(ReportOperations.class);
            TraceAspect aspect = context.getBean(TraceAspect.class);

            System.out.println("[" + label + "] isJdk="
                    + AopUtils.isJdkDynamicProxy(proxy));
            System.out.println("[" + label + "] isCglib="
                    + AopUtils.isCglibProxy(proxy));
            System.out.println("[" + label + "] equalsSelf="
                    + proxy.equals(proxy));

            int firstHash = proxy.hashCode();
            int secondHash = proxy.hashCode();
            System.out.println("[" + label + "] hashStable="
                    + (firstHash == secondHash));

            System.out.println("[" + label + "] toString="
                    + proxy.toString());
            System.out.println("[" + label + "] adviceBeforeBusiness="
                    + aspect.count());

            String result = proxy.runReport(label);
            System.out.println("[" + label + "] result=" + result);
            System.out.println("[" + label + "] adviceAfterBusiness="
                    + aspect.count());
        }
    }

    public static void main(String[] args) {
        inspect("JDK", JdkConfig.class);
        inspect("CGLIB", CglibConfig.class);
    }
}
```

**Vì sao hai context?** `@EnableAspectJAutoProxy(proxyTargetClass=false)` và `@EnableAspectJAutoProxy(proxyTargetClass=true)` cùng hiệu lực trong một context có thể hợp nhất theo thiết lập mạnh hơn; tách context giúp so sánh đúng hai lựa chọn proxy. Cả hai context đều có một `TraceAspect` riêng và cùng định nghĩa target nghiệp vụ.

### 7.7. Chạy và dự kiến stdout

Với Maven và JDK 17+ tại thư mục có `pom.xml`:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.proxycheck.DemoApp
```

Các dòng do chương trình tự in ra theo thứ tự (bỏ qua log Maven hoặc Spring nếu môi trường có):

```text
[JDK] isJdk=true
[JDK] isCglib=false
[JDK] equalsSelf=true
[JDK] hashStable=true
[JDK] toString=ReportService(target)
[JDK] adviceBeforeBusiness=0
[ADVICE] before runReport
[TARGET] run JDK
[JDK] result=report:JDK
[JDK] adviceAfterBusiness=1
[CGLIB] isJdk=false
[CGLIB] isCglib=true
[CGLIB] equalsSelf=true
[CGLIB] hashStable=true
[CGLIB] toString=ReportService(target)
[CGLIB] adviceBeforeBusiness=0
[ADVICE] before runReport
[TARGET] run CGLIB
[CGLIB] result=report:CGLIB
[CGLIB] adviceAfterBusiness=1
```

Ý nghĩa:

1. **Hai proxy type khác nhau** dù dùng chung source class và aspect. `AopUtils` cung cấp bằng chứng trực tiếp hơn so với so sánh tiền tố tên class.
2. `equalsSelf=true` chứng minh phản xạ ngay trên object Spring trả về, **không chứng minh** `proxy.equals(rawTarget)` hay `proxyA.equals(proxyB)`.
3. `hashStable=true` chứng minh hai lời gọi liên tiếp trong cấu hình ví dụ cho cùng hash; chương trình **không in hash number** vì đó không phải hợp đồng portable.
4. `toString` hiển thị output có chủ đích của target. Nó không trả về tên generated proxy class, và không nên được dùng để phát hiện proxy type.
5. `adviceBeforeBusiness=0` cho biết các lời gọi Object methods ở trên **không kích hoạt `@Before` vốn chỉ chọn `runReport`**.
6. Sau một lời gọi business qua Spring bean proxy, `adviceAfterBusiness=1`, method target chạy một lần và return value giữ nguyên.

Ví dụ không khẳng định rằng `toString()` **không thể** bị advice. Nó chỉ cho thấy **pointcut của ứng dụng đã cố ý chọn riêng business method**.

## 8. Kiểm chứng thêm equality thực tế giữa hai Spring proxies

Một ứng dụng cần so sánh các proxies nên kiểm thử công khai những trường hợp nó sẽ thật sự thực hiện:

```java
// Đoạn minh họa đặt trong test có ApplicationContext context.
Object first = context.getBean("reportOperations");
Object second = context.getBean("reportOperations");

System.out.println("sameReference=" + (first == second));
System.out.println("equalByEquals=" + first.equals(second));
System.out.println("hashEqualIfEquals="
        + (!first.equals(second) || first.hashCode() == second.hashCode()));
```

Với bean singleton như ví dụ, hai lần lookup cùng bean name trong một context bình thường sẽ trả cùng reference; khi đó hai dòng đầu thường là `true`. Nhưng đó là kết quả của **singleton bean lookup** và **proxy identity**, không phải chứng cứ hai *khác nhau* proxies có semantics bằng nhau.

Khi cần đánh giá **hai proxies khác nhau**, thiết lập hai bean definitions riêng hoặc hai Spring contexts; kiểm thử:

```java
boolean leftEqualsRight = left.equals(right);
boolean rightEqualsLeft = right.equals(left);
boolean hashesAgreeWhenEqual =
        !leftEqualsRight || left.hashCode() == right.hashCode();
```

Đây là fragment đòi hỏi biến `left` và `right` trong test. Ngoài `equals`/hash, nên xem xét **symmetry** giữa hai chiều và **transitivity** khi có ba đối tượng. Không hardcode rằng hai proxy khác nhau cùng bọc một loại target bắt buộc bằng nhau.

Nếu target tự override `equals/hashCode`, hãy chạy lại chính test đó dưới JDK proxy và CGLIB; việc target override không có nghĩa cả hai proxy styles sẽ chuyển tiếp method theo cách giống nhau.

### 8.1. Test đúng scope và đường lấy bean

Các trường hợp có giá trị thực tế:

| Tình huống | Điều phải quan sát |
| --- | --- |
| Lấy singleton bean cùng name hai lần | `==`, `equals`, `hashCode`; expected stable reference trong cùng context. |
| Lấy prototype bean hai lần | Hai bean/proxy references và semantics equality; không mặc định chúng bằng nhau. |
| Hai bean names trỏ tới hai target instances | Equality của hai proxies và hash contract nếu bằng nhau. |
| Request/session scoped proxy | Tách reference proxy sống lâu khỏi target thay đổi theo scope. |
| Hai ApplicationContexts độc lập | So sánh sau khi xác định có đúng cùng cấu hình và không dựa vào singleton toàn JVM. |
| Target override Object methods | Kiểm tra symmetry/consistency của proxy, target và các domain value objects liên quan. |

Hãy đánh giá semantics mà ứng dụng thật sự cần. Không cần can thiệp vào internals nếu một business identifier độc lập đủ làm key.

## 9. Những điều dễ sai khi debug ba phương thức trên Spring proxy

### 9.1. “Target override `equals`, vậy proxy cũng dùng y hệt”

**Sai khi coi là quy tắc mặc định.** Spring AOP có thể xử lý `equals` ở hạ tầng proxy, đặc biệt khi interface không khai báo method đó, hoặc khi dùng CGLIB interceptor chuyên dụng. Hãy tách test raw target và test proxy được lấy từ Spring.

### 9.2. “Hai proxy của cùng target class phải bằng nhau”

**Không đủ căn cứ.** Class target chỉ là một yếu tố; interface set, advisors và `TargetSource` có thể tham gia. `proxy1 == proxy2` lại là phép đo identity riêng.

### 9.3. “Hash code bằng nhau chứng minh hai Spring beans là một”

**Không.** Hash collision luôn có thể xảy ra. Dùng `==` để kiểm tra cùng reference, bean name/type để kiểm tra cấu hình, business key để so sánh nghiệp vụ và `equals` theo hợp đồng thực tế. Hash chỉ giúp cấu trúc dữ liệu phân phối bucket, không chứng minh identity.

### 9.4. “In `toString` là đủ biết bean có bị proxy”

**Không.** Target có thể override `toString` và trả một chuỗi cố định như demo. Dùng:

```java
boolean aop = org.springframework.aop.support.AopUtils.isAopProxy(bean);
boolean jdk = org.springframework.aop.support.AopUtils.isJdkDynamicProxy(bean);
boolean cglib = org.springframework.aop.support.AopUtils.isCglibProxy(bean);
```

Đây là fragment cần một biến `bean` đã lấy từ container. Có AOP proxy vẫn **không đảm bảo** một method cụ thể được advice; cần kiểm tra pointcut và lần gọi.

### 9.5. “`toString` vô hại, có thể log mọi Spring bean”

**Không luôn đúng.** `toString` của target có thể chứa object graph lớn, property bí mật, lazy-loaded association hoặc logic tính toán. Một số advice còn có thể khớp method đó khi pointcut quá rộng. Trong production, logging chọn thông tin đã lọc là lựa chọn an toàn và dễ kiểm soát hơn.

### 9.6. “CGLIB luôn chặn mọi Object method”

**Không.** Class proxy chịu giới hạn của inheritance và khả năng override; `final` methods, `private` methods hoặc class `final` có hạn chế. Riêng `equals/hashCode` còn có nhánh triển khai đặc biệt. Không chuyển từ JDK sang CGLIB chỉ vì muốn “đảm bảo” domain equality, vì đó không phải trách nhiệm của việc chọn proxy type.

### 9.7. “Không có dòng `@Before` nghĩa là Object methods không đi qua proxy”

**Sai về cách suy luận.** Advice có thể chỉ chọn business method. Một Object method vẫn có thể được xử lý bởi proxy mà không khớp advisor đó. Khi cần xác định luồng dispatch, xem các advisors/pointcuts và kiểm thử riêng từng method.

## 10. Checklist khi refactor AOP hoặc nâng phiên bản Spring

1. **Xác định đúng proxy type** từ bean đã inject, bằng `AopUtils.isJdkDynamicProxy`/`isCglibProxy`.
2. **Xác định bean scope và reference semantics:** singleton, prototype, scoped proxy; phân biệt proxy và target.
3. **Kiểm tra `equals` phản xạ và đối xứng** trên đúng các tổ hợp object ứng dụng có ý định so sánh.
4. **Kiểm tra `hashCode` ổn định** và hợp đồng `equals => same hash`; không assert hash number cụ thể.
5. **Kiểm tra `toString` có output hợp lệ**, không phát sinh side effects không mong muốn, không làm lộ thông tin nhạy cảm.
6. **Đo số lần advice** trước/sau khi gọi `equals/hashCode/toString`, nhất là khi dùng pointcut rộng hoặc advisor đặc biệt.
7. **Đối chiếu JDK vs CGLIB** nếu thay `proxyTargetClass`, thay interface, bật Spring Boot auto-config hoặc nâng phiên bản framework.
8. **Không lấy proxy equality làm business identity** nếu yêu cầu chính là so theo id của domain object.
9. **Kiểm tra usage trong collections/caches**: nếu đang dùng proxy làm key, xác định lifecycle của key và tính ổn định theo cấu hình thật.
10. **Đưa các quan sát vào automated tests** chạy trên Spring context giống production; phân biệt sự bảo đảm từ Java object contract với chi tiết hiện thực nội bộ của một phiên bản Spring.

### Tài liệu đối chiếu

- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — AOP Proxies: https://docs.spring.io/spring-framework/reference/core/aop/introduction-proxies.html
- Spring Framework — Spring AOP APIs: https://docs.spring.io/spring-framework/reference/core/aop-api.html
- Spring Framework — JdkDynamicAopProxy source: https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/JdkDynamicAopProxy.java
- Spring Framework — CglibAopProxy source: https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/CglibAopProxy.java

**Khi làm việc với Spring AOP, hãy coi ba phương thức `equals`, `hashCode`, `toString` là những lời gọi có ngữ nghĩa cần kiểm chứng ngay trên proxy thực tế.** Điều này ngăn những giả định sai về identity, tránh cache bugs và giúp phân biệt rõ chính sách advice với cơ chế object dispatch của Spring.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jdk-cglib-proxies-012">Tình huống lỗi ép kiểu khi chuyển cơ chế proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Tình huống lỗi ép kiểu khi chuyển cơ chế proxy trong Spring AOP

## 1. Hiện tượng: bean vẫn hoạt động, nhưng cast sang lớp triển khai bị lỗi

Khi Spring AOP bọc một Spring bean bằng proxy, **kiểu runtime của reference mà caller nhận được có thể không còn là kiểu concrete của target**. Trong cấu hình dùng **JDK dynamic proxy**, proxy triển khai interface mà bean công bố, nhưng **không kế thừa implementation class**. Vì vậy mã ép kiểu từ interface reference sang implementation có thể ném `ClassCastException`, dù method gọi qua interface vẫn kích hoạt advice bình thường.

Ví dụ ý tưởng:

~~~java
PaymentOperations payment = context.getBean(PaymentOperations.class);
payment.pay("A-01");                 // Hợp lệ với JDK proxy và CGLIB proxy

PaymentService impl = (PaymentService) payment;
// ClassCastException nếu payment là JDK dynamic proxy
~~~

Đoạn trên thuộc ứng dụng Spring đã khai báo `PaymentOperations` và `PaymentService`, không phải chương trình chạy độc lập. Nó cho thấy **lỗi thuộc kiểu object runtime**, không phải do method `pay` hay pointcut sai. Ngược lại, nếu Spring tạo **CGLIB class-based proxy**, proxy là subclass của `PaymentService` và có thể cast về `PaymentService` khi class có thể được subclass thành công.

Tình huống thực tế thường xuất hiện sau khi đổi `proxyTargetClass`, sửa cấu hình `@EnableAspectJAutoProxy`, đổi thuộc tính Spring Boot `spring.aop.proxy-target-class`, thêm transaction AOP config hoặc nâng cấu hình Spring khiến proxy đã dùng trước đây được tạo theo cơ chế khác.

**Phạm vi bài:** phân tích lỗi ép kiểu do **Spring AOP proxy**, tái hiện với hai ApplicationContext, cách kiểm tra đúng loại proxy và sửa dependency/call site. Cú pháp `@AspectJ` được dùng chỉ để bật advice trong Spring; bài không bàn sâu AspectJ weaving bên ngoài Spring.

## 2. Hai proxy có quan hệ kiểu khác nhau

Giả sử có:

~~~java
public interface PaymentOperations {
    void pay(String orderId);
}

public class PaymentService implements PaymentOperations {
    @Override
    public void pay(String orderId) {
        System.out.println("SERVICE: paid " + orderId);
    }
}
~~~

Đây là **hai định nghĩa rút gọn**; các file hoàn chỉnh xuất hiện ở phần 4. Spring AOP có hai cách tạo wrapper phổ biến:

~~~text
Target class:
PaymentService implements PaymentOperations

JDK dynamic proxy:
         PaymentOperations
                ^
                |
          jdk.proxy...$Proxy...
                |
         [delegates to target PaymentService]

  proxy instanceof PaymentOperations -> true
  proxy instanceof PaymentService    -> false

CGLIB class proxy:
          PaymentService
                ^
                |
       PaymentService$$SpringCGLIB$$...
                |
         [delegates to target PaymentService]

  proxy instanceof PaymentOperations -> true
  proxy instanceof PaymentService    -> true
~~~

Các tên proxy phía trên chỉ mô tả ý tưởng: phần tiền tố/số hậu tố trong class name có thể đổi theo JDK và Spring version. Hãy kiểm tra bằng `instanceof` và `AopUtils` thay vì so khớp chuỗi tên class.

| Phép kiểm tra trên bean AOP proxy | JDK dynamic proxy | CGLIB class proxy |
| --- | --- | --- |
| `bean instanceof PaymentOperations` | `true` | `true` |
| `bean instanceof PaymentService` | `false` | `true` |
| `((PaymentService) bean)` | `ClassCastException` | Cast thành công nếu proxy đúng target class |
| `AopUtils.isJdkDynamicProxy(bean)` | `true` | `false` |
| `AopUtils.isCglibProxy(bean)` | `false` | `true` |
| `AopUtils.isAopProxy(bean)` | `true` | `true` |
| `bean.getClass() == PaymentService.class` | `false` | `false` |

Điều kiện áp dụng bảng: **cả hai object đều là Spring AOP proxy hợp lệ của cùng loại target**; CGLIB class proxy tạo được và bean thực sự được proxy. Nếu không có advisor nào áp dụng hoặc bean không được container xử lý, đối tượng có thể là target gốc và các kết quả khác đi.

Khi dùng JDK proxy, JVM không cho phép cast xuống `PaymentService` vì proxy không có quan hệ kế thừa với lớp đó. Việc target phía sau proxy là `PaymentService` **không làm chính đối tượng proxy trở thành `PaymentService`**.

## 3. Tại sao đổi cấu hình có thể làm code cũ vỡ?

### 3.1. `proxyTargetClass` chọn cơ chế proxy

Trong Spring Framework thuần:

~~~java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
class InterfaceProxyConfig {
}
~~~

Với target triển khai interface và có advisor phù hợp, Spring **thường** chọn JDK dynamic proxy. `false` nghĩa là không ép class-based proxy; khi target không có interface thích hợp, Spring vẫn có thể dùng CGLIB.

Cấu hình khác:

~~~java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class ClassProxyConfig {
}
~~~

`true` buộc hạ tầng auto-proxy chọn **class-based proxy** khi bean cần AOP. Nếu hai cấu hình kích hoạt cùng một auto-proxy creator trong **cùng ApplicationContext**, thiết lập mạnh hơn (`true`) có thể được hợp nhất và ảnh hưởng **nhiều bean**, không chỉ bean “thuộc” class cấu hình đó.

Một luồng thay đổi dễ gặp:

~~~text
Trước:
bean implements interface
  |
@EnableAspectJAutoProxy(proxyTargetClass = true)
  |
Spring trả CGLIB subclass proxy
  |
(PaymentService) bean -> OK

Sau:
bỏ ép class proxy / đặt false
  |
Spring chọn JDK proxy cho bean có interface
  |
(PaymentService) bean -> ClassCastException
~~~

Chiều ngược lại cũng có thể ảnh hưởng ứng dụng: code vốn giả định `bean.getClass()` là kiểu JDK proxy, lấy method/field bằng reflection, hoặc so sánh chính xác proxy class name có thể không còn phù hợp khi chuyển sang CGLIB. Phần lớn ứng dụng nên phụ thuộc **interface/capability**, không dựa vào implementation runtime của proxy.

### 3.2. Spring Boot có mặc định riêng

Spring Boot khi có AOP auto-configuration thường ưu tiên class-based proxy. Có thể cấu hình:

~~~properties
spring.aop.proxy-target-class=true
~~~

hoặc:

~~~properties
spring.aop.proxy-target-class=false
~~~

Trong cấu hình tương ứng, `false` cho phép dùng interface-based proxy khi interface thích hợp. Tuy nhiên, các annotation hoặc XML AOP/transaction khác có thể đẩy thiết lập hợp nhất thành class proxy trong cùng context. Vì thế **đừng suy ra kiểu proxy cuối cùng chỉ từ một dòng properties**; kiểm tra bean thực tế.

### 3.3. Khi source code cast trực tiếp từ `getBean(...)`

Hai lối lấy bean cần phân biệt:

~~~java
Object bean = context.getBean("paymentService");
PaymentService impl = (PaymentService) bean; // Có thể ClassCastException
~~~

và:

~~~java
PaymentService impl = context.getBean(PaymentService.class);
// Có thể thất bại khi bean thực tế là JDK proxy.
~~~

Cách đầu lấy reference theo tên rồi **ép kiểu Java**, nên `ClassCastException` là kết quả trực tiếp nếu proxy JDK không thể gán cho `PaymentService`. Cách sau là **type-based lookup trong BeanFactory**: tùy bean definition, cơ chế dự đoán kiểu và thời điểm tạo proxy, Spring có thể báo `NoSuchBeanDefinitionException`, `BeanNotOfRequiredTypeException` hoặc lỗi phù hợp khác. Không mặc định rằng mọi lỗi “ép kiểu” trong Spring đều là `ClassCastException`.

Với injection:

~~~java
public PaymentJob(PaymentService paymentService) {
    this.paymentService = paymentService;
}
~~~

Nếu chỉ có bean dạng JDK proxy expose `PaymentOperations`, dependency theo implementation class không còn hợp kiểu và có thể làm context không khởi động được với lỗi autowiring/creation liên quan. Đây là **đoạn constructor minh họa** trong một class có field tương ứng, chưa phải file Java độc lập.

## 4. Ứng dụng Java 17+ tái hiện cả lỗi lẫn cách hoạt động đúng

Ví dụ chạy bằng **Java 17+, Maven 3.9+, Spring Framework 6.2.0**. Hai ApplicationContext sử dụng **cùng định nghĩa service và aspect** nhưng khác cấu hình proxy. Ta chủ động bắt `ClassCastException` ở chế độ JDK để chương trình vẫn chạy và ghi nhận kết quả; ở chế độ CGLIB, cast thành công.

Cấu trúc dự án:

~~~text
spring-aop-proxy-cast/
├── pom.xml
└── src/main/java/example/
    ├── Demo.java
    ├── PaymentOperations.java
    ├── PaymentService.java
    └── AuditAspect.java
~~~

### 4.1. Tạo `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>spring-aop-proxy-cast</artifactId>
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
                    <mainClass>example.Demo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp `AnnotationConfigApplicationContext` và cấu hình bean, `spring-aop` chứa hạ tầng proxy cùng `AopUtils`; `aspectjweaver` cung cấp các kiểu AspectJ dùng khi Spring phân tích `@AspectJ` pointcut. Việc có `aspectjweaver` trong project **không** kích hoạt AspectJ bytecode weaving; mã dưới đây chỉ dùng Spring AOP proxy.

### 4.2. Interface được caller sử dụng

`src/main/java/example/PaymentOperations.java`:

~~~java
package example;

public interface PaymentOperations {
    void pay(String orderId);
}
~~~

### 4.3. Class triển khai interface

`src/main/java/example/PaymentService.java`:

~~~java
package example;

public class PaymentService implements PaymentOperations {
    @Override
    public void pay(String orderId) {
        System.out.println("SERVICE: paid " + orderId);
    }

    public void implementationOnly() {
        System.out.println("IMPLEMENTATION: called");
    }
}
~~~

Method `implementationOnly()` không thuộc interface. Nó dùng để chứng minh một class proxy có thể expose method ở implementation class, còn JDK proxy thì không. Bản thân method này **không được aspect phía dưới advice** vì pointcut chỉ khớp `pay(...)`.

### 4.4. Aspect đảm bảo service cần được proxy

`src/main/java/example/AuditAspect.java`:

~~~java
package example;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AuditAspect {
    @Before("execution(* example.PaymentService.pay(..))")
    public void beforePayment() {
        System.out.println("ADVICE: before pay");
    }
}
~~~

Spring nhận diện `AuditAspect` là aspect khi nó được đăng ký thành **Spring bean**. Biểu thức `execution(* example.PaymentService.pay(..))` khớp method execution `pay` trên target. Advisor này khiến `PaymentService` được auto-proxy khi Spring AOP bật, cho phép thử hành vi của cả hai loại proxy.

### 4.5. Chạy hai context với flag khác nhau

`src/main/java/example/Demo.java`:

~~~java
package example;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

public class Demo {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    public static class BaseConfig {
        @Bean
        public PaymentService paymentService() {
            return new PaymentService();
        }

        @Bean
        public AuditAspect auditAspect() {
            return new AuditAspect();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    public static class ForceClassProxyConfig {
    }

    private static void inspect(
            String label,
            AnnotationConfigApplicationContext context,
            boolean expectClassProxy) {

        PaymentOperations bean = context.getBean(PaymentOperations.class);

        boolean aop = AopUtils.isAopProxy(bean);
        boolean jdk = AopUtils.isJdkDynamicProxy(bean);
        boolean cglib = AopUtils.isCglibProxy(bean);
        boolean implementation = bean instanceof PaymentService;

        if (!aop || cglib != expectClassProxy
                || jdk == expectClassProxy
                || implementation != expectClassProxy) {
            throw new IllegalStateException("Unexpected proxy type: " + label);
        }

        System.out.println(label + "_JDK: " + jdk);
        System.out.println(label + "_CGLIB: " + cglib);
        System.out.println(label + "_IS_IMPLEMENTATION: " + implementation);

        bean.pay("A-01");

        if (expectClassProxy) {
            PaymentService service = (PaymentService) bean;
            System.out.println(label + "_CAST: OK");
            service.implementationOnly();
        } else {
            try {
                PaymentService service = (PaymentService) bean;
                service.implementationOnly();
                throw new IllegalStateException("JDK cast unexpectedly worked");
            } catch (ClassCastException expected) {
                System.out.println(label + "_CAST: ClassCastException");
            }
        }
    }

    public static void main(String[] args) {
        try (var jdk =
                new AnnotationConfigApplicationContext(BaseConfig.class)) {
            inspect("JDK", jdk, false);
        }

        try (var cglib = new AnnotationConfigApplicationContext(
                BaseConfig.class, ForceClassProxyConfig.class)) {
            inspect("CGLIB", cglib, true);
        }

        System.out.println("CHECK: PASS");
    }
}
~~~

**Vì sao hai context cho kết quả khác nhau?**

1. `BaseConfig` khai báo `@EnableAspectJAutoProxy(proxyTargetClass = false)`. `PaymentService` implements `PaymentOperations` và có advisor `@Before` áp dụng, nên ở Spring Framework thuần 6.2 ví dụ này dùng **JDK dynamic proxy**.
2. `ForceClassProxyConfig` chỉ bổ sung `@EnableAspectJAutoProxy(proxyTargetClass = true)`, không định nghĩa thêm service hay aspect. Khi nạp **cùng context** với `BaseConfig`, hạ tầng auto-proxy được hợp nhất với thiết lập `true` và tạo **CGLIB class proxy**.
3. Trong cả hai context, người gọi lấy `PaymentOperations` qua Spring container và `bean.pay("A-01")` đều đi qua advisor. Do đó vấn đề cast **không bắt nguồn từ việc Spring AOP advice bị mất**.
4. `AopUtils.isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy` đo loại proxy runtime. `bean instanceof PaymentService` xác nhận tính gán kiểu trước khi cast.
5. Nhánh JDK cố tình cast `PaymentOperations` reference sang `PaymentService`. `ClassCastException` được bắt để in dòng giải thích ổn định, tránh phụ thuộc chuỗi exception message của JDK.
6. Nhánh CGLIB cast thành công và gọi được `implementationOnly()`, nhưng method này không match pointcut nên không in thêm `ADVICE`.
7. Điều kiện kiểm tra chủ động ném `IllegalStateException` nếu các giả định proxy không đúng, vì vậy `CHECK: PASS` không phải một dòng được in vô điều kiện ngay từ đầu.

### 4.6. Chạy và kiểm tra output

Chạy tại thư mục `spring-aop-proxy-cast`:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do code ứng dụng in ra, đúng thứ tự:

~~~text
JDK_JDK: true
JDK_CGLIB: false
JDK_IS_IMPLEMENTATION: false
ADVICE: before pay
SERVICE: paid A-01
JDK_CAST: ClassCastException
CGLIB_JDK: false
CGLIB_CGLIB: true
CGLIB_IS_IMPLEMENTATION: true
ADVICE: before pay
SERVICE: paid A-01
CGLIB_CAST: OK
IMPLEMENTATION: called
CHECK: PASS
~~~

Maven, SLF4J hoặc Spring có thể phát thêm thông báo tùy môi trường. Chỉ các dòng trên do `System.out.println` của ví dụ tạo ra.

Kết quả đáng chú ý:

- **Cả hai chế độ đều có `ADVICE: before pay`** trước mã nghiệp vụ.
- **Chỉ JDK proxy không gán được về implementation class**; lỗi cast được tạo có chủ ý.
- **CGLIB proxy có thể gán về implementation class**, nhưng khả năng gọi method implementation **không đồng nghĩa method ấy sẽ được advice**.
- `CHECK: PASS` xác nhận loại proxy đúng trong cả hai context và chương trình tới cuối mà không gặp lỗi ngoài dự kiến.

## 5. Cách sửa đúng theo nhu cầu

### 5.1. Ưu tiên interface cho dependency mà caller cần

Nếu business code chỉ cần hợp đồng `pay(...)`, giải pháp có tính ổn định cao là inject/lấy bean theo interface:

~~~java
package example;

public final class PaymentJob {
    private final PaymentOperations payments;

    public PaymentJob(PaymentOperations payments) {
        this.payments = payments;
    }

    public void execute() {
        payments.pay("A-01");
    }
}
~~~

Đây là **class Java hoàn chỉnh**, nhưng để Spring tạo/inject nó cần đăng ký `PaymentJob` là bean trong application, chẳng hạn qua `@Bean` hoặc `@Component`. Kiểu `PaymentOperations` tương thích với JDK proxy và CGLIB proxy. Nếu có nhiều bean cùng interface, thêm `@Qualifier`/`@Primary` để lựa chọn bean phù hợp; các annotation đó **không đổi bản chất loại proxy**.

Với code đang có:

~~~java
PaymentOperations payments = context.getBean(PaymentOperations.class);
payments.pay("A-01");
~~~

Giữ interface xuyên suốt call chain giúp tránh ép kiểu xuống implementation chỉ để gọi method đã được khai báo trong contract.

### 5.2. Thật sự cần phương thức chỉ thuộc implementation?

Trong ví dụ, `implementationOnly()` chỉ thuộc `PaymentService`. Có ba hướng lựa chọn:

1. **Bổ sung method đó vào interface** nếu nó thực sự là hành vi công khai mà caller cần; khi đó JDK proxy có thể expose method đó. Hãy cập nhật interface contract rõ ràng và kiểm tra pointcut/advice nếu cần.
2. **Tách chức năng thành interface/service riêng** nếu implementation-only method không thuộc contract hiện có. Đây thường là giải pháp tốt hơn so với cast dựa trên lớp cụ thể.
3. **Yêu cầu class proxy bằng `proxyTargetClass = true`** khi có lý do bắt buộc làm việc qua implementation class. Cần đánh giá ảnh hưởng tới **các bean AOP khác cùng context**, final class/method, tính tương thích module/JDK và thiết lập AOP/transaction khác. Không nên coi đây là sửa một dòng chỉ tác động đến một bean.

Ép kiểu bằng `(PaymentService)` sau khi kiểm tra `instanceof` có thể tránh exception bằng logic điều kiện, nhưng **không giúp truy cập implementation-only method khi runtime là JDK proxy**. Điều cần sửa là contract hoặc cấu hình, không phải đặt thêm cast.

### 5.3. Tránh tìm implementation class khi không cần

Với JDK proxy, các cách sau có thể gây lỗi ở những giai đoạn khác nhau:

~~~java
Object bean = context.getBean("paymentService");
PaymentService cast = (PaymentService) bean;
~~~

Đây là cast Java trực tiếp có thể ném `ClassCastException`.

~~~java
PaymentService byType = context.getBean(PaymentService.class);
~~~

Đây là lookup theo kiểu concrete có thể thất bại do proxy không gán được về `PaymentService`; loại Spring exception cụ thể phụ thuộc vào việc BeanFactory phân giải và kiểm tra kiểu. Tránh “bắt `ClassCastException`” như cách xử lý duy nhất cho tình huống `getBean(Class)` hoặc autowiring.

Giải pháp interface:

~~~java
PaymentOperations byContract = context.getBean(PaymentOperations.class);
~~~

Tất cả snippet trong phần này đều **đặt trong method đã có biến `context`** và các class mẫu đã định nghĩa ở phần 4; không phải file chạy riêng.

### 5.4. Lấy thông tin target mà không cast proxy thành target

Khi cần phục vụ logging/debug:

~~~java
Object bean = context.getBean("paymentService");

System.out.println(
    org.springframework.aop.support.AopUtils.isAopProxy(bean));
System.out.println(
    org.springframework.aop.support.AopUtils.isJdkDynamicProxy(bean));
System.out.println(
    org.springframework.aop.support.AopUtils.isCglibProxy(bean));
System.out.println(
    org.springframework.aop.support.AopUtils.getTargetClass(bean).getName());
~~~

`AopUtils.getTargetClass(bean)` giúp xem lớp target được Spring AOP biết đến, **không biến proxy thành instance của lớp đó**. Đây là đoạn kiểm tra chèn vào ứng dụng hiện có; nó không khuyên tháo proxy để gọi target trực tiếp. Lấy target object và gọi thẳng vào nó có thể bỏ qua advice, transaction và security.

## 6. Những “cách sửa” dễ tạo lỗi mới

| Cách thử | Vì sao chưa đúng hoặc chưa đủ? |
| --- | --- |
| Thêm `(PaymentService)` ở nhiều nơi | Không đổi type hierarchy của JDK proxy; vẫn `ClassCastException` |
| Dùng `@Qualifier("paymentService")` nhưng field/constructor type là `PaymentService` | `@Qualifier` chọn bean theo tên/metadata, không biến JDK proxy thành implementation |
| Đổi `proxyTargetClass = true` trên một config rồi cho rằng chỉ bean đó bị ảnh hưởng | Auto-proxy settings có thể hợp nhất, đổi loại proxy cho nhiều bean cùng context |
| Cho rằng `@Aspect` hoặc `@Before` tạo CGLIB proxy | Aspect khai báo advisor; loại proxy do hạ tầng và cấu hình quyết định |
| Thêm `aspectjweaver` hoặc `-javaagent` chỉ để chữa cast | Thay đổi sang weaving là vấn đề khác; class cast của Spring proxy cần xử lý ở contract/proxy strategy |
| Cast sau khi gọi `AopUtils.getTargetClass` | Metadata target class không làm reference trở thành object của class đó |
| Lấy trực tiếp target object bên trong proxy rồi gọi method | Có thể bỏ qua advice và những behavior dựa trên Spring AOP |
| Chuyển toàn bộ class/method thành `final` rồi ép CGLIB | CGLIB subclass proxy không thể override `final` method hoặc subclass `final` class |
| Kiểm tra `bean.getClass() == PaymentService.class` để xác nhận advice | Với cả JDK và CGLIB, proxy runtime class thường khác target class |
| Coi `this.pay()` được advice sau khi ép CGLIB | Self-invocation trên target vẫn không qua Spring proxy |

Bật CGLIB vì cast ở một vị trí có thể gây tác động phụ đối với các bean khác: class `final` không proxy hóa được bằng subclass, method `final`/`private` không thể override để intercept, và các điểm injection theo interface hoặc reflection có thể vận hành khác mong đợi. Cần xem toàn bộ các bean có advisor khi thay đổi cấu hình toàn context.

## 7. Quy trình xác định nguyên nhân trong ứng dụng Spring

Khi gặp `ClassCastException` chứa class name dạng `jdk.proxy...$Proxy...` hoặc lỗi inject/lookup theo implementation:

1. **Xác định reference thật:** bean đến từ `ApplicationContext` hay được tạo bằng `new`? Nếu từ Spring, ghi tên bean và phương thức dùng để lookup/inject. Với proxy, lỗi cast phụ thuộc type runtime.
2. **Kiểm tra interface và implementation:** target có interface nào? Điểm gọi đang cần contract method hay method chỉ nằm trong implementation?
3. **Đo ba trạng thái trên reference:** `AopUtils.isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy`, kết hợp `instanceof Interface` và `instanceof Implementation`.
4. **Tìm thiết lập proxy hiệu lực:** `@EnableAspectJAutoProxy`, `proxyTargetClass`, Spring Boot AOP properties, XML `<aop:aspectj-autoproxy/>`, transaction auto-proxy config, context cha/con và các thiết lập gộp.
5. **Kiểm tra vì sao bean được proxy:** advisor/`@Aspect` nào khớp; một bean không khớp advisor có thể là target thường và không tái hiện lỗi.
6. **Đổi đúng thiết kế dependency:** ưu tiên interface cho hành vi công khai, dùng `@Qualifier` nếu có nhiều implementation. Nếu bắt buộc method ở implementation, cân nhắc đổi contract hoặc class proxy.
7. **Kiểm tra lớp và method khi ép CGLIB:** `final`, `private`, visibility, Java modules và trạng thái subclassing.
8. **Chạy lại kiểm chứng:** assert đúng loại proxy, gọi method qua reference bean và xác nhận advice chạy; không dừng ở việc cast không còn ném exception.

### Phân biệt các loại lỗi

- **`ClassCastException` tại dòng `(PaymentService) object`**: reference runtime không phải instance của class ấy; nếu là JDK proxy thì đây là hệ quả tự nhiên.
- **`BeanNotOfRequiredTypeException` / lỗi lookup concrete type**: bean Spring không phù hợp kiểu mà client yêu cầu; xem dấu vết BeanFactory và injection point.
- **`NoSuchBeanDefinitionException`**: không tìm thấy bean đáp ứng kiểu/tên yêu cầu trong context được tra cứu; có thể là sai context, thiếu đăng ký hoặc kiểu expose khác.
- **`NoUniqueBeanDefinitionException`**: có nhiều bean cùng interface, cần chọn bằng `@Qualifier`/`@Primary` — đây không phải lỗi cast JDK/CGLIB.
- **Advice không chạy dù bean cast được**: cast thành công **không bảo đảm proxy tồn tại** hay pointcut khớp. Kiểm tra `AopUtils.isAopProxy` và lời gọi qua proxy.

## 8. Câu hỏi tự kiểm tra

1. **Tại sao JDK proxy có thể được gán cho `PaymentOperations` nhưng không cho `PaymentService`?**  
   Vì proxy thực thi interface, không phải subclass của implementation class.

2. **Dùng CGLIB là mọi cast xuống concrete class đều an toàn?**  
   Không. Chỉ an toàn khi reference thực sự là instance/subclass của class mong muốn, target đủ điều kiện subclass và không có nhầm lẫn bean.

3. **Một bean chỉ có method công khai qua interface cần sửa thế nào để không phụ thuộc loại proxy?**  
   Inject/lấy và gọi qua interface, tránh ép xuống implementation.

4. **`@Qualifier` có chữa ClassCastException do JDK proxy?**  
   Không. Nó chỉ giúp chọn bean, không sửa type hierarchy.

5. **Vì sao `getBean(PaymentService.class)` và `(PaymentService) getBean("paymentService")` có thể tạo exception khác nhau?**  
   Cách đầu là lookup/type checking do Spring thực hiện, còn cách sau là cast object ở JVM.

6. **Chỉ cần thêm `proxyTargetClass = true` để hết lỗi mọi nơi?**  
   Không. Cấu hình có phạm vi theo context/auto-proxy creator, có thể ảnh hưởng nhiều bean và có giới hạn subclassing; ưu tiên kiểm tra contract trước.

7. **CGLIB có sửa được self-invocation không?**  
   Không. `this.otherMethod()` trong target vẫn có thể đi vòng ngoài Spring proxy.

## Tài liệu Spring Framework

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK dynamic proxy, CGLIB proxy, giới hạn và self-invocation.
- [Spring Framework 6.2 — AOP Proxies](https://docs.spring.io/spring-framework/reference/6.2/core/aop/introduction-proxies.html): mô hình proxy của Spring AOP.
- [Spring Framework 6.2 — @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj.html): cơ chế Spring dùng cú pháp khai báo aspect.
- [Spring Framework 6.2 — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html): cấu hình `@EnableAspectJAutoProxy`.
- [Spring Boot Reference — AOP](https://docs.spring.io/spring-boot/reference/features/aop.html): `spring.aop.proxy-target-class` trong Boot.


</details>

- [Quay lại đầu trang](#back-to-top)
