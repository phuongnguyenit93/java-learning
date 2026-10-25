<a id="back-to-top"></a>

# Cấu hình XML

## Menu
- [Namespace aop và đăng ký bean phụ thuộc](#xml-aop-configuration-001)
- [aop:aspectj-autoproxy cho aspect dùng annotation](#xml-aop-configuration-002)
- [aop:config và khai báo aop:aspect](#xml-aop-configuration-003)
- [Pointcut có tên và pointcut-ref trong XML](#xml-aop-configuration-004)
- [aop:before, after-returning, after-throwing, after và around](#xml-aop-configuration-005)
- [Ràng buộc tham số, returning và throwing trong XML](#xml-aop-configuration-006)
- [Cấu hình order và khác biệt thứ tự khai báo so với @Aspect](#xml-aop-configuration-007)
- [proxy-target-class và expose-proxy trong cấu hình XML](#xml-aop-configuration-008)
- [Thoát ký tự và dùng and, or, not trong biểu thức](#xml-aop-configuration-009)
- [Kết hợp XML với Java configuration mà không tạo hành vi trùng lặp](#xml-aop-configuration-010)
- [Chuyển đổi cấu hình XML sang annotation và kiểm tra tương đương](#xml-aop-configuration-011)

## <a id="xml-aop-configuration-001">Namespace aop và đăng ký bean phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Namespace `aop` và đăng ký bean phụ thuộc trong Spring AOP bằng XML

## Mục tiêu

Trong một ứng dụng Spring dùng cấu hình XML, viết một lớp chứa phương thức advice vẫn chưa đủ để lời gọi nghiệp vụ được Spring AOP chặn. Cần phối hợp đúng **XML namespace `aop`**, cơ chế tạo proxy và các bean liên quan: bean nghiệp vụ (target), bean chứa advice, các bean mà aspect phụ thuộc, và bean advice riêng khi dùng `<aop:advisor>`.

Bài này tập trung vào Spring AOP trong Spring Framework: đọc một cấu hình XML, xác định bean nào cần đăng ký, hiểu Spring nối pointcut với advice ra sao, và kiểm tra hành vi của proxy. Đây là cấu hình **Spring AOP dựa trên proxy**, không phải cấu hình AspectJ bytecode weaving.

Sau bài học, bạn có thể:

- Khai báo `xmlns:aop` và `spring-aop.xsd` hợp lệ trong `<beans>`.
- Dùng `<aop:config>`, `<aop:pointcut>`, `<aop:aspect>`, `<aop:around>` với một Java class advice thông thường.
- Đăng ký target, aspect và bean dependency bằng `<bean>` / `ref` / `constructor-arg`, không nhầm `ref` của aspect với `pointcut-ref` hay `advice-ref`.
- Phân biệt cấu hình aspect kiểu XML schema và cấu hình lớp `@Aspect` qua `<aop:aspectj-autoproxy/>`.
- Chẩn đoán bean chưa được quản lý, advice không chạy, tham chiếu sai, self-invocation và lựa chọn kiểu proxy.

## 1. Khai báo namespace `aop` trong Spring XML

Một file cấu hình XML sử dụng `aop:` cần khai báo namespace và địa chỉ schema:

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

    <!-- Định nghĩa bean Spring và cấu hình AOP tại đây. -->

</beans>
~~~

Ở đây:

1. `xmlns` mặc định trỏ đến beans namespace để dùng `<bean>`, `<constructor-arg>`, `<property>`.
2. `xmlns:aop` khai báo prefix `aop` gắn với Spring AOP namespace.
3. `xsi:schemaLocation` ghép từng namespace URI với URL XSD tương ứng. Thiếu cặp schema của `aop` có thể làm XML không được parser/schema validator hiểu như mong đợi.
4. Namespace `aop` **cung cấp cú pháp cấu hình**; nó không tự tạo aspect hay target bean chỉ vì có `xmlns:aop`.

Trong dự án dùng Maven, tối thiểu cho ví dụ ở phần sau có `spring-context`, `spring-aop` và `aspectjweaver` tương thích. Với Spring AOP kiểu `@AspectJ` và pointcut AspectJ expression, thư viện AspectJ phục vụ parsing/matching, trong khi runtime interception vẫn là Spring proxy; ví dụ không cần AspectJ compiler hoặc load-time weaving.

## 2. Hai con đường khai báo Spring AOP bằng XML

Spring XML hỗ trợ hai cơ chế khai báo AOP. Với **schema-based AOP**, `<aop:config>` chứa pointcut và các phần tử advice; bean Java chứa method advice có thể là POJO, không cần `@Aspect`. Với **@AspectJ style bật bằng XML**, `<aop:aspectj-autoproxy/>` nhận diện các bean đã đăng ký có `@Aspect` và annotation `@Before`, `@Around`... trong Java.

| Cấu hình | Nơi khai báo quy tắc | Điều bắt buộc |
| --- | --- | --- |
| `<aop:config>` / `<aop:aspect ref="auditBean">` | Pointcut, loại advice và method mapping nằm ở XML | Bean target và bean chứa Java advice method |
| `<aop:aspectj-autoproxy/>` | Pointcut và advice annotation nằm trong `@Aspect` Java | Target bean, aspect bean, hạ tầng @AspectJ được bật |

Trong cả hai trường hợp, caller phải đi qua **Spring AOP proxy**. Không đăng ký cùng một hành vi hai lần qua hai cơ chế. Bài **“aop:aspectj-autoproxy cho aspect dùng annotation”** kiểm chứng riêng cách bật @Aspect và đăng ký aspect bean.

## 3. Mối quan hệ giữa các bean và thẻ AOP

Một sơ đồ điển hình:

~~~text
Spring ApplicationContext
    |
    +-- <bean id="orderService" class="...DefaultOrderService"/>
    |       |
    |       +--> target được Spring AOP proxy nếu pointcut match
    |
    +-- <bean id="auditFormatter" class="...AuditFormatter"/>
    |       |
    |       +--> bean phụ thuộc được inject cho auditAspectBean
    |
    +-- <bean id="auditAspectBean" class="...AuditAspect">
    |       +-- constructor-arg ref="auditFormatter"
    |
    +-- <aop:config>
            +-- <aop:pointcut id="serviceCalls" .../>
            +-- <aop:aspect ref="auditAspectBean">
                    +-- <aop:around pointcut-ref="serviceCalls"
                                    method="around"/>
~~~

Phân biệt các thuộc tính:

| Thành phần | Tham chiếu đến | Tác dụng |
| --- | --- | --- |
| `<aop:aspect ref="auditAspectBean">` | **Spring bean ID** chứa method advice | Chỉ định đối tượng triển khai aspect |
| `<aop:around method="around">` | **Tên method** trên bean aspect | Chọn implementation của around advice |
| `<aop:around pointcut-ref="serviceCalls">` | **Pointcut ID**, không phải bean ID | Chọn join point sẽ áp dụng advice |
| `<constructor-arg ref="auditFormatter">` | **Spring bean ID** hỗ trợ aspect | Inject dependency vào aspect |
| `<aop:advisor advice-ref="interceptorBean">` | **Spring bean ID** implement advice interface | Cấu hình advisor kiểu Spring AOP API |

Nói cách khác, ID được dùng trong cùng một chuỗi ký tự không có nghĩa đó là cùng loại đối tượng. Spring quản lý lifecycle và dependency injection cho các bean, còn `<aop:config>` nối pointcut với advice để áp dụng proxy.

## 4. Ví dụ chạy được: `<aop:config>` với bean phụ thuộc

Ví dụ sử dụng Java 17, Spring Framework 6.1.15, Maven, và **không dùng `@Aspect`**. Aspect nhận một bean `AuditFormatter` qua constructor để minh họa chính xác quan hệ phụ thuộc.

Cấu trúc:

~~~text
spring-xml-aop-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── demo/
        │       ├── Application.java
        │       ├── OrderService.java
        │       ├── DefaultOrderService.java
        │       ├── AuditFormatter.java
        │       └── AuditAspect.java
        └── resources/
            └── applicationContext.xml
~~~

### 4.1. Maven dependencies

**pom.xml**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-xml-aop-demo</artifactId>
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
                    <mainClass>demo.Application</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.2. Interface và target bean

**src/main/java/demo/OrderService.java**

~~~java
package demo;

public interface OrderService {
    String place(String orderId);
    String reject(String orderId);
}
~~~

**src/main/java/demo/DefaultOrderService.java**

~~~java
package demo;

public class DefaultOrderService implements OrderService {
    @Override
    public String place(String orderId) {
        System.out.println("service: place " + orderId);
        return "accepted " + orderId;
    }

    @Override
    public String reject(String orderId) {
        throw new IllegalArgumentException("rejected " + orderId);
    }
}
~~~

Lớp `DefaultOrderService` không cần `@Service` vì sẽ được đăng ký bằng `<bean>` trong XML. `OrderService` giúp ví dụ dùng JDK interface-based proxy.

### 4.3. Bean phụ thuộc và Java class triển khai advice

**src/main/java/demo/AuditFormatter.java**

~~~java
package demo;

public class AuditFormatter {
    public String label(String method) {
        return "audit: " + method;
    }
}
~~~

**src/main/java/demo/AuditAspect.java**

~~~java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;

public class AuditAspect {
    private final AuditFormatter formatter;

    public AuditAspect(AuditFormatter formatter) {
        this.formatter = formatter;
    }

    // Được aop:around trong XML gọi; không cần @Around/@Aspect.
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String operation = joinPoint.getSignature().getName();
        System.out.println(formatter.label("before " + operation));

        try {
            Object result = joinPoint.proceed();
            System.out.println(formatter.label("after " + operation));
            return result;
        } catch (Throwable ex) {
            System.out.println(formatter.label("error " + operation
                    + " " + ex.getClass().getSimpleName()));
            throw ex;
        }
    }
}
~~~

`AuditAspect` là Spring bean chứa callback được XML trỏ đến. Dependency `AuditFormatter` được constructor-inject, không tạo thủ công bằng `new` trong aspect. Giữ `formatter` là `final` giúp quan hệ phụ thuộc rõ ràng; không lưu trạng thái của từng lời gọi vào mutable field dùng chung giữa các request.

### 4.4. Đăng ký bean và `aop:config`

**src/main/resources/applicationContext.xml**

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

    <!-- 1) Target: bean thực hiện nghiệp vụ. -->
    <bean id="orderService" class="demo.DefaultOrderService"/>

    <!-- 2) Dependency mà aspect sử dụng. -->
    <bean id="auditFormatter" class="demo.AuditFormatter"/>

    <!-- 3) Bean Java triển khai phương thức advice. -->
    <bean id="auditAspectBean" class="demo.AuditAspect">
        <constructor-arg ref="auditFormatter"/>
    </bean>

    <!-- 4) Cấu hình AOP qua XML schema. -->
    <aop:config proxy-target-class="false">
        <aop:pointcut
            id="serviceCalls"
            expression="execution(* demo.DefaultOrderService.*(..))"/>

        <aop:aspect id="orderAuditAspect" ref="auditAspectBean">
            <aop:around pointcut-ref="serviceCalls" method="around"/>
        </aop:aspect>
    </aop:config>

</beans>
~~~

Điểm quan trọng trong XML:

- `<bean id="orderService">` đặt target dưới quyền quản lý của Spring. Nếu nơi gọi viết `new DefaultOrderService()`, đối tượng đó không được Spring AOP proxy.
- `<bean id="auditFormatter">` là dependency độc lập.
- `<bean id="auditAspectBean">` cần tồn tại để `<aop:aspect ref="auditAspectBean">` trỏ tới. `constructor-arg ref="auditFormatter"` là dependency injection của Spring.
- `<aop:pointcut id="serviceCalls">` xác định phương thức của target class sẽ được match. `<aop:around pointcut-ref="serviceCalls">` dùng pointcut ID đã khai báo.
- `method="around"` phải trùng tên phương thức advice hiện hữu trong `AuditAspect`; đây **không phải** tên annotation.
- `proxy-target-class="false"` yêu cầu Spring ưu tiên interface-based proxy nếu target có interface phù hợp. Trong ví dụ `OrderService` là giao diện để caller lấy bean và gọi.
- `aop:config` đăng ký hạ tầng advisor/auto-proxy theo XML. Cấu hình này tự đủ cho aspect schema-based, không cần thêm `<aop:aspectj-autoproxy/>` chỉ để chạy `AuditAspect`.

Bên trong một `<aop:config>`, thứ tự khai báo các phần tử theo Spring schema là **pointcut, advisor rồi aspect**; nếu sử dụng nhiều loại, giữ thứ tự để XML hợp lệ. Các thẻ advice cụ thể được khai báo trong `<aop:aspect>`.

### 4.5. Lấy bean từ Spring context và chạy

**src/main/java/demo/Application.java**

~~~java
package demo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Application {
    public static void main(String[] args) {
        try (var context =
                     new ClassPathXmlApplicationContext("applicationContext.xml")) {
            OrderService service = context.getBean(OrderService.class);

            System.out.println("result: " + service.place("O-7"));

            try {
                service.reject("O-9");
            } catch (IllegalArgumentException ex) {
                System.out.println("caught: " + ex.getMessage());
            }
        }
    }
}
~~~

Từ thư mục dự án chạy:

~~~bash
mvn -q compile exec:java
~~~

Các dòng `System.out` của chương trình theo thứ tự:

~~~text
audit: before place
service: place O-7
audit: after place
result: accepted O-7
audit: before reject
audit: error reject IllegalArgumentException
caught: rejected O-9
~~~

Maven/ứng dụng có thể sinh log hạ tầng khác theo cấu hình môi trường, nhưng các dòng trên thể hiện thứ tự nghiệp vụ quan trọng.

**Diễn giải đường đi**: `context.getBean(OrderService.class)` trả bean service đã được Spring AOP áp dụng proxy khi pointcut khớp. Caller gọi `place()` qua proxy; interceptor gọi `AuditAspect.around()`; aspect dùng bean `AuditFormatter`, sau đó `proceed()` đi vào `DefaultOrderService.place()`. Nếu target ném lỗi, advice ghi nhãn lỗi rồi ném lại; caller vẫn nhận `IllegalArgumentException`.

**Khi lỗi cấu hình**: xóa `<bean id="auditFormatter"...>` hoặc đổi `ref="auditFormatter"` thành ID không tồn tại sẽ khiến Spring không khởi tạo đầy đủ `AuditAspect`. Xóa `<bean id="auditAspectBean"...>` nhưng giữ `<aop:aspect ref="auditAspectBean">` khiến tham chiếu aspect không hợp lệ. Đó là lỗi bean wiring, không phải lỗi biểu thức `execution(...)`.

## 5. `aop:config` còn hỗ trợ những phần tử gì?

Ngoài `<aop:aspect>`, `<aop:config>` hỗ trợ **named pointcut** ở cấp cấu hình, **advisor** dùng advice interface, và các loại advice `before`, `after-returning`, `after-throwing`, `after`, `around`. Ví dụ:

~~~xml
<aop:config>
    <aop:pointcut id="serviceCalls"
                  expression="execution(* demo..*Service.*(..))"/>
    <aop:aspect ref="auditBean">
        <aop:before pointcut-ref="serviceCalls" method="before"/>
    </aop:aspect>
</aop:config>
~~~

Đây là fragment: bean `auditBean` cần method `before()` tương thích. Một `<aop:advisor advice-ref="interceptorBean" .../>` thay thế bằng **bean implement Advice/MethodInterceptor** chứ không tự gọi Java method tùy ý. Nếu có đầy đủ ba nhóm ở cấp `aop:config`, XSD yêu cầu **pointcut → advisor → aspect**. Các bài **“aop:config và khai báo aop:aspect”**, **“Pointcut có tên và pointcut-ref”** và **“aop:before...around”** lần lượt giải thích cấu trúc, chia sẻ scope và semantics từng advice.

## 6. Đăng ký đúng các bean mà AOP cần

### 6.1. Bean nghiệp vụ (target)

Target phải là **Spring-managed bean**, bằng `<bean>`, component scanning hoặc cấu hình bean tương đương. Khi dùng XML thuần, `<bean id="orderService" class="..."/>` trực tiếp là cách dễ xác minh nhất. Chỉ khai báo pointcut không tạo ra bean matching.

Một bean được proxy khi Spring có advisor khớp với method/target phù hợp. Không phải mọi bean trong context đều được bọc proxy, và tên bean được lấy từ context có thể trỏ tới proxy chứ không phải instance target nguyên bản.

### 6.2. Bean chứa aspect và dependency của nó

Với schema-based:

~~~xml
<bean id="auditFormatter" class="demo.AuditFormatter"/>

<bean id="auditAspectBean" class="demo.AuditAspect">
    <constructor-arg ref="auditFormatter"/>
</bean>

<aop:config>
    <aop:aspect ref="auditAspectBean">
        <aop:before
            pointcut="execution(* demo..*Service.place(..))"
            method="before"/>
    </aop:aspect>
</aop:config>
~~~

Đây chỉ là mẫu wiring; Java `AuditAspect` phải có method `before` nếu sử dụng thay cho phiên bản `around` ở mục 4. Các bean phụ thuộc của aspect dùng cú pháp Spring beans namespace thông thường (`constructor-arg`, `property`, `autowire` nếu được cấu hình). `<aop:aspect ref>` phải trỏ đến bean backing aspect; nó không phải cơ chế inject dependency.

Nếu aspect phụ thuộc một repository/client/service khác, khai báo bean phù hợp và inject vào aspect. Lưu ý tránh pointcut quá rộng vô tình intercept các method của chính aspect hoặc bean hỗ trợ, gây hành vi vòng lặp hoặc log ngoài dự kiến.

### 6.3. Phạm vi, khởi tạo và thứ tự bean

- Với schema-defined aspect, Spring hỗ trợ mô hình **singleton** cho aspect instance, nên không đặt request state vào field mutable của aspect. Với dữ liệu lời gọi, dùng biến cục bộ trong advice hoặc cơ chế context phù hợp.
- Constructor injection theo `ref` giúp Spring biết phụ thuộc cần được khởi tạo/resolve. Với singleton bean thông thường, container sẽ tạo dependency phù hợp khi khởi tạo aspect; **thứ tự các dòng `<bean>` trong cùng XML không phải công cụ sắp xếp logic advice**.
- Thứ tự chạy nhiều advisors/aspects là vấn đề AOP ordering (`order`, `Ordered`, `@Order` tùy cách khai báo); không suy diễn từ thứ tự đặt `<bean>` hay thời điểm dependency được inject.
- `depends-on` là cơ chế kiểm soát khởi tạo khi có quan hệ cần thứ tự nhưng không có dependency injection trực tiếp; nếu đã có `constructor-arg ref`, thông thường không cần `depends-on` chỉ để bean formatter sẵn sàng. `depends-on` không thay `ref` và không quyết định thứ tự advice.

## 7. Biến thể XML: bật các lớp `@Aspect` bằng `aop:aspectj-autoproxy`

Khi advice được viết trong class `@Aspect`, XML có thể kích hoạt bằng `<aop:aspectj-autoproxy/>` và đăng ký aspect bằng `<bean>`:

~~~xml
<bean id="orderService" class="demo.DefaultOrderService"/>
<bean id="annotatedAuditAspect" class="demo.AnnotatedAuditAspect"/>
<aop:aspectj-autoproxy/>
~~~

`demo.AnnotatedAuditAspect` phải thực sự có `@Aspect` và annotation advice. Nếu nó cần `AuditFormatter`, đăng ký bean formatter rồi inject qua `constructor-arg ref="auditFormatter"` như bean thường. `@Aspect` một mình không tạo bean, và `<aop:aspectj-autoproxy>` không tự bật component scanning. Trong kiểu này không cần thêm `<aop:aspect ref="annotatedAuditAspect">` để định nghĩa lại chính advice ấy.

## 8. Tác động của cấu hình proxy đến bean wiring

`proxy-target-class="false"` cho phép Spring ưu tiên **JDK interface proxy** với bean có interface phù hợp; caller nên lấy bean qua interface, như `OrderService.class`. `proxy-target-class="true"` yêu cầu **class-based proxy** và có giới hạn đối với final class, final/private methods. Spring có thể hợp nhất thiết lập auto-proxy trong cùng context nên một cấu hình khác yêu cầu CGLIB có thể ảnh hưởng kết quả.

Dù loại proxy nào, `this.otherMethod()` trong target và `new Target()` ngoài container không tự đi qua proxy. Muốn tìm hiểu `expose-proxy`, `AopContext.currentProxy()`, thread-local boundary và cách tự kiểm tra proxy thật, xem bài **“proxy-target-class và expose-proxy trong cấu hình XML”**.

## 9. Lỗi thường gặp khi cấu hình namespace và bean phụ thuộc

| Biểu hiện | Nguyên nhân có thể | Cách kiểm tra/sửa |
| --- | --- | --- |
| XML parser không nhận `aop:config` | Thiếu `xmlns:aop`, cặp `schemaLocation`, hoặc XSD không phù hợp | Đối chiếu URI và `spring-aop.xsd` chính thức |
| Context báo không tìm thấy bean được tham chiếu | `ref="auditAspectBean"`, `constructor-arg ref` hoặc `advice-ref` trỏ ID không tồn tại | Kiểm tra đúng tên bean ID và context chứa bean |
| Spring không tạo được aspect bean | Constructor dependency chưa đăng ký hoặc signature/constructor-arg không khớp | Kiểm tra `<bean>` hỗ trợ và constructor của aspect |
| Advice không chạy | Pointcut không match class/method, target không do Spring quản lý, caller dùng `new` | Kiểm tra bean thực tế từ ApplicationContext và pointcut |
| Advice không chạy khi gọi từ cùng bean | Self-invocation `this.method()` bypass proxy | Gọi qua ranh giới Spring bean proxy thích hợp |
| Khai báo `<aop:aspect ref="...">` nhưng advice method không có | `method="..."` sai tên hoặc signature sai | Khớp đúng method trên backing aspect bean |
| Dùng `<aop:aspectj-autoproxy/>` nhưng `@Aspect` không chạy | Chưa đăng ký bean `@Aspect` hoặc thiếu aspectjweaver | Đăng ký bằng `<bean>`/component scan và kiểm tra classpath |
| Log bị lặp | Định nghĩa cùng advice ở cả schema-based và `@Aspect`, hoặc nhiều advisors match | Loại cấu hình trùng, xem proxy/advisor chain |
| `getBean(DefaultOrderService.class)` không hoạt động như mong muốn | Bean đang là JDK interface proxy | Lấy bằng `OrderService`, hoặc cân nhắc class proxy |
| XML có `&&` trong expression | Ký tự ampersand chưa escape | Dùng `and` hoặc `&amp;&amp;` |
| `<aop:advisor advice-ref>` tham chiếu bean thường | Advice bean chưa implement đúng Spring AOP Advice/MethodInterceptor contract | Đăng ký class advice tương ứng API |
| Thứ tự advice ngoài mong đợi | Hiểu nhầm thứ tự khai báo bean là thứ tự interception | Cấu hình và kiểm thử advisor order rõ ràng |

## 10. Kiểm thử cấu hình XML trong Spring

Kiểm thử nên khởi tạo context bằng **cùng XML** mà ứng dụng dùng; không `new` target để kiểm thử advice. Các kiểm tra có giá trị:

1. Context khởi tạo thành công: schema được parse, bean ID và dependency được resolve.
2. Lấy bean qua interface; xác minh lời gọi `place` đi qua before → service → after, còn `reject` đi qua before → error và exception gốc vẫn đến caller.
3. Nếu bỏ cấu hình `<aop:config>` trong môi trường test riêng, quan sát direct target output không có log advice: giúp nhận diện điểm activation.
4. Đổi pointcut trong môi trường test để cố tình loại `place` khỏi vùng match; xác minh advice không chạy để biết expression đang hoạt động.
5. Cấu hình advisor/aspect khác: đo đúng số lần interception và thứ tự order thay vì suy đoán.
6. Xác minh bean `AuditAspect` được inject đúng `AuditFormatter`; không chia sẻ mutable per-request state trên singleton aspect.
7. Nếu thay JDK/class proxy, kiểm tra cách lấy bean và method đủ điều kiện được advice.

Có thể kiểm tra cơ chế proxy trong Java test:

~~~java
import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

try (var context =
         new ClassPathXmlApplicationContext("applicationContext.xml")) {
    Object service = context.getBean("orderService");
    System.out.println(AopUtils.isAopProxy(service)); // true nếu được proxy
}
~~~

Đây là đoạn test **minh họa nhúng trong một method/runner Java**, không phải file độc lập có main. `isAopProxy` cho biết instance trả về là Spring AOP proxy; nó không chứng minh mọi method đều được pointcut match. Khi debug cần xét cả tên method, kiểu proxy, advisors và cách caller gọi bean.

## 11. Phân biệt ba lỗi về vòng đời và trách nhiệm

**Bean definition failure**: Spring chưa tạo được dependency như `auditFormatter`; context có thể fail trước khi business method được gọi. Cần sửa `<bean>`, `ref`, constructor injection hoặc phạm vi ApplicationContext.

**Advisor/pointcut mismatch**: context khởi động được và target chạy nhưng method không nằm trong expression, advice có thể không được áp dụng. Cần xét `execution(...)`, class thật và proxy boundary.

**Business exception**: target method thực sự được gọi và ném lỗi; `@AfterThrowing` hoặc `aop:around` có thể ghi nhận lỗi trước khi caller thấy exception. Cần giữ nguyên exception và bảo đảm advice không vô tình nuốt lỗi, trừ khi yêu cầu nghiệp vụ chỉ định chính sách khác.

Chúng là ba tình huống khác nhau, và Spring XML cung cấp dấu hiệu kiểm tra ở những chỗ khác nhau.

## Tổng kết

Trong cấu hình XML của Spring AOP, `xmlns:aop` và `spring-aop.xsd` cung cấp cú pháp, **`<aop:config>`** khai báo pointcut và advice, còn **`<bean>`** tạo đối tượng target/aspect cùng các dependency cần injection. `<aop:aspect ref>` liên kết đến bean triển khai advice; `pointcut-ref` liên kết đến pointcut; `method` liên kết đến tên method. Nếu chọn kiểu Java `@Aspect`, bật `<aop:aspectj-autoproxy/>` và vẫn phải đăng ký aspect bean. Mọi invocation cần đi qua Spring proxy thì mới được advice áp dụng.

### Tài liệu Spring chính thức

- [Spring Framework — Schema-based AOP Support](https://docs.spring.io/spring-framework/reference/core/aop/schema.html)
- [Spring Framework — Enabling @AspectJ Support](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework — XML Schema-based Configuration](https://docs.spring.io/spring-framework/reference/core/appendix/xsd-schemas.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-aop-configuration-002">aop:aspectj-autoproxy cho aspect dùng annotation</a>

<details>
<summary>Xem chi tiết</summary>

# `<aop:aspectj-autoproxy>`: kích hoạt `@Aspect` từ cấu hình XML trong Spring

Trong ứng dụng Spring Framework sử dụng XML để khai báo bean, phần tử **`<aop:aspectj-autoproxy/>`** kích hoạt cơ chế Spring AOP tự tạo proxy cho các Spring bean phù hợp với pointcut của aspect viết bằng annotation **`@Aspect`, `@Before`, `@AfterReturning`, `@Around`...**. Đây là cách sử dụng cú pháp annotation của AspectJ để định nghĩa *advice/pointcut* trong một application context được cấu hình bằng XML.

Điểm cần phân biệt: XML **bật cơ chế xử lý aspect**, còn các class Java mang `@Aspect` **vẫn phải được đăng ký thành Spring bean** (trực tiếp bằng `<bean>` hoặc bằng component scanning). Spring AOP thực hiện advice thông qua **Spring proxy**, không tự động weave bytecode mọi đối tượng Java trong JVM.

Bài này tập trung vào cấu hình và vận hành **Spring Aspect/AOP**. Không đi sâu các mô hình AOP độc lập với Spring.

## 1. `<aop:aspectj-autoproxy/>` làm gì?

Ví dụ khai báo cơ bản:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd">

    <aop:aspectj-autoproxy/>

    <!-- Cả target và aspect đều cần là Spring bean. -->
    <bean id="orderService" class="com.example.xmlaop.OrderServiceImpl"/>
    <bean id="loggingAspect" class="com.example.xmlaop.LoggingAspect"/>
</beans>
```

Khi Spring xây dựng `ApplicationContext`:

1. XML namespace `aop` cung cấp phần tử `<aop:aspectj-autoproxy/>`. Spring đăng ký bộ xử lý tự tạo AOP proxy (`AnnotationAwareAspectJAutoProxyCreator`, một dạng bean post-processor).
2. Spring tạo các bean được khai báo, trong đó có bean `loggingAspect`. Trên bean aspect, Spring nhận diện annotation `@Aspect` và các advice dùng annotation.
3. Khi bean `orderService` có method khớp pointcut, Spring cung cấp một **proxy** thay cho việc đưa trực tiếp target object cho caller lấy từ context.
4. Lời gọi từ client **qua proxy** kích hoạt advice; advice chạy trước/sau/vòng quanh target tùy annotation. Việc gọi method trên target vẫn là logic do target cung cấp.

`<aop:aspectj-autoproxy/>` **không** tự component-scan, **không** tự tạo bean cho mọi class có `@Aspect` và **không** làm cho lời gọi trực tiếp bằng `new` trở thành lời gọi qua proxy. Nó kích hoạt cách Spring áp dụng các aspect bean đã có trong context.

## 2. Ví dụ chạy được với Spring Framework 6 và Java 17

Ví dụ tạo một Spring application context bằng XML (không cần Spring Boot), có một service xử lý đơn hàng và một aspect ghi log trước lời gọi service.

### 2.1. Cấu trúc dự án

```text
spring-xml-aop/
├── pom.xml
└── src/
    └── main/
        ├── java/com/example/xmlaop/
        │   ├── Demo.java
        │   ├── OrderService.java
        │   ├── OrderServiceImpl.java
        │   └── LoggingAspect.java
        └── resources/
            └── applicationContext.xml
```

**`pom.xml`** (các phiên bản cụ thể phục vụ ví dụ độc lập; dự án thực tế có thể quản lý phiên bản bằng BOM):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="
           http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-xml-aop</artifactId>
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
            </plugin>
        </plugins>
    </build>
</project>
```

`spring-context` cung cấp application context và quản lý bean; `spring-aop` cung cấp cơ chế AOP; `aspectjweaver` cung cấp các type và hỗ trợ phân tích biểu thức pointcut kiểu AspectJ được Spring AOP sử dụng. Ví dụ chỉ dùng **Spring proxy AOP**: không cài Java agent hay dùng AspectJ compile-time/load-time weaving.

### 2.2. Interface và target bean

**`src/main/java/com/example/xmlaop/OrderService.java`**

```java
package com.example.xmlaop;

public interface OrderService {
    void placeOrder(String orderId);
}
```

**`src/main/java/com/example/xmlaop/OrderServiceImpl.java`**

```java
package com.example.xmlaop;

public class OrderServiceImpl implements OrderService {
    @Override
    public void placeOrder(String orderId) {
        System.out.println("SERVICE placeOrder " + orderId);
    }
}
```

Ta dùng interface để thấy hành vi của **JDK dynamic proxy** khi để chế độ mặc định `proxy-target-class="false"`.

### 2.3. Aspect khai báo bằng annotation

**`src/main/java/com/example/xmlaop/LoggingAspect.java`**

```java
package com.example.xmlaop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class LoggingAspect {
    @Before("execution(* com.example.xmlaop.OrderServiceImpl.placeOrder(..))")
    public void beforePlaceOrder() {
        System.out.println("ASPECT before placeOrder");
    }
}
```

- `@Aspect` cho Spring biết bean này chứa định nghĩa aspect.
- `@Before` khai báo advice chạy trước khi method được pointcut chọn thực thi.
- Biểu thức `execution(* com.example.xmlaop.OrderServiceImpl.placeOrder(..))` chọn method `placeOrder` của implementation trong ví dụ. Spring dùng pointcut đó để xác định phương thức nào cần áp dụng advice khi gọi qua proxy.
- Class aspect **không cần `@Component`** khi đã khai báo thành Spring bean bằng XML.

Đừng tự gọi `new LoggingAspect()` rồi mong Spring tự áp dụng. Phải để instance đó thuộc Spring context.

### 2.4. Kích hoạt proxy trong XML

**`src/main/resources/applicationContext.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd">

    <!-- Bật @Aspect-based Spring AOP proxying. -->
    <aop:aspectj-autoproxy proxy-target-class="false"/>

    <bean id="orderService" class="com.example.xmlaop.OrderServiceImpl"/>

    <!-- Đăng ký aspect làm Spring bean: bước cần thiết. -->
    <bean id="loggingAspect" class="com.example.xmlaop.LoggingAspect"/>
</beans>
```

Trong cấu hình này:

- `xmlns:aop` và cặp schema `spring-aop.xsd` cho phép XML nhận phần tử thuộc Spring AOP namespace.
- `<aop:aspectj-autoproxy/>` kích hoạt trình tự tạo proxy cho các target bean phù hợp.
- Hai `<bean>` đưa **target** và **aspect** vào cùng application context.
- `proxy-target-class="false"` yêu cầu ưu tiên JDK proxy khi bean có interface thích hợp. Nếu target không có interface, Spring có thể dùng proxy dựa trên class. Đây là lựa chọn proxy, không liên quan việc có dùng annotation trên aspect hay không.

Không cần đặt `@EnableAspectJAutoProxy` trong code Java để bật thêm lần nữa; đó là dạng kích hoạt tương ứng khi dùng cấu hình bằng annotation/Java. Ví dụ này cố ý dùng XML làm nguồn cấu hình.

### 2.5. Client chạy qua Spring context

**`src/main/java/com/example/xmlaop/Demo.java`**

```java
package com.example.xmlaop;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Demo {
    public static void main(String[] args) {
        try (ClassPathXmlApplicationContext context =
                 new ClassPathXmlApplicationContext("applicationContext.xml")) {

            OrderService service =
                context.getBean("orderService", OrderService.class);

            System.out.println("PROXY " + AopUtils.isAopProxy(service));
            System.out.println("JDK_PROXY " + AopUtils.isJdkDynamicProxy(service));

            service.placeOrder("A001");
        }
    }
}
```

Chạy ở thư mục dự án có Maven và JDK 17 trở lên:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.xmlaop.Demo
```

Các dòng do code của ví dụ in ra, theo đúng thứ tự (không tính thông báo riêng từ môi trường Maven):

```text
PROXY true
JDK_PROXY true
ASPECT before placeOrder
SERVICE placeOrder A001
```

Lý do: `getBean(...)` trả Spring proxy cho service; lời gọi `service.placeOrder("A001")` đi qua proxy và chạy `@Before` trước thân method. Nếu bỏ `<aop:aspectj-autoproxy/>` khỏi XML nhưng giữ hai bean, với cấu hình ví dụ này `getBean` sẽ nhận target thường: `PROXY false`, `JDK_PROXY false`, và không có dòng log `ASPECT`. Đây là cách đối chiếu hữu ích để nhận diện tác dụng của phần tử cấu hình.

## 3. Đăng ký aspect bằng component scanning trong XML

Ngoài khai báo từng `<bean>`, ứng dụng có thể để Spring quét annotation để tìm bean. Khi đó `@Aspect` **chỉ định nghĩa aspect**, còn `@Component` biến class đó thành ứng viên được component-scan đăng ký:

```java
package com.example.xmlaop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {
    @Before("execution(* com.example.xmlaop.OrderServiceImpl.placeOrder(..))")
    public void beforePlaceOrder() {
        System.out.println("ASPECT before placeOrder");
    }
}
```

Nếu muốn component-scan cả service, thêm `@Service` vào `OrderServiceImpl`. XML tương ứng có thể viết:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xmlns:context="http://www.springframework.org/schema/context"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd
           http://www.springframework.org/schema/context
           https://www.springframework.org/schema/context/spring-context.xsd">

    <context:component-scan base-package="com.example.xmlaop"/>
    <aop:aspectj-autoproxy/>
</beans>
```

**Đây là biến thể cấu hình**, không phải phần XML thay thế vào ví dụ hoàn chỉnh ở mục 2 nếu chưa thêm `@Component`/`@Service` vào các class. Nếu đã chuyển sang scan, hãy bỏ những khai báo `<bean>` trùng để tránh hai instance không chủ định.

Nhớ hai trách nhiệm độc lập:

| Khai báo | Tác dụng |
| --- | --- |
| `<context:component-scan .../>` | Đăng ký các class đủ điều kiện thành Spring bean, gồm aspect có `@Component` |
| `<aop:aspectj-autoproxy/>` | Kích hoạt Spring AOP proxy và áp dụng advice từ các bean mang `@Aspect` |

## 4. Tùy chọn quan trọng: `proxy-target-class` và `expose-proxy`

Hai thuộc tính của `<aop:aspectj-autoproxy>` điều khiển hạ tầng proxy, không thay đổi annotation trong `@Aspect`:

~~~xml
<aop:aspectj-autoproxy proxy-target-class="false" expose-proxy="false"/>
~~~

- `proxy-target-class="false"`: với bean có interface phù hợp, Spring thường chọn JDK dynamic proxy; không có interface có thể dùng class proxy. `true` yêu cầu class-based proxy, chịu giới hạn final/private và module visibility.
- `expose-proxy="true"`: cho phép `AopContext.currentProxy()` trong lời gọi đã đi qua proxy, hữu ích cho trường hợp rất cụ thể nhưng tạo coupling và phụ thuộc thread. Nó **không tự chuyển** `this.method()` thành proxy call.
- Trong context có nhiều cấu hình AOP/transaction, các auto-proxy settings có thể được hợp nhất; hãy kiểm tra loại proxy thực tế bằng `AopUtils`, đừng dựa vào một dòng XML.

Bài **“proxy-target-class và expose-proxy trong cấu hình XML”** có ví dụ thay từng thuộc tính, đối chiếu JDK/CGLIB và self-invocation.

## 5. Vì sao annotation tồn tại nhưng advice không chạy?

Khi advice annotation không chạy, phân loại nguyên nhân theo đường thực thi thay vì chỉ tìm lỗi pointcut:

| Dấu hiệu | Kiểm tra đầu tiên |
| --- | --- |
| `@Aspect` tồn tại nhưng chưa được Spring đăng ký | `<bean>` hoặc `@Component` kết hợp `<context:component-scan>` |
| Aspect bean tồn tại nhưng không có advisor | `<aop:aspectj-autoproxy/>`, namespace/schema, classpath `aspectjweaver` |
| Bean có proxy nhưng một method không có advice | `execution(...)` có match method/annotation thật không |
| Chỉ mất advice khi gọi nội bộ | `this.method()` bỏ qua Spring proxy |
| Target tạo thủ công không có advice | Đưa target vào context và inject reference proxy |
| Log xuất hiện hai lần | Có nhiều aspect beans/advisors cùng triển khai một concern không |

`<aop:aspectj-autoproxy/>` không tương đương với `<aop:aspect>` trong `<aop:config>`. Chỉ phối hợp khi thật sự dùng hai kiểu khai báo riêng với trách nhiệm không chồng lấn; bài **“Kết hợp XML với Java configuration mà không tạo hành vi trùng lặp”** phân tích cụ thể các nguồn duplicate.

## 6. Khi nào dùng, khi nào chuyển sang cấu hình Java?

Nếu dự án hiện có `applicationContext.xml` quản lý Spring bean, `<aop:aspectj-autoproxy/>` cho phép tiếp tục cấu hình ứng dụng bằng XML trong khi viết advice gọn bằng annotation Java.

Ở ứng dụng dùng Java configuration, cơ chế tương ứng là `@EnableAspectJAutoProxy` (trong ứng dụng Spring Boot, auto-configuration liên quan có thể đã kích hoạt Spring AOP tùy dependencies/config). Việc chọn XML hay Java config thay đổi **cách đăng ký hạ tầng proxy**; nguyên tắc Spring bean, pointcut matching và gọi qua proxy vẫn cần được tuân thủ.

**Kiểm tra nhanh trước khi bàn giao cấu hình:**

1. XML khai báo `xmlns:aop` và `spring-aop.xsd` đúng.
2. Có `<aop:aspectj-autoproxy/>` trong application context phù hợp.
3. Class aspect có `@Aspect` và các advice annotation hợp lệ; aspect thực sự được đăng ký Spring bean.
4. Target cũng là Spring bean, pointcut khớp method muốn intercept.
5. Mã sử dụng bean từ context và gọi **qua proxy**; kiểu injection phù hợp loại proxy.
6. Dependency Spring AOP/AspectJ đầy đủ; phép thử `AopUtils.isAopProxy(...)` cùng output advice xác nhận cơ chế hoạt động.

**Ghi nhớ:** `<aop:aspectj-autoproxy/>` là công tắc **Spring XML để kích hoạt AOP proxy cho `@Aspect` beans**. Nó không thay thế việc đăng ký bean và không biến mọi lời gọi Java thành join point được intercept; cả XML, bean và đường đi của lời gọi phải đúng thì advice mới chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-aop-configuration-003">aop:config và khai báo aop:aspect</a>

<details>
<summary>Xem chi tiết</summary>

# `aop:config` và khai báo `aop:aspect` trong Spring AOP bằng XML

## 1. Mục tiêu và ngữ cảnh

Spring Framework cho phép khai báo **Spring AOP proxy, pointcut và advice bằng XML** qua namespace `aop`. Cách này hữu ích khi duy trì dự án cấu hình Spring bằng XML hoặc cần bổ sung hành vi cắt ngang cho bean đã có mà không muốn đặt annotation AOP trên chính lớp nghiệp vụ.

Bài này tập trung vào hai thành phần:

- `<aop:config>`: nơi khai báo pointcut, advisor và aspect để Spring tự tạo proxy cho các bean phù hợp.
- `<aop:aspect>`: ánh xạ **một Spring bean chứa phương thức advice** với pointcut và các loại advice khai báo trong XML.

Ví dụ xuyên suốt là ghi trace cho phương thức thanh toán trong **Spring AOP proxy-based**. Các khái niệm chung về lập trình hướng khía cạnh chỉ được nhắc tới khi cần hiểu chính xác hành vi Spring.

Sau bài học, bạn có thể giải thích vai trò `ref`, `id`, `pointcut-ref`, `method`; viết file XML hoàn chỉnh; chạy một ứng dụng Java 17+; kiểm tra proxy thật sự được tạo; và chẩn đoán trường hợp XML hợp lệ nhưng advice không chạy.

## 2. Thành phần XML và các quan hệ quan trọng

`<aop:config>` tạo các định nghĩa AOP theo schema; `<aop:aspect ref="loggingBean">` gắn **bean advice đã đăng ký** với danh sách method/pointcut tương ứng. Khung sau thể hiện đủ ba loại tên:

~~~xml
<bean id="targetService" class="demo.TargetService"/>
<bean id="loggingBean" class="demo.LoggingAdvice"/>
<aop:config>
    <aop:pointcut id="serviceCall"
                  expression="execution(* demo.TargetService.work(..))"/>
    <aop:aspect id="loggingAspect" ref="loggingBean">
        <aop:before method="beforeWork" pointcut-ref="serviceCall"/>
    </aop:aspect>
</aop:config>
~~~

Đây là fragment, phải có `TargetService.work` và `LoggingAdvice.beforeWork` tương ứng. **`loggingBean`** là Spring bean ID; **`loggingAspect`** là ID khai báo aspect; **`serviceCall`** là pointcut ID. `method="beforeWork"` gọi Java method của bean `loggingBean`; `pointcut-ref="serviceCall"` không trỏ tới method Java. Nếu khai báo thêm `aop:advisor`, giữ thứ tự XSD **pointcut → advisor → aspect**.

Cơ chế thực thi: caller → Spring AOP proxy → advisor phù hợp → advice method → target. Khác với `<aop:aspectj-autoproxy>`, Java POJO `LoggingAdvice` không cần `@Aspect`; metadata advice đã có trong XML.

## 3. Dạng pointcut, phạm vi và lựa chọn khai báo

### Pointcut dùng chung

~~~xml
<aop:config>
    <aop:pointcut id="paymentOperations"
                  expression="execution(public * demo.PaymentService.*(..))"/>
    <aop:aspect ref="auditBean">
        <aop:before method="check" pointcut-ref="paymentOperations"/>
        <aop:after method="finish" pointcut-ref="paymentOperations"/>
    </aop:aspect>
</aop:config>
~~~

Ví dụ là fragment: `auditBean` phải được khai báo thành Spring bean và có hai method phù hợp. Pointcut ở cấp `aop:config` có thể được các aspect/advisor trong cấu hình tham chiếu, giúp tránh lặp lại expression.

Biểu thức `execution(public * demo.PaymentService.*(..))` mô tả các method public, bất kỳ kiểu trả về, bất kỳ tên, số lượng tham số bất kỳ, thuộc class `demo.PaymentService`. Nó **không có nghĩa** “mọi service trong package demo”. Hãy chọn package/class/method thực tế để giới hạn phạm vi.

### Pointcut trực tiếp trong advice

~~~xml
<aop:aspect ref="auditBean">
    <aop:before method="check"
                pointcut="execution(* demo.PaymentService.pay(..))"/>
</aop:aspect>
~~~

`pointcut="..."` phù hợp khi biểu thức chỉ dùng một lần; `pointcut-ref="..."` phù hợp khi muốn tái sử dụng pointcut đã đặt tên. Không đặt cả hai trên cùng một advice.

### Pointcut cục bộ trong aspect

~~~xml
<aop:aspect ref="auditBean">
    <aop:pointcut id="withinPayments"
                  expression="within(demo..*)"/>
    <aop:before method="check" pointcut-ref="withinPayments"/>
</aop:aspect>
~~~

`within(demo..*)` là biểu thức minh họa cho các type dưới package `demo`; trong dự án thật cần thu hẹp nếu không muốn quét quá nhiều bean. Pointcut cục bộ thuộc phạm vi cấu hình của aspect đó.

### Kết hợp điều kiện và escaping XML

~~~xml
<aop:pointcut id="namedPayment"
              expression="execution(* demo.PaymentService.pay(..)) and args(orderId)"/>
~~~

Trong XML, toán tử `&&` phải viết `&amp;&amp;`; Spring cũng hỗ trợ các từ `and`, `or` và `not`, thường dễ đọc hơn. Với expression có `<` hoặc `>` trong giá trị thuộc tính, cũng phải escape đúng cú pháp XML. Đối với biểu thức có binding như `args(orderId)`, method advice phải khai báo tham số/binding tương ứng; fragment trên chỉ nhằm diễn đạt cú pháp pointcut.

## 4. Ví dụ chạy được: tạo proxy và khai báo `aop:aspect`

Ví dụ tối giản dưới đây sử dụng **Spring Framework 6.2.x, Java 17+, Maven**. Không yêu cầu Spring Boot, không chạy web server; mục tiêu là thấy một lời gọi method đi qua Spring AOP proxy và advice trong XML.

### 4.1. Cấu trúc tệp

~~~text
xml-spring-aop/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/
    │   │   ├── BillingService.java
    │   │   ├── TraceAspect.java
    │   │   └── Main.java
    │   └── resources/
    │       └── spring/aop-context.xml
    └── test/java/demo/
        └── XmlAopTest.java
~~~

### 4.2. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>xml-spring-aop</artifactId>
    <version>1.0-SNAPSHOT</version>
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
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Các phiên bản được cố định để ví dụ có thể tái hiện; trong ứng dụng thực hãy đồng bộ với BOM/framework version của dự án. `aspectjweaver` cung cấp các thành phần AspectJ cần cho biểu thức pointcut của Spring AOP, **không có nghĩa ví dụ đang dùng AspectJ load-time weaving**.

### 4.3. `src/main/java/demo/BillingService.java`

~~~java
package demo;

public class BillingService {
    public String pay(String orderId) {
        System.out.println("service.pay:" + orderId);

        if ("bad".equals(orderId)) {
            throw new IllegalArgumentException("invalid order");
        }
        return "paid:" + orderId;
    }
}
~~~

Method `pay` là public, không final; class cũng không final để ví dụ có thể dùng class-based proxy.

### 4.4. `src/main/java/demo/TraceAspect.java`

~~~java
package demo;

import java.util.concurrent.atomic.AtomicInteger;
import org.aspectj.lang.ProceedingJoinPoint;

public class TraceAspect {
    private final AtomicInteger invocationCount = new AtomicInteger();

    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        invocationCount.incrementAndGet();
        String orderId = (String) joinPoint.getArgs()[0];
        System.out.println("aspect.before:" + orderId);

        try {
            Object result = joinPoint.proceed();
            System.out.println("aspect.after-returning:" + result);
            return result;
        } catch (Throwable exception) {
            System.out.println("aspect.after-throwing:"
                    + exception.getClass().getSimpleName());
            throw exception;
        } finally {
            System.out.println("aspect.finally:" + orderId);
        }
    }

    public int invocationCount() {
        return invocationCount.get();
    }
}
~~~

Đây là một Spring bean Java bình thường: **không có `@Aspect` hay `@Around`**. `trace(ProceedingJoinPoint)` được gọi vì XML khai báo `<aop:around method="trace" .../>`. Dữ liệu `orderId` là biến cục bộ của mỗi invocation; bộ đếm dùng `AtomicInteger` vì aspect bean thường là singleton và có thể phục vụ nhiều lời gọi đồng thời.

Lời gọi `joinPoint.proceed()` quyết định việc thực thi method gốc. Khi thành công, advice trả nguyên kết quả; khi thất bại, advice ghi dấu vết rồi ném lại exception, không nuốt lỗi. Khối `finally` chạy cả ở nhánh thành công và lỗi.

### 4.5. `src/main/resources/spring/aop-context.xml`

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

    <bean id="billingService" class="demo.BillingService"/>
    <bean id="traceAspectBean" class="demo.TraceAspect"/>

    <aop:config proxy-target-class="true">
        <aop:pointcut id="payOperation"
                      expression="execution(public * demo.BillingService.pay(..))"/>

        <aop:aspect id="billingTrace" ref="traceAspectBean">
            <aop:around pointcut-ref="payOperation" method="trace"/>
        </aop:aspect>
    </aop:config>
</beans>
~~~

Các điểm đáng chú ý:

- `traceAspectBean` là bean chứa logic advice; `billingTrace` là định danh XML của aspect; `payOperation` là định danh pointcut.
- `proxy-target-class="true"` yêu cầu Spring dùng class-based proxy để ví dụ có thể truy xuất bean qua `BillingService.class`. Với interface và JDK proxy, cách lấy bean/cast có thể khác.
- `pointcut-ref="payOperation"` tránh lặp lại expression.
- `aop:config` đăng ký cơ chế AOP proxy cho các bean Spring phù hợp; không cần thêm `<aop:aspectj-autoproxy/>` vào ví dụ chỉ dùng schema-style này.

### 4.6. `src/main/java/demo/Main.java`

~~~java
package demo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context =
                     new ClassPathXmlApplicationContext("spring/aop-context.xml")) {
            BillingService service = context.getBean(BillingService.class);

            System.out.println(service.pay("ok"));

            try {
                service.pay("bad");
            } catch (IllegalArgumentException exception) {
                System.out.println("caller.caught:" + exception.getMessage());
            }
        }
    }
}
~~~

Chạy trong thư mục `xml-spring-aop` với Maven và JDK 17+:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.Main
~~~

**Các dòng stdout do chính mã Java in ra**, theo thứ tự:

~~~text
aspect.before:ok
service.pay:ok
aspect.after-returning:paid:ok
aspect.finally:ok
paid:ok
aspect.before:bad
service.pay:bad
aspect.after-throwing:IllegalArgumentException
aspect.finally:bad
caller.caught:invalid order
~~~

Log của Maven hoặc thư viện (nếu cấu hình logging khác) không thuộc output kiểm chứng trên. Trace thành công và trace lỗi cùng đi qua một Spring AOP proxy được tạo từ XML.

### 4.7. Kiểm thử proxy và advice trong `src/test/java/demo/XmlAopTest.java`

~~~java
package demo;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlAopTest {
    @Test
    void xmlAspectInterceptsSuccessAndFailureThroughProxy() {
        try (var context =
                     new ClassPathXmlApplicationContext("spring/aop-context.xml")) {
            BillingService service = context.getBean(BillingService.class);
            TraceAspect trace = context.getBean("traceAspectBean", TraceAspect.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertTrue(AopUtils.isCglibProxy(service));
            assertEquals(0, trace.invocationCount());

            assertEquals("paid:ok", service.pay("ok"));
            assertThrows(IllegalArgumentException.class,
                    () -> service.pay("bad"));

            assertEquals(2, trace.invocationCount());
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q test
~~~

Kỳ vọng: **1 test thành công, tổng cộng 2 lần advice chạy**. Test đo được cả cấu hình proxy lẫn thực tế Spring gọi advice; chỉ assert kết quả thanh toán mà không assert `invocationCount` sẽ không đủ chứng minh XML AOP đang hoạt động.

## 5. Khai báo các loại advice trong `aop:aspect`

Spring XML schema hỗ trợ các loại advice quen thuộc, không bắt buộc dùng `around`:

| Element XML | Khi chạy | Điều kiện và chữ ký method |
| --- | --- | --- |
| `<aop:before>` | Trước khi gọi target | Method advice thường trả `void`, có thể nhận `JoinPoint`. |
| `<aop:after-returning>` | Target/chuỗi advice hoàn thành bình thường | Có thể dùng `returning="retVal"` để nhận kết quả. |
| `<aop:after-throwing>` | Target/chuỗi advice thoát bằng exception phù hợp | Có thể dùng `throwing="error"` để nhận exception. |
| `<aop:after>` | Sau khi method kết thúc, kể cả thất bại | Giống ý nghĩa `finally`; không dùng để thay thế xử lý trả về. |
| `<aop:around>` | Bao quanh invocation | Method phải nhận `ProceedingJoinPoint` ở vị trí đầu, trả `Object` và gọi `proceed()` nếu muốn thực thi target. |

Ví dụ khai báo thay thế **không phải phần của cấu hình chạy ở mục 4**:

~~~xml
<aop:aspect id="audit" ref="auditAdviceBean">
    <aop:before pointcut-ref="payOperation" method="beforePayment"/>
    <aop:after-returning pointcut-ref="payOperation"
                         method="afterSuccess"
                         returning="result" arg-names="result"/>
    <aop:after-throwing pointcut-ref="payOperation"
                        method="afterError"
                        throwing="error" arg-names="error"/>
    <aop:after pointcut-ref="payOperation" method="cleanup"/>
</aop:aspect>
~~~

Các method tương ứng trong bean `auditAdviceBean`, ví dụ:

~~~java
public void beforePayment() {
    // Chạy trước.
}

public void afterSuccess(Object result) {
    // Nhận giá trị trả về khi thành công.
}

public void afterError(Exception error) {
    // Nhận exception khớp kiểu tham số.
}

public void cleanup() {
    // Chạy khi invocation kết thúc.
}
~~~

Đây là **các method minh họa nằm trong class advice**, chưa là tệp Java độc lập. `returning` và `throwing` đặt tên biến được bind với tham số method advice; `arg-names` giúp khai báo binding rõ khi metadata tên tham số trong bytecode không sẵn. Chữ ký `Exception` chỉ khớp exception tương thích; một advice bắt `Throwable` có phạm vi rộng hơn.

### Advice nào phù hợp?

- Chỉ cần kiểm tra/log trước: ưu tiên `before`.
- Chỉ cần xử lý kết quả trả về: `after-returning`.
- Chỉ cần ghi nhận lỗi: `after-throwing`.
- Cần cleanup dù thành công hay lỗi: `after`.
- Cần đo thời gian bao gồm trước/sau, thay đổi đối số, quyết định retry hoặc chặn gọi target: `around`. Với `around`, quên gọi `proceed()` khiến method đích không chạy.

Nếu nhiều advice cần chạy ở cùng join point, phải xác định thứ tự; không suy ra thứ tự chỉ bằng tên method Java.

## 6. Thứ tự áp dụng aspect và các cấu hình mở rộng

### Thứ tự giữa các aspect

Có thể khai báo `order` trực tiếp:

~~~xml
<aop:config>
    <aop:pointcut id="operation"
                  expression="execution(* demo.BillingService.pay(..))"/>

    <aop:aspect id="security" ref="securityAdviceBean" order="0">
        <aop:before method="verify" pointcut-ref="operation"/>
    </aop:aspect>

    <aop:aspect id="tracing" ref="traceAdviceBean" order="10">
        <aop:around method="trace" pointcut-ref="operation"/>
    </aop:aspect>
</aop:config>
~~~

Đây là fragment minh họa: hai bean advice và method phải tồn tại. **Giá trị order nhỏ hơn có độ ưu tiên cao hơn.** Advice ưu tiên cao chạy trước trên đường vào, sau trên đường ra. Khi nhiều advice ở *cùng một* `<aop:aspect>`, theo tài liệu Spring, thứ tự khai báo các advice element quyết định độ ưu tiên; nếu logic trở nên phức tạp, tách aspect và kiểm thử thứ tự thực tế.

### `aop:advisor` khác gì `aop:aspect`?

~~~xml
<aop:config>
    <aop:pointcut id="serviceOps"
                  expression="execution(* demo.BillingService.pay(..))"/>
    <aop:advisor pointcut-ref="serviceOps" advice-ref="methodInterceptorBean"/>
</aop:config>
~~~

`aop:advisor` kết nối một **bean triển khai interface advice của Spring AOP** (ví dụ `MethodInterceptor`) với pointcut; còn `aop:aspect` cho phép nhóm nhiều phương thức advice Java bình thường vào một aspect bằng XML. Nếu đồng thời có advisor và aspect trong một `aop:config` thì đặt advisor trước aspect như yêu cầu của schema.

### `<aop:aspectj-autoproxy/>` không thay thế `<aop:aspect>`

`<aop:aspectj-autoproxy/>` kích hoạt auto-detection cho các bean khai báo `@Aspect`; `<aop:config>` là cách cấu hình pointcut/advice trực tiếp bằng XML. Hai kiểu đều dùng Spring AOP, nhưng metadata khai báo khác nhau. Với ví dụ ở mục 4, chỉ cần `<aop:config>` và bean Java bình thường.

## 7. Những giới hạn proxy-based AOP cần biết

XML schema-based AOP chỉ can thiệp **method execution đi qua Spring proxy** của bean được quản lý. Tạo đối tượng bằng `new` hoặc gọi `this.inner()` từ target sẽ không tự kích hoạt interceptor mới. JDK proxy thường expose interface methods; CGLIB subclass proxy không override được private/final method hoặc final class.

Một `aop-context.xml` chưa được import/load vào Spring context sẽ không tạo advisor, dù file nằm trên classpath. Lớp advice thường là singleton: lưu dữ liệu riêng invocation trong biến cục bộ, không trong field mutable dùng chung. Bài chuyên sâu về **proxy-target-class/expose-proxy** giải thích và thử từng giới hạn proxy.

## 8. Chẩn đoán khi cấu hình XML không hoạt động

Nếu XML parse được nhưng advice không chạy, xác minh theo thứ tự: (1) context đã nạp đúng XML và các bean ID `ref` đều tồn tại; (2) `method` khớp chữ ký Java trên advice bean; (3) `pointcut-ref`/expression khớp class/method thực tế; (4) target lấy từ Spring context và lời gọi qua proxy; (5) có advisor khác hoặc cấu hình chồng lấn thay đổi hành vi không.

Dùng `AopUtils.isAopProxy(bean)` như phép kiểm tra cấu hình bổ trợ, rồi **assert bộ đếm/event trong advice** cho một method phải match, một method không match, cả success và exception path. Một bean được proxy không có nghĩa tất cả advice đều chạy trên mọi method.

## 9. Checklist triển khai

- [ ] File XML đã khai báo `beans`, `aop` namespace và các XSD phù hợp.
- [ ] Bean target và bean advice được quản lý trong **cùng ApplicationContext phù hợp** hoặc context có khả năng nhìn thấy nhau.
- [ ] Các phần tử con của `aop:config` đúng thứ tự schema; `ref`, `pointcut-ref`, `method` đều tồn tại.
- [ ] Biểu thức pointcut chỉ khớp method/package cần áp dụng; điều kiện kết hợp được escape hợp lệ trong XML.
- [ ] Lựa chọn loại advice ít quyền lực nhất đáp ứng nhu cầu; với `around` có `proceed()` và preserve exception/return value đúng yêu cầu.
- [ ] Kiểm tra class/interface proxy, method public/final, self-invocation và cách caller lấy bean.
- [ ] Test qua `ClassPathXmlApplicationContext` hoặc Spring context thật, đo advice invocation thay vì chỉ xem response.
- [ ] Nếu có nhiều aspect, test thứ tự advice với `order` hoặc `Ordered`.
- [ ] Không lưu dữ liệu request-specific vào mutable field của aspect singleton.

**Kết quả mong đợi:** một bean nghiệp vụ giữ nguyên code, Spring tạo proxy từ `<aop:config>`, chạy phương thức advice của bean tham chiếu bởi `<aop:aspect ref="...">` và trả đúng giá trị/ngoại lệ cho caller. Những điều này được kiểm chứng trong test, không chỉ suy ra từ việc XML parse thành công.

## Tài liệu tham khảo

- Spring Framework Reference — Schema-based AOP Support: https://docs.spring.io/spring-framework/reference/core/aop/schema.html
- Spring Framework Reference — AOP Concepts: https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html
- Spring Framework Reference — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework Reference — Declaring a Pointcut: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-aop-configuration-004">Pointcut có tên và pointcut-ref trong XML</a>

<details>
<summary>Xem chi tiết</summary>

# Pointcut có tên và `pointcut-ref` trong cấu hình XML của Spring AOP

## 1. Vấn đề cần giải quyết

Trong Spring AOP, một ứng dụng có thể cần áp dụng nhiều hành vi cắt ngang cho cùng nhóm phương thức: kiểm tra đầu vào, ghi nhận sự kiện, đo thời gian, hoặc quản lý giao dịch. Nếu mỗi advice tự viết lại biểu thức pointcut, phạm vi chặn dễ bị lệch sau khi đổi tên phương thức hoặc tổ chức lại gói.

**Pointcut có tên (named pointcut)** là cách khai báo biểu thức chọn phương thức một lần trong cấu hình Spring XML:

```xml
<aop:pointcut id="orderOperations"
              expression="execution(* example.OrderService.place(..))"/>
```

Các advice và advisor sau đó dùng **`pointcut-ref="orderOperations"`**. `pointcut-ref` là tham chiếu tới **`id` của phần tử `aop:pointcut`**, không phải tên phương thức Java, tên aspect bean hay biểu thức AspectJ.

```text
                  <aop:pointcut id="orderOperations">
                                 |
                    +------------+------------+
                    |                         |
     <aop:before pointcut-ref>    <aop:after-returning pointcut-ref>
                    |                         |
            Trước khi thực thi        Khi trả về bình thường
                    +------------+------------+
                                 |
                  Spring AOP proxy chọn
                  OrderService.place(..)
```

Điểm cốt lõi là **một định nghĩa phạm vi, nhiều vị trí sử dụng**. Spring vẫn chặn các lời gọi đi qua **proxy Spring**; XML không biến Spring AOP thành bộ công cụ chặn mọi điểm thực thi trong JVM.

### Ba phần tử cần phân biệt

| Phần tử XML | Vai trò | Giá trị thuộc tính quan trọng |
| --- | --- | --- |
| `<aop:pointcut>` | Đặt tên cho một biểu thức chọn join point | `id`, `expression` |
| `<aop:aspect>` | Gắn các advice XML với **một Spring bean** có phương thức advice | `ref` trỏ tới bean; có thể có `order` |
| `<aop:before>`, `<aop:after-returning>`, `<aop:around>`... | Chọn lúc chạy advice và phương thức Java xử lý | `method`, **`pointcut-ref` hoặc `pointcut`** |
| `<aop:advisor>` | Gắn một bean `Advice`/interceptor với pointcut | `advice-ref`, `pointcut-ref`, có thể có `order` |

Một `<aop:aspect ref="traceAdvice">` tham chiếu **bean chứa phương thức advice thông thường**, còn `<aop:advisor advice-ref="someInterceptor">` đòi bean phù hợp với giao diện `Advice`/interceptor. Không hoán đổi hai cách khai báo chỉ vì chúng cùng có thuộc tính `ref`.

## 2. Cú pháp Spring XML chính xác

Trong Spring XML schema, một named pointcut có **ID** và **biểu thức**; advice dùng một trong hai thuộc tính `pointcut-ref="id"` hoặc `pointcut="expression"`, không dùng cả hai:

~~~xml
<aop:config>
    <aop:pointcut id="placeCalls"
                  expression="execution(* example.OrderService.place(..))"/>
    <aop:aspect ref="traceAdvice">
        <aop:before method="before" pointcut-ref="placeCalls"/>
    </aop:aspect>
</aop:config>
~~~

Đây là fragment cần các bean `OrderService`, `traceAdvice` và Java method `before`. `pointcut-ref` phải là tên ID `placeCalls`, **không có `()`**. Trong `aop:config`, các phần tử ở cấp cao nhất có thứ tự **pointcut → advisor → aspect**. Kết hợp Boolean trong thuộc tính XML dùng `and`, `or`, `not` hoặc escape `&&` thành `&amp;&amp;`.

`proxy-target-class="true"` là tùy chọn nếu cần class proxy, không phải điều kiện để pointcut có tên hoạt động. Ví dụ runnable ở mục 5 khai báo một pointcut và hai aspect cùng tham chiếu nó.

## 3. Pointcut mức `aop:config` và pointcut bên trong `aop:aspect`

**Pointcut chung (top-level)** được khai báo là phần tử con trực tiếp của `aop:config`:

```xml
<aop:config>
    <aop:pointcut id="serviceCalls"
                  expression="execution(public * example..*Service.*(..))"/>

    <aop:aspect ref="auditAdvice">
        <aop:before method="recordAttempt"
                    pointcut-ref="serviceCalls"/>
    </aop:aspect>

    <aop:aspect ref="metricsAdvice">
        <aop:after-returning method="recordSuccess"
                             pointcut-ref="serviceCalls"/>
    </aop:aspect>
</aop:config>
```

Đây là **đoạn XML minh họa**, chỉ có thể dùng khi bạn đã khai báo các bean và phương thức tương ứng. Cả hai aspect chọn cùng một tập join point; thay biểu thức ở `serviceCalls` cập nhật phạm vi cho cả hai.

**Pointcut cục bộ của một aspect** được khai báo bên trong `aop:aspect`:

```xml
<aop:config>
    <aop:aspect ref="auditAdvice">
        <aop:pointcut id="localWrites"
                      expression="execution(* example..*Service.save*(..))"/>

        <aop:before method="recordAttempt"
                    pointcut-ref="localWrites"/>
        <aop:after-returning method="recordSuccess"
                             pointcut-ref="localWrites"/>
    </aop:aspect>
</aop:config>
```

Ở đây pointcut nằm cùng phần khai báo aspect, phù hợp khi chỉ các advice của aspect đó cần dùng. Nếu nhiều aspect/advisor phải dùng chung, đưa pointcut lên mức `aop:config` để hợp đồng phạm vi tập trung và dễ đọc hơn.

**Lưu ý quan trọng:** `id` của pointcut XML dùng để liên kết bằng `pointcut-ref`. Không thể viết một expression kiểu `expression="serviceCalls() and ..."` chỉ để gọi `id="serviceCalls"` như gọi một named `@Pointcut` trong Java. Theo tài liệu Spring, named pointcut được định nghĩa bằng XML **không được dùng như tên hàm để cấu thành một pointcut expression mới**. Nếu muốn composition qua tên hàm, có thể tham chiếu một phương thức `@Pointcut` đã được định nghĩa trong kiểu `@Aspect` bằng tên đủ điều kiện, ví dụ `example.SharedPointcuts.businessMethods()`, khi kiến trúc thực sự cần cách kết hợp đó. Cách đơn giản nhất trong XML thuần là khai báo expression cuối cùng dưới một `id` mới.

## 4. Phân biệt `pointcut-ref`, `pointcut`, `ref` và `advice-ref`

Ghi nhớ sự khác biệt nằm ở **loại định danh**:

| Thuộc tính | Giá trị là gì? |
| --- | --- |
| `<aop:pointcut id="placeCalls">` | ID của định nghĩa pointcut XML |
| `<aop:before pointcut-ref="placeCalls">` | Tham chiếu đúng pointcut ID |
| `<aop:before pointcut="execution(...)">` | Pointcut expression inline |
| `<aop:aspect ref="traceAdvice">` | ID của **Spring bean** chứa Java advice method |
| `<aop:advisor advice-ref="interceptorBean">` | ID bean implement Spring `Advice`/`MethodInterceptor` |
| `<aop:before method="before">` | Tên Java method trên bean `traceAdvice` |

`pointcut-ref="placeCalls()"` và `expression="placeCalls()"` không tham chiếu được **XML ID** bằng cú pháp named Java method. Một ID top-level dùng chung qua `pointcut-ref`; nếu cần kết hợp điều kiện, hãy viết biểu thức hoàn chỉnh trong định nghĩa pointcut.

## 5. Ví dụ chạy được: hai aspect cùng chia sẻ một named pointcut

Ví dụ dùng Java 17+, Spring Framework 6.2.8, AspectJ Weaver và Maven. Một pointcut mức `aop:config` chọn duy nhất `OrderService.place(..)`. Aspect kiểm tra số lượng và aspect ghi nhận sự kiện cùng dùng `pointcut-ref="placeCalls"`. Phương thức `status()` không bị chọn.

Cấu trúc thư mục:

```text
named-pointcut-demo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/example/
    │   │   ├── App.java
    │   │   ├── EventRecorder.java
    │   │   ├── OrderService.java
    │   │   ├── GuardAdvice.java
    │   │   └── TraceAdvice.java
    │   └── resources/spring-aop.xml
    └── test/java/example/XmlPointcutTest.java
```

### 5.1. Khai báo dependencies

`pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>named-pointcut-demo</artifactId>
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

### 5.2. Java: đối tượng đích và hai bean advice

`src/main/java/example/EventRecorder.java`:

```java
package example;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventRecorder {
    private final List<String> events = new CopyOnWriteArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }
}
```

`src/main/java/example/OrderService.java`:

```java
package example;

public class OrderService {
    private final EventRecorder recorder;

    public OrderService(EventRecorder recorder) {
        this.recorder = recorder;
    }

    public String place(String sku, int quantity) {
        recorder.add("target:place");
        return "PLACED:" + sku + ":" + quantity;
    }

    public String status() {
        recorder.add("target:status");
        return "READY";
    }
}
```

`src/main/java/example/GuardAdvice.java`:

```java
package example;

import org.aspectj.lang.JoinPoint;

public class GuardAdvice {
    private final EventRecorder recorder;

    public GuardAdvice(EventRecorder recorder) {
        this.recorder = recorder;
    }

    public void check(JoinPoint joinPoint) {
        int quantity = (Integer) joinPoint.getArgs()[1];
        if (quantity <= 0) {
            recorder.add("reject:" + quantity);
            throw new IllegalArgumentException("quantity must be positive");
        }
        recorder.add("guard:" + quantity);
    }
}
```

`src/main/java/example/TraceAdvice.java`:

```java
package example;

import org.aspectj.lang.JoinPoint;

public class TraceAdvice {
    private final EventRecorder recorder;

    public TraceAdvice(EventRecorder recorder) {
        this.recorder = recorder;
    }

    public void before(JoinPoint joinPoint) {
        recorder.add("before:" + joinPoint.getSignature().getName());
    }

    public void afterSuccess(JoinPoint joinPoint) {
        recorder.add("after:" + joinPoint.getSignature().getName());
    }
}
```

Hai class `GuardAdvice` và `TraceAdvice` ở đây **không có `@Aspect`**, vì chúng được kết nối thành schema-based aspects trong XML. Điều kiện chặn nằm trong `<aop:pointcut>`; phương thức Java chỉ chứa hành vi. `JoinPoint` là tham số mà Spring/AspectJ cung cấp cho advice và ở ví dụ này được dùng để xem method đang chặn và đối số.

### 5.3. XML: khai báo pointcut một lần, tham chiếu từ hai aspect

`src/main/resources/spring-aop.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd">

    <bean id="eventRecorder" class="example.EventRecorder"/>

    <bean id="orderService" class="example.OrderService">
        <constructor-arg ref="eventRecorder"/>
    </bean>

    <bean id="guardAdvice" class="example.GuardAdvice">
        <constructor-arg ref="eventRecorder"/>
    </bean>

    <bean id="traceAdvice" class="example.TraceAdvice">
        <constructor-arg ref="eventRecorder"/>
    </bean>

    <aop:config proxy-target-class="true">
        <!-- Pointcut có tên ở mức config: dùng chung cho nhiều aspect. -->
        <aop:pointcut id="placeCalls"
                      expression="execution(* example.OrderService.place(..))"/>

        <!-- Order thấp hơn nghĩa là precedence cao hơn. -->
        <aop:aspect id="guard" ref="guardAdvice" order="0">
            <aop:before method="check" pointcut-ref="placeCalls"/>
        </aop:aspect>

        <aop:aspect id="trace" ref="traceAdvice" order="1">
            <aop:before method="before" pointcut-ref="placeCalls"/>
            <aop:after-returning method="afterSuccess"
                                 pointcut-ref="placeCalls"/>
        </aop:aspect>
    </aop:config>
</beans>
```

**Điểm cần quan sát:** `placeCalls` xuất hiện đúng một lần ở thuộc tính `id` của pointcut nhưng được tham chiếu trong cả hai aspect và nhiều advice. Nếu muốn chọn thêm một method khác, đổi expression tại một nơi. `order="0"` của `guard` làm kiểm tra chạy trước `trace` ở `place()`; lỗi kiểm tra ngăn method đích và advice nằm phía trong thực thi.

### 5.4. Chạy context và kiểm tra hành vi

`src/main/java/example/App.java`:

```java
package example;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class App {
    public static void main(String[] args) {
        try (var context = new ClassPathXmlApplicationContext("spring-aop.xml")) {
            OrderService service = context.getBean("orderService", OrderService.class);
            EventRecorder events = context.getBean(EventRecorder.class);

            System.out.println("value=" + service.place("A-1", 2));
            System.out.println("status=" + service.status());

            try {
                service.place("A-1", 0);
            } catch (IllegalArgumentException ex) {
                System.out.println("error=" + ex.getMessage());
            }

            System.out.println("events=" + events.snapshot());
        }
    }
}
```

Lệnh chạy tại gốc `named-pointcut-demo`:

```bash
mvn -q compile exec:java -Dexec.mainClass=example.App
```

Stdout của phần logic (không tính log môi trường Maven/Spring nếu có):

```text
value=PLACED:A-1:2
status=READY
error=quantity must be positive
events=[guard:2, before:place, target:place, after:place, target:status, reject:0]
```

Diễn giải từng trường hợp:

- `place("A-1", 2)` khớp `execution(* example.OrderService.place(..))`; guard ghi `guard:2`, trace ghi `before:place`, target ghi `target:place`, sau khi trả về bình thường trace ghi `after:place`.
- `status()` **không khớp** pointcut nên chỉ ghi `target:status`. Đây là bằng chứng phạm vi chặn đủ hẹp.
- `place("A-1", 0)` khớp, nhưng guard có precedence cao hơn ném ngoại lệ trước khi target và trace chạy; `after-returning` cũng không chạy vì không có kết quả thành công.

Trong ứng dụng thực, tránh phụ thuộc vào thứ tự giữa nhiều advice nếu không khai báo precedence rõ. `order` điều khiển thứ tự aspect; không thay đổi nội dung expression. Và đây là một ví dụ kiểm tra tiền điều kiện trong advice để thấy interceptor order, không phải khuyến nghị đưa toàn bộ kiểm tra nghiệp vụ vào Spring AOP.

## 6. Kiểm thử tự động: named pointcut chỉ chặn đúng phương thức

`src/test/java/example/XmlPointcutTest.java`:

```java
package example;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlPointcutTest {
    @Test
    void namedPointcutIsSharedAndOnlyMatchesPlace() {
        try (var context = new ClassPathXmlApplicationContext("spring-aop.xml")) {
            OrderService service = context.getBean("orderService", OrderService.class);
            EventRecorder recorder = context.getBean(EventRecorder.class);

            assertTrue(AopUtils.isAopProxy(service));
            assertEquals("PLACED:A-1:2", service.place("A-1", 2));
            assertEquals("READY", service.status());

            assertEquals(List.of(
                    "guard:2",
                    "before:place",
                    "target:place",
                    "after:place",
                    "target:status"), recorder.snapshot());
        }
    }

    @Test
    void failingGuardStopsTargetAndInnerAdvice() {
        try (var context = new ClassPathXmlApplicationContext("spring-aop.xml")) {
            OrderService service = context.getBean("orderService", OrderService.class);
            EventRecorder recorder = context.getBean(EventRecorder.class);

            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.place("A-1", 0));

            assertEquals("quantity must be positive", error.getMessage());
            assertEquals(List.of("reject:0"), recorder.snapshot());
        }
    }
}
```

Chạy:

```bash
mvn -q test
```

Hai phép thử bổ sung nhau:

1. `AopUtils.isAopProxy(service)` kiểm tra bean thực sự được proxy. Kiểm tra danh sách sự kiện chứng minh cả guard và trace dùng đúng **cùng một named pointcut**; `status()` không bị ảnh hưởng.
2. Lần gọi lỗi chứng minh precedence đã thiết kế và việc không vô tình gọi target/advice sau khi guard từ chối. Nếu xóa `pointcut-ref` hoặc gõ nhầm tên `placeCalls`, application context có thể không khởi tạo; nếu nới expression thành `execution(* example.OrderService.*(..))`, test phạm vi sẽ phát hiện `status()` bị chặn.

Với hệ thống lớn, nên có test tương tự trên một interface/proxy JDK nếu cấu hình thực dùng `proxy-target-class="false"`. Test cấu hình XML chỉ gọi Java object tự tạo bằng `new OrderService(...)` sẽ không chứng minh Spring proxy đã chặn.

## 7. `pointcut-ref` trên advisor khác gì trên aspect?

Trong Spring XML có hai cách kết nối:

**Cách A — schema aspect**: một bean Java chứa nhiều method advice; `aop:before` và `aop:after-returning` gọi các method qua `method`.

```xml
<aop:aspect ref="traceAdvice">
    <aop:before method="before" pointcut-ref="placeCalls"/>
</aop:aspect>
```

**Cách B — Spring advisor**: một bean chuyên trách thực thi advice, ví dụ một implementation của `org.aopalliance.intercept.MethodInterceptor`, gắn với pointcut:

```xml
<!-- Trích đoạn để thêm bên trong <beans> và <aop:config> tương ứng. -->
<bean id="metricsInterceptor" class="example.MetricsInterceptor"/>

<aop:config>
    <aop:pointcut id="placeCalls"
                  expression="execution(* example.OrderService.place(..))"/>

    <aop:advisor advice-ref="metricsInterceptor"
                 pointcut-ref="placeCalls"
                 order="2"/>
</aop:config>
```

`MetricsInterceptor` cần triển khai `MethodInterceptor.invoke(MethodInvocation)` và gọi `invocation.proceed()` để tiếp tục chuỗi chặn. Trích đoạn trên **không thuộc bộ ví dụ runnable ở mục 5**; muốn dùng thì tạo bean/class thật và tránh lặp định nghĩa `placeCalls` trong cùng cấu hình.

Với giao dịch khai báo bằng XML, Spring còn dùng cách phối hợp `<tx:advice id="txAdvice">...</tx:advice>` rồi `<aop:advisor advice-ref="txAdvice" pointcut-ref="..."/>`. Khi ấy `pointcut-ref` vẫn chọn tập phương thức; `advice-ref` chọn chính sách giao dịch. Chúng không thay thế nhau.

**Điều kiện schema:** trong một `aop:config`, đặt `aop:advisor` **sau** các `aop:pointcut` và **trước** các `aop:aspect`. Đây là điểm khác với thứ tự Java annotation, bởi XML schema xác định vị trí phần tử.

## 8. Các lỗi cấu hình thường gặp và cách xử lý

Các lỗi riêng của named pointcut gồm: sai `pointcut-ref` ID làm Spring không resolve; di chuyển `<aop:pointcut>` sai vị trí XSD; nhầm XML ID với named Java method `pc()`; và sửa expression dùng chung nhưng thiếu negative tests cho những aspect khác. Sửa `pointcut-ref` bằng đúng ID không kèm `()`, kiểm tra vị trí/cấu trúc pointcut ở scope phù hợp, rồi khởi tạo context thật.

Khi đổi tên `id`, rà mọi `pointcut-ref`; khi đổi expression mà giữ ID, kiểm tra tác động với **tất cả consumer advisors/aspects**. Nếu advice không chạy dù ID hợp lệ, phân biệt mismatch của expression với việc caller bypass proxy. Với các toán tử `&&` trong XML, viết `and` hoặc `&amp;&amp;`.

## 9. Cách đặt tên và bảo trì pointcut XML

Nên đặt tên nói rõ **tập phương thức được chặn**, như `orderWrites`, `invoiceServiceOperations` hoặc `publicBusinessMethods`. Tên `pointcut1` hay `allMethods` không giúp reviewer phát hiện expression quá rộng. Chú ý ba loại thay đổi:

1. **Đổi expression mà giữ `id`:** mọi advice/advisor đang dùng `pointcut-ref` nhận phạm vi mới. Cần cập nhật test của cả những aspect tiêu thụ pointcut.
2. **Đổi `id`:** phải cập nhật mọi `pointcut-ref`; nếu bỏ sót, context có thể lỗi khi khởi tạo.
3. **Chuyển pointcut từ cục bộ ra mức `aop:config`:** giữ nguyên expression, điều chỉnh vị trí phần tử và tham chiếu để nhiều aspect có thể dùng chung; chạy kiểm thử hồi quy.

Nên gom các pointcut mang cùng ý nghĩa nghiệp vụ, nhưng đừng tái sử dụng một pointcut quá rộng cho các chính sách cần đối tượng khác nhau (chẳng hạn audit mọi service so với transaction chỉ áp dụng thao tác ghi). Một name có vẻ chung nhưng expression chứa danh sách method rất cụ thể cũng dễ bị stale khi refactor; test bảo vệ phạm vi chọn là cần thiết.

Nếu muốn tách cấu hình thành nhiều file XML, có thể dùng `<import resource="other-aop.xml"/>` theo cơ chế cấu hình Spring; bảo đảm nơi định nghĩa pointcut và nơi tham chiếu thực sự nằm trong phạm vi XML/schema và bean definition có thể được Spring xử lý. Với cấu hình phức tạp, test khởi tạo `ApplicationContext` là bằng chứng tốt hơn suy đoán rằng tên `id` toàn cục sẽ có hiệu lực bất kể nơi khai báo.

## 10. Bài tập kiểm chứng

Từ bộ ví dụ runnable, thực hiện theo trình tự:

1. Chạy `mvn -q test` rồi đổi expression `placeCalls` thành `execution(* example.OrderService.*(..))`. Quan sát test chặn ngoài dự kiến `status()`; trả lại expression ban đầu.
2. Đổi `pointcut-ref="placeCalls"` trong riêng `trace` thành một tên không tồn tại. Quan sát lỗi khởi tạo context; sửa lại đúng tên.
3. Trong aspect `trace`, thử khai báo một pointcut cục bộ dành cho `status()` và một advice riêng dùng `pointcut-ref` của nó. Kiểm tra `place()` và `status()` sinh các sự kiện đúng phạm vi.
4. Đổi `order` của các aspect và cập nhật test để thấy khác biệt thứ tự. Riêng trường hợp guard ném lỗi, hãy giải thích advice nào có cơ hội chạy trước.
5. Bổ sung một `MethodInterceptor` hợp lệ rồi gắn bằng `aop:advisor` vào **cùng top-level pointcut**. Chứng minh bằng test rằng advisor và schema aspect chia sẻ đúng tập lời gọi.

**Kết quả cần đạt:** bạn đọc được XML và xác định được expression nào chọn phương thức, `pointcut-ref` trỏ tới `id` nào, advice thuộc bean nào, trình tự thực thi ra sao, và test nào chứng minh không chặn thừa.

### Tài liệu chính thức

- Spring Framework 6.2 — *Schema-based AOP Support* (named pointcuts, scope, advisor, thứ tự): https://docs.spring.io/spring-framework/reference/6.2/core/aop/schema.html
- Spring Framework — *Spring AOP Proxies* (self-invocation, giới hạn proxy): https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — *Pointcut Expressions* (cú pháp `execution`, `args`, v.v.): https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/pointcuts.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-aop-configuration-005">aop:before, after-returning, after-throwing, after và around</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP XML: aop:before, aop:after-returning, aop:after-throwing, aop:after và aop:around

## 1. Mục tiêu và phạm vi

Spring Framework cung cấp **schema-based Spring AOP** cho những dự án muốn khai báo pointcut và advice **trong XML** thay vì ghi các annotation `@Aspect`, `@Before`, `@Around` trực tiếp vào class Java.

Năm phần tử advice quan trọng trong namespace `aop` là:

- `<aop:before>`: chạy trước method execution được pointcut chọn.
- `<aop:after-returning>`: chạy sau khi method execution **trả về bình thường**.
- `<aop:after-throwing>`: chạy khi method execution **kết thúc bằng exception phù hợp**.
- `<aop:after>`: dạng **after finally**, chạy khi method execution thoát ra theo cả hai cách.
- `<aop:around>`: bao quanh invocation, được quyền gọi `ProceedingJoinPoint.proceed()`, trả kết quả hoặc ném lại lỗi.

Tất cả là cơ chế **Spring Aspect trong Spring AOP**: chúng chặn những method executions trên **Spring-managed proxy**, không phải full AspectJ bytecode weaving.

Khác với cách dùng `@Aspect`:

```java
@Aspect
public class MyAspect {
    @Before("execution(* com.example.service.*.*(..))")
    public void before() { }
}
```

ở XML style, class giữ **các method Java thông thường**, không cần `@Aspect` hoặc `@Before`. Spring đọc cấu hình XML, tham chiếu Java object qua thuộc tính `ref`, rồi biến các method tương ứng thành advice:

```xml
<aop:aspect ref="auditAdvice">
    <aop:before pointcut-ref="businessOperation" method="before"/>
</aop:aspect>
```

Hai đoạn trên là **fragments**, không phải hai cấu hình cần kích hoạt cùng lúc. Bài đi sâu vào cách khai báo năm advice types, liên kết XML/Java, binding return/exception, ordering, kết quả chạy, lỗi cấu hình và các giới hạn của Spring proxy.

## 2. Hệ thống thành phần của XML AOP

Spring XML schema nối **target bean**, **advice POJO bean** và **pointcut** qua `<aop:config>`:

~~~xml
<bean id="paymentService" class="com.example.xmlaop.PaymentService"/>
<bean id="auditAdvice" class="com.example.xmlaop.AuditAdvice"/>
<aop:config>
    <aop:pointcut id="paymentOperation"
                  expression="execution(* com.example.xmlaop.PaymentService.pay(..))"/>
    <aop:aspect ref="auditAdvice">
        <aop:before method="before" pointcut-ref="paymentOperation"/>
    </aop:aspect>
</aop:config>
~~~

Đây là fragment: backing bean phải có method `before`. XML không đòi Java class chứa advice mang `@Aspect`. Trong `aop:config`, giữ thứ tự XSD **pointcut → advisor → aspect**; trong **cùng** `aop:aspect`, thứ tự khai báo advice có ảnh hưởng precedence (mục 8).

`pointcut-ref` chỉ chọn method execution được áp dụng; `method` là tên Java advice callback; `ref` trên `aop:aspect` là bean ID. Dùng `pointcut` inline thay vì `pointcut-ref` nếu biểu thức chỉ cần một lần. Các thẻ này không làm Spring AOP thoát giới hạn proxy.

## 3. `<aop:before>` — chạy trước invocation

Before advice chạy trước method execution được chọn. Nó phù hợp cho logging nhẹ, đếm số lượt gọi, validate những điều kiện cho phép chặn bằng exception, hoặc đặt metadata cần thiết trước khi đi vào target.

XML:

```xml
<aop:before
    pointcut-ref="paymentOperation"
    method="before"/>
```

Java backing method:

```java
public void before() {
    System.out.println("[BEFORE]");
}
```

Đây là fragments của Java advice bean và XML aspect. Spring gọi `before()` khi có match. Nếu `before()` ném exception, target có thể **không được chạy** vì execution bị chặn trước khi chuyển tiếp.

**Giới hạn:** `before()` không có `ProceedingJoinPoint` để quyết định gọi target bao nhiêu lần, và không thể return một replacement method result. Nếu chỉ cần chạy trước method, `before` đơn giản và ít nguy cơ hơn `around`.

Muốn biết tên method hoặc arguments, Spring XML advice có thể nhận `JoinPoint` làm tham số đầu:

```java
public void before(org.aspectj.lang.JoinPoint jp) {
    System.out.println("method=" + jp.getSignature().getName());
}
```

Đây là fragment. Không cache raw `JoinPoint` vào mutable singleton advice bean field để dùng chung giữa các requests; mỗi invocation có metadata riêng.

## 4. `<aop:after-returning>` — chỉ khi trả về bình thường

After-returning advice chạy khi method execution **không ném lỗi ra ngoài** và có normal return. Nó không chạy trên exception path tương ứng.

XML không bind return value:

```xml
<aop:after-returning
    pointcut-ref="paymentOperation"
    method="returned"/>
```

Java:

```java
public void returned() {
    System.out.println("[RETURNED]");
}
```

Nếu cần nhận kết quả trả về, thêm thuộc tính `returning`:

```xml
<aop:after-returning
    pointcut-ref="paymentOperation"
    method="returnedValue"
    returning="result"
    arg-names="result"/>
```

Java:

```java
public void returnedValue(Object result) {
    System.out.println("[RETURNED] result=" + result);
}
```

Các fragments này không được dùng đồng thời với cả hai method nếu không chủ đích muốn **hai return advices**. `returning="result"` là **binding identifier**, cần được ánh xạ tới Java advice parameter. `arg-names="result"` khai báo tên tường minh, tránh phụ thuộc vào compiler metadata hoặc parameter name inference. Kiểu của Java parameter có thể **giới hạn các return values được binding/matching**; dùng type phù hợp với target method.

### 4.1. Phân biệt return value và final outcome

Ví dụ service:

```java
public CompletableFuture<String> start() {
    return new CompletableFuture<>();
}
```

`<aop:after-returning>` có thể chạy ngay khi method đã trả về một **pending Future**. Nó **không chứng minh async task đã xong**; một Future còn có thể hoàn thành exceptionally về sau.

Tương tự, method trả `Mono` hoặc `Flux` có thể return **Publisher** trước subscription. `after-returning` quan sát việc Java method trả Publisher, không phải reactive `onComplete`/`onError`.

Nếu cần quan sát async completion, dùng `CompletionStage.whenComplete` hoặc Reactor operators phù hợp, không suy từ XML advice lifecycle.

### 4.2. Không thay replacement result bằng gán lại local

```java
public void returnedValue(Object result) {
    result = "different";  // Chỉ gán biến local.
}
```

Đây là anti-pattern fragment: caller **không tự nhận** chuỗi mới. Muốn thay kết quả được trả cho caller, nên dùng `around` và return replacement tương thích contract, hoặc thiết kế transformation trong service.

## 5. `<aop:after-throwing>` — chỉ khi invocation thoát bằng exception

After-throwing advice chạy khi method execution bị lỗi và thoát qua exception. Để bind exception, dùng thuộc tính `throwing`:

```xml
<aop:after-throwing
    pointcut-ref="paymentOperation"
    method="thrown"
    throwing="error"
    arg-names="error"/>
```

Backing method:

```java
public void thrown(Throwable error) {
    System.out.println("[THROW] "
            + error.getClass().getSimpleName());
}
```

`throwing="error"` map exception được quan sát vào Java parameter tên `error`. Nếu Java type là `IllegalArgumentException`, advice chỉ áp dụng cho exceptions phù hợp kiểu ấy theo Spring type restriction; `Throwable` rộng hơn.

**Không suy rằng `after-throwing` sẽ nuốt lỗi hoặc phục hồi kết quả.** Sau khi advice ghi log, exception thông thường vẫn được propagate. Muốn retry, fallback hoặc đổi exception, dùng `around` một cách có kiểm soát; không nên dùng after-throwing như một cách lặng lẽ thay đổi control flow.

### 5.1. Lỗi nào được quan sát?

| Kịch bản | `after-throwing` nhắm method? |
| --- | --- |
| Target method ném `IllegalStateException`, lỗi thoát ra | Có, nếu pointcut/type match |
| Target method trả bình thường | Không |
| Method trả pending `CompletableFuture`, Future fail sau return | Không tự chạy theo future completion |
| Method trả `Mono.error(ex)` bình thường, sau đó subscribe mới phát error | Không tự chạy chỉ vì reactive `onError` |
| Method ném lỗi ngay lúc xây dựng Publisher trước return | Có, nếu lỗi thoát từ invocation |
| Inner advice bắt một lỗi và return fallback, outer advice thấy normal return | Phụ thuộc vị trí advisor chain; lỗi có thể không vượt ranh giới được quan sát |

Trong project demo bên dưới, `PaymentService.pay("fail")` ném `IllegalStateException` **đồng bộ**, nên `<aop:after-throwing>` được kích hoạt như dự kiến.

## 6. `<aop:after>` — after finally, không chỉ sau thành công

`<aop:after>` có **finally semantics**:

```xml
<aop:after
    pointcut-ref="paymentOperation"
    method="afterFinally"/>
```

Java:

```java
public void afterFinally() {
    System.out.println("[FINALLY]");
}
```

Nó chạy khi method invocation thoát ra **normal return hoặc exception**, phù hợp để release resource/clear trạng thái có lifecycle cùng invocation.

**Không nhầm** `after` với `after-returning`:

| Tình huống | `after-returning` | `after-throwing` | `after` |
| --- | --- | --- | --- |
| Normal return | Có | Không | Có |
| Exception được propagate | Không | Có nếu type phù hợp | Có |
| Method không được gọi vì outer `around` short-circuit | Inner advice/target không chạy, nên thường không có các advice inner | Tương tự | Tương tự |
| Future trả ra bình thường nhưng fail về sau | Đã chạy ở mốc method return | Không chạy vì future late failure | Đã chạy ở mốc method return |

**Exception từ chính advice khác:** kết quả quan sát còn phụ thuộc precedence/chain; đừng coi mọi advice luôn áp trực tiếp target mà không có các advisors khác. Khi cleanup cần bảo toàn lỗi gốc, logic cleanup cũng cần được thiết kế để không vô tình ném lỗi mới che mất nguyên nhân ban đầu.

## 7. `<aop:around>` — điều khiển `proceed()`, return và throw

Around advice bao quanh method execution. Nó có thể ghi log trước/sau target, trả fallback, rút ngắn lời gọi hoặc retry theo policy. Đó là advice có quyền điều khiển mạnh nhất, nên chỉ dùng khi `before/after` không đủ.

XML:

```xml
<aop:around
    pointcut-ref="paymentOperation"
    method="around"/>
```

Java:

```java
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    System.out.println("[AROUND] enter");
    try {
        Object result = pjp.proceed();
        System.out.println("[AROUND] return");
        return result;
    } catch (Throwable error) {
        System.out.println("[AROUND] throw");
        throw error;
    }
}
```

Đây là method fragment dành cho backing aspect bean. Phương thức around advice **phải** nhận `ProceedingJoinPoint` làm **tham số đầu tiên**, thông thường return `Object` và khai báo `throws Throwable` vì `proceed()` có thể ném Throwable.

### 7.1. `proceed()` không phải bước tự động

`pjp.proceed()` chuyển tiếp invocation vào phần interceptor chain còn lại. Nếu không gọi `proceed()`, target có thể **không được thực thi** và caller chỉ nhận giá trị do around advice tự trả (nếu return type hợp lệ).

```java
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    if (cacheHit()) {
        return cachedValue();
    }
    return pjp.proceed();
}
```

Đây là fragment minh họa cache short-circuit, cần hai helpers có hợp đồng cache rõ ràng. Không dùng kiểu ngắt này cho phương thức có side effects nếu cache policy chưa chứng minh tương đương.

### 7.2. `proceed()` nhiều lần

Trong Spring AOP, around advice được phép gọi `proceed()` 0, 1 hoặc nhiều lần. Nhiều lần có thể dùng để retry, nhưng cũng có thể **lặp target và downstream advices, lặp side effects**:

```java
public Object retry(ProceedingJoinPoint pjp) throws Throwable {
    try {
        return pjp.proceed();
    } catch (TemporaryFailure ex) {
        return pjp.proceed();
    }
}
```

Đây là fragment yêu cầu `TemporaryFailure` do ứng dụng định nghĩa. Retry phải có giới hạn, classification transient errors, idempotency/transaction boundaries và observability. Không gọi lại `proceed()` để “kiểm thử” target trên operation trừ tiền, gửi email hoặc publish event nếu operation không an toàn để lặp.

### 7.3. `proceed(Object[] arguments)`

Với Spring proxy-based AOP, overload `proceed(Object[])` cho phép chuyển tiếp arguments được thay thế; mảng phải phù hợp **số lượng và kiểu của underlying Java method arguments**:

```java
Object[] args = pjp.getArgs().clone();
args[0] = normalize((String) args[0]);
return pjp.proceed(args);
```

Đây là fragment, cần `normalize`. Không tự cho rằng chỉ gán lại Java parameter local trong advice làm caller/target nhận argument mới; phải truyền đúng `args` tới `proceed(args)`.

### 7.4. Timing an toàn trong around

```java
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    long started = System.nanoTime();
    try {
        return pjp.proceed();
    } finally {
        long elapsed = System.nanoTime() - started;
        metrics.record(elapsed);
    }
}
```

Đây là fragment. `started` là **local variable**, tránh race khi backing advice bean dùng singleton scope. Nếu target trả Future/Publisher, duration trên chỉ đo **Java method invocation**, không tự đo eventual async/reactive completion.

## 8. Thứ tự advice trong **cùng** XML `<aop:aspect>`

Đây là điểm khác biệt dễ sai nhất khi chuyển giữa XML và `@AspectJ` annotation-style.

**Spring XML schema AOP có quy tắc: khi nhiều advice elements trong cùng `<aop:aspect>` cùng match join point, precedence từ cao đến thấp được xác định bởi **thứ tự khai báo các phần tử advice trong XML**.**

Ví dụ:

```xml
<aop:aspect ref="auditAdvice">
    <!-- Precedence cao nhất trong aspect XML này -->
    <aop:around method="around" pointcut-ref="paymentOperation"/>
    <aop:before method="before" pointcut-ref="paymentOperation"/>
    <aop:after method="afterFinally" pointcut-ref="paymentOperation"/>
    <aop:after-returning method="returnedValue"
        returning="result" arg-names="result"
        pointcut-ref="paymentOperation"/>
    <aop:after-throwing method="thrown"
        throwing="error" arg-names="error"
        pointcut-ref="paymentOperation"/>
</aop:aspect>
```

Với sequence XML trên, `around` là outer advice; `before` chạy trên đường vào; `after` có precedence cao hơn các advice return/throw được đặt sau nó, nên theo mô hình nested finally nó chạy **sau** `after-returning` hoặc `after-throwing` trên đường ra.

Đối chiếu một normal return:

```text
[AROUND] enter
[BEFORE]
[TARGET] execute
[RETURNED] result=...
[FINALLY]
[AROUND] return
```

Với exception:

```text
[AROUND] enter
[BEFORE]
[TARGET] throws exception
[THROW] IllegalStateException
[FINALLY]
[AROUND] throw
```

Nếu đổi thứ tự các XML advice elements, precedence/relative execution có thể thay đổi. **Đừng hoán đổi order tùy ý chỉ để nhóm các tag theo alphabet**, nhất là khi around/after/throwing được dùng cùng lúc.

### 8.1. Không đem quy tắc XML áp dụng cho annotation-style `@Aspect`

Trong một Java class `@Aspect` có hai `@Before` advice cùng match, Spring **không bảo đảm** thứ tự dựa trên vị trí khai báo hai Java methods. Java reflection không cung cấp source declaration order như một contract. Spring annotation-style có quy tắc ordering theo loại advice và `@After` finally semantics riêng.

**Riêng XML `<aop:aspect>`**, Spring docs quy định rõ declaration order của **các advice elements** dùng để quyết định precedence trong cùng aspect. Đây là ngoại lệ chủ ý, không phải mâu thuẫn.

### 8.2. Giữa **nhiều aspects** thì dùng `order`

```xml
<aop:aspect ref="securityAdvice" order="1">
    <aop:before method="check" pointcut-ref="paymentOperation"/>
</aop:aspect>

<aop:aspect ref="auditAdvice" order="10">
    <aop:around method="around" pointcut-ref="paymentOperation"/>
</aop:aspect>
```

Đây là fragment; các bean `securityAdvice`/`auditAdvice` và pointcut phải tồn tại. Aspect với **giá trị order nhỏ hơn** có precedence **cao hơn**. High-precedence advice chạy trước trên đường vào, sau trên đường ra.

Có thể dùng `@Order` trên backing bean hoặc implement `Ordered` theo cơ chế Spring, nhưng không nên trộn các cơ chế với giá trị mâu thuẫn rồi tự đoán thứ tự cuối. `<aop:advisor>` cũng có thuộc tính `order`, ảnh hưởng chain khi kết hợp transaction/caching/security advisors.

**Khuyến nghị:** Nếu một `<aop:aspect>` có quá nhiều advice cùng join point mà order khó hiểu, cân nhắc gom logic vào một method advice rõ ràng hoặc tách thành nhiều aspects có `order` tường minh.

## 9. Binding parameters trong XML: `returning`, `throwing` và `arg-names`

Ba khái niệm có vai trò khác nhau:

| XML attribute | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| `method` | `method="returnedValue"` | Tên Java method trên backing advice bean |
| `pointcut` | `pointcut="execution(...)"` | Expression chọn method execution |
| `pointcut-ref` | `pointcut-ref="paymentOperation"` | Dùng named pointcut đã khai báo |
| `returning` | `returning="result"` | Bind **normal return value** vào parameter |
| `throwing` | `throwing="error"` | Bind **thrown exception** vào parameter |
| `arg-names` | `arg-names="result"` | Ánh xạ formal parameter names tường minh |
| `ref` ở `<aop:aspect>` | `ref="auditAdvice"` | Bean chứa Java advice methods |
| `order` ở `<aop:aspect>` | `order="10"` | Precedence giữa aspects |

### 9.1. Bind Java method argument

XML:

```xml
<aop:before
    method="beforeWithArgument"
    pointcut="execution(* com.example.xmlaop.PaymentService.pay(..)) and args(reference)"
    arg-names="reference"/>
```

Java:

```java
public void beforeWithArgument(String reference) {
    System.out.println("reference=" + reference);
}
```

Hai fragments dùng `args(reference)` như một **binding identifier**; nó không phải tên tham số “tự động đọc” từ target method. `args` match **argument position và type** phù hợp, rồi chuyển giá trị vào Java advice method.

Nếu Java advice có leading `JoinPoint`, Spring có quy tắc đặc biệt cho phép bỏ tên leading join point khỏi `arg-names`. Với around advice, leading `ProceedingJoinPoint` cũng được xử lý tương ứng. Tránh dùng overload advice Java methods cùng tên khiến matching/binding khó dự đoán.

### 9.2. Return type/exception type làm điều kiện binding

`returnedValue(String result)` chỉ hợp với những return values Spring có thể bind vào `String`. `thrown(IllegalStateException error)` quan sát loại lỗi tương ứng; `thrown(Throwable error)` rộng hơn. Không dùng type restriction vô tình loại bỏ các exceptions/results cần logging.

`returning` và `throwing` **không phải** cách thay thế kết quả/exception bằng gán lại advice parameter. Nếu thật sự cần transform, hãy dùng around với behavior contract rõ ràng.

### 9.3. Tên parameter và compiler

`arg-names` trong XML tương đương ý định của `argNames` trong annotation-style, cho phép tránh phụ thuộc vào Java `-parameters` và cơ chế suy luận tên parameter. Dù vậy, Java parameter **thứ tự, số lượng và type** vẫn phải khớp. Khi tăng/giảm parameters, cần cập nhật cả XML declaration và Java method signature rồi kiểm thử context startup.

## 10. Ví dụ chạy được: Maven + Java 17 + Spring Framework XML AOP

Project này minh họa **đủ cả năm loại advice trong cùng `<aop:aspect>`**, thứ tự XML được chủ động thiết kế để có stdout xác định.

Bộ phiên bản cố định: Java 17+, Spring Framework 6.1.14, AspectJ Weaver 1.9.22.1, Maven. Đây là phiên bản minh họa, **không khẳng định phiên bản mới nhất**. Không dùng Spring Boot hoặc AspectJ weaving để giữ trọng tâm vào schema-based Spring AOP.

### 10.1. Cấu trúc thư mục

```text
spring-xml-five-advices/
├── pom.xml
└── src/main/
    ├── java/com/example/xmlaop/
    │   ├── PaymentService.java
    │   ├── AuditAdvice.java
    │   └── DemoApp.java
    └── resources/
        └── applicationContext.xml
```

### 10.2. `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-xml-five-advices</artifactId>
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

Spring `spring-context` kéo `spring-aop` cùng nhóm dependencies liên quan qua dependency tree cho ví dụ; `aspectjweaver` hỗ trợ pointcut expression parser. Không cần thêm `@Aspect` trên Java advice POJO.

### 10.3. `src/main/java/com/example/xmlaop/PaymentService.java`

```java
package com.example.xmlaop;

public class PaymentService {
    public String pay(String mode) {
        System.out.println("[TARGET] pay " + mode);

        if ("fail".equals(mode)) {
            throw new IllegalStateException("simulated");
        }
        return "paid:" + mode;
    }

    public String health() {
        System.out.println("[TARGET] health");
        return "UP";
    }
}
```

`pay("ok")` trả bình thường; `pay("fail")` ném đồng bộ; `health()` dùng để chứng minh **pointcut chỉ nhắm pay()**, không chặn mọi method trên bean.

### 10.4. `src/main/java/com/example/xmlaop/AuditAdvice.java`

```java
package com.example.xmlaop;

import org.aspectj.lang.ProceedingJoinPoint;

public class AuditAdvice {

    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[AROUND] enter");
        try {
            Object result = pjp.proceed();
            System.out.println("[AROUND] normal exit");
            return result;
        } catch (Throwable ex) {
            System.out.println("[AROUND] exceptional exit");
            throw ex;
        }
    }

    public void before() {
        System.out.println("[BEFORE]");
    }

    public void afterFinally() {
        System.out.println("[FINALLY]");
    }

    public void returnedValue(Object result) {
        System.out.println("[RETURNED] result=" + result);
    }

    public void thrown(Throwable error) {
        System.out.println("[THROW] "
                + error.getClass().getSimpleName());
    }
}
```

Đây là **Java POJO thuần**. Không có `@Aspect` hoặc `@Around` annotations. Spring XML sẽ chọn từng method bằng `method` attribute.

`around(ProceedingJoinPoint)` dùng local variable để giữ result/exception; `afterFinally()` không có tham số. `returnedValue(Object result)` và `thrown(Throwable error)` nhận binding thông qua XML `returning`/`throwing`. Có cả success và exception paths nhưng **không nuốt exception**.

### 10.5. `src/main/resources/applicationContext.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd">

    <!-- Target được Spring quản lý -->
    <bean id="paymentService"
          class="com.example.xmlaop.PaymentService"/>

    <!-- Backing advice POJO: không cần @Aspect -->
    <bean id="auditAdvice"
          class="com.example.xmlaop.AuditAdvice"/>

    <aop:config proxy-target-class="true">
        <!-- Pointcut hẹp, chỉ chọn pay(String) -->
        <aop:pointcut
            id="paymentOperation"
            expression="execution(public * com.example.xmlaop.PaymentService.pay(..))"/>

        <aop:aspect id="paymentAudit" ref="auditAdvice">
            <!-- Precedence cao xuống thấp, XML declaration order -->
            <aop:around
                method="around"
                pointcut-ref="paymentOperation"/>
            <aop:before
                method="before"
                pointcut-ref="paymentOperation"/>
            <aop:after
                method="afterFinally"
                pointcut-ref="paymentOperation"/>
            <aop:after-returning
                method="returnedValue"
                returning="result"
                arg-names="result"
                pointcut-ref="paymentOperation"/>
            <aop:after-throwing
                method="thrown"
                throwing="error"
                arg-names="error"
                pointcut-ref="paymentOperation"/>
        </aop:aspect>
    </aop:config>
</beans>
```

**Lý do chọn XML order này:** `aop:around` ở ngoài cùng; `aop:before` chạy trước target; `aop:after` có precedence cao hơn các after-returning/after-throwing elements được khai báo sau nó nên cleanup/finally theo đường unwind diễn ra sau chúng. Vị trí các child XML tags **không phải thứ tự thời gian giản đơn**, vì có chiều đi vào và đi ra.

`proxy-target-class="true"` chọn class-based CGLIB proxy, giúp `PaymentService` không implement interface vẫn được lấy bằng concrete class từ context. Dù proxy style khác, logic Java advice bean vẫn là POJO.

### 10.6. `src/main/java/com/example/xmlaop/DemoApp.java`

```java
package com.example.xmlaop;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DemoApp {
    public static void main(String[] args) {
        try (ClassPathXmlApplicationContext context =
                 new ClassPathXmlApplicationContext("applicationContext.xml")) {

            PaymentService service =
                    context.getBean("paymentService", PaymentService.class);

            System.out.println("isAopProxy="
                    + AopUtils.isAopProxy(service));

            System.out.println("[CALLER] success");
            String success = service.pay("ok");
            System.out.println("[CALLER] result=" + success);

            System.out.println("[CALLER] failure");
            try {
                service.pay("fail");
            } catch (IllegalStateException ex) {
                System.out.println("[CALLER] caught="
                        + ex.getMessage());
            }

            System.out.println("[CALLER] health");
            System.out.println("[CALLER] result=" + service.health());
        }
    }
}
```

Caller lấy Spring bean từ XML context nên lời gọi `pay` đi **qua Spring proxy**, khác với `new PaymentService().pay(...)` ngoài container.

### 10.7. Chạy Maven

Từ thư mục root có `pom.xml` và JDK 17+:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.xmlaop.DemoApp
```

Các dòng do chương trình tự in, theo source và XML declarations của demo, **không tính log Maven/Spring bổ sung tùy môi trường**:

```text
isAopProxy=true
[CALLER] success
[AROUND] enter
[BEFORE]
[TARGET] pay ok
[RETURNED] result=paid:ok
[FINALLY]
[AROUND] normal exit
[CALLER] result=paid:ok
[CALLER] failure
[AROUND] enter
[BEFORE]
[TARGET] pay fail
[THROW] IllegalStateException
[FINALLY]
[AROUND] exceptional exit
[CALLER] caught=simulated
[CALLER] health
[TARGET] health
[CALLER] result=UP
```

### 10.8. Vì sao có thứ tự này?

**Success path:**

1. Spring AOP proxy chọn `paymentOperation` cho `pay("ok")`.
2. XML around element có precedence cao nhất nên in `[AROUND] enter`.
3. `pjp.proceed()` chuyển tiếp tới before element, in `[BEFORE]`.
4. Target chạy, in `[TARGET] pay ok` và trả chuỗi.
5. Return advice nhận `result="paid:ok"`, in `[RETURNED]`.
6. Finally advice in `[FINALLY]`.
7. Around lấy kết quả từ `proceed()`, in `normal exit` và return đúng chuỗi cho caller.

**Failure path:**

1. Around in `enter`; before in `[BEFORE]`; target in `[TARGET] pay fail`.
2. Target ném `IllegalStateException`, không có normal return nên `after-returning` không chạy.
3. After-throwing bind `Throwable error` và in tên loại exception.
4. After-finally vẫn chạy.
5. Lỗi propagate đến catch của outer around, được log rồi ném lại cho caller.
6. Caller catch và in message `simulated`.

**Unmatched method:** `health()` không khớp `execution(...pay(..))` nên không có bất kỳ advice log nào khi `health()` chạy.

Stdout là **kết quả dự kiến từ code đã rà soát tĩnh**. Khi chạy project trên máy thật, nên xác minh bằng Maven/integration test đúng Spring version và config; không dùng kết quả này làm cam kết cho application có thêm advisors hoặc order khác.

## 11. Những lưu ý khi XML advice cùng xuất hiện với infrastructure advisors

Trong một ứng dụng có transaction, cache, security hoặc XML/`@Aspect` advice khác, **vị trí của advisor trong chain** ảnh hưởng kết quả được quan sát. Ví dụ logging advisor bên trong transaction interceptor có thể ghi success trước khi transaction commit ngoài cùng; `after-returning` chưa chứng minh commit thành công.

Nếu outer `around` bắt lỗi rồi trả fallback, outer after-returning có thể thấy normal return; nếu `proceed()` lặp nhiều lần, downstream target/advice cũng có thể chạy nhiều lần. Vì vậy cần xác định `order`, phạm vi transaction, retry idempotency và assert event trace của context thật. Bài **“Kết hợp XML với Java configuration…”** xét riêng nguy cơ đăng ký trùng advisor và bean.

## 12. Spring proxy boundaries vẫn tồn tại dù dùng XML

Dù dùng XML, Spring AOP vẫn xử lý **method execution của Spring-managed bean qua proxy**. `this.method()` trong target và `new Target()` ngoài container không kích hoạt một lần proxy interception mới. Với JDK proxy, caller dùng interface; với class proxy, final/private targets không thể override; Future/Publisher completion xảy ra ngoài thời điểm Java method trả về.

Những giới hạn này ảnh hưởng tất cả năm advice types. Bài **“proxy-target-class và expose-proxy trong cấu hình XML”** đưa thí nghiệm đo loại proxy, self-invocation và `AopContext.currentProxy()` cụ thể.

## 13. Khi nào XML AOP thích hợp?

XML configuration có giá trị trong các dự án:

- Dùng XML bean definitions và muốn gắn advice vào POJOs **không sửa class Java**.
- Có các aspects legacy vốn giữ methods bình thường.
- Muốn bật/tắt pointcuts/advice bindings theo configuration đang quản lý có kiểm soát.
- Đang migration giữa schema-based XML AOP và `@AspectJ` annotations.

Ưu điểm gồm tách configuration khỏi Java source và không cần advice bean mang AspectJ annotations. Đổi lại, Java method names trong XML strings **không được Java compiler type-check trực tiếp**; refactor tên method/package phải cập nhật XML và integration tests. Càng nhiều advices cùng join point thì declaration order càng khó kiểm soát.

### So sánh với annotation-style

| Ý định | Spring XML AOP | Spring `@AspectJ` style |
| --- | --- | --- |
| Aspect source | Backing Java POJO + `<aop:aspect ref=...>` | Java `@Aspect` class được Spring quản lý |
| Before | `<aop:before>` | `@Before` |
| After returning | `<aop:after-returning>` | `@AfterReturning` |
| After throwing | `<aop:after-throwing>` | `@AfterThrowing` |
| After finally | `<aop:after>` | `@After` |
| Around | `<aop:around>` | `@Around` |
| Named pointcut | `<aop:pointcut id=...>` | `@Pointcut` |
| Explicit parameter names | XML `arg-names` | Annotation `argNames` |
| Precedence between aspects | XML `order` / Spring ordering | `@Order` / `Ordered` |
| Within same aspect | **XML advice element order** | Precedence by advice type; two same-type advice methods have undefined order |
| Join points | Spring proxy method execution | Spring proxy method execution |

**Không phải** cứ chuyển sang `@Aspect` là Spring AOP có thể chặn private methods/constructor/field get-set; proxy boundary vẫn giữ nguyên.

## 14. Lỗi thường gặp khi dùng năm XML advice tags

| Triệu chứng | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| Context load lỗi vì không biết namespace `aop` | Thiếu `xmlns:aop`/schemaLocation `spring-aop.xsd` | Khai báo đúng namespace và XSD |
| XML parsing lỗi ở `&&` | Dấu `&` chưa escape trong attribute | Dùng `&amp;&amp;` hoặc `and` phù hợp |
| `ref="auditAdvice"` không resolve | Chưa đăng ký backing advice bean hoặc sai ID | Định nghĩa `<bean id="auditAdvice" .../>` |
| `method="returnedValue"` không được tìm thấy | Sai Java method name/signature | Đồng bộ XML với backing bean Java |
| `pointcut-ref` không resolve | Sai/missing named pointcut ID | Khai báo đúng `<aop:pointcut id=...>` |
| `after-returning` không chạy trên exception | Chỉ áp dụng normal return | Dùng `after-throwing` cho exception |
| `after-throwing` không chạy dù Future lỗi muộn | Spring AOP nhìn method normal return | Quan sát Future completion separately |
| `after` chạy cả thành công và lỗi | Đây là finally semantics | Dùng đúng theo mục đích cleanup |
| `around` khiến target không chạy | Quên `pjp.proceed()` hoặc short-circuit | Gọi `proceed()` khi muốn chuyển tiếp |
| Caller không nhận kết quả target | Around quên return result | `return pjp.proceed()` hoặc replacement hợp lệ |
| `returning` không bind đúng | Sai `arg-names`/type, method result type khác | Đồng bộ identifiers và Java signature |
| `throwing` không bind | Parameter không đúng tên/type | Đồng bộ XML `throwing`/`arg-names` và Java method |
| Thứ tự logs khác từ XML tag order | Đọc precedence như thứ tự thời gian đơn chiều | Phân biệt đường vào/ra interceptor chain |
| Hai aspects chạy thứ tự không rõ | Không có `order`/Spring precedence | Khai báo distinct `order` values |
| `health()` bị advice dù chỉ muốn `pay()` | Pointcut quá rộng | Dùng `execution` hẹp, negative tests |
| XML aspect không chạy cho bean tự `new` | Caller bypass Spring proxy | Lấy bean từ context |
| Method self-invocation không có advice | `this.method()` bypass proxy | Refactor giao tiếp giữa Spring beans |
| Không lấy được concrete class type | JDK dynamic proxy chỉ expose interface | Inject interface hoặc đổi proxy strategy phù hợp |
| CGLIB không advice `final` method | Không override được method | Điều chỉnh contract/proxy strategy |
| Trộn XML AOP với auto-proxy creator cấu hình thủ công | Hạ tầng proxy conflict | Chọn một cách cấu hình nhất quán |
| XML references cũ sau refactor | Compiler không kiểm tra string XML | Chạy context-start/integration tests trong CI |

## 15. Checklist review và test

Trước khi đưa XML AOP advice vào module, kiểm tra:

1. XML `beans`, `aop` namespaces và schema locations hợp lệ.
2. `<aop:config>` có đúng pointcuts/advisors/aspects và đúng XSD declaration order.
3. Target và backing advice Java objects đều là Spring beans trong đúng context.
4. Pointcut expression chọn đúng class/method, tránh vô tình match toàn bộ service layer.
5. `method` trỏ đúng tên/signature trên backing advice bean.
6. `pointcut-ref` trỏ đúng named pointcut ID, không nhầm Java method name.
7. `returning`/`throwing`/`arg-names` ánh xạ đúng Java formal parameters.
8. `around` có `ProceedingJoinPoint` đầu tiên và return value/exception contract đúng.
9. Thứ tự **XML advice declarations trong một `<aop:aspect>`** có chủ ý.
10. `order` giữa nhiều aspects/advisors được khai báo nếu có dependency thực tế.
11. Integration test cho normal return, exception, method không match và target không được advice khi bypass proxy.
12. Nếu có transaction/cache/security, test chain/commit behavior trong context thật.
13. Các metrics dùng local start/actor state, không giữ `JoinPoint` vào fields singleton.
14. Không log user tokens/PII hoặc toàn bộ result object không được phép.
15. Không dùng after-returning để suy Future/Publisher đã complete.
16. Khi XML/Java refactor, integration test phải **load XML resource từ classpath** để phát hiện sai configuration strings.

### Nguồn chính thức

- Spring Framework — **Schema-based AOP Support**, đầy đủ ví dụ năm `aop` advice tags, parameter binding và ordering: https://docs.spring.io/spring-framework/reference/core/aop/schema.html
- Spring Framework — **Declaring Advice** và precedence trong `@AspectJ` style: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
- Spring Framework — **Proxying Mechanisms**: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — **AOP Concepts**: https://docs.spring.io/spring-framework/reference/core/aop/introduction-defn.html
- Spring Framework — **XML Schema-based Configuration**: https://docs.spring.io/spring-framework/reference/core/appendix/xsd-schemas.html

**Kết luận:** Spring XML AOP dùng `<aop:before>`, `<aop:after-returning>`, `<aop:after-throwing>`, `<aop:after>` và `<aop:around>` để liên kết **Java advice methods thông thường** với **Spring proxy method-execution join points**. Năm loại giữ đúng semantics của Spring `@AspectJ`, nhưng có một quy tắc cần nhớ: **trong cùng `<aop:aspect>`, thứ tự khai báo advice elements quyết định precedence**. Thiết kế đúng `returning`/`throwing` binding, return/throw contract và proxy boundary giúp cấu hình XML chạy đúng, an toàn và có thể kiểm chứng bằng integration tests.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-aop-configuration-006">Ràng buộc tham số, returning và throwing trong XML</a>

<details>
<summary>Xem chi tiết</summary>

# Ràng buộc tham số, `returning` và `throwing` trong Spring AOP XML

## 1. Mục tiêu

Khi cấu hình aspect qua XML, Spring AOP cho phép advice nhận trực tiếp dữ liệu từ method được chặn mà không phải tự lấy và ép kiểu mọi giá trị từ `JoinPoint.getArgs()`. Ba cơ chế trọng tâm là:

- **Ràng buộc tham số đầu vào** từ pointcut, ví dụ `args(orderId, ..)`, vào tham số cùng tên của advice.
- **`returning`** trên `<aop:after-returning>`: đưa giá trị method trả về khi hoàn tất bình thường vào advice.
- **`throwing`** trên `<aop:after-throwing>`: đưa ngoại lệ được method ném ra khỏi join point vào advice.

Ngoài ra, **`arg-names`** trong XML khai báo rõ thứ tự tên tham số mà Spring dùng khi kết nối pointcut và phương thức advice; điều này đặc biệt hữu ích khi mã Java không giữ tên tham số ở runtime.

Bài học tập trung vào **Spring AOP dạng schema XML** (`<aop:config>`, `<aop:aspect>`, các phần tử advice). Không cần dùng annotation `@Aspect` hoặc weaving AspectJ để chạy ví dụ.

## 2. Phân biệt các thành phần rất dễ nhầm

| Thành phần XML | Vai trò | Ví dụ |
| --- | --- | --- |
| `<aop:pointcut expression="..."/>` | Chọn method execution và có thể khai báo biến binding | `args(orderId, ..)` |
| `method` | Tên phương thức Java trên bean được tham chiếu bởi `<aop:aspect ref="...">` | `method="beforeProcess"` |
| `arg-names` | Danh sách tên binding ứng với tham số của advice theo thứ tự | `arg-names="orderId,receipt"` |
| `returning` | Tên biến nhận giá trị trả về, dành cho after-returning advice | `returning="receipt"` |
| `throwing` | Tên biến nhận ngoại lệ, dành cho after-throwing advice | `throwing="failure"` |

Ba quy tắc kết nối cần nhớ:

1. Trong `args(orderId, ..)`, **`orderId` là tên binding**; phương thức advice nhận tham số tương ứng với tên này. Nó không bắt buộc trùng tên parameter của method nghiệp vụ, nhưng kiểu và vị trí phải phù hợp.
2. `returning="receipt"` yêu cầu advice có tham số nhận giá trị trả về tương ứng tên `receipt`. `throwing="failure"` yêu cầu tham số nhận ngoại lệ tương ứng tên `failure`.
3. `arg-names` chứa tên **các tham số phải bind** theo thứ tự khai báo trong method advice. Nếu method có `JoinPoint` là tham số đầu tiên, Spring có thể suy ra tham số này theo kiểu nên có thể bỏ tên nó ra khỏi danh sách; vẫn cần chỉ rõ các binding còn lại để cấu hình dễ kiểm tra.

**Không nhầm `returning` hoặc `throwing` với phương thức Java trả về/ném ngoại lệ.** Chúng là thuộc tính của phần tử **advice XML** quy định giá trị mà Spring chuyển đến advice sau khi method được chặn kết thúc.

## 3. Luồng Spring AOP khi một method được gọi

Giả sử caller gọi `invoiceService.process("INV-1", false)` qua Spring proxy:

~~~text
Caller
  |
  v
Spring proxy: pointcut khớp process(..), args(invoiceId, ..)
  |
  +-- <aop:before> nhận invoiceId = "INV-1"
  |
  v
Target InvoiceService.process(...)
  |
  +-- trả về String "paid:INV-1"
  |      |
  |      +-- <aop:after-returning returning="receipt">
  |               invoiceId = "INV-1"
  |               receipt = "paid:INV-1"
  |      |
  |      +-- trả giá trị ban đầu về caller
  |
  +-- hoặc ném IllegalStateException
         |
         +-- <aop:after-throwing throwing="failure">
                  invoiceId được bind từ input
                  failure là ngoại lệ thực tế
         |
         +-- ngoại lệ tiếp tục truyền về caller
~~~

`<aop:after-returning>` **chỉ chạy khi method hoàn thành bình thường**. `<aop:after-throwing>` **chỉ chạy khi join point kết thúc bằng ném ngoại lệ**. Nếu method tự bắt một ngoại lệ rồi trả kết quả bình thường, đó là nhánh `after-returning`, không phải nhánh `after-throwing`.

Mọi điều trên giả định lời gọi đi qua Spring AOP proxy. Self-invocation kiểu `this.process(...)` trong chính target bean không đi qua proxy và sẽ không kích hoạt advice ở lời gọi nội bộ đó.

## 4. Ví dụ độc lập: XML + Java 17 + Spring Framework 6

Ví dụ dùng **Java 17**, Spring AOP được cung cấp bởi **Spring Boot starter AOP 3.3.0** (dependency management đưa Spring Framework 6.1.x phù hợp), nhưng khởi tạo trực tiếp Spring `ClassPathXmlApplicationContext`. Vì vậy toàn bộ **khai báo aspect nằm trong XML**, không có `@Aspect`, `@EnableAspectJAutoProxy`, hoặc chương trình Spring Boot khởi chạy bằng `SpringApplication.run`.

### 4.1. Cấu trúc dự án

~~~text
pom.xml
src/main/java/com/example/xmlaop/
    InvoiceService.java
    InvoiceAdvice.java
    Demo.java
src/main/resources/
    applicationContext.xml
~~~

### 4.2. Maven

Tệp `pom.xml`:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>spring-aop-xml-binding-demo</artifactId>
    <version>1.0.0</version>

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
                <version>3.3.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Starter AOP có dependency Spring AOP và AspectJ weaver phục vụ việc phân tích pointcut. Ở đây nó chỉ cung cấp thư viện qua Maven; **chính XML** sẽ tạo auto-proxy.

### 4.3. Target service

Tệp `src/main/java/com/example/xmlaop/InvoiceService.java`:

~~~java
package com.example.xmlaop;

public class InvoiceService {

    public String process(String invoiceId, boolean reject) {
        System.out.println("service:" + invoiceId);

        if (reject) {
            throw new IllegalStateException("declined:" + invoiceId);
        }

        return "paid:" + invoiceId;
    }
}
~~~

Hai nhánh được cố ý viết đơn giản:

- `reject = false`: phương thức **trả String**.
- `reject = true`: phương thức **ném IllegalStateException** và không trả một String nào.

Spring AOP sẽ nhận cùng một tham số `invoiceId` tại before advice, rồi chọn **một trong hai** advice sau thực thi tùy cách method kết thúc.

### 4.4. Java advice bean bình thường

Tệp `src/main/java/com/example/xmlaop/InvoiceAdvice.java`:

~~~java
package com.example.xmlaop;

public class InvoiceAdvice {

    public void beforeProcess(String invoiceId) {
        System.out.println("before:" + invoiceId);
    }

    public void afterSuccess(String invoiceId, String receipt) {
        System.out.println("returned:" + invoiceId + "->" + receipt);
    }

    public void afterFailure(String invoiceId, IllegalStateException failure) {
        System.out.println(
                "thrown:" + invoiceId + "->" + failure.getMessage());
    }
}
~~~

Đây là POJO, không cần `@Aspect`. Các method advice có tên và kiểu tham số rõ ràng; mapping được khai báo bằng XML.

**Sự khác biệt về kiểu:** `String receipt` nghĩa là after-returning advice nhận những kết quả tương thích với `String`; `IllegalStateException failure` nghĩa là after-throwing advice chỉ nhận ngoại lệ thuộc kiểu `IllegalStateException` hoặc kiểu con của nó. Dùng `Object receipt` để nhận các kết quả tham chiếu thuộc nhiều kiểu, và `Throwable failure` nếu muốn theo dõi nhiều loại lỗi (bao gồm `Error`). Không gán kiểu cụ thể quá hẹp so với method định theo dõi.

### 4.5. Cấu hình XML chứa pointcut và binding

Tệp `src/main/resources/applicationContext.xml`:

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

    <bean id="invoiceService"
          class="com.example.xmlaop.InvoiceService"/>

    <bean id="invoiceAdvice"
          class="com.example.xmlaop.InvoiceAdvice"/>

    <aop:config proxy-target-class="true">
        <aop:aspect id="invoiceAspect" ref="invoiceAdvice">

            <aop:pointcut
                    id="invoiceProcess"
                    expression="execution(public * com.example.xmlaop.InvoiceService.process(..))
                                and args(invoiceId, ..)"/>

            <aop:before
                    pointcut-ref="invoiceProcess"
                    method="beforeProcess"
                    arg-names="invoiceId"/>

            <aop:after-returning
                    pointcut-ref="invoiceProcess"
                    method="afterSuccess"
                    returning="receipt"
                    arg-names="invoiceId,receipt"/>

            <aop:after-throwing
                    pointcut-ref="invoiceProcess"
                    method="afterFailure"
                    throwing="failure"
                    arg-names="invoiceId,failure"/>

        </aop:aspect>
    </aop:config>
</beans>
~~~

Đọc theo thứ tự:

1. `<bean id="invoiceService" .../>` tạo **target bean**. `<bean id="invoiceAdvice" .../>` tạo **POJO advice**.
2. `<aop:config>` kích hoạt cơ chế Spring AOP proxy cho khai báo XML trong ApplicationContext. `proxy-target-class="true"` dùng class-based proxy; phương thức `process` là public và không final nên có thể intercept.
3. `<aop:aspect ref="invoiceAdvice">` nối khái niệm aspect trong XML với Java bean `InvoiceAdvice`.
4. `execution(public * ...InvoiceService.process(..))` thu hẹp phạm vi đúng method `process`, với mọi kiểu trả về và số lượng argument. Phần `args(invoiceId, ..)` yêu cầu argument đầu có thể bind với `String invoiceId` của advice.
5. `arg-names="invoiceId"` của before advice tạo mapping giữa biến trong pointcut và `beforeProcess(String invoiceId)`.
6. `returning="receipt"` và `arg-names="invoiceId,receipt"` đưa **giá trị trả về** vào tham số thứ hai của `afterSuccess(String invoiceId, String receipt)`.
7. `throwing="failure"` và `arg-names="invoiceId,failure"` đưa **ngoại lệ** vào tham số thứ hai của `afterFailure(String invoiceId, IllegalStateException failure)`.

**Tại sao trong expression dùng `and`?** Thuộc tính XML không thể chứa ký tự `&` thô trong `&&`; Spring AspectJ pointcut expression cho phép dạng từ khóa `and`, `or`, `not`. Nếu thực sự viết `&&`, phải escape thành `&amp;&amp;`. Ví dụ chọn `and` để XML dễ đọc.

**Tại sao `arg-names` lại chứa `receipt`/`failure` dù pointcut không khai báo chúng?** Vì đây là các binding bổ sung do **thuộc tính `returning`/`throwing` trên advice** cung cấp. Chúng cần tên để ghép với tham số Java, mặc dù không xuất hiện ở `args(...)`.

### 4.6. Main chạy hai trường hợp

Tệp `src/main/java/com/example/xmlaop/Demo.java`:

~~~java
package com.example.xmlaop;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Demo {

    public static void main(String[] args) {
        try (ClassPathXmlApplicationContext context =
                     new ClassPathXmlApplicationContext(
                             "applicationContext.xml")) {

            InvoiceService service =
                    context.getBean("invoiceService", InvoiceService.class);

            System.out.println("== success ==");
            String receipt = service.process("INV-1", false);
            System.out.println("caller:" + receipt);

            System.out.println("== failure ==");
            try {
                service.process("INV-2", true);
            } catch (IllegalStateException failure) {
                System.out.println(
                        "caller caught:" + failure.getMessage());
            }
        }
    }
}
~~~

Tại thư mục chứa `pom.xml`, chạy:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=com.example.xmlaop.Demo
~~~

Bỏ qua log môi trường/Maven, dòng do chương trình in ra có **thứ tự và nội dung xác định**:

~~~text
== success ==
before:INV-1
service:INV-1
returned:INV-1->paid:INV-1
caller:paid:INV-1
== failure ==
before:INV-2
service:INV-2
thrown:INV-2->declined:INV-2
caller caught:declined:INV-2
~~~

Phân tích:

- Nhánh thành công: `beforeProcess` chạy trước target; target trả `paid:INV-1`; `afterSuccess` nhận đồng thời `invoiceId` ban đầu và kết quả; caller vẫn nhận **chính giá trị trả về gốc**.
- Nhánh lỗi: `beforeProcess` vẫn chạy; target ném `IllegalStateException`; `afterFailure` nhận ngoại lệ; caller vẫn bắt được **ngoại lệ gốc**.
- `afterSuccess` không được gọi trong nhánh lỗi; `afterFailure` không được gọi trong nhánh thành công.
- Cấu hình XML không sử dụng `@Aspect` và chương trình không tự khởi tạo `InvoiceService` bằng `new` ở caller, vì làm vậy sẽ đi vòng qua proxy.

## 5. Tìm hiểu kỹ `args(...)` và kiểu binding

Với biểu thức `args(invoiceId, ..)`:

- `invoiceId` là **biến pointcut** cần bind. Kiểu `String` được suy ra từ tham số advice `beforeProcess(String invoiceId)`, `afterSuccess(String invoiceId, ...)`, v.v.
- `..` biểu diễn các đối số còn lại có số lượng bất kỳ. Do đó `process(String, boolean)` khớp và lấy giá trị String đầu tiên.
- Đây là **runtime argument matching**; nó không đơn giản là “phương thức có biến Java nguồn tên `invoiceId`”. Tên tham số ở source của target method không quyết định tên binding trong pointcut.
- Nếu muốn match chính xác signature, thêm ràng buộc `execution(public String com.example.xmlaop.InvoiceService.process(String,boolean))`, nhưng vẫn có thể dùng `args(invoiceId, ..)` để lấy giá trị cụ thể.

Một ví dụ XML ngắn khác khi cần bind hai tham số:

~~~xml
<aop:before
        pointcut="execution(* com.example.xmlaop.InvoiceService.process(..))
                  and args(invoiceId,reject)"
        method="auditInput"
        arg-names="invoiceId,reject"/>
~~~

Method trên advice tương ứng phải có chữ ký phù hợp, chẳng hạn:

~~~java
public void auditInput(String invoiceId, boolean reject) {
    System.out.println("request=" + invoiceId + ", reject=" + reject);
}
~~~

Hai đoạn vừa nêu **chỉ minh họa biến thể**, chưa có `auditInput` trong class `InvoiceAdvice` của chương trình chạy ở mục 4. Nếu thay XML và thêm method như trên, Spring sẽ truyền cả hai đối số theo đúng thứ tự. Không nên chèn riêng XML này vào bản chính nếu chưa bổ sung method.

Khi muốn đọc toàn bộ args mà không thiết lập binding tường minh, before advice có thể nhận `JoinPoint`:

~~~java
public void inspect(org.aspectj.lang.JoinPoint joinPoint) {
    Object[] arguments = joinPoint.getArgs();
    System.out.println("argumentCount=" + arguments.length);
}
~~~

Đây cũng là **biến thể minh họa**. Cách `JoinPoint.getArgs()` linh hoạt hơn nhưng bạn phải tự đọc và ép kiểu. Với một vài biến rõ ràng, binding qua `args` và `arg-names` thường dễ bảo trì hơn.

## 6. `returning`: nhận kết quả nhưng không thay giá trị trả về

`<aop:after-returning>` chỉ kích hoạt khi matched method **hoàn thành bình thường**. Khi có `returning="receipt"`, Spring truyền kết quả vào tham số advice có tên binding tương ứng.

Ví dụ `afterSuccess(String invoiceId, String receipt)` chỉ định `String`: đó là một **ràng buộc kiểu**. Trong pointcut rộng chặn nhiều method trả nhiều kiểu khác nhau, advice nhận `String receipt` chỉ áp dụng cho các lời gọi có giá trị trả về phù hợp kiểu `String`. Nếu dùng `Object receipt`, phạm vi nhận kết quả tham chiếu rộng hơn. Trong method khai báo trả `void`, không có giá trị kết quả để bind vào `String` như ví dụ.

After-returning advice **không thể dùng return của chính advice để thay kết quả** mà caller nhận. Chẳng hạn:

~~~java
// Biến thể minh họa, không phải method trong ví dụ chính:
public String afterSuccess(String invoiceId, String receipt) {
    return "overridden"; // không biến thành return của InvoiceService.process()
}
~~~

Nếu yêu cầu nghiệp vụ là đổi hoặc thay thế kết quả, phải dùng `<aop:around>` với `ProceedingJoinPoint` và chủ động trả về kết quả muốn chuyển cho caller. Việc around advice đổi kết quả cần cân nhắc contract và type của method.

**Lưu ý về object mutable:** advice nhận một tham chiếu tới object kết quả có thể sửa **trạng thái bên trong object đó**, nếu object mutable. Điều này khác với việc thay hẳn reference trả về. Tránh thay đổi ngầm data ở advice đo đạc/audit vì làm kết quả phụ thuộc thứ tự interceptor.

## 7. `throwing`: nhận ngoại lệ được phát ra, không tự xử lý phục hồi

Khai báo:

~~~xml
<aop:after-throwing
        pointcut-ref="invoiceProcess"
        method="afterFailure"
        throwing="failure"
        arg-names="invoiceId,failure"/>
~~~

Spring truyền ngoại lệ ném ra khỏi matched method đến tham số `failure` của advice. `IllegalStateException failure` chỉ khớp ngoại lệ cùng kiểu hoặc kiểu con; nếu method ném `IOException`, advice với kiểu `IllegalStateException` sẽ không xử lý nó.

**After-throwing khác `catch`**:

- Advice `afterFailure` trong ví dụ chỉ ghi nhận thông tin.
- Nó không tự nuốt ngoại lệ để chuyển execution thành thành công.
- `return` bên trong after-throwing advice (nếu method có return type) không thay thế ngoại lệ của target.
- Caller vẫn có thể xử lý bằng `try/catch`.
- Nếu target bắt lỗi nội bộ rồi trả kết quả bình thường, Spring không có ngoại lệ thoát khỏi join point để chạy after-throwing advice.

Nếu muốn fallback khi target ném ngoại lệ, dùng `<aop:around>` và xử lý exception quanh `proceed()` theo một **chính sách nghiệp vụ được xác định rõ**. Đừng nhầm `after-throwing` với `@ControllerAdvice`/exception resolver của Spring MVC: đây là hai cơ chế xử lý ở các boundary khác nhau.

## 8. `arg-names`: cách Spring khớp tên biến với chữ ký advice

Ràng buộc biến trong Spring AOP không thể dựa chắc chắn vào **tên parameter của Java source** nếu bytecode không giữ tên. Spring có các chiến lược khám phá tên, nhưng với XML bạn có thể chủ động cấu hình `arg-names` để tránh sự mơ hồ.

Giả sử:

~~~java
public void afterSuccess(String order, String result) {
    // ...
}
~~~

Nếu pointcut dùng biến `invoiceId` và `returning="receipt"`, vẫn có thể khai báo:

~~~xml
<aop:after-returning
        pointcut-ref="invoiceProcess"
        method="afterSuccess"
        returning="receipt"
        arg-names="invoiceId,receipt"/>
~~~

Ở đây **thứ tự** trong `arg-names` tương ứng tham số Java thứ nhất và thứ hai; tên source `order`/`result` không quyết định mapping khi Spring dùng danh sách `arg-names` tường minh. Dù vậy, nên đặt cùng tên trong Java và XML để tài liệu và implementation dễ theo dõi.

### Nếu advice nhận `JoinPoint`

Ví dụ before advice nhận join point trước rồi nhận argument được bind:

~~~java
public void beforeProcess(
        org.aspectj.lang.JoinPoint joinPoint,
        String invoiceId) {
    System.out.println(joinPoint.getSignature().getName() + ":" + invoiceId);
}
~~~

Khai báo XML có thể là:

~~~xml
<aop:before
        pointcut-ref="invoiceProcess"
        method="beforeProcess"
        arg-names="invoiceId"/>
~~~

Spring có thể tự nhận biết tham số đầu `JoinPoint` theo kiểu, vì vậy không cần đưa tham số `joinPoint` vào `arg-names`. Đây là **biến thể minh họa**: nếu dùng chữ ký này thì phải thay method `beforeProcess` trong ví dụ chính cho phù hợp.

Với `<aop:around>`, tham số đầu tiên của method advice phải là `ProceedingJoinPoint` để gọi `proceed()`. Binding cho các tham số còn lại tuân theo cùng nguyên tắc, còn around advice quyết định có chạy target hay không.

### Lỗi cấu hình tên thường gặp

- XML ghi `returning="retVal"` nhưng `arg-names="invoiceId,result"`: tên `retVal` không có chỗ bind; có thể phát sinh lỗi tạo aspect/advisor hoặc ràng buộc không hợp lệ.
- XML ghi `throwing="failure"` còn method advice chỉ có tham số `String failure`: kiểu không tương thích với exception object.
- `arg-names="receipt,invoiceId"` nhưng method khai báo tham số theo thứ tự `(String invoiceId, String receipt)`: hai giá trị đều là String nên lỗi **khó phát hiện qua type checking**, khiến log hoặc logic bị hoán đổi.
- Pointcut ghi `args(invoiceId, ..)` nhưng advice cần bind cả `tenantId` mà expression không đưa vào: variable thiếu nguồn giá trị.
- Quên `arg-names` rồi kỳ vọng Spring luôn lấy được tên Java parameter khi biên dịch không bật cơ chế giữ tên phù hợp: binding có thể phụ thuộc tên đã khám phá hoặc bị lỗi.

Đặc biệt với các binding cùng kiểu như String/String, **test so sánh giá trị thực tế** mới chứng minh mapping đúng; Java compiler không thể phát hiện mọi trường hợp đảo thứ tự về mặt nghiệp vụ.

## 9. Phối hợp nhiều biến: inputs + returning/throwing

Một advice có thể nhận cả input từ `args(...)` và output/exception. Ví dụ chính đã dùng:

| Lời gọi | Pointcut `args(invoiceId, ..)` | Dữ liệu thêm từ XML | Advice nhận |
| --- | --- | --- | --- |
| `process("INV-1", false)` | `invoiceId="INV-1"` | `receipt="paid:INV-1"` | `afterSuccess(String, String)` |
| `process("INV-2", true)` | `invoiceId="INV-2"` | `failure=IllegalStateException("declined:INV-2")` | `afterFailure(String, IllegalStateException)` |

Dù hai after-advice dùng cùng `pointcut-ref`, việc method kết thúc thành công hay lỗi sẽ quyết định loại advice nào được gọi; `returning` và `throwing` **không đồng thời bind cho cùng một lần hoàn tất**.

Khi điểm chặn cần phân biệt nhiều overload hoặc các kiểu argument khác nhau, hãy thu hẹp `execution` theo signature và dùng `args` cho các giá trị cụ thể muốn nhận. Đừng dùng biểu thức quá rộng bao hết mọi bean/method nếu chỉ cần audit một operation, vì các binding type có thể gây matching không như dự kiến hoặc tạo overhead không cần thiết.

## 10. Điều kiện hoạt động và lỗi thường gặp với XML AOP

### 10.1. Advice không được gọi dù XML đọc thành công

Kiểm tra lần lượt:

1. Có khai báo namespace `aop`, `spring-aop.xsd`, `<aop:config>` và `<aop:aspect ref="...">` đúng hay không?
2. Bean aspect được tham chiếu bởi `ref` có tồn tại không, và `method` có đúng tên method trên bean đó không?
3. Bean target có được **Spring quản lý** và caller có lấy **proxy** từ context không?
4. Pointcut `execution(...)` có khớp class, method và kiểu tham số thực tế không?
5. Có self-invocation `this.process(...)` hoặc method private/final làm lời gọi không được proxy intercept không?
6. Có lỗi khai báo `arg-names`, `returning`, `throwing` hoặc kiểu tham số của advice không?

Ví dụ dùng class-based proxy nên `InvoiceService` không cần interface. Nếu đổi sang JDK proxy, target phải có interface được proxy và caller nên inject/lấy bean bằng **kiểu interface**, không ép proxy thành concrete class.

### 10.2. XML bị parser từ chối do dấu ampersand

Sai:

~~~xml
<!-- Sai cú pháp XML: không thể đặt & thô trong attribute -->
<aop:pointcut
        id="x"
        expression="execution(* com.example..*(..)) && args(name,..)"/>
~~~

Đúng (dùng từ khóa):

~~~xml
<aop:pointcut
        id="x"
        expression="execution(* com.example..*(..)) and args(name,..)"/>
~~~

Hoặc dùng entity XML:

~~~xml
<aop:pointcut
        id="x"
        expression="execution(* com.example..*(..)) &amp;&amp; args(name,..)"/>
~~~

Các XML trên chỉ minh họa cú pháp escape/pointcut; chúng không thay thế cấu hình demo vốn có kiểu binding tường minh.

### 10.3. Lỗi binding không chắc sẽ xuất hiện ở compile-time

Cấu hình XML được Spring xử lý khi khởi tạo ApplicationContext. Nếu advice tham chiếu tên không tồn tại, số lượng biến không khớp hoặc không thể resolve method, lỗi có thể xuất hiện **khi startup** chứ không phải lúc biên dịch Java. Với logic security/audit/transaction quan trọng, viết integration test khởi tạo context và gọi method qua bean proxy ở cả hai nhánh success/failure.

### 10.4. `returning` không phải lúc nào cũng là kết quả “nghiệp vụ đã hoàn tất”

Nếu method trả `CompletableFuture`, after-returning advice thường thấy **đối tượng future được trả**, chưa chắc thấy kết quả xử lý cuối cùng. Nếu method trả `Mono`/`Flux`, advice thấy publisher được trả, không tự đo việc subscription hoàn thành. Khi cần audit kết quả cuối của công việc bất đồng bộ, phải theo dõi completion/subscription bằng cơ chế thích hợp, thay vì suy luận từ lúc method return.

### 10.5. Thứ tự nhiều advice và xử lý ngoại lệ

Khi phối hợp advice từ các aspect/advisor khác nhau, thứ tự interceptor ảnh hưởng dữ liệu nhìn thấy và thời điểm ghi log. Nếu một around advice ngoài cùng bắt exception và trả fallback, advice after-throwing nằm **bên ngoài** around advice đó có thể không còn nhìn thấy ngoại lệ. Nếu after-throwing nằm **bên trong** và nhìn thấy exception trước lúc bị bắt, nó có thể được kích hoạt. Xác định ranh giới đo và thứ tự theo hành vi cần bảo đảm; khi phải kiểm soát, dùng cơ chế order phù hợp và kiểm thử thay vì suy đoán thứ tự.

### 10.6. Nơi đặt cấu hình

Schema-based AOP nằm trong Spring ApplicationContext thực sự tải XML. Một XML riêng có khai báo `<aop:config>` sẽ không có tác dụng nếu ứng dụng chưa import/tải file đó. Nếu ứng dụng Spring Boot chủ yếu dùng Java config, có thể dùng `@ImportResource("classpath:applicationContext.xml")` trên một configuration class để tải XML; đây là **cách tích hợp bổ sung**, không phải yêu cầu của demo sử dụng `ClassPathXmlApplicationContext`.

## 11. Checklist kiểm chứng

Trước khi xem binding XML AOP là đúng, cần xác nhận:

- Spring khởi động được ApplicationContext với schema `aop` và đủ dependency.
- Pointcut chỉ chọn các method cần thiết, không chặn nhầm các operation khác.
- `args` bind đúng **giá trị input**, kể cả khi tên Java source khác biến trong pointcut.
- `arg-names` có thứ tự tương ứng với danh sách tham số Java của advice.
- `returning` bind đúng kết quả trong success; after-returning không chạy ở nhánh lỗi.
- `throwing` bind đúng exception type trong failure; after-throwing không chạy ở nhánh thành công.
- Caller vẫn nhận return value hoặc ngoại lệ gốc, trừ khi có around advice chủ ý thay đổi chúng.
- Test gọi **thông qua Spring proxy**, không gọi trực tiếp `new Target()` hoặc self-invocation.
- Khi dùng async/reactive, đã xác định rõ lời gọi method kết thúc hay tác vụ thực tế kết thúc mới là mốc quan sát.

## 12. Ghi nhớ

Spring AOP XML tách biệt **phạm vi cần chặn** (pointcut) và **dữ liệu đưa vào advice** (binding). `args(...)` gắn giá trị đầu vào với tên biến; `arg-names` ghép tên đó với chữ ký Java advice; `returning` và `throwing` bổ sung binding cho kết quả bình thường hoặc ngoại lệ. Dù cấu hình nằm trong XML, cơ chế Spring AOP vẫn là **proxy-based**, có giới hạn self-invocation và thứ tự advisor như những cách khai báo Spring AOP khác.

Tài liệu chính thức:

- [Spring Framework — Schema-based AOP Support](https://docs.spring.io/spring-framework/reference/core/aop/schema.html)
- [Spring Framework — Declaring Advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html)
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-aop-configuration-007">Cấu hình order và khác biệt thứ tự khai báo so với @Aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu hình order trong XML và khác biệt thứ tự khai báo với @Aspect trong Spring AOP

## 1. Bài toán: nhiều advice cùng áp dụng cho một Spring bean

Khi Spring bean có nhiều XML hoặc annotation advice cùng match, kết quả quan sát phụ thuộc **precedence của advisor chain**. Có ba phạm vi khác nhau: (1) `order` giữa các aspect/advisor, (2) **thứ tự phần tử advice trong cùng XML `<aop:aspect>`**, và (3) quy tắc annotation advice trong cùng class `@Aspect`. Bài này tập trung **so sánh ba phạm vi bằng cùng một lời gọi**, vì vị trí code/bean definition không thể thay cho hợp đồng ordering.

Ví dụ hai around advice lồng nhau: aspect precedence cao vào trước, rồi thoát sau. Nếu đặt audit bên ngoài metrics thì trace là `AUDIT enter → METRICS enter → target → METRICS exit → AUDIT exit`.

## 2. `order`: số nhỏ hơn có precedence cao hơn

`order` số **nhỏ hơn** có precedence **cao hơn**. Ví dụ:

~~~xml
<aop:aspect ref="auditBean" order="1">
    <aop:around method="around" pointcut-ref="payment"/>
</aop:aspect>
<aop:aspect ref="metricsBean" order="2">
    <aop:around method="around" pointcut-ref="payment"/>
</aop:aspect>
~~~

Đây là fragment cần hai bean và pointcut đã khai báo. Nếu cả hai gọi `proceed()`, audit bọc metrics, nên log:

~~~text
AUDIT enter
METRICS enter
PAY
METRICS exit
AUDIT exit
~~~

Số nhỏ hơn không có nghĩa log luôn xuất hiện trước khi đi ra: outer aspect được gọi **sau cùng** trên đường unwind. Nếu hai aspects không được đặt precedence tường minh, đừng dựa vào thứ tự các dòng XML hoặc thứ tự bean được tạo.

## 3. XML: `<aop:aspect order="...">` điều khiển precedence giữa aspect

Spring hỗ trợ namespace `aop`; cấu hình hợp lệ cần namespace/schema và bean chứa logic advice:

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd">

    <bean id="billingService" class="demo.BillingService"/>
    <bean id="auditBean" class="demo.XmlAuditAspect"/>
    <bean id="metricsBean" class="demo.XmlMetricsAspect"/>

    <aop:config proxy-target-class="true">
        <aop:pointcut id="payment"
                      expression="execution(* demo.BillingService.pay(..))"/>

        <aop:aspect id="audit" ref="auditBean" order="1">
            <aop:around method="around" pointcut-ref="payment"/>
        </aop:aspect>

        <aop:aspect id="metrics" ref="metricsBean" order="2">
            <aop:around method="around" pointcut-ref="payment"/>
        </aop:aspect>
    </aop:config>
</beans>
```

Trong `<aop:config>`, khai báo **pointcut trước, advisor sau, aspect cuối** nếu đồng thời có cả ba loại phần tử. `ref` trong `<aop:aspect>` tham chiếu bean Java thông thường: class chứa method `around` **không cần** `@Aspect` vì pointcut/advice đã được mô tả ở XML.

Các điểm cần phân biệt:

- `order` trên `<aop:aspect>` dành cho precedence **giữa các aspect**. Nó không tự sắp lại vị trí các method trong class backing bean.
- Có thể dùng `@Order` hoặc triển khai `org.springframework.core.Ordered` trên bean backing aspect theo quy tắc Spring; nếu đã chọn cấu hình XML minh bạch, đặt `order` ngay trên `<aop:aspect>` thường dễ kiểm tra hơn.
- Cấu hình `proxy-target-class="true"` lựa chọn proxy dựa trên subclass trong ví dụ; nó **không** biến Spring AOP thành AspectJ weaving.
- Nếu dùng `<aop:advisor>` độc lập, chính phần tử advisor cũng có thuộc tính `order` riêng, ví dụ `<aop:advisor advice-ref="guardAdvice" pointcut-ref="payment" order="0"/>`. `guardAdvice` phải là bean advice thích hợp, chẳng hạn implement `org.aopalliance.intercept.MethodInterceptor`. Đây là ví dụ minh họa thuộc tính, chưa phải một advisor chạy độc lập.
- Khi có các advisor khác của Spring như transaction/security, phải xét order của **các advisor đó**. Không thể suy ra toàn bộ thứ tự hệ thống chỉ từ một `<aop:aspect order="...">`.

## 4. Điểm khác biệt cốt lõi: **thứ tự khai báo advice trong cùng XML aspect**

Theo tài liệu Spring AOP schema, khi **hai advice cùng nằm trong một** `<aop:aspect>` và cùng khớp join point, **thứ tự khai báo phần tử advice từ trên xuống dưới thể hiện precedence từ cao đến thấp**.

Ví dụ minh họa, với backing bean có method `around` và `check` phù hợp:

```xml
<aop:aspect id="validationAndAudit" ref="validationAuditBean" order="3">
    <aop:around method="around" pointcut-ref="payment"/>
    <aop:before method="check" pointcut-ref="payment"/>
</aop:aspect>
```

Ở đây `around` được khai báo **trước** `before` nên có precedence cao hơn và bao phía ngoài khi chuỗi advice được thực thi. Đổi thứ tự hai dòng làm thay đổi precedence của chúng trong nhóm XML aspect này.

Cần phân biệt hai kiểu thay đổi:

| Thao tác | Tác động mong đợi |
|---|---|
| Đổi `order="1"` thành `order="5"` của một `<aop:aspect>` | Thay mức ưu tiên của **aspect đó so với các aspect/advisor khác** |
| Đổi vị trí `<aop:around>` với `<aop:before>` **trong cùng** `<aop:aspect>` | Thay precedence **nội bộ** của các advice XML cùng aspect |
| Đổi vị trí hai `<aop:aspect>` khác nhau nhưng giữ `order` là 1 và 2 | Không đảo precedence được xác định bởi hai giá trị `order` |
| Đảo vị trí method `@Before` trong Java class `@Aspect` | Không xác lập thứ tự chạy được bảo đảm |

**Không suy diễn** “XML luôn chạy đúng trật tự mọi phần tử trong cả file”. Quy tắc thứ tự khai báo ở đây áp dụng cho **các advice cùng một `<aop:aspect>`**, còn precedence giữa aspect cần `order`.

Nếu một XML aspect có nhiều advice cùng khớp điểm cắt, nên giữ thứ tự rõ, tránh viết các advice phụ thuộc side effect lẫn nhau. Khi khó diễn đạt thứ tự, tách thành các aspect có `order` hoặc gộp logic cần tuần tự vào một `around` advice có thứ tự gọi minh bạch.

## 5. So với `@Aspect`: `@Order` chỉ cho cấp aspect

Trong cấu hình annotation, các class được Spring quản lý dùng `@Aspect` và `@Order`:

```java
@Aspect
@Order(1)
public class AnnotationAuditAspect {
    @Around("execution(* demo.BillingService.pay(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("AUDIT enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("AUDIT exit");
        }
    }
}
```

Và aspect thứ hai:

```java
@Aspect
@Order(2)
public class AnnotationMetricsAspect {
    @Around("execution(* demo.BillingService.pay(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("METRICS enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("METRICS exit");
        }
    }
}
```

Cả hai đoạn chỉ hiển thị thân class (cần các import `@Aspect`, `@Order`, `@Around` và `ProceedingJoinPoint`); phiên bản **tệp đầy đủ** nằm ở ví dụ chạy được bên dưới.

`@Order(1)` và `@Order(2)` tạo chuỗi bao lời gọi **tương tự XML**. Nhưng nếu một class `@Aspect` có nhiều advice:

- Spring áp dụng thứ tự precedence theo **loại advice** trong cùng aspect: `@Around`, `@Before`, `@After`, `@AfterReturning`, `@AfterThrowing` từ cao đến thấp theo quy tắc Spring. Riêng `@After` có hành vi finally nên trên thực tế chạy sau `@AfterReturning` hoặc `@AfterThrowing` tương ứng.
- Khi nhiều advice **cùng loại** (ví dụ hai method đều mang `@Before`) trong cùng class `@Aspect` cùng khớp join point, **thứ tự giữa chúng không được xác định**. Java reflection không bảo đảm giữ thứ tự khai báo method của mã nguồn.
- `@Order` trên **class aspect** không chọn thứ tự của hai method `@Before` **trong cùng class**. Đổi thứ tự viết hai method trong mã nguồn cũng không khắc phục được.
- Nếu thứ tự giữa hai advice cùng loại là yêu cầu nghiệp vụ, hãy gộp chúng thành một advice có thứ tự gọi rõ ràng, hoặc tách thành hai class `@Aspect` và chỉ định `@Order` cho từng class.

Ví dụ **không nên phụ thuộc vào thứ tự khai báo**:

```java
@Aspect
@Order(1)
public class AmbiguousBeforeAspect {
    @Before("execution(* demo.BillingService.pay(..))")
    public void validate() {
        System.out.println("validate");
    }

    @Before("execution(* demo.BillingService.pay(..))")
    public void audit() {
        System.out.println("audit");
    }
}
```

Đoạn này chỉ minh họa bẫy về thứ tự; không thuộc hai file aspect trong ví dụ chạy bên dưới. **Không kết luận chắc chắn** stdout phải là `validate` rồi `audit`. Đây là khác biệt quan trọng so với thứ tự phần tử advice trong **cùng** `<aop:aspect>` của XML.

## 6. Ví dụ chạy được: hai cấu hình, cùng một thứ tự ưu tiên

Ví dụ **Java 17+, Spring Framework 6.2.x, Gradle** dưới đây có hai chế độ chạy độc lập:

- `xml`: đăng ký hai aspect qua `<aop:config>` và `<aop:aspect order>`.
- `annotation`: đăng ký hai class `@Aspect` bằng `@EnableAspectJAutoProxy` và `@Order`.

Chúng sử dụng **cùng** `BillingService` nhưng tạo **hai ApplicationContext khác nhau** để so sánh công bằng, tránh đăng ký cùng advice hai lần. Spring AOP vẫn thực thi qua proxy trong cả hai chế độ.

Cấu trúc dự án:

```text
spring-aop-order-demo/
├── settings.gradle
├── build.gradle
└── src/main/
    ├── java/demo/
    │   ├── Main.java
    │   ├── BillingService.java
    │   ├── XmlAuditAspect.java
    │   ├── XmlMetricsAspect.java
    │   ├── AnnotationAuditAspect.java
    │   ├── AnnotationMetricsAspect.java
    │   └── AnnotationConfig.java
    └── resources/aop.xml
```

### 6.1. Gradle và service

**`settings.gradle`**

```groovy
rootProject.name = 'spring-aop-order-demo'
```

**`build.gradle`**

```groovy
plugins {
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
    implementation 'org.springframework:spring-context:6.2.13'
    implementation 'org.springframework:spring-aop:6.2.13'
    implementation 'org.aspectj:aspectjweaver:1.9.24'
}

application {
    mainClass = 'demo.Main'
}
```

`spring-context` cung cấp container, `spring-aop` cung cấp proxy AOP, còn `aspectjweaver` cung cấp phần cần thiết để Spring phân tích AspectJ pointcut/annotation aspect. Việc có `aspectjweaver` trên classpath **không có nghĩa** ví dụ này kích hoạt load-time weaving: cả hai chế độ đều cấu hình **Spring AOP proxy**.

**`src/main/java/demo/BillingService.java`**

```java
package demo;

public class BillingService {
    public void pay() {
        System.out.println("PAY");
    }
}
```

### 6.2. Backing bean cho XML aspect

**`src/main/java/demo/XmlAuditAspect.java`**

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;

public class XmlAuditAspect {
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("AUDIT enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("AUDIT exit");
        }
    }
}
```

**`src/main/java/demo/XmlMetricsAspect.java`**

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;

public class XmlMetricsAspect {
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("METRICS enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("METRICS exit");
        }
    }
}
```

Các class này không có `@Aspect`. XML cung cấp pointcut, phương thức advice và order.

**`src/main/resources/aop.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd">

    <bean id="billingService" class="demo.BillingService"/>
    <bean id="auditBean" class="demo.XmlAuditAspect"/>
    <bean id="metricsBean" class="demo.XmlMetricsAspect"/>

    <aop:config proxy-target-class="true">
        <aop:pointcut id="payment"
                      expression="execution(* demo.BillingService.pay(..))"/>

        <aop:aspect id="audit" ref="auditBean" order="1">
            <aop:around method="around" pointcut-ref="payment"/>
        </aop:aspect>

        <aop:aspect id="metrics" ref="metricsBean" order="2">
            <aop:around method="around" pointcut-ref="payment"/>
        </aop:aspect>
    </aop:config>
</beans>
```

### 6.3. Hai annotation aspect tương đương

**`src/main/java/demo/AnnotationAuditAspect.java`**

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(1)
public class AnnotationAuditAspect {
    @Around("execution(* demo.BillingService.pay(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("AUDIT enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("AUDIT exit");
        }
    }
}
```

**`src/main/java/demo/AnnotationMetricsAspect.java`**

```java
package demo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

@Aspect
@Order(2)
public class AnnotationMetricsAspect {
    @Around("execution(* demo.BillingService.pay(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("METRICS enter");
        try {
            return pjp.proceed();
        } finally {
            System.out.println("METRICS exit");
        }
    }
}
```

**`src/main/java/demo/AnnotationConfig.java`**

```java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AnnotationConfig {
    @Bean
    public BillingService billingService() {
        return new BillingService();
    }

    @Bean
    public AnnotationAuditAspect annotationAuditAspect() {
        return new AnnotationAuditAspect();
    }

    @Bean
    public AnnotationMetricsAspect annotationMetricsAspect() {
        return new AnnotationMetricsAspect();
    }
}
```

`@EnableAspectJAutoProxy` bật Spring AOP cho context annotation này; `@Bean` giúp aspect trở thành Spring-managed bean. Chỉ khai báo `@Aspect` trên class tự nó không đăng ký bean với container.

### 6.4. Chọn chế độ chạy

**`src/main/java/demo/Main.java`**

```java
package demo;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {
    public static void main(String[] args) {
        String mode = args.length == 0 ? "xml" : args[0];
        if (!mode.equals("xml") && !mode.equals("annotation")) {
            throw new IllegalArgumentException(
                    "mode must be xml or annotation");
        }

        ConfigurableApplicationContext context =
                mode.equals("xml")
                        ? new ClassPathXmlApplicationContext("aop.xml")
                        : new AnnotationConfigApplicationContext(
                                AnnotationConfig.class);

        try (context) {
            context.getBean(BillingService.class).pay();
        }
    }
}
```

Chạy bằng Gradle Wrapper hiện có trong dự án:

```bash
./gradlew run --args=xml
./gradlew run --args=annotation
```

Trên Windows, thay `./gradlew` bằng `.\gradlew.bat`. Nếu khởi tạo thư mục mới chưa có wrapper, cần tạo `gradlew`/`gradlew.bat` bằng bản Gradle tương thích hoặc dùng lệnh `gradle run --args=xml` và `gradle run --args=annotation` khi Gradle đã được cài.

**Cả hai chế độ in cùng những dòng ứng dụng sau, đúng thứ tự:**

```text
AUDIT enter
METRICS enter
PAY
METRICS exit
AUDIT exit
```

Gradle có thể in thêm thông tin task/build bên ngoài phần stdout trên. Đổi `order` giữa XML `audit`/`metrics` từ 1/2 thành 2/1 (hoặc đổi `@Order` tương ứng trong chế độ annotation), sẽ đổi aspect bên ngoài; expected stdout khi đó là:

```text
METRICS enter
AUDIT enter
PAY
AUDIT exit
METRICS exit
```

Đây là phép kiểm chứng hữu ích: **order quyết định lớp bao ngoài**, trong khi thứ tự log trên đường về đảo lại theo tính chất của `around` advice.

## 7. Quy tắc thực tế khi phối hợp XML, annotation và advisor

Khi pha trộn XML với `@Aspect`, transaction hoặc advisor do framework tạo, xác định **đủ các advisor đang áp dụng** trước khi lý giải thứ tự. Cùng giá trị order không tạo contract ổn định; `@Order` của một aspect không sắp xếp hai advice cùng loại bên trong chính class đó. Đổi thứ tự tạo bean hoặc vị trí các element `<aop:aspect>` khác nhau cũng không thay giá trị precedence đã khai báo.

Nếu retry, transaction và audit có thứ tự khác nhau, ranh giới commit, số lần target chạy và event audit có thể khác; dùng integration test trên Spring proxy thật. Trong giai đoạn migration, giữ nguyên tập advisors dự kiến và kiểm tra event trace trước/sau; bài **“Kết hợp XML với Java configuration mà không tạo hành vi trùng lặp”** phân tích nguồn duplicated registrations.

## 8. Checklist kiểm tra thứ tự advice trong ứng dụng Spring

1. Advice cần so sánh có thực sự cùng khớp một method execution đi qua Spring proxy không?
2. Chúng thuộc **hai aspect khác nhau** hay **cùng một XML `<aop:aspect>`** hay **cùng class `@Aspect`**?
3. Nếu khác aspect, đã đặt giá trị `order`/`@Order`/`Ordered` **khác nhau, tường minh** chưa?
4. Nếu cùng XML aspect, đã kiểm tra **thứ tự khai báo phần tử advice** và loại advice chưa?
5. Nếu cùng class `@Aspect`, có đang vô tình dựa vào thứ tự source code của hai advice cùng loại không?
6. Nếu có `@Around`, đang xét log vào hay log ra? `proceed()` và `finally` có làm chiều quan sát đảo lại không?
7. Có `<aop:advisor>`, transaction advisor hoặc security advisor cùng áp dụng mà chưa xem order riêng không?
8. Có đăng ký trùng aspect qua XML, `@Bean` hoặc component scan không?
9. Đã kiểm thử thứ tự bằng stdout hoặc test tích hợp gọi **qua proxy** thay vì gọi trực tiếp một đối tượng `new` chưa?

## 9. Ghi nhớ và tài liệu Spring

| Cấp cấu hình | Cách kiểm soát precedence | Có dựa được vào thứ tự dòng khai báo? |
|---|---|---|
| Hai `<aop:aspect>` XML | Thuộc tính `order` trên từng aspect (hoặc cơ chế ordering của backing bean) | Không dùng dòng XML thay cho `order` |
| Hai advice **trong cùng** `<aop:aspect>` | Thứ tự phần tử advice từ trên xuống dưới và semantics từng loại advice | **Có**, đây là quy tắc riêng của Spring XML schema AOP |
| Hai class `@Aspect` | `@Order` trên aspect hoặc `Ordered` | Không dựa vào vị trí class hoặc method trong source |
| Nhiều method advice **trong cùng** `@Aspect` | Quy tắc loại advice; cùng loại có thể không xác định | **Không**; đổi vị trí hai method cùng loại không tạo contract |
| `<aop:advisor>` | Thuộc tính `order` của advisor | Không dựa vào vị trí dòng XML khi không xác định precedence |

Tài liệu tham khảo của Spring Framework:

- [Schema-based AOP Support – Advice Ordering](https://docs.spring.io/spring-framework/reference/core/aop/schema.html#advice-ordering)
- [Declaring Advice – Advice Ordering for @Aspect](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html#advice-ordering)

**Kết luận về cấu hình Spring Aspect:** dùng `order`/`@Order` để quy định precedence **giữa các aspect**. Trong **cùng XML `<aop:aspect>`**, thứ tự phần tử advice có ý nghĩa; trong **cùng Java `@Aspect`**, không dùng thứ tự viết method để ép hai advice cùng loại chạy theo ý muốn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-aop-configuration-008">proxy-target-class và expose-proxy trong cấu hình XML</a>

<details>
<summary>Xem chi tiết</summary>

# `proxy-target-class` và `expose-proxy` trong cấu hình XML của Spring AOP

## 1. Vì sao cần hai thuộc tính này?

Trong Spring Framework, một bean được áp dụng Spring AOP thường được cung cấp cho nơi gọi dưới dạng **proxy**. Khi ứng dụng cấu hình aspect bằng XML, hai thuộc tính có ảnh hưởng quan trọng nhưng giải quyết **hai vấn đề khác nhau**:

- `proxy-target-class`: quyết định Spring ưu tiên loại proxy nào cho bean có thể được proxy, đặc biệt khi class triển khai interface. `true` yêu cầu proxy dựa trên class (CGLIB); `false` cho phép cơ chế mặc định của Spring chọn JDK dynamic proxy nếu có interface thích hợp, còn class không có interface vẫn cần class proxy.
- `expose-proxy`: quyết định proxy hiện tại có được Spring công bố tạm thời cho `AopContext.currentProxy()` trong **luồng đang thực thi lời gọi qua proxy** hay không. `true` mở khả năng truy cập đó; `false` là mặc định và không bảo đảm đọc được proxy.

Hai thuộc tính đều có trong **`<aop:config>`** (schema-based aspect) và **`<aop:aspectj-autoproxy>`** (tự tìm bean `@Aspect`). Chúng là các setting của cơ chế **Spring AOP proxy**, không phải kỹ thuật AspectJ compile-time weaving hay load-time weaving.

### Bảng đối chiếu nhanh

| XML | Tác dụng chính | Không làm được |
| --- | --- | --- |
| `proxy-target-class="false"` | Dùng quy tắc chọn proxy thông thường; bean có interface thường nhận JDK proxy, bean không có interface dùng class proxy. | Không ép JDK proxy cho class không có interface; không sửa self-invocation. |
| `proxy-target-class="true"` | Ép dùng class proxy (CGLIB) cho các bean do auto-proxy creator tương ứng xử lý. | Không intercept `private`, `final` method hoặc self-invocation qua `this`. |
| `expose-proxy="false"` | Không bật khả năng tra cứu proxy hiện tại qua `AopContext.currentProxy()`. | Không có nghĩa advice bị tắt; lời gọi qua proxy vẫn được intercept. |
| `expose-proxy="true"` | Cho phép `AopContext.currentProxy()` hoạt động trong lời gọi được quản lý bởi AOP proxy. | Không tự đổi `this.method()` thành lời gọi proxy; không truyền proxy sang thread khác. |

## 2. Viết XML đúng schema Spring

Ví dụ một file `src/main/resources/aop.xml` dùng Spring AOP schema:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd">

    <bean id="orderService" class="com.example.springxml.OrderService"/>
    <bean id="traceAdvice" class="com.example.springxml.TraceAdvice"/>

    <aop:config proxy-target-class="false" expose-proxy="true">
        <aop:aspect ref="traceAdvice">
            <aop:pointcut id="orderMethods"
                          expression="execution(* com.example.springxml.OrderService.*(..))"/>
            <aop:around method="around" pointcut-ref="orderMethods"/>
        </aop:aspect>
    </aop:config>
</beans>
```

Ý nghĩa trong Spring:

1. `<aop:config>` đăng ký hạ tầng tự tạo proxy khi có advisor phù hợp với bean.
2. `<aop:aspect ref="traceAdvice">` trỏ đến bean chứa method advice thuần Java; **bean advice này không bắt buộc có `@Aspect`** vì XML đã khai báo aspect.
3. `<aop:pointcut>` xác định method execution của `OrderService` cần áp dụng; `<aop:around>` liên kết method `around` với pointcut đã đặt tên.
4. `proxy-target-class="false"` cho phép chọn JDK proxy cho `OrderService` vì ví dụ bên dưới có interface. `expose-proxy="true"` cho phép service lấy proxy trong lúc đang chạy thông qua lời gọi đã được advice.

Cú pháp XML dùng tên `proxy-target-class`, `expose-proxy` (kebab-case), khác với tên thuộc tính Java `proxyTargetClass` và `exposeProxy`. Không viết `proxyTargetClass` trực tiếp trong tag XML.

### Khi dùng `@Aspect` thay cho aspect khai báo trong XML

Nếu aspect là một bean Java có `@Aspect`, có thể bật cơ chế nhận diện nó bằng:

```xml
<aop:aspectj-autoproxy proxy-target-class="true"
                       expose-proxy="true"/>
```

Các bean `@Aspect` vẫn cần được đăng ký bằng `<bean>` hoặc component scanning. Tag này **không tự tạo bean aspect** và khác với `<aop:config><aop:aspect ...>`. Thông thường chọn cách khai báo phù hợp với dự án; tránh tạo advisor trùng nhau khi cấu hình cả hai đường. Cả hai cần phần Spring AOP trên classpath và khả năng phân tích biểu thức AspectJ (thường có `aspectjweaver`).

## 3. Ví dụ Java 17+ quan sát loại proxy và self-invocation

Ví dụ sau chạy với **Spring Framework + Spring AOP được khởi tạo trực tiếp bằng XML**, không dựa vào Spring Boot auto-configuration; nhờ đó việc so sánh `proxy-target-class` có kết quả dễ kiểm soát. Cấu trúc mã:

```text
src/main/java/com/example/springxml/
  OrderOperations.java
  OrderService.java
  TraceAdvice.java
  Demo.java
src/main/resources/aop.xml
```

**`OrderOperations.java`** — interface được caller sử dụng:

```java
package com.example.springxml;

public interface OrderOperations {
    void place();
    void reserve();
}
```

**`OrderService.java`** — hai cách gọi cùng method `reserve()`:

```java
package com.example.springxml;

import org.springframework.aop.framework.AopContext;

public class OrderService implements OrderOperations {

    @Override
    public void place() {
        System.out.println("service: place");

        // Gọi trực tiếp trên 'this': không đi qua Spring proxy.
        this.reserve();

        // Đi qua Spring proxy đang được expose trong lời gọi place().
        ((OrderOperations) AopContext.currentProxy()).reserve();
    }

    @Override
    public void reserve() {
        System.out.println("service: reserve");
    }
}
```

**`TraceAdvice.java`** — POJO chứa advice được tham chiếu trong XML:

```java
package com.example.springxml;

import org.aspectj.lang.ProceedingJoinPoint;

public class TraceAdvice {

    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getName();
        System.out.println("before: " + method);
        try {
            return pjp.proceed();
        } finally {
            System.out.println("after: " + method);
        }
    }
}
```

**`Demo.java`** — lấy bean qua interface và kiểm tra proxy:

```java
package com.example.springxml;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Demo {

    public static void main(String[] args) {
        try (ClassPathXmlApplicationContext context =
                     new ClassPathXmlApplicationContext("aop.xml")) {

            OrderOperations orders =
                    context.getBean("orderService", OrderOperations.class);

            System.out.println("aopProxy=" + AopUtils.isAopProxy(orders));
            System.out.println("jdkProxy=" + AopUtils.isJdkDynamicProxy(orders));
            orders.place();
        }
    }
}
```

**Các dependency cần có:** `spring-context`, `spring-aop` và `org.aspectj:aspectjweaver`, ở các phiên bản tương thích với nhau (có thể quản lý bằng BOM Spring/Boot). Ví dụ Java trên cần classpath chứa các thư viện đó; không thể chỉ dùng `javac` với JDK thuần. Trong dự án Maven đã cấu hình `exec-maven-plugin`, có thể chạy main với `mvn compile exec:java -Dexec.mainClass=com.example.springxml.Demo`. Trong Gradle có plugin `application`, đặt main class là `com.example.springxml.Demo` và chạy task `run`. Các lệnh này giả định dự án đã được khai báo dependency/plugin phù hợp.

Với XML ở mục 2, những dòng do mã ví dụ in ra (không tính logging khác do cấu hình runtime) theo thứ tự sẽ là:

```text
aopProxy=true
jdkProxy=true
before: place
service: place
service: reserve
before: reserve
service: reserve
after: reserve
after: place
```

Giải thích: `orders.place()` gọi vào JDK proxy và đi qua `around`. Lệnh `this.reserve()` được thực hiện trực tiếp trên target, nên chỉ có dòng `service: reserve`. Lần gọi `AopContext.currentProxy().reserve()` quay lại proxy, vì thế có cả `before: reserve` và `after: reserve`. `AopContext.currentProxy()` được gọi trong cùng thread và bên trong method mà proxy đang xử lý; nếu gọi ngoài hoàn cảnh đó, Spring có thể ném `IllegalStateException`.

## 4. Thay `proxy-target-class`: kết quả nào thay đổi?

Đổi duy nhất thuộc tính trong file XML:

```xml
<aop:config proxy-target-class="true" expose-proxy="true">
    <!-- Giữ nguyên aspect, pointcut và các bean của ví dụ -->
</aop:config>
```

**Đây là đoạn rút gọn minh họa vị trí thuộc tính:** trong file runnable vẫn phải giữ nguyên các phần tử con `<aop:aspect>` và `<aop:pointcut>` như mục 2, không thay thế chúng bằng comment. Sau khi đổi, Spring tạo proxy dựa trên subclass của `OrderService`; các dòng đầu thành:

```text
aopProxy=true
jdkProxy=false
```

Phần log `before/after/service` của ví dụ vẫn như trước. Trong cấu hình CGLIB này, `context.getBean("orderService", OrderService.class)` có thể dùng được vì proxy là subclass của target class; ở chế độ JDK proxy, bean được inject/cast qua `OrderOperations`, còn lookup đòi chính `OrderService.class` có thể không khớp type.

Điểm cần nhớ:

- `false` không phải cam kết “luôn là JDK proxy”. Không có interface thích hợp thì Spring cần class proxy.
- `true` yêu cầu class proxy ngay cả khi target có interface. Nó **không** biến Spring AOP thành bytecode weaving hoặc làm method `final`/`private` intercept được.
- CGLIB dùng subclass của target; class `final` không thể subclass để proxy. `final` method không override được; `private` method không được advice trong cơ chế này. JDK proxy chỉ cung cấp các method của interface được proxy cho caller.
- Khi chương trình inject qua interface, việc đổi sang CGLIB thường không cần thiết chỉ để gọi các method đã công bố trên interface. Nếu đổi cho mục đích inject target concrete type hoặc intercept method chỉ nằm ở class, cần kiểm thử đầy đủ.
- Có thể dùng `AopUtils.isCglibProxy(bean)` và `AopUtils.isJdkDynamicProxy(bean)` trong integration test; tránh giả định class runtime có tên cố định.

### Cấu hình nhiều XML và cơ chế “strongest wins”

Spring hợp nhất các cấu hình auto-proxy creator trong **cùng ApplicationContext**. Nếu một `<aop:config>` bật `proxy-target-class="true"` trong khi cấu hình khác để `false`, việc bật class proxy có thể ảnh hưởng tất cả bean được các cơ chế AOP đó proxy, bao gồm các cấu hình tương tác như `<aop:aspectj-autoproxy>` và `<tx:annotation-driven>`. Giá trị `false` cục bộ không phải cách bảo đảm ghi đè một setting `true` từ cấu hình khác.

Vì vậy khi kết quả thực tế không giống ví dụ, kiểm tra toàn bộ XML được import cùng hạ tầng Spring AOP và transaction. Trong Spring Boot, auto-configuration có thể chọn class proxy mặc định tùy phiên bản/cấu hình; `spring.aop.proxy-target-class` là property ở lớp Spring Boot, còn các thuộc tính XML ở đây thuộc Spring Framework. Không lấy mặc định của ứng dụng Boot để suy luận kết quả của một ví dụ `ClassPathXmlApplicationContext` thuần.

## 5. Thay `expose-proxy`: tác động và ranh giới

Nếu đổi riêng `expose-proxy` thành `false`:

```xml
<aop:config proxy-target-class="false" expose-proxy="false">
    <!-- Giữ nguyên aspect và pointcut của ví dụ -->
</aop:config>
```

Câu lệnh `this.reserve()` trong `place()` vẫn thực thi trực tiếp và không có advice. Nhưng lệnh `AopContext.currentProxy()` không có proxy hiện tại được expose theo cấu hình này, nên ném `IllegalStateException`. Do `TraceAdvice.around` có `finally`, log trước khi lỗi được ném khỏi caller (bỏ qua phần startup) sẽ là:

```text
aopProxy=true
jdkProxy=true
before: place
service: place
service: reserve
after: place
```

Sau đó stack trace có thể xuất hiện nếu exception được để lan ra ngoài `main`. Đây là **hành vi có chủ đích để phân biệt setting**: bật/tắt `expose-proxy` không quyết định bean có được advice hay không; nó chỉ quyết định có thể tra cứu proxy trong ngữ cảnh lời gọi đang được intercept.

### Vì sao Spring không tự sửa self-invocation?

Trong Spring AOP, lời gọi bên ngoài vào proxy mới kích hoạt interceptor chain. Bên trong body của target, `this` vẫn là đối tượng target; `this.reserve()` bỏ qua proxy. Dùng `proxy-target-class="true"` cũng không đổi điều này.

Khi cần advice chạy trên một lời gọi nội bộ, thứ tự lựa chọn hợp lý là:

1. **Tách method cần intercept sang một Spring bean khác**, để bean hiện tại gọi qua reference được inject; cách này dễ test và không gắn business code với API Spring AOP.
2. Nếu kiến trúc đòi giữ trong class, có thể tổ chức lời gọi qua self-reference trỏ đến bean proxy, nhưng cần xử lý dependency cycle và kiểu proxy cẩn thận.
3. Chỉ khi cần thiết mới bật `expose-proxy="true"` và gọi `AopContext.currentProxy()`. Đây là cách ràng buộc source nghiệp vụ trực tiếp với Spring AOP, làm khó unit test thuần Java và tăng chi phí truy cập proxy hiện tại.

**Hạn chế của `AopContext.currentProxy()`:**

- Proxy được expose thông qua ngữ cảnh gắn với thread của lời gọi AOP hiện tại; không mang nguyên sang `CompletableFuture`, `@Async`, `ExecutorService` hoặc thread khác.
- Gọi từ constructor, từ đối tượng được tạo bằng `new`, trước khi vào lời gọi proxy hoặc sau khi ra khỏi lời gọi proxy không có bảo đảm lấy được proxy.
- Kiểu trả về là `Object`. Với JDK proxy, cast sang **interface có được proxy**; cast sang class implementation gây `ClassCastException`.
- Proxy lấy được phải là proxy Spring liên quan đúng lời gọi hiện tại; `expose-proxy` không cấp global singleton lookup.
- Bật `true` chỉ để “phòng trường hợp cần” là không cần thiết. Nó tạo coupling và overhead ngay cả khi ứng dụng không dùng `AopContext`.

## 6. So sánh `<aop:config>`, `<aop:aspectj-autoproxy>` và Java config

| Ngữ cảnh | Cấu hình | Khi nào sử dụng |
| --- | --- | --- |
| Aspect thuần Java khai báo bằng Spring XML | `<aop:config proxy-target-class="true" expose-proxy="true"> ... </aop:config>` | Advice/pointcut nằm trong XML, tham chiếu bean bằng `ref`. |
| Bean `@Aspect` được Spring nhận diện | `<aop:aspectj-autoproxy proxy-target-class="true" expose-proxy="true"/>` | Advice/pointcut nằm trong Java `@Aspect`, XML bật auto-proxy. |
| Spring `@Configuration` | `@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)` | Cùng hai ý nghĩa ở Java configuration. |
| Programmatic AOP | `ProxyFactory.setProxyTargetClass(true)` và `ProxyFactory.setExposeProxy(true)` | Tạo proxy thủ công qua `ProxyFactory`, khác với auto-proxy XML toàn context. |

Lưu ý `<aop:scoped-proxy proxy-target-class="..."/>` thuộc cơ chế scoped proxy cho bean theo scope; đừng đánh đồng nó với `expose-proxy` trên `<aop:config>`/`<aop:aspectj-autoproxy>`.

Trong ứng dụng Boot nhập XML bằng `@ImportResource("classpath:aop.xml")` nếu cần tích hợp cấu hình kiểu cũ vào Java configuration; sau đó kiểm tra các bean và auto-proxy settings có thể đã được Boot hay module khác đăng ký. Đây không phải lý do để lặp lại tag AOP ở nhiều nơi.

## 7. Kiểm tra cấu hình và chẩn đoán lỗi

| Triệu chứng | Kiểm tra | Hướng xử lý |
| --- | --- | --- |
| `getBean(..., OrderService.class)` không lấy được bean, nhưng inject interface được | Bean là JDK dynamic proxy | Inject interface; chỉ ép class proxy khi có yêu cầu thực tế. |
| Method không có advice dù đã đặt pointcut | Proxy được tạo chưa, method không match, hoặc lời gọi đi qua `this` | Kiểm tra `AopUtils.isAopProxy`, expression, cách caller nhận bean từ context. |
| `AopContext.currentProxy()` ném `IllegalStateException` | `expose-proxy` chưa bật hoặc đang ngoài lời gọi qua proxy | Ưu tiên gọi sang bean khác; nếu phải dùng `AopContext`, bật đúng setting và kiểm tra thread/call path. |
| `ClassCastException` với `(OrderService) AopContext.currentProxy()` | Actual proxy là JDK interface proxy | Cast sang `OrderOperations` hoặc cân nhắc class proxy. |
| Chọn `proxy-target-class="true"` rồi Spring không tạo được proxy | Target class `final`, method `final`, hạn chế Java module/class loading hoặc conflict hạ tầng | Điều chỉnh khả năng proxy của target và kiểm tra bean definition, JVM/runtime. |
| Đã đặt `proxy-target-class="false"` nhưng vẫn thấy CGLIB | Target không có interface hoặc có một config khác ép `true` | Rà soát cùng `ApplicationContext`, `<tx:annotation-driven>`, `<aop:aspectj-autoproxy>` và Boot. |
| Viết XML nhưng Spring báo không biết `aop:` | Thiếu `xmlns:aop`, schemaLocation hoặc dependency AOP | Khai báo đúng Spring AOP XSD và thư viện tương thích. |
| Advice bị gọi hai lần | Đăng ký hai advisor/aspect trùng, hai bean aspect hoặc pointcut chồng nhau | Kiểm tra các config XML/Java cùng context, giữ một đường đăng ký rõ ràng. |

Có thể kiểm thử proxy bằng integration test sau khi context khởi động:

```java
Object bean = context.getBean("orderService");

assert org.springframework.aop.support.AopUtils.isAopProxy(bean);
assert org.springframework.aop.support.AopUtils.isJdkDynamicProxy(bean);
```

Hai `assert` trên thuộc **kịch bản XML `proxy-target-class="false"` của ví dụ** và cần bật assertions với `-ea` nếu chạy như Java code thông thường; trong test JUnit nên dùng `assertTrue(...)`. Cần test thêm dấu vết advice (counter hoặc log collector) vì chỉ kiểm tra lớp proxy chưa chứng minh biểu thức pointcut thực sự áp dụng.

### Checklist review khi thay đổi XML thật

1. Xác định `<aop:config>` hay `<aop:aspectj-autoproxy>` đang được sử dụng và đã đăng ký các bean aspect/advice chưa.
2. Liệt kê interface mà các consumer dùng để inject bean; chỉ bật class proxy khi có yêu cầu về class/method bên ngoài interface.
3. Xác định self-invocation có thật sự cần intercept hay không. Nếu có, ưu tiên tách bean; nếu dùng `AopContext`, kiểm tra `expose-proxy` và lời gọi qua proxy.
4. Kiểm tra ảnh hưởng của `<tx:annotation-driven>`, Spring Boot AOP, `@EnableAspectJAutoProxy` hay nhiều file XML lên auto-proxy creator hợp nhất.
5. Chạy test với bean lấy từ Spring context, thử nhánh thành công/lỗi, so sánh loại proxy, đếm advice và kiểm tra những caller inject qua concrete class.

## 8. Tài liệu Spring chính thức để tra cứu

- [Spring Framework 6.2 — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): cách chọn JDK/CGLIB, self-invocation và `AopContext`.
- [Spring Framework 6.2 — Schema-based AOP Support](https://docs.spring.io/spring-framework/reference/6.2/core/aop/schema.html): `<aop:config>`, khai báo aspect, pointcut và advice XML.
- [Spring AOP XSD](https://www.springframework.org/schema/aop/spring-aop.xsd): xác nhận `proxy-target-class` và `expose-proxy` đều được hỗ trợ trên `config` và `aspectj-autoproxy`.

**Điểm cần ghi nhớ:** `proxy-target-class` điều khiển **kiểu proxy**; `expose-proxy` điều khiển **khả năng lấy proxy hiện tại qua `AopContext`**. Cả hai đều hoạt động trong cơ chế proxy của Spring AOP và không tự giải quyết self-invocation qua `this`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-aop-configuration-009">Thoát ký tự và dùng and, or, not trong biểu thức</a>

<details>
<summary>Xem chi tiết</summary>


# Thoát ký tự và sử dụng `and`, `or`, `not` trong biểu thức Spring AOP cấu hình XML

## 1. Vấn đề thực tế

Khi định nghĩa pointcut trong `applicationContext.xml`, ta đang viết **biểu thức pointcut của Spring AOP bên trong một thuộc tính XML**. Hai lớp cú pháp được xử lý nối tiếp nhau:

1. XML parser đọc tài liệu, giải mã XML entities trong giá trị thuộc tính (ví dụ `&amp;` thành ký tự `&`).
2. Spring AOP/AspectJ pointcut parser nhận chuỗi biểu thức sau khi XML đã giải mã, rồi xác định những lời gọi method trên Spring bean nào khớp.

Vì vậy một biểu thức hoàn toàn hợp lệ trong Java `@Pointcut` có thể làm XML không hợp lệ nếu chép nguyên ký tự `&&` vào attribute `expression`. Trong cấu hình XML, Spring cho phép dùng **từ khóa `and`, `or`, `not`** tương đương các toán tử `&&`, `||`, `!`. Cách dùng từ khóa thường dễ đọc hơn và tránh phải escape ký tự `&`.

Mục tiêu bài này là viết và kiểm tra các biểu thức **Spring AOP proxy-based** trong namespace `aop`; chỉ giải thích phần ngôn ngữ biểu thức liên quan trực tiếp đến Spring XML.

## 2. Bảng đối chiếu cú pháp toán tử

| Ý nghĩa | Biểu thức trong Java `@Pointcut` | Cách khuyên dùng ở Spring XML | Dạng ký hiệu vẫn dùng được trong XML |
| --- | --- | --- | --- |
| Đồng thời thỏa hai điều kiện (AND) | `A && B` | `A and B` | `A &amp;&amp; B` |
| Thỏa ít nhất một điều kiện (OR) | `A || B` | `A or B` | `A || B` |
| Phủ định một điều kiện (NOT) | `!A` | `not A` | `!A` |
| Nhóm điều kiện | `(A || B) && !C` | `(A or B) and not C` | `(A || B) &amp;&amp; !C` |

Trong bảng, `A`, `B`, `C` là các pointcut sub-expression hợp lệ, chẳng hạn `execution(* demo.OrderService.save(..))`, `within(demo..*)`, `bean(*Service)` hoặc `@annotation(demo.Audited)`.

- `and` kết hợp các điều kiện: một method được advice nếu **mọi** điều kiện đều khớp.
- `or` tạo tập hợp: một method được advice khi **ít nhất một** nhánh khớp.
- `not` loại trừ các join point mà điều kiện được phủ định khớp.
- Ngoặc đơn là cú pháp **pointcut expression**, không phải XML entity; dùng trực tiếp để làm rõ thứ tự tính.
- `&&`, `||`, `!` là toán tử trong biểu thức pointcut, **không phải** phép toán Java được chạy khi method nghiệp vụ thực thi. Spring phân tích chúng để tạo điều kiện chọn join point.

### Ví dụ đơn giản

~~~xml
<!-- Chỉ advice method public trong package demo. -->
<aop:pointcut id="publicInDemo"
    expression="within(demo..*) and execution(public * *(..))"/>
~~~

Điều kiện `within(demo..*)` chọn phạm vi khai báo, còn `execution(public * *(..))` chọn các method execution `public`. Spring AOP chỉ chặn **lời gọi method đi qua Spring proxy**; biểu thức không tự làm cho các đối tượng được tạo bằng `new` trở thành bean.

Với OR:

~~~xml
<!-- Match method send(...) hoặc receive(...), bất kể số lượng argument. -->
<aop:pointcut id="ioOperations"
    expression="execution(* *.send(..)) or execution(* *.receive(..))"/>
~~~

Với NOT:

~~~xml
<!-- Chọn bean tên kết thúc bằng Service nhưng loại bean testService. -->
<aop:pointcut id="nonTestServices"
    expression="bean(*Service) and not bean(testService)"/>
~~~

`bean(idOrNamePattern)` là pointcut designator riêng của **Spring AOP** để xét tên bean. Nó có thể kết hợp các toán tử ở trên; không phải cùng một cơ chế với quét class hoặc chọn package.

## 3. Vì sao `&&` cần `&amp;&amp;` trong XML?

Ký tự `&` có vai trò mở đầu entity reference của XML, vì vậy không thể xuất hiện trần trong giá trị attribute.

**Sai: XML không hợp lệ**

~~~xml
<aop:pointcut id="bad"
    expression="within(demo..*) && execution(public * *(..))"/>
~~~

Bộ phân tích XML gặp `&` đầu tiên và mong đợi một entity hợp lệ; việc phân tích dừng trước khi Spring kịp hiểu pointcut. Lỗi thường biểu hiện như `SAXParseException` hoặc thông báo liên quan entity/name, không phải lỗi logic của `execution(...)`.

**Đúng: dùng chữ `and`**

~~~xml
<aop:pointcut id="correctWords"
    expression="within(demo..*) and execution(public * *(..))"/>
~~~

**Cũng đúng: escape từng ký tự `&`**

~~~xml
<aop:pointcut id="correctEntities"
    expression="within(demo..*) &amp;&amp; execution(public * *(..))"/>
~~~

Sau XML parsing, ví dụ thứ ba đưa đến Spring AOP một chuỗi chứa `&&`. Hai biểu thức đúng trong mục này có cùng ý nghĩa match; chúng không khác nhau về thời điểm advice chạy hay khả năng tạo proxy.

Cần phân biệt:

- **Nội dung XML source**: `&amp;&amp;` có tổng cộng hai entity `&amp;` liên tiếp.
- **Chuỗi đã parse**: `&&`.
- **Pointcut expression**: toán tử AND nối hai điều kiện.

Không viết `&amp;amp;&amp;amp;` do escape hai lần: khi XML parser giải mã một lượt, Spring có thể nhận literal `&amp;&amp;` và pointcut parser không nhận được toán tử mong muốn.

## 4. Các XML entity cần nhớ khi pointcut nằm trong attribute

| Ký tự muốn đưa vào giá trị attribute | Cách viết XML source | Khi nào liên quan |
| --- | --- | --- |
| `&` | `&amp;` | Bắt buộc nếu dùng ký hiệu `&&` |
| `<` | `&lt;` | Bắt buộc trong XML attribute nếu thực sự cần dấu nhỏ hơn |
| `>` | `&gt;` | Có thể dùng để rõ ràng, dù ký tự `>` thường được phép trong attribute |
| `"` | `&quot;` | Cần khi attribute được bọc bằng dấu nháy kép và muốn chứa nháy kép bên trong |
| `'` | `&apos;` | Cần khi attribute được bọc bằng dấu nháy đơn và muốn chứa dấu nháy đơn bên trong |

Không phải tất cả entity trong bảng đều thường xuyên dùng ở pointcut; phổ biến nhất là `&amp;`. Các toán tử `||` và `!` không chứa ký tự XML bắt buộc escape, vì vậy phiên bản ký hiệu của OR và NOT có thể đặt thẳng trong attribute.

XML entity và cú pháp pointcut là **hai cơ chế độc lập**. Viết `&amp;` chỉ làm tài liệu XML hợp lệ; nó không thay đổi ý nghĩa chọn method. Các ký tự `*`, `..`, `(..)`, `@` trong biểu thức Spring AOP thường được viết nguyên vẹn.

**Lưu ý đặc biệt:** Không áp dụng cách escape cho code Java `@Aspect` một cách máy móc:

~~~java
@Pointcut("within(demo..*) && execution(public * *(..))")
public void publicOperationsInDemo() {}
~~~

Trong Java string literal ở trên, `&&` không nằm trong XML attribute nên không phải viết `&amp;&amp;`.

## 5. Chọn đúng thứ tự bằng ngoặc đơn

Khi phối hợp `and`, `or`, `not`, dùng ngoặc để diễn đạt rõ **nhóm tích cực** và **nhóm loại trừ**. Cách này tránh nhầm ý nghĩa giữa người đọc và kết quả parser khi biểu thức phức tạp.

Xét quy tắc nghiệp vụ:

> Chỉ theo dõi bean service, trên hai method `send` hoặc `receive`, nhưng không áp dụng cho bean thử nghiệm `testService`.

Viết:

~~~text
bean(*Service)
and (execution(* demo.DefaultMessageService.send(..))
     or execution(* demo.DefaultMessageService.receive(..)))
and not bean(testService)
~~~

Trong một XML attribute, xuống dòng có thể bị chuẩn hóa thành dấu cách. Với bài học hoặc cấu hình cần dễ diff, viết gọn trên cùng dòng hoặc tách pointcut một cách có chủ đích.

~~~xml
<aop:pointcut id="auditedCalls"
    expression="bean(*Service) and (execution(* demo.DefaultMessageService.send(..)) or execution(* demo.DefaultMessageService.receive(..))) and not bean(testService)"/>
~~~

Quy tắc trên thực hiện ba bước logic:

1. `bean(*Service)`: chọn những Spring beans mà tên khớp wildcard.
2. `(send or receive)`: chỉ giữ lại hai nhóm method, không giới hạn vào một nhóm duy nhất.
3. `and not bean(testService)`: trừ các invocation trên bean thử nghiệm.

Nếu viết thành `bean(*Service) and send or receive and not bean(testService)` mà không có ngoặc, cấu trúc đã khác về cách liên kết toán tử và dễ khiến `receive` vượt qua một phần điều kiện mong muốn. Luôn đưa `or` vào nhóm rõ ràng khi hai nhánh phải dùng chung điều kiện bao ngoài.

**Một tình huống nhầm phủ định:**

~~~text
not (A or B)   =   (not A) and (not B)
not (A and B)  =   (not A) or (not B)
~~~

Ví dụ `not (send or receive)` chọn các method **không thuộc cả hai nhóm send/receive**; còn `not send or receive` có ngữ nghĩa khác. Trong Spring AOP, các điểm match vẫn phải là method invocation qua proxy, dù biểu thức Boolean về mặt hình thức cho kết quả true.

## 6. Ví dụ hoàn chỉnh Spring XML AOP dùng cả `and`, `or`, `not`

Ví dụ sử dụng Java 17, Maven và Spring Framework 6.1.15. Hai bean `messageService` và `testService` dùng cùng implementation `DefaultMessageService`. Chỉ bean `messageService` được advice trên `send` và `receive`; method `health` không nằm trong biểu thức, còn bean thử nghiệm bị loại bằng `not bean(testService)`.

Cấu trúc:

~~~text
spring-xml-pointcut-operators/
├── pom.xml
└── src/main/
    ├── java/demo/
    │   ├── Application.java
    │   ├── MessageService.java
    │   ├── DefaultMessageService.java
    │   └── LoggingAspect.java
    └── resources/applicationContext.xml
~~~

### 6.1. Maven

**pom.xml**

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-xml-pointcut-operators</artifactId>
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
                <configuration><mainClass>demo.Application</mainClass></configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cung cấp ApplicationContext, `spring-aop` cung cấp Spring proxy/advisor runtime, còn `aspectjweaver` chứa công cụ hỗ trợ phân tích/match AspectJ pointcut được Spring dùng. Đây vẫn là **Spring AOP**, không chạy compile-time hay load-time weaving.

### 6.2. Interface dùng để gọi qua Spring proxy

**src/main/java/demo/MessageService.java**

~~~java
package demo;

public interface MessageService {
    String send(String id);
    String receive(String id);
    String health();
}
~~~

**src/main/java/demo/DefaultMessageService.java**

~~~java
package demo;

public class DefaultMessageService implements MessageService {
    @Override
    public String send(String id) {
        System.out.println("target: send " + id);
        return "sent " + id;
    }

    @Override
    public String receive(String id) {
        System.out.println("target: receive " + id);
        return "received " + id;
    }

    @Override
    public String health() {
        System.out.println("target: health");
        return "UP";
    }
}
~~~

Hai bean cùng Java class có thể cho ra kết quả advice khác nhau vì pointcut `bean(...)` xét Spring bean name. Không cần gắn annotation trên class để ví dụ XML-based này hoạt động.

### 6.3. Bean chứa advice

**src/main/java/demo/LoggingAspect.java**

~~~java
package demo;

import org.aspectj.lang.JoinPoint;

public class LoggingAspect {
    public void before(JoinPoint joinPoint) {
        System.out.println("advice: " + joinPoint.getSignature().getName());
    }
}
~~~

`LoggingAspect` là Java class đơn giản. Nó sẽ được đăng ký bằng `<bean>` và liên kết tới `<aop:before method="before">`. Không cần `@Aspect` hoặc `@Before` trong lớp khi dùng schema-based AOP.

### 6.4. Định nghĩa pointcut trong XML

**src/main/resources/applicationContext.xml**

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

    <bean id="messageService" class="demo.DefaultMessageService"/>
    <bean id="testService" class="demo.DefaultMessageService"/>
    <bean id="loggingAspect" class="demo.LoggingAspect"/>

    <aop:config proxy-target-class="false">
        <aop:pointcut id="auditedCalls"
            expression="bean(*Service) and (execution(* demo.DefaultMessageService.send(..)) or execution(* demo.DefaultMessageService.receive(..))) and not bean(testService)"/>

        <aop:aspect ref="loggingAspect">
            <aop:before pointcut-ref="auditedCalls" method="before"/>
        </aop:aspect>
    </aop:config>
</beans>
~~~

Các thành phần có vai trò rõ ràng:

- `xmlns:aop` và `spring-aop.xsd` khai báo cú pháp namespace cho tài liệu XML.
- Ba `<bean>` tạo target bean (hai tên), và aspect bean chứa method advice.
- `<aop:pointcut id="auditedCalls">` lưu một biểu thức hoàn chỉnh; `pointcut-ref="auditedCalls"` tham chiếu **ID cấu hình pointcut**.
- `<aop:aspect ref="loggingAspect">` trỏ đến Spring bean chứa method `before`.
- `proxy-target-class="false"` phù hợp với `MessageService` interface của hai target. Lời gọi bên ngoài nên lấy bean qua interface.
- Khi advice chạy, `LoggingAspect.before` chỉ ghi log trước khi method target thực thi, không thay đổi return value.

`not bean(testService)` không xóa bean `testService` khỏi context; nó chỉ làm cho **pointcut cụ thể này** không match method invocation trên bean đó.

### 6.5. Gọi Spring beans

**src/main/java/demo/Application.java**

~~~java
package demo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Application {
    public static void main(String[] args) {
        try (var context =
                new ClassPathXmlApplicationContext("applicationContext.xml")) {

            MessageService production =
                    context.getBean("messageService", MessageService.class);
            MessageService testing =
                    context.getBean("testService", MessageService.class);

            System.out.println("result: " + production.send("M-1"));
            System.out.println("result: " + production.receive("M-2"));
            System.out.println("result: " + production.health());
            System.out.println("result: " + testing.send("T-1"));
        }
    }
}
~~~

Ở thư mục chứa `pom.xml`, chạy:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chương trình in (bỏ qua log Maven/Spring phụ thuộc môi trường) theo thứ tự:

~~~text
advice: send
target: send M-1
result: sent M-1
advice: receive
target: receive M-2
result: received M-2
target: health
result: UP
target: send T-1
result: sent T-1
~~~

Giải thích kết quả:

1. `production.send`: bean tên `messageService` match `bean(*Service)`, nhánh `send` match, điều kiện `not bean(testService)` cũng true ⇒ advice chạy.
2. `production.receive`: khớp nhánh `receive` của OR ⇒ advice chạy.
3. `production.health`: không khớp cả hai nhánh method-name bên trong ngoặc ⇒ không chạy advice.
4. `testing.send`: khớp tên method nhưng bean tên `testService` bị phủ định ⇒ không chạy advice.

Cần lấy service từ `ApplicationContext` để có Spring-managed proxy. Tạo `new DefaultMessageService()` rồi gọi `send` sẽ không đi qua Spring AOP. Self-invocation như `this.send(...)` bên trong chính target cũng thường bỏ qua proxy.

## 7. Biểu thức dùng dấu toán tử có ý nghĩa tương đương

Ta có thể thay **chỉ thuộc tính expression** trong pointcut ví dụ bằng:

~~~xml
<aop:pointcut id="auditedCalls"
    expression="bean(*Service) &amp;&amp; (execution(* demo.DefaultMessageService.send(..)) || execution(* demo.DefaultMessageService.receive(..))) &amp;&amp; !bean(testService)"/>
~~~

Bản này tương đương bản dùng `and`, `or`, `not`. XML parser giải mã hai cặp `&amp;&amp;` thành `&&`; Spring pointcut parser hiểu các toán tử và điều kiện như cũ. Dấu `||`, `!`, dấu ngoặc và wildcard dùng nguyên văn. **Không đặt cả hai `<aop:pointcut id="auditedCalls">` trùng ID trong cùng cấu hình**; đây là một bản thay thế để so sánh.

Sơ đồ quan sát cú pháp:

~~~text
applicationContext.xml
  expression="A &amp;&amp; (B || C) &amp;&amp; !D"
            |
            | XML parser
            v
  pointcut string: A && (B || C) && !D
            |
            | Spring AOP/AspectJ expression parser
            v
  match: A and (B or C) and not D
            |
            | Spring advisor / proxy
            v
  Chỉ lời gọi bean method thỏa điều kiện mới chạy advice
~~~

Đặc biệt, escape đúng không làm thay đổi logic lọc `bean(*Service)`: tên `messageService` và `testService` vẫn như trước.

## 8. Phân biệt lỗi XML parsing và lỗi pointcut parsing

**Lỗi ở lớp XML parsing:** XML chưa được parse thành document hợp lệ.

~~~xml
<!-- Sai XML: dấu & chưa escape. -->
<aop:pointcut id="brokenXml"
    expression="execution(* demo..*.*(..)) && bean(*Service)"/>
~~~

Hệ quả: Spring có thể báo lỗi khi đọc XML trước khi tạo ApplicationContext. Cách sửa: thay `&&` bằng `and`, hoặc `&amp;&amp;`; kiểm tra thêm namespace và `schemaLocation`.

**Lỗi ở lớp pointcut parsing:** XML hợp lệ nhưng biểu thức đưa vào Spring không hợp lệ hoặc dùng designator không được Spring AOP hỗ trợ.

~~~xml
<!-- XML hợp lệ; biểu thức sai vì thiếu vế bên phải của and. -->
<aop:pointcut id="brokenPointcut"
    expression="execution(* demo..*.*(..)) and"/>
~~~

Hệ quả: XML parser hoàn thành; Spring/AspectJ pointcut parser báo lỗi expression/advisor khi khởi tạo hạ tầng. Cách sửa: viết đầy đủ vế phải và đảm bảo kiểu pointcut designator được Spring AOP hỗ trợ (`execution`, `within`, `this`, `target`, `args`, `bean`, các designator annotation tương ứng...). Spring AOP không hỗ trợ mọi loại join point của AspectJ (ví dụ field `get`, `set` hoặc `call`).

**Không chạy advice dù XML và pointcut hợp lệ:** biểu thức match tập phương thức rỗng, bean không đi qua proxy hoặc có self-invocation. Đây là vấn đề runtime/proxy boundary, không có nghĩa phải thêm XML escaping.

## 9. Cẩn trọng với named pointcut trong XML

Một `<aop:pointcut id="allServiceMethods" expression="..."/>` đặt tên cho **một biểu thức hoàn chỉnh**. Advice dùng `pointcut-ref="allServiceMethods"`, còn `expression="allServiceMethods and not bean(testService)"` **không hợp lệ** vì ID XML không phải named pointcut method có thể gọi trong expression.

Nếu cần phủ định bean, khai báo scope cuối cùng ngay trong expression:

~~~xml
<aop:pointcut id="productionServices"
    expression="execution(* demo..*Service.*(..)) and not bean(testService)"/>
~~~

Khi các advice dùng cùng tập method, chúng tham chiếu ID `productionServices`. Bài **“Pointcut có tên và pointcut-ref trong XML”** phân tích đầy đủ khả năng dùng lại của ID tại cấp config/aspect, cùng test thay đổi phạm vi.

## 10. Kết hợp pointcut `@annotation`, `within` và `bean`

Ví dụ để log mọi method trong package `demo` **có annotation** `@Audited`, ngoại trừ bean nội bộ `internalService`:

~~~xml
<aop:pointcut id="annotatedBusinessMethods"
    expression="within(demo..*) and @annotation(demo.Audited) and not bean(internalService)"/>
~~~

Điều kiện trên có ý nghĩa:

- `within(demo..*)` giới hạn method execution trong namespace class/type tương ứng.
- `@annotation(demo.Audited)` lọc theo annotation trên method.
- `not bean(internalService)` loại bean nội bộ theo ID/name.

Đây là ví dụ **độc lập**; class `demo.Audited` cần được định nghĩa với runtime retention và đặt trên method thực tế nếu muốn chạy. Không nhầm `@annotation` là XML annotation namespace; ký tự `@` ở đây thuộc pointcut syntax.

Trong project lớn, kết hợp phạm vi kiểu/package với điều kiện mục tiêu giúp expression dễ kiểm tra hơn. Không dùng `or` vô ý để đưa vào các method ngoài vùng cần thiết:

~~~xml
<!-- Dễ chọn nhầm: nhánh thứ hai không chịu giới hạn within(demo..*). -->
<aop:pointcut id="tooBroad"
    expression="within(demo..*) and execution(* *.save(..)) or execution(* *.delete(..))"/>
~~~

Phiên bản nêu rõ cùng một phạm vi:

~~~xml
<aop:pointcut id="scoped"
    expression="within(demo..*) and (execution(* *.save(..)) or execution(* *.delete(..)))"/>
~~~

Các ví dụ này chỉ minh họa điều kiện; không cần tạo thêm advice nếu mục tiêu hiện tại chỉ là phân tích cú pháp.

## 11. Kết hợp với `aop:aspectj-autoproxy` và `@Aspect`

Với aspect dùng annotation Java, cú pháp logic nằm **trong Java string** nên không cần XML entity:

~~~java
@Aspect
public class TraceAspect {
    @Before("within(demo..*) && (execution(* *.send(..)) || execution(* *.receive(..))) && !bean(testService)")
    public void before() {}
}
~~~

Đây là fragment, cần imports và phải đăng ký `TraceAspect` thành Spring bean. XML chỉ cần đăng ký bean ấy rồi bật `<aop:aspectj-autoproxy/>`; annotation method có thể dùng `&&` bình thường. Nếu dùng schema-style `<aop:config>` để định nghĩa advice, không cần thêm auto-detection @AspectJ cho chính advice ấy.

## 12. Thứ tự sửa lỗi khi pointcut XML không hoạt động

1. **Kiểm tra XML hợp lệ:** `xmlns:aop`, `spring-aop.xsd`, đúng cặp namespace–schema trong `xsi:schemaLocation`, không chứa ký tự `&` chưa escape; tránh `&&` thô trong attribute.
2. **Kiểm tra expression hợp lệ:** mỗi toán tử có đủ toán hạng, các ngoặc cân bằng, `and`/`or`/`not` không viết sai chính tả, designator nằm trong tập Spring AOP hỗ trợ.
3. **Kiểm tra ý nghĩa Boolean:** dùng ngoặc cho nhóm OR rồi AND với điều kiện chung; `not` được áp dụng vào đúng sub-expression; tránh lọc nhiều hơn hoặc rộng hơn mong muốn.
4. **Kiểm tra bean thực tế:** tên bean có thật và match `bean(...)`, target thuộc ApplicationContext, advice/aspect bean đã đăng ký, `pointcut-ref` khớp ID.
5. **Kiểm tra proxy invocation:** gọi qua bean Spring (interface khi dùng JDK proxy), không dùng `new` target hoặc trông đợi self-invocation tự được chặn.
6. **Kiểm tra output bằng nhiều trường hợp:** một method phải match AND, một method thỏa một nhánh OR, một method bị NOT loại, một method không thuộc nhóm; so sánh thứ tự log advice/target.

### Ma trận test cho ví dụ mục 6

| Bean | Method | `bean(*Service)` | `send or receive` | `not bean(testService)` | Advice? |
| --- | --- | --- | --- | --- | --- |
| `messageService` | `send` | Đúng | Đúng | Đúng | Có |
| `messageService` | `receive` | Đúng | Đúng | Đúng | Có |
| `messageService` | `health` | Đúng | Sai | Đúng | Không |
| `testService` | `send` | Đúng | Đúng | Sai | Không |
| `testService` | `receive` | Đúng | Đúng | Sai | Không |

Nếu môi trường thực tế có nhiều auto-proxy configurations hoặc kiểu proxy khác nhau, kiểm tra bằng integration test lấy bean trực tiếp từ cùng `ApplicationContext`. Không suy đoán rằng một biểu thức pointcut đúng là đủ để advice chạy mọi lúc: Spring AOP phụ thuộc khả năng intercept qua proxy.

## 13. Những lỗi dễ lặp lại

Các lỗi nổi bật: (1) `&&` thô làm XML parser từ chối tài liệu; (2) escape hai lần khiến parser pointcut nhận literal sai; (3) thiếu ngoặc quanh OR mở rộng tập match; (4) nhầm `pointcut-ref` là biểu thức; (5) `not bean(testService)` bị bỏ và chặn nhầm bean thử nghiệm.

Khi sửa, kiểm tra **hai tầng parser** theo thứ tự XML entity → pointcut expression rồi thử ma trận: hai method cần match, method ngoài nhóm OR, bean phải loại trừ, method trên object không qua proxy. Chỉ có proxy chưa chứng minh advice cụ thể đã chạy.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-aop-configuration-010">Kết hợp XML với Java configuration mà không tạo hành vi trùng lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp XML với Java configuration mà không tạo hành vi trùng lặp trong Spring AOP

## 1. Bài toán và mục tiêu

Trong một ứng dụng Spring Framework có lịch sử cấu hình lâu dài, một phần bean và Spring AOP advice thường được khai báo bằng XML, trong khi các chức năng mới dùng `@Configuration`, `@Bean`, `@Aspect` và `@EnableAspectJAutoProxy`. Hai cách khai báo **có thể cùng tồn tại**, bởi Spring đưa chúng vào cùng cơ chế proxy và advisor. Rủi ro không nằm ở việc một dự án có cả hai loại tệp, mà ở cách đăng ký cùng một aspect, cùng một chức năng hoặc nhiều advisor chồng lấn khiến một method bị audit, logging hay metrics **hai lần**.

Chẳng hạn, một service xử lý `process("A")`. Mục tiêu là có **đúng một bản ghi audit** cho lời gọi ấy. Nếu vừa giữ khai báo `<aop:aspect>` trong XML vừa đăng ký `@Aspect` làm cùng chức năng với cùng pointcut, audit có thể bị ghi hai lần dù nghiệp vụ chỉ chạy một lần. Ngược lại, hai advice độc lập trên cùng join point (ví dụ audit và transaction) là hành vi hợp lệ; không nên coi mọi trường hợp có nhiều advice là lỗi.

Bài tập trung vào **Spring AOP được quản lý bởi Spring ApplicationContext**:

- Cách nạp bean XML từ Java configuration bằng `@ImportResource`.
- Cách khai báo aspect theo XML schema (`<aop:config>`/`<aop:aspect>`) song song với aspect theo `@Aspect`.
- Quy tắc phân công trách nhiệm, tên bean, pointcut và auto-proxy để tránh gọi advice hai lần.
- Cách viết kiểm thử **số lần advice chạy**, không chỉ kiểm tra business result.
- Các ranh giới Spring proxy, context hierarchy, thứ tự advisor và quy trình chuyển dần cấu hình XML sang Java.

Kiến thức AOP tổng quát và AspectJ weaving độc lập không phải phạm vi của bài này.

## 2. Phân biệt ba việc dễ bị nhầm

| Việc được cấu hình | Ví dụ | Hành vi |
| --- | --- | --- |
| **Đăng ký bean** | `<bean id="paymentService" .../>` hoặc `@Bean paymentService()` | Đưa đối tượng nghiệp vụ/advice vào Spring container; chưa tự nói bean nào bị AOP. |
| **Khai báo advisor/aspect** | `<aop:aspect ref="xmlAuditAdvice">` hay `@Aspect` + `@Before` | Quy định method nào sẽ được intercept và logic nào chạy ở đó. |
| **Bật auto-proxying** | `<aop:config>`, `<aop:aspectj-autoproxy/>`, `@EnableAspectJAutoProxy` | Cho Spring tạo proxy và gắn advisor đủ điều kiện lên bean. |

Các cơ chế auto-proxy tiêu chuẩn được Spring tích hợp/thống nhất. Vì vậy, việc trong cùng context có `<aop:config>` và `@EnableAspectJAutoProxy` **không tự chứng minh** mỗi advice được chạy hai lần. Tuy nhiên, chúng có thể cùng lấy được hai **advisor mang cùng hành vi**, và khi đó method bị xử lý trùng.

Một nguồn trùng lặp rõ ràng:

~~~java
@Aspect
public class SharedAuditAspect {
    @Before("execution(* demo.PaymentService.process(..))")
    public void beforeProcess() {
        // ghi một audit event
    }
}
~~~

~~~xml
<bean id="sharedAuditAspect" class="demo.SharedAuditAspect"/>

<aop:config>
    <aop:aspect ref="sharedAuditAspect">
        <aop:before method="beforeProcess"
                    pointcut="execution(* demo.PaymentService.process(..))"/>
    </aop:aspect>
</aop:config>
~~~

Nếu `@Aspect` đã được Spring auto-detect và cũng có một khai báo XML `aop:aspect` tham chiếu đúng advice đó, **một phương thức advice được khai báo theo hai đường**. Đây là cấu hình cần tránh. Các fragment này chỉ mô tả một cấu hình sai, **không phải** phần của ví dụ runnable ở dưới.

### Quy tắc phân quyền cấu hình

1. **Một bean chỉ có một chủ sở hữu đăng ký** trong một application context: chọn XML `<bean>` hoặc Java `@Bean`/component scan; không tự tạo hai tên khác nhau cho cùng một logic.
2. **Một trách nhiệm cắt ngang chỉ có một chủ sở hữu advice**: nếu XML đã audit `process`, đừng thêm một Java aspect khác audit chính `process` cùng sự kiện. Nếu đang migration, bỏ khai báo cũ trong cùng bước chuyển sang khai báo mới.
3. **Hai concern độc lập có thể cùng dùng Spring AOP**: một XML aspect audit method `process`, một Java aspect ghi sự kiện vận hành cho method `cancel`; mỗi bên có pointcut rõ và test riêng.
4. **Một điểm bật @AspectJ auto-proxy rõ ràng** cho các aspect viết bằng annotation; không đăng ký lặp bean aspect. Nếu `<aop:config>` cũng hiện diện để khai báo XML advice, cơ chế đó sẽ được Spring phối hợp.

## 3. Sơ đồ kết hợp XML và Java configuration

Ví dụ sẽ tạo đúng **một Spring ApplicationContext**:

~~~text
AnnotationConfigApplicationContext(AppConfig.class)
                   |
                   +--> AppConfig.java
                   |      @Configuration
                   |      @ImportResource("classpath:spring/legacy-aop.xml")
                   |      @EnableAspectJAutoProxy(proxyTargetClass = true)
                   |      @Bean EventRecorder
                   |      @Bean JavaCancelAspect (@Aspect)
                   |
                   +--> legacy-aop.xml
                          <bean id="paymentService" ...>
                          <bean id="xmlAuditAdvice" ...>
                          <aop:config>
                             <aop:pointcut ...process(..)>
                             <aop:aspect ref="xmlAuditAdvice">
                                <aop:before method="beforeProcess" ...>
                             </aop:aspect>
                          </aop:config>
                   |
                   +--> Spring proxy for PaymentService
                          process("A") --> XML audit exactly once
                          cancel("B")  --> Java @Aspect exactly once
~~~

Ở ví dụ này:

- XML sở hữu bean nghiệp vụ `paymentService` và bean advice `xmlAuditAdvice`.
- Java configuration sở hữu `eventRecorder` và `javaCancelAspect`.
- XML audit và Java aspect có **hai trách nhiệm/pointcut riêng**.
- Cả hai được thử qua **cùng Spring proxy** để xác minh không có sự kiện trùng.

Có thể để hai aspect áp dụng lên *cùng* method nếu thực hiện hai concern khác nhau, nhưng cần xác định thứ tự và kiểm thử từng sự kiện. Ví dụ chọn hai method để quan sát rành mạch và tránh ngộ nhận.

## 4. Ví dụ Maven + Spring Framework 6.x + Java 17 chạy được

Ứng dụng mẫu không dùng Spring Boot và không có HTTP server, nhằm quan sát chính xác Spring AOP proxy. Maven, JDK 17+ và dependency truy cập được từ Maven repository là đủ. Với Spring Boot 3.x, cách đăng ký bean, `@ImportResource` và nguyên tắc chống trùng vẫn tương tự; dependency và auto-configuration phải theo BOM của Boot đang dùng.

### 4.1. Cấu trúc

~~~text
mixed-spring-aop/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/demo/
    │   │   ├── AppConfig.java
    │   │   ├── EventRecorder.java
    │   │   ├── PaymentService.java
    │   │   ├── XmlAuditAdvice.java
    │   │   ├── JavaCancelAspect.java
    │   │   └── Main.java
    │   └── resources/spring/legacy-aop.xml
    └── test/java/demo/MixedAopConfigurationTest.java
~~~

### 4.2. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>mixed-spring-aop</artifactId>
    <version>1.0-SNAPSHOT</version>

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
                <version>3.5.0</version>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-context` cho phép dùng Java configuration và load XML; `spring-aop` cung cấp API proxy/AOP; `aspectjweaver` phục vụ cách biểu diễn pointcut và các annotation AspectJ mà Spring AOP phân tích. Đây là **Spring AOP proxy**, không phải chương trình dùng AspectJ load-time weaving. Các phiên bản chỉ là bộ dependency minh họa cố định, không phải khuyến nghị phiên bản mới nhất cho mọi dự án.

### 4.3. `src/main/java/demo/PaymentService.java`

~~~java
package demo;

public class PaymentService {
    public String process(String orderId) {
        return "processed:" + orderId;
    }

    public String cancel(String orderId) {
        return "cancelled:" + orderId;
    }
}
~~~

Class không `final`, method public và không `final` để Spring có thể dùng class-based proxy. Cả hai method đều không có code audit; đây là concern nằm ngoài service.

### 4.4. `src/main/java/demo/EventRecorder.java`

~~~java
package demo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventRecorder {
    private final List<String> events = new CopyOnWriteArrayList<>();

    public void add(String event) {
        events.add(event);
    }

    public List<String> snapshot() {
        return List.copyOf(events);
    }
}
~~~

Recorder là bean dùng chung để test quan sát **số lần advice chạy**. `CopyOnWriteArrayList` giúp an toàn với các lời gọi đồng thời trong ví dụ nhỏ; hệ thống production thường dùng logging, meter registry hoặc audit sink thực tế. Không dựa vào chuỗi log của framework để đếm vì logging có thể được cấu hình khác nhau.

### 4.5. `src/main/java/demo/XmlAuditAdvice.java`

~~~java
package demo;

import org.aspectj.lang.JoinPoint;

public class XmlAuditAdvice {
    private final EventRecorder recorder;

    public XmlAuditAdvice(EventRecorder recorder) {
        this.recorder = recorder;
    }

    public void beforeProcess(JoinPoint joinPoint) {
        recorder.add("xml:process:" + joinPoint.getArgs()[0]);
    }
}
~~~

`XmlAuditAdvice` là class Java bình thường **không gắn `@Aspect` hay `@Before`**. Spring sẽ biết đây là advice vì khai báo `<aop:before>` trong XML. Chỉ có một bean `xmlAuditAdvice` được đăng ký.

### 4.6. `src/main/java/demo/JavaCancelAspect.java`

~~~java
package demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
public class JavaCancelAspect {
    private final EventRecorder recorder;

    public JavaCancelAspect(EventRecorder recorder) {
        this.recorder = recorder;
    }

    @Before("execution(public * demo.PaymentService.cancel(..))")
    public void beforeCancel(JoinPoint joinPoint) {
        recorder.add("java:cancel:" + joinPoint.getArgs()[0]);
    }
}
~~~

Đây là **annotation-style aspect**. Trong ví dụ này không đánh dấu class bằng `@Component`, vì `@Bean` ở `AppConfig` sẽ đăng ký nó chính xác một lần. Pointcut chỉ áp dụng cho `cancel` nên không chồng lấn nghiệp vụ audit XML của `process`.

### 4.7. `src/main/resources/spring/legacy-aop.xml`

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

    <bean id="paymentService" class="demo.PaymentService"/>

    <bean id="xmlAuditAdvice" class="demo.XmlAuditAdvice">
        <constructor-arg ref="eventRecorder"/>
    </bean>

    <aop:config proxy-target-class="true">
        <aop:pointcut id="processOperation"
                      expression="execution(public * demo.PaymentService.process(..))"/>

        <aop:aspect id="xmlAuditAspect" ref="xmlAuditAdvice">
            <aop:before method="beforeProcess"
                        pointcut-ref="processOperation"/>
        </aop:aspect>
    </aop:config>
</beans>
~~~

`eventRecorder` chưa khai báo trong XML vì do Java `@Bean` quản lý trong **cùng context**. Spring giải quyết tham chiếu `ref="eventRecorder"` khi khởi tạo bean advice. `proxy-target-class="true"` buộc chọn class-based proxy cho target, nhất quán với Java config.

Lưu ý: **không có** `<aop:aspectj-autoproxy/>` ở file này. Java configuration ở dưới sẽ bật @AspectJ auto-proxying; `<aop:config>` vẫn cung cấp advisor schema-style. Việc bỏ declaration lặp ở đây nhằm cấu hình rõ trách nhiệm, chứ không vì có hai tag bật auto-proxy chắc chắn tạo hai lần advice.

### 4.8. `src/main/java/demo/AppConfig.java`

~~~java
package demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.ImportResource;

@Configuration
@ImportResource("classpath:spring/legacy-aop.xml")
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AppConfig {
    @Bean
    public EventRecorder eventRecorder() {
        return new EventRecorder();
    }

    @Bean
    public JavaCancelAspect javaCancelAspect(EventRecorder recorder) {
        return new JavaCancelAspect(recorder);
    }
}
~~~

Cơ chế liên kết:

- `@ImportResource` nạp định nghĩa bean và advisor trong file XML vào context đang khởi tạo từ `AppConfig`.
- `@Bean` tạo một `EventRecorder` cho cả hai advice.
- `@Bean` đăng ký đúng một instance `JavaCancelAspect`, có metadata `@Aspect` được Spring hỗ trợ khi `@EnableAspectJAutoProxy` đang bật.
- XML `<aop:config>` định nghĩa advisor cho `process`; Java `@Aspect` định nghĩa advisor cho `cancel`. Mỗi đường khai báo chạy một chức năng riêng.
- Hai nơi cùng chỉ định class-based proxy. Trong một Spring application context, hạ tầng auto-proxy tiêu chuẩn được thống nhất và cấu hình proxy mạnh hơn có thể ảnh hưởng mọi advice cùng dùng hạ tầng đó.

### 4.9. `src/main/java/demo/Main.java`

~~~java
package demo;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            EventRecorder events = context.getBean(EventRecorder.class);

            System.out.println("proxy:" + AopUtils.isAopProxy(service));
            System.out.println(service.process("A"));
            System.out.println(service.cancel("B"));
            System.out.println("events:" + events.snapshot());
        }
    }
}
~~~

Chạy tại thư mục `mixed-spring-aop`:

~~~bash
mvn -q compile exec:java -Dexec.mainClass=demo.Main
~~~

**Các dòng do mã Java chủ động in ra**:

~~~text
proxy:true
processed:A
cancelled:B
events:[xml:process:A, java:cancel:B]
~~~

Các log thư viện/Maven (nếu có) phụ thuộc cấu hình riêng của môi trường và không nằm trong stdout minh họa. Quan trọng nhất là mảng events chứa **chính xác hai sự kiện theo hai concern**, không phải bốn sự kiện do advice vô tình được đăng ký hai lần.

## 5. Kiểm thử: xác nhận mỗi advice được thực thi đúng một lần

Tạo `src/test/java/demo/MixedAopConfigurationTest.java`:

~~~java
package demo;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedAopConfigurationTest {

    @Test
    void xmlAndJavaAspectsCoexistWithoutDuplicateAdvice() {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            PaymentService service = context.getBean(PaymentService.class);
            EventRecorder recorder = context.getBean(EventRecorder.class);

            // Test có gọi qua Spring AOP proxy thật.
            assertTrue(AopUtils.isAopProxy(service));
            assertTrue(AopUtils.isCglibProxy(service));

            assertEquals("processed:A", service.process("A"));
            assertEquals("cancelled:B", service.cancel("B"));

            // Đếm sự kiện, không chỉ assert business result.
            assertEquals(List.of("xml:process:A", "java:cancel:B"),
                         recorder.snapshot());

            // Gọi lặp để chắc chắn tỷ lệ 1 lời gọi : 1 advice.
            assertEquals("processed:C", service.process("C"));
            assertEquals("cancelled:D", service.cancel("D"));
            assertEquals(
                    List.of("xml:process:A", "java:cancel:B",
                            "xml:process:C", "java:cancel:D"),
                    recorder.snapshot());
        }
    }

    @Test
    void everyAspectBeanIsRegisteredExactlyOnce() {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            assertArrayEquals(new String[] {"javaCancelAspect"},
                    context.getBeanNamesForType(JavaCancelAspect.class));
            assertArrayEquals(new String[] {"xmlAuditAdvice"},
                    context.getBeanNamesForType(XmlAuditAdvice.class));
        }
    }
}
~~~

Chạy:

~~~bash
mvn -q test
~~~

Kỳ vọng **2 bài test pass** khi cấu hình được nạp và Spring AOP được áp dụng đúng. Assertion kiểm tra số lần thực thi advice và số lượng bean sẽ phát hiện nhiều lỗi khác nhau:

- Một concern có hai advisor → event bị lặp mặc dù service chỉ chạy một lần.
- Aspect Java bị đăng ký qua `@Bean` và `@Component` ở hai đường → tên/số lượng bean hoặc event có thể khác kỳ vọng.
- XML chưa được nạp hoặc pointcut sai → thiếu sự kiện `xml:process:A`.
- `@EnableAspectJAutoProxy` thiếu hoặc Java aspect chưa được đăng ký → thiếu `java:cancel:B`.
- Caller lấy target trực tiếp, không qua proxy → `AopUtils.isAopProxy` thất bại và không có advice event.

Đây là **test tích hợp nhỏ cho configuration + proxy**, không chỉ unit test của class recorder. Nếu chuyển từ Spring XML sang Java, giữ các assertion này trong quá trình migration để biết ngay khi xóa thiếu hoặc giữ thừa một khai báo.

## 6. Những cấu hình hay gây trùng lặp

### 6.1. Một chức năng được định nghĩa đồng thời trong XML và `@Aspect`

Ví dụ XML cũ vẫn khai báo:

~~~xml
<aop:aspect ref="xmlAuditAdvice">
    <aop:before method="beforeProcess"
                pointcut="execution(* demo.PaymentService.process(..))"/>
</aop:aspect>
~~~

Sau đó có người thêm Java aspect mới cũng audit `process`:

~~~java
@Aspect
class JavaAuditAspect {
    @Before("execution(* demo.PaymentService.process(..))")
    public void beforeProcess() {
        // Ghi cùng một loại audit event với XML advice.
    }
}
~~~

Nếu bean `JavaAuditAspect` được đăng ký và auto-proxy tìm thấy, **hai advice cùng chức năng** sẽ chạy cho một lời gọi, trừ khi cấu hình khác chặn một đường. Cách sửa: xác định **một owner của audit**; xóa definition XML cũ khi Java aspect đã thay thế hoàn toàn, hoặc chỉ dùng XML đến lúc migration xong. Nếu thực sự là hai concern độc lập, đặt tên event, scope và test riêng để làm rõ hành vi mong muốn.

### 6.2. Một `@Aspect` được đăng ký thành nhiều Spring bean

~~~java
@Aspect
@Component
class DuplicateCandidateAspect {
    // advice ...
}
~~~

~~~java
@Configuration
@ComponentScan("demo")
class Config {
    @Bean
    DuplicateCandidateAspect duplicateCandidateAspect() {
        return new DuplicateCandidateAspect();
    }
}
~~~

Đây là **ví dụ sai**, không thuộc demo runnable. Nếu component scanning nhận `@Component`, còn method `@Bean` đăng ký một bean **khác tên**, Spring có thể có nhiều aspect instance của cùng class. Khi advisor được tạo cho cả hai, một join point có thể được advice hai lần. Nếu trùng **cùng tên bean**, hành vi có thể là lỗi cấu hình/bean overriding tùy chế độ container chứ không nên mặc định coi Spring đã âm thầm bỏ bản trùng.

Chọn đúng một trong hai: `@Component` + scan **hoặc** `@Bean` tường minh. Đừng cố sửa duplication bằng `@Primary`; annotation đó giúp chọn candidate khi inject một bean, không mặc nhiên xóa các advisor sinh ra từ aspect khác.

### 6.3. Đăng ký aspect bằng Java và tham chiếu lại như schema-style XML aspect

Nếu một bean đã mang `@Aspect` và các `@Before`/`@Around`, rồi XML tiếp tục có `<aop:aspect ref="sameBean">` khai báo chính method ấy, ứng dụng có **hai nguồn advice metadata** cho cùng logic. Khi đó nên chọn một style làm chủ concern. Để chuyển tiếp an toàn, có thể giữ bean Java chứa logic chung dưới dạng collaborator, và chỉ có **một** XML advice **hoặc** một `@Aspect` wrapper làm owner; không khai báo hai wrapper cùng pointcut khi chưa có chủ đích rõ ràng.

### 6.4. Khai báo auto-proxy nhiều lần

Các tag `<aop:config>`, `<aop:aspectj-autoproxy/>`, `<tx:annotation-driven/>` và các annotation tương ứng như `@EnableAspectJAutoProxy`/`@EnableTransactionManagement` tham gia cấu hình proxy của Spring. Theo tài liệu Spring, các thiết lập tiêu chuẩn được thống nhất; nếu một cấu hình yêu cầu `proxy-target-class="true"` thì lựa chọn class-based proxy có thể có hiệu lực rộng hơn bạn nghĩ.

Vì vậy:

- **Không suy rằng có 2 tag bật proxy ⇒ advice chạy 2 lần.** Điều cần đếm là advisor và event thực tế.
- Không thêm các auto-proxy creator độc lập như `BeanNameAutoProxyCreator`/`DefaultAdvisorAutoProxyCreator` vào context đã có `<aop:config>` nếu không có thiết kế rõ. Tài liệu Spring cảnh báo việc trộn creator trực tiếp với schema AOP có thể khiến advice bị bỏ qua hoặc cấu hình proxy không như dự kiến.
- Với ứng dụng lớn, thống nhất chính sách JDK proxy so với CGLIB và kiểm thử cả method trong interface lẫn implementation.

### 6.5. Trùng do nạp file XML hoặc quét class ở nhiều đường

Ví dụ `@ImportResource("classpath:spring/legacy-aop.xml")` đã load tệp, nhưng một `<import resource="legacy-aop.xml"/>` khác cũng load cùng cấu hình, hoặc module bootstrapping tạo hai context rồi cả hai cùng được dùng xử lý. Kết quả tùy cách bean definition được đặt tên, override, bean factory và mối quan hệ context.

Không nên đoán kết quả từ việc “hai lần import”. Hãy kiểm tra **danh sách bean và advisor ở context thật**, các nguồn load, tên bean/alias và việc target nhận bao nhiêu proxy/advice; dùng test đếm event để nhận diện hậu quả thực tế.

## 7. Khi XML và Java cần áp dụng trên cùng một method

Hai advice khác trách nhiệm trên cùng method là một cấu hình hợp lệ, ví dụ:

~~~text
process()
  -> Java security advice: kiểm tra quyền
  -> XML audit advice: ghi sự kiện
  -> target PaymentService.process()
~~~

Đây là **hai hành vi khác nhau**, không phải “audit hai lần”. Vấn đề phải giải quyết là thứ tự và kết quả:

- Thứ tự advice khi vào/ra phụ thuộc precedence của advisor/aspect, ví dụ `@Order` trên Java aspect hoặc `order` của `<aop:aspect>`. Một advice ưu tiên cao chạy **trước** trên đường vào và **sau** trên đường ra nếu bao quanh invocation.
- Nếu security chặn ngay từ đầu, audit nằm bên trong có thể không chạy. Hãy xác định audit nghiệp vụ cần ghi cả request bị từ chối hay chỉ method được chấp thuận rồi sắp xếp order tương ứng.
- Khi Java transaction advice và XML audit advice cùng áp dụng, cần xác định audit nên nằm ngoài hay trong transaction, được gọi khi rollback hay không.
- Viết test theo hành vi: với một `process("A")`, assert có đúng một `security:checked:A` và đúng một `audit:process:A`; nếu cần thứ tự thì assert danh sách event có thứ tự, thay vì chỉ kiểm tra số lượng.

Ví dụ khai báo order minh họa:

~~~xml
<aop:aspect id="auditAspect" ref="xmlAuditAdvice" order="20">
    <aop:before method="beforeProcess"
                pointcut="execution(* demo.PaymentService.process(..))"/>
</aop:aspect>
~~~

~~~java
@Aspect
@Order(10)
class SecurityAspect {
    @Before("execution(* demo.PaymentService.process(..))")
    public void checkAccess() {
        // kiểm tra quyền của caller
    }
}
~~~

Hai đoạn **chưa phải class/file chạy độc lập**. Với `@Order(10)` và XML `order="20"`, Java advice có ưu tiên cao hơn. Nếu muốn phụ thuộc thứ tự chính xác giữa nhiều advisor trong hệ thống đang dùng transaction/security, kiểm thử context hoàn chỉnh thay vì tin vào thứ tự khai báo file.

## 8. Kiểm chứng khi chuyển từ XML sang Java

Để chuyển **một concern** từ XML sang Java, trước tiên ghi lại các advisor XML, pointcut, bean IDs, `order` và tập method phải match/không match. Viết integration test đếm chính advice đó, gồm success và exception path; chuẩn bị class `@Aspect` mới nhưng chưa đăng ký trùng vào production context.

Khi chuyển, **bỏ cùng một lần** XML `<aop:aspect>`/`aop:advisor` cũ của concern ấy và đăng ký đúng một bean `@Aspect` mới. Giữ `@ImportResource` và các XML bean definitions khác nếu chưa chuyển. Test lại số event mỗi invocation và thứ tự advisor; nếu không còn khai báo schema-based AOP thì mới xem xét bỏ `<aop:config>`.

Bài **“Chuyển đổi cấu hình XML sang annotation và kiểm tra tương đương”** giữ vai trò hướng dẫn migration chi tiết: bảng ánh xạ XML → annotations, chương trình hai context độc lập và đối chiếu proxy/return/exception/events. Bài hiện tại tập trung vào **cách cùng tồn tại an toàn trong một context** trước và trong lúc migration.

## 9. Cách chẩn đoán số lần advice chạy sai

### Kiểm tra bean và proxy

Trong test, cần kiểm tra đúng context/bean:

~~~java
PaymentService service = context.getBean(PaymentService.class);
boolean proxied = AopUtils.isAopProxy(service);
String[] javaAspectNames = context.getBeanNamesForType(JavaCancelAspect.class);
String[] xmlAdviceNames = context.getBeanNamesForType(XmlAuditAdvice.class);
~~~

Đây là fragment chạy **bên trong test có biến `context`**. Với code ở mục 5, cả hai danh sách phải có đúng một phần tử tên theo cấu hình. Nếu aspect được tạo ở parent và child context, hãy kiểm tra từng context riêng và xem service thực sự là bean của context nào.

Có thể inspect advisor của proxy:

~~~java
if (service instanceof org.springframework.aop.framework.Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor);
    }
}
~~~

Đây là **đoạn chẩn đoán**, không phải stdout cố định; tên/lớp advisor có thể khác theo phiên bản Spring và các starter đang bật. Đừng assert số lượng advisor bằng một hằng số tùy tiện: infrastructure có thể bổ sung các interceptor mà bạn không tự khai báo. Thay vào đó, assert số lượng **event nghiệp vụ** và từng loại event.

### Ma trận lỗi và cách xử lý

| Hiện tượng | Điều kiểm tra đầu tiên | Hướng khắc phục |
| --- | --- | --- |
| Một lần `process` ghi hai audit event | Có hai advisor match cùng pointcut và cùng audit logic? | Chọn một owner, bỏ khai báo audit trùng. |
| Aspect Java chạy hai lần | `@Component` và `@Bean` cùng tạo aspect bean? | Đăng ký duy nhất một instance. |
| XML advice không chạy | `@ImportResource` đúng path, `<aop:config>`/`pointcut-ref` đúng, bean được gọi qua proxy? | Đọc danh sách advisor và kiểm tra pointcut. |
| Java `@Aspect` không chạy | Có bật `@EnableAspectJAutoProxy` và aspect bean được đăng ký? | Bổ sung activation/bean đúng nơi. |
| Bean class-cast lỗi | Proxy là JDK interface-based hay class-based? | Inject theo interface hoặc cấu hình proxy hợp lệ. |
| Bản ghi audit nhiều nhưng advice chỉ chạy một lần | Recorder/logger có nhiều appender, target cũng tự ghi audit? | Tách event tại advice với side effect bên ngoài. |
| Một test xanh dù advice không chạy | Test chỉ assert kết quả của service? | Assert cả proxy và event counter. |
| Advice order không như kỳ vọng | `@Order` và XML `order` có nhất quán? | Thêm kiểm thử event trước/sau. |
| Method nội bộ không bị intercept | Self-invocation gọi `this.otherMethod()` | Gọi qua bean/proxy đúng thiết kế Spring. |

### Lưu ý Spring Boot

Spring Boot có thể auto-configure AOP và chọn class-based proxy theo property/phiên bản. Nếu dự án Boot đồng thời dùng `spring-boot-starter-aop`, `@EnableAspectJAutoProxy` và XML `<aop:config>`, hãy xác minh bean/advisor thực tế thay vì suy rằng cứ có nhiều activation là có nhiều proxy. Với `@ImportResource`, cân nhắc bean definition overriding: hai bean cùng tên có thể bị chặn bởi cấu hình Boot hoặc có hành vi ghi đè nếu cho phép; **không dùng overriding để giải quyết ambiguity**.

Spring AOP vẫn chỉ intercept các execution đi qua Spring proxy. Tạo mới target bằng `new` hoặc self-invocation không đi qua proxy có thể làm event thiếu; đây không phải dấu hiệu có hai hay một cấu hình.

## 10. Checklist đưa vào dự án

- [ ] Chỉ một `ApplicationContext` sở hữu những bean/advisor cần kết hợp; quan hệ parent/child được xác định rõ.
- [ ] Mỗi bean AOP có một đường đăng ký (`<bean>` hoặc `@Bean`/component scan); tên/alias rõ ràng.
- [ ] Với mỗi concern, xác định một nguồn khai báo pointcut/advice chính (XML schema hoặc `@Aspect`).
- [ ] `@ImportResource` nạp đúng file XML; XML có namespace `aop` đúng và không import lặp ngoài chủ đích.
- [ ] `@EnableAspectJAutoProxy` (hoặc một cách activation tương đương) sẵn sàng khi có `@Aspect`; `<aop:config>` sử dụng đúng khi còn XML advice.
- [ ] Chọn thống nhất chiến lược proxy và kiểm tra giới hạn interface/final/self-invocation.
- [ ] Nếu nhiều concern cùng khớp một method, ghi rõ trách nhiệm và order, có test tương ứng.
- [ ] Test có cả `AopUtils.isAopProxy`, đếm bean aspect, đếm **advice invocation** theo từng method, positive/negative path.
- [ ] Khi migration một concern, xóa khai báo advice cũ **cùng lúc** đăng ký advice mới và chạy lại test.

**Tiêu chí hoàn thành:** XML bean và Java configuration nạp trong cùng Spring context; đúng proxy được tạo; mỗi lời gọi `process` sinh **một XML audit event**, mỗi lời gọi `cancel` sinh **một Java advice event**; không có bean/aspect/advisor cùng trách nhiệm được đăng ký ngoài chủ đích.

## Tài liệu tham khảo chính thức

- Spring Framework — Mixing Aspect Types: https://docs.spring.io/spring-framework/reference/core/aop/mixing-styles.html
- Spring Framework — Schema-based AOP Support: https://docs.spring.io/spring-framework/reference/core/aop/schema.html
- Spring Framework — Enabling @AspectJ Support: https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/aspectj-support.html
- Spring Framework — Proxying Mechanisms: https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
- Spring Framework — ImportResource: https://docs.spring.io/spring-framework/docs/6.2.6/javadoc-api/org/springframework/context/annotation/ImportResource.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="xml-aop-configuration-011">Chuyển đổi cấu hình XML sang annotation và kiểm tra tương đương</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển cấu hình Spring AOP từ XML sang annotation và kiểm tra tương đương

Trong ứng dụng Spring Framework, cấu hình AOP bằng XML và cấu hình aspect qua annotation đều có thể tạo **Spring AOP proxy** để chạy advice quanh các method của Spring bean. Khi chuyển đổi, mục tiêu không chỉ là xóa XML: cần **giữ nguyên bean được áp dụng, biểu thức pointcut, kiểu advice, thứ tự thực thi, loại proxy, kết quả trả về và hành vi khi phát sinh exception**.

Có hai tình huống XML khác nhau, nên xác định đúng trường hợp trước khi sửa:

1. **XML định nghĩa cả pointcut và advice**, chẳng hạn `<aop:config>`, `<aop:aspect ref="...">`, `<aop:around>`: chuyển định nghĩa đó sang class Java có `@Aspect`, `@Pointcut`, `@Around`... và kích hoạt proxy bằng `@EnableAspectJAutoProxy`.
2. **XML đã dùng aspect annotation** (class có `@Aspect`, XML có `<aop:aspectj-autoproxy/>`): định nghĩa advice đã ở Java; chỉ cần chuyển cách **đăng ký bean và bật auto-proxy** sang Java configuration, không viết lại advice chỉ để thay cú pháp XML.

Bài học tập trung vào **Spring Aspect/Spring AOP**: các ví dụ dùng phương thức Spring bean thông qua proxy, không thực hiện compile-time hoặc load-time weaving của AspectJ.

## 1. Bảng ánh xạ XML → annotation/Java configuration

| Cấu hình XML | Cấu hình Java tương ứng | Cần bảo toàn |
| --- | --- | --- |
| `<aop:config>` (khai báo AOP theo schema) | `@Aspect` trên class advice và `@EnableAspectJAutoProxy` trong `@Configuration` | Bean nào khớp, phương thức nào được intercept |
| `<aop:aspect ref="traceAdvice">` | Spring bean có class `@Aspect`; tạo bằng `@Bean` hoặc `@Component` | Cùng lifecycle và dependencies của advice |
| `<aop:pointcut id="pc" expression="..."/>` | `@Pointcut("...")` trên method không có body, hoặc biểu thức trực tiếp trong advice | Giữ nguyên biểu thức, các điều kiện runtime |
| `<aop:before method="before" pointcut-ref="pc"/>` | `@Before("pc()")` | Thời điểm chạy trước method |
| `<aop:after method="after" pointcut-ref="pc"/>` | `@After("pc()")` | Chạy sau khi method kết thúc, bao gồm cả lỗi |
| `<aop:after-returning method="success" returning="result" .../>` | `@AfterReturning(pointcut="pc()", returning="result")` | Chỉ nhánh trả về bình thường, cách bind kết quả |
| `<aop:after-throwing method="failure" throwing="error" .../>` | `@AfterThrowing(pointcut="pc()", throwing="error")` | Chỉ nhánh ném exception, loại exception bind |
| `<aop:around method="around" pointcut-ref="pc"/>` | `@Around("pc()")` trên method nhận `ProceedingJoinPoint` | Số lần `proceed()`, return value, lỗi được ném lại |
| `<aop:aspect order="10" ...>` | `@Order(10)` trên class `@Aspect` hoặc `Ordered` | Thứ tự ưu tiên giữa các aspect; test thứ tự thật |
| `proxy-target-class="true/false"` ở `<aop:config>` hoặc `<aop:aspectj-autoproxy>` | `@EnableAspectJAutoProxy(proxyTargetClass = true/false)` | Loại proxy JDK hay class-based, kiểu bean có thể inject |
| `expose-proxy="true"` | `@EnableAspectJAutoProxy(exposeProxy = true)` | Khả năng dùng `AopContext.currentProxy()` nếu ứng dụng đang phụ thuộc vào nó |
| `<bean .../>` hoặc `<context:component-scan .../>` | `@Bean` hoặc `@ComponentScan` với `@Component`/`@Service` | Bean target và bean aspect đều được đăng ký đúng context |

**Lưu ý:** các đoạn `@Before("pc()")` trong bảng giả định có một method `@Pointcut` tên `pc` cùng aspect. Nếu thay bằng biểu thức pointcut đầy đủ trong annotation thì không cần method đó. Cú pháp XML advice có thể khai báo `pointcut="..."` trực tiếp thay cho `pointcut-ref`; tương tự, annotation cũng cho phép biểu thức trực tiếp.

**Không tự động coi mọi điểm là một-một.** Thứ tự giữa nhiều advice cùng một aspect có thể khác khi đổi kiểu khai báo hoặc khi thêm advice mới; thông số proxy có thể bị ảnh hưởng bởi các cấu hình Spring khác trong cùng context. Vì thế phải kiểm tra hành vi chạy thực tế.

## 2. Ví dụ trước và sau có thể chạy để đối chiếu

Ta tạo **hai application context riêng biệt** với cùng interface/target, dùng Spring Framework 6, Java 17, Maven. Context thứ nhất định nghĩa advice trong XML `<aop:config>`. Context thứ hai định nghĩa advice bằng `@Aspect` và bật bằng `@EnableAspectJAutoProxy`. Mỗi context có một `TraceLog` độc lập để tránh kết quả thử nghiệm ảnh hưởng lẫn nhau.

Ca sử dụng có hai nhánh:

- `placeOrder("A-7")` trả về `"OK:A-7"` và ghi sự kiện vào audit.
- `placeOrder("bad")` ném `IllegalArgumentException`; advice ghi sự kiện lỗi và **ném lại nguyên exception**.

Bài kiểm tra so sánh cả kết quả, loại proxy, thứ tự sự kiện và exception giữa hai context. Việc dùng một `@Around` advice ở mỗi bên khiến luồng kiểm tra dễ quan sát.

### 2.1. Cấu trúc dự án Maven

```text
spring-aop-xml-migration/
├── pom.xml
└── src/main/
    ├── java/com/example/aopmigration/
    │   ├── OrderService.java
    │   ├── OrderServiceImpl.java
    │   ├── TraceLog.java
    │   ├── XmlTraceAdvice.java
    │   ├── AnnotatedTraceAspect.java
    │   ├── JavaAopConfig.java
    │   └── MigrationCheck.java
    └── resources/
        └── legacy-aop.xml
```

**`pom.xml`:** Maven và JDK 17 trở lên; Spring 6 tương thích Java 17. `aspectjweaver` cần cho các type AspectJ và khả năng phân tích pointcut mà Spring AOP sử dụng.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>spring-aop-xml-migration</artifactId>
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
            </plugin>
        </plugins>
    </build>
</project>
```

Trong dự án Spring Boot đã có dependency management, thường dùng `spring-boot-starter-aop` và để Boot quản lý version thay vì sao chép toàn bộ cấu hình Maven trên.

### 2.2. Target bean dùng chung

**`OrderService.java`**

```java
package com.example.aopmigration;

public interface OrderService {
    String placeOrder(String orderId);
}
```

**`OrderServiceImpl.java`**

```java
package com.example.aopmigration;

public class OrderServiceImpl implements OrderService {
    @Override
    public String placeOrder(String orderId) {
        if ("bad".equals(orderId)) {
            throw new IllegalArgumentException("invalid order");
        }
        return "OK:" + orderId;
    }
}
```

**`TraceLog.java`**

```java
package com.example.aopmigration;

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

Ví dụ chạy tuần tự trên một thread, nên `ArrayList` đủ để ghi nhận event; trong ứng dụng đa luồng, cần dùng recorder/logger có tính an toàn luồng phù hợp. Hai context tạo hai `TraceLog` khác nhau.

### 2.3. Trước: advice và pointcut khai báo bằng XML

**`XmlTraceAdvice.java`** là một POJO: chưa có `@Aspect` vì XML sẽ định nghĩa advice nào chạy.

```java
package com.example.aopmigration;

import org.aspectj.lang.ProceedingJoinPoint;

public class XmlTraceAdvice {
    private final TraceLog log;

    public XmlTraceAdvice(TraceLog log) {
        this.log = log;
    }

    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getName();
        log.add("enter:" + method);

        try {
            Object result = joinPoint.proceed();
            log.add("ok:" + result);
            return result;
        } catch (Throwable failure) {
            log.add("fail:" + failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
```

**`src/main/resources/legacy-aop.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:aop="http://www.springframework.org/schema/aop"
       xsi:schemaLocation="
           http://www.springframework.org/schema/beans
           https://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.springframework.org/schema/aop
           https://www.springframework.org/schema/aop/spring-aop.xsd">

    <bean id="traceLog" class="com.example.aopmigration.TraceLog"/>
    <bean id="orderService" class="com.example.aopmigration.OrderServiceImpl"/>

    <bean id="xmlTraceAdvice" class="com.example.aopmigration.XmlTraceAdvice">
        <constructor-arg ref="traceLog"/>
    </bean>

    <aop:config proxy-target-class="false">
        <aop:pointcut id="orderOperation"
            expression="execution(* com.example.aopmigration.OrderServiceImpl.placeOrder(..))"/>
        <aop:aspect ref="xmlTraceAdvice">
            <aop:around pointcut-ref="orderOperation" method="around"/>
        </aop:aspect>
    </aop:config>
</beans>
```

Ở đây `<aop:config>` kết nối method `XmlTraceAdvice.around(...)` với pointcut. Bean target `orderService` sẽ được Spring cung cấp dưới dạng proxy vì pointcut khớp. Do target có interface `OrderService` và XML đặt `proxy-target-class="false"`, Spring có thể tạo JDK dynamic proxy.

### 2.4. Sau: aspect annotation và Java configuration

**`AnnotatedTraceAspect.java`** mang định nghĩa advice trực tiếp trong Java:

```java
package com.example.aopmigration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class AnnotatedTraceAspect {
    private final TraceLog log;

    public AnnotatedTraceAspect(TraceLog log) {
        this.log = log;
    }

    @Pointcut("execution(* com.example.aopmigration.OrderServiceImpl.placeOrder(..))")
    public void orderOperation() {
    }

    @Around("orderOperation()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getName();
        log.add("enter:" + method);

        try {
            Object result = joinPoint.proceed();
            log.add("ok:" + result);
            return result;
        } catch (Throwable failure) {
            log.add("fail:" + failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
```

Phần body của `around` giống bản XML: trước và sau `proceed()` ghi lại các sự kiện như nhau. Chỉ chuyển nơi khai báo **điểm cắt và kiểu advice** từ XML sang annotation.

**`JavaAopConfig.java`** thay XML đăng ký bean và bật proxy:

```java
package com.example.aopmigration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
public class JavaAopConfig {
    @Bean
    public TraceLog traceLog() {
        return new TraceLog();
    }

    @Bean
    public OrderService orderService() {
        return new OrderServiceImpl();
    }

    @Bean
    public AnnotatedTraceAspect annotatedTraceAspect(TraceLog traceLog) {
        return new AnnotatedTraceAspect(traceLog);
    }
}
```

- `@EnableAspectJAutoProxy` bật infrastructure proxy cho bean mang `@Aspect`.
- `proxyTargetClass = false` duy trì lựa chọn ưu tiên JDK proxy như XML ban đầu. Khi hệ thống có cấu hình proxy khác, phải kiểm tra loại proxy thực tế.
- `@Bean` đăng ký đủ **target**, **log** và **aspect** vào application context. `@Aspect` **không tự đăng ký bean**; ví dụ này không cần `@ComponentScan` hay `@Component`.
- `@Configuration(proxyBeanMethods = false)` phù hợp ví dụ vì các phương thức `@Bean` không gọi nhau trực tiếp; dependency được Spring truyền vào tham số `annotatedTraceAspect(TraceLog)`.

### 2.5. Chạy hai cấu hình và so sánh hành vi

**`MigrationCheck.java`**

```java
package com.example.aopmigration;

import java.util.List;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MigrationCheck {
    record RunResult(
            boolean proxy,
            boolean jdkProxy,
            String successValue,
            String errorType,
            String errorMessage,
            List<String> events
    ) {
    }

    private static RunResult run(String label, ApplicationContext context) {
        OrderService service = context.getBean(OrderService.class);
        TraceLog log = context.getBean(TraceLog.class);

        boolean proxy = AopUtils.isAopProxy(service);
        boolean jdkProxy = AopUtils.isJdkDynamicProxy(service);
        String successValue = service.placeOrder("A-7");

        String errorType = null;
        String errorMessage = null;
        try {
            service.placeOrder("bad");
            throw new AssertionError("Expected a rejected order");
        } catch (IllegalArgumentException failure) {
            errorType = failure.getClass().getSimpleName();
            errorMessage = failure.getMessage();
        }

        RunResult result = new RunResult(
                proxy, jdkProxy, successValue, errorType,
                errorMessage, log.snapshot()
        );
        System.out.println(label + " " + result);
        return result;
    }

    public static void main(String[] args) {
        try (ClassPathXmlApplicationContext xml =
                     new ClassPathXmlApplicationContext("legacy-aop.xml");
             AnnotationConfigApplicationContext java =
                     new AnnotationConfigApplicationContext(JavaAopConfig.class)) {

            RunResult before = run("XML", xml);
            RunResult after = run("JAVA", java);

            if (!before.equals(after)) {
                throw new AssertionError(
                        "AOP behavior changed after migration"
                );
            }
            if (!before.proxy() || !before.jdkProxy()) {
                throw new AssertionError("Expected JDK AOP proxy");
            }
            System.out.println("EQUIVALENT true");
        }
    }
}
```

Chạy trong thư mục dự án:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.example.aopmigration.MigrationCheck
```

Nếu dùng Windows PowerShell, có thể đặt argument trong dấu nháy nếu shell/Maven phiên bản hiện tại yêu cầu:

```powershell
mvn -q compile exec:java "-Dexec.mainClass=com.example.aopmigration.MigrationCheck"
```

**Các dòng do chính `MigrationCheck` in ra** (không bao gồm log Maven, Spring hoặc thư viện khác):

```text
XML RunResult[proxy=true, jdkProxy=true, successValue=OK:A-7, errorType=IllegalArgumentException, errorMessage=invalid order, events=[enter:placeOrder, ok:OK:A-7, enter:placeOrder, fail:IllegalArgumentException]]
JAVA RunResult[proxy=true, jdkProxy=true, successValue=OK:A-7, errorType=IllegalArgumentException, errorMessage=invalid order, events=[enter:placeOrder, ok:OK:A-7, enter:placeOrder, fail:IllegalArgumentException]]
EQUIVALENT true
```

`record RunResult` (Java 17) tự cung cấp `equals()` so sánh các thành phần, gồm cả danh sách event. Nếu thiếu aspect, sai pointcut, advice nuốt exception, khác loại proxy hoặc thay đổi return value, đối chiếu này sẽ phát hiện nhiều dạng sai khác. Ví dụ kiểm tra **hành vi cụ thể của hai lệnh gọi**; một hệ thống thật cần bổ sung tập ca thử cho các method và trường hợp của nghiệp vụ.

## 3. Nếu XML đã dùng `<aop:aspectj-autoproxy/>`

Đây là quá trình chuyển **bootstrap proxy** và bean registration, không cần thay đổi annotation advice.

**Trước, trong XML:**

```xml
<aop:aspectj-autoproxy proxy-target-class="false"/>
<bean id="orderService" class="com.example.aopmigration.OrderServiceImpl"/>
<bean id="annotatedTraceAspect"
      class="com.example.aopmigration.AnnotatedTraceAspect">
    <constructor-arg ref="traceLog"/>
</bean>
```

Ví dụ rút gọn ở trên giả định XML đã có bean `traceLog`; thiếu bean đó thì XML không hoàn chỉnh. Class `AnnotatedTraceAspect` đã có `@Aspect` và `@Around`, nên khi chuyển sang Java config có thể dùng cấu hình ở mục 2.4: `@EnableAspectJAutoProxy` và `@Bean` tương ứng. Không thêm `<aop:config>` để khai báo lại chính advice đó.

Có thể chọn `@Component` trên aspect, `@Service` trên target và `@ComponentScan` trên config thay cho `@Bean`; **chỉ chọn một cơ chế đăng ký cho mỗi bean**, tránh bean trùng làm pointcut được áp dụng ở nơi không mong muốn.

## 4. Tiêu chí kiểm tra tương đương trước khi loại bỏ XML

Thành công của một ca trả kết quả giống nhau chưa đủ chứng minh toàn bộ cấu hình đã tương đương. Đối chiếu có hệ thống:

| Tiêu chí | Phải kiểm tra |
| --- | --- |
| **Spring beans** | Target, aspect, collaborator đăng ký đầy đủ, cùng scope cần thiết; không bean bị trùng hoặc nằm sai application context |
| **Phạm vi pointcut** | Biểu thức giữ nguyên hoặc đã được chứng minh khớp cùng tập method; kiểm tra cả method phải chạy và method không được intercept |
| **Loại advice** | `before`, `after`, `after-returning`, `after-throwing`, `around` giữ đúng semantics và cách bind `JoinPoint`/return value/exception |
| **Kết quả/ngoại lệ** | `@Around` gọi `proceed()` đúng số lần, trả đúng kết quả, không nuốt hoặc tự thay exception nếu bản cũ không làm |
| **Thứ tự** | Nếu nhiều aspect, giữ `order` tương ứng bằng `@Order`/`Ordered`; kiểm tra thứ tự event thực tế khi cùng pointcut |
| **Proxy** | `proxy-target-class` → `proxyTargetClass`; kiểm tra `AopUtils.isAopProxy` và `isJdkDynamicProxy`/`isCglibProxy` |
| **Điểm gọi** | Client lấy bean từ Spring context và gọi qua proxy, không gọi object được tạo bằng `new` |
| **Lỗi đặc thù** | Các trường hợp exception, return `null`, tham số biên, method không khớp pointcut đều có test |
| **Tác dụng phụ** | Thứ tự log/metrics/audit, transaction, security advice và các interceptor khác không thay đổi trái kỳ vọng |

### Cách viết kiểm thử Spring AOP ở mức tích hợp

- **Kiểm tra pointcut dương và âm:** trong mỗi context, gọi method được chọn và method không được chọn, xác minh chỉ method được chọn có advice. Không chỉ test bằng cách gọi trực tiếp class target.
- **Kiểm tra đường lỗi:** buộc target ném exception; so loại lỗi, thông điệp hoặc error contract, và thứ tự advice.
- **Kiểm tra có nhiều aspect:** nếu dùng transaction, tracing, audit hoặc security cùng lúc, cần kiểm thử order thực tế. `@Order` quyết định precedence giữa aspect, không bảo đảm quy tắc thứ tự tùy ý giữa nhiều advice khai báo cùng một class.
- **Kiểm tra đúng proxy:** dùng `AopUtils` và kiểu injection hợp lệ. JDK proxy thường chỉ triển khai interface; code injection bằng concrete class có thể lỗi sau migration nếu đổi proxy type.
- **Kiểm tra context thực:** nếu ứng dụng có parent/child context (chẳng hạn root/web), bật proxy ở đúng context chứa bean cần xử lý; activation nằm ở context con không mặc nhiên áp dụng cho bean ở context cha.
- **Kiểm tra self-invocation:** `this.otherMethod()` trong cùng target bean thường bỏ qua Spring proxy cả trước và sau migration. Nếu yêu cầu advice, đổi luồng gọi qua Spring bean/proxy phù hợp.

Bộ kiểm tra ở mục 2 cho thấy cách so output và proxy tự động; khi refactor ứng dụng thật, có thể đưa cùng logic đối chiếu vào bài integration test JUnit và mở rộng theo mọi method trong danh sách pointcut.

## 5. Những khác biệt dễ tạo lỗi khi chuyển đổi

### 5.1. Quên bật `@EnableAspectJAutoProxy` hoặc quên khai báo bean `@Aspect`

`@Aspect` định nghĩa aspect nhưng không tự biến class thành Spring bean. Khi bỏ XML `<aop:aspectj-autoproxy/>`, ứng dụng dùng Spring Framework Java config cần bật proxy bằng `@EnableAspectJAutoProxy` (hoặc một cấu hình auto-proxy tương đương đã tồn tại). Sau đó đăng ký class aspect bằng `@Bean` hoặc `@Component` kết hợp component scanning.

Với Spring Boot có AOP auto-configuration, đừng thêm annotation chỉ vì quen tay: kiểm tra dependency/cấu hình Boot đang áp dụng, rồi đối chiếu proxy thực tế.

### 5.2. Thay đổi loại proxy

XML `proxy-target-class="false"` tương ứng Java `proxyTargetClass = false`. Nếu thay thành `true`, proxy dựa trên class có thể intercept một tập method khác so với JDK proxy, nhưng có giới hạn ở method `private`/`final` và class `final`. Trong ứng dụng Boot, mặc định có thể ưu tiên class-based proxy tùy cấu hình; vì vậy migration cần kiểm tra giá trị hiệu lực, không chỉ đọc annotation.

### 5.3. Sửa pointcut trong lúc chuyển cú pháp

Đừng vừa migration vừa mở rộng từ `execution(* com.example.aopmigration.OrderServiceImpl.placeOrder(..))` sang biểu thức package rộng hơn như `execution(* com.example..*(..))`. Điều đó thay đổi các method được advice và làm mất khả năng kiểm chứng equivalence. Giữ nguyên biểu thức trước, sau đó mở rộng trong thay đổi riêng có test.

Điều tương tự xảy ra với `@annotation(...)`, `args(...)`, `this(...)`, `target(...)`: cú pháp có thể giống, nhưng matcher chịu ảnh hưởng của proxy type, annotation placement và runtime arguments. Test trong ngữ cảnh Spring thật nếu phụ thuộc vào chúng.

### 5.4. Thứ tự advice và transaction thay đổi

XML có thể quy định `order` tại `<aop:aspect>`; annotation tương ứng có thể sử dụng `@Order` tại class `@Aspect`. Nhưng một proxy có thể chứa cả advisor của Spring Transaction, Spring Security và các advisor khác. Bảo toàn một con số order chưa đủ nếu tập advisor thực tế thay đổi. Kiểm tra các event theo vòng vào/ra và transaction boundaries để xác nhận.

### 5.5. Cấu hình AOP trùng nhau

Đừng để lại advisor XML `<aop:config>` rồi thêm một `@Aspect` thực hiện **cùng concern** nếu cả hai đều chạy: bạn có thể ghi audit hai lần hoặc gọi logic hai lần. Trước khi xóa XML, đối chiếu để biết phần cũ nào được thay bằng class mới; sau khi đã xác nhận tương đương, chỉ giữ một nguồn định nghĩa mỗi advice. Nếu cần duy trì song song trong thời gian migration, đặt chúng trong **hai application context/test fixture độc lập** như ví dụ, không cùng production context.

## 6. Quy trình migration an toàn và tiêu chí hoàn tất

1. **Chụp trạng thái cũ:** liệt kê target bean, aspect, pointcut, kiểu advice, `order`, loại proxy, và các dependency/bean scope liên quan trong XML.
2. **Đóng băng hành vi bằng test:** lấy kết quả từ cấu hình cũ qua Spring proxy, gồm các nhánh thành công, lỗi, method khớp và không khớp, cùng thứ tự side effect.
3. **Ánh xạ XML sang annotation:** chuyển `<aop:aspect>` và advice sang `@Aspect`/`@Before`/`@Around`...; giữ nguyên biểu thức pointcut và logic; đăng ký đủ bean.
4. **Chuyển hạ tầng proxy:** dùng `@EnableAspectJAutoProxy` hoặc cấu hình Boot tương ứng; giữ proxy type và thứ tự advisor có chủ đích.
5. **Chạy cùng bộ kiểm tra trên cấu hình mới:** so return/exception/log/side effects, số advice được gọi, proxy type và đường self-invocation nếu có.
6. **Loại bỏ cấu hình trùng sau khi pass:** xóa advisor XML đã thay thế và XML bean definition không còn dùng; giữ những XML bean configuration khác nếu chưa thuộc phạm vi migration.
7. **Kiểm thử tích hợp ứng dụng:** xác nhận injection, transaction, security, exception handling và các lời gọi từ những entry point thật vẫn đúng.

Tiêu chí **tương đương** là: *cùng ca gọi vào Spring bean qua proxy cho cùng kết quả và side effect, cùng method được pointcut chọn, và cùng contract lỗi/thứ tự liên quan*. Ví dụ `MigrationCheck` cho một phép đo tự động có thể lặp lại; khi ứng dụng có nhiều aspect và method, cần mở rộng tập test tương xứng trước khi kết luận hoàn tất chuyển đổi.

</details>

- [Quay lại đầu trang](#back-to-top)
