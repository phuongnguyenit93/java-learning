<a id="back-to-top"></a>

# Quan sát Proxy

## Menu
- [AopUtils để nhận diện AOP proxy, JDK proxy và CGLIB proxy](#proxy-inspection-001)
- [Lấy kiểu đích mà không nhầm với tên lớp proxy](#proxy-inspection-002)
- [Advised và danh sách advisor đã gắn](#proxy-inspection-003)
- [Đọc TargetSource từ proxy khi API cho phép](#proxy-inspection-004)
- [Thay advisor trong proxy có thể chỉnh sửa và tác động vận hành](#proxy-inspection-005)
- [frozen, opaque và các giới hạn thao tác với proxy](#proxy-inspection-006)
- [Giữ việc tháo proxy trong chẩn đoán thay vì luồng nghiệp vụ](#proxy-inspection-007)
- [Rủi ro gọi trực tiếp target và bỏ qua toàn bộ advice](#proxy-inspection-008)

## <a id="proxy-inspection-001">AopUtils để nhận diện AOP proxy, JDK proxy và CGLIB proxy</a>

<details>
<summary>Xem chi tiết</summary>

# AopUtils: nhận diện Spring AOP proxy, JDK proxy và CGLIB proxy

## 1. Vì sao cần kiểm tra proxy trong Spring AOP?

Một bean được trả về từ `ApplicationContext` có thể là chính đối tượng nghiệp vụ, hoặc **Spring AOP proxy** đứng trước đối tượng nghiệp vụ. Với proxy, lời gọi từ bên ngoài đi qua một chuỗi advisor/interceptor rồi mới tới target. Khi một `@Before`, `@Around`, `@Transactional` hoặc advice khác không chạy như mong đợi, việc đầu tiên nên kiểm tra là: **đối tượng caller đang gọi có phải Spring AOP proxy không?**

Spring Framework cung cấp `org.springframework.aop.support.AopUtils` để trả lời trực tiếp:

- `isAopProxy(bean)`: bean có phải Spring AOP proxy không?
- `isJdkDynamicProxy(bean)`: đó có phải **Spring AOP proxy triển khai qua JDK dynamic proxy** không?
- `isCglibProxy(bean)`: đó có phải **Spring AOP proxy kiểu class-based, dựa trên CGLIB** không?
- `getTargetClass(bean)`: kiểu target mà proxy đại diện là gì, nếu có thông tin target; nếu không có thì trả về class của đối tượng thường.

Tác dụng chính là **quan sát và chẩn đoán cấu hình Spring AOP đang chạy thật**. Một class mang `@Service` hoặc method mang annotation advice không chứng minh caller nhận được proxy. Cũng không thể suy ra chắc chắn loại proxy chỉ từ tên class hiển thị trong log.

Bài chỉ tập trung vào cơ chế proxy/advisor của **Spring AOP**. Các kỹ thuật Java proxy tổng quát được nhắc tới để phân biệt kết quả `AopUtils`, không nhằm trình bày một chuyên đề AOP/Reflection riêng.

## 2. Các phương thức AopUtils và ý nghĩa chính xác

Các API đang xét thuộc `org.springframework.aop.support.AopUtils` trong module `spring-aop`:

~~~java
import org.springframework.aop.support.AopUtils;

boolean springProxy = AopUtils.isAopProxy(candidate);
boolean jdkProxy = AopUtils.isJdkDynamicProxy(candidate);
boolean classProxy = AopUtils.isCglibProxy(candidate);
Class<?> targetType = AopUtils.getTargetClass(candidate);
~~~

Đây là snippet đặt trong phương thức Java với biến `candidate` đã có giá trị. Các API nhận object và dùng để kiểm tra đối tượng đang chạy, không cần người gọi tự phân tích tên lớp proxy.

| Phương thức | Trả về | Câu hỏi đúng mà phương thức trả lời |
| --- | --- | --- |
| `AopUtils.isAopProxy(Object)` | `boolean` | Object có phải proxy do hạ tầng Spring AOP tạo theo cơ chế JDK/CGLIB hay không? |
| `AopUtils.isJdkDynamicProxy(Object)` | `boolean` | Object có phải **Spring AOP JDK dynamic proxy** hay không? |
| `AopUtils.isCglibProxy(Object)` | `boolean` | Object có phải **Spring AOP CGLIB proxy** hay không? |
| `AopUtils.getTargetClass(Object)` | `Class<?>` | Target class đã biết của proxy là gì? Nếu object thường, trả class của chính nó |

**Điểm quan trọng:** `isJdkDynamicProxy` của Spring kiểm tra thêm marker `org.springframework.aop.SpringProxy`; nó không đơn giản đồng nghĩa với `java.lang.reflect.Proxy.isProxyClass(...)`. Tương tự, `isCglibProxy` quan tâm đến proxy của Spring AOP chứ không phải mọi object có class name trông giống CGLIB.

Trong điều kiện thông thường, một object không đồng thời được phân loại là Spring JDK proxy **và** Spring CGLIB proxy. Nếu `isJdkDynamicProxy` hoặc `isCglibProxy` là `true`, `isAopProxy` cũng là `true` theo các cơ chế Spring hỗ trợ. Nhưng hai kết quả riêng biệt này **không cho biết** chính xác bao nhiêu advice đã được thực thi trong lần gọi cụ thể.

### Tại sao `getTargetClass` hữu ích?

JDK proxy được định nghĩa bằng một lớp proxy của JDK, không phải subclass của implementation. Do đó:

~~~java
System.out.println(bean.getClass().getName());
~~~

có thể in tên proxy động như `jdk.proxy...` (tên không ổn định giữa môi trường và phiên bản JDK). Còn:

~~~java
System.out.println(AopUtils.getTargetClass(bean).getName());
~~~

có thể trả về class nghiệp vụ thực tế mà Spring proxy đại diện, chẳng hạn `demo.OrderServiceImpl`, nếu proxy mang thông tin target class.

Với CGLIB, `bean.getClass()` thường là subclass được sinh ra, có tên chứa dấu hiệu CGLIB; `getTargetClass` giúp chẩn đoán đúng target class thay vì dựa vào tên generated subclass. Với object thường, `getTargetClass` trả class của chính object đó.

`getTargetClass` không phải một API đảm bảo **unwrap toàn bộ nhiều lớp proxy**, cũng không bắt buộc phải lấy được target instance. Nếu cần tìm target class xuyên qua nhiều lớp Spring proxy được lồng, xem `AopProxyUtils.ultimateTargetClass(Object)` và hợp đồng của nó; khi cần target object thật sự, phải xử lý `TargetSource` thận trọng để tránh phá semantics pooling/scoped targets.

## 3. Bảng nhận diện các trường hợp điển hình

| Đối tượng | `isAopProxy` | `isJdkDynamicProxy` | `isCglibProxy` |
| --- | --- | --- | --- |
| Target Java thông thường, không qua proxy | `false` | `false` | `false` |
| Spring AOP proxy qua interface bằng JDK | `true` | `true` | `false` |
| Spring AOP class-based proxy qua CGLIB | `true` | `false` | `true` |
| JDK dynamic proxy do `Proxy.newProxyInstance` tạo thủ công, không có marker Spring | `false` | `false` | `false` |

Bảng này giả định object được phân loại bằng các kiểu thông dụng. Điều kiện cần kiểm tra trực tiếp là **instance đang được sử dụng trong ứng dụng**, không phải nguồn code class mà nó kế thừa hay interface mà nó khai báo.

Ví dụ, đối tượng mà code tạo bằng:

~~~java
OrderService target = new OrderServiceImpl();
~~~

không tự biến thành Spring AOP proxy chỉ vì class có `@Service`. Đối tượng được lấy bằng:

~~~java
OrderService service = context.getBean(OrderService.class);
~~~

**có thể** là proxy nếu Spring thực sự áp dụng advisor/auto-proxy creator cho bean ấy. Vì thế cần kiểm tra `service` lấy từ context, không kiểm tra một object khác có cùng type do mình vừa `new`.

## 4. JDK proxy và CGLIB proxy trong Spring AOP

Spring AOP dựa trên **proxy method execution**. Việc Spring tạo JDK hay CGLIB thường chịu ảnh hưởng bởi interface của target và cấu hình `proxyTargetClass`.

| Tiêu chí | Spring JDK dynamic proxy | Spring CGLIB proxy |
| --- | --- | --- |
| Cơ chế | Một object triển khai các proxy interfaces | Một subclass được tạo từ target class |
| Phù hợp | Service có interface contract | Target không có interface thích hợp hoặc muốn dùng class-based proxy |
| Cast và lookup | Cast theo interface được proxy triển khai | Có thể cast về target class nếu subclassing hợp lệ |
| Lời gọi method được advice | Những method gọi qua interface proxy tương ứng | Những method có thể được subclass proxy intercept |
| Hạn chế đặc thù | Không thể ép JDK proxy thành implementation class | Final class không thể subclass; final/private methods không thể bị advice qua override |
| Giá trị quan sát `AopUtils` | `isJdkDynamicProxy=true` | `isCglibProxy=true` |

Để ép class-based proxy trong cấu hình Spring:

~~~java
ProxyFactory factory = new ProxyFactory(target);
factory.setProxyTargetClass(true);
Object proxy = factory.getProxy();
~~~

Đây là snippet độc lập về ý tưởng, có `target` đã được khai báo và Spring AOP dependency phù hợp. `setProxyTargetClass(true)` chọn đường class-based cho target có thể được subclass. Với interface-based proxy, khai báo rõ interfaces và `setProxyTargetClass(false)`.

Ngoài code tạo proxy thủ công, Spring cũng hỗ trợ các cấu hình:

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Configuration
class AopConfiguration {}
~~~

~~~xml
<aop:aspectj-autoproxy proxy-target-class="true"/>
~~~

Hai đoạn này là **biến thể cấu hình riêng**. Annotation cần `org.springframework.context.annotation.EnableAspectJAutoProxy`, `Configuration`; XML cần namespace `aop` và schema hợp lệ. Không đặt chúng vào ví dụ `ProxyFactory` ở phần 5 vì ví dụ ấy tự tạo proxy và không dùng `@Aspect`.

Trong Spring Boot, giá trị default của cơ chế proxy có thể do auto-configuration/property quyết định, khác với giả định đơn giản “có interface thì luôn dùng JDK”. Hãy quan sát object thực tế bằng `AopUtils` thay vì đoán từ class definition.

## 5. Ví dụ Java 17 chạy được: quan sát bốn loại object

Ví dụ tạo một target thường, một Spring JDK proxy, một Spring CGLIB proxy và một JDK proxy thường do Java tạo thủ công. Qua đó ta thấy `AopUtils` phân biệt Spring AOP proxy với dynamic proxy không do Spring AOP quản lý.

### 5.1. Cấu trúc dự án

~~~text
spring-aop-inspection/
├── pom.xml
└── src/main/java/demo/ProxyInspectionDemo.java
~~~

### 5.2. pom.xml

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-inspection</artifactId>
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
                    <mainClass>demo.ProxyInspectionDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Dependency `spring-aop` và các dependency chuyển tiếp cung cấp `ProxyFactory`, `AopUtils`, `MethodInterceptor` và lớp proxy cần thiết. Ví dụ không dùng expression parser kiểu `@AspectJ`, do đó không cần tự thêm `aspectjweaver` chỉ để chạy ví dụ này.

### 5.3. src/main/java/demo/ProxyInspectionDemo.java

~~~java
package demo;

import java.lang.reflect.Proxy;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public class ProxyInspectionDemo {

    public interface Greeting {
        String hello(String name);
    }

    // Không final: có thể tạo CGLIB subclass.
    public static class GreetingService implements Greeting {
        @Override
        public String hello(String name) {
            return "Hello " + name;
        }
    }

    private static void inspect(String label, Object object) {
        System.out.println(label
                + ": aop=" + AopUtils.isAopProxy(object)
                + ", jdk=" + AopUtils.isJdkDynamicProxy(object)
                + ", cglib=" + AopUtils.isCglibProxy(object)
                + ", target=" + AopUtils.getTargetClass(object).getSimpleName());
    }

    public static void main(String[] args) {
        GreetingService target = new GreetingService();

        MethodInterceptor logging = invocation -> {
            System.out.println("advice: " + invocation.getMethod().getName());
            return invocation.proceed();
        };

        // Spring JDK proxy: có interface và không ép class-based proxy.
        ProxyFactory jdkFactory = new ProxyFactory();
        jdkFactory.setTarget(target);
        jdkFactory.setInterfaces(Greeting.class);
        jdkFactory.setProxyTargetClass(false);
        jdkFactory.addAdvice(logging);
        Greeting jdk = (Greeting) jdkFactory.getProxy();

        // Spring CGLIB proxy: ép subclass của GreetingService.
        ProxyFactory cglibFactory = new ProxyFactory();
        cglibFactory.setTarget(target);
        cglibFactory.setProxyTargetClass(true);
        cglibFactory.addAdvice(logging);
        Greeting cglib = (Greeting) cglibFactory.getProxy();

        // Java dynamic proxy thường, không có SpringProxy marker.
        Greeting plainJdk = (Greeting) Proxy.newProxyInstance(
                Greeting.class.getClassLoader(),
                new Class<?>[] { Greeting.class },
                (proxy, method, methodArgs) ->
                        "Java handler " + methodArgs[0]
        );

        inspect("target", target);
        inspect("spring-jdk", jdk);
        inspect("spring-cglib", cglib);

        System.out.println("plain-jdk: javaProxy="
                + Proxy.isProxyClass(plainJdk.getClass())
                + ", springAop=" + AopUtils.isAopProxy(plainJdk));

        System.out.println("jdk-result=" + jdk.hello("Lan"));
        System.out.println("cglib-result=" + cglib.hello("Minh"));
    }
}
~~~

Dùng Java 17 và Maven:

~~~bash
mvn -q compile exec:java
~~~

Các dòng `System.out` kỳ vọng với classpath bình thường:

~~~text
target: aop=false, jdk=false, cglib=false, target=GreetingService
spring-jdk: aop=true, jdk=true, cglib=false, target=GreetingService
spring-cglib: aop=true, jdk=false, cglib=true, target=GreetingService
plain-jdk: javaProxy=true, springAop=false
advice: hello
jdk-result=Hello Lan
advice: hello
cglib-result=Hello Minh
~~~

Maven hoặc logger của framework có thể in thông báo bổ sung; các dòng trên thể hiện đúng logic dự kiến của ví dụ. Không cố assert tên class generated proxy vì tên ấy phụ thuộc JDK/Spring version và class loader.

### 5.4. Điều ví dụ chứng minh

1. `target` là class bình thường; `AopUtils.isAopProxy(target)` trả `false` dù object này cũng chính là target của các proxy được tạo sau.
2. Spring JDK proxy implement `Greeting`; `AopUtils.isJdkDynamicProxy(jdk)` trả `true`, đồng thời `isCglibProxy` trả `false`.
3. Spring CGLIB proxy là subclass được sinh từ `GreetingService`; `AopUtils.isCglibProxy(cglib)` trả `true`.
4. `Proxy.isProxyClass(plainJdk.getClass())` của Java trả `true`, nhưng `AopUtils.isAopProxy(plainJdk)` trả `false`. **Spring AOP proxy** và **JDK dynamic proxy nói chung** không đồng nghĩa.
5. Cả hai Spring proxy cùng dùng `logging` advice. Việc kiểm tra `AopUtils` không chạy advice; chỉ khi gọi `jdk.hello` và `cglib.hello` qua proxy mới thấy `advice: hello`.
6. `AopUtils.getTargetClass` của hai Spring proxy trả kiểu `GreetingService`, giúp chẩn đoán lớp triển khai thực tế.

Ví dụ không cần `ApplicationContext` vì `ProxyFactory` là Spring AOP **programmatic API**. Trong ứng dụng @AspectJ, @Transactional hoặc XML AOP thông thường, cần lấy bean từ context đang chạy để kiểm tra proxy mà caller sử dụng thật.

## 6. Dùng AopUtils để kiểm tra Spring-managed bean

Trong Spring application thật, bean có thể được auto-proxy bởi `@EnableAspectJAutoProxy`, transaction advisor hoặc hạ tầng AOP khác. Hãy lấy object từ đúng Spring context:

~~~java
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

static void report(ApplicationContext context, String beanName) {
    Object bean = context.getBean(beanName);
    System.out.println("bean=" + beanName);
    System.out.println("isAopProxy=" + AopUtils.isAopProxy(bean));
    System.out.println("isJdkDynamicProxy=" + AopUtils.isJdkDynamicProxy(bean));
    System.out.println("isCglibProxy=" + AopUtils.isCglibProxy(bean));
    System.out.println("targetClass=" + AopUtils.getTargetClass(bean).getName());
}
~~~

Đây là **một method minh họa**, cần đặt trong Java class và truyền `ApplicationContext` đã khởi tạo. Nếu bean không nằm trong context, `getBean` ném lỗi; nếu context chưa bật AOP hoặc không có advisor match, bean có thể vẫn là object thường.

Có thể kiểm tra trực tiếp bean theo interface:

~~~java
OrderService service = context.getBean(OrderService.class);
boolean advisedObject = AopUtils.isAopProxy(service);
~~~

`OrderService` trong snippet là interface của ứng dụng, không phải class Spring có sẵn. Với JDK proxy, tìm bean theo interface phù hợp; lookup/cast theo concrete implementation không nhất thiết hoạt động.

### Điểm phân biệt: class khai báo annotation và object được advice

Không suy luận:

~~~java
@Aspect
@Component
class LoggingAspect { /* advice */ }
~~~

rằng `LoggingAspect` tự nó là AOP proxy, hoặc rằng mọi bean khác đều được proxy. Annotation ở aspect định nghĩa advisor, còn Spring phải đăng ký aspect bean, tạo proxy cho các target bean thích hợp và xử lý lời gọi qua proxy.

Một `@Transactional` trên method cũng không tự bảo đảm transaction advice sẽ chạy khi gọi `this.someMethod()` nội bộ trong cùng bean. `AopUtils.isAopProxy(bean)` trả `true` chứng minh đối tượng đã được proxy, **không bảo đảm mọi method và mọi đường gọi đều đi qua advisor đó**.

## 7. Khi cần phân tích thêm chuỗi advisors

`AopUtils` trả thông tin tổng quan. Nếu object là Spring proxy và hỗ trợ `org.springframework.aop.framework.Advised`, có thể kiểm tra các advisors:

~~~java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

static void inspectAdvisors(Object bean) {
    if (!AopUtils.isAopProxy(bean)) {
        System.out.println("not a Spring AOP proxy");
        return;
    }
    if (bean instanceof Advised advised) {
        for (Advisor advisor : advised.getAdvisors()) {
            System.out.println(advisor.getClass().getName());
        }
    } else {
        System.out.println("proxy does not expose Advised");
    }
}
~~~

Đây là **đoạn method minh họa**. Spring AOP proxy có thể không expose `Advised` (ví dụ cấu hình opaque), và không phải mọi advisor đều hiển thị một class name dễ hiểu. Chỉ inspect những gì contract của object cho phép; đừng ép cast `Advised` khi `instanceof` false.

Ngay cả khi `getAdvisors()` trả một hoặc nhiều phần tử, để xác định **method cụ thể** có bị chặn hay không cần xét pointcut/method matcher, thứ tự interceptor và hành vi thực tế. Đừng coi số lượng advisors là bằng chứng đầy đủ rằng advice đã thực thi.

Trong môi trường Spring, một số proxy có thể lồng nhau, target source có thể scoped/lazy/pooling; gọi logic “unwrap” bừa bãi để tìm target thật có thể thay đổi lifecycle của target. Để quan sát trước tiên, `AopUtils.getTargetClass` thường đủ an toàn cho mục tiêu báo cáo target type.

## 8. Vì sao JDK proxy và CGLIB có thể khác nhau trong ứng dụng?

### 8.1. Bean có interface

Target triển khai interface thường có thể dùng JDK dynamic proxy. Spring proxy implement interface đó, caller gọi theo interface. Nhưng JDK proxy **không phải subtype của class implementation**:

~~~java
ServiceApi service = context.getBean(ServiceApi.class);
// Nếu Spring dùng JDK proxy, lệnh sau có thể gây ClassCastException:
ServiceImpl concrete = (ServiceImpl) service;
~~~

Đây là snippet minh họa với `ServiceApi` và `ServiceImpl` do ứng dụng định nghĩa. Khi gặp lỗi cast, kiểm tra `AopUtils.isJdkDynamicProxy(service)` rồi dùng interface contract phù hợp hoặc xác định vì sao cần chuyển sang class-based proxy.

### 8.2. Target không có interface hoặc bị ép class-based

Spring có thể dùng CGLIB class-based proxy để tạo subclass của target. Khi `proxyTargetClass=true`, kể cả target có interface, proxy thường là CGLIB.

Giới hạn cần nhớ: final class không thể subclass; final method và private method không thể được advice qua cơ chế override của CGLIB; Java Module System có thể hạn chế class proxying. Một lần kiểm tra `isCglibProxy=true` **không chứng minh** một final method đã bị advice.

### 8.3. Cấu hình ảnh hưởng loại proxy

Bên cạnh `ProxyFactory`, các cách bật AOP trong Spring có thể chọn class proxy:

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = true)
~~~

~~~xml
<aop:config proxy-target-class="true">
    <!-- pointcuts and aspects -->
</aop:config>
~~~

Khi nhiều tính năng Spring như transaction và @AspectJ dùng chung auto-proxy creator, tùy phiên bản và cấu hình, tùy chọn mạnh về class-based proxy có thể ảnh hưởng nhiều target trong cùng context. Vì vậy khi debug hãy ghi **Spring Framework/Boot version**, loại bean và cấu hình thực tế rồi quan sát AopUtils.

Nếu một bean không được proxy, chỉ đổi cấu hình `proxyTargetClass` thường **không đủ**; phải có advisor phù hợp, bean thuộc phạm vi auto-proxying và invocation phải qua proxy.

## 9. Lỗi chẩn đoán phổ biến

| Cách chẩn đoán sai | Vì sao sai | Cách dùng Spring AOP chính xác |
| --- | --- | --- |
| Tên class chứa dấu `$$SpringCGLIB$$` nên mọi method đã được aspect chặn | Class name chỉ gợi ý công nghệ sinh class, không chứng minh method matcher đã chạy | Dùng `AopUtils.isCglibProxy`, sau đó test method qua proxy |
| `Proxy.isProxyClass` trả `true` thì là Spring AOP proxy | JDK proxy còn được Java/library khác sử dụng | Dùng `AopUtils.isJdkDynamicProxy` hoặc `isAopProxy` |
| Bean có `@Service` nên chắc chắn là proxy | `@Service` đăng ký bean, không tự tạo AOP advisor | Lấy instance từ context và gọi `AopUtils` |
| Có `@Aspect` class nên tất cả services đều có AOP proxy | Chỉ những bean/method được advisor match mới được áp dụng AOP | Kiểm tra phạm vi component scan, pointcut và auto-proxy creator |
| `isAopProxy=true` nghĩa là một method chắc chắn chạy advice | Proxy có thể chỉ intercept một số methods; self-invocation bypass proxy | Test đúng method/đường gọi qua proxy |
| `getTargetClass` luôn unwrap được target object | API trả Class, không đảm bảo lấy instance bên trong | Dùng `getTargetClass` cho metadata, tránh unwrap tùy tiện |
| Cast JDK proxy thành implementation | Proxy chỉ implement interface, không subclass implementation | Cast theo interface và kiểm tra loại proxy |
| CGLIB proxy luôn intercept final/private method | Cơ chế subclass override không intercept được các method đó | Kiểm tra visibility/final và Spring proxy boundary |
| Kiểm tra `new ServiceImpl()` rồi kết luận bean trong context không có AOP | Hai object khác nhau, target tự tạo không qua BeanFactory | Gọi `context.getBean` và inspect object thật |
| Tưởng `isAopProxy=true` chứng minh transaction đang active | Proxy có thể có advisor khác; transaction có boundary/context riêng | Kiểm tra transaction interceptor và hành vi bên trong method |

## 10. Một quy trình debug ngắn cho Spring AOP

1. **Bắt đầu từ caller thật:** lấy instance service đúng như controller/service khác đang nhận từ `ApplicationContext`. Đừng debug một instance tự tạo bằng `new`.
2. **In ba flags:** `isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy`; chúng cho biết có proxy không và loại nào.
3. **In target class:** dùng `AopUtils.getTargetClass` để kiểm tra Spring đang đại diện cho implementation nào, nhất là khi bean name/qualifier có thể trỏ tới nhiều implementation.
4. **Kiểm tra contract:** nếu là JDK proxy, caller phải dùng interface proxy expose; nếu class-based, kiểm tra final/visibility/module.
5. **Nếu có proxy nhưng advice không chạy:** xem Spring đã đăng ký aspect/advisor chưa, pointcut match method nào, order của advisors và lời gọi có qua proxy hay self-invocation.
6. **Nếu muốn xem advisor list:** dùng `bean instanceof Advised` trước khi đọc `getAdvisors()`; đừng phụ thuộc vào tên generated class trong test.
7. **Xác minh bằng lời gọi thật:** test một method có advice và một method không match, so kết quả, exception và side effects. Đảm bảo advice không đổi semantics ngoài mục tiêu.
8. **Ghi nhận phiên bản:** Spring Framework/Boot có thể đổi default proxying hoặc cách sinh class. Chỉ assert flags đã cấu hình chủ động, không dựa vào tên class proxy.

Một integration test có thể assert:

~~~java
Object service = context.getBean("orderService");

assertTrue(AopUtils.isAopProxy(service));
assertTrue(
    AopUtils.isJdkDynamicProxy(service)
        || AopUtils.isCglibProxy(service)
);
~~~

Đây là **snippet JUnit** giả định đã có `context`, bean ID và static import `org.junit.jupiter.api.Assertions.assertTrue`. Chỉ áp dụng assertion thứ nhất nếu test **biết chắc** bean đó phải được proxy theo cấu hình. Nếu muốn assert loại JDK/CGLIB cụ thể, hãy cấu hình proxy strategy tương ứng trong test, tránh phụ thuộc default tình cờ.

## 11. Các giới hạn của AopUtils cần nhớ

- `AopUtils` là **bộ tiện ích chẩn đoán Spring AOP**. Nó không kích hoạt proxy, không sửa pointcut và không tự thêm advice.
- `getTargetClass` có thể trả implementation type hữu ích, nhưng không xác định một invocation đã match pointcut hay chưa.
- Spring AOP proxy chỉ intercept method call đi qua proxy; object target có thể tự gọi `this.otherMethod()` và bỏ qua chain của proxy cho method đó.
- Một object có thể là JDK dynamic proxy nhưng không phải Spring AOP proxy; sự phân biệt này quan trọng khi dùng các framework khác trong cùng ứng dụng.
- Loại proxy không nên quyết định cách triển khai nghiệp vụ dựa trên class-name substring. Sử dụng interface contract, introspection API và test.
- Đừng bật class-based proxy chỉ để thấy `isCglibProxy=true`; chọn strategy theo public API/bean type và yêu cầu advice.
- Nếu AOP hoạt động trên đối tượng scoped/lazy/target source đặc biệt, tránh gọi trực tiếp các thao tác tháo proxy có side effect chỉ để log metadata. `AopUtils` là bước quan sát đầu tiên phù hợp hơn.

## 12. Ghi nhớ

**`AopUtils.isAopProxy(bean)` xác định một object có phải Spring AOP proxy. Hai hàm `isJdkDynamicProxy` và `isCglibProxy` phân loại cơ chế proxy. `getTargetClass` giúp tìm kiểu target thay vì tên proxy được sinh.** Để đánh giá chính xác hoạt động advice, hãy lấy bean thực tế từ Spring context và gọi method **qua chính proxy đó**; chỉ nhìn vào proxy type chưa đủ để kết luận pointcut, transaction hoặc advice đã thực thi.

### Tài liệu chính thức

- [Spring Framework 6.1 — AopUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.1.4/javadoc-api/org/springframework/aop/support/AopUtils.html).
- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- [Spring Framework — ProxyFactoryBean](https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html).
- [Spring Framework — Programmatic Creation of @AspectJ Proxies](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/programmatic.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-inspection-002">Lấy kiểu đích mà không nhầm với tên lớp proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Lấy kiểu đích mà không nhầm với tên lớp proxy trong Spring AOP

## 1. Vấn đề thực tế trong Spring

Khi Spring tạo AOP proxy cho một bean, đối tượng mà client nhận được thường là **proxy**, trong khi nghiệp vụ được thực thi trên **target**. Vì vậy, câu lệnh:

```java
Class<?> runtimeClass = bean.getClass();
```

trả về lớp runtime của **object hiện được giữ trong biến `bean`**, không nhất thiết là lớp triển khai nghiệp vụ. Hai cơ chế Spring AOP phổ biến có sự khác biệt:

- **JDK dynamic proxy:** lớp sinh động implement những interface được proxy, có thể có tên dạng `jdk.proxy2.$Proxy...`; nó thường không kế thừa lớp triển khai.
- **CGLIB class-based proxy:** Spring tạo lớp con động của lớp target, có thể có tên chứa `$$SpringCGLIB$$...`.
- **Không được AOP proxy:** `bean.getClass()` thường chính là lớp đối tượng thật.

Ví dụ, khi `InvoiceService implements InvoicePort`, cả một proxy JDK và một proxy CGLIB có thể chuyển tiếp lời gọi tới `InvoiceService`, nhưng `getClass()` của chúng là hai loại class được tạo lúc chạy. Mã tìm annotation, ghi tên class vào telemetry, tìm method triển khai hoặc kiểm tra kiểu target không nên dựa vào tên class sinh động.

**Mục tiêu:** dùng đúng API của **Spring Framework AOP** để xác định loại proxy, lấy kiểu target và method triển khai; hiểu giới hạn của từng cách tìm lớp gốc. Đây là bài về quan sát Spring AOP proxy, không mở rộng sang cơ chế reflection hay AspectJ weaving nói chung.

## 2. Phân biệt runtime class, target class và exposed type

| Khái niệm | Câu hỏi | Ví dụ |
| --- | --- | --- |
| Runtime class | Object đang giữ được JVM tạo từ class nào? | `jdk.proxy...$Proxy...` hoặc `InvoiceService$$SpringCGLIB$$...` |
| Target class | Kiểu đối tượng nghiệp vụ mà Spring AOP proxy hướng tới là gì? | `InvoiceService.class` nếu metadata target được biết |
| Exposed type | Client có thể gọi qua interface/class nào? | `InvoicePort` với JDK proxy; `InvoiceService` với CGLIB proxy |

```text
Client qua interface:
  InvoicePort
       |
       v
  JDK dynamic proxy (implements InvoicePort)
       |
       v
  InvoiceService (target)

Client qua class-based proxy:
  InvoiceService (declared type)
       |
       v
  CGLIB subclass of InvoiceService (proxy)
       |
       v
  InvoiceService (target)
```

Các kết luận quan trọng:

1. Với JDK proxy, `proxy instanceof InvoicePort` thường đúng; `proxy instanceof InvoiceService` thường sai, dù target là `InvoiceService`.
2. Với CGLIB proxy, `proxy instanceof InvoiceService` thường đúng vì proxy là subclass; nhưng `proxy.getClass() == InvoiceService.class` sai.
3. Không dùng `getClass().getSuperclass()` như một phương pháp chung để bóc proxy. Superclass của JDK proxy không phải class nghiệp vụ; CGLIB có thể còn nhiều lớp wrapper, và còn vấn đề target source động.
4. Tìm được lớp target không tự động chứng minh mọi method sẽ được advice intercept. Pointcut, loại proxy và việc gọi qua proxy vẫn quyết định điều đó.

## 3. AopUtils: API nên dùng khi có bean instance

Spring cung cấp `org.springframework.aop.support.AopUtils`.

```java
import org.springframework.aop.support.AopUtils;

// bean là một object đang được ứng dụng Spring sử dụng.
boolean isSpringAop = AopUtils.isAopProxy(bean);
boolean isJdk = AopUtils.isJdkDynamicProxy(bean);
boolean isCglib = AopUtils.isCglibProxy(bean);
Class<?> targetType = AopUtils.getTargetClass(bean);
```

Đoạn trên là **mã minh họa khi biến `bean` đã tồn tại**, không phải một chương trình Java độc lập. Các method mang ý nghĩa:

| API | Chức năng |
| --- | --- |
| `isAopProxy(Object)` | Kiểm tra object có phải Spring AOP proxy dạng JDK/CGLIB được Spring nhận diện |
| `isJdkDynamicProxy(Object)` | Nhận biết Spring AOP proxy dựa trên JDK dynamic proxy |
| `isCglibProxy(Object)` | Nhận biết Spring AOP proxy theo class-based CGLIB |
| `getTargetClass(Object)` | Xác định lớp target nếu có thông tin của Spring AOP proxy, fallback về lớp thường của object |

`AopUtils.getTargetClass(bean)` là lựa chọn thông dụng khi cần **class nghiệp vụ phía sau một Spring AOP proxy**. Với object không proxy, nó trả class của object. Kết quả là một `Class<?>`, **không phải instance target**.

Không suy luận như sau:

```java
// Không nên: phụ thuộc tên class runtime do JDK/Spring sinh ra.
boolean seemsProxy = bean.getClass().getName().contains("$$SpringCGLIB$$");

// Dùng đúng API:
boolean springCglib = AopUtils.isCglibProxy(bean);
```

`Proxy.isProxyClass(bean.getClass())` thuộc Java Reflection chỉ cho biết một dạng JDK proxy; nó không thay thế `AopUtils.isAopProxy` khi câu hỏi là **Spring AOP proxy**.

**Giới hạn:** `getTargetClass` làm việc dựa vào thông tin target mà proxy hoặc `TargetClassAware`/`TargetSource` công bố. Khi một nguồn target không công bố được kiểu cụ thể, API không thể phép màu để biết mọi runtime target sẽ là gì. Đừng mặc định kết quả luôn là class thực sâu nhất sau mọi lớp proxy lồng nhau.

## 4. Proxy lồng nhau: AopProxyUtils.ultimateTargetClass

Khi một proxy được tạo quanh một proxy khác, đôi khi cần kiểu target cuối cùng:

```java
import org.springframework.aop.framework.AopProxyUtils;

Class<?> deepestKnownType = AopProxyUtils.ultimateTargetClass(bean);
```

Khác với thao tác tìm target của proxy ngoài, `ultimateTargetClass` cố đi tiếp qua các proxy bên trong **khi việc quan sát an toàn**, chủ yếu bằng cách đi qua những target **singleton** sẵn có. Javadoc nhấn mạnh thao tác truy vết không gây side effect.

```text
outer Spring AOP proxy
       |
       v
inner Spring AOP proxy
       |
       v
InvoiceService singleton target

AopUtils.getTargetClass(outer):
    class target do lớp proxy ngoài khai báo / nhận diện

AopProxyUtils.ultimateTargetClass(outer):
    cố tìm class InvoiceService qua cả hai proxy
```

**Chú ý giới hạn:** nếu có target động, target trong scope, object pool hoặc proxy không cho biết target mà không cần tạo/lấy object, `ultimateTargetClass` có thể phải dừng sớm. Nó không có nghĩa là “luôn lấy đúng class cuối cùng cho mọi cấu hình”. Nếu cần trả lời câu hỏi kiểu target có thể đổi sau từng lời gọi, cần xét hợp đồng `TargetSource` của ứng dụng.

## 5. Ví dụ Java 17+ chạy độc lập: JDK proxy, CGLIB proxy, object thường

Ví dụ tạo hai proxy bằng **`ProxyFactory` của Spring AOP 6.2.14** trên cùng kiểu `InvoiceService`. Trước khi quan sát, cả hai đều được gắn một `MethodInterceptor` chỉ gọi `proceed()` để chắc chắn có Spring AOP advice. Output chỉ in những đặc điểm ổn định; không cố định tên class sinh động.

Tạo ba file:

**`settings.gradle`**

```groovy
rootProject.name = 'spring-aop-target-type'
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
    implementation 'org.springframework:spring-aop:6.2.14'
}

application {
    mainClass = 'TargetTypeDemo'
}
```

**`src/main/java/TargetTypeDemo.java`**

```java
import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public class TargetTypeDemo {

    public interface InvoicePort {
        String issue(String id);
    }

    // Không final để Spring có thể tạo CGLIB subclass.
    public static class InvoiceService implements InvoicePort {
        @Override
        public String issue(String id) {
            return "created:" + id;
        }
    }

    public static void main(String[] args) throws Exception {
        MethodInterceptor passthrough = call -> call.proceed();

        ProxyFactory jdkFactory = new ProxyFactory(new InvoiceService());
        jdkFactory.setInterfaces(InvoicePort.class);
        jdkFactory.setProxyTargetClass(false);
        jdkFactory.addAdvice(passthrough);
        Object jdkProxy = jdkFactory.getProxy();

        ProxyFactory cglibFactory = new ProxyFactory(new InvoiceService());
        cglibFactory.setProxyTargetClass(true);
        cglibFactory.addAdvice(passthrough);
        Object cglibProxy = cglibFactory.getProxy();

        Object plain = new InvoiceService();

        System.out.println("JDK AOP=" + AopUtils.isAopProxy(jdkProxy));
        System.out.println("JDK kind=" + AopUtils.isJdkDynamicProxy(jdkProxy));
        System.out.println("JDK CGLIB=" + AopUtils.isCglibProxy(jdkProxy));
        System.out.println("JDK runtime equals target="
                + (jdkProxy.getClass() == InvoiceService.class));
        System.out.println("JDK target="
                + AopUtils.getTargetClass(jdkProxy).getSimpleName());
        System.out.println("JDK instanceof target="
                + (jdkProxy instanceof InvoiceService));

        Method declared = InvoicePort.class.getMethod("issue", String.class);
        Method actual = AopUtils.getMostSpecificMethod(
                declared, AopUtils.getTargetClass(jdkProxy));
        System.out.println("implementation method="
                + actual.getDeclaringClass().getSimpleName()
                + "." + actual.getName());

        System.out.println("CGLIB AOP=" + AopUtils.isAopProxy(cglibProxy));
        System.out.println("CGLIB kind=" + AopUtils.isCglibProxy(cglibProxy));
        System.out.println("CGLIB runtime equals target="
                + (cglibProxy.getClass() == InvoiceService.class));
        System.out.println("CGLIB target="
                + AopUtils.getTargetClass(cglibProxy).getSimpleName());
        System.out.println("CGLIB instanceof target="
                + (cglibProxy instanceof InvoiceService));

        System.out.println("plain AOP=" + AopUtils.isAopProxy(plain));
        System.out.println("plain target="
                + AopUtils.getTargetClass(plain).getSimpleName());
        System.out.println("ultimate JDK="
                + AopProxyUtils.ultimateTargetClass(jdkProxy).getSimpleName());

        System.out.println("result="
                + ((InvoicePort) jdkProxy).issue("INV-7"));
    }
}
```

Thực thi tại thư mục dự án (có Gradle được cài đặt):

```bash
gradle run --quiet
# Nếu dự án có Gradle Wrapper: ./gradlew run --quiet
```

**Stdout chương trình**, không tính thông báo của Gradle hay JVM:

```text
JDK AOP=true
JDK kind=true
JDK CGLIB=false
JDK runtime equals target=false
JDK target=InvoiceService
JDK instanceof target=false
implementation method=InvoiceService.issue
CGLIB AOP=true
CGLIB kind=true
CGLIB runtime equals target=false
CGLIB target=InvoiceService
CGLIB instanceof target=true
plain AOP=false
plain target=InvoiceService
ultimate JDK=InvoiceService
result=created:INV-7
```

Phân tích kết quả:

1. `jdkProxy` do Spring tạo trên `InvoicePort` nên `isJdkDynamicProxy` đúng; class runtime của nó khác `InvoiceService.class` và bản thân proxy không phải instance của `InvoiceService`.
2. `AopUtils.getTargetClass(jdkProxy)` vẫn tìm được class nghiệp vụ vì cấu hình `ProxyFactory` giữ metadata target rõ ràng.
3. `cglibProxy` là subclass của `InvoiceService` nên `instanceof InvoiceService` đúng, nhưng class runtime vẫn là lớp khác.
4. `plain` không phải proxy Spring AOP nên `isAopProxy` sai; `getTargetClass` trả chính `InvoiceService.class`.
5. `AopUtils.getMostSpecificMethod` chuyển method được khai báo trên `InvoicePort` thành method triển khai trên `InvoiceService`.
6. `ultimateTargetClass` tìm ra `InvoiceService` vì `ProxyFactory` nhận target singleton trực tiếp. Lời gọi `issue` được thực thi qua interface và interceptor chuyển tiếp tới target.

Trong thực tế có thể in `jdkProxy.getClass().getName()` để chẩn đoán, nhưng **không nên assert tên đó trong bài test hoặc đưa nguyên văn làm key metrics**, vì tên sinh động thay đổi theo môi trường.

## 6. Tìm annotation class và method đúng với target

Một lỗi điển hình khi Spring AOP hoạt động là annotation “biến mất”:

```java
// Sai ngữ nghĩa với JDK proxy nếu annotation nằm trên InvoiceService:
boolean found = bean.getClass().isAnnotationPresent(MyMarker.class);
```

Thay vào đó, nếu annotation được đặt ở lớp triển khai:

```java
// Mã minh họa; bean và MyMarker được định nghĩa trong ứng dụng.
Class<?> targetType = AopUtils.getTargetClass(bean);
MyMarker marker = org.springframework.core.annotation.AnnotatedElementUtils
        .findMergedAnnotation(targetType, MyMarker.class);
```

`AnnotatedElementUtils.findMergedAnnotation` là API hỗ trợ annotation hợp nhất/meta-annotation theo quy tắc của Spring. Dù vậy, việc lấy `targetType` **không tự quyết định** liệu annotation nên được tìm trên interface, superclass, method hay implementation. Nếu hệ thống cho phép đặt annotation trên interface thì chính sách tìm kiếm phải thể hiện rõ.

Với annotation phương thức, JDK proxy thường làm việc qua method khai báo trên interface, trong khi annotation có thể nằm ở method override trên target. Sử dụng:

```java
import java.lang.reflect.Method;

// Đoạn minh họa nếu đã có bean và Method trên interface:
Class<?> targetType = AopUtils.getTargetClass(bean);
Method implementation = AopUtils.getMostSpecificMethod(interfaceMethod, targetType);
```

`getMostSpecificMethod` tìm implementation cụ thể nếu có, kể cả xử lý bridge method theo quy tắc Spring AOP. Sau đó có thể xét `AnnotatedElementUtils.findMergedAnnotation(implementation, MyMarker.class)`.

**Phân biệt method để introspect với method có thể invoke trên proxy.** Việc tìm ra method trên class target không có nghĩa có thể dùng reflection gọi trực tiếp method đó với JDK proxy (vì proxy không phải instance của target class). Client phải gọi qua interface mà proxy expose. Khi cần chọn method có thể gọi trên exposed type, xem `AopUtils.selectInvocableMethod`.

## 7. ClassUtils.getUserClass khác với AopUtils

Spring Core có tiện ích:

```java
import org.springframework.util.ClassUtils;

Class<?> userClass = ClassUtils.getUserClass(bean);
```

`ClassUtils.getUserClass` hỗ trợ suy ra class ứng dụng từ subclass sinh bởi CGLIB. Nó có giá trị trong một số tình huống class runtime có hậu tố được Spring tạo.

Tuy nhiên:

- Với **JDK dynamic proxy**, nó không đủ để xác định lớp triển khai phía sau chỉ từ cấu trúc class: JDK proxy implement interface chứ không extends lớp nghiệp vụ.
- Với **proxy lồng nhau** hoặc `TargetSource` động, nó không xử lý ý nghĩa target theo cách `AopProxyUtils.ultimateTargetClass` làm.
- Với Spring AOP bean, cách diễn đạt đúng ý thường là `AopUtils.getTargetClass(bean)`.

Đừng nhầm `getUserClass` với một thuật toán bóc mọi proxy bất kể framework.

## 8. TargetSource và giới hạn quan sát đối tượng

Spring AOP giữ thông tin target thông qua `TargetSource`. Khi proxy cho phép truy cập interface cấu hình `Advised`:

```java
// Mã minh họa; không tự ý lấy instance target.
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    org.springframework.aop.TargetSource source = advised.getTargetSource();
    Class<?> knownType = source.getTargetClass(); // Có thể null.
}
```

Lưu ý các điểm có ảnh hưởng thực tế:

- `TargetSource.getTargetClass()` cung cấp metadata **class**, có thể trả `null` khi class không dự đoán trước được.
- `TargetSource.getTarget()` là thao tác lấy **object target**. Với source theo pool, prototype, scope hoặc lazy/dynamic, nó có thể tạo/mượn target và có hợp đồng release. Không gọi chỉ để in tên class.
- `AopProxyUtils.ultimateTargetClass` tránh đi sâu bằng cách tạo target có thể gây side effect; với dynamic target nó chỉ truy vấn tới mức an toàn.
- Proxy có thể được cấu hình `opaque`, khiến client không thể cast thành `Advised`; do đó `instanceof Advised` không phải bộ nhận diện Spring AOP proxy đầy đủ.

Giữ riêng hai câu hỏi: “kiểu target Spring biết được là gì?” và “instance nào thực thi lời gọi lần này?”. Chúng chỉ chắc chắn trùng trong những cấu hình có target cố định, chẳng hạn `SingletonTargetSource`.

## 9. ApplicationContext.getType(beanName): type theo tên bean

Khi **chưa có object**, bạn có thể dùng thông tin của Spring container:

```java
// Mã minh họa khi biến context đã được cung cấp.
Class<?> predictedOrExposedType = context.getType("invoiceService");
```

`ApplicationContext.getType(String)` là truy vấn metadata theo tên bean; trong các trường hợp đặc biệt kết quả có thể `null`. Nó không hoàn toàn đồng nghĩa với “lớp target cuối cùng của một instance proxy đang chạy”. Với `FactoryBean`, scoped proxy, bean được proxy hoặc bean có type prediction, class do container expose và class target runtime có thể khác nhau.

Chọn API theo mục tiêu:

| Nhu cầu | API |
| --- | --- |
| Đã có bean object và cần target class Spring AOP | `AopUtils.getTargetClass(bean)` |
| Đã có bean và cần thử đi xuyên các proxy singleton | `AopProxyUtils.ultimateTargetClass(bean)` |
| Chỉ có tên bean và muốn hỏi type container biết | `ApplicationContext.getType(beanName)` |
| Muốn đúng runtime class JVM của object | `bean.getClass()` |

## 10. Kiểm tra proxy khi advice không chạy

Tìm được target class đúng chưa đủ để kết luận AOP đang chạy. Cần kiểm tra bốn điểm sau:

1. **Object thực sự dùng:** code có gọi trên bean Spring được inject hay có đoạn `new InvoiceService()` tự tạo ra object riêng?
2. **Loại proxy:** `AopUtils.isAopProxy`, `isJdkDynamicProxy` và `isCglibProxy` cho biết hình thức proxy. Class target đã biết nhưng method không nằm trên interface JDK proxy thì client không gọi được method đó qua interface.
3. **Lựa chọn method:** kiểm tra `Pointcut`, `Advisor`, annotation hay method matcher. Proxy được tạo có thể có advisor nhưng từng method vẫn không khớp.
4. **Đường đi:** lời gọi từ bên ngoài đi qua proxy mới được advice chặn; `this.otherMethod()` trong target thường là self-invocation đi vòng ngoài proxy. CGLIB không thể override/advice method `final`; method `private` cũng không phải điểm chặn kiểu proxy thông thường.

Nếu proxy expose `Advised`, có thể xem cấu hình advisor:

```java
// Mã minh họa trong môi trường Spring AOP.
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    for (org.springframework.aop.Advisor advisor : advised.getAdvisors()) {
        System.out.println(advisor.getClass().getName());
    }
}
```

Danh sách này cho biết advisor có trong cấu hình, **không chứng minh mỗi advisor đều chạy cho method đang gọi**. Để chứng minh, cần gọi qua proxy và kiểm tra condition/trace thực tế.

## 11. Sai lầm dễ gặp

| Sai lầm | Hệ quả | Sửa thế nào |
| --- | --- | --- |
| Xem `bean.getClass()` là class nghiệp vụ | In ra lớp JDK proxy/CGLIB sinh động, lookup annotation sai | Dùng `AopUtils.getTargetClass` khi muốn target |
| Dựa vào tên có `$$SpringCGLIB$$` | Fragile theo cơ chế/tên class sinh động | Dùng `AopUtils.isCglibProxy` |
| Lấy superclass của JDK proxy | Được `java.lang.reflect.Proxy` thay vì class triển khai | Hỏi `AopUtils.getTargetClass` |
| Ép JDK proxy về concrete class | `ClassCastException` | Dùng interface expose hoặc cân nhắc class-based proxy |
| Dùng `ClassUtils.getUserClass` cho mọi proxy | Không bóc được JDK/dynamic target | Chọn API Spring AOP thích hợp |
| Giả định `ultimateTargetClass` luôn bóc tới đích | Scoped/dynamic target có thể không truy được | Chấp nhận metadata hiện có, không tùy tiện lấy instance |
| Gọi `TargetSource.getTarget()` để xem class | Có thể kích hoạt tạo/mượn object và yêu cầu release | Dùng API hỏi target type |
| Method annotation tìm trên JDK proxy class | Không thấy annotation trên target implementation | Lấy target class, dùng `getMostSpecificMethod` |
| Nghĩ nhận diện target class đồng nghĩa advice chạy | Bỏ qua pointcut và self-invocation | Kiểm tra proxy, pointcut, đường gọi và loại method |

## 12. Bài tập tự kiểm tra

**Câu 1.** Một bean trong Spring implements `InvoicePort`. `bean.getClass().getName()` hiển thị `jdk.proxy2.$Proxy...`. Làm sao tìm lớp target mà Spring biết?

**Đáp án:** kiểm tra `AopUtils.isJdkDynamicProxy(bean)` và dùng `AopUtils.getTargetClass(bean)`. Không phân tích tên string để suy ra class.

**Câu 2.** Vì sao `proxy instanceof InvoiceService` đúng với CGLIB nhưng sai với JDK proxy trong ví dụ?

**Đáp án:** CGLIB tạo subclass của `InvoiceService`; JDK proxy tạo lớp triển khai interface `InvoicePort`, không kế thừa `InvoiceService`.

**Câu 3.** Khi proxy A nằm ngoài proxy B, API nào có khả năng đi xuyên cả hai để lấy target singleton cuối cùng?

**Đáp án:** `AopProxyUtils.ultimateTargetClass(A)`, trong giới hạn có thể truy vết các singleton target không gây side effect.

**Câu 4.** Annotation `@MyMarker` đặt trên method override trong target nhưng `Method` lấy từ JDK proxy trỏ về interface. Nên làm gì?

**Đáp án:** tìm class target bằng `AopUtils.getTargetClass`, chuyển sang implementation method bằng `AopUtils.getMostSpecificMethod`, rồi tra annotation theo chiến lược Spring của ứng dụng.

**Câu 5.** `AopUtils.isAopProxy(bean) == true` có chứng minh mọi method của bean đều chạy advice không?

**Đáp án:** không. Các advisor còn kiểm tra pointcut; loại proxy và self-invocation cũng giới hạn interception.

## Tài liệu Spring chính thức

- [Spring Framework Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
- [Spring Framework Reference — Spring AOP APIs](https://docs.spring.io/spring-framework/reference/core/aop-api.html).
- [Spring Javadoc — AopUtils](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/support/AopUtils.html).
- [Spring Javadoc — AopProxyUtils](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/framework/AopProxyUtils.html).
- [Spring Javadoc — TargetSource](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/aop/TargetSource.html).
- [Spring Javadoc — ClassUtils](https://docs.spring.io/spring-framework/docs/6.2.14/javadoc-api/org/springframework/util/ClassUtils.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-inspection-003">Advised và danh sách advisor đã gắn</a>

<details>
<summary>Xem chi tiết</summary>

# Spring AOP: `Advised` và danh sách advisor đã gắn

## Mục tiêu học tập

Bài học tập trung vào khả năng **quan sát và quản lý cấu hình một Spring AOP proxy đang tồn tại** qua interface `org.springframework.aop.framework.Advised`. Sau bài học, bạn có thể:

- Nhận diện một object có phải Spring AOP proxy trước khi kiểm tra cấu hình.
- Lấy `Advisor[]` bằng `getAdvisors()`; đọc advice, pointcut và vị trí của advisor.
- Giải thích vì sao `addAdvice(...)` cũng xuất hiện dưới dạng `Advisor`.
- Thêm, xóa, thay advisor trên proxy khi được phép và kiểm tra hiệu lực với lời gọi thực.
- Phân biệt các thuộc tính `frozen`, `opaque`, `proxyTargetClass` và ranh giới self-invocation.
- Dùng thông tin advisor để debug cấu hình Spring AOP mà không nhầm “advisor có trong danh sách” với “advice chắc chắn chạy cho một phương thức”.

**Phạm vi:** đây là API quan sát và cấu hình **Spring AOP proxy**. Các khái niệm AOP chung chỉ được giải thích ngắn gọn khi phục vụ trực tiếp việc đọc Spring proxy.

## 1. `Advised` là gì?

Spring AOP tạo proxy để áp dụng các thành phần advice/pointcut quanh các lời gọi method. Interface `Advised` là một **cổng nhìn vào cấu hình advisor của proxy**, do hạ tầng Spring AOP hỗ trợ.

~~~java
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;

Object bean = /* đối tượng đã lấy từ Spring hoặc ProxyFactory */;
if (bean instanceof Advised advised) {
    Advisor[] advisors = advised.getAdvisors();
    System.out.println("Number of advisors: " + advisors.length);
}
~~~

Đây là **fragment minh họa**, chưa phải chương trình độc lập vì `bean` cần được cung cấp. Khi proxy **không ở chế độ opaque**, một Spring AOP proxy thông thường có thể được ép về `Advised`, dù proxy là JDK dynamic proxy hay CGLIB.

Phân biệt hai kiểu dữ liệu:

- `Advised` là **interface chứa khả năng quan sát và cấu hình** của proxy.
- `Advisor` là một phần tử **trong danh sách cấu hình**, cung cấp một `Advice` và, với `PointcutAdvisor`, một `Pointcut` xác định lớp/method phù hợp.

Không phải mọi object implements interface nghiệp vụ đều implements `Advised`. Một service thuần được khởi tạo bằng `new` không trở thành proxy chỉ vì trong ứng dụng có Spring AOP.

### Ba lớp thông tin cần đọc đúng

| Câu hỏi | Cần kiểm tra | Kết luận đúng |
| --- | --- | --- |
| Có đang cầm Spring AOP proxy không? | `AopUtils.isAopProxy(bean)` | Nhận diện proxy Spring AOP |
| Proxy cho phép quan sát cấu hình không? | `bean instanceof Advised` | Có thể truy cập `getAdvisors()` nếu đúng |
| Advisor đó có thể advice method này không? | Loại advisor, pointcut, proxy type, thứ tự chain và lời gọi thực | Cần kiểm tra thêm ngoài `getAdvisors()` |

`AopUtils.isAopProxy(bean)` hữu ích cho bước thứ nhất; `instanceof Advised` cần kiểm tra **riêng**. Một proxy cấu hình `opaque = true` vẫn có thể là Spring AOP proxy nhưng không expose `Advised` để caller cast.

## 2. Danh sách advisor nói lên điều gì?

Phương thức cơ bản:

~~~java
Advisor[] advisors = advised.getAdvisors();
~~~

Mảng này trả về các `Advisor` được cấu hình cho proxy, theo thứ tự advisor đang được gắn. Mỗi phần tử có thể là:

- `DefaultPointcutAdvisor` chứa một `Pointcut` và một `Advice`;
- một loại `Advisor` khác, chẳng hạn advisor được hạ tầng Spring tạo ra để cung cấp interceptor cho transaction, security, cache hoặc khía cạnh được khai báo qua @AspectJ-style (tùy cấu hình);
- advisor được Spring tạo tự động từ một advice mà bạn thêm bằng `addAdvice(...)`.

Khi đã có `Advisor advisor`, bạn có thể đọc:

~~~java
import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;

Advice advice = advisor.getAdvice();
System.out.println(advice.getClass().getName());

if (advisor instanceof PointcutAdvisor pointcutAdvisor) {
    System.out.println(pointcutAdvisor.getPointcut().getClassFilter());
    System.out.println(pointcutAdvisor.getPointcut().getMethodMatcher());
}
~~~

Đoạn trên **không chạy độc lập**; `advisor` cần lấy từ một `Advised`. Một `Advisor` không bắt buộc là `PointcutAdvisor`; ví dụ một `IntroductionAdvisor` có vai trò khác, nên phải kiểm tra `instanceof` trước khi gọi `getPointcut()`.

**Điểm quan trọng:** mảng `getAdvisors()` cung cấp cấu hình advisor, **không phải** danh sách interceptor chắc chắn đã chạy trên phương thức gần nhất. Spring AOP vẫn xét class filter, method matcher, advice adapter, khả năng method được proxy và thứ tự chain tại từng lời gọi.

### Tại sao `addAdvice` vẫn hiện ra thành `Advisor`?

Nếu gọi:

~~~java
advised.addAdvice(myInterceptor);
~~~

Spring bọc advice này bằng `DefaultPointcutAdvisor`, với pointcut khớp rộng, rồi thêm vào danh sách advisor của proxy. Đây là **fragment**, giả sử `advised` là `Advised` không bị frozen và `myInterceptor` là một `MethodInterceptor` hợp lệ.

Vì vậy khi gọi `getAdvisors()` sau đó, bạn vẫn thấy **một `Advisor`**, không thấy riêng một object `MethodInterceptor` ở cấp phần tử mảng. Lấy advice gốc qua `advisor.getAdvice()`.

## 3. API quan trọng của `Advised`

Những thao tác thường dùng:

| API | Mục đích | Lưu ý |
| --- | --- | --- |
| `getAdvisors()` | Lấy danh sách advisor đang gắn | Đọc cấu hình, không khẳng định advisor chạy trên mọi method |
| `indexOf(Advisor advisor)` | Tìm vị trí advisor | Trả `-1` nếu advisor không hiện diện |
| `addAdvice(Advice advice)` | Thêm một advice và để Spring tạo advisor bọc nó | Pointcut mặc định khớp rộng trong phạm vi proxy |
| `addAdvice(int pos, Advice advice)` | Chèn advice ở một vị trí | Thứ tự có thể đổi hành vi |
| `addAdvisor(Advisor advisor)` | Thêm advisor vào cuối chain | Giữ pointcut của advisor truyền vào |
| `addAdvisor(int pos, Advisor advisor)` | Chèn advisor ở vị trí chỉ định | Vị trí `0` là đầu chain |
| `removeAdvisor(Advisor advisor)` | Xóa advisor được chỉ định | Trả `true` khi đã xóa |
| `removeAdvisor(int index)` | Xóa advisor theo vị trí | Index phải hợp lệ |
| `replaceAdvisor(old, replacement)` | Thay advisor hiện có | Trả `false` nếu không tìm thấy advisor cũ |
| `removeAdvice(Advice advice)` | Xóa advisor chứa advice đó | Dùng khi bạn chỉ giữ reference đến advice |
| `isFrozen()` | Cho biết danh sách advice/advisor có bị khóa sửa | `true` thì thao tác sửa có thể ném `AopConfigException` |

Các phương thức khác của `Advised` giúp kiểm tra cấu hình proxy như `getProxiedInterfaces()`, `isInterfaceProxied(...)`, `isProxyTargetClass()` và `getTargetSource()`. Chúng trả lời những câu hỏi khác nhau: **proxy expose interface nào**, có ép class proxy không và target đến từ đâu. Không nên dùng `getTargetSource().getTarget()` rồi gọi business method trực tiếp để “thử advice” vì lời gọi trực tiếp có thể bỏ qua proxy và còn ảnh hưởng vòng đời target source.

### Thứ tự advisor

`addAdvisor(advisor)` nối vào cuối danh sách; `addAdvisor(0, advisor)` đưa vào đầu. Spring AOP dùng interceptor chain với trình tự tương ứng, nhưng nhiều advice có logic trước/sau `proceed()` nên **thứ tự thông báo hoàn tất thường đảo chiều**.

Ví dụ khi chain là:

~~~text
LoggingAround -> ValidationAround -> target
~~~

Nếu mỗi interceptor in trước và sau `proceed()`, caller thấy:

~~~text
Logging before
Validation before
target
Validation after
Logging after
~~~

Đây là sơ đồ minh họa trật tự gọi, không phải output của chương trình ví dụ phía dưới. Khi dùng transaction hoặc retry advisor, thứ tự chain có thể quyết định advice có quan sát lời gọi thất bại, lặp lại hoặc chưa commit hay không. Vì vậy, **vị trí advisor là thông tin có ý nghĩa**, nhưng danh sách advisor một mình chưa chứng minh kết quả cuối.

## 4. Ví dụ Java 17+ chạy được: quan sát, thêm và gỡ advisor

Dựng `ProxyFactory` cho một service có interface, rồi:

1. Xác nhận object trả về là JDK dynamic proxy và implement `Advised`.
2. Quan sát advisor ban đầu.
3. Gọi method để thấy advice hiện tại.
4. Thêm một `MethodBeforeAdvice` chỉ áp dụng cho `greet`.
5. Gọi lại qua **cùng proxy** để thấy chain đã thay đổi.
6. Gỡ advisor trace và xác minh lời gọi sau đó thay đổi theo.

Ví dụ dùng Spring Framework 6.2.x, Maven và JDK 17+.

### 4.1. Cấu trúc thư mục

~~~text
inspect-advised/
├── pom.xml
└── src/main/java/example/AdvisedDemo.java
~~~

### 4.2. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>inspect-advised</artifactId>
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
                    <mainClass>example.AdvisedDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

### 4.3. `src/main/java/example/AdvisedDemo.java`

~~~java
package example;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class AdvisedDemo {

    public interface Greeter {
        String greet(String name);
        String status();
    }

    public static class GreeterImpl implements Greeter {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }

        @Override
        public String status() {
            return "ready";
        }
    }

    public static void main(String[] args) {
        MethodInterceptor trace = invocation -> {
            System.out.println("TRACE " + invocation.getMethod().getName());
            return invocation.proceed();
        };

        DefaultPointcutAdvisor traceAdvisor =
                new DefaultPointcutAdvisor(Pointcut.TRUE, trace);

        ProxyFactory factory = new ProxyFactory(new GreeterImpl());
        factory.setInterfaces(Greeter.class);
        factory.setProxyTargetClass(false);
        factory.addAdvisor(traceAdvisor);

        Object object = factory.getProxy();
        System.out.println("Spring AOP proxy: " + AopUtils.isAopProxy(object));
        System.out.println("JDK proxy: " + AopUtils.isJdkDynamicProxy(object));
        System.out.println("Exposes Advised: " + (object instanceof Advised));

        Greeter proxy = (Greeter) object;
        Advised advised = (Advised) object;

        System.out.println("Initial advisors: " + advised.getAdvisors().length);
        System.out.println("Trace position: " + advised.indexOf(traceAdvisor));
        System.out.println("Result: " + proxy.greet("Lan"));

        NameMatchMethodPointcut onlyGreet = new NameMatchMethodPointcut();
        onlyGreet.setMappedName("greet");

        MethodBeforeAdvice verify = (method, arguments, target) ->
                System.out.println("BEFORE " + method.getName());

        DefaultPointcutAdvisor greetAdvisor =
                new DefaultPointcutAdvisor(onlyGreet, verify);

        advised.addAdvisor(greetAdvisor);

        System.out.println("After add: " + advised.getAdvisors().length);
        System.out.println("Result: " + proxy.greet("Minh"));
        System.out.println("Status: " + proxy.status());

        System.out.println("Removed trace: " +
                advised.removeAdvisor(traceAdvisor));
        System.out.println("After remove: " + advised.getAdvisors().length);
        System.out.println("Result: " + proxy.greet("An"));
        System.out.println("Status: " + proxy.status());
        System.out.println("Frozen: " + advised.isFrozen());
    }
}
~~~

### 4.4. Chạy chương trình

Trong thư mục `inspect-advised` chứa `pom.xml`:

~~~bash
mvn -q compile exec:java
~~~

Phần stdout do chương trình tạo ra:

~~~text
Spring AOP proxy: true
JDK proxy: true
Exposes Advised: true
Initial advisors: 1
Trace position: 0
TRACE greet
Result: Hello Lan
After add: 2
TRACE greet
BEFORE greet
Result: Hello Minh
TRACE status
Status: ready
Removed trace: true
After remove: 1
BEFORE greet
Result: Hello An
Status: ready
Frozen: false
~~~

Maven có thể in thông báo riêng trong môi trường chạy; các dòng trên là `System.out.println` do code ví dụ sinh ra.

### 4.5. Điều gì đã xảy ra?

**Bước 1 — cấu hình ban đầu.** `ProxyFactory` bao một `GreeterImpl`, expose interface `Greeter`, thêm `traceAdvisor` dùng `Pointcut.TRUE`. Khi lấy proxy, `AopUtils` xác nhận Spring AOP/JDK proxy và `instanceof Advised` đúng. Danh sách ban đầu có **một** advisor; `indexOf(traceAdvisor)` trả `0`.

**Bước 2 — gọi qua proxy.** `proxy.greet("Lan")` đi qua `trace` vì `Pointcut.TRUE` khớp method được xét trên proxy. Caller vẫn nhận `Hello Lan`, và interceptor in `TRACE greet` trước đó.

**Bước 3 — thêm advisor chọn lọc.** `greetAdvisor` kết hợp `NameMatchMethodPointcut` chỉ khớp tên `greet` với `MethodBeforeAdvice`. `advised.addAdvisor(greetAdvisor)` gắn thêm advisor vào **proxy đang tồn tại**. Lời gọi `greet` tiếp theo đi qua trace rồi before advice; `status` chỉ qua trace vì pointcut `onlyGreet` không khớp `status`.

**Bước 4 — gỡ trace.** `removeAdvisor(traceAdvisor)` trả `true`, danh sách còn một advisor. `greet("An")` vẫn được `BEFORE` quan sát, trong khi `status()` không còn dòng `TRACE`. Biến `proxy` giữ nguyên reference; việc điều chỉnh chain hợp lệ được Spring AOP áp dụng cho các lời gọi tiếp theo.

**Bước 5 — cấu hình sửa được.** `isFrozen() == false` vì `ProxyFactory` trong ví dụ không khóa thay đổi advisor. Không nên suy ra mọi bean Spring ở production đều có thể sửa theo cách này; cần kiểm tra trạng thái và chính sách cấu hình thực tế.

Trong ví dụ, `getAdvisors()` trả đúng hai phần tử sau `addAdvisor(...)`, nhưng chỉ method `greet` thỏa pointcut của phần tử thứ hai. Đây là minh chứng vì sao **số advisor không phải số advice chạy trên mỗi method**.

## 5. Cách quan sát đầy đủ mà không sửa cấu hình

Trong ứng dụng thật, thường chỉ cần đọc:

~~~java
import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;

public static void describeProxy(Object bean) {
    System.out.println("Spring AOP proxy: " + AopUtils.isAopProxy(bean));

    if (!(bean instanceof Advised advised)) {
        System.out.println("Advised API unavailable");
        return;
    }

    Advisor[] advisors = advised.getAdvisors();
    System.out.println("Advisor count: " + advisors.length);

    for (int i = 0; i < advisors.length; i++) {
        Advisor advisor = advisors[i];
        System.out.println("#" + i + " advisor="
                + advisor.getClass().getName());
        System.out.println("  advice="
                + advisor.getAdvice().getClass().getName());

        if (advisor instanceof PointcutAdvisor p) {
            System.out.println("  pointcut="
                    + p.getPointcut().getClass().getName());
        }
    }

    System.out.println("Frozen: " + advised.isFrozen());
}
~~~

Đây là **phương thức Java độc lập ở mức snippet**, phải đặt trong một class và dự án đã có `spring-aop` mới chạy được. Nó cố ý dùng tên class thay vì gọi `toString()` của toàn bộ advisor, vì `toString()` có thể chứa dữ liệu cấu hình hoặc chi tiết không ổn định giữa các phiên bản.

Để phân tích kỹ hơn, đọc `p.getPointcut().getClassFilter()` và `p.getPointcut().getMethodMatcher()`. Tuy nhiên, một matcher có thể là dynamic matcher dựa vào tham số runtime; không có một phép xem mảng advisor nào thay thế được kiểm thử đường gọi thực tế.

Nếu không có `Advised`, có thể xảy ra ít nhất ba tình huống: object không phải Spring AOP proxy; proxy có cấu hình `opaque`; hoặc object là proxy của cơ chế khác. Hãy kiểm tra `AopUtils.isAopProxy` và nguồn tạo object, không vội kết luận “Spring AOP không hoạt động”.

## 6. `frozen`: khóa thay đổi danh sách advisor

Trong Spring AOP, cấu hình `frozen` giúp ngăn việc bổ sung, loại bỏ hoặc thay đổi advice/advisor qua API `Advised`. `Advised.isFrozen()` cho biết trạng thái khóa.

Có thể đặt cờ trước khi tạo proxy:

~~~java
ProxyFactory factory = new ProxyFactory(new GreeterImpl());
factory.setInterfaces(Greeter.class);
factory.addAdvisor(traceAdvisor);
factory.setFrozen(true);

Greeter lockedProxy = (Greeter) factory.getProxy();
Advised locked = (Advised) lockedProxy;

System.out.println(locked.isFrozen()); // true
// locked.removeAdvisor(traceAdvisor) sẽ ném AopConfigException
~~~

Đây là **fragment dùng các kiểu `Greeter`, `GreeterImpl`, `traceAdvisor` từ chương trình trước**, không chạy độc lập khi tách riêng.

Việc `setFrozen(true)` cần được thực hiện **trên cấu hình proxy/factory vào thời điểm phù hợp trước khi sử dụng**. Khi muốn ổn định advisor chain ở production, frozen giúp tránh việc caller tình cờ tháo logging/security/transaction advice. Song đây không phải cơ chế bảo mật thay cho phân quyền quản trị ứng dụng.

**Phân biệt:** `frozen` khóa **thay đổi advice/advisor**, không có nghĩa là đối tượng target trở nên immutable, không cho mutate field hoặc không cho thực thi business method.

## 7. `opaque`: proxy không expose `Advised` ra caller

Spring AOP proxy thông thường hỗ trợ `Advised` để caller có thể nhìn và quản lý cấu hình. Nếu muốn ẩn khả năng này khỏi interface proxy, cấu hình `opaque`:

~~~java
ProxyFactory factory = new ProxyFactory(new GreeterImpl());
factory.setInterfaces(Greeter.class);
factory.addAdvisor(traceAdvisor);
factory.setOpaque(true);

Object proxy = factory.getProxy();

System.out.println(proxy instanceof Greeter); // true
System.out.println(proxy instanceof Advised); // false
~~~

Đây là **fragment dùng các kiểu `Greeter`, `GreeterImpl`, `traceAdvisor` từ ví dụ**, không chạy độc lập. Khi opaque, caller vẫn gọi business API qua proxy; điều thay đổi là không thể cast sang `Advised` để truy vấn hay sửa cấu hình theo interface đó. Đó là khác biệt với frozen:

| Trạng thái | `instanceof Advised` | `getAdvisors()` từ caller | Sửa qua `Advised` từ caller |
| --- | --- | --- | --- |
| Mặc định, không frozen | Thường `true` | Có | Có thể, nếu cấu hình cho phép |
| `frozen = true`, không opaque | `true` | Có | Bị chặn bởi `AopConfigException` |
| `opaque = true` | `false` | Không truy cập qua proxy | Không truy cập qua proxy |

**Opaque không xóa các advisor đang áp dụng.** Advice vẫn có thể chạy, chỉ là interface cấu hình được giấu khỏi reference proxy. Đừng coi opaque là ranh giới bảo mật chống code có toàn quyền truy cập factory/bean definition; nó là lựa chọn expose API.

## 8. JDK proxy, CGLIB và `Advised`

`Advised` được Spring AOP hỗ trợ với cả hai kiểu proxy, nếu không opaque:

- **JDK dynamic proxy:** thường proxy một hoặc nhiều business interface. Spring bổ sung các interface hạ tầng cần thiết (trong đó có `Advised`) vào proxy. Caller cast sang interface nghiệp vụ; không được cast sang implementation nếu đó là JDK proxy.
- **CGLIB proxy:** là subclass của target class và cũng có thể cung cấp interface `Advised`, tùy cấu hình. Caller có thể cast sang target class khi target có thể được subclass.

Để xác nhận loại proxy:

~~~java
import org.springframework.aop.support.AopUtils;

System.out.println(AopUtils.isAopProxy(bean));
System.out.println(AopUtils.isJdkDynamicProxy(bean));
System.out.println(AopUtils.isCglibProxy(bean));
~~~

Đoạn trên là **fragment** với biến `bean` đã có; không nên nhận diện loại proxy bằng việc so tên class chứa `$Proxy` hoặc `$$SpringCGLIB$$` vì tên sinh ra là chi tiết implementation.

**Ranh giới quan trọng:** Có `Advisor` trong `getAdvisors()` không khiến Spring AOP advice được constructor, method private/final trên CGLIB hay self-invocation. Với JDK dynamic proxy, method phải nằm trên interface proxy và được gọi qua reference proxy. Với CGLIB, method cần có thể được intercept bằng subclass proxy. Lời gọi `this.otherMethod()` bên trong target thường đi thẳng từ target, bỏ qua proxy.

## 9. Danh sách advisor có phải thứ tự thực thi thực tế không?

Danh sách `getAdvisors()` thể hiện thứ tự advisor cấu hình, nhưng việc chạy trên một method cụ thể còn phụ thuộc:

1. **Tập method thực sự được proxy:** JDK interface hay CGLIB class proxy.
2. **`PointcutAdvisor`:** class filter và method matcher (có thể static hoặc runtime).
3. **Advice adapter:** Spring chuyển `MethodBeforeAdvice`, `AfterReturningAdvice`, `ThrowsAdvice` thành `MethodInterceptor` khi cần.
4. **Thứ tự các interceptor:** interceptor chạy trước/sau `proceed()` theo logic riêng; có thể chặn hoặc lặp lời gọi.
5. **Kết quả/ngoại lệ:** after-returning advice chỉ chạy nếu phần bên trong hoàn thành bình thường; throws advice có thể chỉ nhận exception phù hợp.

Ví dụ có hai advisor:

~~~text
0 -> DefaultPointcutAdvisor(Pointcut.TRUE, TraceInterceptor)
1 -> DefaultPointcutAdvisor(name="greet", BeforeAdvice)
~~~

Với `greet(...)`, cả hai có thể tham gia. Với `status()`, chỉ trace phù hợp. Nếu trước đó có interceptor quyết định không gọi `proceed()`, target chưa chắc được thực thi, dù `getAdvisors()` vẫn hiển thị đầy đủ.

Nếu dùng Spring AOP tạo advisor cho transaction, security hoặc caching, việc đổi thứ tự có thể đổi kết quả hoặc quan sát lỗi. Vì vậy không nên tự động sắp xếp advisor trong production chỉ để “đẹp” danh sách. Hãy kiểm tra semantics của từng advisor và dùng cấu hình thứ tự chính thức của Spring khi cần.

## 10. Thay đổi advisor lúc runtime: giới hạn thực tế

Spring cho phép thêm hoặc xóa nhiều advisor sau khi proxy đã được tạo, nếu `frozen = false`. Đây là tính năng hữu ích trong **kiểm thử và chẩn đoán**, nhưng cần hiểu giới hạn:

- **Introduction advisor:** không thể tùy tiện thêm/bớt trên một proxy đã được tạo khi nó làm thay đổi tập interface mà proxy expose. Trong trường hợp này cần xây proxy mới từ factory/cấu hình thích hợp.
- **Thứ tự và concurrency:** sửa chain trên một proxy đang phục vụ nhiều request có thể khiến các lời gọi quan sát các cấu hình khác nhau theo thời điểm. Tránh dựa vào mutation runtime như một cơ chế điều khiển nghiệp vụ.
- **Caching:** Spring có thể cache interceptor chain theo method; thay đổi advisor hợp lệ được hạ tầng hỗ trợ cập nhật cho những lần gọi sau. Không nên tự mutate mảng `Advisor[]` nhận từ `getAdvisors()` để thay cấu hình; dùng `addAdvisor`, `removeAdvisor` hoặc `replaceAdvisor`.
- **Advice singleton và trạng thái:** cùng một advice object có thể được chia sẻ cho nhiều invocation. Không lưu dữ liệu per-request trong field mutable chưa được bảo vệ chỉ để tiện debug.
- **Ranh giới bean:** thay đổi một proxy không có nghĩa mọi proxy khác cùng interface hoặc cùng target class tự thay đổi theo. Mức chia sẻ phụ thuộc vào cách factory/container tạo và quản lý proxy.
- **Tác động ngoài ý muốn:** gỡ nhầm advisor liên quan đến security, transaction hay retry có thể làm đổi hợp đồng ứng dụng. Ưu tiên quan sát chỉ đọc trong production.

Khi cần kiểm thử advisor chain động, dùng proxy riêng cho test, assert `getAdvisors().length` trước/sau và **gọi lại** qua cùng proxy để xác nhận hiệu lực.

## 11. Checklist gỡ lỗi advisor không chạy

1. **Có đúng object không?** Đối tượng đang cầm là bean proxy từ Spring context hay object target được tạo bằng `new`? Xác nhận bằng `AopUtils.isAopProxy`.
2. **Có thể đọc `Advised` không?** Dùng `instanceof Advised`; nếu không, kiểm tra `opaque` hoặc cơ chế proxy khác.
3. **Advisor có tồn tại không?** Duyệt `advised.getAdvisors()`, xem advice class và pointcut của `PointcutAdvisor`; xác định đúng instance thay vì chỉ so tên.
4. **Method có match không?** Kiểm tra class filter, method matcher, annotation/signature, dynamic conditions.
5. **Lời gọi có đi qua proxy không?** Kiểm tra self-invocation, gọi target trực tiếp, method final/private và JDK interface.
6. **Có interceptor phía trước chặn không?** Có advice ném ngoại lệ hoặc không gọi `proceed()` không?
7. **Có thứ tự đặc biệt không?** Transaction, retry, cache, security và advice logging có thể bao nhau theo thứ tự khác dự kiến.
8. **Cấu hình có bị đóng băng không?** `isFrozen()` có trả `true` khi bạn cố thêm/xóa advisor?
9. **Có mutation ngoài luồng không?** Nếu tests hoặc runtime thay đổi advisor, kiểm tra thời điểm và ảnh hưởng tới những invocation đang chạy.
10. **Đọc lại sau thao tác:** gọi `getAdvisors()` và một lời gọi thực để xác nhận cấu hình kết quả, thay vì chỉ dựa vào giá trị trả về của một API.

## 12. Những lỗi thường gặp

**“Proxy có một advisor nên phương thức nào cũng được advice.”** Sai: advisor có thể mang pointcut chọn lọc; phương thức không match sẽ không gọi advice.

**“`getAdvisors()` trả trực tiếp danh sách advice.”** Sai: phần tử là `Advisor`. Đọc advice qua `getAdvice()`; nếu cần pointcut phải kiểm tra `instanceof PointcutAdvisor`.

**“Sau `addAdvice(...)` không thấy advice vì danh sách chỉ chứa advisor.”** Spring tự bọc advice trong advisor; đó là hành vi đúng.

**“Cast sang `Advised` là an toàn với mọi object Spring.”** Sai: bean không proxy, proxy opaque hoặc proxy khác có thể không implements `Advised`. Dùng `instanceof`.

**“Nếu `isFrozen()` đúng, không còn ai gọi service được.”** Sai: frozen chỉ liên quan khả năng sửa cấu hình AOP advisor/advice; business method vẫn được gọi bình thường.

**“`opaque` vô hiệu hóa advice.”** Sai: opaque chỉ ẩn interface cấu hình khỏi caller; logic advice vẫn gắn.

**“Gỡ một advisor qua `removeAdvisor` luôn vô hại.”** Sai: có thể thay đổi bảo đảm transaction, security, retry hoặc audit. Nên kiểm tra phạm vi, thứ tự và chạy trên proxy thử nghiệm.

**“Sau khi sửa mảng `getAdvisors()`, chain sẽ đổi.”** Không được dựa vào điều đó. Dùng API thay đổi cấu hình của `Advised` để Spring xử lý cập nhật nội bộ.

**“Số lần method chạy bằng số advisor.”** Sai: các advisor được match riêng, interceptor có thể tiếp tục, chặn, retry hoặc chỉ quan sát sự kiện, và target thường không chạy một lần cho mỗi advisor.

## 13. Bài tập thực hành

Từ `AdvisedDemo` ở mục 4:

1. Đổi `onlyGreet.setMappedName("greet")` thành `"status"`. Dự đoán output ở lần gọi `greet` và `status` trước/sau khi gỡ trace.
2. Thay `advised.addAdvisor(greetAdvisor)` bằng `advised.addAdvisor(0, greetAdvisor)`. So sánh thứ tự `BEFORE` và `TRACE` khi `greet` chạy.
3. Thêm một `AfterReturningAdvice` chỉ match `greet`. Quan sát nó chỉ ghi nhận kết quả ở đường trả về bình thường và xác định vị trí advisor bằng `indexOf`.
4. Dùng `factory.setFrozen(true)` trước `getProxy()` rồi thử `advised.addAdvisor(greetAdvisor)`. Kiểm tra `isFrozen()` và ngoại lệ.
5. Dùng `factory.setOpaque(true)`, xác nhận `AopUtils.isAopProxy(object)` vẫn đúng nhưng `object instanceof Advised` sai.
6. Tạo thêm một proxy khác từ một `ProxyFactory` độc lập. Thử thêm advisor vào proxy đầu, xác minh proxy thứ hai có tự thay đổi hay không.

## 14. Tài liệu tham khảo chính thức của Spring

- [Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advised.html): đọc và thay đổi danh sách advisor, `frozen`, thao tác `Advised`.
- [The Advisor API in Spring](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advisor.html): vai trò `Advisor` và `DefaultPointcutAdvisor`.
- [Creating AOP Proxies Programmatically with the ProxyFactory](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html): thêm advice/advisor và lấy proxy.
- [Pointcut API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pointcuts.html): class filter và method matcher.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/6.2/core/aop/proxying.html): JDK dynamic proxy, CGLIB, self-invocation và giới hạn của proxy.
- [Advised Javadoc](https://docs.spring.io/spring-framework/docs/6.2.19/javadoc-api/org/springframework/aop/framework/Advised.html): chữ ký và hợp đồng của interface.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-inspection-004">Đọc TargetSource từ proxy khi API cho phép</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc TargetSource từ proxy khi API cho phép trong Spring AOP

## 1. Mục tiêu và phạm vi

Một đối tượng dịch vụ trong Spring có thể là **Spring AOP proxy** thay vì instance target thật. Khi cần chẩn đoán hoặc kiểm thử cách proxy được cấu hình, Spring cung cấp API để quan sát `TargetSource` mà proxy sử dụng. Tuy nhiên, **không phải proxy nào cũng cho phép truy cập cấu hình này qua public interface**, và việc lấy một target instance trực tiếp có thể ảnh hưởng vòng đời của target động.

Bài học này tập trung vào **Spring AOP**:

- Xác định đối tượng có phải AOP proxy bằng `AopUtils.isAopProxy(...)`.
- Kiểm tra có thể xem cấu hình qua `org.springframework.aop.framework.Advised` hay không.
- Lấy `org.springframework.aop.TargetSource` bằng `Advised.getTargetSource()`.
- Phân biệt việc **đọc metadata** (`getTargetClass()`, `isStatic()`) và việc **mượn target thật** (`getTarget()` / `releaseTarget(...)`).
- Hiểu `SingletonTargetSource`, target source động, proxy opaque và những giới hạn trong ứng dụng Spring thực tế.
- Dùng ví dụ Java 17+ chứng minh việc quan sát proxy không phải gọi method nghiệp vụ trên target trực tiếp.

Bài không bàn về reflection hay proxy nói chung; các cơ chế và giới hạn dưới đây thuộc API Spring AOP.

## 2. Quan hệ giữa proxy, Advised và TargetSource

Một lời gọi qua Spring AOP proxy có thể đi theo mô hình:

```text
Client
  |
  v
Spring AOP proxy
  |-- Advised: cấu hình advisor, advice, interface, TargetSource
  |
  |-- MethodInterceptor / chuỗi advice phù hợp
  |
  v
TargetSource.getTarget()
  |
  v
target instance: thực hiện method
  |
  v
TargetSource.releaseTarget(target) khi cần
```

Sơ đồ mô tả ý nghĩa, không bắt buộc mọi proxy đều có cùng chi tiết tối ưu hóa. Ví dụ `SingletonTargetSource` có thể được Spring cache target vì đó là nguồn tĩnh; target source động phải quản lý việc lấy/trả instance phù hợp với từng invocation.

**`TargetSource` không đồng nhất với target**:

- `TargetSource` là đối tượng biết cách cung cấp target cho Spring AOP.
- Target là instance thực hiện method nghiệp vụ.
- Proxy là đối tượng mà client nắm giữ để đi qua advice/interceptor chain.
- `Advised` là interface cho phép xem hoặc thay đổi một phần cấu hình AOP của proxy **nếu proxy expose interface đó**.

Một target source có thể luôn trả cùng target, thay đổi target theo thời gian, hoặc lấy target từ pool. Vì vậy, chỉ nhìn class của proxy không đủ suy ra target hiện thời.

### API chính

`org.springframework.aop.framework.Advised` có phương thức:

```java
TargetSource getTargetSource();
```

`org.springframework.aop.TargetSource` cung cấp hợp đồng:

```java
Class<?> getTargetClass();
boolean isStatic();
Object getTarget() throws Exception;
void releaseTarget(Object target) throws Exception;
```

Trong Spring 6.x, `isStatic()` và `releaseTarget(...)` có default implementation, nhưng các lớp target source vẫn có thể override chúng để quản lý tài nguyên.

| API | Có thể dùng để | Điều cần chú ý |
| --- | --- | --- |
| `Advised.getTargetSource()` | Lấy đối tượng quản lý target của proxy | Chỉ khi cast được sang `Advised` |
| `TargetSource.getTargetClass()` | Xem kiểu target dự kiến | Có thể trả `null` |
| `TargetSource.isStatic()` | Biết source luôn trả cùng một target hay không | Không có nghĩa target immutable theo nghĩa Java |
| `TargetSource.getTarget()` | Lấy target instance | Có thể trả `null`, ném exception, hoặc mượn object đang được quản lý |
| `TargetSource.releaseTarget(target)` | Trả target vừa lấy cho source | Quan trọng với pooling/dynamic target source |
| `AopUtils.isAopProxy(object)` | Xác định Spring AOP proxy | Không đảm bảo `object instanceof Advised` |

Không nên gọi `getTarget()` như một thao tác “đọc metadata” chung. Với pool, mỗi lần gọi có thể checkout target; nếu không release, pool có thể cạn. Với source prototype, mỗi lần gọi có thể tạo một target mới. Chỉ cần hiểu class/static-flag thì ưu tiên `getTargetClass()` và `isStatic()`.

## 3. Kiểm tra điều kiện “khi API cho phép”

Không được ép kiểu bừa một service bean thành `Advised`. Luồng kiểm tra nên là:

```java
Object candidate = /* bean hoặc object đã có trong ứng dụng */;
if (AopUtils.isAopProxy(candidate)) {
    if (candidate instanceof Advised advised) {
        TargetSource source = advised.getTargetSource();
        // Đọc getTargetClass(), isStatic() tại đây.
    } else {
        // Proxy tồn tại nhưng không expose Advised.
    }
} else {
    // Không xác nhận đây là Spring AOP proxy.
}
```

Đây là **mảnh mã minh họa**, cần object `candidate` cùng các import từ `org.springframework.aop` và `org.springframework.aop.support`. Ví dụ chạy độc lập ở mục 5 khai báo đầy đủ.

### 3.1. Vì sao Spring AOP proxy có thể không expose Advised?

Spring AOP proxy thường có thể được kiểm tra qua `Advised`. Tuy nhiên, cấu hình `opaque=true` (ví dụ `ProxyFactory.setOpaque(true)`) yêu cầu **không expose interface cấu hình `Advised` trên proxy**. Khi ấy:

- `AopUtils.isAopProxy(bean)` có thể vẫn trả `true`.
- `bean instanceof Advised` trả `false`.
- Ép kiểu `(Advised) bean` sẽ thất bại; không được dùng cast mù.
- Ứng dụng vẫn có thể gọi method nghiệp vụ qua proxy và nhận advice như bình thường.
- Người tạo proxy có thể nắm `ProxyFactory`/cấu hình gốc để đọc `getTargetSource()`, nhưng client chỉ được cấp bean proxy không nhất thiết truy cập được.

Ngoài ra, object nhận được có thể không phải Spring AOP proxy, hoặc là proxy từ cơ chế khác. Spring AOP API không đảm bảo phân tích được mọi loại proxy.

### 3.2. Đọc từ Spring container

Khi có `ApplicationContext context` và một service bean tên `orderService`:

```java
Object bean = context.getBean("orderService");
if (AopUtils.isAopProxy(bean) && bean instanceof Advised advised) {
    TargetSource source = advised.getTargetSource();
    Class<?> type = source.getTargetClass();
    System.out.println(type == null ? "unknown" : type.getName());
}
```

Đoạn trên chỉ minh họa và giả định bean `orderService` tồn tại. Trong ứng dụng, bean được Spring auto-proxy có thể có thêm advisor của transaction/security. Việc đọc `TargetSource` giúp xem cấu hình của **proxy thực tế đã nhận từ container**; không được lấy bean target bằng tên riêng rồi giả định đó chính là object client đang gọi.

## 4. Static và dynamic TargetSource khác nhau thế nào?

### 4.1. Static TargetSource

Với cấu hình thường gặp:

```java
TaskServiceImpl target = new TaskServiceImpl();
ProxyFactory factory = new ProxyFactory(target);
TaskService proxy = (TaskService) factory.getProxy();
```

Spring có thể dùng `SingletonTargetSource` để bọc target. `SingletonTargetSource.isStatic()` trả `true` và `getTarget()` trả đúng target đã gắn, không cần thao tác release tài nguyên. Đoạn trên là **mảnh mã**, các class `TaskService` và `TaskServiceImpl` được khai báo trong ví dụ hoàn chỉnh bên dưới.

**Static ở đây là đặc tính nguồn target**: cùng target object được trả về, chứ không tuyên bố mọi trường của target bất biến hay không có concurrency.

### 4.2. Dynamic TargetSource

Dynamic source có `isStatic() == false`. Nó có thể:

- Cấp target khác nhau cho mỗi invocation, như một triển khai prototype.
- Checkout/return target từ một pool.
- Theo dõi hoặc chọn target theo chính sách runtime.

Nếu chỉ muốn biết target type, có thể gọi `getTargetClass()` mà không checkout target. Không nên lấy một target từ dynamic source chỉ để in `target.getClass()`; thao tác này có thể tiêu tốn tài nguyên và không đảm bảo target đó là target của **lần gọi nghiệp vụ tiếp theo**.

Nếu thật sự phải lấy target thủ công trong một tác vụ kiểm thử/diagnostic có kiểm soát, cặp thao tác tối thiểu là:

```java
Object borrowed = source.getTarget();
try {
    if (borrowed != null) {
        // Chỉ đọc trạng thái cần thiết khi bảo đảm an toàn truy cập.
    }
} finally {
    if (borrowed != null && !source.isStatic()) {
        source.releaseTarget(borrowed);
    }
}
```

Đây là **mảnh mã minh họa**, giả định đã có biến `TargetSource source` và method chứa mã khai báo `throws Exception` hoặc xử lý ngoại lệ. Đối với source động, phải trả đúng object nhận được; tránh giữ tham chiếu sau khi release, nhất là khi object có thể quay lại pool. Với source tĩnh, không cần gọi `releaseTarget(...)`. `getTarget()` cũng có thể là `null` nên đừng gọi method trên target mà chưa kiểm tra.

**Ưu tiên trong code ứng dụng:** dùng **proxy** để gọi nghiệp vụ. Việc tự acquire/release target chỉ dành cho nhu cầu công cụ chẩn đoán, kiểm thử hoặc hạ tầng có hiểu biết về vòng đời.

### 4.3. Hot-swappable TargetSource: “static” không đồng nghĩa “không đổi cấu hình”

`HotSwappableTargetSource` quản lý một target có thể thay thế trong khi proxy vẫn giữ nguyên identity. Kiểu source này có thể báo `isStatic()==false` vì target có thể được đổi, dù tại một thời điểm nhiều lời gọi nhận cùng object. Không kết luận “động” đồng nghĩa “mỗi lần chắc chắn có object mới”.

Với một proxy dùng hot-swappable source, `getTargetClass()` cho bạn thông tin kiểu target; nó không phải cam kết identity của instance ở thời điểm gọi. Nếu hệ thống đang swap target đồng thời, giá trị nhìn thấy lúc inspect và lúc invoke có thể khác.

## 5. Ví dụ chạy được: đọc metadata, singleton target và target động

Ví dụ sử dụng `ProxyFactory` tạo ba proxy:

1. Proxy bình thường với `SingletonTargetSource`: đọc `TargetSource` và xác minh target identity.
2. Proxy với custom **dynamic** `TargetSource`: chỉ inspect metadata; gọi nghiệp vụ qua proxy rồi kiểm tra số lượt Spring acquire/release target.
3. Proxy có `opaque=true`: vẫn là Spring AOP proxy nhưng client không thể cast sang `Advised`.

Cả ba đều khai báo một interface Java rõ ràng để ví dụ không phụ thuộc tên class proxy sinh ra trong JVM.

**`settings.gradle`**:

```groovy
rootProject.name = 'spring-aop-targetsource-read'
```

**`build.gradle`** (Java 17+, Spring Framework 6.1):

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
    mainClass = 'example.TargetSourceDemo'
}
```

**`src/main/java/example/TargetSourceDemo.java`**:

```java
package example;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.target.SingletonTargetSource;

public final class TargetSourceDemo {

    public interface Greeting {
        String hello(String name);
    }

    static final class GreetingService implements Greeting {
        private final String id;

        GreetingService(String id) {
            this.id = id;
        }

        @Override
        public String hello(String name) {
            return id + ":" + name;
        }
    }

    // Chỉ dùng cho ví dụ đơn luồng để đếm acquire/release.
    static final class CountingTargetSource implements TargetSource {
        int acquisitions;
        int releases;

        @Override
        public Class<?> getTargetClass() {
            return GreetingService.class;
        }

        @Override
        public boolean isStatic() {
            return false;
        }

        @Override
        public Object getTarget() {
            acquisitions++;
            return new GreetingService("dynamic-" + acquisitions);
        }

        @Override
        public void releaseTarget(Object target) {
            releases++;
        }
    }

    private static void inspect(String label, Object candidate) {
        System.out.println("=== " + label + " ===");
        System.out.println("aopProxy=" + AopUtils.isAopProxy(candidate));

        if (!(candidate instanceof Advised advised)) {
            System.out.println("advised=false");
            return;
        }

        TargetSource source = advised.getTargetSource();
        Class<?> targetClass = source.getTargetClass();
        System.out.println("advised=true");
        System.out.println("sourceType=" + source.getClass().getSimpleName());
        System.out.println("isStatic=" + source.isStatic());
        System.out.println("targetType="
                + (targetClass == null ? "unknown" : targetClass.getSimpleName()));
    }

    private static MethodInterceptor tracing() {
        return invocation -> {
            System.out.println("ADVICE before");
            try {
                return invocation.proceed();
            } finally {
                System.out.println("ADVICE after");
            }
        };
    }

    public static void main(String[] args) throws Exception {
        GreetingService fixedTarget = new GreetingService("fixed");

        ProxyFactory staticFactory = new ProxyFactory(fixedTarget);
        staticFactory.setInterfaces(Greeting.class);
        staticFactory.setProxyTargetClass(false);
        staticFactory.addAdvice(tracing());

        Greeting staticProxy = (Greeting) staticFactory.getProxy();
        inspect("STATIC", staticProxy);

        // Chỉ gọi getTarget() khi đã xác nhận SingletonTargetSource.
        TargetSource fixedSource = ((Advised) staticProxy).getTargetSource();
        if (!(fixedSource instanceof SingletonTargetSource singleton)) {
            throw new AssertionError("Expected SingletonTargetSource");
        }
        System.out.println("targetSame=" + (singleton.getTarget() == fixedTarget));
        System.out.println("proxyResult=" + staticProxy.hello("Lan"));
        System.out.println("directResult=" + fixedTarget.hello("Lan"));

        CountingTargetSource counting = new CountingTargetSource();
        ProxyFactory dynamicFactory = new ProxyFactory();
        dynamicFactory.setInterfaces(Greeting.class);
        dynamicFactory.setProxyTargetClass(false);
        dynamicFactory.setTargetSource(counting);
        dynamicFactory.addAdvice(tracing());

        Greeting dynamicProxy = (Greeting) dynamicFactory.getProxy();
        inspect("DYNAMIC", dynamicProxy);

        // inspect() không gọi getTarget(), nên chưa acquire target.
        System.out.println("beforeCalls="
                + counting.acquisitions + "/" + counting.releases);
        System.out.println("call1=" + dynamicProxy.hello("Mai"));
        System.out.println("call2=" + dynamicProxy.hello("Nam"));
        System.out.println("afterCalls="
                + counting.acquisitions + "/" + counting.releases);

        ProxyFactory opaqueFactory =
                new ProxyFactory(new GreetingService("hidden"));
        opaqueFactory.setInterfaces(Greeting.class);
        opaqueFactory.setProxyTargetClass(false);
        opaqueFactory.setOpaque(true);

        Greeting opaqueProxy = (Greeting) opaqueFactory.getProxy();
        inspect("OPAQUE", opaqueProxy);
        System.out.println("opaqueResult=" + opaqueProxy.hello("An"));
    }
}
```

Chạy tại thư mục chứa `build.gradle` bằng `gradle run`. Với Gradle Wrapper có sẵn, có thể dùng `./gradlew run` trên Linux/macOS hoặc `.\gradlew.bat run` trên PowerShell.

**Stdout dự kiến từ `main`**, không tính log của Gradle:

```text
=== STATIC ===
aopProxy=true
advised=true
sourceType=SingletonTargetSource
isStatic=true
targetType=GreetingService
targetSame=true
ADVICE before
ADVICE after
proxyResult=fixed:Lan
directResult=fixed:Lan
=== DYNAMIC ===
aopProxy=true
advised=true
sourceType=CountingTargetSource
isStatic=false
targetType=GreetingService
beforeCalls=0/0
ADVICE before
ADVICE after
call1=dynamic-1:Mai
ADVICE before
ADVICE after
call2=dynamic-2:Nam
afterCalls=2/2
=== OPAQUE ===
aopProxy=true
advised=false
opaqueResult=hidden:An
```

### Vì sao stdout có thứ tự này?

**Ở STATIC:**

- `ProxyFactory(fixedTarget)` thiết lập target cố định và thông thường dùng `SingletonTargetSource`.
- `inspect(...)` đọc class và static flag của `TargetSource` qua interface `Advised`.
- `singleton.getTarget() == fixedTarget` là `true` vì đây là source singleton đã biết. Với source này, đọc trực tiếp là thao tác kiểm chứng identity có kiểm soát.
- `staticProxy.hello("Lan")` chạy interceptor; `fixedTarget.hello("Lan")` gọi thẳng target, do đó dòng `directResult` **không có** hai dòng advice xung quanh.

**Ở DYNAMIC:**

- `inspect(...)` chỉ dùng `getTargetSource()`, `getTargetClass()` và `isStatic()` nên `beforeCalls=0/0`.
- Mỗi lần gọi `dynamicProxy.hello(...)` qua proxy làm Spring lấy target từ `CountingTargetSource.getTarget()` và trả lại bằng `releaseTarget(...)` sau khi xử lý. Sau hai lần gọi, bộ đếm là `2/2`.
- Hai kết quả khác nhau vì source minh họa tạo `GreetingService("dynamic-1")` và `GreetingService("dynamic-2")`. Không suy rộng rằng tất cả dynamic source đều phải tạo mới mỗi lần.

**Ở OPAQUE:**

- `opaqueFactory.setOpaque(true)` làm proxy không expose `Advised`. Nó vẫn là Spring AOP proxy nên `AopUtils.isAopProxy(...)` cho kết quả `true`, nhưng `candidate instanceof Advised` là `false`.
- `opaqueProxy.hello("An")` vẫn gọi được phương thức của interface `Greeting`. Kết quả nghiệp vụ không phụ thuộc khả năng cast proxy thành `Advised`.
- Nếu cần quan sát source trong trường hợp này, **người giữ `opaqueFactory`** có thể gọi `opaqueFactory.getTargetSource()`; client chỉ có proxy opaque thì không được phép giả định có cách truy cập source qua `Advised`.

### Kiểm chứng bổ sung

Ví dụ đã có các điểm kiểm tra thiết yếu, nhưng bạn có thể thay phần trace bằng assertion:

```java
if (!AopUtils.isAopProxy(staticProxy)) {
    throw new AssertionError("Not a Spring AOP proxy");
}
if (((Advised) staticProxy).getTargetSource().isStatic() != true) {
    throw new AssertionError("Expected static TargetSource");
}
if (counting.acquisitions != 2 || counting.releases != 2) {
    throw new AssertionError("TargetSource was not balanced");
}
if (opaqueProxy instanceof Advised) {
    throw new AssertionError("Opaque proxy unexpectedly exposes Advised");
}
```

Đoạn này là **mảnh mở rộng** đặt cuối `main` của ví dụ; các biến tương ứng đã được định nghĩa phía trên. Kiểm tra `acquisitions == releases` chỉ là hợp đồng của **CountingTargetSource trong ví dụ**, không phải công thức xác định toàn bộ hành vi của mọi framework proxy.

## 6. Những sai lầm phổ biến khi quan sát TargetSource

### 6.1. Ép kiểu proxy thành Advised mà không kiểm tra

**Sai trong code dùng chung:**

```java
Advised advised = (Advised) context.getBean("myService");
```

Nếu bean không phải Spring AOP proxy, hoặc proxy opaque, dòng cast sẽ ném `ClassCastException`. Nên dùng pattern matching:

```java
Object bean = context.getBean("myService");
if (AopUtils.isAopProxy(bean) && bean instanceof Advised advised) {
    TargetSource source = advised.getTargetSource();
    // Chỉ quan sát metadata ở đây.
}
```

Hai đoạn trên là minh họa, giả định context và bean tồn tại.

### 6.2. Nhầm proxy class với target class

`proxy.getClass()` trả class runtime của proxy (JDK proxy hoặc class proxy do Spring sinh ra). Nó không nhất thiết bằng `targetSource.getTargetClass()`. Với JDK proxy, Java interface client thường là giao diện được proxy triển khai. Hãy phân biệt:

| Cần biết | Gọi API |
| --- | --- |
| Class của proxy runtime | `proxy.getClass()` |
| Kiểu target khai báo bởi TargetSource | `source.getTargetClass()` |
| Target thực đang phục vụ một invocation | Do `TargetSource.getTarget()` cung cấp, phụ thuộc thời điểm |
| Proxy có expose cấu hình AOP | `proxy instanceof Advised` |

`getTargetClass()` có thể trả `null`. Không viết `source.getTargetClass().getName()` mà bỏ qua kiểm tra null trong tiện ích kiểm tra tổng quát.

### 6.3. Tự getTarget() làm lệch vòng đời

Đoạn sau nguy hiểm với target source động:

```java
Object target = source.getTarget();
System.out.println(target);
// Quên releaseTarget(target)
```

Nếu source quản lý pool, việc lấy mà không trả có thể giữ mất một slot; nếu source prototype, lần lấy tạo thêm object chỉ để debug. Nên dùng metadata khi đủ. Nếu bắt buộc mượn target, `releaseTarget(...)` trong `finally` và tuân theo hợp đồng riêng của loại source.

### 6.4. Nghĩ isStatic() trả false nghĩa mỗi lần một object khác

`isStatic() == false` chỉ nói **không cam kết một object cố định**; đó có thể là pooling, hot swap, hoặc prototype. Hai invocation liên tiếp vẫn có thể nhận cùng instance, ví dụ pool chỉ có một target hoặc target chưa được swap.

### 6.5. Bỏ qua advice khi gọi thẳng target

Nếu bạn gọi `((Greeting) fixedSource.getTarget()).hello("Lan")` trên `SingletonTargetSource`, đó là lời gọi **thẳng target**, không qua proxy và không được Spring AOP interceptor của proxy đó xử lý. Phương thức nghiệp vụ vẫn chạy, nên việc chỉ so kết quả trả về có thể che giấu khác biệt. Trace `ADVICE` trong ví dụ làm rõ hai đường đi.

### 6.6. Đánh đồng TargetSource với Advisor hoặc MethodMatcher

- `TargetSource` quản lý **đối tượng target**.
- `Advisor` và `MethodMatcher` xác định **advice nào phù hợp** với method/lời gọi.
- `Advised` expose cấu hình của proxy, bao gồm `getAdvisors()` và `getTargetSource()`.

Một proxy có thể dùng static target source và vẫn có nhiều advisor; nó cũng có thể dùng dynamic target source nhưng chỉ một advisor. Đây là hai chiều cấu hình độc lập.

### 6.7. Dùng AopUtils.isAopProxy() như bảo đảm chắc chắn inspect được

`AopUtils.isAopProxy(bean)` xác nhận bean là một Spring AOP proxy. Nó không bảo đảm cast sang `Advised`, không chứng minh target có thể đọc ở thời điểm hiện tại, và không tự động gỡ nhiều lớp proxy lồng nhau. Khi cần xem từng lớp wrapper, việc truy cập phải theo API mà **mỗi lớp** công khai; không dùng reflection mở private fields chỉ để đi vòng cấu hình opaque.

## 7. Cách quan sát an toàn trong ứng dụng thật

Ưu tiên một tiện ích chỉ đọc metadata:

```java
static void logTargetSource(Object bean) {
    if (!AopUtils.isAopProxy(bean)) {
        System.out.println("Not a Spring AOP proxy");
        return;
    }
    if (!(bean instanceof Advised advised)) {
        System.out.println("Spring AOP proxy does not expose Advised");
        return;
    }

    TargetSource source = advised.getTargetSource();
    Class<?> type = source.getTargetClass();
    System.out.println("source=" + source.getClass().getName());
    System.out.println("static=" + source.isStatic());
    System.out.println("targetType=" + (type == null ? "unknown" : type.getName()));
}
```

Đây là method minh họa đặt trong class có import tương ứng. Không gọi `getTarget()` nên ít nguy cơ thay đổi trạng thái pool/prototype. Trong môi trường nhiều request, hạn chế log dữ liệu nhạy cảm hoặc gọi tiện ích liên tục trên luồng nghiệp vụ; chỉ in metadata cần thiết cho chẩn đoán.

Nếu proxy được tạo bởi `ProxyFactoryBean` trong container, `context.getBean("serviceName")` thường là proxy sản phẩm; muốn xem factory cấu hình, tên đặc biệt `"&serviceName"` lấy chính `FactoryBean` khi bean đó thực sự là FactoryBean. Đó là **hướng quan sát khác** với cast proxy sang `Advised`: factory và proxy là hai object khác nhau. Một số proxy do auto-proxy creator tạo không có một bean `ProxyFactoryBean` tương ứng để tra cứu bằng `&`.

Nếu một bean có `TargetSource` động, hãy phân biệt **thông tin cấu hình** với **instance target được mượn ở một thời điểm**. Kết quả inspect không đảm bảo kết quả của lời gọi sau đó: source có thể swap/chọn lại target.

## 8. Trường hợp không thể đọc TargetSource qua proxy

Những tình huống cần thông báo rõ ràng thay vì cố ép kiểu:

1. **Không phải Spring AOP proxy:** `AopUtils.isAopProxy(bean) == false`. Có thể đây là target thô hoặc proxy từ công nghệ khác.
2. **Spring AOP proxy opaque:** `isAopProxy == true` nhưng `bean instanceof Advised == false`. Nếu bạn là người tạo proxy, đọc cấu hình từ `ProxyFactory`/`ProxyFactoryBean`; client không có quyền introspect qua interface này.
3. **Source báo class null:** `getTargetClass() == null`. Ghi rõ chưa xác định được target type, không tự suy đoán bằng ép kiểu.
4. **Source có target động:** `getTarget()` có thể tốn chi phí, ném exception hoặc yêu cầu release. Cân nhắc không acquire chỉ vì nhu cầu ghi log.
5. **Proxy nhiều lớp:** có thể đang nhìn `TargetSource` của lớp proxy ngoài cùng; nó không nhất thiết mô tả mọi lớp bên trong. Cần hiểu từng lớp và API thực tế của nó.

Nếu mục tiêu là xác nhận lời gọi có chạy advice, kiểm tra trace của interceptor hoặc test hành vi qua proxy. Chỉ đọc `TargetSource` **không chứng minh** advice/pointcut nào thực sự match, hoặc target method nào đã được gọi.

## 9. Câu hỏi tự kiểm tra

1. Muốn xem TargetSource của Spring AOP proxy khi được phép, bước quan trọng nhất là gì? **Xác minh `bean instanceof Advised` rồi gọi `((Advised) bean).getTargetSource()`**.
2. `AopUtils.isAopProxy(bean) == true` có bảo đảm `bean instanceof Advised` không? **Không**, proxy opaque là ví dụ phản chứng.
3. `source.getTargetClass()` có thể trả gì khi kiểu target chưa xác định? **`null`**.
4. `isStatic() == false` có nghĩa là mỗi lần gọi chắc chắn có instance khác không? **Không**, còn phụ thuộc chiến lược target source.
5. Vì sao không nên gọi `source.getTarget()` chỉ để debug pool? **Có thể checkout target cần được release**, làm lệch tài nguyên.
6. Khi đã mượn target động và không có lỗi acquire, cần giải phóng thế nào? **Dùng `releaseTarget(target)` trong `finally` khi target khác null và theo hợp đồng source**.
7. Gọi thẳng target lấy từ `SingletonTargetSource` có kích hoạt advice Spring AOP của proxy không? **Không**; advice áp dụng cho lời gọi đi qua proxy.
8. Có thể dùng `TargetSource` để biết thứ tự `Advisor` đã chạy không? **Không**; cần `Advised.getAdvisors()` và test trace chain tương ứng.

## 10. Tài liệu Spring chính thức

- [Spring Framework Reference — Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html)
- [Spring Framework Reference — Using TargetSource Implementations](https://docs.spring.io/spring-framework/reference/core/aop-api/targetsource.html)
- [Spring Framework Reference — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring Framework API — TargetSource (6.1)](https://docs.spring.io/spring-framework/docs/6.1.16/javadoc-api/org/springframework/aop/TargetSource.html)
- [Spring Framework API — Advised](https://docs.spring.io/spring-framework/docs/6.1.16/javadoc-api/org/springframework/aop/framework/Advised.html)
- [Spring Framework API — SingletonTargetSource](https://docs.spring.io/spring-framework/docs/6.1.16/javadoc-api/org/springframework/aop/target/SingletonTargetSource.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-inspection-005">Thay advisor trong proxy có thể chỉnh sửa và tác động vận hành</a>

<details>
<summary>Xem chi tiết</summary>

# Thay Advisor trong Spring AOP proxy có thể chỉnh sửa và tác động vận hành

## 1. Mục tiêu và bối cảnh

Trong Spring Framework AOP, một proxy có thể công khai giao diện **`org.springframework.aop.framework.Advised`** để đọc và thay đổi danh sách `Advisor` sau khi proxy đã được tạo. Cơ chế này đặc biệt hữu ích khi thử nghiệm, chẩn đoán, đo đạc tạm thời hoặc kiểm soát cấu hình proxy bằng chương trình. Thay advisor có thể thay đổi **những lời gọi nào được chặn**, **advice nào chạy**, và **thứ tự các advice trong chuỗi xử lý** mà không nhất thiết phải tạo một proxy mới.

Ví dụ thực tế: proxy cho `PaymentService` đang có một `DefaultPointcutAdvisor` gắn interceptor ghi log dạng cũ. Ta thay advisor đó bằng một advisor mới cũng chọn `pay(...)`, nhưng ghi log dạng mới. Đối tượng proxy được client sử dụng vẫn là **cùng một tham chiếu**; lần gọi tiếp theo đi qua proxy có thể quan sát hành vi mới.

Bài tập trung vào **Spring AOP proxy, `Advised`, `Advisor` và `ProxyFactory`**, không bàn lại lý thuyết AOP phổ thông. Khi vận hành cần nhớ: thay advisor trên một proxy cụ thể không đồng nghĩa với thay toàn bộ proxy cùng loại trong ApplicationContext, cũng không phải thao tác được bảo đảm đồng bộ như một giao dịch đối với mọi lời gọi đang chạy.

Sau bài này, bạn có thể:

- Phân biệt `Advisor`, `Advice`, `PointcutAdvisor` và interface quản trị proxy `Advised`.
- Dùng `getAdvisors()`, `indexOf(...)`, `replaceAdvisor(old, new)`, `addAdvisor(...)` và `removeAdvisor(...)` đúng hợp đồng API.
- Kiểm chứng bằng Java 17+ rằng một **proxy hiện hữu** áp dụng advisor mới cho các lời gọi tiếp theo.
- Hiểu `isFrozen()`, `opaque`, hạn chế của `IntroductionAdvisor` và sự khác nhau giữa cấu hình proxy với thay đổi bean/class của target.
- Đánh giá ảnh hưởng tới kết quả nghiệp vụ, chain ordering, thread safety, phạm vi rollout và khả năng khôi phục.

## 2. Cấu trúc proxy và điểm có thể chỉnh sửa

```text
Client ------------------> Spring AOP proxy (cùng một object)
                                  │
                      Advised: advisor chain
                      ┌────────────────────────────┐
                      │ [0] DefaultPointcutAdvisor │
                      │     Pointcut: pay(...)     │
                      │     Advice: oldInterceptor │
                      └────────────────────────────┘
                                  │
                             target.pay()

             advised.replaceAdvisor(oldAdvisor, newAdvisor)
                                  │
                                  ▼
Client ------------------> Spring AOP proxy (vẫn object đó)
                                  │
                      Advised: advisor chain
                      ┌────────────────────────────┐
                      │ [0] DefaultPointcutAdvisor │
                      │     Pointcut: pay(...)     │
                      │     Advice: newInterceptor │
                      └────────────────────────────┘
                                  │
                             target.pay()
```

Một `Advisor` có thể mang pointcut xác định tập phương thức và một advice thực thi hành vi. Ví dụ `DefaultPointcutAdvisor` thực thi `PointcutAdvisor`, cho phép `getPointcut()` và `getAdvice()`. Khi thay cả advisor, bạn có thể thay **pointcut**, **advice**, hoặc cả hai. Việc thay đổi pointcut rộng/hẹp hơn rất đáng kiểm thử vì có thể chặn thêm hoặc bỏ chặn những method trước đó.

`Advised` là interface quản lý cấu hình AOP của proxy. Những proxy Spring AOP thông thường có thể ép kiểu sang `Advised`, trừ khi cấu hình cố ý ẩn giao diện quản lý (ví dụ `opaque=true`). Trước khi thao tác với một đối tượng lấy từ nơi khác, kiểm tra `instanceof Advised` và xem `isFrozen()`.

### 2.1. Các API quan trọng của `Advised`

| Phương thức | Kết quả hoặc tác dụng | Điểm cần thận trọng |
| --- | --- | --- |
| `getAdvisors()` | Trả về các advisor đang gắn theo thứ tự | Dùng để quan sát cấu hình, không tự chứng minh advice chạy trên mọi method |
| `indexOf(advisor)` | Trả index advisor hoặc `-1` nếu không tìm thấy | Index phản ánh chain hiện tại; không lưu dùng lâu nếu chain có thể đổi |
| `replaceAdvisor(old, new)` | Thay advisor được tìm thấy; trả `true` khi thay thành công, `false` khi không có advisor cũ | Không được ngầm coi `false` là đã cập nhật |
| `addAdvisor(advisor)` | Thêm advisor vào cuối chain | Có thể thay đổi phạm vi và thứ tự intercept |
| `addAdvisor(index, advisor)` | Chèn advisor vào vị trí chỉ định | `0` là đầu chain; index sai có thể ném lỗi cấu hình |
| `removeAdvisor(advisor)` | Xóa advisor nếu tìm thấy, trả boolean | Có thể vô tình loại logic quan trọng như kiểm tra quyền |
| `removeAdvisor(index)` | Xóa advisor tại vị trí cụ thể | Không nên dùng index cũ sau khi chain thay đổi |
| `addAdvice(advice)` | Thêm advice, Spring bọc thành advisor có pointcut luôn khớp | Có thể áp dụng trên mọi method proxy có thể chặn |
| `isFrozen()` | Cho biết cấu hình advice có bị đóng băng không | `true` nghĩa là không được thay cấu hình advisor/advice |

Một `Advised` không phải API để **đổi code của target**. `replaceAdvisor` chỉ xử lý danh sách advisor của cấu hình proxy. Một số API khác cho phép thay `TargetSource` nếu không bị frozen, nhưng đó là một bài toán riêng và có tác động vòng đời lớn hơn.

### 2.2. Phân biệt `replaceAdvisor` với `removeAdvisor` rồi `addAdvisor`

Khi muốn thay *một vị trí* đã biết trong chain, ưu tiên:

```java
boolean changed = advised.replaceAdvisor(oldAdvisor, newAdvisor);
```

Hành động này giữ ý định “thay thành phần cũ bằng thành phần mới” và có kết quả boolean để kiểm tra. Nếu tự gọi:

```java
advised.removeAdvisor(oldAdvisor);
advised.addAdvisor(newAdvisor);
```

thì advisor mới được thêm **cuối chain**, làm thay đổi thứ tự so với vị trí cũ. Hai bước riêng còn tạo ra một khoảng thời gian cấu hình trung gian nếu các thread khác gọi proxy đồng thời. Không sử dụng hai bước này như một cách “thay thế tương đương” khi thứ tự hoặc tính liên tục quan trọng.

Dù dùng `replaceAdvisor`, đây vẫn **không phải một ranh giới giao dịch toàn ứng dụng**: các invocation đang chạy có thể đã dựng hoặc bắt đầu chain của chúng trước thời điểm thay. Khi cần chuyển đổi bảo đảm tuyệt đối cho toàn hệ thống, phải có cơ chế triển khai, ngăn/điều phối lưu lượng và quản lý phiên bản rõ ràng.

## 3. Ví dụ Java 17+ chạy được: thay Advisor trên cùng proxy

Bài toán: ban đầu chặn `pay(String)` bằng advice “old”; sau đó thay bằng advice “new”; cuối cùng khôi phục advisor cũ. Phương thức `status()` không nằm trong pointcut, để ta phát hiện trường hợp advisor có phạm vi quá rộng.

### 3.1. Dependencies và môi trường chạy

Trong dự án Gradle sử dụng Spring Boot 3.x, Spring Framework 6.x và Java 17+, thêm dependencies sau vào `build.gradle` hiện hữu:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
```

Đây chỉ là **mảnh cấu hình bổ sung** trong dự án đã có Spring Boot plugin và dependency management; không phải toàn bộ `build.gradle`. Ví dụ dưới đây dùng `ProxyFactory` trực tiếp, nên không cần khởi tạo `ApplicationContext` hoặc dùng `@Aspect`.

### 3.2. Chương trình hoàn chỉnh

Tạo `src/main/java/demo/aop/AdvisorReplacementDemo.java`:

```java
package demo.aop;

import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

public class AdvisorReplacementDemo {

    public interface PaymentOperations {
        String pay(String paymentId);
        String status();
    }

    public static class PaymentService implements PaymentOperations {
        private final List<String> events;

        public PaymentService(List<String> events) {
            this.events = events;
        }

        @Override
        public String pay(String paymentId) {
            events.add("target:pay:" + paymentId);
            return "PAID:" + paymentId;
        }

        @Override
        public String status() {
            events.add("target:status");
            return "OK";
        }
    }

    public static void main(String[] args) {
        List<String> events = new ArrayList<>();

        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("pay");

        MethodInterceptor oldAdvice = invocation -> {
            events.add("old:before");
            Object result = invocation.proceed();
            events.add("old:after");
            return result;
        };

        MethodInterceptor newAdvice = invocation -> {
            events.add("new:before");
            Object result = invocation.proceed();
            events.add("new:after");
            return result;
        };

        DefaultPointcutAdvisor oldAdvisor =
                new DefaultPointcutAdvisor(pointcut, oldAdvice);
        DefaultPointcutAdvisor newAdvisor =
                new DefaultPointcutAdvisor(pointcut, newAdvice);

        ProxyFactory factory = new ProxyFactory(new PaymentService(events));
        factory.setInterfaces(PaymentOperations.class);
        factory.addAdvisor(oldAdvisor);

        PaymentOperations proxy = (PaymentOperations) factory.getProxy();
        Advised advised = (Advised) proxy;

        System.out.println("frozen=" + advised.isFrozen());
        System.out.println("before=" + proxy.pay("A"));
        System.out.println("replace=" + advised.replaceAdvisor(
                oldAdvisor, newAdvisor));
        System.out.println("after=" + proxy.pay("B"));
        System.out.println("status=" + proxy.status());

        System.out.println("rollback=" + advised.replaceAdvisor(
                newAdvisor, oldAdvisor));
        System.out.println("restored=" + proxy.pay("C"));
        System.out.println("advisors=" + advised.getAdvisors().length);
        System.out.println("events=" + String.join(" | ", events));
    }
}
```

Để chạy chương trình từ Gradle, bổ sung task vào `build.gradle` hiện tại:

```groovy
tasks.register('runAdvisorReplacement', JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'demo.aop.AdvisorReplacementDemo'
}
```

Sau đó chạy:

```bash
./gradlew runAdvisorReplacement
```

Windows PowerShell:

```powershell
.\gradlew.bat runAdvisorReplacement
```

**Các dòng stdout do `main()` in theo đúng thứ tự** (không bao gồm log khác do Gradle hay cấu hình logging của dự án):

```text
frozen=false
before=PAID:A
replace=true
after=PAID:B
status=OK
rollback=true
restored=PAID:C
advisors=1
events=old:before | target:pay:A | old:after | new:before | target:pay:B | new:after | target:status | old:before | target:pay:C | old:after
```

Phân tích:

1. `factory.addAdvisor(oldAdvisor)` gắn advisor ban đầu; `getProxy()` trả proxy áp dụng advice của Spring. `factory.setInterfaces(PaymentOperations.class)` xác định interface mà client có thể gọi.
2. `proxy.pay("A")` được pointcut chọn và ghi sự kiện `old:...`.
3. `advised.replaceAdvisor(oldAdvisor, newAdvisor)` trả `true`; proxy vẫn là đối tượng đang được biến `proxy` tham chiếu. Spring cập nhật chain để các lời gọi tiếp theo có thể chạy `newAdvice`.
4. `proxy.pay("B")` ghi sự kiện `new:...`; `proxy.status()` không khớp pointcut nên chỉ có `target:status`.
5. `replaceAdvisor(newAdvisor, oldAdvisor)` thực hiện rollback cấu hình, sau đó `proxy.pay("C")` lại dùng `oldAdvice`.
6. Mỗi thời điểm trước/sau thao tác đều có **một advisor** trong proxy. Thay thế không nhằm nhân đôi chain như cách thêm riêng advisor mới.

Đoạn code cố ý đặt trạng thái sự kiện trong `List<String>` để minh họa thứ tự *tuần tự một luồng*. `ArrayList` không phải cấu trúc lưu sự kiện phù hợp cho demo có nhiều request đồng thời; nếu viết concurrent test, dùng cấu trúc đồng bộ hoặc bộ ghi sự kiện riêng.

## 4. Kiểm thử JUnit 5 xác minh hành vi và các nhánh thất bại

Một test tốt không chỉ assert `replaceAdvisor(...) == true`. Cần xác nhận **hành vi trước, sau, khi không match, khi rollback**, kết quả trả về của target, và thử biến thể bị frozen.

Tạo `src/test/java/demo/aop/AdvisorReplacementTest.java`. Test tái sử dụng các kiểu public static nằm trong `AdvisorReplacementDemo.java`:

```java
package demo.aop;

import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;

import static org.junit.jupiter.api.Assertions.*;

class AdvisorReplacementTest {

    @Test
    void existingProxyUsesReplacementAndCanRollBack() {
        List<String> events = new ArrayList<>();
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("pay");

        MethodInterceptor oldAdvice = invocation -> {
            events.add("old");
            return invocation.proceed();
        };
        MethodInterceptor newAdvice = invocation -> {
            events.add("new");
            return invocation.proceed();
        };

        DefaultPointcutAdvisor oldAdvisor =
                new DefaultPointcutAdvisor(pointcut, oldAdvice);
        DefaultPointcutAdvisor newAdvisor =
                new DefaultPointcutAdvisor(pointcut, newAdvice);

        ProxyFactory factory = new ProxyFactory(
                new AdvisorReplacementDemo.PaymentService(events));
        factory.setInterfaces(AdvisorReplacementDemo.PaymentOperations.class);
        factory.addAdvisor(oldAdvisor);

        var proxy = (AdvisorReplacementDemo.PaymentOperations) factory.getProxy();
        Advised advised = (Advised) proxy;
        Object originalProxyReference = proxy;

        assertEquals("PAID:A", proxy.pay("A"));
        assertEquals(0, advised.indexOf(oldAdvisor));
        assertFalse(advised.isFrozen());

        assertTrue(advised.replaceAdvisor(oldAdvisor, newAdvisor));
        assertSame(originalProxyReference, proxy);
        assertEquals(-1, advised.indexOf(oldAdvisor));
        assertEquals(0, advised.indexOf(newAdvisor));
        assertEquals(1, advised.getAdvisors().length);
        assertSame(newAdvisor, advised.getAdvisors()[0]);

        assertEquals("PAID:B", proxy.pay("B"));
        assertEquals("OK", proxy.status());

        // Advisor cũ không còn trong chain; không tự coi false là success.
        assertFalse(advised.replaceAdvisor(oldAdvisor, newAdvisor));
        assertTrue(advised.replaceAdvisor(newAdvisor, oldAdvisor));
        assertEquals("PAID:C", proxy.pay("C"));

        assertEquals(List.of(
                "old", "target:pay:A",
                "new", "target:pay:B",
                "target:status",
                "old", "target:pay:C"), events);
    }

    @Test
    void frozenConfigurationRejectsAdvisorReplacement() {
        List<String> events = new ArrayList<>();
        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("pay");

        MethodInterceptor oldAdvice = invocation -> invocation.proceed();
        MethodInterceptor newAdvice = invocation -> invocation.proceed();

        DefaultPointcutAdvisor oldAdvisor =
                new DefaultPointcutAdvisor(pointcut, oldAdvice);
        DefaultPointcutAdvisor newAdvisor =
                new DefaultPointcutAdvisor(pointcut, newAdvice);

        ProxyFactory factory = new ProxyFactory(
                new AdvisorReplacementDemo.PaymentService(events));
        factory.setInterfaces(AdvisorReplacementDemo.PaymentOperations.class);
        factory.addAdvisor(oldAdvisor);
        factory.setFrozen(true);

        Advised advised = (Advised) factory.getProxy();
        assertTrue(advised.isFrozen());
        assertThrows(AopConfigException.class,
                () -> advised.replaceAdvisor(oldAdvisor, newAdvisor));
        assertSame(oldAdvisor, advised.getAdvisors()[0]);
    }
}
```

Chạy:

```bash
./gradlew test --tests 'demo.aop.AdvisorReplacementTest'
```

Windows PowerShell:

```powershell
.\gradlew.bat test --tests 'demo.aop.AdvisorReplacementTest'
```

Khi các assertion đúng, Gradle báo `BUILD SUCCESSFUL`. Đây là **kết quả mong đợi theo logic test**, không phải log của một lần thực thi được chứng minh trong Sheet. Trường hợp thứ hai chứng minh cấu hình frozen ngăn thay advisor; test xác nhận advisor cũ vẫn là phần tử đang cấu hình.

### 4.1. Vì sao cần test ca không khớp?

Nếu khi thay advisor bạn vô tình dùng:

```java
new DefaultPointcutAdvisor(newAdvice)
```

constructor một tham số dùng `Pointcut.TRUE`, có thể chặn cả `status()`. Khi ấy test `events` có thêm `new` trước `target:status` và sẽ fail. Vì vậy test negative case của pointcut phát hiện việc thay advisor làm mở rộng phạm vi ngoài ý muốn.

### 4.2. Kiểm tra snapshot trước và sau

Chỉ dựa vào việc có class `Advised` là chưa đủ. Khi thay advisor, nên lưu các thông tin đủ để giải thích thay đổi:

```java
Advised advised = (Advised) proxy;
int oldPosition = advised.indexOf(oldAdvisor);
boolean frozen = advised.isFrozen();
int beforeCount = advised.getAdvisors().length;

// Chỉ thay sau khi đã xác minh mục tiêu.
boolean replaced = advised.replaceAdvisor(oldAdvisor, newAdvisor);

int afterCount = advised.getAdvisors().length;
int newPosition = advised.indexOf(newAdvisor);
```

Đây là **mảnh minh họa API**; cần khai báo `proxy` và hai advisor trước khi chạy. Các biến cho phép ghi nhận trạng thái trước/sau; không phải cơ chế khóa chống xung đột. Trong môi trường có nhiều luồng đồng thời điều chỉnh cấu hình, đọc `indexOf` rồi thay không tự động tạo một transaction bảo đảm mọi dữ liệu snapshot luôn đồng nhất.

## 5. Những giới hạn quan trọng

### 5.1. `frozen=true` chặn thay đổi advice/advisor

`ProxyFactory` có thể cấu hình:

```java
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvisor(approvedAdvisor);
factory.setFrozen(true);
Object proxy = factory.getProxy();
```

Đây là đoạn minh họa; cần định nghĩa `target` và `approvedAdvisor`. Khi `Advised.isFrozen()` trả về `true`, những lời gọi như `addAdvisor`, `removeAdvisor`, `replaceAdvisor` bị từ chối bằng lỗi cấu hình `AopConfigException`. Đây là lựa chọn có ích khi muốn tránh thay đổi những advisor bảo vệ bất biến nghiệp vụ, kiểm tra quyền hoặc transaction một cách tùy tiện.

Một proxy frozen vẫn có thể **thực thi advisor đã được gắn trước đó** bình thường. Frozen chỉ ngăn sửa cấu hình advice, không tắt AOP.

### 5.2. `opaque=true` ẩn interface quản trị

Spring `ProxyConfig` hỗ trợ `setOpaque(true)`. Với proxy opaque, client không thể giả định object cũng triển khai `Advised`. Ví dụ:

```java
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvisor(approvedAdvisor);
factory.setOpaque(true);
Object proxy = factory.getProxy();

// Với proxy opaque, không dựa vào cast proxy sang Advised.
```

Đây là mảnh minh họa API. **Opaque** và **frozen** là hai lựa chọn khác nhau:

- `opaque` điều khiển việc công khai giao diện cấu hình cho người sử dụng proxy.
- `frozen` điều khiển khả năng sửa cấu hình advice/advisor.

Nếu không cần cho client động chạm cấu hình AOP, có thể cân nhắc bật cả hai, tùy mục tiêu vận hành.

### 5.3. `IntroductionAdvisor` có giới hạn đặc biệt

Một `IntroductionAdvisor` có thể làm proxy công khai thêm interface. Những interface được sinh ra khi tạo proxy; **thay introduction advisor sau khi proxy đã được tạo** không tự cập nhật kiểu/interface của chính đối tượng proxy đã tồn tại. Javadoc của `Advised.replaceAdvisor` cảnh báo phải lấy lại proxy khi introduction thay đổi tập interface.

Bởi vậy ví dụ chính chỉ thay `DefaultPointcutAdvisor` mang `MethodInterceptor` thông thường. Không suy luận từ ví dụ đó rằng có thể thêm/bỏ interface Java lên một proxy sống mà client đang giữ.

### 5.4. Gọi qua proxy mới có tác dụng

```java
PaymentOperations proxy = /* proxy Spring AOP */;
proxy.pay("A"); // có thể đi qua advisor chain

PaymentService target = /* đối tượng đích */;
target.pay("A"); // gọi trực tiếp; không đi qua chain của proxy
```

Đây là mảnh minh họa. Khi target gọi một method khác bằng `this.method()`, lời gọi nội bộ có thể bỏ qua Spring AOP proxy (**self-invocation**). Thay advisor không tự khắc phục vấn đề call path này.

### 5.5. Thay một proxy không phải đổi mọi bean

Nếu ứng dụng có nhiều AOP proxies, chỉnh `Advised` của một object không có nghĩa Spring sẽ nhân bản thay đổi sang tất cả. Ngay cả khi các proxy cùng lớp target hoặc chia sẻ advice bean, tập advisor mỗi proxy có thể khác. Tương tự, thay đổi advisor của một proxy đang tồn tại không tự sửa cấu hình Java/XML của Spring cho lần khởi động tiếp theo.

Trong quản trị phiên bản cấu hình, xem thay đổi runtime là **trạng thái tạm thời** trừ khi có cơ chế đồng bộ với nguồn cấu hình bền vững và quy trình triển khai chuẩn.

## 6. Tác động vận hành khi thay advisor

| Khía cạnh | Tác động có thể xảy ra | Việc nên kiểm tra |
| --- | --- | --- |
| Phạm vi phương thức | Pointcut mới chọn thêm/bớt method, có thể vô tình advice quá rộng | Positive/negative tests trên class, overload và lời gọi runtime |
| Thứ tự interceptor | Thay advisor có thể giữ vị trí, nhưng tự remove/add sẽ thay vị trí | So `getAdvisors()` và trace before/after |
| Tính đúng nghiệp vụ | Advice có thể bỏ `proceed()`, sửa return value hoặc xử lý exception khác | Kiểm thử response, exception và side effects |
| Security/transaction | Loại hoặc đổi advisor bảo vệ có thể thay hành vi kiểm soát quyền/giao dịch | Hạn chế người có quyền sửa; ưu tiên frozen với chain nhạy cảm |
| Hiệu năng | Mỗi interceptor bổ sung việc khởi tạo chain, logic đo đạc, logging hoặc tra metadata | So sánh latency/error rate và overhead sau thay |
| Concurrency | Lời gọi đang chạy có thể đang dùng chain được xác định trước đó | Không coi việc thay như switch đồng thời tuyệt đối cho mọi request |
| Phạm vi triển khai | Chỉ proxy được sửa thay đổi; instance khác có thể giữ cấu hình cũ | Inventory proxy/bean/instance, tránh rollout một phần không kiểm soát |
| Tính bền vững | Restart hay tạo proxy mới có thể quay về cấu hình định nghĩa ban đầu | Quản lý cấu hình nguồn và quy trình rollback |
| Quan sát | Log mới có thể nhiều hơn, ít hơn hoặc chứa thông tin không phù hợp | Kiểm tra độ nhạy dữ liệu log và dung lượng log |
| Khôi phục | Advisor cũ có thể cần để phục hồi hành vi | Giữ tham chiếu, đo trạng thái sau rollback và test tính tương thích |

### 6.1. Một tình huống dễ sai: thay advisor kiểm tra quyền

Giả sử ban đầu advisor kiểm tra quyền chặn `pay`, còn advisor mới chỉ ghi log. Nếu thay trực tiếp bằng logging advisor, proxy vẫn hoạt động và test trả `PAID:...` có thể pass, nhưng hành vi kiểm soát quyền đã mất.

Vì vậy, không đánh giá an toàn của một thay đổi advisor chỉ bằng “không có exception”. Đánh giá cả **danh sách policy bắt buộc**, đường đi của các phương thức nhạy cảm, và kết quả ca từ chối. Nếu muốn thêm logging, thường nên **thêm advisor riêng đúng vị trí** thay vì thay thế advisor security đang tồn tại.

### 6.2. Một tình huống dễ sai: advice không gọi `proceed()`

```java
MethodInterceptor interceptor = invocation -> {
    return "SKIPPED";
};
```

Đây là advice hợp lệ về mặt cấu trúc, nhưng không gọi target. Khi nó thay advisor cũ, method có thể trả một kết quả mới hoàn toàn và không có side effect của target. Spring không tự bảo đảm mọi around advice đều gọi `proceed()`; đó là trách nhiệm của logic advice.

Nếu advice cần xử lý kết thúc cả khi có ngoại lệ, sử dụng:

```java
MethodInterceptor interceptor = invocation -> {
    long start = System.nanoTime();
    try {
        return invocation.proceed();
    } finally {
        long elapsed = System.nanoTime() - start;
        recordLatency(elapsed); // hàm minh họa cần tự định nghĩa
    }
};
```

Đoạn này là mảnh minh họa, không phải chương trình Java độc lập. `finally` không tự nuốt ngoại lệ; nó chỉ bảo đảm chạy đoạn dọn dẹp/đo đạc trước khi ngoại lệ tiếp tục lan ra, trừ khi chính mã trong `finally` ném lỗi khác.

### 6.3. Tác động lên lời gọi đang thực thi

Giả sử thread T1 đang gọi `proxy.pay()` và đã đi vào advice cũ. Thread T2 thực hiện `replaceAdvisor(old, new)`. Không nên giả định T1 sẽ “nhảy” giữa hai advice trong cùng một invocation, cũng không nên giả định tất cả lời gọi đang bắt đầu trên nhiều node lập tức áp dụng advice mới.

Spring có cơ chế làm mới/cập nhật chain khi cấu hình thay đổi, nhưng API `Advised` không cấp một hợp đồng cutover đồng thời toàn hệ thống. Nếu yêu cầu nghiệp vụ phải chuyển trạng thái ở một thời điểm chính xác, cần dùng cách triển khai có quản lý lưu lượng, quiescence/drain, versioning hoặc dựng proxy/bean mới rồi chuyển traffic bằng cơ chế ứng dụng kiểm soát.

## 7. Quy trình cập nhật an toàn và có thể rollback

Đối với công việc thử nghiệm hoặc công cụ vận hành chuyên biệt, một quy trình kiểm soát gọn gồm:

1. **Xác định đúng instance:** Đảm bảo đối tượng là Spring proxy có thể truy cập `Advised` và đó là proxy thực sự nhận lưu lượng cần thay. Không sửa target hoặc một proxy test rồi coi như đã thay instance production.
2. **Đọc trạng thái:** Kiểm tra `isFrozen()`, `getAdvisors()`, `indexOf(oldAdvisor)`, loại pointcut và thứ tự hiện tại. Chụp snapshot phục vụ debug và rollback.
3. **Kiểm tra chính sách:** Phân loại advisor cần thiết cho security, transaction, auditing; đối chiếu pointcut cũ và mới trên các phương thức nhạy cảm.
4. **Đánh giá Advice:** Xác định liệu `proceed()` được gọi không, return/exception có giữ hợp đồng không, trạng thái mutable có an toàn khi nhiều thread dùng chung không.
5. **Thực hiện thay thế có kiểm soát:** Gọi `replaceAdvisor(oldAdvisor, newAdvisor)`, kiểm tra giá trị trả về; nếu `false`, không báo thay thành công.
6. **Readback cấu hình:** Kiểm tra `getAdvisors()`, thứ tự, `indexOf(newAdvisor)` và sự vắng mặt của advisor cũ. Đừng xem snapshot là cơ chế khóa đồng thời.
7. **Smoke test và quan sát:** Gọi ca match, non-match, exception; theo dõi error rate, latency, logs và các chức năng nghiệp vụ bị ảnh hưởng.
8. **Rollback khi cần:** Gọi `replaceAdvisor(newAdvisor, oldAdvisor)` khi đối tượng cũ còn tồn tại và cấu hình chưa bị thay đổi cạnh tranh; xác nhận lại hành vi.
9. **Đồng bộ bền vững:** Nếu thay đổi được chấp thuận lâu dài, cập nhật cấu hình nguồn/triển khai có kiểm chứng. Trạng thái runtime một instance không đủ cho restart hay rollout nhiều node.

**Lưu ý về cạnh tranh:** `indexOf(oldAdvisor) >= 0` chỉ là thông tin tại lúc đọc. Nếu có nhiều tác nhân cùng sửa cấu hình, kết quả có thể thay đổi trước khi bạn gọi `replaceAdvisor`. Hãy giới hạn quyền truy cập công cụ và tuần tự hóa thao tác theo chính sách ứng dụng.

## 8. Các lỗi thường gặp khi quan sát và sửa proxy

### 8.1. `ClassCastException` khi ép về `Advised`

Nguyên nhân thường gặp: đối tượng không phải Spring AOP proxy hoặc proxy được cấu hình `opaque`. Cần kiểm tra loại object, nguồn bean/proxy; tránh ép kiểu mọi bean một cách mù quáng. `AopUtils.isAopProxy(object)` (Spring) có thể hỗ trợ nhận diện AOP proxy nhưng **không thay thế** kiểm tra `instanceof Advised` trước khi cast.

### 8.2. `replaceAdvisor` trả `false`

`oldAdvisor` có thể không còn trong chain của proxy đó. Một adviser “tương tự về cấu hình” nhưng là instance khác không bảo đảm khớp tiêu chí tìm kiếm. Giữ tham chiếu chính xác đã dùng khi gắn advisor, hoặc nhận diện advisor trong `getAdvisors()` để kiểm tra trước khi thay.

Không viết:

```java
advised.replaceAdvisor(oldAdvisor, newAdvisor);
System.out.println("Updated successfully");
```

nếu bỏ qua giá trị trả về; thông báo có thể sai. Thay bằng:

```java
boolean changed = advised.replaceAdvisor(oldAdvisor, newAdvisor);
if (!changed) {
    throw new IllegalStateException("Advisor cũ không có trong chain");
}
```

Các đoạn trên là mảnh Java minh họa; biến advisor và `advised` phải được khai báo từ trước.

### 8.3. `AopConfigException` vì frozen

`isFrozen() == true` báo cấu hình không được sửa; đừng cố vòng qua frozen trong thời gian vận hành. Phân biệt quyết định cố ý khóa policy với lỗi quyền truy cập. Muốn cập nhật thì đưa thay đổi vào cách dựng proxy/phiên bản triển khai chính thức.

### 8.4. Thay đổi thứ tự ngoài ý muốn

Khi cần đổi advisor cũ tại cùng một vị trí, dùng `replaceAdvisor`; khi cần đổi thứ tự có chủ đích, xem xét `removeAdvisor`/`addAdvisor(index, ...)` ở giai đoạn cấu hình có kiểm soát. Trên proxy đang phục vụ đồng thời, nhiều thao tác rời rạc tạo trạng thái trung gian cần lưu ý.

### 8.5. Thay vào proxy nhưng test không thấy hiệu quả

- Client đang gọi trực tiếp target hoặc gọi một proxy khác.
- Self-invocation bypass proxy.
- Pointcut không khớp class/method hoặc đối số động.
- Ứng dụng có nhiều bean instances hoặc nodes và chỉ một object được cập nhật.
- Advice sau `proceed()` không chạy do exception.
- Proxy/factory đã frozen, phương thức replace không thành công hoặc ném lỗi.

Để phân biệt nguyên nhân, hãy kiểm tra chain của **đúng instance proxy**, sau đó chạy test trước/sau với một phương thức chắc chắn khớp pointcut.

## 9. Thực hành mở rộng

**Bài tập 1 — Mở rộng Pointcut:** Từ chương trình chính, tạo advisor mới dùng `NameMatchMethodPointcut` với `setMappedNames("pay", "status")`. Thay advisor cũ, dự đoán và kiểm tra vì sao `status()` bắt đầu được advice chặn. Sau đó rollback.

**Bài tập 2 — Gắn hai advisor:** Thêm một logging advisor vào vị trí `0` qua `addAdvisor(0, loggingAdvisor)`. Kiểm tra thứ tự before/after khi gọi `pay()`, sau đó xóa advisor theo đúng instance. Không thay thế advisor có policy bắt buộc bằng logging advisor.

**Bài tập 3 — Frozen:** Dùng `factory.setFrozen(true)` sau khi gắn các advisor cần thiết nhưng trước khi tạo proxy. Chứng minh `Advised.isFrozen()` là `true` và thay thế bị từ chối. Đánh giá tình huống vì sao nên đóng băng cấu hình kiểm tra quyền.

**Bài tập 4 — Cấu hình opaque:** Dùng `factory.setOpaque(true)` rồi kiểm tra `proxy instanceof Advised`. Giải thích khác nhau giữa việc ẩn API quản trị và việc khóa cấu hình bằng frozen.

**Bài tập 5 — Tính bền vững:** Tạo proxy mới từ một `ProxyFactory` độc lập với advisor cũ, trong khi proxy ban đầu đã được thay advisor. Quan sát hai proxy hoạt động khác nhau; giải thích tại sao thay đổi một instance không trở thành cấu hình mặc định của toàn hệ thống.

**Tiêu chí hoàn thành:** Người học chứng minh được trên **cùng proxy** rằng `replaceAdvisor` làm thay đổi hành vi các lời gọi sau khi thay, vẫn giữ method không match nguyên trạng, có thể rollback; đồng thời giải thích chính xác hạn chế `frozen`, `opaque`, `IntroductionAdvisor` và rủi ro thay runtime trên nhiều thread/nodes.

## 10. Tài liệu Spring chính thức

- [Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advised.html) — `Advised`, `getAdvisors()`, `replaceAdvisor()`, frozen và cảnh báo khi chỉnh proxy đã tạo.
- [Advised Javadoc (Spring 6.2)](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/framework/Advised.html) — hợp đồng API và trường hợp introduction.
- [Creating AOP Proxies Programmatically](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/prog.html) — dùng `ProxyFactory` với target và advisor.
- [Spring Advisor API](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/advisor.html) — kết hợp `Pointcut` và `Advice`.
- [Using ProxyFactoryBean](https://docs.spring.io/spring-framework/reference/6.2/core/aop-api/pfb.html) — `frozen` và các tùy chọn cấu hình proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-inspection-006">frozen, opaque và các giới hạn thao tác với proxy</a>

<details>
<summary>Xem chi tiết</summary>

# Frozen, opaque và các giới hạn thao tác với Spring AOP proxy

## 1. Vì sao cần hiểu frozen và opaque?

Spring AOP proxy không chỉ chuyển lời gọi đến target qua các advice. Theo cấu hình mặc định, proxy còn cho phép mã có tham chiếu đến nó **quan sát** và đôi khi **chỉnh sửa** danh sách advisor trong lúc chạy thông qua interface `org.springframework.aop.framework.Advised`.

Điều đó hữu ích khi kiểm thử hoặc chẩn đoán, nhưng không phải lúc nào cũng nên cho mọi caller điều chỉnh proxy:

- `frozen`: giữ nguyên cấu hình AOP của proxy, ngăn các thao tác thay đổi advice/advisor qua API cấu hình.
- `opaque`: không công khai interface quản trị `Advised` trên proxy, nên caller không thể cast proxy sang `Advised` để đọc và chỉnh advisor.

Đây là **hai lựa chọn của Spring AOP `ProxyConfig`**, có thể được thiết lập trên `ProxyFactory` trước khi gọi `getProxy()`. Cả hai ảnh hưởng đến khả năng thao tác với proxy, **không tự tắt advice** hay thay đổi hành vi nghiệp vụ của target.

Bài này chỉ bàn đến Spring AOP và các API quản lý proxy của Spring, tập trung vào trường hợp tạo proxy bằng `ProxyFactory` và quan sát bằng `Advised`/`AopUtils`.

## 2. Mô hình proxy và giao diện Advised

Một proxy Spring AOP mặc định có hai bề mặt:

```text
                           Spring AOP proxy
                         /                  \
                        /                    \
            Business interface       Advised interface
            OrderService             getAdvisors()
            save(), find()           addAdvice()
                        \            addAdvisor()
                         \           removeAdvisor()
                          \              /
                           \            /
                           AOP interceptor chain
                                     |
                                    target
```

**Business interface** là hợp đồng mà mã nghiệp vụ nên dùng. **`Advised`** là giao diện quản trị proxy của Spring:

```java
import org.springframework.aop.framework.Advised;

Advised advised = (Advised) proxy;

int count = advised.getAdvisors().length;
boolean frozen = advised.isFrozen();
```

Đây là đoạn mã minh họa khi `proxy` là Spring AOP proxy **không opaque**. Nếu `opaque=true`, cast sẽ thất bại vì proxy không công khai `Advised`.

Một số API quan trọng của `Advised`:

| API | Tác dụng |
| --- | --- |
| `getAdvisors()` | Lấy snapshot mảng advisor đang cấu hình |
| `addAdvice(Advice)` | Thêm advice; Spring thường bọc trong advisor với `Pointcut.TRUE` |
| `addAdvisor(Advisor)` | Thêm advisor có thể có pointcut riêng |
| `removeAdvice(Advice)` | Yêu cầu bỏ advisor chứa advice đã cho |
| `removeAdvisor(Advisor)` | Yêu cầu bỏ advisor tương ứng |
| `replaceAdvisor(Advisor, Advisor)` | Thay một advisor bằng advisor khác |
| `isFrozen()` | Cho biết cấu hình đã bị đóng băng hay chưa |
| `getProxiedInterfaces()` | Liệt kê các interface được proxy công khai |
| `getTargetSource()` | Truy cập cơ chế cung cấp target theo API quản trị |
| `toProxyConfigString()` | Chuỗi mô tả cấu hình proxy phục vụ chẩn đoán |

Với cấu hình bình thường, `Advised` hỗ trợ thay đổi advice khi proxy đang được sử dụng. Đây là khả năng kỹ thuật; việc thay đổi chain tại runtime trong hệ thống đang xử lý request cần thận trọng vì có thể làm thay đổi hành vi quan sát và thứ tự interceptor.

## 3. frozen: khóa thay đổi cấu hình advice

Thiết lập:

```java
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvice(traceAdvice);
factory.setFrozen(true);

Object proxy = factory.getProxy();
```

Đây là fragment API (cần định nghĩa `target` và `traceAdvice`). `setFrozen(true)` là phương thức từ `ProxyConfig` được `ProxyFactory` kế thừa. Mặc định `frozen=false`.

Khi proxy được tạo với `frozen=true`:

1. Proxy vẫn thực thi advice đã được cấu hình trước đó.
2. Nếu proxy không opaque, caller vẫn có thể cast sang `Advised` và **đọc** thông tin như `getAdvisors()`, `isFrozen()`.
3. Các lời gọi cố gắng **thêm, bỏ hoặc thay advice/advisor** qua `Advised` bị từ chối; Spring ném `AopConfigException`.
4. Cấu hình không thể thay `TargetSource` qua API `Advised` khi ở trạng thái frozen.
5. Danh sách và thứ tự advisor đã lắp được cố định theo cơ chế freeze của Spring.

Ví dụ tình huống:

```java
Advised config = (Advised) proxy;

System.out.println(config.isFrozen());  // true
System.out.println(config.getAdvisors().length); // vẫn đọc được

config.addAdvice(otherAdvice); // AopConfigException
```

Đây là fragment, chỉ để minh họa kết quả khi proxy có `frozen=true`, `opaque=false`. Không chạy riêng nếu thiếu biến `proxy`/`otherAdvice`.

**Phân biệt khóa cấu hình với khóa hành vi:** `frozen` không làm target trở thành immutable, không đồng bộ hóa method, không ngăn thay đổi trạng thái trong một advice object do ứng dụng giữ tham chiếu, và không tạo ranh giới phân quyền bảo mật cho code có đặc quyền truy cập cấu hình ở nơi khác. Ví dụ, nếu advice giữ một biến cấu hình mutable hoặc trỏ đến một service khác, sự thay đổi bên ngoài vẫn có thể tác động đến cách advice thực thi. `frozen` chỉ nhắm vào cấu trúc cấu hình AOP được Spring quản lý.

### Khi nào nên dùng frozen?

- Cấu hình advice đã hoàn chỉnh trước khi công bố proxy và không dự kiến thay đổi runtime.
- Muốn ngăn caller vô tình thêm/bỏ interceptor qua `Advised`.
- Muốn giảm bề mặt can thiệp đến chuỗi advice, đặc biệt đối với auditing hoặc các chính sách mà ứng dụng dựa vào.

Không xem `frozen` là biện pháp bảo mật duy nhất: phải bảo vệ cả logic advice, target, điểm khởi tạo và những đường gọi bỏ qua proxy.

## 4. opaque: ẩn giao diện cấu hình Advised

Thiết lập:

```java
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvice(traceAdvice);
factory.setOpaque(true);

Object proxy = factory.getProxy();
```

Mặc định `opaque=false`. Khi `opaque=true`, Spring AOP proxy **không công khai** `Advised` cho caller. Hậu quả điển hình:

```java
boolean advised = proxy instanceof Advised; // false
Advised config = (Advised) proxy; // ClassCastException
```

Đây là fragment minh họa khi `proxy` được tạo từ cấu hình `setOpaque(true)`. Nếu muốn hỗ trợ nhiều loại proxy một cách an toàn, luôn kiểm tra `instanceof Advised` trước khi cast.

Điều `opaque` **không làm**:

- Không ngăn gọi business interface hoặc chạy advice.
- Không bảo đảm ẩn được sự tồn tại của Spring proxy. Ví dụ `AopUtils.isAopProxy(proxy)` vẫn có thể nhận biết proxy.
- Không mặc nhiên chuyển JDK proxy thành CGLIB hoặc ngược lại.
- Không mã hóa thông tin, bảo vệ target khỏi reflection ở mọi trường hợp hay thay thế kiểm soát truy cập.
- Không đặt `frozen=true`. Đối tượng `ProxyFactory`/hạ tầng tạo proxy còn được owner giữ có thể vẫn quản lý cấu hình theo quyền của nó.

`opaque` là công cụ **giảm khả năng truy cập giao diện quản trị từ proxy reference**, không phải giải pháp che giấu toàn bộ việc ứng dụng dùng Spring.

### Khi nào nên dùng opaque?

- Cung cấp một service/proxy qua API công khai và muốn caller chỉ thấy business contract.
- Muốn chặn một thói quen chẩn đoán không phù hợp như ép kiểu mọi proxy sang `Advised` ở tầng nghiệp vụ.
- Muốn tránh để code nhận proxy tự ý thêm hoặc gỡ advice qua giao diện quản trị.

Đổi lại, tooling nội bộ vốn dựa vào `((Advised) bean).getAdvisors()` sẽ không còn dùng được trực tiếp trên proxy opaque. Cần quan sát qua cấu hình tạo proxy hoặc các công cụ/hook đã thiết kế từ trước.

## 5. So sánh frozen, opaque và trường hợp kết hợp

| Cấu hình | Proxy công khai `Advised`? | `getAdvisors()` qua proxy? | Thêm/bỏ advice qua `Advised`? | Advice đã cấu hình có chạy? |
| --- | --- | --- | --- | --- |
| Mặc định (`false`, `false`) | Có | Có | Có, nếu cấu hình cho phép | Có |
| Chỉ `frozen=true` | Có | Có | Không; thao tác thay đổi bị từ chối | Có |
| Chỉ `opaque=true` | Không | Không qua cast `Advised` | Không qua proxy reference vì thiếu interface | Có |
| Cả `frozen=true` và `opaque=true` | Không | Không qua cast `Advised` | Không qua proxy reference; cấu hình cũng được đánh dấu frozen | Có |

Hai cờ không thay thế nhau:

- **Frozen** trả lời: *có được thay đổi cấu hình advice/advisor hay không?*
- **Opaque** trả lời: *proxy có cho caller nhìn thấy giao diện quản trị `Advised` hay không?*

Nếu cần xem cấu hình sau khi tạo proxy, chỉ `frozen` vẫn cho phép đọc qua `Advised`. Nếu cần caller chỉ tiếp xúc business API, `opaque` che giao diện quản trị. Có thể dùng cả hai khi cấu hình phải ổn định và người dùng proxy không được dùng `Advised`.

## 6. Ví dụ Java 17+ hoàn chỉnh: bốn proxy cạnh nhau

Ví dụ tạo bốn Spring AOP proxy cùng business interface và advice. Nó cho thấy sự khác nhau giữa proxy mặc định, frozen, opaque và frozen + opaque. Không cần khởi động Spring `ApplicationContext` hay dùng annotation `@Aspect` để chạy.

Cấu trúc dự án:

```text
proxy-flags-demo/
├── settings.gradle
├── build.gradle
└── src/main/java/demo/ProxyFlagsDemo.java
```

Tệp `settings.gradle`:

```groovy
rootProject.name = 'proxy-flags-demo'
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
    implementation 'org.springframework:spring-aop:6.2.12'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = 'demo.ProxyFlagsDemo'
}
```

`spring-aop:6.2.12` là một phiên bản Spring Framework 6.2 cụ thể cho bài học. Trong ứng dụng Spring Boot có quản lý dependency, hãy dùng phiên bản Spring Framework được Boot quản lý để tránh lệch version.

Tệp `src/main/java/demo/ProxyFlagsDemo.java`:

```java
package demo;

import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public class ProxyFlagsDemo {

    public interface MessageService {
        void send(String message);
    }

    public static class MessageServiceImpl implements MessageService {
        @Override
        public void send(String message) {
            System.out.println("[target] " + message);
        }
    }

    private static ProxyFactory factoryWithAdvice() {
        ProxyFactory factory = new ProxyFactory(new MessageServiceImpl());

        MethodBeforeAdvice baseAdvice = (method, args, target) ->
                System.out.println("[before] " + method.getName());

        factory.addAdvice(baseAdvice);
        return factory;
    }

    public static void main(String[] args) {
        // A. Mặc định: có Advised, cấu hình không frozen.
        ProxyFactory regularFactory = factoryWithAdvice();
        MessageService regular =
                (MessageService) regularFactory.getProxy();

        System.out.println("regular advised: " + (regular instanceof Advised));

        MethodBeforeAdvice extraAdvice = (method, methodArgs, target) ->
                System.out.println("[extra] " + method.getName());

        ((Advised) regular).addAdvice(extraAdvice);
        regular.send("A");

        // B. Frozen: Advised còn thấy được, nhưng không thể thêm advice.
        ProxyFactory frozenFactory = factoryWithAdvice();
        frozenFactory.setFrozen(true);
        MessageService frozen =
                (MessageService) frozenFactory.getProxy();

        Advised frozenConfig = (Advised) frozen;
        System.out.println("frozen advised: " + (frozen instanceof Advised));
        System.out.println("frozen flag: " + frozenConfig.isFrozen());

        try {
            frozenConfig.addAdvice(extraAdvice);
            System.out.println("unexpected modification");
        } catch (AopConfigException ex) {
            System.out.println("frozen add: blocked");
        }

        frozen.send("B");

        // C. Opaque: proxy vẫn áp dụng advice, nhưng không có Advised.
        ProxyFactory opaqueFactory = factoryWithAdvice();
        opaqueFactory.setOpaque(true);
        MessageService opaque =
                (MessageService) opaqueFactory.getProxy();

        System.out.println("opaque AOP proxy: "
                + AopUtils.isAopProxy(opaque));
        System.out.println("opaque advised: " + (opaque instanceof Advised));
        opaque.send("C");

        // D. Kết hợp hai cờ.
        ProxyFactory bothFactory = factoryWithAdvice();
        bothFactory.setFrozen(true);
        bothFactory.setOpaque(true);
        MessageService both = (MessageService) bothFactory.getProxy();

        System.out.println("both AOP proxy: " + AopUtils.isAopProxy(both));
        System.out.println("both advised: " + (both instanceof Advised));
        both.send("D");
    }
}
```

Chạy với JDK 17+ và Gradle:

```bash
gradle run
```

Nếu dự án có Gradle Wrapper, dùng `./gradlew run` trên Linux/macOS, hoặc `gradlew.bat run` trên Windows. Stdout dự kiến từ chương trình (bỏ các log của Gradle):

```text
regular advised: true
[before] send
[extra] send
[target] A
frozen advised: true
frozen flag: true
frozen add: blocked
[before] send
[target] B
opaque AOP proxy: true
opaque advised: false
[before] send
[target] C
both AOP proxy: true
both advised: false
[before] send
[target] D
```

Giải thích:

1. `factoryWithAdvice()` tạo target và cài một `MethodBeforeAdvice` trước khi proxy được tạo. Cả bốn proxy đều có dòng `[before] send` vì không cờ nào vô hiệu hóa advice.
2. Proxy **mặc định** vẫn công khai `Advised`. Thêm `extraAdvice` qua `((Advised) regular).addAdvice(...)` thành công, nên `regular.send("A")` có hai dòng before.
3. Proxy **frozen** vẫn cast được sang `Advised`, nhưng `isFrozen()` trả `true`; gọi `addAdvice` ném `AopConfigException` và chương trình in `frozen add: blocked`. Advice đã có vẫn hoạt động.
4. Proxy **opaque** không công khai `Advised`, nhưng `AopUtils.isAopProxy(opaque)` vẫn là `true` và lời gọi business vẫn chạy advice.
5. Proxy **frozen + opaque** đồng thời khóa thay đổi advisor và che giao diện quản trị; caller vẫn sử dụng bình thường qua `MessageService`.

**Vì sao ví dụ bắt đúng `AopConfigException`?** Đây là exception Spring AOP dùng khi từ chối thay đổi advice trong cấu hình frozen. Code in một chuỗi cố định thay vì phụ thuộc message exception nội bộ có thể thay đổi giữa các phiên bản Spring.

## 7. frozen không có nghĩa là “mọi thứ bất biến”

Có một giới hạn quan trọng trong thực tế: frozen bảo vệ **cấu trúc advisor/advice trong Spring AOP configuration**, không tự đóng băng trạng thái của chính các đối tượng được tham chiếu.

Ví dụ advice giữ một biến mutable:

```java
final class TraceSwitch {
    volatile boolean enabled = true;
}

TraceSwitch traceSwitch = new TraceSwitch();

MethodBeforeAdvice advice = (method, args, target) -> {
    if (traceSwitch.enabled) {
        System.out.println("[trace] " + method.getName());
    }
};

ProxyFactory factory = new ProxyFactory(target);
factory.addAdvice(advice);
factory.setFrozen(true);
Object proxy = factory.getProxy();

// Owner còn nắm tham chiếu và có thể đổi trạng thái advice:
traceSwitch.enabled = false;
```

Đây là **fragment minh họa**, biến `target` đã được khai báo ở chương trình khác. Sau khi đổi `enabled`, lời gọi qua proxy không còn in trace dù cấu hình advisor không bị thêm, bỏ hoặc thay. Điều đó không vi phạm `frozen`: nội dung của advice đã thay đổi theo biến mà nó quan sát.

Tương tự, frozen:

- Không đảm bảo target thread-safe.
- Không chặn reflection hoặc các đường gọi trực tiếp đến target ngoài proxy.
- Không ngăn biến cấu hình/nhà cung cấp dependency ở tầng khác thay đổi.
- Không ngăn chương trình tạo **một proxy khác** với những advisor khác.
- Không thay thế Spring Security, authorization hoặc kiểm soát quyền truy cập ở mức ứng dụng.

Do đó, không xem `frozen=true` là một “security sandbox”. Nó chủ yếu là khóa quản trị cấu hình AOP và có thể hỗ trợ tối ưu hóa nhỏ của Spring.

## 8. opaque không có nghĩa là “proxy không thể bị phát hiện”

Khi `setOpaque(true)`, caller không thể sử dụng `Advised` qua proxy đó; tuy nhiên, vẫn có những tín hiệu khác để quan sát rằng object đang là proxy:

```java
boolean springAop = AopUtils.isAopProxy(service);
boolean jdk = AopUtils.isJdkDynamicProxy(service);
boolean cglib = AopUtils.isCglibProxy(service);
```

Đoạn này dùng biến `service` đã có và phù hợp cho code chẩn đoán. `opaque` không mặc nhiên làm cả ba biểu thức bằng `false`. Nếu Spring tạo JDK dynamic proxy thì `isJdkDynamicProxy` có thể trả `true`, còn với CGLIB là `isCglibProxy`.

Cũng không được suy rằng `opaque` giữ bí mật về implementation trong mọi trường hợp. Người có quyền truy cập bean factory, `ProxyFactory`, source code, logs hoặc dependency injection có thể biết cách proxy được cấu hình. `opaque` chỉ loại bỏ bề mặt quản trị `Advised` khỏi **proxy instance** phát cho caller.

### Kiểm tra an toàn ở tầng chẩn đoán

Thay vì ép kiểu trực tiếp:

```java
Advised advised = (Advised) service; // sai nếu opaque
```

Nên viết:

```java
if (service instanceof Advised advised) {
    System.out.println("Frozen: " + advised.isFrozen());
    System.out.println("Advisor count: " + advised.getAdvisors().length);
} else if (AopUtils.isAopProxy(service)) {
    System.out.println("Spring AOP proxy without exposed Advised interface");
} else {
    System.out.println("Not a detected Spring AOP proxy");
}
```

Ở đây `service` là biến được cung cấp bên ngoài fragment. Cách kiểm tra này tránh `ClassCastException` trên opaque proxy và không kết luận nhầm rằng “không Advised tức là không có AOP”.

## 9. Giới hạn thao tác khác với proxy Spring AOP

Ngoài frozen và opaque, cần phân biệt những ranh giới thực tế:

| Chủ đề | Giới hạn | Hậu quả thực tế |
| --- | --- | --- |
| JDK proxy | Proxy công khai các business interface được proxy; không phải subclass của implementation | Ép kiểu proxy sang lớp triển khai có thể gây `ClassCastException` |
| CGLIB proxy | Lớp `final` không thể subclass; method `final`/`private` không thể override để intercept | Một số method không thể được advice như kỳ vọng |
| Self-invocation | `this.otherMethod()` trong target không đi qua proxy | Advice dành cho `otherMethod` không chạy trên lời gọi nội bộ |
| Pointcut | `MethodMatcher` chỉ chọn method phù hợp | Có advisor trong danh sách không đảm bảo mọi method chạy advice đó |
| frozen | Chặn thay đổi advice/advisor qua API cấu hình | Không thể `addAdvisor`/`removeAdvisor` trên proxy frozen |
| opaque | Không expose `Advised` | Không thể cast để gọi `getAdvisors` qua proxy |
| Target source | Việc thay target phụ thuộc API/cấu hình được phép | Không tùy tiện `setTargetSource` khi frozen |
| Vòng đời | `getProxy()` không phải lifecycle manager cho resource của target | Đối tượng ngoài IoC vẫn cần owner quản lý resource |
| Thread safety | Tạo proxy không làm state của target/advice tự an toàn | Phải bảo vệ state khi dùng đồng thời |
| Production config | Thay advisor động có thể làm chain thay đổi giữa các lời gọi | Cấu hình trước khi publish; chỉ thay động khi có kiểm thử rõ |

Một proxy `frozen` vẫn có thể thực hiện logic business phức tạp, và proxy `opaque` vẫn có thể bị inspect ở mức class/interface thông thường. Hãy đặt đúng kỳ vọng cho từng cờ.

## 10. Thứ tự cấu hình và lỗi dễ gặp

### 10.1. Cấu hình hoàn chỉnh trước khi freeze

**Đúng:**

```java
ProxyFactory factory = new ProxyFactory(target);
factory.addAdvice(firstAdvice);
factory.addAdvisor(secondAdvisor);
factory.setFrozen(true);
Object proxy = factory.getProxy();
```

**Sai về trình tự mục tiêu:**

```java
ProxyFactory factory = new ProxyFactory(target);
factory.setFrozen(true);
factory.addAdvisor(secondAdvisor); // bị từ chối
```

Các đoạn trên cần các biến `target`, `firstAdvice` và `secondAdvisor` từ ứng dụng. Hãy thêm đầy đủ advice/advisor trước khi gọi `setFrozen(true)`; việc freeze nên là bước chốt cấu hình.

### 10.2. Opaque nhưng code chẩn đoán giả định luôn có Advised

```java
ProxyFactory factory = new ProxyFactory(target);
factory.setOpaque(true);
Object proxy = factory.getProxy();

if (proxy instanceof Advised advised) {
    // Không vào nhánh này với opaque proxy thông thường.
    System.out.println(advised.getAdvisors().length);
}
```

Nếu phải quan sát danh sách advisor trong ứng dụng opaque, giữ một điểm theo dõi từ bên tạo cấu hình, hoặc log danh sách advisor trước khi publish proxy. Đừng dựa vào ép kiểu `Advised` từ các consumer của proxy.

### 10.3. Dùng opaque như một cơ chế bảo vệ advice khỏi mọi thay đổi

`opaque=true` không tự động freeze. Nếu một thành phần đáng tin cậy còn nắm `ProxyFactory` hoặc các object cấu hình, vẫn phải có quy tắc quản trị riêng cho việc thay đổi. Nếu mục đích là khóa cấu hình, cần cân nhắc `frozen` đồng thời.

### 10.4. Tưởng frozen ngăn mọi đường đi bỏ qua advice

Cờ frozen không làm lời gọi `target.method()` hay `this.method()` trở thành lời gọi qua proxy. Những hạn chế JDK/CGLIB, self-invocation và phạm vi pointcut vẫn tồn tại.

### 10.5. Thay đổi cấu hình proxy đang được dùng mà không kiểm soát

Ngay cả với proxy không frozen, việc sửa advisor runtime có thể làm các lời gọi về sau nhận interceptor chain khác. Dùng việc đó có chủ đích cho test, instrumentation hoặc cấu hình động có kiểm soát; trong ứng dụng ổn định, thường hoàn thiện cấu hình trước khi cung cấp proxy cho nhiều caller.

## 11. Kịch bản lựa chọn cấu hình

**Kịch bản A — Debug local:** muốn thêm một interceptor tạm thời để quan sát lỗi, đồng thời xem danh sách advisor. Giữ `opaque=false`, `frozen=false` trong môi trường được kiểm soát. Khi triển khai ổn định, xem xét bỏ thay đổi động.

**Kịch bản B — Proxy nghiệp vụ đã cấu hình sẵn:** muốn caller quan sát trạng thái proxy bằng `Advised` nhưng không sửa được advice. Dùng `frozen=true` và `opaque=false`.

**Kịch bản C — Thư viện xuất business interface:** chỉ muốn bên dùng nhận API nghiệp vụ `MessageService`, không phụ thuộc Spring `Advised`. Dùng `opaque=true`; thêm `frozen=true` nếu cũng muốn cố định advice chain.

**Kịch bản D — AOP cho security/audit:** có advisor quan trọng và muốn ngăn consumer gỡ interceptor qua `Advised`. Freeze cấu hình và thiết kế ranh giới sở hữu proxy phù hợp. Nhưng bảo đảm an toàn nghiệp vụ phải đến từ toàn bộ thiết kế Spring Security/authorization, các điểm gọi và ownership, không chỉ hai cờ này.

## 12. Câu hỏi tự kiểm tra

1. `frozen=true` và `opaque=false` thì `proxy instanceof Advised` là gì? **Thông thường là `true`**; vẫn đọc được danh sách advisor, nhưng thêm/bỏ advice bị từ chối.
2. `frozen=false` và `opaque=true` thì `AopUtils.isAopProxy(proxy)` có thể là `true` không? **Có**; opaque chỉ che `Advised`.
3. Sau khi freeze, có thể đặt `traceSwitch.enabled=false` cho state do advice đọc không? **Có**, vì không thay cấu trúc advisor/advice.
4. Một proxy opaque có tự bỏ `MethodBeforeAdvice` đã đăng ký không? **Không**; advice vẫn chạy khi pointcut và proxy interception phù hợp.
5. Nếu caller giữ target gốc và gọi trực tiếp, frozen có bắt advice chạy không? **Không**; lời gọi phải đi qua proxy.
6. Khi một advisor đã được lắp vào proxy frozen, có thể thay bằng `((Advised) proxy).replaceAdvisor(...)` không? **Không**; Spring từ chối việc thay cấu hình.
7. Khi chuẩn bị freeze, nên gọi `addAdvisor` trước hay sau `setFrozen(true)`? **Trước**, để hoàn thiện chain trước khi khóa.

## 13. Tài liệu Spring Framework

- [ProxyConfig Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x-SNAPSHOT/javadoc-api/org/springframework/aop/framework/ProxyConfig.html): ý nghĩa và mặc định của `setFrozen`, `setOpaque`.
- [Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html): `Advised`, thay đổi advisor runtime và `AopConfigException` với frozen.
- [ProxyFactoryBean](https://docs.spring.io/spring-framework/reference/core/aop-api/pfb.html): các thuộc tính frozen và cấu hình proxy qua Spring.
- [Creating AOP Proxies Programmatically](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html): tạo proxy với `ProxyFactory`, gắn advice và advisor.
- [Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): JDK/CGLIB, giới hạn method và self-invocation.
- [AopUtils Javadoc](https://docs.spring.io/spring-framework/docs/6.2.12/javadoc-api/org/springframework/aop/support/AopUtils.html): `isAopProxy`, `isJdkDynamicProxy`, `isCglibProxy`.

**Ghi nhớ:** `frozen` khóa cấu hình advice/advisor, `opaque` che giao diện quản trị `Advised` khỏi proxy; cả hai không làm advice ngừng chạy và không xóa những giới hạn cơ bản của Spring AOP proxy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-inspection-007">Giữ việc tháo proxy trong chẩn đoán thay vì luồng nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ việc tháo proxy trong chẩn đoán thay vì luồng nghiệp vụ — Spring AOP

## 1. Vấn đề cần giải quyết

Khi một bean đã được Spring AOP tăng cường, đối tượng mà một bean khác nhận từ Spring Container thường là **proxy**. Lời gọi vào proxy đi qua chuỗi interceptor/advice mà Spring đã cấu hình, rồi mới đến phương thức đích nếu phù hợp.

Một cách “đi tắt” thường thấy khi debug là lấy bean đích (target) ra khỏi proxy rồi giữ lại để gọi nghiệp vụ. Cách này có thể giúp quan sát một chi tiết triển khai trong thời điểm chẩn đoán, nhưng **không nên trở thành đường đi của luồng nghiệp vụ**. Khi gọi trực tiếp target, ta có thể bỏ qua các hành vi AOP như advice cho `@Transactional`, `@Cacheable`, `@Async`, kiểm tra quyền hoặc logging do aspect của ứng dụng đảm nhiệm. Với target động, việc tự lấy và giữ target còn vi phạm hợp đồng quản lý vòng đời của Spring.

Ranh giới quan trọng:

- **Quan sát proxy:** xác định bean có được Spring AOP proxy hay không; loại proxy, lớp target, advisor và loại `TargetSource`; ghi nhận thời gian/exception qua advice.
- **Tháo proxy có kiểm soát để chẩn đoán:** khi thực sự cần xem state cụ thể của target trong debugger hoặc kiểm thử chuyên biệt, hiểu giới hạn của loại target và không biến target thành dependency nghiệp vụ.
- **Thực thi nghiệp vụ:** luôn tiếp tục gọi qua **bean proxy do Spring cung cấp**; không lưu target đã tháo vào field, cache hoặc trả cho code nghiệp vụ.

Đây là bài về **Spring AOP và bean trong Spring Container**. Phần cơ chế proxy/aspect chung không được lặp lại ngoài những điều cần để sử dụng Spring đúng cách.

## 2. Vì sao bỏ qua proxy là nguy hiểm?

Giả sử một phương thức service được Spring áp dụng advice. Đường đi mong muốn:

```text
Controller / caller
    |
    v
Spring bean (AOP proxy)
    |
    +--> interceptor/advice được Spring chọn
    |
    v
Target method
```

Nếu code lấy target trước rồi gọi trực tiếp, đường đi trở thành:

```text
Controller / caller
    |
    v
Target method   <-- không đi qua chuỗi interceptor của proxy ngoài
```

Hậu quả tùy vào advisor, pointcut và cấu hình thực tế:

| Tình huống Spring | Gọi qua proxy | Gọi thẳng target đã tháo |
| --- | --- | --- |
| `@Transactional` được kích hoạt qua Spring AOP | Transaction interceptor có cơ hội mở/tham gia giao dịch theo cấu hình | Transaction interceptor trên proxy bị bỏ qua; lời gọi không nhận hành vi giao dịch đó |
| `@Cacheable` được bật qua proxy | Cache interceptor xét key/cache hit/miss | Không chạy cache interceptor của proxy; phương thức có thể thực thi lại |
| `@Async` qua proxy | Interceptor có thể chuyển việc thực thi sang executor theo cấu hình | Thực thi như lời gọi Java thông thường trên target; mất hành vi chuyển luồng qua proxy |
| Advice đo thời gian, audit, bảo mật | Advice phù hợp pointcut được chạy | Advice trên lớp proxy vừa bị bỏ qua không chạy |
| `TargetSource` động | Proxy làm việc với target theo quy tắc của `TargetSource` | Caller có thể giữ nhầm một target tạm thời hoặc không trả tài nguyên đúng cách |

Cụm “bỏ qua advice” áp dụng cho **advice trên proxy bị vượt qua**. Nếu target chính là một proxy lồng bên trong thì lời gọi trực tiếp vào target đó có thể vẫn đi qua các interceptor của lớp trong. Không nên suy đoán tất cả advice đều biến mất hoặc vẫn còn khi chưa kiểm tra cấu trúc thực tế.

Một chi tiết liên quan: khi một phương thức bên trong cùng đối tượng gọi `this.otherMethod()`, Spring AOP dạng proxy cũng không chặn được lời gọi nội bộ ấy như một lời gọi từ bên ngoài qua proxy. Đây là đặc điểm cần xem lại thiết kế lời gọi và bean, không phải lý do để tự tháo proxy.

## 3. Ưu tiên quy trình quan sát chỉ đọc

Thay vì tháo target, hãy xác định **đúng object mà caller đang giữ** và đi từ thông tin ít can thiệp nhất đến thông tin chi tiết hơn:

1. **Nhận diện proxy và kiểu đích:** dùng `AopUtils.isAopProxy(bean)`, `isJdkDynamicProxy(bean)`, `isCglibProxy(bean)` và `getTargetClass(bean)`. Đây là thông tin chẩn đoán, không phải bằng chứng advice đã thực thi.
2. **Xem cấu hình đang công khai:** chỉ khi `bean instanceof Advised advised` mới đọc `advised.getAdvisors()`, `advised.getTargetSource().getTargetClass()` (có thể `null`) hoặc `isStatic()`. Proxy `opaque` có thể không công khai `Advised`.
3. **Có proxy lồng nhau:** `AopProxyUtils.ultimateTargetClass(bean)` có thể dò kiểu đích qua các singleton proxy mà không chủ động mượn target động. Kết quả `Class<?>` không phải đối tượng nghiệp vụ để gọi.
4. **Cần chứng minh advice chạy:** thực hiện lời gọi kiểm thử **qua chính proxy**, xác nhận pointcut/method matcher và trace. Đọc danh sách advisor hoặc biết target class chưa đủ chứng minh interception.

Các API nhận diện proxy và quản lý `TargetSource` được giới thiệu ở những bài trước; ở đây ranh giới cần giữ là **đọc metadata khác với lấy một raw target instance**. Không ép một JDK proxy sang class triển khai chỉ vì đã xác định được target type; không gọi `TargetSource.getTarget()` chỉ để biết class hoặc để định tuyến nghiệp vụ.

## 4. Ví dụ Java 17+: quan sát proxy nhưng tiếp tục gọi qua proxy

Ví dụ tự tạo proxy với `ProxyFactory` để thấy rõ đường gọi. Trong dự án Spring sử dụng `@Aspect`/advisor do container tạo, hãy quan sát **chính instance được inject/lấy qua `ApplicationContext`**, thay vì tự dựng proxy thử rồi suy ra cấu hình production.

Đặt file `pom.xml` tại thư mục demo:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>spring-aop-proxy-observation</artifactId>
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

Tạo `src/main/java/ProxyObservationDemo.java`:

```java
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

public class ProxyObservationDemo {

    interface InvoiceService {
        String issue(String invoiceId);
    }

    static final class DefaultInvoiceService implements InvoiceService {
        @Override
        public String issue(String invoiceId) {
            return "issued:" + invoiceId;
        }
    }

    static void observe(Object bean) {
        System.out.println("springAopProxy=" + AopUtils.isAopProxy(bean));
        System.out.println("targetClass=" +
                AopUtils.getTargetClass(bean).getSimpleName());

        if (bean instanceof Advised advised) {
            System.out.println("advisorCount=" + advised.getAdvisors().length);
            System.out.println("targetStatic=" +
                    advised.getTargetSource().isStatic());
        }
        // Không gọi advised.getTargetSource().getTarget() ở đây.
    }

    public static void main(String[] args) {
        ProxyFactory factory = new ProxyFactory(new DefaultInvoiceService());
        factory.addAdvice((MethodInterceptor) invocation -> {
            System.out.println("advice: before " +
                    invocation.getMethod().getName());
            try {
                return invocation.proceed();
            } finally {
                System.out.println("advice: after " +
                        invocation.getMethod().getName());
            }
        });

        // Giữ đối tượng proxy; không thay bằng instance target.
        InvoiceService service = (InvoiceService) factory.getProxy();

        observe(service);
        System.out.println(service.issue("INV-7"));
    }
}
```

Chạy từ thư mục chứa `pom.xml` với JDK 17+ và Maven:

```bash
mvn -q compile exec:java -Dexec.mainClass=ProxyObservationDemo
```

Stdout của demo:

```text
springAopProxy=true
targetClass=DefaultInvoiceService
advisorCount=1
targetStatic=true
advice: before issue
advice: after issue
issued:INV-7
```

Ý nghĩa:

- `observe` đọc trạng thái qua `AopUtils`, `Advised` và metadata của `TargetSource`; nó không tháo target.
- `advisorCount=1` vì ví dụ gắn một advice, được Spring bọc thành advisor. Ứng dụng thực tế có thể có nhiều advisor; số advisor không cho biết số lớp proxy.
- `targetStatic=true` là thuộc tính của `SingletonTargetSource` được `ProxyFactory` dùng với instance cố định trong demo; **không suy ra mọi Spring AOP proxy đều static**.
- Lời gọi `service.issue(...)` vẫn đi qua proxy nên hai log advice xuất hiện trước kết quả trả về.

Nếu mã nghiệp vụ thay `service` bằng bean target rồi giữ target, điểm chặn của lớp proxy sẽ không có cơ hội chạy trên lời gọi đó. Đây là lý do không đưa thao tác tháo proxy vào service/controller/repository thông thường.

## 5. Phân biệt “đọc cấu hình proxy” với “tháo target”

Một Spring AOP proxy triển khai `Advised` có thể cho phép truy cập `getTargetSource()`. Nhưng `TargetSource` là **abstraction về nguồn target**, không phải lời hứa rằng luôn có một singleton an toàn để dùng lâu dài.

Các phương thức đáng phân biệt:

- `TargetSource.getTargetClass()`: hỏi class của target nếu biết; có thể trả về `null`. Đây là metadata.
- `TargetSource.isStatic()`: `true` nghĩa là các lần gọi `getTarget()` dùng cùng instance target; `false` nghĩa là target có thể thay đổi giữa các lời gọi.
- `TargetSource.getTarget()`: yêu cầu một instance target, có thể ném exception hoặc trả `null`, và có thể kích hoạt logic cấp phát/lấy target.
- `TargetSource.releaseTarget(target)`: phía đã chủ động lấy target phải tuân thủ cơ chế hoàn trả nếu nguồn động yêu cầu. Spring AOP tự thực hiện vòng đời phù hợp trong lời gọi qua proxy; code tự gọi `getTarget()` thì không còn được hưởng tự động sự bảo vệ đó.

Ví dụ `PrototypeTargetSource`, target theo thread, target từ pool, `HotSwappableTargetSource` hoặc nguồn target tùy biến có quy tắc cấp phát/thay thế khác nhau. Nếu code tự lấy target rồi giữ trong biến static hoặc field singleton, nó có thể dùng sai vòng đời, không phản ánh target được proxy chọn trong lần gọi tiếp theo hoặc giữ tài nguyên quá lâu.

### Đoạn cần tránh trong code nghiệp vụ

```java
// Chỉ minh họa anti-pattern; KHÔNG dùng trong service/controller.
Advised advised = (Advised) springBean;
Object rawTarget = advised.getTargetSource().getTarget();
((InvoiceService) rawTarget).issue("INV-7"); // Bỏ qua advice của proxy ngoài.
```

Đây là fragment cảnh báo, **không phải chương trình chạy độc lập**. Nó còn chưa xử lý target nullable, checked exception, target động và nghĩa vụ hoàn trả. Không “chữa” fragment bằng việc lưu `rawTarget` và trả về sau: hướng đúng là **gọi `springBean` qua interface dịch vụ**.

### Nếu chỉ cần xem target trong debugger hoặc kiểm thử

Spring cung cấp `AopProxyUtils.getSingletonTarget(candidate)`: API này chỉ trả target nếu nguồn thực sự là `SingletonTargetSource`; với trường hợp khác (hoặc không phải proxy phù hợp), nó trả `null`. Vì vậy nó là công cụ chẩn đoán có giới hạn, không phải cách lấy target tổng quát.

Trong scope kiểm thử Spring có `AopTestUtils.getTargetObject(candidate)` để lấy một lớp target và `AopTestUtils.getUltimateTargetObject(candidate)` để tháo các lớp proxy lồng nhau. Tài liệu Spring cảnh báo rằng `getUltimateTargetObject` với `TargetSource` **không static** có thể gây hành vi không mong muốn, thậm chí đệ quy vô hạn. Các helper này thuộc `spring-test`, phục vụ nhu cầu kiểm chứng state rất cụ thể; chúng không phải API định tuyến lời gọi production.

Ví dụ **fragment trong test** khi thực sự cần xem state nội bộ sau khi **đã gọi qua proxy**:

```java
// Trong kiểm thử có dependency spring-test; không gọi nghiệp vụ bằng raw.
InvoiceService service = applicationContext.getBean(InvoiceService.class);
service.issue("INV-7");                         // Quan sát hành vi qua AOP
Object raw = AopTestUtils.getTargetObject(service); // Chỉ inspect state
// assert trạng thái nội bộ của raw theo kiểu target thích hợp
```

Fragment trên giả định có `applicationContext` và import `org.springframework.test.util.AopTestUtils`. Không dùng nó để thiết kế hệ thống phụ thuộc vào chi tiết triển khai target. Nếu target là đối tượng động hoặc không biết chắc nguồn target, hãy ưu tiên cách kiểm thử qua interface công khai, spy/metrics của advice và container thay vì tháo.

## 6. Chẩn đoán Spring AOP trong ứng dụng thật

Khi phát sinh nghi vấn “aspect không chạy”, “transaction không hoạt động” hoặc “bean sai kiểu”, quy trình an toàn là **quan sát instance mà caller đang dùng**:

1. Tại nơi nhận dependency, kiểm tra `AopUtils.isAopProxy(bean)`. Nếu kết quả `false`, chưa thể kết luận module không có AOP: phải kiểm tra bean thực tế, cách tạo/inject, pointcut và cấu hình.
2. Xem loại proxy với `AopUtils.isJdkDynamicProxy(bean)` hoặc `AopUtils.isCglibProxy(bean)`, rồi xem `AopUtils.getTargetClass(bean)`. Với JDK proxy, caller phải dựa vào interface mà proxy công bố, không ép kiểu sang class triển khai.
3. Khi `bean instanceof Advised`, xem `getAdvisors()` và `getTargetSource().isStatic()`. Danh sách advisor cho biết cấu hình proxy **đang nhìn thấy**, không chứng minh một lời gọi cụ thể chắc chắn được advice đó chọn: pointcut và loại phương thức vẫn quyết định.
4. Tạo lời gọi thử qua **chính bean proxy**; đặt log/breakpoint ở advice hoặc dùng kiểm thử tích hợp để xác nhận advice đã chạy đúng số lần và thứ tự.
5. Kiểm tra xem caller có tự `new` implementation, lấy raw target, gọi self-invocation hoặc giữ tham chiếu target từ trước hay không. Sửa đường gọi hoặc cấu hình bean thay vì vô hiệu hóa proxy.

Mẫu quan sát gọn trong Spring-managed code (fragment, giả định đã có `ApplicationContext context`):

```java
Object bean = context.getBean("invoiceService");

System.out.println("is Spring AOP proxy: " + AopUtils.isAopProxy(bean));
System.out.println("target type: " + AopUtils.getTargetClass(bean).getName());

if (bean instanceof Advised advised) {
    for (var advisor : advised.getAdvisors()) {
        System.out.println("advisor: " + advisor.getClass().getName());
    }
    System.out.println("target source is static: " +
            advised.getTargetSource().isStatic());
}
```

Phải import `AopUtils` và `Advised` như ví dụ đầy đủ ở mục 4; fragment này không độc lập. Chỉ ghi metadata cần thiết, tránh log target object hoặc tham số nghiệp vụ nhạy cảm một cách vô điều kiện. Việc gọi `bean.toString()` để “nhìn cho rõ” có thể tạo lời gọi trên proxy/target tùy interceptor; metadata thường ổn định và dễ diễn giải hơn.

Trong ứng dụng có `@Transactional`, cũng cần kiểm tra giao dịch bằng kiểm thử truy cập thực sự qua proxy. Nếu code bypass proxy để gọi trực tiếp target thì việc sửa propagation, rollback rules hoặc pointcut có thể không giải quyết nguyên nhân.

## 7. Những nhận định sai dễ dẫn đến tháo proxy nhầm chỗ

**“Muốn biết class thật thì phải `getTarget()`.”** Không cần. Bắt đầu bằng `AopUtils.getTargetClass(bean)`; nếu cần xét proxy lồng nhau, `AopProxyUtils.ultimateTargetClass(bean)` có thể giúp trong giới hạn singleton target.

**“Đã tìm được target thì dùng nó sẽ nhanh hơn.”** Target không đảm bảo tương đương với bean proxy về hợp đồng hành vi. Tối ưu hiệu năng bằng đường tắt này có thể làm mất transaction, cache, logging, bảo mật hoặc việc quản lý target động.

**“Lấy target lúc khởi tạo rồi cache lại là an toàn.”** Không đúng với nhiều `TargetSource` động. Ngay cả singleton target, việc gọi trực tiếp sẽ bỏ qua proxy ngoài. Việc một lần tháo thành công không chứng minh cách sử dụng đó hợp lệ lâu dài.

**“`Advised#getAdvisors()` cho thấy đủ mọi advice trên mọi lớp.”** Nó chỉ mô tả proxy `Advised` đang kiểm tra. Proxy có thể lồng nhau; cũng có thể có hạ tầng khác không xuất hiện qua interface này. Cần đánh giá bằng hành vi thực tế và đường gọi.

**“Có thể tháo proxy trong test nên có thể dùng cách đó trong service.”** Utility trong `spring-test` nhằm phục vụ tình huống kiểm thử hẹp, có cảnh báo riêng với nguồn target động. Dependency nghiệp vụ nên dùng bean proxy được Spring quản lý.

**“`AopUtils.isAopProxy(bean)` là `false` thì phải tự tạo một proxy.”** Chưa đủ thông tin. Bean có thể được tạo sai container, không khớp pointcut, không áp dụng auto-proxy hoặc không cần advice. Phải kiểm tra cấu hình Spring AOP trước khi can thiệp tạo proxy.

## 8. Quy tắc thực hành

- Giữ **Spring-managed proxy** làm tham chiếu được truyền giữa các bean; ưu tiên injection theo interface/hợp đồng công khai.
- Trong monitoring và troubleshooting, dùng `AopUtils`, `Advised`, `Advisor` và metadata của `TargetSource` trước khi nghĩ đến lấy target.
- Khi cần quan sát invocation, đặt log/metric/trace trong advice hoặc test gọi qua proxy. Không chọc xuyên proxy chỉ để biết mã target có chạy.
- Khi thật sự cần inspect target ở test/debugger, kiểm tra loại `TargetSource` và giới hạn API; không cho raw target “thoát” vào code nghiệp vụ.
- Với lỗi aspect/transaction/cache không áp dụng, tìm **đường gọi nào bypass proxy** và khắc phục bằng thiết kế bean/pointcut phù hợp.
- Giữ phân biệt: *biết class đích* ≠ *sở hữu instance đích* ≠ *được phép gọi nghiệp vụ qua instance đích*.

### Tài liệu Spring

- [Spring AOP — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
- [Spring AOP API — Manipulating Advised Objects](https://docs.spring.io/spring-framework/reference/core/aop-api/advised.html)
- [AopUtils — Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/support/AopUtils.html)
- [AopProxyUtils — Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/framework/AopProxyUtils.html)
- [TargetSource — Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/aop/TargetSource.html)
- [AopTestUtils — Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/util/AopTestUtils.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="proxy-inspection-008">Rủi ro gọi trực tiếp target và bỏ qua toàn bộ advice</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro gọi trực tiếp target và bỏ qua toàn bộ advice trong Spring AOP

## 1. Vấn đề: một method nhưng hai đường gọi khác nhau

Trong Spring AOP, cùng một phương thức nghiệp vụ có thể được gọi theo hai đường:

- **Qua Spring AOP proxy:** caller cầm tham chiếu proxy, lời gọi đi qua các interceptor/advice áp dụng cho method rồi mới tới target.
- **Trực tiếp vào target:** caller cầm tham chiếu chính đối tượng đích; lời gọi vào method mà **không qua interceptor chain của Spring proxy**.

Điều thứ hai không có nghĩa method Java không chạy. Trái lại, method vẫn chạy, nhưng các hành vi cắt ngang chỉ được gắn ở lớp Spring AOP proxy — ví dụ audit logging, đo thời gian, transaction interceptor, cache interceptor, retry, async hoặc kiểm tra quyền được khai báo dưới dạng method advice — có thể không được áp dụng cho lời gọi ấy.

Ví dụ một Spring service:

~~~java
@Service
public class InvoiceService {
    @Transactional
    public void pay(String invoiceId) {
        // Xử lý thanh toán.
    }
}
~~~

Nếu đã bật transaction management và Spring tạo đúng transaction proxy, `invoiceProxy.pay("I-1")` có thể đi qua transaction interceptor. Nhưng gọi vào **instance target gốc** `rawInvoiceService.pay("I-1")` bỏ qua transaction advice của chính proxy đó. Khi đó không thể suy luận rằng phương thức tự tạo transaction mới chỉ nhờ annotation `@Transactional`.

**Phạm vi bài:** quan sát Spring proxy/target, những cách vô tình lấy và gọi target, giới hạn self-invocation, hệ quả trên các advisor của Spring, và cách kiểm thử/sửa thiết kế. Đây là kiến thức **Spring AOP proxy-based**, không phải một bài tổng quan về AspectJ weaving.

## 2. Sơ đồ đường gọi trong Spring AOP

~~~text
                  Có Spring AOP proxy
Caller
  |
  +-- proxy.pay("I-1")
          |
          +-- [Advisor/Interceptor A: kiểm tra quyền]
          +-- [Advisor/Interceptor B: transaction]
          +-- [Advisor/Interceptor C: logging]
          |
          +-- target.pay("I-1")
                  |
                  +-- Business method
                  |
          <--- kết quả / exception qua interceptor chain
  |
  +-- target.pay("I-2")       <-- giữ tham chiếu target gốc
          |
          +-- Business method
          |
          X-- không có Spring proxy interceptors cho lời gọi này
~~~

Điểm quan trọng là **đối tượng nhận lời gọi**. Việc method có annotation hay class có aspect phù hợp không tự tạo một bước interception trên một tham chiếu target thô.

Cấu hình Spring `@AspectJ`, XML `<aop:config>`, `ProxyFactory`, `ProxyFactoryBean` và nhiều tính năng Spring như transaction/cache dùng chung nguyên lý proxy này. Có sự khác biệt về cách đăng ký advisor, nhưng khi caller gọi trực tiếp target thì không có cơ hội đi qua **advisor chain gắn với proxy**.

Đừng kết luận mọi kiểm tra đều bị vô hiệu hóa: điều kiện được viết **bên trong business method**, database constraints hay bảo vệ ở tầng khác vẫn có thể chạy. Cụm “bỏ qua toàn bộ advice” ở đây nghĩa là **bỏ qua advice của Spring proxy tại ranh giới lời gọi đó**, không nhất thiết mọi cơ chế bảo vệ khác của ứng dụng.

## 3. Phân biệt proxy object và target object

| Quan sát | Proxy của Spring AOP | Target được proxy bao quanh |
| --- | --- | --- |
| Vai trò | Đón lời gọi từ caller, xét advisor và điều phối interceptor chain | Chứa logic nghiệp vụ |
| Lời gọi method có thể chạy advice? | Có, nếu method phù hợp pointcut và thực sự đi qua proxy | Không thông qua proxy chain chỉ bằng cách gọi trực tiếp target |
| `AopUtils.isAopProxy(obj)` | Thường là `true` với Spring AOP proxy | Thường là `false` với target POJO |
| `obj.getClass()` | JDK proxy class hoặc subclass tạo bởi Spring CGLIB | Concrete implementation class |
| `obj instanceof ServiceInterface` | Có nếu proxy expose interface này | Có nếu target implement interface |
| Lấy instance trong ứng dụng | Thường nhận qua `ApplicationContext`/DI hoặc `ProxyFactory.getProxy()` | Đối tượng được bọc trong proxy hoặc bị tạo trực tiếp bằng `new` |
| Trách nhiệm caller | Gọi qua contract của bean proxy | Không giữ/công khai target để né proxy advice |

Hai object thường **khác nhau về identity**, mặc dù proxy chuyển lời gọi tới cùng target:

~~~java
Object proxy = factory.getProxy();
Object target = originalTarget;

System.out.println(proxy == target); // false trong ví dụ ProxyFactory thường.
~~~

Đây là snippet minh họa trong scope có `factory` và `originalTarget`. Không nên dùng `equals()` để kết luận proxy và target giống nhau, vì `equals` có thể được triển khai/ủy quyền tùy loại proxy. Dùng `AopUtils` để biết object nào là Spring AOP proxy.

## 4. Ví dụ Java 17 chạy được: proxy call, direct call và self-invocation

Ví dụ này dùng **Spring Framework 6.1.15**, tạo proxy bằng `ProxyFactory` để mọi tham chiếu được nhìn thấy rõ. Chỉ method `charge` được một advisor match; method `submit` gọi `charge` nội bộ bằng `this`.

Với cách thiết kế này, ta quan sát chính xác rằng:

1. `proxy.charge(...)` chạy advice.
2. `target.charge(...)` gọi thẳng method, không chạy advice.
3. `proxy.submit(...)` đi tới target `submit`, rồi `this.charge(...)` trên target **không** chạy advice `charge`.

### 4.1. Cấu trúc

~~~text
spring-aop-direct-target-risk/
├── pom.xml
└── src/main/java/demo/DirectTargetDemo.java
~~~

### 4.2. `pom.xml`

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>spring-aop-direct-target-risk</artifactId>
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
                    <mainClass>demo.DirectTargetDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

`spring-aop` kéo các dependency cần thiết cho programmatic proxy/advisor. Ở ví dụ này không dùng annotation `@Aspect` hay parser biểu thức pointcut AspectJ, nên không cần thêm `aspectjweaver` chỉ để chạy mã.

### 4.3. `src/main/java/demo/DirectTargetDemo.java`

~~~java
package demo;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

public class DirectTargetDemo {

    public interface PaymentOperations {
        void charge(String reference);
        void submit(String reference);
    }

    public static class PaymentTarget implements PaymentOperations {

        @Override
        public void charge(String reference) {
            System.out.println("target: charge " + reference);
        }

        @Override
        public void submit(String reference) {
            System.out.println("target: submit " + reference);
            this.charge(reference); // Không qua Spring proxy.
        }
    }

    public static void main(String[] args) {
        PaymentTarget target = new PaymentTarget();

        // Chỉ method "charge" mới được áp dụng advice.
        StaticMethodMatcherPointcut chargePointcut =
                new StaticMethodMatcherPointcut() {
                    @Override
                    public boolean matches(Method method, Class<?> targetClass) {
                        return method.getName().equals("charge");
                    }
                };

        MethodInterceptor audit = invocation -> {
            System.out.println("advice: before charge");
            try {
                Object result = invocation.proceed();
                System.out.println("advice: after charge");
                return result;
            } catch (Throwable ex) {
                System.out.println("advice: error charge");
                throw ex;
            }
        };

        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(target);
        factory.setInterfaces(PaymentOperations.class);
        factory.setProxyTargetClass(false);
        factory.addAdvisor(new DefaultPointcutAdvisor(chargePointcut, audit));

        PaymentOperations proxy = (PaymentOperations) factory.getProxy();

        System.out.println("proxyIsAop=" + AopUtils.isAopProxy(proxy));
        System.out.println("targetIsAop=" + AopUtils.isAopProxy(target));

        System.out.println("-- through proxy --");
        proxy.charge("P-1");

        System.out.println("-- direct target --");
        target.charge("P-2");

        System.out.println("-- proxy submit, internal charge --");
        proxy.submit("P-3");
    }
}
~~~

Từ thư mục chứa `pom.xml`, chạy:

~~~bash
mvn -q compile exec:java
~~~

Các dòng do chương trình tự in, theo đúng thứ tự:

~~~text
proxyIsAop=true
targetIsAop=false
-- through proxy --
advice: before charge
target: charge P-1
advice: after charge
-- direct target --
target: charge P-2
-- proxy submit, internal charge --
target: submit P-3
target: charge P-3
~~~

**Đọc kết quả:**

- `P-1`: có hai dòng advice vì caller có tham chiếu `proxy`, method `charge` match `StaticMethodMatcherPointcut`.
- `P-2`: target chạy nhưng không có dòng advice, vì caller dùng biến `target`.
- `P-3`: caller gọi `submit` qua proxy; `submit` không match pointcut nên không có advice cho `submit`. Bên trong `PaymentTarget.submit`, `this.charge` cũng không đi qua proxy, nên dù `charge` match pointcut, audit advice **không chạy**.
- `AopUtils.isAopProxy` giúp xác minh biến nào thực sự là Spring AOP proxy. Không nên suy đoán từ annotation hoặc tên Java class.

Ví dụ này mang tính giáo dục: nó **cố tình giữ tham chiếu target** để chứng minh bypass. Trong ứng dụng thực tế, nên để caller nhận bean proxy từ Spring DI và hạn chế expose raw target.

## 5. Self-invocation: gọi `this.method()` cũng là đường bỏ qua advice

Self-invocation không nhất thiết thể hiện bằng cú pháp `this.`; gọi trực tiếp method khác cùng object vẫn có cùng vấn đề:

~~~java
@Service
public class BillingService {

    public void submit(String id) {
        charge(id); // Ngầm định this.charge(id).
    }

    @Transactional
    public void charge(String id) {
        // Nghiệp vụ.
    }
}
~~~

Đây là snippet minh họa trong Spring application có transaction manager và transaction proxy được cấu hình. Nếu `submit` được gọi từ bean bên ngoài qua proxy, còn `submit` không có transaction advice riêng, lời gọi `charge` nội bộ không tự mở transaction dựa trên annotation `@Transactional` đặt ở `charge`.

Ngược lại, nếu method ngoài `submit` **đã được** transaction advice bao quanh và một transaction đang active, lời gọi nội bộ có thể vẫn chạy **bên trong transaction hiện hữu**. Vấn đề chính là `charge` không nhận **một lượt transaction interception riêng** (chẳng hạn các propagation semantics riêng của `charge` có thể không được áp dụng). Vì vậy không nói chung chung “self-invocation luôn không có transaction” mà phải xét advisor của outer invocation.

Self-invocation là giới hạn của **cả Spring JDK dynamic proxy lẫn Spring class-based proxy**. Đổi `proxyTargetClass=true` không tự sửa đường gọi `this.method()` bên trong target. Spring AOP không intercept mọi Java call site.

## 6. Vì sao đôi khi ứng dụng lại cầm được target trực tiếp?

### 6.1. Tự `new` service ở một điểm gọi

~~~java
InvoiceService service = new InvoiceService();
service.pay("I-10");
~~~

Đây là một service instance do application code tự tạo, không phải bean proxy do Spring quản lý. Với lời gọi này, không có lý do để mong Spring AOP auto-proxy bean được dùng. Dù `InvoiceService` mang `@Service`, annotation đó **không** can thiệp vào `new`.

Trong ứng dụng dùng Spring IoC, ưu tiên constructor injection của **bean contract** mà container cung cấp:

~~~java
@Service
public class InvoiceControllerService {
    private final InvoiceOperations invoiceService;

    public InvoiceControllerService(InvoiceOperations invoiceService) {
        this.invoiceService = invoiceService;
    }

    public void pay(String id) {
        invoiceService.pay(id); // Gọi qua Spring-injected bean reference.
    }
}
~~~

Snippet thứ hai giả định có interface `InvoiceOperations`, bean phù hợp và Spring component scanning. Spring-injected reference **có thể** là proxy nếu advisor match; để chắc chắn, kiểm tra object thực tế bằng `AopUtils`.

### 6.2. Giữ lại biến target sau khi tạo ProxyFactory

Trong ví dụ phần 4, chương trình có cả:

~~~java
PaymentTarget target = new PaymentTarget();
PaymentOperations proxy = (PaymentOperations) factory.getProxy();
~~~

Nếu vô tình truyền `target` cho một module thay vì `proxy`, module ấy bỏ qua advice. Tên biến “service” không giúp phân biệt; object identity và nơi lấy tham chiếu mới quan trọng.

### 6.3. Trực tiếp lấy target từ Spring `Advised` / `TargetSource`

Spring có API quan sát proxy:

~~~java
if (bean instanceof org.springframework.aop.framework.Advised advised) {
    var source = advised.getTargetSource();
    System.out.println("targetClass=" + source.getTargetClass());
}
~~~

Đây là snippet chẩn đoán với biến `bean` đã có. `Advised` cho phép xem advisor/target-source khi proxy expose interface đó; không phải mọi proxy đều bắt buộc expose `Advised` (có tùy chọn opaque).

Một đoạn code **có rủi ro**, không nên đưa vào business path:

~~~java
Object raw = ((org.springframework.aop.framework.Advised) proxy)
        .getTargetSource()
        .getTarget();
// Gọi method trên raw ở đây sẽ bỏ qua proxy advice.
~~~

Lưu ý:

- Cú pháp này giả định `proxy instanceof Advised` và proxy có target source phù hợp; có thể `ClassCastException` nếu không đúng.
- `TargetSource.getTarget()` không phải lúc nào cũng trả **một singleton ổn định**. Các target source động, pooled, lazy/scoped có thể có lifecycle riêng, hoặc `getTarget()` trả `null`.
- Với dynamic target source, Spring có thể yêu cầu gọi `releaseTarget(...)` theo contract sau khi dùng; tự rút target không đúng cách có thể tạo rò rỉ hoặc phá phạm vi sống của object.
- Raw target có thể thiếu giao diện/method được **introduction advisor** bổ sung trên proxy.
- Kể cả khi lấy được raw object, dùng nó để gọi nghiệp vụ vẫn bỏ qua interceptor chain mà proxy áp dụng.

**Cách quan sát an toàn hơn:** bắt đầu bằng `AopUtils.isAopProxy(bean)`, `AopUtils.getTargetClass(bean)`, `AopUtils.isJdkDynamicProxy(bean)`, `AopUtils.isCglibProxy(bean)`, và nếu hỗ trợ thì đọc `((Advised) bean).getAdvisors()` — thay vì unwrap target rồi chạy business method.

### 6.4. Test utility có thể làm rò target vào production

Spring Test có `AopTestUtils.getTargetObject(...)` và `AopTestUtils.getUltimateTargetObject(...)` để hỗ trợ một số loại integration test. Các utility này không phải lối truy cập business service thông thường. Nếu test cần truy cập field/counter nội bộ target để assert, hãy cân nhắc thiết kế observable test seam tốt hơn; dù bắt buộc unwrap để kiểm tra state, **không dùng raw target để kiểm chứng advice có chạy hay không**.

Với các scoped/động target source, việc cố unwrap tới “ultimate target” có thể không có ý nghĩa ổn định hoặc ảnh hưởng lifecycle. Không đưa code unwrap từ test vào production service rồi giả định AOP vẫn hoạt động.

## 7. Hệ quả cụ thể với các tính năng Spring dùng proxy

| Advice/hạ tầng áp dụng trên Spring proxy | Nếu gọi qua proxy (đủ điều kiện) | Nếu gọi trực tiếp raw target |
| --- | --- | --- |
| `@Before` / `@Around` audit, logging | Advice chạy trên join point match | Bỏ qua advice này |
| `@Transactional` với proxy transaction management | Transaction interceptor áp dụng propagation/rollback semantics | Không có lượt transaction interception của method đích; transaction ngoài nếu có vẫn có thể tồn tại |
| `@Cacheable` / `@CacheEvict` ở Spring cache proxy | Cache interceptor đọc, ghi hoặc xóa cache theo cấu hình | Bỏ qua hoạt động cache interceptor của lời gọi ấy |
| `@Async` ở async advisor | Có thể chuyển method invocation đến executor theo cấu hình | Method body được gọi như Java thông thường trên thread caller, trừ khi tự triển khai async bên trong |
| `@Retryable` nếu Spring Retry advisor được bật | Retry interceptor có thể chạy chính sách retry | Không chạy retry advice của proxy |
| Method-security annotations được triển khai qua Spring Security method proxy | Method-security interceptor có thể từ chối/cho phép | Interceptor ở proxy boundary này không chạy; vẫn phải xét các lớp kiểm soát quyền khác |
| Method-validation được cài bằng Spring AOP advisor | Có thể kiểm tra tham số/return ở proxy method boundary | Bỏ qua validation advice tại boundary ấy |
| Tracing/metrics aspect | Aspect có thể quan sát lời gọi | Thiếu span/counter/timing gắn ở advice đó |

Bảng mô tả các tính năng **khi đã cấu hình và đang dùng Spring proxy-based interception**. Không phải annotation đơn lẻ nào cũng đủ để tạo advisor. Cũng không phải mọi tính năng đều áp dụng cho mọi đường thực thi: một số hệ thống có instrumentation bên ngoài proxy, nên cần xác minh thực tế.

### Ví dụ transaction propagation bị bỏ qua

Giả sử `outer()` đã có transaction `REQUIRED`, còn `inner()` yêu cầu `REQUIRES_NEW`. Nếu `outer()` gọi `this.inner()`, Spring không qua transaction interceptor của `inner`, do đó **không có hành vi suspend transaction cũ/tạo transaction mới do advice `inner` yêu cầu** tại lời gọi nội bộ đó.

Snippet minh họa, không phải chương trình chạy độc lập:

~~~java
@Service
public class AccountService {
    @Transactional
    public void outer() {
        inner(); // Không đi qua proxy của inner().
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inner() {
        // ...
    }
}
~~~

Muốn kiểm tra chính xác, cần có `PlatformTransactionManager`, datasource/hạ tầng transaction và transaction management được kích hoạt. Kết luận ở đây là **cơ chế advice bị bypass**, không phải khẳng định dữ liệu database chắc chắn commit/rollback theo một kết quả cố định.

### Ví dụ cache không được áp dụng

Nếu `@Cacheable` đặt ở `loadProduct`, một caller sử dụng raw target sẽ chạy trực tiếp body thay vì hỏi cache interceptor. Các method gọi nội bộ trong cùng target có thể bị tương tự. Vì vậy đọc được đúng dữ liệu không đủ để chứng minh cache aspect đang hoạt động: cần kiểm tra số lần underlying repository được truy cập hoặc cache statistics qua proxy invocation.

## 8. Phát hiện đường gọi bypass thay vì chỉ nhìn trạng thái proxy

Kiểm tra **tham chiếu ngay tại caller**, không chỉ một bean cùng tên ở nơi khác:

~~~java
Object service = context.getBean("paymentService");
System.out.println(AopUtils.isAopProxy(service));
System.out.println(AopUtils.getTargetClass(service).getName());
~~~

Đây là snippet cần `ApplicationContext context`, bean hợp lệ và import `org.springframework.aop.support.AopUtils`. Nếu proxy công khai `Advised`, có thể kiểm tra `getAdvisors()` và pointcut. Nhưng **cả proxy=true lẫn advisor hiện diện đều không chứng minh invocation thực tế đã đi qua advisor**: cần theo dấu biến caller sử dụng và assert sự kiện advice khi gọi đúng method.

### Dấu hiệu ưu tiên trong code review

1. Controller/facade tự `new SomeService()` thay vì nhận Spring-managed bean.
2. Cả `target` và `proxy` cùng tồn tại, nhưng caller khác được truyền `target`.
3. `Advised.getTargetSource().getTarget()` được dùng để thực thi nghiệp vụ, hoặc `AopTestUtils.getTargetObject(...)` lọt ra ngoài kiểm thử.
4. Có `this.advisedMethod()` hoặc lời gọi nội bộ `advisedMethod()` trên cùng target.
5. Test tự `new Target()` nhưng kỳ vọng `@Transactional`, `@Cacheable` hoặc Spring `@Aspect` chạy.
6. Caller không cast được JDK proxy về implementation nên lấy raw target làm đường vòng.

Với mỗi dấu hiệu, đối chiếu **proxy-reference → pointcut match → advice trace → target effect**. Chỉ khi có trace trên đúng lời gọi mới phân biệt được lỗi bypass với một phương thức không match advisor.

## 9. Cách sửa ưu tiên: tách bean và gọi qua Spring-managed reference

Nếu một method trong bean A phải áp dụng aspect trên method của bean B, hãy để A **inject B** rồi gọi qua interface/bean reference được Spring cung cấp.

Ví dụ minh họa cấu trúc:

~~~java
public interface ChargeService {
    void charge(String reference);
}

@Service
public class ChargeServiceImpl implements ChargeService {
    @Override
    @Transactional
    public void charge(String reference) {
        // Charge business logic.
    }
}

@Service
public class CheckoutWorkflow {

    private final ChargeService chargeService;

    public CheckoutWorkflow(ChargeService chargeService) {
        this.chargeService = chargeService;
    }

    public void submit(String reference) {
        chargeService.charge(reference); // Đi qua injected Spring reference.
    }
}
~~~

Đây là **mẫu nhiều file/class minh họa**, không phải một Java source file độc lập: cần import Spring `@Service`, `@Transactional`, component scanning/bean registration và transaction manager phù hợp.

So với `this.charge`, kiến trúc này cho Spring cơ hội đưa `ChargeService` proxy vào `CheckoutWorkflow`. Không tự tạo `new ChargeServiceImpl()` trong workflow. Khi pointcut match, advice chạy trên `charge` trong lời gọi từ `submit`.

Một phương án khác là đưa điều kiện bắt buộc vào chính phương thức service hoặc lớp policy được gọi trực tiếp nếu đó là **business invariant**. Không dùng AOP làm lớp duy nhất bảo đảm một quy tắc domain không thể bị bỏ qua bởi đường gọi hợp lệ khác.

### Có nên self-inject bean vào chính nó?

Một số ứng dụng truyền proxy của bean vào chính bean để gọi method khác qua proxy. Spring có thể hỗ trợ những biến thể self-reference, nhưng chúng làm ranh giới phụ thuộc khó hiểu hơn và dễ gặp vòng phụ thuộc, proxy typing hoặc lifecycle issues. Tách component thành hai bean với trách nhiệm rõ ràng thường dễ test/bảo trì hơn.

### `AopContext.currentProxy()` là lựa chọn cuối, không phải mặc định

Spring có thể expose proxy hiện tại khi bật tùy chọn:

~~~java
factory.setExposeProxy(true);
~~~

và trong lời gọi đi qua proxy:

~~~java
((ChargeService) AopContext.currentProxy()).charge(reference);
~~~

Những đoạn trên là **snippets minh họa**, cần `AopContext` import, interface/type phù hợp và proxy exposure được bật. `AopContext.currentProxy()` dựa vào ThreadLocal của lời gọi Spring AOP đang xử lý; nếu không trong advice invocation phù hợp hoặc `exposeProxy` chưa bật, có thể ném `IllegalStateException`. Nó ràng buộc business class với Spring AOP runtime và không nên coi là API để lấy bean toàn cục. Khi xử lý bất đồng bộ hoặc chuyển thread, càng không nên giả định `currentProxy()` còn khả dụng.

Trong tài liệu Spring, hướng ưu tiên là **tránh self-invocation bằng cách refactor**, không lạm dụng `AopContext` chỉ để khắc phục một cấu trúc đang khó bảo trì.

## 10. Cách viết test chứng minh advice chạy thật

Một test hữu ích phải gọi **đúng object** mà production caller sử dụng. Với Spring integration test, nên lấy từ `ApplicationContext` rồi kiểm tra:

- Đối tượng được Spring cung cấp có phải proxy (khi cấu hình dự kiến có proxy)?
- Method `charge` có đúng một lần advice start/end, cả result và exception có được giữ nguyên?
- Method không match pointcut không bị advice nhầm?
- Direct target được tạo trong fixture thử nghiệm không tạo log advice?
- Self-invocation trong cùng bean có đúng biểu hiện bypass của proxy mode?
- Khi refactor sang bean khác và gọi qua injected proxy, advice chạy trở lại?
- Với `@Transactional(REQUIRES_NEW)`, dùng integration test có transaction manager thật và quan sát các transaction boundaries đúng quy tắc thay vì chỉ assert một annotation tồn tại?

Một chiến lược tránh `System.out` cho test tự động là tạo **test advice** ghi vào một collector thread-safe, rồi assert số lần advice được gọi. Không lấy target ra và tự gọi để assert aspect; như vậy test đã loại bỏ chính cơ chế đang được thử.

### Bảng dự kiến cho ví dụ `DirectTargetDemo`

| Lời gọi | Caller reference | `charge` chạy? | Audit advice trên `charge` chạy? |
| --- | --- | --- | --- |
| `proxy.charge("P-1")` | Spring proxy | Có | Có |
| `target.charge("P-2")` | Raw target | Có | Không |
| `proxy.submit("P-3")` → `this.charge(...)` | Proxy chỉ ở `submit`; nội bộ target gọi thẳng `charge` | Có | Không |
| Một bean khác inject đúng `PaymentOperations` proxy rồi gọi `charge` | Spring-injected proxy | Có | Có, nếu advisor match |

Bảng này phân biệt **method body có chạy** với **advice có chạy**, tránh hiểu nhầm rằng bypass nghĩa là không thực thi chức năng.

## 11. CGLIB có loại bỏ rủi ro không?

Không. Cả JDK interface proxy và Spring class-based proxy đều áp dụng advice thông qua **lời gọi đi qua proxy**. Dù với CGLIB, target method đã được đưa vào luồng thực thi target thì lời gọi qua `this` trong đối tượng target vẫn có thể tránh interception của method tự gọi theo mô hình Spring proxy-based.

Một class-based proxy còn có giới hạn với `final` method, `private` method và cấu hình module/package access; đó là các nguyên nhân **độc lập** với direct-target bypass. Đổi:

~~~java
factory.setProxyTargetClass(true);
~~~

không làm caller đang giữ `target` đột nhiên gọi tới `proxy`, cũng không bảo đảm mọi self-invocation được chặn.

Ngược lại, Spring AOP không phải AspectJ compile-time/load-time weaving; cơ chế weaving cấp bytecode có hành vi khác với self-invocation. Nhưng việc chuyển sang weaving là quyết định kiến trúc riêng, không phải cách chữa mặc định cho bài toán quan sát proxy hiện tại.

## 12. Một số lỗi thiết kế và cách đánh giá rủi ro

| Sai sót | Ảnh hưởng thực tế | Cách khắc phục |
| --- | --- | --- |
| Tạo service bằng `new` rồi trông đợi Spring aspect | Không có Spring proxy của bean ấy | Inject bean từ Spring context |
| Dùng `getTargetSource().getTarget()` để có concrete class | Bỏ qua advice, có thể làm sai lifecycle target source | Gọi qua proxy; điều chỉnh interface/proxy strategy hợp lý |
| Method `@Transactional` được gọi từ `this` | Transaction interceptor riêng của method nội bộ không chạy | Tách bean boundary hoặc thiết kế transaction tại entry point |
| Test target trực tiếp rồi cho rằng logging aspect hỏng | Test đã bypass cơ chế cần quan sát | Gọi bean proxy trong integration test |
| Coi `AopUtils.isAopProxy=true` nghĩa là mọi method được advice | Method matcher có thể loại method | Kiểm tra advisors, pointcut và lời gọi thật |
| Đổi `proxyTargetClass=true` để sửa self-invocation | Không thay đổi việc `this.method` bypass proxy | Refactor đường gọi |
| Cho raw target tham gia luồng kiểm tra quyền/transaction | Bỏ qua method-level interceptor tương ứng | Không phát tán target, thêm bảo vệ domain/service boundary |
| Nghĩ tất cả transaction đều biến mất khi bypass | Có thể vẫn có outer transaction hợp lệ | Kiểm tra transaction context và phương thức nào có interceptor |
| Dùng `AopContext.currentProxy()` mọi nơi | Spring-specific coupling, yêu cầu exposeProxy và invocation context | Ưu tiên DI/tách bean |
| Tự unwrap target source có scope/pooling | Mất cơ chế acquire/release và semantics lifecycle | Quan sát metadata/advisor thay vì gọi raw target |
| Đưa `AopTestUtils` vào production code | Rò rỉ test-only helper, làm thay đổi đường gọi | Hạn chế trong test, thiết kế API nghiệp vụ qua proxy |

### Checklist code review

1. Bean service được khởi tạo và injected ở đâu? Caller có **tham chiếu thực tế** nào?
2. Có chỗ nào gọi phương thức annotated từ cùng instance bằng `this` hoặc không ghi `this`?
3. Có chỗ nào unwrap `Advised`, `TargetSource`, sử dụng `getTarget()` để gọi nghiệp vụ?
4. Rủi ro business nào phụ thuộc lời gọi qua proxy (`@Transactional`, method security, cache, async, logging...)?
5. Nếu một tính năng phải luôn được thực thi, liệu có đường gọi hợp lệ nào từ các bean khác đi thẳng vào target không?
6. Test có lấy đúng Spring-managed bean và kiểm chứng hiệu ứng advice thực tế?
7. Khi sửa, có thể tách boundary sang một bean khác và inject proxy theo interface thay vì self-reference?
8. Với scoped/dynamic target, có bảo toàn lifecycle mà không ép giữ raw target lâu dài?

## 13. Kết luận

Điểm cốt lõi của Spring AOP là **proxy không phải target**. `proxy.method()` cho Spring cơ hội áp dụng các advisor và advice tương ứng; `target.method()` chỉ gọi business method, bỏ qua interceptor chain của Spring proxy. Self-invocation `this.method()` trong target là một trường hợp bypass khác dù entry point ban đầu có đi qua proxy.

Để tránh rủi ro, hãy để caller nhận bean qua Spring DI, không phát tán raw target, tránh dùng `TargetSource.getTarget()` cho nghiệp vụ, và tổ chức các method cần aspect ở ranh giới các bean có thể gọi qua proxy. Dùng `AopUtils` và integration test trên **chính object caller sử dụng** để xác minh proxy, nhưng vẫn kiểm tra pointcut/advisor cụ thể vì proxy tồn tại chưa chứng minh một advice đã thực thi.

### Tài liệu tham khảo Spring chính thức

- [Spring Framework — Proxying Mechanisms](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html): mô tả `this`-self-invocation, giải pháp refactor, self injection, `AopContext.currentProxy()`.
- [Spring Framework — Creating AOP Proxies Programmatically with ProxyFactory](https://docs.spring.io/spring-framework/reference/core/aop-api/prog.html).
- [Spring Framework — AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/introduction-proxies.html).
- [Spring Framework — Advice API](https://docs.spring.io/spring-framework/reference/core/aop-api/advice.html).
- [Spring Framework — TargetSource API](https://docs.spring.io/spring-framework/docs/6.1.15/javadoc-api/org/springframework/aop/TargetSource.html).


</details>

- [Quay lại đầu trang](#back-to-top)
