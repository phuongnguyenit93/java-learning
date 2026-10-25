<a id="back-to-top"></a>

# Tra cứu và ôn tập

## Menu
- [Bảng ánh xạ annotation, XML và API AOP của Spring](#spring-aop-review-001)
- [Bảng so sánh proxy JDK, proxy CGLIB và tích hợp AspectJ trong Spring](#spring-aop-review-002)
- [Bảng pointcut được Spring hỗ trợ và trường hợp sử dụng](#spring-aop-review-003)
- [Bảng loại advice, dữ liệu binding và khả năng thay đổi luồng](#spring-aop-review-004)
- [Danh sách rà soát đăng ký bean, proxy và thứ tự advice](#spring-aop-review-005)
- [Câu hỏi tình huống advice không chạy hoặc chạy sai thứ tự](#spring-aop-review-006)
- [Bài tập lựa chọn Spring AOP, cơ chế Spring khác hoặc tích hợp AspectJ](#spring-aop-review-007)
- [Định hướng đọc tài liệu và mã nguồn Spring AOP theo nhu cầu](#spring-aop-review-008)

## <a id="spring-aop-review-001">Bảng ánh xạ annotation, XML và API AOP của Spring</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng ánh xạ annotation, XML và API AOP của Spring

## 1. Mục đích tra cứu

Spring Framework cho phép cấu hình **Spring AOP** theo các cách khác nhau. Ba nhóm thường gặp khi đọc codebase hoặc bảo trì một ứng dụng Spring là:

1. **Annotation @AspectJ style kết hợp Java configuration**: lớp aspect mang **@Aspect**, phương thức mang **@Before**, **@Around**, ...; Spring đăng ký nó thành bean và tự tạo proxy theo pointcut.
2. **XML AOP namespace**: dùng các phần tử **<aop:config>**, **<aop:aspect>**, **<aop:pointcut>**, **<aop:before>** và tương tự để khai báo pointcut/advice mà không bắt buộc đặt annotation @Aspect trên advice class.
3. **Spring AOP API**: tạo và ghép **ProxyFactory**, **Pointcut**, **Advice**, **Advisor**; nếu dùng cấu hình bean có thể gặp **ProxyFactoryBean**. Spring cũng có **AspectJProxyFactory** để tạo proxy trực tiếp từ lớp @Aspect.

Mục đích của bảng ánh xạ là đọc được **cùng một yêu cầu advice** khi nó được biểu diễn bằng các cấu hình khác nhau, hiểu khác biệt về vòng đời Spring bean và không dịch máy móc một cấu hình sang cấu hình khác.

**Ranh giới:** Đây là bài tập trung vào **Spring Aspect và Spring AOP proxy**. Tài liệu không giải thích lại toàn bộ lý thuyết AOP phổ quát và không đánh đồng Spring AOP với AspectJ compile-time/load-time weaving.

Trong cả ba ví dụ thực hành, yêu cầu giống nhau:

> Trước khi gọi **OrderService.place(String)** qua AOP proxy, ghi sự kiện **advice:...**; target sau đó ghi **target:place**. Method **status()** không thuộc pointcut nên chỉ ghi sự kiện target.

## 2. Bảng định hướng: chọn cách cấu hình nào?

| Yêu cầu | Annotation + Java config | XML AOP namespace | Spring AOP API |
|---|---|---|---|
| Khai báo aspect | **@Aspect** trên Java class, class đó phải là Spring bean | **<aop:aspect ref="...">** tham chiếu Spring bean chứa advice methods | Ghép **Advice** và **Advisor**; hoặc **AspectJProxyFactory** với lớp **@Aspect** |
| Khai báo pointcut | **@Pointcut("expression")** hoặc expression ngay trong annotation advice | **<aop:pointcut id="..." expression="..."/>** | **Pointcut**, ví dụ **AspectJExpressionPointcut**, **NameMatchMethodPointcut** |
| Khai báo before advice | **@Before("...")** | **<aop:before method="..." pointcut-ref="..."/>** | **MethodBeforeAdvice** hoặc **MethodInterceptor** trong advisor |
| Khai báo after-returning | **@AfterReturning(...)** | **<aop:after-returning .../>** | **AfterReturningAdvice** hoặc interceptor phù hợp |
| Khai báo after-throwing | **@AfterThrowing(...)** | **<aop:after-throwing .../>** | **ThrowsAdvice** hoặc interceptor phù hợp |
| Khai báo after/finally | **@After(...)** | **<aop:after .../>** | Interceptor có **try/finally** |
| Khai báo around | **@Around("...")** và **ProceedingJoinPoint.proceed()** | **<aop:around .../>**, advice method nhận **ProceedingJoinPoint** | **MethodInterceptor.invoke(MethodInvocation)** và **invocation.proceed()** |
| Bật auto-proxy cho @Aspect beans | **@EnableAspectJAutoProxy** | **<aop:aspectj-autoproxy/>** | Không cần khi dùng **ProxyFactory** thủ công; factory trực tiếp tạo proxy |
| Đăng ký target/aspect | **@Bean**, **@Component**, **@Service** với scan phù hợp | **<bean .../>** | Target object + factory; hoặc bean definition/factory bean |
| JDK/CGLIB proxy | **proxyTargetClass** | **proxy-target-class** | **setProxyTargetClass(...)**, **setInterfaces(...)** |
| Ordering | **@Order**, **Ordered** | **order** trên aspect/advisor phù hợp | Advisor/Advice có **Ordered** hoặc đặt thứ tự vào factory |
| Chỗ lấy proxy | **ApplicationContext.getBean(...)** | **ApplicationContext.getBean(...)** | **factory.getProxy()** hoặc bean do **ProxyFactoryBean** trả về |
| Đặc điểm | Dễ đọc ngay trên lớp Java | Thuận tiện cấu hình ngoài Java, thường gặp ở dự án cũ | Chi tiết, linh hoạt, cần tự quản lý nhiều bước hơn |

Các hàng ở trên là **ánh xạ về ý nghĩa/chức năng**, **không phải phép đổi cú pháp 1:1**. Ví dụ **@Before** của @AspectJ style có ngữ nghĩa before advice, nhưng một API **MethodInterceptor** có thể làm việc trước và sau cùng lúc; XML aspect cần một Spring bean chứa method được khai báo bằng **method=...**, không nhất thiết là lớp có **@Aspect**.

## 3. Hai cách viết XML khác nhau cần phân biệt

**<aop:aspectj-autoproxy/>** và **<aop:config>** không cùng vai trò, dù cả hai đều liên quan Spring AOP.

### 3.1. XML bật nhận diện lớp @Aspect

~~~xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd">

    <aop:aspectj-autoproxy/>

    <bean id="auditAspect" class="demo.AnnotationAuditAspect">
        <constructor-arg ref="eventLog"/>
    </bean>

    <!-- Các bean eventLog, orderService cũng phải được khai báo. -->
</beans>
~~~

Ở đây **AnnotationAuditAspect** vẫn chứa **@Aspect**, **@Before** và pointcut annotation trong Java. XML chỉ yêu cầu Spring xử lý các bean dạng @Aspect và tạo AOP proxy khi có match. **<aop:aspectj-autoproxy/> không đồng nghĩa với AspectJ bytecode weaving.**

Tương ứng bằng Java configuration:

~~~java
@Configuration
@EnableAspectJAutoProxy
class AopConfiguration {
    @Bean
    AnnotationAuditAspect auditAspect(EventLog events) {
        return new AnnotationAuditAspect(events);
    }
}
~~~

Đây là hai **đoạn cấu hình minh họa**; cả hai cần bổ sung target và các dependencies trong context thực tế.

### 3.2. XML định nghĩa aspect bằng schema

~~~xml
<aop:config>
    <aop:pointcut id="placeOperation"
                  expression="execution(* demo.OrderService.place(..))"/>

    <aop:aspect ref="xmlAuditAdvice">
        <aop:before method="beforePlace"
                    pointcut-ref="placeOperation"/>
    </aop:aspect>
</aop:config>
~~~

Trong trường hợp này, **xmlAuditAdvice** là tên một Spring bean chứa method **beforePlace()**. Lớp Java của bean ấy **không buộc phải mang @Aspect hoặc @Before**. Chính phần tử **<aop:before>** cung cấp liên kết giữa pointcut và method advice.

Điểm khác nhau để nhớ:

- **<aop:aspectj-autoproxy/>**: đọc metadata **@AspectJ annotations** trên Spring bean.
- **<aop:config> + <aop:aspect>**: cấu hình advisor/aspect **trong XML schema**.
- Cả hai đều thuộc **Spring AOP proxy-based interception**, không phải lệnh bật bytecode weaving.

## 4. Bảng ánh xạ từng advice type

| Kiểu logic | Annotation Java | XML schema | API Spring gần tương đương | Điều cần giữ khi chuyển đổi |
|---|---|---|---|---|
| Chạy trước | **@Before("pc()")** | **<aop:before method="before" pointcut-ref="pc"/>** | **MethodBeforeAdvice** + PointcutAdvisor | Không tự bỏ target; có thể chặn đường đi bằng exception |
| Chạy sau khi thành công | **@AfterReturning(pointcut="pc()", returning="result")** | **<aop:after-returning method="after" pointcut-ref="pc" returning="result"/>** | **AfterReturningAdvice** | Method phải hoàn tất bình thường; tham số result đúng binding |
| Chạy khi exception | **@AfterThrowing(pointcut="pc()", throwing="ex")** | **<aop:after-throwing method="onError" pointcut-ref="pc" throwing="ex"/>** | **ThrowsAdvice** | Exception phải thoát ra từ phần chain tương ứng; không tự suy rằng caller cuối cùng chắc chắn nhìn thấy lỗi nếu có advisor khác |
| Chạy cuối cùng | **@After("pc()")** | **<aop:after method="cleanup" pointcut-ref="pc"/>** | Interceptor với **finally** | Chạy khi return bình thường và exception |
| Bao quanh | **@Around("pc()")** | **<aop:around method="around" pointcut-ref="pc"/>** | **MethodInterceptor** | Giữ số lần **proceed()**, return value và Throwable đúng contract |

**Không nên gọi tất cả loại advice là @Around:** @Around có quyền short-circuit, gọi lại target, đổi return và exception; advice đơn giản như @Before hoặc @AfterReturning dễ xác định hợp đồng hơn.

Ví dụ Java @AspectJ cho after returning:

~~~java
@AfterReturning(
    pointcut = "execution(* demo.OrderService.place(..))",
    returning = "result"
)
public void afterPlace(String result) {
    // Quan sát chuỗi result; không thay return value của caller.
}
~~~

XML schema:

~~~xml
<aop:after-returning method="afterPlace"
                     pointcut="execution(* demo.OrderService.place(..))"
                     returning="result"/>
~~~

Hai đoạn **minh họa ánh xạ**, không phải ứng dụng độc lập. Phương thức advice XML có thể khai báo tham số **String result** nếu Spring có đủ metadata/binding phù hợp; khi chuyển đổi một dự án thật phải kiểm tra tên tham số, kiểu và điều kiện binding.

## 5. Pointcut: mapping nhưng không làm mất ngữ nghĩa

Ví dụ yêu cầu chọn method **place** của **OrderService**:

~~~java
@Pointcut("execution(* demo.OrderService.place(..))")
public void placingOrder() {}

@Before("placingOrder()")
public void beforePlace() {
    // ...
}
~~~

Tương đương về phạm vi ở XML:

~~~xml
<aop:pointcut id="placingOrder"
              expression="execution(* demo.OrderService.place(..))"/>
<aop:aspect ref="xmlAuditAdvice">
    <aop:before method="beforePlace"
                pointcut-ref="placingOrder"/>
</aop:aspect>
~~~

Tương ứng ở low-level API:

~~~java
AspectJExpressionPointcut pointcut =
    new AspectJExpressionPointcut();
pointcut.setExpression(
    "execution(* demo.OrderService.place(..))"
);
~~~

**Lưu ý:** Spring AOP chỉ hỗ trợ **method execution join points trên Spring beans/proxies** theo cơ chế này. Biểu thức AspectJ cho field access hay constructor call không được Spring AOP proxy hỗ trợ. Điểm khác nữa là **bean(...)** là designator được Spring AOP bổ sung để chọn theo tên Spring bean; nó không có ý nghĩa tương đương khi bạn tự tạo một **ProxyFactory** cho đối tượng Java ngoài BeanFactory.

Các cách thu hẹp pointcut cần chú ý:

- **execution(...)**: khớp method signature/type.
- **within(package..*)**: giới hạn target type/package.
- **@annotation(...)**: chọn method có annotation phù hợp.
- **bean(namePattern)**: dựa vào tên bean trong Spring container.
- **args(...)**: có thể kiểm tra argument types tại runtime.

Nếu di chuyển từ Java @AspectJ sang XML, **đừng chỉ chép tên method**: phải giữ nguyên pointcut expression, target types, bean names và annotation semantics. Với XML expression chứa toán tử **&&**, cần viết **&amp;&amp;** để hợp lệ XML, hoặc dùng từ khóa **and** được AspectJ expression parser chấp nhận trong ngữ cảnh phù hợp.

## 6. Cách Spring tạo proxy trong từng kiểu cấu hình

### 6.1. Java annotation configuration

~~~java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
class AopConfig {
    // @Bean target và @Bean @Aspect objects.
}
~~~

Mặc định cấu hình Spring Framework ưu tiên **JDK dynamic proxy** nếu target có interface phù hợp và không có thiết lập mạnh hơn; nếu không có interface, có thể dùng class-based/CGLIB proxy.

### 6.2. XML

~~~xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
~~~

Hoặc khi dùng schema AOP:

~~~xml
<aop:config proxy-target-class="true">
    <!-- aspect/advisor declarations -->
</aop:config>
~~~

**proxy-target-class="true"** yêu cầu class-based/CGLIB proxy cho các bean chịu tác động của cấu hình auto-proxy. Nó không cho phép advice các final/private methods bằng cách override và không sửa vấn đề self-invocation.

### 6.3. API

~~~java
ProxyFactory factory = new ProxyFactory(targetObject);
factory.setInterfaces(ServiceInterface.class);
factory.setProxyTargetClass(false);
factory.addAdvisor(advisor);
Object proxy = factory.getProxy();
~~~

Đây là mảnh minh họa có các biến chưa khai báo. Khác với auto-proxy trong ApplicationContext, **ProxyFactory** tạo proxy theo lệnh gọi **getProxy()**: nếu client giữ reference đến **targetObject** gốc thay vì proxy, advice không chạy.

**Quan trọng khi trộn cấu hình:** Các thành phần tự động tạo proxy trong cùng một ApplicationContext có thể hợp nhất và nhận thiết lập proxy mạnh nhất. Ví dụ một cấu hình khác yêu cầu **proxyTargetClass=true** có thể làm proxy được tạo khác với giả định mặc định. Trong Spring Boot, cấu hình AOP auto-configuration có thể chọn CGLIB theo property riêng. Hãy kiểm tra loại proxy thực tế bằng **AopUtils.isJdkDynamicProxy** / **isCglibProxy**, đừng đoán từ annotation đơn lẻ.

## 7. Bài thực hành: ba cách cấu hình, một hành vi Spring AOP

Dưới đây là **một Maven project hoàn chỉnh** minh họa:

1. **Annotation @AspectJ + @EnableAspectJAutoProxy** trong Spring ApplicationContext.
2. **XML schema-based AOP** với **<aop:config>** trong một ApplicationContext khác.
3. **Low-level API** với **ProxyFactory + AspectJExpressionPointcut + DefaultPointcutAdvisor**.

Ba đường chạy sử dụng cùng interface/target class và cùng quy tắc chọn method **place(...)**, nhưng dùng **EventLog instance độc lập**. Mỗi trường hợp phải ghi event advice trước event target; method **status()** phải chạy không có advice.

Cấu trúc:

~~~text
spring-aop-mapping/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/
    │   │   ├── OrderPort.java
    │   │   ├── OrderService.java
    │   │   ├── EventLog.java
    │   │   ├── AnnotationAuditAspect.java
    │   │   ├── XmlAuditAdvice.java
    │   │   ├── AnnotationConfig.java
    │   │   ├── ProgrammaticAop.java
    │   │   └── App.java
    │   └── resources/aop-context.xml
    └── test/java/demo/
        └── AopMappingTest.java
~~~

### 7.1. Maven dependencies

Tệp **pom.xml**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-mapping</artifactId>
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
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**spring-context** quản lý bean/config, **spring-aop** tạo proxy, **aspectjweaver** hỗ trợ xử lý biểu thức AspectJ trong Spring AOP. Dependency AspectJ Weaver **không tự bật weaving**. JUnit Jupiter thực thi các integration tests của ba cấu hình.

### 7.2. Service chung cho cả ba cách

Tệp **src/main/java/demo/OrderPort.java**:

~~~java
package demo;

public interface OrderPort {
    String place(String code);
    String status();
}
~~~

Tệp **src/main/java/demo/EventLog.java**:

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public class EventLog {
    private final List<String> events = new ArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> events() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
~~~

Tệp **src/main/java/demo/OrderService.java**:

~~~java
package demo;

public class OrderService implements OrderPort {
    private final EventLog log;

    public OrderService(EventLog log) {
        this.log = log;
    }

    @Override
    public String place(String code) {
        log.add("target:place");
        return "placed:" + code;
    }

    @Override
    public String status() {
        log.add("target:status");
        return "ready";
    }
}
~~~

**place(...)** và **status()** là public methods thuộc interface. Điểm chọn của ví dụ là **execution(* demo.OrderService.place(..))**, nên chỉ **place** được advice. Method **status** giúp kiểm tra ca âm tính. Với interface **OrderPort**, ta có thể kiểm chứng JDK dynamic proxy ở cả ba cách cấu hình.

### 7.3. Cách thứ nhất: annotation @AspectJ

Tệp **src/main/java/demo/AnnotationAuditAspect.java**:

~~~java
package demo;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class AnnotationAuditAspect {
    private final EventLog log;

    public AnnotationAuditAspect(EventLog log) {
        this.log = log;
    }

    @Before("execution(* demo.OrderService.place(..))")
    public void beforePlace() {
        log.add("annotation:before");
    }
}
~~~

Tệp **src/main/java/demo/AnnotationConfig.java**:

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AnnotationConfig {
    @Bean
    public EventLog eventLog() {
        return new EventLog();
    }

    @Bean
    public OrderPort orderPort(EventLog log) {
        return new OrderService(log);
    }

    @Bean
    public AnnotationAuditAspect annotationAuditAspect(EventLog log) {
        return new AnnotationAuditAspect(log);
    }
}
~~~

Spring tạo bean **AnnotationAuditAspect** và đọc **@Aspect/@Before** vì cấu hình đã bật **@EnableAspectJAutoProxy**. Target implements **OrderPort**, nên với context độc lập này Spring có thể tạo JDK interface proxy cho **orderPort**.

**AnnotationAuditAspect** chỉ có một trách nhiệm ghi event before. Nó không thay kết quả, exception hoặc số lần target được gọi.

### 7.4. Cách thứ hai: XML namespace AOP

Tệp **src/main/java/demo/XmlAuditAdvice.java**:

~~~java
package demo;

public class XmlAuditAdvice {
    private final EventLog log;

    public XmlAuditAdvice(EventLog log) {
        this.log = log;
    }

    public void beforePlace() {
        log.add("xml:before");
    }
}
~~~

Class này **không có @Aspect hoặc @Before**. Các mối liên kết AOP nằm trong XML.

Tệp **src/main/resources/aop-context.xml**:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd">

    <bean id="eventLog" class="demo.EventLog"/>

    <bean id="orderPort" class="demo.OrderService">
        <constructor-arg ref="eventLog"/>
    </bean>

    <bean id="xmlAuditAdvice" class="demo.XmlAuditAdvice">
        <constructor-arg ref="eventLog"/>
    </bean>

    <aop:config proxy-target-class="false">
        <aop:pointcut
            id="placeOperation"
            expression="execution(* demo.OrderService.place(..))"/>

        <aop:aspect id="auditAspect"
                    ref="xmlAuditAdvice">
            <aop:before
                method="beforePlace"
                pointcut-ref="placeOperation"/>
        </aop:aspect>
    </aop:config>
</beans>
~~~

**<aop:pointcut>** tạo một named pointcut trong XML, **<aop:aspect ref="xmlAuditAdvice">** liên kết Spring bean chứa advice method và **<aop:before>** chỉ method phải gọi. Không cần thêm **<aop:aspectj-autoproxy/>** vào chính ví dụ này, vì ta đang dùng **schema-based aspect declaration** qua **<aop:config>**.

### 7.5. Cách thứ ba: Spring AOP API

Tệp **src/main/java/demo/ProgrammaticAop.java**:

~~~java
package demo;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;

public final class ProgrammaticAop {
    private ProgrammaticAop() {
    }

    public static OrderPort createProxy(EventLog log) {
        OrderService target = new OrderService(log);

        AspectJExpressionPointcut pointcut =
            new AspectJExpressionPointcut();
        pointcut.setExpression(
            "execution(* demo.OrderService.place(..))"
        );

        MethodInterceptor advice = invocation -> {
            log.add("api:before");
            return invocation.proceed();
        };

        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(OrderPort.class);
        factory.addAdvisor(
            new DefaultPointcutAdvisor(pointcut, advice)
        );

        return (OrderPort) factory.getProxy();
    }
}
~~~

Những thành phần này có vai trò khác nhau:

- **AspectJExpressionPointcut** là đối tượng **Pointcut** chọn method thích hợp.
- **MethodInterceptor** là mã thực thi khi method invocation được advice.
- **DefaultPointcutAdvisor** ghép **Pointcut + Advice**.
- **ProxyFactory** nhận target + advisor rồi tạo AOP proxy.
- **factory.getProxy()** trả về proxy; **target** gốc vẫn là Java object thường.

Mã API tạo proxy **thủ công** và không cần ApplicationContext trong trường hợp này. **MethodInterceptor** ở đây hoạt động giống before advice bởi nó ghi event trước, rồi luôn gọi **invocation.proceed()** và trả lại kết quả; nhưng interface MethodInterceptor có quyền điều khiển flow mạnh hơn **@Before**.

**Đừng nhầm với AspectJProxyFactory:** Nếu muốn đưa trực tiếp một object **@Aspect** vào factory, Spring còn cung cấp **AspectJProxyFactory** và **addAspect(aspectInstance)**. Đó là một API khác với cách tự ghép **DefaultPointcutAdvisor**; cả hai vẫn tạo proxy Spring.

## 8. Main chạy ba cách độc lập

Tệp **src/main/java/demo/App.java**:

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class App {
    private static void check(
            String mode, OrderPort service, EventLog log) {
        System.out.println(
            mode + ":jdk=" +
            AopUtils.isJdkDynamicProxy(service)
        );
        System.out.println(
            mode + ":value=" + service.place("A-01")
        );
        System.out.println(
            mode + ":status=" + service.status()
        );
        System.out.println(
            mode + ":events=" + log.events()
        );
    }

    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(
                    AnnotationConfig.class)) {
            check(
                "annotation",
                context.getBean(OrderPort.class),
                context.getBean(EventLog.class)
            );
        }

        try (var context =
                new ClassPathXmlApplicationContext(
                    "aop-context.xml")) {
            check(
                "xml",
                context.getBean(OrderPort.class),
                context.getBean(EventLog.class)
            );
        }

        EventLog apiLog = new EventLog();
        OrderPort apiProxy =
            ProgrammaticAop.createProxy(apiLog);
        check("api", apiProxy, apiLog);
    }
}
~~~

Mỗi context có các bean và advisor riêng; cấu hình annotation không lẫn với XML. Nhánh API dùng **EventLog** độc lập và proxy được tạo trực tiếp. Đây là cách tránh việc cấu hình auto-proxy trong một ApplicationContext ảnh hưởng nhau khi đang học so sánh.

Chạy tại thư mục gốc:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.App
~~~

**Những dòng chương trình tự in ra** theo thứ tự kỳ vọng:

~~~text
annotation:jdk=true
annotation:value=placed:A-01
annotation:status=ready
annotation:events=[annotation:before, target:place, target:status]
xml:jdk=true
xml:value=placed:A-01
xml:status=ready
xml:events=[xml:before, target:place, target:status]
api:jdk=true
api:value=placed:A-01
api:status=ready
api:events=[api:before, target:place, target:status]
~~~

Số dòng log do Maven/Spring hoặc runtime phát sinh thêm có thể phụ thuộc cấu hình môi trường. Điểm cần đối chiếu là **ba nhánh đều trả kết quả/kiểu proxy tương ứng, chỉ khác tên event advice**.

## 9. Integration tests bảo đảm ba cách tạo proxy hoạt động tương đương

Tệp **src/test/java/demo/AopMappingTest.java**:

~~~java
package demo;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AopMappingTest {
    private static void assertContract(
            OrderPort port,
            EventLog log,
            String expectedAdvice) {
        assertTrue(AopUtils.isAopProxy(port));
        assertTrue(AopUtils.isJdkDynamicProxy(port));

        assertEquals("placed:X", port.place("X"));
        assertEquals("ready", port.status());

        assertEquals(
            List.of(
                expectedAdvice,
                "target:place",
                "target:status"
            ),
            log.events()
        );
    }

    @Test
    void annotationConfigurationWorks() {
        try (var context =
                new AnnotationConfigApplicationContext(
                    AnnotationConfig.class)) {
            assertContract(
                context.getBean(OrderPort.class),
                context.getBean(EventLog.class),
                "annotation:before"
            );
        }
    }

    @Test
    void xmlSchemaConfigurationWorks() {
        try (var context =
                new ClassPathXmlApplicationContext(
                    "aop-context.xml")) {
            assertContract(
                context.getBean(OrderPort.class),
                context.getBean(EventLog.class),
                "xml:before"
            );
        }
    }

    @Test
    void programmaticFactoryWorks() {
        EventLog log = new EventLog();
        OrderPort proxy =
            ProgrammaticAop.createProxy(log);

        assertContract(proxy, log, "api:before");
    }

    @Test
    void directTargetCallHasNoProxyAdvice() {
        EventLog log = new EventLog();
        OrderService target = new OrderService(log);

        assertEquals("placed:X", target.place("X"));
        assertEquals(List.of("target:place"), log.events());
    }
}
~~~

**Vì sao test cả proxy type lẫn event?** **AopUtils.isAopProxy** chứng minh đối tượng có proxy; nó không chứng minh pointcut **place** đã match. Event advice trước target mới chứng minh wiring đúng. Phép thử **status()** kiểm tra phạm vi âm tính: không có advice event cho method ngoài pointcut.

Test thứ tư chứng minh gọi thẳng target **không đi qua Spring proxy**. Vì cả ba cách cấu hình đều dùng proxy chứ không weave bytecode, điều kiện này quan trọng khi debug.

Chạy:

~~~bash
mvn test
~~~

Có thể chạy riêng:

~~~bash
mvn -Dtest=AopMappingTest test
~~~

Suite gồm **4 test methods**; summary Maven Surefire **dự kiến**:

~~~text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
~~~

Đây là kết quả mong đợi theo code và assertions, không phải bản sao log của một lần Maven đã chạy trong tài liệu.

## 10. Mapping thứ tự advice và advisor

Các cơ chế đều có cách điều khiển thứ tự, nhưng quy tắc không chỉ là thứ tự dòng code:

| Cách | Công cụ ordering | Chú ý |
|---|---|---|
| **@AspectJ** | **@Order** trên aspect hoặc implements **Ordered** | Với nhiều aspect, xác định precedence rõ; không dựa vào thứ tự khai báo class |
| **XML schema** | Thuộc tính **order** trên **<aop:aspect>** / **<aop:advisor>** theo cấu hình phù hợp | Khi có nhiều aspects/advisors, định nghĩa thứ tự thực thi |
| **AOP API** | **Ordered** trên advisors, **setOrder(...)** khi hỗ trợ; thứ tự advisor trong proxy factory | Không suy ra ngữ nghĩa chỉ từ tên advisor |

Với **@Around**, thứ tự advisor ảnh hưởng giá trị timing, exception và thứ tự before/after. Trong ví dụ chỉ có một advice match cho **place()**, nên không có kết quả chồng chéo.

Nếu ứng dụng cùng có **@Transactional**, caching, method security hoặc retry, phải kiểm tra thứ tự advisor thực tế. Một advice ở ngoài transaction boundary có thể quan sát trạng thái khác với advice ở bên trong. Đừng trộn ba kiểu cấu hình chỉ để chỉnh ordering khi không có nhu cầu thực.

## 11. XML ProxyFactoryBean không đồng nghĩa <aop:config>

Một cách XML thấp hơn là dùng **org.springframework.aop.framework.ProxyFactoryBean**:

~~~xml
<bean id="proxiedOrderService"
      class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="target" ref="plainOrderService"/>
    <property name="proxyInterfaces">
        <list>
            <value>demo.OrderPort</value>
        </list>
    </property>
    <property name="interceptorNames">
        <list>
            <value>orderAdvisor</value>
        </list>
    </property>
</bean>
~~~

Đây là **mảnh XML minh họa**, chưa bao gồm khai báo **plainOrderService**, **orderAdvisor** và các collaborators; không được thêm nguyên xi vào project mẫu rồi xem là hoàn chỉnh.

**ProxyFactoryBean** tạo proxy thông qua BeanFactory và cần biết những advisor/interceptor nào được áp dụng. Còn **<aop:config>** khai báo aspect/advisor với schema rồi để Spring hạ tầng auto-proxy lựa chọn bean phù hợp. **ProxyFactory** trong Java chủ động gọi **getProxy()**. Cả ba có thể thực hiện interception nhưng khác cách tạo/đăng ký proxy và cách gắn nó vào container.

## 12. Khi nào chuyển đổi có thể sai dù cú pháp trông giống?

### 12.1. Nhầm @Aspect thành Spring bean

**@Aspect** không tự là **@Component**. Nếu đưa aspect từ XML sang Java annotation, cần **@Bean** hoặc **@Component** cùng component scan đúng package. Nếu thiếu, Spring có thể không tạo advisor và target không có advice.

### 12.2. Chép pointcut mà đổi bean name

**bean(orderService)** chọn theo tên Spring bean; **orderService** trong biểu thức không tự bằng tên Java class. Nếu XML sử dụng **<bean id="orderPort">**, chuyển sang @Bean method **orderService()** sẽ đổi tên bean mặc định và có thể làm pointcut bean(...) không còn match.

### 12.3. Dùng API proxy mà client vẫn giữ target gốc

~~~java
OrderService target = new OrderService(log);
ProxyFactory factory = new ProxyFactory(target);
// Thêm advisor...
OrderPort proxy = (OrderPort) factory.getProxy();

// target.place("X") bỏ qua AOP proxy.
// proxy.place("X") đi qua AOP proxy.
~~~

Đây là mảnh minh họa, biến **log** và advisor cần được tạo tương ứng. Phải bảo đảm chính object được client sử dụng là proxy.

### 12.4. Đặt cả annotation và XML advice cho cùng concern

Nếu đăng ký cả **@Aspect** và một XML **<aop:aspect>** cùng ghi audit trước method, một invocation có thể nhận **hai lần ghi event**. Việc dùng nhiều kiểu cấu hình trong cùng application không sai về nguyên tắc, nhưng cần nhận diện advisor nào thực thi và tránh trùng chức năng.

### 12.5. Trộn cấu hình proxy JDK/CGLIB

**proxyTargetClass=true** ở một phần cấu hình có thể nâng proxy type toàn context theo cơ chế auto-proxy chung. Vì vậy việc thêm XML config vào ứng dụng dùng annotation đôi khi làm thay đổi injection theo concrete type. Hãy test **AopUtils.isJdkDynamicProxy** hoặc **isCglibProxy** tại runtime.

### 12.6. Bỏ qua self-invocation

Dù dùng Java @AspectJ, XML schema hay ProxyFactory, **this.method()** từ bên trong target không quay lại proxy. Chọn CGLIB không tự sửa được điều này. Nếu thực sự cần interception của self-invocation, nên xem lại ranh giới Spring beans/đường gọi hoặc đánh giá AspectJ weaving.

### 12.7. Đổi exception hoặc giá trị trả về trong @Around

Một XML around advice hay MethodInterceptor có thể gọi **proceed()** rồi thay kết quả, hoặc catch exception rồi trả fallback. Khi chuyển từ @Before sang API MethodInterceptor, test phải xác nhận code mới **gọi proceed đúng số lần**, trả đúng value và giữ exception contract. Ánh xạ về mục tiêu "chạy trước" không bảo đảm implementation tự an toàn.

### 12.8. Nhầm AOP infrastructure với dịch vụ khác của Spring

**@EnableTransactionManagement** và **<tx:annotation-driven>** có liên hệ với cơ chế proxy/transaction interceptor, nhưng **không phải ánh xạ trực tiếp của @Aspect** do bạn tự viết. Tương tự, @Async, @Cacheable hoặc method security có policy và infrastructure riêng. Không thay chúng bằng một XML advice tùy ý rồi xem là cùng ngữ nghĩa.

## 13. Bảng tổng kết “nhìn ký hiệu, biết vai trò”

| Ký hiệu trong code | Diễn giải nhanh |
|---|---|
| **@Aspect** | Lớp chứa advice kiểu @AspectJ; cần được tạo thành Spring bean nếu auto-proxy |
| **@Pointcut** | Đặt tên cho pointcut expression có thể tái sử dụng |
| **@Before / @AfterReturning / @AfterThrowing / @After** | Advice có semantics trước, sau thành công, sau lỗi, hoặc finally |
| **@Around** | Advice điều khiển proceed, có quyền ảnh hưởng return/exception |
| **@EnableAspectJAutoProxy** | Bật phát hiện @Aspect beans và auto-proxy trong Spring context |
| **<aop:aspectj-autoproxy/>** | XML tương ứng của việc kích hoạt @AspectJ auto-proxy |
| **<aop:config>** | Khai báo Spring schema-based aspects/advisors |
| **<aop:aspect ref="...">** | Dùng bean Java có advice methods được XML liên kết |
| **<aop:pointcut>** | Named pointcut trong XML |
| **<aop:before>, <aop:around>, ...** | Liên kết advice method với pointcut trong XML |
| **Pointcut** | API chọn class/method có thể match |
| **Advice / MethodInterceptor** | API cho logic interception |
| **Advisor / DefaultPointcutAdvisor** | API ghép pointcut và advice |
| **ProxyFactory** | API tạo proxy trực tiếp từ target và advisors |
| **ProxyFactoryBean** | FactoryBean Spring quản lý một proxy được cấu hình qua bean definitions |
| **AspectJProxyFactory** | Tạo proxy chương trình từ @AspectJ aspects |
| **AopUtils.isAopProxy(...)** | Kiểm tra một object là Spring AOP proxy |
| **AopUtils.isJdkDynamicProxy(...)** | Kiểm tra JDK interface proxy |
| **AopUtils.isCglibProxy(...)** | Kiểm tra CGLIB/class-based proxy |

## 14. Quy trình đọc một project Spring đã có

Khi nhận một codebase dùng lẫn annotation, XML và API:

1. **Tìm điểm bật proxy:** @EnableAspectJAutoProxy, XML aop:aspectj-autoproxy/aop:config, Spring Boot AOP auto-config hoặc bean ProxyFactoryBean.
2. **Tìm aspect/advisor:** lớp @Aspect, XML aop:aspect, DefaultPointcutAdvisor, MethodInterceptor, bean advisors.
3. **Ghi ra mỗi pointcut thực tế:** expression, package, annotation và bean name liên quan.
4. **Kiểm tra target bean:** class/interface thực, nơi đăng ký và cách client lấy bean.
5. **Xem advice có quyền đổi behavior không:** @Around, MethodInterceptor, retry/cache/transaction ordering.
6. **Kiểm tra trùng advisor:** có cùng concern được áp dụng bởi cả annotation và XML không?
7. **Chạy integration test qua context:** xác nhận proxy type, method match và behavior đúng.
8. **Chỉ refactor một style sang style khác khi có regression tests:** giữ cùng business contract, pointcut scope và số lần side effect.

Với codebase nhỏ, **@AspectJ + @EnableAspectJAutoProxy** thường là cách dễ đọc và gần code Java. Với legacy enterprise apps, XML schema/ProxyFactoryBean vẫn có thể đang cung cấp contract quan trọng. Với yêu cầu hạ tầng tùy biến, API mang lại quyền kiểm soát advisor/proxy lớn hơn nhưng cũng đòi hỏi quản lý nhiều chi tiết hơn.

## 15. Bài ôn tập

**Câu 1.** Spring XML **<aop:aspectj-autoproxy/>** có đồng nghĩa AspectJ weaving không?

**Không.** Nó kích hoạt Spring AOP proxy cho các aspect bean theo annotation @AspectJ.

**Câu 2.** Muốn chuyển **@Before("pc()")** sang XML schema, cần những gì?

Một bean chứa advice method, một pointcut XML tương ứng và phần tử **<aop:before method="..." pointcut-ref="..."/>** trong **<aop:aspect ref="...">**.

**Câu 3.** Vì sao MethodInterceptor không phải bản sao 1:1 của @Before?

MethodInterceptor kiểm soát **invocation.proceed()** và có thể short-circuit, thay return/exception; before advice có phạm vi can thiệp hạn chế hơn.

**Câu 4.** **ProxyFactory** khác Spring auto-proxy thế nào?

ProxyFactory tạo proxy thủ công; caller phải dùng đối tượng **getProxy()**. Spring auto-proxy thay bean exposure bằng proxy trong ApplicationContext khi cấu hình/aspect khớp.

**Câu 5.** Chỉ **AopUtils.isAopProxy(port)=true** có chứng minh advice ghi audit chạy đúng không?

Không. Phải gọi đúng method qua proxy và assert effect của advice, đồng thời test method không thuộc phạm vi.

**Câu 6.** Vì sao thay **bean(orderPort)** thành **bean(orderService)** có thể làm mất advice?

Vì **bean(...)** dựa trên tên Spring bean. Đổi identifier có thể thay tập bean được match.

**Câu 7.** Có nên trộn cả ba cách cấu hình vào một ApplicationContext để so sánh không?

Không cần. Để test đối chiếu chính xác, nên dùng context/proxy độc lập rồi so contract. Nếu production thực sự dùng hỗn hợp, cần kiểm tra advisor trùng, ordering và proxy configuration chung.

## 16. Checklist khi cần ánh xạ cấu hình

- [ ] Đã phân biệt **<aop:aspectj-autoproxy/>** với **<aop:config>**.
- [ ] @Aspect class/bean XML/advisor API được đăng ký đúng nơi.
- [ ] Pointcut expression và bean names giữ nguyên ý nghĩa.
- [ ] Advice type và các binding return/exception được chuyển đúng.
- [ ] Caller nhận proxy từ Spring hoặc từ ProxyFactory.getProxy().
- [ ] JDK/CGLIB được kiểm tra theo runtime; không dựa vào giả định.
- [ ] @Around hoặc MethodInterceptor không vô tình đổi value/throw/proceed count.
- [ ] Không dùng một cấu hình proxy AOP như công tắc AspectJ weaving.
- [ ] Không dùng self-invocation làm bằng chứng pointcut bị sai.
- [ ] Đã có test dương tính/âm tính xác minh advice thực thi đúng.
- [ ] Không để cùng một concern bị advice hai lần do cấu hình trùng.
- [ ] Nếu dùng transaction/cache/security, giữ hạ tầng/chính sách riêng của tính năng đó.

## 17. Tài liệu chính thức

- Spring Framework 6.2 — Schema-based AOP Support: https://docs.spring.io/spring-framework/reference/6.2/core/aop/schema.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Declaring Pointcuts: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html
- Spring Framework — Declaring Advice: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — Spring AOP APIs: https://docs.spring.io/spring-framework/reference/core/aop-api.html
- Spring Framework — Pointcut API: https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html
- Spring Framework — Advisor API: https://docs.spring.io/spring-framework/reference/core/aop-api/advisor.html
- Spring Framework — ProxyFactory programmatic API: https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html
- Spring Framework — ProxyFactoryBean: https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html
- Spring Framework — Programmatic @AspectJ proxy creation: https://docs.spring.io/spring-framework/reference/core/aop/aspectj-programmatic.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-review-002">Bảng so sánh proxy JDK, proxy CGLIB và tích hợp AspectJ trong Spring</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng so sánh proxy JDK, proxy CGLIB và tích hợp AspectJ trong Spring

## 1. Đọc nhanh: ba lựa chọn không cùng tầng cơ chế

Khi một `@Aspect` được khai báo trong Spring, có **hai cách thực thi rất khác nhau**:

- **Spring AOP dựa trên proxy:** Spring tạo đối tượng trung gian và áp dụng advisor vào **method execution đi qua proxy**. Proxy có thể là **JDK dynamic proxy** (implements interface) hoặc **class-based proxy CGLIB** (subclass của target).
- **AspectJ weaving tích hợp trong ứng dụng Spring:** AspectJ sửa bytecode của lớp được weave, thường ở **compile time (CTW)** hoặc **load time (LTW)**. Spring hỗ trợ việc kết hợp AspectJ với container, đặc biệt bằng `@EnableLoadTimeWeaving`/`LoadTimeWeaver`, nhưng việc weaving **không phải** Spring AOP proxy.

Vì vậy, **JDK proxy và CGLIB là hai chiến lược của Spring AOP**; còn **AspectJ weaving là cơ chế khác**, không đơn thuần là “loại proxy thứ ba”.

Bảng tra cứu tổng quát:

| Tiêu chí | Spring AOP: JDK proxy | Spring AOP: CGLIB proxy | AspectJ CTW/LTW trong ứng dụng Spring |
| --- | --- | --- | --- |
| Vị trí intercept | Invocation đi vào interface proxy | Invocation đi vào class-based proxy | Bytecode của class đã được weave |
| Đối tượng trung gian | Proxy implement business interfaces | Proxy subclass target class | Không bắt buộc có Spring AOP proxy cho advice đã weave |
| Điều kiện chọn | Target có interface và không buộc class proxy | Target không có interface, hoặc cấu hình ép class proxy | Cấu hình AspectJ compiler/weaver/agent và aspect, độc lập lựa chọn Spring proxy |
| Bean bắt buộc được Spring quản lý? | **Có** đối với Spring AOP tự tạo proxy | **Có** đối với Spring AOP tự tạo proxy | **Không bắt buộc**: class/instance đã weave có thể không do Spring tạo |
| Join point trong phạm vi thường dùng | Method execution trên Spring bean qua proxy | Method execution trên Spring bean qua proxy | Có thể có method/constructor execution, call, field access... tùy pointcut và class được weave |
| Interface method | Thuận tiện khi gọi qua interface proxy | Có, nhưng proxy là subclass | Có nếu bytecode thích hợp được weave |
| Public method chỉ có trong class, không có trên interface | Thường không gọi/advice được qua JDK interface proxy | Có thể advice nếu override được | Có thể advice bằng weaving |
| Private method | Không được intercept qua JDK proxy | Không override được nên không được advice | Method execution của private method có thể được weave |
| Final method | **Có thể** được advice khi lời gọi đi qua method của JDK interface proxy dù implementation final | Không override được nên không advice | Có thể được weave tại bytecode, nếu pointcut phù hợp |
| Final class | Có thể proxy qua business interface | Không subclass được | Có thể weave bytecode của class phù hợp |
| Self-invocation: `this.method()` trong target | Bỏ qua Spring proxy nên không kích hoạt advice riêng của method đó | Tương tự | Advice `execution` có thể chạy nếu method đó được weave |
| Đối tượng tạo bằng `new` | Không tự có Spring proxy | Không tự có Spring proxy | Có thể nhận advice nếu class tương ứng được weave |
| Thời điểm cần chuẩn bị | Lúc Spring tạo/configure bean proxy | Lúc Spring tạo/configure bean proxy | CTW: build; LTW: lúc ClassLoader định nghĩa class trước khi chạy |
| Thư viện/hạ tầng | Spring AOP, AspectJ annotation support nếu dùng `@Aspect` | Spring AOP + CGLIB được tích hợp trong Spring Core | AspectJ compiler/weaver/runtime, cấu hình weaving; Spring LTW là tích hợp tùy chọn |
| Cấu hình Spring tiêu biểu | `@EnableAspectJAutoProxy(proxyTargetClass=false)` | `@EnableAspectJAutoProxy(proxyTargetClass=true)` | `@EnableLoadTimeWeaving` và `META-INF/aop.xml` cho Spring-managed LTW |
| Khi nào chọn | Có interface ổn định, chỉ cần intercept lời gọi qua bean | Cần intercept method public/protected phù hợp ở concrete class | Cần xử lý join point ngoài proxy như self-invocation/private/constructor/field hoặc non-Spring objects |
| Rủi ro vận hành | Interface injection, kiểu proxy khác target concrete type | Giới hạn `final`/`private` và class/module visibility | Build/agent, ClassLoader, thời điểm nạp class, phạm vi weaving, debug phức tạp |
| Độ phức tạp | Thường ít nhất | Tăng khi concrete class và final/module constraints | Cao hơn vì quản lý bytecode weaving và toolchain |

**Không suy luận tốc độ:** JDK proxy, CGLIB và weaving có chi phí khác nhau tùy số advisor, logic advice, JIT, cách deploy, tần suất gọi. Không có quy tắc phổ quát rằng một lựa chọn luôn nhanh nhất. Đo benchmark đúng trường hợp sử dụng và tập trung giảm I/O/logic nặng trong advice trước khi tranh luận vài nanosecond proxy dispatch.

## 2. Ranh giới Spring proxy là yếu tố quyết định

Sơ đồ hai chiến lược Spring AOP:

~~~text
Client -> Spring-managed bean reference (PROXY)
             |
             +-- JDK dynamic proxy implements ServicePort
             |      -> advice/interceptor chain
             |      -> target ServiceImpl.method()
             |
             +-- CGLIB subclass proxy of ServiceImpl
                    -> advice/interceptor chain
                    -> target ServiceImpl.method()

Trong cả hai trường hợp, advice riêng của method chỉ xuất hiện
khi lời gọi đi qua proxy được Spring cung cấp và pointcut khớp.
~~~

Ví dụ đối với `ServiceImpl`:

~~~java
public class ServiceImpl implements ServicePort {
    @Override
    public String outer() {
        return this.inner(); // self-invocation: bỏ qua Spring AOP proxy
    }

    @Override
    public String inner() {
        return "ok";
    }
}
~~~

Đây là **đoạn minh họa**, cần interface `ServicePort` được khai báo. Nếu pointcut chỉ chọn `inner()`, gọi `service.outer()` qua proxy không khiến advice `inner` chạy; gọi `service.inner()` từ client qua proxy thì advice có thể chạy.

**Thay JDK bằng CGLIB không chữa self-invocation.** Vấn đề là `this` của target, không phải kiểu proxy bên ngoài. Cách sửa ưu tiên khi thực sự cần advice là đưa `inner()` sang một **bean cộng tác** rồi inject/gọi qua Spring proxy. Cách khác như self-injection hay `AopContext.currentProxy()` làm tăng coupling với Spring AOP và cần cân nhắc cẩn trọng.

AspectJ weaving có thể khác: nếu bytecode `ServiceImpl.inner()` đã được weave và pointcut là method execution, lời gọi `this.inner()` vẫn thực thi body woven, nên advice có thể chạy. Đây là một trong các lý do chọn weaving khi không thể đổi cấu trúc bean.

## 3. Cách Spring lựa chọn JDK hoặc CGLIB

### 3.1. Java configuration

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = false)
class InterfaceProxyConfig {
}
~~~

Nếu target implements business interface và hạ tầng không có cấu hình khác ép class proxy, Spring thường tạo **JDK dynamic proxy**.

~~~java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class ClassProxyConfig {
}
~~~

`proxyTargetClass=true` yêu cầu **class-based proxy**. Các đoạn trên là **các lựa chọn cấu hình minh họa**, không dùng cùng lúc để so sánh trong một Spring context.

XML tương đương cho annotation-style aspects:

~~~xml
<aop:aspectj-autoproxy proxy-target-class="false"/>
<!-- Hoặc, ở context riêng: -->
<aop:aspectj-autoproxy proxy-target-class="true"/>
~~~

`<aop:aspectj-autoproxy>` cho phép Spring xử lý `@Aspect` **đã đăng ký thành bean**; đừng nhầm với `<aop:config>` khai báo schema-style advisor/pointcut trực tiếp bằng XML. `@Aspect` không tự tạo Spring bean: thêm `@Component` để component scan nhận hoặc đăng ký bằng `@Bean`.

### 3.2. Spring Boot và cấu hình hợp nhất

Trong các phiên bản Spring Boot dùng AOP auto-configuration, có thuộc tính:

~~~properties
spring.aop.proxy-target-class=false
~~~

Giá trị `false` yêu cầu ưu tiên JDK proxy khi có interface; `true` yêu cầu class proxy. Nhiều thế hệ Spring Boot mặc định chọn class proxy; **không coi đây là mặc định cố định của mọi version hoặc mọi môi trường**.

Quan trọng hơn: nhiều yêu cầu auto-proxy trong một context (Spring AOP, transaction advisors, XML hoặc Java config) có thể được Spring **gộp lại**. Nếu một cấu hình buộc `proxyTargetClass=true`, ý định dùng JDK proxy của cấu hình khác có thể bị lấn át. Hãy kiểm tra object thực bằng `AopUtils` thay vì chỉ nhìn annotation/property ở một chỗ.

~~~java
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("orderService");
System.out.println("AOP proxy : " + AopUtils.isAopProxy(bean));
System.out.println("JDK proxy : " + AopUtils.isJdkDynamicProxy(bean));
System.out.println("CGLIB     : " + AopUtils.isCglibProxy(bean));
System.out.println("Target    : " + AopUtils.getTargetClass(bean));
~~~

Đây là **đoạn chèn vào test/debug có biến `context` và bean name tương ứng**, không phải chương trình độc lập. Kiểu proxy và class name có thể khác nhau giữa Spring version, Boot profile và app-server ClassLoader.

### 3.3. Hai câu hỏi chẩn đoán hữu ích

- **Cần inject theo concrete class?** JDK dynamic proxy thường không assignable về concrete `ServiceImpl`; inject qua `ServicePort` nếu được. CGLIB proxy subclass thường assignable về target class, nhưng có giới hạn final/module.
- **Có method final/private hoặc final class?** Với class proxy, các method không override được không thể advice. Với JDK proxy, implementation method `final` vẫn có thể được advice *khi được gọi qua interface method*; điều này **không** có nghĩa private method trở thành interceptable.

## 4. Ví dụ chạy được: so sánh JDK proxy và CGLIB với cùng pointcut

Ví dụ dùng **Java 21, Gradle, JUnit 5 và Spring Framework** lấy dependency từ BOM Spring Boot 3.3.5. **Không bật AspectJ weaving.** Test tạo hai Spring contexts riêng, tránh các cấu hình ép proxy ảnh hưởng lẫn nhau.

### 4.1. Cấu trúc

~~~text
spring-aop-compare/
├── settings.gradle
├── build.gradle
└── src/test/java/com/example/proxycompare/
    └── ProxyStrategyComparisonTest.java
~~~

`settings.gradle`:

~~~groovy
rootProject.name = 'spring-aop-compare'
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

`src/test/java/com/example/proxycompare/ProxyStrategyComparisonTest.java`:

~~~java
package com.example.proxycompare;

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

public class ProxyStrategyComparisonTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class JdkConfig {
        @Bean
        TaskPort taskPort() {
            return new TaskService();
        }

        @Bean
        CountProbe countProbe() {
            return new CountProbe();
        }

        @Bean
        TraceAspect traceAspect(CountProbe probe) {
            return new TraceAspect(probe);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CglibConfig {
        @Bean
        TaskPort taskPort() {
            return new TaskService();
        }

        @Bean
        CountProbe countProbe() {
            return new CountProbe();
        }

        @Bean
        TraceAspect traceAspect(CountProbe probe) {
            return new TraceAspect(probe);
        }
    }

    @Test
    void jdkProxyInterceptionAndSelfInvocation() {
        try (var context = new AnnotationConfigApplicationContext(
                JdkConfig.class)) {

            TaskPort service = context.getBean(TaskPort.class);
            CountProbe probe = context.getBean(CountProbe.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertTrue(AopUtils.isJdkDynamicProxy(service));
            assertFalse(AopUtils.isCglibProxy(service));

            assertEquals("ok", service.outer());
            assertEquals(0, probe.count()); // this.inner(): bypass

            assertEquals("ok", service.inner());
            assertEquals(1, probe.count()); // Proxy -> advice
            assertEquals("ready", service.status());
            assertEquals(1, probe.count()); // Non-match
        }
    }

    @Test
    void cglibProxyInterceptionAndSelfInvocation() {
        try (var context = new AnnotationConfigApplicationContext(
                CglibConfig.class)) {

            TaskPort service = context.getBean(TaskPort.class);
            CountProbe probe = context.getBean(CountProbe.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertTrue(AopUtils.isCglibProxy(service));
            assertFalse(AopUtils.isJdkDynamicProxy(service));

            assertEquals("ok", service.outer());
            assertEquals(0, probe.count()); // CGLIB vẫn bypass self-call

            assertEquals("ok", service.inner());
            assertEquals(1, probe.count());
            assertEquals("ready", service.status());
            assertEquals(1, probe.count());
        }
    }
}

interface TaskPort {
    String outer();
    String inner();
    String status();
}

class TaskService implements TaskPort {
    @Override
    public String outer() {
        return this.inner();
    }

    @Override
    public String inner() {
        return "ok";
    }

    @Override
    public String status() {
        return "ready";
    }
}

class CountProbe {
    private final AtomicInteger hits = new AtomicInteger();

    void hit() {
        hits.incrementAndGet();
    }

    int count() {
        return hits.get();
    }
}

@Aspect
class TraceAspect {
    private final CountProbe probe;

    TraceAspect(CountProbe probe) {
        this.probe = probe;
    }

    @Before("execution(* com.example.proxycompare..*.inner(..))")
    public void beforeInner() {
        probe.hit();
    }
}
~~~

Mỗi JUnit method dựng **context mới** và `CountProbe` mới; do đó không có counter tích lũy xuyên test. `@Aspect` được đăng ký thành Spring bean qua `@Bean`, không cần `@Component`; aspect chỉ match `inner()`, còn `outer()` và `status()` không match.

Chạy với Gradle hỗ trợ Java 21:

~~~bash
gradle test --tests com.example.proxycompare.ProxyStrategyComparisonTest
~~~

Nếu dự án có Gradle wrapper, có thể thay bằng `./gradlew test --tests com.example.proxycompare.ProxyStrategyComparisonTest` (PowerShell: `.\gradlew.bat test --tests com.example.proxycompare.ProxyStrategyComparisonTest`).

**Kỳ vọng:** 2 JUnit tests thành công. Đây là mã tham khảo cần chạy tại máy có Gradle/Java tương thích; không coi đây là output đã thực thi. Gradle không có stdout cố định vì log phụ thuộc version và terminal.

### Ma trận kết quả của bài test

| Context | Lời gọi | `CountProbe.count()` sau lời gọi | Kết luận |
| --- | --- | --- | --- |
| JDK proxy | `service.outer()` → `this.inner()` | 0 | Self-invocation bypass JDK proxy |
| JDK proxy | `service.inner()` | 1 | Advice chạy qua interface proxy |
| JDK proxy | `service.status()` | Vẫn 1 | Method không khớp |
| CGLIB proxy | `service.outer()` → `this.inner()` | 0 | Self-invocation cũng bypass CGLIB |
| CGLIB proxy | `service.inner()` | 1 | Advice chạy qua class proxy |
| CGLIB proxy | `service.status()` | Vẫn 1 | Method không khớp |

**Test không kiểm tra weaving.** Nếu đưa AspectJ agent/weaving vào cùng tiến trình, test có thể có kết quả khác hoặc advice trùng; tuyệt đối không dùng ma trận proxy này để khẳng định hành vi của AspectJ CTW/LTW.

## 5. AspectJ tích hợp Spring: weaving thay vì thêm proxy

AspectJ có thể được tích hợp vào ứng dụng Spring khi yêu cầu không nằm trong ranh giới method execution qua Spring proxy, chẳng hạn intercept **self-invocation**, **đối tượng không do Spring tạo**, hoặc một số method/constructor/field join point.

### 5.1. Hai cách weave cần phân biệt

| Cách | Bytecode biến đổi khi nào? | Tích hợp/giới hạn liên quan Spring |
| --- | --- | --- |
| **Compile-time weaving (CTW)** | Compiler/weaver của AspectJ biến đổi class trong quá trình build | Spring container vẫn có thể tạo và inject beans bình thường; không cần transformer LTW cho class đã woven |
| **Load-time weaving (LTW)** | Transformer AspectJ biến đổi bytecode trước khi JVM định nghĩa lớp | Spring có `LoadTimeWeaver` và `@EnableLoadTimeWeaving` để cấu hình theo ClassLoader/context phù hợp; hoặc JVM dùng AspectJ `-javaagent` |

**LTW phụ thuộc thời điểm:** transformer phải sẵn sàng với ClassLoader định nghĩa target class **trước khi class đó được JVM định nghĩa**. Nếu target đã load/define trước khi Spring-managed LTW được kích hoạt, việc tạo lại bean không tự weave bytecode cũ.

**Spring AOP không có điều kiện này:** class target được JVM load từ trước vẫn có thể được Spring bọc proxy khi khởi tạo bean. Vì vậy, không áp quy tắc “transformer trước class loading” của LTW để chẩn đoán mọi lỗi Spring proxy.

### 5.2. Ví dụ cấu hình Spring-managed LTW

~~~java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableLoadTimeWeaving;

@Configuration
@EnableLoadTimeWeaving(
    aspectjWeaving = EnableLoadTimeWeaving.AspectJWeaving.ENABLED
)
public class AspectJLoadTimeConfig {
}
~~~

Đoạn trên là **mảnh cấu hình dùng trong ứng dụng Spring**, không phải chương trình Java độc lập. Cần `aspectjweaver` ở runtime, ClassLoader/instrumentation tương thích và cấu hình AspectJ `META-INF/aop.xml` có thể được loader nhìn thấy.

Ví dụ `src/main/resources/META-INF/aop.xml`:

~~~xml
<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD//EN"
    "https://www.eclipse.org/aspectj/dtd/aspectj.dtd">
<aspectj>
    <aspects>
        <aspect name="com.example.tracing.TimingAspect"/>
    </aspects>
    <weaver options="-showWeaveInfo">
        <include within="com.example.service..*"/>
    </weaver>
</aspectj>
~~~

Ví dụ chỉ khai báo một **aspect name giả định** và package cần weave: phải tạo `com.example.tracing.TimingAspect` và thêm đúng dependencies trước khi chạy. `aop.xml` là metadata của AspectJ weaver, **không phải** Spring `<aop:aspectj-autoproxy>`.

Một con đường khác khi bạn điều khiển JVM startup:

~~~bash
java -javaagent:/absolute/path/aspectjweaver.jar -jar application.jar
~~~

Ở đây `aspectjweaver.jar` là JAR thật trên filesystem, không đơn thuần là đường dẫn tới nested dependency trong executable Boot JAR. Với Spring-managed LTW ở môi trường ClassLoader không tự hỗ trợ instrumentation, có thể cần `-javaagent:/absolute/path/spring-instrument.jar` từ JVM startup để Spring đăng ký transformer; **`spring-instrument` và `aspectjweaver` không hoán đổi vai trò**.

Một khi đã dùng AspectJ weaving để advice một target, **đừng đồng thời đăng ký cùng aspect như Spring AOP bean một cách vô thức**: advisor proxy và woven advice có thể cùng tác động làm advice chạy hai lần. Phải kiểm tra ai quản lý aspect instance, phương thức nào được weave, và advisor nào còn áp trên proxy Spring.

### 5.3. Mô phỏng điều khác biệt với self-invocation

Giả sử có woven aspect:

~~~java
package com.example.tracing;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class TimingAspect {
    @Before("execution(* com.example.service.MyService.inner(..))")
    public void beforeInner() {
        System.out.println("woven beforeInner");
    }
}
~~~

Nếu `MyService` được weave bằng AspectJ theo cấu hình tương ứng, **method execution của `inner` đã chứa advice**, nên lời gọi `this.inner()` bên trong `outer()` có thể in `woven beforeInner`. Đây là **mã minh họa phụ thuộc CTW/LTW**, không phải một thí nghiệm chạy được chỉ bằng cách thêm `@EnableAspectJAutoProxy`.

Không nhầm `@Aspect` là bảo chứng của weaving: cùng một class `@Aspect` có thể được Spring xử lý theo proxy hoặc AspectJ xử lý theo weaving tùy **đăng ký và pipeline triển khai**.

## 6. Chọn cơ chế theo yêu cầu thực tế

| Yêu cầu trong Spring | Lựa chọn ưu tiên | Vì sao / kiểm tra gì |
| --- | --- | --- |
| Logging/audit/tracing cho public service method được gọi qua Spring bean | JDK proxy nếu API interface phù hợp; CGLIB nếu cần class method | Đơn giản, dễ test, không cần weaving |
| Ứng dụng thiết kế theo interfaces, inject theo interface | JDK dynamic proxy | API/proxy boundary rõ; JDK proxy không phải subclass concrete target |
| Cần intercept concrete class method không có trong interface | CGLIB, nếu class/method override được | Chọn class proxy và test `final`/`private` |
| Class `final` có interface nghiệp vụ và chỉ intercept các interface methods | JDK proxy thường phù hợp | Implementation `final` không chặn invocation handler của interface proxy |
| Phải intercept private/final method hoặc self-invocation, không thể tách bean | Đánh giá AspectJ weaving | Bỏ giới hạn proxy, đổi lại chi phí build/instrumentation và kiểm chứng bytecode |
| Intercept constructor/field access hoặc new object không phải Spring bean | AspectJ weaving | Ngoài tập join point Spring AOP proxy |
| Cần kiểm soát weaving theo ClassLoader trong application server | Spring-managed AspectJ LTW nếu môi trường hỗ trợ | Kiểm tra `LoadTimeWeaver`, `aop.xml` và thời điểm class loading |
| Cần test dễ ổn định khi refactor | Spring AOP proxy với Spring context + positive/negative tests | Dễ kiểm chứng target/proxy, pointcut, return, side effects |
| Nâng cấp Boot/Framework, chạy native/AOT | Kiểm tra khả năng sinh proxy/hints theo version cụ thể | Native build cần cấu hình build-time phù hợp; weaving runtime không mặc nhiên tương thích |

**Thứ tự ưu tiên thường hợp lý:** dùng Spring proxy cho method boundary thông thường; sửa thiết kế bằng bean cộng tác nếu chỉ vướng self-invocation; dùng AspectJ weaving khi yêu cầu thật sự vượt giới hạn proxy và độ phức tạp thêm có giá trị.

## 7. Cách kiểm thử/quan sát để tránh kết luận nhầm

**Với Spring AOP proxy**, ở đúng `ApplicationContext` cần chẩn đoán:

~~~java
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

Object bean = context.getBean("taskPort");
System.out.println("proxy=" + AopUtils.isAopProxy(bean));
System.out.println("jdk=" + AopUtils.isJdkDynamicProxy(bean));
System.out.println("cglib=" + AopUtils.isCglibProxy(bean));

if (bean instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
~~~

Đây là **đoạn debug chèn vào test có context và bean name**, không phải chương trình độc lập. Có proxy/advisor chưa chứng minh pointcut khớp method: luôn kiểm tra **advice thực chạy đúng một lần** và trường hợp không được chạy.

**Với AspectJ LTW**, kiểm tra thêm:

- JVM agent/`spring-instrument` và thời điểm transformer được đăng ký.
- Defining ClassLoader của target class, aspect visibility và mọi `META-INF/aop.xml` trên loader tương ứng.
- Log `-showWeaveInfo` cùng class loading log; điều quan trọng là **target class đã được weave hay chưa**, không chỉ Spring bean có proxy hay không.
- Hành vi khi chạy self-invocation hoặc object không thuộc Spring (ca chỉ weaving mới có thể xử lý).
- Có đồng thời Spring proxy và woven advice không; nếu có, test tránh số lần advice bị nhân đôi.

Với mọi chiến lược, nếu advice liên quan `@Transactional`, caching, `@Async`, authorization hoặc audit quan trọng, ngoài count cần test exception, rollback, kết quả dữ liệu và các lần gọi âm tính. Một test chỉ đếm sự xuất hiện của log không đủ khẳng định hợp đồng nghiệp vụ được bảo toàn.

### Lỗi chẩn đoán phổ biến

| Triệu chứng | Nguyên nhân cần xác minh |
| --- | --- |
| `@Aspect` được khai báo nhưng không có advice | Chưa là Spring bean, chưa bật Spring auto-proxy; hoặc với weaving chưa được khai trong `aop.xml`/build |
| `this.inner()` không có advice trên JDK/CGLIB | Spring proxy không intercept self-invocation |
| Class-based proxy không advice method final/private | CGLIB không thể override method ấy |
| JDK proxy không inject vào field concrete class | JDK proxy implement interface, không kế thừa target concrete class |
| AspectJ LTW không áp dụng cho class | Weaver quá muộn, loader khác, `aop.xml`/include không khớp, aspect không thấy được |
| Spring Boot thay default proxy mode sau đổi profile/config | Property và hạ tầng auto-proxy hợp nhất khác nhau |
| Advice chạy hai lần | Aspect được kích hoạt đồng thời bởi proxy + weaving hoặc đăng ký trùng advisors |
| Native image AOP khác JVM | Generated proxy metadata, native runtime hints hoặc build-time bean graph cần kiểm tra |

## 8. Checklist ôn tập

1. **JDK và CGLIB đều là Spring AOP proxy**: chúng chỉ khác đối tượng proxy/khả năng override, không khác nguyên lý self-invocation.
2. **`@Aspect` không đồng nghĩa AspectJ weaving**: annotation có thể được Spring xử lý bằng `@EnableAspectJAutoProxy`.
3. **JDK proxy** thích hợp với interface API; implementation method `final` vẫn có thể được advice qua interface method.
4. **CGLIB** proxy subclass: không advice được final/private methods, không subclass final class.
5. **AspectJ CTW/LTW** can thiệp bytecode: có thể xử lý self-invocation, non-Spring objects và thêm loại join point khi class được weave đúng.
6. **Spring-managed LTW** liên quan `LoadTimeWeaver`, ClassLoader và `META-INF/aop.xml`; agent/weaver phải sẵn sàng đúng lúc.
7. **Refactor trước weaving** nếu nhu cầu chỉ là gọi giữa hai method Spring beans; tránh tăng độ phức tạp không cần thiết.
8. **Kiểm thử bằng Spring context thật** với positive, negative, self-invocation, kiểu proxy, số lần advice, return/exception và tác dụng phụ.
9. **Đừng bật nhiều hạ tầng AOP cùng mục tiêu** mà không kiểm tra registration và advice duplication.
10. **Đúng phiên bản, đúng môi trường:** Spring Framework/Boot, Java, packaging, native image và application server ClassLoader ảnh hưởng cấu hình thực tế.

**Kết luận:** Lựa chọn không bắt đầu từ “JDK, CGLIB hay AspectJ cái nào mạnh hơn”, mà bắt đầu từ **join point cần can thiệp**, **đường gọi qua Spring bean proxy**, **khả năng thay đổi thiết kế** và **môi trường triển khai**. Spring proxy giải quyết phần lớn AOP method-boundary trong ứng dụng; weaving dành cho các ràng buộc vượt ra ngoài proxy.

## Tài liệu tham khảo

- Spring Framework — [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- Spring Framework — [AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/introduction-proxies.html)
- Spring Framework — [Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/enable.html)
- Spring Framework — [Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- Spring Framework — [Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)
- Spring Framework — [Spring AOP APIs](https://docs.spring.io/spring-framework/reference/core/aop-api.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-review-003">Bảng pointcut được Spring hỗ trợ và trường hợp sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng pointcut được Spring hỗ trợ và trường hợp sử dụng

## 1. Phạm vi tra cứu

Spring AOP sử dụng cú pháp biểu thức pointcut của AspectJ nhưng **chỉ áp dụng cho method execution trên Spring bean thông qua AOP proxy**. Vì vậy, cùng một biểu thức có thể hợp lệ trong ngôn ngữ AspectJ nhưng **không được Spring AOP proxy hỗ trợ**. Bài này tập trung cách chọn pointcut trong Spring Framework (annotation `@Aspect` và API `Pointcut`/`Advisor` của Spring), không ôn lại lý thuyết AspectJ nói chung.

Bốn bước quyết định: (1) chọn method hoặc layer; (2) chọn bean/type/annotation/arguments khi thực sự cần; (3) xác minh caller đi qua proxy; (4) kiểm tra integration test trên Spring context thật. `@EnableAspectJAutoProxy` tạo **Spring proxy**, không kích hoạt bytecode weaving.

## 2. Bảng 10 pointcut designator Spring AOP hỗ trợ

Spring Framework hỗ trợ **9 designator AspectJ thuộc tập con** và **1 designator `bean` do Spring bổ sung**.

| Designator | Khớp điều gì trong Spring AOP? | Ví dụ | Tình huống thích hợp |
| --- | --- | --- | --- |
| `execution(...)` | Chữ ký **method được thực thi** | `execution(* demo.OrderService.submit(..))` | Mặc định chọn method theo tên, return type, tham số, package |
| `within(...)` | Method execution bên trong type/package khớp mẫu | `within(demo.service..*)` | Giới hạn một service layer/package |
| `this(...)` | **Spring proxy** là instance của type | `this(demo.OrderService)` | Chọn theo interface mà proxy công bố |
| `target(...)` | **Target object** phía sau proxy là instance của type | `target(demo.DefaultOrderService)` | Chọn implementation hoặc target interface |
| `args(...)` | Type của **argument runtime** được truyền tới invocation | `args(java.io.Serializable)` | Điều kiện phụ thuộc object truyền vào, không chỉ signature |
| `@target(...)` | Runtime target class mang annotation | `@target(demo.MonitoredType)` | Chọn đối tượng thuộc class có marker |
| `@args(...)` | Runtime class của argument mang annotation | `@args(demo.ClassifiedPayload)` | Chính sách phụ thuộc loại payload được truyền |
| `@within(...)` | Method execution nằm trong type có annotation | `@within(demo.MonitoredType)` | Chọn các method của annotated type |
| `@annotation(...)` | Method được thực thi mang annotation | `@annotation(demo.MonitoredMethod)` | Chọn method đánh dấu audit/metrics có chủ đích |
| `bean(...)` | **Tên Spring bean** hoặc wildcard tên bean | `bean(orderService)`; `bean(*Service)` | Nhiều bean cùng class, cần chỉ chọn bean được đặt tên |

**`bean(...)` chỉ dành cho Spring AOP**, không phải PCD AspectJ weaving thuần. Nó làm việc với Spring bean name (có hỗ trợ `*`), **không** regex tổng quát và không phải class name. `bean(orderService)` chỉ có ý nghĩa khi bean tương ứng đã được đăng ký trong Spring context.

Các loại `@annotation`, `@target`, `@within`, `@args` khác nhau về vị trí metadata. Đặc biệt, `@args` **không** tìm annotation ở khai báo tham số của method; nó kiểm tra annotation trên **runtime type của object argument**.

## 3. `execution(...)`: cú pháp thiết yếu

Mẫu chung:

```text
execution(modifiers-pattern?
          return-type-pattern
          declaring-type-pattern?method-name-pattern(parameter-pattern)
          throws-pattern?)
```

Bảng thực hành (ví dụ package giả định là `demo`):

| Biểu thức | Ý nghĩa |
| --- | --- |
| `execution(public * *(..))` | Mọi public method Spring có thể intercept |
| `execution(* demo.OrderService.submit(..))` | `submit` của contract `OrderService`, mọi danh sách tham số |
| `execution(java.lang.String demo.OrderService.submit(..))` | `submit` trả `String` |
| `execution(* demo.OrderService.submit(java.lang.String))` | `submit` có đúng một tham số khai báo `String` |
| `execution(* demo.service.*.*(..))` | Các method của type trong package `demo.service` trực tiếp |
| `execution(* demo.service..*.*(..))` | Các method của type trong `demo.service` và package con |
| `execution(* demo..*Service.*(..))` | Các method của type có tên phù hợp `*Service` trong phạm vi `demo` |
| `execution(* demo.OrderService.save*(..))` | Method có tên bắt đầu `save` |
| `execution(* demo.OrderService.ping())` | Method `ping` không có tham số |
| `execution(* demo.OrderService.find(*))` | Method `find` nhận đúng một tham số bất kỳ |
| `execution(* demo.OrderService.send(java.lang.String,..))` | `send` có `String` đầu tiên, theo sau 0 hoặc nhiều tham số |

Dấu `*` ở return type nghĩa là mọi return type, ở method name là wildcard tên. Dấu `..` trong **package** là nhiều cấp package; trong danh sách **tham số** là 0 hoặc nhiều tham số. `(..)` khác `(*)`: vế đầu cho phép 0 đến nhiều arguments, vế sau yêu cầu **đúng một** argument. Khi method overload, `submit(..)` chọn nhiều overload; muốn chính xác hãy khai báo parameter pattern.

### Những sai lầm khi dùng `execution`

- `execution(* demo.OrderService.*(..))` có thể intercept cả những method ít quan trọng có tần suất cao: tránh ghi log payload hoặc làm I/O nặng trong mọi advice.
- `execution(public * *(..))` có thể quá rộng. Kết hợp type/package/annotation/bean để giới hạn.
- `execution(* demo.OrderService.publish(java.io.Serializable))` chọn theo **kiểu khai báo của tham số**; khác `args(java.io.Serializable)` chọn theo **kiểu object truyền vào lúc chạy**.
- `execution` không intercept method **call-site** ở nơi gọi như `call(...)` của AspectJ weaving. Spring chỉ quan sát method executions đi qua proxy.

## 4. Các khác biệt thường nhầm lẫn

### 4.1. `this` khác `target` trong Spring

Trong Spring AOP:

```text
this(demo.OrderService)          -> đối tượng proxy mà caller thấy
target(demo.DefaultOrderService) -> đối tượng target bên dưới proxy
```

Nếu Spring dùng **JDK dynamic proxy**, proxy implements business interface `OrderService` nhưng **không extends** implementation `DefaultOrderService`. Do đó `this(DefaultOrderService)` có thể không khớp, trong khi `target(DefaultOrderService)` khớp. Với **class-based proxy (CGLIB)**, proxy thường là subclass implementation, nên `this(DefaultOrderService)` có thể khớp.

Khi pointcut cần type **target** để chọn nghiệp vụ, ưu tiên `target(...)`; chỉ dùng `this(...)` nếu ý định chính là **proxy type**. Đừng thay hai designator cho nhau mà chưa test khi cấu hình JDK/CGLIB khác nhau.

### 4.2. `args` khác tham số của `execution`

Giả sử method khai báo:

```java
public void publish(Object event) { }
```

- `execution(* *.publish(java.io.Serializable))` yêu cầu **signature khai báo** tham số `Serializable`, nên không khớp method `publish(Object)` chỉ vì caller truyền `String`.
- `args(java.io.Serializable)` kiểm tra **runtime argument**. Lời gọi `publish("text")` có thể khớp, `publish(new Object())` có thể không.
- `args` có thể làm việc kiểm tra pointcut xảy ra **mỗi invocation**, thay vì chỉ đánh giá static method matcher. Không chọn dynamic PCD chỉ để làm việc mà `execution` đã đủ.

### 4.3. `@annotation` khác `@within`/`@target`/`@args`

| Điều kiện | Nơi đặt/kiểm tra annotation | Gợi ý sử dụng |
| --- | --- | --- |
| `@annotation(demo.MonitoredMethod)` | Trên method được thực thi | Chủ động bật quan sát trên từng method |
| `@within(demo.MonitoredType)` | Trên type nơi method được khai báo | Quan sát toàn bộ annotated class/type |
| `@target(demo.MonitoredType)` | Trên runtime class của target instance | Áp dụng chính sách cho đối tượng có annotated class |
| `@args(demo.ClassifiedPayload)` | Trên **runtime class của argument** | Chọn invocation theo loại dữ liệu truyền vào |

Khi có inheritance, interface annotations, overridden methods hoặc bridge methods, kết quả khớp phụ thuộc metadata và method thực tế mà Spring AOP giải quyết. Cần **integration test trên đúng type hierarchy** thay vì giả định `@target` và `@within` luôn tương đương.

Annotation được kiểm tra khi chạy nên được khai báo `@Retention(RetentionPolicy.RUNTIME)` và `@Target` hợp lý (`METHOD` hoặc `TYPE`). Annotation thiếu runtime retention dễ làm pointcut không thấy marker.

### 4.4. `within` khác `bean`

`within(demo.service..*)` lọc **type/package**; `bean(orderService)` lọc **tên bean instance** trong Spring. Hai bean có thể dùng cùng implementation class nhưng khác tên: `within` có thể khớp cả hai, `bean` chỉ chọn bean mong muốn. `bean(*Service)` giúp áp dụng convention naming nhưng nên xác minh tên bean thực tế, kể cả tên được tạo bởi `@Bean`, `@Component`, alias và cấu hình.

## 5. Danh sách designator không hỗ trợ trong Spring proxy AOP

Các PCD AspectJ sau đây **không được trình phân tích biểu thức của Spring AOP hỗ trợ**:

| PCD không được hỗ trợ | Ý nghĩa trong AspectJ / lý do không dùng với Spring proxy |
| --- | --- |
| `call(...)` | Method **call site**; Spring proxy chỉ xử lý method execution |
| `get(...)`, `set(...)` | Field access; proxy không biến đổi bytecode truy cập field |
| `initialization(...)`, `preinitialization(...)`, `staticinitialization(...)` | Khởi tạo constructor/class; không phải method execution qua proxy |
| `handler(...)` | Exception handler join point |
| `adviceexecution(...)` | Join point khi advice chạy |
| `withincode(...)` | Vị trí lexical bên trong code |
| `cflow(...)`, `cflowbelow(...)` | AspectJ call-flow pointcut |
| `if(...)` | AspectJ conditional pointcut expression |
| `@this(...)`, `@withincode(...)` | Annotation PCD ngoài tập được Spring AOP hỗ trợ |

Đưa PCD không hỗ trợ vào expression Spring thường dẫn đến `IllegalArgumentException` ở lúc parse/cấu hình. Cần đánh giá lại yêu cầu: nếu muốn intercept constructor/field/self-invocation, Spring AOP proxy có thể **không phải cơ chế phù hợp**; việc dùng AspectJ weaving là một chế độ khác, không chỉ là đổi một chuỗi pointcut.

**Ngoại lệ dễ nhầm:** Spring có **API** `org.springframework.aop.support.ControlFlowPointcut`, nhưng nó **không làm expression `cflow(...)` trở thành hợp lệ**. Spring API pointcut bằng Java và tập PCD AspectJ được hỗ trợ trong biểu thức là hai giao diện cấu hình khác nhau.

## 6. Ghép pointcut và đặt tên để tái sử dụng

| Toán tử | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `&&` | Cả hai điều kiện | `execution(* demo.OrderService.submit(..)) && bean(orderService)` |
| `||` | Một trong hai | `bean(orderService) || bean(backupOrderService)` |
| `!` | Phủ định | `within(demo.service..*) && !bean(testService)` |
| `(...)` | Nhóm biểu thức | `(bean(orderService) || bean(backupOrderService)) && execution(* *.submit(..))` |

Trong class có `@Aspect`, dùng `@Pointcut` để tạo tên và tái sử dụng:

```java
@Pointcut("execution(public * demo.service..*.*(..))")
public void serviceOperation() {}

@Pointcut("@annotation(demo.MonitoredMethod)")
public void monitoredMethod() {}

@Pointcut("serviceOperation() && monitoredMethod()")
public void monitoredServiceOperation() {}

@Before("monitoredServiceOperation()")
public void beforeMonitored() {
    // ...
}
```

Đây là **fragment bên trong class `@Aspect`**, chưa phải Java file chạy độc lập. Các method `@Pointcut` phải có return type `void` và body rỗng; Spring đọc chúng để định nghĩa biểu thức, không cần caller chạy chúng như business methods. Có thể dùng fully qualified reference như `demo.SharedPointcuts.serviceOperation()` khi pointcut được định nghĩa trong một public class dùng chung.

**Thực hành nên ưu tiên:** bắt đầu từ `execution` hoặc `@annotation`, bổ sung `within`/`bean` khi có lý do và giữ scope hẹp.

## 7. Bảng công thức cho Spring service thực tế

| Mục đích | Biểu thức gợi ý | Điểm cần kiểm tra |
| --- | --- | --- |
| Một operation được theo dõi | `execution(* demo.OrderService.submit(..))` | Overload/signature |
| Tất cả method của business interface | `execution(* demo.OrderService.*(..))` | Mức độ rộng của advice |
| Public method của service package | `execution(public * demo.service..*.*(..))` | Hạ tầng, hot methods, overhead |
| Chỉ một bean cùng class với bean khác | `bean(orderService)` | Tên Spring bean |
| Bean theo naming convention | `bean(*Service)` | Wildcard tên bean |
| Một method **trên một bean** | `bean(orderService) && execution(* demo.OrderService.submit(..))` | Kết hợp name và signature |
| Method có marker | `@annotation(demo.MonitoredMethod)` | Runtime retention của annotation |
| Method marker chỉ trong layer | `within(demo.service..*) && @annotation(demo.MonitoredMethod)` | Nơi class/method nằm |
| Class target có marker | `@target(demo.MonitoredType)` | Runtime class và hierarchy |
| Method trong annotated type | `@within(demo.MonitoredType)` | Declaring type/method override |
| Runtime argument implements interface | `args(java.io.Serializable)` | Tốn chi phí dynamic check khi cần |
| Runtime argument class có marker | `@args(demo.ClassifiedPayload)` | Không phải parameter annotation |
| Proxy triển khai service interface | `this(demo.OrderService)` | JDK/CGLIB proxy type |
| Target thuộc class implementation | `target(demo.DefaultOrderService)` | Target, không phải proxy type |
| Loại trừ bean test | `execution(* demo.service..*.*(..)) && !bean(testService)` | Bean naming |
| Chỉ method trả `String` | `execution(java.lang.String demo.OrderService.*(..))` | Return type khai báo |

Không coi bảng là bảo đảm advice luôn chạy: `@Aspect` phải được Spring đăng ký thành bean, infrastructure phải bật, method phải đi qua proxy, pointcut phải match, và proxy phải intercept được method đó. `this.otherMethod()` trên target thường bypass proxy.

## 8. Ví dụ Maven + Java 17: `execution` kết hợp `bean`

Ví dụ cho thấy hai beans cùng implementation class nhưng aspect chỉ chọn **một bean và một method**. Dùng **Spring Framework 6.2.14**, **AspectJ Weaver 1.9.24**, Java 17+; toàn bộ là **Spring proxy AOP**, không có AspectJ LTW.

### 8.1. Cấu trúc

```text
spring-pointcuts/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── OrderService.java
    │   ├── DefaultOrderService.java
    │   ├── TrackingAspect.java
    │   ├── Config.java
    │   └── Main.java
    └── test/java/demo/
        └── PointcutTest.java
```

### 8.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-pointcuts</artifactId>
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

### 8.3. `src/main/java/demo/OrderService.java`

```java
package demo;

public interface OrderService {
    String submit(String id);
    String status(String id);
    String submitInternally(String id);
}
```

### 8.4. `src/main/java/demo/DefaultOrderService.java`

```java
package demo;

public class DefaultOrderService implements OrderService {
    @Override
    public String submit(String id) {
        System.out.println("TARGET submit " + id);
        return "submitted:" + id;
    }

    @Override
    public String status(String id) {
        System.out.println("TARGET status " + id);
        return "status:" + id;
    }

    @Override
    public String submitInternally(String id) {
        return this.submit(id);
    }
}
```

`submitInternally` minh họa self-invocation: vì lời gọi `this.submit(...)` nằm trong target nên không quay lại Spring proxy.

### 8.5. `src/main/java/demo/TrackingAspect.java`

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TrackingAspect {
    private final AtomicInteger calls = new AtomicInteger();

    @Around(
        "execution(* demo.OrderService.submit(..))" +
        " && bean(orderService)"
    )
    public Object track(ProceedingJoinPoint point) throws Throwable {
        calls.incrementAndGet();
        System.out.println("ADVICE before");
        try {
            return point.proceed();
        } finally {
            System.out.println("ADVICE after");
        }
    }

    public int calls() {
        return calls.get();
    }
}
```

`execution` chọn method `submit` của contract `OrderService`, còn `bean(orderService)` yêu cầu đúng Spring bean name. Điều kiện `&&` giúp cùng implementation ở bean khác không được gắn advice này. `AtomicInteger` cho phép đếm invocation của singleton aspect từ nhiều thread mà không dùng `count++`.

### 8.6. `src/main/java/demo/Config.java`

```java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class Config {
    @Bean("orderService")
    public OrderService orderService() {
        return new DefaultOrderService();
    }

    @Bean("secondaryOrderService")
    public OrderService secondaryOrderService() {
        return new DefaultOrderService();
    }

    @Bean
    public TrackingAspect trackingAspect() {
        return new TrackingAspect();
    }
}
```

Cả hai beans có cùng Java implementation, nhưng `bean(orderService)` chỉ chọn bean thứ nhất. `@EnableAspectJAutoProxy` là cấu hình Spring proxy AOP; với interface service, `proxyTargetClass=false` cho phép JDK proxy nếu không có cấu hình khác ép class-based proxy.

### 8.7. `src/main/java/demo/Main.java`

```java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            OrderService primary =
                    context.getBean("orderService", OrderService.class);
            OrderService secondary =
                    context.getBean("secondaryOrderService", OrderService.class);
            TrackingAspect aspect = context.getBean(TrackingAspect.class);

            System.out.println("PRIMARY_PROXY=" + AopUtils.isAopProxy(primary));
            System.out.println("SECONDARY_PROXY=" + AopUtils.isAopProxy(secondary));
            System.out.println("PRIMARY=" + primary.submit("A"));
            System.out.println("SECONDARY=" + secondary.submit("B"));
            System.out.println("STATUS=" + primary.status("C"));
            System.out.println("INTERNAL=" + primary.submitInternally("D"));
            System.out.println("ADVICE_CALLS=" + aspect.calls());
        }
    }
}
```

Chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.Main
```

**Stdout kỳ vọng** của ứng dụng tối giản:

```text
PRIMARY_PROXY=true
SECONDARY_PROXY=false
ADVICE before
TARGET submit A
ADVICE after
PRIMARY=submitted:A
TARGET submit B
SECONDARY=submitted:B
TARGET status C
STATUS=status:C
TARGET submit D
INTERNAL=submitted:D
ADVICE_CALLS=1
```

Vì `secondaryOrderService` không khớp advisor nào ở demo tối giản, nó thường không được proxy. Nếu có các infrastructure advisors khác trong dự án lớn, `SECONDARY_PROXY` có thể thay đổi; **số lượt gọi advice riêng `TrackingAspect`** vẫn phải tuân thủ pointcut.

`status` không phải `submit`, nên không có advice. `submitInternally` gọi `this.submit` bên trong target, nên không có advice dù `submit` chính là method mà pointcut chọn. Đây là **giới hạn proxy**, không phải lỗi của expression.

## 9. Bảng Spring AOP Pointcut API (không phải biểu thức AspectJ)

Ngoài annotation-style, Spring cung cấp `org.springframework.aop.Pointcut` với `ClassFilter` và `MethodMatcher`, cho phép ghép cùng `DefaultPointcutAdvisor`/`ProxyFactory`.

| API | Chức năng | Khi sử dụng |
| --- | --- | --- |
| `Pointcut` | Abstraction kết hợp `ClassFilter` + `MethodMatcher` | Tự xây/trao đổi pointcut trong AOP API |
| `AspectJExpressionPointcut` | Parse biểu thức AspectJ thuộc tập con Spring hỗ trợ | Khi dùng `execution`, `within`, `@annotation` trong Java config |
| `NameMatchMethodPointcut` | Match method name, wildcard tên đơn giản `*` | Vài method như `save`, `update*`; **không phân biệt overload** |
| `JdkRegexpMethodPointcut` | Regex trên fully qualified method name | Quy tắc naming regex legacy |
| `RegexpMethodPointcutAdvisor` | Advisor ghép regex pointcut và advice | Muốn wiring dễ hơn khi dùng regex |
| `AnnotationMatchingPointcut` | Match annotation ở type hoặc method | Chọn marker trong programmatic configuration |
| `StaticMethodMatcherPointcut` | Tự viết matcher dựa trên signature và target class | Overload hoặc policy static riêng |
| `DynamicMethodMatcherPointcut` | Matcher xét thêm runtime arguments | Điều kiện động thực sự cần; chi phí cao hơn |
| `ControlFlowPointcut` | Kiểm tra call stack hiện tại | Tình huống đặc biệt/legacy, overhead đáng kể |
| `ComposablePointcut` | Union/intersection các pointcuts | Tái dùng pointcut Java |
| `Pointcuts` | Các helper static để tổ hợp pointcuts | Composition nhỏ |
| `Pointcut.TRUE` | Match mọi method của target thích hợp | Advisor thực sự cần áp rộng trong phạm vi proxy |

**Không nhầm** `ControlFlowPointcut` API với PCD `cflow(...)` AspectJ (không được Spring proxy expression parser hỗ trợ).

### Static so với runtime matching

Trong Spring AOP API:

```java
public interface Pointcut {
    ClassFilter getClassFilter();
    MethodMatcher getMethodMatcher();
}
```

Và hình thức interface matcher:

```java
public interface MethodMatcher {
    boolean matches(Method method, Class<?> targetClass);
    boolean isRuntime();
    boolean matches(Method method, Class<?> targetClass, Object... args);
}
```

Đây là **mô tả signature API Spring**, không phải code định nghĩa interface mới trong project. Nếu `isRuntime() == false`, Spring có thể quyết định match bằng method/class và cache kết quả; nếu true, còn cần kiểm tra actual arguments mỗi invocation. Ưu tiên static matching nếu đủ điều kiện, nhất là service hot paths.

## 10. Ví dụ Pointcut API với cùng service

Không cần `@Aspect` để tạo Spring AOP proxy trực tiếp. Thêm một class **không thuộc demo main ở mục 8** khi cần thử:

`src/main/java/demo/ApiMain.java`

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class ApiMain {
    public static void main(String[] args) {
        AtomicInteger advised = new AtomicInteger();

        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("status");

        MethodInterceptor interceptor = invocation -> {
            advised.incrementAndGet();
            System.out.println("API before");
            try {
                return invocation.proceed();
            } finally {
                System.out.println("API after");
            }
        };

        ProxyFactory factory = new ProxyFactory(new DefaultOrderService());
        factory.setInterfaces(OrderService.class);
        factory.addAdvisor(
            new DefaultPointcutAdvisor(pointcut, interceptor)
        );

        OrderService proxy = (OrderService) factory.getProxy();
        System.out.println("PROXY=" + AopUtils.isAopProxy(proxy));
        System.out.println("STATUS=" + proxy.status("Z"));
        System.out.println("SUBMIT=" + proxy.submit("X"));
        System.out.println("ADVICE_CALLS=" + advised.get());
    }
}
```

Chạy:

```bash
mvn -q compile exec:java -Dexec.mainClass=demo.ApiMain
```

Stdout dự kiến:

```text
PROXY=true
API before
TARGET status Z
API after
STATUS=status:Z
TARGET submit X
SUBMIT=submitted:X
ADVICE_CALLS=1
```

`NameMatchMethodPointcut` chỉ chọn method có tên `status`; **không** chọn `submit`. Tạo proxy theo Java API cũng không tự sửa self-invocation. Nếu dùng `factory.addAdvice(interceptor)` mà không có `DefaultPointcutAdvisor` hẹp, advice có thể áp rộng hơn ý định.

**Biểu thức qua Java API:** khi cần cú pháp `execution`, bạn có thể thay `NameMatchMethodPointcut` bằng `AspectJExpressionPointcut` rồi gắn vào `DefaultPointcutAdvisor`:

```java
// Fragment dùng trong ApiMain, với aspectjweaver đã khai báo trong pom.xml.
org.springframework.aop.aspectj.AspectJExpressionPointcut pc =
    new org.springframework.aop.aspectj.AspectJExpressionPointcut();
pc.setExpression("execution(* demo.OrderService.status(..))");
// Gắn pc vào new DefaultPointcutAdvisor(pc, interceptor).
```

Cách này vẫn là Spring **proxy** AOP, không kích hoạt LTW.

## 11. JUnit 5: kiểm tra biểu thức và pointcut API qua proxy

Tạo `src/test/java/demo/PointcutTest.java`:

```java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class PointcutTest {

    @Test
    void beanAndExecutionSelectOnlyPrimarySubmit() {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            OrderService primary =
                    context.getBean("orderService", OrderService.class);
            OrderService secondary =
                    context.getBean("secondaryOrderService", OrderService.class);
            TrackingAspect aspect = context.getBean(TrackingAspect.class);

            assertTrue(AopUtils.isAopProxy(primary));
            assertEquals("submitted:A", primary.submit("A"));
            assertEquals("submitted:B", secondary.submit("B"));
            assertEquals("status:C", primary.status("C"));
            assertEquals("submitted:D", primary.submitInternally("D"));
            assertEquals(1, aspect.calls());
        }
    }

    @Test
    void manualPointcutSelectsOnlyStatus() {
        AtomicInteger adviceCount = new AtomicInteger();
        NameMatchMethodPointcut pc = new NameMatchMethodPointcut();
        pc.setMappedName("status");

        MethodInterceptor interceptor = invocation -> {
            adviceCount.incrementAndGet();
            return invocation.proceed();
        };

        ProxyFactory factory = new ProxyFactory(new DefaultOrderService());
        factory.setInterfaces(OrderService.class);
        factory.addAdvisor(new DefaultPointcutAdvisor(pc, interceptor));

        OrderService proxy = (OrderService) factory.getProxy();

        assertTrue(AopUtils.isAopProxy(proxy));
        assertEquals("status:Y", proxy.status("Y"));
        assertEquals("submitted:X", proxy.submit("X"));
        assertEquals(1, adviceCount.get());
    }
}
```

Chạy:

```bash
mvn test
```

Kết quả dự kiến: **2 tests, 0 failures, 0 errors**. Bài test thứ nhất chứng minh tổ hợp PCD chỉ intercept `submit` ở named bean; test thứ hai chứng minh `NameMatchMethodPointcut` chỉ intercept `status`. Việc assert side effect của advice quan trọng hơn chỉ xem `getAdvisors().length`: danh sách advisor trên proxy **không phải bằng chứng** một advice đã chạy cho từng method.

## 12. Troubleshooting điểm cắt trong Spring

| Triệu chứng | Nguyên nhân cần kiểm tra | Cách xác minh |
| --- | --- | --- |
| `IllegalArgumentException` khi parse PCD | Dùng designator không được Spring hỗ trợ (`call`, `get`, `cflow`, v.v.) | So với bảng supported PCD; không dùng AspectJ full grammar một cách máy móc |
| Aspect có `@Aspect` nhưng không chạy | Aspect chưa là Spring bean hoặc chưa bật auto-proxy trong đúng context | Kiểm tra bean registration và `@EnableAspectJAutoProxy` |
| Advice không chạy ở `this.method()` | Self-invocation bỏ qua Spring AOP proxy | Tách boundary sang một bean khác hoặc đổi thiết kế |
| `bean(orderService)` không khớp | Tên bean/alias thực khác dự kiến | Xem bean registration trong context đang chạy |
| `this(Implementation)` không khớp | JDK dynamic proxy chỉ implements interface | Phân biệt `this` proxy và `target` target |
| `@annotation` không khớp | Marker đặt sai method hoặc thiếu runtime retention | Kiểm tra method của implementation/interface thực tế |
| `@args` không thấy parameter annotation | `@args` xét annotation của **runtime argument class** | Dùng policy khác nếu cần parameter metadata |
| `args` có hành vi khó đoán | Điều kiện dựa vào runtime argument values/types | Viết test gọi qua proxy với nhiều runtime types |
| `execution(* submit(..))` chọn overload khác | `(..)` chọn mọi số lượng/kiểu tham số | Dùng danh sách tham số cụ thể |
| Pointcut quá rộng, log nhiều, giảm hiệu năng | Scope của `execution`/`within` quá rộng | Thu hẹp package, bean, annotation và bỏ I/O nặng |
| Có advisor mà advice không chạy | Pointcut không khớp method cụ thể hoặc caller không qua proxy | Dùng `AopUtils`, `Advised.getAdvisors()`, breakpoint interceptor chain |
| Final/private method không được intercept | Giới hạn của class-based proxy | Kiểm tra proxy type, visibility, `final` và đường gọi |

Trong Spring Boot, cấu hình auto-proxy có thể chọn CGLIB theo default của Boot, khiến `this`/`target` cho kết quả khác dự đoán từ JDK proxy. Luôn kiểm tra proxy runtime của **bean thật**.

## 13. Checklist tra cứu trước khi đưa pointcut vào production

- [ ] Chọn trong 10 supported PCD; `bean` chỉ áp dụng cho Spring proxy AOP.
- [ ] Dùng `execution` rõ ràng, giới hạn class/package/method càng hẹp càng tốt.
- [ ] Phân biệt `args` runtime và parameter pattern của `execution`.
- [ ] Phân biệt `this` là proxy, `target` là target.
- [ ] Dùng đúng marker retention/target; phân biệt `@annotation`, `@within`, `@target`, `@args`.
- [ ] Không dùng `call`, `get`, `set`, `cflow`, `if` như Spring AOP pointcut expression.
- [ ] Nếu cần method được intercept, bảo đảm lời gọi thực sự đi qua **Spring bean proxy**.
- [ ] Viết integration test kiểm tra advice thực thi, không chỉ kiểm tra biểu thức parse thành công.
- [ ] Tránh ghi dữ liệu nhạy cảm hoặc I/O nặng trong advice bao trùm quá rộng.
- [ ] Với API programmatic, ưu tiên static pointcuts khi đủ; giới hạn dynamic pointcut trên hot path.
- [ ] Xác nhận Spring version và điểm khác Spring Boot/framework configuration ở runtime.

**Kết luận:** Bảng pointcut hữu ích nhất khi đi kèm hiểu biết về **Spring proxy boundary**. `execution`, `@annotation` và `bean` thường đã đủ cho các nhu cầu aspect trong Spring; các designator và AOP API khác phục vụ những trường hợp rõ ràng hơn nhưng **không mở rộng Spring proxy AOP thành AspectJ weaving**.

## 14. Tài liệu chính thức

- [Spring Framework 6.2 — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html): 9 AspectJ PCD supported, Spring `bean()`, examples, kết hợp biểu thức.
- [Spring Framework 6.2 — Pointcut API in Spring](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pointcuts.html): `Pointcut`, `ClassFilter`, `MethodMatcher`, regex, static/dynamic và control-flow API.
- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): parse AspectJ expressions và Spring proxy runtime.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, final/private, self-invocation.
- [Spring Framework — Programmatic ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html): `DefaultPointcutAdvisor` và proxy Java API.
- [Spring Framework — Advised API](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html): cách kiểm tra advisors.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-review-004">Bảng loại advice, dữ liệu binding và khả năng thay đổi luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng loại advice, dữ liệu binding và khả năng thay đổi luồng trong Spring AOP

## 1. Cách đọc bảng

Trong Spring Framework, một lớp được khai báo `@Aspect` có thể cung cấp các advice thông qua `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After` và `@Around`. Khi bật hỗ trợ `@AspectJ` của **Spring AOP**, framework đọc các annotation, tạo advisors/interceptors và thực thi chúng khi lời gọi method đi qua Spring proxy phù hợp.

Ba câu hỏi cần tách biệt:

- **Khi nào advice chạy?** Trước method, sau khi method trả về, khi method ném lỗi, trong finally, hay bao quanh toàn bộ lời gọi.
- **Advice nhận được dữ liệu gì?** `JoinPoint`, `ProceedingJoinPoint`, method arguments, annotation, target/proxy, return value, exception — phụ thuộc pointcut và thuộc tính binding của annotation.
- **Advice được phép thay đổi gì?** Có thể chặn bằng cách ném exception, có thể quyết định không gọi target, thay return value, bắt rồi đổi exception, hoặc chỉ quan sát và thực thi side effects.

Không phải mọi advice được phép gọi `proceed()`, cũng không phải mọi advice được bind `returning` hay `throwing`. Điểm quan trọng nhất của bảng dưới đây là **sự khác nhau giữa khả năng *quan sát* luồng và khả năng *điều khiển* luồng**.

## 2. Bảng tra cứu nhanh năm loại advice

| Advice trong Spring `@Aspect` | Thời điểm chạy | Binding đặc trưng | Đọc kết quả/exception | Khả năng đổi luồng |
| --- | --- | --- | --- | --- |
| `@Before` | Trước khi method/join point được gọi bởi interceptor kế tiếp | `JoinPoint`, `args(...)`, `@annotation(...)`, `this`, `target`… | Không có return/exception của invocation sắp chạy | **Không thay được return bằng giá trị tự trả**; có thể ném exception để ngăn lời gọi tiếp |
| `@AfterReturning` | Chỉ khi lời gọi được advice quan sát **trả về bình thường** | Các binding từ pointcut; `returning="name"` cho giá trị trả về | Có thể bind return value với kiểu tương thích | Không thể thay return value của caller bằng giá trị trả về từ method advice; có thể gây lỗi nếu advice ném exception hoặc thực hiện side effect |
| `@AfterThrowing` | Chỉ khi lời gọi được advice quan sát **ném Throwable phù hợp** | Các binding từ pointcut; `throwing="name"` cho exception | Có thể bind exception với kiểu tương thích | Không thể dùng return của advice để phục hồi thành công; advice có thể ném lỗi khác, làm thay exception được caller thấy |
| `@After` | Khi lời gọi được advice bao quanh kết thúc, theo semantics `finally` | `JoinPoint` và binding pointcut phù hợp | Không có thuộc tính `returning`/`throwing` trên `@After` | Chạy ở cả success/failure; nếu ném exception có thể che kết quả/lỗi cũ; không chủ động đổi return value bằng `return` |
| `@Around` | Bao quanh việc gọi interceptor/target tiếp theo | `ProceedingJoinPoint`, args/annotation binding; có thể đọc result/exception từ `proceed()` | Có khi gọi `proceed()` và xử lý kết quả hoặc exception | **Mạnh nhất:** có thể không gọi `proceed()`, gọi với args khác, trả giá trị thay thế, xử lý/đổi exception, bao quanh thời gian chạy |

**Chú thích quan trọng:** Spring AOP là proxy-based method-execution AOP. Một `@Before` ném exception là *chặn invocation* bằng lỗi, chứ không phải trả về một kết quả tùy chọn thành công. `@AfterReturning` có thể sửa **trạng thái của một object kết quả mutable** nếu reference được bind, nhưng việc method advice tự `return` một object khác không thay return value đã định cho caller. Nếu cần quyền kiểm soát return/exception/short-circuit tường minh, chọn `@Around`.

### 2.1. Gợi ý chọn advice

| Nhu cầu trong Spring service | Chọn thường hợp lý | Vì sao |
| --- | --- | --- |
| Ghi lại lời gọi trước khi xử lý | `@Before` | Ít quyền điều khiển, đơn giản hơn `@Around` |
| Audit giá trị trả về thành công | `@AfterReturning` | Có binding `returning`, không chạy trên exception matching |
| Phân loại lỗi theo Throwable | `@AfterThrowing` | Có binding `throwing`, chỉ xử lý nhánh lỗi phù hợp |
| Dọn tài nguyên/phát tín hiệu khi kết thúc | `@After` | Chạy tương tự finally, gồm cả thành công và thất bại |
| Timer có event vào/ra, fallback, retry, short-circuit | `@Around` | Có `proceed()`, kiểm soát toàn bộ execution path |

Không chọn `@Around` chỉ vì “mạnh nhất”: một `@Around` quên `proceed()` hoặc nuốt exception sẽ thay đổi nghiệp vụ mà code có vẻ vẫn biên dịch bình thường. Chọn advice **ít quyền nhất** đủ đáp ứng yêu cầu sẽ dễ review và kiểm thử hơn.

## 3. Dữ liệu binding: bảng theo nguồn dữ liệu

Binding trong `@AspectJ` style của Spring cho phép ánh xạ giá trị từ join point vào tham số method advice.

| Nguồn | Cú pháp/trường hợp sử dụng | Tham số advice phù hợp | Điều cần ghi nhớ |
| --- | --- | --- | --- |
| Join point hiện tại | Tham số đầu `JoinPoint jp` trong before/after | `JoinPoint` | Có signature, target, proxy, args; không có `proceed()` |
| Bao quanh join point | Tham số đầu `ProceedingJoinPoint pjp` trong around | `ProceedingJoinPoint` | Dùng `proceed()` và có thể nhận return/exception |
| Static metadata | `JoinPoint.StaticPart` | `JoinPoint.StaticPart` | Không chứa toàn bộ dữ liệu invocation; hữu ích cho signature/source metadata |
| Arguments | `args(orderId,..)` | `String orderId`, v.v. | Ánh xạ theo biến trong pointcut; kiểm tra số lượng, vị trí và kiểu |
| Annotation method | `@annotation(marker)` | `Tracked marker` | Type là annotation; cần xem annotation nằm trên method Spring nhìn thấy |
| Proxy/target | `this(proxy)`, `target(targetBean)` | Type phù hợp | Với JDK proxy, `this` và `target` có thể khác class/interface |
| Annotation target/type/args | `@target`, `@within`, `@args` | Annotation type phù hợp | Không nhầm `@args` (annotation trên runtime argument types) với `args` (giá trị arguments) |
| Giá trị return | `@AfterReturning(returning="result")` | `String result`, `Object result`, kiểu tương thích | Chỉ trên normal return; binding có thể giới hạn join points theo kiểu return |
| Exception | `@AfterThrowing(throwing="failure")` | `IllegalStateException failure`, `Throwable failure` | Advice chạy khi exception thực tế khớp kiểu binding |
| Method metadata | `((MethodSignature) jp.getSignature()).getMethod()` | `Method` sau cast | Với proxy interface, có thể cần resolve implementation method |
| Argument array | `jp.getArgs()` | `Object[]` | Dữ liệu invocation runtime; không cần `args(...)` khi dùng kiểu tổng quát |
| Value returned by around | `Object value = pjp.proceed()` | Local variable | Khác với `returning` trên `@AfterReturning` |

### 3.1. `argNames` và tên tham số

Ví dụ:

```java
@Before(
    value = "bean(paymentService) && execution(* pay(..)) && args(code)",
    argNames = "code"
)
public void beforePayment(String anyJavaParameterName) {
    // anyJavaParameterName nhận giá trị binding "code"
}
```

`argNames` mô tả **tên biến trong pointcut theo thứ tự tham số advice**; tên tham số Java `anyJavaParameterName` không buộc phải trùng. Nếu không dùng `argNames`, Spring có thể khai thác metadata tên tham số được giữ khi biên dịch `javac -parameters`, hoặc suy luận khi trường hợp không mơ hồ.

Khi có `JoinPoint`/`ProceedingJoinPoint` ở tham số đầu, tên của tham số đặc biệt đó thường được **bỏ qua trong `argNames`**.

```java
@Around(
    value = "bean(paymentService) && execution(* pay(..)) && args(code)",
    argNames = "code"
)
public Object around(
        ProceedingJoinPoint pjp,
        String anyJavaParameterName) throws Throwable {
    return pjp.proceed();
}
```

Đây là **các fragment Java trong Spring `@Aspect`**, không phải class có thể chạy độc lập. `argNames` chỉ giải quyết ánh xạ tên biến, **không sửa pointcut sai** và không biến một method không match thành matched join point.

### 3.2. Binding giá trị trả về

```java
@AfterReturning(
    pointcut = "execution(* com.example.app.PaymentService.pay(..))"
             + " && args(code)",
    returning = "result",
    argNames = "code,result"
)
public void afterPaid(String codeValue, String returnedValue) {
    // Quan sát returnedValue, không thay được response bằng return của advice.
}
```

`returning="result"` phải tương ứng tham số nhận giá trị. Nếu `returnedValue` là `String`, một invocation trả kiểu không tương thích có thể không kích hoạt binding như kỳ vọng. Nếu method trả `void`, cần xem cẩn thận dạng binding thay vì giả định luôn có đối tượng trả về hữu ích. Không đặt `returning` trên `@Before` hoặc `@After`.

### 3.3. Binding exception

```java
@AfterThrowing(
    pointcut = "execution(* com.example.app.PaymentService.pay(..))"
             + " && args(code)",
    throwing = "problem",
    argNames = "code,problem"
)
public void onProblem(String codeValue, IllegalStateException error) {
    // Chỉ chạy khi lời gọi throws kiểu exception tương thích.
}
```

Nếu target ném `IllegalArgumentException` thay vì `IllegalStateException`, advice trên sẽ không được gọi cho exception đó. Với `Throwable`, binding có phạm vi rộng hơn, nhưng nên giữ đúng mục đích logging/exception policy.

### 3.4. Advice annotation đối với interface và implementation

Với JDK dynamic proxy, `MethodSignature.getMethod()` có thể trỏ tới method được khai báo trên interface. Nếu annotation nằm trên implementation, việc gọi Java reflection `getAnnotation()` trên method interface có thể trả `null`, dù implementation có annotation.

Khi phải tự trích xuất metadata trong custom aspect, có thể dùng:

```java
Method exposed = ((MethodSignature) pjp.getSignature()).getMethod();
Method targetMethod = AopUtils.getMostSpecificMethod(
    exposed, pjp.getTarget().getClass());
Tracked marker = AnnotatedElementUtils.findMergedAnnotation(
    targetMethod, Tracked.class);
```

Đây là **fragment** cần các import `org.aspectj.lang.reflect.MethodSignature`, `java.lang.reflect.Method`, `org.springframework.aop.support.AopUtils` và `org.springframework.core.annotation.AnnotatedElementUtils`. Nếu policy chủ động sử dụng annotations ở interface, hãy test fallback/hierarchy resolution phù hợp. Không mặc định tất cả API annotation lookup có cùng semantics.

## 4. Bảng khả năng thay đổi luồng: những điều được và không được

| Hành vi | `@Before` | `@AfterReturning` | `@AfterThrowing` | `@After` | `@Around` |
| --- | --- | --- | --- | --- | --- |
| Đọc arguments hiện tại | Có, khi bind/getArgs | Có | Có | Có | Có |
| Chạy trước target | Có | Không | Không | Không | Có |
| Quan sát normal return | Không | Có | Không | Không qua `returning` | Có, nếu `proceed()` trả về |
| Quan sát thrown exception | Không phải exception của target tương lai | Không | Có | Không qua `throwing` | Có, nếu `proceed()` ném |
| Chạy trên nhánh success lẫn failure | Chạy trước cả hai nếu intercept được gọi | Chỉ success | Chỉ error tương thích | Có | Tùy code bên trong |
| Tự quyết định bỏ qua target | Không bằng return của advice | Không | Không | Không | **Có** (không gọi `proceed()`) |
| Tự thay giá trị trả về cho caller | Không | **Không** bằng return của advice | Không | Không | **Có** |
| Catch và phục hồi exception thành kết quả thành công | Không | Không | Không bằng return của advice | Không | **Có** |
| Có thể ném exception, ảnh hưởng lời gọi | Có | Có | Có | Có | Có |
| Có quyền gọi `proceed()` | Không | Không | Không | Không | **Có** |
| Có thể gọi nhiều lần/retry tiếp | Không | Không | Không | Không | **Có**, cần cân nhắc side effects và interceptor chain |
| Tự đo chính xác thời gian quanh target | Không, cần phối hợp thêm | Không đơn lẻ | Không đơn lẻ | Không đơn lẻ | **Có** |

**Hai chỗ dễ hiểu sai:**

1. `@AfterReturning` là advice kiểu `void` trên một nhánh hoàn tất thành công. Kể cả Java cho phép method advice có return type khác và logic nội bộ tùy ý, **Spring không dùng giá trị trả về của advice để thay return value của method được advice**. Thay đổi field của object mutable thì khác, vì caller vẫn nhận cùng reference với state mới.
2. `@AfterThrowing` không phải cơ chế catch-and-recover giống `try/catch` của caller. Nếu `@AfterThrowing` kết thúc bình thường, exception gốc vẫn được truyền tiếp. Nếu advice lại ném exception mới, exception mà caller thấy có thể bị thay. Muốn phục hồi bằng fallback thành công hãy thiết kế `@Around` có chủ đích.

## 5. Trật tự chạy khi có nhiều advice

### 5.1. Bao bọc của `@Around`

Với hai Spring aspects khác nhau:

```java
@Aspect
@Order(10)
class OuterAspect {
    @Around("execution(* com.example.app.PaymentService.pay(..))")
    public Object outer(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}

@Aspect
@Order(20)
class InnerAspect {
    @Around("execution(* com.example.app.PaymentService.pay(..))")
    public Object inner(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}
```

Trên lời gọi phù hợp, `@Order(10)` có precedence cao hơn `@Order(20)`. Nếu mỗi around advice ghi `enter` trước `proceed()` và `exit` trong `finally`, quan hệ lồng nhau là:

```text
outer.enter
  inner.enter
    target
  inner.exit
outer.exit
```

Đây là **code fragment mô tả thứ tự**, chưa đầy đủ `import`, class public và cấu hình `@Bean`. Thứ tự giữa **các aspect khác nhau** có thể điều khiển bằng `@Order`/Ordered. Không nên giả định thứ tự between aspects có cùng precedence đã được cố định.

### 5.2. Các advice khác nhau trên cùng method

Một lời gọi có `@Before`, `@AfterReturning`, `@AfterThrowing`, `@After`, `@Around` có thể tạo chuỗi events khác nhau theo:

- Precedence giữa các advisors/aspects.
- Loại advice và điều kiện match.
- Kết quả thực tế của target và interceptor nằm phía trong.
- `@Around` có gọi `proceed()`, xử lý exception hoặc thay response hay không.

`@After` có semantics tương đương finally của lời gọi được advice quan sát. Trong các phiên bản Spring hiện đại, khi cùng dùng `@After` với `@AfterReturning`/`@AfterThrowing`, cần chú ý quan hệ thực thi kiểu finally so với advice hoàn tất tương ứng. **Không suy toàn bộ event order chỉ từ thứ tự method khai báo trong source**, đặc biệt khi nhiều advice cùng nằm trong một aspect.

Nếu nghiệp vụ phụ thuộc thứ tự, tách các aspect mang vai trò rõ ràng và dùng `@Order` phân biệt; sau đó viết integration test gọi Spring proxy thật và so sánh trace.

### 5.3. Chú ý với transaction, cache và async của Spring

Một custom `@Aspect` có thể nằm cùng advisor chain với `TransactionInterceptor`, cache interceptor hoặc async interceptor:

- **`@Transactional`:** custom `@AfterReturning` không mặc nhiên có nghĩa transaction **đã commit**. Nếu custom advisor nằm trong transaction advisor, nó có thể quan sát success trước commit. Muốn event sau commit phải dùng transaction-bound mechanism và test rollback.
- **`@Cacheable`:** cache hit có thể bỏ qua method target. Custom advice nằm ngoài cache interceptor vẫn có thể chạy, trong khi advice nằm trong có thể bị bỏ qua trên cache hit.
- **`@Async`:** target chạy trên worker executor; việc `@Before` custom advice chạy phía caller hay worker phụ thuộc vị trí trong chain. Dùng future/correlation ID để kiểm thử, không suy từ thread log đơn lẻ.

Không suy rằng mọi `@Order` custom aspect có precedence cao hơn advisor hạ tầng. Hãy kiểm tra `@EnableTransactionManagement(order=...)`, `@EnableCaching(order=...)`, hạ tầng async và các cấu hình Spring của ứng dụng cụ thể.

## 6. Ví dụ JUnit 5 chạy được: năm advice trong Spring AOP

Fixture dưới đây tập trung vào **binding và quyền thay đổi luồng**. Nó khai báo năm advice trong năm Spring aspect beans với thứ tự rõ ràng, gọi qua một service proxy thật và kiểm thử ba nhánh: success, exception, short-circuit.

- `AroundAspect` có precedence cao nhất (`@Order(10)`), bọc toàn bộ lời gọi. Nếu code là `"BLOCK"`, nó tự trả `"blocked:BLOCK"` **không gọi `proceed()`**.
- `BeforeAspect` (`@Order(20)`) bind cả `String code` và `@Tagged` annotation.
- `ReturningAspect` (`@Order(30)`) bind `returning="result"`.
- `ThrowingAspect` (`@Order(40)`) bind `throwing="failure"`.
- `FinallyAspect` (`@Order(50)`) chạy khi invocation phía trong hoàn tất; ghi `after.finally`.

Các aspect chỉ ghi sự kiện, ngoại trừ around advice có nhánh chặn chủ động. Kỳ vọng được xác định trước khi chạy test.

### 6.1. Maven + Java 17

Cấu trúc project:

```text
spring-advice-cheatsheet/
├── pom.xml
└── src/test/java/lab/AdviceMatrixTest.java
```

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>lab</groupId>
    <artifactId>spring-advice-cheatsheet</artifactId>
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

`aspectjweaver` ở đây cung cấp phần hỗ trợ pointcut/`@AspectJ` cho Spring AOP. Không bật AspectJ LTW hoặc compile-time weaving; `@EnableAspectJAutoProxy` mới là cấu hình tạo Spring AOP proxy trong fixture.

### 6.2. File `src/test/java/lab/AdviceMatrixTest.java`

```java
package lab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

import static org.junit.jupiter.api.Assertions.*;

public class AdviceMatrixTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Tagged {
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

    public static class PaymentService {
        private final Trace trace;

        public PaymentService(Trace trace) {
            this.trace = trace;
        }

        @Tagged("PAYMENT")
        public String submit(String code) {
            trace.add("target:" + code);

            if ("ERR".equals(code)) {
                throw new IllegalStateException("invalid:" + code);
            }
            return "paid:" + code;
        }

        public String ping() {
            trace.add("target.ping");
            return "pong";
        }
    }

    @Aspect
    @Order(10)
    public static class AroundAspect {
        private final Trace trace;

        public AroundAspect(Trace trace) {
            this.trace = trace;
        }

        @Around("bean(paymentService) && execution(* submit(..))")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            String code = (String) pjp.getArgs()[0];
            trace.add("around.enter:" + code);

            try {
                if ("BLOCK".equals(code)) {
                    trace.add("around.shortCircuit:" + code);
                    return "blocked:" + code;
                }

                Object result = pjp.proceed();
                trace.add("around.return:" + code);
                return result;
            } catch (Throwable failure) {
                trace.add("around.throw:" + code);
                throw failure;
            } finally {
                trace.add("around.finally:" + code);
            }
        }
    }

    @Aspect
    @Order(20)
    public static class BeforeAspect {
        private final Trace trace;

        public BeforeAspect(Trace trace) {
            this.trace = trace;
        }

        @Before(
            value = "bean(paymentService) && execution(* submit(..))"
                  + " && args(code) && @annotation(marker)",
            argNames = "code,marker"
        )
        public void before(String incoming, Tagged tagged) {
            trace.add("before:" + incoming + ":" + tagged.value());
        }
    }

    @Aspect
    @Order(30)
    public static class ReturningAspect {
        private final Trace trace;

        public ReturningAspect(Trace trace) {
            this.trace = trace;
        }

        @AfterReturning(
            pointcut = "bean(paymentService) && execution(* submit(..))"
                     + " && args(code)",
            returning = "result",
            argNames = "code,result"
        )
        public void afterSuccess(String incoming, String value) {
            trace.add("after.return:" + incoming + ":" + value);
        }
    }

    @Aspect
    @Order(40)
    public static class ThrowingAspect {
        private final Trace trace;

        public ThrowingAspect(Trace trace) {
            this.trace = trace;
        }

        @AfterThrowing(
            pointcut = "bean(paymentService) && execution(* submit(..))"
                     + " && args(code)",
            throwing = "failure",
            argNames = "code,failure"
        )
        public void afterFailure(
                String incoming, IllegalStateException failure) {
            trace.add("after.throw:" + incoming
                + ":" + failure.getMessage());
        }
    }

    @Aspect
    @Order(50)
    public static class FinallyAspect {
        private final Trace trace;

        public FinallyAspect(Trace trace) {
            this.trace = trace;
        }

        @After("bean(paymentService) && execution(* submit(..))")
        public void cleanup() {
            trace.add("after.finally");
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
        public PaymentService paymentService(Trace trace) {
            return new PaymentService(trace);
        }

        @Bean
        public AroundAspect aroundAspect(Trace trace) {
            return new AroundAspect(trace);
        }

        @Bean
        public BeforeAspect beforeAspect(Trace trace) {
            return new BeforeAspect(trace);
        }

        @Bean
        public ReturningAspect returningAspect(Trace trace) {
            return new ReturningAspect(trace);
        }

        @Bean
        public ThrowingAspect throwingAspect(Trace trace) {
            return new ThrowingAspect(trace);
        }

        @Bean
        public FinallyAspect finallyAspect(Trace trace) {
            return new FinallyAspect(trace);
        }
    }

    @Test
    void normal_return_runs_returning_but_not_throwing() {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            Trace trace = context.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("paid:A7", service.submit("A7"));

            assertEquals(List.of(
                "around.enter:A7",
                "before:A7:PAYMENT",
                "target:A7",
                "after.finally",
                "after.return:A7:paid:A7",
                "around.return:A7",
                "around.finally:A7"
            ), trace.snapshot());
        }
    }

    @Test
    void thrown_exception_runs_throwing_but_not_returning() {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            Trace trace = context.getBean(Trace.class);

            IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.submit("ERR")
            );
            assertEquals("invalid:ERR", failure.getMessage());

            assertEquals(List.of(
                "around.enter:ERR",
                "before:ERR:PAYMENT",
                "target:ERR",
                "after.finally",
                "after.throw:ERR:invalid:ERR",
                "around.throw:ERR",
                "around.finally:ERR"
            ), trace.snapshot());
        }
    }

    @Test
    void around_can_short_circuit_before_other_advice() {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            Trace trace = context.getBean(Trace.class);

            assertEquals("blocked:BLOCK", service.submit("BLOCK"));
            assertEquals(List.of(
                "around.enter:BLOCK",
                "around.shortCircuit:BLOCK",
                "around.finally:BLOCK"
            ), trace.snapshot());
        }
    }

    @Test
    void other_method_is_not_advised() {
        try (var context =
                 new AnnotationConfigApplicationContext(Config.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            Trace trace = context.getBean(Trace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("pong", service.ping());
            assertEquals(List.of("target.ping"), trace.snapshot());
        }
    }
}
```

### 6.3. Chạy fixture

Trong thư mục chứa `pom.xml`:

```bash
mvn test
```

Bốn test được thiết kế để `PASS` khi Spring context/proxy được tạo đúng. Các `List.of(...)` trong JUnit là **vết kỳ vọng**, không phải toàn bộ stdout Maven. Ví dụ đảm bảo:

- Với `"A7"`: `@Before` bind đúng code/annotation; target trả kết quả; `@After` chạy; `@AfterReturning` nhận đúng kết quả; `@AfterThrowing` không chạy; around nhận return.
- Với `"ERR"`: `@Before` chạy; target ném `IllegalStateException`; `@After` vẫn chạy; `@AfterThrowing` bind lỗi; `@AfterReturning` không chạy; around rethrow exception gốc.
- Với `"BLOCK"`: around có precedence cao nhất và không gọi `proceed()`. Không có `target`, `@Before`, `@After`, `@AfterReturning` hay `@AfterThrowing` từ các advisor phía trong.
- Với `ping()`: pointcut chỉ chọn `submit(..)`, nên không custom advice nào kích hoạt dù `PaymentService` vẫn là Spring bean proxy.

Việc cố định `@Order` khác nhau là **cơ sở** cho trace nested trong fixture. Nếu thay đổi thứ tự advisors, expected list phải được xem xét lại như một phần của thay đổi hành vi; không chỉ sửa test cho PASS mà không kiểm tra chính sách.

## 7. Bảng ca biên: điều gì có thể khiến lời gọi thay đổi?

| Tình huống | Advice quan sát gì? | Kết quả thực tế có thể xảy ra |
| --- | --- | --- |
| `@Before` ném exception | Chưa vào target | Target không chạy; caller nhận exception hoặc bị `@Around` ngoài xử lý |
| `@Around` return sớm | Không gọi `proceed()` | Advisors phía trong và target không chạy |
| `@Around` gọi `proceed()` hai lần | Hai lần thực thi phần phía trong | Target và side effects có thể lặp; transaction/cache/retry tương tác phức tạp |
| Target trả `null` | Return branch | `@AfterReturning` có thể chạy nhưng type binding/logic phải hỗ trợ `null` |
| Target ném exception khác kiểu `throwing` binding | Error branch | `@AfterThrowing` có thể không match; exception vẫn đi ra |
| `@AfterReturning` tự ném exception | Đã có normal return từ phần bên trong | Caller có thể thấy exception thay vì return ban đầu |
| `@After` tự ném exception | Hoàn tất invocation bên trong | Có thể che exception hoặc kết quả cũ, tương tự `finally` ném lỗi |
| `@Around` catch exception rồi return fallback | Error branch phía trong | Caller có thể nhận success/fallback thay cho lỗi |
| `@Around` đổi args trước `proceed(args)` | Giá trị args tùy lúc advice can thiệp | Target nhận đầu vào đã thay; method signatures/types vẫn phải hợp lệ |
| Có nhiều custom aspects cùng precedence | Phụ thuộc chain được tạo | Không nên coi một thứ tự quan sát ngẫu nhiên là invariant |
| Annotation chỉ trên implementation, proxy expose interface | Lookup pointcut/metadata tùy cơ chế | Cần test chính xác trên Spring proxy được tạo |
| Spring self-invocation `this.submit()` | Lời gọi target nội bộ | Advice proxy cho method được gọi nội bộ có thể bị bỏ qua |
| Cached method trả từ `@Cacheable` advisor | Cache hit | Target có thể không chạy, tùy custom advice đứng ngoài hay trong cache interceptor |
| Async interceptor chuyển thread | Có nhiều mốc thời gian | Advice có thể chạy khác thread hoặc thời điểm target; cần await và correlation |

### 7.1. Điều chỉnh tham số với `ProceedingJoinPoint`

```java
@Around("execution(* com.example.app.PaymentService.pay(..))")
public Object normalize(ProceedingJoinPoint pjp) throws Throwable {
    Object[] args = pjp.getArgs().clone();
    args[0] = ((String) args[0]).trim();
    return pjp.proceed(args);
}
```

Đây là **fragment**, giả định method được intercept có ít nhất một `String` argument. Cách này đổi dữ liệu mà target nhận, khác `@Before` chỉ bind một tham số đọc được. Trong Spring AOP, dùng `proceed(Object[])` cần giữ số lượng và kiểu arguments phù hợp với method signature. Không áp dụng tùy tiện cho các method overload hoặc argument types không đồng nhất.

### 7.2. Retry trong around

```java
@Around("bean(paymentService) && execution(* pay(..))")
public Object withRetry(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (TransientFailure error) {
        return pjp.proceed();
    }
}
```

Đây là **fragment** giả định `TransientFailure` là exception ứng dụng; không phải fixture đã chạy. Nó có thể gọi target hai lần nếu lần đầu ném lỗi thuộc loại được bắt. Cẩn trọng với idempotency, transaction, cache và các advisors nằm phía trong retry aspect. Nếu business cần retry đáng tin cậy, xem xét cơ chế retry chuyên dụng và viết integration test theo đường gọi thực tế.

## 8. Các lỗi binding phổ biến trong Spring AOP

### 8.1. Tên biến pointcut không khớp `argNames`

```java
@Before(
    value = "execution(* submit(..)) && args(code)",
    argNames = "wrongName"
)
public void before(String incoming) {
    // Binding không nhất quán với biến "code".
}
```

`args(code)` công bố biến `code`; Spring cần biết tham số advice nào nhận `code`. Nếu build không có metadata tham số hoặc biến binding mơ hồ, dùng `argNames="code"` sẽ rõ ràng hơn. Với Java hiện đại nên cấu hình `-parameters` cho module chứa `@Aspect`.

### 8.2. Nhầm `@annotation` với `args`

```java
@Before(
    value = "execution(* submit(..)) && @annotation(marker)",
    argNames = "marker"
)
public void before(Tagged marker) {}
```

Ở đây `marker` là annotation object, không phải tham số String của method. Nếu muốn String argument, cần `args(code,..)` và tham số `String code` riêng.

### 8.3. `@AfterReturning` không chạy vì target ném lỗi

Chỉ vì một method được chọn bởi pointcut không có nghĩa mọi advice kind đều chạy. Return branch và exception branch khác nhau. Khi debug, kiểm tra target result/exception trước rồi mới kiểm tra binding.

### 8.4. `@AfterThrowing` không chạy do kiểu exception

`throwing="error"` bind exception vào tham số có kiểu cụ thể. Nếu method ném exception không tương thích với kiểu đó, advice sẽ không được gọi. Nếu muốn quan sát rộng, có thể dùng `Throwable`; nếu chỉ xử lý nghiệp vụ đặc thù, giữ kiểu exception hẹp.

### 8.5. Lời gọi không qua Spring proxy

Đăng ký `@Aspect` và `@EnableAspectJAutoProxy` không làm mọi instance tạo bằng `new` trở thành AOP proxy. Trong test phải lấy service từ Spring context. Với self-invocation, method được gọi nội bộ trên `this` không đi qua proxy mode. Đây là giới hạn của Spring AOP, không phải bug binding.

## 9. Câu hỏi ôn tập theo tình huống

| Câu hỏi | Đáp án cần nhớ |
| --- | --- |
| Chỉ cần ghi argument trước lời gọi? | `@Before`, binding `args(...)` hoặc `JoinPoint.getArgs()` |
| Cần ghi return value khi thành công? | `@AfterReturning(returning="...")` |
| Cần ghi exception của một loại lỗi? | `@AfterThrowing(throwing="...")` với kiểu tương thích |
| Cần dọn tài nguyên dù success hay error? | `@After` hoặc `finally` trong `@Around` |
| Cần không chạy target trong một số trường hợp? | `@Around` và chủ động không `proceed()` |
| Cần trả fallback sau khi target ném exception? | `@Around` catch rồi return fallback |
| Cần tính thời gian trọn lời gọi? | `@Around` ghi thời điểm trước/sau `proceed()` |
| Cần lấy `@Tagged` instance trên method? | `@annotation(tagged)`, binding kiểu annotation |
| `@AfterReturning` method `return` một `String` mới có đổi return caller không? | Không: Spring không dùng return của method advice làm return replacement |
| Vì sao `@AfterThrowing` không chạy khi `RuntimeException` xảy ra? | Kiểm tra pointcut và kiểu tham số `throwing`; có thể không tương thích |
| Vì sao advice có annotation nhưng không chạy? | Kiểm tra aspect bean, proxy, pointcut, method visibility và self-invocation |
| Có thể xác định order giữa aspects bằng `@Order` không? | Có, số nhỏ hơn precedence cao hơn; test trace actual |
| `JoinPoint` và `ProceedingJoinPoint` khác nhau thế nào? | `ProceedingJoinPoint` dùng trong around và có `proceed()` |
| Return value `null` là success hay failure? | Normal return, không phải exception |
| `@After` nghĩa là “chỉ khi lỗi”? | Không, đó là finally-style completion |

## 10. Checklist thiết kế một custom Spring aspect

- [ ] Nêu đúng scope và chỉ dùng Spring AOP pointcuts tương thích với method-execution join points.
- [ ] Chọn advice type ít quyền nhất đủ phục vụ policy.
- [ ] Xác định dữ liệu phải bind: `args`, annotation, result hay exception.
- [ ] Có tên `argNames` và `returning`/`throwing` nhất quán khi cần.
- [ ] Không giả định `@Before` hoặc `@AfterReturning` có thể tự thay result bằng `return`.
- [ ] Nếu dùng `@Around`, đảm bảo `proceed()` được gọi đúng số lần hoặc short-circuit có chủ đích.
- [ ] Đã định nghĩa cách xử lý exception và side effect của chính advice.
- [ ] Có `@Order`/ordering contract nếu nhiều aspects phải phối hợp.
- [ ] Kiểm thử lời gọi qua Spring proxy, gồm success, exception và method không match.
- [ ] Không nhầm hoàn tất advice với transaction commit/cache miss/async worker completion.
- [ ] Không đặt mutable request state vào field của singleton aspect.
- [ ] Khi cần đọc metadata, biết method có thể đến từ interface proxy và dùng Spring utilities thích hợp.

## 11. Kết luận

Bảng tra cứu cần nhớ:

- **`@Before`**: quan sát trước invocation, có thể chặn bằng exception, không thể tự `return` một kết quả thay target.
- **`@AfterReturning`**: quan sát nhánh normal return và bind result, không phải công cụ replace return value.
- **`@AfterThrowing`**: quan sát nhánh exception tương thích, không phục hồi thành success bằng return của advice.
- **`@After`**: finalization-style advice cho hai nhánh, có thể tự ném lỗi.
- **`@Around`**: bao quanh, có `proceed()`, là lựa chọn duy nhất trong năm loại có quyền short-circuit, thay args/result, xử lý exception một cách tường minh.

Trong **Spring Framework**, quyền đó chỉ được thực thi khi lời gọi đi qua AOP proxy phù hợp và advisor/pointcut match. Vì vậy kiến thức về advice phải đi kèm test có Spring context thật, `AopUtils.isAopProxy`, trace theo từng lời gọi và assertion trên kết quả/exception, không chỉ học thuộc tên annotation.

### Tài liệu chính thức

- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html): đầy đủ các loại advice, binding, `proceed()`, ordering.
- [Spring Framework — Declaring a Pointcut](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html): pointcut designators và dữ liệu binding.
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html): đăng ký `@Aspect` như Spring bean, bật auto-proxy.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB proxy và self-invocation.
- [Spring Framework — Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html): kiểm tra danh sách advisors trong Spring proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-review-005">Danh sách rà soát đăng ký bean, proxy và thứ tự advice</a>

<details>
<summary>Xem chi tiết</summary>

# Danh sách rà soát đăng ký bean, proxy và thứ tự advice trong Spring AOP

## 1. Mục đích sử dụng

Đây là checklist tra cứu dành cho ứng dụng **Spring Framework sử dụng Spring AOP** khi một `@Aspect` không chạy, advice không có hiệu lực, chạy hai lần hoặc chạy sai thứ tự. Tập trung vào những ranh giới thực sự quyết định behavior: **Spring bean registration → auto-proxy infrastructure → proxy reference → advisor/pointcut match → advice precedence**.

Chẩn đoán đúng phải tách được bốn câu hỏi:

1. **Aspect đã được đăng ký thành Spring bean/advisor chưa?** Có `@Aspect` trên source không đồng nghĩa auto-proxy creator đã biết nó.
2. **Bean target có proxy thích hợp chưa?** Object mà caller giữ có thể là Spring AOP proxy, raw target, hoặc bean của `ApplicationContext` khác.
3. **Điểm cắt và lời gọi có match không?** Pointcut đúng vẫn không chạy nếu `this.method()` bỏ qua proxy, method không thể intercept hoặc đối số/annotation không khớp.
4. **Advisor precedence có đúng không?** Hai advice đều chạy nhưng có thể lồng nhau khác dự kiến; `@Order` số nhỏ có precedence cao, đi vào trước/đi ra sau.

**Không dùng `AopUtils.isAopProxy(bean) == true` như bằng chứng duy nhất cho custom aspect.** Bean có thể được Spring tạo proxy vì `@Transactional`, caching hoặc advisor khác, còn custom advisor chưa match.

Bài học chỉ tập trung vào **Aspect trong Spring**, không mở rộng sang Servlet Filter, bytecode weaving chung hay mọi lifecycle callback không liên quan trực tiếp đến Spring AOP.

## 2. Checklist tổng quát: rà soát trước khi chỉnh sửa

- [ ] **Đúng container?** Aspect/advisor và target bean nằm trong `ApplicationContext` có quan hệ cấu hình phù hợp.
- [ ] **Hạ tầng AOP được bật?** `@EnableAspectJAutoProxy` hoặc XML Spring AOP tương ứng được thực sự nạp.
- [ ] **Đúng aspect bean?** Có `@Aspect` **và** `@Component` được scan hoặc `@Bean` được import; không đăng ký trùng.
- [ ] **Target qua Spring?** Reference lấy từ dependency injection hoặc `context.getBean`, không phải `new Service()`.
- [ ] **Không khởi tạo quá sớm?** BPP/BeanFactoryPostProcessor không ép tạo service trước khi auto-proxy creator hoàn tất.
- [ ] **Đã xác minh proxy runtime?** `AopUtils.isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy`.
- [ ] **Method có thể intercept?** CGLIB không override `final`/`private`; JDK proxy chỉ công bố interface proxy.
- [ ] **Đường gọi qua proxy?** Không phải `this.inner()` hoặc reference raw target.
- [ ] **Pointcut phù hợp?** Kiểm `execution`/`within`/`@annotation`/`args`/`bean` cùng target class, method overload và runtime arguments.
- [ ] **Đúng advisor?** Kiểm `Advised.getAdvisors()` nếu có và ghi nhận advice invocation thật sự.
- [ ] **Thứ tự có chủ đích?** Chọn `@Order`/`Ordered` hoặc XML `order`; không đoán từ vị trí các method trong file.
- [ ] **Có test đối chứng?** Ít nhất có method match, method không match, exception, self-invocation và kiểm thứ tự vào/ra.

**Quy tắc xử lý:** nếu chưa có proxy/advisor phù hợp, thay `@Order` không khắc phục được. Nếu đã có advisor đúng mà advice không chạy, kiểm đường gọi và pointcut **trước** khi sửa configuration.

## 3. Sơ đồ xác định điểm hỏng

~~~text
Không thấy advice hoặc advice sai
   |
   +-- Bean reference do Spring tạo?
   |      |-- Không → new/raw target; lấy bean được inject
   |      +-- Có
   |
   +-- Spring AOP proxy?
   |      |-- Không → context, @EnableAspectJAutoProxy, aspect bean,
   |      |            BeanPostProcessor phase, advisor eligibility
   |      +-- Có
   |
   +-- Advisor cần tìm có trong proxy?
   |      |-- Không → pointcut / advisor selection / khác context
   |      +-- Có
   |
   +-- Method call qua proxy?
   |      |-- Không → this.method(), @PostConstruct, raw reference
   |      +-- Có
   |
   +-- Có thể advise method?
   |      |-- CGLIB final/private hoặc JDK sai interface?
   |      +-- Có
   |
   +-- Pointcut runtime match?
   |      |-- Không → method signature, annotation, args, bean name
   |      +-- Có
   |
   +-- Advice chạy mà số lần/order sai?
          |-- Kiểm @Order / Ordered / XML order
          |-- Kiểm nhiều advisor cùng join point
          |-- Kiểm loại advice, retry, transaction, exception
          +-- Kiểm event trace và assertion trong test
~~~

Nếu `isAopProxy=false`, đừng tự kết luận lỗi: một bean không thuộc bất kỳ pointcut/advisor nào **không cần proxy**. Chỉ coi đó là dấu hiệu lỗi khi yêu cầu của bài toán khẳng định method phải được advise.

## 4. Đăng ký @Aspect và bật Spring AOP

### 4.1. Hai điều kiện khác nhau: metadata và Spring bean

~~~java
@Aspect
@Component
public class RequestAuditAspect {

    @Before("execution(* com.example.service..*(..))")
    public void before() {
        // ghi nhận sự kiện hợp lệ
    }
}
~~~

Đoạn là **snippet**, cần imports `Aspect`, `Before`, `Component` và component scan bao phủ package. `@Aspect` không tự đăng ký một singleton bean; `@Component` hoặc `@Bean` mới cho Spring biết instance.

Một phương án không cần component scan:

~~~java
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class AopConfiguration {

    @Bean
    RequestAuditAspect auditAspect() {
        return new RequestAuditAspect();
    }
}
~~~

Đây là snippet configuration, cần imports `Configuration`/`EnableAspectJAutoProxy`/`Bean` và class aspect phù hợp. Trong ví dụ đầy đủ ở mục 10, chúng ta khai báo hai aspect beans theo chính pattern này.

### 4.2. Rà soát cụ thể

| Hạng mục | Câu hỏi cần trả lời | Dấu hiệu lỗi |
| --- | --- | --- |
| `@Aspect` | Class khai báo advisor/advice đúng không? | Chỉ có Java method thường |
| `@Component` hoặc `@Bean` | Spring biết aspect object không? | `@Aspect` không thành Spring bean |
| Component scanning | Package aspect có nằm trong scan range? | Bean aspect không có trong context |
| Profiles/conditions | `@Profile`/`@Conditional` có loại aspect? | Chỉ fail trên một environment |
| `@EnableAspectJAutoProxy` | Được import vào đúng context? | Không phát hiện annotation-style aspect |
| Advisor đặc thù | Transaction/cache/security đã bật riêng? | Có proxy nhưng custom advisor vắng |
| Trùng config | Có hai aspect beans cùng logic? | Advice có thể chạy lặp |
| Context hierarchy | Service thuộc root hay servlet child? | Aspect ở child không tự post-process root bean |

Với Spring Boot có AOP starter, Boot có thể kích hoạt auto-proxy theo cấu hình, nhưng default proxy type còn phụ thuộc properties và phiên bản. **Quan sát proxy thật**, không khẳng định cố định JDK hoặc CGLIB chỉ từ tên starter.

### 4.3. Phân biệt các cấu hình XML

- `<aop:aspectj-autoproxy/>`: phát hiện các **`@Aspect` bean** trong Spring AOP.
- `<aop:config><aop:aspect>...`: schema-based Spring AOP advisor declarations, không bắt class advice có `@Aspect`.
- `@EnableAspectJAutoProxy`: Java/annotation-style enabling.
- `META-INF/aop.xml` của AspectJ LTW: **không phải** cấu hình Spring AOP proxy.

Cấu hình nhiều auto-proxy pathways có thể được hợp nhất và lựa chọn các proxy settings mạnh hơn. Không bật bừa cả schema XML và annotation aspect cho cùng behavior; test số lần advice thực thi.

## 5. BeanPostProcessor và lifecycle: vì sao bean tạo sớm không được proxy?

Spring AOP auto-proxy creator có vai trò `BeanPostProcessor`. Khi các BPP cần thiết đã đăng ký, bean được tạo và xử lý thông thường có thể được bọc thành proxy. Nhưng **một application bean được instantiate quá sớm** trong lúc Spring còn tạo/đăng ký BPP có thể không đủ điều kiện nhận auto-proxy.

~~~text
ApplicationContext.refresh()
   |
   +-- Process BeanFactoryPostProcessors
   |      └-- Nếu gọi getBean(service) → service có thể bị tạo QUÁ SỚM
   |
   +-- Đăng ký BeanPostProcessors / auto-proxy creator
   |
   +-- Tạo singleton beans thông thường
          └-- AOP post-processor → xem advisor → proxy nếu phù hợp
~~~

Rà soát:
- [ ] `BeanFactoryPostProcessor` có ép gọi `beanFactory.getBean("myService")` không?
- [ ] Một `BeanPostProcessor` phụ thuộc trực tiếp vào business service muốn auto-proxy không?
- [ ] Method `@Bean` đăng ký BPP/BFPP được khai báo `static` khi phù hợp và return type nhận diện được là post-processor chưa?
- [ ] Bean được gọi từ constructor/`@PostConstruct` rồi nhầm lời gọi đó đã đi qua proxy cuối cùng không?
- [ ] Có custom `FactoryBean`/circular reference với early proxy reference làm đường khởi tạo phức tạp không?

Spring có thể cảnh báo:

~~~text
Bean 'myService' ... is not eligible for getting processed by
all BeanPostProcessors (for example: not eligible for auto-proxying).
~~~

Thông báo chỉ mang tính minh họa; wording và log level có thể khác theo Spring version. Nếu gặp, hãy lần theo **ai ép bean khởi tạo trước khi BPP hoàn tất**. Không sửa triệu chứng bằng cách chỉ thêm `@Lazy`: lời gọi `getBean()` từ processor vẫn có thể buộc lazy bean instantiate sớm.

**Lưu ý:** không phải mọi “early reference” đều là lỗi thiếu proxy. Spring có cơ chế `getEarlyBeanReference` cho một số circular dependencies. Phải quan sát proxy cuối cùng, log và timeline thật trước khi kết luận.

## 6. Đúng ApplicationContext và đúng reference

### 6.1. Root vs child context

Ứng dụng Spring MVC truyền thống có thể có root context chứa services và child `DispatcherServlet` context chứa controllers. BPP được scoped theo context; **child BPP không xử lý lại parent-owned beans**.

~~~text
ROOT ApplicationContext
  └-- billingService (target)

CHILD ApplicationContext
  ├-- @EnableAspectJAutoProxy
  ├-- loggingAspect
  └-- billingController
         └-- inject billingService từ parent
              (child BPP không tự bọc parent bean)
~~~

Nếu service thuộc root, phải cấu hình aspect/auto-proxy tương ứng ở root để nó được xử lý đúng. Spring Boot nhiều configuration classes **không nhất thiết có nhiều contexts**, nên kiểm runtime hierarchy thay vì chỉ nhìn packages.

### 6.2. Snippet xem bean ownership

Trong một phương thức đã có `ConfigurableApplicationContext context`:

~~~java
System.out.println("ctx=" + context.getId());
System.out.println("parent=" +
    (context.getParent() == null
        ? "none" : context.getParent().getId()));
System.out.println("visible=" +
    context.containsBean("billingService"));
System.out.println("localDefinition=" +
    context.getBeanFactory().containsBeanDefinition("billingService"));
System.out.println("localSingleton=" +
    context.getBeanFactory().containsSingleton("billingService"));
~~~

`containsBean` có thể trả true nhờ parent lookup; `containsBeanDefinition` là thông tin về definition **local**, không đại diện mọi singleton đăng ký thủ công, nên xem cả `containsSingleton` khi cần.

Một caller có thể giữ reference raw target được lấy từ `new`, custom factory hoặc quá trình khởi tạo, trong khi `context.getBean(...)` là proxy. Kiểm **object identity** và nơi reference được inject, không chỉ class name.

## 7. Kiểm proxy JDK/CGLIB và danh sách advisors

| Câu hỏi | JDK dynamic proxy | CGLIB class-based proxy |
| --- | --- | --- |
| Kế thừa concrete target class? | Không | Có, nếu target có thể subclass |
| Implements business interface? | Có theo interfaces được công bố | Có thể |
| Lấy bean theo concrete class? | Có thể không khả dụng | Thường khả dụng |
| Có thể advise implementation method final? | Có thể qua method của interface | Không thể override final method |
| Final target class | Có thể proxy qua interface | Không thể subclass |
| Private method | Không thể là interface proxy method | Không thể override/intercept riêng |
| `this.inner()` | Bỏ qua Spring proxy advice | Bỏ qua Spring proxy advice |

**Không dùng câu “final method luôn bỏ qua AOP” cho mọi proxy.** Nó đúng với giới hạn override CGLIB; JDK interface proxy có thể intercept lời gọi interface dù implementation method là final.

Trong code đã có `ApplicationContext context`:

~~~java
Object bean = context.getBean("billingService");

System.out.println("proxy=" + AopUtils.isAopProxy(bean));
System.out.println("jdk=" + AopUtils.isJdkDynamicProxy(bean));
System.out.println("cglib=" + AopUtils.isCglibProxy(bean));
System.out.println("target=" +
    AopUtils.getTargetClass(bean).getName());
System.out.println("runtime=" + bean.getClass().getName());

if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println("advisor=" +
            advisor.getClass().getName());
    }
}
~~~

Đây là snippet cần import `org.springframework.aop.support.AopUtils`, và không phải một chương trình độc lập. Dùng `Advised.getAdvisors()` để xem advisor được gắn khi proxy hỗ trợ interface `Advised`; **một advisor tồn tại chưa có nghĩa nó match mọi method**.

**`AopUtils.getTargetClass(bean)` là class đích, không nói object là raw target.** Phải kết hợp với `isAopProxy` và runtime class.

## 8. Pointcut, method eligibility và self-invocation

Spring AOP chỉ hỗ trợ **method execution join point trên proxy**. Khi pointcut không chạy, đối chiếu biểu thức và **đường gọi thực tế**.

| Designator | Câu hỏi rà soát |
| --- | --- |
| `execution(* demo.service.*Service.*(..))` | Package/type/method/overload thật có match? |
| `within(demo.service..*)` | Class được Spring xem là target có nằm trong type pattern? |
| `@annotation(tracked)` | Method được annotate đúng chỗ, runtime retention đúng, binding đúng? |
| `args(id,..)` | Runtime args có số lượng/vị trí/type phù hợp? |
| `bean(*Service)` | Bean name có match, có shadowing giữa contexts? |
| `this(proxy)` | Object proxy có đúng interface/class để bind? |
| `target(obj)` | Target object phù hợp kiểu bind? |

**Self-invocation:** khi caller gọi `proxy.outer()`, advice trên outer có thể chạy; nhưng target `outer()` gọi `this.inner()` thì riêng advice trên inner **không tự chạy**, dù method inner vẫn thực thi. Tương tự, object tạo bằng `new Service()` không được Spring auto-proxy.

CGLIB không thể advise final/private method bằng subclass override. Với JDK, caller phải qua interface proxy; cố inject theo concrete implementation có thể lỗi.

**Đối chứng nhanh khi pointcut nghi sai:**
1. Dùng một method `public`, không `final`.
2. Viết pointcut hẹp `execution(* demo.service.ExampleService.run(..))`.
3. Gọi method **qua injected Spring bean** từ bên ngoài target.
4. Tăng counter/event trong advice.
5. Chỉ khi case dương tính chạy mới mở rộng pointcut/annotation binding để xác định phần sai.

## 9. Thứ tự advice: quy tắc thực thi chính xác

### 9.1. Giữa các @Aspect classes

Hai aspects khác nhau cùng match method có thể được thứ tự hóa bằng `@Order(n)` hoặc `Ordered`. **Số nhỏ hơn = precedence cao hơn**.

Ví dụ, với `@Order(10)` và `@Order(20)`, cả hai dùng `@Around` gọi `proceed()` đúng một lần:

~~~text
@Order(10): before
    @Order(20): before
        target method
    @Order(20): after
@Order(10): after
~~~

Aspect precedence cao chạy **trước trên đường vào, sau trên đường ra** — giống wrapper bao ngoài. Khi method throw, nhánh `catch/finally` của inner thường chạy trước nhánh tương ứng của outer.

Nếu các aspects không có order tường minh hoặc cùng precedence, không nên dựa vào thứ tự quan sát tình cờ trong một môi trường.

### 9.2. Trong cùng một class @Aspect

- Spring áp quy tắc precedence dựa trên **loại advice**, ví dụ `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing`. `@After` có finally semantics trên đường ra.
- **Hai advice cùng loại trong cùng một `@Aspect` class** cùng match một join point không có thứ tự được bảo đảm từ vị trí method trong source; Java reflection không cung cấp thứ tự khai báo đáng tin.
- Nếu cần hai `@Before` chạy theo thứ tự nhất định, **gộp logic trong một advice** hoặc tách ra hai aspect classes đặt `@Order` rõ.
- Không dùng `@AfterReturning` để bắt exception và không dùng `@AfterThrowing` để ghi nhận return bình thường.

### 9.3. Trong schema-based XML Spring AOP

`<aop:aspect order="10">` có precedence cao hơn `<aop:aspect order="20">`. Với **nhiều advice trong cùng một `<aop:aspect>`**, Spring XML schema dùng thứ tự advice elements được khai báo để xác định precedence, khác với cách suy luận từ vị trí Java methods trong `@Aspect`.

Fragment minh họa:

~~~xml
<aop:aspect id="outer" ref="outerAdvice" order="10">
    <aop:around method="around" pointcut-ref="operations"/>
</aop:aspect>
<aop:aspect id="inner" ref="innerAdvice" order="20">
    <aop:around method="around" pointcut-ref="operations"/>
</aop:aspect>
~~~

Đây là fragment cần parent `<aop:config>`, bean refs và pointcut `operations`; **không phải file XML độc lập**.

### 9.4. @Order không phải một “thứ tự chung toàn ứng dụng”

`@Order` của aspect quy định AOP advisor precedence. Thứ tự `BeanPostProcessor`, Servlet Filter, MVC HandlerInterceptor, Spring Security filter chain và transaction advisor còn có cơ chế/giá trị riêng.

Nếu custom aspect đo thời gian đặt **ngoài** transaction advice, có thể đo cả mở/commit transaction; đặt **trong** sẽ có boundary khác. Thứ tự không đơn thuần là quyết định trình bày log, mà có thể ảnh hưởng security, rollback và retry semantics.

## 10. Ví dụ Java 17+ chạy được: hai aspects và kiểm thứ tự lồng nhau

Project sử dụng **Spring Framework 6.1.14**, AOP proxy kiểu CGLIB, Gradle và JUnit Jupiter. Bài tập tạo hai `@Aspect` thực, order 10/20, ghi trace trong success và error, rồi kiểm tra các đường không đi qua advice.

### 10.1. Cấu trúc và dependency

~~~text
build.gradle
src/main/java/demo/check/
    EventTrace.java
    LedgerService.java
    OuterAspect.java
    InnerAspect.java
    Config.java
    Main.java
src/test/java/demo/check/
    AopChecklistTests.java
~~~

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

    testImplementation platform('org.junit:junit-bom:5.11.3')
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

application {
    mainClass = 'demo.check.Main'
}

test {
    useJUnitPlatform()
}
~~~

Không cần `-javaagent` hoặc `META-INF/aop.xml`: hạ tầng ở đây là **Spring AOP proxy**, không phải AspectJ LTW.

### 10.2. Recorder và target

**`src/main/java/demo/check/EventTrace.java`**

~~~java
package demo.check;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventTrace {
    private final CopyOnWriteArrayList<String> events =
            new CopyOnWriteArrayList<>();

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

**`src/main/java/demo/check/LedgerService.java`**

~~~java
package demo.check;

public class LedgerService {
    private final EventTrace trace;

    public LedgerService(EventTrace trace) {
        this.trace = trace;
    }

    public String book(String code, int units) {
        trace.add("target");
        if (units < 0) {
            throw new IllegalArgumentException("units must be nonnegative");
        }
        return "booked:" + code + ":" + units;
    }

    public String preview(String code) {
        return "preview:" + code;
    }

    public String bookInternally(String code, int units) {
        return this.book(code, units);
    }
}
~~~

`book` có nhánh thành công/lỗi; `preview` không match pointcut; `bookInternally` cố ý gọi `this.book` để chứng minh self-invocation. Code target không trực tiếp gọi aspect; sự kiện `target` chỉ cho biết business method chạy.

### 10.3. Outer aspect ưu tiên cao

**`src/main/java/demo/check/OuterAspect.java`**

~~~java
package demo.check;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class OuterAspect {
    private final EventTrace trace;

    public OuterAspect(EventTrace trace) {
        this.trace = trace;
    }

    @Around("execution(* demo.check.LedgerService.book(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        trace.add("outer.before");
        try {
            Object result = pjp.proceed();
            trace.add("outer.after");
            return result;
        } catch (Throwable ex) {
            trace.add("outer.error");
            throw ex;
        }
    }
}
~~~

### 10.4. Inner aspect ưu tiên thấp

**`src/main/java/demo/check/InnerAspect.java`**

~~~java
package demo.check;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(20)
public class InnerAspect {
    private final EventTrace trace;

    public InnerAspect(EventTrace trace) {
        this.trace = trace;
    }

    @Around("execution(* demo.check.LedgerService.book(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        trace.add("inner.before");
        try {
            Object result = pjp.proceed();
            trace.add("inner.after");
            return result;
        } catch (Throwable ex) {
            trace.add("inner.error");
            throw ex;
        }
    }
}
~~~

Hai `@Around` nằm ở **hai classes**, mỗi class có một order riêng. `@Order(10)` chạy ngoài `@Order(20)`; chúng đều gọi `proceed()` chính xác một lần và truyền nguyên exception nếu target lỗi.

### 10.5. Spring configuration

**`src/main/java/demo/check/Config.java`**

~~~java
package demo.check;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class Config {

    @Bean
    EventTrace eventTrace() {
        return new EventTrace();
    }

    @Bean
    LedgerService ledgerService(EventTrace trace) {
        return new LedgerService(trace);
    }

    @Bean
    OuterAspect outerAspect(EventTrace trace) {
        return new OuterAspect(trace);
    }

    @Bean
    InnerAspect innerAspect(EventTrace trace) {
        return new InnerAspect(trace);
    }
}
~~~

`@Aspect` phải có Spring bean instance, nên hai `@Bean` của `OuterAspect`/`InnerAspect` quan trọng. Ví dụ không dùng component scanning, giảm khả năng đăng ký lặp.

### 10.6. Main và stdout có thể dự đoán

**`src/main/java/demo/check/Main.java`**

~~~java
package demo.check;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {

            LedgerService service = context.getBean(LedgerService.class);
            EventTrace trace = context.getBean(EventTrace.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println("cglib=" + AopUtils.isCglibProxy(service));
            System.out.println("value=" + service.book("A-1", 2));
            System.out.println("trace=" + trace.snapshot());
        }
    }
}
~~~

Chạy từ project có Gradle wrapper:

~~~bash
./gradlew run
~~~

Windows PowerShell:

~~~powershell
.\gradlew.bat run
~~~

Các dòng do chương trình in ra (không tính log Spring/Gradle):

~~~text
proxy=true
cglib=true
value=booked:A-1:2
trace=[outer.before, inner.before, target, inner.after, outer.after]
~~~

Đây là **expected stdout theo code**, không phải kết quả build đã chạy. Chuỗi event chứng minh cụ thể thứ tự. Chỉ check `isAopProxy=true` thì không đủ để biết hai aspects đã chạy hay lồng nhau đúng.

## 11. JUnit Jupiter: test registration, order, error và self-invocation

**`src/test/java/demo/check/AopChecklistTests.java`**

~~~java
package demo.check;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class AopChecklistTests {

    @Test
    void proxyAndAdviceOrderAreCorrect() {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            LedgerService service = context.getBean(LedgerService.class);
            EventTrace trace = context.getBean(EventTrace.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertTrue(AopUtils.isCglibProxy(service));
            assertTrue(service instanceof Advised);
            assertTrue(((Advised) service).getAdvisors().length >= 2);

            assertEquals("booked:A-1:2", service.book("A-1", 2));
            assertEquals(List.of(
                "outer.before", "inner.before", "target",
                "inner.after", "outer.after"
            ), trace.snapshot());
        }
    }

    @Test
    void exceptionsUnwindAdvisorsInReverseOrder() {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            LedgerService service = context.getBean(LedgerService.class);
            EventTrace trace = context.getBean(EventTrace.class);

            var error = assertThrows(IllegalArgumentException.class,
                    () -> service.book("A-2", -1));

            assertEquals("units must be nonnegative", error.getMessage());
            assertEquals(List.of(
                "outer.before", "inner.before", "target",
                "inner.error", "outer.error"
            ), trace.snapshot());
        }
    }

    @Test
    void methodOutsidePointcutIsNotAdvised() {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            LedgerService service = context.getBean(LedgerService.class);
            EventTrace trace = context.getBean(EventTrace.class);

            assertEquals("preview:A-3", service.preview("A-3"));
            assertTrue(trace.snapshot().isEmpty());
        }
    }

    @Test
    void selfInvocationBypassesSpringProxy() {
        try (var context =
                new AnnotationConfigApplicationContext(Config.class)) {
            LedgerService service = context.getBean(LedgerService.class);
            EventTrace trace = context.getBean(EventTrace.class);

            assertEquals("booked:A-4:4", service.bookInternally("A-4", 4));
            assertEquals(List.of("target"), trace.snapshot());
        }
    }

    @Test
    void newObjectHasNoSpringAopProxy() {
        EventTrace trace = new EventTrace();
        LedgerService raw = new LedgerService(trace);

        assertFalse(AopUtils.isAopProxy(raw));
        assertEquals("booked:A-5:5", raw.book("A-5", 5));
        assertEquals(List.of("target"), trace.snapshot());
    }
}
~~~

Chạy:

~~~bash
./gradlew test --tests demo.check.AopChecklistTests
~~~

Windows:

~~~powershell
.\gradlew.bat test --tests demo.check.AopChecklistTests
~~~

Với cấu hình đúng và dependency tải được, kỳ vọng **5 test passes**. Không nhầm “expected” với kết quả test đã thực sự chạy. Test có một positive control (đủ proxy, 2 advisors, events vào/ra) và các negative controls (method không match, self-invocation, raw object). Đây là cách tránh kết luận sai kiểu “advice không chạy do self-invocation” trong khi thực tế aspect chưa bao giờ được đăng ký.

## 12. Chẩn đoán khi lời gọi trả đúng kết quả nhưng không có event

| Triệu chứng | Nguyên nhân có thể | Kiểm tra kế tiếp |
| --- | --- | --- |
| `@Aspect` đã tồn tại nhưng không thấy advice | Aspect không thành bean hoặc không bật AOP | Scan/import đúng context, `@Bean`/`@Component` |
| Bean có trong context nhưng không phải proxy | Không match advisor, khởi tạo sớm, context không có BPP | `isAopProxy`, BPP log, ownership |
| Bean có proxy nhưng custom advice không chạy | Proxy được tạo bởi advisor khác hoặc pointcut không match | `getAdvisors`, method/annotations |
| Lời gọi `this.inner()` không advice | Self-invocation bypass proxy | Tách bean và gọi qua injected proxy |
| `new Service().run()` không advice | Raw Java object | Dùng Spring-managed instance |
| CGLIB không intercept final/private | Subclass không override được | Public non-final service boundary |
| JDK proxy không cast sang concrete class | Không subclass target | Inject business interface hoặc kiểm CGLIB config |
| Advice chạy lặp | Trùng bean/aspect, nhiều advisor hoặc retry | Advisor list, bean definitions, số proceed/calls |
| Advice thứ tự đảo | `@Order` sai/không có, XML order khác | Events trước/sau và precedence |
| `@AfterReturning` không chạy khi method throw | Returning advice chỉ quan sát normal return | `@AfterThrowing` hoặc `@Around` |
| Bean được gọi trong `@PostConstruct` không advice | Init callback chưa đi qua proxy cuối cùng | Test lời gọi sau refresh qua proxy |
| Child aspect không ảnh hưởng root service | BPP không xuyên context | Đăng ký AOP đúng context sở hữu target |

Nếu advisor có `retry`, target có thể được gọi nhiều lần dù caller chỉ gọi một lần. Khi chẩn đoán, tách số lần **proxy invocation**, **proceed()**, **target execution** và **subscriber signals** nếu method trả `Mono`/`Flux`.

## 13. Checklist khi cần thay đổi order trong production

Thứ tự advice ảnh hưởng không chỉ logs:
- Transaction advice mở/commit trước hay sau custom metric/audit?
- Security advisor có kiểm soát lời gọi trước khi custom retry/side effect xảy ra?
- Logging aspect có ghi dữ liệu nhạy cảm trước khi authorization check không?
- Retry advice có gọi lại target và một số inner aspects nhiều lần không?
- Exception được ghi nhận tại outer advisor hay đã được inner advisor xử lý thành fallback?
- Trong Spring WebFlux, đo method invocation hay thời điểm subscription/terminal?

Khi muốn thay `@Order`:
1. Lưu thứ tự hiện tại bằng integration test.
2. Xác định advisors thực tế (không chỉ các `@Aspect` nhìn thấy trong code).
3. Đổi precedence tối thiểu theo mục tiêu nghiệp vụ.
4. Test success, exception, transaction rollback/security behavior và số lời gọi target.
5. Đo lại latency nếu advisor chạy trên hot path; không tối ưu bằng cách bỏ cross-cutting guard quan trọng.

**Với advice cùng loại trong cùng aspect**, nếu thứ tự là business invariant, gộp method hoặc tách classes. Đừng dựa vào thứ tự sắp xếp theo tên method hay thứ tự xuất hiện trong source.

## 14. Checklist bảo trì trước khi merge một Spring Aspect

- [ ] Target beans, context owners và profiles được xác định.
- [ ] Aspect có đúng một registration hoặc đăng ký lặp đã được chứng minh không gây tác hại.
- [ ] Hạ tầng Spring AOP tương thích với cấu hình ứng dụng.
- [ ] Proxy type JDK/CGLIB đã được test theo API caller sử dụng.
- [ ] Không dựa vào `this.method()`, private/final method để nhận advice qua proxy.
- [ ] Pointcut match tập method cần bảo vệ, có negative test.
- [ ] Thứ tự advisor với transaction/security/cache/retry được test.
- [ ] `@Around` gọi `proceed()` đúng semantics, truyền nguyên return/exception khi cần.
- [ ] Đã xử lý concurrency nếu aspect là singleton có mutable state; tránh state theo request đặt trong field chia sẻ.
- [ ] Không log toàn bộ arguments/token/password mặc định; dùng whitelist/masking.
- [ ] Advice không chèn blocking I/O vào hot path nếu không thật cần.
- [ ] JUnit integration test tạo Spring context thật, không chỉ mock/raw object.
- [ ] Chẩn đoán `isAopProxy`/`getAdvisors` đã được viết lại thành checks/assertions có ích.
- [ ] Có kiểm tra sau upgrade Spring/JDK/AspectJ dependency vì proxy config và method resolution có thể đổi.

## 15. Câu hỏi ôn tập nhanh

**Câu 1.** `@Aspect` đã có nhưng Spring không tạo aspect bean: advice có chạy không?

Không theo cơ chế Spring AOP ở bài này. Cần aspect instance trong đúng `ApplicationContext` và auto-proxy infrastructure.

**Câu 2.** Một bean `isAopProxy=true` có chắc custom audit aspect đã match method không?

Không. Nó có thể được proxy vì advisor khác. Kiểm advisor list và sự kiện advice thực tế.

**Câu 3.** Hai aspect `@Order(10)` và `@Order(20)` đều `@Around`: cái nào chạy trước/sau?

`@Order(10)` chạy trước lúc đi vào target và thoát sau `@Order(20)`. Nó là outer wrapper khi cả hai gọi `proceed()` bình thường.

**Câu 4.** Hai `@Before` trong cùng `@Aspect` được viết theo thứ tự A rồi B. Có bảo đảm A chạy trước B không?

Không. Thứ tự giữa advice cùng loại trong cùng aspect không được đảm bảo từ source declaration order.

**Câu 5.** `child.getBean("rootService")` có làm BPP của child proxy root-owned singleton không?

Không. Child có thể lookup được parent bean, nhưng BPP được scoped theo container sở hữu bean.

**Câu 6.** Tại sao gọi `this.book()` không chạy Spring advice của `book()`?

Vì lời gọi nội bộ trên target không đi qua Spring AOP proxy.

**Câu 7.** Vì sao private/final method không được CGLIB advise?

CGLIB subclass proxy không override được private/final method. Method vẫn có thể chạy Java bình thường, nhưng không qua interceptor tại execution ấy.

**Câu 8.** `AopUtils.getTargetClass(bean)` trả concrete class thì bean chắc chắn không phải proxy?

Không. API này trả target type, không phải status của object. Xem `isAopProxy`.

**Câu 9.** Mục đích của negative test `preview` và self-invocation trong ví dụ là gì?

Chúng chứng minh scope pointcut đúng và phân biệt phương thức chạy nghiệp vụ với phương thức thực sự được advice.

**Câu 10.** Với reactive `Mono`/`Flux`, `@Around` bao quanh `pjp.proceed()` có đo được thời gian chạy toàn pipeline không?

Không chắc. Method thường trả publisher trước khi subscription; muốn đo signal cần Reactor operators tương ứng và giữ lifecycle reactive.

## 16. Tài liệu tham khảo chính thức

- [Spring Framework — @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html): đăng ký aspect beans, bật Spring AOP.
- [Spring Framework — Advice Ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html): `@Order`, precedence đường vào/ra và nhiều advice trong một aspect.
- [Spring Framework — Schema-based AOP](https://docs.spring.io/spring-framework/reference/6.2/core/aop/schema.html): `<aop:config>`, XML advisor và advice order.
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK/CGLIB, final/private, self-invocation.
- [Spring Framework — BeanPostProcessor Lifecycle](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html): BPP, early initialization và eligibility for auto-proxying.
- [Spring Framework — Spring AOP API](https://docs.spring.io/spring-framework/reference/core/aop-api.html): `Advised`, `ProxyFactory`, advisor/interceptor chain.
- [Spring Framework — TestContext Integration](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/support-classes.html): Spring/JUnit tests.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-review-006">Câu hỏi tình huống advice không chạy hoặc chạy sai thứ tự</a>

<details>
<summary>Xem chi tiết</summary>

# Câu hỏi tình huống: Spring advice không chạy hoặc chạy sai thứ tự

## 1. Cách sử dụng bộ tình huống

Trong Spring Framework, một `@Aspect` được khai báo không có nghĩa advice tự chạy trên mọi object có tên method phù hợp. Phần lớn vấn đề khi triển khai Spring AOP nằm ở **bean wiring**, **proxy boundary**, **pointcut matching**, **advisor ordering** hoặc cách `@Around` thực thi `proceed()`.

Bài này là bộ **câu hỏi tình huống và lời giải có thể kiểm chứng** để ôn tập, phỏng vấn, code review và chẩn đoán lỗi thực tế khi dùng Spring Aspect. Mọi nội dung đều đặt trong **Spring AOP proxy mode**: `@Aspect` là bean trong Spring container, `@EnableAspectJAutoProxy` hoặc infrastructure tương đương tạo proxy và những lời gọi đi qua proxy mới được advice. AspectJ bytecode weaving chỉ được nhắc tới khi so sánh ranh giới cơ chế.

Mỗi tình huống nên được giải theo cùng một trình tự:

1. **Dấu hiệu:** advice không chạy, chạy thiếu, chạy hai lần hay vào sai thứ tự?
2. **Giả thuyết:** vấn đề thuộc bean, proxy, pointcut, advisor, advice control flow hay target method?
3. **Bằng chứng:** kiểm tra `ApplicationContext`, `AopUtils`, `Advised`, events/counters và lời gọi thực tế.
4. **Cách sửa tối thiểu:** chỉnh đúng nguyên nhân, không đổi toàn bộ proxy strategy hoặc thêm annotation ngẫu nhiên.
5. **Regression test:** xác minh cả method match, method không match, return value, exception và ordering cần thiết.

**Ba câu hỏi quan trọng nhất** là: (1) *Caller có đang giữ Spring proxy không?* (2) *Advisor của aspect có match đúng method không?* (3) *Advice đứng ở lớp nào của interceptor chain và có `proceed()` không?*

## 2. Tra cứu nhanh theo dấu hiệu

Bài checklist trước hướng dẫn tuyến **bean → proxy → advisor → pointcut**; tại đây dùng bằng chứng ban đầu để chọn tình huống cụ thể:

| Triệu chứng | Bằng chứng tối thiểu | Xem tình huống |
| --- | --- | --- |
| Không có proxy hoặc advice | Aspect bean, AOP enabling, context chủ sở hữu, thời điểm tạo bean | 1–5, 19–20 |
| Proxy có nhưng method không nhận advice | Advisor list, signature, annotation, runtime arguments, caller reference | 3–9, 18, 22 |
| Advice vào nhưng target bị bỏ qua hoặc chạy lặp | Vết `proceed()`, cache hit, retry attempt count | 12–16 |
| Advice chạy sai thứ tự | `@Order`, chain thực, vết chiều vào/ra, tx/cache boundary | 10–11, 15–17 |
| Chỉ lỗi trong test/async | Mocked bean, thread/future, reactive subscription | 20–21 |

**Phải đo ba điều riêng:** `isAopProxy=true` chỉ xác nhận proxy; `getAdvisors()` chỉ cho thấy các advisor đã gắn; **event tại method được gọi qua proxy** mới chứng minh advice đã thực thi. Phân biệt số lần **caller → advice → target** trước khi sửa cấu hình.

## 3. Tình huống 1 — Có @Aspect nhưng advice không chạy

**Câu hỏi:** Service là một Spring bean, class `AuditAspect` chứa `@Before("execution(* demo..*Service.save(..))")`, nhưng không hề có audit log. Có phải pointcut sai không?

**Trả lời:** Có thể pointcut sai, nhưng **việc đầu tiên phải kiểm tra là aspect đã được đăng ký thành Spring bean chưa**. `@Aspect` đơn lẻ không phải một Spring stereotype tự đăng ký bean nếu thiếu component scan hoặc `@Bean`.

Ví dụ chưa đủ:

~~~java
@Aspect
public class AuditAspect {
    @Before("execution(* demo..*Service.save(..))")
    public void log() {
        System.out.println("save called");
    }
}
~~~

Đây là snippet minh họa, cần imports và một bean target phù hợp. Nếu `AuditAspect` không có `@Component` và không được khai báo bởi `@Bean`, Spring không tự tìm thấy instance.

Có thể đăng ký tường minh:

~~~java
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
    @Bean
    AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
~~~

**Kiểm tra:** `context.getBeanNamesForType(AuditAspect.class)`, sau đó lấy đúng target bean từ context và gọi method thông qua proxy. Nếu aspect bean đã hiện diện và advice vẫn vắng, mới xét pointcut/method signature.

## 4. Tình huống 2 — Aspect là bean nhưng quên bật AOP

**Câu hỏi:** `@Bean AuditAspect` đã có trong `ApplicationContext`, nhưng `AopUtils.isAopProxy(service)` trả `false`.

**Trả lời:** `@Aspect` bean được đăng ký **không thay thế việc bật auto-proxy infrastructure**. Với cấu hình Spring Framework Java thuần, bật `@EnableAspectJAutoProxy`; với XML có `<aop:aspectj-autoproxy/>`. Trong Spring Boot có thể có auto-configuration tương đương, nhưng không nên suy luận sự tồn tại của auto-proxy từ việc có Spring trên classpath.

~~~java
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
class OnlyAopConfig {
    @Bean
    AuditAspect auditAspect() {
        return new AuditAspect();
    }
}
~~~

Nếu dùng `@EnableAspectJAutoProxy` ở **một context khác** với context tạo service, nó có thể không proxy service đó. Spring AOP bean post-processors có phạm vi theo context quản lý bean.

**Kiểm tra:** log bean names/contexts, `AopUtils.isAopProxy(actualInjectedService)`, và một test control có/không `@EnableAspectJAutoProxy` trong cùng module.

## 5. Tình huống 3 — Gọi đúng method nhưng không đi qua proxy

**Câu hỏi:** Advice chạy trong controller nhưng không chạy trong unit test gọi `new PaymentService().pay()`.

**Trả lời:** Đó là hành vi đúng của **Spring AOP proxy mode**. Object tạo bằng `new` ngoài Spring IoC không đi qua auto-proxy `BeanPostProcessor` nên không có advisor chain trên reference ấy.

~~~java
PaymentService raw = new PaymentService();
raw.pay(); // Chỉ business method; không đi qua Spring AOP proxy.
~~~

So sánh với:

~~~java
PaymentApi service = context.getBean(PaymentApi.class);
service.pay(); // Spring AOP advice chạy nếu pointcut match.
~~~

Snippets này cần các types/constructor tương ứng. Quan sát object **thực tế**:

~~~java
System.out.println(AopUtils.isAopProxy(service));
System.out.println(AopUtils.getTargetClass(service));
~~~

**Không** dùng lời gọi raw target sau khi unwrap từ proxy để test AOP. Nếu method business chạy mà advice không chạy, đó có thể đơn giản vì test đã bypass proxy.

## 6. Tình huống 4 — Method A gọi B trong cùng target, B không được advice

**Câu hỏi:** `service.process()` được gọi qua proxy, nhưng bên trong `process()` gọi `this.save()`; `@Transactional` hoặc custom `@Around` dành cho `save()` không chạy.

**Trả lời:** Đây là **self-invocation**. Khi control flow đã tới target, `this.save()` gọi trực tiếp trên target object, **không quay lại proxy** để áp dụng advice riêng cho `save`.

~~~java
public class OrderService {
    public void process() {
        save(); // self-invocation: không có một proxy call mới cho save.
    }

    @Transactional
    public void save() {
        // persistence
    }
}
~~~

Nếu `process` đã nằm trong một transaction do caller/outer method mở, `save` vẫn có thể chạy trong transaction **đang tồn tại**; điều bị bỏ qua là transaction advice *riêng của lời gọi `save`*.

**Sửa ưu tiên:** tách `save` sang một Spring-managed bean khác, inject proxy đó vào workflow. Không cố sửa bằng `proxyTargetClass=true`; CGLIB cũng là proxy-based và vẫn gặp self-invocation. `AopContext.currentProxy()` chỉ là lựa chọn cuối khi đã bật `exposeProxy=true` và chấp nhận coupling.

## 7. Tình huống 5 — JDK proxy, CGLIB và final/private

**Câu hỏi:** Target có `final` method, một `@Around` match tên method nhưng không chạy. Đổi sang CGLIB có giúp không?

**Trả lời:** Không nhất thiết; **CGLIB phải override method**. `final` methods không override được, `private` methods cũng không, và `final` classes không subclass được. JDK proxy hoạt động theo **interface method contract**, vì vậy một final implementation class *có thể* được advice qua JDK interface proxy khi mọi điều kiện phù hợp.

| Tình huống | Kết luận |
| --- | --- |
| Kotlin/Java final class implement business interface, Spring dùng JDK proxy | Vẫn có thể advice interface methods |
| Không interface, class final, Spring cần CGLIB | Không tạo class-based proxy được |
| Class open nhưng method final, Spring dùng CGLIB | Class proxy có thể có, method final không được advise qua override |
| Method private chỉ được target gọi nội bộ | Không là join point mà Spring proxy thông thường intercept |
| JDK proxy được cast thành concrete implementation | Có thể `ClassCastException`; inject theo interface |

**Kiểm tra:** `AopUtils.isJdkDynamicProxy`, `isCglibProxy`, `getTargetClass` cùng `Modifier.isFinal`. Đừng chỉ nhìn `isAopProxy=true` rồi suy luận mọi method đều được advice.

## 8. Tình huống 6 — Biểu thức execution không match vì signature

**Câu hỏi:** Target method là `String calculate(String id)`, nhưng advice dùng `execution(* demo.Service.calculate())`. Vì sao không chạy?

**Trả lời:** `()` match **method không có tham số**; nó không match một `String` parameter. Dùng `(..)` nếu muốn 0 hoặc nhiều arguments, hoặc viết một tham số cụ thể.

~~~java
@Around("execution(* demo.Service.calculate(java.lang.String))")
~~~

hoặc:

~~~java
@Around("execution(* demo.Service.calculate(..))")
~~~

Đây là snippets annotation expression; cần đặt trên advice method trong một `@Aspect` bean. Cũng cần kiểm tra overloads, package đúng, return-type pattern, visibility và target implementation class. `execution(* demo..*Service.*(..))` có thể match **quá rộng**, làm lời gọi nhiều method bị advise ngoài ý định.

**Kiểm tra:** lấy đúng `java.lang.reflect.Method` từ **target class**; có thể dùng `AspectJExpressionPointcut.matches(method,targetClass)` để kiểm tra static eligibility và vẫn cần test lời gọi qua proxy.

## 9. Tình huống 7 — Pointcut bean(...), within(...) và tên bean

**Câu hỏi:** `@Around("bean(orderService)")` không chạy dù class tên `OrderService` và target được inject thành công.

**Trả lời:** `bean(...)` là **Spring-specific pointcut designator** match **Spring bean name**, không phải tên Java class. Nếu bean có ID `checkoutPort`, pointcut `bean(orderService)` không match dù object thuộc class `OrderService`.

~~~java
@Bean("checkoutPort")
OrderPort checkoutPort() {
    return new OrderService();
}
~~~

Lúc đó một pointcut như `bean(checkoutPort)` mới xét đúng bean ID. Khi dùng bean aliases/wildcards, kiểm tra tên thực trong context. `within(demo.service..*)` liên quan phạm vi **type**; không thay thế bằng `bean(...)` tùy tiện vì semantics khác nhau.

**Kiểm tra:** `context.getBeanDefinitionNames()`, `containsBean("checkoutPort")` và advisor pointcut expression. Một class có thể được đăng ký dưới nhiều IDs; hãy kiểm tra object caller dùng.

## 10. Tình huống 8 — @annotation không match annotation đặt ở nơi khác

**Câu hỏi:** Method có annotation nghiệp vụ trên interface nhưng Spring @Aspect dùng `@annotation(demo.Audited)` không chạy ở một cấu hình proxy.

**Trả lời:** Annotation placement, method resolution và proxy strategy có thể ảnh hưởng cách Spring tìm metadata. Không nên giả định **annotation chỉ gắn ở interface** luôn có cùng behavior với annotation đặt trên **concrete implementation method** trong mọi Spring integration mode.

Nếu cần dễ bảo trì với proxy-based Spring AOP, thường đặt annotation cần advise trên method thực tế của implementation và viết integration test với đúng JDK/CGLIB strategy, nhất là khi method có overloads/bridge methods.

~~~java
public interface OrderPort {
    void place(String id);
}

public class OrderService implements OrderPort {
    @Audited
    @Override
    public void place(String id) { /* ... */ }
}
~~~

`@Audited` ở đây là custom annotation phải có runtime retention và target method thích hợp. Không dùng test chỉ gọi annotation metadata trên interface để chứng minh Spring advisor đã match.

## 11. Tình huống 9 — Pointcut match tĩnh nhưng không match arguments runtime

**Câu hỏi:** `AopUtils.canApply(pointcut, Target.class)` trả true nhưng một invocation cụ thể không chạy advice.

**Trả lời:** `canApply` cho biết **eligibility ở mức class/method**, không khẳng định mọi runtime argument đều thỏa. Spring `Pointcut` có `ClassFilter` và `MethodMatcher`; một method matcher có thể yêu cầu đánh giá động với `isRuntime()`.

~~~java
MethodMatcher matcher = pointcut.getMethodMatcher();
boolean staticMatch = matcher.matches(method, targetClass);
boolean dynamicMatch = !matcher.isRuntime()
        || matcher.matches(method, targetClass, actualArgs);
~~~

Đoạn này là snippet với `pointcut`, `method`, `targetClass`, `actualArgs` đã khai báo, cần `org.springframework.aop.MethodMatcher`.

**Bằng chứng:** check đúng target class, method và **arguments caller thực truyền**, kể cả `null`, subtype và generic bridge methods. Một static true không đảm bảo dynamic true; ngược lại, nếu đã false ở static eligibility thì sửa đúng pointcut/target chứ không điều chỉnh advice body.

## 12. Tình huống 10 — Hai aspects cùng match nhưng before/after bị đảo

**Câu hỏi:** Có hai `@Around` aspects: `AuditAspect @Order(10)` và `QuotaAspect @Order(20)`. Order log dự kiến là gì?

**Trả lời:** Trong Spring, giá trị `@Order` **nhỏ hơn có precedence cao hơn**. Advice precedence cao chạy **trước khi vào** join point và chạy **sau cùng khi thoát ra**, theo kiểu bọc lồng nhau.

~~~text
Audit @Order(10) [outer] : before
  Quota @Order(20) [inner] : before
    Target.method()
  Quota @Order(20) [inner] : after
Audit @Order(10) [outer] : after
~~~

Ví dụ output:

~~~text
audit:before
quota:before
target
quota:after
audit:after
~~~

Nếu đảo order thành Audit 20, Quota 10, các lớp outer/inner cũng đảo. Với `@AfterThrowing` hoặc `catch` trong `@Around`, đường exception phải được xét riêng. Nếu cả hai aspects có **cùng precedence**, thứ tự giữa chúng không nên bị hard-code thành một contract ổn định; đặt `@Order` tường minh hoặc gộp logic khi yêu cầu ordering quan trọng.

**Không dùng `@Order` để sửa vấn đề advice không tồn tại**, thiếu Spring proxy hoặc pointcut không match. `@Order` chỉ sắp thứ tự các advisors thật sự được áp dụng.

## 13. Tình huống 11 — @After, @AfterReturning, @AfterThrowing nhầm nhau

**Câu hỏi:** `@AfterReturning` không chạy khi target ném exception. Đây có phải lỗi advisor ordering?

**Trả lời:** Không. `@AfterReturning` chỉ chạy khi join point **trả về bình thường**. `@AfterThrowing` chạy trên đường exception thoát khỏi join point mà advice quan sát. `@After` mang finally-style semantics, có thể chạy cả đường return và throw.

Một `@Around` có thể gom ba đường:

~~~java
@Around("execution(* demo.OrderService.submit(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    try {
        Object result = pjp.proceed();
        return result;
    } catch (Throwable ex) {
        throw ex;
    } finally {
        // Cleanup/observation chung cho normal return và throw.
    }
}
~~~

Đây là snippet minh họa, cần imports và `@Aspect` bean. Nếu một advice **phía trong** bắt exception và trả fallback thành công, advice **phía ngoài** có thể nhìn thấy normal return, không nhìn thấy exception gốc. Do đó khi nhiều advisors lồng nhau, thứ tự và exception handling cùng quyết định advice nào thấy lỗi.

**Bẫy khác:** Thứ tự các advice methods *cùng loại* trong cùng `@Aspect` class không nên được suy luận từ thứ tự xuất hiện trong source Java; Spring không đảm bảo reflection trả declaration order. Tách thành aspects có `@Order` rõ ràng khi cần hợp đồng ordering.

## 14. Tình huống 12 — @Around có before nhưng target không chạy

**Câu hỏi:** Console in `around:before` nhưng method nghiệp vụ hoàn toàn không chạy, không ném lỗi. Vì sao?

**Trả lời:** Một `@Around` có thể **không gọi `ProceedingJoinPoint.proceed()`**:

~~~java
@Around("execution(* demo.OrderService.get(..))")
public Object maybeSkip(ProceedingJoinPoint pjp) throws Throwable {
    return "cached-value"; // cố tình short-circuit target
}
~~~

Đoạn trên là snippet minh họa. Short-circuit có thể là hợp lệ (cache/security/policy) hoặc là bug do quên `proceed()`. Nếu advice khác phía ngoài gọi `proceed()` nhưng interceptor cache phía trong trả cache hit, target cũng có thể không chạy.

Để chẩn đoán, ghi events ở **advice trước/sau proceed**, **mỗi inner advisor quan trọng**, và **ngay trong target**. Có before nhưng không có target không nhất thiết là Spring proxy “bị lỗi”.

## 15. Tình huống 13 — Advice chạy hai lần nhưng target một lần

**Câu hỏi:** Mỗi lần gọi controller chỉ có một target log, nhưng audit `before` xuất hiện hai lần.

**Trả lời:** Kiểm tra **duplicate advisors** hoặc **nested proxies**:

- Một `ProxyFactory.addAdvice(audit)` bị gọi hai lần.
- `ProxyFactoryBean.interceptorNames` chứa advisor trùng.
- Class `@Aspect` vừa được `@ComponentScan` tạo bean, vừa được `@Bean` đăng ký với một ID khác.
- Manual `ProxyFactory` bọc một Spring bean vốn đã là AOP proxy.
- XML AOP advisor và @Aspect advisor khác cùng ghi một message.

Quan sát:

~~~java
if (bean instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}

Object inner = AopProxyUtils.getSingletonTarget(bean);
System.out.println("innerProxy="
        + (inner != null && AopUtils.isAopProxy(inner)));
~~~

Snippets cần `bean`, imports `Advised`, `AopProxyUtils`, `AopUtils`. Đừng unwrap dynamic/scoped TargetSource bừa bãi.

**Không kết luận hai `@Enable...` luôn tạo hai proxies**. Một số Spring auto-proxy creators được hợp nhất; quan sát số advisors/layers runtime mới có giá trị.

## 16. Tình huống 14 — Advice một lần, target nhiều lần

**Câu hỏi:** Audit ngoài cùng chạy một lần nhưng target gọi remote payment ba lần.

**Trả lời:** Nhiều khả năng có **retry advice bên trong** hoặc một `@Around` chủ động gọi `proceed()` nhiều lần. Spring Retry có thể áp dụng policy `maxAttempts=3`; một outer audit sẽ chỉ thấy một logical invocation, còn target có thể được gọi nhiều attempts.

~~~text
Audit (outer) -> Retry (inner) -> Transaction (inner) -> Target
       1               3                    3?          3
~~~

Dấu `3?` nhắc rằng mỗi attempt có thể vào transaction interceptor nhưng **không luôn tạo ba physical transactions riêng** nếu caller có outer transaction hoặc propagation khác. Không nhầm lượt interceptor với số transaction vật lý.

**Cách sửa** phụ thuộc contract: giới hạn retry budget, chọn advisor ordering đúng, không duplicate retry chains và bảo vệ side effects bằng idempotency key. Nếu đang chẩn đoán “advice chạy ít hơn target”, đừng chỉ tăng audit log hoặc xóa retry một cách mù quáng.

## 17. Tình huống 15 — Custom aspect với @Cacheable chạy sai thứ tự

**Câu hỏi:** Một service có `@Cacheable`. Custom audit chạy lần đầu nhưng **không chạy ở lần thứ hai** cùng cache key. Có phải pointcut sai?

**Trả lời:** Có thể hoàn toàn đúng nếu Spring **CacheInterceptor ở ngoài** custom audit:

~~~text
Cache (outer)
  Audit (inner)
    Target

Lần 1 cache miss -> audit + target
Lần 2 cache hit  -> CacheInterceptor trả dữ liệu,
                    audit và target không được gọi.
~~~

Nếu muốn audit mọi **caller request**, đặt custom audit ở ngoài cache:

~~~java
@Aspect
@Order(-100)
class AuditAspect {
    // @Around ... 
}
~~~

và lựa chọn cache advisor order thấp precedence hơn, ví dụ `@EnableCaching(order = Ordered.LOWEST_PRECEDENCE)`. Với lower numerical order là higher precedence, audit `-100` nằm ngoài cache `LOWEST_PRECEDENCE`.

**Kiểm chứng:** gọi hai lần cùng key, assert hai audit events nhưng chỉ một target execution khi audit ở ngoài; đảo advisor order và xác minh chỉ một audit event nếu cache ở ngoài. Đừng dùng `@Order` của một aspect để suy luận tất cả auto-configurations khác sẽ tự đổi.

## 18. Tình huống 16 — @Transactional hoạt động nhưng audit ở ngoài transaction

**Câu hỏi:** Custom `@Around` đo được lời gọi `@Transactional`, nhưng `TransactionSynchronizationManager.isActualTransactionActive()` trong phần before trả false.

**Trả lời:** Nếu transaction advisor ở **bên trong** custom audit, phần `before` của audit chạy **trước** transaction begin. Sau khi `pjp.proceed()` đi qua transaction interceptor, transaction có thể được begin/commit/rollback phía trong.

~~~text
Audit (outer)
  Transaction (inner)
    Target
~~~

Nếu muốn audit logic chạy **trong transaction**, đặt transaction advisor với order có precedence cao hơn audit, hoặc chuyển audit viết DB vào một phương án transaction-aware thích hợp. Nhưng **cẩn trọng**: audit ghi file/Kafka/HTTP bên ngoài DB transaction không được tự động rollback. Nếu caller đã có transaction trước đó, `isActualTransactionActive()` ở outer advice vẫn có thể true.

**Bằng chứng:** kiểm tra real transaction manager, propagation, trạng thái transaction tại entry/exit, DB commit/rollback; chỉ nhìn log order không đủ chứng minh atomicity.

## 19. Tình huống 17 — @Order đã có nhưng thứ tự log vẫn không như dự đoán

**Câu hỏi:** `@Order(1)` đã gắn lên AuditAspect, nhưng một `@Before` khác vẫn in trước hoặc một `@After` in sau.

**Trả lời:** Xem **toàn bộ advisors**, không chỉ hai custom aspects:

- Transaction/cache/security/retry advisors có `Ordered` hoặc order riêng.
- Một advisor được khai báo qua `ProxyFactoryBean.interceptorNames` có vị trí cụ thể trong factory.
- Aspect có nhiều advice types trong cùng class; semantics before/after/throw/finally khác nhau.
- Có nested proxies; `@Order` chỉ sắp các advisors trong một chain thích hợp, **không sắp toàn bộ proxy layers xuyên nhau**.
- `@Order` có thể đặt sai object/annotation hoặc aspect đang được đăng ký nhiều lần.
- Hai aspects có cùng order → không nên dựa vào declaration order trong Java source.

**Cách xác minh:** `((Advised) bean).getAdvisors()`, `AopProxyUtils.getSingletonTarget` với static target source, ghi before/after trong mỗi advice và target rồi đối chiếu call tree. Nếu lỗi xuất hiện sau khi thêm `@EnableCaching` hoặc transaction config, hãy ghi cả advisor đó vào sơ đồ trước khi đổi order.

## 20. Tình huống 18 — Bean có proxy nhưng method không có advice

**Câu hỏi:** `isAopProxy=true` nhưng `service.status()` không in audit, trong khi `service.submit()` lại có audit.

**Trả lời:** Một proxy có thể tồn tại vì **một method khác** match advisor. Pointcut `execution(* demo.OrderService.submit(..))` không có nghĩa `status()` cũng được advice. Đây là negative-control behavior nên có trong test.

Nếu cần advice cả hai, đổi pointcut một cách có chủ đích hoặc dùng named pointcut phù hợp; không mở rộng thành `execution(* *(..))` quá rộng khi dễ chặn methods không liên quan. Method non-match chạy trực tiếp qua proxy delegation xuống target mà không vào custom advice.

## 21. Tình huống 19 — Spring bean bị tạo quá sớm hoặc nằm trong context khác

**Câu hỏi:** Ở test context advice chạy, nhưng ở application thật thì không. Các annotations hoàn toàn giống nhau.

**Trả lời:** Kiểm tra **context ownership và bean lifecycle**. Một bean có thể được khởi tạo trước khi auto-proxy `BeanPostProcessor` đầy đủ được đăng ký, có thể có cảnh báo của Spring về *not eligible for getting processed by all BeanPostProcessors*. Hoặc custom aspect được đăng ký ở child web context trong khi service bean nằm tại root parent context; auto-proxy processor của child không mặc định đi ngược để xử lý lại bean parent.

**Cách điều tra:** xác định ApplicationContext nơi target được **tạo**, AOP config thuộc context nào, singleton initialization timing, BeanPostProcessors và reference được inject. Với test, chỉ dùng một `AnnotationConfigApplicationContext` nhỏ giúp cô lập lỗi.

## 22. Tình huống 20 — @MockitoBean khiến aspect “biến mất”

**Câu hỏi:** Với Spring Framework 6.2+ TestContext, thêm `@MockitoBean` cho `OrderPort`, rồi kiểm tra advice `@Around` trên OrderService. Không thấy advice.

**Trả lời:** `@MockitoBean` dùng bean override để **thay bean gốc bằng Mockito mock**. Khi mock **chính target bean** đang cần kiểm thử AOP, bạn không còn gọi original Spring proxy/advised target đó. Nếu mục tiêu là test aspect, **mock collaborator** của service (repository/gateway), còn service chính để Spring tạo proxy. Nếu cần quan sát implementation thật, cân nhắc `@MockitoSpyBean`, nhưng Spring AOP proxy có thể bao spy target; kiểm tra rõ layer đang stub/verify.

Đừng sử dụng kết quả `Mockito.verify(mock)` làm bằng chứng `@Aspect` đã chạy. Một Mockito mock có thể trả stubbed result hoàn toàn không đi qua custom Spring advice.

## 23. Tình huống 21 — @Async hoặc reactive return khiến thời điểm advice khác kỳ vọng

**Câu hỏi:** `@Around` ghi `after` trước khi tác vụ async hoàn tất; developer tưởng advice chạy sai thứ tự.

**Trả lời:** Với `@Async` và một proxy method trả `CompletableFuture`/Future, advice có thể đo **thời điểm method trả future**, không tự động đợi asynchronous computation kết thúc. Tùy advisor ordering, custom `@Around` có thể chạy trên caller thread hay executor thread; phải quan sát thiết kế/ordering thực tế.

Tương tự, method trả `Publisher` trong reactive stack có thể **trả đối tượng publisher trước khi có subscription**. Một synchronous `@Around` quanh việc trả Publisher không đương nhiên bao cả thời gian xử lý downstream; muốn đo execution thật phải dùng reactive operators phù hợp và truyền context.

**Kiểm chứng:** assert completion bằng `join()`/test utilities, quan sát event timestamps/threads và xác định advice đang đo proxy method invocation, future completion hay subscription lifecycle. Không khẳng định mọi Spring async/reactive APIs có cùng semantics qua các versions.

## 24. Tình huống 22 — Pointcut dùng call(...) hoặc field access nhưng Spring AOP không chạy

**Câu hỏi:** Viết `@Before("call(* demo.Service.pay(..))")` hoặc pointcut cho field write, nhưng Spring báo lỗi expression hoặc không có advice.

**Trả lời:** Spring **proxy-based AOP** hỗ trợ chủ yếu **method execution join points**. Nó hỗ trợ các pointcut designators phù hợp như `execution`, `within`, `this`, `target`, `args`, `@annotation`, `@within`, `@target`, `bean(...)` (Spring-specific), nhưng **không hỗ trợ** tất cả `call`, `get`, `set`, `cflow` của AspectJ weaving.

Nếu yêu cầu thật sự là constructor, field access hoặc call sites, đó là cơ chế **AspectJ bytecode weaving**, không giải quyết chỉ bằng `@EnableAspectJAutoProxy`. Trong phạm vi Spring proxy AOP, viết pointcut theo method execution trên Spring beans.

## 25. Ví dụ chạy được: hai Spring aspects có @Order và các negative controls

Ví dụ Java 17 + Maven dưới đây dùng **Spring Framework 6.1.15**, AspectJ Weaver 1.9.22.1 và JUnit Jupiter. Mục tiêu là thấy bằng chứng đầy đủ về:

1. Hai `@Around` aspects có order xác định: `AuditAspect @Order(10)` bao ngoài `QuotaAspect @Order(20)`.
2. Pointcut chỉ match `TransferService.transfer(..)`, không match `status()`.
3. `selfInvocation(...)` gọi `this.transfer(...)` trên target nên không có aspect events.
4. Exception từ target đi ra theo đường `quota:error`, rồi `audit:error`.
5. Context đối chứng có aspect beans nhưng **không bật auto-proxy**, nên service không được advise.

### 25.1. Cấu trúc dự án

~~~text
spring-aop-advice-scenarios/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── TransferPort.java
    │   ├── Events.java
    │   ├── TransferService.java
    │   ├── AuditAspect.java
    │   ├── QuotaAspect.java
    │   ├── AppConfig.java
    │   └── Demo.java
    └── test/java/demo/
        └── AdviceScenariosTest.java
~~~

### 25.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-advice-scenarios</artifactId>
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
                <configuration>
                    <release>17</release>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>demo.Demo</mainClass>
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

`spring-context` đăng ký `AnnotationConfigApplicationContext`/`@Bean`; `spring-aop` có proxy infrastructure và `AopUtils`; `aspectjweaver` cung cấp parser/matcher cho Spring `@AspectJ` expressions. **Không** thêm AspectJ Java agent: đây là Spring proxy mode.

### 25.3. Interface — src/main/java/demo/TransferPort.java

~~~java
package demo;

public interface TransferPort {
    String transfer(String id);
    String status();
    String selfInvocation(String id);
}
~~~

Interface là contract cho Spring JDK dynamic proxy; caller không cần cast proxy sang `TransferService`.

### 25.4. Recorder — src/main/java/demo/Events.java

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public class Events {
    private final List<String> items = new ArrayList<>();

    public void add(String event) {
        items.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(items);
    }
}
~~~

Đây là recorder đơn luồng dùng cho chương trình mẫu và từng test context riêng. Nếu test concurrent/async, thay bằng collection thread-safe hoặc đồng bộ rõ ràng.

### 25.5. Target — src/main/java/demo/TransferService.java

~~~java
package demo;

public class TransferService implements TransferPort {
    private final Events events;

    public TransferService(Events events) {
        this.events = events;
    }

    @Override
    public String transfer(String id) {
        events.add("target:transfer:" + id);
        if ("BAD".equals(id)) {
            throw new IllegalArgumentException("blocked:" + id);
        }
        return "ok:" + id;
    }

    @Override
    public String status() {
        events.add("target:status");
        return "READY";
    }

    @Override
    public String selfInvocation(String id) {
        events.add("target:self");
        return this.transfer(id);  // bypass Spring proxy advice.
    }
}
~~~

`selfInvocation()` được gọi **qua proxy** nhưng pointcut không match method này; khi target gọi `this.transfer()`, invocation **không quay lại proxy**, do đó không có audit/quota around events cho lần gọi nội bộ.

### 25.6. AuditAspect — src/main/java/demo/AuditAspect.java

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(10)
public class AuditAspect {
    private final Events events;

    public AuditAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.TransferService.transfer(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        String id = String.valueOf(pjp.getArgs()[0]);
        events.add("audit:before:" + id);

        try {
            Object result = pjp.proceed();
            events.add("audit:after:" + id);
            return result;
        } catch (Throwable error) {
            events.add("audit:error:" + id);
            throw error;
        }
    }
}
~~~

`@Order(10)` nghĩa AuditAspect có precedence cao hơn QuotaAspect ở ví dụ; audit bao ngoài chain, không phải “chạy xong hoàn toàn trước” quota: các after events xuất hiện theo thứ tự ngược.

### 25.7. QuotaAspect — src/main/java/demo/QuotaAspect.java

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(20)
public class QuotaAspect {
    private final Events events;

    public QuotaAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.TransferService.transfer(..))")
    public Object quota(ProceedingJoinPoint pjp) throws Throwable {
        String id = String.valueOf(pjp.getArgs()[0]);
        events.add("quota:before:" + id);

        try {
            Object result = pjp.proceed();
            events.add("quota:after:" + id);
            return result;
        } catch (Throwable error) {
            events.add("quota:error:" + id);
            throw error;
        }
    }
}
~~~

Để quan sát ordering, cả hai `@Around` đều thật sự gọi `proceed()`; nếu QuotaAspect bỏ `proceed()`, target sẽ không chạy và AuditAspect vẫn có thể ghi after với giá trị trả từ QuotaAspect.

### 25.8. Spring config — src/main/java/demo/AppConfig.java

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class AppConfig {

    @Bean
    Events events() {
        return new Events();
    }

    @Bean
    TransferPort transferPort(Events events) {
        return new TransferService(events);
    }

    @Bean
    AuditAspect auditAspect(Events events) {
        return new AuditAspect(events);
    }

    @Bean
    QuotaAspect quotaAspect(Events events) {
        return new QuotaAspect(events);
    }
}
~~~

Config chỉ đăng ký **bốn application beans**: recorder, target, hai aspects. Aspect classes **không cần `@Component`** vì đã có `@Bean`. Spring tự đăng ký infrastructure tạo advisor và proxy; tránh component scan toàn package hoặc tạo `ProxyFactory` thủ công khiến khó phân biệt trùng.

### 25.9. Main — src/main/java/demo/Demo.java

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class Demo {
    public static void main(String[] args) {
        try (var context =
                 new AnnotationConfigApplicationContext(AppConfig.class)) {
            TransferPort service = context.getBean(TransferPort.class);
            Events events = context.getBean(Events.class);

            System.out.println("aop=" + AopUtils.isAopProxy(service));
            System.out.println("jdk=" + AopUtils.isJdkDynamicProxy(service));

            System.out.println("transfer=" + service.transfer("A"));
            System.out.println("status=" + service.status());
            System.out.println("self=" + service.selfInvocation("B"));

            try {
                service.transfer("BAD");
            } catch (IllegalArgumentException ex) {
                System.out.println("caught=" + ex.getMessage());
            }

            System.out.println("events="
                    + String.join("|", events.snapshot()));
        }
    }
}
~~~

Đây là main chạy trên **Spring bean proxy**. Quan sát **`AopUtils` + recorder events**, không phải proxy generated class name.

### 25.10. Chạy và expected output

~~~bash
mvn -q compile exec:java
~~~

Các dòng do ứng dụng in dự kiến:

~~~text
aop=true
jdk=true
transfer=ok:A
status=READY
self=ok:B
caught=blocked:BAD
events=audit:before:A|quota:before:A|target:transfer:A|quota:after:A|audit:after:A|target:status|target:self|target:transfer:B|audit:before:BAD|quota:before:BAD|target:transfer:BAD|quota:error:BAD|audit:error:BAD
~~~

Giải thích:

- `transfer("A")`: `@Order(10)` audit ngoài, `@Order(20)` quota trong, target ở cuối; exit order đảo lại.
- `status()`: pointcut `transfer(..)` không match nên chỉ có `target:status`.
- `selfInvocation("B")`: entrypoint selfInvocation không match; target gọi `this.transfer()` nên chỉ có `target:self|target:transfer:B`.
- `transfer("BAD")`: cả hai advice vào trước target, target ném exception; `quota:error` in trước `audit:error`, caller nhận `IllegalArgumentException`.

Maven/Spring có thể in thêm thông báo ngoài các dòng ứng dụng; output nêu trên là **kỳ vọng do source Java tự in**, không phải log Maven đã được xác nhận chạy trên một máy cụ thể.

## 26. JUnit 5: biến tình huống thành regression tests

**src/test/java/demo/AdviceScenariosTest.java**

~~~java
package demo;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

class AdviceScenariosTest {

    // Có aspect beans, nhưng KHÔNG có @EnableAspectJAutoProxy.
    @Configuration(proxyBeanMethods = false)
    static class WithoutProxyConfig {
        @Bean
        Events events() {
            return new Events();
        }

        @Bean
        TransferPort transferPort(Events events) {
            return new TransferService(events);
        }

        @Bean
        AuditAspect auditAspect(Events events) {
            return new AuditAspect(events);
        }

        @Bean
        QuotaAspect quotaAspect(Events events) {
            return new QuotaAspect(events);
        }
    }

    @Test
    void adviceNestingFollowsExplicitOrder() {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            TransferPort port = context.getBean(TransferPort.class);
            Events events = context.getBean(Events.class);

            assertTrue(AopUtils.isAopProxy(port));
            assertTrue(AopUtils.isJdkDynamicProxy(port));
            assertEquals("ok:A", port.transfer("A"));
            assertEquals(
                List.of("audit:before:A", "quota:before:A",
                        "target:transfer:A", "quota:after:A",
                        "audit:after:A"),
                events.snapshot()
            );
        }
    }

    @Test
    void nonmatchingMethodRunsWithoutEitherAdvice() {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            TransferPort port = context.getBean(TransferPort.class);
            Events events = context.getBean(Events.class);

            assertEquals("READY", port.status());
            assertEquals(List.of("target:status"), events.snapshot());
        }
    }

    @Test
    void selfInvocationBypassesTransferAdvice() {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            TransferPort port = context.getBean(TransferPort.class);
            Events events = context.getBean(Events.class);

            assertEquals("ok:B", port.selfInvocation("B"));
            assertEquals(
                List.of("target:self", "target:transfer:B"),
                events.snapshot()
            );
        }
    }

    @Test
    void exceptionUnwindsInnerThenOuterAdvice() {
        try (var context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {
            TransferPort port = context.getBean(TransferPort.class);
            Events events = context.getBean(Events.class);

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> port.transfer("BAD")
            );
            assertEquals("blocked:BAD", ex.getMessage());
            assertEquals(
                List.of("audit:before:BAD", "quota:before:BAD",
                        "target:transfer:BAD", "quota:error:BAD",
                        "audit:error:BAD"),
                events.snapshot()
            );
        }
    }

    @Test
    void aspectBeansWithoutAutoProxyDoNotApplyAdvice() {
        try (var context =
                new AnnotationConfigApplicationContext(
                        WithoutProxyConfig.class)) {
            TransferPort port = context.getBean(TransferPort.class);
            Events events = context.getBean(Events.class);

            assertFalse(AopUtils.isAopProxy(port));
            assertEquals("ok:RAW", port.transfer("RAW"));
            assertEquals(
                List.of("target:transfer:RAW"),
                events.snapshot()
            );
        }
    }
}
~~~

Mỗi test tạo/đóng context riêng nên recorder không mang state từ lần test khác. Negative control `WithoutProxyConfig` giúp chứng minh **có @Aspect bean chưa đủ** để advice chạy. Các tests còn kiểm tra normal return, non-match, self-invocation và exception ordering.

Chạy:

~~~bash
mvn -q test
~~~

Theo mã nguồn, báo cáo Surefire dự kiến:

~~~text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
~~~

Đây là **kỳ vọng logic khi compile/test thành công**, không phải kết quả Maven thực thi đã đo. File `target/surefire-reports/demo.AdviceScenariosTest.txt` sẽ chứa report trên môi trường chạy.

## 27. Câu hỏi nâng cao để tự ôn tập

### Q1. `isAopProxy=true` có chứng minh custom aspect đã chạy?

**Không.** Bean có thể được proxy do caching/transaction/security advisors khác, trong khi custom aspect bean chưa đăng ký hoặc pointcut của nó không match. Phải inspect advisor cần tìm và gọi method qua proxy để assert behavior thật.

### Q2. `@Order(0)` có bắt buộc audit luôn chạy trước transaction, cache và retry?

**Không.** Nó cho aspect một precedence tương đối trong advisor chain thích hợp. Cần xét order của **mọi advisors**, nested proxies, context configuration và outer transaction/caller. Một advisor khác có order thấp hơn vẫn có thể ở ngoài audit; cache hit hoặc security denial có thể short-circuit inner advice.

### Q3. `@Before` chạy nhưng `@AfterReturning` không chạy trên exception là bug?

**Không.** `@AfterReturning` không xử lý exception path. Kiểm tra `@AfterThrowing`, `@After` hoặc `@Around` `finally` phù hợp. Quan sát thứ tự khi nhiều aspects được lồng.

### Q4. Thêm `proxyTargetClass=true` sửa được mọi advice không chạy?

**Không.** Nó đổi sang class-based CGLIB proxy, không đăng ký missing aspect, không sửa pointcut, không biến raw object thành bean và không giải quyết self-invocation. Với final class/method còn có thể làm proxying thất bại.

### Q5. Có `@EnableAspectJAutoProxy` và `@EnableTransactionManagement` đồng nghĩa có hai Spring proxies?

**Không mặc định.** Spring có thể phối hợp/hợp nhất AOP infrastructure để một proxy có nhiều advisors. Tuy nhiên manual `ProxyFactory`, `ProxyFactoryBean` hoặc custom BeanPostProcessors có thể tạo nested proxies thật. Inspect `Advised` và static singleton target layer khi cần.

### Q6. Vì sao log `after` xuất hiện trong custom audit nhưng database cuối cùng rollback?

Nếu audit nằm **bên trong** transaction advisor, log after có thể chạy trước transaction commit cuối cùng; nếu có lỗi ở commit phase, audit đã in trước khi rollback. Nếu audit ngoài transaction, behavior khác. Chỉ nên xác minh transaction bằng DB state/transaction manager, không bằng log một mình.

### Q7. Vì sao `@Cacheable` có hiệu lực nhưng audit không xuất hiện trên cache hit?

Nếu cache advisor **bên ngoài** custom audit, hit trả kết quả trước khi chain vào audit. Nếu mục tiêu audit mọi incoming calls, thiết lập custom order nằm ngoài caching advisor và viết hit/miss regression test.

### Q8. Nếu `@Around` gọi `proceed()` hai lần thì advice có chạy hai lần?

Một `@Around` có thể vào **một lần** rồi chủ động gọi `proceed()` hai lần; downstream advisors/target có thể vào hai lần. Không suy luận số lần target từ số lần advice ngoài cùng. Nếu cần retry, dùng chính sách bounded/idempotent và test attempts.

### Q9. Có thể test pointcut mà không khởi tạo Spring context không?

Có thể kiểm tra tĩnh bằng `AspectJExpressionPointcut.matches(method,targetClass)` hoặc API pointcut thích hợp, nhưng nó **không chứng minh** Spring aspect bean được đăng ký, proxy đã tạo, caller dùng đúng bean và runtime arguments match. Với mục tiêu Spring advice, cần ít nhất một integration test nhỏ qua ApplicationContext.

### Q10. Pointcut `execution(* *(..))` có an toàn để “bắt hết rồi lọc trong advice”?

Không phải lựa chọn tốt mặc định. Nó có thể làm Spring proxy nhiều beans/methods không liên quan, dẫn tới overhead, hành vi mới ở boundary khác, lỗi class-based proxy cho final classes hoặc interactions khó kiểm soát. Viết pointcut cụ thể theo package, bean, annotation/method và có negative tests.

### Q11. Spring AOP có intercept được `new Service()`, constructor, field set hoặc method call site không?

Spring proxy-based AOP không intercept general constructor/field/call join points. Nếu thực sự cần các join points đó thì phải xét **AspectJ weaving integration** với Spring và deployment/weaver requirements tương ứng — đây là cơ chế khác với auto-proxy.

### Q12. Nếu `@Aspect` được scan hai lần, tại sao advice in trùng?

Phải xác định có thật **hai aspect beans/advisors** hay không. `@Configuration` imports trùng tên có thể được Spring xử lý khác với hai distinct `@Bean` names. Đừng nhìn số annotation occurrences rồi mặc định trùng; hãy kiểm tra bean names, advisor lists, số invocations và proxy layers.

## 28. Bài tập biến đổi để luyện chẩn đoán

Từ project mục 25, thử từng thay đổi **một lần một biến**, dự đoán events rồi chạy lại:

| Biến đổi chủ đích | Dự đoán | Bằng chứng cần kiểm tra |
| --- | --- | --- |
| Bỏ `@EnableAspectJAutoProxy` | Không có Spring AOP proxy do hai aspects trong context riêng | `AopUtils.isAopProxy=false`, chỉ target event |
| Bỏ `@Bean AuditAspect` | Chỉ QuotaAspect vào method `transfer` | Không có `audit:before/after` |
| Đổi `@Order(10)` thành `@Order(30)` trên Audit | Quota ở ngoài Audit | Thứ tự before/after đảo layer |
| Đổi pointcut Audit thành `execution(* demo.TransferService.status(..))` | Audit ở status, không ở transfer | Hai paths negative/positive |
| Gọi `new TransferService(new Events()).transfer("A")` | Chỉ raw target event | Không có proxy/advice |
| Bỏ `pjp.proceed()` trong Quota và trả fallback | Audit before/after vẫn có thể chạy, target vắng | Target counter 0 |
| Gọi `selfInvocation("B")` | Self-call transfer không qua advice | `target:self` và `target:transfer:B` |
| Thêm một QuotaAspect bean khác cùng pointcut | Có thể thêm một advisor tương tự | Advisor list và quota log thêm lượt |
| Thêm cache advisor ở ngoài Audit rồi gọi cùng key hai lần | Cache hit có thể bypass Audit | Audit count ít hơn caller count |
| Thêm retry advisor quanh target có hai lỗi tạm thời | Một proxy call có thể vào target nhiều lần | Attempts và side effects |
| Đổi thành CGLIB nhưng target method final | Method final không advice được | `isCglibProxy`, modifiers và events |
| Thay target bằng `@MockitoBean` trong Spring Test | Bare mock có thể không mang original aspect advice | Context bean override, proxy identity |

Khi bài luyện không cho kết quả dự đoán, hãy **quay về chặng sớm nhất** chưa có bằng chứng: bean → proxy → advisor/pointcut → advice → target. Không thay nhiều cấu hình cùng lúc vì sẽ mất khả năng phân biệt nguyên nhân.

## 29. Checklist chẩn đoán thực tế

- [ ] Tôi biết đúng `ApplicationContext`, target bean ID và reference caller dùng.
- [ ] `@Aspect` class đã là Spring bean, không chỉ có annotation.
- [ ] Auto-proxy infrastructure đã được bật trong context tạo target.
- [ ] `AopUtils.isAopProxy` kiểm tra **object thực**, không phải object `new` khác.
- [ ] Tôi biết proxy là JDK hay CGLIB và method có thể được proxy không.
- [ ] Pointcut match đúng target class/method/arguments thực tế.
- [ ] `bean(...)` tham chiếu **Spring bean name** chứ không phải Java class name.
- [ ] `@annotation` dùng annotation placement nhất quán và đã được integration test.
- [ ] Advisor có mặt trên proxy, nếu proxy expose `Advised`.
- [ ] Tôi đã phân biệt static matching và dynamic runtime matcher.
- [ ] Tôi biết `@Order` của tất cả advisors liên quan và hiểu outer/inner ordering.
- [ ] Tôi không nhầm `@AfterReturning` với `@AfterThrowing` hoặc finally-style `@After`.
- [ ] `@Around` dùng `proceed()` đúng số lần theo contract, rethrow exception và trả result hợp lệ.
- [ ] Có counter/sự kiện độc lập trong target để phân biệt method execution với advice invocation.
- [ ] Tôi đã loại trừ self-invocation, raw-target bypass và nested proxies ngoài ý định.
- [ ] Tôi đã kiểm tra cache hit, retry attempts, transaction boundary và async/reactive timing nếu liên quan.
- [ ] Không có aspect bean/Advisor registration trùng ngoài ý định.
- [ ] Test không mock chính bean cần kiểm thử AOP rồi suy ra advice vẫn hoạt động.
- [ ] Có regression tests cho method match/non-match, normal return, throw, self-invocation và ordering.

## 30. Kết luận và tài liệu Spring

Khi **Spring advice không chạy**, hãy bắt đầu ở bean registration và proxy reference, sau đó mới tới pointcut/advisor eligibility. Khi **advice chạy sai thứ tự**, đừng chỉ nhìn `@Order` một mình: phải mô tả **nested invocation chain**, các advisors của Spring transaction/cache/retry/security, và vị trí các `@Around` có thể short-circuit hoặc gọi `proceed()` nhiều lần.

Mọi tình huống trong bài quy về câu hỏi: **Ai tạo bean? Caller đi qua proxy nào? Advisor nào match? Advice nào được vào? Có `proceed()` không? Target chạy bao nhiêu lần?** Việc ghi events và kiểm thử với một Spring context tối thiểu có giá trị hơn suy đoán từ generated proxy class names hay chỉ đọc annotations.

### Nguồn tham khảo chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html).
- [Spring Framework — Declaring Pointcuts](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/pointcuts.html).
- [Spring Framework — Declaring Advice, Advice Ordering](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/advice.html).
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/ataspectj/aspectj-support.html).
- [Spring Framework — Programmatic ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html).
- [Spring Framework — Pointcut API](https://docs.spring.io/spring-framework/reference/core/aop-api/pointcuts.html).
- [Spring Framework — Caching Annotations](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html).
- [Spring Framework — Declarative Transaction Implementation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-review-007">Bài tập lựa chọn Spring AOP, cơ chế Spring khác hoặc tích hợp AspectJ</a>

<details>
<summary>Xem chi tiết</summary>

# Bài tập lựa chọn Spring AOP, cơ chế Spring khác hoặc tích hợp AspectJ

## 1. Mục tiêu của bài ôn tập

Khi gặp yêu cầu “cần làm một việc trước/sau một thao tác”, nhiều người lập tức viết `@Aspect` và `@Around`. Nhưng trong ứng dụng Spring, đó không phải lúc nào cũng là lựa chọn đúng: Spring đã có **transaction management, caching, async execution, validation, event listeners, Spring MVC HTTP interceptors, servlet filters, method/HTTP security** cùng nhiều cơ chế chuyên biệt. Ở chiều ngược lại, **Spring AOP proxy** cũng có giới hạn rõ ràng: nó không tự chặn mọi object `new`, self-invocation, constructor execution hay field access. Khi cần đi xa hơn giới hạn ấy, phải đánh giá **AspectJ weaving tích hợp Spring**, hoặc refactor để tránh phải weave.

Bài này là **bộ bài tập ra quyết định trong phạm vi Spring Aspect**. Người học sẽ chọn một trong ba nhóm:

- **A — Spring AOP proxy với custom aspect:** Spring-managed bean, method-execution join point, custom cross-cutting concern chưa có Spring abstraction thích hợp.
- **S — Cơ chế Spring có sẵn, ưu tiên không tự viết aspect:** `@Transactional`, `@Cacheable`, `@Async`, `@Validated`, `@EventListener`, `@ControllerAdvice`, MVC `HandlerInterceptor`, servlet `Filter`, Spring Security, v.v. Một số cơ chế này **vẫn dùng Spring AOP ở bên dưới**, nhưng ta nên chọn abstraction chuyên biệt thay vì tự viết lại interceptor.
- **J — AspectJ weaving được Spring tích hợp:** cần join points ngoài method calls qua Spring proxy, object ngoài container, self-invocation không thể refactor, field/constructor join points hoặc Spring-provided AspectJ aspects. Đòi cấu hình **compile-time weaving (CTW)** hoặc **load-time weaving (LTW)**, dependencies và classloading phù hợp.

**Đặc biệt:** đây không phải ba khái niệm luôn loại trừ nhau. `@Transactional` là cơ chế Spring chuyên biệt nhưng **mặc định chạy bằng AOP proxy**; AspectJ mode của chính transaction/caching infrastructure có thể dùng weaving; custom `@Aspect` cũng có thể chạy song song với cache/transaction advisors. Trong bài thi, “chọn S” nghĩa là **chọn API chuyên biệt của Spring**, không phải khẳng định nó tuyệt đối không dùng AOP.

## 2. Năm câu hỏi trước khi chọn công cụ

Hãy hỏi theo đúng thứ tự:

1. **Đã có Spring abstraction giải quyết đúng yêu cầu chưa?** Cache kết quả → `@Cacheable`; transaction → `@Transactional`; kiểm soát truy cập HTTP → Spring Security filter chain; xử lý REST exception → `@RestControllerAdvice`; sự kiện nghiệp vụ → `ApplicationEventPublisher`/`@EventListener`.
2. **Đối tượng nào đang được xử lý?** Spring-managed bean, HTTP request/response, event, scheduled task, hay domain object được code ngoài Spring tạo bằng `new`?
3. **Cần chặn join point nào?** Method execution đi qua Spring proxy, hay constructor/field access/call site? Spring AOP proxy chỉ chặn method execution trên eligible Spring beans.
4. **Caller có đi qua proxy không?** External invocation qua bean proxy hay `this.method()`, private/final method, raw target? Một Spring annotation trên code không tự sửa proxy boundary.
5. **Có đáng trả chi phí weaving không?** Nếu có thể refactor thành Spring-managed collaborator/factory, dùng event/filter/Spring abstraction, hãy cân nhắc phương án đơn giản trước khi thêm AspectJ compiler/agent, weaving rules, module/classloader testing.

### Quy tắc chọn nhanh

~~~text
Yêu cầu mới
   |
   +-- Đã có Spring feature chuyên biệt phù hợp?
   |       |
   |       +-- CÓ --> S: Dùng @Transactional/@Cacheable/@Async,
   |                    @EventListener, MVC/Filter/Security/Validation...
   |                    Vẫn kiểm tra proxy boundary nếu feature dùng AOP.
   |
   +-- KHÔNG --> Chỉ cần custom advice trên Spring bean method
   |               và mọi caller đi qua bean proxy?
   |       |
   |       +-- CÓ --> A: Custom @Aspect + @EnableAspectJAutoProxy,
   |                    bean registration, pointcut, integration test.
   |
   +-- KHÔNG --> Có thể đổi thiết kế để lời gọi đi qua
   |               một Spring bean khác / injected factory / event?
   |       |
   |       +-- CÓ --> S/A với refactor, tránh weaving nếu được.
   |
   +-- KHÔNG --> Cần đối tượng ngoài container, constructor,
                   field join points hoặc self-call thực sự?
           |
           +-- CÓ --> J: Spring + AspectJ CTW/LTW, xem
                        spring-aspects, @Configurable, mode=ASPECTJ
                        theo use case; kiểm thử bytecode/classloader.
~~~

## 3. So sánh kỹ thuật: ranh giới của từng phương án

| Tiêu chí | A — custom Spring AOP | S — cơ chế Spring chuyên biệt | J — AspectJ tích hợp Spring |
| --- | --- | --- | --- |
| Đối tượng áp dụng chính | Spring-managed bean/proxy | Tùy module: bean, web request, event, transaction, validation... | Woven classes, kể cả object ngoài IoC khi cấu hình phù hợp |
| Điểm chặn chính | Method **execution qua proxy** | Theo contract của feature; có feature nội bộ dùng AOP proxy | AspectJ woven join points: method/constructor/field/call... theo aspect |
| Dependency/điều kiện | Spring AOP, aspect là bean, `@EnableAspectJAutoProxy`, pointcut match | Bật module/config cần thiết, ví dụ cache manager/transaction manager/SecurityFilterChain | AspectJ compiler/weaver, CTW/LTW và aspect tương ứng; Spring wiring khi cần |
| Self-invocation | Không intercept thêm lời gọi nội bộ | Nếu dùng proxy mode: thường cùng hạn chế | Bytecode weaving có thể chặn self-invocation phù hợp |
| Object `new` ngoài container | Không được auto-proxy | Có thể xử lý bằng API/factory rõ ràng; tùy module | Có thể weave class; Spring `@Configurable` có cơ chế tiêm qua aspect |
| HTTP request trước DispatcherServlet | Không phải ranh giới tối ưu | Servlet Filter/Spring Security filter chain | Thường không cần |
| Transaction/cache/async | Không nên tự viết lại hạ tầng | `@Transactional`, `@Cacheable`, `@Async` | Chỉ chọn AspectJ mode khi có lý do thực và hạ tầng weaving |
| Độ phức tạp triển khai | Trung bình, nhất là ordering và pointcut | Thấp hơn khi feature đúng vấn đề | Cao hơn do weaving, build, classloader, diagnostics |
| Độ an toàn khi thay framework | Phụ thuộc pointcut/proxy semantics | Dựa trên contract Spring module | Cần kiểm chứng bytecode, weaving agent, Spring/AspectJ version compatibility |

**Phân biệt then chốt:** `@Aspect` là kiểu **khai báo advice**, không mặc định đồng nghĩa với bytecode weaving. Khi Spring xử lý `@Aspect` qua `@EnableAspectJAutoProxy`, nó tạo **Spring proxy**. Để cùng một `@AspectJ` style aspect chạy qua AspectJ weaving cần cấu hình AspectJ compiler/weaver riêng, không phải chỉ thêm thư viện `aspectjweaver` vào Maven.

## 4. Các cơ chế Spring thường nên dùng trước khi tự viết aspect

| Yêu cầu | Lựa chọn Spring ưu tiên | Vì sao không tự viết @Around mặc định? |
| --- | --- | --- |
| Quản lý transaction, rollback, propagation | `@Transactional`, `@EnableTransactionManagement`, `PlatformTransactionManager` | Spring đã xử lý transaction resource, lifecycle, rollback, propagation |
| Cache method result / invalidation | `@Cacheable`, `@CacheEvict`, `@CachePut`, `CacheManager` | Key, cache abstraction, eviction, error handling đã có |
| Chạy công việc bất đồng bộ | `@Async`, `@EnableAsync`, executor | Threading/exception/future contract cần quản lý chuyên biệt |
| Scheduling | `@Scheduled`, `@EnableScheduling` | Đây là kích hoạt công việc theo lịch, không phải advice quanh một invocation sẵn có |
| Publish/consume application events | `ApplicationEventPublisher`, `@EventListener`, `@TransactionalEventListener` | Decoupling và phase semantics của event khác method interception |
| Validation request DTO/beans | Jakarta Bean Validation, `@Valid`, `@Validated` | Không nên tự phản chiếu fields bằng aspect; Spring MVC/validation đã có |
| HTTP request rộng, trước/ngoài MVC | Servlet `Filter` / `OncePerRequestFilter` | HTTP chain boundary khác service method execution |
| Spring MVC handler-specific metrics/context | `HandlerInterceptor` | Biết handler mapping/routing, không cần match controller methods bằng pointcut |
| HTTP authentication/authorization | Spring Security `SecurityFilterChain`, method security khi đúng cấp | Security cần consistent filter/method security model, không nên custom aspect tự làm tất cả |
| REST exception mapping | `@ControllerAdvice`, `@ExceptionHandler`, `ResponseEntityExceptionHandler` | Xử lý HTTP status/body ở MVC exception pipeline |
| Cần thay đổi cách khởi tạo Spring beans | Bean definitions, factory methods, `BeanPostProcessor` khi thật cần | Bean lifecycle khác method advice; BPP rất nhạy cảm nên phải giới hạn |
| Cross-cutting service audit theo method tùy chỉnh | Custom Spring `@Aspect` | Đúng proxy join point; cần pointcut và ordering/test |
| Đi qua non-bean constructor/field write | AspectJ weaving tích hợp Spring | Spring AOP proxy không intercept loại join point này |

Không suy ra `@Transactional`/ `@Cacheable` không có proxy: **proxy mode là mặc định** của cả hai. Chọn abstraction chuyên biệt (S) nhưng vẫn phải kiểm tra external calls, self-invocation, bean scope và pointcut/advisor visibility.

## 5. Phần bài tập — tự chọn trước khi xem đáp án

Với mỗi đề, hãy viết ra: **A, S hay J**, tên Spring API/aspect cụ thể, một điều kiện cấu hình, và một regression test đủ chứng minh giải pháp đúng. Có thể đưa một phương án *thứ hai* khi các giả định thay đổi, nhưng phải chỉ ra **vì sao**.

| Mã | Tình huống thực tế | Điều kiện bắt buộc |
| --- | --- | --- |
| BT01 | Log trước/sau các method `submit` của `@Service` để thêm correlation info tùy chỉnh | Mọi caller inject Spring bean đúng cách |
| BT02 | Tự rollback database khi một `@Service` method thất bại | Không muốn tự mở/commit JDBC connections |
| BT03 | Gọi tra cứu với cùng key nhiều lần mà không gọi database lại | Có CacheManager phù hợp |
| BT04 | Chặn mọi HTTP request trước khi vào Spring MVC để áp policy chung | Bao gồm cả URL không map tới controller |
| BT05 | Chỉ ghi metrics gắn với Spring MVC handler được chọn | Cần biết handler mapping |
| BT06 | Chỉ người dùng đủ quyền mới vào API trong web app | Đây là yêu cầu bảo mật chính |
| BT07 | Trả JSON error chuẩn khi controller ném domain exception | Kết quả phải là HTTP response đúng mã lỗi |
| BT08 | Phát `OrderPlaced` để module khác gửi thông báo | Producer không muốn biết consumer |
| BT09 | Xác thực DTO request trước khi vào business service | Cần constraint validation chuẩn |
| BT10 | Một service chạy việc gửi email ở thread khác | Đã chọn executor và error policy |
| BT11 | Business bean gọi `this.calculate()` nhưng muốn custom method advice trên `calculate` | Code được phép refactor |
| BT12 | Bean `@Transactional` có self-call và không được phép refactor/tách bean | Phải advice cả lời gọi nội bộ |
| BT13 | Entity do ORM/legacy code trực tiếp `new` cần collaborator Spring | Không thay được nơi tạo instance |
| BT14 | Cần bắt mọi lần ghi một field domain object, không có setter | Không thể đổi thành method invocation |
| BT15 | Kotlin class `final` implement interface, cần audit method trên Spring bean | Có thể dùng JDK interface proxy |
| BT16 | Java service không interface nhưng cần method advice; class và method đều không final | Spring quản lý bean |
| BT17 | Custom audit phải ghi cả **cache hit** và cache miss trên `@Cacheable` method | Có thể điều chỉnh advisor order |
| BT18 | Một Spring bean đã có transaction advice, giờ thêm một custom `@Around` đo latency | Cần bảo toàn rollback/exception |
| BT19 | Một request bị retry 3 lần bởi policy; metrics phải phân biệt request và attempt | Có retry infrastructure |
| BT20 | Có hai dòng audit cho một lần gọi, business method chỉ chạy một lần | Code có `ProxyFactoryBean` và `@Aspect` |
| BT21 | Mỗi tối chạy một tác vụ tổng hợp, không có caller gọi method theo yêu cầu | Kích hoạt theo lịch |
| BT22 | Muốn đo thời gian xử lý thực khi một method trả Reactor `Mono` | Work thực diễn ra khi subscribe |
| BT23 | Một `@Service` có private method ghi audit; muốn chặn chính private method ấy | Có thể đổi thiết kế |
| BT24 | Một domain object được tạo trước khi Spring context refresh, muốn `@Configurable` inject vào constructor | Không kiểm soát được thời điểm tạo |
| BT25 | `@Cacheable` với self-call cần áp dụng cả nội bộ nhưng không đổi caller được | Chấp nhận build-time/load-time weaving |
| BT26 | Nâng cấp module làm Spring AOP advice tự nhiên biến mất; bean vẫn ở context | Cần xác định nguyên nhân trước khi chọn công nghệ |

Gợi ý cách tự chấm: mỗi đề 4 điểm — **1 điểm** lựa chọn phù hợp, **1 điểm** hạ tầng cần thiết, **1 điểm** giới hạn/caveat, **1 điểm** assertion/integration test. **26 đề × 4 = 104 điểm**; đây là khung ôn tập, không phải thang chấm chính thức của Spring.

## 6. Đáp án và lập luận: BT01–BT09

### BT01 — Audit tùy chỉnh quanh service method

**Chọn A: Spring AOP custom `@Aspect`.** Target là Spring-managed service, caller đi qua proxy, yêu cầu chính xác là method-execution interception mà không có abstraction Spring nào đã định nghĩa riêng định dạng audit này. Đăng ký aspect thành bean bằng `@Component` hoặc `@Bean`, bật `@EnableAspectJAutoProxy` nếu chưa có auto-proxy infrastructure, dùng `@Around("execution(* ...submit(..))")` và bảo đảm `proceed()` được gọi một lần trừ khi có policy khác.

**Test:** gọi service qua `ApplicationContext`; `AopUtils.isAopProxy=true`, event order before → target → after; method khác không bị match. Không bắt đầu bằng AspectJ weaving vì chi phí không cần thiết.

### BT02 — Transaction và rollback

**Chọn S: Spring declarative transaction `@Transactional`.** Bật transaction management, cấu hình `PlatformTransactionManager`, method ở bean được gọi qua Spring proxy. Kiểm tra propagation, exception rollback mặc định và resource thực. Không tự viết `@Around` rồi gọi JDBC commit/rollback trong aspect nếu chỉ cần Spring transaction.

**Test:** insert rồi ném `RuntimeException`, query database xác minh row rollback; kiểm tra trường hợp success commit. Lưu ý Spring transaction mặc định **proxy mode**, nên self-invocation không tự mở transaction mới.

### BT03 — Cache kết quả theo key

**Chọn S: `@Cacheable` + `@EnableCaching` + `CacheManager`.** Spring cung cấp key/cache/evict contracts; một custom aspect tự viết map cache dễ sai concurrency, TTL, invalidation và exception policy.

**Test:** hai lần gọi cùng key qua proxy, assert cùng result và target counter tăng một lần; gọi khác key target counter tăng. Thêm negative test gọi raw object để thấy annotation không tự cache khi bypass proxy.

### BT04 — HTTP request trước DispatcherServlet

**Chọn S: Servlet `Filter` hoặc `OncePerRequestFilter` trong Spring web stack.** Filter chain ở boundary HTTP, không đòi một controller/service Spring method đã được resolve. Rõ hơn custom `@Around` trên tất cả controllers.

**Test:** HTTP request tới mapped và unmapped URLs; kiểm tra filter behavior. Nếu concern là authentication/authorization, ưu tiên Spring Security `SecurityFilterChain` chứ không tự viết security policy rời rạc.

### BT05 — Spring MVC handler-specific metadata

**Chọn S: `HandlerInterceptor`.** Nó nhận context của mapped MVC handler, phù hợp thống kê theo handler hoặc path pattern, trong khi Servlet Filter hoạt động trước/ngoài handler mapping.

**Test:** MockMvc request tới hai mappings; kiểm tra `preHandle` và `afterCompletion` cho handler đã chọn. Tránh dùng `HandlerInterceptor` như security enforcement layer chính; Spring khuyến nghị Spring Security/filter chain cho HTTP security.

### BT06 — Bảo vệ HTTP endpoint

**Chọn S: Spring Security.** HTTP authentication, authorization, session/CSRF/filter order và method security đã có hệ sinh thái chính thức. Một `@Around` tự đọc username rồi tự chặn method có thể bỏ lỡ routes hoặc layer khác.

**Test:** mock authenticated/unauthenticated requests, assert 401/403/200 tùy policy; kiểm tra method-level `@PreAuthorize` nếu business use case đòi quyền tại service boundary. **Ghi chú:** method security cũng có thể dùng Spring AOP advisors; chọn S là chọn **chính sách Spring Security**, không phải phủ nhận AOP bên dưới.

### BT07 — Chuyển exception thành HTTP JSON

**Chọn S: `@RestControllerAdvice` + `@ExceptionHandler`.** Cơ chế MVC exception resolution phù hợp ánh xạ domain exception sang status/body, khác việc advice service method. Custom `@Around` ở service không nên chịu trách nhiệm HTTP status.

**Test:** dùng MockMvc gọi controller ném exception; assert HTTP status, JSON fields, correlation ID nếu cần. Phần service vẫn ném domain exception chuẩn.

### BT08 — Thông báo sự kiện nghiệp vụ

**Chọn S: `ApplicationEventPublisher` và `@EventListener`.** Producer publish domain event; listener xử lý riêng, giảm coupling. Nếu cần listener chỉ chạy sau DB commit, xem `@TransactionalEventListener(phase=AFTER_COMMIT)` với transaction boundaries phù hợp.

**Test:** publish event qua context, assert listener nhận đúng payload; với transactional event test commit/rollback. **Không** coi Spring in-process events là durable messaging hoặc exactly-once delivery tự động.

### BT09 — Validate DTO đầu vào

**Chọn S: Jakarta Bean Validation qua Spring MVC `@Valid`, `@Validated` khi phù hợp.** Spring có cơ chế binding/validation và exception reporting; custom aspect reflection qua field không phải cách tốt để thay thế constraint engine.

**Test:** MockMvc gửi invalid request, assert 400/problem detail theo config; hợp lệ thì controller vào service. Trường hợp method validation trên service phụ thuộc Spring version, `@Validated` và validation infrastructure; vẫn chọn abstraction Spring validation.

## 7. Đáp án và lập luận: BT10–BT18

### BT10 — Async work

**Chọn S: `@Async` + `@EnableAsync` + executor phù hợp**, nếu mục tiêu là thực thi bất đồng bộ của Spring bean. Method phải được caller đi qua proxy khi chạy proxy mode; self-invocation không tự đưa việc lên executor. Kiểm tra return type `Future`/`CompletableFuture` hoặc `void`, error handling và thread context.

**Test:** task được thực thi trong configured executor, caller nhận completion handle theo contract; chờ `join()` hoặc test synchronization, đừng dùng `Thread.sleep` tùy hứng.

### BT11 — Self-invocation nhưng được refactor

**Chọn A với refactor, hoặc S nếu yêu cầu có abstraction chuyên biệt.** Nếu custom `@Aspect` phải intercept `calculate()`, tách `calculate` sang **Spring bean khác** rồi inject proxy ấy vào caller. Điều này tạo external proxy call; không cần AspectJ weaving.

**Test:** direct `this.calculate()` đối chứng không ghi advice; gọi qua injected collaborator ghi before/target/after. Đừng chữa bằng `proxyTargetClass=true`, vì CGLIB vẫn bị self-invocation.

### BT12 — Self-invocation của @Transactional, không đổi code được

**Chọn J với Spring transaction AspectJ mode**, nếu thực sự cần transaction advice trên internal method call và không thể refactor. `@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)`, module `spring-aspects` và AspectJ CTW/LTW phù hợp; implementation method/class annotations phải được weave thực sự.

**Test:** một self-invoked method có transaction attribute khác được thực thi dưới weaving, xác minh isolation/rollback DB bằng transaction manager; so với proxy mode để chứng minh khác biệt. **Không** tự viết custom AspectJ transaction aspect nếu Spring `AnnotationTransactionAspect` đã đáp ứng yêu cầu.

~~~java
@Configuration
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
class WovenTxConfig {
    // PlatformTransactionManager + dependencies.
}
~~~

Snippet minh họa cần imports `org.springframework.context.annotation.AdviceMode`, `org.springframework.transaction.annotation.EnableTransactionManagement`, `@Configuration` và build/runtime weaver. Chỉ annotation không làm JVM tự weave bytecode.

### BT13 — Object ngoài container cần Spring DI

**Ưu tiên S qua factory/injection tường minh nếu đổi được thiết kế; theo ràng buộc đề, chọn J.** Dùng Spring-provided `@Configurable` trên domain type và `AnnotationBeanConfigurerAspect` từ `spring-aspects`, kết nối aspect với BeanFactory bằng `@EnableSpringConfigured` hoặc XML `<context:spring-configured/>`, rồi **bắt buộc AspectJ weave annotated class**.

**Test:** tạo object bằng `new` sau khi context đã refresh, collaborator có mặt khi method chạy; chạy đối chứng **không weaving** thì injection không xuất hiện. Đừng đặt `@Configurable` lên chính Spring bean bình thường vì có thể gây **double initialization**.

### BT14 — Field write join point

**Chọn J khi không được refactor**, vì proxy-based Spring AOP không intercept arbitrary field `get/set` join points. AspectJ CTW/LTW hỗ trợ loại join point tương ứng nếu class được weave, pointcut đúng và classloader thích hợp.

**Giải pháp thay thế:** nếu có quyền đổi domain API, encapsulate mutation qua public method trên Spring-managed bean, lúc đó chọn A hoặc domain method bình thường tùy cross-cutting requirement. **Test:** thay đổi field và kiểm tra aspect invocation bằng woven integration test, không suy diễn từ Spring `AopUtils.isAopProxy` trên một object `new`.

### BT15 — Kotlin final có interface

**Chọn A: JDK interface-based Spring AOP proxy**, không cần AspectJ weaving chỉ vì Kotlin class final. JDK proxy triển khai interface, không subclass final implementation; consumer inject interface. Kiểm tra liệu config khác có ép `proxyTargetClass=true` không.

**Test:** `AopUtils.isJdkDynamicProxy=true`, pointcut match implementation method, advice chạy qua injected interface. Nếu buộc CGLIB, cần class/method mở được bằng `kotlin-spring` compiler plugin hoặc Kotlin `open` phù hợp.

### BT16 — Java class không interface, không final

**Chọn A: Spring class-based CGLIB proxy** khi public method match custom aspect. Spring subclass được class và override method phù hợp. Không cần tạo interface giả chỉ để Spring AOP hoạt động.

**Test:** lấy bean theo concrete class, `AopUtils.isCglibProxy=true`, method-call advice ghi event; một private/final method đối chứng sẽ không được advised qua CGLIB.

### BT17 — Audit cả cache hit/miss

**Chọn kết hợp S + A:** `@Cacheable` giải quyết cache; custom `@Around` giải quyết audit riêng. Đặt audit **ngoài CacheInterceptor** nếu phải ghi mọi proxy invocation. Ví dụ `@Order(-100)` cho audit, `@EnableCaching(order=Ordered.LOWEST_PRECEDENCE)` cho cache.

**Test:** hai calls cùng key → **hai audit before/after** nhưng chỉ **một target execution**. Nếu cache nằm ngoài audit, hit có thể short-circuit và audit phía trong không chạy. Không tự viết cache lại bằng aspect vì muốn thêm audit.

### BT18 — Đã có transaction advisor, thêm đo latency

**Chọn A + S**: giữ `@Transactional` hiện hữu, thêm custom Spring `@Around` chỉ cho tracing/timing. Không thêm `ProxyFactory` bọc proxy cũ vô điều kiện; Spring có thể quản lý nhiều advisors trên cùng bean.

**Test:** success/exception paths; audit giữ nguyên result, rethrow exception, transaction commit/rollback vẫn đúng. Nếu audit ở ngoài transaction, elapsed time có thể bao gồm transaction interceptor; nếu ở trong, thời điểm commit có thể sau event `after`. Chọn `@Order` dựa theo ý nghĩa cần đo.

## 8. Đáp án và lập luận: BT19–BT26

### BT19 — Retry attempts và audit request-level

**Chọn S cho retry infrastructure, A nếu cần custom audit metrics:** dùng `@Retryable`/Spring Retry khi module đã được chọn và cấu hình policy; custom audit để đo request-level hoặc attempt-level. Không tự viết loop `proceed()` không giới hạn trong aspect.

**Test:** target fail hai lần và success ở attempt thứ ba; audit ngoài retry chạy một lượt logical call, audit trong retry có thể chạy ba lượt. Assert attempts, side effects/idempotency và transaction boundaries độc lập.

### BT20 — Hai audit events, target một lần

**Chưa chọn công nghệ mới; chọn sửa cấu hình Spring AOP hiện tại (A).** Kiểm tra `Advised.getAdvisors()`, số `AuditAspect` beans, `ProxyFactoryBean`, manual `ProxyFactory` và nested singleton target via `AopProxyUtils.getSingletonTarget`. Một proxy hai advisors hoặc outer+inner proxy đều có thể tạo hai audit invocations.

**Test:** một external method call, đếm advisor/audit invocations và target invocations riêng; chỉ khi xác định duplicate thật mới xóa registration trùng. **Không** bật AspectJ weaving để chữa lỗi duplicate.

### BT21 — Chạy job hằng đêm

**Chọn S: Spring scheduling `@Scheduled` + `@EnableScheduling`**, hoặc external scheduler phù hợp quy mô. `@Around` intercept invocation tồn tại, **không tự tạo ra lịch gọi**.

**Test:** unit test body của job và integration test scheduler configuration/timing bằng clock/test harness thích hợp; không sleep tuỳ ý. Nếu job cần audit, *bổ sung* A trên service được scheduler gọi qua Spring proxy, không thay thế scheduler.

### BT22 — Đo toàn bộ reactive pipeline

**Ưu tiên S: Reactor operators và context-aware instrumentation** cho thời gian subscription/completion, ví dụ `Mono.defer`, `doOnEach`, `doFinally`, `Context` phù hợp; có thể bổ sung Micrometer observation tích hợp Spring. Một synchronous `@Around` quanh method trả `Mono` có thể chỉ đo **thời gian tạo Publisher**, không đo downstream execution.

**Test:** dùng Reactor Test `StepVerifier` hoặc equivalent, assert events tại subscription/completion; không coi return của `Mono` là kết quả computation đã xong. Chọn A nếu chỉ muốn đo chi phí method **tạo Publisher** của Spring bean; đừng suy diễn A đo request completion.

### BT23 — Private method, code cho phép refactor

**Chọn refactor rồi A hoặc S:** đưa behavior cần advice ra **public method trên Spring bean khác**, để call đi qua proxy, hoặc di chuyển chính sách ra Spring abstraction nếu nó là transaction/cache/validation. Spring CGLIB không override `private`, JDK proxy không expose private method.

**Test:** method public mới được advise, private helper chỉ còn business logic thuần. AspectJ weaving (J) là **phương án dự phòng** nếu bài toán thật sự không thể refactor và buộc intercept private method join point.

### BT24 — @Configurable trước context refresh

**Chưa có giải pháp tự động chỉ từ annotation; đây là blocker về lifecycle.** Dù chọn J và bật `AnnotationBeanConfigurerAspect` + weaving, aspect vẫn cần được **Spring cấu hình với BeanFactory trước khi object được tạo**. Nếu object xuất hiện trước đó, Spring không thể bảo đảm inject retroactively bằng `@Configurable`.

**Hướng sửa:** thay đổi bootstrap/creation timing, tạo object sau context refresh, hoãn initialization hoặc dùng explicit factory/passed collaborator khi có thể. `preConstruction=true` giải quyết **thời điểm inject so với constructor body**, **không** tự tạo một ApplicationContext chưa tồn tại. **Test:** tạo object trước/sau context readiness, phân biệt behavior và tránh silent null dependency.

### BT25 — @Cacheable self-call không thể refactor

**Chọn J với chính Spring caching AspectJ mode**, nếu ràng buộc không thể đổi caller được xác minh. `@EnableCaching(mode = AdviceMode.ASPECTJ)` cùng `spring-aspects` và AspectJ CTW/LTW giúp caching intercept nội bộ theo bytecode weaving thay vì proxy boundary.

~~~java
@Configuration
@EnableCaching(mode = AdviceMode.ASPECTJ)
class WovenCacheConfig {
    // CacheManager bean và AspectJ weaving build/runtime configuration.
}
~~~

Đây là **snippet minh họa**, chưa runnable nếu không có `CacheManager`, `spring-aspects` và weaving agent/build. **Test:** gọi self-invoked cached method qua một wrapper rồi assert target execution count cho cùng key, cache-hit behavior; đừng kiểm tra `AopUtils.isAopProxy` như điều kiện bắt buộc ở AspectJ mode, vì weaving không cần Spring AOP proxy.

### BT26 — Advice biến mất sau nâng cấp

**Chọn chẩn đoán Spring AOP (A), chưa chuyển sang J ngay.** Đi theo chuỗi bean → proxy → advisor/pointcut → advice → target. Xác minh đúng `@Aspect` bean và component scan, `@EnableAspectJAutoProxy`, proxy JDK/CGLIB settings, Kotlin `final` flags, pointcut expression, ordering, context ownership, self-invocation và những bean overrides trong tests.

**Test:** context tối thiểu trên đúng Spring version hiện tại, một matching method và một non-matching method, inspect proxy/advisors, assert events. Khi nguyên nhân là tính năng Spring khác, chuyển S; chỉ chọn J khi phát hiện yêu cầu join point thực sự vượt Spring proxy capabilities.

## 9. Bài thực hành Java 17: cùng ứng dụng dùng A và S, không cần J

Bài thực hành dưới đây dùng **custom Spring `@Aspect`** trên method `place`, **Spring `@Cacheable`** trên method `summary`, và một method `selfPlace` cho thấy lời gọi `this.place` không qua proxy. Từ một Spring context nhỏ, ta chứng minh bằng events:

1. `place("A")` qua bean proxy có audit before → target → after (**A**).
2. `summary("X")` gọi hai lần cùng key nhưng target chỉ chạy một lần (**S**).
3. `selfPlace("B")` gọi `this.place("B")` trên target, audit không chạy do Spring proxy boundary (**khi thật cần interception nội bộ, cân nhắc refactor/J**).
4. Object tạo bằng `new` không tự nhận advice/cache (**không nên chọn A để can thiệp raw object**).
5. Method `place("FAIL")` ném exception; custom `@Around` ghi error rồi **rethrow**, không nuốt lỗi.

### 9.1. Cấu trúc

~~~text
spring-aop-choice-lab/
├── pom.xml
└── src/
    ├── main/java/demo/
    │   ├── OrderPort.java
    │   ├── Events.java
    │   ├── OrderService.java
    │   ├── AuditAspect.java
    │   ├── LabConfig.java
    │   └── App.java
    └── test/java/demo/
        └── ChoiceLabTest.java
~~~

### 9.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-choice-lab</artifactId>
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
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

**Lưu ý:** `aspectjweaver` được Spring AOP dùng để phân tích `@AspectJ` expression, nhưng lab **không** dùng AspectJ Java agent/CTW/LTW. Việc có `aspectjweaver` dependency không khiến các object `new` tự được advise. `@EnableCaching` cần một `CacheManager` và Spring infrastructure riêng.

### 9.3. OrderPort.java

~~~java
package demo;

public interface OrderPort {
    String place(String id);
    String summary(String id);
    String selfPlace(String id);
}
~~~

Interface cho phép Spring dùng JDK dynamic proxy; test/caller lấy bean theo `OrderPort`, không ép proxy thành concrete `OrderService`.

### 9.4. Events.java

~~~java
package demo;

import java.util.ArrayList;
import java.util.List;

public class Events {
    private final List<String> values = new ArrayList<>();

    public void record(String event) {
        values.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(values);
    }
}
~~~

Recorder này dành cho demo và integration tests **đơn luồng, context riêng mỗi test**. Trong production concurrent environment, cần event/tracing infrastructure phù hợp hoặc collection thread-safe.

### 9.5. OrderService.java

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cache.annotation.Cacheable;

public class OrderService implements OrderPort {
    private final Events events;
    private final AtomicInteger summaryExecutions = new AtomicInteger();

    public OrderService(Events events) {
        this.events = events;
    }

    @Override
    public String place(String id) {
        events.record("target:place:" + id);

        if ("FAIL".equals(id)) {
            throw new IllegalStateException("rejected:" + id);
        }

        return "ok:" + id;
    }

    @Override
    @Cacheable("summaries")
    public String summary(String id) {
        int count = summaryExecutions.incrementAndGet();
        events.record("target:summary:" + id + ":" + count);
        return "summary:" + id + ":" + count;
    }

    @Override
    public String selfPlace(String id) {
        events.record("target:self:" + id);
        return this.place(id);
    }
}
~~~

Có hai loại behavior khác nhau:

- `place` không được Spring feature chuyên biệt xử lý; custom aspect sẽ audit.
- `summary` đã được Spring caching abstraction hỗ trợ: `@Cacheable` + `CacheManager`.
- `selfPlace` gọi `this.place`, không đi ngược ra Spring AOP proxy để `place` advice chạy riêng.

Đây là ví dụ giải thích **ranh giới công nghệ**, không phải recommendation để service thật đặt self-call nếu cần aspect.

### 9.6. AuditAspect.java

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class AuditAspect {
    private final Events events;

    public AuditAspect(Events events) {
        this.events = events;
    }

    @Around("execution(* demo.OrderService.place(..))")
    public Object audit(ProceedingJoinPoint point) throws Throwable {
        String id = String.valueOf(point.getArgs()[0]);
        events.record("audit:before:" + id);

        try {
            Object result = point.proceed();
            events.record("audit:after:" + id);
            return result;
        } catch (Throwable error) {
            events.record("audit:error:" + id);
            throw error;
        }
    }
}
~~~

Aspect `@Around` giữ nguyên result/exception; `proceed()` chuyển invocation qua interceptor chain tới target. Nó match `OrderService.place(..)`, **không match** `summary` hay `selfPlace`. `@Aspect` class không tự được scan; `LabConfig` khai báo `@Bean AuditAspect`.

### 9.7. LabConfig.java

~~~java
package demo;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
@EnableCaching
public class LabConfig {

    @Bean
    Events events() {
        return new Events();
    }

    @Bean
    OrderPort orderService(Events events) {
        return new OrderService(events);
    }

    @Bean
    AuditAspect auditAspect(Events events) {
        return new AuditAspect(events);
    }

    @Bean
    CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("summaries");
    }
}
~~~

Đây là một `ApplicationContext` tối thiểu với bốn application beans: recorder, target, aspect, cache manager. Spring thêm hạ tầng AOP/caching. Ta **không** tự bọc một proxy bên trong `ProxyFactory`, vì không có yêu cầu tạo nested proxies. Dù `@EnableCaching` và `@EnableAspectJAutoProxy` cùng xuất hiện, không mặc định kết luận có hai proxy layers; hãy quan sát đối tượng ở runtime khi cần.

### 9.8. App.java

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class App {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(LabConfig.class)) {
            OrderPort service = context.getBean(OrderPort.class);
            Events events = context.getBean(Events.class);

            System.out.println("aop=" + AopUtils.isAopProxy(service));
            System.out.println("jdk=" + AopUtils.isJdkDynamicProxy(service));

            System.out.println("place=" + service.place("A"));
            System.out.println("summary1=" + service.summary("X"));
            System.out.println("summary2=" + service.summary("X"));
            System.out.println("self=" + service.selfPlace("B"));

            try {
                service.place("FAIL");
            } catch (IllegalStateException ex) {
                System.out.println("caught=" + ex.getMessage());
            }

            System.out.println("events=" +
                    String.join("|", events.snapshot()));
        }
    }
}
~~~

### 9.9. Build, chạy và kết quả mong đợi

~~~bash
mvn -q compile exec:java
~~~

Theo logic ứng dụng, các dòng do `System.out.println` in:

~~~text
aop=true
jdk=true
place=ok:A
summary1=summary:X:1
summary2=summary:X:1
self=ok:B
caught=rejected:FAIL
events=audit:before:A|target:place:A|audit:after:A|target:summary:X:1|target:self:B|target:place:B|audit:before:FAIL|target:place:FAIL|audit:error:FAIL
~~~

Phân tích:

1. Spring trả JDK AOP proxy, nên `aop=true`, `jdk=true`.
2. `place("A")` qua proxy → audit before, target, audit after.
3. `summary("X")` lần đầu cache miss → target in `target:summary:X:1`.
4. `summary("X")` lần hai cache hit → vẫn trả `summary:X:1`, **không** ghi thêm target event.
5. `selfPlace("B")` đi qua proxy vào `selfPlace`, nhưng gọi `this.place("B")` bên trong target → chỉ có `target:self:B`, `target:place:B` mà **không có** audit around cho B.
6. `place("FAIL")` qua proxy → audit before → target throws → audit error; exception giữ nguyên ra caller.

Maven/Spring logging có thể thêm dòng khác; phần liệt kê là **kết quả mong đợi theo mã**, chưa phải một lần thực thi Maven được xác minh trong môi trường viết bài.

## 10. JUnit 5: chứng minh từng lựa chọn có đúng không

**src/test/java/demo/ChoiceLabTest.java**

~~~java
package demo;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

class ChoiceLabTest {

    @Configuration(proxyBeanMethods = false)
    static class WithoutAopConfig {

        @Bean
        Events events() {
            return new Events();
        }

        @Bean
        OrderPort orderService(Events events) {
            return new OrderService(events);
        }

        @Bean
        AuditAspect auditAspect(Events events) {
            return new AuditAspect(events);
        }
    }

    @Test
    void customAspectIsCorrectForExternalSpringBeanCall() {
        try (var ctx =
                new AnnotationConfigApplicationContext(LabConfig.class)) {
            OrderPort service = ctx.getBean(OrderPort.class);
            Events events = ctx.getBean(Events.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertTrue(AopUtils.isJdkDynamicProxy(service));
            assertEquals("ok:A", service.place("A"));

            assertEquals(
                List.of("audit:before:A", "target:place:A", "audit:after:A"),
                events.snapshot());
        }
    }

    @Test
    void springCacheFeatureReusesResultInsteadOfCustomAspect() {
        try (var ctx =
                new AnnotationConfigApplicationContext(LabConfig.class)) {
            OrderPort service = ctx.getBean(OrderPort.class);
            Events events = ctx.getBean(Events.class);

            assertEquals("summary:X:1", service.summary("X"));
            assertEquals("summary:X:1", service.summary("X"));
            assertEquals("summary:Y:2", service.summary("Y"));

            assertEquals(
                List.of("target:summary:X:1", "target:summary:Y:2"),
                events.snapshot());
        }
    }

    @Test
    void selfInvocationDoesNotPassThroughSpringAspect() {
        try (var ctx =
                new AnnotationConfigApplicationContext(LabConfig.class)) {
            OrderPort service = ctx.getBean(OrderPort.class);
            Events events = ctx.getBean(Events.class);

            assertEquals("ok:B", service.selfPlace("B"));
            assertEquals(
                List.of("target:self:B", "target:place:B"),
                events.snapshot());
        }
    }

    @Test
    void rawObjectHasNoSpringAspectAndNoCacheInterception() {
        Events events = new Events();
        OrderPort raw = new OrderService(events);

        assertFalse(AopUtils.isAopProxy(raw));
        assertEquals("ok:R", raw.place("R"));
        assertEquals("summary:X:1", raw.summary("X"));
        assertEquals("summary:X:2", raw.summary("X"));

        assertEquals(
                List.of("target:place:R",
                        "target:summary:X:1",
                        "target:summary:X:2"),
                events.snapshot());
    }

    @Test
    void aroundAdvicePreservesBusinessException() {
        try (var ctx =
                new AnnotationConfigApplicationContext(LabConfig.class)) {
            OrderPort service = ctx.getBean(OrderPort.class);
            Events events = ctx.getBean(Events.class);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> service.place("FAIL"));

            assertEquals("rejected:FAIL", ex.getMessage());
            assertEquals(
                List.of("audit:before:FAIL",
                        "target:place:FAIL",
                        "audit:error:FAIL"),
                events.snapshot());
        }
    }

    @Test
    void registeringAspectBeanWithoutAutoproxyIsInsufficient() {
        try (var ctx = new AnnotationConfigApplicationContext(
                WithoutAopConfig.class)) {
            OrderPort service = ctx.getBean(OrderPort.class);
            Events events = ctx.getBean(Events.class);

            assertFalse(AopUtils.isAopProxy(service));
            assertEquals("ok:RAW", service.place("RAW"));
            assertEquals(List.of("target:place:RAW"), events.snapshot());
        }
    }
}
~~~

Bộ test dùng đúng Spring Framework ApplicationContext, không cần Spring Boot hoặc database. **Sáu** `@Test` chứng minh lần lượt:

- AOP proxy tạo được cho bean và chạy custom aspect.
- Caching abstraction đúng hơn tự viết aspect lưu kết quả.
- Self-invocation **bypass** proxy dù bean ngoài đã được Spring quản lý.
- Raw `new` không tự nhận AOP hoặc caching.
- `@Around` không nuốt exception.
- Có `@Aspect` bean nhưng không bật auto-proxy thì advice không chạy.

Chạy:

~~~bash
mvn -q test
~~~

Kỳ vọng report `target/surefire-reports/demo.ChoiceLabTest.txt`:

~~~text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
~~~

Đây là kết quả **dự kiến khi compile và chạy thành công**, không phải log đã được đo trên máy hiện tại.

## 11. Cách chuyển từ lựa chọn S sang J đúng phạm vi Spring

Nếu **cơ chế Spring đã có** nhưng vướng self-invocation hoặc non-bean target, không nên ngay lập tức viết một custom AspectJ aspect thay thế transaction/caching. Spring cung cấp một số **AspectJ aspects trong `spring-aspects`** cho những tính năng được hỗ trợ.

### 11.1. @Transactional ở AspectJ mode

~~~java
@Configuration
@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
class TxAspectJConfig {
    // PlatformTransactionManager và các bean khác.
}
~~~

**Điều kiện đi kèm:** `spring-aspects`, AspectJ CTW/LTW thực sự bật cho target types, AspectJ-compatible annotation placement và transaction manager. Với `AdviceMode.ASPECTJ`, việc thêm `@Transactional` trên **interface** không đáng tin cậy cho weaving, vì annotations Java trên interface không được tự kế thừa sang implementation; Spring docs khuyến nghị annotate concrete class/method.

### 11.2. @Cacheable ở AspectJ mode

~~~java
@Configuration
@EnableCaching(mode = AdviceMode.ASPECTJ)
class CacheAspectJConfig {
    // CacheManager bean và AspectJ weaving build/runtime configuration.
}
~~~

**Điều kiện đi kèm:** Spring caching aspect hỗ trợ phù hợp, `spring-aspects`, AspectJ weaver được kích hoạt, cache manager, classpath và runtime weaving config. Không dùng hai snippets mục 11 như một chương trình runnable độc lập; chúng là **cấu hình biến thể** cần thêm hạ tầng.

### 11.3. @Configurable và AnnotationBeanConfigurerAspect

~~~java
@Configurable("invoiceDraft")
public class InvoiceDraft {
    // Spring-provided AspectJ aspect có thể cấu hình existing object.
}
~~~

Kèm `@EnableSpringConfigured` hoặc `<context:spring-configured/>`, `spring-aspects`, AspectJ weaving và bean definition metadata đúng. Annotation `@Configurable` một mình **không inject gì**; `@EnableSpringConfigured` chỉ kết nối aspect với BeanFactory chứ **không thực hiện weaving**. `@Configurable(preConstruction=true)` cho phép yêu cầu cấu hình trước constructor body nếu weaving setup đúng, nhưng **không cứu được** object được tạo khi ApplicationContext/BeanFactory chưa sẵn sàng.

### 11.4. AspectJ không phải phép chữa mọi lỗi proxy

Chỉ cân nhắc J sau khi chứng minh **join point cần chặn không đi qua proxy** và không có Spring abstraction/refactor hợp lý. Nếu vấn đề chỉ là aspect bean chưa đăng ký, pointcut viết sai, advisor order bị đảo hoặc self-invocation có thể refactor, thêm weaving sẽ gia tăng độ phức tạp mà chưa sửa gốc.

Một ứng dụng có thể phối hợp Spring AOP proxies và AspectJ weaving, nhưng khi cùng concern được xử lý bởi hai cơ chế, cần kiểm soát **double advice**, aspect selection và ordering. Không coi `AopUtils.isAopProxy` là công cụ phát hiện mọi woven join point: woven target có thể không phải Spring proxy.

## 12. Kiểm thử ra quyết định trên hệ thống thực

Tạo ba nhóm kiểm thử thay vì chỉ một test “aspect hoạt động”:

### Nhóm A — Spring AOP custom behavior

1. Bean và aspect đều được Spring đăng ký.
2. `AopUtils.isAopProxy(actualService)` đúng với kỳ vọng.
3. Method match pointcut qua proxy có before/target/after đúng.
4. Method không match hoặc self-invoked method được đánh giá đúng giới hạn.
5. `@Around` giữ return/exception semantics, không lặp `proceed()` ngoài chủ ý.
6. Nếu có multiple advisors, kiểm tra outer/inner ordering và duplicate registrations.

### Nhóm S — Spring module chuyên biệt

1. Transaction: assert actual database commit/rollback, không chỉ log.
2. Caching: assert hit/miss, key, eviction và target execution count.
3. Async: assert Future completion, executor/threading policy và error handling.
4. MVC HTTP: dùng MockMvc hoặc web integration tests cho handler/exception/filter behavior.
5. Security: test 401/403/200 và method authorization theo authentication state.
6. Event: assert listener invoked đúng phase/transaction outcome; đừng giả định event là durable queue.
7. Validation: assert constraint violation và HTTP response/BindingResult.
8. Retry: assert số attempts, idempotency/side effects chứ không chỉ return value.

### Nhóm J — Spring + AspectJ weaving thật

1. Build/runtime thực sự có AspectJ compiler hoặc agent và weaving rules `META-INF/aop.xml` phù hợp.
2. Target class/constructor/field join point đã được weave; verify weave info/build output khi cần.
3. AspectJ aspect được Spring kết nối BeanFactory nếu có `@Configurable`.
4. Tạo non-bean object bằng `new` **sau** context readiness nếu cần injection; test đối chứng không weaving.
5. Self-invocation/internal method được advice đúng điều kiện của AspectJ mode.
6. Với Spring transaction/cache AspectJ mode, test behavior thật của transaction/cache manager.
7. Kiểm tra classloader, JPMS, module boundaries và deployment mode; không coi JVM `-javaagent` có mặt là đủ nếu class đã load trước lúc weave.
8. Nếu Spring AOP cũng bật, kiểm tra double interception/duplicate side effects.

## 13. Những lựa chọn sai phổ biến

| Lựa chọn vội | Vấn đề thật | Lựa chọn đúng hơn |
| --- | --- | --- |
| Custom `@Around` tự commit JDBC transaction | Rollback/propagation/resource lifecycle phức tạp | Spring `@Transactional` |
| Custom map cache trong aspect | Cache key/eviction/concurrency không được quản lý đúng | Spring cache abstraction |
| Aspect trên mọi controller để xử lý request authorization | HTTP filter chain/route coverage không chắc | Spring Security |
| Aspect chỉ để map business exception → JSON | Service advice không có MVC response pipeline | `@RestControllerAdvice` |
| Aspect viết lại event bus | Method interception không phải publish/subscribe | `ApplicationEventPublisher` |
| `@Aspect` trên class `new` nhưng không có Spring bean | Không có auto-proxying ngoài IoC | Factory/DI hoặc AspectJ weaving khi bắt buộc |
| Chỉ `@EnableAspectJAutoProxy` để chặn constructor/field | Spring proxy không chặn loại join point ấy | AspectJ CTW/LTW (nếu không refactor được) |
| `@EnableSpringConfigured` nhưng không bật weaver | Aspect cấu hình với Spring nhưng target không woven | Bổ sung AspectJ weaving hoặc đổi thiết kế |
| `@Order(0)` để chữa aspect chưa đăng ký | Order chỉ sắp advisors đã tồn tại | Đăng ký aspect bean + bật auto-proxy |
| `proxyTargetClass=true` để chữa self-invocation | CGLIB vẫn là proxy, self-call bypass | Refactor sang Spring collaborator hoặc AspectJ khi không thể |
| Thấy `@Transactional` + custom `@Aspect` rồi tự thêm `ProxyFactory` | Có thể tạo nested proxies/double advice | Để Spring auto-proxy quản lý advisors |
| Đo `Mono` bằng time quanh return của Java method | Chỉ đo việc tạo Publisher, không phải subscription | Reactor instrumentation/context |
| Mock chính service rồi assert aspect behavior | Mockito replacement không phải original advised target | Mock collaborator, giữ real Spring service |
| Dùng `@Configurable` lên Spring `@Component` bình thường | Có thể double initialization/configuration | DI bình thường hoặc chỉ dùng với non-bean types |
| Bật AspectJ vì Kotlin class final | JDK proxy với interface vẫn có thể hoạt động | Chọn interface proxy/kotlin-spring all-open khi CGLIB cần |

## 14. Checklist cuối bài và tài liệu chính thức

Trước khi phê duyệt một pull request thêm custom Spring aspect hoặc AspectJ weaving, hãy xác nhận:

- [ ] Có chứng cứ yêu cầu không được giải quyết trực tiếp bởi một Spring feature sẵn có.
- [ ] Đã xác định rõ **Spring bean method** hay HTTP/event/domain field/constructor boundary.
- [ ] Caller thực sự gọi Spring proxy khi phương án dựa trên proxy.
- [ ] Cấu hình `@Aspect` bean, `@EnableAspectJAutoProxy`/advisor infrastructure, pointcut và proxy strategy đúng.
- [ ] Nếu dùng `@Transactional`, `@Cacheable`, `@Async`, security: module/manager/executor/policy tương ứng đầy đủ.
- [ ] Nếu cần self-invocation, đã thử refactor trước khi chọn AspectJ mode.
- [ ] Nếu cần non-bean/constructor/field joins, đã định nghĩa cụ thể woven types, aspect và weaver configuration.
- [ ] `@EnableSpringConfigured` không bị nhầm là thay thế AspectJ weaving.
- [ ] Có test dương tính, âm tính, exception path và kiểm tra side effects.
- [ ] Advisor order, cache hit/miss, retry attempts và transaction commit/rollback được kiểm tra độc lập khi có nhiều concerns.
- [ ] Không đăng ký cùng một aspect/advisor hai lần hoặc bọc proxy có sẵn ngoài ý định.
- [ ] Có kế hoạch quan sát production: bean IDs, proxy/target class, advisor chain, weaving logs khi dùng J.
- [ ] Có lối quay lại khi weaving/config thay đổi làm khác Spring startup/classloader behavior.

### Tài liệu tham khảo chính thức

- [Spring Framework — Choosing Which AOP Declaration Style to Use](https://docs.spring.io/spring-framework/reference/core/aop/choosing.html).
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html).
- [Spring Framework — Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html).
- [Spring Framework — Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html).
- [Spring Framework — @Transactional Annotation Semantics](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).
- [Spring Framework — Application Events and @EventListener](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html).
- [Spring Framework — Spring MVC Handler Interceptors](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/interceptors.html).
- [Spring Framework — Spring MVC @ControllerAdvice](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html).
- [Spring Framework — Spring AOP API](https://docs.spring.io/spring-framework/reference/core/aop-api.html).
- [Spring Framework — Spring Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html).
- [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).

## 15. Tổng kết: chọn đơn giản nhất nhưng đúng ranh giới

Hãy **chọn Spring abstraction có sẵn trước** nếu nó giải quyết đúng yêu cầu; chọn **custom Spring AOP aspect** khi cần một concern đặc thù quanh method trên Spring-managed bean; chỉ chọn **AspectJ weaving tích hợp Spring** khi proxy boundary thật sự không đáp ứng yêu cầu và việc refactor không khả thi.

Ba phương án có thể kết hợp nhưng không được nhập nhằng: `@Transactional` là Spring feature **có thể được thực hiện bởi Spring AOP proxy**, `@Aspect` là annotation style **không tự tạo bytecode weaving**, và `@EnableSpringConfigured` là một phần kết nối Spring IoC với AspectJ aspect **không thay thế CTW/LTW**.

Một lời giải đạt yêu cầu phải nêu **đối tượng**, **join point**, **cơ chế thực thi**, **điều kiện hạ tầng**, **giới hạn** và **test chứng minh**. Đừng chỉ trả lời tên một annotation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="spring-aop-review-008">Định hướng đọc tài liệu và mã nguồn Spring AOP theo nhu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Định hướng đọc tài liệu và mã nguồn Spring AOP theo nhu cầu

## 1. Mục tiêu: đọc đúng tài liệu, đúng source và đúng phiên bản

Khi sử dụng **Spring Aspect/Spring AOP**, người học dễ lạc giữa tài liệu Spring Framework, Spring Boot, các interface AOP Alliance, AspectJ annotations, mã Java trong `spring-aop` và các source files thuộc `spring-beans`/`spring-context`. Mục tiêu không phải đọc hết repository Spring Framework từ đầu đến cuối, mà là **đặt câu hỏi → chọn tài liệu mô tả contract → tái hiện behavior bằng test → lần theo đoạn source chịu trách nhiệm**.

Bài hướng dẫn này giúp bạn trả lời những loại câu hỏi thực tế:
- Vì sao class có `@Aspect` mà advice không chạy?
- Khi nào bean nhận **JDK dynamic proxy** hoặc **CGLIB class proxy**?
- Tại sao self-invocation, method final/private, bean tạo bằng `new` không chịu Spring proxy advice?
- Spring tìm và chuyển `@Before`/`@Around` thành advisors ở đâu?
- Biểu thức `execution(...)`, `@annotation(...)`, `args(...)` được parse/match tại đâu?
- Spring lấy interceptor chain cho một method và gọi `proceed()` thế nào?
- Phân biệt thứ tự `@Order` trên các aspects và thứ tự advice cùng aspect ra sao?
- Muốn tìm nguồn gốc `@Transactional`, `@Cacheable` hoặc `@Async` thì đọc các lớp integration nào?
- Khi nâng phiên bản Spring, làm sao không nhầm source trên nhánh `main` với bytecode thực tế đang chạy?

**Giới hạn:** trọng tâm là **Spring AOP trong ứng dụng Spring**. Chỉ nhắc AspectJ ở chỗ Spring sử dụng cú pháp pointcut, `@AspectJ` annotations hoặc tích hợp weaving; không đào sâu compiler/weaver của AspectJ như một giáo trình riêng.

## 2. Quy trình đọc 5 bước để tránh mất thời gian

1. **Xác định behavior cần hiểu.** Viết một câu hỏi có chủ ngữ và đường gọi cụ thể, ví dụ: “Tại sao `@Around` không chạy khi `OrderService.placeOrder()` gọi `this.reserve()`?”
2. **Kiểm tra mô hình đang dùng.** Spring AOP proxy, Spring integration advisor hay AspectJ load-time weaving? Không trộn ba cơ chế khi chưa phân biệt.
3. **Đọc đúng trang reference.** Tìm câu trả lời về contract, phạm vi được hỗ trợ, hạn chế và cách cấu hình trước khi nhìn implementation.
4. **Chạy một test nhỏ trong Spring `ApplicationContext` thật.** Assert proxy type, advisor list và event advice; đừng chỉ đọc code hoặc test logic target bằng `new`.
5. **Đi theo một tuyến source.** Đặt breakpoint ở class có trách nhiệm tương ứng, xem parameter/bean/method thực tế; ghi lại kết luận và version.

Một nguyên tắc quan trọng: **tài liệu reference mô tả hành vi có thể dựa vào; source mã `internal` giúp giải thích vì sao version hiện tại làm như vậy, nhưng không phải API ổn định để ứng dụng phụ thuộc**.

## 3. Bản đồ tài liệu Spring AOP nên lưu

Các trang sau là điểm xuất phát chính thức; hãy chọn cùng nhánh phiên bản với ứng dụng:

| Nhu cầu | Đọc trước | Sau đó mới đào sâu |
| --- | --- | --- |
| Nắm Spring AOP tổng quát | `reference/core/aop.html` | Proxying Mechanisms và @AspectJ Support |
| Hiểu `@Aspect` và activation | `reference/core/aop/ataspectj.html` | Advice và Pointcuts |
| Viết `execution`, `@annotation`, `args`, `bean` | `reference/core/aop/ataspectj/pointcuts.html` | `AspectJExpressionPointcut` |
| Binding tham số, `@Around`, `proceed()` | `reference/core/aop/ataspectj/advice.html` | Advisor factory và invocation chain |
| Không chạy vì self-invocation/final/private | `reference/core/aop/proxying.html` | JDK/CGLIB proxy classes |
| XML `<aop:config>`/`<aop:around>` | `reference/core/aop/schema.html` | Spring AOP namespace parsing |
| Tự dùng `ProxyFactory`/`Advised` | `reference/core/aop-api.html` | `ProxyFactory`, `AdvisedSupport`, `ReflectiveMethodInvocation` |
| Bean tạo sớm/mất auto-proxy | `reference/core/beans/factory-extension.html` | `AbstractAutoProxyCreator` và bean lifecycle |
| `@Transactional` không hoạt động | `reference/data-access/transaction/declarative/annotations.html` | Transaction advisor/interceptor integration |
| Cần advise object ngoài container bằng weaving | `reference/core/aop/using-aspectj.html` | AspectJ LTW integration trong Spring |
| Test Spring AOP với JUnit | `reference/testing/testcontext-framework.html` | SpringExtension, @SpringJUnitConfig |

Các đường dẫn trên bắt đầu bằng `https://docs.spring.io/spring-framework/`. Ví dụ liên kết cụ thể:
- [Spring AOP overview](https://docs.spring.io/spring-framework/reference/core/aop.html)
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Declaring Pointcuts](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Schema-based AOP](https://docs.spring.io/spring-framework/reference/core/aop/schema.html)
- [Spring AOP APIs](https://docs.spring.io/spring-framework/reference/core/aop-api.html)
- [Spring BeanPostProcessor extension points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html)
- [Using AspectJ with Spring](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)

**Chú ý phiên bản:** URL `/reference/...` thường dẫn đến tài liệu mặc định của bản Spring mới nhất đang được xuất bản. Một ứng dụng dùng Spring Framework 6.1 hoặc 6.2 không nên mặc định nhận mọi chi tiết trong Spring 7. Chọn đúng version trong bộ chọn của website, ví dụ `/reference/6.2/...` nếu deployment thực tế dùng Spring 6.2. Những phần mới như cấu hình proxy theo từng bean hoặc cách auto-proxy creator hợp nhất có thể thay đổi giữa major versions.

## 4. Nhìn các dependency trước khi mở repository

Trước khi tra source, đọc **runtime dependency graph** của project:
- Dự án dùng Spring Framework 6.x hay 7.x? Các module `spring-aop`, `spring-beans`, `spring-core`, `spring-context` có cùng dòng version?
- Là Spring Boot application được quản lý dependency bởi Boot BOM hay tự pin Spring Framework?
- Có `spring-aspects`, `aspectjweaver`, `spring-tx`, `spring-security`, `spring-cache` integration hoặc thư viện auto-configuration nào không?
- Có bật `-javaagent:aspectjweaver.jar` hay chỉ dùng Spring AOP proxy thông thường?

Với Gradle, có thể kiểm dependency của project **của chính bạn** bằng lệnh:

~~~bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencyInsight --dependency spring-aop --configuration runtimeClasspath
./gradlew dependencyInsight --dependency aspectjweaver --configuration runtimeClasspath
~~~

Với Maven:

~~~bash
mvn dependency:tree -Dincludes=org.springframework:spring-aop,org.aspectj:aspectjweaver
~~~

Lệnh Maven chỉ minh họa cách lọc nhóm dependency, còn syntax/plugin behavior tùy Maven/dependency plugin. Quan trọng là xác nhận **artifact và version thực chạy**, không chỉ version người viết thấy trong bài blog.

**Chọn source tag:** Khi ứng dụng dùng version `6.2.x`, ưu tiên source ở tag release `v6.2.x` tương ứng thay vì mặc định xem `main`. Trong GitHub, dùng bộ chọn branch/tag ở repository hoặc thay `blob/main/` thành `blob/v<version>/` khi release tag đó thực sự tồn tại. Giữ ghi chú “source link là branch main (có thể thay đổi)” nếu chưa pin commit/tag.

## 5. Bản đồ modules Spring Framework dành cho AOP

Không phải mọi Spring AOP class đều nằm trong module `spring-aop`:

| Module | Vai trò gần với Spring Aspect | Nên đọc khi |
| --- | --- | --- |
| `spring-aop` | `Advisor`, `Pointcut`, `Advice` adapter, `ProxyFactory`, JDK/CGLIB dispatch, `@AspectJ` integration | Hầu hết câu hỏi về AOP |
| `spring-context` | `@EnableAspectJAutoProxy`, cấu hình Java, tạo `ApplicationContext` và component scanning | Khai báo aspect/config nhưng proxy không xuất hiện |
| `spring-beans` | `BeanFactory`, `BeanPostProcessor`, singleton creation, initialization callbacks | Bean được tạo quá sớm hoặc sai lifecycle |
| `spring-core` | Ordering metadata, utilities, CGLIB được Spring đóng gói, logging/application startup | Order và proxy internals |
| `spring-tx` | Transaction advisors/interceptors và `@Transactional` | Transaction advice, propagation và rollback |
| `spring-aspects` | AspectJ aspects tích hợp sẵn cho các tính năng Spring đặc biệt | Dùng AspectJ weaving với `@Configurable`/transaction AspectJ mode |
| `spring-test` | TestContext integration, `@SpringJUnitConfig` | Cần reproduce behavior bằng integration test |
| Spring Security modules | Method security advisor/interceptor khi ứng dụng dùng security | `@PreAuthorize`/method security không chạy |

**Đừng đọc mã của Spring Boot auto-configuration thay cho core Spring AOP** khi câu hỏi là “proxy dispatch hoạt động thế nào”. Ngược lại, nếu feature **chỉ hoạt động khi bật Spring Boot starter/properties**, hãy đọc Boot auto-configuration sau khi đã hiểu Spring Framework contract.

## 6. Source map: theo dấu từ @EnableAspectJAutoProxy đến @Around

Trong repository [spring-projects/spring-framework](https://github.com/spring-projects/spring-framework), đường đi tham khảo trong các phiên bản Spring gần đây:

~~~text
spring-context
└─ org.springframework.context.annotation
   ├─ EnableAspectJAutoProxy
   └─ AspectJAutoProxyRegistrar

spring-aop
├─ org.springframework.aop.config
│  └─ AopConfigUtils
├─ org.springframework.aop.aspectj.annotation
│  ├─ AnnotationAwareAspectJAutoProxyCreator
│  ├─ ReflectiveAspectJAdvisorFactory
│  └─ BeanFactoryAspectJAdvisorsBuilder
├─ org.springframework.aop.framework.autoproxy
│  ├─ AbstractAutoProxyCreator
│  ├─ AbstractAdvisorAutoProxyCreator
│  └─ DefaultAdvisorAutoProxyCreator
├─ org.springframework.aop.aspectj
│  ├─ AspectJExpressionPointcut
│  └─ AspectJAroundAdvice
├─ org.springframework.aop.framework
│  ├─ ProxyFactory
│  ├─ AdvisedSupport
│  ├─ DefaultAopProxyFactory
│  ├─ JdkDynamicAopProxy
│  ├─ CglibAopProxy
│  └─ ReflectiveMethodInvocation
└─ org.springframework.aop.support
   └─ AopUtils
~~~

**Đây là bản đồ khái niệm các file/class để tra cứu**, không phải tất cả đều public API hoặc bảo đảm tên và vị trí không thay đổi ở mọi release. Mã `spring-aop` thường tham chiếu các utility ở các module khác. Hãy tìm symbol trong **tag cùng version thực chạy**, không copy snippet nội bộ từ `main` để dùng trong production.

Một số source links trực tiếp có thể mở:
- [AnnotationAwareAspectJAutoProxyCreator.java](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/aspectj/annotation/AnnotationAwareAspectJAutoProxyCreator.java)
- [AbstractAutoProxyCreator.java](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.java)
- [AbstractAdvisorAutoProxyCreator.java](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/autoproxy/AbstractAdvisorAutoProxyCreator.java)
- [AspectJExpressionPointcut.java](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/aspectj/AspectJExpressionPointcut.java)
- [JdkDynamicAopProxy.java](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/JdkDynamicAopProxy.java)
- [CglibAopProxy.java](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/CglibAopProxy.java)
- [ReflectiveMethodInvocation.java](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/ReflectiveMethodInvocation.java)
- [AdvisedSupport.java](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/AdvisedSupport.java)

Trong repository, dùng GitHub “Go to file” để chuyển nhanh đến class tên tương ứng; nếu tag đã chọn không có file ở đường dẫn trên, hãy tìm symbol ở tag đó thay vì suy đoán vị trí.

## 7. Lộ trình đọc source theo một lời gọi thật

Một chuỗi tham khảo từ lúc annotation được xử lý đến invocation:

~~~text
@EnableAspectJAutoProxy
  |
  v
AspectJAutoProxyRegistrar / AopConfigUtils
  |  đăng ký auto-proxy infrastructure theo BeanFactory
  v
AnnotationAwareAspectJAutoProxyCreator
  |  tìm candidate advisors, kể cả @AspectJ-style advice
  v
ReflectiveAspectJAdvisorFactory / AspectJExpressionPointcut
  |  biến metadata @Aspect/@Around thành advisors/pointcuts
  v
AbstractAutoProxyCreator
  |  kiểm bean eligibility rồi wrapIfNecessary(...)
  v
ProxyFactory / AdvisedSupport / DefaultAopProxyFactory
  |  chọn JDK dynamic proxy hoặc CGLIB class-based proxy
  v
JdkDynamicAopProxy / CglibAopProxy
  |  lời gọi runtime tới một method trên proxy
  v
advisor/interceptor chain
  |  chọn những interceptors phù hợp method
  v
ReflectiveMethodInvocation.proceed()
  |  lần lượt đi qua interceptors
  v
target method
~~~

Đường đi thực tế có thể có **nhánh khác** tùy advisor type, static/dynamic pointcut, proxy caching, target source, Spring release và lời gọi method; không phải mọi invocation đều tạo mới `ReflectiveMethodInvocation` theo cùng nhánh. Sơ đồ giúp tìm **điểm dừng debug**, không thay thế đọc source thực tế.

### 7.1. Khi @Aspect chưa trở thành advisor

Bắt đầu từ:
1. `@EnableAspectJAutoProxy` và `AspectJAutoProxyRegistrar`: Spring có nhận configuration enabling không?
2. `AnnotationAwareAspectJAutoProxyCreator.findCandidateAdvisors()`: candidate advisors lấy từ Spring advisor beans và `@AspectJ` aspect definitions như thế nào?
3. `BeanFactoryAspectJAdvisorsBuilder`: aspect bean name có được phát hiện không?
4. `ReflectiveAspectJAdvisorFactory`: advice method có được diễn giải thành `Advisor` không? Pointcut expression có hợp lệ không?

Dấu hiệu cần chụp trong debugger: tên bean aspect, target bean name, loại advisor, method signature, expression, exception khi parse annotation.

### 7.2. Khi bean không được proxy

Chuyển sang:
- `AbstractAutoProxyCreator.postProcessAfterInitialization(...)`;
- `AbstractAutoProxyCreator.wrapIfNecessary(...)`;
- `AbstractAdvisorAutoProxyCreator.findEligibleAdvisors(...)`;
- `AbstractAutoProxyCreator.createProxy(...)`.

Câu hỏi nên ghi lại:
- Auto-proxy creator được đăng ký trong **đúng container** chưa?
- Bean name/class thực tế là gì?
- Bean có bị xem như infrastructure class, skip hoặc không có eligible advisor không?
- Bean có được instantiate trước khi đủ `BeanPostProcessor` tham gia không?
- Có parent/child `ApplicationContext` khiến advisor và target thuộc hai bean factories khác nhau không?

**Đừng tạo thêm bean bằng `getBean(...)` ngay trong callback debug hoặc BeanFactoryPostProcessor** nếu nó làm thay đổi lifecycle; dùng breakpoint/log ở thời điểm thích hợp.

### 7.3. Khi proxy có nhưng method không bị chặn

Kiểm lần lượt:
- `AopUtils.isAopProxy(bean)`/`isJdkDynamicProxy`/`isCglibProxy` để xác định strategy thực.
- `Advised.getAdvisors()` khi proxy hỗ trợ `Advised`.
- `AspectJExpressionPointcut.matches(...)` và các method thuộc `MethodMatcher` khi cần tìm nguyên nhân pointcut không khớp.
- `JdkDynamicAopProxy.invoke(...)` nếu caller đi qua interface proxy.
- Các callback intercept của `CglibAopProxy` nếu caller dùng subclass proxy.
- `AdvisedSupport.getInterceptorsAndDynamicInterceptionAdvice(...)` hoặc API tương ứng ở version được pin.
- `ReflectiveMethodInvocation.proceed()` khi muốn quan sát chain thực thi.

**Lưu ý Spring 7+:** một số API/implementation trong đường hot path được refactor theo version; hãy tìm **symbol ở source tag tương ứng**, không giả định method name và thứ tự call stack luôn giống ví dụ của Spring 6.

### 7.4. Khi advice chạy sai thứ tự

Đọc trước reference **Declaring Advice → Advice Ordering**, rồi mới xem:
- Order metadata trên `@Aspect` (`@Order` hoặc `Ordered`).
- `AnnotationAwareOrderComparator` và logic sorting eligible advisors (nếu version dùng).
- Precedence logic dành cho AspectJ advisors/advice kinds.
- Danh sách advisors **ở proxy cụ thể** và trình tự interceptors match method thực tế.

Không chỉ dựa vào thứ tự liệt kê `@Before`/`@After` methods trong source. Khi hai advice cùng loại trong cùng `@Aspect` cần thứ tự bắt buộc, thường nên gộp hoặc tách aspects với `@Order` rõ.

## 8. Lựa chọn lộ trình theo vấn đề thường gặp

### 8.1. “Không thấy advice @Before/@Around chạy”

**Đọc:**
1. @AspectJ Support;
2. Proxying Mechanisms;
3. Declaring Pointcuts.

**Sau đó xem source:** `AnnotationAwareAspectJAutoProxyCreator` → `AbstractAutoProxyCreator.wrapIfNecessary` → `AspectJExpressionPointcut.matches`.

**Test cần có:** method public non-final gọi qua bean injected proxy làm positive control, cùng một method gọi bằng `new` hoặc self-invocation làm negative control.

**Kết luận có thể có:** chưa đăng ký aspect bean, context sai, auto-proxy creator không chạy, pointcut không match, bean không eligible, caller không đi qua proxy.

### 8.2. “JDK proxy và CGLIB cư xử khác nhau”

**Đọc:** Proxying Mechanisms, đặc biệt final class, final/private method, visibility, module path và self-invocation.

**Xem source:** `DefaultAopProxyFactory` → `JdkDynamicAopProxy` hoặc `CglibAopProxy` → `AopUtils`.

**Kiểm tra:**
- `AopUtils.isJdkDynamicProxy`/`isCglibProxy`;
- Target có interface? Caller inject theo interface hay concrete class?
- Có `proxyTargetClass=true` hoặc XML `proxy-target-class="true"`?
- Implementation method final được invoke qua interface proxy khác với CGLIB subclass override.

**Không kết luận:** CGLIB luôn nhanh hơn JDK hoặc final method luôn không được Spring AOP advise. Chúng khác mechanism; test trên đúng version và contract public API.

### 8.3. “Tại sao this.inner() không nhận transaction/log advice?”

**Đọc:** Proxying Mechanisms → Understanding AOP Proxies.

**Xem source:** `ReflectiveMethodInvocation.proceed` để hiểu interceptor chỉ chạy khi lời gọi đã đi vào proxy, không phải máy ảo Java tự intercept mọi `this.inner()`.

**Kiểm chứng:** gọi `service.inner()` qua Spring proxy rồi gọi `service.outer()` khi outer dùng `this.inner()`. Compare recorder events.

**Giải pháp ưu tiên:** tách method quan trọng thành bean khác và gọi qua dependency injected proxy. Nếu cần bytecode weaving thực sự, xem Using AspectJ with Spring Applications và cấu hình LTW riêng.

### 8.4. “Bean bị khởi tạo sớm, không được auto-proxy”

**Đọc:** BeanPostProcessor & Container Extension Points.

**Xem source:** `AbstractAutoProxyCreator.postProcessAfterInitialization`; lifecycle trong `spring-beans` để tìm thời điểm BeanPostProcessor được đăng ký.

**Kiểm chứng:** startup log có thể chứa `not eligible for auto-proxying`. Xem custom `BeanFactoryPostProcessor`/`BeanPostProcessor` có ép `getBean(...)` trong pha quá sớm không. Đừng nhầm early reference được Spring tạo cho circular dependencies với mọi bean bị khởi tạo sớm.

### 8.5. “@annotation, args, argNames không bind”

**Đọc:** Pointcuts và Declaring Advice → binding advice parameters.

**Xem source:** `ReflectiveAspectJAdvisorFactory` và `AspectJExpressionPointcut`; tìm class xử lý argument binding của Spring ở version cụ thể.

**Kiểm chứng:** annotation phải có runtime retention phù hợp, đặt đúng method; `argNames`/`-parameters`; argument positions/types; JDK interface vs implementation method annotation lookup.

**Phân biệt:** `@annotation(annotationObj)` bind method annotation, `args(arg)` bind runtime arguments, còn `@args(AnnoType)` hỏi về annotation trên runtime type của arguments.

### 8.6. “XML Spring AOP chạy khác @Aspect”

**Đọc:** Schema-based AOP Support và @AspectJ Support.

**Xem source:** namespace parsing ở `spring-aop` và cấu trúc advisors sau khi context khởi tạo. Không nhất thiết đọc nguyên mọi XML parser nếu lỗi thực tế chỉ là pointcut hoặc advisor order.

**Kiểm chứng:** hai `ApplicationContext` độc lập với cùng business class/pointcut; so method result, exception, event count, advisor ordering và proxy type. Không bật cả hai định nghĩa advice trùng lên một context rồi gọi đó là parity.

### 8.7. “@Transactional không mở transaction hoặc propagation sai”

**Đọc:** Transaction Management → Declarative Transaction Management → Annotation-driven settings. Sau đó mới quay lại Proxying Mechanisms để xác định self-invocation.

**Xem source:** các entry points trong `spring-tx` như `TransactionInterceptor`, `BeanFactoryTransactionAttributeSourceAdvisor`, `AnnotationTransactionAttributeSource` và transaction management configuration liên quan; nối từ transaction advisor sang Spring AOP invocation chain.

**Kiểm chứng:** transaction manager có trong đúng context? Bean thực sự được proxied? Method call qua proxy? Transaction attribute/propagation có match method được gọi không? Có transaction outer đang hoạt động khiến inner method tưởng như được intercept?

**Đừng hiểu nhầm:** khi outer `@Transactional` mở transaction rồi gọi `this.inner()`, inner code có thể vẫn chạy trong transaction outer, nhưng **transaction interceptor riêng của inner** không được kích hoạt để áp propagation khác như `REQUIRES_NEW`.

### 8.8. “Advice quá chậm hoặc khởi động quá lâu”

**Đọc:** Spring AOP APIs, Proxying Mechanisms, bean lifecycle.

**Xem source:** `AbstractAutoProxyCreator.createProxy` và `ProxyFactory` khi đo creation; `AdvisedSupport` và `ReflectiveMethodInvocation` khi đo per-invocation chain. Inspect **logic trong advice**: log, network, database, reflection, annotation resolution.

**Đo:** JFR/profiler hoặc JMH microbenchmark có đối chứng direct call vs proxied call và cùng số advisors. Không bịa số ns/op. Phân biệt cost tạo proxy (thường trong bean lifecycle) với cost của mỗi method invocation; no-op advice khác xa advice thực hiện I/O.

## 9. Một ví dụ Java 17+ để neo việc đọc tài liệu/source

Đây là một mini-project dùng **Spring Framework 6.2.19**, một `@Aspect` bean, class-based CGLIB proxy và recorder. Nó đủ để bạn đặt breakpoint ở Spring internals và quan sát hai đường gọi: method được gọi qua proxy và self-invocation không đi qua proxy.

**Cấu trúc project:**

~~~text
build.gradle
src/main/java/demo/reader/
    CheckoutService.java
    CallEvents.java
    TraceAspect.java
    DemoConfig.java
    Main.java
src/test/java/demo/reader/
    AopReadingTests.java
~~~

### 9.1. build.gradle

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
    implementation 'org.springframework:spring-context:6.2.19'
    implementation 'org.springframework:spring-aop:6.2.19'
    implementation 'org.aspectj:aspectjweaver:1.9.22.1'

    testImplementation platform('org.junit:junit-bom:5.11.3')
    testImplementation 'org.junit.jupiter:junit-jupiter'
}

application {
    mainClass = 'demo.reader.Main'
}

test {
    useJUnitPlatform()
}
~~~

Trong một ứng dụng Spring Boot đã có BOM, hãy dùng version Spring/AspectJ do dependency management phù hợp cung cấp và pin source đúng theo runtime. Không lắp lẫn module Spring ở các dòng 6.x/7.x chỉ để chạy theo ví dụ.

### 9.2. Service có self-invocation

**`src/main/java/demo/reader/CheckoutService.java`**

~~~java
package demo.reader;

public class CheckoutService {
    public String checkout(int order) {
        return "ok:" + order;
    }

    public String checkoutInternally(int order) {
        return this.checkout(order);
    }
}
~~~

### 9.3. Event recorder

**`src/main/java/demo/reader/CallEvents.java`**

~~~java
package demo.reader;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CallEvents {
    private final CopyOnWriteArrayList<String> items =
            new CopyOnWriteArrayList<>();

    public void record(String event) {
        items.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(items);
    }

    public void clear() {
        items.clear();
    }
}
~~~

Recorder tạo event để test, không tạo I/O làm nhiễu demo. Đây chỉ là công cụ học tập; với log/audit production phải xem throughput, policy bảo vệ dữ liệu và transaction semantics.

### 9.4. @Aspect và @Around

**`src/main/java/demo/reader/TraceAspect.java`**

~~~java
package demo.reader;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TraceAspect {
    private final CallEvents events;

    public TraceAspect(CallEvents events) {
        this.events = events;
    }

    @Around("execution(* demo.reader.CheckoutService.checkout(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        events.record("before");
        try {
            Object result = joinPoint.proceed();
            events.record("after");
            return result;
        } catch (Throwable ex) {
            events.record("error");
            throw ex;
        }
    }
}
~~~

Đây là `@AspectJ` annotation style, nhưng cơ chế kích hoạt vẫn là **Spring AOP proxy**, không phải AspectJ load-time weaving. `@Around` gọi `proceed()` đúng một lần và giữ nguyên return/exception.

### 9.5. Spring configuration

**`src/main/java/demo/reader/DemoConfig.java`**

~~~java
package demo.reader;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class DemoConfig {
    @Bean
    CheckoutService checkoutService() {
        return new CheckoutService();
    }

    @Bean
    CallEvents callEvents() {
        return new CallEvents();
    }

    @Bean
    TraceAspect traceAspect(CallEvents events) {
        return new TraceAspect(events);
    }
}
~~~

Hai điều cần quan sát khi debug:
- `@EnableAspectJAutoProxy` đã đăng ký auto-proxy infrastructure;
- `@Bean TraceAspect` biến class `@Aspect` thành Spring bean, cho phép `AnnotationAwareAspectJAutoProxyCreator` tìm advice.

### 9.6. Main

**`src/main/java/demo/reader/Main.java`**

~~~java
package demo.reader;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                new AnnotationConfigApplicationContext(DemoConfig.class)) {
            CheckoutService service = context.getBean(CheckoutService.class);
            CallEvents events = context.getBean(CallEvents.class);

            System.out.println("proxy=" + AopUtils.isAopProxy(service));
            System.out.println("cglib=" + AopUtils.isCglibProxy(service));

            System.out.println("direct=" + service.checkout(7));
            System.out.println("events=" + events.snapshot());

            System.out.println("internal=" + service.checkoutInternally(9));
            System.out.println("eventsAfterInternal=" + events.snapshot());
        }
    }
}
~~~

Chạy khi project có Gradle wrapper:

~~~bash
./gradlew run
~~~

Windows:

~~~powershell
.\gradlew.bat run
~~~

Expected output của những lệnh `System.out.println` (không tính log Gradle/Spring):

~~~text
proxy=true
cglib=true
direct=ok:7
events=[before, after]
internal=ok:9
eventsAfterInternal=[before, after]
~~~

Đây là **output dự kiến theo source**, không phải kết quả đã chạy trên máy người đọc. Method `checkout(7)` đi qua Spring proxy nên có hai events. Method `checkoutInternally(9)` gọi `this.checkout(9)` trong target; không có thêm event dù business method vẫn trả `ok:9`.

## 10. Test JUnit Jupiter: đọc runtime evidence thay vì chỉ đọc code

**`src/test/java/demo/reader/AopReadingTests.java`**

~~~java
package demo.reader;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class AopReadingTests {

    @Test
    void springBeanIsProxiedAndAroundAdviceRuns() {
        try (var context =
                new AnnotationConfigApplicationContext(DemoConfig.class)) {
            CheckoutService bean = context.getBean(CheckoutService.class);
            CallEvents events = context.getBean(CallEvents.class);

            assertTrue(AopUtils.isAopProxy(bean));
            assertTrue(AopUtils.isCglibProxy(bean));
            assertTrue(bean instanceof Advised);
            assertTrue(((Advised) bean).getAdvisors().length >= 1);

            assertEquals("ok:7", bean.checkout(7));
            assertEquals(List.of("before", "after"), events.snapshot());
        }
    }

    @Test
    void selfInvocationDoesNotEnterProxyAgain() {
        try (var context =
                new AnnotationConfigApplicationContext(DemoConfig.class)) {
            CheckoutService bean = context.getBean(CheckoutService.class);
            CallEvents events = context.getBean(CallEvents.class);

            assertEquals("ok:9", bean.checkoutInternally(9));
            assertTrue(events.snapshot().isEmpty());
        }
    }

    @Test
    void rawJavaObjectIsNotSpringAopProxy() {
        CheckoutService raw = new CheckoutService();

        assertFalse(AopUtils.isAopProxy(raw));
        assertEquals("ok:10", raw.checkout(10));
    }
}
~~~

Lệnh chạy:

~~~bash
./gradlew test --tests demo.reader.AopReadingTests
~~~

Đây là test dùng Spring `ApplicationContext` thật, không tự mock AOP engine. Nếu source/config đúng và dependencies có thể tải, **kỳ vọng 3 tests pass**; không được trình bày expected outcome như một báo cáo test đã thực thi.

Khi test chạy trong IDE, có thể đặt breakpoint từ bên ngoài ở:
1. `AbstractAutoProxyCreator.wrapIfNecessary` trong giai đoạn tạo bean;
2. `AnnotationAwareAspectJAutoProxyCreator.findCandidateAdvisors` khi Spring xác định candidate advisors;
3. `AspectJExpressionPointcut.matches` để quan sát việc lọc method;
4. `CglibAopProxy` interceptor callback khi `service.checkout(7)` đi vào;
5. `ReflectiveMethodInvocation.proceed` trước và sau invocation của `TraceAspect.around(...)`;
6. `CheckoutService.checkout` để xem số lần target thực thi.

Với test self-invocation, debugger có thể không dừng ở Spring proxy callback cho `this.checkout`; đó là **bằng chứng của proxy boundary**, không phải lỗi advisor.

## 11. Cách đọc source code hiệu quả bằng IDE

### 11.1. Ưu tiên Attach Sources của đúng artifact

Nếu dùng IntelliJ IDEA/Eclipse/VS Code Java:
- Mở class `ProxyFactory` hoặc `AbstractAutoProxyCreator` bằng “Go to Declaration”.
- IDE có thể tự tải `spring-aop-<version>-sources.jar` nếu cấu hình dependency resolution hợp lệ.
- Xác minh sources version **khớp JAR binary thực tế**. Nếu IDE mở một source decompiled hoặc sai version, line numbers và method flows có thể khác runtime.
- Đọc javadoc/method contract trước, rồi vào method implementation.
- Với internal classes, đặt breakpoint và xem stack frames, không copy private API làm production integration point.

Nếu muốn theo dõi Spring release code trên GitHub, mở tag/commit tương ứng; `main` có thể đã đổi so với artifact.

### 11.2. Từ public API xuống private implementation

Một trình tự đọc ngắn hơn đọc file 600–800 lines liên tục:

~~~text
API được ứng dụng gọi
  └── `AopUtils` / `ProxyFactory` / @EnableAspectJAutoProxy
        ↓
Interface/abstract contract
  └── Advisor / Advice / Pointcut / Advised / AopProxy
        ↓
Implementation cụ thể
  └── AnnotationAwareAspectJAutoProxyCreator
      JdkDynamicAopProxy / CglibAopProxy
        ↓
Điểm nhạy cảm cần hiểu
  └── eligible advisors / method matcher / invocation.proceed()
        ↓
Test của Spring Framework tương ứng (nếu cần)
~~~

Thay vì grep mọi từ “AOP” trong repository, tìm **type name** hoặc **method name** từ stack trace. Nếu muốn biết “tại sao advisor không match”, đọc `matches`; nếu muốn biết “tại sao không tạo proxy”, đọc `wrapIfNecessary`; nếu muốn biết “thứ tự gọi”, đọc `proceed` và advisor ordering, không bắt đầu từ `@Aspect` annotation declaration.

### 11.3. Tìm tests trong repository Spring

Spring Framework có các tests trong từng module, ví dụ `spring-aop/src/test/java`. Test cases rất có giá trị khi cần hiểu một edge case không được reference mô tả theo tất cả biến thể.

Cách tìm:
- Tìm bằng symbol như `AnnotationAwareAspectJAutoProxyCreator` hoặc `AspectJExpressionPointcut`.
- Tìm test name mô tả `proxyTargetClass`, `selfInvocation`, `@annotation` hoặc advisor precedence.
- Đọc test setup để biết version/config/bean definitions, sau đó xem assertion.
- Không coi một test version `main` là bảo đảm behavior cho artifact `6.1.x` đang chạy.

Tests của Spring là **bằng chứng version-specific**, còn reference API/Javadoc là nơi quyết định hợp đồng được hỗ trợ. Nếu phát hiện khác biệt, pin bug reproduction trên phiên bản bạn dùng trước khi mở issue.

## 12. Học bằng cách đặt breakpoint đúng thứ tự

| Bạn muốn biết | Breakpoint/source entry | Biến nên xem |
| --- | --- | --- |
| AOP registration có chạy? | `AspectJAutoProxyRegistrar`, `AopConfigUtils` | BeanFactory, infrastructure bean definitions |
| Spring đã phát hiện aspect? | `AnnotationAwareAspectJAutoProxyCreator.findCandidateAdvisors` | Aspect bean names, candidate advisor count |
| Khi nào bean được proxy? | `AbstractAutoProxyCreator.wrapIfNecessary` | beanName, bean class, skip flag, specific interceptors |
| Chọn JDK hay CGLIB? | `DefaultAopProxyFactory.createAopProxy` | proxyTargetClass, interfaces, target class |
| Annotation/pointcut match? | `AspectJExpressionPointcut.matches` | method, targetClass, args, pointcut expression |
| Advisors trên bean là gì? | `Advised.getAdvisors` ở runtime | Advisor class, order, pointcut |
| Method gọi tới proxy thế nào? | `JdkDynamicAopProxy.invoke` hoặc CGLIB callbacks | proxy, method, args, target |
| Chuỗi interceptor chạy ra sao? | `ReflectiveMethodInvocation.proceed` | currentInterceptorIndex, chain length, current interceptor |
| Around gọi tiếp target lúc nào? | `AspectJAroundAdvice`, `TraceAspect.around` | JoinPoint, proceed, target invocation |
| Self-invocation bỏ qua proxy? | Breakpoint ở proxy callback và target `checkout` | Đối chiếu stack traces của call direct vs self |
| Thứ tự advice sai? | Advisor sorting/precedence trong auto-proxy creator | Advisors order và event sequence |

Các tên method/source entry là **gợi ý trên dòng Spring gần đây**; implementation có thể đổi. Trước khi đặt breakpoint, xác minh method tồn tại ở exact source tag runtime.

**Cảnh báo khi debug production:** bật `TRACE` toàn `org.springframework` hoặc gắn debugger vào đường hot path có thể tăng chi phí và làm lộ method arguments. Nên tái hiện bằng integration test hoặc staging với dữ liệu an toàn.

## 13. Một phiên điều tra hoàn chỉnh: “Có @Aspect nhưng không log”

Giả sử ứng dụng có:

~~~java
@Aspect
public class AuditAspect {
    @Before("execution(* app.service.InvoiceService.save(..))")
    public void audit() {
        // logic log
    }
}
~~~

Đây là fragment, cần import `Aspect`/`Before` và cấu hình bean phù hợp. Bạn thấy `InvoiceService.save` được gọi, nhưng không thấy log.

**Bước 1 — kiểm Spring context**
- `AuditAspect` được đăng ký bằng `@Bean` hay `@Component` thực sự được scan?
- `@EnableAspectJAutoProxy`/schema AOP hoặc Boot AOP setup đã bật?
- Aspect và service thuộc cùng context owner? Parent-child contexts có làm advisor nằm ở nơi khác không?
- `InvoiceService` có bị tạo sớm trước BPP không?

**Bước 2 — kiểm proxy**
- Object caller giữ có phải Spring bean injected/reference không?
- `AopUtils.isAopProxy(bean)` true/false?
- JDK/CGLIB type có đúng contract method được gọi?
- `Advised.getAdvisors()` có custom advisor liên quan không?

**Bước 3 — kiểm lời gọi**
- Caller gọi `bean.save()` qua proxy hay `this.save()` bên trong target?
- Có object `new InvoiceService()` được tạo ở lớp khác?
- Method target có `private`, `final`, overload hoặc pointcut mismatch?
- Với annotation binding, annotation nằm ở interface hay implementation; runtime retention thế nào?

**Bước 4 — kiểm source**
- Không có candidate advisor: xem `AnnotationAwareAspectJAutoProxyCreator`/`ReflectiveAspectJAdvisorFactory`.
- Có advisor nhưng không proxy: xem `AbstractAutoProxyCreator.wrapIfNecessary`/`findEligibleAdvisors`.
- Có proxy nhưng không intercept: xem `AspectJExpressionPointcut.matches`, JDK/CGLIB invocation path.
- Có intercept nhưng log không xuất hiện: xem code advice và logger level/sink/exception; phân biệt **“advice không chạy”** với **“log bị lọc”**.

**Bước 5 — khóa chẩn đoán bằng test**
- Đối chứng dương tính: lời gọi method qua Spring-injected proxy tạo event.
- Đối chứng âm tính: raw object hoặc self-invocation không tạo event trong Spring proxy mode.
- Test một overload/annotation case có chủ đích.
- Nếu sửa config làm event xuất hiện hai lần, kiểm trùng advisor hoặc aspect registration.

**Kết luận nên viết trong issue** theo cấu trúc: “Spring version + proxy strategy + AOP registration + bean owner + method signature + pointcut + actual advisor list + minimal test case + observed vs expected behavior.” Đây là thông tin đủ giúp người đọc source không phải đoán lại từ đầu.

## 14. Phân biệt Spring AOP và AspectJ weaving khi đọc docs

Spring có thể dùng `@AspectJ` annotation style, nhưng điều đó không đồng nghĩa bytecode được AspectJ weave. Hai cơ chế:

| Câu hỏi | Spring AOP proxy | AspectJ CTW/LTW được Spring tích hợp |
| --- | --- | --- |
| Bean phải thuộc Spring? | Thông thường target phải qua Spring auto-proxy | Type được weave có thể có instance tạo ngoài Spring |
| Lời gọi nội bộ `this.foo()` | Không đi qua proxy | Execution join point đã weave có thể được advise |
| Hạ tầng kích hoạt | `@EnableAspectJAutoProxy`/Spring advisor configuration | AspectJ compiler/weaver và tích hợp classloader/javaagent phù hợp |
| Cấu hình file | Spring Java/XML config | Thường có `META-INF/aop.xml` cho AspectJ LTW |
| Nên đọc source nào | `spring-aop` + bean lifecycle | Spring LTW integration ở `spring-context` và AspectJ weaving infrastructure |
| Dùng lúc nào | Method cross-cutting trên Spring beans | Cần advise join points ngoài proxy hoặc object không Spring-managed |

Nếu nhu cầu chỉ là “`@Transactional` self-invocation không chạy”, **đọc Spring proxy semantics trước**. Không bắt đầu bằng việc bật AspectJ agent nếu service boundary có thể thiết kế rõ bằng bean khác. Khi thực sự dùng AspectJ mode, cần thêm các phần như `spring-aspects`, AspectJ configuration và version compatibility theo feature, chứ không chỉ đặt `@EnableAspectJAutoProxy`.

## 15. Theo dõi precedence trong source thực

Thay vì lặp lại trace `outer/inner`, hãy đối chiếu các tầng ở **đúng Spring source tag**:

1. Đọc reference **Advice Ordering**: `@Order` số nhỏ có precedence cao, đi vào trước/đi ra sau; hai advice cùng loại trong một `@Aspect` không có thứ tự dựa trên Java source declaration.
2. Xem metadata `Ordered` và logic sort eligible advisors; phân biệt XML `<aop:aspect>` với custom aspects và transaction/cache/security advisors.
3. Inspect `Advised.getAdvisors()` của bean thực tế, sau đó breakpoint ở nơi dựng method interceptor chain và tại `ReflectiveMethodInvocation.proceed()`; theo dõi interceptor index và dynamic matching.
4. So sánh trace **success, exception, short-circuit, retry**. `@Around` có thể không gọi `proceed()` hoặc gọi nhiều lần, nên advisor list không chứng minh số lần target chạy.

## 16. Tránh những bẫy thường gặp khi nghiên cứu source

- **Dựa trên code `main` cho release cũ:** API và auto-proxy internals có thể đổi. Pin tag/version và đọc đúng JAR/sources.
- **Nhầm `@AspectJ` style với AspectJ LTW:** Spring AOP mặc định tạo proxies; AspectJ agent là cơ chế khác.
- **Chỉ nhìn `@Aspect` class:** chưa chứng minh aspect là Spring bean và advisor đã được đưa vào proxy.
- **Đọc từ `CglibAopProxy` ngay khi lỗi là bean creation:** lúc đó nên vào `AbstractAutoProxyCreator`/BPP phase.
- **Nhìn số advisors mà không kiểm từng method match:** advisors có thể chỉ áp dụng cho vài methods.
- **Kết luận advice sai khi thực ra logger bị tắt:** dùng event recorder/counter trong test để đo method entry.
- **Không tách proxy và target:** `AopUtils.getTargetClass(bean)` cho biết target type, không chứng minh object đang là raw instance.
- **Tin rằng mọi `final` method đều không được advise:** CGLIB không override final, nhưng JDK interface proxy có thể chặn lời gọi interface tới implementation final.
- **Dựa vào private/internal Spring class như API ổn định:** hãy dùng public `ProxyFactory`, `Advisor`, `Advised`, `AopUtils` khi code ứng dụng cần can thiệp có chủ đích.
- **Không kiểm ApplicationContext ownership:** child BPP không tự post-process parent-owned beans.
- **Lấy method output đúng làm bằng chứng aspect chạy:** target vẫn chạy bình thường ngay cả khi bypass advice.
- **Đưa log I/O vào benchmark về proxy overhead:** đang đo logger/network thay vì interceptor mechanics.
- **Không đọc tests Spring có cùng edge case:** bỏ qua nguồn minh chứng thực tế tốt hơn các bài blog mô tả một nửa trường hợp.
- **Không lưu giả thuyết, bước tái hiện và kết quả:** lần điều tra sau lại đọc từ đầu.

## 17. Checklist 1 trang: chọn nơi mở tiếp theo

| Câu hỏi cụ thể | Đọc docs | Source mở tiếp |
| --- | --- | --- |
| `@Aspect` không được phát hiện | @AspectJ Support | `AnnotationAwareAspectJAutoProxyCreator` |
| Advice method không biến thành advisor | Declaring Advice | `ReflectiveAspectJAdvisorFactory` |
| Bean không được proxy | Proxying + BeanPostProcessor | `AbstractAutoProxyCreator` |
| Method pointcut không match | Declaring Pointcuts | `AspectJExpressionPointcut` |
| Interface proxy bị lỗi cast | Proxying Mechanisms | `JdkDynamicAopProxy` |
| Class proxy/final/private issue | Proxying Mechanisms | `CglibAopProxy` |
| Không biết chain gọi advice ra sao | Spring AOP APIs | `ReflectiveMethodInvocation` |
| Muốn tự thêm advisors | Spring AOP APIs | `ProxyFactory`/`AdvisedSupport` |
| Advice order sai | Declaring Advice/Schema AOP | Advisor sorting và `Ordered` metadata |
| `@Transactional` không đúng | Transaction declarative docs | `TransactionInterceptor` và transaction advisor |
| Bean tạo trước BPP | Container Extension Points | `spring-beans` lifecycle + `AbstractAutoProxyCreator` |
| Root/child context khác nhau | Spring context/bean lifecycle docs | Bean registration/auto-proxy creator của từng container |
| Unit test không thấy advice | Spring TestContext | `@SpringJUnitConfig` + `AopUtils` |
| Startup/proxy overhead cao | AOP API + BeanPostProcessor | `createProxy`/`ProxyFactory` |
| Muốn advise `new` object | Using AspectJ with Spring | LTW integration, không phải default Spring proxy |

Một cách học hiệu quả là **mỗi tuần chọn một tình huống thực và hoàn tất vòng**: đọc reference, code tối thiểu, đặt breakpoint, xác định source method responsible, viết test dương tính/âm tính, rồi ghi lại kết luận.

## 18. Ôn tập

**Câu 1.** Muốn hiểu tại sao `this.inner()` không nhận `@Around`, mở trang nào trước?

Mở **Proxying Mechanisms**, phần Understanding AOP Proxies. Spring AOP dựa vào proxy; lời gọi `this.inner()` trên target không đi qua proxy. Sau đó mới xem source invocation chain nếu cần.

**Câu 2.** Phần nào của Spring source thích hợp nhất khi `@Aspect` là bean nhưng target không có proxy?

Xem `AnnotationAwareAspectJAutoProxyCreator`, `AbstractAutoProxyCreator.wrapIfNecessary` và advisor eligibility, đồng thời kiểm bean lifecycle/context.

**Câu 3.** Muốn biết `@annotation(tracked)` vì sao không match, mở đâu?

Đọc Declaring Pointcuts/Advice Parameter Binding rồi xem `AspectJExpressionPointcut`, annotation resolution và advisor factory ở đúng Spring version.

**Câu 4.** Tại sao nên pin source theo tag/version?

Vì `main` phát triển liên tục; code runtime có thể không cùng implementation, API, breakpoint và semantics với latest source.

**Câu 5.** Bean `isAopProxy=true` có nghĩa mọi custom `@Aspect` đều đã vào chain chưa?

Không. Proxy có thể do advisor khác. Cần xem `Advised.getAdvisors()`, pointcut matching và event thực tế từ advice.

**Câu 6.** Chọn đọc `JdkDynamicAopProxy` hay `CglibAopProxy` bằng cách nào?

Đọc runtime proxy type qua `AopUtils.isJdkDynamicProxy`/`isCglibProxy` rồi theo đúng implementation. Không đoán từ target class name.

**Câu 7.** Nếu muốn tìm chỗ một interceptor gọi interceptor kế tiếp, nên xem class nào?

`ReflectiveMethodInvocation.proceed()` và các implementation invocation tương ứng, theo phiên bản Spring đang dùng.

**Câu 8.** Khi `@Transactional` không nhận `REQUIRES_NEW` ở self-call, có chắc code inner không chạy trong transaction nào?

Không. Outer có thể đang mở transaction; vấn đề là interceptor riêng của inner bị bypass, nên propagation mới không được áp dụng.

**Câu 9.** Cùng method trả đúng kết quả nhưng không có log, có kết luận aspect không chạy được không?

Chưa. Cần xác minh logging sink/level và dùng recorder/counter kiểm method advice entry, đồng thời xem proxy/advisor.

**Câu 10.** Nên đọc source implementation trước hay reference trước?

Thường đọc **reference để nắm contract**, chạy test có quan sát, rồi đọc đúng source method để hiểu behavior ở version cụ thể. Với bug đã có stack trace, có thể bắt đầu từ symbol trong stack trace nhưng vẫn đối chiếu contract của docs.

## 19. Tài liệu chính thức và mã nguồn tra cứu

**Reference**
- [Spring AOP Overview](https://docs.spring.io/spring-framework/reference/core/aop.html)
- [@AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html)
- [Declaring Pointcuts](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html)
- [Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Schema-based AOP](https://docs.spring.io/spring-framework/reference/core/aop/schema.html)
- [Spring AOP APIs](https://docs.spring.io/spring-framework/reference/core/aop-api.html)
- [Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html)
- [Declarative Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- [Using AspectJ with Spring Applications](https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html)

**Source code**
- [Spring Framework repository](https://github.com/spring-projects/spring-framework)
- [AnnotationAwareAspectJAutoProxyCreator](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/aspectj/annotation/AnnotationAwareAspectJAutoProxyCreator.java)
- [AbstractAutoProxyCreator](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/autoproxy/AbstractAutoProxyCreator.java)
- [AspectJExpressionPointcut](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/aspectj/AspectJExpressionPointcut.java)
- [JdkDynamicAopProxy](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/JdkDynamicAopProxy.java)
- [CglibAopProxy](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/CglibAopProxy.java)
- [ReflectiveMethodInvocation](https://github.com/spring-projects/spring-framework/blob/main/spring-aop/src/main/java/org/springframework/aop/framework/ReflectiveMethodInvocation.java)

Đường dẫn source `/blob/main/` dùng để tra cứu hiện tại; để debug ứng dụng thực tế, **đổi sang tag/commit tương ứng chính xác với dependency version** và đính kèm Sources JAR cùng version cho IDE.


</details>

- [Quay lại đầu trang](#back-to-top)
